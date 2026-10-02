"""16x16 Chocobo Whistle icon. Gold body, yellow feather on the left, clear background.

    python tools/paint_whistle.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src/main/resources/assets/chocobosreborn/textures/item/chocobo_whistle.png"

GOLD = (0xE0, 0xB0, 0x40, 255)
SHADE = (0x8A, 0x64, 0x20, 255)
HIGHLIGHT = (0xF6, 0xE2, 0xA0, 255)
FEATHER = (0xF5, 0xB8, 0x12, 255)

# Side-view pea whistle: ring on top, feather to the left, window in the tube.
ROWS = [
	"................",
	"......#HH#......",
	".....#H..H#.....",
	".....#....#.....",
	"......#GG#......",
	".FF...#GG#......",
	"FFFF.#GGGGG#....",
	"FFFFF#GHHGGG#...",
	".FFFF#GH..GGD#..",
	"..FF.#GHHHGGD#..",
	"...F.#GGGGGGD#..",
	"......#DDDDD#...",
	".......#DD#.....",
	"................",
	"................",
	"................",
]

PIXEL = {
	"#": SHADE,
	"H": HIGHLIGHT,
	"G": GOLD,
	"D": SHADE,
	"F": FEATHER,
}


def paint() -> Image.Image:
	if len(ROWS) != 16 or any(len(row) != 16 for row in ROWS):
		raise SystemExit("whistle map must be 16x16")
	image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(ROWS):
		for x, cell in enumerate(row):
			if cell == ".":
				continue
			image.putpixel((x, y), PIXEL[cell])
	return image


def main() -> None:
	image = paint()
	DEST.parent.mkdir(parents=True, exist_ok=True)
	image.save(DEST)
	print("wrote", DEST.relative_to(ROOT), image.size, image.mode)


if __name__ == "__main__":
	main()
