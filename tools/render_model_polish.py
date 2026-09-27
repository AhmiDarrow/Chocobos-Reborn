"""Blender QA of OBJ snapshots exported by ModelSurfaceTest's actual Java skinner.

CR_MODEL_PREVIEWS=1 gradlew test --tests '*ModelSurfaceTest'
blender -b -P tools/render_model_polish.py
Outputs build/model-polish/*.png, including alternate views and run poses.
"""
import sys
from pathlib import Path
import bpy
import numpy as np
from PIL import Image
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from paint_albedo import PLUMAGE, recolor_plumage
import stills

OUT = ROOT / 'build/model-polish'
TEX = ROOT / 'src/main/resources/assets/chocobosreborn/textures/entity'
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
closeup = '--beaks' in args
if closeup:
    args.remove('--beaks')
regions = '--regions' in args
if regions:
    args.remove('--regions')
face_only = '--face' in args
if face_only:
    args.remove('--face')
blink = '--blink' in args
if blink:
    args.remove('--blink')

for variant in (args or ['chocobo', 'chocobo_saddled', 'chocobo_armor_iron', 'chocobo_armor_diamond']):
    shots = [('male', 'idle', 'black', 'front'),
                                    ('female', 'idle', 'purple', 'front'),
                                    ('female', 'run', 'flame', 'back'),
                                    ('male', 'idle', 'yellow', 'back')]
    if closeup:
        shots = [('male', 'idle', 'yellow', 'before'), ('male', 'idle', 'yellow', 'after')]
    if regions:
        shots = [('male', 'run', 'yellow', 'tail'), ('male', 'idle', 'yellow', 'tufts'),
                 ('male', 'idle', 'yellow', 'wing'), ('male', 'idle', 'black', 'beakdark')]
    if face_only:
        shots = [('male', 'idle', 'yellow', 'face'), ('male', 'idle', 'black', 'beakdark')]
    for sex, clip, breed, view in shots:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        obj_path = OUT / f'{variant}_{sex}_{clip}.obj'
        if closeup or regions or face_only:
            from model_polish_audit import read_mesh
            source = ROOT / ('art/polish/originals' if view == 'before' else 'src/main/resources/assets/chocobosreborn/entity')
            _, parts, clips = read_mesh(source / f'{variant}.ncgb')
            _, vertices, triangles = parts[0]
            frame = clips[clip][5]
            matrices = frame[vertices['bone']]
            transformed = np.einsum('vkij,vj->vki', matrices[:, :, :, :3], vertices['pos']) + matrices[:, :, :, 3]
            positions = np.sum(transformed * vertices['weight'][:, :, None], axis=1)
            obj_path = OUT / f'{variant}_{view}.obj'
            with obj_path.open('w') as f:
                for pos, uv in zip(positions, vertices['uv']):
                    f.write(f'v {pos[0]} {-pos[2]} {pos[1]}\nvt {uv[0]} {1-uv[1]}\n')
                for a, b, c in triangles + 1:
                    f.write(f'f {a}/{a} {b}/{b} {c}/{c}\n')
        bpy.ops.wm.obj_import(filepath=str(obj_path), forward_axis='Y', up_axis='Z')
        body = next(o for o in bpy.data.objects if o.type == 'MESH')
        mat = bpy.data.materials.new('atlas')
        mat.use_nodes = True
        body.data.materials.clear()
        body.data.materials.append(mat)
        path = TEX / variant / f'{breed}.png'
        if not path.exists():
            px = np.asarray(Image.open(TEX / variant / 'yellow.png').convert('RGBA')).astype(np.float32) / 255
            colored = recolor_plumage(px, PLUMAGE[breed])
            path = OUT / f'{variant}_{breed}.png'
            Image.fromarray(np.clip(colored * 255 + 0.5, 0, 255).astype(np.uint8)).save(path)
        if blink:
            base = np.asarray(Image.open(path).convert('RGBA')).copy()
            overlay = np.asarray(Image.open(TEX / variant / 'eyes_blink.png').convert('RGBA')).copy()
            if breed != 'yellow':
                overlay = np.clip(recolor_plumage(overlay.astype(np.float32) / 255, PLUMAGE[breed]) * 255 + .5, 0, 255).astype(np.uint8)
            use = overlay[:, :, 3] > 0
            base[use] = overlay[use]
            path = OUT / f'{variant}_{breed}_closed.png'
            Image.fromarray(base).save(path)
        node = mat.node_tree.nodes.new('ShaderNodeTexImage')
        node.image = bpy.data.images.load(str(path))
        node.interpolation = 'Closest'
        mat.node_tree.links.new(node.outputs['Color'], mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
        stills.studio((720, 880), lit=True)
        bpy.context.scene.cycles.samples = 24
        bpy.context.scene.cycles.use_denoising = False
        bpy.ops.object.camera_add()
        cam = bpy.context.object
        bpy.context.scene.camera = cam
        stills.frame_bird(cam, body)
        if closeup:
            stills.aim(cam, (0, -0.83, 1.8), (1.1, -1.95, 1.84), 65)
        if view == 'tail':
            stills.aim(cam, (0, .58, 1.15), (1.6, 2.6, 1.0), 65)
        elif view == 'tufts':
            stills.aim(cam, (0, -.38, 2.03), (1.5, -1.4, 2.18), 65)
        elif view == 'wing':
            stills.aim(cam, (.38, 0, 1.2), (2.4, .7, 1.0), 65)
        elif view == 'beakdark':
            stills.aim(cam, (0, -.83, 1.8), (1.1, -1.95, 1.84), 65)
        elif view == 'face':
            stills.aim(cam, (0, -.83, 1.8), (1.1, -1.95, 1.84), 65)
        if view == 'back':
            cam.location.x *= -1
            cam.location.y *= -1
            cam.rotation_euler = (Vector((0, 0, 1.15)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
        suffix = '_blink' if blink else ''
        stills.render(OUT / f'{variant}_{sex}_{clip}_{breed}_{view}{suffix}.png')
