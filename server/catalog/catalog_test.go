package catalog

import (
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
// every animation is on sale.
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
		if !used[animation] {
			t.Errorf("no animated palette uses %s", animation)
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
