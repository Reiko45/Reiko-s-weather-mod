"""Tiny toolkit for building Minecraft element (3D) item models from code.

A model is a list of boxes. Each box face gets a small painted image (a procedural material or a
piece of pixel art); the images of one model are packed into one square texture, and the JSON model
points every face at its spot in that texture. `preview()` renders models the way the inventory shows
them, so shapes can be checked without starting the game.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'reikos_weather')
MODID = 'reikos_weather'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


# ---------------------------------------------------------------- colours

def rgb(c):
    if isinstance(c, str):
        c = c.lstrip('#')
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255 if len(c) < 8 else int(c[6:8], 16))
    return tuple(c) if len(c) == 4 else tuple(c) + (255,)


def scale(c, f):
    c = rgb(c)
    return tuple(int(max(0, min(255, round(v * f)))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# ---------------------------------------------------------------- materials (painters)
# A painter is f(w, h, face, rng) -> uint8 array (h, w, 4), drawn as the face is seen from outside, upright.

def _noise(w, h, rng, amount):
    return 1 + (np.array([[rng.random() for _ in range(w)] for _ in range(h)]) - 0.5) * 2 * amount


def _bevel(arr, amount):
    h, w = arr.shape[:2]
    if amount <= 0 or w < 3 or h < 3:
        return arr
    f = np.ones((h, w))
    f[0, :] += amount
    f[:, 0] += amount * 0.6
    f[-1, :] -= amount
    f[:, -1] -= amount * 0.6
    arr[..., :3] = np.clip(arr[..., :3] * f[..., None], 0, 255)
    return arr


def solid(c, noise=0.05, bevel=0.0):
    c = rgb(c)

    def paint(w, h, face, rng):
        arr = np.zeros((h, w, 4), float)
        arr[...] = c
        arr[..., :3] *= _noise(w, h, rng, noise)[..., None]
        _bevel(arr, bevel)
        return np.clip(arr, 0, 255).astype(np.uint8)
    return paint


def stripes(c1, c2, period=2, along='u', noise=0.04):
    """Bands of two colours; along='u' gives vertical bands, 'v' horizontal ones."""
    a, b = rgb(c1), rgb(c2)

    def paint(w, h, face, rng):
        arr = np.zeros((h, w, 4), float)
        for y in range(h):
            for x in range(w):
                i = x if along == 'u' else y
                arr[y, x] = a if (i // period) % 2 == 0 else b
        arr[..., :3] *= _noise(w, h, rng, noise)[..., None]
        return np.clip(arr, 0, 255).astype(np.uint8)
    return paint


def wood(c, grain=0.14, noise=0.04, along=None):
    """Grain lines running along the long side of the face (or `along`='u'/'v')."""
    c = rgb(c)

    def paint(w, h, face, rng):
        ax = along or ('u' if w >= h else 'v')
        arr = np.zeros((h, w, 4), float)
        arr[...] = c
        n = h if ax == 'u' else w
        line = [1 + (rng.random() - 0.5) * 2 * grain for _ in range(n)]
        for y in range(h):
            for x in range(w):
                arr[y, x, :3] *= line[y if ax == 'u' else x]
        arr[..., :3] *= _noise(w, h, rng, noise)[..., None]
        return np.clip(arr, 0, 255).astype(np.uint8)
    return paint


def metal(c, shine=0.25, noise=0.03):
    """A soft vertical gradient with one bright streak, like brushed metal."""
    c = rgb(c)

    def paint(w, h, face, rng):
        arr = np.zeros((h, w, 4), float)
        arr[...] = c
        streak = rng.randrange(max(1, w)) if w > 2 else -1
        for y in range(h):
            for x in range(w):
                f = 1 + shine * 0.5 - shine * (y / max(1, h - 1))
                if x == streak:
                    f += shine * 0.6
                arr[y, x, :3] *= f
        arr[..., :3] *= _noise(w, h, rng, noise)[..., None]
        return np.clip(arr, 0, 255).astype(np.uint8)
    return paint


def speckle(c, dots, chance=0.25, noise=0.05):
    """Base colour with random dots picked from `dots`."""
    c = rgb(c)
    dots = [rgb(d) for d in dots]

    def paint(w, h, face, rng):
        arr = np.zeros((h, w, 4), float)
        arr[...] = c
        arr[..., :3] *= _noise(w, h, rng, noise)[..., None]
        for y in range(h):
            for x in range(w):
                if rng.random() < chance:
                    arr[y, x] = rng.choice(dots)
        return np.clip(arr, 0, 255).astype(np.uint8)
    return paint


def art(rows, pal, flip=False):
    """Pixel art: rows of characters, each mapped through `pal` ('.' or ' ' = transparent).
    Stretched (nearest) if the face is a different size."""
    rows = [r for r in rows]
    h, w = len(rows), max(len(r) for r in rows)
    base = np.zeros((h, w, 4), np.uint8)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in '. ':
                continue
            base[y, x] = rgb(pal[ch])
    if flip:
        base = base[:, ::-1]

    def paint(fw, fh, face, rng):
        if (fw, fh) == (w, h):
            return base.copy()
        ys = (np.arange(fh) * h // fh).clip(0, h - 1)
        xs = (np.arange(fw) * w // fw).clip(0, w - 1)
        return base[ys][:, xs].copy()
    paint.grid = base
    return paint


def grid(rows, pal):
    """Pixel art as an array (for extrusions)."""
    return art(rows, pal).grid


# ---------------------------------------------------------------- the model

class Model:
    def __init__(self, name, seed=None, texture_name=None):
        self.name = name
        self.tex_name = texture_name or name
        self.rng = random.Random(seed if seed is not None else name)
        self.elements = []   # dicts: from, to, rotation, faces{face: (imgkey, rect, flip_u, flip_v)}, extra
        self.images = {}     # key -> uint8 array
        self.display = None
        self.gui_rot = (25, -35, 0)
        self.gui_fill = 15.0
        self.gui_max = 1.6
        self.particle = None  # image key for the particle face

    # -- images
    def _add_image(self, arr):
        arr = np.ascontiguousarray(arr, dtype=np.uint8)
        key = (arr.shape, arr.tobytes())
        self.images.setdefault(key, arr)
        return key

    @staticmethod
    def face_size(frm, to, face):
        dx, dy, dz = (abs(to[i] - frm[i]) for i in range(3))
        return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy),
                'up': (dx, dz), 'down': (dx, dz)}[face]

    # -- boxes
    def box(self, frm, to, mat, rot=None, faces=None, shade=True, light=0, skip=()):
        """A cuboid. `mat` paints every face; `faces` overrides single faces (None = leave the face out)."""
        faces = dict(faces or {})
        el = {'from': list(frm), 'to': list(to), 'rotation': rot, 'faces': {}, 'shade': shade, 'light': light}
        for f in FACES:
            if f in skip:
                continue
            painter = faces.get(f, mat) if f in faces else mat
            if painter is None:
                continue
            w, h = self.face_size(frm, to, f)
            if w < 1e-6 or h < 1e-6:
                continue
            tw, th = max(1, int(round(w))), max(1, int(round(h)))
            key = self._add_image(painter(tw, th, f, self.rng))
            el['faces'][f] = (key, (0, 0, tw, th), False, False)
        self.elements.append(el)
        return el

    def boxes(self, spec, mat, **kw):
        for frm, to in spec:
            self.box(frm, to, mat, **kw)

    # -- pixel-art extrusion
    def extrude(self, img, x0, ytop, z1, z2, side='edge', back='mirror', rot=None, light=0, shade=True,
                axis='z', mask=None):
        """Turn pixel art into a slab: every opaque pixel becomes a 1x1 column from z1 to z2.
        z1/z2 may be numbers or per-pixel arrays (a relief: pixels with equal depth are merged).
        axis='z': art seen from the south (x right, y up). axis='y': art seen from above (x right, z down);
        then `ytop` is the north edge (z) and z1..z2 is the height range (y).
        Front shows the art, back shows it mirrored (or `back` art / painter), sides repeat the edge
        pixels (or a painter given as `side`). `light` may also be a per-pixel array."""
        img = np.asarray(img, np.uint8)
        h, w = img.shape[:2]
        m = img[..., 3] > 0 if mask is None else np.asarray(mask, bool)
        Z1 = np.broadcast_to(np.asarray(z1, float), (h, w))
        Z2 = np.broadcast_to(np.asarray(z2, float), (h, w))
        LT = np.broadcast_to(np.asarray(light, int), (h, w))
        key = self._add_image(img)
        bkey = self._add_image(back) if isinstance(back, np.ndarray) else None

        def covers(r, c, lo, hi):
            return 0 <= r < h and 0 <= c < w and m[r, c] and Z1[r, c] <= lo and Z2[r, c] >= hi

        groups = {}
        for r in range(h):
            for c in range(w):
                if m[r, c]:
                    groups.setdefault((Z1[r, c], Z2[r, c], LT[r, c]), []).append((r, c))
        for (lo, hi, lt), cells in groups.items():
            gm = np.zeros((h, w), bool)
            for r, c in cells:
                gm[r, c] = True
            for (c0, r0, c1, r1) in _rects(gm):
                self._slab(img, key, bkey, back, side, axis, x0, ytop, lo, hi, rot, int(lt), shade, h, w,
                           c0, r0, c1, r1, covers)

    def _slab(self, img, key, bkey, back, side, axis, x0, ytop, z1, z2, rot, light, shade, h, w,
              c0, r0, c1, r1, covers):
        if True:
            if axis == 'z':
                frm = [x0 + c0, ytop - r1, z1]
                to = [x0 + c1, ytop - r0, z2]
                fr, bk, top, bot, left, right = 'south', 'north', 'up', 'down', 'west', 'east'
            else:
                frm = [x0 + c0, z1, ytop + r0]
                to = [x0 + c1, z2, ytop + r1]
                fr, bk, top, bot, left, right = 'up', 'down', 'north', 'south', 'west', 'east'
            el = {'from': frm, 'to': to, 'rotation': rot, 'faces': {}, 'shade': shade, 'light': light}
            el['faces'][fr] = (key, (c0, r0, c1, r1), False, False)
            if isinstance(back, str) and back == 'mirror':
                el['faces'][bk] = (key, (c0, r0, c1, r1), True, False) if axis == 'z' else (key, (c0, r0, c1, r1), False, True)
            elif bkey is not None:
                el['faces'][bk] = (bkey, (w - c1, r0, w - c0, r1), False, False) if axis == 'z' else (bkey, (c0, h - r1, c1, h - r0), False, False)
            elif callable(back):
                bw, bh = c1 - c0, r1 - r0
                el['faces'][bk] = (self._add_image(back(bw, bh, bk, self.rng)), (0, 0, bw, bh), False, False)
            edges = {
                top: (not all(covers(r0 - 1, c, z1, z2) for c in range(c0, c1)), img[r0, c0:c1]),
                bot: (not all(covers(r1, c, z1, z2) for c in range(c0, c1)), img[r1 - 1, c0:c1]),
                left: (not all(covers(r, c0 - 1, z1, z2) for r in range(r0, r1)), img[r0:r1, c0]),
                right: (not all(covers(r, c1, z1, z2) for r in range(r0, r1)), img[r0:r1, c1 - 1]),
            }
            for f, (needed, px) in edges.items():
                if not needed:
                    continue
                fw, fh = self.face_size(frm, to, f)
                tw, th = max(1, int(round(fw))), max(1, int(round(fh)))
                if side == 'edge':
                    el['faces'][f] = (self._add_image(_edge_image(f, px, tw, th, axis)), (0, 0, tw, th), False, False)
                elif side is not None:
                    el['faces'][f] = (self._add_image(side(tw, th, f, self.rng)), (0, 0, tw, th), False, False)
            self.elements.append(el)

    # -- helpers for round things
    def octo(self, cx, cz, y1, y2, r, mat, faces=None, **kw):
        """An octagonal post: an axis-aligned box plus the same box turned 45°."""
        self.box([cx - r, y1, cz - r], [cx + r, y2, cz + r], mat, faces=faces, **kw)
        self.box([cx - r, y1, cz - r], [cx + r, y2, cz + r], mat, faces=faces,
                 rot={'origin': [cx, y1, cz], 'axis': 'y', 'angle': 45}, **kw)

    # -- output
    def pack(self):
        items = sorted(self.images.items(), key=lambda kv: (-kv[1].shape[0], -kv[1].shape[1]))
        for size in (16, 32, 64, 128):
            pos, x, y, row = {}, 0, 0, 0
            ok = True
            for k, a in items:
                h, w = a.shape[:2]
                if w > size:
                    ok = False
                    break
                if x + w > size:
                    x, y, row = 0, y + row, 0
                if y + h > size:
                    ok = False
                    break
                pos[k] = (x, y)
                x += w
                row = max(row, h)
            if ok:
                tex = np.zeros((size, size, 4), np.uint8)
                for k, a in items:
                    px, py = pos[k]
                    tex[py:py + a.shape[0], px:px + a.shape[1]] = a
                return size, pos, tex
        raise ValueError(f'{self.name}: textures do not fit in 128x128')

    def uv(self, face_ref, size, pos):
        key, rect = face_ref[0], face_ref[1]
        fu, fv = face_ref[2], face_ref[3]
        px, py = pos[key]
        s = 16.0 / size
        c0, r0, c1, r1 = rect
        u1, v1, u2, v2 = (px + c0) * s, (py + r0) * s, (px + c1) * s, (py + r1) * s
        if fu:
            u1, u2 = u2, u1
        if fv:
            v1, v2 = v2, v1
        return [_r(u1), _r(v1), _r(u2), _r(v2)]

    def bounds(self):
        pts = []
        for el in self.elements:
            pts.extend(_corners(el))
        pts = np.array(pts)
        return pts.min(0), pts.max(0)

    def to_json(self, size, pos):
        tex = f'{MODID}:item/model/{self.tex_name}'
        out = {'credit': 'Generated by tools/item_models', 'texture_size': [size, size],
               'textures': {'0': tex, 'particle': tex}, 'elements': []}
        for el in self.elements:
            e = {'from': [_r(v) for v in el['from']], 'to': [_r(v) for v in el['to']]}
            if el['rotation']:
                r = dict(el['rotation'])
                r['origin'] = [_r(v) for v in r['origin']]
                e['rotation'] = r
            if not el['shade']:
                e['shade'] = False
            if el['light']:
                e['neoforge_data'] = {'block_light': el['light'], 'sky_light': el['light']}
            e['faces'] = {}
            for f, ref in el['faces'].items():
                e['faces'][f] = {'uv': self.uv(ref, size, pos), 'texture': '#0'}
            out['elements'].append(e)
        out['display'] = self.display_json()
        return out

    def save(self):
        size, pos, tex = self.pack()
        os.makedirs(os.path.join(ROOT, 'textures', 'item', 'model'), exist_ok=True)
        Image.fromarray(tex, 'RGBA').save(os.path.join(ROOT, 'textures', 'item', 'model', self.tex_name + '.png'))
        with open(os.path.join(ROOT, 'models', 'item', self.name + '.json'), 'w') as fh:
            json.dump(self.to_json(size, pos), fh, indent=1)
            fh.write('\n')
        return size, pos, tex

    # -- display transforms
    def gui_transform(self):
        rx, ry, rz = self.gui_rot
        R = _rot_xyz(rx, ry, rz)
        pts = []
        for el in self.elements:
            pts.extend(_corners(el))
        p = (np.array(pts) - 8.0) @ R.T
        lo, hi = p.min(0), p.max(0)
        ext = max(hi[0] - lo[0], hi[1] - lo[1])
        s = min(self.gui_max, self.gui_fill / ext)
        c = (lo + hi) / 2 * s
        return {'rotation': [rx, ry, rz], 'translation': [_r(-c[0]), _r(-c[1]), 0], 'scale': [_r(s)] * 3}

    def display_json(self):
        d = {k: dict(v) for k, v in (self.display or HELD).items()}
        if self.display is None or self.display is HELD:
            # Standing objects are built resting on the floor of the 16-unit box; hold them by their middle.
            lo, hi = self.bounds()
            c = (lo + hi) / 2
            for k in ('firstperson_righthand', 'thirdperson_righthand', 'ground', 'fixed'):
                if k in d:
                    t = d[k]
                    R = _rot_xyz(*t['rotation'])
                    off = (np.array([8.0, 8.0, 8.0]) - c) * np.array(t['scale'])
                    t['translation'] = [_r(max(-80, min(80, a + b))) for a, b in zip(t['translation'], off @ R.T)]
        d['gui'] = self.gui_transform()
        return d


# ---------------------------------------------------------------- display presets

def T(rot=(0, 0, 0), tr=(0, 0, 0), sc=1.0):
    s = sc if isinstance(sc, (list, tuple)) else [sc] * 3
    return {'rotation': list(rot), 'translation': list(tr), 'scale': list(s)}


# Flat-ish things built in the x/y plane like a sprite: the vanilla held-item poses.
FLAT = {
    'ground': T((0, 0, 0), (0, 2, 0), 0.5),
    'head': T((0, 180, 0), (0, 13, 7), 1),
    'thirdperson_righthand': T((0, 0, 0), (0, 3, 1), 0.55),
    'firstperson_righthand': T((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
    'fixed': T((0, 180, 0), (0, 0, 0), 1),
}
# Long tools drawn corner to corner, like vanilla's handheld sprites.
TOOL = {
    'ground': T((0, 0, 0), (0, 2, 0), 0.5),
    'head': T((0, 180, 0), (0, 13, 7), 1),
    'thirdperson_righthand': T((0, -90, 55), (0, 4, 0.5), 0.85),
    'thirdperson_lefthand': T((0, 90, -55), (0, 4, 0.5), 0.85),
    'firstperson_righthand': T((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
    'firstperson_lefthand': T((0, 90, -25), (1.13, 3.2, 1.13), 0.68),
    'fixed': T((0, 180, 0), (0, 0, 0), 1),
}
# Standing objects (mugs, bowls, hats ...) held upright in front of you.
HELD = {
    'ground': T((0, 0, 0), (0, 3, 0), 0.5),
    'head': T((0, 180, 0), (0, 13, 7), 1),
    'thirdperson_righthand': T((75, 45, 0), (0, 2.5, 0), 0.4),
    'firstperson_righthand': T((10, -20, 0), (0.5, 4.5, 0), 0.42),
    'fixed': T((0, 180, 0), (0, 0, 0), 0.75),
}


# ---------------------------------------------------------------- geometry helpers

def _r(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def _rects(m):
    """Greedy split of a boolean mask into rectangles (c0, r0, c1, r1)."""
    m = m.copy()
    h, w = m.shape
    out = []
    for r in range(h):
        c = 0
        while c < w:
            if not m[r, c]:
                c += 1
                continue
            c1 = c
            while c1 < w and m[r, c1]:
                c1 += 1
            r1 = r + 1
            while r1 < h and m[r1, c:c1].all():
                r1 += 1
            m[r:r1, c:c1] = False
            out.append((c, r, c1, r1))
            c = c1
    return out


def _edge_image(face, px, tw, th, axis):
    """Side of an extruded slab: the edge pixels of the art, stretched across the slab's depth."""
    px = np.asarray(px, np.uint8)
    n = len(px)
    if axis == 'z':
        if face in ('up', 'down'):          # u runs along x
            return np.repeat(px[None, :, :], th, 0)[:, :tw]
        return np.repeat(px[:, None, :], tw, 1)[:th]   # east/west: v runs down the column
    # axis y: every side is u = along the edge, v = height
    if face == 'north':                     # u = 16 - x
        px = px[::-1]
    elif face == 'east':                    # u = 16 - z
        px = px[::-1]
    return np.repeat(px[None, :, :], th, 0)[:, :tw]


def _rot_xyz(rx, ry, rz):
    """Matrix for Minecraft's ItemTransform rotation (rotationXYZ: X then Y then Z, intrinsic)."""
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def _el_rot(el):
    r = el.get('rotation')
    if not r:
        return None
    ang = math.radians(r['angle'])
    ca, sa = math.cos(ang), math.sin(ang)
    if r['axis'] == 'x':
        M = np.array([[1, 0, 0], [0, ca, -sa], [0, sa, ca]])
    elif r['axis'] == 'y':
        M = np.array([[ca, 0, sa], [0, 1, 0], [-sa, 0, ca]])
    else:
        M = np.array([[ca, -sa, 0], [sa, ca, 0], [0, 0, 1]])
    if r.get('rescale'):
        k = 1 / math.cos(math.radians(abs(r['angle']))) if abs(r['angle']) in (22.5, 45) else 1
        S = np.eye(3)
        for i, ax in enumerate('xyz'):
            if ax != r['axis']:
                S[i, i] = k
        M = S @ M
    return M, np.array(r['origin'], float)


def _xf(el, p):
    rr = _el_rot(el)
    p = np.asarray(p, float)
    if rr is None:
        return p
    M, o = rr
    return (p - o) @ M.T + o


def _corners(el):
    f, t = el['from'], el['to']
    return [_xf(el, (x, y, z)) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]


# Corners of each face in Minecraft's UV order: (top-left, top-right, bottom-right, bottom-left) of the
# face as seen from outside, upright (north edge up for 'up', south edge up for 'down').
def _face_quad(el, face):
    (x1, y1, z1), (x2, y2, z2) = el['from'], el['to']
    q = {
        'south': [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        'north': [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        'east': [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        'west': [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        'up': [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        'down': [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]
    return [_xf(el, p) for p in q]


NORMALS = {'south': (0, 0, 1), 'north': (0, 0, -1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


# ---------------------------------------------------------------- preview (software rasteriser)

def render(model_json, tex, view='gui', px=192):
    """Render a model JSON (with its texture array) as the inventory would: orthographic, GUI transform."""
    img = np.zeros((px, px, 4), float)
    zbuf = np.full((px, px), -1e9)
    size = tex.shape[0]
    d = model_json['display'][view]
    R = _rot_xyz(*d['rotation'])
    S = np.array(d['scale'], float)
    Tr = np.array(d['translation'], float)
    L0 = np.array([-0.35, 0.9, 0.55]); L0 /= np.linalg.norm(L0)
    L1 = np.array([0.5, 0.3, 0.8]); L1 /= np.linalg.norm(L1)
    k = px / 16.0
    for e in model_json['elements']:
        el = {'from': e['from'], 'to': e['to'], 'rotation': e.get('rotation')}
        for f, fd in e['faces'].items():
            quad = [((np.array(p) - 8) * S) @ R.T + Tr for p in _face_quad(el, f)]
            n = np.array(NORMALS[f], float)
            rr = _el_rot(el)
            if rr is not None:
                n = n @ rr[0].T
            n = (n * (1 / S)) @ R.T
            n /= np.linalg.norm(n)
            if n[2] <= 1e-4:
                continue
            light = 15 in [e.get('neoforge_data', {}).get('block_light', 0)]
            shadev = 1.0 if light or e.get('shade') is False else min(1.0, 0.45 + 0.45 * max(0, n @ L0) + 0.35 * max(0, n @ L1))
            u1, v1, u2, v2 = fd['uv']
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            scr = [((p[0] + 8) * k, (8 - p[1]) * k, p[2]) for p in quad]
            for tri in ((0, 1, 2), (0, 2, 3)):
                _tri(img, zbuf, [scr[i] for i in tri], [uvs[i] for i in tri], tex, size, shadev)
    out = np.clip(img, 0, 255).astype(np.uint8)
    return out


def _tri(img, zbuf, P, UV, tex, size, shadev):
    h, w = zbuf.shape
    xs = [p[0] for p in P]
    ys = [p[1] for p in P]
    x0, x1 = max(0, int(math.floor(min(xs)))), min(w - 1, int(math.ceil(max(xs))))
    y0, y1 = max(0, int(math.floor(min(ys)))), min(h - 1, int(math.ceil(max(ys))))
    if x1 < x0 or y1 < y0:
        return
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = P
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
    l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
    l3 = 1 - l1 - l2
    inside = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
    if not inside.any():
        return
    z = l1 * az + l2 * bz + l3 * cz
    u = l1 * UV[0][0] + l2 * UV[1][0] + l3 * UV[2][0]
    v = l1 * UV[0][1] + l2 * UV[1][1] + l3 * UV[2][1]
    tu = np.clip((u / 16 * size).astype(int), 0, size - 1)
    tv = np.clip((v / 16 * size).astype(int), 0, size - 1)
    col = tex[tv, tu].astype(float)
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    ok = inside & (z > sub) & (col[..., 3] > 25)
    sub[ok] = z[ok]
    region = img[y0:y1 + 1, x0:x1 + 1]
    c = col.copy()
    c[..., :3] *= shadev
    c[..., 3] = 255
    region[ok] = c[ok]


def sheet(entries, path, px=160, cols=7, bg=(139, 139, 139)):
    """entries: list of (label, model_json, tex). Saves a contact sheet like inventory slots."""
    rows = (len(entries) + cols - 1) // cols
    W, H = cols * (px + 10) + 10, rows * (px + 26) + 10
    out = Image.new('RGBA', (W, H), (60, 60, 68, 255))
    dr = ImageDraw.Draw(out)
    for i, (label, mj, tex) in enumerate(entries):
        x = 10 + (i % cols) * (px + 10)
        y = 10 + (i // cols) * (px + 26)
        dr.rectangle([x, y, x + px - 1, y + px - 1], fill=bg + (255,))
        im = Image.fromarray(render(mj, tex, 'gui', px), 'RGBA')
        out.alpha_composite(im, (x, y))
        dr.text((x + 2, y + px + 4), label[:24], fill=(255, 255, 255, 255))
    out.save(path)
