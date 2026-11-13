"""
Modelo GeckoLib do chefe das Torres de Desafio: Lorde Kobold do Pinaculo (tower_kobold_lord).
Reaproveita o empacotador/pintor de modelos de tools/gen/nether_citadel_geo.py (Model/Cube/anim).
Importado por tools/gen/towers.py (nao rodar sozinho).
"""
import random

from nether_citadel_geo import Model, Cube, anim, save_anims, H  # noqa: F401
from pixelart import darken

FUR = "#8A5A34"
FUR_L = "#B07A4A"
FUR_D = "#5E3A20"
MANE = "#4A2C18"
LEATHER = "#3E2A1C"
ARMOR = "#5A606A"
ARMOR_L = "#8A929C"
GOLD = "#D9A632"
CLOTH = "#8C1E1E"
EYE = "#FFE04A"
STEEL = "#B8C0C8"
WOOD = "#5A3A20"
BLADE = "#DDE6EE"
EDGE = "#9FE8FF"


def deco_head(f, c):
    fr = f["front"]
    fr.rect(1, 3, 2, 3, EYE, True)
    fr.rect(fr.w - 3, 3, fr.w - 2, 3, EYE, True)
    fr.rect(0, 2, fr.w - 1, 2, darken(H(MANE), 0.2))


def deco_snout(f, c):
    fr = f["front"]
    fr.rect(2, 0, 3, 1, "#1A100A")
    for x in range(fr.w):
        if x % 2 == 0:
            fr.set(x, fr.h - 1, "#F0E8D8")


def deco_plate(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, 0, GOLD)
    fr.rect(0, fr.h - 1, fr.w - 1, fr.h - 1, GOLD)
    cx = fr.w // 2
    fr.rect(cx - 1, 2, cx, fr.h - 3, GOLD)


def deco_trim(f, c):
    for n in ("front", "back", "left", "right"):
        f[n].rect(0, f[n].h - 1, f[n].w - 1, f[n].h - 1, GOLD)


def deco_shield(f, c):
    for n in ("left", "right"):
        fc = f[n]
        fc.rect(0, 0, fc.w - 1, 0, GOLD)
        fc.rect(0, fc.h - 1, fc.w - 1, fc.h - 1, GOLD)
        fc.rect(0, 0, 0, fc.h - 1, GOLD)
        fc.rect(fc.w - 1, 0, fc.w - 1, fc.h - 1, GOLD)
        fc.rect(fc.w // 2 - 1, fc.h // 2 - 1, fc.w // 2, fc.h // 2, "#E8C860")


def deco_axe(f, c):
    for n in ("left", "right"):
        fc = f[n]
        fc.rect(0, 0, 0, fc.h - 1, "#F4F8FF")
        rnd = random.Random(n)
        for _ in range(4):
            fc.set(rnd.randrange(1, fc.w), rnd.randrange(fc.h), darken(H(STEEL), 0.25))


def deco_blade(f, c):
    for n in ("left", "right", "top", "bottom", "front"):
        fc = f[n]
        if fc.h >= fc.w:
            fc.rect(0, 0, 0, fc.h - 1, EDGE, True)
        else:
            fc.rect(0, 0, fc.w - 1, 0, EDGE, True)


def kobold_lord():
    m = Model("tower_kobold_lord", 160, 160)
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-4, 18, 0], [
        Cube([-7.5, 4, -3], [6, 14, 6], FUR_D),
        Cube([-8, 0, -4.5], [7, 4, 8], LEATHER, deco_trim)])
    m.bone("left_leg", "root", [4, 18, 0], [
        Cube([1.5, 4, -3], [6, 14, 6], FUR_D),
        Cube([1, 0, -4.5], [7, 4, 8], LEATHER, deco_trim)])
    m.bone("body", "root", [0, 18, 0], [
        Cube([-7, 16, -4], [14, 8, 8], LEATHER, deco_trim),
        Cube([-9, 24, -5.5], [18, 13, 11], FUR),
        Cube([-8, 25, -6.5], [16, 11, 1], ARMOR_L, deco_plate),
        Cube([-4, 9, -4.6], [8, 8, 1], CLOTH),
        Cube([-6.5, 34, -3], [13, 6, 9], MANE)])
    m.bone("tail", "body", [0, 18, 4], [Cube([-1.5, 16, 4], [3, 3, 12], FUR_D)], rotation=[35, 0, 0])
    m.bone("head", "body", [0, 37, -1], [
        Cube([-5, 37, -6], [10, 9, 9], FUR, deco_head),
        Cube([-3, 37, -11], [6, 5, 5], FUR_L, deco_snout),
        Cube([-5, 46, -3], [3, 5, 2], FUR_D),
        Cube([2, 46, -3], [3, 5, 2], FUR_D),
        Cube([-5.5, 45, -6.5], [11, 2, 10], GOLD),
        Cube([-1, 47, -6.5], [2, 3, 1], GOLD)])
    m.bone("right_arm", "body", [-10, 35, 0], [
        Cube([-15, 32, -4], [6, 5, 8], ARMOR, deco_trim),
        Cube([-14, 18, -3], [5, 15, 6], FUR)])
    m.bone("axe", "right_arm", [-11.5, 19.5, 0], [
        Cube([-12.5, 18.5, -18], [2, 2, 24], WOOD),
        Cube([-13, 13, -19], [3, 14, 5], STEEL, deco_axe),
        Cube([-12.5, 26, -17], [2, 3, 2], GOLD)])
    m.bone("nodachi", "right_arm", [-11.5, 19.5, 0], [
        Cube([-12.5, 18.5, -6], [2, 2, 9], "#2A1A14"),
        Cube([-13.5, 17.5, -7], [4, 4, 1], GOLD),
        Cube([-12, 18.5, -41], [1, 3, 34], BLADE, deco_blade)])
    m.bone("left_arm", "body", [10, 35, 0], [
        Cube([9, 32, -4], [6, 5, 8], ARMOR, deco_trim),
        Cube([9, 18, -3], [5, 15, 6], FUR)])
    m.bone("buckler", "left_arm", [14, 24, 0], [
        Cube([14, 18, -6], [2, 12, 12], ARMOR_L, deco_shield),
        Cube([16, 22, -2], [1, 4, 4], GOLD)])
    return m


def kobold_lord_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.5, [0, 0.5, 0]), (3.0, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [-10, 0, 4]), (1.5, [-14, 0, 6]), (3.0, [-10, 0, 4])]},
            "left_arm": {"rotation": [(0, [-20, 0, -6]), (1.5, [-24, 0, -8]), (3.0, [-20, 0, -6])]},
            "tail": {"rotation": [(0, [35, -10, 0]), (1.5, [35, 10, 0]), (3.0, [35, -10, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (1.5, [-4, 0, 0]), (3.0, [0, 0, 0])]}}
    walk = {"right_leg": {"rotation": [(0, [28, 0, 0]), (0.5, [-28, 0, 0]), (1.0, [28, 0, 0])]},
            "left_leg": {"rotation": [(0, [-28, 0, 0]), (0.5, [28, 0, 0]), (1.0, [-28, 0, 0])]},
            "right_arm": {"rotation": [(0, [-25, 0, 4]), (0.5, [0, 0, 4]), (1.0, [-25, 0, 4])]},
            "left_arm": {"rotation": [(0, [-10, 0, -6]), (0.5, [-30, 0, -6]), (1.0, [-10, 0, -6])]},
            "body": {"position": [(0, [0, 0, 0]), (0.25, [0, 0.8, 0]), (0.5, [0, 0, 0]), (0.75, [0, 0.8, 0]), (1.0, [0, 0, 0])]},
            "tail": {"rotation": [(0, [40, -15, 0]), (0.5, [40, 15, 0]), (1.0, [40, -15, 0])]}}
    sleep = {"body": {"rotation": [(0, [15, 0, 0]), (4.0, [15, 0, 0])], "position": [(0, [0, -4, 0]), (4.0, [0, -4, 0])]},
             "head": {"rotation": [(0, [25, 0, 0]), (2.0, [30, 0, 0]), (4.0, [25, 0, 0])]},
             "right_leg": {"rotation": [(0, [-70, 0, 0]), (4.0, [-70, 0, 0])]},
             "left_leg": {"rotation": [(0, [10, 0, 0]), (4.0, [10, 0, 0])]},
             "right_arm": {"rotation": [(0, [-50, 0, 10]), (4.0, [-50, 0, 10])]},
             "left_arm": {"rotation": [(0, [-30, 0, -10]), (4.0, [-30, 0, -10])]}}
    intro = {"body": {"rotation": [(0, [15, 0, 0]), (1.0, [0, 0, 0]), (1.6, [-14, 0, 0]), (3.0, [0, 0, 0])],
                      "position": [(0, [0, -4, 0]), (1.0, [0, 0, 0]), (3.0, [0, 0, 0])]},
             "head": {"rotation": [(0, [25, 0, 0]), (1.4, [-40, 0, 0]), (2.4, [-40, 0, 0]), (3.0, [0, 0, 0])]},
             "right_arm": {"rotation": [(0, [-50, 0, 10]), (1.4, [-160, 0, 20]), (2.4, [-160, 0, 20]), (3.0, [-10, 0, 4])]},
             "left_arm": {"rotation": [(0, [-30, 0, -10]), (1.4, [-40, 0, -70]), (2.4, [-40, 0, -70]), (3.0, [-20, 0, -6])]},
             "right_leg": {"rotation": [(0, [-70, 0, 0]), (1.0, [0, 0, 0])]}}
    death = {"body": {"rotation": [(0, [0, 0, 0]), (0.8, [-20, 0, 0]), (2.0, [-80, 0, 10])],
                      "position": [(0, [0, 0, 0]), (0.8, [0, -4, 0]), (2.0, [0, -12, 6])]},
             "right_leg": {"rotation": [(0, [0, 0, 0]), (2.0, [70, 0, 0])]},
             "left_leg": {"rotation": [(0, [0, 0, 0]), (2.0, [80, 0, 0])]},
             "head": {"rotation": [(0, [0, 0, 0]), (2.0, [-30, 0, 0])]},
             "right_arm": {"rotation": [(0, [-10, 0, 0]), (2.0, [-150, 0, 40])]},
             "left_arm": {"rotation": [(0, [-20, 0, 0]), (2.0, [-150, 0, -40])]}}
    cleave = {"right_arm": {"rotation": [(0, [-10, 0, 4]), (0.5, [-170, 0, 10]), (0.65, [10, 0, 4]), (0.9, [-10, 0, 4])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.5, [-10, 15, 0]), (0.65, [12, -10, 0]), (0.9, [0, 0, 0])]}}
    bash = {"left_arm": {"rotation": [(0, [-20, 0, -6]), (0.6, [-80, -30, 0]), (0.8, [-90, 0, 0]), (1.0, [-20, 0, -6])],
                         "position": [(0, [0, 0, 0]), (0.6, [0, 0, 3]), (0.8, [0, 0, -5]), (1.0, [0, 0, 0])]},
            "body": {"rotation": [(0, [0, 0, 0]), (0.6, [0, -25, 0]), (0.8, [10, 10, 0]), (1.0, [0, 0, 0])]}}
    spin = {"body": {"rotation": [(0, [0, 0, 0]), (0.5, [0, 30, 0]), (0.75, [0, -180, 0]), (1.0, [0, -360, 0]), (1.1, [0, -360, 0])]},
            "right_arm": {"rotation": [(0, [-10, 0, 4]), (0.5, [-90, 0, 80]), (1.0, [-90, 0, 80]), (1.1, [-10, 0, 4])]}}
    summon = {"head": {"rotation": [(0, [0, 0, 0]), (0.5, [-45, 0, 0]), (1.0, [-45, 0, 0]), (1.2, [0, 0, 0])]},
              "right_arm": {"rotation": [(0, [-10, 0, 4]), (0.5, [-30, 0, 70]), (1.2, [-10, 0, 4])]},
              "left_arm": {"rotation": [(0, [-20, 0, -6]), (0.5, [-30, 0, -70]), (1.2, [-20, 0, -6])]}}
    draw = {"right_arm": {"rotation": [(0, [-10, 0, 4]), (0.5, [-40, 0, 60]), (1.0, [-170, 0, 10]), (1.5, [-10, 0, 4])]},
            "left_arm": {"rotation": [(0, [-20, 0, -6]), (0.5, [-60, 0, -80]), (1.5, [-20, 0, -6])]},
            "head": {"rotation": [(0, [0, 0, 0]), (1.0, [-30, 0, 0]), (1.5, [0, 0, 0])]},
            "body": {"rotation": [(0, [0, 0, 0]), (1.0, [-12, 0, 0]), (1.5, [0, 0, 0])]}}
    slash3 = {"right_arm": {"rotation": [(0, [-10, 0, 4]), (0.3, [-100, -60, 0]), (0.45, [-60, 60, 0]),
                                         (0.75, [-100, 60, 0]), (0.9, [-60, -60, 0]), (1.15, [-170, 0, 0]),
                                         (1.3, [10, 0, 0]), (1.5, [-10, 0, 4])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.3, [0, 30, 0]), (0.45, [0, -30, 0]), (0.75, [0, -30, 0]),
                                    (0.9, [0, 30, 0]), (1.15, [-10, 0, 0]), (1.3, [12, 0, 0]), (1.5, [0, 0, 0])]}}
    lunge = {"right_arm": {"rotation": [(0, [-10, 0, 4]), (0.6, [-80, 0, 0]), (0.75, [-90, 0, 0]), (1.0, [-10, 0, 4])],
                           "position": [(0, [0, 0, 0]), (0.6, [0, 0, 5]), (0.75, [0, 0, -7]), (1.0, [0, 0, 0])]},
             "body": {"rotation": [(0, [0, 0, 0]), (0.6, [0, 25, 0]), (0.75, [15, -10, 0]), (1.0, [0, 0, 0])]},
             "right_leg": {"rotation": [(0, [0, 0, 0]), (0.75, [-30, 0, 0]), (1.0, [0, 0, 0])]},
             "left_leg": {"rotation": [(0, [0, 0, 0]), (0.75, [30, 0, 0]), (1.0, [0, 0, 0])]}}
    return {"idle": anim(3.0, True, idle), "walk": anim(1.0, True, walk), "sleep": anim(4.0, True, sleep),
            "intro": anim(3.0, False, intro), "death": anim(2.0, "hold", death), "cleave": anim(0.9, False, cleave),
            "bash": anim(1.0, False, bash), "spin": anim(1.1, False, spin), "summon": anim(1.2, False, summon),
            "draw": anim(1.5, False, draw), "slash3": anim(1.5, False, slash3), "lunge": anim(1.0, False, lunge)}


def build_all(assets):
    m = kobold_lord()
    m.save(assets)
    save_anims(assets, m.ident, kobold_lord_anims())
    print("  modelo", m.ident)
