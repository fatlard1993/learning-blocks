#!/usr/bin/env python3
"""
Draw the feelings: little speech bubbles with a pixel face in them, shown over a creature, a
character, a plushie or Bob (glad, sad, mad, sleepy, surprised, scared, love, cold, hot), and the
waving hand a character holds up to say hello.

Each is a 32x32 item texture, shown on an item display that faces whoever looks at it. Nothing is
registered: the display carries a plain item with its model swapped for one of these, by the item
model component, so they need no item of their own.

Writes textures/item/emote/<name>.png, models/item/emote_<name>.json and items/emote/<name>.json.

Usage: python3 generate_emotes.py
"""

import json
import pathlib

from generate_textures import write_png

HERE = pathlib.Path(__file__).parent
NS = "phonics-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS

CLEAR = (0, 0, 0, 0)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


INK = rgb(0x2E2A30)
WHITE = rgb(0xFFFFFF)
PAPER = rgb(0xFBF8F0)
CHEEK = rgb(0xF29BAE)
TEAR = rgb(0x3A7BD5)
DROP = rgb(0x7FC4F0)
RED = rgb(0xE8483C)
SNOW = rgb(0xE6F4FF)


class Canvas:
    def __init__(self):
        self.px = [[CLEAR] * 32 for _ in range(32)]

    def dot(self, x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            self.px[y][x] = c

    def disc(self, cx, cy, r, c):
        for y in range(32):
            for x in range(32):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.dot(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            self.dot(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)

    def pixels(self):
        return self.px


def bubble(face):
    """A round bubble with a tail, outlined, and a face of this colour in it."""
    c = Canvas()
    # The tail, pointing down to whoever is feeling it.
    for y in range(22, 32):
        right = 13 + (31 - y)
        for x in range(12, min(right, 21) + 1):
            c.dot(x, y, INK)
    c.disc(16, 14, 13.5, INK)
    for y in range(22, 30):
        right = 13 + (29 - y)
        for x in range(13, min(right, 20) + 1):
            c.dot(x, y, PAPER)
    c.disc(16, 14, 12.4, PAPER)
    c.disc(16, 14, 9.6, INK)
    c.disc(16, 14, 8.6, face)
    return c


def eyes(c, y=12, gap=3):
    for x in (16 - gap - 2, 16 + gap):
        c.rect(x, y, x + 2, y + 2, INK)


def cheeks(c, y=16):
    for x in (10, 21):
        c.dot(x, y, CHEEK)
        c.dot(x + 1, y, CHEEK)


def smile(c, y=16):
    c.line(12, y, 13, y + 1, INK)
    c.line(14, y + 2, 17, y + 2, INK)
    c.line(18, y + 1, 19, y, INK)


def frown(c, y=19):
    c.line(12, y, 13, y - 1, INK)
    c.line(14, y - 2, 17, y - 2, INK)
    c.line(18, y - 1, 19, y, INK)


def glad():
    c = bubble(rgb(0xF6D146))
    eyes(c)
    cheeks(c)
    smile(c)
    return c


def sad():
    c = bubble(rgb(0x9CC8F0))
    eyes(c, 12)
    c.line(10, 10, 12, 9, INK)
    c.line(19, 9, 21, 10, INK)
    frown(c)
    # A tear, running down.
    c.rect(11, 14, 12, 17, TEAR)
    c.rect(10, 16, 13, 18, TEAR)
    return c


def mad():
    c = bubble(rgb(0xF07060))
    eyes(c, 12)
    # Cross eyebrows, down at the middle.
    c.line(10, 9, 14, 11, INK)
    c.line(21, 9, 17, 11, INK)
    c.rect(13, 17, 19, 18, INK)
    return c


def sleepy():
    c = bubble(rgb(0xC8B8F0))
    c.rect(11, 13, 14, 14, INK)
    c.rect(18, 13, 21, 14, INK)
    c.rect(12, 18, 16, 19, INK)
    # A sleepy bubble at the nose.
    c.disc(21.5, 18.5, 2.6, INK)
    c.disc(21.5, 18.5, 1.8, rgb(0xE8F6FF))
    c.dot(21, 17, WHITE)
    return c


def surprised():
    c = bubble(rgb(0xF6D146))
    for x in (12, 20):
        c.disc(x, 12, 2.2, WHITE)
        c.disc(x, 12, 1.0, INK)
    c.disc(16, 18.5, 2.4, INK)
    c.disc(16, 18.5, 1.2, rgb(0xB0403A))
    return c


def scared():
    """Scared, but cute: wide eyes, a wobbly mouth and a drop of sweat."""
    c = bubble(rgb(0xB8E8C0))
    for x in (12, 20):
        c.disc(x, 12.5, 2.4, WHITE)
        c.rect(x - 1, 12, x, 14, INK)
    for i, x in enumerate(range(11, 21)):
        c.dot(x, 18 + (i % 2), INK)
    c.rect(23, 8, 25, 12, DROP)
    c.dot(24, 7, DROP)
    return c


def love():
    c = bubble(rgb(0xF6D146))
    heart = [".X.X.", "XXXXX", "XXXXX", ".XXX.", "..X.."]
    for cx in (12, 20):
        for y, row in enumerate(heart):
            for x, on in enumerate(row):
                if on == "X":
                    c.dot(cx - 2 + x, 9 + y, RED)
    smile(c, 17)
    return c


def cold():
    c = bubble(rgb(0xBFE6FF))
    eyes(c, 12)
    # Chattering teeth: a zig-zag between two lips.
    c.rect(11, 16, 21, 21, INK)
    c.rect(12, 17, 20, 20, WHITE)
    for i, x in enumerate(range(12, 20)):
        c.dot(x, 18 + (i % 2), INK)
    # Frost on the top of the head.
    for x, y in ((13, 7), (16, 6), (19, 7), (11, 9), (21, 9)):
        c.dot(x, y, SNOW)
    return c


def hot():
    c = bubble(rgb(0xF59A50))
    c.line(10, 12, 13, 13, INK)
    c.line(13, 13, 10, 14, INK)
    c.line(21, 12, 18, 13, INK)
    c.line(18, 13, 21, 14, INK)
    c.rect(13, 17, 19, 20, INK)
    c.rect(14, 18, 18, 20, RED)
    # Drops of sweat, both sides.
    for x in (7, 24):
        c.rect(x, 9, x + 2, 13, DROP)
        c.dot(x + (1 if x < 16 else 0), 8, DROP)
    return c


def hand():
    """A waving hand, hello: an open palm, four fingers a little spread (the middle one tallest),
    the thumb angled out from the side of the palm, outlined, with motion arcs either side; in the
    same bubble as the feelings."""
    c = bubble(PAPER)
    c.disc(16, 14, 12.4, PAPER)
    skin, line, arc = rgb(0xF6D2B4), rgb(0x8A5A44), rgb(0x5EB8F2)
    ox, oy = 7, 3
    mask = set()
    # Fingers: (left outline column, top row, lean): two skin columns each, an outline between.
    for x0, top, lean in ((3, 3, -1), (6, 1, 0), (9, 2, 0), (12, 4, 1)):
        for row in range(top, 10):
            shift = round(lean * (9 - row) / 6)
            for col in (x0 + 1, x0 + 2):
                mask.add((col + shift, row))
    for row in range(9, 17):
        for col in range(4, 15):
            mask.add((col, row))
    mask -= {(4, 16), (14, 16)}
    # The thumb, out and up from the side of the palm.
    for col, row in ((3, 12), (3, 13), (2, 11), (2, 12), (1, 10), (1, 11), (0, 9), (0, 10), (3, 11), (2, 10), (1, 12), (0, 11), (2, 13)):
        mask.add((col, row))
    for col, row in mask:
        for n in ((col + 1, row), (col - 1, row), (col, row + 1), (col, row - 1)):
            if n not in mask:
                c.dot(ox + n[0], oy + n[1], line)
    for col, row in mask:
        c.dot(ox + col, oy + row, skin)
    # The crease of the palm.
    for col in range(7, 12):
        c.dot(ox + col, oy + 13, rgb(0xE8B896))
    # Motion arcs, either side.
    for x, y in ((7, 6), (6, 7), (6, 8), (7, 9), (24, 6), (25, 7), (25, 8), (24, 9)):
        c.dot(x, y, arc)
    return c


EMOTES = {
    "glad": glad, "sad": sad, "mad": mad, "sleepy": sleepy, "surprised": surprised, "scared": scared,
    "love": love, "cold": cold, "hot": hot, "hand": hand,
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for name, draw in EMOTES.items():
        write_png(ASSETS / f"textures/item/emote/{name}.png", draw().pixels())
        write(ASSETS / f"models/item/emote_{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/emote/{name}"},
        })
        write(ASSETS / f"items/emote/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/emote_{name}"}})
    print(f"Wrote {len(EMOTES)} emotes")


if __name__ == "__main__":
    main()
