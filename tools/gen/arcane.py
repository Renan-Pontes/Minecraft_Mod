"""
Assets do modulo arcane (texturas, blockstates e modelos dos blocos tecnicos + icones de efeito).
Rodar da raiz do projeto:  py -I tools/gen/arcane.py
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")


def write_json(rel, data):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


# ---------------------------------------------------------------------------------------------
#  Luz Arcana: gradiente radial (nucleo branco -> lilas translucido) + faiscas
# ---------------------------------------------------------------------------------------------
def arcane_light():
    c = Canvas(16, 16)
    core = (255, 252, 255, 255)
    inner = (232, 214, 255, 255)
    mid = (196, 156, 255, 225)
    outer = (160, 112, 240, 140)
    rim = (130, 84, 214, 70)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 2.2:
                col = core
            elif d < 3.8:
                col = mix(core, inner, (d - 2.2) / 1.6)
            elif d < 5.4:
                col = mix(inner, mid, (d - 3.8) / 1.6)
            elif d < 7.0:
                col = mix(mid, outer, (d - 5.4) / 1.6)
            else:
                col = mix(outer, rim, min(1.0, (d - 7.0) / 2.0))
            c.set(x, y, col)
    for (x, y) in ((3, 4), (12, 3), (13, 11), (4, 12), (8, 1), (1, 8), (14, 7), (7, 14)):
        c.set(x, y, (255, 255, 255, 200))
    c.save(os.path.join(A, "textures", "block", "arcane_light.png"))

    face = lambda uv: {d: {"uv": uv, "texture": "#orb"} for d in ("north", "south", "east", "west", "up", "down")}
    write_json("models/block/arcane_light.json", {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "render_type": "minecraft:translucent",
        "textures": {"orb": "isekai_mahou:block/arcane_light", "particle": "isekai_mahou:block/arcane_light"},
        "elements": [
            {"from": [6.5, 6.5, 6.5], "to": [9.5, 9.5, 9.5], "shade": False, "faces": face([5, 5, 11, 11])},
            {"from": [5, 5, 5], "to": [11, 11, 11], "shade": False, "faces": face([0, 0, 16, 16])},
        ],
    })
    write_json("blockstates/arcane_light.json", {"variants": {"": {"model": "isekai_mahou:block/arcane_light"}}})


# ---------------------------------------------------------------------------------------------
#  Pedra Conjurada: tijolos violeta com runas brilhando
# ---------------------------------------------------------------------------------------------
def conjured_stone():
    c = Canvas(16, 16)
    base = hex_rgba("4F3A7A")
    mortar = hex_rgba("241A3E")
    for y in range(16):
        for x in range(16):
            c.set(x, y, base)
    # Fileiras de tijolos de 4px, alternando o deslocamento
    for row in range(4):
        y0 = row * 4
        for x in range(16):
            c.set(x, y0, mortar)
        off = 0 if row % 2 == 0 else 4
        for k in range(2):
            xj = (off + k * 8) % 16
            for y in range(y0, y0 + 4):
                c.set(xj, y, mortar)
        # Sombreamento de cada tijolo: topo claro, base escura
        for x in range(16):
            if c.get(x, y0 + 1) != mortar:
                c.set(x, y0 + 1, lighten(base, 0.18))
            if c.get(x, y0 + 3) != mortar:
                c.set(x, y0 + 3, darken(base, 0.22))
    c.noise(0.07, seed=11)
    # Runas (pixels brilhantes) em alguns tijolos
    glow = hex_rgba("D9B8FF")
    bright = (255, 255, 255, 255)
    for (x, y) in ((3, 2), (4, 2), (4, 3), (11, 6), (12, 6), (12, 7), (13, 6), (6, 10), (6, 11), (7, 10), (13, 14), (14, 14), (2, 14)):
        c.set(x, y, glow)
    for (x, y) in ((4, 2), (12, 6), (6, 10), (13, 14)):
        c.set(x, y, bright)
    c.save(os.path.join(A, "textures", "block", "conjured_stone.png"))
    write_json("models/block/conjured_stone.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "isekai_mahou:block/conjured_stone"},
    })
    write_json("blockstates/conjured_stone.json", {"variants": {"": {"model": "isekai_mahou:block/conjured_stone"}}})


# ---------------------------------------------------------------------------------------------
#  Icones dos efeitos (18x18), no mesmo estilo dos do nucleo: disco sombreado + simbolo
# ---------------------------------------------------------------------------------------------
def effect_icon(name, color, symbol_rows, symbol_color=(255, 255, 255, 255)):
    c = Canvas(18, 18)
    base = hex_rgba(color)
    c.circle(9, 9, 7.5, darken(base, 0.2))
    c.circle(9, 9, 6.2, base)
    c.shade_sphere(9, 9, 7.5, light=0.45, dark=0.5)
    c.outline(darken(base, 0.7))
    shadow = darken(base, 0.6)
    pal_s = {"#": shadow}
    pal = {"#": symbol_color}
    c.from_rows(symbol_rows, pal_s, ox=5, oy=5)  # sombra deslocada 1px
    c.from_rows(symbol_rows, pal, ox=4, oy=4)
    c.save(os.path.join(A, "textures", "mob_effect", name + ".png"))


REFLECT = [
    "...##.##...",
    "..#.....#..",
    ".#..###..#.",
    ".#..#.#..#.",
    ".#..###..#.",
    "..#.....#..",
    "...#...#...",
    "....#.#....",
    ".....#.....",
]
SHRINK = [
    "#.........#",
    ".#.......#.",
    "..#.....#..",
    "....###....",
    "....#.#....",
    "....###....",
    "..#.....#..",
    ".#.......#.",
    "#.........#",
]
GROWTH = [
    "###.....###",
    "##.......##",
    "#.#.....#.#",
    "...#####...",
    "...#...#...",
    "...#####...",
    "#.#.....#.#",
    "##.......##",
    "###.....###",
]


if __name__ == "__main__":
    arcane_light()
    conjured_stone()
    effect_icon("arcane_reflect", "8E5CE0", REFLECT)
    effect_icon("arcane_shrink", "3FA6C0", SHRINK)
    effect_icon("arcane_growth", "E0892F", GROWTH)
    print("arcane assets ok")
