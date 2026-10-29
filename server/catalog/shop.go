package catalog

import (
	"fmt"
	"regexp"
)

// ShopItem is one style piece players buy with achievement points. Kind picks
// what it does: a color source ("color", "palette", "animatedPalette" or
// "rainbow") or a "format" flag. The remaining fields are its parameters; an
// animated palette names both its palette and its animation, so each one is a
// single finished look rather than a combination players assemble.
type ShopItem struct {
	ID        string `json:"id"`
	Kind      string `json:"kind"`
	Name      string `json:"name"`
	Price     int    `json:"price"`
	Color     string `json:"color,omitempty"`
	Palette   string `json:"palette,omitempty"`
	Animation string `json:"animation,omitempty"`
	Format    string `json:"format,omitempty"`
}

// IsSource reports whether the item colors a word, as opposed to formatting it.
func (i *ShopItem) IsSource() bool {
	return i.Kind != "format"
}

// Style is how a player restyles one title slot, by shop item ID: at most one
// color source and any number of formats. It replaces the word's native style
// completely, so a style with nothing in it is not allowed.
type Style struct {
	Source  string   `json:"source,omitempty"`
	Formats []string `json:"formats,omitempty"`
}

// Items lists every shop item the style uses.
func (s *Style) Items() []string {
	items := append([]string{}, s.Formats...)
	if s.Source != "" {
		items = append(items, s.Source)
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
	case "format":
		want = []string{"format"}
		if !formats[i.Format] {
			return fmt.Errorf("format must be bold, italic, underlined or strikethrough")
		}
	default:
		return fmt.Errorf("unsupported kind %q", i.Kind)
	}

	set := map[string]bool{
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
	if s.Source == "" && len(s.Formats) == 0 {
		return fmt.Errorf("a style needs a color or a format")
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
		if !ok || item.IsSource() {
			return fmt.Errorf("%q is not a format", id)
		}
		if seen[id] {
			return fmt.Errorf("format %q is listed twice", id)
		}
		seen[id] = true
	}
	return nil
}

// Restyle returns the word as one run in the given style, which must have
// passed CheckStyle.
func (c *Catalog) Restyle(w Word, s *Style) Word {
	run := Run{Text: w.Text}
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
