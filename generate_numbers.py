#!/usr/bin/env python3
"""
Draw the number tiles: the same wooden toy blocks as the letter tiles, with a numeral painted on
the front and back (0 to 20) and the same amount in dots on the two sides: a die's pips up to six,
a ten-frame from seven to ten, and two ten-frames, the top one full, from eleven to twenty. Zero's
sides are an empty ten-frame. The top and bottom are the plain tile top.

Numerals are green, the colour of the Numbers subject; dots are red on the paler panel.

The symbol tiles + - = come after them (21 to 23): the sign alone, the same on every side.

Writes textures/block/number_<n>.png and number_<n>_dots.png, models/block/number_<n>.json, the
number blockstate (by its number) and the item definition (by the item's block state).

Usage: python3 generate_numbers.py
"""

import json
import pathlib

from generate_textures import tile, stamp, write_png, SIZE

HERE = pathlib.Path(__file__).parent
NS = "learning-blocks-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS
OUT = ASSETS / "textures/block"

NUMERAL = (0x2E, 0x7D, 0x32, 0xFF)
DOT = (0xC0, 0x39, 0x2B, 0xFF)
FRAME = (0xB8, 0x8C, 0x4E, 0xFF)
HIGHEST = 20
# The symbol tiles, after the numbers: plus, minus and equals (numbers' N4, adding and taking away).
SYMBOLS = {21: "+", 22: "-", 23: "=", 24: "yes", 25: "no"}
LAST = 25
# Logic's same-or-different answers: a green tick and a red cross.
TICK = (0x3C, 0xA0, 0x3C, 0xFF)
CROSS = (0xD0, 0x3A, 0x30, 0xFF)
SYMBOL = (0x9B, 0x3D, 0xB8, 0xFF)

DIGITS = {
    "0": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    "3": [".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."],
    "4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    "6": ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
    "7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
    "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    "9": [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
}


def symbol_texture(n):
    """A symbol tile: the sign big and bold, in its own colour, on the plain wood; the same on every side."""
    g = tile(SIZE, f"symbol{n}")
    mid = SIZE // 2
    arm, bar = 22, 5
    sign = SYMBOLS[n]
    def rect(x0, y0, x1, y1):
        for y in range(y0, y1):
            for x in range(x0, x1):
                g[y][x] = SYMBOL
    if sign in "+-":
        rect(mid - arm, mid - bar // 2 - 1, mid + arm, mid + bar // 2 + 2)
    if sign == "+":
        rect(mid - bar // 2 - 1, mid - arm, mid + bar // 2 + 2, mid + arm)
    if sign == "=":
        rect(mid - arm, mid - 11, mid + arm, mid - 11 + bar + 1)
        rect(mid - arm, mid + 6, mid + arm, mid + 6 + bar + 1)
    if sign in ("yes", "no"):
        colour = TICK if sign == "yes" else CROSS
        def dot(x, y):
            for dy in range(-3, 4):
                for dx in range(-3, 4):
                    if dx * dx + dy * dy <= 10 and 0 <= x + dx < SIZE and 0 <= y + dy < SIZE:
                        g[int(y + dy)][int(x + dx)] = colour
        steps = 60
        if sign == "yes":
            # A tick: down to the corner, then a long stroke up.
            for i in range(steps + 1):
                t = i / steps
                dot(14 + t * 12, 32 + t * 14)
                dot(26 + t * 24, 46 - t * 30)
        else:
            for i in range(steps + 1):
                t = i / steps
                dot(16 + t * 32, 16 + t * 32)
                dot(48 - t * 32, 16 + t * 32)
    return g


def numeral_texture(n):
    g = tile(SIZE, f"number{n}")
    text = str(n)
    scale = 5 if len(text) == 1 else 4
    gap = scale
    width = len(text) * 5 * scale + (len(text) - 1) * gap
    x0 = (SIZE - width + 1) // 2
    y0 = (SIZE - 7 * scale + 1) // 2
    for i, ch in enumerate(text):
        stamp(g, DIGITS[ch], NUMERAL, x0 + i * (5 * scale + gap), y0, scale)
    return g


def disc(g, cx, cy, r, colour):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if 0 <= x < SIZE and 0 <= y < SIZE and (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                g[y][x] = colour


def ring(g, cx, cy, r, colour):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if 0 <= x < SIZE and 0 <= y < SIZE and r - 1.0 <= d <= r:
                g[y][x] = colour


# A die's pips, on a 3x3 grid: which cells for each number.
PIPS = {1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8]}


def frame(g, y_mid, filled):
    """A ten-frame across the panel at this height: two rows of five, the first {filled} dotted."""
    for i in range(10):
        cx = 12 + (i % 5) * 10
        cy = y_mid - 5 + (i // 5) * 10
        if i < filled:
            disc(g, cx, cy, 3.6, DOT)
        else:
            ring(g, cx, cy, 3.6, FRAME)


def dots_texture(n):
    g = tile(SIZE, f"dots{n}")
    if 1 <= n <= 6:
        for cell in PIPS[n]:
            disc(g, 18 + (cell % 3) * 14, 18 + (cell // 3) * 14, 5.0, DOT)
    elif n <= 10:
        frame(g, 32, n)
    else:
        frame(g, 21, 10)
        frame(g, 43, n - 10)
    return g


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for n in range(HIGHEST + 1):
        write_png(OUT / f"number_{n}.png", numeral_texture(n))
        write_png(OUT / f"number_{n}_dots.png", dots_texture(n))
        write(ASSETS / f"models/block/number_{n}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "north": f"{NS}:block/number_{n}", "south": f"{NS}:block/number_{n}",
                "east": f"{NS}:block/number_{n}_dots", "west": f"{NS}:block/number_{n}_dots",
                "up": f"{NS}:block/letter_top", "down": f"{NS}:block/letter_top",
                "particle": f"{NS}:block/number_{n}",
            },
        })
    for n in SYMBOLS:
        write_png(OUT / f"number_{n}.png", symbol_texture(n))
        write(ASSETS / f"models/block/number_{n}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "north": f"{NS}:block/number_{n}", "south": f"{NS}:block/number_{n}",
                "east": f"{NS}:block/number_{n}", "west": f"{NS}:block/number_{n}",
                "up": f"{NS}:block/letter_top", "down": f"{NS}:block/letter_top",
                "particle": f"{NS}:block/number_{n}",
            },
        })
    write(ASSETS / "blockstates/number.json", {
        "variants": {f"number={n}": {"model": f"{NS}:block/number_{n}"} for n in range(LAST + 1)}
    })
    write(ASSETS / "items/number.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:block_state",
            "block_state_property": "number",
            "cases": [{"when": str(n), "model": {"type": "minecraft:model", "model": f"{NS}:block/number_{n}"}} for n in range(LAST + 1)],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:block/number_1"},
        }
    })
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text())
    lang[f"block.{NS}.number"] = "Number Tile"
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {HIGHEST + 1} number tiles and {len(SYMBOLS)} symbols")


if __name__ == "__main__":
    main()
