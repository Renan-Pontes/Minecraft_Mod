"""
Modelos GeckoLib do modulo nether_citadel (Lorde Demonio, Cavaleiro Infernal, Sucubo Feiticeira):
.geo.json com box UV empacotado automaticamente, textura pintada por face (2-3 tons + ruido) e glowmask,
e animacoes escritas por keyframes.

Importado por tools/gen/nether_citadel.py (nao rodar sozinho).
"""
import json
import os
import random

from pixelart import Canvas, hex_rgba, lighten, darken

CLEAR = (0, 0, 0, 0)


def H(h):
    return h if isinstance(h, tuple) else hex_rgba(h)


class Cube:
    def __init__(self, origin, size, color, deco=None, glow=False, inflate=0.0, rotation=None, pivot=None):
        self.origin, self.size, self.color = origin, size, H(color)
        self.deco, self.glow, self.inflate = deco, glow, inflate
        self.rotation, self.pivot = rotation, pivot
        self.uv = None


class Face:
    """Regiao de uma face na textura (para decoracoes: olhos, frisos, rachaduras)."""

    def __init__(self, m, x, y, w, h):
        self.m, self.x, self.y, self.w, self.h = m, x, y, w, h

    def set(self, fx, fy, c, glow=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, H(c))
            if glow:
                self.m.glow.set(self.x + fx, self.y + fy, H(c))

    def rect(self, x0, y0, x1, y1, c, glow=False):
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                self.set(xx, yy, c, glow)


class Model:
    def __init__(self, ident, tw, th):
        self.ident, self.tw, self.th = ident, tw, th
        self.bones = []
        self.rnd = random.Random("nether_citadel/" + ident)

    def bone(self, name, parent, pivot, cubes=(), rotation=None):
        self.bones.append({"name": name, "parent": parent, "pivot": pivot, "cubes": list(cubes), "rotation": rotation})

    # ---------------------------------------------------------------- empacotamento do box UV
    def pack(self):
        cubes = [c for b in self.bones for c in b["cubes"]]
        order = sorted(cubes, key=lambda c: -(c.size[2] + c.size[1]))
        x = y = row_h = 0
        for c in order:
            w, h, d = c.size
            bw, bh = 2 * (d + w), d + h
            if x + bw > self.tw:
                x, y, row_h = 0, y + row_h, 0
            c.uv = (x, y)
            x += bw
            row_h = max(row_h, bh)
        if y + row_h > self.th:
            raise SystemExit(f"textura pequena demais para {self.ident}: precisa de {y + row_h} px de altura")

    # ---------------------------------------------------------------- pintura
    def paint(self):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for b in self.bones:
            for c in b["cubes"]:
                self.paint_cube(c)

    def paint_cube(self, c):
        u, v = c.uv
        w, h, d = c.size
        base = c.color
        top, bottom = lighten(base, 0.18), darken(base, 0.35)

        def fill(x0, y0, fw, fh, col, grad=True):
            for yy in range(fh):
                for xx in range(fw):
                    k = col
                    if grad and fh > 2:
                        k = darken(col, 0.22 * yy / (fh - 1))
                    n = (self.rnd.random() - 0.5) * 0.12
                    k = lighten(k, n) if n > 0 else darken(k, -n)
                    if xx == 0 or xx == fw - 1:
                        k = darken(k, 0.12)
                    self.tex.set(x0 + xx, y0 + yy, k)
                    if c.glow:
                        self.glow.set(x0 + xx, y0 + yy, k)

        fill(u + d, v, w, d, top, grad=False)
        fill(u + d + w, v, w, d, bottom, grad=False)
        fill(u, v + d, d, h, darken(base, 0.08))
        fill(u + d, v + d, w, h, base)
        fill(u + d + w, v + d, d, h, darken(base, 0.08))
        fill(u + 2 * d + w, v + d, w, h, darken(base, 0.15))
        if c.deco:
            faces = {
                "front": Face(self, u + d, v + d, w, h), "back": Face(self, u + 2 * d + w, v + d, w, h),
                "right": Face(self, u, v + d, d, h), "left": Face(self, u + d + w, v + d, d, h),
                "top": Face(self, u + d, v, w, d), "bottom": Face(self, u + d + w, v, w, d),
            }
            c.deco(faces, c)

    # ---------------------------------------------------------------- saida
    def geo(self):
        bones = []
        for b in self.bones:
            o = {"name": b["name"], "pivot": b["pivot"]}
            if b["parent"]:
                o["parent"] = b["parent"]
            if b["rotation"]:
                o["rotation"] = b["rotation"]
            if b["cubes"]:
                cl = []
                for c in b["cubes"]:
                    e = {"origin": c.origin, "size": c.size, "uv": list(c.uv)}
                    if c.inflate:
                        e["inflate"] = c.inflate
                    if c.rotation:
                        e["rotation"] = c.rotation
                        e["pivot"] = c.pivot
                    cl.append(e)
                o["cubes"] = cl
            bones.append(o)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 2, 0]},
            "bones": bones}]}

    def save(self, assets):
        self.pack()
        self.paint()
        os.makedirs(os.path.join(assets, "geo/entity"), exist_ok=True)
        with open(os.path.join(assets, f"geo/entity/{self.ident}.geo.json"), "w", encoding="utf-8") as f:
            json.dump(self.geo(), f, indent=1)
        self.tex.save(os.path.join(assets, f"textures/entity/{self.ident}.png"))
        self.glow.save(os.path.join(assets, f"textures/entity/{self.ident}_glowmask.png"))


# =====================================================================================
#  Animacoes
# =====================================================================================
def kf(points):
    """{tempo: [x,y,z]} com chaves em texto."""
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t else "0.0": v for t, v in points}


def anim(length, loop, bones):
    out = {"animation_length": length, "bones": {}}
    if loop == "hold":
        out["loop"] = "hold_on_last_frame"
    else:
        out["loop"] = bool(loop)
    for name, chans in bones.items():
        out["bones"][name] = {ch: kf(pts) for ch, pts in chans.items()}
    return out


def save_anims(assets, ident, anims):
    data = {"format_version": "1.8.0", "animations": {f"animation.{ident}.{k}": v for k, v in anims.items()}}
    os.makedirs(os.path.join(assets, "animations/entity"), exist_ok=True)
    with open(os.path.join(assets, f"animations/entity/{ident}.animation.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


# =====================================================================================
#  LORDE DEMONIO
# =====================================================================================
SKIN = "#7A1622"
SKIN_D = "#4E0C16"
ARMOR = "#25202A"
ARMOR_L = "#3A3340"
GOLD = "#D9A632"
LAVA = "#FF6A1E"
EYE = "#FFD84A"
HORN = "#2A2024"
CAPE = "#5A0C14"
BLADE = "#3B3440"
BLADE_EDGE = "#FF4A2A"
WING = "#3C0E1A"
WING_BONE = "#1E1418"


def deco_lord_head(f, c):
    fr = f["front"]
    fr.rect(1, 3, 3, 3, EYE, True)
    fr.rect(5, 3, 7, 3, EYE, True)
    fr.rect(0, 2, 8, 2, darken(H(SKIN_D), 0.2))
    fr.rect(2, 6, 6, 6, "#1A0508")
    fr.set(3, 7, "#E8E0D0")
    fr.set(5, 7, "#E8E0D0")


def deco_lava_cracks(f, c):
    rnd = random.Random(str(c.origin))
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        x = rnd.randrange(max(1, fc.w))
        for y in range(fc.h):
            if rnd.random() < 0.7:
                fc.set(x, y, LAVA, True)
            x = max(0, min(fc.w - 1, x + rnd.choice((-1, 0, 0, 1))))


def deco_chest(f, c):
    deco_lava_cracks(f, c)
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, 0, GOLD)
    cx = fr.w // 2
    for i in range(4):
        fr.set(cx - 1 - i, 3 + i, GOLD)
        fr.set(cx + i, 3 + i, GOLD)
    fr.rect(cx - 1, 6, cx, 8, LAVA, True)


def deco_belt(f, c):
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, 2, f[name].w - 1, 3, GOLD)
    f["front"].rect(f["front"].w // 2 - 1, 1, f["front"].w // 2, 4, "#B5121E", True)


def deco_trim(f, c):
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, f[name].h - 1, f[name].w - 1, f[name].h - 1, GOLD)


def deco_blade(f, c):
    for name in ("left", "right", "top", "bottom"):
        fc = f[name]
        if fc.w >= fc.h:
            fc.rect(0, 0, fc.w - 1, 0, BLADE_EDGE, True)
        else:
            fc.rect(0, 0, 0, fc.h - 1, BLADE_EDGE, True)
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, fr.h - 1, BLADE_EDGE, True)


def deco_wing(f, c):
    for name in ("front", "back"):
        fc = f[name]
        for i in range(0, fc.w, 5):
            for y in range(fc.h):
                fc.set(i, y, WING_BONE)
        fc.rect(0, 0, fc.w - 1, 0, WING_BONE)


def demon_lord():
    m = Model("demon_lord", 128, 128)
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-3.5, 22, 0], [
        Cube([-6, 5, -3], [5, 17, 6], SKIN_D),
        Cube([-6.5, 0, -3.5], [6, 6, 7], ARMOR, deco_trim)])
    m.bone("left_leg", "root", [3.5, 22, 0], [
        Cube([1, 5, -3], [5, 17, 6], SKIN_D),
        Cube([0.5, 0, -3.5], [6, 6, 7], ARMOR, deco_trim)])
    m.bone("body", "root", [0, 22, 0], [
        Cube([-6, 22, -3.5], [12, 6, 7], ARMOR, deco_belt),
        Cube([-8, 28, -4.5], [16, 14, 9], ARMOR_L, deco_chest),
        Cube([-6, 16, -4], [12, 7, 8], "#2E1418", deco_trim)])
    m.bone("cape", "body", [0, 42, 4.5], [Cube([-7, 13, 4.5], [14, 29, 1], CAPE, deco_trim)])
    m.bone("head", "body", [0, 42, 0], [
        Cube([-4.5, 42, -4.5], [9, 9, 9], SKIN, deco_lord_head),
        Cube([-5, 49, -5], [10, 2, 10], GOLD),
        Cube([4.5, 47, -1.5], [3, 3, 3], HORN),
        Cube([6, 49, -1], [2, 5, 2], HORN),
        Cube([6.5, 54, -0.5], [1, 4, 1], "#E8D8C0"),
        Cube([-7.5, 47, -1.5], [3, 3, 3], HORN),
        Cube([-8, 49, -1], [2, 5, 2], HORN),
        Cube([-7.5, 54, -0.5], [1, 4, 1], "#E8D8C0")])
    m.bone("right_arm", "body", [-9, 40, 0], [
        Cube([-14, 36, -4], [6, 6, 8], ARMOR, deco_trim),
        Cube([-12, 44 - 2, -1], [2, 4, 2], HORN),
        Cube([-12.5, 22, -2.5], [5, 15, 5], SKIN)])
    m.bone("sword", "right_arm", [-10, 23, 0], [
        Cube([-11, 21.5, -3], [2, 2, 6], "#3A1A10"),
        Cube([-13, 20.5, -4], [6, 4, 1], GOLD),
        Cube([-10.5, 21, -26], [1, 3, 22], BLADE, deco_blade)])
    m.bone("left_arm", "body", [9, 40, 0], [
        Cube([8, 36, -4], [6, 6, 8], ARMOR, deco_trim),
        Cube([10, 42, -1], [2, 4, 2], HORN),
        Cube([7.5, 22, -2.5], [5, 15, 5], SKIN)])
    m.bone("left_wing", "body", [3, 38, 4.5], [
        Cube([3, 40, 4.5], [22, 2, 2], WING_BONE),
        Cube([4, 22, 5], [20, 18, 1], WING, deco_wing)])
    m.bone("right_wing", "body", [-3, 38, 4.5], [
        Cube([-25, 40, 4.5], [22, 2, 2], WING_BONE),
        Cube([-24, 22, 5], [20, 18, 1], WING, deco_wing)])
    return m


def demon_lord_anims():
    wings = {"left_wing": {"rotation": [(0, [0, -15, 0]), (0.5, [0, 10, 0]), (1.0, [0, -15, 0])]},
             "right_wing": {"rotation": [(0, [0, 15, 0]), (0.5, [0, -10, 0]), (1.0, [0, 15, 0])]}}
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.5, [0, 0.4, 0]), (3.0, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [-8, 0, 3]), (1.5, [-10, 0, 5]), (3.0, [-8, 0, 3])]},
            "left_arm": {"rotation": [(0, [0, 0, -3]), (1.5, [0, 0, -5]), (3.0, [0, 0, -3])]},
            "cape": {"rotation": [(0, [4, 0, 0]), (1.5, [8, 0, 0]), (3.0, [4, 0, 0])]},
            "left_wing": {"rotation": [(0, [0, -15, 0]), (1.5, [0, 0, 0]), (3.0, [0, -15, 0])]},
            "right_wing": {"rotation": [(0, [0, 15, 0]), (1.5, [0, 0, 0]), (3.0, [0, 15, 0])]}}
    walk = {"right_leg": {"rotation": [(0, [25, 0, 0]), (0.6, [-25, 0, 0]), (1.2, [25, 0, 0])]},
            "left_leg": {"rotation": [(0, [-25, 0, 0]), (0.6, [25, 0, 0]), (1.2, [-25, 0, 0])]},
            "right_arm": {"rotation": [(0, [-25, 0, 3]), (0.6, [5, 0, 3]), (1.2, [-25, 0, 3])]},
            "left_arm": {"rotation": [(0, [15, 0, -3]), (0.6, [-15, 0, -3]), (1.2, [15, 0, -3])]},
            "body": {"position": [(0, [0, 0, 0]), (0.3, [0, 0.6, 0]), (0.6, [0, 0, 0]), (0.9, [0, 0.6, 0]), (1.2, [0, 0, 0])]},
            "cape": {"rotation": [(0, [12, 0, 0]), (0.6, [18, 0, 0]), (1.2, [12, 0, 0])]}}
    walk.update({k: {"rotation": [(t * 1.2, v) for t, v in ch["rotation"]]} for k, ch in wings.items()})
    sleep = {"head": {"rotation": [(0, [28, 0, 0]), (2.0, [32, 0, 0]), (4.0, [28, 0, 0])]},
             "body": {"rotation": [(0, [8, 0, 0]), (4.0, [8, 0, 0])]},
             "right_arm": {"rotation": [(0, [-40, 0, 10]), (4.0, [-40, 0, 10])]},
             "left_arm": {"rotation": [(0, [-30, 0, -10]), (4.0, [-30, 0, -10])]}}
    intro = {"head": {"rotation": [(0, [30, 0, 0]), (1.0, [0, 0, 0]), (1.6, [-20, 0, 0]), (3.0, [0, 0, 0])]},
             "right_arm": {"rotation": [(0, [-40, 0, 10]), (1.2, [-170, 0, 5]), (2.2, [-170, 0, 5]), (3.0, [-10, 0, 3])]},
             "left_arm": {"rotation": [(0, [-30, 0, -10]), (1.4, [-20, 0, -60]), (3.0, [0, 0, -3])]},
             "body": {"rotation": [(0, [8, 0, 0]), (1.6, [-12, 0, 0]), (3.0, [0, 0, 0])]}}
    death = {"body": {"rotation": [(0, [0, 0, 0]), (0.8, [25, 0, 0]), (2.0, [55, 0, 8])],
                      "position": [(0, [0, 0, 0]), (0.8, [0, -6, 0]), (2.0, [0, -14, 0])]},
             "right_leg": {"rotation": [(0, [0, 0, 0]), (0.8, [-80, 0, 0]), (2.0, [-90, 0, 0])]},
             "left_leg": {"rotation": [(0, [0, 0, 0]), (0.8, [-80, 0, 0]), (2.0, [-90, 0, 0])]},
             "head": {"rotation": [(0, [0, 0, 0]), (2.0, [40, 0, 0])]},
             "right_arm": {"rotation": [(0, [-10, 0, 0]), (2.0, [20, 0, 30])]}}
    cleave = {"right_arm": {"rotation": [(0, [-8, 0, 3]), (0.45, [-160, 30, 0]), (0.6, [20, -30, 0]), (0.85, [-8, 0, 3])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.45, [-5, 30, 0]), (0.6, [8, -30, 0]), (0.85, [0, 0, 0])]}}
    thrust = {"right_arm": {"rotation": [(0, [-8, 0, 3]), (0.6, [-80, 0, 0]), (0.75, [-95, 0, 0]), (1.0, [-8, 0, 3])],
                            "position": [(0, [0, 0, 0]), (0.6, [0, 0, 4]), (0.75, [0, 0, -6]), (1.0, [0, 0, 0])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.6, [0, 20, 0]), (0.75, [10, -10, 0]), (1.0, [0, 0, 0])]}}
    hellfire = {"right_arm": {"rotation": [(0, [-8, 0, 3]), (0.5, [-170, 0, 10]), (0.8, [-20, 0, 3]), (1.0, [-8, 0, 3])]},
                "left_arm": {"rotation": [(0, [0, 0, -3]), (0.5, [-170, 0, -10]), (0.8, [-20, 0, -3]), (1.0, [0, 0, -3])]},
                "head": {"rotation": [(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (1.0, [0, 0, 0])]}}
    summon = {"right_arm": {"rotation": [(0, [-8, 0, 3]), (0.6, [-30, 0, 85]), (1.2, [-8, 0, 3])]},
              "left_arm": {"rotation": [(0, [0, 0, -3]), (0.6, [-30, 0, -85]), (1.2, [0, 0, -3])]},
              "head": {"rotation": [(0, [0, 0, 0]), (0.6, [-30, 0, 0]), (1.2, [0, 0, 0])]}}
    transform = {"body": {"rotation": [(0, [0, 0, 0]), (1.0, [-22, 0, 0]), (2.0, [0, 0, 0])]},
                 "right_arm": {"rotation": [(0, [-8, 0, 3]), (1.0, [-20, 0, 100]), (2.0, [-8, 0, 3])]},
                 "left_arm": {"rotation": [(0, [0, 0, -3]), (1.0, [-20, 0, -100]), (2.0, [0, 0, -3])]},
                 "left_wing": {"rotation": [(0, [0, 60, 0]), (1.0, [0, -30, 0]), (2.0, [0, -10, 0])]},
                 "right_wing": {"rotation": [(0, [0, -60, 0]), (1.0, [0, 30, 0]), (2.0, [0, 10, 0])]},
                 "head": {"rotation": [(0, [0, 0, 0]), (1.0, [-35, 0, 0]), (2.0, [0, 0, 0])]}}
    meteor = {"right_arm": {"rotation": [(0, [-8, 0, 3]), (0.5, [-185, 0, 0]), (0.85, [-185, 0, 0]), (1.0, [-8, 0, 3])]},
              "head": {"rotation": [(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (1.0, [0, 0, 0])]}}
    return {"idle": anim(3.0, True, idle), "walk": anim(1.2, True, walk), "sleep": anim(4.0, True, sleep),
            "intro": anim(3.0, False, intro), "death": anim(2.0, "hold", death), "cleave": anim(0.85, False, cleave),
            "thrust": anim(1.0, False, thrust), "hellfire": anim(1.0, False, hellfire), "summon": anim(1.2, False, summon),
            "transform": anim(2.0, False, transform), "meteor": anim(1.0, False, meteor)}


# =====================================================================================
#  CAVALEIRO INFERNAL
# =====================================================================================
K_ARMOR = "#2B2730"
K_ARMOR_L = "#46404E"
K_TRIM = "#A8321E"
K_VISOR = "#FF7A2A"


def deco_k_helm(f, c):
    fr = f["front"]
    fr.rect(0, 3, fr.w - 1, 4, "#0E0B10")
    fr.rect(1, 3, 2, 3, K_VISOR, True)
    fr.rect(5, 3, 6, 3, K_VISOR, True)
    fr.rect(3, 5, 4, 7, "#0E0B10")
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, 0, f[name].w - 1, 0, K_TRIM)


def deco_k_chest(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, 0, K_TRIM)
    fr.rect(fr.w // 2 - 1, 2, fr.w // 2, 8, K_TRIM)
    fr.rect(fr.w // 2 - 2, 4, fr.w // 2 + 1, 5, K_TRIM)
    fr.set(fr.w // 2 - 1, 5, K_VISOR, True)
    fr.set(fr.w // 2, 5, K_VISOR, True)
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, f[name].h - 3, f[name].w - 1, f[name].h - 2, "#3A1A12")


def deco_k_shield(f, c):
    for name in ("left", "right"):
        fc = f[name]
        fc.rect(0, 0, fc.w - 1, 0, K_TRIM)
        fc.rect(0, fc.h - 1, fc.w - 1, fc.h - 1, K_TRIM)
        fc.rect(0, 0, 0, fc.h - 1, K_TRIM)
        fc.rect(fc.w - 1, 0, fc.w - 1, fc.h - 1, K_TRIM)
        cx, cy = fc.w // 2, fc.h // 2
        for i in range(3):
            fc.set(cx - i, cy - 3 + i, K_VISOR, True)
            fc.set(cx + i - 1, cy - 3 + i, K_VISOR, True)
        fc.rect(cx - 1, cy, cx, cy + 3, K_TRIM)


def demon_knight():
    m = Model("demon_knight", 128, 64)
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-2, 13, 0], [Cube([-4, 0, -2], [4, 13, 4], K_ARMOR, deco_trim)])
    m.bone("left_leg", "root", [2, 13, 0], [Cube([0, 0, -2], [4, 13, 4], K_ARMOR, deco_trim)])
    m.bone("body", "root", [0, 13, 0], [Cube([-5, 13, -3], [10, 13, 6], K_ARMOR_L, deco_k_chest)])
    m.bone("head", "body", [0, 26, 0], [
        Cube([-4, 26, -4], [8, 8, 8], K_ARMOR, deco_k_helm),
        Cube([4, 31, -1], [2, 2, 2], HORN), Cube([5, 33, -1], [2, 4, 2], HORN),
        Cube([-6, 31, -1], [2, 2, 2], HORN), Cube([-7, 33, -1], [2, 4, 2], HORN)])
    m.bone("right_arm", "body", [-6, 24, 0], [
        Cube([-10, 21, -3], [5, 4, 6], K_ARMOR, deco_trim),
        Cube([-9, 12, -2], [4, 10, 4], K_ARMOR_L)])
    m.bone("sword", "right_arm", [-7, 12, 0], [
        Cube([-8, 11, -3], [2, 2, 4], "#3A1A10"),
        Cube([-9.5, 10, -4], [5, 4, 1], K_TRIM),
        Cube([-7.5, 10.5, -18], [1, 3, 14], BLADE, deco_blade)])
    m.bone("left_arm", "body", [6, 24, 0], [
        Cube([5, 21, -3], [5, 4, 6], K_ARMOR, deco_trim),
        Cube([5, 12, -2], [4, 10, 4], K_ARMOR_L)])
    m.bone("shield", "left_arm", [9, 16, 0], [Cube([9, 8, -6], [1, 15, 12], "#1E1A22", deco_k_shield)])
    return m


def demon_knight_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.25, [0, 0.3, 0]), (2.5, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [-10, 0, 0]), (1.25, [-14, 0, 2]), (2.5, [-10, 0, 0])]},
            "left_arm": {"rotation": [(0, [-20, 0, 0]), (1.25, [-22, 0, -2]), (2.5, [-20, 0, 0])]}}
    walk = {"right_leg": {"rotation": [(0, [28, 0, 0]), (0.5, [-28, 0, 0]), (1.0, [28, 0, 0])]},
            "left_leg": {"rotation": [(0, [-28, 0, 0]), (0.5, [28, 0, 0]), (1.0, [-28, 0, 0])]},
            "right_arm": {"rotation": [(0, [-25, 0, 0]), (0.5, [0, 0, 0]), (1.0, [-25, 0, 0])]},
            "left_arm": {"rotation": [(0, [-20, 0, 0]), (0.5, [-30, 0, 0]), (1.0, [-20, 0, 0])]}}
    attack = {"right_arm": {"rotation": [(0, [-10, 0, 0]), (0.2, [-150, 0, -8]), (0.35, [-10, 0, 4]), (0.5, [-10, 0, 0])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.2, [-4, 14, 0]), (0.35, [6, -14, 0]), (0.5, [0, 0, 0])]}}
    charge = {"body": {"rotation": [(0, [0, 0, 0]), (0.4, [20, 0, 0]), (1.0, [0, 0, 0])]},
              "right_arm": {"rotation": [(0, [-10, 0, 0]), (0.4, [-90, 0, 0]), (1.0, [-10, 0, 0])]},
              "left_arm": {"rotation": [(0, [-20, 0, 0]), (0.4, [-70, 0, 0]), (1.0, [-20, 0, 0])]}}
    block = {"left_arm": {"rotation": [(0, [-20, 0, 0]), (0.1, [-70, 30, 0]), (0.4, [-20, 0, 0])]}}
    return {"idle": anim(2.5, True, idle), "walk": anim(1.0, True, walk), "attack": anim(0.5, False, attack),
            "charge": anim(1.0, False, charge), "block": anim(0.4, False, block)}


# =====================================================================================
#  SUCUBO FEITICEIRA
# =====================================================================================
S_SKIN = "#C86A8A"
S_CLOTH = "#2A0E24"
S_HAIR = "#4A1240"
S_EYE = "#FF5AD8"
S_WING = "#5A1838"


def deco_s_head(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, 1, S_HAIR)
    fr.set(1, 3, S_EYE, True)
    fr.set(5, 3, S_EYE, True)
    fr.set(3, 5, "#8A2A4A")
    fr.rect(0, 0, 0, 4, S_HAIR)
    fr.rect(fr.w - 1, 0, fr.w - 1, 4, S_HAIR)


def deco_s_torso(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, 2, S_CLOTH)
    fr.rect(0, fr.h - 2, fr.w - 1, fr.h - 1, S_CLOTH)
    fr.set(fr.w // 2, 1, S_EYE, True)


def deco_s_hips(f, c):
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, 0, f[name].w - 1, 0, "#C04A9A")


def demon_succubus():
    m = Model("demon_succubus", 64, 64)
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-1.5, 12, 0], [Cube([-3, 0, -1.5], [3, 12, 3], S_CLOTH, deco_trim)])
    m.bone("left_leg", "root", [1.5, 12, 0], [Cube([0, 0, -1.5], [3, 12, 3], S_CLOTH, deco_trim)])
    m.bone("body", "root", [0, 12, 0], [
        Cube([-3.5, 12, -2], [7, 4, 4], S_CLOTH, deco_s_hips),
        Cube([-3, 16, -1.5], [6, 8, 3], S_SKIN, deco_s_torso)])
    m.bone("head", "body", [0, 24, 0], [
        Cube([-3.5, 24, -3.5], [7, 7, 7], S_SKIN, deco_s_head),
        Cube([-4, 20, 1], [8, 12, 3], S_HAIR),
        Cube([-4, 30, -4], [8, 2, 8], S_HAIR),
        Cube([2.5, 30, -1], [1, 3, 1], HORN), Cube([3, 32, 0], [1, 2, 2], HORN),
        Cube([-3.5, 30, -1], [1, 3, 1], HORN), Cube([-4, 32, 0], [1, 2, 2], HORN)])
    m.bone("right_arm", "body", [-3.5, 23, 0], [Cube([-5, 13, -1], [2, 10, 2], S_SKIN)])
    m.bone("left_arm", "body", [3.5, 23, 0], [Cube([3, 13, -1], [2, 10, 2], S_SKIN)])
    m.bone("left_wing", "body", [1, 21, 1.5], [Cube([1, 13, 1.5], [10, 10, 1], S_WING, deco_wing)])
    m.bone("right_wing", "body", [-1, 21, 1.5], [Cube([-11, 13, 1.5], [10, 10, 1], S_WING, deco_wing)])
    m.bone("tail", "body", [0, 13, 2], [
        Cube([-0.5, 12.5, 2], [1, 1, 8], S_CLOTH),
        Cube([-1.5, 11.5, 10], [3, 3, 1], "#C04A9A", glow=True)])
    return m


def demon_succubus_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.0, [0, 0.4, 0]), (2.0, [0, 0, 0])]},
            "left_wing": {"rotation": [(0, [0, -20, 0]), (1.0, [0, 15, 0]), (2.0, [0, -20, 0])]},
            "right_wing": {"rotation": [(0, [0, 20, 0]), (1.0, [0, -15, 0]), (2.0, [0, 20, 0])]},
            "tail": {"rotation": [(0, [10, -15, 0]), (1.0, [5, 15, 0]), (2.0, [10, -15, 0])]},
            "right_arm": {"rotation": [(0, [0, 0, 6]), (1.0, [0, 0, 10]), (2.0, [0, 0, 6])]},
            "left_arm": {"rotation": [(0, [0, 0, -6]), (1.0, [0, 0, -10]), (2.0, [0, 0, -6])]}}
    walk = {"right_leg": {"rotation": [(0, [25, 0, 0]), (0.45, [-25, 0, 0]), (0.9, [25, 0, 0])]},
            "left_leg": {"rotation": [(0, [-25, 0, 0]), (0.45, [25, 0, 0]), (0.9, [-25, 0, 0])]},
            "right_arm": {"rotation": [(0, [-20, 0, 5]), (0.45, [20, 0, 5]), (0.9, [-20, 0, 5])]},
            "left_arm": {"rotation": [(0, [20, 0, -5]), (0.45, [-20, 0, -5]), (0.9, [20, 0, -5])]},
            "tail": {"rotation": [(0, [10, -20, 0]), (0.45, [10, 20, 0]), (0.9, [10, -20, 0])]},
            "left_wing": {"rotation": [(0, [0, -10, 0]), (0.45, [0, 10, 0]), (0.9, [0, -10, 0])]},
            "right_wing": {"rotation": [(0, [0, 10, 0]), (0.45, [0, -10, 0]), (0.9, [0, 10, 0])]}}
    cast = {"right_arm": {"rotation": [(0, [0, 0, 6]), (0.35, [-95, 0, 0]), (0.7, [0, 0, 6])]},
            "body": {"rotation": [(0, [0, 0, 0]), (0.35, [0, 15, 0]), (0.7, [0, 0, 0])]}}
    charm = {"right_arm": {"rotation": [(0, [0, 0, 6]), (0.5, [-80, -20, 0]), (1.0, [0, 0, 6])]},
             "left_arm": {"rotation": [(0, [0, 0, -6]), (0.5, [-80, 20, 0]), (1.0, [0, 0, -6])]},
             "head": {"rotation": [(0, [0, 0, 0]), (0.5, [5, 0, 15]), (1.0, [0, 0, 0])]},
             "left_wing": {"rotation": [(0, [0, -20, 0]), (0.5, [0, 35, 0]), (1.0, [0, -20, 0])]},
             "right_wing": {"rotation": [(0, [0, 20, 0]), (0.5, [0, -35, 0]), (1.0, [0, 20, 0])]}}
    return {"idle": anim(2.0, True, idle), "walk": anim(0.9, True, walk), "cast": anim(0.7, False, cast),
            "charm": anim(1.0, False, charm)}


def build_all(assets):
    for model, anims in ((demon_lord(), demon_lord_anims()), (demon_knight(), demon_knight_anims()),
                         (demon_succubus(), demon_succubus_anims())):
        model.save(assets)
        save_anims(assets, model.ident, anims)
        print("  modelo", model.ident)
