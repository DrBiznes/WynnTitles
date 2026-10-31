// Package store persists players, sessions, achievement unlocks and style
// purchases in SQLite.
package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// migrations run in order; PRAGMA user_version records how many have been
// applied. Never edit a migration that has shipped; append a new one instead.
var migrations = []string{
	`CREATE TABLE players (
		uuid            TEXT PRIMARY KEY,
		username        TEXT NOT NULL,
		equipped_prefix TEXT,
		equipped_suffix TEXT,
		created_at      INTEGER NOT NULL,
		last_seen_at    INTEGER NOT NULL
	);

	CREATE TABLE sessions (
		token_hash TEXT PRIMARY KEY,
		uuid       TEXT NOT NULL REFERENCES players(uuid),
		created_at INTEGER NOT NULL,
		expires_at INTEGER NOT NULL
	);
	CREATE INDEX sessions_by_uuid ON sessions(uuid);

	-- One row per player per achievement. Doubles as the audit log for claims:
	-- moderation revokes by setting revoked_at instead of deleting the row.
	CREATE TABLE unlocks (
		uuid           TEXT NOT NULL REFERENCES players(uuid),
		achievement_id TEXT NOT NULL,
		trigger_type   TEXT NOT NULL,
		evidence       TEXT,
		mod_version    TEXT,
		claimed_at     INTEGER NOT NULL,
		revoked_at     INTEGER,
		revoke_reason  TEXT,
		PRIMARY KEY (uuid, achievement_id)
	);`,

	`-- Style shop items a player has bought. Price is what they paid, so
	-- repricing an item never changes anyone's balance. Resetting deletes the
	-- rows, which refunds them.
	CREATE TABLE purchases (
		uuid         TEXT NOT NULL REFERENCES players(uuid),
		item_id      TEXT NOT NULL,
		price        INTEGER NOT NULL,
		purchased_at INTEGER NOT NULL,
		PRIMARY KEY (uuid, item_id)
	);

	-- How each slot is restyled, as catalog.Style JSON. NULL shows the word's
	-- native style.
	ALTER TABLE players ADD COLUMN prefix_style TEXT;
	ALTER TABLE players ADD COLUMN suffix_style TEXT;`,

	`CREATE TABLE moderation_log (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		actor TEXT NOT NULL,
		uuid TEXT NOT NULL REFERENCES players(uuid),
		action TEXT NOT NULL,
		reason TEXT NOT NULL,
		details TEXT NOT NULL,
		created_at INTEGER NOT NULL
	);
	CREATE INDEX moderation_by_player ON moderation_log(uuid, id);`,
}

type Store struct {
	db *sql.DB
}

type Player struct {
	UUID           string
	Username       string
	EquippedPrefix string
	EquippedSuffix string
	// PrefixStyle and SuffixStyle are catalog.Style JSON, or empty for the
	// word's native style.
	PrefixStyle string
	SuffixStyle string
}

type Purchase struct {
	ItemID string
	Price  int
}

type Unlock struct {
	UUID          string
	AchievementID string
	TriggerType   string
	Evidence      string
	ModVersion    string
	ClaimedAt     time.Time
	Revoked       bool
	RevokeReason  string
}

var (
	ErrNotFound        = errors.New("not found")
	ErrAlreadyOwned    = errors.New("already owned")
	ErrNotEnoughPoints = errors.New("not enough points")
)

func Open(path string) (*Store, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, err
	}

	dsn := "file:" + path + "?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	// SQLite allows one writer at a time. Title lookups are served from memory,
	// so a single connection is plenty and avoids SQLITE_BUSY entirely.
	db.SetMaxOpenConns(1)

	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) migrate() error {
	var version int
	if err := s.db.QueryRow(`PRAGMA user_version`).Scan(&version); err != nil {
		return err
	}

	for i := version; i < len(migrations); i++ {
		tx, err := s.db.Begin()
		if err != nil {
			return err
		}
		if _, err := tx.Exec(migrations[i]); err != nil {
			tx.Rollback()
			return fmt.Errorf("migration %d: %w", i+1, err)
		}
		if _, err := tx.Exec(fmt.Sprintf(`PRAGMA user_version = %d`, i+1)); err != nil {
			tx.Rollback()
			return err
		}
		if err := tx.Commit(); err != nil {
			return err
		}
	}
	return nil
}

// UpsertPlayer records a login, creating the player on first sight and keeping
// their username current (Minecraft names can change).
func (s *Store) UpsertPlayer(ctx context.Context, uuid, username string, now time.Time) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO players (uuid, username, created_at, last_seen_at) VALUES (?, ?, ?, ?)
		ON CONFLICT (uuid) DO UPDATE SET username = excluded.username, last_seen_at = excluded.last_seen_at`,
		uuid, username, now.UnixMilli(), now.UnixMilli())
	return err
}

const playerColumns = `uuid, username, COALESCE(equipped_prefix, ''), COALESCE(equipped_suffix, ''),
	COALESCE(prefix_style, ''), COALESCE(suffix_style, '')`

type scanner interface {
	Scan(dest ...any) error
}

func scanPlayer(row scanner) (Player, error) {
	var p Player
	err := row.Scan(&p.UUID, &p.Username, &p.EquippedPrefix, &p.EquippedSuffix, &p.PrefixStyle, &p.SuffixStyle)
	return p, err
}

func (s *Store) Player(ctx context.Context, uuid string) (Player, error) {
	p, err := scanPlayer(s.db.QueryRowContext(ctx, `SELECT `+playerColumns+` FROM players WHERE uuid = ?`, uuid))
	if errors.Is(err, sql.ErrNoRows) {
		return p, ErrNotFound
	}
	return p, err
}

// SetEquipped stores the equipped words; an empty string unequips that slot.
func (s *Store) SetEquipped(ctx context.Context, uuid, prefix, suffix string) error {
	_, err := s.db.ExecContext(ctx,
		`UPDATE players SET equipped_prefix = NULLIF(?, ''), equipped_suffix = NULLIF(?, '') WHERE uuid = ?`,
		prefix, suffix, uuid)
	return err
}

// SetStyles stores how each slot is restyled; an empty string goes back to
// the word's native style.
func (s *Store) SetStyles(ctx context.Context, uuid, prefixStyle, suffixStyle string) error {
	_, err := s.db.ExecContext(ctx,
		`UPDATE players SET prefix_style = NULLIF(?, ''), suffix_style = NULLIF(?, '') WHERE uuid = ?`,
		prefixStyle, suffixStyle, uuid)
	return err
}

// PlayersWithTitles returns every player who has a prefix or suffix equipped,
// used to warm the in-memory title cache at startup.
func (s *Store) PlayersWithTitles(ctx context.Context) ([]Player, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT `+playerColumns+` FROM players
		WHERE equipped_prefix IS NOT NULL OR equipped_suffix IS NOT NULL`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var players []Player
	for rows.Next() {
		p, err := scanPlayer(rows)
		if err != nil {
			return nil, err
		}
		players = append(players, p)
	}
	return players, rows.Err()
}

func (s *Store) CreateSession(ctx context.Context, tokenHash, uuid string, now, expires time.Time) error {
	_, err := s.db.ExecContext(ctx,
		`INSERT INTO sessions (token_hash, uuid, created_at, expires_at) VALUES (?, ?, ?, ?)`,
		tokenHash, uuid, now.UnixMilli(), expires.UnixMilli())
	return err
}

// SessionUUID returns the player a session token belongs to, or ErrNotFound if
// the token is unknown or expired.
func (s *Store) SessionUUID(ctx context.Context, tokenHash string, now time.Time) (string, error) {
	var uuid string
	err := s.db.QueryRowContext(ctx,
		`SELECT uuid FROM sessions WHERE token_hash = ? AND expires_at > ?`, tokenHash, now.UnixMilli()).
		Scan(&uuid)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNotFound
	}
	return uuid, err
}

func (s *Store) DeleteSession(ctx context.Context, tokenHash string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE token_hash = ?`, tokenHash)
	return err
}

func (s *Store) DeleteExpiredSessions(ctx context.Context, now time.Time) (int64, error) {
	res, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE expires_at <= ?`, now.UnixMilli())
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}

// Unlocks returns every unlock for a player, including revoked ones.
func (s *Store) Unlocks(ctx context.Context, uuid string) ([]Unlock, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT uuid, achievement_id, trigger_type, COALESCE(evidence, ''), COALESCE(mod_version, ''),
		       claimed_at, revoked_at IS NOT NULL, COALESCE(revoke_reason, '')
		FROM unlocks WHERE uuid = ? ORDER BY claimed_at`, uuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var unlocks []Unlock
	for rows.Next() {
		var u Unlock
		var claimedAt int64
		if err := rows.Scan(&u.UUID, &u.AchievementID, &u.TriggerType, &u.Evidence, &u.ModVersion,
			&claimedAt, &u.Revoked, &u.RevokeReason); err != nil {
			return nil, err
		}
		u.ClaimedAt = time.UnixMilli(claimedAt)
		unlocks = append(unlocks, u)
	}
	return unlocks, rows.Err()
}

// InsertUnlocks records several claims in one transaction, so a burst of claims
// costs one disk sync. It reports which were new, in order; rows that already
// exist (claimed or revoked) are left alone.
func (s *Store) InsertUnlocks(ctx context.Context, unlocks []Unlock) ([]bool, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	inserted := make([]bool, len(unlocks))
	for i, u := range unlocks {
		res, err := tx.ExecContext(ctx, `
			INSERT INTO unlocks (uuid, achievement_id, trigger_type, evidence, mod_version, claimed_at)
			VALUES (?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?)
			ON CONFLICT (uuid, achievement_id) DO NOTHING`,
			u.UUID, u.AchievementID, u.TriggerType, u.Evidence, u.ModVersion, u.ClaimedAt.UnixMilli())
		if err != nil {
			return nil, err
		}
		n, err := res.RowsAffected()
		if err != nil {
			return nil, err
		}
		inserted[i] = n == 1
	}
	return inserted, tx.Commit()
}

// UnlockCounts returns how many players exist and how many of them hold each
// achievement, ignoring revoked unlocks.
func (s *Store) UnlockCounts(ctx context.Context) (int, map[string]int, error) {
	var players int
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM players`).Scan(&players); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT achievement_id, COUNT(*) FROM unlocks WHERE revoked_at IS NULL GROUP BY achievement_id`)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()

	counts := map[string]int{}
	for rows.Next() {
		var id string
		var n int
		if err := rows.Scan(&id, &n); err != nil {
			return 0, nil, err
		}
		counts[id] = n
	}
	return players, counts, rows.Err()
}

// Purchases returns every shop item a player owns.
func (s *Store) Purchases(ctx context.Context, uuid string) ([]Purchase, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT item_id, price FROM purchases WHERE uuid = ? ORDER BY purchased_at, item_id`, uuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var purchases []Purchase
	for rows.Next() {
		var p Purchase
		if err := rows.Scan(&p.ItemID, &p.Price); err != nil {
			return nil, err
		}
		purchases = append(purchases, p)
	}
	return purchases, rows.Err()
}

// Buy records the purchase of every item, or none of them. earned is the
// player's points from achievements; the balance left after what they have
// already spent must cover the whole order. It fails with ErrAlreadyOwned or
// ErrNotEnoughPoints.
func (s *Store) Buy(ctx context.Context, uuid string, items []Purchase, earned int, now time.Time) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	total := 0
	for _, item := range items {
		var owned bool
		if err := tx.QueryRowContext(ctx,
			`SELECT EXISTS (SELECT 1 FROM purchases WHERE uuid = ? AND item_id = ?)`, uuid, item.ItemID).
			Scan(&owned); err != nil {
			return err
		}
		if owned {
			return ErrAlreadyOwned
		}
		total += item.Price
	}

	var spent int
	if err := tx.QueryRowContext(ctx,
		`SELECT COALESCE(SUM(price), 0) FROM purchases WHERE uuid = ?`, uuid).Scan(&spent); err != nil {
		return err
	}
	if earned-spent < total {
		return ErrNotEnoughPoints
	}

	for _, item := range items {
		if _, err := tx.ExecContext(ctx,
			`INSERT INTO purchases (uuid, item_id, price, purchased_at) VALUES (?, ?, ?, ?)`,
			uuid, item.ItemID, item.Price, now.UnixMilli()); err != nil {
			return err
		}
	}
	return tx.Commit()
}
