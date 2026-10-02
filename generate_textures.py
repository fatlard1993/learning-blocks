#!/usr/bin/env python3
"""
Draw the letter tiles, and the mod's icon.

One wooden tile face, and twenty-six letters stamped on it, plus the gap a letter goes in. The
letters are lowercase, because that is what a child learning to read meets first, and drawn from
a small hand-made bitmap font rather than any installed one, so the output is the same on every
machine and the tiles never change just because somebody's fonts did.

Vowels are red and consonants blue, the colours most early-reading material uses, so a child can
see the difference as well as hear it.

Pure standard library, deterministic, same approach as poopsmith's generate_textures.py.

Usage: python3 generate_textures.py
"""

import pathlib
import math
import random
import struct
import zlib

HERE = pathlib.Path(__file__).parent
ASSETS = HERE / "src/main/resources/assets/phonics-justfatlard"
OUT = ASSETS / "textures/block"

SIZE = 64
SCALE = 5

CLEAR = (0, 0, 0, 0)
# A classic wooden toy block, lit from the top left: natural light wood, edges that roll away,
# a raised border round each face and a recessed panel with the letter painted on it.
WOOD = (0xF5, 0xD6, 0x9A, 0xFF)
WOOD_GRAIN = (0xE8, 0xC4, 0x82, 0xFF)
WOOD_LIGHT = (0xFC, 0xEA, 0xC0, 0xFF)
WOOD_SHADE = (0xC9, 0x9A, 0x58, 0xFF)
WOOD_DEEP = (0xA6, 0x76, 0x3A, 0xFF)
PANEL = (0xE6, 0xBD, 0x76, 0xFF)
PANEL_GRAIN = (0xD6, 0xAA, 0x62, 0xFF)
VOWEL = (0xC0, 0x39, 0x2B, 0xFF)
CONSONANT = (0x1F, 0x4E, 0x9C, 0xFF)
GAP = (0x5A, 0x50, 0x46, 0xFF)

# Lit: the tile whose sound is being said. The same block washed warm yellow, loud enough to read
# from across the room and still the same block, so it looks lit rather than swapped.
GLOW = (0xFF, 0xD8, 0x3A)


def glow(c):
    return tuple(round(c[i] * 0.4 + GLOW[i] * 0.6) for i in range(3)) + (0xFF,)


# Five columns, nine rows: two for ascenders, five for the body of a letter, two for descenders.
# Rows left out at the bottom are empty.
FONT = {
    "a": [".....", ".....", ".###.", "....#", ".####", "#...#", ".####"],
    "b": ["#....", "#....", "####.", "#...#", "#...#", "#...#", "####."],
    "c": [".....", ".....", ".###.", "#....", "#....", "#....", ".###."],
    "d": ["....#", "....#", ".####", "#...#", "#...#", "#...#", ".####"],
    "e": [".....", ".....", ".###.", "#...#", "#####", "#....", ".###."],
    "f": ["..##.", ".#...", "####.", ".#...", ".#...", ".#...", ".#..."],
    "g": [".....", ".....", ".####", "#...#", "#...#", "#...#", ".####", "....#", ".###."],
    "h": ["#....", "#....", "####.", "#...#", "#...#", "#...#", "#...#"],
    # i and l are a plain stroke with a small tail to the right at the foot, and no flag at the top:
    # with a flag and a base, l is the digit 1 to anybody who has just learned to count.
    "i": ["..#..", ".....", "..#..", "..#..", "..#..", "..#..", "..##."],
    "j": ["...#.", ".....", "..##.", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "k": ["#....", "#....", "#..#.", "#.#..", "##...", "#.#..", "#..#."],
    "l": ["..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..##."],
    "m": [".....", ".....", "##.#.", "#.#.#", "#.#.#", "#.#.#", "#.#.#"],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "#...#"],
    "o": [".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###."],
    "p": [".....", ".....", "####.", "#...#", "#...#", "#...#", "####.", "#....", "#...."],
    "q": [".....", ".....", ".####", "#...#", "#...#", "#...#", ".####", "....#", "....#"],
    "r": [".....", ".....", "#.##.", "##..#", "#....", "#....", "#...."],
    "s": [".....", ".....", ".####", "#....", ".###.", "....#", "####."],
    "t": [".....", ".#...", "####.", ".#...", ".#...", ".#..#", "..##."],
    "u": [".....", ".....", "#...#", "#...#", "#...#", "#...#", ".####"],
    "v": [".....", ".....", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "w": [".....", ".....", "#...#", "#...#", "#.#.#", "#.#.#", ".#.#."],
    "x": [".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    "y": [".....", ".....", "#...#", "#...#", "#...#", "#...#", ".####", "....#", ".###."],
    "z": [".....", ".....", "#####", "...#.", "..#..", ".#...", "#####"],
    "blank": [".....", ".....", ".....", ".....", ".....", ".....", ".....", "#####"],
}

GLYPH_W, GLYPH_H = 5, 9


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9))
                + chunk(b"IEND", b""))


def tile(size, seed, lit=False, height=None):
    """A blank wooden block face: a rolled edge, a raised border, and a recessed panel, all grained."""
    w, h = size, height or size
    rng = random.Random(seed)
    phase, wave = rng.uniform(0, 6.3), rng.uniform(1.5, 3.5)
    g = [[WOOD] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            top, left, bottom, right = y, x, h - 1 - y, w - 1 - x
            d = min(top, left, bottom, right)
            lit_side = min(top, left) <= min(bottom, right)
            grain = math.sin((y + wave * math.sin(x / 9 + phase)) * 1.3 + phase) > 0.72 or rng.random() < 0.05
            if d == 0:
                c = WOOD_SHADE if lit_side else WOOD_DEEP
                if min(top, bottom) == 0 and min(left, right) == 0:
                    c = WOOD_DEEP
            elif d == 1:
                c = WOOD_LIGHT if lit_side else WOOD_SHADE
            elif d < 5:
                c = WOOD_GRAIN if grain else WOOD
            elif d == 5:
                # The border's inner slope: in shadow under the top and left, catching light at the
                # bottom and right.
                c = WOOD_SHADE if lit_side else WOOD_LIGHT
            else:
                c = PANEL_GRAIN if grain else PANEL
            g[y][x] = c
    if lit:
        g = [[glow(px) for px in row] for row in g]
    return g


def stamp(g, glyph, colour, x0, y0, scale):
    """Paint a glyph, with a thin shadow below and to the right so the paint sits on the wood."""
    painted = set()
    for row, line in enumerate(glyph):
        for col, cell in enumerate(line):
            if cell == "#":
                painted |= {(x0 + col * scale + dx, y0 + row * scale + dy) for dy in range(scale) for dx in range(scale)}
    for x, y in painted:
        if (x + 1, y + 1) not in painted and 0 <= y + 1 < len(g) and 0 <= x + 1 < len(g[0]):
            under = g[y + 1][x + 1]
            g[y + 1][x + 1] = tuple(round(v * 0.8) for v in under[:3]) + (under[3],)
    for row, line in enumerate(glyph):
        for col, cell in enumerate(line):
            if cell != "#":
                continue
            for dy in range(scale):
                for dx in range(scale):
                    g[y0 + row * scale + dy][x0 + col * scale + dx] = colour



# Capitals, five columns and seven rows, sitting on the same line as the small letters.
UPPER = {
    "a": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "b": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "c": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "d": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "e": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "f": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "g": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "h": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "i": [".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "j": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "k": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "l": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "m": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "n": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "o": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "p": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    "r": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "s": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "t": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "u": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "v": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "w": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "x": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
}

# Two letters, one sound: both on one face, a little smaller. Vowel teams are red, the rest blue.
DIGRAPHS = ["sh", "ch", "th", "ck", "qu", "ll", "ss", "ff", "zz", "ee", "ea", "oa", "ai", "oo", "ie", "gg", "ar", "or", "ur", "ir", "er", "ow", "ou", "ay", "igh", "oi", "oy", "ng", "nk", "aw", "wh", "le", "dge"]
PAIR_SCALE = 4


# Tiles that say a vowel sound though a consonant is in them: red, with the vowels.
VOWEL_SOUNDS = {"ar", "or", "ur", "ir", "er", "ow", "ou", "ay", "igh", "oi", "oy", "aw"}


def colour_of(name):
    if name == "blank":
        return GAP
    return VOWEL if name in VOWEL_SOUNDS or all(c in "aeiou" for c in name) else CONSONANT


def columns(glyph):
    """The columns a glyph uses, left and right, so three letters can sit close."""
    used = [x for x in range(GLYPH_W) if any(len(row) > x and row[x] == "#" for row in glyph)]
    return (used[0], used[-1]) if used else (0, GLYPH_W - 1)


def pair_texture(name, lit=False):
    if len(name) == 3:
        return triple_texture(name, lit)
    g = tile(SIZE, name, lit)
    gap = 4
    width = 2 * GLYPH_W * PAIR_SCALE + gap
    x0 = (SIZE - width + 1) // 2
    y0 = (SIZE - GLYPH_H * PAIR_SCALE + 1) // 2
    for i, letter in enumerate(name):
        stamp(g, FONT[letter], colour_of(name), x0 + i * (GLYPH_W * PAIR_SCALE + gap), y0, PAIR_SCALE)
    return g


def triple_texture(name, lit=False):
    """Three letters, one sound (igh): each only as wide as it is, a pixel apart."""
    g = tile(SIZE, name, lit)
    spans = [columns(FONT[c]) for c in name]
    width = sum((b - a + 1) * PAIR_SCALE for a, b in spans) + (len(name) - 1)
    x = (SIZE - width + 1) // 2
    y0 = (SIZE - GLYPH_H * PAIR_SCALE + 1) // 2
    for c, (a, b) in zip(name, spans):
        stamp(g, FONT[c], colour_of(name), x - a * PAIR_SCALE, y0, PAIR_SCALE)
        x += (b - a + 1) * PAIR_SCALE + 1
    return g


def capital_texture(letter, lit=False):
    g = tile(SIZE, "cap_" + letter, lit)
    x0 = (SIZE - GLYPH_W * SCALE + 1) // 2
    y0 = (SIZE - GLYPH_H * SCALE + 1) // 2
    stamp(g, UPPER[letter], VOWEL if letter in "aeiou" else CONSONANT, x0, y0, SCALE)
    return g


# The gap's underscore in a reader's own colour, when two read together: whose gap it is.
BLANK_COLOURS = {"blank_blue": (0x2B, 0x6C, 0xE0, 0xFF), "blank_orange": (0xF0, 0x8A, 0x1E, 0xFF), "blank_green": (0x2F, 0xA8, 0x44, 0xFF)}


def blank_texture(name, lit=False):
    """A gap's underscore, thick, in a reader's colour."""
    g = tile(SIZE, name, lit)
    x0 = (SIZE - GLYPH_W * SCALE + 1) // 2
    y0 = (SIZE - GLYPH_H * SCALE + 1) // 2
    stamp(g, FONT["blank"][:6] + ["#####", "#####"], BLANK_COLOURS[name], x0, y0, SCALE)
    return g


def letter_texture(name, lit=False):
    g = tile(SIZE, name, lit)
    x0 = (SIZE - GLYPH_W * SCALE + 1) // 2
    y0 = (SIZE - GLYPH_H * SCALE + 1) // 2
    stamp(g, FONT[name], colour_of(name), x0, y0, SCALE)
    return g


def main():
    for name in FONT:
        write_png(OUT / f"letter_{name}.png", letter_texture(name))
        write_png(OUT / f"letter_{name}_lit.png", letter_texture(name, lit=True))
    for name in DIGRAPHS:
        write_png(OUT / f"letter_{name}.png", pair_texture(name))
        write_png(OUT / f"letter_{name}_lit.png", pair_texture(name, lit=True))
    for letter in UPPER:
        write_png(OUT / f"letter_cap_{letter}.png", capital_texture(letter))
        write_png(OUT / f"letter_cap_{letter}_lit.png", capital_texture(letter, lit=True))
    for name in BLANK_COLOURS:
        write_png(OUT / f"letter_{name}.png", blank_texture(name))
        write_png(OUT / f"letter_{name}_lit.png", blank_texture(name, lit=True))
    write_png(OUT / "letter_top.png", tile(SIZE, "top"))
    write_png(OUT / "letter_top_lit.png", tile(SIZE, "top", lit=True))
    print(f"Wrote {2 * (len(FONT) + len(DIGRAPHS) + 1)} textures to {OUT.relative_to(HERE)}; the icon is generate_icon.py's")


if __name__ == "__main__":
    main()
