#!/usr/bin/env python3
"""
Draw the toys: the second collectible family, in the plushies' chunky style. Wooden and felt
toys in bright colours, a few boxes each with little painted details (windows, wheels, spots, a
face on the train), placed as small models facing whoever put them down and shown as the same
models in the inventory.

Each toy's sheet is 64x64: felt swatches in the top-left quarter (up to sixteen colours), and up
to three painted pictures in the other quarters, each at most 8 by 8 model units (4 pixels a
unit). A box's side is a swatch's name or "@picture".

Writes textures/block/toy_<name>.png, models/block/toy_<name>.json, the toy blockstate (by kind
and facing), an item definition for each toy_<name> (the cap and the wig worn on the head as
their own little models), the loot table and the English names.

Usage: python3 generate_toys.py
"""

import json
import pathlib
import random

from generate_plushies import Face, felt, rgb, shade, write, CLEAR, INK, WHITE, CHEEK
from generate_textures import tile, stamp, write_png, VOWEL, CONSONANT

HERE = pathlib.Path(__file__).parent
NS = "phonics-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS
DATA = HERE / "src/main/resources/data" / NS

RED, BLUE, YELLOW, GREEN = rgb(0xE8483C), rgb(0x3A7BD5), rgb(0xF6D146), rgb(0x5DB04A)
ORANGE, PINK, CREAM, BLACK = rgb(0xF08A2C), rgb(0xF29BAE), rgb(0xF4F0E6), rgb(0x2E2A30)
WOOD, SILVER, PURPLE, SKY = rgb(0xC9965E), rgb(0xC9CED6), rgb(0x9B6FD4), rgb(0xAEDCF2)

QUARTERS = [(32, 0), (0, 32), (32, 32)]


def mirror(box):
    (x0, y0, z0), (x1, y1, z1) = box[0], box[1]
    return ((16 - x1, y0, z0), (16 - x0, y1, z1)) + tuple(box[2:])


def pair(box):
    return [box, mirror(box)]


def wheels(x0, x1, zs, y=0, size=2.2, role="tyre", part=None, hub=None):
    """Four (or more) wheels, either side, at these z's. With a part name, each wheel is its own
    part (part0, part1 at the first z, left and right...) with a hub bar across its outside, so it
    can be seen to turn."""
    out = []
    for i, z in enumerate(zs):
        if part is None:
            out += pair(((x0, y, z), (x0 + 1.2, y + size, z + size), role))
            continue
        left, right = {"part": f"{part}{2 * i}"}, {"part": f"{part}{2 * i + 1}"}
        wheel = ((x0, y, z), (x0 + 1.2, y + size, z + size), role)
        bar = ((x0 - 0.2, y + size / 2 - 0.3, z + 0.2), (x0, y + size / 2 + 0.3, z + size - 0.2), hub)
        out += [wheel + (left,), bar + (left,), mirror(wheel) + (right,), mirror(bar) + (right,)]
    return out


def windows(w, h, colour, glass, panes, frame=None):
    """A row of windows on a side: panes across, each a light pane with a frame."""
    f = Face(w, h, colour)
    pw = f.w / panes
    for i in range(panes):
        f.rect(i * pw + 2, 2, (i + 1) * pw - 2, f.h - 2, glass)
        f.rect(i * pw + 2, 2, (i + 1) * pw - 2, 4, shade(glass, 1.12))
    return f


GLYPHS = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#"],
    "B": ["####.", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".####", "#....", "#....", "#....", "#....", ".####"],
}


# --- The toys -------------------------------------------------------------------------------

def drum():
    c = {"side": RED, "skin": CREAM, "rim": YELLOW, "stick": WOOD, "tip": RED}
    boxes = [
        ((3, 0, 3), (13, 7, 13), "side"),
        ((2.6, 6.4, 2.6), (13.4, 7.4, 13.4), "rim"),
        ((2.6, 0, 2.6), (13.4, 1, 13.4), "rim"),
        ((3.2, 7.4, 3.2), (12.8, 7.6, 12.8), "skin"),
        ((5, 7.6, 6), (12, 8.2, 6.6), "stick"),
        ((4, 7.6, 9.4), (11, 8.2, 10), "stick"),
        ((4, 7.6, 5.8), (5, 8.6, 6.8), "tip"),
        ((3, 7.6, 9.2), (4, 8.6, 10.2), "tip"),
    ]
    # Zigzag cords round the side.
    side = Face(8, 7, RED)
    for x in range(0, 32, 4):
        for i in range(4):
            side.dot(x + i, 4 + i * 5, CREAM)
            side.dot(x + 3 - i, 4 + i * 5, CREAM)
    boxes[0] = ((3, 0, 3), (13, 7, 13), "side", {"north": "@cords", "south": "@cords", "east": "@cords", "west": "@cords"})
    return c, {"cords": side}, boxes


def kite():
    """A diamond kite, taller than wide, standing on its bow-tied tail: cross spars on the sail."""
    c = {"red": RED, "yellow": YELLOW, "blue": BLUE, "green": GREEN, "spar": shade(WOOD, 0.8), "string": CREAM,
         "bow": PINK}
    lean = {}
    rows = [(14.2, 15, 7.6), (13.4, 14.2, 6.8), (12.6, 13.4, 6), (11.8, 12.6, 5.2), (11, 11.8, 5.6),
            (10.2, 11, 6.1), (9.4, 10.2, 6.6), (8.6, 9.4, 7.1), (7.8, 8.6, 7.5)]
    boxes = []
    for y0, y1, x0 in rows:
        high = y0 >= 11.8
        boxes.append(((x0, y0, 7.7), (8, y1, 8.3), "red" if high else "blue", lean))
        boxes.append(((8, y0, 7.7), (16 - x0, y1, 8.3), "yellow" if high else "green", lean))
    boxes += [
        ((7.75, 7.8, 7.5), (8.25, 15, 7.7), "spar", lean),
        ((5.2, 12, 7.5), (10.8, 12.4, 7.7), "spar", lean),
        ((7.9, 5.6, 7.9), (8.1, 7.8, 8.1), "string"),
        ((7, 6, 7.8), (9, 6.8, 8.2), "bow"),
        ((8.6, 3.6, 7.9), (8.8, 5.6, 8.1), "string"),
        ((7.9, 5.4, 7.9), (8.8, 5.6, 8.1), "string"),
        ((7.7, 3.6, 7.8), (9.7, 4.4, 8.2), "bow"),
        ((7.5, 1.4, 7.9), (7.7, 3.6, 8.1), "string"),
        ((7.5, 3.4, 7.9), (8.6, 3.6, 8.1), "string"),
        ((6.6, 1.6, 7.8), (8.6, 2.4, 8.2), "bow"),
        ((7.5, 0, 7.6), (8.9, 0.4, 9.6), "string"),
        ((7.5, 0.4, 7.9), (7.7, 1.4, 8.1), "string"),
    ]
    # The tail, everything under the sail, is a part of its own: it waves.
    boxes = [box + ({"part": "tail"},) if len(box) == 3 and box[1][1] <= 7.8 else box for box in boxes]
    return c, {}, boxes

def top():
    """A wooden spinning top: small, a point, a wide shoulder and a thin spindle to twist."""
    c = {"body": RED, "band": YELLOW, "point": WOOD, "spindle": BLUE, "low": shade(RED, 0.85)}
    boxes = [
        ((7.5, 0, 7.5), (8.5, 0.8, 8.5), "point"),
        ((6.9, 0.8, 6.9), (9.1, 1.6, 9.1), "low"),
        ((6, 1.6, 6), (10, 2.6, 10), "low"),
        ((5.1, 2.6, 5.1), (10.9, 3.8, 10.9), "body"),
        ((4.7, 3.8, 4.7), (11.3, 4.6, 11.3), "band"),
        ((5.1, 4.6, 5.1), (10.9, 5.4, 10.9), "body"),
        ((6.3, 5.4, 6.3), (9.7, 6, 9.7), "body"),
        ((7.5, 6, 7.5), (8.5, 8.4, 8.5), "spindle"),
        ((7.2, 8.4, 7.2), (8.8, 9, 8.8), "spindle"),
    ]
    return c, {}, boxes

def ring():
    c = {"base": WOOD, "red": RED, "orange": ORANGE, "yellow": YELLOW, "green": GREEN, "blue": BLUE, "knob": RED}
    boxes = [
        ((4, 0, 4), (12, 1, 12), "base"),
        # The post the rings go on, hidden inside them until they come off.
        ((7.3, 1, 7.3), (8.7, 9, 8.7), "base"),
        ((4, 1, 4), (12, 2.6, 12), "red", {"part": "ring0"}),
        ((4.5, 2.6, 4.5), (11.5, 4.2, 11.5), "orange", {"part": "ring1"}),
        ((5, 4.2, 5), (11, 5.8, 11), "yellow", {"part": "ring2"}),
        ((5.5, 5.8, 5.5), (10.5, 7.4, 10.5), "green", {"part": "ring3"}),
        ((6, 7.4, 6), (10, 9, 10), "blue", {"part": "ring4"}),
        ((7.2, 9, 7.2), (8.8, 10, 8.8), "base"),
        ((6.8, 10, 6.8), (9.2, 11.6, 9.2), "knob"),
    ]
    return c, {}, boxes


def cap():
    c = {"crown": RED, "brim": shade(RED, 0.78), "button": CREAM, "band": shade(RED, 0.9)}
    # A star on the front of the crown, as a team cap has.
    front = Face(8, 3, RED)
    for x, y in ((16, 2), (15, 4), (16, 4), (17, 4), (13, 5), (14, 5), (15, 5), (16, 5), (17, 5), (18, 5), (19, 5),
                 (14, 6), (15, 6), (16, 6), (17, 6), (18, 6), (14, 7), (15, 7), (17, 7), (18, 7), (13, 8), (14, 8), (18, 8), (19, 8)):
        front.dot(x, y, CREAM)
    boxes = [
        ((4, 0, 5), (12, 3, 13), "crown", {"north": "@front"}),
        ((4.6, 3, 5.6), (11.4, 4.4, 12.4), "crown"),
        ((5.8, 4.4, 6.8), (10.2, 5.2, 11.2), "crown"),
        ((7.4, 5.2, 8.4), (8.6, 5.8, 9.6), "button"),
        ((4.2, 0, 0.6), (11.8, 0.8, 5), "brim"),
        ((3.9, 0, 4.9), (12.1, 0.6, 13.1), "band"),
    ]
    return c, {"front": front}, boxes


def wig():
    """A curly dress-up wig on a plain foam head: no face, so it reads as the wig, not a doll."""
    c = {"foam": rgb(0xE9E6E1), "neck": shade(rgb(0xE9E6E1), 0.88), "hair": ORANGE, "curl": shade(ORANGE, 1.12),
         "deep": shade(ORANGE, 0.88)}
    boxes = [
        ((6.4, 0, 6.4), (9.6, 2, 9.6), "neck"),
        ((5.5, 2, 5.5), (10.5, 7, 10.5), "foam"),
        ((4.8, 6.4, 4.8), (11.2, 8.4, 11.2), "hair"),
        ((4.8, 1.6, 9.4), (11.2, 6.4, 11.2), "hair"),
        ((4.8, 2.4, 4.8), (5.5, 6.4, 9.4), "hair"),
        ((10.5, 2.4, 4.8), (11.2, 6.4, 9.4), "hair"),
        ((5.5, 5.8, 4.8), (10.5, 6.4, 5.5), "hair"),
    ]
    curls = [(5.2, 8.2, 5.4), (7.4, 8.4, 5.2), (9.6, 8.2, 5.4), (5.8, 8.4, 7.6), (8.6, 8.6, 7.8), (6.2, 8.2, 9.8),
             (8.8, 8.2, 9.6), (4.2, 5.4, 6), (4.2, 3.2, 7.4), (11.2, 5.4, 6), (11.2, 3.2, 7.4), (5.6, 3.6, 11.2),
             (8.2, 4.8, 11.2), (9.6, 2.2, 11.2), (6.4, 1.4, 11), (4.4, 1.8, 9.6), (11, 1.8, 9.6), (6.6, 5.6, 4.2),
             (8.8, 5.6, 4.2)]
    for i, (x, y, z) in enumerate(curls):
        boxes.append(((x - 0.1, y, z - 0.1), (x + 1.3, y + 1.3, z + 1.3), ("curl", "deep", "hair")[i % 3]))
    return c, {}, boxes

def net():
    """A butterfly net side on: a long handle, the hoop seen edge on, and a deep mesh bag drooping out
    from it, the shape that says net (face on, a hoop is a racket)."""
    c = {"handle": WOOD, "hoop": RED, "grip": BLUE}
    mesh = Face(6, 6, rgb(0xF4FAFB))
    for i in range(0, 24, 3):
        mesh.rect(i, 0, i + 1, 24, shade(SKY, 0.92))
        mesh.rect(0, i, 24, i + 1, shade(SKY, 0.92))
    x0, x1 = 4.6, 5.4
    boxes = [
        ((x0, 14.1, 6.2), (x1, 14.7, 9.8), "hoop"),
        ((x0, 7.3, 6.2), (x1, 7.9, 9.8), "hoop"),
        ((x0, 9.2, 4.3), (x1, 12.8, 4.9), "hoop"),
        ((x0, 9.2, 11.1), (x1, 12.8, 11.7), "hoop"),
    ]
    for z0, z1, za, zb in ((4.9, 5.6, 5.5, 6.3), (10.4, 11.1, 9.7, 10.5)):
        boxes += [((x0, 12.8, z0), (x1, 13.5, z1), "hoop"), ((x0, 13.4, za), (x1, 14.2, zb), "hoop"),
                  ((x0, 8.5, z0), (x1, 9.2, z1), "hoop"), ((x0, 7.8, za), (x1, 8.6, zb), "hoop")]
    boxes += [
        ((x1, 8, 5), (7, 14, 11), "@mesh"),
        ((7, 7, 5.6), (8.6, 12.4, 10.4), "@mesh"),
        ((8.6, 5.8, 6.4), (10, 10.4, 9.6), "@mesh"),
        ((10, 4.8, 7.2), (11, 8, 8.8), "@mesh"),
        ((4.55, 1.6, 7.55), (5.45, 7.3, 8.45), "handle"),
        ((4.4, 0, 7.4), (5.6, 1.6, 8.6), "grip"),
    ]
    return c, {"mesh": mesh}, boxes

def pin():
    c = {"body": CREAM, "stripe": RED, "shade": shade(CREAM, 0.92)}
    boxes = [
        ((6, 0, 6), (10, 1, 10), "shade"),
        ((5.4, 1, 5.4), (10.6, 5.5, 10.6), "body"),
        ((6.2, 5.5, 6.2), (9.8, 6.3, 9.8), "body"),
        ((6.6, 6.3, 6.6), (9.4, 7.1, 9.4), "stripe"),
        ((6.8, 7.1, 6.8), (9.2, 7.7, 9.2), "body"),
        ((6.6, 7.7, 6.6), (9.4, 8.5, 9.4), "stripe"),
        ((6.2, 8.5, 6.2), (9.8, 11.5, 9.8), "body"),
    ]
    return c, {}, boxes


def sock():
    c = {"sock": CREAM, "stripe": RED, "mouth": RED, "heel": shade(CREAM, 0.9), "inside": rgb(0x8C2A2A)}
    # The top of the head with its button eyes, and the jaw under it with a red lip: the jaw opens.
    face = Face(6, 2.6, CREAM)
    face.disc(6, 5.5, 2.6, BLACK)
    face.disc(6, 5.5, 1.2, CREAM)
    face.disc(18, 5.5, 2.6, BLACK)
    face.disc(18, 5.5, 1.2, CREAM)
    face.rect(4, 8, 20, 11, RED)
    jaw = Face(6, 1.4, CREAM)
    jaw.rect(4, 0, 20, 2, RED)
    boxes = [
        ((5, 0, 6.5), (11, 1.6, 11), "stripe"),
        ((5, 1.6, 6.5), (11, 3, 11), "sock"),
        ((5, 3, 6.5), (11, 4.4, 11), "stripe"),
        ((5, 4.4, 6.5), (11, 9, 11), "sock"),
        ((5, 8.4, 2.8), (11, 11, 6.5), "sock", {"north": "@face"}),
        ((5.4, 8.0, 3.2), (10.6, 8.4, 6.5), "inside"),
        ((5, 7, 2.8), (11, 8.4, 6.5), "sock", {"north": "@jaw", "up": "mouth", "part": "jaw"}),
        ((5, 9, 6.5), (11, 11, 9), "heel"),
    ]
    return c, {"face": face, "jaw": jaw}, boxes


def crib():
    c = {"wood": rgb(0xF2EDE4), "mattress": PINK, "pillow": CREAM, "post": shade(rgb(0xF2EDE4), 0.9)}
    boxes = [
        ((3, 1.5, 4), (13, 3, 12), "mattress"),
        ((3.6, 3, 4.4), (6, 3.8, 11.6), "pillow"),
        ((2.6, 1, 3.6), (13.4, 1.5, 12.4), "wood"),
    ]
    for x in (2.4, 12.6):
        for z in (3.4, 11.6):
            boxes.append(((x, 0, z), (x + 1, 8, z + 1), "post"))
    boxes += [((2.4, 7, 3.6), (13.6, 7.6, 4.2), "wood"), ((2.4, 7, 11.8), (13.6, 7.6, 12.4), "wood"),
              ((2.6, 7, 4.2), (3.2, 7.6, 11.8), "wood"), ((12.8, 7, 4.2), (13.4, 7.6, 11.8), "wood")]
    for x in (4.5, 6.5, 8.5, 10.5):
        boxes.append(((x, 3, 3.7), (x + 0.6, 7, 4.1), "wood"))
        boxes.append(((x, 3, 11.9), (x + 0.6, 7, 12.3), "wood"))
    return c, {}, boxes


def car():
    c = {"body": RED, "cab": RED, "tyre": BLACK, "light": YELLOW, "bumper": SILVER}
    side = windows(6, 3, RED, SKY, 2)
    front = windows(6, 3, RED, SKY, 1)
    boxes = [
        ((4, 1.2, 2.5), (12, 4.2, 13.5), "body"),
        ((5, 4.2, 5), (11, 7.2, 11), "cab", {"east": "@side", "west": "@side", "north": "@front", "south": "@front"}),
        ((5, 2.2, 2.1), (6.4, 3.2, 2.5), "light"),
        ((9.6, 2.2, 2.1), (11, 3.2, 2.5), "light"),
        ((4.4, 1.2, 1.8), (11.6, 1.8, 2.5), "bumper"),
    ]
    boxes += wheels(3, 13, (3.4, 10.4), part="wheel", hub="bumper")
    return c, {"side": side, "front": front}, boxes


def van():
    c = {"body": BLUE, "tyre": BLACK, "light": YELLOW, "stripe": CREAM, "bumper": SILVER}
    side = windows(8, 3, BLUE, SKY, 3)
    front = windows(7, 3, BLUE, SKY, 1)
    boxes = [
        ((4.5, 1.2, 1.5), (11.5, 4.5, 14.5), "body"),
        ((4.5, 4.5, 3.5), (11.5, 9.5, 14.5), "body", {"east": "@side", "west": "@side"}),
        ((4.6, 4.5, 1.6), (11.4, 7.5, 3.5), "body", {"north": "@front"}),
        ((4.4, 3.4, 1.5), (11.6, 4.2, 14.6), "stripe"),
        ((5.2, 2, 1.1), (6.6, 3, 1.5), "light"),
        ((9.4, 2, 1.1), (10.8, 3, 1.5), "light"),
        ((4.8, 1.2, 1.0), (11.2, 1.8, 1.5), "bumper"),
    ]
    boxes += wheels(3.4, 12.6, (2.6, 11.4))
    return c, {"side": side, "front": front}, boxes


def jet():
    c = {"body": CREAM, "nose": RED, "wing": BLUE, "fin": RED, "glass": SKY, "tyre": BLACK, "blade": SILVER}
    boxes = [
        ((7.5, 3.0, -0.6), (8.5, 4.0, 0.2), "fin", {"part": "prop"}),
        ((4.4, 3.25, -0.45), (11.6, 3.75, -0.15), "blade", {"part": "prop"}),
        ((6.5, 2, 1.5), (9.5, 5, 14.5), "body"),
        ((7, 2.5, 0.2), (9, 4.5, 1.5), "nose"),
        ((7, 5, 2.6), (9, 6, 5.6), "glass"),
        ((1, 3, 6), (15, 3.8, 10), "wing"),
        ((7.5, 5, 11.6), (8.5, 9, 14.6), "fin"),
        ((4, 3.6, 12.6), (12, 4.2, 14.6), "wing"),
        ((7.4, 0, 3.4), (8.6, 2, 4.6), "tyre"),
        ((5, 0, 8), (6.2, 3, 9.2), "tyre"),
        ((9.8, 0, 8), (11, 3, 9.2), "tyre"),
    ]
    return c, {}, boxes


def bus():
    c = {"body": YELLOW, "tyre": BLACK, "stripe": BLACK, "light": RED, "bumper": BLACK, "door": shade(YELLOW, 0.88)}
    side = windows(8, 3, YELLOW, SKY, 4)
    front = windows(8, 3, YELLOW, SKY, 1)
    # A folding door on the right, near the front: two leaves, each a tall window in a frame.
    door = Face(2, 7, shade(YELLOW, 0.88))
    door.rect(1, 2, 7, 13, SKY)
    door.rect(1, 2, 7, 4, shade(SKY, 1.12))
    door.rect(1, 16, 7, 26, shade(YELLOW, 0.8))
    boxes = [
        ((3.5, 1.2, 1), (12.5, 4.5, 15), "body"),
        ((3.5, 4.5, 1), (12.5, 9.5, 15), "body", {"east": "@side", "west": "@side", "north": "@front"}),
        ((3.4, 4.2, 1), (12.6, 4.6, 15), "stripe"),
        ((4.2, 2.2, 0.6), (5.6, 3.2, 1), "light"),
        ((10.4, 2.2, 0.6), (11.8, 3.2, 1), "light"),
        ((3.6, 1.2, 0.5), (12.4, 1.8, 1), "bumper"),
        ((12.5, 1.4, 2.0), (12.8, 8.6, 3.8), "door", {"east": "@door", "part": "door0"}),
        ((12.5, 1.4, 3.8), (12.8, 8.6, 5.6), "door", {"east": "@door", "part": "door1"}),
    ]
    boxes += wheels(2.4, 13.6, (2.2, 11.8))
    return c, {"side": side, "front": front, "door": door}, boxes


def ship():
    c = {"hull": RED, "deck": CREAM, "cabin": CREAM, "funnel": RED, "top": BLACK, "keel": BLUE}
    portholes = Face(5, 3, CREAM)
    for x in (4, 10, 16):
        portholes.disc(x, 6, 2, SKY)
    boxes = [
        ((4, 0, 3), (12, 1.6, 13), "keel"),
        ((3.6, 1.6, 2), (12.4, 4.4, 14), "hull"),
        ((3.4, 4.4, 1.8), (12.6, 5, 14.2), "deck"),
        ((5.5, 5, 5.5), (10.5, 8, 11), "cabin", {"east": "@ports", "west": "@ports", "north": "@ports", "south": "@ports"}),
        ((7, 8, 7), (9, 11, 9), "funnel"),
        ((7, 11, 7), (9, 11.8, 9), "top"),
    ]
    return c, {"ports": portholes}, boxes


def doll():
    c = {"dress": PINK, "skin": rgb(0xF6D2B4), "hair": YELLOW, "shoe": RED, "collar": CREAM}
    face = Face(5, 5, rgb(0xF6D2B4))
    face.eye(6, 9, 2.2)
    face.eye(14, 9, 2.2)
    face.cheeks(13, 6.5)
    face.smile(15.5, 2.5)
    face.rect(0, 0, 20, 3, YELLOW)
    boxes = [
        ((5, 0, 6), (7.4, 1.2, 9), "shoe"),
        ((8.6, 0, 6), (11, 1.2, 9), "shoe"),
        ((5, 1.2, 5.5), (11, 6.5, 10.5), "dress"),
        ((5.6, 6.2, 6), (10.4, 6.8, 10), "collar"),
        ((5.5, 6.8, 5.5), (10.5, 11.8, 10.5), "skin", {"north": "@face"}),
        ((5.2, 10.8, 5.2), (10.8, 12.4, 10.8), "hair"),
        ((5.2, 7.2, 9.4), (10.8, 10.8, 10.8), "hair"),
    ]
    boxes += pair(((3.6, 7.6, 7.2), (5.2, 10.2, 8.8), "hair"))
    boxes += pair(((3.8, 2.6, 7), (5, 6, 8.6), "skin"))
    return c, {"face": face}, boxes


def block():
    """A classic wooden ABC block, the same wood and paint as the letter tiles."""
    def face(letter, seed):
        f = Face(8, 8, CLEAR)
        f.px = tile(32, "abc" + seed)
        stamp(f.px, GLYPHS[letter], VOWEL if letter in "AEIOU" else CONSONANT, 9, 7, 3)
        return f
    faces = {"a": face("A", "a"), "b": face("B", "b"), "c": face("C", "c")}
    boxes = [
        ((4, 0, 4), (12, 8, 12), "@a", {"north": "@a", "south": "@a", "east": "@b", "west": "@b", "up": "@c", "down": "@c"}),
    ]
    return {}, faces, boxes

def train():
    c = {"boiler": GREEN, "cab": RED, "roof": BLACK, "chimney": BLACK, "base": BLUE, "tyre": BLACK, "catcher": YELLOW}
    face = Face(5, 5, GREEN)
    face.disc(10, 10, 9.5, CREAM)
    face.eye(6.5, 8, 2)
    face.eye(13.5, 8, 2)
    face.cheeks(12.5, 6)
    face.smile(14.5, 2.5)
    side = windows(6, 3, RED, SKY, 1)
    boxes = [
        ((4, 1, 1.5), (12, 2.4, 14.5), "base"),
        ((5.5, 2.4, 1.5), (10.5, 7.4, 8.5), "boiler", {"north": "@face"}),
        ((4.5, 2.4, 8.5), (11.5, 9.5, 14.5), "cab", {"east": "@side", "west": "@side"}),
        ((4, 9.5, 8), (12, 10.4, 15), "roof"),
        ((7, 7.4, 3), (9, 10.4, 5), "chimney"),
        ((6.4, 10.4, 2.4), (9.6, 11.2, 5.6), "chimney"),
        ((5, 0.4, 0.4), (11, 2, 1.5), "catcher"),
    ]
    boxes += wheels(2.8, 13.2, (2.4, 6.4, 10.8), size=2.4, part="wheel", hub="catcher")
    return c, {"face": face, "side": side}, boxes


def truck():
    c = {"cab": ORANGE, "bed": YELLOW, "tyre": BLACK, "light": YELLOW, "bumper": SILVER, "inside": shade(YELLOW, 0.8),
         "frame": rgb(0x3A3540)}
    front = windows(7, 3, ORANGE, SKY, 1)
    side = windows(5, 3, ORANGE, SKY, 1)
    boxes = [
        ((4.5, 1.2, 1), (11.5, 8, 6), "cab", {"north": "@front", "east": "@side", "west": "@side"}),
        ((4, 1.2, 6), (12, 2.4, 15), "bed", {"part": "bed"}),
        ((4, 2.4, 6), (4.8, 6, 15), "bed", {"part": "bed"}),
        ((11.2, 2.4, 6), (12, 6, 15), "bed", {"part": "bed"}),
        ((4.8, 2.4, 14.2), (11.2, 6, 15), "bed", {"part": "bed"}),
        ((4.8, 2.4, 6), (11.2, 2.6, 14.2), "inside", {"part": "bed"}),
        ((5, 2.2, 0.6), (6.4, 3.2, 1), "light"),
        ((9.6, 2.2, 0.6), (11, 3.2, 1), "light"),
        ((4.6, 1.2, 0.4), (11.4, 1.8, 1), "bumper"),
        # The chassis everything stands on: two rails the length of the truck, an axle through each
        # pair of wheels, the hinge the bed tips on at the back, and a bumper there too.
        ((4.6, 0.6, 0.6), (5.8, 1.2, 15.2), "frame"),
        ((10.2, 0.6, 0.6), (11.4, 1.2, 15.2), "frame"),
        ((4.0, 1.0, 3.2), (12.0, 1.6, 3.8), "frame"),
        ((4.0, 1.0, 12.8), (12.0, 1.6, 13.4), "frame"),
        ((5.8, 0.8, 14.4), (10.2, 1.2, 15.2), "frame"),
        ((6.6, 1.2, 14.6), (9.4, 1.6, 15.4), "frame"),
        ((4.6, 0.8, 15.2), (11.4, 1.6, 15.8), "bumper"),
    ]
    boxes += wheels(2.8, 13.2, (2.2, 11.8), size=2.6)
    return c, {"front": front, "side": side}, boxes


TOYS = {
    "drum": drum, "kite": kite, "top": top, "ring": ring, "cap": cap, "wig": wig, "net": net, "pin": pin,
    "sock": sock, "crib": crib, "car": car, "van": van, "jet": jet, "bus": bus, "ship": ship, "doll": doll,
    "block": block, "train": train, "truck": truck,
}

NAMES = {"top": "Spinning top", "ring": "Stacking rings", "cap": "Toy cap", "wig": "Dress-up wig", "net": "Butterfly net",
         "pin": "Bowling pin", "sock": "Sock puppet", "crib": "Doll's crib", "car": "Toy car", "van": "Toy van",
         "jet": "Toy jet", "bus": "Toy bus", "ship": "Toy ship", "doll": "Doll", "block": "ABC block",
         "train": "Toy train", "truck": "Toy truck", "drum": "Drum", "kite": "Kite"}

# Worn on the head, as the little models round G made (generate_things.py writes those).
WORN = {"cap", "wig"}


# --- The sheet and the model -----------------------------------------------------------------

def build(name, make):
    colours, pictures, boxes = make()
    assert len(colours) <= 16, name
    assert len(pictures) <= 3, name
    rng = random.Random("toy:" + name)
    sheet = [[CLEAR] * 64 for _ in range(64)]
    swatch = {}
    for i, (role, colour) in enumerate(colours.items()):
        cx, cy = (i % 4) * 8, (i // 4) * 8
        for y, row in enumerate(felt(rng, colour)):
            for x, px in enumerate(row):
                sheet[cy + y][cx + x] = px
        swatch[role] = [cx / 4 + 0.5, cy / 4 + 0.5, cx / 4 + 1.5, cy / 4 + 1.5]
    picture_uv = {}
    for (key, picture), (qx, qy) in zip(pictures.items(), QUARTERS):
        assert picture.w <= 32 and picture.h <= 32, (name, key)
        for y, row in enumerate(picture.pixels()):
            for x, px in enumerate(row):
                sheet[qy + y][qx + x] = px
        picture_uv[key] = [qx / 4, qy / 4, qx / 4 + picture.w / 4, qy / 4 + picture.h / 4]
    write_png(ASSETS / f"textures/block/toy_{name}.png", sheet)

    elements = []
    parts = {}
    for box in boxes:
        lo, hi, role = box[0], box[1], box[2]
        sides = box[3] if len(box) > 3 else {}
        faces = {}
        for side in ("north", "south", "east", "west", "up", "down"):
            which = sides.get(side, role)
            uv = picture_uv[which[1:]] if which.startswith("@") else swatch[which]
            faces[side] = {"uv": uv, "texture": "#toy"}
        element = {"from": list(lo), "to": list(hi), "faces": faces}
        if "rot" in sides:
            axis, angle, origin = sides["rot"]
            element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
        elements.append(element)
        parts.setdefault(sides.get("part", "body"), []).append(element)
    write(ASSETS / f"models/block/toy_{name}.json", model(name, elements))
    # A toy whose play moves a part of it (the truck's bed, the car's wheels) is also drawn in pieces:
    # its body and each part on its own, for the displays that play it (play/Anims.java). Shown by an
    # item display, so each has an item model too, under toy_part/.
    if len(parts) > 1:
        for piece, piece_elements in parts.items():
            write(ASSETS / f"models/block/toy_{name}_{piece}.json", model(name, piece_elements))
            write(ASSETS / f"items/toy_part/{name}_{piece}.json",
                  {"model": {"type": "minecraft:model", "model": f"{NS}:block/toy_{name}_{piece}"}})


def model(name, elements):
    return {
        "parent": "minecraft:block/block",
        "textures": {"toy": f"{NS}:block/toy_{name}", "particle": f"{NS}:block/toy_{name}"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [20, 205, 0], "translation": [0, 1, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def main():
    for name, make in TOYS.items():
        build(name, make)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    write(ASSETS / "blockstates/toy.json", {
        "variants": {
            f"facing={facing},hidden={hidden},kind={name}": {"model": f"{NS}:block/hidden"} if hidden == "true"
            else {"model": f"{NS}:block/toy_{name}", "y": y}
            for name in TOYS for facing, y in turn.items() for hidden in ("false", "true")
        }
    })
    for name in TOYS:
        model = {"type": "minecraft:model", "model": f"{NS}:block/toy_{name}"}
        if name in WORN:
            # Worn, the little model on the head; anywhere else, the toy.
            model = {"type": "minecraft:select", "property": "minecraft:display_context",
                     "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}],
                     "fallback": model}
        write(ASSETS / f"items/toy_{name}.json", {"model": model})
    write(DATA / "loot_table/blocks/toy.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:toy", "properties": {"kind": name}}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:toy_{name}"}],
        } for name in TOYS],
        "random_sequence": f"{NS}:blocks/toy",
    })
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    lang = {k: v for k, v in lang.items() if not k.startswith(f"item.{NS}.toy_")}
    lang[f"block.{NS}.toy"] = "Toy"
    for name in TOYS:
        lang[f"item.{NS}.toy_{name}"] = NAMES.get(name, name.capitalize())
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {len(TOYS)} toys")


if __name__ == "__main__":
    main()
