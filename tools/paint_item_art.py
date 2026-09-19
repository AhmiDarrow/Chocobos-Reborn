"""SUPERSEDED (2026-09-19): tools/pixel_items.py is the source of truth for these textures
(32x32, Ninjacat Skies family style). Running this would overwrite them with the old art,
so it refuses unless given --legacy.

Hand-painted 16x16 item/block icons: gates, pocketwatch, almanac.

Meshy downsample made the gates muddy and the two items were 5-6 colour blobs.
Vanilla Minecraft wants hard pixels, a dark outline, and two-tone shading.

    python tools/paint_item_art.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/chocobosreborn/textures"
PREVIEW = ROOT / "art/icons/preview"


def blank(w: int = 16, h: int = 16) -> Image.Image:
	return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def p(im: Image.Image, x: int, y: int, c: tuple[int, ...]) -> None:
	if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
		im.putpixel((x, y), c if len(c) == 4 else (*c, 255))


def fill(im: Image.Image, x0: int, y0: int, x1: int, y1: int, c: tuple[int, ...]) -> None:
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			p(im, x, y, c)


def save(im: Image.Image, rel: str) -> None:
	dest = ASSETS / rel
	dest.parent.mkdir(parents=True, exist_ok=True)
	im.save(dest)
	PREVIEW.mkdir(parents=True, exist_ok=True)
	im.resize((256, 256), Image.Resampling.NEAREST).save(PREVIEW / (dest.stem + "_x16.png"))
	print("wrote", dest.relative_to(ROOT), im.size)


# ------------------------------------------------------------------ palettes

INK = (18, 14, 12)
IRON0, IRON1, IRON2, IRON3 = (22, 22, 26), (48, 48, 56), (78, 78, 88), (118, 118, 128)
GOLD0, GOLD1, GOLD2, GOLD3 = (122, 78, 14), (184, 120, 22), (232, 164, 22), (255, 220, 120)
PANEL0, PANEL1 = (24, 20, 16), (38, 32, 26)
LEATHER0, LEATHER1, LEATHER2 = (58, 34, 18), (92, 54, 28), (132, 82, 42)
PAGE0, PAGE1, PAGE2 = (232, 214, 168), (244, 232, 196), (210, 190, 140)
CREAM, CREAM2 = (236, 228, 200), (210, 200, 168)
STEM = (90, 58, 12)
GREEN0, GREEN1, GREEN2 = (28, 90, 32), (52, 150, 58), (120, 210, 90)

GEMS = {
	"square_gate": ((24, 92, 36), (56, 168, 64), (150, 230, 120)),
	"square_gate_short": ((168, 128, 16), (232, 196, 48), (255, 240, 140)),
	"square_gate_long": ((24, 72, 140), (56, 140, 210), (140, 210, 245)),
	"square_gate_return": ((140, 28, 24), (210, 64, 48), (240, 150, 110)),
}


def gate(name: str) -> Image.Image:
	g0, g1, g2 = GEMS[name]
	im = blank()
	# iron surround so neighbouring blocks read as a framed portal
	fill(im, 0, 0, 15, 15, IRON0)
	fill(im, 1, 1, 14, 14, IRON1)
	# gold frame, lit top-left, shaded bottom-right
	fill(im, 2, 2, 13, 13, GOLD1)
	for i in range(2, 14):
		p(im, i, 2, GOLD3)
		p(im, 2, i, GOLD2)
		p(im, i, 13, GOLD0)
		p(im, 13, i, GOLD0)
	fill(im, 4, 4, 11, 11, PANEL0)
	fill(im, 5, 5, 10, 10, PANEL1)
	# rivets
	for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
		p(im, x, y, GOLD3)
		p(im, x, y + (1 if y == 3 else -1), GOLD1)
	# outlined gem (3x3 with a highlight)
	fill(im, 6, 6, 10, 10, INK)
	fill(im, 7, 7, 9, 9, g1)
	p(im, 7, 7, g2)
	p(im, 8, 7, g2)
	p(im, 9, 9, g0)
	p(im, 8, 8, g1)
	return im


def pocketwatch() -> Image.Image:
	im = blank()
	cx, cy = 8, 9

	def rad(x: int, y: int) -> float:
		return ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5

	for y in range(16):
		for x in range(16):
			d = rad(x, y)
			if d <= 6.4:
				p(im, x, y, INK)
			if 4.35 < d <= 5.85:
				p(im, x, y, GOLD1)
			if d <= 4.35:
				p(im, x, y, CREAM)
	# thick gold rim bevel
	for x, y in ((4, 5), (5, 4), (6, 4), (3, 6), (3, 7), (4, 4)):
		if im.getpixel((x, y))[3]:
			p(im, x, y, GOLD3)
	for x, y in ((12, 12), (11, 13), (13, 11), (12, 13), (13, 12)):
		if im.getpixel((x, y))[3]:
			p(im, x, y, GOLD0)
	# hour marks
	p(im, 11, 9, INK)
	p(im, 8, 12, INK)
	p(im, 5, 9, INK)
	# gysahl cabochon at 12
	p(im, 8, 5, GREEN2)
	p(im, 7, 5, GREEN0)
	p(im, 9, 5, GREEN0)
	p(im, 8, 6, GREEN1)
	# thin hands: hour 12, minute 3
	p(im, 8, 8, INK)
	p(im, 8, 7, INK)
	p(im, 9, 9, INK)
	p(im, 10, 9, INK)
	p(im, 8, 9, GOLD3)
	# winding stem
	p(im, 7, 2, INK)
	p(im, 8, 2, GOLD1)
	p(im, 9, 2, INK)
	p(im, 7, 1, GOLD0)
	p(im, 8, 1, GOLD3)
	p(im, 9, 1, GOLD0)
	p(im, 8, 0, GOLD2)
	# bow / chain loop
	p(im, 10, 0, INK)
	p(im, 11, 0, GOLD2)
	p(im, 11, 1, GOLD1)
	p(im, 12, 1, INK)
	return im


def almanac() -> Image.Image:
	im = blank()
	# closed book, 3/4: dark spine, leather cover, page stack
	fill(im, 1, 2, 13, 14, INK)
	fill(im, 2, 3, 3, 13, LEATHER0)  # spine
	p(im, 2, 4, LEATHER1)
	p(im, 2, 8, LEATHER1)
	fill(im, 4, 3, 11, 13, LEATHER1)
	fill(im, 5, 4, 10, 5, LEATHER2)
	fill(im, 5, 4, 5, 12, LEATHER2)
	# gold corners
	p(im, 4, 3, GOLD3)
	p(im, 5, 3, GOLD2)
	p(im, 4, 4, GOLD2)
	p(im, 10, 3, GOLD3)
	p(im, 11, 3, GOLD2)
	p(im, 11, 4, GOLD1)
	p(im, 4, 13, GOLD1)
	p(im, 5, 13, GOLD0)
	p(im, 10, 13, GOLD1)
	p(im, 11, 13, GOLD0)
	p(im, 11, 12, GOLD1)
	# gold clasp
	p(im, 11, 7, GOLD3)
	p(im, 11, 8, GOLD2)
	p(im, 10, 7, GOLD1)
	p(im, 10, 8, GOLD0)
	# gold three-toed print (the same mark as GP)
	p(im, 5, 7, GOLD3)
	p(im, 7, 7, GOLD3)
	p(im, 9, 7, GOLD3)
	p(im, 6, 8, GOLD2)
	p(im, 7, 8, GOLD3)
	p(im, 8, 8, GOLD2)
	p(im, 7, 9, GOLD1)
	p(im, 7, 10, GOLD0)
	# gysahl sprig
	p(im, 5, 5, GREEN2)
	p(im, 4, 5, GREEN1)
	p(im, 5, 6, GREEN0)
	# page stack
	fill(im, 12, 3, 13, 13, PAGE2)
	fill(im, 12, 4, 12, 12, PAGE0)
	p(im, 13, 4, PAGE1)
	for y in (6, 8, 10, 12):
		p(im, 12, y, PAGE2)
	# bookmark
	p(im, 8, 2, (176, 36, 32))
	p(im, 8, 1, (220, 64, 48))
	p(im, 8, 0, (176, 36, 32))
	p(im, 9, 0, (128, 24, 20))
	return im


def almanac_gui() -> Image.Image:
	# Dark journal: existing almanac copy is drawn in cream/gold, so the page stays dark.
	im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
	fill(im, 0, 0, 255, 255, LEATHER0)
	fill(im, 5, 5, 250, 250, LEATHER1)
	fill(im, 10, 10, 245, 11, GOLD1)
	fill(im, 10, 244, 245, 245, GOLD0)
	fill(im, 10, 10, 11, 245, GOLD1)
	fill(im, 244, 10, 245, 245, GOLD0)
	fill(im, 14, 14, 241, 241, (22, 16, 12, 255))
	fill(im, 16, 16, 239, 239, (32, 24, 18, 255))
	for x0, y0 in ((6, 6), (238, 6), (6, 238), (238, 238)):
		fill(im, x0, y0, x0 + 11, y0 + 11, GOLD1)
		p(im, x0 + 2, y0 + 2, GOLD3)
	fill(im, 14, 14, 18, 241, (18, 12, 10, 255))
	return im


def main() -> None:
	for name in GEMS:
		save(gate(name), f"block/{name}.png")
	save(pocketwatch(), "item/chocobo_pocketwatch.png")
	save(almanac(), "item/chocobo_almanac.png")
	save(almanac_gui(), "gui/almanac.png")


if __name__ == "__main__":
	import sys as _sys
	if "--legacy" not in _sys.argv:
	    raise SystemExit("superseded by tools/pixel_items.py; pass --legacy to run anyway")
	_sys.argv = [a for a in _sys.argv if a != "--legacy"]
	main()
