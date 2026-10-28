// Package auth verifies Minecraft account ownership with Mojang's session
// server, the same handshake a Minecraft server performs when you join it.
//
//  1. The mod asks for a challenge and receives a random serverId.
//  2. The mod calls Mojang's joinServer with that serverId and its own session.
//  3. We call Mojang's hasJoined with the username and serverId; Mojang only
//     answers with the profile if step 2 really happened for that account.
package auth

import (
	"context"
	"crypto/md5"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

type Profile struct {
	UUID     string
	Username string
}

// Verifier confirms that username completed joinServer with serverId.
type Verifier interface {
	HasJoined(ctx context.Context, username, serverID string) (Profile, error)
}

var ErrNotJoined = errors.New("mojang did not confirm the session")

type MojangVerifier struct {
	BaseURL string
	Client  *http.Client
}

func (m *MojangVerifier) HasJoined(ctx context.Context, username, serverID string) (Profile, error) {
	q := url.Values{"username": {username}, "serverId": {serverID}}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		strings.TrimRight(m.BaseURL, "/")+"/session/minecraft/hasJoined?"+q.Encode(), nil)
	if err != nil {
		return Profile{}, err
	}

	resp, err := m.Client.Do(req)
	if err != nil {
		return Profile{}, fmt.Errorf("mojang request: %w", err)
	}
	defer resp.Body.Close()

	// Mojang answers 204 No Content when the join never happened.
	if resp.StatusCode == http.StatusNoContent {
		return Profile{}, ErrNotJoined
	}
	if resp.StatusCode != http.StatusOK {
		return Profile{}, fmt.Errorf("mojang returned %s", resp.Status)
	}

	var body struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return Profile{}, fmt.Errorf("decode mojang response: %w", err)
	}

	uuid, ok := NormalizeUUID(body.ID)
	if !ok {
		return Profile{}, fmt.Errorf("mojang returned malformed uuid %q", body.ID)
	}
	return Profile{UUID: uuid, Username: body.Name}, nil
}

// OfflineVerifier trusts any username and derives the same offline-mode UUID
// Minecraft uses for development clients. It exists only for local testing
// with `./gradlew runClient`; never enable it on a public server.
type OfflineVerifier struct{}

func (OfflineVerifier) HasJoined(_ context.Context, username, _ string) (Profile, error) {
	return Profile{UUID: OfflineUUID(username), Username: username}, nil
}

// OfflineUUID matches Minecraft's UUIDUtil.createOfflinePlayerUUID: a version 3
// (MD5) UUID of "OfflinePlayer:<name>".
func OfflineUUID(username string) string {
	sum := md5.Sum([]byte("OfflinePlayer:" + username))
	sum[6] = sum[6]&0x0f | 0x30
	sum[8] = sum[8]&0x3f | 0x80
	return formatUUID(sum[:])
}

// NormalizeUUID accepts dashed or undashed UUIDs and returns the lowercase
// dashed form that the store and the title cache key on.
func NormalizeUUID(s string) (string, bool) {
	s = strings.ToLower(strings.ReplaceAll(s, "-", ""))
	if len(s) != 32 {
		return "", false
	}
	b, err := hex.DecodeString(s)
	if err != nil {
		return "", false
	}
	return formatUUID(b), true
}

func formatUUID(b []byte) string {
	h := hex.EncodeToString(b)
	return h[0:8] + "-" + h[8:12] + "-" + h[12:16] + "-" + h[16:20] + "-" + h[20:32]
}

// Challenges remembers recently issued serverIds so each can be used once, by
// the username it was issued to, within its lifetime.
type Challenges struct {
	mu      sync.Mutex
	pending map[string]challenge
	ttl     time.Duration
	max     int
}

type challenge struct {
	username string
	expires  time.Time
}

var ErrTooManyChallenges = errors.New("too many pending logins, try again shortly")

func NewChallenges(ttl time.Duration, max int) *Challenges {
	return &Challenges{pending: map[string]challenge{}, ttl: ttl, max: max}
}

func (c *Challenges) Issue(username string, now time.Time) (string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if len(c.pending) >= c.max {
		c.pruneLocked(now)
		if len(c.pending) >= c.max {
			return "", ErrTooManyChallenges
		}
	}

	serverID := randomHex(20)
	c.pending[serverID] = challenge{username: strings.ToLower(username), expires: now.Add(c.ttl)}
	return serverID, nil
}

// Consume reports whether serverID was issued to username and is unexpired.
// A serverID is removed on first use whether or not it matches.
func (c *Challenges) Consume(serverID, username string, now time.Time) bool {
	c.mu.Lock()
	defer c.mu.Unlock()

	ch, ok := c.pending[serverID]
	delete(c.pending, serverID)
	return ok && now.Before(ch.expires) && ch.username == strings.ToLower(username)
}

func (c *Challenges) pruneLocked(now time.Time) {
	for id, ch := range c.pending {
		if !now.Before(ch.expires) {
			delete(c.pending, id)
		}
	}
}

// NewToken returns a session token for the client and the hash we store. Only
// the hash touches the database, so a leaked database cannot log anyone in.
func NewToken() (token, hash string) {
	b := make([]byte, 32)
	rand.Read(b)
	token = "wts_" + base64.RawURLEncoding.EncodeToString(b)
	return token, HashToken(token)
}

func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func randomHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}
