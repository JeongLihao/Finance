"""Render the Finance block models to a PNG contact sheet, for checking shapes without the game.

Usage: python tools/textures/preview_models.py <vanilla_textures_root> <out.png> [model ...]
<vanilla_textures_root> is a folder holding assets/minecraft/textures/block (extracted from a
client jar). Models are read from src/main/resources/assets/finance/models/block.

Each texel of every face is drawn as a projected quad, painter-sorted, with vanilla's
per-direction face shading. Element rotations are honoured; uv rotation is not.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/finance')
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}
NORMAL = {'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1),
          'east': (1, 0, 0), 'west': (-1, 0, 0)}


def load_model(name):
    with open(os.path.join(ASSETS, 'models/block', name + '.json'), encoding='utf-8') as f:
        m = json.load(f)
    textures, elements = {}, None
    parent = m.get('parent', '')
    if parent.startswith('finance:block/'):
        textures, elements = load_model(parent.split('/')[-1])
    textures = dict(textures)
    textures.update(m.get('textures', {}))
    return textures, m.get('elements', elements) or []


def resolve(textures, ref):
    while ref.startswith('#'):
        ref = textures[ref[1:]]
    return ref


_cache = {}


def texture(ref, vanilla):
    if ref not in _cache:
        ns, path = ref.split(':')
        base = vanilla if ns == 'minecraft' else os.path.join(ROOT, 'src/main/resources')
        im = Image.open(os.path.join(base, 'assets', ns, 'textures', path + '.png')).convert('RGBA')
        _cache[ref] = im.crop((0, 0, im.width, im.width))  # animated strips: first frame
    return _cache[ref]


def face_point(face, f, t, a, b):
    """Point on a face for fractions a (along u) and b (along v), matching vanilla default uv."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    lerp = lambda p, q, s: p + (q - p) * s
    return {
        'north': (lerp(x2, x1, a), lerp(y2, y1, b), z1),
        'south': (lerp(x1, x2, a), lerp(y2, y1, b), z2),
        'west': (x1, lerp(y2, y1, b), lerp(z1, z2, a)),
        'east': (x2, lerp(y2, y1, b), lerp(z2, z1, a)),
        'up': (lerp(x1, x2, a), y2, lerp(z1, z2, b)),
        'down': (lerp(x1, x2, a), y1, lerp(z2, z1, b)),
    }[face]


def rotate_point(p, rot):
    if not rot:
        return p
    o = rot['origin']
    ang = math.radians(rot['angle'])
    c, s = math.cos(ang), math.sin(ang)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if rot['axis'] == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif rot['axis'] == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def render(name, vanilla, yaw, size=320):
    textures, elements = load_model(name)
    scale = size / 34
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(30)), math.sin(math.radians(30))

    def project(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp
        return (size / 2 - x * scale, size * 0.55 - y * scale, z)

    polys = []
    for e in elements:
        f, t, rot = e['from'], e['to'], e.get('rotation')
        for face, spec in e['faces'].items():
            n = rotate_point(NORMAL[face], {'origin': [0, 0, 0], 'axis': rot['axis'], 'angle': rot['angle']}) if rot else NORMAL[face]
            nx, ny, nz = n
            nx, nz = nx * cy - nz * sy, nx * sy + nz * cy
            ny, nz = ny * cp + nz * sp, -ny * sp + nz * cp
            if nz > 0.01:
                continue  # facing away from the camera
            img = texture(resolve(textures, spec['texture']), vanilla)
            k = img.width / 16
            u1, v1, u2, v2 = spec['uv']
            nu = max(1, round(abs(u2 - u1) * k))
            nv = max(1, round(abs(v2 - v1) * k))
            shade = SHADE[face] if not rot else 0.9
            for i in range(nu):
                for j in range(nv):
                    su = u1 + (u2 - u1) * (i + 0.5) / nu
                    sv = v1 + (v2 - v1) * (j + 0.5) / nv
                    r, g, b, a = img.getpixel((min(img.width - 1, int(su * k)), min(img.width - 1, int(sv * k))))
                    if a < 128:
                        continue
                    corners = [face_point(face, f, t, (i + di) / nu, (j + dj) / nv)
                               for di, dj in ((0, 0), (1, 0), (1, 1), (0, 1))]
                    pts = [project(rotate_point(c, rot)) for c in corners]
                    depth = sum(p[2] for p in pts) / 4
                    polys.append((depth, [(p[0], p[1]) for p in pts],
                                  (int(r * shade), int(g * shade), int(b * shade))))
    polys.sort(key=lambda q: -q[0])
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _, pts, col in polys:
        d.polygon(pts, fill=col, outline=col)
    return im


def main():
    vanilla, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or sorted(n[:-5] for n in os.listdir(os.path.join(ASSETS, 'models/block')))
    cell = 320
    sheet = Image.new('RGBA', (cell * 2 * min(3, len(names)), cell * ((len(names) + 2) // 3)), (124, 160, 108, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        x, y = (i % 3) * cell * 2, (i // 3) * cell
        for k, yaw in enumerate((35, -35)):
            im = render(name, vanilla, yaw)
            sheet.alpha_composite(im, (x + k * cell, y))
        d.text((x + 8, y + 6), name, fill=(20, 20, 20))
    sheet.save(out)


if __name__ == '__main__':
    main()
