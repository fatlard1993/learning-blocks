#!/usr/bin/env python3
"""
Write what every thing in the classroom can do with every other: its affordances.

One file a thing, data/learning-blocks-justfatlard/phonics_relations/<word>.json, read by Relations.java.
The same table serves Bob (put the cat in the box), Bob's goals (made from it), the preposition
read-it rounds, and toy play in the world (the doll into the crib, the owl into the car).

A thing says what it is ("creature", "plush", "toy", "prop", "block", "item"); how it is shown
(an entity, or a block and its properties) and held (an item); how tall it is, in blocks, taken
from its own model where it has one; its tags ("small" things go in and on things, "tiny" ones
fit a jar, a "wearer" can wear a cap); whether it is "wearable" or a "lid"; and what it "holds":
for each of in, on, under and ride, which tags it accepts and the pose a thing takes there (its
foot's height above the holder's, its scale) and the sound it makes.

Usage: python3 generate_relations.py
"""

import json
import pathlib

HERE = pathlib.Path(__file__).parent
NS = "learning-blocks-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / NS
OUT = HERE / "src/main/resources/data" / NS / "phonics_relations"


def model_height(model):
    """The top of a block model, in blocks."""
    data = json.loads((ASSETS / f"models/block/{model}.json").read_text())
    return round(max(element["to"][1] for element in data["elements"]) / 16, 3)


PLUSHIES = ["owl", "duck", "ant", "rat", "ox", "shark", "crab", "worm", "moth", "yak", "teddy", "snail", "seal", "mouse",
            "snake", "skunk", "hawk", "ladybug", "mole", "crow", "pug", "deer", "whale", "fly", "beetle", "mice",
            "bobcat", "catfish", "starfish", "chipmunk", "puffin", "robin", "lobster", "hedgehog"]
TINY = {"ant", "ladybug", "worm", "mice", "fly", "beetle", "snail", "moth", "crab"}
VEHICLES = {"car", "van", "bus", "truck", "train", "ship", "jet"}

things = {}


def thing(word, **fields):
    things[word] = fields


# --- Creatures Bob uses: they ride each other, and things go on their heads -------------------
for word, entity, height in (("cat", "cat", 0.7), ("pig", "pig", 0.9), ("hen", "chicken", 0.7), ("dog", "wolf", 0.85), ("fox", "fox", 0.7)):
    thing(word, **{"is": "creature", "entity": f"minecraft:{entity}", "height": height, "tags": ["creature", "wearer"]})

# --- Plushies: small, wear caps, and the tiny ones fit a jar ----------------------------------
for name in PLUSHIES:
    tags = ["small", "plush", "wearer"] + (["tiny"] if name in TINY else [])
    thing(name, **{"is": "plush", "block": f"{NS}:plush", "properties": {"kind": name}, "item": f"{NS}:plush_{name}",
                   "height": model_height(f"plush_{name}"), "tags": tags})
# The ladybug is read as bug.
thing("bug", **dict(things["ladybug"]))

# --- Toys --------------------------------------------------------------------------------------
TOYS = ["drum", "kite", "top", "ring", "cap", "wig", "net", "pin", "sock", "crib", "car", "van", "jet", "bus", "ship",
        "doll", "block", "train", "truck"]
for name in TOYS:
    tags = ["small", "toy"]
    fields = {"is": "toy", "block": f"{NS}:toy", "properties": {"kind": name}, "item": f"{NS}:toy_{name}",
              "height": model_height(f"toy_{name}"), "tags": tags}
    if name in ("cap", "wig"):
        fields["wearable"] = True
    if name == "doll":
        tags += ["doll", "wearer"]
    holds = {}
    if name in VEHICLES:
        # Riders sit in it (or in a truck's dump bed, on a ship's deck), and go where it goes.
        seat = {"car": 0.35, "van": 0.45, "bus": 0.55, "truck": 0.2, "train": 0.5, "ship": 0.4, "jet": 0.3}[name]
        back = {"truck": 0.25, "train": 0.15}.get(name, 0.0)
        holds["ride"] = {"accepts": ["plush", "doll"], "y": seat, "z": back, "scale": 0.55, "sound": "minecraft:block.note_block.didgeridoo"}
    if name == "crib":
        # Tucked in, and it rocks; room under it too.
        holds["in"] = {"accepts": ["plush", "doll"], "y": 0.12, "scale": 0.6, "sound": "minecraft:block.note_block.chime"}
        holds["on"] = {"accepts": ["small"], "y": 0.5, "scale": 0.6}
        holds["under"] = {"accepts": ["tiny"], "y": 0.0, "scale": 0.45}
    if holds:
        fields["holds"] = holds
    thing(name, **fields)
for name in ("drum", "block"):
    things[name]["holds"] = {"on": {"accepts": ["small"], "y": things[name]["height"], "scale": 0.6}}

# --- Props: plain things, not collected --------------------------------------------------------
def prop(name, holds=None, tags=None, height=None):
    fields = {"is": "prop", "block": f"{NS}:{name}", "item": f"{NS}:{name}", "height": height or model_height(name), "tags": tags or []}
    if holds:
        fields["holds"] = holds
    thing(name, **fields)


prop("bin", {"in": {"accepts": ["small"], "y": 0.45, "scale": 0.55, "sound": "minecraft:block.metal.place"}})
prop("tub", {"in": {"accepts": ["plush", "doll", "creature"], "y": 0.32, "scale": 0.6, "float": True, "sound": "minecraft:entity.player.splash"}})
prop("box", {"in": {"accepts": ["small", "creature"], "y": 0.2, "scale": 0.6, "sound": "minecraft:block.wood.place"}})
prop("jar", {"in": {"accepts": ["tiny"], "y": 0.08, "scale": 0.5, "sound": "minecraft:block.glass.place"}})
prop("mug", {"in": {"accepts": ["tiny"], "y": 0.22, "scale": 0.4, "sound": "minecraft:block.glass.place"}}, tags=["small"])
prop("desk", {"on": {"accepts": ["small", "creature"], "y": 0.69, "scale": 0.6}, "under": {"accepts": ["small", "creature"], "y": 0.0, "scale": 0.55}})
prop("fan")
prop("mop")
prop("corn", tags=["small"])
prop("fork", tags=["small"])

# --- Vanilla things Bob already knows ---------------------------------------------------------
thing("pot", **{"is": "block", "block": "minecraft:flower_pot", "height": 0.375, "tags": [],
                "holds": {"in": {"accepts": ["tiny", "small"], "y": 0.1, "scale": 0.45}}})
thing("bed", **{"is": "block", "block": "minecraft:red_bed", "properties": {"part": "head"}, "height": 0.5625, "tags": [],
                "holds": {"on": {"accepts": ["small", "creature"], "y": 0.5625, "scale": 0.6}}})
thing("rug", **{"is": "block", "block": "minecraft:red_carpet", "height": 0.0625, "tags": [],
                "holds": {"on": {"accepts": ["small", "creature"], "y": 0.0625, "scale": 0.6}}})
thing("hat", **{"is": "item", "item": "minecraft:leather_helmet", "height": 0.3, "tags": ["small"], "wearable": True})
thing("lid", **{"is": "block", "block": "minecraft:spruce_slab", "height": 0.12, "tags": ["small"], "lid": True})


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        if old.stem not in things:
            old.unlink()
    for word, fields in things.items():
        (OUT / f"{word}.json").write_text(json.dumps(fields, indent=2) + "\n")
    print(f"Wrote {len(things)} things")


if __name__ == "__main__":
    main()
