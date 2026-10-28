package catalog

import (
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
}

func TestParseRejectsBadEntries(t *testing.T) {
	cases := map[string]string{
		"duplicate id": `[` + valid("a") + `,` + valid("a") + `]`,
		"wrong points": strings.Replace(`[`+valid("a")+`]`, `"points": 1`, `"points": 3`, 1),
		"runs mismatch": strings.Replace(`[`+valid("a")+`]`, `"runs": [{"text": "Hero"}]`,
			`"runs": [{"text": "Her"}]`, 1),
		"unknown field": strings.Replace(`[`+valid("a")+`]`, `"tier"`, `"teir": "x", "tier"`, 1),
		"bad trigger":   strings.Replace(`[`+valid("a")+`]`, `"questName": "Q"`, `"questName": ""`, 1),
		"bad color": strings.Replace(`[`+valid("a")+`]`, `{"text": "Hero"}`,
			`{"text": "Hero", "color": "pink"}`, 1),
		"no icon fallback": strings.Replace(`[`+valid("a")+`]`, `"fallback": "minecraft:book"`, `"fallback": ""`, 1),
		"bad icon model": strings.Replace(`[`+valid("a")+`]`, `"model": "wynn/prop/quest_scroll"`,
			`"model": "minecraft:item/Wynn"`, 1),
	}

	for name, input := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Parse([]byte(input)); err == nil {
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
		"rewards": {"suffix": {"text": "Hero", "runs": [{"text": "Hero"}]}}
	}`
}
