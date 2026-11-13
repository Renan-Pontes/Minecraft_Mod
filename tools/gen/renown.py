"""
Gera os assets do modulo renown (Ranking, titulos e recordes de chefe).
Uso (na raiz do projeto):  py -I tools/gen/renown.py

Saidas:
  - textures/block/renown_board_front.png   frente do Quadro de Renome (pergaminho com trofeu e 3 linhas de ranking)
  - textures/block/renown_board_side.png    madeira escura com friso dourado
  - textures/gui/tab/renown.png             icone da aba do menu B (trofeu)
  - models/block/renown_board.json, blockstates/renown_board.json, models/item/renown_board.json
  - data: loot_table/blocks/renown_board.json, recipe/renown_board.json
  - merge: src/main/merge/renown/data/minecraft/tags/block/mineable/axe.json
"""
import json
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402

NS = "isekai_mahou"
A = os.path.join(ROOT, "src", "main", "resources", "assets", NS)
D = os.path.join(ROOT, "src", "main", "resources", "data", NS)
M = os.path.join(ROOT, "src", "main", "merge", "renown", "data")

OUT = hex_rgba("1a1208")
WOOD0 = hex_rgba("2e1f12")
WOOD1 = hex_rgba("47301b")
WOOD2 = hex_rgba("5e4126")
WOOD3 = hex_rgba("7a5733")
PAPER = hex_rgba("e9dcb8")
PAPER2 = hex_rgba("d6c69a")
INK = hex_rgba("5b4a36")
GOLD0 = hex_rgba("8a5a12")
GOLD1 = hex_rgba("d49a22")
GOLD2 = hex_rgba("ffd86b")
GOLD3 = hex_rgba("fff3b8")
SILVER = hex_rgba("c9d2dc")
BRONZE = hex_rgba("c0763a")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(c, rel):
    path = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    c.save(path)


def front():
    c = Canvas(16, 16)
    rnd = random.Random(7)
    # moldura de madeira escura com friso dourado por dentro
    for y in range(16):
        for x in range(16):
            base = WOOD1 if (x + y // 5) % 4 else WOOD2
            c.set(x, y, darken(base, rnd.random() * 0.12))
    for i in range(16):
        c.set(i, 0, WOOD3)
        c.set(0, i, WOOD3)
        c.set(i, 15, WOOD0)
        c.set(15, i, WOOD0)
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i), (i, 14), (14, i)):
            c.set(x, y, GOLD0)
    c.set(1, 1, GOLD2)
    c.set(14, 1, GOLD1)
    # pergaminho
    for y in range(2, 14):
        for x in range(2, 14):
            c.set(x, y, PAPER if rnd.random() > 0.18 else PAPER2)
    # trofeu no topo (a "manchete" do quadro)
    c.from_rows([
        "g.GGGG.g",
        "gGHGGGGg",
        ".gGHGGg.",
        "..GGGG..",
        "...gg...",
        "..gGGg..",
    ], {"g": GOLD0, "G": GOLD1, "H": GOLD3}, ox=4, oy=2)
    # tres linhas: medalha (ouro/prata/bronze) + nome + numero
    for k, medal in enumerate((GOLD2, SILVER, BRONZE)):
        yy = 9 + k * 2
        c.set(3, yy, medal)
        for x in range(5, 10 - k):
            c.set(x, yy, INK)
        c.set(12, yy, INK)
        c.set(11, yy, INK if k == 0 else PAPER2)
    save(c, "block/renown_board_front")


def side():
    c = Canvas(16, 16)
    rnd = random.Random(21)
    for y in range(16):
        for x in range(16):
            base = WOOD1 if x % 5 else WOOD0
            c.set(x, y, darken(base, rnd.random() * 0.14))
    for x in range(16):
        c.set(x, 0, lighten(WOOD3, 0.05))
        c.set(x, 15, WOOD0)
    for x in range(0, 16, 4):
        c.set(x + 1, 7, GOLD0)
        c.set(x + 2, 7, GOLD1)
    save(c, "block/renown_board_side")


def tab_icon():
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "..oooooooooooo..",
        ".ogoGGHGGGGGogo.",
        ".ogoGHHGGGGGogo.",
        ".ogoGHGGGGGGogo.",
        "..ogoGGGGGGogo..",
        "...ooGGGGGGoo...",
        ".....oGGGGo.....",
        "......oGGo......",
        "......oGGo......",
        ".....oogGoo.....",
        "....ogGGGGgo....",
        "....oggggggo....",
        "...oGGGHGGGGo...",
        "...oooooooooo...",
        "................",
    ], {"o": OUT, "g": GOLD0, "G": GOLD1, "H": GOLD3})
    save(c, "gui/tab/renown")


def faces_all(tex, uv=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    return {d: dict(f) for d in ("north", "south", "east", "west", "up", "down")}


def models():
    write_json(os.path.join(A, "models", "block", "renown_board.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/renown_board_side",
            "front": f"{NS}:block/renown_board_front",
            "side": f"{NS}:block/renown_board_side",
        },
        "elements": [
            # postes
            {"from": [1, 0, 6.5], "to": [3, 16, 9.5], "faces": faces_all("#side", [0, 0, 3, 16])},
            {"from": [13, 0, 6.5], "to": [15, 16, 9.5], "faces": faces_all("#side", [0, 0, 3, 16])},
            # painel (frente = norte)
            {"from": [0, 2, 7], "to": [16, 16, 9], "faces": {
                "north": {"texture": "#front", "uv": [0, 1, 16, 15]},
                "south": {"texture": "#side", "uv": [0, 0, 16, 14]},
                "east": {"texture": "#side", "uv": [7, 0, 9, 14]},
                "west": {"texture": "#side", "uv": [7, 0, 9, 14]},
                "up": {"texture": "#side", "uv": [0, 7, 16, 9]},
                "down": {"texture": "#side", "uv": [0, 7, 16, 9]}}},
            # cimalha
            {"from": [-0.5, 15.5, 6], "to": [16.5, 17, 10], "faces": faces_all("#side", [0, 5, 16, 9])},
        ],
    })
    write_json(os.path.join(A, "blockstates", "renown_board.json"), {"variants": {
        "facing=north": {"model": f"{NS}:block/renown_board"},
        "facing=east": {"model": f"{NS}:block/renown_board", "y": 90},
        "facing=south": {"model": f"{NS}:block/renown_board", "y": 180},
        "facing=west": {"model": f"{NS}:block/renown_board", "y": 270},
    }})
    write_json(os.path.join(A, "models", "item", "renown_board.json"), {"parent": f"{NS}:block/renown_board"})


def data():
    write_json(os.path.join(D, "loot_table", "blocks", "renown_board.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:renown_board"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{NS}:blocks/renown_board",
    })
    write_json(os.path.join(D, "recipe", "renown_board.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["PGP", "WWW", "S S"],
        "key": {
            "P": {"item": "minecraft:paper"},
            "G": {"item": "minecraft:gold_ingot"},
            "W": {"tag": "minecraft:planks"},
            "S": {"item": "minecraft:stick"},
        },
        "result": {"id": f"{NS}:renown_board", "count": 1},
    })
    write_json(os.path.join(M, "minecraft", "tags", "block", "mineable", "axe.json"), {"values": [f"{NS}:renown_board"]})


if __name__ == "__main__":
    front()
    side()
    tab_icon()
    models()
    data()
    print("renown: assets gerados")
