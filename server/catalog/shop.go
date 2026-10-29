package catalog

import (
	"fmt"
	"regexp"
	"strings"
)

// ShopItem is one style piece players buy with achievement points. Kind picks
// what it does: a color source ("color", "palette", "animatedPalette" or
// "rainbow"), a "format" flag or a "font". The remaining fields are its parameters; an
// animated palette names both its palette and its animation, so each one is a
// single finished look rather than a combination players assemble.
type ShopItem struct {
	ID        string   `json:"id"`
	Kind      string   `json:"kind"`
	Name      string   `json:"name"`
	Price     int      `json:"price"`
	Color     string   `json:"color,omitempty"`
	Palette   string   `json:"palette,omitempty"`
	Animation string   `json:"animation,omitempty"`
	Format    string   `json:"format,omitempty"`
	Font      string   `json:"font,omitempty"`
	Allows    []string `json:"allows,omitempty"`
	Formats   *bool    `json:"formats,omitempty"`
}

// IsSource reports whether the item colors a word, as opposed to formatting it.
func (i *ShopItem) IsSource() bool {
	return i.Kind == "color" || i.Kind == "palette" || i.Kind == "animatedPalette" || i.Kind == "rainbow"
}

// Style is how a player restyles one title slot, by shop item ID: at most one
// color source, one font and any number of compatible formats. It replaces the word's native style
// completely, so a style with nothing in it is not allowed.
type Style struct {
	Source      string   `json:"source,omitempty"`
	Formats     []string `json:"formats,omitempty"`
	Font        string   `json:"font,omitempty"`
	Color       string   `json:"color,omitempty"`
	LetterColor string   `json:"letterColor,omitempty"`
}

// Items lists every shop item the style uses.
func (s *Style) Items() []string {
	items := append([]string{}, s.Formats...)
	if s.Source != "" {
		items = append(items, s.Source)
	}
	if s.Font != "" {
		items = append(items, s.Font)
	}
	return items
}

// Obfuscated is left out on purpose: it is reserved for native styles.
var formats = map[string]bool{"bold": true, "italic": true, "underlined": true, "strikethrough": true}

var shopID = regexp.MustCompile(`^[a-z0-9_]+$`)

func (c *Catalog) setShop(items []ShopItem) error {
	c.Shop = items
	c.shopByID = make(map[string]*ShopItem, len(items))
	for i := range c.Shop {
		item := &c.Shop[i]
		if err := validateShopItem(item); err != nil {
			return fmt.Errorf("shop item %q: %w", item.ID, err)
		}
		if _, dup := c.shopByID[item.ID]; dup {
			return fmt.Errorf("duplicate shop item id %q", item.ID)
		}
		c.shopByID[item.ID] = item
	}
	return nil
}

func (c *Catalog) ShopItem(id string) (*ShopItem, bool) {
	item, ok := c.shopByID[id]
	return item, ok
}

func validateShopItem(i *ShopItem) error {
	if !shopID.MatchString(i.ID) {
		return fmt.Errorf("id must be lowercase letters, digits and underscores")
	}
	if i.Name == "" {
		return fmt.Errorf("name is required")
	}
	if i.Price <= 0 {
		return fmt.Errorf("price must be positive")
	}

	var want []string
	switch i.Kind {
	case "color":
		want = []string{"color"}
		if err := validateColor(i.Color); err != nil {
			return err
		}
	case "palette", "animatedPalette":
		want = []string{"palette"}
		// Rainbow is its own item, so palettes leave it out.
		if !palettes[i.Palette] || i.Palette == "rainbow" {
			return fmt.Errorf("unknown palette %q", i.Palette)
		}
		if i.Kind == "animatedPalette" {
			want = append(want, "animation")
			if !animations[i.Animation] {
				return fmt.Errorf("animation must be flow, pulse, shimmer, wave, flicker or sparkle")
			}
		}
	case "rainbow":
	case "font":
		want = []string{"font", "allows", "formats"}
		if !fontIDs[i.Font] {
			return fmt.Errorf("unknown font %q", i.Font)
		}
		if i.Formats == nil || len(i.Allows) == 0 {
			return fmt.Errorf("font requires allows and formats")
		}
		seen := map[string]bool{}
		for _, kind := range i.Allows {
			if kind != "color" && kind != "palette" && kind != "animatedPalette" && kind != "rainbow" {
				return fmt.Errorf("unknown allowed source %q", kind)
			}
			if seen[kind] {
				return fmt.Errorf("duplicate allowed source %q", kind)
			}
			seen[kind] = true
		}
		if i.Banner() && (len(i.Allows) != 1 || i.Allows[0] != "color" || *i.Formats) {
			return fmt.Errorf("banners require solid colors and no formats")
		}
	case "format":
		want = []string{"format"}
		if !formats[i.Format] {
			return fmt.Errorf("format must be bold, italic, underlined or strikethrough")
		}
	default:
		return fmt.Errorf("unsupported kind %q", i.Kind)
	}

	set := map[string]bool{
		"font": i.Font != "", "allows": i.Allows != nil, "formats": i.Formats != nil,
		"color": i.Color != "", "palette": i.Palette != "", "animation": i.Animation != "", "format": i.Format != "",
	}
	for _, field := range want {
		delete(set, field)
	}
	for field, present := range set {
		if present {
			return fmt.Errorf("%s does not apply to a %s item", field, i.Kind)
		}
	}
	return nil
}

// CheckStyle reports whether a style is well formed: every item exists and is
// in the right place, formats are not repeated, and it has at least one item.
// It does not check who owns the items.
func (c *Catalog) CheckStyle(s *Style) error {
	if s.Source == "" && len(s.Formats) == 0 && s.Font == "" {
		return fmt.Errorf("a style needs a color, format or font")
	}
	if s.Source != "" {
		item, ok := c.ShopItem(s.Source)
		if !ok || !item.IsSource() {
			return fmt.Errorf("%q is not a color, palette or rainbow", s.Source)
		}
	}
	seen := map[string]bool{}
	for _, id := range s.Formats {
		item, ok := c.ShopItem(id)
		if !ok || item.Kind != "format" {
			return fmt.Errorf("%q is not a format", id)
		}
		if seen[id] {
			return fmt.Errorf("format %q is listed twice", id)
		}
		seen[id] = true
	}
	if s.Font != "" {
		font, ok := c.ShopItem(s.Font)
		if !ok || font.Kind != "font" {
			return fmt.Errorf("%q is not a font", s.Font)
		}
		if len(s.Formats) > 0 && !*font.Formats {
			return fmt.Errorf("%s: formats are unavailable", font.Name)
		}
		if s.Source != "" {
			source, _ := c.ShopItem(s.Source)
			allowed := false
			for _, kind := range font.Allows {
				allowed = allowed || kind == source.Kind
			}
			if !allowed {
				return fmt.Errorf("%s: incompatible color source", font.Name)
			}
		}
		if s.LetterColor != "" && !font.Banner() {
			return fmt.Errorf("letter color requires a banner")
		}
	} else if s.LetterColor != "" {
		return fmt.Errorf("letter color requires a banner")
	}
	if s.Color != "" {
		if s.Font == "" || s.Source != "" {
			return fmt.Errorf("fallback color requires a font without a source")
		}
		if err := validateColor(s.Color); err != nil {
			return err
		}
	}
	if s.LetterColor != "" {
		if err := validateColor(s.LetterColor); err != nil {
			return err
		}
	}
	return nil
}

var fontIDs = map[string]bool{
	"minecraft:language/wynncraft": true, "minecraft:language/five": true, "minecraft:language/wynncraft_outline": true,
	"minecraft:language/wynnic": true, "minecraft:language/high_gavellian": true, "minecraft:language/old_fruman": true,
	"minecraft:banner/pill": true, "minecraft:banner/box": true, "minecraft:banner/flag": true, "minecraft:banner/ribbon": true,
}

func (i *ShopItem) Banner() bool { return strings.HasPrefix(i.Font, "minecraft:banner/") }

// BannerCharacter returns the first character with no safe banner glyph. ASCII
// case folding avoids turning unsupported letters (such as ß) into other words.
func BannerCharacter(text string) rune {
	for _, r := range text {
		if !(r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || r == ' ') {
			return r
		}
	}
	return 0
}

// CheckWordStyle also checks glyph coverage for a particular title word.
func (c *Catalog) CheckWordStyle(w Word, s *Style) error {
	if s == nil {
		return nil
	}
	if err := c.CheckStyle(s); err != nil {
		return err
	}
	if font, ok := c.ShopItem(s.Font); ok && font.Banner() {
		if r := BannerCharacter(w.Text); r != 0 {
			return fmt.Errorf("%s: no banner glyph for %q", font.Name, r)
		}
	}
	return nil
}

// Restyle returns the word as one run in the given style, which must have
// passed CheckStyle.
func (c *Catalog) Restyle(w Word, s *Style) Word {
	// Stored styles may outlive a catalog update; never render an invalid mix.
	if c.CheckWordStyle(w, s) != nil {
		return w
	}
	run := Run{Text: w.Text, Color: s.Color}
	if font, ok := c.ShopItem(s.Font); ok {
		run.Font, run.LetterColor = font.Font, s.LetterColor
	}
	if item, ok := c.ShopItem(s.Source); ok {
		switch item.Kind {
		case "color":
			run.Color = item.Color
		case "palette":
			run.Effect = &Effect{Type: "gradient", Palette: item.Palette}
		case "animatedPalette":
			run.Effect = &Effect{Type: "gradient", Palette: item.Palette, Animated: true, Animation: item.Animation}
		case "rainbow":
			run.Effect = &Effect{Type: "rainbow", Animated: true}
		}
	}
	for _, id := range s.Formats {
		item, _ := c.ShopItem(id)
		switch item.Format {
		case "bold":
			run.Bold = true
		case "italic":
			run.Italic = true
		case "underlined":
			run.Underlined = true
		case "strikethrough":
			run.Strikethrough = true
		}
	}
	return Word{Text: w.Text, Runs: []Run{run}}
}
