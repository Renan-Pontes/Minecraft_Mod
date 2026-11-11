"""
Assets do modulo "domain" (rode da raiz do projeto:  py -I tools/gen/domain.py)
  - textures/entity/domain_dome.png   64x64  malha hexagonal "de barreira", branca (tingida no renderer), repete nas bordas
  - textures/entity/domain_ring.png   64x32  faixa de runas da base do domo (embaixo opaca, em cima some)
  - textures/mob_effect/domain_gravity.png 18x18  icone do efeito "Peso do Dominio"
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")


def dome_texture():
    """Malha hexagonal periodica em 64x64 (base (16,0) e (8,16): fecha exatamente na borda da textura)."""
    W = H = 64
    c = Canvas(W, H)
    a = (16.0, 0.0)
    b = (8.0, 16.0)
    pts = []
    for m in range(-2, 7):
        for n in range(-2, 7):
            pts.append((m * a[0] + n * b[0], m * a[1] + n * b[1]))
    rnd = random.Random(7)
    sparkle = {(rnd.randrange(W), rnd.randrange(H)) for _ in range(10)}
    for y in range(H):
        for x in range(W):
            px, py = x + 0.5, y + 0.5
            ds = sorted(math.hypot(px - qx, (py - qy) * 0.92) for qx, qy in pts)
            d1, d2 = ds[0], ds[1]
            edge = (d2 - d1) * 0.5          # distancia ate a aresta da celula (Voronoi)
            if edge < 0.75:
                alpha = 255                 # aresta: linha brilhante
            elif edge < 1.6:
                alpha = 170                 # brilho em volta da aresta
            else:
                # interior: leve degradê (mais claro perto da aresta), base fraca
                alpha = int(70 + 50 * max(0.0, 1.0 - (edge - 1.6) / 4.0))
            v = 255 if edge < 1.6 else 235
            c.set(x, y, (v, v, v, alpha))
    for (x, y) in sparkle:
        c.set(x, y, (255, 255, 255, 255))
    c.save(os.path.join(A, "textures", "entity", "domain_dome.png"))


def ring_texture():
    """Faixa de runas: linha forte embaixo (v=1), runas no meio, desvanece para cima (v=0)."""
    W, H = 64, 32
    c = Canvas(W, H)
    for y in range(H):
        f = y / (H - 1)                    # 0 em cima, 1 embaixo
        base_a = int(40 + 120 * f * f)
        for x in range(W):
            c.set(x, y, (255, 255, 255, base_a))
    # linhas-guia
    for x in range(W):
        for y in (H - 2, H - 1):
            c.set(x, y, (255, 255, 255, 255))
        c.set(x, H - 10, (255, 255, 255, 230))
        c.set(x, 12, (255, 255, 255, 140))
    # runas: 4 por tile, tracos aleatorios numa grade 6x8
    rnd = random.Random(42)
    for k in range(4):
        x0 = k * 16 + 4
        y0 = 14
        strokes = [((0, 0), (6, 0)), ((3, 0), (3, 7)), ((0, 7), (6, 7))]
        for _ in range(3):
            p1 = (rnd.randint(0, 6), rnd.randint(0, 7))
            p2 = (rnd.randint(0, 6), rnd.randint(0, 7))
            strokes.append((p1, p2))
        rnd.shuffle(strokes)
        for (ax, ay), (bx, by) in strokes[:4]:
            c.line(x0 + ax, y0 + ay, x0 + bx, y0 + by, (255, 255, 255, 255))
        # ponto central da runa
        c.set(x0 + 3, y0 + 3, (255, 255, 255, 255))
        c.set(x0 + 3, y0 + 4, (255, 255, 255, 255))
    c.save(os.path.join(A, "textures", "entity", "domain_ring.png"))


def gravity_icon():
    c = Canvas(18, 18)
    base = hex_rgba("4B3B8F")
    c.circle(9, 9, 7.5, darken(base, 0.25))
    c.circle(9, 9, 6.3, base)
    c.shade_sphere(9, 9, 7.5, light=0.45, dark=0.5)
    arrow = lighten(hex_rgba("C9B8FF"), 0.2)
    # seta para baixo
    for y in range(4, 11):
        c.set(8, y, arrow)
        c.set(9, y, arrow)
    for i, y in enumerate(range(10, 14)):
        for x in range(5 + i, 13 - i):
            c.set(x, y, arrow)
    # "chao" pesado
    for x in range(4, 14):
        c.set(x, 14, darken(arrow, 0.25))
    c.outline(darken(base, 0.7))
    c.save(os.path.join(A, "textures", "mob_effect", "domain_gravity.png"))


if __name__ == "__main__":
    dome_texture()
    ring_texture()
    gravity_icon()
    print("domain assets ok")
