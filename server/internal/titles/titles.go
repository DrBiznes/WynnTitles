// Package titles derives a player's available title words from their unlocks
// and keeps the public uuid -> display title map in memory.
package titles

import (
	"sync"

	"github.com/DrBzines/WynnTitles/server/catalog"
)

// Title is what other players see above someone's head.
type Title struct {
	Plain string        `json:"plain"`
	Runs  []catalog.Run `json:"runs"`
}

// Words lists the prefixes and suffixes a player may equip, in catalog order.
type Words struct {
	Prefixes []catalog.Word `json:"prefixes"`
	Suffixes []catalog.Word `json:"suffixes"`
}

// Available returns the words unlocked by the given achievement IDs. When two
// achievements reward the same text, the first one in catalog order wins.
func Available(cat *catalog.Catalog, unlockedIDs []string) Words {
	unlocked := make(map[string]bool, len(unlockedIDs))
	for _, id := range unlockedIDs {
		unlocked[id] = true
	}

	words := Words{Prefixes: []catalog.Word{}, Suffixes: []catalog.Word{}}
	seenPrefix, seenSuffix := map[string]bool{}, map[string]bool{}
	for _, a := range cat.Achievements {
		if !unlocked[a.ID] {
			continue
		}
		if p := a.Rewards.Prefix; p != nil && !seenPrefix[p.Text] {
			seenPrefix[p.Text] = true
			words.Prefixes = append(words.Prefixes, *p)
		}
		if s := a.Rewards.Suffix; s != nil && !seenSuffix[s.Text] {
			seenSuffix[s.Text] = true
			words.Suffixes = append(words.Suffixes, *s)
		}
	}
	return words
}

func (w Words) Prefix(text string) (catalog.Word, bool) {
	return find(w.Prefixes, text)
}

func (w Words) Suffix(text string) (catalog.Word, bool) {
	return find(w.Suffixes, text)
}

func find(words []catalog.Word, text string) (catalog.Word, bool) {
	for _, w := range words {
		if w.Text == text {
			return w, true
		}
	}
	return catalog.Word{}, false
}

// Slot is one equipped word and the shop style it is worn in. A nil Style
// shows the word's native style.
type Slot struct {
	Word  string
	Style *catalog.Style
}

// Build joins the equipped words into a display title. Words that are no
// longer available (renamed in the catalog, or revoked) are dropped, so the
// result is nil when nothing valid is equipped. Styles must already be checked
// against the catalog and the player's purchases.
func Build(cat *catalog.Catalog, words Words, prefix, suffix Slot) *Title {
	p, hasPrefix := words.Prefix(prefix.Word)
	s, hasSuffix := words.Suffix(suffix.Word)
	if hasPrefix && prefix.Style != nil {
		p = cat.Restyle(p, prefix.Style)
	}
	if hasSuffix && suffix.Style != nil {
		s = cat.Restyle(s, suffix.Style)
	}

	var t Title
	if hasPrefix {
		t.Plain = p.Text
		t.Runs = append(t.Runs, p.Runs...)
	}
	if hasPrefix && hasSuffix {
		t.Plain += " "
		t.Runs = append(t.Runs, catalog.Run{Text: " "})
	}
	if hasSuffix {
		t.Plain += s.Text
		t.Runs = append(t.Runs, s.Runs...)
	}

	if t.Plain == "" {
		return nil
	}
	return &t
}

// Cache holds the display title of every player who has one equipped. The
// batch lookup endpoint reads only from here, never from SQLite.
type Cache struct {
	mu     sync.RWMutex
	titles map[string]*Title
}

func NewCache() *Cache {
	return &Cache{titles: map[string]*Title{}}
}

// Set stores a player's title; nil removes it.
func (c *Cache) Set(uuid string, t *Title) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if t == nil {
		delete(c.titles, uuid)
	} else {
		c.titles[uuid] = t
	}
}

// Lookup returns a title (or nil) for every requested UUID, so clients can
// cache "no title" answers too.
func (c *Cache) Lookup(uuids []string) map[string]*Title {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make(map[string]*Title, len(uuids))
	for _, uuid := range uuids {
		out[uuid] = c.titles[uuid]
	}
	return out
}

func (c *Cache) Len() int {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return len(c.titles)
}
