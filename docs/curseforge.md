# CurseForge publishing

No CurseForge project exists for Chocobos Reborn yet (Ninjacat Skies still carries it as a
local jar). Creating one is a website step in the Author Console; the API cannot do it.

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

## Uploading by API

Once the project exists, record its ID here (the owner confirms it; never infer it from
search results) and put a token from the Author Console's API tokens page in
`tools/secrets/.env` as `CURSEFORGE_TOKEN=...` (the folder is git-ignored). Ninjacat
Skies' `tools/upload_curseforge.py` is the working reference for the upload call
(`POST /api/projects/<id>/upload-file`, version ids from `/api/game/versions`).

## GitHub

Source: https://github.com/AhmiDarrow/Chocobos-Reborn. Each release is tagged `v<version>`
with the jar attached.
