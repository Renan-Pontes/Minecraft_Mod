# -*- coding: utf-8 -*-
"""release 1.0 do modulo "character": os DOIS altares e as Transformacoes mais bonitas.

Uso (na raiz do projeto):  py -I tools/gen/character_release.py

Gera (so arquivos novos deste modulo; NAO roda o character.py nem o icons.py):
- Altar do Recomeco (new_beginnings_altar): texturas, modelo, blockstate, item, loot, receita e a tag de picareta
  (junto com o Altar da Reencarnacao);
- as 11 particulas de assinatura das racas (textures/particle/race_mote_<raca>.png + particles/race_mote_<raca>.json);
- os 2 santuarios raros (renewal_shrine, reincarnation_shrine): .nbt, structure, structure_set, template_pool e as tags
  de bioma;
- as traducoes novas: JUNTA as chaves de extra_lang() nos pt_br/en_us do modulo (o character.py tambem chama
  extra_lang() quando regrava as traducoes, entao nada se perde).
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
MERGE = os.path.join(ROOT, "src/main/merge/character")
LANG = os.path.join(ROOT, "src/main/lang/character")
NS = "isekai_mahou"


def ns(x):
    return NS + ":" + x


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def region(c, x0, y0, w, h, base, seed, amount=0.08):
    rnd = random.Random(seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            f = (rnd.random() - 0.5) * 2 * amount
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))


# =====================================================================================
#  Altar do Recomeco: pedra clara, friso dourado, runas verdes e um broto de luz
# =====================================================================================
def altar_textures():
    rnd = random.Random(77)
    stone, stone2 = hex_rgba("d9d4c8"), hex_rgba("b3ad9f")
    gold, rune, sprout = hex_rgba("d8b25a"), hex_rgba("5fcf86"), hex_rgba("b6ffcf")
    out = {}

    side = Canvas(16, 16)            # base: blocos claros com friso dourado
    for y in range(16):
        for x in range(16):
            off = 0 if (y // 4) % 2 == 0 else 4
            mortar = y % 4 == 3 or (x + off) % 8 == 7
            base = stone2 if mortar else stone
            f = (rnd.random() - 0.5) * 0.1
            side.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))
    side.rect(0, 0, 15, 0, gold)
    side.rect(0, 1, 15, 1, darken(gold, 0.35))
    for x in (3, 11):                # musgo subindo
        side.set(x, 15, rune)
        side.set(x + 1, 14, darken(rune, 0.2))
    out["new_beginnings_altar_base"] = side

    top = Canvas(16, 16)             # topo da base: lajotas claras
    for y in range(16):
        for x in range(16):
            mortar = x % 8 == 0 or y % 8 == 0
            base = stone2 if mortar else lighten(stone, 0.05)
            f = (rnd.random() - 0.5) * 0.08
            top.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))
    out["new_beginnings_altar_base_top"] = top

    bottom = Canvas(16, 16)
    region(bottom, 0, 0, 16, 16, stone2, 78, 0.07)
    out["new_beginnings_altar_bottom"] = bottom

    pillar = Canvas(16, 16)          # coluna: pedra clara com espiral verde (a vida voltando)
    region(pillar, 0, 0, 16, 16, hex_rgba("c9c3b4"), 79, 0.07)
    for x in (2, 13):
        pillar.rect(x, 0, x, 15, darken(gold, 0.2))
    for y in range(16):
        x = int(7.5 + math.sin(y * 0.8) * 2.6)
        pillar.set(x, y, rune)
        pillar.set(x + 1, y, darken(rune, 0.25))
        if y % 5 == 2:
            pillar.set(x + 2, y - 1 if y else y, sprout)
    out["new_beginnings_altar_pillar"] = pillar

    plate = Canvas(16, 16)           # tampa: marmore com circulo de ouro e a espiral do recomeco
    region(plate, 0, 0, 16, 16, hex_rgba("e6e1d6"), 80, 0.06)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.2 <= d <= 7.2:
                plate.set(x, y, gold)
            elif d < 2.0:
                plate.set(x, y, sprout if d < 1.1 else rune)
    for k in range(26):              # espiral
        t = k / 25.0
        ang = t * math.pi * 3.2
        r = 2.2 + t * 3.2
        plate.set(int(8 + math.cos(ang) * r), int(8 + math.sin(ang) * r), mix(rune, sprout, t))
    out["new_beginnings_altar_top"] = plate

    rim = Canvas(16, 16)             # lateral da tampa: faixa dourada com gemas verdes
    region(rim, 0, 0, 16, 16, hex_rgba("e0dbcf"), 81, 0.06)
    rim.rect(0, 13, 15, 15, darken(gold, 0.1))
    rim.rect(0, 13, 15, 13, lighten(gold, 0.25))
    for x in (3, 8, 13):
        rim.set(x, 14, sprout)
    out["new_beginnings_altar_rim"] = rim

    crystal = Canvas(16, 16)         # o broto de luz
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            crystal.set(x, y, mix(lighten(sprout, 0.4), darken(rune, 0.25), t))
    crystal.line(2, 13, 13, 2, lighten(sprout, 0.7))
    out["new_beginnings_altar_crystal"] = crystal
    return out


def altar_assets():
    for name, canvas in altar_textures().items():
        canvas.save(os.path.join(A, "textures/block", name + ".png"))
    # O modelo e o mesmo do Altar da Reencarnacao (mesma forma), so com as texturas deste
    with open(os.path.join(A, "models/block/reincarnation_altar.json"), encoding="utf-8") as f:
        model = json.loads(f.read().replace("reincarnation_altar", "new_beginnings_altar"))
    write_json(os.path.join(A, "models/block/new_beginnings_altar.json"), model)
    write_json(os.path.join(A, "models/item/new_beginnings_altar.json"), {"parent": ns("block/new_beginnings_altar")})
    write_json(os.path.join(A, "blockstates/new_beginnings_altar.json"),
               {"variants": {"": {"model": ns("block/new_beginnings_altar")}}})
    write_json(os.path.join(D, "loot_table/blocks/new_beginnings_altar.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": ns("new_beginnings_altar")}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": ns("blocks/new_beginnings_altar")})
    write_json(os.path.join(D, "recipe/new_beginnings_altar.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" G ", "MAM", "CCC"],
        "key": {"G": {"item": "minecraft:golden_apple"}, "M": {"item": ns("mana_crystal")},
                "A": {"item": "minecraft:amethyst_block"}, "C": {"item": "minecraft:calcite"}},
        "result": {"id": ns("new_beginnings_altar"), "count": 1}})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"replace": False, "values": [ns("reincarnation_altar"), ns("new_beginnings_altar")]})


# =====================================================================================
#  Particulas de assinatura das racas (16x16, desenhadas a mao)
# =====================================================================================
def _pal(**kw):
    return {k: hex_rgba(v) if isinstance(v, str) else v for k, v in kw.items()}


MOTES = {
    # Humano: estrela dourada de 4 pontas
    "human": (["................", ".......w........", ".......w........", "......ywy.......", "......ywy.......",
               ".....yywyy......", "..yyywwwwwyyy...", ".wwwwwwWwwwwww..", "..yyywwwwwyyy...", ".....yywyy......",
               "......ywy.......", "......ywy.......", ".......w........", ".......w........", "................",
               "................"], _pal(y="e8a83a", w="ffe58a", W="ffffff")),
    # Elfo: folha em forma de lua crescente
    "elf": (["................", "........gg......", "......ggGg......", ".....gGGg.......", "....gGLg........",
             "...gGLg.........", "...gGLg.........", "...gGLg.........", "...gGLLg........", "....gGLLg.......",
             "....gGGLLgg.....", ".....gGGLLLgg...", "......ggGGGGg...", "........gggg....", "................",
             "................"], _pal(g="3f9a63", G="7bd88f", L="d6ffe0")),
    # Anao: runa angular em brasa
    "dwarf": (["................", "................", "....rrrrrrrr....", "....rOOOOOOr....", "....rOrrrrrr....",
               "....rOr.........", "....rOrrrrr.....", "....rOYYYOr.....", "....rOrrrrr.....", "....rOr.........",
               "....rOrrrrrr....", "....rOOOOOOr....", "....rrrrrrrr....", "................", "................",
               "................"], _pal(r="8a2c0a", O="ff8a2a", Y="ffe07a")),
    # Meio-Orc: presa manchada de sangue
    "half_orc": (["................", "..........bb....", ".........bWb....", "........bWWb....", ".......bWWb.....",
                  "......bWWb......", ".....bWWWb......", "....bWWWb.......", "....bWWRb.......", "...bWRRRb.......",
                  "...bRRRb........", "...bRRb.........", "....bb..........", "................", "................",
                  "................"], _pal(b="4a0a0c", W="efe6d2", R="c81e28")),
    # Demonio: chama negra com nucleo vermelho
    "demon": (["................", ".......k........", "......kk........", "......kKk.......", ".....kKKk..k....",
               ".....kKRKkkk....", "....kKRRKKk.....", "....kKROORKk....", "...kKRROORKk....", "...kKROYORKk....",
               "...kKROYYOKk....", "....kKOYYOKk....", "....kKKOOKk.....", ".....kkKKk......", ".......kk.......",
               "................"], _pal(k="1c0a14", K="4a1020", R="c81e28", O="ff5a2a", Y="ffd27a")),
    # Draconico: escama (branca: o jogo tinge com a cor da ancestralidade)
    "dragonborn": (["................", ".......dd.......", "......dWWd......", ".....dWWWWd.....", "....dWWwWWWd....",
                    "...dWWwwwWWWd...", "...dWWwwwWWWd...", "...dWWWwWWWgd...", "...dWWWWWWggd...", "....dWWWWggd....",
                    "....dWWWgggd....", ".....dWgggd.....", "......dggd......", ".......dd.......", "................",
                    "................"], _pal(d="6a6a6a", W="f2f2f2", w="ffffff", g="b4b4b4")),
    # Bestial: tres riscos de garra (montados por _claw_rows)
    "beastfolk": (None, _pal(o="8a4a10", Y="ffb040", y="ffe6a0")),
    # Aracnideo: pedaco de teia
    "arachnid": (["................", ".w......w......w", "..w.....w.....w.", "...wpppwwwpppw..", "...pw...w...wp..",
                  "...p.w..w..w.p..", "...p..wpwpw..p..", ".wwwwwwpWpwwwwww", "...p..wpwpw..p..", "...p.w..w..w.p..",
                  "...pw...w...wp..", "...wpppwwwpppw..", "..w.....w.....w.", ".w......w......w", "................",
                  "................"], _pal(w="f2f0ff", p="b48af0", W="ffffff")),
    # Fantasma: alma com dois olhos
    "phantom": (["................", ".....cccccc.....", "....cCCCCCCc....", "...cCWWWWWWCc...", "...cCWWWWWWCc...",
                 "...cCWkWWkWCc...", "...cCWkWWkWCc...", "...cCWWWWWWCc...", "...cCWWkkWWCc...", "...cCWWWWWWCc...",
                 "....cCWWWWCc....", "....cCWcCWCc....", ".....cCc.cCc....", "......c...c.....", "................",
                 "................"], _pal(c="4a8aa8", C="9fd8e8", W="eaffff", k="1a3040")),
    # Anjo: pena branca
    "angel": (["................", "...........gg...", "..........gWWg..", ".........gWWWg..", "........gWWYWg..",
               ".......gWWYWg...", "......gWWYWWg...", ".....gWWYWWg....", "....gWWYWWg.....", "....gWYWWg......",
               "...gWYWWg.......", "...gYWgg........", "..gYgg..........", "..gg............", "................",
               "................"], _pal(g="c9b06a", W="ffffff", Y="ffe9a8")),
    # Slime: bolha de geleia
    "slime": (["................", "................", ".....gggggg.....", "....gGGGGGGg....", "...gGWWGGGGGg...",
               "..gGWWGGGGGGGg..", "..gGWGGGGGGGGg..", "..gGGGGGGGGGGg..", "..gGGGGGGGGGGg..", "..gGGGGGGGGLGg..",
               "..gGGGGGGGLLGg..", "...gGGGGGLLGg...", "....gGGGGGGg....", ".....gggggg.....", "................",
               "................"], _pal(g="3f8a2a", G="8fe05a", W="f0ffd8", L="5fb83a")),
}


def _claw_rows():
    """Tres talhos paralelos em diagonal, mais grossos no meio e afinando nas pontas."""
    grid = [["."] * 16 for _ in range(16)]
    for base in (1, 5, 9):
        for r in range(1, 14):
            x = base + r // 3
            grid[r][x] = "o"
            if 2 <= r <= 12:
                grid[r][x + 1] = "Y"
            if 4 <= r <= 10:
                grid[r][x + 2] = "y"
    return ["".join(row) for row in grid]


def mote_assets():
    for rid, (rows, palette) in MOTES.items():
        if rows is None:
            rows = _claw_rows()
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), rid
        c = Canvas(16, 16)
        c.from_rows(rows, palette)
        c.save(os.path.join(A, "textures/particle", "race_mote_%s.png" % rid))
        write_json(os.path.join(A, "particles", "race_mote_%s.json" % rid), {"textures": [ns("race_mote_" + rid)]})


# =====================================================================================
#  Santuarios raros (um para cada altar)
# =====================================================================================
EXCLUSIONS = [{"other_set": ns("guild_city"), "chunk_count": 10}, {"other_set": ns("capital_city"), "chunk_count": 13},
              {"other_set": ns("capital_sahra"), "chunk_count": 13}, {"other_set": ns("capital_nordheim"), "chunk_count": 13}]


def overworld_structure(name, biomes_list, spacing, separation, salt):
    write_json(os.path.join(D, "worldgen/structure", name + ".json"), {
        "type": "minecraft:jigsaw", "biomes": "#%s:has_structure/%s" % (NS, name), "step": "surface_structures",
        "spawn_overrides": {}, "terrain_adaptation": "beard_thin", "start_pool": "%s:%s/start" % (NS, name), "size": 1,
        "start_height": {"absolute": -1}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(D, "worldgen/template_pool", name, "start.json"), {
        "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element", "location": ns(name), "processors": "minecraft:empty",
            "projection": "rigid"}}]})
    write_json(os.path.join(D, "worldgen/structure_set", name + ".json"), {
        "structures": [{"structure": ns(name), "weight": 1}],
        "placement": {"type": ns("random_spread_multi"), "spacing": spacing, "separation": separation, "salt": salt,
                      "exclusion_zones": EXCLUSIONS}})
    write_json(os.path.join(MERGE, "data", NS, "tags/worldgen/biome/has_structure", name + ".json"),
               {"replace": False, "values": biomes_list})


def disk(s, cx, cz, r, y, state):
    for x in range(s.size[0]):
        for z in range(s.size[2]):
            if math.hypot(x - cx, z - cz) <= r + 0.3:
                s.set(x, y, z, state)


def renewal_shrine():
    """Santuario do Recomeco: clareira de pedra clara e musgo, quatro colunas com copas de cerejeira e o altar no meio."""
    s = Structure(13, 9, 13)
    c = 6
    rnd = random.Random(91)
    for y in range(1, 9):
        disk(s, c, c, 6, y, "minecraft:air")
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - c, z - c)
            if d <= 6.3:
                if d <= 2.4:
                    s.set(x, 0, z, "minecraft:smooth_quartz")
                elif d <= 4.4:
                    s.set(x, 0, z, "minecraft:calcite" if rnd.random() < 0.6 else "minecraft:moss_block")
                else:
                    s.set(x, 0, z, "minecraft:moss_block" if rnd.random() < 0.55 else "minecraft:grass_block[snowy=false]")
    # Degrau central e o altar
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 1, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]")
    s.set(c, 1, c, "minecraft:chiseled_quartz_block")
    s.set(c, 2, c, ns("new_beginnings_altar"))
    # Quatro colunas com copa
    for (px, pz) in ((c - 4, c - 4), (c + 4, c - 4), (c - 4, c + 4), (c + 4, c + 4)):
        s.fill(px, 1, pz, px, 4, pz, "minecraft:quartz_pillar[axis=y]")
        s.set(px, 5, pz, "minecraft:cherry_log[axis=y]")
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                for dy in (5, 6):
                    if (dx, dz) == (0, 0) and dy == 5:
                        continue
                    if dy == 6 and abs(dx) + abs(dz) == 2 and rnd.random() < 0.5:
                        continue
                    s.set(px + dx, dy, pz + dz, "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]")
        s.set(px, 7, pz, "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]")
    # Lanternas e flores em volta do degrau
    for (lx, lz) in ((c - 2, c), (c + 2, c), (c, c - 2), (c, c + 2)):
        s.set(lx, 1, lz, "minecraft:lantern[hanging=false,waterlogged=false]")
    for k in range(10):
        a = 2 * math.pi * k / 10 + 0.3
        x, z = round(c + math.cos(a) * 5.2), round(c + math.sin(a) * 5.2)
        if s.get(x, 0, z) in ("minecraft:moss_block", "minecraft:grass_block[snowy=false]") and s.get(x, 1, z) in (None, "minecraft:air"):
            s.set(x, 1, z, "minecraft:flowering_azalea" if k % 3 == 0 else "minecraft:pink_petals[facing=north,flower_amount=3]")
    s.save(os.path.join(D, "structure", "renewal_shrine.nbt"), keep_air=True)


def reincarnation_shrine():
    """Santuario da Reencarnacao: circulo de ardosia e pedra-negra, quatro obeliscos de obsidiana com lanternas de alma."""
    s = Structure(13, 9, 13)
    c = 6
    rnd = random.Random(92)
    for y in range(1, 9):
        disk(s, c, c, 6, y, "minecraft:air")
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - c, z - c)
            if d <= 6.3:
                if d <= 2.4:
                    s.set(x, 0, z, "minecraft:polished_blackstone")
                elif d <= 4.6:
                    s.set(x, 0, z, "minecraft:polished_deepslate" if rnd.random() < 0.7 else "minecraft:cobbled_deepslate")
                else:
                    s.set(x, 0, z, "minecraft:cobbled_deepslate" if rnd.random() < 0.5 else "minecraft:coarse_dirt")
    # Anel de runas (chorona) em volta do centro
    for k in range(8):
        a = 2 * math.pi * k / 8
        s.set(round(c + math.cos(a) * 3.5), 0, round(c + math.sin(a) * 3.5), "minecraft:crying_obsidian")
    # Degrau central e o altar
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 1, z, "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]")
    s.set(c, 1, c, "minecraft:chiseled_polished_blackstone")
    s.set(c, 2, c, ns("reincarnation_altar"))
    # Quatro obeliscos
    for i, (px, pz) in enumerate(((c - 4, c - 4), (c + 4, c - 4), (c - 4, c + 4), (c + 4, c + 4))):
        h = 5 if i % 2 == 0 else 4
        s.fill(px, 1, pz, px, h, pz, "minecraft:obsidian")
        s.set(px, 2, pz, "minecraft:crying_obsidian")
        s.set(px, h + 1, pz, "minecraft:polished_blackstone_wall[east=none,north=none,south=none,up=true,west=none,waterlogged=false]")
        s.set(px, h + 2, pz, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    # Velas de alma no chao (fogueiras de alma: luz e fumaca, sem espalhar fogo)
    for (lx, lz) in ((c - 3, c), (c + 3, c)):
        s.set(lx, 1, lz, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    for (lx, lz) in ((c, c - 3), (c, c + 3)):
        s.set(lx, 1, lz, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    s.save(os.path.join(D, "structure", "reincarnation_shrine.nbt"), keep_air=True)


def structures():
    renewal_shrine()
    reincarnation_shrine()
    overworld_structure("renewal_shrine", ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                                           "minecraft:cherry_grove", "minecraft:flower_forest", "minecraft:birch_forest",
                                           "minecraft:forest"], 44, 18, 771100431)
    overworld_structure("reincarnation_shrine", ["minecraft:dark_forest", "minecraft:taiga", "minecraft:old_growth_pine_taiga",
                                                 "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga",
                                                 "minecraft:windswept_hills", "minecraft:swamp", "minecraft:savanna",
                                                 "minecraft:badlands", "minecraft:desert"], 56, 22, 771100432)


# =====================================================================================
#  Traducoes novas (chave: (pt_br, en_us)) — tambem substituem as antigas que falavam do custo/receita de antes
# =====================================================================================
TEXT = {
    "block.isekai_mahou.new_beginnings_altar": ("Altar do Recomeço", "Altar of New Beginnings"),
    "block.isekai_mahou.new_beginnings_altar.tooltip": (
        "Devolve todos os pontos de talento e desfaz a especialização da sua classe.",
        "Refunds every talent point and undoes your class specialization."),
    "block.isekai_mahou.new_beginnings_altar.tooltip2": ("Custo: %s Cristais de Mana por recomeço.", "Cost: %s Mana Crystals per reset."),
    "block.isekai_mahou.reincarnation_altar.tooltip": (
        "Renasça com outra raça ou outra classe: o nível fica, o que era só da antiga se perde.",
        "Be reborn as another race or class: your level stays, what belonged only to the old one is lost."),
    "block.isekai_mahou.reincarnation_altar.tooltip2": (
        "Custo: %s (raça) ou %s (classe) Cristais de Mana + 1 Núcleo de Guardião.",
        "Cost: %s (race) or %s (class) Mana Crystals + 1 Guardian Core."),
    "map.isekai_mahou.structure.renewal_shrine": ("Santuário do Recomeço", "Shrine of New Beginnings"),
    "map.isekai_mahou.structure.reincarnation_shrine": ("Santuário da Reencarnação", "Shrine of Reincarnation"),
    "hint.isekai_mahou.character.altars": (
        "Errou nos talentos? Procure um Altar do Recomeço. Raça e classe só mudam no raro Altar da Reencarnação.",
        "Regret your talents? Find an Altar of New Beginnings. Race and class only change at the rare Altar of Reincarnation."),

    "commands.isekai_mahou.character.renew": ("Talentos devolvidos para %s jogador(es).", "Talents refunded for %s player(s)."),
    "commands.isekai_mahou.character.class": ("Classe trocada para %s jogador(es).", "Class changed for %s player(s)."),
    "commands.isekai_mahou.character.race": ("Raça de %s jogador(es) trocada para %s.", "Race of %s player(s) changed to %s."),

    "message.isekai_mahou.character.cant_pay": ("Falta o custo: %s.", "You can't pay the cost: %s."),
    "message.isekai_mahou.character.cost_crystals": ("%s× %s", "%s× %s"),
    "message.isekai_mahou.character.cost_with_core": (
        "%s + %s Núcleo de Guardião (de qualquer chefe elemental)", "%s + %s Guardian Core (from any elemental boss)"),
    "message.isekai_mahou.character.renewal_far": ("Você se afastou do Altar do Recomeço.", "You walked away from the Altar of New Beginnings."),
    "message.isekai_mahou.character.renewal_nothing": (
        "Você não tem pontos de talento gastos nem especialização para desfazer.",
        "You have no spent talent points and no specialization to undo."),
    "message.isekai_mahou.character.renewed": ("Recomeço! Seus talentos voltaram.", "A new beginning! Your talents are back."),
    "message.isekai_mahou.character.renewed_body": (
        "Todos os pontos de talento voltaram e a especialização foi desfeita. Abra o menu (%s) e monte de novo.",
        "Every talent point is back and your specialization was undone. Open the menu (%s) and build again."),
    "message.isekai_mahou.character.class_invalid": ("Essa classe não pode ser escolhida no altar.", "That class can't be chosen at the altar."),
    "message.isekai_mahou.character.same_class": ("Você já é %s.", "You already are a %s."),
    "message.isekai_mahou.character.class_story_locked": (
        "%s é um destino, não uma escolha: o altar não consegue desfazê-lo.", "%s is a fate, not a choice: the altar can't undo it."),
    "message.isekai_mahou.character.class_in_form": ("Termine a Transformação antes de renascer.", "Finish your Transformation before being reborn."),
    "message.isekai_mahou.character.class_reborn": ("Você renasceu como %s!", "You were reborn as a %s!"),
    "message.isekai_mahou.character.class_reborn_body": (
        "A vida de %1$s ficou para trás: talentos, glifos de origem (%3$s esquecidos), graus e itens da classe antiga se foram. "
        "O nível, o Círculo e os elementos continuam. Bem-vindo(a), %2$s.",
        "Your life as a %1$s is behind you: talents, origin glyphs (%3$s forgotten), ranks and class items are gone. "
        "Your level, Circle and elements remain. Welcome, %2$s."),

    "screen.isekai_mahou.character.altar_back": ("Voltar", "Back"),
    "screen.isekai_mahou.character.renewal_confirm": ("Recomeçar", "Start Anew"),
    "screen.isekai_mahou.character.class_confirm": ("Renascer", "Be Reborn"),
    "screen.isekai_mahou.character.class_confirm_sure": ("Confirmar (sem volta)", "Confirm (no going back)"),
    "screen.isekai_mahou.character.altar_cost_core": ("+ %s Núcleo de Guardião (você tem %s)", "+ %s Guardian Core (you have %s)"),
    "screen.isekai_mahou.character.altar_cost_race": (
        "Custo: %s Cristais de Mana (tem %s) + %s Núcleo de Guardião (tem %s)",
        "Cost: %s Mana Crystals (have %s) + %s Guardian Core (have %s)"),
    "screen.isekai_mahou.character.hub_race": ("Trocar de Raça", "Change Race"),
    "screen.isekai_mahou.character.hub_class": ("Trocar de Classe", "Change Class"),
    "screen.isekai_mahou.character.hub_now": ("Atual: %s", "Current: %s"),
    "screen.isekai_mahou.character.hub_none": ("nenhuma", "none"),
    "screen.isekai_mahou.character.hub_race_desc": (
        "A passiva e as skills raciais trocam junto. Os feitos de raça ficam guardados.",
        "The passive and racial skills change with it. Your race feats are kept."),
    "screen.isekai_mahou.character.hub_class_desc": (
        "Mantém o nível, o Círculo e os elementos. Perde talentos, glifos de origem, graus e itens da classe antiga.",
        "Keeps your level, Circle and elements. Loses the old class's talents, origin glyphs, ranks and items."),
    "screen.isekai_mahou.character.hub_note": (
        "Para só refazer os talentos, procure o Altar do Recomeço.", "To only redo your talents, find the Altar of New Beginnings."),
    "screen.isekai_mahou.character.classes": ("Classes", "Classes"),
    "screen.isekai_mahou.character.class_tag": ("%s · %s", "%s · %s"),
    "screen.isekai_mahou.character.class_keep_title": ("Você mantém", "You keep"),
    "screen.isekai_mahou.character.class_keep": (
        "Nível, Círculo, elementos despertados, glifos comuns, raça e tudo que não é da classe.",
        "Level, Circle, awakened elements, common glyphs, race and everything that isn't class-bound."),
    "screen.isekai_mahou.character.class_lose_title": ("Você perde", "You lose"),
    "screen.isekai_mahou.character.class_lose": (
        "Tudo que era só de %s: talentos e especialização, glifos de origem (e as magias montadas com eles), graus de "
        "ritual e itens de classe no inventário.",
        "Everything that belonged only to the %s: talents and specialization, origin glyphs (and spells built with them), "
        "ritual ranks and class items in your inventory."),
    "screen.isekai_mahou.character.renewal_head": ("Recomeçar os talentos de %s", "Start the %s talents anew"),
    "screen.isekai_mahou.character.renewal_line1": (
        "Todos os pontos de talento gastos voltam para você distribuir de novo.",
        "Every spent talent point comes back for you to spend again."),
    "screen.isekai_mahou.character.renewal_line2": (
        "A especialização é desfeita: você escolhe outra (ou a mesma) no menu.",
        "Your specialization is undone: pick another (or the same) in the menu."),
    "screen.isekai_mahou.character.renewal_line3": (
        "Nível, Círculo, glifos, raça e classe não mudam.", "Level, Circle, glyphs, race and class do not change."),
    "screen.isekai_mahou.character.renewal_other": (
        "Para trocar de raça ou de classe, procure o Altar da Reencarnação.",
        "To change race or class, find the Altar of Reincarnation."),
    "screen.isekai_mahou.character.altar_note": (
        "Renascer com outra raça custa caro: a passiva e as skills raciais mudam junto (os feitos ficam).",
        "Being reborn as another race is expensive: the passive and racial skills change with it (feats are kept)."),
    "screen.isekai_mahou.character.altar_info": (
        "Trocar de raça ou de classe: Altar da Reencarnação (santuários raros; custa muito caro). Refazer talentos: Altar do Recomeço.",
        "Change race or class: Altar of Reincarnation (rare shrines; very expensive). Redo talents: Altar of New Beginnings."),
}


def extra_lang():
    """(pt_br, en_us) das chaves novas — o character.py junta isto quando regrava as traducoes do modulo."""
    return {k: v[0] for k, v in TEXT.items()}, {k: v[1] for k, v in TEXT.items()}


def merge_lang():
    pt, en = extra_lang()
    for name, extra in (("pt_br.json", pt), ("en_us.json", en)):
        path = os.path.join(LANG, name)
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(extra)
        write_json(path, dict(sorted(data.items())))
    return len(pt)


def main():
    altar_assets()
    mote_assets()
    structures()
    n = merge_lang()
    print("character_release: altar, %d particulas, 2 santuarios, %d chaves de traducao" % (len(MOTES), n))


if __name__ == "__main__":
    main()
