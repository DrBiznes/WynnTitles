package catalog

import (
	"encoding/json"
	"fmt"
	"os"
	"reflect"
	"strings"
	"testing"
)

func TestEmbeddedCatalogIsValid(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(c.Achievements) == 0 {
		t.Fatal("catalog is empty")
	}
	if _, ok := c.Get(c.Achievements[0].ID); !ok {
		t.Fatal("Get could not find the first achievement")
	}
	if len(c.Shop) == 0 {
		t.Fatal("shop is empty")
	}
	for _, item := range c.Shop {
		if item.Format == "obfuscated" {
			t.Fatalf("%s sells obfuscated, which is reserved for native styles", item.ID)
		}
	}
}

// Every animated palette should be its own look: no two share a palette, and
// every animation that is not native only is on sale.
func TestEmbeddedShopAnimationsAreDistinct(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	palettes := map[string]string{}
	used := map[string]bool{}
	for _, item := range c.Shop {
		if item.Kind != "animatedPalette" {
			continue
		}
		if other, dup := palettes[item.Palette]; dup {
			t.Errorf("%s and %s both animate the %s palette", item.ID, other, item.Palette)
		}
		palettes[item.Palette] = item.ID
		used[item.Animation] = true
	}
	for animation := range animations {
		if !used[animation] && !nativeOnly[animation] {
			t.Errorf("no animated palette uses %s", animation)
		}
	}
}

// Native styles are looks nobody can buy: effects bring their own colors
// rather than a shop palette, solid colors are hex rather than the named
// colors the shop sells, and no two native effects are the same.
func TestEmbeddedNativeStylesAreUnique(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	looks := map[string]string{}
	for _, a := range c.Achievements {
		for _, word := range []*Word{a.Rewards.Prefix, a.Rewards.Suffix} {
			if word == nil {
				continue
			}
			for _, run := range word.Runs {
				where := a.ID + " " + run.Text
				if run.Color != "" && namedColors[run.Color] && !run.Obfuscated {
					t.Errorf("%s uses %s, a color the shop sells; use a hex color", where, run.Color)
				}
				e := run.Effect
				if e == nil {
					continue
				}
				if e.Palette != "" || e.Type == "rainbow" {
					t.Errorf("%s uses a shop palette; give it its own colors", where)
				}
				look := fmt.Sprint(e.Colors, e.Animated, e.Animation)
				if other, dup := looks[look]; dup {
					t.Errorf("%s looks the same as %s", where, other)
				}
				looks[look] = where
			}
		}
	}
}

// The title builder looks words up by text and keeps the first, so a repeated
// prefix or suffix would silently leave its achievement with nothing to wear.
func TestEmbeddedTitleWordsAreUnique(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	prefixes, suffixes := map[string]string{}, map[string]string{}
	for _, a := range c.Achievements {
		for _, slot := range []struct {
			kind  string
			word  *Word
			owner map[string]string
		}{
			{"prefix", a.Rewards.Prefix, prefixes},
			{"suffix", a.Rewards.Suffix, suffixes},
		} {
			if slot.word == nil {
				continue
			}
			key := strings.ToLower(slot.word.Text)
			if other, dup := slot.owner[key]; dup {
				t.Errorf("%s %q on %s is already used by %s", slot.kind, slot.word.Text, a.ID, other)
			}
			slot.owner[key] = a.ID
		}
	}
}

func TestPlainWordsGetOneRun(t *testing.T) {
	c, err := Parse([]byte(`[`+valid("a")+`]`), []byte(testShop))
	if err != nil {
		t.Fatal(err)
	}
	runs := c.Achievements[0].Rewards.Suffix.Runs
	if len(runs) != 1 || runs[0] != (Run{Text: "Plain"}) {
		t.Fatalf("runs = %+v, want one plain run", runs)
	}
}

// A misspelled place can never unlock, so every territorySet name must be a
// discovery. testdata/discoveries.json holds the world, territorial and secret
// names from Wynntils' content_book_dump.json in its Static-Storage repository.
func TestTerritorySetNamesAreDiscoveries(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile("testdata/discoveries.json")
	if err != nil {
		t.Fatal(err)
	}
	var names []string
	if err := json.Unmarshal(data, &names); err != nil {
		t.Fatal(err)
	}
	// VisitedTerritories in the mod treats "Town of Lutho" as this territory.
	known := map[string]bool{"lutho": true}
	for _, name := range names {
		known[strings.ToLower(name)] = true
	}
	for _, a := range c.Achievements {
		for _, name := range a.Trigger.TerritoryNames {
			if !known[strings.ToLower(name)] {
				t.Errorf("%s: %q is not a discovery", a.ID, name)
			}
		}
	}
}

func TestRaidTrigger(t *testing.T) {
	raid := strings.Replace(valid("a"), `{"type": "quest", "questName": "Q"}`,
		`{"type": "raid", "raidName": "The Nameless Anomaly", "amount": 1}`, 1)
	if _, err := Parse([]byte(`[`+raid+`]`), []byte(testShop)); err != nil {
		t.Fatal(err)
	}
	noName := strings.Replace(raid, `"raidName": "The Nameless Anomaly", `, ``, 1)
	if _, err := Parse([]byte(`[`+noName+`]`), []byte(testShop)); err == nil {
		t.Fatal("a raid trigger without raidName should be rejected")
	}
}

func TestTriggerParameters(t *testing.T) {
	withTrigger := func(trigger string) []byte {
		return []byte(`[` + strings.Replace(valid("a"), `{"type": "quest", "questName": "Q"}`, trigger, 1) + `]`)
	}
	good := []string{
		`{"type": "statistic", "statistic": "wars", "amount": 100}`,
		`{"type": "dungeon", "dungeonName": "Decrepit Sewers", "amount": 1}`,
		`{"type": "dungeonSet", "dungeonNames": ["Ice Barrows", "Infested Pit"]}`,
		`{"type": "professionLevel", "professionName": "Mining", "level": 132}`,
		`{"type": "professionLevelAll", "professionNames": ["Mining", "Fishing"], "level": 125}`,
		`{"type": "characterRoster", "level": 50}`,
		`{"type": "gamemodeLevel", "gamemodes": ["hardcore", "hunted"], "level": 80}`,
		`{"type": "playtime", "amount": 100}`,
		`{"type": "guild", "guildRank": "owner"}`,
		`{"type": "coordinates", "x": 13777, "z": -4042, "radius": 64}`,
		`{"type": "itemAtLocation", "itemName": "Dynamite", "x": -200, "z": -281, "radius": 8}`,
		`{"type": "territorySet", "territoryNames": ["Ragni", "Detlas"]}`,
		`{"type": "territorySet", "territoryNames": ["The Moon"]}`,
		`{"type": "itemRarityCount", "rarity": "mythic", "amount": 3}`,
		`{"type": "itemSet", "itemSets": [["A", "B"], ["C", "D"]]}`,
	}
	for _, trigger := range good {
		if _, err := Parse(withTrigger(trigger), []byte(testShop)); err != nil {
			t.Errorf("%s: %v", trigger, err)
		}
	}
	bad := []string{
		`{"type": "statistic", "statistic": "pvpKills", "amount": 1}`,
		`{"type": "dungeon", "dungeonName": "Decrepit Sewers"}`,
		`{"type": "dungeonSet", "dungeonNames": ["Ice Barrows"]}`,
		`{"type": "professionLevel", "professionName": "Smithing", "level": 100}`,
		`{"type": "professionLevelAll", "professionNames": ["Mining", "mining"], "level": 100}`,
		`{"type": "gamemodeLevel", "gamemodes": ["HARDCORE"], "level": 50}`,
		`{"type": "guild", "guildRank": "king"}`,
		`{"type": "coordinates", "x": 1, "z": 1}`,
		`{"type": "itemAtLocation", "x": 1, "z": 1, "radius": 8}`,
		`{"type": "territorySet", "territoryNames": []}`,
		`{"type": "territorySet", "territoryNames": ["Ragni", ""]}`,
		`{"type": "itemRarityCount", "rarity": "epic", "amount": 1}`,
		`{"type": "itemSet", "itemSets": [["A", "B"], ["C"]]}`,
	}
	for _, trigger := range bad {
		if _, err := Parse(withTrigger(trigger), []byte(testShop)); err == nil {
			t.Errorf("%s: expected an error", trigger)
		}
	}
}

func TestParseRejectsBadEntries(t *testing.T) {
	cases := map[string]string{
		"duplicate id":  `[` + valid("a") + `,` + valid("a") + `]`,
		"wrong points":  strings.Replace(`[`+valid("a")+`]`, `"points": 1`, `"points": 3`, 1),
		"unknown field": strings.Replace(`[`+valid("a")+`]`, `"tier"`, `"teir": "x", "tier"`, 1),
		"bad trigger":   strings.Replace(`[`+valid("a")+`]`, `"questName": "Q"`, `"questName": ""`, 1),
		"bad color": strings.Replace(`[`+valid("a")+`]`, `"text": "Hero"}`,
			`"text": "Hero", "runs": [{"text": "Hero", "color": "pink"}]}`, 1),
		"no icon fallback": strings.Replace(`[`+valid("a")+`]`, `"fallback": "minecraft:book"`, `"fallback": ""`, 1),
		"bad icon model": strings.Replace(`[`+valid("a")+`]`, `"model": "wynn/prop/quest_scroll"`,
			`"model": "minecraft:item/Wynn"`, 1),
		"styled runs mismatch": strings.Replace(`[`+valid("a")+`]`, `"text": "Hero"}`,
			`"text": "Hero", "runs": [{"text": "Her"}]}`, 1),
		"unknown animation": strings.Replace(`[`+valid("a")+`]`, `"text": "Hero"}`,
			`"text": "Hero", "runs": [{"text": "Hero", "effect": {"type": "gradient", "palette": "ice", "animated": true, "animation": "spin"}}]}`, 1),
		"animation without animated": strings.Replace(`[`+valid("a")+`]`, `"text": "Hero"}`,
			`"text": "Hero", "runs": [{"text": "Hero", "effect": {"type": "gradient", "palette": "ice", "animation": "wave"}}]}`, 1),
	}

	for name, input := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Parse([]byte(input), []byte(testShop)); err == nil {
				t.Fatal("expected an error")
			}
		})
	}
}

func valid(id string) string {
	return `{
		"id": "` + id + `", "category": "quest", "name": "N", "description": "D",
		"tier": "bronze", "points": 1,
		"icon": {"model": "wynn/prop/quest_scroll", "fallback": "minecraft:book"},
		"trigger": {"type": "quest", "questName": "Q"},
		"rewards": {"prefix": {"text": "Hero"}, "suffix": {"text": "Plain"}}
	}`
}

const testShop = `[
	{"id": "color_red", "kind": "color", "name": "Red", "price": 2, "color": "red"},
	{"id": "palette_ice", "kind": "palette", "name": "Ice", "price": 5, "palette": "ice"},
	{"id": "animated_ice", "kind": "animatedPalette", "name": "Frostglint", "price": 9, "palette": "ice", "animation": "shimmer"},
	{"id": "rainbow", "kind": "rainbow", "name": "Rainbow", "price": 20},
	{"id": "bold", "kind": "format", "name": "Bold", "price": 3, "format": "bold"}
]`

func TestParseRejectsBadShopItems(t *testing.T) {
	cases := map[string]string{
		"duplicate id":       `[{"id": "x", "kind": "rainbow", "name": "R", "price": 1}, {"id": "x", "kind": "rainbow", "name": "R", "price": 1}]`,
		"free":               `[{"id": "x", "kind": "rainbow", "name": "R", "price": 0}]`,
		"unknown kind":       `[{"id": "x", "kind": "glow", "name": "G", "price": 1}]`,
		"obfuscated":         `[{"id": "x", "kind": "format", "name": "O", "price": 1, "format": "obfuscated"}]`,
		"rainbow palette":    `[{"id": "x", "kind": "palette", "name": "R", "price": 1, "palette": "rainbow"}]`,
		"no animation":       `[{"id": "x", "kind": "animatedPalette", "name": "I", "price": 1, "palette": "ice"}]`,
		"unknown animation":  `[{"id": "x", "kind": "animatedPalette", "name": "I", "price": 1, "palette": "ice", "animation": "spin"}]`,
		"native animation":   `[{"id": "x", "kind": "animatedPalette", "name": "I", "price": 1, "palette": "ice", "animation": "decipher"}]`,
		"static animation":   `[{"id": "x", "kind": "palette", "name": "I", "price": 1, "palette": "ice", "animation": "wave"}]`,
		"bad color":          `[{"id": "x", "kind": "color", "name": "P", "price": 1, "color": "pink"}]`,
		"stray field":        `[{"id": "x", "kind": "color", "name": "R", "price": 1, "color": "red", "palette": "ice"}]`,
		"bad id":             `[{"id": "Red Thing", "kind": "rainbow", "name": "R", "price": 1}]`,
		"unknown json field": `[{"id": "x", "kind": "rainbow", "name": "R", "price": 1, "cost": 3}]`,
	}
	for name, shop := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Parse([]byte(`[`+valid("a")+`]`), []byte(shop)); err == nil {
				t.Fatal("expected an error")
			}
		})
	}
}

func TestCheckStyleAndRestyle(t *testing.T) {
	c, err := Parse([]byte(`[`+valid("a")+`]`), []byte(testShop))
	if err != nil {
		t.Fatal(err)
	}
	bad := map[string]Style{
		"empty":            {},
		"format as source": {Source: "bold"},
		"source as format": {Formats: []string{"color_red"}},
		"repeated format":  {Formats: []string{"bold", "bold"}},
		"unknown item":     {Source: "glow"},
	}
	for name, style := range bad {
		if c.CheckStyle(&style) == nil {
			t.Errorf("%s: expected CheckStyle to fail", name)
		}
	}

	native := Word{Text: "Greg", Runs: []Run{{Text: "Greg", Color: "dark_gray", Obfuscated: true}}}
	style := Style{Source: "animated_ice", Formats: []string{"bold"}}
	if err := c.CheckStyle(&style); err != nil {
		t.Fatal(err)
	}
	got := c.Restyle(native, &style)
	want := []Run{{Text: "Greg", Bold: true, Effect: &Effect{Type: "gradient", Palette: "ice", Animated: true, Animation: "shimmer"}}}
	if !reflect.DeepEqual(got.Runs, want) {
		t.Fatalf("restyled = %+v, want %+v", got.Runs, want)
	}
}

func TestFontRulesAndPricing(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	points, cost, fonts := 0, 0, 0
	for _, a := range c.Achievements {
		points += a.Points
	}
	for _, item := range c.Shop {
		cost += item.Price
		if item.Kind != "font" {
			continue
		}
		fonts++
		style := &Style{Font: item.ID, Source: "color_red", Formats: []string{"bold"}}
		if item.Banner() {
			if c.CheckStyle(style) == nil {
				t.Fatal("banner accepted bold")
			}
			style.Formats = nil
			style.Source = "palette_ice"
			if c.CheckStyle(style) == nil {
				t.Fatal("banner accepted palette")
			}
			style.Source = "animated_ice"
			if c.CheckStyle(style) == nil {
				t.Fatal("banner accepted animation")
			}
			style.Source = "rainbow"
			if c.CheckStyle(style) == nil {
				t.Fatal("banner accepted rainbow")
			}
			style.Source = "color_red"
			style.LetterColor = "yellow"
		}
		if err := c.CheckStyle(style); err != nil {
			t.Fatal(err)
		}
		got := c.Restyle(Word{Text: "Greg", Runs: []Run{{Text: "Greg", Obfuscated: true}}}, style)
		if got.Runs[0].Font != item.Font || got.Runs[0].Obfuscated {
			t.Fatalf("font failed: %+v", got)
		}
	}
	if fonts != 10 {
		t.Fatalf("fonts = %d", fonts)
	}
	// Completionists can buy most of the shop but not all of it, so points
	// still mean something until the next batch of achievements and items.
	ratio := float64(points) / float64(cost)
	if ratio < 0.80 || ratio > 0.85 {
		t.Fatalf("earnable/shop = %.3f (%d/%d)", ratio, points, cost)
	}
	for _, style := range []Style{{Font: "bold"}, {Source: "font_pill"}, {Formats: []string{"font_pill"}}, {Font: "font_wynnic", LetterColor: "red"}, {Color: "red", Formats: []string{"bold"}}} {
		if c.CheckStyle(&style) == nil {
			t.Fatalf("accepted %+v", style)
		}
	}
}

func TestFontCatalogValidationAndCoverage(t *testing.T) {
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	font, _ := c.ShopItem("font_pill")
	for _, mutate := range []func(*ShopItem){
		func(i *ShopItem) { i.Font = "minecraft:missing" },
		func(i *ShopItem) { i.Formats = nil },
		func(i *ShopItem) { b := true; i.Formats = &b },
		func(i *ShopItem) { i.Allows = nil },
		func(i *ShopItem) { i.Allows = []string{"rainbow"} },
		func(i *ShopItem) { i.Allows = []string{"color", "color"} },
		func(i *ShopItem) { i.Color = "red" },
	} {
		bad := *font
		mutate(&bad)
		if validateShopItem(&bad) == nil {
			t.Fatalf("accepted malformed font %+v", bad)
		}
	}
	style := &Style{Font: "font_pill"}
	if err := c.CheckWordStyle(Word{Text: "Wynn Hero 123"}, style); err != nil {
		t.Fatal(err)
	}
	for _, text := range []string{"Queen's", "Café", "ß"} {
		if c.CheckWordStyle(Word{Text: text}, style) == nil {
			t.Fatalf("banner accepted %q", text)
		}
	}
}
