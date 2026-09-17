"""Position-based game rig for the chocobo (sliced 2026-09-16 from the retired
merge_b_ship.py / meshy_ship.py). Bone names are what ChocoboMeshRenderer expects.

    lm = landmarks(co)            # normalised-height landmarks from vertex coords
    arm = build_armature(body, lm)
    paint_weights(body, arm, lm)  # soft joints, max 4 influences
    white_vcol(body)              # game = vcol x atlas
    plant(arm, body)              # scale to TARGET_H, feet on z=0, centred
"""
from __future__ import annotations

import math

import bpy
import numpy as np
from mathutils import Vector

TARGET_H = 2.25  # mesh authored at 2.25 m; ChocoboEntity.ADULT_H scales it in-game

BONES = ("root", "body", "neck", "neck_mid", "head", "crest", "crest_male", "bridle",
         "saddle", "tail", "wing_l", "wing_r", "leg_l", "shin_l", "foot_l",
         "leg_r", "shin_r", "foot_r")


def apply_tr(obj):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)


def landmarks(co, lid_zn=None):
    zmin = float(co[:, 2].min())
    H = float(co[:, 2].max() - zmin)
    zn = (co[:, 2] - zmin) / H
    x, y = co[:, 0], co[:, 1]

    def slice_(lo, hi):
        return (zn >= lo) & (zn < hi)

    # leg columns from the shin band
    shin = slice_(0.06, 0.19)
    lx = float(np.median(x[shin & (x < 0)]))
    rx = float(np.median(x[shin & (x > 0)]))
    ly = float(np.median(y[shin & (x < 0)]))
    ry = float(np.median(y[shin & (x > 0)]))
    # foot: forward extent of toes
    foot = slice_(0.0, 0.05)
    toe_y = float(np.quantile(y[foot], 0.05))
    # head base: lowest zn where the slice is clearly forward of the body
    # (skull sits at y < -0.1 while the tail fan is y > 0.4)
    neck_top = 0.72   # short neck: zn 0.58..0.72 on the still-8 bird
    neck_base = 0.58
    hip = 0.34
    ankle = 0.20
    toes = 0.05
    # skull top vs crest quills: width of the head slice
    head = zn > neck_top
    # skull lid = the largest upward-facing face above the neck (measured by
    # the caller from polygons); quills are everything above it.
    z_skull_top = (lid_zn + 0.006) if lid_zn is not None else 0.90
    head_y = float(np.median(y[head & (zn < z_skull_top)]))
    neck_y = float(np.median(y[slice_(neck_base, neck_top) & (y < 0.2)]))
    body_y = float(np.median(y[slice_(hip, neck_base) & (np.abs(y) < 0.45)]))
    tail_y0 = float(np.quantile(y[slice_(0.40, 0.75)], 0.80))
    return dict(
        zmin=zmin, H=H, lx=lx, rx=rx, ly=ly, ry=ry, toe_y=toe_y, hip=hip, ankle=ankle,
        toes=toes, neck_base=neck_base, neck_top=neck_top, skull_top=z_skull_top,
        head_y=head_y, neck_y=neck_y, body_y=body_y, tail_y0=tail_y0,
    )


def build_armature(body, lm):
    H = lm["H"]
    z0 = lm["zmin"]

    def Z(zn):
        return z0 + zn * H

    arm_data = bpy.data.armatures.new("rig")
    arm = bpy.data.objects.new("rig", arm_data)
    bpy.context.scene.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones

    def bone(name, head, tail, parent=None):
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        if parent:
            b.parent = eb[parent]
        b.use_connect = False
        b.use_deform = True
        return b

    by = lm["body_y"]
    bone("root", (0, by, Z(0.0)), (0, by, Z(0.10)))
    bone("body", (0, by + 0.05 * H, Z(lm["hip"])), (0, by - 0.02 * H, Z(lm["neck_base"])), "root")
    ny = lm["neck_y"]
    n_mid = 0.5 * (lm["neck_base"] + lm["neck_top"])
    bone("neck", (0, ny + 0.02 * H, Z(lm["neck_base"])), (0, ny, Z(n_mid)), "body")
    bone("neck_mid", (0, ny, Z(n_mid)), (0, ny - 0.01 * H, Z(lm["neck_top"])), "neck")
    hy = lm["head_y"]
    bone("head", (0, ny - 0.01 * H, Z(lm["neck_top"])), (0, hy, Z(lm["skull_top"])), "neck_mid")
    bone("crest", (0, hy + 0.02 * H, Z(lm["skull_top"])), (0, hy + 0.04 * H, Z(lm["skull_top"] + 0.04)), "head")
    bone("crest_male", (0, hy + 0.02 * H, Z(lm["skull_top"])), (0, hy + 0.05 * H, Z(1.0)), "head")
    bone("bridle", (0, hy - 0.10 * H, Z(lm["neck_top"] + 0.05)), (0, hy - 0.16 * H, Z(lm["neck_top"] + 0.05)), "head")
    bone("saddle", (0, by, Z(lm["neck_base"]) + 0.02 * H), (0, by, Z(lm["neck_base"]) + 0.06 * H), "body")
    bone("tail", (0, lm["tail_y0"] - 0.02 * H, Z(0.50)), (0, lm["tail_y0"] + 0.18 * H, Z(0.70)), "body")
    for side, sx in (("l", -1.0), ("r", 1.0)):
        bone(f"wing_{side}", (sx * 0.16 * H, by, Z(0.60)), (sx * 0.20 * H, by + 0.10 * H, Z(0.50)), "body")
        lx = lm["lx"] if side == "l" else lm["rx"]
        ly = lm["ly"] if side == "l" else lm["ry"]
        bone(f"leg_{side}", (lx, ly + 0.02 * H, Z(lm["hip"])), (lx, ly, Z(lm["ankle"])), "root")
        bone(f"shin_{side}", (lx, ly, Z(lm["ankle"])), (lx, ly, Z(lm["toes"])), f"leg_{side}")
        bone(f"foot_{side}", (lx, ly, Z(lm["toes"])), (lx, lm["toe_y"], Z(0.01)), f"shin_{side}")
    bpy.ops.object.mode_set(mode="OBJECT")
    print("bones", [b.name for b in arm.data.bones])
    return arm


def _ramp(v, lo, hi):
    """0 below lo, 1 above hi, linear between."""
    return np.clip((v - lo) / max(hi - lo, 1e-6), 0.0, 1.0)


def paint_weights(body, arm, lm):
    me = body.data
    nv = len(me.vertices)
    co = np.empty(nv * 3, dtype=np.float32)
    me.vertices.foreach_get("co", co)
    co = co.reshape(nv, 3)
    H = lm["H"]
    zn = (co[:, 2] - lm["zmin"]) / H
    x, y = co[:, 0], co[:, 1]
    soft = 0.025  # half-width of joint blends in zn

    W = {n: np.zeros(nv, dtype=np.float32) for n in BONES}

    # --- legs: columns around the shin centre lines
    leg_r_col = np.hypot(x - lm["rx"], (y - lm["ry"]) * 0.6) / H
    leg_l_col = np.hypot(x - lm["lx"], (y - lm["ly"]) * 0.6) / H
    in_leg_band = zn < lm["hip"] + soft
    # everything below the hip belongs to the nearer leg column; the thigh
    # blends into the body over the hip.
    is_left = leg_l_col <= leg_r_col
    thigh_w = 1.0 - _ramp(zn, lm["hip"] - soft, lm["hip"] + soft)          # 1 below hip
    ankle_w = 1.0 - _ramp(zn, lm["ankle"] - soft, lm["ankle"] + soft)      # 1 below ankle
    toes_w = 1.0 - _ramp(zn, lm["toes"] - 0.015, lm["toes"] + 0.015)      # 1 below toes
    for side, mask in (("l", is_left), ("r", ~is_left)):
        m = mask & in_leg_band
        foot = toes_w * m
        shin = (ankle_w - toes_w) * m
        leg = (thigh_w - ankle_w) * m
        W[f"foot_{side}"] += np.clip(foot, 0, 1)
        W[f"shin_{side}"] += np.clip(shin, 0, 1)
        W[f"leg_{side}"] += np.clip(leg, 0, 1)
    body_w = (1.0 - thigh_w)

    # --- neck / head / crest along z
    neck_lo = _ramp(zn, lm["neck_base"] - soft, lm["neck_base"] + soft)
    n_mid = 0.5 * (lm["neck_base"] + lm["neck_top"])
    neck_mid_lo = _ramp(zn, n_mid - soft, n_mid + soft)
    head_lo = _ramp(zn, lm["neck_top"] - soft, lm["neck_top"] + soft)
    crest_lo = _ramp(zn, lm["skull_top"] - 0.006, lm["skull_top"] + 0.006)
    # the tail fan rises through the neck band at y > 0: keep it off the neck
    tail_mask = (y > lm["tail_y0"] - 0.04 * H) & (zn > 0.36) & (zn < 0.85)
    tail_w = _ramp(y, lm["tail_y0"] - 0.08 * H, lm["tail_y0"] + 0.02 * H) * tail_mask
    upper = 1.0 - tail_w
    W["body"] += body_w * (1.0 - neck_lo) * upper
    W["neck"] += body_w * (neck_lo - neck_mid_lo) * upper
    W["neck_mid"] += body_w * (neck_mid_lo - head_lo) * upper
    W["head"] += body_w * (head_lo - crest_lo) * upper
    # crest: star quills above the skull. crest_male = the tall quills (hidden on
    # females); crest = the lid strip just above the skull top so the female
    # keeps a short tuft.
    quill = crest_lo * upper * body_w
    male_q = _ramp(zn, lm["skull_top"] + 0.008, lm["skull_top"] + 0.022)
    W["crest"] += quill * (1.0 - male_q)
    W["crest_male"] += quill * male_q
    W["tail"] += body_w * tail_w

    # --- wings: side bulges of the torso
    wing_band = (zn > 0.40) & (zn < 0.66) & (np.abs(y - lm["body_y"]) < 0.30 * H)
    wide = _ramp(np.abs(x) / H, 0.165, 0.20) * wing_band
    for side, mask in (("l", x < 0), ("r", x >= 0)):
        w = wide * mask * W["body"]
        W[f"wing_{side}"] += w
        W["body"] -= w

    # normalise, cap 4 influences
    total = sum(W.values())
    total[total <= 1e-6] = 1.0
    for n in BONES:
        W[n] /= total
    # write groups
    for g in list(body.vertex_groups):
        body.vertex_groups.remove(g)
    groups = {n: body.vertex_groups.new(name=n) for n in BONES}
    stack = np.stack([W[n] for n in BONES], axis=1)
    order = np.argsort(-stack, axis=1)[:, :4]
    for vi in range(nv):
        ws = [(float(stack[vi, k]), BONES[k]) for k in order[vi] if stack[vi, k] > 1e-4]
        tot = sum(w for w, _ in ws) or 1.0
        for w, n in ws:
            groups[n].add([vi], w / tot, "REPLACE")
    counts = {n: int((stack[:, i] > 0.5).sum()) for i, n in enumerate(BONES)}
    print("weights (verts >0.5)", counts)
    mod = body.modifiers.new("Armature", "ARMATURE")
    mod.object = arm
    body.parent = arm


def white_vcol(body):
    me = body.data
    attr = me.color_attributes.get("Color") or me.color_attributes.new(
        name="Color", type="FLOAT_COLOR", domain="CORNER")
    me.color_attributes.active_color = attr
    attr.data.foreach_set("color", np.ones(len(me.loops) * 4, dtype=np.float32))


def mesh_dict(body):
    me = body.data
    nv, nl, npoly = len(me.vertices), len(me.loops), len(me.polygons)
    co = np.empty(nv * 3, dtype=np.float32); me.vertices.foreach_get("co", co)
    loop_vi = np.empty(nl, dtype=np.int32); me.loops.foreach_get("vertex_index", loop_vi)
    uv = np.empty(nl * 2, dtype=np.float32); me.uv_layers.active.data.foreach_get("uv", uv)
    ps = np.empty(npoly, dtype=np.int32); me.polygons.foreach_get("loop_start", ps)
    pt = np.empty(npoly, dtype=np.int32); me.polygons.foreach_get("loop_total", pt)
    pn = np.empty(npoly * 3, dtype=np.float32); me.polygons.foreach_get("normal", pn)
    pc = np.empty(npoly * 3, dtype=np.float32); me.polygons.foreach_get("center", pc)
    return dict(co=co.reshape(nv, 3), loop_vi=loop_vi, uv=uv.reshape(nl, 2), p_start=ps,
                p_total=pt, p_normal=pn.reshape(npoly, 3), p_center=pc.reshape(npoly, 3))


def plant(arm, body):
    bpy.context.view_layer.update()
    bb = [body.matrix_world @ Vector(c) for c in body.bound_box]
    h = max(v.z for v in bb) - min(v.z for v in bb)
    s = TARGET_H / max(h, 1e-4)
    arm.scale = (s, s, s)
    apply_tr(arm)
    bpy.context.view_layer.update()
    bb = [body.matrix_world @ Vector(c) for c in body.bound_box]
    arm.location.z -= min(v.z for v in bb)
    arm.location.x -= 0.5 * (min(v.x for v in bb) + max(v.x for v in bb))
    arm.location.y -= 0.5 * (min(v.y for v in bb) + max(v.y for v in bb))
    apply_tr(arm)
    bpy.context.view_layer.update()
    print("planted h", round(body.dimensions.z, 3), "dim", tuple(round(c, 3) for c in body.dimensions))


def install_gaits():
    """Kept for callers; ncgb_export binds tools/chocobo_gait.py directly."""
    return None
