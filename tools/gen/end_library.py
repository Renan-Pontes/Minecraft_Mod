"""
Gerador de assets do modulo end_library (v1.6 — Biblioteca do Vazio):
modelos GeckoLib (Devorador de Estrelas, Escriba do Vazio, Fagulha Estelar) + animacoes, texturas de itens/blocos/armadura,
modelos de itens/blocos, loot tables, receitas, tags (merge) e worldgen do minerio/spawns.
A estrutura (.nbt) fica em tools/gen/end_library_struct.py e as traducoes em tools/gen/end_library_lang.py.

Rodar a partir da raiz do projeto:  py -I tools/gen/end_library.py
"""
import importlib.util
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken, item_model  # noqa: E402

# Reaproveita o construtor de modelos com box UV + pintura por face do bosses_g (so as funcoes; main() nao roda).
_spec = importlib.util.spec_from_file_location("bosses_g_gen", os.path.join(ROOT, "tools", "gen", "bosses_g.py"))
bg = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bg)
Model, P, chain, speckle, glow_faces, cracks, hem, teeth, rim, solid = (bg.Model, bg.P, bg.chain, bg.speckle, bg.glow_faces,
                                                                        bg.cracks, bg.hem, bg.teeth, bg.rim, bg.solid)
keys, cyc, anim, spin, S, C, save_anims = bg.keys, bg.cyc, bg.anim, bg.spin, bg.S, bg.C, bg.save_anims

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/end_library")
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
#  Devorador de Estrelas (chefe)
# =====================================================================================
VOID = "#1A0F2E"
VOID_L = "#2E1C4E"
STAR = "#FFFFFF"
STAR2 = "#CDB8FF"
ARC = "#B98CFF"
GOLD = "#FFE9A8"
TEETH = "#F1EAFF"
GUM = "#5A1A6A"


def build_star_devourer():
    m = Model("star_devourer")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 32, 0), "root")
    sky = chain(speckle(STAR, 0.05, 11, g=True), speckle(STAR2, 0.04, 23, g=True), cracks(ARC, 5, 4, ("front", "left", "right", "top")))
    m.cube("body", (-12, 20, -12), (24, 24, 24), P(VOID, deco=sky, noise=0.07))
    # bordas arredondadas (tampa de cima/baixo e laterais)
    m.cube("body", (-10, 18, -10), (20, 2, 20), P(VOID_L, deco=speckle(STAR2, 0.06, 31, g=True)))
    m.cube("body", (-10, 44, -10), (20, 2, 20), P(VOID_L, deco=speckle(STAR, 0.06, 37, g=True)))
    m.cube("body", (-14, 22, -10), (2, 20, 20), P(VOID_L, deco=speckle(STAR, 0.05, 41, g=True)))
    m.cube("body", (12, 22, -10), (2, 20, 20), P(VOID_L, deco=speckle(STAR, 0.05, 43, g=True)))
    m.cube("body", (-10, 22, 12), (20, 20, 2), P(VOID_L, deco=speckle(STAR2, 0.05, 47, g=True)))
    # o vazio dentro da boca (aparece quando as mandibulas abrem)
    m.cube("body", (-9, 26, -17), (18, 8, 2), P("#7A2CFF", deco=glow_faces("#7A2CFF", core="#E8D8FF")))

    m.bone("upper_jaw", (0, 34, -12), "body")

    def ujaw(fs):
        teeth(TEETH, GUM, "front", True, 2)(fs)
        fs["bottom"].fill(H(GUM))
        speckle(STAR2, 0.05, 53, faces=("top", "left", "right"), g=True)(fs)
    m.cube("upper_jaw", (-11, 32, -21), (22, 8, 9), P(VOID_L, deco=ujaw))
    m.bone("lower_jaw", (0, 28, -12), "body")

    def ljaw(fs):
        teeth(TEETH, GUM, "front", False, 2)(fs)
        fs["top"].fill(H(GUM))
    m.cube("lower_jaw", (-10, 21, -20), (20, 6, 8), P(VOID_L, deco=ljaw))

    m.bone("eye", (0, 47, -10), "body")

    def eye(fs):
        f = fs["front"]
        f.rect(0, 0, f.w - 1, f.h - 1, H("#FFD24A"), True)
        f.rect(1, 1, f.w - 2, f.h - 2, H("#FFF2B0"), True)
        f.rect(3, 1, 4, f.h - 2, H("#2A0A3A"), True)
        f.set(3, 1, H(STAR), True)
    m.cube("eye", (-4, 44, -15), (8, 6, 5), P(VOID_L, deco=eye))

    m.bone("crown", (0, 46, 0), "body")
    for i in range(6):
        a = math.tau * i / 6
        x, z = math.cos(a) * 8, math.sin(a) * 8
        rx, rz = -25 * math.sin(a), 25 * math.cos(a)
        m.cube("crown", (x - 1, 45, z - 1), (2, 10, 2), P(GOLD, deco=glow_faces(GOLD, core=STAR)),
               rotation=[round(rx, 2), 0, round(rz, 2)], pivot=[round(x, 2), 45, round(z, 2)])

    m.bone("halo", (0, 34, 15), "body")
    for i in range(8):
        a = 360 * i / 8
        r = 18
        x = math.cos(math.radians(a)) * r
        y = 34 + math.sin(math.radians(a)) * r
        m.cube("halo", (x - 4, y - 1, 14), (8, 2, 1), P(ARC, deco=glow_faces(ARC, core=STAR2)),
               rotation=[0, 0, round(a + 90, 2)], pivot=[round(x, 2), round(y, 2), 14.5])

    m.bone("orbit", (0, 32, 0), "root")
    for i in range(3):
        a = math.tau * i / 3
        x, z = math.cos(a) * 22, math.sin(a) * 22
        m.cube("orbit", (x - 2, 30, z - 2), (4, 4, 4), P(STAR, deco=glow_faces("#FFF2B0", core=STAR)))

    for i, (x, z) in enumerate(((-7, -6), (7, -6), (-7, 7), (7, 7))):
        b1 = m.bone(f"tent{i}_1", (x, 20, z), "body")
        m.cube(b1, (x - 2, 10, z - 2), (4, 10, 4), P(VOID_L, deco=speckle(STAR2, 0.08, 60 + i, g=True)))
        b2 = m.bone(f"tent{i}_2", (x, 10, z), b1)
        m.cube(b2, (x - 1.5, 1, z - 1.5), (3, 9, 3), P(VOID, deco=speckle(ARC, 0.1, 70 + i, g=True)))
        b3 = m.bone(f"tent{i}_3", (x, 1, z), b2)
        m.cube(b3, (x - 1, -7, z - 1), (2, 8, 2), P("#3A1A5E", deco=glow_faces("#6A3AB0", faces=("bottom",))))
    m.save(bounds=(7, 6), seed=161)

    tents = [(f"tent{i}", sx, sz) for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1)))]
    a = {}

    def sway(length, amp, speed=1, extra=0.0):
        out = {}
        for n, sx, sz in tents:
            ph = (int(n[-1]) * 0.25) + extra
            for seg, k in (("_1", 1.0), ("_2", 1.4), ("_3", 1.8)):
                out[n + seg] = {"rotation": cyc(length, lambda p, ph=ph, k=k, sz=sz, sx=sx: [
                    amp * k * S(p * speed + ph) * 0.6 + 6 * sz, 0, amp * k * C(p * speed + ph) * 0.5 - 6 * sx], steps=8)}
        return out

    idle = {"body": {"position": cyc(3.0, lambda p: [0, 1.5 * S(p), 0])},
            "upper_jaw": {"rotation": cyc(3.0, lambda p: [-3 - 3 * S(p), 0, 0])},
            "lower_jaw": {"rotation": cyc(3.0, lambda p: [3 + 4 * S(p), 0, 0])},
            "orbit": {"rotation": spin(3.0, 1, 1)},
            "halo": {"rotation": cyc(3.0, lambda p: [0, 0, 45 * p])},
            "eye": {"scale": keys([(0, [1, 1, 1]), (2.6, [1, 1, 1]), (2.75, [1, 0.15, 1]), (2.9, [1, 1, 1]), (3.0, [1, 1, 1])])}}
    idle.update(sway(3.0, 12))
    a["idle"] = anim(3.0, idle, loop=True)
    walk = {"body": {"position": cyc(2.0, lambda p: [0, 1.5 * S(p), 0]), "rotation": keys([(0, [-8, 0, 0]), (2.0, [-8, 0, 0])])},
            "orbit": {"rotation": spin(2.0, 1, 1)},
            "halo": {"rotation": cyc(2.0, lambda p: [0, 0, 45 * p])}}
    walk.update(sway(2.0, 18))
    for n, sx, sz in tents:
        walk[n + "_1"]["rotation"] = cyc(2.0, lambda p, sz=sz: [25 + 10 * S(p) + 4 * sz, 0, 0], steps=8)
    a["walk"] = anim(2.0, walk, loop=True)
    sleep = {"body": {"position": keys([(0, [0, -8, 0]), (4.0, [0, -8, 0])])},
             "eye": {"scale": keys([(0, [1, 0.1, 1]), (4.0, [1, 0.1, 1])])},
             "upper_jaw": {"rotation": keys([(0, [0, 0, 0]), (4.0, [0, 0, 0])])},
             "orbit": {"rotation": spin(4.0, 1, 1), "scale": keys([(0, [0.6, 0.6, 0.6]), (4.0, [0.6, 0.6, 0.6])])},
             "halo": {"scale": cyc(4.0, lambda p: [0.7 + 0.05 * S(p)] * 3)},
             "crown": {"scale": keys([(0, [1, 0.6, 1]), (4.0, [1, 0.6, 1])])}}
    for n, sx, sz in tents:
        sleep[n + "_1"] = {"rotation": keys([(0, [55 * -sz, 0, 55 * sx]), (4.0, [55 * -sz, 0, 55 * sx])])}
        sleep[n + "_2"] = {"rotation": keys([(0, [50 * -sz, 0, 50 * sx]), (4.0, [50 * -sz, 0, 50 * sx])])}
        sleep[n + "_3"] = {"rotation": keys([(0, [45 * -sz, 0, 45 * sx]), (4.0, [45 * -sz, 0, 45 * sx])])}
    a["sleep"] = anim(4.0, sleep, loop=True)
    intro = {"body": {"position": keys([(0, [0, -8, 0]), (1.0, [0, -2, 0]), (1.6, [0, 6, 0]), (2.6, [0, 6, 0]), (3.0, [0, 0, 0])]),
                      "rotation": keys([(0, [0, 0, 0]), (1.6, [-18, 0, 0]), (2.6, [-18, 0, 0]), (3.0, [0, 0, 0])])},
             "eye": {"scale": keys([(0, [1, 0.1, 1]), (0.8, [1, 0.1, 1]), (1.1, [1.3, 1.3, 1.3]), (3.0, [1, 1, 1])])},
             "upper_jaw": {"rotation": keys([(0, [0, 0, 0]), (1.4, [-10, 0, 0]), (1.7, [-38, 0, 0]), (2.6, [-38, 0, 0]), (3.0, [-3, 0, 0])])},
             "lower_jaw": {"rotation": keys([(0, [0, 0, 0]), (1.4, [8, 0, 0]), (1.7, [34, 0, 0]), (2.6, [34, 0, 0]), (3.0, [3, 0, 0])])},
             "halo": {"scale": keys([(0, [0.7, 0.7, 0.7]), (1.6, [1.4, 1.4, 1.4]), (3.0, [1, 1, 1])]),
                      "rotation": keys([(0, [0, 0, 0]), (3.0, [0, 0, 90])])},
             "orbit": {"rotation": spin(3.0, 1, 2), "scale": keys([(0, [0.6, 0.6, 0.6]), (1.6, [1.2, 1.2, 1.2]), (3.0, [1, 1, 1])])}}
    for n, sx, sz in tents:
        intro[n + "_1"] = {"rotation": keys([(0, [55 * -sz, 0, 55 * sx]), (1.0, [55 * -sz, 0, 55 * sx]), (1.7, [-40 * -sz, 0, -40 * sx]),
                                             (2.6, [-30 * -sz, 0, -30 * sx]), (3.0, [6 * sz, 0, -6 * sx])])}
        intro[n + "_2"] = {"rotation": keys([(0, [50 * -sz, 0, 50 * sx]), (1.7, [-20 * -sz, 0, -20 * sx]), (3.0, [0, 0, 0])])}
        intro[n + "_3"] = {"rotation": keys([(0, [45 * -sz, 0, 45 * sx]), (1.7, [-15 * -sz, 0, -15 * sx]), (3.0, [0, 0, 0])])}
    a["intro"] = anim(3.0, intro)
    death = {"root": {"scale": keys([(0, [1, 1, 1]), (1.2, [1.1, 1.1, 1.1]), (2.5, [0.1, 0.1, 0.1])]),
                      "rotation": keys([(0, [0, 0, 0]), (2.5, [0, 540, 0])])},
             "body": {"position": keys([(0, [0, 0, 0]), (2.5, [0, 10, 0])])},
             "upper_jaw": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-45, 0, 0]), (2.5, [-45, 0, 0])])},
             "lower_jaw": {"rotation": keys([(0, [0, 0, 0]), (0.6, [40, 0, 0]), (2.5, [40, 0, 0])])},
             "eye": {"scale": keys([(0, [1, 1, 1]), (0.6, [1.4, 1.4, 1.4]), (2.5, [1, 0.1, 1])])}}
    for n, sx, sz in tents:
        death[n + "_1"] = {"rotation": keys([(0, [0, 0, 0]), (2.5, [-70 * -sz, 0, -70 * sx])])}
    a["death"] = anim(2.5, death)

    def jaws(t_open, t_hold, t_close, up=-40, low=36):
        return {"upper_jaw": {"rotation": keys([(0, [0, 0, 0]), (t_open, [up, 0, 0]), (t_hold, [up, 0, 0]), (t_close, [-3, 0, 0])])},
                "lower_jaw": {"rotation": keys([(0, [0, 0, 0]), (t_open, [low, 0, 0]), (t_hold, [low, 0, 0]), (t_close, [3, 0, 0])])}}
    bite = jaws(0.4, 0.45, 0.6, -50, 45)
    bite["body"] = {"position": keys([(0, [0, 0, 0]), (0.4, [0, 2, 4]), (0.55, [0, -1, -8]), (0.8, [0, 0, 0])])}
    a["bite"] = anim(0.8, bite)
    grav = {"body": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 5, 0]), (1.2, [0, 5, 0]), (1.5, [0, 0, 0])])},
            "eye": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.5, 1.5, 1.5]), (1.2, [1.5, 1.5, 1.5]), (1.5, [1, 1, 1])])},
            "halo": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.4, 1.4, 1.4]), (1.5, [1, 1, 1])]),
                     "rotation": keys([(0, [0, 0, 0]), (1.5, [0, 0, -90])])},
            "orbit": {"rotation": spin(1.5, 1, 2)}}
    for n, sx, sz in tents:
        grav[n + "_1"] = {"rotation": keys([(0, [0, 0, 0]), (0.5, [-50 * -sz, 0, -50 * sx]), (1.2, [-50 * -sz, 0, -50 * sx]), (1.5, [0, 0, 0])])}
    a["gravity"] = anim(1.5, grav)
    met = jaws(0.5, 1.2, 1.6)
    met["body"] = {"rotation": keys([(0, [0, 0, 0]), (0.5, [28, 0, 0]), (1.2, [28, 0, 0]), (1.6, [0, 0, 0])])}
    met["crown"] = {"scale": keys([(0, [1, 1, 1]), (0.5, [1.35, 1.35, 1.35]), (1.2, [1.35, 1.35, 1.35]), (1.6, [1, 1, 1])])}
    a["meteor"] = anim(1.6, met)
    beam = jaws(0.3, 1.2, 1.4, -45, 40)
    beam["body"] = {"position": keys([(0, [0, 0, 0]), (0.3, [0, 0, 2]), (1.2, [0, 0, 4]), (1.4, [0, 0, 0])])}
    beam["eye"] = {"scale": keys([(0, [1, 1, 1]), (0.3, [1.3, 1.3, 1.3]), (1.4, [1, 1, 1])])}
    a["beam"] = anim(1.4, beam)
    hole = jaws(0.4, 1.7, 2.0, -45, 40)
    hole["body"] = {"rotation": keys([(0, [0, 0, 0]), (2.0, [0, 360, 0])])}
    hole["orbit"] = {"rotation": spin(2.0, 1, 3), "scale": keys([(0, [1, 1, 1]), (1.0, [0.5, 0.5, 0.5]), (2.0, [1, 1, 1])])}
    hole["halo"] = {"scale": keys([(0, [1, 1, 1]), (0.6, [1.6, 1.6, 1.6]), (1.7, [1.6, 1.6, 1.6]), (2.0, [1, 1, 1])])}
    a["black_hole"] = anim(2.0, hole)
    summ = {"halo": {"scale": keys([(0, [1, 1, 1]), (0.4, [1.5, 1.5, 1.5]), (0.7, [0.8, 0.8, 0.8]), (1.0, [1, 1, 1])])},
            "crown": {"scale": keys([(0, [1, 1, 1]), (0.4, [1.3, 1.3, 1.3]), (1.0, [1, 1, 1])])}}
    for n, sx, sz in tents:
        summ[n + "_1"] = {"rotation": keys([(0, [0, 0, 0]), (0.4, [-35 * -sz, 0, -35 * sx]), (1.0, [0, 0, 0])])}
    a["summon"] = anim(1.0, summ)
    save_anims("star_devourer", a)


# =====================================================================================
#  Escriba do Vazio
# =====================================================================================
def build_void_scribe():
    m = Model("void_scribe")
    robe, robe_d, trim = "#2B1F4A", "#1A1230", "#B98CFF"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 14, 0), "root")
    m.cube("body", (-5, 0, -4), (10, 14, 8), P(robe, deco=chain(hem(robe_d, True, seed=4), bg.folds(robe_d, 3),
                                                                rim(trim, top=False, bottom=False))))
    m.cube("body", (-4, 14, -2.5), (8, 10, 5), P(robe, deco=chain(bg.band([1], trim, g=True), speckle(trim, 0.03, 9, g=True))))
    m.bone("head", (0, 24, 0), "body")

    def hood(fs):
        f = fs["front"]
        f.rect(1, 2, 7, 8, H("#0A0612"))
        f.rect(2, 4, 2, 4, H("#7FE8FF"), True)
        f.rect(6, 4, 6, 4, H("#7FE8FF"), True)
        f.set(4, 6, H("#3A2A5A"))
        f.row(0, H(trim), True)
    m.cube("head", (-4.5, 24, -4.5), (9, 9, 9), P(robe, deco=hood))
    m.cube("head", (-2, 32, -1), (4, 3, 4), P(robe_d), rotation=[-20, 0, 0], pivot=[0, 32, 1])
    for name, x in (("arm_left", 4), ("arm_right", -7)):
        m.bone(name, (x + 1.5, 23, 0), "body")
        m.cube(name, (x, 13, -2), (3, 10, 4), P(robe, deco=chain(hem(robe_d, True, seed=x + 20), bg.band([2], trim, g=True))))
        m.cube(name, (x + 0.5, 11, -1.5), (2, 2, 3), P("#5A4A7A"))
    m.bone("book", (0, 17, -7), "body")

    def pages(fs):
        f = fs["top"]
        f.fill(H("#EDE6D6"))
        for yy in range(1, f.h - 1):
            for xx in range(1, f.w - 1, 2):
                if (xx + yy) % 3:
                    f.set(xx, yy, H(trim), True)
        f.rect(f.w // 2, 0, f.w // 2, f.h - 1, H("#8A7AA0"))
    m.cube("book", (-4, 15.5, -9.5), (8, 1, 5), P("#4A2A6A", deco=rim("#D8B65A")))
    m.cube("book", (-3.5, 16.5, -9), (7, 1, 4), P("#EDE6D6", deco=pages))
    m.bone("pages", (0, 22, 0), "body")
    for i, (x, y, z) in enumerate(((8, 22, 0), (-7, 26, 4), (-2, 19, -9))):
        m.cube("pages", (x - 1, y - 1.5, z), (2, 3, 1), P("#EDE6D6", deco=chain(speckle(trim, 0.4, 80 + i, faces=("front", "back"), g=True))))
    m.save(bounds=(2, 3), seed=171)

    a = {}
    a["idle"] = anim(2.0, {"body": {"position": cyc(2.0, lambda p: [0, 1.0 * S(p), 0])},
                           "book": {"position": cyc(2.0, lambda p: [0, 0.6 * S(p + 0.25), 0])},
                           "pages": {"rotation": spin(2.0, 1, 1)},
                           "arm_left": {"rotation": cyc(2.0, lambda p: [-35 + 3 * S(p), 0, 0])},
                           "arm_right": {"rotation": cyc(2.0, lambda p: [-35 - 3 * S(p), 0, 0])}}, loop=True)
    a["walk"] = anim(1.0, {"body": {"position": cyc(1.0, lambda p: [0, 1.0 * S(p * 2), 0]), "rotation": keys([(0, [6, 0, 0]), (1.0, [6, 0, 0])])},
                           "pages": {"rotation": spin(1.0, 1, 1)},
                           "arm_left": {"rotation": cyc(1.0, lambda p: [-35 + 8 * S(p), 0, 0])},
                           "arm_right": {"rotation": cyc(1.0, lambda p: [-35 - 8 * S(p), 0, 0])}}, loop=True)
    a["cast"] = anim(0.8, {"arm_left": {"rotation": keys([(0, [-35, 0, 0]), (0.3, [-120, 0, -15]), (0.6, [-100, 0, -10]), (0.8, [-35, 0, 0])])},
                           "arm_right": {"rotation": keys([(0, [-35, 0, 0]), (0.3, [-120, 0, 15]), (0.6, [-100, 0, 10]), (0.8, [-35, 0, 0])])},
                           "book": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 6, -2]), (0.6, [0, 6, -2]), (0.8, [0, 0, 0])]),
                                    "rotation": keys([(0, [0, 0, 0]), (0.3, [-60, 0, 0]), (0.6, [-60, 0, 0]), (0.8, [0, 0, 0])])},
                           "pages": {"rotation": spin(0.8, 1, 2)}})
    save_anims("void_scribe", a)


# =====================================================================================
#  Fagulha Estelar
# =====================================================================================
def build_star_wisp():
    m = Model("star_wisp")
    m.bone("root", (0, 0, 0))
    m.bone("core", (0, 5, 0), "root")
    m.cube("core", (-3, 2, -3), (6, 6, 6), P("#FFF2B0", deco=glow_faces("#FFF2B0", core=STAR)))
    for (o, s) in (((-1, 4, -6), (2, 2, 3)), ((-1, 4, 3), (2, 2, 3)), ((-6, 4, -1), (3, 2, 2)), ((3, 4, -1), (3, 2, 2)),
                   ((-1, 8, -1), (2, 3, 2)), ((-1, -1, -1), (2, 3, 2))):
        m.cube("core", o, s, P("#FFD24A", deco=glow_faces("#FFD24A")))
    m.bone("tail", (0, 5, 3), "root")
    m.cube("tail", (-1.5, 3.5, 3), (3, 3, 4), P("#8A5CFF", deco=glow_faces("#8A5CFF", core="#C9A8FF")))
    m.cube("tail", (-1, 4, 7), (2, 2, 3), P("#5A2CB0", deco=glow_faces("#5A2CB0")))
    m.save(bounds=(1, 1), seed=181)
    a = {"idle": anim(1.0, {"core": {"rotation": spin(1.0, 1, 1)},
                            "root": {"position": cyc(1.0, lambda p: [0, 0.8 * S(p), 0])},
                            "tail": {"rotation": cyc(1.0, lambda p: [0, 15 * S(p), 0])}}, loop=True),
         "dash": anim(0.6, {"core": {"rotation": spin(0.6, 1, 2), "scale": keys([(0, [1, 1, 1]), (0.2, [0.8, 0.8, 1.4]), (0.6, [1, 1, 1])])},
                            "tail": {"scale": keys([(0, [1, 1, 1]), (0.2, [1, 1, 2.0]), (0.6, [1, 1, 1])])}})}
    save_anims("star_wisp", a)


# =====================================================================================
#  Texturas de itens (16x16)
# =====================================================================================
TEX_ITEM = os.path.join(ASSETS, "textures/item")
TEX_BLOCK = os.path.join(ASSETS, "textures/block")


def outline_dark(c, col="#120A20"):
    c.outline(H(col))


def tex_star_fragment():
    c = Canvas(16, 16)
    pts = [(8, 1), (11, 5), (13, 9), (10, 14), (6, 14), (3, 10), (4, 5)]
    for y in range(16):
        for x in range(16):
            inside = False
            j = len(pts) - 1
            for i in range(len(pts)):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > y + 0.5) != (yj > y + 0.5) and x + 0.5 < (xj - xi) * (y + 0.5 - yi) / (yj - yi + 1e-9) + xi:
                    inside = not inside
                j = i
            if inside:
                t = (x + y) / 30
                c.set(x, y, mix(H("#FFF6D0"), H("#A47CFF"), t))
    c.line(8, 2, 8, 13, H("#FFFFFF"))
    c.line(5, 6, 11, 11, H("#E8D8FF"))
    c.set(9, 4, H("#FFFFFF")); c.set(6, 9, H("#FFFFFF"))
    c.noise(0.05, 3)
    outline_dark(c, "#2A1650")
    c.set(13, 2, H("#FFF2B0")); c.set(12, 2, H("#FFF2B0", 160)); c.set(13, 3, H("#FFF2B0", 160))
    c.save(os.path.join(TEX_ITEM, "star_fragment.png"))


def tex_star_ingot():
    c = Canvas(16, 16)
    rows = ["................",
            "................",
            "................",
            "................",
            "......aaaaaaa...",
            ".....abbbbbbca..",
            "....abbbbbbcca..",
            "...abbbbbbccca..",
            "..addddddddcca..",
            "..addddddddca...",
            "..adddddddda....",
            "..aaaaaaaaa.....",
            "................"]
    pal = {"a": H("#140A28"), "b": H("#7A5CD8"), "c": H("#4A2E9A"), "d": H("#5A3CB8")}
    c.from_rows(rows, pal)
    for x, y in ((5, 9), (9, 8), (7, 6)):
        c.set(x, y, H("#FFF2B0"))
    c.set(8, 5, H("#FFFFFF"))
    c.line(3, 8, 10, 8, H("#9C80F0"))
    c.save(os.path.join(TEX_ITEM, "star_ingot.png"))


def tex_template():
    c = Canvas(16, 16)
    c.rect(3, 1, 12, 14, H("#2A2440"))
    c.outline_rect(3, 1, 12, 14, H("#120E20"))
    c.rect(4, 2, 11, 2, H("#3E3660"))
    c.noise(0.06, 5)
    star = [(7, 4), (8, 4), (7, 5), (8, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7),
            (6, 8), (9, 8), (5, 9), (10, 9), (7, 8), (8, 8)]
    for x, y in star:
        c.set(x, y, H("#FFD24A"))
    c.set(7, 4, H("#FFF6D0")); c.set(7, 6, H("#FFF6D0"))
    c.line(5, 12, 10, 12, H("#8A6CFF"))
    c.save(os.path.join(TEX_ITEM, "star_upgrade_smithing_template.png"))


def blade_sprite(path, blade_lo, blade_hi, guard, grip, pommel, edge_glow=None):
    c = Canvas(16, 16)
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.set(x, y, mix(H(blade_lo), H(blade_hi), i / 9))
        c.set(x - 1, y, mix(H(blade_lo), H(blade_hi), i / 9 * 0.7))
        c.set(x, y + 1, darken(mix(H(blade_lo), H(blade_hi), i / 9), 0.25))
    c.set(14, 0, H(edge_glow or blade_hi)); c.set(15, 0, H(edge_glow or blade_hi))
    for x, y in ((2, 9), (3, 10), (4, 11), (5, 12), (6, 13), (3, 9), (6, 12)):
        c.set(x, y, H(guard))
    for x, y in ((3, 12), (2, 13)):
        c.set(x, y, H(grip))
    c.set(4, 12, H(grip)); c.set(3, 13, darken(H(grip), 0.2))
    c.set(1, 14, H(pommel)); c.set(2, 14, H(pommel)); c.set(1, 15, H(pommel))
    outline_dark(c)
    c.save(path)


def tex_tools():
    blade_sprite(os.path.join(TEX_ITEM, "star_sword.png"), "#6A4CD0", "#F2EAFF", "#FFD24A", "#2A1A44", "#FFE9A8", "#FFFFFF")
    c = Canvas(16, 16)
    for i in range(10):  # cabo
        c.set(3 + i, 13 - i, H("#2A1A44") if i % 3 else H("#4A2E70"))
    head = [(4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 3), (11, 3), (12, 4), (12, 5), (13, 6), (13, 7), (13, 8),
            (3, 2), (2, 3), (9, 1), (8, 1), (7, 1), (6, 1), (13, 9), (14, 9)]
    for i, (x, y) in enumerate(head):
        c.set(x, y, mix(H("#F2EAFF"), H("#6A4CD0"), (i % 7) / 7))
    for x, y in ((10, 4), (11, 5), (9, 3)):
        c.set(x, y, H("#FFD24A"))
    c.set(12, 3, H("#FFFFFF"))
    outline_dark(c)
    c.save(os.path.join(TEX_ITEM, "star_pickaxe.png"))


ARMOR_ROWS = {
    "star_helmet": ["................", "................", "................", "....aaaaaaaa....", "...abbbbbbbba...",
                    "...abcbbbbcba...", "...abbbbbbbba...", "...ab......ba...", "...ab......ba...", "...aa......aa...",
                    "................"],
    "star_chestplate": ["................", "..aaa......aaa..", ".abba......abba.", ".abbaaaaaaaabba.", ".abbbbbcbbbbbba.",
                        ".aaabbbbbbbbaaa.", "...abbcbbcbba...", "...abbbbbbbba...", "...abbbbbbbba...", "...abbbcbbbba...",
                        "...abbbbbbbba...", "...aaaaaaaaaa..."],
    "star_leggings": ["................", "................", "...aaaaaaaaaa...", "...abbbcbbbba...", "...abbbbbbbba...",
                      "...abbbaabbba...", "...abba..abba...", "...abba..abba...", "...abba..abba...", "...abba..abba...",
                      "...abca..acba...", "...aaaa..aaaa..."],
    "star_boots": ["................", "................", "................", "................", "................",
                   "................", "...aaaa..aaaa...", "...abba..abba...", "...abba..abba...", "..abbba..abbba..",
                   "..abcba..abcba..", "..aaaaa..aaaaa.."],
}


def tex_armor_items():
    pal = {"a": H("#120A20"), "b": H("#5A3CB8"), "c": H("#FFD24A")}
    for name, rows in ARMOR_ROWS.items():
        c = Canvas(16, 16)
        c.from_rows(rows, pal)
        for y in range(16):
            for x in range(16):
                p = c.get(x, y)
                if p == pal["b"]:
                    c.set(x, y, mix(H("#8A6CF0"), H("#3A2486"), (x + y) / 26))
        c.noise(0.05, len(name))
        c.save(os.path.join(TEX_ITEM, name + ".png"))


def tex_void_tome():
    c = Canvas(16, 16)
    c.rect(3, 2, 12, 14, H("#3A1E5A"))
    c.rect(4, 3, 12, 13, H("#4E2A78"))
    c.rect(3, 2, 4, 14, H("#24123A"))
    c.rect(12, 3, 13, 13, H("#EDE6D6"))
    for y in range(4, 13, 2):
        c.set(13, y, H("#BEB4A0"))
    # olho/estrela do selo
    c.circle(8.5, 8, 2.6, H("#FFD24A"))
    c.circle(8.5, 8, 1.6, H("#FFF2B0"))
    c.set(8, 8, H("#2A0A3A")); c.set(8, 7, H("#2A0A3A"))
    # correntes do selo
    for x in range(4, 13):
        if x % 2 == 0:
            c.set(x, 11, H("#A8A0B8"))
        else:
            c.set(x, 11, H("#6A6080"))
    c.noise(0.04, 13)
    outline_dark(c)
    c.save(os.path.join(TEX_ITEM, "void_tome.png"))


def tex_treasure_gate():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.0 <= d <= 7.2:
                c.set(x, y, mix(H("#FFE27A"), H("#B8860B"), (d - 5) / 2.2))
            elif d < 5.0:
                c.set(x, y, mix(H("#FFF6C8", 230), H("#E8B830", 200), d / 5))
    # ondulacao dourada e uma lamina saindo do portal
    for i in range(7):
        c.set(5 + i, 10 - i, H("#E8EEF8"))
        c.set(6 + i, 10 - i, H("#9AA4B8"))
    c.set(4, 11, H("#7A4A1A")); c.set(3, 12, H("#7A4A1A")); c.set(4, 12, H("#D8A040"))
    for x, y in ((8, 2), (13, 8), (8, 13), (2, 8)):
        c.set(x, y, H("#FFFFFF"))
    outline_dark(c, "#4A2A08")
    c.save(os.path.join(TEX_ITEM, "void_treasure_gate.png"))


def tex_overlord_staff():
    c = Canvas(16, 16)
    for i in range(11):
        x, y = 2 + i, 14 - i
        c.set(x, y, H("#E8C050") if i % 2 else H("#B88A20"))
        c.set(x + 1, y, H("#8A6010"))
    # coroa de serpentes douradas com 7 gemas
    ring = [(11, 1), (13, 1), (14, 3), (14, 5), (12, 6), (10, 5), (10, 3)]
    gems = ["#FF3B30", "#3B8BFF", "#4CFF7A", "#FFD24A", "#B98CFF", "#FFFFFF", "#FF8A1C"]
    for (x, y), g in zip(ring, gems):
        c.set(x, y, H(g))
    for x, y in ((12, 1), (14, 2), (14, 4), (13, 6), (11, 6), (10, 4), (10, 2), (12, 2), (12, 3), (12, 4), (13, 4), (11, 4)):
        if c.get(x, y)[3] == 0:
            c.set(x, y, H("#D8A830"))
    c.set(12, 3, H("#2A0A3A")); c.set(12, 4, H("#5A1A8A"))
    outline_dark(c, "#2A1A08")
    c.save(os.path.join(TEX_ITEM, "void_overlord_staff.png"))


def tex_blocks():
    rnd = random.Random(77)
    # Minerio de Fragmento Estelar (pedra do End com cristais)
    c = Canvas(16, 16)
    base = H("#DCDDA0")
    for y in range(16):
        for x in range(16):
            k = rnd.random()
            col = base if k > 0.25 else (H("#C8C88C") if k > 0.08 else H("#ECEDB8"))
            c.set(x, y, col)
    for x, y in ((3, 4), (11, 2), (7, 11), (13, 12), (2, 13), (9, 7)):
        c.set(x, y, H("#B4B07A"))
    for cx, cy in ((4, 5), (11, 10), (8, 3), (3, 12)):
        for dx, dy, col in ((0, 0, "#FFFFFF"), (1, 0, "#E8D8FF"), (0, 1, "#C9A8FF"), (-1, 0, "#FFF2B0"), (0, -1, "#FFF2B0"),
                            (1, 1, "#8A6CFF")):
            c.set(cx + dx, cy + dy, H(col))
    c.save(os.path.join(TEX_BLOCK, "star_fragment_ore.png"))
    # Estante do Vazio (lado e topo)
    c = Canvas(16, 16)
    frame, frame_d = H("#3B2752"), H("#24163A")
    c.rect(0, 0, 15, 15, frame)
    c.rect(0, 0, 15, 0, frame_d); c.rect(0, 15, 15, 15, frame_d); c.rect(0, 7, 15, 8, frame_d)
    cols = ["#6A3AB0", "#2E8C8C", "#8A2A6A", "#C9A8FF", "#4A5CC8", "#D8B65A", "#5A2CB0"]
    for row_y in (1, 9):
        x = 1
        while x < 15:
            w = 1 if rnd.random() < 0.6 else 2
            col = H(rnd.choice(cols))
            h0 = row_y + (1 if rnd.random() < 0.3 else 0)
            c.rect(x, h0, min(14, x + w - 1), row_y + 5, col)
            c.set(x, row_y + 2, lighten(col, 0.4))
            x += w + (1 if rnd.random() < 0.15 else 0)
    c.save(os.path.join(TEX_BLOCK, "void_bookshelf.png"))
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, H("#4A3268") if (y // 4) % 2 == 0 else H("#3E2A5A"))
    for y in (3, 7, 11, 15):
        c.rect(0, y, 15, y, H("#24163A"))
    c.noise(0.05, 4)
    c.save(os.path.join(TEX_BLOCK, "void_bookshelf_top.png"))


def tex_armor_layers():
    rnd = random.Random(5)
    base_a, base_b, trim, dark = H("#6A4CD0"), H("#3A2486"), H("#FFD24A"), H("#1A1030")

    def paint(c, x0, y0, w, h, band_rows=(), star=False):
        for yy in range(h):
            for xx in range(w):
                col = mix(base_a, base_b, yy / max(1, h - 1))
                if rnd.random() < 0.08:
                    col = lighten(col, 0.15)
                c.set(x0 + xx, y0 + yy, col)
        for r in band_rows:
            c.rect(x0, y0 + r, x0 + w - 1, y0 + r, trim)
        if star and w >= 4 and h >= 4:
            c.set(x0 + w // 2, y0 + h // 2, H("#FFFFFF"))

    l1 = Canvas(64, 32)
    # capacete (cabeca 8x8x8 em 0,0) — frente com viseira aberta
    paint(l1, 8, 0, 8, 8)
    paint(l1, 0, 8, 8, 8, (5,))
    paint(l1, 16, 8, 8, 8, (5,))
    paint(l1, 24, 8, 8, 8, (5,))
    paint(l1, 8, 8, 8, 8, (3,))
    for y in range(12, 16):
        for x in range(10, 14):
            l1.set(x, y, CLEAR)
    l1.set(11, 8, H("#FFFFFF")); l1.set(12, 8, H("#FFFFFF"))
    # peitoral: tronco (16,16) e bracos (40,16)
    paint(l1, 20, 16, 8, 4)
    paint(l1, 20, 20, 8, 12, (0, 11), star=True)
    paint(l1, 16, 20, 4, 12, (0,))
    paint(l1, 28, 20, 4, 12, (0,))
    paint(l1, 32, 20, 8, 12, (0, 11))
    paint(l1, 44, 16, 4, 4)
    for x0 in (40, 44, 48, 52):
        paint(l1, x0, 20, 4, 12, (0, 9))
    # botas: parte de baixo das pernas (0,16)
    paint(l1, 8, 16, 4, 4)
    for x0 in (0, 4, 8, 12):
        paint(l1, x0, 26, 4, 6, (0,))
    for x, y in ((5, 30), (6, 30)):
        l1.set(x, y, dark)
    l1.save(os.path.join(ASSETS, "textures/models/armor/star_layer_1.png"))
    l2 = Canvas(64, 32)
    for x0 in (0, 4, 8, 12):
        paint(l2, x0, 20, 4, 12, (0, 7))
    paint(l2, 4, 16, 4, 4)
    paint(l2, 20, 26, 8, 6, (0,))
    paint(l2, 16, 26, 4, 6, (0,))
    paint(l2, 28, 26, 4, 6, (0,))
    paint(l2, 32, 26, 8, 6, (0,))
    l2.save(os.path.join(ASSETS, "textures/models/armor/star_layer_2.png"))


# =====================================================================================
#  Modelos de itens/blocos
# =====================================================================================
def models():
    gen = ["star_fragment", "star_ingot", "star_upgrade_smithing_template", "star_helmet", "star_chestplate", "star_leggings",
           "star_boots", "void_tome", "void_treasure_gate"]
    for i in gen:
        item_model(ASSETS, i)
    for i in ("star_sword", "star_pickaxe", "void_overlord_staff"):
        item_model(ASSETS, i, parent="minecraft:item/handheld")
    for e in ("star_devourer", "void_scribe", "star_wisp"):
        write_json(os.path.join(ASSETS, "models/item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    # blocos
    write_json(os.path.join(ASSETS, "blockstates/star_fragment_ore.json"), {"variants": {"": {"model": f"{NS}:block/star_fragment_ore"}}})
    write_json(os.path.join(ASSETS, "models/block/star_fragment_ore.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/star_fragment_ore"}})
    write_json(os.path.join(ASSETS, "models/item/star_fragment_ore.json"), {"parent": f"{NS}:block/star_fragment_ore"})
    write_json(os.path.join(ASSETS, "blockstates/void_bookshelf.json"), {"variants": {"": {"model": f"{NS}:block/void_bookshelf"}}})
    write_json(os.path.join(ASSETS, "models/block/void_bookshelf.json"),
               {"parent": "minecraft:block/cube_column",
                "textures": {"end": f"{NS}:block/void_bookshelf_top", "side": f"{NS}:block/void_bookshelf"}})
    write_json(os.path.join(ASSETS, "models/item/void_bookshelf.json"), {"parent": f"{NS}:block/void_bookshelf"})


# =====================================================================================
#  Loot, receitas, tags, worldgen
# =====================================================================================
def item_entry(item, lo=1, hi=1, weight=1, fns=None):
    e = {"type": "minecraft:item", "name": item if ":" in item else "minecraft:" + item, "weight": weight}
    f = []
    if (lo, hi) != (1, 1):
        f.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fns:
        f += fns
    if f:
        e["functions"] = f
    return e


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def lpool(entries, rolls=1, hi=None, conditions=None):
    r = rolls if hi is None else {"type": "minecraft:uniform", "min": rolls, "max": hi}
    p = {"rolls": r, "bonus_rolls": 0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def empty(w):
    return {"type": "minecraft:empty", "weight": w}


KILLED_BY_PLAYER = [{"condition": "minecraft:killed_by_player"}]


def write_loot():
    ent = os.path.join(DATA, "loot_table/entities")
    write_json(os.path.join(ent, "star_devourer.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("ender_pearl", 4, 8)]),
        lpool([item_entry("chorus_fruit", 6, 12)]),
        lpool([item_entry("experience_bottle", 5, 10)]),
        lpool([item_entry("shulker_shell", 1, 3)]),
    ], "random_sequence": NS + ":entities/star_devourer"})
    write_json(os.path.join(ent, "void_scribe.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("paper", 1, 3, 3), item_entry("book", 1, 1, 1)]),
        lpool([item_entry("ender_pearl", 1, 1, 1), empty(2)]),
        lpool([item_entry("lapis_lazuli", 1, 3, 1), empty(2)]),
        lpool([item_entry(NS + ":void_tome", 1, 1, 3), empty(97)], conditions=KILLED_BY_PLAYER),
    ], "random_sequence": NS + ":entities/void_scribe"})
    write_json(os.path.join(ent, "star_wisp.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("glowstone_dust", 1, 2)]),
        lpool([item_entry(NS + ":star_fragment", 1, 1, 8), empty(92)], conditions=KILLED_BY_PLAYER),
    ], "random_sequence": NS + ":entities/star_wisp"})

    ch = os.path.join(DATA, "loot_table/chests")
    write_json(os.path.join(ch, "end_library.json"), {"type": "minecraft:chest", "pools": [
        lpool([item_entry("paper", 3, 9, 6), item_entry("book", 1, 4, 6), item_entry("ender_pearl", 1, 3, 4),
               item_entry("chorus_fruit", 2, 6, 4), item_entry("experience_bottle", 2, 6, 4), item_entry("lapis_lazuli", 3, 9, 4),
               item_entry("gold_ingot", 2, 5, 3), item_entry("candle", 1, 3, 2), item_entry("ink_sac", 1, 3, 2),
               item_entry("feather", 1, 4, 2), item_entry("amethyst_shard", 2, 6, 2), item_entry("diamond", 1, 2, 1)], 4, 7),
        lpool([item_entry("book", 1, 1, 5, [ench(20, 30)]), item_entry("golden_apple", 1, 1, 2),
               item_entry(NS + ":star_fragment", 1, 2, 3), item_entry(NS + ":mana_crystal", 2, 5, 3)], 1, 2),
        lpool([item_entry(NS + ":void_tome", 1, 1, 3), empty(7)]),
    ], "random_sequence": NS + ":chests/end_library"})
    write_json(os.path.join(ch, "end_library_archive.json"), {"type": "minecraft:chest", "pools": [
        lpool([item_entry("book", 2, 5, 5), item_entry("ender_pearl", 2, 4, 4), item_entry("experience_bottle", 4, 10, 5),
               item_entry("shulker_shell", 1, 2, 2), item_entry("diamond", 1, 3, 3), item_entry("emerald", 2, 6, 3),
               item_entry("gold_ingot", 3, 7, 3), item_entry("end_crystal", 1, 1, 1), item_entry("dragon_breath", 1, 3, 2)], 3, 6),
        lpool([item_entry("book", 1, 1, 6, [ench(30, 39)]), item_entry("diamond_sword", 1, 1, 1, [ench(30, 39)]),
               item_entry("enchanted_golden_apple", 1, 1, 1), item_entry(NS + ":star_fragment", 1, 3, 5),
               item_entry(NS + ":mana_crystal", 4, 8, 3)], 1, 3),
        lpool([item_entry(NS + ":void_tome", 1, 2, 6), empty(4)]),
        lpool([item_entry(NS + ":star_upgrade_smithing_template", 1, 1, 12), empty(88)]),
        lpool([item_entry(NS + ":void_treasure_gate", 1, 1, 3), item_entry(NS + ":void_overlord_staff", 1, 1, 3),
               item_entry(NS + ":relic_seraph_feather", 1, 1, 1), item_entry(NS + ":relic_dragon_scale", 1, 1, 1), empty(92)]),
    ], "random_sequence": NS + ":chests/end_library_archive"})

    blk = os.path.join(DATA, "loot_table/blocks")
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write_json(os.path.join(blk, "star_fragment_ore.json"), {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:star_fragment_ore", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{NS}:star_fragment", "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}], "random_sequence": NS + ":blocks/star_fragment_ore"})
    write_json(os.path.join(blk, "void_bookshelf.json"), {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:void_bookshelf"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": NS + ":blocks/void_bookshelf"})


def write_recipes():
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "star_ingot.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{NS}:star_fragment"}] * 4 + [{"item": "minecraft:gold_ingot"}] * 2
                       + [{"item": "minecraft:diamond"}, {"item": "minecraft:ender_eye"}],
        "result": {"id": f"{NS}:star_ingot", "count": 1}})
    write_json(os.path.join(rec, "star_upgrade_smithing_template.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["DTD", "DED", "DDD"],
        "key": {"D": {"item": "minecraft:diamond"}, "T": {"item": f"{NS}:star_upgrade_smithing_template"},
                "E": {"item": "minecraft:end_stone"}},
        "result": {"id": f"{NS}:star_upgrade_smithing_template", "count": 2}})
    write_json(os.path.join(rec, "void_bookshelf.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["PPP", "BCB", "PPP"],
        "key": {"P": {"item": "minecraft:purpur_block"}, "B": {"item": "minecraft:book"}, "C": {"item": "minecraft:popped_chorus_fruit"}},
        "result": {"id": f"{NS}:void_bookshelf", "count": 1}})
    for piece in ("sword", "pickaxe", "helmet", "chestplate", "leggings", "boots"):
        write_json(os.path.join(rec, f"star_{piece}_smithing.json"), {
            "type": "minecraft:smithing_transform",
            "template": {"item": f"{NS}:star_upgrade_smithing_template"},
            "base": {"item": f"minecraft:netherite_{piece}"},
            "addition": {"item": f"{NS}:star_ingot"},
            "result": {"id": f"{NS}:star_{piece}", "count": 1}})


def write_tags_and_worldgen():
    mt = os.path.join(MERGE, "data/minecraft/tags")
    write_json(os.path.join(mt, "block/mineable/pickaxe.json"), {"values": [f"{NS}:star_fragment_ore"]})
    write_json(os.path.join(mt, "block/needs_diamond_tool.json"), {"values": [f"{NS}:star_fragment_ore"]})
    write_json(os.path.join(mt, "block/mineable/axe.json"), {"values": [f"{NS}:void_bookshelf"]})
    write_json(os.path.join(mt, "block/enchantment_power_provider.json"), {"values": [f"{NS}:void_bookshelf"]})
    write_json(os.path.join(mt, "item/swords.json"), {"values": [f"{NS}:star_sword"]})
    write_json(os.path.join(mt, "item/pickaxes.json"), {"values": [f"{NS}:star_pickaxe"]})
    write_json(os.path.join(mt, "item/head_armor.json"), {"values": [f"{NS}:star_helmet"]})
    write_json(os.path.join(mt, "item/chest_armor.json"), {"values": [f"{NS}:star_chestplate"]})
    write_json(os.path.join(mt, "item/leg_armor.json"), {"values": [f"{NS}:star_leggings"]})
    write_json(os.path.join(mt, "item/foot_armor.json"), {"values": [f"{NS}:star_boots"]})
    write_json(os.path.join(mt, "item/trimmable_armor.json"),
               {"values": [f"{NS}:star_{p}" for p in ("helmet", "chestplate", "leggings", "boots")]})
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": [f"{NS}:star_devourer"]})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/end_library.json"),
               {"values": ["minecraft:end_highlands", "minecraft:end_midlands"]})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/boss_lair.json"), {"values": [f"{NS}:end_library"]})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/lair/end_library.json"), {"values": [f"{NS}:end_library"]})

    wg = os.path.join(DATA, "worldgen")
    write_json(os.path.join(wg, "configured_feature/star_fragment_ore.json"), {
        "type": "minecraft:ore", "config": {"size": 4, "discard_chance_on_air_exposure": 0.3, "targets": [
            {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"},
             "state": {"Name": f"{NS}:star_fragment_ore"}}]}})
    write_json(os.path.join(wg, "placed_feature/star_fragment_ore.json"), {
        "feature": f"{NS}:star_fragment_ore", "placement": [
            {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 8},
                                                           "max_inclusive": {"absolute": 72}}},
            {"type": "minecraft:biome"}]})
    end_outer = ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands"]
    bm = os.path.join(DATA, "neoforge/biome_modifier")
    write_json(os.path.join(bm, "end_library_star_ore.json"), {
        "type": "neoforge:add_features", "biomes": end_outer, "features": f"{NS}:star_fragment_ore", "step": "underground_ores"})
    write_json(os.path.join(bm, "end_library_void_spawns.json"), {
        "type": "neoforge:add_spawns", "biomes": ["minecraft:end_highlands", "minecraft:end_midlands"],
        "spawners": [{"type": f"{NS}:void_scribe", "weight": 3, "minCount": 1, "maxCount": 2},
                     {"type": f"{NS}:star_wisp", "weight": 4, "minCount": 1, "maxCount": 3}]})

    write_json(os.path.join(wg, "structure/end_library.json"), {
        "type": f"{NS}:end_library", "biomes": f"#{NS}:has_structure/end_library", "step": "surface_structures",
        "terrain_adaptation": "beard_thin", "spawn_overrides": {}})
    write_json(os.path.join(wg, "structure_set/end_library.json"), {
        "structures": [{"structure": f"{NS}:end_library", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 28, "separation": 10, "salt": 1946300964}})


def main():
    build_star_devourer()
    build_void_scribe()
    build_star_wisp()
    tex_star_fragment()
    tex_star_ingot()
    tex_template()
    tex_tools()
    tex_armor_items()
    tex_void_tome()
    tex_treasure_gate()
    tex_overlord_staff()
    tex_blocks()
    tex_armor_layers()
    models()
    write_loot()
    write_recipes()
    write_tags_and_worldgen()
    print("end_library: assets gerados")


if __name__ == "__main__":
    main()
