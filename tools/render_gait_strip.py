"""Render a frame strip of one clip (run / walk / idle) from art/fresh_chocobo.blend.

    blender --background --python tools/render_gait_strip.py -- --clip run --frames 8
Writes art/preview/gait_<clip>.png (side view) and gait_<clip>_34.png.
"""
from __future__ import annotations

import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ncgb_export import animate  # noqa: E402
from stills import GAME_CAM, PREVIEW, TARGET_H, aim, contact_sheet, pose_hide, render, studio  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
BLEND = ROOT / "art/fresh_chocobo.blend"


def arg(name, default):
    if name in sys.argv:
        return type(default)(sys.argv[sys.argv.index(name) + 1])
    return default


def main():
    clip = arg("--clip", "run")
    nfr = arg("--frames", 8)
    bpy.ops.wm.open_mainfile(filepath=str(BLEND))
    arm = bpy.data.objects["rig"]
    body = bpy.data.objects["chocobo"]
    cam = bpy.context.scene.camera or bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    if cam.name not in bpy.context.scene.collection.objects:
        bpy.context.scene.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    for o in list(bpy.data.objects):
        if o.type == "MESH" and o.name.lower().startswith("floor"):
            bpy.data.objects.remove(o, do_unlink=True)
        elif o.type == "LIGHT":
            bpy.data.objects.remove(o, do_unlink=True)
    studio((520, 640), lit=True)
    bpy.context.scene.cycles.use_denoising = False
    bpy.context.scene.cycles.samples = 16
    animate(arm, clip)
    pose_hide(arm, ("saddle", "bridle"), True)
    h = TARGET_H
    n = bpy.context.scene.frame_end
    side, q34 = [], []
    for k in range(nfr):
        f = 1 + int(round(k * n / nfr))
        bpy.context.scene.frame_set(f)
        aim(cam, (0.0, 0.0, 0.50 * h), (7.0 * GAME_CAM, 0.0, 0.50 * h), 50)
        p = PREVIEW / f"gait_{clip}_side_{k}.png"
        render(p)
        side.append(p)
        aim(cam, (0.0, -0.1 * h, 0.50 * h), (5.4 * GAME_CAM, -3.4 * GAME_CAM, 0.52 * h), 48)
        p = PREVIEW / f"gait_{clip}_34_{k}.png"
        render(p)
        q34.append(p)
    contact_sheet(side, PREVIEW / f"gait_{clip}.png", cols=nfr)
    contact_sheet(q34, PREVIEW / f"gait_{clip}_34.png", cols=nfr)
    for p in side + q34:
        p.unlink(missing_ok=True)
    print("strip", PREVIEW / f"gait_{clip}.png")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        traceback.print_exc()
        sys.exit(1)
