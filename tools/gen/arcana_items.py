"""
Gera os assets do modulo arcana_items (v1.0).
Uso (na raiz do projeto):  py -I tools/gen/arcana_items.py

Saidas (16x16) em textures/item + models/item:
  - mana_necklace / greater_mana_necklace / grand_mana_necklace   corrente com pingente de cristal (3 niveis)
  - blank_recipe_scroll   pergaminho em branco com lacre roxo
  - recipe_scroll         pergaminho gravado (circulo runico roxo)
  - return_scroll         pergaminho com seta verde de retorno
  - alchemist_bag         bolsa de couro com fecho de cristal
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, item_model  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
OUT = hex_rgba("15101f")


def draw(name, rows, pal, noise=0.04, seed=1):
    assert len(rows) == 16, (name, len(rows))
    for r in rows:
        assert len(r) == 16, (name, r, len(r))
    c = Canvas(16, 16)
    c.from_rows(rows, pal)
    c.noise(noise, seed=seed)
    c.save(os.path.join(A, "textures", "item", name + ".png"))
    item_model(A, name)


# --------------------------------------------------------------------------------------------- colares
NECK = [
    "................",
    "..oo........oo..",
    ".occo......occo.",
    ".oco........oco.",
    ".oco........oco.",
    ".occo......occo.",
    "..oco......oco..",
    "..occo....occo..",
    "...occo..occo...",
    "....occoocco....",
    "......oGGo......",
    ".....oGWGgo.....",
    ".....oGGggo.....",
    "......oggo......",
    ".......oo.......",
    "................",
]
NECK_2 = list(NECK)
NECK_2[7] = "..ocgo....ogco.."
NECK_2[8] = "...ocGo..oGco..."
NECK_3 = [
    "................",
    "..oo........oo..",
    ".occo......occo.",
    ".oco........oco.",
    ".ogo........ogo.",
    ".occo......occo.",
    "..oco......oco..",
    "..ocgo....ogco..",
    "...ocGo..oGco...",
    "....occoocco....",
    ".....oGGGGo.....",
    "....oGWWGGgo....",
    "....oGWGGggo....",
    ".....oGGggo.....",
    "......oggo......",
    ".......oo.......",
]


def necklaces():
    silver = {"o": OUT, "c": hex_rgba("c8ccd6"), "g": hex_rgba("2f62d8"), "G": hex_rgba("6aa6ff"), "W": hex_rgba("e8f3ff")}
    gold = {"o": OUT, "c": hex_rgba("e2b23c"), "g": hex_rgba("5a3fd6"), "G": hex_rgba("a58cff"), "W": hex_rgba("f1eaff")}
    grand = {"o": OUT, "c": hex_rgba("f6d66a"), "g": hex_rgba("28a6c8"), "G": hex_rgba("8ff0ff"), "W": hex_rgba("ffffff")}
    draw("mana_necklace", NECK, silver, seed=3)
    draw("greater_mana_necklace", NECK_2, gold, seed=4)
    draw("grand_mana_necklace", NECK_3, grand, seed=5)


# --------------------------------------------------------------------------------------------- pergaminhos
def scroll_rows(body):
    assert len(body) == 8
    return [
        "................",
        "..oooooooooooo..",
        ".oLLLLLLLLLLLLo.",
        ".oDDDDDDDDDDDDo.",
    ] + ["..o" + b + "o.." for b in body] + [
        ".oLLLLLLLLLLLLo.",
        ".oDDDDDDDDDDDDo.",
        "..oooooooooooo..",
        "................",
    ]


def scrolls():
    base = {"o": OUT, "L": hex_rgba("f0e2b8"), "D": hex_rgba("b99c62"), "P": hex_rgba("e6d4a0"), "p": hex_rgba("cdb682")}
    blank = dict(base, s=hex_rgba("8a4fd8"), S=hex_rgba("c9a0ff"), l=hex_rgba("b8a274"))
    draw("blank_recipe_scroll", scroll_rows([
        "PPPPPPPPPP",
        "PllllllPPP",
        "PPPPPPPPPP",
        "PlllllPPPP",
        "PPPPPPPssP",
        "PllllPsSSs",
        "PPPPPPsSss",
        "ppppppPssp",
    ]), blank, seed=7)
    rune = dict(base, s=hex_rgba("7a3fd0"), S=hex_rgba("c9a0ff"), W=hex_rgba("f6efff"))
    draw("recipe_scroll", scroll_rows([
        "PPPssssPPP",
        "PPsSPPSsPP",
        "PsSPsWPSsP",
        "PsPsWWsPsP",
        "PsPsWWsPsP",
        "PsSPsWPSsP",
        "PPsSPPSsPP",
        "pppsssspPp",
    ]), rune, seed=8)
    ret = dict(base, g=hex_rgba("2f9a5c"), G=hex_rgba("7fe0a8"), W=hex_rgba("eafff2"))
    draw("return_scroll", scroll_rows([
        "PPPPPPPPPP",
        "PPPgPPPPPP",
        "PPgGgggPPP",
        "PgGGGGGgPP",
        "PPgGgggGgP",
        "PPPgPPgGgP",
        "PPPPPPgGgP",
        "pppppggGgp",
    ]), ret, seed=9)


# --------------------------------------------------------------------------------------------- bolsa
def bag():
    pal = {"o": OUT, "B": hex_rgba("8a5a34"), "L": hex_rgba("b98556"), "D": hex_rgba("5e3a20"), "T": hex_rgba("e2b23c"),
           "G": hex_rgba("8a4fd8"), "W": hex_rgba("e4d0ff")}
    draw("alchemist_bag", [
        "................",
        "......oooo......",
        ".....oBLLBo.....",
        "......oTTo......",
        ".....oTTTTo.....",
        "....oBBLLBBo....",
        "...oBBLLBBBBo...",
        "..oBBLBBBBBBDo..",
        "..oBLBBGGBBBDo..",
        "..oBLBGWGGBBDo..",
        "..oBBBGGGGBBDo..",
        "..oBBBBGGBBDDo..",
        "..oBBBBBBBDDDo..",
        "...oBBBBDDDDo...",
        "....oooooooo....",
        "................",
    ], pal, seed=11)


if __name__ == "__main__":
    necklaces()
    scrolls()
    bag()
    print("arcana_items: 7 texturas + modelos")
