"""Gera os assets/dados do modulo "trials" (Camaras de Provacao).

Uso (na raiz do projeto):  py -I tools/gen/trials.py

Gera: texturas dos blocos (altar, portal de saida, pedra runica), icone da barreira, o selo do ceu do Reino,
modelos/blockstates, a dimensao isekai_mahou:trial_realm (dimension, dimension_type, bioma), loot table da pedra
runica e as tags compartilhadas (picareta, imunes ao Wither/Dragao) no merge do modulo.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/trials")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save_tex(c, rel):
    c.save(os.path.join(A, "textures", rel + ".png"))


# ------------------------------------------------------------------------------------------------
# Paleta
# ------------------------------------------------------------------------------------------------
ST0 = hex_rgba("15111F")
ST1 = hex_rgba("241D33")
ST2 = hex_rgba("332A47")
ST3 = hex_rgba("43395C")
ST4 = hex_rgba("564A74")
GD0 = hex_rgba("5E440E")
GD1 = hex_rgba("8A6A1E")
GD2 = hex_rgba("C9A23A")
GD3 = hex_rgba("F2D06B")
AR0 = hex_rgba("3A1E7A")
AR1 = hex_rgba("6A3FD0")
AR2 = hex_rgba("9B6BFF")
AR3 = hex_rgba("C9AEFF")
AR4 = hex_rgba("F4ECFF")
CY0 = hex_rgba("0B2A44")
CY1 = hex_rgba("1F6FA0")
CY2 = hex_rgba("4FD2FF")
CY3 = hex_rgba("BDF5FF")
PL0 = hex_rgba("6E6886")
PL1 = hex_rgba("8F89A8")
PL2 = hex_rgba("B2ADC8")
PL3 = hex_rgba("D4D0E4")


def stone_fill(c, x0, y0, x1, y1, base, seed, amount=0.08):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            f = (rnd.random() - 0.5) * 2 * amount
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))


def glow_pixel(c, x, y, core, halo):
    c.set(x, y, core)
    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p = c.get(x + ox, y + oy)
        if p[3] > 0 and p not in (core,):
            c.set(x + ox, y + oy, mix(p, halo, 0.45))


# ------------------------------------------------------------------------------------------------
# Altar da Provacao
# ------------------------------------------------------------------------------------------------
def altar_side():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST2, 11)
    # faixas douradas (topo e base)
    for x in range(16):
        c.set(x, 0, GD1)
        c.set(x, 1, GD2 if x % 3 else GD3)
        c.set(x, 2, GD0)
        c.set(x, 13, GD0)
        c.set(x, 14, GD2 if (x + 1) % 4 else GD3)
        c.set(x, 15, GD1)
    # juntas da pedra
    for x in range(16):
        if x in (5, 11):
            for y in range(3, 13):
                c.set(x, y, darken(c.get(x, y), 0.35))
    for x in range(16):
        c.set(x, 8, darken(c.get(x, 8), 0.25))
    # runa arcana brilhando no centro: losango + barra
    rune = [(8, 4), (7, 5), (9, 5), (6, 6), (10, 6), (5, 7), (11, 7), (6, 8), (10, 8), (7, 9), (9, 9), (8, 10),
            (8, 6), (8, 7), (8, 8), (7, 7), (9, 7)]
    for (x, y) in rune:
        c.set(x, y, AR1)
    for (x, y) in rune:
        glow_pixel(c, x, y, AR3 if (x, y) in ((8, 7), (8, 4), (8, 10), (5, 7), (11, 7)) else AR2, AR1)
    c.set(8, 7, AR4)
    save_tex(c, "block/trial_altar_side")


def altar_top():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST3, 12)
    cx = cy = 8
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 6.0 <= r < 7.2:
                c.set(x, y, GD2 if (x + y) % 3 else GD3)
            elif 4.4 <= r < 5.2:
                c.set(x, y, AR2)
            elif r < 2.2:
                c.set(x, y, AR4 if r < 1.1 else AR3)
            elif r < 3.2:
                c.set(x, y, mix(c.get(x, y), AR1, 0.5))
    # 4 tracos radiais (runas) entre os aneis
    for ang in (45, 135, 225, 315):
        a = math.radians(ang)
        for d in (5.6, 6.0):
            c.set(int(cx + math.cos(a) * d), int(cy + math.sin(a) * d), AR3)
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, GD1)
    save_tex(c, "block/trial_altar_top")


def altar_bottom():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST1, 13)
    c.outline_rect(0, 0, 15, 15, ST0)
    save_tex(c, "block/trial_altar_bottom")


# ------------------------------------------------------------------------------------------------
# Portal de Saida
# ------------------------------------------------------------------------------------------------
def exit_side():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, PL1, 21, 0.06)
    for x in range(16):
        c.set(x, 0, GD2)
        c.set(x, 1, GD1)
        c.set(x, 14, GD1)
        c.set(x, 15, GD2)
    # veios de cristal ciano subindo
    rnd = random.Random(5)
    for vx in (3, 8, 12):
        x = vx
        for y in range(13, 1, -1):
            c.set(x, y, CY2 if y % 3 else CY3)
            if rnd.random() < 0.35:
                x = max(1, min(14, x + rnd.choice((-1, 1))))
                c.set(x, y, CY1)
    for y in range(2, 14):
        c.set(0, y, PL0)
        c.set(15, y, PL0)
    save_tex(c, "block/trial_exit_side")


def exit_top():
    c = Canvas(16, 16)
    cx = cy = 8
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            spiral = (math.sin(ang * 3 + r * 1.2) + 1) / 2
            if r > 7.3:
                c.set(x, y, GD2 if (x + y) % 2 else GD1)
            elif r > 6.4:
                c.set(x, y, PL2)
            else:
                t = r / 6.4
                base = mix(CY3, CY0, t)
                c.set(x, y, mix(base, AR2, spiral * 0.45 * (1 - t * 0.3)))
    c.set(7, 7, AR4)
    c.set(8, 8, AR4)
    save_tex(c, "block/trial_exit_top")


# ------------------------------------------------------------------------------------------------
# Pedra Runica
# ------------------------------------------------------------------------------------------------
def rune_stone():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST2, 31)
    for i in range(16):
        c.set(i, 0, ST0)
        c.set(i, 8, ST0)
        c.set(0, i, ST0)
        c.set(8, i, ST0)
    glyphs = [
        [(3, 2), (3, 3), (3, 4), (3, 5), (4, 3), (5, 4), (4, 5)],
        [(11, 2), (12, 3), (13, 4), (12, 5), (11, 6), (11, 4)],
        [(2, 11), (3, 11), (4, 11), (5, 11), (3, 12), (4, 13), (3, 10)],
        [(12, 10), (12, 11), (12, 12), (12, 13), (11, 12), (13, 12), (10, 10), (14, 14)],
    ]
    for g in glyphs:
        for (x, y) in g:
            c.set(x, y, AR1)
        for (x, y) in g:
            glow_pixel(c, x, y, AR2, AR0)
        x, y = g[0]
        c.set(x, y, AR3)
    save_tex(c, "block/trial_rune_stone")


# ------------------------------------------------------------------------------------------------
# Barreira (icone do item)
# ------------------------------------------------------------------------------------------------
def barrier_icon():
    c = Canvas(16, 16)
    red = hex_rgba("E0407A")
    red_d = hex_rgba("8A1E4A")
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.2 <= r <= 7.2:
                c.set(x, y, red if r < 6.4 else red_d)
    for i in range(3, 13):
        c.set(i, i, red)
        c.set(i + 1, i, red_d)
    for (x, y) in ((8, 1), (14, 8), (8, 14), (1, 8)):
        c.set(x, y, AR3)
    save_tex(c, "item/trial_barrier")


# ------------------------------------------------------------------------------------------------
# Selo do ceu (branco, tingido pelo elemento no cliente)
# ------------------------------------------------------------------------------------------------
def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / (vx * vx + vy * vy)))
    return math.hypot(px - (ax + vx * t), py - (ay + vy * t))


def line_alpha(d, w):
    if d <= w:
        return 1.0
    if d <= w + 1.0:
        return 1.0 - (d - w)
    return 0.0


def trial_sigil():
    size = 128
    cx = cy = size / 2
    hexa = [(cx + math.cos(math.radians(-90 + i * 60)) * 40, cy + math.sin(math.radians(-90 + i * 60)) * 40) for i in range(6)]
    tri_lines = [(hexa[i], hexa[(i + 2) % 6]) for i in range(6)]
    inner = [(cx + math.cos(math.radians(i * 45)) * 18, cy + math.sin(math.radians(i * 45)) * 18) for i in range(8)]
    inner_lines = [(inner[i], inner[(i + 3) % 8]) for i in range(8)]

    def fn(x, y):
        r = math.hypot(x - cx, y - cy)
        ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
        a = 0.0
        a = max(a, line_alpha(abs(r - 62), 0.8))
        a = max(a, line_alpha(abs(r - 55), 0.55))
        a = max(a, 0.15 * line_alpha(abs(r - 58.5), 3.0))
        if 55.8 < r < 61.2:
            k = int(ang // 7.5)
            mid = k * 7.5 + 3.75
            if k % 3 == 0:
                mx = cx + math.cos(math.radians(mid)) * 58.5
                my = cy + math.sin(math.radians(mid)) * 58.5
                a = max(a, line_alpha(abs(math.hypot(x - mx, y - my) - 1.3), 0.45))
            else:
                da = abs(ang - mid) * math.pi / 180 * r
                a = max(a, 0.85 * line_alpha(da, 0.4))
        a = max(a, line_alpha(abs(r - 40), 0.6))
        for (p, q) in tri_lines:
            a = max(a, 0.85 * line_alpha(seg_dist(x, y, p[0], p[1], q[0], q[1]), 0.45))
        for (px, py) in hexa:
            a = max(a, line_alpha(abs(math.hypot(x - px, y - py) - 3.2), 0.5))
        a = max(a, line_alpha(abs(r - 18), 0.55))
        for (p, q) in inner_lines:
            a = max(a, 0.7 * line_alpha(seg_dist(x, y, p[0], p[1], q[0], q[1]), 0.35))
        if r < 4:
            a = max(a, 1.0 - r / 4)
        if r > 63.5:
            a = 0.0
        return a

    c = Canvas(size, size)
    ss = 3
    for y in range(size):
        for x in range(size):
            acc = 0.0
            for sy in range(ss):
                for sx in range(ss):
                    acc += fn(x + (sx + 0.5) / ss, y + (sy + 0.5) / ss)
            al = max(0.0, min(1.0, acc / (ss * ss)))
            if al > 0.003:
                c.set(x, y, (255, 255, 255, int(round(al * 255))))
    save_tex(c, "environment/trial_sigil")


# ------------------------------------------------------------------------------------------------
# Modelos / blockstates
# ------------------------------------------------------------------------------------------------
def faces(side, up=None, down=None):
    f = {d: {"texture": side} for d in ("north", "south", "east", "west")}
    if up:
        f["up"] = {"texture": up}
    if down:
        f["down"] = {"texture": down}
    return f


def models():
    write_json(os.path.join(A, "models/block/trial_altar.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/trial_altar_side",
            "side": f"{NS}:block/trial_altar_side",
            "top": f"{NS}:block/trial_altar_top",
            "bottom": f"{NS}:block/trial_altar_bottom",
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {**faces("#side", "#bottom"), "down": {"texture": "#bottom", "cullface": "down"}}},
            {"from": [4, 3, 4], "to": [12, 10, 12], "faces": faces("#side")},
            {"from": [2, 10, 2], "to": [14, 13, 14], "faces": faces("#side", "#top", "#bottom")},
        ],
    })
    write_json(os.path.join(A, "models/block/trial_exit.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/trial_exit_side",
            "side": f"{NS}:block/trial_exit_side",
            "top": f"{NS}:block/trial_exit_top",
            "bottom": f"{NS}:block/trial_altar_bottom",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {**faces("#bottom", "#top"), "down": {"texture": "#bottom", "cullface": "down"}}},
            {"from": [3, 2, 3], "to": [13, 12, 13], "faces": faces("#side", "#top")},
        ],
    })
    write_json(os.path.join(A, "models/block/trial_barrier.json"), {
        "textures": {"particle": f"{NS}:item/trial_barrier"},
    })
    write_json(os.path.join(A, "models/block/trial_rune_stone.json"), {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"{NS}:block/trial_rune_stone"},
    })
    for bid in ("trial_altar", "trial_exit", "trial_barrier", "trial_rune_stone"):
        write_json(os.path.join(A, f"blockstates/{bid}.json"), {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
    for bid in ("trial_altar", "trial_exit", "trial_rune_stone"):
        write_json(os.path.join(A, f"models/item/{bid}.json"), {"parent": f"{NS}:block/{bid}"})
    write_json(os.path.join(A, "models/item/trial_barrier.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/trial_barrier"},
    })


# ------------------------------------------------------------------------------------------------
# Dimensao do Reino das Provacoes
# ------------------------------------------------------------------------------------------------
def dimension():
    write_json(os.path.join(D, "dimension/trial_realm.json"), {
        "type": f"{NS}:trial_realm",
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": f"{NS}:trial_realm",
                "layers": [{"block": "minecraft:air", "height": 1}],
                "lakes": False,
                "features": False,
                "structure_overrides": [],
            },
        },
    })
    write_json(os.path.join(D, "dimension_type/trial_realm.json"), {
        "ultrawarm": False,
        "natural": False,
        "coordinate_scale": 1.0,
        "piglin_safe": False,
        "respawn_anchor_works": False,
        "has_raids": False,
        "min_y": 0,
        "height": 256,
        "logical_height": 256,
        "monster_spawn_light_level": 0,
        "monster_spawn_block_light_limit": 0,
        "has_skylight": True,
        "has_ceiling": False,
        "ambient_light": 0.25,
        "fixed_time": 18000,
        "bed_works": False,
        "infiniburn": "#minecraft:infiniburn_end",
        "effects": f"{NS}:trial_realm",
    })
    write_json(os.path.join(D, "worldgen/biome/trial_realm.json"), {
        "has_precipitation": False,
        "temperature": 0.5,
        "downfall": 0.0,
        "effects": {
            "sky_color": 0x1A1236,
            "fog_color": 0x120C24,
            "water_color": 0x3F76E4,
            "water_fog_color": 0x050533,
            "music": {
                "sound": "minecraft:music.end",
                "min_delay": 6000,
                "max_delay": 12000,
                "replace_current_music": False,
            },
            "particle": {"options": {"type": "minecraft:end_rod"}, "probability": 0.0005},
        },
        "spawners": {k: [] for k in ("monster", "creature", "ambient", "axolotls", "underground_water_creature",
                                       "water_creature", "water_ambient", "misc")},
        "spawn_costs": {},
        "carvers": {},
        "features": [[] for _ in range(11)],
    })


def data_files():
    write_json(os.path.join(D, "loot_table/blocks/trial_rune_stone.json"), {
        "type": "minecraft:block",
        "random_sequence": f"{NS}:blocks/trial_rune_stone",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:trial_rune_stone"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    tags = os.path.join(MERGE, "data/minecraft/tags/block")
    write_json(os.path.join(tags, "mineable/pickaxe.json"), {"values": [f"{NS}:trial_rune_stone"]})
    immune = [f"{NS}:trial_altar", f"{NS}:trial_exit", f"{NS}:trial_barrier"]
    write_json(os.path.join(tags, "wither_immune.json"), {"values": immune})
    write_json(os.path.join(tags, "dragon_immune.json"), {"values": immune})


def main():
    altar_side()
    altar_top()
    altar_bottom()
    exit_side()
    exit_top()
    rune_stone()
    barrier_icon()
    trial_sigil()
    models()
    dimension()
    data_files()
    print("trials: assets gerados")


if __name__ == "__main__":
    main()
