"""Chocobo Farm — the overworld front door to Chocobo Square.

Generates the structure template NBT (barn, fenced paddock, hay, trough, two
wild yellows, a Farmhand and a Stablehand) and the worldgen JSON that spawns it in open
grassland. Everything is original; block palette is vanilla.

    python tools/write_farm_structure.py
"""
from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data"
NS = "chocobosreborn"

# ----------------------------------------------------------------- NBT writer
T_BYTE, T_INT, T_FLOAT, T_DOUBLE, T_STRING, T_LIST, T_COMPOUND, T_INT_ARRAY = 1, 3, 5, 6, 8, 9, 10, 11


class Byte(int):
    pass


class Float(float):
    pass


class IntArray(list):
    pass


def _tag_type(v):
    if isinstance(v, Byte):
        return T_BYTE
    if isinstance(v, bool):
        return T_BYTE
    if isinstance(v, int):
        return T_INT
    if isinstance(v, Float):
        return T_FLOAT
    if isinstance(v, float):
        return T_DOUBLE
    if isinstance(v, str):
        return T_STRING
    if isinstance(v, IntArray):
        return T_INT_ARRAY
    if isinstance(v, list):
        return T_LIST
    if isinstance(v, dict):
        return T_COMPOUND
    raise TypeError(type(v))


def _payload(v):
    t = _tag_type(v)
    if t == T_BYTE:
        return struct.pack(">b", int(v))
    if t == T_INT:
        return struct.pack(">i", v)
    if t == T_FLOAT:
        return struct.pack(">f", v)
    if t == T_DOUBLE:
        return struct.pack(">d", v)
    if t == T_STRING:
        b = v.encode("utf-8")
        return struct.pack(">H", len(b)) + b
    if t == T_INT_ARRAY:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">i", x) for x in v)
    if t == T_LIST:
        if not v:
            return bytes([0]) + struct.pack(">i", 0)
        et = _tag_type(v[0])
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(x) for x in v)
    if t == T_COMPOUND:
        out = b""
        for k, x in v.items():
            out += bytes([_tag_type(x)]) + _payload(k) + _payload(x)
        return out + b"\x00"
    raise TypeError


def nbt_file(root: dict) -> bytes:
    return gzip.compress(bytes([T_COMPOUND]) + _payload("") + _payload(root))


# ------------------------------------------------------------------ the farm
SX, SY, SZ = 27, 9, 25   # template size
blocks: dict[tuple[int, int, int], tuple[str, dict]] = {}


def put(x, y, z, name, props=None):
    full = name if ":" in name else f"minecraft:{name}"
    blocks[(x, y, z)] = (full, props or {})


def box(x0, y0, z0, x1, y1, z1, name, props=None, hollow=False):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if hollow and not edge:
                    continue
                put(x, y, z, name, props)


def build():
    # Ground plate: grass over dirt so the farm reads on any terrain. y=0 dirt, y=1 grass, floor level y=2.
    for x in range(SX):
        for z in range(SZ):
            put(x, 0, z, "dirt")
            put(x, 1, z, "grass_block", {"snowy": "false"})
    # ---- Barn (x 2..12, z 2..12): spruce log frame, oak plank walls, stair roof
    bx0, bz0, bx1, bz1 = 2, 2, 12, 12
    box(bx0, 2, bz0, bx1, 2, bz1, "oak_planks")                 # floor
    for y in range(3, 7):
        box(bx0, y, bz0, bx1, y, bz1, "oak_planks", hollow=True)
    for (cx, cz) in ((bx0, bz0), (bx0, bz1), (bx1, bz0), (bx1, bz1)):
        for y in range(2, 8):
            put(cx, y, cz, "spruce_log", {"axis": "y"})
    # doorway on the +z side (toward the paddock)
    for y in (3, 4):
        put(7, y, bz1, "air")
        put(6, y, bz1, "air")
    put(7, 5, bz1, "spruce_planks")
    put(6, 5, bz1, "spruce_planks")
    # windows
    for (wx, wz) in ((4, bz0), (10, bz0), (bx0, 5), (bx0, 9), (bx1, 5), (bx1, 9)):
        put(wx, 4, wz, "glass_pane", {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"})
    # roof: stepped spruce stairs, ridge along x
    for step in range(0, 5):
        y = 7 + step
        z_lo, z_hi = bz0 + step, bz1 - step
        if z_lo > z_hi:
            break
        for x in range(bx0 - 1, bx1 + 2):
            if z_lo == z_hi:
                put(x, y, z_lo, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                put(x, y, z_lo, "spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
                put(x, y, z_hi, "spruce_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
                for z in range(z_lo + 1, z_hi):
                    if step == 0:
                        put(x, y, z, "spruce_planks")
    # inside: hay, lantern, a barrel, a crafting table
    put(3, 3, 3, "hay_block", {"axis": "y"}); put(4, 3, 3, "hay_block", {"axis": "y"}); put(3, 4, 3, "hay_block", {"axis": "y"})
    put(11, 3, 3, "barrel", {"facing": "up", "open": "false"})
    put(11, 3, 4, "crafting_table")
    put(7, 6, 7, "lantern", {"hanging": "true", "waterlogged": "false"})
    # ---- Paddock (x 2..24, z 14..22): oak fence ring, gate on the barn side
    px0, pz0, px1, pz1 = 2, 14, 24, 22
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (px0, px1) or z in (pz0, pz1):
                put(x, 2, z, "oak_fence")
    put(7, 2, pz0, "oak_fence_gate", {"facing": "north", "open": "false", "powered": "false", "in_wall": "false"})
    put(6, 2, pz0, "oak_fence_gate", {"facing": "north", "open": "false", "powered": "false", "in_wall": "false"})
    # trough + hay + a gysahl patch inside the pen
    put(20, 2, 16, "water_cauldron", {"level": "3"})
    put(21, 2, 16, "hay_block", {"axis": "y"})
    for (gx, gz) in ((4, 20), (5, 20), (4, 21), (5, 21)):
        put(gx, 1, gz, "farmland", {"moisture": "7"})
        put(gx, 2, gz, "chocobosreborn:gysahl_green", {"age": "4"}, )
    put(6, 1, 20, "water", {"level": "0"})
    # torches on fence posts
    for (tx, tz) in ((px0, pz0), (px1, pz0), (px0, pz1), (px1, pz1)):
        put(tx, 3, tz, "torch")
    # sign post at the path
    put(14, 2, 12, "spruce_fence")
    put(14, 3, 12, "lantern", {"hanging": "false", "waterlogged": "false"})
    # small stable stalls along the east fence (x 22..24): straw beds
    for z in (17, 19, 21):
        put(23, 1, z, "hay_block", {"axis": "y"})


def resolve_fences():
    """Give fences their connection states so they render joined on placement."""
    fence_like = {"minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:oak_fence_gate"}
    for (x, y, z), (name, props) in list(blocks.items()):
        if name not in ("minecraft:oak_fence", "minecraft:spruce_fence"):
            continue
        p = dict(props)
        for key, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
            nb = blocks.get((x + dx, y, z + dz))
            solid = nb is not None and (nb[0] in fence_like or nb[0] in ("minecraft:oak_planks", "minecraft:spruce_log", "minecraft:hay_block"))
            p[key] = "true" if solid else "false"
        p["waterlogged"] = "false"
        blocks[(x, y, z)] = (name, p)


def entities():
    def kin(x, z, role, yaw, y=2):
        return {"blockPos": [x, y, z], "pos": [x + 0.5, float(y), z + 0.5],
                "nbt": {"id": f"{NS}:kin_steward", "Role": role, "PersistenceRequired": Byte(1),
                        "CustomNameVisible": Byte(1), "Rotation": [Float(yaw), Float(0.0)], "NoAI": Byte(0)}}

    def bird(x, z, yaw):
        return {"blockPos": [x, 2, z], "pos": [x + 0.5, 2.0, z + 0.5],
                "nbt": {"id": f"{NS}:chocobo", "Plumage": 0, "Grade": 1, "PersistenceRequired": Byte(1),
                        "Rotation": [Float(yaw), Float(0.0)]}}
    return [
        kin(15, 12, 6, 90.0),        # Farmhand by the sign post (steward behaviour; Esther herself lives in the Square)
        kin(10, 5, 7, 180.0, y=3),   # Stablehand in the barn by the barrel (floor is y=2)
        bird(9, 17, 200.0), bird(18, 20, 40.0),
    ]


def template() -> dict:
    palette = []
    index = {}
    entries = []
    for (x, y, z), (name, props) in sorted(blocks.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            state = {"Name": name}
            if props:
                state["Properties"] = {k: v for k, v in props.items()}
            palette.append(state)
        entries.append({"pos": [x, y, z], "state": index[key]})
    return {
        "size": [SX, SY + 4, SZ],
        "palette": palette,
        "blocks": entries,
        "entities": entities(),
        "DataVersion": 3955,
    }


def write_worldgen():
    def w(rel, obj):
        p = DATA / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")

    w(f"{NS}/worldgen/template_pool/chocobo_farm.json", {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": f"{NS}:chocobo_farm",
            "processors": "minecraft:empty",
            "projection": "rigid",
        }}],
    })
    w(f"{NS}/worldgen/structure/chocobo_farm.json", {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/chocobo_farm",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": f"{NS}:chocobo_farm",
        "size": 1,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
    })
    w(f"{NS}/worldgen/structure_set/chocobo_farm.json", {
        "structures": [{"structure": f"{NS}:chocobo_farm", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 34, "separation": 12,
                      "salt": 731205917},
    })
    w(f"{NS}/tags/worldgen/biome/has_structure/chocobo_farm.json", {"replace": False, "values": [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna",
        "minecraft:savanna_plateau", "minecraft:cherry_grove",
    ]})


def main():
    build()
    resolve_fences()
    out = DATA / NS / "structure" / "chocobo_farm.nbt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(nbt_file(template()))
    write_worldgen()
    print("farm:", len(blocks), "blocks,", len(entities()), "entities ->", out)


if __name__ == "__main__":
    main()
