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
