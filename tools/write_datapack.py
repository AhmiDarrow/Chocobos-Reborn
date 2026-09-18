"""Write src/main/resources/data/** for ChocobosReborn (1.21.1 pack layout).

Loot tables, recipes, tags, compostables, biome spawns, the global loot
modifier that drops gysahl seeds from grass, and the Chocobo Square
dimension. Deterministic; re-run after changing ids.

    python tools/write_datapack.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data"
NS = "chocobosreborn"


def w(rel: str, obj):
    p = DATA / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def item_entry(item, count=None, extra_funcs=None, conditions=None):
    e = {"type": "minecraft:item", "name": item}
    funcs = []
    if count is not None:
        if isinstance(count, tuple):
            funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
        else:
            funcs.append({"function": "minecraft:set_count", "count": count})
    if extra_funcs:
        funcs.extend(extra_funcs)
    if funcs:
        e["functions"] = funcs
    if conditions:
        e["conditions"] = conditions
    return e


def block_loot(block, entries, block_type="minecraft:block"):
    return {"type": block_type, "pools": [{"rolls": 1, "entries": entries}]}


def main():
    # ------------------------------------------------------------ loot tables
    w(f"{NS}/loot_table/blocks/square_gate.json", block_loot("square_gate", [item_entry(f"{NS}:square_gate")]))
    w(f"{NS}/loot_table/blocks/boost_pad.json", block_loot("boost_pad", [item_entry(f"{NS}:boost_pad")]))
    # crop: seeds always; greens only when grown (age 4). Fortune adds seeds.
    grown = [{"condition": "minecraft:block_state_property", "block": f"{NS}:gysahl_green",
              "properties": {"age": "4"}}]
    w(f"{NS}/loot_table/blocks/gysahl_green.json", {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [item_entry(f"{NS}:gysahl_green_seeds")]},
            {"rolls": 1, "conditions": grown, "entries": [item_entry(f"{NS}:gysahl_green", (1, 2))]},
            {"rolls": 1, "conditions": grown, "entries": [item_entry(f"{NS}:gysahl_green_seeds", (0, 2), [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                 "formula": "minecraft:binomial_with_bonus_count", "parameters": {"extra": 3, "probability": 0.5714286}}])]},
            # a gysahl patch sometimes throws a better green, or a Pepio nut (FF7 farm luck)
            {"rolls": 1, "conditions": grown + [{"condition": "minecraft:random_chance", "chance": 0.08}],
             "entries": [item_entry(f"{NS}:krakka_green"), item_entry(f"{NS}:tantal_green")]},
            {"rolls": 1, "conditions": grown + [{"condition": "minecraft:random_chance", "chance": 0.05}],
             "entries": [item_entry(f"{NS}:pepio_nut")]},
        ],
    })

    # ----------------------------------------------------------------- recipes
    def shaped(name, pattern, key, result, count=1):
        w(f"{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                        "pattern": pattern, "key": key,
                                        "result": {"id": result, "count": count}})

    def shapeless(name, ingredients, result, count=1):
        w(f"{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                        "ingredients": ingredients, "result": {"id": result, "count": count}})

    shaped("chocobo_saddle", ["LLL", "SIS"], {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:string"},
                                             "I": {"item": "minecraft:iron_ingot"}}, f"{NS}:chocobo_saddle")
    # Chocobo Lure: a materia-like orb; amethyst around a gysahl green
    shaped("chocobo_lure", [" A ", "AYA", " A "], {"A": {"item": "minecraft:amethyst_shard"},
                                                    "Y": {"item": f"{NS}:gysahl_green"}}, f"{NS}:chocobo_lure")
    # cheap greens can be grown up from gysahl; the good ones come from the Sage, prizes, and farm luck
    shapeless("krakka_green", [{"item": f"{NS}:gysahl_green"}, {"item": f"{NS}:gysahl_green"},
                               {"item": "minecraft:bone_meal"}], f"{NS}:krakka_green")
    shapeless("tantal_green", [{"item": f"{NS}:gysahl_green"}, {"item": f"{NS}:gysahl_green"},
                               {"item": "minecraft:sugar"}], f"{NS}:tantal_green")
    shapeless("chocobo_almanac", [{"item": "minecraft:book"}, {"item": f"{NS}:gysahl_green"}], f"{NS}:chocobo_almanac")
    for tier, mat in (("leather", "minecraft:leather"), ("iron", "minecraft:iron_ingot"), ("diamond", "minecraft:diamond")):
        shaped(f"{tier}_chocobo_armor", ["X X", "XXX", "X X"], {"X": {"item": mat}}, f"{NS}:{tier}_chocobo_armor")
    shapeless("netherite_chocobo_armor", [{"item": f"{NS}:diamond_chocobo_armor"}, {"item": "minecraft:netherite_ingot"}],
              f"{NS}:netherite_chocobo_armor")
    shaped("saddlebags", ["LLL", "LCL", "LLL"], {"L": {"item": "minecraft:leather"}, "C": {"item": "minecraft:chest"}}, f"{NS}:saddlebags")
    shaped("chocobo_pocketwatch", [" N ", "NCN", " Y "], {"N": {"item": "minecraft:gold_nugget"}, "C": {"item": "minecraft:clock"},
                                                          "Y": {"item": f"{NS}:gysahl_green"}}, f"{NS}:chocobo_pocketwatch")

    # -------------------------------------------------------------------- tags
    w("minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"{NS}:square_gate"]})
    w("minecraft/tags/block/crops.json", {"replace": False, "values": [f"{NS}:gysahl_green"]})
    w("minecraft/tags/block/maintains_farmland.json", {"replace": False, "values": [f"{NS}:gysahl_green"]})
    w("minecraft/tags/item/villager_plantable_seeds.json", {"replace": False, "values": [f"{NS}:gysahl_green_seeds"]})
    w("minecraft/tags/item/chicken_food.json", {"replace": False, "values": [f"{NS}:gysahl_green_seeds"]})
    w("neoforge/data_maps/item/compostables.json", {"values": {
        f"{NS}:gysahl_green_seeds": {"chance": 0.3},
        f"{NS}:gysahl_green": {"chance": 0.65},
        f"{NS}:krakka_green": {"chance": 0.65},
        f"{NS}:tantal_green": {"chance": 0.65},
        f"{NS}:pahsana_green": {"chance": 0.65},
        f"{NS}:curiel_green": {"chance": 0.65},
        f"{NS}:mimett_green": {"chance": 0.65},
        f"{NS}:reagan_green": {"chance": 0.65},
        f"{NS}:sylkis_green": {"chance": 0.65},
    }})

    # ------------------------------------------------------------------ spawns
    w(f"{NS}/neoforge/biome_modifier/chocobo_overworld.json", {
        "type": "neoforge:add_spawns",
        "biomes": "#minecraft:is_overworld",
        "spawners": [{"type": f"{NS}:chocobo", "weight": 6, "minCount": 2, "maxCount": 4}],
    })
    w(f"{NS}/neoforge/biome_modifier/chocobo_cold_pads.json", {
        "type": "neoforge:add_spawns",
        "biomes": ["minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga",
                   "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks",
                   "minecraft:grove", "minecraft:frozen_river"],
        "spawners": [{"type": f"{NS}:chocobo", "weight": 10, "minCount": 1, "maxCount": 3}],
    })
    w(f"{NS}/neoforge/biome_modifier/chocobo_nether.json", {
        "type": "neoforge:add_spawns",
        "biomes": ["minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:nether_wastes"],
        "spawners": [{"type": f"{NS}:chocobo", "weight": 3, "minCount": 1, "maxCount": 2}],
    })
    w(f"{NS}/neoforge/biome_modifier/chocobo_end.json", {
        "type": "neoforge:add_spawns",
        "biomes": ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands", "minecraft:end_barrens"],
        "spawners": [{"type": f"{NS}:chocobo", "weight": 2, "minCount": 1, "maxCount": 2}],
    })

    # ------------------------------------------------- gysahl seeds from grass
    w("neoforge/loot_modifiers/global_loot_modifiers.json", {"replace": False,   # NeoForge reads only this path
                                                           "entries": [f"{NS}:gysahl_seeds_from_grass",
                                                                       f"{NS}:carob_from_ravager",
                                                                       f"{NS}:zeio_from_piglin_brute"]})
    # FF7: Carob Nuts come from the Vlakorados, Zeio Nuts from the goblins of Goblin Island.
    w(f"{NS}/loot_modifiers/carob_from_ravager.json", {
        "type": f"{NS}:add_item",
        "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
                        "predicate": {"type": "minecraft:ravager"}}],
        "item": f"{NS}:carob_nut", "count": 1, "chance": 0.5,
    })
    w(f"{NS}/loot_modifiers/zeio_from_piglin_brute.json", {
        "type": f"{NS}:add_item",
        "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
                        "predicate": {"type": "minecraft:piglin_brute"}}],
        "item": f"{NS}:zeio_nut", "count": 1, "chance": 0.12,
    })
    w(f"{NS}/loot_modifiers/gysahl_seeds_from_grass.json", {
        "type": f"{NS}:add_item",
        "conditions": [{"condition": "minecraft:any_of", "terms": [
            {"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:hoes"}},
            {"condition": "minecraft:random_chance", "chance": 0.12},
        ]}, {"condition": "minecraft:any_of", "terms": [
            {"condition": "minecraft:block_state_property", "block": "minecraft:short_grass"},
            {"condition": "minecraft:block_state_property", "block": "minecraft:tall_grass"},
            {"condition": "minecraft:block_state_property", "block": "minecraft:fern"},
            {"condition": "minecraft:block_state_property", "block": "minecraft:large_fern"},
        ]}],
        "item": f"{NS}:gysahl_green_seeds",
        "count": 1,
        "chance": 1.0,
    })

    # ------------------------------------------------- Chocobo Square dimension
    w(f"{NS}/dimension_type/square.json", {
        "ultrawarm": False, "natural": False, "piglin_safe": False, "respawn_anchor_works": False,
        "bed_works": False, "has_raids": False, "has_skylight": True, "has_ceiling": False,
        "coordinate_scale": 1.0, "ambient_light": 0.0,
        "logical_height": 384, "effects": f"{NS}:square", "infiniburn": "#minecraft:infiniburn_overworld",
        "min_y": -64, "height": 384, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
    })
    w(f"{NS}/dimension/square.json", {
        "type": f"{NS}:square",
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": "minecraft:the_void",   # nothing spawns, nothing grows
                "lakes": False, "features": False,
                "structure_overrides": [],  # no villages, farms or anything else in the Square
                "layers": [],               # the Square is a void: the village and the courses are islands
            },
        },
    })
    # ------------------------------------------------------------ advancements
    def adv(name, parent, icon, title, desc, criteria, frame="task", background=None, hidden=False):
        d = {"display": {"icon": {"id": icon}, "title": {"translate": f"advancement.{NS}.{name}"},
                         "description": {"translate": f"advancement.{NS}.{name}.desc"},
                         "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": hidden},
             "criteria": criteria}
        if parent:
            d["parent"] = f"{NS}:{parent}"
        if background:
            d["display"]["background"] = background
        w(f"{NS}/advancement/{name}.json", d)

    choc = f"{NS}:chocobo"
    adv("root", None, f"{NS}:gysahl_green", "", "", {"seeds": {"trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": f"{NS}:gysahl_green_seeds"}]}}},
        background="minecraft:textures/block/hay_block_side.png")
    adv("tame", "root", f"{NS}:gysahl_green", "", "", {"tame": {"trigger": "minecraft:tame_animal",
        "conditions": {"entity": {"type": choc}}}})
    adv("ride", "tame", f"{NS}:chocobo_saddle", "", "", {"ride": {"trigger": "minecraft:started_riding",
        "conditions": {"player": {"vehicle": {"type": choc}}}}})
    adv("hatch", "tame", f"{NS}:pepio_nut", "", "", {"bred": {"trigger": "minecraft:bred_animals",
        "conditions": {"child": {"type": choc}}}})
    adv("green_or_blue", "hatch", f"{NS}:carob_nut", "", "", {
        "green": {"trigger": "minecraft:bred_animals", "conditions": {"child": {"type": choc, "nbt": "{Plumage:1}"}}},
        "blue": {"trigger": "minecraft:bred_animals", "conditions": {"child": {"type": choc, "nbt": "{Plumage:2}"}}}},
        frame="goal")
    w(f"{NS}/advancement/green_or_blue.json", {**json.loads((DATA / f"{NS}/advancement/green_or_blue.json").read_text()),
                                               "requirements": [["green", "blue"]]})
    adv("black", "green_or_blue", f"{NS}:black_chocobo_spawn_egg", "", "", {"black": {"trigger": "minecraft:bred_animals",
        "conditions": {"child": {"type": choc, "nbt": "{Plumage:4}"}}}}, frame="goal")
    adv("gold", "black", f"{NS}:zeio_nut", "", "", {"gold": {"trigger": "minecraft:bred_animals",
        "conditions": {"child": {"type": choc, "nbt": "{Plumage:5}"}}}}, frame="challenge")
    adv("square", "ride", f"{NS}:gp", "", "", {"enter": {"trigger": "minecraft:impossible"}})
    adv("first_place", "square", f"{NS}:square_gate", "", "", {"win": {"trigger": "minecraft:impossible"}}, frame="goal")
    adv("class_s", "first_place", f"{NS}:sylkis_green", "", "", {"s": {"trigger": "minecraft:impossible"}}, frame="challenge")
    adv("lure", "root", f"{NS}:chocobo_lure", "", "", {"lure": {"trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": f"{NS}:chocobo_lure"}]}}})
    adv("farm", "root", "minecraft:hay_block", "", "", {"farm": {"trigger": "minecraft:location",
        "conditions": {"player": {"location": {"structures": f"{NS}:chocobo_farm"}}}}})

    print("datapack written under", DATA)


if __name__ == "__main__":
    main()
