"""
Gera os assets do modulo creatures_c (rode da raiz do projeto: py -I tools/gen/creatures_c.py):
  - modelos GeckoLib (.geo.json), animacoes (.animation.json), texturas e glowmasks das 6 feras
    (dire_wolf, harpy, basilisk, mimic, gargoyle, fairy) — o UV e empacotado automaticamente
  - texturas 16x16 + modelos dos itens (materiais e ovos)
  - loot tables, receitas e biome modifiers (spawns)
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba as H, darken, lighten, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
T = (0, 0, 0, 0)
TAU = 2 * math.pi


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  UV de caixa (Bedrock): retangulos (x, y, w, h) de cada face
#  front = norte (-Z, a "cara"), right = lado -X (direita da criatura), left = +X, top = cima
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


def px_iter(rect):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            yield i, j, x + i, y + j


def surf(c, rect, face, col, rnd, light=0.12, dark=0.2, edge=0.12, noise=0.05, streak=0.0, sdark=0.22, slight=0.0):
    """Material base de uma face: gradiente vertical, borda escurecida, ruido e (opcional) mechas de pelo."""
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    strokes = {}
    if streak:
        for i in range(w):
            if rnd.random() < streak:
                j0 = rnd.randrange(h)
                ln = rnd.randint(2, 4)
                tone = -sdark if rnd.random() < 0.6 else slight or sdark * 0.6
                for j in range(j0, min(h, j0 + ln)):
                    strokes[(i, j)] = tone
    for j in range(h):
        t = j / max(1, h - 1)
        for i in range(w):
            if face == "top":
                p = lighten(col, light)
            elif face == "bottom":
                p = darken(col, dark)
            else:
                p = mix(lighten(col, light * 0.7), darken(col, dark * 0.7), t)
            s = strokes.get((i, j))
            if s is not None:
                p = darken(p, -s) if s < 0 else lighten(p, s)
            if edge and w > 2 and h > 2 and (i in (0, w - 1) or j in (0, h - 1)):
                p = darken(p, edge)
            if noise:
                n = (rnd.random() - 0.5) * 2 * noise
                p = lighten(p, n) if n > 0 else darken(p, -n)
            c.set(x + i, y + j, p)


def std(col, faces=None, seed=1, **kw):
    """Pintor padrao: uma cor (ou cor por face) com o material base."""
    def p(c, g, f, cb):
        rnd = random.Random(seed)
        for k, r in f.items():
            surf(c, r, k, (faces or {}).get(k, col), rnd, **kw)
    return p


def chain(*painters):
    def p(c, g, f, cb):
        for q in painters:
            q(c, g, f, cb)
    return p


def clear(c, rect):
    for i, j, X, Y in px_iter(rect):
        c.set(X, Y, T)


# =====================================================================================
#  Modelo com empacotamento automatico de UV
# =====================================================================================
class Model:
    def __init__(self, ident, tw):
        self.ident = ident
        self.tw = tw
        self.th = 0
        self.bones = []
        self.cubes = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name}
        if parent:
            b["parent"] = parent
        b["pivot"] = [float(v) for v in pivot]
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return name

    def box(self, bone, origin, size, paint, inflate=None):
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "inflate": inflate, "uv": None})

    def pack(self):
        items = sorted(self.cubes, key=lambda cb: (-(cb["size"][2] + cb["size"][1]), -(cb["size"][2] + cb["size"][0])))
        x = y = rowh = 0
        for cb in items:
            w, h, d = cb["size"]
            W, Hh = 2 * (d + w), d + h
            if W > self.tw:
                raise SystemExit(f"{self.ident}: cubo largo demais ({W})")
            if x + W > self.tw:
                x, y, rowh = 0, y + rowh, 0
            cb["uv"] = [x, y]
            x += W
            rowh = max(rowh, Hh)
        used = y + rowh
        self.th = max(32, ((used + 15) // 16) * 16)

    def build(self, vb_w, vb_h, vb_off_y):
        self.pack()
        c = Canvas(self.tw, self.th)
        g = Canvas(self.tw, self.th)
        for cb in self.cubes:
            w, h, d = cb["size"]
            u, v = cb["uv"]
            cb["paint"](c, g, box_faces(u, v, w, h, d), cb)
        bones = []
        for b in self.bones:
            o = dict(b)
            cubes = []
            for cb in self.cubes:
                if cb["bone"] == b["name"]:
                    cu = {"origin": cb["origin"], "size": cb["size"], "uv": cb["uv"]}
                    if cb["inflate"]:
                        cu["inflate"] = cb["inflate"]
                    cubes.append(cu)
            if cubes:
                o["cubes"] = cubes
            bones.append(o)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": vb_w, "visible_bounds_height": vb_h,
                            "visible_bounds_offset": [0, vb_off_y, 0]},
            "bones": bones}]}
        save_json(os.path.join(A, "geo/entity", self.ident + ".geo.json"), geo)
        return c, g


def save_tex(ident, c, g=None):
    c.save(os.path.join(A, "textures/entity", ident + ".png"))
    if g is not None:
        g.save(os.path.join(A, "textures/entity", ident + "_glowmask.png"))


# =====================================================================================
#  Animacoes
# =====================================================================================
def r3(v):
    return [round(x, 2) for x in v]


def sampled(length, steps, fn):
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        out[str(round(t, 4))] = r3(fn(t))
    return out


def keys(d):
    return {str(k): r3(v) for k, v in d.items()}


def pose(v, length=1.0):
    return {"0.0": r3(v), str(length): r3(v)}


def anim(length, bones, loop=True):
    return {"loop": loop, "animation_length": length, "bones": bones}


def save_anims(ident, anims):
    save_json(os.path.join(A, "animations/entity", ident + ".animation.json"), {
        "format_version": "1.8.0",
        "animations": {"animation.%s.%s" % (ident, k): v for k, v in anims.items()}})


def S(t, L, phase=0.0, k=1.0):
    return math.sin(TAU * k * t / L + phase)


# =====================================================================================
#  LOBO ATROZ
# =====================================================================================
def dire_wolf():
    FUR, FUR_D, FUR_L = H("4B4852"), H("2C2932"), H("6E6A77")
    BELLY, MANE, TIP = H("8F8A97"), H("5C5865"), H("B6B1BF")
    SNOUT, NOSE, EYE = H("7A7581"), H("131116"), H("FFB627")
    EAR_IN, CLAW, FANG, MOUTH = H("8A5F6A"), H("1C1A1F"), H("EFEADB"), H("5A1F27")
    LEATHER, LEATHER_L, LEATHER_D, GOLD, COLLAR = H("6B3F1F"), H("8E5B31"), H("4A2A12"), H("E3B341"), H("B3262B")

    def fur(base, belly=None, top=None, seed=1, streak=0.45):
        def p(c, g, f, cb):
            rnd = random.Random(seed)
            for k, r in f.items():
                col = top if (k == "top" and top) else (belly if (k == "bottom" and belly) else base)
                surf(c, r, k, col, rnd, streak=streak, sdark=0.25, slight=0.18, noise=0.06, edge=0.1)
                if belly and k in ("front", "back", "left", "right"):
                    x, y, w, h = r
                    for j in range(max(0, h - 3), h):
                        for i in range(w):
                            c.set(x + i, y + j, mix(c.get(x + i, y + j), belly, 0.25 + 0.2 * (j - (h - 3))))
        return p

    def torso(c, g, f, cb):
        fur(FUR, belly=BELLY, seed=3)(c, g, f, cb)
        x, y, w, h = f["top"]
        rnd = random.Random(5)
        for j in range(h):
            for i in range(3, w - 3):
                c.set(x + i, y + j, darken(FUR_D, rnd.random() * 0.15))

    def mane(c, g, f, cb):
        fur(MANE, belly=FUR_L, seed=7, streak=0.7)(c, g, f, cb)
        rnd = random.Random(8)
        for k in ("front", "left", "right", "back", "top"):
            x, y, w, h = f[k]
            for _ in range(w * h // 5):
                i, j = rnd.randrange(w), rnd.randrange(h)
                c.set(x + i, y + j, mix(TIP, MANE, rnd.random() * 0.4))

    def skull(c, g, f, cb):
        fur(FUR, belly=FUR_L, seed=11, streak=0.3)(c, g, f, cb)
        x, y, w, h = f["front"]
        for (i, j, col) in ((1, 2, FUR_D), (2, 2, FUR_D), (5, 2, FUR_D), (6, 2, FUR_D),
                            (1, 3, EYE), (2, 3, NOSE), (5, 3, NOSE), (6, 3, EYE)):
            c.set(x + i, y + j, col)
        g.set(x + 1, y + 3, EYE)
        g.set(x + 6, y + 3, EYE)
        for i in range(w):
            c.set(x + i, y + h - 1, mix(c.get(x + i, y + h - 1), SNOUT, 0.5))
        # olhos tambem visiveis de lado (perto da frente)
        xr, yr, wr, hr = f["right"]
        c.set(xr + wr - 1, yr + 3, EYE)
        g.set(xr + wr - 1, yr + 3, EYE)
        xl, yl, wl, hl = f["left"]
        c.set(xl, yl + 3, EYE)
        g.set(xl, yl + 3, EYE)

    def snout(c, g, f, cb):
        std(SNOUT, faces={"top": lighten(SNOUT, 0.08)}, seed=13, noise=0.05, edge=0.08)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(1, 4):
            c.set(x + i, y, NOSE)
            c.set(x + i, y + 1, NOSE if i == 2 else darken(SNOUT, 0.3))
        for i in range(w):
            c.set(x + i, y + h - 1, darken(MOUTH, 0.2))
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, darken(MOUTH, 0.1))
        x, y, w, h = f["top"]
        for j in range(h):
            c.set(x + 2, y + j, darken(SNOUT, 0.15))

    def jaw(c, g, f, cb):
        std(darken(SNOUT, 0.1), faces={"bottom": BELLY}, seed=17)(c, g, f, cb)
        x, y, w, h = f["top"]
        for i, j, X, Y in px_iter(f["top"]):
            edge = i in (0, w - 1) or j == h - 1
            c.set(X, Y, FANG if edge and (i + j) % 2 == 0 else (darken(FANG, 0.25) if edge else MOUTH))
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y, FANG if i % 2 == 0 else darken(FANG, 0.3))

    def fang(c, g, f, cb):
        std(FANG, seed=19, edge=0.0, noise=0.02, dark=0.1)(c, g, f, cb)

    def ear(c, g, f, cb):
        std(FUR_D, seed=23)(c, g, f, cb)
        for k in ("front", "back"):
            x, y, w, h = f[k]
            c.set(x, y, T)
            c.set(x + w - 1, y, T)
            if k == "front":
                for j in range(1, h):
                    c.set(x + 1, y + j, EAR_IN)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            c.set(x, y, T)
        x, y, w, h = f["top"]
        c.set(x, y, T)
        c.set(x + w - 1, y, T)

    def tail(c, g, f, cb):
        fur(FUR, belly=FUR_L, seed=29, streak=0.6)(c, g, f, cb)
        rnd = random.Random(30)
        x, y, w, h = f["back"]
        for i, j, X, Y in px_iter(f["back"]):
            c.set(X, Y, mix(TIP, MANE, rnd.random() * 0.3))
        for k, cols in (("right", range(0, 3)), ("left", range(f["left"][2] - 3, f["left"][2]))):
            x, y, w, h = f[k]
            for i in cols:
                for j in range(h):
                    c.set(x + i, y + j, mix(TIP, MANE, rnd.random() * 0.35))
        for k in ("top", "bottom"):
            x, y, w, h = f[k]
            for j in range(3):
                for i in range(w):
                    c.set(x + i, y + j, mix(TIP, MANE, rnd.random() * 0.35))

    def leg(seed):
        def p(c, g, f, cb):
            fur(FUR, seed=seed, streak=0.35)(c, g, f, cb)
            for k in ("front", "back", "left", "right"):
                x, y, w, h = f[k]
                for j in range(h - 2, h):
                    for i in range(w):
                        c.set(x + i, y + j, darken(FUR_D, 0.1 * (j - h + 2)))
            x, y, w, h = f["front"]
            for i in range(w):
                c.set(x + i, y + h - 1, CLAW if i % 2 == 0 else lighten(CLAW, 0.25))
            x, y, w, h = f["bottom"]
            for i, j, X, Y in px_iter(f["bottom"]):
                c.set(X, Y, H("3A3540"))
        return p

    def saddle_seat(c, g, f, cb):
        std(LEATHER, faces={"top": LEATHER_L}, seed=31, noise=0.04)(c, g, f, cb)
        x, y, w, h = f["top"]
        for i, j, X, Y in px_iter(f["top"]):
            if i in (0, w - 1) or j in (0, h - 1):
                c.set(X, Y, GOLD if (i + j) % 2 == 0 else darken(GOLD, 0.3))
            elif i in (1, w - 2) or j in (1, h - 2):
                c.set(X, Y, LEATHER_D)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y, GOLD if i % 2 == 0 else LEATHER_D)

    def saddle_strap(c, g, f, cb):
        std(LEATHER_D, seed=37, noise=0.04)(c, g, f, cb)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 3, GOLD)
            c.set(x + w // 2, y + h - 2, darken(GOLD, 0.35))

    def horn(c, g, f, cb):
        std(LEATHER_L, faces={"top": GOLD}, seed=41)(c, g, f, cb)

    def collar(c, g, f, cb):
        std(COLLAR, seed=43, noise=0.03)(c, g, f, cb)
        x, y, w, h = f["front"]
        mid = w // 2
        c.set(x + mid - 1, y + 1, GOLD)
        c.set(x + mid, y + 1, GOLD)
        c.set(x + mid - 1, y, lighten(GOLD, 0.3))
        c.set(x + mid, y, lighten(GOLD, 0.3))

    m = Model("dire_wolf", 128)
    m.bone("root")
    m.bone("body", "root", (0, 17, 0))
    m.box("body", (-5, 12, -7), (10, 10, 17), torso)
    m.box("body", (-6.5, 11, -12), (13, 13, 8), mane)
    m.bone("saddle", "body", (0, 22, 0))
    m.box("saddle", (-5.5, 21.5, -5), (11, 2, 10), saddle_seat)
    m.box("saddle", (-5.5, 14.5, -1.5), (11, 7, 3), saddle_strap, inflate=0.1)
    m.box("saddle", (-1, 23.5, -5), (2, 2, 2), horn)
    m.bone("collar", "body", (0, 14, -8))
    m.box("collar", (-7, 13, -12.5), (14, 2, 8), collar)
    m.bone("head", "body", (0, 19, -11))
    m.box("head", (-4, 15.5, -18), (8, 8, 7), skull)
    m.box("head", (-2.5, 15.5, -23), (5, 4, 5), snout)
    m.box("head", (1, 23.5, -14), (3, 4, 1), ear)
    m.box("head", (-4, 23.5, -14), (3, 4, 1), ear)
    m.box("head", (-2.5, 13.5, -22.5), (1, 2, 1), fang)
    m.box("head", (1.5, 13.5, -22.5), (1, 2, 1), fang)
    m.bone("jaw", "head", (0, 15.5, -17.5))
    m.box("jaw", (-2, 13.5, -22.5), (4, 2, 5), jaw)
    m.bone("tail", "body", (0, 20.5, 9.5), rotation=(-35, 0, 0))
    m.box("tail", (-2, 18.5, 9.5), (4, 4, 11), tail)
    m.bone("front_left_leg", "root", (3, 14, -8))
    m.box("front_left_leg", (1, 0, -10), (4, 14, 4), leg(51))
    m.bone("front_right_leg", "root", (-3, 14, -8))
    m.box("front_right_leg", (-5, 0, -10), (4, 14, 4), leg(53))
    m.bone("back_left_leg", "root", (3.5, 14, 6))
    m.box("back_left_leg", (1, 7, 3), (5, 8, 6), fur(FUR, seed=55, streak=0.5))
    m.box("back_left_leg", (1.5, 0, 5.5), (4, 7, 3), leg(57))
    m.bone("back_right_leg", "root", (-3.5, 14, 6))
    m.box("back_right_leg", (-6, 7, 3), (5, 8, 6), fur(FUR, seed=59, streak=0.5))
    m.box("back_right_leg", (-5.5, 0, 5.5), (4, 7, 3), leg(61))
    c, g = m.build(4, 3, 1.0)
    save_tex("dire_wolf", c, g)

    # ---------------- animacoes ----------------
    LW, LR = 1.0, 0.55
    walk = anim(LW, {
        "front_left_leg": {"rotation": sampled(LW, 8, lambda t: [28 * S(t, LW), 0, 0])},
        "back_right_leg": {"rotation": sampled(LW, 8, lambda t: [28 * S(t, LW), 0, 0])},
        "front_right_leg": {"rotation": sampled(LW, 8, lambda t: [-28 * S(t, LW), 0, 0])},
        "back_left_leg": {"rotation": sampled(LW, 8, lambda t: [-28 * S(t, LW), 0, 0])},
        "body": {"position": sampled(LW, 8, lambda t: [0, 0.35 * abs(S(t, LW)), 0]),
                 "rotation": sampled(LW, 8, lambda t: [0, 0, 1.5 * S(t, LW)])},
        "head": {"rotation": sampled(LW, 8, lambda t: [3 * S(t, LW, 0, 2), 0, 0])},
        "tail": {"rotation": sampled(LW, 8, lambda t: [0, 12 * S(t, LW), 0])},
    })
    run = anim(LR, {
        "front_left_leg": {"rotation": sampled(LR, 8, lambda t: [-50 * S(t, LR), 0, 0])},
        "front_right_leg": {"rotation": sampled(LR, 8, lambda t: [-50 * S(t, LR, 0.6), 0, 0])},
        "back_left_leg": {"rotation": sampled(LR, 8, lambda t: [50 * S(t, LR, 0.4), 0, 0])},
        "back_right_leg": {"rotation": sampled(LR, 8, lambda t: [50 * S(t, LR, 1.0), 0, 0])},
        "body": {"rotation": sampled(LR, 8, lambda t: [5 * S(t, LR, math.pi / 2), 0, 0]),
                 "position": sampled(LR, 8, lambda t: [0, 1.0 * abs(S(t, LR)), 0])},
        "head": {"rotation": sampled(LR, 8, lambda t: [-5 * S(t, LR, math.pi / 2), 0, 0])},
        "tail": {"rotation": sampled(LR, 8, lambda t: [25 + 8 * S(t, LR), 0, 0])},
    })
    LI = 3.0
    idle = anim(LI, {
        "body": {"position": sampled(LI, 6, lambda t: [0, 0.25 * S(t, LI), 0])},
        "head": {"rotation": sampled(LI, 6, lambda t: [2 * S(t, LI, 1.0), 3 * S(t, LI, 0, 0.5), 0])},
        "tail": {"rotation": sampled(LI, 6, lambda t: [0, 6 * S(t, LI), 0])},
        "jaw": {"rotation": sampled(LI, 6, lambda t: [4 + 4 * S(t, LI, 0, 2), 0, 0])},
    })
    sit = anim(1.0, {
        "root": {"position": pose([0, -3, 0])},
        "body": {"rotation": pose([-25, 0, 0])},
        "head": {"rotation": pose([22, 0, 0])},
        "front_left_leg": {"position": pose([0, 3, 0]), "rotation": pose([-8, 0, 0])},
        "front_right_leg": {"position": pose([0, 3, 0]), "rotation": pose([-8, 0, 0])},
        "back_left_leg": {"position": pose([0, 1.5, 2]), "rotation": pose([-80, 0, 0])},
        "back_right_leg": {"position": pose([0, 1.5, 2]), "rotation": pose([-80, 0, 0])},
        "tail": {"rotation": pose([-20, 0, 0])},
    })
    jump = anim(0.5, {
        "front_left_leg": {"rotation": pose([-55, 0, 0], 0.5)},
        "front_right_leg": {"rotation": pose([-50, 0, 0], 0.5)},
        "back_left_leg": {"rotation": pose([45, 0, 0], 0.5)},
        "back_right_leg": {"rotation": pose([50, 0, 0], 0.5)},
        "tail": {"rotation": pose([30, 0, 0], 0.5)},
        "head": {"rotation": pose([-8, 0, 0], 0.5)},
    })
    bite = anim(0.5, {
        "head": {"rotation": keys({0.0: [0, 0, 0], 0.15: [12, 0, 0], 0.3: [6, 0, 0], 0.5: [0, 0, 0]})},
        "jaw": {"rotation": keys({0.0: [0, 0, 0], 0.12: [38, 0, 0], 0.22: [0, 0, 0], 0.5: [0, 0, 0]})},
        "body": {"position": keys({0.0: [0, 0, 0], 0.15: [0, 0, -2], 0.5: [0, 0, 0]})},
    }, loop=False)
    howl = anim(2.2, {
        "head": {"rotation": keys({0.0: [0, 0, 0], 0.4: [-48, 0, 0], 1.8: [-52, 0, 0], 2.2: [0, 0, 0]})},
        "jaw": {"rotation": keys({0.0: [0, 0, 0], 0.4: [22, 0, 0], 1.6: [26, 0, 0], 2.0: [0, 0, 0]})},
        "body": {"rotation": keys({0.0: [0, 0, 0], 0.4: [-8, 0, 0], 1.8: [-8, 0, 0], 2.2: [0, 0, 0]})},
        "tail": {"rotation": keys({0.0: [0, 0, 0], 0.4: [-15, 0, 0], 2.2: [0, 0, 0]})},
    }, loop=False)
    save_anims("dire_wolf", {"idle": idle, "walk": walk, "run": run, "sit": sit, "jump": jump, "bite": bite, "howl": howl})


# =====================================================================================
#  HARPIA
# =====================================================================================
def harpy():
    SKIN, SKIN_D = H("E9C3A0"), H("C99A78")
    HAIR, HAIR_L = H("5A2E22"), H("8A4A33")
    FEA, FEA_L, FEA_D, FEA_TIP = H("7A5536"), H("B48A5E"), H("4A3020"), H("EBDDC2")
    LEG, LEG_D, TALON = H("D9A93A"), H("A87C22"), H("221A14")
    EYE, PUPIL, LIP, CREST = H("F2C230"), H("1A1206"), H("B5636A"), H("C2412E")

    def feathers(base, tip=FEA_TIP, seed=1, jag=True):
        """Penas: base com fileiras em escama, pontas claras e borda de baixo recortada."""
        def p(c, g, f, cb):
            rnd = random.Random(seed)
            for k, r in f.items():
                surf(c, r, k, base, rnd, noise=0.05, edge=0.06)
                if k in ("top", "bottom"):
                    continue
                x, y, w, h = r
                for j in range(h):
                    for i in range(w):
                        if (i + (j // 2) % 2) % 2 == 0 and j % 2 == 1:
                            c.set(x + i, y + j, darken(c.get(x + i, y + j), 0.18))
                for i in range(w):
                    c.set(x + i, y + h - 1, tip if i % 2 == 0 else mix(tip, base, 0.5))
                    if jag and i % 2 == 1:
                        c.set(x + i, y + h - 1, T)
        return p

    def head(c, g, f, cb):
        std(SKIN, seed=3, noise=0.03, edge=0.06)(c, g, f, cb)
        x, y, w, h = f["front"]
        for (i, j, col) in ((1, 2, HAIR), (4, 2, HAIR), (1, 3, EYE), (2, 3, PUPIL), (3, 3, PUPIL), (4, 3, EYE),
                            (2, 4, SKIN_D), (2, 5, LIP), (3, 5, LIP), (0, 4, H("E8A898")), (5, 4, H("E8A898"))):
            c.set(x + i, y + j, col)

    def hair(c, g, f, cb):
        rnd = random.Random(5)
        for k, r in f.items():
            surf(c, r, k, HAIR, rnd, streak=0.6, sdark=0.25, slight=0.25, noise=0.05, edge=0.0)
        x, y, w, h = f["front"]
        for i, j, X, Y in px_iter(f["front"]):
            fringe = j == 0 or (j == 1 and i % 3 != 1) or (i in (0, w - 1) and j < h - 1)
            if not fringe:
                c.set(X, Y, T)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            front_i = w - 1 if k == "right" else 0
            for j in range(3, h):
                c.set(x + front_i, y + j, T)
        x, y, w, h = f["bottom"]
        clear(c, f["bottom"])

    def hair_back(c, g, f, cb):
        rnd = random.Random(7)
        for k, r in f.items():
            surf(c, r, k, HAIR, rnd, streak=0.7, sdark=0.25, slight=0.25, noise=0.05, edge=0.0)
            if k in ("front", "back", "left", "right"):
                x, y, w, h = r
                for i in range(w):
                    if i % 2 == 0:
                        c.set(x + i, y + h - 1, T)

    def crest(c, g, f, cb):
        std(CREST, seed=9, noise=0.06)(c, g, f, cb)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y, FEA_TIP if i % 2 == 0 else T)

    def torso(c, g, f, cb):
        std(SKIN, seed=11, noise=0.03, edge=0.05)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, FEA_L)
        x, y, w, h = f["front"]
        c.set(x + w // 2 - 1, y + 5, SKIN_D)

    def arm(c, g, f, cb):
        feathers(FEA_D, tip=FEA, seed=13, jag=False)(c, g, f, cb)

    def claws(c, g, f, cb):
        std(LEG, seed=15)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, TALON)

    def wing(c, g, f, cb):
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                kk = (w - 1 - i) if k == "right" else i      # 0 = junto do braco
                limit = h - 1 - round(kk * 0.62)
                if (kk // 2) % 2 == 1:
                    limit -= 1                                # pontas em escada (penas)
                for j in range(h):
                    if j > limit:
                        c.set(x + i, y + j, T)
                        continue
                    if j < 4:
                        col = FEA_D if (i + j) % 2 else mix(FEA_D, FEA, 0.5)
                    elif j >= limit - 1:
                        col = FEA_TIP if j == limit else mix(FEA_TIP, FEA_L, 0.4)
                    else:
                        col = FEA if (j // 3) % 2 == 0 else mix(FEA, FEA_D, 0.45)
                        if kk % 2 == 0:
                            col = lighten(col, 0.1)
                    c.set(x + i, y + j, col)

    def thigh(c, g, f, cb):
        feathers(FEA, seed=17)(c, g, f, cb)

    def shin(c, g, f, cb):
        std(LEG, seed=19, edge=0.0)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for j in range(0, h, 2):
                c.set(x, y + j, LEG_D)

    def foot(c, g, f, cb):
        std(LEG, faces={"top": lighten(LEG, 0.05)}, seed=21)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y, TALON)
        x, y, w, h = f["top"]
        for i in range(w):
            c.set(x + i, y + h - 1, TALON if i % 2 == 0 else LEG_D)

    m = Model("harpy", 64)
    m.bone("root")
    m.bone("body", "root", (0, 12, 0))
    m.box("body", (-3, 12, -1.5), (6, 8, 3), torso)
    m.box("body", (-3.5, 17, -2), (7, 3, 4), feathers(FEA_L, seed=23))
    m.box("body", (-3.5, 9, -2), (7, 4, 4), feathers(FEA, seed=25))
    m.bone("head", "body", (0, 20, 0))
    m.box("head", (-3, 20, -3), (6, 6, 6), head)
    m.box("head", (-3.5, 19.5, -3.5), (7, 7, 7), hair)
    m.box("head", (-3.5, 14, 2.5), (7, 6, 2), hair_back)
    m.box("head", (-0.5, 26, -1.5), (1, 3, 5), crest)
    m.bone("left_wing", "body", (3, 19.5, 0))
    m.box("left_wing", (3, 12, -1), (2, 8, 2), arm)
    m.box("left_wing", (3, 10, -1), (2, 2, 2), claws)
    m.box("left_wing", (4, 5, 0.5), (0, 15, 9), wing)
    m.bone("right_wing", "body", (-3, 19.5, 0))
    m.box("right_wing", (-5, 12, -1), (2, 8, 2), arm)
    m.box("right_wing", (-5, 10, -1), (2, 2, 2), claws)
    m.box("right_wing", (-4, 5, 0.5), (0, 15, 9), wing)
    m.bone("left_leg", "root", (1.5, 10, 0))
    m.box("left_leg", (0, 6, -1.5), (3, 4, 3), thigh)
    m.box("left_leg", (1, 1, -0.5), (1, 5, 1), shin)
    m.box("left_leg", (0, 0, -2.5), (3, 1, 4), foot)
    m.bone("right_leg", "root", (-1.5, 10, 0))
    m.box("right_leg", (-3, 6, -1.5), (3, 4, 3), thigh)
    m.box("right_leg", (-2, 1, -0.5), (1, 5, 1), shin)
    m.box("right_leg", (-3, 0, -2.5), (3, 1, 4), foot)
    c, g = m.build(3, 3, 1.0)
    save_tex("harpy", c)

    LF = 0.8
    fly = anim(LF, {
        "left_wing": {"rotation": sampled(LF, 12, lambda t: [8 * S(t, LF, 0.6), 0, -85 + 32 * S(t, LF)])},
        "right_wing": {"rotation": sampled(LF, 12, lambda t: [8 * S(t, LF, 0.6), 0, 85 - 32 * S(t, LF)])},
        "body": {"rotation": sampled(LF, 8, lambda t: [18 + 3 * S(t, LF), 0, 0])},
        "head": {"rotation": sampled(LF, 8, lambda t: [-14 - 3 * S(t, LF), 0, 0])},
        "left_leg": {"rotation": sampled(LF, 8, lambda t: [40 + 6 * S(t, LF, 1.0), 0, 0])},
        "right_leg": {"rotation": sampled(LF, 8, lambda t: [40 + 6 * S(t, LF, 1.3), 0, 0])},
        "root": {"position": sampled(LF, 8, lambda t: [0, 1.2 * S(t, LF, math.pi / 2), 0])},
    })
    LH = 1.0
    hover = anim(LH, {
        "left_wing": {"rotation": sampled(LH, 12, lambda t: [6 * S(t, LH, 0.6), 0, -92 + 26 * S(t, LH)])},
        "right_wing": {"rotation": sampled(LH, 12, lambda t: [6 * S(t, LH, 0.6), 0, 92 - 26 * S(t, LH)])},
        "body": {"rotation": sampled(LH, 8, lambda t: [5 + 2 * S(t, LH), 0, 0])},
        "left_leg": {"rotation": sampled(LH, 8, lambda t: [12 + 8 * S(t, LH, 1.0), 0, 0])},
        "right_leg": {"rotation": sampled(LH, 8, lambda t: [12 + 8 * S(t, LH, 1.4), 0, 0])},
        "root": {"position": sampled(LH, 8, lambda t: [0, 1.0 * S(t, LH, math.pi / 2), 0])},
        "head": {"rotation": sampled(LH, 8, lambda t: [0, 0, 3 * S(t, LH, 0, 0.5)])},
    })
    dive = anim(0.4, {
        "left_wing": {"rotation": sampled(0.4, 4, lambda t: [60, 0, -28 + 4 * S(t, 0.4)])},
        "right_wing": {"rotation": sampled(0.4, 4, lambda t: [60, 0, 28 - 4 * S(t, 0.4)])},
        "body": {"rotation": pose([50, 0, 0], 0.4)},
        "head": {"rotation": pose([-40, 0, 0], 0.4)},
        "left_leg": {"rotation": pose([-45, 0, 0], 0.4)},
        "right_leg": {"rotation": pose([-45, 0, 0], 0.4)},
    })
    LP = 3.0
    perch = anim(LP, {
        "left_wing": {"rotation": sampled(LP, 6, lambda t: [15, 0, -8 - 3 * S(t, LP)])},
        "right_wing": {"rotation": sampled(LP, 6, lambda t: [15, 0, 8 + 3 * S(t, LP)])},
        "body": {"position": sampled(LP, 6, lambda t: [0, 0.2 * S(t, LP), 0])},
        "head": {"rotation": sampled(LP, 6, lambda t: [0, 10 * S(t, LP, 0, 0.5), 0])},
    })
    LW = 0.8
    walk = anim(LW, {
        "left_leg": {"rotation": sampled(LW, 8, lambda t: [25 * S(t, LW), 0, 0])},
        "right_leg": {"rotation": sampled(LW, 8, lambda t: [-25 * S(t, LW), 0, 0])},
        "left_wing": {"rotation": sampled(LW, 8, lambda t: [15, 0, -12 - 6 * abs(S(t, LW))])},
        "right_wing": {"rotation": sampled(LW, 8, lambda t: [15, 0, 12 + 6 * abs(S(t, LW))])},
        "body": {"position": sampled(LW, 8, lambda t: [0, 0.4 * abs(S(t, LW)), 0])},
    })
    claw = anim(0.5, {
        "left_leg": {"rotation": keys({0.0: [0, 0, 0], 0.15: [-75, 0, 0], 0.3: [25, 0, 0], 0.5: [0, 0, 0]})},
        "right_leg": {"rotation": keys({0.0: [0, 0, 0], 0.18: [-70, 0, 0], 0.33: [25, 0, 0], 0.5: [0, 0, 0]})},
        "left_wing": {"rotation": keys({0.0: [0, 0, -90], 0.15: [0, 0, -115], 0.5: [0, 0, -90]})},
        "right_wing": {"rotation": keys({0.0: [0, 0, 90], 0.15: [0, 0, 115], 0.5: [0, 0, 90]})},
    }, loop=False)
    screech = anim(1.0, {
        "head": {"rotation": keys({0.0: [0, 0, 0], 0.2: [-35, 0, 0], 0.8: [-38, 0, 0], 1.0: [0, 0, 0]})},
        "body": {"rotation": keys({0.0: [0, 0, 0], 0.2: [-12, 0, 0], 0.8: [-12, 0, 0], 1.0: [0, 0, 0]})},
        "left_wing": {"rotation": keys({0.0: [0, 0, -90], 0.2: [-10, 0, -125], 0.8: [-10, 0, -125], 1.0: [0, 0, -90]})},
        "right_wing": {"rotation": keys({0.0: [0, 0, 90], 0.2: [-10, 0, 125], 0.8: [-10, 0, 125], 1.0: [0, 0, 90]})},
    }, loop=False)
    save_anims("harpy", {"fly": fly, "hover": hover, "dive": dive, "perch": perch, "walk": walk, "claw": claw, "screech": screech})


# =====================================================================================
#  BASILISCO
# =====================================================================================
def basilisk():
    SC, SC_D, SC_L = H("4E6B2E"), H("2E431B"), H("7C9645")
    BELLY, BELLY_D = H("C9C27A"), H("A39B57")
    CREST, CREST_L, GOLD, GEM = H("B8321F"), H("E8613A"), H("D9B44A"), H("FF5A3C")
    EYE, PUPIL, MOUTH, TOOTH, CLAW, STRIPE = H("FFE14A"), H("201A05"), H("6B1E24"), H("ECE6D2"), H("2A2620"), H("23361A")

    def scales(base=SC, seed=1, stripes=True, belly=True):
        def p(c, g, f, cb):
            rnd = random.Random(seed)
            for k, r in f.items():
                col = BELLY if (k == "bottom" and belly) else base
                surf(c, r, k, col, rnd, noise=0.05, edge=0.1)
                x, y, w, h = r
                if k == "bottom" and belly:
                    for j in range(0, h, 2):
                        for i in range(w):
                            c.set(x + i, y + j, mix(c.get(x + i, y + j), BELLY_D, 0.6))
                    continue
                for j in range(h):
                    for i in range(w):
                        if (i + j) % 2 == 0 and (j % 2 == 0):
                            c.set(x + i, y + j, mix(c.get(x + i, y + j), SC_L, 0.35))
                if stripes and k == "top":
                    for j in range(1, h, 4):
                        for i in range(w):
                            c.set(x + i, y + j, mix(c.get(x + i, y + j), STRIPE, 0.75))
                if stripes and k in ("left", "right"):
                    for i in range(1, w, 4):
                        for j in range(0, max(1, h - 2)):
                            c.set(x + i, y + j, mix(c.get(x + i, y + j), STRIPE, 0.6))
                if belly and k in ("left", "right", "front", "back") and h >= 3:
                    for i in range(w):
                        c.set(x + i, y + h - 1, mix(c.get(x + i, y + h - 1), BELLY, 0.7))
        return p

    def spines(c, g, f, cb):
        std(CREST, faces={"top": CREST_L}, seed=3)(c, g, f, cb)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                if i % 3 == 1:
                    c.set(x + i, y, T)
                c.set(x + i, y + h - 1, darken(CREST, 0.3))

    def head(c, g, f, cb):
        scales(seed=5, stripes=False)(c, g, f, cb)
        x, y, w, h = f["front"]
        for (i, col) in ((0, EYE), (1, PUPIL), (w - 2, PUPIL), (w - 1, EYE)):
            c.set(x + i, y + 1, col)
            g.set(x + i, y + 1, EYE if col == EYE else H("FFB000"))
        for i in (0, 1, w - 2, w - 1):
            c.set(x + i, y, darken(SC_D, 0.2))
        xr, yr, wr, hr = f["right"]
        c.set(xr + wr - 1, yr + 1, EYE)
        c.set(xr + wr - 2, yr + 1, EYE)
        g.set(xr + wr - 1, yr + 1, EYE)
        g.set(xr + wr - 2, yr + 1, EYE)
        xl, yl, wl, hl = f["left"]
        c.set(xl, yl + 1, EYE)
        c.set(xl + 1, yl + 1, EYE)
        g.set(xl, yl + 1, EYE)
        g.set(xl + 1, yl + 1, EYE)

    def snout(c, g, f, cb):
        scales(base=SC, seed=7, stripes=False, belly=False)(c, g, f, cb)
        x, y, w, h = f["front"]
        c.set(x + 1, y, SC_D)
        c.set(x + w - 2, y, SC_D)
        for i in range(w):
            c.set(x + i, y + h - 1, TOOTH if i % 2 == 0 else MOUTH)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, TOOTH if i % 2 == 0 else MOUTH)

    def jaw(c, g, f, cb):
        scales(base=SC, seed=9, stripes=False)(c, g, f, cb)
        x, y, w, h = f["top"]
        for i, j, X, Y in px_iter(f["top"]):
            edge = i in (0, w - 1) or j == h - 1
            c.set(X, Y, (TOOTH if (i + j) % 2 == 0 else darken(TOOTH, 0.3)) if edge else mix(MOUTH, H("8E2A35"), 0.3 * ((i + j) % 2)))

    def crown(c, g, f, cb):
        std(GOLD, seed=11, noise=0.05)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, darken(GOLD, 0.35))
        x, y, w, h = f["front"]
        if h >= 3:
            c.set(x + w // 2, y + 1, GEM)
            g.set(x + w // 2, y + 1, GEM)

    def frill(c, g, f, cb):
        rnd = random.Random(13)
        for k, r in f.items():
            surf(c, r, k, CREST, rnd, noise=0.06, edge=0.0)
        for k in ("front", "back"):
            x, y, w, h = f[k]
            for i in range(w):
                if i % 2 == 1:
                    c.set(x + i, y, T)
                if i in (0, w - 1):
                    c.set(x + i, y, T)
                    c.set(x + i, y + 1, T)
                c.set(x + i, y + h - 1, darken(CREST, 0.3))
            for sx in (2, w - 3):
                c.set(x + sx, y + 2, STRIPE)
                c.set(x + sx, y + 3, GOLD)
                g.set(x + sx, y + 3, darken(GOLD, 0.3))
            for i in range(3, w - 3):
                if i % 2 == 0:
                    c.set(x + i, y + 2, CREST_L)

    def tail_tip(c, g, f, cb):
        scales(seed=15)(c, g, f, cb)
        x, y, w, h = f["back"]
        for i, j, X, Y in px_iter(f["back"]):
            c.set(X, Y, CREST)

    def leg_upper(c, g, f, cb):
        scales(seed=17, stripes=False)(c, g, f, cb)

    def leg_lower(c, g, f, cb):
        scales(seed=19, stripes=False, belly=False)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y + h - 1, CLAW if i % 2 == 0 else SC_D)

    def tongue(c, g, f, cb):
        # lingua bifurcada: o meio da ponta e transparente (a ponta de tras fica escondida na boca)
        for k in ("top", "bottom"):
            x, y, w, h = f[k]
            for i, j, X, Y in px_iter(f[k]):
                end = j in (0, h - 1)
                if end and i == w // 2:
                    c.set(X, Y, T)
                else:
                    c.set(X, Y, H("A8324A") if end else H("D94A5E"))

    m = Model("basilisk", 128)
    m.bone("root")
    m.bone("body", "root", (0, 6, 0))
    m.box("body", (-4.5, 3, -8), (9, 6, 17), scales(seed=21))
    m.box("body", (-0.5, 9, -7), (1, 2, 15), spines)
    m.bone("head", "body", (0, 7, -8))
    m.box("head", (-3.5, 4, -15), (7, 5, 7), head)
    m.box("head", (-2.5, 4, -19), (5, 3, 4), snout)
    m.box("head", (-0.5, 9, -14), (1, 4, 3), crown)
    m.box("head", (-2.5, 9, -13), (1, 3, 2), crown)
    m.box("head", (1.5, 9, -13), (1, 3, 2), crown)
    m.bone("frill", "head", (0, 7, -9))
    m.box("frill", (-5.5, 4, -9.5), (11, 6, 1), frill)
    m.bone("jaw", "head", (0, 4.5, -10))
    m.box("jaw", (-2.5, 2.5, -18.5), (5, 2, 8), jaw)
    m.bone("tongue", "head", (0, 4.25, -16))
    m.box("tongue", (-1.5, 4.25, -18), (3, 0, 4), tongue)
    m.bone("tail1", "body", (0, 6, 9))
    m.box("tail1", (-3, 3.5, 9), (6, 5, 9), scales(seed=23))
    m.bone("tail2", "tail1", (0, 6, 18))
    m.box("tail2", (-2, 4, 18), (4, 3, 9), scales(seed=25))
    m.bone("tail3", "tail2", (0, 5.5, 27))
    m.box("tail3", (-1, 4.5, 27), (2, 2, 8), tail_tip)
    for name, sx, z in (("front_left_leg", 1, -5.5), ("front_right_leg", -1, -5.5),
                        ("back_left_leg", 1, 5.5), ("back_right_leg", -1, 5.5)):
        m.bone(name, "root", (4.5 * sx, 6, z))
        ux = 4 if sx > 0 else -8
        lx = 6.5 if sx > 0 else -9.5
        m.box(name, (ux, 4, z - 1.5), (4, 3, 3), leg_upper)
        m.box(name, (lx, 0, z - 1.5), (3, 5, 3), leg_lower)
    c, g = m.build(4, 2, 0.5)
    save_tex("basilisk", c, g)

    LI = 4.0
    idle = anim(LI, {
        "body": {"position": sampled(LI, 8, lambda t: [0, 0.15 * S(t, LI, 0, 2), 0])},
        "tail1": {"rotation": sampled(LI, 8, lambda t: [0, 6 * S(t, LI), 0])},
        "tail2": {"rotation": sampled(LI, 8, lambda t: [0, 8 * S(t, LI, -0.6), 0])},
        "tail3": {"rotation": sampled(LI, 8, lambda t: [0, 10 * S(t, LI, -1.2), 0])},
        "head": {"rotation": sampled(LI, 8, lambda t: [0, 6 * S(t, LI, 0, 0.5), 0])},
        "tongue": {"position": keys({0.0: [0, 0, 0], 1.0: [0, 0, 0], 1.1: [0, 0, -4], 1.35: [0, 0, -4], 1.45: [0, 0, 0],
                                     1.55: [0, 0, -4], 1.7: [0, 0, 0], 4.0: [0, 0, 0]})},
    })
    LW = 1.0
    walk = anim(LW, {
        "front_left_leg": {"rotation": sampled(LW, 8, lambda t: [0, 25 * S(t, LW), -8 * max(0, S(t, LW, math.pi / 2))])},
        "back_right_leg": {"rotation": sampled(LW, 8, lambda t: [0, -25 * S(t, LW), 8 * max(0, S(t, LW, math.pi / 2))])},
        "front_right_leg": {"rotation": sampled(LW, 8, lambda t: [0, 25 * S(t, LW), 8 * max(0, S(t, LW, -math.pi / 2))])},
        "back_left_leg": {"rotation": sampled(LW, 8, lambda t: [0, -25 * S(t, LW), -8 * max(0, S(t, LW, -math.pi / 2))])},
        "body": {"rotation": sampled(LW, 8, lambda t: [0, 6 * S(t, LW), 0])},
        "head": {"rotation": sampled(LW, 8, lambda t: [0, -5 * S(t, LW), 0])},
        "tail1": {"rotation": sampled(LW, 8, lambda t: [0, -8 * S(t, LW, -0.5), 0])},
        "tail2": {"rotation": sampled(LW, 8, lambda t: [0, -10 * S(t, LW, -1.0), 0])},
        "tail3": {"rotation": sampled(LW, 8, lambda t: [0, -12 * S(t, LW, -1.5), 0])},
    })
    LG = 1.0
    gaze = anim(LG, {
        "head": {"rotation": sampled(LG, 4, lambda t: [-18, 0, 0])},
        "frill": {"scale": sampled(LG, 8, lambda t: [1.35 + 0.08 * S(t, LG), 1.35 + 0.08 * S(t, LG), 1])},
        "jaw": {"rotation": sampled(LG, 4, lambda t: [10, 0, 0])},
        "body": {"position": sampled(LG, 4, lambda t: [0, 1.0, 0])},
        "front_left_leg": {"rotation": pose([0, 0, -12], LG)},
        "front_right_leg": {"rotation": pose([0, 0, 12], LG)},
        "tail3": {"rotation": sampled(LG, 8, lambda t: [8, 15 * S(t, LG, 0, 2), 0])},
    })
    bite = anim(0.45, {
        "head": {"position": keys({0.0: [0, 0, 0], 0.12: [0, 0, -3], 0.3: [0, 0, 0]}),
                 "rotation": keys({0.0: [0, 0, 0], 0.12: [-10, 0, 0], 0.25: [8, 0, 0], 0.45: [0, 0, 0]})},
        "jaw": {"rotation": keys({0.0: [0, 0, 0], 0.12: [40, 0, 0], 0.22: [0, 0, 0], 0.45: [0, 0, 0]})},
    }, loop=False)
    save_anims("basilisk", {"idle": idle, "walk": walk, "gaze": gaze, "bite": bite})


# =====================================================================================
#  MIMICO (bau)
# =====================================================================================
def mimic():
    W1, W2, W3, W4 = H("A47227"), H("AB792D"), H("8F691D"), H("A76E1F")
    WD, FRAME, FRAME2, BAND, BAND_D = H("7F5F22"), H("2A251D"), H("463E32"), H("775A30"), H("332E25")
    INSIDE = H("432700")
    MOUTH, GUM, TONGUE, TONGUE_D = H("3E0A10"), H("8E2A35"), H("C2404F"), H("8E2433")
    TOOTH, TOOTH_D, EYE, PUPIL = H("F1ECDC"), H("BDB59C"), H("FFE45C"), H("120808")
    LATCH, LATCH_D, LEG, LEG_D, CLAW = H("B9B9B9"), H("6C6C6C"), H("5A2E3E"), H("3A1C28"), H("1C1414")

    def planks(c, rect, rnd, bottom_band=True):
        x, y, w, h = rect
        tones = [W1, W2, W3, W4]
        for j in range(h):
            # tabuas horizontais: cada linha com um tom, veios curtos e emenda escura a cada 4 linhas
            row_tone = tones[(j // 2) % 4]
            seam = j % 4 == 3
            for i in range(w):
                col = darken(row_tone, 0.12) if seam else row_tone
                r = rnd.random()
                if r < 0.08:
                    col = WD
                elif r < 0.16:
                    col = W2
                c.set(x + i, y + j, col)
        for i in range(w):
            c.set(x + i, y, FRAME)
            c.set(x + i, y + h - 1, BAND_D if bottom_band else FRAME)
            if bottom_band and h > 4:
                c.set(x + i, y + h - 2, BAND)
        for j in range(h):
            c.set(x, y + j, FRAME2)
            c.set(x + w - 1, y + j, FRAME2)

    def base(c, g, f, cb):
        rnd = random.Random(3)
        for k in ("front", "back", "left", "right"):
            planks(c, f[k], rnd)
        planks(c, f["bottom"], rnd, bottom_band=False)
        # boca: borda de madeira, gengiva, garganta escura
        x, y, w, h = f["top"]
        for i, j, X, Y in px_iter(f["top"]):
            d = min(i, j, w - 1 - i, h - 1 - j)
            if d == 0:
                col = WD
            elif d == 1:
                col = GUM
            else:
                col = mix(MOUTH, GUM, max(0.0, 0.35 - 0.08 * d))
            c.set(X, Y, col)

    def lid(c, g, f, cb):
        rnd = random.Random(5)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            planks(c, f[k], rnd, bottom_band=False)
            for i in range(w):
                c.set(x + i, y + h - 1, darken(W3, 0.15))
        x, y, w, h = f["top"]
        planks(c, f["top"], rnd, bottom_band=False)
        for i, j, X, Y in px_iter(f["top"]):
            if i in (0, w - 1) or j in (0, h - 1):
                c.set(X, Y, FRAME)
        # ceu da boca com dois olhos
        x, y, w, h = f["bottom"]
        for i, j, X, Y in px_iter(f["bottom"]):
            d = min(i, j, w - 1 - i, h - 1 - j)
            c.set(X, Y, WD if d == 0 else (GUM if d == 1 else MOUTH))
        for ex in (3, 8):
            for (i, j) in ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1)):
                c.set(x + ex + i, y + 6 + j, EYE)
                g.set(x + ex + i, y + 6 + j, EYE)
            c.set(x + ex + 1, y + 6, PUPIL)
            c.set(x + ex + 1, y + 7, PUPIL)
            g.set(x + ex + 1, y + 6, T)
            g.set(x + ex + 1, y + 7, T)

    def latch(c, g, f, cb):
        for k, r in f.items():
            for i, j, X, Y in px_iter(r):
                c.set(X, Y, LATCH if i == 0 else LATCH_D)
        x, y, w, h = f["front"]
        c.set(x, y + 2, H("1E1E1E"))
        c.set(x + 1, y + 2, H("1E1E1E"))
        c.set(x, y, lighten(LATCH, 0.3))

    def teeth(up):
        """Fileira de dentes com vaos transparentes; up=True: pontas para cima."""
        def p(c, g, f, cb):
            for k, r in f.items():
                x, y, w, h = r
                if k in ("top", "bottom"):
                    for i, j, X, Y in px_iter(r):
                        n = i + j
                        tipside = (k == "top") == up
                        if tipside:
                            c.set(X, Y, TOOTH if n % 2 == 0 else T)
                        else:
                            c.set(X, Y, GUM)
                    continue
                if w == 1:
                    for j in range(h):
                        c.set(x, y + j, TOOTH)
                    continue
                for i in range(w):
                    tooth = i % 2 == 0
                    for j in range(h):
                        tip = (j == 0) if up else (j == h - 1)
                        if tooth:
                            c.set(x + i, y + j, TOOTH if tip else TOOTH_D)
                        else:
                            c.set(x + i, y + j, T if tip else GUM)
        return p

    def tongue(c, g, f, cb):
        for k, r in f.items():
            for i, j, X, Y in px_iter(r):
                c.set(X, Y, TONGUE_D if k != "top" else TONGUE)
        x, y, w, h = f["top"]
        for j in range(h):
            c.set(x + w // 2, y + j, TONGUE_D)
        c.set(x, y + h - 1, T)
        c.set(x + w - 1, y + h - 1, T)
        x, y, w, h = f["front"]
        c.set(x, y, T)
        c.set(x + w - 1, y, T)

    def legp(c, g, f, cb):
        std(LEG, faces={"bottom": LEG_D}, seed=7)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y + h - 1, CLAW if i % 2 == 0 else LEG_D)

    m = Model("mimic", 64)
    m.bone("root")
    m.bone("body", "root", (0, 0, 0))
    m.box("body", (-7, 0, -7), (14, 10, 14), base)
    m.box("body", (-6.5, 9, -6.5), (13, 2, 1), teeth(True))
    m.box("body", (-6.5, 9, -5.5), (1, 2, 12), teeth(True))
    m.box("body", (5.5, 9, -5.5), (1, 2, 12), teeth(True))
    m.bone("lid", "body", (0, 9, 7))
    m.box("lid", (-7, 9, -7), (14, 5, 14), lid)
    m.box("lid", (-1, 7, -8), (2, 4, 1), latch)
    m.box("lid", (-6.5, 7, -6.5), (13, 2, 1), teeth(False))
    m.box("lid", (-6.5, 7, -5.5), (1, 2, 12), teeth(False))
    m.box("lid", (5.5, 7, -5.5), (1, 2, 12), teeth(False))
    m.bone("tongue", "body", (0, 10, 4))
    m.box("tongue", (-2.5, 9.5, -5), (5, 1, 9), tongue)
    for name, x, z in (("leg_front_left", 3.5, -6), ("leg_front_right", -6.5, -6),
                       ("leg_back_left", 3.5, 3), ("leg_back_right", -6.5, 3)):
        m.bone(name, "root", (x + 1.5, 4, z + 1.5))
        m.box(name, (x, 0, z), (3, 4, 3), legp)
    c, g = m.build(2, 2, 0.5)
    save_tex("mimic", c, g)

    still = {"body": {"position": pose([0, 0, 0])}, "lid": {"rotation": pose([0, 0, 0])},
             "tongue": {"position": pose([0, 0, 0]), "rotation": pose([0, 0, 0])}}
    disguised = anim(1.0, still)
    LI = 2.0
    idle = anim(LI, {
        "body": {"position": sampled(LI, 8, lambda t: [0, 4 + 0.3 * S(t, LI), 0])},
        "lid": {"rotation": sampled(LI, 8, lambda t: [-14 - 6 * S(t, LI), 0, 0])},
        "tongue": {"rotation": sampled(LI, 8, lambda t: [0, 8 * S(t, LI, 0, 2), 0])},
    })
    LW = 0.5
    walk = anim(LW, {
        "body": {"position": sampled(LW, 8, lambda t: [0, 4 + 1.6 * abs(S(t, LW)), 0]),
                 "rotation": sampled(LW, 8, lambda t: [0, 0, 4 * S(t, LW)])},
        "lid": {"rotation": sampled(LW, 8, lambda t: [-12 - 22 * abs(S(t, LW)), 0, 0])},
        "leg_front_left": {"rotation": sampled(LW, 8, lambda t: [35 * S(t, LW), 0, 0])},
        "leg_back_right": {"rotation": sampled(LW, 8, lambda t: [35 * S(t, LW), 0, 0])},
        "leg_front_right": {"rotation": sampled(LW, 8, lambda t: [-35 * S(t, LW), 0, 0])},
        "leg_back_left": {"rotation": sampled(LW, 8, lambda t: [-35 * S(t, LW), 0, 0])},
        "tongue": {"position": sampled(LW, 8, lambda t: [0, 0, -1.5 * abs(S(t, LW))])},
    })
    bite = anim(0.4, {
        "lid": {"rotation": keys({0.0: [-10, 0, 0], 0.12: [-80, 0, 0], 0.22: [0, 0, 0], 0.4: [-12, 0, 0]})},
        "body": {"position": keys({0.0: [0, 4, 0], 0.12: [0, 5, -3], 0.25: [0, 4, -1], 0.4: [0, 4, 0]})},
        "tongue": {"position": keys({0.0: [0, 0, 0], 0.1: [0, 0, -3], 0.2: [0, 0, 0]})},
    }, loop=False)
    wake = anim(0.8, {
        "body": {"position": keys({0.0: [0, 0, 0], 0.2: [0, 9, 0], 0.45: [0, 4, 0], 0.8: [0, 4, 0]})},
        "lid": {"rotation": keys({0.0: [0, 0, 0], 0.2: [-85, 0, 0], 0.5: [-60, 0, 0], 0.8: [-22, 0, 0]})},
        "tongue": {"position": keys({0.0: [0, 0, 0], 0.3: [0, 1, -4], 0.6: [0, 0, -1], 0.8: [0, 0, 0]}),
                   "rotation": keys({0.0: [0, 0, 0], 0.3: [25, 0, 0], 0.8: [0, 0, 0]})},
    }, loop=False)
    save_anims("mimic", {"disguised": disguised, "idle": idle, "walk": walk, "bite": bite, "wake": wake})


# =====================================================================================
#  GARGULA
# =====================================================================================
def gargoyle():
    ST, ST_L, ST_D, ST_DD = H("8A8A8E"), H("A8A8AD"), H("616167"), H("45454B")
    CRACK, MOSS, MOSS_L = H("2E2E33"), H("5F7A3A"), H("7E9A4E")
    EYE_STONE, EYE_GLOW, EMBER = H("55555B"), H("FF7A2E"), H("FFA040")
    CLAW = H("3A3A40")

    def stone(base=ST, seed=1, moss=0.0, cracks=1, glow_cracks=False, faces=None):
        def p(c, g, f, cb):
            rnd = random.Random(seed)
            for k, r in f.items():
                col = (faces or {}).get(k, base)
                surf(c, r, k, col, rnd, noise=0.09, edge=0.14, light=0.1, dark=0.25)
                x, y, w, h = r
                if w <= 0 or h <= 0:
                    continue
                for _ in range(w * h // 9):
                    c.set(x + rnd.randrange(w), y + rnd.randrange(h), mix(ST_DD, col, 0.4) if rnd.random() < 0.5 else ST_L)
                if moss and k == "top":
                    for i, j, X, Y in px_iter(r):
                        if rnd.random() < moss:
                            c.set(X, Y, MOSS if rnd.random() < 0.7 else MOSS_L)
                if moss and k in ("front", "back", "left", "right"):
                    for i in range(w):
                        if rnd.random() < moss:
                            c.set(x + i, y, MOSS)
                            if rnd.random() < 0.4 and h > 1:
                                c.set(x + i, y + 1, MOSS_L)
                if k in ("front", "back", "left", "right") and w >= 3 and h >= 4:
                    for _ in range(cracks):
                        cx, cy = rnd.randrange(w), rnd.randrange(h // 2)
                        for s in range(rnd.randint(2, 4)):
                            if 0 <= cx < w and 0 <= cy < h:
                                c.set(x + cx, y + cy, CRACK)
                                if glow_cracks:
                                    g.set(x + cx, y + cy, EMBER)
                            cy += 1
                            cx += rnd.choice((-1, 0, 1))
        return p

    def head(c, g, f, cb):
        stone(seed=3, moss=0.25)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in (1, 2, w - 3, w - 2):
            c.set(x + i, y + 2, EYE_STONE)
            g.set(x + i, y + 2, EYE_GLOW)
        c.set(x + 2, y + 3, ST_DD)
        c.set(x + w - 3, y + 3, ST_DD)

    def muzzle(c, g, f, cb):
        stone(seed=5, cracks=0)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y + 1, CRACK)
        c.set(x, y + 2, ST_L)
        c.set(x + w - 1, y + 2, ST_L)
        c.set(x + 1, y, ST_DD)
        c.set(x + w - 2, y, ST_DD)

    def horn(c, g, f, cb):
        stone(base=ST_D, seed=7, cracks=0)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y, ST_DD)

    def membrane(root_left):
        def draw(c, g, rect, root_at_left):
            x, y, w, h = rect
            tips = [(0, h - 1), (5, h - 1), (10, h - 4), (w - 1, h - 9)]

            def edge_y(i):
                for a, b in zip(tips, tips[1:]):
                    if a[0] <= i <= b[0]:
                        t = (i - a[0]) / max(1, b[0] - a[0])
                        base_y = a[1] + (b[1] - a[1]) * t
                        return base_y - 3.0 * math.sin(math.pi * t)
                return 0
            for i in range(w):
                ii = i if root_at_left else w - 1 - i
                ey = edge_y(ii)
                for j in range(h):
                    if j <= 1:
                        c.set(x + i, y + j, ST_D if j == 0 else ST)
                    elif j <= ey:
                        t = j / max(1, h - 1)
                        c.set(x + i, y + j, mix(H("7C7C83"), H("5A5A61"), t))
                    else:
                        c.set(x + i, y + j, T)
            # dedos (ossos da asa) da raiz-topo ate cada ponta
            for (tx, ty) in tips[1:]:
                steps = max(abs(tx), abs(ty)) * 2
                for s in range(steps + 1):
                    px = round(tx * s / steps)
                    py = round(ty * s / steps)
                    xx = px if root_at_left else w - 1 - px
                    if 0 <= xx < w and 0 <= py < h:
                        c.set(x + xx, y + py, ST_DD)

        def p(c, g, f, cb):
            draw(c, g, f["front"], root_left)
            draw(c, g, f["back"], not root_left)
        return p

    def strut(c, g, f, cb):
        stone(base=ST_D, seed=9, cracks=0)(c, g, f, cb)

    def claws(c, g, f, cb):
        stone(base=ST_D, seed=11, cracks=0)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, CLAW if i % 2 == 0 else ST_DD)

    def foot(c, g, f, cb):
        stone(base=ST_D, seed=13, cracks=0)(c, g, f, cb)
        x, y, w, h = f["front"]
        for i in range(w):
            c.set(x + i, y, CLAW)
        x, y, w, h = f["top"]
        for i in range(w):
            c.set(x + i, y + h - 1, CLAW if i % 2 == 0 else ST_DD)

    def tail_tip(c, g, f, cb):
        stone(base=ST_D, seed=15, cracks=0)(c, g, f, cb)
        for k in ("top", "bottom"):
            x, y, w, h = f[k]
            c.set(x, y, T)
            c.set(x + w - 1, y, T)

    m = Model("gargoyle", 128)
    m.bone("root")
    m.bone("body", "root", (0, 12, 0))
    m.box("body", (-4.5, 13, -2.5), (9, 9, 5), stone(seed=21, cracks=2, glow_cracks=True))
    m.box("body", (-5.5, 19, -3), (11, 3, 6), stone(seed=23, moss=0.3))
    m.box("body", (-3.5, 11, -2), (7, 2, 4), stone(base=ST_D, seed=25, cracks=0))
    m.bone("head", "body", (0, 22, -1))
    m.box("head", (-3.5, 22, -4.5), (7, 6, 6), head)
    m.box("head", (-2, 22, -6.5), (4, 3, 2), muzzle)
    m.box("head", (-3.5, 26, -5), (7, 1, 1), stone(base=ST_D, seed=27, cracks=0))
    m.box("head", (2, 27, -2.5), (2, 3, 2), horn)
    m.box("head", (2.5, 29, -0.5), (1, 3, 2), horn)
    m.box("head", (-4, 27, -2.5), (2, 3, 2), horn)
    m.box("head", (-3.5, 29, -0.5), (1, 3, 2), horn)
    m.box("head", (3.5, 24, -1), (2, 3, 1), horn)
    m.box("head", (-5.5, 24, -1), (2, 3, 1), horn)
    m.bone("left_arm", "body", (5.5, 20.5, 0))
    m.box("left_arm", (5, 11, -1.5), (3, 10, 3), stone(seed=31, cracks=1, glow_cracks=True))
    m.box("left_arm", (5, 9, -1.5), (3, 2, 3), claws)
    m.bone("right_arm", "body", (-5.5, 20.5, 0))
    m.box("right_arm", (-8, 11, -1.5), (3, 10, 3), stone(seed=33, cracks=1, glow_cracks=True))
    m.box("right_arm", (-8, 9, -1.5), (3, 2, 3), claws)
    m.bone("left_wing", "body", (2, 20.5, 2.5), rotation=(0, -30, 0))
    m.box("left_wing", (2, 23, 2), (15, 1, 1), strut)
    m.box("left_wing", (2, 8, 2.5), (16, 15, 0), membrane(True))
    m.bone("right_wing", "body", (-2, 20.5, 2.5), rotation=(0, 30, 0))
    m.box("right_wing", (-17, 23, 2), (15, 1, 1), strut)
    m.box("right_wing", (-18, 8, 2.5), (16, 15, 0), membrane(False))
    m.bone("tail", "body", (0, 12, 2), rotation=(-30, 0, 0))
    m.box("tail", (-1, 11, 2), (2, 2, 9), stone(base=ST_D, seed=35, cracks=0))
    m.box("tail", (-2, 10.5, 10.5), (4, 3, 1), tail_tip)
    for side, sx in (("left", 1), ("right", -1)):
        leg, shin, ft = side + "_leg", side + "_shin", side + "_foot"
        m.bone(leg, "root", (2.5 * sx, 12, 0))
        m.box(leg, ((1 if sx > 0 else -4), 6, -2.5), (3, 6, 4), stone(seed=41 + sx, cracks=1))
        m.bone(shin, leg, (2.5 * sx, 6, -0.5))
        m.box(shin, ((1.5 if sx > 0 else -3.5), 1, -1), (2, 5, 2), stone(base=ST_D, seed=45 + sx, cracks=0))
        m.bone(ft, shin, (2.5 * sx, 1, 0))
        m.box(ft, ((1 if sx > 0 else -4), 0, -3), (3, 1, 4), foot)
    c, g = m.build(4, 3, 1.0)
    save_tex("gargoyle", c, g)

    statue = anim(1.0, {
        "root": {"position": pose([0, -5, 0])},
        "body": {"rotation": pose([25, 0, 0])},
        "head": {"rotation": pose([-12, 0, 0])},
        "left_leg": {"rotation": pose([-60, 0, 0])},
        "right_leg": {"rotation": pose([-60, 0, 0])},
        "left_shin": {"rotation": pose([108, 0, 0])},
        "right_shin": {"rotation": pose([108, 0, 0])},
        "left_foot": {"rotation": pose([-48, 0, 0])},
        "right_foot": {"rotation": pose([-48, 0, 0])},
        "left_arm": {"rotation": pose([-55, 0, -8])},
        "right_arm": {"rotation": pose([-55, 0, 8])},
        "left_wing": {"rotation": pose([0, -50, 0])},
        "right_wing": {"rotation": pose([0, 50, 0])},
        "tail": {"rotation": pose([-15, 0, 0])},
    })
    LI = 1.2
    idle = anim(LI, {
        "body": {"rotation": sampled(LI, 8, lambda t: [20 + 2 * S(t, LI), 0, 0])},
        "head": {"rotation": sampled(LI, 8, lambda t: [-14, 0, 0])},
        "left_leg": {"rotation": sampled(LI, 8, lambda t: [20 + 5 * S(t, LI, 1.0), 0, 0])},
        "right_leg": {"rotation": sampled(LI, 8, lambda t: [20 + 5 * S(t, LI, 1.3), 0, 0])},
        "left_shin": {"rotation": sampled(LI, 8, lambda t: [15, 0, 0])},
        "right_shin": {"rotation": sampled(LI, 8, lambda t: [15, 0, 0])},
        "left_wing": {"rotation": sampled(LI, 12, lambda t: [0, 10 + 45 * S(t, LI), 6 * S(t, LI, 0.5)])},
        "right_wing": {"rotation": sampled(LI, 12, lambda t: [0, -10 - 45 * S(t, LI), -6 * S(t, LI, 0.5)])},
        "root": {"position": sampled(LI, 8, lambda t: [0, 1.0 * S(t, LI, math.pi / 2), 0])},
        "tail": {"rotation": sampled(LI, 8, lambda t: [0, 10 * S(t, LI), 0])},
    })
    LF = 0.7
    fly = anim(LF, {
        "body": {"rotation": sampled(LF, 8, lambda t: [55 + 3 * S(t, LF), 0, 0])},
        "head": {"rotation": sampled(LF, 8, lambda t: [-45, 0, 0])},
        "left_leg": {"rotation": sampled(LF, 8, lambda t: [50, 0, 0])},
        "right_leg": {"rotation": sampled(LF, 8, lambda t: [50, 0, 0])},
        "left_arm": {"rotation": sampled(LF, 8, lambda t: [35, 0, -10])},
        "right_arm": {"rotation": sampled(LF, 8, lambda t: [35, 0, 10])},
        "left_wing": {"rotation": sampled(LF, 12, lambda t: [0, 20 + 55 * S(t, LF), 0])},
        "right_wing": {"rotation": sampled(LF, 12, lambda t: [0, -20 - 55 * S(t, LF), 0])},
        "tail": {"rotation": sampled(LF, 8, lambda t: [25, 8 * S(t, LF), 0])},
    })
    attack = anim(0.5, {
        "right_arm": {"rotation": keys({0.0: [0, 0, 0], 0.15: [-140, 0, 10], 0.3: [30, 0, 0], 0.5: [0, 0, 0]})},
        "left_arm": {"rotation": keys({0.0: [0, 0, 0], 0.1: [0, 0, 0], 0.25: [-140, 0, -10], 0.4: [30, 0, 0], 0.5: [0, 0, 0]})},
        "head": {"rotation": keys({0.0: [0, 0, 0], 0.25: [15, 0, 0], 0.5: [0, 0, 0]})},
    }, loop=False)
    awaken = anim(0.8, {
        "body": {"rotation": sampled(0.8, 16, lambda t: [0, 0, 4 * math.sin(t * 60) * (1 - t / 0.8)])},
        "left_wing": {"rotation": keys({0.0: [0, 0, 0], 0.3: [0, 45, 0], 0.8: [0, 0, 0]})},
        "right_wing": {"rotation": keys({0.0: [0, 0, 0], 0.3: [0, -45, 0], 0.8: [0, 0, 0]})},
        "head": {"rotation": keys({0.0: [0, 0, 0], 0.3: [-25, 0, 0], 0.8: [0, 0, 0]})},
    }, loop=False)
    save_anims("gargoyle", {"statue": statue, "idle": idle, "fly": fly, "attack": attack, "awaken": awaken})


# =====================================================================================
#  FADA
# =====================================================================================
def fairy():
    SKIN, BLUSH = H("FFE6D5"), H("FFB5BE")
    EYE, EYE_HI = H("2FAE68"), H("D8FFE6")
    HAIR, HAIR_D, HAIR_L = H("FF9EC7"), H("E26FA3"), H("FFC9E0")
    PETAL, PETAL_L, PETAL_D = H("FF8FB8"), H("FFD1E3"), H("E0608F")
    LEAF, LEAF_D, FLOWER = H("5DBB63"), H("3C8E46"), H("FFF27A")

    def head(c, g, f, cb):
        std(SKIN, seed=3, noise=0.02, edge=0.04)(c, g, f, cb)
        x, y, w, h = f["front"]
        for (i, j, col) in ((1, 2, EYE_HI), (1, 3, EYE), (3, 2, EYE_HI), (3, 3, EYE),
                            (0, 4, BLUSH), (4, 4, BLUSH), (2, 4, H("E87D8F"))):
            c.set(x + i, y + j, col)
        g.set(x + 1, y + 2, EYE_HI)
        g.set(x + 3, y + 2, EYE_HI)

    def hair(c, g, f, cb):
        rnd = random.Random(5)
        for k, r in f.items():
            surf(c, r, k, HAIR, rnd, streak=0.6, sdark=0.18, slight=0.25, noise=0.04, edge=0.0)
        x, y, w, h = f["front"]
        for i, j, X, Y in px_iter(f["front"]):
            keep = j == 0 or (j == 1 and i % 2 == 0) or (i in (0, w - 1) and j < 4)
            if not keep:
                c.set(X, Y, T)
        for k in ("left", "right"):
            x, y, w, h = f[k]
            fi = w - 1 if k == "right" else 0
            c.set(x + fi, y + h - 1, T)
        clear(c, f["bottom"])

    def flower(c, g, f, cb):
        for k in ("top", "bottom"):
            x, y, w, h = f[k]
            for i, j, X, Y in px_iter(f[k]):
                ctr = i == w // 2 and j == h // 2
                petal = (i == w // 2) != (j == h // 2)
                c.set(X, Y, FLOWER if ctr else (PETAL_L if petal else T))
                if ctr:
                    g.set(X, Y, FLOWER)

    def ear(c, g, f, cb):
        std(SKIN, seed=7, edge=0.0)(c, g, f, cb)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            c.set(x, y, lighten(SKIN, 0.1))

    def torso(c, g, f, cb):
        std(LEAF, seed=9, noise=0.04, edge=0.08)(c, g, f, cb)
        x, y, w, h = f["front"]
        c.set(x + w // 2, y + 1, PETAL_L)
        c.set(x + w // 2, y + 2, PETAL)
        for k in ("front", "back", "left", "right"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y, SKIN)

    def skirt(c, g, f, cb):
        for k, r in f.items():
            x, y, w, h = r
            for i, j, X, Y in px_iter(r):
                if k == "top":
                    c.set(X, Y, LEAF_D)
                elif k == "bottom":
                    c.set(X, Y, PETAL_L)
                else:
                    col = PETAL if i % 2 == 0 else PETAL_L
                    if j == 0:
                        col = LEAF
                    elif j == h - 1:
                        col = PETAL_D if i % 2 == 0 else T
                    c.set(X, Y, col)

    def limb(leg):
        """Braco (manga de pele) ou perna (sapatilha de folha)."""
        def p(c, g, f, cb):
            std(SKIN, seed=11, edge=0.0, noise=0.02)(c, g, f, cb)
            for k in ("front", "back", "left", "right"):
                x, y, w, h = f[k]
                c.set(x, y + h - 1 if leg else y, LEAF_D if leg else LEAF)
            if leg:
                for i, j, X, Y in px_iter(f["bottom"]):
                    c.set(X, Y, LEAF_D)
        return p

    def wing(root_left, big):
        def draw(c, g, rect, root_at_left):
            x, y, w, h = rect
            cy = (h - 1) / 2.0
            for j in range(h):
                for i in range(w):
                    ii = i if root_at_left else w - 1 - i
                    t = ii / max(1, w - 1)
                    rx = (w - 1) * 0.55
                    ry = h / 2.0
                    cxp = (w - 1) * 0.5
                    inside = ((ii - cxp) / (rx + 0.6)) ** 2 + ((j - cy) / (ry + 0.1)) ** 2 <= 1.0
                    if ii == 0 and abs(j - cy) <= 1:
                        inside = True
                    if not inside:
                        c.set(x + i, y + j, T)
                        continue
                    border = not (((ii - cxp) / (rx - 0.4)) ** 2 + ((j - cy) / (ry - 0.9)) ** 2 <= 1.0)
                    if border:
                        col = (255, 150, 200, 225)
                        c.set(x + i, y + j, col)
                        g.set(x + i, y + j, (255, 170, 215, 200))
                    else:
                        base = mix((150, 255, 220, 150), (255, 190, 225, 150), t)
                        c.set(x + i, y + j, base)
            # pintinhas que brilham
            for (dx, dy) in ((0.55, 0.35), (0.7, 0.65)) if big else ((0.6, 0.5),):
                ii = round((w - 1) * dx)
                xx = ii if root_at_left else w - 1 - ii
                yy = round((h - 1) * dy)
                c.set(x + xx, y + yy, (255, 255, 255, 235))
                g.set(x + xx, y + yy, (255, 255, 255, 220))

        def p(c, g, f, cb):
            draw(c, g, f["front"], root_left)
            draw(c, g, f["back"], not root_left)
        return p

    m = Model("fairy", 64)
    m.bone("root")
    m.bone("body", "root", (0, 4, 0))
    m.box("body", (-1.5, 4, -1), (3, 4, 2), torso)
    m.box("body", (-2.5, 2, -2.5), (5, 3, 5), skirt)
    m.bone("head", "body", (0, 8, 0))
    m.box("head", (-2.5, 8, -2.5), (5, 5, 5), head)
    m.box("head", (-2.5, 8, -2.5), (5, 5, 5), hair, inflate=0.35)
    m.box("head", (0.5, 13.5, -1.5), (3, 0, 3), flower)
    m.box("head", (2.5, 10, -0.5), (1, 2, 1), ear)
    m.box("head", (-3.5, 10, -0.5), (1, 2, 1), ear)
    m.bone("left_arm", "body", (1.5, 7.5, 0))
    m.box("left_arm", (1.5, 4.5, -0.5), (1, 3, 1), limb(False))
    m.bone("right_arm", "body", (-1.5, 7.5, 0))
    m.box("right_arm", (-2.5, 4.5, -0.5), (1, 3, 1), limb(False))
    m.bone("left_wing", "body", (0.5, 7, 1), rotation=(0, -25, 8))
    m.box("left_wing", (0.5, 6, 1), (7, 7, 0), wing(True, True))
    m.box("left_wing", (0.5, 1.5, 1.05), (5, 5, 0), wing(True, False))
    m.bone("right_wing", "body", (-0.5, 7, 1), rotation=(0, 25, -8))
    m.box("right_wing", (-7.5, 6, 1), (7, 7, 0), wing(False, True))
    m.box("right_wing", (-5.5, 1.5, 1.05), (5, 5, 0), wing(False, False))
    m.bone("left_leg", "root", (0.75, 3, 0))
    m.box("left_leg", (0.25, 0, -0.5), (1, 3, 1), limb(True))
    m.bone("right_leg", "root", (-0.75, 3, 0))
    m.box("right_leg", (-1.25, 0, -0.5), (1, 3, 1), limb(True))
    c, g = m.build(2, 2, 0.5)
    save_tex("fairy", c, g)

    L = 1.0
    idle = anim(L, {
        "root": {"position": sampled(L, 8, lambda t: [0, 0.8 * S(t, L), 0])},
        "left_wing": {"rotation": sampled(L, 16, lambda t: [0, 35 * S(t, L, 0, 4), 0])},
        "right_wing": {"rotation": sampled(L, 16, lambda t: [0, -35 * S(t, L, 0, 4), 0])},
        "left_arm": {"rotation": sampled(L, 8, lambda t: [0, 0, -10 - 8 * S(t, L)])},
        "right_arm": {"rotation": sampled(L, 8, lambda t: [0, 0, 10 + 8 * S(t, L)])},
        "left_leg": {"rotation": sampled(L, 8, lambda t: [12 * S(t, L), 0, 0])},
        "right_leg": {"rotation": sampled(L, 8, lambda t: [-12 * S(t, L), 0, 0])},
        "head": {"rotation": sampled(L, 8, lambda t: [0, 0, 5 * S(t, L, 0, 0.5)])},
    })
    LF = 0.6
    fly = anim(LF, {
        "root": {"position": sampled(LF, 8, lambda t: [0, 0.5 * S(t, LF), 0])},
        "body": {"rotation": sampled(LF, 8, lambda t: [20, 0, 0])},
        "head": {"rotation": sampled(LF, 8, lambda t: [-15, 0, 0])},
        "left_wing": {"rotation": sampled(LF, 16, lambda t: [0, 40 * S(t, LF, 0, 3), 0])},
        "right_wing": {"rotation": sampled(LF, 16, lambda t: [0, -40 * S(t, LF, 0, 3), 0])},
        "left_arm": {"rotation": sampled(LF, 8, lambda t: [30, 0, -15])},
        "right_arm": {"rotation": sampled(LF, 8, lambda t: [30, 0, 15])},
        "left_leg": {"rotation": sampled(LF, 8, lambda t: [25, 0, 0])},
        "right_leg": {"rotation": sampled(LF, 8, lambda t: [30, 0, 0])},
    })
    heal = anim(0.8, {
        "root": {"rotation": keys({0.0: [0, 0, 0], 0.8: [0, 360, 0]})},
        "left_arm": {"rotation": keys({0.0: [0, 0, 0], 0.15: [-160, 0, -20], 0.6: [-160, 0, -20], 0.8: [0, 0, 0]})},
        "right_arm": {"rotation": keys({0.0: [0, 0, 0], 0.15: [-160, 0, 20], 0.6: [-160, 0, 20], 0.8: [0, 0, 0]})},
    }, loop=False)
    happy = anim(1.0, {
        "root": {"position": keys({0.0: [0, 0, 0], 0.25: [0, 3, 0], 0.5: [0, 0, 0], 0.75: [0, 3, 0], 1.0: [0, 0, 0]}),
                 "rotation": keys({0.0: [0, 0, 0], 0.5: [0, 180, 0], 1.0: [0, 360, 0]})},
        "left_arm": {"rotation": keys({0.0: [0, 0, 0], 0.2: [0, 0, -120], 0.8: [0, 0, -120], 1.0: [0, 0, 0]})},
        "right_arm": {"rotation": keys({0.0: [0, 0, 0], 0.2: [0, 0, 120], 0.8: [0, 0, 120], 1.0: [0, 0, 0]})},
    }, loop=False)
    save_anims("fairy", {"idle": idle, "fly": fly, "heal": heal, "happy": happy})


# =====================================================================================
#  ITENS (16x16)
# =====================================================================================
def item_pelt():
    c = Canvas(16, 16)
    base, dark, light = H("55515D"), H("2E2B34"), H("8E8999")
    rnd = random.Random(1)
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 5.2, (y + 0.5 - 8.5) / 5.8
            inside = dx * dx + dy * dy <= 1.0
            for (lx, ly) in ((3.2, 3.5), (12.8, 3.5), (3.2, 13.2), (12.8, 13.2), (8, 2.2)):
                if (x + 0.5 - lx) ** 2 + (y + 0.5 - ly) ** 2 <= 2.4:
                    inside = True
            if inside:
                col = base
                if abs(x + 0.5 - 8) < 1.6:
                    col = dark
                n = rnd.random()
                col = lighten(col, 0.25) if n < 0.18 else (darken(col, 0.2) if n > 0.85 else col)
                c.set(x, y, col)
    for (x, y) in ((5, 6), (10, 6), (6, 10), (10, 11), (4, 9), (11, 8)):
        c.set(x, y, light)
    c.outline(H("1C1A20"))
    c.save(os.path.join(A, "textures/item/dire_wolf_pelt.png"))


def item_feather():
    c = Canvas(16, 16)
    brown, dark, cream, shaft = H("7A5536"), H("4A3020"), H("EBDDC2"), H("F4EBD8")
    ax, ay, bx, by = 3.0, 13.5, 12.8, 2.0
    L = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / L, (by - ay) / L
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px * ux + py * uy) / L
            d = abs(px * -uy + py * ux)
            if t < 0 or t > 1:
                continue
            width = 2.7 * (math.sin(math.pi * min(1.0, t * 1.1)) ** 0.6) if t > 0.12 else 0
            if d <= 0.55:
                c.set(x, y, shaft if t > 0.1 else H("C9B79A"))
            elif d <= width:
                col = brown if t < 0.72 else mix(brown, cream, (t - 0.72) / 0.28)
                if int(t * 9) % 2 == 0 and t < 0.75:
                    col = darken(col, 0.25)
                c.set(x, y, col)
    c.outline(darken(dark, 0.3))
    c.save(os.path.join(A, "textures/item/harpy_feather.png"))


def item_scale():
    c = Canvas(16, 16)
    g, gd, gl = H("4E6B2E"), H("2E431B"), H("9CB860")
    for y in range(16):
        for x in range(16):
            r = (x + 0.5 - 8) ** 2 + (y + 0.5 - 7) ** 2 <= 30
            tri = y >= 7 and abs(x + 0.5 - 8) <= (14.5 - y) * 0.75
            if r or tri:
                c.set(x, y, g)
    c.shade_sphere(8, 7, 6.5, light=0.5, dark=0.45)
    for y in range(3, 14):
        c.set(8, y, gd)
    for (x, y) in ((5, 4), (6, 3), (4, 5)):
        c.set(x, y, gl)
    c.outline(H("1A260F"))
    c.save(os.path.join(A, "textures/item/basilisk_scale.png"))


def item_eye():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 6.2:
                c.set(x, y, mix(H("FFE14A"), H("E07A10"), min(1.0, d / 6.2) ** 1.5))
    for y in range(3, 13):
        c.set(7, y, H("140F05"))
        if 5 <= y <= 10:
            c.set(8, y, H("140F05"))
    c.set(5, 5, (255, 255, 255, 255))
    c.set(6, 5, (255, 250, 220, 255))
    for (x, y) in ((3, 9), (12, 6), (11, 11), (4, 6)):
        c.set(x, y, H("B0301E"))
    c.outline(H("4A1208"))
    c.save(os.path.join(A, "textures/item/basilisk_eye.png"))


def item_tooth():
    c = Canvas(16, 16)
    for st in range(0, 101):
        t = st / 100
        cx = 10 - 5 * t - 1.2 * math.sin(math.pi * t)
        cy = 3.5 + 10.5 * t
        w = 2.6 * (1 - t) ** 0.8 + 0.3
        for y in range(16):
            for x in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= w * w:
                    col = mix(H("F1ECDC"), H("C9C0A2"), t * 0.6)
                    if y <= 4:
                        col = H("8E2A35") if y <= 3 else H("B85460")
                    c.set(x, y, col)
    for y in range(6, 11):
        x = round(10 - 5 * (y - 3.5) / 10.5) - 1
        c.set(x, y, (255, 255, 250, 255))
    c.outline(H("3A2A20"))
    c.save(os.path.join(A, "textures/item/mimic_tooth.png"))


def item_fragment():
    c = Canvas(16, 16)
    pts = [(4, 2), (11, 1.5), (14.5, 7), (12.5, 14), (5, 14.5), (1.5, 9)]

    def inside(px, py):
        n = len(pts)
        res = False
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 > py) != (y2 > py):
                xi = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
                if px < xi:
                    res = not res
        return res
    rnd = random.Random(3)
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                t = (x + y) / 30
                col = mix(H("B0B0B5"), H("5E5E64"), t)
                n = rnd.random()
                col = lighten(col, 0.15) if n < 0.15 else (darken(col, 0.18) if n > 0.85 else col)
                c.set(x, y, col)
    for (x, y) in ((6, 4), (7, 5), (7, 6), (8, 7), (8, 8), (9, 9), (10, 10)):
        c.set(x, y, H("2E2E33"))
    c.set(9, 8, H("FF7A2E"))
    c.set(10, 9, H("FFA040"))
    c.outline(H("232327"))
    c.save(os.path.join(A, "textures/item/gargoyle_fragment.png"))


def item_dust():
    c = Canvas(16, 16)
    rnd = random.Random(5)
    cols = [H("FF9EC7"), H("FFC9E0"), H("96FFDC"), H("FFFFFF"), H("E26FA3")]
    for y in range(8, 15):
        for x in range(16):
            if ((x + 0.5 - 8) / 6.0) ** 2 + ((y + 0.5 - 14.0) / 5.0) ** 2 <= 1.0:
                c.set(x, y, rnd.choice(cols))
    c.outline(H("7A2E55"))
    # brilhos soltos no ar (sem contorno, leves)
    for (sx, sy, col) in ((4, 4, H("FFFFFF")), (11, 2, H("FFF27A")), (13, 6, H("96FFDC")), (7, 1, H("FFC9E0"))):
        c.set(sx, sy, col)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.set(sx + dx, sy + dy, col[:3] + (150,))
    c.save(os.path.join(A, "textures/item/fairy_dust.png"))


def items():
    item_pelt()
    item_feather()
    item_scale()
    item_eye()
    item_tooth()
    item_fragment()
    item_dust()
    for i in ("dire_wolf_pelt", "harpy_feather", "basilisk_scale", "basilisk_eye", "mimic_tooth", "gargoyle_fragment", "fairy_dust"):
        save_json(os.path.join(A, "models/item", i + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/" + i}})
    for e in ("dire_wolf", "harpy", "basilisk", "mimic", "gargoyle", "fairy"):
        save_json(os.path.join(A, "models/item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  DADOS: loot, receitas, spawns
# =====================================================================================
def drop(item, lo, hi, looting=True, conditions=None):
    fn = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]
    if looting:
        fn.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                   "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}})
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": item, "functions": fn}]}
    if conditions:
        pool["conditions"] = conditions
    return pool


def chance(p, per_level=0.02):
    return [{"condition": "minecraft:killed_by_player"},
            {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
             "unenchanted_chance": p, "enchanted_chance": {"type": "minecraft:linear", "base": p + per_level,
                                                           "per_level_above_first": per_level}}]


def loot(ident, pools):
    save_json(os.path.join(D, "loot_table/entities", ident + ".json"),
              {"type": "minecraft:entity", "pools": pools, "random_sequence": "isekai_mahou:entities/" + ident})


def data():
    M = "isekai_mahou:"
    loot("dire_wolf", [drop(M + "dire_wolf_pelt", 1.0, 2.0), drop("minecraft:bone", 0.0, 2.0)])
    loot("harpy", [drop(M + "harpy_feather", 1.0, 3.0), drop("minecraft:feather", 0.0, 2.0)])
    loot("basilisk", [drop(M + "basilisk_scale", 1.0, 3.0),
                      drop(M + "basilisk_eye", 1.0, 1.0, looting=False, conditions=chance(0.12, 0.04))])
    # Mimico: "bau" com loot bom
    treasure = {"rolls": {"type": "minecraft:uniform", "min": 2.0, "max": 4.0}, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 20,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2.0, "max": 6.0}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 20,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2.0, "max": 5.0}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 15,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 4.0}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 7,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:golden_apple", "weight": 8},
        {"type": "minecraft:item", "name": "minecraft:book", "weight": 10,
         "functions": [{"function": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}]},
        {"type": "minecraft:item", "name": M + "mana_crystal", "weight": 12,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}, "add": False}]},
        {"type": "minecraft:item", "name": M + "essence_arcane", "weight": 6},
        {"type": "minecraft:item", "name": "minecraft:name_tag", "weight": 4},
        {"type": "minecraft:item", "name": "minecraft:saddle", "weight": 4},
    ]}
    loot("mimic", [drop(M + "mimic_tooth", 1.0, 3.0), treasure])
    loot("gargoyle", [drop(M + "gargoyle_fragment", 1.0, 3.0), drop("minecraft:cobblestone", 1.0, 2.0, looting=False),
                      drop(M + "essence_earth", 1.0, 1.0, looting=False, conditions=chance(0.1, 0.03))])
    loot("fairy", [drop(M + "fairy_dust", 1.0, 2.0)])

    rec = os.path.join(D, "recipe")
    save_json(os.path.join(rec, "dire_wolf_pelt_to_leather.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": M + "dire_wolf_pelt"}], "result": {"id": "minecraft:leather", "count": 3}})
    save_json(os.path.join(rec, "harpy_feather_to_feathers.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": M + "harpy_feather"}], "result": {"id": "minecraft:feather", "count": 3}})
    save_json(os.path.join(rec, "saddle_from_dire_wolf_pelt.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PPP", "S S", " I "],
        "key": {"P": {"item": M + "dire_wolf_pelt"}, "S": {"item": "minecraft:string"}, "I": {"item": "minecraft:iron_ingot"}},
        "result": {"id": "minecraft:saddle", "count": 1}})

    bm = os.path.join(D, "neoforge/biome_modifier")

    def spawns(name, biomes, ident, weight, lo, hi):
        save_json(os.path.join(bm, "creatures_c_" + name + ".json"), {
            "type": "neoforge:add_spawns", "biomes": biomes,
            "spawners": [{"type": M + ident, "weight": weight, "minCount": lo, "maxCount": hi}]})

    mountains = {"type": "neoforge:or", "values": ["#minecraft:is_mountain", "#c:is_windswept"]}
    spawns("dire_wolf_taiga", "#minecraft:is_taiga", "dire_wolf", 8, 2, 4)
    spawns("dire_wolf_forest", "#minecraft:is_forest", "dire_wolf", 3, 1, 2)
    spawns("harpy", mountains, "harpy", 12, 1, 3)
    spawns("basilisk_desert", {"type": "neoforge:or", "values": ["#c:is_desert", "#minecraft:is_badlands"]}, "basilisk", 10, 1, 1)
    spawns("basilisk_cave", "#c:is_cave", "basilisk", 5, 1, 1)
    spawns("mimic", "#minecraft:is_overworld", "mimic", 3, 1, 1)
    spawns("gargoyle", mountains, "gargoyle", 6, 1, 2)
    spawns("fairy", "#c:is_floral", "fairy", 8, 2, 3)


if __name__ == "__main__":
    dire_wolf()
    harpy()
    basilisk()
    mimic()
    gargoyle()
    fairy()
    items()
    data()
    print("creatures_c: assets gerados")
