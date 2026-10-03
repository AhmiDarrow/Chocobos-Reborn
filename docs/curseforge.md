# CurseForge publishing

Canonical project ID: **1699008** (confirmed by the owner on 2026-09-17; do not infer the
project from similarly named search results).

Project: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn

## What to upload

- Jar: `build/libs/chocobosreborn-<version>.jar` from `./gradlew build` (GameTests are
  excluded from the jar; `python tools/ship_atlases.py` writes the yellow / End /
  Nether atlases at 1024 RGB from the masters in `art/atlases`, ignored; the five
  solid breeds are recoloured from yellow on the client). 1.0.0 was about 82 MB; the
  jar is now about 49 MB.
- Icon: `docs/public/chocobos-reborn-icon-400.png` (400x400).
- Description: `docs/public/store-description.md` (`store-description.html` is the same
  text rendered for the console's HTML editor).
- Changelog: `docs/RELEASE_<version>.md`, as a heading plus short player-facing bullets.
- Tags: Minecraft 1.21.1, NeoForge, Client + Server, release. Display name
  `Chocobos Reborn <version> - <subtitle>`.

## 1.1.9 - In Their Element
Uploaded 2026-10-02: `chocobosreborn-1.1.9.jar` as file **9043833** ("Chocobos Reborn 1.1.9 - In Their Element",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.9.md`) via
`tools/upload_curseforge.py`. Every water / ridge / lava shortcut pays (fast climb, suited pace), a tougher field keyed off the rider, remote glide fix, COURSE_VERSION 16.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.9
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9043833
SHA-256: `013d4f1fdb4075cb839e4509436163e277958cbc7fc10f97251fffb57db838ce`.

## 1.1.8 - Two Heats in Three
Uploaded 2026-10-02: `chocobosreborn-1.1.8.jar` as file **9038410** ("Chocobos Reborn 1.1.8 - Two Heats in Three",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.8.md`) via
`tools/upload_curseforge.py`. The named rivals run about two ranked heats in three (`RaceClass.RIVAL_CHANCE` = 2/3, up from 1/2).
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.8
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9038410
SHA-256: `bf7b089e677e219411bda2bee3b821436e4e37bc5bc4b87c96fed75a21a4ce81`.

## 1.1.7 - Every Other Heat
Uploaded 2026-10-01: `chocobosreborn-1.1.7.jar` as file **9034590** ("Chocobos Reborn 1.1.7 - Every Other Heat",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.7.md`) via
`tools/upload_curseforge.py`. The named rivals (Ahmi and Risika in C, Teiyo and Jolo from B) run about half the ranked heats, rolled when the heat is posted; a waiting stake on an absent rival is refunded.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.7
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9034590
SHA-256: `0da6b8e21c30c20acff47a3d4e8012bc71b411534b6d65b1e2468f030244e53d`.

## 1.1.6 - The Whistle
Uploaded 2026-10-01: `chocobosreborn-1.1.6.jar` as file **9034121** ("Chocobos Reborn 1.1.6 - The Whistle",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.6.md`) via
`tools/upload_curseforge.py`. Gold and a feather call your own tame chocobos from any distance, including another dimension, up to eight, Stay and Wander included. A bird in a heat stays, a bird you are riding stays, and a bird the whistle has never been near may need you to see it once. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.6
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9034121
SHA-256: `50d065f9bc8093a5bc81e6dc820d3cb478e16fec18e188ec5e00a96bd7d7d4dd`.

## 1.1.5 - Light on the Feet
Uploaded 2026-10-01: `chocobosreborn-1.1.5.jar` as file **9025422** ("Chocobos Reborn 1.1.5 - Light on the Feet",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.5.md`) via
`tools/upload_curseforge.py`. Profile-driven performance (resting-bird move replay, steward door re-plans, soft course plans, faster skinning); GP/follow maps cleared on stop. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.5
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9025422
SHA-256: `f1c5b4f7cc0817641880db4a35a37e7c70294f9acc766134d82dc899ac51cdfa`.

## 1.1.4 - Earned in Class
Uploaded 2026-09-30: `chocobosreborn-1.1.4.jar` as file **9022598** ("Chocobos Reborn 1.1.4 - Earned in Class",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.4.md`) via
`tools/upload_curseforge.py`. Colour breeding counts wins in the stage's class: C5 / B8 / A10 each, certain at 16 / 24 / 32; Great parents for Black, Wonderful Black for Gold; save format 5. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.4
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9022598
SHA-256: `cf8a17b010b7cdd5b2a25232bbdacc0abaaeedad078db25d62a40b4354e3107d`.

## 1.1.3 - Class Rivals
Uploaded 2026-09-30: `chocobosreborn-1.1.3.jar` as file **9021519** ("Chocobos Reborn 1.1.3 - Class Rivals",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.3.md`) via
`tools/upload_curseforge.py`. Class C seats Ahmi and Risika; promotion bars C36 / B54 / A72 with save-format-4 scale-up. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.3
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9021519
SHA-256: `1ef2634090e6530cda9e6b67852624ce0ad69101f715dfea91665a7e07c2b622`.

## 1.1.2 - Chicobos
Uploaded 2026-09-30: `chocobosreborn-1.1.2.jar` as file **9018068** ("Chocobos Reborn 1.1.2 - Chicobos",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.2.md`) via
`tools/upload_curseforge.py`. Babies are chicobos everywhere; Green + Blue + Carob needs the wins for White too; duel picker capped at the purse; almanac and signs match 1.1.1; rename cleaning. Server and every rider need this jar.

## 1.1.1 - Fair Odds
Uploaded 2026-09-28: `chocobosreborn-1.1.1.jar` as file **9004058** ("Chocobos Reborn 1.1.1 - Fair Odds",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.1.md`) via
`tools/upload_curseforge.py`. Riders bump outside heats too; Rook never pays more than the purse (stake cap purse / odds), duel stakes stop at the course purse, a heat with any finisher settles bets. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.1
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/9004058
SHA-256: `f30fe6450c41c1a32262720694829e7c50557ac0a9041dc3951952de94bd9dfb`.

## 1.1.0 - Full Grid
Uploaded 2026-09-28: `chocobosreborn-1.1.0.jar` as file **8999472** ("Chocobos Reborn 1.1.0 - Full Grid",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.1.0.md`) via
`tools/upload_curseforge.py`. Forty-eight courses (long one-lap sprints, short 3-5 lap grands prix), racers bump, points and purses by distance, rebuilt AI, cleaner courses, Flame and Purple do not race. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.1.0
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/8999472
SHA-256: `def13932ebb9b678c6c90d61a431f95a26099f28f6585ade77a8e484c6c947a2`.

## 1.0.19 - Back on track
Uploaded 2026-09-27: `chocobosreborn-1.0.19.jar` as file **8996621** ("Chocobos Reborn 1.0.19 - Back on track",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.19.md`) via
`tools/upload_curseforge.py`. A wide line keeps the lap, a real shortcut sets the rider back on the road, and every fork is marked. Older birds convert once. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.19
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/8996621
SHA-256: `3463648ad51b2c461003162721a483acc4c4c5170564d3057c6c69a346076c66`.
The GitHub asset digest matches the tested local jar.

## 1.0.18 - Race day
Uploaded 2026-09-27: `chocobosreborn-1.0.18.jar` as file **8996427** ("Chocobos Reborn 1.0.18 - Race day",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.18.md`) via
`tools/upload_curseforge.py`. Other birds' legs move again, class C is a race, Teiyo and Jolo pace off the rider's bird, class-based stands with a client-drawn crowd, and Whiskerwind v13. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.18
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/8996427
SHA-256: `401a2400583600ce4c3dc561c7a9236776ae123c1ba79b8d77faee82f9f54814`.

## 1.0.17 - Held pace
Uploaded 2026-09-27: `chocobosreborn-1.0.17.jar` as file **8995550** ("Chocobos Reborn 1.0.17 - Held pace",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.17.md`) via
`tools/upload_curseforge.py`. Other birds follow the server stream, the field holds its own pace, and nine points promote. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.17
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/8995550
SHA-256: `1768d75217fa5fbea2fa1bc6d6210b5e9fbc177cc69ec46dfa28402cfbc1d4af`.
The GitHub asset digest matches the tested local jar.

## 1.0.16 - Smoother remote races
Uploaded 2026-09-26: `chocobosreborn-1.0.16.jar` as file **8986453** ("Chocobos Reborn 1.0.16 - Smoother remote races",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.16.md`) via
`tools/upload_curseforge.py`. API upload accepted; public moderation status was not verified.
Server and every client require 1.0.16. Validated across all 24 tracks with three player clients
and three normal AI, plus five dedicated-server profiles, 162 unit tests and 27 GameTests.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.16
CurseForge: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn/files/8986453
SHA-256: `f66f6e46cd9a093564c37722a64dd8265e8ac6cbf60f96f3a534054e0c30407e`.
The GitHub asset digest matches the tested local jar.

## 1.0.15 - Long bloodlines
Uploaded 2026-09-25: `chocobosreborn-1.0.15.jar` as file **8977931** ("Chocobos Reborn 1.0.15 - Long bloodlines",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.15.md`) via
`tools/upload_curseforge.py`. An empty dash stays locked until stamina is back to 50,
and a perfect bird is a long bloodline. Server and every rider need this jar.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.15

## 1.0.14 - Steady dash
Uploaded 2026-09-25: `chocobosreborn-1.0.14.jar` as file **8973427** ("Chocobos Reborn 1.0.14 - Steady dash",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.14.md`) via
`tools/upload_curseforge.py`. A guest's dash spends stamina again, and a short lag spike
no longer yanks the bird back. The host jar is enough for the stamina fix.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.14

## 1.0.13 - Clear pages
Uploaded 2026-09-23: `chocobosreborn-1.0.13.jar` as file **8960886** ("Chocobos Reborn 1.0.13 - Clear pages",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.13.md`) via
`tools/upload_curseforge.py`. Almanac preview birds (not in the level) skip the distance LOD.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.13

## 1.0.12 - Sound sleepers
Uploaded 2026-09-23: `chocobosreborn-1.0.12.jar` as file **8957838** ("Chocobos Reborn 1.0.12 - Sound sleepers",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.12.md`) via
`tools/upload_curseforge.py`. Whiskerwind's beds (bed_works false) no longer explode when clicked.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.12

## 1.0.11 - Fine feathers
Uploaded 2026-09-23: `chocobosreborn-1.0.11.jar` as file **8957333** ("Chocobos Reborn 1.0.11 - Fine feathers",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.11.md`) via
`tools/upload_curseforge.py`. Tame adults shed a feather every 5 to 10 minutes and a brush frees one;
a sated or digesting bird that is hurt eats a green to heal without training.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.11

## 1.0.10 - Level field
Uploaded 2026-09-23: `chocobosreborn-1.0.10.jar` as file **8957253** ("Chocobos Reborn 1.0.10 - Level field",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.10.md`) via
`tools/upload_curseforge.py`. Distance LODs for the birds; host and guests on one clock at the finish
(ping credit, sub-tick crossings) and client-timed boost pads; duels one on one for the pot only;
course relays clear old leftovers, the River cairn's spring in a basin, watertight boost pads.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.10

## 1.0.9 - Follow me
Uploaded 2026-09-23: `chocobosreborn-1.0.9.jar` as file **8954786** ("Chocobos Reborn 1.0.9 - Follow me",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.9.md`) via
`tools/upload_curseforge.py`. A new tame follows, and Follow comes with you into the Nether, the End,
Whiskerwind, and back. Stay, Wander, a lead, a race, and the Square's own birds stay put.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.9

## 1.0.8 - Solid ground
Uploaded 2026-09-19: `chocobosreborn-1.0.8.jar` as file **8926685** ("Chocobos Reborn 1.0.8 - Solid ground",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.8.md`) via
`tools/upload_curseforge.py`. Course islands no longer have holes to fall through and the fall rescue catches
every drop; scenery, rails and pools stay off the racing line and pools are walled; every terrain feature
re-placed so a colour is worth about 2 % of a lap on all 24 courses; three boosts per sprint and five per
grand prix on corner exits; a landmark of its own on every course; riders invulnerable in Whiskerwind.
GitHub: https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.8

## 1.0.7 - Fresh paint
Uploaded 2026-09-19: `chocobosreborn-1.0.7.jar` as file **8925242** ("Chocobos Reborn 1.0.7 - Fresh paint",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.7.md`) via
`tools/upload_curseforge.py`. Items, blocks and crop stages redrawn at 32x32; course bogs, entered odds,
scratching, crash-left fans, dismount lock and ledger saves fixed; Carob prize docs corrected. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.7

## 1.0.6 - Fair bets
Uploaded 2026-09-19: `chocobosreborn-1.0.6.jar` as file **8923710** ("Chocobos Reborn 1.0.6 - Fair bets",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.6.md`) via
`tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.6

## 1.0.5 - Follow, Stay, Wander
Uploaded 2026-09-19: `chocobosreborn-1.0.5.jar` as file **8922192** ("Chocobos Reborn 1.0.5 - Follow, Stay, Wander",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.5.md`) via
`tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.5

## 1.0.4 - Wild Gysahl
Uploaded 2026-09-19: `chocobosreborn-1.0.4.jar` as file **8918139** ("Chocobos Reborn 1.0.4 - Wild Gysahl",
release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.4.md`) via
`tools/upload_curseforge.py`. Tribal Power 3.5.0 grows its Wild Gysahl in The March when both mods are installed. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.4

## 1.0.3 - Gysahl
Uploaded 2026-09-17: `chocobosreborn-1.0.3.jar` as file **8908978** ("Chocobos Reborn 1.0.3 -
Gysahl", release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.3.md`)
via `tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.3

## 1.0.2 - Lighter
Uploaded 2026-09-17: `chocobosreborn-1.0.2.jar` as file **8908734** ("Chocobos Reborn 1.0.2 -
Lighter", release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.2.md`)
via `tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.2

## 1.0.1 - Whiskerwind
Uploaded 2026-09-17: `chocobosreborn-1.0.1.jar` as file **8908165** ("Chocobos Reborn 1.0.1 -
Whiskerwind", release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.1.md`)
via `tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.1

## 1.0.0 - Whiskerwind
Uploaded 2026-09-17: `chocobosreborn-1.0.0.jar` as file **8903892** ("Chocobos Reborn 1.0.0 -
Whiskerwind", release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.0.md`)
via `tools/upload_curseforge.py`. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.0

## Uploading by API

`python tools/upload_curseforge.py --jar build/libs/chocobosreborn-<v>.jar --display-name
"Chocobos Reborn <v> - <subtitle>" --changelog-file docs/RELEASE_<v>.md`. The author
token is `CF_AUTHOR_TOKEN=...` in `tools/secrets/.env` (git-ignored; the script also
reads Ninjacat Skies' copy).

## GitHub

Source: https://github.com/AhmiDarrow/Chocobos-Reborn. Each release is tagged `v<version>`
with the jar attached.
