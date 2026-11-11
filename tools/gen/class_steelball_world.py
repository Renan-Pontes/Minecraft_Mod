"""
Dados de mundo do modulo class_steelball (usado por tools/gen/class_steelball.py — rode aquele).

  - Sepultura do Santo: molde .nbt (sarcofago, pilares quebrados, velas, bau com Relicario), worldgen jigsaw de 1 peca
    com posicionamento isekai_mahou:random_spread_multi (fora das cidades/capitais/vilas), tag de bioma (merge) e tag
    de estrutura isekai_mahou:steelball_saint_tomb (a Bussola do Santo procura por ela);
  - loot do bau da sepultura (Relicario garantido, outro com 30%);
  - receitas (bola de aco e os 4 graus, Carta do Mestre, Apito do Corcel, Bussola do Santo).
"""
import math
import os
import random

import class_steelball as S
from structgen import Structure, Byte  # noqa: F401

D, MERGE, write_json = S.D, S.MERGE, S.write_json

TOMB = 13


def tomb():
    print("== Sepultura do Santo")
    s = Structure(TOMB, 8, TOMB)
    c = TOMB // 2
    rnd = random.Random(1911)
    s.fill(0, 1, 0, TOMB - 1, 7, TOMB - 1, "minecraft:air")
    # chao: praca de arenito gasta (circulo), borda de terra grossa e areia
    for x in range(TOMB):
        for z in range(TOMB):
            d = math.hypot(x - c, z - c)
            if d <= 4.6:
                b = rnd.choice(["minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:smooth_sandstone",
                                "minecraft:chiseled_sandstone" if (x + z) % 7 == 0 else "minecraft:sandstone"])
            elif d <= 6.2:
                b = rnd.choice(["minecraft:coarse_dirt", "minecraft:sand", "minecraft:sandstone", "minecraft:coarse_dirt",
                                "minecraft:gravel"])
            else:
                b = rnd.choice(["minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:grass_block", "minecraft:sand"])
            s.set(x, 0, z, b)
    # espiral de arenito vermelho no chao (o giro)
    for k in range(60):
        t = k / 59
        a = t * math.pi * 3.2
        r = 0.6 + 3.8 * t
        x, z = int(round(c + math.cos(a) * r)), int(round(c + math.sin(a) * r))
        if math.hypot(x - c, z - c) <= 4.6:
            s.set(x, 0, z, "minecraft:cut_red_sandstone")
    # sarcofago no centro (3 x 5), tampa de laje e o santo coberto (la branca)
    for x in range(c - 1, c + 2):
        for z in range(c - 2, c + 3):
            edge = x in (c - 1, c + 1) or z in (c - 2, c + 2)
            s.set(x, 1, z, "minecraft:chiseled_sandstone" if edge else "minecraft:white_wool")
            s.set(x, 2, z, "minecraft:smooth_sandstone_slab[type=bottom]")
    s.set(c, 2, c - 1, "minecraft:cut_sandstone_slab[type=bottom]")
    s.set(c, 2, c + 1, "minecraft:cut_sandstone_slab[type=bottom]")
    # bau do relicario na cabeceira (norte), virado para fora
    s.chest(c, 1, c - 3, "isekai_mahou:chests/steelball_saint_tomb", facing="north")
    s.set(c - 1, 1, c - 3, "minecraft:candle[candles=3,lit=true]")
    s.set(c + 1, 1, c - 3, "minecraft:candle[candles=2,lit=true]")
    # 4 pilares quebrados nas diagonais (alturas diferentes) com velas
    for i, (dx, dz) in enumerate(((-4, -4), (4, -4), (-4, 4), (4, 4))):
        x, z = c + dx, c + dz
        hgt = (3, 2, 4, 1)[i]
        for y in range(1, hgt + 1):
            s.set(x, y, z, "minecraft:cut_sandstone" if y < hgt else "minecraft:chiseled_sandstone")
        if hgt >= 2:
            s.set(x, hgt + 1, z, "minecraft:candle[candles=4,lit=true]" if i % 2 == 0 else "minecraft:sandstone_slab[type=bottom]")
        else:
            s.set(x, 2, z, "minecraft:sandstone_wall")
    # pilar caido (deitado) a leste e pedacos de osso
    for x in range(c + 2, c + 5):
        s.set(x, 1, c + 2, "minecraft:sandstone_wall" if x == c + 4 else "minecraft:smooth_sandstone")
    s.set(c - 3, 1, c + 1, "minecraft:bone_block[axis=x]")
    s.set(c - 4, 1, c + 1, "minecraft:bone_block[axis=x]")
    s.set(c + 3, 1, c - 2, "minecraft:dead_bush")
    s.set(c - 2, 1, c + 4, "minecraft:dead_bush")
    s.set(c + 2, 1, c + 5, "minecraft:decorated_pot")
    s.set(c - 4, 1, c - 2, "minecraft:cobweb")
    # estaca com lanterna e as ferraduras (bigorna lascada) — marca de cavaleiro
    s.set(c + 3, 1, c - 4, "minecraft:spruce_fence")
    s.set(c + 3, 2, c - 4, "minecraft:spruce_fence")
    s.set(c + 3, 3, c - 4, "minecraft:lantern[hanging=false]")
    s.set(c - 3, 1, c - 4, "minecraft:chipped_anvil[facing=east]")
    s.set(c, 1, c + 4, "minecraft:hay_block[axis=x]")
    n = s.save(os.path.join(D, "structure", "steelball_saint_tomb.nbt"), keep_air=True)
    print(f"  steelball_saint_tomb: {n} blocos")

    write_json(os.path.join(D, "worldgen/structure/steelball_saint_tomb.json"), {
        "type": "minecraft:jigsaw", "biomes": "#isekai_mahou:has_structure/steelball_saint_tomb",
        "step": "surface_structures", "spawn_overrides": {}, "terrain_adaptation": "beard_thin",
        "start_pool": "isekai_mahou:steelball_saint_tomb/start", "size": 1, "start_height": {"absolute": -1},
        "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(D, "worldgen/template_pool/steelball_saint_tomb/start.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                "location": "isekai_mahou:steelball_saint_tomb",
                                                "processors": "minecraft:empty", "projection": "rigid"}}]})
    write_json(os.path.join(D, "worldgen/structure_set/steelball_saint_tomb.json"), {
        "structures": [{"structure": "isekai_mahou:steelball_saint_tomb", "weight": 1}],
        "placement": {"type": "isekai_mahou:random_spread_multi", "spacing": 26, "separation": 9, "salt": 1946315011,
                      "exclusion_zones": [
                          {"other_set": "minecraft:villages", "chunk_count": 4},
                          {"other_set": "isekai_mahou:guild_city", "chunk_count": 10},
                          {"other_set": "isekai_mahou:capital_city", "chunk_count": 13},
                          {"other_set": "isekai_mahou:capital_sahra", "chunk_count": 13},
                          {"other_set": "isekai_mahou:capital_nordheim", "chunk_count": 13}]}})
    # tag da estrutura (a Bussola do Santo procura pela tag): nome unico do modulo, fica nos recursos
    write_json(os.path.join(D, "tags/worldgen/structure/steelball_saint_tomb.json"), {
        "replace": False, "values": ["isekai_mahou:steelball_saint_tomb"]})
    write_json(os.path.join(MERGE, "data/isekai_mahou/tags/worldgen/biome/has_structure/steelball_saint_tomb.json"), {
        "replace": False,
        "values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:desert",
                   "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands", "minecraft:savanna",
                   "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:forest", "minecraft:birch_forest",
                   "minecraft:taiga", "minecraft:snowy_plains", "minecraft:cherry_grove", "minecraft:windswept_hills"]})


def loot():
    print("== loot")

    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e

    write_json(os.path.join(D, "loot_table/chests/steelball_saint_tomb.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "isekai_mahou:steelball_relic"}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.3}],
             "entries": [{"type": "minecraft:item", "name": "isekai_mahou:steelball_relic"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                item("minecraft:gold_nugget", 20, 3, 9),
                item("minecraft:bone", 14, 1, 4),
                item("minecraft:golden_carrot", 10, 1, 4),
                item("minecraft:iron_ingot", 10, 1, 3),
                item("minecraft:candle", 8, 1, 3),
                item("isekai_mahou:essence_earth", 6),
                item("isekai_mahou:essence_wind", 6),
                item("minecraft:saddle", 4),
                item("minecraft:golden_apple", 3),
                item("minecraft:name_tag", 2),
                item("isekai_mahou:mana_crystal", 4)]}]})


def recipes():
    print("== receitas")
    R = os.path.join(D, "recipe")

    def shaped(name, pattern, keys, result, count=1, cat="equipment"):
        write_json(os.path.join(R, name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
            "key": {k: {"item": v} for k, v in keys.items()}, "result": {"id": result, "count": count}})

    def shapeless(name, items, result, count=1, cat="equipment"):
        write_json(os.path.join(R, name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": cat,
            "ingredients": [{"item": i} for i in items], "result": {"id": result, "count": count}})

    shaped("steel_ball", [" I ", "IEI", " I "], {"I": "minecraft:iron_ingot", "E": "isekai_mahou:essence_earth"},
           "isekai_mahou:steel_ball")
    shapeless("steel_ball_t1", ["isekai_mahou:steel_ball", "minecraft:iron_block", "isekai_mahou:essence_earth"],
              "isekai_mahou:steel_ball_t1")
    shapeless("steel_ball_t2", ["isekai_mahou:steel_ball_t1", "minecraft:gold_ingot", "minecraft:gold_ingot",
                                "isekai_mahou:essence_wind", "isekai_mahou:essence_earth"], "isekai_mahou:steel_ball_t2")
    shapeless("steel_ball_t3", ["isekai_mahou:steel_ball_t2", "minecraft:diamond", "minecraft:diamond",
                                "minecraft:gold_block", "isekai_mahou:essence_earth", "isekai_mahou:essence_wind"],
              "isekai_mahou:steel_ball_t3")
    shapeless("steel_ball_t4", ["isekai_mahou:steel_ball_t3", "minecraft:netherite_ingot", "isekai_mahou:steelball_relic",
                                "isekai_mahou:mana_crystal", "isekai_mahou:essence_earth"], "isekai_mahou:steel_ball_t4")
    shapeless("steelball_mentor_letter", ["minecraft:paper", "minecraft:feather", "minecraft:ink_sac"],
              "isekai_mahou:steelball_mentor_letter", cat="misc")
    shaped("steelball_horse_whistle", ["  S", "IG ", "II "], {"S": "minecraft:string", "I": "minecraft:iron_nugget",
                                                             "G": "minecraft:gold_nugget"},
           "isekai_mahou:steelball_horse_whistle", cat="misc")
    shaped("steelball_saint_compass", [" G ", "GCG", " E "], {"G": "minecraft:gold_ingot", "C": "minecraft:compass",
                                                              "E": "isekai_mahou:essence_earth"},
           "isekai_mahou:steelball_saint_compass", cat="misc")


def run():
    tomb()
    loot()
    recipes()
