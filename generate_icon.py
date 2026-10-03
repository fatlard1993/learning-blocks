#!/usr/bin/env python3
"""
Draw the mod's icon: three wooden toy blocks stacked A over B and C.

The same blocks the classroom is built from, read straight out of the tile textures
generate_textures.py makes, so the icon always matches the tiles a child actually plays
with. Stacked the way every toddler's ABC blocks end up: two on the floor, one on top.

Pure standard library, deterministic. Run generate_textures.py first.

Usage: python3 generate_icon.py
"""

import pathlib
import struct
import zlib

HERE = pathlib.Path(__file__).parent
ASSETS = HERE / "src/main/resources/assets/learning-blocks-justfatlard"
TILES = ASSETS / "textures/block"
ICON = ASSETS / "icon.png"

SIZE = 128
TILE = 64


def read_png(path):
	"""An 8-bit RGBA, non-interlaced PNG, as rows of (r, g, b, a)."""
	data = path.read_bytes()
	pos, idat, width, height = 8, b"", 0, 0
	while pos < len(data):
		length, kind = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + length]
		if kind == b"IHDR":
			width, height, depth, colour = struct.unpack(">IIBB", body[:10])
			assert depth == 8 and colour == 6, f"{path.name}: expected 8-bit RGBA"
		elif kind == b"IDAT":
			idat += body
		pos += 12 + length
	raw, stride, rows, previous = zlib.decompress(idat), width * 4, [], bytearray(width * 4)
	for y in range(height):
		kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
		for x in range(stride):
			left = line[x - 4] if x >= 4 else 0
			up = previous[x]
			corner = previous[x - 4] if x >= 4 else 0
			if kind == 1:
				line[x] = (line[x] + left) & 255
			elif kind == 2:
				line[x] = (line[x] + up) & 255
			elif kind == 3:
				line[x] = (line[x] + (left + up) // 2) & 255
			elif kind == 4:
				p = left + up - corner
				pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
				line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
		rows.append([tuple(line[i:i + 4]) for i in range(0, stride, 4)])
		previous = line
	return rows


def write_png(path, rows):
	raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

	def chunk(kind, body):
		return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

	header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
	path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def icon():
	canvas = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
	for name, left, top in (("letter_cap_b", 0, TILE), ("letter_cap_c", TILE, TILE), ("letter_cap_a", TILE // 2, 0)):
		tile = read_png(TILES / f"{name}.png")
		for y in range(TILE):
			for x in range(TILE):
				if tile[y][x][3]:
					canvas[top + y][left + x] = tile[y][x]
	return canvas


if __name__ == "__main__":
	write_png(ICON, icon())
	print(f"wrote {ICON.relative_to(HERE)}")
