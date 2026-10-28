package auth

import (
	"testing"
	"time"
)

func TestOfflineUUIDMatchesMinecraft(t *testing.T) {
	// Value from UUIDUtil.createOfflinePlayerUUID("Notch").
	if got, want := OfflineUUID("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f"; got != want {
		t.Fatalf("OfflineUUID = %s, want %s", got, want)
	}
}

func TestNormalizeUUID(t *testing.T) {
	for _, in := range []string{"069A79F444E94726A5BEFCA90E38AAF5", "069a79f4-44e9-4726-a5be-fca90e38aaf5"} {
		if got, ok := NormalizeUUID(in); !ok || got != "069a79f4-44e9-4726-a5be-fca90e38aaf5" {
			t.Fatalf("NormalizeUUID(%q) = %q, %v", in, got, ok)
		}
	}
	if _, ok := NormalizeUUID("not-a-uuid"); ok {
		t.Fatal("accepted garbage")
	}
}

func TestChallenges(t *testing.T) {
	now := time.Now()
	c := NewChallenges(time.Minute, 2)

	id, _ := c.Issue("Notch", now)
	if c.Consume(id, "jeb_", now) {
		t.Fatal("consumed with the wrong username")
	}
	id, _ = c.Issue("Notch", now)
	if !c.Consume(id, "notch", now) {
		t.Fatal("username match should be case-insensitive")
	}
	id, _ = c.Issue("Notch", now)
	if c.Consume(id, "Notch", now.Add(2*time.Minute)) {
		t.Fatal("consumed an expired challenge")
	}

	c.Issue("a", now)
	c.Issue("b", now)
	if _, err := c.Issue("c", now); err != ErrTooManyChallenges {
		t.Fatalf("expected ErrTooManyChallenges, got %v", err)
	}
	if _, err := c.Issue("c", now.Add(2*time.Minute)); err != nil {
		t.Fatalf("expired entries should be pruned: %v", err)
	}
}
