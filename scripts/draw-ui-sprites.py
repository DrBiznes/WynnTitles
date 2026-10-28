"""Draws the WynnTitles screen widgets as pixel art in the style of Wynncraft's
own menus (wood frame, gold fittings, parchment plaques), no image library
needed. Everything here is drawn from scratch; nothing comes from the
Wynncraft pack.

    python3 scripts/draw-ui-sprites.py mod/src/main/resources/assets/wynntitles/textures/gui/sprites/widget [--preview out.png]

Nine-slice borders are written to each sprite's .mcmeta. WynnUi.java relies on
the sizes noted next to each sprite (button height, tab size, scrollbar width).
"""
import json, os, struct, sys, zlib

T = (0, 0, 0, 0)
def c(s, a=255): return (int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16), a)

# Shared with draw-icon-frames.py, so the frames and the screen match.
OUT = c('#140d08')
WOOD_HI, WOOD, WOOD_MID, WOOD_LO, WOOD_DK = c('#a06c47'), c('#8a5a3a'), c('#7a4e32'), c('#6a4329'), c('#57361f')
GOLD_HI, GOLD, GOLD_LO, GOLD_DK = c('#fff0a0'), c('#e9bd3a'), c('#b0801c'), c('#7a5512')
# The panel's inside is darker stained wood; wells are cut into it.
INSIDE, INSIDE_GRAIN, INSIDE_LIGHT, INSIDE_SHADE = c('#2a2118'), c('#241c14'), c('#33281c'), c('#1f1811')
WELL, WELL_SHADE, WELL_LIGHT = c('#1a140e'), c('#0f0b07'), c('#3d3022')
PARCH_HI, PARCH, PARCH_LO, PARCH_DK = c('#f4e4bb'), c('#e2cd9d'), c('#cab486'), c('#a38d63')


class Canvas:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, col):
        if 0 <= x < self.w and 0 <= y < self.h and col is not None:
            self.px[y][x] = col

    def rect(self, x0, y0, x1, y1, col):  # inclusive
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, col)

    def save(self, path):
        raw = b''.join(b'\0' + bytes(v for p in row for v in p) for row in self.px)
        def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
        open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                               + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def edge_dist(x, y, w, h):
    return min(x, y, w - 1 - x, h - 1 - y)


def rounded(cv, r=1):
    """Clears the outermost corner pixels so boxes read as slightly rounded."""
    for (x, y) in [(0, 0), (cv.w - 1, 0), (0, cv.h - 1), (cv.w - 1, cv.h - 1)]:
        cv.set(x, y, T)
        if r > 1:
            dx = 1 if x == 0 else -1
            dy = 1 if y == 0 else -1
            cv.set(x + dx, y, T); cv.set(x, y + dy, T); cv.set(x + dx, y + dy, OUT)


def gold_plate(cv, x0, y0, size):
    """A square gold corner fitting with a rivet, lit from the top left."""
    x1, y1 = x0 + size - 1, y0 + size - 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                col = OUT
            elif x == x0 + 1 or y == y0 + 1:
                col = GOLD_HI
            elif x == x1 - 1 or y == y1 - 1:
                col = GOLD_LO
            else:
                col = GOLD
            cv.set(x, y, col)
    m = x0 + size // 2, y0 + size // 2
    cv.set(m[0], m[1], GOLD_DK)
    cv.set(m[0] - 1, m[1] - 1, GOLD_HI)


def panel():
    """48x48, nine-slice border 8. Frame: outline, 4px wood, outline, then the inside."""
    W = H = 48
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            # Which side is nearest decides grain direction and bevel.
            top_left = (y <= x and y <= H - 1 - x) or (x <= y and x <= W - 1 - y)
            along = x if min(y, H - 1 - y) <= min(x, W - 1 - x) else y
            if d == 0 or d == 5:
                col = OUT
            elif d <= 4:
                if d == 1:
                    col = WOOD_HI if top_left else WOOD_LO
                elif d == 4:
                    col = WOOD_DK if top_left else WOOD_MID
                else:
                    col = WOOD_MID if (along + d * 5) % 16 in (3, 4, 5, 11) else WOOD
                    if (along + d * 3) % 16 == 9:
                        col = WOOD_LO
            elif d == 6:
                col = INSIDE_SHADE if top_left else INSIDE_LIGHT
            else:
                col = INSIDE
                if (y % 4 == 1 and (x * 3 + y) % 16 in (0, 1, 2, 3, 4)) or (y % 4 == 3 and (x * 5 + y) % 16 in (7, 8)):
                    col = INSIDE_GRAIN
            cv.set(x, y, col)
    rounded(cv)
    for (x, y) in [(0, 0), (W - 7, 0), (0, H - 7), (W - 7, H - 7)]:
        gold_plate(cv, x, y, 7)
    return cv, dict(width=W, height=H, border=8)


def plaque():
    """48x15 parchment nameboard for headings, with gold rods at the ends. Border 7/4."""
    W, H = 48, 15
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            d = edge_dist(x, y, W, H)
            if d == 0:
                col = OUT
            elif x <= 3 or x >= W - 4:  # rods
                col = GOLD_HI if x in (1, W - 3) else GOLD_LO if x in (3, W - 4) else GOLD
                if y == H - 2: col = GOLD_LO
            elif x in (4, W - 5):
                col = OUT
            elif y == 1:
                col = PARCH_HI
            elif y == H - 2:
                col = PARCH_DK
            elif y == H - 3:
                col = PARCH_LO
            else:
                col = PARCH
                if (x + y * 7) % 16 == 0: col = PARCH_LO
            cv.set(x, y, col)
    rounded(cv)
    return cv, dict(width=W, height=H, border=dict(left=7, right=7, top=4, bottom=4))


def button(state):
    """32x18 gold button with a 3px lip under the face. Border 4 sides, 3 top, 5 bottom."""
    W, H = 32, 18
    hi, face, lo, dk = {
        'normal': (GOLD_HI, GOLD, GOLD_LO, GOLD_DK),
        'hover': (c('#fffbe0'), c('#f7d360'), c('#c99426'), c('#8a6216')),
        'disabled': (c('#8c7b66'), c('#6b5c4b'), c('#4d4236'), c('#3a3129')),
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
    rounded(cv)
    return cv, dict(width=W, height=H, border=dict(left=4, right=4, top=3, bottom=5))


def tab(selected):
    """Bookmark tabs on the left of the panel: 26x24 (30x24 selected); the right end
    tucks under the panel frame. The 18x18 slot at (4, 3) holds the tab's item."""
    W, H = (30 if selected else 26), 24
    body, hi, lo = (WOOD, WOOD_HI, WOOD_LO) if selected else (WOOD_LO, WOOD_MID, WOOD_DK)
    cv = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            if x == 0 or y in (0, H - 1):
                col = OUT
            elif y == 1 or x == 1:
                col = hi
            elif y == H - 2:
                col = lo
            else:
                col = body
                if (x * 2 + y * 5) % 16 == 3: col = lo
            cv.set(x, y, col)
    cv.set(0, 0, T); cv.set(0, H - 1, T)
    # Item slot
    for y in range(3, 21):
        for x in range(4, 22):
            if x == 4 or y == 3:
                col = OUT
            elif x == 21 or y == 20:
                col = WELL_LIGHT
            elif x == 5 or y == 4:
                col = WELL_SHADE
            else:
                col = WELL
            cv.set(x, y, col)
    if selected:
        for y in (5, 18):
            cv.set(24, y, GOLD); cv.set(25, y, GOLD_LO)
    return cv, None


def well():
    """16x16 engraved inset for lists, border 2."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = edge_dist(x, y, 16, 16)
            tl = x <= y if x + y < 15 else False
            if d == 0:
                col = OUT if (x == 0 or y == 0) else WELL_LIGHT
            elif d == 1:
                col = WELL_SHADE if (x == 1 or y == 1) else WELL
            else:
                col = WELL
            cv.set(x, y, col)
    return cv, dict(width=16, height=16, border=2)


def row(state):
    """16x12 list row highlights, border 3 left and 1 elsewhere."""
    cv = Canvas(16, 12)
    for y in range(12):
        for x in range(16):
            d = edge_dist(x, y, 16, 12)
            if state == 'hover':
                col = (255, 232, 180, 44) if d == 0 else (255, 232, 180, 18)
            else:
                if x <= 1:
                    col = GOLD if x == 0 else GOLD_LO
                elif d == 0:
                    col = (233, 189, 58, 150)
                else:
                    col = (233, 189, 58, 46)
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
    """32x20 dark plate with a gold rim for the title preview, border 5."""
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
                col = c('#0e0a06') if (x == 3 or y == 3) else c('#1d1610')
            else:
                col = c('#15100b')
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


def gem():
    """7x5 gold diamond for the middle of dividers."""
    cv = Canvas(7, 5)
    rows = ['  #o#  ', ' #hgl# ', '#hggll#', ' #gll# ', '  #l#  ']
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            cv.set(x, y, {'#': OUT, 'h': GOLD_HI, 'g': GOLD, 'l': GOLD_LO, 'o': OUT}.get(ch))
    return cv, None


SPRITES = {
    'panel': panel(),
    'plaque': plaque(),
    'button': button('normal'),
    'button_hover': button('hover'),
    'button_disabled': button('disabled'),
    'tab': tab(False),
    'tab_selected': tab(True),
    'well': well(),
    'row_hover': row('hover'),
    'row_selected': row('selected'),
    'scroll_track': scroll_track(),
    'scroll_thumb': scroll_thumb(),
    'nameplate': nameplate(),
    'progress': progress(False),
    'progress_fill': progress(True),
    'gem': gem(),
}

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
    # Every sprite at 6x on a mid grey, in a row.
    S = 6
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
