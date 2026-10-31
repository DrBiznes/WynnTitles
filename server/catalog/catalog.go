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

// Effect colors a run letter by letter. Animation picks how an animated
// effect moves (see animations); empty is "flow", the scrolling gradient that
// native styles and rainbow use. Mods that predate animations ignore the field
// and show flow, so it degrades gracefully. CycleMs overrides the animation's
// own beat.
type Effect struct {
	Type      string   `json:"type"`
	Colors    []string `json:"colors,omitempty"`
	Palette   string   `json:"palette,omitempty"`
	Animated  bool     `json:"animated,omitempty"`
	CycleMs   int      `json:"cycleMs,omitempty"`
	Animation string   `json:"animation,omitempty"`
}

type Run struct {
	Text          string  `json:"text"`
	Font          string  `json:"font,omitempty"`
	LetterColor   string  `json:"letterColor,omitempty"`
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

	DungeonName     string   `json:"dungeonName,omitempty"`
	DungeonNames    []string `json:"dungeonNames,omitempty"`
	ProfessionName  string   `json:"professionName,omitempty"`
	ProfessionNames []string `json:"professionNames,omitempty"`
	TerritoryNames  []string `json:"territoryNames,omitempty"`
	Gamemodes       []string `json:"gamemodes,omitempty"`
	GuildRank       string   `json:"guildRank,omitempty"`
	Rarity          string   `json:"rarity,omitempty"`
	// ItemSets is met when every item of any one set is held or worn.
	ItemSets [][]string `json:"itemSets,omitempty"`
	// X, Z and Radius are a circle on the map, in blocks; height is ignored.
	X      int `json:"x,omitempty"`
	Z      int `json:"z,omitempty"`
	Radius int `json:"radius,omitempty"`
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

// Statistics are account-wide counters from the Wynncraft API's globalData.
var statistics = map[string]bool{
	"mobsKilled": true, "chestsFound": true, "wars": true, "dungeonsCompleted": true, "raidsCompleted": true,
	"lootruns": true, "caves": true, "worldEvents": true, "completedQuests": true,
}

var professions = map[string]bool{
	"Woodcutting": true, "Mining": true, "Fishing": true, "Farming": true, "Alchemism": true, "Armouring": true,
	"Cooking": true, "Jeweling": true, "Scribing": true, "Tailoring": true, "Weaponsmithing": true, "Woodworking": true,
}

// Gamemodes and guild ranks use the Wynncraft API's names.
var gamemodes = map[string]bool{
	"hardcore": true, "ironman": true, "ultimate_ironman": true, "craftsman": true, "hunted": true,
}

var guildRanks = map[string]bool{
	"recruit": true, "recruiter": true, "captain": true, "strategist": true, "chief": true, "owner": true,
}

var rarities = map[string]bool{
	"unique": true, "rare": true, "set": true, "legendary": true, "fabled": true, "mythic": true,
}

var palettes = map[string]bool{
	"rainbow": true, "red": true, "red_purple": true, "flame": true, "ember": true, "sunset": true,
	"ocean": true, "ice": true, "toxic": true, "forest": true, "royal": true, "void": true,
	"nebula": true, "borealis": true, "storm": true, "blossom": true, "brass": true,
	"ash": true, "gaze": true,
}

// Animations are how an animated effect moves; the mod draws them (titles/TitleAnimation.java):
//   - flow: the gradient scrolls along the word
//   - pulse: the whole word breathes through the palette at once
//   - shimmer: a bright highlight sweeps across the word
//   - wave: colors ripple letter by letter
//   - flicker: letters waver and flare at random, like fire or a glitch
//   - sparkle: letters twinkle bright one at a time, like sparks or stars
//   - comet: a white head with a fading tail streaks across, then rests
//   - aurora: two gradients drift opposite ways at different speeds, blended
//   - charge: the word fills from dim grey, flashes, drains and rests dim
//   - ripple: rings of color spread out from the middle letter
//   - converge: gleams from both ends meet in the middle and flash
//   - oculus: scrambled lids open from the middle, a bold iris glances, they shut
//
// The rest are native only (see nativeOnly):
//   - decipher: letters scramble, show as Wynnic, then resolve one by one
//   - glitch: still, then bursts of obfuscated cyan and magenta letters
//   - eclipse: a shadow with a bright rim crosses the word
//   - strike: a lightning flash, each letter cooling back at its own pace
//   - heartbeat: a lub-dub double beat that brightens the word
//   - swell: a bold crest rolls along a flowing gradient
//   - ascend: each color wipes over the last, and the final one lands and holds bold
//   - reel: letters spin scrambled, lock in from the left, then a gleam crosses
//   - prism: the word flashes white, and the light scatters into its colors
var animations = map[string]bool{
	"flow": true, "pulse": true, "shimmer": true, "wave": true, "flicker": true, "sparkle": true,
	"comet": true, "aurora": true, "charge": true, "ripple": true, "converge": true, "oculus": true,
	"decipher": true, "glitch": true, "eclipse": true, "strike": true, "heartbeat": true, "swell": true,
	"ascend": true, "reel": true, "prism": true,
}

// nativeOnly animations belong to achievements' native styles and are never
// sold, like obfuscated, so the biggest titles have looks nobody can buy.
var nativeOnly = map[string]bool{
	"decipher": true, "glitch": true, "eclipse": true, "strike": true, "heartbeat": true, "swell": true,
	"ascend": true, "reel": true, "prism": true,
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
			return fmt.Errorf("unknown trigger.statistic %q", t.Statistic)
		}
	case "dungeon":
		need("dungeonName", t.DungeonName != "")
		need("amount", t.Amount > 0)
	case "dungeonSet":
		need("dungeonNames", allSet(t.DungeonNames))
	case "professionLevel":
		need("level", t.Level > 0)
		if !professions[t.ProfessionName] {
			return fmt.Errorf("unknown trigger.professionName %q", t.ProfessionName)
		}
	case "professionLevelAll":
		need("professionNames", len(t.ProfessionNames) > 0)
		need("level", t.Level > 0)
		for _, name := range t.ProfessionNames {
			if !professions[name] {
				return fmt.Errorf("unknown profession %q in trigger.professionNames", name)
			}
		}
	case "characterRoster":
		need("level", t.Level > 0)
	case "gamemodeLevel":
		need("gamemodes", len(t.Gamemodes) > 0)
		need("level", t.Level > 0)
		for _, mode := range t.Gamemodes {
			if !gamemodes[mode] {
				return fmt.Errorf("unknown gamemode %q in trigger.gamemodes", mode)
			}
		}
	case "playtime", "accountAge", "leaderboard", "shinyItem":
		// Hours, years, the worst placement that counts, and items held.
		need("amount", t.Amount > 0)
	case "guild":
		if !guildRanks[t.GuildRank] {
			return fmt.Errorf("unknown trigger.guildRank %q", t.GuildRank)
		}
	case "coordinates":
		need("radius", t.Radius > 0)
	case "itemAtLocation":
		need("itemName", t.ItemName != "")
		need("radius", t.Radius > 0)
	case "territorySet":
		need("territoryNames", allNamed(t.TerritoryNames))
	case "itemRarityCount":
		need("amount", t.Amount > 0)
		if !rarities[t.Rarity] {
			return fmt.Errorf("unknown trigger.rarity %q", t.Rarity)
		}
	case "itemSet":
		ok := len(t.ItemSets) > 0
		for _, set := range t.ItemSets {
			ok = ok && allSet(set)
		}
		need("itemSets", ok)
	default:
		return fmt.Errorf("unsupported trigger type %q", t.Type)
	}

	if len(missing) > 0 {
		return fmt.Errorf("trigger %s requires %s", t.Type, strings.Join(missing, ", "))
	}
	return nil
}

// allSet reports whether names is a list of at least two names, none empty.
func allSet(names []string) bool {
	return len(names) >= 2 && allNamed(names)
}

func allNamed(names []string) bool {
	if len(names) == 0 {
		return false
	}
	for _, name := range names {
		if name == "" {
			return false
		}
	}
	return true
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
	if e.Animation != "" {
		if !animations[e.Animation] {
			return fmt.Errorf("unknown animation %q", e.Animation)
		}
		if !e.Animated {
			return fmt.Errorf("animation needs animated: true")
		}
	}
	return nil
}

func validateColor(color string) error {
	if namedColors[strings.ToLower(color)] || hexColor.MatchString(color) {
		return nil
	}
	return fmt.Errorf("%q is not a Minecraft color name or #rrggbb", color)
}
