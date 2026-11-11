"""
Gera as texturas dos efeitos visuais do sistema de glifos (pacote client/spellfx + glyph/fx).

  py -I tools/gen/fx_spells.py

Tudo em BRANCO/cinza com alfa suave (anti-serrilhado por super-amostragem): o jogo tinge com a cor do elemento
ou da magia. Saídas:
  - assets/isekai_mahou/textures/particle/fx_*.png   (sprites das partículas: brasa, faísca, gelo, gota, folha,
    pedra, vento, brilho, sombra, runa, fumaça, clarão, onda de choque, círculos mágicos, pilar)
  - assets/isekai_mahou/particles/fx_*.json          (lista de sprites de cada tipo de partícula)
  - assets/isekai_mahou/textures/entity/fx_*.png     (núcleo/halo/rastro do projétil, anel do feixe)
Não usa nada de tools/gen/icons.py.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX_P = os.path.join(ASSETS, "textures", "particle")
TEX_E = os.path.join(ASSETS, "textures", "entity")
PJSON = os.path.join(ASSETS, "particles")
SS = 4  # super-amostragem

for d in (TEX_P, TEX_E, PJSON):
    os.makedirs(d, exist_ok=True)


# =====================================================================================
#  Utilidades
# =====================================================================================
def grid(w, h):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    return xs + 0.5, ys + 0.5


def save_rgba(path, value, alpha):
    """value/alpha: arrays 0..1 (h, w). value = brilho (branco = 1)."""
    v = np.clip(value, 0, 1)
    a = np.clip(alpha, 0, 1)
    rgb = (v * 255).astype(np.uint8)
    img = np.dstack([rgb, rgb, rgb, (a * 255).astype(np.uint8)])
    Image.fromarray(img).save(path)


def save_img(path, img):
    img.save(path)


def canvas(w, h):
    """Canvas de alfa (L) super-amostrado."""
    return Image.new("L", (w * SS, h * SS), 0)


def finish(alpha_img, w, h, value=None, blur=0.0):
    """Reduz o canvas, devolve array de alfa 0..1."""
    if blur > 0:
        alpha_img = alpha_img.filter(ImageFilter.GaussianBlur(blur * SS))
    small = alpha_img.resize((w, h), Image.LANCZOS)
    return np.asarray(small, dtype=np.float64) / 255.0


def write_particle(name, textures):
    with open(os.path.join(PJSON, name + ".json"), "w", encoding="utf-8") as f:
        json.dump({"textures": ["isekai_mahou:" + t for t in textures]}, f, indent=2)
        f.write("\n")


def P(name):
    return os.path.join(TEX_P, name + ".png")


def E(name):
    return os.path.join(TEX_E, name + ".png")


# =====================================================================================
#  Partículas simples
# =====================================================================================
def glow():
    n = 32
    x, y = grid(n, n)
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    a = np.exp(-(d / 0.42) ** 2) + 0.35 * np.exp(-(d / 0.8) ** 2)
    a = np.clip(a, 0, 1) * (d < 1)
    v = 0.75 + 0.25 * np.exp(-(d / 0.3) ** 2)
    save_rgba(P("fx_glow"), v, a)
    write_particle("fx_glow", ["fx_glow"])


def flash():
    n = 64
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2
    d = np.hypot(dx, dy) / (n / 2)
    ang = np.arctan2(dy, dx)
    rays = (np.abs(np.cos(ang * 4)) ** 24) * np.exp(-(d / 0.95) ** 2) * 0.8
    rays2 = (np.abs(np.cos(ang * 4 + math.pi / 4)) ** 40) * np.exp(-(d / 0.6) ** 2) * 0.5
    core = np.exp(-(d / 0.22) ** 2) + 0.45 * np.exp(-(d / 0.55) ** 2)
    a = np.clip(core + rays + rays2, 0, 1) * (d < 1)
    save_rgba(P("fx_flash"), np.ones_like(a), a)
    write_particle("fx_flash", ["fx_flash"])


def shockwave():
    n = 64
    x, y = grid(n, n)
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    ring = np.exp(-((d - 0.88) / 0.06) ** 2)
    inner = np.clip((d - 0.35) / 0.55, 0, 1) ** 2 * 0.35 * (d < 0.9)
    a = np.clip(ring + inner, 0, 1) * (d < 1)
    v = 0.8 + 0.2 * ring
    save_rgba(P("fx_shockwave"), v, a)
    write_particle("fx_shockwave", ["fx_shockwave"])


def streak(name, w, h, soft):
    x, y = grid(w, h)
    u = x / w
    vv = (y - h / 2) / (h / 2)
    across = np.exp(-(vv / soft) ** 2)
    along = np.clip(u / 0.65, 0, 1) ** 1.3 * np.clip((1 - u) / 0.12, 0, 1)
    a = across * along
    v = 0.8 + 0.2 * np.exp(-(vv / (soft * 0.4)) ** 2)
    save_rgba(P(name), v, a)


def spark():
    streak("fx_spark", 32, 8, 0.45)
    write_particle("fx_spark", ["fx_spark"])


def wind():
    names = []
    rnd = random.Random(7)
    for k in range(3):
        w, h = 32, 16
        img = canvas(w, h)
        dr = ImageDraw.Draw(img)
        # arcos finos e curvos (riscos de vento)
        for j in range(2 + k % 2):
            yy = h * (0.3 + 0.4 * rnd.random())
            amp = h * (0.08 + 0.12 * rnd.random())
            pts = []
            for i in range(24):
                t = i / 23
                pts.append(((2 + t * (w - 4)) * SS, (yy + math.sin(t * math.pi * (1 + j)) * amp) * SS))
            for i in range(len(pts) - 1):
                t = i / (len(pts) - 1)
                wdt = max(1, int(SS * (0.6 + 1.2 * math.sin(t * math.pi))))
                c = int(255 * min(1.0, math.sin(t * math.pi) * 1.3) * (0.7 + 0.3 * (j == 0)))
                dr.line([pts[i], pts[i + 1]], fill=c, width=wdt)
        a = finish(img, w, h, blur=0.35)
        nm = f"fx_wind_{k}"
        save_rgba(P(nm), np.ones_like(a), a)
        names.append(nm)
    write_particle("fx_wind", names)


def mote():
    names = []
    for k, (pts, sharp) in enumerate([(4, 10), (6, 14), (4, 22)]):
        n = 16
        x, y = grid(n, n)
        dx, dy = x - n / 2, y - n / 2
        d = np.hypot(dx, dy) / (n / 2)
        ang = np.arctan2(dy, dx)
        star = (np.abs(np.cos(ang * pts / 2)) ** sharp) * np.clip(1 - d, 0, 1) ** 1.2
        core = np.exp(-(d / 0.25) ** 2)
        a = np.clip(star * 1.2 + core, 0, 1)
        nm = f"fx_mote_{k}"
        save_rgba(P(nm), np.ones_like(a), a)
        names.append(nm)
    write_particle("fx_mote", names)


def ember():
    """6 quadros: língua de chama que sobe, afina e vira brasa."""
    names = []
    n = 16
    x, y = grid(n, n)
    for k in range(6):
        t = k / 5
        cx = n / 2 + math.sin(k * 1.7) * 0.8
        h = (0.95 - 0.55 * t)  # altura relativa da chama
        wdt = (0.42 - 0.18 * t)
        # forma de gota invertida: base arredondada embaixo, ponta em cima
        ny = (y - n * 0.78) / (n * 0.7 * h)  # 0 na base, negativo para cima
        nx = (x - cx) / (n / 2)
        up = np.clip(-ny, 0, 1.2)
        width = wdt * np.clip(1 - up, 0, 1) ** 0.8 * (1 + 0.6 * np.exp(-((ny + 0.1) / 0.25) ** 2))
        body = np.exp(-(nx / np.maximum(width, 1e-3)) ** 2 * 2.2)
        base = np.exp(-((ny - 0.0) / 0.22) ** 2) * np.exp(-(nx / (wdt * 1.2)) ** 2)
        mask = (ny < 0.35) & (up < 1.0)
        a = np.clip(np.where(mask, body, 0) + base * 0.8, 0, 1)
        a *= (1 - 0.35 * t)
        v = np.clip(0.65 + 0.35 * np.exp(-(nx / (wdt * 0.6)) ** 2) * np.exp(-((ny + 0.15) / 0.35) ** 2), 0, 1)
        nm = f"fx_ember_{k}"
        save_rgba(P(nm), v, a)
        names.append(nm)
    write_particle("fx_ember", names)


def poly_points(cx, cy, r, sides, rot, jitter=0.0, rnd=None):
    pts = []
    for i in range(sides):
        a = rot + 2 * math.pi * i / sides
        rr = r * (1 + (rnd.uniform(-jitter, jitter) if rnd else 0))
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


def frost():
    names = []
    n = 16
    # 2 flocos de neve
    for k in range(2):
        img = canvas(n, n)
        dr = ImageDraw.Draw(img)
        c = n / 2 * SS
        R = (n / 2 - 1) * SS
        for i in range(6):
            a = math.pi / 6 * k + i * math.pi / 3
            ex, ey = c + math.cos(a) * R, c + math.sin(a) * R
            dr.line([(c, c), (ex, ey)], fill=255, width=int(SS * 1.1))
            for f in (0.45, 0.7):
                bx, by = c + math.cos(a) * R * f, c + math.sin(a) * R * f
                for s in (-1, 1):
                    b = a + s * math.pi / 4
                    L = R * (0.32 if f < 0.5 else 0.22)
                    dr.line([(bx, by), (bx + math.cos(b) * L, by + math.sin(b) * L)], fill=220, width=int(SS * 0.9))
        dr.ellipse([c - SS * 1.4, c - SS * 1.4, c + SS * 1.4, c + SS * 1.4], fill=255)
        a_ = finish(img, n, n, blur=0.2)
        nm = f"fx_frost_{k}"
        save_rgba(P(nm), np.ones_like(a_), a_)
        names.append(nm)
    # 2 estilhaços (losango alongado com faceta clara)
    for k in range(2):
        img = canvas(n, n)
        dr = ImageDraw.Draw(img)
        c = n / 2 * SS
        L, W = (7.2 - k * 1.2) * SS, (2.4 + k * 0.6) * SS
        rot = 0.6 + k * 0.9
        ca, sa = math.cos(rot), math.sin(rot)

        def p(u, v):
            return (c + u * ca - v * sa, c + u * sa + v * ca)
        dr.polygon([p(-L, 0), p(0, -W), p(L, 0), p(0, W)], fill=170)
        dr.polygon([p(-L, 0), p(0, -W), p(L * 0.2, 0)], fill=255)
        a_ = finish(img, n, n)
        img2 = img.resize((n, n), Image.LANCZOS)
        v = np.asarray(img2, dtype=np.float64) / 255.0
        v = np.where(a_ > 0, np.clip(v / np.maximum(a_, 1e-3), 0, 1), 1)
        a_ = np.clip(a_ * 1.6, 0, 1)
        nm = f"fx_frost_{k + 2}"
        save_rgba(P(nm), 0.55 + 0.45 * v, a_)
        names.append(nm)
    write_particle("fx_frost", names)


def droplet():
    names = []
    n = 16
    x, y = grid(n, n)
    for k in range(2):
        cx, cy = n / 2, n * 0.6
        r = 4.2 - k * 1.0
        dx, dy = x - cx, y - cy
        d = np.hypot(dx, dy)
        body = np.clip(r - d + 0.5, 0, 1)
        # ponta para cima
        tip = (y < cy) & (np.abs(dx) < (y - (cy - r * 2.1)) * 0.5) & (y > cy - r * 2.1)
        a = np.clip(body + tip * 1.0, 0, 1) * 0.85
        hl = np.exp(-(((x - (cx - r * 0.35)) / 1.0) ** 2 + ((y - (cy - r * 0.2)) / 1.3) ** 2))
        v = np.clip(0.7 + 0.3 * hl, 0, 1)
        a = np.clip(a + hl * 0.3, 0, 1)
        nm = f"fx_droplet_{k}"
        save_rgba(P(nm), v, a)
        names.append(nm)
    write_particle("fx_droplet", names)


def leaf():
    names = []
    n = 16
    for k in range(4):
        img = canvas(n, n)
        val = Image.new("L", (n * SS, n * SS), 0)
        dr = ImageDraw.Draw(img)
        dv = ImageDraw.Draw(val)
        c = n / 2 * SS
        rot = 0.7 + k * 0.5
        ca, sa = math.cos(rot), math.sin(rot)

        def p(u, v):
            return (c + u * ca - v * sa, c + u * sa + v * ca)
        pts = []
        if k < 2:  # folha: lâmina pontuda
            L, W = 6.6 * SS, (2.8 - 0.5 * k) * SS
            for i in range(21):
                t = i / 20
                pts.append(p(-L + 2 * L * t, W * math.sin(math.pi * t) ** 0.8))
            for i in range(21):
                t = 1 - i / 20
                pts.append(p(-L + 2 * L * t, -W * math.sin(math.pi * t) ** 0.8))
            dr.polygon(pts, fill=255)
            dv.polygon(pts, fill=200)
            dv.line([p(-L, 0), p(L * 0.9, 0)], fill=255, width=int(SS * 0.7))
            dr.line([p(-L - SS * 1.5, 0), p(-L, 0)], fill=255, width=int(SS * 0.8))
            dv.line([p(-L - SS * 1.5, 0), p(-L, 0)], fill=150, width=int(SS * 0.8))
        else:  # pétala: gota arredondada
            L, W = 5.0 * SS, 3.4 * SS
            for i in range(40):
                t = 2 * math.pi * i / 40
                u = math.cos(t) * L
                v = math.sin(t) * W * (0.55 + 0.45 * (1 + math.cos(t)) / 2)
                pts.append(p(u, v))
            dr.polygon(pts, fill=255)
            dv.polygon(pts, fill=235)
            dv.ellipse([c - SS * 1.5, c - SS * 1.5, c + SS * 1.5, c + SS * 1.5], fill=255)
        a = finish(img, n, n)
        vs = np.asarray(val.resize((n, n), Image.LANCZOS), dtype=np.float64) / 255.0
        v = np.where(a > 0.01, np.clip(vs / np.maximum(a, 1e-3), 0, 1), 1)
        nm = f"fx_leaf_{k}"
        save_rgba(P(nm), v, a)
        names.append(nm)
    write_particle("fx_leaf", names)


def stone():
    names = []
    n = 16
    rnd = random.Random(42)
    for k in range(4):
        img = canvas(n, n)
        val = Image.new("L", (n * SS, n * SS), 0)
        dr = ImageDraw.Draw(img)
        dv = ImageDraw.Draw(val)
        c = n / 2 * SS
        r = (4.6 - 0.6 * k) * SS
        pts = poly_points(c, c, r, rnd.randint(5, 7), rnd.random() * 6, 0.3, rnd)
        dr.polygon(pts, fill=255)
        dv.polygon(pts, fill=150)
        # faceta clara em cima, sombra embaixo
        top = [pts[i] for i in range(len(pts)) if pts[i][1] < c + r * 0.2]
        if len(top) >= 2:
            dv.polygon(top + [(c, c)], fill=225)
        dv.line(pts + [pts[0]], fill=90, width=int(SS * 0.6))
        a = finish(img, n, n)
        vs = np.asarray(val.resize((n, n), Image.LANCZOS), dtype=np.float64) / 255.0
        v = np.where(a > 0.01, np.clip(vs / np.maximum(a, 1e-3), 0, 1), 1)
        nm = f"fx_stone_{k}"
        save_rgba(P(nm), v, np.clip(a * 1.2, 0, 1))
        names.append(nm)
    write_particle("fx_stone", names)


def blob_noise(n, seed, blobs, rmin, rmax, spread):
    rnd = random.Random(seed)
    x, y = grid(n, n)
    a = np.zeros((n, n))
    for _ in range(blobs):
        bx = n / 2 + rnd.gauss(0, spread)
        by = n / 2 + rnd.gauss(0, spread)
        br = rnd.uniform(rmin, rmax)
        a = np.maximum(a, np.exp(-(((x - bx) ** 2 + (y - by) ** 2) / (br * br))) * rnd.uniform(0.6, 1.0))
    return a


def wisp():
    """4 quadros de fiapos de sombra (fumaça em espiral)."""
    names = []
    n = 32
    x, y = grid(n, n)
    for k in range(4):
        dx, dy = x - n / 2, y - n / 2
        d = np.hypot(dx, dy) / (n / 2)
        ang = np.arctan2(dy, dx)
        swirl = 0.5 + 0.5 * np.sin(ang * 2 + d * (5 + k) + k * 1.3)
        base = blob_noise(n, 100 + k, 9, 3.0, 6.5, 4.5)
        a = np.clip(base * (0.55 + 0.45 * swirl) * np.clip(1.1 - d, 0, 1) * 1.25, 0, 1)
        a *= 1 - 0.15 * k
        v = 0.55 + 0.45 * swirl
        nm = f"fx_wisp_{k}"
        save_rgba(P(nm), v, a)
        names.append(nm)
    write_particle("fx_wisp", names)


def smoke():
    names = []
    n = 32
    x, y = grid(n, n)
    for k in range(4):
        d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
        base = blob_noise(n, 300 + k, 10, 3.5, 7.0, 4.0)
        a = np.clip(base * np.clip(1.05 - d, 0, 1) * 1.1, 0, 1) * 0.85
        v = 0.75 + 0.25 * base
        nm = f"fx_smoke_{k}"
        save_rgba(P(nm), v, a)
        names.append(nm)
    write_particle("fx_smoke", names)


# =====================================================================================
#  Runas
# =====================================================================================
RUNE_RND = random.Random(1234)
# Cada runa: lista de traços em coordenadas 0..1 (gerada uma vez, determinística)
RUNE_SHAPES = []
for _r in range(16):
    strokes = []
    style = _r % 4
    sx = RUNE_RND.choice([0.5, 0.35, 0.65])
    if style == 0:  # haste vertical
        strokes.append([(sx, 0.08), (sx, 0.92)])
    elif style == 1:  # duas hastes (forma de H/M)
        strokes.append([(0.25, 0.1), (0.25, 0.9)])
        strokes.append([(0.75, 0.1), (0.75, 0.9)])
        sx = 0.25
    elif style == 2:  # haste inclinada
        strokes.append([(0.3, 0.92), (0.7, 0.08)])
        sx = 0.5
    else:  # losango aberto
        strokes.append([(0.5, 0.08), (0.82, 0.5), (0.5, 0.92), (0.18, 0.5), (0.5, 0.08)])
        sx = 0.5
    for _ in range(RUNE_RND.randint(1, 3)):
        kind = RUNE_RND.randint(0, 3)
        y0 = RUNE_RND.choice([0.15, 0.3, 0.5, 0.7])
        if kind == 0:  # diagonal
            strokes.append([(sx, y0), (sx + RUNE_RND.choice([-0.35, 0.35]), y0 + 0.25)])
        elif kind == 1:  # gancho
            d = RUNE_RND.choice([-1, 1])
            strokes.append([(sx, y0), (sx + 0.3 * d, y0 + 0.12), (sx, y0 + 0.28)])
        elif kind == 2:  # barra
            strokes.append([(sx - 0.3, y0), (sx + 0.3, y0 + RUNE_RND.choice([-0.1, 0, 0.1]))])
        else:  # chevron
            strokes.append([(sx - 0.28, y0 + 0.2), (sx, y0), (sx + 0.28, y0 + 0.2)])
    RUNE_SHAPES.append(strokes)


def draw_rune(dr, idx, cx, cy, size, rot, width, fill=255):
    ca, sa = math.cos(rot), math.sin(rot)
    for stroke in RUNE_SHAPES[idx % len(RUNE_SHAPES)]:
        pts = []
        for (u, v) in stroke:
            px, py = (u - 0.5) * size, (v - 0.5) * size
            pts.append((cx + px * ca - py * sa, cy + px * sa + py * ca))
        dr.line(pts, fill=fill, width=max(1, int(width)), joint="curve")


def runes():
    names = []
    n = 16
    for k in range(8):
        img = canvas(n, n)
        dr = ImageDraw.Draw(img)
        draw_rune(dr, k, n / 2 * SS, n / 2 * SS, 12 * SS, 0, 1.4 * SS)
        a = finish(img, n, n)
        glow_ = finish(img, n, n, blur=1.2) * 0.6
        a = np.clip(np.maximum(a, glow_), 0, 1)
        nm = f"fx_rune_{k}"
        save_rgba(P(nm), np.ones_like(a), a)
        names.append(nm)
    write_particle("fx_rune", names)


# =====================================================================================
#  Círculos mágicos (128x128)
# =====================================================================================
def ring(dr, c, r, w, fill=255):
    dr.ellipse([c - r, c - r, c + r, c + r], outline=fill, width=max(1, int(w)))


def circle_main():
    """Círculo principal: anel duplo, faixa de runas, hexagrama, pontos nos vértices."""
    n = 128
    img = canvas(n, n)
    dr = ImageDraw.Draw(img)
    c = n / 2 * SS
    R = (n / 2 - 1.5) * SS
    ring(dr, c, R, 1.6 * SS)
    ring(dr, c, R * 0.86, 1.1 * SS)
    # runas na faixa entre os dois anéis
    count = 20
    for i in range(count):
        a = 2 * math.pi * i / count
        rr = R * 0.93
        draw_rune(dr, i, c + math.cos(a) * rr, c + math.sin(a) * rr, 7.0 * SS, a + math.pi / 2, 0.9 * SS)
    ring(dr, c, R * 0.62, 1.0 * SS)
    # hexagrama
    for off in (0, math.pi / 3):
        pts = poly_points(c, c, R * 0.84, 3, -math.pi / 2 + off)
        dr.line(pts + [pts[0]], fill=255, width=int(1.1 * SS), joint="curve")
    # pontos nos vértices
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        px, py = c + math.cos(a) * R * 0.84, c + math.sin(a) * R * 0.84
        dr.ellipse([px - 2.4 * SS, py - 2.4 * SS, px + 2.4 * SS, py + 2.4 * SS], outline=255, width=int(0.9 * SS))
    # centro: anel pequeno + estrela de 8
    ring(dr, c, R * 0.22, 1.0 * SS)
    for i in range(8):
        a = i * math.pi / 4
        dr.line([(c + math.cos(a) * R * 0.08, c + math.sin(a) * R * 0.08),
                 (c + math.cos(a) * R * 0.32, c + math.sin(a) * R * 0.32)], fill=255, width=int(0.8 * SS))
    a = finish(img, n, n)
    g = finish(img, n, n, blur=2.0) * 0.55
    a = np.clip(np.maximum(a, g), 0, 1)
    save_rgba(P("fx_circle_0"), np.ones_like(a), a)


def circle_inner():
    """Selo interno: quadrados girados, anel de marcações e runas pequenas no meio."""
    n = 128
    img = canvas(n, n)
    dr = ImageDraw.Draw(img)
    c = n / 2 * SS
    R = (n / 2 - 2) * SS
    ring(dr, c, R, 1.2 * SS)
    for i in range(48):
        a = 2 * math.pi * i / 48
        l0 = R * (0.9 if i % 4 else 0.82)
        dr.line([(c + math.cos(a) * l0, c + math.sin(a) * l0), (c + math.cos(a) * R, c + math.sin(a) * R)],
                fill=255, width=int(0.8 * SS))
    for off in (0, math.pi / 4):
        pts = poly_points(c, c, R * 0.78, 4, off)
        dr.line(pts + [pts[0]], fill=255, width=int(1.1 * SS))
    ring(dr, c, R * 0.55, 1.0 * SS)
    for i in range(8):
        a = 2 * math.pi * i / 8 + math.pi / 8
        draw_rune(dr, i + 5, c + math.cos(a) * R * 0.42, c + math.sin(a) * R * 0.42, 6 * SS, a + math.pi / 2, 0.9 * SS)
    pts = poly_points(c, c, R * 0.3, 5, -math.pi / 2)
    star = [pts[(i * 2) % 5] for i in range(5)]
    dr.line(star + [star[0]], fill=255, width=int(1.0 * SS))
    a = finish(img, n, n)
    g = finish(img, n, n, blur=2.0) * 0.5
    a = np.clip(np.maximum(a, g), 0, 1)
    save_rgba(P("fx_circle_1"), np.ones_like(a), a)


def circle_outer():
    """Camada de ultimate: anel largo com runas grandes e raios para fora."""
    n = 128
    img = canvas(n, n)
    dr = ImageDraw.Draw(img)
    c = n / 2 * SS
    R = (n / 2 - 1.5) * SS
    ring(dr, c, R, 1.4 * SS)
    ring(dr, c, R * 0.76, 1.4 * SS)
    ring(dr, c, R * 0.72, 0.8 * SS)
    count = 12
    for i in range(count):
        a = 2 * math.pi * i / count
        draw_rune(dr, i + 3, c + math.cos(a) * R * 0.88, c + math.sin(a) * R * 0.88, 10 * SS, a + math.pi / 2, 1.1 * SS)
        b = a + math.pi / count
        dr.line([(c + math.cos(b) * R * 0.78, c + math.sin(b) * R * 0.78),
                 (c + math.cos(b) * R * 0.98, c + math.sin(b) * R * 0.98)], fill=255, width=int(0.8 * SS))
    # pentagrama grande + círculo interno
    pts = poly_points(c, c, R * 0.7, 5, -math.pi / 2)
    star = [pts[(i * 2) % 5] for i in range(5)]
    dr.line(star + [star[0]], fill=255, width=int(1.2 * SS))
    ring(dr, c, R * 0.3, 1.0 * SS)
    a = finish(img, n, n)
    g = finish(img, n, n, blur=2.2) * 0.55
    a = np.clip(np.maximum(a, g), 0, 1)
    save_rgba(P("fx_circle_2"), np.ones_like(a), a)


def circle_simple():
    """Magias fracas: anel simples com marcações."""
    n = 64
    img = canvas(n, n)
    dr = ImageDraw.Draw(img)
    c = n / 2 * SS
    R = (n / 2 - 1.5) * SS
    ring(dr, c, R, 1.2 * SS)
    ring(dr, c, R * 0.7, 0.8 * SS)
    for i in range(16):
        a = 2 * math.pi * i / 16
        dr.line([(c + math.cos(a) * R * 0.74, c + math.sin(a) * R * 0.74),
                 (c + math.cos(a) * R * (0.9 if i % 2 else 0.97), c + math.sin(a) * R * (0.9 if i % 2 else 0.97))],
                fill=255, width=int(0.8 * SS))
    pts = poly_points(c, c, R * 0.66, 4, 0)
    dr.line(pts + [pts[0]], fill=255, width=int(0.8 * SS))
    a = finish(img, n, n)
    g = finish(img, n, n, blur=1.5) * 0.5
    a = np.clip(np.maximum(a, g), 0, 1)
    # mesmo tamanho dos outros no atlas não é necessário
    save_rgba(P("fx_circle_3"), np.ones_like(a), a)


def circles():
    circle_main()
    circle_inner()
    circle_outer()
    circle_simple()
    write_particle("fx_circle", ["fx_circle_0", "fx_circle_1", "fx_circle_2", "fx_circle_3"])


def pillar():
    w, h = 32, 64
    x, y = grid(w, h)
    u = (x - w / 2) / (w / 2)
    v = y / h  # 0 = topo
    across = np.exp(-(u / 0.55) ** 2) * 0.75 + np.exp(-(u / 0.15) ** 2) * 0.5
    along = np.clip(v / 0.6, 0, 1) ** 1.5 * np.clip((1 - v) / 0.04, 0, 1)
    streaks = 0.85 + 0.15 * np.sin(u * 18 + v * 3)
    a = np.clip(across * along * streaks, 0, 1)
    vv = 0.8 + 0.2 * np.exp(-(u / 0.15) ** 2)
    save_rgba(P("fx_pillar"), vv, a)
    write_particle("fx_pillar", ["fx_pillar"])


# =====================================================================================
#  Entidade: projétil e feixe
# =====================================================================================
def orb_core():
    n = 32
    x, y = grid(n, n)
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    a = np.clip(np.exp(-(d / 0.5) ** 4) + 0.5 * np.exp(-(d / 0.75) ** 2), 0, 1) * (d < 1)
    v = 0.7 + 0.3 * np.exp(-(d / 0.35) ** 2)
    save_rgba(E("fx_orb_core"), v, a)


def orb_glow():
    n = 64
    x, y = grid(n, n)
    dx, dy = x - n / 2, y - n / 2
    d = np.hypot(dx, dy) / (n / 2)
    ang = np.arctan2(dy, dx)
    halo = np.exp(-(d / 0.45) ** 2) * 0.9 + 0.3 * np.exp(-(d / 0.85) ** 2)
    rays = (np.abs(np.cos(ang * 3)) ** 30) * np.exp(-(d / 0.9) ** 2) * 0.35
    a = np.clip(halo + rays, 0, 1) * (d < 1)
    save_rgba(E("fx_orb_glow"), np.ones_like(a), a)


def trail():
    w, h = 64, 16
    x, y = grid(w, h)
    u = x / w
    vv = (y - h / 2) / (h / 2)
    rnd = np.random.default_rng(5)
    noise = np.zeros((h, w))
    for k in range(5):
        phase = rnd.uniform(0, 6.28)
        freq = rnd.uniform(1, 3)
        off = rnd.uniform(-0.5, 0.5)
        noise += np.exp(-((vv - off * 0.6) / 0.18) ** 2) * (0.5 + 0.5 * np.sin(u * 2 * math.pi * freq + phase))
    across = np.exp(-(vv / 0.55) ** 2)
    a = np.clip(across * (0.55 + 0.45 * noise / 2.2), 0, 1)
    v = 0.8 + 0.2 * np.exp(-(vv / 0.25) ** 2)
    save_rgba(E("fx_trail"), v, a)


def beam_ring():
    n = 64
    x, y = grid(n, n)
    d = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    a = np.clip(np.exp(-((d - 0.85) / 0.07) ** 2) + 0.4 * np.exp(-((d - 0.62) / 0.04) ** 2), 0, 1) * (d < 1)
    save_rgba(E("fx_beam_ring"), np.ones_like(a), a)


def main():
    glow()
    flash()
    shockwave()
    spark()
    wind()
    mote()
    ember()
    frost()
    droplet()
    leaf()
    stone()
    wisp()
    smoke()
    runes()
    circles()
    pillar()
    orb_core()
    orb_glow()
    trail()
    beam_ring()
    print("fx_spells: texturas e particles/*.json gerados")


if __name__ == "__main__":
    main()
