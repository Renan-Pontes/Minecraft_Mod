"""
Modelos GeckoLib (geo + texturas + glowmask) e animacoes do modulo hueco_mundo:
Vazio Rastejante, Vazio Brutamontes, Vazio Alado, Menos Gigante, Arrancar (5 variacoes de mascara/pele),
Rei Vazio (+ pele da Ressurreicao), Imperatriz das Areias (+ pele da Ressurreicao), Fada Travessa, Cao de Espinhos
e Rainha dos Espinhos (+ pele do Veredito). Chamado por tools/gen/hueco_mundo.py.
Frente do modelo = -Z. Unidades em pixels (16 = 1 bloco).
"""
import math
import random

from hueco_mundo_lib import (Model, P, H, CLEAR, chain, band, hem, folds, speckle, bark, cracks, rim, solid, glow_faces,
                             cyc, keys, S, C, anim, save_anims, eyes, teeth, veins, fur, lighten, darken, SIDES)

# ---------------------------------------------------------------- paletas
BLK, BLK_D, BLK_L = "#1C1B22", "#0E0E12", "#2E2C38"
BONE, BONE_D, BONE_L = "#EDE8DC", "#C9C2B0", "#FFFDF6"
HOLE = "#050507"
RED, RED_L = "#FF3550", "#FFB0BC"
GOLD, GOLD_D, GOLD_L = "#C9A23A", "#8A6A1E", "#F2D27A"


def hole(face, cx, cy, r):
    """Buraco de Vazio (circulo preto com borda escura) numa face."""
    def d(fs):
        f = fs[face]
        for y in range(f.h):
            for x in range(f.w):
                dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if dd <= r:
                    f.set(x, y, H(HOLE))
                elif dd <= r + 0.9:
                    f.set(x, y, H("#3A3640"))
    return d


def mask_face(eye_y=1, eye_xs=(1, None), glow=RED, nose=True, tooth_row=True):
    """Mascara de osso: orbitas pretas com pupila brilhante, nariz e dentes na base."""
    def d(fs):
        f = fs["front"]
        xs = [eye_xs[0], eye_xs[1] if eye_xs[1] is not None else f.w - eye_xs[0] - 2]
        for x in xs:
            f.rect(x, eye_y, x + 1, eye_y + 1, H(HOLE))
            f.set(x + (1 if x < f.w // 2 else 0), eye_y + 1, H(glow), True)
        if nose and f.h > 4:
            f.set(f.w // 2, eye_y + 2, H(BONE_D))
            f.set(f.w // 2 - 1 if f.w % 2 == 0 else f.w // 2, eye_y + 2, H(BONE_D))
        if tooth_row:
            for x in range(0, f.w):
                f.set(x, f.h - 1, H(HOLE) if x % 2 else H(BONE_L))
        for n in ("left", "right", "top"):
            fs[n].scatter(H(BONE_D), 0.12, 31 + len(n))
    return d


def plates(color, step=3, faces=("top",)):
    def d(fs):
        for n in faces:
            f = fs[n]
            for y in range(0, f.h, step):
                f.row(y, H(color))
    return d


# =====================================================================================
#  VAZIO RASTEJANTE
# =====================================================================================
def build_crawler():
    m = Model("hollow_crawler")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 7, 0), "root")
    m.cube("body", (-5, 4, -9), (10, 6, 18), P(BLK, deco=chain(speckle(BLK_L, 0.18, 3), plates(BONE_D, 4), hole("top", 5, 9, 2.2),
                                                             hole("left", 9, 3, 1.4), hole("right", 9, 3, 1.4))))
    for k, z in enumerate((-6, -2, 2, 6)):
        m.cube("body", (-0.5, 10, z), (1, 3 - (k % 2), 1), P(BONE, noise=0.02))
    m.bone("head", (0, 8, -9), "body")
    m.cube("head", (-4, 4, -16), (8, 6, 7), P(BONE, deco=mask_face(eye_y=1, eye_xs=(1, None))))
    m.cube("head", (-1, 10, -15), (2, 2, 6), P(BONE_D))
    m.bone("jaw", (0, 5, -10), "head")
    m.cube("jaw", (-3.5, 2, -15.5), (7, 2, 6), P(BONE_D, deco=teeth(BONE_L, HOLE, "front", False, 2)))
    for side in (-1, 1):
        for k, z in enumerate((-6, 0, 6)):
            name = ("leg_l" if side < 0 else "leg_r") + str(k + 1)
            m.bone(name, (5 * side, 7, z), "body")
            ux = 5 if side > 0 else -9
            lx = 8 if side > 0 else -10
            m.cube(name, (ux, 6, z - 1), (4, 2, 2), P(BLK, deco=band([0], BONE_D)))
            m.cube(name, (lx, 0, z - 1), (2, 7, 2), P(BONE_D, deco=band([6], HOLE)))
    m.bone("tail1", (0, 7, 9), "body")
    m.cube("tail1", (-2, 5, 9), (4, 3, 7), P(BLK, deco=plates(BONE_D, 3)))
    m.bone("tail2", (0, 6.5, 16), "tail1")
    m.cube("tail2", (-1, 5.5, 16), (2, 2, 7), P(BONE))
    m.save(bounds=(3, 2))

    legs_a = ["leg_l1", "leg_r2", "leg_l3"]
    legs_b = ["leg_r1", "leg_l2", "leg_r3"]
    a = {}
    a["idle"] = anim(2.0, {"body": {"position": cyc(2.0, lambda p: [0, 0.3 * S(p), 0])},
                           "head": {"rotation": cyc(4.0, lambda p: [2 * S(p), 8 * S(p), 0])},
                           "jaw": {"rotation": cyc(2.0, lambda p: [4 + 3 * S(p), 0, 0])},
                           "tail1": {"rotation": cyc(2.0, lambda p: [0, 10 * S(p), 0])},
                           "tail2": {"rotation": cyc(2.0, lambda p: [0, 14 * S(p - 0.15), 0])}}, loop=True)
    walk = {"body": {"rotation": cyc(0.6, lambda p: [0, 0, 3 * S(p)])},
            "tail1": {"rotation": cyc(0.6, lambda p: [0, 14 * S(p), 0])}, "tail2": {"rotation": cyc(0.6, lambda p: [0, 18 * S(p - 0.2), 0])}}
    for n in legs_a:
        walk[n] = {"rotation": cyc(0.6, lambda p: [0, 25 * S(p), 10 * max(0, S(p))])}
    for n in legs_b:
        walk[n] = {"rotation": cyc(0.6, lambda p: [0, -25 * S(p), 10 * max(0, -S(p))])}
    a["walk"] = anim(0.6, walk, loop=True)
    a["bite"] = anim(0.5, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.3, [12, 0, 0]), (0.5, [0, 0, 0])]),
                                    "position": keys([(0, [0, 0, 0]), (0.3, [0, 0, -2]), (0.5, [0, 0, 0])])},
                           "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.15, [40, 0, 0]), (0.3, [0, 0, 0]), (0.5, [4, 0, 0])])}})
    leap = {"body": {"rotation": keys([(0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.45, [10, 0, 0]), (0.6, [0, 0, 0])])}}
    for n in legs_a + legs_b:
        leap[n] = {"rotation": keys([(0, [0, 0, 0]), (0.15, [0, 0, -40 if "_l" in n else 40]), (0.6, [0, 0, 0])])}
    a["leap"] = anim(0.6, leap)
    a["emerge"] = anim(0.6, {"root": {"position": keys([(0, [0, -10, 0]), (0.3, [0, 2, 0]), (0.6, [0, 0, 0])])},
                             "head": {"rotation": keys([(0, [-30, 0, 0]), (0.3, [-35, 0, 0]), (0.6, [0, 0, 0])])},
                             "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.3, [45, 0, 0]), (0.6, [4, 0, 0])])}})
    bur = {"head": {"rotation": cyc(3.0, lambda p: [-8, 6 * S(p), 0])}, "body": {"position": keys([(0, [0, -2, 0]), (3.0, [0, -2, 0])])}}
    for n in legs_a + legs_b:
        bur[n] = {"rotation": keys([(0, [0, 0, -55 if "_l" in n else 55]), (3.0, [0, 0, -55 if "_l" in n else 55])])}
    a["burrowed"] = anim(3.0, bur, loop=True)
    save_anims("hollow_crawler", a)


# =====================================================================================
#  VAZIO BRUTAMONTES
# =====================================================================================
def build_brute():
    m = Model("hollow_brute")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 14, 0), "root")
    for name, x in (("leg_l", 2), ("leg_r", -7)):
        m.bone(name, (x + 2.5, 14, 1), "body")
        m.cube(name, (x, 0, -1), (5, 14, 5), P(BLK, deco=chain(fur(BLK_D, BLK_L, 5 + x, density=0.2), band([13], BONE_D))))
    m.cube("body", (-6, 12, -4), (12, 4, 8), P(BLK_D, deco=speckle(BLK_L, 0.2, 7)))
    m.cube("body", (-8, 16, -5), (16, 14, 10), P(BLK, deco=chain(fur(BLK_D, BLK_L, 9, density=0.22), hole("front", 8, 6, 3.0))))
    m.cube("body", (-9, 28, -5.5), (6, 3, 11), P(BONE, deco=plates(BONE_D, 2, faces=SIDES)))
    m.cube("body", (3, 28, -5.5), (6, 3, 11), P(BONE, deco=plates(BONE_D, 2, faces=SIDES)))
    m.bone("head", (0, 29, -3), "body")
    m.cube("head", (-4.5, 27, -11), (9, 8, 8), P(BONE, deco=mask_face(eye_y=2, eye_xs=(1, None))))
    m.cube("head", (-6.5, 32, -9), (2, 6, 2), P(BONE_D), rotation=[0, 0, 25], pivot=[-5.5, 32, -8])
    m.cube("head", (4.5, 32, -9), (2, 6, 2), P(BONE_D), rotation=[0, 0, -25], pivot=[5.5, 32, -8])
    for name, x0, sx in (("arm_l", 8, 1), ("arm_r", -14, -1)):
        m.bone(name, (8.5 * sx, 29, 0), "body")
        m.cube(name, (x0, 15, -3), (6, 14, 6), P(BLK, deco=fur(BLK_D, BLK_L, 11 + x0, density=0.22)))
        fx = x0 - 1 if sx > 0 else x0 - 1
        m.cube(name, (fx, 3, -4), (8, 12, 8), P(BLK_D, deco=chain(band([0, 1], BLK), speckle(BLK_L, 0.15, 12))))
        m.cube(name, (fx - 0.5, 3, -4.5), (9, 5, 1), P(BONE, deco=band([4], BONE_D)))
    m.save(bounds=(4, 4))

    a = {}
    a["idle"] = anim(2.4, {"body": {"position": cyc(2.4, lambda p: [0, 0.5 * S(p), 0])},
                           "arm_l": {"rotation": cyc(2.4, lambda p: [3 * S(p), 0, -3])},
                           "arm_r": {"rotation": cyc(2.4, lambda p: [-3 * S(p), 0, 3])},
                           "head": {"rotation": cyc(4.8, lambda p: [2 * S(p), 10 * S(p), 0])}}, loop=True)
    a["walk"] = anim(1.0, {"body": {"position": cyc(1.0, lambda p: [0, 0.8 * abs(S(p)), 0]), "rotation": cyc(1.0, lambda p: [6, 4 * S(p), 0])},
                           "leg_l": {"rotation": cyc(1.0, lambda p: [24 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(1.0, lambda p: [-24 * S(p), 0, 0])},
                           "arm_l": {"rotation": cyc(1.0, lambda p: [-26 * S(p), 0, -4])},
                           "arm_r": {"rotation": cyc(1.0, lambda p: [26 * S(p), 0, 4])}}, loop=True)
    a["punch"] = anim(0.6, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.2, [-60, -20, 10]), (0.35, [-100, 10, 0]), (0.6, [0, 0, 0])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.2, [0, 15, 0]), (0.35, [8, -15, 0]), (0.6, [0, 0, 0])])}})
    a["slam"] = anim(1.0, {"arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, -10]), (0.8, [-30, 0, 0]), (1.0, [0, 0, 0])])},
                           "arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, 10]), (0.8, [-30, 0, 0]), (1.0, [0, 0, 0])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-15, 0, 0]), (0.8, [25, 0, 0]), (1.0, [0, 0, 0])]),
                                    "position": keys([(0, [0, 0, 0]), (0.5, [0, 3, 0]), (0.8, [0, -2, 0]), (1.0, [0, 0, 0])])},
                           "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (0.8, [10, 0, 0]), (1.0, [0, 0, 0])])}})
    save_anims("hollow_brute", a)


# =====================================================================================
#  VAZIO ALADO
# =====================================================================================
def build_wraith():
    m = Model("hollow_wraith")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 7, 0), "root")
    m.cube("body", (-5, 4, -8), (10, 4, 16), P(BLK, deco=chain(plates(BONE_D, 3), hole("top", 5, 10, 1.8))))
    m.bone("head", (0, 6, -8), "body")
    m.cube("head", (-3, 3, -14), (6, 5, 6), P(BONE, deco=mask_face(eye_y=1, eye_xs=(0, None), tooth_row=False)))
    m.cube("head", (-1, 3, -17), (2, 2, 3), P(BONE_D))
    for name, sx in (("wing_l", -1), ("wing_r", 1)):
        m.bone(name, (5 * sx, 7, -2), "body")
        x0 = -21 if sx < 0 else 5

        def memb(fs, sx=sx):
            for n, f in fs.items():
                f.fill(H("#26232E"))
                for xx in range(0, f.w, 4):
                    f.col(xx, H(BONE_D))
                if n in ("top", "bottom"):
                    for xx in range(f.w):
                        cut = int(2 + 2 * abs(math.sin(xx * 0.7)))
                        for yy in range(f.h - cut, f.h):
                            f.set(xx, yy, CLEAR)
        m.cube(name, (x0, 6.5, -6), (16, 1, 12), P("#26232E", deco=memb))
        m.cube(name, (x0, 7, -6.5), (16, 1, 1), P(BONE))
    m.bone("tail", (0, 6, 8), "body")
    m.cube("tail", (-0.5, 5.5, 8), (1, 1, 14), P(BONE))
    m.cube("tail", (-1.5, 5, 21), (3, 2, 2), P(BONE_D, deco=glow_faces(RED)))
    m.save(bounds=(3, 2))

    a = {}
    fly = {"body": {"position": cyc(0.8, lambda p: [0, 1.2 * S(p), 0])},
           "wing_l": {"rotation": cyc(0.8, lambda p: [0, 0, 32 * S(p)])},
           "wing_r": {"rotation": cyc(0.8, lambda p: [0, 0, -32 * S(p)])},
           "tail": {"rotation": cyc(0.8, lambda p: [6 * S(p - 0.25), 10 * S(p), 0])},
           "head": {"rotation": cyc(1.6, lambda p: [3 * S(p), 0, 0])}}
    a["fly"] = anim(0.8, fly, loop=True)
    a["idle"] = a["fly"]
    a["walk"] = a["fly"]
    a["shoot"] = anim(0.4, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.1, [-18, 0, 0]), (0.25, [6, 0, 0]), (0.4, [0, 0, 0])])}})
    a["dive"] = anim(1.2, {"body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [30, 0, 0]), (1.0, [30, 0, 0]), (1.2, [0, 0, 0])])},
                           "wing_l": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 50, 15]), (1.0, [0, 50, 15]), (1.2, [0, 0, 0])])},
                           "wing_r": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, -50, -15]), (1.0, [0, -50, -15]), (1.2, [0, 0, 0])])}})
    save_anims("hollow_wraith", a)


# =====================================================================================
#  MENOS GIGANTE
# =====================================================================================
def build_menos():
    m = Model("menos_giant")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 0, 0), "root")

    def robe(fs):
        folds(BLK_D, 4)(fs)
        speckle(BLK_L, 0.06, 5)(fs)
    m.cube("body", (-12, 0, -9), (24, 60, 18), P(BLK, deco=chain(robe, hem(BLK_D, ragged=True, seed=8))))
    m.cube("body", (-10, 60, -8), (20, 30, 16), P(BLK, deco=robe))
    m.cube("body", (-8, 90, -7), (16, 14, 14), P(BLK, deco=robe))
    for k in range(8):
        ang = k * 45
        rx, rz = math.sin(math.radians(ang)) * 7, -math.cos(math.radians(ang)) * 6
        m.cube("body", (rx - 1, 98, rz - 1), (2, 12, 2), P(BONE, noise=0.02),
               rotation=[-25 * math.cos(math.radians(ang)), 0, -25 * math.sin(math.radians(ang))], pivot=[rx, 98, rz])
    for x in (-9, 3):
        m.cube("body", (x, 0, -12), (6, 3, 4), P(BLK_D, deco=lambda fs: [fs["front"].set(xx, fs["front"].h - 1, H(BONE)) for xx in (0, 2, 4)]))
    m.bone("head", (0, 104, 0), "body")
    m.cube("head", (-7, 104, -9), (14, 20, 14), P(BONE, deco=chain(mask_face(eye_y=5, eye_xs=(2, None), tooth_row=False),
                                                                  cracks(BONE_D, 4, 2, faces=("front",), g=False))))
    m.cube("head", (-2, 110, -25), (4, 4, 16), P(BONE, deco=band([3], BONE_D)))
    m.bone("cero", (0, 112, -26), "head")
    m.cube("cero", (-1.5, 110.5, -27), (3, 3, 2), P(RED, deco=glow_faces(RED, core=RED_L)))
    m.save(bounds=(4, 9))

    a = {}
    a["idle"] = anim(4.0, {"body": {"rotation": cyc(4.0, lambda p: [0, 0, 2 * S(p)])},
                           "head": {"rotation": cyc(6.0, lambda p: [2 * S(p), 12 * S(p), 0])},
                           "cero": {"scale": cyc(1.0, lambda p: [1 + 0.15 * S(p)] * 3)}}, loop=True)
    a["walk"] = anim(2.0, {"body": {"rotation": cyc(2.0, lambda p: [0, 0, 4 * S(p)]), "position": cyc(2.0, lambda p: [0, 1.2 * abs(S(p)), 0])},
                           "head": {"rotation": cyc(2.0, lambda p: [2 * S(p * 2), 0, -2 * S(p)])}}, loop=True)
    a["cero"] = anim(2.0, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (1.6, [-14, 0, 0]), (1.75, [8, 0, 0]), (2.0, [0, 0, 0])])},
                           "cero": {"scale": keys([(0, [1, 1, 1]), (1.6, [3.2, 3.2, 3.2]), (1.75, [4, 4, 4]), (2.0, [1, 1, 1])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (1.6, [-3, 0, 0]), (1.75, [4, 0, 0]), (2.0, [0, 0, 0])])}})
    a["stomp"] = anim(1.0, {"body": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 5, 0]), (0.75, [0, -2, 0]), (1.0, [0, 0, 0])]),
                                     "rotation": keys([(0, [0, 0, 0]), (0.5, [-4, 0, 0]), (0.75, [3, 0, 0]), (1.0, [0, 0, 0])])}})
    save_anims("menos_giant", a)


# =====================================================================================
#  ARRANCAR (5 variacoes)
# =====================================================================================
ARR = [  # cabelo, cabelo escuro, pele, detalhe (faixa/cor da variacao)
    {"hair": "#3FA0E8", "hair_d": "#1F5E9A", "skin": "#E9D2BE", "acc": "#4FB8FF", "hole": ("front", 4, 3)},
    {"hair": "#16161C", "hair_d": "#050507", "skin": "#F1F1EA", "acc": "#55FF8A", "hole": ("throat", 0, 0)},
    {"hair": "#5FCFB8", "hair_d": "#2F8A7A", "skin": "#EBD4C2", "acc": "#FF8FC8", "hole": ("back", 4, 5)},
    {"hair": "#7A5A3A", "hair_d": "#4A3420", "skin": "#E2C9B0", "acc": "#B8C4D8", "hole": ("front", 4, 8)},
    {"hair": "#101014", "hair_d": "#000000", "skin": "#E6D0BA", "acc": "#C9A23A", "hole": ("eye", 0, 0)},
]
WHITE, WHITE_D, SASH = "#F4F4F0", "#CFCFCA", "#2B2B33"


def build_arrancar():
    for v, pal in enumerate(ARR):
        m = Model("arrancar")
        hair, hair_d, skin, acc = pal["hair"], pal["hair_d"], pal["skin"], pal["acc"]
        m.bone("root", (0, 0, 0))
        m.bone("body", (0, 24, 0), "root")

        def jacket(fs, pal=pal):
            for n in SIDES:
                fs[n].col(fs[n].w // 2, H(WHITE_D))
            fs["front"].row(fs["front"].h - 3, H(SASH))
            fs["front"].row(fs["front"].h - 2, H(SASH))
            fs["back"].row(fs["back"].h - 3, H(SASH))
            fs["front"].rect(3, 0, 4, 2, H(pal["skin"]))
            if pal["hole"][0] == "front":
                hole("front", pal["hole"][1], pal["hole"][2], 1.4)(fs)
            if pal["hole"][0] == "back":
                hole("back", pal["hole"][1], pal["hole"][2], 1.4)(fs)
            fs["front"].set(1, 1, H(pal["acc"]))
        m.cube("body", (-4, 12, -2), (8, 12, 4), P(WHITE, deco=jacket, noise=0.03))
        m.cube("body", (-4.5, 6, -2.5), (9, 7, 5), P(WHITE, deco=chain(folds(WHITE_D, 3), rim(SASH, top=True, bottom=False)), noise=0.03))
        m.bone("head", (0, 24, 0), "body")

        def face(fs, pal=pal):
            f = fs["front"]
            f.rect(0, 0, 7, 1, H(pal["hair"]))
            f.set(0, 2, H(pal["hair"]))
            f.set(7, 2, H(pal["hair"]))
            f.rect(1, 3, 2, 3, H("#FFFFFF"))
            f.rect(5, 3, 6, 3, H("#FFFFFF"))
            f.set(2, 3, H(pal["acc"]), True)
            f.set(5, 3, H(pal["acc"]), True)
            f.rect(3, 6, 4, 6, H(darken(H(pal["skin"]), 0.3)))
            if pal["hole"][0] == "throat":
                f.rect(3, 7, 4, 7, H(HOLE))
                for yy in (4, 5):  # marcas de lagrima
                    f.set(1, yy, H(pal["acc"]), True)
                    f.set(6, yy, H(pal["acc"]), True)
            if pal["hole"][0] == "eye":
                f.rect(5, 3, 6, 4, H(HOLE))
            for n in ("top", "back", "left", "right"):
                fs[n].fill(H(pal["hair"]))
                fs[n].scatter(H(pal["hair_d"]), 0.25, 3 + len(n))
            for n in ("left", "right"):
                fs[n].rect(2, 4, 5, 7, H(pal["skin"]))
        m.cube("head", (-4, 24, -4), (8, 8, 8), P(skin, deco=face, noise=0.02))
        m.cube("head", (-4.5, 27, -0.5), (9, 6, 5), P(hair, deco=speckle(hair_d, 0.3, 9 + v)))
        # mascaras (uma por variacao; o renderer mostra so a da variacao)
        bone_p = P(BONE, deco=speckle(BONE_D, 0.15, 40 + v), noise=0.02)
        m.bone("mask_0", (0, 24, 0), "head")
        m.cube("mask_0", (-4.6, 24, -4.6), (4, 3, 1), P(BONE, deco=teeth(BONE_L, HOLE, "front", True, 1)))
        m.cube("mask_0", (-4.6, 24, -4), (1, 4, 5), bone_p)
        m.bone("mask_1", (0, 24, 0), "head")
        m.cube("mask_1", (-4.5, 31, -4.5), (9, 2, 9), bone_p)
        m.cube("mask_1", (2, 33, -2), (1, 6, 1), P(BONE), rotation=[0, 0, -15], pivot=[2.5, 33, -1.5])
        m.bone("mask_2", (0, 24, 0), "head")
        m.cube("mask_2", (-3, 31.5, -4.6), (6, 2, 3), bone_p)
        m.cube("mask_2", (-6.5, 30, -2), (2, 2, 5), P(BONE_D), rotation=[20, 0, 0], pivot=[-5.5, 31, 0])
        m.cube("mask_2", (4.5, 30, -2), (2, 2, 5), P(BONE_D), rotation=[20, 0, 0], pivot=[5.5, 31, 0])
        m.bone("mask_3", (0, 24, 0), "head")
        m.cube("mask_3", (-3, 22, -2.7), (6, 2, 1), P(BONE, deco=teeth(BONE_L, HOLE, "front", False, 1)))
        m.bone("mask_4", (0, 24, 0), "head")
        m.cube("mask_4", (0.2, 27, -4.6), (4, 4, 1), bone_p)
        m.cube("mask_4", (1.5, 31, -4.3), (1, 4, 1), P(BONE), rotation=[-10, 0, -10], pivot=[2, 31, -4])
        for name, x0, px in (("arm_r", -8, -5), ("arm_l", 4, 5)):
            m.bone(name, (px, 22, 0), "body")

            def sleeve(fs, pal=pal):
                for n in SIDES:
                    fs[n].row(fs[n].h - 1, H(pal["skin"]))
                    fs[n].row(fs[n].h - 2, H(pal["skin"]))
                    fs[n].row(fs[n].h - 4, H(WHITE_D))
            m.cube(name, (x0, 12, -2), (4, 12, 4), P(WHITE, deco=sleeve, noise=0.03))
        m.bone("sword", (-6, 13, 0), "arm_r")
        m.cube("sword", (-6.5, 12, -3), (1, 1, 4), P(SASH, deco=band([0], acc)))
        m.cube("sword", (-7.5, 11.5, -4), (3, 2, 1), P(GOLD))
        m.cube("sword", (-6.5, 12, -16), (1, 1, 12), P("#D8DDE6", deco=lambda fs: fs["top"].fill(H("#FFFFFF"))))
        for name, x0, px in (("leg_r", -4, -2), ("leg_l", 0, 2)):
            m.bone(name, (px, 12, 0), "body")

            def hakama(fs):
                for n in SIDES:
                    fs[n].row(fs[n].h - 1, H(SASH))
                    fs[n].row(fs[n].h - 2, H(SASH))
            m.cube(name, (x0, 0, -2), (4, 12, 4), P(WHITE, deco=chain(folds(WHITE_D, 3), hakama), noise=0.03))
        m.save(bounds=(2, 3), seed=7 + v, tex_name="arrancar_%d" % v, geo=(v == 0))

    a = {}
    a["idle"] = anim(2.4, {"body": {"position": cyc(2.4, lambda p: [0, 0.2 * S(p), 0])},
                           "arm_r": {"rotation": cyc(2.4, lambda p: [-8 + 3 * S(p), 0, 4])},
                           "arm_l": {"rotation": cyc(2.4, lambda p: [-3 * S(p), 0, -4])},
                           "head": {"rotation": cyc(4.8, lambda p: [2 * S(p), 0, 0])}}, loop=True)
    a["walk"] = anim(0.8, {"leg_l": {"rotation": cyc(0.8, lambda p: [30 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(0.8, lambda p: [-30 * S(p), 0, 0])},
                           "arm_l": {"rotation": cyc(0.8, lambda p: [-26 * S(p), 0, -3])},
                           "arm_r": {"rotation": cyc(0.8, lambda p: [-10 + 18 * S(p), 0, 3])},
                           "body": {"position": cyc(0.8, lambda p: [0, 0.4 * abs(S(p)), 0])}}, loop=True)
    a["slash"] = anim(0.5, {"arm_r": {"rotation": keys([(0, [-10, 0, 0]), (0.15, [-150, 30, 0]), (0.3, [-20, -40, 0]), (0.5, [-10, 0, 0])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.15, [0, 20, 0]), (0.3, [0, -25, 0]), (0.5, [0, 0, 0])])}})
    a["cero"] = anim(0.8, {"arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.2, [-90, 0, 0]), (0.6, [-90, 0, 0]), (0.8, [0, 0, 0])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.2, [0, -15, 0]), (0.6, [0, -15, 0]), (0.8, [0, 0, 0])])}})
    a["signature"] = anim(1.2, {"root": {"rotation": keys([(0, [0, 0, 0]), (0.4, [0, -40, 0]), (0.8, [0, 320, 0]), (1.2, [0, 360, 0])])},
                                "arm_r": {"rotation": keys([(0, [-10, 0, 0]), (0.4, [-90, 0, 60]), (0.8, [-90, 0, 90]), (1.2, [-10, 0, 0])])},
                                "body": {"position": keys([(0, [0, 0, 0]), (0.4, [0, -2, 0]), (0.8, [0, -1, 0]), (1.2, [0, 0, 0])])}})
    a["sonido"] = anim(0.3, {"body": {"position": keys([(0, [0, -2, 0]), (0.15, [0, -1, 0]), (0.3, [0, 0, 0])]),
                                      "rotation": keys([(0, [15, 0, 0]), (0.3, [0, 0, 0])])}})
    save_anims("arrancar", a)


# =====================================================================================
#  FADA TRAVESSA
# =====================================================================================
def build_pixie():
    m = Model("fey_pixie")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 5, 0), "root")
    m.cube("body", (-1.5, 3, -1), (3, 4, 2), P("#FF8FC8", deco=band([0], "#FFD6EA")))
    m.cube("body", (-2, 1.5, -1.5), (4, 2, 3), P("#F26AAE", deco=hem("#C2407E")))
    m.cube("body", (-1, 0, -0.5), (1, 2, 1), P("#F2D8C8"))
    m.cube("body", (0, 0, -0.5), (1, 2, 1), P("#F2D8C8"))
    for name, x in (("arm_r", -2.5), ("arm_l", 1.5)):
        m.bone(name, (x + 0.5, 6.5, 0), "body")
        m.cube(name, (x, 4, -0.5), (1, 3, 1), P("#F2D8C8"))
    m.bone("head", (0, 7, 0), "body")

    def face(fs):
        f = fs["front"]
        f.set(1, 2, H("#7AF0FF"), True)
        f.set(2, 2, H("#7AF0FF"), True)
        f.set(1, 3, H("#E89AB0"))
        f.set(2, 3, H("#E89AB0"))
    m.cube("head", (-2, 7, -2), (4, 4, 4), P("#F7E0D2", deco=face, noise=0.02))
    m.cube("head", (-2, 9.5, -2), (4, 2, 4), P("#FFB8E8", deco=speckle("#FF7AC8", 0.3, 4)), inflate=0.2)
    m.cube("head", (-2, 6.5, 0.6), (4, 3, 2), P("#FFB8E8", deco=speckle("#FF7AC8", 0.3, 5)))
    glass = (186, 240, 255, 150)

    def wing(fs):
        for n, f in fs.items():
            for y in range(f.h):
                for x in range(f.w):
                    edge = x in (0, f.w - 1) or y in (0, f.h - 1)
                    f.set(x, y, H("#FFFFFF") if edge else glass, True)
    for name, x0, w, y0, h, px in (("wing_ul", -7, 6, 6, 6, -0.5), ("wing_ur", 1, 6, 6, 6, 0.5),
                                   ("wing_ll", -5, 4, 3, 4, -0.5), ("wing_lr", 1, 4, 3, 4, 0.5)):
        m.bone(name, (px, 6, 1), "body")
        m.cube(name, (x0, y0, 1), (w, h, 0), P(glass, deco=wing, noise=0.0, grad=False))
    m.save(bounds=(1, 1))

    a = {}
    fly = {"body": {"position": cyc(0.6, lambda p: [0, 0.8 * S(p), 0])},
           "wing_ul": {"rotation": cyc(0.2, lambda p: [0, -35 * S(p), 0])}, "wing_ur": {"rotation": cyc(0.2, lambda p: [0, 35 * S(p), 0])},
           "wing_ll": {"rotation": cyc(0.2, lambda p: [0, -30 * S(p - 0.1), 0])}, "wing_lr": {"rotation": cyc(0.2, lambda p: [0, 30 * S(p - 0.1), 0])},
           "arm_r": {"rotation": cyc(1.2, lambda p: [10 * S(p), 0, 10])}, "arm_l": {"rotation": cyc(1.2, lambda p: [-10 * S(p), 0, -10])}}
    a["fly"] = anim(0.6, fly, loop=True)
    a["idle"] = a["fly"]
    a["walk"] = a["fly"]
    a["cast"] = anim(0.5, {"arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.15, [-100, 0, 0]), (0.35, [-100, 0, 0]), (0.5, [0, 0, 0])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.15, [-10, 0, 0]), (0.5, [0, 0, 0])])}})
    save_anims("fey_pixie", a)


# =====================================================================================
#  CAO DE ESPINHOS
# =====================================================================================
BARK, BARK_D, BARK_L, LEAF, ROSE = "#4E3A26", "#2E2116", "#6E5638", "#4E7A2E", "#E0405A"


def build_hound():
    m = Model("bramble_hound")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 9, 0), "root")
    m.cube("body", (-3, 6, -6), (6, 6, 13), P(BARK, deco=chain(bark(BARK_D, BARK_L, 4), speckle(LEAF, 0.15, 6))))
    for k, z in enumerate((-4, -1, 2, 5)):
        m.cube("body", (-0.5, 12, z), (1, 2 + (k % 2), 1), P("#C8B89A"))
    for x in (-3.5, 2.5):
        m.cube("body", (x, 11, -5), (1, 1, 1), P(ROSE, deco=glow_faces(ROSE)))
    m.bone("head", (0, 10, -6), "body")
    m.cube("head", (-3, 7, -11), (6, 6, 5), P(BARK, deco=chain(bark(BARK_D, BARK_L, 8), eyes("#7CFF6A", [(1, 1), (3, 1)]))))
    m.cube("head", (-1.5, 7, -14), (3, 3, 3), P(BARK_D, deco=teeth("#E8DCC0", BARK_D, "front", True, 1)))
    m.cube("head", (-3, 13, -9), (1, 2, 1), P(LEAF))
    m.cube("head", (2, 13, -9), (1, 2, 1), P(LEAF))
    for name, x, z in (("leg_fl", -2, -4), ("leg_fr", 2, -4), ("leg_bl", -2, 5), ("leg_br", 2, 5)):
        m.bone(name, (x, 6, z), "body")
        m.cube(name, (x - 1, 0, z - 1), (2, 6, 2), P(BARK, deco=bark(BARK_D, BARK_L, 12 + x + z)))
    m.bone("tail", (0, 11, 7), "body")
    m.cube("tail", (-0.5, 10, 7), (1, 1, 7), P(BARK_L, deco=speckle(LEAF, 0.4, 13)))
    m.save(bounds=(2, 2))

    legs = ["leg_fl", "leg_fr", "leg_bl", "leg_br"]
    a = {}
    a["idle"] = anim(2.0, {"body": {"position": cyc(2.0, lambda p: [0, 0.2 * S(p), 0])},
                           "tail": {"rotation": cyc(1.0, lambda p: [-20, 15 * S(p), 0])},
                           "head": {"rotation": cyc(4.0, lambda p: [3 * S(p), 10 * S(p), 0])}}, loop=True)
    walk = {"tail": {"rotation": cyc(0.6, lambda p: [-25, 20 * S(p), 0])}, "body": {"position": cyc(0.6, lambda p: [0, 0.4 * abs(S(p)), 0])}}
    for k, n in enumerate(legs):
        sgn = 1 if k in (0, 3) else -1
        walk[n] = {"rotation": cyc(0.6, lambda p, s=sgn: [32 * s * S(p), 0, 0])}
    a["walk"] = anim(0.6, walk, loop=True)
    a["bite"] = anim(0.45, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.3, [15, 0, 0]), (0.45, [0, 0, 0])])},
                            "body": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 0, -2]), (0.45, [0, 0, 0])])}})
    save_anims("bramble_hound", a)


# =====================================================================================
#  REI VAZIO (+ _p2 da Ressurreicao)
# =====================================================================================
ROBE, ROBE_D, ROBE_L, FURW = "#17141E", "#0A090E", "#2C2638", "#E8E2D6"


def build_king():
    for phase in (1, 2):
        p2 = phase == 2
        robe_c = "#120E16" if p2 else ROBE
        m = Model("hollow_king")
        m.bone("root", (0, 0, 0))
        m.bone("body", (0, 24, 0), "root")

        def robe_low(fs, p2=p2):
            folds(ROBE_D, 4)(fs)
            band([0, 1], GOLD)(fs)
            for n in SIDES:
                f = fs[n]
                f.row(f.h - 3, H(GOLD))
            fs["front"].col(fs["front"].w // 2, H(GOLD))
            if p2:
                hem(ROBE_D, ragged=True, seed=12)(fs)
                speckle(BONE_D, 0.05, 13)(fs)

        def torso(fs, p2=p2):
            folds(ROBE_D, 4)(fs)
            fs["front"].col(fs["front"].w // 2, H(GOLD))
            fs["front"].col(fs["front"].w // 2 - 1, H(GOLD_D))
            if p2:  # costelas a mostra
                f = fs["front"]
                for y in range(2, f.h - 3, 2):
                    for x in range(2, f.w - 2):
                        f.set(x, y, H(BONE) if x != f.w // 2 else H(BONE_D))
                    for x in range(2, f.w - 2):
                        f.set(x, y + 1, H(HOLE))
                f.rect(f.w // 2 - 1, f.h // 2, f.w // 2, f.h // 2 + 1, H(GOLD_L), True)
        m.cube("body", (-8, 0, -6), (16, 24, 12), P(robe_c, deco=robe_low, noise=0.04))
        m.cube("body", (-7, 24, -4.5), (14, 16, 9), P(robe_c, deco=torso, noise=0.04))
        m.cube("body", (-8, 37, -5.5), (16, 4, 11), P(FURW, deco=fur("#C8C0B0", "#FFFFFF", 14, density=0.35)))
        m.cube("body", (-8, 8, 5), (16, 32, 1), P(robe_c, deco=chain(rim(GOLD, faces=("back", "front"), top=False),
                                                                     hem(ROBE_D, ragged=p2, seed=15, faces=("back", "front")))))
        m.bone("head", (0, 41, 0), "body")
        eye = GOLD_L

        def skull(fs, p2=p2):
            mask_face(eye_y=3, eye_xs=(1, None), glow=eye)(fs)
            if p2:
                cracks(GOLD_L, 16, 3, faces=("front", "left", "right"), g=True)(fs)
        m.cube("head", (-4.5, 41, -5), (9, 9, 9), P(BONE, deco=skull, noise=0.04))

        def crown(fs):
            band([1], GOLD_D)(fs)
            fs["front"].set(fs["front"].w // 2, 1, H(RED), True)
            fs["front"].set(fs["front"].w // 2 - 1, 1, H(RED), True)
        m.cube("head", (-5, 49, -5.5), (10, 3, 10), P(GOLD, deco=crown, noise=0.03))
        for (x, z) in ((-5, -5.5), (4, -5.5), (-5, 3.5), (4, 3.5), (-0.5, -5.5)):
            m.cube("head", (x, 52, z), (1, 3 if x != -0.5 else 4, 1), P(GOLD_L))
        for name, x0, px, sx in (("arm_r", -11, -8.5, -1), ("arm_l", 6, 8.5, 1)):
            m.bone(name, (px, 38, 0), "body")
            m.cube(name, (x0, 26, -2.5), (5, 13, 5), P(robe_c, deco=chain(folds(ROBE_D, 3), band([12, 11], GOLD)), noise=0.04))
            m.cube(name, (x0 + 0.5, 22, -2), (4, 4, 4), P(BONE, deco=speckle(BONE_D, 0.2, 18 + px)))
        # v1.3.2: pegada a 8 px do pe do cabo, na mao; o osso aponta o machado para a frente (antes o cabo atravessava o braco)
        m.bone("axe", (-8.5, 24, 0), "arm_r", rotation=[110, 0, 0])
        m.cube("axe", (-9.5, 16, -1), (2, 34, 2), P("#2A2018", deco=band([10, 20, 30], GOLD_D)))

        def blade(fs):
            for n in ("front", "back"):
                f = fs[n]
                f.col(0, H(BONE_L))
                f.col(1, H(BONE))
                f.rect(f.w - 2, 0, f.w - 1, f.h - 1, H(GOLD_D))
            fs["left"].fill(H(GOLD))
            fs["right"].fill(H(GOLD))
        m.cube("axe", (-10, 40, -10), (3, 12, 9), P(BLK, deco=chain(blade, cracks(GOLD_L, 21, 2, faces=("left", "right"), g=p2))))
        m.cube("axe", (-10, 50, -1.5), (3, 3, 3), P(GOLD, deco=lambda fs: fs["top"].fill(H(GOLD_L))))
        m.save(bounds=(4, 4), seed=9, tex_name="hollow_king" + ("_p2" if p2 else ""), geo=not p2)

    a = {}
    a["sleep"] = anim(4.0, {"head": {"rotation": cyc(4.0, lambda p: [24 + 2 * S(p), 0, 0])},
                            "body": {"position": cyc(4.0, lambda p: [0, 0.3 * S(p), 0]), "rotation": keys([(0, [8, 0, 0]), (4.0, [8, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-20, 0, 8]), (4.0, [-20, 0, 8])])},
                            "arm_l": {"rotation": keys([(0, [-30, -20, -10]), (4.0, [-30, -20, -10])])}}, loop=True)
    a["intro"] = anim(3.0, {"head": {"rotation": keys([(0, [24, 0, 0]), (0.8, [-15, 0, 0]), (2.2, [-15, 0, 0]), (3.0, [0, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-20, 0, 8]), (1.2, [-170, 0, 10]), (2.0, [-170, 0, 10]), (2.4, [-40, 0, 0]),
                                                        (3.0, [-15, 0, 5])])},
                            "arm_l": {"rotation": keys([(0, [-30, -20, -10]), (1.2, [-40, 0, -60]), (2.4, [-40, 0, -60]), (3.0, [0, 0, -5])])},
                            "body": {"rotation": keys([(0, [8, 0, 0]), (1.2, [-8, 0, 0]), (2.4, [10, 0, 0]), (3.0, [0, 0, 0])])}})
    a["idle"] = anim(3.0, {"body": {"position": cyc(3.0, lambda p: [0, 0.4 * S(p), 0])},
                           "head": {"rotation": cyc(6.0, lambda p: [2 * S(p), 8 * S(p), 0])},
                           "arm_r": {"rotation": cyc(3.0, lambda p: [-15 + 2 * S(p), 0, 5])},
                           "arm_l": {"rotation": cyc(3.0, lambda p: [-2 * S(p), 0, -5])}}, loop=True)
    a["walk"] = anim(1.4, {"body": {"rotation": cyc(1.4, lambda p: [3, 5 * S(p), 2 * S(p)]), "position": cyc(1.4, lambda p: [0, 0.6 * abs(S(p)), 0])},
                           "arm_r": {"rotation": cyc(1.4, lambda p: [-20 + 10 * S(p), 0, 5])},
                           "arm_l": {"rotation": cyc(1.4, lambda p: [-15 * S(p), 0, -5])}}, loop=True)
    a["cleave"] = anim(0.9, {"arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.4, [-120, 60, 30]), (0.6, [-80, -80, 0]), (0.9, [-15, 0, 5])])},
                             "body": {"rotation": keys([(0, [0, 0, 0]), (0.4, [0, 35, 0]), (0.6, [5, -40, 0]), (0.9, [0, 0, 0])])}})
    a["throw"] = anim(1.3, {"arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.7, [-200, 0, 10]), (0.9, [-40, 0, 0]), (1.3, [-15, 0, 5])])},
                            "axe": {"scale": keys([(0, [1, 1, 1]), (0.85, [1, 1, 1]), (0.9, [0, 0, 0]), (1.2, [0, 0, 0]), (1.3, [1, 1, 1])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.7, [-12, 20, 0]), (0.9, [14, -10, 0]), (1.3, [0, 0, 0])])}})
    a["breath"] = anim(1.5, {"head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (1.2, [18, 0, 0]), (1.5, [0, 0, 0])])},
                             "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-10, 0, 0]), (1.2, [14, 0, 0]), (1.5, [0, 0, 0])])},
                             "arm_l": {"rotation": keys([(0, [0, 0, -5]), (0.5, [-60, 0, -40]), (1.2, [-80, 0, -10]), (1.5, [0, 0, -5])])}})
    a["summon"] = anim(1.0, {"arm_l": {"rotation": keys([(0, [0, 0, -5]), (0.4, [-170, 0, -15]), (0.8, [-170, 0, -15]), (1.0, [0, 0, -5])])},
                             "head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.0, [0, 0, 0])])}})
    a["respira"] = anim(4.0, {"arm_l": {"rotation": keys([(0, [0, 0, -5]), (0.8, [-150, 0, -50]), (3.4, [-150, 0, -50]), (4.0, [0, 0, -5])])},
                              "arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.8, [-150, 0, 50]), (3.4, [-150, 0, 50]), (4.0, [-15, 0, 5])])},
                              "head": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-35, 0, 0]), (3.4, [-35, 0, 0]), (4.0, [0, 0, 0])])},
                              "body": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-8, 0, 0]), (3.4, [-8, 0, 0]), (4.0, [0, 0, 0])]),
                                       "position": keys([(0, [0, 0, 0]), (0.8, [0, 3, 0]), (3.4, [0, 3, 0]), (4.0, [0, 0, 0])])}})
    a["resurrect"] = anim(2.0, {"body": {"position": cyc(2.0, lambda p: [0.6 * S(p * 10), 0, 0], steps=24),
                                         "rotation": keys([(0, [0, 0, 0]), (0.6, [-15, 0, 0]), (1.6, [-15, 0, 0]), (2.0, [0, 0, 0])])},
                                "arm_l": {"rotation": keys([(0, [0, 0, -5]), (0.6, [-90, 0, -70]), (1.6, [-90, 0, -70]), (2.0, [0, 0, -5])])},
                                "arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.6, [-90, 0, 70]), (1.6, [-90, 0, 70]), (2.0, [-15, 0, 5])])},
                                "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-40, 0, 0]), (1.6, [-40, 0, 0]), (2.0, [0, 0, 0])])}})
    a["death"] = anim(2.5, {"root": {"position": keys([(0, [0, 0, 0]), (1.0, [0, -8, 0]), (2.5, [0, -14, 2])]),
                                     "rotation": keys([(0, [0, 0, 0]), (1.0, [12, 0, 0]), (2.5, [70, 0, 5])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, 0]), (2.5, [30, 0, 15])])},
                            "arm_r": {"rotation": keys([(0, [-15, 0, 5]), (2.5, [10, 0, 40])])},
                            "arm_l": {"rotation": keys([(0, [0, 0, -5]), (2.5, [10, 0, -40])])}})
    save_anims("hollow_king", a)


# =====================================================================================
#  IMPERATRIZ DAS AREIAS (+ _p2)
# =====================================================================================
TAN, TAN_D, BLOND, BLOND_D, TEAL = "#C99A72", "#A27850", "#F2D06A", "#C9A23A", "#3FA7C9"


def build_empress():
    for phase in (1, 2):
        p2 = phase == 2
        m = Model("sand_empress")
        m.bone("root", (0, 0, 0))
        m.bone("body", (0, 16, 0), "root")
        cloth = BONE if p2 else WHITE
        cloth_d = BONE_D if p2 else WHITE_D

        def pants(fs, p2=p2):
            folds(cloth_d, 3)(fs)
            if p2:
                band([2, 6, 10], TEAL)(fs)
        for name, x0, px in (("leg_r", -4, -2), ("leg_l", 0, 2)):
            m.bone(name, (px, 16, 0), "body")
            m.cube(name, (x0, 0, -2), (4, 16, 4), P(cloth, deco=chain(pants, band([15], "#3A3030"))))

        def jacket(fs, p2=p2):
            f = fs["front"]
            for y in range(f.h):
                f.set(f.w // 2, y, H(cloth_d))
            f.rect(2, 0, 5, 3, H(TAN))  # decote
            if p2:
                for n in SIDES:
                    fs[n].row(3, H(TEAL))
                    fs[n].row(8, H(TEAL))
                f.rect(3, 1, 4, 2, H(TEAL), True)
        m.cube("body", (-4, 16, -2.5), (8, 12, 5), P(cloth, deco=jacket, noise=0.03))
        m.cube("body", (-4.5, 8, -2.5), (9, 8, 5), P(cloth, deco=chain(folds(cloth_d, 3), rim("#3A3030", top=True, bottom=False))))
        m.cube("body", (-4.5, 26, -3), (9, 5, 6), P(cloth, deco=chain(rim(cloth_d), (band([1], TEAL) if p2 else None))))
        m.bone("fin", (0, 24, 2.5), "body")

        def fin(fs):
            for n, f in fs.items():
                for y in range(f.h):
                    for x in range(f.w):
                        if n in ("left", "right") and x > (f.h - y) * f.w / f.h + 1:
                            f.set(x, y, CLEAR)
        m.cube("fin", (-0.5, 20, 2.5), (1, 9, 6), P("#6E8A9A", deco=chain(fin, band([0], "#9AB8C8"))))
        m.bone("tail", (0, 14, 2.5), "body")
        m.cube("tail", (-1, 6, 2.5), (2, 9, 2), P("#6E8A9A"))
        m.cube("tail", (-3, 2, 3), (6, 4, 1), P("#5A7484", deco=band([0], "#9AB8C8")))
        m.bone("head", (0, 28, 0), "body")

        def face(fs, p2=p2):
            f = fs["front"]
            f.rect(0, 0, 6, 1, H(BLOND))
            f.rect(1, 3, 2, 3, H("#FFFFFF"))
            f.rect(4, 3, 5, 3, H("#FFFFFF"))
            f.set(2, 3, H("#3AA86A"), True)
            f.set(4, 3, H("#3AA86A"), True)
            if p2:
                f.set(0, 4, H(TEAL), True)
                f.set(6, 4, H(TEAL), True)
            for n in ("top", "back"):
                fs[n].fill(H(BLOND))
                fs[n].scatter(H(BLOND_D), 0.3, 22 + len(n))
            for n in ("left", "right"):
                fs[n].rect(0, 0, fs[n].w - 1, 2, H(BLOND))
                fs[n].rect(fs[n].w - 3, 0, fs[n].w - 1, fs[n].h - 1, H(BLOND))
        m.cube("head", (-3.5, 28, -3.5), (7, 8, 7), P(TAN, deco=face, noise=0.02))
        m.cube("head", (-3.6, 27.6, -3.9), (7, 3, 1), P(BONE, deco=teeth(BONE_L, HOLE, "front", False, 1)))
        m.cube("head", (-4, 33, -4), (8, 3, 8), P(BLOND, deco=speckle(BLOND_D, 0.3, 24)))
        for k, x in enumerate((-3, -1, 1)):
            m.cube("head", (x, 24, 3), (2, 9, 2), P(BLOND, deco=band([2, 5, 8], BLOND_D)))
        for name, x0, px in (("arm_r", -7, -5.5), ("arm_l", 4, 5.5)):
            m.bone(name, (px, 26, 0), "body")

            def sleeve(fs):
                for n in SIDES:
                    fs[n].row(fs[n].h - 1, H(TAN))
                    fs[n].row(fs[n].h - 2, H(TAN))
            m.cube(name, (x0, 15, -1.5), (3, 12, 3), P(cloth, deco=chain(sleeve, (band([4], TEAL) if p2 else None)), noise=0.03))
        m.bone("sword", (-5.5, 16, 0), "arm_r")
        m.cube("sword", (-6, 15, -3.5), (1, 1, 4), P("#2A2A30", deco=band([0], GOLD)))
        m.cube("sword", (-7, 14.5, -4.5), (3, 2, 1), P(GOLD))

        def blade(fs):
            for n in ("left", "right"):
                f = fs[n]
                f.rect(3, 1, f.w - 4, f.h - 2, H(HOLE))  # o vazio no meio da lamina
                for x in range(0, f.w, 2):
                    f.set(x, 0, H(BONE_L))
        m.cube("sword", (-6.5, 13.5, -16.5), (1, 3, 12), P(BONE, deco=blade))
        m.save(bounds=(3, 3), seed=11, tex_name="sand_empress" + ("_p2" if p2 else ""), geo=not p2)

    a = {}
    a["sleep"] = anim(4.0, {"head": {"rotation": cyc(4.0, lambda p: [15 + 2 * S(p), 0, 0])},
                            "arm_r": {"rotation": keys([(0, [-50, 40, 0]), (4.0, [-50, 40, 0])])},
                            "arm_l": {"rotation": keys([(0, [-50, -40, 0]), (4.0, [-50, -40, 0])])},
                            "body": {"position": cyc(4.0, lambda p: [0, 0.2 * S(p), 0])}}, loop=True)
    a["intro"] = anim(3.0, {"head": {"rotation": keys([(0, [15, 0, 0]), (1.0, [-5, 0, 0]), (3.0, [0, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-50, 40, 0]), (1.0, [-10, 0, 0]), (1.8, [-170, 0, 0]), (2.4, [-60, 0, 0]),
                                                        (3.0, [-15, 0, 0])])},
                            "arm_l": {"rotation": keys([(0, [-50, -40, 0]), (1.0, [0, 0, -20]), (3.0, [0, 0, 0])])}})
    a["idle"] = anim(2.6, {"body": {"position": cyc(2.6, lambda p: [0, 0.25 * S(p), 0])},
                           "arm_r": {"rotation": cyc(2.6, lambda p: [-15 + 3 * S(p), 0, 4])},
                           "arm_l": {"rotation": cyc(2.6, lambda p: [-3 * S(p), 0, -4])},
                           "fin": {"rotation": cyc(2.6, lambda p: [0, 6 * S(p), 0])},
                           "tail": {"rotation": cyc(2.0, lambda p: [10, 15 * S(p), 0])}}, loop=True)
    a["walk"] = anim(0.8, {"leg_l": {"rotation": cyc(0.8, lambda p: [28 * S(p), 0, 0])},
                           "leg_r": {"rotation": cyc(0.8, lambda p: [-28 * S(p), 0, 0])},
                           "arm_l": {"rotation": cyc(0.8, lambda p: [-22 * S(p), 0, -3])},
                           "arm_r": {"rotation": cyc(0.8, lambda p: [-15 + 10 * S(p), 0, 3])},
                           "tail": {"rotation": cyc(0.8, lambda p: [10, 20 * S(p), 0])}}, loop=True)
    a["slash"] = anim(0.5, {"arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.15, [-130, 40, 0]), (0.3, [-30, -50, 0]), (0.5, [-15, 0, 0])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.15, [0, 25, 0]), (0.3, [0, -30, 0]), (0.5, [0, 0, 0])])}})
    a["wave"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.5, [-90, 80, 0]), (0.75, [-90, -60, 0]), (1.0, [-15, 0, 0])])},
                           "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, 40, 0]), (0.75, [0, -40, 0]), (1.0, [0, 0, 0])])}})
    a["cascade"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.4, [-175, 0, 0]), (0.7, [-175, 0, 0]), (1.0, [-15, 0, 0])])},
                              "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-160, 0, -20]), (0.7, [-160, 0, -20]), (1.0, [0, 0, 0])])},
                              "head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-25, 0, 0]), (1.0, [0, 0, 0])])}})
    a["whirl"] = anim(0.8, {"root": {"rotation": keys([(0, [0, 0, 0]), (0.8, [0, 360, 0])])},
                            "arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.2, [-90, 0, 80]), (0.6, [-90, 0, 80]), (0.8, [-15, 0, 0])])}})
    a["dive"] = anim(0.8, {"root": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 4, -2]), (0.8, [0, -40, -6])]),
                                    "rotation": keys([(0, [0, 0, 0]), (0.3, [-20, 0, 0]), (0.8, [60, 0, 0])])}})
    a["emerge"] = anim(0.8, {"root": {"position": keys([(0, [0, -30, 0]), (0.3, [0, 8, 0]), (0.8, [0, 0, 0])]),
                                      "rotation": keys([(0, [-60, 0, 0]), (0.3, [-10, 0, 0]), (0.8, [0, 0, 0])])},
                             "arm_r": {"rotation": keys([(0, [-170, 0, 0]), (0.3, [-120, 0, 0]), (0.8, [-15, 0, 0])])}})
    a["storm"] = anim(1.5, {"root": {"rotation": keys([(0, [0, 0, 0]), (1.5, [0, 720, 0])])},
                            "arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.3, [-90, 0, 90]), (1.2, [-90, 0, 90]), (1.5, [-15, 0, 0])])},
                            "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-90, 0, -90]), (1.2, [-90, 0, -90]), (1.5, [0, 0, 0])])}})
    a["resurrect"] = anim(2.0, {"body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [20, 0, 0]), (1.4, [-15, 0, 0]), (2.0, [0, 0, 0])])},
                                "arm_r": {"rotation": keys([(0, [-15, 0, 0]), (0.6, [-20, 0, 40]), (1.4, [-170, 0, 0]), (2.0, [-15, 0, 0])])},
                                "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-20, 0, -40]), (1.4, [-160, 0, 0]), (2.0, [0, 0, 0])])},
                                "fin": {"scale": keys([(0, [0, 0, 0]), (1.4, [1.3, 1.3, 1.3]), (2.0, [1, 1, 1])])},
                                "tail": {"scale": keys([(0, [0, 0, 0]), (1.4, [1.2, 1.2, 1.2]), (2.0, [1, 1, 1])])}})
    a["death"] = anim(2.5, {"root": {"position": keys([(0, [0, 0, 0]), (1.0, [0, -6, 0]), (2.5, [0, -12, 4])]),
                                     "rotation": keys([(0, [0, 0, 0]), (1.0, [10, 0, 0]), (2.5, [80, 0, 0])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (2.5, [25, 0, 0])])},
                            "arm_r": {"rotation": keys([(0, [-15, 0, 0]), (2.5, [10, 0, 30])])}})
    save_anims("sand_empress", a)


# =====================================================================================
#  RAINHA DOS ESPINHOS (+ _p2)
# =====================================================================================
PETAL, PETAL_D, LEAFD, SKINQ, THORNB = "#F29AD8", "#C2609E", "#2F4A2A", "#E8DDF2", "#5A3E28"


def build_queen():
    for phase in (1, 2):
        p2 = phase == 2
        petal = "#B8D0F0" if p2 else PETAL
        petal_d = "#7A94C8" if p2 else PETAL_D
        green = "#3A4A5A" if p2 else LEAFD
        skin = "#E4F0FF" if p2 else SKINQ
        glow = "#CFE8FF" if p2 else "#FF6AD0"
        m = Model("thorn_queen")
        m.bone("root", (0, 0, 0))
        m.bone("body", (0, 20, 0), "root")

        def petals(fs, petal=petal, petal_d=petal_d):
            for n in SIDES:
                f = fs[n]
                for x in range(0, f.w, 3):
                    for y in range(f.h):
                        if (y + x) % 4 == 0:
                            f.set(x, y, H(petal_d))
            hem(petal_d, ragged=True, seed=30)(fs)
        m.cube("body", (-7, 0, -6), (14, 10, 12), P(petal, deco=petals))
        m.cube("body", (-6, 10, -5), (12, 10, 10), P(green, deco=chain(speckle(petal, 0.08, 31), band([0], petal_d))))

        def bodice(fs, glow=glow):
            f = fs["front"]
            f.rect(3, 3, 4, 4, H(glow), True)
            f.set(2, 4, H(green))
            f.set(5, 4, H(green))
            fs["front"].rect(2, 0, 5, 1, H(skin))
        m.cube("body", (-4, 20, -2.5), (8, 10, 5), P(green, deco=chain(bodice, speckle(darken(H(green), 0.3), 0.2, 32))))
        for k in range(5):
            x = -4 + k * 2
            m.cube("body", (x, 29, -3), (1, 2, 1), P(THORNB))
        m.bone("head", (0, 30, 0), "body")

        def face(fs, glow=glow):
            f = fs["front"]
            f.rect(0, 0, 6, 1, H("#1E2A1A"))
            f.rect(1, 3, 2, 3, H(glow), True)
            f.rect(4, 3, 5, 3, H(glow), True)
            f.set(3, 5, H("#B87AA8"))
            for n in ("top", "back", "left", "right"):
                fs[n].fill(H("#1E2A1A"))
                fs[n].scatter(H("#34482C"), 0.3, 33 + len(n))
            for n in ("left", "right"):
                fs[n].rect(0, 3, 2, 6, H(skin))
        m.cube("head", (-3.5, 30, -3.5), (7, 8, 7), P(skin, deco=face, noise=0.02))
        m.cube("head", (-4, 26, 0.5), (8, 12, 4), P("#1E2A1A", deco=speckle("#34482C", 0.3, 34)))
        m.cube("head", (-4, 37, -4), (8, 2, 8), P(THORNB, deco=speckle(glow, 0.15, 35, g=True)))
        for (x, z, h) in ((-4, -4, 3), (-1.5, -4.5, 4), (1, -4, 3), (3, -1, 3), (-4, 2, 3)):
            m.cube("head", (x, 39, z), (1, h, 1), P(THORNB, deco=lambda fs: fs["top"].fill(H("#C8B89A"))))
        m.cube("head", (-0.5, 39, -4.6), (2, 2, 1), P(glow, deco=glow_faces(glow)))
        for name, x0, px in (("arm_r", -6.5, -5), ("arm_l", 3.5, 5)):
            m.bone(name, (px, 29, 0), "body")
            m.cube(name, (x0, 17, -1.5), (3, 12, 3), P(skin, deco=chain(veins(green, 36 + px, 3, faces=SIDES))))
        m.bone("staff", (-5, 18, 0), "arm_r")
        m.cube("staff", (-6, 2, -1), (2, 38, 2), P(THORNB, deco=chain(bark("#3A2818", "#7A5A3A", 37), speckle(green, 0.2, 38))))
        m.cube("staff", (-7.5, 40, -2.5), (5, 5, 5), P(petal, deco=glow_faces(petal, core=glow)))
        glass = (220, 240, 255, 140) if p2 else (255, 214, 245, 140)

        def wing(fs, glass=glass, glow=glow):
            for n, f in fs.items():
                for y in range(f.h):
                    for x in range(f.w):
                        edge = x in (0, f.w - 1) or y in (0, f.h - 1)
                        vein = (x + y) % 6 == 0
                        f.set(x, y, H(glow) if edge else (H("#FFFFFF", 200) if vein else glass), True)
        for name, x0, w, y0, h, px, py in (("wing_ul", -18, 16, 24, 16, -2, 28), ("wing_ur", 2, 16, 24, 16, 2, 28),
                                           ("wing_ll", -14, 12, 12, 12, -2, 24), ("wing_lr", 2, 12, 12, 12, 2, 24)):
            m.bone(name, (px, py, 2.5), "body")
            m.cube(name, (x0, y0, 2.5), (w, h, 0), P(glass, deco=wing, noise=0.0, grad=False))
        m.save(bounds=(4, 4), seed=13, tex_name="thorn_queen" + ("_p2" if p2 else ""), geo=not p2)

    def wings(length, amp, base=15):
        return {"wing_ul": {"rotation": cyc(length, lambda p: [0, base + amp * S(p), 0])},
                "wing_ur": {"rotation": cyc(length, lambda p: [0, -base - amp * S(p), 0])},
                "wing_ll": {"rotation": cyc(length, lambda p: [0, base + amp * S(p - 0.1), 0])},
                "wing_lr": {"rotation": cyc(length, lambda p: [0, -base - amp * S(p - 0.1), 0])}}
    a = {}
    sleep = {"root": {"position": keys([(0, [0, -6, 0]), (4.0, [0, -6, 0])])},
             "head": {"rotation": cyc(4.0, lambda p: [28 + 2 * S(p), 0, 0])},
             "arm_r": {"rotation": keys([(0, [-30, 0, 10]), (4.0, [-30, 0, 10])])},
             "arm_l": {"rotation": keys([(0, [-40, -30, 0]), (4.0, [-40, -30, 0])])}}
    sleep.update(wings(4.0, 3, base=45))
    a["sleep"] = anim(4.0, sleep, loop=True)
    intro = {"root": {"position": keys([(0, [0, -6, 0]), (1.2, [0, 2, 0]), (3.0, [0, 0, 0])])},
             "head": {"rotation": keys([(0, [28, 0, 0]), (1.2, [-10, 0, 0]), (3.0, [0, 0, 0])])},
             "arm_l": {"rotation": keys([(0, [-40, -30, 0]), (1.6, [-120, 0, -60]), (2.4, [-120, 0, -60]), (3.0, [0, 0, -5])])},
             "arm_r": {"rotation": keys([(0, [-30, 0, 10]), (1.6, [-60, 0, 20]), (3.0, [-15, 0, 5])])}}
    intro.update({k: {"rotation": keys([(0, [0, v, 0]), (1.2, [0, v * 0.2, 0]), (3.0, [0, v / 3, 0])])}
                  for k, v in (("wing_ul", 45), ("wing_ur", -45), ("wing_ll", 45), ("wing_lr", -45))})
    a["intro"] = anim(3.0, intro)
    idle = {"root": {"position": cyc(3.0, lambda p: [0, 1.2 + 1.2 * S(p), 0])},
            "head": {"rotation": cyc(6.0, lambda p: [2 * S(p), 6 * S(p), 0])},
            "arm_r": {"rotation": cyc(3.0, lambda p: [-15 + 2 * S(p), 0, 5])},
            "arm_l": {"rotation": cyc(3.0, lambda p: [-2 * S(p), 0, -6])}}
    idle.update(wings(1.5, 12))
    a["idle"] = anim(3.0, idle, loop=True)
    walk = dict(idle)
    walk["body"] = {"rotation": keys([(0, [8, 0, 0]), (3.0, [8, 0, 0])])}
    a["walk"] = anim(3.0, walk, loop=True)
    a["lance"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.5, [-60, 0, 30]), (0.7, [-100, 0, 0]), (1.0, [-15, 0, 5])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-8, 15, 0]), (0.7, [10, -10, 0]), (1.0, [0, 0, 0])])}})
    pet = {"body": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 360, 0])])}}
    pet.update({k: {"rotation": keys([(0, [0, v / 3, 0]), (0.5, [0, v, 0]), (1.0, [0, v / 3, 0])])}
                for k, v in (("wing_ul", 60), ("wing_ur", -60), ("wing_ll", 60), ("wing_lr", -60))})
    a["petals"] = anim(1.0, pet)
    a["charm"] = anim(0.9, {"arm_l": {"rotation": keys([(0, [0, 0, -6]), (0.3, [-95, 0, 0]), (0.7, [-95, 0, 0]), (0.9, [0, 0, -6])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 0, 10]), (0.9, [0, 0, 0])])}})
    a["summon"] = anim(1.0, {"arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.4, [-170, 0, 5]), (0.8, [-170, 0, 5]), (1.0, [-15, 0, 5])])}})
    seeds = {"root": {"position": keys([(0, [0, 1, 0]), (0.5, [0, 6, 0]), (1.4, [0, 6, 0]), (1.7, [0, 1, 0])])},
             "arm_l": {"rotation": keys([(0, [0, 0, -6]), (0.5, [-160, 0, -40]), (1.4, [-160, 0, -40]), (1.7, [0, 0, -6])])},
             "arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.5, [-160, 0, 40]), (1.4, [-160, 0, 40]), (1.7, [-15, 0, 5])])}}
    seeds.update(wings(0.4, 25, base=5))
    a["seeds"] = anim(1.7, seeds)
    verdict = {"arm_l": {"rotation": keys([(0, [0, 0, -6]), (0.8, [-170, 0, -30]), (3.4, [-170, 0, -30]), (4.0, [0, 0, -6])])},
               "arm_r": {"rotation": keys([(0, [-15, 0, 5]), (0.8, [-170, 0, 30]), (3.4, [-170, 0, 30]), (4.0, [-15, 0, 5])])},
               "head": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-35, 0, 0]), (3.4, [-35, 0, 0]), (4.0, [0, 0, 0])])},
               "root": {"position": keys([(0, [0, 1, 0]), (0.8, [0, 8, 0]), (3.4, [0, 8, 0]), (4.0, [0, 1, 0])])}}
    verdict.update(wings(0.6, 20, base=0))
    a["verdict"] = anim(4.0, verdict)
    death = {"root": {"position": keys([(0, [0, 1, 0]), (1.0, [0, -6, 0]), (2.5, [0, -12, 0])]),
                      "rotation": keys([(0, [0, 0, 0]), (2.5, [60, 0, 10])])},
             "head": {"rotation": keys([(0, [0, 0, 0]), (2.5, [30, 0, 0])])}}
    death.update({k: {"rotation": keys([(0, [0, 0, 0]), (2.5, [0, v, 30 if v > 0 else -30])])}
                  for k, v in (("wing_ul", 60), ("wing_ur", -60), ("wing_ll", 60), ("wing_lr", -60))})
    a["death"] = anim(2.5, death)
    save_anims("thorn_queen", a)


def build_all():
    build_crawler()
    build_brute()
    build_wraith()
    build_menos()
    build_arrancar()
    build_pixie()
    build_hound()
    build_king()
    build_empress()
    build_queen()
    print("  modelos: 10 (+ variacoes e fases)")
