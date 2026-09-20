"""Contact sheet of every course map, four to a row, for eyeballing a layout change.

    ./gradlew test --tests "*CourseMapDump*"      # writes build/track_maps/<id>.png
    python tools/track_sheet.py                   # writes art/preview/track_maps.png

Each tile is the course scaled to fit, on its class's background, with the name,
lap length and what the course carries underneath.
"""

import pathlib
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAPS = ROOT / "build" / "track_maps"
OUT = ROOT / "art" / "preview" / "track_maps.png"
TILE = (460, 300)
PAD = 14
LABEL = 34
CLASS_BG = {"c": (32, 44, 36), "b": (36, 38, 52), "a": (46, 34, 40), "s": (44, 40, 28)}

TABLE = ROOT / "src/main/java/tk/darrow/chocobosreborn/race/RaceTrack.java"


def course_table():
    """name -> (lap target, laps, feature summary) straight from the enum."""
    out = {}
    pattern = re.compile(
        r"^\t([A-Z_]+)\(RaceClass\.[CBAS], \d+, Theme\.([A-Z]+), Shape\.([A-Z]+), (\d+), (\d+)(.*)\)[,;]$")
    for line in TABLE.read_text(encoding="utf-8").splitlines():
        m = pattern.match(line)
        if not m:
            continue
        name, theme, shape, lap, laps, rest = m.groups()
        bits = []
        for kind in ("water", "ridge", "lava", "mud"):
            n = rest.count(kind + "(")
            if n:
                bits.append(f"{n}x {kind}")
        bits.append(f"{rest.count('boost(')} boosts")
        out[name.lower()] = (f"{name.replace('_', ' ').title()}  —  {theme.title()} / {shape.title()}",
                             f"{lap} blocks x {laps} lap{'s' if laps != '1' else ''}  ·  " + ", ".join(bits))
    return out


def main():
    if not MAPS.is_dir():
        sys.exit("no build/track_maps: run the CourseMapDumpTest first")
    table = course_table()
    names = [n for n in table if (MAPS / f"{n}.png").is_file()]
    order = {"c": 0, "b": 1, "a": 2, "s": 3}
    names.sort(key=lambda n: (order.get(n[0], 9), n))
    if not names:
        sys.exit("no course maps found")
    cols = 4
    rows = (len(names) + cols - 1) // cols
    cell = (TILE[0] + PAD, TILE[1] + LABEL + PAD)
    sheet = Image.new("RGB", (cols * cell[0] + PAD, rows * cell[1] + PAD), (18, 18, 20))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arialbd.ttf", 15)
        small = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = small = ImageFont.load_default()
    for i, name in enumerate(names):
        img = Image.open(MAPS / f"{name}.png").convert("RGB")
        scale = min(TILE[0] / img.width, TILE[1] / img.height)
        img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.NEAREST)
        x = PAD + (i % cols) * cell[0]
        y = PAD + (i // cols) * cell[1]
        draw.rectangle([x, y, x + TILE[0], y + TILE[1] + LABEL], fill=CLASS_BG.get(name[0], (30, 30, 30)))
        sheet.paste(img, (x + (TILE[0] - img.width) // 2, y + (TILE[1] - img.height) // 2))
        title, detail = table[name]
        draw.text((x + 8, y + TILE[1] + 3), title, font=font, fill=(236, 236, 226))
        draw.text((x + 8, y + TILE[1] + 19), detail, font=small, fill=(158, 162, 168))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"{OUT.relative_to(ROOT)}  ({len(names)} courses, {sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()
