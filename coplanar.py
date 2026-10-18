"""
Keeps two boxes' faces from lying in the same place, where the renderer can't tell which is in
front and flickers between them.

Two cases come up, and each has the face it meant to show:

- A box wholly inside another (a whale's belly in its body) is a detail painted on the surface they
  share: its face is pushed out a tenth of a pixel, so it is the one in front.
- A box passing into another (a robot's leg, ending inside its foot) has its face covered by the
  other's: that face is dropped.
- Otherwise the two only partly overlap (a teddy's leg poking out under its body), and the later
  box wins, pushed out like a detail: the models list bodies first and what goes on them after.

Faces drawn alike can't be seen to flicker, so they are left alone, and so are rotated boxes.
"""

NUDGE = 0.1
SIDES = (("west", "east"), ("down", "up"), ("north", "south"))


def _inside(a, b):
    return all(b["from"][k] <= a["from"][k] and a["to"][k] <= b["to"][k] for k in range(3))


def _face_within(a, b, axis):
    return all(b["from"][k] <= a["from"][k] and a["to"][k] <= b["to"][k] for k in range(3) if k != axis)


def _push(element, end, axis, low):
    element[end][axis] = round(element[end][axis] + (-NUDGE if low else NUDGE), 3)


def settle(elements):
    """Settles every pair of coplanar faces, in place."""
    # Again until nothing moves: a face pushed out for one pair can land on another's.
    for _ in range(8):
        if not _settle_once(elements):
            return
    raise ValueError("coplanar faces that keep moving")


def _settle_once(elements):
    moved = False
    for i, a in enumerate(elements):
        for b in elements[i + 1:]:
            if "rotation" in a or "rotation" in b:
                continue
            for axis in range(3):
                others = [k for k in range(3) if k != axis]
                if not all(min(a["to"][k], b["to"][k]) > max(a["from"][k], b["from"][k]) for k in others):
                    continue
                for low, end in ((True, "from"), (False, "to")):
                    side = SIDES[axis][0 if low else 1]
                    if a[end][axis] != b[end][axis] or side not in a["faces"] or side not in b["faces"]:
                        continue
                    if a["faces"][side] == b["faces"][side]:
                        continue
                    moved = True
                    if _inside(a, b):
                        _push(a, end, axis, low)
                    elif _inside(b, a):
                        _push(b, end, axis, low)
                    elif _face_within(a, b, axis):
                        del a["faces"][side]
                    elif _face_within(b, a, axis):
                        del b["faces"][side]
                    else:
                        _push(b, end, axis, low)
    return moved
