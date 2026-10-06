#!/usr/bin/env python3
"""
Write the letter block's blockstate, a model for every letter, two-letter tile and the gap, and the item definition that makes a
tile in the hand look like the letter it carries.

Near-identical files, one per tile, are exactly the kind of thing nobody should edit by hand, so
nobody does: change this and run it.

Usage: python3 generate_models.py
"""

import json
import pathlib
import string

HERE = pathlib.Path(__file__).parent
NS = "learning-blocks-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS

# Two letters, one sound: consonant digraphs and doubles, qu, and the vowel teams.
DIGRAPHS = ["sh", "ch", "th", "ck", "qu", "ll", "ss", "ff", "zz", "ee", "ea", "oa", "ai", "oo", "ie", "gg", "ar", "or", "ur", "ir", "er", "ow", "ou", "ay", "igh", "oi", "oy", "ng", "nk", "aw", "wh", "le", "dge"]
NAMES = list(string.ascii_lowercase) + ["blank"] + DIGRAPHS + ["cap_" + c for c in string.ascii_lowercase] + ["blank_blue", "blank_orange", "blank_green"]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for name in NAMES:
        for lit in (False, True):
            suffix = "_lit" if lit else ""
            # The letter on all four sides, upright; plain tile top and bottom.
            write(ASSETS / f"models/block/letter_{name}{suffix}.json", {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {
                    "side": f"{NS}:block/letter_{name}{suffix}",
                    "top": f"{NS}:block/letter_top{suffix}",
                    "bottom": f"{NS}:block/letter_top{suffix}",
                },
            })

    # The loose tile and the board's own letters look the same; they differ only in whether they
    # can be broken.
    for block in ("letter", "board_letter"):
        write(ASSETS / f"blockstates/{block}.json", {
            "variants": {
                f"letter={name},lit={str(lit).lower()}": {"model": f"{NS}:block/letter_{name}{'_lit' if lit else ''}"}
                for name in NAMES for lit in (False, True)
            }
        })

    # A tile carries its letter in its block_state component, and draws as that letter's block.
    write(ASSETS / "items/letter.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:block_state",
            "block_state_property": "letter",
            "cases": [
                {"when": name, "model": {"type": "minecraft:model", "model": f"{NS}:block/letter_{name}"}}
                for name in NAMES
            ],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:block/letter_blank"},
        }
    })
    print(f"Wrote {len(NAMES) * 2} models, two blockstates and the item definition")


if __name__ == "__main__":
    main()
