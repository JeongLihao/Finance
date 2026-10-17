"""Procedural 16x16 pixel-art textures for the Finance mod.

Usage: python tools/textures/gen_textures.py [output_assets_textures_dir]
Default output: src/main/resources/assets/finance/textures
Style: vanilla 16px density, graphite steel + brass + teal palette, walnut for furniture.
"""
import math
import os
import sys

from PIL import Image

P = {
    # graphite steel
    '0': '#121419', '1': '#22262e', '2': '#2e333c', '3': '#3a404a', '4': '#4b525e', '5': '#646c7a',
    # brass
    'a': '#4a3210', 'b': '#7a5418', 'c': '#a87a2a', 'd': '#d4a540', 'e': '#f4d67e',
    # teal
    't': '#0b2a2e', 'u': '#12434a', 'v': '#1b5f63', 'w': '#2a8582', 'x': '#52b5a8',
    # walnut
    'h': '#2a170c', 'i': '#43271a', 'j': '#5e3a22', 'k': '#7d512f', 'l': '#9a6a3e',
    # felt
    'f': '#123a20', 'g': '#1d5530', 'G': '#2b7342',
    # paper
    'p': '#7a6444', 'q': '#b9a27c', 'r': '#dccba2', 's': '#f3e8c8',
    # status green / yellow / red / gray
    'N': '#1f7a3a', 'n': '#3fd46a', 'm': '#b0f7bb',
    'Y': '#a8781a', 'y': '#f2c230', 'z': '#fff2a0',
    'R': '#8a1c22', 'o': '#e0393b', 'O': '#ffa090',
    'S': '#555b66', 'T': '#9aa1ad', 'U': '#d6dbe2',
    # copper / orange
    'A': '#8e3f14', 'B': '#c8661e', 'C': '#f09a3c',
    # screen
    'D': '#081019', 'E': '#14243a', 'F': '#3a8ee0', 'H': '#9cd0ff',
    # misc
    'X': '#ece6d6', 'W': '#ffffff', 'K': '#c9c1ad',
}


def h(x, y, s=0):
    v = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return v ^ (v >> 16)


class Tex:
    def __init__(self, base=None):
        self.g = [r[:] for r in base.g] if base else [['.'] * 16 for _ in range(16)]

    def copy(self):
        return Tex(self)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = c
        return self

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)
        return self

    def noise(self, x0, y0, x1, y1, main, dark, light, seed=0, rate=7):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                r = h(x, y, seed) % rate
                self.px(x, y, dark if r == 0 else light if r == 1 else main)
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
        self.px(x1, y0, light).px(x0, y1, dark)
        return self

    def grid(self, x0, y0, rows):
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c not in ' .':
                    self.px(x0 + dx, y0 + dy, c)
                elif c == '.' and len(rows) == 16 and len(row) == 16:
                    self.px(x0 + dx, y0 + dy, '.')
        return self

    def save(self, path):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c != '.':
                    v = P[c]
                    im.putpixel((x, y), (int(v[1:3], 16), int(v[3:5], 16), int(v[5:7], 16), 255))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


def rivet(t, x, y):
    t.px(x, y, 'e').px(x + 1, y, 'd').px(x, y + 1, 'd').px(x + 1, y + 1, 'b')


def casing(fill=('3', '2', '4'), edge='1', hi='4', lo='2', seed=1):
    t = Tex().noise(0, 0, 15, 15, *fill, seed=seed, rate=16)
    t.box(0, 0, 15, 15, edge)
    t.bevel(1, 1, 14, 14, hi, lo)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(t, x, y)
    return t


def plain_casing(seed=1):
    t = Tex().noise(0, 0, 15, 15, '3', '2', '4', seed=seed, rate=16)
    t.box(0, 0, 15, 15, '1')
    t.bevel(1, 1, 14, 14, '4', '2')
    return t


def teal_casing():
    return casing(fill=('v', 'u', 'w'), edge='t', hi='w', lo='u', seed=3)


def indicator(dark, mid, light):
    t = casing(seed=5)
    t.box(4, 4, 11, 11, '0')
    t.bevel(5, 5, 10, 10, 'd', 'b')
    t.px(10, 5, 'c').px(5, 10, 'c')
    t.rect(6, 6, 9, 9, mid)
    t.px(6, 6, light).px(7, 6, light).px(6, 7, light)
    t.px(9, 9, dark).px(8, 9, dark).px(9, 8, dark)
    # side glow strips
    for y in (7, 8):
        t.px(2, y, dark).px(13, y, dark)
    return t


def machine_top():
    t = casing(seed=7)
    for y in (4, 6, 8, 10):
        t.rect(4, y, 11, y, '0')
        t.rect(4, y + 1, 11, y + 1, '4')
    t.px(3, 4, '1').px(12, 4, '1')
    return t


def screen_base(seed):
    t = plain_casing(seed)
    t.box(1, 1, 14, 11, '0')
    t.rect(2, 2, 13, 10, 'D')
    # control strip
    t.rect(2, 12, 13, 13, '2')
    t.rect(2, 12, 13, 12, '4')
    return t


def market_terminal():
    t = screen_base(11)
    t.grid(2, 2, [
        "            ",
        " n  NNNN nnn",
        "nnn         ",
        "EEEEEEEEEEEE",
        "ooo RRRR ooo",
        " o          ",
        "EEEEEEEEEEEE",
        " n  NNN  nn ",
        "nnn         ",
    ])
    t.grid(3, 12, ["T 5 T", "S 3 S"])
    t.px(11, 12, 'n').px(11, 13, 'N').px(12, 12, 'o').px(12, 13, 'R')
    return t


def securities_terminal():
    t = screen_base(13)
    t.rect(2, 6, 13, 6, 'E')
    t.rect(2, 9, 13, 9, 'E')
    candles = [  # x, wick_top, body_top, body_bot, wick_bot, rising
        (3, 5, 6, 8, 9, False),
        (5, 4, 5, 7, 8, True),
        (7, 3, 4, 6, 7, True),
        (9, 4, 5, 6, 8, False),
        (11, 2, 3, 5, 6, True),
        (13, 3, 3, 4, 5, True),
    ]
    for x, wt, bt, bb, wb, up in candles:
        t.rect(x, wt, x, wb, 'S')
        t.rect(x, bt, x, bb, 'n' if up else 'o')
        t.px(x, bt, 'm' if up else 'O')
    for x in (3, 5, 7, 9, 11, 13):
        t.px(x, 10, 'F')
    t.px(12, 10, 'E').px(10, 10, 'E')
    t.grid(3, 12, ["F F", "E E"])
    t.px(9, 12, 'C').px(9, 13, 'B').px(11, 12, 'C').px(11, 13, 'B')
    return t


def bank_counter():
    t = plain_casing(15)
    # teller window
    t.box(1, 1, 14, 9, 'b')
    t.bevel(1, 1, 14, 9, 'd', 'a')
    t.rect(2, 2, 13, 8, 'u')
    t.rect(2, 2, 13, 2, 't')
    for x in (4, 7, 8, 11):
        t.rect(x, 2, x, 8, 'c')
        t.px(x, 2, 'd')
    t.rect(2, 5, 13, 5, 'b')
    t.px(3, 3, 'x').px(10, 3, 'x')
    # counter top
    t.rect(1, 10, 14, 10, 'l')
    t.rect(1, 11, 14, 11, 'j')
    # coin stack + note
    t.grid(4, 8, ["ed", "dc", "ed"])
    t.px(4, 10, 'd').px(5, 10, 'b')
    t.grid(10, 9, ["sr"]).px(12, 9, 'q')
    t.px(2, 13, '5').px(13, 13, '5')
    return t


def central_bank_console():
    t = teal_casing()
    t.rect(2, 2, 13, 13, 'u')
    t.grid(2, 2, [
        "     ee     ",
        "   eeddcc   ",
        " eddddcccbb ",
        "bbbbbbbbbbbb",
        " sq sq sq sq",
        " sq sq sq sq",
        " sq sq sq sq",
        " sq sq sq sq",
        " sq sq sq sq",
        "dddddddddddd",
        "cccccccccccc",
        "bbbbbbbbbbbb",
    ])
    t.px(7, 4, 'o').px(8, 4, 'o')
    return t


def company_desk():
    t = Tex().noise(0, 0, 15, 15, 'j', 'i', 'k', seed=17, rate=5)
    t.box(0, 0, 15, 15, 'h')
    # desktop with green leather inlay
    t.rect(1, 1, 14, 3, 'l')
    t.rect(2, 2, 13, 2, 'G')
    t.rect(1, 3, 14, 3, 'k')
    t.rect(1, 4, 14, 4, 'h')
    for y0 in (6, 11):
        t.box(2, y0, 13, y0 + 3, 'h')
        t.noise(3, y0 + 1, 12, y0 + 2, 'k', 'j', 'l', seed=y0, rate=6)
        t.rect(3, y0 + 1, 12, y0 + 1, 'l')
        t.px(7, y0 + 2, 'd').px(8, y0 + 2, 'b')
    return t


def boardroom_table():
    t = Tex().noise(0, 0, 15, 15, 'i', 'h', 'j', seed=19, rate=5)
    t.bevel(0, 0, 15, 15, 'j', 'h')
    t.box(2, 2, 13, 13, 'c')
    t.bevel(2, 2, 13, 13, 'd', 'b')
    t.noise(3, 3, 12, 12, 'g', 'f', 'G', seed=23, rate=8)
    t.bevel(3, 3, 12, 12, 'f', 'G')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        t.px(x, y, 'e')
    return t


def company_factory_controller():
    t = plain_casing(29)
    cx, cy = 6, 7
    teeth = [(5, 2, 7, 3), (5, 11, 7, 12), (1, 6, 2, 8), (10, 6, 11, 8),
             (2, 3, 3, 4), (9, 3, 10, 4), (2, 10, 3, 11), (9, 10, 10, 11)]
    mask = set()
    for x0, y0, x1, y1 in teeth:
        mask |= {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}
    mask |= {(x, y) for x in range(16) for y in range(16) if math.hypot(x - cx, y - cy) <= 3.6}
    for x, y in mask:
        shade = (x - cx) + (y - cy)
        t.px(x, y, 'e' if shade <= -4 else 'd' if shade <= 0 else 'c' if shade <= 4 else 'b')
    for x, y in mask:  # dark outline toward lower right
        if (x + 1, y) not in mask:
            t.px(x + 1, y, '1')
        if (x, y + 1) not in mask:
            t.px(x, y + 1, '1')
    t.rect(5, 6, 7, 8, 'b').rect(6, 7, 6, 7, '0').px(5, 6, 'c')
    t.box(12, 1, 14, 14, '1')
    t.rect(13, 2, 13, 13, 'B')
    t.rect(13, 2, 13, 2, 'C')
    for y in (5, 10):
        t.rect(12, y, 14, y, 'b')
    t.px(13, 7, 'n').px(13, 8, 'N')
    return t


def warehouse_controller():
    t = plain_casing(31)
    t.box(2, 2, 5, 13, '0')
    t.rect(3, 3, 4, 12, '1')
    for y, c in ((12, 'n'), (11, 'n'), (10, 'n'), (9, 'n'), (8, 'y'), (7, 'y'), (6, 'B')):
        t.px(3, y, c).px(4, y, c)
    t.px(3, 5, '2').px(4, 5, '2').px(3, 4, '2').px(4, 4, '2')
    # crate panel
    t.box(7, 4, 13, 10, 'i')
    t.rect(8, 5, 12, 9, 'k')
    t.rect(8, 5, 12, 5, 'l')
    for i in range(5):
        t.px(8 + i, 5 + i, 'i').px(12 - i, 5 + i, 'i')
    for x, y in ((7, 4), (13, 4), (7, 10), (13, 10)):
        t.px(x, y, 'd')
    t.rect(7, 12, 13, 12, '2').px(8, 12, 'n').px(9, 12, 'n').px(10, 12, 'y')
    return t


AWNING = {'green': ('N', 'n'), 'yellow': ('Y', 'y'), 'red': ('R', 'o')}


def settlement_station(state):
    dark, mid = AWNING[state]
    t = Tex().noise(0, 0, 15, 15, 'k', 'j', 'l', seed=37, rate=6)
    for y in (8, 11, 14):
        t.rect(0, y, 15, y, 'j')
    # awning
    for x in range(16):
        stripe = (x // 2) % 2 == 0
        t.rect(x, 0, x, 3, mid if stripe else 'X')
        t.px(x, 0, dark if stripe else 'K')
    for x in range(16):
        if x % 4 in (1, 2):
            t.px(x, 4, dark if (x // 2) % 2 == 0 else 'K')
    t.rect(0, 5, 15, 5, 'h')
    t.box(0, 0, 15, 15, 'i')
    t.rect(0, 0, 0, 15, 'i').rect(15, 0, 15, 15, 'i')
    # counter
    t.rect(1, 10, 14, 10, 'l')
    t.rect(1, 11, 14, 11, 'i')
    if state == 'red':
        # shutters closed
        t.rect(1, 6, 14, 9, 'j')
        for y in (6, 8):
            t.rect(1, y, 14, y, 'k')
        t.rect(7, 6, 8, 9, 'i')
        t.px(7, 8, 'T').px(8, 8, 'S')
    else:
        t.rect(1, 6, 14, 9, 'h')
        # goods: emerald, bread, coins, crate
        t.grid(2, 7, [" n ", "nmN", " N "])
        t.grid(6, 8, ["ccB", "BBA"])
        if state == 'green':
            t.grid(10, 8, ["ed", "dc"])
            t.grid(12, 7, ["kl", "jk", "ij"])
        else:
            t.grid(11, 9, ["dc"])
    return t


def survey_board():
    t = Tex().noise(0, 0, 15, 15, 'l', 'k', 'q', seed=41, rate=5)
    t.box(0, 0, 15, 15, 'i')
    t.bevel(1, 1, 14, 14, 'h', 'j')
    # map sheet
    t.rect(7, 2, 13, 8, 'r')
    t.grid(8, 3, ["GGrFF", "GgrrF", "rrGrr", "FrGGo"])
    t.box(7, 2, 13, 8, 'q')
    # notes
    t.rect(2, 3, 5, 7, 's')
    for y in (4, 6):
        t.rect(3, y, 4, y, 'p')
    t.rect(3, 10, 7, 13, 'X')
    t.rect(4, 11, 6, 11, 'p').rect(4, 12, 5, 12, 'p')
    t.rect(10, 11, 12, 13, 'z')
    t.px(11, 12, 'Y')
    # string between pins
    for x, y in ((3, 3), (10, 2), (5, 10), (11, 11)):
        t.px(x, y, 'o')
    return t


def finance_guide():
    return Tex().grid(0, 0, [
        "................",
        "..0000000000000.",
        ".0duvvvvvvvvvw0.",
        ".0cuvvvvvvvvvw0.",
        ".0duvvvvvvvvvw0.",
        ".0cuvvvveddvvw0.",
        ".0cuvvveddcbvw0.",
        ".0duvvvddvcbvw0.",
        ".0cuvvvdccbbvw0.",
        ".0cuvvvvbbbvvw0.",
        ".0duvvvvvvvvvw0.",
        ".0cuvvvvvvvvvw0.",
        ".0duuuuuuuuuuu0.",
        ".0cussssssssss0.",
        "..0rrrrrrrrrrr0.",
        "...00000000000..",
    ])


def portable_ledger():
    t = Tex().grid(0, 0, [
        "................",
        "......0000......",
        ".....0eddc0.....",
        "..0000dccb0000..",
        "..0kl0bbbb0lk0..",
        "..0kssssssssk0..",
        "..0ksNNssoosk0..",
        "..0ksqqqqqqqk0..",
        "..0ksNsNssosk0..",
        "..0ksqqqqqqqk0..",
        "..0ksNNNssNsk0..",
        "..0ksqqqqqqqk0..",
        "..0kssssssrrk0..",
        "..0jkkkkkkkkj0..",
        "..0iiiiiiiiii0..",
        "...0000000000...",
    ])
    return t


def sealed_cargo_crate():
    t = Tex().grid(0, 0, [
        "................",
        ".00000000000000.",
        ".0edllllllllde0.",
        ".0dkkkkkkkkkkd0.",
        ".0ijjjjjjjjjji0.",
        ".0llkllllllkll0.",
        ".0kkkkkkkkkkkk0.",
        ".0jjjjjjjjjjjj0.",
        ".0llllllllllll0.",
        ".0kkkkkkkkkkkk0.",
        ".0jjjjjjjjjjjj0.",
        ".0llkllllllkll0.",
        ".0dkkkkkkkkkkd0.",
        ".0ebiiiiiiiibe0.",
        ".00000000000000.",
        "................",
    ])
    # twine
    t.rect(7, 2, 8, 13, 'q').rect(7, 2, 7, 13, 'r')
    t.rect(2, 7, 13, 8, 'q').rect(2, 7, 13, 7, 'r')
    # wax seal
    t.grid(6, 6, [".oo.", "oOoR", "ooRR", ".RR."])
    return t


def survey_note():
    return Tex().grid(0, 0, [
        "................",
        "..000000000000..",
        ".0ssssssrsssss0.",
        ".0sGGsssrssFFs0.",
        ".0sGgGssrsFFFs0.",
        ".0ssGsRsrssFss0.",
        ".0sssssRrsssss0.",
        ".0ssssssRsssGs0.",
        ".0sFFsssrRsGGs0.",
        ".0FFFsssrsoGs00.",
        ".0sFsssorossq0..",
        ".0ssssssosrsq0..",
        ".0ssssssrsqq0...",
        ".0qqqqqqrqq0....",
        "..000000000.....",
        "................",
    ])


TEXTURES = {
    'block/machine_casing': lambda: casing(seed=1),
    'block/machine_top': machine_top,
    'block/teal_casing': teal_casing,
    'block/indicator_normal': lambda: indicator('N', 'n', 'm'),
    'block/indicator_warning': lambda: indicator('Y', 'y', 'z'),
    'block/indicator_risk': lambda: indicator('R', 'o', 'O'),
    'block/indicator_offline': lambda: indicator('1', 'S', 'T'),
    'block/indicator_full': lambda: indicator('A', 'B', 'C'),
    'block/market_terminal': market_terminal,
    'block/securities_terminal': securities_terminal,
    'block/bank_counter': bank_counter,
    'block/central_bank_console': central_bank_console,
    'block/company_desk': company_desk,
    'block/boardroom_table': boardroom_table,
    'block/company_factory_controller': company_factory_controller,
    'block/warehouse_controller': warehouse_controller,
    'block/settlement_trade_station_green': lambda: settlement_station('green'),
    'block/settlement_trade_station_yellow': lambda: settlement_station('yellow'),
    'block/settlement_trade_station_red': lambda: settlement_station('red'),
    'block/survey_board': survey_board,
    'item/finance_guide': finance_guide,
    'item/portable_ledger': portable_ledger,
    'item/sealed_cargo_crate': sealed_cargo_crate,
    'item/survey_note': survey_note,
}


def main():
    root = os.path.join(os.path.dirname(__file__), '..', '..')
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'src/main/resources/assets/finance/textures')
    for name, fn in TEXTURES.items():
        t = fn()
        for r in t.g:
            assert len(r) == 16
        t.save(os.path.join(out, name + '.png'))
    print(f'wrote {len(TEXTURES)} textures to {out}')


if __name__ == '__main__':
    main()
