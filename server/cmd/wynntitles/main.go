// Command wynntitles runs the WynnTitles API server.
//
// Configuration comes from environment variables:
//
//	WT_ADDR         listen address (default ":8080")
//	WT_DB           SQLite database path (default "data/wynntitles.db")
//	WT_SESSION_TTL  how long a login lasts (default "720h")
//	WT_MOJANG_URL   Mojang session server (default "https://sessionserver.mojang.com")
//	WT_OFFLINE_AUTH "true" skips Mojang and trusts any username. Local testing
//	                with ./gradlew runClient only; never enable it in production.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/api"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

func main() {
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
		Log:        log,
		Now:        time.Now,
	}
	if err := srv.LoadTitles(ctx); err != nil {
		return err
	}

	go pruneSessions(ctx, db, log)

	httpServer := &http.Server{
		Addr:              env("WT_ADDR", ":8080"),
		Handler:           srv.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
	}

	errc := make(chan error, 1)
	go func() {
		log.Info("listening", "addr", httpServer.Addr, "achievements", len(cat.Achievements),
			"catalog", cat.Version, "titles", srv.Titles.Len())
		errc <- httpServer.ListenAndServe()
	}()

	select {
	case err := <-errc:
		return err
	case <-ctx.Done():
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
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
