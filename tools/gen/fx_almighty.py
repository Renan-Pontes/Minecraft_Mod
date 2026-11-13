"""
Texturas do Todo-Poderoso (w10) em textures/entity/:
  quincy_fx_ink.png        mancha de tinta/sombra BRANCA com borda irregular (o jogo tinge de preto: poças, aura,
                           tentáculos, borrões)
  quincy_fx_shadow_eye.png olho do Todo-Poderoso dentro de uma mancha de tinta negra: esclera clara, íris escura e
                           VÁRIAS pupilas (ja colorido)
NAO mexe em nenhuma outra textura.

Uso:  py -I tools/gen/fx_almighty.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ENT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/entity")
SS = 4  # supersample


def save(im, name):
    os.makedirs(ENT, exist_ok=True)
    im.save(os.path.join(ENT, name))
    print("ok", name)


def clamp(v):
    return max(0, min(255, int(round(v))))


def value_noise(size, cells, seed):
    rnd = random.Random(seed)
    g = [[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)]

    def at(x, y):
        fx, fy = x / size * cells, y / size * cells
        ix, iy = int(fx), int(fy)
        tx, ty = fx - ix, fy - iy
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[iy][ix] * (1 - tx) + g[iy][min(cells, ix + 1)] * tx
        b = g[min(cells, iy + 1)][ix] * (1 - tx) + g[min(cells, iy + 1)][min(cells, ix + 1)] * tx
        return a * (1 - ty) + b * ty

    return at


def ink(size=64, seed=7):
    """Mancha de tinta: forte no meio, borda rasgada por ruído (com respingos)."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = im.load()
    n1 = value_noise(size, 4, seed)
    n2 = value_noise(size, 9, seed + 1)
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / (size / 2)
            ang = math.atan2(y - c, x - c)
            wob = 0.09 * math.sin(ang * 5 + 1.3) + 0.05 * math.sin(ang * 9 + 0.4)
            edge = 0.78 + wob + 0.35 * (n1(x, y) - 0.5) + 0.15 * (n2(x, y) - 0.5)
            a = 1.0 - max(0.0, d - edge * 0.55) / max(0.05, edge * 0.45)
            a = max(0.0, min(1.0, a)) ** 1.4
            px[x, y] = (255, 255, 255, clamp(255 * a))
    # respingos
    d = ImageDraw.Draw(im)
    rnd = random.Random(seed + 5)
    for _ in range(7):
        ang = rnd.random() * math.tau
        r = size * (0.36 + rnd.random() * 0.1)
        x, y = c + math.cos(ang) * r, c + math.sin(ang) * r
        s = 1 + rnd.random() * 2.2
        d.ellipse([x - s, y - s, x + s, y + s], fill=(255, 255, 255, 200))
    return im.filter(ImageFilter.GaussianBlur(0.6))


def shadow_eye(size=64, seed=11):
    S = size * SS
    base = ink(S, seed).resize((S, S))
    # a mancha vira tinta NEGRA (com um leve roxo-azulado nas bordas)
    px = base.load()
    for y in range(S):
        for x in range(S):
            a = px[x, y][3]
            px[x, y] = (6, 4, 12, clamp(min(255, a * 1.15)))
    ink_only = base.copy()
    im = base.copy()
    d = ImageDraw.Draw(im)
    cx, cy = S / 2, S / 2
    w, h = S * 0.40, S * 0.22
    top = [(cx - w + 2 * w * i / 40, cy - math.sin(i / 40 * math.pi) * h) for i in range(41)]
    bot = [(cx + w - 2 * w * i / 40, cy + math.sin(i / 40 * math.pi) * h * 0.85) for i in range(41)]
    d.polygon(top + bot, fill=(232, 226, 214, 255))
    # veias finas na esclera
    rnd = random.Random(seed)
    for _ in range(8):
        sx = cx + (rnd.random() - 0.5) * w * 1.6
        sy = cy + (rnd.random() - 0.5) * h * 0.9
        ex = sx + (rnd.random() - 0.5) * w * 0.5
        ey = sy + (rnd.random() - 0.5) * h * 0.4
        d.line([(sx, sy), (ex, ey)], fill=(170, 40, 40, 140), width=max(1, SS // 2))
    # íris escura (carmim → negro) e várias pupilas
    ir = h * 0.95
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=(40, 8, 14, 255))
    ir2 = ir * 0.78
    d.ellipse([cx - ir2, cy - ir2, cx + ir2, cy + ir2], fill=(96, 18, 26, 255))
    pupils = [(0, 0)] + [(math.cos(a) * ir * 0.48, math.sin(a) * ir * 0.48) for a in [i * math.tau / 6 + 0.3 for i in range(6)]]
    for i, (ox, oy) in enumerate(pupils):
        pr = ir * (0.2 if i == 0 else 0.13)
        d.ellipse([cx + ox - pr, cy + oy - pr, cx + ox + pr, cy + oy + pr], fill=(4, 2, 6, 255))
    # brilho
    hr = ir * 0.12
    d.ellipse([cx - ir * 0.45 - hr, cy - ir * 0.5 - hr, cx - ir * 0.45 + hr, cy - ir * 0.5 + hr], fill=(255, 255, 255, 230))
    # recorta a íris para dentro do olho: o que saiu do contorno volta a ser tinta
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(top + bot, fill=255)
    out = Image.composite(im, ink_only, mask)
    # pálpebras (contorno grosso de tinta)
    od = ImageDraw.Draw(out)
    od.line(top, fill=(4, 2, 8, 255), width=SS * 2)
    od.line(bot, fill=(4, 2, 8, 255), width=SS * 2)
    return out.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    save(ink(64), "quincy_fx_ink.png")
    save(shadow_eye(64), "quincy_fx_shadow_eye.png")
