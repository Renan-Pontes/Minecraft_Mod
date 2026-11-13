"""
Gera os assets do modulo gear_plus (gemas com encaixe, cinzel, aba "Equipamento").
Uso (na raiz do projeto):  py -I tools/gen/gear_plus.py

Saidas:
  - textures/item/gem_<tipo>[_chipped|_flawless].png   18 gemas (6 tipos x 3 lapidacoes), 16x16
  - textures/item/gem_chisel.png                       Cinzel de Lapidario
  - textures/gui/tab/gear_plus.png                     icone da aba do menu B (peitoral com uma gema)
  - models/item/<itens>.json
  - data/isekai_mahou/recipe/gear_plus_*.json          3 lascadas -> lapidada; 3 lapidadas + Estilhaco do Abismo -> perfeita; cinzel
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, darken, hex_rgba, item_model, lighten  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
D = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")

# tipo -> cor base (igual a GemType.color)
GEMS = {
    "ruby": "e0304a",
    "sapphire": "3a78f0",
    "emerald": "2fcb6a",
    "topaz": "f2b63a",
    "amethyst": "a65cf0",
    "onyx": "4a4460",
}

CHIPPED = [
    "................",
    "................",
    "................",
    "................",
    "......oo........",
    ".....olmo.......",
    "....olmmdo......",
    "....ommmdo.oo...",
    ".....omddoomo...",
    "......oddomdo...",
    ".......oo.odo...",
    "...........o....",
    "................",
    "................",
    "................",
    "................",
]

CUT = [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...olllwlmmdo...",
    "..olwllmmmmddo..",
    "..ommmmmmmdddo..",
    "...omlmmmdddo...",
    "....omlmmddo....",
    ".....omlmdo.....",
    "......ommo......",
    ".......oo.......",
    "................",
    "................",
    "................",
    "................",
]

FLAWLESS = [
    "..s.............",
    ".sss....oo......",
    "..s...oollmo....",
    ".....olwwlmmo...",
    "....olwwllmmdo..",
    "...ollwllmmmddo.",
    "...olllmmmmdddo.",
    "...ommmmmmddddo.",
    "....ommmmdddko..",
    ".....ommdddko...",
    "......omddko..s.",
    ".......odko..sss",
    "........oo....s.",
    "................",
    "................",
    "................",
]

CHISEL = [
    "................",
    "............oo..",
    "...........osso.",
    "..........ossto.",
    ".........ossto..",
    "........ogtto...",
    ".......obgo.....",
    "......obbo......",
    ".....obho.......",
    "....obho........",
    "...obho.........",
    "..obho..........",
    "..oho...........",
    "...o............",
    "................",
    "................",
]

TAB = [
    "................",
    "..oo......oo....",
    ".oaaoooooaaao...",
    ".oaaallllaaao...",
    ".oaoalllloaao...",
    "..o.oallao.o....",
    "....oaggao......",
    "....oagwgo......",
    "....oaggao......",
    "....oaaaao......",
    "....oaddao......",
    "....oddddo......",
    ".....oooo.......",
    "................",
    "................",
    "................",
]


def check(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    return rows


def gem_palette(base_hex):
    base = hex_rgba(base_hex)
    return {
        "o": darken(base, 0.72),
        "k": darken(base, 0.55),
        "d": darken(base, 0.32),
        "m": base,
        "l": lighten(base, 0.38),
        "w": lighten(base, 0.8),
        "s": hex_rgba("fff7d6", 235),
    }


def gems():
    for name, color in GEMS.items():
        pal = gem_palette(color)
        for suffix, rows in (("_chipped", CHIPPED), ("", CUT), ("_flawless", FLAWLESS)):
            item = "gem_" + name + suffix
            c = Canvas(16, 16)
            c.from_rows(check(rows), pal)
            c.save(os.path.join(A, "textures", "item", item + ".png"))
            item_model(A, item)


def chisel():
    pal = {
        "o": hex_rgba("1b1512"),
        "s": hex_rgba("c9d2dc"),   # aco claro
        "t": hex_rgba("8893a3"),   # aco sombra
        "g": hex_rgba("e7b84a"),   # anel dourado
        "b": hex_rgba("7a4a26"),   # cabo
        "h": hex_rgba("a8693a"),   # brilho do cabo
    }
    c = Canvas(16, 16)
    c.from_rows(check(CHISEL), pal)
    c.save(os.path.join(A, "textures", "item", "gem_chisel.png"))
    item_model(A, "gem_chisel", parent="minecraft:item/handheld")


def tab_icon():
    pal = {
        "o": hex_rgba("0f0c18"),
        "a": hex_rgba("8c93ad"),   # placa
        "l": hex_rgba("c7cde0"),   # brilho da placa
        "d": hex_rgba("5a607a"),   # sombra
        "g": hex_rgba("e0304a"),   # gema
        "w": hex_rgba("ffb0bd"),
    }
    c = Canvas(16, 16)
    c.from_rows(check(TAB), pal)
    c.save(os.path.join(A, "textures", "gui", "tab", "gear_plus.png"))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def recipes():
    for name in GEMS:
        chipped = "isekai_mahou:gem_%s_chipped" % name
        cut = "isekai_mahou:gem_%s" % name
        flawless = "isekai_mahou:gem_%s_flawless" % name
        write_json(os.path.join(D, "recipe", "gear_plus_gem_%s.json" % name), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": chipped}] * 3,
            "result": {"id": cut, "count": 1},
        })
        write_json(os.path.join(D, "recipe", "gear_plus_gem_%s_flawless.json" % name), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": cut}] * 3 + [{"item": "isekai_mahou:abyss_shard"}],
            "result": {"id": flawless, "count": 1},
        })
    write_json(os.path.join(D, "recipe", "gear_plus_gem_chisel.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["F", "I", "S"],
        "key": {
            "F": {"item": "minecraft:flint"},
            "I": {"item": "minecraft:iron_ingot"},
            "S": {"item": "minecraft:stick"},
        },
        "result": {"id": "isekai_mahou:gem_chisel", "count": 1},
    })


if __name__ == "__main__":
    gems()
    chisel()
    tab_icon()
    recipes()
    print("gear_plus: assets gerados")
