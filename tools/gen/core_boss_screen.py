"""
Textura da vinheta das maldições de chefe (v1.3), em BRANCO com alpha — o Java tinge pela cor de cada maldição:
  assets/isekai_mahou/textures/misc/boss_vignette.png  (256x256) centro transparente, bordas opacas (curva suave)
e os ícones (18x18) das maldições em assets/isekai_mahou/textures/mob_effect/boss_*.png
Rodar:  py -I tools/gen/core_boss_screen.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

OUT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/misc")


def vignette(size=256):
    c = Canvas(size, size)
    h = size / 2.0
    for y in range(size):
        for x in range(size):
            # distancia "quadrada arredondada": as bordas da tela (16:9 esticado) escurecem por igual
            dx = abs(x + 0.5 - h) / h
            dy = abs(y + 0.5 - h) / h
            d = (dx ** 4 + dy ** 4) ** 0.25
            t = min(1.0, max(0.0, (d - 0.55) / 0.45))
            a = int(255 * (t * t * (3 - 2 * t)) ** 1.2)
            c.set(x, y, (255, 255, 255, a))
    return c


ICONS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/mob_effect")
INK = (24, 16, 28, 255)


def icon(draw):
    c = Canvas(18, 18)
    draw(c)
    c.outline(INK)
    return c


def scorched(c):
    # peitoral derretendo + chama
    c.rect(4, 5, 13, 12, (150, 150, 160, 255)); c.rect(5, 6, 12, 11, (190, 190, 200, 255))
    for x, h in ((5, 3), (8, 4), (11, 2)):
        c.rect(x, 13, x + 1, 12 + h, (255, 120, 30, 255))
    c.circle(9, 4, 2, (255, 210, 60, 255)); c.set(9, 1, (255, 90, 20, 255))


def drowning(c):
    for (x, y, r) in ((6, 11, 3), (12, 7, 2), (9, 4, 1), (12, 13, 1)):
        c.circle(x, y, r, (90, 160, 255, 255)); c.set(x - max(1, r // 2), y - max(1, r // 2), (220, 240, 255, 255))


def whispers(c):
    c.circle(9, 9, 6, (60, 20, 100, 255)); c.circle(9, 9, 4, (20, 6, 36, 255))
    c.rect(6, 8, 7, 9, (200, 120, 255, 255)); c.rect(11, 8, 12, 9, (200, 120, 255, 255))
    c.line(6, 13, 12, 13, (150, 80, 220, 255))


def time_slow(c):
    c.circle(9, 9, 7, (230, 200, 130, 255)); c.circle(9, 9, 5, (250, 240, 210, 255))
    c.line(9, 9, 9, 5, (60, 40, 20, 255)); c.line(9, 9, 12, 10, (60, 40, 20, 255))


def soul_drain(c):
    # coração rachado com alma subindo
    c.circle(6, 10, 3, (160, 30, 40, 255)); c.circle(11, 10, 3, (160, 30, 40, 255)); c.rect(5, 11, 12, 13, (160, 30, 40, 255))
    c.line(8, 9, 9, 14, (30, 10, 10, 255))
    c.circle(9, 4, 2, (140, 255, 235, 255))


def frog(c):
    c.circle(9, 11, 6, (90, 190, 60, 255)); c.circle(6, 6, 2, (90, 190, 60, 255)); c.circle(12, 6, 2, (90, 190, 60, 255))
    c.set(6, 6, (10, 10, 10, 255)); c.set(12, 6, (10, 10, 10, 255)); c.line(6, 13, 12, 13, (40, 90, 30, 255))


def inverted(c):
    c.line(4, 6, 13, 6, (200, 110, 255, 255)); c.line(11, 4, 13, 6, (200, 110, 255, 255)); c.line(11, 8, 13, 6, (200, 110, 255, 255))
    c.line(4, 12, 13, 12, (255, 200, 90, 255)); c.line(4, 12, 6, 10, (255, 200, 90, 255)); c.line(4, 12, 6, 14, (255, 200, 90, 255))


def chained(c):
    for i, (x, y) in enumerate(((3, 12), (7, 9), (11, 6))):
        col = (170, 170, 180, 255) if i % 2 == 0 else (120, 120, 130, 255)
        c.outline_rect(x, y - 2, x + 4, y + 1, col)
    c.set(15, 3, (200, 40, 40, 255)); c.set(14, 4, (200, 40, 40, 255))


def main():
    os.makedirs(OUT, exist_ok=True)
    vignette().save(os.path.join(OUT, "boss_vignette.png"))
    os.makedirs(ICONS, exist_ok=True)
    for name, fn in (("scorched", scorched), ("drowning", drowning), ("whispers", whispers), ("time_slow", time_slow),
                     ("soul_drain", soul_drain), ("frog", frog), ("inverted", inverted), ("chained", chained)):
        icon(fn).save(os.path.join(ICONS, "boss_" + name + ".png"))
    print("ok: boss_vignette.png + 8 icones")


if __name__ == "__main__":
    main()
