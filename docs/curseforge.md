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
