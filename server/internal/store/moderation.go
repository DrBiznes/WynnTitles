package store

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"slices"
	"time"
)

var ErrNoChange = errors.New("nothing changed; the record may already have been updated")

// PlayerSearch narrows and orders SearchPlayers. Sort is "joined", "claims" or
// anything else for most recently seen.
type PlayerSearch struct {
	Query, Sort   string
	SuspendedOnly bool
	Page          int
}

// PlayerRow is a player with their count of active claims.
type PlayerRow struct {
	Player
	Claims int
}

// SearchPlayers uses a literal substring, with bounded pages even for an empty search.
func (s *Store) SearchPlayers(ctx context.Context, search PlayerSearch) ([]PlayerRow, error) {
	order := "last_seen_at DESC"
	switch search.Sort {
	case "joined":
		order = "created_at DESC"
	case "claims":
		order = "claims DESC"
	}
	rows, err := s.db.QueryContext(ctx, `SELECT `+playerColumns+`,
 (SELECT COUNT(*) FROM unlocks u WHERE u.uuid = players.uuid AND u.revoked_at IS NULL) AS claims FROM players
 WHERE (instr(lower(username), lower(?)) > 0 OR instr(replace(lower(uuid), '-', ''), replace(lower(?), '-', '')) > 0)
 AND (NOT ? OR suspended_at IS NOT NULL)
 ORDER BY `+order+`, uuid LIMIT 50 OFFSET ?`, search.Query, search.Query, search.SuspendedOnly, search.Page*50)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	players := []PlayerRow{}
	for rows.Next() {
		var p PlayerRow
		var created, seen int64
		if err := rows.Scan(&p.UUID, &p.Username, &p.EquippedPrefix, &p.EquippedSuffix, &p.PrefixStyle, &p.SuffixStyle,
			&created, &seen, &p.Suspended, &p.SuspendReason, &p.Claims); err != nil {
			return nil, err
		}
		p.CreatedAt, p.LastSeenAt = time.UnixMilli(created), time.UnixMilli(seen)
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
	rows, err := s.db.QueryContext(ctx, `SELECT id, actor, COALESCE(uuid, ''), action, reason, details, created_at
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
	// Suspending takes the claims to revoke as items, so both commit together.
	// Sessions stay valid: a rejected token would send the mod back through the
	// Mojang login on every retry, while a 403 just tells it to stop.
	statusChanged := false
	if action == "suspend" || action == "unsuspend" {
		query := `UPDATE players SET suspended_at = ?, suspend_reason = ? WHERE uuid = ? AND suspended_at IS NULL`
		args := []any{now.UnixMilli(), reason, uuid}
		if action == "unsuspend" {
			query, args = `UPDATE players SET suspended_at = NULL, suspend_reason = NULL WHERE uuid = ? AND suspended_at IS NOT NULL`, []any{uuid}
		}
		result, err := tx.ExecContext(ctx, query, args...)
		if err != nil {
			return err
		}
		n, err := result.RowsAffected()
		if err != nil {
			return err
		}
		if n == 0 {
			return ErrNoChange
		}
		statusChanged = true
	}
	revoking := action == "revoke" || action == "revoke_all" || action == "suspend"
	changed := []string{}
	previousReason := ""
	refunded := 0
	for _, item := range items {
		id := item.ID
		var count int64
		switch action {
		case "revoke", "restore", "revoke_all", "restore_all", "suspend":
			var revoked bool
			if err := tx.QueryRowContext(ctx, `SELECT revoked_at IS NOT NULL, COALESCE(revoke_reason, '') FROM unlocks WHERE uuid = ? AND achievement_id = ?`, uuid, id).Scan(&revoked, &previousReason); err != nil {
				if errors.Is(err, sql.ErrNoRows) {
					return ErrNotFound
				}
				return err
			}
			if revoking == revoked {
				return ErrNoChange
			}
			if revoking {
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
	if len(changed) == 0 && !statusChanged {
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

// ClaimControls are live switches for what the server accepts, kept in the
// database so a bad detector or mod release needs no deploy to stop.
type ClaimControls struct {
	Paused             bool     `json:"paused,omitempty"`
	PausedAchievements []string `json:"pausedAchievements,omitempty"`
	// MinModVersion refuses claims from older mods; LatestModVersion only flags
	// claims from versions that were never released. Empty turns either off.
	MinModVersion    string `json:"minModVersion,omitempty"`
	LatestModVersion string `json:"latestModVersion,omitempty"`
}

// AchievementPaused says whether one achievement is paused on its own.
func (c ClaimControls) AchievementPaused(id string) bool {
	return slices.Contains(c.PausedAchievements, id)
}

func (s *Store) ClaimControls(ctx context.Context) (ClaimControls, error) {
	var controls ClaimControls
	var value string
	err := s.db.QueryRowContext(ctx, `SELECT value FROM claim_controls WHERE id = 1`).Scan(&value)
	if errors.Is(err, sql.ErrNoRows) {
		return controls, nil
	}
	if err != nil {
		return controls, err
	}
	return controls, json.Unmarshal([]byte(value), &controls)
}

// SetClaimControls commits the switches and their audit entry together.
func (s *Store) SetClaimControls(ctx context.Context, actor, action, reason string, controls ClaimControls, now time.Time) error {
	value, err := json.Marshal(controls)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `INSERT INTO claim_controls(id, value) VALUES(1, ?)
 ON CONFLICT(id) DO UPDATE SET value = excluded.value`, string(value)); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO moderation_log(actor, uuid, action, reason, details, created_at) VALUES(?, NULL, ?, ?, ?, ?)`, actor, action, reason, string(value), now.UnixMilli()); err != nil {
		return err
	}
	return tx.Commit()
}

// ClaimRow is a claim with the player it belongs to, for lists across players.
type ClaimRow struct {
	Unlock
	Username  string
	Suspended bool
}

const claimRowQuery = `SELECT u.uuid, u.achievement_id, u.trigger_type, COALESCE(u.evidence, ''), COALESCE(u.mod_version, ''),
 u.claimed_at, u.revoked_at IS NOT NULL, COALESCE(u.revoke_reason, ''), p.username, p.suspended_at IS NOT NULL
 FROM unlocks u JOIN players p ON p.uuid = u.uuid `

func (s *Store) claimRows(ctx context.Context, query string, args ...any) ([]ClaimRow, error) {
	rows, err := s.db.QueryContext(ctx, claimRowQuery+query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	claims := []ClaimRow{}
	for rows.Next() {
		var c ClaimRow
		var at int64
		if err := rows.Scan(&c.UUID, &c.AchievementID, &c.TriggerType, &c.Evidence, &c.ModVersion, &at, &c.Revoked, &c.RevokeReason, &c.Username, &c.Suspended); err != nil {
			return nil, err
		}
		c.ClaimedAt = time.UnixMilli(at)
		claims = append(claims, c)
	}
	return claims, rows.Err()
}

// RecentClaims returns the newest claims from every player.
func (s *Store) RecentClaims(ctx context.Context, limit int) ([]ClaimRow, error) {
	return s.claimRows(ctx, `ORDER BY u.claimed_at DESC, u.uuid, u.achievement_id LIMIT ?`, limit)
}

// AchievementClaims returns one page of an achievement's claims, revoked included.
func (s *Store) AchievementClaims(ctx context.Context, id string, page int) ([]ClaimRow, error) {
	return s.claimRows(ctx, `WHERE u.achievement_id = ? ORDER BY u.claimed_at DESC, u.uuid LIMIT 50 OFFSET ?`, id, page*50)
}

// ActiveClaimCount counts what ClearAchievement would remove.
func (s *Store) ActiveClaimCount(ctx context.Context, id string) (int, error) {
	var n int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM unlocks WHERE achievement_id = ? AND revoked_at IS NULL`, id).Scan(&n)
	return n, err
}

// ClearAchievement deletes every active claim of one achievement so a detector
// mistake can be undone and honest players can earn it again. Revoked claims
// stay, so a cheater cannot use this to escape a revocation. Each deleted claim
// is copied into its player's audit entry, and the affected UUIDs are returned.
func (s *Store) ClearAchievement(ctx context.Context, actor, id, reason string, now time.Time) ([]string, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, `SELECT uuid, trigger_type, COALESCE(evidence, ''), COALESCE(mod_version, ''), claimed_at
 FROM unlocks WHERE achievement_id = ? AND revoked_at IS NULL ORDER BY uuid`, id)
	if err != nil {
		return nil, err
	}
	type cleared struct {
		IDs        []string `json:"ids"`
		Trigger    string   `json:"triggerType"`
		Evidence   string   `json:"evidence"`
		ModVersion string   `json:"modVersion"`
		ClaimedAt  int64    `json:"claimedAt"`
	}
	uuids := []string{}
	entries := []cleared{}
	for rows.Next() {
		var uuid string
		c := cleared{IDs: []string{id}}
		if err := rows.Scan(&uuid, &c.Trigger, &c.Evidence, &c.ModVersion, &c.ClaimedAt); err != nil {
			rows.Close()
			return nil, err
		}
		uuids = append(uuids, uuid)
		entries = append(entries, c)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if len(uuids) == 0 {
		return nil, ErrNoChange
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM unlocks WHERE achievement_id = ? AND revoked_at IS NULL`, id); err != nil {
		return nil, err
	}
	for i, uuid := range uuids {
		details, err := json.Marshal(entries[i])
		if err != nil {
			return nil, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO moderation_log(actor, uuid, action, reason, details, created_at) VALUES(?, ?, ?, ?, ?, ?)`, actor, uuid, "clear_achievement", reason, string(details), now.UnixMilli()); err != nil {
			return nil, err
		}
	}
	return uuids, tx.Commit()
}

// ModVersionCount is how many players claimed from one mod version recently.
type ModVersionCount struct {
	Version         string
	Players, Claims int
}

// Overview is the headline numbers for the dashboard.
type Overview struct {
	Players, Suspended, Active24h, Active7d int
	Claims, Claims24h, Revoked              int
	DatabaseBytes                           int64
	ModVersions                             []ModVersionCount
}

func (s *Store) Overview(ctx context.Context, now time.Time) (Overview, error) {
	var o Overview
	day, week := now.Add(-24*time.Hour).UnixMilli(), now.Add(-7*24*time.Hour).UnixMilli()
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*), COUNT(suspended_at),
 COALESCE(SUM(last_seen_at > ?), 0), COALESCE(SUM(last_seen_at > ?), 0) FROM players`, day, week).
		Scan(&o.Players, &o.Suspended, &o.Active24h, &o.Active7d)
	if err != nil {
		return o, err
	}
	err = s.db.QueryRowContext(ctx, `SELECT COUNT(*), COALESCE(SUM(claimed_at > ?), 0), COUNT(revoked_at) FROM unlocks`, day).
		Scan(&o.Claims, &o.Claims24h, &o.Revoked)
	if err != nil {
		return o, err
	}
	err = s.db.QueryRowContext(ctx, `SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()`).Scan(&o.DatabaseBytes)
	if err != nil {
		return o, err
	}
	// Grants are not claims, so they say nothing about which mods are in use.
	rows, err := s.db.QueryContext(ctx, `SELECT COALESCE(mod_version, ''), COUNT(DISTINCT uuid), COUNT(*) FROM unlocks
 WHERE claimed_at > ? AND COALESCE(mod_version, '') <> 'dashboard' GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 20`, week)
	if err != nil {
		return o, err
	}
	defer rows.Close()
	for rows.Next() {
		var v ModVersionCount
		if err := rows.Scan(&v.Version, &v.Players, &v.Claims); err != nil {
			return o, err
		}
		o.ModVersions = append(o.ModVersions, v)
	}
	return o, rows.Err()
}
