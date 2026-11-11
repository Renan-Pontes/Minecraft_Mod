"""
Gera a folha de sprites da UI do núcleo: assets/isekai_mahou/textures/gui/core_ui.png (64x32).

Mapa (u, v, w, h) — espelhado em client/MahouUi.java:
    seta para cima (branca, tingível) .... (0, 0, 16, 16)
    canto sup. esquerdo .................. (16, 0, 8, 8)
    canto sup. direito ................... (24, 0, 8, 8)
    canto inf. esquerdo .................. (16, 8, 8, 8)
    canto inf. direito ................... (24, 8, 8, 8)
    gema em losango ...................... (32, 0, 7, 7)
    estrela de 4 pontas .................. (40, 0, 7, 7)

Uso:  py -I tools/gen/core_ui.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "gui", "core_ui.png")

PAL = {
    "D": hex_rgba("07050C"),   # contorno escuro
    "G": hex_rgba("FFD86B"),   # ouro
    "g": hex_rgba("8A7038"),   # ouro escuro
    "W": hex_rgba("FFF2B0"),   # brilho
    "O": hex_rgba("B98CFF"),   # gema arcana
    "o": hex_rgba("6A4BA0"),   # gema escura
    "w": (255, 255, 255, 255),  # seta: branco (tingido no jogo)
    "s": (205, 205, 215, 255),  # seta: sombra clara
    "k": (40, 34, 52, 255),     # seta: contorno
}

ARROW = [
    ".......kk.......",
    "......kwwk......",
    ".....kwwwsk.....",
    "....kwwwwssk....",
    "...kwwwwwsssk...",
    "..kwwwwwwssssk..",
    ".kwwwwwwwsssssk.",
    "kkkkkwwwssskkkkk",
    "....kwwwssk.....",
    "....kwwwssk.....",
    "....kwwwssk.....",
    "....kwwwssk.....",
    "....kwwwssk.....",
    "....kwwwssk.....",
    "....kkkkkkk.....",
    "................",
]

CORNER_TL = [
    "DDDDDDD.",
    "DWGGGGD.",
    "DGOOgD..",
    "DGOgD...",
    "DGgD....",
    "DGD.....",
    "DD......",
    "........",
]

GEM = [
    "...D...",
    "..DGD..",
    ".DGOOD.",
    "DGOWoGD",
    ".DgooD.",
    "..DgD..",
    "...D...",
]

STAR = [
    "...G...",
    "...W...",
    "..GWG..",
    "GWWWWWG",
    "..GWG..",
    "...W...",
    "...G...",
]


def mirror_h(rows):
    return [r[::-1] for r in rows]


def mirror_v(rows):
    return list(reversed(rows))


def main():
    c = Canvas(64, 32)
    c.from_rows(ARROW, PAL, 0, 0)
    c.from_rows(CORNER_TL, PAL, 16, 0)
    c.from_rows(mirror_h(CORNER_TL), PAL, 24, 0)
    c.from_rows(mirror_v(CORNER_TL), PAL, 16, 8)
    c.from_rows(mirror_v(mirror_h(CORNER_TL)), PAL, 24, 8)
    c.from_rows(GEM, PAL, 32, 0)
    c.from_rows(STAR, PAL, 40, 0)
    c.save(OUT)
    print("gerado:", OUT)


if __name__ == "__main__":
    main()
