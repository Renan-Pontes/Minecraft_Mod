"""
Gerador de assets/dados do modulo world_events (v1.0 — eventos do mundo):
texturas (blocos e itens do meteoro), modelos de bloco/item, modelos GeckoLib + animacoes dos invasores
(reaproveita a montagem de modelo de tools/gen/depths_geo.py: box UV automatico, textura por face, glowmask),
loot tables, receitas, tags (merge) e traducoes (src/main/lang/world_events).

Rodar a partir da raiz do projeto:  py -I tools/gen/world_events.py
"""
import json
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402
from depths_geo import Model, Cube, anim, save_anims  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/world_events")
LANG = os.path.join(ROOT, "src/main/lang/world_events")
NS = "isekai_mahou"
P = "world_events_"


def H(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  TEXTURAS
# =====================================================================================
ROCK = H("#2A2630")
ROCK_L = H("#3E3846")
ROCK_D = H("#17141C")
EMBER = H("#C8501E")
STAR = H("#4FE0D0")
STAR_L = H("#B8FFF4")
STAR_D = H("#1E8C8A")
STAR_DD = H("#0E4A52")
ALIEN = H("#5A3A8A")
ALIEN_L = H("#8A62C0")
ALIEN_D = H("#34205A")
ACID = H("#7CFF6A")


def save_item(c, name):
    c.save(os.path.join(ASSETS, f"textures/item/{P}{name}.png"))


def save_block(c, name):
    c.save(os.path.join(ASSETS, f"textures/block/{P}{name}.png"))


def rock_bg(seed):
    """Rocha de meteoro: escura, cheia de crateras pequenas, com brasas nas rachaduras."""
    rnd = random.Random(seed)
    c = Canvas(16, 16, ROCK)
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c.set(x, y, ROCK_D)
        c.set((x + 1) % 16, y, ROCK_D)
        c.set(x, (y + 1) % 16, ROCK_L)
    for _ in range(10):
        c.set(rnd.randrange(16), rnd.randrange(16), ROCK_L)
    c.noise(0.10, seed)
    return c


def tex_blocks():
    c = rock_bg(41)
    for (x, y) in ((3, 4), (4, 5), (5, 5), (11, 10), (12, 11), (8, 13)):
        c.set(x, y, EMBER)
    c.set(4, 4, darken(EMBER, 0.4)); c.set(12, 10, darken(EMBER, 0.4))
    save_block(c, "meteorite")

    c = rock_bg(57)
    for (x, y) in ((3, 3), (10, 4), (6, 9), (12, 11), (3, 12)):
        c.set(x, y, STAR_L)
        for (dx, dy) in ((1, 0), (0, 1), (-1, 0)):
            c.set(x + dx, y + dy, STAR)
        c.set(x + 1, y + 1, STAR_D)
        c.set(x, y - 1, STAR_D)
    save_block(c, "star_ore")

    c = Canvas(16, 16, STAR)
    c.outline_rect(0, 0, 15, 15, STAR_DD)
    c.outline_rect(1, 1, 14, 14, STAR_D)
    for i in range(2, 14):
        c.set(i, 2, STAR_L)
        c.set(2, i, STAR_L)
        c.set(i, 13, STAR_D)
        c.set(13, i, STAR_D)
    for i in range(4, 12):
        c.set(i, 15 - i, lighten(STAR, 0.35))
        c.set(i, 14 - i, lighten(STAR, 0.2))
    c.noise(0.05, 3)
    save_block(c, "star_metal_block")

    c = Canvas(16, 16, H("#DFFFFA"))
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            c.set(x, y, lighten(STAR, max(0.0, 0.85 - d * 0.07)))
    c.outline_rect(0, 0, 15, 15, STAR_DD)
    for i in range(16):
        c.set(i, 7, STAR_D) if i in (0, 1, 14, 15) else None
        c.set(7, i, STAR_D) if i in (0, 1, 14, 15) else None
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, STAR_D)
    for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        c.set(x, y, H("#FFFFFF"))
    save_block(c, "star_lamp")


def tex_items():
    c = Canvas(16, 16)
    rows = ["................",
            "................",
            "......xxxx......",
            ".....xhllmx.....",
            "....xhlmmmdx....",
            "...xhlmmmmddx...",
            "...xlmmmmmddx...",
            "..xlmmmrmmmdx...",
            "..xmmmrrmmddx...",
            "..xmmmmmmdddx...",
            "...xmmmmdddx....",
            "...xdmmdddx.....",
            "....xddddx......",
            ".....xxxx.......",
            "................",
            "................"]
    c.from_rows(rows, {"x": STAR_DD, "h": STAR_L, "l": lighten(STAR, 0.3), "m": STAR, "d": STAR_D, "r": ROCK})
    c.set(12, 3, STAR_L); c.set(2, 12, STAR_L)
    save_item(c, "star_metal")

    c = Canvas(16, 16)
    c.circle(8, 8, 6, ALIEN)
    c.shade_sphere(8, 8, 6)
    c.circle(8, 8, 2.6, ACID)
    c.set(7, 7, H("#E8FFE0")); c.set(8, 7, lighten(ACID, 0.4))
    for (x, y) in ((4, 5), (11, 4), (12, 10), (5, 12)):
        c.set(x, y, ALIEN_D)
        c.set(x + (1 if x < 8 else -1), y + (1 if y < 8 else -1), darken(ACID, 0.3))
    c.outline(H("#1A0E30"))
    save_item(c, "alien_core")

    c = Canvas(16, 16)
    brass, brass_l, brass_d = H("#C8A03A"), H("#F0D070"), H("#7A5A1A")
    c.circle(9.5, 6.5, 5.5, brass)
    c.circle(9.5, 6.5, 3.8, STAR)
    for (x, y) in ((8, 4), (9, 4), (8, 5)):
        c.set(x, y, STAR_L)
    c.set(11, 8, STAR_D); c.set(10, 9, STAR_D); c.set(11, 7, STAR_D)
    c.set(9, 6, H("#1A0E30")); c.set(10, 6, ALIEN)
    for (x, y) in ((6, 2), (7, 1), (5, 4)):
        c.set(x, y, brass_l)
    for i in range(5):
        c.set(5 - i, 10 + i, brass_d if i % 2 else brass)
        c.set(6 - i, 10 + i, brass)
    c.set(1, 15, brass_d)
    c.outline(H("#2A1A08"))
    save_item(c, "omen_lens")


def models():
    bs, bm, im = (os.path.join(ASSETS, p) for p in ("blockstates", "models/block", "models/item"))
    for b in ("meteorite", "star_ore", "star_metal_block", "star_lamp"):
        write_json(os.path.join(bs, f"{P}{b}.json"), {"variants": {"": {"model": f"{NS}:block/{P}{b}"}}})
        write_json(os.path.join(bm, f"{P}{b}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{P}{b}"}})
        write_json(os.path.join(im, f"{P}{b}.json"), {"parent": f"{NS}:block/{P}{b}"})
    for i in ("star_metal", "alien_core", "omen_lens"):
        write_json(os.path.join(im, f"{P}{i}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{P}{i}"}})
    for e in ("alien_grunt", "alien_spitter", "alien_larva", "alien_overseer"):
        write_json(os.path.join(im, f"{P}{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  MODELOS GECKOLIB
# =====================================================================================
SKIN = "#5A3A8A"
SKIN_L = "#7A56B0"
SKIN_D = "#3A2462"
PLATE = "#2E2440"
BELLY = "#8E78B8"
TEAL = "#2E6A7A"
TEAL_L = "#4A9AA8"
GLOW = "#7CFF6A"
GLOW_P = "#C89CFF"
CLAW = "#D8D0E8"


def deco_alien_head(f, c):
    """Cabeca alongada: dois olhos verdes rasgados (brilham) e boca fina."""
    fr = f["front"]
    fr.rect(0, 2, 2, 3, "#101018")
    fr.rect(fr.w - 3, 2, fr.w - 1, 3, "#101018")
    fr.rect(0, 2, 1, 2, GLOW, True)
    fr.rect(fr.w - 2, 2, fr.w - 1, 2, GLOW, True)
    fr.rect(2, fr.h - 2, fr.w - 3, fr.h - 2, SKIN_D)
    for name in ("left", "right", "top"):
        fc = f[name]
        for x in range(1, fc.w, 3):
            fc.rect(x, 0, x, fc.h - 1, SKIN_D)


def deco_chest(f, c):
    fr = f["front"]
    fr.rect(2, 1, fr.w - 3, fr.h - 2, BELLY)
    for y in range(2, fr.h - 2, 2):
        fr.rect(2, y, fr.w - 3, y, SKIN_L)
    fr.set(fr.w // 2, 2, GLOW, True)
    fr.set(fr.w // 2 - 1, 2, GLOW, True)
    for name in ("back", "left", "right"):
        fc = f[name]
        for y in range(1, fc.h, 3):
            fc.rect(0, y, fc.w - 1, y, SKIN_D)


def deco_limb(f, c):
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        fc.rect(0, fc.h // 2, fc.w - 1, fc.h // 2, SKIN_D)
        fc.rect(0, fc.h - 1, fc.w - 1, fc.h - 1, SKIN_D)


def deco_plate(f, c):
    for name in ("front", "back", "left", "right", "top"):
        fc = f[name]
        fc.rect(0, 0, fc.w - 1, 0, lighten(H(PLATE), 0.25))
        for x in range(2, fc.w, 4):
            fc.set(x, fc.h // 2, GLOW_P, True)


def grunt():
    m = Model(P + "alien_grunt", 64, 64, bounds=(2, 3, 1))
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-2, 10, 0], [Cube([-3.5, 0, -1.5], [3, 10, 3], SKIN_D, deco_limb)])
    m.bone("left_leg", "root", [2, 10, 0], [Cube([0.5, 0, -1.5], [3, 10, 3], SKIN_D, deco_limb)])
    m.bone("body", "root", [0, 10, 0], [
        Cube([-4, 10, -2.5], [8, 11, 5], SKIN, deco_chest),
        Cube([-5, 18, -3], [10, 3, 6], PLATE, deco_plate)])
    m.bone("head", "body", [0, 21, 0], [
        Cube([-3.5, 21, -5], [7, 7, 9], SKIN_L, deco_alien_head),
        Cube([-1, 28, -3], [2, 2, 7], PLATE)])
    m.bone("right_arm", "body", [-5, 20, 0], [
        Cube([-7, 8, -1.5], [2, 13, 3], SKIN, deco_limb),
        Cube([-7.5, 5, -2], [3, 3, 4], CLAW)])
    m.bone("left_arm", "body", [5, 20, 0], [
        Cube([5, 8, -1.5], [2, 13, 3], SKIN, deco_limb),
        Cube([4.5, 5, -2], [3, 3, 4], CLAW)])
    return m


def grunt_anims():
    idle = {"body": {"rotation": [(0, [8, 0, 0]), (1.5, [10, 0, 0]), (3.0, [8, 0, 0])]},
            "head": {"rotation": [(0, [-6, 0, 0]), (1.5, [-2, 4, 0]), (3.0, [-6, 0, 0])]},
            "right_arm": {"rotation": [(0, [-6, 0, 4]), (1.5, [-10, 0, 6]), (3.0, [-6, 0, 4])]},
            "left_arm": {"rotation": [(0, [-6, 0, -4]), (1.5, [-10, 0, -6]), (3.0, [-6, 0, -4])]}}
    walk = {"body": {"rotation": [(0, [14, 0, 0]), (0.8, [14, 0, 0])]},
            "right_leg": {"rotation": [(0, [32, 0, 0]), (0.4, [-32, 0, 0]), (0.8, [32, 0, 0])]},
            "left_leg": {"rotation": [(0, [-32, 0, 0]), (0.4, [32, 0, 0]), (0.8, [-32, 0, 0])]},
            "right_arm": {"rotation": [(0, [-40, 0, 4]), (0.4, [10, 0, 4]), (0.8, [-40, 0, 4])]},
            "left_arm": {"rotation": [(0, [10, 0, -4]), (0.4, [-40, 0, -4]), (0.8, [10, 0, -4])]}}
    attack = {"right_arm": {"rotation": [(0, [-6, 0, 4]), (0.15, [-150, 0, -10]), (0.3, [-30, 0, 20]), (0.5, [-6, 0, 4])]},
              "left_arm": {"rotation": [(0, [-6, 0, -4]), (0.2, [-130, 0, 10]), (0.35, [-30, 0, -20]), (0.5, [-6, 0, -4])]},
              "body": {"rotation": [(0, [8, 0, 0]), (0.15, [-4, 0, 0]), (0.3, [22, 0, 0]), (0.5, [8, 0, 0])]}}
    return {"idle": anim(3.0, True, idle), "walk": anim(0.8, True, walk), "attack": anim(0.5, False, attack)}


def deco_sac(f, c):
    """Bolsa de plasma: veias claras subindo."""
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        for x in range(1, fc.w, 2):
            fc.rect(x, 0, x, fc.h - 1, "#D8FFC8", True)


def deco_spitter_body(f, c):
    fr = f["front"]
    fr.rect(1, 1, 2, 2, "#101018")
    fr.rect(fr.w - 3, 1, fr.w - 2, 2, "#101018")
    fr.set(1, 1, GLOW, True)
    fr.set(fr.w - 2, 1, GLOW, True)
    for name in ("left", "right", "back", "top"):
        fc = f[name]
        for y in range(1, fc.h, 3):
            fc.rect(0, y, fc.w - 1, y, darken(H(TEAL), 0.3))


def deco_snout(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, fr.h - 1, "#101018")
    fr.set(fr.w // 2, fr.h // 2, GLOW, True)


def spitter():
    m = Model(P + "alien_spitter", 64, 64, bounds=(3, 3, 1))
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 12, 0], [
        Cube([-4, 10, -4], [8, 8, 8], TEAL, deco_spitter_body),
        Cube([-1.5, 12, -9], [3, 3, 5], TEAL_L, deco_snout)])
    m.bone("sac", "body", [0, 18, 1], [Cube([-3, 18, -2], [6, 5, 6], GLOW, deco_sac, glow=True)])
    m.bone("leg_front", "root", [0, 11, -3], [Cube([-1, 0, -6], [2, 11, 2], SKIN_D, deco_limb)])
    m.bone("leg_left", "root", [3, 11, 3], [Cube([3.5, 0, 3.5], [2, 11, 2], SKIN_D, deco_limb)])
    m.bone("leg_right", "root", [-3, 11, 3], [Cube([-5.5, 0, 3.5], [2, 11, 2], SKIN_D, deco_limb)])
    return m


def spitter_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.0, [0, 0.6, 0]), (2.0, [0, 0, 0])]},
            "sac": {"scale": [(0, [1, 1, 1]), (1.0, [1.12, 1.15, 1.12]), (2.0, [1, 1, 1])]}}
    walk = {"leg_front": {"rotation": [(0, [25, 0, 0]), (0.35, [-25, 0, 0]), (0.7, [25, 0, 0])]},
            "leg_left": {"rotation": [(0, [-20, 0, -8]), (0.35, [20, 0, 8]), (0.7, [-20, 0, -8])]},
            "leg_right": {"rotation": [(0, [20, 0, -8]), (0.35, [-20, 0, 8]), (0.7, [20, 0, -8])]},
            "body": {"position": [(0, [0, 0, 0]), (0.35, [0, 0.8, 0]), (0.7, [0, 0, 0])]}}
    attack = {"body": {"rotation": [(0, [0, 0, 0]), (0.15, [-15, 0, 0]), (0.3, [25, 0, 0]), (0.5, [0, 0, 0])]}}
    shoot = {"sac": {"scale": [(0, [1, 1, 1]), (0.85, [1.5, 1.6, 1.5]), (0.95, [0.8, 0.7, 0.8]), (1.2, [1, 1, 1])]},
             "body": {"rotation": [(0, [0, 0, 0]), (0.85, [-10, 0, 0]), (0.95, [12, 0, 0]), (1.2, [0, 0, 0])],
                      "position": [(0, [0, 0, 0]), (0.85, [0, -1, 0]), (0.95, [0, 0, 2]), (1.2, [0, 0, 0])]}}
    return {"idle": anim(2.0, True, idle), "walk": anim(0.7, True, walk), "attack": anim(0.5, False, attack),
            "shoot": anim(1.2, False, shoot)}


def deco_larva_head(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, fr.h - 1, "#2A1840")
    fr.set(1, 1, GLOW, True)
    fr.set(fr.w - 2, 1, GLOW, True)
    for x in range(0, fr.w, 2):
        fr.set(x, fr.h - 1, CLAW)


def deco_segment(f, c):
    for name in ("left", "right", "top"):
        fc = f[name]
        for x in range(1, fc.w, 2):
            fc.rect(x, 0, x, fc.h - 1, darken(c.color, 0.25))
    tp = f["top"]
    tp.set(tp.w // 2, tp.h // 2, GLOW, True)


def larva():
    m = Model(P + "alien_larva", 32, 32, bounds=(2, 1, 0))
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 2, 0], [Cube([-3, 0, -3], [6, 4, 6], "#6A4A9A", deco_segment)])
    m.bone("head", "body", [0, 2, -3], [
        Cube([-2.5, 0.5, -7], [5, 4, 4], "#8A62C0", deco_larva_head),
        Cube([-2.5, 0.5, -9], [1, 1, 2], CLAW),
        Cube([1.5, 0.5, -9], [1, 1, 2], CLAW)])
    m.bone("tail", "body", [0, 2, 3], [
        Cube([-2, 0, 3], [4, 3, 4], "#5A3A8A", deco_segment),
        Cube([-1, 0, 7], [2, 2, 3], "#4A2E78")])
    return m


def larva_anims():
    idle = {"head": {"rotation": [(0, [0, -6, 0]), (0.8, [0, 6, 0]), (1.6, [0, -6, 0])]},
            "tail": {"rotation": [(0, [0, 8, 0]), (0.8, [0, -8, 0]), (1.6, [0, 8, 0])]}}
    walk = {"head": {"rotation": [(0, [0, -14, 0]), (0.2, [0, 14, 0]), (0.4, [0, -14, 0])]},
            "tail": {"rotation": [(0, [0, 20, 0]), (0.2, [0, -20, 0]), (0.4, [0, 20, 0])]},
            "body": {"position": [(0, [0, 0, 0]), (0.2, [0, 0.5, 0]), (0.4, [0, 0, 0])]}}
    attack = {"head": {"rotation": [(0, [0, 0, 0]), (0.1, [-30, 0, 0]), (0.2, [20, 0, 0]), (0.35, [0, 0, 0])],
                       "position": [(0, [0, 0, 0]), (0.2, [0, 0, -2]), (0.35, [0, 0, 0])]}}
    return {"idle": anim(1.6, True, idle), "walk": anim(0.4, True, walk), "attack": anim(0.35, False, attack)}


def deco_dome(f, c):
    """Cranio-domo do Arauto: sulcos de cerebro e veias roxas que brilham."""
    rnd = random.Random("dome")
    for name in ("front", "back", "left", "right", "top"):
        fc = f[name]
        for y in range(1, fc.h, 3):
            for x in range(fc.w):
                if (x + y) % 5 != 0:
                    fc.set(x, y, darken(c.color, 0.3))
        for _ in range(max(2, fc.w * fc.h // 30)):
            fc.set(rnd.randrange(fc.w), rnd.randrange(fc.h), GLOW_P, True)
    fr = f["front"]
    cx = fr.w // 2
    fr.rect(cx - 3, 3, cx + 2, 8, "#0A0612")


def deco_eye(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, fr.h - 1, GLOW, True)
    fr.rect(1, 1, fr.w - 2, fr.h - 2, "#E8FFE0", True)
    fr.rect(fr.w // 2 - 1, 0, fr.w // 2, fr.h - 1, "#0A0612", True)


def deco_robe(f, c):
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        for x in range(0, fc.w, 3):
            fc.rect(x, 0, x, fc.h - 1, darken(c.color, 0.3))
        fc.rect(0, fc.h - 1, fc.w - 1, fc.h - 1, GLOW_P, True)
    fr = f["front"]
    fr.rect(fr.w // 2 - 1, 2, fr.w // 2, 4, GLOW_P, True)


def deco_tentacle(f, c):
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        for y in range(2, fc.h, 4):
            fc.rect(0, y, fc.w - 1, y, SKIN_D)
        fc.rect(0, fc.h - 2, fc.w - 1, fc.h - 1, GLOW_P, True)


def overseer():
    m = Model(P + "alien_overseer", 128, 128, bounds=(4, 5, 2))
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 16, 0], [
        Cube([-5, 16, -3], [10, 14, 6], "#2A1E44", deco_robe),
        Cube([-7, 28, -4], [14, 3, 8], PLATE, deco_plate)])
    m.bone("head", "body", [0, 31, 0], [
        Cube([-7, 31, -7], [14, 12, 14], "#6A4AA0", deco_dome),
        Cube([-2, 35, -8], [4, 4, 1], GLOW, deco_eye, glow=True),
        Cube([-1, 43, -1], [2, 4, 2], GLOW_P, glow=True),
        Cube([-5, 43, -1], [2, 2, 2], GLOW_P, glow=True),
        Cube([3, 43, -1], [2, 2, 2], GLOW_P, glow=True)])
    m.bone("right_arm", "body", [-7, 28, 0], [
        Cube([-10, 14, -1.5], [3, 14, 3], SKIN, deco_limb),
        Cube([-9.5, 7, -1], [2, 7, 2], CLAW)])
    m.bone("left_arm", "body", [7, 28, 0], [
        Cube([7, 14, -1.5], [3, 14, 3], SKIN, deco_limb),
        Cube([7.5, 7, -1], [2, 7, 2], CLAW)])
    m.bone("tentacle_fl", "body", [-3, 16, -2], [Cube([-4, 2, -3], [2, 14, 2], SKIN, deco_tentacle)])
    m.bone("tentacle_fr", "body", [3, 16, -2], [Cube([2, 2, -3], [2, 14, 2], SKIN, deco_tentacle)])
    m.bone("tentacle_bl", "body", [-3, 16, 2], [Cube([-4, 2, 1], [2, 14, 2], SKIN_L, deco_tentacle)])
    m.bone("tentacle_br", "body", [3, 16, 2], [Cube([2, 2, 1], [2, 14, 2], SKIN_L, deco_tentacle)])
    return m


def sway(a, t):
    return {"rotation": [(0, [a, 0, 0]), (t / 2, [-a, 0, a / 2]), (t, [a, 0, 0])]}


def overseer_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (2.0, [0, 1.5, 0]), (4.0, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (2.0, [3, 0, 2]), (4.0, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [0, 0, 6]), (2.0, [-6, 0, 10]), (4.0, [0, 0, 6])]},
            "left_arm": {"rotation": [(0, [0, 0, -6]), (2.0, [-6, 0, -10]), (4.0, [0, 0, -6])]},
            "tentacle_fl": sway(8, 4.0), "tentacle_fr": sway(-8, 4.0),
            "tentacle_bl": sway(-10, 4.0), "tentacle_br": sway(10, 4.0)}
    walk = {"body": {"rotation": [(0, [10, 0, 0]), (1.0, [10, 0, 0])],
                     "position": [(0, [0, 0, 0]), (0.5, [0, 1, 0]), (1.0, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [-15, 0, 8]), (0.5, [5, 0, 8]), (1.0, [-15, 0, 8])]},
            "left_arm": {"rotation": [(0, [5, 0, -8]), (0.5, [-15, 0, -8]), (1.0, [5, 0, -8])]},
            "tentacle_fl": sway(30, 1.0), "tentacle_fr": sway(-30, 1.0),
            "tentacle_bl": sway(-30, 1.0), "tentacle_br": sway(30, 1.0)}
    attack = {"right_arm": {"rotation": [(0, [0, 0, 6]), (0.2, [-150, 0, -10]), (0.4, [-20, 0, 25]), (0.6, [0, 0, 6])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.2, [-6, 12, 0]), (0.4, [14, -12, 0]), (0.6, [0, 0, 0])]}}
    pulse = {"right_arm": {"rotation": [(0, [0, 0, 6]), (1.0, [-170, 0, 20]), (1.3, [-20, 0, 60]), (1.6, [0, 0, 6])]},
             "left_arm": {"rotation": [(0, [0, 0, -6]), (1.0, [-170, 0, -20]), (1.3, [-20, 0, -60]), (1.6, [0, 0, -6])]},
             "body": {"position": [(0, [0, 0, 0]), (1.0, [0, 5, 0]), (1.3, [0, -3, 0]), (1.6, [0, 0, 0])]},
             "head": {"rotation": [(0, [0, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [15, 0, 0]), (1.6, [0, 0, 0])]}}
    beam = {"head": {"rotation": [(0, [0, 0, 0]), (0.9, [-18, 0, 0]), (1.1, [10, 0, 0]), (1.4, [0, 0, 0])],
                     "scale": [(0, [1, 1, 1]), (0.9, [1.1, 1.1, 1.1]), (1.1, [0.95, 0.95, 0.95]), (1.4, [1, 1, 1])]},
            "right_arm": {"rotation": [(0, [0, 0, 6]), (0.9, [-80, 30, 0]), (1.4, [0, 0, 6])]},
            "left_arm": {"rotation": [(0, [0, 0, -6]), (0.9, [-80, -30, 0]), (1.4, [0, 0, -6])]}}
    summon = {"right_arm": {"rotation": [(0, [0, 0, 6]), (0.5, [-170, 0, 30]), (0.9, [-170, 0, 30]), (1.2, [0, 0, 6])]},
              "left_arm": {"rotation": [(0, [0, 0, -6]), (0.5, [-170, 0, -30]), (0.9, [-170, 0, -30]), (1.2, [0, 0, -6])]},
              "head": {"rotation": [(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (0.9, [-25, 0, 0]), (1.2, [0, 0, 0])]}}
    return {"idle": anim(4.0, True, idle), "walk": anim(1.0, True, walk), "attack": anim(0.6, False, attack),
            "pulse": anim(1.6, False, pulse), "beam": anim(1.4, False, beam), "summon": anim(1.2, False, summon)}


def geo():
    for model, anims in ((grunt(), grunt_anims()), (spitter(), spitter_anims()), (larva(), larva_anims()),
                         (overseer(), overseer_anims())):
        model.save(ASSETS)
        save_anims(ASSETS, model.ident, anims)
        print("  modelo", model.ident)


# =====================================================================================
#  LOOT, RECEITAS, TAGS
# =====================================================================================
def ie(name, lo=1, hi=1, weight=1, conditions=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    if hi > 1 or lo != 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if conditions:
        e["conditions"] = conditions
    return e


def pool(entries, conditions=None):
    p = {"rolls": 1, "bonus_rolls": 0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def loot_tables():
    lt = os.path.join(DATA, "loot_table")
    survives = [{"condition": "minecraft:survives_explosion"}]
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    for b in ("meteorite", "star_metal_block", "star_lamp"):
        write_json(os.path.join(lt, f"blocks/{P}{b}.json"), {"type": "minecraft:block", "pools": [
            pool([ie(f"{NS}:{P}{b}")], survives)], "random_sequence": f"{NS}:blocks/{P}{b}"})
    write_json(os.path.join(lt, f"blocks/{P}star_ore.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{P}star_ore", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{NS}:{P}star_metal", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{NS}:blocks/{P}star_ore"})

    killed = [{"condition": "minecraft:killed_by_player"}]

    def chance(c):
        return killed + [{"condition": "minecraft:random_chance", "chance": c}]

    def entity(name, pools):
        write_json(os.path.join(lt, f"entities/{P}{name}.json"),
                   {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{P}{name}"})

    entity("alien_grunt", [
        pool([ie("phantom_membrane", 0, 1)]),
        pool([ie(f"{NS}:{P}star_metal")], chance(0.08)),
        pool([ie(f"{NS}:essence_arcane")], chance(0.10))])
    entity("alien_spitter", [
        pool([ie("glowstone_dust", 0, 2)]),
        pool([ie(f"{NS}:{P}star_metal")], chance(0.10)),
        pool([ie(f"{NS}:mana_crystal")], chance(0.15))])
    entity("alien_larva", [
        pool([ie("slime_ball", 0, 1)])])
    entity("alien_overseer", [
        pool([ie(f"{NS}:{P}alien_core")], killed),
        pool([ie(f"{NS}:{P}star_metal", 2, 4)]),
        pool([ie(f"{NS}:mana_crystal", 2, 4)], killed),
        pool([ie(f"{NS}:essence_arcane", 1, 2)], killed)])


def recipes():
    rc = os.path.join(DATA, "recipe")
    metal, block = f"{NS}:{P}star_metal", f"{NS}:{P}star_metal_block"
    write_json(os.path.join(rc, f"{P}star_metal_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
        "key": {"#": {"item": metal}}, "result": {"id": block, "count": 1}})
    write_json(os.path.join(rc, f"{P}star_metal_from_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": block}],
        "result": {"id": metal, "count": 9}})
    write_json(os.path.join(rc, f"{P}star_lamp.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": [" G ", "GSG", " G "],
        "key": {"G": {"item": "minecraft:glass"}, "S": {"item": metal}},
        "result": {"id": f"{NS}:{P}star_lamp", "count": 2}})
    write_json(os.path.join(rc, f"{P}omen_lens.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" S ", "SCS", " Y "],
        "key": {"S": {"item": metal}, "C": {"item": f"{NS}:{P}alien_core"}, "Y": {"item": "minecraft:spyglass"}},
        "result": {"id": f"{NS}:{P}omen_lens", "count": 1}})


def tags():
    bt = os.path.join(MERGE, "data/minecraft/tags/block")
    write_json(os.path.join(bt, "mineable/pickaxe.json"),
               {"values": [f"{NS}:{P}{b}" for b in ("meteorite", "star_ore", "star_metal_block")]})
    write_json(os.path.join(bt, "needs_stone_tool.json"), {"values": [f"{NS}:{P}meteorite"]})
    write_json(os.path.join(bt, "needs_iron_tool.json"), {"values": [f"{NS}:{P}star_ore", f"{NS}:{P}star_metal_block"]})


# =====================================================================================
#  TRADUCOES
# =====================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def lang():
    for bid, pt, en in (("meteorite", "Meteorito", "Meteorite"),
                        ("star_ore", "Minério Estelar", "Star Ore"),
                        ("star_metal_block", "Bloco de Metal Estelar", "Block of Star Metal"),
                        ("star_lamp", "Lâmpada Estelar", "Star Lamp")):
        t(f"block.{NS}.{P}{bid}", pt, en)
    t(f"item.{NS}.{P}star_metal", "Metal Estelar", "Star Metal")
    t(f"item.{NS}.{P}alien_core", "Núcleo Alienígena", "Alien Core")
    t(f"item.{NS}.{P}omen_lens", "Lente de Presságios", "Omen Lens")
    t(f"item.{NS}.{P}omen_lens.desc", "Clique direito: mostra o evento do mundo e onde ele está.",
      "Right-click: shows the current world event and where it is.")
    for eid, pt, en in (("alien_grunt", "Batedor Estelar", "Star Scout"),
                        ("alien_spitter", "Atirador Estelar", "Star Spitter"),
                        ("alien_larva", "Larva Estelar", "Star Larva"),
                        ("alien_overseer", "Arauto Estelar", "Star Herald")):
        t(f"entity.{NS}.{P}{eid}", pt, en)
        t(f"item.{NS}.{P}{eid}_spawn_egg", f"Ovo de {pt}", f"{en} Spawn Egg")
    t(f"bestiary.{NS}.{P}alien_grunt.desc",
      "Soldado raso dos invasores que vêm com os meteoros. Corre e bate com as garras; a gosma dele deixa lento.",
      "Foot soldier of the invaders that come with meteors. Charges and claws; its slime slows you.")
    t(f"bestiary.{NS}.{P}alien_spitter.desc",
      "Fica de longe e dispara plasma em linha reta. A faixa verde no chão avisa: dê um passo para o lado.",
      "Stays back and fires plasma in a straight line. The green strip on the ground warns you: step aside.")
    t(f"bestiary.{NS}.{P}alien_larva.desc",
      "Pequena, rápida e sempre em bando. Pula na cara. Tem medo de fogo.",
      "Small, fast and always in a pack. Leaps at your face. Afraid of fire.")
    t(f"bestiary.{NS}.{P}alien_overseer.desc",
      "Comanda a invasão do meteoro. Pulso em volta dele, raio em linha reta e larvas quando está ferido — tudo "
      "avisado no chão. A luz o fere; a sombra quase nada. Deixa um Núcleo Alienígena.",
      "Leads the meteor invasion. A pulse around it, a straight beam, and larvae when wounded - all telegraphed on the "
      "ground. Light hurts it; shadow barely does. Drops an Alien Core.")

    events = {
        "meteor": ("Queda de Meteoro", "Meteor Fall", "Um meteoro risca o céu!", "A meteor streaks across the sky!",
                   "A cratera do meteoro esfriou.", "The meteor crater has gone cold.",
                   "Um meteoro caiu por perto: siga a fumaça. Há Minério Estelar na cratera — e visitantes.",
                   "A meteor fell nearby: follow the smoke. There is Star Ore in the crater - and visitors."),
        "blood_moon": ("Lua de Sangue", "Blood Moon", "A Lua de Sangue se ergue...", "The Blood Moon rises...",
                       "A Lua de Sangue se pôs.", "The Blood Moon has set.",
                       "Lua de Sangue: mais monstros até amanhecer, com um pouco mais de XP e loot. As cidades continuam seguras.",
                       "Blood Moon: more monsters until dawn, with a bit more XP and loot. Cities stay safe."),
        "arcane_storm": ("Tempestade Arcana", "Arcane Storm", "Uma Tempestade Arcana se forma.", "An Arcane Storm gathers.",
                         "A Tempestade Arcana passou.", "The Arcane Storm has passed.",
                         "Tempestade Arcana: magias de água e vento ficam mais fortes enquanto ela durar.",
                         "Arcane Storm: water and wind spells are stronger while it lasts."),
        "earthquake": ("Terremoto", "Earthquake", "A terra está tremendo!", "The ground is shaking!",
                       "A terra se acalmou.", "The ground has settled.",
                       "Terremoto: fendas se abrem no ermo e deixam minérios à mostra. Cuidado onde pisa.",
                       "Earthquake: cracks open in the wilds and expose ores. Watch your step."),
        "mana_storm": ("Tempestade de Mana", "Mana Storm", "Uma Tempestade de Mana cobre o céu.", "A Mana Storm fills the sky.",
                       "A Tempestade de Mana se dissipou.", "The Mana Storm has faded.",
                       "Tempestade de Mana: toda magia fica mais forte e cristais de mana caem do céu ao ar livre.",
                       "Mana Storm: all magic is stronger and mana crystals fall from the sky outdoors."),
    }
    for eid, (pt, en, spt, sen, ept, een, hpt, hen) in events.items():
        t(f"world_event.{NS}.{eid}", pt, en)
        t(f"world_event.{NS}.{eid}.start", spt, sen)
        t(f"world_event.{NS}.{eid}.end", ept, een)
        t(f"hint.{NS}.world_events.{eid}", hpt, hen)
    t(f"world_event.{NS}.meteor.fizzle", "O meteoro se desfez no ar.", "The meteor burned up in the air.")
    t(f"world_event.{NS}.meteor.invaders", "Algo está saindo da cratera...", "Something is crawling out of the crater...")
    t(f"world_event.{NS}.lens.calm", "O céu está calmo.", "The sky is calm.")
    t(f"world_event.{NS}.lens.active", "%s — faltam uns %s min.", "%s - about %s min left.")
    t(f"world_event.{NS}.lens.where", "%s — a %s blocos, para o %s.", "%s - %s blocks away, to the %s.")
    for d, pt, en in (("n", "norte", "north"), ("ne", "nordeste", "north-east"), ("e", "leste", "east"),
                      ("se", "sudeste", "south-east"), ("s", "sul", "south"), ("sw", "sudoeste", "south-west"),
                      ("w", "oeste", "west"), ("nw", "noroeste", "north-west")):
        t(f"world_event.{NS}.lens.dir.{d}", pt, en)

    c = f"command.{NS}.world_events."
    t(c + "none", "Nenhum evento do mundo ativo.", "No world event is active.")
    t(c + "unknown", "Evento desconhecido. Use: meteor, blood_moon, arcane_storm, earthquake, mana_storm, star_shower.",
      "Unknown event. Use: meteor, blood_moon, arcane_storm, earthquake, mana_storm, star_shower.")
    t(c + "no_site", "Não deu para começar: nenhum lugar livre por perto (longe de cidades, estruturas e construções) "
      "ou você não está no mundo normal.",
      "Could not start: no free spot nearby (away from cities, structures and builds) or you are not in the overworld.")
    t(c + "started", "Evento iniciado: %s", "Event started: %s")
    t(c + "stopped", "Evento encerrado.", "Event stopped.")
    t(c + "auto", "Sorteio automático de eventos: %s", "Automatic event rolls: %s")
    t(c + "chance", "Chance dos eventos: %s%% do normal", "Event chance: %s%% of normal")
    t(c + "site", "Aqui é lugar livre: %s · lugar livre achado por perto: %s", "This spot is free: %s · free spot found nearby: %s")
    t(c + "aliens", "Invasores invocados.", "Invaders summoned.")
    t(c + "status", "Evento: %s (%ss restantes) · lugar: %s · automático: %s · chance: %s%% · dia %s, próximo "
      "permitido no dia %s · chunks com blocos de jogador: %s",
      "Event: %s (%ss left) · site: %s · auto: %s · chance: %s%% · day %s, next allowed on day %s · "
      "chunks with player blocks: %s")

    for i, name in ((0, "pt_br"), (1, "en_us")):
        write_json(os.path.join(LANG, name + ".json"), {k: v[i] for k, v in sorted(L.items())})


def main():
    print("world_events: texturas")
    tex_blocks(); tex_items()
    print("world_events: modelos")
    models(); geo()
    print("world_events: dados")
    loot_tables(); recipes(); tags(); lang()
    print("world_events: pronto (%d traducoes)" % len(L))


if __name__ == "__main__":
    main()
