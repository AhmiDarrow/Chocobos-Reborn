"""Render breed stills from the shipped atlases without re-running the Meshy import:

    blender -b art/fresh_chocobo.blend -P tools/render_breeds.py -- chocobo purple flame
    blender -b art/fresh_chocobo.blend -P tools/render_breeds.py -- chocobo --atlases <dir> --prefix derived_

Writes art/preview/<prefix>atlas_<variant>_<breed>.png for each breed named (default:
all). --atlases reads <dir>/<breed>.png instead of the shipped folder; --out picks the
output folder.
"""
import sys
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import stills  # noqa: E402
from paint_albedo import PLUMAGE, recolor_plumage  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import tempfile  # noqa: E402

TEXDIR = ROOT / "src/main/resources/assets/chocobosreborn/textures/entity"
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


def opt(flag, default=None):
    if flag in args:
        i = args.index(flag)
        value = args[i + 1]
        del args[i:i + 2]
        return value
    return default


atlases = opt("--atlases")
prefix = opt("--prefix", "")
out_dir = opt("--out")
variant = args[0] if args else "chocobo"
names = args[1:] or list(PLUMAGE)

body = next(o for o in bpy.data.objects if o.type == "MESH")
cam = bpy.context.scene.camera
if cam is None:
    bpy.ops.object.camera_add()
    cam = bpy.context.object
    bpy.context.scene.camera = cam
nodes = []
for slot in body.material_slots:
    if slot.material and slot.material.node_tree:
        for n in slot.material.node_tree.nodes:
            if n.type == "TEX_IMAGE" and n.image is not None:
                nodes.append(n)
if not nodes:
    raise RuntimeError("no image texture on the body material")
out = Path(out_dir) if out_dir else ROOT / "art/preview"
out.mkdir(parents=True, exist_ok=True)
for name in names:
    p = (Path(atlases) if atlases else TEXDIR / variant) / f"{name}.png"
    if not p.is_file() and not atlases:
        # solid breeds are not in the jar: derive from the shipped yellow like the client does
        yellow = TEXDIR / variant / "yellow.png"
        px = np.asarray(Image.open(yellow).convert("RGBA")).astype(np.float32) / 255.0
        out = recolor_plumage(px, PLUMAGE[name], variant)
        p = Path(tempfile.gettempdir()) / f"chocobosreborn_{variant}_{name}.png"
        Image.fromarray((np.clip(out, 0, 1) * 255.0 + 0.5).astype(np.uint8), "RGBA").save(p)
    # the blend's albedo is packed; swap in a fresh image loaded from the shipped atlas
    img = bpy.data.images.load(str(p), check_existing=False)
    for n in nodes:
        n.image = img
    stills.frame_bird(cam, body)
    stills.render(out / f"{prefix}atlas_{variant}_{name}.png")
    print("rendered", name)
