"""Paints the textures (and writes the models) for the weather station, weather radar, lightning battery and
seismograph.

    python3 tools/block_textures/paint.py [--sheet preview.png]

Each texture is 16x16 pixel art drawn from character grids plus a little procedural shading. Screens, charge
cells and lamps also get a "glow" texture: the same pixels on a transparent background, drawn by a second,
full-bright layer in the block model so they light up at night.
"""
import json
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'reikos_weather')
TEX = os.path.join(ROOT, 'textures', 'block')
MODELS = os.path.join(ROOT, 'models', 'block')
OUT = {}  # name -> RGBA array, written at the end


def rgb(h):
    h = h.lstrip('#')
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255]


def grid(rows, pal):
    assert len(rows) == 16, len(rows)
    img = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch != '.':
                img[y, x] = rgb(pal[ch])
    return img


def stamp(img, x0, y0, rows, pal):
    """Draw a small piece of pixel art onto img ('.' = leave as is)."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img[y0 + y, x0 + x] = rgb(pal[ch])


def grain(img, chars_mask, amount, seed):
    """Gentle per-pixel brightness noise on the pixels in chars_mask."""
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            if chars_mask[y][x]:
                f = 1 + (r.random() - 0.5) * 2 * amount
                img[y, x, :3] = np.clip(img[y, x, :3] * f, 0, 255)


def mask_of(rows, chars):
    return [[ch in chars for ch in row] for row in rows]


def glow_of(img, region):
    """The glowing layer: img's pixels inside region (x0, y0, x1, y1 exclusive), transparent elsewhere."""
    g = np.zeros_like(img)
    x0, y0, x1, y1 = region
    g[y0:y1, x0:x1] = img[y0:y1, x0:x1]
    return g


# ---------------------------------------------------------------- palettes

COPPER = {'D': '#4a2214', 'd': '#8a4229', 'm': '#a5552f', 'c': '#c26b47', 'h': '#e2905f', 'R': '#f6c39c',
          'p': '#5aa58c', 'P': '#3f8a73'}
IRON = {'i': '#3b3f45', 'I': '#5d636b', 'j': '#2a2d31'}
BRASS = {'b': '#dcae45', 'B': '#a97f25'}
SCREEN = {'n': '#0e2230', 'N': '#11293a'}


# ---------------------------------------------------------------- weather station

STATION = [
    'DDDDDDDDDDDDDDDD',
    'DRhhhhhhhhhhhhRD',
    'DhcvvvvvvvvvvcdD',
    'DhcVVVVVVVVVVcdD',
    'DhcvvvvvvvvvvcdD',
    'DhcVVVVVVVVVVcdD',
    'DhKKKKKKKKKKKKdD',
    'DhKnnnnnnnnnnkdD',
    'DhKNNNNNNNNNNkdD',
    'DhKnnnnnnnnnnkdD',
    'DhKNNNNNNNNNNkdD',
    'DhKnnnnnnnnnnkdD',
    'DhKNNNNNNNNNNkdD',
    'Dhkkkkkkkkkkkkdd',
    'DRmBbbbBmmgmemRD',
    'DDDDDDDDDDDDDDDD',
]
STATION_PAL = dict(COPPER, **BRASS, **SCREEN, v='#3a1c10', V='#d07a52', K='#4d3b33', k='#231a16',
                   g='#7dff6a', e='#ff5a3a')
SCREEN_AT = (3, 7)  # top-left of the 10x6 display

ICONS = {
    'precipitation': ([
        '...wwww...',
        '..wwwwwwW.',
        '.WWWWWWWW.',
        '..u..u..u.',
        '.u..u..u..',
        'u..u..u...',
    ], {'w': '#e4ecf2', 'W': '#9fb0bf', 'u': '#58b4ff'}),
    'storm': ([
        '..WWWWW...',
        '.WWWWWWWW.',
        'WWWWyyWWWW',
        '....yy....',
        '...yyyy...',
        '.....y....',
    ], {'W': '#6f7f8e', 'y': '#ffe04a'}),
    'wind': ([
        'wwwwwww...',
        '.......w..',
        '..wwwwwwww',
        '.........w',
        'wwwwww..w.',
        '......ww..',
    ], {'w': '#dff4ff'}),
    'weather': ([
        '.y..y.....',
        '..yyy.....',
        'yyyyyww...',
        '..yywwwww.',
        '..wwwwwwww',
        '...WWWWWW.',
    ], {'y': '#ffd23a', 'w': '#f2f6fa', 'W': '#aebdcb'}),
    'season': ([
        '...ggyy...',
        '..gggyyy..',
        '..gggyyy..',
        '..wwwooo..',
        '..wwwooo..',
        '...wwoo...',
    ], {'g': '#72d65a', 'y': '#ffd43c', 'o': '#f0822e', 'w': '#e6f4ff'}),
}


def station():
    rows = STATION
    for mode, (icon, pal) in ICONS.items():
        img = grid(rows, STATION_PAL)
        grain(img, mask_of(rows, 'chm'), 0.06, 7)
        stamp(img, SCREEN_AT[0], SCREEN_AT[1], icon, pal)
        OUT[f'weather_station_side_{mode}'] = img
        OUT[f'weather_station_glow_{mode}'] = glow_of(img, (3, 7, 13, 13))
    top = [
        'DDDDDDDDDDDDDDDD',
        'DRhhhhhhhhhhhhRD',
        'Dhcccccccccccc dD'.replace(' ', ''),
        'Dhccccccccccc pdD'.replace(' ', ''),
        'DhcccchhhhcccpdD',
        'DhccchddddhcccdD',
        'DhcchdvvvvdhccdD',
        'DhcchdvbbvdhccdD',
        'DhcchdvbbvdhccdD',
        'DhcchdvvvvdhccdD',
        'DhccchddddhcccdD',
        'DhPcccmmmmccccdD',
        'DhpPccccccccccdD',
        'DhccccccccccccdD',
        'DRmmmmmmmmmmmmRD',
        'DDDDDDDDDDDDDDDD',
    ]
    img = grid(top, dict(STATION_PAL, b='#2a120a'))
    grain(img, mask_of(top, 'c'), 0.06, 11)
    OUT['weather_station_top'] = img
    bottom = [
        'DDDDDDDDDDDDDDDD',
        'DddddddddddddddD',
        'DdmmmmmmmmmmmmdD',
        'DdmDmmmmmmmmDmdD',
        'DdmmDmmmmmmDmmdD',
        'DdmmmDmmmmDmmmdD',
        'DdmmmmDmmDmmmmdD',
        'DdmmmmmDDmmmmmdD',
        'DdmmmmmDDmmmmmdD',
        'DdmmmmDmmDmmmmdD',
        'DdmmmDmmmmDmmmdD',
        'DdmmDmmmmmmDmmdD',
        'DdmDmmmmmmmmDmdD',
        'DdmmmmmmmmmmmmdD',
        'DddddddddddddddD',
        'DDDDDDDDDDDDDDDD',
    ]
    img = grid(bottom, STATION_PAL)
    grain(img, mask_of(bottom, 'm'), 0.06, 12)
    OUT['weather_station_bottom'] = img


# ---------------------------------------------------------------- weather radar

RADAR_PAL = {'O': '#3e434a', 'w': '#dfe3e7', 'W': '#f5f7f9', 's': '#b7bdc4', '+': '#7d848c',
             'k': '#262a2f', 'K': '#4c535b', 'v': '#2a2e33', 'V': '#9aa1a9',
             'a': '#30343a', 'A': '#e8ecef', 'r': '#e0403a', 'G': '#56e06a',
             # scope
             'n': '#04140a', 'q': '#0f3a1c', 'L': '#9dffad', 'l': '#3fbf5a', 'e': '#2fae4e', 'y': '#e8d23a', 'x': '#ff4a3a'}


def radar():
    side = [
        'OOOOOOOOOOOOOOOO',
        'OWWWWWWWWWWWWWWO',
        'Ow+KKKKKKKKKK+sO',
        'OwKknnnnnnnnkksO',
        'OwKnnnnnnnnnnksO',
        'OwKnnnnnnnnnnksO',
        'OwKnnnnnnnnnnksO',
        'OwKnnnnnnnnnnksO',
        'OwKnnnnnnnnnnksO',
        'OwKnnnnnnnnnnksO',
        'OwKknnnnnnnnkksO',
        'Ow+kkkkkkkkkk+sO',
        'OwwwwwwwwwwwwwsO',
        'OwaAwaAwwwGwrwsO',
        'OsvVvVvVvVvVvVsO',
        'OOOOOOOOOOOOOOOO',
    ]
    img = grid(side, RADAR_PAL)
    grain(img, mask_of(side, 'ws'), 0.035, 21)
    # the radar scope (10x8 at 3,3): range ring, crosshair, the sweep and a storm echo off to the south-east
    stamp(img, 3, 3, [
        'kqqqqqqqqk',
        'q...c..LLq',
        'q...c.Ll.q',
        'qccccXl..q',
        'q...c..g.q',
        'q.g.c.gyyq',
        'q...c..yrq',
        'kqqqqqqqqk',
    ], {'k': '#262a2f', 'q': '#17592e', 'c': '#0d3018', 'L': '#a8ffb6', 'l': '#2f9a4a', 'X': '#d2ffda',
        'g': '#3ad06a', 'y': '#e8d23a', 'r': '#ff4a3a'})
    OUT['weather_radar_side'] = img
    OUT['weather_radar_glow_side'] = glow_of(img, (3, 3, 13, 11))
    # the radome, from above: a white dome of panels on a steel base, the lightning rod in the middle
    top = np.zeros((16, 16, 4), np.uint8)
    base = [
        'OOOOOOOOOOOOOOOO',
        'O+sssssssssss+sO',
        'Osssssssssssss sO'.replace(' ', ''),
    ]
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            top[y, x] = rgb('#3e434a' if edge else '#aeb4bb')
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        top[y, x] = rgb('#6c737b')
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 6.9:
                # lit from the top-left: brighter toward that side, darker toward the rim
                lum = 0.93 + 0.07 * (-(dx + dy) / 10) - 0.10 * (r / 7) ** 2
                col = np.array(rgb('#eef1f3')[:3]) * lum
                # panel seams: rings and spokes
                if abs(r - 4.0) < 0.5 or (r > 1.5 and (abs(dx) < 0.5 or abs(dy) < 0.5)):
                    col = col * 0.86
                top[y, x, :3] = np.clip(col, 0, 255)
                top[y, x, 3] = 255
            elif r <= 7.6:
                top[y, x] = rgb('#7d848c')
    stamp(top, 7, 7, ['hh', 'cd'], {'h': '#f0a070', 'c': '#c46c4a', 'd': '#8a4229'})
    top[6, 7] = rgb('#ff4a3a')  # aviation light
    OUT['weather_radar_top'] = top
    glow = np.zeros_like(top)
    glow[6, 7] = top[6, 7]
    OUT['weather_radar_glow_top'] = glow
    bottom = [
        'OOOOOOOOOOOOOOOO',
        'O+sssssssssss+sO',
        'OsKsssssssssKssO',
        'OssKssssssssKssO'.replace('OssKssssssssKssO', 'OssKsssssssKsssO'),
        'OsssKsssssKssssO',
        'OssssKsssKsssssO',
        'OsssssKsKssssssO',
        'OssssssKsssssssO',
        'OsssssKsKssssssO',
        'OssssKsssKsssssO',
        'OsssKsssssKssssO',
        'OssKsssssssKsssO',
        'OsKsssssssssKssO',
        'OssssssssssssssO',
        'O+sssssssssss+sO',
        'OOOOOOOOOOOOOOOO',
    ]
    img = grid(bottom, RADAR_PAL)
    grain(img, mask_of(bottom, 's'), 0.04, 23)
    OUT['weather_radar_bottom'] = img


# ---------------------------------------------------------------- lightning battery

BAT_PAL = dict(COPPER, **IRON, F='#141c22', s='#0c1318', o='#1a252d', O='#2a3a46',
               e='#4fc8ff', L='#dcfbff', W='#ffffff', t='#5d636b')


def battery():
    def cells(charge):
        rows = [
            'DIIIIIIIIIIIIIID',
            'DiiiiFFFFFFiiiiD',
        ]
        for n in (4, 3, 2, 1):
            for k in range(2):
                lit = n <= charge
                inner = ('eLLe' if k == 0 else 'eLLe') if lit else ('Oooo' if k == 0 else 'oooo')
                rows.append('Dhc' + ('t' if k == 0 else 'c') + 'cF' + inner + 'Fc' + ('t' if k == 0 else 'c') + 'cdD')
            if n > 1:
                rows.append('DhcccFssssFcccdD')
        rows += ['DiiiiFFFFFFiiiiD', 'DjjjjjjjjjjjjjjD', 'DDDDDDDDDDDDDDDD']
        rows = ['DDDDDDDDDDDDDDDD'] + rows[:14] + ['DDDDDDDDDDDDDDDD']
        return rows

    for charge in range(5):
        rows = cells(charge)
        img = grid(rows, BAT_PAL)
        grain(img, mask_of(rows, 'c'), 0.06, 31)
        if charge:
            # a spark crackling in the topmost charged cell
            r = random.Random(charge)
            y = [i for i, row in enumerate(rows) if 'eLLe' in row][0]
            img[y, 7 + r.randrange(2)] = rgb('#ffffff')
        OUT[f'lightning_battery_side_{charge}'] = img
        if charge:
            g = np.zeros_like(img)
            for y, row in enumerate(rows):
                for x, ch in enumerate(row):
                    if ch in 'LeW' and 5 < x < 10:
                        g[y, x] = img[y, x]
            OUT[f'lightning_battery_glow_{charge}'] = g
    front = [
        'DDDDDDDDDDDDDDDD',
        'DIIIIIIIIIIIIIID',
        'DiiiiiiiiiiiiiiD',
        'DhcjjjjjjjjjjcdD',
        'DhcjjjjjjjyyjcdD',
        'DhcjjjjjjyyjjcdD',
        'DhcjjjjjyyjjjcdD',
        'DhcjjjjyyyyyjcdD',
        'DhcjjjjjjyyjjcdD',
        'DhcjjjjjyyjjjcdD',
        'DhcjjjjyyjjjjcdD',
        'DhcjjjjyjjjjjcdD',
        'DhcjjjjjjjjjjcdD',
        'DhcrRrccccKkKcdD',
        'DjjjjjjjjjjjjjjD',
        'DDDDDDDDDDDDDDDD',
    ]
    pal = dict(BAT_PAL, y='#ffd23a', r='#9a1e18', R='#ff5a4a', K='#1c1c1c', k='#5a5a5a')
    img = grid(front, pal)
    grain(img, mask_of(front, 'c'), 0.06, 33)
    OUT['lightning_battery_front'] = img
    top = [
        'DDDDDDDDDDDDDDDD',
        'DIIIIIIIIIIIIIID',
        'DItccccccccccticD'.replace('DItccccccccccticD', 'DItcccccccccctiD'),
        'DicccccccccccciD',
        'DicccccwwcccccjD',
        'DiccccwWWwccccjD',
        'DicccwWggWwcccjD',
        'DiccwWghhgWwccjD',
        'DiccwWghdgWwccjD',
        'DicccwWggWwcccjD',
        'DiccccwWWwccccjD',
        'DicccccwwcccccjD',
        'DiccccccccccccjD',
        'DitjjjjjjjjjjtjD',
        'DjjjjjjjjjjjjjjD',
        'DDDDDDDDDDDDDDDD',
    ]
    img = grid(top, dict(BAT_PAL, w='#a9a49b', W='#efebe4', g='#cfc9be', h='#f0a070', d='#a5552f'))
    grain(img, mask_of(top, 'c'), 0.06, 35)
    OUT['lightning_battery_top'] = img


# ---------------------------------------------------------------- seismograph

SEIS_PAL = dict(COPPER, **BRASS, S='#a3a3a3', s='#8c8c8c', T='#bdbdbd', X='#727272',
                f='#f1e8cf', F='#d2c6a6', g='#ded3b3', k='#2a2a36', r='#c22a24', a='#5a3f86', A='#c79cff')


def seismograph():
    def front(active):
        rows = [
            'DDDDDDDDDDDDDDDD',
            'DRhhhhhhhhhhhhRD',
            'DhFFFFFFFFFFFFdD',
            'DhfgffgffgffgfdD',
            'DhfgffgffgffgfdD',
            'DhfgffgffgffgfdD',
            'DhfgffgffgffgfdD',
            'DhfgffgffgffgfdD',
            'DhFFFFFFFFFFFFdD',
            'DRmmmmmmmmmmmmRD',
            'DddddddddddddddD',
            'TTTTTTTTTTTTTTTT',
            'SSSSSSSaaSSSSSSS',
            'SsSSSSSaaSSSSSsS',
            'SSSSSSSSSSSSSSSS',
            'XXXXXXXXXXXXXXXX',
        ]
        img = grid(rows, SEIS_PAL)
        grain(img, mask_of(rows, 'm'), 0.06, 41)
        grain(img, mask_of(rows, 'S'), 0.03, 42)
        # the pen trace across the paper drum (rows 3-7, columns 2-13)
        # the pen trace across the paper drum (rows 3-7, columns 2-13): a flat line, or tremor spikes
        ink = rgb('#2a2a36')
        for x in range(2, 14):
            img[5, x] = ink
        if active:
            for x, lo, hi in ((5, 4, 6), (7, 3, 7), (8, 4, 6), (9, 3, 7), (11, 4, 6)):
                for y in range(lo, hi + 1):
                    img[y, x] = ink
        else:
            img[4, 7] = ink
        trace = [5] * 12
        # the stylus arm, coming down from the right to the pen tip
        tip = trace[-3]
        for (x, y) in ((13, 2), (12, 3), (12, 4)):
            img[y, x] = rgb('#dcae45')
        img[min(7, max(3, tip)), 11] = rgb('#1a1a1a')
        if active:
            img[12:14, 7:9] = rgb('#c79cff')
            img[12, 7] = rgb('#f0dcff')
        OUT['seismograph_front_active' if active else 'seismograph_front'] = img
        if active:
            OUT['seismograph_glow'] = glow_of(img, (7, 12, 9, 14))

    front(False)
    front(True)
    side = [
        'DDDDDDDDDDDDDDDD',
        'DRhhhhhhhhhhhhRD',
        'DhccccccccccccdD',
        'DhccccBBBBccccdD',
        'DhcccBffffBcccdD',
        'DhcccBfkfffBccdD'.replace('DhcccBfkfffBccdD', 'DhccBffkfffBccdD'),
        'DhccBfffkffBccdD'.replace('DhccBfffkffBccdD', 'DhccBfffkfBcccdD'),
        'DhcccBffffBcccdD',
        'DhccccBBBBccccdD',
        'DRmmmmmmmmmmmmRD',
        'DddddddddddddddD',
        'TTTTTTTTTTTTTTTT',
        'SSSSSSSSSSSSSSSS',
        'SsSSSSSSSSSSSSsS',
        'SSSSSSSSSSSSSSSS',
        'XXXXXXXXXXXXXXXX',
    ]
    img = grid(side, SEIS_PAL)
    grain(img, mask_of(side, 'cm'), 0.06, 43)
    grain(img, mask_of(side, 'S'), 0.03, 44)
    OUT['seismograph_side'] = img
    top = [
        'DDDDDDDDDDDDDDDD',
        'DRhhhhhhhhhhhhRD',
        'DhGGGGGGGGGGGGdD',
        'DhGgGGGbGGGGGGdD',
        'DhGGgGGbGGGGGGdD',
        'DhGGGGGbGGGGGGdD',
        'DhGGGGGbGGGGGGdD',
        'DhGGGGiIIiGGGGdD',
        'DhGGGiIAaIiGGGdD',
        'DhGGGiIaAIiGGGdD',
        'DhGGGGiiiiGGGGdD',
        'DhGGGGGGGGGgGGdD',
        'DhGGGGGGGGGGgGdD',
        'DhGGGGGGGGGGGGdD',
        'DRmmmmmmmmmmmmRD',
        'DDDDDDDDDDDDDDDD',
    ]
    img = grid(top, dict(SEIS_PAL, **IRON, G='#3c5058', g='#86a4b0'))
    grain(img, mask_of(top, 'h'), 0.05, 45)
    OUT['seismograph_top'] = img


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
    return out


def main(argv):
    station()
    radar()
    battery()
    seismograph()
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
        S, cols = 128, 7
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
