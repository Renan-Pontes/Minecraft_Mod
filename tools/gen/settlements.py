"""Gera os assets do módulo "settlements" (v1.0): CONTROLE DE CIDADES, CIDADE PRÓPRIA E GUERRA.

Uso (na raiz do projeto):  py -I tools/gen/settlements.py

Gera:
  - modelo GeckoLib dos NPCs das cidades dos jogadores (geo/entity/settlement_npc.geo.json + animações idle/walk/run/
    work/talk/guard/wave/attack/cheer/bow) e as texturas <papel>_<estilo>_<variante> (construtor, morador, guarda,
    mercador, miliciano × Reino/Desértico/Nórdico);
  - Campeão da Cidade (geo/animações intro/idle/walk/attack/attack2/slam/charge/spin/roar + textura por reino);
  - Coração da cidade (pedestal + cristal do renderer), Carta de Fundação, Selo do Juramento, ovos, ícone da aba e as
    artes dos 3 estilos da tela de fundação; receita da Carta;
  - os moldes .nbt das plantas (tools/gen/settlements_build.py) e as traduções src/main/lang/settlements/.
Os moldes e as traduções são conferidos contra o Java do módulo no fim (chaves usadas, moldes que o Java pede).
"""
import json
import math
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
import cities as cz  # noqa: E402  (pintores e construtor de modelos dos NPCs das cidades)

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/settlements")
JAVA = os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/settlements")
NS = "isekai_mahou"
T = (0, 0, 0, 0)
H = cz.H
Geo = cz.Geo
px = cz.px
jitter = cz.jitter
write_json = cz.write_json

STYLES = ["kingdom", "desert", "nordic"]


# =================================================================================================
#  Pintores extras (chifres, espada, machado)
# =================================================================================================
def horn(color="EDE3C8"):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = mix(color, hex_rgba("8A7A5A"), fy / max(1, fh - 1) * 0.5) if name not in ("top", "bottom") else color
                if name == "top":
                    col = lighten(color, 0.2)
                if fy % 2 == 0 and name in ("front", "back"):
                    col = darken(col, 0.08)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def blade(metal="D8DEE6"):
    metal = H(metal)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(metal, 0.3) if fx == 0 else metal
                if fy == 0:
                    col = hex_rgba("FFFFFF")
                if name in ("left", "right"):
                    col = darken(metal, 0.15)
                c.set(x, y, jitter(col, rnd, 0.03))
    return p


def hilt(wrap="3A2A1E", guard="E8B84A"):
    wrap, guard = H(wrap), H(guard)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = wrap if fy % 2 else darken(wrap, 0.2)
                if fy == fh - 1:
                    col = guard
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


# =================================================================================================
#  NPCs das cidades dos jogadores
# =================================================================================================
def npc_geo():
    g = Geo("settlement_npc")
    g.bone("root")
    g.bone("body", "root", (0, 24, 0))
    g.cube("body", (-4, 12, -2), (8, 12, 4), "body")
    g.bone("head", "body", (0, 24, 0))
    g.cube("head", (-4, 24, -4), (8, 8, 8), "head")
    g.cube("head", (-4, 24, -4), (8, 8, 8), "hair", 0.5)
    g.bone("hair_long", "head", (0, 27, 4))
    g.cube("hair_long", (-4.5, 17, 3.6), (9, 10, 2), "hair_long")
    g.bone("beard", "head", (0, 24, -4))
    g.cube("beard", (-3.5, 21, -5), (7, 5, 1), "beard")
    g.bone("hood", "head", (0, 24, 0))
    g.cube("hood", (-4, 24, -4), (8, 8, 8), "hood", 0.75)
    g.cube("hood", (-4.5, 18, 3.5), (9, 6, 1), "hood_drape")
    g.bone("helmet", "head", (0, 24, 0))
    g.cube("helmet", (-4, 24, -4), (8, 8, 8), "helmet", 1.0)
    g.cube("helmet", (-5.5, 26, -5.5), (11, 1, 11), "helmet_rim")
    g.bone("plume", "head", (0, 33, 0))
    g.cube("plume", (-0.5, 33, -3), (1, 4, 7), "plume")
    g.bone("horns", "head", (0, 30, 0))
    g.cube("horns", (-7, 29, -1), (2, 2, 2), "horn")
    g.cube("horns", (-8, 31, -1), (2, 3, 2), "horn")
    g.cube("horns", (5, 29, -1), (2, 2, 2), "horn")
    g.cube("horns", (6, 31, -1), (2, 3, 2), "horn")
    g.bone("cap", "head", (0, 32, 0))
    g.cube("cap", (-4.5, 31.6, -4.5), (9, 2, 9), "cap_top")
    g.cube("cap", (-4, 31.6, -7.5), (8, 1, 3), "cap_brim")
    g.bone("right_arm", "body", (-5, 22, 0))
    g.cube("right_arm", (-8, 12, -2), (4, 12, 4), "arm_r")
    g.bone("left_arm", "body", (5, 22, 0))
    g.cube("left_arm", (4, 12, -2), (4, 12, 4), "arm_l")
    g.bone("spear", "right_arm", (-6, 13, 0))
    g.cube("spear", (-6.5, 0, -0.5), (1, 36, 1), "spear_shaft")
    g.cube("spear", (-7, 36, -1), (2, 5, 2), "spear_tip")
    g.cube("spear", (-7.5, 33, -1.5), (3, 1, 3), "spear_guard")
    g.bone("hammer", "right_arm", (-6, 12, 0))
    g.cube("hammer", (-6.5, 4, -0.5), (1, 9, 1), "hammer_handle")
    g.cube("hammer", (-7.5, 1, -2), (3, 3, 4), "hammer_head")
    # espada e machado apontando para a frente (como segurados), saindo do punho
    g.bone("sword", "right_arm", (-6, 12, 0))
    g.cube("sword", (-6.5, 11, -1), (1, 3, 1), "sword_grip")
    g.cube("sword", (-7.5, 10, -2.5), (3, 1, 4), "sword_guard")
    g.cube("sword", (-6.5, 10, -13), (1, 1, 10), "sword_blade")
    g.bone("axe", "right_arm", (-6, 12, 0))
    g.cube("axe", (-6.5, 11, -12), (1, 1, 12), "axe_handle")
    g.cube("axe", (-7, 8.5, -12.5), (2, 5, 3), "axe_head")
    g.bone("shield", "left_arm", (8, 17, 0))
    g.cube("shield", (8, 9, -4), (1, 11, 8), "shield")
    g.bone("pauldron_r", "right_arm", (-5, 22, 0))
    g.cube("pauldron_r", (-9, 21, -3), (6, 4, 6), "pauldron")
    g.bone("pauldron_l", "left_arm", (5, 22, 0))
    g.cube("pauldron_l", (3, 21, -3), (6, 4, 6), "pauldron")
    g.bone("cape", "body", (0, 24, 2))
    g.cube("cape", (-4.5, 4, 2.1), (9, 20, 1), "cape")
    g.bone("apron", "body", (0, 21, -2))
    g.cube("apron", (-3.5, 6, -2.8), (7, 15, 1), "apron")
    g.bone("skirt", "body", (0, 12, 0))
    g.cube("skirt", (-4.5, 1, -2.5), (9, 12, 5), "skirt")
    g.bone("satchel", "body", (4, 13, 0))
    g.cube("satchel", (4.2, 8, -1.5), (2, 4, 3), "satchel")
    g.bone("right_leg", "root", (-1.9, 12, 0))
    g.cube("right_leg", (-3.9, 0, -2), (4, 12, 4), "leg")
    g.bone("left_leg", "root", (1.9, 12, 0))
    g.cube("left_leg", (-0.1, 0, -2), (4, 12, 4), "leg")
    g.pack()
    return g


def kf(*pairs):
    return cz.kf(*pairs)


def npc_animations():
    base = cz.animations()["animations"]
    a = {}
    for k, v in base.items():
        a[k.replace("animation.city_npc.", "animation.settlement_npc.")] = v
    p = "animation.settlement_npc"
    # guarda em posição de sentido: arma em pé, respiração lenta, olhar que varre a rua
    a[f"{p}.guard"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"rotation": kf((0, [0, 0, 0]), (2.0, [1, 0, 0]), (4.0, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (1.0, [0, 14, 0]), (2.0, [0, 0, 0]), (3.0, [0, -14, 0]), (4.0, [0, 0, 0]))},
        "right_arm": {"rotation": kf((0, [-14, 0, 2]), (2.0, [-16, 0, 2]), (4.0, [-14, 0, 2]))},
        "left_arm": {"rotation": kf((0, [-8, 0, -4]), (2.0, [-10, 0, -4]), (4.0, [-8, 0, -4]))},
        "cape": {"rotation": kf((0, [3, 0, 0]), (2.0, [5, 0, 0]), (4.0, [3, 0, 0]))},
        "plume": {"rotation": kf((0, [0, 0, 0]), (2.0, [-5, 0, 0]), (4.0, [0, 0, 0]))},
    }}
    # o construtor martela mais forte (o martelo desce no bloco)
    a[f"{p}.work"] = {"loop": True, "animation_length": 0.9, "bones": {
        "right_arm": {"rotation": kf((0, [-70, 0, 0]), (0.3, [-150, 0, 6]), (0.45, [-50, 0, 0]), (0.55, [-58, 0, 0]),
                                     (0.9, [-70, 0, 0]))},
        "left_arm": {"rotation": kf((0, [-40, 0, -10]), (0.45, [-36, 0, -10]), (0.9, [-40, 0, -10]))},
        "head": {"rotation": kf((0, [18, 0, 0]), (0.45, [24, 0, 0]), (0.9, [18, 0, 0]))},
        "body": {"rotation": kf((0, [6, 0, 0]), (0.3, [2, 0, 0]), (0.45, [10, 0, 0]), (0.9, [6, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": a}


SKIN = ["F2CFB0", "E0AC85", "C98E64", "A86E48", "7A4A30", "F5D9C0"]
STYLE_COL = {"kingdom": "3F6FD8", "desert": "E0902A", "nordic": "6FB8E0"}
STYLE_TRIM = {"kingdom": "E8B84A", "desert": "F0E0B0", "nordic": "EDEFF5"}


def base_kit():
    kit = cz.base_kit()
    kit.update({"horn": horn(), "sword_grip": hilt(), "sword_guard": cz.metal("E8B84A", rivets=False),
                "sword_blade": blade(), "axe_handle": cz.wood_rod("6B4A2E"), "axe_head": cz.metal("8E99A8", rivets=False)})
    return kit


def person(skin, hair, style, eye, shirt_p, sleeve, pants, boots, female=False, **kw):
    kit = base_kit()
    kit.update({
        "head": cz.head(skin, hair, style, eye=eye, lashes=female, blush=female, **kw),
        "hair": cz.hair_overlay(hair, style),
        "hair_long": cz.long_hair(hair, braid=female),
        "beard": cz.beard(darken(H(hair), 0.05)),
        "body": shirt_p,
        "arm_r": cz.arm(sleeve, skin, sleeve_rows=8),
        "arm_l": cz.arm(sleeve, skin, sleeve_rows=8),
        "leg": cz.leg(pants, boots),
    })
    return kit


def look(role, style, v):
    """Pintores de uma variante (variantes ímpares = mulheres, como no Java)."""
    female = v % 2 == 1
    rnd = random.Random(f"{role}/{style}/{v}")
    skin = rnd.choice(SKIN if style != "nordic" else ["F5D9C0", "F2CFB0", "E0AC85"])
    if style == "desert":
        skin = rnd.choice(["C98E64", "A86E48", "7A4A30", "E0AC85"])
    hair = rnd.choice({"kingdom": ["5A3A22", "3A2A22", "C97A3A", "1E1A1A", "8A6A4A"],
                       "desert": ["1E1A1A", "141010", "3A2A22", "4A2A1A"],
                       "nordic": ["E8D8A0", "D9C07A", "C9612E", "B07030", "EDE3C8"]}[style])
    eye = rnd.choice(["3A6BC9", "4A6B3A", "5A3A22", "3A8ACF"])
    hstyle = "long" if female else rnd.choice(["short", "short", "bald"] if role != "resident" else ["short"])
    col, trim = STYLE_COL[style], STYLE_TRIM[style]
    if role == "builder":
        shirt = {"kingdom": "8E5A3A", "desert": "E8D8B0", "nordic": "6B4A3A"}[style]
        kit = person(skin, hair, hstyle, eye, cz.body(shirt, belt="3A2A1E", buckle="9AA4B0"), shirt,
                     {"kingdom": "4A4A5A", "desert": "C9A27A", "nordic": "4A3A2E"}[style], "3E2E22",
                     female=female, stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=4, glove="5A3E26")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=4, glove="5A3E26")
        kit["apron"] = cz.apron({"kingdom": "A57A4A", "desert": "B98A5A", "nordic": "5A3E26"}[style], pocket="3A2618",
                                stains=True)
        kit["cap_top"] = cz.cap_top({"kingdom": "E8B84A", "nordic": "8A6A4A", "desert": "D9A040"}[style], band_c=col)
        kit["cap_brim"] = cz.solid(darken(H({"kingdom": "E8B84A", "nordic": "8A6A4A", "desert": "D9A040"}[style]), 0.2))
        kit["hood"] = cz.hood("D9A040", trim="F0E0B0")
        kit["hood_drape"] = cz.solid("C99030")
        kit["satchel"] = cz.satchel("7A5636", "4A3220")
        kit["hammer_head"] = cz.metal("6E7782")
        return kit
    if role == "resident":
        shirt = rnd.choice({"kingdom": ["5A6BA8", "3E6B35", "8E5A3A", "D9A040"],
                            "desert": ["F0E8D8", "E8D8B0", "D9C07A", "C9A27A"],
                            "nordic": ["6B3A2A", "3A4A6B", "4A6B5A", "8A6A4A"]}[style])
        kit = person(skin, hair, hstyle, eye, cz.body(shirt, vest=rnd.choice([None, "5A3E26", "3A3A4A"]) if not female else None,
                                                      belt="3A2A1E" if not female else None,
                                                      sash=trim if female else None,
                                                      collar=lighten(H(shirt), 0.3) if female else None),
                     shirt, rnd.choice(["4A4A5A", "5A4632", "3A3A4A"]), "3E2E22", female=female,
                     freckles=rnd.random() < 0.3)
        dress = rnd.choice({"kingdom": ["7A4AA0", "8A2B2B", "2E6A8A", "B05A8A"],
                            "desert": ["1E7A7A", "C9433A", "6B3FA0", "E0902A"],
                            "nordic": ["2E4A7A", "7A2A2A", "4A6B3A", "5A3E26"]}[style])
        kit["skirt"] = cz.skirt(dress, trim, pattern=lighten(H(dress), 0.25))
        if female:
            kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=6, cuff=trim)
            kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=6, cuff=trim)
        hood_c = rnd.choice(["1E7A7A", "E0902A", "C9433A", "F0E8D8"])
        kit["hood"] = cz.hood(hood_c, trim="E8B84A")
        kit["hood_drape"] = cz.solid(darken(H(hood_c), 0.1))
        kit["satchel"] = cz.satchel(rnd.choice(["7A5636", "5A3E26", "8A6A4A"]), "3A2A1E")
        return kit
    if role == "guard":
        metal = {"kingdom": "A8B2BE", "desert": "D9A84A", "nordic": "7A8088"}[style]
        kit = person(skin, hair, hstyle, eye, cz.body({"kingdom": "2B3A67", "desert": "F0E8D8", "nordic": "4A3A2E"}[style],
                                                      armor=metal, tabard=col, tabard_trim=trim, emblem=trim,
                                                      belt="3A2A1E"),
                     metal, {"kingdom": "2B3A67", "desert": "C9A27A", "nordic": "4A3A2E"}[style], "4A3A2E",
                     female=female, stubble=not female, scar=v == 0 and style == "nordic")
        kit["arm_r"] = cz.arm(metal, skin, sleeve_rows=9, glove="4A3A2E", bracer=metal)
        kit["arm_l"] = cz.arm(metal, skin, sleeve_rows=9, glove="4A3A2E", bracer=metal)
        kit["leg"] = cz.leg({"kingdom": "2B3A67", "desert": "C9A27A", "nordic": "4A3A2E"}[style], "4A3A2E", boot_rows=5,
                            greave=metal)
        kit["helmet"] = cz.helmet(metal, trim=trim if style != "nordic" else "4A4A54")
        kit["helmet_rim"] = cz.metal(darken(H(metal), 0.1), rivets=False)
        kit["plume"] = cz.plume(["C9433A", "3F6FD8"][v % 2])
        kit["pauldron"] = cz.metal(metal)
        kit["shield"] = cz.shield({"kingdom": "3F6FD8", "desert": "E0902A", "nordic": "8A6A4A"}[style],
                                  {"kingdom": "8E99A8", "desert": "D9A84A", "nordic": "5A5A64"}[style], trim)
        kit["cape"] = cz.cape(col, darken(H(col), 0.4), trim=trim) if style != "nordic" else \
            cz.cape("8A7A6A", "5A4A3A", trim="EDEFF5", ermine=True)
        kit["hood"] = cz.hood("F0E8D8", trim="E0902A")
        kit["hood_drape"] = cz.solid("E8DCC4")
        kit["spear_tip"] = cz.metal("D8DEE6", rivets=False)
        kit["axe_head"] = cz.metal("9AA4B0", rivets=False)
        return kit
    if role == "merchant":
        vest = {"kingdom": "3E6B35", "desert": "6B3FA0", "nordic": "5A3E26"}[style]
        shirt = {"kingdom": "F0E8D8", "desert": "E8D8B0", "nordic": "8A6A4A"}[style]
        kit = person(skin, hair, hstyle, eye, cz.body(shirt, vest=vest, buttons="E8B84A", belt="3A2A1E", collar="FFFFFF"),
                     shirt, "3A3A4A", "2A1E16", female=female)
        kit["apron"] = cz.apron({"kingdom": "D9CFB8", "desert": "E8D8B0", "nordic": "6B5A4A"}[style], pocket="E8B84A")
        kit["cap_top"] = cz.cap_top(col, band_c=trim)
        kit["cap_brim"] = cz.solid(darken(H(col), 0.25))
        kit["hood"] = cz.hood("6B3FA0", trim="E8B84A")
        kit["hood_drape"] = cz.solid("4A2A70")
        kit["skirt"] = cz.skirt("6B3FA0", "E8B84A", pattern="FFD86B")
        kit["satchel"] = cz.satchel("E8B84A" if style == "desert" else "7A5636", "3A2A1E")
        return kit
    # militia
    gambeson = {"kingdom": "8A2B2B", "desert": "C9A27A", "nordic": "5A4A3A"}[style]
    metal = {"kingdom": "9AA4B0", "desert": "C9A23A", "nordic": "6E7782"}[style]
    kit = person(skin, hair, hstyle, eye, cz.body(gambeson, straps="3A2A1E", belt="2A1E16", buckle="9AA4B0",
                                                  emblem=col),
                 gambeson, "4A3A2E", "3A2A1E", female=female, stubble=not female, scar=v == 1)
    kit["arm_r"] = cz.arm(gambeson, skin, sleeve_rows=7, glove="3A2A1E")
    kit["arm_l"] = cz.arm(gambeson, skin, sleeve_rows=7, glove="3A2A1E")
    kit["helmet"] = cz.helmet(metal, trim="4A4A54")
    kit["pauldron"] = cz.metal(metal)
    kit["shield"] = cz.shield({"kingdom": "C9433A", "desert": "1E7A7A", "nordic": "6B4A2E"}[style], metal, col)
    kit["hood"] = cz.hood("C9A27A", trim="8A2B2B")
    kit["hood_drape"] = cz.solid("B08A5A")
    kit["sword_blade"] = blade("C8CED6")
    return kit


VARIANTS = {"builder": 2, "resident": 4, "guard": 2, "merchant": 2, "militia": 2}


def npc_files():
    g = npc_geo()
    write_json(os.path.join(A, "geo/entity/settlement_npc.geo.json"), g.to_json(bounds=(3, 4)))
    write_json(os.path.join(A, "animations/entity/settlement_npc.animation.json"), npc_animations())
    out = os.path.join(A, "textures/entity/settlement_npc")
    n = 0
    for role, count in VARIANTS.items():
        for si, style in enumerate(STYLES):
            for v in range(count):
                g.paint(look(role, style, v), 7000 + n).save(os.path.join(out, f"{role}_{style}_{v}.png"))
                n += 1
    return n


# =================================================================================================
#  Campeão da Cidade (2,6 blocos, armadura de placas, espadão, escudo, capa)
# =================================================================================================
def champion_geo():
    g = Geo("city_champion", 128, 128)
    g.bone("root")
    g.bone("body", "root", (0, 22, 0))
    g.cube("body", (-6, 22, -3.5), (12, 16, 7), "torso")
    g.cube("body", (-6.5, 16, -4), (13, 6, 8), "tasset")
    g.cube("body", (-3, 30, -4.5), (6, 6, 1), "emblem")
    g.bone("head", "body", (0, 38, 0))
    g.cube("head", (-4, 38, -4), (8, 9, 8), "helm", 0.25)
    g.cube("head", (-0.5, 47, -5), (1, 4, 10), "crest")
    g.cube("head", (-3, 41, -4.8), (6, 1, 1), "visor")
    g.bone("cape", "body", (0, 37, 3.5))
    g.cube("cape", (-6, 8, 3.6), (12, 29, 1), "cape")
    g.bone("right_arm", "body", (-7, 35, 0))
    g.cube("right_arm", (-11, 19, -2.5), (5, 17, 5), "arm")
    g.cube("right_arm", (-12.5, 32, -4), (8, 6, 8), "pauldron")
    g.bone("greatsword", "right_arm", (-8.5, 20, 0))
    g.cube("greatsword", (-9, 17, -1), (1, 5, 2), "gs_grip")
    g.cube("greatsword", (-10, 22, -3.5), (3, 1, 7), "gs_guard")
    g.cube("greatsword", (-9, 23, -1.5), (1, 26, 3), "gs_blade")
    g.cube("greatsword", (-9.5, 15.5, -1.5), (2, 2, 3), "gs_pommel")
    g.bone("left_arm", "body", (7, 35, 0))
    g.cube("left_arm", (6, 19, -2.5), (5, 17, 5), "arm")
    g.cube("left_arm", (4.5, 32, -4), (8, 6, 8), "pauldron")
    g.bone("shield", "left_arm", (11, 26, 0))
    g.cube("shield", (11, 16, -6), (2, 18, 12), "shield")
    g.bone("right_leg", "root", (-3, 17, 0))
    g.cube("right_leg", (-6, 0, -3), (6, 17, 6), "legs")
    g.bone("left_leg", "root", (3, 17, 0))
    g.cube("left_leg", (0, 0, -3), (6, 17, 6), "legs")
    g.pack()
    return g


def plate(base, trim, accent):
    base, trim, accent = H(base), H(trim), H(accent)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = base
                if name == "top":
                    col = lighten(base, 0.2)
                elif name == "bottom":
                    col = darken(base, 0.35)
                else:
                    if fy == 0 or fy == fh - 1:
                        col = trim
                    elif fy % 5 == 4:
                        col = darken(base, 0.18)
                    if name == "front" and fh > 10 and abs(fx - (fw - 1) / 2) < 1 and 2 < fy < fh - 2:
                        col = lighten(base, 0.25)
                    if (fx in (1, fw - 2)) and fy % 5 == 2:
                        col = hex_rgba("EDEFF5")
                    if name == "front" and fh <= 8 and fw >= 10 and fy in (2, 3):
                        col = accent
                c.set(x, y, jitter(col, rnd, 0.035))
    return p


def helm(base, trim, eye_glow):
    base, trim, glow = H(base), H(trim), H(eye_glow)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = base
                if name == "top":
                    col = lighten(base, 0.2)
                    if fx in (3, 4):
                        col = trim
                elif name == "bottom":
                    col = darken(base, 0.5)
                elif name == "front":
                    if fy == 3 and 1 <= fx <= fw - 2:
                        col = hex_rgba("101014")
                        if fx in (2, fw - 3):
                            col = glow
                    elif fy >= 5 and fx % 2 == 0 and 2 <= fx <= fw - 3:
                        col = darken(base, 0.35)
                    elif fx in (3, 4) and fy < 3:
                        col = trim
                else:
                    if fy == 2:
                        col = trim
                c.set(x, y, jitter(col, rnd, 0.035))
    return p


def champion_painters(style):
    if style == "desert":
        base, trim, accent, cloth, lining, eye = "D9A84A", "F0E0B0", "1E7A7A", "F0E8D8", "1E7A7A", "7FF0E8"
    elif style == "nordic":
        base, trim, accent, cloth, lining, eye = "6E7782", "C9D6E0", "6FB8E0", "8A7A6A", "3A4A6B", "9FE8FF"
    else:
        base, trim, accent, cloth, lining, eye = "B8C0CA", "E8B84A", "3F6FD8", "3F6FD8", "1E3570", "FFE38A"
    crest = {"kingdom": "C9433A", "desert": "1E7A7A", "nordic": "EDEFF5"}[style]
    return {
        "torso": plate(base, trim, accent), "tasset": plate(darken(H(base), 0.08), trim, accent),
        "emblem": cz.solid(accent, 0.02, shade=False), "helm": helm(base, trim, eye), "crest": cz.plume(crest),
        "visor": cz.solid("101014", 0.0, shade=False),
        "cape": cz.cape(cloth, lining, trim=trim, emblem=trim, ermine=style == "nordic"),
        "arm": plate(base, trim, accent), "pauldron": cz.metal(lighten(H(base), 0.05)),
        "gs_grip": hilt("2A1E16", trim), "gs_guard": cz.metal(trim, rivets=False), "gs_blade": blade("E2E8F0"),
        "gs_pommel": cz.gem(accent),
        "shield": cz.shield(accent if style != "nordic" else "6B4A2E", trim, trim if style != "nordic" else accent),
        "legs": plate(darken(H(base), 0.05), trim, accent),
    }


def champion_animations():
    p = "animation.city_champion"
    a = {
        f"{p}.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (1.5, [2, 0, 0]), (3.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (1.5, [-14, 0, 6]), (3.0, [-10, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-18, 0, -6]), (1.5, [-20, 0, -8]), (3.0, [-18, 0, -6]))},
            "cape": {"rotation": kf((0, [4, 0, 0]), (1.5, [8, 0, 0]), (3.0, [4, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (1.5, [-3, 0, 0]), (3.0, [0, 0, 0]))},
        }},
        f"{p}.walk": {"loop": True, "animation_length": 1.2, "bones": {
            "right_leg": {"rotation": kf((0, [26, 0, 0]), (0.6, [-26, 0, 0]), (1.2, [26, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-26, 0, 0]), (0.6, [26, 0, 0]), (1.2, [-26, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-24, 0, 4]), (0.6, [-6, 0, 4]), (1.2, [-24, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-10, 0, -6]), (0.6, [-28, 0, -6]), (1.2, [-10, 0, -6]))},
            "body": {"rotation": kf((0, [3, 4, 0]), (0.6, [3, -4, 0]), (1.2, [3, 4, 0])),
                     "position": kf((0, [0, 0, 0]), (0.3, [0, -0.6, 0]), (0.6, [0, 0, 0]), (0.9, [0, -0.6, 0]), (1.2, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [12, 0, 0]), (0.6, [20, 0, 0]), (1.2, [12, 0, 0]))},
        }},
        # ajoelhado com a espada fincada → levanta e ergue a espada (2 s, = os 40 ticks de entrada do Java)
        f"{p}.intro": {"loop": False, "animation_length": 2.0, "bones": {
            "root": {"position": kf((0, [0, -7, 0]), (0.9, [0, -7, 0]), (1.3, [0, 0, 0]), (2.0, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [-80, 0, 0]), (0.9, [-80, 0, 0]), (1.3, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [80, 0, 0]), (0.9, [80, 0, 0]), (1.3, [0, 0, 0]))},
            "body": {"rotation": kf((0, [18, 0, 0]), (0.9, [18, 0, 0]), (1.3, [-8, 0, 0]), (2.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [25, 0, 0]), (0.9, [25, 0, 0]), (1.4, [-15, 0, 0]), (2.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-55, 0, 0]), (0.9, [-55, 0, 0]), (1.4, [-175, 0, 10]), (1.8, [-175, 0, 10]),
                                         (2.0, [-10, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-40, 0, -10]), (1.3, [-20, 0, -30]), (2.0, [-18, 0, -6]))},
        }},
        f"{p}.attack": {"loop": False, "animation_length": 0.7, "bones": {
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.25, [-170, 0, 10]), (0.4, [-30, 0, 0]), (0.7, [-10, 0, 4]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.4, [14, 0, 0]), (0.7, [0, 0, 0]))},
        }},
        f"{p}.attack2": {"loop": False, "animation_length": 0.7, "bones": {
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.2, [-80, 60, 0]), (0.4, [-80, -70, 0]), (0.7, [-10, 0, 4]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.2, [0, 30, 0]), (0.4, [0, -35, 0]), (0.7, [0, 0, 0]))},
        }},
        f"{p}.slam": {"loop": False, "animation_length": 1.2, "bones": {
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.4, [-185, 0, -15]), (0.65, [-185, 0, -15]), (0.75, [-40, 0, 0]),
                                         (1.2, [-10, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-18, 0, -6]), (0.4, [-185, 0, 15]), (0.65, [-185, 0, 15]), (0.75, [-40, 0, 0]),
                                        (1.2, [-18, 0, -6]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.4, [-15, 0, 0]), (0.75, [30, 0, 0]), (1.0, [30, 0, 0]), (1.2, [0, 0, 0]))},
            "root": {"position": kf((0, [0, 0, 0]), (0.45, [0, 4, 0]), (0.7, [0, 0, 0]), (0.75, [0, -1.5, 0]), (1.2, [0, 0, 0]))},
        }},
        f"{p}.charge": {"loop": False, "animation_length": 0.9, "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.15, [22, 0, 0]), (0.75, [22, 0, 0]), (0.9, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-18, 0, -6]), (0.15, [-80, -30, 0]), (0.75, [-80, -30, 0]), (0.9, [-18, 0, -6]))},
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.15, [20, 0, 10]), (0.75, [20, 0, 10]), (0.9, [-10, 0, 4]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.2, [40, 0, 0]), (0.4, [-40, 0, 0]), (0.6, [40, 0, 0]), (0.9, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.2, [-40, 0, 0]), (0.4, [40, 0, 0]), (0.6, [-40, 0, 0]), (0.9, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [4, 0, 0]), (0.2, [45, 0, 0]), (0.75, [45, 0, 0]), (0.9, [4, 0, 0]))},
        }},
        f"{p}.spin": {"loop": False, "animation_length": 1.5, "bones": {
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.15, [-90, 0, 85]), (1.35, [-90, 0, 85]), (1.5, [-10, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-18, 0, -6]), (0.15, [-60, 0, -70]), (1.35, [-60, 0, -70]), (1.5, [-18, 0, -6]))},
            "cape": {"rotation": kf((0, [4, 0, 0]), (0.2, [60, 0, 0]), (1.3, [60, 0, 0]), (1.5, [4, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.15, [6, 0, 0]), (1.35, [6, 0, 0]), (1.5, [0, 0, 0]))},
        }},
        f"{p}.roar": {"loop": False, "animation_length": 1.4, "bones": {
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-30, 0, 0]), (1.1, [-30, 0, 0]), (1.4, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-12, 0, 0]), (1.1, [-12, 0, 0]), (1.4, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-10, 0, 4]), (0.3, [-40, 0, 60]), (1.1, [-40, 0, 60]), (1.4, [-10, 0, 4]))},
            "left_arm": {"rotation": kf((0, [-18, 0, -6]), (0.3, [-40, 0, -60]), (1.1, [-40, 0, -60]), (1.4, [-18, 0, -6]))},
            "cape": {"rotation": kf((0, [4, 0, 0]), (0.3, [30, 0, 0]), (1.1, [30, 0, 0]), (1.4, [4, 0, 0]))},
        }},
    }
    return {"format_version": "1.8.0", "animations": a}


def champion_files():
    g = champion_geo()
    write_json(os.path.join(A, "geo/entity/city_champion.geo.json"), g.to_json(bounds=(4, 4)))
    write_json(os.path.join(A, "animations/entity/city_champion.animation.json"), champion_animations())
    for i, s in enumerate(STYLES):
        g.paint(champion_painters(s), 8100 + i).save(os.path.join(A, f"textures/entity/city_champion_{s}.png"))


# =================================================================================================
#  Coração, itens, ovos, ícones, artes dos estilos
# =================================================================================================
MARBLE = ["E8E2D6", "DCD4C4", "D0C8B8", "F0EADC"]
GOLDC = hex_rgba("E8B84A")


def marble_tex(seed, kind="side"):
    c = Canvas(16, 16)
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            col = hex_rgba(MARBLE[rnd.randrange(4)] if rnd.random() < 0.3 else MARBLE[0])
            c.set(x, y, jitter(col, rnd, 0.03))
    # veios
    for k in range(3):
        x = rnd.randrange(16)
        for y in range(16):
            x = max(0, min(15, x + rnd.choice((-1, 0, 0, 1))))
            c.set(x, y, hex_rgba("B8B0A0"))
    if kind == "top":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 5.0 <= d <= 6.3:
                    c.set(x, y, GOLDC if (x + y) % 3 else hex_rgba("FFE38A"))
                if d < 2.4:
                    c.set(x, y, hex_rgba("6FB8FF") if d < 1.4 else hex_rgba("BFE6FF"))
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                c.set(x, y, hex_rgba("A57A22"))
    elif kind == "band":
        for x in range(16):
            c.set(x, 0, GOLDC)
            c.set(x, 1, hex_rgba("A57A22"))
            c.set(x, 14, hex_rgba("A57A22"))
            c.set(x, 15, GOLDC)
        # brasão: torre estilizada no meio
        for (x, y) in [(6, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9),
                       (6, 10), (7, 10), (8, 10), (9, 10)]:
            c.set(x, y, GOLDC)
        c.set(7, 8, hex_rgba("6FB8FF"))
    else:
        for x in range(16):
            c.set(x, 0, lighten(hex_rgba(MARBLE[0]), 0.2))
            c.set(x, 15, hex_rgba("A8A090"))
    return c


def heart_block():
    marble_tex(31).save(os.path.join(A, "textures/block/settlement_heart_side.png"))
    marble_tex(32, "band").save(os.path.join(A, "textures/block/settlement_heart_band.png"))
    marble_tex(33, "top").save(os.path.join(A, "textures/block/settlement_heart_top.png"))
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            base = mix(hex_rgba("FFFFFF"), hex_rgba("C8D4E0"), y / 15)
            if (x + y) % 5 == 0:
                base = lighten(base, 0.4)
            if abs(x - 7.5) < 1 and y < 10:
                base = hex_rgba("FFFFFF")
            c.set(x, y, (base[0], base[1], base[2], 235))
    c.save(os.path.join(A, "textures/entity/settlement_heart_crystal.png"))

    def el(frm, to, side):
        faces = {}
        for f in ("north", "south", "east", "west"):
            faces[f] = {"texture": side, "uv": [frm[0] if f in ("north", "south") else frm[2], 16 - to[1],
                                                 to[0] if f in ("north", "south") else to[2], 16 - frm[1]]}
        faces["up"] = {"texture": "#top", "uv": [frm[0], frm[2], to[0], to[2]]}
        faces["down"] = {"texture": "#side", "uv": [frm[0], frm[2], to[0], to[2]]}
        return {"from": list(frm), "to": list(to), "faces": faces}

    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/settlement_heart_side", "side": f"{NS}:block/settlement_heart_side",
                     "band": f"{NS}:block/settlement_heart_band", "top": f"{NS}:block/settlement_heart_top"},
        # mesmas caixas do VoxelShape do bloco (base, coluna, capitel)
        "elements": [el((0, 0, 0), (16, 3, 16), "#side"), el((2, 3, 2), (14, 10, 14), "#band"),
                     el((1, 10, 1), (15, 13, 15), "#side")],
    }
    write_json(os.path.join(A, "models/block/settlement_heart.json"), model)
    write_json(os.path.join(A, "blockstates/settlement_heart.json"), {"variants": {"": {"model": f"{NS}:block/settlement_heart"}}})


def charter_tex():
    c = Canvas(16, 16)
    paper, paper_d, ink = hex_rgba("F3E6C8"), hex_rgba("D9C49A"), hex_rgba("6A4A2E")
    # pergaminho enrolado em diagonal
    for i in range(16):
        for w in range(-3, 4):
            x, y = 2 + i * 12 // 15 + w // 2, 13 - i * 11 // 15 + (w + 1) // 2
            c.set(x + 0, y, paper if w < 2 else paper_d)
    for i in range(16):
        x, y = 2 + i * 12 // 15, 13 - i * 11 // 15
        c.set(x - 2, y - 1, paper_d)
    # rolos nas pontas
    for (cx, cy) in ((2, 13), (14, 2)):
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                c.set(cx + dx, cy + dy, hex_rgba("C9A27A") if (dx + dy) % 2 else hex_rgba("A57A4A"))
        c.set(cx, cy, hex_rgba("6B4A2E"))
    # linhas de texto e o brasão
    for k in range(3):
        for i in range(3, 9):
            x, y = 4 + i - k, 11 - i + k * 2
            if (i + k) % 3:
                c.set(x, y, ink)
    # fita dourada e lacre vermelho
    for i in range(5):
        c.set(6 + i, 9 + (i % 2), GOLDC)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1), (2, 1), (1, 2)):
        c.set(9 + dx, 11 + dy, hex_rgba("C9433A"))
    c.set(9, 11, hex_rgba("FF7A6A"))
    c.set(10, 12, hex_rgba("8A1E1E"))
    c.outline(hex_rgba("3A2A1E"))
    return c


def seal_tex():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.0)
            if d <= 6.4:
                col = mix(hex_rgba("FFE38A"), hex_rgba("A57A22"), (x + y) / 30)
                if 5.2 <= d:
                    col = hex_rgba("C9933A")
                if 3.0 <= d <= 3.6:
                    col = hex_rgba("A57A22")
                c.set(x, y, col)
    # coroa no meio
    for (x, y) in [(5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (5, 7), (7, 6), (8, 6), (10, 7), (5, 6), (10, 6),
                   (6, 9), (7, 9), (8, 9), (9, 9)]:
        c.set(x, y, hex_rgba("8A5A12"))
    c.set(7, 5, hex_rgba("C9433A"))
    c.set(8, 5, hex_rgba("C9433A"))
    # fita
    for y in range(13, 16):
        c.set(6, y, hex_rgba("3F6FD8"))
        c.set(9, y, hex_rgba("3F6FD8"))
    c.set(5, 15, hex_rgba("2B4FA8"))
    c.set(10, 15, hex_rgba("2B4FA8"))
    c.set(11, 4, hex_rgba("FFFFFF"))
    c.outline(hex_rgba("4A3210"))
    return c


def items():
    charter_tex().save(os.path.join(A, "textures/item/founding_charter.png"))
    seal_tex().save(os.path.join(A, "textures/item/oath_seal.png"))
    for it in ("founding_charter", "oath_seal"):
        write_json(os.path.join(A, f"models/item/{it}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{it}"}})
    for e in ("settlement_builder", "settlement_resident", "settlement_guard", "settlement_merchant", "settlement_militia",
              "city_champion"):
        write_json(os.path.join(A, f"models/item/{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    # Receita da Carta de Fundação (onda 9, "fundar mais fácil"): 2 papéis + 2 lingotes de ouro + livro e pena — começo
    # de jogo (antes eram blocos de esmeralda e de ouro). O Escriba da cidade inicial também vende.
    write_json(os.path.join(D, "recipe/founding_charter.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" P ", "GBG", " P "],
        "key": {"P": {"item": "minecraft:paper"}, "G": {"item": "minecraft:gold_ingot"},
                "B": {"item": "minecraft:writable_book"}},
        "result": {"id": f"{NS}:founding_charter", "count": 1}})
    write_json(os.path.join(D, "advancement/recipes/misc/founding_charter.json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {"has_paper": {"trigger": "minecraft:inventory_changed",
                                   "conditions": {"items": [{"items": "minecraft:paper"}]}},
                     "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                                        "conditions": {"recipe": f"{NS}:founding_charter"}}},
        "requirements": [["has_paper", "has_the_recipe"]],
        "rewards": {"recipes": [f"{NS}:founding_charter"]}})


def tab_icon():
    c = Canvas(16, 16)
    stone, stone_d, blue, gold = hex_rgba("B8BEC8"), hex_rgba("7A808A"), hex_rgba("3F6FD8"), GOLDC
    c.rect(2, 7, 13, 14, stone)
    for x in (2, 4, 9, 11, 13):
        c.set(x, 6, stone)
    c.rect(5, 3, 10, 14, lighten(stone, 0.1))
    for x in (5, 7, 8, 10):
        c.set(x, 2, stone)
    c.rect(7, 10, 8, 14, hex_rgba("3A2A1E"))
    c.set(7, 6, hex_rgba("2A2A3A"))
    c.set(8, 6, hex_rgba("2A2A3A"))
    for y in range(0, 4):
        c.set(12, y, hex_rgba("6B4A2E"))
    c.rect(13, 0, 15, 2, blue)
    c.set(14, 1, gold)
    for x in range(2, 14):
        c.set(x, 14, stone_d)
    c.outline(hex_rgba("2A2420"))
    c.save(os.path.join(A, "textures/gui/settlements/tab.png"))


def style_art(style):
    W, Hh = 128, 80
    c = Canvas(W, Hh)
    rnd = random.Random(f"art/{style}")
    if style == "kingdom":
        sky_top, sky_bot = hex_rgba("5A8AE0"), hex_rgba("C8E0FF")
    elif style == "desert":
        sky_top, sky_bot = hex_rgba("E0603A"), hex_rgba("FFD08A")
    else:
        sky_top, sky_bot = hex_rgba("10182E"), hex_rgba("3A5A7A")
    for y in range(Hh):
        for x in range(W):
            c.set(x, y, mix(sky_top, sky_bot, y / (Hh - 1)))
    if style == "nordic":
        # aurora e estrelas
        for x in range(W):
            yc = 14 + int(6 * math.sin(x / 11.0)) + int(3 * math.sin(x / 4.0))
            for dy in range(-4, 5):
                t = 1 - abs(dy) / 5
                col = mix(c.get(x, yc + dy), hex_rgba("6BF0B0") if x % 40 < 25 else hex_rgba("8FC8FF"), t * 0.7)
                c.set(x, yc + dy, col)
        for _ in range(40):
            c.set(rnd.randrange(W), rnd.randrange(30), hex_rgba("FFFFFF"))
        c.circle(104, 12, 5, hex_rgba("EDEFF5"))
        # colinas nevadas
        for x in range(W):
            h = 50 + int(5 * math.sin(x / 13.0 + 1))
            for y in range(h, Hh):
                c.set(x, y, hex_rgba("E8F0F8") if y < h + 3 else hex_rgba("C8D8E8"))
        # pinheiros
        for tx in (8, 20, 112, 122):
            for k in range(10):
                w = k // 2
                for dx in range(-w, w + 1):
                    c.set(tx + dx, 38 + k, hex_rgba("1E3A2E") if (dx + k) % 3 else hex_rgba("EDEFF5"))
            c.rect(tx, 48, tx, 52, hex_rgba("4A3220"))
        # casa comprida de telhado íngreme
        c.rect(44, 46, 84, 60, hex_rgba("6B4A2E"))
        for k in range(14):
            c.rect(40 + k, 46 - k, 88 - k, 46 - k, hex_rgba("3A2A1E") if k % 2 else hex_rgba("4A3424"))
        for x in range(46, 84, 4):
            c.rect(x, 47, x, 60, hex_rgba("4A3220"))
        c.rect(61, 52, 66, 60, hex_rgba("2A1E14"))
        c.set(55, 51, hex_rgba("FFC060"))
        c.set(74, 51, hex_rgba("FFC060"))
        c.set(64, 31, hex_rgba("EDE3C8"))
        c.set(63, 30, hex_rgba("EDE3C8"))
        c.set(65, 30, hex_rgba("EDE3C8"))
        # paliçada de troncos com pontas
        for x in range(0, W, 3):
            for y in range(58, 72):
                c.set(x, y, hex_rgba("7A5636"))
                c.set(x + 1, y, hex_rgba("6B4A2E"))
            c.set(x, 57, hex_rgba("8A6A4A"))
            c.set(x + 1, 56, hex_rgba("8A6A4A"))
        c.rect(0, 72, W - 1, Hh - 1, hex_rgba("E8F0F8"))
    elif style == "desert":
        c.circle(96, 30, 9, hex_rgba("FFE8A0"))
        for x in range(W):
            h = 56 + int(4 * math.sin(x / 9.0)) + int(2 * math.sin(x / 3.7))
            for y in range(h, Hh):
                c.set(x, y, mix(hex_rgba("F0C878"), hex_rgba("D9A050"), (y - h) / 20))
        # pirâmide com degraus e topo dourado
        for k in range(22):
            y = 56 - k
            for x in range(36 + k, 92 - k):
                col = hex_rgba("E8C890") if x < 64 else hex_rgba("C9A060")
                if k % 3 == 0:
                    col = darken(col, 0.1)
                c.set(x, y, col)
        c.rect(62, 33, 65, 35, hex_rgba("FFD84A"))
        c.rect(60, 50, 67, 56, hex_rgba("6A4A22"))
        # obelisco
        c.rect(100, 26, 103, 58, hex_rgba("E0C080"))
        c.rect(101, 23, 102, 25, hex_rgba("FFD84A"))
        for y in range(30, 56, 4):
            c.set(101, y, hex_rgba("A57A22"))
        # palmeira
        c.line(18, 62, 22, 38, hex_rgba("7A5A32"))
        for ang in range(0, 360, 45):
            a = math.radians(ang)
            c.line(22, 38, int(22 + 9 * math.cos(a)), int(38 + 5 * math.sin(a) + 2), hex_rgba("3A8A3A"))
        # muro simples de arenito
        c.rect(0, 64, W - 1, 70, hex_rgba("E8D0A0"))
        for x in range(0, W, 6):
            c.rect(x, 62, x + 2, 63, hex_rgba("E8D0A0"))
        for x in range(0, W, 8):
            c.set(x, 67, hex_rgba("C9A870"))
        c.rect(0, 71, W - 1, Hh - 1, hex_rgba("F0C878"))
    else:
        for _ in range(4):
            cx, cy = rnd.randrange(10, 118), rnd.randrange(6, 22)
            for dx in range(-8, 9):
                for dy in range(-2, 3):
                    if dx * dx / 64 + dy * dy / 6 <= 1:
                        c.set(cx + dx, cy + dy, hex_rgba("F4F8FF"))
        for x in range(W):
            h = 54 + int(4 * math.sin(x / 15.0 + 2))
            for y in range(h, Hh):
                c.set(x, y, hex_rgba("5AA040") if y < h + 2 else hex_rgba("4A8A34"))
        # castelo: torre central e duas laterais com bandeiras azuis
        stone, stone_d = hex_rgba("C8CCD4"), hex_rgba("9AA0AA")
        c.rect(44, 30, 84, 62, stone)
        for x in range(44, 85, 4):
            c.rect(x, 28, x + 1, 29, stone)
        c.rect(56, 16, 72, 40, lighten(stone, 0.05))
        for x in range(56, 73, 4):
            c.rect(x, 14, x + 1, 15, stone)
        for (tx, top) in ((36, 24), (88, 24)):
            c.rect(tx, top, tx + 8, 62, stone)
            for k in range(6):
                c.rect(tx - 1 + k, top - 1 - k, tx + 9 - k, top - 1 - k, hex_rgba("2B4FA8"))
            c.rect(tx + 4, top - 12, tx + 4, top - 7, hex_rgba("4A3220"))
            c.rect(tx + 5, top - 12, tx + 8, top - 10, hex_rgba("3F6FD8"))
            c.set(tx + 6, top - 11, GOLDC)
        c.rect(61, 50, 67, 62, hex_rgba("4A3220"))
        for (wx, wy) in ((50, 40), (77, 40), (63, 24), (40, 36), (92, 36)):
            c.rect(wx, wy, wx + 1, wy + 2, hex_rgba("2A3A5A"))
        c.rect(60, 22, 68, 26, hex_rgba("3F6FD8"))
        c.set(64, 24, GOLDC)
        # muralha de pedra com ameias
        c.rect(0, 64, W - 1, 72, stone_d)
        for x in range(0, W, 5):
            c.rect(x, 62, x + 2, 63, stone_d)
        for x in range(0, W, 7):
            c.set(x, 68, darken(stone_d, 0.15))
        c.rect(0, 73, W - 1, Hh - 1, hex_rgba("5AA040"))
    # moldura
    for x in range(W):
        c.set(x, 0, hex_rgba("2A2016"))
        c.set(x, Hh - 1, hex_rgba("2A2016"))
    for y in range(Hh):
        c.set(0, y, hex_rgba("2A2016"))
        c.set(W - 1, y, hex_rgba("2A2016"))
    c.save(os.path.join(A, f"textures/gui/settlements/style_{style}.png"))


def gui():
    tab_icon()
    for s in STYLES:
        style_art(s)


# =================================================================================================
#  Main
# =================================================================================================
def main():
    n = npc_files()
    champion_files()
    heart_block()
    items()
    gui()
    import settlements_build as sb
    built = sb.build_all()
    import settlements_lang as sl
    pt, en = sl.translations()
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))
    problems = check(pt, en, built)
    for p in problems:
        print("ERRO:", p)
    print(f"settlements: {n} texturas de NPC, {len(built)} moldes, {len(pt)} traduções")
    if problems:
        sys.exit(1)


def java_sources():
    src = ""
    for dp, _, files in os.walk(JAVA):
        for f in files:
            if f.endswith(".java"):
                src += open(os.path.join(dp, f), encoding="utf-8").read() + "\n"
    return src


def check(pt, en, built):
    """Confere as traduções contra o Java (chaves literais e prefixos montados) e os moldes que o Java pede."""
    out = []
    src = java_sources()
    for k in sorted(set(re.findall(r'"((?:settlements|entity|item|block)\.isekai_mahou\.[a-z0-9_.]*)"', src))):
        if k.endswith("."):
            if not any(x.startswith(k) for x in pt):
                out.append(f"prefixo sem nenhuma tradução: {k}")
            continue
        for lg, m in (("pt_br", pt), ("en_us", en)):
            if k not in m:
                out.append(f"tradução faltando [{lg}]: {k}")
    # falas sorteadas (SettlementNpc.line(grupo, n)) e chaves montadas (SettlementsLang.tr("prefixo." + ...))
    roles = ["builder", "resident", "guard", "merchant", "militia"]
    groups = [(g, int(n)) for g, n in re.findall(r'line\("([a-z.]+)", (\d+)\)', src)]
    groups += [(f"{r}.{suffix}", int(n)) for suffix, n in re.findall(r'line\(role\(\)\.id\(\) \+ "\.([a-z]+)", (\d+)\)', src)
               for r in roles]
    for g, n in groups:
        for i in range(n):
            if f"settlements.{NS}.line.{g}.{i}" not in pt:
                out.append(f"fala faltando: line.{g}.{i}")
    for pre in sorted(set(re.findall(r'SettlementsLang\.tr\("([a-z0-9_.]+\.)"', src))):
        if not any(k.startswith(f"settlements.{NS}.{pre}") for k in pt):
            out.append(f"prefixo sem tradução: {pre}")
    for st_ in STYLES:
        for suf in ("", ".desc", ".bonus"):
            if f"settlements.{NS}.style.{st_}{suf}" not in pt:
                out.append(f"estilo sem tradução: {st_}{suf}")
    if set(pt) != set(en):
        out.append(f"pt_br e en_us diferentes: {sorted(set(pt) ^ set(en))[:10]}")
    for k, v in pt.items():
        if v.count("%s") != en[k].count("%s") or len(re.findall(r"%\d\$s", v)) != len(re.findall(r"%\d\$s", en[k])):
            out.append(f"argumentos diferentes em {k}")
    import settlements_build as sb
    for name in sb.expected_templates():
        if name not in built:
            out.append(f"molde faltando: {name}")
    return out


if __name__ == "__main__":
    main()
