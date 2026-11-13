"""Gera os assets/dados do modulo "mana_core" (v1.5): Nucleo de Mana, Condutor de Mana, Intoxicacao por Mana.

Uso (na raiz do projeto):  py -I tools/gen/mana_core_assets.py

Gera: texturas do Nucleo (camara com 7 niveis de mana, moldura, tampo, fundo), icone do Condutor, icone do efeito,
a tela do Nucleo e a tela NOVA do Pedestal Runico (pergaminho + filtro lado a lado), modelos/blockstate, loot table
(o Nucleo guarda a carga), receitas e a tag de picareta (merge).
Reaproveita a paleta e os ajudantes de tools/gen/automation.py (sem rodar o main dele).
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, ".."))
import automation as au  # noqa: E402
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A, D, NS = au.A, au.D, au.NS
MERGE = os.path.join(au.ROOT, "src/main/merge/mana_core")
write_json, save_tex, jitter, bevel = au.write_json, au.save_tex, au.jitter, au.bevel

CLEAR = (0, 0, 0, 0)
MG0 = hex_rgba("3A0F4F")   # magenta da sobrecarga
MG1 = hex_rgba("7A1FA0")
MG2 = hex_rgba("B03CE0")
MG3 = hex_rgba("E04BFF")
MG4 = hex_rgba("FFB8FF")


# ------------------------------------------------------------------------------------------------
# Nucleo de Mana
# ------------------------------------------------------------------------------------------------
def core_frame():
    """Base e tampa: pedra runica com friso dourado. Linhas 0-2 = tampa (lado), 13-15 = base (lado)."""
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, au.ST2, 301, 0.06)
    for x in range(16):
        # tampa (y 0..2)
        c.set(x, 0, au.GD2 if x % 4 else au.GD3)
        c.set(x, 1, au.AR2 if x % 5 == 2 else au.ST3)
        c.set(x, 2, au.GD0)
        # base (y 13..15)
        c.set(x, 13, au.GD1)
        c.set(x, 14, au.AR1 if x % 5 == 2 else au.ST1)
        c.set(x, 15, au.ST0)
    for x in (0, 15):
        for y in (0, 1, 2, 13, 14, 15):
            c.set(x, y, au.GD2)
    save_tex(c, "block/mana_core_frame")


def core_side(level):
    """Camara de cristal (area visivel x 1..14, y 3..12) com o mana subindo; colunas douradas nas bordas."""
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, au.ST1, 311, 0.05)
    # Colunas (x 1..2 e 13..14)
    for y in range(3, 13):
        for x in (1, 14):
            c.set(x, y, au.GD1)
        for x in (2, 13):
            c.set(x, y, au.ST3 if y % 3 else au.GD2)
    # Vidro (x 3..12, y 4..11)
    x0, x1, y0, y1 = 3, 12, 4, 11
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, au.GL0 if (x + y) % 4 else au.GL1)
    bevel(c, x0 - 1, y0 - 1, x1 + 1, y1 + 1, au.ST0, au.ST4)
    h = y1 - y0 + 1  # 8
    fill = [0, 1, 3, 4, 6, 7, 8][level]
    rnd = random.Random(320 + level)
    for y in range(y1 - fill + 1, y1 + 1):
        for x in range(x0, x1 + 1):
            depth = (y1 - y) / max(1, fill)
            c.set(x, y, mix(au.AR2, au.AR0, depth * 0.7) if (x - x0) > 1 else au.AR3)
    if fill > 0:
        top = y1 - fill + 1
        for x in range(x0, x1 + 1):
            c.set(x, top, au.AR4 if level == 6 else au.AR3)
        for _ in range(1 + level):
            bx = rnd.randint(x0 + 1, x1 - 1)
            by = rnd.randint(top, y1)
            c.set(bx, by, au.AR4)
    # Cristal flutuando no meio (sempre visivel, mais aceso quanto mais cheio)
    glow = level / 6.0
    crystal = [
        ".##.",
        "#oc#",
        "#oo#",
        ".##.",
    ]
    pal = {"#": lighten(au.AR1, 0.3 * glow), "o": lighten(au.AR3, 0.3 * glow), "c": au.AR4}
    c.from_rows(crystal, pal, 6, 5)
    # Reflexo
    for y in range(y0, y1 + 1):
        if (y - y0) % 5 < 2:
            c.set(x0, y, lighten(c.get(x0, y), 0.3))
    # Gotas de ouro nos cantos
    for (x, y) in ((1, 3), (14, 3), (1, 12), (14, 12)):
        c.set(x, y, au.GD3)
    save_tex(c, f"block/mana_core_side_{level}")


def core_top():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, au.ST2, 331, 0.05)
    bevel(c, 0, 0, 15, 15, au.GD2, au.GD0)
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 5.2 <= d <= 6.0:
                c.set(x, y, au.GD2)
            elif 3.0 <= d <= 3.7:
                c.set(x, y, au.AR2)
    # Losango de cristal
    rows = [
        "..#..",
        ".#o#.",
        "#ooo#",
        ".#o#.",
        "..#..",
    ]
    c.from_rows(rows, {"#": au.AR2, "o": au.AR4}, 6, 6)
    for k in range(8):
        a = k * math.pi / 4
        c.set(int(round(7.5 + math.cos(a) * 5.6)), int(round(7.5 + math.sin(a) * 5.6)), au.GD3)
    save_tex(c, "block/mana_core_top")


def core_bottom():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, au.ST1, 341, 0.05)
    bevel(c, 0, 0, 15, 15, au.ST3, au.ST0)
    save_tex(c, "block/mana_core_bottom")


def core_inner():
    """Teto/chao da camara (visto entre a tampa e a base) — pedra escura."""
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, au.ST0, 351, 0.05)
    save_tex(c, "block/mana_core_inner")


# ------------------------------------------------------------------------------------------------
# Condutor de Mana (item) — vara de ouro com diapasao e cristal
# ------------------------------------------------------------------------------------------------
def conductor_icon():
    c = Canvas(16, 16)
    rows = [
        "...........o.o..",
        "..........o#.#o.",
        "..........o#c#o.",
        "...........##o..",
        "..........gg....",
        ".........gG.....",
        "........gG......",
        ".......gG.......",
        "......gG........",
        ".....gG.........",
        "....gG..........",
        "...rGr..........",
        "..rgr...........",
        ".gg.............",
        "gg..............",
        "................",
    ]
    pal = {"o": au.AR3, "#": au.AR1, "c": au.AR4, "g": au.GD1, "G": au.GD3, "r": au.AR2}
    c.from_rows(rows, pal)
    c.set(12, 2, au.AR4)
    c.outline(hex_rgba("1A1024"))
    save_tex(c, "item/mana_conductor")


# ------------------------------------------------------------------------------------------------
# Intoxicacao por Mana (icone 18x18)
# ------------------------------------------------------------------------------------------------
def intoxication_icon():
    c = Canvas(18, 18)
    # Gota de mana rachada, magenta, com espirais
    rows = [
        "........#.........",
        ".......#o#........",
        "......#ooo#.......",
        ".....#oooo##......",
        "....#ooxoooo#.....",
        "...#oooxoooo#.....",
        "...#ooooxooo##....",
        "..#ooooxooooo#....",
        "..#oooxoooooo#....",
        "..#ooooxooo*o#....",
        "..#oooooxoo*o#....",
        "...#ooooooooo#....",
        "...#oo*oooo#......",
        "....##oooo##......",
        "......####........",
    ]
    c.from_rows(rows, {"#": MG0, "o": MG2, "x": MG0, "*": MG4}, 2, 1)
    c.shade_sphere(8, 10, 7, 0.35, 0.35)
    # Espirais (tontura)
    for (x, y) in ((14, 3), (15, 4), (15, 5), (14, 6), (13, 5), (16, 9), (16, 10), (15, 11)):
        c.set(x, y, MG4)
    save_tex(c, "mob_effect/mana_intoxication")


# ------------------------------------------------------------------------------------------------
# Telas
# ------------------------------------------------------------------------------------------------
def gui_core():
    c = Canvas(256, 256)
    W, H = 176, 144
    au.panel(c, W, H, 361)
    # Reserva (barra 9..166 x 19..30)
    au.inset(c, 8, 18, 167, 31)
    for k in range(4):
        c.set(6, 19 + k * 4, au.GD2)
        c.set(169, 19 + k * 4, au.GD2)
    au.separator(c, 6, 169, 47)
    # Seu mana (barra 9..166 x 62..65)
    au.inset(c, 8, 61, 167, 66)
    au.separator(c, 6, 169, 69)
    au.separator(c, 6, 169, 120)
    save_tex(c, "gui/mana_core")


def gui_pedestal():
    """Tela do Pedestal v1.5: pergaminho (9,20) e FILTRO (30,20) lado a lado no topo da coluna da esquerda."""
    c = Canvas(256, 256)
    W, H = 176, 236
    au.panel(c, W, H, 131)
    au.inset(c, 6, 15, 49, 100)      # coluna do pergaminho/filtro
    au.inset(c, 51, 14, 169, 100)    # configuracao
    for i in range(6):
        y = 18 + 14 * i + 10
        if i < 5:
            for x in range(54, 167, 2):
                c.set(x, y, hex_rgba("2A2140"))
    au.slot(c, 9, 20)
    au.slot(c, 30, 20)
    # Cantos dourados no espaco do pergaminho, cantos verdes-agua no filtro
    for (x, y) in ((7, 18), (26, 18), (7, 37), (26, 37)):
        c.set(x, y, au.GD3)
    for (x, y) in ((28, 18), (47, 18), (28, 37), (47, 37)):
        c.set(x, y, hex_rgba("6BE3C8"))
    # Trilho da proxima conjuracao (9..46, 92..94)
    au.inset(c, 8, 91, 47, 96)
    # Barra de mana (9..166, 115..118)
    au.inset(c, 8, 114, 167, 119)
    au.separator(c, 6, 169, 142)
    au.inventory(c, 8, 154, 212)
    save_tex(c, "gui/automation_pedestal")


# ------------------------------------------------------------------------------------------------
# Modelos e blockstates
# ------------------------------------------------------------------------------------------------
def tex(name):
    return f"{NS}:block/{name}"


def models():
    sides = ("north", "south", "east", "west")
    write_json(os.path.join(A, "models/block/mana_core_0.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": tex("mana_core_top"),
            "frame": tex("mana_core_frame"),
            "side": tex("mana_core_side_0"),
            "top": tex("mana_core_top"),
            "bottom": tex("mana_core_bottom"),
            "inner": tex("mana_core_inner"),
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 3, 16], "faces": {
                **{d: {"texture": "#frame", "uv": [0, 13, 16, 16], "cullface": d} for d in sides},
                "up": {"texture": "#inner", "uv": [0, 0, 16, 16]},
                "down": {"texture": "#bottom", "uv": [0, 0, 16, 16], "cullface": "down"}}},
            {"from": [1, 3, 1], "to": [15, 13, 15], "faces": {d: {"texture": "#side", "uv": [1, 3, 15, 13]} for d in sides}},
            {"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
                **{d: {"texture": "#frame", "uv": [0, 0, 16, 3], "cullface": d} for d in sides},
                "up": {"texture": "#top", "uv": [0, 0, 16, 16], "cullface": "up"},
                "down": {"texture": "#inner", "uv": [0, 0, 16, 16]}}},
        ],
    })
    for lv in range(1, 7):
        write_json(os.path.join(A, f"models/block/mana_core_{lv}.json"), {
            "parent": f"{NS}:block/mana_core_0",
            "textures": {"side": tex(f"mana_core_side_{lv}")},
        })
    write_json(os.path.join(A, "blockstates/mana_core.json"), {"variants": {
        f"fill={lv}": {"model": f"{NS}:block/mana_core_{lv}"} for lv in range(7)}})
    write_json(os.path.join(A, "models/item/mana_core.json"), {
        "parent": f"{NS}:block/mana_core_0",
        "overrides": [{"predicate": {f"{NS}:core_fill": p}, "model": f"{NS}:block/mana_core_{lv}"}
                      for lv, p in ((1, 0.0001), (2, 0.2), (3, 0.4), (4, 0.6), (5, 0.8), (6, 1.0))],
    })
    write_json(os.path.join(A, "models/item/mana_conductor.json"), {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": f"{NS}:item/mana_conductor"},
    })


# ------------------------------------------------------------------------------------------------
# Dados
# ------------------------------------------------------------------------------------------------
def loot_tables():
    write_json(os.path.join(D, "loot_table/blocks/mana_core.json"), {
        "type": "minecraft:block",
        "random_sequence": f"{NS}:blocks/mana_core",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:mana_core",
                         "functions": [{"function": "minecraft:copy_components", "source": "block_entity",
                                        "include": [f"{NS}:core_mana"]}]}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


def recipes():
    m = f"{NS}:mana_crystal"
    ea = f"{NS}:essence_arcane"
    au.shaped("mana_core", ["AMA", "EBE", "ODO"], {
        "A": "minecraft:amethyst_block", "M": m, "E": ea, "B": f"{NS}:mana_battery",
        "O": "minecraft:crying_obsidian", "D": "minecraft:diamond"},
        f"{NS}:mana_core", category="misc")
    au.shaped("mana_conductor", ["  M", " A ", "G  "], {
        "M": m, "A": "minecraft:amethyst_shard", "G": "minecraft:gold_ingot"},
        f"{NS}:mana_conductor", category="equipment")


def tags():
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [f"{NS}:mana_core"]})


def main():
    core_frame()
    for lv in range(7):
        core_side(lv)
    core_top()
    core_bottom()
    core_inner()
    conductor_icon()
    intoxication_icon()
    gui_core()
    gui_pedestal()
    models()
    loot_tables()
    recipes()
    tags()
    print("mana_core: assets gerados")


if __name__ == "__main__":
    main()
