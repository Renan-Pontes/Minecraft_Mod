"""
Gera os ícones da aba "Caminhos" (módulo paths): uma tira 144x16 com 9 ícones 16x16 em tons de cinza
(o jogo pinta com a cor do elemento). Ordem: awakening, apprentice, adept, fluidity, master, cataclysm,
domain, archmage, lock.

Uso: py -I tools/gen/paths.py [raiz_do_projeto] [--preview]
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas  # noqa: E402

root = next((a for a in sys.argv[1:] if not a.startswith("--")), os.path.dirname(os.path.dirname(HERE)))
PREVIEW = "--preview" in sys.argv
OUT = os.path.join(root, "src/main/resources/assets/isekai_mahou/textures/gui/paths_nodes.png")

O = (34, 32, 42, 255)      # contorno
D = (118, 116, 128, 255)   # sombra
M = (178, 176, 188, 255)   # meio-tom
L = (226, 226, 232, 255)   # luz
W = (255, 255, 255, 255)   # brilho


def poly(c, pts, col):
    """Preenche um polígono (centro do pixel dentro)."""
    n = len(pts)
    for y in range(c.h):
        for x in range(c.w):
            px, py = x + 0.5, y + 0.5
            inside = False
            j = n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            if inside:
                c.set(x, y, col)


def thick_line(c, x0, y0, x1, y1, r, col):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for s in range(steps + 1):
        t = s / steps
        c.circle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r, col)


def icon_awakening():
    """Olho que se abre, com raios."""
    c = Canvas(16, 16)
    cx, cy, hw, hh = 8.0, 10.0, 7.2, 4.0
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - cx) / hw
            if abs(dx) < 1:
                half = hh * (1 - dx * dx)
                if abs(y + 0.5 - cy) <= half:
                    c.set(x, y, L if y + 0.5 < cy else M)
    c.circle(8, 10.0, 2.9, D)
    c.circle(8, 10.0, 2.1, M)
    c.circle(8, 10.0, 1.2, O)
    c.set(7, 9, W)
    for x0, y0, x1, y1 in ((7, 1, 7, 2), (8, 1, 8, 2), (2, 3, 3, 4), (13, 3, 12, 4)):
        c.line(x0, y0, x1, y1, W)
    c.outline(O)
    return c


def icon_apprentice():
    """Livro aberto."""
    c = Canvas(16, 16)
    poly(c, [(1, 4.5), (7.6, 5.8), (7.6, 14), (1, 12.7)], L)
    poly(c, [(8.4, 5.8), (15, 4.5), (15, 12.7), (8.4, 14)], M)
    for i, y in enumerate((7, 9, 11)):
        c.line(2, y - 1 + i // 3, 6, y, D)
        c.line(10, y, 13, y - 1 + i // 3, D)
    c.line(8, 5, 8, 14, D)
    c.line(1, 13, 7, 14, D)
    c.line(9, 14, 15, 13, O)
    c.set(3, 5, W); c.set(4, 5, W)
    c.outline(O)
    return c


def icon_adept():
    """Cajado com orbe brilhante."""
    c = Canvas(16, 16)
    thick_line(c, 3, 14.2, 9.2, 6.6, 0.9, M)
    c.line(3, 14, 9, 7, D)
    c.circle(11.3, 4.6, 3.3, M)
    c.circle(11.0, 4.3, 2.4, L)
    c.circle(10.4, 3.7, 1.0, W)
    for x, y in ((14, 9), (6, 2), (15, 2)):
        c.set(x, y, W)
    c.outline(O)
    return c


def icon_fluidity():
    """Três correntes de vento/água em movimento."""
    c = Canvas(16, 16)
    for row, (base, x0, x1, col) in enumerate(((3.0, 2, 13, W), (8.0, 4, 15, L), (13.0, 1, 11, M))):
        for i in range(x0 * 4, x1 * 4 + 1):
            x = i / 4.0
            c.set(int(x), int(base + 1.1 * math.sin((x + row * 2.0) * 0.7)), col)
    c.outline(O)
    return c


def icon_master():
    """Coroa com três pontas e gemas."""
    c = Canvas(16, 16)
    poly(c, [(1.5, 13), (1.5, 5.5), (5, 9), (8, 3), (11, 9), (14.5, 5.5), (14.5, 13)], L)
    c.rect(2, 11, 13, 12, M)
    c.rect(2, 12, 13, 12, D)
    for x, y in ((2, 5), (8, 2), (13, 5)):
        c.circle(x + 0.5, y + 0.5, 1.2, W)
    c.set(5, 11, W); c.set(10, 11, W)
    c.set(7, 11, O); c.set(8, 11, O)
    c.set(4, 8, M); c.set(11, 8, M); c.set(8, 6, M)
    c.outline(O)
    return c


def icon_cataclysm():
    """Meteoro caindo com rastro."""
    c = Canvas(16, 16)
    thick_line(c, 2, 2, 8.5, 8.5, 1.6, D)
    thick_line(c, 3.5, 2.5, 9, 8, 1.0, M)
    thick_line(c, 2.5, 4.5, 8, 9.5, 0.7, M)
    c.circle(10.5, 10.5, 4.0, M)
    c.circle(10.0, 10.0, 3.1, L)
    c.circle(9.2, 9.2, 1.4, W)
    for x, y in ((15, 6), (6, 14), (14, 15), (1, 9)):
        c.set(x, y, W)
    c.outline(O)
    return c


def icon_domain():
    """Cúpula de barreira sobre o chão, com linhas hexagonais."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 12.5
            d = math.hypot(dx, dy)
            if d <= 7.4 and dy <= 0:
                c.set(x, y, L if d > 6.3 else (M if (x + y) % 2 == 0 else D))
    for x0, y0, x1, y1 in ((5, 11, 8, 7), (8, 7, 11, 11), (3, 9, 13, 9), (8, 7, 8, 5)):
        c.line(x0, y0, x1, y1, L)
    c.rect(0, 13, 15, 14, D)
    c.rect(0, 13, 15, 13, M)
    c.set(5, 6, W); c.set(4, 7, W)
    c.outline(O)
    return c


def icon_archmage():
    """Chapéu de mago com estrela."""
    c = Canvas(16, 16)
    poly(c, [(8.5, 0.8), (12.5, 11), (3.5, 11)], M)
    poly(c, [(8.5, 0.8), (10, 4.5), (8.5, 11), (5.5, 11)], L)
    for y in range(11, 15):
        for x in range(16):
            if ((x + 0.5 - 8) / 7.6) ** 2 + ((y + 0.5 - 12.6) / 1.9) ** 2 <= 1:
                c.set(x, y, M if y < 13 else D)
    c.rect(4, 10, 12, 10, D)
    c.set(8, 6, W); c.set(7, 6, W); c.set(9, 6, W); c.set(8, 5, W); c.set(8, 7, W)
    c.outline(O)
    return c


def icon_lock():
    """Cadeado (nó bloqueado)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 7)
            if 2.6 <= d <= 4.1 and y + 0.5 <= 7.5:
                c.set(x, y, M)
    c.rect(4, 7, 4, 8, M); c.rect(11, 7, 11, 8, M)
    c.rect(3, 8, 12, 14, L)
    c.rect(3, 13, 12, 14, M)
    c.rect(7, 10, 8, 11, O)
    c.set(7, 12, O); c.set(8, 12, O)
    c.set(4, 9, W)
    c.outline(O)
    return c


ICONS = [icon_awakening, icon_apprentice, icon_adept, icon_fluidity, icon_master,
         icon_cataclysm, icon_domain, icon_archmage, icon_lock]

strip = Canvas(16 * len(ICONS), 16)
for i, fn in enumerate(ICONS):
    ic = fn()
    for y in range(16):
        for x in range(16):
            strip.set(i * 16 + x, y, ic.get(x, y))
    if PREVIEW:
        print(fn.__name__)
        for y in range(16):
            print("".join(" " if ic.get(x, y)[3] == 0 else
                          {O: "#", D: "x", M: "+", L: "-", W: "@"}.get(ic.get(x, y), "?") for x in range(16)))
strip.save(OUT)
print("ok:", OUT)
