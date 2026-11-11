"""
Gera os assets do modulo fusions_b (rode da raiz do projeto:  py -I tools/gen/fusions_b.py).

  - textures/entity/hellfire_imp.png (+ _glow)      Diabrete Infernal (UV do HellfireImpModel)
  - textures/entity/abyss_tentacle.png (+ _glow)    Tentaculo do Abismo (UV do AbyssTentacleModel)
  - textures/mob_effect/<efeito>.png                icones 18x18 dos efeitos de status
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, darken, lighten, mix  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
ENTITY = os.path.join(ASSETS, "textures", "entity")
EFFECT = os.path.join(ASSETS, "textures", "mob_effect")

CLEAR = (0, 0, 0, 0)


def rgb(r, g, b, a=255):
    return (r, g, b, a)


# =====================================================================================
#  Pintura de caixas no layout UV padrao do Minecraft
# =====================================================================================

def faces(u, v, w, h, d):
    """Retangulos (x0, y0, largura, altura) de cada face de uma caixa com texOffs(u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(c, rect, color, shade_rows=True, seed=0):
    """Preenche um retangulo com a cor, escurecendo de cima para baixo e com leve ruido."""
    x0, y0, w, h = rect
    rnd = random.Random(seed + x0 * 31 + y0 * 17)
    for y in range(h):
        for x in range(w):
            col = color
            if shade_rows and h > 1:
                t = y / (h - 1)
                col = lighten(col, 0.12 * (1 - t)) if t < 0.5 else darken(col, 0.18 * (t - 0.5) * 2)
            f = (rnd.random() - 0.5) * 0.12
            col = lighten(col, f) if f > 0 else darken(col, -f)
            c.set(x0 + x, y0 + y, col)


def paint_box(c, u, v, w, h, d, base, top=None, bottom=None, seed=0):
    f = faces(u, v, w, h, d)
    fill(c, f["top"], top or lighten(base, 0.15), False, seed)
    fill(c, f["bottom"], bottom or darken(base, 0.35), False, seed + 1)
    fill(c, f["right"], darken(base, 0.12), True, seed + 2)
    fill(c, f["front"], base, True, seed + 3)
    fill(c, f["left"], darken(base, 0.12), True, seed + 4)
    fill(c, f["back"], darken(base, 0.22), True, seed + 5)
    return f


# =====================================================================================
#  Diabrete Infernal (64x64)
# =====================================================================================

def hellfire_imp():
    c = Canvas(64, 64)
    glow = Canvas(64, 64)
    skin = rgb(176, 32, 42)
    skin_dark = rgb(104, 14, 24)
    belly = rgb(226, 110, 64)
    horn = rgb(42, 26, 28)
    bone = rgb(150, 128, 104)
    eye = rgb(255, 222, 70)
    ember = rgb(255, 150, 40)

    # Cabeca 6x6x6 em (0,0)
    f = paint_box(c, 0, 0, 6, 6, 6, skin, top=darken(skin, 0.1), seed=1)
    fx, fy, _, _ = f["front"]
    face = [
        "ssssss",
        "sBssBs",
        "sEssEs",
        "ssccss",
        "sMFFMs",
        "ssMMss",
    ]
    pal = {"s": None, "B": darken(skin_dark, 0.4), "E": eye, "c": darken(skin, 0.15), "M": rgb(40, 6, 10), "F": rgb(240, 232, 214)}
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            if pal.get(ch):
                c.set(fx + x, fy + y, pal[ch])
    for ex in (1, 4):
        glow.set(fx + ex, fy + 2, eye)
        glow.set(fx + ex, fy + 1, rgb(140, 60, 10))
    # Orelhas pontudas nas laterais
    for side in ("right", "left"):
        sx, sy, sw, sh = f[side]
        c.set(sx + sw // 2, sy + 1, darken(skin, 0.3))
        c.set(sx + sw // 2 - 1, sy + 2, darken(skin, 0.25))

    # Corpo 5x6x3 em (0,12): barriga clara e brasa no peito
    f = paint_box(c, 0, 12, 5, 6, 3, skin, seed=2)
    bx, by, bw, bh = f["front"]
    for y in range(1, bh):
        for x in range(1, bw - 1):
            c.set(bx + x, by + y, mix(belly, skin, 0.15 * y))
    c.set(bx + 2, by + 1, ember)
    glow.set(bx + 2, by + 1, ember)
    c.set(bx + 1, by + 3, darken(belly, 0.25))
    c.set(bx + 3, by + 3, darken(belly, 0.25))

    # Bracos 2x5x2 em (24,0) e (32,0): garras escuras na ponta
    for u in (24, 32):
        f = paint_box(c, u, 0, 2, 5, 2, skin, seed=u)
        for side in ("right", "front", "left", "back"):
            x0, y0, w, h = f[side]
            for x in range(w):
                c.set(x0 + x, y0 + h - 1, rgb(30, 8, 10))
                c.set(x0 + x, y0 + h - 2, darken(skin, 0.3))
        x0, y0, w, h = f["bottom"]
        for x in range(w):
            for y in range(h):
                c.set(x0 + x, y0 + y, rgb(30, 8, 10))
        c.set(x0, y0 + h - 1, rgb(220, 206, 184))

    # Pernas 2x4x2 em (24,8) e (32,8): cascos
    for u in (24, 32):
        f = paint_box(c, u, 8, 2, 4, 2, darken(skin, 0.1), seed=u + 7)
        for side in ("right", "front", "left", "back"):
            x0, y0, w, h = f[side]
            for x in range(w):
                c.set(x0 + x, y0 + h - 1, rgb(26, 16, 16))

    # Chifres 1x3x1 em (40,0) e (44,0): ponta de osso, base escura
    for u in (40, 44):
        f = faces(u, 0, 1, 3, 1)
        for name, (x0, y0, w, h) in f.items():
            for y in range(h):
                for x in range(w):
                    col = bone if (name in ("top",) or y == 0) else mix(horn, bone, 0.25 * (2 - y)) if y < 2 else horn
                    c.set(x0 + x, y0 + y, col)

    # Cauda 1x1x5 em (40,6) e ponta 2x2x2 em (52,6)
    paint_box(c, 40, 6, 1, 1, 5, skin, seed=40)
    f = paint_box(c, 52, 6, 2, 2, 2, rgb(36, 10, 14), seed=52)
    x0, y0, _, _ = f["front"]
    glow.set(x0, y0, rgb(200, 60, 20))

    # Asas 0x7x8 em (0,24): membrana com ossos e borda recortada
    for x0 in (0, 8):
        y0 = 24 + 8
        for x in range(8):
            for y in range(7):
                scallop = y == 6 and x % 3 != 1 or y == 5 and x % 3 == 0 and x > 0
                if scallop:
                    continue
                col = mix(rgb(96, 12, 22), rgb(50, 6, 14), y / 7)
                if y == 0 or (x in (2, 5) and y < 5):
                    col = rgb(46, 14, 16)
                c.set(x0 + x, y0 + y, col)
    c.save(os.path.join(ENTITY, "hellfire_imp.png"))
    glow.save(os.path.join(ENTITY, "hellfire_imp_glow.png"))


# =====================================================================================
#  Tentaculo do Abismo (64x32)
# =====================================================================================

def abyss_tentacle():
    c = Canvas(64, 32)
    glow = Canvas(64, 32)
    base = rgb(44, 34, 86)
    sucker = rgb(176, 146, 196)
    sucker_in = rgb(96, 62, 120)
    biolum = rgb(90, 230, 255)
    boxes = [(0, 0, 4, 6, 4), (16, 0, 4, 6, 4), (32, 0, 3, 6, 3), (44, 0, 3, 5, 3), (0, 12, 2, 5, 2), (8, 12, 2, 4, 2)]
    rnd = random.Random(7)
    for i, (u, v, w, h, d) in enumerate(boxes):
        col = mix(base, rgb(70, 40, 110), i / 6)
        f = paint_box(c, u, v, w, h, d, col, seed=i * 11)
        # Ventosas na frente (o lado virado para a vitima)
        fx, fy, fw, fh = f["front"]
        for y in range(0, fh, 2):
            for x in range(fw):
                if (x + y // 2) % 2 == 0:
                    c.set(fx + x, fy + y, sucker if fw > 2 else lighten(sucker, 0.1))
                    if fw > 2 and y + 1 < fh:
                        c.set(fx + x, fy + y + 1, sucker_in)
        # Pontos bioluminescentes nas laterais e costas
        for side in ("right", "left", "back"):
            sx, sy, sw, sh = f[side]
            for _ in range(max(1, sw * sh // 9)):
                px, py = sx + rnd.randrange(sw), sy + rnd.randrange(sh)
                c.set(px, py, lighten(biolum, 0.1))
                glow.set(px, py, biolum)
    c.save(os.path.join(ENTITY, "abyss_tentacle.png"))
    glow.save(os.path.join(ENTITY, "abyss_tentacle_glow.png"))


# =====================================================================================
#  Icones de efeito (18x18)
# =====================================================================================

def icon_crystallized():
    c = Canvas(18, 18)
    light, mid, dark = rgb(255, 210, 255), rgb(224, 122, 240), rgb(130, 50, 160)
    # Tres cristais
    for (cx, top, w) in ((9, 1, 3), (5, 6, 2), (13, 5, 2)):
        for y in range(top, 16):
            half = w if y > top + w else max(0, y - top)
            for x in range(cx - half, cx + half + 1):
                col = light if x < cx else (mid if x == cx else dark)
                c.set(x, y, col)
    c.rect(3, 15, 15, 16, darken(mid, 0.3))
    c.outline(rgb(60, 20, 80))
    return c


def icon_crystal_armor():
    c = Canvas(18, 18)
    mid, light, dark = rgb(224, 122, 240), rgb(255, 214, 255), rgb(120, 46, 150)
    for y in range(2, 16):
        half = 6 if y < 10 else 6 - (y - 9)
        for x in range(9 - half, 9 + half):
            c.set(x, y, light if x < 9 else mid)
    c.line(9, 2, 9, 15, dark)
    c.line(4, 6, 9, 10, lighten(light, 0.3))
    c.line(13, 6, 9, 10, dark)
    c.outline(rgb(60, 20, 80))
    return c


def icon_prism():
    c = Canvas(18, 18)
    for i, col in enumerate((rgb(255, 64, 64), rgb(255, 154, 48), rgb(255, 232, 64), rgb(80, 224, 96), rgb(64, 160, 255), rgb(154, 92, 255))):
        c.line(10, 9, 17, 4 + i * 2, col)
    c.line(0, 9, 7, 9, rgb(255, 255, 255))
    for y in range(3, 15):
        half = (y - 3) // 2
        for x in range(8 - half, 8 + half + 1):
            c.set(x, y, rgb(230, 230, 255) if x < 8 else rgb(170, 170, 220))
    c.outline(rgb(50, 40, 90))
    return c


def icon_zero_gravity():
    c = Canvas(18, 18)
    blue, light = rgb(157, 180, 255), rgb(230, 238, 255)
    for a in range(0, 360, 12):
        x = 9 + math.cos(math.radians(a)) * 7
        y = 12 + math.sin(math.radians(a)) * 2.5
        c.set(int(x), int(y), blue)
    for k, y0 in enumerate((3, 7)):
        for i in range(4):
            c.set(9 - i, y0 + i, light if k == 0 else blue)
            c.set(9 + i, y0 + i, light if k == 0 else blue)
    c.circle(9, 12, 1.6, light)
    c.outline(rgb(40, 50, 100))
    return c


def icon_astral_projection():
    c = Canvas(18, 18)
    ghost, light = rgb(170, 190, 255, 220), rgb(240, 245, 255)
    c.circle(9, 5, 3, ghost)
    for y in range(8, 16):
        half = 4 if y < 14 else 3
        for x in range(9 - half, 9 + half + 1):
            if y >= 14 and (x - 9) % 2 == 0:
                continue
            c.set(x, y, ghost)
    c.set(8, 5, rgb(40, 50, 110))
    c.set(10, 5, rgb(40, 50, 110))
    for (x, y) in ((2, 2), (15, 4), (3, 12), (15, 13)):
        c.set(x, y, light)
        c.set(x + 1, y, rgb(180, 200, 255))
        c.set(x, y + 1, rgb(180, 200, 255))
    c.outline(rgb(40, 50, 110))
    return c


def icon_flame(core, mid, edge):
    c = Canvas(18, 18)
    rows = [
        "........x.........",
        ".......xx.........",
        ".......xxx........",
        "......xxxx...x....",
        "......xxxxx..xx...",
        ".....xxxxxx.xxx...",
        "....xxxxmxxxxxx...",
        "....xxxmmmxxxxx...",
        "...xxxxmmmmxxxx...",
        "...xxxmmmmmmxxx...",
        "...xxmmmcmmmxxx...",
        "...xxmmmccmmmxx...",
        "...xxmmcccmmmxx...",
        "....xmmcccmmmx....",
        "....xxmmccmmxx....",
        ".....xxmmmmxx.....",
        "......xxxxxx......",
        "..................",
    ]
    c.from_rows(rows, {"x": edge, "m": mid, "c": core})
    return c


def icon_black_flame():
    c = icon_flame(rgb(120, 20, 30), rgb(30, 6, 10), rgb(10, 2, 4))
    c.outline(rgb(192, 24, 46))
    return c


def icon_drop(color, highlight, outline):
    c = Canvas(18, 18)
    for y in range(2, 16):
        if y < 9:
            half = (y - 2) * 0.7
        else:
            half = math.sqrt(max(0, 25 - (y - 11) ** 2))
        for x in range(18):
            if abs(x + 0.5 - 9) <= half:
                c.set(x, y, color)
    c.shade_sphere(9, 11, 5)
    c.set(7, 9, highlight)
    c.set(7, 10, highlight)
    c.set(8, 8, highlight)
    c.outline(outline)
    return c


def icon_blood_pact():
    return icon_drop(rgb(150, 0, 20), rgb(255, 140, 140), rgb(50, 0, 6))


def icon_venom():
    c = icon_drop(rgb(31, 138, 106), rgb(170, 255, 220), rgb(10, 30, 50))
    c.set(9, 12, rgb(10, 26, 58))
    c.set(10, 13, rgb(10, 26, 58))
    return c


def icon_drown():
    c = Canvas(18, 18)
    for (cx, cy, r) in ((7, 11, 4), (13, 5, 2.6), (12, 14, 1.8), (4, 4, 1.5)):
        c.circle(cx, cy, r, rgb(63, 120, 220))
        c.circle(cx, cy, max(0.6, r - 1.2), rgb(26, 50, 120))
        c.set(int(cx - r * 0.5), int(cy - r * 0.5), rgb(220, 240, 255))
    c.outline(rgb(10, 20, 50))
    return c


def icon_inked():
    c = Canvas(18, 18)
    ink = rgb(16, 16, 24)
    c.circle(9, 9, 5, ink)
    rnd = random.Random(3)
    for a in range(0, 360, 45):
        r = 6 + rnd.random() * 2.5
        x, y = 9 + math.cos(math.radians(a)) * r, 9 + math.sin(math.radians(a)) * r
        c.circle(x, y, 1.4, ink)
        c.line(9, 9, int(x), int(y), ink)
    c.set(7, 7, rgb(90, 90, 120))
    c.outline(rgb(70, 70, 110))
    return c


def icon_inverted_gravity():
    c = Canvas(18, 18)
    purple, light = rgb(107, 91, 168), rgb(190, 170, 255)
    for i in range(6):
        c.set(9 - i, 2 + i, light)
        c.set(9 + i, 2 + i, light)
        c.set(9 - i, 3 + i, purple)
        c.set(9 + i, 3 + i, purple)
    c.rect(8, 4, 10, 13, purple)
    c.rect(8, 4, 8, 13, light)
    c.rect(3, 15, 15, 16, rgb(60, 44, 30))
    c.outline(rgb(30, 20, 60))
    return c


ICONS = {
    "crystallized": icon_crystallized,
    "crystal_armor": icon_crystal_armor,
    "prism_reflection": icon_prism,
    "zero_gravity": icon_zero_gravity,
    "astral_projection": icon_astral_projection,
    "black_flame": icon_black_flame,
    "blood_pact": icon_blood_pact,
    "abyssal_venom": icon_venom,
    "abyssal_drown": icon_drown,
    "inked": icon_inked,
    "inverted_gravity": icon_inverted_gravity,
}


def main():
    hellfire_imp()
    abyss_tentacle()
    for name, fn in ICONS.items():
        fn().save(os.path.join(EFFECT, name + ".png"))
    print("fusions_b: texturas geradas")


if __name__ == "__main__":
    main()
