#!/usr/bin/env python3
"""
Draw the plushies: a little family of soft toys a child can collect, place and hear.

Each plush is a handful of boxes (a body, a head, ears, a beak, fins) in a block model, felt-
coloured from a small palette, with a face drawn on the front of its head: big eyes with a
highlight, rosy cheeks, a smile or a beak. Everything is made here from hand-placed numbers, so
the output is the same on every machine.

Writes, for every plush: textures/block/plush_<name>.png (a 64x64 sheet: felt swatches on top,
the face below), models/block/plush_<name>.json, the plush blockstate (by kind and facing), the
plush item definition (by kind), the loot table and the English names.

Pure standard library, deterministic, like generate_textures.py.

Usage: python3 generate_plushies.py
"""

import json
import pathlib
import random

from generate_textures import write_png

HERE = pathlib.Path(__file__).parent
NS = "phonics-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS
DATA = HERE / "src/main/resources/data" / NS

SHEET = 64
UNIT = 4            # texture pixels per model unit, for the face
CELL = 8            # a felt swatch, in texture pixels
CLEAR = (0, 0, 0, 0)
INK = (0x24, 0x1C, 0x1C, 0xFF)
WHITE = (0xFF, 0xFF, 0xFF, 0xFF)
CHEEK = (0xF4, 0x8F, 0xA4, 0xFF)


def rgb(h):
    return ((h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF, 0xFF)


def shade(c, by):
    return tuple(max(0, min(255, int(v * by))) for v in c[:3]) + (c[3],)


# --- Faces -----------------------------------------------------------------------------------

class Face:
    """The front of a head, drawn at UNIT pixels a model unit, w by h units."""

    def __init__(self, w, h, fill):
        self.w, self.h = int(w * UNIT), int(h * UNIT)
        self.px = [[fill] * self.w for _ in range(self.h)]
        self.fill = fill

    def dot(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def disc(self, cx, cy, r, c):
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(max(0, int(y0)), min(self.h, int(y1))):
            for x in range(max(0, int(x0)), min(self.w, int(x1))):
                self.px[y][x] = c

    def eye(self, cx, cy, r=3.2):
        """A big shiny button eye."""
        self.disc(cx, cy, r, INK)
        self.disc(cx - r * 0.35, cy - r * 0.35, max(1.0, r * 0.38), WHITE)
        self.dot(cx + r * 0.35, cy + r * 0.4, (0x5A, 0x4E, 0x4E, 0xFF))

    def big_eye(self, cx, cy, r, ring):
        """An owl's: a pale disc round a button eye."""
        self.disc(cx, cy, r, ring)
        self.eye(cx, cy, r * 0.62)

    def cheeks(self, y, spread):
        mid = self.w / 2
        for side in (-1, 1):
            self.disc(mid + side * spread, y, 1.8, CHEEK)

    def smile(self, y, half, c=INK):
        mid = self.w / 2
        for i in range(-int(half), int(half) + 1):
            dy = 0 if abs(i) < half - 1 else -1
            self.dot(mid + i - 0.5, y + dy, c)

    def pixels(self):
        return self.px


# --- The plushies ----------------------------------------------------------------------------
#
# A box: (from, to, swatch) or (from, to, swatch, {face: swatch-or-"face"}). The model's front is
# north (low z); the blockstate turns it to face whoever placed it. "face" puts the drawn face
# on that side; it must be the head's north side, at most 8 by 8 units.

def mirror(box):
    (x0, y0, z0), (x1, y1, z1) = box[0], box[1]
    return ((16 - x1, y0, z0), (16 - x0, y1, z1)) + tuple(box[2:])


def pair(box):
    return [box, mirror(box)]


def owl():
    c = {"body": rgb(0x9A6A44), "dark": rgb(0x6E4A2E), "belly": rgb(0xF0DDB6), "beak": rgb(0xF2A93B), "wing": rgb(0xB58358)}
    f = Face(8, 8, c["body"])
    f.rect(6, 15, 26, 32, c["belly"])
    for x, y in ((10, 20), (16, 22), (22, 20), (13, 27), (19, 27)):
        f.dot(x, y, shade(c["belly"], 0.78)); f.dot(x + 1, y + 1, shade(c["belly"], 0.78))
    f.big_eye(10, 11, 6.5, c["belly"])
    f.big_eye(22, 11, 6.5, c["belly"])
    for i in range(4):
        f.rect(15 - i * 0.5, 15 + i, 17 + i * 0.5, 16 + i, c["beak"])
    boxes = [((4, 0, 5), (12, 8, 11), "body", {"north": "face"})]
    boxes += pair(((4, 8, 6), (5.5, 10, 8), "dark"))
    boxes += pair(((3.4, 1.5, 6.2), (4, 6.2, 10), "wing"))
    boxes += pair(((5, 0, 4), (7, 0.8, 5), "beak"))
    return c, f, boxes


def duck():
    c = {"body": rgb(0xF7D84A), "dark": rgb(0xE8B92E), "beak": rgb(0xF08A2C)}
    f = Face(6, 5, c["body"])
    f.eye(7, 8, 2.8)
    f.eye(17, 8, 2.8)
    f.cheeks(14, 8.5)
    boxes = [
        ((4, 0, 6.5), (12, 5, 12.5), "body"),
        ((5, 5, 5.5), (11, 10, 10.5), "body", {"north": "face"}),
        ((6.25, 6, 3.6), (9.75, 7.5, 5.5), "beak"),
        ((7, 4.5, 12.5), (9, 6.5, 13.5), "dark"),
    ]
    boxes += pair(((3.5, 1, 7.5), (4, 4, 11.5), "dark"))
    return c, f, boxes


def ant():
    c = {"body": rgb(0x4A3329), "dark": rgb(0x2E201B), "face": rgb(0x6A4A3C), "leg": rgb(0x241915)}
    f = Face(4.5, 4.5, c["face"])
    f.big_eye(5, 7, 4, WHITE)
    f.big_eye(13, 7, 4, WHITE)
    f.cheeks(13, 6.5)
    f.smile(14.5, 2)
    boxes = [
        ((5.75, 1, 2), (10.25, 5.5, 6), "face", {"north": "face"}),
        ((7.25, 2.4, 6), (8.75, 3.4, 6.8), "dark"),
        ((6.5, 1.4, 6.8), (9.5, 4.2, 9.4), "body"),
        ((7.25, 2.2, 9.4), (8.75, 3.2, 10.2), "dark"),
        ((5, 0.5, 10.2), (11, 6.2, 15.2), "body"),
    ]
    # Elbowed antennae, and three legs a side out from the middle, reaching the floor.
    boxes += pair(((6.6, 5.5, 3.4), (7.1, 7.6, 3.9), "leg"))
    boxes += pair(((5.6, 7.1, 2.4), (7.1, 7.6, 3.9), "leg"))
    for z in (7.0, 8.0, 9.0):
        boxes += pair(((4.2, 2.2, z), (6.5, 2.7, z + 0.5), "leg"))
        boxes += pair(((3.7, 0, z), (4.2, 2.7, z + 0.5), "leg"))
    return c, f, boxes


def rat():
    c = {"body": rgb(0xA7A4AE), "dark": rgb(0x86838E), "pink": rgb(0xF2A3B3), "nose": rgb(0xE7768F)}
    f = Face(6, 5, c["body"])
    f.eye(7, 7, 2.6)
    f.eye(17, 7, 2.6)
    f.disc(12, 13.5, 2.2, c["nose"])
    for side in (-1, 1):
        for i in range(4):
            f.dot(12 + side * (5 + i), 14 + (i // 2) * 0 + (1 if i == 3 else 0), shade(c["body"], 0.6))
    boxes = [
        ((4, 0, 6.5), (12, 6, 12.5), "body"),
        ((5, 0.5, 3), (11, 5.5, 6.5), "body", {"north": "face"}),
        ((7.5, 0.4, 12.5), (8.5, 1.4, 15.5), "pink"),
        ((7.5, 1.4, 14.5), (8.5, 3.6, 15.5), "pink"),
    ]
    boxes += pair(((4.2, 5, 4.2), (6.8, 7.8, 5.0), "pink"))
    return c, f, boxes


def ox():
    c = {"body": rgb(0xC07A42), "dark": rgb(0x9A5A2E), "muzzle": rgb(0xF6E2C6), "horn": rgb(0xF4EBD8), "nose": rgb(0x6A3E2A)}
    f = Face(6, 6, c["body"])
    f.rect(2, 13, 22, 24, c["muzzle"])
    f.disc(8, 18.5, 1.3, c["nose"])
    f.disc(16, 18.5, 1.3, c["nose"])
    f.eye(7, 7, 2.6)
    f.eye(17, 7, 2.6)
    boxes = [
        ((4, 0, 6.5), (12, 7, 13.5), "body"),
        ((5, 1.5, 2.5), (11, 7.5, 6.5), "body", {"north": "face"}),
        ((5.5, 7.5, 3.5), (10.5, 8.3, 6), "dark"),
    ]
    boxes += pair(((2.6, 6.6, 3.8), (5, 7.8, 5), "horn"))
    boxes += pair(((2.6, 7.8, 3.8), (3.8, 10.2, 5), "horn"))
    boxes += pair(((4, 5.2, 4.2), (5, 6.2, 5.2), "dark"))
    return c, f, boxes


def shark():
    c = {"body": rgb(0x6F92B3), "dark": rgb(0x55779A), "belly": rgb(0xF2F4F6)}
    f = Face(7, 6, c["body"])
    f.rect(0, 15, 28, 24, c["belly"])
    f.eye(8, 8, 2.6)
    f.eye(20, 8, 2.6)
    f.cheeks(13, 9.5)
    for x in range(8, 20):
        f.dot(x, 17, INK)
    for x in range(9, 20, 3):
        f.dot(x, 18, WHITE); f.dot(x + 1, 18, WHITE)
    boxes = [
        ((4.5, 1.5, 2.5), (11.5, 7.5, 13.5), "body", {"north": "face"}),
        ((4.8, 0, 2.7), (11.2, 1.5, 13.2), "belly"),
        ((7.25, 7.5, 7), (8.75, 10.5, 10.5), "dark"),
        ((7.25, 2.5, 13.5), (8.75, 8.5, 15.5), "dark"),
    ]
    boxes += pair(((3, 1.5, 6), (4.5, 2.5, 8.5), "dark"))
    return c, f, boxes


def crab():
    c = {"body": rgb(0xE8654A), "dark": rgb(0xC24A34), "eye": rgb(0xFFFFFF), "pupil": rgb(0x241C1C)}
    f = Face(8, 5, c["body"])
    f.cheeks(9, 10)
    f.smile(12, 4)
    boxes = [
        ((4, 0.5, 5), (12, 5.5, 11), "body", {"north": "face"}),
    ]
    boxes += pair(((5.6, 5.5, 6.5), (6.4, 7.2, 7.3), "dark"))
    boxes += pair(((5, 7.2, 6), (7, 9.2, 8), "eye"))
    boxes += pair(((5.6, 7.7, 5.7), (6.4, 8.7, 6), "pupil"))
    boxes += pair(((2.6, 1.5, 6.2), (4, 2.7, 7.4), "dark"))
    boxes += pair(((0.8, 1, 3.6), (3.6, 4.4, 6.6), "body"))
    for z in (7.6, 9.0, 10.4):
        boxes += pair(((2.4, 0, z), (4, 0.6, z + 0.5), "dark"))
    return c, f, boxes


def worm():
    c = {"body": rgb(0xF29BAE), "dark": rgb(0xDB7E94)}
    f = Face(6, 6, c["body"])
    f.eye(7, 8, 2.6)
    f.eye(17, 8, 2.6)
    f.cheeks(14, 8.5)
    f.smile(17, 3)
    boxes = [
        ((5, 4.5, 3.5), (11, 10.5, 8), "body", {"north": "face"}),
        ((5.6, 2.8, 4.2), (10.4, 4.5, 7.6), "dark"),
        ((5.4, 0, 4.2), (10.6, 2.8, 8.4), "body"),
        ((5.8, 0, 8.4), (10.2, 2.4, 9.6), "dark"),
        ((6.2, 0, 9.6), (9.8, 2.2, 12.6), "body"),
        ((6.8, 0, 12.6), (9.2, 1.6, 14.6), "dark"),
    ]
    return c, f, boxes


def moth():
    c = {"body": rgb(0xF1E7CF), "dark": rgb(0xCDB58E), "wing": rgb(0xD8C19A), "collar": rgb(0xFBF6EA)}
    f = Face(5, 5, c["body"])
    f.eye(6, 8, 2.6)
    f.eye(14, 8, 2.6)
    f.cheeks(14, 6.5)
    f.smile(16, 2)
    # The wings' soft pattern: a darker edge, two pale-ringed spots and a few freckles.
    p = Face(5, 8, c["wing"])
    p.rect(0, 28, 20, 32, shade(c["wing"], 0.82))
    for y in (8, 20):
        p.disc(10, y, 3.6, c["collar"])
        p.disc(10, y, 2.1, rgb(0x9C7E5A))
    for x, y in ((4, 3), (15, 13), (5, 25), (16, 26)):
        p.dot(x, y, shade(c["wing"], 0.75))
    boxes = [
        ((5.5, 0.8, 2.5), (10.5, 5.8, 6), "body", {"north": "face"}),
        ((5.2, 4, 5.6), (10.8, 6.2, 7.4), "collar"),
        ((6, 0, 6), (10, 4.6, 13.5), "dark"),
        # Folded back along the body, like a little tent, the pattern on top.
        ((3.4, 4.0, 6.5), (8.4, 4.8, 14.5), "wing", {"up": "pattern", "rot": ("z", 22.5, (8, 4.4, 10))}),
        ((7.6, 4.0, 6.5), (12.6, 4.8, 14.5), "wing", {"up": "pattern", "rot": ("z", -22.5, (8, 4.4, 10))}),
    ]
    boxes += pair(((6.6, 5.8, 3.6), (7.2, 8.6, 4.2), "dark"))
    boxes += pair(((5.8, 8.2, 3.4), (7.6, 9.2, 4.4), "dark"))
    return c, f, boxes, p


def yak():
    c = {"body": rgb(0x5C4434), "dark": rgb(0x3F2E23), "face": rgb(0x8A6A52), "horn": rgb(0xF2E8D2), "nose": rgb(0x3A2A20)}
    f = Face(6, 6, c["face"])
    f.rect(0, 0, 24, 5, c["dark"])
    f.eye(7, 9, 2.6)
    f.eye(17, 9, 2.6)
    f.disc(9, 19, 1.3, c["nose"])
    f.disc(15, 19, 1.3, c["nose"])
    boxes = [
        ((3.5, 0, 6.5), (12.5, 8, 13.5), "body"),
        ((5, 1, 2.5), (11, 7, 6.5), "face", {"north": "face"}),
        ((4.6, 6.4, 2.2), (11.4, 8.2, 5), "dark"),
        ((3.2, 0, 7), (12.8, 2, 13.2), "dark"),
    ]
    boxes += pair(((2.8, 6.8, 3.5), (4.6, 7.8, 4.6), "horn"))
    boxes += pair(((2.8, 7.8, 3.5), (3.8, 10, 4.6), "horn"))
    return c, f, boxes


def teddy():
    c = {"body": rgb(0xC48A55), "dark": rgb(0xA06C3E), "belly": rgb(0xF1D6AE), "nose": rgb(0x4A3226)}
    f = Face(7, 6, c["body"])
    f.disc(14, 17, 6, c["belly"])
    f.eye(8, 9, 2.6)
    f.eye(20, 9, 2.6)
    f.disc(14, 14.5, 2, c["nose"])
    f.smile(18.5, 2)
    f.cheeks(15, 9.5)
    boxes = [
        ((4.5, 0, 6), (11.5, 6, 11), "body", {"north": "belly"}),
        ((4.5, 6, 5), (11.5, 12, 10.5), "body", {"north": "face"}),
        ((5, 0, 4.4), (7.4, 2.2, 6.6), "dark", {"north": "belly"}),
        ((8.6, 0, 4.4), (11, 2.2, 6.6), "dark", {"north": "belly"}),
    ]
    boxes += pair(((4, 11, 7), (6.2, 13.2, 8.6), "dark"))
    boxes += pair(((3.4, 2, 7), (4.5, 5.4, 9.6), "dark"))
    return c, f, boxes


def snail():
    c = {"body": rgb(0xE9D29A), "dark": rgb(0xCDB27A), "shell": rgb(0xB8743E), "eye": rgb(0xE9D29A)}
    f = Face(5, 4, c["body"])
    f.eye(6, 6, 2.4)
    f.eye(14, 6, 2.4)
    f.cheeks(11, 6.5)
    f.smile(13, 2)
    # The shell's spiral, on its sides.
    p = Face(7, 7, c["shell"])
    spiral = [(14, 14, 12.5), (14, 14, 9.5), (14, 14, 6.5), (14, 14, 3.5)]
    for i, (cx, cy, r) in enumerate(spiral):
        p.disc(cx, cy, r, shade(c["shell"], 0.72 if i % 2 == 0 else 1.15))
    p.disc(14, 14, 1.4, shade(c["shell"], 0.6))
    boxes = [
        ((5.5, 0, 1.5), (10.5, 4, 5.5), "body", {"north": "face"}),
        ((5.8, 0, 5.5), (10.2, 2, 15), "body"),
        ((4.6, 2, 5.6), (11.4, 9, 12.6), "shell", {"east": "pattern", "west": "pattern"}),
    ]
    boxes += pair(((6.3, 4, 2.6), (6.9, 6.6, 3.2), "dark"))
    boxes += pair(((5.9, 6.4, 2.2), (7.3, 7.8, 3.6), "dark"))
    return c, f, boxes, p


def seal():
    c = {"body": rgb(0x8C98A6), "dark": rgb(0x6E7A88), "light": rgb(0xB9C3CE), "nose": rgb(0x2E2A2E)}
    f = Face(6, 5.5, c["body"])
    f.rect(4, 12, 20, 22, c["light"])
    f.eye(7, 7, 2.8)
    f.eye(17, 7, 2.8)
    f.disc(12, 12.5, 1.6, c["nose"])
    f.smile(16, 1.5)
    for side in (-1, 1):
        for i in range(3):
            f.dot(12 + side * (4 + i * 1.5), 14 + i - 1, shade(c["light"], 0.6))
    boxes = [
        ((5, 0, 5), (11, 5.5, 12.5), "body", {"north": "light"}),
        ((5, 4, 2.5), (11, 9.5, 7), "body", {"north": "face"}),
        ((6, 0, 12.5), (10, 2, 14.5), "dark"),
        ((4.8, 0, 14.5), (11.2, 1, 16), "dark"),
    ]
    boxes += pair(((3.6, 0, 6), (5, 1.4, 9.4), "dark"))
    return c, f, boxes


def mouse():
    c = {"body": rgb(0xD8C9B0), "dark": rgb(0xB9A88C), "pink": rgb(0xF4A9B8), "nose": rgb(0xE7768F)}
    f = Face(5, 4.5, c["body"])
    f.eye(6, 7, 2.4)
    f.eye(14, 7, 2.4)
    f.disc(10, 12.5, 1.8, c["nose"])
    f.cheeks(11, 6.5)
    boxes = [
        ((5, 0, 6.5), (11, 5, 12), "body"),
        ((5.5, 0.5, 3), (10.5, 5, 6.5), "body", {"north": "face"}),
        ((7.6, 0.3, 12), (8.4, 1.1, 15.6), "pink"),
    ]
    # Big round ears: the mouse's, where the rat's are small.
    boxes += pair(((3.4, 4.6, 4.2), (6.8, 8.4, 5.0), "dark", {"north": "pink"}))
    return c, f, boxes


def snake():
    c = {"body": rgb(0x5DB04A), "dark": rgb(0x468A38), "belly": rgb(0xD8E89A), "tongue": rgb(0xE8483C)}
    f = Face(5, 4.5, c["body"])
    f.eye(5.5, 6, 2.4)
    f.eye(14.5, 6, 2.4)
    f.cheeks(10.5, 7.5)
    f.smile(13, 2)
    f.rect(9, 16, 11, 18, c["tongue"])
    boxes = [
        ((3, 0, 5), (13, 2.5, 14.5), "dark", {"north": "belly"}),
        ((4.5, 2.5, 6), (11.5, 4.8, 13), "body"),
        ((6, 4.8, 7.5), (10, 6.6, 11.5), "dark"),
        ((5.5, 5.5, 4.2), (10.5, 10, 8.2), "body", {"north": "face"}),
        ((7, 2.5, 5.2), (9, 5.6, 6.2), "body"),
        ((9.3, 5.6, 3.4), (10.7, 6.1, 4.2), "tongue"),
    ]
    return c, f, boxes


def skunk():
    c = {"body": rgb(0x2E2A30), "white": rgb(0xF2F0EA), "pink": rgb(0xF4A9B8)}
    f = Face(6, 5.5, c["body"])
    f.rect(10, 0, 14, 22, c["white"])
    f.big_eye(6.5, 9, 3.6, WHITE)
    f.big_eye(17.5, 9, 3.6, WHITE)
    f.disc(12, 17, 1.6, c["pink"])
    boxes = [
        ((4.5, 0, 6), (11.5, 6, 12), "body"),
        ((7, 6, 6.2), (9, 6.6, 11.8), "white"),
        ((5, 0.5, 2.5), (11, 6, 6.2), "body", {"north": "face"}),
        # The big fluffy tail, white along its middle.
        ((5, 3, 11), (11, 13, 14.6), "body"),
        ((7, 3, 14.6), (9, 13, 15), "white"),
        ((7, 13, 11.6), (9, 13.4, 14), "white"),
    ]
    boxes += pair(((5, 6, 3.2), (6.4, 7.4, 4.4), "body"))
    return c, f, boxes


def hawk():
    c = {"body": rgb(0x8A5A34), "dark": rgb(0x6A4024), "head": rgb(0xF1E6D0), "beak": rgb(0xF2B53B), "chest": rgb(0xE8D3AE)}
    f = Face(6, 5, c["head"])
    f.rect(0, 0, 24, 3, shade(c["head"], 0.9))
    f.eye(6, 8, 2.6)
    f.eye(18, 8, 2.6)
    for x in range(3, 10):
        f.dot(x, 4 + (x - 3) // 3, c["dark"])
        f.dot(24 - x, 4 + (x - 3) // 3, c["dark"])
    boxes = [
        ((4.5, 0, 5), (11.5, 7, 11), "body", {"north": "chest"}),
        ((5, 7, 4), (11, 12, 9), "head", {"north": "face"}),
        ((7, 8, 2.6), (9, 10, 4), "beak"),
        ((7.6, 7.4, 2.4), (8.4, 8.2, 3.0), "beak"),
        ((5.5, 0, 11), (10.5, 3, 14.5), "dark"),
    ]
    boxes += pair(((3.6, 1, 5.6), (4.5, 7, 10.8), "dark"))
    boxes += pair(((5.5, 0, 4.2), (7, 0.8, 5.2), "beak"))
    return c, f, boxes


def ladybug():
    c = {"shell": rgb(0xE23B30), "head": rgb(0x2A2224), "leg": rgb(0x1E1A1C), "spot": rgb(0x241C1C)}
    f = Face(5, 4, c["head"])
    f.big_eye(5.5, 7, 3.6, WHITE)
    f.big_eye(14.5, 7, 3.6, WHITE)
    f.cheeks(12.5, 7.5)
    # The shell's spots, and the line down its middle, on top.
    p = Face(8, 8, c["shell"])
    p.rect(15, 0, 17, 32, c["spot"])
    for x, y in ((7, 7), (24, 7), (6, 19), (25, 19), (9, 28), (22, 28)):
        p.disc(x, y, 3.2, c["spot"])
    side = Face(8, 5, c["shell"])
    boxes = [
        ((5.5, 0.4, 2.2), (10.5, 4.4, 5), "head", {"north": "face"}),
        ((3.8, 0.8, 4.6), (12.2, 6.8, 13.4), "shell", {"up": "pattern"}),
        ((4.4, 0.8, 4), (11.6, 6, 4.6), "shell"),
    ]
    boxes += pair(((6.3, 4.4, 2.8), (6.8, 6.4, 3.3), "leg"))
    for z in (6, 8.5, 11):
        boxes += pair(((2.8, 0, z), (3.8, 0.7, z + 0.6), "leg"))
    return c, f, boxes, p

def mole():
    c = {"body": rgb(0x4F4448), "dark": rgb(0x3A3134), "pink": rgb(0xF29CB0), "nose": rgb(0xE9708C)}
    f = Face(7, 6, c["body"])
    f.eye(8, 8, 1.9)
    f.eye(20, 8, 1.9)
    f.cheeks(14, 8.5)
    for dx, dy in ((0, -2), (2, 0), (0, 2), (-2, 0), (1.5, 1.5), (-1.5, 1.5), (1.5, -1.5), (-1.5, -1.5)):
        f.disc(14 + dx, 15 + dy, 1.3, c["nose"])
    f.disc(14, 15, 1.6, c["pink"])
    boxes = [
        ((4.5, 0, 4), (11.5, 6, 13), "body", {"north": "face"}),
        ((7, 0.6, 13), (9, 1.6, 14.6), "pink"),
    ]
    boxes += pair(((3, 0, 2.6), (5.6, 1.6, 5.4), "pink"))
    return c, f, boxes


def crow():
    c = {"body": rgb(0x2E2E3A), "dark": rgb(0x20202A), "sheen": rgb(0x46465A), "beak": rgb(0x8A8A94)}
    f = Face(6, 5, c["sheen"])
    f.big_eye(6, 8, 3.6, WHITE)
    f.big_eye(18, 8, 3.6, WHITE)
    f.cheeks(15, 9)
    boxes = [
        ((4.5, 0, 5), (11.5, 7, 11), "body"),
        ((5, 7, 4), (11, 12, 9), "sheen", {"north": "face"}),
        ((6.8, 8, 2.2), (9.2, 9.6, 4), "beak"),
        ((5.5, 0.5, 11), (10.5, 2.5, 14.5), "dark"),
    ]
    boxes += pair(((3.6, 1, 5.6), (4.5, 7, 10.8), "dark"))
    boxes += pair(((5.5, 0, 4.2), (7, 0.8, 5.2), "beak"))
    return c, f, boxes


def pug():
    c = {"body": rgb(0xE2C9A0), "dark": rgb(0x2E2626), "mask": rgb(0x4A3C38), "tongue": rgb(0xF29CB0)}
    f = Face(7, 6, c["body"])
    f.disc(14, 16, 8, c["mask"])
    f.big_eye(8, 9, 3.6, WHITE)
    f.big_eye(20, 9, 3.6, WHITE)
    for x in range(10, 18):
        f.dot(x, 3, shade(c["body"], 0.8))
    f.disc(14, 14, 1.8, c["dark"])
    f.rect(13, 19, 15, 22, c["tongue"])
    boxes = [
        ((4.5, 0, 6), (11.5, 6, 12.5), "body"),
        ((4.5, 2, 2.5), (11.5, 8, 6.5), "body", {"north": "face"}),
        ((7, 6, 11.2), (9, 8.6, 12.6), "body"),
        ((7, 7.6, 12.6), (9, 8.6, 13.6), "body"),
    ]
    boxes += pair(((3.6, 5.2, 3.2), (5, 8.2, 5.2), "dark"))
    boxes += pair(((5, 0, 4.6), (7.2, 2, 6.4), "body"))
    return c, f, boxes


def deer():
    c = {"body": rgb(0xB57A48), "dark": rgb(0x8E5A32), "cream": rgb(0xF2E2C4), "antler": rgb(0xE9D8B8), "nose": rgb(0x2E2626)}
    f = Face(6, 5, c["body"])
    f.rect(6, 11, 18, 20, c["cream"])
    f.eye(6.5, 7, 2.6)
    f.eye(17.5, 7, 2.6)
    f.disc(12, 13, 1.7, c["nose"])
    f.cheeks(11, 9)
    boxes = [
        ((4.5, 0, 6), (11.5, 6, 13), "body", {"north": "cream"}),
        ((5, 6, 3), (11, 11, 7.5), "body", {"north": "face"}),
        ((7, 4, 13), (9, 6.2, 14.2), "cream"),
    ]
    boxes += pair(((3.4, 9.2, 4.6), (5, 10.4, 6), "dark"))
    boxes += pair(((5.6, 11, 4.9), (6.3, 14.2, 5.6), "antler"))
    boxes += pair(((4.2, 12.8, 4.9), (5.6, 13.5, 5.6), "antler"))
    boxes += pair(((6.3, 13.4, 4.9), (7.2, 14.1, 5.6), "antler"))
    boxes += pair(((5, 0, 4.4), (7, 2.4, 6.2), "dark"))
    return c, f, boxes

def whale():
    c = {"body": rgb(0x4A6FA8), "dark": rgb(0x3A5A8C), "belly": rgb(0xEAF0F6), "spout": rgb(0xA8DCF4)}
    f = Face(8, 6, c["body"])
    f.rect(0, 16, 32, 24, c["belly"])
    f.eye(8, 9, 2.8)
    f.eye(24, 9, 2.8)
    f.cheeks(13, 12)
    f.smile(18, 5)
    boxes = [
        ((4, 0, 3), (12, 6, 13), "body", {"north": "face"}),
        ((4.2, 0, 3.2), (11.8, 1.4, 12.8), "belly"),
        ((6, 1.5, 13), (10, 4, 14.6), "dark"),
        ((3.5, 3, 14.6), (12.5, 4.2, 16), "dark"),
        ((7.4, 6, 6.4), (8.6, 7.4, 7.6), "spout"),
        ((6.2, 7.4, 5.2), (9.8, 8.4, 8.8), "spout"),
    ]
    boxes += pair(((2.8, 0.6, 6), (4, 1.6, 8.8), "dark"))
    return c, f, boxes


def fly():
    c = {"body": rgb(0x4A4A52), "dark": rgb(0x34343A), "eye": rgb(0xD8423A), "wing": rgb(0xDDEFF7)}
    f = Face(5, 4.5, c["body"])
    f.disc(5, 8, 4.4, c["eye"])
    f.disc(15, 8, 4.4, c["eye"])
    f.eye(5.5, 8.5, 2)
    f.eye(14.5, 8.5, 2)
    f.smile(15.5, 1.5, rgb(0xEEEEEE))
    boxes = [
        ((5.5, 1.5, 3), (10.5, 6, 6.5), "body", {"north": "face"}),
        ((5.5, 1, 6.5), (10.5, 5.5, 10), "dark"),
        ((6, 1, 10), (10, 4.5, 13.5), "body"),
        ((3.2, 5.5, 7), (7.6, 6, 13.5), "wing"),
        ((8.4, 5.5, 7), (12.8, 6, 13.5), "wing"),
    ]
    for z in (6.8, 8.2, 9.6):
        boxes += pair(((3.8, 0, z), (5.5, 1.6, z + 0.5), "dark"))
    return c, f, boxes


def beetle():
    c = {"shell": rgb(0x2F7A5A), "sheen": rgb(0x47A07A), "head": rgb(0x234A3A), "leg": rgb(0x1E2A26), "horn": rgb(0x3A3A40)}
    f = Face(5, 4, c["head"])
    f.big_eye(5.5, 7, 3.4, WHITE)
    f.big_eye(14.5, 7, 3.4, WHITE)
    f.cheeks(12.5, 7.5)
    p = Face(8, 8, c["shell"])
    p.rect(15, 0, 17, 32, c["head"])
    for x in (6, 22):
        p.rect(x, 4, x + 4, 26, c["sheen"])
    boxes = [
        ((5.5, 0.4, 2.4), (10.5, 4.4, 5.2), "head", {"north": "face"}),
        ((3.8, 0.6, 5), (12.2, 6.2, 14), "shell", {"up": "pattern"}),
        ((7.4, 4.4, 2.6), (8.6, 7.8, 3.8), "horn"),
        ((7.4, 7.0, 3.8), (8.6, 7.8, 5.0), "horn"),
    ]
    for z in (6, 8.5, 11):
        boxes += pair(((2.8, 0, z), (3.8, 0.7, z + 0.6), "leg"))
    return c, f, boxes, p


def mice():
    c = {"body": rgb(0xD8C9B0), "dark": rgb(0xB9A88C), "pink": rgb(0xF4A9B8), "nose": rgb(0xE7768F)}
    f = Face(3.5, 3, c["body"])
    f.eye(4, 4.5, 1.6)
    f.eye(10, 4.5, 1.6)
    f.disc(7, 8.5, 1.3, c["nose"])
    boxes = []
    # Three little mice, side by side, the middle one a step ahead.
    for x, z in ((1.6, 6), (6.25, 4.5), (10.9, 6)):
        boxes += [
            ((x, 0, z + 3), (x + 3.5, 3.2, z + 6.5), "body"),
            ((x, 0.2, z), (x + 3.5, 3.2, z + 3), "body", {"north": "face"}),
            ((x - 0.4, 2.8, z + 0.8), (x + 1.1, 4.6, z + 1.3), "dark", {"north": "pink"}),
            ((x + 2.4, 2.8, z + 0.8), (x + 3.9, 4.6, z + 1.3), "dark", {"north": "pink"}),
            ((x + 1.5, 0.2, z + 6.5), (x + 2, 0.7, z + 9), "pink"),
        ]
    return c, f, boxes


# --- Big words (round H): two-chunk names, gap-only ------------------------------------------

def bobcat():
    c = {"body": rgb(0xC9965C), "spot": rgb(0x6E4A28), "muzzle": rgb(0xF3E4C8), "tuft": rgb(0x2A2224),
         "nose": rgb(0xE88A8A), "light": rgb(0xDDB07A), "ruff": rgb(0xE8CFA6)}
    f = Face(7, 6, c["body"])
    # The ruff of pale fur round its cheeks, and a muzzle.
    f.rect(0, 12, 4, 24, c["ruff"])
    f.rect(24, 12, 28, 24, c["ruff"])
    f.rect(6, 13, 22, 24, c["muzzle"])
    for x in (10, 13.3, 16.6):
        f.rect(x, 0, x + 1.4, 4.5, c["spot"])
    f.eye(8, 9, 2.8)
    f.eye(20, 9, 2.8)
    f.cheeks(16, 10)
    f.disc(14, 15, 1.6, c["nose"])
    f.smile(18, 2)
    for side in (-1, 1):
        for i in range(3):
            f.dot(14 + side * (5 + i * 1.6), 16 + i - 1, shade(c["muzzle"], 0.62))
    # Spots, on its back and sides.
    p = Face(7, 6, c["body"])
    for x, y in ((4, 4), (13, 3), (22, 5), (8, 11), (18, 12), (3, 18), (12, 19), (24, 18)):
        p.disc(x, y, 2.2, c["spot"])
    boxes = [
        ((5, 0, 6.5), (11, 6, 13.5), "body", {"up": "pattern", "east": "pattern", "west": "pattern"}),
        ((4.5, 5, 4), (11.5, 11, 9), "body", {"north": "face"}),
        ((7.2, 3.5, 13.5), (8.8, 5, 14.7), "light"),
        ((7.2, 3.8, 14.7), (8.8, 5, 15.5), "tuft"),
    ]
    boxes += pair(((4.8, 11, 6), (6.4, 13, 7.4), "body"))
    boxes += pair(((5.2, 13, 6.3), (6, 14.6, 7.0), "tuft"))
    boxes += pair(((5.4, 0, 4.4), (7.2, 1.4, 7), "light"))
    return c, f, boxes, p

def catfish():
    c = {"body": rgb(0x7E8C70), "dark": rgb(0x5E6A52), "belly": rgb(0xE8E2C8), "whisker": rgb(0x3A3430)}
    f = Face(8, 5, c["body"])
    f.rect(0, 13, 32, 20, c["belly"])
    f.eye(7, 6, 2.6)
    f.eye(25, 6, 2.6)
    f.cheeks(11, 11)
    for x in range(10, 22):
        f.dot(x, 14, INK)
    boxes = [
        ((4, 0, 3), (12, 5, 11), "body", {"north": "face"}),
        ((4.6, 0.2, 11), (11.4, 4.2, 13.5), "body"),
        ((5.2, 0.6, 13.5), (10.8, 3.6, 14.4), "dark"),
        ((4.6, 0, 14.4), (11.4, 4.6, 15.6), "dark"),
        ((7.3, 5, 5.5), (8.7, 6.6, 9.5), "dark"),
        ((4.2, 0, 3.2), (11.8, 0.8, 10.8), "belly"),
    ]
    # Long whiskers out to each side, and two drooping from the chin.
    boxes += pair(((0.6, 1.9, 3.0), (4, 2.4, 3.5), "whisker"))
    boxes += pair(((1.6, 1.1, 2.8), (4, 1.5, 3.2), "whisker"))
    boxes += pair(((6, 0, 2.2), (6.5, 1.6, 2.7), "whisker"))
    boxes += pair(((2.6, 0.8, 6), (4, 1.6, 8.6), "dark"))
    return c, f, boxes


def starfish():
    c = {"body": rgb(0xF2875A), "dot": rgb(0xFFC9A0), "dark": rgb(0xD96A44)}
    f = Face(5, 5, c["body"])
    f.eye(6, 8, 2.4)
    f.eye(14, 8, 2.4)
    f.cheeks(12, 7)
    f.smile(14.5, 2)
    p = Face(4, 4, c["body"])
    for x, y in ((3, 3), (9, 5), (5, 10), (12, 12), (3, 14)):
        p.disc(x, y, 1.2, c["dot"])
    boxes = [
        ((5.5, 3.5, 7), (10.5, 8.5, 9), "body", {"north": "face"}),
        ((6.5, 8.5, 7.2), (9.5, 12.5, 8.8), "body", {"north": "pattern"}),
        ((7, 12.5, 7.4), (9, 13.6, 8.6), "dark"),
    ]
    boxes += pair(((1.5, 5, 7.2), (5.5, 8, 8.8), "body", {"north": "pattern"}))
    boxes += pair(((0.6, 5.4, 7.4), (1.5, 7.4, 8.6), "dark"))
    boxes += pair(((5, 0, 7.2), (7.4, 3.5, 8.8), "body", {"north": "pattern"}))
    return c, f, boxes, p


def chipmunk():
    c = {"body": rgb(0xC07A44), "stripe": rgb(0x3E2A1E), "light": rgb(0xF6E6CA), "tail": rgb(0x9A5E36), "nose": rgb(0x3A2A24)}
    f = Face(6, 5.5, c["body"])
    f.rect(0, 13, 24, 22, c["light"])
    # The stripe through each eye, with a pale one under it: a chipmunk's face.
    f.rect(1, 7, 10, 9, c["stripe"])
    f.rect(14, 7, 23, 9, c["stripe"])
    f.rect(1, 10, 9, 12, c["light"])
    f.rect(15, 10, 23, 12, c["light"])
    f.rect(11, 0, 13, 6, c["stripe"])
    f.eye(7, 8, 2.6)
    f.eye(17, 8, 2.6)
    f.disc(5, 16, 3.6, c["light"])
    f.disc(19, 16, 3.6, c["light"])
    f.cheeks(15, 7.5)
    f.disc(12, 14, 1.2, c["nose"])
    f.smile(17, 1.5)
    # Stripes down its back and along its sides: dark, pale, dark.
    p = Face(7, 6, c["body"])
    for y, colour in ((4, c["stripe"]), (8, c["light"]), (11, c["stripe"]), (14, c["light"]), (17, c["stripe"])):
        p.rect(0, y, 28, y + 2, colour)
    boxes = [
        ((5, 0, 5.5), (11, 6, 12.5), "body", {"up": "pattern", "east": "pattern", "west": "pattern"}),
        ((5, 5.5, 4), (11, 11, 9), "body", {"north": "face"}),
        ((5.8, 1, 12.5), (10.2, 9, 14.6), "tail", {"north": "pattern"}),
        ((6.2, 9, 11.4), (9.8, 11.8, 14), "tail"),
    ]
    boxes += pair(((5.2, 11, 5.6), (6.6, 12.4, 7), "body"))
    boxes += pair(((5.6, 0, 4.2), (7.2, 1.2, 6), "light"))
    return c, f, boxes, p

def puffin():
    c = {"body": rgb(0x26242C), "white": rgb(0xF6F4F0), "tip": rgb(0xEE4E2A), "band": rgb(0xF6C62E),
         "base": rgb(0x7E8CA4), "stripe": rgb(0xF4EAD6), "feet": rgb(0xFF7A1E), "grey": rgb(0xB4B8C2)}
    f = Face(7, 7, c["body"])
    # A white face under a black cap, black down the edges; the eyes high and wide of the beak,
    # each with its little dark line running back from it.
    f.rect(2, 4, 26, 28, c["white"])
    f.rect(4, 3, 24, 4, c["white"])
    for side, x in ((-1, 7.5), (1, 20.5)):
        f.disc(x, 9, 2.8, c["tip"])
        f.eye(x, 9, 2.1)
        for i in range(4):
            f.dot(x + side * (3 + i), 7.5 - i * 0.6, c["grey"])
    f.cheeks(13.5, 10)
    boxes = [
        # A round, upright body: wider in the middle than at the top or the foot.
        ((4.5, 1, 5), (11.5, 8, 11), "body", {"north": "face"}),
        ((5, 0.4, 5.4), (11, 1, 10.6), "body"),
        ((5.3, 8, 5.8), (10.7, 9, 10.2), "body"),
        # The beak: a big triangle in bands, grey-blue at the base, a pale stripe, yellow, then
        # orange-red to the tip.
        ((6.9, 1.6, 4.4), (9.1, 5.2, 5.0), "base"),
        ((6.95, 1.8, 4.1), (9.05, 5.0, 4.4), "stripe"),
        ((7.0, 2.0, 3.4), (9.0, 4.7, 4.1), "band"),
        ((7.15, 2.2, 2.6), (8.85, 4.3, 3.4), "tip"),
        ((7.3, 2.4, 1.9), (8.7, 3.7, 2.6), "tip"),
        ((7.5, 2.6, 1.3), (8.5, 3.2, 1.9), "tip"),
        ((6.5, 0.6, 11), (9.5, 2.2, 12.6), "body"),
    ]
    boxes += pair(((3.8, 2, 6), (4.5, 7.4, 10.4), "body"))
    boxes += pair(((5.0, 0, 3.6), (7.6, 0.6, 6.2), "feet"))
    return c, f, boxes
def robin():
    c = {"body": rgb(0x8A6448), "breast": rgb(0xE8653A), "beak": rgb(0xF2C14A), "dark": rgb(0x6A4A34), "leg": rgb(0x5A4030)}
    f = Face(7, 7, c["body"])
    f.disc(14, 24, 12, c["breast"])
    f.eye(8, 8, 2.6)
    f.eye(20, 8, 2.6)
    f.cheeks(13, 9)
    boxes = [
        ((4.5, 0.8, 5), (11.5, 7.8, 11), "body", {"north": "face"}),
        ((7.2, 4.4, 4.2), (8.8, 5.6, 5), "beak"),
        ((7.6, 4.6, 3.6), (8.4, 5.4, 4.2), "beak"),
        ((5.5, 7.8, 6), (10.5, 8.4, 10), "body"),
        ((6, 3, 11), (10, 4.4, 14.2), "dark"),
    ]
    boxes += pair(((3.8, 2, 6), (4.5, 6.6, 10.4), "dark"))
    boxes += pair(((6.2, 0, 7.4), (6.8, 0.8, 8.4), "leg"))
    return c, f, boxes


def lobster():
    c = {"body": rgb(0xD8402E), "dark": rgb(0xA82E22), "light": rgb(0xF0745A), "feeler": rgb(0x7A2018)}
    f = Face(5, 3.5, c["body"])
    f.eye(5.5, 5, 2.2)
    f.eye(14.5, 5, 2.2)
    f.cheeks(10, 6.5)
    f.smile(11.5, 2)
    # A long body, a tail of bands, a fan at the end, and big claws held up in front.
    boxes = [
        ((5.5, 0, 4), (10.5, 3.5, 9), "body", {"north": "face"}),
        ((6.5, 3.5, 5), (9.5, 4.2, 8), "dark"),
        ((5.8, 0, 9), (10.2, 3, 10.6), "light"),
        ((6, 0, 10.6), (10, 2.6, 12.2), "body"),
        ((6.3, 0, 12.2), (9.7, 2.2, 13.6), "light"),
        ((5, 0, 13.6), (11, 1, 15.6), "dark"),
    ]
    # Feelers, long and swept back over its head.
    boxes += pair(((6.4, 3.5, 3.4), (6.8, 8.5, 3.8), "feeler"))
    boxes += pair(((6.4, 8.1, 3.8), (6.8, 8.5, 8.5), "feeler"))
    boxes += pair(((3.8, 1.2, 4.4), (5.5, 2.2, 6), "dark"))
    boxes += pair(((1.4, 1.6, 0.8), (4.8, 5.2, 4.6), "body"))
    boxes += pair(((1.4, 5.2, 0.8), (2.6, 6.2, 2.6), "light"))
    boxes += pair(((3.6, 5.2, 0.8), (4.8, 6.6, 2.2), "light"))
    for z in (6.2, 7.6, 9.0):
        boxes += pair(((4.4, 0, z), (5.5, 0.6, z + 0.6), "dark"))
    return c, f, boxes

def hedgehog():
    c = {"spikes": rgb(0x5E4230), "tip": rgb(0xE2CCA4), "face": rgb(0xF0DEBE), "nose": rgb(0x2A2224), "feet": rgb(0xD8B890)}
    f = Face(5, 4.5, c["face"])
    f.eye(5.5, 7, 2.4)
    f.eye(14.5, 7, 2.4)
    f.cheeks(12, 7.5)
    f.smile(15, 1.5)
    # Spikes: dark felt in rows of pale-tipped points.
    p = Face(8, 8, c["spikes"])
    for row in range(0, 32, 5):
        for col in range((row // 5) % 2 * 3, 32, 6):
            p.rect(col + 1, row, col + 3, row + 1, c["tip"])
            p.rect(col, row + 1, col + 4, row + 2, shade(c["tip"], 0.8))
    spikes = {"up": "pattern", "east": "pattern", "west": "pattern", "south": "pattern", "north": "pattern"}
    boxes = [
        ((5.5, 0, 2.6), (10.5, 4.5, 5), "face", {"north": "face"}),
        ((7, 0.8, 1.4), (9, 2.6, 2.6), "face"),
        ((7.5, 1.7, 0.9), (8.5, 2.6, 1.4), "nose"),
        ((4, 0.4, 5), (12, 6, 13), "spikes", spikes),
        ((4.8, 6, 5.6), (11.2, 7.6, 12.4), "spikes", spikes),
        ((5.8, 7.6, 6.6), (10.2, 8.6, 11.4), "spikes", spikes),
        ((4.6, 0, 4.4), (11.4, 0.4, 12.6), "feet"),
    ]
    # Points standing up off its back.
    for x, z in ((5, 6), (7.4, 7.2), (9.8, 6), (6, 9.6), (8.6, 9.8), (4.4, 11.6), (10.6, 11.4), (7.4, 12.2)):
        boxes.append(((x, 6.4 if 5 < x < 10.5 and 6 < z < 12 else 5.4, z), (x + 1, 8.4 if 5 < x < 10.5 and 6 < z < 12 else 7, z + 1), "spikes", spikes))
    boxes += pair(((5.6, 4.5, 4.0), (6.6, 5.6, 4.8), "face"))
    return c, f, boxes, p


PLUSHIES = {
    "owl": owl, "duck": duck, "ant": ant, "rat": rat, "ox": ox, "shark": shark, "crab": crab,
    "worm": worm, "moth": moth, "yak": yak, "teddy": teddy, "snail": snail, "seal": seal, "mouse": mouse,
    "snake": snake, "skunk": skunk, "hawk": hawk, "ladybug": ladybug, "mole": mole, "crow": crow, "pug": pug,
    "deer": deer, "whale": whale, "fly": fly, "beetle": beetle, "mice": mice,
    "bobcat": bobcat, "catfish": catfish, "starfish": starfish, "chipmunk": chipmunk, "puffin": puffin,
    "robin": robin, "lobster": lobster, "hedgehog": hedgehog,
}

NAMES = {"teddy": "Teddy bear plushie", "mice": "Mice plushie"}


# --- The sheet and the model -----------------------------------------------------------------

def felt(rng, colour):
    """A swatch of felt: the colour, with a soft speckle."""
    cell = []
    for y in range(CELL):
        row = []
        for x in range(CELL):
            by = 1.0 + (rng.random() - 0.5) * 0.08
            row.append(shade(colour, by))
        cell.append(row)
    return cell


def build(name, make):
    made = make()
    colours, face, boxes = made[0], made[1], made[2]
    pattern = made[3] if len(made) > 3 else None
    rng = random.Random(name)
    sheet = [[CLEAR] * SHEET for _ in range(SHEET)]
    # Felt swatches along the top half: one per colour.
    swatch = {}
    for i, (role, colour) in enumerate(colours.items()):
        cx, cy = (i % 8) * CELL, (i // 8) * CELL
        for y, row in enumerate(felt(rng, colour)):
            for x, px in enumerate(row):
                sheet[cy + y][cx + x] = px
        # uv in model units of the 64px sheet (16 units across): inset, so filtering never bleeds.
        u, v = cx / 4, cy / 4
        swatch[role] = [u + 0.5, v + 0.5, u + 1.5, v + 1.5]
    # The face, in the bottom half.
    for y, row in enumerate(face.pixels()):
        for x, px in enumerate(row):
            sheet[32 + y][x] = px
    face_uv = [0, 8, face.w / 4, 8 + face.h / 4]
    pattern_uv = None
    if pattern is not None:
        for y, row in enumerate(pattern.pixels()):
            for x, px in enumerate(row):
                sheet[32 + y][32 + x] = px
        pattern_uv = [8, 8, 8 + pattern.w / 4, 8 + pattern.h / 4]
    write_png(ASSETS / f"textures/block/plush_{name}.png", sheet)

    elements = []
    for box in boxes:
        lo, hi, role = box[0], box[1], box[2]
        sides = box[3] if len(box) > 3 else {}
        faces = {}
        for side in ("north", "south", "east", "west", "up", "down"):
            which = sides.get(side, role)
            uv = face_uv if which == "face" else pattern_uv if which == "pattern" else swatch[which]
            faces[side] = {"uv": uv, "texture": "#plush"}
        element = {"from": list(lo), "to": list(hi), "faces": faces}
        if "rot" in sides:
            axis, angle, origin = sides["rot"]
            element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
        elements.append(element)
    write(ASSETS / f"models/block/plush_{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"plush": f"{NS}:block/plush_{name}", "particle": f"{NS}:block/plush_{name}"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [20, 205, 0], "translation": [0, 1.5, 0], "scale": [1.05, 1.05, 1.05]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 12, 0], "scale": [0.9, 0.9, 0.9]},
        },
    })


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for name, make in PLUSHIES.items():
        build(name, make)
    # Facing whoever placed it: the model's front is north.
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    write(ASSETS / "blockstates/plush.json", {
        "variants": {
            f"facing={facing},hidden={hidden},kind={name}": {"model": f"{NS}:block/hidden"} if hidden == "true"
            else {"model": f"{NS}:block/plush_{name}", "y": y}
            for name in PLUSHIES for facing, y in turn.items() for hidden in ("false", "true")
        }
    })
    # Hidden while a display of it plays in its place (Play.java): nothing drawn, so nothing doubles.
    write(ASSETS / "models/block/hidden.json", {"textures": {"particle": "minecraft:block/white_wool"}, "elements": []})
    # One item for each, drawn as its block.
    for name in PLUSHIES:
        write(ASSETS / f"items/plush_{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/plush_{name}"}})
    # Broken, it comes back as itself.
    # A pool a kind, each only for its own kind: broken, a plushie is itself again.
    write(DATA / "loot_table/blocks/plush.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:plush", "properties": {"kind": name}}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:plush_{name}"}],
        } for name in PLUSHIES],
        "random_sequence": f"{NS}:blocks/plush",
    })
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    lang[f"block.{NS}.plush"] = "Plushie"
    for name in PLUSHIES:
        lang.pop(f"item.{NS}.plush.{name}", None)
        lang[f"item.{NS}.plush_{name}"] = NAMES.get(name, name.capitalize() + " plushie")
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {len(PLUSHIES)} plushies")


if __name__ == "__main__":
    main()
