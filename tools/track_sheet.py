"""Contact sheet of every course map for eyeballing a layout change: one band per class,
its sprints on one row and its grands prix on the next, in table order (six to a row, so
48 courses make eight rows).

    ./gradlew test --tests "*CourseMapDump*"      # writes build/track_maps/<id>.png
    python tools/track_sheet.py                   # writes art/preview/track_maps.png

Each tile is the course scaled to fit, on its class's background, with the name,
lap length, laps and what the course carries underneath. The four 48-course themes
laid over an existing course (build/track_maps/themes/) go on a last row.
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
        r"^\t([A-Z_]+)\(RaceClass\.([CBAS]), (\d+), Theme\.([A-Z_]+), Shape\.([A-Z_]+), (\d+), (\d+)(.*)\)[,;]$")
    for line in TABLE.read_text(encoding="utf-8").splitlines():
        m = pattern.match(line)
        if not m:
            continue
        name, cls, course, theme, shape, lap, laps, rest = m.groups()
        bits = []
        for kind in ("water", "ridge", "lava", "mud"):
            n = rest.count(kind + "(")
            if n:
                bits.append(f"{n}x {kind}")
        bits.append(f"{rest.count('boost(')} boosts")
        kind = "sprint" if laps == "1" else "grand prix"
        out[name.lower()] = (f"{name.replace('_', ' ').title()}  —  {theme.replace('_', ' ').title()} / {shape.title()}",
                             f"{kind}: {lap} blocks x {laps} lap{'s' if laps != '1' else ''}  ·  " + ", ".join(bits),
                             "CBAS".index(cls), laps == "1", int(course))
    return out


def main():
    if not MAPS.is_dir():
        sys.exit("no build/track_maps: run the CourseMapDumpTest first")
    table = course_table()
    # one row of sprints and one of grands prix per class, in table (course index) order
    rows = []
    for c in range(4):
        for sprint in (True, False):
            row = sorted((n for n, v in table.items() if v[2] == c and v[3] == sprint and (MAPS / f"{n}.png").is_file()),
                         key=lambda n: table[n][4])
            if row:
                rows.append([(MAPS / f"{n}.png", table[n][0], table[n][1], n[0]) for n in row])
    themes = sorted((MAPS / "themes").glob("*.png")) if (MAPS / "themes").is_dir() else []
    if themes:
        rows.append([(p, p.stem.replace("_on_", " on ").replace("_", " ").title(), "theme preview (no course wears it yet)", "t")
                     for p in themes])
    if not rows:
        sys.exit("no course maps found")
    cols = max(len(r) for r in rows)
    cell = (TILE[0] + PAD, TILE[1] + LABEL + PAD)
    sheet = Image.new("RGB", (cols * cell[0] + PAD, len(rows) * cell[1] + PAD), (18, 18, 20))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arialbd.ttf", 15)
        small = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = small = ImageFont.load_default()
    count = 0
    for r, row in enumerate(rows):
        for i, (path, title, detail, cls) in enumerate(row):
            img = Image.open(path).convert("RGB")
            scale = min(TILE[0] / img.width, TILE[1] / img.height)
            img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.NEAREST)
            x = PAD + i * cell[0]
            y = PAD + r * cell[1]
            draw.rectangle([x, y, x + TILE[0], y + TILE[1] + LABEL], fill=CLASS_BG.get(cls, (30, 30, 30)))
            sheet.paste(img, (x + (TILE[0] - img.width) // 2, y + (TILE[1] - img.height) // 2))
            draw.text((x + 8, y + TILE[1] + 3), title, font=font, fill=(236, 236, 226))
            draw.text((x + 8, y + TILE[1] + 19), detail, font=small, fill=(158, 162, 168))
            count += 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"{OUT.relative_to(ROOT)}  ({count} maps, {sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()
