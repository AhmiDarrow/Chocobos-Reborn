# CurseForge publishing

Canonical project ID: **1699008** (confirmed by the owner on 2026-09-17; do not infer the
project from similarly named search results).

Project: https://www.curseforge.com/minecraft/mc-mods/chocobos-reborn

## What to upload

- Jar: `build/libs/chocobosreborn-<version>.jar` from `./gradlew build` (GameTests are
  excluded from the jar; atlases ship at 1024, masters stay in `art/atlases`, ignored).
  1.0.0 is about 82 MB.
- Icon: `docs/public/chocobos-reborn-icon-400.png` (400x400).
- Description: `docs/public/store-description.md` (`store-description.html` is the same
  text rendered for the console's HTML editor).
- Changelog: `docs/RELEASE_<version>.md`, as a heading plus short player-facing bullets.
- Tags: Minecraft 1.21.1, NeoForge, Client + Server, release. Display name
  `Chocobos Reborn <version> - <subtitle>`.

## 1.0.0 - Whiskerwind
Uploaded 2026-09-17: `chocobosreborn-1.0.0.jar` as file **8903892** ("Chocobos Reborn 1.0.0 -
Whiskerwind", release, 1.21.1 / NeoForge / Client+Server, changelog `docs/RELEASE_1.0.0.md`)
via `tools/upload_curseforge.py`. Awaiting CurseForge approval. GitHub:
https://github.com/AhmiDarrow/Chocobos-Reborn/releases/tag/v1.0.0

## Uploading by API

`python tools/upload_curseforge.py --jar build/libs/chocobosreborn-<v>.jar --display-name
"Chocobos Reborn <v> - <subtitle>" --changelog-file docs/RELEASE_<v>.md`. The author
token is `CF_AUTHOR_TOKEN=...` in `tools/secrets/.env` (git-ignored; the script also
reads Ninjacat Skies' copy).

## GitHub

Source: https://github.com/AhmiDarrow/Chocobos-Reborn. Each release is tagged `v<version>`
with the jar attached.
