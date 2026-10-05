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
NS = "learning-blocks-justfatlard"
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
    """A classic diamond kite: a big flat sail in four colours with its cross spars, leaning back on
    the reel its string winds onto, and a bow-tied tail trailing behind on the floor. The sail, the
    tail and the string are parts: the kite lifts off its reel and flies."""
    c = {"red": RED, "yellow": YELLOW, "blue": BLUE, "green": GREEN, "spar": shade(WOOD, 0.75), "string": CREAM,
         "bow": PINK, "reel": WOOD, "flange": RED, "wound": shade(CREAM, 0.92)}
    lean = ("x", 22.5, (8, 2.4, 8.6))
    sail = {"part": "sail", "rot": lean}
    tip, wide, top, half = 2.4, 10.6, 15.8, 6.6
    boxes = []
    y = tip
    while y < top - 0.01:
        y1 = min(top, y + 0.8)
        mid = (y + y1) / 2
        hw = half * (mid - tip) / (wide - tip) if mid < wide else half * (top - mid) / (top - wide)
        hw = max(0.6, hw)
        upper = mid >= wide
        boxes.append(((8 - hw, y, 8.3), (8, y1, 8.9), "red" if upper else "blue", sail))
        boxes.append(((8, y, 8.3), (8 + hw, y1, 8.9), "yellow" if upper else "green", sail))
        y = y1
    boxes += [
        ((7.7, tip, 8.0), (8.3, top, 8.3), "spar", sail),
        ((8 - half + 0.4, wide - 0.3, 8.0), (8 + half - 0.4, wide + 0.3, 8.3), "spar", sail),
        # The reel, in front: a spool with the string wound on, and the string up to the sail's tip.
        ((6.6, 0.5, 3.2), (9.4, 2.5, 5.2), "wound"),
        ((6.0, 0, 2.8), (6.6, 3.0, 5.6), "flange"),
        ((9.4, 0, 2.8), (10.0, 3.0, 5.6), "flange"),
        ((7.9, 2.3, 5.2), (8.1, 2.5, 8.6), "string", {"part": "string"}),
    ]
    tail = {"part": "tail"}
    boxes += [
        ((7.9, 0.3, 8.7), (8.1, 2.4, 8.9), "string", tail),
        ((7.9, 0, 8.9), (8.1, 0.2, 15.6), "string", tail),
        ((6.9, 0, 10.4), (9.1, 0.5, 11.2), "bow", tail),
        ((6.9, 0, 12.6), (9.1, 0.5, 13.4), "bow", tail),
        ((6.9, 0, 14.8), (9.1, 0.5, 15.6), "bow", tail),
    ]
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
    """Stacking rings: square rings, each four bars round a hole, on a wooden post with a round knob."""
    c = {"base": WOOD, "red": RED, "orange": ORANGE, "yellow": YELLOW, "green": GREEN, "blue": BLUE, "knob": RED,
         "post": shade(WOOD, 0.9)}
    hole0, hole1 = 6.7, 9.3
    boxes = [
        ((4, 0, 4), (12, 1, 12), "base"),
        ((7.3, 1, 7.3), (8.7, 10, 8.7), "post"),
        # The knob, rounded: a wide middle between narrower top and bottom.
        ((7.1, 10, 7.1), (8.9, 10.4, 8.9), "knob"),
        ((6.7, 10.4, 6.7), (9.3, 11.4, 9.3), "knob"),
        ((7.1, 11.4, 7.1), (8.9, 11.8, 8.9), "knob"),
    ]
    rings = [(4, 1, 2.6, "red"), (4.5, 2.6, 4.2, "orange"), (5, 4.2, 5.8, "yellow"), (5.5, 5.8, 7.4, "green"), (6, 7.4, 9, "blue")]
    for k, (lo, y0, y1, colour) in enumerate(rings):
        hi = 16 - lo
        part = {"part": f"ring{k}"}
        boxes += [
            ((lo, y0, lo), (hi, y1, hole0), colour, part),
            ((lo, y0, hole1), (hi, y1, hi), colour, part),
            ((lo, y0, hole0), (hole0, y1, hole1), colour, part),
            ((hole1, y0, hole0), (hi, y1, hole1), colour, part),
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
    # A door on the right, near the front: two leaves, each a tall window in a frame.
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
        # The door, flush on the right near the front: painted, two leaves.
        ((12.5, 1.4, 2.0), (12.6, 8.6, 3.8), "door", {"east": "@door"}),
        ((12.5, 1.4, 3.8), (12.6, 8.6, 5.6), "door", {"east": "@door"}),
    ]
    boxes += wheels(2.4, 13.6, (2.2, 11.8), part="wheel", hub="light")
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



# --- The counting toys (round M): for Numbers, earned by mastering numbers -------------------

# A die's pips on a 3x3 grid: which cells for each number.
PIPS = {1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8]}


def pips(face, n, colour, x0=0, y0=0, w=None, h=None, r=None, inset=0):
    """A die's pips for n in this part of a face (pixels), on its 3x3 grid, kept {inset} in from its edges."""
    w = face.w if w is None else w
    h = face.h if h is None else h
    r = r or min(w, h) / 9
    x0, y0, w, h = x0 + inset, y0 + inset, w - 2 * inset, h - 2 * inset
    for cell in PIPS[n]:
        cx = x0 + w * (cell % 3 * 2 + 1) / 6
        cy = y0 + h * (cell // 3 * 2 + 1) / 6
        face.disc(cx, cy, r, colour)


def abacus():
    """A counting frame: a wooden frame on two feet, four wires of five bright beads, some pushed
    across to count."""
    c = {"wood": WOOD, "foot": shade(WOOD, 0.85), "wire": SILVER, "red": RED, "yellow": YELLOW, "green": GREEN, "blue": BLUE}
    boxes = [
        ((2, 0.8, 7.2), (3.2, 13, 8.8), "wood"), ((12.8, 0.8, 7.2), (14, 13, 8.8), "wood"),
        ((1.4, 0, 5.4), (3.8, 0.8, 10.6), "foot"), ((12.2, 0, 5.4), (14.6, 0.8, 10.6), "foot"),
        ((3.2, 11.8, 7.4), (12.8, 13, 8.6), "wood"), ((3.2, 1.6, 7.4), (12.8, 2.8, 8.6), "wood"),
    ]
    # Counted beads pushed to the left, the rest to the right: 5, 3, 4, 1 counted.
    for row, (colour, counted) in enumerate([("red", 5), ("yellow", 3), ("green", 4), ("blue", 1)]):
        y = 10.2 - row * 2.2
        boxes.append(((3.2, y - 0.2, 7.8), (12.8, y + 0.2, 8.2), "wire"))
        for i in range(5):
            x = 3.3 + i * 1.3 if i < counted else 12.7 - (5 - i) * 1.3
            boxes.append(((x, y - 0.8, 7.2), (x + 1.2, y + 0.8, 8.8), colour))
    return c, {}, boxes


def dice():
    """Two big wooden dice, white with black pips: one square on, one turned a little."""
    c = {"white": CREAM, "black": BLACK}
    faces = {}
    for n in (5, 3, 2):
        f = Face(6, 6, CREAM)
        pips(f, n, BLACK)
        faces[str(n)] = f
    small = {"north": "@3", "south": "@3", "east": "@2", "west": "@2", "up": "@5", "down": "@5"}
    boxes = [
        ((2, 0, 3), (8, 6, 9), "white", {"north": "@5", "south": "@2", "east": "@3", "west": "@3", "up": "@2", "down": "@5"}),
        ((9.5, 0, 1.5), (14, 4.5, 6), "white", dict(small, rot=("y", 22.5, (11.75, 2.25, 3.75)))),
    ]
    return c, faces, boxes


def dominoes():
    """Dominoes: black with white pips, two lying crossed and one standing up behind."""
    c = {"black": BLACK, "white": CREAM}

    def domino(top, bottom, across=False):
        f = Face(7, 3.5, BLACK) if across else Face(3.5, 7, BLACK)
        r = 1.6
        if across:
            pips(f, top, CREAM, 0, 0, f.w / 2, f.h, r, 2)
            pips(f, bottom, CREAM, f.w / 2, 0, f.w / 2, f.h, r, 2)
            f.rect(f.w / 2 - 0.5, 1, f.w / 2 + 0.5, f.h - 1, CREAM)
        else:
            pips(f, top, CREAM, 0, 0, f.w, f.h / 2, r, 2)
            pips(f, bottom, CREAM, 0, f.h / 2, f.w, f.h / 2, r, 2)
            f.rect(1, f.h / 2 - 0.5, f.w - 1, f.h / 2 + 0.5, CREAM)
        return f
    faces = {"a": domino(2, 3), "b": domino(4, 1, across=True), "c": domino(6, 5)}
    # Big: a domino 3 by 6 (two 3x3 halves), so the pips read.
    boxes = [
        ((1.5, 0, 1.5), (5, 1, 8.5), "black", {"up": "@a"}),
        ((6, 0, 2), (13, 1, 5.5), "black", {"up": "@b"}),
        ((8, 0, 9), (11.5, 7, 10.2), "black", {"north": "@c", "south": "@c"}),
    ]
    return c, faces, boxes


def sorter():
    """A shape sorter: a red box with a circle, a square and a triangle cut in its lid, and the
    shapes beside it."""
    c = {"red": RED, "blue": BLUE, "yellow": YELLOW, "green": GREEN}
    lid = Face(8, 8, RED)
    hole = shade(RED, 0.45)
    lid.disc(9, 9, 5, hole)
    lid.rect(18, 4, 28, 14, hole)
    for row in range(10):
        lid.rect(16 - row, 18 + row, 17 + row, 19 + row, hole)
    boxes = [
        ((2, 0, 6.5), (10, 6, 14.5), "red", {"up": "@lid"}),
        # In front of it: a square block, a stepped triangle and a round one (two crossed boxes).
        ((11.5, 0, 8), (14.5, 3, 11), "green"),
        ((1.5, 0, 1.5), (5.5, 1, 4), "yellow"), ((2.2, 1, 1.5), (4.8, 2, 4), "yellow"), ((2.9, 2, 1.5), (4.1, 3, 4), "yellow"),
        ((8, 0, 1.5), (12, 2.5, 4.5), "blue"), ((8.5, 0, 1), (11.5, 2.5, 5), "blue"),
    ]
    return c, {"lid": lid}, boxes


def scale():
    """A balance scale tipped by its load: a wooden post, a red beam leaning down to the heavier
    pan, a big block on that side and a little one on the other."""
    c = {"wood": WOOD, "red": RED, "knob": YELLOW, "pan": SILVER, "string": CREAM, "big": BLUE, "small": GREEN}
    tilt = ("z", 22.5, (8, 10.4, 8))
    boxes = [
        ((4, 0, 6), (12, 1, 10), "wood"),
        ((7.3, 1, 7.3), (8.7, 10, 8.7), "wood"),
        ((1.5, 10, 7.5), (14.5, 10.8, 8.5), "red", {"rot": tilt}),
        ((7, 9.6, 7), (9, 11.2, 9), "knob"),
        # The low side: string, pan and the big block.
        ((2.3, 4.6, 7.8), (2.7, 8.2, 8.2), "string"),
        ((0.5, 4, 6), (4.5, 4.6, 10), "pan"),
        ((1.2, 4.6, 6.7), (3.8, 7.2, 9.3), "big"),
        # The high side.
        ((13.3, 9.2, 7.8), (13.7, 12.6, 8.2), "string"),
        ((11.5, 8.6, 6), (15.5, 9.2, 10), "pan"),
        ((12.8, 9.2, 7.3), (14.2, 10.6, 8.7), "small"),
    ]
    return c, {}, boxes


# --- The thinking toys (round N): for Logic, earned by mastering logic --------------------------

def puzzle():
    """Jigsaw pieces: two lying flat, fitted together (a knob in a notch), and one standing behind."""
    c = {"red": RED, "green": GREEN, "yellow": YELLOW, "edge": shade(RED, 0.8), "gedge": shade(GREEN, 0.8)}
    boxes = [
        # The red piece and its knob, into the green piece's notch.
        ((1.5, 0, 2), (7, 1.2, 7.5), "red"), ((7, 0, 4), (8.6, 1.2, 5.6), "red"),
        ((7, 0, 2), (12.5, 1.2, 4), "green"), ((7, 0, 5.6), (12.5, 1.2, 7.5), "green"), ((8.6, 0, 4), (12.5, 1.2, 5.6), "green"),
        # A yellow one standing up behind, its knob on top.
        ((4, 0, 9.5), (10.5, 6, 10.7), "yellow"), ((6.4, 6, 9.5), (8.1, 7.6, 10.7), "yellow"),
    ]
    return c, {}, boxes


def cups():
    """Stacking cups: four upside down in a tower, biggest at the bottom, and a blue one beside, the
    right way up and open."""
    c = {"red": RED, "orange": ORANGE, "yellow": YELLOW, "green": GREEN, "blue": BLUE, "inside": shade(BLUE, 0.6)}
    boxes = []
    y = 0.0
    for size, colour in zip([7.0, 5.8, 4.6, 3.4], ["red", "orange", "yellow", "green"]):
        lo = 6.0 - size / 2
        hi = 6.0 + size / 2
        boxes.append(((lo, y, lo + 1), (hi, y + 2.4, hi + 1), colour))
        boxes.append(((lo - 0.3, y, lo + 0.7), (hi + 0.3, y + 0.5, hi + 1.3), colour))
        y += 2.4
    # The open one: four walls and a floor.
    x0, x1, z0, z1, h, t = 10.8, 15.2, 1, 5.4, 3.2, 0.5
    boxes += [
        ((x0, 0, z0), (x1, 0.5, z1), "inside"),
        ((x0, 0, z0), (x1, h, z0 + t), "blue"), ((x0, 0, z1 - t), (x1, h, z1), "blue"),
        ((x0, 0, z0), (x0 + t, h, z1), "blue"), ((x1 - t, 0, z0), (x1, h, z1), "blue"),
    ]
    return c, {}, boxes


def maze():
    """A bead maze: a wooden base and three bright wires looping over it, beads on each."""
    c = {"wood": WOOD, "red": RED, "blue": BLUE, "green": GREEN, "yellow": YELLOW, "orange": ORANGE, "purple": PURPLE}
    boxes = [((1.5, 0, 4), (14.5, 1.6, 12), "wood")]
    wires = [(2.5, 13.5, 11.0, 6.0, "red"), (4.2, 11.8, 8.4, 8.0, "blue"), (5.9, 10.1, 5.8, 10.0, "green")]
    w = 0.5
    for x0, x1, top, z, colour in wires:
        boxes += [
            ((x0, 1.6, z), (x0 + w, top, z + w), colour),
            ((x1 - w, 1.6, z), (x1, top, z + w), colour),
            ((x0, top - w, z), (x1, top, z + w), colour),
        ]
    beads = [(5, 11.0 - 0.25, 6.25, "yellow"), (6.4, 11.0 - 0.25, 6.25, "purple"), (13.75, 6.0, 6.25, "orange"),
             (7.5, 8.4 - 0.25, 8.25, "yellow"), (4.45, 4.5, 8.25, "red"), (8.0, 5.8 - 0.25, 10.25, "orange"), (9.0, 5.8 - 0.25, 10.25, "purple")]
    for x, y, z, colour in beads:
        boxes.append(((x - 0.7, y - 0.7, z - 0.7), (x + 0.7, y + 0.7, z + 0.7), colour))
    return c, {}, boxes


def cube():
    """A puzzle cube, mixed up: black, with three by three coloured squares on every side."""
    c = {"black": BLACK}
    stickers = [RED, BLUE, YELLOW, GREEN, ORANGE, CREAM]
    rng = random.Random("cube")
    faces = {}
    for name in ("a", "b", "c"):
        f = Face(8, 8, BLACK)
        cell = f.w / 3
        for row in range(3):
            for col in range(3):
                f.rect(col * cell + 1, row * cell + 1, (col + 1) * cell - 1, (row + 1) * cell - 1, rng.choice(stickers))
        faces[name] = f
    boxes = [((4, 0, 4), (12, 8, 12), "black", {"north": "@a", "south": "@b", "east": "@c", "west": "@b", "up": "@c", "down": "@a"})]
    return c, faces, boxes


def robot():
    """A toy robot: square body and head, a face of lights, an antenna with a red ball, and arms."""
    c = {"body": BLUE, "head": SILVER, "limb": shade(SILVER, 0.8), "red": RED, "dark": BLACK}
    face = Face(6, 4, SILVER)
    face.rect(4, 4, 9, 9, YELLOW)
    face.rect(15, 4, 20, 9, YELLOW)
    face.rect(6, 12, 18, 14, BLACK)
    chest = Face(8, 6, BLUE)
    chest.rect(6, 5, 26, 19, shade(BLUE, 0.75))
    chest.disc(11, 12, 2.5, RED)
    chest.disc(18, 12, 2.5, YELLOW)
    boxes = [
        ((5, 0, 7), (7, 3.5, 9), "limb"), ((9, 0, 7), (11, 3.5, 9), "limb"),
        ((4.4, 0, 6.4), (7.4, 0.8, 9.4), "dark"), ((8.6, 0, 6.4), (11.6, 0.8, 9.4), "dark"),
        ((4, 3.5, 6), (12, 9.5, 10), "body", {"north": "@chest"}),
        ((2.4, 5, 7.2), (4, 9, 8.8), "limb"), ((12, 5, 7.2), (13.6, 9, 8.8), "limb"),
        ((2.2, 4, 7), (4.2, 5.2, 9), "red"), ((11.8, 4, 7), (13.8, 5.2, 9), "red"),
        ((5, 9.5, 6.5), (11, 13.5, 9.5), "head", {"north": "@face"}),
        ((7.6, 13.5, 7.6), (8.4, 15, 8.4), "limb"), ((7.1, 15, 7.1), (8.9, 16, 8.9), "red"),
    ]
    return c, {"face": face, "chest": chest}, boxes

TOYS = {
    "drum": drum, "kite": kite, "top": top, "ring": ring, "cap": cap, "wig": wig, "net": net, "pin": pin,
    "sock": sock, "crib": crib, "car": car, "van": van, "jet": jet, "bus": bus, "ship": ship, "doll": doll,
    "block": block, "train": train, "truck": truck,
    "abacus": abacus, "dice": dice, "dominoes": dominoes, "sorter": sorter, "scale": scale,
    "puzzle": puzzle, "cups": cups, "maze": maze, "cube": cube, "robot": robot,
}

NAMES = {"top": "Spinning top", "ring": "Stacking rings", "cap": "Toy cap", "wig": "Dress-up wig", "net": "Butterfly net",
         "pin": "Bowling pin", "sock": "Sock puppet", "crib": "Doll's crib", "car": "Toy car", "van": "Toy van",
         "jet": "Toy jet", "bus": "Toy bus", "ship": "Toy ship", "doll": "Doll", "block": "ABC block",
         "train": "Toy train", "truck": "Toy truck", "drum": "Drum", "kite": "Kite",
         "abacus": "Abacus", "dice": "Dice", "dominoes": "Dominoes", "sorter": "Shape sorter",
         "scale": "Balance scale", "puzzle": "Puzzle pieces", "cups": "Stacking cups", "maze": "Bead maze", "cube": "Puzzle cube",
         "robot": "Toy robot"}

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
            # 26's shape (see generate_plushies.py): the old one is not read, and every toy would drop.
            "condition": {"type": "minecraft:match_block", "blocks": f"{NS}:toy", "state": {"kind": name}},
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
