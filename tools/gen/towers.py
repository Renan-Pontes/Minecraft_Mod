"""
Gerador de assets/dados do modulo towers (v1.6 — Torres de Desafio; v1.0 release — visual novo, variantes novas,
Ilhas Voadoras, afixos e recordes):
texturas (itens, blocos), modelos de item/bloco, modelo GeckoLib do Lorde Kobold (tools/gen/towers_geo.py),
moldes .nbt das 6 torres (tools/gen/towers_struct.py) e das 3 ilhas (tools/gen/towers_sky.py) + worldgen, loot tables,
receita, tags (merge), capitulo do diario e traducoes (src/main/lang/towers).

Os conjuntos de estrutura (worldgen/structure_set) guardam o `spacing`/`separation` que ja estiver no arquivo: o
espacamento pode ter sido ajustado por fora (torres do chao: 42/15, definido pelo trabalho de biomas da release).

Rodar a partir da raiz do projeto:  py -I tools/gen/towers.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix  # noqa: E402
import towers_struct  # noqa: E402
import towers_sky  # noqa: E402
import towers_geo  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/towers")
LANG = os.path.join(ROOT, "src/main/lang/towers")
NS = "isekai_mahou"
CLEAR = (0, 0, 0, 0)


def H(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  TEXTURAS DE ITEM (16x16)
# =====================================================================================
def tex_crystal():
    """Cristal de Teleporte: octaedro azul-celeste com brilho (estilo SAO)."""
    c = Canvas(16, 16)
    body, light, dark, glow = H("#3FA8FF"), H("#A8E4FF"), H("#1A4E9A"), H("#FFFFFF")
    for y in range(1, 15):
        half = 7 - abs(y - 7.5) * 0.95
        for x in range(16):
            dx = x + 0.5 - 8
            if abs(dx) <= half:
                k = body
                if dx < -half * 0.3:
                    k = light if y < 8 else body
                elif dx > half * 0.35:
                    k = dark
                if y >= 8 and dx > -half * 0.3:
                    k = darken(k, 0.18)
                c.set(x, y, k)
    c.line(8, 1, 8, 14, mix(body, light, 0.5))
    c.line(4, 7, 11, 7, mix(light, glow, 0.4))
    c.set(6, 4, glow); c.set(6, 5, light); c.set(5, 5, glow)
    c.outline(H("#0E2A5A"))
    c.set(13, 2, H("#DDF4FF")); c.set(2, 12, H("#DDF4FF")); c.set(13, 12, H("#9FD8FF"))
    c.save(os.path.join(ASSETS, "textures/item/tower_teleport_crystal.png"))


def tex_blade(name, blade, edge, dark, guard, gem):
    """Espada diagonal (ponta no canto superior direito)."""
    c = Canvas(16, 16)
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.set(x, y, blade)
        c.set(x + 1, y, edge if i < 9 else blade)
        c.set(x, y + 1, dark)
    c.set(15, 0, edge)
    # guarda
    for i in range(-2, 3):
        c.set(4 + i, 11 + i, guard)
    c.set(4, 11, gem)
    # cabo
    c.set(3, 12, H("#2A1E1A")); c.set(2, 13, H("#3A2A22")); c.set(1, 14, guard)
    c.outline(H("#0A0A12"))
    c.save(os.path.join(ASSETS, f"textures/item/{name}.png"))


def tex_items():
    tex_crystal()
    tex_blade("tower_twin_blade_noir", H("#2A2E3A"), H("#6C7488"), H("#14161E"), H("#4A4E5E"), H("#7A2AFF"))
    tex_blade("tower_twin_blade_azure", H("#9FD8E8"), H("#E8FBFF"), H("#4E8EA8"), H("#C8D8E0"), H("#2AE0A0"))


# =====================================================================================
#  TEXTURAS DE BLOCO (16x16)
# =====================================================================================
RUNE_ROWS = [
    "................",
    "................",
    "......####......",
    ".....#....#.....",
    "....#..##..#....",
    "...#..#..#..#...",
    "...#.#....#.#...",
    "...#.#.##.#.#...",
    "...#.#.##.#.#...",
    "...#.#....#.#...",
    "...#..#..#..#...",
    "....#..##..#....",
    ".....#....#.....",
    "......####......",
    "................",
    "................",
]


def stone_bg(seed, base="#6A6E78"):
    c = Canvas(16, 16, H(base))
    c.noise(0.12, seed)
    for i in range(16):
        c.set(i, 0, darken(H(base), 0.3)); c.set(0, i, darken(H(base), 0.3))
        c.set(i, 15, darken(H(base), 0.45)); c.set(15, i, darken(H(base), 0.45))
    return c


def tex_blocks():
    bt = os.path.join(ASSETS, "textures/block")
    # Selo: vidro arcano translucido com hexagrama
    c = Canvas(16, 16, H("#7FD4FF", 90))
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.15:
                c.set(x, y, H("#B8ECFF", 120))
    for i in range(16):
        c.set(i, 0, H("#E8FBFF", 220)); c.set(0, i, H("#E8FBFF", 220))
        c.set(i, 15, H("#3A8ACF", 200)); c.set(15, i, H("#3A8ACF", 200))
    c.from_rows(RUNE_ROWS, {"#": H("#F2FCFF", 235)})
    c.save(os.path.join(bt, "tower_seal.png"))
    # Nucleo do andar: lados (pedestal), topo com circulo (vermelho = ativo, verde = vencido), base
    side = stone_bg(11, "#5A5E6A")
    for x in range(16):
        side.set(x, 3, H("#D9A632")); side.set(x, 12, H("#D9A632"))
    for x in (4, 8, 11):
        for y in range(5, 11):
            side.set(x, y, H("#8A6CFF") if y % 2 == 0 else H("#5A3CCF"))
    side.save(os.path.join(bt, "tower_floor_core_side.png"))
    for name, glow, mid in (("tower_floor_core_top", "#FF5A3A", "#FFC84A"), ("tower_floor_core_top_cleared", "#4CFF7A", "#C8FFD8")):
        t = stone_bg(13, "#4E525E")
        t.from_rows(RUNE_ROWS, {"#": H(glow)})
        t.rect(7, 7, 8, 8, H(mid))
        t.save(os.path.join(bt, f"{name}.png"))
    stone_bg(17, "#3E424C").save(os.path.join(bt, "tower_floor_core_bottom.png"))
    # Alavanca Runica: apagada / acesa
    for name, rune, glow in (("tower_rune_switch", "#3A3C48", None), ("tower_rune_switch_lit", "#8FE8FF", "#FFFFFF")):
        t = stone_bg(19, "#575B66")
        t.rect(3, 3, 12, 12, darken(H("#575B66"), 0.25))
        t.outline_rect(3, 3, 12, 12, H("#2E3038"))
        t.line(7, 5, 7, 10, H(rune)); t.line(8, 5, 8, 10, H(rune))
        t.line(5, 7, 10, 7, H(rune)); t.line(5, 8, 10, 8, H(rune))
        t.set(5, 5, H(rune)); t.set(10, 5, H(rune)); t.set(5, 10, H(rune)); t.set(10, 10, H(rune))
        if glow:
            t.set(7, 7, H(glow)); t.set(8, 8, H(glow))
        t.save(os.path.join(bt, f"{name}.png"))


# =====================================================================================
#  MODELOS
# =====================================================================================
def models():
    bm = os.path.join(ASSETS, "models/block")
    bs = os.path.join(ASSETS, "blockstates")
    im = os.path.join(ASSETS, "models/item")
    write_json(os.path.join(bm, "tower_seal.json"), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                                      "textures": {"all": f"{NS}:block/tower_seal"}})
    write_json(os.path.join(bs, "tower_seal.json"), {"variants": {"": {"model": f"{NS}:block/tower_seal"}}})
    write_json(os.path.join(im, "tower_seal.json"), {"parent": f"{NS}:block/tower_seal"})
    for suffix, top in (("", "tower_floor_core_top"), ("_cleared", "tower_floor_core_top_cleared")):
        write_json(os.path.join(bm, f"tower_floor_core{suffix}.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"{NS}:block/{top}", "bottom": f"{NS}:block/tower_floor_core_bottom", "side": f"{NS}:block/tower_floor_core_side"}})
    write_json(os.path.join(bs, "tower_floor_core.json"), {"variants": {
        "cleared=false": {"model": f"{NS}:block/tower_floor_core"},
        "cleared=true": {"model": f"{NS}:block/tower_floor_core_cleared"}}})
    write_json(os.path.join(im, "tower_floor_core.json"), {"parent": f"{NS}:block/tower_floor_core"})
    for suffix in ("", "_lit"):
        write_json(os.path.join(bm, f"tower_rune_switch{suffix}.json"), {"parent": "minecraft:block/cube_all",
                                                                         "textures": {"all": f"{NS}:block/tower_rune_switch{suffix}"}})
    write_json(os.path.join(bs, "tower_rune_switch.json"), {"variants": {
        "lit=false": {"model": f"{NS}:block/tower_rune_switch"}, "lit=true": {"model": f"{NS}:block/tower_rune_switch_lit"}}})
    write_json(os.path.join(im, "tower_rune_switch.json"), {"parent": f"{NS}:block/tower_rune_switch_lit"})
    write_json(os.path.join(im, "tower_teleport_crystal.json"), {"parent": "minecraft:item/generated",
                                                                  "textures": {"layer0": f"{NS}:item/tower_teleport_crystal"}})
    for b in ("tower_twin_blade_noir", "tower_twin_blade_azure"):
        write_json(os.path.join(im, f"{b}.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{b}"}})
    write_json(os.path.join(im, "tower_kobold_lord_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  WORLDGEN
# =====================================================================================
BIOMES = {
    "stone": ["minecraft:plains", "minecraft:forest", "minecraft:birch_forest", "minecraft:taiga", "minecraft:meadow",
              "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:flower_forest",
              "minecraft:sunflower_plains", "minecraft:cherry_grove",
              f"{NS}:flat_lands_kingdom_meadows", f"{NS}:sakura_fields"],
    "frost": ["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes", "minecraft:grove",
              "minecraft:snowy_slopes", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga"],
    "sand": ["minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands",
             "minecraft:savanna", "minecraft:savanna_plateau", f"{NS}:flat_lands_golden_steppe"],
    # v1.0: floresta fechada/selva/pantano; submersa nos oceanos; flutuante por cima das terras baixas e do mar
    "forest": ["minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:dark_forest",
               "minecraft:old_growth_birch_forest", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:forest",
               "minecraft:flower_forest", f"{NS}:crystal_forest", f"{NS}:haunted_swamp", f"{NS}:twilight_grove"],
    "sunken": ["minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
               "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"],
    "floating": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:birch_forest",
                 "minecraft:flower_forest", "minecraft:savanna", "minecraft:desert", "minecraft:snowy_plains",
                 "minecraft:taiga", "minecraft:swamp", "minecraft:beach", "minecraft:ocean", "minecraft:deep_ocean",
                 "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean",
                 "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                 f"{NS}:flat_lands_kingdom_meadows", f"{NS}:flat_lands_golden_steppe", f"{NS}:sakura_fields"],
}
# Ilhas Voadoras: so por cima de terras baixas e do mar (numa montanha a ilha nasceria enterrada na rocha)
SKY_BIOMES = ["#minecraft:is_ocean", "#minecraft:is_beach", "#minecraft:is_river",
              "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes",
              "minecraft:desert", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:forest",
              "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
              "minecraft:dark_forest", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga",
              "minecraft:old_growth_spruce_taiga", "minecraft:savanna", "minecraft:jungle", "minecraft:sparse_jungle",
              "minecraft:bamboo_jungle", "minecraft:mushroom_fields",
              f"{NS}:flat_lands_kingdom_meadows", f"{NS}:flat_lands_golden_steppe", f"{NS}:sakura_fields",
              f"{NS}:crystal_forest", f"{NS}:haunted_swamp", f"{NS}:twilight_grove"]
# Conjunto de cada variante: as do chao dividem a mesma grade; a flutuante tem a sua (mais rara)
GROUND = ["stone", "frost", "sand", "forest", "sunken"]
CITY_ZONES = [
    {"other_set": f"{NS}:guild_city", "chunk_count": 10},
    {"other_set": f"{NS}:capital_city", "chunk_count": 13},
    {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
    {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13}]


def structure_set(name, structures, placement):
    """
    Escreve o conjunto. Se o arquivo ja existe, mantem SO o `spacing`/`separation` dele (o trabalho de biomas ajusta o
    espacamento por fora); o resto do placement (sal, zonas de exclusao) e sempre o daqui.
    """
    path = os.path.join(DATA, f"worldgen/structure_set/{name}.json")
    if os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as f:
                old = json.load(f).get("placement", {})
            for k in ("spacing", "separation"):
                if isinstance(old.get(k), int):
                    placement[k] = old[k]
        except (OSError, ValueError):
            pass
    write_json(path, {"structures": structures, "placement": placement})


def pool_json(sid):
    write_json(os.path.join(DATA, f"worldgen/template_pool/towers/{sid}.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                "location": f"{NS}:{towers_struct.TEMPLATE_DIR}/{sid}",
                                                "processors": "minecraft:empty", "projection": "rigid"}}]})


def sky_height(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}


def worldgen():
    ids = []
    for variant, biomes in BIOMES.items():
        sid = f"tower_{variant}"
        ids.append(sid)
        pool_json(sid)
        js = {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{sid}",
            "step": "surface_structures",
            # beard_thin: cria base de terra embaixo e abre espaco no terreno em volta da porta
            "terrain_adaptation": "beard_thin",
            "spawn_overrides": {},
            "start_pool": f"{NS}:towers/{sid}",
            "size": 1,
            "start_height": {"absolute": -1},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False}
        if variant == "sunken":
            # Altura fixa: os 3 primeiros andares ficam dentro do mar e o cais 1 bloco acima da agua (y=63).
            # ignore_waterlogging: o molde manda (interior seco; so o que ja nasce alagado no molde tem agua).
            # (com altura absoluta o jogo poe a base do molde 1 bloco abaixo do valor: "ground level delta" = 1)
            js["start_height"] = {"absolute": towers_struct.SUNKEN_Y + 1}
            del js["project_start_to_heightmap"]
            js["liquid_settings"] = "ignore_waterlogging"
        elif variant == "floating":
            js["start_height"] = sky_height(140, 160)
            del js["project_start_to_heightmap"]
            js["terrain_adaptation"] = "none"
        write_json(os.path.join(DATA, f"worldgen/structure/{sid}.json"), js)
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"), {"values": biomes})
    # Um conjunto so para as do chao: em cada ponto da grade, a variante do bioma local (o jogo testa todas)
    structure_set("towers", [{"structure": f"{NS}:tower_{v}", "weight": 1} for v in GROUND],
                  {"type": f"{NS}:random_spread_multi", "spacing": 42, "separation": 15, "salt": 1946306501,
                   "exclusion_zones": [{"other_set": "minecraft:villages", "chunk_count": 4}] + CITY_ZONES})
    structure_set("towers_sky", [{"structure": f"{NS}:tower_floating", "weight": 1}],
                  {"type": f"{NS}:random_spread_multi", "spacing": 60, "separation": 24, "salt": 1946306611,
                   "exclusion_zones": CITY_ZONES})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "towers.json"), {"values": [f"{NS}:{s}" for s in ids]})

    # ---------------- Ilhas Voadoras: um conjunto, tres moldes (a pequena e a mais comum)
    isles = []
    for name, _, weight in towers_sky.ISLANDS:
        isles.append({"structure": f"{NS}:{name}", "weight": weight})
        pool_json(name)
        write_json(os.path.join(DATA, f"worldgen/structure/{name}.json"), {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/sky_islands",
            "step": "surface_structures",
            "terrain_adaptation": "none",
            "spawn_overrides": {},
            "start_pool": f"{NS}:towers/{name}",
            "size": 1,
            "start_height": sky_height(135, 190),
            "max_distance_from_center": 80,
            "use_expansion_hack": False})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/sky_islands.json"),
               {"values": SKY_BIOMES})
    # Longe das cidades (sombra/queda em cima delas) e da Torre Flutuante (as duas ficam na mesma faixa de altura)
    structure_set("sky_islands", isles,
                  {"type": f"{NS}:random_spread_multi", "spacing": 36, "separation": 14, "salt": 1946306777,
                   "exclusion_zones": [{"other_set": f"{NS}:guild_city", "chunk_count": 6},
                                       {"other_set": f"{NS}:capital_city", "chunk_count": 8},
                                       {"other_set": f"{NS}:capital_sahra", "chunk_count": 8},
                                       {"other_set": f"{NS}:capital_nordheim", "chunk_count": 8},
                                       {"other_set": f"{NS}:towers_sky", "chunk_count": 6}]})
    write_json(os.path.join(tags, "sky_islands.json"), {"values": [d["structure"] for d in isles]})


# =====================================================================================
#  LOOT, RECEITA, TAGS
# =====================================================================================
def ie(name, lo=1, hi=1, weight=1, funcs=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    f = list(funcs or [])
    if hi > 1 or lo != 1:
        f.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if f:
        e["functions"] = f
    return e


def scroll(glyph, weight=1):
    return {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": weight,
            "functions": [{"function": "minecraft:set_components", "components": {f"{NS}:glyph": f"{NS}:{glyph}"}}]}


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def pool(entries, lo=1, hi=1):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi} if hi != lo else lo, "bonus_rolls": 0, "entries": entries}


COMMON_GLYPHS = ["ignite", "ember", "fire_wall", "water_blade", "soak", "bubble", "tide", "rock", "stalagmite",
                 "stoneskin", "tremor", "gust", "wind_blade", "tornado", "glide", "double_jump", "heal", "holy_ray",
                 "prism", "regenerate", "darkness", "shadow_step", "blind", "curse", "chains"]
AUGMENTS = ["echo", "split", "homing", "critical", "persistent", "vampiric", "chain", "overload"]
# Glifos espaciais do modulo space_magic (por id)
SPACE = {"space_step": 6, "space_gravity": 4, "return_mark": 4, "dimensional_inventory": 2, "dimension_gate": 1}
# Ceu: glifos de vento/luz/voo (ilhas voadoras)
SKY_GLYPHS = ["gust", "wind_blade", "tornado", "glide", "double_jump", "holy_ray", "prism", "space_step"]


def loot_tables():
    ch = os.path.join(DATA, "loot_table/chests")
    crystal = f"{NS}:tower_teleport_crystal"
    write_json(os.path.join(ch, "towers_floor.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("bread", 2, 5, 4), ie("cooked_beef", 2, 4, 3), ie("iron_ingot", 2, 6, 5), ie("gold_ingot", 1, 4, 3),
              ie("arrow", 6, 16, 3), ie("experience_bottle", 1, 4, 3), ie("lapis_lazuli", 3, 8, 2), ie("emerald", 1, 3, 2),
              ie(f"{NS}:mana_crystal", 1, 3, 3), ie(f"{NS}:essence_arcane", 1, 2, 2)], 3, 6),
        pool([ie(crystal, 1, 2, 4), ie("iron_sword", 1, 1, 2, [ench(10, 20)]), ie("book", 1, 1, 2, [ench(10, 20)]),
              ie("golden_apple", 1, 1, 1), {"type": "minecraft:empty", "weight": 3}]),
        pool([scroll(g) for g in COMMON_GLYPHS] + [{"type": "minecraft:empty", "weight": 18}]),
    ], "random_sequence": f"{NS}:chests/towers_floor"})
    write_json(os.path.join(ch, "towers_treasure.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("diamond", 1, 3, 4), ie("gold_ingot", 3, 8, 5), ie("emerald", 2, 6, 4), ie("experience_bottle", 3, 8, 3),
              ie(f"{NS}:mana_crystal", 2, 5, 4), ie("golden_apple", 1, 2, 2), ie("ender_pearl", 1, 3, 2)], 3, 6),
        pool([ie(f"{NS}:essence_fire", 1, 2), ie(f"{NS}:essence_water", 1, 2), ie(f"{NS}:essence_earth", 1, 2),
              ie(f"{NS}:essence_wind", 1, 2), ie(f"{NS}:essence_light", 1, 2), ie(f"{NS}:essence_shadow", 1, 2),
              ie("book", 1, 1, 3, [ench(20, 30)]), ie("diamond_sword", 1, 1, 1, [ench(15, 30)])], 1, 3),
        pool([ie(crystal, 1, 3, 3), {"type": "minecraft:empty", "weight": 2}]),
        pool([scroll(g) for g in COMMON_GLYPHS + AUGMENTS] + [scroll(g, w) for g, w in SPACE.items() if w >= 4]),
        pool([ie(f"{NS}:tower_twin_blade_noir"), ie(f"{NS}:tower_twin_blade_azure"), {"type": "minecraft:empty", "weight": 22}]),
    ], "random_sequence": f"{NS}:chests/towers_treasure"})
    write_json(os.path.join(ch, "towers_summit.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("diamond", 2, 5, 5), ie("emerald", 4, 10, 4), ie("gold_block", 1, 2, 3), ie("netherite_scrap", 1, 2, 2),
              ie(f"{NS}:mana_crystal", 4, 8, 4), ie("enchanted_golden_apple", 1, 1, 1), ie("experience_bottle", 6, 12, 3)], 4, 7),
        pool([ie("book", 1, 1, 4, [ench(30, 39)]), ie("diamond_chestplate", 1, 1, 2, [ench(25, 35)]),
              ie("diamond_sword", 1, 1, 2, [ench(25, 35)]), ie(f"{NS}:essence_arcane", 2, 4, 3)], 1, 2),
        pool([ie(crystal, 2, 4)]),
        pool([scroll(g, w) for g, w in SPACE.items()], 1, 2),
        pool([scroll(g) for g in COMMON_GLYPHS + AUGMENTS], 1, 2),
        pool([ie(f"{NS}:tower_twin_blade_noir"), ie(f"{NS}:tower_twin_blade_azure"), {"type": "minecraft:empty", "weight": 6}]),
        # v1.0 (pedido do modulo arcana_items): raros de cofre
        pool([ie(f"{NS}:blank_recipe_scroll", 1, 1, 2), scroll("recover_belongings", 1), {"type": "minecraft:empty", "weight": 9}]),
    ], "random_sequence": f"{NS}:chests/towers_summit"})
    # ---------------- Ilhas Voadoras
    write_json(os.path.join(ch, "sky_island.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("feather", 2, 6, 4), ie("phantom_membrane", 1, 3, 3), ie("gold_ingot", 1, 4, 4), ie("iron_ingot", 2, 5, 4),
              ie("amethyst_shard", 2, 6, 3), ie("emerald", 1, 3, 2), ie("experience_bottle", 1, 4, 3), ie("golden_carrot", 2, 5, 2),
              ie(f"{NS}:mana_crystal", 1, 3, 4), ie(f"{NS}:essence_wind", 1, 2, 3), ie(f"{NS}:essence_light", 1, 1, 2)], 3, 6),
        pool([ie(crystal, 1, 2, 3), ie("book", 1, 1, 2, [ench(10, 22)]), ie("spyglass", 1, 1, 1), ie("saddle", 1, 1, 1),
              {"type": "minecraft:empty", "weight": 3}]),
        pool([scroll(g) for g in SKY_GLYPHS] + [{"type": "minecraft:empty", "weight": 10}]),
    ], "random_sequence": f"{NS}:chests/sky_island"})
    write_json(os.path.join(ch, "sky_island_vault.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("diamond", 1, 3, 4), ie("gold_ingot", 3, 7, 4), ie("emerald", 2, 5, 3), ie("experience_bottle", 3, 8, 3),
              ie("phantom_membrane", 2, 4, 2), ie(f"{NS}:mana_crystal", 2, 5, 4), ie(f"{NS}:essence_wind", 2, 3, 3),
              ie(f"{NS}:essence_light", 1, 2, 2), ie("golden_apple", 1, 1, 2)], 4, 6),
        pool([ie("book", 1, 1, 3, [ench(22, 32)]), ie("diamond_boots", 1, 1, 1, [ench(18, 28)]),
              ie("bow", 1, 1, 2, [ench(18, 28)]), ie(crystal, 2, 3, 3)], 1, 2),
        pool([scroll(g) for g in SKY_GLYPHS] + [scroll(g, 1) for g in AUGMENTS]),
        pool([ie(f"{NS}:blank_recipe_scroll", 1, 1, 1), {"type": "minecraft:empty", "weight": 7}]),
    ], "random_sequence": f"{NS}:chests/sky_island_vault"})
    lt = os.path.join(DATA, "loot_table")
    write_json(os.path.join(lt, "entities/tower_kobold_lord.json"), {"type": "minecraft:entity", "pools": []})
    # blocos fixos da torre: nao soltam nada (inquebraveis em survival)
    for b in ("tower_seal", "tower_floor_core", "tower_rune_switch"):
        write_json(os.path.join(lt, f"blocks/{b}.json"), {"type": "minecraft:block", "pools": [],
                                                           "random_sequence": f"{NS}:blocks/{b}"})


def recipes():
    rc = os.path.join(DATA, "recipe")
    write_json(os.path.join(rc, "tower_teleport_crystal.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" A ", "AEA", " M "],
        "key": {"A": {"item": "minecraft:amethyst_shard"}, "E": {"item": "minecraft:ender_pearl"},
                "M": {"item": f"{NS}:mana_crystal"}},
        "result": {"id": f"{NS}:tower_teleport_crystal", "count": 2}})


def tags():
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": [f"{NS}:tower_kobold_lord"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/item/swords.json"),
               {"values": [f"{NS}:tower_twin_blade_noir", f"{NS}:tower_twin_blade_azure"]})


# =====================================================================================
#  DIARIO
# =====================================================================================
def journal():
    loc = {"tag": f"{NS}:towers", "dimension": "minecraft:overworld"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 65) para o fim do Ato II (Círculo 4); XP na escala dos vizinhos.
    steps = [
        {"id": "enter", "condition": {"type": "flag", "flag": "towers.entered"}, "xp": 60,
         "icon": "minecraft:stone_bricks", "locate": loc},
        {"id": "floors", "condition": {"type": "counter", "key": "towers.floor", "min": 3}, "xp": 150,
         "icon": f"{NS}:tower_floor_core", "locate": loc},
        {"id": "puzzle", "condition": {"type": "flag", "flag": "towers.puzzle"}, "optional": True, "xp": 100,
         "icon": f"{NS}:tower_rune_switch", "locate": loc},
        {"id": "summit", "condition": {"type": "counter", "key": "towers.cleared", "min": 1}, "xp": 400,
         "icon": "minecraft:bell", "locate": loc,
         "rewards": {"items": [{"id": f"{NS}:tower_teleport_crystal", "count": 3}]}},
        {"id": "kobold_lord", "condition": {"type": "bosses", "ids": ["tower_kobold_lord"], "min": 1}, "optional": True,
         "xp": 350, "icon": "minecraft:iron_axe", "locate": loc, "requires": [f"{NS}:tower_kobold_lord"]},
        {"id": "crystal", "condition": {"type": "flag", "flag": "towers.crystal"}, "optional": True, "xp": 50,
         "icon": f"{NS}:tower_teleport_crystal"},
        {"id": "starburst", "condition": {"type": "flag", "flag": "towers.twin"}, "optional": True, "xp": 200,
         "icon": f"{NS}:tower_twin_blade_noir"},
        {"id": "three", "condition": {"type": "counter", "key": "towers.cleared", "min": 3}, "optional": True, "xp": 800,
         "icon": f"{NS}:tower_twin_blade_azure", "locate": loc,
         "rewards": {"items": [{"id": "minecraft:diamond", "count": 8}]}},
        # v1.0
        {"id": "affixes", "condition": {"type": "counter", "key": "towers.affix_floor", "min": 3}, "optional": True, "xp": 250,
         "icon": "minecraft:blaze_powder", "locate": loc},
        {"id": "record", "condition": {"type": "counter", "key": "towers.records", "min": 5}, "optional": True, "xp": 200,
         "icon": "minecraft:clock", "locate": loc},
        {"id": "sky_island", "condition": {"type": "flag", "flag": "towers.sky_island"}, "optional": True, "xp": 200,
         "icon": "minecraft:feather", "locate": {"tag": f"{NS}:sky_islands", "dimension": "minecraft:overworld"}},
    ]
    write_json(os.path.join(DATA, "journal/towers.json"), {"chapters": [{
        "id": "towers", "order": 26, "color": "#7FD4FF", "intro": True,
        "icon": [f"{NS}:tower_teleport_crystal", f"{NS}:tower_twin_blade_noir", f"{NS}:tower_floor_core"],
        "steps": steps,
        "rewards": {"xp": 1000, "items": [{"id": f"{NS}:mana_crystal", "count": 12}], "glyph_scrolls": 1, "glyph_tier": 3}}]})


# =====================================================================================
#  TRADUCOES
# =====================================================================================
PT = {}
EN = {}


def t(key, pt, en):
    PT[key] = pt
    EN[key] = en


def lang_v10():
    """v1.0: variantes novas, Ilhas Voadoras, afixos e recordes."""
    tw = f"tower.{NS}"
    m = f"message.{NS}"
    c = f"command.{NS}.towers"
    for sid, pt, en in (("tower_forest", "Torre da Floresta", "Forest Tower"), ("tower_sunken", "Torre Submersa", "Sunken Tower"),
                        ("tower_floating", "Torre Flutuante", "Floating Tower"),
                        ("tower_sky_island_shrine", "Ilha Voadora", "Flying Island"),
                        ("tower_sky_island_ruins", "Ilha Voadora (templo)", "Flying Island (temple)"),
                        ("tower_sky_island_keep", "Ilha Voadora (torre de vigia)", "Flying Island (watchtower)")):
        t(f"map.{NS}.structure.{sid}", pt, en)
    # afixos
    t(f"{tw}.affixes", "Afixos do andar: %s", "Floor affixes: %s")
    for aid, pt, en, dpt, den in (
            ("fortified", "Fortificados", "Fortified", "Inimigos com +40% de vida.", "Enemies have +40% health."),
            ("savage", "Selvagens", "Savage", "Inimigos causam +30% de dano.", "Enemies deal +30% damage."),
            ("swift", "Velozes", "Swift", "Inimigos 25% mais rápidos.", "Enemies are 25% faster."),
            ("horde", "Horda", "Horde", "Duas criaturas a mais em cada onda.", "Two more creatures in every wave."),
            ("regenerating", "Regeneração", "Regenerating", "Inimigos recuperam vida o tempo todo.", "Enemies regenerate all the time."),
            ("shielded", "Escudados", "Shielded", "Inimigos nascem com escudo e resistência.", "Enemies spawn with a shield and resistance."),
            ("volatile", "Voláteis", "Volatile", "Inimigos explodem 1 s depois de morrer. Afaste-se!",
             "Enemies explode 1 s after dying. Step back!"),
            ("vampiric", "Vampíricos", "Vampiric", "Inimigos se curam com o dano que causam.", "Enemies heal from the damage they deal."),
            ("burning", "Ardentes", "Burning", "Os golpes dos inimigos incendeiam.", "Enemy hits set you on fire."),
            ("chilling", "Gélidos", "Chilling", "Os golpes dos inimigos deixam você lento.", "Enemy hits slow you down."),
            ("champion", "Campeão", "Champion", "Cada onda traz um campeão: maior, o dobro da vida, brilhando.",
             "Each wave brings a champion: bigger, double health, glowing."),
            ("thorns", "Espinhos", "Thorns", "Golpes corpo a corpo devolvem parte do dano.", "Melee hits reflect part of the damage.")):
        t(f"{tw}.affix.{aid}", pt, en)
        t(f"{tw}.affix.{aid}.desc", dpt, den)
    # recordes
    t(f"{tw}.time.world", "%s — recorde do mundo!", "%s — world record!")
    t(f"{tw}.time.personal", "%s — novo recorde pessoal!", "%s — new personal best!")
    t(f"{tw}.time.normal", "%s (seu melhor: %s)", "%s (your best: %s)")
    t(f"{tw}.time.summit", "Guardião vencido em %s", "Guardian defeated in %s")
    t(f"{tw}.time.broadcast", "%s bateu o recorde: %s da Torre %s em %s!", "%s broke the record: %s of the %s Tower in %s!")
    # dicas de primeira vez
    t(f"hint.{NS}.towers.affixes", "Este andar tem afixos: passe o mouse no nome deles, no chat, para ver o que fazem.",
      "This floor has affixes: hover their names in chat to see what they do.")
    t(f"hint.{NS}.towers.updraft", "Uma Corrente Ascendente sobe até a ilha: entre na coluna brilhante para subir (agache para descer).",
      "An Updraft rises to the island: step into the glowing column to go up (sneak to go down).")
    # ceu
    t(f"{m}.towers.sky_cushion", "A magia da ilha amortece a sua queda.", "The island's magic cushions your fall.")
    t(f"{m}.towers.sky_island", "Uma Ilha Voadora! Ruínas de quem viveu acima das nuvens — e o que deixaram para trás.",
      "A Flying Island! Ruins of those who lived above the clouds — and what they left behind.")
    # comandos
    t(f"{c}.island_not_found", "Nenhuma Ilha Voadora encontrada perto (só chunks novos do Overworld têm).",
      "No Flying Island found nearby (only new Overworld chunks have them).")
    t(f"{c}.island_tp", "Teleportado para a Ilha Voadora em %s %s %s.", "Teleported to the Flying Island at %s %s %s.")
    t(f"{c}.affix_all", "Afixos: %s", "Affixes: %s")
    t(f"{c}.affix_now", "Andar %s agora: %s", "Floor %s now: %s")
    t(f"{c}.affix_set", "Andar %s: próximo despertar com %s.", "Floor %s: next awakening with %s.")
    t(f"{c}.affix_random", "Andar %s: afixos voltam a ser sorteados.", "Floor %s: affixes are random again.")
    t(f"{c}.affix_unknown", "Afixo desconhecido: %s", "Unknown affix: %s")
    t(f"{c}.records_empty", "Nenhum recorde de torre ainda.", "No tower records yet.")
    t(f"{c}.records_line", "Torre %s · %s: %s — %s (%s afixos)", "%s Tower · %s: %s — %s (%s affixes)")
    t(f"{c}.records_cleared", "Recordes das torres apagados.", "Tower records wiped.")


def lang():
    b = f"block.{NS}"
    i = f"item.{NS}"
    t(f"{b}.tower_seal", "Selo da Torre", "Tower Seal")
    t(f"{b}.tower_floor_core", "Núcleo do Andar", "Floor Core")
    t(f"{b}.tower_rune_switch", "Alavanca Rúnica", "Rune Lever")
    t(f"{i}.tower_teleport_crystal", "Cristal de Teleporte", "Teleport Crystal")
    t(f"{i}.tower_teleport_crystal.desc", "Um cristal azul que guarda o caminho de volta.", "A blue crystal that remembers the way back.")
    t(f"{i}.tower_teleport_crystal.use", "Segure para usar: dentro de uma torre volta à entrada; fora, ao seu ponto de renascimento.",
      "Hold to use: inside a tower, returns to its entrance; elsewhere, to your respawn point.")
    t(f"{i}.tower_teleport_crystal.boss", "Não funciona perto de um chefe.", "Doesn't work near a boss.")
    t(f"{i}.tower_twin_blade_noir", "Lâmina Gêmea Noturna", "Night Twin Blade")
    t(f"{i}.tower_twin_blade_azure", "Lâmina Gêmea Celeste", "Azure Twin Blade")
    t(f"{i}.tower_twin_blade.desc", "Metade de um par. Sozinha, é só uma boa espada.", "Half of a pair. Alone, it's just a good sword.")
    t(f"{i}.tower_twin_blade.use", "Com a gêmea na outra mão — Usar: Torrente Estelar, 16 golpes seguidos (30 s).",
      "With its twin in the other hand — Use: Starlight Torrent, 16 hits in a row (30 s).")
    t(f"{i}.tower_twin_blade.paired", "Par completo!", "Pair complete!")
    t(f"{i}.tower_kobold_lord_spawn_egg", "Ovo Gerador de Lorde Kobold do Pináculo", "Pinnacle Kobold Lord Spawn Egg")
    t(f"entity.{NS}.tower_kobold_lord", "Lorde Kobold do Pináculo", "Pinnacle Kobold Lord")

    m = f"message.{NS}"
    t(f"{m}.towers.entered", "Torre de Desafio! Vença cada andar para abrir a passagem para cima — o guardião espera no pináculo.",
      "Challenge Tower! Clear each floor to open the way up — the guardian waits at the pinnacle.")
    t(f"{m}.towers.protected", "A magia da torre protege estas pedras. Conquiste o pináculo primeiro.",
      "The tower's magic protects these stones. Conquer the pinnacle first.")
    t(f"{m}.towers.roof_denied", "A barreira do pináculo te repele: vença todos os andares primeiro!",
      "The pinnacle's barrier repels you: clear every floor first!")
    t(f"{m}.towers.puzzle_hint", "Acenda todas as runas. Cada alavanca alterna também as vizinhas.",
      "Light every rune. Each lever also flips its neighbours.")
    t(f"{m}.tower_teleport_crystal.blocked", "Uma presença poderosa perturba o cristal!", "A powerful presence disturbs the crystal!")
    t(f"{m}.tower_teleport_crystal.entrance", "Teleporte! — de volta à entrada da torre.", "Teleport! — back to the tower entrance.")
    t(f"{m}.tower_teleport_crystal.home", "Teleporte! — de volta ao seu ponto de renascimento.", "Teleport! — back to your respawn point.")
    t(f"{m}.tower_twin_blade.need_pair", "Segure a outra Lâmina Gêmea na outra mão.", "Hold the other Twin Blade in your other hand.")
    t(f"{m}.tower_twin_blade.starburst", "Torrente Estelar!", "Starlight Torrent!")
    t(f"{m}.tower_twin_blade.finish", "Torrente Estelar — %s golpes!", "Starlight Torrent — %s hits!")

    tw = f"tower.{NS}"
    t(f"{tw}.floor_title", "Andar %s", "Floor %s")
    t(f"{tw}.summit_title", "Pináculo", "Pinnacle")
    t(f"{tw}.kind.combat", "Salão das Ondas — derrote todos os inimigos", "Hall of Waves — defeat every enemy")
    t(f"{tw}.kind.elite", "Salão da Elite — os mais fortes da torre", "Elite Hall — the tower's strongest")
    t(f"{tw}.kind.treasure", "Câmara do Tesouro — vença os guardas", "Treasure Chamber — beat the guards")
    t(f"{tw}.kind.puzzle", "Salão das Runas — acenda todas as runas", "Hall of Runes — light every rune")
    t(f"{tw}.kind.boss", "O Guardião desperta", "The Guardian awakens")
    t(f"{tw}.hud.wave", "Andar %s · Onda %s/%s · Inimigos: %s", "Floor %s · Wave %s/%s · Enemies: %s")
    t(f"{tw}.hud.puzzle", "Andar %s · Runas acesas: %s/%s", "Floor %s · Runes lit: %s/%s")
    t(f"{tw}.hud.boss", "Pináculo · Derrote %s", "Pinnacle · Defeat %s")
    t(f"{tw}.cleared_title", "Andar %s vencido!", "Floor %s cleared!")
    t(f"{tw}.cleared_sub", "A passagem para cima se abriu.", "The way up is open.")
    t(f"{tw}.conquered_title", "Torre conquistada!", "Tower conquered!")
    t(f"{tw}.conquered_sub", "O cofre do pináculo se abriu.", "The pinnacle vault is open.")
    t(f"{tw}.reset", "Ninguém ficou no andar %s: os inimigos se dispersaram.", "Nobody stayed on floor %s: the enemies dispersed.")
    t(f"{tw}.guardian", "%s guarda o pináculo!", "%s guards the pinnacle!")
    t(f"{tw}.core.status", "Núcleo do Andar %s — %s", "Floor %s Core — %s")
    t(f"{tw}.core.idle", "adormecido", "dormant")
    t(f"{tw}.core.active", "em desafio", "challenge in progress")
    t(f"{tw}.core.cleared", "vencido", "cleared")
    # v1.0: o clique no Nucleo tambem mostra o recorde do andar (qualquer jogador ve, sem comando)
    t(f"{tw}.core.record", "%s · Recorde: %s (%s)", "%s · Record: %s (%s)")
    t(f"{tw}.core.record_mine", "%s · Recorde: %s (%s) · Seu melhor: %s", "%s · Record: %s (%s) · Your best: %s")
    for v, (pt, en) in {"stone": ("de Pedra", "Stone"), "frost": ("de Gelo", "Frost"), "sand": ("de Areia", "Sand"),
                        "forest": ("da Floresta", "Forest"), "sunken": ("Submersa", "Sunken"),
                        "floating": ("Flutuante", "Floating")}.items():
        t(f"place.{NS}.tower_{v}", f"Torre de Desafio {pt}", f"{en} Challenge Tower")
        t(f"sign.{NS}.towers.door_2.{v}", pt.upper(), en.upper())
        t(f"{tw}.variant.{v}", pt, en)
    lang_v10()

    s = f"sign.{NS}.towers"
    t(f"{s}.door_1", "TORRE DE DESAFIO", "CHALLENGE TOWER")
    t(f"{s}.door_3", "Vença ou volte.", "Win or turn back.")
    t(f"{s}.puzzle_1", "Acenda as runas.", "Light the runes.")
    t(f"{s}.puzzle_2", "Cada uma acorda", "Each one wakes")
    t(f"{s}.puzzle_3", "as vizinhas.", "its neighbours.")
    t(f"{s}.summit_1", "O tesouro é de", "The treasure belongs")
    t(f"{s}.summit_2", "quem vencer.", "to the victor.")

    c = f"command.{NS}.towers"
    t(f"{c}.given", "Itens das Torres de Desafio entregues.", "Challenge Tower items given.")
    t(f"{c}.not_found", "Nenhuma Torre de Desafio encontrada perto (só chunks novos do Overworld criados desde a v1.6 têm).",
      "No Challenge Tower found nearby (only new Overworld chunks generated since v1.6 have them).")
    t(f"{c}.tp", "Teleportado para a Torre de Desafio em %s %s %s.", "Teleported to the Challenge Tower at %s %s %s.")
    t(f"{c}.spawn_done", "%s invocado.", "%s summoned.")
    t(f"{c}.spawn_fail", "Não foi possível invocar o chefe aqui.", "Couldn't summon the boss here.")
    t(f"{c}.no_core", "Nenhum Núcleo do Andar perto (até 16 blocos).", "No Floor Core nearby (within 16 blocks).")
    t(f"{c}.floor_cleared", "Andar %s vencido à força.", "Floor %s force-cleared.")
    t(f"{c}.reset", "Torre reiniciada (andares e conquista).", "Tower reset (floors and conquest).")

    # ---------------- chefe
    k = "tower_kobold_lord"
    t(f"announce.{NS}.{k}.intro", "Humanos... no MEU pináculo? Sentinelas, ao ataque!", "Humans... on MY pinnacle? Sentinels, attack!")
    t(f"announce.{NS}.{k}.phase1", "Grrr... vocês mordem. Eu também!", "Grrr... you bite. So do I!")
    t(f"announce.{NS}.{k}.phase2", "Chega de machado. Provem a minha verdadeira lâmina!", "Enough axe. Taste my true blade!")
    t(f"announce.{NS}.{k}.summon", "Sentinelas! Protejam o rei!", "Sentinels! Protect the king!")
    t(f"announce.{NS}.{k}.death", "O pináculo... é de vocês... por enquanto...", "The pinnacle... is yours... for now...")
    t(f"attack.{NS}.{k}.cleave", "Lorde Kobold — Machadada", "Kobold Lord — Axe Cleave")
    t(f"attack.{NS}.{k}.bash", "Lorde Kobold — Investida de Broquel", "Kobold Lord — Buckler Rush")
    t(f"attack.{NS}.{k}.spin", "Lorde Kobold — Rodopio", "Kobold Lord — Whirlwind")
    t(f"attack.{NS}.{k}.slash3", "Lorde Kobold — Corte Triplo", "Kobold Lord — Triple Slash")
    t(f"attack.{NS}.{k}.lunge", "Lorde Kobold — Iai Relâmpago", "Kobold Lord — Lightning Iai")
    t(f"attack.{NS}.{k}.rising", "Lorde Kobold — Lua Ascendente", "Kobold Lord — Rising Moon")
    t(f"boss.{NS}.{k}.subtitle", "Guardião do Pináculo", "Guardian of the Pinnacle")
    t(f"boss.{NS}.{k}.phase1", "Fúria do Rei", "The King's Fury")
    t(f"boss.{NS}.{k}.phase2", "A Lâmina Escondida", "The Hidden Blade")
    t(f"boss_entrance.{NS}.{k}", "Machado, Broquel... e um segredo", "Axe, Buckler... and a secret")
    t(f"bestiary.{NS}.{k}.desc",
      "Rei dos kobolds que guarda o topo das Torres de Desafio. Luta de machado e broquel — até sacar a lâmina longa que esconde nas costas.",
      "King of the kobolds who guards the top of the Challenge Towers. Fights with axe and buckler — until he draws the long blade hidden on his back.")
    t(f"message.{NS}.{k}.sentinels", "Sentinelas protegem o Lorde: enquanto 2+ estiverem vivas, ele leva metade do dano!",
      "Sentinels guard the Lord: while 2+ live, he takes half damage!")
    t(f"message.{NS}.{k}.nodachi", "Ele trocou de arma! Os golpes ficaram mais longos e rápidos — mantenha distância das marcas.",
      "He switched weapons! His strikes are longer and faster now — keep clear of the marks.")

    # ---------------- diario
    j = f"journal.{NS}.towers"
    t(f"{j}.title", "As Torres de Desafio", "The Challenge Towers")
    t(f"{j}.intro", "Torres antigas brotam pelas planícies, gelos, desertos e selvas — algumas dentro do mar, outras no céu. Cada andar é um desafio — e no topo, um guardião protege um tesouro.",
      "Ancient towers rise across plains, ice, deserts and jungles — some inside the sea, others in the sky. Each floor is a trial — and at the top, a guardian protects a treasure.")
    t(f"{j}.lore", "Ninguém sabe quem as ergueu. Dizem que quem chega ao pináculo sai mais forte — se sair.",
      "Nobody knows who built them. They say whoever reaches the pinnacle comes out stronger — if they come out.")
    t(f"{j}.outro", "O pináculo é seu. Lá de cima, dá para ver a próxima torre no horizonte...",
      "The pinnacle is yours. From up there, you can see the next tower on the horizon...")
    steps = {
        "enter": ("Entre numa Torre de Desafio", "Enter a Challenge Tower",
                  "Siga a bússola do diário. Pedra nas planícies, gelo na neve, areia nos desertos, musgo nas selvas e pântanos; a Submersa fica no mar (desça pelo cais) e a Flutuante, no céu.",
                  "Follow the journal compass. Stone in plains, frost in snow, sand in deserts, moss in jungles and swamps; the Sunken one is in the sea (go down from the dock) and the Floating one, in the sky.",
                  "A porta ao sul tem uma placa: \"Vença ou volte\".", "The south door has a sign: \"Win or turn back\".",
                  "Overworld", "Overworld"),
        "floors": ("Vença 3 andares", "Clear 3 floors",
                   "Cada andar tem ondas de inimigos ou um enigma. Vencendo, os Selos do teto somem e a escada abre.",
                   "Each floor has enemy waves or a puzzle. Win and the ceiling Seals vanish, opening the stairs.",
                   "Se todos saírem do andar por 1 minuto, os inimigos se dispersam e a onda recomeça.",
                   "If everyone leaves a floor for 1 minute, the enemies disperse and the wave restarts.",
                   "Andares", "Floors"),
        "puzzle": ("Resolva um Salão das Runas", "Solve a Hall of Runes",
                   "Acenda todas as Alavancas Rúnicas da parede norte. Cada uma alterna também as vizinhas.",
                   "Light every Rune Lever on the north wall. Each one also flips its neighbours.",
                   "Dica: comece pela ponta e vá acertando da esquerda para a direita.",
                   "Tip: start at one end and fix them left to right.", "Opcional", "Optional"),
        "summit": ("Conquiste o pináculo", "Conquer the pinnacle",
                   "Vença os 7 andares e o Guardião no topo. O cofre do pináculo se abre com a vitória.",
                   "Clear all 7 floors and the Guardian at the top. The pinnacle vault opens with victory.",
                   "O guardião depende da torre: o Lorde Kobold, ou um rei das terras em volta.",
                   "The guardian depends on the tower: the Kobold Lord, or a king of the surrounding lands.",
                   "Topo", "Top"),
        "kobold_lord": ("Derrote o Lorde Kobold do Pináculo", "Defeat the Pinnacle Kobold Lord",
                        "Machado e broquel, sentinelas que o protegem (mate-as!) e, no fim, uma lâmina longa escondida.",
                        "Axe and buckler, sentinels that shield him (kill them!) and, at the end, a hidden long blade.",
                        "\"Não é um talwar... é uma nodachi!\"", "\"That's no talwar... it's a nodachi!\"",
                        "Opcional", "Optional"),
        "crystal": ("Use um Cristal de Teleporte", "Use a Teleport Crystal",
                    "Baús das torres ou receita (ametista, pérola do Ender e Cristal de Mana). Segure para usar.",
                    "Tower chests or crafting (amethyst, ender pearl and Mana Crystal). Hold to use.",
                    "\"Teleporte!\"", "\"Teleport!\"", "Opcional", "Optional"),
        "starburst": ("Solte a Torrente Estelar", "Unleash the Starlight Torrent",
                      "Junte as duas Lâminas Gêmeas (cofres e baús de tesouro das torres), uma em cada mão, e use.",
                      "Get both Twin Blades (tower vaults and treasure chests), one in each hand, and use them.",
                      "Dezesseis golpes. Nem um a menos.", "Sixteen strikes. Not one fewer.", "Opcional", "Optional"),
        "three": ("Conquiste 3 torres", "Conquer 3 towers",
                  "Cada torre só pode ser conquistada uma vez. Procure outras pelo mundo.",
                  "Each tower can be conquered only once. Find others around the world.",
                  "Pedra, gelo, areia, floresta, mar e céu: são seis tipos.", "Stone, frost, sand, forest, sea and sky: six kinds.", "Opcional", "Optional"),
    }
    steps.update({
        "affixes": ("Vença 3 andares com afixos", "Clear 3 floors with affixes",
                    "Acima do térreo, cada andar de ondas sorteia afixos: os inimigos ficam Fortificados, Voláteis, em Horda...",
                    "Above the ground floor, each wave floor rolls affixes: enemies become Fortified, Volatile, a Horde...",
                    "Passe o mouse no nome do afixo, no chat, para ler o que ele faz.",
                    "Hover an affix name in chat to read what it does.", "Opcional", "Optional"),
        "record": ("Marque 5 recordes pessoais", "Set 5 personal records",
                   "Cada andar cronometra do despertar à vitória. Bater o seu melhor tempo conta.",
                   "Each floor is timed from waking to victory. Beating your best time counts.",
                   "O tempo aparece quando o andar cai. Clique no Núcleo do Andar para ver o recorde do mundo e o seu.",
                   "The time shows when the floor falls. Click the Floor Core to see the world record and yours.", "Opcional", "Optional"),
        "sky_island": ("Pise numa Ilha Voadora", "Set foot on a Flying Island",
                       "Procure ilhas no céu. Embaixo de cada uma sobe uma Corrente Ascendente: entre nela para subir.",
                       "Look for islands in the sky. Under each one rises an Updraft: step into it to go up.",
                       "Ruínas antigas guardam baús lá em cima. Agache dentro do poço para descer devagar.",
                       "Old ruins guard chests up there. Sneak inside the well to float back down.", "Céu", "Sky"),
    })
    for sid, (pn, en, ph, eh, pl, el, ptip, etip) in steps.items():
        t(f"{j}.{sid}", pn, en)
        t(f"{j}.{sid}.hint", ph, eh)
        t(f"{j}.{sid}.lore", pl, el)
        t(f"{j}.{sid}.tip", ptip, etip)

    for name, d in (("pt_br", PT), ("en_us", EN)):
        write_json(os.path.join(LANG, f"{name}.json"), dict(sorted(d.items())))


def main():
    tex_items()
    tex_blocks()
    models()
    towers_geo.build_all(ASSETS)
    towers_struct.build_all(DATA)
    towers_sky.build_all(DATA)
    worldgen()
    loot_tables()
    recipes()
    tags()
    journal()
    lang()
    print("towers: ok")


if __name__ == "__main__":
    main()
