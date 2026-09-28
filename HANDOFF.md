# Current release: 1.0.19 - Back on track

A wide line keeps the lap. A real shortcut sets the rider back on the road and holds them for a second. Every fork is marked, and the HUD says whether this bird can take it. Older birds convert once: old promotion marks become points of nine, and a bird with no born stats rolls a bloodline from its grade. See [release notes](docs/RELEASE_1.0.19.md). Server and every rider need this jar. Publication records are in docs/curseforge.md. The 1.0.18 notes remain in docs/RELEASE_1.0.18.md.

# Chocobos Reborn — session handoff (internal)

Date: 2026-09-16 (evening). Repo: the checkout root (Windows dev box)
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

Commands (repo root; Blender from scoop (`scoop\apps\blender\current\blender.exe`),
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

* Jar: `./gradlew build` -> `build/libs/chocobosreborn-<version>.jar` (~82 MB). GameTests are
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

## Optimisation pass (2026-09-17 21:30, Ahmi: "optimize")

* **Renderer** (`client/ChocoboMeshRenderer` + new `client/MeshSkinner`): the bird is
  31k tris / 93k flat-shaded verts skinned on the CPU every frame. The pose (model-view,
  yaw, age scale) is now folded into the 18 bone matrices once per frame and vertices
  are emitted raw (`addVertex(x,y,z)`), so vanilla's per-vertex `addVertex(pose, ...)`
  matrix transform is gone; positions are skinned once per unique position
  (`Part.posIndex` / `upos`: 15.4k slots, identical weights, ~6 verts each) and only
  normals per vertex; influences are sorted heaviest-first with zero weights trimmed at
  load (`Part.ucount`), weights normalised at load so the no-hidden-bone path skips the
  visibility rescale; vertex colours are cached per part and breed (the vertex tint
  never changes per frame - shipped vcol is (254,254,254), so `isPlumage` never
  matches); the glow pass only runs over `Part.emissiveTri` (empty for every shipped
  mesh - it used to re-emit the whole bird whenever a vertex glowed); the gait
  crossfade reuses scratch arrays instead of `Arrays.copyOf` twice a frame. Skinning
  one bird: ~1.9 ms -> ~1.3 ms in the JUnit timing (pose transform savings on top).
  `client/ModRenderTypes.entityCutoutNoCullTriangles` is vanilla's cutout-no-cull state
  in TRIANGLES mode, 93k vertices instead of 124k (entity quads need a fourth,
  duplicated vertex); a glowing bird (`isCurrentlyGlowing`) falls back to the quad
  type because the outline pass is quads. Pixel-identical output by construction;
  `MeshSkinnerTest` checks the folded path against model-space skinning + pose on the
  shipped mesh.
* **Jar**: the 32 breed atlases were RGBA with alpha all 255; `tools/ship_atlases.py`
  now ships them (Lanczos from the 2048 masters in `art/atlases/`, which reproduces the
  approved 1024s byte for byte, then RGB): 82.1 MB -> 76.4 MB, no pixel changed. Run it
  after `fresh_ship.py` / `repaint_atlases.py`. What is left: the four `.ncgb` meshes
  (6.4 MB each, 2.5 MB zipped; NCGB v1 is shared with Ninjacat, left alone) and 14 MB
  of music.
* **Derived breed atlases** (Ahmi saw the shipped-vs-derived render sheet, "if it
  comes out looking ok we move forward" -> go): the jar ships only `yellow`, `purple`
  and `flame` per mesh (12 atlases, 19 MB). Green / Blue / White / Black / Gold are
  `client/DerivedAtlasTexture`: an `AbstractTexture` registered under the old path
  (`textures/entity/<variant>/<breed>.png`) on first use from
  `ChocoboMeshRenderer.getTextureLocation` (`ChocoboColor.derivedAtlas()`), whose
  `load` reads the yellow atlas, recolours it with `client/AtlasTint` (float32
  replica of `paint_albedo.recolor_plumage`; `AtlasTintTest` shows 0 texels differ
  from the Python derivation across all 20 atlases when `CR_DERIVED_DIR` points at
  them, and pins known texels otherwise) and uploads it; the texture manager re-runs
  `load` on every resource reload, so packs replacing yellow carry through. The
  rendered difference against the approved files was 0.02-0.16 % of pixels at the
  beak / eye rim (island edges after the Lanczos), invisible at 1x. Jar 76 MB -> 49 MB.
  Tools: masters (2048 RGBA, all eight breeds) live in `art/atlases/<variant>/` and
  `stills.write_breed_atlases`, `repaint_atlases.py`, `pad_atlases.py` now write
  there; `ship_atlases.py` makes the three jar copies (1024 RGB) and deletes stale
  solid-breed files; `render_breeds.py` derives a missing solid breed from the shipped
  yellow (and takes `--atlases <dir> --prefix --out` for comparisons). Not yet seen
  in a client here (headless box): first in-game check = every breed on the almanac
  breed row and a wild Green; a magenta bird or "Could not load texture" in the log
  means the derived texture failed.
* **Gate fix**: `greensTrainAndSate` had been red since 1.0.1 added the 5-minute
  greens cooldown (`ChocoboGreen.trainReady`): 45 feeds in one tick landed once. It now
  checks one survival feed + cooldown refusal, then feeds in creative (no cooldown) and
  reads satiety from the `GreensFed` save data. 120 JUnit, 10/10 GameTests, build.

## Wild sources (2026-09-17, Ahmi: "normal gysahl greens should be the only one you can find, everything is gated behind the shops in whiskerwind")

* Gysahl is the only green or nut outside Whiskerwind. The gysahl crop loot table
  drops greens and seeds only (the 8% Krakka/Tantal and 5% Pepio pools are gone).
  Seeds from grass stay (`gysahl_seeds_from_grass`, the only global loot modifier).
* `carob_from_ravager` and `zeio_from_piglin_brute` are deleted; Carob and Zeio are
  Bilo's stock (90 / 128 GP) and A/S prizes only, so colour breeding needs the town.
* The farm Stablehand (`RaceShops`, role `stablehand`) sells Gysahl and seeds only.
* Krakka and Tantal crafting recipes kept as the one cheap bridge (Ahmi's call).
* `tools/write_datapack.py` matches the committed data again (it also emits
  `survives_explosion` on block loot now); rerunning it only changes line endings.
* README, SPEC, store description (md + html) and the almanac greens / nuts / farm /
  items pages say so.

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
person names for keepers, kin or places (Esther, Jolo/Teiyo, Sage Wynn, Bilo the
Nutkeeper, Marl). The one exception, kept on purpose (Ahmi, 2026-09-19): the 64
AI racer names in `race/FieldRoster.java` are FF character names and stay as they
are, as a nod on the race card. Do not rename them.

Kin (keepers, fans, farmhands) are Tribal Power kin: skins
`textures/entity/kin/kin_{elder,drummer,hunter,weaver}.png` + `_glow` + tribe
cloaks, mapped per role in `race/TownRole`. To change a look, edit it in Tribal
Power and copy the PNGs across.

Almanac chapters live in `en_us.json` (`chocobosreborn.almanac.*`).
Calls: `tools/wark_candidates.py --ship G` (synthesised, `synth_wark.py`). Race
music: Ahmi's own Suno generations (check the Suno plan's terms before publishing
under CC-BY-SA as the README says). Items/blocks/GUI: `tools/pixel_items.py` (see below). Datapack:
`tools/write_datapack.py`; farm template: `tools/write_farm_structure.py`.

## Item / block art (2026-09-19 cohesion pass)

Every item, crop stage, gate, the boost pad and `gui/almanac.png` are drawn by
`python tools/pixel_items.py [name-filter...]` (primitives in `tools/pixelkit.py`):
native 32x32 (boost pad 32x128, 4 frames, same `.mcmeta`), hard alpha, 1 px outline
tinted from ink `#111a22`, 5-tone hue-shifted ramps, top-left light — the Ninjacat
Skies family style shared with Tribal Power. It is the source of truth: edit the
drawing function, rerun, never hand-edit the PNGs. The almanac GUI keeps the old
geometry (16 px nine-slice in `AlmanacScreen`), recoloured navy / copper / teal.
`tools/meshy_icons.py`, `tools/paint_item_art.py` and `tools/draw_boost_pad.py` are
kept for history but refuse to run without `--legacy` (they would overwrite this art
with the old 16 px Meshy downsamples). The bird is not touched by any of this.
The mod has no particle textures of its own.

## Falling through a course (2026-09-19, Ahmi: "once in a class B race player seems to fall through the map or the map and all textures besides the skybox vanish")

Both symptoms are one fault: the rider drops out of the world and keeps falling, so
in a void dimension nothing is left on screen but the sky.

* **Cause.** `RaceCourseLayout` stamped the island along the centre line every 0.5
  blocks with lanes every 0.5 blocks. On the outside of a tight corner, over the lip
  of a hill and at the mouth of a detour, consecutive stamps land more than a block
  apart, so whole block columns were never written — a hole clean through the island
  (no road, no rock, nothing). Measured before the fix: 761 empty columns under the
  racing area across the 24 courses, 152 of them a 2x2 square or larger, which is
  wide enough for the 1.75-wide bird hitbox to fall through. B_GLACIER, B_FORD,
  C_SHORE, A_CRYSTAL, A_EMBER, A_TEMPLE and all four S sprints had one.
* **Why nothing caught it.** `RaceScoring.squareFallRescue(y, onCourse)` only fired
  `y < 50 && !onCourse`, and `RaceCourseLayout.onCourse` is true within a block of any
  road tile — which is exactly where these holes are. A rider who fell through one was
  never rescued: they fell for ever.
* **Fix.** Terrain is stamped by `stampSection`, called twice per decoration step
  (0.25 blocks along the line) with lanes every `LANE_STEP` = 0.25; the decoration
  cadence (`i % 16`, `i % 24`, kerb and rainbow striping) is untouched, so the courses
  look the same. `plugHoles()` then fills any column left empty under the road and its
  one-block skirt, at the level most of its neighbours sit at. Islands grew 0.5-1.6 %
  in blocks and 0-1 chunks each; silhouettes unchanged. `squareFallRescue(y)` now
  rescues any racer below y 50 wherever they are (nothing legitimate is below the
  islands at y 59 — flight is blocked during a heat).
* **`COURSE_VERSION` 5 -> 6**, so islands laid by an older build are re-laid on their
  next use; an existing world fixes itself the first time a course is raced again.
* **Regression test** `race/CourseIslandTest`: no void column under the road or its
  skirt, a floor under every lane at 0.25 resolution, and every stall over solid
  ground, for all 24 courses. Gates green: 130 unit tests, 11 GameTests, jar builds.
* Still worth an in-game look: the plugged columns use `theme.road` inside the band
  and `theme.ground` outside it, so a patch in a water or lava feature reads as road.

## Course polish pass (2026-09-19, Ahmi: "look for further issues and areas of polish in the race courses")

Audited all 24 course plans block by block. The fall-through fix above came from one
stamp pass overlapping itself; the same cause was leaving other things on the courses.

* **The plan is stamped in layers now** (`RaceCourseLayout` constructor): ground and
  margins for the whole lap, then the road band (features first, plain road second),
  then kerbs and rails, then scenery, then the structures. A circuit folds back on
  itself on a tight corner, so in a single pass a later section overwrote road an
  earlier one had already laid. Measured on the racing line before / after: cacti,
  camp fire, leaves, flowers and ferns on the road 0 (was ~90 blocks' worth), fence
  and wall rails standing in the band 5-25 samples (was 250-770 per course), stray
  water / lava / mud from a feature on a completely different part of the lap 0-4
  samples (was 44-171). Whole-course surface purity: 0.61 % -> 0.16 % of band samples
  not road, and most of what is left is the gold start arrow, which belongs there.
  `put` refuses to touch a road tile while `sparingRoad` is set (scenery, kerbs,
  rails, lamps, warning posts); the gantry, line, grid, arrow and stand still cross it.
* **Pools are sealed.** Water and lava are placed as source blocks with no block
  update, so they sit still until something disturbs them and then drain over the
  island. Every pool had 4-38 open faces on the detour side, where the kerb was
  skipped to open the detour. Both sides are kerbed alongside a pool now, and the
  strip between band and kerb is walled (one kerb stamp per step steps diagonally on
  a diagonal leg and leaked through the corner). All 24 courses: 0 open faces.
* **`COURSE_VERSION` 6 -> 7**, so islands are re-laid again on next use.
* **Tests** (`race/CourseIslandTest`, 6): no void column under the racing area, a
  floor under every lane, every stall on solid ground, pools cannot drain, nothing
  standing in the racing line, no stray terrain off its own feature.

### Open question: what a detour costs (numbers, no change made)

The terrain features are meant to pay off for the bird that suits them. Measuring the
real path (swing out, round, swing back) against the direct line:

| cheap (nearly free) | fair | expensive |
| --- | --- | --- |
| S_STARFALL water +4.7 / +6.3 | most features +13 to +20 | S_VOID mud +50 |
| A_INFERNO ridge +7.2, S_MAELSTROM ridge +7.3 | | S_KEEP lava +32.4 |
| S_CITADEL lava +7.6, A_TEMPLE mud +7.7 | | A_DEEPS water +33.6 |

All in blocks, laps are 600-1600. The spread is geometry, not design: a detour laid
on the outside of a bend is long, one on a straight or the inside of a bend costs only
the two connectors. So a Blue bird gains ~0.4 % of a lap on S_STARFALL and ~5 % on
A_DEEPS for the same ability. If this should be evened out, the options are to pick
the detour side per feature (whichever is longer — touches `RacerGoal`'s lane choice
and the island spacing) or to scale the detour offset per feature to hit a target
cost. Ahmi's call; nothing was changed.

Also noticed, not changed: a bird is invulnerable in the Square (`squareProtected`)
but **its rider is not**, so a player who drives a non-lava bird into a lava feature
burns and can die on the course (A_EMBER, A_INFERNO, S_KEEP, S_CITADEL). Death in the
Square is already handled (forfeit + return), so this may be intended; fire resistance
for a racing rider would make it a racing mistake instead of an inventory loss.

## Detour balance, rider safety, course polish (2026-09-19, Ahmi: "1 balance detours 2 make player invincible as well in the square seems an easy fix"; "then polish every course, every one should feel amazing, unique and fun")

### 1. Detours are worth the same everywhere

`RaceTrack.detourCost(f)` measures what a feature is actually worth: the blocks a
detour-taker travels over the bird that goes straight through, the swing out and back
included. `RaceTrack.detourTarget()` is what it should be — 2 % of a lap, floored at 13
blocks and capped at 28. Before this pass the spread was -6 to +50 blocks: on
S_STARFALL the water detour was *shorter* than the direct line, so a Blue bird lost
time by using its ability, while S_VOID's bog cost 50. It is geometry: a detour on a
straight costs only the two connectors, one round the outside of a big bend costs a
tenth of a lap.

Every terrain feature was re-placed by a solver (a DP over a 0.01 grid of the lap)
under these constraints, and the spans in the course table are its output:

* each detour as near `detourTarget()` as the course allows — now 13 to 29 blocks,
  every colour feature inside 0.55x-1.3x its course's target (was 0.4 %-5 % of a lap,
  now 1.0 %-2.1 %);
* a bog aims at 45 % of the target and must be the cheapest thing on the course to go
  round — nobody suits a bog, so its detour is everyone's route, not a toll;
* features sit level on the hill profile (a pool on a slope steps), spread round the
  lap rather than bunched in one half, and clear of the opening stretch where the
  field is still bunched off the grid;
* boost strips take the corner exits that are left: **three on a sprint, five on a
  grand prix** (was two or three), each on a straight, clear of every detour connector.

`CourseBalanceTest` holds all of it. The solver itself was scratch — the numbers are
baked into `RaceTrack` so a course can still be hand-tuned; re-run the maths by
comparing `detourCost` against `detourTarget` if a shape ever changes.

### 2. A rider is as safe as the bird

`RaceManager.onInvulnerabilityCheck` (NeoForge `EntityInvulnerabilityCheckEvent`) makes
a player in Whiskerwind invulnerable to everything that does not bypass invulnerability,
the same rule the birds have had (`ChocoboEntity.isInvulnerableTo`). Riding a bird that
cannot take a lava feature now costs a place, not a life and an inventory in the void.
`/kill` still works. The Square tick also puts out a burning rider and teleports any
visitor who is below y 50 and not in a saddle back to the paddock (a rider in the
saddle is the session's to rescue, bird and all). `RaceScoring.squareRiderProtected`
and `squareVisitorFallRescue` are the rules, unit-tested.

### 3. Every course has its own landmark

The set pieces were one per theme, and two courses share a theme, so the sprint and the
grand prix of each looked the same. The sprints keep theirs; the twelve grand prix
courses get their own (`grandPrixLandmark`): windmill (C_DOWNS), cider barn (C_CIDER),
beached shipwreck (C_LAGOON), terracotta arch (B_MESA), mill wheel (B_RAPIDS), frozen
waterfall (B_GLACIER), dripstone hall (A_DEEPS), mossy idol with gold eyes (A_TEMPLE),
bone arch over a soul fire (A_INFERNO), a ring hung over the road (S_STARFALL), a
banner gatehouse (S_CITADEL), a caged crystal (S_MAELSTROM). Landmarks are stamped with
`sparingRoad` set, so a fold of the course cannot end up wearing one.
`CourseIdentityTest` pins that each of the twenty-four lays its own signature blocks.

**Preview**: `python tools/track_sheet.py` builds `art/preview/track_maps.png` — all 24
courses four to a row on their class colour, with lap length, laps, features and boost
count under each. Run the `CourseMapDumpTest` first to refresh `build/track_maps/`.
The sheet is the working preview, **not the gate** — the gate is the saddle.

Gates: 136 unit tests, 11 GameTests, jar builds.

## Legs, class C, the rivals, Whiskerwind v13 (2026-09-27, unreleased; Ahmi: "class c is too easy, and ai birds legs don't seem to ever move"; "the town needs a rework ... increase its size by about 50% make it feel more alive"; research FF7 and adjust)

### Frozen legs on other birds

1.0.17's `ChocoboEntity.travel` returns early on a client for any bird it does not
drive (the server stream places it). Vanilla feeds `walkAnimation` from the end of
`travel`, so every AI bird's and every other rider's legs froze. The early return
now calls `calculateEntityAnimation(false)` first. Not yet seen in a client.

### Class C, and why 1.0.17 made it easy

1.0.17 put `gradeSpeedMul` on the AI stack (`RacerGoal`), and `grade()` includes one
step per 120 training points. So the C field ran as Average (x0.94, 22 training x4 =
88 < 120) and lost C's 10 % rubber band at the same time: a C bird cruised ~0.86 of a
fresh Good bird. B/A/S got faster in 1.0.17 for the same reason (B Great 1.10, A and
S Wonderful 1.18). Now:

* C field training 34 (±4 still crosses the first step, so C races at Good), cruise
  0.845 -> 0.880, reaction 14-26 (was 16-30), stumbles 0.7/lap (was 1.0).
  `RaceScoring.fieldPace(C)` = 0.985 (was 0.855): a fresh Good bird with no training
  now has a race. `FieldPaceTest` pins it and the C<B<A<S order.
* **Teiyo / Jolo run off the rider's bird (FF7 Teioh).** At the grid,
  `RaceScoring.rivalPace` = max(field x1.06, share x best rider's `speedMul()`),
  share B 1.00 / A 1.05 / S 1.10; Jolo 4 % under Teiyo. `RacerGoal.paceScale` scales
  the rival's own stack onto that. Before this a B Teiyo cruised ~1.64 (Wonderful from
  400 training points x the old x1.17), ~45 % over the B field. Set once per heat, so
  "the field holds its own pace" still holds.
* **Colours.** Jolo rides Blue in B, White in A, Gold only in S
  (`RaceScoring.joloColor`). The S fallback colour is Blue, not Gold. `FieldRoster`
  class B is Yellow / Green / Blue only (Vincent, Rude, Tseng, Nanaki, Rufus Blue;
  Reno, Reeve Green; Elena, Scarlet Yellow); class A lost two Blacks and a Yellow to
  Green / Blue (Kiros, Steiner Green; Vivi Blue). No roster bird is Gold.

FF7 research (agent, sources: FF wiki Chocobo Square / Teioh / breeding, gmo7897
GameFAQs guide) suggested but **not done**: Teiyo appearance odds B 1/8, A 1/4, S 1/2
(needs pre-heat bets on him refunded when he does not show), promotion 3 wins
(= 6 points, sprint 2 / GP 3), R1+R2-style faster stamina refill when holding back,
pooled breeding wins (FF7 pools the pair's wins: 4 / 9 / 12, 10 % with none), a
quinella bet, Teiyo x1.25 stamina (`ChocoboEntity.maxStamina` passes teioh=false).
The research also noted `RacerProfile.energyDrain/energyRecover` are unused: the AI
spends the same stamina pool as a rider.

### Whiskerwind v13 (PADDOCK_VERSION 13)

`VillageLayout` is the Minecraft-free source of every position: RADIUS 46 -> 69 round
the same centre (0, -72); plaza (0, -52) r16 (ARRIVAL and `RaceTrack.PADDOCK_Z` moved
to z -51.5); fountain (0, -84) basin r7 inside a paved ring r12; arch z -24 and the
overlook r12 beyond it (Esther at z -16.5); return gate z -126. `plots()` lists every
footprint; `VillagePlanTest` asserts all on the island (0.5 in from the rim) and none
overlapping, plus residents housed, the schedule, keys translated.

* Old landmarks spread 1.5x: race hall (39, -51), inn (-39, -75), stable (42, -87),
  windmill (-47, -95), stalls at x ±22.5 round the plaza, Exchange / Rook at x ±15.5
  past the fountain, Sable / Pell at x ±24.5 on the north street.
* New (`VillageDistrict`, `VillageBuildings.nestBarn / jockeyLounge`): 8 cottages,
  streets and lanes as polylines (never pave a plot), a ranch paddock (fence gap 1 wide:
  grown birds stay in, kin walk through), a closed chick nursery by the nest barn
  (turtle eggs on hay: they never hatch off sand), a pond with a dock (a waterfall
  below it), an orchard with the bridge to the shrine islet (now (-38, -158)), the
  winners' board (x 5..13, z -30) by the arch, two fingerposts (arrows relative to the
  reader), fruit and fish stalls, trees on a jittered grid (`scatter`).
* **Practice gates removed** (Ahmi). `SquareGateBlock` SHORT/LONG kinds and
  `RaceScoring.funGateCourse` are left for old block states; nothing places them.
  Home sign now says "the north gate".
* **Easter egg (Ahmi): a gold saucer model at the statue's feet** (`goldSaucer`,
  5 south of the statue in the basin: blackstone pylon with end-rod lights, 3x3 gold
  disc with copper arms, glass domes lit by glowstone, end-rod spire) with a plaque
  "a souvenir from a saucer of gold, far far away". Vanilla blocks only.
* **Townsfolk**: 10 `TownRole.RESIDENT_*` kin (Tamsin, Orrin, Brisa, Hobb, Wren, Lark,
  Mabry, Pip, Dunmore, Quill), posted at home in `TownPosts`, `TownRoutineGoal`
  (thinks once a second): home at night, work 1000-8000, fountain/plaza to 10500, the
  inn to 12500; a heat within 90 s or running (`TownLife.heatLive`) sends them to the
  overlook rail (not at night), and they cheer (`DATA_CHEER`, renderer arms-up) for the
  last 10 s and the race. Doors open (OpenDoorGoal), FOLLOW_RANGE 48, partial paths
  resume, a walker stuck 45 s out of sight steps onto its spot. Keeper sync leaves
  residents alone unless >110 blocks off or fallen. Click: their own line or one of 18
  tips (`chocobosreborn.gossip.N`, all checked against the rules).
* **Town birds by patch** (`SquareBuilder.FLOCKS`, `ChocoboEntity.setTownHome`, saved
  as `TownHome`): 6 grazing in the ranch, 2 saddled in the stable yard, 3 chicks in the
  nursery (a town chick's age is held, it never grows). Strays are replaced.
* **Race day** (`TownLife`): the inn bell rings 3 strokes at the 2- and 1-minute calls
  and 5 at GO; the winners' board keeps each class's last ranked winner (SquareData
  `Winners`; AI regulars count; an unnamed bird shows its colour); a rider's ranked win
  fires five rockets over the plaza.

Not yet seen in a client: all of the above. First visit rebuilds the village (a
few seconds; players are held above the plaza and put back).

## Stands and crowd (2026-09-27, Ahmi: "we need more stands and fans, the tracks feel kind of dead; stands inside and outside, class based, S the most popular; as lag free as possible")

* **Siting** (`race/CourseStands`, pure, shared by server and client): the main
  infield stand on the start straight stays; each class adds more, alternating inside
  / outside the loop: C +2 (3 total), B +4 (5), A +6 (7), S +9 (10). Rows: main C/B 4,
  A 5, S 6; extras C 3-4, B 4, A 4-5, S 5-6; extras are 22 / 26 / 28 / 32 blocks long.
  A site is searched outward from an even spread round the lap and must be off the start
  zone, 12 blocks clear of every terrain feature (connectors included) and boost strip,
  22 of the landmark, on a straight or gentle sweeper (turn <= 0.30, then 0.55 / 0.8
  rad, then a 70 % length), level within 2 blocks, 8 blocks from a stand on the same
  side, and its whole footprint must be > 3 blocks (tile-centre distance, `CLEAR2` 9)
  from **every** road tile of the lap (a circuit folds back on itself) and from other
  stands, the back wall > 6. Road tiles come from `RaceCourseLayout.roadTiles` (the
  band + detour stamps without the block plan), checked on a distance grid. All 24
  courses get their full count (C 2 in / 1 out; B 3 / 2; A 3-4 / 3-4; S 5-6 / 4-5).
* **Stand geometry** uses `CourseStands.lane`, a lane point with the normal blended
  between spline samples: `pointAtLane`'s stepped normal leaves gaps 20 blocks out on
  a bend. `stampGround` widens the island under each stand to back wall + 3 (either
  side), easing back over 6 blocks past the ends so the rim still tapers, and
  `groundUnderStands` fills any column the stamp missed; a stand floor is the highest
  road level alongside it, with a plinth where the ground dips.
* **Looks** (`RaceCourseLayout.buildStand`): C wooden benches (oak / cherry / bamboo by
  theme) under a green and white awning; B / A the quartz stand with yellow / red aisles
  under the yellow awning; S grand stands: theme-kerb aisles, taller back wall, solid
  roof with a kerb trim, flag poles on the roof, wall banners every 2 blocks. Front posts
  carry the lamps; decoration, flags, verge and lamps keep off stands (`sidesAt`,
  `nearStand`).
* **Fans** are `FanPost(x, y, z, yaw, stand)` on seat cells only (never a post or banner
  column), a deterministic scatter weighted to front rows and the middle, >= 3 per stand;
  class targets 40 / 80 / 140 / 220 with a +-10 % per-course wobble. Totals: C 36-43,
  B 72-88, A 129-151, S 198-242.
* **No fan entities any more.** `RaceSession.spawnFans` and its list are gone;
  `SquareBuilder.spawnKeepers` still scrubs strays from old worlds. The crowd is drawn by
  `client/CourseCrowdRenderer` (game bus, `RenderLevelStageEvent` AFTER_ENTITIES), Square
  only, for courses whose island is within reach of the camera; plans are computed off
  the render thread on first approach. Per stand: box distance + frustum cull, a check
  that the stand is built (seat block under the light probe, so an island still on an
  older plan shows no floating fans), one light sample, one "racing birds within 48"
  test (bird positions refreshed twice a second per course). Per fan: 96-block and
  frustum cull, then LOD: < 40 animated (sway, look about; arms up + hop while birds are
  near) with skin, cloak and eye glow; 40-72 one of two precomputed poses, skin + cloak;
  72-96 skin only. Draws grouped by texture (<= 12 render-type switches), kin geometry
  captured once from the baked `KinStewardRenderer.LAYER` and emitted through reused
  matrices (no allocation in the hot loop). Looks: FAN_SWARM / CLOCK / SPROUT / CLAW
  picked per fan from a hash, with a 0.90-1.04 size wobble.
* **`COURSE_VERSION` 8 -> 9.** Tests: `CourseCrowdTest` (per-class stand counts, fan
  ranges, strictly increasing by class, both sides on every course, no stand block
  within 3 of any road tile, every fan on a seat in its stand box facing the road, island
  under every seat column); `RaceTrackTest` no longer pins 8 fans. 172 unit tests green.
* **Needs an in-game look:** crowd facing and hop, cloak / glow alignment on the
  captured mesh, light under the S roofs, wall-banner support on diagonal stands,
  frame time on an S course with the whole crowd in view.

## Old saves convert once (2026-09-27, Ahmi: "have it convert old birds into the new system ... old birds prior to the new breeding/stat system as well")

`ChocoboEntity` writes `SaveFormat` 2. A bird read with a lower (or no) format runs
`convertOldSave` once, then saves as 2: class progress goes through
`RaceScoring.migratedClassPoints` (1 or 2 marks of the old three -> 3 or 6 points of
nine; Class S -> 9; anything else is already points), and a bird that is not a race
NPC or town bird and has no born stats at all (`BreedGenes.blankLine`: all four genes
0, i.e. from before 1.0.15 bloodlines) gets `rollWildBlood()` from its born grade.
Training, wins, colour and grade are untouched. Known overlap: a bird that won 1-2
sprints on 1.0.17 / 1.0.18 also reads as old marks and gets x3. The almanac ledger
follows on the bird's next load (`ledgerUpdate` in `onAddedToLevel`). Tests:
`OldSaveConversionTest` (rules) and GameTest `oldSaveConvertsOnce` (NBT round trip:
converts, then does not convert again).

## Off the road: a set-back, not a lost lap; shortcuts marked (2026-09-27, Ahmi: "going out of bounds, making them repeat the lap and putting them in last place is way too rough ... look at mario kart"; "shortcuts need to have something that shows they are there")

**Before:** `RaceLapProgress.update` zeroed the lap and waited for the next start
crossing the moment a bird was more than a block off any road tile: one wide corner
cost a full lap, i.e. last place.

**Now** (`RaceLapProgress.step` -> `NONE / LAP / RESCUE`): off the road nothing
accrues and the last on-road progress is the anchor. Back on no further round than
anchor + `SKIP_BLOCKS` (10) / lap length: the stretch counts, nothing happens (a wide
line is free). Further round (a shortcut), `OFF_LIMIT_TICKS` (80 = 4 s) off the road,
more than `RaceScoring.STRAY_BLOCKS` (24) from the last road position, a jump over an
eighth of a lap between ticks, or a fall (`squareFallRescue`): `RaceSession.rescue`
sets the bird on the road at the anchor (the detour lane if it does not suit the
feature there), facing up the course, held `RESCUE_HOLD_TICKS` (20) via `raceHeld`,
action bar "Set back on the road. Your lap still counts" + chime. While off, the
action bar counts down the seconds left. Applies to the AI too. The lap is never
forfeited. `update(progress, onCourse)` survives as a thin wrapper (GameTests use it).

**Shortcut markers** (`RaceCourseLayout.shortcutMarkers`, COURSE_VERSION 10): for
every terrain feature, a dashed 3-wide stripe in the feature colour (water light
blue, ridge lime, lava orange, bog brown) down the road centre from 22 blocks before
the fork (`start - DETOUR_CONNECT`) to the feature, skipping boost strips; a gantry 4
blocks before the fork: post on the infield verge (skipped if a fold puts it on
road), glazed-terracotta beam over the road at +8, wall banners facing the riders in
the colours of the breeds that take it straight (`shortcutBanners`: yellow for Gold,
blue, white, black, purple, green, red for Flame; brown for a bog), and a sign
(`ShortcutSign`, text written in `SquareBuilder.buildTrack`, lang
`chocobosreborn.sign.shortcut.<type>.N`). `RaceSession.warnShortcut`: 45 blocks
before each fork, once per lap per feature, the rider's action bar says "Shortcut
ahead! ... stay on the coloured stripe" (with a chime) when the bird suits it, or
"... take the road round the outside". Tests: `RaceLapProgressTest` (rewritten),
`ShortcutMarkerTest`.
## AI pass (2026-09-27, unreleased; Ahmi: "another AI pass for quality and balance")

### Bugs found and fixed

* **Rivals ignored colour.** `rivalPace` got the rider's grade x training only and ran it on
  Teiyo's Black (0.40) / Jolo's land speed, so a Green rider in B met a Teiyo faster than a
  maxed Green (sim: 59 s vs 69.5 s) and a Gold S Jolo beat a maxed Black. Now
  `RaceScoring.rivalPaceAbs` is fed the rider's land speed x grade x training.
* **Field colour spread.** Field birds ran their raw land speed: a Yellow in S was at half a
  Black's pace, never in the race. `RaceScoring.fieldLandSpeed` = class land speed
  (`classLandSpeed`: C 0.20, B 0.27, A 0.375, S 0.42) + a quarter of the colour's edge;
  `RaceSession.spawnField` sets that as the field bird's `paceScale`.
* **Sprints were "the last lap" from GO**: the AI burnt its bar off the grid and into the
  first corner. Now `RacerProfile.finalPush` = the last lap of a GP / last half of a sprint.
* **Boost strips**: each covers one 3-block lane (outside / centre / inside); the inside line
  never touched an outside one. The AI now lines up for a strip at `boostAim`
  (C 45 % .. S 95 %), `RacerLine.aimsForBoost`.
* **Passing** always swung 2.2 outward: an outer bird aimed through the outside rail. Now
  it goes round on the side away from the slow bird unless that side has no room, only when
  the other bird is actually in its lane, and every aimed lane is clamped to ±4.5
  (`RacerLine.LANE_LIMIT`, rail on the kerb at 6.5).
* **Stumbles** rolled per tick on a 900-tick lap: a C grand-prix lap (~3500 ticks) got ~4x
  the class rate. Now per block run (`RacerLine.stumbleChance`).
* **Finished birds** coasted at 0.6x on the racing line (and still dashed). Now they park in
  lane -3.5 and never dash.
* **Dash economy**: the threshold/reserve rule hovered the bar at 30-45 % all race and flickered
  the dash on and off at the threshold. `RacerProfile.wantsDash`: bursts with hysteresis,
  never below 3 % (no lock), no burn into a braking corner unless the bar is plentiful,
  plenty (reserve + 25 %) spent anywhere, the reserve spent in the push. A per-heat
  `withTemper` (front-runner / closer) moves the reserve so the field does not look cloned.
* **Rubber-band remnants removed**: `rubberBand` / `bandFactor`, the player-gap dash trigger
  and `RaceSession`'s playerGap feed are gone; `RacerProfileTest` fails if a profile ever
  grows a band / gap / player component again.
* **Dead fields removed**: `energyDrain` / `energyRecover` and `averageSpeed()` (the AI has
  always spent its bird's own bar). The honest number is now the simulation.

Checked and fine: shortcut choice (`RacerLineTest` walks every colour x every feature on all
24 courses: suited birds straight through, everyone else on the detour, bogs gone round by
savvy birds of any colour), start reaction, detour connectors.

### Numbers (`RacerProfile.of`)

| class | cruise | dash | reserve | reaction | wobble | stumbles/lap | line | hairpin lift | boost aim |
|---|---|---|---|---|---|---|---|---|---|
| C | 0.885 (was 0.880) | 1.24 (1.22) | 0.05 | 14-26 | 0.90 | 0.60 (0.70) | 0.35 | 26 % (30) | 45 % |
| B | 0.885 (0.882) | 1.32 (1.27) | 0.10 | 11-19 | 0.60 | 0.40 (0.50) | 0.60 | 21 % | 65 % |
| A | 0.945 (0.887) | 1.40 (1.32) | 0.15 | 9-14 | 0.35 | 0.20 | 0.80 | 16 % | 85 % |
| S | 0.990 (0.923) | 1.48 (1.36) | 0.20 | 7-11 | 0.15 | 0.05 | 0.95 | 11 % | 95 % |

Rivals: S discipline; cruise = max(`rivalFloor` x field, share x rider's cruise), floor
B 0.86 / A 0.98 / S 1.03 (was 1.06 everywhere), share B 0.92 / A 1.00 / S 1.08 (was 1.00 /
1.05 / 1.10); Jolo 4 % under Teiyo. The floor is under 1 low down because Teiyo's 100-point
stamina and intelligence are worth far more against a B field (48 points) than an S one.

### The ladder (`RaceSimTest`, table in `build/race_sim.txt`)

`RaceSim` (test code, no Minecraft) runs one bird over the real course geometry with the
real stamina rules (pool, drain, intel skip, 1/3 recovery, lock at empty, dash / empty
multipliers, boost strips, detour cost, 5 ticks per block of ridge for climbers) and the AI's
real `wantsDash` / `cornerLift`. "Driven well" = dash anywhere over a quarter bar, straights
below it, never into the lock; a rider lifts 10 % on a hairpin. Mean heat seconds over the
six courses, reference riders Good-born C Yellow / B Green / A, S Black:

| class | field avg | field best | fresh, driven well | fresh, cruising | wins field from | beats Teiyo from |
|---|---|---|---|---|---|---|
| C | 221.7 -> 213.2 s | 214.2 -> 205.9 | 189.1 (W) | 223.3 (L) | fresh | — |
| B | 167.3 -> 146.3 | 144.4 -> 139.0 | 154.6 (L) | 183.9 | 25 training | 50 (1.0.18: never on a Green) |
| A | 132.9 -> 86.8 | 85.5 -> 76.9 | 113.3 (L) | 136.8 | 50 | 75 (1.0.18: 50) |
| S | 124.9 -> 70.9 | 80.5 -> 63.5 | 125.8 (L) | 150.9 | 90 (1.0.18: 75) | maxed (1.0.18: 75, but Gold Jolo beat a maxed Black) |

A maxed Black cruising loses S; a maxed Gold driven well wins S by ~4 % over Teiyo (who keys
off it). The B/A/S "field avg" before is dragged down by raw-speed Yellows, so compare the
field best. `RaceSimTest` pins every line of the ladder, plus "the AI never runs its bar
into the lock" over every course.

### Needs an in-game look

* Boost-strip line-ups (a C bird swinging 4 blocks for an outside pad), passing on the inside,
  finished birds parking in the outside lane.
* The dash now bursts instead of flickering; S birds dash through sweepers when the bar is high.
* Ridges: the sim charges a climber 5 ticks per block to go over (vanilla climb 0.2 b/t). If
  climbing is slower, a Green / Black / White gains nothing from a ridge over its detour.
* Racer `zza` is x0.98 in vanilla `aiStep`, so the AI may run ~2 % under the sim.
* Exotic wild colours (Flame 0.40, Purple 0.45) are twice a Yellow in C: unchanged, by design?

## 48 courses, phase 1 (2026-09-27, unreleased; Ahmi: "Double the amount of tracks for all classes, and the grand prix and sprint size need to be swapped. Sprints are long tracks, 1 time. Grand prix are shorter tracks with more laps"; "some tracks are 3, 4 or 5 laps with more laps likely being shortest. No track should be shorter than current class C sprints")

Phase 1 is the framework and the swap; phase 2 (four class agents in parallel) adds 6
courses per class on top of it. Nothing about course counts is hard-coded any more: a class
has however many rows of the `RaceTrack` table carry it (`ofClass`, `courseCount`,
`sprintsOf`, `grandsPrixOf`, `forClass` clamps to what exists), and sprint or grand prix is
the lap count alone (`isSprint` = 1 lap, `isGrandPrix` = 3-5; `isShort` is gone). Prizes,
points and purses already keyed off `getLaps() > 1` (`RaceSession`, `RacePrizes`,
`RaceScoring.purse`); the fun gates now run course 3 (a sprint) for SHORT and course 0 (a
five-lap grand prix) for LONG (`RaceScoring.funGateCourse`).

### The swap (ordinals unchanged; lap targets in blocks)

| course | before | after | heat | notes |
|---|---|---|---|---|
| C_MEADOW | sprint 600 x 1 | GP 600 x 5 | 3000 | |
| C_ORCHARD | sprint 612 x 1 | GP 660 x 4 | 2640 | lap lengthened |
| C_SHORE | sprint 624 x 1 | GP 760 x 3 | 2280 | lap lengthened; renamed Shore Sprint -> **Shore Grand Prix** |
| C_DOWNS / C_CIDER / C_LAGOON | GP 1150 / 1170 / 1190 x 3 | sprint x 1 | 1150-1190 | |
| B_CANYON | sprint 650 x 1 | GP 650 x 5 | 3250 | |
| B_FORD | sprint 662 x 1 | GP 715 x 4 | 2860 | lap lengthened |
| B_FROST | sprint 674 x 1 | GP 825 x 3 | 2475 | lap lengthened; ridge 0.47-0.51 -> 0.55-0.59 |
| B_MESA / B_RAPIDS / B_GLACIER | GP 1280 / 1300 / 1320 x 3 | sprint x 1 | 1280-1320 | B_MESA renamed Mesa Grand Prix -> **Mesa Dash** |
| A_CRYSTAL | sprint 700 x 1 | GP 700 x 5 | 3500 | |
| A_CANOPY | sprint 712 x 1 | GP 770 x 4 | 3080 | lap lengthened |
| A_EMBER | sprint 724 x 1 | GP 890 x 3 | 2670 | lap lengthened; lava 0.31-0.36 -> 0.27-0.32 |
| A_DEEPS / A_TEMPLE / A_INFERNO | GP 1420 / 1440 / 1460 x 3 | sprint x 1 | 1420-1460 | |
| S_SKYWAY | sprint 750 x 1 | GP 750 x 5 | 3750 | |
| S_KEEP | sprint 762 x 1 | GP 825 x 4 | 3300 | lap lengthened |
| S_VOID | sprint 774 x 1 | GP 950 x 3 | 2850 | lap lengthened |
| S_STARFALL / S_CITADEL / S_MAELSTROM | GP 1560 / 1580 / 1600 x 3 | sprint x 1 | 1560-1600 | |

Every changed course passes `CourseBalanceTest` as it stands (detours 0.55-1.3x target, bog
cheapest, boosts on corner exits clear of connectors). Boost strips go by lap length: **five on
a sprint's long lap, three on a grand prix's short one** — the swap left every course with the
right count, so no strip moved. A grand-prix heat is 2.0-2.6x the class's shortest sprint:
the "up to ~1.6x" in the brief cannot hold with five laps of at least 600 blocks against a
1150-block C sprint, so the heats sit under the old 3-lap grands prix (3450-4800) instead.

**Balance shift to look at** (`RaceSimTest`, `build/race_sim.txt`): a maxed Gold driven well
now beats S Teiyo by ~8.2 % (was ~4 %; the test bound went 6 % -> 9 %), and a 90-trained Black
now beats Teiyo in S (was: maxed only). Every other ladder line holds: C won fresh; B field
from 25 training, Teiyo from 50; A field from 50, Teiyo from 75; S field from 90. Not retuned.

**`COURSE_VERSION` 10 -> 11** (six laps grew, set pieces re-dispatched). `clearChunks` now clears
the whole bounding box of the island (+1 chunk), infield included: a lap that grew was laid
smaller about the same centre, so its old road sits inside the new one.

### Framework

* **Island grid** (`RaceTrack.columnOf` / `slotX` / `slotZ`): class rows stay at z 700 + 900 per
  class; courses 0-5 keep their columns (x = (col - 2.5) x 820, -2050 .. +2050); course 6 goes
  west of column 0 (x -2870), 7 east of column 5 (+2870), 8 at -3690, 9 at +3690, 10 at -4510,
  11 at +4510. `MAX_ISLAND_RADIUS` 385 (half extent, detour and margins included) and
  `MAX_COURSES_PER_CLASS` 12; `RaceTrackTest.theIslandGridHoldsTwelveCoursesPerClass` checks all
  48 slots at the maximum size and every course against it. No existing island moved.
* **Themes**: four new, one per class, each with a palette, verge detail, margin decoration,
  stand wood, music and a set piece (`themeLandmark`): **FARMLAND** (C: coarse-dirt road,
  lime / white kerbs, birch fences, hay rounds, pumpkin patches, birch trees; a scarecrow in a
  pumpkin hat), **SAVANNA** (B: smooth red sandstone road, acacias, terracotta stacks, termite
  mounds; a great flat-crowned acacia), **MUSHROOM** (A: podzol road, mushroom-block kerbs,
  mycelium, huge red and brown mushrooms, froglights; a giant mushroom), **DEEP_DARK** (S:
  deepslate-tile road, sculk, ancient-city pillars with soul lanterns, candles; a warden's frame
  of reinforced deepslate). Music: FARMLAND chocobo_dash, SAVANNA rune_dash, MUSHROOM
  gallop_of_heroes, DEEP_DARK speed_of_the_dragon (`RaceScoring.raceLoopKey(Theme)`, by theme
  now). No course wears them yet: `CourseThemeTest` lays each over two courses of its class that
  between them carry every terrain feature the class races (`RaceCourseLayout.dressedAs`) and
  checks a sealed, dressed island with its set piece; `CourseMapDumpTest` writes those previews
  to `build/track_maps/themes/`.
* **Landmarks** are dispatched per course: `landmark()` -> `landmarkC/B/A/S` (one `case` per
  course, `default` = the theme's set piece), each class followed by its own set-piece methods.
  The original 24 keep exactly the pieces they had. `RaceCourseLayout.landmarkBlocks()` /
  `landmarkShape()` record what the set piece laid; `CourseIdentityTest` now checks every
  signature is laid by the set piece itself, no two courses share a signature or build the
  same set piece, and courses of a theme differ in shape and palette.
* **Course picker** (`client/CourseSelectScreen`, also the duel picker): a tab per class (the
  bird's and below), sprints left and grands prix right, up to six rows each, every button
  "Name · 1 lap · 1150 m" / "Name · 5 laps × 600 m" with the old tooltip (theme, features,
  lower-class note). Fits 640 x 360 (1080p, GUI scale 3) and 480 x 270 with room; anything
  taller scrolls.
* **Words**: almanac Whiskerwind page ("a sprint (one long lap, two minutes or more) or a
  grand prix (three, four or five laps of a shorter circuit ...)"), README, store description
  (+ rendered html), SPEC. The count stays "Twenty-four" until phase 2 lands.
* **Tools**: `tools/track_sheet.py` lays one band per class (sprints row, grands prix row, table
  order, six to a row) plus a theme-preview row; the enum regex takes themes with underscores.
* **GameTest** `squareBuildsCourseAndRunsHeat` runs course 0 (now a 5-lap GP of 600-block laps):
  it asserts three racers have a lap done at tick 2100 (`RaceSession.lapsDone`) instead of three
  finishers. Not run here (GameTests are Ahmi's after merge).
* **Tests**: `RaceTrackTest` (`everyClassHasAsManySprintsAsGrandsPrix`,
  `theOriginalCoursesSwappedFormat`, `sprintsAreOneLongLapGrandsPrixShortLapsMoreLapsShorter`,
  the grid test, and `twelveCoursesPerClass` **@Disabled until phase 2**), `CourseBalanceTest`
  (boosts by format), `CourseIdentityTest`, new `CourseThemeTest` (themes + lang keys for every
  course and theme). 223 unit tests (2 skipped), `build` green.

### How to add a course (phase 2 class agents)

Work only inside your own class's marker regions (`// ---- new X ... (phase 2) begin ----` /
`end ----`); never edit, reorder or rename an existing row, and leave README / store / SPEC
counts, `COURSE_VERSION` and the top of this file to the merge (a new course builds on first
use; nothing existing changes).

1. **Enum row** (`RaceTrack`, your class's block at the end of the table, every row ending in a
   comma): `X_NAME(RaceClass.X, index, Theme.T, Shape.S, lapTarget, laps, features...)`. Indices
   continue 6, 7, 8 = the three sprints (laps 1), then 9, 10, 11 = the three grands prix (5, 4
   and 3 laps, like 0-2). The index is the row's place among the class's rows and places the
   island; a wrong one fails `everyClassHasAsManySprintsAsGrandsPrix`.
2. **Lengths** (`sprintsAreOneLongLapGrandsPrixShortLapsMoreLapsShorter`):
   * sprint lap at least C 1100 / B 1220 / A 1360 / S 1500 (aim C 1150-1250, B 1280-1380,
     A 1420-1520, S 1560-1660) and the island half extent at most 385 (`getRadiusX/Z`);
   * grand prix lap at least 600 and shorter than the class's shortest sprint; within the class a
     GP with more laps never has a longer lap (5 laps <= every 4 <= every 3, +5 slack) against
     the existing ones: C 5-lap 600-660, 4-lap 600-760, 3-lap >= 660; B 5 <= 715, 4 in
     650-825, 3 >= 715; A 5 <= 770, 4 in 700-890, 3 >= 770; S 5 <= 825, 4 in 750-950, 3 >= 825
     (and consistent with each other); the heat (lap x laps) 1.6-3.4x the class's shortest sprint.
3. **Shape** (your class's shape region, one per course, unique silhouette): `p(x, z, hill)`
   in course units, first three points `(0,0) (30,0) (60,0)` (the start straight), last
   `(-30, 0)`, hills 0..12 blocks with the lowest 0 (`circuitsHaveCornersHillsAndWalkableSlopes`:
   total rise 2-12, a real corner somewhere), start straight within 0.14 rad over 40 blocks, legs
   far enough apart at your scale that detours never touch (`legsNeverRunIntoEachOther`: > 38
   blocks with terrain, > 30 open road) — keep legs 22+ units apart and check.
4. **Features**: C boosts only; B exactly one terrain feature (water or ridge; lava is A/S);
   A two or more with a bog (`mud`); S three or more. Spans between 0.05 and 0.95, 0.02 apart,
   terrain on level road (a span where the hill profile is flat), never in the opening stretch.
   Each colour feature's `detourCost` must be 0.55-1.3x `detourTarget()` (2 % of a lap, 13-28)
   and >= 12, the bog the cheapest thing to go round (aim ~45 % of target): slide spans along a
   bend and measure with a scratch test printing `track.detourCost(f)`. Boosts: **five on a
   sprint, three on a grand prix**, `boost(start)` on a corner exit (`turnAhead(start, 45) <
   0.7`), clear of every terrain span by 0.012. `CourseBalanceTest`, `ShortcutMarkerTest`,
   `DetourSteeringTest`, `RacerLineTest`, `CourseIslandTest`, `CourseLiquidTest` and
   `CourseCrowdTest` cover it.
5. **Theme**: at least one new course on your class's new theme (C FARMLAND, B SAVANNA, A
   MUSHROOM, S DEEP_DARK; `twelveCoursesPerClass` wants four themes a class), the rest new shapes
   on existing themes. Add a theme only if a course truly needs one (its region in `Theme`, plus
   `raceLoopKey`, `decorate`, `verge`, `themeLandmark`, stand wood, lang).
6. **Landmark**: in `RaceCourseLayout.landmarkX`, `case X_NAME -> onPlinth(t, surf, this::piece);`
   between your markers, and the `piece(int x, int y, int z)` method in your class's set-piece
   region (it stands on a 7x7 plinth ROAD_HALF + 6 outside the road at about t = 0.5; keep it
   within ~7x7 and under ~16 tall; `archOver` / `halo` are the over-the-road options). The first
   course on a new theme may use the theme's piece (`this::themeLandmark`); any other course
   needs a piece of its own. Add its signature (blocks the piece lays, unique) to
   `CourseIdentityTest.signatureOf` between your markers — the switch must cover every course.
7. **Lang**: `"chocobosreborn.track.<id>": "Name"` between `_anchor.track.x.begin` and
   `_anchor.track.x.end` in `en_us.json` (CRLF). A sprint's name must not say Grand Prix, nor a
   grand prix's Sprint.
8. **Island**: nothing to do; the index places it. Check the grid test.
9. **Run**: `./gradlew test --tests "*Course*" --tests "*RaceTrackTest" --tests "*RaceSim*"
   --tests "*RacerLine*" --tests "*Detour*" --tests "*Shortcut*"` (below-normal priority,
   `--no-daemon -Dorg.gradle.workers.max=1`), then `test`; eyeball with the
   `CourseMapDumpTest` + `python tools/track_sheet.py`. `RaceSimTest` averages every course of
   the class, so new courses move the ladder: keep it green. After all four classes merge,
   remove `@Disabled` from `RaceTrackTest.twelveCoursesPerClass`, bump `COURSE_VERSION` only if
   an existing course changed, and update the README / store / SPEC count.

## Open

1. In-game test pass: the new circuits (hills, kerbs, rails, detours, boost pads,
   bogs, lava), the class-based stands and the drawn crowd, the rebuilt village, saddle combat,
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

### Phase 2: class C courses

Six new class C courses (rows 6-11 of the C block, open road and boosts only, as class C
always is). The fun is in the silhouette, rolling hills (up to 8 blocks on Harvest Hills)
and gentle, flowing corners. Every new course has its own set piece.

| id | name | theme | shape | lap x laps | features | landmark |
|---|---|---|---|---|---|---|
| c_harvest | Harvest Hills | FARMLAND | SCYTHE (handle straight, blade sweeping up and back to its tip; hills to 8) | 1200 x 1 | 5 boosts (0.16, 0.30, 0.57, 0.66, 0.78) | scarecrow in a pumpkin hat (the FARMLAND piece) |
| c_honeycomb | Honeycomb Trail | ORCHARD | HONEYCOMB (three hex cells, twelve sides, climbing cell to cell) | 1160 x 1 | 5 boosts (0.14, 0.31, 0.48, 0.64, 0.81) | honeycomb tower with hives, honey crown, potted flowers |
| c_scallop | Scallop Sands | SHORE | SCALLOP (hinge and two ears on the start, ribbed rim over the top) | 1240 x 1 | 5 boosts (0.17, 0.32, 0.43, 0.61, 0.76) | sandcastle: four turrets, keep, flag |
| c_heartfield | Heartfield Grand Prix | MEADOW | HEART (point just before the line, two lobes, the dip) | 630 x 5 | 3 boosts (0.36, 0.71, 0.93) | striped hot-air balloon moored on a post |
| c_kite_hill | Kite Hill | MEADOW | KITE (diamond up to its tip, a tail of bows back to the line) | 700 x 4 | 3 boosts (0.21, 0.46, 0.93) | kite with birch spars and a tail, flying on a chain |
| c_horseshoe | Horseshoe Farm | FARMLAND | HORSESHOE (heels up: round the toe, over one heel, down the inside, over the other) | 800 x 3 | 3 boosts (0.15, 0.36, 0.76) | red barn with a hay loft and a copper-capped silo |

Signatures in `CourseIdentityTest`: carved pumpkin + z hay (scarecrow), honeycomb + honey
block, chiseled sandstone + sandstone wall, red + yellow wool (balloon), chain + light blue
wool (kite), red terracotta + waxed cut copper (barn and silo).

**Outside the C markers** (small, needed by every class branch; the merge should keep one copy):
* `build.gradle`: the test JVM gets `maxHeapSize = '2g'`. Every course's plan is cached while
  the course tests run and 30 islands already ran the default 512 MB heap out of memory; 48
  will need it too.
* `RaceTrackTest.everyClassHasAsManySprintsAsGrandsPrix`: each class may have 6 or 12 courses
  on its own while the class branches land one at a time (it demanded every class match class
  C). `twelveCoursesPerClass` still holds all four to 12 once it is enabled.

**Shape notes**: the honeycomb's bottom edge is a little longer than the other eleven: the main
stand runs about 50 blocks up the start straight on the inside, and a hex corner inside that
put the stand's back wall over its own seats (`CourseCrowdTest`). The heart's point is the
sharpest corner of the six (a hairpin-ish V onto the start straight, boost on the exit).

**Ladder** (`RaceSimTest`, all green, no expectation moved; phase 1 -> with these six): C
field avg 197.7 -> 202.0 s, field best 190.9 -> 195.1, fresh Good Yellow driven well 174.7 ->
178.8 (still wins, by 8.4 % vs 8.5 %), cruising 206.8 -> 211.7 (still loses to the field
average, by 4.8 % vs 4.6 %). The means rise about 2 % because the new heats are a little longer
on average (the new grands prix run 2400-3150 blocks).

### Phase 2: class B courses

Six new class B courses (indices 6-11), one terrain feature each, three water and three ridge, so
a Blue and a Green rider each get three that suit them (the class as a whole: six water, six ridge).
SAVANNA gets three courses, CANYON, RIVER and SNOW one more each (three a theme).

| id | name | theme | shape | lap x laps | features | landmark |
|---|---|---|---|---|---|---|
| B_ACACIA | Acacia Run | SAVANNA | TUSK (crescent: climbing outer sweep, hairpin tip, concave run home) | 1340 x 1 | water 0.51-0.54 on the tip hairpin (25.5 / 26.8, 0.95x); boosts 0.14 0.36 0.58 0.69 0.80 | great acacia (the theme piece) |
| B_GULCH | Arrowhead Gulch | CANYON | ARROWHEAD (two flanks to a point, notched tail) | 1320 x 1 | ridge 0.66-0.70 round the lower barb (28.1 / 26.4, 1.06x); boosts 0.14 0.33 0.50 0.73 0.82 | balanced rock (boulder on a sandstone neck) |
| B_OXBOW | Oxbow Bend | RIVER | OXBOW (pinched meander loop, falling diagonal home) | 1360 x 1 | water 0.20-0.25 on the first bend (26.6 / 27.2, 0.98x); boosts 0.36 0.52 0.65 0.79 0.87 | fisher's stilt hut |
| B_BAOBAB | Baobab Loop | SAVANNA | LOZENGE (rhombus) | 680 x 5 | water 0.78-0.825 (13.3 / 13.6, 0.98x); boosts 0.21 0.44 0.69 | baobab |
| B_KOPJE | Kopje Circuit | SAVANNA | HEATER (heater shield) | 760 x 4 | ridge 0.70-0.745 on the flank (15.2 / 15.2, 1.00x); boosts 0.25 0.59 0.93 | kopje (granite boulder pile) |
| B_SNOWCAP | Snowcap Ring | SNOW | MITTEN (round hand, thumb out the side) | 860 x 3 | ridge 0.40-0.435 (16.7 / 17.2, 0.97x); boosts 0.21 0.65 0.93 | snowman in a top hat |

Detour costs are `detourCost / detourTarget`. Sprints stay above B_MESA (1280), so the
class's shortest sprint and every existing length rule are unchanged; grand prix laps
5-lap 680 <= 4-lap 760 <= 3-lap 860. Legs 49-64 blocks apart, islands at most 249 x 199.

**Outside the markers** (both small, both needed by any class that lands alone):
* `build.gradle`: test `maxHeapSize = '2g'`. `RaceCourseLayout.CACHE` keeps every layout, and
  30 courses already ran the 512m default test JVM out of heap (OOM in `CourseCrowdTest`).
* `RaceTrackTest.everyClassHasAsManySprintsAsGrandsPrix`: the count is checked per class
  (6 or 12 each) instead of "every class as many as C", which cannot hold while the classes
  merge one at a time; `twelveCoursesPerClass` still pins 12 each once it is enabled.

**RaceSim** (B, mean heat s, phase 1 -> with these six): field best 126.5 -> 129.3, fresh
Green driven well 139.8 -> 143.0 (still loses), 25-trained 122.1 -> 125.0 (still wins the
field), 50-trained vs Teiyo 94.0 / 99 -> 96.2 / 101 (still wins). Every ratio moves under
0.3 %; no expectation changed.

### Phase 2: class A courses

Six new class A courses (indices 6-11), two on the new MUSHROOM theme. Every silhouette is new, every set piece is new except A_TOADSTOOL's, and every colour detour sits inside 0.55-1.3x the course target with the bog the cheapest way round. Detour costs in blocks are given against each course's target.

| id | name | theme | shape | lap x laps | features (detour cost / target) | landmark |
| --- | --- | --- | --- | --- | --- | --- |
| A_TOADSTOOL | Toadstool Rise | MUSHROOM | TOADSTOOL (bulb foot, stem climb, domed cap) | 1480 x 1 | ridge 0.29-0.33 (23/28), bog 0.47-0.51 (11), water 0.66-0.70 (26); 5 boosts | giant mushroom (the theme piece) |
| A_AMMONITE | Ammonite Coil | CAVERN | AMMONITE (one coil into a hairpin at its heart and back out) | 1440 x 1 | ridge 0.36-0.41 (19/28), ridge 0.49-0.52 (26), bog 0.83-0.86 (7); 5 boosts | fossil coil: bone in an upright calcite slab |
| A_FORGE | Anvil Forge | NETHER | ANVILHORN (foot, waist, face, long horn) | 1500 x 1 | lava 0.30-0.33 (21/28), bog 0.46-0.49 (8), lava 0.64-0.67 (24); 5 boosts | iron anvil over a magma hearth |
| A_GROTTO | Blindfish Grotto | CAVERN | BLINDFISH (head, tail stock, two tail lobes) | 760 x 5 | bog 0.15-0.18 (14/15), water 0.38-0.41 (17), water 0.66-0.69 (15); 3 boosts | eyeless cave fish on a basalt stalk |
| A_MOONSHELF | Moonshelf Hollow | MUSHROOM | SHELFCAP (outer arc, deep concave inner arc, two horns) | 800 x 4 | ridge 0.18-0.22 (18/16), lava 0.34-0.38 (17), bog 0.58-0.63 (8); 3 boosts | dead trunk ringed with glowing shelf fungi |
| A_MACHETE | Machete Cut | JUNGLE | MACHETE (edge, point, spine, handle, pommel) | 950 x 3 | ridge 0.19-0.22 (16/19), bog 0.44-0.47 (11), water 0.64-0.67 (19); 3 boosts | machete driven into a jungle stump |

Colours: ridge-heavy (AMMONITE), lava-heavy (FORGE), water-heavy (GROTTO), mixed on the rest. Grand prix laps: 5 laps 760 <= 4 laps 800 <= 3 laps 950, all under the 1420 shortest sprint; heats 2.0-2.7x.

Outside the markers: `build.gradle` test `maxHeapSize = '2g'` (the 30+ course tests ran out of the default heap; the same change as class B); `RaceTrackTest.everyClassHasAsManySprintsAsGrandsPrix` takes 6 or 12 courses per class while the classes merge one at a time. `RaceSimTest` untouched: the A ladder holds (field from 50 training, Teiyo from 75; A field best 67.7 -> 70.1 on the sim's scale).

### Phase 2: class S courses

Six new S courses (indices 6-11), built to the "How to add a course" recipe. Every one has four
terrain features on a sprint or three on a grand prix, always with a bog. The detour costs are
solver output (a scratch designer that measures `detourCost` on the spline), not guesses.

| id | name | theme | shape | lap x laps | features (detour / target, blocks) | landmark |
| --- | --- | --- | --- | --- | --- | --- |
| S_ABYSS (6) | Abyssal Spiral | DEEP_DARK | NAUTILUS: one coil spiralling in and down 8 blocks to a hairpin at the heart, then back out between its own turns | 1620 x 1 | water 19, ridge 22, water 25 (the pool at the heart), bog 11 / 28; 5 boosts | warden frame (the DEEP_DARK theme piece; first course on the theme) |
| S_ZENITH (7) | Zenith Comet | END | COMET: a long straight tail into a huge round head climbing 9 blocks, a hairpin at the tail tip | 1600 x 1 | ridge 17, water 19 (on the head), bog 9, ridge 28 (tail hairpin) / 28; 5 boosts | comet: a glowstone head floating 11 up, trailing a white and light-blue glass tail down to the plinth |
| S_BASTION (8) | Star Bastion | KEEP | STARFORT: five arrowhead bastions on straight curtain walls, climbing to 7 at the top bastion | 1580 x 1 | water 28, ridge 28, bog 9 (on a curtain), water 28, all on bastion tips / 28; 5 boosts | belfry: a blackstone tower, a bell hung in its open top, a spire and a soul lantern |
| S_ORBIT (9) | Ringed Orbit | SKYWAY | SATURN: a round planet with its ring poking out either side as two hairpin fingers | 790 x 5 | water 16, water 17 (on the planet's arcs), bog 12.5 (on a ring) / 16; 3 boosts | ringed planet: gold and orange bands on end rods, a tilted ring of light-grey glass |
| S_ECLIPSE (10) | Eclipse Crescent | SKYWAY | CRESCENT_MOON: a fat outer arc, a concave inner one and two blunt horns | 880 x 4 | ridge 17.5, bog 8 (in the hollow of the moon), ridge 14 / 17.6; 3 boosts | eclipse: a black disc with an ochre-froglight corona on a quartz column |
| S_RIFT (11) | Sculk Rift | DEEP_DARK | FISSURE: a block of ground split by a jagged crack that zigzags 8 blocks down and back out | 1000 x 3 | ridge 20, water 20 (flooded bottom of the crack), ridge 20, bog 10 / 20; 3 boosts | rift shards: two deepslate shards leaning apart over sculk, a shrieker (cannot summon) and candles between them |

* **Which colours they favour**: ABYSS, BASTION and ORBIT lean Blue (two waters), ZENITH, ECLIPSE and
  RIFT lean climbers (two ridges). **No lava on the new S courses**, on purpose: Flame does not race,
  so in S a lava feature pays only Gold (and Gold Jolo). With lava on three of them (the first draft:
  the comet head, a bastion tip, the bottom of the rift) the maxed-Gold-vs-Teiyo margin went to 8.7 %,
  against the 9 % bound. The KEEP lava stays on S_KEEP / S_CITADEL.
* **The S ladder** (`RaceSimTest`, mean heat over the class's courses; six courses before, twelve after):
  field average 61.1 -> 61.2 s, field best (Black, +2.5 % form) 54.9 -> 54.5 s; half-trained Black
  75.4 -> 75.6 s (still loses); 90-trained Black 51.6 -> 51.1 s (still wins the field, by 6.2 %, was 6.0 %);
  maxed Black 48.6 -> 47.8 s and Teiyo against it 50.7 -> 50.2 s (the Black beats Teiyo by 4.8 %, was
  4.3 %); maxed Black cruising 75.7 -> 75.3 s (still loses); Jolo 3.5 % -> 4.3 % behind Teiyo; maxed Gold
  36.3 -> 35.7 s, Teiyo 8.2 % -> 8.4 % behind it (bound 9 %). Every ladder line holds; no expectation
  moved, nothing in the AI retuned. Sprints are where a well-driven bird gains most on Teiyo (the Black
  beats him by 7-12 % on the three new sprints, 3-4 % on the new grands prix).
* The SKYWAY road is rainbow concrete in 4-block stripes counted from t = 0, and the start arrow is
  yellow: on a SKYWAY sprint of 1500-1700 blocks the arrow always lands on or next to the yellow stripe
  (S_STARFALL already does; `startArrowIsABlockArrowOnEveryCourse` caught the comet at 1600). So the
  comet is an END course and the two SKYWAY courses are the short-lap grands prix.
* Outside the class markers: `build.gradle` test task `maxHeapSize = '2g'` (48 courses of layouts
  overflow the default heap) and `RaceTrackTest.everyClassHasAsManySprintsAsGrandsPrix` lets each class
  have 6 or 12 courses while the classes merge one at a time (same change as the B and C agents).
* Needs an in-game look: the spiral's descent into the heart (8 blocks over ~700 blocks of road, the
  features sit on short level terraces), the star fort's bastion-tip features (hairpin corners with a
  pool or ridge on the point), and the comet's floating head.

### Phase 2 merged (2026-09-28)

All four class branches merged into main (C, B, A, S); 48 courses, twelve a class (6 sprints,
6 grands prix), every class on four themes. `twelveCoursesPerClass` is enabled and
`everyClassHasAsManySprintsAsGrandsPrix` now demands 12 a class. README / store (html
re-rendered) / SPEC say forty-eight; sprint laps 1150-1620, grand-prix laps 600-1000.
`COURSE_VERSION` stays 11 (no existing course changed in phase 2; new islands build on first
use). Gates: 224 unit tests (1 skipped, AtlasTint), all 28 GameTests, build green. Unreleased;
it ships with the AI pass and the Flame/Purple racing ban as the next version.

Open before release: an in-game look at the tight corners the class agents flagged (C Heart
point and Horseshoe heels, A Ammonite inner coil and Grotto tail, S Abyssal Spiral terraces,
Star Bastion tips, the floating comet head); S ladder: a maxed Gold driven well beats Teiyo by
~8.4 % (test bound 9 %), 90-trained Black wins S; no new S course carries lava (only Gold would
gain in S now that Flame does not race).

## Racer collision (kart bumps) (2026-09-28, unreleased, branch `racer-collision`; Ahmi: "Racers need collision, being able to phase through the other racers makes it less fun or challenging. This will make it more fair and complicated." Chose **kart bumps** over a solid wall or a soft vanilla push)

### The model (`race/RacerContact`, pure, `RacerContactTest`)

Two racers touch when their centres are under `REACH` 1.6 blocks apart (the bird box is 1.75)
and within `HEIGHT` 1.5 in height (one over a ridge passes over). The resolving bird looks at
where the other one sits against its own heading (a 50-degree cone, `CONE` 0.64):

| contact | this bird | numbers |
|---|---|---|
| rear-end (other ahead) | takes out all of the closing speed, bounces back 30 % x weight ratio of it, loses pace | loss = ratio x (0.10 + 0.5 x closing), cap 0.25, fading linearly over 15 ticks (`Slow`); a 20 % bump costs ~1.6 ticks of running, ~2 blocks at a gallop |
| front (other behind) | a nudge on along its own heading only, no pace loss | 0.25 x 1.3 x closing x share, at most 0.06 b/t |
| side | pushed apart square to its heading, pace kept | ratio x (0.12 + 0.6 x lateral closing) when closing > 0.03 b/t |
| head-on | the other bird is ahead of both: both take the rear-end | |
| any overlap | eased out of it | 0.15 x overlap, at most 0.1 b/t |

A tick's shove is capped at 0.45 b/t (`MAX_IMPULSE`); friction (0.546 a tick on the ground)
turns that into at most ~1 block of drift. Rubbing at the same pace is a rub, not a bump (no
pace loss). A clean pass (the AI swings 2.4 out) never comes within reach: it costs nothing.

**Weight**: 1, +0.5 dashing, +0.5 on a boost pad (`RacerContact.weight`); every effect scales
with `ratio` = 2 x other / (self + other), 1 at equal weight. So a dashing or boosted bird shoves
harder and is shoved less, and a dasher rear-ending pays less than a plain bird would. Class,
grade and stats are left out on purpose: speed already enters as closing speed.

**Rail / void guard** (`Guard`, `guardLateral`): the sideways part of a shove never carries a bird
past `RacerLine.LANE_LIMIT` (4.5, half a block of air before the rail on the kerb at 6.5; drift
counted as dv / (1 - 0.546)); inward is always allowed; off the road band (|lane| > 5.5: a detour,
a connector, a set-back) there is no sideways shove at all, only the bounce along the road. Nothing
is ever vertical. Vanilla pushing stays off for racers (`isPushable` false via `squareProtected`), so
nothing doubles up.

**Exempt (ghosts)** (`RacerContact.solid`, `ChocoboEntity.contactSolid`): solid = racing, not held,
not a ghost. So birds on the grid (held), during a set-back hold, finished AI (set ghost at the line,
parking in lane -3.5), finished and forfeited riders (racing is already cleared), and a set-back bird
for `RESCUE_GHOST_TICKS` (40) after its hold **and until it is clear of every solid racer**
(`RaceSession.touchesRacer`) all pass through the field. Grid stalls are 1.8 apart, more than the
1.6 reach (`theGridIsWiderThanTheContactReach`). Synced flags: new `DATA_CONTACT` int (bit 1 ghost,
bit 2 dashing).

### Netcode: each bird is resolved by whoever simulates it

`ChocoboEntity.travel` -> `applyRacerContact` runs only where the bird is simulated: the server for AI
birds (after `RacerMoveControl` set the speed), the **driving client** for a rider's own bird (after
`getRiddenSpeed`); remote birds on a client return early (lerped) and a rider's bird on the server
never travels. It changes only this bird's own velocity (the shove) and its own speed setting (the
fading pace loss). Nobody moves another racer, and the server gets the rider's bumped position as an
ordinary vehicle move, so there is nothing to correct: impulses are at most 0.45 b/t against vanilla's
10-block per-step check, and entity-to-entity contact is not part of the server's collision replay.
The zero-correction result should hold; the hub harness has to confirm it.

What each side knows of the others: velocities come from positions (`contactVx/Vz`, smoothed, teleports
ignored), since a rider's bird has no server velocity and remote birds have none on a client. Weight:
the driving client knows its own dash (the predicted frame) and pad (`localBoostTicks`); everyone else
goes by the synced dash flag (AI: `RacerGoal`; a rider: set from each acknowledged input frame in
`drainRaceInputs`) and `DATA_BOOST`. **Lead**: a remote bird is drawn half a round trip plus the lerp
(~2 ticks) behind the server, and the server sees this client's bird half a round trip late, so the
driving client leads every remote bird by its velocity x `leadTicks(rtt)` = 2 + rtt / 50 ms, capped at
10 (host 2, 150 ms 5, 300 ms 8). That compares the same moment the server compares for its AI. RTT is
vanilla's player-list latency (`CLIENT_RTT_MS`, installed by the client mod; it updates slowly).

### AI (`RacerGoal.traffic`, helpers in `RacerLine`, tests in `RacerLineTest`)

Skill = the profile's `lineHold` (C 0.35, B 0.60, A 0.80, S and the rivals 0.95). Scans solid racers
within 12 blocks every 5 ticks; positions along the road by lap progress (`blocksAhead`), lanes at the
other bird's own progress.
* **A bird ahead in the lane we aim for** (within `trafficLook` = 2 + 4 x skill + 25 x skill x closing
  blocks): pass on the side with room (`passSide`), or the other side if a bird alongside holds that
  lane; a pass now wins over a boost strip's lane. Both sides boxed in and about to touch its tail:
  match its pace (`followScale`, 0.5..1, never faster than its own pace). Rolled once per bird met:
  `trafficMiss` = 0.5 x (1 - skill), so a C bird drives into the tail about one time in three (30 ticks),
  an S bird one in forty.
* **A bird alongside** (within 2.5 blocks along the road): never aim nearer than `sideClear`
  (1.7 + 0.3 x skill) on its side (`keepClear`); C wobble still rubs sometimes.
* **Defend**: a leader with a faster bird up to 6 blocks behind and lining up on the inside (1.6-3.5
  blocks in) drifts up to 1 x skill blocks across (`defendLane`), holds it 40 ticks, then not again for
  80: one move, never a weave. Only on the road band.
* Speeds of the others come from `contactVx/Vz`: the old pass check compared server velocities, and a
  rider's bird has none on the server, so the AI tried to pass every rider it met.
`RaceSimTest` is single-bird and does not run `RacerGoal`: no ladder line moved. No rubber-banding
(`RacerProfileTest` still passes; nothing new looks at the player).

### Harness (`harness/RaceHarnessClient.input`)

Each bot takes its lane from its grid stall while held (clamped to 4.5) and drives it instead of the
centre line, and swings `PASS_OFFSET` round a solid racer 1-10 blocks ahead within 2 blocks of its lane
(sticky 40 ticks). Output files and formats are unchanged. This changes the bots' lines, so finish-time
comparisons against older reports are not like for like.

### Tests

`RacerContactTest` (15: apart, clean overtake, rear-end, harder rear-end up to the cap, tailgating rub,
side-by-side, head-on at 45 degrees, dash/boost weight, ghosts/held, grid wider than reach, clamp,
never past the lane limit, no sideways shove off the band, the fade, the lead); `RacerLineTest` +5
(traffic skill C..S and the rivals, blocksAhead, keepClear, followScale, defend). GameTest
`twoAiBirdsInOneLaneNeverOverlapAndBothLap` (batch `ai_contact`, C_MEADOW): a 1.3x-pace AI bird six
blocks behind a 1.0x one in the same lane; centres never under 0.9, the two meet (closest under 4.5)
and both earn a full lap. Results: 244 unit tests (1 skipped, AtlasTint), all 29 GameTests, `build` green. The GameTests ran twice: the first run was on a build from before the last traffic fix (a bird nose-to-tail within 2.5 blocks now counts as ahead, not alongside, and a faster bird ahead is no obstacle); the second, on the committed code, also passed.

### Known limits

* **Anti-cheat**: a modified client can ignore bumps (it simulates its own bird). The server does not
  check it; any check that corrects an honest client would bring corrections back. A possible later
  check: count server-side overlaps of a rider's bird with AI birds that never produced a velocity
  drop, log only.
* The two sides judge from different views: the server sees a rider half a round trip late, the rider
  sees remote birds led by an estimate. Straight-line running leads well; a bird turning or braking
  inside the lead window can give a bump the other side did not see (or miss one). Rider vs rider:
  each client leads by its own RTT only, so the error is about the sum of the two half-trips.
* A remote bird is still drawn where the lerp puts it, so at high ping a guest can be bumped by a bird
  drawn a little behind the contact point.
* The RTT source is vanilla's player-list latency, which refreshes slowly.

### Needs an in-game / hub-harness look

* **Corrections**: the hub harness (three clients, 150 / 300 ms + jitter) must still report zero vehicle
  corrections with the bots now in lanes and bumping.
* Feel: is a rear-end noticeable but not brutal (10-25 % for ~0.75 s), is a side rub felt without
  knocking anyone off, is a dasher's shove visibly harder?
* Narrow spots: detour roads and connectors (no sideways shove there), ridges, the tight corners the class
  agents flagged (C Heart point, Horseshoe heels, A Ammonite coil, S Abyssal Spiral terraces, Star Bastion
  tips).
* The start: six birds 1.8 apart at GO, then the AI blending to the inside line with `keepClear`.
* AI: C birds bumping about one pass in three, S and the rivals clean, leaders covering the inside once
  without weaving, nobody stuck behind a slower bird for long.
* Ghosts: a set-back bird dropped into traffic, finished AI parking, a finished rider coasting.

## Points by distance (2026-09-28, unreleased, branch `points-balance`; Ahmi: "Now that we have swapped the sprints and grand prix, further balance the point system for ranks, since some grand prix are 3, 4, 5 laps etc. Assign the values based off maybe distance or something fair")

### The rule (`RaceScoring.winPoints`, `RaceTrack.winPoints()`)

* A ranked first place is worth **round(4 x heat length / the class's shortest sprint)** points.
  Heat length is `raceLength()` (lap x laps); the reference is derived from the table
  (`RaceScoring.referenceLength`: min `raceLength()` over `sprintsOf(rc)`, cached), never hard-coded.
* Every sprint is **4** (pinned; a class's longest sprint is under 1.125x its shortest, so the pin
  never changes a value today). A grand prix scores by its heat and never under 4.
* **36 promote** (`RaceClass.POINTS_TO_PROMOTE`, the one constant; `SPRINT_POINTS` /
  `GRAND_PRIX_POINTS` are gone): nine sprint wins, as before, or four or five grand prix wins today.
  Spare points do not carry over. Class S is the top (shows 36, earns nothing more). Below-class
  racing, duels and unranked heats still earn no points; `raceWins` (breeding) still counts every
  ranked first place as 1.
* **Purse by the same ratio** (`RaceScoring.purse(RaceTrack)` = `basePurse(rc)` x points / 4,
  rounded; base C 6 / B 12 / A 24 / S 48): a sprint pays the base, a grand prix 1.75-2.8x it today.
  `RacePrizes.gp(track, place, ranked)` still pays 1st / 2nd / 3rd full / half / quarter, unranked
  half, below-class half again (`RaceSession`).

### The table today (`WinPointsTest.writeTheTable`, `build/win_points.txt`)

Generated from the current course table. **It changes when the short-GP rework lands** (grand prix
laps cut to a share of the shortest sprint, heats ~1.15-1.6x a sprint): the rule stays, the grands
prix then land at 5-6 points (6-8 wins to promote) and purses of 1.25-1.5x the base. x ref = heat /
shortest sprint; wins = grand prix wins to promote from zero; purse = first place (old = 1 / 3x
base); secs = the class favourite at its best form in `RaceSim` (the pace a winner beats); marks =
five-minute heat marks the heat ties up (13 s hold + heat + ~30 s back to Esther + 10 s last call).

| course | format | heat | x ref | pts | wins | purse | old | secs | marks |
|---|---|---|---|---|---|---|---|---|---|
| C_DOWNS | sprint | 1150 | 1.00 | 4 | 9 | 6 | 6 | 114 | 1 |
| C_CIDER | sprint | 1170 | 1.02 | 4 | 9 | 6 | 6 | 117 | 1 |
| C_LAGOON | sprint | 1190 | 1.03 | 4 | 9 | 6 | 6 | 117 | 1 |
| C_HARVEST | sprint | 1200 | 1.04 | 4 | 9 | 6 | 6 | 119 | 1 |
| C_HONEYCOMB | sprint | 1160 | 1.01 | 4 | 9 | 6 | 6 | 116 | 1 |
| C_SCALLOP | sprint | 1240 | 1.08 | 4 | 9 | 6 | 6 | 125 | 1 |
| C_SHORE | 3 x 760 | 2280 | 1.98 | 8 | 5 | 12 | 18 | 230 | 1 |
| C_HORSESHOE | 3 x 800 | 2400 | 2.09 | 8 | 5 | 12 | 18 | 242 | 1 |
| C_ORCHARD | 4 x 660 | 2640 | 2.30 | 9 | 4 | 14 | 18 | 268 | 2 |
| C_KITE_HILL | 4 x 700 | 2800 | 2.43 | 10 | 4 | 15 | 18 | 283 | 2 |
| C_MEADOW | 5 x 600 | 3000 | 2.61 | 10 | 4 | 15 | 18 | 299 | 2 |
| C_HEARTFIELD | 5 x 630 | 3150 | 2.74 | 11 | 4 | 17 | 18 | 311 | 2 |
| B_MESA | sprint | 1280 | 1.00 | 4 | 9 | 12 | 12 | 76 | 1 |
| B_RAPIDS | sprint | 1300 | 1.02 | 4 | 9 | 12 | 12 | 78 | 1 |
| B_GLACIER | sprint | 1320 | 1.03 | 4 | 9 | 12 | 12 | 80 | 1 |
| B_ACACIA | sprint | 1340 | 1.05 | 4 | 9 | 12 | 12 | 81 | 1 |
| B_GULCH | sprint | 1320 | 1.03 | 4 | 9 | 12 | 12 | 79 | 1 |
| B_OXBOW | sprint | 1360 | 1.06 | 4 | 9 | 12 | 12 | 82 | 1 |
| B_FROST | 3 x 825 | 2475 | 1.93 | 8 | 5 | 24 | 36 | 153 | 1 |
| B_SNOWCAP | 3 x 860 | 2580 | 2.02 | 8 | 5 | 24 | 36 | 161 | 1 |
| B_FORD | 4 x 715 | 2860 | 2.23 | 9 | 4 | 27 | 36 | 176 | 1 |
| B_KOPJE | 4 x 760 | 3040 | 2.38 | 10 | 4 | 30 | 36 | 185 | 1 |
| B_CANYON | 5 x 650 | 3250 | 2.54 | 10 | 4 | 30 | 36 | 195 | 1 |
| B_BAOBAB | 5 x 680 | 3400 | 2.66 | 11 | 4 | 33 | 36 | 205 | 1 |
| A_DEEPS | sprint | 1420 | 1.00 | 4 | 9 | 24 | 24 | 43 | 1 |
| A_TEMPLE | sprint | 1440 | 1.01 | 4 | 9 | 24 | 24 | 44 | 1 |
| A_INFERNO | sprint | 1460 | 1.03 | 4 | 9 | 24 | 24 | 44 | 1 |
| A_TOADSTOOL | sprint | 1480 | 1.04 | 4 | 9 | 24 | 24 | 44 | 1 |
| A_AMMONITE | sprint | 1440 | 1.01 | 4 | 9 | 24 | 24 | 45 | 1 |
| A_FORGE | sprint | 1500 | 1.06 | 4 | 9 | 24 | 24 | 46 | 1 |
| A_EMBER | 3 x 890 | 2670 | 1.88 | 8 | 5 | 48 | 72 | 84 | 1 |
| A_MACHETE | 3 x 950 | 2850 | 2.01 | 8 | 5 | 48 | 72 | 88 | 1 |
| A_CANOPY | 4 x 770 | 3080 | 2.17 | 9 | 4 | 54 | 72 | 92 | 1 |
| A_MOONSHELF | 4 x 800 | 3200 | 2.25 | 9 | 4 | 54 | 72 | 100 | 1 |
| A_CRYSTAL | 5 x 700 | 3500 | 2.46 | 10 | 4 | 60 | 72 | 100 | 1 |
| A_GROTTO | 5 x 760 | 3800 | 2.68 | 11 | 4 | 66 | 72 | 111 | 1 |
| S_STARFALL | sprint | 1560 | 1.00 | 4 | - | 48 | 48 | 32 | 1 |
| S_CITADEL | sprint | 1580 | 1.01 | 4 | - | 48 | 48 | 33 | 1 |
| S_MAELSTROM | sprint | 1600 | 1.03 | 4 | - | 48 | 48 | 33 | 1 |
| S_ABYSS | sprint | 1620 | 1.04 | 4 | - | 48 | 48 | 32 | 1 |
| S_ZENITH | sprint | 1600 | 1.03 | 4 | - | 48 | 48 | 34 | 1 |
| S_BASTION | sprint | 1580 | 1.01 | 4 | - | 48 | 48 | 31 | 1 |
| S_VOID | 3 x 950 | 2850 | 1.83 | 7 | - | 84 | 144 | 68 | 1 |
| S_RIFT | 3 x 1000 | 3000 | 1.92 | 8 | - | 96 | 144 | 71 | 1 |
| S_KEEP | 4 x 825 | 3300 | 2.12 | 8 | - | 96 | 144 | 76 | 1 |
| S_ECLIPSE | 4 x 880 | 3520 | 2.26 | 9 | - | 108 | 144 | 80 | 1 |
| S_SKYWAY | 5 x 750 | 3750 | 2.40 | 10 | - | 120 | 144 | 87 | 1 |
| S_ORBIT | 5 x 790 | 3950 | 2.53 | 10 | - | 120 | 144 | 78 | 1 |

Per class today: sprints 4 points everywhere; grands prix C 8-11, B 8-11, A 8-11, S 7-10 (S does
not promote). Wins to promote by grand prix: 5 on the 3-lap courses, 4 on the 4- and 5-lap ones.

### Economy check (numbers from the table)

* **Per minute of racing, the formats now pay the same**: mean GP per racing minute, sprints vs
  grands prix, C 3.1 / 3.1, B 9.1 / 9.4, A 32.5 / 34.4, S 89.1 / 81.2 (S grand prix laps carry
  more features per block, so they run a little slower). The old flat 3x paid grands prix ~30 %
  more per minute (C 4.0, B 12.2, A 45.5, S 113.5).
* **Per hour on the five-minute timetable a grand prix still pays about twice a sprint**, because
  every heat but the four longest C grands prix fits in one mark: C 72 vs 109 GP/hr (the 4- and
  5-lap C grands prix run 268-311 s and take two marks), B 144 vs 336, A 288 vs 660, S 576 vs 1248.
  The long grand prix is the better pay, as asked; it is just no longer 3x.
* **At the short-GP rework (~1.3x a sprint, estimated at today's GP pace per block)**: 5 points,
  8 wins, purse C 8 / B 15 / A 30 / S 60, one mark: GP per racing minute stays level with sprints
  (C 3.2, B 8.8, A 32.3, S 78.2) and per hour a grand prix pays 1.25-1.33x a sprint (C 96, B 180, A 360,
  S 720).
* **Shop prices: no change.** A grand prix racer earns about a quarter less than under the flat 3x
  (C 12-17 for 18, A 48-66 for 72, S 84-120 for 144), sprint racers are unchanged, and item prizes
  are per win as before. Reagan 40 / Sylkis 80 / Carob 48 / Zeio 96 are still one or two A / S grand
  prix wins, which is the "long season" the stall prices were set for. If the short-GP rework lands
  and GP income feels thin, nudge the purse base, not the prices.

### Old saves: `ChocoboEntity.SAVE_FORMAT` 3

* `RaceScoring.convertedClassPoints(format, classId, stored)` runs the steps in order:
  format < 2 -> `migratedClassPoints` (old marks: 1 -> 3, 2 -> 6 of 9; Class S -> 9), then
  format < 3 -> `rescaledClassPoints` (x4 onto 36; Class S -> 36). So a format-2 bird with 5 of 9
  has 20 of 36, and a format-1 bird with two marks has 24 of 36 (the same share of the way up
  both times). Wild blood is still rolled only for format < 2 birds with a blank line.
* The almanac ledger (`BirdRecord`) now saves `"Ladder": 36`; a record without it is rescaled on
  load (`BirdRecord.ladderPoints`), so a bird in an unloaded chunk does not show 6 of 36.
* `OldSaveConversionTest` covers both steps, the 2 -> 3 step alone, 1 -> 3 in order, Class S and
  a current bird left alone. GameTest `oldSaveConvertsOnce` now expects 24 (was 6) and adds a
  format-2 bird (5 -> 20, blood not re-rolled). **Not run here** (GameTests are Ahmi's).

### Shown to the player

* **Course picker** (`CourseSelectScreen`, ranked tab of the bird's own class below S): "Name · 1 lap
  · 1150 m · 4 pts" / "Name · 5 laps × 600 m · 10 pts"; a label too wide for its column (only
  Heartfield Grand Prix at 480 x 270) falls back to "5×630 m". The tooltip adds "First place: 10
  points (36 promote), 15 GP", or just the GP on a lower-class tab (half purse) or in Class S.
  Duels show neither (no points, no purse).
* **After a ranked win**: "+10 points (26 of 36) in Class C." (`race.class`), or "+10 points:
  promoted to Class B!" (new `race.promoted`); a Class S win shows the top-of-ladder line with the
  bird's real first-place count (it used to print class points there).
* Text: almanac racing line and the Whiskerwind page, `gossip.7`, the Race Hall board signs
  (board.5-7: "sprint wins 4," / "GPs by length," / "36 up, no drop"), README, store description
  (+ html), SPEC, `docs/RELEASE_1.0.19.md` (new "Points by distance" block, old-bird numbers).

### Tests

New `WinPointsTest` (relative, so it survives the short-GP rework): the reference is the shortest
sprint; every sprint 4 and sprints within 1.125x; every grand prix = round(4 x ratio) and more than
a sprint; points never fall as the heat grows; the formula over ratios 1.15-3.4; nine sprint wins
promote (eight do not); grand prix wins to promote = ceil(36 / points), fewer than nine; Class S
stays; purse = base x points / 4 and places half / quarter; plus the table. `RaceScoringTest` and
`OldSaveConversionTest` moved to the 36 ladder.

### Short grands prix: class C

Ahmi: "Grand prix are supposed to be short courses, multiple laps; sprints long one-lap tracks" (a C
grand prix was 5 x 630 = 3150 blocks, about five minutes). The six C grands prix are rebuilt to the
lap-share rule in `RaceTrackTest.grandPrixLapShare` against C's shortest sprint (C_DOWNS, 1150): all
six new shapes, laps 298-480, heats 1380-1552 blocks (1.20-1.35x a sprint). Sprints untouched. Every
id, ordinal, index, theme, lap count, course name, shape name and set piece is unchanged (the set
pieces still stand at t = 0.5 on the outside, and every one still fits beside the smaller road).

| id | name | laps x lap | heat (x sprint) | shape | features | landmark | borrows from |
|---|---|---|---|---|---|---|---|
| c_meadow | Meadow Circuit | 5 x 305 | 1525 (1.33x) | STADIUM: short oval, crest in turn 1 (hill 3), bus-stop chicane kinking out of the back straight, one long constant sweeper onto the line | 3 boosts (0.34, 0.54, 0.86) | lone oak, beside the chicane | Baby Park's tiny oval; the chicane is a Monza-style braking chicane on a straight (Variante / the old Spa Bus Stop), the final sweeper a gentler Parabolica |
| c_orchard | Orchard Loop | 4 x 355 | 1420 (1.23x) | ZIGZAG: a lightning bolt; the right side steps out and up (hill 5), a plateau across the top, a zig back down the far side | 3 boosts (0.17, 0.47, 0.88) | great cherry | Suzuka's uphill S-curves on the right-side steps; the zig down is a downhill esses (Becketts in reverse) |
| c_shore | Shore Grand Prix | 3 x 460 | 1380 (1.20x) | PEANUT: two lobes through a narrow waist; up through the waist to a round top lobe at the crest (hill 5), back down through the waist, a harbour chicane before the final corner | 3 boosts (0.18, 0.53, 0.88) | striped lighthouse | Monaco: Sainte-Devote-style turn 1, the climb to a Casino-square loop, the waist as the squeeze past the harbour, the Nouvelle-chicane / Piscine flick on the waterfront before the line |
| c_heartfield | Heartfield Grand Prix | 5 x 298 | 1490 (1.30x) | HEART: long lobe up to the crest, the dip flicked at the top (hill 5), second lobe, down the far side to the point onto the line | 3 boosts (0.30, 0.63, 0.88) | hot-air balloon | COTA turn 1 (a climb into a tight crest corner) for the dip; the point is a Rascasse-style last corner onto the straight |
| c_kite_hill | Kite Hill | 4 x 388 | 1552 (1.35x) | KITE: a long diamond, climbing the right side to the tip over the crest (hill 6), a fast top edge, the tail streaming in an S down to the point | 3 boosts (0.16, 0.54, 0.88) | kite on a chain | Spa's Raidillon (the long climb to a blind crest), Maggotts-Becketts for the tail S, Luigi Circuit's long straight into turn 1 |
| c_horseshoe | Horseshoe Farm | 3 x 480 | 1440 (1.25x) | HORSESHOE: heels up; a fast toe (the start straight), up and over one heel (hill 5), down into the notch and a compression at its foot (hill 1), up over the other heel | 3 boosts (0.15, 0.53, 0.88) | red barn and silo, standing in the notch | Eau Rouge / Raidillon: down into the dip and straight back up, twice over the two heels; Mario Circuit's U-turns |

Numbers held: every C grand prix lap share in the window (5 laps 0.259-0.265, 4 laps 0.309-0.337,
3 laps 0.400-0.417), more laps never a longer lap (5: 298-305 <= 4: 355-388 <= 3: 460-480), heats
1.20-1.35x. The shortest C lap (298) stays under the least a B grand prix can be (0.24 x 1280 =
307), so "grand prix laps get longer up the ladder" holds whatever B lands with. Open road, three
boosts each on corner exits (`turnAhead(45) < 0.7`), start straight turns 0.041-0.100 rad over 40
blocks, closest legs 36.5-52 blocks (need 30), hills 3-6, three stands each (2 in, 1 out), 36-43 fans.

**How the shapes were made**: a lap this short scales every silhouette down, and the rules fight
it: the grid needs ~70 blocks of straight (40 blocks past t = 0), any two points 80 blocks apart
along the lap must be 30 apart, and a C corner should not go under about 11 blocks of radius. The
old shapes shrunk to size fail all three, so each was redrawn (scratch Python mirror of
`TrackSpline`, not committed): tightest corners are the Meadow chicane (~10), the Heart point and
the Orchard last corner (~11), the Kite and Shore (~13), Horseshoe (~15). No crossover: the legs
test is 2D, so a figure-eight cannot pass. Two things the layout tests caught on the way and the
shapes now avoid: a height change right next to a fold (Heartfield's dip was on a slope, so the
margin grass of the upper leg stood over the lower road) and a SHORE pool on a hill step near the
chicane (its water ran down onto the road); both stretches are now level.

**Ladder** (`RaceSimTest`, every line green, no expectation moved): C field average 202.0 -> 136.6 s,
field best 195.1 -> 131.8 s, fresh Good Yellow driven well 178.8 -> 118.2 s (wins by 10.3 %, was
8.4 %), cruising 211.7 -> 141.5 s (loses to the field average by 3.6 %, was 4.8 %). The heats are
about a third shorter, so the means drop; the ratios barely move.

**GameTest** `squareBuildsCourseAndRunsHeat` (course 0, C_MEADOW): at tick 2100 it now asks for three
racers with **two** laps done (`lapsDone(2) >= 3`, was one). Two laps are 610 blocks, the same
distance the old single 600-block lap asked for in the same ~92 s (about 70 % of the C field's ~9.7
blocks a second). Not run here; if it is flaky in-game, going back to `lapsDone(1)` is the safe
fallback. `twoAiBirdsInOneLaneNeverOverlapAndBothLap` (C_MEADOW, one lap in 3500 ticks) and
`duelIsOneOnOne` (C_SHORE stalls) only get easier.

**For the merge**: six existing islands changed shape, so `COURSE_VERSION` should go 11 -> 12 (and
`RaceTrackTest.courseVersionElevenRelaysTheIslands` with it); left alone here as the phase-2 rules
say. Full `test` on this branch: 244 tests, 1 skipped, 1 failure, `sprintsAreOneLongLapGrandsPrix...`
on B_CANYON's lap share (class B not landed yet); every class C line of it passes. Needs an in-game
look: the Meadow chicane at five-lap pace with bumping, the Heart dip and point, the Horseshoe notch
compression, and whether three boosts on a 300-block lap feel like too many.

### Short grands prix: class B

Six class B grands prix rebuilt as short technical circuits (Ahmi: "grand prix are supposed to be short
courses, multiple laps; sprints long one-lap tracks"; "Mario Kart meets F1"). Sprints (3-8) untouched;
the class's shortest sprint is still B_MESA at 1280. Each course keeps its one terrain feature type (water or
ridge: the class stays six water / six ridge), its theme, laps, landmark and name. Every shortcut is the lap's
risk/reward moment: the Blue or Green bird takes the direct line straight through, everyone else swings round.

| id | name | laps x lap | heat (blocks, x sprint) | shape | features (detour / target) | landmark | borrowed from |
|---|---|---|---|---|---|---|---|
| b_canyon | Canyon Pass | 5 x 340 | 1700, 1.33x | DELTA: triangle; hairpin off the line, climb to a crest hairpin, the pass bowing in down the far side, hairpin home | ridge 0.53-0.66 down the pass (15.4 / 13); boosts 0.22 0.485 0.84 | hoodoo (t 0.50, crest exit) | La Source hairpin off the grid, an Eau Rouge / Raidillon climb to a blind crest, Baby Park's five-lap loop |
| b_ford | Fern Ford | 4 x 395 | 1580, 1.23x | KIDNEY (was LOLLIPOP): two round lobes, the river bend bowing deep between them | water 0.40-0.52 across the bend (14.3 / 13); boosts 0.33 0.82 0.93 | cairn (t 0.56) | Monza-style long round ends (Parabolica), a Yoshi Circuit / Luigi Circuit water line across the inside of the bend, boost into the ford |
| b_frost | Frost Hollow | 3 x 500 | 1500, 1.17x | LOLLIPOP (was KIDNEY): an ice lolly; the stick out and back, a 260-degree loop at its end | ridge 0.58-0.62 on the return stick (15.7 / 13); boosts 0.44 0.65 0.87 | ice spire (t 0.50) | S-bend necks (Suzuka esses) into a constant-radius carousel, a Monaco hairpin at the stick's end |
| b_baobab | Baobab Loop | 5 x 345 | 1725, 1.35x | LOZENGE: leaning parallelogram, two sharp and two open corners, back straight sagging round a waterhole | water 0.41-0.53 on the sag (14.8 / 13); boosts 0.155 0.655 0.84 | baobab (t 0.56) | Baby Park's tiny many-lap loop, Monaco's harbour line: the waterhole sits where the back straight bends away |
| b_kopje | Kopje Circuit | 4 x 405 | 1620, 1.27x | HEATER: shield with one flank battered in; flat top, long domed flank to the point, hollow flank to a sharp corner | ridge 0.53-0.65 down the hollow flank (15.6 / 13); boosts 0.30 0.48 0.82 | kopje (t 0.50) | COTA's long climbing turn to the top, the point and the hollow flank as a left-right (Senna S), boost on the point exit into the fork |
| b_snowcap | Snowcap Ring | 3 x 515 | 1545, 1.21x | MITTEN: round hand, thumb out the side, down the cuff | ridge 0.56-0.60 along the thumb (15.9 / 13); boosts 0.44 0.74 0.89 | snowman (t 0.50) | a big round sweeper (Interlagos' Curva do Sol), a narrow finger with a hairpin tip (Baku castle / Monaco Loews), the wrist S back onto the line |

Laps 5 x 340-345 (0.27x the shortest sprint), 4 x 395-405 (0.31x), 3 x 500-515 (0.39-0.40x); heats
1.17-1.35x the sprint. 3 x 490 would be a 1.148x heat, under the 1.15 floor, so the 3-lap laps are 500 and 515.
Legs 39.5-47 blocks apart (need > 38), islands at most 125 x 75 half extent, 5 stands and 72-88 fans each.

**Shape swap**: B_FORD now races `Shape.KIDNEY` and B_FROST `Shape.LOLLIPOP` (no enum renamed). A lollipop cannot
fit a 395 lap (the start straight alone is a quarter of it, leaving no room for a round loop 38+ blocks from the
stick); it fits Frost Hollow's 500 (an ice lolly), and the bean with the river bend is Fern Ford's. HEATER keeps its
name; one flank is now hollow.

**Why every short lap has a concave bend** (engine fact, no shared code changed): at these lap lengths the detour
connectors (`DETOUR_CONNECT` 0.012 of a lap, 4-5 blocks) swing 12.5 blocks out, so a detour on a straight costs
2 x (hypot(c, 12.5) - c): 18.1 blocks at 340, 17.2 at 395, above the 1.3 x 13 = 16.9 ceiling of
`CourseBalanceTest` (a straight only works from about a 418 lap). `detourCost` samples tangents per block, so a
tight reverse bend adds length rather than saving it; only a long gentle reverse bend (radius ~60-120, 25-60
degrees) under a long feature (0.10-0.13 of the lap, 35-50 blocks) brings the cost into the window. So the four
5- and 4-lap courses carry their water or ridge on a long bend that bows in toward the infield (the pass, the river
bend, the sag, the hollow flank). The 3-lap courses sit on straights (15.7 / 15.9). Python port of `TrackSpline` +
`detourCost` (matches Java to 0.1 block) was the design tool; scratch, not committed.

**RaceSim** (B, mean heat seconds over all twelve B courses, before -> after): field best 129.3 -> 86.9, fresh Green
driven well 143.0 -> 95.7 (still loses, +10.1 % vs +10.6 %), 25-trained 125.0 -> 82.3 (still wins the field, by
5.3 % vs 3.3 %), 50-trained vs Teiyo 96.2 / 101 -> 61.8 / 64 (still wins). Every ladder line holds; no expectation moved.

**Merge notes**: the six islands shrank a lot (B_FROST's old 825 lap to 500) and `clearChunks` clears only the new
island's box plus one chunk, so old road would be left hanging round the new, smaller islands: when the four classes
land, `COURSE_VERSION` needs a bump and the relay needs to clear the old footprint (for instance the whole slot out to
`MAX_ISLAND_RADIUS`). Not changed here (shared code). In-game look wanted: the 12.5-block detour connectors over 4
blocks on the 340 laps (steep swing out), and five birds with collision on 340-block laps.

### Short grands prix: class S

Six class S grands prix rebuilt as short technical circuits (Ahmi: "grand prix are supposed to be short
courses, multiple laps; sprints long one-lap tracks"; "Mario Kart meets F1"). Sprints (3-8) untouched; the
class's shortest sprint is still S_STARFALL at 1560. Every course keeps its theme, laps, landmark, name and
colour lean; every course now has a bog (S_SKYWAY had none: ridge, water, ridge + a new bog in the notch), no
new lava (S_KEEP keeps its two, nothing else gains any: only Gold gains from lava now Flame birds cannot race).
All six shapes are new outlines under the old enum names.

| id | name | laps x lap | heat (blocks, x sprint) | shape | features (detour / target 13) | landmark | borrowed from |
|---|---|---|---|---|---|---|---|
| s_skyway | Rainbow Skyway | 5 x 448 | 2240, 1.44x | BOOMERANG: an elbow off the line, up one arm to a crest hairpin at the tip (+5), down into the notch, along the other arm, hairpin home | ridge 0.15 (16.4), bog 0.43-0.54 in the notch (10.7), water 0.57 (15.5), ridge 0.65 (16.3); boosts 0.115 0.365 0.895 | glass arch (t 0.71, over the home hairpin) | COTA turn 1 (uphill hairpin at a crest), Rainbow Road risk/reward line on the back arm, Monaco Grand Hotel hairpin onto the line |
| s_keep | Obsidian Keep | 4 x 485 | 1940, 1.24x | RAMPART: a square keep, four right-angle corners, up to the battlements (+4) and back, the west curtain pinched in round its moat | lava 0.43 (15.9), ridge 0.51 (15.8), bog 0.69-0.78 on the moat bend (10.5), lava 0.89 before the line (16.1); boosts 0.125 0.37 0.625 | blackstone arch (t 0.59) | Baku's castle section (square walls, 90-degree street corners), Monaco harbour: the moat bend and a lava pool before the line |
| s_void | Void Reach | 3 x 610 | 1830, 1.17x | HAMMER on its head: the head along the line with a hairpin at each end, a tall handle climbing (+5) over a crest hairpin, down a waisted side | water 0.28 (15.2), ridge 0.36 (14.2) up the handle, bog 0.60-0.67 in the waist (9.9); boosts 0.17 0.545 0.88 | obsidian spire (t 0.50, crest hairpin) | La Source / Monaco hairpins at both ends of the head, a Monza-style drag up the handle, Maggotts-Becketts esses in the waist |
| s_orbit | Ringed Orbit | 5 x 450 | 2250, 1.44x | SATURN: the ring along the line with a sharp tip at each end, the planet swelling up (+5) between two sweeping shoulders | water 0.12 (16.3), bog 0.38-0.48 on the first shoulder (14.8), water 0.71 (16.4); boosts 0.275 0.555 0.63 | ringed planet (t 0.53) | Baby Park / Luigi Circuit's tiny many-lap loop, Monaco swimming-pool water after the line, Suzuka-style shoulders into the dome |
| s_eclipse | Eclipse Crescent | 4 x 485 | 1940, 1.24x | CRESCENT_MOON: the outer arc along the line and up to two curled horns (+5), the hollow of the moon dipping deep between them | bog 0.36-0.465 in the hollow (13.0), ridge 0.49 (14.8), ridge 0.89 before the line (16.3); boosts 0.105 0.30 0.56 | eclipse disc (t 0.56) | Monza Parabolica (the long outer arc), Interlagos-style crest hairpins at the horns, a long constant-radius hollow |
| s_rift | Sculk Rift | 3 x 610 | 1830, 1.17x | FISSURE: a split block, the line along the foot, a climb up the far wall to the lip (+3), a jagged fissure falling in esses to its flooded bottom and back out | ridge 0.21, ridge 0.32 (14.3 each) up the wall, bog 0.52-0.62 on the lip (9.7), water 0.72 in the flooded bottom (14.6); boosts 0.15 0.645 0.87 | rift shards (t 0.65) | Spa Eau Rouge / Raidillon climb up the wall, Suzuka S-curves down the fissure, Interlagos' climb out to the line |

Laps 5 x 448-450 (0.287-0.288x the shortest sprint), 4 x 485 (0.311x), 3 x 610 (0.391x); heats 1.17-1.44x.
3 x 610 keeps the 3-lap heats over the 1.15 floor; every 5-lap lap is over 415 so S stays above A (being rebuilt
to 376-410) on the ladder test. Legs 40.3-57.8 apart (need > 38), islands at most 115 x 111 half extent.

**Why the features sit where they do** (engine facts, measured with a Python port of `TrackSpline` + `detourCost`
that matches Java to 0.1 block; scratch, not committed): at 450 a detour on a straight costs 16.3-16.5 (the
connector swing alone; ceiling 16.9), at 485 about 15.9, at 610 about 14.4. A concave bend only saves on the inside
detour if it is long and gentle (radius about 80-120, 25-40 degrees); a tight one costs more because `detourCost`
samples the tangent per block. So every course has one long gentle concave bend (the notch, the moat, the waist, the
shoulder, the hollow, the lip) and the bog lives there (9.7-14.8, always the cheapest); the colour features sit on
straights (14.2-16.4).

**Stands** (`CourseCrowdTest`: an S course must seat exactly ten): on 450-485 laps with three or four features the
planner could only find 3-9 sites (every stand keeps 12 blocks off a feature connector or boost strip and 22 off the
landmark). `CourseStands.planStands` gained three last-resort tries (half and third-length stands, gentler turn
limit), which only run for a stand that found no site before, so every course that already seated all its stands is
unchanged (C/B/A/S sprints included). With them, and with the features and boosts packed to leave room (a Python
emulation of the planner matched Java exactly), all six S grands prix seat 10 stands and 203-242 fans. Shared-code
change: flag it at merge if another class touched the same line.

**RaceSim** (S, mean heat seconds over all twelve S courses, before -> after): field best 54.5 -> 36.1, maxed Black
driven well 47.8 -> 30.9 with Teiyo against it 50.2 -> 33.4 (the Black wins by 5.0 % -> 7.8 %), maxed Gold 35.7 ->
24.8 with Teiyo 38.7 -> 26.6 (Teiyo 8.4 % -> 7.1 % behind the Gold, bound 9 %), 90-trained Black beats the field
best by 6.7 % -> 11.4 %, half-trained still loses (50.5 vs field average 40.4), maxed cruising still loses (52.3).
Every ladder line holds; no expectation moved, nothing in the AI retuned.

**Merge notes**: `COURSE_VERSION` not bumped (the merge will; the six islands shrank a lot, as for B). S_ORBIT's first
pool sits at 0.12 so its shortcut stripe starts after the start arrow (at 0.08 the stripe painted over the arrow).
In-game look wanted: the crest hairpins (Skyway tip, Void handle top, Eclipse horns), the Rampart right angles at
r 18, ten grand stands on a 450-block lap, and five colliding birds on the Skyway notch bog.

### Short grands prix: class A

Class A's six grands prix rebuilt to the lap-share rule (`RaceTrackTest.grandPrixLapShare`) against A's
shortest sprint (A_DEEPS, 1420): six new outlines, laps 372-590, heats 1710-2025 blocks (1.20-1.43x a
sprint). Sprints untouched. Every id, ordinal, index, theme, lap count, course name, shape name, feature
type set (the colour lean) and set piece is unchanged; every course keeps two colour features and a bog.

| id | name | laps x lap | heat (x sprint) | shape | features (detour cost, target 13) | landmark | borrows from |
|---|---|---|---|---|---|---|---|
| a_crystal | Crystal Caverns | 5 x 372 | 1860 (1.31x) | DEE: the start straight is the D's flat back, a bowl climbing 180 degrees round the far end (hill 4), a crest straight sagging home over the top, a tight drop (R ~12) and a short flat side back to the line | bog 0.34-0.44 (15.5), water 0.4675-0.5375 (16.6), ridge 0.6875-0.7375 (16.6); boosts 0.2725, 0.65, 0.835 | amethyst geode, t 0.56 (the water pushes it off 0.5) | Monza: a long straight into the Parabolica, here climbing; the Curva Grande's gentle kink is the sagging crest straight where the bog and the pool sit |
| a_canopy | Canopy Rush | 4 x 440 | 1760 (1.24x) | ELBOW: a boot; the foot is the start straight, a double-apex hairpin at the toe, the inside of the elbow one long reverse sweeper, a climb up the upright to a hairpin at the top (hill 5), a run down its back | water 0.2925-0.3225 (16.3), bog 0.6575-0.7075 (15.3), ridge 0.9175-0.9475 (16.6); boosts 0.2575, 0.595, 0.8825 | step pyramid, t 0.5 | COTA turn 1 (the climb to a hairpin at the top of the upright); the elbow's reverse sweeper is a Becketts-style change of direction; the ridge on the run to the flag is a Mario Kart last-corner risk |
| a_ember | Ember Fields | 3 x 590 | 1770 (1.25x) | TRIDENT: a trident head; a broad back (the start straight), two deep notches (R ~19) cut down into its top leaving three points, the middle one a free-standing tine over the crest (hill 5) | bog 0.19-0.23 (14.5), lava 0.7825-0.8125 (15.7), ridge 0.865-0.945 (16.0); boosts 0.305, 0.51, 0.7325 | fortress tower with its lava fall, t 0.5 (the middle tine) | Baku's castle section (the three points: tight, walled, one bird wide through the tips) and Monaco's Loews/Portier (hairpin, reverse U, hairpin); a Monza straight home with the ridge on it |
| a_grotto | Blindfish Grotto | 5 x 405 | 2025 (1.43x) | BLINDFISH: a cave fish; the flat belly is the start straight, a round nose hairpin, the back sweeping down into a forked tail: two hairpin tips (R ~10.5) either side of a straight trailing edge | water 0.3175-0.3675 (16.4), bog 0.41-0.49 (15.4), water 0.6875-0.7275 (16.6); boosts 0.5875, 0.6275, 0.8725 | eyeless cave fish, t 0.53 | Baby Park / Luigi Circuit (a tiny loop run five times), Suzuka's hairpin twice at the tail; the back is a long downhill reverse sweep with the shortcuts on it |
| a_moonshelf | Moonshelf Hollow | 4 x 455 | 1820 (1.28x) | SHELFCAP: a shelf fungus on its trunk; the trunk is the start straight, the cap climbs and rolls over a long crest (hill 4) to its lip (a hairpin), the gilled underside sweeps back down to the trunk in one long reverse curve | ridge 0.465-0.495 (16.5), lava 0.5275-0.5575 (16.5), bog 0.6875-0.7275 (15.3); boosts 0.30, 0.615, 0.92 | dead trunk ringed with shelf fungi, t 0.59 | Spa: up Raidillon and over the crest (the cap), then Interlagos' descending sweep back to the pits (the underside) |
| a_machete | Machete Cut | 3 x 570 | 1710 (1.20x) | MACHETE: the edge is the start straight, sweeping up to the point, a flat spine back into a narrower handle, a round pommel (hairpin), and an S-step at the heel onto the edge | water 0.4125-0.4525 (16.5), bog 0.48-0.53 (14.8), ridge 0.5825-0.6225 (15.9); boosts 0.2625, 0.7225, 0.9325 | machete in a jungle stump, t 0.56 | Montreal: the long straight, L'Epingle (the pommel hairpin) and the last chicane (the heel S) onto the line; the spine is a shortcut alley, Mario Kart style |

Numbers held (checked with every class A line of the suite green): lap shares 5 laps 0.262 / 0.285, 4 laps
0.310 / 0.320, 3 laps 0.401 / 0.415; more laps never a longer lap; heats 1.20-1.43x. **The shortest A lap
(372) sits between the most B's shortest can be (0.29 x 1280 = 371) and the least S's can be (0.24 x 1560 =
374), so "grand prix laps get longer up the ladder" holds whatever B and S land with.** (The brief suggested
376-410 for the 5-lap courses; above 374 an S 5-lap course at its floor would undercut A.) Legs 40-50 apart
(need 38), tightest corners ~10-12 blocks, hills 2-5, seven stands each (3-4 in, 3-4 out), 129-154 fans.

**The detour window is the hard part at this size.** A grand prix this short has the floor target (13), so
a colour detour must cost 12-16.9. The cost model (`detourCost`) steps the lane's normal once per block, and
every step adds its jump to the detour: a straight costs about 17.6 at 372 blocks, 17.1 at 405, 16.6 at 440
and 15.0 at 580, and a bend of either hand adds, except a gentle reverse (concave) bend with a long span,
where the step is smaller than the sampling step and the geometry wins. So on the 5- and 4-lap courses
every colour feature sits on a long, gentle right-hander (a sagging crest, a reverse sweeper), spans are
long (0.03-0.10 of a lap) and the colour costs sit near the top of the window (16.3-16.6); the bog is always
the cheapest (14.5-15.5, not the 45 % the doc aims at: nothing on a lap this short gets lower without folding
the road). The 3-lap courses can use straights.

**How the shapes were made**: a turtle outline (straights and arcs, closed by solving a few straights'
lengths) in a scratch Python mirror of `TrackSpline`, a feature solver over `detourCost`, and a Python port
of `CourseStands.planStands` (seven stands is the other hard constraint: every feature and boost blocks 12
blocks either side, the landmark 22), all checked against the real classes by a scratch probe; none of it
committed. The 38-block leg rule rules out small serpentines: any U-turn whose legs are 80 blocks apart
along the lap must be 38 wide, which is why Ember's notches are R ~19 and its middle tine is short.

**Ladder** (`RaceSimTest`, every line green, no expectation moved): A field average 79.2 -> 53.7 s, field best
70.1 -> 47.2 s, half-trained Black driven well 67.2 -> 44.1 s (still wins, by 6.6 %, was 4.1 %), fresh Black
71.6 s vs field average 53.7 s (still loses). Heats a third shorter; the ratios barely move.

**For the merge**: six A islands changed shape, so `COURSE_VERSION` goes up with the other classes (left
alone here). Full `test` on this branch: 244 tests, 1 skipped, 1 failure,
`sprintsAreOneLongLapGrandsPrixShortLapsMoreLapsShorter` on C_MEADOW's lap share (class C has not landed
on this branch; B and S have not either); every class A line of it passes. Needs an in-game look: Grotto's two tail hairpins and its two boosts
0.04 apart after the upper tip (the only boost set that seats all seven stands), Ember's tines at three-lap
pace with bumping, the long ridges (Ember's 0.08 of a lap on the run home), and Crystal's climbing bowl.
