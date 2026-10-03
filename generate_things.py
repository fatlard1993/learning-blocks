#!/usr/bin/env python3
"""
The things in the classroom that are not toys, and the picture cards.

The worn cap and wig (the toy cap's and wig's look on a head; the toys themselves are made by
generate_toys.py), three props to put down (a bin, a bathtub, a cardboard box: block models
over vanilla textures), and picture cards for words no item shows (the moon, a sad face).

Usage: python3 generate_things.py
"""

import json
import pathlib

from generate_textures import write_png

HERE = pathlib.Path(__file__).parent
NS = "phonics-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS
DATA = HERE / "src/main/resources/data" / NS


def rgb(h, a=0xFF):
    return ((h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF, a)


CLEAR = (0, 0, 0, 0)
OUT = rgb(0x2B2222)

# Picture cards, for words no item shows: drawn on a panel by Pandorical.
CARDS = {
    "moon": ({"n": rgb(0x1E2A5A), "y": rgb(0xF6E27A), "o": rgb(0xD9C25A), "w": rgb(0xFFFFFF)}, [
        "nnnnnnnnnnnnnnnn",
        "nnwnnnnnnnnnnwnn",
        "nnnnnnyyyynnnnnn",
        "nnnnnyyyyyynnnnn",
        "nnnnyyyyynnnnnnn",
        "nnnyyyyynnnnnnwn",
        "nnnyyyyonnnnnnnn",
        "nnyyyyoonnnnnnnn",
        "nnyyyyoonnnnnnnn",
        "nnnyyyyonnnnnnnn",
        "nnnyyyyynnnnnnnn",
        "nnnnyyyyynnnnwnn",
        "nnnnnyyyyyynnnnn",
        "nnwnnnyyyynnnnnn",
        "nnnnnnnnnnnnnnnn",
        "nnnnnnnnnnwnnnnn",
    ]),
    "sad": ({"y": rgb(0xF6D146), "o": rgb(0xD9A82E), "k": rgb(0x2B2222), "b": rgb(0x5EB8F2)}, [
        "................",
        ".....######.....",
        "...##yyyyyy##...",
        "..#yyyyyyyyyy#..",
        ".#yyyyyyyyyyyy#.",
        ".#yyykyyyykyyy#.",
        "#yyyykyyyykyyyy#",
        "#yyyybyyyyyyyyy#",
        "#yyyybyyyyyyyyy#",
        "#yyyyyyyyyyyyyy#",
        "#yyyyykkkkyyyyy#",
        ".#yyykyyyykyyy#.",
        ".#yyyyyyyyyyyy#.",
        "..#yyyyyyyyyy#..",
        "...##oooooo##...",
        ".....######.....",
    ]),
    "glad": ({"y": rgb(0xF6D146), "o": rgb(0xD9A82E), "k": rgb(0x2B2222), "p": rgb(0xF29CB0), "r": rgb(0xC0392B)}, [
        "................",
        ".....######.....",
        "...##yyyyyy##...",
        "..#yyyyyyyyyy#..",
        ".#yyyyyyyyyyyy#.",
        ".#yyykyyyykyyy#.",
        "#yyyykyyyykyyyy#",
        "#yyyyyyyyyyyyyy#",
        "#ypyyyyyyyyyypy#",
        "#yykyyyyyyyykyy#",
        "#yyykkkkkkkkyyy#",
        ".#yyykrrrrkyyy#.",
        ".#yyyykkkkyyyy#.",
        "..#yyyyyyyyyy#..",
        "...##oooooo##...",
        ".....######.....",
    ]),
    "mouth": ({"r": rgb(0xD94A5A), "d": rgb(0xA8303E), "w": rgb(0xFFFFFF), "k": rgb(0x5A1E26), "p": rgb(0xF29CB0)}, [
        "................",
        "................",
        "................",
        "....rrr..rrr....",
        "..rrrrrrrrrrrr..",
        ".rrrrrrrrrrrrrr.",
        "rrdddddddddddrrr",
        "rdwwkwwkwwkwwkdr",
        "rdkkkkkkkkkkkkdr",
        "rdkkkkppppkkkkdr",
        "rdwwkwwkwwkwwkdr",
        "rrdddddddddddrrr",
        ".rrrrrrrrrrrrrr.",
        "..rrrrrrrrrrrr..",
        "....rrrrrrrr....",
        "................",
    ]),
    "town": ({"s": rgb(0x9ED4F2), "r": rgb(0xC0392B), "b": rgb(0x3A7BD5), "g": rgb(0x2FA844), "w": rgb(0xF2E8C9),
              "k": rgb(0x2B2222), "y": rgb(0xF6D146), "c": rgb(0x8A6A4A), "l": rgb(0x6CB43C)}, [
        "ssssssssssssssss",
        "ssssssssyyssssss",
        "ssssssssyysssbss",
        "ssssrsssssssbbbs",
        "sssrrrsssssbbbbb",
        "ssrrrrrsssssbwbs",
        "ssswwwsssgggbwbs",
        "ssswkwssgggggwbs",
        "ssswkwsgggggggbs",
        "sbbwwwssswwwssbs",
        "sbwwwwwssswkwsbs",
        "sbwkwkwssswkwsbs",
        "sbwwwwwssswwwsbs",
        "lllllllllllllllll"[:16],
        "cccccccccccccccc",
        "llllllllllllllll",
    ]),
    # Round H's big words.
    "sunset": ({"p": rgb(0xF29CB0), "o": rgb(0xF08A2C), "y": rgb(0xF6D146), "r": rgb(0xE8483C), "b": rgb(0x3A7BD5),
                "d": rgb(0x2A5AA8), "w": rgb(0xFFE9A8)}, [
        "pppppppppppppppp",
        "pppppppppppppppp",
        "oooooooooooooooo",
        "oooooyyyyyyooooo",
        "oooyyyyyyyyyyooo",
        "ooyyyyyyyyyyyyoo",
        "rryyyyyyyyyyyyrr",
        "ryyyyyyyyyyyyyyr",
        "bbbbbwwwwwwbbbbb",
        "bbbbbbbbbbbbbbbb",
        "bbbwwwwwwwwwwbbb",
        "dddddddddddddddd",
        "ddddddwwwwdddddd",
        "dddddddddddddddd",
        "dddddddddddddddd",
        "dddddddddddddddd",
    ]),
    "cupcake": ({"r": rgb(0xD8302E), "p": rgb(0xF29CB0), "w": rgb(0xFFF4F6), "c": rgb(0xB8743E), "d": rgb(0x8A5428),
                 "g": rgb(0x5EA84A)}, [
        "................",
        "........g.......",
        ".......rr.......",
        "......rrrr......",
        "......#rr#......",
        "....##pppp##....",
        "...#pppwpppp#...",
        "..#ppppppwppp#..",
        "..#pwppppppppp#.",
        "..#############.",
        "...#cdcdcdcdc#..",
        "...#cdcdcdcdc#..",
        "....#cdcdcdc#...",
        "....#cdcdcdc#...",
        ".....#######....",
        "................",
    ]),
    "popcorn": ({"w": rgb(0xFFFBEA), "y": rgb(0xF6E08A), "r": rgb(0xE8483C), "s": rgb(0xFFFFFF)}, [
        "................",
        "....ww.yww.w....",
        "..wwyww.wwywww..",
        ".wwwwwywwwwyww..",
        ".##############.",
        ".#rrsrrsrrsrrs#.",
        "..#rsrrsrrsrr#..",
        "..#rsrrsrrsrr#..",
        "..#rsrrsrrsrr#..",
        "..#rsrrsrrsrr#..",
        "..#rsrrsrrsrr#..",
        "...#srrsrrsr#...",
        "...#srrsrrsr#...",
        "...#srrsrrsr#...",
        "...##########...",
        "................",
    ]),
    "rainbow": ({"s": rgb(0x9ED4F2), "r": rgb(0xE8483C), "o": rgb(0xF08A2C), "y": rgb(0xF6D146), "g": rgb(0x5DB04A),
                 "b": rgb(0x3A7BD5), "v": rgb(0x9B6FD4), "w": rgb(0xFFFFFF)}, [
        "ssssssssssssssss",
        "ssssssssssssssss",
        "sssssrrrrrrsssss",
        "sssrrooooooorrss",
        "ssroooyyyyyoooss"[:16],
        "sroyyyggggyyyors",
        "royygggbbgggyyor",
        "roygbbbvvbbbgyor",
        "roygbvvssvvbgyor",
        "roygbvssssvbgyor",
        "roygbvssssvbgyor",
        "wwwgbvssssvbgwww",
        "wwwwwvsssssvwwww",
        "wwwwwwssssswwwww",
        "sssssssssssssss s".replace(" ", "")[:16],
        "ssssssssssssssss",
    ]),
    "snowman": ({"s": rgb(0x9ED4F2), "w": rgb(0xFFFFFF), "g": rgb(0xD8E4EE), "k": rgb(0x2B2222), "o": rgb(0xF08A2C),
                 "r": rgb(0xE8483C), "b": rgb(0x7A5430)}, [
        "ssssskkkkkksssss",
        "sssssskkkksssss s".replace(" ", "")[:16],
        "ssssskkkkkksssss",
        "sssswwwwwwwwssss",
        "sssswkwwwwkwssss",
        "sssswwwooowwssss",
        "ssssswwwwwwsssss",
        "sssrrrrrrrrrrsss",
        "bssswwwwwwwwsssb",
        "sbswwwkwwwwwwsbs",
        "ssbwwwwwwwwwwbss",
        "sswwwwkwwwwwwwss",
        "sswwwwwwwwwwwwss",
        "sswwwwkwwwwwwwss",
        "ggggwwwwwwwwgggg",
        "gggggggggggggggg",
    ]),
    "pancake": ({"t": rgb(0xE8B86A), "d": rgb(0xB8803A), "y": rgb(0xF6E07A), "s": rgb(0x9A4A1E), "p": rgb(0xF4F0E6)}, [
        "................",
        "................",
        "......yyyy......",
        "....ssyyyyss....",
        "..##ssssssss##..",
        ".#tttsttttsttt#.",
        ".#dddsddddsddd#.",
        ".#ttttttttsttt#.",
        ".#dddddddddddd#.",
        ".#tttttttttttt#.",
        ".#dddddddddddd#.",
        ".#tttttttttttt#.",
        ".##############.",
        "pppppppppppppppp",
        ".pppppppppppppp.",
        "................",
    ]),
    "sandbox": ({"s": rgb(0x9ED4F2), "y": rgb(0xEED69A), "o": rgb(0xD8BA76), "w": rgb(0xA8743E), "d": rgb(0x7A5430),
                 "r": rgb(0xE8483C), "b": rgb(0x3A7BD5), "g": rgb(0x6CB43C)}, [
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssssssbsssss",
        "sssssssssbbbssss",
        "ssssrrsssssbssss",
        "sssrrrrssssbssss",
        "wwwwwwwwwwwbwwww",
        "wyyyyoyyyybbyyyw",
        "wyyoyyyyyyyyyoyw",
        "wyyyyyyoyyyyyyyw",
        "wyoyyyyyyyyoyyyw",
        "wwwwwwwwwwwwwwww",
        "dddddddddddddddd",
        "gggggggggggggggg",
        "gggggggggggggggg",
    ]),
}


# --- Round I's cards: the sky, feelings and doing words -----------------------------------------

class Grid:
    """A 16 by 16 card drawn with a few shapes, as rows of palette letters for CARDS."""

    def __init__(self, fill):
        self.rows = [[fill] * 16 for _ in range(16)]

    def dot(self, x, y, ch):
        if 0 <= x < 16 and 0 <= y < 16:
            self.rows[y][x] = ch

    def disc(self, cx, cy, r, ch, below=None):
        for y in range(16):
            for x in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r and (below is None or y < below):
                    self.rows[y][x] = ch

    def band(self, y0, y1, ch):
        for y in range(y0, y1):
            self.rows[y] = [ch] * 16

    def done(self):
        return ["".join(row) for row in self.rows]


def night_card():
    g = Grid("n")
    for x, y in ((2, 1), (6, 3), (1, 5), (4, 8), (13, 9), (8, 10), (2, 11), (10, 5)):
        g.dot(x, y, "w")
    g.disc(11.5, 4.5, 3.3, "m")
    g.disc(13.2, 3.4, 2.7, "n")
    g.disc(8, 21, 9, "g")
    return {"n": rgb(0x1E2A5A), "w": rgb(0xFFFFFF), "m": rgb(0xF6E27A), "g": rgb(0x24402E)}, g.done()


def day_card():
    g = Grid("s")
    g.disc(4.5, 4.5, 2.7, "y")
    for x, y in ((4, 0), (4, 8), (0, 4), (8, 4), (1, 1), (7, 1), (1, 7), (7, 7)):
        g.dot(x, y, "o")
    for cx, cy, r in ((11, 5.5, 1.9), (13, 4.6, 2.0), (14.8, 5.8, 1.4)):
        g.disc(cx, cy, r, "c")
    g.disc(8, 22, 10.5, "g")
    return {"s": rgb(0x8CCBF2), "y": rgb(0xF6D146), "o": rgb(0xF0A830), "c": rgb(0xFFFFFF), "g": rgb(0x5DB04A)}, g.done()


def dark_card():
    g = Grid("k")
    for x in (4, 10):
        g.dot(x, 7, "w")
        g.dot(x + 1, 7, "w")
        g.dot(x, 8, "w")
        g.dot(x + 1, 8, "p")
    return {"k": rgb(0x14121A), "w": rgb(0xF4F0E6), "p": rgb(0x3A3550)}, g.done()


def dusk_card():
    g = Grid("a")
    g.band(4, 8, "b")
    g.band(8, 12, "c")
    g.disc(8, 12.4, 2.6, "y", below=12)
    g.band(12, 16, "g")
    for x, y in ((3, 1), (12, 2)):
        g.dot(x, y, "w")
    return {"a": rgb(0x3B2E6E), "b": rgb(0x8E5A9E), "c": rgb(0xF08A4C), "y": rgb(0xF6C24A), "g": rgb(0x2E4A3A), "w": rgb(0xFFFFFF)}, g.done()


def dawn_card():
    g = Grid("a")
    g.band(4, 8, "b")
    g.band(8, 12, "c")
    g.disc(8, 12.4, 3.4, "y", below=12)
    g.band(12, 16, "g")
    return {"a": rgb(0x9CC8F0), "b": rgb(0xF2A6B8), "c": rgb(0xF8E08A), "y": rgb(0xFFF2A0), "g": rgb(0x5DB04A)}, g.done()


def hide_card():
    """A bush, and two eyes peeking out of it."""
    g = Grid(".")
    for cx, cy, r in ((5, 10, 4.2), (11, 10, 4.2), (8, 7.5, 4.6), (8, 12, 4.0)):
        g.disc(cx, cy, r, "g")
    for cx, cy in ((4.5, 9), (11.5, 11.5), (8.5, 5.5)):
        g.disc(cx, cy, 1.2, "d")
    for x in (6, 9):
        g.dot(x, 9, "w")
        g.dot(x + 1, 9, "k")
    return {"g": rgb(0x5DB04A), "d": rgb(0x3E8A3A), "w": rgb(0xFFFFFF), "k": rgb(0x2B2222)}, g.done()


def emote_card(name, marks=()):
    """The face out of a feeling's bubble (generate_emotes.py), cropped to the card."""
    import generate_emotes
    pixels = getattr(generate_emotes, name)().pixels()
    letters, palette, rows = "abcdefghijklmnopqrstuvwxyz", {}, []
    for y in range(6, 22):
        row = ""
        for x in range(8, 24):
            px = tuple(pixels[y][x])
            # Only the face: the bubble round it is left out.
            if px[3] == 0 or (x + 0.5 - 16) ** 2 + (y + 0.5 - 14) ** 2 > 9.6 ** 2:
                row += "."
                continue
            if px not in palette:
                palette[px] = letters[len(palette)]
            row += palette[px]
        rows.append(row)
    rows = [list(row) for row in rows]
    for x, y, colour in marks:
        if colour not in palette:
            palette[colour] = letters[len(palette)]
        rows[y][x] = palette[colour]
    return {ch: px for px, ch in palette.items()}, ["".join(row) for row in rows]


def wave_card():
    """The waving hand, out of its bubble (generate_emotes.py), at full size: a card of 18."""
    import generate_emotes
    pixels = generate_emotes.hand().pixels()
    keep = {generate_emotes.rgb(h) for h in (0xF6D2B4, 0x8A5A44, 0xE8B896)}
    letters, palette, rows = "abcdefghijklmnopqrstuvwxyz", {}, []
    for y in range(3, 21):
        row = ""
        for x in range(6, 24):
            px = tuple(pixels[y][x])
            if px not in keep:
                row += "."
                continue
            if px not in palette:
                palette[px] = letters[len(palette)]
            row += palette[px]
        rows.append(row)
    return {ch: px for px, ch in palette.items()}, rows


def nod_card():
    """A happy face with its eyes shut, and arrows up and down either side of it: a nod."""
    g = Grid(".")
    g.disc(8, 8.5, 4.9, "#")
    g.disc(8, 8.5, 4.1, "y")
    for x, y in ((6, 7), (6, 8), (10, 7), (10, 8), (6, 10), (7, 11), (8, 11), (9, 11), (10, 10)):
        g.dot(x, y, "k")
    for x in (1, 14):
        for y in range(2, 15):
            g.dot(x, y, "b")
        for dx in (-1, 1):
            g.dot(x + dx, 3, "b")
            g.dot(x + dx, 13, "b")
    return {"y": rgb(0xF6D146), "k": rgb(0x2B2222), "b": rgb(0x3A7BD5)}, g.done()


CARDS.update({"night": night_card(), "day": day_card(), "dark": dark_card(), "dusk": dusk_card(), "dawn": dawn_card(),
              "hide": hide_card(), "mad": emote_card("mad"), "cold": emote_card("cold"), "wave": wave_card(), "nod": nod_card()})


def card(name):
    palette, rows = CARDS[name]
    assert all(len(r) == len(rows) for r in rows), name
    return [[CLEAR if ch == "." else OUT if ch == "#" else palette[ch] for ch in row] for row in rows]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def cube(lo, hi, texture, uv_full=False):
    faces = {}
    for side in ("north", "south", "east", "west", "up", "down"):
        faces[side] = {"texture": texture}
    return {"from": list(lo), "to": list(hi), "faces": faces}


def turned(element, axis, angle, origin):
    element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    return element


def ring(cx, cy, r, z0, z1, t, texture):
    """An open octagon standing up, facing north: eight bars round (cx, cy)."""
    k = r * 0.41
    bars = [cube((cx - k, cy + r - t, z0), (cx + k, cy + r, z1), texture),
            cube((cx - k, cy - r, z0), (cx + k, cy - r + t, z1), texture),
            cube((cx - r, cy - k, z0), (cx - r + t, cy + k, z1), texture),
            cube((cx + r - t, cy - k, z0), (cx + r, cy + k, z1), texture)]
    d = (r + k) / 2 - t * 0.354
    for sx in (-1, 1):
        for sy in (-1, 1):
            bar = cube((cx + sx * d - k - t / 4, cy + sy * d - t / 2, z0),
                       (cx + sx * d + k + t / 4, cy + sy * d + t / 2, z1), texture)
            bars.append(turned(bar, "z", -45 if sx == sy else 45, (cx + sx * d, cy + sy * d, (z0 + z1) / 2)))
    return bars


# Small things show bigger in the inventory.
GUI_SCALE = {"bin": 0.7, "fork": 0.8, "mug": 0.85, "jar": 0.8, "corn": 0.8}


def model(elements, textures, display=None):
    data = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if display:
        data["display"] = display
    return data


# Worn on the head as a carved pumpkin is: model space with the head about 1.6 to 14.4 each way.
HEAD = {"gui": {"rotation": [25, 135, 0], "translation": [0, -7, 0], "scale": [0.9, 0.9, 0.9]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.6, 0.6, 0.6]}}

# Worn things: 3D, sized to a head (8 units) and drawn with vanilla wool.
WORN = {
    # A baseball cap: a red crown, a white button, a brim out over the eyes (north).
    "cap": ({"crown": "minecraft:block/red_wool", "button": "minecraft:block/white_wool"}, [
        cube((1.2, 13.4, 1.2), (14.8, 16.6, 14.8), "#crown"),
        cube((2.4, 16.6, 2.4), (13.6, 17.6, 13.6), "#crown"),
        cube((7.4, 17.6, 7.4), (8.6, 18.2, 8.6), "#button"),
        cube((2.4, 13.4, -4.2), (13.6, 14.0, 1.2), "#crown"),
    ]),
    # A wig: curly hair over the top, the back and the sides, open at the face.
    "wig": ({"hair": "minecraft:block/orange_wool"}, [
        cube((0.8, 14.2, 0.8), (15.2, 17.4, 15.2), "#hair"),
        cube((0.8, 2.5, 13.6), (15.2, 14.2, 15.2), "#hair"),
        cube((0.8, 4.5, 0.8), (2.2, 14.2, 13.6), "#hair"),
        cube((13.8, 4.5, 0.8), (15.2, 14.2, 13.6), "#hair"),
        cube((2.2, 12.2, 0.8), (13.8, 14.2, 2.0), "#hair"),
    ]),
}

# Blocks: placeable, facing whoever put them down (north is the front), over vanilla textures.
BLOCKS = {
    "bin": ({"side": "minecraft:block/light_gray_concrete", "lid": "minecraft:block/iron_block", "rim": "minecraft:block/gray_concrete"}, [
        cube((3.5, 0, 3.5), (12.5, 12, 12.5), "#side"),
        cube((3, 12, 3), (13, 13.2, 13), "#lid"),
        cube((6.5, 13.2, 7.5), (9.5, 14, 8.5), "#rim"),
    ], (3, 0, 3, 13, 14, 13), "minecraft:gray_concrete"),
    "tub": ({"tub": "minecraft:block/white_concrete", "water": "minecraft:block/light_blue_concrete", "foot": "minecraft:block/gold_block", "tap": "minecraft:block/iron_block"}, [
        cube((0.5, 1.5, 2), (15.5, 3, 14), "#tub"),
        cube((0.5, 3, 2), (15.5, 8, 3.2), "#tub"),
        cube((0.5, 3, 12.8), (15.5, 8, 14), "#tub"),
        cube((0.5, 3, 3.2), (1.7, 8, 12.8), "#tub"),
        cube((14.3, 3, 3.2), (15.5, 8, 12.8), "#tub"),
        cube((1.7, 3, 3.2), (14.3, 6.5, 12.8), "#water"),
        cube((1.5, 0, 3), (3, 1.5, 4.5), "#foot"),
        cube((13, 0, 3), (14.5, 1.5, 4.5), "#foot"),
        cube((1.5, 0, 11.5), (3, 1.5, 13), "#foot"),
        cube((13, 0, 11.5), (14.5, 1.5, 13), "#foot"),
        cube((14, 8, 7.4), (15, 10, 8.6), "#tap"),
        cube((13, 9, 7.4), (14, 10, 8.6), "#tap"),
    ], (0.5, 0, 2, 15.5, 10, 14), "minecraft:white_concrete"),
    # A cardboard box, open at the top, its four flaps folded out: something to put things in.
    "box": ({"side": "minecraft:block/sandstone_top", "inside": "minecraft:block/sandstone_bottom", "flap": "minecraft:block/sandstone_bottom"}, [
        cube((2, 0, 2), (14, 1, 14), "#inside"),
        cube((2, 0, 2), (14, 9, 2.8), "#side"),
        cube((2, 0, 13.2), (14, 9, 14), "#side"),
        cube((2, 0, 2.8), (2.8, 9, 13.2), "#side"),
        cube((13.2, 0, 2.8), (14, 9, 13.2), "#side"),
        turned(cube((2.2, 9, 2), (13.8, 13, 2.5), "#flap"), "x", -45, (8, 9, 2)),
        turned(cube((2.2, 9, 13.5), (13.8, 13, 14), "#flap"), "x", 45, (8, 9, 14)),
        turned(cube((2, 9, 2.6), (2.5, 12.4, 13.4), "#flap"), "z", 22.5, (2, 9, 8)),
        turned(cube((13.5, 9, 2.6), (14, 12.4, 13.4), "#flap"), "z", -22.5, (14, 9, 8)),
    ], (2, 0, 2, 14, 9, 14), "minecraft:sandstone"),
    # A glass jar, open, its screw thread at the neck: for putting things in.
    "jar": ({"glass": "phonics-justfatlard:block/prop_jar", "rim": "minecraft:block/iron_block", "base": "phonics-justfatlard:block/prop_jar_base"}, [
        cube((5, 0, 5), (11, 0.6, 11), "#base"),
        cube((5, 0.6, 5), (11, 8, 5.6), "#glass"),
        cube((5, 0.6, 10.4), (11, 8, 11), "#glass"),
        cube((5, 0.6, 5.6), (5.6, 8, 10.4), "#glass"),
        cube((10.4, 0.6, 5.6), (11, 8, 10.4), "#glass"),
        cube((5.6, 8, 5.6), (10.4, 9, 6.2), "#glass"),
        cube((5.6, 8, 9.8), (10.4, 9, 10.4), "#glass"),
        cube((5.6, 8, 6.2), (6.2, 9, 9.8), "#glass"),
        cube((9.8, 8, 6.2), (10.4, 9, 9.8), "#glass"),
        cube((5.8, 9, 5.8), (10.2, 10, 10.2), "#rim"),
    ], (5, 0, 5, 11, 10, 11), "minecraft:glass"),
    # A desk fan: a round guard, blades inside, a motor behind, on a stand.
    "fan": ({"body": "minecraft:block/white_concrete", "guard": "minecraft:block/light_gray_concrete", "blade": "minecraft:block/light_blue_concrete", "hub": "minecraft:block/blue_concrete"}, [
        cube((4.5, 0, 5), (11.5, 1, 11), "#body"),
        cube((7.4, 1, 8), (8.6, 6, 9.2), "#body"),
        cube((6.8, 8.4, 8.6), (9.2, 11.6, 11), "#body"),
        cube((7.2, 9.2, 7.6), (8.8, 10.8, 8.6), "#hub"),
        turned(cube((7.4, 10.8, 7.9), (8.6, 14, 8.3), "#blade"), "z", 22.5, (8, 10, 8)),
        turned(cube((7.4, 6, 7.9), (8.6, 9.2, 8.3), "#blade"), "z", 22.5, (8, 10, 8)),
        turned(cube((4, 9.4, 7.9), (7.2, 10.6, 8.3), "#blade"), "z", 22.5, (8, 10, 8)),
        turned(cube((8.8, 9.4, 7.9), (12, 10.6, 8.3), "#blade"), "z", 22.5, (8, 10, 8)),
    ] + ring(8, 10, 4.6, 7.4, 8.4, 0.6, "#guard") + [cube((7.6, 6, 7.6), (8.4, 10, 8.6), "#guard")],
        (4.5, 0, 5, 11.5, 15, 11), "minecraft:white_concrete"),
    # A mop standing on its head: a wooden handle, a band, a heap of white strings.
    "mop": ({"handle": "minecraft:block/stripped_birch_log", "band": "minecraft:block/blue_concrete", "strings": "minecraft:block/white_wool"}, [
        cube((7.5, 4, 7.5), (8.5, 16, 8.5), "#handle"),
        cube((7, 3.4, 7), (9, 4.8, 9), "#band"),
        cube((5.5, 1.4, 5.5), (10.5, 3.6, 10.5), "#strings"),
        turned(cube((4, 0.4, 6), (5.6, 3.2, 10), "#strings"), "z", 22.5, (5.6, 3.2, 8)),
        turned(cube((10.4, 0.4, 6), (12, 3.2, 10), "#strings"), "z", -22.5, (10.4, 3.2, 8)),
        turned(cube((6, 0.4, 4), (10, 3.2, 5.6), "#strings"), "x", -22.5, (8, 3.2, 5.6)),
        turned(cube((6, 0.4, 10.4), (10, 3.2, 12), "#strings"), "x", 22.5, (8, 3.2, 10.4)),
    ], (5, 0, 5, 11, 16, 11), "minecraft:white_wool"),
    # An ear of corn on its side, the husk peeled back at the stalk end.
    "corn": ({"cob": "phonics-justfatlard:block/prop_corn", "husk": "minecraft:block/lime_terracotta", "stalk": "minecraft:block/green_terracotta"}, [
        cube((4, 0.6, 6), (12.6, 4.4, 10), "#cob"),
        cube((12.6, 1.2, 6.6), (13.6, 3.8, 9.4), "#cob"),
        cube((1, 1.6, 7.4), (3, 3.4, 8.6), "#stalk"),
        cube((3, 0, 5.6), (6.6, 0.6, 10.4), "#husk"),
        turned(cube((2.4, 0.4, 5.2), (6.4, 4.2, 5.8), "#husk"), "y", -22.5, (3.4, 2, 5.6)),
        turned(cube((2.4, 0.4, 10.2), (6.4, 4.2, 10.8), "#husk"), "y", 22.5, (3.4, 2, 10.4)),
        turned(cube((2.4, 4.4, 6.2), (6, 5, 9.8), "#husk"), "z", 22.5, (3, 4.4, 8)),
    ], (1, 0, 5, 13.6, 5, 11), "minecraft:hay_block"),
    # A mug: a drink inside, a handle at the side.
    "mug": ({"mug": "minecraft:block/white_concrete", "band": "minecraft:block/red_concrete", "drink": "minecraft:block/brown_concrete"}, [
        cube((5, 0, 5), (11, 6, 11), "#mug"),
        cube((5, 6, 5), (11, 7, 5.6), "#mug"),
        cube((5, 6, 10.4), (11, 7, 11), "#mug"),
        cube((5, 6, 5.6), (5.6, 7, 10.4), "#mug"),
        cube((10.4, 6, 5.6), (11, 7, 10.4), "#mug"),
        cube((5.6, 5.6, 5.6), (10.4, 6.2, 10.4), "#drink"),
        cube((4.9, 4.2, 4.9), (11.1, 5, 11.1), "#band"),
        cube((11, 1.4, 7.3), (12.8, 2.2, 8.7), "#mug"),
        cube((12, 2.2, 7.3), (12.8, 4.8, 8.7), "#mug"),
        cube((11, 4.8, 7.3), (12.8, 5.6, 8.7), "#mug"),
    ], (5, 0, 5, 13, 7, 11), "minecraft:white_concrete"),
    # A school desk: a wooden top on metal legs, a shelf under it for books.
    "desk": ({"top": "minecraft:block/oak_planks", "leg": "minecraft:block/gray_concrete", "shelf": "minecraft:block/light_gray_concrete"}, [
        cube((1, 10, 2.5), (15, 11, 13.5), "#top"),
        cube((1.5, 0, 3), (2.5, 10, 4), "#leg"),
        cube((13.5, 0, 3), (14.5, 10, 4), "#leg"),
        cube((1.5, 0, 12), (2.5, 10, 13), "#leg"),
        cube((13.5, 0, 12), (14.5, 10, 13), "#leg"),
        cube((2.5, 8, 4), (13.5, 8.6, 12.5), "#shelf"),
        cube((2.5, 8.6, 12), (13.5, 10, 12.5), "#shelf"),
    ], (1, 0, 2.5, 15, 11, 13.5), "minecraft:oak_planks"),
    # A big fork lying flat, corner to corner.
    "fork": ({"metal": "minecraft:block/iron_block"}, [
        turned(cube((7.1, 0, 0.6), (8.9, 1.2, 8.6), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((6.5, 0, 8.6), (9.5, 1.2, 10.2), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((5.4, 0, 10.2), (10.6, 1.2, 11.4), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((5.4, 0, 11.4), (6.4, 1.2, 15.4), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((6.8, 0, 11.4), (7.8, 1.2, 15.4), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((8.2, 0, 11.4), (9.2, 1.2, 15.4), "#metal"), "y", 45, (8, 0, 8)),
        turned(cube((9.6, 0, 11.4), (10.6, 1.2, 15.4), "#metal"), "y", 45, (8, 0, 8)),
    ], (2, 0, 2, 14, 1.2, 14), "minecraft:iron_block"),
}

NAMES = {"bin": "Bin", "tub": "Bathtub", "box": "Box", "jar": "Jar", "fan": "Fan", "mop": "Mop", "corn": "Corn",
         "mug": "Mug", "desk": "Desk", "fork": "Fork"}


def kernels():
    """An ear of corn's kernels: 64 square, four pixels a model unit, so each little kernel is a
    rounded two-by-two pixel bead in neat rows, a darker seam between them."""
    seam, kernel, shine, low = rgb(0xC98F1C), rgb(0xF5C842), rgb(0xFFE88A), rgb(0xE0AA2A)
    px = [[seam] * 64 for _ in range(64)]
    for row in range(0, 64, 3):
        offset = (row // 3) % 2
        for col in range(offset, 64, 3):
            for dy in range(2):
                for dx in range(2):
                    if row + dy < 64 and col + dx < 64:
                        px[row + dy][col + dx] = shine if (dx, dy) == (0, 0) else low if (dx, dy) == (1, 1) else kernel
    return px


def glass(base=False):
    """The jar's glass, opaque but pale and shiny: a blue-white pane with a bright streak."""
    pale, edge, shine = rgb(0xA9DDEB), rgb(0x6FB3C8), rgb(0xFFFFFF)
    px = [[pale] * 16 for _ in range(16)]
    for i in range(16):
        px[i][0] = px[i][15] = edge
        if base:
            px[0][i] = px[15][i] = edge
        else:
            px[i][3] = px[i][4] = shine
            px[i][11] = rgb(0xD4F0F7)
    return px

BLOCK_DISPLAY = {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
                 "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                 "fixed": {"rotation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
                 "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                 "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}


def main():
    # The cap and the wig as worn: the toy items (generate_toys.py) use these on the head.
    for name, (textures, elements) in WORN.items():
        textures = dict(textures)
        textures["particle"] = next(iter(textures.values()))
        write(ASSETS / f"models/item/{name}.json", model(elements, textures, HEAD))
    for name in CARDS:
        write_png(ASSETS / f"textures/card/{name}.png", card(name))
    write_png(ASSETS / "textures/block/prop_jar.png", glass())
    write_png(ASSETS / "textures/block/prop_corn.png", kernels())
    write_png(ASSETS / "textures/block/prop_jar_base.png", glass(base=True))
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    for name, (textures, elements, shape, base) in BLOCKS.items():
        textures = dict(textures)
        textures["particle"] = next(iter(textures.values()))
        display = json.loads(json.dumps(BLOCK_DISPLAY))
        if name in GUI_SCALE:
            display["gui"]["scale"] = [GUI_SCALE[name]] * 3
        write(ASSETS / f"models/block/{name}.json", model(elements, textures, display))
        write(ASSETS / f"blockstates/{name}.json", {"variants": {f"facing={f}": {"model": f"{NS}:block/{name}", "y": y} for f, y in turn.items()}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": f"{NS}:blocks/{name}",
        })
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    for name in BLOCKS:
        lang[f"block.{NS}.{name}"] = NAMES[name]
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {len(WORN)} worn models, {len(CARDS)} picture cards and {len(BLOCKS)} prop blocks")

if __name__ == "__main__":
    main()
