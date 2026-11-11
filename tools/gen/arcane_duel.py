"""
Gera os assets do modulo arcane_duel (contramagica e mecanicas de Magic: The Gathering).

  py -I tools/gen/arcane_duel.py

Texturas de visual (textures/entity/arcane_duel/), brancas com alfa e tingidas em jogo pela cor do vertice
(tipo de render aditivo: o alfa da a intensidade):
- rune_circle.png (128x128): vidro/circulo runico — aneis, 24 runas, heptagrama, raios e rachaduras finas.
- hex_cell.png (32x32): um painel hexagonal da casca do Resguardo (borda forte, miolo fraco, runa no centro).
- spell_card.png (64x96): carta de magia — moldura dupla com cantos, faixa de titulo, janela com sigilo, linhas de texto.
- clock_face.png (128x128): mostrador do Passo no Tempo — 12 marcas, 60 tracinhos, aneis e espiral.
- exile_rift.png (64x64): nucleo da fenda do Exilar — redemoinho roxo-escuro/preto com bordas violeta (translucido, NAO aditivo).
Icones de efeito (textures/mob_effect/, 18x18): hexproof.png (escudo hexagonal ciano), indestructible.png (egide dourada).
Tambem gera as traducoes (tools/gen/arcane_duel_lang.py).
Tudo anti-serrilhado por funcoes de distancia.
"""
import math
import os
import random
import runpy
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures")
OUT = os.path.join(ASSETS, "entity", "arcane_duel")
EFFECTS = os.path.join(ASSETS, "mob_effect")
TAU = math.pi * 2


def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


def put_white(c, x, y, v):
    k = int(round(clamp01(v) * 255))
    c.set(x, y, (255, 255, 255, k) if k > 0 else (0, 0, 0, 0))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def line_v(d, w, pxw):
    """Intensidade de uma linha de meia-largura w a distancia d (anti-serrilhado de 1 pixel)."""
    return clamp01(1 - (d - w) / pxw) if d > w else 1.0


# =====================================================================================
#  Circulo runico (vidro magico)
# =====================================================================================
def rune_circle():
    n = 128
    c = Canvas(n, n)
    pxw = 1.0 / (n * 0.5)
    rnd = random.Random(23)
    runes = [(TAU * i / 24, rnd.randint(0, 3)) for i in range(24)]
    # Heptagrama {7/3}
    pts = [(math.cos(TAU * j / 7 - math.pi / 2) * 0.78, math.sin(TAU * j / 7 - math.pi / 2) * 0.78) for j in range(7)]
    star = [(pts[j], pts[(j + 3) % 7]) for j in range(7)]
    # Rachaduras finas do vidro (do centro para fora, em zigue-zague)
    cracks = []
    for k in range(6):
        a = TAU * k / 6 + rnd.uniform(-0.3, 0.3)
        p = (0.0, 0.0)
        for s in range(4):
            r1 = 0.22 * (s + 1) + rnd.uniform(-0.03, 0.03)
            a1 = a + rnd.uniform(-0.25, 0.25)
            q = (math.cos(a1) * r1, math.sin(a1) * r1)
            cracks.append((p, q))
            p = q
    for y in range(n):
        for x in range(n):
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            r = math.hypot(fx, fy)
            ang = math.atan2(fy, fx)
            v = 0.0
            for rad, w, k in ((0.96, 0.016, 1.0), (0.88, 0.007, 0.85), (0.84, 0.005, 0.6), (0.52, 0.010, 0.9),
                              (0.47, 0.004, 0.55), (0.14, 0.012, 1.0)):
                v = max(v, line_v(abs(r - rad), w, pxw) * k)
            if 0.88 < r < 0.96:
                t = (r - 0.88) / 0.08
                for a, kind in runes:
                    da = (ang - a + math.pi) % TAU - math.pi
                    arc = abs(da) * r
                    if arc > 0.06:
                        continue
                    if kind == 0:
                        v = max(v, line_v(arc, 0.006, pxw) * 0.95)
                    elif kind == 1:
                        v = max(v, line_v(math.hypot(arc, (t - 0.5) * 0.08), 0.012, pxw))
                    elif kind == 2:
                        v = max(v, line_v(abs(arc - 0.03 * (1 - t)), 0.005, pxw) * 0.9)
                    else:
                        v = max(v, line_v(abs(t - 0.5) * 0.08, 0.005, pxw) * (1 if arc < 0.04 else 0) * 0.85)
            if r < 0.86:
                for a1, b1 in star:
                    v = max(v, line_v(seg_dist(fx, fy, a1[0], a1[1], b1[0], b1[1]), 0.006, pxw) * 0.85)
            for a1, b1 in cracks:
                v = max(v, line_v(seg_dist(fx, fy, a1[0], a1[1], b1[0], b1[1]), 0.002, pxw) * 0.45)
            if r < 0.52:
                v = max(v, 0.14 * (1 - r / 0.52) + 0.05)
            if r > 0.985:
                v = 0
            put_white(c, x, y, v)
    c.save(os.path.join(OUT, "rune_circle.png"))


# =====================================================================================
#  Painel hexagonal do Resguardo
# =====================================================================================
def hex_dist(fx, fy):
    """Distancia 'hexagonal' (0 no centro, 1 na borda) de um hexagono de vertices no eixo X."""
    ax, ay = abs(fx), abs(fy)
    return max(ay / (math.sqrt(3) / 2), (ax + ay / math.sqrt(3)))


def hex_cell():
    n = 32
    c = Canvas(n, n)
    pxw = 1.0 / (n * 0.5)
    for y in range(n):
        for x in range(n):
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            h = hex_dist(fx, fy) / 0.95
            if h > 1.0 + pxw:
                continue
            v = line_v(abs(h - 0.93), 0.05, pxw * 1.2)          # borda forte
            v = max(v, line_v(abs(h - 0.72), 0.015, pxw) * 0.45)  # borda interna
            v = max(v, 0.16 + 0.12 * h)                            # miolo de vidro
            # runa do centro: losango com risco
            d = abs(fx) + abs(fy)
            v = max(v, line_v(abs(d - 0.28), 0.03, pxw) * 0.8)
            v = max(v, line_v(abs(fx), 0.025, pxw) * (1 if abs(fy) < 0.4 else 0) * 0.6)
            put_white(c, x, y, v)
    c.save(os.path.join(OUT, "hex_cell.png"))


# =====================================================================================
#  Carta de magia
# =====================================================================================
def rounded_rect_dist(px, py, hw, hh, rad):
    qx, qy = abs(px) - hw + rad, abs(py) - hh + rad
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - rad


def spell_card():
    w, h = 64, 96
    c = Canvas(w, h)
    pxw = 1.0 / 32
    for y in range(h):
        for x in range(w):
            fx = (x + 0.5) / 32 - 1          # -1..1
            fy = (y + 0.5) / 32 - 1.5        # -1.5..1.5
            d = rounded_rect_dist(fx, fy, 0.96, 1.46, 0.12)
            if d > pxw:
                continue
            v = 0.13                                                # corpo da carta (fraco)
            v = max(v, line_v(abs(d + 0.03), 0.03, pxw))            # moldura externa
            v = max(v, line_v(abs(d + 0.13), 0.012, pxw) * 0.8)     # moldura interna
            # faixa de titulo
            if -1.32 < fy < -1.08 and abs(fx) < 0.8:
                v = max(v, 0.32)
                if abs(fy + 1.2) < 0.03 and abs(fx) < 0.6:
                    v = max(v, 0.75)
            # janela da arte com sigilo
            wd = rounded_rect_dist(fx, fy + 0.28, 0.74, 0.62, 0.06)
            v = max(v, line_v(abs(wd), 0.018, pxw) * 0.9)
            if wd < 0:
                gx, gy = fx, fy + 0.28
                gr = math.hypot(gx, gy)
                v = max(v, line_v(abs(gr - 0.42), 0.02, pxw) * 0.85)
                v = max(v, line_v(abs(abs(gx) + abs(gy) - 0.5), 0.022, pxw))
                v = max(v, line_v(abs(gr - 0.14), 0.04, pxw) * 0.9)
                v = max(v, 0.3 * clamp01(1 - gr / 0.5))
                for k in range(8):
                    a = TAU * k / 8
                    dd = seg_dist(gx, gy, math.cos(a) * 0.16, math.sin(a) * 0.16, math.cos(a) * 0.38, math.sin(a) * 0.38)
                    v = max(v, line_v(dd, 0.01, pxw) * 0.6)
            # linhas de texto
            for ly in (0.62, 0.8, 0.98):
                if abs(fy - ly) < 0.025 and abs(fx) < (0.7 if ly < 0.9 else 0.45):
                    v = max(v, 0.55)
            # gemas nos cantos
            for cx, cy in ((-0.78, -1.28), (0.78, -1.28), (-0.78, 1.28), (0.78, 1.28)):
                v = max(v, line_v(math.hypot(fx - cx, fy - cy), 0.05, pxw))
            put_white(c, x, y, v)
    c.save(os.path.join(OUT, "spell_card.png"))


# =====================================================================================
#  Mostrador do relogio
# =====================================================================================
def clock_face():
    n = 128
    c = Canvas(n, n)
    pxw = 1.0 / (n * 0.5)
    for y in range(n):
        for x in range(n):
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            r = math.hypot(fx, fy)
            ang = math.atan2(fy, fx)
            if r > 0.99:
                continue
            v = 0.0
            for rad, w, k in ((0.95, 0.02, 1.0), (0.86, 0.006, 0.7), (0.62, 0.008, 0.75), (0.1, 0.03, 1.0)):
                v = max(v, line_v(abs(r - rad), w, pxw) * k)
            # 60 tracinhos
            if 0.86 < r < 0.93:
                a60 = (ang % (TAU / 60) + TAU / 60) % (TAU / 60)
                arc = min(a60, TAU / 60 - a60) * r
                v = max(v, line_v(arc, 0.004, pxw) * 0.6)
            # 12 marcas grossas (as de 3 em 3 maiores)
            for k in range(12):
                a = TAU * k / 12
                big = k % 3 == 0
                r0, r1 = (0.66, 0.92) if big else (0.72, 0.9)
                dd = seg_dist(fx, fy, math.cos(a) * r0, math.sin(a) * r0, math.cos(a) * r1, math.sin(a) * r1)
                v = max(v, line_v(dd, 0.028 if big else 0.015, pxw))
            # espiral do tempo entre o miolo e o anel interno
            if 0.12 < r < 0.6:
                t = (ang / TAU) % 1.0
                for turn in range(3):
                    sr = 0.14 + 0.15 * (t + turn)
                    v = max(v, line_v(abs(r - sr), 0.006, pxw) * 0.5)
            v = max(v, 0.1 * clamp01(1 - r))
            put_white(c, x, y, v)
    c.save(os.path.join(OUT, "clock_face.png"))


# =====================================================================================
#  Nucleo da fenda (translucido)
# =====================================================================================
def exile_rift():
    n = 64
    c = Canvas(n, n)
    for y in range(n):
        for x in range(n):
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            r = math.hypot(fx, fy)
            if r > 1:
                continue
            ang = math.atan2(fy, fx)
            swirl = 0.5 + 0.5 * math.sin(ang * 3 + r * 9.0)
            arms = swirl ** 6
            edge = clamp01((r - 0.55) / 0.45)
            # Centro preto, bracos roxos, borda violeta brilhante
            base = (6, 2, 14)
            arm = (88, 28, 160)
            rim = (196, 120, 255)
            k_arm = arms * (0.35 + 0.65 * r)
            k_rim = edge ** 3
            col = [base[i] + (arm[i] - base[i]) * k_arm for i in range(3)]
            col = [col[i] + (rim[i] - col[i]) * k_rim for i in range(3)]
            alpha = clamp01(1.0 - clamp01((r - 0.82) / 0.18)) * (0.97 - 0.2 * k_rim)
            c.set(x, y, (int(col[0]), int(col[1]), int(col[2]), int(alpha * 255)))
    c.save(os.path.join(OUT, "exile_rift.png"))


# =====================================================================================
#  Icones de efeito (18x18)
# =====================================================================================
def icon_hexproof():
    c = Canvas(18, 18)
    dark, mid, light, white = (20, 70, 96, 255), (60, 170, 210, 255), (127, 227, 255, 255), (230, 252, 255, 255)
    for y in range(18):
        for x in range(18):
            fx = (x + 0.5 - 9) / 8.0
            fy = (y + 0.5 - 9) / 8.0
            h = max(abs(fx) / (math.sqrt(3) / 2), abs(fx) / (math.sqrt(3) / 2) * 0.5 + abs(fy))
            if h > 1.0:
                continue
            if h > 0.84:
                col = dark
            elif h > 0.7:
                col = light
            else:
                col = mid if (fy + fx * 0.3) > -0.1 else light
            c.set(x, y, col)
    # runa central (losango + risco)
    for (x, y) in ((9, 5), (8, 6), (10, 6), (7, 7), (11, 7), (6, 8), (12, 8), (7, 9), (11, 9), (8, 10), (10, 10), (9, 11)):
        c.set(x - 0, y, white)
    for y in range(6, 12):
        c.set(9, y, white)
    c.save(os.path.join(EFFECTS, "hexproof.png"))


def icon_indestructible():
    c = Canvas(18, 18)
    dark, mid, light, white = (110, 70, 10, 255), (220, 160, 40, 255), (255, 210, 74, 255), (255, 246, 210, 255)
    for y in range(18):
        for x in range(18):
            fx = (x + 0.5 - 9) / 7.5
            fy = (y + 0.5 - 8) / 8.0
            # escudo: topo reto, ponta embaixo
            if fy < -0.85 or fy > 1.05:
                continue
            half = 0.95 if fy < 0.2 else 0.95 * (1 - (fy - 0.2) / 0.85)
            if abs(fx) > half:
                continue
            edge = half - abs(fx) < 0.18 or fy < -0.68
            col = dark if edge else (light if fx < 0.05 else mid)
            c.set(x, y, col)
    # estrela/brilho no centro
    for (x, y) in ((9, 4), (9, 5), (9, 6), (9, 7), (9, 8), (9, 9), (9, 10), (6, 7), (7, 7), (8, 7), (10, 7), (11, 7), (12, 7),
                   (8, 6), (10, 6), (8, 8), (10, 8)):
        c.set(x, y, white)
    c.save(os.path.join(EFFECTS, "indestructible.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    rune_circle()
    hex_cell()
    spell_card()
    clock_face()
    exile_rift()
    icon_hexproof()
    icon_indestructible()
    runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), "arcane_duel_lang.py"), run_name="__main__")
    print("arcane_duel: texturas geradas em", OUT, "e", EFFECTS)
