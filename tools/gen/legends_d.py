"""
Gera as texturas do modulo legends_d (Atomico, Desintegracao, Parar o Tempo, Cabra).

  py -I tools/gen/legends_d.py

- entity/legends_d/atomic_swirl.png (64x64, repete sem emenda): plasma turbulento das cascas da esfera atomica.
  Branco sobre PRETO (energySwirl soma a cor), tingido em jogo de azul-violeta.
- entity/legends_d/atomic_ring.png (128x128): orbita de atomo com runas (anel duplo, tracos, "eletrons"), branco
  com alfa, tingido em jogo.
- entity/legends_d/holy_circle.png (128x128): circulo sagrado da Desintegracao (aneis, hexagrama, cruz de luz,
  runas angulares), branco com alfa.
- entity/legends_d/clock_face.png (128x128): mostrador de relogio (12 marcas, numerais em tracos, aneis, centro).
- entity/legends_d/atomic_circle.png (128x128): circulo magico do Atomico (aneis duplos, simbolo do atomo com
  tres orbitas e nucleo, faixa de runas, pontas de estrela), branco com alfa: as camadas da carga e o raio no chao.
- entity/legends_d/atomic_hex.png (64x64, repete sem emenda): favos de energia da bolha protetora dos aliados.
  Branco sobre PRETO (energySwirl), tingido de azul em jogo.
- mob_effect/time_stopped.png, goat_curse.png e atomic_bubble.png (18x18): icones dos efeitos.
Tudo anti-serrilhado por funcoes de distancia.
"""
import math
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures")
OUT = os.path.join(TEX, "entity", "legends_d")
EFFECT = os.path.join(TEX, "mob_effect")
TAU = math.pi * 2


def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


def put_gray(c, x, y, v):
    k = int(round(clamp01(v) * 255))
    c.set(x, y, (k, k, k, k))


def put_white(c, x, y, v):
    k = int(round(clamp01(v) * 255))
    c.set(x, y, (255, 255, 255, k) if k > 0 else (0, 0, 0, 0))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    qx, qy = ax + dx * t, ay + dy * t
    return math.hypot(px - qx, py - qy)


def polar_grid(n):
    """Itera (x, y, fx, fy, r, ang) com fx/fy em -1..1."""
    for y in range(n):
        for x in range(n):
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            yield x, y, fx, fy, math.hypot(fx, fy), math.atan2(fy, fx)


# =====================================================================================
#  Plasma da esfera atomica (repete sem emenda: so frequencias inteiras)
# =====================================================================================
def atomic_swirl():
    n = 64
    c = Canvas(n, n)
    rnd = random.Random(23)
    ph = [rnd.random() * TAU for _ in range(6)]
    for y in range(n):
        for x in range(n):
            u, v = x / n, y / n
            warp = 2.1 * math.sin(TAU * (u + 2 * v) + ph[0]) + 1.4 * math.cos(TAU * (3 * u - v) + ph[1])
            cells = 0.5 + 0.5 * math.sin(TAU * (2 * u + 3 * v) + warp)
            veins = 0.5 + 0.5 * math.sin(TAU * (5 * u + 2 * v) + 2.4 * math.sin(TAU * (u - 2 * v) + ph[2]))
            sparks = 0.5 + 0.5 * math.sin(TAU * (7 * u - 4 * v) + 1.7 * math.cos(TAU * (2 * u + v) + ph[3]))
            val = cells ** 4 * 0.85 + veins ** 12 * 0.75 + sparks ** 20 * 0.6 + 0.06
            put_gray(c, x, y, val)
    c.save(os.path.join(OUT, "atomic_swirl.png"))


# =====================================================================================
#  Orbita do atomo
# =====================================================================================
def atomic_ring():
    n = 128
    c = Canvas(n, n)
    px_w = 1.0 / (n * 0.5)
    rnd = random.Random(5)
    runes = [(TAU * i / 32, rnd.randint(0, 2)) for i in range(32)]
    for x, y, fx, fy, r, ang in polar_grid(n):
        def ring(rad, w):
            return clamp01(1 - abs(r - rad) / (w + px_w))

        v = 0.0
        v = max(v, ring(0.94, 0.022))
        v = max(v, ring(0.86, 0.007) * 0.8)
        v = max(v, ring(0.70, 0.010) * 0.55)
        # Brilho suave da orbita (faixa larga)
        v = max(v, clamp01(1 - abs(r - 0.9) / 0.12) * 0.25)
        # Runas entre os aneis externos
        if 0.86 < r < 0.94:
            for a, kind in runes:
                da = (ang - a + math.pi) % TAU - math.pi
                arc = abs(da) * r
                if arc > 0.05:
                    continue
                t = (r - 0.86) / 0.08
                if kind == 0:
                    v = max(v, clamp01(1 - arc / (0.007 + px_w)) * 0.95)
                elif kind == 1:
                    v = max(v, clamp01(1 - abs(arc - 0.025 * t) / (0.006 + px_w)) * 0.85)
                elif abs(t - 0.5) < 0.2:
                    v = max(v, clamp01(1 - arc / 0.035) * 0.8)
        # Tres "eletrons" (nos brilhantes) na orbita
        for k in range(3):
            a = TAU * k / 3 + 0.4
            ex, ey = math.cos(a) * 0.9, math.sin(a) * 0.9
            d = math.hypot(fx - ex, fy - ey)
            v = max(v, clamp01(1 - d / 0.07) ** 1.5)
        # Segmentos tracejados no anel interno
        if abs(r - 0.70) < 0.03:
            dash = 0.5 + 0.5 * math.sin(ang * 24)
            v = max(v, dash ** 4 * 0.6)
        put_white(c, x, y, v)
    c.save(os.path.join(OUT, "atomic_ring.png"))


# =====================================================================================
#  Circulo magico do Atomico (camadas da carga e raio da explosao no chao)
# =====================================================================================
def atomic_circle():
    n = 128
    c = Canvas(n, n)
    px_w = 1.0 / (n * 0.5)
    rnd = random.Random(77)
    runes = [(TAU * i / 40, rnd.randint(0, 3)) for i in range(40)]

    def ring(r, rad, w):
        return clamp01(1 - abs(r - rad) / (w + px_w))

    for x, y, fx, fy, r, ang in polar_grid(n):
        v = 0.0
        # Aneis externos (duplo) e faixa de runas entre eles
        v = max(v, ring(r, 0.96, 0.016))
        v = max(v, ring(r, 0.84, 0.010) * 0.85)
        if 0.85 < r < 0.95:
            t = (r - 0.85) / 0.10
            for a, kind in runes:
                da = (ang - a + math.pi) % TAU - math.pi
                arc = abs(da) * r
                if arc > 0.045:
                    continue
                if kind == 0:
                    v = max(v, clamp01(1 - arc / (0.006 + px_w)) * 0.9 * (1 if 0.2 < t < 0.8 else 0))
                elif kind == 1:
                    v = max(v, clamp01(1 - abs(arc - 0.03 * (t - 0.5)) / (0.006 + px_w)) * 0.85)
                elif kind == 2:
                    v = max(v, clamp01(1 - abs(t - 0.5) / 0.12) * clamp01(1 - arc / 0.03) * 0.8)
                else:
                    v = max(v, clamp01(1 - math.hypot(arc, (t - 0.5) * 0.1) / 0.012) * 0.95)
        # Estrela de 8 pontas inscrita (linhas finas entre pontos do anel 0.84)
        for k in range(8):
            a0 = TAU * k / 8
            a1 = TAU * (k + 3) / 8
            d = seg_dist(fx, fy, math.cos(a0) * 0.84, math.sin(a0) * 0.84, math.cos(a1) * 0.84, math.sin(a1) * 0.84)
            v = max(v, clamp01(1 - d / (0.006 + px_w)) * 0.45)
        # Anel interno tracejado
        if abs(r - 0.62) < 0.03:
            dash = 0.5 + 0.5 * math.sin(ang * 36)
            v = max(v, (dash ** 3) * 0.7 * clamp01(1 - abs(r - 0.62) / 0.012))
        v = max(v, ring(r, 0.58, 0.006) * 0.6)
        # Simbolo do atomo: tres orbitas elipticas + nucleo
        for k in range(3):
            rot = TAU * k / 6
            cr, sr = math.cos(rot), math.sin(rot)
            ux = fx * cr + fy * sr
            uy = -fx * sr + fy * cr
            e = math.hypot(ux / 0.46, uy / 0.17)
            v = max(v, clamp01(1 - abs(e - 1) * 0.17 / (0.010 + px_w)) * 0.95)
            # eletron em cada orbita
            ea = 0.7 + k * 2.1
            ex, ey = math.cos(ea) * 0.46, math.sin(ea) * 0.17
            gx, gy = ex * cr - ey * sr, ex * sr + ey * cr
            v = max(v, clamp01(1 - math.hypot(fx - gx, fy - gy) / 0.045) ** 1.4)
        v = max(v, clamp01(1 - r / 0.09) ** 1.2)
        v = max(v, ring(r, 0.13, 0.008) * 0.8)
        # Brilho largo suave
        v = max(v, clamp01(1 - abs(r - 0.9) / 0.14) * 0.18)
        put_white(c, x, y, v)
    c.save(os.path.join(OUT, "atomic_circle.png"))


# =====================================================================================
#  Favos da bolha protetora (repete sem emenda)
# =====================================================================================
def atomic_hex():
    n = 64
    c = Canvas(n, n)
    cols, rows = 4, 2          # 4 favos na largura, 2 periodos de linha na altura
    sx = n / cols              # largura de um favo
    sy = n / rows              # periodo vertical (duas fileiras deslocadas)
    for y in range(n):
        for x in range(n):
            best = 9.0
            second = 9.0
            # Centros do favo (malha deslocada) com copias vizinhas para emendar
            for j in range(-1, rows * 2 + 2):
                cy = j * sy / 2
                off = (sx / 2) if j % 2 else 0.0
                for i in range(-1, cols + 2):
                    cx = i * sx + off
                    dx = (x + 0.5 - cx) / sx
                    dy = (y + 0.5 - cy) / sx
                    d = math.hypot(dx, dy)
                    if d < best:
                        best, second = d, best
                    elif d < second:
                        second = d
            edge = second - best
            line = clamp01(1 - edge / 0.09) ** 1.3
            inner = clamp01(1 - best / 0.55) * 0.12
            u, v = x / n, y / n
            shimmer = 0.5 + 0.5 * math.sin(TAU * (u + v))
            put_gray(c, x, y, line * (0.75 + 0.25 * shimmer) + inner + 0.03)
    c.save(os.path.join(OUT, "atomic_hex.png"))


# =====================================================================================
#  Circulo sagrado da Desintegracao
# =====================================================================================
def holy_circle():
    n = 128
    c = Canvas(n, n)
    px_w = 1.0 / (n * 0.5)
    rnd = random.Random(31)
    runes = [(TAU * i / 20, rnd.randint(0, 3)) for i in range(20)]
    hexa = []
    for k in range(2):
        pts = []
        for j in range(3):
            a = TAU * j / 3 + k * math.pi / 3 - math.pi / 2
            pts.append((math.cos(a) * 0.74, math.sin(a) * 0.74))
        for j in range(3):
            hexa.append((pts[j], pts[(j + 1) % 3]))
    for x, y, fx, fy, r, ang in polar_grid(n):
        def ring(rad, w):
            return clamp01(1 - abs(r - rad) / (w + px_w))

        v = 0.0
        v = max(v, ring(0.96, 0.02))
        v = max(v, ring(0.90, 0.006) * 0.8)
        v = max(v, ring(0.78, 0.012))
        v = max(v, ring(0.45, 0.010) * 0.85)
        v = max(v, ring(0.22, 0.012))
        # Runas angulares (tracos em forma de cruz, triangulo e barra) entre 0.80 e 0.90
        if 0.80 < r < 0.90:
            for a, kind in runes:
                da = (ang - a + math.pi) % TAU - math.pi
                arc = abs(da) * r
                if arc > 0.06:
                    continue
                t = (r - 0.80) / 0.10
                if kind == 0:
                    v = max(v, clamp01(1 - arc / (0.007 + px_w)) * 0.95)
                elif kind == 1:
                    if abs(t - 0.5) < 0.12:
                        v = max(v, clamp01(1 - arc / 0.04) * 0.9)
                    v = max(v, clamp01(1 - arc / (0.006 + px_w)) * 0.8)
                elif kind == 2:
                    v = max(v, clamp01(1 - abs(arc - 0.03 * t) / (0.006 + px_w)) * 0.85)
                else:
                    v = max(v, clamp01(1 - abs(arc - 0.03 * (1 - t)) / (0.006 + px_w)) * 0.85)
        # Hexagrama
        if r < 0.80:
            for (a1, b1) in hexa:
                d = seg_dist(fx, fy, a1[0], a1[1], b1[0], b1[1])
                v = max(v, clamp01(1 - d / (0.008 + px_w)) * 0.85)
        # Cruz de luz e raios finos
        if r < 0.78:
            for k in range(4):
                a = TAU * k / 4
                da = (ang - a + math.pi) % TAU - math.pi
                v = max(v, clamp01(1 - abs(da) * r / (0.012 + px_w)) * 0.9 * (1 - r / 0.9))
            for k in range(12):
                a = TAU * k / 12 + TAU / 24
                da = (ang - a + math.pi) % TAU - math.pi
                if 0.22 < r < 0.45:
                    v = max(v, clamp01(1 - abs(da) * r / (0.004 + px_w)) * 0.5)
        # Miolo luminoso
        if r < 0.22:
            v = max(v, 0.35 * (1 - r / 0.22) + 0.1)
        put_white(c, x, y, v)
    c.save(os.path.join(OUT, "holy_circle.png"))


# =====================================================================================
#  Mostrador do relogio
# =====================================================================================
def clock_face():
    n = 128
    c = Canvas(n, n)
    px_w = 1.0 / (n * 0.5)
    for x, y, fx, fy, r, ang in polar_grid(n):
        def ring(rad, w):
            return clamp01(1 - abs(r - rad) / (w + px_w))

        v = 0.0
        v = max(v, ring(0.95, 0.025))
        v = max(v, ring(0.88, 0.008) * 0.8)
        v = max(v, ring(0.55, 0.008) * 0.6)
        v = max(v, ring(0.08, 0.02))
        # 60 marcas pequenas e 12 grandes
        da60 = ((ang + math.pi / 2) % (TAU / 60) + TAU / 60) % (TAU / 60)
        da60 = min(da60, TAU / 60 - da60)
        if 0.80 < r < 0.88:
            v = max(v, clamp01(1 - da60 * r / (0.004 + px_w)) * 0.7)
        da12 = ((ang + math.pi / 2) % (TAU / 12) + TAU / 12) % (TAU / 12)
        da12 = min(da12, TAU / 12 - da12)
        if 0.66 < r < 0.88:
            v = max(v, clamp01(1 - da12 * r / (0.014 + px_w)))
        # "Numerais" em tracos verticais (I, II, III...) na faixa 0.58–0.64, simplificados por grupo
        if 0.57 < r < 0.65:
            for k in range(12):
                a = -math.pi / 2 + TAU * k / 12
                da = (ang - a + math.pi) % TAU - math.pi
                arc = da * r
                strokes = (k % 3) + 1
                for s in range(strokes):
                    off = (s - (strokes - 1) / 2) * 0.022
                    v = max(v, clamp01(1 - abs(arc - off) / (0.004 + px_w)) * 0.8)
        # Brilho do vidro
        if r < 0.9:
            v = max(v, 0.08 + 0.06 * (1 - r))
        put_white(c, x, y, v)
    c.save(os.path.join(OUT, "clock_face.png"))


# =====================================================================================
#  Icones dos efeitos (18x18)
# =====================================================================================
def icon_time_stopped():
    c = Canvas(18, 18)
    gold = (232, 184, 74, 255)
    gold_d = (150, 110, 40, 255)
    face = (226, 226, 236, 255)
    face_d = (170, 170, 186, 255)
    ink = (40, 34, 52, 255)
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d <= 8.0:
                c.set(x, y, gold if d > 6.6 else (face if (x + y) > 15 else face_d))
            if 6.6 < d <= 8.0 and (x + y) > 18:
                c.set(x, y, gold_d)
    # Marcas 12/3/6/9
    for (x, y) in ((8, 3), (9, 3), (14, 8), (14, 9), (8, 14), (9, 14), (3, 8), (3, 9)):
        c.set(x, y, ink)
    # Ponteiros travados (12 e 3)
    for y in range(4, 9):
        c.set(8, y, ink)
    for x in range(9, 13):
        c.set(x, 9, ink)
    c.set(8, 9, (255, 255, 255, 255))
    # Rachadura (o tempo "quebrado")
    for (x, y) in ((12, 3), (11, 4), (12, 5), (13, 6)):
        c.set(x, y, (120, 120, 140, 255))
    c.outline((20, 16, 28, 255))
    c.save(os.path.join(EFFECT, "time_stopped.png"))


def icon_atomic_bubble():
    c = Canvas(18, 18)
    rim = (120, 200, 255, 255)
    rim_d = (60, 130, 220, 255)
    body = (70, 150, 240, 150)
    body_l = (150, 215, 255, 170)
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d <= 8.0:
                if d > 6.8:
                    c.set(x, y, rim if (x + y) < 17 else rim_d)
                else:
                    c.set(x, y, body_l if (x - 5) ** 2 + (y - 5) ** 2 < 10 else body)
    # Favo no meio
    hexc = (225, 245, 255, 255)
    for (x, y) in ((7, 6), (8, 6), (9, 6), (10, 6), (6, 7), (11, 7), (6, 8), (11, 8), (6, 9), (11, 9), (6, 10), (11, 10),
                   (7, 11), (8, 11), (9, 11), (10, 11)):
        c.set(x, y, hexc)
    # Reflexo
    for (x, y) in ((4, 4), (5, 4), (4, 5), (5, 3)):
        c.set(x, y, (255, 255, 255, 255))
    c.outline((16, 40, 80, 255))
    c.save(os.path.join(EFFECT, "atomic_bubble.png"))


def icon_goat_curse():
    rows = [
        "..................",
        "..h............h..",
        ".hh............hh.",
        ".h..............h.",
        ".h...wwwwwwww...h.",
        "..h.wwwwwwwwww.h..",
        "...wwwwwwwwwwww...",
        "..eewwkwwwwkwwee..",
        "..eewwwwwwwwwwee..",
        "....wwwwwwwwww....",
        ".....wwwwwwww.....",
        ".....wwppppww.....",
        "......wppppw......",
        "......wwwwww......",
        ".......bbbb.......",
        ".......bbbb.......",
        "........bb........",
        "..................",
    ]
    pal = {
        "h": (196, 170, 120, 255),
        "w": (236, 228, 212, 255),
        "e": (214, 200, 176, 255),
        "k": (60, 40, 30, 255),
        "p": (226, 170, 160, 255),
        "b": (190, 176, 150, 255),
    }
    c = Canvas(18, 18)
    c.from_rows(rows, pal)
    # Sombra lateral e brilho de maldicao (roxo) no topo
    for y in range(18):
        for x in range(18):
            px = c.get(x, y)
            if px[3] and x > 11 and px[:3] == pal["w"][:3]:
                c.set(x, y, (210, 200, 184, 255))
    for (x, y) in ((8, 1), (9, 1), (8, 2), (9, 2)):
        c.set(x, y, (170, 110, 230, 255))
    c.outline((40, 30, 24, 255))
    c.save(os.path.join(EFFECT, "goat_curse.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(EFFECT, exist_ok=True)
    atomic_swirl()
    atomic_ring()
    atomic_circle()
    atomic_hex()
    holy_circle()
    clock_face()
    icon_time_stopped()
    icon_goat_curse()
    icon_atomic_bubble()
    print("legends_d: texturas geradas em", OUT, "e", EFFECT)
