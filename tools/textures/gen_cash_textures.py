"""Textures for the physical currency items (coin, 10 and 100 banknotes).

Usage: python tools/textures/gen_cash_textures.py [output_assets_textures_dir]
Reuses the palette and Tex helper of gen_textures.py, but only writes the cash items,
so it never touches the other textures.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_textures import Tex  # noqa: E402


def coin():
    # brass coin with a raised rim and a square hole, like an old copper cash coin
    return Tex().grid(0, 0, [
        "................",
        "................",
        ".....aaaaaa.....",
        "...aabddddcba...",
        "...abdeeeeddba..",
        "..abdeddddcdcba.",
        "..adedcaaccdcba.",
        "..adedca..cdcba.",
        "..adedca..cdcba.",
        "..adedcaaccdcba.",
        "..abddcccccccba.",
        "...abdcccccbba..",
        "...aabbbbbbba...",
        ".....aaaaaa.....",
        "................",
        "................",
    ])


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
    'item/coin': coin,
    'item/banknote_10': lambda: banknote('w', 'u', 'x', 'x', 'd'),
    'item/banknote_100': lambda: banknote('c', 'b', 'e', 'd', 'o'),
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
