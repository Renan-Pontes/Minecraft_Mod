"""
Gera os assets do modulo origins_b (rode da raiz do projeto: py -I tools/gen/origins_b.py):
  - invocacoes GeckoLib: summoned_imp, summoned_demon, undead_servant, bone_warrior
    (textura + glowmask, .geo.json, .animation.json)
  - item shadow_blade (textura + modelo handheld) e modelos dos ovos
  - icones 18x18 dos efeitos bone_shield, death_contract, demon_wings, lich_form
  - tag minecraft:undead (servo e guerreiro de ossos) no merge do modulo
O script confere se as regioes de UV dos cubos se sobrepoem (erro = para tudo).
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix, item_model  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/origins_b")
T = (0, 0, 0, 0)
TAU = 2 * math.pi


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


# =====================================================================================
#  UV de caixa (Bedrock): retangulo (x, y, w, h) de cada face. "right" = lado -X, "front" = -Z (frente).
# =====================================================================================
def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(c, rect, col):
    x, y, w, h = rect
    if w > 0 and h > 0:
        c.rect(x, y, x + w - 1, y + h - 1, col)


def vgrad(c, rect, top, bottom):
    x, y, w, h = rect
    for j in range(h):
        t = j / max(1, h - 1)
        for i in range(w):
            c.set(x + i, y + j, mix(top, bottom, t))


def paint_box(c, u, v, w, h, d, col, shade=True, grad=None):
    """Todas as faces numa cor (topo mais claro, lados/baixo mais escuros); grad = cor de baixo (degrade vertical)."""
    f = box_faces(u, v, w, h, d)
    for key in ("front", "back", "right", "left"):
        base = col if key == "front" else darken(col, 0.12 if key == "back" else 0.07)
        if grad:
            vgrad(c, f[key], base, darken(grad, 0.1 if key != "front" else 0.0))
        else:
            fill(c, f[key], base if shade else col)
    fill(c, f["top"], lighten(col, 0.12) if shade else col)
    fill(c, f["bottom"], darken(col, 0.22) if shade else col)
    return f


def side_faces(f):
    return [f[k] for k in ("front", "right", "back", "left")]


def point_in_poly(x, y, poly):
    """Ponto dentro de poligono (regra par-impar)."""
    inside = False
    n = len(poly)
    for i in range(n):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % n]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            inside = not inside
    return inside


def r3(v):
    return [round(x, 2) for x in v]


def sampled(length, steps, fn):
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        out[str(round(t, 4))] = r3(fn(t))
    return out


def cube(origin, size, uv, inflate=None):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    return c


def check_uv(name, bones, tw, th):
    """Para tudo se duas regioes de UV se sobrepoem ou saem da textura."""
    rects = []
    for b in bones:
        for cb in b.get("cubes", []):
            w, h, d = cb["size"]
            u, v = cb["uv"]
            rw, rh = 2 * (w + d), h + d
            if u + rw > tw or v + rh > th:
                raise SystemExit(f"[{name}] UV fora da textura: bone {b['name']} {cb}")
            rects.append((u, v, u + rw, v + rh, b["name"]))
    for i in range(len(rects)):
        for j in range(i + 1, len(rects)):
            a, b = rects[i], rects[j]
            if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                raise SystemExit(f"[{name}] UV sobreposto: {a} x {b}")


def write_geo(name, bones, tw, th, vbw, vbh):
    check_uv(name, bones, tw, th)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": vbw, "visible_bounds_height": vbh, "visible_bounds_offset": [0, vbh / 2, 0]},
        "bones": bones}]}
    save_json(os.path.join(A, f"geo/entity/{name}.geo.json"), geo)


def write_anims(name, anims):
    save_json(os.path.join(A, f"animations/entity/{name}.animation.json"), {
        "format_version": "1.8.0",
        "animations": {f"animation.{name}.{k}": v for k, v in anims.items()}})


# =====================================================================================
#  DIABRETE (summoned_imp) 64x64
# =====================================================================================
IMP_BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "body", "parent": "root", "pivot": [0, 7, 0],
     "cubes": [cube([-2.5, 5, -1.5], [5, 5, 3], [0, 16])]},
    {"name": "head", "parent": "body", "pivot": [0, 10, 0],
     "cubes": [cube([-3, 10, -2.5], [6, 5, 5], [0, 0]),
               cube([-1, 11, -3.5], [2, 1, 1], [22, 0])]},
    {"name": "left_horn", "parent": "head", "pivot": [2, 15, 0],
     "cubes": [cube([1.5, 15, -0.5], [1, 2, 1], [28, 0]), cube([2.5, 16.5, 0], [1, 2, 1], [28, 4])]},
    {"name": "right_horn", "parent": "head", "pivot": [-2, 15, 0],
     "cubes": [cube([-2.5, 15, -0.5], [1, 2, 1], [32, 0]), cube([-3.5, 16.5, 0], [1, 2, 1], [32, 4])]},
    {"name": "left_ear", "parent": "head", "pivot": [3, 13, 0],
     "cubes": [cube([3, 12, -0.5], [2, 2, 1], [36, 0])]},
    {"name": "right_ear", "parent": "head", "pivot": [-3, 13, 0],
     "cubes": [cube([-5, 12, -0.5], [2, 2, 1], [42, 0])]},
    {"name": "left_arm", "parent": "body", "pivot": [3.5, 9.5, 0],
     "cubes": [cube([2.5, 5.5, -1], [2, 4, 2], [16, 16])]},
    {"name": "right_arm", "parent": "body", "pivot": [-3.5, 9.5, 0],
     "cubes": [cube([-4.5, 5.5, -1], [2, 4, 2], [24, 16])]},
    {"name": "left_leg", "parent": "root", "pivot": [1.25, 5, 0],
     "cubes": [cube([0.25, 1, -1], [2, 4, 2], [0, 24])]},
    {"name": "right_leg", "parent": "root", "pivot": [-1.25, 5, 0],
     "cubes": [cube([-2.25, 1, -1], [2, 4, 2], [8, 24])]},
    {"name": "tail", "parent": "body", "pivot": [0, 6, 1.5],
     "cubes": [cube([-0.5, 5.5, 1.5], [1, 1, 4], [16, 24])]},
    {"name": "tail_tip", "parent": "tail", "pivot": [0, 6, 5.5],
     "cubes": [cube([-1, 5, 5.5], [2, 2, 1], [26, 24])]},
    {"name": "left_wing", "parent": "body", "pivot": [1.5, 10, 1.5],
     "cubes": [cube([2, 6, 1.5], [8, 7, 0], [0, 32])]},
    {"name": "right_wing", "parent": "body", "pivot": [-1.5, 10, 1.5],
     "cubes": [cube([-10, 6, 1.5], [8, 7, 0], [0, 40])]},
]


def bat_wing(c, x0, y0, w, h, root_left, membrane, membrane_dk, bone):
    """Asa de morcego num plano w x h: osso no topo, 3 dedos, membrana recortada embaixo."""
    def put(i, j, col):
        xx = i if root_left else w - 1 - i
        c.set(x0 + xx, y0 + j, col)
    for i in range(w):
        t = i / max(1, w - 1)
        # recorte da borda de baixo: tres "conchas" entre os dedos
        scallop = 0.8 + 1.4 * abs(math.sin(t * math.pi * 2.6))
        bottom = (h - 1) - scallop * (0.4 + 0.6 * t)
        for j in range(h):
            if j <= bottom:
                put(i, j, mix(membrane, membrane_dk, j / max(1, h - 1)))
    for i in range(w):
        put(i, 0, bone)
    for (tx, ty) in ((w - 1, h - 3), (int(w * 0.62), h - 1), (int(w * 0.3), h - 1)):
        x, y = 0, 0
        steps = max(tx, ty)
        for s in range(steps + 1):
            put(round(x + (tx - x) * s / steps), round(y + (ty - y) * s / steps), bone)
    put(w - 1, 0, lighten(bone, 0.35))  # garrinha na ponta


def imp_textures():
    c = Canvas(64, 64)
    g = Canvas(64, 64)
    skin = hex_rgba("B3262B")
    skin_lt = hex_rgba("D9443F")
    skin_dk = hex_rgba("7A161C")
    belly = hex_rgba("E0703A")
    horn = hex_rgba("2A1A1A")
    horn_tip = hex_rgba("6A5048")
    eye = hex_rgba("FFE14D")
    pupil = hex_rgba("3A0A00")
    mouth = hex_rgba("3A0A0A")
    teeth = hex_rgba("F0E6C8")
    claw = hex_rgba("1E1010")

    # Cabeca 6x5x5
    f = paint_box(c, 0, 0, 6, 5, 5, skin)
    fx, fy, _, _ = f["front"]
    for i in range(6):
        c.set(fx + i, fy, skin_dk)  # sobrancelha franzida
    c.set(fx + 1, fy + 1, eye); c.set(fx + 4, fy + 1, eye)
    c.set(fx + 2, fy + 1, skin_dk); c.set(fx + 3, fy + 1, skin_dk)
    g.set(fx + 1, fy + 1, eye); g.set(fx + 4, fy + 1, eye)
    c.set(fx + 0, fy + 1, lighten(skin, 0.1)); c.set(fx + 5, fy + 1, lighten(skin, 0.1))
    for i in range(1, 5):
        c.set(fx + i, fy + 3, mouth)
    c.set(fx + 1, fy + 3, teeth); c.set(fx + 4, fy + 3, teeth)
    c.set(fx + 0, fy + 2, skin_lt); c.set(fx + 5, fy + 2, skin_lt)
    c.set(fx + 2, fy + 4, skin_dk); c.set(fx + 3, fy + 4, skin_dk)
    fill(c, f["top"], skin_dk)
    tx, ty, tw, th = f["top"]
    for i in range(tw):
        c.set(tx + i, ty + th - 1, skin)
    # Focinho 2x1x1
    sn = paint_box(c, 22, 0, 2, 1, 1, skin_lt)
    c.set(sn["front"][0], sn["front"][1], skin_dk)
    c.set(sn["front"][0] + 1, sn["front"][1], skin_dk)
    # Chifres (base escura, ponta clara)
    for u in (28, 32):
        paint_box(c, u, 0, 1, 2, 1, horn)
        p = paint_box(c, u, 4, 1, 2, 1, horn)
        for r in side_faces(p):
            c.set(r[0], r[1], horn_tip)
        fill(c, p["top"], lighten(horn_tip, 0.2))
    # Orelhas pontudas
    for u in (36, 42):
        e = paint_box(c, u, 0, 2, 2, 1, skin)
        x, y, w, h = e["front"]
        c.set(x, y + 1, skin_dk); c.set(x + 1, y + 1, hex_rgba("5A0E12"))
    # Corpo 5x5x3: barriga clara na frente, costas escuras
    b = paint_box(c, 0, 16, 5, 5, 3, skin)
    bx, by, bw, bh = b["front"]
    for j in range(1, 5):
        for i in range(1, 4):
            c.set(bx + i, by + j, mix(belly, skin, 0.25 if j in (1, 4) else 0.0))
    c.set(bx + 2, by + 3, darken(belly, 0.25))  # umbigo
    vgrad(c, b["back"], skin_dk, darken(skin_dk, 0.2))
    # Bracos e pernas
    for u in (16, 24):
        a = paint_box(c, u, 16, 2, 4, 2, skin)
        for r in side_faces(a):
            x, y, w, h = r
            for i in range(w):
                c.set(x + i, y + h - 1, claw)
        fill(c, a["bottom"], claw)
    for u in (0, 8):
        lg = paint_box(c, u, 24, 2, 4, 2, skin, grad=skin_dk)
        for r in side_faces(lg):
            x, y, w, h = r
            for i in range(w):
                c.set(x + i, y + h - 1, horn)  # cascos
        fill(c, lg["bottom"], horn)
    # Cauda e ponta de flecha
    paint_box(c, 16, 24, 1, 1, 4, skin_dk)
    tp = paint_box(c, 26, 24, 2, 2, 1, horn)
    c.set(tp["front"][0], tp["front"][1], horn_tip)
    # Asas
    for (v, root_left) in ((32, True), (40, False)):
        # frente e verso (o verso e espelhado)
        bat_wing(c, 0, v, 8, 7, root_left, hex_rgba("8E1A22", 235), hex_rgba("4A0A10", 235), hex_rgba("2A0A0E"))
        bat_wing(c, 8, v, 8, 7, not root_left, hex_rgba("6E1218", 235), hex_rgba("3A080C", 235), hex_rgba("2A0A0E"))
    c.noise(0.05, seed=11)
    # brilho por cima do ruido
    g.set(fx + 1, fy + 1, eye); g.set(fx + 4, fy + 1, eye)
    c.save(os.path.join(A, "textures/entity/summoned_imp.png"))
    g.save(os.path.join(A, "textures/entity/summoned_imp_glowmask.png"))


def imp_anims():
    L = 0.5
    fly = {"loop": True, "animation_length": L, "bones": {
        "root": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(TAU * t / L), 0])},
        "left_wing": {"rotation": sampled(L, 12, lambda t: [0, 45 * math.sin(TAU * t / L), 8 * math.sin(TAU * t / L)])},
        "right_wing": {"rotation": sampled(L, 12, lambda t: [0, -45 * math.sin(TAU * t / L), -8 * math.sin(TAU * t / L)])},
        "tail": {"rotation": sampled(L, 8, lambda t: [-20 + 8 * math.sin(TAU * t / L), 18 * math.sin(TAU * t / L * 0.5), 0])},
        "tail_tip": {"rotation": sampled(L, 8, lambda t: [0, 20 * math.sin(TAU * t / L * 0.5 + 1.0), 0])},
        "left_leg": {"rotation": sampled(L, 8, lambda t: [15 + 10 * math.sin(TAU * t / L), 0, -5])},
        "right_leg": {"rotation": sampled(L, 8, lambda t: [15 - 10 * math.sin(TAU * t / L), 0, 5])},
        "left_arm": {"rotation": sampled(L, 8, lambda t: [-15 + 6 * math.sin(TAU * t / L), 0, -12])},
        "right_arm": {"rotation": sampled(L, 8, lambda t: [-15 - 6 * math.sin(TAU * t / L), 0, 12])},
        "body": {"rotation": sampled(L, 8, lambda t: [10 + 3 * math.sin(TAU * t / L), 0, 0])},
        "left_ear": {"rotation": sampled(L, 4, lambda t: [0, 0, -6 * math.sin(TAU * t / L)])},
        "right_ear": {"rotation": sampled(L, 4, lambda t: [0, 0, 6 * math.sin(TAU * t / L)])},
    }}
    cast = {"loop": False, "animation_length": 0.4, "bones": {
        "right_arm": {"rotation": {"0.0": [0, 0, 0], "0.1": [-40, 0, 20], "0.2": [-95, 0, -10], "0.4": [0, 0, 0]}},
        "left_arm": {"rotation": {"0.0": [0, 0, 0], "0.1": [-40, 0, -20], "0.2": [-95, 0, 10], "0.4": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.1": [-15, 0, 0], "0.2": [12, 0, 0], "0.4": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.2": [15, 0, 0], "0.4": [0, 0, 0]}},
    }}
    write_anims("summoned_imp", {"fly": fly, "cast": cast})


# =====================================================================================
#  DEMONIO MAIOR (summoned_demon) 128x128
# =====================================================================================
DEMON_BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "waist", "parent": "root", "pivot": [0, 24, 0],
     "cubes": [cube([-6, 21, -4], [12, 4, 8], [0, 22]),
               cube([-3, 13, -4.2], [6, 8, 0], [40, 22])]},
    {"name": "body", "parent": "waist", "pivot": [0, 25, 0],
     "cubes": [cube([-7, 25, -4.5], [14, 13, 9], [0, 0])]},
    {"name": "head", "parent": "body", "pivot": [0, 38, -2],
     "cubes": [cube([-4, 38, -7], [8, 8, 8], [48, 0])]},
    {"name": "jaw", "parent": "head", "pivot": [0, 39, -2],
     "cubes": [cube([-3.5, 36, -7.5], [7, 3, 6], [80, 0])]},
    {"name": "left_horn", "parent": "head", "pivot": [4, 44, -3],
     "cubes": [cube([3.5, 43, -4.5], [3, 3, 3], [106, 0]), cube([5.5, 45.5, -4], [2, 4, 2], [106, 6]),
               cube([6.5, 49, -3.5], [1, 3, 1], [114, 6])]},
    {"name": "right_horn", "parent": "head", "pivot": [-4, 44, -3],
     "cubes": [cube([-6.5, 43, -4.5], [3, 3, 3], [106, 12]), cube([-7.5, 45.5, -4], [2, 4, 2], [106, 18]),
               cube([-7.5, 49, -3.5], [1, 3, 1], [114, 18])]},
    {"name": "left_arm", "parent": "body", "pivot": [9, 35, 0],
     "cubes": [cube([7, 25, -3], [5, 11, 6], [0, 34]), cube([7, 35, -2], [4, 3, 4], [70, 34])]},
    {"name": "left_forearm", "parent": "left_arm", "pivot": [9.5, 25, 0],
     "cubes": [cube([7, 14, -3.5], [6, 11, 7], [22, 34])]},
    {"name": "left_claws", "parent": "left_forearm", "pivot": [9.5, 14, 0],
     "cubes": [cube([7.5, 11, -3], [5, 3, 6], [48, 34])]},
    {"name": "right_arm", "parent": "body", "pivot": [-9, 35, 0],
     "cubes": [cube([-12, 25, -3], [5, 11, 6], [0, 52]), cube([-11, 35, -2], [4, 3, 4], [70, 41])]},
    {"name": "right_forearm", "parent": "right_arm", "pivot": [-9.5, 25, 0],
     "cubes": [cube([-13, 14, -3.5], [6, 11, 7], [22, 52])]},
    {"name": "right_claws", "parent": "right_forearm", "pivot": [-9.5, 14, 0],
     "cubes": [cube([-12.5, 11, -3], [5, 3, 6], [48, 52])]},
    {"name": "left_leg", "parent": "waist", "pivot": [3.5, 24, 0],
     "cubes": [cube([1, 15, -3.5], [6, 10, 7], [0, 70])]},
    {"name": "left_shin", "parent": "left_leg", "pivot": [4, 15, 0],
     "cubes": [cube([1.5, 3, -3], [5, 12, 6], [26, 70]), cube([1, 0, -4], [6, 3, 7], [48, 70])]},
    {"name": "right_leg", "parent": "waist", "pivot": [-3.5, 24, 0],
     "cubes": [cube([-7, 15, -3.5], [6, 10, 7], [0, 88])]},
    {"name": "right_shin", "parent": "right_leg", "pivot": [-4, 15, 0],
     "cubes": [cube([-6.5, 3, -3], [5, 12, 6], [26, 88]), cube([-7, 0, -4], [6, 3, 7], [48, 88])]},
    {"name": "tail", "parent": "waist", "pivot": [0, 22, 3.5],
     "cubes": [cube([-1.5, 20.5, 3.5], [3, 3, 8], [74, 70])]},
    {"name": "tail2", "parent": "tail", "pivot": [0, 22, 11.5],
     "cubes": [cube([-1, 21, 11.5], [2, 2, 8], [74, 81])]},
    {"name": "tail_tip", "parent": "tail2", "pivot": [0, 22, 19.5],
     "cubes": [cube([-2, 20.5, 19.5], [4, 3, 2], [96, 70])]},
    {"name": "left_wing", "parent": "body", "pivot": [2.5, 36, 4.5],
     "cubes": [cube([2.5, 22, 4.5], [24, 18, 0], [0, 106]), cube([2.5, 39, 4], [24, 2, 1], [74, 98])]},
    {"name": "right_wing", "parent": "body", "pivot": [-2.5, 36, 4.5],
     "cubes": [cube([-26.5, 22, 4.5], [24, 18, 0], [48, 106]), cube([-26.5, 39, 4], [24, 2, 1], [74, 101])]},
]


def lava_cracks(c, g, rect, seed, density=0.08):
    """Rachaduras de lava (brilham na glowmask) num retangulo de face."""
    import random
    rnd = random.Random(seed)
    x, y, w, h = rect
    lava = hex_rgba("FF7A1A")
    hot = hex_rgba("FFC14D")
    n = max(1, int(w * h * density / 3))
    for _ in range(n):
        cx, cy = rnd.randrange(w), rnd.randrange(h)
        length = rnd.randint(2, 4)
        dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (-1, 1)))
        for s in range(length):
            px, py = cx + dx * s, cy + dy * s
            if 0 <= px < w and 0 <= py < h:
                col = hot if s == length // 2 else lava
                c.set(x + px, y + py, col)
                g.set(x + px, y + py, col)


def demon_textures():
    c = Canvas(128, 128)
    g = Canvas(128, 128)
    skin = hex_rgba("5A1E1F")
    skin_lt = hex_rgba("7A2A26")
    skin_dk = hex_rgba("3A1416")
    plate = hex_rgba("2A1416")
    horn = hex_rgba("1E1414")
    horn_mid = hex_rgba("3B2E2B")
    horn_tip = hex_rgba("C9B79C")
    eye = hex_rgba("FFF2A0")
    eye_core = hex_rgba("FFB020")
    hoof = hex_rgba("141010")
    claw = hex_rgba("D8CBB0")
    mouth = hex_rgba("1A0606")
    fang = hex_rgba("EFE6D0")

    # Peito 14x13x9: musculos, placas escuras, rachaduras de lava
    ch = paint_box(c, 0, 0, 14, 13, 9, skin, grad=skin_dk)
    x, y, w, h = ch["front"]
    for i in range(w):
        for j in range(h):
            # peitorais (claros em cima), abdomen em gomos
            if j < 5 and (i != 6 and i != 7):
                c.set(x + i, y + j, mix(skin_lt, skin, j / 5))
            elif j >= 6 and 3 <= i <= 10 and (j - 6) % 3 != 2 and i not in (6, 7):
                c.set(x + i, y + j, lighten(skin, 0.08))
    for j in range(h):
        c.set(x + 6, y + j, skin_dk); c.set(x + 7, y + j, skin_dk)
    c.set(x + 3, y + 3, hex_rgba("FF7A1A")); g.set(x + 3, y + 3, hex_rgba("FF7A1A"))  # marca do pacto
    c.set(x + 10, y + 3, hex_rgba("FF7A1A")); g.set(x + 10, y + 3, hex_rgba("FF7A1A"))
    for r in (ch["front"], ch["back"], ch["left"], ch["right"]):
        lava_cracks(c, g, r, seed=r[0] * 7 + r[1])
    # espinha nas costas
    bx, by, bw, bh = ch["back"]
    for j in range(0, bh, 2):
        c.set(bx + bw // 2, by + j, plate); c.set(bx + bw // 2 - 1, by + j, plate)
    fill(c, ch["top"], skin_lt)
    # Quadril 12x4x8 (cinto de ferro escuro) e tanga
    pv = paint_box(c, 0, 22, 12, 4, 8, plate)
    for r in side_faces(pv):
        xx, yy, ww, hh = r
        for i in range(ww):
            c.set(xx + i, yy, hex_rgba("4A3A36"))
            if i % 4 == 1:
                c.set(xx + i, yy + 2, hex_rgba("8A7A70"))  # rebites
    lc = box_faces(40, 22, 6, 8, 0)
    vgrad(c, lc["front"], hex_rgba("3A0E10"), hex_rgba("200608"))
    vgrad(c, lc["back"], hex_rgba("2A0A0C"), hex_rgba("180405"))
    xx, yy, ww, hh = lc["front"]
    for i in range(ww):
        if i % 2 == 0:
            c.set(xx + i, yy + hh - 1, T)  # barra rasgada
    c.set(xx + 2, yy + 2, hex_rgba("FF7A1A")); c.set(xx + 3, yy + 2, hex_rgba("FF7A1A"))
    g.set(xx + 2, yy + 2, hex_rgba("FF7A1A")); g.set(xx + 3, yy + 2, hex_rgba("FF7A1A"))

    # Cabeca 8x8x8: olhos brilhando, testa pesada
    hd = paint_box(c, 48, 0, 8, 8, 8, skin)
    x, y, w, h = hd["front"]
    for i in range(w):
        c.set(x + i, y + 2, skin_dk)
        c.set(x + i, y + 1, skin_lt)
    for (i, j) in ((1, 3), (2, 3), (5, 3), (6, 3)):
        col = eye if i in (2, 5) else eye_core
        c.set(x + i, y + j, col); g.set(x + i, y + j, col)
    c.set(x + 3, y + 5, skin_dk); c.set(x + 4, y + 5, skin_dk)  # narinas
    for i in range(1, 7):
        c.set(x + i, y + 7, mouth)
    c.set(x + 1, y + 6, fang); c.set(x + 6, y + 6, fang)
    lava_cracks(c, g, hd["top"], seed=91, density=0.05)
    fill(c, hd["back"], skin_dk)
    # Mandibula 7x3x6
    jw = paint_box(c, 80, 0, 7, 3, 6, skin_dk)
    x, y, w, h = jw["front"]
    for i in range(w):
        c.set(x + i, y, mouth)
    c.set(x + 1, y, fang); c.set(x + 5, y, fang); c.set(x + 3, y, fang)
    fill(c, jw["top"], hex_rgba("6A1010"))  # lingua/boca vista de cima
    # Chifres: base escura -> ponta osso
    for (vb, vm) in ((0, 6), (12, 18)):
        paint_box(c, 106, vb, 3, 3, 3, horn)
        m = paint_box(c, 106, vm, 2, 4, 2, horn_mid, grad=horn)
        tp = paint_box(c, 114, vm, 1, 3, 1, horn_tip, grad=horn_mid)
        fill(c, tp["top"], lighten(horn_tip, 0.3))
        for r in side_faces(m):
            c.set(r[0], r[1] + 1, lighten(horn_mid, 0.15))

    # Bracos: ombro, antebraco grosso, garras
    for (vu, vs) in ((34, 34), (52, 41)):
        up = paint_box(c, 0, vu, 5, 11, 6, skin, grad=skin_dk)
        for r in side_faces(up):
            lava_cracks(c, g, r, seed=r[0] + r[1] * 3, density=0.06)
        fa = paint_box(c, 22, vu, 6, 11, 7, skin_dk)
        for r in side_faces(fa):
            xx, yy, ww, hh = r
            for i in range(ww):
                c.set(xx + i, yy, plate)
                c.set(xx + i, yy + 1, hex_rgba("4A3A36"))  # bracadeira de ferro
            lava_cracks(c, g, (xx, yy + 3, ww, hh - 3), seed=xx * 5 + yy, density=0.07)
        cl = paint_box(c, 48, vu, 5, 3, 6, skin_dk)
        for r in side_faces(cl):
            xx, yy, ww, hh = r
            for i in range(0, ww, 2):
                c.set(xx + i, yy + hh - 1, claw)
                c.set(xx + i, yy + hh - 2, darken(claw, 0.2))
        fill(c, cl["bottom"], claw)
        sp = paint_box(c, 70, vs, 4, 3, 4, horn)
        fill(c, sp["top"], horn_tip)
    # Pernas: coxa, canela, casco
    for v in (70, 88):
        th_ = paint_box(c, 0, v, 6, 10, 7, skin, grad=skin_dk)
        for r in side_faces(th_):
            lava_cracks(c, g, r, seed=r[0] * 3 + r[1], density=0.05)
        sh = paint_box(c, 26, v, 5, 12, 6, skin_dk, grad=hex_rgba("2A0E10"))
        for r in side_faces(sh):
            xx, yy, ww, hh = r
            for i in range(ww):
                if (i + yy) % 3 == 0:
                    c.set(xx + i, yy + 2, plate)
        hf = paint_box(c, 48, v, 6, 3, 7, hoof)
        x, y, w, h = hf["front"]
        c.set(x + w // 2, y, hex_rgba("2A2420")); c.set(x + w // 2, y + 1, hex_rgba("2A2420"))
    # Cauda
    t1 = paint_box(c, 74, 70, 3, 3, 8, skin_dk)
    t2 = paint_box(c, 74, 81, 2, 2, 8, skin_dk, grad=plate)
    for r in side_faces(t1) + side_faces(t2):
        if r[2] > 3:
            for i in range(0, r[2], 3):
                c.set(r[0] + i, r[1], plate)
    tt = paint_box(c, 96, 70, 4, 3, 2, horn)
    fill(c, tt["front"], horn_mid)
    c.set(tt["front"][0] + 1, tt["front"][1] + 1, hex_rgba("FF7A1A"))
    g.set(tt["front"][0] + 1, tt["front"][1] + 1, hex_rgba("FF7A1A"))

    # Asas 24x18 (planos) + osso superior 24x2x1
    for (u, root_left) in ((0, True), (48, False)):
        bat_wing(c, u, 106, 24, 18, root_left, hex_rgba("6A1218", 235), hex_rgba("2A0608", 225), hex_rgba("1E0A0A"))
        bat_wing(c, u + 24, 106, 24, 18, not root_left, hex_rgba("4E0E12", 235), hex_rgba("1E0405", 225), hex_rgba("1E0A0A"))
        # veias de brasa na membrana (frente)
        for k in range(5):
            i = 4 + k * 4
            xx = u + (i if root_left else 23 - i)
            yy = 106 + 3 + (k % 3) * 3
            if c.get(xx, yy)[3] > 0:
                c.set(xx, yy, hex_rgba("C0401A", 240))
                g.set(xx, yy, hex_rgba("C0401A", 200))
    for v in (98, 101):
        wb = paint_box(c, 74, v, 24, 2, 1, hex_rgba("1E0A0A"))
        for r in (wb["front"], wb["back"]):
            c.set(r[0] + (0 if v == 98 else r[2] - 1), r[1], horn_tip)
    c.noise(0.05, seed=23)
    c.save(os.path.join(A, "textures/entity/summoned_demon.png"))
    g.save(os.path.join(A, "textures/entity/summoned_demon_glowmask.png"))


def demon_anims():
    LI = 3.0
    idle = {"loop": True, "animation_length": LI, "bones": {
        "body": {"rotation": sampled(LI, 12, lambda t: [2 + 2 * math.sin(TAU * t / LI), 0, 0]),
                 "position": sampled(LI, 12, lambda t: [0, 0.3 * math.sin(TAU * t / LI), 0])},
        "head": {"rotation": sampled(LI, 12, lambda t: [3 * math.sin(TAU * t / LI + 1), 6 * math.sin(TAU * t / LI * 0.5), 0])},
        "jaw": {"rotation": sampled(LI, 12, lambda t: [4 + 4 * math.sin(TAU * t / LI * 2), 0, 0])},
        "left_arm": {"rotation": sampled(LI, 12, lambda t: [3 * math.sin(TAU * t / LI), 0, -6 - 2 * math.sin(TAU * t / LI)])},
        "right_arm": {"rotation": sampled(LI, 12, lambda t: [-3 * math.sin(TAU * t / LI), 0, 6 + 2 * math.sin(TAU * t / LI)])},
        "left_forearm": {"rotation": sampled(LI, 6, lambda t: [-12, 0, 0])},
        "right_forearm": {"rotation": sampled(LI, 6, lambda t: [-12, 0, 0])},
        "left_wing": {"rotation": sampled(LI, 12, lambda t: [0, -25 + 10 * math.sin(TAU * t / LI), 0])},
        "right_wing": {"rotation": sampled(LI, 12, lambda t: [0, 25 - 10 * math.sin(TAU * t / LI), 0])},
        "tail": {"rotation": sampled(LI, 12, lambda t: [-15, 15 * math.sin(TAU * t / LI), 0])},
        "tail2": {"rotation": sampled(LI, 12, lambda t: [8, 15 * math.sin(TAU * t / LI - 0.8), 0])},
        "tail_tip": {"rotation": sampled(LI, 12, lambda t: [0, 15 * math.sin(TAU * t / LI - 1.6), 0])},
    }}
    LW = 1.6
    walk = {"loop": True, "animation_length": LW, "bones": {
        "left_leg": {"rotation": sampled(LW, 12, lambda t: [26 * math.sin(TAU * t / LW), 0, 0])},
        "right_leg": {"rotation": sampled(LW, 12, lambda t: [-26 * math.sin(TAU * t / LW), 0, 0])},
        "left_shin": {"rotation": sampled(LW, 12, lambda t: [max(0, -30 * math.sin(TAU * t / LW + 0.6)), 0, 0])},
        "right_shin": {"rotation": sampled(LW, 12, lambda t: [max(0, 30 * math.sin(TAU * t / LW + 0.6)), 0, 0])},
        "left_arm": {"rotation": sampled(LW, 12, lambda t: [-18 * math.sin(TAU * t / LW), 0, -6])},
        "right_arm": {"rotation": sampled(LW, 12, lambda t: [18 * math.sin(TAU * t / LW), 0, 6])},
        "left_forearm": {"rotation": sampled(LW, 6, lambda t: [-15, 0, 0])},
        "right_forearm": {"rotation": sampled(LW, 6, lambda t: [-15, 0, 0])},
        "waist": {"position": sampled(LW, 12, lambda t: [0, -0.8 * abs(math.sin(TAU * t / LW)), 0])},
        "body": {"rotation": sampled(LW, 12, lambda t: [6, 5 * math.sin(TAU * t / LW), 0])},
        "head": {"rotation": sampled(LW, 12, lambda t: [-4, -4 * math.sin(TAU * t / LW), 0])},
        "left_wing": {"rotation": sampled(LW, 6, lambda t: [0, -35, 0])},
        "right_wing": {"rotation": sampled(LW, 6, lambda t: [0, 35, 0])},
        "tail": {"rotation": sampled(LW, 12, lambda t: [-10, 22 * math.sin(TAU * t / LW), 0])},
        "tail2": {"rotation": sampled(LW, 12, lambda t: [6, 18 * math.sin(TAU * t / LW - 1), 0])},
    }}
    attack = {"loop": False, "animation_length": 0.6, "bones": {
        "right_arm": {"rotation": {"0.0": [0, 0, 6], "0.2": [-150, -20, 20], "0.35": [-20, 25, 0], "0.6": [0, 0, 6]}},
        "right_forearm": {"rotation": {"0.0": [-12, 0, 0], "0.2": [-40, 0, 0], "0.35": [0, 0, 0], "0.6": [-12, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.2": [-8, 20, 0], "0.35": [14, -20, 0], "0.6": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.2": [-10, 0, 0], "0.35": [10, 0, 0], "0.6": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.25": [30, 0, 0], "0.5": [0, 0, 0]}},
    }}
    slam = {"loop": False, "animation_length": 1.0, "bones": {
        "left_arm": {"rotation": {"0.0": [0, 0, -6], "0.4": [-170, 0, -25], "0.6": [-30, 0, -10], "1.0": [0, 0, -6]}},
        "right_arm": {"rotation": {"0.0": [0, 0, 6], "0.4": [-170, 0, 25], "0.6": [-30, 0, 10], "1.0": [0, 0, 6]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.4": [-18, 0, 0], "0.6": [30, 0, 0], "0.8": [25, 0, 0], "1.0": [0, 0, 0]}},
        "waist": {"position": {"0.0": [0, 0, 0], "0.4": [0, 1, 0], "0.6": [0, -4, 0], "0.85": [0, -3, 0], "1.0": [0, 0, 0]}},
        "left_leg": {"rotation": {"0.0": [0, 0, 0], "0.6": [-25, 0, -8], "1.0": [0, 0, 0]}},
        "right_leg": {"rotation": {"0.0": [0, 0, 0], "0.6": [-25, 0, 8], "1.0": [0, 0, 0]}},
        "left_shin": {"rotation": {"0.0": [0, 0, 0], "0.6": [40, 0, 0], "1.0": [0, 0, 0]}},
        "right_shin": {"rotation": {"0.0": [0, 0, 0], "0.6": [40, 0, 0], "1.0": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.4": [35, 0, 0], "0.7": [10, 0, 0], "1.0": [0, 0, 0]}},
        "left_wing": {"rotation": {"0.0": [0, -25, 0], "0.4": [0, 20, 0], "0.6": [0, -40, 0], "1.0": [0, -25, 0]}},
        "right_wing": {"rotation": {"0.0": [0, 25, 0], "0.4": [0, -20, 0], "0.6": [0, 40, 0], "1.0": [0, 25, 0]}},
    }}
    volley = {"loop": False, "animation_length": 0.9, "bones": {
        "head": {"rotation": {"0.0": [0, 0, 0], "0.2": [-25, 0, 0], "0.35": [15, 0, 0], "0.75": [12, 0, 0], "0.9": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "0.2": [10, 0, 0], "0.35": [40, 0, 0], "0.75": [35, 0, 0], "0.9": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.2": [-12, 0, 0], "0.35": [10, 0, 0], "0.9": [0, 0, 0]}},
        "left_arm": {"rotation": {"0.0": [0, 0, -6], "0.3": [-20, 0, -45], "0.75": [-20, 0, -45], "0.9": [0, 0, -6]}},
        "right_arm": {"rotation": {"0.0": [0, 0, 6], "0.3": [-20, 0, 45], "0.75": [-20, 0, 45], "0.9": [0, 0, 6]}},
        "left_wing": {"rotation": {"0.0": [0, -25, 0], "0.3": [0, 30, 0], "0.9": [0, -25, 0]}},
        "right_wing": {"rotation": {"0.0": [0, 25, 0], "0.3": [0, -30, 0], "0.9": [0, 25, 0]}},
    }}
    emerge = {"loop": False, "animation_length": 1.5, "bones": {
        "root": {"position": {"0.0": [0, -46, 0], "0.9": [0, -6, 0], "1.2": [0, 0, 0], "1.5": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [35, 0, 0], "0.9": [20, 0, 0], "1.25": [-15, 0, 0], "1.5": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [30, 0, 0], "1.0": [10, 0, 0], "1.25": [-30, 0, 0], "1.5": [0, 0, 0]}},
        "jaw": {"rotation": {"0.0": [0, 0, 0], "1.1": [0, 0, 0], "1.25": [40, 0, 0], "1.5": [0, 0, 0]}},
        "left_arm": {"rotation": {"0.0": [-170, 0, -10], "0.9": [-120, 0, -30], "1.25": [-40, 0, -60], "1.5": [0, 0, -6]}},
        "right_arm": {"rotation": {"0.0": [-170, 0, 10], "0.9": [-120, 0, 30], "1.25": [-40, 0, 60], "1.5": [0, 0, 6]}},
        "left_wing": {"rotation": {"0.0": [0, -80, 0], "1.0": [0, -70, 0], "1.3": [0, 25, 0], "1.5": [0, -25, 0]}},
        "right_wing": {"rotation": {"0.0": [0, 80, 0], "1.0": [0, 70, 0], "1.3": [0, -25, 0], "1.5": [0, 25, 0]}},
    }}
    write_anims("summoned_demon", {"idle": idle, "walk": walk, "attack": attack, "slam": slam, "volley": volley, "emerge": emerge})


# =====================================================================================
#  SERVO MORTO-VIVO (undead_servant) 64x64 (layout de skin de jogador)
# =====================================================================================
SERVANT_BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "waist", "parent": "root", "pivot": [0, 12, 0],
     "cubes": [cube([-4.5, 5, -2.5], [9, 8, 5], [16, 32])]},
    {"name": "body", "parent": "waist", "pivot": [0, 24, 0],
     "cubes": [cube([-4, 12, -2], [8, 12, 4], [16, 16])]},
    {"name": "cloak", "parent": "body", "pivot": [0, 24, 2.2], "rotation": [6, 0, 0],
     "cubes": [cube([-4, 7, 2.2], [8, 17, 0], [0, 32])]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0],
     "cubes": [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]},
    {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0],
     "cubes": [cube([-8, 12, -2], [4, 12, 4], [40, 16])]},
    {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0],
     "cubes": [cube([4, 12, -2], [4, 12, 4], [32, 48])]},
    {"name": "right_leg", "parent": "waist", "pivot": [-1.9, 12, 0],
     "cubes": [cube([-3.9, 0, -2], [4, 12, 4], [0, 16])]},
    {"name": "left_leg", "parent": "waist", "pivot": [1.9, 12, 0],
     "cubes": [cube([-0.1, 0, -2], [4, 12, 4], [16, 48])]},
]


def servant_textures():
    c = Canvas(64, 64)
    g = Canvas(64, 64)
    skin = hex_rgba("7A8C6E")
    skin_lt = hex_rgba("9AAB8A")
    skin_dk = hex_rgba("5E6E55")
    rot = hex_rgba("4A5A3F")
    bone = hex_rgba("D8D0B8")
    robe = hex_rgba("2C4F4E")
    robe_dk = hex_rgba("142626")
    robe_lt = hex_rgba("3D6664")
    trim = hex_rgba("3FE0D0")
    eye = hex_rgba("7FFFF0")
    mouth = hex_rgba("1E1A16")

    # Cabeca: pele podre, olho de alma, mandibula com dentes a mostra
    hd = paint_box(c, 0, 0, 8, 8, 8, skin)
    for r in side_faces(hd) + [hd["top"]]:
        x, y, w, h = r
        for (i, j) in ((1, 2), (5, 5), (6, 1), (2, 6), (4, 3)):
            if i < w and j < h:
                c.set(x + i, y + j, rot)
    x, y, w, h = hd["front"]
    for i in range(1, 7):
        c.set(x + i, y + 2, skin_dk)  # sobrancelha
    for (i, j) in ((1, 3), (2, 3), (5, 3), (6, 3)):
        c.set(x + i, y + j, hex_rgba("101818"))  # orbitas fundas
    c.set(x + 2, y + 3, eye); c.set(x + 5, y + 3, eye)
    g.set(x + 2, y + 3, eye); g.set(x + 5, y + 3, eye)
    c.set(x + 3, y + 4, skin_dk); c.set(x + 4, y + 4, skin_dk)  # nariz caido
    for i in range(2, 6):
        c.set(x + i, y + 6, mouth)
    for i in (2, 3, 4, 5):
        c.set(x + i, y + 5 + (i % 2), bone if i % 2 else mouth)
    c.set(x + 1, y + 6, skin_lt); c.set(x + 6, y + 6, skin_lt)
    # Capuz (camada inflada): fechado no topo/lados/atras, aberto no rosto
    hood = box_faces(32, 0, 8, 8, 8)
    vgrad(c, hood["top"], robe_lt, robe)
    for key in ("right", "left", "back"):
        xx, yy, ww, hh = hood[key]
        for j in range(hh):
            for i in range(ww):
                if key != "back" and j >= hh - 2 and (i + j) % 2 == 0:
                    continue  # barra esfarrapada
                c.set(xx + i, yy + j, mix(robe, robe_dk, j / (hh - 1)))
    xx, yy, ww, hh = hood["front"]
    for i in range(ww):
        c.set(xx + i, yy, robe)
        c.set(xx + i, yy + 1, robe_dk)
    for j in range(hh):
        c.set(xx, yy + j, robe_dk); c.set(xx + ww - 1, yy + j, robe_dk)
    for i in range(1, ww - 1):
        c.set(xx + i, yy + 1, trim if i % 2 else robe_dk)
        if i % 2:
            g.set(xx + i, yy + 1, darken(trim, 0.3))

    # Tronco: tunica rasgada mostrando costelas
    bd = paint_box(c, 16, 16, 8, 12, 4, robe, grad=robe_dk)
    x, y, w, h = bd["front"]
    for j in range(2, 8):
        for i in range(2, 6):
            c.set(x + i, y + j, skin_dk)
    for j in (3, 5, 7):
        for i in range(2, 6):
            c.set(x + i, y + j, bone)  # costelas
    for j in range(2, 9):
        c.set(x + 1, y + j, robe_lt); c.set(x + 6, y + j, robe_lt)  # bordas do rasgo
    for i in range(w):
        c.set(x + i, y + 10, hex_rgba("3A2A1A"))  # corda na cintura
    c.set(x + 3, y + 10, trim); g.set(x + 3, y + 10, darken(trim, 0.3))
    # Saia do manto 9x8x5 (rasgada embaixo)
    sk = box_faces(16, 32, 9, 8, 5)
    for key, r in sk.items():
        xx, yy, ww, hh = r
        for j in range(hh):
            for i in range(ww):
                if key in ("top", "bottom"):
                    c.set(xx + i, yy + j, robe_dk)
                    continue
                if j >= hh - 2 and (i * 3 + j) % 4 == 0:
                    continue
                col = mix(robe, robe_dk, j / (hh - 1))
                if j == 0:
                    col = trim if key == "front" and i % 3 == 1 else robe_lt
                c.set(xx + i, yy + j, col)
    fill(c, sk["top"], T)  # o topo some dentro do tronco
    # Capa (plano 8x17) atras, rasgada
    for (x0, mirror) in ((0, False), (8, True)):
        for j in range(17):
            for i in range(8):
                ii = 7 - i if mirror else i
                tear = (ii * 5 + 3) % 4
                if j >= 17 - tear:
                    continue
                col = mix(robe, robe_dk, j / 16.0)
                if ii in (0, 7):
                    col = darken(robe_dk, 0.2)
                c.set(x0 + i, 32 + j, (col[0], col[1], col[2], 235))
    # Bracos: manga ate o cotovelo, antebraco podre, dedos ossudos
    for (u, v) in ((40, 16), (32, 48)):
        a = paint_box(c, u, v, 4, 12, 4, skin)
        for r in side_faces(a):
            xx, yy, ww, hh = r
            for j in range(0, 5):
                for i in range(ww):
                    if j == 4 and (i % 2 == 0):
                        continue
                    c.set(xx + i, yy + j, robe if j < 4 else robe_dk)
            c.set(xx + 1, yy + 7, rot); c.set(xx + 2, yy + 9, rot)
            for i in range(ww):
                c.set(xx + i, yy + hh - 1, bone if i % 2 == 0 else skin_dk)
        fill(c, a["bottom"], bone)
        fill(c, a["top"], robe)
    # Pernas: calca rasgada e pes descalcos
    for (u, v) in ((0, 16), (16, 48)):
        lg = paint_box(c, u, v, 4, 12, 4, hex_rgba("2A2A24"), grad=hex_rgba("1E1E1A"))
        for r in side_faces(lg):
            xx, yy, ww, hh = r
            for j in range(8, hh):
                for i in range(ww):
                    c.set(xx + i, yy + j, skin_dk if j < hh - 1 else skin)
            c.set(xx + 1, yy + 9, bone)
        fill(c, lg["bottom"], skin_dk)
    c.noise(0.06, seed=31)
    # olhos por cima do ruido
    fx, fy, _, _ = hd["front"]
    c.set(fx + 2, fy + 3, eye); c.set(fx + 5, fy + 3, eye)
    c.save(os.path.join(A, "textures/entity/undead_servant.png"))
    g.save(os.path.join(A, "textures/entity/undead_servant_glowmask.png"))


def servant_anims():
    LI = 2.5
    idle = {"loop": True, "animation_length": LI, "bones": {
        "right_arm": {"rotation": sampled(LI, 10, lambda t: [-80 + 5 * math.sin(TAU * t / LI), 4, 3 * math.sin(TAU * t / LI)])},
        "left_arm": {"rotation": sampled(LI, 10, lambda t: [-80 - 5 * math.sin(TAU * t / LI), -4, -3 * math.sin(TAU * t / LI)])},
        "head": {"rotation": sampled(LI, 10, lambda t: [6, 0, 8 + 4 * math.sin(TAU * t / LI)])},
        "body": {"rotation": sampled(LI, 10, lambda t: [5, 0, 2 * math.sin(TAU * t / LI)])},
        "cloak": {"rotation": sampled(LI, 10, lambda t: [3 * math.sin(TAU * t / LI + 0.5), 0, 0])},
    }}
    LW = 1.3
    walk = {"loop": True, "animation_length": LW, "bones": {
        "right_leg": {"rotation": sampled(LW, 10, lambda t: [28 * math.sin(TAU * t / LW), 0, 0])},
        "left_leg": {"rotation": sampled(LW, 10, lambda t: [-28 * math.sin(TAU * t / LW), 0, 0])},
        "right_arm": {"rotation": sampled(LW, 10, lambda t: [-82 + 8 * math.sin(TAU * t / LW), 4, 0])},
        "left_arm": {"rotation": sampled(LW, 10, lambda t: [-82 - 8 * math.sin(TAU * t / LW), -4, 0])},
        "body": {"rotation": sampled(LW, 10, lambda t: [8, 0, 5 * math.sin(TAU * t / LW)])},
        "waist": {"position": sampled(LW, 10, lambda t: [0, 0.5 * abs(math.sin(TAU * t / LW)), 0])},
        "head": {"rotation": sampled(LW, 10, lambda t: [4, 0, 10 + 6 * math.sin(TAU * t / LW)])},
        "cloak": {"rotation": sampled(LW, 10, lambda t: [16 + 6 * math.sin(2 * TAU * t / LW), 0, 0])},
    }}
    attack = {"loop": False, "animation_length": 0.5, "bones": {
        "right_arm": {"rotation": {"0.0": [-80, 0, 0], "0.15": [-140, 0, 10], "0.3": [-40, 0, -5], "0.5": [-80, 0, 0]}},
        "left_arm": {"rotation": {"0.0": [-80, 0, 0], "0.15": [-140, 0, -10], "0.3": [-40, 0, 5], "0.5": [-80, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.15": [-6, 0, 0], "0.3": [14, 0, 0], "0.5": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [12, 0, 0], "0.5": [0, 0, 0]}},
    }}
    rise = {"loop": False, "animation_length": 1.5, "bones": {
        "root": {"position": {"0.0": [0, -30, 0], "0.6": [0, -16, 0], "1.1": [0, -2, 0], "1.3": [0, 0, 0], "1.5": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [-35, 0, 10], "0.7": [-25, 0, -8], "1.2": [10, 0, 4], "1.5": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [-40, 0, 0], "0.8": [-20, 0, 15], "1.2": [15, 0, 10], "1.5": [0, 0, 0]}},
        "right_arm": {"rotation": {"0.0": [-175, 0, 10], "0.5": [-160, 0, -10], "1.0": [-120, 0, 15], "1.5": [-80, 0, 0]}},
        "left_arm": {"rotation": {"0.0": [-175, 0, -10], "0.6": [-150, 0, 10], "1.1": [-110, 0, -15], "1.5": [-80, 0, 0]}},
    }}
    write_anims("undead_servant", {"idle": idle, "walk": walk, "attack": attack, "rise": rise})


# =====================================================================================
#  GUERREIRO DE OSSOS (bone_warrior) 64x64
# =====================================================================================
WARRIOR_BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "waist", "parent": "root", "pivot": [0, 12, 0],
     "cubes": [cube([-4, 10, -2.5], [8, 3, 5], [0, 48])]},
    {"name": "body", "parent": "waist", "pivot": [0, 24, 0],
     "cubes": [cube([-4, 12, -2], [8, 12, 4], [16, 16]),
               cube([3.5, 21, -2.5], [4, 3, 5], [28, 49]),
               cube([-7.5, 21, -2.5], [4, 3, 5], [46, 49])]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0],
     "cubes": [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.6),
               cube([4.5, 29, -0.5], [2, 1, 1], [0, 56]), cube([5.5, 30, -0.5], [1, 3, 1], [6, 56]),
               cube([-6.5, 29, -0.5], [2, 1, 1], [10, 56]), cube([-6.5, 30, -0.5], [1, 3, 1], [16, 56])]},
    {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0],
     "cubes": [cube([-6, 12, -1], [2, 12, 2], [40, 16])]},
    {"name": "sword", "parent": "right_arm", "pivot": [-5, 11.5, -1],
     "cubes": [cube([-5.5, 11, -2.5], [1, 1, 3], [0, 32]), cube([-5.5, 11, 0.5], [1, 1, 1], [0, 36]),
               cube([-6.5, 10.5, -3.5], [3, 2, 1], [8, 32]), cube([-5.5, 10.5, -13.5], [1, 2, 10], [16, 32])]},
    {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0],
     "cubes": [cube([4, 12, -1], [2, 12, 2], [48, 16])]},
    {"name": "shield", "parent": "left_arm", "pivot": [6.5, 15.5, 0],
     "cubes": [cube([6, 11, -4], [1, 9, 8], [40, 32]), cube([7, 14.5, -1], [1, 2, 2], [58, 32])]},
    {"name": "right_leg", "parent": "waist", "pivot": [-2, 12, 0],
     "cubes": [cube([-3, 0, -1], [2, 12, 2], [0, 16])]},
    {"name": "left_leg", "parent": "waist", "pivot": [2, 12, 0],
     "cubes": [cube([1, 0, -1], [2, 12, 2], [8, 16])]},
]


def warrior_textures():
    c = Canvas(64, 64)
    g = Canvas(64, 64)
    bone = hex_rgba("E8E0C8")
    bone_md = hex_rgba("C9BFA3")
    bone_dk = hex_rgba("A39878")
    gap = hex_rgba("3A3228")
    iron = hex_rgba("4A4E52")
    iron_lt = hex_rgba("6B7075")
    iron_dk = hex_rgba("2E3236")
    rust = hex_rgba("7A4A2A")
    eye = hex_rgba("6FF7FF")
    leather = hex_rgba("5A3A22")
    cloth = hex_rgba("2F3A3A")

    # Cranio 8x8x8
    sk = paint_box(c, 0, 0, 8, 8, 8, bone)
    x, y, w, h = sk["front"]
    for (i, j) in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)):
        c.set(x + i, y + j, gap)
    c.set(x + 2, y + 4, eye); c.set(x + 5, y + 4, eye)
    g.set(x + 2, y + 4, eye); g.set(x + 5, y + 4, eye)
    c.set(x + 3, y + 5, gap); c.set(x + 4, y + 5, gap)  # nariz
    for i in range(1, 7):
        c.set(x + i, y + 7, gap if i % 2 else bone_md)  # dentes
        c.set(x + i, y + 6, bone_md)
    for r in (sk["right"], sk["left"], sk["back"]):
        xx, yy, ww, hh = r
        c.set(xx + 2, yy + 3, bone_dk); c.set(xx + 3, yy + 4, bone_dk)
    # Elmo (camada inflada): aberto no rosto, com crista
    hm = box_faces(32, 0, 8, 8, 8)
    vgrad(c, hm["top"], iron_lt, iron)
    xx, yy, ww, hh = hm["top"]
    for j in range(hh):
        c.set(xx + 3, yy + j, iron_dk); c.set(xx + 4, yy + j, iron_lt)  # crista
    for key in ("right", "left", "back"):
        xx, yy, ww, hh = hm[key]
        for j in range(hh - 2):
            for i in range(ww):
                col = mix(iron, iron_dk, j / (hh - 1))
                if (i * 7 + j * 3) % 11 == 0:
                    col = rust
                c.set(xx + i, yy + j, col)
        for i in range(ww):
            c.set(xx + i, yy + hh - 3, iron_lt)  # aba
    xx, yy, ww, hh = hm["front"]
    for i in range(ww):
        c.set(xx + i, yy, iron); c.set(xx + i, yy + 1, iron_lt)
    for j in range(2, 6):
        c.set(xx + 3, yy + j, iron_dk); c.set(xx + 4, yy + j, iron)  # protetor de nariz
    for j in range(hh - 2):
        c.set(xx, yy + j, iron_dk); c.set(xx + ww - 1, yy + j, iron_dk)
    # Chifres do elmo
    for u in (0, 10):
        paint_box(c, u, 56, 2, 1, 1, bone_md)
    for u in (6, 16):
        p = paint_box(c, u, 56, 1, 3, 1, bone)
        fill(c, p["top"], lighten(bone, 0.4))

    # Caixa toracica 8x12x4 (vazada entre as costelas)
    bd = box_faces(16, 16, 8, 12, 4)
    for key, r in bd.items():
        xx, yy, ww, hh = r
        if key in ("top", "bottom"):
            fill(c, r, bone_md)
            continue
        for j in range(hh):
            for i in range(ww):
                spine = key in ("front", "back") and i in (ww // 2 - 1, ww // 2)
                rib = j % 2 == 1 and j < 9
                pelvis = j >= 10
                if spine:
                    col = bone_md
                elif rib:
                    col = bone if i not in (0, ww - 1) else bone_dk
                elif pelvis:
                    col = bone_dk
                else:
                    col = T
                if col != T:
                    c.set(xx + i, yy + j, col)
    # Ombreiras de ferro
    for u in (28, 46):
        pd = paint_box(c, u, 49, 4, 3, 5, iron)
        for r in side_faces(pd):
            xx, yy, ww, hh = r
            for i in range(ww):
                c.set(xx + i, yy, iron_lt)
                c.set(xx + i, yy + hh - 1, iron_dk)
        fill(c, pd["top"], iron_lt)
        xx, yy, ww, hh = pd["top"]
        c.set(xx + ww // 2, yy + hh // 2, bone)  # rebite de osso
    # Cinto/saiote 8x3x5
    pv = paint_box(c, 0, 48, 8, 3, 5, cloth)
    for r in side_faces(pv):
        xx, yy, ww, hh = r
        for i in range(ww):
            c.set(xx + i, yy, leather)
            if i % 3 == 2:
                c.set(xx + i, yy + hh - 1, T)
    fx, fy, fw, fh = pv["front"]
    c.set(fx + fw // 2, fy, iron_lt); c.set(fx + fw // 2 - 1, fy, iron_lt)  # fivela
    # Bracos e pernas finos: ossos com juntas
    for (u, v) in ((40, 16), (48, 16), (0, 16), (8, 16)):
        lb = paint_box(c, u, v, 2, 12, 2, bone_md)
        for r in side_faces(lb):
            xx, yy, ww, hh = r
            for j in (0, 5, 6, hh - 1):
                for i in range(ww):
                    c.set(xx + i, yy + j, bone)
            c.set(xx, yy + 3, bone_dk)
        fill(c, lb["bottom"], bone_dk)
    # Espada: cabo de couro, pomo e guarda de ferro, lamina de osso afiada
    paint_box(c, 0, 32, 1, 1, 3, leather)
    paint_box(c, 0, 36, 1, 1, 1, iron_lt)
    gd = paint_box(c, 8, 32, 3, 2, 1, iron)
    fill(c, gd["front"], iron_lt)
    bl = box_faces(16, 32, 1, 2, 10)
    for key, r in bl.items():
        xx, yy, ww, hh = r
        for j in range(hh):
            for i in range(ww):
                if key in ("right", "left"):
                    # lado largo da lamina: fio claro em cima, sulco no meio
                    col = bone if j == 0 else (bone_md if j == 1 else bone_dk)
                    if key == "right":
                        ii = ww - 1 - i
                    else:
                        ii = i
                    if ii == 0:
                        col = lighten(bone, 0.4)  # ponta
                    c.set(xx + i, yy + j, col)
                else:
                    c.set(xx + i, yy + j, bone_md if key != "bottom" else bone_dk)
    # Escudo 1x9x8: placa de osso com borda de ferro e caveira
    sh = box_faces(40, 32, 1, 9, 8)
    for key, r in sh.items():
        xx, yy, ww, hh = r
        for j in range(hh):
            for i in range(ww):
                c.set(xx + i, yy + j, iron)
    # face de fora do escudo = lado "left" (+X)
    xx, yy, ww, hh = sh["left"]
    for j in range(hh):
        for i in range(ww):
            edge = i in (0, ww - 1) or j in (0, hh - 1)
            if edge:
                col = iron_lt if (i + j) % 2 else iron
            else:
                col = mix(bone, bone_md, j / (hh - 1))
                if (i + j * 2) % 5 == 0:
                    col = bone_dk
            c.set(xx + i, yy + j, col)
    skull = [(2, 2), (3, 2), (4, 2), (5, 2), (1, 3), (2, 3), (3, 3), (4, 3), (5, 3), (6, 3), (1, 4), (6, 4), (2, 5), (3, 5), (4, 5),
             (5, 5), (3, 6), (4, 6)]
    for (i, j) in skull:
        c.set(xx + i, yy + j, gap)
    for (i, j) in ((2, 4), (5, 4)):
        c.set(xx + i, yy + j, eye)
        g.set(xx + i, yy + j, darken(eye, 0.35))
    # lado de dentro (contra o braco) = "right": tabuas e alca de couro
    xx, yy, ww, hh = sh["right"]
    for j in range(hh):
        for i in range(ww):
            c.set(xx + i, yy + j, bone_dk if (j % 3) else gap)
    for j in range(2, hh - 2):
        c.set(xx + ww // 2, yy + j, leather)
    paint_box(c, 58, 32, 1, 2, 2, iron_lt)
    c.noise(0.04, seed=41)
    c.save(os.path.join(A, "textures/entity/bone_warrior.png"))
    g.save(os.path.join(A, "textures/entity/bone_warrior_glowmask.png"))


def warrior_anims():
    LI = 2.0
    idle = {"loop": True, "animation_length": LI, "bones": {
        "right_arm": {"rotation": sampled(LI, 8, lambda t: [-25 + 3 * math.sin(TAU * t / LI), 0, 4])},
        "sword": {"rotation": sampled(LI, 8, lambda t: [25, 0, 0])},
        "left_arm": {"rotation": sampled(LI, 8, lambda t: [-35 + 3 * math.sin(TAU * t / LI), -15, -6])},
        "head": {"rotation": sampled(LI, 8, lambda t: [0, 6 * math.sin(TAU * t / LI * 0.5), 2 * math.sin(TAU * t / LI)])},
        "body": {"rotation": sampled(LI, 8, lambda t: [2, 0, 1.5 * math.sin(TAU * t / LI)])},
        "right_leg": {"rotation": sampled(LI, 4, lambda t: [8, 0, 3])},
        "left_leg": {"rotation": sampled(LI, 4, lambda t: [-8, 0, -3])},
    }}
    LW = 0.9
    walk = {"loop": True, "animation_length": LW, "bones": {
        "right_leg": {"rotation": sampled(LW, 10, lambda t: [32 * math.sin(TAU * t / LW), 0, 0])},
        "left_leg": {"rotation": sampled(LW, 10, lambda t: [-32 * math.sin(TAU * t / LW), 0, 0])},
        "right_arm": {"rotation": sampled(LW, 10, lambda t: [-25 - 12 * math.sin(TAU * t / LW), 0, 4])},
        "sword": {"rotation": sampled(LW, 4, lambda t: [25, 0, 0])},
        "left_arm": {"rotation": sampled(LW, 10, lambda t: [-35 + 8 * math.sin(TAU * t / LW), -15, -6])},
        "waist": {"position": sampled(LW, 10, lambda t: [0, 0.5 * abs(math.sin(TAU * t / LW)), 0])},
        "body": {"rotation": sampled(LW, 10, lambda t: [6, 6 * math.sin(TAU * t / LW), 0])},
    }}
    attack = {"loop": False, "animation_length": 0.45, "bones": {
        "right_arm": {"rotation": {"0.0": [-25, 0, 4], "0.15": [-160, -20, 15], "0.28": [-10, 30, -5], "0.45": [-25, 0, 4]}},
        "sword": {"rotation": {"0.0": [25, 0, 0], "0.15": [40, 0, 0], "0.28": [0, 0, 0], "0.45": [25, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.15": [-5, 20, 0], "0.28": [10, -25, 0], "0.45": [0, 0, 0]}},
    }}
    block = {"loop": False, "animation_length": 0.5, "bones": {
        "left_arm": {"rotation": {"0.0": [-35, -15, -6], "0.1": [-85, -40, 0], "0.35": [-85, -40, 0], "0.5": [-35, -15, -6]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [-6, -10, 0], "0.35": [-6, -10, 0], "0.5": [0, 0, 0]}},
        "waist": {"position": {"0.0": [0, 0, 0], "0.1": [0, 0, 0.8], "0.35": [0, 0, 0.8], "0.5": [0, 0, 0]}},
    }}
    assemble = {"loop": False, "animation_length": 1.0, "bones": {
        "right_leg": {"scale": {"0.0": [0, 0, 0], "0.25": [1, 1, 1]}},
        "left_leg": {"scale": {"0.0": [0, 0, 0], "0.1": [0, 0, 0], "0.35": [1, 1, 1]}},
        "waist": {"position": {"0.0": [0, -6, 0], "0.5": [0, -2, 0], "0.8": [0, 0, 0]}},
        "body": {"scale": {"0.0": [0, 0, 0], "0.25": [0, 0, 0], "0.5": [1, 1, 1]},
                 "rotation": {"0.0": [20, 0, 0], "0.5": [20, 0, 0], "0.8": [-5, 0, 0], "1.0": [0, 0, 0]}},
        "head": {"scale": {"0.0": [0, 0, 0], "0.55": [0, 0, 0], "0.75": [1.15, 1.15, 1.15], "0.85": [1, 1, 1]},
                 "rotation": {"0.0": [0, 0, 0], "0.75": [-25, 0, 0], "1.0": [0, 0, 0]}},
        "right_arm": {"scale": {"0.0": [0, 0, 0], "0.4": [0, 0, 0], "0.65": [1, 1, 1]},
                      "rotation": {"0.0": [-25, 0, 4], "0.65": [-140, 0, 10], "0.9": [-25, 0, 4]}},
        "left_arm": {"scale": {"0.0": [0, 0, 0], "0.45": [0, 0, 0], "0.7": [1, 1, 1]}},
    }}
    write_anims("bone_warrior", {"idle": idle, "walk": walk, "attack": attack, "block": block, "assemble": assemble})


# =====================================================================================
#  Item: Lamina Sombria (16x16, segurada na mao)
# =====================================================================================
def shadow_blade():
    c = Canvas(16, 16)
    edge = hex_rgba("C89BFF")
    glow = hex_rgba("9A4FE0")
    core = hex_rgba("2A1240")
    deep = hex_rgba("140820")
    hilt = hex_rgba("3A2A30")
    hilt_lt = hex_rgba("6A5060")
    gem = hex_rgba("FF3A5A")
    # lamina diagonal: da ponta (15,0) ate (5,10)
    for k in range(0, 11):
        x, y = 15 - k, k
        c.set(x, y, core)
        c.set(x - 1, y, deep)
        c.set(x, y + 1, glow if k % 3 else edge)
        if k > 1:
            c.set(x - 1, y - 1, edge if k % 2 else glow)
    c.set(15, 0, edge); c.set(14, 0, glow)
    # guarda curva com espinhos
    for (x, y) in ((2, 9), (3, 9), (4, 10), (5, 11), (6, 12), (7, 12), (3, 8), (8, 13)):
        c.set(x, y, hilt_lt)
    c.set(5, 10, gem); c.set(4, 11, hilt)
    # cabo e pomo
    for k in range(0, 4):
        c.set(3 - k + 1, 12 + k - 1, hilt)
    c.set(1, 15, hilt_lt); c.set(0, 15, glow); c.set(1, 14, hilt)
    c.outline((10, 4, 18, 255))
    path = os.path.join(A, "textures/item/shadow_blade.png")
    c.save(path)
    item_model(A, "shadow_blade", parent="minecraft:item/handheld")


# =====================================================================================
#  Icones de efeito 18x18
# =====================================================================================
def effect_icons():
    out = os.path.join(A, "textures/mob_effect")
    # Escudo de Ossos: escudo redondo com dois ossos cruzados e caveirinha no centro
    c = Canvas(18, 18)
    c.circle(9, 9, 7.6, hex_rgba("5A5048"))
    c.circle(9, 9, 6.4, hex_rgba("A39878"))
    c.shade_sphere(9, 9, 7.6, light=0.3, dark=0.4)
    bone_c, bone_s = hex_rgba("F0E8D0"), hex_rgba("C9BFA3")
    for (x0, y0, x1, y1) in ((4, 4, 13, 13), (13, 4, 4, 13)):
        c.line(x0, y0, x1, y1, bone_c)
        for (x, y) in ((x0, y0), (x1, y1)):
            c.set(x - 1, y, bone_c); c.set(x, y - 1, bone_c); c.set(x + 1, y, bone_s); c.set(x, y + 1, bone_s)
    c.circle(9, 9, 2.4, hex_rgba("E8E0C8"))
    c.set(8, 9, hex_rgba("3FE0D0")); c.set(10, 9, hex_rgba("3FE0D0")); c.set(9, 11, hex_rgba("3A3228"))
    c.outline(hex_rgba("2A2420"))
    c.save(os.path.join(out, "bone_shield.png"))
    # Contrato de Morte: pergaminho com selo de sangue
    c = Canvas(18, 18)
    c.rect(4, 3, 13, 14, hex_rgba("E6D3A8"))
    c.rect(3, 2, 14, 3, hex_rgba("BFA47A"))
    c.rect(3, 14, 14, 15, hex_rgba("BFA47A"))
    for y in (5, 7, 9):
        c.line(6, y, 11, y, hex_rgba("6A5038"))
    c.circle(10.5, 11.5, 2.6, hex_rgba("9E1B22"))
    c.shade_sphere(10.5, 11.5, 2.6)
    c.set(10, 11, hex_rgba("FF7A1A"))
    c.outline(hex_rgba("2A1810"))
    c.save(os.path.join(out, "death_contract.png"))
    # Asas Demoniacas: par de asas de morcego abertas para cima, saindo do centro
    c = Canvas(18, 18)
    for side in (-1, 1):
        root = (9 + side * 0.5, 10.0)
        tips = [(9 + side * 8.0, 2.0), (9 + side * 7.5, 9.0), (9 + side * 4.5, 14.0)]
        poly = [root] + tips
        for y in range(18):
            for x in range(18):
                if point_in_poly(x + 0.5, y + 0.5, poly):
                    t = abs(x + 0.5 - 9) / 9
                    c.set(x, y, mix(hex_rgba("A0202A"), hex_rgba("4A0A10"), t))
        rx = 9 if side > 0 else 8
        for (tx, ty) in tips:
            c.line(rx, 9, min(17, max(0, int(round(tx)))), int(round(ty)), hex_rgba("2A0A0E"))
        c.set(17 if side > 0 else 0, 1, hex_rgba("E8E0C8"))  # garra na ponta
    c.rect(8, 8, 9, 11, hex_rgba("3A0A0E"))
    c.outline(hex_rgba("1A0406"))
    c.save(os.path.join(out, "demon_wings.png"))
    # Forma de Lich: caveira coroada com olhos de alma
    c = Canvas(18, 18)
    c.circle(9, 9, 5.5, hex_rgba("E8E0C8"))
    c.rect(6, 12, 11, 14, hex_rgba("D8D0B8"))
    c.shade_sphere(9, 9, 5.5, light=0.3, dark=0.35)
    for (x, y) in ((6, 8), (7, 8), (10, 8), (11, 8), (6, 9), (11, 9)):
        c.set(x, y, hex_rgba("101818"))
    c.set(7, 9, hex_rgba("3FE0D0")); c.set(10, 9, hex_rgba("3FE0D0"))
    c.set(8, 11, hex_rgba("3A3228")); c.set(9, 11, hex_rgba("3A3228"))
    for x in range(7, 11, 1):
        c.set(x, 13, hex_rgba("3A3228") if x % 2 else hex_rgba("E8E0C8"))
    for x in (5, 7, 9, 11, 13):
        c.set(x, 2, hex_rgba("3FE0D0"))
    c.rect(5, 3, 13, 4, hex_rgba("2C8F88"))
    c.outline(hex_rgba("102020"))
    c.save(os.path.join(out, "lich_form.png"))


# =====================================================================================
#  Ovos, tags
# =====================================================================================
def eggs_and_tags():
    for e in ("summoned_imp", "summoned_demon", "undead_servant", "bone_warrior"):
        save_json(os.path.join(A, f"models/item/{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    save_json(os.path.join(MERGE, "data/minecraft/tags/entity_type/undead.json"), {
        "replace": False,
        "values": ["isekai_mahou:undead_servant", "isekai_mahou:bone_warrior"]})


def main():
    write_geo("summoned_imp", IMP_BONES, 64, 64, 2, 2)
    imp_textures()
    imp_anims()
    write_geo("summoned_demon", DEMON_BONES, 128, 128, 5, 4)
    demon_textures()
    demon_anims()
    write_geo("undead_servant", SERVANT_BONES, 64, 64, 2, 3)
    servant_textures()
    servant_anims()
    write_geo("bone_warrior", WARRIOR_BONES, 64, 64, 3, 3)
    warrior_textures()
    warrior_anims()
    shadow_blade()
    effect_icons()
    eggs_and_tags()
    print("origins_b: assets gerados")


if __name__ == "__main__":
    main()
