// Package api exposes the HTTP API the mod talks to.
package api

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

const (
	maxBodyBytes      = 64 << 10
	maxClaimBodyBytes = 1 << 20
	maxEvidenceBytes  = 4 << 10
	maxBatchUUIDs     = 500
	maxBatchClaims    = 200
	// How stale last_seen_at may get, so most requests don't write to the database.
	lastSeenPrecision = time.Hour
)

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_]{1,16}$`)

type Server struct {
	Store      *store.Store
	Catalog    *catalog.Catalog
	Titles     *titles.Cache
	Challenges *auth.Challenges
	Verifier   auth.Verifier
	SessionTTL time.Duration
	// StatsTTL is how long achievement stats are reused before counting again;
	// zero counts on every request.
	StatsTTL time.Duration
	Log      *slog.Logger
	Now      func() time.Time
	// Version is the build shown on the dashboard.
	Version string
	// AppealURL is where the suspension message sends players, if set.
	AppealURL string

	// errors counts internal errors since startup, for the dashboard.
	errors  atomic.Int64
	statsMu sync.Mutex
	stats   *statsResponse
	statsAt time.Time
	// Serialize account mutations with moderation so an older in-flight title
	// update cannot put a revoked reward back into the public cache.
	mutationMu sync.Mutex
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	mux.HandleFunc("GET /v1/catalog", s.getCatalog)
	mux.HandleFunc("GET /v1/stats/achievements", s.getAchievementStats)
	mux.HandleFunc("POST /v1/auth/challenge", s.challenge)
	mux.HandleFunc("POST /v1/auth/verify", s.verify)
	mux.HandleFunc("POST /v1/auth/logout", s.authed(s.logout))
	mux.HandleFunc("GET /v1/me", s.authed(s.getMe))
	mux.HandleFunc("PUT /v1/me/title", s.authed(s.putTitle))
	mux.HandleFunc("PUT /v1/me/style", s.authed(s.putStyle))
	mux.HandleFunc("POST /v1/shop/buy", s.authed(s.buy))
	mux.HandleFunc("POST /v1/claims", s.authed(s.claim))
	mux.HandleFunc("POST /v1/titles/batch", s.batchTitles)
	return s.recoverer(mux)
}

// LoadTitles fills the title cache from the database. Titles are rebuilt from
// the current catalog, so formatting changes apply on restart.
func (s *Server) LoadTitles(ctx context.Context) error {
	players, err := s.Store.PlayersWithTitles(ctx)
	if err != nil {
		return err
	}
	for _, p := range players {
		me, err := s.me(ctx, p.UUID)
		if err != nil {
			return err
		}
		s.Titles.Set(p.UUID, me.PublicTitle())
	}
	return nil
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := s.Store.Ping(ctx); err != nil {
		s.Log.Error("health check", "err", err)
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{"ok": false})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "titles": s.Titles.Len()})
}

func (s *Server) getCatalog(w http.ResponseWriter, r *http.Request) {
	etag := `"` + s.Catalog.Version + `"`
	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", "public, max-age=300")
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"version":      s.Catalog.Version,
		"achievements": s.Catalog.Achievements,
		"shop":         s.Catalog.Shop,
	})
}

type statsResponse struct {
	// Players is everyone who has logged in with the mod.
	Players int `json:"players"`
	// Unlocks has every catalog achievement, mapped to how many players hold it.
	Unlocks map[string]int `json:"unlocks"`
}

// getAchievementStats reports how many players hold each achievement, for the
// rarity the mod shows. Counting scans every unlock, so the result is reused
// for StatsTTL.
func (s *Server) getAchievementStats(w http.ResponseWriter, r *http.Request) {
	stats, err := s.achievementStats(r.Context())
	if err != nil {
		s.internalError(w, err)
		return
	}
	w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(s.StatsTTL.Seconds())))
	writeJSON(w, http.StatusOK, stats)
}

func (s *Server) achievementStats(ctx context.Context) (*statsResponse, error) {
	s.statsMu.Lock()
	defer s.statsMu.Unlock()
	now := s.Now()
	if s.stats != nil && now.Sub(s.statsAt) < s.StatsTTL {
		return s.stats, nil
	}

	players, counts, err := s.Store.UnlockCounts(ctx)
	if err != nil {
		return nil, err
	}
	stats := &statsResponse{Players: players, Unlocks: make(map[string]int, len(s.Catalog.Achievements))}
	for _, a := range s.Catalog.Achievements {
		stats.Unlocks[a.ID] = counts[a.ID]
	}
	s.stats, s.statsAt = stats, now
	return stats, nil
}

func (s *Server) invalidateStats() {
	s.statsMu.Lock()
	s.stats = nil
	s.statsMu.Unlock()
}

func (s *Server) challenge(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username string `json:"username"`
	}
	if !s.decode(w, r, &req) {
		return
	}
	if !usernamePattern.MatchString(req.Username) {
		writeError(w, http.StatusBadRequest, "invalid username")
		return
	}

	serverID, err := s.Challenges.Issue(req.Username, s.Now())
	if err != nil {
		writeError(w, http.StatusServiceUnavailable, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"serverId": serverID})
}

func (s *Server) verify(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username string `json:"username"`
		ServerID string `json:"serverId"`
	}
	if !s.decode(w, r, &req) {
		return
	}
	if !s.Challenges.Consume(req.ServerID, req.Username, s.Now()) {
		writeError(w, http.StatusUnauthorized, "unknown or expired challenge")
		return
	}

	profile, err := s.Verifier.HasJoined(r.Context(), req.Username, req.ServerID)
	if errors.Is(err, auth.ErrNotJoined) {
		writeError(w, http.StatusUnauthorized, "mojang could not verify this account")
		return
	}
	if err != nil {
		s.Log.Error("verify with mojang", "err", err)
		writeError(w, http.StatusBadGateway, "could not reach mojang, try again later")
		return
	}

	now := s.Now()
	if err := s.Store.UpsertPlayer(r.Context(), profile.UUID, profile.Username, now); err != nil {
		s.internalError(w, err)
		return
	}
	token, hash := auth.NewToken()
	expires := now.Add(s.SessionTTL)
	if err := s.Store.CreateSession(r.Context(), hash, profile.UUID, now, expires); err != nil {
		s.internalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"token":     token,
		"uuid":      profile.UUID,
		"username":  profile.Username,
		"expiresAt": expires.UnixMilli(),
	})
}

func (s *Server) logout(w http.ResponseWriter, r *http.Request, uuid string) {
	if err := s.Store.DeleteSession(r.Context(), auth.HashToken(bearer(r))); err != nil {
		s.internalError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

type meResponse struct {
	UUID     string       `json:"uuid"`
	Username string       `json:"username"`
	Unlocked []unlockView `json:"unlocked"`
	// Points is everything earned from achievements; Balance is what is left
	// to spend in the shop.
	Points   int           `json:"points"`
	Balance  int           `json:"balance"`
	Owned    []string      `json:"owned"`
	Words    titles.Words  `json:"words"`
	Equipped equippedView  `json:"equipped"`
	Styles   stylesView    `json:"styles"`
	Title    *titles.Title `json:"title"`

	suspended bool
}

// PublicTitle is what other players may see. Every write to the title cache goes
// through it, so a suspended player's title stays hidden across restarts and
// in-flight requests.
func (m *meResponse) PublicTitle() *titles.Title {
	if m.suspended {
		return nil
	}
	return m.Title
}

type unlockView struct {
	AchievementID string `json:"achievementId"`
	ClaimedAt     int64  `json:"claimedAt"`
}

type equippedView struct {
	Prefix *string `json:"prefix"`
	Suffix *string `json:"suffix"`
}

// stylesView has a slot's shop style, or null for the word's native style.
type stylesView struct {
	Prefix *catalog.Style `json:"prefix"`
	Suffix *catalog.Style `json:"suffix"`
}

func (s *Server) getMe(w http.ResponseWriter, r *http.Request, uuid string) {
	me, err := s.me(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, me)
}

func (s *Server) me(ctx context.Context, uuid string) (*meResponse, error) {
	player, err := s.Store.Player(ctx, uuid)
	if err != nil {
		return nil, err
	}
	unlocks, err := s.Store.Unlocks(ctx, uuid)
	if err != nil {
		return nil, err
	}

	purchases, err := s.Store.Purchases(ctx, uuid)
	if err != nil {
		return nil, err
	}

	me := &meResponse{UUID: player.UUID, Username: player.Username, Unlocked: []unlockView{}, Owned: []string{}, suspended: player.Suspended}
	var ids []string
	for _, u := range unlocks {
		a, ok := s.Catalog.Get(u.AchievementID)
		if u.Revoked || !ok {
			continue
		}
		ids = append(ids, u.AchievementID)
		me.Points += a.Points
		me.Unlocked = append(me.Unlocked, unlockView{AchievementID: u.AchievementID, ClaimedAt: u.ClaimedAt.UnixMilli()})
	}

	// Purchases of items since removed from the shop still count as spent
	// until a dashboard reset refunds them.
	owned := map[string]bool{}
	me.Balance = me.Points
	for _, p := range purchases {
		me.Balance -= p.Price
		if _, ok := s.Catalog.ShopItem(p.ItemID); ok {
			owned[p.ItemID] = true
			me.Owned = append(me.Owned, p.ItemID)
		}
	}

	me.Words = titles.Available(s.Catalog, ids)
	// Report only what is actually displayed, so the mod never shows a stale
	// selection for a word or style that has since disappeared.
	me.Styles.Prefix = s.wornStyle(player.PrefixStyle, owned)
	me.Styles.Suffix = s.wornStyle(player.SuffixStyle, owned)
	me.Title = titles.Build(s.Catalog, me.Words,
		titles.Slot{Word: player.EquippedPrefix, Style: me.Styles.Prefix},
		titles.Slot{Word: player.EquippedSuffix, Style: me.Styles.Suffix})
	if _, ok := me.Words.Prefix(player.EquippedPrefix); ok {
		me.Equipped.Prefix = &player.EquippedPrefix
	}
	if _, ok := me.Words.Suffix(player.EquippedSuffix); ok {
		me.Equipped.Suffix = &player.EquippedSuffix
	}
	return me, nil
}

// wornStyle decodes a stored style, dropping it if the catalog no longer
// allows it or the player no longer owns every item in it.
func (s *Server) wornStyle(stored string, owned map[string]bool) *catalog.Style {
	if stored == "" {
		return nil
	}
	var style catalog.Style
	if err := json.Unmarshal([]byte(stored), &style); err != nil || s.Catalog.CheckStyle(&style) != nil {
		return nil
	}
	for _, id := range style.Items() {
		if !owned[id] {
			return nil
		}
	}
	return &style
}

// writeMe answers with the player's profile after a change, and updates the
// public title to match.
func (s *Server) writeMe(w http.ResponseWriter, r *http.Request, uuid string) {
	me, err := s.me(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	s.Titles.Set(uuid, me.PublicTitle())
	writeJSON(w, http.StatusOK, me)
}

func (s *Server) putTitle(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		Prefix *string `json:"prefix"`
		Suffix *string `json:"suffix"`
	}
	if !s.decode(w, r, &req) {
		return
	}

	s.mutationMu.Lock()
	defer s.mutationMu.Unlock()

	words, err := s.availableWords(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	prefix, suffix := deref(req.Prefix), deref(req.Suffix)
	if _, ok := words.Prefix(prefix); prefix != "" && !ok {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("prefix %q is not unlocked", prefix))
		return
	}
	if _, ok := words.Suffix(suffix); suffix != "" && !ok {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("suffix %q is not unlocked", suffix))
		return
	}

	me, err := s.me(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	p, _ := words.Prefix(prefix)
	q, _ := words.Suffix(suffix)
	for _, slot := range []struct {
		word  catalog.Word
		style *catalog.Style
	}{{p, me.Styles.Prefix}, {q, me.Styles.Suffix}} {
		if err := s.Catalog.CheckWordStyle(slot.word, slot.style); err != nil {
			writeError(w, http.StatusBadRequest, err.Error()+"; change the font on the Styles tab first")
			return
		}
	}

	if err := s.Store.SetEquipped(r.Context(), uuid, prefix, suffix); err != nil {
		s.internalError(w, err)
		return
	}
	s.writeMe(w, r, uuid)
}

// putStyle sets how each slot is styled. A null slot goes back to the word's
// native style; every item in a style must be owned.
func (s *Server) putStyle(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		Prefix *catalog.Style `json:"prefix"`
		Suffix *catalog.Style `json:"suffix"`
	}
	if !s.decode(w, r, &req) {
		return
	}

	s.mutationMu.Lock()
	defer s.mutationMu.Unlock()

	me, err := s.me(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	owned := map[string]bool{}
	for _, id := range me.Owned {
		owned[id] = true
	}

	var stored [2]string
	for i, slot := range []struct {
		name  string
		style *catalog.Style
	}{{"prefix", req.Prefix}, {"suffix", req.Suffix}} {
		if slot.style == nil {
			continue
		}
		if err := s.Catalog.CheckStyle(slot.style); err != nil {
			writeError(w, http.StatusBadRequest, slot.name+" style: "+err.Error())
			return
		}
		var word catalog.Word
		if i == 0 {
			word, _ = me.Words.Prefix(deref(me.Equipped.Prefix))
		} else {
			word, _ = me.Words.Suffix(deref(me.Equipped.Suffix))
		}
		if err := s.Catalog.CheckWordStyle(word, slot.style); err != nil {
			writeError(w, http.StatusBadRequest, slot.name+" style: "+err.Error())
			return
		}
		for _, id := range slot.style.Items() {
			if !owned[id] {
				writeError(w, http.StatusBadRequest, fmt.Sprintf("%s style: %q is not owned", slot.name, id))
				return
			}
		}
		b, err := json.Marshal(slot.style)
		if err != nil {
			s.internalError(w, err)
			return
		}
		stored[i] = string(b)
	}

	if err := s.Store.SetStyles(r.Context(), uuid, stored[0], stored[1]); err != nil {
		s.internalError(w, err)
		return
	}
	s.writeMe(w, r, uuid)
}

// buy spends points on shop items, all or nothing, so trying on a whole style
// and buying it is one step.
func (s *Server) buy(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		ItemIDs []string `json:"itemIds"`
	}
	if !s.decode(w, r, &req) {
		return
	}

	s.mutationMu.Lock()
	defer s.mutationMu.Unlock()
	if len(req.ItemIDs) == 0 {
		writeError(w, http.StatusBadRequest, "nothing to buy")
		return
	}

	var order []store.Purchase
	seen := map[string]bool{}
	for _, id := range req.ItemIDs {
		item, ok := s.Catalog.ShopItem(id)
		if !ok {
			writeError(w, http.StatusNotFound, fmt.Sprintf("unknown shop item %q", id))
			return
		}
		if !seen[id] {
			seen[id] = true
			order = append(order, store.Purchase{ItemID: id, Price: item.Price})
		}
	}

	me, err := s.me(r.Context(), uuid)
	if err != nil {
		s.internalError(w, err)
		return
	}
	err = s.Store.Buy(r.Context(), uuid, order, me.Points, s.Now())
	switch {
	case errors.Is(err, store.ErrAlreadyOwned):
		writeError(w, http.StatusConflict, "already owned")
		return
	case errors.Is(err, store.ErrNotEnoughPoints):
		writeError(w, http.StatusBadRequest, "not enough points")
		return
	case err != nil:
		s.internalError(w, err)
		return
	}
	s.Log.Info("shop purchase", "uuid", uuid, "items", req.ItemIDs)
	s.writeMe(w, r, uuid)
}

// claimResult is one claim's outcome: "unlocked", "alreadyUnlocked", or one that
// records nothing and does not fail the others: "unknown", "outdated" (the mod's
// catalog is older than the server's), "tooLarge", "updateRequired" (below the
// dashboard's minimum mod version) or "paused". The mod retries all of these later.
type claimResult struct {
	AchievementID string `json:"achievementId"`
	Status        string `json:"status"`
}

// claim records any number of claims in one transaction. A first login meets dozens
// of achievements at once, and one transaction per claim would hold up everyone
// else's writes behind the mutation lock.
func (s *Server) claim(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		Claims []struct {
			AchievementID string          `json:"achievementId"`
			TriggerType   string          `json:"triggerType"`
			Evidence      json.RawMessage `json:"evidence"`
		} `json:"claims"`
		ModVersion string `json:"modVersion"`
	}
	if !s.decodeLimit(w, r, &req, maxClaimBodyBytes) {
		return
	}
	if len(req.Claims) == 0 {
		writeError(w, http.StatusBadRequest, "nothing to claim, update the mod")
		return
	}
	if len(req.Claims) > maxBatchClaims {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("at most %d claims per request", maxBatchClaims))
		return
	}

	s.mutationMu.Lock()
	defer s.mutationMu.Unlock()

	controls, err := s.Store.ClaimControls(r.Context())
	if err != nil {
		s.internalError(w, err)
		return
	}
	updateRequired := controls.MinModVersion != "" && compareVersions(req.ModVersion, controls.MinModVersion) < 0

	results := make([]claimResult, len(req.Claims))
	var unlocks []store.Unlock
	var valid []int
	now := s.Now()
	for i, c := range req.Claims {
		results[i].AchievementID = c.AchievementID
		a, ok := s.Catalog.Get(c.AchievementID)
		switch {
		case !ok:
			results[i].Status = "unknown"
		case c.TriggerType != a.Trigger.Type:
			results[i].Status = "outdated"
		case len(c.Evidence) > maxEvidenceBytes:
			results[i].Status = "tooLarge"
		case updateRequired:
			results[i].Status = "updateRequired"
		case controls.Paused || controls.AchievementPaused(a.ID):
			results[i].Status = "paused"
		default:
			valid = append(valid, i)
			unlocks = append(unlocks, store.Unlock{
				UUID:          uuid,
				AchievementID: a.ID,
				TriggerType:   c.TriggerType,
				Evidence:      string(c.Evidence),
				ModVersion:    limit(req.ModVersion, 64),
				ClaimedAt:     now,
			})
		}
	}

	inserted, err := s.Store.InsertUnlocks(r.Context(), unlocks)
	if err != nil {
		s.internalError(w, err)
		return
	}
	newly := 0
	for n, i := range valid {
		results[i].Status = "alreadyUnlocked"
		if inserted[n] {
			results[i].Status = "unlocked"
			newly++
		}
	}
	if newly > 0 {
		s.Log.Info("achievements unlocked", "uuid", uuid, "count", newly)
	}
	writeJSON(w, http.StatusOK, map[string]any{"results": results})
}

func (s *Server) batchTitles(w http.ResponseWriter, r *http.Request) {
	var req struct {
		UUIDs []string `json:"uuids"`
	}
	if !s.decode(w, r, &req) {
		return
	}
	if len(req.UUIDs) > maxBatchUUIDs {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("at most %d uuids per request", maxBatchUUIDs))
		return
	}

	uuids := make([]string, 0, len(req.UUIDs))
	for _, raw := range req.UUIDs {
		if uuid, ok := auth.NormalizeUUID(raw); ok {
			uuids = append(uuids, uuid)
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"titles": s.Titles.Lookup(uuids)})
}

func (s *Server) availableWords(ctx context.Context, uuid string) (titles.Words, error) {
	unlocks, err := s.Store.Unlocks(ctx, uuid)
	if err != nil {
		return titles.Words{}, err
	}
	var ids []string
	for _, u := range unlocks {
		if !u.Revoked {
			ids = append(ids, u.AchievementID)
		}
	}
	return titles.Available(s.Catalog, ids), nil
}

type authedHandler func(w http.ResponseWriter, r *http.Request, uuid string)

func (s *Server) authed(next authedHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := bearer(r)
		if token == "" {
			writeError(w, http.StatusUnauthorized, "missing session token")
			return
		}
		now := s.Now()
		session, err := s.Store.Session(r.Context(), auth.HashToken(token), now)
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusUnauthorized, "session expired, log in again")
			return
		}
		if err != nil {
			s.internalError(w, err)
			return
		}
		if now.Sub(session.LastSeenAt) > lastSeenPrecision {
			if err := s.Store.TouchPlayer(r.Context(), session.UUID, now); err != nil {
				s.Log.Error("touch player", "err", err)
			}
		}
		// 403, not 401: the mod answers a 401 with a fresh Mojang login.
		if session.Suspended {
			message := "this WynnTitles account is suspended"
			if s.AppealURL != "" {
				message += "; appeal at " + s.AppealURL
			}
			writeError(w, http.StatusForbidden, message)
			return
		}
		next(w, r, session.UUID)
	}
}

// compareVersions orders dotted version numbers, ignoring any suffix after - or
// +. A version that doesn't parse sorts below every real one.
func compareVersions(a, b string) int {
	x, okX := parseVersion(a)
	y, okY := parseVersion(b)
	switch {
	case !okX && !okY:
		return 0
	case !okX:
		return -1
	case !okY:
		return 1
	}
	return slices.Compare(x, y)
}

func parseVersion(version string) ([]int, bool) {
	version, _, _ = strings.Cut(version, "+")
	version, _, _ = strings.Cut(version, "-")
	parts := strings.Split(version, ".")
	if len(parts) != 3 {
		return nil, false
	}
	numbers := make([]int, len(parts))
	for i, part := range parts {
		n, err := strconv.Atoi(part)
		if err != nil || n < 0 {
			return nil, false
		}
		numbers[i] = n
	}
	return numbers, true
}

func (s *Server) recoverer(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				s.Log.Error("panic", "path", r.URL.Path, "panic", v)
				writeError(w, http.StatusInternalServerError, "internal error")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

func (s *Server) decode(w http.ResponseWriter, r *http.Request, v any) bool {
	return s.decodeLimit(w, r, v, maxBodyBytes)
}

func (s *Server) decodeLimit(w http.ResponseWriter, r *http.Request, v any, maxBytes int64) bool {
	r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return false
	}
	return true
}

func (s *Server) internalError(w http.ResponseWriter, err error) {
	s.errors.Add(1)
	s.Log.Error("request failed", "err", err)
	writeError(w, http.StatusInternalServerError, "internal error")
}

func bearer(r *http.Request) string {
	token, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
	if !ok {
		return ""
	}
	return strings.TrimSpace(token)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func limit(s string, n int) string {
	if len(s) > n {
		return s[:n]
	}
	return s
}
