"""Gera as texturas da classe Hollow (v1.3).

- textures/entity/hollowfied_body.png (64x64): mascara por rank, buraco no peito, ossos e as partes da Resurreccion,
  no layout de UV de HollowBodyLayer (texOffs + caixas).
- textures/gui/class/hollowfied.png (32x32): icone da classe (mascara de caveira com listras rubras e o buraco).

Uso: py -I tools/gen/class_hollow.py
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "isekai_mahou", "textures")

BONE = (242, 238, 230, 255)
BONE_MID = (214, 206, 192, 255)
BONE_DARK = (168, 156, 140, 255)
RED = (208, 20, 44, 255)
RED_DARK = (120, 8, 20, 255)
BLACK = (14, 4, 8, 255)
VOID = (4, 0, 2, 255)

rnd = random.Random(7)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def fill(img, x, y, w, h, c, noise=0.06):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < img.width and 0 <= j < img.height:
                img.putpixel((i, j), shade(c, 1 + rnd.uniform(-noise, noise)))


def box(img, u, v, w, h, d, c, side=None):
    """Pinta as 6 faces do UV de caixa do Minecraft (texOffs u,v; tamanho w,h,d)."""
    side = side or shade(c, 0.85)
    fill(img, u + d, v, w, d, shade(c, 1.05))          # topo
    fill(img, u + d + w, v, w, d, shade(c, 0.7))       # baixo
    fill(img, u, v + d, d, h, side)                    # direita
    fill(img, u + d, v + d, w, h, c)                   # frente
    fill(img, u + d + w, v + d, d, h, side)            # esquerda
    fill(img, u + d + w + d, v + d, w, h, shade(c, 0.8))  # trás


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def body():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # ---- máscara 9x9x1 em (0,0): frente em (1,1)
    box(img, 0, 0, 9, 9, 1, BONE)
    fx, fy = 1, 1
    for (ex, ey) in ((1, 3), (5, 3)):
        fill(img, fx + ex, fy + ey, 3, 2, BLACK, 0)          # olhos
    for x in (fx + 2, fx + 6):                              # listras rubras descendo dos olhos
        for y in range(fy + 5, fy + 9):
            px(img, x, y, RED if y % 2 else RED_DARK)
    for x in range(fx + 1, fx + 8):                          # rachadura na testa
        if x % 3 == 0:
            px(img, x, fy, BONE_DARK)
    px(img, fx + 4, fy + 1, RED)
    px(img, fx + 4, fy + 2, RED_DARK)
    # ---- mandíbula 7x3x1 em (0,10): dentes
    box(img, 0, 10, 7, 3, 1, BONE_MID)
    for i in range(7):
        fill(img, 1 + i, 11, 1, 3, BONE if i % 2 == 0 else BLACK, 0)
    # ---- bico do Gillian 2x2x3 em (20,0)
    box(img, 20, 0, 2, 2, 3, BONE_MID)
    # ---- focinho do Adjuchas 4x3x3 em (20,6)
    box(img, 20, 6, 4, 3, 3, BONE_MID)
    fill(img, 23, 10, 4, 1, BLACK, 0)
    # ---- chifres curtos 1x1x3 em (36,0)
    box(img, 36, 0, 1, 1, 3, BONE_DARK)
    # ---- chifres longos do Vasto Lorde: 2x2x5 (44,0), 1x1x4 (44,7), 1x1x3 (54,7)
    box(img, 44, 0, 2, 2, 5, BONE)
    box(img, 44, 7, 1, 1, 4, BONE_MID)
    box(img, 54, 7, 1, 1, 3, RED_DARK)
    # ---- fragmento do Arrancar: 1x3x5 (0,16) + dentes 3x2x1 (12,16)
    box(img, 0, 16, 1, 3, 5, BONE)
    box(img, 12, 16, 3, 2, 1, BONE)
    for i in range(3):
        px(img, 13 + i, 18, BLACK if i % 2 else BONE)
    # ---- buraco no peito (frente 24,16 e costas 32,16; caixa 4x4x0 → face em +0)
    for (u, v) in ((24, 16), (32, 16)):
        fill(img, u, v, 8, 4, (0, 0, 0, 0), 0)
        for i in range(4):
            for j in range(4):
                edge = i in (0, 3) or j in (0, 3)
                corner = i in (0, 3) and j in (0, 3)
                if corner:
                    continue
                px(img, u + i, v + j, RED_DARK if edge else VOID)
                px(img, u + 4 + i, v + j, RED_DARK if edge else VOID)
    # ---- ombreiras 5x3x6 em (0,24)
    box(img, 0, 24, 5, 3, 6, BONE_MID)
    for i in range(0, 22, 3):
        fill(img, i, 30, 1, 3, BONE_DARK, 0)
    # ---- costelas 8x6x1 em (0,34)
    box(img, 0, 34, 8, 6, 1, (0, 0, 0, 0))
    for j in range(0, 6, 2):
        fill(img, 1, 35 + j, 8, 1, BONE, 0.04)
    fill(img, 4, 35, 2, 6, BONE_MID, 0)
    # ---- crista da Pantera 2x2x6 em (24,24) e cauda 1x1x6 em (40,24)
    box(img, 24, 24, 2, 2, 6, BONE)
    for i in range(24, 40, 4):
        px(img, i, 30, (63, 169, 245, 255))
    box(img, 40, 24, 1, 1, 6, BONE_MID)
    # ---- chifres do Murciélago 1x4x1 em (24,34)
    box(img, 24, 34, 1, 4, 1, BONE)
    # ---- asas do Murciélago 12x10x0 em (0,42): membrana negra com veios verdes
    fill(img, 0, 42, 24, 10, (0, 0, 0, 0), 0)
    for i in range(12):
        for j in range(10):
            # borda inferior recortada (as pontas da asa)
            cut = j > 7 and (i % 4) in (1, 2)
            if cut:
                continue
            c = (20, 16, 26, 240)
            if j == 0 or i in (0, 4, 8):
                c = (46, 224, 122, 255) if j % 3 else (26, 120, 70, 255)
            px(img, i, 42 + j, shade(c, 1 + rnd.uniform(-0.05, 0.05)))
            px(img, 12 + i, 42 + j, shade(c, 0.9))
    # ---- capuz da Serpiente 10x8x1 em (26,42)
    box(img, 26, 42, 10, 8, 1, (90, 60, 130, 255))
    for i in range(27, 37):
        for j in range(43, 51):
            if (i + j) % 3 == 0:
                px(img, i, j, (176, 96, 255, 255))
    # ---- cauda da Serpiente: 3x3x6 (40,33), 2x2x6 (48,52), 1x1x5 (24,52)
    for (u, v, w, h, d) in ((40, 33, 3, 3, 6), (48, 52, 2, 2, 6), (24, 52, 1, 1, 5)):
        box(img, u, v, w, h, d, (110, 74, 160, 255))
    out = os.path.join(ROOT, "entity", "hollowfied_body.png")
    img.save(out)
    print("ok", out)


def icon():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # máscara (oval) com sombra
    for x in range(32):
        for y in range(32):
            dx, dy = (x - 15.5) / 11.0, (y - 14) / 13.0
            if dx * dx + dy * dy <= 1.0:
                f = 1.0 - 0.25 * max(0.0, dx) - 0.15 * max(0.0, dy)
                img.putpixel((x, y), shade(BONE, f))
    # olhos
    for (cx, cy) in ((10, 12), (21, 12)):
        for x in range(cx - 3, cx + 3):
            for y in range(cy - 1, cy + 2):
                px(img, x, y, BLACK)
    # listras
    for x in (10, 21):
        for y in range(14, 24):
            px(img, x, y, RED)
            px(img, x + 1, y, RED_DARK)
    # dentes
    for i in range(9):
        x = 11 + i
        for y in range(22, 25):
            px(img, x, y, BLACK if i % 2 else BONE)
    # contorno escuro
    src = img.copy()
    for x in range(32):
        for y in range(32):
            if src.getpixel((x, y))[3] == 0:
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + ox < 32 and 0 <= y + oy < 32 and src.getpixel((x + ox, y + oy))[3] > 0:
                        img.putpixel((x, y), (40, 6, 12, 255))
                        break
    # buraco (canto inferior direito)
    for x in range(22, 30):
        for y in range(24, 31):
            d = ((x - 26) ** 2 + (y - 27.5) ** 2) ** 0.5
            if d < 3.6:
                px(img, x, y, VOID if d < 2.4 else RED_DARK)
    out = os.path.join(ROOT, "gui", "class", "hollowfied.png")
    img.save(out)
    print("ok", out)


if __name__ == "__main__":
    body()
    icon()
