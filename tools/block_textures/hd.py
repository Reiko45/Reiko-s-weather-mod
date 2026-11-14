"""A small engine for painting detailed, lit block textures.

A face is built up as a height map plus material colours: panels are raised or sunk with bevelled edges,
rivets and knobs are domes, louvres are slanted slats. Lighting from the upper left (with specular glints and
dirt in the crevices) turns the height map into shading, so the result reads as real, embossed hardware.
Everything is drawn at twice the final size and scaled down, which smooths the edges. Screens and lamps go in
an emissive layer that the lighting leaves alone, and that layer also becomes the block's "glow" texture.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

LIGHT = np.array([-0.52, -0.62, 0.58])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0])
HALF = HALF / np.linalg.norm(HALF)


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


# ---------------------------------------------------------------- noise

def value_noise(n, cells, rng, stretch=(1, 1)):
    """Smooth noise in -1..1: random values on a coarse grid, scaled up with bicubic filtering."""
    cw, ch = max(1, int(cells / stretch[0])), max(1, int(cells / stretch[1]))
    g = rng.random((ch, cw)) * 255
    im = Image.fromarray(g.astype(np.uint8), 'L').resize((n, n), Image.BICUBIC)
    return np.asarray(im, float) / 127.5 - 1.0


def fbm(n, rng, base=4, octaves=4, stretch=(1, 1)):
    out = np.zeros((n, n))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(n, base * 2 ** o, rng, stretch)
        total += amp
        amp *= 0.5
    return out / total


def blur(a, r):
    """Box blur (twice, so roughly Gaussian) with radius r pixels."""
    r = max(1, int(r))
    for _ in range(2):
        for axis in (0, 1):
            pad = np.pad(a, [(r, r) if i == axis else (0, 0) for i in range(a.ndim)], mode='edge')
            c = np.cumsum(pad, axis=axis)
            c = np.insert(c, 0, 0, axis=axis)
            hi = np.take(c, np.arange(2 * r + 1, c.shape[axis]), axis=axis)
            lo = np.take(c, np.arange(0, c.shape[axis] - 2 * r - 1), axis=axis)
            a = (hi - lo) / (2 * r + 1)
    return a


# ---------------------------------------------------------------- materials

MATERIALS = {
    # name: (base colour, variation, streak, specular, gloss)
    'copper': ('#b8673f', 0.09, 0.07, 0.45, 18),
    'copper_light': ('#d98a5a', 0.07, 0.06, 0.55, 22),
    'copper_dark': ('#7e4026', 0.10, 0.05, 0.30, 14),
    'brass': ('#c99a42', 0.07, 0.07, 0.6, 24),
    'iron': ('#4a4f56', 0.10, 0.04, 0.30, 16),
    'iron_dark': ('#2c2f34', 0.10, 0.03, 0.25, 14),
    'steel': ('#9aa1a8', 0.06, 0.05, 0.45, 22),
    'paint_white': ('#dfe2e5', 0.035, 0.0, 0.22, 30),
    'paint_grey': ('#b8bdc3', 0.04, 0.0, 0.22, 30),
    'rubber': ('#222326', 0.06, 0.0, 0.12, 10),
    'glass': ('#121a21', 0.05, 0.0, 0.9, 70),
    'paper': ('#efe7d0', 0.03, 0.0, 0.05, 6),
    'stone': ('#9e9e9e', 0.06, 0.0, 0.06, 8),
    'stone_light': ('#b2b2b2', 0.05, 0.0, 0.06, 8),
    'porcelain': ('#ece8df', 0.025, 0.0, 0.7, 40),
    'red': ('#b02a24', 0.05, 0.0, 0.5, 30),
    'yellow': ('#e6b62a', 0.05, 0.0, 0.3, 20),
    'black': ('#18191b', 0.05, 0.0, 0.3, 20),
    'cream': ('#efe6c8', 0.03, 0.0, 0.2, 12),
    'solar': ('#1b2a52', 0.06, 0.0, 0.8, 50),
    'amethyst': ('#8e62c9', 0.08, 0.0, 0.7, 40),
    'gold': ('#e2b23a', 0.06, 0.06, 0.7, 30),
    'obsidian': ('#1d1229', 0.16, 0.0, 0.5, 40),
    'diamond': ('#8de9e2', 0.05, 0.0, 0.9, 60),
    'paint_yellow': ('#d9ac26', 0.04, 0.0, 0.25, 26),
    'enamel': ('#ece4cc', 0.025, 0.0, 0.28, 24),
    'oak': ('#a37a4b', 0.06, 0.0, 0.12, 10),
    'oak_v': ('#a37a4b', 0.06, 0.0, 0.12, 10),
    'oak_dark': ('#6f4e2f', 0.07, 0.0, 0.12, 10),
    'oak_dark_v': ('#6f4e2f', 0.07, 0.0, 0.12, 10),
    'walnut': ('#4b3122', 0.07, 0.0, 0.18, 12),
    'galvanized': ('#a9b0b5', 0.07, 0.04, 0.45, 20),
}


class Face:
    def __init__(self, size=64, ss=2, seed=0):
        self.n, self.ss = size, ss
        self.N = N = size * ss
        self.rng = np.random.default_rng(seed)
        self.H = np.zeros((N, N))
        self.A = np.zeros((N, N, 3))
        self.S = np.zeros((N, N))
        self.G = np.full((N, N), 16.0)
        self.M = np.full((N, N), '', dtype=object)
        self.E = np.zeros((N, N, 3))
        self.Ea = np.zeros((N, N))
        self._mats = {}

    # ---- coordinates are in final texture pixels (0..size); masks are float 0..1 at the working size

    def _canvas(self, fn):
        im = Image.new('L', (self.N, self.N), 0)
        fn(ImageDraw.Draw(im))
        return np.asarray(im, float) / 255.0

    def rect(self, x0, y0, x1, y1, r=0):
        s = self.ss
        if r <= 0:
            # exact pixel coverage, so thin lines come out as faint lines rather than vanishing
            p = np.arange(self.N)
            cx = np.clip(np.minimum(x1 * s, p + 1) - np.maximum(x0 * s, p), 0, 1)
            cy = np.clip(np.minimum(y1 * s, p + 1) - np.maximum(y0 * s, p), 0, 1)
            return cy[:, None] * cx[None, :]
        return self._canvas(lambda d: d.rounded_rectangle([x0 * s, y0 * s, max(x0 * s, x1 * s - 1), max(y0 * s, y1 * s - 1)],
                                                          radius=r * s, fill=255))

    def circle(self, cx, cy, r, r_in=None):
        s = self.ss
        Y, X = np.mgrid[0:self.N, 0:self.N]
        d = np.hypot((X + 0.5) / s - cx, (Y + 0.5) / s - cy)
        m = (d <= r).astype(float)
        if r_in is not None:
            m *= (d > r_in)
        return m

    def poly(self, pts):
        s = self.ss
        return self._canvas(lambda d: d.polygon([(x * s, y * s) for x, y in pts], fill=255))

    def line(self, pts, w):
        s = self.ss
        return self._canvas(lambda d: d.line([(x * s, y * s) for x, y in pts], fill=255, width=max(1, int(round(w * s))), joint='curve'))

    def xy(self):
        Y, X = np.mgrid[0:self.N, 0:self.N]
        return (X + 0.5) / self.ss, (Y + 0.5) / self.ss

    # ---- materials

    def material(self, name):
        if name not in self._mats:
            base, var, streak, spec, gloss = MATERIALS[name]
            n = self.N
            col = hexc(base)
            f = 1 + var * fbm(n, self.rng, base=6, octaves=4)
            if streak:
                f = f + streak * fbm(n, self.rng, base=24, octaves=3, stretch=(12, 1))
            A = col[None, None, :] * f[..., None]
            if name.startswith('copper'):
                # oxidised blotches
                ox = fbm(n, self.rng, base=5, octaves=3)
                A = A * (1 - 0.12 * np.clip(ox, 0, 1)[..., None])
            if name.startswith(('oak', 'walnut')):
                # wood: long grain, with the darker late-wood lines of the rings
                vertical = name.endswith('_v')
                st = (1, 14) if vertical else (14, 1)
                g = fbm(n, self.rng, base=20, octaves=3, stretch=st)
                X, Y = np.meshgrid(np.arange(n), np.arange(n))
                across = (X if vertical else Y) / self.ss
                warp = fbm(n, self.rng, base=3, octaves=2, stretch=st) * 6
                rings = np.sin((across + warp) * 1.9) > 0.75
                A = col[None, None, :] * (1 + 0.10 * g)[..., None] * (1 - 0.16 * rings)[..., None]
            if name == 'obsidian':
                sp = fbm(n, self.rng, base=30, octaves=2)
                A = A + (np.clip(sp - 0.45, 0, 1) * 2.0)[..., None] * hexc('#6a3aa0')[None, None, :]
            if name == 'paper':
                fib = fbm(n, self.rng, base=40, octaves=2, stretch=(3, 1))
                A = A * (1 + 0.02 * fib)[..., None]
            self._mats[name] = (np.clip(A, 0, 255), spec, gloss)
        return self._mats[name]

    def paint(self, mask, name, tint=None):
        A, spec, gloss = self.material(name)
        if tint is not None:
            A = A * (hexc(tint) / 255.0)[None, None, :]
        m = mask[..., None]
        self.A = self.A * (1 - m) + A * m
        self.S = self.S * (1 - mask) + spec * mask
        self.G = self.G * (1 - mask) + gloss * mask
        self.M[mask > 0.5] = name

    def colour(self, mask, c, spec=0.3, gloss=20):
        m = mask[..., None]
        self.A = self.A * (1 - m) + hexc(c)[None, None, :] * m
        self.S = self.S * (1 - mask) + spec * mask
        self.G = self.G * (1 - mask) + gloss * mask

    def fill(self, name):
        self.paint(np.ones((self.N, self.N)), name)

    # ---- height

    def ramp(self, mask, bevel):
        """0 at the mask's edge rising to 1 'bevel' texture pixels inside it."""
        b = max(1, int(round(bevel * self.ss)))
        im = Image.fromarray((mask * 255).astype(np.uint8), 'L')
        from PIL import ImageFilter
        acc = np.zeros((self.N, self.N))
        cur = im
        for _ in range(b):
            acc += np.asarray(cur, float) / 255.0
            cur = cur.filter(ImageFilter.MinFilter(3))
        return np.clip(acc / b, 0, 1)

    def level(self, mask, h, bevel=1.0, mat=None, tint=None):
        """Set the surface inside mask to height h, with a bevelled edge."""
        w = self.ramp(mask, bevel) if bevel > 0 else mask
        self.H = self.H * (1 - w) + h * w
        if mat:
            self.paint(mask, mat, tint)

    def lift(self, mask, h, bevel=1.0, mat=None, tint=None):
        """Raise (or with negative h, sink) the surface inside mask by h."""
        w = self.ramp(mask, bevel) if bevel > 0 else mask
        self.H = self.H + h * w
        if mat:
            self.paint(mask, mat, tint)

    def dome(self, cx, cy, r, h, mat=None, tint=None):
        X, Y = self.xy()
        d = np.hypot(X - cx, Y - cy)
        inside = d < r
        prof = np.sqrt(np.clip(1 - (d / r) ** 2, 0, 1)) * h
        base = self.H.copy()
        self.H = np.where(inside, np.maximum(self.H, base.max() * 0 + self._local(cx, cy) + prof), self.H)
        if mat:
            self.paint(inside.astype(float), mat, tint)
        return inside.astype(float)

    def _local(self, cx, cy):
        x = int(min(self.N - 1, max(0, cx * self.ss)))
        y = int(min(self.N - 1, max(0, cy * self.ss)))
        return self.H[y, x]

    def bulge_y(self, x0, y0, x1, y1, h):
        """A horizontal cylinder (bulging toward the viewer across its height), e.g. a paper drum."""
        X, Y = self.xy()
        inside = (X >= x0) & (X < x1) & (Y >= y0) & (Y < y1)
        yc, half = (y0 + y1) / 2, (y1 - y0) / 2
        prof = np.sqrt(np.clip(1 - ((Y - yc) / half) ** 2, 0, 1)) * h
        self.H = np.where(inside, self.H + prof, self.H)

    def screw(self, cx, cy, r=1.6, mat='steel', slot=True, angle=None):
        self.dome(cx, cy, r, r * 0.9, mat)
        if slot:
            a = self.rng.random() * math.pi if angle is None else angle
            dx, dy = math.cos(a) * r * 0.75, math.sin(a) * r * 0.75
            m = self.line([(cx - dx, cy - dy), (cx + dx, cy + dy)], 0.45)
            self.H -= m * 0.5
            self.A = self.A * (1 - 0.35 * m[..., None])

    # ---- emissive

    def emit(self, mask, c, a=1.0):
        m = mask * a
        self.E = self.E * (1 - m[..., None]) + hexc(c)[None, None, :] * m[..., None]
        self.Ea = np.maximum(self.Ea, m)

    def emit_glow(self, mask, c, radius, strength=0.5, clip=None):
        """A soft halo of light around a shape (inside 'clip' if given)."""
        g = np.clip(blur(mask, radius * self.ss) * strength, 0, 1)
        if clip is not None:
            g = g * clip
        g = g * (1 - mask)
        self.E = self.E * (1 - g[..., None]) + hexc(c)[None, None, :] * g[..., None]
        self.Ea = np.maximum(self.Ea, g)

    # ---- weathering

    def weather(self, patina=0.5, grime=0.5, wear=0.4):
        cav = blur(self.H, 3 * self.ss) - self.H
        cav = np.clip(cav * 0.6, 0, 1)
        n = fbm(self.N, self.rng, base=8, octaves=4)
        copper = np.isin(self.M, ['copper', 'copper_light', 'copper_dark'])
        if patina:
            p = np.clip((cav * 1.6 + n * 0.8 - 0.35) * patina * 2, 0, 1) * copper
            pc = hexc('#5d9e88') * (1 + 0.08 * n)[..., None]
            self.A = self.A * (1 - p[..., None]) + pc * p[..., None]
        if grime:
            g = np.clip(cav * 1.2 * grime, 0, 0.5)
            self.A = self.A * (1 - g[..., None])
        if wear:
            # bright, worn metal on raised edges
            gy, gx = np.gradient(self.H)
            slope = np.clip(np.hypot(gx, gy) * 0.8, 0, 1)
            edge = np.clip(slope * (n * 0.5 + 0.7) * wear, 0, 0.5) * copper
            self.A = self.A * (1 - edge[..., None]) + hexc('#e9a67a')[None, None, :] * edge[..., None]

    # ---- output

    def render(self):
        gy, gx = np.gradient(self.H)
        nrm = np.stack([-gx * 1.6, -gy * 1.6, np.ones_like(self.H)], -1)
        nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True)
        diff = np.clip(nrm @ LIGHT, 0, None)
        shade = 0.40 + 0.60 * diff / LIGHT[2]
        cav = np.clip((blur(self.H, 2 * self.ss) - self.H) * 0.35, 0, 0.55)
        spec = self.S * np.clip(nrm @ HALF, 0, 1) ** self.G
        col = self.A * shade[..., None] * (1 - cav)[..., None] + spec[..., None] * np.array([255, 248, 236])
        col = col * (1 - self.Ea[..., None]) + self.E * self.Ea[..., None]
        # glow halos (emit_glow) only add light
        col = np.clip(col, 0, 255)
        n, s = self.n, self.ss
        small = col.reshape(n, s, n, s, 3).mean(axis=(1, 3))
        ea = self.Ea.reshape(n, s, n, s).mean(axis=(1, 3))
        img = np.concatenate([small, np.full((n, n, 1), 255.0)], -1).round().astype(np.uint8)
        glow = img.copy()
        glow[ea < 0.35] = 0
        return img, glow
