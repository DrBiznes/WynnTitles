// Package catalog loads and validates the achievement catalog and the style
// shop embedded in the server binary. The catalog is the single source of truth
// for achievements, title words and shop items; the mod downloads it from
// GET /v1/catalog at runtime.
package catalog

import (
	"bytes"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
)

//go:embed achievements.json
var embedded []byte

//go:embed shop.json
var embeddedShop []byte

type Effect struct {
	Type     string   `json:"type"`
	Colors   []string `json:"colors,omitempty"`
	Palette  string   `json:"palette,omitempty"`
	Animated bool     `json:"animated,omitempty"`
	CycleMs  int      `json:"cycleMs,omitempty"`
}

type Run struct {
	Text          string  `json:"text"`
	Color         string  `json:"color,omitempty"`
	Bold          bool    `json:"bold,omitempty"`
	Italic        bool    `json:"italic,omitempty"`
	Underlined    bool    `json:"underlined,omitempty"`
	Strikethrough bool    `json:"strikethrough,omitempty"`
	Obfuscated    bool    `json:"obfuscated,omitempty"`
	Effect        *Effect `json:"effect,omitempty"`
}

// Word is a title word. Text is the plain form and must equal the
// concatenation of every run's text. Most words have no runs in
// achievements.json and are shown plain; a few have a native style, which a
// shop style replaces.
type Word struct {
	Text string `json:"text"`
	Runs []Run  `json:"runs"`
}

type Rewards struct {
	Prefix *Word `json:"prefix,omitempty"`
	Suffix *Word `json:"suffix,omitempty"`
}

// Trigger tells the mod when to claim an achievement. Type picks the detector;
// the remaining fields are its parameters.
type Trigger struct {
	Type          string `json:"type"`
	QuestName     string `json:"questName,omitempty"`
	ClassName     string `json:"className,omitempty"`
	Level         int    `json:"level,omitempty"`
	TerritoryName string `json:"territoryName,omitempty"`
	ItemName      string `json:"itemName,omitempty"`
	Amount        int    `json:"amount,omitempty"`
	Statistic     string `json:"statistic,omitempty"`
	RaidName      string `json:"raidName,omitempty"`
}

// Icon is drawn inside the tier frame. Model is an item model from the
// Wynncraft resource pack, relative to minecraft:item/ (for example
// "wynn/prop/quest_scroll"); the mod finds which item shows it. Fallback is a
// vanilla item shown when the pack is not loaded or the model is gone.
type Icon struct {
	Model    string `json:"model,omitempty"`
	Fallback string `json:"fallback"`
}

type Achievement struct {
	ID          string  `json:"id"`
	Category    string  `json:"category"`
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Tier        string  `json:"tier"`
	Points      int     `json:"points"`
	Icon        Icon    `json:"icon"`
	Trigger     Trigger `json:"trigger"`
	Rewards     Rewards `json:"rewards"`
}

type Catalog struct {
	Achievements []Achievement
	Shop         []ShopItem
	// Version is a content hash, used as the ETag for GET /v1/catalog.
	Version string

	byID     map[string]*Achievement
	shopByID map[string]*ShopItem
}

var tierPoints = map[string]int{"bronze": 1, "silver": 3, "gold": 5}

var statistics = map[string]bool{"mobsKilled": true, "chestsFound": true}

var palettes = map[string]bool{
	"rainbow": true, "red": true, "red_purple": true, "flame": true, "ember": true, "sunset": true,
	"ocean": true, "ice": true, "toxic": true, "forest": true, "royal": true, "void": true,
}

var namedColors = map[string]bool{
	"black": true, "dark_blue": true, "dark_green": true, "dark_aqua": true, "dark_red": true,
	"dark_purple": true, "gold": true, "gray": true, "dark_gray": true, "blue": true, "green": true,
	"aqua": true, "red": true, "light_purple": true, "yellow": true, "white": true,
}

var hexColor = regexp.MustCompile(`^#[0-9a-fA-F]{6}$`)

var (
	modelPath   = regexp.MustCompile(`^[a-z0-9_.-]+(/[a-z0-9_.-]+)*$`)
	vanillaItem = regexp.MustCompile(`^minecraft:[a-z0-9_]+$`)
)

// Load parses and validates the embedded catalog and shop.
func Load() (*Catalog, error) {
	return Parse(embedded, embeddedShop)
}

// Parse parses and validates achievements.json and shop.json. Unknown fields
// are rejected so typos fail at startup instead of silently doing nothing.
func Parse(achievementsData, shopData []byte) (*Catalog, error) {
	var achievements []Achievement
	if err := decodeStrict(achievementsData, &achievements); err != nil {
		return nil, fmt.Errorf("parse catalog: %w", err)
	}
	var shop []ShopItem
	if err := decodeStrict(shopData, &shop); err != nil {
		return nil, fmt.Errorf("parse shop: %w", err)
	}

	c := &Catalog{Achievements: achievements, byID: make(map[string]*Achievement, len(achievements))}
	for i := range c.Achievements {
		a := &c.Achievements[i]
		if err := validate(a); err != nil {
			return nil, fmt.Errorf("achievement %q: %w", a.ID, err)
		}
		if _, dup := c.byID[a.ID]; dup {
			return nil, fmt.Errorf("duplicate achievement id %q", a.ID)
		}
		c.byID[a.ID] = a
	}
	if err := c.setShop(shop); err != nil {
		return nil, err
	}

	normalized, err := json.Marshal([]any{c.Achievements, c.Shop})
	if err != nil {
		return nil, err
	}
	sum := sha256.Sum256(normalized)
	c.Version = hex.EncodeToString(sum[:8])
	return c, nil
}

func decodeStrict(data []byte, v any) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.DisallowUnknownFields()
	return dec.Decode(v)
}

func (c *Catalog) Get(id string) (*Achievement, bool) {
	a, ok := c.byID[id]
	return a, ok
}

func validate(a *Achievement) error {
	for field, value := range map[string]string{
		"id": a.ID, "category": a.Category, "name": a.Name, "description": a.Description,
	} {
		if value == "" {
			return fmt.Errorf("%s is required", field)
		}
	}

	points, ok := tierPoints[a.Tier]
	if !ok {
		return fmt.Errorf("tier must be bronze, silver, or gold")
	}
	if a.Points != points {
		return fmt.Errorf("points must be %d for %s tier", points, a.Tier)
	}

	if a.Rewards.Prefix == nil && a.Rewards.Suffix == nil {
		return fmt.Errorf("must reward a prefix or a suffix")
	}
	if a.Rewards.Prefix != nil {
		if err := validateWord(a.Rewards.Prefix); err != nil {
			return fmt.Errorf("rewards.prefix: %w", err)
		}
	}
	if a.Rewards.Suffix != nil {
		if err := validateWord(a.Rewards.Suffix); err != nil {
			return fmt.Errorf("rewards.suffix: %w", err)
		}
	}

	if err := validateIcon(&a.Icon); err != nil {
		return fmt.Errorf("icon: %w", err)
	}
	return validateTrigger(&a.Trigger)
}

func validateIcon(i *Icon) error {
	if i.Model != "" && !modelPath.MatchString(i.Model) {
		return fmt.Errorf("model %q must be a lowercase path like wynn/prop/quest_scroll", i.Model)
	}
	if !vanillaItem.MatchString(i.Fallback) {
		return fmt.Errorf("fallback %q must be a vanilla item id like minecraft:book", i.Fallback)
	}
	return nil
}

func validateTrigger(t *Trigger) error {
	var missing []string
	need := func(name string, ok bool) {
		if !ok {
			missing = append(missing, name)
		}
	}

	switch t.Type {
	case "quest":
		need("questName", t.QuestName != "")
	case "combatLevel":
		need("className", t.ClassName != "")
		need("level", t.Level > 0)
	case "combatLevelAnyClass", "totalProfessionLevel":
		need("level", t.Level > 0)
	case "location":
		need("territoryName", t.TerritoryName != "")
	case "inventoryItem":
		need("itemName", t.ItemName != "")
		need("amount", t.Amount > 0)
	case "raid":
		need("raidName", t.RaidName != "")
		need("amount", t.Amount > 0)
	case "statistic":
		need("amount", t.Amount > 0)
		if !statistics[t.Statistic] {
			return fmt.Errorf("trigger.statistic must be mobsKilled or chestsFound")
		}
	default:
		return fmt.Errorf("unsupported trigger type %q", t.Type)
	}

	if len(missing) > 0 {
		return fmt.Errorf("trigger %s requires %s", t.Type, strings.Join(missing, ", "))
	}
	return nil
}

func validateWord(w *Word) error {
	if w.Text == "" {
		return fmt.Errorf("text is required")
	}
	if len(w.Runs) == 0 {
		w.Runs = []Run{{Text: w.Text}}
		return nil
	}

	var joined strings.Builder
	for i := range w.Runs {
		r := &w.Runs[i]
		if r.Text == "" {
			return fmt.Errorf("runs[%d].text is required", i)
		}
		joined.WriteString(r.Text)
		if r.Color != "" {
			if err := validateColor(r.Color); err != nil {
				return fmt.Errorf("runs[%d].color: %w", i, err)
			}
			r.Color = strings.ToLower(r.Color)
		}
		if r.Effect != nil {
			if err := validateEffect(r.Effect); err != nil {
				return fmt.Errorf("runs[%d].effect: %w", i, err)
			}
		}
	}

	if joined.String() != w.Text {
		return fmt.Errorf("runs must join to %q, got %q", w.Text, joined.String())
	}
	return nil
}

func validateEffect(e *Effect) error {
	if e.Type != "rainbow" && e.Type != "gradient" {
		return fmt.Errorf("type must be rainbow or gradient")
	}
	if e.Colors != nil && len(e.Colors) < 2 {
		return fmt.Errorf("colors needs at least two entries")
	}
	for i, color := range e.Colors {
		if err := validateColor(color); err != nil {
			return fmt.Errorf("colors[%d]: %w", i, err)
		}
		e.Colors[i] = strings.ToLower(color)
	}
	if e.Palette != "" {
		e.Palette = strings.ToLower(e.Palette)
		if !palettes[e.Palette] {
			return fmt.Errorf("unknown palette %q", e.Palette)
		}
	}
	if e.Type == "gradient" && len(e.Colors) == 0 && e.Palette == "" {
		return fmt.Errorf("gradient needs colors or a palette")
	}
	if e.CycleMs < 0 {
		return fmt.Errorf("cycleMs must be positive")
	}
	return nil
}

func validateColor(color string) error {
	if namedColors[strings.ToLower(color)] || hexColor.MatchString(color) {
		return nil
	}
	return fmt.Errorf("%q is not a Minecraft color name or #rrggbb", color)
}
