"""
Assets do modulo "legends_a" (rode da raiz do projeto:  py -I tools/gen/legends_a.py)
  - textures/entity/explosion_sigil.png     128x128  circulo magico runico branco (tingido no renderer): circulos no ceu
  - textures/entity/arsenal_portal.png       64x64   ondulacao dourada em espiral (miolo do portal)
  - textures/entity/arsenal_portal_rim.png   64x64   aro de runas do portal (branco)
  - textures/entity/legend_glow.png          32x32   brilho radial (halos, feixes)
  - textures/entity/crater_fire.png          64x64   fogo turbulento que repete nas bordas (bola de fogo / coluna)
  - textures/entity/crater_smoke.png         64x64   fumaca que repete nas bordas (cogumelo)
  - textures/entity/crater_shockwave.png     64x32   faixa da onda de choque (v=0 borda externa forte, v=1 some)
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "entity")


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


# ---------------------------------------------------------------------------------------------
#  Ruido periodico (repete nas bordas da textura)
# ---------------------------------------------------------------------------------------------
class PeriodicNoise:
    def __init__(self, period, seed):
        rnd = random.Random(seed)
        self.p = period
        self.g = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def value(self, x, y):
        p = self.p
        x0, y0 = int(math.floor(x)) % p, int(math.floor(y)) % p
        x1, y1 = (x0 + 1) % p, (y0 + 1) % p
        fx, fy = x - math.floor(x), y - math.floor(y)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.g[y0][x0] + (self.g[y0][x1] - self.g[y0][x0]) * sx
        b = self.g[y1][x0] + (self.g[y1][x1] - self.g[y1][x0]) * sx
        return a + (b - a) * sy


def fbm(layers, u, v):
    """u, v em 0..1; cada camada tem periodo inteiro, entao a soma repete em 0..1."""
    total, amp, norm = 0.0, 1.0, 0.0
    for n in layers:
        total += amp * n.value(u * n.p, v * n.p)
        norm += amp
        amp *= 0.5
    return total / norm


# ---------------------------------------------------------------------------------------------
#  Desenho suave (campo de distancia) para circulos grandes
# ---------------------------------------------------------------------------------------------
def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
    qx, qy = ax + dx * t, ay + dy * t
    return math.hypot(px - qx, py - qy)


def cover(dist, width):
    """Cobertura 0..1 de uma linha de meia-largura `width` (borda suave de ~1px)."""
    return clamp(width + 0.5 - dist)


def sigil():
    S = 128
    c = Canvas(S, S)
    cx = cy = S / 2
    segs = []
    # Hexagrama inscrito
    R_star = 45.0
    for k in range(2):
        pts = []
        for i in range(3):
            a = math.radians(90 + 120 * i + 60 * k)
            pts.append((cx + math.cos(a) * R_star, cy - math.sin(a) * R_star))
        for i in range(3):
            segs.append((pts[i], pts[(i + 1) % 3], 1.0))
    # Estrela de 8 pontas no miolo
    for i in range(8):
        a = math.radians(22.5 * i)
        b = math.radians(22.5 * i + 135)
        segs.append(((cx + math.cos(a) * 20, cy + math.sin(a) * 20), (cx + math.cos(b) * 20, cy + math.sin(b) * 20), 0.6))
    # Raios curtos entre os aneis de runas
    for i in range(48):
        a = math.radians(7.5 * i)
        r0, r1 = (51.5, 55.0) if i % 2 == 0 else (52.5, 54.0)
        segs.append(((cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1), 0.5))
    # "Runas": pequenos tracos angulares em 16 posicoes na faixa externa
    rnd = random.Random(42)
    for i in range(16):
        a0 = math.radians(22.5 * i + 11.25)
        base_r = 59.0
        tang = (-math.sin(a0), math.cos(a0))
        rad = (math.cos(a0), math.sin(a0))
        ox, oy = cx + rad[0] * base_r, cy + rad[1] * base_r
        shape = rnd.choice([
            [(-2, -1.5), (0, 1.5), (2, -1.5)],
            [(-2, 1.5), (-2, -1.5), (2, -1.5), (2, 1.5)],
            [(-2, 0), (2, 0), (0, 1.5), (0, -1.5)],
            [(-2, -1.5), (2, 1.5), (-2, 1.5), (2, -1.5)],
        ])
        for j in range(len(shape) - 1):
            (u0, v0), (u1, v1) = shape[j], shape[j + 1]
            p0 = (ox + tang[0] * u0 + rad[0] * v0, oy + tang[1] * u0 + rad[1] * v0)
            p1 = (ox + tang[0] * u1 + rad[0] * v1, oy + tang[1] * u1 + rad[1] * v1)
            segs.append((p0, p1, 0.45))
    rings = [(62.5, 1.2), (56.0, 0.8), (50.0, 0.9), (45.5, 0.6), (24.0, 0.9), (20.5, 0.5), (5.0, 0.7)]
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > 64.5:
                continue
            a = 0.0
            for r, w in rings:
                a = max(a, cover(abs(d - r), w))
            for (p0, p1, w) in segs:
                if a >= 1.0:
                    break
                a = max(a, cover(seg_dist(px, py, p0[0], p0[1], p1[0], p1[1]), w))
            # Leve preenchimento brilhante (o circulo "acende" por dentro), mais forte na faixa de runas
            fill = 0.10 + (0.10 if 50 < d < 62.5 else 0.0) + (0.08 if d < 24 else 0.0)
            alpha = clamp(max(a, fill))
            v = 255 if a > 0.3 else 235
            c.set(x, y, (v, v, v, int(alpha * 255)))
    c.save(os.path.join(TEX, "explosion_sigil.png"))


def portal():
    S = 64
    c = Canvas(S, S)
    cx = cy = S / 2
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy) / (S / 2)
            if d > 1.0:
                continue
            ang = math.atan2(dy, dx)
            ripple = 0.5 + 0.5 * math.sin(d * 22.0 - ang * 2.0)
            edge = clamp((1.0 - d) / 0.12)
            core = clamp(1.0 - d / 0.35)
            bright = 0.62 + 0.38 * ripple
            bright = bright + (1 - bright) * core
            r = int(255 * clamp(bright))
            g = int(236 * clamp(bright))
            b = int(170 * clamp(bright * 0.9 + core * 0.4))
            alpha = clamp((0.55 + 0.45 * ripple) * edge + core * 0.4)
            c.set(x, y, (r, g, b, int(alpha * 255)))
    c.save(os.path.join(TEX, "arsenal_portal.png"))


def portal_rim():
    S = 64
    c = Canvas(S, S)
    cx = cy = S / 2
    segs = []
    for i in range(24):
        a = math.radians(15 * i)
        r0, r1 = (26.0, 28.5) if i % 3 else (24.5, 29.0)
        segs.append(((cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1), 0.5))
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > 32:
                continue
            a = max(cover(abs(d - 30.5), 0.9), cover(abs(d - 23.5), 0.6))
            for (p0, p1, w) in segs:
                a = max(a, cover(seg_dist(px, py, p0[0], p0[1], p1[0], p1[1]), w))
            c.set(x, y, (255, 255, 255, int(clamp(a) * 255)))
    c.save(os.path.join(TEX, "arsenal_portal_rim.png"))


def glow():
    S = 32
    c = Canvas(S, S)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - S / 2, y + 0.5 - S / 2) / (S / 2)
            a = clamp(1.0 - d) ** 2
            c.set(x, y, (255, 255, 255, int(a * 255)))
    c.save(os.path.join(TEX, "legend_glow.png"))


def fire():
    S = 64
    c = Canvas(S, S)
    layers = [PeriodicNoise(4, 11), PeriodicNoise(8, 12), PeriodicNoise(16, 13), PeriodicNoise(32, 14)]
    for y in range(S):
        for x in range(S):
            n = fbm(layers, x / S, y / S)
            v = clamp((n - 0.25) / 0.6)
            # Branco-amarelado nas partes quentes, laranja-escuro nas frias (o renderer ainda tinge)
            r = 255
            g = int(150 + 105 * v)
            b = int(70 + 170 * v * v)
            alpha = int(255 * clamp(0.45 + 0.55 * v))
            c.set(x, y, (r, g, b, alpha))
    c.save(os.path.join(TEX, "crater_fire.png"))


def smoke():
    S = 64
    c = Canvas(S, S)
    layers = [PeriodicNoise(4, 21), PeriodicNoise(8, 22), PeriodicNoise(16, 23)]
    for y in range(S):
        for x in range(S):
            n = fbm(layers, x / S, y / S)
            v = clamp((n - 0.2) / 0.65)
            g = int(150 + 105 * v)
            alpha = int(255 * clamp(0.55 + 0.45 * v))
            c.set(x, y, (g, g, g, alpha))
    c.save(os.path.join(TEX, "crater_smoke.png"))


def shockwave():
    W, H = 64, 32
    c = Canvas(W, H)
    layers = [PeriodicNoise(8, 31), PeriodicNoise(16, 32)]
    for y in range(H):
        v = y / (H - 1)   # 0 = borda externa (frente da onda), 1 = miolo
        base = clamp(1.0 - v) ** 1.6
        front = clamp(1.0 - abs(v - 0.12) / 0.12)
        for x in range(W):
            n = fbm(layers, x / W, 0.37)
            a = clamp(base * (0.6 + 0.4 * n) + front * 0.6)
            c.set(x, y, (255, 255, 255, int(a * 255)))
    c.save(os.path.join(TEX, "crater_shockwave.png"))


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    sigil()
    portal()
    portal_rim()
    glow()
    fire()
    smoke()
    shockwave()
    print("legends_a: texturas geradas em", TEX)
