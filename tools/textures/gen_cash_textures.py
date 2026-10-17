"""Textures for the physical currency items: penny, ten pence, one pound note.

Usage: python tools/textures/gen_cash_textures.py [output_assets_textures_dir]
Reuses the palette and Tex helper of gen_textures.py, but only writes the cash items,
so it never touches the other textures.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_textures import Tex  # noqa: E402


COIN = [
    "................",
    "................",
    ".....aaaaaa.....",
    "...aabddddcba...",
    "...abdeeeeddba..",
    "..abdeddddddcba.",
    "..adedcccccdcba.",
    "..adedcbbcbdcba.",
    "..adedcbccbdcba.",
    "..adedccbbcdcba.",
    "..abddcccccccba.",
    "...abdcccccbba..",
    "...aabbbbbbba...",
    ".....aaaaaa.....",
    "................",
    "................",
]


def coin(colors):
    """Round coin with a raised rim and an embossed face; colors maps a..e (dark to light)."""
    return Tex().grid(0, 0, [''.join(colors.get(ch, ch) for ch in row) for row in COIN])


def banknote(fill, dark, light, edge, seal):
    t = Tex().grid(0, 0, [
        "................",
        "................",
        "................",
        ".00000000000000.",
        ".0LLLLLLLLLLLL0.",
        ".0LMMMMMMMMMML0.",
        ".0LMMMMMMMMMML0.",
        ".0LMMMMMMMMMML0.",
        ".0LMMMMMMMMMML0.",
        ".0LMMMMMMMMMML0.",
        ".0LMMMMMMMMMML0.",
        ".0LLLLLLLLLLLL0.",
        ".00000000000000.",
        "................",
        "................",
        "................",
    ])
    for y in range(16):
        for x in range(16):
            c = t.g[y][x]
            if c == 'L':
                t.g[y][x] = dark
            elif c == 'M':
                t.g[y][x] = fill
    t.g[3] = list(".0" + edge * 12 + "0.")  # top highlight edge
    # portrait oval on the left, value stripes on the right
    t.grid(3, 5, [".ss.", "sqqs", "sqqs", ".ss."])
    t.rect(9, 6, 12, 6, light).rect(9, 8, 11, 8, light)
    t.grid(10, 9, [seal * 2, seal * 2])
    return t


TEXTURES = {
    # copper penny
    'item/penny': lambda: coin({'a': 'i', 'b': 'A', 'c': 'B', 'd': 'C', 'e': 'O'}),
    # silver ten pence
    'item/ten_pence': lambda: coin({'a': '2', 'b': 'S', 'c': 'T', 'd': 'U', 'e': 'W'}),
    # green one pound note
    'item/pound_note': lambda: banknote('w', 'u', 'x', 'x', 'd'),
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
