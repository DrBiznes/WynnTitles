package store

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"
)

var ErrNoChange = errors.New("nothing changed; the record may already have been updated")

// SearchPlayers uses a literal substring, with bounded pages even for an empty search.
func (s *Store) SearchPlayers(ctx context.Context, query string, page int) ([]Player, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT `+playerColumns+` FROM players
 WHERE instr(lower(username), lower(?)) > 0 OR instr(replace(lower(uuid), '-', ''), replace(lower(?), '-', '')) > 0
 ORDER BY last_seen_at DESC, uuid LIMIT 50 OFFSET ?`, query, query, page*50)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	players := []Player{}
	for rows.Next() {
		p, err := scanPlayer(rows)
		if err != nil {
			return nil, err
		}
		players = append(players, p)
	}
	return players, rows.Err()
}

type ModerationEntry struct {
	ID                                   int64
	Actor, UUID, Action, Reason, Details string
	CreatedAt                            time.Time
}

func (s *Store) ModerationLog(ctx context.Context, uuid string) ([]ModerationEntry, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id, actor, uuid, action, reason, details, created_at
 FROM moderation_log WHERE (? = '' OR uuid = ?) ORDER BY id DESC LIMIT 100`, uuid, uuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []ModerationEntry{}
	for rows.Next() {
		var e ModerationEntry
		var at int64
		if err := rows.Scan(&e.ID, &e.Actor, &e.UUID, &e.Action, &e.Reason, &e.Details, &at); err != nil {
			return nil, err
		}
		e.CreatedAt = time.UnixMilli(at)
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

type ModerationItem struct {
	ID string
	// Catalog trigger retained for grants; developer provenance is in evidence.
	TriggerType string
}

// Moderate commits the change and its audit entry together. Callers validate
// catalog IDs and developer identity; revoked claims are restored explicitly.
func (s *Store) Moderate(ctx context.Context, actor, uuid, action, reason string, items []ModerationItem, now time.Time) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var exists bool
	if err := tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM players WHERE uuid = ?)`, uuid).Scan(&exists); err != nil {
		return err
	}
	if !exists {
		return ErrNotFound
	}
	changed := []string{}
	previousReason := ""
	for _, item := range items {
		id := item.ID
		var count int64
		switch action {
		case "revoke", "restore":
			var revoked bool
			if err := tx.QueryRowContext(ctx, `SELECT revoked_at IS NOT NULL, COALESCE(revoke_reason, '') FROM unlocks WHERE uuid = ? AND achievement_id = ?`, uuid, id).Scan(&revoked, &previousReason); err != nil {
				if errors.Is(err, sql.ErrNoRows) {
					return ErrNotFound
				}
				return err
			}
			if (action == "revoke" && revoked) || (action == "restore" && !revoked) {
				return ErrNoChange
			}
			if action == "revoke" {
				_, err = tx.ExecContext(ctx, `UPDATE unlocks SET revoked_at = ?, revoke_reason = ? WHERE uuid = ? AND achievement_id = ?`, now.UnixMilli(), reason, uuid, id)
			} else {
				_, err = tx.ExecContext(ctx, `UPDATE unlocks SET revoked_at = NULL, revoke_reason = NULL WHERE uuid = ? AND achievement_id = ?`, uuid, id)
			}
			count = 1
		case "grant_achievement":
			result, e := tx.ExecContext(ctx, `INSERT INTO unlocks(uuid, achievement_id, trigger_type, evidence, mod_version, claimed_at)
    VALUES (?, ?, ?, ?, 'dashboard', ?) ON CONFLICT(uuid, achievement_id) DO NOTHING`, uuid, id, item.TriggerType, `{"source":"developer_grant"}`, now.UnixMilli())
			err = e
			if err == nil {
				count, err = result.RowsAffected()
			}
		case "grant_styles":
			result, e := tx.ExecContext(ctx, `INSERT INTO purchases(uuid, item_id, price, purchased_at)
    VALUES (?, ?, 0, ?) ON CONFLICT(uuid, item_id) DO NOTHING`, uuid, id, now.UnixMilli())
			err = e
			if err == nil {
				count, err = result.RowsAffected()
			}
		default:
			return errors.New("unknown moderation action")
		}
		if err != nil {
			return err
		}
		if count > 0 {
			changed = append(changed, id)
		}
	}
	if len(changed) == 0 {
		return ErrNoChange
	}
	details, err := json.Marshal(struct {
		IDs            []string `json:"ids"`
		PreviousReason string   `json:"previousReason,omitempty"`
	}{changed, previousReason})
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO moderation_log(actor, uuid, action, reason, details, created_at) VALUES(?, ?, ?, ?, ?, ?)`, actor, uuid, action, reason, string(details), now.UnixMilli()); err != nil {
		return err
	}
	return tx.Commit()
}
