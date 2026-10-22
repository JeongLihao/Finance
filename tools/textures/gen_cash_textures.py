"""Physical currency textures, hand-drawn pixel grids in vanilla 16px item style.

Usage: python tools/textures/gen_cash_textures.py [output_assets_textures_dir]

- penny / ten_pence: coins lying flat in a small stack (copper 2 high, silver 3 high);
  told apart by colour, no face value.
- pound_note: green one-pound note.
- blank_exchange_note / exchange_note: white Victorian-style note, empty or written on
  (ink lines and a red wax seal).
"""
import os
import sys

from PIL import Image

COPPER = ['#3b1a0f', '#5e2a17', '#86391f', '#a84b28', '#c9663a', '#e48a57', '#f6b383', '#ffe0c4']
SILVER = ['#25282e', '#3f444d', '#5d646f', '#7d8592', '#9ea7b3', '#bfc7d1', '#dde3ea', '#ffffff']

# One coin lying down, 3/4 view. Digits index the metal ramp, o is the outline.
COIN = [
    "...oooooo...",
    ".oo776655oo.",
    "o5766555443o",
    "o4555554433o",
    "oo44444333oo",
    "o3232323221o",
    ".oo222211oo.",
    "...oooooo...",
]

GREEN_NOTE = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    "oPPPPPPPPPPPPPPo",
    "oPDMDMDMDMDMDMQo",
    "oPMLLLLPPPGGPDQo",
    "oPDLDDLPMPGgPMQo",
    "oPMLDDLPPPPPPDQo",
    "oPDLLLLPMMMMPMQo",
    "oPMDMDMDMDMDMDQo",
    "oQQQQQQQQQQQQQQo",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]
GREEN = {'o': '#21402a', 'D': '#36633d', 'M': '#5e9158', 'L': '#b5d6a0', 'P': '#e6f1d6', 'Q': '#c3d9ad',
         'G': '#e1bb4b', 'g': '#9a7420'}

WHITE_NOTE = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    "oWWWWWWWWWWWWWWo",
    "oWkkkkWWWWWWWWGo",
    "oWWWWWWWkWkWWWGo",
    "oWkkkWWkWkWkWWWo",
    "oWWWWWWWWWWWWWWo",
    "oWkkkkkWWkkkkWWo",
    "oWWWWWWWWWWWWRWo",
    "oSSSSSSSSSSSRrSo",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]
# blank: printed heading and gold corner only, empty lines to write on, no seal
BLANK_NOTE = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    "oWWWWWWWWWWWWWWo",
    "oWkkkkWWWWWWWWGo",
    "oWWWWWWWWWWWWWGo",
    "oWSSSSSSSSSSSWWo",
    "oWWWWWWWWWWWWWWo",
    "oWSSSSSSSSSSSWWo",
    "oWWWWWWWWWWWWWWo",
    "oSSSSSSSSSSSSSSo",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]
WHITE = {'o': '#4a4438', 'W': '#f5f1e6', 'S': '#ddd6c4', 'k': '#2b2b2b', 'G': '#c9a23a', 'R': '#b0302a',
         'r': '#6e1a16'}


def rgb(h):
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


def blank():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def coin_stack(ramp, offsets):
    """Draw one coin per offset, bottom first, so upper coins cover the faces below."""
    im = blank()
    for ox, oy in offsets:
        for y, row in enumerate(COIN):
            for x, ch in enumerate(row):
                if ch != '.':
                    im.putpixel((ox + x, oy + y), rgb(ramp[0] if ch == 'o' else ramp[int(ch)]))
    return im


def grid(rows, colors):
    im = blank()
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), rgb(colors[ch]))
    return im


TEXTURES = {
    'item/penny': lambda: coin_stack(COPPER, [(2, 7), (3, 4)]),
    'item/ten_pence': lambda: coin_stack(SILVER, [(3, 8), (2, 5), (3, 2)]),
    'item/pound_note': lambda: grid(GREEN_NOTE, GREEN),
    'item/blank_exchange_note': lambda: grid(BLANK_NOTE, WHITE),
    'item/exchange_note': lambda: grid(WHITE_NOTE, WHITE),
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
