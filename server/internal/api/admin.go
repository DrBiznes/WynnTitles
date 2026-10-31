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
	"slices"
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
	started                time.Time
}

type adminClaim struct {
	store.Unlock
	Name   string
	Points int
	Flags  []string
}
type adminFeedClaim struct {
	store.ClaimRow
	Name  string
	Flags []string
}
type adminPlayer struct {
	store.PlayerRow
	Points int
}
type adminAchievement struct {
	catalog.Achievement
	Holders int
	Paused  bool
}
type adminPage struct {
	// View picks the page for routes that aren't a player or the search page.
	View                 string
	CSRF, Query, Message string
	Sort                 string
	SuspendedOnly        bool
	Page, Next, Previous int
	Players              []adminPlayer
	Player               *meResponse
	Record               *store.Player
	Claims               []adminClaim
	Feed                 []adminFeedClaim
	Flagged              bool
	Audit                []store.ModerationEntry
	Achievements         []catalog.Achievement
	Rarity               []adminAchievement
	Achievement          *adminAchievement
	Shop                 []catalog.ShopItem
	Developer            bool
	Confirm              *adminCommand
	Overview             store.Overview
	Controls             store.ClaimControls
	Version, Catalog     string
	Uptime               string
	Errors               int64
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
	a := &adminHandler{server: s, config: config, userHash: sha256.Sum256([]byte(config.Username)), passwordHash: sha256.Sum256([]byte(config.Password)), key: make([]byte, 32), started: s.Now()}
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
	funcs := template.FuncMap{
		"at": func(t time.Time) string { return t.UTC().Format("2006-01-02 15:04 UTC") },
		"mb": func(n int64) string { return fmt.Sprintf("%.1f MB", float64(n)/(1<<20)) },
	}
	var err error
	a.template, err = template.New("dashboard.html").Funcs(funcs).ParseFS(adminFiles, "admin/dashboard.html")
	if err != nil {
		return nil, err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", a.index)
	mux.HandleFunc("GET /player/{uuid}", a.player)
	mux.HandleFunc("GET /claims", a.claims)
	mux.HandleFunc("GET /achievements", a.achievements)
	mux.HandleFunc("GET /achievement/{id}", a.achievement)
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
	sort := r.URL.Query().Get("sort")
	suspended := r.URL.Query().Get("suspended") == "1"
	rows, err := a.server.Store.SearchPlayers(r.Context(), store.PlayerSearch{Query: q, Sort: sort, SuspendedOnly: suspended, Page: page})
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	players := make([]adminPlayer, 0, len(rows))
	for _, row := range rows {
		me, err := a.server.me(r.Context(), row.UUID)
		if err != nil {
			a.server.internalError(w, err)
			return
		}
		players = append(players, adminPlayer{PlayerRow: row, Points: me.Points})
	}
	audit, err := a.server.Store.ModerationLog(r.Context(), "")
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	overview, err := a.server.Store.Overview(r.Context(), a.server.Now())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	a.render(w, adminPage{Query: q, Sort: sort, SuspendedOnly: suspended, Page: page, Next: page + 1, Previous: page - 1, Players: players, Audit: audit, Message: savedMessage(r),
		Overview: overview, Controls: controls, Version: a.server.Version, Catalog: a.server.Catalog.Version, Uptime: a.server.Now().Sub(a.started).Round(time.Minute).String(), Errors: a.server.errors.Load()})
}

func savedMessage(r *http.Request) string {
	if r.URL.Query().Get("updated") == "1" {
		return "Change saved and recorded in the audit history."
	}
	return ""
}

// claimFlags names what looks wrong with a claim. Evidence comes from the client,
// so a clean claim proves nothing; these catch careless fakes and old builds.
func claimFlags(evidence, version string, controls store.ClaimControls) []string {
	if version == "dashboard" {
		return nil
	}
	flags := []string{}
	if _, ok := parseVersion(version); !ok {
		flags = append(flags, "Invalid mod version")
	} else {
		if controls.MinModVersion != "" && compareVersions(version, controls.MinModVersion) < 0 {
			flags = append(flags, "Below minimum mod version")
		}
		if controls.LatestModVersion != "" && compareVersions(version, controls.LatestModVersion) > 0 {
			flags = append(flags, "Unreleased mod version")
		}
	}
	if !json.Valid([]byte(evidence)) || !strings.HasPrefix(evidence, "{") || evidence == "{}" {
		flags = append(flags, "Missing or malformed evidence")
	}
	return flags
}

func (a *adminHandler) feedClaims(rows []store.ClaimRow, controls store.ClaimControls) []adminFeedClaim {
	feed := make([]adminFeedClaim, 0, len(rows))
	for _, row := range rows {
		c := adminFeedClaim{ClaimRow: row, Name: row.AchievementID, Flags: claimFlags(row.Evidence, row.ModVersion, controls)}
		if achievement, ok := a.server.Catalog.Get(row.AchievementID); ok {
			c.Name = achievement.Name
		}
		feed = append(feed, c)
	}
	return feed
}

// claims lists the newest claims. Flags are worked out here, so the flagged view
// covers a bounded window of recent claims rather than the whole table.
func (a *adminHandler) claims(w http.ResponseWriter, r *http.Request) {
	flagged := r.URL.Query().Get("flagged") == "1"
	limit := 200
	if flagged {
		limit = 2000
	}
	rows, err := a.server.Store.RecentClaims(r.Context(), limit)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	feed := a.feedClaims(rows, controls)
	if flagged {
		feed = slices.DeleteFunc(feed, func(c adminFeedClaim) bool { return len(c.Flags) == 0 })
		feed = feed[:min(len(feed), 200)]
	}
	a.render(w, adminPage{View: "claims", Feed: feed, Flagged: flagged})
}

func (a *adminHandler) achievements(w http.ResponseWriter, r *http.Request) {
	_, counts, err := a.server.Store.UnlockCounts(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	rarity := make([]adminAchievement, 0, len(a.server.Catalog.Achievements))
	for _, item := range a.server.Catalog.Achievements {
		rarity = append(rarity, adminAchievement{Achievement: item, Holders: counts[item.ID], Paused: controls.AchievementPaused(item.ID)})
	}
	a.render(w, adminPage{View: "achievements", Rarity: rarity, Controls: controls})
}

func (a *adminHandler) achievement(w http.ResponseWriter, r *http.Request) {
	item, ok := a.server.Catalog.Get(r.PathValue("id"))
	if !ok {
		http.NotFound(w, r)
		return
	}
	page, err := strconv.Atoi(r.URL.Query().Get("page"))
	if err != nil || page < 0 || page > 100000 {
		page = 0
	}
	rows, err := a.server.Store.AchievementClaims(r.Context(), item.ID, page)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	holders, err := a.server.Store.ActiveClaimCount(r.Context(), item.ID)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	a.render(w, adminPage{View: "achievement", Achievement: &adminAchievement{Achievement: *item, Holders: holders, Paused: controls.AchievementPaused(item.ID)}, Feed: a.feedClaims(rows, controls), Page: page, Next: page + 1, Previous: page - 1, Message: savedMessage(r)})
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
	record, err := a.server.Store.Player(r.Context(), uuid)
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	claims := []adminClaim{}
	for _, u := range unlocks {
		c := adminClaim{Unlock: u, Name: u.AchievementID, Flags: claimFlags(u.Evidence, u.ModVersion, controls)}
		if achievement, ok := a.server.Catalog.Get(u.AchievementID); ok {
			c.Name = achievement.Name
			c.Points = achievement.Points
		}
		claims = append(claims, c)
	}
	a.render(w, adminPage{Player: me, Record: &record, Claims: claims, Audit: audit, Achievements: a.server.Catalog.Achievements, Shop: a.server.Catalog.Shop, Developer: uuid == a.config.DeveloperUUID, Message: savedMessage(r)})
}

// controlChange works out new claim controls for a control action. It returns
// false for any other action.
func controlChange(controls store.ClaimControls, action, item string, cat *catalog.Catalog) (store.ClaimControls, string, bool, error) {
	next := controls
	next.PausedAchievements = slices.Clone(controls.PausedAchievements)
	description := ""
	switch action {
	case "pause_claims":
		if controls.Paused {
			return next, "", true, store.ErrNoChange
		}
		next.Paused = true
		description = "Stop accepting every achievement claim. The mod keeps retrying, so nothing is lost and claims land once you resume."
	case "resume_claims":
		if !controls.Paused {
			return next, "", true, store.ErrNoChange
		}
		next.Paused = false
		description = "Start accepting claims again."
	case "pause_achievement", "resume_achievement":
		if _, ok := cat.Get(item); !ok {
			return next, "", true, errors.New("unknown achievement")
		}
		pausing := action == "pause_achievement"
		if pausing == controls.AchievementPaused(item) {
			return next, "", true, store.ErrNoChange
		}
		if pausing {
			next.PausedAchievements = append(next.PausedAchievements, item)
			description = "Stop accepting claims for " + item + ". Existing holders keep it. The mod keeps retrying until you resume."
		} else {
			next.PausedAchievements = slices.DeleteFunc(next.PausedAchievements, func(id string) bool { return id == item })
			description = "Start accepting claims for " + item + " again."
		}
	case "set_min_mod_version", "set_latest_mod_version":
		if _, ok := parseVersion(item); item != "" && !ok {
			return next, "", true, errors.New("a version looks like 1.2.3, or leave it blank to turn this off")
		}
		field := &next.MinModVersion
		description = "Refuse claims from mods older than " + item + ". Those players keep their titles and need to update to earn more."
		if action == "set_latest_mod_version" {
			field = &next.LatestModVersion
			description = "Flag claims from mods newer than " + item + " as unreleased. Nothing is refused."
		}
		if *field == item {
			return next, "", true, store.ErrNoChange
		}
		*field = item
		if item == "" {
			description = "Turn this version check off."
		}
	default:
		return next, "", false, nil
	}
	return next, description, true, nil
}

// command validates every submitted value again on apply. Only the configured
// developer UUID can receive grants, even if someone edits a hidden form field.
func (a *adminHandler) command(r *http.Request) (adminCommand, []store.ModerationItem, error) {
	c := adminCommand{UUID: r.PostForm.Get("uuid"), Action: r.PostForm.Get("action"), Item: strings.TrimSpace(r.PostForm.Get("item")), Reason: strings.TrimSpace(r.PostForm.Get("reason"))}
	if len(c.Reason) < 3 || len(c.Reason) > 1000 {
		return c, nil, errors.New("provide a reason between 3 and 1000 characters")
	}
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		return c, nil, err
	}
	if _, description, ok, err := controlChange(controls, c.Action, c.Item, a.server.Catalog); ok {
		c.UUID, c.Description = "", description
		return c, nil, err
	}
	if c.Action == "clear_achievement" {
		c.UUID = ""
		if _, ok := a.server.Catalog.Get(c.Item); !ok {
			return c, nil, errors.New("unknown achievement")
		}
		holders, err := a.server.Store.ActiveClaimCount(r.Context(), c.Item)
		if err != nil {
			return c, nil, err
		}
		if holders == 0 {
			return c, nil, store.ErrNoChange
		}
		c.Description = fmt.Sprintf("Delete %d active claims of %s so those players can earn it again. Their points and words go with it, so balances may go negative. Each claim is copied into that player's audit entry. Revoked claims stay blocked. If the detector is still broken, pause this achievement first or it will be claimed again.", holders, c.Item)
		return c, nil, nil
	}
	// The other action without a player; apply hands it to ResetAllPurchases.
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
	case "revoke_all", "restore_all", "suspend", "unsuspend":
		player, err := a.server.Store.Player(r.Context(), uuid)
		if err != nil {
			return c, nil, err
		}
		if (c.Action == "suspend" && player.Suspended) || (c.Action == "unsuspend" && !player.Suspended) {
			return c, nil, store.ErrNoChange
		}
		unlocks, err := a.server.Store.Unlocks(r.Context(), uuid)
		if err != nil {
			return c, nil, err
		}
		ids = nil
		if c.Action != "unsuspend" && (c.Action != "suspend" || c.Item == "revoke_all") {
			for _, u := range unlocks {
				if u.Revoked == (c.Action == "restore_all") {
					ids = append(ids, u.AchievementID)
				}
			}
		}
		switch c.Action {
		case "revoke_all", "restore_all":
			if len(ids) == 0 {
				return c, nil, store.ErrNoChange
			}
			c.Description = fmt.Sprintf("Revoke all %d active claims. Each stays blocked until restored. Paid purchases remain and balance may become negative.", len(ids))
			if c.Action == "restore_all" {
				c.Description = fmt.Sprintf("Restore all %d revoked claims, including any revoked one at a time for other reasons.", len(ids))
			}
		case "suspend":
			c.Description = fmt.Sprintf("Hide this player's title, shut them out of the API and leave them out of rarity. Also revokes %d claims. Their records are kept.", len(ids))
		case "unsuspend":
			c.Description = "Give this player access again. Their title returns, and revoked claims stay revoked until restored."
		}
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
	if c.UUID == "" {
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
	switch c.Action {
	case "reset_all_purchases":
		a.resetAll(w, r, c)
		return
	case "clear_achievement":
		a.clearAchievement(w, r, c)
		return
	}
	if c.UUID == "" {
		a.setControls(w, r, c)
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
	a.server.Titles.Set(c.UUID, me.PublicTitle())
	a.server.invalidateStats()
	http.Redirect(w, r, "/player/"+c.UUID+"?updated=1", http.StatusSeeOther)
}

// setControls runs under apply's mutation lock, after the signature check.
func (a *adminHandler) setControls(w http.ResponseWriter, r *http.Request, c adminCommand) {
	controls, err := a.server.Store.ClaimControls(r.Context())
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	next, _, _, err := controlChange(controls, c.Action, c.Item, a.server.Catalog)
	if err != nil {
		http.Error(w, err.Error(), 409)
		return
	}
	if err := a.server.Store.SetClaimControls(r.Context(), a.config.Username, c.Action, c.Reason, next, a.server.Now()); err != nil {
		a.server.internalError(w, err)
		return
	}
	target := "/?updated=1"
	if c.Action == "pause_achievement" || c.Action == "resume_achievement" {
		target = "/achievement/" + url.PathEscape(c.Item) + "?updated=1"
	}
	http.Redirect(w, r, target, http.StatusSeeOther)
}

// clearAchievement runs under apply's mutation lock, after the signature check.
func (a *adminHandler) clearAchievement(w http.ResponseWriter, r *http.Request, c adminCommand) {
	uuids, err := a.server.Store.ClearAchievement(r.Context(), a.config.Username, c.Item, c.Reason, a.server.Now())
	if errors.Is(err, store.ErrNoChange) {
		http.Error(w, err.Error(), 409)
		return
	}
	if err != nil {
		a.server.internalError(w, err)
		return
	}
	if a.refreshPlayers(w, r, uuids) {
		http.Redirect(w, r, "/achievement/"+url.PathEscape(c.Item)+"?updated=1", http.StatusSeeOther)
	}
}

// refreshPlayers rebuilds public titles after a change to many players, and
// finishes even when the browser disconnects after the commit.
func (a *adminHandler) refreshPlayers(w http.ResponseWriter, r *http.Request, uuids []string) bool {
	ctx := context.WithoutCancel(r.Context())
	for _, uuid := range uuids {
		me, err := a.server.me(ctx, uuid)
		if err != nil {
			a.server.internalError(w, err)
			return false
		}
		a.server.Titles.Set(uuid, me.PublicTitle())
	}
	a.server.invalidateStats()
	return true
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
	if a.refreshPlayers(w, r, uuids) {
		http.Redirect(w, r, "/?updated=1", http.StatusSeeOther)
	}
}
