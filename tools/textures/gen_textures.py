"""Procedural 16x16 pixel-art textures for the Finance mod, in a Create-mod-like style.

Usage: python tools/textures/gen_textures.py [output_assets_textures_dir]
Default output: src/main/resources/assets/finance/textures

Style notes (after Create's casings): chunky 2px metal frames (andesite / brass / copper)
with soft top-left light, recessed wooden centers, warm low-saturation ramps, no pure-black
outlines, brass-rimmed gauges and amber nixie-style displays.
"""
import math
import os
import sys

from PIL import Image

# ramps, dark -> light
ANDESITE = ['#353638', '#4e5052', '#6a6c6d', '#868886', '#a3a4a0', '#c2c1bb']
BRASS = ['#4a2e12', '#774c1b', '#a46e28', '#cc9638', '#e8bf58', '#fbe496']
COPPER = ['#4a2216', '#7a3922', '#a9532f', '#cf7444', '#e99863', '#f7c08c']
WOOD = ['#3a2615', '#533720', '#6c492a', '#876034', '#a27741', '#b98d52']
DARKWOOD = ['#1f140b', '#2e1f11', '#412c18', '#553a20', '#6a4a2a', '#7e5a34']
SPRUCE = ['#2e1d10', '#432c18', '#5a3c21', '#714d2b', '#8a6036', '#a07343']
FELT = ['#14341f', '#1d4a2b', '#285f37', '#357545', '#468b54']
CORK = ['#5e4026', '#7a5634', '#966c42', '#b08552', '#c49c66']
PAPER = ['#7d6a4c', '#ab9672', '#cfbd96', '#e8dcbc', '#f7f0dc']
DISPLAY = ['#120e0b', '#1b1611', '#262019', '#332a20']
DIAL = ['#8d877b', '#c9c3b4', '#ebe6d8', '#fbf8ee']
TEAL = ['#0f2e2d', '#174443', '#1f5b58', '#2b736e', '#3f8d85', '#5ba89c']
GLOW = {
    'amber': ['#5c2208', '#b04e12', '#ec8a2a', '#ffc066', '#fff0c8'],
    'green': ['#173f16', '#2f7d27', '#58bf43', '#9fe67c', '#e2ffd0'],
    'yellow': ['#5e420a', '#b98a18', '#efc439', '#ffe385', '#fff8d8'],
    'red': ['#4e0f0d', '#a0261d', '#e2483a', '#ff8f78', '#ffe0d6'],
    'gray': ['#2c2d2f', '#4a4c4f', '#6a6d70', '#8e9194', '#b4b6b8'],
    'orange': ['#5a250a', '#b4521a', '#ee7f2c', '#ffb36a', '#ffe6c4'],
}


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

    def get(self, x, y):
        return self.g[y][x]

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

    def save(self, path):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c:
                    im.putpixel((x, y), (int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16), 255))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


def jitter(ramp, i, x, y, seed, rate=6):
    r = h(x, y, seed) % rate
    if r == 0:
        i -= 1
    elif r == 1:
        i += 1
    return ramp[max(0, min(len(ramp) - 1, i))]


# ---------------------------------------------------------------- materials

def planks(t, x0, y0, x1, y1, ramp, seed=0, plank=4, vertical=False):
    """Soft wooden planks with grain, highlight on each plank's top edge."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            a, b = (x - x0, y - y0) if not vertical else (y - y0, x - x0)
            row = b % plank
            board = b // plank
            if row == 0:
                i = 3
            elif row == plank - 1:
                i = 1
            else:
                i = 2
            # butt joints offset per board
            if (a + board * 5 + seed) % 11 == 0 and row != 0:
                i = 1
            elif h(a // 4, b, seed + board) % 6 == 0 and 0 < row < plank - 1:
                i = 3
            t.px(x, y, jitter(ramp, i, x, y, seed + 7, 18))
    return t


def frame(t, ramp, seed=0, inset_shadow=None):
    """Create-style 2px metal frame with soft lighting and rounded inner corners."""
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            if ring > 1:
                continue
            lit = (x + y) < 15  # top-left half
            if ring == 0:
                i = 2 if lit else 1
                if x in (0, 15) and y in (0, 15):
                    i = 1
            else:
                i = 4 if lit else 3
            t.px(x, y, jitter(ramp, i, x, y, seed, 14))
    # highlight streaks on top/left ring 1
    for k in range(3, 13):
        if h(k, 0, seed) % 3 == 0:
            t.px(k, 1, ramp[5])
    # bolts in the corners of ring 1
    for (bx, by) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        t.px(bx, by, ramp[5] if (bx, by) == (1, 1) else ramp[4] if bx + by < 15 else ramp[2])
    # recessed shadow just inside the frame
    if inset_shadow:
        dark, light = inset_shadow
        for k in range(2, 14):
            t.px(k, 2, dark).px(2, k, dark)
        # rounded inner corners
        t.px(2, 2, ramp[3]).px(13, 2, ramp[3]).px(2, 13, ramp[2]).px(13, 13, ramp[2])
    return t


def casing(metal, wood=WOOD, seed=0):
    t = Tex()
    planks(t, 2, 2, 13, 13, wood, seed=seed)
    frame(t, metal, seed, inset_shadow=(wood[0], wood[4]))
    return t


def recessed(t, x0, y0, x1, y1, ramp):
    """Dark recessed panel with a lip: shadow on top-left, light on bottom-right."""
    t.rect(x0, y0, x1, y1, ramp[1])
    t.bevel(x0, y0, x1, y1, ramp[0], ramp[3])
    return t


def lamp(t, cx, cy, glow, rim=BRASS):
    """4x4 glowing glass in a brass rim, top-left of the glass at (cx, cy)."""
    t.box(cx - 1, cy - 1, cx + 4, cy + 4, rim[2])
    t.bevel(cx - 1, cy - 1, cx + 4, cy + 4, rim[4], rim[1])
    t.px(cx - 1, cy - 1, rim[3]).px(cx + 4, cy + 4, rim[0])
    for y in range(4):
        for x in range(4):
            d = x + y
            t.px(cx + x, cy + y, glow[3] if d <= 1 else glow[2] if d <= 4 else glow[1])
    t.px(cx, cy, glow[4]).px(cx + 1, cy, glow[4])
    return t


def bolt(t, x, y, ramp):
    t.px(x, y, ramp[4]).px(x + 1, y + 1, ramp[1]).px(x + 1, y, ramp[3]).px(x, y + 1, ramp[2])


# ---------------------------------------------------------------- casings / tops

def machine_casing():
    return casing(ANDESITE, WOOD, seed=1)


def teal_casing():
    return casing(BRASS, DARKWOOD, seed=3)


def machine_top():
    t = casing(ANDESITE, WOOD, seed=5)
    # shaft socket: andesite square with a dark axle
    recessed(t, 5, 5, 10, 10, ANDESITE)
    t.rect(6, 6, 9, 9, ANDESITE[3])
    t.bevel(6, 6, 9, 9, ANDESITE[5], ANDESITE[2])
    t.rect(7, 7, 8, 8, ANDESITE[1])
    t.px(7, 7, ANDESITE[0])
    return t


def indicator(glow):
    t = casing(ANDESITE, WOOD, seed=9)
    recessed(t, 4, 4, 11, 11, ANDESITE)
    lamp(t, 6, 6, GLOW[glow])
    # light spill on the wood
    if glow != 'gray':
        for x, y in ((7, 3), (8, 3), (3, 7), (3, 8), (12, 7), (12, 8), (7, 12), (8, 12)):
            t.px(x, y, GLOW[glow][1])
    return t


# ---------------------------------------------------------------- machines

def display_panel(seed):
    t = Tex()
    frame(t, BRASS, seed)
    recessed(t, 2, 2, 13, 13, DISPLAY)
    return t


def market_terminal():
    t = display_panel(11)
    A, G, R = GLOW['amber'], GLOW['green'], GLOW['red']
    D = DISPLAY
    # three ticker lines: arrow + amber digits
    rows = [(4, G, True), (7, R, False), (10, G, True)]
    for y, col, up in rows:
        if up:
            t.px(4, y - 1, col[3]).rect(3, y, 5, y, col[2])
        else:
            t.rect(3, y - 1, 5, y - 1, col[2]).px(4, y, col[3])
        for x in (7, 8, 9, 11, 12):
            t.px(x, y - 1, A[2]).px(x, y, A[3] if (x + y) % 3 else A[2])
        t.px(10, y, A[1])
    for y in (5, 8):
        t.rect(3, y + 1, 12, y + 1, D[2])
    t.px(12, 3, A[4])
    return t


def securities_terminal():
    t = display_panel(13)
    A = GLOW['amber']
    D = DISPLAY
    for y in (5, 8, 11):
        for x in range(3, 13, 2):
            t.px(x, y, D[3])
    pts = [(3, 10), (5, 9), (6, 10), (8, 7), (9, 8), (11, 5), (12, 4)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for k in range(n + 1):
            x = x0 + round((x1 - x0) * k / n)
            y = y0 + round((y1 - y0) * k / n)
            t.px(x, y + 1, A[1])
            t.px(x, y, A[3])
    t.px(12, 4, A[4]).px(12, 3, A[2])
    # volume ticks
    for x, hgt in ((4, 1), (7, 1), (10, 1), (12, 1)):
        t.rect(x, 12 - hgt + 1, x, 12, GLOW['green'][1])
    return t


def bank_counter():
    t = Tex()
    planks(t, 2, 2, 13, 13, DARKWOOD, seed=15)
    frame(t, BRASS, 15, inset_shadow=(DARKWOOD[0], DARKWOOD[4]))
    # teller window with brass bars over teal glass
    recessed(t, 3, 3, 12, 8, TEAL)
    t.px(4, 4, TEAL[5]).px(5, 4, TEAL[4]).px(4, 5, TEAL[4])
    for x in (5, 8, 10):
        t.rect(x, 4, x, 7, BRASS[3])
        t.px(x, 4, BRASS[5]).px(x, 7, BRASS[1])
    t.rect(4, 6, 11, 6, BRASS[2])
    # counter ledge + coin slot
    t.rect(2, 9, 13, 9, BRASS[4])
    t.rect(2, 10, 13, 10, BRASS[1])
    t.rect(6, 12, 9, 12, DARKWOOD[0])
    t.rect(6, 11, 9, 11, BRASS[2]).px(6, 11, BRASS[4])
    for x, y in ((3, 11), (11, 11)):
        bolt(t, x, y, BRASS)
    return t


def central_bank_console():
    t = Tex()
    planks(t, 2, 2, 13, 13, DARKWOOD, seed=17)
    frame(t, BRASS, 17, inset_shadow=(DARKWOOD[0], DARKWOOD[4]))
    L = {'e': BRASS[5], 'd': BRASS[4], 'c': BRASS[2], 'b': BRASS[1],
         'W': DIAL[2], 'w': DIAL[1], 'g': GLOW['green'][2], 'y': GLOW['yellow'][2], 'r': GLOW['red'][2],
         'n': DARKWOOD[1], 'h': ANDESITE[0]}
    t.grid(2, 2, [
        "....eeed....",
        "..eegggyyd..",
        ".eggWWWWyrd.",
        ".egWWWWWWrc.",
        "egWWWWWWnWrc",
        "eWWWWWWnWWWc",
        "dWWWWWhWWWWb",
        "dWWWWWWWWwwb",
        ".cWWWWWWwwb.",
        ".cwWWWWwwwb.",
        "..cbwwwwbb..",
        "....bbbb....",
    ], L)
    return t


def company_desk():
    t = Tex()
    planks(t, 0, 0, 15, 15, DARKWOOD, seed=19, plank=5)
    t.bevel(0, 0, 15, 15, DARKWOOD[3], DARKWOOD[0])
    # desktop lip with green leather
    t.rect(1, 1, 14, 3, WOOD[3])
    t.rect(1, 1, 14, 1, WOOD[4])
    t.rect(2, 2, 13, 2, FELT[3])
    t.rect(1, 4, 14, 4, DARKWOOD[0])
    for y0 in (6, 11):
        t.rect(2, y0, 13, y0 + 3, WOOD[2])
        t.bevel(2, y0, 13, y0 + 3, WOOD[4], WOOD[0])
        t.rect(3, y0 + 1, 12, y0 + 2, WOOD[3])
        t.rect(6, y0 + 1, 9, y0 + 1, BRASS[3]).px(6, y0 + 1, BRASS[5])
        t.rect(7, y0 + 2, 8, y0 + 2, BRASS[1])
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        bolt(t, x, y, BRASS)
    return t


def boardroom_table():
    t = Tex()
    planks(t, 0, 0, 15, 15, DARKWOOD, seed=21, plank=4, vertical=True)
    t.bevel(0, 0, 15, 15, DARKWOOD[4], DARKWOOD[0])
    t.box(2, 2, 13, 13, BRASS[3])
    t.bevel(2, 2, 13, 13, BRASS[4], BRASS[1])
    for y in range(3, 13):
        for x in range(3, 13):
            t.px(x, y, jitter(FELT, 2, x, y, 23, 7))
    t.bevel(3, 3, 12, 12, FELT[1], FELT[3])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(t, x, y, BRASS)
    return t


COG = [
    "....4433....",
    ".44.4433.33.",
    ".4444433333.",
    "..44444333..",
    "4444DDDC3322",
    "4444DaAB3322",
    "3333DAAB2222",
    "3333CBBB2222",
    "..33332222..",
    ".3333322222.",
    ".33.3222.22.",
    "....3222....",
]


def cog(t, wood=SPRUCE, hub=ANDESITE):
    """Create-style cogwheel face filling the 12x12 inner area."""
    L = {'4': wood[4], '3': wood[3], '2': wood[2],
         'a': hub[0], 'A': hub[1], 'B': hub[3], 'C': hub[4], 'D': hub[5]}
    t.grid(2, 2, COG, L)
    # drop shadow onto the recessed background
    for y, row in enumerate(COG):
        for x, ch in enumerate(row):
            if ch != '.':
                for nx, ny in ((x + 1, y + 1),):
                    if ny < 12 and nx < 12 and COG[ny][nx] == '.':
                        t.px(nx + 2, ny + 2, DISPLAY[0])
    return t


def company_factory_controller():
    t = Tex()
    frame(t, ANDESITE, 29)
    recessed(t, 2, 2, 13, 13, DISPLAY)
    cog(t)
    lamp_c = GLOW['green']
    t.px(12, 3, lamp_c[3]).px(12, 4, lamp_c[1]).px(11, 3, BRASS[2])
    return t


def warehouse_controller():
    t = casing(ANDESITE, WOOD, seed=31)
    # tank window gauge on the left
    recessed(t, 3, 3, 6, 12, DISPLAY)
    levels = [GLOW['green']] * 4 + [GLOW['yellow']] * 2 + [GLOW['orange']]
    for k, g in enumerate(levels):
        y = 11 - k
        t.px(4, y, g[3]).px(5, y, g[2])
    t.px(4, 4, '#3a3f44').px(5, 4, '#2b2f33')
    t.rect(3, 7, 3, 7, ANDESITE[4])
    # crate on the right with andesite corners
    t.rect(8, 4, 12, 11, SPRUCE[3])
    t.bevel(8, 4, 12, 11, SPRUCE[4], SPRUCE[1])
    for i in range(6):
        x = 8 + round(i * 4 / 5)
        t.px(x, 5 + i, SPRUCE[0]).px(20 - x, 5 + i, SPRUCE[0])
    t.rect(8, 4, 12, 4, SPRUCE[5])
    for x, y in ((8, 4), (12, 4), (8, 11), (12, 11)):
        t.px(x, y, ANDESITE[4] if y == 4 else ANDESITE[2])
    return t


# ---------------------------------------------------------------- furniture / stations

AWNING = {
    'green': ['#1e5a2a', '#2f7d3a', '#4aa055'],
    'yellow': ['#94690f', '#c99a22', '#e8be45'],
    'red': ['#7a1c1c', '#a92c26', '#cf4a3c'],
}
CLOTH = ['#b7ad98', '#ddd3bf', '#f2ecdf']


def settlement_station(state):
    aw = AWNING[state]
    t = Tex()
    planks(t, 0, 0, 15, 15, SPRUCE, seed=37, plank=4)
    # awning: soft shaded stripes
    for x in range(16):
        c = aw if (x // 2) % 2 == 0 else CLOTH
        t.px(x, 0, c[0])
        t.rect(x, 1, x, 3, c[1])
        t.px(x, 1, c[2])
        if x % 4 in (1, 2):
            t.px(x, 4, c[0])
    t.rect(0, 5, 15, 5, SPRUCE[0])
    t.rect(0, 4, 15, 4, SPRUCE[0]) if False else None
    for x in range(16):
        if x % 4 not in (1, 2):
            t.px(x, 4, SPRUCE[0])
    # posts
    for x in (0, 15):
        t.rect(x, 4, x, 15, SPRUCE[1])
    t.rect(1, 4, 1, 15, SPRUCE[3]) if False else None
    # counter top
    t.rect(1, 10, 14, 10, SPRUCE[5])
    t.rect(1, 11, 14, 11, SPRUCE[1])
    if state == 'red':
        # closed shutter
        planks(t, 1, 6, 14, 9, WOOD, seed=3, plank=2)
        t.rect(7, 6, 8, 9, WOOD[1])
        t.px(7, 8, ANDESITE[4]).px(8, 8, ANDESITE[2])
    else:
        t.rect(1, 6, 14, 9, SPRUCE[0])
        E = ['#1c6b4a', '#2fae6e', '#7ff0b0']
        t.px(3, 7, E[1]).px(2, 8, E[1]).px(3, 8, E[2]).px(4, 8, E[0]).px(3, 9, E[0])
        t.rect(6, 8, 8, 9, '#b06a2a').rect(6, 8, 8, 8, '#d89448').px(8, 9, '#7d4a1d')
        if state == 'green':
            t.px(10, 9, BRASS[4]).px(11, 9, BRASS[2]).px(10, 8, BRASS[5]).px(11, 8, BRASS[3])
            t.rect(12, 7, 13, 9, WOOD[3]).rect(12, 7, 13, 7, WOOD[5]).px(13, 9, WOOD[1])
        else:
            t.px(11, 9, BRASS[4]).px(12, 9, BRASS[2])
    return t


def survey_board():
    t = Tex()
    for y in range(2, 14):
        for x in range(2, 14):
            t.px(x, y, jitter(CORK, 2, x, y, 41, 4))
    frame(t, SPRUCE, 41, inset_shadow=(CORK[0], CORK[4]))
    # map
    t.rect(7, 3, 12, 8, PAPER[3])
    t.bevel(7, 3, 12, 8, PAPER[4], PAPER[1])
    L = {'g': FELT[3], 'G': FELT[4], 'b': '#4a86b8', 'B': '#79acd6', 'x': GLOW['red'][2]}
    t.grid(8, 4, ["gG.bB", "g..bb", ".x.g.", "bb.gG"], L)
    # notes
    t.rect(3, 3, 5, 7, PAPER[4]).bevel(3, 3, 5, 7, PAPER[4], PAPER[2])
    t.px(4, 5, PAPER[0]).px(4, 6, PAPER[1])
    t.rect(3, 9, 7, 12, PAPER[3]).bevel(3, 9, 7, 12, PAPER[4], PAPER[1])
    t.rect(4, 10, 6, 10, PAPER[0]).rect(4, 11, 5, 11, PAPER[1])
    t.rect(9, 10, 12, 12, GLOW['yellow'][3]).bevel(9, 10, 12, 12, GLOW['yellow'][4], GLOW['yellow'][2])
    t.px(10, 11, GLOW['yellow'][1])
    for x, y in ((4, 3), (9, 3), (5, 9), (10, 10)):
        t.px(x, y, GLOW['red'][2])
    return t


# ---------------------------------------------------------------- items

def item(rows, legend):
    t = Tex()
    t.grid(0, 0, rows, legend)
    return t


def finance_guide():
    L = {'o': TEAL[0], '1': TEAL[1], '2': TEAL[2], '3': TEAL[3], '4': TEAL[4], '5': TEAL[5],
         'a': BRASS[0], 'b': BRASS[1], 'c': BRASS[2], 'd': BRASS[3], 'e': BRASS[4], 'f': BRASS[5],
         'p': PAPER[2], 'q': PAPER[3], 'r': PAPER[4], 'n': PAPER[1]}
    return item([
        "................",
        "...ooooooooooo..",
        "..oed444444443o.",
        "..ob3333333332o.",
        "..o23333ddd332o.",
        "..o2333dfedc32o.",
        "..o2333ed1cb32o.",
        "..o2333dccbb32o.",
        "..o23333bbb332o.",
        "..o2333333333 2o.".replace(' ', ''),
        "..o2333333333 2o.".replace(' ', ''),
        "..od22222222dbo.",
        "..oarrrrrrrrrao.",
        "..oqqqqqqqqqqpo.",
        "...onnnnnnnnnno.",
        "....oooooooooo..",
    ], L)


def portable_ledger():
    L = {'o': WOOD[0], '1': WOOD[1], '2': WOOD[2], '3': WOOD[3], '4': WOOD[4],
         'A': ANDESITE[1], 'B': ANDESITE[3], 'C': ANDESITE[4], 'D': ANDESITE[5],
         'p': PAPER[2], 'q': PAPER[3], 'r': PAPER[4], 'n': PAPER[1],
         'g': GLOW['green'][1], 'x': GLOW['red'][1]}
    return item([
        "................",
        "......AAAA......",
        ".....ADCCBA.....",
        "..oooABBBBAooo..",
        "..o43AAAAAA34o..",
        "..o3rrrrrrrr2o..",
        "..o3rggrrxxq2o..",
        "..o3qnnnnnnq2o..",
        "..o3rgrgrrxq2o..",
        "..o3qnnnnnnq2o..",
        "..o3rgggrrgq2o..",
        "..o3qnnnnnnq2o..",
        "..o3rrrrrqqp2o..",
        "..o2222222221o..",
        "..o1111111111o..",
        "...oooooooooo...",
    ], L)


def sealed_cargo_crate():
    L = {'o': SPRUCE[0], '1': SPRUCE[1], '2': SPRUCE[2], '3': SPRUCE[3], '4': SPRUCE[4], '5': SPRUCE[5],
         'b': BRASS[1], 'c': BRASS[2], 'd': BRASS[3], 'e': BRASS[4], 'f': BRASS[5],
         'q': PAPER[1], 'r': PAPER[2],
         'R': GLOW['red'][0], 'S': GLOW['red'][1], 'T': GLOW['red'][2], 'U': GLOW['red'][3]}
    return item([
        "................",
        ".oooooooooooooo.",
        ".ofe5555rq555eo.",
        ".oe44444rq4444o.",
        ".o333333rq3333o.",
        ".o222222rq2222o.",
        ".orrrrrSSSrrrro.",
        ".oqqqqSUTSqqqqo.",
        ".o5555STTR5555o.",
        ".o44444SRRq444o.",
        ".o333333rq3333o.",
        ".o222222rq2222o.",
        ".od11111rq111co.",
        ".occ1111rq11cbo.",
        ".oooooooooooooo.",
        "................",
    ], L)


def survey_note():
    L = {'o': PAPER[0], 'n': PAPER[1], 'p': PAPER[2], 'q': PAPER[3], 'r': PAPER[4],
         'g': FELT[3], 'G': FELT[4], 'b': '#4a86b8', 'B': '#79acd6',
         'x': GLOW['red'][2], 'y': GLOW['red'][1]}
    return item([
        "................",
        "..oooooooooooo..",
        ".orrrrrrqrrrrro.",
        ".orGGrrrqrrBBro.",
        ".orGggrrqrbBbro.",
        ".orrgryrqrrbrro.",
        ".orrrrryqrrrrro.",
        ".orrrrrrqyrrGro.",
        ".orBBrrrqryGgro.",
        ".obBbrrrqrxgroo.",
        ".orbrrrxqxrrpo..",
        ".orrrrrrxrrnno..",
        ".oqqqqqxqxqno...",
        ".oppppppqppo....",
        "..ooooooooo.....",
        "................",
    ], L)


TEXTURES = {
    'block/machine_casing': machine_casing,
    'block/machine_top': machine_top,
    'block/teal_casing': teal_casing,
    'block/indicator_normal': lambda: indicator('green'),
    'block/indicator_warning': lambda: indicator('yellow'),
    'block/indicator_risk': lambda: indicator('red'),
    'block/indicator_offline': lambda: indicator('gray'),
    'block/indicator_full': lambda: indicator('orange'),
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
        fn().save(os.path.join(out, name + '.png'))
    print(f'wrote {len(TEXTURES)} textures to {out}')


if __name__ == '__main__':
    main()
