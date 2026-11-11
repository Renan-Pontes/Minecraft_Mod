"""
Texturas de GUI do modulo "cinematic" (anuncios e cutscenes).
Todas sao brancas/pretas com alpha para serem TINGIDAS no codigo (GuiGraphics.setColor):
  - cinematic_vignette.png : escurecimento radial suave das bordas (cutscenes)
  - cinematic_glow.png     : brilho eliptico suave (atras dos titulos / anuncios de ultimate)
  - cinematic_corner.png   : ornamento de canto (moldura das falas de chefe)
  - cinematic_diamond.png  : losango decorativo (pontas das linhas dos anuncios)
Rodar a partir da raiz do projeto:  py -I tools/gen/cinematic.py
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

OUT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui")


def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def vignette():
    w = h = 256
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            nx = (x + 0.5) / w * 2 - 1
            ny = (y + 0.5) / h * 2 - 1
            # elipse um pouco mais larga: as laterais escurecem menos que os cantos
            d = math.sqrt(nx * nx * 0.8 + ny * ny * 1.05)
            a = smoothstep(0.42, 1.32, d) ** 1.35
            c.set(x, y, (6, 4, 12, int(round(235 * a))))
    c.save(os.path.join(OUT, "cinematic_vignette.png"))


def glow():
    w, h = 128, 64
    c = Canvas(w, h)
    edge = math.exp(-4.2)
    for y in range(h):
        for x in range(w):
            nx = (x + 0.5) / w * 2 - 1
            ny = (y + 0.5) / h * 2 - 1
            d2 = nx * nx + ny * ny
            g = (math.exp(-4.2 * d2) - edge) / (1 - edge)
            # nucleo levemente mais forte para dar "bloom"
            g = max(0.0, g) ** 1.25
            c.set(x, y, (255, 255, 255, int(round(255 * g))))
    c.save(os.path.join(OUT, "cinematic_glow.png"))


def corner():
    rows = [
        "##############..",
        "#++++++++++###..",
        "#+##########....",
        "#+#.............",
        "#+#..##.........",
        "#+#.#++#........",
        "#+#.#++#........",
        "#+#..##.........",
        "#+#.............",
        "#+#.............",
        "#+#.............",
        "##..............",
        "##..............",
        "#...............",
        "................",
        "................",
    ]
    pal = {"#": (255, 255, 255, 255), "+": (255, 255, 255, 140)}
    c = Canvas(16, 16)
    c.from_rows(rows, pal)
    c.save(os.path.join(OUT, "cinematic_corner.png"))


def diamond():
    rows = [
        "....#....",
        "...#+#...",
        "..#+++#..",
        ".#++#++#.",
        "#++#.#++#",
        ".#++#++#.",
        "..#+++#..",
        "...#+#...",
        "....#....",
    ]
    pal = {"#": (255, 255, 255, 255), "+": (255, 255, 255, 120)}
    c = Canvas(9, 9)
    c.from_rows(rows, pal)
    c.save(os.path.join(OUT, "cinematic_diamond.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    vignette()
    glow()
    corner()
    diamond()
    print("texturas do cinematic geradas em", OUT)
