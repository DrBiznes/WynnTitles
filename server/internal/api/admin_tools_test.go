package api

import (
	"context"
	"fmt"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

// apiCall sends an authenticated request to the public API as a fresh session.
func apiCall(t *testing.T, s *Server, uuid, method, path, body string) *httptest.ResponseRecorder {
	t.Helper()
	token, hash := auth.NewToken()
	now := time.Now()
	if err := s.Store.CreateSession(context.Background(), hash, uuid, now, now.Add(time.Hour)); err != nil {
		t.Fatal(err)
	}
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	r.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	s.Handler().ServeHTTP(w, r)
	return w
}

func seedClaim(t *testing.T, s *Server, uuid string, index int, evidence, version string) catalog.Achievement {
	t.Helper()
	a := s.Catalog.Achievements[index]
	if _, err := s.Store.InsertUnlocks(context.Background(), []store.Unlock{{UUID: uuid, AchievementID: a.ID, TriggerType: a.Trigger.Type, Evidence: evidence, ModVersion: version, ClaimedAt: time.Now()}}); err != nil {
		t.Fatal(err)
	}
	return a
}

func claimBody(a catalog.Achievement, version string) string {
	return fmt.Sprintf(`{"claims":[{"achievementId":%q,"triggerType":%q,"evidence":{"a":1}}],"modVersion":%q}`, a.ID, a.Trigger.Type, version)
}

func TestSuspensionHidesPlayerEverywhere(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	a := seedClaim(t, s, otherUUID, 0, `{"a":1}`, "1.0.0")
	second := seedClaim(t, s, otherUUID, 1, `{"a":1}`, "1.0.0")
	if err := s.Store.SetEquipped(ctx, otherUUID, a.Rewards.Prefix.Text, ""); err != nil {
		t.Fatal(err)
	}
	if err := s.LoadTitles(ctx); err != nil {
		t.Fatal(err)
	}
	if s.Titles.Lookup([]string{otherUUID})[otherUUID] == nil {
		t.Fatal("title missing before suspension")
	}
	s.AppealURL = "https://example.com/appeal"
	if code := adminAction(t, h, otherUUID, "suspend", "", "Faked claims"); code != 303 {
		t.Fatalf("suspend %d", code)
	}
	if s.Titles.Lookup([]string{otherUUID})[otherUUID] != nil {
		t.Fatal("suspended title still public")
	}
	// A 401 would send the mod back through the Mojang login on every retry.
	w := apiCall(t, s, otherUUID, "GET", "/v1/me", "")
	if w.Code != 403 || !strings.Contains(w.Body.String(), "https://example.com/appeal") {
		t.Fatalf("suspended me %d %s", w.Code, w.Body.String())
	}
	if w := apiCall(t, s, otherUUID, "POST", "/v1/claims", claimBody(s.Catalog.Achievements[2], "1.0.0")); w.Code != 403 {
		t.Fatalf("suspended claim %d", w.Code)
	}
	stats, err := s.achievementStats(ctx)
	if err != nil || stats.Players != 1 || stats.Unlocks[a.ID] != 0 {
		t.Fatalf("suspended player in rarity %+v %v", stats, err)
	}
	// The title must not come back with the cache at startup.
	s.Titles = titles.NewCache()
	if err := s.LoadTitles(ctx); err != nil {
		t.Fatal(err)
	}
	if s.Titles.Lookup([]string{otherUUID})[otherUUID] != nil {
		t.Fatal("suspended title returned after restart")
	}
	unlocks, _ := s.Store.Unlocks(ctx, otherUUID)
	if unlocks[0].Revoked || unlocks[1].Revoked {
		t.Fatal("plain suspension revoked claims")
	}
	if code := adminAction(t, h, otherUUID, "suspend", "", "Second attempt"); code != 400 {
		t.Fatalf("duplicate suspend %d", code)
	}
	if code := adminAction(t, h, otherUUID, "unsuspend", "", "Appeal accepted"); code != 303 {
		t.Fatalf("unsuspend %d", code)
	}
	if s.Titles.Lookup([]string{otherUUID})[otherUUID] == nil {
		t.Fatal("title not restored after suspension")
	}
	if w := apiCall(t, s, otherUUID, "GET", "/v1/me", ""); w.Code != 200 {
		t.Fatalf("restored me %d", w.Code)
	}
	if code := adminAction(t, h, otherUUID, "suspend", "revoke_all", "Faked everything"); code != 303 {
		t.Fatalf("suspend with revoke %d", code)
	}
	unlocks, _ = s.Store.Unlocks(ctx, otherUUID)
	if !unlocks[0].Revoked || !unlocks[1].Revoked {
		t.Fatal("suspension did not revoke claims")
	}
	audit, _ := s.Store.ModerationLog(ctx, otherUUID)
	if audit[0].Action != "suspend" || !strings.Contains(audit[0].Details, second.ID) {
		t.Fatalf("audit %+v", audit[0])
	}
}

func TestRevokeAndRestoreAll(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	for i := range 3 {
		seedClaim(t, s, otherUUID, i, `{"a":1}`, "1.0.0")
	}
	if code := adminAction(t, h, otherUUID, "revoke_all", "all", "Fake client"); code != 303 {
		t.Fatalf("revoke all %d", code)
	}
	me, _ := s.me(ctx, otherUUID)
	if me.Points != 0 || len(me.Unlocked) != 0 {
		t.Fatalf("revoke all left %+v", me)
	}
	if code := adminAction(t, h, otherUUID, "revoke_all", "all", "Again"); code != 400 {
		t.Fatalf("nothing left to revoke %d", code)
	}
	if code := adminAction(t, h, otherUUID, "restore_all", "all", "Mistake"); code != 303 {
		t.Fatalf("restore all %d", code)
	}
	me, _ = s.me(ctx, otherUUID)
	if len(me.Unlocked) != 3 {
		t.Fatalf("restore all left %+v", me)
	}
}

func TestClearAchievementLetsPlayersEarnItAgain(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	a := seedClaim(t, s, testUUID, 0, `{"bad":"detector"}`, "1.0.0")
	seedClaim(t, s, otherUUID, 0, `{"a":1}`, "1.0.0")
	if code := adminAction(t, h, otherUUID, "revoke", a.ID, "Cheater"); code != 303 {
		t.Fatalf("revoke %d", code)
	}
	if code := adminAction(t, h, "", "clear_achievement", a.ID, "Detector fired for everyone"); code != 303 {
		t.Fatalf("clear %d", code)
	}
	inserted, err := s.Store.InsertUnlocks(ctx, []store.Unlock{{UUID: testUUID, AchievementID: a.ID, ClaimedAt: time.Now()}, {UUID: otherUUID, AchievementID: a.ID, ClaimedAt: time.Now()}})
	if err != nil || !inserted[0] {
		t.Fatalf("cleared player could not earn it again: %v %v", inserted, err)
	}
	if inserted[1] {
		t.Fatal("clearing lifted a revocation")
	}
	audit, _ := s.Store.ModerationLog(ctx, testUUID)
	if len(audit) != 1 || audit[0].Action != "clear_achievement" || !strings.Contains(audit[0].Details, "detector") {
		t.Fatalf("evidence not kept in audit: %+v", audit)
	}
	if code := adminAction(t, h, "", "clear_achievement", "no_such_achievement", "Bad id"); code != 400 {
		t.Fatalf("unknown achievement %d", code)
	}
}

func TestClaimControls(t *testing.T) {
	s, h := adminFixture(t)
	first, second := s.Catalog.Achievements[0], s.Catalog.Achievements[1]
	status := func(a catalog.Achievement, version string) string {
		t.Helper()
		w := apiCall(t, s, otherUUID, "POST", "/v1/claims", claimBody(a, version))
		if w.Code != 200 {
			t.Fatalf("claim %d %s", w.Code, w.Body.String())
		}
		return regexp.MustCompile(`"status":"(\w+)"`).FindStringSubmatch(w.Body.String())[1]
	}
	if code := adminAction(t, h, "", "pause_achievement", first.ID, "Broken detector"); code != 303 {
		t.Fatalf("pause one %d", code)
	}
	if got := status(first, "1.0.0"); got != "paused" {
		t.Fatalf("paused achievement = %s", got)
	}
	if got := status(second, "1.0.0"); got != "unlocked" {
		t.Fatalf("other achievement = %s", got)
	}
	if code := adminAction(t, h, "", "resume_achievement", first.ID, "Fixed"); code != 303 {
		t.Fatalf("resume one %d", code)
	}
	if code := adminAction(t, h, "", "pause_claims", "", "Incident"); code != 303 {
		t.Fatalf("pause all %d", code)
	}
	if code := adminAction(t, h, "", "pause_claims", "", "Incident"); code != 400 {
		t.Fatalf("pause twice %d", code)
	}
	if got := status(first, "1.0.0"); got != "paused" {
		t.Fatalf("paused claims = %s", got)
	}
	if code := adminAction(t, h, "", "resume_claims", "", "Over"); code != 303 {
		t.Fatalf("resume all %d", code)
	}
	if code := adminAction(t, h, "", "set_min_mod_version", "not-a-version", "Typo"); code != 400 {
		t.Fatalf("bad version %d", code)
	}
	if code := adminAction(t, h, "", "set_min_mod_version", "1.2.0", "Bad build"); code != 303 {
		t.Fatalf("set minimum %d", code)
	}
	if got := status(first, "1.1.9"); got != "updateRequired" {
		t.Fatalf("old mod = %s", got)
	}
	if got := status(first, "garbage"); got != "updateRequired" {
		t.Fatalf("unparsable mod = %s", got)
	}
	if got := status(first, "1.10.0"); got != "unlocked" {
		t.Fatalf("new mod = %s", got)
	}
	// Server-wide actions have no player, and the audit page must still render.
	audit, _ := s.Store.ModerationLog(context.Background(), "")
	if len(audit) != 5 || audit[0].UUID != "" {
		t.Fatalf("audit %+v", audit)
	}
	if page := adminRequest(h, "GET", "/", nil, true); page.Code != 200 || !strings.Contains(page.Body.String(), "Server-wide") {
		t.Fatalf("overview %d", page.Code)
	}
}

func TestDashboardFindsProblems(t *testing.T) {
	s, h := adminFixture(t)
	ctx := context.Background()
	if err := s.Store.SetClaimControls(ctx, "dev", "set_latest_mod_version", "test", store.ClaimControls{LatestModVersion: "1.0.0"}, time.Now()); err != nil {
		t.Fatal(err)
	}
	clean := seedClaim(t, s, testUUID, 0, `{"a":1}`, "1.0.0")
	fake := seedClaim(t, s, otherUUID, 1, ``, "9.9.9")
	grant := seedClaim(t, s, testUUID, 2, `{"source":"developer_grant"}`, "dashboard")
	flagged := adminRequest(h, "GET", "/claims?flagged=1", nil, true).Body.String()
	for _, want := range []string{"Unreleased mod version", "Missing or malformed evidence", fake.ID} {
		if !strings.Contains(flagged, want) {
			t.Fatalf("flagged page lacks %q: %s", want, flagged)
		}
	}
	if strings.Contains(flagged, clean.ID) || strings.Contains(flagged, grant.ID) {
		t.Fatal("clean claim flagged")
	}
	for _, path := range []string{"/", "/?sort=claims", "/?sort=joined&suspended=1", "/claims", "/achievements", "/achievement/" + clean.ID, "/player/" + otherUUID} {
		if w := adminRequest(h, "GET", path, nil, true); w.Code != 200 {
			t.Fatalf("%s = %d: %s", path, w.Code, w.Body.String())
		}
	}
	if w := adminRequest(h, "GET", "/achievement/no_such", nil, true); w.Code != 404 {
		t.Fatalf("unknown achievement = %d", w.Code)
	}
	if body := adminRequest(h, "GET", "/achievement/"+clean.ID, nil, true).Body.String(); !strings.Contains(body, "Notch") {
		t.Fatal("holder missing from achievement page")
	}
	overview, err := s.Store.Overview(ctx, time.Now())
	if err != nil || overview.Players != 2 || overview.Claims != 3 || overview.DatabaseBytes == 0 {
		t.Fatalf("overview %+v %v", overview, err)
	}
	if len(overview.ModVersions) != 2 {
		t.Fatalf("grants counted as mod versions: %+v", overview.ModVersions)
	}
}

func TestAPIUseUpdatesLastSeenAndHealthSeesDatabase(t *testing.T) {
	s, _ := adminFixture(t)
	ctx := context.Background()
	old := time.Now().Add(-48 * time.Hour)
	if err := s.Store.TouchPlayer(ctx, otherUUID, old); err != nil {
		t.Fatal(err)
	}
	apiCall(t, s, otherUUID, "GET", "/v1/me", "")
	player, _ := s.Store.Player(ctx, otherUUID)
	if time.Since(player.LastSeenAt) > time.Minute {
		t.Fatalf("last seen not updated: %v", player.LastSeenAt)
	}
	r := httptest.NewRequest("GET", "/healthz", nil)
	w := httptest.NewRecorder()
	s.Handler().ServeHTTP(w, r)
	if w.Code != 200 {
		t.Fatalf("healthy %d", w.Code)
	}
	s.Store.Close()
	w = httptest.NewRecorder()
	s.Handler().ServeHTTP(w, r)
	if w.Code != 503 {
		t.Fatalf("closed database health %d", w.Code)
	}
}

func TestCompareVersions(t *testing.T) {
	for _, c := range []struct {
		a, b string
		want int
	}{{"1.2.3", "1.2.3", 0}, {"1.10.0", "1.9.9", 1}, {"0.15.1-beta", "0.15.2", -1}, {"0.15.1+mc1.21", "0.15.1", 0}, {"junk", "0.0.1", -1}, {"", "0.0.1", -1}} {
		if got := compareVersions(c.a, c.b); got != c.want {
			t.Fatalf("compareVersions(%q, %q) = %d", c.a, c.b, got)
		}
	}
}
