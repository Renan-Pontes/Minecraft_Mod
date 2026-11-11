"""
Gera os assets do modulo earth_wind:
  - blocos temporarios: raised_earth_stone (pedra com runas) e earthen_quicksand (areia animada) + modelos/blockstates
  - icones dos efeitos de status (18x18)
  - entidades GeckoLib: summoned_stone_golem (geo, animacoes, textura 128x128 + glowmask) e summoned_sylph (64x64)
  - modelos dos ovos das invocacoes
Uso (na raiz do projeto):  py -I tools/gen/earth_wind.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, darken, lighten, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")


def write_json(rel, data):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def rgba(r, g, b, a=255):
    return (r, g, b, a)


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(v)))


def value_noise(seed, cell):
    """Ruido por celulas (cada celula cell x cell tem um valor aleatorio): da o aspecto de pedras/blocos."""
    rnd = random.Random(seed)
    cache = {}

    def f(x, y):
        k = (x // cell, y // cell)
        if k not in cache:
            cache[k] = rnd.random()
        return cache[k]
    return f


# =====================================================================================
#  Blocos
# =====================================================================================
STONE_L = rgba(146, 130, 112)
STONE_M = rgba(116, 102, 88)
STONE_D = rgba(84, 72, 62)
MORTAR = rgba(54, 46, 40)
RUNE = rgba(255, 168, 58)
RUNE_L = rgba(255, 220, 130)


def raised_earth_stone():
    c = Canvas(16, 16)
    n = value_noise(7, 1)
    for y in range(16):
        for x in range(16):
            v = n(x, y)
            c.set(x, y, mix(STONE_M, STONE_L if v > 0.5 else STONE_D, abs(v - 0.5) * 0.9))
    # juntas de pedras irregulares
    for x in range(16):
        c.set(x, 0, MORTAR)
        c.set(x, 7, MORTAR)
    for y in range(0, 8):
        c.set(5, y, MORTAR)
        c.set(12, y, MORTAR)
    for y in range(8, 16):
        c.set(1, y, MORTAR)
        c.set(9, y, MORTAR)
    for x in range(16):
        c.set(x, 15, darken(c.get(x, 15), 0.25))
    # luz no topo de cada pedra
    for x in range(16):
        if c.get(x, 1) != MORTAR:
            c.set(x, 1, lighten(c.get(x, 1), 0.2))
        if c.get(x, 8) != MORTAR:
            c.set(x, 8, lighten(c.get(x, 8), 0.2))
    # runa da terra no centro (losango com cruz)
    for (x, y) in [(8, 4), (7, 5), (9, 5), (6, 6), (10, 6), (7, 7), (9, 7), (8, 8)]:
        c.set(x, y, RUNE)
    for (x, y) in [(8, 5), (8, 6), (8, 7), (7, 6), (9, 6)]:
        c.set(x, y, RUNE_L)
    for (x, y) in [(3, 11), (4, 12), (5, 11), (13, 10), (14, 11), (13, 12), (11, 3), (2, 3)]:
        c.set(x, y, RUNE)
    c.save(os.path.join(A, "textures/block/raised_earth_stone.png"))
    write_json("models/block/raised_earth_stone.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "isekai_mahou:block/raised_earth_stone"}})
    write_json("blockstates/raised_earth_stone.json", {"variants": {"": [
        {"model": "isekai_mahou:block/raised_earth_stone"},
        {"model": "isekai_mahou:block/raised_earth_stone", "y": 90},
        {"model": "isekai_mahou:block/raised_earth_stone", "y": 180},
        {"model": "isekai_mahou:block/raised_earth_stone", "y": 270}]}})


def earthen_quicksand():
    frames = 4
    c = Canvas(16, 16 * frames)
    sand = rgba(214, 190, 134)
    sand_d = rgba(176, 148, 96)
    sand_l = rgba(236, 218, 170)
    for f in range(frames):
        rot = f * math.pi / 2 / frames
        rnd = random.Random(20 + f)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                # espiral girando para o centro
                s = math.sin(a * 2 + r * 0.9 - rot * 4)
                col = mix(sand, sand_d if s < 0 else sand_l, min(1.0, abs(s) * 0.55 + (0.25 if r < 2.5 else 0)))
                if r < 1.6:
                    col = darken(sand_d, 0.25)
                f2 = (rnd.random() - 0.5) * 0.08
                col = lighten(col, f2) if f2 > 0 else darken(col, -f2)
                c.set(x, y + 16 * f, col)
    c.save(os.path.join(A, "textures/block/earthen_quicksand.png"))
    with open(os.path.join(A, "textures/block/earthen_quicksand.png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": 5, "interpolate": True}}, fh, indent=2)
    write_json("models/block/earthen_quicksand.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "isekai_mahou:block/earthen_quicksand"}})
    write_json("blockstates/earthen_quicksand.json", {"variants": {"": {"model": "isekai_mahou:block/earthen_quicksand"}}})


# =====================================================================================
#  Icones de efeito (18x18)
# =====================================================================================
def icon_stoneskin():
    c = Canvas(18, 18)
    # escudo de pedra
    for y in range(2, 16):
        half = 7 if y < 9 else max(1, 7 - (y - 8))
        for x in range(9 - half, 9 + half):
            c.set(x, y, STONE_M)
    n = value_noise(3, 2)
    for y in range(18):
        for x in range(18):
            p = c.get(x, y)
            if p[3]:
                c.set(x, y, mix(STONE_D, STONE_L, n(x, y) * 0.9))
    for x in range(3, 15):
        c.set(x, 2, lighten(STONE_L, 0.2))
    c.line(9, 4, 9, 13, RUNE)
    c.line(6, 7, 12, 7, RUNE)
    c.set(9, 7, RUNE_L)
    c.outline(rgba(40, 34, 30))
    c.save(os.path.join(A, "textures/mob_effect/stoneskin.png"))


def icon_weighted():
    c = Canvas(18, 18)
    iron = rgba(88, 84, 92)
    # alca
    c.circle(9, 6, 4.2, iron)
    c.circle(9, 6, 2.4, (0, 0, 0, 0))
    for y in range(6, 18):
        for x in range(18):
            if c.get(x, y)[3] and y >= 6:
                c.set(x, y, (0, 0, 0, 0))
    # bola do peso
    c.circle(9, 11.5, 5.6, iron)
    c.shade_sphere(9, 11.5, 5.6, light=0.5, dark=0.5)
    c.rect(6, 4, 7, 7, iron)
    c.rect(11, 4, 12, 7, iron)
    c.line(7, 2, 11, 2, iron)
    c.set(6, 3, iron)
    c.set(12, 3, iron)
    c.outline(rgba(26, 24, 28))
    c.save(os.path.join(A, "textures/mob_effect/weighted.png"))


WIND = rgba(159, 240, 208)
WIND_L = rgba(220, 255, 244)
WIND_D = rgba(72, 170, 140)


def chevron(c, cx, cy, col):
    for i in range(5):
        c.set(cx - i, cy + i, col)
        c.set(cx + i, cy + i, col)
        c.set(cx - i, cy + i + 1, darken(col, 0.2))
        c.set(cx + i, cy + i + 1, darken(col, 0.2))


def icon_double_jump():
    c = Canvas(18, 18)
    chevron(c, 9, 2, WIND_L)
    chevron(c, 9, 8, WIND)
    for x in (4, 7, 11, 14):
        c.set(x, 16, WIND_D)
    c.outline(rgba(30, 70, 60))
    c.save(os.path.join(A, "textures/mob_effect/double_jump.png"))


def icon_glide():
    c = Canvas(18, 18)
    # asa-delta: dossel curvo de vento com cordas ate o "piloto"
    for x in range(2, 17):
        t = (x - 9) / 7.0
        y = int(round(3 + 4 * t * t))
        c.set(x, y, WIND_L)
        c.set(x, y + 1, WIND)
        c.set(x, y + 2, WIND_D)
    c.line(3, 9, 8, 14, rgba(200, 230, 220))
    c.line(15, 9, 10, 14, rgba(200, 230, 220))
    c.rect(8, 14, 10, 16, rgba(120, 104, 90))
    c.set(9, 13, rgba(230, 200, 170))
    c.outline(rgba(30, 70, 60))
    c.save(os.path.join(A, "textures/mob_effect/wind_glide.png"))


def icon_swiftness():
    c = Canvas(18, 18)
    for (y, x0, x1, col) in [(4, 3, 13, WIND_L), (8, 1, 15, WIND), (12, 4, 14, WIND_L), (15, 7, 12, WIND_D)]:
        c.line(x0, y, x1, y, col)
        c.set(x1 + 1, y - 1, col)
        c.set(x1 + 2, y - 2, darken(col, 0.15))
    c.outline(rgba(30, 70, 60))
    c.save(os.path.join(A, "textures/mob_effect/wind_swiftness.png"))


def icon_barrier():
    c = Canvas(18, 18)
    for i in range(60):
        a = i / 60 * math.pi * 1.8
        r = 7.2 - i / 60 * 1.5
        c.set(int(round(9 + math.cos(a) * r)), int(round(9 + math.sin(a) * r)), WIND if i % 7 else WIND_L)
    for i in range(30):
        a = i / 30 * math.pi * 1.6 + 2.5
        r = 4.0
        c.set(int(round(9 + math.cos(a) * r)), int(round(9 + math.sin(a) * r)), WIND_L)
    # flecha desviada
    c.line(2, 2, 7, 7, rgba(150, 120, 90))
    c.set(2, 3, rgba(220, 220, 220))
    c.set(3, 2, rgba(220, 220, 220))
    c.outline(rgba(30, 70, 60))
    c.save(os.path.join(A, "textures/mob_effect/wind_barrier.png"))


def icon_silent_steps():
    c = Canvas(18, 18)
    foot = rgba(120, 150, 138)
    foot_d = rgba(96, 124, 112)

    sole = ["x.x.x.",
            "......",
            ".xxxx.",
            "xxxxxx",
            "xxxxxx",
            ".xxxx.",
            "..xxx.",
            "..xxx.",
            ".xxxx.",
            ".xxxx.",
            "..xx.."]

    def footprint(ox, oy, col):
        c.from_rows(sole, {"x": col}, ox, oy)

    footprint(2, 5, foot)
    footprint(10, 1, foot_d)
    # tracos de vento "abafando" o som
    c.line(1, 16, 9, 16, WIND)
    c.line(10, 13, 16, 13, WIND_L)
    c.outline(rgba(28, 40, 36))
    c.save(os.path.join(A, "textures/mob_effect/silent_steps.png"))


# =====================================================================================
#  Modelos GeckoLib (box UV)
# =====================================================================================
class Atlas:
    """Empacota regioes de UV (box UV) numa textura sem sobreposicao."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.x = 0
        self.y = 0
        self.row_h = 0
        self.slots = {}

    def alloc(self, key, size):
        if key in self.slots:
            return self.slots[key]
        w, h, d = size
        rw, rh = 2 * (w + d), d + h
        if self.x + rw > self.w:
            self.x = 0
            self.y += self.row_h
            self.row_h = 0
        assert self.y + rh <= self.h, "textura pequena demais para " + key
        uv = (self.x, self.y)
        self.slots[key] = uv
        self.x += rw
        self.row_h = max(self.row_h, rh)
        return uv


def faces(uv, size):
    """Retangulos (x, y, w, h) de cada face no box UV."""
    u, v = uv
    w, h, d = size
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(canvas, uv, size, painter, glow=None):
    for name, (x0, y0, fw, fh) in faces(uv, size).items():
        for yy in range(fh):
            for xx in range(fw):
                col = painter(name, xx, yy, fw, fh, x0 + xx, y0 + yy)
                if col is not None:
                    canvas.set(x0 + xx, y0 + yy, col)
                if glow is not None:
                    g = glow(name, xx, yy, fw, fh)
                    if g is not None:
                        glow_canvas, gc = g
                        glow_canvas.set(x0 + xx, y0 + yy, gc)


def cube(origin, size, uv, inflate=0):
    c = {"origin": list(origin), "size": list(size), "uv": list(uv)}
    if inflate:
        c["inflate"] = inflate
    return c


def bone(name, pivot, parent=None, cubes=None):
    b = {"name": name, "pivot": list(pivot)}
    if parent:
        b["parent"] = parent
    if cubes:
        b["cubes"] = cubes
    return b


def geo(model_id, tw, th, bones, vw, vh):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + model_id, "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": vw, "visible_bounds_height": vh, "visible_bounds_offset": [0, vh / 2, 0]},
        "bones": bones}]}


def kf(frames):
    """{tempo: [x,y,z]} -> formato de keyframes do GeckoLib."""
    return {("%g" % t if t != int(t) else "%.1f" % t): list(v) for t, v in frames}


# ---------------------------------------------------------------------------------------
#  Golem de Pedra
# ---------------------------------------------------------------------------------------
def golem():
    gid = "summoned_stone_golem"
    W, H = 128, 128
    tex = Canvas(W, H)
    glow = Canvas(W, H)
    at = Atlas(W, H)
    S = {
        "torso": (18, 13, 12), "waist": (10, 6, 8), "hump": (12, 6, 3), "head": (8, 9, 8), "brow": (10, 2, 3),
        "shoulder": (8, 8, 10), "arm": (6, 18, 8), "fist": (8, 9, 10), "leg": (7, 19, 8),
    }
    uv = {k: at.alloc(k, S[k]) for k in ["torso", "leg", "fist", "shoulder", "head", "arm", "waist", "hump", "brow"]}

    base_noise = value_noise(11, 3)
    fine = random.Random(5)
    moss = rgba(84, 122, 52)
    moss_l = rgba(116, 156, 66)

    def stone(name, xx, yy, fw, fh, ax, ay, moss_top=True, dark=0.0):
        v = base_noise(ax, ay)
        col = mix(STONE_D, STONE_L, 0.25 + v * 0.6)
        # cada bloco 3x3 do ruido vira uma "pedra": luz em cima/esquerda, sombra embaixo/direita
        lx, ly = ax % 3, ay % 3
        if ly == 0 or lx == 0:
            col = lighten(col, 0.12)
        if ly == 2 or lx == 2:
            col = darken(col, 0.18)
        if (lx == 2 and ly == 2) and fine.random() < 0.5:
            col = darken(col, 0.3)
        # rachaduras
        if fine.random() < 0.03:
            col = MORTAR
        if name == "top" and moss_top and fine.random() < 0.55:
            col = moss if fine.random() < 0.6 else moss_l
        elif name != "top" and name != "bottom" and yy == 0 and moss_top and fine.random() < 0.4:
            col = moss
        if name == "bottom":
            col = darken(col, 0.3)
        # sombreamento vertical (mais escuro embaixo)
        if name not in ("top", "bottom") and fh > 2:
            col = darken(col, 0.18 * yy / fh)
        if dark:
            col = darken(col, dark)
        f = (fine.random() - 0.5) * 0.08
        return lighten(col, f) if f > 0 else darken(col, -f)

    def plain(name, xx, yy, fw, fh, ax, ay):
        return stone(name, xx, yy, fw, fh, ax, ay)

    # torso com a runa da terra brilhando no peito
    def torso_glow(name, xx, yy, fw, fh):
        if name != "front":
            return None
        cx, cy = fw / 2 - 0.5, 5.5
        d = math.hypot(xx - cx, (yy - cy) * 1.1)
        diamond = abs(xx - cx) + abs(yy - cy)
        if 3.4 < d < 4.4 or (diamond < 2.6) or (abs(xx - cx) < 0.6 and 1 < yy < 11) or (abs(yy - cy) < 0.6 and 2 < xx < fw - 3):
            return (glow, RUNE_L if diamond < 1.6 else RUNE)
        return None

    def torso_paint(name, xx, yy, fw, fh, ax, ay):
        g = torso_glow(name, xx, yy, fw, fh)
        if g is not None:
            return g[1]
        if name == "front" and (xx in (0, fw - 1)):
            return darken(stone(name, xx, yy, fw, fh, ax, ay), 0.15)
        return stone(name, xx, yy, fw, fh, ax, ay)

    paint_box(tex, uv["torso"], S["torso"], torso_paint, torso_glow)
    paint_box(tex, uv["waist"], S["waist"], lambda *a: stone(*a, moss_top=False, dark=0.08))
    paint_box(tex, uv["hump"], S["hump"], plain)

    # cabeca com olhos ambar brilhando sob a sobrancelha
    def head_glow(name, xx, yy, fw, fh):
        if name == "front" and yy in (3, 4) and xx in (1, 2, 5, 6):
            return (glow, RUNE_L if yy == 3 else RUNE)
        return None

    def head_paint(name, xx, yy, fw, fh, ax, ay):
        g = head_glow(name, xx, yy, fw, fh)
        if g is not None:
            return g[1]
        col = stone(name, xx, yy, fw, fh, ax, ay)
        if name == "front":
            if yy == 2:
                col = darken(col, 0.35)
            if yy == 7 and 2 <= xx <= 5:
                col = MORTAR  # boca
        return col

    paint_box(tex, uv["head"], S["head"], head_paint, head_glow)
    paint_box(tex, uv["brow"], S["brow"], lambda *a: stone(*a, dark=0.1))
    paint_box(tex, uv["shoulder"], S["shoulder"], plain)

    # bracos com veios de runa
    def arm_glow(name, xx, yy, fw, fh):
        if name in ("right", "left", "front") and xx == fw // 2 and yy % 6 in (1, 2) and 2 < yy < fh - 2:
            return (glow, RUNE)
        return None

    def arm_paint(name, xx, yy, fw, fh, ax, ay):
        g = arm_glow(name, xx, yy, fw, fh)
        return g[1] if g else stone(name, xx, yy, fw, fh, ax, ay, moss_top=False)

    paint_box(tex, uv["arm"], S["arm"], arm_paint, arm_glow)

    def fist_paint(name, xx, yy, fw, fh, ax, ay):
        col = stone(name, xx, yy, fw, fh, ax, ay, moss_top=False, dark=0.05)
        if name == "front" and yy in (2, 5) and 0 < xx < fw - 1:
            col = darken(col, 0.35)  # nos dos dedos
        if name == "bottom":
            col = darken(col, 0.1)
        return col

    paint_box(tex, uv["fist"], S["fist"], fist_paint)
    paint_box(tex, uv["leg"], S["leg"], lambda *a: stone(*a, moss_top=False, dark=0.05))

    tex.save(os.path.join(A, "textures/entity/" + gid + ".png"))
    glow.save(os.path.join(A, "textures/entity/" + gid + "_glowmask.png"))

    bones = [
        bone("root", [0, 0, 0]),
        bone("body", [0, 19, 0], "root", [
            cube([-5, 19, -4], S["waist"], uv["waist"]),
            cube([-9, 25, -6], S["torso"], uv["torso"]),
            cube([-6, 31, 5], S["hump"], uv["hump"]),
        ]),
        bone("head", [0, 37, -3], "body", [
            cube([-4, 35, -10], S["head"], uv["head"]),
            cube([-5, 41, -11], S["brow"], uv["brow"]),
        ]),
        bone("right_arm", [-11, 35, 0], "body", [
            cube([-17, 30, -5], S["shoulder"], uv["shoulder"]),
            cube([-16, 12, -4], S["arm"], uv["arm"]),
            cube([-17, 3, -5], S["fist"], uv["fist"]),
        ]),
        bone("left_arm", [11, 35, 0], "body", [
            cube([9, 30, -5], S["shoulder"], uv["shoulder"]),
            cube([10, 12, -4], S["arm"], uv["arm"]),
            cube([9, 3, -5], S["fist"], uv["fist"]),
        ]),
        bone("right_leg", [-4.5, 19, 0], "root", [cube([-8, 0, -4], S["leg"], uv["leg"])]),
        bone("left_leg", [4.5, 19, 0], "root", [cube([1, 0, -4], S["leg"], uv["leg"])]),
    ]
    write_json("geo/entity/" + gid + ".geo.json", geo(gid, W, H, bones, 3, 3.5))

    anims = {
        "animation.%s.idle" % gid: {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (1.5, [2, 0, 0]), (3, [0, 0, 0])]),
                     "position": kf([(0, [0, 0, 0]), (1.5, [0, -0.4, 0]), (3, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (1.0, [0, 6, 0]), (2.0, [0, -6, 0]), (3, [0, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [0, 0, 2]), (1.5, [-3, 0, 4]), (3, [0, 0, 2])])},
            "left_arm": {"rotation": kf([(0, [0, 0, -2]), (1.5, [-3, 0, -4]), (3, [0, 0, -2])])},
        }},
        "animation.%s.walk" % gid: {"loop": True, "animation_length": 1.2, "bones": {
            "right_leg": {"rotation": kf([(0, [25, 0, 0]), (0.6, [-25, 0, 0]), (1.2, [25, 0, 0])])},
            "left_leg": {"rotation": kf([(0, [-25, 0, 0]), (0.6, [25, 0, 0]), (1.2, [-25, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [-18, 0, 3]), (0.6, [18, 0, 3]), (1.2, [-18, 0, 3])])},
            "left_arm": {"rotation": kf([(0, [18, 0, -3]), (0.6, [-18, 0, -3]), (1.2, [18, 0, -3])])},
            "body": {"rotation": kf([(0, [3, 0, 2]), (0.6, [3, 0, -2]), (1.2, [3, 0, 2])]),
                     "position": kf([(0, [0, 0, 0]), (0.3, [0, 0.6, 0]), (0.6, [0, 0, 0]), (0.9, [0, 0.6, 0]), (1.2, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, -2]), (0.6, [0, 0, 2]), (1.2, [0, 0, -2])])},
        }},
        "animation.%s.attack" % gid: {"loop": False, "animation_length": 0.7, "bones": {
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-150, 0, -10]), (0.45, [-40, 0, 0]), (0.7, [0, 0, 0])])},
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-150, 0, 10]), (0.45, [-40, 0, 0]), (0.7, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-12, 0, 0]), (0.45, [16, 0, 0]), (0.7, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.45, [10, 0, 0]), (0.7, [0, 0, 0])])},
        }},
        "animation.%s.summon" % gid: {"loop": False, "animation_length": 1.2, "bones": {
            "root": {"position": kf([(0, [0, -44, 0]), (0.8, [0, 2, 0]), (1.0, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [25, 0, 0]), (1.0, [0, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.7, [-70, 0, -15]), (1.2, [0, 0, 0])])},
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.7, [-70, 0, 15]), (1.2, [0, 0, 0])])},
        }},
    }
    write_json("animations/entity/" + gid + ".animation.json", {"format_version": "1.8.0", "animations": anims})


# ---------------------------------------------------------------------------------------
#  Silfide
# ---------------------------------------------------------------------------------------
def sylph():
    gid = "summoned_sylph"
    W, H = 64, 64
    tex = Canvas(W, H)
    at = Atlas(W, H)
    S = {
        "head": (6, 6, 6), "hair_top": (7, 2, 7), "hair_back": (7, 7, 2), "ponytail": (2, 3, 5),
        "torso": (5, 7, 3), "skirt": (6, 3, 4), "arm": (2, 6, 2), "wing": (10, 12, 0),
        "tail1": (4, 4, 3), "tail2": (2, 3, 2), "tail3": (1, 2, 1),
    }
    order = ["wing", "head", "hair_top", "hair_back", "torso", "skirt", "ponytail", "tail1", "arm", "tail2", "tail3"]
    uv = {k: at.alloc(k, S[k]) for k in order}
    rnd = random.Random(9)

    skin = rgba(226, 255, 244, 240)
    hair = rgba(62, 190, 168)
    hair_l = rgba(150, 240, 220)
    dress = rgba(159, 240, 208, 235)
    dress_d = rgba(92, 196, 166, 235)
    white = rgba(240, 255, 250, 235)

    def hair_paint(name, xx, yy, fw, fh, ax, ay):
        t = (yy / max(1, fh - 1)) if name not in ("top", "bottom") else 0.3
        col = mix(hair_l, hair, t)
        if (xx + yy) % 3 == 0:
            col = darken(col, 0.12)
        if name == "bottom":
            col = darken(col, 0.25)
        return col

    def head_paint(name, xx, yy, fw, fh, ax, ay):
        col = skin
        if name == "front":
            if yy <= 1:
                return hair_l if (xx + yy) % 2 else hair  # franja
            if yy in (2, 3) and xx in (1, 4):
                return rgba(70, 225, 255) if yy == 3 else rgba(230, 255, 255)
            if yy == 4 and xx in (0, 5):
                return rgba(255, 200, 210, 240)  # bochechas
            if yy == 5 and xx in (2, 3):
                return rgba(200, 150, 160, 240)
        if name in ("right", "left") and yy <= 2:
            return hair
        if name == "top":
            return hair
        if name == "back":
            return hair
        return col

    def dress_paint(name, xx, yy, fw, fh, ax, ay):
        t = yy / max(1, fh - 1)
        col = mix(dress, dress_d, t * 0.7)
        if name in ("front", "back", "right", "left") and yy == 0:
            col = white  # gola
        if name in ("front", "back", "right", "left") and yy == 4:
            col = rgba(70, 180, 150, 240)  # faixa
        if name == "front" and (xx, yy) in ((2, 2), (1, 2), (3, 2), (2, 1), (2, 3)):
            col = rgba(235, 255, 250, 245)  # brasao de vento
        if name == "top":
            col = skin
        return col

    def skirt_paint(name, xx, yy, fw, fh, ax, ay):
        col = mix(dress_d, dress, 0.3 + 0.2 * math.sin(xx))
        if yy == fh - 1:
            col = white
        return col

    def arm_paint(name, xx, yy, fw, fh, ax, ay):
        if yy < 2:
            return dress
        return skin

    def wing_paint(name, xx, yy, fw, fh, ax, ay):
        # asa de libelula translucida com veios
        cx, cy = 0.5, fh * 0.45
        nx = xx / fw
        ny = (yy - cy) / (fh * 0.55)
        inside = (nx * 1.05) ** 2 + (ny * (1.0 + nx * 0.6)) ** 2 < 1.0
        if not inside:
            return None
        edge = (nx * 1.05) ** 2 + (ny * (1.0 + nx * 0.6)) ** 2 > 0.78
        vein = (yy == int(cy)) or (abs(yy - (cy + nx * 4)) < 0.6) or (abs(yy - (cy - nx * 4)) < 0.6)
        if edge:
            return rgba(200, 255, 240, 200)
        if vein:
            return rgba(150, 235, 215, 175)
        a = 110 + int(30 * rnd.random())
        return rgba(215, 255, 245, a)

    def tail_paint(alpha):
        def p(name, xx, yy, fw, fh, ax, ay):
            col = mix(dress, white, 0.4 * ((xx + yy) % 2))
            return (col[0], col[1], col[2], alpha - int(40 * yy / max(1, fh)))
        return p

    paint_box(tex, uv["wing"], S["wing"], wing_paint)
    paint_box(tex, uv["head"], S["head"], head_paint)
    paint_box(tex, uv["hair_top"], S["hair_top"], hair_paint)
    paint_box(tex, uv["hair_back"], S["hair_back"], hair_paint)
    paint_box(tex, uv["ponytail"], S["ponytail"], hair_paint)
    paint_box(tex, uv["torso"], S["torso"], dress_paint)
    paint_box(tex, uv["skirt"], S["skirt"], skirt_paint)
    paint_box(tex, uv["arm"], S["arm"], arm_paint)
    paint_box(tex, uv["tail1"], S["tail1"], tail_paint(215))
    paint_box(tex, uv["tail2"], S["tail2"], tail_paint(170))
    paint_box(tex, uv["tail3"], S["tail3"], tail_paint(125))
    tex.save(os.path.join(A, "textures/entity/" + gid + ".png"))

    bones = [
        bone("root", [0, 0, 0]),
        bone("body", [0, 12, 0], "root", [
            cube([-2.5, 12, -1.5], S["torso"], uv["torso"]),
            cube([-3, 9, -2], S["skirt"], uv["skirt"]),
        ]),
        bone("head", [0, 19, 0], "body", [
            cube([-3, 19, -3], S["head"], uv["head"]),
            cube([-3.5, 24, -3.5], S["hair_top"], uv["hair_top"]),
            cube([-3.5, 17, 2], S["hair_back"], uv["hair_back"]),
        ]),
        bone("ponytail", [0, 23, 3], "head", [cube([-1, 21, 4], S["ponytail"], uv["ponytail"])]),
        bone("right_arm", [-3, 18, 0], "body", [cube([-4.5, 12, -1], S["arm"], uv["arm"])]),
        bone("left_arm", [3, 18, 0], "body", [cube([2.5, 12, -1], S["arm"], uv["arm"])]),
        bone("right_wing", [-1, 17, 1.5], "body", [cube([-11, 10, 1.5], S["wing"], uv["wing"])]),
        bone("left_wing", [1, 17, 1.5], "body", [cube([1, 10, 1.5], S["wing"], uv["wing"])]),
        bone("tail", [0, 9, 0], "body", [cube([-2, 5, -1.5], S["tail1"], uv["tail1"])]),
        bone("tail_tip", [0, 5, 0], "tail", [
            cube([-1, 2, -1], S["tail2"], uv["tail2"]),
            cube([-0.5, 0, -0.5], S["tail3"], uv["tail3"]),
        ]),
    ]
    write_json("geo/entity/" + gid + ".geo.json", geo(gid, W, H, bones, 2.5, 2.5))

    def flap(period, length, amp):
        frames = []
        t = 0.0
        sign = 1
        while t <= length + 1e-6:
            frames.append((round(t, 3), [0, amp * sign, 0]))
            t += period / 2
            sign = -sign
        return frames

    def mirror(frames):
        return [(t, [v[0], -v[1], -v[2]]) for t, v in frames]

    idle_wing = flap(0.5, 2.0, 25)
    fly_wing = flap(0.25, 1.0, 35)
    anims = {
        "animation.%s.idle" % gid: {"loop": True, "animation_length": 2.0, "bones": {
            "root": {"position": kf([(0, [0, 0, 0]), (1.0, [0, 1.2, 0]), (2.0, [0, 0, 0])])},
            "left_wing": {"rotation": kf(idle_wing)},
            "right_wing": {"rotation": kf(mirror(idle_wing))},
            "right_arm": {"rotation": kf([(0, [0, 0, 10]), (1.0, [-5, 0, 18]), (2.0, [0, 0, 10])])},
            "left_arm": {"rotation": kf([(0, [0, 0, -10]), (1.0, [-5, 0, -18]), (2.0, [0, 0, -10])])},
            "tail": {"rotation": kf([(0, [10, 0, 0]), (1.0, [-5, 0, 4]), (2.0, [10, 0, 0])])},
            "tail_tip": {"rotation": kf([(0, [15, 0, 0]), (1.0, [-10, 0, -6]), (2.0, [15, 0, 0])])},
            "ponytail": {"rotation": kf([(0, [10, 0, 0]), (1.0, [25, 0, 0]), (2.0, [10, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (1.0, [-3, 0, 3]), (2.0, [0, 0, 0])])},
        }},
        "animation.%s.fly" % gid: {"loop": True, "animation_length": 1.0, "bones": {
            "body": {"rotation": kf([(0, [20, 0, 0]), (0.5, [24, 0, 0]), (1.0, [20, 0, 0])])},
            "left_wing": {"rotation": kf(fly_wing)},
            "right_wing": {"rotation": kf(mirror(fly_wing))},
            "right_arm": {"rotation": kf([(0, [30, 0, 15]), (1.0, [30, 0, 15])])},
            "left_arm": {"rotation": kf([(0, [30, 0, -15]), (1.0, [30, 0, -15])])},
            "tail": {"rotation": kf([(0, [35, 0, 0]), (0.5, [28, 0, 5]), (1.0, [35, 0, 0])])},
            "tail_tip": {"rotation": kf([(0, [20, 0, 0]), (0.5, [30, 0, -6]), (1.0, [20, 0, 0])])},
            "ponytail": {"rotation": kf([(0, [40, 0, 0]), (0.5, [50, 0, 0]), (1.0, [40, 0, 0])])},
        }},
        "animation.%s.attack" % gid: {"loop": False, "animation_length": 0.5, "bones": {
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-140, 0, 20]), (0.3, [30, 0, -10]), (0.5, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, -25, 0]), (0.3, [0, 30, 0]), (0.5, [0, 0, 0])])},
            "left_wing": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, 40, 0]), (0.5, [0, 0, 0])])},
            "right_wing": {"rotation": kf([(0, [0, 0, 0]), (0.15, [0, -40, 0]), (0.5, [0, 0, 0])])},
        }},
    }
    write_json("animations/entity/" + gid + ".animation.json", {"format_version": "1.8.0", "animations": anims})


def spawn_eggs():
    """Modelos dos ovos das invocacoes (o jogo pinta o ovo com as cores do item)."""
    for gid in ("summoned_stone_golem", "summoned_sylph"):
        write_json("models/item/" + gid + "_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


if __name__ == "__main__":
    raised_earth_stone()
    earthen_quicksand()
    icon_stoneskin()
    icon_weighted()
    icon_double_jump()
    icon_glide()
    icon_swiftness()
    icon_barrier()
    icon_silent_steps()
    golem()
    sylph()
    spawn_eggs()
    print("earth_wind: assets gerados em", A)
