package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestAdminPasswordConfiguration(t *testing.T) {
	t.Setenv("WT_ADMIN_PASSWORD", "")
	t.Setenv("WT_ADMIN_PASSWORD_FILE", "")
	t.Setenv("WT_ADMIN_USERNAME", "dev")
	t.Setenv("WT_ADMIN_ALLOWED_CIDRS", "192.0.2.0/24,127.0.0.0/8")
	path := filepath.Join(t.TempDir(), "password")
	if err := os.WriteFile(path, []byte("a-random-password-with-24-characters\n"), 0600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("WT_ADMIN_PASSWORD_FILE", path)
	cfg, err := adminConfig()
	if err != nil || cfg.Password != "a-random-password-with-24-characters" || len(cfg.AllowedCIDRs) != 2 {
		t.Fatal("password file not loaded", err)
	}
	t.Setenv("WT_ADMIN_PASSWORD", "also-configured")
	if _, err := adminConfig(); err == nil {
		t.Fatal("ambiguous password config accepted")
	}
	t.Setenv("WT_ADMIN_PASSWORD", "")
	t.Setenv("WT_ADMIN_PASSWORD_FILE", path+"missing")
	if _, err := adminConfig(); err == nil {
		t.Fatal("missing password file accepted")
	}
}
