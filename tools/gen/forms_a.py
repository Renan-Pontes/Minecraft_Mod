"""
Texturas do modulo forms_a (rode da raiz do projeto: py -I tools/gen/forms_a.py).

  textures/entity/spell_falling.png       nucleo do meteoro da Queda (32x32, tons de cinza: o renderer pinta com a cor da magia)
  textures/entity/spell_falling_glow.png  halo suave em volta do meteoro (32x32, branco com alpha radial + raios)
  textures/entity/spell_beam.png          nucleo do raio do Feixe (32x64; U = largura, V = comprimento, repete em V;
                                          branco com alpha: camada translucida "beacon_beam")
  textures/entity/spell_beam_glow.png     halo do raio (32x64, repete em V; PRE-MULTIPLICADO: cor = brilho, para a
                                          camada aditiva "eyes", onde preto = invisivel)
  textures/entity/spell_beam_flare.png    clarao das pontas do raio (32x32, radial com raios; pre-multiplicado)
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

TEX = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/entity")


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def meteor_core():
    """Rocha arredondada e irregular, com rachaduras incandescentes e brilho no canto de cima."""
    size = 32
    c = Canvas(size, size)
    rnd = random.Random(7)
    cx = cy = 16.0
    # Contorno irregular: raio varia com o angulo (algumas "lascas")
    bumps = [rnd.uniform(-1.4, 1.2) for _ in range(12)]

    def radius_at(a):
        t = (a / (2 * math.pi)) * len(bumps)
        i = int(t) % len(bumps)
        j = (i + 1) % len(bumps)
        f = t - int(t)
        f = f * f * (3 - 2 * f)
        return 11.5 + bumps[i] * (1 - f) + bumps[j] * f

    # Rachaduras: caminhos aleatorios saindo do centro
    cracks = set()
    for k in range(6):
        a = k * math.pi / 3 + rnd.uniform(-0.35, 0.35)
        x, y = cx, cy
        for _ in range(rnd.randint(7, 11)):
            a += rnd.uniform(-0.5, 0.5)
            x += math.cos(a)
            y += math.sin(a)
            cracks.add((int(x), int(y)))
            if rnd.random() < 0.12:
                cracks.add((int(x) + rnd.choice((-1, 1)), int(y)))

    for py in range(size):
        for px in range(size):
            dx, dy = px + 0.5 - cx, py + 0.5 - cy
            d = math.hypot(dx, dy)
            r = radius_at(math.atan2(dy, dx) % (2 * math.pi))
            if d > r:
                continue
            # Sombreamento esferico (luz em cima a esquerda) + granulado da rocha
            lx, ly = (dx + 4.0) / r, (dy + 4.0) / r
            light = 1.0 - min(1.0, math.hypot(lx, ly))
            rim = max(0.0, (d / r - 0.8) / 0.2)
            v = 78 + 90 * light - 40 * rim + rnd.uniform(-12, 12)
            if (px, py) in cracks:
                v = 255
            elif any((px + ox, py + oy) in cracks for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                v = max(v, 165)
            g = clamp(v)
            c.set(px, py, (g, g, g, 255))
    # Borda escura de 1px para o meteoro "destacar" do halo
    c.outline((40, 40, 40, 190))
    # Centro bem quente
    for py in range(size):
        for px in range(size):
            d = math.hypot(px + 0.5 - cx, py + 0.5 - cy)
            if d < 4.5 and c.get(px, py)[3] > 0:
                p = c.get(px, py)
                f = 1 - d / 4.5
                g = clamp(p[0] + (255 - p[0]) * f * 0.8)
                c.set(px, py, (g, g, g, 255))
    return c


def meteor_glow():
    """Halo radial com 8 raios suaves; branco puro com alpha decrescente."""
    size = 32
    c = Canvas(size, size)
    cx = cy = 16.0
    for py in range(size):
        for px in range(size):
            dx, dy = px + 0.5 - cx, py + 0.5 - cy
            d = math.hypot(dx, dy) / 16.0
            if d >= 1.0:
                continue
            a = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.cos(a * 8)
            base = (1.0 - d) ** 1.8
            alpha = base * (0.7 + 0.3 * rays) + max(0.0, 0.4 - d) * 1.5
            c.set(px, py, (255, 255, 255, clamp(alpha * 255)))
    return c


BEAM_W, BEAM_H = 32, 64


def _beam_phases(seed):
    """Fases suaves por coluna (U) para os filamentos de energia: variam devagar de uma coluna para a outra."""
    rnd = random.Random(seed)
    knots = [rnd.random() for _ in range(5)]
    out = []
    for px in range(BEAM_W):
        t = px / (BEAM_W - 1) * (len(knots) - 1)
        i = min(int(t), len(knots) - 2)
        f = t - i
        f = f * f * (3 - 2 * f)
        out.append(knots[i] * (1 - f) + knots[i + 1] * f)
    return out


def _streak(px, py, ph1, ph2):
    """Filamentos ao longo do raio; frequencias inteiras em V para a textura repetir sem emenda."""
    v = py / BEAM_H
    return 0.6 * math.sin(2 * math.pi * (2 * v + ph1[px])) + 0.4 * math.sin(2 * math.pi * (5 * v + ph2[px] * 2))


def _knots(py, centers, width):
    """"Pulsos" de energia (gaussianas em V, com volta: repete sem emenda)."""
    total = 0.0
    for c in centers:
        dv = abs(py + 0.5 - c)
        dv = min(dv, BEAM_H - dv)
        total += math.exp(-(dv / width) ** 2)
    return total


def beam_core():
    """Nucleo do raio: opaco no meio, some nas bordas, com filamentos e pulsos correndo ao longo dele."""
    c = Canvas(BEAM_W, BEAM_H)
    ph1, ph2 = _beam_phases(11), _beam_phases(23)
    pulses = [6, 27, 45]
    for py in range(BEAM_H):
        for px in range(BEAM_W):
            d = abs(px + 0.5 - BEAM_W / 2) / (BEAM_W / 2)
            base = max(0.0, 1.0 - d * d) ** 1.5
            s = _streak(px, py, ph1, ph2)
            k = _knots(py, pulses, 4.0) * max(0.0, 1.0 - d * 1.6)
            a = base * (0.72 + 0.2 * s) + 0.35 * k
            if d < 0.18:
                a = max(a, 0.95)
            g = 205 + 50 * (1.0 - d) + 20 * k
            c.set(px, py, (clamp(g), clamp(g), clamp(g), clamp(a * 255)))
    return c


def beam_glow():
    """Halo do raio, pre-multiplicado (cor = brilho): gaussiana larga com filamentos fracos."""
    c = Canvas(BEAM_W, BEAM_H)
    ph1, ph2 = _beam_phases(31), _beam_phases(47)
    for py in range(BEAM_H):
        for px in range(BEAM_W):
            d = abs(px + 0.5 - BEAM_W / 2) / (BEAM_W / 2)
            b = math.exp(-(d * 2.4) ** 2) * (0.86 + 0.14 * _streak(px, py, ph1, ph2))
            v = clamp(b * 255)
            c.set(px, py, (v, v, v, v))
    return c


def beam_flare():
    """Clarao das pontas, pre-multiplicado: miolo forte + 4 raios longos + 4 raios finos na diagonal."""
    size = 32
    c = Canvas(size, size)
    cx = cy = 16.0
    for py in range(size):
        for px in range(size):
            dx, dy = px + 0.5 - cx, py + 0.5 - cy
            r = math.hypot(dx, dy) / 16.0
            if r >= 1.0:
                continue
            a = math.atan2(dy, dx)
            core = (1.0 - r) ** 2.4
            # |cos 2a| vale 1 em 0, 90, 180 e 270 graus (4 raios); |sin 2a| nas diagonais
            rays = abs(math.cos(2 * a)) ** 28 * (1.0 - r) ** 1.2 * 0.8
            rays += abs(math.sin(2 * a)) ** 60 * (1.0 - r) ** 1.6 * 0.45
            v = clamp(min(1.0, core + rays) * 255)
            c.set(px, py, (v, v, v, v))
    return c


if __name__ == "__main__":
    meteor_core().save(os.path.join(TEX, "spell_falling.png"))
    meteor_glow().save(os.path.join(TEX, "spell_falling_glow.png"))
    beam_core().save(os.path.join(TEX, "spell_beam.png"))
    beam_glow().save(os.path.join(TEX, "spell_beam_glow.png"))
    beam_flare().save(os.path.join(TEX, "spell_beam_flare.png"))
    print("forms_a: texturas geradas em", TEX)
