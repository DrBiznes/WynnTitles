package api

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"html/template"
	"io/fs"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
)

//go:embed admin/*
var adminFiles embed.FS

// AdminConfig is independent of Minecraft player authentication. The caller
// mounts AdminHandler ONLY on the private listener, never on Handler().
type AdminConfig struct {
	Username, Password, DeveloperUUID string
	AllowedCIDRs                      []string
}

type adminHandler struct {
	server                 *Server
	config                 AdminConfig
	userHash, passwordHash [32]byte
	csrf                   string
	key                    []byte
	networks               []*net.IPNet
	template               *template.Template
}

type adminClaim struct {
	store.Unlock
	Name   string
	Points int
}
type adminPage struct {
	CSRF, Query, Message string
	Page, Next, Previous int
	Players              []store.Player
	Player               *meResponse
	Claims               []adminClaim
	Audit                []store.ModerationEntry
	Achievements         []catalog.Achievement
	Shop                 []catalog.ShopItem
	Developer            bool
	Confirm              *adminCommand
}
type adminCommand struct {
	UUID, Action, Item, Reason, Signature, Description string
}

func (s *Server) AdminHandler(config AdminConfig) (http.Handler, error) {
	if strings.TrimSpace(config.Username) == "" || len(config.Username) > 64 || len(config.Password) < 24 {
		return nil, errors.New("dashboard requires WT_ADMIN_USERNAME and a password of at least 24 characters")
	}
	if config.DeveloperUUID != "" {
		normalized, ok := auth.NormalizeUUID(config.DeveloperUUID)
		if !ok {
			return nil, errors.New("WT_DEV_UUID must be a Minecraft UUID")
		}
		config.DeveloperUUID = normalized
	}
	if len(config.AllowedCIDRs) == 0 {
		config.AllowedCIDRs = []string{"127.0.0.0/8", "::1/128"}
	}
	a := &adminHandler{server: s, config: config, userHash: sha256.Sum256([]byte(config.Username)), passwordHash: sha256.Sum256([]byte(config.Password)), key: make([]byte, 32)}
	a.config.Password = ""
	for _, raw := range config.AllowedCIDRs {
		_, network, err := net.ParseCIDR(strings.TrimSpace(raw))
		if err != nil {
			return nil, fmt.Errorf("invalid dashboard allowed CIDR: %w", err)
		}
		a.networks = append(a.networks, network)
	}
	if _, err := rand.Read(a.key); err != nil {
		return nil, err
	}
	a.csrf = a.sign("csrf")
	funcs := template.FuncMap{"at": func(t time.Time) string { return t.UTC().Format("2006-01-02 15:04 UTC") }}
	var err error
	a.template, err = template.New("dashboard.html").Funcs(funcs).ParseFS(adminFiles, "admin/dashboard.html")
	if err != nil {
		return nil, err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", a.index)
	mux.HandleFunc("GET /player/{uuid}", a.player)
	mux.HandleFunc("POST /preview", a.preview)
	mux.HandleFunc("POST /apply", a.apply)
	assets, err := fs.Sub(adminFiles, "admin")
	if err != nil {
		return nil, err
	}
	mux.Handle("GET /assets/", http.StripPrefix("/assets/", http.FileServer(http.FS(assets))))
	return s.recoverer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		// no-referrer makes native form posts send Origin: null, which our
		// strict origin check intentionally rejects. Keep same-origin posts
		// working while suppressing referrers to other sites.
		w.Header().Set("Referrer-Policy", "same-origin")
		w.Header().Set("Content-Security-Policy", "default-src 'none'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		ip := net.ParseIP(host)
		allowed := false
		if err == nil {
			for _, network := range a.networks {
				allowed = allowed || network.Contains(ip)
			}
		}
		if !allowed {
			http.Error(w, "Dashboard is restricted to configured networks", http.StatusForbidden)
			return
		}
		user, password, ok := r.BasicAuth()
		uh, ph := sha256.Sum256([]byte(user)), sha256.Sum256([]byte(password))
		valid := subtle.ConstantTimeCompare(uh[:], a.userHash[:]) & subtle.ConstantTimeCompare(ph[:], a.passwordHash[:])
		if !ok || valid != 1 {
			w.Header().Set("WWW-Authenticate", `Basic realm="WynnTitles moderation", charset="UTF-8"`)
			http.Error(w, "Dashboard login required", http.StatusUnauthorized)
			return
		}
		// Never trust forwarding headers for network or origin authorization.
		if r.Method == http.MethodPost {
			if origin := r.Header.Get("Origin"); origin != "" {
				u, err := url.Parse(origin)
				scheme := "http"
				if r.TLS != nil {
					scheme = "https"
				}
				if err != nil || u.Host != r.Host || u.Scheme != scheme {
					http.Error(w, "Cross-origin form rejected", http.StatusForbidden)
					return
				}
			}
			if r.Header.Get("Sec-Fetch-Site") == "cross-site" {
				http.Error(w, "Cross-site form rejected", http.StatusForbidden)
				return
			}
			r.Body = http.MaxBytesReader(w, r.Body, 16<<10)
			if err := r.ParseForm(); err != nil {
				http.Error(w, "Invalid form", http.StatusBadRequest)
				return
			}
			if !hmac.Equal([]byte(r.PostForm.Get("csrf")), []byte(a.csrf)) {
				http.Error(w, "Reload the dashboard and try again", http.StatusForbidden)
				return
			}
		}
		mux.ServeHTTP(w, r)
	})), nil
}

func (a *adminHandler) sign(value string) string {
	mac := hmac.New(sha256.New, a.key)
	mac.Write([]byte(value))
	return hex.EncodeToString(mac.Sum(nil))
}
func (a *adminHandler) commandSignature(c adminCommand) string {
	b, _ := json.Marshal([]string{c.UUID, c.Action, c.Item, c.Reason})
	return a.sign(string(b))
}
func (a *adminHandler) render(w http.ResponseWriter, data adminPage) {
	data.CSRF = a.csrf
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := a.template.Execute(w, data); err != nil {
		a.server.Log.Error("render dashboard", "err", err)
	}
}
func (a *adminHandler) index(w http.ResponseWriter, r *http.Request) {
	page, err := strconv.Atoi(r.URL.Query().Get("page"))
	if err != nil || page < 0 {
		page = 0
	}
	if page > 100000 {
		http.Error(w, "Page out of range", 400)
		return
	}
	q := r.URL.Query().Get("q")
	if len(q) > 100 {
		http.Error(w, "Search is too long", 400)
		return
	}
	players, err := a.server.Store.SearchPlayers(r.Context(), q, page)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	audit, err := a.server.Store.ModerationLog(r.Context(), "")
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	message := ""
	if r.URL.Query().Get("updated") == "1" {
		message = "Change saved and recorded in the audit history."
	}
	a.render(w, adminPage{Query: q, Page: page, Next: page + 1, Previous: page - 1, Players: players, Audit: audit, Message: message})
}
func (a *adminHandler) player(w http.ResponseWriter, r *http.Request) {
	uuid, ok := auth.NormalizeUUID(r.PathValue("uuid"))
	if !ok {
		http.NotFound(w, r)
		return
	}
	me, err := a.server.me(r.Context(), uuid)
	if errors.Is(err, store.ErrNotFound) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	unlocks, err := a.server.Store.Unlocks(r.Context(), uuid)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	audit, err := a.server.Store.ModerationLog(r.Context(), uuid)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	claims := []adminClaim{}
	for _, u := range unlocks {
		c := adminClaim{Unlock: u, Name: u.AchievementID}
		if achievement, ok := a.server.Catalog.Get(u.AchievementID); ok {
			c.Name = achievement.Name
			c.Points = achievement.Points
		}
		claims = append(claims, c)
	}
	message := ""
	if r.URL.Query().Get("updated") == "1" {
		message = "Change saved and recorded in the audit history."
	}
	a.render(w, adminPage{Player: me, Claims: claims, Audit: audit, Achievements: a.server.Catalog.Achievements, Shop: a.server.Catalog.Shop, Developer: uuid == a.config.DeveloperUUID, Message: message})
}

// command validates every submitted value again on apply. Only the configured
// developer UUID can receive grants, even if someone edits a hidden form field.
func (a *adminHandler) command(r *http.Request) (adminCommand, []store.ModerationItem, error) {
	c := adminCommand{UUID: r.PostForm.Get("uuid"), Action: r.PostForm.Get("action"), Item: r.PostForm.Get("item"), Reason: strings.TrimSpace(r.PostForm.Get("reason"))}
	if len(c.Reason) < 3 || len(c.Reason) > 1000 {
		return c, nil, errors.New("provide a reason between 3 and 1000 characters")
	}
	// The only action without a player; apply hands it to ResetAllPurchases.
	if c.Action == "reset_all_purchases" {
		c.UUID, c.Item = "", "*"
		players, items, points, err := a.server.Store.PurchaseTotals(r.Context())
		if err != nil {
			return c, nil, err
		}
		if items == 0 {
			return c, nil, store.ErrNoChange
		}
		c.Description = fmt.Sprintf("Refund all %d purchases across %d players, returning %d points (free grants return nothing), and put every affected player's title slots back to their native style. Each player gets an audit entry.", items, players, points)
		return c, nil, nil
	}
	uuid, ok := auth.NormalizeUUID(c.UUID)
	if !ok {
		return c, nil, errors.New("invalid player UUID")
	}
	c.UUID = uuid
	ids := []string{c.Item}
	switch c.Action {
	case "revoke", "restore":
		unlocks, err := a.server.Store.Unlocks(r.Context(), uuid)
		if err != nil {
			return c, nil, err
		}
		found := false
		for _, u := range unlocks {
			if u.AchievementID == c.Item {
				found = true
				if u.Revoked != (c.Action == "restore") {
					return c, nil, store.ErrNoChange
				}
			}
		}
		if !found {
			return c, nil, errors.New("claim not found")
		}
		c.Description = c.Action + " achievement " + c.Item + ". Revocation removes its points and title words; paid purchases remain and balance may become negative. Restoring returns its rewards."
	case "reset_purchases":
		purchases, err := a.server.Store.Purchases(r.Context(), uuid)
		if err != nil {
			return c, nil, err
		}
		if len(purchases) == 0 {
			return c, nil, store.ErrNoChange
		}
		ids = nil
		paid := 0
		for _, p := range purchases {
			ids = append(ids, p.ItemID)
			paid += p.Price
		}
		c.Description = fmt.Sprintf("Refund all %d purchases, returning the %d points paid (free grants return nothing), and put both title slots back to their native style.", len(purchases), paid)
	case "grant_achievement", "grant_styles":
		if a.config.DeveloperUUID == "" || uuid != a.config.DeveloperUUID {
			return c, nil, errors.New("grants are restricted to WT_DEV_UUID")
		}
		if c.Action == "grant_achievement" {
			if c.Item == "*" {
				ids = nil
				for _, item := range a.server.Catalog.Achievements {
					ids = append(ids, item.ID)
				}
			} else if _, ok := a.server.Catalog.Get(c.Item); !ok {
				return c, nil, errors.New("unknown achievement")
			}
			c.Description = "Grant selected achievements to your developer account, including their points and title words. Existing claims and revocations are preserved. These grants count toward public rarity."
		} else {
			if c.Item == "*" {
				ids = nil
				for _, item := range a.server.Catalog.Shop {
					ids = append(ids, item.ID)
				}
			} else if _, ok := a.server.Catalog.ShopItem(c.Item); !ok {
				return c, nil, errors.New("unknown style")
			}
			c.Description = "Grant selected shop styles to your developer account at zero cost. Existing paid purchases are preserved. A purchase reset removes free grants and refunds zero points for them."
		}
	default:
		return c, nil, errors.New("unknown action")
	}
	items := make([]store.ModerationItem, 0, len(ids))
	for _, id := range ids {
		item := store.ModerationItem{ID: id}
		if c.Action == "grant_achievement" {
			achievement, _ := a.server.Catalog.Get(id)
			item.TriggerType = achievement.Trigger.Type
		}
		items = append(items, item)
	}
	return c, items, nil
}
func (a *adminHandler) preview(w http.ResponseWriter, r *http.Request) {
	c, _, err := a.command(r)
	if err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	c.Signature = a.commandSignature(c)
	if c.Action == "reset_all_purchases" {
		a.render(w, adminPage{Confirm: &c})
		return
	}
	me, err := a.server.me(r.Context(), c.UUID)
	if errors.Is(err, store.ErrNotFound) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	a.render(w, adminPage{Player: me, Confirm: &c})
}
func (a *adminHandler) apply(w http.ResponseWriter, r *http.Request) {
	a.server.mutationMu.Lock()
	defer a.server.mutationMu.Unlock()
	c, ids, err := a.command(r)
	if err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	if !hmac.Equal([]byte(r.PostForm.Get("signature")), []byte(a.commandSignature(c))) {
		http.Error(w, "Review and confirm the action first", 403)
		return
	}
	if c.Action == "reset_all_purchases" {
		a.resetAll(w, r, c)
		return
	}
	err = a.server.Store.Moderate(r.Context(), a.config.Username, c.UUID, c.Action, c.Reason, ids, a.server.Now())
	if errors.Is(err, store.ErrNoChange) {
		http.Error(w, err.Error(), 409)
		return
	}
	if errors.Is(err, store.ErrNotFound) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	// Finish cache updates even when the browser disconnects after the commit.
	me, err := a.server.me(context.WithoutCancel(r.Context()), c.UUID)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	a.server.Titles.Set(c.UUID, me.Title)
	a.server.invalidateStats()
	http.Redirect(w, r, "/player/"+c.UUID+"?updated=1", http.StatusSeeOther)
}

// resetAll runs under apply's mutation lock, after the signature check.
func (a *adminHandler) resetAll(w http.ResponseWriter, r *http.Request, c adminCommand) {
	uuids, err := a.server.Store.ResetAllPurchases(r.Context(), a.config.Username, c.Reason, a.server.Now())
	if errors.Is(err, store.ErrNoChange) {
		http.Error(w, err.Error(), 409)
		return
	}
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	// Finish cache updates even when the browser disconnects after the commit.
	ctx := context.WithoutCancel(r.Context())
	for _, uuid := range uuids {
		me, err := a.server.me(ctx, uuid)
		if err != nil {
			a.server.internalError(w, err)
			return
		}
		a.server.Titles.Set(uuid, me.Title)
	}
	a.server.invalidateStats()
	http.Redirect(w, r, "/?updated=1", http.StatusSeeOther)
}
