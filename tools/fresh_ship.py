"""Fresh-start ship (Ahmi, 2026-09-16 evening): take the Meshy image-to-3D of the
approved still-8 (tools/meshy_i23d_fresh.py -> art/meshy/fresh/<tag>.glb) AS IS -
Meshy's mesh, Meshy's UVs, Meshy's baked texture - and only rig, plant, breed-tint,
render stills and export (tools/chocobo_rig.py, tools/stills.py, tools/ncgb_export.py).
No remesh, no re-bake, no repaint: those steps mangled the head and eyes.

    blender --background --python tools/fresh_ship.py                  # plain bird
    blender --background --python tools/fresh_ship.py -- --tag saddled  # saddle + bridle + reins baked in

--tag fresh -> mesh "chocobo", atlases textures/entity/chocobo/, stills meshy_char_*;
--tag saddled -> "chocobo_saddled", textures/entity/chocobo_saddled/, meshy_saddled_*.
The renderer picks the saddled mesh + atlases when the bird wears a saddle.
Writes art/fresh_<variant>.blend and src/.../entity/<variant>.ncgb.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chocobo_rig as rig  # noqa: E402
from chocobo_rig import apply_tr  # noqa: E402
from ncgb_export import export_ncgb  # noqa: E402
import stills  # noqa: E402
from stills import albedo_pixels, find_albedo, render_previews, write_breed_atlases  # noqa: E402
import pad_atlases  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
EXPORT = ROOT / "src/main/resources/assets/chocobosreborn/entity"


def arg(name, default):
    if name in sys.argv:
        return type(default)(sys.argv[sys.argv.index(name) + 1])
    return default


def load(glb: Path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(glb))
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    body = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.select_all(action="DESELECT")
        for o in meshes:
            o.select_set(True)
        bpy.context.view_layer.objects.active = body
        bpy.ops.object.join()
        body = bpy.context.object
    for o in list(bpy.data.objects):
        if o is not body:
            bpy.data.objects.remove(o, do_unlink=True)
    body.name = "chocobo"
    apply_tr(body)
    print("loaded", glb.name, len(body.data.polygons), "faces", "materials", [m.name for m in body.data.materials])
    return body


def orient_front_neg_y(body):
    """Meshy's orientation follows the image. The rig wants -Y forward: the crest
    is the highest point, so the top 4 % of vertices vs the whole-body centroid
    gives the forward direction; yaw the mesh so it points to -Y."""
    me = body.data
    nv = len(me.vertices)
    co = np.empty(nv * 3, dtype=np.float32); me.vertices.foreach_get("co", co); co = co.reshape(nv, 3)
    top = co[co[:, 2] >= np.quantile(co[:, 2], 0.96)]
    f = top[:, :2].mean(axis=0) - co[:, :2].mean(axis=0)
    ang = math.atan2(f[1], f[0])            # current forward angle in the XY plane
    yaw = (-math.pi / 2) - ang              # rotate so forward -> -Y
    R = Matrix.Rotation(yaw, 4, "Z")
    body.matrix_world = R @ body.matrix_world
    apply_tr(body)
    print("oriented: forward was %.0f deg, yawed %.0f deg" % (math.degrees(ang), math.degrees(yaw)))


def flat_shade(body):
    body.data.polygons.foreach_set("use_smooth", [False] * len(body.data.polygons))
    body.data.update()


VARIANTS = {"fresh": ("chocobo", "meshy_char"), "saddled": ("chocobo_saddled", "meshy_saddled"),
            "armor_iron": ("chocobo_armor_iron", "meshy_armor_iron"),
            "armor_diamond": ("chocobo_armor_diamond", "meshy_armor_diamond")}


def main():
    tag = arg("--tag", "fresh")
    variant, prefix = VARIANTS[tag]
    stills.VARIANT, stills.STILL_PREFIX = variant, prefix
    blend = ROOT / f"art/fresh_{variant}.blend"
    glb = ROOT / "art/meshy/fresh" / f"{tag}.glb"
    if not glb.is_file():
        raise FileNotFoundError(glb)
    body = load(glb)
    orient_front_neg_y(body)
    flat_shade(body)
    me = body.data
    nv = len(me.vertices)
    co = np.empty(nv * 3, dtype=np.float32); me.vertices.foreach_get("co", co); co = co.reshape(nv, 3)
    lm = rig.landmarks(co, None)
    print("landmarks", {k: (round(v, 3) if isinstance(v, float) else v) for k, v in lm.items()})
    arm = rig.build_armature(body, lm)
    rig.paint_weights(body, arm, lm)
    rig.white_vcol(body)

    src_img = find_albedo(body)
    if src_img is None:
        raise RuntimeError("no base colour image on the Meshy material")
    src_img.pack()
    px = albedo_pixels(src_img)
    print("texture", src_img.size[:], "px", px.shape)
    rig.plant(arm, body)
    yellow_px = write_breed_atlases(src_img, px=px)
    pad_atlases.main(variant)

    bpy.ops.object.camera_add()
    cam = bpy.context.object
    bpy.context.scene.camera = cam
    render_previews(arm, body, src_img, yellow_px)

    EXPORT.mkdir(parents=True, exist_ok=True)
    arm.data.pose_position = "POSE"
    stills.pose_hide(arm, ("crest_male",), False)
    stills.pose_hide(arm, ("saddle", "bridle"), True)
    export_ncgb(variant, arm, body, EXPORT, smooth_normals=0.0, uv_inset=0.0)
    stills._reset_pose(arm)
    bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    print("done")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        import traceback
        traceback.print_exc()
        sys.exit(1)
