"""Paints the textures (and writes the models) for the weather station, weather radar, lightning battery and
seismograph.

    python3 tools/block_textures/paint.py [--sheet preview.png]

The textures are 64x64 and rendered by hd.py: each face is laid out as raised and sunken panels, rivets,
screws, louvres, glass and dials with real materials (brushed copper with patina, brass, painted steel,
porcelain, paper, smooth stone), then lit. Screens, charge cells and lamps also get a "glow" texture: the same
pixels on a transparent background, drawn by a second, full-bright layer in the block model so they light up
at night.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from hd import Face, fbm, hexc  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'reikos_weather')
TEX = os.path.join(ROOT, 'textures', 'block')
MODELS = os.path.join(ROOT, 'models', 'block')
OUT = {}  # name -> RGBA array, written at the end


def save(name, face, glow_name=None):
    img, glow = face.render()
    OUT[name] = img
    if glow_name:
        OUT[glow_name] = glow


# ---------------------------------------------------------------- shared bits

SEGMENTS = {'0': 'abcdef', '1': 'bc', '2': 'abged', '3': 'abgcd', '4': 'fgbc', '5': 'afgcd', '6': 'afgedc',
            '7': 'abc', '8': 'abcdefg', '9': 'abcdfg', 'S': 'afgcd', 'P': 'abefg', 'C': 'adef', '-': 'g'}


def digits(f, text, x, y, w, h, t, on, off='#10262f'):
    """Seven-segment readout; unlit segments show faintly, like a real LCD."""
    for ch in text:
        segs = {
            'a': (x + t, y, x + w - t, y + t), 'd': (x + t, y + h - t, x + w - t, y + h),
            'g': (x + t, y + h / 2 - t / 2, x + w - t, y + h / 2 + t / 2),
            'f': (x, y + t, x + t, y + h / 2 - t / 2), 'b': (x + w - t, y + t, x + w, y + h / 2 - t / 2),
            'e': (x, y + h / 2 + t / 2, x + t, y + h - t), 'c': (x + w - t, y + h / 2 + t / 2, x + w, y + h - t),
        }
        lit = SEGMENTS.get(ch, '')
        for k, r in segs.items():
            f.emit(f.rect(*r), on if k in lit else off)
        x += w + t * 1.8


def screen_bg(f, x0, y0, x1, y1, r, top='#0b1e2a', bottom='#06121a'):
    """Backlit glass: a soft gradient with faint scanlines."""
    X, Y = f.xy()
    m = f.rect(x0, y0, x1, y1, r)
    t = np.clip((Y - y0) / (y1 - y0), 0, 1)
    col = hexc(top)[None, None, :] * (1 - t[..., None]) + hexc(bottom)[None, None, :] * t[..., None]
    col = col * (1 - 0.10 * ((np.floor(Y * 2) % 2) == 0))[..., None]
    f.E = f.E * (1 - m[..., None]) + col * m[..., None]
    f.Ea = np.maximum(f.Ea, m)
    return m


def reflection(f, mask, x0, x1, strength=0.10):
    """A diagonal sheen across glass."""
    X, Y = f.xy()
    band = ((X + Y * 0.6) > x0) & ((X + Y * 0.6) < x1)
    m = mask * band
    f.E = f.E * (1 - m[..., None] * strength) + 255 * m[..., None] * strength
    f.A = f.A * (1 - m[..., None] * strength) + 255 * m[..., None] * strength


def cloud(f, cx, cy, s, c1, c2):
    m = np.clip(f.circle(cx - 5 * s, cy + 1 * s, 3.2 * s) + f.circle(cx - 1 * s, cy - 1.6 * s, 4.3 * s)
                + f.circle(cx + 4 * s, cy + 0.4 * s, 3.4 * s) + f.rect(cx - 7.5 * s, cy + 0.5 * s, cx + 7.4 * s, cy + 4.4 * s, 2 * s), 0, 1)
    X, Y = f.xy()
    under = m * np.clip((Y - (cy + 0.5 * s)) / (4 * s), 0, 1)
    f.emit(m, c1)
    f.emit(under, c2, 0.8)
    return m


def plate(f, mat, h=3.0, inner=1.6, rivets=True, rivet_mat='copper_light'):
    f.fill(mat)
    f.level(f.rect(0, 0, 64, 64), h, 1.4)
    f.level(f.rect(4, 4, 60, 60, 1.5), h - inner, 1.2)
    if rivets:
        for x, y in ((2.2, 2.2), (61.8, 2.2), (2.2, 61.8), (61.8, 61.8), (32, 2.2), (32, 61.8), (2.2, 32), (61.8, 32)):
            f.dome(x, y, 1.35, 1.3, rivet_mat)


# ---------------------------------------------------------------- weather station

def icon_precipitation(f):
    cloud(f, 21, 35.5, 1.0, '#e6eef4', '#93a6b7')
    for i in range(5):
        x = 15 + i * 3.2
        f.emit(f.line([(x, 41.5), (x - 1.6, 46.5)], 0.9), '#5ab8ff')
    digits(f, '64', 35, 33, 5, 10, 1.1, '#a9ecff')
    f.emit(f.poly([(50.5, 34.5), (48.6, 38.5), (52.4, 38.5)]) + f.circle(50.5, 39, 1.95), '#5ab8ff')


def icon_storm(f):
    cloud(f, 21, 34.5, 1.0, '#8a99a8', '#4c5a68')
    bolt = f.poly([(22.5, 37), (17.5, 43.5), (21, 43.5), (18.5, 48.5), (26, 41), (22.5, 41), (25.5, 37)])
    f.emit(bolt, '#ffe14a')
    f.emit_glow(bolt, '#ffcf3a', 2.0, 0.6)
    digits(f, '3', 36, 33, 5, 10, 1.1, '#ffd25a')
    for i, (h, c) in enumerate(((3, '#ffe14a'), (5.5, '#ff9a2a'), (8, '#ff4030'))):
        f.emit(f.rect(45 + i * 2.6, 43 - h, 46.8 + i * 2.6, 43), c)


def icon_wind(f):
    for (y, x0, x1, curl) in ((33, 12, 27, 1), (38.5, 14, 31, -1), (44, 12, 25, 1)):
        pts = [(x, y) for x in np.arange(x0, x1, 0.5)]
        r = 2.2
        cx, cy = x1, y - r * curl
        for k in range(0, 200, 15):
            a = math.radians(k)
            pts.append((cx + r * math.sin(a), cy + r * curl * math.cos(a)))
        f.emit(f.line(pts, 1.0), '#dff6ff')
    digits(f, '12', 35, 33, 5, 10, 1.1, '#a9ecff')
    f.emit(f.line([(48.5, 43), (52, 39.5)], 0.9) + f.poly([(52.8, 38.6), (49.8, 39.2), (52.2, 41.6)]), '#a9ecff')


def icon_weather(f):
    sun = f.circle(16.5, 35, 4.2)
    for k in range(8):
        a = math.radians(k * 45)
        f.emit(f.line([(16.5 + math.cos(a) * 5.4, 35 + math.sin(a) * 5.4), (16.5 + math.cos(a) * 7.2, 35 + math.sin(a) * 7.2)], 0.9), '#ffd23a')
    f.emit(sun, '#ffcc33')
    f.emit_glow(sun, '#ffb020', 2.5, 0.5)
    cloud(f, 24, 41, 0.85, '#f2f6fa', '#a9b8c6')
    digits(f, '07', 35, 33, 5, 10, 1.1, '#a9ecff')


def icon_season(f):
    cx, cy, r = 21, 39, 8
    X, Y = f.xy()
    disc = f.circle(cx, cy, r)
    quads = (((X >= cx) & (Y < cy), '#ffd43c'), ((X < cx) & (Y < cy), '#72d65a'),
             ((X < cx) & (Y >= cy), '#e6f4ff'), ((X >= cx) & (Y >= cy), '#f0822e'))
    for q, c in quads:
        f.emit(disc * q, c)
    f.emit(f.rect(cx - 0.45, cy - r, cx + 0.45, cy + r) * disc + f.rect(cx - r, cy - 0.45, cx + r, cy + 0.45) * disc, '#0b1e2a')
    digits(f, 'SP', 35, 33, 5, 10, 1.1, '#a9ecff')


ICONS = {'precipitation': icon_precipitation, 'storm': icon_storm, 'wind': icon_wind,
         'weather': icon_weather, 'season': icon_season}


def louvres(f, x0, y0, x1, y1, n, mat):
    f.level(f.rect(x0, y0, x1, y1, 1), 0.2, 0.8, 'iron_dark')
    X, Y = f.xy()
    step = (y1 - y0) / n
    for i in range(n):
        a = y0 + i * step + 0.4
        b = a + step * 0.78
        m = (X >= x0 + 0.6) & (X < x1 - 0.6) & (Y >= a) & (Y < b)
        f.H = np.where(m, 0.2 + 2.6 * (1 - (Y - a) / (b - a)), f.H)
        f.paint(m.astype(float), mat)


def station():
    for k, (mode, icon) in enumerate(ICONS.items()):
        f = Face(seed=100)  # same casing on every mode; only the screen changes
        plate(f, 'copper')
        louvres(f, 8, 7, 56, 23, 4, 'copper')
        f.level(f.rect(7, 26, 57, 52, 3), 2.8, 1.2, 'iron')
        f.level(f.rect(9.5, 28.5, 54.5, 49.5, 2), 1.4, 0.8, 'glass')
        scr = screen_bg(f, 9.5, 28.5, 54.5, 49.5, 2)
        icon(f)
        reflection(f, scr, 46, 52, 0.07)
        f.level(f.rect(9, 54, 38, 59.5, 0.8), 2.4, 0.6, 'brass')
        f.colour(f.rect(11, 55.6, 25, 56.5), '#6e5222', 0.2)
        f.colour(f.rect(11, 57.3, 33, 58.1), '#6e5222', 0.2)
        for x, c in ((46.5, '#6cff5a'), (52, '#ff5a3a')):
            f.dome(x, 56.8, 1.7, 1.3, 'rubber')
            led = f.circle(x, 56.8, 1.05)
            f.emit(led, c)
            f.emit_glow(led, c, 1.2, 0.5)
        f.weather(patina=0.45, grime=0.5, wear=0.4)
        save(f'weather_station_side_{mode}', f, f'weather_station_glow_{mode}')

    f = Face(seed=101)
    plate(f, 'copper')
    X, Y = f.xy()
    cx, cy = 32, 26
    d = np.hypot(X - cx, Y - cy)
    f.level(f.circle(cx, cy, 15.5), 4.2, 1.2, 'brass')
    cone = d < 13
    f.H = np.where(cone, 4.2 - 5.0 * (1 - d / 13), f.H)
    f.paint(cone.astype(float), 'copper_light')
    hole = d < 3.6
    f.H = np.where(hole, -2.5, f.H)
    f.paint(hole.astype(float), 'iron_dark')
    for i in range(-3, 4):
        f.colour(f.rect(cx + i * 1.1 - 0.2, cy - 3.6, cx + i * 1.1 + 0.2, cy + 3.6) * hole, '#8b929a', 0.4)
        f.colour(f.rect(cx - 3.6, cy + i * 1.1 - 0.2, cx + 3.6, cy + i * 1.1 + 0.2) * hole, '#8b929a', 0.4)
    f.level(f.rect(9, 45, 55, 59, 1), 2.6, 0.8, 'iron')
    f.level(f.rect(10.5, 46.5, 53.5, 57.5, 0.5), 2.3, 0.4, 'solar')
    for x in np.arange(10.5 + 43 / 6, 53, 43 / 6):
        f.colour(f.rect(x - 0.25, 46.5, x + 0.25, 57.5), '#8995ad', 0.6)
    f.colour(f.rect(10.5, 51.8, 53.5, 52.2), '#8995ad', 0.6)
    for x, y in ((8, 8), (56, 8), (8, 40), (56, 40)):
        f.screw(x, y, 1.7)
    f.weather(patina=0.55, grime=0.5, wear=0.4)
    save('weather_station_top', f)

    f = Face(seed=102)
    plate(f, 'copper_dark', rivets=False)
    for a, b in (((6, 6), (58, 58)), ((58, 6), (6, 58))):
        f.level(f.line([a, b], 4.5), 2.6, 1.0, 'iron')
    f.dome(32, 32, 6, 2.5, 'iron')
    f.level(f.circle(32, 32, 2.6), 1.0, 0.6, 'rubber')
    for x, y in ((7.5, 7.5), (56.5, 7.5), (7.5, 56.5), (56.5, 56.5)):
        f.dome(x, y, 4.6, 2.8, 'rubber')
    f.weather(patina=0.25, grime=0.7, wear=0.2)
    save('weather_station_bottom', f)


# ---------------------------------------------------------------- weather radar

def radar():
    f = Face(seed=200)
    f.fill('paint_white')
    f.level(f.rect(0, 0, 64, 64), 3, 1.2)
    f.level(f.rect(3, 3, 61, 61, 1.5), 2.2, 0.8)
    for x, y in ((5, 5), (59, 5), (5, 59), (59, 59)):
        f.screw(x, y, 1.4)
    f.level(f.rect(6.5, 7, 57.5, 43, 5), 3.6, 1.4, 'iron_dark')
    f.level(f.rect(9.5, 10, 54.5, 40, 4), 2.0, 1.0, 'glass')
    scr = screen_bg(f, 9.5, 10, 54.5, 40, 4, '#03140a', '#020b06')
    X, Y = f.xy()
    cx, cy, R = 32, 25, 13.5
    d = np.hypot(X - cx, Y - cy)
    disc = (d <= R) * scr
    # phosphor glow, brightest in the middle
    g = np.clip(1 - d / R, 0, 1)
    col = hexc('#0b3418')[None, None, :] * g[..., None] + hexc('#03120a')[None, None, :] * (1 - g[..., None])
    f.E = f.E * (1 - disc[..., None]) + col * disc[..., None]
    for rr in (4.5, 9, 13.5):
        f.emit(f.circle(cx, cy, rr + 0.3, rr - 0.3) * scr, '#1f7a3c')
    f.emit((f.rect(cx - 0.25, cy - R, cx + 0.25, cy + R) + f.rect(cx - R, cy - 0.25, cx + R, cy + 0.25)) * disc, '#16592c')
    # the sweep and its fading trail (turning clockwise; the trail lags behind it)
    ang = np.degrees(np.arctan2(-(Y - cy), X - cx))
    lead = 40.0
    lag = (ang - lead) % 360
    trail = disc * (lag < 75) * (1 - lag / 75) ** 2
    f.E = f.E * (1 - trail[..., None] * 0.6) + hexc('#3cff6a')[None, None, :] * trail[..., None] * 0.6
    a = math.radians(lead)
    f.emit(f.line([(cx, cy), (cx + math.cos(a) * R, cy - math.sin(a) * R)], 0.6) * disc, '#c8ffd2')
    # a storm off to the south-east, coloured by how heavy the rain is
    n = fbm(f.N, f.rng, base=10, octaves=3)
    blob = np.exp(-(((X - 40) / 5.5) ** 2 + ((Y - 30.5) / 4.0) ** 2))
    inten = np.clip(blob * 1.25 + n * 0.35, 0, 1) * disc
    for lo, c in ((0.35, '#2fd35a'), (0.6, '#f0d23a'), (0.8, '#ff3b30')):
        f.emit((inten > lo).astype(float), c)
    clutter = (np.clip(n, 0, 1) > 0.45) * (d < 5) * disc
    f.emit(clutter.astype(float), '#2fae4e', 0.7)
    f.emit(f.circle(cx, cy, 0.8), '#d2ffda')
    digits(f, '40', 12, 12, 1.8, 3.4, 0.5, '#3cc864', '#0a2412')
    reflection(f, scr, 50, 57, 0.06)
    # controls: two knobs, a toggle, status lights
    for x in (13, 22):
        f.dome(x, 49.5, 3.3, 2.6, 'rubber')
        f.colour(f.line([(x, 49.5), (x + 1.8, 47.6)], 0.5), '#e8eaec', 0.3)
    f.level(f.rect(28.5, 46.5, 34, 52.5, 0.6), 3.4, 0.5, 'steel')
    f.level(f.line([(31.2, 49.5), (32.6, 46.8)], 1.1), 5.5, 0.4, 'steel')
    for x, c in ((41, '#6cff5a'), (46, '#ffb43a'), (51, '#6cff5a')):
        f.dome(x, 49.5, 1.6, 1.2, 'rubber')
        led = f.circle(x, 49.5, 1.0)
        f.emit(led, c)
        f.emit_glow(led, c, 1.2, 0.45)
    for i in range(5):
        f.lift(f.rect(8 + i * 10, 55.5, 15 + i * 10, 59, 1), -1.3, 0.5, 'iron_dark')
    f.weather(patina=0, grime=0.45, wear=0)
    save('weather_radar_side', f, 'weather_radar_glow_side')

    # the radome from above: a white dome of panels on a steel base, the lightning rod at the top
    f = Face(seed=201)
    f.fill('steel')
    f.level(f.rect(0, 0, 64, 64), 2.5, 1.0)
    for x, y in ((4.5, 4.5), (59.5, 4.5), (4.5, 59.5), (59.5, 59.5)):
        f.dome(x, y, 2.0, 1.6, 'iron')
    X, Y = f.xy()
    d = np.hypot(X - 32, Y - 32)
    R = 28
    dome = d < R
    f.H = np.where(dome, 2.5 + 18 * np.sqrt(np.clip(1 - (d / R) ** 2, 0, 1)), f.H)
    f.paint(dome.astype(float), 'paint_white')
    ang = np.degrees(np.arctan2(Y - 32, X - 32)) % 60
    seams = dome * ((np.abs(d - R * 0.42) < 0.35) | (np.abs(d - R * 0.74) < 0.35) | ((np.minimum(ang, 60 - ang) < 0.9 * 12 / np.maximum(d, 1)) & (d > R * 0.42)))
    f.H -= seams * 0.4
    f.A = f.A * (1 - 0.14 * seams[..., None])
    f.dome(32, 32, 2.8, 2.5, 'copper_light')
    f.dome(32, 27.2, 1.5, 1.2, 'red')
    beacon = f.circle(32, 27.2, 1.0)
    f.emit(beacon, '#ff4a3a')
    f.emit_glow(beacon, '#ff3020', 1.5, 0.5)
    f.weather(patina=0, grime=0.35, wear=0)
    save('weather_radar_top', f, 'weather_radar_glow_top')

    f = Face(seed=202)
    f.fill('steel')
    f.level(f.rect(0, 0, 64, 64), 3, 1.2)
    f.level(f.rect(4, 4, 60, 60, 1.5), 1.8, 1.0)
    for a, b in (((6, 6), (58, 58)), ((58, 6), (6, 58)), ((32, 4), (32, 60)), ((4, 32), (60, 32))):
        f.level(f.line([a, b], 3.0), 2.6, 0.8, 'iron')
    for x, y in ((7, 7), (57, 7), (7, 57), (57, 57), (32, 32)):
        f.dome(x, y, 2.4, 1.8, 'steel')
    f.level(f.rect(40, 12, 52, 20, 1.5), 3.0, 0.8, 'rubber')
    f.weather(patina=0, grime=0.6, wear=0)
    save('weather_radar_bottom', f)


# ---------------------------------------------------------------- lightning battery

def battery_body(f):
    plate(f, 'copper')
    f.level(f.rect(0, 0, 64, 9), 4.2, 1.2, 'iron')
    f.level(f.rect(0, 55, 64, 64), 4.2, 1.2, 'iron')
    for x in (6, 18, 46, 58):
        for y in (4.5, 59.5):
            f.dome(x, y, 1.9, 1.5, 'steel')


def battery():
    for charge in range(5):
        f = Face(seed=300)
        battery_body(f)
        f.level(f.rect(20.5, 10.5, 43.5, 53.5, 2.5), 4.4, 1.2, 'iron_dark')
        f.level(f.rect(24, 12.5, 40, 51.5, 1.5), 2.0, 0.8, 'glass')
        X, Y = f.xy()
        for i in range(4):
            y1 = 51.5 - i * 9.75
            y0 = y1 - 8.75
            cell = f.rect(24, y0, 40, y1)
            if i < charge:
                # plasma: a white-hot core fading to blue at the glass
                t = np.clip(np.abs(X - 32) / 8, 0, 1)
                core = hexc('#f2feff')[None, None, :] * (1 - t[..., None]) ** 2 + hexc('#3e9ae6')[None, None, :] * (1 - (1 - t[..., None]) ** 2)
                f.E = f.E * (1 - cell[..., None]) + core * cell[..., None]
                f.Ea = np.maximum(f.Ea, cell)
                rng = np.random.default_rng(31 + i * 7 + charge)
                for _ in range(2):
                    pts, x = [], 25 + rng.random() * 3
                    y = y0 + 1 + rng.random() * (y1 - y0 - 2)
                    while x < 39:
                        pts.append((x, y))
                        x += 1.2 + rng.random() * 1.5
                        y = float(np.clip(y + rng.normal() * 1.4, y0 + 0.8, y1 - 0.8))
                    f.emit(f.line(pts, 0.45) * cell, '#ffffff')
            else:
                f.colour(cell * (np.abs(X - 26.5) < 0.6), '#2c3c48', 0.9)
            for yy in (y0 + 0.2, y1 - 1.4):
                f.level(f.rect(29.5, yy, 34.5, yy + 1.2, 0.4), 2.6, 0.3, 'steel')
                if i < charge:
                    f.emit(f.rect(29.5, yy, 34.5, yy + 1.2, 0.4), '#c9d6e0')
            if i < 3:
                f.level(f.rect(24, y0 - 1.0, 40, y0), 3.2, 0.3, 'iron_dark')
            lamp = f.circle(48.5, (y0 + y1) / 2, 1.1)
            f.dome(48.5, (y0 + y1) / 2, 1.8, 1.3, 'rubber')
            if i < charge:
                f.emit(lamp, '#7fe0ff')
                f.emit_glow(lamp, '#5fc8ff', 1.2, 0.45)
            f.lift(f.rect(13.5, (y0 + y1) / 2 - 0.35, 18.5, (y0 + y1) / 2 + 0.35), -0.5, 0)
        glass = f.rect(24, 12.5, 40, 51.5, 1.5)
        reflection(f, glass, 33, 37, 0.10)
        f.weather(patina=0.45, grime=0.5, wear=0.4)
        save(f'lightning_battery_side_{charge}', f, f'lightning_battery_glow_{charge}' if charge else None)

    f = Face(seed=301)
    battery_body(f)
    f.level(f.rect(8, 11.5, 56, 46.5, 2), 2.6, 1.0, 'iron_dark')
    for x, y in ((10.5, 14), (53.5, 14), (10.5, 44), (53.5, 44)):
        f.screw(x, y, 1.2)
    f.level(f.poly([(32, 13.5), (48.5, 41.5), (15.5, 41.5)]), 3.0, 0.5, 'black')
    f.level(f.poly([(32, 17), (45.2, 39.4), (18.8, 39.4)]), 3.2, 0.4, 'yellow')
    f.level(f.poly([(33.6, 21.5), (27.2, 31.5), (31.4, 31.5), (29.4, 37.5), (36.8, 27), (32.6, 27), (35, 21.5)]), 3.3, 0.2, 'black')
    for a, b in ((18, 46), (22, 42)):
        pass
    f.colour(f.rect(17, 42.6, 47, 43.6), '#d9b23a', 0.2)
    f.colour(f.rect(21, 44.4, 43, 45.2), '#d9b23a', 0.2)
    for x, mat, sign in ((21, 'red', '+'), (43, 'black', '-')):
        f.dome(x, 50.5, 4.2, 3.0, mat)
        f.dome(x, 50.5, 2.2, 2.0, 'brass')
        f.colour(f.rect(x + 5.5, 50.1, x + 8, 50.9), '#f0e0c0', 0.2)
        if sign == '+':
            f.colour(f.rect(x + 6.35, 49.25, x + 7.15, 51.75), '#f0e0c0', 0.2)
    f.weather(patina=0.45, grime=0.5, wear=0.4)
    save('lightning_battery_front', f)

    f = Face(seed=302)
    f.fill('iron')
    f.level(f.rect(0, 0, 64, 64), 4.2, 1.2)
    f.level(f.rect(5, 5, 59, 59, 1.5), 2.6, 1.0, 'copper')
    X, Y = f.xy()
    d = np.hypot(X - 32, Y - 32)
    ins = d < 13.5
    f.H = np.where(ins, 3.5 + 4.5 * (1 - d / 13.5) + 0.7 * np.cos(d * 2 * math.pi / 2.7), f.H)
    f.paint(ins.astype(float), 'porcelain')
    f.dome(32, 32, 3.2, 3.2, 'copper_light')
    for x, y in ((10, 10), (54, 10), (10, 54), (54, 54)):
        f.dome(x, y, 2.6, 2.0, 'steel')
        f.colour(f.circle(x, y, 1.0), '#4a4f56', 0.3)
    f.level(f.circle(50, 21, 3), 3.8, 0.6, 'brass')
    f.weather(patina=0.5, grime=0.5, wear=0.4)
    save('lightning_battery_top', f)


# ---------------------------------------------------------------- seismograph

def stone_base(f, y0):
    f.level(f.rect(0, y0, 64, 64), 3.2, 1.0, 'stone')
    f.lift(f.rect(1.5, y0 + 1.5, 62.5, 62.5), -0.5, 0.6)
    f.lift(f.rect(0, 52.5, 64, 53.3), -0.7, 0)


def amethyst(f, cx, cy, active):
    f.level(f.circle(cx, cy, 5.2), 4.4, 0.8, 'brass')
    facets = [
        ([(cx, cy - 4), (cx + 3.2, cy - 1.5), (cx, cy)], '#d6b4ff'),
        ([(cx + 3.2, cy - 1.5), (cx + 3.2, cy + 1.8), (cx, cy)], '#a072e0'),
        ([(cx + 3.2, cy + 1.8), (cx, cy + 4), (cx, cy)], '#7a4cc0'),
        ([(cx, cy + 4), (cx - 3.2, cy + 1.8), (cx, cy)], '#6a3eac'),
        ([(cx - 3.2, cy + 1.8), (cx - 3.2, cy - 1.5), (cx, cy)], '#8c5cd2'),
        ([(cx - 3.2, cy - 1.5), (cx, cy - 4), (cx, cy)], '#bb92f4'),
    ]
    gem = np.zeros((f.N, f.N))
    for pts, c in facets:
        m = f.poly(pts)
        gem = np.clip(gem + m, 0, 1)
        if active:
            f.emit(m, c)
        else:
            f.colour(m, c, 0.8, 40)
            f.A = f.A * (1 - 0.35 * m[..., None])
    f.lift(gem, 1.2, 0.4)
    if active:
        f.emit_glow(gem, '#b98cff', 2.0, 0.55)


def seismograph():
    for active in (False, True):
        f = Face(seed=400)
        f.fill('copper')
        f.level(f.rect(0, 0, 64, 41), 3, 1.2)
        f.level(f.rect(4, 4, 60, 37, 2), 3.8, 1.0, 'brass')
        f.level(f.rect(6.5, 6.5, 57.5, 34.5, 1), 1.5, 0.6, 'paper')
        f.bulge_y(6.5, 6.5, 57.5, 34.5, 4.5)
        X, Y = f.xy()
        drum = f.rect(6.5, 6.5, 57.5, 34.5, 1)
        grid = np.zeros((f.N, f.N))
        for x in np.arange(8, 57, 3.5):
            grid += f.rect(x - 0.18, 6.5, x + 0.18, 34.5)
        for y in np.arange(9, 34, 3.5):
            grid += f.rect(6.5, y - 0.18, 57.5, y + 0.18)
        grid = np.clip(grid, 0, 1) * drum
        f.A = f.A * (1 - 0.45 * grid[..., None]) + hexc('#8fb0d8')[None, None, :] * 0.45 * grid[..., None]
        f.colour(f.rect(6.5, 30.6, 57.5, 31.0) * drum, '#d0645a', 0.05)
        rng = np.random.default_rng(5 if active else 3)
        pts = []
        for x in np.arange(7.5, 50.5, 0.35):
            env = 0.35 + (9.0 * math.exp(-((x - 33) / 6.5) ** 2) if active else 0.4 * math.exp(-((x - 30) / 4) ** 2))
            pts.append((x, 20.5 + env * math.sin(x * 3.1 + rng.normal() * 0.6) * (0.6 + 0.4 * rng.random())))
        ink = f.line(pts, 0.55) * drum
        f.colour(ink, '#1e1e2a', 0.1)
        f.level(f.line([(58.5, 6), (50.6, 20.2)], 1.3), 7.5, 0.4, 'brass')
        f.dome(58.5, 6, 2.2, 2.0, 'brass')
        f.dome(50.4, 20.6, 1.1, 1.0, 'black')
        reflection(f, drum, 12, 20, 0.08)
        f.level(f.rect(0, 37.5, 64, 41), 3.6, 0.6, 'brass')
        stone_base(f, 41)
        amethyst(f, 32, 47, active)
        f.level(f.rect(10, 56.5, 24, 60, 0.6), 3.6, 0.4, 'brass')
        f.colour(f.rect(11.5, 57.8, 22.5, 58.6), '#6e5222', 0.2)
        f.weather(patina=0.4, grime=0.45, wear=0.35)
        save('seismograph_front_active' if active else 'seismograph_front', f, 'seismograph_glow' if active else None)

    f = Face(seed=401)
    f.fill('copper')
    f.level(f.rect(0, 0, 64, 41), 3, 1.2)
    for x, y in ((3, 3), (61, 3), (3, 35), (61, 35)):
        f.dome(x, y, 1.35, 1.3, 'copper_light')
    f.level(f.circle(32, 19, 13.5), 4.6, 1.0, 'brass')
    f.level(f.circle(32, 19, 11.2), 3.0, 0.8, 'cream')
    for k in range(13):
        a = math.radians(225 - k * 22.5)
        r0 = 8.6 if k % 3 else 7.6
        f.colour(f.line([(32 + math.cos(a) * r0, 19 - math.sin(a) * r0), (32 + math.cos(a) * 10.3, 19 - math.sin(a) * 10.3)], 0.45), '#2a2a30', 0.1)
    f.colour(f.poly([(32, 19), (32 + math.cos(math.radians(150)) * 8, 19 - math.sin(math.radians(150)) * 8), (32.4, 19.6)]) +
             f.line([(32, 19), (32 + math.cos(math.radians(150)) * 9, 19 - math.sin(math.radians(150)) * 9)], 0.55), '#a01818', 0.3)
    f.dome(32, 19, 1.4, 1.2, 'brass')
    reflection(f, f.circle(32, 19, 11.2), 30, 36, 0.10)
    f.level(f.rect(21, 34, 43, 37.5, 0.6), 3.6, 0.4, 'brass')
    f.level(f.rect(0, 37.5, 64, 41), 3.6, 0.6, 'brass')
    stone_base(f, 41)
    f.weather(patina=0.45, grime=0.45, wear=0.35)
    save('seismograph_side', f)

    f = Face(seed=402)
    plate(f, 'copper')
    glass = f.rect(7, 7, 57, 57, 2)
    # what's under the glass: the drum along the front, and the heavy pendulum
    f.paint(glass, 'paint_grey', tint='#8c949c')
    f.colour(f.rect(9, 45.5, 55, 54.5, 1) * glass, '#e8dfc4', 0.1)
    for x in np.arange(11, 55, 3.5):
        f.colour(f.rect(x - 0.15, 45.5, x + 0.15, 54.5) * glass, '#b9c6d6', 0.1)
    f.colour(f.line([(14, 12), (31, 31)], 1.6), '#d0a24a', 0.5)
    f.colour(f.circle(32, 33, 9.5), '#6c727a', 0.4)
    f.colour(f.circle(32, 33, 9.5, 8.2), '#454a51', 0.4)
    f.colour(f.circle(32, 33, 6.5, 6.0), '#8a9098', 0.4)
    f.colour(f.circle(14, 12, 2.4), '#d9aa4e', 0.5)
    f.colour(f.circle(32, 33, 3.4), '#a477e0', 0.6)
    f.colour(f.circle(31.2, 32.2, 1.2), '#d8bcff', 0.6)
    f.A = f.A * (1 - 0.12 * glass[..., None])
    f.level(glass, 1.6, 0.8)
    f.S = f.S * (1 - glass) + 0.85 * glass
    f.G = f.G * (1 - glass) + 60 * glass
    reflection(f, glass, 18, 26, 0.12)
    reflection(f, glass, 30, 32, 0.08)
    for x in (20, 44):
        f.level(f.rect(x - 3, 4.5, x + 3, 8.5, 0.5), 3.4, 0.4, 'brass')
    f.weather(patina=0.45, grime=0.5, wear=0.4)
    save('seismograph_top', f)


# ---------------------------------------------------------------- tornado siren

def hazard(f, mask, width=4.0):
    X, Y = f.xy()
    stripe = (np.floor((X + Y) / width) % 2) == 0
    f.colour(mask * stripe, '#e3b12a', 0.25, 20)
    f.colour(mask * (~stripe), '#1d1d1f', 0.25, 20)


def siren():
    for on in (False, True):
        f = Face(seed=500)
        f.fill('paint_grey')
        f.level(f.rect(0, 0, 64, 64), 3, 1.2)
        f.level(f.rect(3, 3, 61, 61, 1.5), 2.2, 0.8)
        band = f.rect(3, 4, 61, 10.5)
        f.level(band, 2.7, 0.5)
        hazard(f, band, 4)
        cx, cy = 32, 37
        f.level(f.circle(cx, cy, 21), 3.8, 1.2, 'steel')
        X, Y = f.xy()
        d = np.hypot(X - cx, Y - cy)
        horn = d < 18.5
        f.H = np.where(horn, 3.0 - 4.5 * (1 - d / 18.5) + 1.0 * np.abs(np.sin(d * math.pi / 3.1)), f.H)
        f.paint(horn.astype(float), 'iron')
        throat = d < 4.6
        f.H = np.where(throat, -3.0, f.H)
        f.paint(throat.astype(float), 'iron_dark')
        f.level(f.rect(cx - 0.9, cy - 19, cx + 0.9, cy + 19) * horn, 4.4, 0.4, 'steel')
        f.level(f.rect(cx - 19, cy - 0.9, cx + 19, cy + 0.9) * horn, 4.4, 0.4, 'steel')
        f.dome(cx, cy, 2.4, 2.0, 'steel')
        for k in range(8):
            a = math.radians(k * 45 + 22.5)
            f.screw(cx + math.cos(a) * 19.8, cy + math.sin(a) * 19.8, 1.0)
        for x in (8.5, 55.5):
            f.level(f.circle(x, 15.5, 4.4), 3.6, 0.6, 'steel')
            lens = f.dome(x, 15.5, 3.5, 3.2, 'red')
            if on:
                f.emit(lens, '#ff4030')
                f.emit(f.circle(x - 1.0, 14.4, 1.0), '#ffd0c0')
                f.emit_glow(lens, '#ff3020', 2.0, 0.6)
            else:
                f.A = f.A * (1 - 0.35 * lens[..., None])
        for x, w in ((24, 4), (29, 3), (33, 4), (38, 3)):
            f.colour(f.rect(x, 58.6, x + w, 59.6), '#3b3f44', 0.2)
        f.weather(patina=0, grime=0.5, wear=0)
        save('tornado_siren_side_on' if on else 'tornado_siren_side', f, 'tornado_siren_glow' if on else None)

    f = Face(seed=501)
    f.fill('paint_grey')
    f.level(f.rect(0, 0, 64, 64), 3, 1.2)
    X, Y = f.xy()
    d = np.hypot(X - 32, Y - 32)
    ang = np.arctan2(Y - 32, X - 32)
    f.level(f.circle(32, 32, 26), 4.0, 1.2, 'steel')
    fan = d < 23
    f.H = np.where(fan, 0.5, f.H)
    f.paint(fan.astype(float), 'iron_dark')
    blades = fan * (d > 4) * (np.sin(6 * (ang + d * 0.05)) > 0.25)
    f.H = f.H + blades * 1.4
    f.paint(blades.astype(float), 'iron')
    for r in (6, 11, 16, 21):
        f.level(f.circle(32, 32, r + 0.5, r - 0.5) * fan, 3.2, 0.3, 'steel')
    for a in range(0, 360, 45):
        t = math.radians(a)
        f.level(f.line([(32 + math.cos(t) * 4, 32 + math.sin(t) * 4), (32 + math.cos(t) * 23, 32 + math.sin(t) * 23)], 0.9), 3.2, 0.3, 'steel')
    f.dome(32, 32, 4.5, 3.0, 'steel')
    for x, y in ((5, 5), (59, 5), (5, 59), (59, 59)):
        f.screw(x, y, 1.6)
    f.weather(patina=0, grime=0.55, wear=0)
    save('tornado_siren_top', f)

    f = Face(seed=502)
    f.fill('steel')
    f.level(f.rect(0, 0, 64, 64), 3, 1.2)
    f.level(f.rect(4, 4, 60, 60, 1.5), 2.0, 1.0)
    for x, y in ((9, 9), (55, 9), (9, 55), (55, 55)):
        f.dome(x, y, 5, 2.6, 'rubber')
    f.level(f.circle(32, 32, 6), 3.2, 0.8, 'iron')
    f.level(f.circle(32, 32, 3), 1.0, 0.5, 'rubber')
    f.weather(patina=0, grime=0.7, wear=0)
    save('tornado_siren_bottom', f)


# ---------------------------------------------------------------- storm probe (one texture holding every part)

def tube(f, x0, y0, x1, y1, across_x):
    """A strip of galvanised tube, shaded round across its width."""
    X, Y = f.xy()
    m = f.rect(x0, y0, x1, y1)
    t = ((X - x0) / (x1 - x0)) if across_x else ((Y - y0) / (y1 - y0))
    prof = np.sqrt(np.clip(1 - (2 * t - 1) ** 2, 0, 1))
    f.paint(m, 'galvanized')
    f.H = np.where(m > 0, 1.0 + 3.0 * prof, f.H)


def probe():
    f = Face(seed=600)
    f.fill('galvanized')
    # A (0,0)-(24,16): the sensor housing's sides: white louvres and a label
    f.paint(f.rect(0, 0, 24, 16), 'paint_white')
    f.level(f.rect(0, 0, 24, 16), 2.0, 0.6)
    X, Y = f.xy()
    for i in range(4):
        a, b = 1.5 + i * 2.6, 1.5 + i * 2.6 + 2.0
        m = (X >= 1.5) & (X < 22.5) & (Y >= a) & (Y < b)
        f.H = np.where(m, 1.0 + 1.8 * (1 - (Y - a) / (b - a)), f.H)
    f.level(f.rect(4, 12.2, 20, 14.8, 0.4), 2.4, 0.3, 'brass')
    f.colour(f.rect(5.5, 13.2, 16, 13.8), '#6e5222', 0.2)
    # B (24,0)-(48,24): its top and bottom
    f.paint(f.rect(24, 0, 48, 24), 'paint_white')
    f.level(f.rect(24, 0, 48, 24), 2.0, 0.8)
    f.level(f.rect(25.5, 1.5, 46.5, 22.5, 1), 1.6, 0.6)
    for x, y in ((27.5, 3.5), (44.5, 3.5), (27.5, 20.5), (44.5, 20.5)):
        f.screw(x, y, 0.9)
    f.level(f.circle(36, 12, 3), 0.6, 0.5, 'iron_dark')
    # C (48,0)-(52,32): vertical tube; D (0,32)-(32,34): horizontal tube
    tube(f, 48, 0, 52, 32, True)
    tube(f, 0, 32, 32, 34, False)
    for y in (6, 26):
        f.lift(f.rect(48, y, 52, y + 0.6), -0.6, 0)
    # E (52,0)-(58,6): the anemometer cups, cupped and dark
    X, Y = f.xy()
    dc = np.hypot(X - 55, Y - 3)
    cup = (X >= 52) & (X < 58) & (Y >= 0) & (Y < 6)
    f.paint(cup.astype(float), 'iron')
    f.H = np.where(cup, 3.0 - 2.0 * np.sqrt(np.clip(1 - (dc / 3) ** 2, 0, 1)), f.H)
    # F (56,8)-(60,12): tube ends
    f.paint(f.rect(56, 8, 60, 12), 'steel')
    f.dome(58, 10, 2, 1.2, 'steel')
    f.weather(patina=0, grime=0.4, wear=0)
    save('storm_probe', f)

    for k, (c1, c2) in enumerate((('#2a2622', '#1a1715'), ('#ffc23a', '#d9821a'), ('#5aff7a', '#1fae4a'))):
        f = Face(seed=610 + k)
        X, Y = f.xy()
        d = np.hypot(X - 32, Y - 32)
        t = np.clip(d / 45, 0, 1)
        col = hexc(c1)[None, None, :] * (1 - t[..., None]) + hexc(c2)[None, None, :] * t[..., None]
        rings = (np.sin(d * 0.9) > 0.6)
        col = col * (1 - 0.12 * rings[..., None])
        if k:
            f.E = col
            f.Ea = np.ones((f.N, f.N))
            f.emit(f.circle(24, 22, 7), '#ffffff', 0.5)
        else:
            f.A = col
            f.S[:] = 0.9
            f.G[:] = 60
            f.H = 6 * np.sqrt(np.clip(1 - (d / 46) ** 2, 0, 1))
        save(f'storm_probe_lamp_{k}', f)


# ---------------------------------------------------------------- weather altar

def runes(f, cx, cy, r, n, size, colour, rng_seed):
    """A ring of small glyphs around (cx, cy), each a few strokes, glowing."""
    rng = np.random.default_rng(rng_seed)
    out = np.zeros((f.N, f.N))
    strokes = [((0, 0), (1, 0)), ((0, 0), (0, 1)), ((1, 0), (1, 1)), ((0, 1), (1, 1)), ((0, 0), (1, 1)),
               ((1, 0), (0, 1)), ((0.5, 0), (0.5, 1)), ((0, 0.5), (1, 0.5))]
    for k in range(n):
        a = 2 * math.pi * k / n - math.pi / 2
        gx, gy = cx + math.cos(a) * r - size / 2, cy + math.sin(a) * r - size / 2
        for i in rng.choice(len(strokes), 3, replace=False):
            (ax, ay), (bx, by) = strokes[i]
            out += f.line([(gx + ax * size, gy + ay * size * 1.3), (gx + bx * size, gy + by * size * 1.3)], 0.55)
    out = np.clip(out, 0, 1)
    f.emit(out, colour)
    f.emit_glow(out, colour, 1.5, 0.4)
    return out


def altar():
    f = Face(seed=700)
    f.fill('obsidian')
    f.level(f.rect(0, 0, 64, 64), 4, 1.2, 'gold')
    f.level(f.rect(4, 4, 60, 60, 1), 2.6, 1.0, 'obsidian')
    X, Y = f.xy()
    # engraved border pattern on the gold frame
    for i in range(8):
        for (x, y) in ((4 + i * 8 - 2, 2), (4 + i * 8 - 2, 62)):
            f.lift(f.poly([(x, y - 1), (x + 1, y), (x, y + 1), (x - 1, y)]), -0.5, 0)
    for (x, y) in ((4, 4), (60, 4), (4, 60), (60, 60)):
        f.dome(x, y, 2.4, 2.2, 'diamond')
    # crying obsidian tears running down from the top
    rng = np.random.default_rng(71)
    tears = np.zeros((f.N, f.N))
    for _ in range(7):
        x = 7 + rng.random() * 50
        y0, y1 = 5 + rng.random() * 10, 14 + rng.random() * 40
        tears += f.line([(x, y0), (x + rng.normal() * 0.6, y1)], 0.6 + rng.random() * 0.5)
    tears = np.clip(tears, 0, 1) * f.rect(5, 5, 59, 59)
    f.emit(tears, '#7a3ad0', 0.75)
    # the emblem: a gold ring with a storm cloud and lightning inlaid in diamond, and runes around it
    f.level(f.circle(32, 32, 19, 16.5), 3.6, 0.6, 'gold')
    cl = np.clip(f.circle(26, 30, 4.5) + f.circle(31.5, 27, 6) + f.circle(37.5, 30, 4.8) + f.rect(22, 30, 42, 34.5, 2), 0, 1)
    f.level(cl, 3.4, 0.6, 'diamond')
    bolt = f.poly([(32.5, 33), (27.5, 41), (31.5, 41), (29, 47), (37, 38), (33, 38), (35.5, 33)])
    f.level(bolt, 3.4, 0.4, 'gold')
    runes(f, 32, 32, 13.5, 0, 2.2, '#7fe8ff', 1)
    runes(f, 32, 32, 23.5, 12, 2.6, '#7fe8ff', 2)
    f.weather(patina=0, grime=0.35, wear=0)
    save('weather_altar_side', f, 'weather_altar_glow_side')

    f = Face(seed=701)
    f.fill('obsidian')
    f.level(f.rect(0, 0, 64, 64), 4, 1.2, 'gold')
    f.level(f.rect(4, 4, 60, 60, 1), 2.6, 1.0, 'obsidian')
    f.level(f.circle(32, 32, 27, 23.5), 4.0, 0.8, 'gold')
    runes(f, 32, 32, 25.2, 16, 2.0, '#7fe8ff', 3)
    f.level(f.circle(32, 32, 21, 19.5), 3.3, 0.4, 'gold')
    for a in range(4):
        t = math.radians(a * 90 - 90)
        tip = (32 + math.cos(t) * 18, 32 + math.sin(t) * 18)
        l = (32 + math.cos(t + 0.35) * 6, 32 + math.sin(t + 0.35) * 6)
        r = (32 + math.cos(t - 0.35) * 6, 32 + math.sin(t - 0.35) * 6)
        f.level(f.poly([tip, l, (32, 32), r]), 3.6, 0.5, 'diamond' if a % 2 == 0 else 'gold')
    for a in range(4):
        t = math.radians(a * 90 - 45)
        tip = (32 + math.cos(t) * 11, 32 + math.sin(t) * 11)
        l = (32 + math.cos(t + 0.5) * 4, 32 + math.sin(t + 0.5) * 4)
        r = (32 + math.cos(t - 0.5) * 4, 32 + math.sin(t - 0.5) * 4)
        f.level(f.poly([tip, l, (32, 32), r]), 3.5, 0.4, 'gold')
    orb = f.dome(32, 32, 5.5, 5.0, 'diamond')
    X, Y = f.xy()
    d = np.hypot(X - 31, Y - 31)
    col = np.clip(1 - d / 5.5, 0, 1)
    f.E = f.E * (1 - orb[..., None]) + (hexc('#7fe0ff')[None, None, :] * (1 - col[..., None]) + 255 * col[..., None]) * orb[..., None]
    f.Ea = np.maximum(f.Ea, orb)
    f.emit_glow(orb, '#8fe8ff', 3.0, 0.55)
    for (x, y) in ((4, 4), (60, 4), (4, 60), (60, 60)):
        f.dome(x, y, 2.4, 2.2, 'diamond')
    f.weather(patina=0, grime=0.3, wear=0)
    save('weather_altar_top', f, 'weather_altar_glow_top')

    f = Face(seed=702)
    f.fill('obsidian')
    f.level(f.rect(0, 0, 64, 64), 3, 1.2)
    for (x, y) in ((0, 0), (52, 0), (0, 52), (52, 52)):
        f.level(f.rect(x, y, x + 12, y + 12, 1), 4.0, 0.8, 'gold')
    f.weather(patina=0, grime=0.4, wear=0)
    save('weather_altar_bottom', f)


# ---------------------------------------------------------------- icebox

def frost(f, mask, amount=0.8, seed=0):
    """Rime: white-blue crystals collecting where the cold leaks out."""
    n = fbm(f.N, np.random.default_rng(seed), base=24, octaves=3)
    m = np.clip((mask * amount * 0.8 + n * 0.55 - 0.55) * 2.0, 0, 0.8)
    sparkle = (fbm(f.N, np.random.default_rng(seed + 1), base=48, octaves=1) > 0.55) * m
    col = hexc('#dcecf7')
    f.A = f.A * (1 - m[..., None]) + col[None, None, :] * m[..., None]
    f.A = f.A * (1 - sparkle[..., None] * 0.5) + 255 * sparkle[..., None] * 0.5
    f.S = f.S * (1 - m) + 0.6 * m
    f.H = f.H + m * 0.5


def icebox():
    f = Face(seed=800)
    f.fill('oak_v')
    f.level(f.rect(0, 0, 64, 64), 3, 1.0)
    # stiles and rails round a raised panel door
    f.level(f.rect(5, 5, 59, 59, 1), 2.4, 0.8, 'oak_v')
    f.level(f.rect(11, 11, 53, 53, 1), 3.2, 1.6, 'oak')
    f.level(f.rect(15, 15, 49, 49, 1), 3.9, 1.0, 'oak')
    for y in (12, 44):
        f.level(f.rect(1.5, y, 13, y + 6, 1), 4.0, 0.5, 'brass')
        for x in (4, 10):
            f.screw(x, y + 3, 0.9, 'brass')
    f.level(f.rect(51, 26, 59, 38, 1.2), 4.0, 0.5, 'brass')
    f.level(f.rect(53, 22, 56, 42, 1.5), 5.4, 0.6, 'brass')
    f.dome(54.5, 32, 2.0, 1.5, 'brass')
    f.level(f.circle(32, 9, 3.4), 3.8, 0.4, 'brass')
    f.level(f.circle(32, 9, 2.6), 3.0, 0.3, 'cream')
    f.colour(f.line([(32, 9), (30.4, 7.6)], 0.5), '#3a62b0', 0.2)
    X, Y = f.xy()
    seam = np.clip(1 - np.minimum.reduce([np.abs(X - 5), np.abs(X - 59), np.abs(Y - 5), np.abs(Y - 59)]) / 3, 0, 1)
    frost(f, np.clip(seam + np.clip((Y - 50) / 14, 0, 1), 0, 1), 0.9, 11)
    f.weather(patina=0, grime=0.35, wear=0)
    save('icebox_front', f)

    f = Face(seed=801)
    f.fill('oak')
    f.level(f.rect(0, 0, 64, 64), 3, 1.0)
    for i in range(4):
        f.level(f.rect(4, 4 + i * 14, 60, 4 + i * 14 + 13, 0.8), 2.4, 0.8, 'oak')
    for (x, y) in ((0, 0), (52, 0), (0, 52), (52, 52)):
        f.level(f.poly([(x, y), (x + 12, y), (x, y + 12)] if (x, y) == (0, 0) else
                       [(x + 12, y), (x + 12, y + 12), (x, y)] if (x, y) == (52, 0) else
                       [(x, y + 12), (x, y), (x + 12, y + 12)] if (x, y) == (0, 52) else
                       [(x + 12, y + 12), (x, y + 12), (x + 12, y)]), 3.8, 0.6, 'brass')
    for (x, y) in ((3, 3), (61, 3), (3, 61), (61, 61)):
        f.screw(x, y, 0.9, 'brass')
    X, Y = f.xy()
    frost(f, np.clip((Y - 48) / 16, 0, 1), 0.9, 12)
    f.weather(patina=0, grime=0.35, wear=0)
    save('icebox_side', f)

    f = Face(seed=802)
    f.fill('oak')
    f.level(f.rect(0, 0, 64, 64), 3, 1.0, 'brass')
    f.level(f.rect(3, 3, 61, 61, 1), 3.4, 0.8, 'oak')
    for i in range(1, 4):
        f.lift(f.rect(3, 3 + i * 14.5 - 0.4, 61, 3 + i * 14.5 + 0.4), -0.8, 0)
    f.level(f.rect(22, 28, 42, 36, 3), 2.0, 1.0, 'iron_dark')
    f.level(f.rect(24, 30.5, 40, 33.5, 1.5), 4.6, 0.6, 'brass')
    X, Y = f.xy()
    edge = np.clip(1 - np.minimum.reduce([X - 3, 61 - X, Y - 3, 61 - Y]) / 5, 0, 1)
    frost(f, edge, 0.8, 13)
    f.weather(patina=0, grime=0.3, wear=0)
    save('icebox_top', f)


# ---------------------------------------------------------------- rain barrel (laid out for the cauldron model)

def staves(f, wet=False, seed=0):
    rng = np.random.default_rng(seed)
    f.fill('oak_dark_v' if wet else 'oak_v')
    X, Y = f.xy()
    for i in range(8):
        x0 = i * 8
        m = f.rect(x0, 0, x0 + 8, 64)
        f.A = f.A * (1 + (rng.random() - 0.5) * 0.18 * m[..., None])
        t = (X - x0) / 8
        f.H = f.H + m * 1.2 * np.sqrt(np.clip(1 - (2 * t - 1) ** 2, 0, 1))
        f.lift(f.rect(x0 - 0.35, 0, x0 + 0.35, 64), -0.9, 0)


def barrel():
    f = Face(seed=900)
    staves(f, seed=901)
    for y in (10, 42):
        f.level(f.rect(0, y, 64, y + 6), 3.4, 0.6, 'iron')
        for x in range(4, 64, 16):
            f.dome(x, y + 3, 1.3, 1.0, 'steel')
    X, Y = f.xy()
    dirt = np.clip((Y - 50) / 14, 0, 1)
    f.A = f.A * (1 - 0.35 * dirt[..., None])
    f.weather(patina=0, grime=0.45, wear=0)
    save('rain_barrel_side', f)

    f = Face(seed=902)
    f.fill('oak_dark')
    X, Y = f.xy()
    ring = f.rect(0, 0, 64, 64) - f.rect(8, 8, 56, 56)
    f.paint(ring, 'oak')
    f.level(ring, 2.4, 0.6)
    ang = np.arctan2(Y - 32, X - 32)
    seams = ring * (np.abs(np.sin(ang * 16)) < 0.08)
    f.lift(seams, -0.7, 0)
    end = fbm(f.N, np.random.default_rng(3), base=16, octaves=2)
    f.A = f.A * (1 + 0.08 * end * ring)[..., None]
    f.paint(f.rect(8, 8, 56, 56), 'oak_dark_v')
    f.weather(patina=0, grime=0.4, wear=0)
    save('rain_barrel_top', f)

    f = Face(seed=903)
    staves(f, wet=True, seed=904)
    X, Y = f.xy()
    moss = np.clip((Y - 40) / 24 + fbm(f.N, f.rng, base=10, octaves=3) * 0.4, 0, 1) ** 1.5
    f.A = f.A * (1 - moss[..., None] * 0.6) + hexc('#3f5a2a')[None, None, :] * moss[..., None] * 0.6
    f.colour(f.rect(0, 14, 64, 15) * 0.35, '#2a2018', 0.1)
    f.weather(patina=0, grime=0.5, wear=0)
    save('rain_barrel_inner', f)

    f = Face(seed=905)
    f.fill('oak_dark')
    for i in range(5):
        f.level(f.rect(0, i * 12.8 + 0.4, 64, (i + 1) * 12.8 - 0.4), 2.0, 0.6, 'oak_dark')
    f.level(f.rect(28, 0, 36, 64), 3.0, 0.5, 'iron')
    for y in range(6, 64, 13):
        f.dome(32, y, 1.3, 1.0, 'steel')
    f.weather(patina=0, grime=0.5, wear=0)
    save('rain_barrel_bottom', f)


# ---------------------------------------------------------------- forecast board (a clean surface: the forecast is drawn on it)

def board():
    f = Face(seed=1000)
    f.fill('walnut')
    f.level(f.rect(0, 0, 64, 64), 3.2, 0.8)
    panel = f.rect(4, 16, 60, 48)
    f.level(panel, 2.2, 0.7, 'enamel')
    stain = fbm(f.N, f.rng, base=6, octaves=4)
    f.A = f.A * (1 - 0.05 * np.clip(stain, 0, 1) * panel)[..., None]
    for y in (23.3, 35.5):
        f.colour(f.rect(6, y - 0.2, 58, y + 0.2), '#b49a76', 0.2)
    for x, y in ((2, 14), (62, 14), (2, 50), (62, 50)):
        f.screw(x, y, 1.1, 'brass')
    f.weather(patina=0, grime=0.25, wear=0)
    save('forecast_board', f)


# ---------------------------------------------------------------- models

GLOW = {'shade': False, 'neoforge_data': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def model(textures, faces, glow_faces=None):
    """A full cube with `faces` {face: texture key}, plus a full-bright copy for glow_faces."""
    out = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
           'textures': textures, 'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16],
                                              'faces': {f: {'texture': '#' + faces[f], 'cullface': f} for f in FACES}}]}
    if glow_faces:
        el = {'from': [0, 0, 0], 'to': [16, 16, 16]}
        el.update(GLOW)
        el['faces'] = {f: {'texture': '#' + k, 'cullface': f} for f, k in glow_faces.items()}
        out['elements'].append(el)
    return out


def t(name):
    return 'reikos_weather:block/' + name


def models():
    out = {}
    for mode in ICONS:
        side = f'weather_station_side_{mode}'
        out[f'weather_station_{mode}'] = model(
            {'particle': t(side), 'side': t(side), 'top': t('weather_station_top'), 'bottom': t('weather_station_bottom'),
             'glow': t(f'weather_station_glow_{mode}')},
            {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'bottom'},
            {f: 'glow' for f in ('north', 'south', 'east', 'west')})
    out['weather_radar'] = model(
        {'particle': t('weather_radar_side'), 'side': t('weather_radar_side'), 'top': t('weather_radar_top'),
         'bottom': t('weather_radar_bottom'), 'glow': t('weather_radar_glow_side'), 'glow_top': t('weather_radar_glow_top')},
        {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'bottom'},
        dict({f: 'glow' for f in ('north', 'south', 'east', 'west')}, up='glow_top'))
    for charge in range(5):
        tex = {'particle': t(f'lightning_battery_side_{charge}'), 'front': t('lightning_battery_front'),
               'side': t(f'lightning_battery_side_{charge}'), 'top': t('lightning_battery_top')}
        glow = None
        if charge:
            tex['glow'] = t(f'lightning_battery_glow_{charge}')
            glow = {f: 'glow' for f in ('south', 'east', 'west')}
        out[f'lightning_battery_c{charge}'] = model(
            tex, {'north': 'front', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'top'}, glow)
    for active in (False, True):
        tex = {'particle': t('seismograph_side'), 'front': t('seismograph_front_active' if active else 'seismograph_front'),
               'side': t('seismograph_side'), 'top': t('seismograph_top')}
        glow = None
        if active:
            tex['glow'] = t('seismograph_glow')
            glow = {'north': 'glow'}
        out['seismograph_active' if active else 'seismograph'] = model(
            tex, {'north': 'front', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'top'}, glow)
    out['tornado_siren'] = model(
        {'particle': t('tornado_siren_side'), 'side': t('tornado_siren_side'), 'top': t('tornado_siren_top'), 'bottom': t('tornado_siren_bottom')},
        {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'bottom'})
    out['tornado_siren_on'] = model(
        {'particle': t('tornado_siren_side_on'), 'side': t('tornado_siren_side_on'), 'top': t('tornado_siren_top'),
         'bottom': t('tornado_siren_bottom'), 'glow': t('tornado_siren_glow')},
        {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'bottom'},
        {f: 'glow' for f in ('north', 'south', 'east', 'west')})
    out['weather_altar'] = model(
        {'particle': t('weather_altar_side'), 'side': t('weather_altar_side'), 'top': t('weather_altar_top'),
         'bottom': t('weather_altar_bottom'), 'glow': t('weather_altar_glow_side'), 'glow_top': t('weather_altar_glow_top')},
        {'north': 'side', 'south': 'side', 'east': 'side', 'west': 'side', 'up': 'top', 'down': 'bottom'},
        dict({f: 'glow' for f in ('north', 'south', 'east', 'west')}, up='glow_top'))
    for k in range(3):
        out[f'storm_probe_{k}'] = probe_model(k)
    with open(os.path.join(MODELS, 'forecast_board.json')) as fh:
        fb = json.load(fh)
    for el in fb['elements']:
        if el['faces'].get('north', {}).get('texture') == '#board':
            el['faces']['north']['uv'] = [0, 3, 16, 13]
    out['forecast_board'] = fb
    return out


# storm probe parts -> regions of its texture (in 0-16 units)
PROBE_UV = {'housing': [0, 0, 6, 4], 'housing_top': [6, 0, 12, 6], 'tube_v': [12, 0, 13, 8], 'tube_h': [0, 8, 8, 8.5],
            'cup': [13, 0, 14.5, 1.5], 'end': [14, 2, 15, 3]}


def probe_model(k):
    with open(os.path.join(MODELS, 'storm_probe_0.json')) as fh:
        base = json.load(fh)
    els = []
    for e in base['elements']:
        (x1, y1, z1), (x2, y2, z2) = e['from'], e['to']
        dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
        faces = {}
        lamp = any(v.get('texture') == '#lamp' for v in e['faces'].values())
        for f in e['faces']:
            if lamp:
                faces[f] = {'texture': '#lamp', 'uv': [0, 0, 16, 16]}
                continue
            if dx >= 5 and dy >= 3:                       # the sensor housing
                part = 'housing_top' if f in ('up', 'down') else 'housing'
            elif dx <= 1.5 and dz <= 1.5 and dy >= 1.4 and dy <= 2 and min(dx, dz) >= 1:  # cups
                part = 'cup'
            elif dx >= 1 and dz >= 1 and dy <= 2 and dx <= 1.6 and dz <= 1.6:
                part = 'cup'
            elif dy >= 2:                                 # legs and the mast
                part = 'tube_v' if f not in ('up', 'down') else 'end'
            elif dx >= 4:                                 # arm along x
                part = 'tube_h' if f in ('north', 'south', 'up', 'down') else 'end'
            elif dz >= 4:                                 # arm along z
                part = 'tube_h' if f in ('east', 'west') else ('tube_v' if f in ('up', 'down') else 'end')
            else:
                part = 'cup'
            faces[f] = {'texture': '#metal', 'uv': PROBE_UV[part]}
        el = dict(e)
        el['faces'] = faces
        if lamp and k:
            el.update(GLOW)
        els.append(el)
    return {'textures': {'particle': t('storm_probe'), 'metal': t('storm_probe'), 'lamp': t(f'storm_probe_lamp_{k}')},
            'elements': els}


def main(argv):
    station()
    radar()
    battery()
    seismograph()
    siren()
    probe()
    altar()
    icebox()
    barrel()
    board()
    for name, img in OUT.items():
        Image.fromarray(img, 'RGBA').save(os.path.join(TEX, name + '.png'))
    for name, m in models().items():
        with open(os.path.join(MODELS, name + '.json'), 'w') as fh:
            json.dump(m, fh, indent=2)
            fh.write('\n')
    print(f'{len(OUT)} textures')
    if '--sheet' in argv:
        path = argv[argv.index('--sheet') + 1]
        names = [n for n in OUT if 'glow' not in n]
        S, cols = 192, 6
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * (S + 8), rows * (S + 22)), (50, 50, 58, 255))
        d = ImageDraw.Draw(sheet)
        for i, n in enumerate(names):
            x, y = (i % cols) * (S + 8) + 4, (i // cols) * (S + 22) + 4
            sheet.alpha_composite(Image.fromarray(OUT[n], 'RGBA').resize((S, S), Image.NEAREST), (x, y))
            d.text((x, y + S + 2), n.replace('weather_', 'w_').replace('lightning_', 'l_')[:22], fill=(255, 255, 255, 255))
        sheet.save(path)


if __name__ == '__main__':
    main(sys.argv[1:])
