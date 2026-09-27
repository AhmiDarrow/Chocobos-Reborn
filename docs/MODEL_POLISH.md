# Model polish — 2026-09-27

The shipped plain, saddled, iron and diamond birds now have local beak geometry
cleanup, a closed short female crest, and cleaner breed recoloring.

## Geometry

`tools/polish_beaks.py` uses Blender's mesh smoothing on the front of each beak.
The attachment ring and sampled eye/tack corners are pinned. Displacement is
capped at 0.006 authored metres; only outward relief is retained so convex corners
stay seated. An orientation check backs off movement that would invert a triangle.
The final pass moves 29 / 20 / 57 / 56 unique vertices
in plain / saddled / iron / diamond respectively. It patches positions and flat
normals in the four NCGB files, retaining triangle indices, UVs, vertex colors,
skin weights and animation data exactly.

The old renderer removed every triangle touching a hidden male-crest vertex.
That removed 1,605 / 2,227 / 1,683 / 2,988 faces and exposed the surface beneath.
`WhiskerMesh.parts(level, male)` now caches a continuous short-crest shape for
females at every existing detail level. It retains all faces and corrects normals
for the vertical compression. Distance thresholds and the LOD algorithm remain
unchanged.

This is local relief cleanup, not a global watertight remesh. The source meshes
include open feather strips and nonmanifold junctions. Their boundary-edge counts
are unchanged by the beak edits; the female cut no longer adds more openings.

## Color

The old `r-g < 60` seed excluded even palette yellow `(245,184,18)`. The cutoff is
now 70, which still excludes the orange beak. Two synchronous neighborhood passes
also catch muted warm texels surrounded by feathers. Blue/white/black eye pixels,
orange beak and continuous leather regions do not seed that cleanup. Java and
Python use the same rule.

`tools/polish_atlases.py` repairs unrecolored texels in the eight existing
purple/flame textures, continuing nearby authored feather accents. It also updates
the local masters if present. The five solid breeds continue to derive from yellow
once at texture load. Yellow textures themselves are unchanged.

## Reproduce and inspect

- `blender -b -P tools/polish_beaks.py` — local originals are backed up under
  `art/polish/originals`; repeated runs start from those originals. Textured Blender
  inspection meshes are saved under `art/polish`. If replacing the source art,
  archive that backup folder before polishing the replacement; otherwise the old
  originals will intentionally be reused. The original `fresh_*.blend` files are
  unchanged. Run this pass after exporting from them.
- `python tools/polish_atlases.py` — existing-atlas repair, idempotent.
- `python tools/model_polish_audit.py` — topology counts from shipped NCGBs.
- Set `CR_MODEL_PREVIEWS=1`, then run `gradlew test --tests '*ModelSurfaceTest'`.
  This writes OBJ snapshots from the actual Java skinner, including idle/run poses
  for both sexes in all outfits.
- `blender -b -P tools/render_model_polish.py` renders front/rear body previews.
  Add `-- --beaks` for close-up before/after pairs. Outputs: `build/model-polish`.
- `render_breeds.py` also fixes a preview variable collision that previously broke
  rendering of derived solid breeds.

## Validation

Nine targeted model, skinning and atlas tests pass, including Java/Python comparison
over all 20 derived 1024 atlases. Full/female meshes were checked at all three LODs.
Binary checks confirm identical rig/clip/UV data and topology, no flipped faces,
and the displacement bound for all four edited NCGBs. The JAR builds successfully.
Blender previews use shipped geometry and textures. An in-game visual verdict is
still needed; Blender does not validate Minecraft lighting or camera transitions.
Racing/latency code is outside this pass.

## Follow-up: tail root, yellow flecks and beak bleed

The owner's in-game feedback identified tail-root stretching, red/black texels on
yellow tufts/wings, and feather/eye colors on the beak. `repair_model_regions.py`
repairs these in all four NCGB variants, after the beak relief pass:

- Rear torso vertices no longer follow neck/head bones. A continuous body/tail
  blend replaces the abrupt tail mask; the lower/upper wing attachment is faded
  into the body as well. `chocobo_rig.py` removes the same hard gates from future
  source exports.
- Identified beak faces sample clean orange atlas interiors shared by the three
  shipped textures. This also prevents derived breed colors reaching the beak.
  Contaminated tuft/wing faces sample nearby clean feather texels. Eye, leather,
  metal and rare shared UV corners are protected. This intentionally changes UVs
  and weights; triangle topology, rest positions, normals and clip matrices stay.
- Backups for this stage are in `art/polish/pre-region-repair`, separate from the
  original beak backups. Repeat execution is deterministic from that stage's
  backup. When replacing source art, archive both backup stages first.
- `render_model_polish.py -- --regions` renders the tail underside in a run pose,
  yellow tufts/wings and a black-breed beak close-up. `check_model_regions.py`
  validates final asset data, protected breed-independent orange samples and all
  48 run frames without touching the shared Gradle build.

The ten targeted Java model tests passed after the weight/initial UV repair.
Later UV refinements use the standalone asset check to avoid overlapping Grok's
active Java work. Maximum measured tail-root edge stretch across the run cycle is
1.219 / 1.144 / 1.230 / 1.152 for plain / saddled / iron / diamond; the old rig had
spikes above 20. The last shared JAR predates the final UV refinements: rebuild
once Grok's network/race pass is ready. Coordination is recorded separately in
`GROK_MODEL_COORDINATION.md`; no network/race source changes belong to this pass.

## Current eye and bridge follow-up

This section supersedes the earlier topology/JAR statements for the final assets.
Shared beak UV corners are now duplicated where needed, keeping their geometric
surface intact while isolating orange from breed colors. Eyes retain their original
design with a cleaned palette. Client-only, three-tick blinks use cached textures,
staggered by entity ID; no packets or race state are involved. Four `eyes_blink.png`
overlays include detached eye UV fragments and protected edge gutters.

`fill_eye_recesses.py` runs after region repair and lifts recessed eye corners in
all outfits without deleting faces or introducing boundary edges/inversions. Its
backup is `art/polish/pre-eye-relief`. The owner's marked horizontal opening under
the plain model's forehead is filled by an enclosed, head-skinned 12-triangle
insert fitted to the asymmetric rim. Existing faces remain. Its atlas sample is
orange in all breeds and unaffected by the eyelid masks. Reproduction order:
beak relief, region repair, eye relief/bridge fill, eye atlas polish, blink masks.

`check_model_regions.py` checks retained surfaces, closed insert topology,
non-inverted geometry, unchanged clips, protected beak colors during blinks, and
all run frames. Blender renders under `build/model-polish` show the actual edited
assets; in-game lighting remains a separate visual check. No release is deployed.

Final validation: all 11 selected model/skinning/atlas/blink tests passed, including
20-atlas Java/Python parity. The standalone asset checks passed on all four
variants. The JAR was rebuilt with these final assets; no release was deployed.
No model-side Gradle task remains running.
