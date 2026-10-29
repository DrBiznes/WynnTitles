package store

import (
	"context"
	"database/sql"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestModerationMigrationAndAtomicAudit(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "existing.db")
	old, err := sql.Open("sqlite", path)
	if err != nil {
		t.Fatal(err)
	}
	for _, migration := range migrations[:2] {
		if _, err := old.Exec(migration); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := old.Exec(`PRAGMA user_version = 2`); err != nil {
		t.Fatal(err)
	}
	if _, err := old.Exec(`INSERT INTO players(uuid,username,created_at,last_seen_at) VALUES('player','Existing',1,1);
 INSERT INTO unlocks(uuid,achievement_id,trigger_type,evidence,claimed_at) VALUES('player','reward','quest','original evidence',1)`); err != nil {
		t.Fatal(err)
	}
	old.Close()
	db, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := db.db.Exec(`CREATE TRIGGER fail_audit BEFORE INSERT ON moderation_log BEGIN SELECT RAISE(FAIL, 'test audit failure'); END`); err != nil {
		t.Fatal(err)
	}
	if err := db.Moderate(ctx, "dev", "player", "revoke", "Audit must be atomic", []ModerationItem{{ID: "reward"}}, time.Now()); err == nil {
		t.Fatal("expected audit insertion to fail")
	}
	unlocks, err := db.Unlocks(ctx, "player")
	if err != nil || len(unlocks) != 1 || unlocks[0].Revoked {
		t.Fatal("failed audit did not roll back revocation", err)
	}
	if _, err := db.db.Exec(`DROP TRIGGER fail_audit`); err != nil {
		t.Fatal(err)
	}
	if err := db.Moderate(ctx, "dev", "player", "revoke", "Invalid reported evidence", []ModerationItem{{ID: "reward"}}, time.Now()); err != nil {
		t.Fatal(err)
	}
	db.Close()
	reopened, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer reopened.Close()
	unlocks, err = reopened.Unlocks(ctx, "player")
	if err != nil || !unlocks[0].Revoked || unlocks[0].Evidence != "original evidence" {
		t.Fatal("revocation or evidence lost after reopen", err)
	}
	log, err := reopened.ModerationLog(ctx, "player")
	if err != nil || len(log) != 1 || log[0].Actor != "dev" || !strings.Contains(log[0].Details, "reward") {
		t.Fatal("audit lost after reopen", err)
	}
}
