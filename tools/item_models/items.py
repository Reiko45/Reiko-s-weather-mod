"""3D item models for Reiko's Weather.

    python3 tools/item_models/items.py                 # regenerate every model
    python3 tools/item_models/items.py barometer tent  # just these
    python3 tools/item_models/items.py --sheet out.png # also render an inventory-style preview sheet

Each function below builds one item. Models are written to models/item/<name>.json with their
texture in textures/item/model/<name>.png. The old flat sprites live in sprites/ and some models
use them as the art for their faces.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from mclib import (Model, FLAT, HELD, TOOL, T, art, grid, solid, stripes, wood, metal, speckle, rgb, scale, mix,  # noqa: E402
                   sheet)

HERE = os.path.dirname(__file__)
REG = {}


def item(fn):
    REG[fn.__name__] = fn
    return fn


# ---------------------------------------------------------------- helpers

def sprite(name):
    return np.array(Image.open(os.path.join(HERE, 'sprites', name + '.png')).convert('RGBA'))


def disc(d, inner=0):
    """Pixel circle (diameter d) as a mask; with `inner`, a ring."""
    m = np.zeros((d, d), bool)
    c = d / 2
    for y in range(d):
        for x in range(d):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c)
            m[y, x] = r <= c and (inner == 0 or r > inner / 2)
    return m


def paint(mask, painter, rng, face='up'):
    """Fill a mask with a painter's colours -> RGBA image."""
    h, w = mask.shape
    img = painter(w, h, face, rng).copy()
    img[~mask] = 0
    return img


def dist_map(alpha):
    """Pixels from the nearest empty pixel (4-neighbour steps), for 'inflating' sprites."""
    h, w = alpha.shape
    d = np.where(alpha, 99, 0)
    for _ in range(10):
        p = np.pad(d, 1, constant_values=0)
        n = np.minimum.reduce([p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]]) + 1
        d = np.where(alpha, np.minimum(d, n), 0)
    return d


def inflate(m, img, zc=8.0, base=0.5, step=0.5, cap=2.0, x0=0, ytop=16, extra=None, **kw):
    """Extrude a sprite with thickness that grows toward its middle, so it looks rounded."""
    a = img[..., 3] > 0
    d = dist_map(a)
    half = np.clip(base + (d - 1) * step, base, cap)
    if extra is not None:
        half = half + extra
    m.extrude(img, x0, ytop, zc - half, zc + half, **kw)


def colour_mask(img, colours):
    cs = [rgb(c)[:3] for c in colours]
    out = np.zeros(img.shape[:2], bool)
    for c in cs:
        out |= (img[..., :3] == c).all(-1) & (img[..., 3] > 0)
    return out


def column(cx, cz, y1, y2, d, top, side, m, inner=0, back=None, light=0):
    """A round post / disc / ring standing on the y axis, built from a pixel circle."""
    mask = disc(d, inner)
    img = paint(mask, top, m.rng)
    m.extrude(img, cx - d / 2, cz - d / 2, y1, y2, axis='y', side=side, back=back or 'mirror', light=light)


def rot(axis, angle, origin):
    return {'origin': list(origin), 'axis': axis, 'angle': angle}


def P(rows, pal):
    return grid(rows, pal)


# ---------------------------------------------------------------- instruments

@item
def barometer(m):
    img = sprite('barometer')
    rim = colour_mask(img, ['#4a2616', '#e59160', '#b8653a'])
    needle = colour_mask(img, ['#b02626'])
    z2 = np.where(rim, 9.75, np.where(needle, 9.25, 9.0))
    m.extrude(img, 0, 16, 6.25, z2, back=metal('#b8653a', 0.2))
    # hanging ring
    m.box([7, 15.5, 7.5], [9, 16.5, 8.5], metal('#e59160'))
    m.box([7.5, 16.5, 7.5], [8.5, 17.25, 8.5], metal('#b8653a'))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def thermometer(m):
    oak = '#9b6a3f'
    board = art([
        'kwWwWk',
        'wWwwWw',
        'wwWwmW',
        'WwwWww',
        'wwWwmw',
        'wWwwwW',
        'wwWwmw',
        'WwwWww',
        'wwWwmW',
        'wWwwww',
        'wwWwmw',
        'WwWwww',
        'wwwWww',
        'wWwwWw',
    ], {'w': oak, 'W': scale(oak, 0.88), 'k': '#3a2414', 'm': '#2a1a10'})
    m.box([5, 1, 7], [11, 15, 8], wood(oak), faces={'south': board})
    glass = art(['g', 'G', 'g', 'g', 'g', 'r', 'R', 'r', 'r', 'R'], {'g': '#e8f2f6', 'G': '#ffffff', 'r': '#d2242a', 'R': '#b81c22'})
    m.box([7.5, 4, 8], [8.5, 14, 9], solid('#dfe9ee'), faces={'south': glass, 'east': glass, 'west': glass})
    m.box([7, 2, 7.75], [9, 4, 9.25], solid('#d2242a', bevel=0.25))
    m.box([7.25, 14, 7.75], [8.75, 14.75, 9.25], metal('#d8a23a'))
    m.box([7.25, 11, 8], [8.75, 11.5, 9.25], metal('#d8a23a'))
    m.box([7.25, 5.5, 8], [8.75, 6, 9.25], metal('#d8a23a'))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def handheld_radar(m):
    body = '#8d939a'
    front = art([
        'lllllllll',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'ldddddddl',
        'lllllllll',
        'lllllllll',
        'lllllllll',
        'lkkkkkkkl',
    ], {'l': body, 'd': '#30343a', 'k': '#4a4f56'})
    m.box([3.5, 1, 6.5], [12.5, 14, 9.5], solid(body, bevel=0.15), faces={'south': front})
    screen = art([
        'dgdgggd',
        'gddrdbg',
        'gdryrdg',
        'gdbbddg',
        'gdbddgg',
        'gddgddg',
        'dgggggd',
    ], {'d': '#0d2e16', 'g': '#1f6e30', 'b': '#7dff8a', 'r': '#ff5040', 'y': '#ffd640'})
    m.box([4.5, 6, 9.5], [11.5, 13, 9.75], solid('#0d2e16'), faces={'south': screen}, light=15)
    for x, c in ((4.5, '#d83a32'), (6.5, '#e8c23a'), (8.5, '#3a7ad8')):
        m.box([x, 3, 9.5], [x + 1, 4, 10], solid(c, bevel=0.2))
    m.box([10.5, 3, 9.5], [11.5, 4, 10], solid('#d8dadc'))
    m.box([10.5, 14, 7.5], [11.5, 17, 8.5], metal('#3a3e44'))
    m.box([10.25, 17, 7.25], [11.75, 18, 8.75], solid('#d83a32', bevel=0.2))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def weather_radio(m):
    red = '#c23a30'
    front = art([
        'rrrrrrrrrrrrrr',
        'rkkkkkkrcccccr',
        'rkgkgkkrcmcmcr',
        'rkkgkgkrcccNcr',
        'rkgkgkkrccNccr',
        'rkkgkgkrrrrrrr',
        'rkgkgkkrrrrrrr',
        'rkkkkkkrrrrrrr',
        'rrrrrrrrrrrrrr',
    ], {'r': red, 'k': '#2a2422', 'g': '#5a524c', 'c': '#efe4c4', 'm': '#8a7a5a', 'N': '#d82a20'})
    m.box([1, 2, 5], [15, 11, 11], solid(red, bevel=0.12), faces={'south': front})
    m.box([1.5, 1, 6], [3.5, 2, 10], solid('#2a2422'))
    m.box([12.5, 1, 6], [14.5, 2, 10], solid('#2a2422'))
    for x in (9.5, 12):
        m.box([x, 3, 11], [x + 1.5, 4.5, 12], solid('#2a2422'), faces={'south': art(['kwk', 'kkk'], {'k': '#2a2422', 'w': '#e8e8e8'})})
    m.box([3, 11, 7.5], [4, 13, 8.5], metal('#3a3a3e'))
    m.box([12, 11, 7.5], [13, 13, 8.5], metal('#3a3a3e'))
    m.box([3, 13, 7.5], [13, 14, 8.5], metal('#2e2e32'))
    m.box([13.5, 11, 9], [14, 19, 9.5], metal('#d0d4da'), rot=rot('z', -22.5, [13.75, 11, 9.25]))
    m.display = HELD
    m.gui_rot = (25, -35, 0)


@item
def weather_almanac(m):
    blue, dark = '#2f56a8', '#1d3878'
    cover = art([
        'gddddddddddg',
        'dccccccccccd',
        'dccccyccccc d'.replace(' ', ''),
        'dccyccyccycd',
        'dcccyyyyyccd',
        'dcyyyYYyyycd',
        'dccyyYYyyccd',
        'dcccwwwyyycd',
        'dccwwwwwwccd',
        'dcwwwWwwwwcd',
        'dccccccccccd',
        'dcgggggggccd',
        'dccccccccccd',
        'gddddddddddg',
    ], {'c': blue, 'd': dark, 'g': '#e3b23c', 'y': '#f2c94a', 'Y': '#ffe58a', 'w': '#f4f4f4', 'W': '#cfd8e4'})
    m.box([2, 1, 9.25], [14, 15, 10], solid(blue), faces={'south': cover})
    m.box([2, 1, 6], [14, 15, 6.75], solid(blue))
    m.box([1.25, 1, 6], [2, 15, 10], solid(dark), faces={'west': stripes(dark, '#e3b23c', 3, 'v')})
    pages = '#f1e6c8'
    m.box([2, 1.5, 6.75], [13.5, 14.5, 9.25], solid(pages),
          faces={'east': stripes(pages, '#d6c7a2', 1, 'u'), 'up': stripes(pages, '#d6c7a2', 1, 'v'),
                 'down': stripes(pages, '#d6c7a2', 1, 'v'), 'south': None, 'north': None})
    m.box([9, 0, 9.3], [10, 1.5, 9.6], solid('#c8302a'))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def rake(m):
    r = rot('z', 45, [8, 8, 8])
    m.box([-1.5, 7.5, 7.5], [12, 8.5, 8.5], wood('#9a6a3e'), rot=r)
    m.box([11, 7.25, 7.25], [12.5, 8.75, 8.75], metal('#6a6e74'), rot=r)
    m.box([12.5, 3, 7.25], [13.5, 13, 8.75], metal('#4e5258'), rot=r)
    for y in (3.25, 5.5, 7.5, 9.5, 11.75):
        m.box([13.5, y, 7.5], [16, y + 1, 8.5], metal('#5e6268'), rot=r)
    m.display = TOOL
    m.gui_rot = (10, -20, 0)


@item
def stormfish(m):
    img = sprite('stormfish')
    inflate(m, img, cap=1.75)
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def hot_cocoa(m):
    mug = '#c43a32'
    side = stripes(mug, '#f2ece2', 1, 'v')

    def mug_side(w, h, face, rng):
        a = solid(mug, 0.04)(w, h, face, rng).astype(float)
        if h >= 6:
            a[2] = rgb('#f2ece2')
            a[3] = rgb('#f2ece2')
        return a.astype(np.uint8)
    column(8, 8, 1, 9, 8, solid(mug), mug_side, m, inner=6)
    column(8, 8, 1, 2, 6, solid(scale(mug, 0.8)), solid(mug), m)
    cocoa = solid('#5a3320', 0.08)
    column(8, 8, 2, 7.5, 6, cocoa, cocoa, m)
    m.box([6, 7.5, 6.75], [7.25, 8.4, 8], solid('#fbf6f0', bevel=0.2))
    m.box([8.25, 7.5, 8.5], [9.5, 8.25, 9.75], solid('#f8e4ea', bevel=0.2))
    m.box([7.25, 7.5, 5.5], [8.25, 8.1, 6.5], solid('#fbf6f0', bevel=0.2))
    m.box([12, 3, 7.5], [13.5, 4, 8.5], solid(mug))
    m.box([12, 7, 7.5], [13.5, 8, 8.5], solid(mug))
    m.box([13.5, 3, 7.5], [14.5, 8, 8.5], solid(mug))
    m.display = HELD
    m.gui_rot = (30, -35, 0)



# ---------------------------------------------------------------- more helpers

def weave(c1, c2):
    a, b = rgb(c1), rgb(c2)

    def paint(w, h, face, rng):
        arr = np.zeros((h, w, 4), np.uint8)
        for y in range(h):
            for x in range(w):
                arr[y, x] = a if (x + y) % 2 == 0 else b
        return arr
    return paint


def blobs(w, h, rng, cover=0.45, n=None):
    """A blotchy mask, for lichen crusts and glowing fungus."""
    n = n or max(3, w * h // 12)
    f = np.zeros((h, w))
    for _ in range(n):
        cx, cy, r = rng.random() * w, rng.random() * h, 0.8 + rng.random() * 1.8
        Y, X = np.mgrid[0:h, 0:w]
        f += np.exp(-((X + 0.5 - cx) ** 2 + (Y + 0.5 - cy) ** 2) / (r * r))
    f += np.array([[rng.random() * 0.35 for _ in range(w)] for _ in range(h)])
    t = np.quantile(f, 1 - cover)
    return f > t


def colour_blobs(mask, colours, rng):
    h, w = mask.shape
    img = np.zeros((h, w, 4), np.uint8)
    cs = [rgb(c) for c in colours]
    for y in range(h):
        for x in range(w):
            if mask[y, x]:
                img[y, x] = cs[int(rng.random() * len(cs))]
    return img


def sector_disc(d, c1, c2, edge=None):
    """Top-down umbrella canopy: eight panels split along the axes and diagonals, alternating colours."""
    img = np.zeros((d, d, 4), np.uint8)
    m = disc(d)
    c = d / 2
    for y in range(d):
        for x in range(d):
            if m[y, x]:
                ang = math.atan2(y + 0.5 - c, x + 0.5 - c)
                sct = int((ang + math.pi) / (math.pi / 4)) % 8
                img[y, x] = rgb(c1 if sct % 2 == 0 else c2)
    return img


def head(m, cx, cz, y1, y2, img, side, light=0):
    """A small top-down piece of art (flower heads etc.) centred on cx, cz."""
    h, w = img.shape[:2]
    m.extrude(img, cx - w / 2, cz - h / 2, y1, y2, axis='y', side=side, light=light)


# ---------------------------------------------------------------- more instruments and gear

@item
def storm_data(m):
    m.box([2.5, 1, 7], [13.5, 15, 7.75], wood('#a06a3a', along='v'))
    paper = art([
        'wwwwwwwww',
        'wwwwwwwww',
        'wkkkkkkww',
        'wwwwwwwww',
        'wlwwwwwrw',
        'wlwwwwrbw',
        'wlwrwwbww',
        'wlrbrbwww',
        'wlbwbwwww',
        'wlllllllw',
        'wgggwgggw',
    ], {'w': '#f4f2ea', 'k': '#4a4a5a', 'l': '#3a3a44', 'r': '#d8302a', 'b': '#3a6ad8', 'g': '#a8a8b0'})
    m.box([3.5, 1.75, 7.75], [12.5, 12.75, 8], solid('#f4f2ea'), faces={'south': paper})
    m.box([6, 11.75, 7.75], [10, 13.75, 8.6], metal('#c8ccd2'))
    m.box([7, 13.75, 7.85], [9, 14.75, 8.35], metal('#9aa0a8'))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def cloud_seeding_rocket(m):
    white, blue = '#f2f2f0', '#3a78c8'
    column(8, 8, 2, 9, 4, solid(white), solid(white, 0.04), m)
    column(8, 8, 9, 10, 4, solid(blue), solid(blue), m)
    column(8, 8, 10, 12, 4, solid(white), solid(white), m)
    column(8, 8, 12, 13.5, 4, solid(blue), solid(blue), m)
    column(8, 8, 13.5, 14.75, 2, solid(blue), solid(scale(blue, 1.1)), m)
    m.box([7.5, 14.75, 7.5], [8.5, 15.5, 8.5], solid('#2a5aa0'))
    column(8, 8, 1, 2, 2, solid('#4a4a4e'), solid('#4a4a4e'), m)
    m.box([7.5, 0, 7.5], [8.5, 1, 8.5], solid('#ffb030'), light=15)
    fin = solid(blue, bevel=0.15)
    for a, b in (([10, 2, 7.75], [12, 4.5, 8.25]), ([10, 4.5, 7.75], [11, 6, 8.25]),
                 ([4, 2, 7.75], [6, 4.5, 8.25]), ([5, 4.5, 7.75], [6, 6, 8.25]),
                 ([7.75, 2, 10], [8.25, 4.5, 12]), ([7.75, 4.5, 10], [8.25, 6, 11]),
                 ([7.75, 2, 4], [8.25, 4.5, 6]), ([7.75, 4.5, 5], [8.25, 6, 6])):
        m.box(a, b, fin)
    m.box([7.5, 6.5, 9.9], [8.5, 7.5, 10.15], solid('#2a3a5a'))
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def umbrella_icon(m):
    red, white = '#c8282a', '#f2eeea'
    for y1, y2, d in ((9, 10, 14), (10, 11, 11), (11, 12, 7), (12, 12.5, 3)):
        img = sector_disc(d, red, white)
        m.extrude(img, 8 - d / 2, 8 - d / 2, y1, y2, axis='y')
    m.box([7.5, 12.5, 7.5], [8.5, 13.75, 8.5], solid('#3a2a20'))
    m.box([7.5, 1.5, 7.5], [8.5, 9, 8.5], solid('#3a2a20'))
    m.box([5.5, 0, 7.5], [8.5, 1.5, 8.5], wood('#7a4a2a'))
    m.box([5, 1.5, 7.5], [6.5, 3, 8.5], wood('#7a4a2a'))
    m.display = {'ground': T((0, 0, 0), (0, 2, 0), 0.5), 'fixed': T((0, 180, 0), (0, 0, 0), 0.8)}
    m.gui_rot = (25, -35, 0)


@item
def paraglider(m):
    red, white = '#d8322a', '#f4f2ee'
    wing = {'up': stripes(red, white, 2, 'u'), 'down': stripes(scale(red, 0.85), scale(white, 0.85), 2, 'u')}
    mat = solid(red)
    m.box([5, 13, 4], [11, 14.25, 12], mat, faces=wing)
    m.box([11, 13, 4], [14.5, 14.25, 12], mat, faces=wing, rot=rot('z', -22.5, [11, 14.25, 8]))
    m.box([14.234, 11.661, 4], [17.234, 12.911, 12], mat, faces=wing, rot=rot('z', -45, [14.234, 12.911, 8]))
    m.box([1.5, 13, 4], [5, 14.25, 12], mat, faces=wing, rot=rot('z', 22.5, [5, 14.25, 8]))
    m.box([-1.234, 11.661, 4], [1.766, 12.911, 12], mat, faces=wing, rot=rot('z', 45, [1.766, 12.911, 8]))
    line = solid('#3a3a40', 0)
    for z in (6, 9.8):
        m.box([6.4, 4, z], [6.6, 13, z + 0.2], line)
        m.box([9.4, 4, z], [9.6, 13, z + 0.2], line)
        m.box([-0.5, 4, z], [8, 4.2, z + 0.2], line, rot=rot('z', -45, [8, 4.1, z + 0.1]))
        m.box([8, 4, z], [16.5, 4.2, z + 0.2], line, rot=rot('z', 45, [8, 4.1, z + 0.1]))
    m.box([6.5, 1.5, 6.5], [9.5, 4, 9.5], solid('#3a3a44', bevel=0.2),
          faces={'south': art(['kkk', 'kyk', 'kkk'], {'k': '#3a3a44', 'y': '#e8c23a'})})
    m.display = HELD
    m.gui_rot = (25, -35, 0)


@item
def ice_saw(m):
    r = rot('z', 45, [8, 8, 8])
    m.box([-1.5, 6.5, 7], [3.5, 9.5, 9], wood('#8a5a32'), rot=r)
    m.box([-1.75, 6.25, 6.75], [-1, 9.75, 9.25], metal('#6a6e74'), rot=r)
    m.box([3.5, 6, 6.75], [4.5, 10, 9.25], metal('#7a7e84'), rot=r)
    blade = P([
        'SSSSSSSSSS..',
        'ssssssssssS.',
        'sssssssssssS',
        't.t.t.t.t.t.',
    ], {'s': '#b8c6d0', 'S': '#e4eef4', 't': '#8a96a0'})
    m.extrude(blade, 4.5, 10, 7.75, 8.25, rot=r)
    m.display = TOOL
    m.gui_rot = (10, -20, 0)


@item
def goggles(m):
    lens = P([
        '.kkkk.',
        'kllwlk',
        'klwllk',
        'kllllk',
        'kllllk',
        '.kkkk.',
    ], {'k': '#3a3c40', 'l': '#5fb0e6', 'w': '#e8f6ff'})
    frame = (lens[..., :3] == rgb('#3a3c40')[:3]).all(-1)
    z2 = np.where(frame, 11.0, 10.25)
    m.extrude(lens, 1.5, 11, 9, z2)
    m.extrude(lens, 8.5, 11, 9, z2)
    m.box([7.5, 8, 9.25], [8.5, 9, 10.25], metal('#3a3c40'))
    leather = solid('#6a4428', bevel=0.1)
    m.box([0.5, 7, 3], [1.5, 9, 10.5], leather)
    m.box([14.5, 7, 3], [15.5, 9, 10.5], leather)
    m.box([0.5, 7, 2], [15.5, 9, 3], leather)
    m.box([7, 6.75, 1.75], [9, 9.25, 2.25], metal('#c8ccd2'))
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def sun_hat(m):
    straw, straw2 = '#e2c06a', '#c9a24a'
    under = solid(scale(straw, 0.8))
    column(8, 8, 4, 5, 15, weave(straw, straw2), solid(straw2), m, back=under)
    column(8, 8, 5, 6, 8, weave(straw, straw2), weave(straw, straw2), m)
    column(8, 8, 6, 7.25, 8, solid('#c8302a'), solid('#c8302a'), m)
    column(8, 8, 7.25, 9.5, 8, weave(straw, straw2), weave(straw, straw2), m)
    column(8, 8, 9.5, 10, 6, weave(straw, straw2), solid(straw2), m)
    m.box([11.75, 6, 7], [12.75, 7.75, 9], solid('#b82a24', bevel=0.2))
    m.box([12, 4.5, 7.5], [12.5, 6, 8], solid('#b82a24'))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def winter_coat(m):
    blue, dark = '#3c5fa5', '#263e78'
    fur = speckle('#eee8d7', ['#f8f4ea', '#d8d0bc'], 0.35)
    front = art([
        'bbbdbbb',
        'bbbdbbb',
        'bbbdwbb',
        'bbbdbbb',
        'bkkdbbb',
        'bbbdwbb',
        'bbbdbbb',
        'bbbdbkk',
        'bbbdwbb',
        'bbbdbbb',
    ], {'b': blue, 'd': dark, 'w': '#eee8d7', 'k': '#2e4c8c'})
    m.box([4.5, 2, 6], [11.5, 12, 10], solid(blue, bevel=0.1), faces={'south': front})
    m.box([3, 10, 6.25], [13, 12, 9.75], solid(blue, bevel=0.1))
    m.box([1.75, 3.5, 6.5], [4.5, 11.5, 9.5], solid(scale(blue, 0.95), bevel=0.1))
    m.box([11.5, 3.5, 6.5], [14.25, 11.5, 9.5], solid(scale(blue, 0.95), bevel=0.1))
    m.box([1.5, 2.5, 6.25], [4.75, 3.5, 9.75], fur)
    m.box([11.25, 2.5, 6.25], [14.5, 3.5, 9.75], fur)
    m.box([4.25, 1.25, 5.75], [11.75, 2.25, 10.25], fur)
    m.box([4, 11.5, 5.5], [12, 13.25, 10.5], fur)
    m.box([5, 12, 4.25], [11, 15, 6.25], solid(blue, bevel=0.1))
    m.box([4.75, 14.25, 4], [11.25, 15.25, 6.5], fur)
    m.display = HELD
    m.gui_rot = (15, -30, 0)


@item
def earthworm(m):
    inflate(m, sprite('earthworm'), base=0.75, step=0.25, cap=1.0, ytop=19)
    m.display = FLAT
    m.gui_rot = (25, -30, 0)


@item
def salt(m):
    salt_c = speckle('#f4f6f8', ['#dfe6ee', '#ffffff', '#cfdbe6'], 0.4)
    for y1, y2, d in ((0, 1.5, 12), (1.5, 3, 9), (3, 4.5, 6), (4.5, 5.5, 3)):
        column(8, 8, y1, y2, d, salt_c, salt_c, m)
    for a in ([1.5, 0, 9], [12.5, 0, 5], [10, 0, 12.5]):
        m.box(a, [a[0] + 1, 1, a[2] + 1], solid('#eef4fa', bevel=0.3))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def sulfur(m):
    y1, y2, y3 = '#f2d23a', '#c9a518', '#fff27a'
    rock = speckle(y2, [y1, '#a88a14'], 0.35)
    crystal = {'up': solid(y3, bevel=0.2), 'south': solid(y1, bevel=0.2), 'north': solid(y1, bevel=0.2),
               'east': solid(y2, bevel=0.2), 'west': solid(y2, bevel=0.2), 'down': solid(y2)}
    m.box([3.5, 0, 4.5], [12.5, 3, 11.5], rock)
    m.box([5, 3, 6], [11, 4, 10], rock)
    m.box([6, 2, 6.5], [8.5, 11, 9], None, faces=crystal, rot=rot('z', 22.5, [7.25, 2, 7.75]))
    m.box([8.5, 2, 7], [10.5, 9, 9], None, faces=crystal, rot=rot('z', -22.5, [9.5, 2, 8]))
    m.box([7, 2, 8.5], [9, 7.5, 10.5], None, faces=crystal, rot=rot('x', 22.5, [8, 2, 9.5]))
    m.box([4.5, 2, 5], [6.5, 6, 7], None, faces=crystal, rot=rot('x', -22.5, [5.5, 2, 6]))
    m.box([10.5, 2, 5], [12, 5, 6.5], None, faces=crystal, rot=rot('z', -45, [11.25, 2, 5.75]))
    m.display = HELD
    m.gui_rot = (25, -35, 0)


# ---------------------------------------------------------------- food and drink

@item
def melon_cooler(m):
    glass, drink = '#d8eef2', '#f0647e'

    def glass_side(w, h, face, rng):
        a = np.zeros((h, w, 4), float)
        for y in range(h):
            for x in range(w):
                c = glass if y < 2 else mix(drink, glass, 0.3)
                a[y, x] = rgb(c)
                if x == 1 and w > 2:
                    a[y, x, :3] = np.minimum(255, a[y, x, :3] * 1.15)
        return a.astype(np.uint8)
    column(8, 8, 0, 1, 7, solid(glass), solid(scale(glass, 0.95)), m)
    column(8, 8, 1, 11, 7, solid(glass), glass_side, m, inner=5)
    column(8, 8, 1, 9.5, 5, speckle(drink, [scale(drink, 1.1), '#ff8aa0'], 0.25), solid(drink), m)
    m.box([6.25, 9, 6.75], [7.5, 10, 8], solid('#eaf8ff', bevel=0.3))
    m.box([8.25, 9, 7.75], [9.5, 9.9, 9], solid('#eaf8ff', bevel=0.3))
    m.box([6.25, 7, 8.75], [7, 15.5, 9.5], stripes('#f4f4f4', '#3aa060', 1, 'v'), rot=rot('z', 22.5, [6.6, 7, 9.1]))
    slice_ = P(['.rkr.', 'rrrrr', 'wwwww', 'ggggg'], {'r': '#f04a4a', 'k': '#2a1a1a', 'w': '#f0f4d8', 'g': '#3a9a3a'})
    m.extrude(slice_, 8.5, 13, 4.6, 5.4)
    m.display = HELD
    m.gui_rot = (25, -35, 0)


def bowl(m, wood_c='#8a5532'):
    rim = scale(wood_c, 1.15)

    def side(w, h, face, rng):
        a = wood(wood_c, along='u')(w, h, face, rng)
        if h >= 3:
            a[h - 2] = rgb(scale(wood_c, 0.72))
        return a
    column(8, 8, 0, 1, 6, solid(scale(wood_c, 0.9)), solid(scale(wood_c, 0.85)), m)
    column(8, 8, 1, 3, 10, solid(wood_c), wood(wood_c, along='u'), m)
    column(8, 8, 3, 6, 12, solid(rim), side, m, inner=10)


@item
def rice_bowl(m):
    bowl(m)
    rice = speckle('#f6f3ea', ['#e8e2d2'] * 6 + ['#ffffff'] * 3 + ['#3a3a3a'], 0.3)
    column(8, 8, 3, 6.5, 10, rice, rice, m)
    column(8, 8, 6.5, 7.5, 8, rice, rice, m)
    column(8, 8, 7.5, 8.25, 4, rice, rice, m)
    m.box([7.25, 8.25, 7.25], [8.75, 9, 8.75], solid('#c8283a', bevel=0.25))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def winter_stew(m):
    bowl(m)
    stew = speckle('#7a4a26', ['#e8792a', '#f0d878', '#4a8a3a', '#9a5a30'], 0.3)
    column(8, 8, 3, 5.5, 10, stew, solid('#6a3e20'), m)
    for a, b, c in (([5, 5.5, 6], [6.25, 6, 7.25], '#e8792a'), ([8.5, 5.5, 5.5], [10, 6.1, 7], '#efd88a'),
                    ([9, 5.5, 9], [10.5, 6.2, 10.5], '#8a4a2a'), ([6.5, 5.5, 9.5], [7.5, 5.9, 10.5], '#e8792a'),
                    ([7.5, 5.5, 7.5], [8.25, 5.75, 8.25], '#4a9a3a')):
        m.box(a, b, solid(c, bevel=0.2))
    m.box([10.25, 5, 8], [11, 13, 8.5], metal('#d0d4d8'), rot=rot('z', -22.5, [10.6, 5, 8.25]))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def icefish(m):
    inflate(m, sprite('icefish'), cap=1.75)
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def cooked_stormfish(m):
    inflate(m, sprite('cooked_stormfish'), cap=1.75)
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def cooked_icefish(m):
    inflate(m, sprite('cooked_icefish'), cap=1.75)
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def kale(m):
    img = sprite('kale')
    vein = colour_mask(img, ['#aacda0'])
    inflate(m, img, base=0.4, step=0.35, cap=1.4, extra=np.where(vein, 0.25, 0.0))
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


@item
def rice(m):
    inflate(m, sprite('rice'), base=0.5, step=0.25, cap=0.9)
    m.display = FLAT
    m.gui_rot = (15, -30, 0)


# ---------------------------------------------------------------- machines and furniture

@item
def forecast_board(m):
    frame = '#8a5a32'
    board = art([
        'fffffffffffff',
        'fcccccccccccf',
        'fcyycccwwwccf',
        'fcyyccwwwwwcf',
        'fccccccbcbccf',
        'fcccccbcbcccf',
        'fcccccccccccf',
        'fcWWWcWWcWWcf',
        'fcccccccccccf',
        'fffffffffffff',
    ], {'f': frame, 'c': '#2e4a3a', 'y': '#f2d24a', 'w': '#eef2f4', 'b': '#7ab8f0', 'W': '#c8d4cc'})
    m.box([1.5, 5, 7], [14.5, 15, 8], wood(frame), faces={'south': board})
    m.box([2.5, 0, 6.25], [3.5, 6, 7], wood(frame))
    m.box([12.5, 0, 6.25], [13.5, 6, 7], wood(frame))
    m.box([7.5, 1, 6.25], [8.5, 14, 7], wood(frame), rot=rot('x', 22.5, [8, 14, 6.6]))
    m.box([2, 4.5, 7.5], [14, 5, 9], wood(scale(frame, 0.9)))
    m.box([10, 5, 8], [11.5, 5.5, 8.5], solid('#f4f4f4'))
    m.display = HELD
    m.gui_rot = (20, -30, 0)


@item
def storm_probe(m):
    gray = '#6a6e74'
    for r in (rot('z', 22.5, [8, 7.5, 8]), rot('z', -22.5, [8, 7.5, 8]), rot('x', 22.5, [8, 7.5, 8])):
        m.box([7.5, 0, 7.5], [8.5, 7.5, 8.5], metal(gray), rot=r)
    m.box([7.5, 7, 7.5], [8.5, 9, 8.5], metal(gray))
    face = art(['wwwww', 'wkwkw', 'wwwww', 'kkkkk'], {'w': '#e8eaec', 'k': '#9aa0a6'})
    m.box([5.5, 9, 5.5], [10.5, 12.5, 10.5], solid('#e8eaec', bevel=0.15), faces={'south': face})
    m.box([6.5, 10.75, 10.5], [7.5, 11.75, 10.75], solid('#5aff6a'), light=15)
    dark = metal('#4a4e54')
    m.box([7.75, 12.5, 7.75], [8.25, 14.5, 8.25], dark)
    m.box([4.5, 14, 7.8], [11.5, 14.4, 8.2], dark)
    m.box([7.8, 14, 4.5], [8.2, 14.4, 11.5], dark)
    for a in ([4, 13.7, 7.5], [11, 13.7, 7.5], [7.5, 13.7, 4], [7.5, 13.7, 11]):
        m.box(a, [a[0] + 1, a[1] + 1, a[2] + 1], solid('#3a3e44', bevel=0.2))
    m.display = HELD
    m.gui_rot = (25, -35, 0)


@item
def weather_vane(m):
    iron, gold = '#3c3c42', '#d8a23a'
    m.box([6, 0, 6], [10, 1, 10], metal(iron))
    m.box([7.5, 1, 7.5], [8.5, 13, 8.5], metal(iron))
    m.box([3, 8, 7.75], [13, 8.5, 8.25], metal(iron))
    m.box([7.75, 8, 3], [8.25, 8.5, 13], metal(iron))
    for a in ([2.5, 7.75, 7.5], [12.5, 7.75, 7.5], [7.5, 7.75, 2.5], [7.5, 7.75, 12.5]):
        m.box(a, [a[0] + 1, a[1] + 1, a[2] + 1], solid(gold, bevel=0.2))
    m.box([7.25, 12.5, 7.25], [8.75, 13.25, 8.75], solid(gold, bevel=0.2))
    m.box([3, 13.25, 7.75], [13, 13.75, 8.25], metal('#c8733a'))
    arrow = P(['x..', 'xx.', 'xxx', 'xx.', 'x..'], {'x': '#c8733a'})
    tail = P(['xx..', '.xx.', '..xx', '.xx.', 'xx..'], {'x': '#5fa88a'})
    m.extrude(arrow, 13, 16, 7.6, 8.4)
    m.extrude(tail, 1, 16, 7.6, 8.4)
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def windmill(m):
    stone, plank, roof = '#8a8a8a', '#a8784a', '#7a3a2a'
    m.box([5, 0, 5], [11, 1, 11], speckle(stone, ['#7a7a7a', '#9a9a9a'], 0.3))
    door = art(['ppppp', 'ppDpp', 'pDDDp', 'pDDDp'], {'p': plank, 'D': '#4a2e1a'})
    m.box([5.5, 1, 5.5], [10.5, 5, 10.5], wood(plank, along='v'), faces={'south': door})
    m.box([6, 5, 6], [10, 9, 10], wood(plank, along='v'))
    m.box([5.5, 9, 5.5], [10.5, 10, 10.5], solid(roof, bevel=0.15))
    m.box([6.5, 10, 6.5], [9.5, 11, 9.5], solid(roof, bevel=0.15))
    m.box([7.5, 11, 7.5], [8.5, 11.75, 8.5], solid(scale(roof, 0.9)))
    m.box([7.25, 7.25, 10], [8.75, 8.75, 11.75], solid('#5a3a22', bevel=0.2))
    sail = art(['clclclcsclclclc', 'sssssssssssssss', 'clclclcsclclclc'], {'c': '#efe8d8', 'l': '#b89a72', 's': '#6a4a2a'})
    sail_v = art(['csc', 'lsl', 'csc', 'lsl', 'csc', 'lsl', 'csc', 'sss', 'csc', 'lsl', 'csc', 'lsl', 'csc', 'lsl', 'csc'],
                 {'c': '#efe8d8', 'l': '#b89a72', 's': '#6a4a2a'})
    r = rot('z', 45, [8, 8, 11])
    m.box([0.5, 6.5, 10.75], [15.5, 9.5, 11.25], solid('#efe8d8'), faces={'south': sail, 'north': sail}, rot=r)
    m.box([6.5, 0.5, 10.8], [9.5, 15.5, 11.2], solid('#efe8d8'), faces={'south': sail_v, 'north': sail_v}, rot=r)
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def tent(m):
    canvas = '#6b8f3a'
    m.box([0.5, 0, 1.5], [15.5, 0.5, 14.5], solid('#5a4a32'))
    panel = {'up': stripes(canvas, scale(canvas, 0.9), 3, 'v'), 'down': solid(scale(canvas, 0.7))}
    m.box([1, 0.5, 2], [10.9, 1.25, 14], solid(scale(canvas, 0.85)), faces=panel, rot=rot('z', 45, [1, 0.5, 8]))
    m.box([5.1, 0.5, 2], [15, 1.25, 14], solid(scale(canvas, 0.85)), faces=panel, rot=rot('z', -45, [15, 0.5, 8]))
    m.box([7.5, 7.75, 1.25], [8.5, 8.75, 14.75], wood('#7a5a3a'))
    pal = {'c': canvas, 'd': '#1e2a14', 'f': '#8aac5a'}
    front = P([
        '......c......',
        '.....ccc.....',
        '....ccccc....',
        '...ccdddcc...',
        '..cccdddccc..',
        '.ccfcdddcfcc.',
        'cccfcdddcfccc',
    ], pal)
    back = P([
        '......c......',
        '.....ccc.....',
        '....ccccc....',
        '...ccccccc...',
        '..ccccccccc..',
        '.ccccccccccc.',
        'ccccccccccccc',
    ], pal)
    m.extrude(front, 1.5, 7.5, 13.25, 13.75)
    m.extrude(back, 1.5, 7.5, 2.25, 2.75)
    for x, z in ((0.25, 14.25), (15.25, 14.25), (0.25, 1.25), (15.25, 1.25)):
        m.box([x, 0, z], [x + 0.5, 1.25, z + 0.5], wood('#7a5a3a'))
    m.display = HELD
    m.gui_rot = (25, -35, 0)


# ---------------------------------------------------------------- nature

@item
def fallen_branch(m):
    img = sprite('fallen_branch')
    leaf = colour_mask(img, ['#5a8c3c'])
    inflate(m, img, base=0.6, step=0.4, cap=1.2, extra=np.where(leaf, -0.35, 0.0))
    m.display = FLAT
    m.gui_rot = (20, -30, 0)


@item
def leaf_pile(m):
    cols = ['#c74027', '#db7621', '#e9842f', '#c23b22', '#eac53c', '#b8652a', '#e17c27', '#be371e']
    leaves = speckle('#d8731e', cols, 0.6)
    for y1, y2, d in ((0, 1.5, 14), (1.5, 3, 11), (3, 4.25, 7), (4.25, 5, 3)):
        column(8, 8, y1, y2, d, leaves, leaves, m)
    for (x, y, z, a, c) in ((3, 1.5, 6, 22.5, '#eac53c'), (10.5, 1.5, 10.5, -22.5, '#c23b22'),
                            (6, 3, 10, 45, '#e9842f'), (9.5, 3, 5.5, -45, '#c74027'), (11, 0, 2.5, 22.5, '#db7621')):
        m.box([x, y, z], [x + 2.5, y + 0.25, z + 1.5], solid(c, bevel=0.2), rot=rot('y', a, [x + 1.25, y, z + 0.75]))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


STEM = '#4f7a35'


def stems(m, tops, colour=STEM):
    """Stems from the ground: (angle_axis, angle, length). Returns the top positions."""
    out = []
    for axis, ang, ln in tops:
        r = rot(axis, ang, [7.95, 0, 7.95]) if ang else None
        m.box([7.6, 0, 7.6], [8.3, ln, 8.3], solid(colour), rot=r)
        a = math.radians(ang)
        if axis == 'z':
            out.append((7.95 - ln * math.sin(a), ln * math.cos(a), 7.95))
        else:
            out.append((7.95, ln * math.cos(a), 7.95 + ln * math.sin(a)))
    return out


def basal_leaves(m, colour, n=3, length=8):
    for i in range(n):
        ang = (-45, -22.5, 0, 22.5, 45)[i * 2 % 5] if n > 2 else 22.5 * (1 if i else -1)
        m.box([8 - length / 2, 0, 7.5], [8 + length / 2, 0.6, 8.5], solid(colour, bevel=0.15),
              rot=rot('y', ang if ang else 0.0001, [8, 0, 8]) if ang else None)


@item
def fireweed(m):
    leafc = '#5e8f3e'
    m.box([7.6, 0, 7.6], [8.4, 12.5, 8.4], solid(STEM))
    lf = solid(leafc, bevel=0.15)
    m.box([8, 2, 7.6], [12, 2.5, 8.1], lf, rot=rot('z', 22.5, [8, 2, 7.85]))
    m.box([4, 4, 7.6], [8, 4.5, 8.1], lf, rot=rot('z', -22.5, [8, 4, 7.85]))
    m.box([7.6, 3, 8], [8.1, 3.5, 12], lf, rot=rot('x', -22.5, [7.85, 3, 8]))
    m.box([7.6, 5, 4], [8.1, 5.5, 8], lf, rot=rot('x', 22.5, [7.85, 5, 8]))
    for (x, y, z, s, c) in ((6.9, 6.5, 7.9, 1.7, '#d6408c'), (9, 7.5, 7.2, 1.6, '#e05a9c'), (8, 8.5, 9.1, 1.6, '#d6408c'),
                            (6.8, 9.5, 7.0, 1.5, '#e86aac'), (9.1, 10.4, 8.4, 1.4, '#d6408c'), (7.4, 11.3, 9.0, 1.3, '#e05a9c'),
                            (8.6, 12.2, 7.2, 1.2, '#c0306e'), (7.8, 13, 8.2, 1.0, '#a8285e'), (8, 13.8, 7.8, 0.8, '#7a8a3a')):
        m.box([x - s / 2, y, z - s / 2], [x + s / 2, y + s, z + s / 2], solid(c, bevel=0.25))
    m.display = HELD
    m.gui_rot = (20, -35, 0)


def flowers(m, tops, img, side, h=1.0):
    for (x, y, z) in tops:
        head(m, x, z, y, y + h, img, side)


@item
def desert_poppy(m):
    tops = stems(m, (('z', 0, 9), ('z', 22.5, 7), ('z', -22.5, 7.5), ('x', -22.5, 5.5)))
    basal_leaves(m, '#7a9a6a', 3, 7)
    img = P(['.oo.', 'oyyo', 'oyyo', '.oo.'], {'o': '#f07a1e', 'y': '#f2c43a'})
    flowers(m, tops, img, solid('#e86a14', bevel=0.2), 1.25)
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def sand_verbena(m):
    tops = stems(m, (('z', 0, 9), ('z', 22.5, 7), ('z', -22.5, 7.5)))
    basal_leaves(m, '#5e8a4a', 3, 7)
    pink = {'p': '#e07ac8', 'l': '#f4b0e0', 'w': '#fff0fa'}
    ball = P(['.pp.', 'plwp', 'pwlp', '.pp.'], pink)
    cap = P(['lp', 'pl'], pink)
    side = speckle('#e07ac8', ['#f4b0e0', '#fff0fa'], 0.35)
    for (x, y, z) in tops:
        head(m, x, z, y - 0.25, y + 1.5, ball, side)
        head(m, x, z, y + 1.5, y + 2.25, cap, side)
    m.display = HELD
    m.gui_rot = (20, -35, 0)


@item
def desert_gold(m):
    tops = stems(m, (('z', 0, 8.5), ('z', 22.5, 7), ('z', -22.5, 6.5), ('x', 22.5, 6)))
    basal_leaves(m, '#6a8a4a', 3, 7)
    img = P(['.yyy.', 'yyyyy', 'yyoyy', 'yyyyy', '.yyy.'], {'y': '#f2c42a', 'o': '#a8661e'})
    flowers(m, tops, img, solid('#e0b020'), 0.75)
    m.display = HELD
    m.gui_rot = (25, -35, 0)


def lichen_rock(m, rock, crust):
    stone = speckle(rock, [scale(rock, 0.9), scale(rock, 1.08)], 0.35)
    m.box([2, 0, 3], [14, 3, 13], stone)
    m.box([4, 3, 5], [11, 4.5, 11], stone)
    m.extrude(colour_blobs(blobs(12, 10, m.rng, 0.5), crust, m.rng), 2, 3, 3, 3.3, axis='y')
    m.extrude(colour_blobs(blobs(7, 6, m.rng, 0.55), crust, m.rng), 4, 5, 4.5, 4.8, axis='y')
    m.extrude(colour_blobs(blobs(12, 3, m.rng, 0.45), crust, m.rng), 2, 3, 13, 13.3)
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def orange_lichen(m):
    lichen_rock(m, '#8a8a86', ['#e8842a', '#f2a83c', '#c86a1e', '#f0c050'])


@item
def black_lichen(m):
    lichen_rock(m, '#b8b2a4', ['#2a2622', '#3e3a34', '#1a1816'])


@item
def foxfire(m):
    bark = '#4a3424'
    rings = art(['bbbbbbbb', 'brrRRrrb', 'brRrrRrb', 'brrRRrrb', 'bbbbbbbb'], {'b': bark, 'r': '#8a6a48', 'R': '#6e5236'})
    m.box([1, 0, 4], [15, 5, 12], wood(bark, along='u'), faces={'east': rings, 'west': rings})
    glow = ['#9affa0', '#5fe07a', '#c8ffb0']
    m.extrude(colour_blobs(blobs(14, 8, m.rng, 0.4), glow, m.rng), 1, 4, 5, 5.3, axis='y', light=15)
    m.extrude(colour_blobs(blobs(14, 5, m.rng, 0.35), glow, m.rng), 1, 5, 12, 12.3, light=15)
    for x, z in ((4, 6), (10.5, 8.5)):
        m.box([x, 5, z], [x + 1, 6.5, z + 1], solid('#d8e8c8'))
        m.box([x - 0.75, 6.5, z - 0.75], [x + 1.75, 7.25, z + 1.75], solid('#9affa0', bevel=0.2), light=15)
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def glowworms(m):
    rock = '#4a4a52'
    stone = speckle(rock, [scale(rock, 0.85), scale(rock, 1.12)], 0.35)
    m.box([2, 13, 3], [14, 16, 13], stone)
    m.box([4, 12, 5], [12, 13, 11], stone)
    for (x, z, ln) in ((4, 5, 6), (6, 9, 9), (8, 6, 4), (9.5, 10.5, 7), (11, 7, 10), (12.5, 10, 5), (5.5, 11.5, 8), (10, 4.5, 6)):
        top = 12 if (4 <= x <= 12 and 5 <= z <= 11) else 13
        m.box([x, top - ln, z], [x + 0.25, top, z + 0.25], solid('#cfe8f0', 0), shade=False)
        y = top - 2
        while y > top - ln:
            m.box([x - 0.125, y, z - 0.125], [x + 0.375, y + 0.5, z + 0.375], solid('#7fd8ff', 0), light=15)
            y -= 2
        m.box([x - 0.25, top - ln - 0.5, z - 0.25], [x + 0.5, top - ln + 0.25, z + 0.5], solid('#b8f0ff', 0), light=15)
    m.display = HELD
    m.gui_rot = (15, -35, 0)



# ---------------------------------------------------------------- block items that were thin slivers in the inventory

BLOCK_TEX = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'reikos_weather', 'textures', 'block')


def block_tex(name):
    return np.array(Image.open(os.path.join(BLOCK_TEX, name + '.png')).convert('RGBA'))[:16, :16]


def tex(name, darken=1.0):
    """Painter that tiles a block texture, starting at a random spot so faces don't all match."""
    t = block_tex(name)

    def paint(w, h, face, rng):
        ox, oy = rng.randrange(16), rng.randrange(16)
        ys = (np.arange(h) + oy) % 16
        xs = (np.arange(w) + ox) % 16
        a = t[ys][:, xs].copy()
        if darken != 1.0:
            a[..., :3] = np.clip(a[..., :3] * darken, 0, 255)
        return a
    return paint


def pile(m, name, layers=((0, 1.5, 14), (1.5, 3, 11), (3, 4.5, 7), (4.5, 5.25, 3))):
    for y1, y2, d in layers:
        column(8, 8, y1, y2, d, tex(name), tex(name, 0.95), m)


@item
def volcanic_ash(m):
    pile(m, 'volcanic_ash')
    for a, c in (([3, 0, 9.5], '#3a3634'), ([11.5, 0, 4.5], '#4a4440'), ([9, 1.5, 11], '#2e2a28')):
        m.box(a, [a[0] + 1.25, a[1] + 1, a[2] + 1.25], solid(c, 0.08, 0.15))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def desert_dust(m):
    pile(m, 'desert_dust', ((0, 1.25, 15), (1.25, 2.5, 12), (2.5, 3.75, 8), (3.75, 4.5, 4)))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def aufeis(m):
    ice = tex('aufeis')
    m.box([1, 0, 1], [15, 1.5, 15], ice)
    m.box([2.5, 1.5, 2], [14, 3, 13], ice)
    m.box([4.5, 3, 3.5], [12, 4.5, 11.5], ice)
    m.box([6, 4.5, 5.5], [10.5, 5.5, 9.5], ice)
    for x, z, h in ((14.25, 6, 1.25), (14.25, 10, 0.75), (13.25, 12.5, 1)):
        m.box([x, 1.5 - h, z], [x + 0.75, 1.5, z + 0.75], solid('#dfeefa', bevel=0.2))
    m.display = HELD
    m.gui_rot = (30, -35, 0)


@item
def beach_wrack(m):
    t = block_tex('beach_wrack')
    m.extrude(t, 0, 0, 0, 0.75, axis='y')
    m.extrude(np.rot90(t).copy(), 1, 1, 0.75, 1.5, axis='y')
    m.extrude(np.rot90(t, 2)[3:13, 3:13].copy(), 4, 4, 1.5, 2.25, axis='y')
    m.box([10.5, 1.5, 3], [12.5, 2.5, 4.5], solid('#f2e8da', bevel=0.25))
    for x, z in ((4, 11), (6.5, 4)):
        m.box([x, 0.75, z], [x + 1, 1.75, z + 1], solid('#8a7a2a', bevel=0.25))
    m.display = HELD
    m.gui_rot = (35, -35, 0)


@item
def glacier_mouse(m):
    moss = tex('glacier_mouse')
    for y1, y2, d in ((0, 1, 6), (1, 2, 8), (2, 3, 10), (3, 5, 10), (5, 6, 10), (6, 7, 8), (7, 8, 6)):
        column(8, 8, y1, y2, d, moss, moss, m)
    m.display = HELD
    m.gui_rot = (25, -35, 0)


# ---------------------------------------------------------------- main

def main(argv):
    names = [a for a in argv if not a.startswith('--')]
    sheet_path = None
    if '--sheet' in argv:
        sheet_path = argv[argv.index('--sheet') + 1]
        names = [n for n in names if n != sheet_path]
    todo = names or list(REG)
    entries = []
    for n in todo:
        fn = REG[n]
        m = Model(n)
        fn(m)
        size, pos, tex = m.save()
        entries.append((n, m.to_json(size, pos), tex))
        print(f'{n}: {len(m.elements)} elements, {size}x{size} texture')
    if sheet_path:
        sheet(entries, sheet_path)


if __name__ == '__main__':
    main(sys.argv[1:])
