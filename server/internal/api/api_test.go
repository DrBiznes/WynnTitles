package api

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/DrBzines/WynnTitles/server/catalog"
	"github.com/DrBzines/WynnTitles/server/internal/auth"
	"github.com/DrBzines/WynnTitles/server/internal/store"
	"github.com/DrBzines/WynnTitles/server/internal/titles"
)

const (
	testUUID     = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
	testUsername = "Notch"
)

// fakeMojang plays the part of sessionserver.mojang.com: it confirms a join
// only for serverIds the test has marked as joined.
type fakeMojang struct {
	mu     sync.Mutex
	joined map[string]bool
}

func (f *fakeMojang) join(serverID string) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.joined[serverID] = true
}

func (f *fakeMojang) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if r.URL.Query().Get("username") != testUsername || !f.joined[r.URL.Query().Get("serverId")] {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"id": "069a79f444e94726a5befca90e38aaf5", "name": testUsername})
}

type harness struct {
	t      *testing.T
	url    string
	mojang *fakeMojang
	cat    *catalog.Catalog
	dbPath string
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	cat, err := catalog.Load()
	if err != nil {
		t.Fatal(err)
	}

	mojang := &fakeMojang{joined: map[string]bool{}}
	mojangServer := httptest.NewServer(mojang)
	t.Cleanup(mojangServer.Close)

	h := &harness{t: t, mojang: mojang, cat: cat, dbPath: filepath.Join(t.TempDir(), "test.db")}
	h.url = h.start(mojangServer.URL)
	return h
}

// start boots a server on the harness database, so calling it twice simulates
// a restart.
func (h *harness) start(mojangURL string) string {
	db, err := store.Open(h.dbPath)
	if err != nil {
		h.t.Fatal(err)
	}
	h.t.Cleanup(func() { db.Close() })

	srv := &Server{
		Store:      db,
		Catalog:    h.cat,
		Titles:     titles.NewCache(),
		Challenges: auth.NewChallenges(time.Minute, 100),
		Verifier:   &auth.MojangVerifier{BaseURL: mojangURL, Client: http.DefaultClient},
		SessionTTL: time.Hour,
		Log:        slog.New(slog.NewTextHandler(io.Discard, nil)),
		Now:        time.Now,
	}
	if err := srv.LoadTitles(context.Background()); err != nil {
		h.t.Fatal(err)
	}
	ts := httptest.NewServer(srv.Handler())
	h.t.Cleanup(ts.Close)
	return ts.URL
}

func (h *harness) do(method, path, token string, body any, out any) int {
	h.t.Helper()
	var reader io.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		reader = bytes.NewReader(b)
	}
	req, _ := http.NewRequest(method, h.url+path, reader)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		h.t.Fatal(err)
	}
	defer resp.Body.Close()
	if out != nil {
		json.NewDecoder(resp.Body).Decode(out)
	}
	return resp.StatusCode
}

func (h *harness) login() string {
	h.t.Helper()
	var ch struct{ ServerID string }
	if code := h.do("POST", "/v1/auth/challenge", "", map[string]string{"username": testUsername}, &ch); code != 200 {
		h.t.Fatalf("challenge: %d", code)
	}
	h.mojang.join(ch.ServerID)

	var session struct{ Token, UUID string }
	code := h.do("POST", "/v1/auth/verify", "", map[string]string{"username": testUsername, "serverId": ch.ServerID}, &session)
	if code != 200 {
		h.t.Fatalf("verify: %d", code)
	}
	if session.UUID != testUUID {
		h.t.Fatalf("uuid = %q, want %q", session.UUID, testUUID)
	}
	return session.Token
}

func TestLoginRequiresMojangJoin(t *testing.T) {
	h := newHarness(t)

	var ch struct{ ServerID string }
	h.do("POST", "/v1/auth/challenge", "", map[string]string{"username": testUsername}, &ch)
	// The client never called joinServer, so Mojang answers 204.
	if code := h.do("POST", "/v1/auth/verify", "", map[string]string{"username": testUsername, "serverId": ch.ServerID}, nil); code != 401 {
		t.Fatalf("verify without join: got %d, want 401", code)
	}
	// Challenges are single use.
	h.mojang.join(ch.ServerID)
	if code := h.do("POST", "/v1/auth/verify", "", map[string]string{"username": testUsername, "serverId": ch.ServerID}, nil); code != 401 {
		t.Fatalf("reused challenge: got %d, want 401", code)
	}
	if code := h.do("GET", "/v1/me", "wts_bogus", nil, nil); code != 401 {
		t.Fatalf("bogus token: got %d, want 401", code)
	}
}

func TestClaimEquipAndLookup(t *testing.T) {
	h := newHarness(t)
	token := h.login()

	a := h.cat.Achievements[0]
	if a.Rewards.Prefix == nil || a.Rewards.Suffix == nil {
		t.Fatal("test expects the first achievement to reward a prefix and a suffix")
	}
	claim := map[string]any{"achievementId": a.ID, "triggerType": a.Trigger.Type, "evidence": map[string]any{"x": 1}}

	// Equipping before unlocking is rejected.
	if code := h.do("PUT", "/v1/me/title", token, map[string]string{"prefix": a.Rewards.Prefix.Text}, nil); code != 400 {
		t.Fatalf("equip locked word: got %d, want 400", code)
	}

	var result struct{ Status string }
	h.do("POST", "/v1/claims", token, claim, &result)
	if result.Status != "unlocked" {
		t.Fatalf("first claim status = %q", result.Status)
	}
	h.do("POST", "/v1/claims", token, claim, &result)
	if result.Status != "alreadyUnlocked" {
		t.Fatalf("second claim status = %q", result.Status)
	}
	if code := h.do("POST", "/v1/claims", token, map[string]any{"achievementId": a.ID, "triggerType": "nope"}, nil); code != 409 {
		t.Fatalf("wrong trigger type: got %d, want 409", code)
	}

	var me meResponse
	h.do("PUT", "/v1/me/title", token, map[string]string{"prefix": a.Rewards.Prefix.Text, "suffix": a.Rewards.Suffix.Text}, &me)
	want := a.Rewards.Prefix.Text + " " + a.Rewards.Suffix.Text
	if me.Title == nil || me.Title.Plain != want {
		t.Fatalf("title = %+v, want %q", me.Title, want)
	}
	if me.Points != a.Points || len(me.Unlocked) != 1 {
		t.Fatalf("me = %+v", me)
	}

	other := "00000000-0000-0000-0000-000000000001"
	var batch struct {
		Titles map[string]*titles.Title
	}
	h.do("POST", "/v1/titles/batch", "", map[string]any{"uuids": []string{"069a79f444e94726a5befca90e38aaf5", other}}, &batch)
	if got := batch.Titles[testUUID]; got == nil || got.Plain != want {
		t.Fatalf("batch title = %+v", got)
	}
	if got, ok := batch.Titles[other]; !ok || got != nil {
		t.Fatalf("missing player should map to null, got %+v (present=%v)", got, ok)
	}

	// Titles survive a restart because the cache is rebuilt from SQLite.
	h.url = h.start("http://unused.invalid")
	batch.Titles = nil
	h.do("POST", "/v1/titles/batch", "", map[string]any{"uuids": []string{testUUID}}, &batch)
	if got := batch.Titles[testUUID]; got == nil || got.Plain != want {
		t.Fatalf("title after restart = %+v", got)
	}

	// Unequipping clears the public title.
	h.do("PUT", "/v1/me/title", token, map[string]any{"prefix": nil, "suffix": nil}, &me)
	if me.Title != nil {
		t.Fatalf("title after unequip = %+v", me.Title)
	}
}

func TestCatalogETag(t *testing.T) {
	h := newHarness(t)
	resp, err := http.Get(h.url + "/v1/catalog")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	etag := resp.Header.Get("ETag")

	req, _ := http.NewRequest("GET", h.url+"/v1/catalog", nil)
	req.Header.Set("If-None-Match", etag)
	resp, err = http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusNotModified {
		t.Fatalf("got %d, want 304", resp.StatusCode)
	}
}

func TestAchievementStats(t *testing.T) {
	h := newHarness(t)
	token := h.login()

	a := h.cat.Achievements[0]
	h.do("POST", "/v1/claims", token, map[string]any{"achievementId": a.ID, "triggerType": a.Trigger.Type}, nil)

	var stats statsResponse
	if code := h.do("GET", "/v1/stats/achievements", "", nil, &stats); code != 200 {
		t.Fatalf("stats: got %d", code)
	}
	if stats.Players != 1 {
		t.Fatalf("players = %d, want 1", stats.Players)
	}
	if len(stats.Unlocks) != len(h.cat.Achievements) {
		t.Fatalf("stats cover %d achievements, want all %d", len(stats.Unlocks), len(h.cat.Achievements))
	}
	if stats.Unlocks[a.ID] != 1 || stats.Unlocks[h.cat.Achievements[1].ID] != 0 {
		t.Fatalf("unlocks = %v", stats.Unlocks)
	}
}

func TestAchievementStatsAreCached(t *testing.T) {
	db, err := store.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	cat, err := catalog.Load()
	if err != nil {
		t.Fatal(err)
	}
	now := time.Unix(1000, 0)
	srv := &Server{Store: db, Catalog: cat, StatsTTL: time.Minute, Now: func() time.Time { return now }}
	ctx := context.Background()

	before, _ := srv.achievementStats(ctx)
	if err := db.UpsertPlayer(ctx, testUUID, testUsername, now); err != nil {
		t.Fatal(err)
	}
	if cached, _ := srv.achievementStats(ctx); cached.Players != before.Players {
		t.Fatalf("players changed within the TTL: %d -> %d", before.Players, cached.Players)
	}
	now = now.Add(time.Minute)
	if fresh, _ := srv.achievementStats(ctx); fresh.Players != 1 {
		t.Fatalf("players after the TTL = %d, want 1", fresh.Players)
	}
}

func TestShopStylesAndReset(t *testing.T) {
	h := newHarness(t)
	token := h.login()

	greg, ok := h.cat.Get("raid_tna")
	if !ok || greg.Rewards.Prefix == nil || !greg.Rewards.Prefix.Runs[0].Obfuscated {
		t.Fatal("test expects raid_tna to reward an obfuscated prefix")
	}
	h.do("POST", "/v1/claims", token, map[string]any{"achievementId": greg.ID, "triggerType": greg.Trigger.Type}, nil)
	// JSON leaves out false flags, so decode each response into a fresh value.
	var me meResponse
	fresh := func() *meResponse {
		me = meResponse{}
		return &me
	}
	h.do("PUT", "/v1/me/title", token, map[string]string{"prefix": "Greg"}, fresh())
	if me.Balance != greg.Points || len(me.Owned) != 0 || me.Styles.Prefix != nil {
		t.Fatalf("before shopping: balance %d, owned %v, style %+v", me.Balance, me.Owned, me.Styles.Prefix)
	}
	if !me.Title.Runs[0].Obfuscated {
		t.Fatalf("Greg should start in its native obfuscated style, got %+v", me.Title.Runs)
	}

	buy := func(ids ...string) int {
		return h.do("POST", "/v1/shop/buy", token, map[string]any{"itemIds": ids}, fresh())
	}
	if code := buy("glow"); code != 404 {
		t.Fatalf("unknown item: got %d, want 404", code)
	}
	if code := buy("rainbow"); code != 400 {
		t.Fatalf("buying past the balance: got %d, want 400", code)
	}
	// Orders are all or nothing.
	if code := buy("color_red", "palette_ice"); code != 400 {
		t.Fatalf("order past the balance: got %d, want 400", code)
	}
	h.do("GET", "/v1/me", token, nil, fresh())
	if len(me.Owned) != 0 {
		t.Fatalf("a refused order bought %v", me.Owned)
	}

	style := map[string]any{"prefix": map[string]any{"source": "color_red", "formats": []string{"bold"}}}
	if code := h.do("PUT", "/v1/me/style", token, style, nil); code != 400 {
		t.Fatalf("wearing unowned items: got %d, want 400", code)
	}
	// Earn enough for the newly priced pieces after checking insufficient funds.
	earned := 0
	for _, a := range h.cat.Achievements {
		h.do("POST", "/v1/claims", token, map[string]any{"achievementId": a.ID, "triggerType": a.Trigger.Type}, nil)
		earned += a.Points
	}
	red, _ := h.cat.ShopItem("color_red")
	bold, _ := h.cat.ShopItem("bold")
	if code := buy("color_red", "bold"); code != 200 || me.Balance != earned-red.Price-bold.Price || len(me.Owned) != 2 {
		t.Fatalf("buy: code %d, balance %d, owned %v", code, me.Balance, me.Owned)
	}
	if code := buy("bold"); code != 409 {
		t.Fatalf("buying twice: got %d, want 409", code)
	}
	if code := h.do("PUT", "/v1/me/style", token, map[string]any{"prefix": map[string]any{}}, nil); code != 400 {
		t.Fatalf("empty style: got %d, want 400", code)
	}

	// A shop style replaces the native one, which reveals Greg.
	h.do("PUT", "/v1/me/style", token, style, fresh())
	want := catalog.Run{Text: "Greg", Color: "red", Bold: true}
	if me.Title == nil || len(me.Title.Runs) != 1 || me.Title.Runs[0].Color != want.Color ||
		!me.Title.Runs[0].Bold || me.Title.Runs[0].Obfuscated || me.Title.Runs[0].Effect != nil {
		t.Fatalf("styled title = %+v, want %+v", me.Title, want)
	}
	lookup := func() *titles.Title {
		var batch struct{ Titles map[string]*titles.Title }
		h.do("POST", "/v1/titles/batch", "", map[string]any{"uuids": []string{testUUID}}, &batch)
		return batch.Titles[testUUID]
	}
	if got := lookup(); got == nil || got.Runs[0].Obfuscated || got.Runs[0].Color != "red" {
		t.Fatalf("public title = %+v", got)
	}
	h.url = h.start("http://unused.invalid")
	if got := lookup(); got == nil || got.Runs[0].Color != "red" {
		t.Fatalf("public title after restart = %+v", got)
	}

	// Resetting refunds everything and goes back to native.
	h.do("POST", "/v1/shop/reset", token, nil, fresh())
	if me.Balance != earned || len(me.Owned) != 0 || me.Styles.Prefix != nil || !me.Title.Runs[0].Obfuscated {
		t.Fatalf("after reset: balance %d, owned %v, style %+v, title %+v", me.Balance, me.Owned, me.Styles.Prefix, me.Title)
	}
	if got := lookup(); got == nil || !got.Runs[0].Obfuscated {
		t.Fatalf("public title after reset = %+v", got)
	}
}
