"""
Gera os assets do modulo home_realm (Dimensao Casa).
Uso (na raiz do projeto):  py -I tools/gen/home_realm.py

Saidas (16x16):
  - textures/item/home_key.png        Chave do Lar (chave dourada com cabeca em forma de casinha e gema azul-ceu)
  - textures/gui/tab/home_realm.png   icone da secao "Lar" do menu B (casinha numa ilha flutuante)
  - models/item/home_key.json
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, item_model  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")

OUT = hex_rgba("2a1a08")      # contorno
GOLD_D = hex_rgba("9a6a14")
GOLD = hex_rgba("e0a82e")
GOLD_L = hex_rgba("ffd86b")
SHINE = hex_rgba("fff4c2")
SKY_D = hex_rgba("3a86c8")
SKY = hex_rgba("8fd8ff")
WHITE = hex_rgba("f4fbff")
ROOF_D = hex_rgba("7a2e22")
ROOF = hex_rgba("c2513a")
WALL_D = hex_rgba("b89a6a")
WALL = hex_rgba("f0deb0")
DOOR = hex_rgba("6a4424")
GRASS_D = hex_rgba("3f8f3a")
GRASS = hex_rgba("6cc24a")
DIRT = hex_rgba("7a5634")
STONE_D = hex_rgba("4a4f5a")
STONE = hex_rgba("7c8492")
INK = hex_rgba("10161c")

KEY = {"o": OUT, "d": GOLD_D, "g": GOLD, "l": GOLD_L, "s": SHINE, "b": SKY_D, "c": SKY, "w": WHITE}
TAB = {"o": INK, "r": ROOF, "R": ROOF_D, "w": WALL, "W": WALL_D, "d": DOOR, "c": SKY, "g": GRASS, "G": GRASS_D,
       "t": DIRT, "s": STONE, "S": STONE_D}


def home_key():
    c = Canvas(16, 16)
    # Chave na diagonal: cabeca-casinha em cima a direita, dentes em baixo a esquerda
    c.from_rows([
        "................",
        "..........oo....",
        ".........olgo...",
        "........olsggo..",
        ".......olgccgdo.",
        ".......ogcwbgdo.",
        ".......ogcbbgdo.",
        "......oogggggdo.",
        ".....olgoddddo..",
        "....olgdoooo....",
        "...olgdo........",
        "..olgdo.........",
        ".olgdgo.........",
        ".ogdodo.........",
        "..oo.o..........",
        "................",
    ], KEY)
    c.save(os.path.join(A, "textures", "item", "home_key.png"))
    item_model(A, "home_key")


def tab_icon():
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        ".......oo.......",
        "......orro......",
        ".....orrrro.....",
        "....orrrrrro....",
        "...oRRRRRRRRo...",
        "....owwwwwwo....",
        "....owcwddwo....",
        "....oWWWddWo....",
        ".oooggggggggooo.",
        ".oGgggggggggGGo.",
        "..ottttttttto...",
        "...osssssSSo....",
        "....osssSSo.....",
        "......oSSo......",
        ".......oo.......",
    ], TAB)
    c.save(os.path.join(A, "textures", "gui", "tab", "home_realm.png"))


if __name__ == "__main__":
    home_key()
    tab_icon()
    print("home_realm: assets gerados")
