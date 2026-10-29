package api

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

const adminPassword = "test-only-password-at-least-24-chars"
const otherUUID = "00000000-0000-4000-8000-000000000001"

func adminFixture(t *testing.T) (*Server, http.Handler) {
	t.Helper()
	db, err := store.Open(filepath.Join(t.TempDir(), "admin.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	cat, err := catalog.Load()
	if err != nil {
		t.Fatal(err)
	}
	s := &Server{Store: db, Catalog: cat, Titles: titles.NewCache(), StatsTTL: time.Hour, Now: time.Now, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	for _, p := range []struct{ uuid, name string }{{testUUID, "Notch"}, {otherUUID, "Other_Player"}} {
		if err := db.UpsertPlayer(context.Background(), p.uuid, p.name, time.Now()); err != nil {
			t.Fatal(err)
		}
	}
	h, err := s.AdminHandler(AdminConfig{Username: "dev", Password: adminPassword, DeveloperUUID: testUUID})
	if err != nil {
		t.Fatal(err)
	}
	return s, h
}
func adminRequest(h http.Handler, method, path string, form url.Values, authenticated bool) *httptest.ResponseRecorder {
	var body io.Reader
	if form != nil {
		body = strings.NewReader(form.Encode())
	}
	r := httptest.NewRequest(method, path, body)
	r.RemoteAddr = "127.0.0.1:1234"
	if form != nil {
		r.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		r.Header.Set("Origin", "http://example.com")
	}
	if authenticated {
		r.SetBasicAuth("dev", adminPassword)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}
func field(t *testing.T, body, name string) string {
	t.Helper()
	m := regexp.MustCompile(`name="` + name + `" value="([^"]+)"`).FindStringSubmatch(body)
	if len(m) != 2 {
		t.Fatalf("missing form field %s in %s", name, body)
	}
	return m[1]
}
func adminAction(t *testing.T, h http.Handler, uuid, action, item, reason string) int {
	t.Helper()
	page := adminRequest(h, "GET", "/player/"+testUUID, nil, true)
	if page.Code != 200 {
		t.Fatalf("player page %d: %s", page.Code, page.Body.String())
	}
	values := url.Values{"csrf": {field(t, page.Body.String(), "csrf")}, "uuid": {uuid}, "action": {action}, "item": {item}, "reason": {reason}}
	preview := adminRequest(h, "POST", "/preview", values, true)
	if preview.Code != 200 {
		return preview.Code
	}
	values.Set("signature", field(t, preview.Body.String(), "signature"))
	result := adminRequest(h, "POST", "/apply", values, true)
	return result.Code
}

func TestAdminBoundaryAndCSRF(t *testing.T) {
	s, h := adminFixture(t)
	for _, path := range []string{"/", "/player/" + testUUID, "/assets/dashboard.css"} {
		if w := adminRequest(h, "GET", path, nil, false); w.Code != 401 {
			t.Fatalf("unauthenticated %s = %d", path, w.Code)
		}
	}
	for _, path := range []string{"/", "/player/" + testUUID, "/assets/dashboard.css", "/preview", "/apply"} {
		if w := adminRequest(s.Handler(), "GET", path, nil, true); w.Code != 404 {
			t.Fatalf("admin route on public listener %s = %d", path, w.Code)
		}
	}
	page := adminRequest(h, "GET", "/player/"+testUUID, nil, true)
	if page.Code != 200 {
		t.Fatalf("render = %d: %s", page.Code, page.Body.String())
	}
	if page.Header().Get("Cache-Control") != "no-store" || page.Header().Get("Referrer-Policy") != "same-origin" || !strings.Contains(page.Header().Get("Content-Security-Policy"), "frame-ancestors 'none'") {
		t.Fatal("missing security headers")
	}
	form := url.Values{"uuid": {testUUID}, "action": {"grant_styles"}, "item": {"*"}, "reason": {"testing grants"}}
	if w := adminRequest(h, "POST", "/preview", form, true); w.Code != 403 {
		t.Fatalf("missing csrf = %d", w.Code)
	}
	form.Set("csrf", field(t, page.Body.String(), "csrf"))
	for _, origin := range []string{"https://evil.example", "null", "https://example.com"} {
		r := httptest.NewRequest("POST", "http://example.com/preview", strings.NewReader(form.Encode()))
		r.RemoteAddr = "127.0.0.1:1"
		r.SetBasicAuth("dev", adminPassword)
		r.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		r.Header.Set("Origin", origin)
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		if w.Code != 403 {
			t.Fatalf("origin %s = %d", origin, w.Code)
		}
	}
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "203.0.113.8:1"
	r.SetBasicAuth("dev", adminPassword)
	r.Header.Set("X-Forwarded-For", "127.0.0.1")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != 403 {
		t.Fatalf("untrusted network = %d", w.Code)
	}
	if w := adminRequest(h, "POST", "/apply", form, true); w.Code != 403 {
		t.Fatalf("unconfirmed action = %d", w.Code)
	}
	audit, _ := s.Store.ModerationLog(context.Background(), "")
	if len(audit) != 0 {
		t.Fatal("rejected actions changed database")
	}
}

func TestModerationRevokeRestoreAndRestart(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	achievement := s.Catalog.Achievements[0]
	_, err := s.Store.InsertUnlock(ctx, store.Unlock{UUID: testUUID, AchievementID: achievement.ID, TriggerType: achievement.Trigger.Type, Evidence: `{"reported":"<script>alert(1)</script>"}`, ModVersion: "test", ClaimedAt: time.Now()})
	if err != nil {
		t.Fatal(err)
	}
	if err := s.Store.SetEquipped(ctx, testUUID, achievement.Rewards.Prefix.Text, achievement.Rewards.Suffix.Text); err != nil {
		t.Fatal(err)
	}
	if err := s.LoadTitles(ctx); err != nil {
		t.Fatal(err)
	}
	item := s.Catalog.Shop[0]
	if err := s.Store.Buy(ctx, testUUID, []store.Purchase{{ItemID: item.ID, Price: item.Price}}, 10000, time.Now()); err != nil {
		t.Fatal(err)
	}
	initial, err := s.achievementStats(ctx)
	if err != nil || initial.Unlocks[achievement.ID] != 1 {
		t.Fatal("initial stats", initial, err)
	}
	page := adminRequest(h, "GET", "/player/"+testUUID, nil, true)
	if strings.Contains(page.Body.String(), "<script>") {
		t.Fatal("unescaped claim evidence")
	}
	if code := adminAction(t, h, testUUID, "revoke", achievement.ID, "Reported claim is invalid"); code != 303 {
		t.Fatalf("revoke %d", code)
	}
	if s.Titles.Lookup([]string{testUUID})[testUUID] != nil {
		t.Fatal("revoked title remains in cache")
	}
	me, err := s.me(ctx, testUUID)
	if err != nil || me.Points != 0 || me.Balance != -item.Price || len(me.Owned) != 1 {
		t.Fatalf("revoked profile %+v %v", me, err)
	}
	stats, _ := s.achievementStats(ctx)
	if stats.Unlocks[achievement.ID] != 0 {
		t.Fatal("rarity not invalidated")
	}
	if inserted, err := s.Store.InsertUnlock(ctx, store.Unlock{UUID: testUUID, AchievementID: achievement.ID, ClaimedAt: time.Now()}); err != nil || inserted {
		t.Fatal("revoked claim was reclaimed")
	}
	if code := adminAction(t, h, testUUID, "revoke", achievement.ID, "Duplicate revoke attempt"); code != 400 {
		t.Fatalf("duplicate revoke %d", code)
	}
	if code := adminAction(t, h, testUUID, "restore", achievement.ID, "Evidence reviewed again"); code != 303 {
		t.Fatalf("restore %d", code)
	}
	if s.Titles.Lookup([]string{testUUID})[testUUID] == nil {
		t.Fatal("restored title missing from cache")
	}
	unlocks, _ := s.Store.Unlocks(ctx, testUUID)
	if unlocks[0].Evidence != `{"reported":"<script>alert(1)</script>"}` || unlocks[0].RevokeReason != "" {
		t.Fatal("original evidence changed or reason not cleared")
	}
	audit, _ := s.Store.ModerationLog(ctx, testUUID)
	if len(audit) != 2 || audit[0].Action != "restore" || audit[0].Actor != "dev" || !strings.Contains(audit[0].Details, "Reported claim is invalid") {
		t.Fatalf("audit %+v", audit)
	}
	// Rebuild the public cache as startup does and verify the database-backed state.
	s.Titles = titles.NewCache()
	if err := s.LoadTitles(ctx); err != nil {
		t.Fatal(err)
	}
	if s.Titles.Lookup([]string{testUUID})[testUUID] == nil {
		t.Fatal("restored title lost on cache reload")
	}

}

func TestDeveloperGrantsAreScopedAndFree(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	if code := adminAction(t, h, otherUUID, "grant_styles", "*", "Testing forged target"); code != 400 {
		t.Fatalf("other account grant %d", code)
	}
	if code := adminAction(t, h, testUUID, "grant_styles", "unknown_item", "Testing invalid item"); code != 400 {
		t.Fatalf("invalid item grant %d", code)
	}
	if code := adminAction(t, h, testUUID, "grant_styles", "*", "x"); code != 400 {
		t.Fatalf("short reason %d", code)
	}
	paid := s.Catalog.Shop[0]
	if err := s.Store.Buy(ctx, testUUID, []store.Purchase{{ItemID: paid.ID, Price: paid.Price}}, 10000, time.Now()); err != nil {
		t.Fatal(err)
	}
	if code := adminAction(t, h, testUUID, "grant_styles", "*", "Developer style collection"); code != 303 {
		t.Fatalf("styles grant %d", code)
	}
	purchases, err := s.Store.Purchases(ctx, testUUID)
	if err != nil || len(purchases) != len(s.Catalog.Shop) {
		t.Fatalf("purchases %d %v", len(purchases), err)
	}
	for _, p := range purchases {
		if p.ItemID == paid.ID {
			if p.Price != paid.Price {
				t.Fatal("paid item overwritten")
			}
		} else if p.Price != 0 {
			t.Fatal("grant charged points")
		}
	}
	if code := adminAction(t, h, testUUID, "grant_achievement", "*", "Developer reward collection"); code != 303 {
		t.Fatalf("achievement grant %d", code)
	}
	me, err := s.me(ctx, testUUID)
	if err != nil || len(me.Unlocked) != len(s.Catalog.Achievements) || me.Balance != me.Points-paid.Price {
		t.Fatal("incorrect granted profile", err)
	}
	first := s.Catalog.Achievements[0]
	if code := adminAction(t, h, testUUID, "revoke", first.ID, "Keep this reward blocked"); code != 303 {
		t.Fatal(code)
	}
	if code := adminAction(t, h, testUUID, "grant_achievement", "*", "Repeat collection grant"); code != 409 {
		t.Fatalf("repeat grant %d", code)
	}
	unlocks, _ := s.Store.Unlocks(ctx, testUUID)
	found := false
	for _, u := range unlocks {
		achievement, _ := s.Catalog.Get(u.AchievementID)
		if u.TriggerType != achievement.Trigger.Type || u.Evidence != `{"source":"developer_grant"}` {
			t.Fatal("grant did not preserve catalog trigger or provenance")
		}
		if u.AchievementID == first.ID {
			found = u.Revoked
		}
	}
	if !found {
		t.Fatal("grant restored revoked achievement")
	}
	if err := s.Store.ResetPurchases(ctx, testUUID); err != nil {
		t.Fatal(err)
	}
	me, _ = s.me(ctx, testUUID)
	if me.Balance != me.Points {
		t.Fatal("free grants generated refunds")
	}
	audit, _ := s.Store.ModerationLog(ctx, "")
	if len(audit) != 3 {
		t.Fatalf("unexpected log count %d", len(audit))
	}
}

func TestAdminConfigValidation(t *testing.T) {
	s, _ := adminFixture(t)
	for _, cfg := range []AdminConfig{{}, {Username: "dev", Password: "short"}, {Username: "dev", Password: adminPassword, DeveloperUUID: "not-a-uuid"}, {Username: "dev", Password: adminPassword, AllowedCIDRs: []string{"not-a-cidr"}}} {
		if _, err := s.AdminHandler(cfg); err == nil {
			t.Fatalf("accepted invalid config %+v", cfg)
		}
	}
	h, err := s.AdminHandler(AdminConfig{Username: "dev", Password: adminPassword})
	if err != nil {
		t.Fatal(err)
	}
	page := adminRequest(h, "GET", "/player/"+testUUID, nil, true)
	if strings.Contains(page.Body.String(), "Developer grants") {
		t.Fatal("grants enabled without WT_DEV_UUID")
	}
}

func TestAdminConfirmationCannotBeEdited(t *testing.T) {
	s, h := adminFixture(t)
	page := adminRequest(h, "GET", "/player/"+testUUID, nil, true)
	f := url.Values{"csrf": {field(t, page.Body.String(), "csrf")}, "uuid": {testUUID}, "action": {"grant_styles"}, "item": {"*"}, "reason": {"Developer collection"}}
	preview := adminRequest(h, "POST", "/preview", f, true)
	if preview.Code != 200 {
		t.Fatal(preview.Code)
	}
	f.Set("signature", field(t, preview.Body.String(), "signature"))
	f.Set("reason", "Changed reason after preview")
	if w := adminRequest(h, "POST", "/apply", f, true); w.Code != 403 {
		t.Fatalf("tampered confirmation %d", w.Code)
	}
	me, _ := s.me(context.Background(), testUUID)
	if len(me.Owned) != 0 {
		t.Fatal("tampered action changed inventory")
	}
}

// Public authentication and response shapes stay separate from admin credentials.
func TestPlayerTokenDoesNotAuthorizeDashboard(t *testing.T) {
	_, h := adminFixture(t)
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "127.0.0.1:1"
	token, _ := auth.NewToken()
	r.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != 401 {
		t.Fatal(w.Code)
	}
}
