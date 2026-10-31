// Command wynntitles runs the WynnTitles API server.
//
// Configuration comes from environment variables:
//
//	WT_ADDR         listen address (default ":8080")
//	WT_DB           SQLite database path (default "data/wynntitles.db")
//	WT_ADMIN_ADDR   separate dashboard listen address (unset disables it)
//	WT_ADMIN_USERNAME dashboard login name
//	WT_ADMIN_PASSWORD dashboard password (24+ characters), or WT_ADMIN_PASSWORD_FILE
//	WT_ADMIN_ALLOWED_CIDRS comma-separated source networks (default loopback)
//	WT_DEV_UUID     only account allowed developer grants (unset disables grants)
//	WT_APPEAL_URL   where the suspension message sends players (unset omits it)
//	WT_SESSION_TTL  how long a login lasts (default "720h")
//	WT_STATS_TTL    how long achievement rarity is cached (default "1m")
//	WT_MOJANG_URL   Mojang session server (default "https://sessionserver.mojang.com")
//	WT_OFFLINE_AUTH "true" skips Mojang and trusts any username. Local testing
//	                with ./gradlew runClient only; never enable it in production.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/api"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

// version is set at build time; see server/Dockerfile.
var version = "dev"

func main() {
	if len(os.Args) == 2 && os.Args[1] == "healthcheck" {
		client := &http.Client{Timeout: 3 * time.Second}
		_, port, err := net.SplitHostPort(env("WT_ADDR", ":8080"))
		if err != nil {
			os.Exit(1)
		}
		response, err := client.Get("http://127.0.0.1:" + port + "/healthz")
		if err != nil {
			os.Exit(1)
		}
		response.Body.Close()
		if response.StatusCode != http.StatusOK {
			os.Exit(1)
		}
		return
	}
	log := slog.New(slog.NewTextHandler(os.Stdout, nil))
	if err := run(log); err != nil {
		log.Error("server stopped", "err", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	sessionTTL, err := time.ParseDuration(env("WT_SESSION_TTL", "720h"))
	if err != nil {
		return err
	}
	statsTTL, err := time.ParseDuration(env("WT_STATS_TTL", "1m"))
	if err != nil {
		return err
	}

	cat, err := catalog.Load()
	if err != nil {
		return err
	}

	db, err := store.Open(env("WT_DB", "data/wynntitles.db"))
	if err != nil {
		return err
	}
	defer db.Close()

	var verifier auth.Verifier = &auth.MojangVerifier{
		BaseURL: env("WT_MOJANG_URL", "https://sessionserver.mojang.com"),
		Client:  &http.Client{Timeout: 10 * time.Second},
	}
	if os.Getenv("WT_OFFLINE_AUTH") == "true" {
		log.Warn("WT_OFFLINE_AUTH is on: anyone can log in as anyone. Use for local testing only.")
		verifier = auth.OfflineVerifier{}
	}

	srv := &api.Server{
		Store:      db,
		Catalog:    cat,
		Titles:     titles.NewCache(),
		Challenges: auth.NewChallenges(2*time.Minute, 10_000),
		Verifier:   verifier,
		SessionTTL: sessionTTL,
		StatsTTL:   statsTTL,
		Log:        log,
		Now:        time.Now,
		Version:    version,
		AppealURL:  os.Getenv("WT_APPEAL_URL"),
	}
	if err := srv.LoadTitles(ctx); err != nil {
		return err
	}

	go pruneSessions(ctx, db, log)

	servers := []*http.Server{newHTTPServer(env("WT_ADDR", ":8080"), srv.Handler())}
	if addr := os.Getenv("WT_ADMIN_ADDR"); addr != "" {
		config, err := adminConfig()
		if err != nil {
			return err
		}
		handler, err := srv.AdminHandler(config)
		if err != nil {
			return err
		}
		servers = append(servers, newHTTPServer(addr, handler))
	}
	// Bind every listener before serving. A private-port conflict fails startup
	// cleanly instead of leaving a partially configured process running.
	listeners := make([]net.Listener, 0, len(servers))
	defer func() {
		for _, listener := range listeners {
			listener.Close()
		}
	}()
	for _, server := range servers {
		listener, err := net.Listen("tcp", server.Addr)
		if err != nil {
			return err
		}
		listeners = append(listeners, listener)
	}
	errc := make(chan error, len(servers))
	for i, server := range servers {
		log.Info("listening", "addr", server.Addr, "dashboard", i == 1)
		go func() { errc <- server.Serve(listeners[i]) }()
	}
	var serveErr error
	select {
	case serveErr = <-errc:
	case <-ctx.Done():
	}
	if errors.Is(serveErr, http.ErrServerClosed) {
		serveErr = nil
	}
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	// Start both shutdowns together so neither listener accepts new writes while
	// the other drains. Wait for both before closing the shared database.
	shutdowns := make(chan error, len(servers))
	for _, server := range servers {
		go func() { shutdowns <- server.Shutdown(shutdownCtx) }()
	}
	for range servers {
		if err := <-shutdowns; err != nil {
			serveErr = errors.Join(serveErr, err)
		}
	}
	return serveErr
}

func newHTTPServer(addr string, handler http.Handler) *http.Server {
	return &http.Server{Addr: addr, Handler: handler, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 10 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 << 10}
}

func adminConfig() (api.AdminConfig, error) {
	password := os.Getenv("WT_ADMIN_PASSWORD")
	if file := os.Getenv("WT_ADMIN_PASSWORD_FILE"); file != "" {
		if password != "" {
			return api.AdminConfig{}, fmt.Errorf("set only one of WT_ADMIN_PASSWORD or WT_ADMIN_PASSWORD_FILE")
		}
		data, err := os.ReadFile(file)
		if err != nil {
			return api.AdminConfig{}, fmt.Errorf("read dashboard password: %w", err)
		}
		password = strings.TrimRight(string(data), "\r\n")
	}
	return api.AdminConfig{Username: os.Getenv("WT_ADMIN_USERNAME"), Password: password, DeveloperUUID: os.Getenv("WT_DEV_UUID"), AllowedCIDRs: strings.FieldsFunc(os.Getenv("WT_ADMIN_ALLOWED_CIDRS"), func(r rune) bool { return r == ',' })}, nil
}

func pruneSessions(ctx context.Context, db *store.Store, log *slog.Logger) {
	ticker := time.NewTicker(time.Hour)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if n, err := db.DeleteExpiredSessions(ctx, time.Now()); err != nil {
				log.Error("prune sessions", "err", err)
			} else if n > 0 {
				log.Info("pruned expired sessions", "count", n)
			}
		}
	}
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
