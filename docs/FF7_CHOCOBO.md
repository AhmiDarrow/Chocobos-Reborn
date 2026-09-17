# FF7 chocobo — size, color, anatomy (research)

Sources: Final Fantasy Wiki (Chocobo / FFVII), Cloud height from official bios,
Minecraft 1 block = 1 m. Reference art was looked at, never fed into any tool.

## Size

- Cloud Strife (FF7 / Rebirth): **173 cm**.
- Minecraft player hitbox: **1.80 m**.
- FF7 chocobos are **moderately taller than humans**, rounder, with more obvious
  feathers than later entries (wiki, *Chocobo (Final Fantasy VII)*).
- Target adult: **3.25 m** to the crest (about 1.9× Cloud, 1.8× Steve) — Ahmi asked for taller than the first 2.25, then 25 % over 2.6.
- Adult hitbox: **1.75 × 3.25** blocks. Saddle sit **2.06 m**.
- Chicobo: the adult mesh scaled to 25 / 50 / 75 % of player height (`ChocoboEntity.getAgeScale`).

Do not use a hen silhouette (stubby legs, no neck at all, skull wider than the body).

## Anatomy (FF7 in-game / race bird)

The shipped mesh is our own: a Meshy image-to-3D of the approved Chocobos Reborn
still-8 (`art/ref/gen/still8.jpg`, `tools/meshy_i23d_fresh.py`), used exactly as
delivered (mesh, UVs, painted texture) and only rigged, planted, breed-tinted and
exported by `tools/fresh_ship.py`. The saddled bird is a second Meshy copy of
`art/ref/gen/still8_saddled.jpg`. No remesh, no re-bake, no repaint, no cuboid
layers, no third-party print files.

- **Long legs**, oval body lifted off the ground.
- **Short, thick neck** — the FF7 bird carries its head close to the body;
  a long thin neck reads as a roadrunner.
- **Big rounded head**, roughly 60 % of the body width, sitting slightly
  forward. Not a hen's skull, not an ostrich pinhead.
- **Crest:** short-medium quills standing up, slight back. Males wear the
  taller crest; females the shorter one.
- **Eye:** large round **blue** iris, white sclera, hard highlight. High on the skull.
- **Beak:** short, thick, hooked down; orange-gold. Never a long straight bill.
- **Wings:** small and folded, not a spread cape.
- **Tail:** upright fan of quills.
- **Feet:** three toes, two forward / one back, pale claws, dark legs.
- Chicobo: the same bird at 25 / 50 / 75 % height.

## Colors (farm line)

Sampled from Nomura yellow, then hue-shifted. Not FFXIV desert-yellow (219,180,87).

| Color | Hex body | Tribe | Notes |
|---|---|---|---|
| Yellow | `#F4C42A` | wild | Nomura lemon-gold |
| Green | `#4CB05A` | Sprout / mountain | FF7 mountain bird |
| Blue | `#3A8FD0` | Stone / river | FF7 river bird |
| White | `#E8E4DC` | Clock echo | Mideel / race bird |
| Black | `#2C2A32` | Claw | charcoal, not pure black |
| Gold | `#E8A416` | Spindle | deep warm gold, distinct from Yellow (was pale #FFD24A) |
| Purple | `#9254D6` | Void / End | our End bird (replaces the Pink / Red dyes) |
| Flame | `#E86824` | Spark | Nether; ember belly |

Beak `#E89430`, claw `#F5E8C8`, eye `#2D6EDC`.

## Voice

Calls are synthesised, never sampled: `tools/wark_candidates.py` style G, a
three-syllable "kwe-kwe-KWEEH" (short breathy A5→B5, bright A♯5→C6→B5, then a held
D6 with a ~32 Hz tremolo falling to G5) set by hand from what the original does in
notes, timing, brightness and tremolo; wark is the same figure a fourth down, slower
and rougher. Tame birds say kweh, wild or hurt birds wark. A file labelled
"custom kweh" turned out to be the FF7 sample itself (waveform-identical) and was
pulled from the repo and the jar; do not reintroduce it or anything derived from it
(pitch-shifted, wiggled, transcribed).

## Do not ship

Official Nomura PNG, FF7 kweh/wark audio, Kraehe/Torojimas sheets.