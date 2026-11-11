"""
Gera as texturas do modulo legends_b (Magias Lendarias B).

  py -I tools/gen/legends_b.py

- converge_swirl.png (64x64, repete sem emenda): redemoinho de energia turbulento das cascas da Convergencia.
  Branco sobre PRETO (o tipo de render energySwirl soma a cor: preto = nada), tingido em jogo pela cor da magia.
- spiral_swirl.png (64x64, repete sem emenda): riscos de vento em diagonal da Esfera Espiral.
- sky_lens.png (128x128): circulo magico das Lancas do Ceu (aneis, runas, hexagrama, raios), branco com alfa,
  tingido em jogo.
Tudo anti-serrilhado por funcoes de distancia.
"""
import math
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "entity", "legends_b")
TAU = math.pi * 2


def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


def smooth(e0, e1, x):
    t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def put_gray(c, x, y, v):
    """Pixel com intensidade v no RGB e no alfa (energySwirl soma o RGB: preto = nada)."""
    k = int(round(clamp01(v) * 255))
    c.set(x, y, (k, k, k, k))


def put_white(c, x, y, v):
    """Pixel branco com alfa v (circulos somados com SRC_ALPHA: o alfa da a intensidade)."""
    k = int(round(clamp01(v) * 255))
    c.set(x, y, (255, 255, 255, k) if k > 0 else (0, 0, 0, 0))


# =====================================================================================
#  Cascas de energia (repetem sem emenda: so frequencias inteiras em u e v)
# =====================================================================================
def converge_swirl():
    n = 64
    c = Canvas(n, n)
    rnd = random.Random(7)
    phases = [rnd.random() * TAU for _ in range(6)]
    for y in range(n):
        for x in range(n):
            u, v = x / n, y / n
            # Distorcao de dominio: as faixas se torcem como espaco deformado
            warp = 1.9 * math.sin(TAU * (2 * v - u) + phases[0]) + 1.3 * math.cos(TAU * (u + 2 * v) + phases[1])
            band = 0.5 + 0.5 * math.sin(TAU * (3 * u + v) + warp)
            fine = 0.5 + 0.5 * math.sin(TAU * (5 * u - 3 * v) + 2.0 * math.sin(TAU * (u + v) + phases[2]))
            wisp = 0.5 + 0.5 * math.sin(TAU * (u - 4 * v) + 1.5 * math.sin(TAU * 3 * u + phases[3]))
            val = band ** 5 * 0.95 + fine ** 10 * 0.55 + wisp ** 14 * 0.45 + 0.04
            put_gray(c, x, y, val)
    c.save(os.path.join(OUT, "converge_swirl.png"))


def spiral_swirl():
    n = 64
    c = Canvas(n, n)
    for y in range(n):
        for x in range(n):
            u, v = x / n, y / n
            streak = 0.5 + 0.5 * math.sin(TAU * (2 * u + 4 * v) + 1.4 * math.sin(TAU * 3 * u))
            thin = 0.5 + 0.5 * math.sin(TAU * (4 * u + 6 * v) + 2.2 * math.sin(TAU * (u - v)))
            gust = 0.5 + 0.5 * math.sin(TAU * (1 * u + 3 * v) + 0.9)
            val = streak ** 7 * 0.9 + thin ** 16 * 0.7 + gust ** 3 * 0.18 + 0.03
            put_gray(c, x, y, val)
    c.save(os.path.join(OUT, "spiral_swirl.png"))


# =====================================================================================
#  Circulo magico das Lancas do Ceu
# =====================================================================================
def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    qx, qy = ax + dx * t, ay + dy * t
    return math.hypot(px - qx, py - qy)


def sky_lens():
    n = 128
    c = Canvas(n, n)
    px_w = 1.0 / (n * 0.5)  # 1 pixel em unidades do raio
    rnd = random.Random(11)
    # Runas: tracos pequenos em 24 posicoes da faixa entre os aneis externos
    runes = []
    for i in range(24):
        a = TAU * i / 24
        kind = rnd.randint(0, 3)
        runes.append((a, kind, rnd.random()))
    hexa = []
    for k in range(2):
        pts = []
        for j in range(3):
            a = TAU * j / 3 + k * math.pi / 3 - math.pi / 2
            pts.append((math.cos(a) * 0.80, math.sin(a) * 0.80))
        for j in range(3):
            hexa.append((pts[j], pts[(j + 1) % 3]))
    for y in range(n):
        for x in range(n):
            # coordenadas -1..1 no centro do pixel
            fx = (x + 0.5) / (n * 0.5) - 1
            fy = (y + 0.5) / (n * 0.5) - 1
            r = math.hypot(fx, fy)
            ang = math.atan2(fy, fx)
            v = 0.0

            def ring(rad, w):
                return clamp01(1 - abs(r - rad) / (w + px_w))

            v = max(v, ring(0.95, 0.018))
            v = max(v, ring(0.87, 0.008) * 0.85)
            v = max(v, ring(0.84, 0.006) * 0.7)
            v = max(v, ring(0.48, 0.012) * 0.9)
            v = max(v, ring(0.42, 0.005) * 0.6)
            v = max(v, ring(0.16, 0.012))
            # Runas na faixa 0.87–0.95
            if 0.87 < r < 0.95:
                for a, kind, extra in runes:
                    da = (ang - a + math.pi) % TAU - math.pi
                    arc = abs(da) * r
                    if arc > 0.07:
                        continue
                    t = (r - 0.87) / 0.08
                    if kind == 0:      # traco radial
                        v = max(v, clamp01(1 - arc / (0.008 + px_w)) * 0.9)
                    elif kind == 1:    # traco + ponto
                        if t < 0.55:
                            v = max(v, clamp01(1 - arc / (0.008 + px_w)) * 0.9)
                        elif abs(t - 0.8) < 0.15:
                            v = max(v, clamp01(1 - math.hypot(arc, (t - 0.8) * 0.08) / (0.012 + px_w)))
                    elif kind == 2:    # "V"
                        v = max(v, clamp01(1 - abs(arc - 0.035 * (1 - t)) / (0.007 + px_w)) * 0.85)
                    else:              # arco curto
                        if abs(t - 0.5) < 0.12:
                            v = max(v, clamp01(1 - abs(arc) / (0.045 + 0.02 * extra)) * 0.8)
            # Hexagrama
            if r < 0.86:
                for (a1, b1) in hexa:
                    d = seg_dist(fx, fy, a1[0], a1[1], b1[0], b1[1])
                    v = max(v, clamp01(1 - d / (0.008 + px_w)) * 0.8)
            # Raios entre os aneis internos e o hexagrama
            if 0.16 < r < 0.48:
                for i in range(8):
                    a = TAU * i / 8 + math.pi / 8
                    da = (ang - a + math.pi) % TAU - math.pi
                    v = max(v, clamp01(1 - abs(da) * r / (0.006 + px_w)) * 0.7)
            # Brilho da lente (miolo suave)
            if r < 0.48:
                v = max(v, 0.18 * (1 - r / 0.48) + 0.05)
            put_white(c, x, y, v)
    c.save(os.path.join(OUT, "sky_lens.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    converge_swirl()
    spiral_swirl()
    sky_lens()
    print("legends_b: texturas geradas em", OUT)
