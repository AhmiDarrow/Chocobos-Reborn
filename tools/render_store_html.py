"""Render docs/public/store-description.md to store-description.html for the CurseForge
Author Console's HTML editor (headings, paragraphs, bold, links, lists, tables).

    python tools/render_store_html.py
"""
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/public/store-description.md"
OUT = ROOT / "docs/public/store-description.html"


def inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    return text


def render(md: str) -> str:
    out, lines, i = [], md.splitlines(), 0
    para: list[str] = []

    def flush():
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>")
            para.clear()

    while i < len(lines):
        line = lines[i]
        if line.startswith("# "):
            flush()
            out.append("<p><strong>" + inline(line[2:]) + "</strong></p>")
        elif line.startswith("## "):
            flush()
            out.append("\n<h2>" + inline(line[3:]) + "</h2>")
        elif line.startswith("- "):
            flush()
            out.append("<ul>")
            while i < len(lines) and lines[i].startswith("- "):
                out.append("<li>" + inline(lines[i][2:]) + "</li>")
                i += 1
            out.append("</ul>")
            continue
        elif re.match(r"^\d+\. ", line):
            flush()
            out.append("<ol>")
            while i < len(lines) and re.match(r"^\d+\. ", lines[i]):
                out.append("<li>" + inline(re.sub(r"^\d+\. ", "", lines[i])) + "</li>")
                i += 1
            out.append("</ol>")
            continue
        elif line.startswith("|"):
            flush()
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip("|").split("|")]
                if not all(re.fullmatch(r"-*", c) for c in cells):
                    rows.append(cells)
                i += 1
            out.append("<table>")
            for cells in rows:
                if any(cells):
                    out.append("<tr>" + "".join("<td>" + inline(c) + "</td>" for c in cells) + "</tr>")
            out.append("</table>")
            continue
        elif line.startswith("!["):
            flush()
            m = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", line)
            if m:
                out.append(f'<p><img src="{m.group(2)}" alt="{html.escape(m.group(1))}"></p>')
        elif line.strip() == "":
            flush()
        else:
            para.append(line.strip())
        i += 1
    flush()
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    OUT.write_text(render(SRC.read_text(encoding="utf-8")), encoding="utf-8")
    print("wrote", OUT)
