"""Draws the WynnTitles screen widgets as pixel art, after Wynncraft's raid
start screen: a regal purple field with a gold pinstripe between two stone
pillars, a stone skull on the bottom ledge, a wine-red ribbon for the title,
a stone tab bar with the Wynncraft eye carved into it, dark stone wells and
stone buttons. No image library needed. Everything here is drawn from
scratch; nothing comes from the Wynncraft pack.

    python3 scripts/draw-ui-sprites.py mod/src/main/resources/assets/wynntitles/textures/gui/sprites/widget [--preview out.png]

Nine-slice borders are written to each sprite's .mcmeta. WynnUi.java and
TitlesScreen.java rely on the sizes noted next to each sprite (the panel's
size and insets, button height, scrollbar width).
"""
import json, math, os, struct, sys, zlib

T = (0, 0, 0, 0)
def c(s, a=255): return (int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16), a)

OUT = c('#1c1719')
GOLD_HI, GOLD, GOLD_MID, GOLD_LO, GOLD_DK = c('#fff1a8'), c('#f7c948'), c('#eda93a'), c('#d9782a'), c('#9c4a1a')
# Stone, warm grey like the raid screen's pillars and bars.
STONE_HI, STONE_LIGHT, STONE, STONE_MID, STONE_LO, STONE_DK = (
    c('#a39da0'), c('#8a8487'), c('#6e686b'), c('#5a5457'), c('#484245'), c('#353033'))
WELL, WELL_SHADE, WELL_LIGHT = c('#3a3438'), c('#2a2528'), c('#524b4f')
# The ribbon: a wine red that sits well on purple.
RED_HI, RED, RED_LO, RED_DK = c('#e0577a'), c('#b02a4a'), c('#851d38'), c('#4f0f22')
# The field between the pillars, a regal purple: light, base, shade, edge.
F_LIGHT, F_BASE, F_SHADE, F_EDGE = c('#6b4290'), c('#5a327c'), c('#4d2a6b'), c('#34184b')

# The panel is one fixed-size picture; TitlesScreen sizes and pads it to match.
PANEL_W, PANEL_H = 416, 229
PILLAR_W = 16
FIELD_X0, FIELD_X1 = 10, PANEL_W - 11   # the field runs under the pillars' inner edges
FIELD_TOP = 10                          # the ribbon straddles this edge
LEDGE_TOP, FIELD_BOTTOM = 216, 222      # the stone ledge the skulls sit on


class Canvas:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, col):
        if 0 <= x < self.w and 0 <= y < self.h and col is not None:
            if col[3] == 255 or self.px[y][x][3] == 0:
                self.px[y][x] = col
            else:
                d, k = self.px[y][x], col[3] / 255
                self.px[y][x] = tuple(int(col[i] * k + d[i] * (1 - k)) for i in range(3)) + (255,)

    def rect(self, x0, y0, x1, y1, col):  # inclusive
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, col)

    def stamp(self, x0, y0, rows, palette, flip_x=False):
        for y, r in enumerate(rows):
            for x, ch in enumerate(r[::-1] if flip_x else r):
                if ch in palette:
                    self.set(x0 + x, y0 + y, palette[ch])

    def save(self, path):
        raw = b''.join(b'\0' + bytes(v for p in row for v in p) for row in self.px)
        def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
        open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                               + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def edge_dist(x, y, w, h):
    return min(x, y, w - 1 - x, h - 1 - y)


def rounded(cv, r=1):
    """Clears the corner pixels so boxes read as rounded; r=2 cuts deeper."""
    for (x, y) in [(0, 0), (cv.w - 1, 0), (0, cv.h - 1), (cv.w - 1, cv.h - 1)]:
        dx, dy = (1 if x == 0 else -1), (1 if y == 0 else -1)
        cv.px[y][x] = T
        if r > 1:
            cv.px[y][x + dx] = T
            cv.px[y + dy][x] = T
            cv.px[y + dy][x + dx] = OUT


STONE_PAL = {'#': OUT, 'h': STONE_HI, 'l': STONE_LIGHT, 's': STONE, 'm': STONE_MID, 'd': STONE_LO, 'k': STONE_DK}

# A stone skull like the ones on the raid screen's ledge, lit from the top left:
# a round crown, deep round sockets, a nose, cheekbones and a row of teeth.
SKULL = [
    '......########......',
    '....##hhhhhlll##....',
    '...#hhhllllsssss#...',
    '..#hhllsssssssssm#..',
    '.#hllssssssssssmmd#.',
    '.#hlssssssssssssmd#.',
    '#hls####ssss####smd#',
    '#ls######ss######md#',
    '#ls######ss######md#',
    '#lss####ssss####smd#',
    '.#lssssss##sssssmd#.',
    '.#lsssss####ssssmd#.',
    '..#lsssssssssssmd#..',
    '...#h#hh#hl#ll#s#...',
    '...#l#ls#ss#sm#m#...',
    '....############....',
]

# A rivet: the raid screen bolts its pillars and bars.
RIVET = ['.#.', '#h#', '.#.']
# A domed bolt head for the pillars' caps. Even-width so it centres on them.
BOLT = ['.##.', '#hl#', '#ld#', '.##.']

# The eye of the Wynncraft emblem as a plain outline, carved into the tab bar.
# Even-width, like the panel, so it centres exactly.
EYE = [
    '....######....',
    '..##......##..',
    '.#...####...#.',
    '#...#....#...#',
    '.#...####...#.',
    '..##......##..',
    '....######....',
]


def stone_block(cv, x0, y0, x1, y1, round_corners=False):
    """An outlined block of stone lit from the top left (inclusive bounds)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                col = OUT
            elif y == y0 + 1:
                col = STONE_HI if x < x1 - 2 else STONE_LIGHT
            elif x == x0 + 1:
                col = STONE_LIGHT
            elif x == x1 - 1 or y == y1 - 1:
                col = STONE_LO
            elif x == x1 - 2:
                col = STONE_MID
            else:
                col = STONE
            cv.set(x, y, col)
    if round_corners:
        for (x, y) in [(x0, y0), (x1, y0), (x0, y1), (x1, y1)]:
            cv.px[y][x] = T


# A pillar's profile from the top, as (rows, inset from each side): a bolted
# slab, a moulding that steps in, a ring, then the shaft. The base mirrors it.
CAPITAL = [(9, 0), (5, 1), (3, 1)]
SHAFT_INSET = 2
# Shading across the round shaft, outline to outline, light from the left.
SHAFT = [OUT, STONE_HI, STONE_LIGHT, STONE_LIGHT, STONE, STONE, STONE, STONE, STONE_MID, STONE_MID, STONE_LO, OUT]


def pillar(cv, x0):
    """A stone column the full height of the panel: capital, a shaft of stacked
    drums, and a base. The shaft is narrower, so the field shows either side."""
    x1 = x0 + PILLAR_W - 1
    top, bottom = 0, PANEL_H - 2
    sx0 = x0 + SHAFT_INSET
    cap_h = sum(h for h, _ in CAPITAL) - len(CAPITAL) + 1   # blocks share outline rows
    shaft_top, shaft_bottom = top + cap_h - 1, bottom - cap_h + 1
    for y in range(shaft_top, shaft_bottom + 1):
        for i, col in enumerate(SHAFT):
            x = sx0 + i
            if col is STONE and ((x * 73856093) ^ (y * 19349663)) % 41 == 0:
                col = STONE_MID                      # weathering
            cv.set(x, y, col)
    # A shadow under the capital, and the joints between the drums.
    for x in range(sx0 + 1, sx0 + len(SHAFT) - 1):
        cv.set(x, shaft_top + 1, STONE_LO)
        cv.set(x, shaft_top + 2, STONE_MID if x > sx0 + 3 else STONE)
    drums = 5
    for k in range(1, drums):
        j = shaft_top + (shaft_bottom - shaft_top) * k // drums
        for x in range(sx0 + 1, sx0 + len(SHAFT) - 1):
            cv.set(x, j, STONE_DK)
            cv.set(x, j + 1, STONE_HI if x < sx0 + 4 else STONE_LIGHT)
    # Capital and base, outermost block last so it sits on top.
    y = top
    caps = []
    for h, inset in CAPITAL:
        caps.append((y, y + h - 1, inset))
        y += h - 1
    for (a, b, inset) in reversed(caps):
        stone_block(cv, x0 + inset, a, x1 - inset, b, round_corners=inset == 0)
        stone_block(cv, x0 + inset, bottom - (b - top), x1 - inset, bottom - (a - top), round_corners=inset == 0)
    slab = CAPITAL[0][0]
    for by in (top + (slab - 4) // 2, bottom - slab + 1 + (slab - 4) // 2):
        cv.stamp(x0 + PILLAR_W // 2 - 2, by, BOLT, STONE_PAL)


def panel():
    """PANEL_W x PANEL_H: the field, its pinstripe and ledge, the pillars and a skull."""
    cv = Canvas(PANEL_W, PANEL_H)
    # Rings centred on the panel; pixel centres sit half a pixel in.
    cx, cy = (PANEL_W - 1) / 2, (FIELD_TOP + LEDGE_TOP - 1) / 2
    for y in range(FIELD_TOP, FIELD_BOTTOM + 1):
        for x in range(FIELD_X0, FIELD_X1 + 1):
            d = min(x - FIELD_X0, y - FIELD_TOP, FIELD_X1 - x, FIELD_BOTTOM - y)
            if d == 0:
                col = OUT
            elif d <= 2:
                col = F_EDGE
            elif d == 3:
                col = F_SHADE
            else:
                col = F_BASE
                # Faint rings spreading from the middle, like the raid screen's.
                r = math.hypot(x - cx, y - cy)
                if r < 200 and int(r) % 16 in (0, 1, 2) and (x + y) % 2 == 0:
                    col = F_LIGHT
                elif (x * 7 + y * 11) % 31 == 0:
                    col = F_SHADE
            cv.set(x, y, col)
    # The gold pinstripe, with brighter ticks at the corners.
    px0, py0, px1, py1 = FIELD_X0 + 9, FIELD_TOP + 5, FIELD_X1 - 9, LEDGE_TOP - 3
    for x in range(px0, px1 + 1):
        for y in (py0, py1):
            cv.set(x, y, GOLD_LO[:3] + (150,))
    for y in range(py0, py1 + 1):
        for x in (px0, px1):
            cv.set(x, y, GOLD_LO[:3] + (150,))
    for (x, y, dx, dy) in [(px0, py0, 1, 1), (px1, py0, -1, 1), (px0, py1, 1, -1), (px1, py1, -1, -1)]:
        for i in range(4):
            cv.set(x + dx * i, y, GOLD)
            cv.set(x, y + dy * i, GOLD)
    # The stone ledge along the bottom.
    for y in range(LEDGE_TOP, FIELD_BOTTOM + 1):
        for x in range(FIELD_X0, FIELD_X1 + 1):
            if y in (LEDGE_TOP, FIELD_BOTTOM) or x in (FIELD_X0, FIELD_X1):
                col = OUT
            elif y == LEDGE_TOP + 1:
                col = STONE_LIGHT
            elif y == FIELD_BOTTOM - 1:
                col = STONE_LO
            else:
                col = STONE_MID if (x // 23) % 2 else STONE
                if x % 23 == 0: col = OUT
            cv.set(x, y, col)
    pillar(cv, 0)
    pillar(cv, PANEL_W - PILLAR_W)
    # A skull in the middle of the ledge.
    cv.stamp(PANEL_W // 2 - len(SKULL[0]) // 2, PANEL_H - len(SKULL), SKULL, STONE_PAL)
    return cv, None


def ribbon():
    """48x20 red ribbon for the screen's title, with folded tails. Border 11 sides, 6 top and bottom."""
    W, H = 48, 20
    cv = Canvas(W, H)
    body_y0, body_y1 = 1, H - 5
    for y in range(H):
        for x in range(W):
            tail = x < 8 or x >= W - 8
            col = None
            if not tail:
                if body_y0 <= y <= body_y1:
                    if y in (body_y0, body_y1) or x in (8, W - 9):
                        col = OUT
                    elif y == body_y0 + 1:
                        col = RED_HI
                    elif y >= body_y1 - 2:
                        col = RED_LO
                    else:
                        col = RED
            else:
                # Tails sit lower and fork into a notch at the outer end.
                tx = x if x < 8 else W - 1 - x
                ty0, ty1 = body_y0 + 4, H - 1
                notch = abs(y - (ty0 + ty1) / 2) < (3 - tx) * 1.2
                if ty0 <= y <= ty1 and not notch:
                    if y in (ty0, ty1) or tx == 0 or abs(y - (ty0 + ty1) / 2) < (3 - tx) * 1.2 + 1:
                        col = OUT
                    elif tx >= 6:
                        col = RED_DK      # the fold under the body
                    else:
                        col = RED_LO
            cv.set(x, y, col)
    return cv, dict(width=W, height=H, border=dict(left=11, right=11, top=6, bottom=6))


def bar():
    """32x18 stone bar with rivets at the ends, for the tab bar. Border 8 sides, 4 top and bottom."""
    W, H = 32, 18
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            if d == 0:
                col = OUT
            elif y == 1:
                col = STONE_LIGHT
            elif y == H - 2:
                col = STONE_LO
            elif y == H - 3:
                col = STONE_MID
            else:
                col = STONE
            cv.set(x, y, col)
    rounded(cv, 2)
    cv.stamp(3, H // 2 - 1, RIVET, STONE_PAL)
    cv.stamp(W - 6, H // 2 - 1, RIVET, STONE_PAL)
    return cv, dict(width=W, height=H, border=dict(left=8, right=8, top=4, bottom=4))


def tab(state):
    """16x14 frame round a tab's name on the tab bar, border 3: a line carved into
    the stone, brighter on hover, and a dark inset with a gold rim when open."""
    W, H = 16, 14
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            if state == 'selected':
                col = OUT if d == 0 else GOLD if d == 1 else WELL
            elif d == 0:
                col = STONE_DK if state == 'normal' else STONE_HI
            elif d == 1 and (x == W - 2 or y == H - 2):
                col = STONE_LIGHT           # the lit lip of the cut
            elif state == 'hover':
                col = (255, 255, 255, 24)
            else:
                col = None
            cv.set(x, y, col)
    rounded(cv, 2 if state == 'selected' else 1)
    return cv, dict(width=W, height=H, border=3)


def eye():
    """14x8 Wynncraft eye carved into the middle of the tab bar: dark cuts, with
    the light catching the lip under each one."""
    cv = Canvas(14, 8)
    for y, r in enumerate(EYE):
        for x, ch in enumerate(r):
            if ch == '#':
                cv.set(x, y, STONE_DK)
                if y + 1 >= len(EYE) or EYE[y + 1][x] != '#':
                    cv.set(x, y + 1, STONE_LIGHT)
    return cv, None


def button(state):
    """32x18 stone button with a 3px lip under the face. Border 4 sides, 3 top, 5 bottom."""
    W, H = 32, 18
    hi, face, lo, dk = {
        'normal': (c('#e2e0e3'), c('#bdbabe'), c('#8e8a8e'), c('#5f5a5e')),
        'hover': (c('#f4f3f5'), c('#d3d0d4'), c('#a19da1'), c('#6f6a6e')),
        'disabled': (STONE_LIGHT, STONE, STONE_MID, STONE_LO),
    }[state]
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            if d == 0:
                col = OUT
            elif y >= H - 4:  # lip
                col = dk if y == H - 2 else lo
            elif y == 1 or x == 1:
                col = hi
            elif x == W - 2:
                col = lo
            else:
                col = face
            cv.set(x, y, col)
    rounded(cv, 2)
    return cv, dict(width=W, height=H, border=dict(left=4, right=4, top=3, bottom=5))


def well():
    """16x16 dark stone recess for lists, rounded, border 3."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = edge_dist(x, y, 16, 16)
            if d == 0:
                col = OUT
            elif d == 1:
                col = WELL_SHADE if (x == 1 or y == 1) else WELL_LIGHT
            else:
                col = WELL
            cv.set(x, y, col)
    rounded(cv, 2)
    return cv, dict(width=16, height=16, border=3)


def row(state):
    """16x12 list row highlights, border 3 left and 1 elsewhere."""
    cv = Canvas(16, 12)
    for y in range(12):
        for x in range(16):
            d = edge_dist(x, y, 16, 12)
            if state == 'hover':
                col = (255, 245, 235, 40) if d == 0 else (255, 245, 235, 16)
            else:
                if x <= 1:
                    col = GOLD if x == 0 else GOLD_LO
                elif d == 0:
                    col = GOLD[:3] + (150,)
                else:
                    col = GOLD[:3] + (36,)
            cv.set(x, y, col)
    return cv, dict(width=16, height=12, border=dict(left=3, right=1, top=1, bottom=1))


def scroll_track():
    """5x16 scrollbar track, border 2."""
    cv = Canvas(5, 16)
    for y in range(16):
        for x in range(5):
            cv.set(x, y, OUT if edge_dist(x, y, 5, 16) == 0 else WELL_SHADE)
    return cv, dict(width=5, height=16, border=2)


def scroll_thumb():
    """5x12 gold scrollbar handle with grip lines, border 2 sides and 3 ends."""
    cv = Canvas(5, 12)
    for y in range(12):
        for x in range(5):
            d = edge_dist(x, y, 5, 12)
            if d == 0:
                col = OUT
            elif x == 1 or y == 1:
                col = GOLD_HI
            elif x == 3 or y == 10:
                col = GOLD_LO
            else:
                col = GOLD_LO if y % 2 == 0 else GOLD
            cv.set(x, y, col)
    rounded(cv)
    return cv, dict(width=5, height=12, border=dict(left=2, right=2, top=3, bottom=3))


def nameplate():
    """32x20 dark stone plate with a gold rim for the title preview, border 5."""
    W, H = 32, 20
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            if d == 0 or d == 2:
                col = OUT
            elif d == 1:
                col = GOLD if (x == 1 or y == 1) else GOLD_LO
            elif d == 3:
                col = WELL_SHADE if (x == 3 or y == 3) else WELL
            else:
                col = c('#2c272a')
            cv.set(x, y, col)
    rounded(cv)
    for (x, y) in [(1, 1), (W - 3, 1), (1, H - 3), (W - 3, H - 3)]:
        cv.rect(x, y, x + 1, y + 1, GOLD_HI)
    return cv, dict(width=W, height=H, border=5)


def progress(fill):
    """16x8 progress bar: the trough (border 2), and the gold fill drawn inside it (border 1)."""
    cv = Canvas(16, 8)
    for y in range(8):
        for x in range(16):
            d = edge_dist(x, y, 16, 8)
            if fill:
                col = GOLD_HI if y == 0 else GOLD_LO if y == 7 else GOLD_DK if x == 15 else GOLD
                if y in (2, 3) and x % 4 == 1: col = GOLD_HI
            else:
                col = OUT if d == 0 else WELL_SHADE if y == 1 else WELL
            cv.set(x, y, col)
    if not fill:
        rounded(cv)
    return cv, dict(width=16, height=8, border=1 if fill else 2)


SPRITES = {
    'panel': panel(),
    'ribbon': ribbon(),
    'bar': bar(),
    'tab': tab('normal'),
    'tab_hover': tab('hover'),
    'tab_selected': tab('selected'),
    'eye': eye(),
    'button': button('normal'),
    'button_hover': button('hover'),
    'button_disabled': button('disabled'),
    'well': well(),
    'row_hover': row('hover'),
    'row_selected': row('selected'),
    'scroll_track': scroll_track(),
    'scroll_thumb': scroll_thumb(),
    'nameplate': nameplate(),
    'progress': progress(False),
    'progress_fill': progress(True),
}

for rows in (SKULL, EYE):
    assert len({len(r) for r in rows}) == 1, rows

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for name, (cv, nine) in SPRITES.items():
    cv.save(f'{out}/{name}.png')
    meta = f'{out}/{name}.png.mcmeta'
    if nine:
        with open(meta, 'w') as f:
            json.dump({'gui': {'scaling': {'type': 'nine_slice', **nine}}}, f)
    elif os.path.exists(meta):
        os.remove(meta)

if '--preview' in sys.argv:
    # Every sprite at 3x on a mid grey, in a row.
    S = 3
    items = list(SPRITES.values())
    PW = sum(cv.w * S + 12 for cv, _ in items) + 12
    PH = max(cv.h for cv, _ in items) * S + 24
    sheet = Canvas(PW, PH, (70, 70, 70, 255))
    ox = 12
    for cv, _ in items:
        for y in range(cv.h * S):
            for x in range(cv.w * S):
                p = cv.px[y // S][x // S]
                if p[3]:
                    a = p[3] / 255
                    sheet.px[12 + y][ox + x] = tuple(int(p[i] * a + 70 * (1 - a)) for i in range(3)) + (255,)
        ox += cv.w * S + 12
    sheet.save(sys.argv[sys.argv.index('--preview') + 1])
