"""
Gera os assets e dados do modulo ritual_necro (Ritual do Necromante - Colheita de Almas).
Uso (na raiz do projeto):  py -I tools/gen/ritual_necro.py  [--preview <pasta>]

Saidas:
  assets/isekai_mahou/textures/{item,block}/...    Filacterio (4 estagios de brilho), Foice de Ossos, Altar Ossuario
  assets/isekai_mahou/models/..., blockstates/...  modelos (altar com sobreposicoes emissivas quando montado)
  data/isekai_mahou/necro_rites/rank_<n>.json      ranks do Rito Ossuario
  data/isekai_mahou/recipe, loot_table
  src/main/lang/ritual_necro/{pt_br,en_us}.json
  src/main/merge/ritual_necro/data/minecraft/tags/{block/mineable/pickaxe,item/swords}.json
  src/main/merge/ritual_necro/data/isekai_mahou/tags/entity_type/necro_soulless.json (outros modulos podem somar)
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "ritual_necro")
MERGE = os.path.join(ROOT, "src", "main", "merge", "ritual_necro")

PREVIEW = None
if "--preview" in sys.argv:
    PREVIEW = sys.argv[sys.argv.index("--preview") + 1]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(canvas, kind, name):
    canvas.save(os.path.join(ASSETS, "textures", kind, name + ".png"))
    if PREVIEW:
        big = Canvas(canvas.w * 16, canvas.h * 16, (40, 40, 48, 255))
        for y in range(canvas.h):
            for x in range(canvas.w):
                p = canvas.get(x, y)
                if p[3] > 0:
                    big.rect(x * 16, y * 16, x * 16 + 15, y * 16 + 15, p)
        big.save(os.path.join(PREVIEW, kind + "_" + name + ".png"))


# =====================================================================================================
#  Paleta
# =====================================================================================================
OUT = rgb("#22180F")
GOLD = rgb("#D6A23A")
GOLD_HI = rgb("#F6DC7C")
GOLD_DK = rgb("#94681F")
BONE = rgb("#E6DCC0")
BONE_HI = rgb("#F7F1E1")
BONE_SH = rgb("#B7AA88")
BONE_DK = rgb("#857857")
MORTAR = rgb("#6D624C")
SOUL = rgb("#5FE0E8")
SOUL_HI = rgb("#C8FFFF")
SOUL_DK = rgb("#1F6E78")


# =====================================================================================================
#  Filacterio (relicario de ouro e osso com a alma presa num vidro) - 4 estagios de brilho
# =====================================================================================================
PHYL_HALF = [
    "......oo",
    ".....oG.",
    "......oG",
    "...ooooo",
    "..oGGGGG",
    "..oGGGGG",
    "...oBBBB",
    "...oBSSS",
    "...oBSSS",
    "...oGSSS",
    "...oBSSS",
    "...oBSSS",
    "...oBBBB",
    "..oGGGGG",
    ".oGGGGGG",
    ".ooooooo",
]

# Cores do nucleo por estagio: (fundo do vidro, chama, centro, nucleo)
CORE = [
    (rgb("#1E2930"), rgb("#2C3C46"), rgb("#3A4E5A"), rgb("#4A606C")),
    (rgb("#163E46"), rgb("#1F6E78"), rgb("#2FA6B0"), rgb("#58C8D0")),
    (rgb("#1F5E68"), rgb("#2FA6B0"), rgb("#5FE0E8"), rgb("#B8FFFF")),
    (rgb("#2C8C96"), rgb("#5FE0E8"), rgb("#A8FFFF"), rgb("#FFFFFF")),
]

# Chama de alma dentro do vidro (6x5, x5..10, y7..11): 0 fundo, 1 chama, 2 centro, 3 nucleo
FLAME = [
    "001100",
    "012210",
    "123321",
    "123321",
    "012210",
]


def phylactery(stage):
    c = Canvas(16, 16)
    for y, half in enumerate(PHYL_HALF):
        row = half + half[::-1]
        for x, ch in enumerate(row):
            if ch == "o":
                c.set(x, y, OUT)
            elif ch == "G":
                col = GOLD
                if y in (4, 13) or x <= 4:
                    col = GOLD_HI
                if y in (5, 14) or x >= 11:
                    col = GOLD_DK
                if y == 9 and x in (4, 11):
                    col = GOLD_HI if x == 4 else GOLD_DK
                c.set(x, y, col)
            elif ch == "B":
                col = BONE
                if x >= 11 or y == 12:
                    col = BONE_SH
                if y == 6 and x <= 5:
                    col = BONE_HI
                c.set(x, y, col)
            elif ch == "S":
                fx, fy = x - 5, y - 7
                level = int(FLAME[fy][fx])
                c.set(x, y, CORE[stage][level])
    # Reflexo do vidro
    c.set(5, 7, lighten(c.get(5, 7), 0.45))
    c.set(5, 8, lighten(c.get(5, 8), 0.25))
    # Gema da tampa (acende junto)
    c.set(7, 4, CORE[stage][2] if stage else GOLD_HI)
    c.set(8, 4, CORE[stage][1] if stage else GOLD)
    # Aura de almas em volta (estagios altos)
    aura = []
    if stage >= 2:
        aura += [(1, 6, 140), (14, 9, 140), (2, 11, 110), (13, 5, 110)]
    if stage >= 3:
        aura += [(0, 8, 200), (15, 7, 200), (1, 3, 160), (14, 12, 160), (12, 1, 120), (3, 0, 120)]
    for x, y, a in aura:
        if c.get(x, y)[3] == 0:
            c.set(x, y, (SOUL[0], SOUL[1], SOUL[2], a))
    return c


for s in range(4):
    save(phylactery(s), "item", "phylactery" if s == 0 else "phylactery_glow_%d" % s)

overrides = [{"predicate": {"isekai_mahou:soul_glow": 0.25 * s}, "model": "isekai_mahou:item/phylactery_glow_%d" % s}
             for s in (1, 2, 3)]
write_json(os.path.join(ASSETS, "models", "item", "phylactery.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/phylactery"},
            "overrides": overrides})
for s in (1, 2, 3):
    write_json(os.path.join(ASSETS, "models", "item", "phylactery_glow_%d.json" % s),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/phylactery_glow_%d" % s}})


# =====================================================================================================
#  Foice de Ossos (cabo de vertebras na diagonal, lamina curva de osso com gume claro e gema de alma)
# =====================================================================================================
def bone_scythe():
    c = Canvas(16, 16)
    # Lamina: arco que sai do topo do cabo para a esquerda e desce, grossa perto do cabo e afinando até a ponta
    for x in range(1, 13):
        k = (12 - x) / 11.0
        top = 1 + round(k * k * 6.5)
        thick = 1 + round(3.2 * (1 - k))
        for i in range(thick):
            if i == 0 and thick > 1:
                col = BONE_SH              # dorso
            elif i == thick - 1:
                col = BONE_HI              # gume (lado de dentro)
            else:
                col = BONE
            c.set(x, top + i, col)
    c.set(1, 8, BONE_HI)                   # ponta
    # Cabo: vertebras de (2,15) ate (11,4), duas colunas de largura
    pts = []
    x0, y0, x1, y1 = 2, 15, 11, 5
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n
        pts.append((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))
    for i, (x, y) in enumerate(pts):
        seg = (i // 2) % 2
        c.set(x, y, BONE if seg == 0 else BONE_SH)
        c.set(x + 1, y, BONE_SH if seg == 0 else BONE_DK)
        if i % 2 == 1:  # "nos" das vertebras saltam para fora
            c.set(x - 1, y, BONE_SH)
    # Encaixe com gema de alma onde a lamina encontra o cabo
    for x, y, col in [(11, 4, GOLD), (12, 4, GOLD_DK), (11, 5, GOLD_DK), (12, 3, SOUL), (13, 3, SOUL_DK), (12, 2, SOUL_HI)]:
        c.set(x, y, col)
    # Pomo de osso na ponta de baixo
    c.set(1, 15, BONE_HI)
    c.set(2, 14, BONE)
    c.outline(OUT)
    return c


save(bone_scythe(), "item", "bone_scythe")
write_json(os.path.join(ASSETS, "models", "item", "bone_scythe.json"),
           {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/bone_scythe"}})


# =====================================================================================================
#  Altar Ossuario: texturas
# =====================================================================================================
def bone_stone(seed, dark=0.0):
    """Tijolos de osso com argamassa escura e variacao leve."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            mortar = (y % 4 == 3) or ((x + off) % 8 == 7)
            col = MORTAR if mortar else mix(BONE, BONE_SH, 0.25 + 0.15 * ((x * 7 + y * 3 + seed) % 5) / 4)
            if not mortar and y % 4 == 0:
                col = lighten(col, 0.18)
            c.set(x, y, darken(col, dark))
    c.noise(0.06, seed)
    return c


def side_texture():
    c = bone_stone(3)
    # Faixa superior (borda da laje: linhas 0..2) com vertebras entalhadas
    for x in range(16):
        c.set(x, 0, BONE_HI)
        c.set(x, 1, BONE if x % 3 else BONE_DK)
        c.set(x, 2, MORTAR)
    # Faixa inferior (base: linhas 13..15)
    for x in range(16):
        c.set(x, 13, BONE_SH if x % 4 else BONE_DK)
        c.set(x, 14, darken(BONE_SH, 0.1))
        c.set(x, 15, darken(MORTAR, 0.2))
    return c


SKULL = [
    ".hhhhhh.",
    "hBBBBBBs",
    "BeeBBeeS",
    "BeeBBeeS",
    "sBBnnBBs",
    ".BtBtBt.",
    ".dtdtdt.",
]


def column_texture(glow=False):
    """Coluna com um cranio entalhado na regiao x4..11, y3..9 (UV [4,3,12,10] no modelo)."""
    c = Canvas(16, 16) if glow else bone_stone(11, 0.18)
    pal = {"h": BONE_HI, "B": BONE, "s": BONE_SH, "S": BONE_SH, "e": rgb("#1A140E"), "n": rgb("#3A2F22"),
           "t": BONE_HI, "d": BONE_DK}
    for y, row in enumerate(SKULL):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if glow:
                if ch == "e":
                    inner = (x in (2, 5) and y == 2)
                    c.set(4 + x, 3 + y, SOUL_HI if inner else SOUL)
            else:
                c.set(4 + x, 3 + y, pal[ch])
    if not glow:
        # Rachaduras da pedra em volta
        for x, y in [(2, 11), (3, 12), (3, 13), (13, 1), (12, 2), (13, 12), (12, 13)]:
            c.set(x, y, MORTAR)
    return c


def top_texture(glow=False):
    """Topo da laje (x2..13): circulo de runas entalhado; a versao glow so tem as linhas acesas."""
    c = Canvas(16, 16) if glow else bone_stone(7, 0.05)
    if not glow:
        for y in range(2, 14):
            for x in range(2, 14):
                c.set(x, y, mix(BONE, BONE_SH, 0.3 + 0.1 * ((x + y) % 3) / 2))
        c.noise(0.05, 21)
    line_col = SOUL if glow else MORTAR
    core_col = SOUL_HI if glow else rgb("#4E4434")
    # Anel
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 4.6 <= d <= 5.4:
                c.set(x, y, line_col)
    # Triangulo invertido (pontas no anel) e ponto central
    pts = [(8 + 5 * math.cos(math.radians(a)), 8 + 5 * math.sin(math.radians(a))) for a in (90, 210, 330)]
    for i in range(3):
        (ax, ay), (bx, by) = pts[i], pts[(i + 1) % 3]
        steps = 24
        for k in range(steps + 1):
            t = k / steps
            c.set(int(ax + (bx - ax) * t), int(ay + (by - ay) * t), line_col)
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        c.set(x, y, core_col)
    # Runas pequenas nos 4 pontos cardeais, fora do anel
    for x, y in [(8, 2), (8, 13), (2, 8), (13, 8), (7, 2), (2, 7)]:
        c.set(x, y, line_col)
    return c


def bottom_texture():
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, darken(BONE_SH, 0.15))
    c.noise(0.08, 5)
    return c


def bone_spike_texture():
    """Ossos dos cantos do topo (2x3 px usados): osso claro com junta."""
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, BONE)
    for y in range(16):
        c.set(0, y, BONE_HI)
        c.set(1, y, BONE)
        c.set(15, y, BONE_SH)
    for x in range(16):
        c.set(x, 0, BONE_HI)
        c.set(x, 2, BONE_SH)
    c.noise(0.05, 9)
    return c


save(side_texture(), "block", "ossuary_altar_side")
save(column_texture(), "block", "ossuary_altar_column")
save(column_texture(glow=True), "block", "ossuary_altar_column_glow")
save(top_texture(), "block", "ossuary_altar_top")
save(top_texture(glow=True), "block", "ossuary_altar_top_glow")
save(bottom_texture(), "block", "ossuary_altar_bottom")
# Topo da laje de baixo (anel visivel em volta da coluna): tijolos de osso sem as faixas da lateral
save(bone_stone(5, 0.08), "block", "ossuary_altar_base")
save(bone_spike_texture(), "block", "ossuary_altar_bone")


# =====================================================================================================
#  Altar Ossuario: modelos e blockstate
# =====================================================================================================
def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def sides(tex, uv, cull=None):
    out = {}
    for f in ("north", "south", "east", "west"):
        out[f] = {"texture": tex, "uv": uv}
        if cull:
            out[f]["cullface"] = f
    return out


def altar_elements():
    els = []
    base = sides("#side", [1, 13, 15, 16])
    base["up"] = {"texture": "#base", "uv": [1, 1, 15, 15]}
    base["down"] = {"texture": "#bottom", "uv": [1, 1, 15, 15], "cullface": "down"}
    els.append(box([1, 0, 1], [15, 3, 15], base))
    els.append(box([4, 3, 4], [12, 10, 12], sides("#column", [4, 3, 12, 10])))
    slab = sides("#side", [2, 0, 14, 3])
    slab["up"] = {"texture": "#top", "uv": [2, 2, 14, 14]}
    slab["down"] = {"texture": "#bottom", "uv": [2, 2, 14, 14]}
    els.append(box([2, 10, 2], [14, 13, 14], slab))
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        f = sides("#bone", [0, 0, 2, 3])
        f["up"] = {"texture": "#bone", "uv": [0, 0, 2, 2]}
        els.append(box([x, 13, z], [x + 2, 16, z + 2], f))
    return els


GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def altar_glow_elements():
    els = []
    top = box([2, 13.02, 2], [14, 13.02, 14], {"up": {"texture": "#top_glow", "uv": [2, 2, 14, 14]}})
    top["shade"] = False
    top["neoforge_data"] = GLOW
    els.append(top)
    eyes = box([3.98, 3, 3.98], [12.02, 10, 12.02], sides("#column_glow", [4, 3, 12, 10]))
    eyes["shade"] = False
    eyes["neoforge_data"] = GLOW
    els.append(eyes)
    return els


TEX = {
    "particle": "isekai_mahou:block/ossuary_altar_side",
    "side": "isekai_mahou:block/ossuary_altar_side",
    "column": "isekai_mahou:block/ossuary_altar_column",
    "top": "isekai_mahou:block/ossuary_altar_top",
    "bottom": "isekai_mahou:block/ossuary_altar_bottom",
    "base": "isekai_mahou:block/ossuary_altar_base",
    "bone": "isekai_mahou:block/ossuary_altar_bone",
}
write_json(os.path.join(ASSETS, "models", "block", "ossuary_altar.json"),
           {"parent": "minecraft:block/block", "textures": TEX, "elements": altar_elements()})
lit_tex = dict(TEX)
lit_tex["top_glow"] = "isekai_mahou:block/ossuary_altar_top_glow"
lit_tex["column_glow"] = "isekai_mahou:block/ossuary_altar_column_glow"
write_json(os.path.join(ASSETS, "models", "block", "ossuary_altar_lit.json"),
           {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": lit_tex,
            "elements": altar_elements() + altar_glow_elements()})
write_json(os.path.join(ASSETS, "models", "item", "ossuary_altar.json"), {"parent": "isekai_mahou:block/ossuary_altar"})
write_json(os.path.join(ASSETS, "blockstates", "ossuary_altar.json"), {"variants": {
    "lit=false": {"model": "isekai_mahou:block/ossuary_altar"},
    "lit=true": {"model": "isekai_mahou:block/ossuary_altar_lit"},
}})


# =====================================================================================================
#  Dados: ranks do Rito Ossuario
#  souls: entity + count; mobs do mod podem nao existir -> fallback (mob vanilla) e fallback_count
# =====================================================================================================
def soul(entity, count, fallback=None, fallback_count=None):
    d = {"entity": entity, "count": count}
    if fallback:
        d["fallback"] = fallback
    if fallback_count:
        d["fallback_count"] = fallback_count
    return d


RITES = [
    dict(rank=1, xp=300, glyph_tier=1, awaken="shadow", element_xp={"shadow": 30}, servant_cap=1, servant_gift=1,
         required_skulls=0, min_circle=1,
         souls=[soul("minecraft:zombie", 10), soul("minecraft:skeleton", 5)]),
    dict(rank=2, xp=550, glyph_tier=2, element_xp={"shadow": 60}, servant_cap=1, servant_gift=1,
         required_skulls=0, min_circle=1,
         souls=[soul("minecraft:skeleton", 8), soul("minecraft:spider", 6),
                soul("isekai_mahou:goblin", 6, "minecraft:zombie", 8)]),
    dict(rank=3, xp=900, glyph_tier=2, awaken="earth", element_xp={"shadow": 60, "earth": 40}, servant_cap=1,
         servant_gift=1, required_skulls=0, min_circle=2,
         souls=[soul("minecraft:creeper", 5), soul("minecraft:spider", 5), soul("minecraft:witch", 2)]),
    dict(rank=4, xp=1500, glyph_tier=3, element_xp={"shadow": 90, "earth": 60}, servant_cap=1, servant_gift=1,
         required_skulls=1, min_circle=3,
         souls=[soul("minecraft:enderman", 4), soul("minecraft:phantom", 3), soul("minecraft:drowned", 5),
                soul("isekai_mahou:orc", 5, "minecraft:pillager", 5)]),
    dict(rank=5, xp=2400, glyph_tier=4, element_xp={"shadow": 120, "earth": 80}, servant_cap=1, servant_gift=2,
         required_skulls=2, min_circle=4,
         souls=[soul("minecraft:wither_skeleton", 3), soul("minecraft:blaze", 4), soul("minecraft:ghast", 1),
                soul("isekai_mahou:troll", 2, "minecraft:vindicator", 3)]),
    dict(rank=6, xp=3600, glyph_tier=4, element_xp={"shadow": 160, "earth": 100}, servant_cap=1, servant_gift=2,
         required_skulls=3, min_circle=5,
         souls=[soul("minecraft:evoker", 2), soul("minecraft:piglin_brute", 2),
                soul("isekai_mahou:orc_berserker", 3, "minecraft:ravager", 1),
                soul("isekai_mahou:goblin_king", 1, "minecraft:elder_guardian", 1)]),
    dict(rank=7, xp=5000, glyph_tier=5, element_xp={"shadow": 220, "earth": 140}, servant_cap=2, servant_gift=3,
         required_skulls=4, min_circle=6,
         souls=[soul("minecraft:wither", 1), soul("isekai_mahou:orc_lord", 1, "minecraft:warden", 1),
                soul("isekai_mahou:minotaur", 1, "minecraft:ravager", 2),
                soul("isekai_mahou:lich", 1, "minecraft:wither_skeleton", 10)]),
]
for r in RITES:
    write_json(os.path.join(DATA, "necro_rites", "rank_%d.json" % r["rank"]), r)


# =====================================================================================================
#  Receitas, loot, tags
# =====================================================================================================
write_json(os.path.join(DATA, "recipe", "ossuary_altar.json"), {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": [" C ", "BGB", "BBB"],
    "key": {"C": {"tag": "minecraft:candles"}, "G": {"item": "minecraft:gold_ingot"}, "B": {"item": "minecraft:bone_block"}},
    "result": {"id": "isekai_mahou:ossuary_altar", "count": 1}})
write_json(os.path.join(DATA, "recipe", "phylactery.json"), {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["NAN", "BVB", " G "],
    "key": {"N": {"item": "minecraft:gold_nugget"}, "A": {"item": "minecraft:amethyst_shard"},
            "B": {"item": "minecraft:bone"}, "V": {"item": "minecraft:glass_bottle"}, "G": {"item": "minecraft:gold_ingot"}},
    "result": {"id": "isekai_mahou:phylactery", "count": 1}})
write_json(os.path.join(DATA, "recipe", "bone_scythe.json"), {
    "type": "minecraft:crafting_shaped", "category": "equipment",
    "pattern": ["BBB", "  S", " S "],
    "key": {"B": {"item": "minecraft:bone_block"}, "S": {"item": "minecraft:bone"}},
    "result": {"id": "isekai_mahou:bone_scythe", "count": 1}})

write_json(os.path.join(DATA, "loot_table", "blocks", "ossuary_altar.json"), {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0,
               "entries": [{"type": "minecraft:item", "name": "isekai_mahou:ossuary_altar"}],
               "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    "random_sequence": "isekai_mahou:blocks/ossuary_altar"})

SOULLESS = ["undead_servant", "bone_warrior", "summoned_imp", "summoned_demon", "familiar_crow", "familiar_cat",
            "living_shadow", "summoned_salamander", "summoned_undine", "summoned_stone_golem", "summoned_sylph",
            "summoned_guardian_fairy", "summoned_ent", "animal_spirit", "spell_illusion", "spell_conjurer",
            "spell_turret", "ascension_shadow", "guide_spirit",
            # partes e lacaios invocados por chefes/fusoes (evita "fazendas" de almas nas lutas)
            "archmage_illusion", "hex_cat", "lich_phylactery", "corrupted_ent_root", "hellfire_imp", "abyss_tentacle"]
# No merge: outros modulos podem somar as proprias invocacoes a esta tag
write_json(os.path.join(MERGE, "data", "isekai_mahou", "tags", "entity_type", "necro_soulless.json"), {
    "values": [{"id": "isekai_mahou:" + s, "required": False} for s in SOULLESS]})
_old_tag = os.path.join(DATA, "tags", "entity_type", "necro_soulless.json")
if os.path.exists(_old_tag):
    os.remove(_old_tag)

write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
           {"values": ["isekai_mahou:ossuary_altar"]})
# Na tag de espadas: encantamentos de espada (Afiação, Varrer, Saque...) e varrida funcionam na Foice
write_json(os.path.join(MERGE, "data", "minecraft", "tags", "item", "swords.json"),
           {"values": ["isekai_mahou:bone_scythe"]})


# =====================================================================================================
#  Traducoes
# =====================================================================================================
M = "message.isekai_mahou.necro."
T = [
    # (chave, pt_br, en_us)
    ("item.isekai_mahou.phylactery", "Filactério", "Phylactery"),
    ("item.isekai_mahou.bone_scythe", "Foice de Ossos", "Bone Scythe"),
    ("block.isekai_mahou.ossuary_altar", "Altar Ossuário", "Ossuary Altar"),

    ("tooltip.isekai_mahou.phylactery.empty", "Vazio: nenhuma alma aprisionada.", "Empty: no souls imprisoned."),
    ("tooltip.isekai_mahou.phylactery.total", "Almas guardadas: %s", "Souls stored: %s"),
    ("tooltip.isekai_mahou.phylactery.soul_line", " • %s ×%s", " • %s ×%s"),
    ("tooltip.isekai_mahou.phylactery.more", " ...e mais %s tipos de alma", " ...and %s more kinds of soul"),
    ("tooltip.isekai_mahou.phylactery.hint1", "Mate com a Foice de Ossos, ou com o Filactério na mão secundária.",
     "Kill with the Bone Scythe, or with the Phylactery in your off hand."),
    ("tooltip.isekai_mahou.phylactery.hint2", "Ofereça as almas no Altar Ossuário à meia-noite.",
     "Offer the souls at the Ossuary Altar at midnight."),
    ("tooltip.isekai_mahou.phylactery.hint3", "Botão direito com ele na mão (na secundária, agachado): mostra o que o próximo rito exige.",
     "Right click while holding it (sneak if it is in the off hand): shows what the next rite requires."),
    ("tooltip.isekai_mahou.phylactery.unknown_soul", "Alma estranha (%s)", "Strange soul (%s)"),
    ("tooltip.isekai_mahou.bone_scythe.1", "Mortes com a foice guardam a alma no Filactério.",
     "Kills with the scythe store the soul in your Phylactery."),
    ("tooltip.isekai_mahou.bone_scythe.2", "Os golpes podem fazer o alvo murchar.", "Strikes may wither the target."),
    ("tooltip.isekai_mahou.bone_scythe.3", "Botão direito: Ceifar (giro que atinge os inimigos em volta).",
     "Right click: Reap (a spinning slash that hits nearby enemies)."),
    ("tooltip.isekai_mahou.ossuary_altar.1", "Centro do Rito Ossuário do Necromante.", "Heart of the Necromancer's Ossuary Rite."),
    ("tooltip.isekai_mahou.ossuary_altar.2", "Embaixo: anel de 8 Blocos de Osso em volta do pé do altar.",
     "Below: a ring of 8 Bone Blocks around the altar's foot."),
    ("tooltip.isekai_mahou.ossuary_altar.3", "Cantos (2 blocos na diagonal): crânios, velas acesas ou luzes de alma.",
     "Corners (2 blocks diagonally): skulls, lit candles or soul lights."),
    ("tooltip.isekai_mahou.ossuary_altar.4", "À meia-noite, clique com o Filactério.", "At midnight, use the Phylactery on it."),

    (M + "altar_busy", "O Altar Ossuário já está conduzindo um rito.", "The Ossuary Altar is already performing a rite."),
    (M + "header", "☠ Rito Ossuário %s", "☠ Ossuary Rite %s"),
    (M + "header_done", "☠ Rito Ossuário: rank %s", "☠ Ossuary Rite: rank %s"),
    (M + "no_rites", "Nenhum rito foi carregado (data/<ns>/necro_rites).", "No rites were loaded (data/<ns>/necro_rites)."),
    (M + "max_rank", "Você completou todos os Ritos Ossuários. Os mortos se curvam diante de você.",
     "You have completed every Ossuary Rite. The dead bow before you."),
    (M + "structure_ok", "Altar completo: anel de ossos e cantos prontos", "Altar complete: bone ring and corners ready"),
    (M + "missing_ring", "Faltam %s Blocos de Osso no anel (em volta do pé do altar, 1 bloco abaixo)",
     "%s Bone Blocks missing from the ring (around the altar's foot, 1 block below)"),
    (M + "missing_corners", "Faltam %s cantos (2 blocos na diagonal do altar)",
     "%s corners missing (2 blocks diagonally from the altar)"),
    (M + "corner_hint", "   Cantos aceitam crânio ou cabeça, vela acesa, tocha, lanterna ou fogueira de alma, no chão ou 1 bloco acima. O que falta está marcado com partículas.",
     "   Corners accept a skull or head, a lit candle, a soul torch, lantern or campfire, on the ground or 1 block up. Missing spots are marked with particles."),
    (M + "skulls", "Crânios verdadeiros (esqueleto ou wither) nos cantos: %s/%s",
     "True skulls (skeleton or wither) on the corners: %s/%s"),
    (M + "circle", "Círculo mágico: %s/%s", "Magic Circle: %s/%s"),
    (M + "midnight_now", "É meia-noite: o véu entre os mundos está fino", "It is midnight: the veil between worlds is thin"),
    (M + "not_midnight", "Ainda não é meia-noite (faltam cerca de %s min reais)",
     "It is not midnight yet (about %s real minutes left)"),
    (M + "no_moon", "Aqui não existe meia-noite: faça o rito sob a lua do Mundo Normal",
     "There is no midnight here: perform the rite under the Overworld moon"),
    (M + "no_phylactery", "Você não tem um Filactério", "You have no Phylactery"),
    (M + "souls_header", "Almas exigidas:", "Souls required:"),
    (M + "soul_line", "%s: %s/%s", "%s: %s/%s"),
    (M + "not_ready", "O rito ainda não pode começar.", "The rite cannot begin yet."),
    (M + "ready", "Tudo pronto! Ofereça o Filactério ao altar.", "Everything is ready! Offer the Phylactery to the altar."),
    (M + "rite_title", "Rito Ossuário %s", "Ossuary Rite %s"),
    (M + "rite_subtitle", "As almas atendem ao chamado da meia-noite", "The souls answer the midnight call"),
    (M + "rite_begin", "Você oferece as almas ao altar... o Rito Ossuário %s começou.",
     "You offer the souls to the altar... the Ossuary Rite %s has begun."),
    (M + "rite_complete", "☠ Rito Ossuário %s concluído!", "☠ Ossuary Rite %s complete!"),
    (M + "glyph_learned", "  ✦ Glifo de Necromancia aprendido: %s", "  ✦ Necromancy glyph learned: %s"),
    (M + "no_glyph", "Nenhum glifo novo de Necromancia desta vez.", "No new Necromancy glyph this time."),
    (M + "servant_cap", "Seu limite de servos mortos-vivos aumentou em %s (bônus total dos ritos: +%s).",
     "Your undead servant limit grew by %s (total rite bonus: +%s)."),
    (M + "servant_gift_full", "Seus %s servos de presente ainda de pé já ocupam as vagas do rito: nenhum novo servo se ergueu.",
     "Your %s gifted servants still standing already fill the rite's slots: no new servant rose."),
    (M + "servant_gift", "%s servo(s) morto(s)-vivo(s) se ergueu(eram) para servi-lo.", "%s undead servant(s) rose to serve you."),
    (M + "soul_lost", "A alma se dispersa... (você não tem um Filactério)", "The soul fades away... (you have no Phylactery)"),
    (M + "phylactery_full", "O Filactério não comporta mais almas de %s", "The Phylactery cannot hold more %s souls"),
    (M + "soul_harvested", "Alma colhida: %s (%s)", "Soul harvested: %s (%s)"),
    (M + "phylactery_report", "Filactério: %s almas guardadas", "Phylactery: %s souls stored"),
    (M + "next_rite", "Próximo: Rito Ossuário %s, que exige:", "Next: Ossuary Rite %s, which requires:"),
    (M + "next_rite_skulls", "   ...e %s crânio(s) verdadeiro(s) nos cantos do altar",
     "   ...and %s true skull(s) on the altar corners"),
    (M + "next_rite_circle", "   ...e estar no Círculo %s", "   ...and being in Circle %s"),

    ("command.isekai_mahou.necro.rank", "Rank do Rito Ossuário definido para %s.", "Ossuary Rite rank set to %s."),
    ("command.isekai_mahou.necro.souls", "%s almas adicionadas ao Filactério.", "%s souls added to the Phylactery."),
    ("command.isekai_mahou.necro.no_phylactery", "Segure um Filactério na mão.", "Hold a Phylactery in your hand."),
]
pt = {k: p for k, p, _ in T}
en = {k: e for k, _, e in T}
write_json(os.path.join(LANG, "pt_br.json"), pt)
write_json(os.path.join(LANG, "en_us.json"), en)
print("ritual_necro: assets gerados (%d traducoes)" % len(T))
