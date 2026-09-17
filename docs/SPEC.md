# Chocobos Reborn — spec

Gameplay inventory. Implement from this file and the JUnit tests. Everything is
FF7-authentic where FF7 defines it; Chocobos Reborn lore fills the gaps. Nothing is
taken from other chocobo mods — items, art, names and mechanics are FF7's or ours.

(Rebuilt 2026-09-16 from the code and README after the original file was lost to
another session's `docs/` rewrite; sections after "Riding" are reconstructed.)

## Colors

FF7 terrain rules: Green crosses mountains, Blue crosses rivers, Black both, Gold
everything (mountains, any water, fire) and Gold flies. White, Purple (End) and
Flame (Nether) are ours; Pink / Red and the saucer dyes were removed 2026-09-16.

| Color | Tribe | Land | Water | HP | Terrain / extra |
|---|---|---|---|---|---|
| Yellow | wild pad-runner | 20 | 10 | 30 | — |
| Green | Sprout / mountain | 27 | 10 | 30 | climbs, step 2 |
| Blue | Stone / river | 27 | 50 | 30 | walks on shallow water (≤ 3 deep), rider water breathing |
| White | Clock-pattern echo (missed Black roll) | 35 | 45 | 40 | climbs + shallow water |
| Black | Claw | 40 | 20 | 40 | climbs + shallow water, rider night vision |
| Gold | Spindle | 50 | 45 | 50 | climbs, any water (ocean), flies (air 80, ~1.5x ground pace; sneak dives), fire immune, slow self-heal, rider water breathing + night vision |
| Purple ("End Chocobo") | Void / End islands | 45 | 40 | 50 | end-stone flecked; climbs, any water, rider water breathing + slow falling (no flight) |
| Flame ("Nether Chocobo") | Spark / Ember Wastes (Nether) | 40 | 10 | 50 | red with lava mottling; fire immune, walks on lava, rider fire resistance |

Speeds are ×10 (0.20 land for Yellow). Purple spawns wild on end stone in the
End's outer biomes (`chocobo_end` biome modifier, `checkSpawn`) and breeds true by
inheritance (a Purple parent passes Purple half the time; no nut changes it).
`ChocoboColor` holds the table; `deepWater()` / `lavaWalk()` / `climb()` /
`waterWalk()` / `fly()` drive `ChocoboEntity.canStandOnFluid` / `onClimbable` /
`travel`.

## Size

Adult 3.25 m to the crest, hitbox 1.75 × 3.25, seat 1.92 m and 0.36 m behind centre, on the saddled mesh's seat (the mesh is authored
at 2.25 and scaled by `ADULT_H / mesh height` in the renderer). Chicobo stages at
25 / 50 / 75 % of player height (`ChocoboEntity.getAgeScale`), same mesh scaled.
Saddled birds render the second mesh `chocobo_saddled` (saddle, bridle, reins
baked in).

## Grades and wild spawn

`ChocoboGrade`: Poor, Average, Good, Great, Wonderful. Wild yellows roll a grade at
spawn (`ChocoboEntity.WildGrade.roll`): on ordinary ground Great is the best; only
birds that spawn on snow or ice (cold pads) can roll Wonderful (~12 %).
`neoforge:add_spawns` in all Overworld biomes, heavier in cold biomes, Flame in
the Nether. Chocobo Lure: tempt goal + glowing on wild birds within 32 blocks of a
holder.

## Taming

A Gysahl Green handed to a wild bird tames it with a 1-in-3 chance per feed
(`mobInteract`). Tame birds sit, follow, and say "kweh"; wild or hurt birds "wark".

## Greens (FF7 training)

`ChocoboGreen`: Gysahl, Krakka, Tantal, Pahsana, Curiel, Mimett, Reagan, Sylkis.
Each feed adds speed / stamina / intelligence / cooperation points (0–100) until
the bird is sated on that green (per-green satiety: 40 / 40 / 40 / 30 / 30 / 24 /
16 / 12 feeds). Speed training raises mounted speed up to +12 %, stamina training
adds to max stamina, intelligence makes dashes cheaper; total training also lifts
the effective grade one step per 60 points (`gradeFromTraining`).

Feeding (any green, owned bird): +stat points, +20 stamina, heals 3 if hurt, a
chick grows faster (vanilla `ageUp`), and the owner sees an action-bar line with
the bird's totals and feeds left. A sated bird gets one feed of each green back
per in-game day. Gysahl alone also heals a wild-tame bird 5 and tames.

## Nuts (FF7 mating)

`ChocoboNut`: Pepio, Luchile, Saraha, Lasan, Pram, Porov, Carob, Zeio. A nut fed to
an owned adult puts it in love; both parents need a nut and opposite sexes
(`canMate`). Chick colour: `Ff7Line` / `BreedRules` / `BreedingOdds`:

1. Two Good-or-better Yellows + Carob → Green or Blue (50/50).
2. Green + Blue + Carob → Black on a hit, White on a miss.
3. Black + Wonderful Yellow + Zeio → Gold on a hit. Gold never without Zeio;
   Gold without Zeio passes on Yellow.

**Race wins are required (FF7).** `ChocoboEntity.minWinsEach`: each parent needs
1 first-place finish for Green/Blue, 2 each for Black, 3 each for Gold, or the
roll is impossible. `BreedingOdds.chance(winsA, winsB, minEach, guarantee)` then
climbs from 25 % at the minimum to certain at the combined guarantee
(`guaranteeWins`: 4 / 9 / 12). Handing a Carob or Zeio to a bird that lacks the
wins shows `chocobosreborn.nut.needs_wins`. A missed roll inherits a parent's
colour at random.

Chick grade = parents' average (+1 with Zeio). Chick talent = nut tier × 4
training points + a sixth of the parents' training (intelligence / cooperation at
half). Chick sex random; grows through three stages.

## Riding

`getControllingPassenger` = the rider when saddled. The rider fights from the saddle:
the bird is not pickable for its driver and ignores damage from its passengers. Dash = sprint (stamina drains
server-side in `tickRidden`), recover when cruising or standing. Jump key = hop,
or flap on Gold (look up while moving to climb; sprint key = dive at the same rate;
no dash while airborne).
Water walkers stand on water (`canStandOnFluid`, shallow unless `deepWater`),
climbers climb walls (`onClimbable`), Flame stands on lava. Sneak = down (dive /
submerge); sneak-dismount only on solid ground. The bird is not pickable for its
driver unless sneaking, so clicks reach blocks. Sneak with an empty hand removes
the saddle (dropped). Flight: `getFlyingSpeed` + `travel` (hold / climb / dive / flap).

## Whiskerwind (Chocobo Square)

Own dimension `chocobosreborn:square`: a void world. `SquareBuilder` + `VillagePlan`
lay the village on an organic sky island centred at (0,-72) (`PADDOCK_VERSION`
rebuilds): plaza and arrival medallion, chocobo fountain, market stalls, cottages, the
inn and bell tower, stable yard, windmill, the race arch with a pier over the void, a
shrine islet. Each of the 24 courses is its own circuit island built on first use at
`RaceTrack.centerX/Z()`. The dimension is a true void (`the_void` biome, no layers), natural
spawns are cancelled (`RaceManager.onFinalizeSpawn`), blocks cannot be broken or placed by
players (`onBreak` / `onPlace`, creative operators excepted), and five untamable town birds
(`ChocoboEntity.townBird`) wander the village. Music: the village theme loops in Whiskerwind, the course loop
during a heat (`client/RaceMusic`). Sky: `client/SquareSky`, the Tribal Power day / night
panoramas over the void with a real day cycle.

Entry: the Chocobo Pocketwatch (`ChocoboPocketwatchItem`: riding an owned saddled
bird it comes too; on foot `enterSquareOnFoot`), or the Farmhand / Esther in the
Overworld (foot or saddle); owned awake birds within 16 blocks follow
(`RaceManager.bringBirds`). Return gate, Esther on foot, or the pocketwatch send you
home (birds follow again).

Courses (`RaceTrack`, `TrackSpline`): six per class, courses 0-2 sprints (1 lap, >= 60 s
at 9 b/s) and 3-5 grands prix (3 laps, >= 120 s per lap, longer up the classes), kart
style: a `Shape` template (straights, sweepers, hairpins, chicanes, hills) as a closed
spline scaled to the lap length, dressed in a `Theme` (three per class: meadow /
orchard / shore, canyon / river / snow, cavern / jungle / nether, skyway / keep / end)
with striped corner kerbs, rails, margins of themed decoration and an infield
grandstand on the start straight where the crowd (`RaceSession.spawnFans`) stands.
Features across the direct line: BOOST strips (every class; `boost_pad` block, +55%
for 50 ticks), WATER (river birds walk it), RIDGE (3-5 blocks; climbers go over), LAVA
(Nether bird and Gold), MUD bogs (everyone -55%), each terrain feature with a detour
road outside (`DETOUR_INNER..DETOUR_OUTER`). C boosts only, B one terrain feature, A
two plus a bog, S three or four. Lap progress is the nearest centre-line sample, so
both routes credit progress; `RacerGoal` brakes for corners and takes the detour when
its bird does not suit the feature (C birds sometimes blunder into bogs).
`RaceCourseLayout` stamps the island, features, detours, stand and lists chunks;
`RaceSession` force-loads them for the heat. `CourseMapDumpTest` draws every course to
`build/track_maps/`.

Ranked heats run on a timetable (`HeatSchedule`): one per class on every five-minute
mark of game time (entrants see the timer on the action bar; a title announces the
transport, a settle period and a five-second title countdown precede GO). The first rider to see Esther picks the course
(`CourseSelectScreen`, `RacePayloads`); later riders of that class join, each taking
an AI racer's stall (six in all). Esther announces to the whole Square at two minutes
and one minute and counts the last ten seconds; riders not in the saddle at the mark
are dropped. The field is filled with AI (`RacerProfile` by class, driven by
`RacerMoveControl` at rider scale, each with a kin jockey in the saddle; Jolo (Gold)
and Teiyo (Black) from B); three
first-place finishes promote, never demote; prizes GP + greens / nuts for a finished
course. Bets at Rook before the heat (pending in player data) or during the hold. No
racing in armour.

Duels: Sable (`TownRole.DUEL`, `DuelDesk`): a rider posts a challenge (course of their
class + 0..32 GP stake, taken up front); the next rider on a saddled bird accepts;
`RaceSession` with two humans + four pace birds, unranked; the winner takes the pot.

Trading: Pell (`TownRole.BROKER`, `TradeDesk`): ride an owned bird up and click to
offer (sneak = gift); the next rider's offer swaps owners; a player on foot claims a
gift.

Stalls (`RaceShops`, `TownRole`): Sage Wynn greens, Bilo nuts, Tack saddles + lures,
the Fair (Almanac, Pocketwatch, fireworks, leads), Marl GP Exchange. Fans wave during
heats. Music: `client/RaceMusic`. Advancements `square`, `first_place`, `class_s`
from `SquareAdvancements`.

## Equipment

`ChocoboEntity` container: saddle, armour, saddlebags + 15 bag slots
(`ChocoboInventoryMenu` / `ChocoboInventoryScreen`; sneak + empty hand or the
inventory key while riding). Armour (`ChocoboArmorItem`): leather 3, iron 5,
diamond 11, netherite 13 armour points; iron and diamond have their own meshes
(`chocobo_armor_*`), netherite shows diamond's, leather none (gold was dropped: its
plating bled the breed colour). Armoured birds cannot
race. Saddlebags (`SaddlebagsItem`): 15 slots, come off only when empty, named on an
anvil. Everything drops on death.

## Chocobo Farm

Jigsaw structure `chocobosreborn:chocobo_farm` (`tools/write_farm_structure.py`):
barn, paddock with two wild yellows, gysahl patch, a Stablehand (greens + plain
nuts stall) and a Farmhand at the gate who sends saddled riders to the Square.
Biome tag `has_structure/chocobo_farm`: plains, sunflower plains, meadow, savannas,
cherry grove; `random_spread` 34/12.

## Advancements

root (Wark!), tame, ride, hatch, green_or_blue, black, gold, lure, farm from
vanilla triggers; square / first_place / class_s impossible-triggers awarded in
code (`tools/write_datapack.py`).

## Items

8 greens, 8 nuts, Gysahl seeds (crop `gysahl_green`), Chocobo Lure, GP, Chocobo
Saddle, Chocobo Almanac (illustrated guide + "My Chocobos" ledger pages with rename,
`ledger/`), Chocobo Pocketwatch (Whiskerwind and back), five Chocobo Armors, Saddlebags,
Square Gate block (Square-internal, no recipe), spawn eggs (8 colours + kin). Loot: seeds from grass, Carob
from ravagers, Zeio from piglin brutes (global loot modifiers). Tooltips
`chocobosreborn.tip.*`.

## Assets

Bird: `tools/fresh_ship.py` (see HANDOFF.md), meshes `chocobo`, `chocobo_saddled`,
`chocobo_armor_iron`, `chocobo_armor_diamond`, eight breed atlases each
(`tools/repaint_atlases.py` re-derives them; End / Nether show feathers carry the
vanilla end stone / lava textures inside `art/masks`). Kin: Tribal Power skins. Calls:
synthesised (`tools/wark_candidates.py`). Music: Ahmi's Suno tracks. Items:
`tools/meshy_icons.py`.
