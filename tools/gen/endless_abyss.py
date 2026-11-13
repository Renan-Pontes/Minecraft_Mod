"""
Gera os assets do modulo endless_abyss (Abismo Infinito / Lutas das Sombras).
Uso (na raiz do projeto):  py -I tools/gen/endless_abyss.py

Saidas (16x16):
  - textures/item/abyss_shard.png       Estilhaco do Abismo (lasca de cristal roxo-escuro)
  - textures/item/abyss_core.png        Nucleo do Abismo (esfera negra com um olho de luz roxa)
  - textures/item/shadow_fragment.png   Fragmento de Sombra (pedaco de sombra com fumaca)
  - textures/gui/tab/endless_abyss.png  icone da aba do menu B (espiral descendo)
  - models/item/<os tres itens>.json
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, item_model  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")

OUT = hex_rgba("0b0712")      # contorno
DEEP = hex_rgba("1a1030")     # roxo quase preto
DARK = hex_rgba("2d1a55")
MID = hex_rgba("5a34a8")
LIGHT = hex_rgba("9a5cff")
GLOW = hex_rgba("d8bcff")
WHITE = hex_rgba("f6efff")
SMOKE = hex_rgba("3a3350", 190)
SMOKE_LO = hex_rgba("221d33", 150)

PAL = {"o": OUT, "d": DEEP, "k": DARK, "m": MID, "l": LIGHT, "g": GLOW, "w": WHITE, "s": SMOKE, "t": SMOKE_LO}


def shard():
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "..........oo....",
        ".........olgo...",
        "........olwlo...",
        ".......omlglo...",
        "......okmllmo...",
        ".....odkmlmko...",
        "....odkmmlmo....",
        "....okmmlmko....",
        "...odkmlmkdo....",
        "...okmlmkdo.....",
        "..odkmmkdo......",
        "..okmkddo.......",
        "..odkdoo........",
        "...ooo..........",
        "................",
    ], PAL)
    c.noise(0.05, seed=11)
    c.save(os.path.join(A, "textures", "item", "abyss_shard.png"))


def core():
    c = Canvas(16, 16)
    c.circle(8, 8, 6.2, DEEP)
    c.shade_sphere(8, 8, 6.2, light=0.25, dark=0.55)
    # anel de luz e o "olho" no centro
    for (x, y) in ((5, 4), (6, 3), (9, 3), (10, 4), (12, 6), (12, 9), (10, 12), (9, 12), (6, 12), (5, 11), (3, 9), (3, 6)):
        c.set(x, y, MID)
    c.from_rows([
        ".kmk.",
        "kmlmk",
        "mlwlm",
        "kmlmk",
        ".kmk.",
    ], PAL, ox=5, oy=5)
    c.set(6, 5, LIGHT)
    c.set(5, 4, GLOW)
    c.outline(OUT)
    c.save(os.path.join(A, "textures", "item", "abyss_core.png"))


def fragment():
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        ".....t....s.....",
        "....s..t...t....",
        "......oo..s.....",
        ".....odko.......",
        "....odkmko..t...",
        "...odkmlkdo.....",
        "...okmlgmko.....",
        "..odkmllmkdo....",
        "..okdmlmkddo....",
        "...odkmkkdo.....",
        "....odkkdo..s...",
        "..t..oddo.......",
        "......oo...t....",
        "....s...........",
        "................",
    ], PAL)
    c.noise(0.05, seed=23)
    c.save(os.path.join(A, "textures", "item", "shadow_fragment.png"))


def tab_icon():
    c = Canvas(16, 16)
    # poco visto de cima: aneis cada vez mais escuros e um ponto de luz no fundo
    c.circle(8, 8, 7.2, MID)
    c.circle(8, 8, 5.6, DARK)
    c.circle(8, 8, 4.0, DEEP)
    c.circle(8, 8, 2.4, OUT)
    c.set(7, 7, LIGHT)
    c.set(8, 8, GLOW)
    # degraus da espiral
    for (x, y) in ((3, 5), (4, 4), (11, 3), (12, 4), (12, 11), (11, 12), (4, 12), (3, 11)):
        c.set(x, y, LIGHT)
    for (x, y) in ((5, 6), (10, 5), (10, 10), (5, 10)):
        c.set(x, y, MID)
    c.outline(OUT)
    c.save(os.path.join(A, "textures", "gui", "tab", "endless_abyss.png"))


def main():
    shard()
    core()
    fragment()
    tab_icon()
    for item in ("abyss_shard", "abyss_core", "shadow_fragment"):
        item_model(A, item)
    print("endless_abyss: 4 texturas, 3 modelos")


if __name__ == "__main__":
    main()
