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
	"strings"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

const (
	maxBodyBytes     = 64 << 10
	maxEvidenceBytes = 4 << 10
	maxBatchUUIDs    = 500
)

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_]{1,16}$`)

type Server struct {
	Store      *store.Store
	Catalog    *catalog.Catalog
	Titles     *titles.Cache
	Challenges *auth.Challenges
	Verifier   auth.Verifier
	SessionTTL time.Duration
	Log        *slog.Logger
	Now        func() time.Time
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	mux.HandleFunc("GET /v1/catalog", s.getCatalog)
	mux.HandleFunc("POST /v1/auth/challenge", s.challenge)
	mux.HandleFunc("POST /v1/auth/verify", s.verify)
	mux.HandleFunc("POST /v1/auth/logout", s.authed(s.logout))
	mux.HandleFunc("GET /v1/me", s.authed(s.getMe))
	mux.HandleFunc("PUT /v1/me/title", s.authed(s.putTitle))
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
		words, err := s.availableWords(ctx, p.UUID)
		if err != nil {
			return err
		}
		s.Titles.Set(p.UUID, titles.Build(words, p.EquippedPrefix, p.EquippedSuffix))
	}
	return nil
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
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
	})
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
	UUID     string        `json:"uuid"`
	Username string        `json:"username"`
	Unlocked []unlockView  `json:"unlocked"`
	Points   int           `json:"points"`
	Words    titles.Words  `json:"words"`
	Equipped equippedView  `json:"equipped"`
	Title    *titles.Title `json:"title"`
}

type unlockView struct {
	AchievementID string `json:"achievementId"`
	ClaimedAt     int64  `json:"claimedAt"`
}

type equippedView struct {
	Prefix *string `json:"prefix"`
	Suffix *string `json:"suffix"`
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

	me := &meResponse{UUID: player.UUID, Username: player.Username, Unlocked: []unlockView{}}
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

	me.Words = titles.Available(s.Catalog, ids)
	me.Title = titles.Build(me.Words, player.EquippedPrefix, player.EquippedSuffix)
	// Report only what is actually displayed, so the mod never shows a stale
	// selection for a word that has since disappeared.
	if _, ok := me.Words.Prefix(player.EquippedPrefix); ok {
		me.Equipped.Prefix = &player.EquippedPrefix
	}
	if _, ok := me.Words.Suffix(player.EquippedSuffix); ok {
		me.Equipped.Suffix = &player.EquippedSuffix
	}
	return me, nil
}

func (s *Server) putTitle(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		Prefix *string `json:"prefix"`
		Suffix *string `json:"suffix"`
	}
	if !s.decode(w, r, &req) {
		return
	}

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

	if err := s.Store.SetEquipped(r.Context(), uuid, prefix, suffix); err != nil {
		s.internalError(w, err)
		return
	}
	s.Titles.Set(uuid, titles.Build(words, prefix, suffix))
	s.getMe(w, r, uuid)
}

func (s *Server) claim(w http.ResponseWriter, r *http.Request, uuid string) {
	var req struct {
		AchievementID string          `json:"achievementId"`
		TriggerType   string          `json:"triggerType"`
		Evidence      json.RawMessage `json:"evidence"`
		ModVersion    string          `json:"modVersion"`
	}
	if !s.decode(w, r, &req) {
		return
	}

	a, ok := s.Catalog.Get(req.AchievementID)
	if !ok {
		writeError(w, http.StatusNotFound, "unknown achievement")
		return
	}
	// A mismatch means the mod is running an outdated catalog.
	if req.TriggerType != a.Trigger.Type {
		writeError(w, http.StatusConflict, "trigger type does not match the catalog, update the mod")
		return
	}
	if len(req.Evidence) > maxEvidenceBytes {
		writeError(w, http.StatusRequestEntityTooLarge, "evidence is too large")
		return
	}

	inserted, err := s.Store.InsertUnlock(r.Context(), store.Unlock{
		UUID:          uuid,
		AchievementID: a.ID,
		TriggerType:   req.TriggerType,
		Evidence:      string(req.Evidence),
		ModVersion:    limit(req.ModVersion, 64),
		ClaimedAt:     s.Now(),
	})
	if err != nil {
		s.internalError(w, err)
		return
	}

	status := "unlocked"
	if !inserted {
		status = "alreadyUnlocked"
	} else {
		s.Log.Info("achievement unlocked", "uuid", uuid, "achievement", a.ID)
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": status, "achievementId": a.ID})
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
		uuid, err := s.Store.SessionUUID(r.Context(), auth.HashToken(token), s.Now())
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusUnauthorized, "session expired, log in again")
			return
		}
		if err != nil {
			s.internalError(w, err)
			return
		}
		next(w, r, uuid)
	}
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
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return false
	}
	return true
}

func (s *Server) internalError(w http.ResponseWriter, err error) {
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
