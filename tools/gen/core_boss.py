"""
Gera as texturas do kit de chefes do nucleo (telegrafias no chao), em BRANCO com alpha — o Java tinge pela cor:
  assets/isekai_mahou/textures/misc/telegraph_runes.png    (256x32) faixa de 8 runas que se repete em volta dos circulos
  assets/isekai_mahou/textures/misc/telegraph_chevron.png  (32x32)  seta ">" que corre ao longo das linhas (aponta para +x)
Rodar:  py -I tools/gen/core_boss.py
"""
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

OUT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/misc")
WHITE = (255, 255, 255, 255)


def thick(c, x0, y0, x1, y1, col, w=2):
    """Linha com espessura w (repete deslocada na horizontal e na vertical)."""
    for ox in range(w):
        for oy in range(w):
            c.line(x0 + ox, y0 + oy, x1 + ox, y1 + oy, col)


def ring(c, cx, cy, r, col):
    """Circulo vazado de 1px."""
    for y in range(c.h):
        for x in range(c.w):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if abs(d - r) < 0.6:
                c.set(x, y, col)


def glow(c, alpha=80):
    """Halo de 1px em volta dos tracos (brilho), sem cobrir os tracos."""
    src = [row[:] for row in c.px]
    for y in range(c.h):
        for x in range(c.w):
            if src[y][x][3] != 0:
                continue
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if 0 <= nx < c.w and 0 <= ny < c.h and src[ny][nx][3] >= 200:
                    c.set(x, y, (255, 255, 255, alpha))
                    break


def rune(c, ox, rnd):
    """Uma runa 32x32 na coluna ox: haste central + 2-3 tracos sorteados (estilo futhark/circulo magico)."""
    cx = ox + 15
    top, bot = 7, 24
    thick(c, cx, top, cx, bot, WHITE)
    strokes = rnd.sample(["diag_up_l", "diag_up_r", "diag_dn_l", "diag_dn_r", "circle_top", "circle_bot",
                          "bar_mid", "bar_top", "hook", "dots", "arrow"], 3)
    for s in strokes:
        if s == "diag_up_l":
            thick(c, cx, top + 5, cx - 7, top, WHITE)
        elif s == "diag_up_r":
            thick(c, cx, top + 5, cx + 7, top, WHITE)
        elif s == "diag_dn_l":
            thick(c, cx, bot - 6, cx - 7, bot - 1, WHITE)
        elif s == "diag_dn_r":
            thick(c, cx, bot - 6, cx + 7, bot - 1, WHITE)
        elif s == "circle_top":
            ring(c, cx + 1, top + 3, 3, WHITE)
        elif s == "circle_bot":
            ring(c, cx + 1, bot - 2, 3, WHITE)
        elif s == "bar_mid":
            thick(c, cx - 6, 15, cx + 7, 15, WHITE)
        elif s == "bar_top":
            thick(c, cx - 5, top, cx + 6, top, WHITE)
        elif s == "hook":
            thick(c, cx, 12, cx + 6, 17, WHITE)
            thick(c, cx + 6, 17, cx + 2, 21, WHITE)
        elif s == "dots":
            c.rect(cx - 6, 11, cx - 5, 12, WHITE)
            c.rect(cx + 6, 19, cx + 7, 20, WHITE)
        elif s == "arrow":
            thick(c, cx - 5, 20, cx, 15, WHITE)
            thick(c, cx, 15, cx + 6, 20, WHITE)


def runes():
    c = Canvas(256, 32)
    rnd = random.Random(7)
    for i in range(8):
        rune(c, i * 32, rnd)
        # Losango separador entre as runas (cruza a emenda: a faixa se repete sem corte)
        sx = i * 32
        for d in range(-2, 3):
            x = (sx + d) % 256
            c.set(x, 16 - (2 - abs(d)), WHITE)
            c.set(x, 16 + (2 - abs(d)), WHITE)
    # Bordas: linha cheia + linha pontilhada (aro do circulo magico)
    for x in range(256):
        c.set(x, 1, (255, 255, 255, 230))
        c.set(x, 30, (255, 255, 255, 230))
        if x % 4 < 2:
            c.set(x, 3, (255, 255, 255, 140))
            c.set(x, 28, (255, 255, 255, 140))
    glow(c, 70)
    c.save(os.path.join(OUT, "telegraph_runes.png"))


def chevron():
    c = Canvas(32, 32)
    # Rastro mais fraco atras da seta (sensacao de movimento)
    faint = (255, 255, 255, 110)
    thick(c, 2, 6, 11, 15, faint, 3)
    thick(c, 11, 16, 2, 25, faint, 3)
    thick(c, 12, 4, 23, 15, WHITE, 4)
    thick(c, 23, 16, 12, 27, WHITE, 4)
    glow(c, 90)
    c.save(os.path.join(OUT, "telegraph_chevron.png"))


if __name__ == "__main__":
    runes()
    chevron()
    print("ok: telegraph_runes.png, telegraph_chevron.png")
