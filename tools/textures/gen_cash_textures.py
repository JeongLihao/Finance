"""Physical currency textures (penny, ten pence, one pound note), vanilla 16px item style.

Usage: python tools/textures/gen_cash_textures.py [output_assets_textures_dir]
Coins are drawn procedurally: round disc, raised rim, light from the top-left,
embossed value, outline in a dark shade of the metal. The note is a tilted sheet
in green ink, the way vanilla paper and maps lie in the inventory.
"""
import os
import sys

from PIL import Image


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


# dark -> light ramps
COPPER = ['#3d1a10', '#6d3421', '#8a4129', '#a8502f', '#c15a36', '#e77c56', '#fc9982', '#fbc3b6']
SILVER = ['#2a2d33', '#4c525b', '#6c747f', '#8d96a2', '#aab3bf', '#c6cdd8', '#e1e6ed', '#ffffff']

DIGITS = {
    '1': [".X.", "XX.", ".X.", ".X.", "XXX"],
    '0': ["XXX", "X.X", "X.X", "X.X", "XXX"],
    'i': [".X", "XX", ".X", ".X", ".X"],  # narrow 1 for two-digit values
}


# Row widths of hand-tuned pixel circles (vanilla-style round shapes, no jaggies).
DISC_10 = [4, 6, 8, 10, 10, 10, 10, 8, 6, 4]
DISC_12 = [4, 8, 10, 10, 12, 12, 12, 12, 10, 10, 8, 4]


def disc(widths):
    top = (16 - len(widths)) // 2
    cells = set()
    for i, w in enumerate(widths):
        x0 = (16 - w) // 2
        for x in range(x0, x0 + w):
            cells.add((x, top + i))
    return cells


def coin(ramp, widths, glyph, glyph_y):
    cells = disc(widths)
    n = len(widths)
    c = 7.5
    px = {}

    def edge(p, s):
        return any((p[0] + ox, p[1] + oy) not in s for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    outline = {p for p in cells if edge(p, cells)}
    inner = cells - outline
    rim = {p for p in inner if edge(p, inner)}
    face = inner - rim
    for (x, y) in cells:
        light = -((x - c) + (y - c)) / n  # >0 toward the top-left
        if (x, y) in outline:
            v = 1 if light > 0.05 else 0
        elif (x, y) in rim:
            v = 5 if light > 0.15 else 4 if light > -0.15 else 3
            if light > 0.45:
                v = 6
        else:
            v = 4 if light < -0.2 else 3 if light < 0.25 else 3
        px[(x, y)] = v
    # a soft highlight on the upper-left of the face
    for (x, y) in face:
        if (x - c) + (y - c) < -4:
            px[(x, y)] = 4
    w = sum(len(DIGITS[ch][0]) for ch in glyph) + len(glyph) - 1
    gx = 8 - (w + 1) // 2
    for ch in glyph:
        rows = DIGITS[ch]
        for yy, row in enumerate(rows):
            for xx, cc in enumerate(row):
                if cc == 'X' and (gx + xx + 1, glyph_y + yy + 1) in face:
                    px[(gx + xx + 1, glyph_y + yy + 1)] = 2
        for yy, row in enumerate(rows):
            for xx, cc in enumerate(row):
                if cc == 'X':
                    px[(gx + xx, glyph_y + yy)] = 6
        gx += len(rows[0]) + 1
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for (x, y), v in px.items():
        im.putpixel((x, y), rgb(ramp[v]))
    return im


# Tilted note, laid out in its own (u along the long side, v across) coordinates.
INK = ['#1c3521', '#2c5233', '#467a49', '#6f9f66', '#a9c993', '#cfe3b8', '#e9f3dc', '#c9a23a', '#8a6a1c']
NOTE_CORNERS = ((0.6, 9.0), (10.0, 2.4), (15.4, 8.4), (6.0, 15.0))  # left, top, right, bottom


def note():
    (ax, ay), (bx, by), _, (dx, dy) = NOTE_CORNERS
    ux, uy = bx - ax, by - ay          # long side
    vx, vy = dx - ax, dy - ay          # short side
    det = ux * vy - uy * vx
    cells = {}
    for y in range(16):
        for x in range(16):
            px_, py_ = x + 0.5 - ax, y + 0.5 - ay
            u = (px_ * vy - py_ * vx) / det
            v = (ux * py_ - uy * px_) / det
            if 0 <= u <= 1 and 0 <= v <= 1:
                cells[(x, y)] = (u, v)
    col = {}
    for (x, y), (u, v) in cells.items():
        c = 5 if 0.2 < v < 0.8 else 4                  # paper, darker toward the long edges
        if u < 0.09 or u > 0.91 or v < 0.2 or v > 0.8:
            c = 3                                      # printed frame
        if ((u - 0.27) / 0.15) ** 2 + ((v - 0.5) / 0.32) ** 2 < 1:
            c = 6                                      # portrait cartouche
            if ((u - 0.27) / 0.08) ** 2 + ((v - 0.5) / 0.17) ** 2 < 1:
                c = 2                                  # portrait
        if 0.52 < u < 0.7 and abs(v - 0.5) < 0.09:
            c = 2                                      # value line
        if ((u - 0.79) / 0.09) ** 2 + ((v - 0.5) / 0.16) ** 2 < 1:
            c = 7                                      # gold seal
        col[(x, y)] = c
    for (x, y) in cells:
        if any((x + ox, y + oy) not in cells for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            col[(x, y)] = 1 if (x - 8) + (y - 8) < 0 else 0
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for p, c in col.items():
        im.putpixel(p, rgb(INK[c]))
    return im


TEXTURES = {
    'item/penny': lambda: coin(COPPER, DISC_10, '1', 5),
    'item/ten_pence': lambda: coin(SILVER, DISC_12, 'i0', 5),
    'item/pound_note': note,
}


def main():
    root = os.path.join(os.path.dirname(__file__), '..', '..')
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'src/main/resources/assets/finance/textures')
    for name, fn in TEXTURES.items():
        path = os.path.join(out, name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
    print(f'wrote {len(TEXTURES)} textures to {out}')


if __name__ == '__main__':
    main()
