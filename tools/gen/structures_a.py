"""Gera os assets/dados do modulo "structures_a" (Santuarios Elementais — prova de Despertar).

Uso (na raiz do projeto):  py -I tools/gen/structures_a.py [--preview]

Gera:
  - texturas: elemental_brick_<el>, shrine_altar_<el>_top/_side (16x16)
  - modelos de bloco/item, blockstates, loot tables dos tijolos
  - loot tables dos baus (chests/shrine_<el>) com Pergaminhos de Glifo do elemento
  - as 6 estruturas .nbt (structure/shrine_<el>.nbt) + worldgen (structure, structure_set, template_pool)
  - tags no merge: has_structure/shrine_<el> (biomas), mineable/pickaxe, structure tag elemental_shrines
Com --preview imprime cortes ASCII de cada estrutura (para conferir o desenho).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
from structgen import Structure  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/structures_a")
NS = "isekai_mahou"
ELEMENTS = ["fire", "water", "earth", "wind", "light", "shadow"]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =================================================================================================
#  TEXTURAS
# =================================================================================================
PAL = {
    "fire": dict(mortar="240C08", b0="5A1A12", b1="74251A", b2="8E3220", acc0="C2410C", acc1="FF7A1E", acc2="FFD24A"),
    "water": dict(mortar="0B2430", b0="1B5466", b1="246C80", b2="2F8598", acc0="2FA6C8", acc1="6FD8F0", acc2="CFF6FF"),
    "earth": dict(mortar="261C10", b0="4E3B24", b1="65502F", b2="7B633B", acc0="3F6A24", acc1="62912E", acc2="E0A93A"),
    "wind": dict(mortar="7FA597", b0="B9D9CC", b1="CDE8DD", b2="E2F4EC", acc0="58B897", acc1="8CE6C6", acc2="FFFFFF"),
    "light": dict(mortar="A8966A", b0="D9CFAE", b1="E8E0C4", b2="F6F1DE", acc0="C9962A", acc1="F0C540", acc2="FFF3B0"),
    "shadow": dict(mortar="08050C", b0="19111F", b1="24182E", b2="30203C", acc0="5E2F94", acc1="8A4FC8", acc2="C9A0FF"),
}


def P(el, k):
    return hex_rgba(PAL[el][k])


def jitter(c, rnd, amount):
    f = (rnd.random() - 0.5) * 2 * amount
    return lighten(c, f) if f > 0 else darken(c, -f)


def brick_layer(c, el, seed, y_from=0, y_to=15):
    """Tijolos 7x3 com rejunte de 1px, fileiras alternadas (textura que repete sem emenda)."""
    rnd = random.Random(seed)
    mortar = P(el, "mortar")
    for y in range(y_from, y_to + 1):
        for x in range(16):
            c.set(x, y, jitter(mortar, rnd, 0.05))
    tones = [P(el, "b0"), P(el, "b1"), P(el, "b1"), P(el, "b2")]
    bricks = []
    for row in range(4):
        y0 = row * 4
        off = 0 if row % 2 == 0 else 4
        for bx in range(2):
            x0 = bx * 8 + off
            base = rnd.choice(tones)
            cells = []
            for y in range(y0, y0 + 3):
                if not (y_from <= y <= y_to):
                    continue
                for i in range(7):
                    x = (x0 + i) % 16
                    col = base
                    if y == y0:
                        col = lighten(base, 0.14)
                    elif y == y0 + 2:
                        col = darken(base, 0.14)
                    if i == 0:
                        col = lighten(col, 0.06)
                    if i == 6:
                        col = darken(col, 0.1)
                    c.set(x, y, jitter(col, rnd, 0.05))
                    cells.append((x, y))
            bricks.append((x0, y0, cells))
    return bricks


def motif_fire(c, rnd, bricks):
    a0, a1, a2 = P("fire", "acc0"), P("fire", "acc1"), P("fire", "acc2")
    # Rachaduras incandescentes que atravessam alguns tijolos.
    for (x0, y0, _cells) in rnd.sample(bricks, 3):
        x = x0 + rnd.randint(1, 3)
        for i in range(3):
            xx = (x + i + rnd.randint(0, 1)) % 16
            c.set(xx, y0 + (i % 3), a1 if i != 1 else a2)
            c.set((xx + 1) % 16, y0 + (i % 3), a0)
    for _ in range(5):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        if c.get(x, y)[0] < 100:
            c.set(x, y, a0)


def motif_water(c, rnd, bricks):
    a0, a1, a2 = P("water", "acc0"), P("water", "acc1"), P("water", "acc2")
    for (x0, y0, _cells) in rnd.sample(bricks, 3):
        # Pequena onda "~" no meio do tijolo.
        pts = [(1, 1), (2, 0), (3, 1), (4, 2), (5, 1)]
        for i, (dx, dy) in enumerate(pts):
            c.set((x0 + dx) % 16, y0 + dy, a1 if i % 2 else a2)
    for _ in range(4):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        c.set(x, y, a0)


def motif_earth(c, rnd, bricks):
    a0, a1, a2 = P("earth", "acc0"), P("earth", "acc1"), P("earth", "acc2")
    # Musgo escorrendo pela borda de cima de alguns tijolos.
    for (x0, y0, _cells) in rnd.sample(bricks, 4):
        for i in range(7):
            if rnd.random() < 0.55:
                c.set((x0 + i) % 16, y0, a1 if rnd.random() < 0.5 else a0)
                if rnd.random() < 0.3:
                    c.set((x0 + i) % 16, y0 + 1, a0)
    # Um veio de âmbar.
    (x0, y0, _cells) = rnd.choice(bricks)
    c.set((x0 + 3) % 16, y0 + 1, a2)
    c.set((x0 + 4) % 16, y0 + 1, lighten(a2, 0.3))
    c.set((x0 + 3) % 16, y0 + 2, darken(a2, 0.25))


def motif_wind(c, rnd, bricks):
    a0, a1 = P("wind", "acc0"), P("wind", "acc1")
    for (x0, y0, _cells) in rnd.sample(bricks, 3):
        # Redemoinho estilizado.
        for (dx, dy, k) in [(1, 1, 0), (2, 0, 1), (3, 0, 1), (4, 1, 1), (3, 2, 0), (2, 1, 0), (5, 2, 1)]:
            c.set((x0 + dx) % 16, y0 + dy, a1 if k else a0)


def motif_light(c, rnd, bricks):
    a0, a1, a2 = P("light", "acc0"), P("light", "acc1"), P("light", "acc2")
    # Incrustações de ouro nos cruzamentos do rejunte.
    for row in range(4):
        y = row * 4 + 3
        off = 0 if row % 2 == 0 else 4
        for bx in range(2):
            x = (bx * 8 + off + 7) % 16
            c.set(x, y, a1)
    # Uma estrela pequena num tijolo.
    (x0, y0, _cells) = rnd.choice(bricks)
    cx = (x0 + 3) % 16
    c.set(cx, y0 + 1, a2)
    c.set((cx - 1) % 16, y0 + 1, a1)
    c.set((cx + 1) % 16, y0 + 1, a1)
    c.set(cx, y0, a0)
    c.set(cx, y0 + 2, a0)


def motif_shadow(c, rnd, bricks):
    a0, a1, a2 = P("shadow", "acc0"), P("shadow", "acc1"), P("shadow", "acc2")
    runes = [[(1, 0), (1, 1), (1, 2), (2, 1), (3, 0), (3, 2)],
             [(1, 1), (2, 0), (3, 1), (2, 2)],
             [(1, 0), (2, 1), (3, 2), (3, 0), (1, 2)]]
    for (x0, y0, _cells) in rnd.sample(bricks, 3):
        r = rnd.choice(runes)
        for i, (dx, dy) in enumerate(r):
            c.set((x0 + dx + 1) % 16, y0 + dy, a1 if i % 2 else a0)
        c.set((x0 + 2) % 16, y0 + 1, a2)


MOTIFS = dict(fire=motif_fire, water=motif_water, earth=motif_earth, wind=motif_wind, light=motif_light, shadow=motif_shadow)

SYMBOLS = {
    "fire": ["...a...",
             "..aa...",
             "..aba..",
             ".abba..",
             ".abcba.",
             "aabcbaa",
             ".aaaaa."],
    "water": ["...a...",
              "...a...",
              "..aba..",
              ".abbba.",
              ".abcba.",
              ".abbba.",
              "..aaa.."],
    "earth": ["...a...",
              "..aba..",
              "..aba..",
              ".abcba.",
              ".abcba.",
              "abbcbba",
              "aaaaaaa"],
    "wind": [".aaaa..",
             "a....a.",
             "..bb..a",
             ".b..b.a",
             ".b.c..a",
             "..b..a.",
             "aaa.a.."],
    "light": ["a..a..a",
              ".a.b.a.",
              "..bcb..",
              "abcccba",
              "..bcb..",
              ".a.b.a.",
              "a..a..a"],
    "shadow": ["..aaa..",
               ".aa....",
               "aab....",
               "aab..c.",
               "aab....",
               ".aab...",
               "..aaa.."],
}


def altar_top(el):
    rnd = random.Random("top" + el)
    c = Canvas(16, 16)
    stone = P(el, "b1")
    for y in range(16):
        for x in range(16):
            c.set(x, y, jitter(stone, rnd, 0.06))
    trim = darken(P(el, "b0"), 0.2)
    c.outline_rect(0, 0, 15, 15, trim)
    c.outline_rect(1, 1, 14, 14, lighten(P(el, "b2"), 0.05))
    # Anel rúnico.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.4 <= d <= 6.3:
                c.set(x, y, P(el, "acc0"))
            elif 4.6 <= d < 5.4:
                c.set(x, y, darken(stone, 0.25))
    # Marcas nos 4 pontos cardeais do anel.
    for (x, y) in [(8, 2), (7, 2), (8, 13), (7, 13), (2, 8), (2, 7), (13, 8), (13, 7)]:
        c.set(x, y, P(el, "acc2"))
    pal = {"a": P(el, "acc0"), "b": P(el, "acc1"), "c": P(el, "acc2")}
    c.from_rows(SYMBOLS[el], pal, 4 if el != "wind" else 4, 4)
    return c


def altar_side(el):
    rnd = random.Random("side" + el)
    c = Canvas(16, 16)
    # Fundo: tijolos do elemento (aparece nas bordas que sobram).
    brick_layer(c, el, "sidebg" + el)
    dark = darken(P(el, "b0"), 0.25)
    # Faixa da base (linhas 13..15).
    for y in range(13, 16):
        for x in range(16):
            col = P(el, "b0") if y > 13 else lighten(P(el, "b2"), 0.08)
            c.set(x, y, jitter(col, rnd, 0.05))
    for x in range(0, 16, 4):
        c.set(x, 14, P(el, "acc0"))
    c.rect(0, 15, 15, 15, dark)
    # Coluna (colunas 3..12, linhas 5..12).
    for y in range(5, 13):
        for x in range(3, 13):
            col = P(el, "b1")
            if x == 3:
                col = lighten(col, 0.12)
            if x == 12:
                col = darken(col, 0.18)
            c.set(x, y, jitter(col, rnd, 0.05))
    # Runa vertical brilhante no meio da coluna.
    for y in range(6, 12):
        c.set(7, y, P(el, "acc1") if y % 2 else P(el, "acc0"))
        c.set(8, y, P(el, "acc1") if y % 2 == 0 else P(el, "acc0"))
    c.set(7, 8, P(el, "acc2"))
    c.set(8, 9, P(el, "acc2"))
    for (x, y) in [(5, 7), (10, 7), (5, 10), (10, 10)]:
        c.set(x, y, P(el, "acc0"))
    # Borda do tampo (linhas 2..4, colunas 1..14).
    for y in range(2, 5):
        for x in range(1, 15):
            col = lighten(P(el, "b2"), 0.1) if y == 2 else (P(el, "b1") if y == 3 else darken(P(el, "b0"), 0.1))
            c.set(x, y, jitter(col, rnd, 0.04))
    for x in range(2, 14, 3):
        c.set(x, 3, P(el, "acc1"))
    return c


def gen_textures():
    for el in ELEMENTS:
        c = Canvas(16, 16)
        bricks = brick_layer(c, el, "brick" + el)
        MOTIFS[el](c, random.Random("motif" + el), bricks)
        c.save(os.path.join(A, "textures/block/elemental_brick_" + el + ".png"))
        altar_top(el).save(os.path.join(A, f"textures/block/shrine_altar_{el}_top.png"))
        altar_side(el).save(os.path.join(A, f"textures/block/shrine_altar_{el}_side.png"))


# =================================================================================================
#  MODELOS / BLOCKSTATES / LOOT DOS BLOCOS
# =================================================================================================
def face(tex, cull=None):
    f = {"texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def gen_models():
    sides = ("north", "south", "east", "west")
    template = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 3, 16],
             "faces": dict({"down": face("#bottom", "down"), "up": face("#top")},
                           **{s: face("#side", s) for s in sides})},
            {"from": [3, 3, 3], "to": [13, 11, 13],
             "faces": {s: face("#side") for s in sides}},
            {"from": [1, 11, 1], "to": [15, 14, 15],
             "faces": dict({"down": face("#bottom"), "up": face("#top")}, **{s: face("#side") for s in sides})},
        ],
    }
    write_json(os.path.join(A, "models/block/shrine_altar_template.json"), template)
    variants = {}
    for el in ELEMENTS:
        write_json(os.path.join(A, f"models/block/shrine_altar_{el}.json"), {
            "parent": f"{NS}:block/shrine_altar_template",
            "textures": {"side": f"{NS}:block/shrine_altar_{el}_side",
                         "top": f"{NS}:block/shrine_altar_{el}_top",
                         "bottom": f"{NS}:block/elemental_brick_{el}"}})
        variants[f"element={el}"] = {"model": f"{NS}:block/shrine_altar_{el}"}
        # Tijolo elemental.
        bid = "elemental_brick_" + el
        write_json(os.path.join(A, f"models/block/{bid}.json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{bid}"}})
        write_json(os.path.join(A, f"blockstates/{bid}.json"), {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
        write_json(os.path.join(A, f"models/item/{bid}.json"), {"parent": f"{NS}:block/{bid}"})
        # Receita: 8 tijolos de pedra + 1 essência do elemento = 8 tijolos elementais.
        write_json(os.path.join(D, f"recipe/{bid}.json"), {
            "type": "minecraft:crafting_shaped", "category": "building", "group": f"{NS}:elemental_brick",
            "pattern": ["BBB", "BEB", "BBB"],
            "key": {"B": {"item": "minecraft:stone_bricks"}, "E": {"item": f"{NS}:essence_{el}"}},
            "result": {"id": f"{NS}:{bid}", "count": 8}})
        write_json(os.path.join(D, f"loot_table/blocks/{bid}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{bid}"})
    write_json(os.path.join(A, "blockstates/shrine_altar.json"), {"variants": variants})
    # Item do altar: o modelo muda conforme o elemento guardado (propriedade isekai_mahou:element, ver cliente).
    write_json(os.path.join(A, "models/item/shrine_altar.json"), {
        "parent": f"{NS}:block/shrine_altar_fire",
        "overrides": [{"predicate": {f"{NS}:element": i}, "model": f"{NS}:block/shrine_altar_{el}"}
                      for i, el in enumerate(ELEMENTS) if i > 0]})


# =================================================================================================
#  LOOT DOS BAÚS
# =================================================================================================
GLYPHS = {
    "fire": [("ignite", 6), ("explosion", 5), ("ember", 5), ("propulsion", 3), ("smoke", 3)],
    "water": [("soak", 6), ("tide", 5), ("bubble", 5), ("water_blade", 3), ("create_water", 2), ("water_breathing", 2)],
    "earth": [("rock", 6), ("raise_pillar", 5), ("stalagmite", 5), ("stoneskin", 3), ("excavate", 2)],
    "wind": [("wind_blade", 6), ("gust", 5), ("impulse", 5), ("double_jump", 3), ("glide", 3)],
    "light": [("holy_ray", 6), ("heal", 5), ("blind", 5), ("regenerate", 3), ("reveal", 2)],
    "shadow": [("life_drain", 6), ("weaken", 5), ("fear", 5), ("darkness", 3), ("shadow_step", 2)],
}

EXTRA_LOOT = {
    "fire": [("minecraft:blaze_powder", 8, 1, 4), ("minecraft:magma_cream", 6, 1, 3), ("minecraft:gold_ingot", 6, 2, 5),
             ("minecraft:fire_charge", 6, 2, 6), ("minecraft:obsidian", 3, 1, 3)],
    "water": [("minecraft:prismarine_shard", 8, 2, 6), ("minecraft:prismarine_crystals", 7, 2, 5),
              ("minecraft:nautilus_shell", 3, 1, 1), ("minecraft:heart_of_the_sea", 1, 1, 1), ("minecraft:gold_ingot", 5, 1, 4)],
    "earth": [("minecraft:amethyst_shard", 8, 2, 6), ("minecraft:iron_ingot", 7, 2, 5), ("minecraft:emerald", 4, 1, 3),
              ("minecraft:raw_copper", 6, 3, 8), ("minecraft:diamond", 1, 1, 1)],
    "wind": [("minecraft:feather", 8, 3, 8), ("minecraft:wind_charge", 6, 2, 6), ("minecraft:phantom_membrane", 4, 1, 2),
             ("minecraft:breeze_rod", 3, 1, 2), ("minecraft:emerald", 4, 1, 3)],
    "light": [("minecraft:glowstone_dust", 8, 3, 8), ("minecraft:gold_ingot", 7, 2, 5), ("minecraft:golden_apple", 2, 1, 1),
              ("minecraft:honey_bottle", 4, 1, 2), ("minecraft:quartz", 6, 3, 8)],
    "shadow": [("minecraft:ender_pearl", 6, 1, 3), ("minecraft:echo_shard", 2, 1, 1), ("minecraft:soul_torch", 6, 2, 6),
               ("minecraft:ink_sac", 6, 2, 5), ("minecraft:crying_obsidian", 4, 1, 3)],
}


def count_fn(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def gen_loot():
    for el in ELEMENTS:
        scroll_entries = [{
            "type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": w,
            "functions": [{"function": "minecraft:set_components", "components": {f"{NS}:glyph": f"{NS}:{g}"}}]}
            for g, w in GLYPHS[el]]
        pools = [
            {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2}, "bonus_rolls": 0, "entries": scroll_entries},
            {"rolls": 1, "bonus_rolls": 0, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:essence_{el}", "weight": 3, "functions": [count_fn(2, 4)]},
                {"type": "minecraft:item", "name": f"{NS}:essence_arcane", "weight": 1, "functions": [count_fn(1, 2)]}]},
            {"rolls": {"type": "minecraft:uniform", "min": 0, "max": 1}, "bonus_rolls": 0, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:mana_crystal", "functions": [count_fn(1, 2)]}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "bonus_rolls": 0, "entries": [
                {"type": "minecraft:item", "name": item, "weight": w, "functions": [count_fn(lo, hi)]}
                for item, w, lo, hi in EXTRA_LOOT[el]] + [{"type": "minecraft:empty", "weight": 4}]},
        ]
        write_json(os.path.join(D, f"loot_table/chests/shrine_{el}.json"), {
            "type": "minecraft:chest", "pools": pools, "random_sequence": f"{NS}:chests/shrine_{el}"})


# =================================================================================================
#  WORLDGEN
# =================================================================================================
WORLDGEN = {
    # el: (biomas, espaçamento, separação, salt, heightmap, start_height, exclusão de vilas)
    "fire": (["#minecraft:is_badlands", "minecraft:desert"], 44, 14, 1946100101, "WORLD_SURFACE_WG", 0, True),
    "water": (["#minecraft:is_ocean"], 40, 12, 1946100102, "OCEAN_FLOOR_WG", 0, False),
    "earth": (["#minecraft:is_hill", "minecraft:stony_peaks", "minecraft:windswept_savanna"], 44, 14, 1946100103,
              "WORLD_SURFACE_WG", 0, False),
    "wind": (["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:snowy_slopes"], 40, 12, 1946100104,
             "WORLD_SURFACE_WG", 0, False),
    "light": (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"], 44, 14, 1946100105,
              "WORLD_SURFACE_WG", 0, True),
    "shadow": (["minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:dark_forest"], 44, 14, 1946100106,
               "WORLD_SURFACE_WG", 0, False),
}


def gen_worldgen():
    for el in ELEMENTS:
        sid = "shrine_" + el
        biomes, spacing, sep, salt, hm, sh, excl = WORLDGEN[el]
        write_json(os.path.join(D, f"worldgen/template_pool/{sid}.json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": f"{NS}:{sid}",
                "processors": "minecraft:empty", "projection": "rigid"}}]})
        write_json(os.path.join(D, f"worldgen/structure/{sid}.json"), {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{sid}",
            "step": "surface_structures",
            "terrain_adaptation": "beard_thin",
            "spawn_overrides": {},
            "start_pool": f"{NS}:{sid}",
            "size": 1,
            "start_height": {"absolute": sh},
            "project_start_to_heightmap": hm,
            "max_distance_from_center": 32,
            "use_expansion_hack": False})
        placement = {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}
        if excl:
            placement["exclusion_zone"] = {"other_set": "minecraft:villages", "chunk_count": 4}
        write_json(os.path.join(D, f"worldgen/structure_set/{sid}.json"), {
            "structures": [{"structure": f"{NS}:{sid}", "weight": 1}], "placement": placement})
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"), {"values": biomes})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/elemental_shrines.json"),
               {"values": [f"{NS}:shrine_{el}" for el in ELEMENTS]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"values": [f"{NS}:elemental_brick_{el}" for el in ELEMENTS]})


# =================================================================================================
#  ESTRUTURAS
# =================================================================================================
ALTAR = "isekai_mahou:shrine_altar[element=%s]"


def brick(el):
    return "isekai_mahou:elemental_brick_" + el


def stairs(block, facing, half="bottom", wl=False):
    return f"{block}[facing={facing},half={half},shape=straight,waterlogged={'true' if wl else 'false'}]"


def slab(block, typ="bottom", wl=False):
    return f"{block}[type={typ},waterlogged={'true' if wl else 'false'}]"


def chest(s, x, y, z, el, facing, wl=False):
    s.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged={'true' if wl else 'false'}]",
          nbt={"LootTable": f"{NS}:chests/shrine_{el}"})


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}

NON_FULL = ("slab", "stairs", "wall", "fence", "pane", "bars", "lantern", "torch", "carpet", "button", "plate",
            "chain", "candle", "rod", "cluster", "bud", "roots", "vine", "lichen", "sign", "banner", "pot", "chest",
            "campfire", "bell", "water", "lava", "air", "kelp", "seagrass", "pickle", "fan", "dripstone", "flower",
            "grass", "fern", "bush", "sapling", "skull", "vein", "sensor", "shrieker", "rail", "snow", "azalea",
            "shrine_altar", "dandelion", "daisy", "poppy", "cornflower", "tulip", "allium", "orchid", "mushroom",
            "cobweb", "fire", "powder")


def base_name(state):
    return state.split("[", 1)[0] if state else None


def is_full(state):
    if not state:
        return False
    n = base_name(state).split(":", 1)[1]
    if n in ("moss_carpet",):
        return False
    if n in ("sea_lantern", "sculk_catalyst", "glowstone"):
        return True
    return not any(k in n for k in NON_FULL) or n.endswith("_block") and "wall" not in n and "slab" not in n


def with_props(state, props):
    name = base_name(state)
    cur = {}
    if "[" in state:
        for kv in state[:-1].split("[", 1)[1].split(","):
            if kv:
                k, v = kv.split("=")
                cur[k] = v
    cur.update(props)
    return name + "[" + ",".join(f"{k}={v}" for k, v in cur.items()) + "]"


def fix_connections(s):
    """Os blocos de estrutura não recalculam conexões ao serem colocados: calcula aqui painéis, grades e muros."""
    for (x, y, z), (state, nbt) in list(s.blocks.items()):
        n = base_name(state)
        is_pane = n.endswith("_pane") or n == "minecraft:iron_bars"
        is_wall = n.endswith("_wall") and "banner" not in n and "torch" not in n and "fan" not in n
        if not (is_pane or is_wall):
            continue
        props = {}
        con = {}
        for d, (dx, dy, dz) in DIRS.items():
            ns = s.get(x + dx, y, z + dz)
            nn = base_name(ns) if ns else ""
            if is_pane:
                ok = bool(ns) and (nn.endswith("_pane") or nn == "minecraft:iron_bars" or nn.endswith("_wall") or is_full(ns))
                props[d] = "true" if ok else "false"
            else:
                ok = bool(ns) and ((nn.endswith("_wall") and "banner" not in nn) or nn.endswith("_pane")
                                   or nn == "minecraft:iron_bars" or is_full(ns) or "fence_gate" in nn)
                props[d] = "low" if ok else "none"
            con[d] = ok
        if is_wall:
            above = s.get(x, y + 1, z)
            straight = (con["north"] and con["south"] and not con["east"] and not con["west"]) or \
                       (con["east"] and con["west"] and not con["north"] and not con["south"])
            props["up"] = "true" if (above and base_name(above) != "minecraft:air") or not straight else "false"
        s.blocks[(x, y, z)] = (with_props(state, props), nbt)


def air_fill(s, cells):
    for (x, y, z) in cells:
        if s.get(x, y, z) is None:
            s.set(x, y, z, "minecraft:air")


# -------------------------------------------------------------------------------------------------
#  Templo das Brasas (fogo)
# -------------------------------------------------------------------------------------------------
def build_fire():
    rnd = random.Random("shrine_fire")
    s = Structure(21, 12, 21)
    c = 10
    # Plataforma externa (y=0).
    for x in range(21):
        for z in range(21):
            edge = x in (0, 20) or z in (0, 20)
            st = "minecraft:polished_blackstone" if edge else rnd.choice(
                ["minecraft:blackstone", "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:gilded_blackstone"]
                if rnd.random() < 0.1 else ["minecraft:blackstone"])
            s.set(x, 0, z, st)
    # Chão interno: blackstone polido com faixas de tijolo elemental formando um losango em volta do altar.
    for x in range(3, 18):
        for z in range(3, 18):
            d = abs(x - c) + abs(z - c)
            if d in (6, 7):
                st = brick("fire")
            elif d == 10:
                st = "minecraft:chiseled_polished_blackstone"
            else:
                st = "minecraft:polished_blackstone_bricks" if (x + z) % 2 == 0 else "minecraft:polished_blackstone"
                if rnd.random() < 0.12:
                    st = "minecraft:cracked_polished_blackstone_bricks"
            s.set(x, 0, z, st)
    # Ar interno (limpa o terreno).
    air_fill(s, [(x, y, z) for x in range(3, 18) for y in range(1, 7) for z in range(3, 18)])
    # Paredes 17x17 (x,z 2..18), altura y=1..6.
    for y in range(1, 7):
        for x in range(2, 19):
            for z in range(2, 19):
                if x not in (2, 18) and z not in (2, 18):
                    continue
                pillar = (x in (2, 6, 10, 14, 18) and z in (2, 18)) or (z in (2, 6, 10, 14, 18) and x in (2, 18))
                if pillar:
                    st = "minecraft:polished_basalt[axis=y]"
                elif y == 1:
                    st = "minecraft:polished_blackstone_bricks"
                elif y == 4:
                    st = brick("fire")
                elif y == 6:
                    st = "minecraft:chiseled_polished_blackstone"
                else:
                    st = "minecraft:blackstone" if rnd.random() < 0.5 else "minecraft:polished_blackstone_bricks"
                    if rnd.random() < 0.15:
                        st = "minecraft:cracked_polished_blackstone_bricks"
                s.set(x, y, z, st)
    # Fendas com grades nas paredes leste/oeste e na sul.
    for wx in (2, 18):
        for z in (6 - 2, 8, 12, 16):
            for y in (3, 4):
                if s.get(wx, y, z) and "basalt" not in s.get(wx, y, z):
                    s.set(wx, y, z, "minecraft:iron_bars")
    # Muralha de lava contida (parede norte, atrás do altar): lava parada atrás de grades.
    for x in range(8, 13):
        for y in range(1, 7):
            s.set(x, y, 1, "minecraft:polished_blackstone_bricks")
    for x in range(9, 12):
        for y in range(1, 6):
            s.set(x, y, 2, "minecraft:lava")
            s.set(x, y, 3, "minecraft:iron_bars")
        s.set(x, 6, 2, "minecraft:magma_block")
        s.set(x, 6, 3, stairs("minecraft:polished_blackstone_brick_stairs", "north", "top"))
    for y in range(1, 7):
        s.set(8, y, 3, "minecraft:polished_basalt[axis=y]")
        s.set(12, y, 3, "minecraft:polished_basalt[axis=y]")
    s.set(10, 7, 2, "minecraft:magma_block")
    # Bacias de lava nos 4 cantos (lava afundada no chão, mureta em volta).
    for (bx, bz) in [(4, 4), (15, 4), (4, 15), (15, 15)]:
        for x in range(bx - 1, bx + 2):
            for z in range(bz - 1, bz + 2):
                if x == bx and z == bz:
                    s.set(x, 0, z, "minecraft:lava")
                else:
                    s.set(x, 0, z, "minecraft:magma_block")
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_wall")
    # Estrado do altar (5x5 + degraus em volta).
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            dx, dz = x - c, z - c
            if max(abs(dx), abs(dz)) <= 2:
                s.set(x, 1, z, brick("fire") if (dx == 0 and dz == 0) else
                      ("minecraft:polished_blackstone_bricks" if max(abs(dx), abs(dz)) == 2 else "minecraft:polished_blackstone"))
            elif abs(dx) == 3 and abs(dz) == 3:
                s.set(x, 1, z, slab("minecraft:polished_blackstone_brick_slab"))
            elif abs(dz) == 3:
                s.set(x, 1, z, stairs("minecraft:polished_blackstone_brick_stairs", "north" if dz > 0 else "south"))
            else:
                s.set(x, 1, z, stairs("minecraft:polished_blackstone_brick_stairs", "west" if dx > 0 else "east"))
    s.set(c, 2, c, ALTAR % "fire")
    # Braseiros nos cantos do estrado.
    for (x, z) in [(c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)]:
        s.set(x, 2, z, "minecraft:polished_blackstone_wall")
        s.set(x, 3, z, "minecraft:lantern[hanging=false,waterlogged=false]")
    # Baú diante da muralha de lava.
    chest(s, c, 1, 4, "fire", "south")
    s.set(c - 1, 1, 4, "minecraft:polished_blackstone_brick_wall")
    s.set(c + 1, 1, 4, "minecraft:polished_blackstone_brick_wall")
    s.set(c - 1, 2, 4, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    s.set(c + 1, 2, 4, "minecraft:candle[candles=2,lit=true,waterlogged=false]")
    # Teto (y=7) com abertura 5x5 sobre o altar e parapeito com ameias.
    for x in range(2, 19):
        for z in range(2, 19):
            if abs(x - c) <= 2 and abs(z - c) <= 2:
                continue
            edge = x in (2, 18) or z in (2, 18)
            s.set(x, 7, z, "minecraft:polished_blackstone_bricks" if edge else "minecraft:blackstone")
            if edge and (x + z) % 2 == 0:
                s.set(x, 8, z, "minecraft:polished_blackstone_brick_wall")
            if abs(x - c) == 3 and abs(z - c) <= 3 or abs(z - c) == 3 and abs(x - c) <= 3:
                s.set(x, 7, z, "minecraft:chiseled_polished_blackstone")
                s.set(x, 8, z, slab("minecraft:polished_blackstone_brick_slab"))
    for y in (7, 8):
        for x in range(c - 2, c + 3):
            for z in range(c - 2, c + 3):
                s.set(x, y, z, "minecraft:air")
    # Lanternas penduradas no teto.
    for (x, z) in [(6, 6), (14, 6), (6, 14), (14, 14), (10, 15)]:
        s.set(x, 6, z, "minecraft:lantern[hanging=true,waterlogged=false]")
    # Torres de canto com fogueira (fumaça visível de longe).
    for (x, z) in [(2, 2), (18, 2), (2, 18), (18, 18)]:
        for y in range(7, 9):
            s.set(x, y, z, "minecraft:polished_basalt[axis=y]")
        s.set(x, 9, z, "minecraft:magma_block")
        s.set(x, 10, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    # Entrada sul (3x3) com arco e braseiros do lado de fora.
    for x in range(c - 1, c + 2):
        for y in range(1, 4):
            s.set(x, y, 18, "minecraft:air")
        s.set(x, 0, 18, "minecraft:polished_blackstone_bricks")
    s.set(c - 1, 4, 18, stairs("minecraft:polished_blackstone_brick_stairs", "west", "top"))
    s.set(c + 1, 4, 18, stairs("minecraft:polished_blackstone_brick_stairs", "east", "top"))
    s.set(c, 4, 18, "minecraft:chiseled_polished_blackstone")
    for x in (c - 3, c + 3):
        s.set(x, 1, 19, "minecraft:polished_blackstone_wall")
        s.set(x, 2, 19, "minecraft:polished_blackstone_wall")
        s.set(x, 3, 19, "minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]")
    for z in range(19, 21):
        for x in range(c - 1, c + 2):
            s.set(x, 0, z, "minecraft:polished_blackstone_bricks")
    # Rochas de basalto e magma espalhadas do lado de fora.
    for _ in range(14):
        x, z = rnd.randint(0, 20), rnd.randint(0, 20)
        if 1 <= x <= 19 and 1 <= z <= 19 or (z >= 19 and 7 <= x <= 13):
            continue
        s.set(x, 1, z, rnd.choice(["minecraft:basalt[axis=y]", "minecraft:magma_block", "minecraft:blackstone"]))
    fix_connections(s)
    return s


# -------------------------------------------------------------------------------------------------
#  Templo Submerso (água)
# -------------------------------------------------------------------------------------------------
def build_water():
    rnd = random.Random("shrine_water")
    s = Structure(21, 13, 21)
    c = 10
    wall_r = 9.0

    def dist(x, z):
        return math.hypot(x - c, z - c)

    # Perfil da cúpula: raio interno por altura.
    inner = {1: 8.4, 2: 8.4, 3: 8.4, 4: 8.4, 5: 8.4, 6: 8.4, 7: 7.6, 8: 6.6, 9: 5.2, 10: 3.2}
    # Cada camada cobre a borda da camada de baixo (cúpula sem frestas).
    outer = {y: max(r + 1.0, inner.get(y - 1, 0) + 0.2) for y, r in inner.items()}
    outer[11] = inner[10] + 0.3
    # Água em todo o interior (o templo é inundado; o jogador recebe Força do Conduíte na prova).
    for y, r in inner.items():
        for x in range(21):
            for z in range(21):
                if dist(x, z) <= r:
                    s.set(x, y, z, "minecraft:water")
    # Piso.
    for x in range(21):
        for z in range(21):
            d = dist(x, z)
            if d > 10.4:
                continue
            if d > 9.4:
                st = "minecraft:dark_prismarine"
            elif 6.5 <= d <= 7.4:
                st = brick("water")
            elif d <= 2.5:
                st = "minecraft:prismarine_bricks"
            else:
                ang = math.atan2(z - c, x - c)
                st = "minecraft:dark_prismarine" if int((ang + math.pi) / (math.pi / 4)) % 2 == 0 else "minecraft:prismarine_bricks"
                if rnd.random() < 0.1:
                    st = "minecraft:prismarine"
            s.set(x, 0, z, st)
    # Paredes e cúpula.
    for y, ro in outer.items():
        ri = inner.get(y, -1)
        for x in range(21):
            for z in range(21):
                d = dist(x, z)
                if ri < d <= ro:
                    if y in (1, 6):
                        st = "minecraft:dark_prismarine"
                    elif y == 4:
                        st = brick("water")
                    elif y >= 7:
                        st = "minecraft:prismarine" if rnd.random() < 0.6 else "minecraft:prismarine_bricks"
                    else:
                        st = "minecraft:prismarine_bricks" if rnd.random() < 0.7 else "minecraft:prismarine"
                    s.set(x, y, z, st)
    # Óculo de vidro + lanterna do mar no topo.
    for x in range(21):
        for z in range(21):
            d = dist(x, z)
            if d <= 2.4:
                s.set(x, 11, z, "minecraft:sea_lantern" if d < 0.6 else "minecraft:light_blue_stained_glass")
    # Lanternas do mar embutidas na parede (y=3) em 8 direções e na cúpula (y=8).
    for k in range(8):
        a = k * math.pi / 4
        x, z = round(c + math.cos(a) * 9), round(c + math.sin(a) * 9)
        s.set(x, 3, z, "minecraft:sea_lantern")
        a2 = a + math.pi / 8
        x2, z2 = round(c + math.cos(a2) * 7.2), round(c + math.sin(a2) * 7.2)
        if s.get(x2, 8, z2) and "water" not in s.get(x2, 8, z2):
            s.set(x2, 8, z2, "minecraft:sea_lantern")
    # 4 portais em arco (N, S, L, O) abertos para o mar.
    for (dx, dz) in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
        for t in (-1, 0, 1):
            for r in (8, 9, 10):
                x = c + dx * r + (t if dx == 0 else 0)
                z = c + dz * r + (t if dz == 0 else 0)
                for y in range(1, 4):
                    s.set(x, y, z, "minecraft:water")
                fx, fz = (x, z)
                if r == 9:
                    s.set(fx, 4, fz, "minecraft:dark_prismarine" if t == 0 else
                          stairs("minecraft:prismarine_brick_stairs",
                                 ("west" if t < 0 else "east") if dx == 0 else ("north" if t < 0 else "south"), "top", True))
    # Pilares internos (raio 7.6) com lanterna do mar no meio.
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = round(c + math.cos(a) * 7.6), round(c + math.sin(a) * 7.6)
        for y in range(1, 7):
            s.set(x, y, z, "minecraft:sea_lantern" if y == 4 else "minecraft:prismarine_bricks" if y in (1, 6) else "minecraft:prismarine")
    # Estrado do altar.
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            dx, dz = x - c, z - c
            m = max(abs(dx), abs(dz))
            if m <= 2:
                s.set(x, 1, z, brick("water") if m == 0 else ("minecraft:dark_prismarine" if m == 2 else "minecraft:prismarine_bricks"))
            elif abs(dx) == 3 and abs(dz) == 3:
                s.set(x, 1, z, slab("minecraft:dark_prismarine_slab", wl=True))
            elif abs(dz) == 3:
                s.set(x, 1, z, stairs("minecraft:prismarine_brick_stairs", "north" if dz > 0 else "south", wl=True))
            else:
                s.set(x, 1, z, stairs("minecraft:prismarine_brick_stairs", "west" if dx > 0 else "east", wl=True))
    s.set(c, 2, c, ALTAR % "water")
    for (x, z) in [(c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)]:
        s.set(x, 2, z, "minecraft:sea_pickle[pickles=4,waterlogged=true]")
    # Baú (norte), corais e algas pelo chão perto das paredes.
    chest(s, c, 1, 4, "water", "south", wl=True)
    s.set(c - 1, 1, 4, "minecraft:dark_prismarine")
    s.set(c + 1, 1, 4, "minecraft:dark_prismarine")
    s.set(c - 1, 2, 4, "minecraft:sea_pickle[pickles=3,waterlogged=true]")
    s.set(c + 1, 2, 4, "minecraft:sea_pickle[pickles=2,waterlogged=true]")
    corals = ["tube", "brain", "bubble", "fire", "horn"]
    for _ in range(26):
        a = rnd.random() * math.pi * 2
        r = rnd.uniform(7.0, 8.3)
        x, z = round(c + math.cos(a) * r), round(c + math.sin(a) * r)
        if s.get(x, 1, z) != "minecraft:water":
            continue
        kind = rnd.random()
        if kind < 0.35:
            s.set(x, 1, z, f"minecraft:{rnd.choice(corals)}_coral_block")
            if s.get(x, 2, z) == "minecraft:water":
                s.set(x, 2, z, f"minecraft:{rnd.choice(corals)}_coral_fan[waterlogged=true]")
        elif kind < 0.7:
            s.set(x, 1, z, "minecraft:seagrass")
        else:
            s.set(x, 1, z, f"minecraft:{rnd.choice(corals)}_coral[waterlogged=true]")
    # Destroços de prismarinho fora do templo.
    for _ in range(12):
        a = rnd.random() * math.pi * 2
        x, z = round(c + math.cos(a) * 10), round(c + math.sin(a) * 10)
        if 0 <= x < 21 and 0 <= z < 21 and dist(x, z) > 9.6 and s.get(x, 1, z) is None:
            s.set(x, 1, z, rnd.choice(["minecraft:prismarine", "minecraft:dark_prismarine", brick("water")]))
    fix_connections(s)
    return s


# -------------------------------------------------------------------------------------------------
#  Caverna Ancestral (terra)
# -------------------------------------------------------------------------------------------------
def build_earth():
    rnd = random.Random("shrine_earth")
    s = Structure(23, 15, 23)
    c = 11
    RX, RY = 11.0, 13.0
    IX, IY = 8.6, 10.0

    def inside(x, y, z, rx, ry):
        return ((x - c) / rx) ** 2 + (y / ry) ** 2 + ((z - c) / rx) ** 2 <= 1.0

    def bump(x, y, z):
        # Pequena irregularidade determinística para a rocha não parecer uma esfera perfeita.
        return 0.55 * math.sin(x * 1.3 + z * 0.7) + 0.45 * math.cos(z * 1.1 - y * 0.9)

    rock = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:cobbled_deepslate",
            "minecraft:deepslate[axis=y]", "minecraft:mossy_cobblestone"]
    for y in range(0, 14):
        for x in range(23):
            for z in range(23):
                b = bump(x, y, z)
                if not inside(x, y, z, RX + b * 0.6, RY + b * 0.6):
                    continue
                if y >= 1 and inside(x, y, z, IX + b * 0.3, IY + b * 0.3):
                    s.set(x, y, z, "minecraft:air")
                    continue
                if y == 0 and inside(x, 0, z, IX, IY):
                    continue  # piso tratado abaixo
                above_out = not inside(x, y + 1, z, RX + bump(x, y + 1, z) * 0.6, RY + bump(x, y + 1, z) * 0.6)
                if above_out and y >= 2:
                    st = "minecraft:moss_block" if rnd.random() < 0.75 else rnd.choice(["minecraft:rooted_dirt", "minecraft:coarse_dirt"])
                else:
                    st = rnd.choice(rock)
                    if rnd.random() < 0.04:
                        st = "minecraft:verdant_froglight[axis=y]"
                    elif rnd.random() < 0.05:
                        st = "minecraft:amethyst_block"
                s.set(x, y, z, st)
    # Vegetação sobre o monte.
    for x in range(23):
        for z in range(23):
            top = None
            for y in range(13, -1, -1):
                if s.get(x, y, z) and s.get(x, y, z) != "minecraft:air":
                    top = y
                    break
            if top is None or top < 3 or s.get(x, top, z) != "minecraft:moss_block":
                continue
            r = rnd.random()
            if r < 0.07:
                s.set(x, top + 1, z, rnd.choice(["minecraft:azalea", "minecraft:flowering_azalea"]))
            elif r < 0.3:
                s.set(x, top + 1, z, "minecraft:moss_carpet")
            elif r < 0.45:
                s.set(x, top + 1, z, "minecraft:short_grass")
    # Piso interno.
    for x in range(23):
        for z in range(23):
            if not inside(x, 0, z, IX, IY):
                continue
            d = math.hypot(x - c, z - c)
            if d <= 3.5:
                st = "minecraft:deepslate_tiles"
            elif 3.5 < d <= 4.5:
                st = brick("earth")
            else:
                st = rnd.choice(["minecraft:polished_deepslate", "minecraft:deepslate_tiles", "minecraft:moss_block",
                                 "minecraft:moss_block", "minecraft:rooted_dirt", "minecraft:cobbled_deepslate"])
            s.set(x, 0, z, st)
            if st == "minecraft:moss_block" and d > 5.5 and rnd.random() < 0.5:
                s.set(x, 1, z, "minecraft:moss_carpet")
    # Entrada sul: túnel 3x4 com moldura de tijolos de pedra.
    for z in range(17, 23):
        for x in range(c - 1, c + 2):
            for y in range(1, 5):
                s.set(x, y, z, "minecraft:air")
            s.set(x, 0, z, "minecraft:deepslate_tiles" if z < 21 else "minecraft:cobbled_deepslate")
        for y in range(1, 5):
            for x in (c - 2, c + 2):
                if s.get(x, y, z) not in (None, "minecraft:air"):
                    s.set(x, y, z, "minecraft:mossy_stone_bricks" if (y + z) % 2 else "minecraft:deepslate_bricks")
        if s.get(c, 5, z) not in (None, "minecraft:air"):
            for x in range(c - 1, c + 2):
                s.set(x, 5, z, "minecraft:deepslate_bricks")
    for x in range(c - 2, c + 3):
        s.set(x, 5, 21, "minecraft:chiseled_deepslate" if x == c else "minecraft:deepslate_bricks")
    for x in (c - 2, c + 2):
        for y in range(1, 5):
            s.set(x, y, 21, "minecraft:deepslate_brick_wall")
    # Estrado do altar e quatro menires com ametista.
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            dx, dz = x - c, z - c
            m = max(abs(dx), abs(dz))
            if m <= 1:
                s.set(x, 1, z, brick("earth") if m == 0 else "minecraft:polished_deepslate")
            elif abs(dx) == 2 and abs(dz) == 2:
                s.set(x, 1, z, slab("minecraft:deepslate_tile_slab"))
            elif abs(dz) == 2:
                s.set(x, 1, z, stairs("minecraft:deepslate_tile_stairs", "north" if dz > 0 else "south"))
            else:
                s.set(x, 1, z, stairs("minecraft:deepslate_tile_stairs", "west" if dx > 0 else "east"))
    s.set(c, 2, c, ALTAR % "earth")
    for (dx, dz) in [(-4, -4), (4, -4), (-4, 4), (4, 4)]:
        x, z = c + dx, c + dz
        s.set(x, 1, z, "minecraft:deepslate_bricks")
        s.set(x, 2, z, "minecraft:deepslate_bricks")
        s.set(x, 3, z, "minecraft:chiseled_deepslate")
        s.set(x, 4, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    # Raízes ancestrais descendo do teto pelas paredes (madeira escura + raízes de mangue).
    for k in range(6):
        a = k * math.pi / 3 + 0.4
        bx, bz = c + math.cos(a) * 7.6, c + math.sin(a) * 7.6
        for y in range(1, 11):
            # A raiz se encosta na parede: anda para fora conforme sobe.
            r = 7.6 + (y - 1) * -0.25
            x, z = round(c + math.cos(a) * r), round(c + math.sin(a) * r)
            if s.get(x, y, z) == "minecraft:air":
                s.set(x, y, z, "minecraft:dark_oak_wood[axis=y]" if y % 3 else "minecraft:mangrove_roots[waterlogged=false]")
        x, z = round(bx), round(bz)
        if s.get(x, 0, z):
            s.set(x, 0, z, "minecraft:rooted_dirt")
    # Teto: raízes pendentes, estalactites e cachos de ametista; liquens no chão.
    for x in range(23):
        for z in range(23):
            for y in range(12, 1, -1):
                if s.get(x, y, z) not in (None, "minecraft:air") and s.get(x, y - 1, z) == "minecraft:air":
                    if "wood" in s.get(x, y, z) or "roots" in s.get(x, y, z):
                        break
                    r = rnd.random()
                    if r < 0.12:
                        s.set(x, y - 1, z, "minecraft:hanging_roots[waterlogged=false]")
                    elif r < 0.2 and y >= 6 and s.get(x, y - 2, z) == "minecraft:air" and s.get(x, y - 3, z) == "minecraft:air":
                        s.set(x, y - 1, z, "minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=false]")
                        s.set(x, y - 2, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
                    elif r < 0.27:
                        s.set(x, y - 1, z, "minecraft:amethyst_cluster[facing=down,waterlogged=false]")
                    break
    for _ in range(30):
        x, z = rnd.randint(2, 20), rnd.randint(2, 20)
        d = math.hypot(x - c, z - c)
        if 5.0 < d < 8.0 and s.get(x, 1, z) == "minecraft:air" and s.get(x, 0, z):
            r = rnd.random()
            if r < 0.4:
                s.set(x, 1, z, "minecraft:glow_lichen[down=true,east=false,north=false,south=false,up=false,waterlogged=false,west=false]")
            elif r < 0.6:
                s.set(x, 1, z, "minecraft:large_amethyst_bud[facing=up,waterlogged=false]")
            elif r < 0.75:
                s.set(x, 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    # Baú (norte) entre duas lanternas.
    chest(s, c, 1, 4, "earth", "south")
    s.set(c - 1, 1, 4, "minecraft:deepslate_bricks")
    s.set(c + 1, 1, 4, "minecraft:deepslate_bricks")
    s.set(c - 1, 2, 4, "minecraft:lantern[hanging=false,waterlogged=false]")
    s.set(c + 1, 2, 4, "minecraft:lantern[hanging=false,waterlogged=false]")
    fix_connections(s)
    return s


# -------------------------------------------------------------------------------------------------
#  Pico dos Ventos (vento)
# -------------------------------------------------------------------------------------------------
WIND_PARKOUR = []


def build_wind():
    rnd = random.Random("shrine_wind")
    s = Structure(21, 26, 21)
    c = 10
    TOP = 18  # piso da plataforma do altar

    def d(x, z):
        return math.hypot(x - c, z - c)

    # Base (y=0): disco de calcita com detalhes de diorito polido.
    for x in range(21):
        for z in range(21):
            r = d(x, z)
            if r <= 10.3:
                st = "minecraft:calcite"
                if 3.4 < r <= 4.4:
                    st = "minecraft:polished_diorite"
                elif r > 9.4:
                    st = "minecraft:diorite"
                elif rnd.random() < 0.12:
                    st = "minecraft:snow_block"
                s.set(x, 0, z, st)
    # Torre central (raio 2.6) até o piso de cima, com faixas de lã e tijolo do vento.
    for y in range(1, TOP):
        for x in range(21):
            for z in range(21):
                if d(x, z) <= 2.6:
                    if y % 6 == 3:
                        st = brick("wind")
                    elif y % 6 == 0:
                        st = "minecraft:white_wool" if (x + z) % 2 else "minecraft:light_blue_wool"
                    else:
                        st = "minecraft:calcite" if rnd.random() < 0.8 else "minecraft:polished_diorite"
                    s.set(x, y, z, st)
    # Mísulas que seguram a plataforma.
    for x in range(21):
        for z in range(21):
            r = d(x, z)
            if r <= 3.6:
                s.set(x, TOP - 2, z, "minecraft:calcite")
            if r <= 4.8:
                s.set(x, TOP - 1, z, "minecraft:calcite" if r <= 3.6 else "minecraft:polished_diorite")
    # Plataforma do altar.
    for x in range(21):
        for z in range(21):
            r = d(x, z)
            if r <= 6.5:
                if r <= 1.5:
                    st = brick("wind")
                elif 3.5 <= r <= 4.4:
                    st = brick("wind")
                elif r > 5.6:
                    st = "minecraft:polished_diorite"
                else:
                    st = "minecraft:calcite"
                s.set(x, TOP, z, st)
                for y in range(TOP + 1, TOP + 7):
                    s.set(x, y, z, "minecraft:air")
    # Escada flutuante em espiral (parkour) em volta da torre.
    WIND_PARKOUR.clear()
    n = TOP - 1
    a0 = math.pi / 2  # começa no sul
    step = math.radians(31)
    prev = None
    for i in range(n):
        a = a0 + i * step
        px, pz = c + math.cos(a) * 8.0, c + math.sin(a) * 8.0
        y = 1 + i
        cells = [(x, z) for x in range(21) for z in range(21) if math.hypot(x - px, z - pz) <= 1.25]
        mat = [brick("wind"), "minecraft:calcite", "minecraft:white_wool", "minecraft:polished_diorite"][i % 4]
        for (x, z) in cells:
            s.set(x, y, z, mat)
            for yy in range(y + 1, y + 4):
                if s.get(x, yy, z) is None:
                    s.set(x, yy, z, "minecraft:air")
        if prev:
            gap = min(math.hypot(x1 - x2, z1 - z2) for (x1, z1) in prev for (x2, z2) in cells) - 1
            WIND_PARKOUR.append(round(gap, 2))
        prev = cells
        if i % 4 == 2:
            # Poste com lanterna para iluminar o caminho à noite (na célula mais externa).
            ox, oz = max(cells, key=lambda p: math.hypot(p[0] - c, p[1] - c))
            s.set(ox, y + 1, oz, "minecraft:diorite_wall")
            s.set(ox, y + 2, oz, "minecraft:lantern[hanging=false,waterlogged=false]")
    last_angle = a0 + (n - 1) * step
    # Parapeito na borda da plataforma, aberto onde o parkour chega.
    for x in range(21):
        for z in range(21):
            r = d(x, z)
            if 5.6 < r <= 6.5:
                ang = math.atan2(z - c, x - c)
                diff = abs((ang - last_angle + math.pi) % (2 * math.pi) - math.pi)
                if diff > 0.38:
                    s.set(x, TOP + 1, z, "minecraft:diorite_wall")
    # Arco com o sino sobre o altar.
    for x in (c - 5, c + 5):
        for y in range(TOP + 1, TOP + 6):
            s.set(x, y, c, "minecraft:calcite" if y < TOP + 5 else "minecraft:chiseled_quartz_block")
    for x in range(c - 4, c + 5):
        s.set(x, TOP + 6, c, "minecraft:calcite" if abs(x - c) > 1 else brick("wind"))
    s.set(c - 4, TOP + 5, c, stairs("minecraft:diorite_stairs", "west", "top"))
    s.set(c + 4, TOP + 5, c, stairs("minecraft:diorite_stairs", "east", "top"))
    s.set(c, TOP + 5, c, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    for x in (c - 5, c + 5):
        s.set(x, TOP + 3, c - 1, "minecraft:white_wall_banner[facing=north]")
        s.set(x, TOP + 3, c + 1, "minecraft:light_blue_wall_banner[facing=south]")
    # Sinos de pé nos postes das diagonais.
    for (dx, dz) in [(-4, -4), (4, -4), (-4, 4), (4, 4)]:
        x, z = c + dx, c + dz
        if d(x, z) > 5.6:
            x, z = c + int(dx * 0.75), c + int(dz * 0.75)
        s.set(x, TOP + 1, z, "minecraft:polished_diorite")
        s.set(x, TOP + 2, z, "minecraft:bell[attachment=floor,facing=" + ("north" if dz < 0 else "south") + ",powered=false]")
    s.set(c, TOP + 1, c, ALTAR % "wind")
    chest(s, c, TOP + 1, c - 3, "wind", "south")
    # Portal de partida do parkour (no chão, ao sul).
    for x in (c - 2, c + 2):
        for y in range(1, 4):
            s.set(x, y, c + 9, "minecraft:calcite")
        s.set(x, 4, c + 9, "minecraft:white_wool")
    for x in range(c - 2, c + 3):
        s.set(x, 5, c + 9, slab("minecraft:diorite_slab"))
    s.set(c, 4, c + 9, "minecraft:bell[attachment=ceiling,facing=east,powered=false]")
    # Espaço livre em volta da torre (o terreno do pico não pode enterrar o parkour).
    air_fill(s, [(x, y, z) for x in range(21) for z in range(21) for y in range(1, TOP + 7) if d(x, z) <= 10.3])
    fix_connections(s)
    return s


# -------------------------------------------------------------------------------------------------
#  Catedral em Ruínas (luz)
# -------------------------------------------------------------------------------------------------
def build_light():
    rnd = random.Random("shrine_light")
    s = Structure(17, 15, 27)
    cx = 8
    AZ = 10  # z do altar
    # Piso: xadrez de quartzo com cruz de pedra luminosa.
    for x in range(17):
        for z in range(27):
            if x in (0, 16) or z in (0, 26):
                st = "minecraft:quartz_bricks"
            elif x in (1, 15) or z in (1, 25):
                st = "minecraft:smooth_quartz"
            else:
                st = "minecraft:smooth_quartz" if (x + z) % 2 == 0 else "minecraft:quartz_block"
                if x == cx and z % 4 == 0 or (z == AZ and x % 4 == 0):
                    st = "minecraft:glowstone"
                elif x == cx:
                    st = brick("light")
            s.set(x, 0, z, st)
    # Mato tomando conta do piso nas laterais (ruína).
    for _ in range(28):
        x, z = rnd.choice([2, 3, 13, 14]), rnd.randint(14, 24)
        s.set(x, 0, z, "minecraft:grass_block[snowy=false]")
        s.set(x, 1, z, rnd.choice(["minecraft:short_grass", "minecraft:dandelion", "minecraft:oxeye_daisy",
                                   "minecraft:short_grass", "minecraft:cornflower"]))
    # Ar interno.
    air_fill(s, [(x, y, z) for x in range(2, 15) for y in range(1, 12) for z in range(2, 25)])
    # Paredes da nave (x=1 e x=15), abside (z=1) e fachada (z=25). Topo irregular de ruína.
    def wall_top(x, z):
        if z <= 8:
            return 11  # trecho ainda coberto pela abóbada
        return 9 + int(2.2 * math.sin(z * 0.9 + x) + 1.5 * math.cos(z * 0.37))

    for z in range(1, 26):
        for x in (1, 15):
            top = wall_top(x, z)
            for y in range(1, top + 1):
                pil = z in (1, 6, 12, 18, 25)
                st = "minecraft:quartz_pillar[axis=y]" if pil else (
                    brick("light") if y == 1 else "minecraft:quartz_bricks" if y % 4 else "minecraft:chiseled_quartz_block")
                s.set(x, y, z, st)
    for x in range(1, 16):
        for y in range(1, 14):
            s.set(x, y, 1, "minecraft:quartz_bricks" if (x + y) % 5 else "minecraft:chiseled_quartz_block")
        top = 13 - abs(x - cx) // 2
        for y in range(1, top + 1):
            s.set(x, y, 25, "minecraft:quartz_bricks" if y % 4 else "minecraft:smooth_quartz")
    # Pilastras internas com pedra luminosa no capitel.
    for z in (6, 12, 18):
        for x in (2, 14):
            for y in range(1, 8):
                s.set(x, y, z, "minecraft:quartz_pillar[axis=y]")
            s.set(x, 8, z, "minecraft:glowstone")
            s.set(x, 9, z, "minecraft:chiseled_quartz_block")
    # Janelas altas de vidro amarelo entre as pilastras (algumas quebradas).
    for x in (1, 15):
        for z in (3, 4, 9, 10, 15, 16, 21, 22):
            for y in range(3, 8):
                if y <= wall_top(x, z) - 1:
                    s.set(x, y, z, "minecraft:yellow_stained_glass_pane" if rnd.random() < 0.85 else "minecraft:air")
            if wall_top(x, z) >= 9:
                s.set(x, 8, z, "minecraft:smooth_quartz")
    # Rosácea na abside (norte) e na fachada (sul).
    for (zw, cyw, rr) in [(1, 8, 3.2), (25, 8, 2.6)]:
        for x in range(1, 16):
            for y in range(1, 14):
                dd = math.hypot(x - cx, y - cyw)
                if dd <= rr:
                    st = "minecraft:yellow_stained_glass"
                    if dd < 0.6:
                        st = "minecraft:glowstone"
                    elif abs(dd - rr * 0.55) < 0.45:
                        st = "minecraft:orange_stained_glass"
                    s.set(x, y, zw, st)
                elif dd <= rr + 0.8:
                    s.set(x, y, zw, "minecraft:chiseled_quartz_block")
    # Portal da fachada.
    for x in range(cx - 2, cx + 3):
        for y in range(1, 5):
            s.set(x, y, 25, "minecraft:air")
    s.set(cx - 2, 5, 25, stairs("minecraft:quartz_stairs", "west", "top"))
    s.set(cx + 2, 5, 25, stairs("minecraft:quartz_stairs", "east", "top"))
    for x in range(cx - 1, cx + 2):
        s.set(x, 5, 25, "minecraft:chiseled_quartz_block")
    for z in (25, 26):
        for x in range(cx - 2, cx + 3):
            s.set(x, 0, z, "minecraft:quartz_bricks")
    # Presbitério (z 2..5) elevado com degraus.
    for x in range(2, 15):
        for z in range(2, 6):
            s.set(x, 1, z, "minecraft:smooth_quartz" if (x + z) % 2 else brick("light"))
        s.set(x, 1, 6, stairs("minecraft:quartz_stairs", "north"))
    for x in (2, 14):
        s.set(x, 1, 6, "minecraft:quartz_pillar[axis=y]")
    # Estrado do altar (3x3 com degraus).
    for x in range(cx - 2, cx + 3):
        for z in range(AZ - 2, AZ + 3):
            dx, dz = x - cx, z - AZ
            m = max(abs(dx), abs(dz))
            if m <= 1:
                s.set(x, 1, z, brick("light") if m == 0 else "minecraft:smooth_quartz")
            elif abs(dx) == 2 and abs(dz) == 2:
                s.set(x, 1, z, slab("minecraft:smooth_quartz_slab"))
            elif abs(dz) == 2:
                s.set(x, 1, z, stairs("minecraft:quartz_stairs", "north" if dz > 0 else "south"))
            else:
                s.set(x, 1, z, stairs("minecraft:quartz_stairs", "west" if dx > 0 else "east"))
    s.set(cx, 2, AZ, ALTAR % "light")
    # Candelabros nas quinas do estrado.
    for (dx, dz) in [(-2, -2), (2, -2), (-2, 2), (2, 2)]:
        s.set(cx + dx, 2, AZ + dz, "minecraft:end_rod[facing=up]")
    # Baú no presbitério, ladeado por velas.
    chest(s, cx, 2, 3, "light", "south")
    s.set(cx - 1, 2, 3, "minecraft:white_candle[candles=4,lit=true,waterlogged=false]")
    s.set(cx + 1, 2, 3, "minecraft:yellow_candle[candles=3,lit=true,waterlogged=false]")
    # Restos do teto: abóbada sobre o presbitério e vigas sobre a nave.
    for x in range(1, 16):
        for z in range(1, 9):
            s.set(x, 12, z, slab("minecraft:smooth_quartz_slab") if 2 <= x <= 14 else "minecraft:quartz_bricks")
    for zb in (12, 18):
        for x in range(2, 15):
            if rnd.random() < 0.8 or abs(x - cx) <= 1:
                s.set(x, 10, zb, "minecraft:quartz_pillar[axis=x]")
        s.set(cx, 9, zb, "minecraft:chain[axis=y,waterlogged=false]")
        s.set(cx, 8, zb, "minecraft:glowstone")
        for d_, f in [((1, 0), "east"), ((-1, 0), "west"), ((0, 1), "south"), ((0, -1), "north")]:
            s.set(cx + d_[0], 8, zb + d_[1], f"minecraft:end_rod[facing={f}]")
    # Lustre pendurado na abóbada.
    for y in (11, 10):
        s.set(cx, y, 6, "minecraft:chain[axis=y,waterlogged=false]")
    s.set(cx, 9, 6, "minecraft:glowstone")
    # Entulho e trepadeiras (ruína).
    for _ in range(18):
        x, z = rnd.choice([2, 3, 4, 12, 13, 14]), rnd.randint(13, 24)
        if s.get(x, 1, z) == "minecraft:air":
            s.set(x, 1, z, rnd.choice([slab("minecraft:smooth_quartz_slab"), "minecraft:chiseled_quartz_block",
                                       stairs("minecraft:quartz_stairs", rnd.choice(["north", "south", "east", "west"])),
                                       "minecraft:quartz_bricks"]))
    for z in range(2, 25):
        for y in range(1, 9):
            if rnd.random() < 0.12 and s.get(0, y, z) is None and s.get(1, y, z) not in (None, "minecraft:air"):
                s.set(0, y, z, "minecraft:vine[east=true,north=false,south=false,up=false,west=false]")
            if rnd.random() < 0.12 and s.get(16, y, z) is None and s.get(15, y, z) not in (None, "minecraft:air"):
                s.set(16, y, z, "minecraft:vine[east=false,north=false,south=false,up=false,west=true]")
    for _ in range(40):
        x, z = rnd.choice([1, 15]), rnd.randint(13, 24)
        for y in range(wall_top(x, z), 4, -1):
            if rnd.random() < 0.5:
                s.set(x, y, z, "minecraft:air")
            else:
                break
    fix_connections(s)
    return s


# -------------------------------------------------------------------------------------------------
#  Santuário das Trevas (sombra)
# -------------------------------------------------------------------------------------------------
def build_shadow():
    rnd = random.Random("shrine_shadow")
    s = Structure(21, 12, 21)
    c = 10

    def octo(x, z, r):
        dx, dz = abs(x - c), abs(z - c)
        return dx <= r and dz <= r and dx + dz <= r + r * 0.42

    inner_r, outer_r = 8, 9
    # Piso.
    for x in range(21):
        for z in range(21):
            if octo(x, z, 10):
                if not octo(x, z, outer_r):
                    st = rnd.choice(["minecraft:soul_soil", "minecraft:blackstone", "minecraft:mud"])
                else:
                    dd = math.hypot(x - c, z - c)
                    if 3.5 <= dd <= 4.4:
                        st = "minecraft:crying_obsidian" if (x + z) % 3 == 0 else brick("shadow")
                    elif dd > 6.2 and rnd.random() < 0.55:
                        st = "minecraft:sculk"
                    elif rnd.random() < 0.15:
                        st = "minecraft:soul_soil"
                    else:
                        st = "minecraft:polished_blackstone_bricks" if rnd.random() < 0.7 else "minecraft:cracked_polished_blackstone_bricks"
                s.set(x, 0, z, st)
    air_fill(s, [(x, y, z) for x in range(21) for y in range(1, 7) for z in range(21) if octo(x, z, inner_r)])
    # Paredes octogonais.
    for y in range(1, 7):
        for x in range(21):
            for z in range(21):
                if octo(x, z, outer_r) and not octo(x, z, inner_r):
                    if y in (2, 5):
                        st = brick("shadow")
                    else:
                        r = rnd.random()
                        st = "minecraft:blackstone" if r < 0.4 else "minecraft:polished_blackstone_bricks" if r < 0.85 else \
                            "minecraft:cracked_polished_blackstone_bricks" if r < 0.97 else "minecraft:gilded_blackstone"
                    s.set(x, y, z, st)
    # Fendas de vidro escurecido.
    for (x, z) in [(c, 1), (1, c), (19, c)]:
        for y in (3, 4):
            s.set(x, y, z, "minecraft:tinted_glass")
    # Teto com óculo de vidro escurecido.
    for x in range(21):
        for z in range(21):
            if octo(x, z, outer_r):
                dd = math.hypot(x - c, z - c)
                if dd <= 1.5:
                    s.set(x, 7, z, "minecraft:tinted_glass")
                elif dd <= 2.6:
                    s.set(x, 7, z, "minecraft:chiseled_polished_blackstone")
                else:
                    s.set(x, 7, z, "minecraft:polished_blackstone_bricks" if rnd.random() < 0.6 else "minecraft:blackstone")
                if octo(x, z, outer_r) and not octo(x, z, inner_r) and (x + z) % 2 == 0:
                    s.set(x, 8, z, "minecraft:polished_blackstone_brick_wall")
            if octo(x, z, 5) and not octo(x, z, 4):
                s.set(x, 8, z, slab("minecraft:blackstone_slab"))
    # Veios de sculk subindo pelas paredes internas.
    for x in range(21):
        for z in range(21):
            if not octo(x, z, inner_r):
                continue
            for d_, (dx, dz) in [("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))]:
                nb = s.get(x + dx, 1, z + dz)
                if nb and nb != "minecraft:air" and not octo(x + dx, z + dz, inner_r) and rnd.random() < 0.35:
                    h = rnd.randint(1, 3)
                    for y in range(1, 1 + h):
                        if s.get(x, y, z) == "minecraft:air":
                            props = {k: "false" for k in ("down", "up", "north", "south", "east", "west", "waterlogged")}
                            props[d_] = "true"
                            s.set(x, y, z, "minecraft:sculk_vein[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]")
                    break
    # Estrado do altar.
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            dx, dz = x - c, z - c
            m = max(abs(dx), abs(dz))
            if m <= 1:
                s.set(x, 1, z, brick("shadow") if m == 0 else "minecraft:polished_blackstone")
            elif abs(dx) == 2 and abs(dz) == 2:
                s.set(x, 1, z, "minecraft:crying_obsidian")
                s.set(x, 2, z, "minecraft:purple_candle[candles=4,lit=true,waterlogged=false]")
            elif abs(dz) == 2:
                s.set(x, 1, z, stairs("minecraft:polished_blackstone_stairs", "north" if dz > 0 else "south"))
            else:
                s.set(x, 1, z, stairs("minecraft:polished_blackstone_stairs", "west" if dx > 0 else "east"))
    s.set(c, 2, c, ALTAR % "shadow")
    # Braseiros de alma e catalisadores de sculk (florescem quando os mobs da prova morrem).
    for (x, z) in [(c - 5, c - 5), (c + 5, c - 5), (c - 5, c + 5), (c + 5, c + 5)]:
        s.set(x, 0, z, "minecraft:soul_soil")
        s.set(x, 1, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    for (x, z) in [(c - 6, c), (c + 6, c)]:
        s.set(x, 1, z, "minecraft:sculk_catalyst[bloom=false]")
    for (x, z) in [(3, 6), (17, 14)]:
        s.set(x, 1, z, "minecraft:sculk_sensor[power=0,sculk_sensor_phase=inactive,waterlogged=false]")
    # Pilares de osso e lanternas de alma penduradas.
    for (x, z) in [(c - 3, 3), (c + 3, 3), (c - 3, 17), (c + 3, 17)]:
        for y in range(1, 7):
            s.set(x, y, z, "minecraft:bone_block[axis=y]" if y % 3 else "minecraft:polished_blackstone_bricks")
    for (x, z) in [(c - 4, c), (c + 4, c), (c, c - 4), (c, c + 4)]:
        s.set(x, 6, z, "minecraft:chain[axis=y,waterlogged=false]")
        s.set(x, 5, z, "minecraft:soul_lantern[hanging=true,waterlogged=false]")
    # Baú (norte) com velas pretas.
    chest(s, c, 1, 3, "shadow", "south")
    s.set(c - 1, 1, 3, "minecraft:polished_blackstone_bricks")
    s.set(c + 1, 1, 3, "minecraft:polished_blackstone_bricks")
    s.set(c - 1, 2, 3, "minecraft:black_candle[candles=3,lit=true,waterlogged=false]")
    s.set(c + 1, 2, 3, "minecraft:black_candle[candles=2,lit=true,waterlogged=false]")
    # Entrada sul com tochas de alma.
    for z in range(17, 21):
        for x in range(c - 1, c + 2):
            for y in range(1, 4):
                if s.get(x, y, z) not in (None,) and octo(x, z, outer_r):
                    s.set(x, y, z, "minecraft:air")
    for x in range(c - 1, c + 2):
        s.set(x, 4, 19, "minecraft:chiseled_polished_blackstone" if x == c else "minecraft:polished_blackstone_bricks")
    for x in (c - 2, c + 2):
        s.set(x, 2, 20, "minecraft:soul_wall_torch[facing=south]")
    fix_connections(s)
    return s


STRUCTURES = {
    "fire": build_fire, "water": build_water, "earth": build_earth,
    "wind": build_wind, "light": build_light, "shadow": build_shadow,
}

CHARS = {
    "air": " ", "water": "~", "lava": "L", "shrine_altar": "A", "chest": "C", "elemental_brick": "E",
    "lantern": "*", "campfire": "^", "glass": "g", "pane": "|", "bars": "#", "stairs": "/", "slab": "_",
    "wall": "o", "sea_lantern": "*", "glowstone": "*", "bell": "B", "magma": "m", "candle": "i",
}


def char_of(state):
    if state is None:
        return "."
    n = base_name(state).split(":")[1]
    for k in ("shrine_altar", "chest", "elemental_brick", "sea_lantern", "glowstone", "campfire", "lantern", "pane",
              "bars", "stairs", "slab", "wall", "glass", "bell", "magma", "candle", "water", "lava"):
        if k in n:
            return CHARS[k]
    if n == "air":
        return " "
    return "X"


def preview(name, s, layers=None):
    sx, sy, sz = s.size
    print(f"===== {name} {s.size} =====")
    for y in (layers if layers is not None else range(sy)):
        print(f"-- y={y}")
        for z in range(sz):
            print("".join(char_of(s.get(x, y, z)) for x in range(sx)))


def gen_structures(show):
    for el, fn in STRUCTURES.items():
        s = fn()
        n = s.save(os.path.join(D, f"structure/shrine_{el}.nbt"), keep_air=True)
        print(f"shrine_{el}: {n} blocos")
        if show:
            preview("shrine_" + el, s)
    if WIND_PARKOUR:
        print("parkour do vento — vãos entre plataformas:", WIND_PARKOUR)


if __name__ == "__main__":
    gen_textures()
    gen_models()
    gen_loot()
    gen_worldgen()
    gen_structures("--preview" in sys.argv)
    print("structures_a: assets gerados.")
