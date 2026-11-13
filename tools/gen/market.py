"""
Gera os assets do modulo "market" (release 1.0 - Mercado entre jogadores).

    py -I tools/gen/market.py

Saida:
  - textures/gui/tab/market.png               icone da aba do menu B (bolsa de moedas com etiqueta)
  - textures/block/market_board_front.png     painel do Quadro do Mercado (moeda pintada + linhas de anuncio)
  - textures/block/market_board_side.png      madeira escura dos postes/verso
  - textures/block/market_board_awning.png    toldo listrado (vermelho e creme) de barraca de feira
  - textures/block/market_board_tag.png       duas etiquetas de preco
  - blockstates/market_board.json, models/block/market_board.json, models/item/market_board.json
  - data: loot_table/blocks/market_board.json, recipe/market_board.json
  - merge: data/minecraft/tags/block/mineable/axe.json
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
D = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")
M = os.path.join(ROOT, "src", "main", "merge", "market", "data")

OUT = hex_rgba("1a1208")
WOOD_D = hex_rgba("3d2812")
WOOD = hex_rgba("5c3d1e")
WOOD_L = hex_rgba("7a5630")
PLANK = hex_rgba("8c6a3c")
PLANK_L = hex_rgba("a5824e")
GOLD_D = hex_rgba("9a6a12")
GOLD = hex_rgba("e2b33a")
GOLD_L = hex_rgba("ffe38a")
CREAM = hex_rgba("efe3c2")
CREAM_D = hex_rgba("cdbd94")
RED = hex_rgba("b8322c")
RED_D = hex_rgba("8a2220")
RED_L = hex_rgba("d9544a")
INK = hex_rgba("4a3a2a")
LEATHER_D = hex_rgba("5a3418")
LEATHER = hex_rgba("8a5626")
LEATHER_L = hex_rgba("b07a3c")


def tab_icon():
    """Bolsa de moedas com uma moeda saindo e uma etiqueta de preco."""
    c = Canvas(16, 16)
    pal = {"o": OUT, "d": LEATHER_D, "m": LEATHER, "l": LEATHER_L, "g": GOLD, "h": GOLD_L, "k": GOLD_D,
           "c": CREAM, "e": CREAM_D, "r": RED}
    rows = [
        "................",
        "......kgg.......",
        ".....kghhg......",
        ".....kgghg..cc..",
        "....dmkggmd.cec.",
        ".....drrrd..cc..",
        "....dmlllmd.r...",
        "...dmlllllmd....",
        "..dmllmmmllmd...",
        "..dmlmkgkmlmd...",
        "..dmlmgghmlmd...",
        "..dmlmkgkmlmd...",
        "..dmmlmmmlmmd...",
        "...dmmmmmmmd....",
        "....ddddddd.....",
        "................",
    ]
    c.from_rows(rows, pal)
    c.outline(OUT)
    c.save(os.path.join(A, "textures", "gui", "tab", "market.png"))


def wood_side():
    c = Canvas(16, 16, WOOD)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y) % 7 == 0:
                c.set(x, y, WOOD_D)
            elif (x + y * 5) % 11 == 0:
                c.set(x, y, WOOD_L)
    for x in (0, 15):
        for y in range(16):
            c.set(x, y, WOOD_D)
    c.noise(0.06, seed=11)
    c.save(os.path.join(A, "textures", "block", "market_board_side.png"))


def front():
    """Painel 16x12 util (a face usa uv 0,0-16,12): moldura escura, tabuas claras, moeda pintada e linhas de anuncio."""
    c = Canvas(16, 16, PLANK)
    for y in range(16):
        for x in range(16):
            if y % 4 == 3:
                c.set(x, y, darken(PLANK, 0.22))
            elif (x * 5 + y * 3) % 13 == 0:
                c.set(x, y, PLANK_L)
    c.outline_rect(0, 0, 15, 11, WOOD_D)
    # moeda pintada no meio-esquerda
    c.circle(4.5, 5.5, 2.6, GOLD)
    c.set(4, 4, GOLD_L)
    c.set(5, 4, GOLD_L)
    c.set(4, 7, GOLD_D)
    c.set(5, 7, GOLD_D)
    c.set(6, 6, GOLD_D)
    c.set(4, 5, GOLD_D)
    c.set(5, 6, GOLD_D)
    # linhas de anuncio (tinta) a direita
    for (y, x1) in ((3, 13), (5, 12), (7, 13), (9, 11)):
        for x in range(9, x1 + 1):
            c.set(x, y, INK)
    c.set(13, 5, GOLD)
    c.set(12, 9, GOLD)
    # parte de baixo (fora do uv da face) fica madeira
    c.rect(0, 12, 15, 15, WOOD)
    c.noise(0.05, seed=5)
    c.save(os.path.join(A, "textures", "block", "market_board_front.png"))


def awning():
    c = Canvas(16, 16)
    for x in range(16):
        stripe = (x // 2) % 2 == 0
        base = RED if stripe else CREAM
        for y in range(16):
            col = base
            if y == 0:
                col = lighten(base, 0.18)
            elif y >= 14:
                col = darken(base, 0.22)
            c.set(x, y, col)
        if stripe:
            c.set(x, 7, RED_L if x % 2 == 0 else RED)
            c.set(x, 13, RED_D)
        else:
            c.set(x, 13, CREAM_D)
    c.noise(0.04, seed=3)
    c.save(os.path.join(A, "textures", "block", "market_board_awning.png"))


def tags():
    """Duas etiquetas (uv 0,0-8,10 e 8,6-16,16): papel creme, furo, preco em dourado."""
    c = Canvas(16, 16)
    for (ox, oy) in ((0, 0), (8, 6)):
        c.rect(ox, oy, ox + 7, oy + 9, CREAM)
        c.outline_rect(ox, oy, ox + 7, oy + 9, CREAM_D)
        c.set(ox + 3, oy + 1, INK)
        c.set(ox + 4, oy + 1, INK)
        for x in range(ox + 2, ox + 6):
            c.set(x, oy + 4, INK)
        c.set(ox + 2, oy + 6, GOLD_D)
        c.set(ox + 3, oy + 6, GOLD)
        c.set(ox + 4, oy + 6, GOLD)
        c.set(ox + 5, oy + 7, GOLD_D)
        c.set(ox + 3, oy + 7, GOLD_D)
    c.save(os.path.join(A, "textures", "block", "market_board_tag.png"))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def all_faces(tex, uv=None):
    face = {"texture": tex}
    if uv:
        face["uv"] = uv
    return {d: dict(face) for d in ("north", "south", "east", "west", "up", "down")}


def jsons():
    write_json(os.path.join(A, "blockstates", "market_board.json"), {"variants": {
        "facing=north": {"model": "isekai_mahou:block/market_board"},
        "facing=east": {"model": "isekai_mahou:block/market_board", "y": 90},
        "facing=south": {"model": "isekai_mahou:block/market_board", "y": 180},
        "facing=west": {"model": "isekai_mahou:block/market_board", "y": 270},
    }})
    panel = all_faces("#side", [0, 0, 16, 12])
    panel["north"] = {"texture": "#front", "uv": [0, 0, 16, 12]}
    panel["east"] = {"texture": "#side", "uv": [7, 0, 9, 12]}
    panel["west"] = {"texture": "#side", "uv": [7, 0, 9, 12]}
    panel["up"] = {"texture": "#side", "uv": [0, 7, 16, 9]}
    panel["down"] = {"texture": "#side", "uv": [0, 7, 16, 9]}
    awn = all_faces("#awning", [0, 0, 16, 3])
    awn["up"] = {"texture": "#awning", "uv": [0, 0, 16, 6]}
    awn["down"] = {"texture": "#awning", "uv": [0, 8, 16, 14]}
    awn["east"] = {"texture": "#awning", "uv": [0, 0, 6, 3]}
    awn["west"] = {"texture": "#awning", "uv": [0, 0, 6, 3]}

    def tag(frm, to, u0, v0):
        return {"from": frm, "to": to, "faces": {
            "north": {"texture": "#tag", "uv": [u0, v0, u0 + 8, v0 + 10]},
            "east": {"texture": "#tag", "uv": [u0, v0, u0 + 1, v0 + 10]},
            "west": {"texture": "#tag", "uv": [u0, v0, u0 + 1, v0 + 10]},
            "up": {"texture": "#tag", "uv": [u0, v0, u0 + 8, v0 + 1]},
            "down": {"texture": "#tag", "uv": [u0, v0, u0 + 8, v0 + 1]},
        }}

    write_json(os.path.join(A, "models", "block", "market_board.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "isekai_mahou:block/market_board_side",
            "front": "isekai_mahou:block/market_board_front",
            "side": "isekai_mahou:block/market_board_side",
            "awning": "isekai_mahou:block/market_board_awning",
            "tag": "isekai_mahou:block/market_board_tag",
        },
        "elements": [
            {"from": [1, 0, 6.5], "to": [3, 16, 9.5], "faces": all_faces("#side")},
            {"from": [13, 0, 6.5], "to": [15, 16, 9.5], "faces": all_faces("#side")},
            {"from": [0, 4, 7], "to": [16, 15, 9], "faces": panel},
            # toldo de barraca por cima (um pouco para a frente)
            {"from": [-0.5, 14.5, 4], "to": [16.5, 16.5, 10], "faces": awn},
            tag([2.5, 6, 6.6], [5.5, 10, 7], 0, 0),
            tag([11, 5, 6.6], [14, 9, 7], 8, 6),
        ],
    })
    write_json(os.path.join(A, "models", "item", "market_board.json"), {"parent": "isekai_mahou:block/market_board"})
    write_json(os.path.join(D, "loot_table", "blocks", "market_board.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "isekai_mahou:market_board"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "isekai_mahou:blocks/market_board",
    })
    write_json(os.path.join(D, "recipe", "market_board.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["NPN", "WWW", "S S"],
        "key": {
            "N": {"item": "minecraft:gold_nugget"},
            "P": {"item": "minecraft:paper"},
            "W": {"tag": "minecraft:planks"},
            "S": {"item": "minecraft:stick"},
        },
        "result": {"id": "isekai_mahou:market_board", "count": 1},
    })
    write_json(os.path.join(M, "minecraft", "tags", "block", "mineable", "axe.json"), {"values": ["isekai_mahou:market_board"]})


def main():
    tab_icon()
    wood_side()
    front()
    awning()
    tags()
    jsons()
    print("market: 5 texturas, 3 modelos/blockstate, loot, receita, tag")


if __name__ == "__main__":
    main()
