"""Gera os assets/dados do modulo "character" (RACAS, sem atributos; v1.0.1: 11 racas livres, niveis de raca e
Transformacoes).

Uso (na raiz do projeto):  py -I tools/gen/character.py

Gera: cadeado, icone da aba "Raca", as texturas das partes raciais do jogador (race_features.png: orelhas, chifres,
barba, caudas; race_features2.png: aureola, olhos e patas de aranha, coroa), as asas (Sombrias, de anjo e draconicas),
a textura das auras/cascas translucidas (aura.png), os icones 18x18 dos efeitos das skills raciais e das 11
Transformacoes (race_form_<id>), o Altar da Reencarnacao (texturas, modelo, blockstate, item, loot, receita, tag de
picareta), as 11 Reliquias Raciais (itens) e as traducoes pt_br/en_us do modulo (racas, passivas por nivel,
Transformacoes, feitos, tracos, os 22 glifos raciais, efeitos e telas). Depois das traducoes, usa o tools/gen/icons.py
(a "arte") para os medalhoes das 4 racas novas (textures/gui/race/<id>.png) e os icones dos 8 glifos raciais novos
(textures/gui/glyph/race_*.png) — os 7 medalhoes e 14 glifos antigos continuam vindo do proprio icons.py.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/character")
LANG = os.path.join(ROOT, "src/main/lang/character")
NS = "isekai_mahou"
T = (0, 0, 0, 0)
OUT = hex_rgba("1a1020")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def gui(name):
    return os.path.join(A, "textures/gui/character", name + ".png")


def icon_lock():
    c = Canvas(16, 16)
    gold, dark = hex_rgba("c9a24a"), hex_rgba("6a5020")
    for y in range(1, 8):
        for x in range(4, 12):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 7)
            if 2.6 <= d <= 4.2 and y <= 7:
                c.set(x, y, hex_rgba("9aa0aa"))
    c.rect(3, 7, 12, 14, gold)
    c.rect(3, 13, 12, 14, dark)
    c.rect(7, 9, 8, 12, hex_rgba("2a1a10"))
    c.shade_sphere(7, 9, 7, 0.35, 0.3)
    c.outline(OUT)
    return c


def icon_tab():
    c = Canvas(16, 16)
    gold, robe, skin = hex_rgba("e8c77a"), hex_rgba("7a5cff"), hex_rgba("f0c8a0")
    c.circle(8, 4.5, 2.8, skin)
    c.rect(5, 2, 11, 2, hex_rgba("4a3020"))
    for y in range(8, 15):
        half = 2 + (y - 8) // 2
        c.rect(8 - half, y, 7 + half, y, robe)
    c.rect(7, 8, 8, 14, gold)
    for (x, y) in ((2, 3), (13, 2), (14, 9), (1, 10)):
        c.set(x, y, gold)
    c.shade_sphere(8, 8, 8, 0.25, 0.3)
    c.outline(OUT)
    return c


# =====================================================================================
#  Partes raciais do jogador (64x64)
# =====================================================================================
def region(c, x0, y0, w, h, base, seed, amount=0.08, strands=False):
    rnd = random.Random(seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            f = (rnd.random() - 0.5) * 2 * amount
            px = lighten(base, f) if f > 0 else darken(base, -f)
            if strands and (x + (y // 2)) % 3 == 0:
                px = darken(px, 0.18)
            c.set(x, y, px)


def scales(c, x0, y0, w, h, base, seed):
    region(c, x0, y0, w, h, base, seed, 0.05)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (y - y0) % 2 == 0 and (x + (y - y0) // 2 * 1) % 3 == 0:
                c.set(x, y, darken(c.get(x, y), 0.28))
            elif (y - y0) % 2 == 1 and (x + 1) % 3 == 0:
                c.set(x, y, lighten(c.get(x, y), 0.15))


def race_features():
    c = Canvas(64, 64)
    skin = hex_rgba("f0c8a0")
    region(c, 0, 0, 10, 7, skin, 1, 0.05)                 # orelha de elfo
    region(c, 0, 8, 6, 4, lighten(skin, 0.05), 2, 0.05)   # ponta
    for x in range(0, 10):
        c.set(x, 6, darken(skin, 0.18))
    horn = hex_rgba("3a2430")
    region(c, 12, 0, 8, 5, horn, 3, 0.1)                  # chifre de demonio (base)
    for x in range(12, 20, 2):
        c.set(x, 2, darken(horn, 0.35))
    region(c, 12, 6, 4, 4, lighten(horn, 0.08), 4, 0.1)
    region(c, 16, 6, 4, 3, hex_rgba("6a4652"), 5, 0.1)    # ponta mais clara
    ivory = hex_rgba("efe6d2")
    region(c, 20, 0, 12, 6, ivory, 6, 0.06)               # chifre de dragonato (tingido)
    for x in range(20, 32, 3):
        c.set(x, 3, darken(ivory, 0.2))
    region(c, 20, 6, 8, 4, lighten(ivory, 0.05), 7, 0.06)
    region(c, 32, 0, 4, 3, hex_rgba("f6f0dc"), 8, 0.04)   # presa
    fur = hex_rgba("f2f0ec")
    region(c, 40, 0, 8, 4, fur, 9, 0.07)                  # orelha animal
    region(c, 40, 4, 6, 2, fur, 10, 0.07)
    region(c, 40, 6, 4, 2, darken(fur, 0.1), 11, 0.07)
    region(c, 48, 8, 4, 2, hex_rgba("f4c8d0"), 12, 0.04)  # parte de dentro
    region(c, 48, 0, 6, 7, fur, 13, 0.07)                 # orelha de coelho
    for y in range(1, 6):
        c.set(49, y, hex_rgba("f4c8d0")); c.set(52, y, hex_rgba("f4c8d0"))
    hair = hex_rgba("e6e2dc")
    region(c, 0, 16, 18, 5, hair, 14, 0.06, strands=True)  # barba
    region(c, 0, 22, 14, 4, hair, 15, 0.06, strands=True)
    region(c, 0, 27, 10, 3, hair, 16, 0.06, strands=True)
    region(c, 20, 16, 16, 2, lighten(hair, 0.05), 17, 0.05, strands=True)
    region(c, 0, 32, 14, 7, fur, 18, 0.09, strands=True)   # cauda animal
    region(c, 0, 46, 12, 6, hex_rgba("ffffff"), 19, 0.04)  # ponta clara
    region(c, 0, 52, 10, 5, hex_rgba("ffffff"), 20, 0.05)  # pompom de coelho
    region(c, 20, 32, 10, 5, hex_rgba("d8d4d8"), 21, 0.08) # cauda de demonio (tingida)
    region(c, 20, 38, 10, 3, hex_rgba("f0e8e8"), 22, 0.05)
    region(c, 20, 41, 4, 2, hex_rgba("f0e8e8"), 23, 0.05)
    scales(c, 32, 32, 18, 9, hex_rgba("f0ece4"), 24)       # cauda de dragonato (tingida)
    scales(c, 32, 41, 16, 8, hex_rgba("f0ece4"), 25)
    scales(c, 32, 49, 12, 6, hex_rgba("f0ece4"), 26)
    return c


# =====================================================================================
#  Altar da Reencarnacao
# =====================================================================================
def altar_textures():
    rnd = random.Random(42)
    stone, stone2 = hex_rgba("2a2436"), hex_rgba("1c1826")
    gold, rune, soul = hex_rgba("d8b25a"), hex_rgba("9a7aff"), hex_rgba("7ff0ff")
    out = {}

    side = Canvas(16, 16)            # base: tijolos escuros com friso dourado
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 0 if row % 2 == 0 else 4
            mortar = y % 4 == 3 or (x + off) % 8 == 7
            base = stone2 if mortar else stone
            f = (rnd.random() - 0.5) * 0.12
            side.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))
    side.rect(0, 0, 15, 0, gold)
    side.rect(0, 1, 15, 1, darken(gold, 0.4))
    out["reincarnation_altar_base"] = side

    top = Canvas(16, 16)             # topo da base: lajotas
    for y in range(16):
        for x in range(16):
            mortar = x % 8 == 0 or y % 8 == 0
            base = stone2 if mortar else lighten(stone, 0.05)
            f = (rnd.random() - 0.5) * 0.1
            top.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))
    out["reincarnation_altar_base_top"] = top

    bottom = Canvas(16, 16)
    region(bottom, 0, 0, 16, 16, stone2, 43, 0.08)
    out["reincarnation_altar_bottom"] = bottom

    pillar = Canvas(16, 16)          # coluna: pedra escura com runas acesas
    region(pillar, 0, 0, 16, 16, hex_rgba("231d30"), 44, 0.1)
    for x in (2, 13):
        pillar.rect(x, 0, x, 15, darken(gold, 0.35))
    glyphs = [(5, 2), (8, 4), (6, 7), (9, 9), (5, 12)]
    for (x, y) in glyphs:
        pillar.rect(x, y, x + 2, y, rune)
        pillar.set(x + 1, y + 1, rune)
        pillar.set(x, y - 1 if y > 0 else y, lighten(rune, 0.4))
    out["reincarnation_altar_pillar"] = pillar

    plate = Canvas(16, 16)           # tampa: obsidiana com circulo de ouro e alma no centro
    region(plate, 0, 0, 16, 16, hex_rgba("1a1428"), 45, 0.1)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.2 <= d <= 7.2:
                plate.set(x, y, gold)
            elif 3.6 <= d <= 4.3:
                plate.set(x, y, rune)
            elif d < 2.2:
                plate.set(x, y, soul if d < 1.2 else darken(soul, 0.3))
    for a in range(6):
        ang = a * math.pi / 3
        x, y = int(8 + math.cos(ang) * 5.3), int(8 + math.sin(ang) * 5.3)
        plate.set(x, y, lighten(gold, 0.3))
    out["reincarnation_altar_top"] = plate

    rim = Canvas(16, 16)             # lateral da tampa: faixa dourada com gemas
    region(rim, 0, 0, 16, 16, hex_rgba("1a1428"), 46, 0.08)
    rim.rect(0, 13, 15, 15, darken(gold, 0.1))
    rim.rect(0, 13, 15, 13, lighten(gold, 0.25))
    for x in (3, 8, 13):
        rim.set(x, 14, soul)
    out["reincarnation_altar_rim"] = rim

    crystal = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            crystal.set(x, y, mix(lighten(soul, 0.45), darken(soul, 0.35), t))
    crystal.line(2, 13, 13, 2, lighten(soul, 0.7))
    out["reincarnation_altar_crystal"] = crystal
    return out


def face(uv, tex, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def altar_model():
    p = "#"
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/reincarnation_altar_base",
            "base": f"{NS}:block/reincarnation_altar_base",
            "base_top": f"{NS}:block/reincarnation_altar_base_top",
            "bottom": f"{NS}:block/reincarnation_altar_bottom",
            "pillar": f"{NS}:block/reincarnation_altar_pillar",
            "top": f"{NS}:block/reincarnation_altar_top",
            "rim": f"{NS}:block/reincarnation_altar_rim",
            "crystal": f"{NS}:block/reincarnation_altar_crystal",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                "down": face([0, 0, 16, 16], p + "bottom", "down"), "up": face([0, 0, 16, 16], p + "base_top"),
                "north": face([0, 0, 16, 4], p + "base", "north"), "south": face([0, 0, 16, 4], p + "base", "south"),
                "west": face([0, 0, 16, 4], p + "base", "west"), "east": face([0, 0, 16, 4], p + "base", "east")}},
            {"from": [3, 4, 3], "to": [13, 11, 13], "faces": {
                "north": face([3, 4, 13, 11], p + "pillar"), "south": face([3, 4, 13, 11], p + "pillar"),
                "west": face([3, 4, 13, 11], p + "pillar"), "east": face([3, 4, 13, 11], p + "pillar")}},
            {"from": [1, 11, 1], "to": [15, 14, 15], "faces": {
                "down": face([1, 1, 15, 15], p + "bottom"), "up": face([1, 1, 15, 15], p + "top"),
                "north": face([1, 12, 15, 15], p + "rim"), "south": face([1, 12, 15, 15], p + "rim"),
                "west": face([1, 12, 15, 15], p + "rim"), "east": face([1, 12, 15, 15], p + "rim")}},
            {"from": [6.5, 14, 6.5], "to": [9.5, 17, 9.5],
             "rotation": {"origin": [8, 15, 8], "axis": "y", "angle": 45},
             "faces": {k: face([4, 4, 10, 10], p + "crystal") for k in ("north", "south", "west", "east", "up")}},
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


# =====================================================================================
#  v1.0: icones 18x18 dos efeitos das skills raciais (estilo vanilla: desenho solto com contorno)
# =====================================================================================
def effect_stone_skin():
    c = Canvas(18, 18)
    stone, dark, light = hex_rgba("9a8f80"), hex_rgba("5e564c"), hex_rgba("c8beb0")
    # escudo de pedra lascado
    for y in range(2, 16):
        half = 6 if y < 10 else max(0, 6 - (y - 9))
        c.rect(9 - half, y, 8 + half, y, stone)
    c.line(5, 5, 8, 9, dark)
    c.line(8, 9, 7, 13, dark)
    c.line(12, 4, 10, 8, dark)
    c.rect(4, 3, 6, 4, light)
    c.shade_sphere(9, 8, 9, 0.35, 0.35)
    c.noise(0.08, 31)
    c.outline(OUT)
    return c


def effect_blood_fury():
    c = Canvas(18, 18)
    red, dark, hi = hex_rgba("b01e28"), hex_rgba("5a0a10"), hex_rgba("ff7a7a")
    # gota de sangue (ponta em cima, barriga redonda) com olhar de furia
    for y in range(1, 9):
        half = (y - 1) * 0.55
        c.rect(int(round(9 - half)), y, int(round(8 + half)), y, red)
    c.circle(9, 11.5, 5.2, red)
    c.line(6, 10, 8, 11, dark)
    c.line(12, 10, 10, 11, dark)
    c.rect(7, 14, 10, 14, dark)
    c.set(7, 6, hi)
    c.set(6, 8, hi)
    c.shade_sphere(9, 11, 6, 0.3, 0.4)
    c.outline(OUT)
    return c


def effect_dragon_scales():
    c = Canvas(18, 18)
    gold, dark, hi = hex_rgba("e0a030"), hex_rgba("7a4a10"), hex_rgba("fff2b0")
    # tres fileiras de escamas em arco
    for row, y0 in enumerate((3, 7, 11)):
        for k in range(3 - (row % 2), 16, 4):
            for dy in range(4):
                w = [1, 2, 2, 1][dy]
                c.rect(k - w, y0 + dy, k + w - 1, y0 + dy, gold)
            c.set(k - 1, y0 + 3, dark)
            c.set(k, y0 + 3, dark)
            c.set(k - 1, y0, hi)
    c.shade_sphere(9, 9, 9, 0.3, 0.3)
    c.outline(OUT)
    return c


def effect_wild_instinct():
    c = Canvas(18, 18)
    amber, dark, iris = hex_rgba("d08a3a"), hex_rgba("4a2a10"), hex_rgba("ffc23a")
    # olho de fera (pupila em fenda)
    for x in range(2, 16):
        t = (x - 2) / 13
        half = int(round(math.sin(t * math.pi) * 4.5))
        c.rect(x, 9 - half, x, 8 + half, amber)
    c.circle(9, 8.5, 3.2, iris)
    c.rect(9, 6, 9, 11, dark)
    c.set(10, 6, hex_rgba("fff6d0"))
    c.shade_sphere(9, 9, 8, 0.3, 0.3)
    c.outline(OUT)
    return c


def effect_shadow_wings():
    c = Canvas(18, 18)
    wing, dark, hi = hex_rgba("5a3478"), hex_rgba("1a0a24"), hex_rgba("b08ae0")
    # par de asas de morcego abertas: borda de cima subindo para as pontas, borda de baixo em vieiras
    for s_ in (1, -1):
        for i in range(8):
            x = (9 + i) if s_ > 0 else (8 - i)
            top = int(round(7 - i * 0.75))
            bot = int(round(12 - i * 0.35 - (1.6 if i % 3 == 1 else 0)))
            if bot >= top:
                c.rect(x, top, x, bot, wing)
            c.set(x, top, dark)
        for i in (3, 6):
            x = (9 + i) if s_ > 0 else (8 - i)
            c.line(9 if s_ > 0 else 8, 7, x, int(round(12 - i * 0.35)) - 1, dark)
        c.set((9 + 7) if s_ > 0 else (8 - 7), int(round(7 - 7 * 0.75)) + 1, hi)
    c.rect(8, 6, 9, 12, dark)
    c.outline(OUT)
    return c


EFFECT_ICONS = {
    "race_stone_skin": effect_stone_skin, "race_blood_fury": effect_blood_fury,
    "race_dragon_scales": effect_dragon_scales, "race_wild_instinct": effect_wild_instinct,
    "race_shadow_wings": effect_shadow_wings,
}


def shadow_wings_texture():
    """64x32: osso 12x1x1 em (0,0), membrana 12x11 em (0,4) (frente) e (12,4) (verso); ponta: osso em (0,16),
    membrana 8x8 em (0,20) e (8,20). A membrana e translucida, mais escura na base e com nervuras."""
    c = Canvas(64, 32)
    bone, bone_hi = hex_rgba("2a1636"), hex_rgba("5a3a74")
    c.rect(0, 0, 25, 1, bone)
    c.rect(0, 0, 25, 0, bone_hi)
    c.rect(0, 16, 17, 17, bone)
    c.rect(0, 16, 17, 16, bone_hi)

    def membrane(x0, y0, w, h, seed):
        rnd = random.Random(seed)
        for y in range(h):
            for x in range(w):
                t = y / max(1, h - 1)
                base = mix(hex_rgba("5a3478", 225), hex_rgba("1a0a24", 175), t)
                # recorte da borda de baixo (pontas de morcego)
                edge = h - 1 - int(abs(math.sin((x + 1) / w * math.pi * 2.0)) * 2.5)
                if y > edge:
                    continue
                if x % 4 == 0 and y < edge - 1:
                    base = hex_rgba("2a1636", 240)  # nervura
                n = rnd.uniform(-0.06, 0.06)
                base = lighten(base, n) if n > 0 else darken(base, -n)
                c.set(x0 + x, y0 + y, base)

    membrane(0, 4, 12, 11, 51)
    membrane(12, 4, 12, 11, 52)
    membrane(0, 20, 8, 8, 53)
    membrane(8, 20, 8, 8, 54)
    return c


# =====================================================================================
#  Reliquias Raciais (itens 16x16): a unica forma de outra raca aprender as skills raciais
# =====================================================================================
def thick_curve(c, p0, p1, p2, r0, r1, color_fn, steps=60):
    """Curva quadratica (p0 -> p2, controle p1) pintada com circulos de raio r0 -> r1; cor por t (0..1)."""
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        r = r0 + (r1 - r0) * t
        col = color_fn(t)
        for py in range(int(y - r) - 1, int(y + r) + 2):
            for px in range(int(x - r) - 1, int(x + r) + 2):
                if (px + 0.5 - x) ** 2 + (py + 0.5 - y) ** 2 <= r * r:
                    c.set(px, py, col)


def sparkle(c, x, y, col):
    c.set(x, y, col)
    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.set(x + ox, y + oy, (col[0], col[1], col[2], 150))


def relic_human():
    """Emblema Humano: medalhao de ouro com o brasao (escudo azul e cruz branca)."""
    c = Canvas(16, 16)
    dark, gold, light = hex_rgba("9a7430"), hex_rgba("e8c77a"), hex_rgba("fff0b0")
    c.circle(8, 8, 6.6, dark)
    c.circle(8, 8, 5.7, gold)
    for x, y in ((8, 2), (13, 7), (8, 13), (3, 8), (4, 4), (12, 4), (4, 12), (12, 12)):
        c.set(x, y, light if y < 8 else dark)
    blue, blue_d = hex_rgba("3a5cc8"), hex_rgba("23367a")
    for y in range(4, 12):
        half = 3 if y <= 8 else (2 if y == 9 else 1 if y == 10 else 0)
        if half:
            c.rect(8 - half, y, 7 + half, y, blue if y < 8 else blue_d)
    white = hex_rgba("f4f0e6")
    c.rect(7, 4, 8, 10, white)
    c.rect(5, 6, 10, 6, white)
    c.shade_sphere(8, 8, 7, 0.3, 0.35)
    c.outline(OUT)
    sparkle(c, 13, 2, light)
    return c


def relic_elf():
    """Lagrima Elfica: gota de cristal verde-agua com folhinhas de prata no topo."""
    c = Canvas(16, 16)
    base, deep = hex_rgba("6fe0c0"), hex_rgba("2a9a78")
    c.circle(8, 10.5, 4.4, base)
    for y in range(3, 8):
        half = (y - 2) * 0.75
        for x in range(16):
            if abs(x + 0.5 - 8) <= half:
                c.set(x, y, base)
    for y in range(3, 15):
        for x in range(16):
            p = c.get(x, y)
            if p[3] and (x >= 9 or y >= 13):
                c.set(x, y, mix(p, deep, 0.45))
    c.shade_sphere(8, 10.5, 5, 0.45, 0.3)
    leaf, silver = hex_rgba("7bd88f"), hex_rgba("d8e4ec")
    c.set(8, 2, silver); c.set(7, 1, leaf); c.set(6, 1, leaf); c.set(9, 1, leaf); c.set(10, 0, leaf)
    c.set(6, 9, hex_rgba("ffffff")); c.set(6, 10, hex_rgba("e8fff8")); c.set(7, 8, hex_rgba("e8fff8"))
    c.outline(OUT)
    sparkle(c, 13, 4, hex_rgba("d8fff0"))
    sparkle(c, 2, 7, hex_rgba("b8f0ff"))
    return c


def relic_dwarf():
    """Runa Ana: placa de pedra com uma runa incandescente."""
    c = Canvas(16, 16)
    stone = hex_rgba("7c6e62")
    c.rect(3, 2, 12, 13, stone)
    c.rect(4, 1, 11, 14, stone)
    c.noise(0.1, seed=77)
    for x in range(3, 13):
        c.set(x, 13, darken(c.get(x, 13), 0.3))
    for y in range(2, 14):
        c.set(12, y, darken(c.get(12, y), 0.25))
        c.set(3, y, lighten(c.get(3, y), 0.15))
    glow, hot = hex_rgba("ff9a30"), hex_rgba("ffe08a")
    rune = [(7, y) for y in range(4, 12)] + [(8, 4), (9, 5), (10, 6), (9, 7), (8, 8), (9, 9), (10, 10), (10, 11)]
    for x, y in rune:
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = c.get(x + ox, y + oy)
            if q[3] and (x + ox, y + oy) not in rune:
                c.set(x + ox, y + oy, mix(q, glow, 0.35))
    for i, (x, y) in enumerate(rune):
        c.set(x, y, hot if i % 3 == 0 else glow)
    c.outline(OUT)
    sparkle(c, 13, 2, hot)
    return c


def relic_orc():
    """Presa Orc: presa de marfim curva com amarra de couro e uma conta verde."""
    c = Canvas(16, 16)
    ivory, ivory_d = hex_rgba("efe2c2"), hex_rgba("b8a47a")
    thick_curve(c, (4, 13), (5, 5), (13, 2), 2.3, 0.5, lambda t: mix(ivory_d, ivory, min(1.0, t * 1.6)))
    for y in range(16):
        for x in range(16):
            p = c.get(x, y)
            if p[3] and c.get(x + 1, y + 1)[3] == 0:
                c.set(x, y, darken(p, 0.22))
            elif p[3] and c.get(x - 1, y - 1)[3] == 0:
                c.set(x, y, lighten(p, 0.15))
    leather, leather_d = hex_rgba("7a5230"), hex_rgba("4a3018")
    for x in range(2, 8):
        c.set(x, 11, leather if x % 2 else leather_d)
        c.set(x - 1, 12, leather_d if x % 2 else leather)
    c.set(2, 13, hex_rgba("8dbf5a")); c.set(1, 14, hex_rgba("5e8a33"))
    c.outline(OUT)
    return c


def relic_demon():
    """Chifre Demoniaco: chifre curvo vermelho-escuro com aneis e ponta em brasa."""
    c = Canvas(16, 16)
    base, tip, ember_c = hex_rgba("a82a40"), hex_rgba("3a1018"), hex_rgba("ff5a48")
    thick_curve(c, (11, 13), (2, 11), (5, 2), 2.4, 0.6,
                lambda t: ember_c if t > 0.9 else mix(base, tip, min(1.0, t * 1.1)))
    # aneis do chifre
    for i in range(1, 6):
        t = i / 6.5
        x = (1 - t) ** 2 * 11 + 2 * (1 - t) * t * 2 + t * t * 5
        y = (1 - t) ** 2 * 13 + 2 * (1 - t) * t * 11 + t * t * 2
        for ox in range(-3, 4):
            px, py = int(x + ox * 0.6), int(y - ox * 0.5)
            q = c.get(px, py)
            if q[3]:
                c.set(px, py, darken(q, 0.35))
    for y in range(16):
        for x in range(16):
            p = c.get(x, y)
            if p[3] and c.get(x - 1, y)[3] == 0:
                c.set(x, y, lighten(p, 0.25))
    c.set(5, 2, hex_rgba("ffb070"))
    c.outline(OUT)
    sparkle(c, 9, 2, hex_rgba("ff9070"))
    c.set(12, 5, hex_rgba("ff6a50", 200))
    c.set(2, 4, hex_rgba("ff6a50", 160))
    return c


def relic_dragon():
    """Escama Draconica: escama grande ambar com crista central e bordas escuras."""
    c = Canvas(16, 16)
    amber, amber_d, amber_l = hex_rgba("e0a030"), hex_rgba("8a5a14"), hex_rgba("ffd878")
    for y in range(1, 15):
        if y <= 8:
            half = 5.5 if y > 2 else 4.5
        else:
            half = 5.5 - (y - 8) * 0.9
        for x in range(16):
            if abs(x + 0.5 - 8) <= half:
                c.set(x, y, amber)
    for y in range(1, 15):
        for x in range(16):
            p = c.get(x, y)
            if not p[3]:
                continue
            if c.get(x - 1, y)[3] == 0 or c.get(x + 1, y)[3] == 0 or c.get(x, y + 1)[3] == 0:
                c.set(x, y, amber_d)
    for y in range(2, 13):
        c.set(7, y, amber_l)
        c.set(8, y, mix(amber, amber_l, 0.5))
    for y in (4, 7, 10):
        for x in range(4, 12):
            if c.get(x, y) == amber:
                c.set(x, y, mix(amber, amber_d, 0.35))
    c.shade_sphere(8, 7, 7.5, 0.3, 0.3)
    c.outline(OUT)
    sparkle(c, 3, 2, amber_l)
    return c


def relic_beast():
    """Garra Bestial: tres garras de osso num cordao de couro, com tufos de pelo."""
    c = Canvas(16, 16)
    cord = hex_rgba("5a3a1e")
    for x in range(1, 15):
        c.set(x, 2 + (1 if 3 <= x <= 12 else 0) + (1 if 6 <= x <= 9 else 0), cord)
    bone, tipc = hex_rgba("efe4c8"), hex_rgba("4a3a2a")
    for (x0, y0, length) in ((4, 4, 7), (8, 5, 9), (12, 4, 7)):
        thick_curve(c, (x0, y0 + 1), (x0 + 1.5, y0 + length * 0.6), (x0 - 1, y0 + length),
                    1.4, 0.4, lambda t: mix(bone, tipc, max(0.0, (t - 0.6) / 0.4)))
    fur, fur_d = hex_rgba("d08a3a"), hex_rgba("8a5420")
    for x0, y0 in ((4, 4), (8, 5), (12, 4)):
        c.rect(x0 - 1, y0, x0 + 1, y0, fur)
        c.set(x0 - 1, y0 + 1, fur_d); c.set(x0 + 1, y0 + 1, fur_d)
    c.outline(OUT)
    return c


def relic_arachnid():
    """Seda de Aracne: carretel de madeira com seda branca enrolada e um fio solto formando teia."""
    c = Canvas(16, 16)
    wood, wood_d = hex_rgba("8a5a30"), hex_rgba("5a3a1e")
    silk, silk_d = hex_rgba("f2f0f8"), hex_rgba("c8c0d8")
    c.rect(3, 2, 12, 3, wood)
    c.rect(3, 12, 12, 13, wood)
    c.rect(3, 3, 12, 3, wood_d)
    c.rect(3, 13, 12, 13, wood_d)
    c.rect(5, 4, 10, 11, silk)
    for y in range(4, 12):
        if y % 2 == 0:
            c.rect(5, y, 10, y, silk_d)
    c.rect(5, 4, 5, 11, silk_d)
    # fio solto descendo e um pedaço de teia
    for (x, y) in ((11, 8), (12, 9), (13, 10), (13, 11), (14, 12), (14, 13)):
        c.set(x, y, silk)
    c.set(13, 13, silk_d)
    c.set(14, 14, silk_d)
    c.outline(OUT)
    sparkle(c, 2, 7, hex_rgba("e6dcff"))
    return c


def relic_phantom():
    """Ectoplasma Antigo: frasco de vidro com névoa ciano brilhante e rolha, uma alma rodopiando dentro."""
    c = Canvas(16, 16)
    glass, glass_d = hex_rgba("a8c8d8", 150), hex_rgba("6a8898", 200)
    mist, mist_l = hex_rgba("6fe0f0"), hex_rgba("d8fbff")
    c.rect(6, 1, 9, 2, hex_rgba("8a5a30"))
    c.rect(6, 3, 9, 4, glass_d)
    c.circle(8, 10, 5.2, glass)
    c.circle(8, 10.6, 4.2, mist)
    c.circle(7, 9.6, 1.6, mist_l)
    c.set(9, 11, mist_l)
    c.set(10, 12, mist_l)
    c.set(5, 8, hex_rgba("ffffff", 220))
    c.shade_sphere(8, 10, 5.2, 0.25, 0.3)
    c.outline(OUT)
    sparkle(c, 13, 3, mist_l)
    return c


def relic_angel():
    """Pluma Seráfica: pena dourada e branca na diagonal, com a haste de ouro e um brilho."""
    c = Canvas(16, 16)
    white, gold, gold_d = hex_rgba("fff8e6"), hex_rgba("ffd86b"), hex_rgba("c8962a")
    for i in range(12):
        cx, cy = 3 + i, 13 - i
        w = 2.6 * math.sin(math.pi * (i + 1) / 13.0)
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                if abs(dx - dy) <= w * 1.4 and abs(dx + dy) <= 1 + (1 if i > 2 else 0):
                    c.set(cx + dx, cy + dy, white if (i + dx) % 3 else mix(white, gold, 0.4))
    for i in range(13):
        c.set(2 + i, 14 - i, gold)
    c.set(1, 15, gold_d)
    c.set(2, 14, gold_d)
    c.outline(OUT)
    sparkle(c, 12, 10, gold)
    return c


def relic_slime():
    """Núcleo Real de Slime: cubo de geleia verde translúcida com o núcleo escuro e uma coroinha."""
    c = Canvas(16, 16)
    gel, gel_d, gel_l = hex_rgba("8fe05a", 230), hex_rgba("4a9a2a", 240), hex_rgba("d8ffb8", 240)
    c.rect(3, 5, 12, 14, gel)
    c.rect(3, 14, 12, 14, gel_d)
    c.rect(12, 5, 12, 14, gel_d)
    c.rect(6, 8, 9, 11, hex_rgba("2e6a1a"))
    c.rect(7, 9, 8, 10, hex_rgba("1a3a10"))
    c.rect(4, 6, 5, 6, gel_l)
    c.set(4, 7, gel_l)
    gold = hex_rgba("ffd86b")
    c.rect(5, 3, 10, 4, gold)
    for x in (5, 7, 8, 10):
        c.set(x, 2, gold)
    c.set(7, 3, hex_rgba("e83434"))
    c.outline(OUT)
    return c


RELIC_ART = {
    "human": relic_human, "elf": relic_elf, "dwarf": relic_dwarf, "half_orc": relic_orc,
    "demon": relic_demon, "dragonborn": relic_dragon, "beastfolk": relic_beast,
    "arachnid": relic_arachnid, "phantom": relic_phantom, "angel": relic_angel, "slime": relic_slime,
}


# =====================================================================================
#  v1.0.1: partes novas do jogador (race_features2.png), asas de anjo/dragão e a textura das auras
# =====================================================================================
def race_features2():
    """64x64: auréola (0,0), olhos de aranha (0,8)/(4,8), coxa da pata (0,16), canela (0,24), coroa (0,40) e as
    pontas da coroa com joia (40,40). As UVs casam com RaceFeatureLayer.model2()."""
    c = Canvas(64, 64)
    gold = hex_rgba("ffd86b")
    region(c, 0, 0, 10, 4, gold, 101, 0.05)
    for x in range(0, 10, 2):
        c.set(x, 1, lighten(gold, 0.45))
    eye = hex_rgba("d01830")
    region(c, 0, 8, 8, 3, eye, 102, 0.06)
    for x in (1, 5):
        c.set(x, 8, hex_rgba("ffb0b0"))
    chit, band = hex_rgba("2a1a36"), hex_rgba("5a3a7a")
    region(c, 0, 16, 18, 3, chit, 103, 0.1)
    for x in range(1, 18, 4):
        c.rect(x, 16, x, 18, band)
    region(c, 0, 24, 4, 11, chit, 104, 0.1)
    for y in range(25, 34, 3):
        c.rect(0, y, 3, y, band)
    c.rect(0, 33, 3, 34, hex_rgba("9a6ad8"))
    region(c, 0, 40, 36, 11, gold, 105, 0.07)
    for x in range(1, 36, 3):
        c.set(x, 49, hex_rgba("c0182e"))
        c.set(x, 50, hex_rgba("e83434"))
    region(c, 40, 40, 4, 3, hex_rgba("e24ac8"), 106, 0.05)
    c.set(41, 40, hex_rgba("ffd0f4"))
    return c


def _wing_layout(membrane_fn, bone, bone_hi):
    """Mesmo layout 64x32 das Asas Sombrias: osso (0,0)/(0,16), membrana (0,4)+(12,4) e ponta (0,20)+(8,20)."""
    c = Canvas(64, 32)
    c.rect(0, 0, 25, 1, bone)
    c.rect(0, 0, 25, 0, bone_hi)
    c.rect(0, 16, 17, 17, bone)
    c.rect(0, 16, 17, 16, bone_hi)
    membrane_fn(c, 0, 4, 12, 11, 61)
    membrane_fn(c, 12, 4, 12, 11, 62)
    membrane_fn(c, 0, 20, 8, 8, 63)
    membrane_fn(c, 8, 20, 8, 8, 64)
    return c


def angel_wings_texture():
    """Asas de penas brancas com reflexo dourado: fileiras de penas (riscos verticais), pontas em leque embaixo."""
    def feathers(c, x0, y0, w, h, seed):
        rnd = random.Random(seed)
        for y in range(h):
            for x in range(w):
                t = y / max(1, h - 1)
                edge = h - 1 - (1 if x % 2 else 0) - int(2 * (x / max(1, w - 1)))
                if y > edge:
                    continue
                base = mix(hex_rgba("fffaf0", 245), hex_rgba("f0e2b8", 235), t * 0.7)
                if x % 2 == 1 and y > 1:
                    base = mix(base, hex_rgba("d8c48a", 240), 0.35)   # separação entre penas
                if y < 2:
                    base = hex_rgba("ffe9a8", 250)                    # fileira dourada de cima
                n = rnd.uniform(-0.04, 0.04)
                base = lighten(base, n) if n > 0 else darken(base, -n)
                c.set(x0 + x, y0 + y, base)
    return _wing_layout(feathers, hex_rgba("e8c46a"), hex_rgba("fff2c0"))


def dragon_wings_texture():
    """Asas dracônicas em tons de cinza claro (o jogo tinge na cor da ancestralidade): membrana com nervuras."""
    def membrane(c, x0, y0, w, h, seed):
        rnd = random.Random(seed)
        for y in range(h):
            for x in range(w):
                t = y / max(1, h - 1)
                edge = h - 1 - int(abs(math.sin((x + 1) / w * math.pi * 1.5)) * 3.0)
                if y > edge:
                    continue
                base = mix(hex_rgba("f0f0f0", 235), hex_rgba("b8b8b8", 210), t)
                if x % 3 == 0 and y < edge - 1:
                    base = hex_rgba("8a8a8a", 245)
                n = rnd.uniform(-0.05, 0.05)
                base = lighten(base, n) if n > 0 else darken(base, -n)
                c.set(x0 + x, y0 + y, base)
    return _wing_layout(membrane, hex_rgba("6a6a6a"), hex_rgba("a0a0a0"))


def aura_texture():
    """64x64 quase branco com leves faixas: a casca translúcida (fantasma, geleia, auras) é tingida no jogo."""
    c = Canvas(64, 64)
    rnd = random.Random(77)
    for y in range(64):
        for x in range(64):
            v = 236 + int(14 * math.sin((y + x * 0.3) * 0.7)) + rnd.randint(-5, 5)
            v = max(200, min(255, v))
            c.set(x, y, (v, v, v, 255))
    return c


# =====================================================================================
#  v1.0.1: ícones 18x18 das Transformações (aura na cor da raça + símbolo)
# =====================================================================================
FORM_SYMBOLS = {
    "angel": ["..######..", "..#....#..", "...####...", "#........#", "##..##..##",
              "###.##.###", ".########.", "..######..", "....##....", "....##...."],
    "demon": ["#........#", "#........#", "##......##", ".##....##.", "..######..",
              "..#.##.#..", "..######..", "...####...", "...#..#...", "....##...."],
    "dragonborn": ["#.........", "##........", "###....#..", "####..###.", "#####.####",
                   "##########", ".#.#.#.###", "........##", ".........#", ".........."],
    "phantom": ["...####...", "..######..", ".########.", ".#..##..#.", ".########.",
                ".########.", ".########.", ".########.", ".##.##.##.", ".#..#...#."],
    "slime": ["..#.##.#..", "..######..", "..........", "...####...", "..######..",
              ".##.##.##.", ".########.", "##########", "##########", ".########."],
    "elf": ["........##", "......####", "....######", "...######.", "..######..",
            ".######...", ".#####....", ".####.....", "##........", "#........."],
    "dwarf": ["..........", "##########", ".#########", "..########", "....####..",
              "....####..", "...######.", "..########", "..########", ".........."],
    "half_orc": ["#........#", "##......##", ".##....##.", ".##....##.", "..##..##..",
                 "..######..", ".########.", ".########.", "..######..", "...####..."],
    "beastfolk": ["#..#..#...", "#..#..#...", ".#..#..#..", ".#..#..#..", "..#..#..#.",
                  "..#..#..#.", "...#..#..#", "...#..#..#", "..........", ".........."],
    "human": ["#...##...#", "##..##..##", "###.##.###", "##########", "##########",
              "#.#.##.#.#", "##########", "..........", "..........", ".........."],
    "arachnid": ["#...##...#", ".#.####.#.", "..######..", "####..####", "..######..",
                 ".#.####.#.", "#..####..#", "...####...", "..#....#..", ".#......#."],
}

RACE_RGB = {"human": "e8c77a", "elf": "7bd88f", "dwarf": "c98a4b", "half_orc": "8dbf5a", "demon": "d8455b",
            "dragonborn": "e0a030", "beastfolk": "d08a3a", "arachnid": "9a6ad8", "phantom": "9fd8e8",
            "angel": "ffe08a", "slime": "8fe05a"}


def effect_form(rid):
    c = Canvas(18, 18)
    col = hex_rgba(RACE_RGB[rid])
    c.circle(9, 9, 8.2, darken(col, 0.55))
    for y in range(18):
        for x in range(18):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
            if 6.6 <= d <= 8.2:
                c.set(x, y, col)
    c.from_rows(FORM_SYMBOLS[rid], {"#": lighten(col, 0.65)}, ox=4, oy=4)
    for (x, y) in ((2, 3), (15, 2), (16, 14), (1, 13)):
        c.set(x, y, lighten(col, 0.8))
    c.outline(OUT)
    return c


# =====================================================================================
#  v1.0.1: medalhões das raças novas (mesma moldura/estilo do tools/gen/icons.py) e ícones dos glifos novos
# =====================================================================================
def _icons():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import icons  # noqa: E402  (tools/gen/icons.py: a "arte" dos glifos e dos retratos)
    return icons


def portrait_arachnid(I, d):
    # Aracnídeo: pele pálida, cabelo negro, QUATRO olhos vermelhos a mais na testa, presinhas e as patas de aranha
    # saindo das costas por cima dos ombros
    for sx in (-1, 1):
        g = 1 if sx < 0 else 2
        for y0 in (11.0, 14.6):
            d.curve([(12 + sx * 4.6, y0 + 2.0), (12 + sx * 9.0, y0 - 4.6), (12 + sx * 11.6, y0 + 3.4)], 1.1, "ink", g=g)
    I.bust(d, "purple.d", hy=11.6, skin="silver", shoulders=9.4)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.2) ** 2 <= 4.9 ** 2 and y <= 8.6, "ink", g=5)
    d.tri((7.2, 8.8), (8.8, 8.8), (6.8, 15.0), "ink", g=5)
    d.tri((16.8, 8.8), (15.2, 8.8), (17.2, 15.0), "ink", g=5)
    I.face(d, hy=11.4, eyes="red", mouth=None)
    for (x, y) in ((9.4, 9.8), (14.6, 9.8)):
        d.put(x, y, "red.l", g=9)
    for (x, y) in ((11.2, 9.2), (12.8, 9.2)):
        d.put(x, y, "red", g=9)
    d.put(11, 14, "W")
    d.put(13, 14, "W")
    d.poly([(9.0, 16.4), (15.0, 16.4), (12.0, 20.0)], "violet", g=7)


def portrait_phantom(I, d):
    # Fantasma: rosto pálido-azulado sob um capuz rasgado, olhos ocos brilhando em ciano e névoa em volta
    I.bust(d, "cloud.d", hy=11.6, skin="sky.l", shoulders=9.0)
    for i in range(5):
        d.tri((5.0 + i * 3.2, 20.6), (8.0 + i * 3.2, 20.6), (6.6 + i * 3.2, 23.8), "cloud.l", g=3)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.0) ** 2 <= 5.6 ** 2 and y <= 9.2, "storm", g=5)
    d.tri((6.4, 8.8), (8.6, 8.8), (6.0, 17.0), "storm", g=5)
    d.tri((17.6, 8.8), (15.4, 8.8), (18.0, 17.0), "storm", g=5)
    I.face(d, hy=11.4, eyes="cyan.l", mouth=None)
    d.rect(11, 14, 12, 14, "sky.d")
    for (x, y) in ((3, 7), (20, 9), (2, 15), (21, 16), (19, 4)):
        d.put(x, y, "cyan.l", g=9)


def portrait_angel(I, d):
    # Anjo: auréola dourada no alto, cabelo loiro, olhos azuis, asas de penas brancas atrás dos ombros e a túnica
    # branca com o broche de ouro
    for sx in (-1, 1):
        g = 1 if sx < 0 else 2
        d.poly([(12 + sx * 4.0, 15.0), (12 + sx * 11.6, 5.6), (12 + sx * 11.4, 12.0), (12 + sx * 9.8, 16.4),
                (12 + sx * 6.0, 18.4)], "white", g=g)
        for k in range(3):
            d.line(12 + sx * (6.2 + k * 1.8), 15.6 - k * 1.4, 12 + sx * (9.8 + k * 0.6), 9.4 + k * 1.4, 0.6, "white.d", g=g)
    I.bust(d, "white", hy=11.6, shoulders=9.0)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.0) ** 2 <= 4.9 ** 2 and y <= 9.2, "yellow", g=5)
    d.tri((7.2, 9.0), (8.8, 9.0), (7.0, 13.8), "yellow", g=5)
    d.tri((16.8, 9.0), (15.2, 9.0), (17.0, 13.8), "yellow", g=5)
    I.face(d, hy=11.4, eyes="sky.d", mouth="flesh.d")
    d.fill(lambda x, y: 0.45 <= ((x - 12) / 4.6) ** 2 + ((y - 3.6) / 1.7) ** 2 <= 1.0, "gold", g=8)
    d.poly([(10.4, 16.6), (13.6, 16.6), (12.0, 19.4)], "gold", g=7)


def portrait_slime(I, d):
    # Slime: um blob de geleia verde de olhos grandes e brilho, o núcleo escuro e a coroinha de ouro torta
    d.fill(lambda x, y: ((x - 12) / 9.8) ** 2 + ((y - 17.4) / 8.6) ** 2 <= 1 and y <= 24, "grass", g=1)
    d.disc(12, 19.0, 2.2, "green.d", g=2)
    d.rect(8, 13, 9, 15, "k")
    d.rect(15, 13, 16, 15, "k")
    d.put(8, 13, "W")
    d.put(15, 13, "W")
    d.arc(12, 16.4, 2.0, 1.2, 20, 160, "green.d")
    d.ellipse(6.8, 11.6, 1.6, 0.9, "white", g=3, rot=-30)
    d.poly([(7.0, 9.0), (17.0, 9.0), (17.6, 3.6), (15.0, 6.2), (12.0, 2.4), (9.0, 6.2), (6.4, 3.6)], "gold", g=5)
    d.put(12, 6, "red")
    d.put(8.8, 7.2, "cyan")
    d.put(15.2, 7.2, "cyan")


NEW_PORTRAITS = [("arachnid", "9A6AD8", portrait_arachnid), ("phantom", "8FCFE0", portrait_phantom),
                 ("angel", "F0D070", portrait_angel), ("slime", "8FD848", portrait_slime)]

# Glifos raciais novos (v1.0.1): moldura/estilo do icons.py com o pictograma e o elemento escolhidos à mão (o chute
# por palavra-chave errava: "raio de luz" virava relâmpago, "apaga o fogo" virava fogo) e o selo de losango nas
# skills raciais. (Se alguém rodar o icons.py inteiro, ele volta ao chute — rode este script de novo depois.)
NEW_GLYPH_ICONS = {
    "race_web_shot": ("root", "earth"),
    "race_venom_fang": ("venom", "nature"),
    "race_spectral_touch": ("life_drain", "shadow"),
    "race_phase_shift": ("invisibility", "shadow"),
    "race_holy_light": ("holy_ray", "light"),
    "race_divine_shield": ("divine_shield", "light"),
    "race_acid_splash": ("bubble", "water"),
    "race_jelly_body": ("protective_bubble", "water"),
}


def make_portraits_and_glyphs():
    I = _icons()
    for rid, col, fn in NEW_PORTRAITS:
        rc = I.rgb(col)
        out, fillmask, geo = I.draw_portrait_frame("form", rc, I.RACE_METAL, rc)
        img = I.compose_portrait(out, fillmask, geo, lambda d, fn=fn: fn(I, d), rc, "arcane", ox=4, oy=5, over_top=12)
        I.save_px(img, os.path.join(A, "textures/gui/race", rid + ".png"))
    specs, _guessed, _names = I.build_specs()
    made = []
    for gid, (pic, elem) in NEW_GLYPH_ICONS.items():
        s = dict(specs.get(gid) or {"kind": "effect", "legend": False})
        if pic in I.PICS:
            s.update(pic=pic, elem=elem, badges=["b_diamond"], args={})
        I.save_px(I.make_glyph_icon(s), os.path.join(A, "textures/gui/glyph", gid + ".png"))
        made.append(f"{gid}={s['pic']}/{s['elem']}")
    return made


# =====================================================================================
#  Traducoes
# =====================================================================================
RACES = {
    # id: (pt, en, desc_pt, desc_en)
    "human": ("Humano", "Human",
              "Versáteis e teimosos, os humanos se adaptam a qualquer caminho. Toda lenda deste mundo começou com um humano que se recusou a desistir.",
              "Versatile and stubborn, humans adapt to any path. Every legend of this world began with a human who refused to give up."),
    "elf": ("Elfo", "Elf",
            "Filhos das florestas antigas, de sentidos aguçados e mira impecável. Enxergam no escuro e a lua guia as suas flechas.",
            "Children of the ancient forests, with keen senses and flawless aim. They see in the dark and the moon guides their arrows."),
    "dwarf": ("Anão", "Dwarf",
              "Baixos, robustos e teimosos como rocha. Mineradores natos e mestres da forja, enxergam nas profundezas e riem de venenos.",
              "Short, sturdy and stubborn as stone. Born miners and masters of the forge, they see in the depths and laugh at poison."),
    "half_orc": ("Meio-Orc", "Half-Orc",
                 "Sangue da Horda e dos homens. Fortes, temidos e difíceis de derrubar: quando a fúria acende, nada os para.",
                 "Blood of the Horde and of men. Strong, feared and hard to bring down: once the fury ignites, nothing stops them."),
    "demon": ("Demônio", "Demon",
              "Marcados por um pacto antigo, carregam chifres, cauda, asas de sombra e o fogo do Abismo nas veias. Encantadores... e perigosos.",
              "Marked by an ancient pact, they bear horns, a tail, shadow wings and the fire of the Abyss in their veins. Charming... and dangerous."),
    "dragonborn": ("Dracônico", "Draconic",
                   "Descendentes do sangue dracônico, com chifres, cauda, escamas na cor do elemento ancestral e um sopro devastador.",
                   "Descendants of draconic blood, with horns, a tail, scales the color of their ancestral element and a devastating breath."),
    "beastfolk": ("Bestial", "Beastfolk",
                  "Meio gente, meio fera: orelhas atentas, cauda inquieta, o faro de um caçador e o salto de um predador.",
                  "Half person, half beast: alert ears, a restless tail, a hunter's nose and a predator's leap."),
    "arachnid": ("Aracnídeo", "Arachnid",
                 "Caçadores das cavernas com olhos a mais e patas finas nas costas. Escalam paredes, tecem teias e mordem com veneno.",
                 "Cave hunters with extra eyes and thin legs on their backs. They climb walls, weave webs and bite with venom."),
    "phantom": ("Fantasma", "Phantom",
                "Uma alma que voltou com corpo de névoa: meio translúcida, leve como o vento, enxerga no escuro e deixa golpes a atravessarem.",
                "A soul that came back with a body of mist: half translucent, light as the wind, sees in the dark and lets blows pass through."),
    "angel": ("Anjo", "Angel",
              "Filhos do céu com auréola dourada e pequenas asas de penas. A luz sagrada cura os aliados e queima os mortos-vivos.",
              "Children of the sky with a golden halo and small feathered wings. Holy light heals allies and burns the undead."),
    "slime": ("Slime", "Slime",
              "Um corpo de geleia translúcida que regenera, quica e engole quase tudo. Subestimado... até virar rei.",
              "A translucent jelly body that regenerates, bounces and swallows almost anything. Underestimated... until it becomes king."),
}

# Passiva: (nome pt, nome en, [nível I..IV pt], [nível I..IV en]) — os números batem com RacePassives.java
PASSIVES = {
    "human": ("Ambição Humana", "Human Ambition",
              ["+10% de XP por abate.",
               "+12% de XP por abate e Adaptação: −15% do MESMO tipo de dano já levado nos últimos 8 s (até 3 tipos).",
               "+15% de XP por abate, Adaptação −20% e +5% de Poder Mágico.",
               "+20% de XP por abate, Adaptação −25% e +10% de Poder Mágico."],
              ["+10% XP per kill.",
               "+12% XP per kill and Adaptation: −15% from the SAME damage type taken in the last 8 s (up to 3 types).",
               "+15% XP per kill, Adaptation −20% and +5% Spell Power.",
               "+20% XP per kill, Adaptation −25% and +10% Spell Power."]),
    "elf": ("Sangue Élfico", "Elven Blood",
            ["Visão no escuro (Shift + tecla racial liga/desliga).",
             "+8% de dano com projéteis (flechas e magias de Projétil, Flecha ou Flecha Lunar) e +8% de regeneração de recurso.",
             "+12% de dano com projéteis e +12% de regeneração de recurso.",
             "+15% de dano com projéteis e +15% de regeneração de recurso."],
            ["Darkvision (Shift + racial key toggles it).",
             "+8% damage with projectiles (arrows and Projectile, Arrow or Moon Arrow spells) and +8% resource regeneration.",
             "+12% projectile damage and +12% resource regeneration.",
             "+15% projectile damage and +15% resource regeneration."]),
    "dwarf": ("Robustez Anã", "Dwarven Fortitude",
              ["Enxerga no escuro debaixo da terra e +10% de velocidade para minerar.",
               "+15% de mineração e +2 de armadura.",
               "Imune a veneno, +20% de mineração, +3 de armadura e +15% de firmeza contra empurrões.",
               "+25% de mineração, +4 de armadura e +25% de firmeza."],
              ["Sees in the dark underground and +10% mining speed.",
               "+15% mining speed and +2 armor.",
               "Immune to poison, +20% mining speed, +3 armor and +15% knockback resistance.",
               "+25% mining speed, +4 armor and +25% knockback resistance."]),
    "half_orc": ("Implacável", "Relentless",
                 ["+5% de dano corpo a corpo.",
                  "+8% de dano corpo a corpo e +4 de vida máxima.",
                  "+10% de dano corpo a corpo, +6 de vida e Implacável: 1× a cada 5 min um golpe fatal te deixa com 1 de vida. Abaixo de 35% de vida, +20% de dano corpo a corpo.",
                  "+12% de dano corpo a corpo e +8 de vida máxima."],
                 ["+5% melee damage.",
                  "+8% melee damage and +4 max health.",
                  "+10% melee damage, +6 health and Relentless: once every 5 min a fatal blow leaves you at 1 health. Below 35% health, +20% melee damage.",
                  "+12% melee damage and +8 max health."]),
    "demon": ("Sangue Infernal", "Infernal Blood",
              ["Visão no escuro e −20% de dano de fogo e lava.",
               "−35% de dano de fogo e +8% de dano com as suas magias de fogo.",
               "Metade do dano de fogo, +12% com magias de fogo, −15% de magias de fogo inimigas e Repreensão Infernal (quem te golpeia pega fogo).",
               "+15% com magias de fogo e −25% de magias de fogo inimigas."],
              ["Darkvision and −20% damage from fire and lava.",
               "−35% fire damage and +8% damage with your fire spells.",
               "Half fire damage, +12% with fire spells, −15% from enemy fire spells and Hellish Rebuke (whoever strikes you catches fire).",
               "+15% with fire spells and −25% from enemy fire spells."]),
    "dragonborn": ("Linhagem Dracônica", "Draconic Lineage",
                   ["−15% de dano de fogo e cai 1 bloco a mais sem se machucar.",
                    "+8% de dano com as suas magias do elemento da ancestralidade e −15% das magias inimigas desse elemento.",
                    "−25% de dano de fogo, +12% / −25% no elemento da ancestralidade e +2 de queda segura.",
                    "+15% com magias da ancestralidade e +2 de armadura (escamas)."],
                   ["−15% fire damage and fall 1 extra block without getting hurt.",
                    "+8% damage with your spells of your ancestral element and −15% from enemy spells of that element.",
                    "−25% fire damage, +12% / −25% in your ancestral element and +2 safe fall.",
                    "+15% with ancestral spells and +2 armor (scales)."]),
    "beastfolk": ("Sentidos Bestiais", "Bestial Senses",
                  ["+4% de velocidade e cai 1 bloco a mais sem se machucar.",
                   "+6% de velocidade e +2 blocos de queda segura.",
                   "10% de chance de esquivar golpes e projéteis (no máx. 1 a cada 2 s), +8% de velocidade e +3 de queda segura.",
                   "12% de esquiva, +10% de velocidade e +4 de queda segura."],
                  ["+4% speed and fall 1 extra block without getting hurt.",
                   "+6% speed and +2 blocks of safe fall.",
                   "10% chance to dodge hits and projectiles (at most once every 2 s), +8% speed and +3 safe fall.",
                   "12% dodge, +10% speed and +4 safe fall."]),
    "arachnid": ("Sangue Aracnídeo", "Arachnid Blood",
                 ["Visão no escuro e escala paredes DEVAGAR (ande contra a parede; Shift gruda; solte para escorregar).",
                  "Escala mais rápido e os golpes corpo a corpo envenenam (Veneno I por 2 s).",
                  "Escala ainda mais rápido e o veneno dura 3 s.",
                  "Escala rápido, Veneno II nos golpes e +5% de velocidade."],
                 ["Darkvision and climbs walls SLOWLY (walk into a wall; Shift sticks; let go to slide).",
                  "Climbs faster and melee hits poison (Poison I for 2 s).",
                  "Climbs even faster and the poison lasts 3 s.",
                  "Climbs fast, Poison II on hits and +5% speed."]),
    "phantom": ("Corpo Etéreo", "Ethereal Body",
                ["Visão no escuro e cai devagar (−15% de gravidade, +2 de queda segura). Corpo meio translúcido.",
                 "−25% de gravidade e +4 de queda segura.",
                 "10% de chance de golpes e projéteis te atravessarem (no máx. 1 a cada 2 s); −30% de gravidade.",
                 "15% de chance de atravessar, −35% de gravidade e +6 de queda segura."],
                ["Darkvision and falls slowly (−15% gravity, +2 safe fall). Half-translucent body.",
                 "−25% gravity and +4 safe fall.",
                 "10% chance for hits and projectiles to pass through you (at most once every 2 s); −30% gravity.",
                 "15% chance to phase, −35% gravity and +6 safe fall."]),
    "angel": ("Graça Celestial", "Celestial Grace",
              ["Regenera +1 de vida a cada 5 s.",
               "Regenera a cada 4 s e +10% de dano contra mortos-vivos.",
               "Regenera a cada 3 s e +20% de dano contra mortos-vivos.",
               "Regenera +2 a cada 3 s, +25% contra mortos-vivos e as asinhas seguram a queda (−15% de gravidade)."],
              ["Regenerates +1 health every 5 s.",
               "Regenerates every 4 s and +10% damage against the undead.",
               "Regenerates every 3 s and +20% damage against the undead.",
               "Regenerates +2 every 3 s, +25% against the undead and your little wings soften falls (−15% gravity)."]),
    "slime": ("Geleia Viva", "Living Jelly",
              ["Regenera +1 de vida a cada 5 s.",
               "Regenera a cada 4 s, −25% de dano de queda e quica ao cair de alto (Shift = não quica).",
               "Regenera a cada 3 s, −50% de dano de queda e quem te golpeia gruda em você (Lentidão).",
               "Regenera +2 a cada 3 s, −75% de dano de queda, +2 de armadura e +20% de firmeza."],
              ["Regenerates +1 health every 5 s.",
               "Regenerates every 4 s, −25% fall damage and bounces back when falling from high (Shift = no bounce).",
               "Regenerates every 3 s, −50% fall damage and whoever strikes you gets stuck (Slowness).",
               "Regenerates +2 every 3 s, −75% fall damage, +2 armor and +20% knockback resistance."]),
}

# Transformação (nível de raça V): (nome pt, nome en, desc pt, desc en, golpe pt, golpe en, golpe desc pt, golpe desc en)
FORMS = {
    "human": ("Despertar Heroico", "Heroic Awakening",
              "Uma aura dourada de herói: limpa os efeitos ruins, cura 30%, +25% de dano corpo a corpo e de Poder Mágico, +10 de vida, +10% de velocidade e −20% de dano recebido.",
              "A golden hero's aura: cleanses harmful effects, heals 30%, +25% melee damage and Spell Power, +10 health, +10% speed and −20% damage taken.",
              "Golpe Heroico", "Heroic Strike",
              "meia-lua dourada que acerta todos à frente (dano alto e empurrão).",
              "a golden crescent that hits everyone ahead (high damage and knockback)."),
    "elf": ("Avatar da Floresta", "Forest Avatar",
            "Coroa de folhas e aura verde: +15% de velocidade e de Poder Mágico, +50% de regeneração de recurso, +30% de dano com flechas e Regeneração para você e os aliados por perto.",
            "A leaf crown and green aura: +15% speed and Spell Power, +50% resource regeneration, +30% arrow damage and Regeneration for you and nearby allies.",
            "Chuva de Folhas", "Leaf Storm",
            "até 8 inimigos à frente (16 blocos) levam dano e ficam Enraizados.",
            "up to 8 enemies ahead (16 blocks) take damage and are Rooted."),
    "dwarf": ("Colosso de Pedra", "Stone Colossus",
              "Cresce 45% com pele de rocha: +10 de armadura, +6 de resistência, imune a empurrões, +30% de dano corpo a corpo, −30% de dano recebido e minera o dobro; um pouco mais lento.",
              "Grows 45% with rock skin: +10 armor, +6 toughness, immune to knockback, +30% melee damage, −30% damage taken and mines twice as fast; a bit slower.",
              "Terremoto", "Earthquake",
              "o chão racha num raio de 6 blocos: dano, joga para cima e Atordoa.",
              "the ground cracks in a 6-block radius: damage, launches up and Stuns."),
    "half_orc": ("Fúria Berserker", "Berserker Fury",
                 "Aura rubra: +50% de dano corpo a corpo, +25% de velocidade de ataque, +15% de velocidade e 20% do dano corpo a corpo vira vida — mas leva +10% de dano.",
                 "A crimson aura: +50% melee damage, +25% attack speed, +15% speed and 20% of melee damage heals you — but you take +10% damage.",
                 "Brado de Guerra", "War Cry",
                 "inimigos num raio de 8 ficam Fracos, Lentos e Expostos; aliados ganham Força.",
                 "enemies within 8 blocks become Weak, Slowed and Exposed; allies gain Strength."),
    "beastfolk": ("Fera Total", "Total Beast",
                  "+35% de velocidade, +30% de velocidade de ataque, +20% de dano, pulo maior, 25% de esquiva e os golpes causam Sangramento.",
                  "+35% speed, +30% attack speed, +20% damage, higher jumps, 25% dodge and your hits cause Bleeding.",
                  "Investida Feral", "Feral Pounce",
                  "salta na presa mais alinhada com o olhar (até 14 blocos) e a dilacera (dano e Sangramento).",
                  "leap at the prey most in line with your gaze (up to 14 blocks) and maul it (damage and Bleeding)."),
    "demon": ("Forma Demoníaca", "Demonic Form",
              "Asas de sombra (VOO), imune a fogo, +30% de dano corpo a corpo e +20% de Poder Mágico; os golpes queimam com chamas negras e uma aura do Abismo fere quem chega perto.",
              "Shadow wings (FLIGHT), immune to fire, +30% melee damage and +20% Spell Power; your hits burn with black flames and an Abyssal aura hurts whoever gets close.",
              "Chamas Negras", "Black Flames",
              "cone de fogo do Abismo (8 blocos): dano alto, Em Chamas e Corrompido.",
              "a cone of Abyssal fire (8 blocks): high damage, Burning and Corrupted."),
    "dragonborn": ("Forma Dracônica", "Draconic Form",
                   "Asas de dragão na cor da ancestralidade (VOO), +8 de armadura, +25% de dano corpo a corpo, 15% maior e −25% de dano recebido.",
                   "Dragon wings in your ancestry's color (FLIGHT), +8 armor, +25% melee damage, 15% larger and −25% damage taken.",
                   "Sopro Dracônico", "Draconic Breath",
                   "cone de 10 blocos no elemento da ancestralidade (fogo queima, água gela, terra petrifica, vento empurra, luz expõe, sombra corrompe).",
                   "a 10-block cone of your ancestral element (fire burns, water chills, earth petrifies, wind pushes, light exposes, shadow corrupts)."),
    "arachnid": ("Forma Aracne", "Arachne Form",
                 "Oito patas de aranha: escala paredes rápido, +20% de velocidade e de ataque, golpes com Veneno II e Lentidão e cai 12 blocos sem se machucar.",
                 "Eight spider legs: climbs walls fast, +20% speed and attack speed, hits with Poison II and Slowness and falls 12 blocks unharmed.",
                 "Tempestade de Teias", "Web Storm",
                 "teias em todos num raio de 8: dano, Enraizado, Lentidão IV e Veneno II.",
                 "webs on everyone within 8 blocks: damage, Rooted, Slowness IV and Poison II."),
    "phantom": ("Forma Espectral", "Spectral Form",
                "Vira um fantasma: invisível para as criaturas, VOO lento, golpes e projéteis passam pela metade, sem dano de queda e +15% de Poder Mágico.",
                "Become a ghost: invisible to creatures, slow FLIGHT, hits and projectiles deal half, no fall damage and +15% Spell Power.",
                "Travessia Espectral", "Spectral Passage",
                "atravessa 8 blocos em linha reta passando pelos inimigos: dano, Fraqueza e Gelado em todos no caminho.",
                "dash 8 blocks in a straight line through enemies: damage, Weakness and Chilled to everyone in the way."),
    "angel": ("Forma Seráfica", "Seraphic Form",
              "Asas de luz enormes (VOO) e auréola radiante: a cada 2 s cura você e os aliados em volta e raios sagrados caem em até 2 inimigos (o dobro em mortos-vivos); +20% de Poder Mágico e +8 de vida.",
              "Huge wings of light (FLIGHT) and a radiant halo: every 2 s heals you and nearby allies and holy rays strike up to 2 enemies (double on the undead); +20% Spell Power and +8 health.",
              "Julgamento", "Judgement",
              "explosão de luz (10 blocos): fere todos os inimigos (o dobro em mortos-vivos) e cura muito os aliados.",
              "a burst of light (10 blocks): hurts every enemy (double on the undead) and greatly heals allies."),
    "slime": ("Forma do Rei Slime", "Slime King Form",
              "Cresce 60% e ganha coroa: regeneração enorme (3+ de vida por segundo), +20 de vida, imune a empurrões e a queda, pulo maior e −15% de dano; golpes fortes soltam gosmas-clone que explodem em ácido.",
              "Grows 60% and gains a crown: huge regeneration (3+ health per second), +20 health, immune to knockback and falls, higher jumps and −15% damage; strong hits split off clone-blobs that burst into acid.",
              "Absorção", "Absorption",
              "engole os inimigos menores em volta (puxa, dano alto e cura metade do dano); os grandes levam ácido e Lentidão.",
              "engulfs the smaller enemies around you (pull, high damage and heals half the damage); big ones take acid and Slowness."),
}

# Feito da raça (nível V): o %s é a meta (RaceProgress.goal)
FEATS = {
    "human": ("Vencer %s monstros.", "Defeat %s monsters."),
    "elf": ("Abater %s criaturas à distância (flechas ou magias de projétil).", "Kill %s creatures at range (arrows or projectile spells)."),
    "dwarf": ("Minerar %s blocos abaixo de y 0.", "Mine %s blocks below y 0."),
    "half_orc": ("Abater %s criaturas corpo a corpo.", "Kill %s creatures in melee."),
    "demon": ("Abater %s criaturas com fogo ou no Nether.", "Kill %s creatures with fire or in the Nether."),
    "dragonborn": ("Abater %s criaturas com magia.", "Kill %s creatures with spells."),
    "beastfolk": ("Domar %s animais.", "Tame %s animals."),
    "arachnid": ("Emboscar %s criaturas no subterrâneo (sem céu acima).", "Ambush %s creatures underground (no sky above)."),
    "phantom": ("Abater %s criaturas à noite (ou em dimensões sem céu).", "Kill %s creatures at night (or in skyless dimensions)."),
    "angel": ("Purificar %s mortos-vivos.", "Purify %s undead."),
    "slime": ("Devorar %s comidas.", "Devour %s foods."),
}

TRAITS = {
    "human_ambition": ("Ambição Sem Limites", "Boundless Ambition",
                       "Sem fraquezas de sangue: serve para qualquer classe e qualquer caminho.",
                       "No weakness of blood: fits any class and any path."),
    "elf_darkvision": ("Visão no Escuro", "Darkvision", "Enxerga em lugares escuros (liga/desliga: Shift + tecla racial ou na aba).",
                       "See in dark places (toggle: Shift + racial key or in the tab)."),
    "elf_attunement": ("Filhos da Lua", "Children of the Moon",
                       "À noite, sob o céu aberto, a Flecha Lunar fica 20% mais forte.",
                       "At night, under the open sky, Moon Arrow is 20% stronger."),
    "dwarf_darkvision": ("Visão das Profundezas", "Deep Sight",
                         "Enxerga no escuro quando está debaixo da terra (liga/desliga: Shift + tecla racial ou na aba).",
                         "See in the dark while underground (toggle: Shift + racial key or in the tab)."),
    "dwarf_stout": ("Atarracado", "Stout", "15% mais baixo: cabe em túneis apertados.", "15% shorter: fits in tight tunnels."),
    "orc_hot_blood": ("Sangue Quente", "Hot Blood",
                      "Ferido (menos da metade da vida), o Golpe Selvagem causa +50% de dano.",
                      "Wounded (below half health), Savage Cleave deals +50% damage."),
    "orc_build": ("Corpulento", "Hulking", "6% maior.", "6% larger."),
    "demon_rebuke": ("Repreensão Infernal", "Hellish Rebuke",
                     "Do nível de raça III: quem te golpeia corpo a corpo pega fogo (no máx. 1 vez a cada 8 s).",
                     "From race level III: whoever strikes you in melee catches fire (at most once every 8 s)."),
    "demon_darkvision": ("Olhos do Abismo", "Abyssal Eyes", "Enxerga no escuro (liga/desliga: Shift + tecla racial ou na aba).",
                         "See in the dark (toggle: Shift + racial key or in the tab)."),
    "dragon_ancestry": ("Ancestralidade", "Ancestry",
                        "O sangue dracônico assume o elemento principal da sua classe: dá a cor das escamas, do sopro, das asas e das resistências.",
                        "Your draconic blood takes your class's main element: it colors your scales, your breath, your wings and your resistances."),
    "dragon_build": ("Porte Dracônico", "Draconic Frame", "8% maior.", "8% larger."),
    "beast_speed": ("Patas Ligeiras", "Swift Paws", "Fica mais rápido a cada nível de raça.", "Gets faster with every race level."),
    "beast_fall": ("Queda Felina", "Feline Fall", "Cai mais alto sem se machucar a cada nível de raça.",
                   "Falls from higher without getting hurt with every race level."),
    "arachnid_eyes": ("Olhos Múltiplos", "Many Eyes", "Quatro olhos a mais na testa: enxerga no escuro (Shift + tecla racial).",
                      "Four extra eyes on your forehead: see in the dark (Shift + racial key)."),
    "arachnid_climb": ("Escalador", "Wallcrawler",
                       "Ande contra uma parede para subir; Shift gruda; soltar escorrega devagar. Sem dano de queda enquanto estiver na parede.",
                       "Walk into a wall to climb; Shift sticks; letting go slides down slowly. No fall damage while on the wall."),
    "phantom_eyes": ("Olhos da Névoa", "Mist Eyes", "Enxerga no escuro (Shift + tecla racial ou na aba).",
                     "See in the dark (Shift + racial key or in the tab)."),
    "phantom_float": ("Leveza Espectral", "Spectral Lightness", "Corpo meio translúcido que cai devagar e solta névoa.",
                      "A half-translucent body that falls slowly and sheds mist."),
    "angel_halo": ("Auréola", "Halo", "Uma auréola dourada flutua sobre a cabeça e brilha no escuro.",
                   "A golden halo floats above your head and glows in the dark."),
    "angel_wings": ("Asinhas", "Little Wings", "Pequenas asas de penas nas costas — na Forma Seráfica viram asas de luz enormes.",
                    "Small feathered wings on your back — in Seraphic Form they become huge wings of light."),
    "slime_bounce": ("Quique", "Bounce", "Do nível de raça II: ao cair de alto você quica de volta (Shift = não quica).",
                     "From race level II: you bounce back when falling from high (Shift = no bounce)."),
    "slime_jelly": ("Geleia", "Jelly", "Corpo de geleia translúcida que balança, regenera e gruda em quem te bate.",
                    "A translucent jelly body that wobbles, regenerates and sticks to whoever hits you."),
}

GLYPHS = {
    # id: (pt, en, desc_pt, desc_en)
    "race_resolve": ("Determinação", "Resolve",
                     "Skill racial do Humano. Efeito: tira os efeitos ruins e as condições (menos a Exaustão Mágica), cura e dá Absorção. Em aliados também; com Inverter, quebra a vontade do inimigo (tira efeitos bons e o deixa Exposto).",
                     "Human racial skill. Effect: removes harmful effects and conditions (except Magic Exhaustion), heals and grants Absorption. Works on allies too; with Invert, breaks an enemy's will (strips good effects and leaves it Exposed)."),
    "race_adaptive": ("Adaptável", "Adaptive",
                      "Skill racial do Humano. Aumento: o efeito anterior causa +25% de dano, ou +50% em alvos que já estão com alguma condição ou efeito ruim.",
                      "Human racial skill. Augment: the previous effect deals +25% damage, or +50% against targets already suffering a condition or harmful effect."),
    "race_moon_arrow": ("Flecha Lunar", "Moon Arrow",
                        "Skill racial do Elfo. Forma: flecha de luar rápida que curva sozinha em direção ao inimigo à frente; à noite, sob o céu, +20% de poder. Aceita Dividir, Espalhar, Perfurar, Ricochete, Teleguiado, Fantasma e Acelerar.",
                        "Elf racial skill. Form: a swift moonlight arrow that curves on its own toward the enemy ahead; at night, under the sky, +20% power. Works with Split, Scatter, Pierce, Ricochet, Homing, Phantom and Accelerate."),
    "race_forest_step": ("Passo da Floresta", "Forest Step",
                         "Skill racial do Elfo. Efeito: em você, some num redemoinho de folhas e reaparece até 8 blocos à frente (sem atravessar paredes), fica rápido e os inimigos perdem o seu rastro. Em aliados: velocidade e regeneração. Com Inverter: raízes prendem o inimigo.",
                         "Elf racial skill. Effect: on yourself, vanish in a swirl of leaves and reappear up to 8 blocks ahead (never through walls), gain speed and enemies lose your trail. On allies: speed and regeneration. With Invert: roots hold the enemy."),
    "race_stone_skin": ("Pele de Rocha", "Stone Skin",
                        "Skill racial do Anão. Efeito: a pele vira granito por 12 s — +armadura, +firmeza e −15% de todo dano recebido, um pouco mais lento. Em aliados também; com Inverter, petrifica o inimigo.",
                        "Dwarf racial skill. Effect: skin turns to granite for 12 s — +armor, +steadiness and −15% damage taken, slightly slower. Works on allies too; with Invert, petrifies the enemy."),
    "race_forge_shout": ("Grito da Forja", "Forge Shout",
                         "Skill racial do Anão. Efeito: um brado com o calor da forja — dano de fogo, empurrão, atordoa e incendeia. Em você (Si Mesmo): conserta 10% das armas e armaduras equipadas e dá Força.",
                         "Dwarf racial skill. Effect: a roar with the forge's heat — fire damage, knockback, stun and burning. On yourself (Self): repairs 10% of your equipped weapons and armor and grants Strength."),
    "race_blood_fury": ("Fúria Sangrenta", "Blood Fury",
                        "Skill racial do Meio-Orc. Efeito: paga 10% da vida e entra em fúria por 10 s — +25% de dano corpo a corpo, ataques e passos mais rápidos e 15% do dano corpo a corpo vira vida. Com Inverter: Sangramento no inimigo.",
                        "Half-Orc racial skill. Effect: pay 10% of your health and rage for 10 s — +25% melee damage, faster attacks and steps, and 15% of melee damage heals you. With Invert: Bleeding on the enemy."),
    "race_savage_cleave": ("Golpe Selvagem", "Savage Cleave",
                           "Skill racial do Meio-Orc. Efeito: talho brutal — dano alto, Sangramento e empurrão; com menos da metade da vida, +50% de dano.",
                           "Half-Orc racial skill. Effect: a brutal gash — high damage, Bleeding and knockback; below half health, +50% damage."),
    "race_hellfire": ("Chamas Infernais", "Hellfire",
                      "Skill racial do Demônio. Efeito: fogo do Abismo — dano de fogo, Em Chamas, Corrompido e fogo por 5 s; água não apaga: alvos molhados tomam +25%.",
                      "Demon racial skill. Effect: fire of the Abyss — fire damage, Burning, Corrupted and flames for 5 s; water won't put it out: wet targets take +25%."),
    "race_shadow_wings": ("Asas Sombrias", "Shadow Wings",
                          "Skill racial do Demônio. Efeito: asas de sombra — uma batida de asa para cima e para a frente e, por 12 s, gravidade menor (dá para planar), queda segura e velocidade. Com Inverter: a sombra puxa o inimigo ao chão (Escuridão e Lentidão).",
                          "Demon racial skill. Effect: shadow wings — a wingbeat up and forward and, for 12 s, lower gravity (you can glide), safe falls and speed. With Invert: the shadow drags the enemy down (Darkness and Slowness)."),
    "race_dragon_breath": ("Sopro do Dragão", "Dragon Breath",
                           "Skill racial do Dracônico. Forma: cone de 7 blocos que sai da boca e aplica os efeitos em tudo à vista. Tem o elemento da magia (só Arcano = o da sua ancestralidade); no elemento da ancestralidade, +25% de poder.",
                           "Draconic racial skill. Form: a 7-block cone from your mouth that applies the effects to everything in sight. It takes the spell's element (pure Arcane = your ancestry's); in your ancestral element, +25% power."),
    "race_dragon_scales": ("Escamas", "Scales",
                           "Skill racial do Dracônico. Efeito: escamas dracônicas por 12 s — +armadura, −25% de dano de magias e 20% do dano corpo a corpo volta para quem bateu. Com Inverter: arranca as escamas do inimigo (Exposto).",
                           "Draconic racial skill. Effect: draconic scales for 12 s — +armor, −25% spell damage and 20% of melee damage is reflected. With Invert: tears the enemy's scales off (Exposed)."),
    "race_predator_leap": ("Salto Predador", "Predator Leap",
                           "Skill racial do Bestial. Forma: salta na presa mais alinhada com o olhar (até 14 blocos) e, ao pousar, aplica os efeitos em volta. Funciona sem efeito (só o salto); a queda não machuca.",
                           "Beastfolk racial skill. Form: leap at the prey most in line with your gaze (up to 14 blocks) and, on landing, apply the effects around you. Works with no effect (just the leap); the fall doesn't hurt."),
    "race_wild_instinct": ("Instinto Selvagem", "Wild Instinct",
                           "Skill racial do Bestial. Efeito: por 12 s, +velocidade, ataques mais rápidos e 20% de esquiva; em você, o faro marca as feras hostis num raio de 20 blocos. Com Inverter: assusta o inimigo (Fraqueza e Lentidão).",
                           "Beastfolk racial skill. Effect: for 12 s, +speed, faster attacks and 20% dodge; on yourself, your nose marks hostile beasts within 20 blocks. With Invert: frightens the enemy (Weakness and Slowness)."),
    # ---- v1.0.1 ----
    "race_web_shot": ("Disparo de Teia", "Web Shot",
                      "Skill racial do Aracnídeo. Efeito: um novelo de teia gruda no alvo — dano leve, Enraizado e Lentidão forte. Em você (Si Mesmo): um fio de seda segura a queda (Queda Lenta).",
                      "Arachnid racial skill. Effect: a ball of web sticks to the target — light damage, Rooted and strong Slowness. On yourself (Self): a silk thread breaks your fall (Slow Falling)."),
    "race_venom_fang": ("Presa Venenosa", "Venom Fang",
                        "Skill racial do Aracnídeo. Efeito: mordida venenosa — dano, Veneno II e Fraqueza; num alvo já envenenado, +40% de dano. Em você (Si Mesmo): suga o próprio veneno.",
                        "Arachnid racial skill. Effect: a venomous bite — damage, Poison II and Weakness; against an already poisoned target, +40% damage. On yourself (Self): draws out your own poison."),
    "race_spectral_touch": ("Toque Espectral", "Spectral Touch",
                            "Skill racial do Fantasma. Efeito: a mão fria de um espírito atravessa o alvo — dano de sombra, Gelado e Fraqueza; 25% do dano volta como vida para você.",
                            "Phantom racial skill. Effect: a spirit's cold hand passes through the target — shadow damage, Chilled and Weakness; 25% of the damage heals you."),
    "race_phase_shift": ("Desvanecer", "Phase Shift",
                         "Skill racial do Fantasma. Efeito: em você, vira névoa — Invisibilidade e Velocidade por 5 s, intangível por 1 s e desliza até 6 blocos à frente atravessando criaturas (nunca paredes). Em aliados: Invisibilidade e Velocidade. Com Inverter: Cegueira e Exposto no inimigo.",
                         "Phantom racial skill. Effect: on yourself, turn into mist — Invisibility and Speed for 5 s, intangible for 1 s and glide up to 6 blocks ahead through creatures (never walls). On allies: Invisibility and Speed. With Invert: Blindness and Exposed on the enemy."),
    "race_holy_light": ("Luz Sagrada", "Holy Light",
                        "Skill racial do Anjo. Efeito: um raio de luz do céu — dano de luz (o DOBRO em mortos-vivos) e Brilho, e você se cura com 15% do dano. Em você (Si Mesmo): cura e apaga o fogo.",
                        "Angel racial skill. Effect: a ray of light from the sky — light damage (DOUBLE on the undead) and Glowing, and you heal for 15% of the damage. On yourself (Self): heals and puts out fire."),
    "race_divine_shield": ("Égide Divina", "Divine Aegis",
                           "Skill racial do Anjo. Efeito: asas de luz protegem o alvo — Absorção (mais forte com Amplificar), Resistência por 6 s e apaga o fogo. Em aliados também; com Inverter, a luz expõe o inimigo (Brilho, Exposto e Fraqueza).",
                           "Angel racial skill. Effect: wings of light shield the target — Absorption (stronger with Amplify), Resistance for 6 s and puts out fire. Works on allies too; with Invert, the light exposes the enemy (Glowing, Exposed and Weakness)."),
    "race_acid_splash": ("Gosma Ácida", "Acid Splash",
                         "Skill racial do Slime. Efeito: uma bolha de gosma corrosiva — dano, Exposto (o ácido come a armadura), Molhado e Lentidão.",
                         "Slime racial skill. Effect: a blob of corrosive goo — damage, Exposed (the acid eats armor), Soaked and Slowness."),
    "race_jelly_body": ("Corpo Gelatinoso", "Jelly Body",
                        "Skill racial do Slime. Efeito: o corpo vira geleia — Regeneração II por 5 s, Resistência por 10 s e um quique para cima. Em aliados também; com Inverter, a geleia prende o inimigo (Enraizado e Lentidão forte).",
                        "Slime racial skill. Effect: your body turns to jelly — Regeneration II for 5 s, Resistance for 10 s and a bounce upward. Works on allies too; with Invert, the jelly pins the enemy (Rooted and strong Slowness)."),
}

EFFECTS = {
    "race_stone_skin": ("Pele de Rocha", "Stone Skin"),
    "race_blood_fury": ("Fúria Sangrenta", "Blood Fury"),
    "race_dragon_scales": ("Escamas Dracônicas", "Draconic Scales"),
    "race_wild_instinct": ("Instinto Selvagem", "Wild Instinct"),
    "race_shadow_wings": ("Asas Sombrias", "Shadow Wings"),
}
for _rid, _f in FORMS.items():
    EFFECTS["race_form_" + _rid] = (_f[0], _f[1])

# Reliquias Raciais: raca -> (id do item, nome pt, nome en, descricao pt, descricao en). Os ids casam com
# RaceRelicItem.itemId (Java).
RELICS = {
    "human": ("relic_human_emblem", "Emblema Humano", "Human Emblem",
              "O brasão de uma antiga casa real. Guardado em guildas e nos baús das cidades.",
              "The crest of an old royal house. Kept in guilds and city chests."),
    "elf": ("relic_elven_tear", "Lágrima Élfica", "Elven Tear",
            "Uma lágrima de luar cristalizada. Fadas, lagos encantados e círculos druídicos às vezes a guardam.",
            "A crystallized tear of moonlight. Fairies, enchanted lakes and druid circles sometimes keep one."),
    "dwarf": ("relic_dwarven_rune", "Runa Anã", "Dwarven Rune",
              "Uma runa de forja ainda quente. Os kobolds a roubam dos anões e a escondem nas tocas.",
              "A forge rune, still warm. Kobolds steal them from dwarves and hide them in their dens."),
    "half_orc": ("relic_orc_fang", "Presa Orc", "Orc Fang",
                 "A presa de um campeão orc. Orcs às vezes a carregam; o Senhor Orc sempre.",
                 "The tusk of an orc champion. Orcs sometimes carry one; the Orc Lord always does."),
    "demon": ("relic_demon_horn", "Chifre Demoníaco", "Demonic Horn",
              "Um chifre ainda em brasa. Patronos demoníacos e o Trono do Abismo o guardam.",
              "A horn still smoldering. Demonic patrons and the Abyss Throne keep them."),
    "dragonborn": ("relic_dragon_scale", "Escama Dracônica", "Draconic Scale",
                   "Uma escama ancestral. O Rei Wyvern e o Dragão Primordial sempre deixam uma.",
                   "An ancestral scale. The Wyvern King and the Primordial Dragon always leave one."),
    "beastfolk": ("relic_beast_claw", "Garra Bestial", "Beast Claw",
                  "Garras de um predador alfa num cordão. A Mantícora sempre; lobos terríveis e homens-lagarto às vezes.",
                  "An alpha predator's claws on a cord. The Manticore always; dire wolves and lizardmen sometimes."),
    "arachnid": ("relic_arachne_silk", "Seda de Aracne", "Arachne Silk",
                 "Um carretel de seda que nunca arrebenta. Aranhas, aranhas de caverna, rastejantes do vazio e baús de minas abandonadas às vezes o guardam.",
                 "A spool of silk that never snaps. Spiders, cave spiders, void crawlers and abandoned mineshaft chests sometimes keep one."),
    "phantom": ("relic_phantom_ectoplasm", "Ectoplasma Antigo", "Ancient Ectoplasm",
                "Um frasco de névoa que sussurra. Phantoms e vexes às vezes o soltam; o Lich sempre, e a Cripta Esquecida o guarda.",
                "A flask of whispering mist. Phantoms and vexes sometimes drop one; the Lich always does, and the Forgotten Crypt keeps them."),
    "angel": ("relic_seraph_feather", "Pluma Seráfica", "Seraphic Feather",
              "Uma pena dourada que ainda brilha. O Serafim sempre deixa uma; sentinelas angelicais, querubins e o santuário celestial às vezes.",
              "A golden feather that still glows. The Seraph always leaves one; angel sentinels, cherubs and the celestial sanctuary sometimes."),
    "slime": ("relic_slime_core", "Núcleo Real de Slime", "Royal Slime Core",
              "O coração de geleia de um rei. O Rei Slime sempre o deixa; slimes e cubos de magma, raramente.",
              "A king's jelly heart. The King Slime always leaves one; slimes and magma cubes, rarely."),
}

KINDS = {"form": ("Forma", "Form"), "effect": ("Efeito", "Effect"), "augment": ("Aumento", "Augment"),
         "trigger": ("Gatilho", "Trigger"), "law": ("Lei", "Law")}

_RELIC_NAMES_PT = ", ".join(v[1] for v in RELICS.values())
_RELIC_NAMES_EN = ", ".join(v[2] for v in RELICS.values())

PT = {
    "block.isekai_mahou.reincarnation_altar": "Altar da Reencarnação",
    "block.isekai_mahou.reincarnation_altar.tooltip": "Renasça com outra raça (a passiva e as skills raciais trocam junto).",
    "block.isekai_mahou.reincarnation_altar.tooltip2": "Custo: %s Cristais de Mana por renascimento.",
    "key.isekai_mahou.racial_ability": "Habilidade racial (Transformação / Shift: visão)",
    "character.isekai_mahou.congrats_race": "Raça: %s",
    "screen.isekai_mahou.character.title": "Escolha a sua Raça",
    "screen.isekai_mahou.character.altar_title": "Altar da Reencarnação",
    "screen.isekai_mahou.character.tab": "Raça",
    "screen.isekai_mahou.character.races": "Raças",
    "screen.isekai_mahou.character.confirm": "Confirmar",
    "screen.isekai_mahou.character.reborn": "Renascer",
    "screen.isekai_mahou.character.footer_race": "Raça escolhida: %s",
    "screen.isekai_mahou.character.all_free": "Todas as raças estão liberadas — escolha a que combina com você.",
    "screen.isekai_mahou.character.altar_cost": "Custo: %s %s (você tem %s)",
    "screen.isekai_mahou.character.altar_same": "Esta já é a sua raça.",
    "screen.isekai_mahou.character.note": "Toda raça começa fraca e cresce nos níveis de raça I–V até a Transformação.",
    "screen.isekai_mahou.character.altar_note": "Renascer troca a raça: a passiva e as skills raciais mudam junto (os feitos ficam).",
    "screen.isekai_mahou.character.tab_note": "Depois de confirmar, só dá para trocar num Altar da Reencarnação.",
    "screen.isekai_mahou.character.race_levels_tag": "Níveis de raça I–V",
    "screen.isekai_mahou.character.passive": "Passiva (cresce com o nível de raça)",
    "screen.isekai_mahou.character.skills": "Skills raciais (glifos do Grimório)",
    "screen.isekai_mahou.character.skills_note": "A 1ª skill vem com o sangue; a 2ª desperta no nível %s (nível de raça II). Monte-as no Grimório com qualquer forma, efeito ou aumento.",
    "screen.isekai_mahou.character.skills_tab_note": "Monte as skills raciais no Grimório (menu %s → Grimório). Outras raças só as aprendem com a Relíquia Racial desta raça.",
    "screen.isekai_mahou.character.skill_known": "aprendida",
    "screen.isekai_mahou.character.skill_level": "nível %s",
    "screen.isekai_mahou.character.skill_known_tip": "Você já conhece esta skill: monte-a no Grimório.",
    "screen.isekai_mahou.character.skill_level_tip": "Desperta no nível %s (com esta raça).",
    "screen.isekai_mahou.character.skill_origin": "Origem racial: %s",
    "screen.isekai_mahou.character.skill_cost": "Custo do glifo: +%s de mana (o Grimório mostra o custo final)",
    "screen.isekai_mahou.character.level_req": "nível %s",
    "screen.isekai_mahou.character.level_reached": "nível %s ✔",
    "screen.isekai_mahou.character.level_req_feat": "nível %s + feito da raça",
    "screen.isekai_mahou.character.form_title": "Transformação:",
    "screen.isekai_mahou.character.form_ready_tag": "liberada",
    "screen.isekai_mahou.character.form_locked_tag": "nível de raça V",
    "screen.isekai_mahou.character.form_requirement": "Transformação (nível de raça V): nível %s + feito da raça — %s",
    "screen.isekai_mahou.character.form_timing": "Dura %s s · recarga de %s min. Transformado, a tecla racial usa o golpe da forma.",
    "screen.isekai_mahou.character.form_feat": "Feito: %s (%s/%s)",
    "screen.isekai_mahou.character.traits": "Traços",
    "screen.isekai_mahou.character.ancestry": "Ancestralidade: %s (o elemento principal da sua classe).",
    "screen.isekai_mahou.character.build": "Escolher Raça",
    "screen.isekai_mahou.character.darkvision_on": "Visão no escuro: ligada",
    "screen.isekai_mahou.character.darkvision_off": "Visão no escuro: desligada",
    "screen.isekai_mahou.character.unbuilt": "Sem raça",
    "screen.isekai_mahou.character.unbuilt_desc": "Você ainda não escolheu a sua raça. Escolha agora para ganhar a passiva, as skills raciais e o caminho até a Transformação!",
    "screen.isekai_mahou.character.level": "Nível %s",
    "screen.isekai_mahou.character.race_level": "Nível de raça %s",
    "screen.isekai_mahou.character.race_level_max": "Nível de raça máximo: a Transformação está liberada!",
    "screen.isekai_mahou.character.next_level": "Nível de raça %s no nível %s (você está no %s).",
    "screen.isekai_mahou.character.next_form": "Nível de raça V: nível %s (você está no %s) + feito: %s (%s/%s).",
    "screen.isekai_mahou.character.form_state_locked": "Transformação: %s (nível de raça V)",
    "screen.isekai_mahou.character.form_state_active": "%s ativa: %s s",
    "screen.isekai_mahou.character.form_state_cooldown": "%s recarregando: %s:%s",
    "screen.isekai_mahou.character.form_state_ready": "%s pronta! Aperte a tecla racial.",
    "screen.isekai_mahou.character.ancestry_short": "Ancestral: %s",
    "screen.isekai_mahou.character.rebirths": "Renascimentos: %s",
    "screen.isekai_mahou.character.key_vision": "liga/desliga a visão no escuro",
    "screen.isekai_mahou.character.key_none": "a Transformação chega no nível de raça V — as skills ficam no Grimório",
    "screen.isekai_mahou.character.key_form": "Transformação (transformado: golpe da forma)",
    "screen.isekai_mahou.character.key_vision_shift": "Shift + tecla racial: visão no escuro.",
    "screen.isekai_mahou.character.progress": "Progresso racial",
    "screen.isekai_mahou.character.unlock_1": "Passiva + 1ª skill",
    "screen.isekai_mahou.character.unlock_2": "Passiva II + 2ª skill",
    "screen.isekai_mahou.character.unlock_passive": "Passiva mais forte",
    "screen.isekai_mahou.character.feat": "Feito da raça (nível V)",
    "screen.isekai_mahou.character.feat_note": "Os feitos contam para todas as raças, mesmo antes de renascer.",
    "screen.isekai_mahou.character.altar_info": "Trocar de raça: Altar da Reencarnação (receita: Totem da Imortalidade, 2 Cristais de Mana, Obsidiana Chorona e 3 Ardósia Polida).",
    "message.isekai_mahou.character.altar_cost": "O renascimento custa %s %s.",
    "message.isekai_mahou.character.altar_far": "Você se afastou do Altar da Reencarnação.",
    "message.isekai_mahou.character.same_race": "Você já é %s.",
    "message.isekai_mahou.character.created": "Raça escolhida: %s!",
    "message.isekai_mahou.character.dodge": "Esquiva!",
    "message.isekai_mahou.character.phase": "Atravessou!",
    "message.isekai_mahou.character.mitosis": "Mitose! Uma gosma-clone se solta e explode em ácido!",
    "message.isekai_mahou.character.legacy_title": "✦ Novidade: escolha a sua raça! ✦",
    "message.isekai_mahou.character.legacy_body": "Cada raça tem uma passiva que cresce, skills raciais montáveis no Grimório e uma Transformação. Abra o menu (%s) → aba Raça para escolher — sem refazer a classe.",
    "message.isekai_mahou.character.not_created": "Escolha a sua raça primeiro (menu → aba Raça).",
    "message.isekai_mahou.character.reborn": "Você renasceu como %s!",
    "message.isekai_mahou.character.relentless": "Implacável! Você se recusa a cair.",
    "message.isekai_mahou.character.altar_no_class": "Escolha a sua classe antes de renascer.",
    "message.isekai_mahou.character.darkvision_on": "Visão no escuro ligada",
    "message.isekai_mahou.character.darkvision_off": "Visão no escuro desligada",
    "message.isekai_mahou.character.skill_awakened": "✦ Skill racial despertada: %s! Monte-a no Grimório (menu %s).",
    "message.isekai_mahou.character.skills_forgotten": "Com o sangue antigo se foram as skills: %s (as aprendidas com Relíquias Raciais ficam).",
    "message.isekai_mahou.character.skill_wrong_race": "%s é uma skill racial de %s: só a Relíquia Racial dessa raça pode ensiná-la a você.",
    "message.isekai_mahou.character.skills_in_grimoire": "A sua raça não tem visão no escuro — as skills raciais estão no Grimório (menu %s).",
    "message.isekai_mahou.character.race_level": "✦ %s — nível de raça %s! ✦",
    "message.isekai_mahou.character.race_level_gain": "A passiva cresceu: %s",
    "message.isekai_mahou.character.form_awakened": "✦ TRANSFORMAÇÃO DESPERTOU: %s! Aperte %s para se transformar (transformado, a mesma tecla usa o golpe da forma).",
    "message.isekai_mahou.character.feat_done": "✦ Feito racial cumprido (%s): %s",
    "message.isekai_mahou.character.form_locked": "%s desperta no nível de raça V (nível %s + o feito da raça). Veja a aba Raça.",
    "message.isekai_mahou.character.form_cooldown": "%s recarregando: %s s.",
    "message.isekai_mahou.character.form_start": "%s!",
    "message.isekai_mahou.character.form_power_hint": "Transformado! %s: aperte %s.",
    "message.isekai_mahou.character.form_power_cooldown": "%s recarregando: %s s.",
    "message.isekai_mahou.character.form_end": "%s acabou. Recarga: %s min %s s.",
    "commands.isekai_mahou.character.reset": "Raça apagada de %s jogador(es).",
    "commands.isekai_mahou.character.rebuild": "Troca de raça grátis aberta para %s jogador(es).",
    "commands.isekai_mahou.character.unknown_race": "Raça desconhecida: %s",
    "commands.isekai_mahou.character.feat": "Feito racial cumprido para %s jogador(es).",
    "commands.isekai_mahou.character.form": "Recarga da Transformação zerada para %s jogador(es).",
    "toast.isekai_mahou.character.skill": "Skill racial despertada!",
    "toast.isekai_mahou.character.race_level": "Nível de raça!",
    "toast.isekai_mahou.character.form": "Transformação despertou!",
    "toast.isekai_mahou.character.legacy": "Escolha a sua Raça!",
    "toast.isekai_mahou.character.legacy_sub": "Menu (B) → aba Raça",
    "overlay.isekai_mahou.character.awakens": "O SANGUE DESPERTA",
    "overlay.isekai_mahou.character.form": "TRANSFORMAÇÃO DESPERTOU",
    "overlay.isekai_mahou.character.form_key": "Aperte %s para se transformar",
    "overlay.isekai_mahou.character.level_sub": "A passiva da raça ficou mais forte — veja a aba Raça",
    "toast.isekai_mahou.character.relic": "Relíquia Racial absorvida!",
    "message.isekai_mahou.character.relic_learned": "✦ A relíquia se desfaz em luz: você aprendeu %s, skill racial de %s! Monte-a no Grimório (menu %s). Ela é sua para sempre.",
    "message.isekai_mahou.character.relic_same_race": "Você já carrega esta linhagem (%s): as skills vêm com o sangue. Agache + clique direito para converter a relíquia em XP.",
    "message.isekai_mahou.character.relic_xp": "A relíquia se desfaz em experiência: +%s XP.",
    "message.isekai_mahou.character.relic_all_known": "Você já domina todas as skills de %s.",
    "message.isekai_mahou.character.relic_level": "A relíquia resiste: %s exige nível %s.",
    "tooltip.isekai_mahou.character.relic_race": "Relíquia Racial: %s",
    "tooltip.isekai_mahou.character.relic_teaches": "Ensina a quem é de OUTRA raça (em ordem):",
    "tooltip.isekai_mahou.character.relic_skill_level": " — nível %s",
    "tooltip.isekai_mahou.character.relic_use": "Clique direito: aprende a próxima skill que você não conhece (consome a relíquia).",
    "tooltip.isekai_mahou.character.relic_forever": "Skills de relíquia são suas para sempre — nem renascer as tira.",
    "tooltip.isekai_mahou.character.relic_same": "Já é desta raça? Agache + clique direito: +%s XP.",
    "screen.isekai_mahou.character.foreign_skills": "Skills de outras raças",
    "screen.isekai_mahou.character.foreign_none": "Nenhuma ainda.",
    "screen.isekai_mahou.character.foreign_skill_tip": "Aprendida com uma Relíquia Racial: é sua para sempre.",
    "screen.isekai_mahou.character.relics_note": "Skills de outras raças: Relíquias Raciais (" + _RELIC_NAMES_PT + "). Caem de criaturas, chefes e baús ligados a cada raça.",
    "screen.isekai_mahou.character.relic_line": "Skills de outras raças: Relíquias Raciais. A desta raça é: %s.",
}

EN = {
    "block.isekai_mahou.reincarnation_altar": "Altar of Reincarnation",
    "block.isekai_mahou.reincarnation_altar.tooltip": "Be reborn as another race (your passive and racial skills change with it).",
    "block.isekai_mahou.reincarnation_altar.tooltip2": "Cost: %s Mana Crystals per rebirth.",
    "key.isekai_mahou.racial_ability": "Racial ability (Transformation / Shift: vision)",
    "character.isekai_mahou.congrats_race": "Race: %s",
    "screen.isekai_mahou.character.title": "Choose your Race",
    "screen.isekai_mahou.character.altar_title": "Altar of Reincarnation",
    "screen.isekai_mahou.character.tab": "Race",
    "screen.isekai_mahou.character.races": "Races",
    "screen.isekai_mahou.character.confirm": "Confirm",
    "screen.isekai_mahou.character.reborn": "Be reborn",
    "screen.isekai_mahou.character.footer_race": "Chosen race: %s",
    "screen.isekai_mahou.character.all_free": "Every race is available — pick the one that suits you.",
    "screen.isekai_mahou.character.altar_cost": "Cost: %s %s (you have %s)",
    "screen.isekai_mahou.character.altar_same": "This is already your race.",
    "screen.isekai_mahou.character.note": "Every race starts weak and grows through race levels I–V up to its Transformation.",
    "screen.isekai_mahou.character.altar_note": "Rebirth changes your race: the passive and racial skills change with it (deeds are kept).",
    "screen.isekai_mahou.character.tab_note": "Once confirmed, it can only be changed at an Altar of Reincarnation.",
    "screen.isekai_mahou.character.race_levels_tag": "Race levels I–V",
    "screen.isekai_mahou.character.passive": "Passive (grows with race level)",
    "screen.isekai_mahou.character.skills": "Racial skills (Grimoire glyphs)",
    "screen.isekai_mahou.character.skills_note": "The 1st skill comes with your blood; the 2nd awakens at level %s (race level II). Assemble them in the Grimoire with any form, effect or augment.",
    "screen.isekai_mahou.character.skills_tab_note": "Assemble your racial skills in the Grimoire (menu %s → Grimoire). Other races can only learn them with this race's Racial Relic.",
    "screen.isekai_mahou.character.skill_known": "learned",
    "screen.isekai_mahou.character.skill_level": "level %s",
    "screen.isekai_mahou.character.skill_known_tip": "You already know this skill: assemble it in the Grimoire.",
    "screen.isekai_mahou.character.skill_level_tip": "Awakens at level %s (with this race).",
    "screen.isekai_mahou.character.skill_origin": "Racial origin: %s",
    "screen.isekai_mahou.character.skill_cost": "Glyph cost: +%s mana (the Grimoire shows the final cost)",
    "screen.isekai_mahou.character.level_req": "level %s",
    "screen.isekai_mahou.character.level_reached": "level %s ✔",
    "screen.isekai_mahou.character.level_req_feat": "level %s + race deed",
    "screen.isekai_mahou.character.form_title": "Transformation:",
    "screen.isekai_mahou.character.form_ready_tag": "unlocked",
    "screen.isekai_mahou.character.form_locked_tag": "race level V",
    "screen.isekai_mahou.character.form_requirement": "Transformation (race level V): level %s + race deed — %s",
    "screen.isekai_mahou.character.form_timing": "Lasts %s s · %s min cooldown. While transformed, the racial key uses the form strike.",
    "screen.isekai_mahou.character.form_feat": "Deed: %s (%s/%s)",
    "screen.isekai_mahou.character.traits": "Traits",
    "screen.isekai_mahou.character.ancestry": "Ancestry: %s (your class's main element).",
    "screen.isekai_mahou.character.build": "Choose Race",
    "screen.isekai_mahou.character.darkvision_on": "Darkvision: on",
    "screen.isekai_mahou.character.darkvision_off": "Darkvision: off",
    "screen.isekai_mahou.character.unbuilt": "No race",
    "screen.isekai_mahou.character.unbuilt_desc": "You haven't chosen your race yet. Choose now to gain its passive, racial skills and the path to its Transformation!",
    "screen.isekai_mahou.character.level": "Level %s",
    "screen.isekai_mahou.character.race_level": "Race level %s",
    "screen.isekai_mahou.character.race_level_max": "Max race level: your Transformation is unlocked!",
    "screen.isekai_mahou.character.next_level": "Race level %s at level %s (you are level %s).",
    "screen.isekai_mahou.character.next_form": "Race level V: level %s (you are level %s) + deed: %s (%s/%s).",
    "screen.isekai_mahou.character.form_state_locked": "Transformation: %s (race level V)",
    "screen.isekai_mahou.character.form_state_active": "%s active: %s s",
    "screen.isekai_mahou.character.form_state_cooldown": "%s recharging: %s:%s",
    "screen.isekai_mahou.character.form_state_ready": "%s ready! Press the racial key.",
    "screen.isekai_mahou.character.ancestry_short": "Ancestry: %s",
    "screen.isekai_mahou.character.rebirths": "Rebirths: %s",
    "screen.isekai_mahou.character.key_vision": "toggles darkvision",
    "screen.isekai_mahou.character.key_none": "the Transformation comes at race level V — your skills are in the Grimoire",
    "screen.isekai_mahou.character.key_form": "Transformation (transformed: form strike)",
    "screen.isekai_mahou.character.key_vision_shift": "Shift + racial key: darkvision.",
    "screen.isekai_mahou.character.progress": "Race progress",
    "screen.isekai_mahou.character.unlock_1": "Passive + 1st skill",
    "screen.isekai_mahou.character.unlock_2": "Passive II + 2nd skill",
    "screen.isekai_mahou.character.unlock_passive": "Stronger passive",
    "screen.isekai_mahou.character.feat": "Race deed (level V)",
    "screen.isekai_mahou.character.feat_note": "Deeds count for every race, even before rebirth.",
    "screen.isekai_mahou.character.altar_info": "To change race: Altar of Reincarnation (recipe: Totem of Undying, 2 Mana Crystals, Crying Obsidian and 3 Polished Deepslate).",
    "message.isekai_mahou.character.altar_cost": "Rebirth costs %s %s.",
    "message.isekai_mahou.character.altar_far": "You moved away from the Altar of Reincarnation.",
    "message.isekai_mahou.character.same_race": "You already are %s.",
    "message.isekai_mahou.character.created": "Race chosen: %s!",
    "message.isekai_mahou.character.dodge": "Dodge!",
    "message.isekai_mahou.character.phase": "Phased through!",
    "message.isekai_mahou.character.mitosis": "Mitosis! A clone-blob splits off and bursts into acid!",
    "message.isekai_mahou.character.legacy_title": "✦ New: choose your race! ✦",
    "message.isekai_mahou.character.legacy_body": "Each race has a growing passive, racial skills you assemble in the Grimoire and a Transformation. Open the menu (%s) → Race tab to choose — without redoing your class.",
    "message.isekai_mahou.character.not_created": "Choose your race first (menu → Race tab).",
    "message.isekai_mahou.character.reborn": "You were reborn as %s!",
    "message.isekai_mahou.character.relentless": "Relentless! You refuse to fall.",
    "message.isekai_mahou.character.altar_no_class": "Choose your class before being reborn.",
    "message.isekai_mahou.character.darkvision_on": "Darkvision on",
    "message.isekai_mahou.character.darkvision_off": "Darkvision off",
    "message.isekai_mahou.character.skill_awakened": "✦ Racial skill awakened: %s! Assemble it in the Grimoire (menu %s).",
    "message.isekai_mahou.character.skills_forgotten": "With your old blood went its skills: %s (those learned from Racial Relics stay).",
    "message.isekai_mahou.character.skill_wrong_race": "%s is a racial skill of the %s: only that race's Racial Relic can teach it to you.",
    "message.isekai_mahou.character.skills_in_grimoire": "Your race has no darkvision — your racial skills are in the Grimoire (menu %s).",
    "message.isekai_mahou.character.race_level": "✦ %s — race level %s! ✦",
    "message.isekai_mahou.character.race_level_gain": "Your passive grew: %s",
    "message.isekai_mahou.character.form_awakened": "✦ TRANSFORMATION AWAKENED: %s! Press %s to transform (while transformed, the same key uses the form strike).",
    "message.isekai_mahou.character.feat_done": "✦ Race deed complete (%s): %s",
    "message.isekai_mahou.character.form_locked": "%s awakens at race level V (level %s + the race deed). See the Race tab.",
    "message.isekai_mahou.character.form_cooldown": "%s recharging: %s s.",
    "message.isekai_mahou.character.form_start": "%s!",
    "message.isekai_mahou.character.form_power_hint": "Transformed! %s: press %s.",
    "message.isekai_mahou.character.form_power_cooldown": "%s recharging: %s s.",
    "message.isekai_mahou.character.form_end": "%s ended. Cooldown: %s min %s s.",
    "commands.isekai_mahou.character.reset": "Race erased for %s player(s).",
    "commands.isekai_mahou.character.rebuild": "Free race change opened for %s player(s).",
    "commands.isekai_mahou.character.unknown_race": "Unknown race: %s",
    "commands.isekai_mahou.character.feat": "Race deed completed for %s player(s).",
    "commands.isekai_mahou.character.form": "Transformation cooldown reset for %s player(s).",
    "toast.isekai_mahou.character.skill": "Racial skill awakened!",
    "toast.isekai_mahou.character.race_level": "Race level up!",
    "toast.isekai_mahou.character.form": "Transformation awakened!",
    "toast.isekai_mahou.character.legacy": "Choose your Race!",
    "toast.isekai_mahou.character.legacy_sub": "Menu (B) → Race tab",
    "overlay.isekai_mahou.character.awakens": "YOUR BLOOD AWAKENS",
    "overlay.isekai_mahou.character.form": "TRANSFORMATION AWAKENED",
    "overlay.isekai_mahou.character.form_key": "Press %s to transform",
    "overlay.isekai_mahou.character.level_sub": "Your racial passive grew stronger — see the Race tab",
    "toast.isekai_mahou.character.relic": "Racial Relic absorbed!",
    "message.isekai_mahou.character.relic_learned": "✦ The relic dissolves into light: you learned %s, a racial skill of the %s! Assemble it in the Grimoire (menu %s). It is yours forever.",
    "message.isekai_mahou.character.relic_same_race": "You already carry this bloodline (%s): its skills come with your blood. Sneak + right click to turn the relic into XP.",
    "message.isekai_mahou.character.relic_xp": "The relic dissolves into experience: +%s XP.",
    "message.isekai_mahou.character.relic_all_known": "You already master every skill of the %s.",
    "message.isekai_mahou.character.relic_level": "The relic resists: %s requires level %s.",
    "tooltip.isekai_mahou.character.relic_race": "Racial Relic: %s",
    "tooltip.isekai_mahou.character.relic_teaches": "Teaches those of ANOTHER race (in order):",
    "tooltip.isekai_mahou.character.relic_skill_level": " — level %s",
    "tooltip.isekai_mahou.character.relic_use": "Right click: learn the next skill you don't know (consumes the relic).",
    "tooltip.isekai_mahou.character.relic_forever": "Relic skills are yours forever — not even rebirth takes them.",
    "tooltip.isekai_mahou.character.relic_same": "Already of this race? Sneak + right click: +%s XP.",
    "screen.isekai_mahou.character.foreign_skills": "Skills of other races",
    "screen.isekai_mahou.character.foreign_none": "None yet.",
    "screen.isekai_mahou.character.foreign_skill_tip": "Learned from a Racial Relic: yours forever.",
    "screen.isekai_mahou.character.relics_note": "Skills of other races: Racial Relics (" + _RELIC_NAMES_EN + "). They drop from creatures, bosses and chests tied to each race.",
    "screen.isekai_mahou.character.relic_line": "Skills of other races: Racial Relics. This race's is: %s.",
}


def lang():
    pt, en = dict(PT), dict(EN)
    for rid, (npt, nen, dpt, den) in RACES.items():
        pt[f"race.isekai_mahou.{rid}"] = npt
        en[f"race.isekai_mahou.{rid}"] = nen
        pt[f"race.isekai_mahou.{rid}.desc"] = dpt
        en[f"race.isekai_mahou.{rid}.desc"] = den
    for rid, (npt, nen, lpt, len_) in PASSIVES.items():
        pt[f"character.isekai_mahou.passive.{rid}"] = npt
        en[f"character.isekai_mahou.passive.{rid}"] = nen
        for i in range(4):
            pt[f"character.isekai_mahou.passive.{rid}.lv{i + 1}"] = lpt[i]
            en[f"character.isekai_mahou.passive.{rid}.lv{i + 1}"] = len_[i]
    for rid, (npt, nen, dpt, den, ppt, pen, pdpt, pden) in FORMS.items():
        k = f"character.isekai_mahou.form.{rid}"
        pt[k], en[k] = npt, nen
        pt[k + ".desc"], en[k + ".desc"] = dpt, den
        pt[k + ".power"], en[k + ".power"] = ppt, pen
        pt[k + ".power_desc"], en[k + ".power_desc"] = pdpt, pden
    for rid, (fpt, fen) in FEATS.items():
        pt[f"character.isekai_mahou.feat.{rid}"] = fpt
        en[f"character.isekai_mahou.feat.{rid}"] = fen
    for tid, (npt, nen, dpt, den) in TRAITS.items():
        pt[f"character.isekai_mahou.trait.{tid}"] = npt
        en[f"character.isekai_mahou.trait.{tid}"] = nen
        pt[f"character.isekai_mahou.trait.{tid}.desc"] = dpt
        en[f"character.isekai_mahou.trait.{tid}.desc"] = den
    for gid, (npt, nen, dpt, den) in GLYPHS.items():
        pt[f"glyph.isekai_mahou.{gid}"] = npt
        en[f"glyph.isekai_mahou.{gid}"] = nen
        pt[f"glyph.isekai_mahou.{gid}.desc"] = dpt
        en[f"glyph.isekai_mahou.{gid}.desc"] = den
    for eid, (npt, nen) in EFFECTS.items():
        pt[f"effect.isekai_mahou.{eid}"] = npt
        en[f"effect.isekai_mahou.{eid}"] = nen
    for rid, (iid, npt, nen, dpt, den) in RELICS.items():
        pt[f"item.isekai_mahou.{iid}"] = npt
        en[f"item.isekai_mahou.{iid}"] = nen
        pt[f"item.isekai_mahou.{iid}.desc"] = dpt
        en[f"item.isekai_mahou.{iid}.desc"] = den
    for kid, (npt, nen) in KINDS.items():
        pt[f"screen.isekai_mahou.character.kind.{kid}"] = npt
        en[f"screen.isekai_mahou.character.kind.{kid}"] = nen
    # release 1.0: as chaves novas (altares, santuarios) moram no character_release.py — junta para nao perde-las
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import character_release
    ept, een = character_release.extra_lang()
    pt.update(ept)
    en.update(een)
    assert set(pt) == set(en), set(pt) ^ set(en)
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))
    return len(pt)


# Arquivos que SAIRAM (atributos e os emblemas 16x16 da v0.9: a v1.0 usa textures/gui/race/<id>.png da arte)
OBSOLETE = ["attr_strength", "attr_dexterity", "attr_constitution", "attr_intelligence", "attr_wisdom", "attr_charisma",
            "race_human", "race_elf", "race_dwarf", "race_half_orc", "race_demon", "race_dragonborn", "race_beastfolk"]


def main():
    for name in OBSOLETE:
        p = gui(name)
        if os.path.exists(p):
            os.remove(p)
    icon_lock().save(gui("lock"))
    icon_tab().save(os.path.join(A, "textures/gui/tab/character.png"))
    race_features().save(os.path.join(A, "textures/entity/character/race_features.png"))
    race_features2().save(os.path.join(A, "textures/entity/character/race_features2.png"))
    shadow_wings_texture().save(os.path.join(A, "textures/entity/character/shadow_wings.png"))
    angel_wings_texture().save(os.path.join(A, "textures/entity/character/angel_wings.png"))
    dragon_wings_texture().save(os.path.join(A, "textures/entity/character/dragon_wings.png"))
    aura_texture().save(os.path.join(A, "textures/entity/character/aura.png"))
    for eid, fn in EFFECT_ICONS.items():
        fn().save(os.path.join(A, "textures/mob_effect", eid + ".png"))
    for rid in FORMS:
        effect_form(rid).save(os.path.join(A, "textures/mob_effect", "race_form_" + rid + ".png"))

    for name, canvas in altar_textures().items():
        canvas.save(os.path.join(A, "textures/block", name + ".png"))
    write_json(os.path.join(A, "models/block/reincarnation_altar.json"), altar_model())
    write_json(os.path.join(A, "models/item/reincarnation_altar.json"), {"parent": f"{NS}:block/reincarnation_altar"})
    write_json(os.path.join(A, "blockstates/reincarnation_altar.json"),
               {"variants": {"": {"model": f"{NS}:block/reincarnation_altar"}}})
    write_json(os.path.join(D, "loot_table/blocks/reincarnation_altar.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:reincarnation_altar"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/reincarnation_altar"})
    write_json(os.path.join(D, "recipe/reincarnation_altar.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" T ", "MCM", "DDD"],
        "key": {"T": {"item": "minecraft:totem_of_undying"}, "M": {"item": f"{NS}:mana_crystal"},
                "C": {"item": "minecraft:crying_obsidian"}, "D": {"item": "minecraft:polished_deepslate"}},
        "result": {"id": f"{NS}:reincarnation_altar", "count": 1}})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"replace": False, "values": [f"{NS}:reincarnation_altar", f"{NS}:new_beginnings_altar"]})
    for rid, (iid, *_rest) in RELICS.items():
        RELIC_ART[rid]().save(os.path.join(A, "textures/item", iid + ".png"))
        item_model(A, iid)
    n = lang()
    made = make_portraits_and_glyphs()   # depois das traduções: o icons.py lê os nomes/descrições dos glifos novos
    print(f"character: assets gerados, {n} chaves de tradução; glifos novos: {', '.join(made)}")


if __name__ == "__main__":
    main()
