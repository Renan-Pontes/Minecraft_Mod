"""
Gerador de assets do modulo travel (Pedras de Teleporte):
  - travel_stone: obelisco runico alto (GeckoLib, ~2,4 blocos): plinto com anel de runas no topo, degrau, fuste com
    runas brilhantes (uma por face), fuste superior com selo, capitel dourado, cristal flutuante girando e tres
    fragmentos orbitando. Textura 64x64 + _glowmask (as runas e o cristal brilham no escuro).
  - animacoes: idle (lenta), awake (jogador perto: mais rapida, cristal sobe), flare (partida/chegada, disparada pelo
    servidor).
  - modelo de item 3D (elementos JSON) com texturas 16x16 proprias, blockstate, modelo do bloco (so particula),
    loot table (copia o nome dado na bigorna), receita, avanco que libera a receita e a tag de picareta (merge).

Rodar a partir da raiz do projeto:  py -I tools/gen/travel.py
Convencoes Bedrock/GeckoLib: 16 unidades = 1 bloco; (0,0,0) = centro da base do bloco. Ossos animados nao tem rotacao
inicial (as rotacoes fixas ficam nos ossos-filho shard_a/b/c).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/travel")
CLEAR = (0, 0, 0, 0)

# ----------------------------------------------------------------------------- paleta
STONE = hex_rgba("4A5068")
STONE_L = hex_rgba("6A7290")
STONE_D = hex_rgba("2E3244")
MORTAR = hex_rgba("1C1E2A")
PLINTH = hex_rgba("3C4054")
RUNE = hex_rgba("6FE3FF")
RUNE_L = hex_rgba("D2F8FF")
RUNE_D = hex_rgba("2A9CC8")
GOLD = hex_rgba("D8B25A")
GOLD_L = hex_rgba("F6DE94")
GOLD_D = hex_rgba("8A6A2A")
CRYSTAL = hex_rgba("8FF0FF")
CRYSTAL_L = hex_rgba("EFFFFF")
CRYSTAL_D = hex_rgba("3FB6E0")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


class Tex:
    """Textura + mascara de brilho pintadas juntas."""

    def __init__(self, w, h):
        self.tex = Canvas(w, h)
        self.glow = Canvas(w, h)

    def set(self, x, y, c, glow=False):
        self.tex.set(x, y, c)
        self.glow.set(x, y, c if glow else CLEAR)


def jitter(c, rnd, amount=0.07):
    f = (rnd.random() - 0.5) * 2 * amount
    return lighten(c, f) if f > 0 else darken(c, -f)


# ----------------------------------------------------------------------------- runas (8x14) para o fuste
RUNES = [
    [
        "........",
        "...##...",
        "..#..#..",
        "...##...",
        "...##...",
        ".#.##.#.",
        "..####..",
        "...##...",
        "...##...",
        "..#..#..",
        ".#....#.",
        "...##...",
        "..#..#..",
        "........",
    ],
    [
        "........",
        "..####..",
        "...##...",
        ".#.##.#.",
        ".######.",
        "...##...",
        "..#..#..",
        "..#..#..",
        "...##...",
        ".#.##.#.",
        "...##...",
        "..####..",
        "...##...",
        "........",
    ],
    [
        "........",
        ".#....#.",
        "..#..#..",
        "...##...",
        "..#..#..",
        "...##...",
        "...##...",
        ".######.",
        "...##...",
        "..#..#..",
        "..#..#..",
        "...##...",
        ".#....#.",
        "........",
    ],
    [
        "........",
        "...##...",
        "...##...",
        "..####..",
        ".#.##.#.",
        "...##...",
        "..#..#..",
        "...##...",
        "..####..",
        "...##...",
        ".#.##.#.",
        "...##...",
        "...##...",
        "........",
    ],
]


def cube_uv(u, v, w, h, d):
    """Retangulos (x, y, w, h) das faces do box UV: top, bottom e as 4 laterais (leste, norte, oeste, sul)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "sides": [(u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)],
    }


def paint_bricks(t, rect, rnd, base, light, dark, course=3):
    x0, y0, w, h = rect
    for fy in range(h):
        for fx in range(w):
            c = jitter(base, rnd)
            row = fy // course
            off = (row % 2) * 3
            if fy % course == course - 1:
                c = MORTAR
            elif (fx + off) % 6 == 5:
                c = darken(base, 0.35)
            elif fy % course == 0:
                c = jitter(light, rnd, 0.05)
            if fx == 0 or fx == w - 1:
                c = darken(c, 0.25)
            t.set(x0 + fx, y0 + fy, c)


def paint_plain(t, rect, rnd, base):
    x0, y0, w, h = rect
    for fy in range(h):
        for fx in range(w):
            t.set(x0 + fx, y0 + fy, jitter(base, rnd))


def paint_stone_texture(rnd):
    t = Tex(64, 64)

    # 1) Plinto 14x3x14 em (0,0): laterais de pedra com friso; topo com anel de runas brilhante
    f = cube_uv(0, 0, 14, 3, 14)
    for side in f["sides"]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                c = jitter(PLINTH, rnd)
                if fy == 0:
                    c = jitter(STONE_L, rnd, 0.04)
                if fy == h - 1:
                    c = darken(PLINTH, 0.3)
                if fx % 7 == 3 and fy == 1:
                    c = RUNE_D
                t.set(x0 + fx, y0 + fy, c, glow=(fx % 7 == 3 and fy == 1))
    x0, y0, w, h = f["top"]
    for fy in range(h):
        for fx in range(w):
            dx, dy = fx - 6.5, fy - 6.5
            r = math.hypot(dx, dy)
            c = jitter(PLINTH, rnd)
            glow = False
            if 5.2 <= r < 6.2:
                ang = math.atan2(dy, dx)
                # anel pontilhado: tracos de runa intercalados
                if int((ang + math.pi) / (2 * math.pi) * 20) % 2 == 0:
                    c, glow = RUNE, True
                else:
                    c = RUNE_D
            elif r >= 6.2:
                c = jitter(STONE_L, rnd, 0.05)
            t.set(x0 + fx, y0 + fy, c, glow)
    paint_plain(t, f["bottom"], rnd, STONE_D)

    # 2) Degrau 12x2x12 em (0,17)
    f = cube_uv(0, 17, 12, 2, 12)
    for side in f["sides"]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                c = jitter(STONE_D if fy == 1 else STONE, rnd)
                if fx % 4 == 0 and fy == 1:
                    c = MORTAR
                t.set(x0 + fx, y0 + fy, c)
    x0, y0, w, h = f["top"]
    for fy in range(h):
        for fx in range(w):
            c = jitter(STONE, rnd)
            corner = (fx in (1, w - 2)) and (fy in (1, h - 2))
            if corner:
                t.set(x0 + fx, y0 + fy, RUNE, True)
            else:
                t.set(x0 + fx, y0 + fy, c)
    paint_plain(t, f["bottom"], rnd, STONE_D)

    # 3) Fuste inferior 8x14x8 em (0,31): pedra lavrada com uma runa diferente por face
    f = cube_uv(0, 31, 8, 14, 8)
    for i, side in enumerate(f["sides"]):
        x0, y0, w, h = side
        rows = RUNES[i % len(RUNES)]
        for fy in range(h):
            for fx in range(w):
                c = jitter(STONE, rnd)
                if fy % 5 == 4:
                    c = darken(STONE, 0.2)
                if fx == 0 or fx == w - 1:
                    c = darken(c, 0.3)
                elif fx == 1:
                    c = lighten(c, 0.08)
                glow = False
                ch = rows[fy][fx] if fy < len(rows) and fx < len(rows[fy]) else "."
                if ch == "#":
                    # sulco brilhante: centro claro, bordas mais escuras
                    neigh = sum(1 for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                                if 0 <= fy + oy < len(rows) and 0 <= fx + ox < 8 and rows[fy + oy][fx + ox] == "#")
                    c = RUNE_L if neigh >= 3 else RUNE
                    glow = True
                else:
                    # halo escuro em volta do sulco (dá profundidade)
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = fx + ox, fy + oy
                        if 0 <= ny < len(rows) and 0 <= nx < 8 and rows[ny][nx] == "#":
                            c = mix(c, RUNE_D, 0.35)
                            break
                t.set(x0 + fx, y0 + fy, c, glow)
    paint_plain(t, f["top"], rnd, STONE_L)
    paint_plain(t, f["bottom"], rnd, STONE_D)

    # 4) Fuste superior 6x9x6 em (32,31): selo em losango e faixa dourada na base
    f = cube_uv(32, 31, 6, 9, 6)
    for side in f["sides"]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                c = jitter(STONE_L if fx < w // 2 else STONE, rnd)
                glow = False
                if fy >= h - 2:
                    c = GOLD if fy == h - 2 else GOLD_D
                elif abs(fx - 2.5) + abs(fy - 3.5) <= 1.6:
                    c, glow = (RUNE_L if abs(fx - 2.5) + abs(fy - 3.5) < 0.8 else RUNE), True
                if fx == 0 or fx == w - 1:
                    c = darken(c, 0.25) if not glow else c
                t.set(x0 + fx, y0 + fy, c, glow)
    paint_plain(t, f["top"], rnd, STONE_L)
    paint_plain(t, f["bottom"], rnd, STONE_D)

    # 5) Capitel 7x2x7 em (0,53): ouro com sombra
    f = cube_uv(0, 53, 7, 2, 7)
    for side in f["sides"]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                t.set(x0 + fx, y0 + fy, jitter(GOLD_L if fy == 0 else GOLD, rnd, 0.05))
    x0, y0, w, h = f["top"]
    for fy in range(h):
        for fx in range(w):
            edge = fx in (0, w - 1) or fy in (0, h - 1)
            t.set(x0 + fx, y0 + fy, jitter(GOLD if edge else STONE_L, rnd, 0.05))
    paint_plain(t, f["bottom"], rnd, GOLD_D)

    # 6) Ponta 4x2x4 em (28,53)
    f = cube_uv(28, 53, 4, 2, 4)
    for side in f["sides"] + [f["top"], f["bottom"]]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                t.set(x0 + fx, y0 + fy, jitter(GOLD_D if fy == h - 1 else GOLD, rnd, 0.05))

    # 7) Cristal 3x4x3 em (48,17) + pontas 1x1x1 em (60,17) e (60,20): tudo brilha
    f = cube_uv(48, 17, 3, 4, 3)
    for k, side in enumerate(f["sides"] + [f["top"], f["bottom"]]):
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                tt = (fx + fy * 0.6) / max(1, w + h * 0.6)
                c = mix(CRYSTAL_L, CRYSTAL_D, min(1.0, tt + (0.15 if k % 2 else 0)))
                t.set(x0 + fx, y0 + fy, c, True)
    for (u, v) in ((60, 17), (60, 20)):
        f = cube_uv(u, v, 1, 1, 1)
        for side in f["sides"] + [f["top"], f["bottom"]]:
            x0, y0, w, h = side
            t.set(x0, y0, CRYSTAL_L, True)

    # 8) Fragmento 2x4x1 em (56,31): lasca de pedra com veio brilhante
    f = cube_uv(56, 31, 2, 4, 1)
    for side in f["sides"] + [f["top"], f["bottom"]]:
        x0, y0, w, h = side
        for fy in range(h):
            for fx in range(w):
                vein = (w >= 2 and fx == (fy % 2)) or (w == 1 and fy in (1, 2))
                if vein:
                    t.set(x0 + fx, y0 + fy, RUNE if fy % 3 else RUNE_L, True)
                else:
                    t.set(x0 + fx, y0 + fy, jitter(STONE_D, rnd))
    return t


# ----------------------------------------------------------------------------- modelo GeckoLib
def cube(origin, size, uv, pivot=None, rotation=None):
    c = {"origin": origin, "size": size, "uv": uv}
    if rotation:
        c["pivot"] = pivot
        c["rotation"] = rotation
    return c


def geo_model():
    shard = lambda: [cube([-1, 18, -8.5], [2, 4, 1], [56, 31])]
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
            cube([-7, 0, -7], [14, 3, 14], [0, 0]),
            cube([-6, 3, -6], [12, 2, 12], [0, 17]),
            cube([-4, 5, -4], [8, 14, 8], [0, 31]),
            cube([-3, 19, -3], [6, 9, 6], [32, 31]),
            cube([-3.5, 28, -3.5], [7, 2, 7], [0, 53]),
            cube([-2, 30, -2], [4, 2, 4], [28, 53]),
        ]},
        {"name": "crystal", "parent": "root", "pivot": [0, 36, 0], "cubes": [
            cube([-1.5, 34, -1.5], [3, 4, 3], [48, 17]),
            cube([-0.5, 38, -0.5], [1, 1, 1], [60, 17]),
            cube([-0.5, 33, -0.5], [1, 1, 1], [60, 20]),
        ]},
        {"name": "orbit", "parent": "root", "pivot": [0, 20, 0]},
        {"name": "shard_a", "parent": "orbit", "pivot": [0, 20, 0], "cubes": shard()},
        {"name": "shard_b", "parent": "orbit", "pivot": [0, 20, 0], "rotation": [0, 120, 0], "cubes": shard()},
        {"name": "shard_c", "parent": "orbit", "pivot": [0, 20, 0], "rotation": [0, 240, 0], "cubes": shard()},
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.travel_stone",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 3,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


def kf(points):
    return {f"{t:.2f}": v for t, v in points}


def animations():
    idle = {
        "loop": True,
        "animation_length": 8.0,
        "bones": {
            "crystal": {
                "rotation": kf([(0, [0, 0, 0]), (8, [0, 720, 0])]),
                "position": kf([(0, [0, 0, 0]), (2, [0, 1, 0]), (4, [0, 0, 0]), (6, [0, 1, 0]), (8, [0, 0, 0])]),
            },
            "orbit": {
                "rotation": kf([(0, [0, 0, 0]), (8, [0, -360, 0])]),
                "position": kf([(0, [0, 0, 0]), (4, [0, 1.5, 0]), (8, [0, 0, 0])]),
            },
        },
    }
    awake = {
        "loop": True,
        "animation_length": 2.0,
        "bones": {
            "crystal": {
                "rotation": kf([(0, [0, 0, 0]), (2, [0, 360, 0])]),
                "position": kf([(0, [0, 2, 0]), (0.5, [0, 3, 0]), (1, [0, 2, 0]), (1.5, [0, 3, 0]), (2, [0, 2, 0])]),
                "scale": kf([(0, [1.1, 1.1, 1.1]), (1, [1.25, 1.25, 1.25]), (2, [1.1, 1.1, 1.1])]),
            },
            "orbit": {
                "rotation": kf([(0, [0, 0, 0]), (2, [0, -360, 0])]),
                "position": kf([(0, [0, 3, 0]), (1, [0, 4.5, 0]), (2, [0, 3, 0])]),
                "scale": kf([(0, [1.15, 1, 1.15]), (2, [1.15, 1, 1.15])]),
            },
        },
    }
    flare = {
        "loop": False,
        "animation_length": 1.2,
        "bones": {
            "crystal": {
                "scale": kf([(0, [1, 1, 1]), (0.25, [2.2, 2.2, 2.2]), (0.6, [1.4, 1.4, 1.4]), (1.2, [1, 1, 1])]),
                "position": kf([(0, [0, 0, 0]), (0.25, [0, 3, 0]), (1.2, [0, 0, 0])]),
            },
            "orbit": {
                "scale": kf([(0, [1, 1, 1]), (0.3, [1.8, 1.3, 1.8]), (1.2, [1, 1, 1])]),
            },
        },
    }
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.travel_stone.idle": idle,
            "animation.travel_stone.awake": awake,
            "animation.travel_stone.flare": flare,
        },
    }


# ----------------------------------------------------------------------------- texturas 16x16 do item
def item_textures(rnd):
    side = Canvas(16, 16)
    rows = RUNES[0]
    for y in range(16):
        for x in range(16):
            c = jitter(STONE, rnd)
            if y % 5 == 4:
                c = darken(STONE, 0.2)
            if x in (0, 15):
                c = darken(c, 0.3)
            side.set(x, y, c)
    # runa central (a runa 8x14 reduzida no meio da face)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                side.set(4 + x, 1 + y, RUNE_L if x in (3, 4) else RUNE)
    side.save(os.path.join(ASSETS, "textures/block/travel_stone_side.png"))

    base = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = jitter(PLINTH, rnd)
            r = math.hypot(x - 7.5, y - 7.5)
            if 5.5 <= r < 6.6 and int((math.atan2(y - 7.5, x - 7.5) + math.pi) / (2 * math.pi) * 16) % 2 == 0:
                c = RUNE
            elif y % 4 == 3 or (x + (y // 4) * 4) % 8 == 7:
                c = darken(PLINTH, 0.3)
            base.set(x, y, c)
    base.save(os.path.join(ASSETS, "textures/block/travel_stone_base.png"))

    gold = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            gold.set(x, y, jitter(GOLD_L if y < 4 else GOLD if y < 12 else GOLD_D, rnd, 0.05))
    gold.save(os.path.join(ASSETS, "textures/block/travel_stone_gold.png"))

    crystal = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            crystal.set(x, y, mix(CRYSTAL_L, CRYSTAL_D, min(1.0, (x + y) / 26)))
    crystal.save(os.path.join(ASSETS, "textures/block/travel_stone_crystal.png"))


def faces(tex, uv=None, sides=("north", "south", "east", "west", "up", "down")):
    out = {}
    for s in sides:
        f = {"texture": tex}
        if uv:
            f["uv"] = uv
        out[s] = f
    return out


def item_model():
    elements = [
        {"from": [1, 0, 1], "to": [15, 2, 15], "faces": faces("#base")},
        {"from": [2, 2, 2], "to": [14, 3, 14], "faces": faces("#base")},
        {"from": [4.5, 3, 4.5], "to": [11.5, 12, 11.5],
         "faces": {**faces("#side", [4, 1, 12, 15], ("north", "south", "east", "west")), **faces("#base", None, ("up", "down"))}},
        {"from": [5, 12, 5], "to": [11, 14, 11], "faces": faces("#side", [5, 0, 11, 4])},
        {"from": [4.5, 14, 4.5], "to": [11.5, 15, 11.5], "faces": faces("#gold")},
        {"from": [6, 15, 6], "to": [10, 16, 10], "faces": faces("#gold")},
        {"from": [7, 16.5, 7], "to": [9, 19, 9], "rotation": {"angle": 45, "axis": "y", "origin": [8, 17.5, 8]},
         "faces": faces("#crystal")},
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "isekai_mahou:block/travel_stone_side",
            "side": "isekai_mahou:block/travel_stone_side",
            "base": "isekai_mahou:block/travel_stone_base",
            "gold": "isekai_mahou:block/travel_stone_gold",
            "crystal": "isekai_mahou:block/travel_stone_crystal",
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


# ----------------------------------------------------------------------------- dados
def data_files():
    write_json(os.path.join(ASSETS, "blockstates/travel_stone.json"),
               {"variants": {"": {"model": "isekai_mahou:block/travel_stone"}}})
    # O bloco é desenhado pelo GeckoLib (ENTITYBLOCK_ANIMATED): o modelo só fornece a partícula de quebra
    write_json(os.path.join(ASSETS, "models/block/travel_stone.json"),
               {"textures": {"particle": "isekai_mahou:block/travel_stone_side"}})
    write_json(os.path.join(ASSETS, "models/item/travel_stone.json"), item_model())

    write_json(os.path.join(DATA, "loot_table/blocks/travel_stone.json"), {
        "type": "minecraft:block",
        "random_sequence": "isekai_mahou:blocks/travel_stone",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:item",
                "name": "isekai_mahou:travel_stone",
                "functions": [{
                    "function": "minecraft:copy_components",
                    "source": "block_entity",
                    "include": ["minecraft:custom_name"],
                }],
            }],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })

    write_json(os.path.join(DATA, "recipe/travel_stone.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["CEC", "ADA", "SOS"],
        "key": {
            "C": {"item": "isekai_mahou:mana_crystal"},
            "E": {"item": "minecraft:ender_eye"},
            "A": {"item": "isekai_mahou:essence_arcane"},
            "D": {"item": "minecraft:diamond"},
            "S": {"item": "minecraft:chiseled_stone_bricks"},
            "O": {"item": "minecraft:crying_obsidian"},
        },
        "result": {"id": "isekai_mahou:travel_stone", "count": 1},
    })

    write_json(os.path.join(DATA, "advancement/recipes/travel_stone.json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_crystal": {"trigger": "minecraft:inventory_changed",
                            "conditions": {"items": [{"items": "isekai_mahou:mana_crystal"}]}},
            "has_eye": {"trigger": "minecraft:inventory_changed",
                        "conditions": {"items": [{"items": "minecraft:ender_eye"}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": "isekai_mahou:travel_stone"}},
        },
        "requirements": [["has_crystal", "has_eye", "has_the_recipe"]],
        "rewards": {"recipes": ["isekai_mahou:travel_stone"]},
    })

    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"values": ["isekai_mahou:travel_stone"]})
    # Imune ao Wither/Dragao e, pelo nucleo (DestructionRules), a magias destrutivas
    for tag in ("wither_immune", "dragon_immune"):
        write_json(os.path.join(MERGE, "data/minecraft/tags/block/%s.json" % tag),
                   {"values": ["isekai_mahou:travel_stone"]})


def main():
    rnd = random.Random(4207)
    t = paint_stone_texture(rnd)
    t.tex.save(os.path.join(ASSETS, "textures/block/travel_stone.png"))
    t.glow.save(os.path.join(ASSETS, "textures/block/travel_stone_glowmask.png"))
    write_json(os.path.join(ASSETS, "geo/block/travel_stone.geo.json"), geo_model())
    write_json(os.path.join(ASSETS, "animations/block/travel_stone.animation.json"), animations())
    item_textures(rnd)
    data_files()
    print("travel: assets gerados")


if __name__ == "__main__":
    main()
