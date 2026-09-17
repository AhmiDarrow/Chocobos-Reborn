"""Upload a Chocobos Reborn jar to the CurseForge project (see docs/curseforge.md).

    python tools/upload_curseforge.py --jar build/libs/chocobosreborn-1.0.0.jar \
        --display-name "Chocobos Reborn 1.0.0 - Whiskerwind" --changelog-file docs/RELEASE_1.0.0.md

The author token comes from tools/secrets/.env (CF_AUTHOR_TOKEN=...), git-ignored.
Tags: Minecraft 1.21.1, NeoForge, Client, Server; release type "release" by default.
"""
import argparse
import json
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = "https://minecraft.curseforge.com/api"
UA = "chocobos-reborn-uploader/1.0"
PROJECT_ID = 1699008          # canonical, confirmed by the owner 2026-09-17
MC_TYPE = 77784               # Minecraft 1.21 version type
LOADER_TYPE = 68441           # modloader version type
ENV_TYPE = 75208              # Client / Server environment type


def load_token() -> str:
    for env in (ROOT / "tools/secrets/.env", ROOT.parent / "ninjacat-skies/tools/secrets/.env"):
        if env.exists():
            for line in env.read_text(encoding="utf-8").splitlines():
                if line.startswith("CF_AUTHOR_TOKEN=") and len(line) > 20:
                    return line.split("=", 1)[1].strip()
    raise SystemExit("CF_AUTHOR_TOKEN missing: put it in tools/secrets/.env")


def api_get(path: str, token: str):
    req = urllib.request.Request(API + path, headers={"X-Api-Token": token, "Accept": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def version_id(versions, name: str, type_id: int) -> int:
    for v in versions:
        if v.get("name") == name and v.get("gameVersionTypeID") == type_id:
            return int(v["id"])
    for v in versions:
        if v.get("name") == name:
            return int(v["id"])
    raise SystemExit(f"Could not resolve game version '{name}' (type {type_id})")


def multipart(fields: dict) -> tuple[bytes, str]:
    boundary = "----cr" + uuid.uuid4().hex
    body = bytearray()
    for name, (filename, data, ctype) in fields.items():
        body += f"--{boundary}\r\n".encode()
        disp = f'Content-Disposition: form-data; name="{name}"'
        if filename:
            disp += f'; filename="{filename}"'
        body += (disp + "\r\n").encode()
        body += f"Content-Type: {ctype}\r\n\r\n".encode()
        body += data + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return bytes(body), boundary


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", required=True)
    ap.add_argument("--display-name", required=True)
    ap.add_argument("--changelog-file", required=True)
    ap.add_argument("--release-type", choices=["alpha", "beta", "release"], default="release")
    args = ap.parse_args()
    jar = Path(args.jar)
    if not jar.exists():
        raise SystemExit(f"Jar not found: {jar}")
    token = load_token()
    versions = api_get("/game/versions", token)
    game_versions = [version_id(versions, "1.21.1", MC_TYPE), version_id(versions, "NeoForge", LOADER_TYPE),
                     version_id(versions, "Client", ENV_TYPE), version_id(versions, "Server", ENV_TYPE)]
    meta = {"changelog": Path(args.changelog_file).read_text(encoding="utf-8"), "changelogType": "markdown",
            "displayName": args.display_name, "releaseType": args.release_type, "gameVersions": game_versions}
    print(f"Uploading {jar.name} ({jar.stat().st_size // 1024} KiB) -> project {PROJECT_ID} as {args.release_type}; gameVersions={game_versions}")
    body, boundary = multipart({
        "metadata": ("", json.dumps(meta).encode("utf-8"), "application/json"),
        "file": (jar.name, jar.read_bytes(), "application/java-archive"),
    })
    req = urllib.request.Request(f"{API}/projects/{PROJECT_ID}/upload-file", data=body, method="POST",
                                 headers={"X-Api-Token": token, "Accept": "application/json", "User-Agent": UA,
                                          "Content-Type": f"multipart/form-data; boundary={boundary}"})
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            data = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Upload failed: HTTP {e.code} {e.read().decode(errors='replace')[:400]}")
    print("Uploaded: file id", data.get("id"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
