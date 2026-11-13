"""
Artes do CÍRCULO MÁGICO POR MAGIA (release 1.0 — glyph/CircleStyle): formas e símbolos que o jogador escolhe no
Grimório para o círculo de conjuração de cada receita.

  py -I tools/gen/mastery_circles.py

Saídas (brancas com alfa suave; o jogo tinge com a cor do círculo):
  - textures/particle/fx_circle_s1..s7.png   formas (128x128), todas com o CENTRO LIVRE para o símbolo
  - textures/particle/fx_circle_y1..y12.png  símbolos do centro (64x64)
  - particles/fx_circle_style.json           lista (formas e depois símbolos — a ordem é o índice usado no Java)
Usa só as utilidades de tools/gen/fx_spells.py (não regenera nada de lá).
"""
import math
import os
import sys

import numpy as np
from PIL import ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fx_spells as fx  # noqa: E402  (tem guarda de __main__: importar não gera nada)

SS = fx.SS
SHAPES = 7
SYMBOLS = 12


def new(n):
    img = fx.canvas(n, n)
    return img, ImageDraw.Draw(img)


def done(img, n, name, blur, glow):
    a = fx.finish(img, n, n)
    g = fx.finish(img, n, n, blur=blur) * glow
    a = np.clip(np.maximum(a, g), 0, 1)
    fx.save_rgba(fx.P(name), np.ones_like(a), a)


def line(dr, pts, w, closed=False):
    if closed:
        pts = list(pts) + [pts[0]]
    dr.line(pts, fill=255, width=max(1, int(w * SS)), joint="curve")


def star_points(c, r, n, step, rot=-math.pi / 2):
    pts = fx.poly_points(c, c, r, n, rot)
    return [pts[(i * step) % n] for i in range(n)]


def rune_band(dr, c, r, count, size, first=0):
    for i in range(count):
        a = 2 * math.pi * i / count
        fx.draw_rune(dr, first + i, c + math.cos(a) * r, c + math.sin(a) * r, size * SS, a + math.pi / 2, 0.9 * SS)


def ticks(dr, c, r0, r1, count, w, every=0, r_long=None):
    for i in range(count):
        a = 2 * math.pi * i / count
        inner = r_long if (every and i % every == 0 and r_long is not None) else r0
        dr.line([(c + math.cos(a) * inner, c + math.sin(a) * inner), (c + math.cos(a) * r1, c + math.sin(a) * r1)],
                fill=255, width=max(1, int(w * SS)))


def dot(dr, x, y, r, w=0.9):
    dr.ellipse([x - r, y - r, x + r, y + r], outline=255, width=max(1, int(w * SS)))


# =====================================================================================
#  Formas (128x128) — nada desenhado dentro de 0,38 R (lugar do símbolo)
# =====================================================================================
def shapes():
    n = 128
    c = n / 2 * SS
    R = (n / 2 - 1.5) * SS

    # 1 — Hexagrama: o círculo clássico (anel duplo com runas + estrela de seis pontas), centro livre
    img, dr = new(n)
    fx.ring(dr, c, R, 1.6 * SS)
    fx.ring(dr, c, R * 0.86, 1.1 * SS)
    rune_band(dr, c, R * 0.93, 20, 7.0)
    for off in (0, math.pi / 3):
        line(dr, fx.poly_points(c, c, R * 0.84, 3, -math.pi / 2 + off), 1.1, True)
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        dot(dr, c + math.cos(a) * R * 0.84, c + math.sin(a) * R * 0.84, 2.4 * SS)
    fx.ring(dr, c, R * 0.40, 1.0 * SS)
    done(img, n, "fx_circle_s1", 2.0, 0.55)

    # 2 — Pentagrama: estrela de cinco pontas, luas pequenas nas pontas
    img, dr = new(n)
    fx.ring(dr, c, R, 1.6 * SS)
    fx.ring(dr, c, R * 0.90, 0.9 * SS)
    ticks(dr, c, R * 0.92, R * 0.985, 40, 0.8)
    line(dr, star_points(c, R * 0.88, 5, 2), 1.3, True)
    line(dr, fx.poly_points(c, c, R * 0.88, 5, -math.pi / 2), 0.8, True)
    for i in range(5):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        dot(dr, c + math.cos(a) * R * 0.88, c + math.sin(a) * R * 0.88, 3.0 * SS, 1.0)
    fx.ring(dr, c, R * 0.40, 1.0 * SS)
    done(img, n, "fx_circle_s2", 2.0, 0.55)

    # 3 — Selo quadrado: dois quadrados girados, faixa de runas por dentro
    img, dr = new(n)
    fx.ring(dr, c, R, 1.5 * SS)
    for off in (0, math.pi / 4):
        line(dr, fx.poly_points(c, c, R * 0.97, 4, off), 1.2, True)
    fx.ring(dr, c, R * 0.66, 1.0 * SS)
    fx.ring(dr, c, R * 0.50, 1.0 * SS)
    rune_band(dr, c, R * 0.58, 12, 6.5, 4)
    for i in range(8):
        a = i * math.pi / 4
        dot(dr, c + math.cos(a) * R * 0.80, c + math.sin(a) * R * 0.80, 1.8 * SS)
    done(img, n, "fx_circle_s3", 2.0, 0.5)

    # 4 — Tríade: triângulo com três selos nos vértices
    img, dr = new(n)
    fx.ring(dr, c, R, 1.5 * SS)
    fx.ring(dr, c, R * 0.93, 0.8 * SS)
    tri = fx.poly_points(c, c, R * 0.90, 3, -math.pi / 2)
    line(dr, tri, 1.3, True)
    line(dr, fx.poly_points(c, c, R * 0.90, 3, math.pi / 2), 0.7, True)
    for (px, py) in tri:
        dot(dr, px, py, 7.0 * SS, 1.1)
        dot(dr, px, py, 3.0 * SS, 0.9)
    fx.ring(dr, c, R * 0.45, 1.1 * SS)
    ticks(dr, c, R * 0.45, R * 0.52, 24, 0.8)
    done(img, n, "fx_circle_s4", 2.0, 0.55)

    # 5 — Solar: raios triangulares em volta de um anel
    img, dr = new(n)
    fx.ring(dr, c, R, 1.3 * SS)
    fx.ring(dr, c, R * 0.62, 1.4 * SS)
    fx.ring(dr, c, R * 0.56, 0.8 * SS)
    rays = 12
    for i in range(rays):
        a = 2 * math.pi * i / rays
        half = math.pi / rays * 0.62
        tip = (c + math.cos(a) * R * 0.97, c + math.sin(a) * R * 0.97)
        b0 = (c + math.cos(a - half) * R * 0.64, c + math.sin(a - half) * R * 0.64)
        b1 = (c + math.cos(a + half) * R * 0.64, c + math.sin(a + half) * R * 0.64)
        line(dr, [b0, tip, b1], 1.1)
        m = a + math.pi / rays
        dr.line([(c + math.cos(m) * R * 0.66, c + math.sin(m) * R * 0.66),
                 (c + math.cos(m) * R * 0.80, c + math.sin(m) * R * 0.80)], fill=255, width=int(0.9 * SS))
    fx.ring(dr, c, R * 0.42, 0.9 * SS)
    done(img, n, "fx_circle_s5", 2.2, 0.55)

    # 6 — Lótus: roseta de oito pétalas (arcos) dentro do anel
    img, dr = new(n)
    fx.ring(dr, c, R, 1.5 * SS)
    fx.ring(dr, c, R * 0.95, 0.8 * SS)
    petals = 8
    pr = R * 0.36
    pc = R * 0.585
    for i in range(petals):
        a = 2 * math.pi * i / petals
        px, py = c + math.cos(a) * pc, c + math.sin(a) * pc
        dr.ellipse([px - pr, py - pr, px + pr, py + pr], outline=255, width=int(1.0 * SS))
    # apaga o miolo (as pétalas se cruzam no centro) e fecha com um anel
    hole = R * 0.40
    dr.ellipse([c - hole, c - hole, c + hole, c + hole], fill=0)
    fx.ring(dr, c, hole, 1.1 * SS)
    done(img, n, "fx_circle_s6", 2.0, 0.5)

    # 7 — Relógio: marcas de horas e minutos, ponteiros curtos nas 12 direções
    img, dr = new(n)
    fx.ring(dr, c, R, 1.6 * SS)
    fx.ring(dr, c, R * 0.80, 1.0 * SS)
    ticks(dr, c, R * 0.92, R * 0.985, 60, 0.7, every=5, r_long=R * 0.82)
    for i in range(12):
        a = -math.pi / 2 + i * math.pi / 6
        fx.draw_rune(dr, i + 2, c + math.cos(a) * R * 0.70, c + math.sin(a) * R * 0.70, 8.0 * SS, a + math.pi / 2, 1.0 * SS)
    fx.ring(dr, c, R * 0.58, 0.8 * SS)
    fx.ring(dr, c, R * 0.42, 1.2 * SS)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        dr.line([(c + math.cos(a) * R * 0.44, c + math.sin(a) * R * 0.44),
                 (c + math.cos(a) * R * 0.56, c + math.sin(a) * R * 0.56)], fill=255, width=int(1.0 * SS))
    done(img, n, "fx_circle_s7", 2.0, 0.55)


# =====================================================================================
#  Símbolos (64x64) — coordenadas -1..1 a partir do centro (y para baixo)
# =====================================================================================
def symbols():
    n = 64
    c = n / 2 * SS
    U = (n / 2 - 5) * SS  # 1 unidade

    def P(x, y):
        return (c + x * U, c + y * U)

    def poly(dr, pts, fill=255):
        dr.polygon([P(x, y) for (x, y) in pts], fill=fill)

    def ell(dr, x, y, rx, ry, fill=255):
        dr.ellipse([c + (x - rx) * U, c + (y - ry) * U, c + (x + rx) * U, c + (y + ry) * U], fill=fill)

    def seg(dr, pts, w, fill=255):
        dr.line([P(x, y) for (x, y) in pts], fill=fill, width=max(1, int(w * SS)), joint="curve")

    def out(img, k):
        done(img, n, "fx_circle_y%d" % k, 1.6, 0.6)

    # 1 — Estrela
    img, dr = new(n)
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 0.95 if i % 2 == 0 else 0.40
        pts.append((math.cos(a) * r, math.sin(a) * r))
    poly(dr, pts)
    out(img, 1)

    # 2 — Lua crescente
    img, dr = new(n)
    ell(dr, 0, 0, 0.88, 0.88)
    ell(dr, 0.38, -0.10, 0.74, 0.74, fill=0)
    out(img, 2)

    # 3 — Sol
    img, dr = new(n)
    ell(dr, 0, 0, 0.42, 0.42)
    ell(dr, 0, 0, 0.24, 0.24, fill=0)
    for i in range(8):
        a = i * math.pi / 4
        r0, r1 = 0.56, (0.95 if i % 2 == 0 else 0.80)
        seg(dr, [(math.cos(a) * r0, math.sin(a) * r0), (math.cos(a) * r1, math.sin(a) * r1)], 2.2)
    out(img, 3)

    # 4 — Olho
    img, dr = new(n)
    top = [(-0.95 + 1.9 * i / 20, -0.52 * math.sin(math.pi * i / 20)) for i in range(21)]
    bot = [(0.95 - 1.9 * i / 20, 0.52 * math.sin(math.pi * i / 20)) for i in range(21)]
    seg(dr, top + bot + [top[0]], 2.2)
    ell(dr, 0, 0, 0.30, 0.30)
    ell(dr, 0, 0, 0.12, 0.12, fill=0)
    out(img, 4)

    # 5 — Chama
    img, dr = new(n)
    poly(dr, [(0.0, -0.98), (0.30, -0.50), (0.62, -0.05), (0.66, 0.38), (0.42, 0.78), (0.0, 0.95),
              (-0.42, 0.78), (-0.66, 0.38), (-0.55, -0.02), (-0.30, -0.30), (-0.22, 0.05), (-0.05, -0.40)])
    poly(dr, [(0.05, 0.05), (0.28, 0.42), (0.18, 0.72), (0.0, 0.80), (-0.20, 0.70), (-0.26, 0.45)], fill=0)
    out(img, 5)

    # 6 — Gota
    img, dr = new(n)
    poly(dr, [(0.0, -0.98), (0.50, 0.05), (-0.50, 0.05)])
    ell(dr, 0, 0.32, 0.58, 0.58)
    ell(dr, -0.18, 0.40, 0.14, 0.20, fill=0)
    out(img, 6)

    # 7 — Folha
    img, dr = new(n)
    side_a = [(-0.80 + 1.6 * i / 16, 0.80 - 1.6 * i / 16) for i in range(17)]
    leaf = []
    for i, (x, y) in enumerate(side_a):
        b = 0.42 * math.sin(math.pi * i / 16)
        leaf.append((x - b, y - b))
    for i, (x, y) in enumerate(reversed(side_a)):
        b = 0.42 * math.sin(math.pi * i / 16)
        leaf.append((x + b, y + b))
    poly(dr, leaf)
    seg(dr, [(-0.95, 0.95), (0.55, -0.55)], 1.6, fill=0)
    seg(dr, [(-0.95, 0.95), (-0.70, 0.70)], 2.0)
    out(img, 7)

    # 8 — Raio
    img, dr = new(n)
    poly(dr, [(0.25, -0.98), (-0.55, 0.10), (-0.08, 0.10), (-0.30, 0.98), (0.58, -0.22), (0.08, -0.22), (0.50, -0.98)])
    out(img, 8)

    # 9 — Caveira
    img, dr = new(n)
    ell(dr, 0, -0.18, 0.74, 0.70)
    poly(dr, [(-0.40, 0.30), (0.40, 0.30), (0.36, 0.90), (-0.36, 0.90)])
    ell(dr, -0.30, -0.12, 0.20, 0.22, fill=0)
    ell(dr, 0.30, -0.12, 0.20, 0.22, fill=0)
    poly(dr, [(0.0, 0.12), (0.10, 0.34), (-0.10, 0.34)], fill=0)
    for x in (-0.18, 0.0, 0.18):
        seg(dr, [(x, 0.58), (x, 0.92)], 1.2, fill=0)
    out(img, 9)

    # 10 — Espada
    img, dr = new(n)
    poly(dr, [(0.0, -0.98), (0.15, -0.72), (0.15, 0.38), (-0.15, 0.38), (-0.15, -0.72)])
    poly(dr, [(-0.52, 0.38), (0.52, 0.38), (0.52, 0.54), (-0.52, 0.54)])
    poly(dr, [(-0.09, 0.54), (0.09, 0.54), (0.09, 0.84), (-0.09, 0.84)])
    ell(dr, 0, 0.88, 0.15, 0.12)
    seg(dr, [(0.0, -0.62), (0.0, 0.30)], 1.0, fill=0)
    out(img, 10)

    # 11 — Coroa
    img, dr = new(n)
    poly(dr, [(-0.85, 0.55), (-0.90, -0.50), (-0.42, 0.00), (0.0, -0.75), (0.42, 0.00), (0.90, -0.50), (0.85, 0.55)])
    poly(dr, [(-0.85, 0.66), (0.85, 0.66), (0.85, 0.86), (-0.85, 0.86)])
    for (x, y) in ((-0.90, -0.60), (0.0, -0.86), (0.90, -0.60)):
        ell(dr, x, y, 0.11, 0.11)
    ell(dr, 0, 0.26, 0.13, 0.13, fill=0)
    out(img, 11)

    # 12 — Floco de neve
    img, dr = new(n)
    for i in range(6):
        a = i * math.pi / 3
        ca, sa = math.cos(a), math.sin(a)
        seg(dr, [(0, 0), (ca * 0.95, sa * 0.95)], 2.0)
        for d in (0.45, 0.70):
            bx, by = ca * d, sa * d
            for s in (-1, 1):
                b = a + s * math.pi / 3
                seg(dr, [(bx, by), (bx + math.cos(b) * 0.24, by + math.sin(b) * 0.24)], 1.6)
    ell(dr, 0, 0, 0.14, 0.14)
    out(img, 12)


def main():
    shapes()
    symbols()
    names = ["fx_circle_s%d" % i for i in range(1, SHAPES + 1)] + ["fx_circle_y%d" % i for i in range(1, SYMBOLS + 1)]
    fx.write_particle("fx_circle_style", names)
    print("mastery_circles: %d formas + %d simbolos + particles/fx_circle_style.json" % (SHAPES, SYMBOLS))


if __name__ == "__main__":
    main()
