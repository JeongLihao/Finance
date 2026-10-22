"""Detail textures for the Finance mod's block models, plus its item icons, in vanilla 16px style.

Usage: python tools/textures/gen_textures.py [output_assets_textures_dir]
Default output: src/main/resources/assets/finance/textures

The block models (tools/textures/gen_models.py) build most surfaces from vanilla textures
(dark oak, smooth stone, quartz, iron bars, wool...). This script only draws what vanilla
has no texture for: screens, drawers, awnings, the notice board, status lamps and items.
Colours stay close to the vanilla palette so the pieces sit next to vanilla materials.
"""
import os
import sys

from PIL import Image

DARK_OAK = ['#26190b', '#33210e', '#3f2a13', '#4b3218', '#5a3e1f', '#6b4c28']
SPRUCE = ['#3b2812', '#4f3519', '#614321', '#72512a', '#866236', '#9a7343']
OAK = ['#5e4a2b', '#7a6038', '#94774a', '#ad8d57', '#bc9862', '#c8a46f']
STONE = ['#4a4a4a', '#5f5f5f', '#747474', '#8a8a8a', '#a0a0a0', '#b6b6b6']
IRON = ['#4a4a4a', '#6e6e6e', '#949494', '#bdbdbd', '#d8d8d8', '#ececec']
BLACKSTONE = ['#16131a', '#1e1a22', '#28232b', '#332d35', '#3d3640', '#4a434c']
GOLD = ['#5a3d04', '#8a6406', '#c2930c', '#e6b81f', '#f8d743', '#fdf09a']
PAPER = ['#8a7a5c', '#b3a27c', '#d1c39d', '#e6dbb9', '#f2ead2', '#fbf7ea']
CORK = ['#5c3e22', '#73502d', '#8a6238', '#9d7243', '#b0844f']
LEATHER = ['#1b3a1f', '#24502a', '#2f6635', '#3d7d42', '#4f9452']
SCREEN = ['#0c0f0e', '#121816', '#18211e', '#203029']
GLOW = {
    'green': ['#1d5a1d', '#3a9a2a', '#6fdc4a', '#b8ff8e', '#f0ffe2'],
    'yellow': ['#6a4c0a', '#b88d14', '#f1c232', '#ffe68a', '#fffbe0'],
    'red': ['#5a1210', '#a8261c', '#e8473a', '#ff9a86', '#fff0ea'],
    'orange': ['#5c280a', '#b0521a', '#f08a2e', '#ffc072', '#fff1dc'],
    'gray': ['#2a2a2c', '#3d3d40', '#55565a', '#6e7075', '#888a8e'],
}
WOOL = {  # stripe colour ramps for the stall awning
    'green': ['#3d5c18', '#4f7a1e', '#5f8f23'],
    'yellow': ['#b88a10', '#dcab18', '#f2c22a'],
    'red': ['#7c1b18', '#9c2722', '#b5312b'],
}
WHITE_WOOL = ['#c9cbc6', '#dfe1dc', '#eef0ec']


def h(x, y, s=0):
    v = (x * 374761393 + y * 668265263 + s * 2654435761) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return v ^ (v >> 16)


class Tex:
    def __init__(self):
        self.g = [[None] * 16 for _ in range(16)]

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = c
        return self

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)
        return self

    def box(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.px(x, y0, c).px(x, y1, c)
        for y in range(y0, y1 + 1):
            self.px(x0, y, c).px(x1, y, c)
        return self

    def bevel(self, x0, y0, x1, y1, light, dark):
        for x in range(x0, x1 + 1):
            self.px(x, y0, light).px(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.px(x0, y, light).px(x1, y, dark)
        return self

    def grid(self, x0, y0, rows, legend):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch in legend:
                    self.px(x0 + dx, y0 + dy, legend[ch])
        return self

    def fill(self, x0, y0, x1, y1, ramp, base, seed, rate=7):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                r = h(x, y, seed) % rate
                i = base - 1 if r == 0 else base + 1 if r == 1 else base
                self.px(x, y, ramp[max(0, min(len(ramp) - 1, i))])
        return self

    def save(self, path):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c:
                    im.putpixel((x, y), (int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16), 255))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


def planks(t, x0, y0, x1, y1, ramp, seed, height=4):
    """Vanilla-like horizontal planks: dark seam under each board, staggered joints."""
    for y in range(y0, y1 + 1):
        row = (y - y0) % height
        board = (y - y0) // height
        for x in range(x0, x1 + 1):
            if row == height - 1 or (x + board * 7 + seed) % 16 == 0:
                c = ramp[1]
            else:
                r = h(x // 2, y, seed + board) % 6
                c = ramp[2] if r == 0 else ramp[4] if r == 1 else ramp[3]
            t.px(x, y, c)
    return t


# ---------------------------------------------------------------- screens and panels

def screen(t, x0, y0, x1, y1):
    """Blackstone bezel around a dark glass screen."""
    t.rect(x0, y0, x1, y1, BLACKSTONE[2])
    t.bevel(x0, y0, x1, y1, BLACKSTONE[4], BLACKSTONE[1])
    t.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SCREEN[1])
    t.px(x0 + 1, y0 + 1, SCREEN[3])
    return t


def market_screen():
    """North face of the price board (uv x 1..15, y 0..9): three ticker rows."""
    t = Tex().rect(0, 0, 15, 15, BLACKSTONE[2])
    screen(t, 1, 0, 14, 8)
    G, R, W = GLOW['green'], GLOW['red'], '#9fb3a9'
    for y, col, up in ((2, G, True), (4, R, False), (6, G, True)):
        if up:
            t.px(4, y - 1, col[2]).rect(3, y, 5, y, col[2])
        else:
            t.rect(3, y - 1, 5, y - 1, col[2]).px(4, y, col[2])
        t.rect(7, y, 9, y, W).rect(11, y, 12, y, col[3])
    return t


def securities_screen():
    """North face of the monitor (uv x 2..14, y 0..7): candlestick chart."""
    t = Tex().rect(0, 0, 15, 15, BLACKSTONE[2])
    screen(t, 2, 0, 13, 6)
    for x in range(3, 13, 2):
        t.px(x, 3, SCREEN[3])
    for x, top, bot, up in ((4, 3, 4, False), (6, 2, 4, True), (8, 2, 3, True), (10, 1, 3, True), (12, 1, 2, True)):
        col = GLOW['green'] if up else GLOW['red']
        t.rect(x, top, x, bot, col[2]).px(x, top, col[3])
    return t


def keyboard():
    """Top of the keyboard slab (uv x 3..13, z 2..6)."""
    t = Tex().rect(0, 0, 15, 15, BLACKSTONE[2])
    t.rect(3, 2, 12, 5, BLACKSTONE[1])
    for y in (2, 4):
        for x in range(3, 13):
            t.px(x, y, STONE[4] if (x + y // 2) % 2 == 0 else STONE[3])
    t.rect(5, 5, 10, 5, STONE[3])
    return t


def gauge_panel():
    """Top of the central bank's sloped panel (uv x 2..14, z 3..13): dial and buttons."""
    t = Tex().rect(0, 0, 15, 15, BLACKSTONE[2])
    t.rect(2, 3, 13, 12, BLACKSTONE[3])
    t.bevel(2, 3, 13, 12, BLACKSTONE[5], BLACKSTONE[1])
    t.grid(3, 3, [
        "...GGGG...",
        ".GGWWWWGG.",
        ".GgWWWWrG.",
        "GWgWWWWrWG",
        "GWWWWWWnWG",
        "GWWWWWnWWG",
        "GWWWWkWWWG",
        ".GWWWWWWG.",
        ".GGWWWWGG.",
        "...GGGG...",
    ], {'G': GOLD[3], 'W': '#ece6d2', 'g': GLOW['green'][1], 'r': GLOW['red'][1],
        'n': BLACKSTONE[1], 'k': BLACKSTONE[0]})
    t.px(4, 4, GOLD[4]).px(3, 6, GOLD[4]).px(11, 11, GOLD[1]).px(12, 9, GOLD[1])
    return t


def factory_panel():
    """North face of the company factory controller cabinet (uv x 1..15, y 3..16)."""
    t = Tex().fill(0, 0, 15, 15, STONE, 3, 5, rate=9)
    t.rect(1, 3, 14, 15, STONE[3])
    t.bevel(1, 3, 14, 15, STONE[4], STONE[1])
    t.grid(5, 4, ["..GGGG..", ".GWWWnG.", "GWWWnWWG", "GWWkWWWG", ".GWWWWG.", "..GGGG.."],
           {'G': IRON[1], 'W': '#e8e4d8', 'n': GLOW['red'][1], 'k': BLACKSTONE[0]})
    for x, col in ((4, GLOW['green']), (7, GLOW['yellow']), (10, GLOW['red'])):
        t.rect(x, 11, x + 1, 11, col[2]).px(x, 11, col[3])
    t.rect(3, 13, 12, 13, BLACKSTONE[1]).rect(3, 14, 12, 14, STONE[2])
    return t


# ---------------------------------------------------------------- furniture details

def teller_grille():
    """Teller window bars (cutout), sampled through uv y 0..7: rail on top, bars every 3px."""
    t = Tex()
    for x in range(1, 16, 3):
        t.rect(x, 1, x, 7, IRON[3]).px(x, 7, IRON[1])
    t.rect(0, 0, 15, 0, IRON[4]).rect(0, 1, 15, 1, IRON[2])
    return t


def desk_drawers():
    """North face of the desk pedestal (uv x 0..7, y 2..16): two drawers."""
    t = planks(Tex(), 0, 0, 15, 15, DARK_OAK, seed=3)
    for y0 in (3, 9):
        t.rect(1, y0, 5, y0 + 4, DARK_OAK[3])
        t.bevel(1, y0, 5, y0 + 4, DARK_OAK[5], DARK_OAK[1])
        t.rect(2, y0 + 2, 4, y0 + 2, IRON[2]).px(2, y0 + 2, IRON[4])
    return t


def ledger_cover():
    """Green leather book cover with gold corners."""
    t = Tex().fill(0, 0, 15, 15, LEATHER, 2, 11, rate=8)
    t.bevel(0, 0, 15, 15, LEATHER[3], LEATHER[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        t.px(x, y, GOLD[3])
    t.rect(6, 6, 9, 9, GOLD[2]).px(6, 6, GOLD[4]).px(9, 9, GOLD[1])
    return t


def ledger_paper():
    """Paper with ruled ledger lines: loose sheets and book page edges."""
    t = Tex().rect(0, 0, 15, 15, PAPER[4])
    for y in range(2, 15, 2):
        t.rect(1, y, 14, y, PAPER[2])
    for x, y in ((3, 4), (4, 4), (6, 4), (3, 8), (5, 8), (7, 8), (3, 12), (4, 12)):
        t.px(x, y, PAPER[0])
    t.rect(10, 4, 12, 4, '#3d7a3d').rect(10, 8, 11, 8, '#a83a2a').rect(10, 12, 12, 12, '#3d7a3d')
    return t


def awning(color):
    """Striped canvas; stripes run along v so they slope down the canopy, hem on the last rows."""
    t = Tex()
    col = WOOL[color]
    for x in range(16):
        ramp = col if (x // 2) % 2 == 0 else WHITE_WOOL
        for y in range(16):
            r = h(x, y, 7) % 7
            t.px(x, y, ramp[0] if r == 0 else ramp[2] if r == 1 else ramp[1])
        t.px(x, 15, ramp[0])
    return t


def notice_board():
    """North face of the survey board (uv y 1..13): cork with pinned notes and a map."""
    t = Tex().fill(0, 0, 15, 15, CORK, 2, 13, rate=4)
    t.rect(8, 2, 13, 7, PAPER[3]).bevel(8, 2, 13, 7, PAPER[4], PAPER[1])
    t.grid(9, 3, ["gg.b", "g..b", ".x.g", "bb.g"],
           {'g': '#5f8a3a', 'b': '#4a7ab0', 'x': GLOW['red'][1]})
    t.rect(2, 2, 5, 6, PAPER[4]).bevel(2, 2, 5, 6, PAPER[5], PAPER[2])
    t.rect(3, 4, 4, 4, PAPER[1])
    t.rect(3, 8, 7, 11, PAPER[3]).bevel(3, 8, 7, 11, PAPER[4], PAPER[1])
    t.rect(4, 9, 6, 9, PAPER[0]).rect(4, 10, 5, 10, PAPER[1])
    t.rect(9, 9, 12, 11, GLOW['yellow'][3]).bevel(9, 9, 12, 11, GLOW['yellow'][4], GLOW['yellow'][2])
    for x, y in ((3, 2), (10, 2), (5, 8), (10, 9)):
        t.px(x, y, GLOW['red'][2])
    return t


def warehouse_front():
    """Full-block front: spruce frame, two shelves holding crates and sacks."""
    t = planks(Tex(), 0, 0, 15, 15, SPRUCE, seed=9)
    for y0 in (1, 8):
        t.rect(1, y0, 14, y0 + 5, SPRUCE[0])
        t.rect(1, y0 + 6, 14, y0 + 6, SPRUCE[4])
    t.box(0, 0, 15, 15, SPRUCE[1])
    for x0 in (2, 8):
        t.rect(x0, 3, x0 + 4, 6, OAK[3]).bevel(x0, 3, x0 + 4, 6, OAK[5], OAK[1])
        t.px(x0 + 1, 4, OAK[1]).px(x0 + 3, 5, OAK[1])
    t.grid(2, 10, [".sss.", "sSSSs", "sSSSs", "sssss"], {'s': '#a28a5e', 'S': '#bba377'})
    t.rect(9, 10, 12, 13, SPRUCE[3]).box(9, 10, 12, 13, SPRUCE[1]).rect(10, 11, 11, 12, SPRUCE[4])
    return t


def factory_front():
    """Factory front: stone frame, iron hatch with a glowing slit."""
    t = Tex().fill(0, 0, 15, 15, STONE, 3, 21, rate=8)
    t.bevel(0, 0, 15, 15, STONE[4], STONE[1])
    t.rect(3, 3, 12, 12, IRON[1]).bevel(3, 3, 12, 12, IRON[3], IRON[0])
    t.rect(5, 4, 10, 5, IRON[2]).rect(5, 11, 10, 11, IRON[2])
    t.rect(4, 7, 11, 9, BLACKSTONE[1])
    t.rect(5, 8, 10, 8, GLOW['orange'][2]).px(6, 8, GLOW['orange'][3]).px(9, 8, GLOW['orange'][3])
    for x in (4, 11):
        for y in (4, 11):
            t.px(x, y, IRON[4])
    return t


def factory_side():
    t = Tex().fill(0, 0, 15, 15, STONE, 3, 23, rate=8)
    t.bevel(0, 0, 15, 15, STONE[4], STONE[1])
    for y in (5, 10):
        t.rect(0, y, 15, y, IRON[1]).rect(0, y - 1, 15, y - 1, IRON[3])
        for x in (2, 13):
            t.px(x, y - 1, IRON[5])
    return t


def factory_top():
    t = Tex().fill(0, 0, 15, 15, STONE, 3, 25, rate=8)
    t.bevel(0, 0, 15, 15, STONE[4], STONE[1])
    t.rect(4, 4, 11, 11, IRON[1]).bevel(4, 4, 11, 11, IRON[0], IRON[3])
    for y in (5, 7, 9):
        t.rect(5, y, 10, y, BLACKSTONE[0])
    return t


def lamp(color):
    """Status lamp, sampled through uv 4..12 on the small lamp cubes."""
    g = GLOW[color]
    t = Tex().rect(0, 0, 15, 15, g[1])
    t.rect(4, 4, 11, 11, g[2])
    t.box(4, 4, 11, 11, g[1])
    t.rect(4, 4, 11, 4, g[2])
    t.rect(5, 5, 7, 7, g[3]).px(5, 5, g[4])
    t.rect(9, 10, 10, 10, g[1])
    return t


# ---------------------------------------------------------------- items (vanilla item style)

def item(rows, legend):
    assert all(len(r) == 16 for r in rows), rows
    return Tex().grid(0, 0, rows, legend)


def finance_guide():
    L = {'o': '#1b2f2f', '2': '#2d5e60', '3': '#3b7779', '4': '#5aa0a0',
         'g': GOLD[3], 'G': GOLD[4], 'b': GOLD[1], 'P': PAPER[5], 'p': PAPER[3], 'q': PAPER[1]}
    return item([
        "................",
        "................",
        "...ooooooooooo..",
        "..o43333333332o.",
        "..o43333333332o.",
        "..o43333GGb332o.",
        "..o4333GgggG32o.",
        "..o4333ggogb32o.",
        "..o4333Ggggb32o.",
        "..o43333bbb332o.",
        "..o43333333332o.",
        "..o22222222222o.",
        "..oPPPPPPPPPPpo.",
        "..oqqqqqqqqqqqo.",
        "...ooooooooooo..",
        "................",
    ], L)


def portable_ledger():
    L = {'o': '#3a2410', '2': OAK[2], '3': OAK[3], '4': OAK[4],
         'i': IRON[1], 'I': IRON[3], 'J': IRON[4],
         'P': PAPER[5], 'p': PAPER[3], 'q': PAPER[1], 'g': '#3d7a3d', 'r': '#a83a2a'}
    return item([
        "................",
        "......iiii......",
        ".....iJIIi......",
        "...oooiiiiooo...",
        "...o4PPPPPP3o...",
        "...o4PgPrrP3o...",
        "...o4qqqqqq3o...",
        "...o4PggPrP3o...",
        "...o4qqqqqq3o...",
        "...o4PgggPP3o...",
        "...o4qqqqqq3o...",
        "...o4PPPPPp3o...",
        "...o33333333o...",
        "...o22222222o...",
        "...oooooooooo...",
        "................",
    ], L)


def sealed_cargo_crate():
    L = {'o': '#3b2812', '1': SPRUCE[1], '3': SPRUCE[3], '4': SPRUCE[4], '5': SPRUCE[5],
         'i': IRON[2], 'I': IRON[4], 'r': '#7c1b18', 'R': '#b5312b', 'S': '#e05a48', 's': PAPER[2]}
    return item([
        "................",
        "................",
        "..oooooooooooo..",
        "..oI55555555Io..",
        "..o4444444444o..",
        "..o3333ss3333o..",
        "..o1111ss1111o..",
        "..o5555RR5555o..",
        "..o444RSRr444o..",
        "..o3333rr3333o..",
        "..o1111ss1111o..",
        "..o5555ss5555o..",
        "..oi44444444io..",
        "..oooooooooooo..",
        "................",
        "................",
    ], L)


def survey_note():
    L = {'o': PAPER[0], 'P': PAPER[4], 'q': PAPER[2],
         'g': '#5f8a3a', 'b': '#4a7ab0', 'x': GLOW['red'][1]}
    return item([
        "................",
        "................",
        "...oooooooooo...",
        "..oPPPPPPPPPPo..",
        "..oPggPPPPbbPo..",
        "..oPgPPxPPPbPo..",
        "..oPPPPPxPPPPo..",
        "..oPPbPPPxPPPo..",
        "..oPbbPPPPxgPo..",
        "..oPPPPPPPxggo..",
        "..oPPPPPPxPPo...",
        "..oqqqqqqqqo....",
        "...oooooooo.....",
        "................",
        "................",
        "................",
    ], L)


TEXTURES = {
    'block/market_screen': market_screen,
    'block/securities_screen': securities_screen,
    'block/keyboard': keyboard,
    'block/gauge_panel': gauge_panel,
    'block/factory_panel': factory_panel,
    'block/teller_grille': teller_grille,
    'block/desk_drawers': desk_drawers,
    'block/ledger_cover': ledger_cover,
    'block/ledger_paper': ledger_paper,
    'block/awning_green': lambda: awning('green'),
    'block/awning_yellow': lambda: awning('yellow'),
    'block/awning_red': lambda: awning('red'),
    'block/notice_board': notice_board,
    'block/warehouse_front': warehouse_front,
    'block/factory_front': factory_front,
    'block/factory_side': factory_side,
    'block/factory_top': factory_top,
    'block/indicator_normal': lambda: lamp('green'),
    'block/indicator_warning': lambda: lamp('yellow'),
    'block/indicator_risk': lambda: lamp('red'),
    'block/indicator_offline': lambda: lamp('gray'),
    'block/indicator_full': lambda: lamp('orange'),
    'item/finance_guide': finance_guide,
    'item/portable_ledger': portable_ledger,
    'item/sealed_cargo_crate': sealed_cargo_crate,
    'item/survey_note': survey_note,
}


def main():
    root = os.path.join(os.path.dirname(__file__), '..', '..')
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'src/main/resources/assets/finance/textures')
    for name, fn in TEXTURES.items():
        fn().save(os.path.join(out, name + '.png'))
    print(f'wrote {len(TEXTURES)} textures to {out}')


if __name__ == '__main__':
    main()
