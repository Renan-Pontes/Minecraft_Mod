# -*- coding: utf-8 -*-
"""Texturas das Transformações raciais (v1.3) — camadas desenhadas sobre o modelo do jogador (64×64, layout de skin).

- golem_skin.png   : pele de rocha do Colosso de Pedra (placas de pedra com juntas escuras)
- molten_cracks.png: rachaduras de magma (só as linhas, resto transparente; desenhada emissiva por cima da rocha)
- fur_shell.png    : pelagem da Fera Total (fios semitransparentes, branca para ser tingida)

Uso: py -I tools/gen/character_forms.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "entity", "character")
R = random.Random(1307)


def golem():
    im = Image.new("RGBA", (64, 64))
    px = im.load()
    for y in range(64):
        for x in range(64):
            # placas de 4×4 desencontradas
            bx = (x + (2 if (y // 4) % 2 else 0)) % 4
            by = y % 4
            joint = bx == 0 or by == 0
            n = R.randint(-14, 14)
            if joint:
                c = (52 + n // 2, 46 + n // 2, 42 + n // 2)
            else:
                base = 118 + ((x * 7 + y * 13) % 9) - 4
                c = (base + n, base - 6 + n, base - 14 + n)
            px[x, y] = (max(0, min(255, c[0])), max(0, min(255, c[1])), max(0, min(255, c[2])), 255)
    im.save(os.path.join(OUT, "golem_skin.png"))


def cracks():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for _ in range(26):
        x, y = R.randrange(64), R.randrange(64)
        dx, dy = R.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
        for _ in range(R.randint(4, 11)):
            if 0 <= x < 64 and 0 <= y < 64:
                hot = R.random() < 0.35
                px[x, y] = (255, 236, 150, 255) if hot else (255, 120 + R.randint(0, 50), 24, 255)
            if R.random() < 0.35:
                dx, dy = R.choice([(1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)])
            x += dx
            y += dy
    im.save(os.path.join(OUT, "molten_cracks.png"))


def fur():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for y in range(64):
        for x in range(64):
            v = R.random()
            if v < 0.55:
                shade = 200 + R.randint(0, 55)
                a = 150 + R.randint(0, 90) if (x + y * 3) % 5 else 110
                px[x, y] = (shade, shade, shade, a)
    im.save(os.path.join(OUT, "fur_shell.png"))


if __name__ == "__main__":
    golem()
    cracks()
    fur()
    print("ok")
