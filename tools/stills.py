"""Studio stills for the chocobo (sliced 2026-09-16 from the retired
meshy_character_ship.py / meshy_ship.py): breed atlases, the 15 QA stills
(male, male_front, female, 9 breeds, 3 chick stages), contact sheets, gait strips.
"""
from __future__ import annotations

import math
import shutil
import time
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector

from chocobo_rig import TARGET_H
from paint_albedo import PLUMAGE, accent_plumage, recolor_plumage, paint_breed

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "art/preview"
TEXDIR = ROOT / "src/main/resources/assets/chocobosreborn/textures/entity"
# Variant = mesh id + texture folder + still prefix. "chocobo" = the plain bird,
# "chocobo_saddled" = the Meshy bird with the saddle / bridle / reins baked in.
VARIANT = "chocobo"
STILL_PREFIX = "meshy_char"
LOOK_H = 3.2
GAME_CAM = TARGET_H / LOOK_H
BREED_IDS = ("yellow", "green", "blue", "white", "black", "gold", "purple", "flame")
PLAYER_H = 1.8
BABY = (
    ("baby0", (PLAYER_H * 0.25) / TARGET_H),
    ("baby1", (PLAYER_H * 0.50) / TARGET_H),
    ("baby2", (PLAYER_H * 0.75) / TARGET_H),
)


def studio(res, lit=True):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "GPU"
    prefs = bpy.context.preferences.addons.get("cycles")
    if prefs:
        try:
            prefs.preferences.compute_device_type = "CUDA"
            for dev in prefs.preferences.devices:
                dev.use = True
        except Exception:
            scene.cycles.device = "CPU"
    scene.cycles.samples = 16 if lit else 8
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = res
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = "PNG"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.93, 0.93, 0.94, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    scene.world = world
    bpy.ops.mesh.primitive_plane_add(size=24, location=(0, 0, -0.002))
    floor = bpy.context.object
    floor.name = "floor"
    fm = bpy.data.materials.new("floor")
    fm.use_nodes = True
    fm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.94, 0.94, 0.95, 1)
    fm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.85
    floor.data.materials.append(fm)
    if lit:
        bpy.ops.object.light_add(type="AREA", location=(-2.8, -3.4, 5.8))
        key = bpy.context.object
        key.data.energy = 360
        key.data.size = 3.6
        key.rotation_euler = (Vector((0, 0, 2.2)) - Vector(key.location)).to_track_quat("-Z", "Y").to_euler()
        bpy.ops.object.light_add(type="AREA", location=(3.4, -1.2, 3.8))
        fill = bpy.context.object
        fill.data.energy = 220
        fill.data.size = 5.0
        bpy.ops.object.light_add(type="AREA", location=(0.2, 4.0, 4.5))
        rim = bpy.context.object
        rim.data.energy = 160
        rim.data.size = 3.0


def aim(cam, target, loc, lens=48):
    cam.location = loc
    cam.data.lens = lens
    cam.data.clip_start = 0.05
    cam.data.clip_end = 80
    cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()


def render(path: Path):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    bpy.context.scene.render.filepath = str(path)
    last = None
    for i in range(6):
        try:
            bpy.ops.render.render(write_still=True)
            print("rendered", path)
            return
        except RuntimeError as e:
            last = e
            msg = str(e)
            if "cannot save" not in msg and "OpenImageIO" not in msg:
                raise
            time.sleep(0.4 * (i + 1))
    raise last


def find_albedo(obj):
    if obj.data.materials:
        mat = obj.data.materials[0]
        if mat and mat.use_nodes:
            for n in mat.node_tree.nodes:
                if n.type == "TEX_IMAGE" and n.image and min(n.image.size) > 4:
                    n.interpolation = "Closest"
                    return n.image
    return None


def albedo_pixels(img):
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    return px.reshape(h, w, 4).copy()


def write_breed_atlases(src_img, px):
    src_img.pixels.foreach_set(px.ravel())
    img = bpy.data.images.new("breed_atlas", src_img.size[0], src_img.size[1], alpha=True)
    for name, rgb in PLUMAGE.items():
        out = paint_breed(px, name, VARIANT)
        img.pixels.foreach_set(out.ravel())
        # one atlas set: chicobos are the adult mesh scaled (ChocoboMeshRenderer)
        d = TEXDIR / VARIANT
        d.mkdir(parents=True, exist_ok=True)
        p = d / f"{name}.png"
        img.filepath_raw = str(p)
        img.file_format = "PNG"
        img.save()
    PREVIEW.mkdir(parents=True, exist_ok=True)
    print("wrote Meshy albedo breed atlases")
    return px


def set_albedo(img, px):
    img.pixels.foreach_set(px.ravel())


def frame_bird(cam, body, lens=48):
    """Adult-style 3/4: whole bird in frame, aim at mid-height, not a torso crop."""
    dg = bpy.context.evaluated_depsgraph_get()
    ev = body.evaluated_get(dg)
    bb = [ev.matrix_world @ Vector(c) for c in ev.bound_box]
    minc = Vector((min(v.x for v in bb), min(v.y for v in bb), min(v.z for v in bb)))
    maxc = Vector((max(v.x for v in bb), max(v.y for v in bb), max(v.z for v in bb)))
    h = max(maxc.z - minc.z, 0.15)
    target = (0.0, 0.5 * (minc.y + maxc.y) - 0.06 * h, minc.z + 0.52 * h)
    loc = (1.70 * h, -1.10 * h, minc.z + 0.52 * h)
    aim(cam, target, loc, lens)


def pose_hide(arm, names, hide):
    s = (0.001, 0.001, 0.001) if hide else (1.0, 1.0, 1.0)
    for name in names:
        pb = arm.pose.bones.get(name)
        if pb is None:
            continue
        pb.scale = s
    bpy.context.view_layer.update()


def contact_sheet(paths, out: Path, cols=3):
    try:
        from PIL import Image
    except ImportError:
        print("no PIL, skip sheet")
        return
    ims = [Image.open(p).convert("RGB") for p in paths if p.is_file()]
    if not ims:
        return
    w, h = ims[0].size
    ims = [im.resize((w, h), Image.Resampling.BILINEAR) for im in ims]
    rows = math.ceil(len(ims) / cols)
    canvas = Image.new("RGB", (cols * w, rows * h), (236, 236, 238))
    for i, im in enumerate(ims):
        canvas.paste(im, ((i % cols) * w, (i // cols) * h))
    canvas.save(out)
    print("sheet", out)


def _copy_image_pixels(dst, src_path: Path):
    loaded = bpy.data.images.load(str(src_path), check_existing=False)
    w, h = loaded.size
    buf = np.empty(w * h * 4, dtype=np.float32)
    loaded.pixels.foreach_get(buf)
    if tuple(dst.size) != (w, h):
        dst.scale(w, h)
    dst.pixels.foreach_set(buf)
    dst.update()


def _reset_pose(arm):
    if arm.animation_data:
        arm.animation_data_clear()
    arm.data.pose_position = "POSE"
    for pb in arm.pose.bones:
        pb.matrix_basis.identity()
        pb.location = (0.0, 0.0, 0.0)
        pb.scale = (1.0, 1.0, 1.0)
        if pb.rotation_mode == "QUATERNION":
            pb.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)
        else:
            pb.rotation_euler = (0.0, 0.0, 0.0)
    bpy.context.scene.frame_set(0)
    bpy.context.view_layer.update()


def render_previews(arm, body, src_img, yellow_px):
    cam = bpy.context.scene.camera
    if cam is None:
        bpy.ops.object.camera_add()
        cam = bpy.context.object
        bpy.context.scene.camera = cam
    for o in list(bpy.data.objects):
        if o.type == "MESH" and o.name.lower().startswith("floor"):
            bpy.data.objects.remove(o, do_unlink=True)
        elif o.type == "LIGHT":
            bpy.data.objects.remove(o, do_unlink=True)
    h = max(body.dimensions.z, TARGET_H)
    studio((720, 880), lit=True)
    bpy.context.scene.cycles.use_denoising = False
    bpy.context.scene.cycles.samples = 32
    target = (0.0, -0.10 * h, 0.52 * h)
    hero_loc = (5.4 * GAME_CAM, -3.4 * GAME_CAM, h * 0.52)

    _reset_pose(arm)
    pose_hide(arm, ("saddle", "bridle"), True)
    pose_hide(arm, ("crest_male",), False)
    aim(cam, target, hero_loc, 48)
    render(PREVIEW / f"{STILL_PREFIX}_male.png")
    aim(cam, (0.0, -0.08 * h, 0.50 * h), (0.04, -7.2 * GAME_CAM, 0.50 * h), 50)
    render(PREVIEW / f"{STILL_PREFIX}_male_front.png")
    aim(cam, target, hero_loc, 48)
    pose_hide(arm, ("crest_male",), True)
    render(PREVIEW / f"{STILL_PREFIX}_female.png")
    pose_hide(arm, ("crest_male",), False)

    breed_paths = []
    for name in BREED_IDS:
        px = paint_breed(yellow_px, name, VARIANT)
        set_albedo(src_img, px)
        pose_hide(arm, ("crest_male",), False)
        aim(cam, target, hero_loc, 48)
        p = PREVIEW / f"{STILL_PREFIX}_{name}.png"
        render(p)
        breed_paths.append(p)
    set_albedo(src_img, yellow_px)
    contact_sheet(breed_paths, PREVIEW / f"{STILL_PREFIX}_breeds.png", cols=3)

    _reset_pose(arm)
    pose_hide(arm, ("crest_male",), True)
    pose_hide(arm, ("saddle", "bridle"), True)
    baby_paths = []
    for name, g in BABY:
        arm.scale = (g, g, g)
        bpy.context.view_layer.update()
        frame_bird(cam, body, 48)
        p = PREVIEW / f"{STILL_PREFIX}_{name}.png"
        render(p)
        baby_paths.append(p)
    arm.scale = (1.0, 1.0, 1.0)
    bpy.context.view_layer.update()
    contact_sheet(
        [PREVIEW / f"{STILL_PREFIX}_male.png", PREVIEW / f"{STILL_PREFIX}_female.png"] + baby_paths,
        PREVIEW / f"{STILL_PREFIX}_stages.png",
        cols=2,
    )
    print("rendered previews")
