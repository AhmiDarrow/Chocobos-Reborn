# Chocobos Reborn — session handoff (internal)

Date: 2026-09-16 (evening). Repo: `C:\Users\Administrator\Projects\chocobos-reborn`
(no git; this file is the state). Mod id `chocobosreborn`, package
`tk.darrow.chocobosreborn`, jar `chocobosreborn-1.0.0.jar`, NeoForge 1.21.1 / 21.1.249.
Next agent: read this, then `docs/SPEC.md`, then `.grok/skills/chocobo-art/SKILL.md`.
The old `Projects/whiskerwind` folder is deleted (art moved here first).

## The bird (approved by Ahmi in-game 2026-09-16: "looks great")

`art/meshy/fresh/fresh.glb` = Meshy image-to-3D of the approved still-8
(`art/ref/gen/still8.jpg`, our own render; `tools/meshy_i23d_fresh.py`, latest
model, ~30k tris, textured). `tools/fresh_ship.py` ships it **as delivered**:
orient -Y forward, flat shade, rig (`tools/chocobo_rig.py`: 18 game bones,
position landmarks, soft-joint weights, plant 2.25 m), white vcol, eight breed
atlases (`paint_albedo.recolor_plumage` on `isPlumage` texels, then `pad_atlases`),
14 stills (`tools/stills.py`), NCGB with idle 120 / walk 64 / run 48
(`tools/ncgb_export.py` + `tools/chocobo_gait.py`). No remesh, no re-bake, no
repaint. 31,164 tris, `chocobo.ncgb` 6.4 MB.

Commands (repo root; Blender at `C:\Users\Administrator\scoop\apps\blender\current\blender.exe`,
its bundled Python needs `scipy` + `pillow`, installed here):

```
python tools/meshy_i23d_fresh.py                              # only for a new base mesh
blender --background --python tools/fresh_ship.py             # plain bird: rig, atlases, stills, NCGB
blender --background --python tools/fresh_ship.py -- --tag saddled   # saddled bird (second mesh)
python tools/preview_qa.py                                    # QA PASS all 14 stills
blender --background --python tools/render_gait_strip.py -- --clip run
./gradlew test && ./gradlew runVerification && ./gradlew build
```

Last run here: QA PASS, 76 JUnit, all 10 GameTests, jar built. Gold breed =
(232,164,22) (Ahmi: "gold needs to look more gold"); egg colour + doc table match.

**Saddled variant (2026-09-16 18:20; Ahmi hated a hand-built saddle and asked for
"the same process": a Meshy copy of his saddled still).** `art/ref/gen/still8_saddled.jpg`
(his own Grok render: bridle with cheek straps, reins, red-seat saddle, grey
buckles) -> `python tools/meshy_i23d_fresh.py --tag saddled --image
art/ref/gen/still8_saddled.jpg` -> `art/meshy/fresh/saddled.glb` ->
`blender --background --python tools/fresh_ship.py -- --tag saddled` -> mesh
`chocobo_saddled.ncgb`, atlases `textures/entity/chocobo_saddled/`, stills
`meshy_saddled_*.png`, blend `art/fresh_chocobo_saddled.blend`. The tack is baked
into that mesh, so `ChocoboMeshRenderer` picks `chocobo_saddled` + its atlases when
`e.saddled()` (falls back to `chocobo` if the mesh is missing); the `saddle` /
`bridle` bones stay in both rigs with no geometry. Leather is not `isPlumage`, so
breed tints leave it alone. Hand-built primitive tack (boxes/rings on the bones)
was tried first and rejected ("nope hate it"); that code is gone.

Files: `art/fresh_chocobo.blend` + `art/fresh_chocobo_saddled.blend` (rigged),
`art/preview/meshy_char_*.png` + `meshy_saddled_*.png` (stills), `gait_run*.png`,
`src/.../entity/{chocobo,chocobo_saddled}.ncgb`,
`textures/entity/{chocobo,chocobo_saddled}/{8 breeds}.png`. `art/backup_sessionstart/` = the bird
from before today's work (byte-exact, from the modpack jar copy); delete when sure.

### What was tried today and rejected (do not retry unasked)

The previous bird was a block-remeshed, re-baked, repainted copy of a Meshy mesh.
Six eye reworks on it (grown paint disc, apex rings, two-tone, flattened eye wall,
decal parts on a cheek-level wall, decal parts on a flush pad) each looked worse
in-game to Ahmi. A low-poly parametric bird and two Meshy text-to-3D birds
("that's an ostrich", "all terrible") were rejected too. Lesson: Blender stills
are not the gate, the client is; change one thing, then ask. Everything from
those pipelines (`merge_b_*`, `paint_mesh`, `build_chocobo`, `voxel_chocobo`,
`meshy_ship`, `meshy_character_ship`, `reproportion`, `build_ff7_chocobo`,
`build_lowpoly_chocobo`, `meshy_t23d_lowpoly`, old blends, old Meshy glbs, the
STL) was deleted in the 2026-09-16 cleanup.

## Rules pass (2026-09-16 18:30, Ahmi: "polish breeding, greens, food; colours need race wins per FF7; colours must have the right abilities")

Superseded on abilities by the colour roster change below (Pink / Red gone, Gold flies again, Purple added). Breeding-win and greens rules in this pass still stand.

* **Colour abilities = FF7 terrain** (`ChocoboColor`): Green climbs (mountains), Blue
  walks on shallow water (rivers, <= 3 blocks deep, `ChocoboEntity.waterIsShallow`)
  + rider water breathing, Black both + night vision. (Gold / Pink / Red flying and
  "no chocobo flies" were the 18:30 snapshot; see roster change.)
* **Colour breeding needs racers** (`BreedingOdds.chance`, `ChocoboEntity.minWinsEach`):
  each parent needs 1 first-place finish for Green/Blue, 2 each for Black, 3 each
  for Gold, else no colour roll; chance 25 % at the minimum -> 100 % at the combined
  guarantee (4 / 9 / 12). Feeding a Carob / Zeio to a bird short of wins shows
  `chocobosreborn.nut.needs_wins`. Test `colourBreedingNeedsWinsOnBothParents`.
* **Greens / food**: every feed heals 3, +20 stamina, grows a chick (`ageUp`), and
  shows `chocobosreborn.greens.fed` (totals + feeds left); satiety recovers one feed
  of each green per day (`aiStep`, 24000 ticks). Tooltips for Carob / Zeio state
  the win requirements. README + `docs/SPEC.md` updated.
* **Docs incident**: at 18:21 another session (branding / CurseForge work for the
  ninjacat-skies pack) wrote `art/branding/`, `docs/public/` (icons + store
  description) and overwrote `src/main/resources/icon.png` (256x256, kept), and
  `docs/SPEC.md` + `docs/FF7_CHOCOBO.md` vanished at the same time. FF7 doc
  restored from this session's transcript; SPEC rebuilt from code + README
  (`art/recovered/` holds the raw recovered text). Leave `docs/public/` to that
  session.

## Colour roster change (2026-09-16 18:40, Ahmi: "Gold has all abilities and can fly. Remove pink and red. Add a purple End chocobo")

* `ChocoboColor` is now YELLOW, GREEN, BLUE, WHITE, BLACK, GOLD, PURPLE(6), FLAME(7)
  (id = ordinal; old worlds with Pink/Red ids 6/7 read back as Purple/Flame).
* **Gold**: climb + any water + fly + fire immune + rider water breathing + night
  vision (the flap / glide code in `travel` is live for Gold only).
* **Purple** (End bird): spawns wild on end stone in end_highlands / midlands /
  small_end_islands / end_barrens (`chocobo_end` biome modifier, `checkSpawn`,
  `finalizeSpawn`); climbs, any water, rider slow falling, no flight (Ahmi: "flying is for gold only")
  (`riderSlowFalling`). Breeds by inheritance only. Egg `purple_chocobo_spawn_egg`
  0x9254D6, plumage (146,84,214).
* **Removed**: Pink, Red, both saucer dyes (items, recipes, models, textures, icon
  prompts, `write_datapack` recipes), the Fair's dye lines -> the Fair now sells
  Sage's Notes 3 GP, 4 firework rockets 2 GP, a lead 3 GP (gametest
  `shopCatalogResolvesAndKeepersHaveNames` needs every shop role to have lines).
* Atlases regenerated for both meshes (8 breeds each); `preview_qa` now expects 14
  stills. README / SPEC / FF7 doc updated.

## Bug sweep (2026-09-16 19:20, five parallel reviewers over every file; all confirmed items fixed, gates green)

Game-breaking, fixed:
* No `BreedGoal` was registered, so nut-fed birds fell in love and never mated in play
  (only the gametests, which call `getBreedOffspring` directly, ever hatched a chick).
* `global_loot_modifiers.json` lived under `data/chocobosreborn/`; NeoForge reads only
  `data/neoforge/loot_modifiers/global_loot_modifiers.json`, so seeds / Carob / Zeio
  never dropped. Moved (and `write_datapack.py` now writes it there).
* `waterIsShallow` probed from the bird's own block (air when standing on water) so
  every river bird crossed the ocean; probes from the block below now.
* Rider jump: impulse lived only in the server-side `handleStartJump`; now recorded in
  `onPlayerJump` (client) + `handleStartJump` (server) and applied in `travel()`.
  Flight and flap are blocked during a heat (`RaceScoring.mayFlyDuringRace`).
* Grade multiplier was applied twice to ridden speed (`speedMul` x `mountedCruise`);
  `mountedCruise` now gets `racing()` so water sections in heats are not double-scaled.
* `grade()` now includes training (`gradeFromTraining`, +1 step per 60 points);
  `bornGrade()` is the rolled grade.
* Race: Jolo is now spawned (i == 2, Gold, 1.08x) and the JOE bet settles on him, not on
  Teiyo; `Racer.progress` is set in the constructor (was null -> NPE if a stall chunk
  was not entity-loaded); stalls use the original field size; prizes only on a completed
  course and GP prizes split into 64-stacks; a forfeit / abort refunds the stake
  (`bet.refunded`); countdown shows 5..1; a dismount during the HOLD remounts instead of
  forfeiting; a command-teleported rider is released (`RaceManager.RELEASING`) and the
  mount veto ignores players changing dimension.
* Betting before a heat: Rook now takes a pick + stake with no live session (player
  persistent data `chocobosreborn_pending_bet/_stake`), consumed by the next
  `RaceSession`. Previously the rider was locked in the stall before Rook could be reached.
* Zeio at 120 GP was unbuyable (one 64-stack cost slot); offers over 64 use both slots.
* Crop models get `render_type: cutout` (were drawing black squares).
* `RaceMusic` restarts if the engine dropped the loop (volume 0 / F3+T) and stops the
  vanilla music manager first.
Smaller: `needs_wins` hint uses `minWinsEach`; Wonderful rolls need an actual snow/ice
pad; no wild spawns inside the Square; chick owner falls back to the mate; Gysahl heal
is server-only; appetite decay keyed on game time; stale `tradingPlayer` cleared; orphan
spawn-egg PNGs and two dead lang keys removed; generators write LF.
Known and left: `hideBone(saddle/bridle)` in the renderer is dead (tack is a second
mesh); ~15 `RaceScoring` helpers are tested but unused (`finishGraceExpired` says 40
ticks, the session uses 600); `RaceMusic` always picks course 0's track; NPC lane
offsets favour inner lanes; spawn egg `use()` on water / dispensers spawns Yellow.

## Racer AI (2026-09-16 19:30, Ahmi: "make sure the racer AI is high quality, C/B/A/S difficulty matches")

`race/RacerProfile` (pure record, unit-tested in `RacerProfileTest`: every axis gets
harder C -> S) drives `race/RacerGoal`:
* pace: cruise 1.15 / 1.32 / 1.48 / 1.62 x movement speed (+/- 6 % per field bird),
  dash 1.20 / 1.25 / 1.30 / 1.35 on an energy budget (drain 1/110 -> 1/200 per tick,
  recover 1/420 -> 1/260); C burns it whenever above 10 %, B/A/S dash on the straights
  (|sin 2pi t| > 0.5) and keep 20 / 35 / 45 % in reserve for the last lap or to answer a
  player breakaway (`wantsDash`).
* start reaction 8-22 / 4-12 / 2-6 / 0-3 ticks; lane wobble 0.9 / 0.6 / 0.35 / 0.15
  blocks; stumbles (0.55x for 20 ticks) 1.0 / 0.5 / 0.2 / 0.05 per lap.
* racing line: every bird blends from its stall lane onto the inside line (+1 block,
  per-bird spread +/-0.6) over the first 140 ticks, weighted by `lineHold`
  (0.35 -> 0.95); a slower racing bird within 3 % of a lap ahead triggers a 40-tick
  swing 2.2 blocks outward to pass (`getEntitiesOfClass` every 5 ticks).
* rubber band vs the player (`bandFactor`, gap in laps from `RaceSession`):
  +/-10 % at C, 6 % B, 3 % A, none at S. Teiyo (1.12x) and Jolo (1.08x) drive at S
  discipline in every class with no band.
* finished birds coast at 0.6x (`lapsDone >= totalLaps`). `RaceSession` feeds
  `playerGap` / `lapsDone` each tick (`RaceLapProgress.lastProgress()`).
Player reference: a bird cruises at attribute x grade, dashes at 1.62x on its own
stamina; a Good Yellow with half-time dashing averages ~1.31x vs the C field's ~1.19x,
a Wonderful Gold ~1.55x vs the S field's ~1.72x cruise-plus-dash, so S needs a trained
bird and full stamina management.

## Gait sampling fix (2026-09-16 19:55, Ahmi: "run animations get very flickery or overly sped up")

`ChocoboMeshRenderer.sampleClip` mapped the walk-animation position straight to
frames (`stride * 0.85 * fps`): vanilla advances that position by up to 1 unit per
tick, so a dash stepped the 48-frame run clip ~34 frames per tick (a strobe), and
walk / run wrapped at different rates so the crossfade blended unrelated phases.
Now `frame = (stride / STRIDE_PER_CYCLE) * frames` with `STRIDE_PER_CYCLE = 12` for
both gait clips (one stride ~0.6 s at a full dash, ~2 s strolling); idle is still
time-based. Tune the constant if the feet slide (too high) or blur (too low).

## Seat, gates, Gold flight (2026-09-16 20:00)

* **Seat** (Ahmi: "player sits slightly above saddle"): measured on
  `art/fresh_chocobo_saddled.blend`, the seat top is z 1.37 of 2.25 at mesh y +0.25
  -> 1.98 m / 0.36 m behind the centre at ADULT_H. `SEAT_H = 1.92F` (a hair into the
  seat), `SEAT_BACK = 0.36F`, attachment rotated by the bird's yaw and scaled by
  `getAgeScale()` (vanilla: rider origin = attachment - 0.6 VEHICLE offset).
* **Gates** (Ahmi: "cannot click anything when seated"): `ChocoboEntity.aiStep` scans
  the ridden bird's box (+0.6 horizontally) every 5 ticks while moving and calls
  `SquareGateBlock.rideThrough` (same switch as a click), 100-tick cooldown on the
  bird. Clicking still works; riding into the gate is the intended way.
* **Gold flight** (Ahmi: "gold fly speed should be higher"): `airSpeed` 0.30 -> 0.80;
  while airborne a flier's ridden cruise is scaled by air/land (1.6x for Gold);
  flap lift 0.12 / climb 0.07 flat (no longer tied to airSpeed). Blocked in heats.
Gates: 83 JUnit, 10 GameTests, build.

## Almanac, pocketwatch, flight, click-through, Nether/End birds (2026-09-16 20:30)

Ahmi's batch: "sage notes do not work -> make it the mod book with a tames page";
"gate still does not work -> a chocobo pocketwatch to Whiskerwind and back"; "gold
fly speed still very slow, remove slow fall, gold slow health regen"; "Flame -> red
Nether Chocobo with lava highlights, Purple -> End Chocobo with end stone accents";
"cannot click anything on a chocobo"; "chocobos need a down movement".

* **Chocobo Almanac** (`item/ChocoboAlmanacItem`, book + gysahl; Fair 3 GP): use ->
  server refreshes the ledger from loaded owned birds within 128 blocks, sends
  `net/AlmanacPayload` (CompoundTag of `BirdRecord`s) -> `client/AlmanacScreen`:
  chapters from lang `chocobosreborn.almanac.<chapter>.title/.body` (overview,
  taming, greens, nuts, colors, riding, square, farm, items) + "My Chocobos" (per-bird
  page: genes, training, racing, born day, parents + nut, children, breeding hint).
  **Ledger** (`ledger/ChocoboLedger`, SavedData on the overworld, `chocobosreborn_ledger`):
  written on tame, hatch (parents + nut), every entity save and death, so birds in
  unloaded chunks still show. Payload registered in `ChocobosReborn.payloads`
  (`RegisterPayloadHandlersEvent`, version "1"); the client installs
  `AlmanacPayload.CLIENT_OPENER` so common code never touches client classes.
  Sage's Notes item, recipe, texture, shop lines and lang removed.
* **Chocobo Pocketwatch** (`item/ChocoboPocketwatchItem`, gold nuggets + clock + gysahl;
  Fair 12 GP; 3 s cooldown): in the Square -> `leaveSquare` (refused mid-heat);
  riding an owned saddled bird -> `enterSquare` with the bird; on foot ->
  `enterSquareOnFoot` (player only). The Square Gate block stays for the Square's own
  course/return gates (SquareBuilder) but has no recipe now.
* **Flight**: `getFlyingSpeed()` override - airborne mobs use the 0.02 "flyingSpeed"
  instead of the movement attribute, which is why Gold crawled in the air; now
  `getSpeed() * 0.20 * air/land` (Gold air 0.80 -> ~1.5x ground pace). `travel()`:
  +0.07/tick hold (gravity nearly cancelled), look up + forward +0.16 climb, sneak
  -0.06 dive, flap +0.10; the old -0.15 glide clamp is gone. Gold regen 1 HP / 2 s.
* **Down control**: `descending` = rider sneaking (set in `travel`); fliers dive, water
  birds sink (`canStandOnFluid` false while descending, -0.05/tick in water).
  `RaceManager.onMount` cancels a controlling rider's dismount unless the bird is on
  solid ground (not airborne / in / over water); teleports go through `RELEASING`
  (`Square.teleportMounted` wraps its `stopRiding`).
* **Click-through**: `ChocoboEntity.isPickable()` is false for the local driver unless
  they sneak (`LOCAL_RIDER` predicate installed by the client mod), so the crosshair
  reaches blocks/entities from the saddle; sneak-click still targets the bird.
* **Nether / End**: FLAME displays as "Nether" (enum id unchanged), plumage red
  (190,42,30) with lava (255,150,24) / char (58,22,20) mottling; PURPLE displays as
  "End", end stone (221,223,165) / void (70,30,120) flecks
  (`paint_albedo.ACCENTS` + `accent_plumage`: hashed 9x9 texel patches, bright ones on the lighter half of the plumage, dark ones in the shadows, edges broken by a finer hash; applied by
  `stills.write_breed_atlases` and the breed stills). Egg colours updated.

## Whiskerwind rebuild, duels, trading, equipment, almanac polish (2026-09-16 21:00-22:00)

Ahmi's batch, all in: almanac with pictures / how-tos / rename; horse-style equipment
(armour on the vanilla tiers + saddlebags, armoured birds cannot race); Farmhand
teleports on foot and birds follow; Whiskerwind is a sky-island village in the void;
courses much longer (drags >= 60 s, oval laps >= 2 min, longer up the classes), 6 per
class, terrain that gets crazier with class (water / ridge shortcuts for the breeds
that excel, slower road around); 6 racers; a 1v1 duel NPC with side bets and a course
picker; a bird-trade NPC; Gold dive on left ctrl as fast as climbing.

* **Dimension**: `dimension/square.json` is now a void flat (no layers).
  `SquareBuilder.buildIsland` lays a tapered rock island under the village
  (PADDOCK_VERSION 3 rebuilds old villages). Every course is its own ring island
  built once at `RaceTrack.centerX/Z()` (class rows z 700 + 900/class, course columns
  x = (course-2.5)*820); `SquareData` keeps a set of built tracks.
* **Tracks** (`RaceTrack`, 24): per class courses 0-2 drags (1 lap) and 3-5 ovals
  (3 laps); radii sized so a 9 b/s bird needs >= 60 s per drag and >= 120 s per oval
  lap (C) up to ~180 s (S). `Feature(type, start, end)`: WATER (two deep, walled) or
  RIDGE (3/3/4/5 blocks by class) replaces the road band; a detour road is laid at
  offsets -8..-15 (outward) with connectors. `RaceCourseLayout` stamps along the
  parameter (not a bounding-box scan) and records `chunks()`; `RaceSession` force-loads
  them for the heat so the field keeps running far from the riders, and holds direct
  entity references (`Racer.ref`) because level lookups go blind while the far island's
  chunks load. Terrain is physical now: `applyTerrain` and the AI terrain fudge are
  gone; `RacerGoal` picks the detour lane early when the bird does not `suits()` the
  feature. `RaceTrackTest` pins lengths, feature counts, island spacing and the layout.
* **Course picker**: Esther (in the Square, on a saddled bird) sends
  `RacePayloads.OpenCourseSelect(classId, mode)` -> `client/CourseSelectScreen` ->
  `CourseChoice(track, mode, stake)` -> `RaceManager.onCourseChosen`. Ranked heats run
  only on the bird's class courses (`race.wrong_class`); a course is busy only while a
  session runs on it (`trackBusy`), so different courses race at once.
* **Duels**: `race/DuelDesk` (Sable, `TownRole.DUEL`, post (-8.5,65,-74.5)). Poster
  picks course + stake (0/4/8/16/32 GP each, taken up front); the next rider on a
  saddled bird who clicks Sable accepts (stake taken) -> `RaceManager.startDuel` ->
  `RaceSession` with two humans + 4 pace birds, unranked, no prizes; winner takes the
  pot, no result refunds both; sneak-click withdraws; expiry 5 min / poster leaves.
  `RaceSession` is now multi-human (`Racer.player`, `humans()`, per-human forfeit /
  finish, `hasPlayer`).
* **Trading**: `race/TradeDesk` (Pell, `TownRole.BROKER`, post (8.5,65,-74.5)): ride an
  owned bird up and click to offer it (sneak-click = gift); a second rider's offer
  swaps owners; a player on foot claims a gift. Ledger updated.
* **Farmhand / Esther overworld**: on foot -> `enterSquareOnFoot`; riding ->
  `enterSquare`; both call `RaceManager.bringBirds` (owned, awake, not ridden birds
  within 16 blocks teleport along); `leaveSquare` brings them back too.
* **Flight controls**: airborne flier: sprint key = dive (-0.16/tick, same as climb),
  no dash in the air; sneak still submerges water birds.
* **Equipment**: `ChocoboEntity` implements `HasCustomInventoryScreen` +
  `ContainerListener`; `SimpleContainer` of 3 + 15 (saddle, armour, saddlebags, bags),
  saved as `Equipment` (old `Saddled` flag migrates into the slot). `syncEquipment`
  drives `DATA_SADDLED`, `DATA_ARMOR` (tier ordinal) and `DATA_BAGS` + the ARMOR
  attribute. Right-click with saddle / armour / bags equips; sneak + empty hand (or the
  inventory key while riding) opens `menu/ChocoboInventoryMenu` (`ModMenus.CHOCOBO`,
  `client/ChocoboInventoryScreen`, drawn panel, bags name from the item's custom name).
  Bags come off only when empty; everything drops on death. `item/ChocoboArmorItem`
  tiers leather 3 / iron 5 / gold 7 / diamond 11 / netherite 13 (recipes: H of the
  material; netherite = diamond + ingot), `SaddlebagsItem` (8 leather round a chest).
  **No racing in armour** (`race.no_armor` in startRace, onCourseChosen, startDuel,
  Sable). Renderer: `meshId()` picks `chocobo_armor_<iron|gold|diamond>` (Meshy copies of
  Ahmi's three armour stills, `art/ref/gen/still8_armor_*.jpg`, shipped by
  `fresh_ship.py -- --tag armor_<tier>`; leather has no mesh, netherite uses diamond's),
  else saddled, else plain. (Gold armour was dropped an hour later: its plating is
  yellow and bled the breed colour.)
* **Almanac v2** (`client/AlmanacScreen`): lang bodies use markup ([h], [step],
  [item:id], [birds], [diagram:breeding]); pages are block lists (headings, item
  lines with rendered icons, a live 8-breed row via `InventoryScreen.renderEntityInInventory`
  on client-side preview entities, the farm-line diagram); bird pages show a live
  saddled preview, genes, racing record, training bars, family line, breeding hint and a
  rename box (`RacePayloads.RenameBird` -> `ChocoboLedger.rename`; a rename of an
  unloaded bird waits in `BirdRecord.pendingName` and applies on next load).

## Kart courses, grandstands, boost pads, saddle combat, End / Nether feathers (2026-09-16 22:00-23:30)

Ahmi: "move the stands to the middle of the race track, remake Whiskerwind, make it
awesome"; "racetracks need a lot more work, review Mario Kart, our courses should have a
similar style"; "player needs to be able to fight on a chocobo"; "end chocobo should
have plumage of endstone texture, same for the nether but with lava" -> then only the
show feathers (tail fan, neck ruff, head crest), body pure purple / red ("perfect",
rebake across saddled / armour); "gold armor color bled through, drop the gold armor
and only use iron and diamond"; "flame and end count as a yellow chocobo in breeding
and cannot pass on nether or end".

* **Courses are circuits now** (`race/TrackSpline`, `race/RaceTrack`): a closed
  Catmull-Rom spline through a `Shape` template, resampled to one sample per block,
  scaled to the class lap length. Ahmi rejected the first set ("basically the same
  shape") and then "different shapes for ALL courses": there are 24 silhouettes, one
  per course (sprints compact: STADIUM, ZIGZAG, PEANUT, DELTA, LOLLIPOP, KIDNEY, DEE,
  ELBOW, TRIDENT, BOOMERANG, RAMPART, HAMMER; grands prix sprawling: ROVAL, CLOUD,
  WAVE, SERPENT, HAIRPIN, STAIRS, SWITCHBACK, CASTLE, CROWN, SWEEPS, HOOK, BEE), each
  with straights, sweepers, hairpins, chicanes and a hill profile in blocks; the test
  pins 24 distinct shapes. Design rule: last point (-30, 0), first three along +x, legs
  >= 6% of the perimeter apart for sprints (small scale). Sheet in
  `art/preview/track_maps_v4.png` ("much better"). Lap length
  length (sprints 600-774, grand prix laps 1150-1600 blocks), centred on its island.
  t = 0 is the template's second control point so the line and grid sit on straight
  road; winding is normalised so +offset is always the infield. Heights: moving
  average + slope clamp (a step every 3 blocks), terrain features flattened, lowest
  road on TRACK_Y. API kept (`pointAt`, `pointAtLane`, `progressAt` = nearest sample,
  `stallPos`, `facingYaw`, `lapLength`, `centerX/Z`) plus `tangent`, `turnAhead`,
  `isStraight`, `groundY`, `theme()`, `terrainFeatures()`, `terrainAt()`.
  `getRadiusX/Z` are now half extents. 24 tracks renamed (lang
  `chocobosreborn.track.*`, e.g. Meadow Circuit, Fern Ford, Rainbow Skyway, Obsidian
  Keep); `RaceScoring.raceLoopKey` remapped by theme.
* **Themes** (`RaceTrack.Theme`, 3 per class, sprint + grand prix share one): MEADOW,
  ORCHARD, SHORE / CANYON, RIVER, SNOW / CAVERN, JUNGLE, NETHER / SKYWAY (rainbow
  concrete road, glass pillars, no rails), KEEP, END. Each has road, striped corner
  kerbs (kerbA/kerbB), rail, wall, margin ground, island rock, post, lamp and its own
  decoration in `RaceCourseLayout.decorate` (hay, cherry trees, palms and pools,
  cacti and terracotta spires, spruce, snow and ice, amethyst and dripstone, bamboo,
  lava pools and soul fire, obsidian and chorus, ...).
* **Features** (`Feature.Type`): WATER, RIDGE, LAVA (Nether bird + Gold cross it;
  `ChocoboColor.lavaWalk` includes GOLD now), MUD (a bog: `mud` blocks, everyone slowed
  -55%, the detour is the smart route) and BOOST (strips of
  `chocobosreborn:boost_pad`, +55% speed for 50 ticks, whoosh + END_ROD trail; the
  carpet-thin block has FACING for the chevrons, animated texture from
  `tools/draw_boost_pad.py`, no recipe). C: boosts only; B: 1 terrain; A: 2 +
  a bog; S: 3-4. `ChocoboEntity.tickCourseEffects` applies both as transient
  MOVEMENT_SPEED modifiers (`chocobosreborn:boost` / `:bog`), so riders and AI alike.
* **Layout** (`RaceCourseLayout`): stamps every 0.5 blocks along t; kerbs striped on
  corners (`turnAhead > 0.22`), rails outside (and inside on corners), walls beside
  liquids, detour road + its own kerb outside features, decorations every 14 blocks
  in the margins, lamps, gantry, and the **grandstand in the infield** along the
  start straight (four tiers of quartz stairs on the theme wall, back wall with
  banner stripes, roof, lamps) with `fanPosts()` (8). `RaceSession.spawnFans` puts
  KIN fans (FAN_* looks cycling) there for the heat and discards them in
  `teardown`; the village posts for fans are gone.
* **AI** (`RacerGoal`): straights from `track.isStraight`, brakes into tight corners
  (`turnAhead(t, 18)`), detours around unsuited terrain via `terrainAt`, and a per-bird
  `bogSavvy` roll (45% + 55% x lineHold) so sloppy C birds sometimes drive into a bog.
* **Saddle combat**: `ChocoboEntity.hurt` ignores damage whose source or direct
  entity is a passenger (swings, sweeps, arrows); with `isPickable` false for the
  driver the crosshair already passes through the bird.
* **Gold armour dropped**: item, recipe, icon, mesh, atlases, blend, glb, stills and
  the still8 reference removed; `ChocoboArmorItem.Tier` is LEATHER / IRON / DIAMOND /
  NETHERITE (netherite shows diamond's mesh, leather none). Existing armour NBT stores
  the tier ordinal, so old diamond / netherite pieces shift (dev worlds only).
* **End / Nether plumage**: `paint_albedo.TEXTURED` tiles the vanilla `end_stone` /
  `lava_still` (frame 0, x4) over the plumage inside a baked UV mask of the tail fan,
  neck ruff and head crest (`tools/bake_feather_mask.py` from the rig's vertex
  groups -> `art/masks/<variant>_feathers.png`, dilated 2 texels), blended 55% / 30%
  toward the breed colour and shaded by the source luma; the body stays pure
  purple / red. `tools/repaint_atlases.py` re-derives every breed atlas from the
  shipped yellow one without Blender (`paint_breed` is the single entry point, used
  by `stills.write_breed_atlases` too); `tools/render_breeds.py` renders breed stills
  from a saved blend + the shipped atlases. Vanilla textures live in `art/ref/vanilla/`
  (pulled from the moddev resources jar). ACCENTS is empty now.
* **Breeding**: `ChocoboColor.toLine` maps PURPLE / FLAME to YELLOW and
  `BreedRules.asBreedingColor` turns an inherited End / Nether colour into Yellow, so
  neither ever passes on.
* **Tests**: `RaceTrackTest` rewritten (progress on and off the line, straight grid,
  theme pairs, length gates, corners / hills / walkable steps, leg spacing via
  `closestLegsAt`, feature rules incl. level terrain and no overlaps, island bounds,
  layout: water, ridge, boost pad, bog, lava, 8 grounded fans, block budget);
  `CourseMapDumpTest` writes `build/track_maps/<id>.png` (sheet in
  `art/preview/track_maps.png`) so a layout can be eyeballed without the game.
  84 tests green.

## Race music (2026-09-16 22:40, Ahmi: "there is supposed to be race music, it came from Downloads/race, I own the license")

The seven loops are Ahmi's licensed tracks from `Downloads/race music` (Chocobo Dash,
Chocobo Race Gallop, Gallop of Adventure, Gallop of Heroes, Rune Dash, Speed of the
Dragon, Victory Stinger), shipped as OGG Vorbis in `assets/chocobosreborn/sounds/music/`
(44 s to 172 s) and registered in `ModSounds` (`music.race.*`, streamed).
`client/RaceMusic` plays a loop while the local player rides a racing bird in the
Square (MUSIC channel, so the music slider must be up); the bird now syncs
`DATA_RACE_TRACK` (set by `RaceSession`, cleared with `setRacing(false)`), so each
course gets the loop `RaceScoring.raceLoopKey` maps it to instead of its class's
first course. The stinger plays for a first-place human finish.

## Village, heat timetable, AI drive, village theme (2026-09-16 22:45-23:30)

Ahmi: "races should be hard but winnable throughout all classes for the racer AI;
races should start every 5 minutes, Esther announces at 2 mins, 1 min, 10 second
countdown, so more than one player could join (replacing an AI racer); Woodland
Pastoral.mp3 is the Whiskerwind village theme."

* **Whiskerwind rebuilt** (`race/VillagePlan` + `SquareBuilder.buildPaddock`,
  PADDOCK_VERSION 4, Z0 -104, Z1 -40, HALF_W 48): an organic island (centre (0,-72),
  rim 46 +- sine wobble, rock deepening to 16, hanging roots, three waterfalls off the
  rim), the paved plaza round the arrival medallion (lamps, flower beds, tribe
  banners), the avenue and two cross streets with lamp posts, the chocobo fountain
  (yellow-concrete bird in a basin, sea lanterns) at (0,-80), four timber cottages,
  the two-storey inn with bell tower (-26,-74), the stable yard (27,-75), the windmill
  on its mound (-34,-86), the race arch + Esther at Z1 with a railed pier to a viewing
  deck over the void (z -14), the return portal at Z0, and a shrine islet (56,-60)
  with a gold egg on a lectern reached by a rope bridge. Stalls round the plaza
  (`TownPosts`: GREENS / TACK at z -52.5, FAIR / TREATS at -66.5, EXCHANGE / BOOKIE
  at -90.5), the duel master and broker at desks by the south street (-99.5) with
  open ground in front. No fans in the village any more (they stand on the courses).
  A subagent tried to write this village and died on the output limit; the plan
  above is hand-written in pieces.
* **Heat timetable** (`race/HeatSchedule`): ranked heats go off on the five-minute
  mark of game time (PERIOD 6000 ticks), one per class. Esther in the Square: a rider
  on a race-ready bird joins the pending heat of the bird's class, or (none pending)
  the course picker opens and `RaceManager.onCourseChosen` schedules it via
  `HeatSchedule.schedule` (next mark at least a minute out). Up to six entrants, each
  replacing an AI racer. Esther announces to everyone in the Square when a heat is
  scheduled, at two minutes and one minute (`heat.notice`: course, class, stalls
  taken) and counts the last ten seconds on the action bar; at the mark, entrants not
  in the saddle of their entered bird in the Square are dropped (`heat.missed`), an
  empty heat is scratched, and the rest start as one `RaceSession` (ranked, prizes per
  human; only the first entrant's pending bet is taken, as before). Duels stay
  instant. `RaceManager.startRace` remains for tests / direct starts.
* **AI drive** (`race/RacerMoveControl`, installed by `installRacer`): vanilla
  MoveControl feeds speed in as the forward input too, so mobs ran at ~speed squared
  (the gametest showed 2 blocks/s). The racer control steers and drives with full
  forward input at speedModifier x MOVEMENT_SPEED, the rider's scale. `RacerProfile`
  cruise / dash retuned on that scale (C 1.00/1.25, B 1.13/1.30, A 1.22/1.35,
  S 1.34/1.40): each field averages ~95% of a well-ridden bird of the class's usual
  grade (rider cruise = grade 0.86..1.18 x training <= 1.12, dash x1.62 on stamina),
  rivals x1.08 / x1.12 on top: hard, winnable.
* **Village theme**: `Woodland Pastoral.mp3` (Ahmi's licence) -> mono OGG
  `sounds/music/woodland_pastoral.ogg` (178 s), `music.village.woodland_pastoral`,
  `ModSounds.VILLAGE_THEME`; `client/RaceMusic` loops it whenever the player is in the
  Square and not racing (`RaceScoring.villageLoopShouldPlay`), swapping to the course
  loop for a heat.
* **GameTest**: `runVerification` now deletes `build/verification/world` first (stale
  course blocks from earlier runs sat in the road); the heat test asserts that at least
  three AI racers finish the sprint within three minutes (with a progress / position
  report on failure) instead of a distance from the stalls.

## Start lag, jockeys, sky, signs, queue timer (2026-09-16 23:30-00:15)

Ahmi: "race starts lag so bad player is always way behind AI racers, should be a visual
countdown on screen into a smooth start; fans should be looking towards the human
players; the village and the racetracks should be in the void, same skybox of day and
night from Tribal Power; AI racers should have riders on them, use more Tribal Power kin;
gates need to be more clear; still need the race timers, one race every 5 minutes,
players join the queue and are transported with a visual message."

* **Start**: `RaceSession.HOLD_TICKS` 260: riders are transported, a title with the
  course name + "Get ready..." shows at tick 30 while their chunks stream in, then the
  last five seconds count down as big gold numbers (`race/Titles`, note-block plings)
  into a green "WARK! Go!" title. AI reaction is measured from GO and floored
  (C 16-30, B 12-20, A 10-14, S 8-11 ticks) so no field jumps the lights.
* **Jockeys**: every AI racer carries a Tribal Power kin (`TownRole.JOCKEY_*`: five
  tribe looks cycling; `JOCKEY_TEIYO` / `JOCKEY_JOLO` for the rivals, whose names now
  sit on the jockey, the bird's name hidden). `RaceSession.mountJockey` spawns and
  mounts them (`KinStewardEntity.installJockey` strips their goals), `RacerGoal` turns
  them with the bird, `teardown` discards them. Kin roles are rendered by
  `KinStewardRenderer` arrays built from `TownRole.values()`, so new roles just work.
* **Crowd**: `KinStewardEntity` look goal now 48 blocks at probability 1, so fans (and
  keepers) track the riders.
* **Sky**: `client/SquareSky` = Tribal Power's `MarchSkyEffects` + `PanoramicSky` in this
  mod's namespace (Ahmi's own `march_day/night` panoramas copied to
  `textures/sky/square_day|night.png`, shader `shaders/core/panoramic_sky.*`), registered
  for `chocobosreborn:square` (`RegisterDimensionSpecialEffectsEvent`,
  `RegisterShadersEvent`). `dimension_type/square.json`: effects `chocobosreborn:square`,
  `fixed_time` removed (day and night cycle), ambient 0. Village and courses stay in the
  void underneath.
* **Gates**: the two course gates under the arch are gone (heats run on the timetable);
  waxed oak wall signs (`SquareBuilder.sign`, translated lines `sign.*`) on the arch
  pillars say "NEXT HEAT every 5 minutes, ride a saddled bird to Esther", "VIEWING PIER"
  and "GOING HOME?"; the return alcove has three gate blocks across it under sea lanterns
  with "RETURN GATE" signs either side. PADDOCK_VERSION 5.
* **Queue**: entrants see "Next heat on X in m:ss, n of 6 stalls" on the action bar
  every second; at the mark they get a "To the stalls!" title as they are transported.

## True void, protection, town birds, village and course flair (2026-09-17 00:15-01:00)

Ahmi: "the whole village is supposed to be a void island, not surrounded by land; the
village needs overall polish, fancy and gorgeous race town (love the statue); race
tracks are void islands and need more flair; no mobs should spawn in this dimension;
Whiskerwind and the racetracks should have spawn block protection; Whiskerwind should
have chocobos wandering around but not tamable."

* **The Square really is a void now.** `dimension/square.json` still had the flat
  bedrock / stone / dirt / grass layers (the earlier "void" change never reached the
  file; `tools/write_datapack.py` regenerates it): now `layers: []`, biome
  `minecraft:the_void`. `dimension_type`: effects `chocobosreborn:square`, no fixed
  time, ambient 0 (the writer matches). The two dev worlds' Square dimension folders
  (`run/saves/*/dimensions/chocobosreborn/square`) were deleted so the old land
  regenerates as void and the village rebuilds; anything left in the Square there is gone.
* **No natural spawns**: `RaceManager.onFinalizeSpawn` cancels every spawn in the Square
  except EVENT / SPAWN_EGG / BREEDING / COMMAND / BUCKET / DISPENSER / TRIGGERED (what the
  mod places itself).
* **Protection**: `BlockEvent.BreakEvent` / `EntityPlaceEvent` are cancelled in the Square
  for everyone but a creative operator (`square.protected` message).
* **Town birds**: `ChocoboEntity.townBird` (synced + NBT): `SquareBuilder.spawnKeepers`
  keeps five in the village (yellow / green / blue / white, `restrictTo` the island),
  untamable and unfeedable (`square.town_bird`).
* **Village polish** (`race/VillageBuildings`, PADDOCK_VERSION 6): a shared race-town
  style: stone-brick footings, dark-oak frames with stair braces and log bands, cream
  infill, shuttered windows with flower boxes, gable roofs with dormers and eave
  trims, brick chimneys that smoke (campfire in the throat), gold-trimmed doors with
  lanterns. Cottages (9x7, four roof materials), the inn (three storeys: arched stone
  ground floor, balcony over the door, slate roof with dormers, taproom, beds, a bell
  tower with a green copper cap and lightning rod), the stable (log barn with hay loft,
  ladder, troughs, tack wall, hitching rail, gated yard), the windmill (tapered stone
  tower, wooden cap, big cloth sails on a mound), the **Race Hall** (sandstone and
  quartz with gold trim, double doors, tribe banners, trophy pedestals under glass, a
  lectern dais, lantern chains, and a notice board of signs outside), striped tribe-
  colour awnings over every stall, the gatehouse arch (two crenellated towers with
  quartz corners and gold caps, the archway with a glazed gold trim line, banners,
  Esther's gold-ringed dais), plaza hedge ring, lantern chains between the lamps,
  benches, hedged avenue with lantern chains. TACK moved to (17.5,-48.5) to make room
  for the hall.
* **Course flair** (`RaceCourseLayout`): set pieces every 8 blocks alternating sides,
  small verge details every 3 blocks (grass, petals, snow, lichen, fungi, end rods...),
  tribe-colour flag lines along the straights, coloured seat backs and a striped awning
  on the grandstand, a starting-light tree (red / amber / green) under the gantry beam,
  a marshal's tower beside it, and a **landmark** per theme at the far side of every
  course: great oak, giant cherry, lighthouse, hoodoo, spring cairn, ice spire, amethyst
  geode, mossy step pyramid, Nether fortress tower with a lava fall, and road arches on
  the Skyway (glass + end rods) and Keep (blackstone + soul lanterns), an obsidian pillar
  in the End. Block budget test raised to 750k.

## Jockey seat, lane boost pads, rider boost (2026-09-17 01:00)

Ahmi: "racer jockeys are standing instead of sitting; booster pads should only cover
part of the raceway and provide a bigger boost; player does not get the bonus".

* `KinStewardRenderer.Model.setupAnim`: a passenger kin sits (legs forward and apart,
  arms on the reins, cloak trailing).
* `RaceCourseLayout.boostLane`: each strip covers one 3-block lane, cycling outside /
  centre / inside per strip, so riders steer for them.
* `ChocoboEntity.tickCourseEffects`: boost is now x2 for 45 ticks (BOOST_POWER 1.0);
  movement is measured from the previous tick's position (`xo` / `zo`) because a
  ridden bird's server-side delta is ~0 (the client drives it), which is why riders
  never triggered a pad.

Esther's announcements, notices, countdowns and queue timers go only to players whose
level is the Square (`ServerLevel.players()` / a level check on entrants); heat
messages go to the racers themselves (Ahmi: "only seen or heard by users in the
Whiskerwind dimension").

## Balance pass, fountain paths, signs, fallen blocks (2026-09-17 01:20)

Ahmi (screenshot): "this area needs paths to the shops on the other side of the fountain;
Esther needs to face in towards the town, same the signs; a lot of fallen wool, flowers etc;
racer AI is slightly too good; spent all greens on speed, the bird got more stamina but
barely faster; birds need to eat less or the greens do more."

* **Greens**: every feed gives twice the points (Gysahl 2/2/0/2 ... Sylkis 8/8/6/6);
  the training grade step is 120 points (was 60) so the ladder is unchanged in feeds.
  **Speed training** is now +0.35% per point (+35% at 100, `ChocoboEntity.SPEED_PER_POINT`),
  was +0.12%; stamina stays +1 per point.
* **AI**: cruise eased 4% (C 0.96, B 1.08, A 1.17, S 1.28).
* **Village** (PADDOCK_VERSION 7): a paved ring round the fountain basin joins the avenue
  on both sides plus paths east and west along z -80, so the south stalls are reachable
  without cutting through the basin. Orientation note that bit us: the town is NORTH of
  the arch (Z1 = -40; the pier runs south toward z +700) and SOUTH of the return gate
  (Z0 = -104). The arch signs now hang on the towers' north faces at z-3, the return-gate
  signs on the frame's south face; Esther's yaw 180 faces the town. Keepers only turn for
  players within 6 blocks (`LookAtPlayerGoal`), the crowd on the courses gets
  `installFan` (64 blocks) instead, so Esther no longer swivels toward the pier.
* **Fallen items**: stall awnings are solid wool (the carpet row over air dropped as
  items); the barn ladder is on the back wall; the pyramid vine attaches
  (`vine[east=true]`); course verges use only self-supporting blocks (no tall grass
  halves, no grass on sand, lichen with `down=true`, nylium instead of roots).

## Gate faces the town, Esther inside the banners, no dock (2026-09-17 01:40)

Ahmi (screenshot): "this whole gate faces off into the void, it should face in; Esther
should be on the inside of that banner post; what is this floating dock". The pier is
gone: `VillagePlan.overlook` lays a railed sandstone terrace on the island edge behind
the arch instead (benches, a lantern post with the town banner). The arch's wall banners
and lanterns are on the town (north) face; Esther's post and dais moved to
PADDOCK_Z1 - 8.5 (inside the plaza's banner posts, which moved to x = +-6), facing the
town. PADDOCK_VERSION 8.

## Avenue width, hedges, overlook rock, signs by Esther (2026-09-17 02:00)

Ahmi (screenshots): "path should be slightly wider, leaf blocks still too close to
fountain; no idea what this floating stuff is; gate signs cannot be seen, they should be
next to Esther." Avenue is 7 wide with lamps and chains at x +-5 and hedges at +-4,
none within 12 blocks of the fountain. The overlook lays its own stone under the
terrace (the island rim wobbles, so its benches and post had been placed over the void).
The heat / home signs are standing signs on posts either side of Esther's dais at eye
height (`oak_sign[rotation=8]`, `writeSign`); the overlook sign sits on its lantern post.
PADDOCK_VERSION 9.

## Esther on the overlook (2026-09-17 02:15)

Ahmi: "move Esther and the signs to the overlook, also there is a lantern on the ground
near the gate." Esther's post is PADDOCK_Z1 + 7.5 (the overlook centre beyond the
arch), facing the town; her gold-ringed dais and the two standing signs are built there
(the arch is laid after the overlook). The overlook's rim lanterns sit on top of the
wall posts instead of on the floor. PADDOCK_VERSION 10.

## Chains, dropped lanterns, jockey seat, saddled field, beak bleed, rider boost (2026-09-17 02:40)

Ahmi: "remove the chains from this area, keep the lanterns minus the one that has fallen;
a few more dropped lanterns around town; jockeys still sit very high and the racer
chocobos should all be saddled; some bleed through on the beak of coloured chocobos;
boosts do not work on the player, only on the AI."

* Avenue lantern chains removed (the lamp posts stay). Cottages and the inn get an attic
  floor so their ceiling lanterns hang from something (they were dropping as items).
  PADDOCK_VERSION 11.
* `KinStewardEntity.getVehicleAttachmentPoint` = (0, 1.15, 0): vanilla SUBTRACTS the
  passenger's vehicle attachment from the seat (a player's is 0.6), so a negative value
  lifted the jockeys two blocks into the air (Ahmi's screenshot); 1.15 seats them, and the
  point also carries 0.30 forward / 0.15 left (rotated by the kin's yaw) since a kin sat
  "slightly too far back and to the right" on the player's seat. AI racers get a saddle in their SADDLE slot (`RaceSession.spawnField`), so
  they render with the saddled mesh.
* Plumage rule tightened to r-g < 60 (beak orange is r-g >= 70; feathers 24-31) in
  `paint_albedo.plumage_mask` and `ChocoboMeshRenderer.isPlumage`; atlases repainted.
* Rider boost: a ridden bird's server delta is ~0 and `xo` is refreshed after the
  rider's move packet lands, so the pad never saw movement. `tickCourseEffects` keeps its
  own last-tick position. The boost is now a synced flag (`DATA_BOOST`) that
  `getRiddenInput` applies on the driving client (x2); the attribute modifier is only
  used for AI birds (no double boost).

## Village persistence (2026-09-17 02:50)

Ahmi: "once Whiskerwind has been generated in a save it should not regenerate every time
a player comes." It does not: `SquareBuilder.buildPaddock` returns immediately while
`SquareData.paddockVersion` (saved in the Square dimension's `chocobosreborn_square.dat`)
matches `PADDOCK_VERSION`, and each course island is laid once (`isBuilt`). The rebuilds
seen tonight came from the eleven version bumps of this session (each bump = one relay on
the next visit). Rule from here: bump `PADDOCK_VERSION` only for a deliberate village
change, never as a matter of course.

## AI eased again (2026-09-17 03:00)

Ahmi: "AI is still a bit too hard." Cruise C 0.91 / B 1.03 / A 1.11 / S 1.22, dash
1.22 / 1.27 / 1.32 / 1.36 (about 9% under the first rider-scale tuning). Rivals keep
their x1.08 / x1.12; then (Ahmi: "move rider difficulty back up by 5%") Teiyo x1.17, Jolo x1.13.

## Release and remove from the almanac (2026-09-17 03:10)

Ahmi: "need to be able to release chocobos from the almanac and remove passed ones."
Bird page: a **Release** button (asks "Set free?" on a second click) for a living bird
and **Remove** for a passed one. `RacePayloads.ReleaseBird(uuid, forget)` ->
`ChocoboLedger.release` (untames the bird wherever it is loaded: equipment dropped,
owner cleared, name cleared, passengers off; if unloaded the id waits in
`PendingRelease` and `applyPendingRelease` runs from `update()` when it next loads) or
`ChocoboLedger.forget` (drops a dead record). Both only for the record's owner.

## Village theme silent (2026-09-17 03:20)

Ahmi: "Whiskerwind town music doesn't play, race music does." The MP3 carried cover
art, and the first ffmpeg conversion kept it as a Theora video stream inside the OGG;
Minecraft's Vorbis decoder gives up on such a file (no log line either). Reconverted with
`-vn -map_metadata -1` (audio only, stereo 44.1 kHz like the race loops). Rule for any
future track: always `-vn`, then check `ffprobe` shows a single vorbis stream.

## Five-minute cadence, racing below your class, village night song (2026-09-17 03:40)

Ahmi: "races should start every 5 minutes, not 6; a user can pick any track in the
current rank or below but gets 1/2 reward for races won in lower ranks; Village Night
Song.mp3 for another village song."

* `HeatSchedule.MIN_LEAD` 200 ticks (was 1200): only a sign-up inside the last ten
  seconds rolls to the next mark, so the wait is never more than five minutes.
* Heats are keyed by the **course's** class. Esther always opens the picker; it lists
  every course of the bird's class and below (two columns per class, details and the
  lower-class note in tooltips). `onCourseChosen` / `startRace` reject only courses
  above the bird's class; `HeatSchedule.start` drops a bird below the heat's class. The
  AI field is drawn from the course's class. Racing below your class: GP halved, every
  other item prize dropped, no `recordFirstPlace` (no promotion credit), a message says
  so (`race.below_class`). A player can be in one pending heat at a time.
* `Village Night Song.mp3` -> `village_night_song.ogg` (audio-only, `-vn`),
  `music.village.village_night_song`, `ModSounds.VILLAGE_NIGHT`; `RaceMusic` plays it in
  Whiskerwind from 12500 to 23500 day-time ticks and Woodland Pastoral by day, swapping
  at dusk and dawn.

## Which way to run (2026-09-17 03:50)

Ahmi: "need an arrow pointing the right direction during the race start countdown."
`RaceCourseLayout.startArrow` paints a 16-block yellow arrow with a gold tip on the road
just past the grid, pointing the way round; during the hold `RaceSession.tickHold` sends
a moving stream of END_ROD sparks up the road from the grid every 4 ticks.

## Picker columns, prices, village playlist (2026-09-17 09:30)

Ahmi: "Esther should only have sprints in one column, grand prix in another; shop
prices need to be higher with the amount of GP you win; village music seems layered
(day vs night at the same time) and vanilla music too: make it a track list."

* `CourseSelectScreen`: per class, sprints in the left column, grands prix in the right.
* `RaceShops`: prices roughly tripled against the purses (Gysahl 2 for 8, Krakka 8,
  Sylkis 128, Carob 90, Zeio 128, saddle 12, lure 30, almanac 8, pocketwatch 30,
  Exchange x3 ...). 128 is the most two 64-stack cost slots can carry.
* `RaceMusic`: the village is a playlist (`VILLAGE`: Woodland Pastoral, Village Night
  Song; non-looping instances, 120-tick gap, then the next); the course loop still
  loops. `onPlaySound` (client `PlaySoundEvent`) drops every MUSIC-channel sound in the
  Square that is not ours, so vanilla's music manager can no longer layer over it.

## Arrow shape, per-heat AI form (2026-09-17 09:45)

Ahmi (screenshot): "arrow needs a pass, not a true arrow shape; racer AI should be slightly
random, each racer each race +-5%." `startArrow`: 3-wide shaft of six rows, then a
triangular head nine wide tapering to a one-block gold tip over seven rows. `RaceSession`
rolls `goal.speed = 1 +- 0.05` for every racer including Teiyo and Jolo (rivals were fixed
at 1.0 before); `RacerProfile.VARIANCE` 0.05.

## Quieter birds, the field faces up the road (2026-09-17 10:00)

Ahmi: "chocobos kweh and wark a bit too often; AI racers start facing the wrong way."
`getAmbientSoundInterval` 900 (was 160). `RaceSession.face` sets yaw, yRotO, body and
head rotation together (moveTo alone left the head at 0 and the body drifted after it) on
spawn and every hold tick for the field and their jockeys.

## Course plan version (2026-09-17 10:15)

Ahmi: "arrows need adjusting on all courses, your fix only worked on the track you fixed
it on." Courses are built once per save, so the new arrow only reached islands laid after
the change. `SquareBuilder.COURSE_VERSION` (stored as `SquareData.courseVersion`) now
gates them: when the constant is newer than the save's, `buildTrack` clears the built set
and every island is relaid in place on its next use (old paint sits on road cells, which
the relay repaints). Bump it whenever `RaceCourseLayout` changes; the village version is
separate.

## Shipping 1.0.0 (2026-09-17 10:40)

Ahmi: "enough testing, fix all the direction arrows for all tracks and then push to gh and
curseforge; update the branding page; only ship what is actually needed for the mod,
gitignore everything else."

* Jar: `./gradlew build` -> `build/libs/chocobosreborn-1.0.0.jar` (~82 MB). GameTests are
  excluded (`exclude 'tk/darrow/chocobosreborn/gametest/**'`; the old exclude named a
  package that never existed). Atlases ship at 1024 (Lanczos from the 2048 masters, which
  now live in `art/atlases/<variant>/`, git-ignored); the jar holds only classes, assets,
  data, icon and pack.mcmeta.
* Git: repository initialised, `art/` and `tools/secrets/` ignored (only what builds the
  mod is tracked: src, gradle, docs, tools). Pushed to
  https://github.com/AhmiDarrow/Chocobos-Reborn with release v1.0.0 carrying the jar.
* Branding: `docs/public/store-description.md` rewritten for 1.0.0 (Whiskerwind, timed
  heats, 24 courses, equipment, almanac, End / Nether feathers) and rendered to
  `store-description.html` by `tools/render_store_html.py`; changelog
  `docs/RELEASE_1.0.0.md`; publishing notes `docs/curseforge.md`. CurseForge project
  **1699008** (Ahmi): `tools/upload_curseforge.py` uploaded the jar as file 8903892
  (release, 1.21.1 / NeoForge / Client+Server), awaiting approval; the author token sits
  in `tools/secrets/.env` (ignored).

## Mod state (Java)

Verified locally on 2026-09-16 (and in the cloud workspace before that):

* `./gradlew test` → 76 JUnit tests, 0 failures.
* `./gradlew runVerification` (headless GameTest server) → all 10 required tests
  pass: growth stages + hitbox, greens training + satiety, nut mating + owned chick
  + talent, same-sex block, Gold-needs-Zeio, saddle + rider control, datapack,
  Chocobo Farm template, shop catalog / keeper names, full Chocobo Square heat.
  Tests: `gametest/ChocobosRebornGameTests.java`; entity-heavy tests pin chunks with
  `setChunkForced`; the heat runs in the test level via `RaceManager.testLevel`.
* `./gradlew runServer` boots clean and creates `world/dimensions/chocobosreborn/square`.
* `./gradlew build` → `build/libs/chocobosreborn-1.0.0.jar` (has `icon.png`, the
  mods-list logo).
* Visual harness: `tools/visual_harness.py` (run config `runVisual`), Linux only.

Whiskerwind: village at the origin (z -78..-48), courses 700+ blocks north; see above.
Sizes: `ChocoboEntity.ADULT_H = 3.25F` (Ahmi: 25 % over 2.6), `SEAT_H = 1.92F` + `SEAT_BACK = 0.36F`,
hitbox 1.75 wide, `RaceTrack.STALL_SPACING = 1.6`; the renderer scales the 2.25 m
mesh by `ADULT_H / mesh.height`. Chicobos = adult mesh × `getAgeScale()`.

Gameplay inventory is in `docs/SPEC.md` (greens, nuts, breeding line, riding,
wild spawn, Chocobo Farm structure, Chocobo Square: Esther / Rook / Tack / Sage
Wynn / Fair / Bilo / Marl, fans, village, advancements, items). Names: no FF7
person names (Esther, Jolo/Teiyo, Sage Wynn, Bilo the Nutkeeper, Marl).

Kin (keepers, fans, farmhands) are Tribal Power kin: skins
`textures/entity/kin/kin_{elder,drummer,hunter,weaver}.png` + `_glow` + tribe
cloaks, mapped per role in `race/TownRole`. To change a look, edit it in Tribal
Power and copy the PNGs across.

Almanac chapters live in `en_us.json` (`chocobosreborn.almanac.*`).
Calls: `tools/wark_candidates.py --ship G` (synthesised, `synth_wark.py`). Race
music: Ahmi's own Suno generations (check the Suno plan's terms before publishing
under CC-BY-SA as the README says). Items: `tools/meshy_icons.py`. Datapack:
`tools/write_datapack.py`; farm template: `tools/write_farm_structure.py`.

## Open

1. In-game test pass: the new circuits (hills, kerbs, rails, detours, boost pads,
   bogs, lava), the infield grandstand crowd, the rebuilt village, saddle combat,
   AI on hairpins, music loop.
2. Keepers share one model (textures differ per role); a hat / apron per role
   would be next. `kin_elder.png` is used (STEWARD, EXCHANGE, FAN_CLAW).
3. Walk to a Chocobo Farm in a client (`/locate structure chocobosreborn:chocobo_farm`).
4. `preview_qa.py` colour gates were written for the old paint; re-check them
   against the Meshy texture if they ever fail.

## Hard constraints

* Do not feed Nomura PNG, Luque `195906`, FFX/CC Sketchfab, or Square in-game
  frames into Meshy. Never ship Square Enix art or audio; the "custom kweh" sample
  and anything derived from it (`ship_kweh_sample.py`, `art/sound_src/`,
  `custom_chocobo_kweh*`) stay deleted.
* Beak orange, plumage tint only via `isPlumage`; white vcol × atlas, no double tint.
* Do not remesh / re-bake / repaint the Meshy bird; do not touch the eyes unasked.
