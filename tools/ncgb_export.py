"""NCGB export + clip baking for the chocobo (sliced 2026-09-16 from the retired
build_chocobo.py; nothing else of that STL/vox pipeline survives).

export_ncgb(name, arm, body, outdir, smooth_normals=0.0, uv_inset=0.0) writes
<outdir>/<name>.ncgb: bones, one textured part per mesh object (floor / player*
excluded), idle / walk / run clips baked from tools/chocobo_gait.py, height, width.
"""
from __future__ import annotations

import math
import struct

import bpy
from mathutils import Matrix, Vector

from chocobo_gait import idle_pose, run_pose, walk_pose

C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))
CI = C.inverted()

CLIP_FRAMES = {"idle": 120, "walk": 64, "run": 48}
CLIP_FPS = {"idle": 24.0, "walk": 30.0, "run": 40.0}


def animate(arm, clip):
    sc = bpy.context.scene
    for act in list(bpy.data.actions):
        bpy.data.actions.remove(act)
    if arm.animation_data:
        arm.animation_data_clear()
    arm.animation_data_create()
    n = CLIP_FRAMES.get(clip, 8)
    pose_fn = idle_pose if clip == "idle" else walk_pose if clip == "walk" else run_pose if clip == "run" else lambda _t: {"rot": {}, "loc": {}}
    sc.frame_start, sc.frame_end = 1, n
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode="POSE")
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    action = bpy.data.actions.new(name=clip)
    arm.animation_data.action = action
    if hasattr(arm.animation_data, "action_slot"):
        pass
    for f in range(1, n + 1):
        t = (f - 1) / n
        pose = pose_fn(t)
        rots = pose.get("rot", pose)
        locs = pose.get("loc", {})
        for pb in arm.pose.bones:
            pb.rotation_euler = rots.get(pb.name, (0.0, 0.0, 0.0))
            pb.location = Vector(locs.get(pb.name, (0.0, 0.0, 0.0)))
            pb.keyframe_insert("rotation_euler", frame=f)
            pb.keyframe_insert("location", frame=f)
    bpy.ops.object.mode_set(mode="OBJECT")
    sc.frame_start, sc.frame_end = 1, n


def srgb(c):
    return tuple(int(255 * max(0, min(1, (v * 12.92 if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055)))) for v in c[:3])


def material_kind(m):
    if m is None or not getattr(m, "use_nodes", False):
        return "plain", (0.5, 0.5, 0.5), 0.0
    p = next((n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if p is None:
        return "plain", (1.0, 1.0, 1.0), 0.0
    c = p.inputs["Base Color"].default_value
    e = p.inputs["Emission Strength"].default_value
    ec = p.inputs["Emission Color"].default_value
    return "plain", (c[0], c[1], c[2]), (e, (ec[0], ec[1], ec[2]))


def export_ncgb(name, arm, body, outdir, smooth_normals=0.0, uv_inset=0.0):
    arm.data.pose_position = "REST"
    bpy.context.view_layer.update()
    objs = [
        o for o in bpy.data.objects
        if o.type == "MESH" and o.name != "floor" and not o.name.startswith("player")
    ]
    rest_world = {o.name: o.matrix_world.copy() for o in objs}
    bones = [bn.name for bn in arm.data.bones]
    bone_index = {n: i for i, n in enumerate(bones)}
    bone_parent = [bone_index.get(arm.data.bones[n].parent.name, -1) if arm.data.bones[n].parent else -1 for n in bones]
    rest_bone = {n: arm.matrix_world @ arm.data.bones[n].matrix_local for n in bones}
    rigid = [o for o in objs if not any(m.type == "ARMATURE" for m in o.modifiers)]
    for o in rigid:
        bone_index[o.name] = len(bones)
        bones.append(o.name)
        bone_parent.append(-1)

    dg = bpy.context.evaluated_depsgraph_get()
    parts = []
    for o in objs:
        ev = o.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        try:
            me.calc_normals_split()
        except Exception:
            pass
        M = rest_world[o.name]
        Mn = M.to_3x3().inverted().transposed()
        # Voxel shells shade as salt-and-pepper (every stair-step face lit differently).
        # smooth_normals > 0: replace each vertex's normal with the area-weighted
        # mean of face normals within that radius (object units) so the cubes stay
        # but the light rolls over the bird like a rounded body.
        vsmooth = None
        if smooth_normals > 0:
            import numpy as _np
            from scipy.spatial import cKDTree as _KD
            npoly = len(me.polygons)
            pc = _np.empty(npoly * 3, dtype=_np.float32); me.polygons.foreach_get("center", pc); pc = pc.reshape(-1, 3)
            pn = _np.empty(npoly * 3, dtype=_np.float32); me.polygons.foreach_get("normal", pn); pn = pn.reshape(-1, 3)
            pa = _np.empty(npoly, dtype=_np.float32); me.polygons.foreach_get("area", pa)
            nv = len(me.vertices)
            vc = _np.empty(nv * 3, dtype=_np.float32); me.vertices.foreach_get("co", vc); vc = vc.reshape(-1, 3)
            tree = _KD(pc)
            vsmooth = _np.zeros((nv, 3), dtype=_np.float32)
            for vi, hits in enumerate(tree.query_ball_point(vc, smooth_normals)):
                if hits:
                    acc = (pn[hits] * pa[hits][:, None]).sum(axis=0)
                    ln = float(_np.linalg.norm(acc))
                    if ln > 1e-6:
                        vsmooth[vi] = acc / ln
            print("smoothed normals r=%.3f over %d verts" % (smooth_normals, nv))
        skinned = any(m.type == "ARMATURE" for m in o.modifiers)
        groups = {g.index: g.name for g in o.vertex_groups} if skinned else {}
        kind, col, em = material_kind(o.data.materials[0] if o.data.materials else None)
        verts, tris, key = [], [], {}
        uv_layer = me.uv_layers.active if me.uv_layers else None
        color_attr = None
        if me.color_attributes:
            color_attr = me.color_attributes.active_color or me.color_attributes[0]

        # UV inset: pull every loop's UV toward its polygon's UV centroid by
        # uv_inset texels (atlas of 2048), so Closest sampling at the island edge
        # never reads the padded gap / a neighbouring island (yellow in pupils,
        # dark flecks on plumage).
        poly_uv_centre = {}
        if uv_inset > 0 and uv_layer is not None:
            for poly in me.polygons:
                us = [uv_layer.data[l].uv for l in poly.loop_indices]
                poly_uv_centre[poly.index] = (sum(u.x for u in us) / len(us), sum(u.y for u in us) / len(us))
        loop_poly = {}
        if uv_inset > 0:
            for poly in me.polygons:
                for l in poly.loop_indices:
                    loop_poly[l] = poly.index

        def vkey(vi, li, pcol, pemit):
            p = C @ (M @ me.vertices[vi].co)
            if vsmooth is not None and float(vsmooth[vi].any()):
                n = (C.to_3x3() @ (Mn @ Vector(vsmooth[vi].tolist()))).normalized()
            else:
                n = (C.to_3x3() @ (Mn @ me.loops[li].normal)).normalized()
            if uv_layer is not None:
                uv = uv_layer.data[li].uv
                uu, vv = float(uv.x), float(uv.y)
                if uv_inset > 0 and li in loop_poly:
                    cx, cy = poly_uv_centre[loop_poly[li]]
                    dx, dy = cx - uu, cy - vv
                    d = (dx * dx + dy * dy) ** 0.5
                    step = uv_inset / 2048.0
                    if d > step:
                        uu += dx / d * step
                        vv += dy / d * step
                    else:
                        uu, vv = cx, cy
            else:
                uu, vv = 0.0, 0.0
            if skinned:
                ws = sorted(
                    ((g.weight, bone_index.get(groups.get(g.group, ""), -1))
                     for g in me.vertices[vi].groups if bone_index.get(groups.get(g.group, ""), -1) >= 0),
                    reverse=True,
                )[:4]
                tot = sum(w for w, _ in ws) or 1.0
                ws = [(w / tot, bi) for w, bi in ws]
            else:
                ws = [(1.0, bone_index.get(o.name, 0))]
            while len(ws) < 4:
                ws.append((0.0, 0))
            k = (round(p.x, 4), round(p.y, 4), round(p.z, 4), round(n.x, 3), round(n.y, 3), round(n.z, 3),
                 round(uu, 4), round(vv, 4), pcol, pemit)
            if k not in key:
                key[k] = len(verts)
                verts.append((p, n, pcol, pemit, ws, (uu, vv)))
            return key[k]

        mat_cache = {}
        for i, m in enumerate(o.data.materials):
            mat_cache[i] = material_kind(m)
        for tri in me.loop_triangles:
            mi = tri.material_index
            kind, col, em = mat_cache.get(mi, ("plain", (0.7, 0.5, 0.1), (0, (0, 0, 0))))
            if kind == "plain":
                c = srgb(col)
                if isinstance(em, tuple) and em[0] > 0:
                    e = srgb(tuple(min(1, v * min(1, em[0] / 2)) for v in em[1]))
                else:
                    e = (0, 0, 0)
            else:
                c, e = (180, 140, 40), (0, 0, 0)

            def tri_vert(i, c=c, e=e):
                li = tri.loops[i]
                if color_attr is not None:
                    col4 = color_attr.data[li].color
                    c = srgb((float(col4[0]), float(col4[1]), float(col4[2])))
                return vkey(tri.vertices[i], li, c, e)

            tris.append(tuple(tri_vert(i) for i in range(3)))
        parts.append((o.name, verts, tris, True))
        ev.to_mesh_clear()

    arm.data.pose_position = "POSE"
    clips = []
    for kind in ("idle", "walk", "run"):
        animate(arm, kind)
        sc = bpy.context.scene
        n = sc.frame_end
        frames = []
        for f in range(1, n + 1):
            sc.frame_set(f)
            dg = bpy.context.evaluated_depsgraph_get()
            row = []
            for bn in arm.data.bones:
                pb = arm.pose.bones[bn.name]
                D = C @ (arm.matrix_world @ pb.matrix @ rest_bone[bn.name].inverted() @ arm.matrix_world.inverted()) @ CI
                row.append(D)
            for o in rigid:
                D = C @ (o.evaluated_get(dg).matrix_world @ rest_world[o.name].inverted()) @ CI
                row.append(D)
            frames.append(row)
        clips.append((kind, CLIP_FPS.get(kind, 30.0), frames))
        print(" clip", kind, n, "frames")

    xs = [v[0] for _, vs, _, _ in parts for v in vs]
    height = max(v.y for v in xs)
    width = max(max(abs(v.x), abs(v.z)) for v in xs) * 2
    outdir.mkdir(parents=True, exist_ok=True)
    out = outdir / f"{name}.ncgb"
    with open(out, "wb") as fh:
        w = fh.write

        def s(x):
            bb = x.encode()
            w(struct.pack("<H", len(bb)))
            w(bb)

        w(b"NCGB")
        w(struct.pack("<I", 1))
        w(struct.pack("<I", len(bones)))
        for i, bn in enumerate(bones):
            s(bn)
            w(struct.pack("<i", bone_parent[i]))
        w(struct.pack("<I", len(parts)))
        nv = nt = 0
        for pname, verts, tris, tex in parts:
            s(pname)
            w(struct.pack("<BI", 1 if tex else 0, len(verts)))
            for p, n, c, e, ws, uv in verts:
                w(struct.pack("<8f", p.x, p.y, p.z, n.x, n.y, n.z, uv[0], 1.0 - uv[1]))
                w(bytes(c))
                w(bytes(e))
                w(struct.pack("<4H", *[bi for _, bi in ws]))
                w(struct.pack("<4f", *[wt for wt, _ in ws]))
            w(struct.pack("<I", len(tris)))
            for t in tris:
                w(struct.pack("<3I", *t))
            nv += len(verts)
            nt += len(tris)
        w(struct.pack("<I", len(clips)))
        for cname, fps, frames in clips:
            s(cname)
            w(struct.pack("<fI", fps, len(frames)))
            for row in frames:
                for D in row:
                    w(struct.pack("<12f", *[D[r][c] for r in range(3) for c in range(4)]))
        w(struct.pack("<2f", height, width))
    print(f"WROTE {out} bones={len(bones)} parts={len(parts)} verts={nv} tris={nt} h={height:.2f} w={width:.2f} {out.stat().st_size}B")
    return height, width
