"""
Assets do modulo espada (v1.3): modelos GeckoLib (geo + texturas por forma + glowmask) e animacoes de
Ulquiorra (selado / Murcielago / Segunda Etapa), Barragan (rei / Arrogante esqueleto) e Grimmjow (selado / Pantera),
mais icones dos itens, ovos, o icone do status Envelhecido e a vinheta de tela.

Cada chefe tem UM .geo.json com todos os ossos de todas as formas (o jogo liga/desliga ossos pela forma) e uma
textura por forma: os cubos sao criados na MESMA ordem e tamanho em todas as variacoes, entao o mapa UV e igual.
Frente do modelo = -Z; direita do personagem = -X. Unidades em pixels (16 = 1 bloco).

Uso:  py -I tools/gen/espada.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from espada_lib import (Model, P, H, CLEAR, ASSETS, chain, band, folds, speckle, rim, solid, glow_faces, cyc, keys, S, C,  # noqa: E402
                        anim, save_anims, fur, lighten, darken, SIDES, write_json)
from pixelart import Canvas, hex_rgba  # noqa: E402

# ---------------------------------------------------------------- paletas comuns
WHITE, WHITE_D, WHITE_L = "#F1F0EA", "#C9C8C0", "#FFFFFF"
SASH = "#17171A"
SKIN_PALE, SKIN_PALE_D = "#E6E2DA", "#BDB8AE"
HAIR_BLK, HAIR_BLK_D = "#121216", "#060608"
BONE, BONE_D, BONE_L = "#ECE6D6", "#BDB5A0", "#FFFDF5"
HOLE = "#050507"
GREEN, GREEN_D, GREEN_L = "#3DFF8E", "#118A48", "#C8FFE0"


def hole(face, cx, cy, r):
    """Buraco de Hollow (circulo preto) numa face."""
    def d(fs):
        f = fs[face]
        for y in range(f.h):
            for x in range(f.w):
                dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if dd <= r:
                    f.set(x, y, H(HOLE))
                elif dd <= r + 0.9:
                    f.set(x, y, H("#34303A"))
    return d


def tear_face(skin, hair, eye, tear, mouth_dark=None):
    """Rosto palido: cabelo preto em cima/lados, olhos verdes brilhantes e as LINHAS DE LAGRIMA verdes descendo."""
    def d(fs):
        f = fs["front"]
        f.rect(0, 0, 7, 1, H(hair))
        f.set(0, 2, H(hair))
        f.set(7, 2, H(hair))
        f.set(0, 3, H(hair))
        # olhos
        f.rect(1, 3, 2, 3, H("#F8F8F4"))
        f.rect(5, 3, 6, 3, H("#F8F8F4"))
        f.set(2, 3, H(eye), True)
        f.set(5, 3, H(eye), True)
        f.set(1, 2, H(darken(H(skin), 0.25)))
        f.set(6, 2, H(darken(H(skin), 0.25)))
        # lagrimas (do olho ate o queixo)
        for yy in range(4, 8):
            f.set(2, yy, H(tear), True)
            f.set(5, yy, H(tear), True)
        f.rect(3, 6, 4, 6, H(mouth_dark or darken(H(skin), 0.3)))
        for n in ("top", "back", "left", "right"):
            fs[n].fill(H(hair))
            fs[n].scatter(H(HAIR_BLK_D), 0.25, 3 + len(n))
        for n in ("left", "right"):
            fs[n].rect(3, 4, 6, 7, H(skin))
    return d


def jacket(sash, accent, hole_at=None):
    def d(fs):
        for n in SIDES:
            fs[n].col(fs[n].w // 2, H(WHITE_D))
        f = fs["front"]
        f.rect(3, 0, 4, 3, H(SKIN_PALE))   # gola aberta
        f.row(f.h - 3, H(sash))
        f.row(f.h - 2, H(sash))
        fs["back"].row(fs["back"].h - 3, H(sash))
        f.set(1, 1, H(accent))
        f.set(6, 1, H(accent))
        if hole_at:
            hole("front", hole_at[0], hole_at[1], hole_at[2])(fs)
    return d


def bat_wing(veins, membrane, seed, glow=True):
    """Asa de morcego: membrana escura com bordas recortadas (transparente) e nervuras verdes brilhantes."""
    def d(fs):
        rnd = random.Random(seed)
        for n in ("front", "back"):
            f = fs[n]
            for x in range(f.w):
                # borda de baixo em "ondas" (recortes entre os dedos)
                depth = int(2 + 3 * abs(math.sin((x + 1) * math.pi / 5.0)))
                for y in range(f.h - depth, f.h):
                    f.set(x, y, CLEAR)
            # nervuras saindo do ombro (canto de cima, perto do corpo)
            for k in range(4):
                ang = 0.15 + k * 0.33
                for t in range(0, 40):
                    x = int(t * math.cos(ang) * 0.9)
                    y = int(t * math.sin(ang) * 0.6)
                    xx = x if n == "back" else f.w - 1 - x
                    if 0 <= xx < f.w and 0 <= y < f.h and f.get(xx, y)[3] > 0:
                        f.set(xx, y, H(veins), glow)
            f.scatter(H(membrane), 0.08, seed + len(n))
        for n in ("left", "right", "top", "bottom"):
            fs[n].fill(H(darken(H(membrane), 0.3)))
        _ = rnd
    return d


# =====================================================================================
#  ULQUIORRA CIFER
# =====================================================================================
ULQ = [
    # 0 selado: uniforme branco, mascara-capacete no lado esquerdo
    dict(skin=SKIN_PALE, hair=HAIR_BLK, coat=WHITE, coat_d=WHITE_D, sash=SASH, tear=GREEN_D, eye=GREEN, helmet=BONE,
         legs=WHITE, wing="#0B0B0E", vein=GREEN, fur="#16161A", tail="#101013", horn=BONE),
    # 1 Murcielago: robe branco de bordas negras, asas, cabelo longo, capacete com chifres
    dict(skin="#DCD8D0", hair=HAIR_BLK, coat=WHITE, coat_d="#2A2A2E", sash="#0C0C0E", tear=GREEN, eye=GREEN, helmet=BONE,
         legs="#ECEBE5", wing="#08080A", vein=GREEN, fur="#16161A", tail="#101013", horn=BONE),
    # 2 Segunda Etapa: corpo negro, pernas de pelo, cauda e chifres; o rosto ainda palido com lagrimas
    dict(skin="#CFCBC2", hair="#050506", coat="#141418", coat_d="#050507", sash="#2B2B30", tear=GREEN, eye=GREEN_L, helmet="#141418",
         legs="#0E0E11", wing="#040405", vein=GREEN, fur="#0A0A0C", tail="#0B0B0D", horn="#1C1C20"),
]


def build_ulquiorra(v):
    pal = ULQ[v]
    m = Model("ulquiorra")
    m.bone("root", (0, 0, 0))
    # pernas (filhas da raiz: o tronco dobra na cintura)
    for name, x0, px in (("leg_r", -4, -2), ("leg_l", 0, 2)):
        m.bone(name, (px, 12, 0), "root")

        def hakama(fs, pal=pal):
            for n in SIDES:
                fs[n].row(fs[n].h - 1, H(pal["sash"]))
                fs[n].row(fs[n].h - 2, H(darken(H(pal["sash"]), 0.2)))
        m.cube(name, (x0, 0, -2), (4, 12, 4), P(pal["legs"], deco=chain(folds(darken(H(pal["legs"]), 0.12), 3), hakama), noise=0.03))
        fur_name = "fur_" + name[-1]
        m.bone(fur_name, (px, 12, 0), name)
        m.cube(fur_name, (x0, 0, -2), (4, 9, 4), P(pal["fur"], deco=fur("#000000", "#26262C", 11 + x0, density=0.4)), inflate=0.55)
    m.bone("body", (0, 12, 0), "root")
    m.cube("body", (-4, 12, -2), (8, 12, 4), P(pal["coat"], deco=jacket(pal["sash"], GREEN_D, hole_at=(4, 2, 1.2)), noise=0.03))
    # abas do casaco (selado) e o robe longo (Murcielago)
    m.bone("coat", (0, 12, 1.5), "body")
    m.cube("coat", (-4.5, 3, 1.5), (9, 9, 1), P(pal["coat"], deco=chain(folds(pal["coat_d"], 3), band([-1], pal["coat_d"])), noise=0.03))
    m.bone("robe", (0, 12, 0), "body")
    m.cube("robe", (-4.6, 1, -2.6), (9, 11, 5), P(pal["coat"], deco=chain(folds(darken(H(pal["coat"]), 0.1), 2),
                                                                               band([-1, -2], pal["coat_d"]))), inflate=0.2)
    # espada na cintura (selado)
    m.bone("sword", (4.5, 11, 0), "body")
    m.cube("sword", (4.3, 10, -7), (1, 2, 13), P("#D8D2C0", deco=chain(band([0], GREEN_D), solid("#1C1C20", faces=("front",)))))
    m.cube("sword", (4.1, 9.5, -8.5), (1, 3, 1), P("#C9A23A"))
    # cabeca
    m.bone("head", (0, 24, 0), "body")
    m.cube("head", (-4, 24, -4), (8, 8, 8), P(pal["skin"], deco=tear_face(pal["skin"], pal["hair"], pal["eye"], pal["tear"]), noise=0.02))
    m.cube("head", (-4.5, 28, -0.5), (9, 5, 5), P(pal["hair"], deco=speckle(HAIR_BLK_D, 0.3, 9 + v)))
    m.bone("hair_long", (0, 30, 3), "head")
    m.cube("hair_long", (-4.5, 14, 2.5), (9, 16, 2), P(pal["hair"], deco=chain(speckle(HAIR_BLK_D, 0.35, 21), folds(HAIR_BLK_D, 2))))
    # capacete (fragmento de mascara do lado esquerdo) e versao com chifres (Murcielago)
    bone_p = P(pal["helmet"], deco=speckle(darken(H(pal["helmet"]), 0.2), 0.15, 40 + v), noise=0.02)
    m.bone("helmet", (0, 24, 0), "head")
    m.cube("helmet", (0.4, 30.5, -4.6), (4, 3, 9), bone_p)
    m.cube("helmet", (3.6, 27, -3), (1, 4, 6), bone_p)
    m.cube("helmet", (2.5, 33, -1.5), (1, 3, 1), P(pal["horn"]), rotation=[0, 0, -20], pivot=[3, 33, -1])
    m.bone("helmet_horns", (0, 24, 0), "head")
    m.cube("helmet_horns", (-4.6, 31, -4.6), (9, 2, 9), bone_p)
    for side in (-1, 1):
        x = 3.5 if side > 0 else -5.5
        m.cube("helmet_horns", (x, 32, -1), (2, 7, 2), P(pal["horn"], deco=band([0], BONE_L)), rotation=[0, 0, -25 * side],
               pivot=[x + 1, 32, 0])
    m.bone("horns", (0, 30, 0), "head")
    for side in (-1, 1):
        x = 4 if side > 0 else -7
        m.cube("horns", (x, 30, -1), (3, 2, 2), P(pal["horn"]))
        m.cube("horns", (x + (2 if side > 0 else 0), 31.5, -1), (1, 6, 1), P(pal["horn"], deco=band([0], "#3A3A42")),
               rotation=[0, 0, -18 * side], pivot=[x + 1, 31.5, -0.5])
    # bracos
    for name, x0, px in (("arm_r", -8, -5), ("arm_l", 4, 5)):
        m.bone(name, (px, 22, 0), "body")

        def sleeve(fs, pal=pal):
            for n in SIDES:
                fs[n].row(fs[n].h - 1, H(pal["skin"]))
                fs[n].row(fs[n].h - 2, H(pal["skin"]))
                fs[n].row(fs[n].h - 4, H(pal["coat_d"]))
        m.cube(name, (x0, 12, -2), (4, 12, 4), P(pal["coat"], deco=sleeve, noise=0.03))
    # lanca (Lanza del Relampago) na mao direita
    # v1.3.2: girada para apontar para a frente com o braco erguido (lanza_charge)
    m.bone("lance", (-6, 13, 0), "arm_r", rotation=[160, 0, 0])
    m.cube("lance", (-6.5, 12.5, -22), (1, 1, 36), P(GREEN, deco=glow_faces(GREEN, core=GREEN_L)))
    m.cube("lance", (-7, 12, -25), (2, 2, 3), P(GREEN_L, deco=glow_faces(GREEN_L)))
    # asas
    for name, sgn in (("wing_r", -1), ("wing_l", 1)):
        px = 1.5 * sgn
        m.bone(name, (px, 21, 2), "body")
        x0 = px if sgn > 0 else px - 18
        m.cube(name, (x0, 7, 2), (18, 16, 1), P(pal["wing"], deco=bat_wing(pal["vein"], darken(H(pal["wing"]), 0.3), 5 + sgn)))
        m.cube(name, (px + (0 if sgn > 0 else -18), 22, 1.5), (18, 1, 2), P(pal["wing"], deco=band([0], "#2A2A30")))
    # cauda (Segunda Etapa)
    m.bone("tail1", (0, 11, 2), "body")
    m.cube("tail1", (-1, 10, 2), (2, 2, 8), P(pal["tail"], deco=speckle("#000000", 0.3, 50)))
    m.bone("tail2", (0, 11, 10), "tail1")
    m.cube("tail2", (-1, 10, 10), (2, 2, 8), P(pal["tail"]))
    m.bone("tail3", (0, 11, 18), "tail2")
    m.cube("tail3", (-0.5, 10.5, 18), (1, 1, 9), P(pal["tail"], deco=band([0], GREEN_D)))
    m.cube("tail3", (-1, 10, 26), (2, 2, 2), P(GREEN, deco=glow_faces(GREEN)))
    tex = ["ulquiorra", "ulquiorra_murcielago", "ulquiorra_segunda"][v]
    m.save(bounds=(4, 3.5), seed=11 + v, tex_name=tex, geo=(v == 0))


def ulquiorra_anims():
    a = {}
    a["idle"] = anim(2.4, {"body": {"position": cyc(2.4, lambda p: [0, 0.15 * S(p), 0])},
                           "arm_r": {"rotation": cyc(2.4, lambda p: [8 + 1.5 * S(p), 0, 6])},
                           "arm_l": {"rotation": cyc(2.4, lambda p: [8 + 1.5 * S(p), 0, -6])},
                           "head": {"rotation": cyc(4.8, lambda p: [3 + 1.5 * S(p), 0, 0])},
                           "wing_r": {"rotation": cyc(2.4, lambda p: [0, 20 + 5 * S(p), 0])},
                           "wing_l": {"rotation": cyc(2.4, lambda p: [0, -20 - 5 * S(p), 0])},
                           "tail1": {"rotation": cyc(2.4, lambda p: [-25 + 4 * S(p), 10 * S(p), 0])},
                           "tail2": {"rotation": cyc(2.4, lambda p: [12, 14 * S(p - 0.15), 0])},
                           "tail3": {"rotation": cyc(2.4, lambda p: [10, 18 * S(p - 0.3), 0])}}, loop=True)
    a["sleep"] = anim(4.0, {"body": {"position": cyc(4.0, lambda p: [0, 0.1 * S(p), 0])},
                            "arm_r": {"rotation": keys([(0, [8, 0, 6]), (4.0, [8, 0, 6])])},
                            "arm_l": {"rotation": keys([(0, [8, 0, -6]), (4.0, [8, 0, -6])])},
                            "head": {"rotation": keys([(0, [12, 0, 0]), (4.0, [12, 0, 0])])}}, loop=True)
    a["walk"] = anim(1.0, {"leg_l": {"rotation": cyc(1.0, lambda p: [22 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(1.0, lambda p: [-22 * S(p), 0, 0])},
                           "arm_r": {"rotation": keys([(0, [8, 0, 6]), (1.0, [8, 0, 6])])},
                           "arm_l": {"rotation": keys([(0, [8, 0, -6]), (1.0, [8, 0, -6])])},
                           "body": {"position": cyc(1.0, lambda p: [0, 0.3 * abs(S(p)), 0])},
                           "wing_r": {"rotation": keys([(0, [0, 25, 0]), (1.0, [0, 25, 0])])},
                           "wing_l": {"rotation": keys([(0, [0, -25, 0]), (1.0, [0, -25, 0])])},
                           "tail1": {"rotation": cyc(1.0, lambda p: [-20, 12 * S(p), 0])}}, loop=True)
    a["fly"] = anim(1.2, {"wing_r": {"rotation": cyc(1.2, lambda p: [0, 10 + 40 * S(p), -10 * C(p)])},
                          "wing_l": {"rotation": cyc(1.2, lambda p: [0, -10 - 40 * S(p), 10 * C(p)])},
                          "body": {"position": cyc(1.2, lambda p: [0, 1.0 * S(p + 0.25), 0]), "rotation": keys([(0, [8, 0, 0]), (1.2, [8, 0, 0])])},
                          "leg_l": {"rotation": cyc(1.2, lambda p: [12 + 4 * S(p), 0, -3])},
                          "leg_r": {"rotation": cyc(1.2, lambda p: [18 + 4 * S(p - 0.2), 0, 3])},
                          "arm_r": {"rotation": keys([(0, [10, 0, 10]), (1.2, [10, 0, 10])])},
                          "arm_l": {"rotation": keys([(0, [10, 0, -10]), (1.2, [10, 0, -10])])},
                          "tail1": {"rotation": cyc(1.2, lambda p: [-35 + 6 * S(p), 8 * S(p), 0])},
                          "tail2": {"rotation": cyc(1.2, lambda p: [15, 12 * S(p - 0.2), 0])},
                          "tail3": {"rotation": cyc(1.2, lambda p: [15, 16 * S(p - 0.4), 0])}}, loop=True)
    a["intro"] = anim(3.0, {"head": {"rotation": keys([(0, [15, 0, 0]), (1.2, [15, 0, 0]), (1.8, [0, 0, 0]), (3.0, [0, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [8, 0, 6]), (3.0, [8, 0, 6])])},
                            "arm_l": {"rotation": keys([(0, [8, 0, -6]), (2.0, [8, 0, -6]), (2.4, [-80, 10, -5]), (3.0, [8, 0, -6])])},
                            "body": {"position": keys([(0, [0, 0, 0]), (3.0, [0, 0, 0])])}})
    a["transform"] = anim(2.0, {"arm_r": {"rotation": cyc(2.0, lambda p: [-60 + 6 * S(p), 0, 35])},
                                "arm_l": {"rotation": cyc(2.0, lambda p: [-60 + 6 * S(p), 0, -35])},
                                "head": {"rotation": keys([(0, [-25, 0, 0]), (2.0, [-25, 0, 0])])},
                                "body": {"position": cyc(2.0, lambda p: [0, 0.5 * S(p), 0])},
                                "leg_l": {"rotation": keys([(0, [10, 0, 0]), (2.0, [10, 0, 0])])},
                                "leg_r": {"rotation": keys([(0, [16, 0, 0]), (2.0, [16, 0, 0])])},
                                "wing_r": {"rotation": cyc(2.0, lambda p: [0, 60 + 10 * S(p), 0])},
                                "wing_l": {"rotation": cyc(2.0, lambda p: [0, -60 - 10 * S(p), 0])}}, loop=True)
    a["transform2"] = anim(2.0, {"arm_r": {"rotation": cyc(2.0, lambda p: [20, 0, 12 + 3 * S(p)])},
                                 "arm_l": {"rotation": cyc(2.0, lambda p: [20, 0, -12 - 3 * S(p)])},
                                 "head": {"rotation": cyc(2.0, lambda p: [30 + 4 * S(p), 0, 0])},
                                 "body": {"rotation": cyc(2.0, lambda p: [25 + 3 * S(p), 0, 0])},
                                 "wing_r": {"rotation": cyc(2.0, lambda p: [0, 75, 20])},
                                 "wing_l": {"rotation": cyc(2.0, lambda p: [0, -75, -20])}}, loop=True)
    a["release"] = anim(1.5, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, 20]), (1.0, [-60, 0, 70]), (1.5, [0, 0, 8])])},
                              "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-60, 0, -40]), (1.0, [-60, 0, -70]), (1.5, [0, 0, -8])])},
                              "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (1.5, [0, 0, 0])])},
                              "wing_r": {"rotation": keys([(0, [0, 10, 0]), (1.0, [0, 80, 0]), (1.5, [0, 25, 0])])},
                              "wing_l": {"rotation": keys([(0, [0, -10, 0]), (1.0, [0, -80, 0]), (1.5, [0, -25, 0])])}})
    a["death"] = anim(4.0, {"body": {"rotation": keys([(0, [0, 0, 0]), (1.5, [12, 0, 0]), (4.0, [18, 0, 0])]),
                                     "position": keys([(0, [0, 0, 0]), (4.0, [0, -1, 0])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (2.0, [20, 0, 0]), (4.0, [25, 0, 0])])},
                            "arm_l": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-80, -10, 0]), (2.8, [-75, -10, 0]), (4.0, [-20, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [0, 0, 0]), (4.0, [10, 0, 10])])},
                            "wing_r": {"rotation": keys([(0, [0, 30, 0]), (4.0, [20, 70, 30])])},
                            "wing_l": {"rotation": keys([(0, [0, -30, 0]), (4.0, [20, -70, -30])])}})
    a["slash"] = anim(0.6, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.18, [-150, 30, 0]), (0.33, [-30, -50, 0]), (0.6, [8, 0, 6])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.18, [0, 25, 0]), (0.33, [0, -30, 0]), (0.6, [0, 0, 0])])}})
    a["sonido"] = anim(0.35, {"body": {"position": keys([(0, [0, -2, 0]), (0.15, [0, -1, 0]), (0.35, [0, 0, 0])]),
                                       "rotation": keys([(0, [18, 0, 0]), (0.35, [0, 0, 0])])}})
    a["bala"] = anim(1.6, {"arm_l": {"rotation": keys([(0, [8, 0, -6]), (0.25, [-90, 0, 0]), (1.4, [-90, 0, 0]), (1.6, [8, 0, -6])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.25, [0, -20, 0]), (1.4, [0, -20, 0]), (1.6, [0, 0, 0])])}})
    a["cero"] = anim(1.6, {"arm_r": {"rotation": keys([(0, [8, 0, 6]), (0.3, [-90, 0, 0]), (1.4, [-90, 0, 0]), (1.6, [8, 0, 6])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 15, 0]), (1.4, [0, 15, 0]), (1.6, [0, 0, 0])])}})
    a["cero_oscuras"] = anim(3.4, {"arm_r": {"rotation": keys([(0, [8, 0, 6]), (0.6, [-95, 0, 0]), (2.6, [-90, 0, 0]), (2.8, [-100, 0, 0]), (3.4, [8, 0, 6])])},
                                   "arm_l": {"rotation": keys([(0, [8, 0, -6]), (0.6, [-70, 0, -40]), (3.0, [-70, 0, -40]), (3.4, [8, 0, -6])])},
                                   "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, 20, 0]), (2.6, [0, 20, 0]), (2.8, [-8, 20, 0]), (3.4, [0, 0, 0])])},
                                   "wing_r": {"rotation": keys([(0, [0, 20, 0]), (1.0, [0, 70, 0]), (2.8, [0, 70, 0]), (3.4, [0, 20, 0])])},
                                   "wing_l": {"rotation": keys([(0, [0, -20, 0]), (1.0, [0, -70, 0]), (2.8, [0, -70, 0]), (3.4, [0, -20, 0])])}})
    a["luz_luna"] = anim(2.0, {"arm_r": {"rotation": keys([(0, [8, 0, 6]), (0.3, [-170, 0, 10]), (0.5, [-40, 0, 0]), (0.75, [-170, 0, 10]),
                                                         (0.95, [-40, 0, 0]), (1.2, [-170, 0, 10]), (1.4, [-40, 0, 0]), (2.0, [8, 0, 6])])},
                               "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-8, 15, 0]), (0.5, [6, -10, 0]), (1.4, [6, -10, 0]), (2.0, [0, 0, 0])])}})
    a["lanza_charge"] = anim(2.6, {"arm_r": {"rotation": keys([(0, [8, 0, 6]), (0.6, [-160, 0, 15]), (2.6, [-165, 0, 15])])},
                                   "arm_l": {"rotation": keys([(0, [8, 0, -6]), (0.6, [-40, 0, -30]), (2.6, [-40, 0, -30])])},
                                   "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-12, 20, 0]), (2.6, [-15, 25, 0])])},
                                   "head": {"rotation": keys([(0, [0, 0, 0]), (2.6, [-5, -15, 0])])}})
    a["lanza_throw"] = anim(0.8, {"arm_r": {"rotation": keys([(0, [-165, 0, 15]), (0.15, [-30, 0, 0]), (0.8, [8, 0, 6])])},
                                  "body": {"rotation": keys([(0, [-15, 25, 0]), (0.15, [15, -25, 0]), (0.8, [0, 0, 0])])}})
    a["tail_grab"] = anim(0.8, {"tail1": {"rotation": keys([(0, [-20, 0, 0]), (0.3, [-60, 0, 0]), (0.5, [10, 0, 0]), (0.8, [-30, 0, 0])])},
                                "tail2": {"rotation": keys([(0, [10, 0, 0]), (0.3, [-30, 0, 0]), (0.5, [20, 0, 0]), (0.8, [20, 0, 0])])},
                                "tail3": {"rotation": keys([(0, [10, 0, 0]), (0.5, [40, 0, 0]), (0.8, [40, 0, 0])])},
                                "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.5, [10, 0, 0]), (0.8, [0, 0, 0])])}})
    a["tail_throw"] = anim(0.6, {"tail1": {"rotation": keys([(0, [-30, 0, 0]), (0.2, [-30, 90, 0]), (0.4, [-20, -60, 0]), (0.6, [-25, 0, 0])])},
                                 "body": {"rotation": keys([(0, [0, 0, 0]), (0.2, [0, 30, 0]), (0.4, [0, -30, 0]), (0.6, [0, 0, 0])])}})
    a["regen"] = anim(0.8, {"arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.2, [0, 0, -60]), (0.5, [0, 0, -50]), (0.8, [8, 0, -6])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 30, 0]), (0.8, [0, 0, 0])])}})
    save_anims("ulquiorra", a)


# =====================================================================================
#  BARRAGAN LOUISENBAIRN
# =====================================================================================
BAR = [
    dict(skin="#C9A27E", skin_d="#9C7A5C", hair="#ECECE6", coat="#1A1A1E", coat_d="#0C0C0E", fur="#F1EFE6", fur_d="#C8C4B6",
         crown="#D9B23A", crown_d="#8A6A1E", gem="#9B4DFF", eye="#2A2A2A", pants="#202024", axe="#2A2A30", blade="#C9C2B0",
         cloak="#1B1022", cloak_d="#0B0610", glow="#B07AE0"),
    # Arrogante: esqueleto (osso cinza-branco), manto negro-roxo, olhos roxos brilhantes, coroa grande
    dict(skin=BONE, skin_d=BONE_D, hair=BONE, coat="#E2DCCB", coat_d="#9E9785", fur="#CFC8B5", fur_d="#9E9785",
         crown="#E0BA48", crown_d="#7A5A12", gem="#C06BFF", eye="#C06BFF", pants="#D6D0BE", axe="#2A2030", blade="#E6DECA",
         cloak="#1B1022", cloak_d="#0B0610", glow="#C06BFF"),
]


def ribs(color):
    def d(fs):
        f = fs["front"]
        for y in range(2, f.h - 2, 2):
            f.rect(1, y, f.w - 2, y, H(color))
        f.col(f.w // 2, H(color))
    return d


def old_face(pal, skull):
    def d(fs):
        f = fs["front"]
        if skull:
            f.fill(H(pal["skin"]))
            f.rect(1, 2, 2, 4, H(HOLE))
            f.rect(5, 2, 6, 4, H(HOLE))
            f.set(2, 3, H(pal["eye"]), True)
            f.set(5, 3, H(pal["eye"]), True)
            f.rect(3, 5, 4, 5, H(HOLE))
            for x in range(1, 7):
                f.set(x, 7, H(HOLE) if x % 2 else H(BONE_L))
                f.set(x, 8, H(BONE_D))
            for n in ("left", "right", "back", "top"):
                fs[n].scatter(H(BONE_D), 0.15, 7 + len(n))
        else:
            f.rect(0, 0, 7, 0, H(pal["hair"]))
            f.rect(1, 2, 2, 2, H(pal["hair"]))   # sobrancelhas brancas grossas
            f.rect(5, 2, 6, 2, H(pal["hair"]))
            f.set(2, 3, H("#F0F0EA"))
            f.set(1, 3, H(pal["eye"]))
            f.set(5, 3, H(pal["eye"]))
            f.set(6, 3, H("#F0F0EA"))
            f.rect(3, 4, 4, 5, H(pal["skin_d"]))
            # cicatriz sobre o olho esquerdo
            for y in range(1, 6):
                f.set(6, y, H("#7A4A3A"))
            for n in ("left", "right"):
                fs[n].rect(0, 0, fs[n].w - 1, 4, H(pal["hair"]))
            fs["back"].fill(H(pal["hair"]))
            fs["top"].fill(H(pal["skin_d"]))
            fs["top"].scatter(H(pal["hair"]), 0.3, 4)
    return d


def crown_deco(pal):
    def d(fs):
        for n in SIDES:
            f = fs[n]
            for x in range(f.w):
                if x % 2 == 1:
                    f.set(x, 0, CLEAR)
            f.row(f.h - 1, H(pal["crown_d"]))
            f.set(f.w // 2, f.h // 2, H(pal["gem"]), True)
    return d


def build_barragan(v):
    pal = BAR[v]
    skull = v == 1
    m = Model("barragan")
    m.bone("root", (0, 0, 0))
    for name, x0, px in (("leg_r", -5.5, -3), ("leg_l", 0.5, 3)):
        m.bone(name, (px, 14, 0), "root")
        m.cube(name, (x0, 0, -2.5), (5, 14, 5), P(pal["pants"], deco=chain(folds(darken(H(pal["pants"]), 0.15), 3),
                                                                             band([-1, -2, -3], "#0A0A0C" if not skull else BONE_D))))
    m.bone("body", (0, 14, 0), "root")
    m.cube("body", (-6, 14, -3.5), (12, 14, 7), P(pal["coat"], deco=chain(ribs(pal["coat_d"]) if skull else folds(pal["coat_d"], 4),
                                                                          band([0, 1], pal["crown_d"]) if not skull else None), noise=0.04))
    m.bone("coat_fur", (0, 26, 0), "body")
    m.cube("coat_fur", (-7, 25, -4.5), (14, 4, 9), P(pal["fur"], deco=fur(pal["fur_d"], WHITE_L, 13, density=0.35)))
    m.cube("coat_fur", (-6.5, 4, -4), (13, 10, 8), P(pal["coat"], deco=chain(folds(pal["coat_d"], 3), band([-1, -2], pal["fur"]))), inflate=0.25)
    m.bone("cloak", (0, 28, 2), "body")
    m.cube("cloak", (-7.5, 2, 2.5), (15, 26, 2), P(pal["cloak"], deco=chain(folds(pal["cloak_d"], 2), band([0], pal["glow"]))))
    m.cube("cloak", (-8, 24, -4.5), (16, 5, 9), P(pal["cloak"], deco=chain(fur(pal["cloak_d"], "#3A2448", 31, density=0.3), band([-1], pal["glow"]))))
    m.cube("cloak", (-5, 29, -2), (10, 10, 7), P(pal["cloak"], deco=folds(pal["cloak_d"], 3)), inflate=0.4)
    m.bone("head", (0, 28, 0), "body")
    m.cube("head", (-4, 28, -4), (8, 9, 8), P(pal["skin"], deco=old_face(pal, skull), noise=0.03))
    m.bone("beard", (0, 30, -4), "head")
    m.cube("beard", (-4, 23, -4.8), (8, 7, 2), P(pal["hair"], deco=chain(speckle("#D8D8D0", 0.4, 17), folds("#D0D0C8", 2))))
    m.cube("beard", (-3, 21, -4.6), (6, 2, 2), P(pal["hair"]))
    m.bone("crown_small", (0, 37, 0), "head")
    m.cube("crown_small", (-3.5, 37, -3.5), (7, 3, 7), P(pal["crown"], deco=crown_deco(pal)))
    m.bone("crown_big", (0, 37, 0), "head")
    m.cube("crown_big", (-4.5, 36.5, -4.5), (9, 5, 9), P(pal["crown"], deco=crown_deco(pal)))
    for k, (x, z) in enumerate(((-4, -4), (3, -4), (-4, 3), (3, 3))):
        m.cube("crown_big", (x, 41, z), (1, 3, 1), P(pal["crown"], deco=solid(pal["gem"], True, faces=("top",))))
    for name, x0, px in (("arm_r", -10, -7), ("arm_l", 6, 7)):
        m.bone(name, (px, 26, 0), "body")
        m.cube(name, (x0, 13, -2), (4, 13, 4), P(pal["coat"], deco=chain(band([0, 1, 2], pal["fur"]),
                                                                        band([-1, -2], pal["skin"])), noise=0.04))
    # Gran Caida: machado de cabo longo na mao direita
    # v1.3.2: o cabo passa pela MAO (pegada a 4 px do pe) e o osso aponta o machado para a frente; as laminas giram 90
    # graus no cabo (cutelo vertical). Antes o cabo subia por dentro do braco e a cabeca ficava no ombro.
    m.bone("axe", (-8, 14.5, 0), "arm_r", rotation=[110, 0, 0])
    m.cube("axe", (-8.5, 10.5, -0.5), (1, 26, 1), P(pal["axe"], deco=band([0, 9, 18], pal["crown"])))
    m.cube("axe", (-14, 28.5, -1), (5, 9, 2), P(pal["blade"], deco=chain(rim(darken(H(pal["blade"]), 0.25)), solid(pal["gem"], True, faces=("front",)) if skull else None)),
           rotation=[0, 90, 0], pivot=[-8, 33, 0])
    m.cube("axe", (-8, 29.5, -1), (4, 7, 2), P(pal["blade"], deco=rim(darken(H(pal["blade"]), 0.25))), rotation=[0, 90, 0], pivot=[-8, 33, 0])
    m.cube("axe", (-9, 36.5, -1), (3, 3, 2), P(pal["crown"]), rotation=[0, 90, 0], pivot=[-8, 38, 0])
    tex = ["barragan", "barragan_arrogante"][v]
    m.save(bounds=(4, 4), seed=21 + v, tex_name=tex, geo=(v == 0))


def barragan_anims():
    a = {}
    a["idle"] = anim(3.0, {"body": {"position": cyc(3.0, lambda p: [0, 0.2 * S(p), 0])},
                           "arm_r": {"rotation": cyc(3.0, lambda p: [-15 + 2 * S(p), 0, 8])},
                           "arm_l": {"rotation": cyc(3.0, lambda p: [-3 * S(p), 0, -8])},
                           "head": {"rotation": cyc(6.0, lambda p: [-4 + 2 * S(p), 6 * S(p), 0])},
                           "cloak": {"rotation": cyc(3.0, lambda p: [4 + 2 * S(p), 0, 0])}}, loop=True)
    a["sleep"] = anim(4.0, {"body": {"position": cyc(4.0, lambda p: [0, 0.15 * S(p), 0])},
                            "head": {"rotation": keys([(0, [10, 0, 0]), (4.0, [10, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-20, 0, 8]), (4.0, [-20, 0, 8])])}}, loop=True)
    a["walk"] = anim(1.3, {"leg_l": {"rotation": cyc(1.3, lambda p: [24 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(1.3, lambda p: [-24 * S(p), 0, 0])},
                           "arm_l": {"rotation": cyc(1.3, lambda p: [-18 * S(p), 0, -6])},
                           "arm_r": {"rotation": keys([(0, [-20, 0, 8]), (1.3, [-20, 0, 8])])},
                           "body": {"position": cyc(1.3, lambda p: [0, 0.4 * abs(S(p)), 0]), "rotation": cyc(1.3, lambda p: [0, 4 * S(p), 0])},
                           "cloak": {"rotation": cyc(1.3, lambda p: [10 + 4 * S(p), 0, 0])}}, loop=True)
    a["intro"] = anim(3.0, {"arm_r": {"rotation": keys([(0, [-20, 0, 8]), (1.0, [-150, 0, 10]), (1.6, [-150, 0, 10]), (2.2, [-20, 0, 8]), (3.0, [-20, 0, 8])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-15, 0, 0]), (3.0, [0, 0, 0])])},
                            "body": {"position": keys([(0, [0, 0, 0]), (2.0, [0, -1, 0]), (2.2, [0, 0, 0]), (3.0, [0, 0, 0])])}})
    a["transform"] = anim(2.0, {"body": {"rotation": cyc(2.0, lambda p: [20 + 4 * S(p), 0, 0])},
                                "head": {"rotation": cyc(2.0, lambda p: [-30 + 5 * S(p), 0, 0])},
                                "arm_r": {"rotation": cyc(2.0, lambda p: [-40, 0, 40 + 5 * S(p)])},
                                "arm_l": {"rotation": cyc(2.0, lambda p: [-40, 0, -40 - 5 * S(p)])}}, loop=True)
    a["release"] = anim(1.4, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-170, 0, 30]), (1.4, [-20, 0, 8])])},
                              "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-170, 0, -30]), (1.4, [0, 0, -8])])},
                              "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-35, 0, 0]), (1.4, [0, 0, 0])])}})
    a["death"] = anim(4.5, {"body": {"rotation": keys([(0, [0, 0, 0]), (1.5, [15, 0, 0]), (3.0, [35, 0, 8]), (4.5, [80, 0, 10])]),
                                     "position": keys([(0, [0, 0, 0]), (3.0, [0, -6, 0]), (4.5, [0, -12, 0])])},
                            "leg_l": {"rotation": keys([(0, [0, 0, 0]), (3.0, [-80, 0, 0]), (4.5, [-90, 0, 0])])},
                            "leg_r": {"rotation": keys([(0, [0, 0, 0]), (3.0, [-80, 0, 0]), (4.5, [-90, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-20, 0, 8]), (4.5, [-10, 0, 40])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (4.5, [30, 0, 0])])}})
    a["axe_smash"] = anim(1.1, {"arm_r": {"rotation": keys([(0, [-20, 0, 8]), (0.55, [-185, 0, 10]), (0.75, [-40, 0, 0]), (1.1, [-20, 0, 8])])},
                                "body": {"rotation": keys([(0, [0, 0, 0]), (0.55, [-15, 10, 0]), (0.75, [20, -5, 0]), (1.1, [0, 0, 0])])},
                                "leg_r": {"rotation": keys([(0, [0, 0, 0]), (0.75, [-20, 0, 0]), (1.1, [0, 0, 0])])}})
    a["axe_throw"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [-20, 0, 8]), (0.5, [-190, 30, 10]), (0.7, [-30, -20, 0]), (1.0, [-20, 0, 8])])},
                                "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-10, 30, 0]), (0.7, [10, -30, 0]), (1.0, [0, 0, 0])])}})
    a["respira"] = anim(2.5, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.9, [-35, 0, 0]), (1.2, [15, 0, 0]), (2.2, [15, 0, 0]), (2.5, [0, 0, 0])])},
                              "body": {"rotation": keys([(0, [0, 0, 0]), (0.9, [-12, 0, 0]), (1.2, [12, 0, 0]), (2.2, [12, 0, 0]), (2.5, [0, 0, 0])]),
                                       "scale": keys([(0, [1, 1, 1]), (0.9, [1.06, 1.04, 1.1]), (1.2, [1, 1, 1]), (2.5, [1, 1, 1])])},
                              "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.9, [-30, 0, -50]), (1.2, [-80, 0, -10]), (2.2, [-80, 0, -10]), (2.5, [0, 0, -8])])}})
    a["grab"] = anim(1.2, {"arm_l": {"rotation": keys([(0, [0, 0, -8]), (0.5, [-60, 0, -60]), (0.9, [-95, 0, 0]), (1.2, [0, 0, -8])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, -20, 0]), (0.9, [10, 15, 0]), (1.2, [0, 0, 0])])}})
    a["dome"] = anim(2.0, {"arm_r": {"rotation": keys([(0, [-20, 0, 8]), (0.8, [-30, 0, 80]), (2.0, [-20, 0, 8])])},
                           "arm_l": {"rotation": keys([(0, [0, 0, -8]), (0.8, [-30, 0, -80]), (2.0, [0, 0, -8])])},
                           "head": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-30, 0, 0]), (2.0, [0, 0, 0])])}})
    a["summon"] = anim(1.5, {"arm_r": {"rotation": keys([(0, [-20, 0, 8]), (0.5, [-160, 0, 8]), (1.0, [-160, 0, 8]), (1.5, [-20, 0, 8])])},
                             "body": {"position": keys([(0, [0, 0, 0]), (1.0, [0, -1, 0]), (1.5, [0, 0, 0])])}})
    save_anims("barragan", a)


# =====================================================================================
#  GRIMMJOW JAEGERJAQUEZ
# =====================================================================================
GRI = [
    dict(skin="#E9D2BC", skin_d="#C2A68C", hair="#2FA8E8", hair_d="#1A6FA8", coat=WHITE, coat_d=WHITE_D, sash=SASH, legs=WHITE,
         mark="#2FE0C8", bone=BONE, claw=BONE, tail=WHITE),
    # Pantera: armadura branca de osso, cabelo azul longo, orelhas, cauda, laminas nos cotovelos, garras
    dict(skin="#E9D2BC", skin_d="#C2A68C", hair="#39C2FF", hair_d="#1A6FA8", coat="#F4F3EE", coat_d="#BFC6CC", sash="#1A6FA8",
         legs="#F4F3EE", mark="#39C2FF", bone="#F7F6F0", claw="#E8F6FF", tail="#F4F3EE"),
]


def grimm_face(pal):
    def d(fs):
        f = fs["front"]
        f.rect(0, 0, 7, 1, H(pal["hair"]))
        f.set(0, 2, H(pal["hair"]))
        f.set(7, 2, H(pal["hair"]))
        # olhos azuis com marcas verde-agua embaixo
        f.rect(1, 3, 2, 3, H("#F8F8F4"))
        f.rect(5, 3, 6, 3, H("#F8F8F4"))
        f.set(2, 3, H("#2F80FF"), True)
        f.set(5, 3, H("#2F80FF"), True)
        f.set(1, 4, H(pal["mark"]), True)
        f.set(2, 4, H(pal["mark"]), True)
        f.set(5, 4, H(pal["mark"]), True)
        f.set(6, 4, H(pal["mark"]), True)
        # sorriso de canto
        f.rect(2, 6, 5, 6, H(darken(H(pal["skin"]), 0.35)))
        f.set(5, 5, H(darken(H(pal["skin"]), 0.35)))
        for n in ("top", "back"):
            fs[n].fill(H(pal["hair"]))
            fs[n].scatter(H(pal["hair_d"]), 0.3, 5 + len(n))
        for n in ("left", "right"):
            fs[n].rect(0, 0, fs[n].w - 1, 2, H(pal["hair"]))
    return d


def abs_hole(pal):
    def d(fs):
        f = fs["front"]
        for y in (4, 6, 8):
            f.set(3, y, H(pal["skin_d"]))
            f.set(4, y, H(pal["skin_d"]))
        f.col(f.w // 2, H(pal["skin_d"]))
        hole("front", 4, 9.5, 1.6)(fs)
    return d


def build_grimmjow(v):
    pal = GRI[v]
    m = Model("grimmjow")
    m.bone("root", (0, 0, 0))
    for name, x0, px in (("leg_r", -4, -2), ("leg_l", 0, 2)):
        m.bone(name, (px, 12, 0), "root")
        m.cube(name, (x0, 0, -2), (4, 12, 4), P(pal["legs"], deco=chain(folds(darken(H(pal["legs"]), 0.12), 3), band([-1, -2], "#151518")), noise=0.03))
    m.bone("body", (0, 12, 0), "root")
    m.cube("body", (-4, 12, -2), (8, 12, 4), P(pal["skin"] if v == 0 else pal["coat"],
                                              deco=chain(abs_hole(pal), band([0, 1], pal["sash"])) if v == 0 else
                                              chain(folds(pal["coat_d"], 2), hole("front", 4, 9.5, 1.6), band([-1, -2], pal["mark"]))))
    m.bone("jacket", (0, 12, 0), "body")

    def open_jacket(fs, pal=pal):
        f = fs["front"]
        for y in range(f.h):
            for x in range(2, f.w - 2):
                f.set(x, y, CLEAR)
        for n in ("left", "right", "back"):
            fs[n].col(0, H(pal["coat_d"]))
        fs["front"].row(f.h - 2, H(pal["sash"]))
    m.cube("jacket", (-4, 12, -2), (8, 12, 4), P(pal["coat"], deco=open_jacket), inflate=0.35)
    m.bone("head", (0, 24, 0), "body")
    m.cube("head", (-4, 24, -4), (8, 8, 8), P(pal["skin"], deco=grimm_face(pal), noise=0.02))
    # cabelo espetado azul
    m.cube("head", (-4.5, 30, -4.5), (9, 3, 9), P(pal["hair"], deco=speckle(pal["hair_d"], 0.3, 7)))
    m.cube("head", (-2, 32, -3), (4, 3, 4), P(pal["hair"], deco=speckle(pal["hair_d"], 0.3, 8)), rotation=[-20, 0, 0], pivot=[0, 32, -1])
    m.bone("jaw_mask", (0, 24, 0), "head")
    m.cube("jaw_mask", (-4.7, 23.5, -4.7), (4, 3, 6), P(pal["bone"], deco=chain(speckle(BONE_D, 0.15, 3),
                                                                                    lambda fs: [fs["front"].set(x, 0, H(HOLE)) for x in range(0, 4, 2)])))
    m.bone("hair_long", (0, 30, 3), "head")
    m.cube("hair_long", (-4.5, 10, 2.8), (9, 20, 2), P(pal["hair"], deco=chain(speckle(pal["hair_d"], 0.35, 22), folds(pal["hair_d"], 2))))
    m.bone("ears", (0, 31, 0), "head")
    for side in (-1, 1):
        x = 2 if side > 0 else -4
        m.cube("ears", (x, 32, -1), (2, 3, 1), P(pal["coat"], deco=solid(pal["mark"], faces=("front",))), rotation=[0, 0, -15 * side],
               pivot=[x + 1, 32, -0.5])
    for name, x0, px in (("arm_r", -8, -5), ("arm_l", 4, 5)):
        m.bone(name, (px, 22, 0), "body")
        m.cube(name, (x0, 12, -2), (4, 12, 4), P(pal["coat"], deco=chain(band([-1, -2, -3, -4, -5], pal["skin"]), band([-6], pal["coat_d"])),
                                                 noise=0.03))
        side = name[-1]
        fin = "fin_" + side
        m.bone(fin, (px, 16, 1), name)
        fx = x0 + (0 if side == "r" else 3)
        m.cube(fin, (fx, 15, 1.5), (1, 6, 5), P(pal["bone"], deco=chain(band([0], pal["mark"]), speckle(BONE_D, 0.2, 9))),
               rotation=[-30, 0, 0], pivot=[fx + 0.5, 16, 2])
        claw = "claws_" + side
        m.bone(claw, (px, 12, 0), name)
        for k in range(3):
            m.cube(claw, (x0 + 0.5 + k * 1.2, 9.5, -2.2), (1, 3, 1), P(pal["claw"], deco=solid(pal["mark"], True, faces=("bottom",))))
    m.bone("tail1", (0, 12, 2), "body")
    m.cube("tail1", (-1, 11, 2), (2, 2, 9), P(pal["tail"], deco=band([0], pal["mark"])))
    m.bone("tail2", (0, 12, 11), "tail1")
    m.cube("tail2", (-1, 11, 11), (2, 2, 9), P(pal["tail"], deco=band([-1], pal["mark"])))
    tex = ["grimmjow", "grimmjow_pantera"][v]
    m.save(bounds=(3, 3), seed=31 + v, tex_name=tex, geo=(v == 0))


def grimmjow_anims():
    a = {}
    a["idle"] = anim(1.6, {"body": {"position": cyc(1.6, lambda p: [0, 0.3 * S(p), 0]), "rotation": keys([(0, [6, 0, 0]), (1.6, [6, 0, 0])])},
                           "arm_r": {"rotation": cyc(1.6, lambda p: [-20 + 4 * S(p), 0, 12])},
                           "arm_l": {"rotation": cyc(1.6, lambda p: [-20 - 4 * S(p), 0, -12])},
                           "head": {"rotation": cyc(3.2, lambda p: [-6, 8 * S(p), 0])},
                           "tail1": {"rotation": cyc(1.6, lambda p: [-20, 20 * S(p), 0])},
                           "tail2": {"rotation": cyc(1.6, lambda p: [15, 25 * S(p - 0.2), 0])}}, loop=True)
    a["sleep"] = anim(4.0, {"arm_r": {"rotation": keys([(0, [0, 0, 6]), (4.0, [0, 0, 6])])},
                            "arm_l": {"rotation": keys([(0, [0, 0, -6]), (4.0, [0, 0, -6])])},
                            "head": {"rotation": keys([(0, [-8, 20, 0]), (4.0, [-8, 20, 0])])}}, loop=True)
    a["walk"] = anim(0.6, {"leg_l": {"rotation": cyc(0.6, lambda p: [35 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(0.6, lambda p: [-35 * S(p), 0, 0])},
                           "arm_l": {"rotation": cyc(0.6, lambda p: [-40 * S(p), 0, -6])},
                           "arm_r": {"rotation": cyc(0.6, lambda p: [40 * S(p), 0, 6])},
                           "body": {"position": cyc(0.6, lambda p: [0, 0.5 * abs(S(p)), 0]), "rotation": keys([(0, [14, 0, 0]), (0.6, [14, 0, 0])])},
                           "tail1": {"rotation": cyc(0.6, lambda p: [-10, 15 * S(p), 0])}}, loop=True)
    a["intro"] = anim(3.0, {"head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-20, 0, 0]), (2.0, [-20, 0, 0]), (3.0, [0, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-90, 0, 40]), (2.0, [-90, 0, 40]), (3.0, [-20, 0, 12])])},
                            "arm_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-90, 0, -40]), (2.0, [-90, 0, -40]), (3.0, [-20, 0, -12])])}})
    a["transform"] = anim(1.0, {"body": {"rotation": cyc(1.0, lambda p: [-20 + 4 * S(p), 0, 0])},
                                "head": {"rotation": cyc(1.0, lambda p: [-40, 0, 3 * S(p)])},
                                "arm_r": {"rotation": cyc(1.0, lambda p: [-30, 0, 70 + 6 * S(p)])},
                                "arm_l": {"rotation": cyc(1.0, lambda p: [-30, 0, -70 - 6 * S(p)])}}, loop=True)
    a["release"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-100, 0, -20]), (0.6, [-60, 0, 80]), (1.0, [-20, 0, 12])])},
                              "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-60, 0, -80]), (1.0, [-20, 0, -12])])}})
    a["death"] = anim(3.5, {"body": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-15, 0, 0]), (2.5, [60, 0, 0]), (3.5, [85, 0, 0])]),
                                     "position": keys([(0, [0, 0, 0]), (2.5, [0, -8, 0]), (3.5, [0, -11, 0])])},
                            "leg_l": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-80, 0, 0]), (3.5, [-90, 0, 0])])},
                            "leg_r": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-70, 0, 0]), (3.5, [-90, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-150, 0, 20]), (3.5, [-20, 0, 40])])}})
    a["punch"] = anim(0.45, {"arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.12, [-30, 30, 0]), (0.22, [-95, -10, 0]), (0.45, [-20, 0, 12])])},
                             "body": {"rotation": keys([(0, [6, 0, 0]), (0.12, [6, 25, 0]), (0.22, [10, -25, 0]), (0.45, [6, 0, 0])])}})
    a["punch2"] = anim(0.55, {"arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.15, [-40, -30, 0]), (0.27, [-110, 15, 0]), (0.55, [-20, 0, -12])])},
                              "body": {"rotation": keys([(0, [6, 0, 0]), (0.15, [6, -30, 0]), (0.27, [14, 30, 0]), (0.55, [6, 0, 0])])},
                              "leg_r": {"rotation": keys([(0, [0, 0, 0]), (0.27, [-30, 0, 0]), (0.55, [0, 0, 0])])}})
    a["sonido"] = anim(0.3, {"body": {"position": keys([(0, [0, -2, 0]), (0.15, [0, -1, 0]), (0.3, [0, 0, 0])]),
                                      "rotation": keys([(0, [25, 0, 0]), (0.3, [6, 0, 0])])}})
    a["cero"] = anim(1.4, {"arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.3, [-90, 0, 0]), (1.2, [-90, 0, 0]), (1.4, [-20, 0, 12])])},
                           "body": {"rotation": keys([(0, [6, 0, 0]), (0.3, [6, 15, 0]), (1.2, [6, 15, 0]), (1.4, [6, 0, 0])])}})
    a["gran_rey_cero"] = anim(2.6, {"arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.4, [-60, 0, 30]), (0.7, [-60, 0, 30]), (1.0, [-20, 0, -12])])},
                                    "arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.4, [-60, 0, -20]), (1.0, [-95, 0, 0]), (2.2, [-95, 0, 0]),
                                                               (2.4, [-110, 0, 0]), (2.6, [-20, 0, 12])])},
                                    "body": {"rotation": keys([(0, [6, 0, 0]), (1.0, [6, 20, 0]), (2.2, [6, 20, 0]), (2.4, [-6, 20, 0]), (2.6, [6, 0, 0])])},
                                    "leg_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [20, 0, 0]), (2.4, [20, 0, 0]), (2.6, [0, 0, 0])])}})
    a["garra"] = anim(1.1, {"arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.5, [-10, 0, 110]), (0.8, [-10, 0, 110]), (1.1, [-20, 0, 12])])},
                            "arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.5, [-10, 0, -110]), (0.8, [-10, 0, -110]), (1.1, [-20, 0, -12])])},
                            "body": {"rotation": keys([(0, [6, 0, 0]), (0.5, [-10, 0, 0]), (0.8, [15, 0, 0]), (1.1, [6, 0, 0])])}})
    a["desgarron"] = anim(1.8, {"arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.7, [-170, 20, 20]), (0.95, [-20, -40, 0]), (1.8, [-20, 0, 12])])},
                                "arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.7, [-60, 0, -40]), (1.8, [-20, 0, -12])])},
                                "body": {"rotation": keys([(0, [6, 0, 0]), (0.7, [-15, 25, 0]), (0.95, [25, -30, 0]), (1.8, [6, 0, 0])])},
                                "head": {"rotation": keys([(0, [0, 0, 0]), (0.7, [-20, 0, 0]), (1.8, [0, 0, 0])])}})
    a["roar"] = anim(1.0, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-45, 0, 0]), (0.8, [-45, 0, 0]), (1.0, [0, 0, 0])])},
                           "body": {"rotation": keys([(0, [6, 0, 0]), (0.35, [-20, 0, 0]), (0.8, [-20, 0, 0]), (1.0, [6, 0, 0])])},
                           "arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.35, [-30, 0, 60]), (1.0, [-20, 0, 12])])},
                           "arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.35, [-30, 0, -60]), (1.0, [-20, 0, -12])])}})
    a["pounce"] = anim(0.8, {"body": {"rotation": keys([(0, [6, 0, 0]), (0.2, [40, 0, 0]), (0.6, [20, 0, 0]), (0.8, [6, 0, 0])])},
                             "arm_r": {"rotation": keys([(0, [-20, 0, 12]), (0.2, [-160, 0, 20]), (0.6, [-60, 0, 10]), (0.8, [-20, 0, 12])])},
                             "arm_l": {"rotation": keys([(0, [-20, 0, -12]), (0.2, [-160, 0, -20]), (0.6, [-60, 0, -10]), (0.8, [-20, 0, -12])])},
                             "leg_l": {"rotation": keys([(0, [0, 0, 0]), (0.2, [40, 0, 0]), (0.8, [0, 0, 0])])},
                             "leg_r": {"rotation": keys([(0, [0, 0, 0]), (0.2, [40, 0, 0]), (0.8, [0, 0, 0])])}})
    save_anims("grimmjow", a)


# =====================================================================================
#  Itens, ovos, icone do status e vinheta
# =====================================================================================
def item_tex(name, draw):
    c = Canvas(16, 16)
    draw(c)
    c.outline((20, 20, 24, 255))
    c.save(os.path.join(ASSETS, "textures/item", name + ".png"))
    write_json(os.path.join(ASSETS, "models/item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/" + name}})


def draw_reishi(c):
    glass = hex_rgba("#BFEFFF", 200)
    c.rect(6, 2, 9, 3, hex_rgba("#8A6A3A"))
    c.rect(7, 4, 8, 5, glass)
    c.circle(7.5, 10, 4.2, glass)
    c.circle(7.5, 10.5, 3.2, hex_rgba("#7FE8FF"))
    c.circle(6.5, 9.5, 1.2, hex_rgba("#E8FFFF"))
    for (x, y) in ((8, 11), (6, 12), (9, 9)):
        c.set(x, y, hex_rgba("#FFFFFF"))


def draw_wing(c):
    for x in range(1, 15):
        top = 3 + int(abs(math.sin(x * 0.4)) * 2)
        bot = 12 - int(abs(math.sin((x + 1) * math.pi / 4.5)) * 3)
        for y in range(top, bot):
            c.set(x, y, hex_rgba("#121216"))
    for x in range(1, 15):
        c.set(x, 3 + x // 4, hex_rgba("#3DFF8E"))
    for k in range(3):
        for t in range(9):
            c.set(2 + t + k, 4 + t * (k + 1) // 3, hex_rgba("#1F8A4E"))


def draw_shard(c):
    pts = [(8, 1), (12, 6), (10, 14), (5, 13), (3, 6)]
    for y in range(16):
        for x in range(16):
            inside = True
            for i in range(len(pts)):
                x1, y1 = pts[i]
                x2, y2 = pts[(i + 1) % len(pts)]
                if (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) < 0:
                    inside = False
                    break
            if inside:
                c.set(x, y, hex_rgba("#D9B23A") if (x + y) % 5 else hex_rgba("#F2D27A"))
    c.circle(7.5, 7.5, 1.6, hex_rgba("#9B4DFF"))
    c.set(7, 7, hex_rgba("#E0C0FF"))


def draw_claw(c):
    for k, x0 in enumerate((3, 7, 11)):
        for t in range(10):
            x = x0 - t // 4
            y = 2 + t
            c.set(x, y, hex_rgba("#F4F3EE"))
            c.set(x + 1, y, hex_rgba("#C9D2DA"))
        c.set(x0 - 2, 12, hex_rgba("#39C2FF"))
    c.rect(2, 12, 13, 14, hex_rgba("#2FA8E8"))


def effect_icon():
    c = Canvas(18, 18)
    # ampulheta sepia com areia caindo
    frame = hex_rgba("#6A5034")
    sand = hex_rgba("#D8C08A")
    for y in range(2, 16):
        w = abs(y - 9) * 0.6 + 1
        for x in range(18):
            if abs(x - 8.5) <= w:
                c.set(x, y, hex_rgba("#F0E6D0", 160))
    c.rect(3, 1, 14, 2, frame)
    c.rect(3, 15, 14, 16, frame)
    for y in range(3, 8):
        w = abs(y - 9) * 0.6 - 0.5
        for x in range(18):
            if abs(x - 8.5) <= w:
                c.set(x, y, sand)
    c.set(8, 9, sand)
    c.set(9, 10, sand)
    c.rect(5, 13, 12, 14, sand)
    c.save(os.path.join(ASSETS, "textures/mob_effect", "aged.png"))


def vignette():
    """Vinheta 256x256: branca, transparente no centro e opaca nas bordas (o jogo tinge com a cor)."""
    c = Canvas(256, 256)
    for y in range(256):
        for x in range(256):
            dx = (x + 0.5 - 128) / 128.0
            dy = (y + 0.5 - 128) / 128.0
            d = math.sqrt(dx * dx + dy * dy)
            a = max(0.0, min(1.0, (d - 0.45) / 0.75))
            a = a * a * (3 - 2 * a)
            c.set(x, y, (255, 255, 255, int(a * 255)))
    c.save(os.path.join(ASSETS, "textures/misc", "espada_vignette.png"))


def eggs():
    for e in ("ulquiorra", "barragan", "grimmjow"):
        write_json(os.path.join(ASSETS, "models/item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def main():
    for v in range(3):
        build_ulquiorra(v)
    ulquiorra_anims()
    for v in range(2):
        build_barragan(v)
    barragan_anims()
    for v in range(2):
        build_grimmjow(v)
    grimmjow_anims()
    item_tex("reishi_restoration", draw_reishi)
    item_tex("murcielago_wing", draw_wing)
    item_tex("arrogante_shard", draw_shard)
    item_tex("pantera_claw", draw_claw)
    effect_icon()
    vignette()
    eggs()
    print("espada: assets gerados")


if __name__ == "__main__":
    main()
