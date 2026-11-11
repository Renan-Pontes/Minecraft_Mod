"""
Mundo do modulo hueco_mundo: dimensoes (Mundo Oco e Reino das Fadas) por JSON, biomas, features, estruturas do
Mundo Normal (Fenda da Garganta, Circulo das Fadas), moldes dos lugares (Mirante, Palacio da Noite Eterna, Corte das
Fadas), loot, receitas e tags. Chamado por tools/gen/hueco_mundo.py.

Coordenadas dos moldes = constantes de HmSites.java (piso, chegada, lugar dos chefes): mude nos dois juntos.
"""
import math
import os
import random
import sys

from hueco_mundo_lib import ROOT, DATA, MERGE, write_json

sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure  # noqa: E402

NS = "isekai_mahou"
STRUCT = os.path.join(DATA, "structure")


def ns(x):
    return x if ":" in x else NS + ":" + x


# =====================================================================================
#  Dimensoes
# =====================================================================================
def noise(name, first, amps):
    write_json(os.path.join(DATA, "worldgen/noise", name + ".json"), {"firstOctave": first, "amplitudes": amps})


def n2d(name):
    return {"type": "minecraft:noise", "noise": ns(name), "xz_scale": 1.0, "y_scale": 0.0}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def gradient(y0, y1):
    return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": 1.0, "to_value": -1.0}


def router(temperature, final):
    return {"barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": temperature, "vegetation": 0.0, "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
            "initial_density_without_jaggedness": final, "final_density": {"type": "minecraft:interpolated", "argument": final},
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0}


def block(name, props=None):
    b = {"Name": ns(name)}
    if props:
        b["Properties"] = props
    return {"type": "minecraft:block", "result_state": b}


def cond(if_true, then):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then}


def depth(add_surface, secondary=0, offset=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "add_surface_depth": add_surface,
            "secondary_depth_range": secondary, "surface_type": "floor"}


def bedrock(name):
    return cond({"type": "minecraft:vertical_gradient", "random_name": ns(name),
                 "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
                block("minecraft:bedrock"))


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def dim_type(name, fixed_time, ambient):
    write_json(os.path.join(DATA, "dimension_type", name + ".json"), {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "piglin_safe": False,
        "respawn_anchor_works": False, "has_raids": False, "min_y": 0, "height": 256, "logical_height": 256,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "monster_spawn_block_light_limit": 0, "has_skylight": True, "has_ceiling": False, "ambient_light": ambient,
        "fixed_time": fixed_time, "bed_works": False, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": ns(name)})


def biome_point(biome, t0, t1):
    return {"biome": ns(biome), "parameters": {"temperature": [t0, t1], "humidity": 0.0, "continentalness": 0.0,
                                                "erosion": 0.0, "weirdness": 0.0, "depth": 0.0, "offset": 0.0}}


def dimension(name, biomes):
    write_json(os.path.join(DATA, "dimension", name + ".json"), {
        "type": ns(name),
        "generator": {"type": "minecraft:noise", "settings": ns(name),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})


def hollow_dimension():
    noise("hm_hollow_biome", -9, [1.0, 0.5])
    noise("hm_hollow_dunes", -7, [1.0, 0.6, 0.2])
    noise("hm_hollow_broad", -9, [1.0, 0.5])
    noise("hm_hollow_ripples", -4, [1.0, 0.5])
    # Dunas: cristas (0,5 - |n|) sobre ondulacao larga e ondinhas de vento; chao perto de y 64.
    dunes = mul(0.30, add(0.5, mul(-1.0, {"type": "minecraft:abs", "argument": n2d("hm_hollow_dunes")})))
    final = add(gradient(24, 104), add(dunes, add(mul(0.20, n2d("hm_hollow_broad")), mul(0.05, n2d("hm_hollow_ripples")))))
    surface = seq(
        bedrock("hm_hollow_bedrock"),
        cond(depth(True, 6), block("hollow_sand")),
        cond(depth(True, 26), block("hollow_sandstone")),
    )
    write_json(os.path.join(DATA, "worldgen/noise_settings/hollow_world.json"), {
        "sea_level": 0, "disable_mob_generation": False, "aquifers_enabled": False, "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": ns("hollow_sandstone")},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "spawn_target": [],
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 2, "size_vertical": 2},
        "noise_router": router(n2d("hm_hollow_biome"), final),
        "surface_rule": surface})
    dim_type("hollow_world", 18000, 0.12)
    dimension("hollow_world", [biome_point("hollow_dunes", -0.45, 0.30), biome_point("quartz_forest", 0.30, 1.0),
                               biome_point("bone_wastes", -1.0, -0.45)])


def fey_dimension():
    noise("hm_fey_biome", -8, [1.0, 0.5])
    noise("hm_fey_hills", -8, [1.0, 0.6, 0.3])
    noise("hm_fey_detail", -5, [1.0, 0.5])
    final = add(gradient(30, 120), add(mul(0.36, n2d("hm_fey_hills")), mul(0.10, n2d("hm_fey_detail"))))
    dry = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
    moss = {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.15,
            "max_threshold": 1.7976931348623157e+308}
    surface = seq(
        bedrock("hm_fey_bedrock"),
        cond(depth(False), seq(
            cond(dry, seq(
                cond({"type": "minecraft:biome", "biome_is": [ns("glowcap_hollow")]}, cond(moss, block("minecraft:moss_block"))),
                block("minecraft:grass_block", {"snowy": "false"}))),
            block("minecraft:sand"))),
        cond(depth(True), seq(cond(dry, block("minecraft:dirt")), block("minecraft:sand"))),
    )
    write_json(os.path.join(DATA, "worldgen/noise_settings/fey_realm.json"), {
        "sea_level": 62, "disable_mob_generation": False, "aquifers_enabled": False, "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": "minecraft:stone"},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "spawn_target": [],
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 2, "size_vertical": 2},
        "noise_router": router(n2d("hm_fey_biome"), final),
        "surface_rule": surface})
    dim_type("fey_realm", 13000, 0.14)
    dimension("fey_realm", [biome_point("fey_glade", -1.0, 0.25), biome_point("glowcap_hollow", 0.25, 1.0)])


# =====================================================================================
#  Features e biomas
# =====================================================================================
def configured(name, obj):
    write_json(os.path.join(DATA, "worldgen/configured_feature", name + ".json"), obj)


def placed(name, feature, placement):
    write_json(os.path.join(DATA, "worldgen/placed_feature", name + ".json"), {"feature": ns(feature), "placement": placement})


def surface_place(count=None, rarity=None, heightmap="WORLD_SURFACE_WG", dry=False):
    p = []
    if count is not None:
        p.append({"type": "minecraft:count", "count": count})
    if rarity is not None:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    p.append({"type": "minecraft:in_square"})
    if dry:
        p.append({"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0})
    p.append({"type": "minecraft:heightmap", "heightmap": heightmap})
    p.append({"type": "minecraft:biome"})
    return p


def patch(name, state, ground, tries, spread):
    configured(name, {"type": "minecraft:random_patch", "config": {
        "tries": tries, "xz_spread": spread, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {
            "to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": ns(state)}}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": [ns(g) for g in ground]}]}}]}}})


def features():
    for f in ("hm_quartz_tree", "hm_hollow_ruin", "hm_bone_remains", "hm_fey_mushroom"):
        configured(f, {"type": ns(f), "config": {}})
    patch("hm_crystal_patch", "hollow_crystal", ["hollow_sand", "hollow_sandstone"], 10, 4)
    patch("hm_moonpetal_patch", "moonpetal", ["minecraft:grass_block", "minecraft:moss_block"], 24, 5)
    # Mundo Oco
    placed("hm_ruins_common", "hm_hollow_ruin", surface_place(rarity=14))
    placed("hm_ruins_rare", "hm_hollow_ruin", surface_place(rarity=30))
    placed("hm_bones_common", "hm_bone_remains", surface_place(rarity=4))
    placed("hm_bones_rare", "hm_bone_remains", surface_place(rarity=12))
    placed("hm_quartz_trees_dense", "hm_quartz_tree", surface_place(count=5))
    placed("hm_quartz_trees_sparse", "hm_quartz_tree", surface_place(rarity=6))
    placed("hm_crystals_dense", "hm_crystal_patch", surface_place(rarity=2, heightmap="MOTION_BLOCKING"))
    placed("hm_crystals_sparse", "hm_crystal_patch", surface_place(rarity=10, heightmap="MOTION_BLOCKING"))
    # Reino das Fadas
    placed("hm_fey_mushrooms_dense", "hm_fey_mushroom", surface_place(count=3, heightmap="OCEAN_FLOOR", dry=True))
    placed("hm_fey_mushrooms_sparse", "hm_fey_mushroom", surface_place(rarity=5, heightmap="OCEAN_FLOOR", dry=True))
    placed("hm_moonpetals", "hm_moonpetal_patch", surface_place(rarity=2, heightmap="MOTION_BLOCKING"))


def spawn(e, w, a, b):
    return {"type": ns(e), "weight": w, "minCount": a, "maxCount": b}


def biome(name, temp, effects, monsters, surface_feats, veg_feats):
    feats = [[] for _ in range(11)]
    feats[4] = [ns(f) for f in surface_feats]   # surface_structures
    feats[9] = [ns(f) for f in veg_feats]       # vegetal_decoration
    write_json(os.path.join(DATA, "worldgen/biome", name + ".json"), {
        "has_precipitation": False, "temperature": temp, "downfall": 0.0, "effects": effects,
        "spawners": {"monster": monsters, "creature": [], "ambient": [], "axolotls": [], "underground_water_creature": [],
                     "water_creature": [], "water_ambient": [], "misc": []},
        "spawn_costs": {}, "carvers": {}, "features": feats})


def hollow_effects(fog):
    return {"sky_color": 0x05070D, "fog_color": fog, "water_color": 0x2E4A7A, "water_fog_color": 0x060A18,
            "particle": {"options": {"type": "minecraft:white_ash"}, "probability": 0.0035},
            "ambient_sound": "minecraft:ambient.soul_sand_valley.loop",
            "mood_sound": {"sound": "minecraft:ambient.soul_sand_valley.mood", "tick_delay": 6000, "block_search_extent": 8,
                           "offset": 2.0},
            "additions_sound": {"sound": "minecraft:ambient.soul_sand_valley.additions", "tick_chance": 0.0111},
            "music": {"sound": "minecraft:music.nether.soul_sand_valley", "min_delay": 12000, "max_delay": 24000,
                      "replace_current_music": False}}


def fey_effects(particle, prob):
    return {"sky_color": 0x24123A, "fog_color": 0x5A3A7A, "water_color": 0x7AD8FF, "water_fog_color": 0x2A1A4A,
            "grass_color": 0x7ED68A, "foliage_color": 0xE88AD0,
            "particle": {"options": {"type": particle}, "probability": prob},
            "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
            "music": {"sound": "minecraft:music.overworld.cherry_grove", "min_delay": 12000, "max_delay": 24000,
                      "replace_current_music": False}}


def biomes():
    # Ordem global das features (a mesma em todos os biomas do reino — evita ciclo de ordem).
    hollow = [
        ("hollow_dunes", hollow_effects(0x1B2130),
         [spawn("hollow_crawler", 60, 2, 4), spawn("hollow_wraith", 30, 1, 2), spawn("hollow_brute", 18, 1, 1),
          spawn("arrancar", 2, 1, 1)],
         ["hm_ruins_common", "hm_bones_rare"], ["hm_quartz_trees_sparse", "hm_crystals_sparse"]),
        ("quartz_forest", hollow_effects(0x1D2436),
         [spawn("hollow_crawler", 40, 1, 3), spawn("hollow_brute", 20, 1, 1), spawn("hollow_wraith", 20, 1, 2),
          spawn("menos_giant", 3, 1, 1), spawn("arrancar", 4, 1, 1)],
         ["hm_ruins_rare"], ["hm_quartz_trees_dense", "hm_crystals_dense"]),
        ("bone_wastes", hollow_effects(0x221F28),
         [spawn("hollow_crawler", 50, 2, 5), spawn("hollow_brute", 25, 1, 2), spawn("hollow_wraith", 25, 1, 3),
          spawn("arrancar", 3, 1, 1), spawn("menos_giant", 1, 1, 1)],
         ["hm_ruins_common", "hm_bones_common"], ["minecraft:patch_dead_bush", "hm_crystals_sparse"]),
    ]
    for name, eff, mobs, surf, veg in hollow:
        biome(name, 0.6, eff, mobs, surf, veg)
    fey = [
        ("fey_glade", fey_effects("minecraft:cherry_leaves", 0.012),
         [spawn("fey_pixie", 40, 1, 3), spawn("bramble_hound", 25, 1, 2)],
         [], ["minecraft:trees_cherry", "hm_fey_mushrooms_sparse", "hm_moonpetals", "minecraft:flower_cherry",
              "minecraft:patch_grass_forest"]),
        ("glowcap_hollow", fey_effects("minecraft:spore_blossom_air", 0.006),
         [spawn("fey_pixie", 30, 2, 4), spawn("bramble_hound", 35, 1, 3)],
         [], ["hm_fey_mushrooms_dense", "hm_moonpetals", "minecraft:patch_grass_forest",
              "minecraft:brown_mushroom_normal"]),
    ]
    for name, eff, mobs, surf, veg in fey:
        biome(name, 0.7, eff, mobs, surf, veg)


# =====================================================================================
#  Moldes
# =====================================================================================
SB = ns("hollow_sandstone_bricks")
SS = ns("hollow_sandstone")
SAND = ns("hollow_sand")
CRYSTAL = ns("hollow_crystal")
QB = "minecraft:quartz_bricks"
QP = "minecraft:quartz_pillar[axis=y]"
CQ = "minecraft:chiseled_quartz_block"
SQ = "minecraft:smooth_quartz"
DARK = "minecraft:polished_blackstone_bricks"
BONE = "minecraft:bone_block[axis=y]"
SEA = "minecraft:sea_lantern"
SOUL = "minecraft:soul_lantern[hanging=false]"
SOUL_HANG = "minecraft:soul_lantern[hanging=true]"
TRAVEL = ns("travel_stone")


def disk(s, cx, cz, r, y, state, rmin=-1.0):
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if rmin < d <= r + 0.5 and (rmin < 0 or d >= rmin):
                s.set(x, y, z, state)


def hollow_hub():
    """Mirante da Garganta: plataforma redonda de arenito com pilares de quartzo (chegada do Mundo Oco)."""
    s = Structure(17, 12, 17)
    c = 8
    disk(s, c, c, 8.4, 0, SS)
    disk(s, c, c, 8.4, 1, SS)
    disk(s, c, c, 8.4, 2, SB)
    disk(s, c, c, 6.4, 2, QB, rmin=5.5)
    s.fill(7, 2, 7, 9, 2, 9, CQ)
    s.set(8, 2, 8, SEA)
    s.set(8, 2, 4, CQ)
    # Parapeito com aberturas nos quatro lados.
    for x in range(17):
        for z in range(17):
            d = math.hypot(x - c, z - c)
            if 7.6 <= d <= 8.5 and abs(x - c) > 1 and abs(z - c) > 1:
                s.set(x, 3, z, SB)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        x, z = round(c + math.cos(a) * 7), round(c + math.sin(a) * 7)
        s.fill(x, 3, z, x, 7, z, QP)
        s.set(x, 8, z, CQ)
        s.set(x, 9, z, SOUL)
    # Arco do Mirante (olha para o norte, onde fica o palacio).
    for x in (5, 11):
        s.fill(x, 3, 1, x, 8, 1, QP)
    s.fill(5, 9, 1, 11, 9, 1, QB)
    s.set(8, 9, 1, CQ)
    s.set(8, 10, 1, CRYSTAL)
    for x in (6, 10):
        s.set(x, 3, 1, "minecraft:air")
    # Altar da volta (o portal abre 3 blocos ao sul dele) e pedra de viagem.
    s.set(8, 3, 11, ns("garganta_altar"))
    s.set(12, 3, 6, TRAVEL)
    s.chest(4, 3, 11, ns("chests/hollow_hub"), facing="east")
    for (x, z) in ((3, 6), (13, 10), (5, 14), (11, 13)):
        s.set(x, 3, z, CRYSTAL)
    for (x, z) in ((1, 9), (15, 7)):
        s.set(x, 4, z, "minecraft:skeleton_skull[rotation=4]")
    s.save(os.path.join(STRUCT, "hollow_hub.nbt"), keep_air=True)


def palace():
    """Palacio da Noite Eterna (81x46x81): Jardim das Areias (sul, Imperatriz), Salao Central e Sala do Trono (norte, Rei)."""
    W = 81
    s = Structure(W, 46, W)
    rnd = random.Random(1717)
    s.fill(0, 0, 0, 80, 2, 80, SS)
    for x in range(W):
        for z in range(W):
            st = QB if (x % 8 == 0 or z % 8 == 0) else SB
            if x % 16 == 8 and z % 16 == 8:
                st = SEA
            s.set(x, 3, z, st)

    def wall_block(y):
        return DARK if y == 4 else QB if y in (12, 19) else SB

    # Muralha externa (2 de espessura, 16 de altura) com ameias e seteiras.
    for y in range(4, 20):
        st = wall_block(y)
        for i in range(2, 79):
            for t in (2, 3, 77, 78):
                s.set(i, y, t, st)
                s.set(t, y, i, st)
    for i in range(2, 79):
        if i % 2 == 0:
            for t in (2, 78):
                s.set(i, 20, t, SB)
                s.set(t, 20, i, SB)
    for i in range(8, 73, 8):
        for y in (14, 15, 16):
            for t in (2, 3, 77, 78):
                s.set(i, y, t, "minecraft:air")
                s.set(t, y, i, "minecraft:air")
    # Torres dos cantos.
    for (cx, cz) in ((4, 4), (76, 4), (4, 76), (76, 76)):
        for y in range(4, 29):
            disk(s, cx, cz, 4, y, wall_block(y) if y < 20 else SB)
        disk(s, cx, cz, 4.4, 29, QB, rmin=3.5)
        for k, r in enumerate((3, 2, 1)):
            disk(s, cx, cz, r, 30 + k, QB)
        s.set(cx, 33, cz, CQ)
        s.set(cx, 34, cz, CRYSTAL)
        for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            s.set(cx + dx, 30, cz + dz, SOUL)
    # Portao sul + torres do portao.
    for x in (33, 34, 35, 45, 46, 47):
        for z in range(76, 81):
            for y in range(4, 25):
                s.set(x, y, z, wall_block(y) if y < 20 else SB)
            if x in (33, 35, 45, 47) and z in (76, 80):
                s.set(x, 25, z, SB)
    s.set(34, 25, 78, CQ)
    s.set(34, 26, 78, CRYSTAL)
    s.set(46, 25, 78, CQ)
    s.set(46, 26, 78, CRYSTAL)
    for x in range(36, 45):
        for z in range(76, 81):
            for y in range(4, 14 if 37 <= x <= 43 else 13):
                s.set(x, y, z, "minecraft:air")
    s.fill(36, 14, 77, 44, 14, 78, CQ)

    # Paredes internas: Sala do Trono (z 4..33) | Salao (z 36..43) | Jardim (z 46..76).
    for z in (34, 35, 44, 45):
        for x in range(4, 77):
            for y in range(4, 20):
                inside_door = 33 <= x <= 47 and y <= 14
                s.set(x, y, z, "minecraft:air" if inside_door else wall_block(y))
        for x in (32, 48):
            for y in range(4, 16):
                s.set(x, y, z, QP)
        s.fill(32, 15, z, 48, 15, z, CQ)

    # Teto da Sala do Trono e do Salao, com oculo sobre o trono e lanternas penduradas.
    for x in range(4, 77):
        for z in range(4, 46):
            if math.hypot(x - 40, z - 19) <= 4.5:
                continue
            s.set(x, 20, z, SQ if (x + z) % 7 else QB)
    for x in range(10, 72, 10):
        for z in (9, 19, 29, 39):
            if math.hypot(x - 40, z - 19) > 5.5:
                s.set(x, 19, z, SOUL_HANG)
    disk(s, 40, 19, 5.5, 21, QB, rmin=4.6)
    # Cupula sobre o salao + agulha com o cristal da lua.
    cx, cy, cz, r = 40, 21, 35, 10
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            for y in range(cy, cy + r + 1):
                d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)
                if abs(d - r) <= 0.55:
                    s.set(x, y, z, QB if (y - cy) % 4 == 0 else SQ)
    s.fill(40, 31, 35, 40, 38, 35, QP)
    s.set(40, 39, 35, CQ)
    s.set(40, 40, 35, CRYSTAL)

    # --- Sala do Trono (Rei Vazio em 40,4,19) ---
    s.fill(31, 4, 4, 49, 4, 10, SB)
    s.fill(31, 4, 11, 49, 4, 11, "minecraft:quartz_slab[type=bottom]")
    s.fill(35, 5, 4, 45, 5, 7, QB)
    s.fill(35, 5, 8, 45, 5, 8, "minecraft:quartz_slab[type=bottom]")
    s.set(40, 6, 5, "minecraft:quartz_stairs[facing=south]")
    s.set(39, 6, 5, "minecraft:quartz_slab[type=bottom]")
    s.set(41, 6, 5, "minecraft:quartz_slab[type=bottom]")
    s.fill(38, 6, 4, 42, 12, 4, DARK)
    for x in (38, 40, 42):
        s.set(x, 13, 4, "minecraft:gold_block")
    s.set(40, 14, 4, "minecraft:gold_block")
    s.chest(36, 6, 5, ns("chests/hollow_palace"), facing="south")
    s.chest(44, 6, 5, ns("chests/hollow_palace"), facing="south")
    # Mascara de Vazio gigante na parede norte, atras do trono.
    for x in range(28, 53):
        for y in range(5, 20):
            ex, ey = (x - 40) / 12.0, (y - 12) / 7.5
            if ex * ex + ey * ey <= 1.0:
                s.set(x, y, 3, BONE)
    for (ex0, ey0) in ((34, 13), (44, 13)):
        s.fill(ex0, ey0, 3, ex0 + 2, ey0 + 2, 3, "minecraft:black_concrete")
        s.set(ex0 + 1, ey0 + 1, 3, "minecraft:redstone_lamp[lit=true]")
    for x in range(33, 48):
        s.set(x, 7, 3, "minecraft:black_concrete" if x % 2 else BONE)
    # Pilares 2x2 e costelas de osso nas paredes.
    for x0 in (27, 52):
        for z0 in (12, 20, 28):
            s.fill(x0, 4, z0, x0 + 1, 18, z0 + 1, QP)
            s.fill(x0, 19, z0, x0 + 1, 19, z0 + 1, CQ)
    for z in range(8, 33, 6):
        for xw in (4, 76):
            s.fill(xw, 4, z, xw, 15, z, BONE)
    # Tapete negro com borda dourada ate o trono.
    for z in range(12, 34):
        for x in range(37, 44):
            s.set(x, 4, z, "minecraft:yellow_carpet" if x in (37, 43) else "minecraft:black_carpet")

    # --- Salao Central ---
    for x in range(8, 73, 8):
        if 31 <= x <= 49:
            continue
        for z in (36, 43):
            s.fill(x, 4, z, x, 18, z, QP)
            s.set(x, 19, z, CQ)
    s.chest(5, 4, 38, ns("chests/hollow_palace"), facing="east")
    s.chest(75, 4, 41, ns("chests/hollow_palace"), facing="west")
    for (x, z) in ((6, 37), (6, 42), (74, 37), (74, 42)):
        s.set(x, 4, z, CRYSTAL)

    # --- Jardim das Areias (Imperatriz em 40,4,59; ceu aberto) ---
    disk(s, 40, 59, 12, 3, SAND)
    disk(s, 40, 59, 13.5, 3, SQ, rmin=12.6)
    for (px, pz) in ((14, 52), (66, 52), (14, 70), (66, 70)):
        disk(s, px, pz, 4.5, 3, "minecraft:prismarine_bricks", rmin=3.4)
        disk(s, px, pz, 3, 2, "minecraft:prismarine")
        disk(s, px, pz, 3, 3, "minecraft:water[level=0]")
        s.set(px, 2, pz, SEA)
    s.fill(38, 3, 73, 42, 3, 76, QB)
    for (tx, tz) in ((10, 61), (70, 61), (22, 48), (58, 48), (22, 74), (58, 74)):
        h = 5 + rnd.randint(0, 2)
        s.fill(tx, 4, tz, tx, 4 + h, tz, QP)
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if rnd.random() < 0.75:
                s.set(tx + dx, 4 + h - 1, tz + dz, SQ)
                s.set(tx + 2 * dx, 4 + h, tz + 2 * dz, SQ)
                s.set(tx + 2 * dx, 5 + h, tz + 2 * dz, CRYSTAL)
        s.set(tx, 5 + h, tz, CRYSTAL)
    for (x, z) in ((30, 50), (50, 50), (28, 68), (52, 68), (40, 47)):
        s.set(x, 4, z, CRYSTAL)
    # Ossada no jardim (lembranca de um Menos devorado).
    for i in range(7):
        s.set(8 + i, 4, 74, "minecraft:bone_block[axis=x]")
    for i in range(0, 7, 2):
        s.fill(8 + i, 5, 73, 8 + i, 6, 73, BONE)
        s.fill(8 + i, 5, 75, 8 + i, 6, 75, BONE)
    s.save(os.path.join(STRUCT, "hollow_palace.nbt"))


def fey_court():
    """Corte das Fadas (61x31x61): chegada ao sul, arena da Rainha (30,3,22) e trono ao norte."""
    s = Structure(61, 31, 61)
    rnd = random.Random(2424)
    c = 30
    disk(s, c, c, 30, 0, "minecraft:stone")
    disk(s, c, c, 30, 1, "minecraft:dirt")
    for x in range(61):
        for z in range(61):
            if math.hypot(x - c, z - c) <= 30.5:
                s.set(x, 2, z, "minecraft:moss_block" if rnd.random() < 0.18 else "minecraft:grass_block[snowy=false]")
    # Caminho da chegada ate o trono.
    for z in range(10, 59):
        for x in range(28, 33):
            s.set(x, 2, z, "minecraft:mossy_stone_bricks" if rnd.random() < 0.6 else "minecraft:stone_bricks")
    disk(s, 30, 53, 5, 2, "minecraft:mossy_stone_bricks")
    disk(s, 30, 53, 5.6, 2, "minecraft:chiseled_stone_bricks", rmin=4.6)
    # Arena: piso de musgo e o anel de Petalas-da-Lua.
    disk(s, 30, 22, 11, 2, "minecraft:moss_block")
    for k in range(40):
        a = 2 * math.pi * k / 40
        x, z = round(30 + math.cos(a) * 12), round(22 + math.sin(a) * 12)
        if not (28 <= x <= 32 and z > 22):
            s.set(x, 2, z, "minecraft:grass_block[snowy=false]")
            s.set(x, 3, z, ns("moonpetal"))
    # Trono de roseira ao norte.
    s.fill(26, 3, 5, 34, 3, 10, "minecraft:mossy_stone_bricks")
    s.fill(26, 3, 11, 34, 3, 11, "minecraft:mossy_stone_brick_slab[type=bottom]")
    s.set(30, 4, 8, "minecraft:mossy_stone_brick_stairs[facing=south]")
    s.fill(29, 4, 7, 31, 8, 7, "minecraft:mossy_stone_bricks")
    s.set(30, 9, 7, ns("fey_glowcap"))
    for (x, y) in ((28, 5), (32, 5), (28, 6), (32, 6), (29, 9), (31, 9), (30, 10), (28, 7), (32, 7)):
        s.set(x, y, 7, "minecraft:flowering_azalea_leaves[persistent=true]")
    for x in (27, 33):
        s.set(x, 4, 8, "minecraft:potted_flowering_azalea_bush")
    s.chest(28, 4, 6, ns("chests/fey_court"), facing="south")
    s.chest(32, 4, 6, ns("chests/fey_court"), facing="south")
    # Anel de cogumelos gigantes (sem tapar o caminho nem a chegada).
    for k in range(12):
        a = 2 * math.pi * k / 12 + 0.13
        x, z = round(c + math.cos(a) * 26), round(c + math.sin(a) * 26)
        if 24 <= x <= 36 and z > 40:
            continue
        h = 6 + (k % 3)
        s.fill(x, 3, z, x, 3 + h, z, "minecraft:mushroom_stem[up=false,down=false]")
        disk(s, x, z, 2.4, 4 + h, ns("fey_glowcap"))
        s.fill(x - 1, 5 + h, z - 1, x + 1, 5 + h, z + 1, ns("fey_glowcap"))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s.set(x + dx, 3 + h, z + dz, "minecraft:shroomlight")
    # Sebe externa de azaleia florida com a abertura da chegada.
    for x in range(61):
        for z in range(61):
            d = math.hypot(x - c, z - c)
            if 29.4 <= d <= 30.4 and not (25 <= x <= 35 and z > 50):
                for y in (3, 4):
                    s.set(x, y, z, "minecraft:flowering_azalea_leaves[persistent=true]")
    # Postes com lanternas ao longo do caminho (fora da arena).
    for z in range(14, 52, 6):
        if math.hypot(0, z - 22) <= 13:
            continue
        for x in (26, 34):
            s.set(x, 3, z, "minecraft:mossy_stone_brick_wall")
            s.set(x, 4, z, "minecraft:lantern[hanging=false]")
    # Chegada: Pedra do Circulo (o portal abre 3 ao sul) e pedra de viagem.
    s.set(26, 3, 53, ns("fey_ring_stone"))
    s.set(34, 3, 53, TRAVEL)
    for _ in range(60):
        x, z = rnd.randint(2, 58), rnd.randint(2, 58)
        if s.get(x, 2, z) and s.get(x, 2, z).startswith("minecraft:grass_block") and s.get(x, 3, z) is None \
                and math.hypot(x - 30, z - 22) > 13.5 and not (25 <= x <= 35):
            s.set(x, 3, z, "minecraft:pink_petals[flower_amount=%d,facing=north]" % rnd.randint(1, 4))
    s.save(os.path.join(STRUCT, "fey_court.nbt"))


def garganta_site():
    """Fenda da Garganta (Mundo Normal): cratera de areia branca, costelas de osso e o altar sob a rachadura do ceu."""
    s = Structure(15, 12, 15)
    c = 7
    rnd = random.Random(55)
    for y in range(1, 11):
        disk(s, c, c, 7, y, "minecraft:air")
    disk(s, c, c, 7, 0, SAND)
    for k in range(10):
        a = 2 * math.pi * k / 10
        for r in range(2, 7):
            if rnd.random() < 0.7:
                s.set(round(c + math.cos(a) * r), 0, round(c + math.sin(a) * r), "minecraft:blackstone")
    for k in range(6):
        a = 2 * math.pi * k / 6 + math.pi / 6
        for y in range(1, 9):
            r = 6.2 - max(0, y - 4) * 0.55
            x, z = round(c + math.cos(a) * r), round(c + math.sin(a) * r)
            if y <= 5 + k % 3:
                s.set(x, y, z, BONE)
    s.set(c, 1, 4, ns("garganta_altar"))
    for x in (5, 9):
        s.fill(x, 1, 4, x, 3, 4, SB)
        s.set(x, 4, 4, SOUL)
    s.chest(11, 0, 10, ns("chests/garganta_site"), facing="west")
    for (x, z) in ((3, 9), (10, 3), (4, 11)):
        s.set(x, 1, z, CRYSTAL)
    s.set(9, 1, 11, "minecraft:skeleton_skull[rotation=6]")
    s.save(os.path.join(STRUCT, "garganta_site.nbt"), keep_air=True)


def fey_ring():
    """Circulo das Fadas (Mundo Normal): pedras musgosas, cogumelos-luz pequenos e a Pedra do Circulo."""
    s = Structure(13, 8, 13)
    c = 6
    rnd = random.Random(66)
    for y in range(1, 7):
        disk(s, c, c, 6, y, "minecraft:air")
    for x in range(13):
        for z in range(13):
            if math.hypot(x - c, z - c) <= 6.5:
                s.set(x, 0, z, "minecraft:moss_block" if rnd.random() < 0.3 else "minecraft:grass_block[snowy=false]")
    for k in range(8):
        a = 2 * math.pi * k / 8
        x, z = round(c + math.cos(a) * 5), round(c + math.sin(a) * 5)
        h = 2 + (k % 2)
        s.fill(x, 1, z, x, h, z, "minecraft:mossy_stone_bricks")
        s.set(x, h + 1, z, "minecraft:mossy_stone_brick_slab[type=bottom]")
    for k in range(6):
        a = 2 * math.pi * k / 6 + 0.5
        x, z = round(c + math.cos(a) * 3.4), round(c + math.sin(a) * 3.4)
        s.set(x, 1, z, "minecraft:mushroom_stem[up=false,down=false]")
        s.set(x, 2, z, ns("fey_glowcap"))
    for k in range(10):
        a = 2 * math.pi * k / 10 + 0.2
        x, z = round(c + math.cos(a) * 4.2), round(c + math.sin(a) * 4.2)
        if s.get(x, 1, z) in (None, "minecraft:air"):
            s.set(x, 1, z, ns("moonpetal"))
    s.set(c, 1, 3, ns("fey_ring_stone"))
    s.chest(3, 0, 9, ns("chests/fey_ring"), facing="east")
    s.save(os.path.join(STRUCT, "fey_ring.nbt"), keep_air=True)


# =====================================================================================
#  Estruturas do Mundo Normal (worldgen)
# =====================================================================================
EXCLUSIONS = [{"other_set": ns("guild_city"), "chunk_count": 10}, {"other_set": ns("capital_city"), "chunk_count": 13},
              {"other_set": ns("capital_sahra"), "chunk_count": 13}, {"other_set": ns("capital_nordheim"), "chunk_count": 13}]


def overworld_structure(name, biomes_list, spacing, separation, salt):
    write_json(os.path.join(DATA, "worldgen/structure", name + ".json"), {
        "type": "minecraft:jigsaw", "biomes": "#%s:has_structure/%s" % (NS, name), "step": "surface_structures",
        "spawn_overrides": {}, "terrain_adaptation": "beard_thin", "start_pool": "%s:%s/start" % (NS, name), "size": 1,
        "start_height": {"absolute": -1}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(DATA, "worldgen/template_pool", name, "start.json"), {
        "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element", "location": ns(name), "processors": "minecraft:empty",
            "projection": "rigid"}}]})
    write_json(os.path.join(DATA, "worldgen/structure_set", name + ".json"), {
        "structures": [{"structure": ns(name), "weight": 1}],
        "placement": {"type": ns("random_spread_multi"), "spacing": spacing, "separation": separation, "salt": salt,
                      "exclusion_zones": EXCLUSIONS}})
    write_json(os.path.join(MERGE, "data", NS, "tags/worldgen/biome/has_structure", name + ".json"),
               {"replace": False, "values": biomes_list})


# =====================================================================================
#  Loot, receitas e tags
# =====================================================================================
def it(name, w, a=1, b=1, chance=None):
    e = {"type": "minecraft:item", "name": ns(name), "weight": w}
    if (a, b) != (1, 1):
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": a, "max": b}, "add": False}]
    return e


def scroll(glyph, w):
    return {"type": "minecraft:item", "name": ns("glyph_scroll"), "weight": w, "functions": [
        {"function": "minecraft:set_components", "components": {ns("glyph"): ns(glyph)}}]}


def chest(name, rmin, rmax, entries):
    write_json(os.path.join(DATA, "loot_table/chests", name + ".json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": rmin, "max": rmax}, "bonus_rolls": 0.0, "entries": entries}]})


def drop(item, a, b, chance=None, looting=True, player=False):
    e = {"type": "minecraft:item", "name": ns(item), "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": a, "max": b}, "add": False}]}
    if looting:
        e["functions"].append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                               "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}})
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [e]}
    conds = []
    if player:
        conds.append({"condition": "minecraft:killed_by_player"})
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "unenchanted_chance": chance,
                      "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.02, "per_level_above_first": 0.02},
                      "enchantment": "minecraft:looting"})
    if conds:
        pool["conditions"] = conds
    return pool


def scroll_drop(glyph, chance):
    return {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [
        {"condition": "minecraft:killed_by_player"}, {"condition": "minecraft:random_chance", "chance": chance}],
        "entries": [scroll(glyph, 1)]}


def entity_loot(name, pools):
    write_json(os.path.join(DATA, "loot_table/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})


def block_loot(name):
    write_json(os.path.join(DATA, "loot_table/blocks", name + ".json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": ns(name)}],
         "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def loot():
    for b in ("hollow_sand", "hollow_sandstone", "hollow_sandstone_bricks", "hollow_crystal", "fey_glowcap", "moonpetal"):
        block_loot(b)
    entity_loot("hollow_crawler", [drop("hollow_mask_fragment", 0, 1), drop("minecraft:bone", 0, 2)])
    entity_loot("hollow_brute", [drop("hollow_mask_fragment", 1, 2), drop("minecraft:bone", 1, 3),
                                 scroll_drop("hollow_hierro", 0.03)])
    entity_loot("hollow_wraith", [drop("hollow_mask_fragment", 0, 1), drop("minecraft:phantom_membrane", 0, 1),
                                  scroll_drop("hollow_sonido", 0.03)])
    entity_loot("menos_giant", [drop("menos_core", 1, 1, looting=False), drop("hollow_mask_fragment", 2, 4),
                                drop("essence_shadow", 1, 2), scroll_drop("hollow_cero", 0.12)])
    entity_loot("arrancar", [drop("arrancar_soul_shard", 1, 1, chance=0.55, looting=False), drop("hollow_mask_fragment", 1, 3),
                             drop("reishi_crystal", 1, 1, chance=0.25, looting=False), scroll_drop("hollow_sonido", 0.08),
                             scroll_drop("hollow_cero", 0.05), scroll_drop("hollow_hierro", 0.05),
                             scroll_drop("garganta_senkai", 0.04), scroll_drop("garganta_reishi", 0.04)])
    entity_loot("fey_pixie", [drop("fey_dust", 1, 2), drop("minecraft:glowstone_dust", 0, 1), scroll_drop("fey_charm", 0.02)])
    entity_loot("bramble_hound", [drop("bramble_heart", 1, 1, chance=0.25, looting=False), drop("minecraft:sweet_berries", 1, 2),
                                  drop("minecraft:stick", 0, 2), scroll_drop("fey_thornbind", 0.03)])
    chest("hollow_ruin", 2, 4, [it("hollow_mask_fragment", 20, 1, 3), it("minecraft:bone", 15, 2, 6), it("hollow_crystal", 10, 1, 3),
                                it("reishi_crystal", 6), it("essence_shadow", 8, 1, 2), it("essence_arcane", 4),
                                it("minecraft:gold_ingot", 6, 1, 3), it("garganta_key", 2), scroll("hollow_sonido", 2),
                                scroll("hollow_hierro", 2), scroll("hollow_cero", 1)])
    chest("hollow_hub", 2, 3, [it("minecraft:bread", 10, 2, 5), it("minecraft:cooked_beef", 6, 1, 3), it("minecraft:torch", 8, 4, 12),
                               it("reishi_crystal", 4), it("hollow_mask_fragment", 6, 1, 2), it("essence_shadow", 4)])
    chest("hollow_palace", 4, 6, [it("reishi_crystal", 10, 1, 3), it("arrancar_soul_shard", 6, 1, 2), it("menos_core", 3),
                                  it("essence_shadow", 10, 2, 4), it("essence_arcane", 6, 1, 2), it("minecraft:diamond", 6, 1, 3),
                                  it("minecraft:gold_ingot", 10, 2, 6), it("minecraft:netherite_scrap", 2), it("garganta_key", 4),
                                  it("mana_crystal", 4, 1, 2), scroll("hollow_cero", 3), scroll("sand_tide", 2),
                                  scroll("hollow_gran_cero", 1), scroll("visored_mask", 2), scroll("reishi_rend", 2),
                                  scroll("garganta_senkai", 2), scroll("garganta_reishi", 2)])
    chest("garganta_site", 2, 4, [it("hollow_mask_fragment", 12, 1, 3), it("essence_shadow", 10, 1, 2), it("minecraft:bone", 10, 2, 5),
                                  it("garganta_key", 3), it("minecraft:ender_pearl", 5, 1, 2), scroll("hollow_cero", 2),
                                  scroll("garganta_senkai", 2), scroll("garganta_reishi", 2), scroll("hollow_sonido", 2)])
    chest("fey_court", 4, 6, [it("fey_dust", 12, 2, 5), it("bramble_heart", 6), it("essence_light", 10, 2, 3),
                              it("essence_wind", 8, 1, 2), it("minecraft:emerald", 8, 2, 5), it("minecraft:amethyst_shard", 8, 2, 4),
                              it("minecraft:golden_apple", 4), it("mana_crystal", 4, 1, 2), scroll("fey_charm", 3),
                              scroll("fey_thornbind", 3)])
    chest("fey_ring", 2, 3, [it("fey_dust", 10, 1, 3), it("moonpetal", 8, 1, 3), it("essence_light", 8),
                             it("minecraft:cookie", 6, 2, 5), it("minecraft:glow_berries", 6, 1, 4),
                             it("minecraft:honey_bottle", 4), scroll("fey_charm", 2), scroll("fey_thornbind", 1)])


def recipe(name, obj):
    write_json(os.path.join(DATA, "recipe", name + ".json"), obj)


def recipes():
    recipe("garganta_key", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" M ", "MCM", " M "],
                            "key": {"M": {"item": ns("hollow_mask_fragment")},
                                    "C": [{"item": ns("menos_core")}, {"item": ns("arrancar_soul_shard")}]},
                            "result": {"id": ns("garganta_key"), "count": 2}})
    recipe("reishi_crystal_from_quartz", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                          "ingredients": [{"item": ns("hollow_crystal")}, {"item": ns("hollow_crystal")},
                                                          {"item": ns("hollow_mask_fragment")}],
                                          "result": {"id": ns("reishi_crystal"), "count": 1}})
    recipe("hollow_sandstone", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["SS", "SS"],
                                "key": {"S": {"item": ns("hollow_sand")}}, "result": {"id": ns("hollow_sandstone"), "count": 1}})
    recipe("hollow_sandstone_bricks", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["SS", "SS"],
                                       "key": {"S": {"item": ns("hollow_sandstone")}},
                                       "result": {"id": ns("hollow_sandstone_bricks"), "count": 4}})
    recipe("fey_dust_from_moonpetal", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                       "ingredients": [{"item": ns("moonpetal")}, {"item": "minecraft:glowstone_dust"}],
                                       "result": {"id": ns("fey_dust"), "count": 2}})


def tag(path, values):
    write_json(os.path.join(MERGE, "data", path + ".json"), {"replace": False, "values": values})


def tags():
    tag("minecraft/tags/block/mineable/pickaxe", [ns("hollow_sandstone"), ns("hollow_sandstone_bricks"), ns("hollow_crystal")])
    tag("minecraft/tags/block/mineable/shovel", [ns("hollow_sand")])
    tag("minecraft/tags/block/mineable/axe", [ns("fey_glowcap")])
    tag("minecraft/tags/block/sand", [ns("hollow_sand")])
    tag("minecraft/tags/block/small_flowers", [ns("moonpetal")])
    tag("minecraft/tags/item/small_flowers", [ns("moonpetal")])
    tag("minecraft/tags/item/axes", [ns("decay_axe")])
    tag("minecraft/tags/item/swords", [ns("tide_sand_blade")])
    tag("c/tags/entity_type/bosses", [ns("hollow_king"), ns("sand_empress"), ns("thorn_queen")])
    tag("isekai_mahou/tags/worldgen/biome/is_hollow_world", [ns("hollow_dunes"), ns("quartz_forest"), ns("bone_wastes")])
    tag("isekai_mahou/tags/worldgen/biome/is_fey_realm", [ns("fey_glade"), ns("glowcap_hollow")])


def build_all():
    hollow_dimension()
    fey_dimension()
    features()
    biomes()
    hollow_hub()
    palace()
    fey_court()
    garganta_site()
    fey_ring()
    overworld_structure("garganta_site", ["#minecraft:is_badlands", "minecraft:desert", "minecraft:savanna",
                                          "minecraft:savanna_plateau", "minecraft:plains", "minecraft:snowy_plains",
                                          "minecraft:windswept_savanna", "minecraft:ice_spikes"], 40, 14, 551177033)
    overworld_structure("fey_ring", ["minecraft:flower_forest", "minecraft:forest", "minecraft:birch_forest",
                                     "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:cherry_grove",
                                     "minecraft:meadow", "minecraft:taiga"], 36, 12, 662288144)
    loot()
    recipes()
    tags()
    print("  mundo: 2 dimensoes, 5 biomas, 5 moldes, 2 estruturas, loot/receitas/tags")
