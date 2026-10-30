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
// Players cannot refund their own purchases; reset_purchases is the only way.
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
	refunded := 0
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
		case "reset_purchases":
			var price int
			if err := tx.QueryRowContext(ctx, `SELECT price FROM purchases WHERE uuid = ? AND item_id = ?`, uuid, id).Scan(&price); err != nil {
				if errors.Is(err, sql.ErrNoRows) {
					continue
				}
				return err
			}
			_, err = tx.ExecContext(ctx, `DELETE FROM purchases WHERE uuid = ? AND item_id = ?`, uuid, id)
			refunded += price
			count = 1
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
	// Styles reference owned items, so a reset also puts both slots back to native.
	if action == "reset_purchases" {
		if _, err := tx.ExecContext(ctx, `UPDATE players SET prefix_style = NULL, suffix_style = NULL WHERE uuid = ?`, uuid); err != nil {
			return err
		}
	}
	details, err := json.Marshal(struct {
		IDs            []string `json:"ids"`
		PreviousReason string   `json:"previousReason,omitempty"`
		Refunded       int      `json:"refunded,omitempty"`
	}{changed, previousReason, refunded})
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO moderation_log(actor, uuid, action, reason, details, created_at) VALUES(?, ?, ?, ?, ?, ?)`, actor, uuid, action, reason, string(details), now.UnixMilli()); err != nil {
		return err
	}
	return tx.Commit()
}

// PurchaseTotals counts what ResetAllPurchases would refund.
func (s *Store) PurchaseTotals(ctx context.Context) (players, items, points int, err error) {
	err = s.db.QueryRowContext(ctx, `SELECT COUNT(DISTINCT uuid), COUNT(*), COALESCE(SUM(price), 0) FROM purchases`).Scan(&players, &items, &points)
	return
}

// ResetAllPurchases refunds every player's purchases and puts their slots back
// to native in one transaction, with an audit entry for each affected player.
// It returns the affected UUIDs so the caller can refresh their titles.
func (s *Store) ResetAllPurchases(ctx context.Context, actor, reason string, now time.Time) ([]string, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, `SELECT uuid, item_id, price FROM purchases ORDER BY uuid, purchased_at, item_id`)
	if err != nil {
		return nil, err
	}
	type refund struct {
		ids      []string
		refunded int
	}
	uuids := []string{}
	refunds := map[string]*refund{}
	for rows.Next() {
		var uuid, id string
		var price int
		if err := rows.Scan(&uuid, &id, &price); err != nil {
			rows.Close()
			return nil, err
		}
		if refunds[uuid] == nil {
			refunds[uuid] = &refund{}
			uuids = append(uuids, uuid)
		}
		refunds[uuid].ids = append(refunds[uuid].ids, id)
		refunds[uuid].refunded += price
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if len(uuids) == 0 {
		return nil, ErrNoChange
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM purchases`); err != nil {
		return nil, err
	}
	for _, uuid := range uuids {
		r := refunds[uuid]
		if _, err := tx.ExecContext(ctx, `UPDATE players SET prefix_style = NULL, suffix_style = NULL WHERE uuid = ?`, uuid); err != nil {
			return nil, err
		}
		details, err := json.Marshal(struct {
			IDs      []string `json:"ids"`
			Refunded int      `json:"refunded,omitempty"`
		}{r.ids, r.refunded})
		if err != nil {
			return nil, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO moderation_log(actor, uuid, action, reason, details, created_at) VALUES(?, ?, ?, ?, ?, ?)`, actor, uuid, "reset_all_purchases", reason, string(details), now.UnixMilli()); err != nil {
			return nil, err
		}
	}
	return uuids, tx.Commit()
}
