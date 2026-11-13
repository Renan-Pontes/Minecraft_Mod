"""
Modelos GeckoLib do modulo depths (Verme-Rei das Profundezas, Verme Cavador, Explorador Perdido):
.geo.json com box UV empacotado automaticamente, textura pintada por face (2-3 tons + ruido) e glowmask,
e animacoes escritas por keyframes.

Importado por tools/gen/depths.py (nao rodar sozinho).
"""
import json
import os
import random

from pixelart import Canvas, hex_rgba, lighten, darken


def H(h):
    return h if isinstance(h, tuple) else hex_rgba(h)


class Cube:
    def __init__(self, origin, size, color, deco=None, glow=False, inflate=0.0, rotation=None, pivot=None):
        self.origin, self.size, self.color = origin, size, H(color)
        self.deco, self.glow, self.inflate = deco, glow, inflate
        self.rotation, self.pivot = rotation, pivot
        self.uv = None


class Face:
    """Regiao de uma face na textura (decoracoes: dentes, placas, rachaduras)."""

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
    def __init__(self, ident, tw, th, bounds=(5, 5, 2)):
        self.ident, self.tw, self.th, self.bounds = ident, tw, th, bounds
        self.bones = []
        self.rnd = random.Random("depths/" + ident)

    def bone(self, name, parent, pivot, cubes=(), rotation=None):
        self.bones.append({"name": name, "parent": parent, "pivot": pivot, "cubes": list(cubes), "rotation": rotation})

    def pack(self):
        cubes = [c for b in self.bones for c in b["cubes"]]
        order = sorted(cubes, key=lambda c: -(c.size[2] + c.size[1]))
        x = y = row_h = 0
        for c in order:
            w, h, d = (int(round(v + 0.49)) for v in c.size)
            bw, bh = 2 * (d + w), d + h
            if x + bw > self.tw:
                x, y, row_h = 0, y + row_h, 0
            c.uv = (x, y)
            x += bw
            row_h = max(row_h, bh)
        if y + row_h > self.th:
            raise SystemExit(f"textura pequena demais para {self.ident}: precisa de {y + row_h} px de altura")

    def paint(self):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for b in self.bones:
            for c in b["cubes"]:
                self.paint_cube(c)

    def paint_cube(self, c):
        u, v = c.uv
        w, h, d = (int(round(s + 0.49)) for s in c.size)
        base = c.color
        top, bottom = lighten(base, 0.18), darken(base, 0.35)

        def fill(x0, y0, fw, fh, col, grad=True):
            for yy in range(fh):
                for xx in range(fw):
                    k = col
                    if grad and fh > 2:
                        k = darken(col, 0.22 * yy / (fh - 1))
                    n = (self.rnd.random() - 0.5) * 0.14
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
        bw, bh, boy = self.bounds
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": bw, "visible_bounds_height": bh, "visible_bounds_offset": [0, boy, 0]},
            "bones": bones}]}

    def save(self, assets):
        self.pack()
        self.paint()
        os.makedirs(os.path.join(assets, "geo/entity"), exist_ok=True)
        with open(os.path.join(assets, f"geo/entity/{self.ident}.geo.json"), "w", encoding="utf-8") as f:
            json.dump(self.geo(), f, indent=1)
        self.tex.save(os.path.join(assets, f"textures/entity/{self.ident}.png"))
        self.glow.save(os.path.join(assets, f"textures/entity/{self.ident}_glowmask.png"))


def kf(points):
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t else "0.0": v for t, v in points}


def anim(length, loop, bones):
    out = {"animation_length": length, "bones": {}}
    out["loop"] = "hold_on_last_frame" if loop == "hold" else bool(loop)
    for name, chans in bones.items():
        out["bones"][name] = {ch: kf(pts) for ch, pts in chans.items()}
    return out


def save_anims(assets, ident, anims):
    data = {"format_version": "1.8.0", "animations": {f"animation.{ident}.{k}": v for k, v in anims.items()}}
    os.makedirs(os.path.join(assets, "animations/entity"), exist_ok=True)
    with open(os.path.join(assets, f"animations/entity/{ident}.animation.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


# =====================================================================================
#  VERME-REI DAS PROFUNDEZAS
# =====================================================================================
FLESH = "#9A6450"
FLESH_D = "#6E4234"
CHITIN = "#4A3A30"
CHITIN_L = "#6A5646"
BELLY = "#C08A74"
TOOTH = "#EDE3C8"
MAW = "#3A0A10"
MANA = "#5AE0FF"
DIRT = "#5A4632"
STONE = "#6E6A66"


def deco_rings(f, c):
    """Aneis de musculo (faixas escuras horizontais) em todas as faces laterais."""
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        for y in range(2, fc.h, 5):
            fc.rect(0, y, fc.w - 1, y, FLESH_D)
        fc.rect(0, fc.h - 1, fc.w - 1, fc.h - 1, darken(H(FLESH_D), 0.2))


def deco_plate(f, c):
    """Placa de quitina com sulcos e cristais de mana incrustados."""
    rnd = random.Random(str(c.origin))
    for name in ("front", "back", "left", "right", "top"):
        fc = f[name]
        for x in range(0, fc.w, 4):
            for y in range(fc.h):
                fc.set(x, y, darken(H(CHITIN), 0.3))
        for _ in range(max(1, fc.w * fc.h // 40)):
            fc.set(rnd.randrange(max(1, fc.w)), rnd.randrange(max(1, fc.h)), MANA, True)


def deco_crystal(f, c):
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        fc.rect(0, 0, 0, fc.h - 1, lighten(H(MANA), 0.5), True)


def deco_jaw(f, c):
    """Mandibula: dentes brancos na face interna (as duas laterais e a de tras, por garantia) e ponta escura."""
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        for x in range(0, fc.w, 2):
            for y in range(fc.h - 2, max(fc.h - 10, 0), -3):
                fc.set(x, y, TOOTH)
                fc.set(x, y - 1, TOOTH)
        fc.rect(0, 0, fc.w - 1, 1, darken(H(CHITIN), 0.2))


def deco_maw(f, c):
    fc = f["top"]
    fc.rect(0, 0, fc.w - 1, fc.h - 1, MAW)
    cx, cy = fc.w // 2, fc.h // 2
    fc.rect(cx - 2, cy - 2, cx + 1, cy + 1, "#1A0206")
    for i in range(0, fc.w, 3):
        fc.set(i, 0, TOOTH)
        fc.set(i, fc.h - 1, TOOTH)
        fc.set(0, i, TOOTH)
        fc.set(fc.w - 1, i, TOOTH)


def deco_mound(f, c):
    rnd = random.Random(str(c.origin) + "m")
    for name in ("front", "back", "left", "right", "top"):
        fc = f[name]
        for _ in range(fc.w * fc.h // 5):
            fc.set(rnd.randrange(fc.w), rnd.randrange(fc.h), rnd.choice((STONE, "#4A3A28", "#7A6448")))


def worm_king():
    m = Model("depths_worm_king", 256, 256, bounds=(7, 9, 4))
    m.bone("root", None, [0, 0, 0])
    m.bone("mound", "root", [0, 0, 0], [
        Cube([-24, 0, -24], [48, 3, 10], DIRT, deco_mound),
        Cube([-24, 0, 14], [48, 3, 10], DIRT, deco_mound),
        Cube([-24, 0, -14], [10, 3, 28], DIRT, deco_mound),
        Cube([14, 0, -14], [10, 3, 28], DIRT, deco_mound),
        Cube([-18, 3, -18], [8, 3, 6], STONE, deco_mound),
        Cube([10, 3, 12], [7, 4, 6], STONE, deco_mound),
        Cube([12, 3, -16], [5, 2, 5], "#4A3A28", deco_mound),
        Cube([-16, 3, 10], [6, 2, 6], "#4A3A28", deco_mound)])
    m.bone("body", "root", [0, 0, 0])
    m.bone("seg1", "body", [0, 0, 0], [
        Cube([-14, 0, -14], [28, 18, 28], FLESH, deco_rings),
        Cube([-12, 2, 14], [24, 14, 3], CHITIN, deco_plate),
        Cube([-3, 9, 16], [5, 9, 5], MANA, deco_crystal, glow=True)])
    m.bone("seg2", "seg1", [0, 18, 0], [
        Cube([-12, 18, -12], [24, 16, 24], FLESH, deco_rings),
        Cube([-10, 20, 12], [20, 12, 3], CHITIN, deco_plate),
        Cube([3, 26, 14], [4, 7, 4], MANA, deco_crystal, glow=True),
        Cube([-13, 22, -6], [3, 10, 12], CHITIN_L, deco_plate)])
    m.bone("seg3", "seg2", [0, 34, 0], [
        Cube([-11, 34, -11], [22, 14, 22], FLESH, deco_rings),
        Cube([-9, 36, 11], [18, 10, 3], CHITIN, deco_plate),
        Cube([-6, 40, 13], [4, 6, 4], MANA, deco_crystal, glow=True),
        Cube([10, 36, -5], [3, 9, 10], CHITIN_L, deco_plate)])
    m.bone("seg4", "seg3", [0, 48, 0], [
        Cube([-10, 48, -10], [20, 12, 20], FLESH, deco_rings),
        Cube([-8, 50, 10], [16, 8, 3], CHITIN, deco_plate)])
    m.bone("head", "seg4", [0, 60, 0], [
        Cube([-12, 60, -12], [24, 8, 24], CHITIN_L, deco_plate),
        Cube([-9, 67, -9], [18, 1, 18], MAW, deco_maw)])
    m.bone("jaw_n", "head", [0, 68, -10], [Cube([-8, 68, -12], [16, 16, 4], CHITIN, deco_jaw)], rotation=[15, 0, 0])
    m.bone("jaw_s", "head", [0, 68, 10], [Cube([-8, 68, 8], [16, 16, 4], CHITIN, deco_jaw)], rotation=[-15, 0, 0])
    m.bone("jaw_e", "head", [10, 68, 0], [Cube([8, 68, -8], [4, 16, 16], CHITIN, deco_jaw)], rotation=[0, 0, 15])
    m.bone("jaw_w", "head", [-10, 68, 0], [Cube([-12, 68, -8], [4, 16, 16], CHITIN, deco_jaw)], rotation=[0, 0, -15])
    return m


def jaws(a):
    """Rotacao das 4 mandibulas para abrir a boca em 'a' graus (alem da pose base de 15)."""
    return {"jaw_n": [a, 0, 0], "jaw_s": [-a, 0, 0], "jaw_e": [0, 0, a], "jaw_w": [0, 0, -a]}


def jaw_track(points):
    """[(t, abertura)] -> canais de rotacao das 4 mandibulas."""
    out = {}
    for name in ("jaw_n", "jaw_s", "jaw_e", "jaw_w"):
        out[name] = {"rotation": [(t, jaws(a)[name]) for t, a in points]}
    return out


def worm_king_anims():
    idle = {"seg1": {"rotation": [(0, [0, 0, 0]), (1.0, [0, 0, 3]), (2.0, [0, 0, 0]), (3.0, [0, 0, -3]), (4.0, [0, 0, 0])]},
            "seg2": {"rotation": [(0, [0, 0, 0]), (1.0, [4, 0, -2]), (2.0, [0, 0, 0]), (3.0, [-4, 0, 2]), (4.0, [0, 0, 0])]},
            "seg3": {"rotation": [(0, [0, 0, 0]), (1.0, [3, 0, -4]), (2.0, [0, 0, 0]), (3.0, [-3, 0, 4]), (4.0, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (2.0, [-6, 0, 0]), (4.0, [0, 0, 0])]}}
    idle.update(jaw_track([(0, 0), (1.0, 8), (2.0, 0), (3.0, 8), (4.0, 0)]))
    sleep = {"body": {"position": [(0, [0, -90, 0]), (2.0, [0, -90, 0])]},
             "mound": {"position": [(0, [0, 0, 0]), (1.0, [0, 1, 0]), (2.0, [0, 0, 0])]}}
    burrowed = {"body": {"position": [(0, [0, -90, 0]), (0.5, [0, -90, 0])]},
                "mound": {"position": [(0, [0, 0, 0]), (0.25, [0, 1.5, 0]), (0.5, [0, 0, 0])]}}
    intro = {"body": {"position": [(0, [0, -90, 0]), (0.8, [0, 8, 0]), (1.1, [0, 0, 0])]},
             "head": {"rotation": [(0, [0, 0, 0]), (1.4, [-30, 0, 0]), (2.4, [-30, 0, 0]), (3.0, [0, 0, 0])]},
             "seg3": {"rotation": [(0, [0, 0, 0]), (1.4, [-10, 0, 0]), (2.4, [-10, 0, 0]), (3.0, [0, 0, 0])]}}
    intro.update(jaw_track([(0, 0), (1.4, 45), (2.4, 45), (3.0, 0)]))
    burrow = {"body": {"position": [(0, [0, 0, 0]), (0.3, [0, 4, 0]), (1.0, [0, -90, 0])]},
              "seg1": {"rotation": [(0, [0, 0, 0]), (1.0, [20, 0, 0])]},
              "seg2": {"rotation": [(0, [0, 0, 0]), (1.0, [15, 0, 0])]}}
    emerge = {"body": {"position": [(0, [0, -90, 0]), (0.4, [0, 10, 0]), (0.8, [0, 0, 0])]}}
    emerge.update(jaw_track([(0, 0), (0.4, 40), (0.8, 0)]))
    bite = {"seg2": {"rotation": [(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.45, [22, 0, 0]), (0.7, [0, 0, 0])]},
            "seg3": {"rotation": [(0, [0, 0, 0]), (0.3, [-8, 0, 0]), (0.45, [22, 0, 0]), (0.7, [0, 0, 0])]},
            "seg4": {"rotation": [(0, [0, 0, 0]), (0.3, [-8, 0, 0]), (0.45, [20, 0, 0]), (0.7, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.45, [20, 0, 0]), (0.7, [0, 0, 0])]}}
    bite.update(jaw_track([(0, 0), (0.3, 50), (0.45, -12), (0.7, 0)]))
    spit = {"seg3": {"rotation": [(0, [0, 0, 0]), (0.4, [-15, 0, 0]), (0.55, [18, 0, 0]), (0.9, [0, 0, 0])]},
            "seg4": {"rotation": [(0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.55, [18, 0, 0]), (0.9, [0, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (0.4, [-15, 0, 0]), (0.55, [25, 0, 0]), (0.9, [0, 0, 0])]}}
    spit.update(jaw_track([(0, 0), (0.4, 20), (0.55, 45), (0.9, 0)]))
    slam = {"body": {"position": [(0, [0, 0, 0]), (0.6, [0, 4, 0]), (0.85, [0, 0, 0])]},
            "seg1": {"rotation": [(0, [0, 0, 0]), (0.6, [-14, 0, 0]), (0.85, [30, 0, 0]), (1.3, [0, 0, 0])]},
            "seg2": {"rotation": [(0, [0, 0, 0]), (0.6, [-10, 0, 0]), (0.85, [22, 0, 0]), (1.3, [0, 0, 0])]},
            "seg3": {"rotation": [(0, [0, 0, 0]), (0.6, [-8, 0, 0]), (0.85, [18, 0, 0]), (1.3, [0, 0, 0])]}}
    summon = {"head": {"rotation": [(0, [0, 0, 0]), (0.4, [-35, 0, 0]), (0.8, [-35, 0, 0]), (1.0, [0, 0, 0])]},
              "seg2": {"rotation": [(0, [0, 0, 0]), (0.2, [0, 0, 4]), (0.4, [0, 0, -4]), (0.6, [0, 0, 4]), (0.8, [0, 0, -4]), (1.0, [0, 0, 0])]}}
    summon.update(jaw_track([(0, 0), (0.4, 55), (0.8, 55), (1.0, 0)]))
    death = {"seg1": {"rotation": [(0, [0, 0, 0]), (1.2, [0, 0, 35]), (2.0, [0, 0, 80])]},
             "seg2": {"rotation": [(0, [0, 0, 0]), (2.0, [0, 0, 15])]},
             "seg3": {"rotation": [(0, [0, 0, 0]), (2.0, [10, 0, 10])]},
             "body": {"position": [(0, [0, 0, 0]), (2.5, [0, -10, 0])]}}
    death.update(jaw_track([(0, 0), (0.5, 45), (2.5, -10)]))
    return {"idle": anim(4.0, True, idle), "sleep": anim(2.0, True, sleep), "burrowed": anim(0.5, True, burrowed),
            "intro": anim(3.0, False, intro), "burrow": anim(1.0, "hold", burrow), "emerge": anim(0.8, False, emerge),
            "bite": anim(0.7, False, bite), "spit": anim(0.9, False, spit), "slam": anim(1.3, False, slam),
            "summon": anim(1.0, False, summon), "death": anim(2.5, "hold", death)}


# =====================================================================================
#  VERME CAVADOR
# =====================================================================================
B_FLESH = "#B07868"
B_CHITIN = "#5E4636"


def deco_b_seg(f, c):
    for name in ("front", "back", "left", "right", "top"):
        fc = f[name]
        fc.rect(0, 0, fc.w - 1, 0, B_CHITIN)
        if fc.h > 3:
            fc.rect(0, fc.h // 2, fc.w - 1, fc.h // 2, darken(H(B_FLESH), 0.25))


def deco_b_head(f, c):
    fr = f["front"]
    fr.rect(1, 1, fr.w - 2, fr.h - 2, MAW)
    for x in range(1, fr.w - 1, 2):
        fr.set(x, 1, TOOTH)
        fr.set(x, fr.h - 2, TOOTH)
    for name in ("left", "right", "top"):
        f[name].rect(0, 0, f[name].w - 1, 0, B_CHITIN)


def burrower():
    m = Model("depths_burrower", 64, 64, bounds=(2, 2, 0.5))
    m.bone("root", None, [0, 0, 0])
    m.bone("mound", "root", [0, 0, 0], [
        Cube([-5, 0, -6], [10, 2, 12], DIRT, deco_mound),
        Cube([-3, 2, -3], [6, 1, 6], "#4A3A28", deco_mound)])
    m.bone("body", "root", [0, 0, 0])
    m.bone("front", "body", [0, 3, -2], [Cube([-3.5, 0, -6], [7, 7, 6], B_FLESH, deco_b_seg),
                                        Cube([-1, 7, -4], [2, 3, 2], MANA, deco_crystal, glow=True)])
    m.bone("head", "front", [0, 4, -6], [Cube([-3, 0.5, -10], [6, 6, 4], B_CHITIN, deco_b_head)])
    m.bone("mand_l", "head", [2, 3, -10], [Cube([1, 1.5, -13], [2, 2, 3], TOOTH)])
    m.bone("mand_r", "head", [-2, 3, -10], [Cube([-3, 1.5, -13], [2, 2, 3], TOOTH)])
    m.bone("mid", "body", [0, 3, 0], [Cube([-3, 0, 0], [6, 6, 6], B_FLESH, deco_b_seg)])
    m.bone("tail", "mid", [0, 2.5, 6], [Cube([-2.5, 0, 6], [5, 5, 5], B_FLESH, deco_b_seg),
                                        Cube([-1.5, 0, 11], [3, 3, 3], darken(H(B_FLESH), 0.15))])
    return m


def burrower_anims():
    idle = {"mid": {"rotation": [(0, [0, 6, 0]), (1.0, [0, -6, 0]), (2.0, [0, 6, 0])]},
            "tail": {"rotation": [(0, [0, -10, 0]), (1.0, [0, 10, 0]), (2.0, [0, -10, 0])]},
            "mand_l": {"rotation": [(0, [0, 0, 0]), (1.0, [0, -20, 0]), (2.0, [0, 0, 0])]},
            "mand_r": {"rotation": [(0, [0, 0, 0]), (1.0, [0, 20, 0]), (2.0, [0, 0, 0])]}}
    walk = {"mid": {"rotation": [(0, [0, 18, 0]), (0.3, [0, -18, 0]), (0.6, [0, 18, 0])]},
            "tail": {"rotation": [(0, [0, -22, 0]), (0.3, [0, 22, 0]), (0.6, [0, -22, 0])]},
            "head": {"rotation": [(0, [0, -8, 0]), (0.3, [0, 8, 0]), (0.6, [0, -8, 0])]},
            "body": {"position": [(0, [0, 0, 0]), (0.15, [0, 0.6, 0]), (0.3, [0, 0, 0]), (0.45, [0, 0.6, 0]), (0.6, [0, 0, 0])]}}
    attack = {"head": {"rotation": [(0, [0, 0, 0]), (0.2, [-25, 0, 0]), (0.3, [20, 0, 0]), (0.5, [0, 0, 0])],
                       "position": [(0, [0, 0, 0]), (0.2, [0, 0, 1]), (0.3, [0, 0, -3]), (0.5, [0, 0, 0])]},
              "mand_l": {"rotation": [(0, [0, 0, 0]), (0.2, [0, -35, 0]), (0.3, [0, 10, 0]), (0.5, [0, 0, 0])]},
              "mand_r": {"rotation": [(0, [0, 0, 0]), (0.2, [0, 35, 0]), (0.3, [0, -10, 0]), (0.5, [0, 0, 0])]}}
    burrow = {"body": {"position": [(0, [0, 0, 0]), (0.4, [0, -10, 0])],
                       "rotation": [(0, [0, 0, 0]), (0.4, [35, 0, 0])]}}
    emerge = {"body": {"position": [(0, [0, -10, 0]), (0.25, [0, 3, 0]), (0.5, [0, 0, 0])],
                       "rotation": [(0, [-40, 0, 0]), (0.5, [0, 0, 0])]}}
    burrowed = {"mound": {"position": [(0, [0, 0, 0]), (0.2, [0, 0.6, 0]), (0.4, [0, 0, 0])]}}
    return {"idle": anim(2.0, True, idle), "walk": anim(0.6, True, walk), "attack": anim(0.5, False, attack),
            "burrow": anim(0.4, "hold", burrow), "emerge": anim(0.5, False, emerge), "burrowed": anim(0.4, True, burrowed)}


# =====================================================================================
#  EXPLORADOR PERDIDO
# =====================================================================================
BONE = "#D8CFB8"
BONE_D = "#A89E86"
VEST = "#4A3A2A"
HELM = "#C8A03A"
LAMP = "#FFE27A"
METAL = "#7A7A82"
WOOD = "#6A4A2A"


def deco_skull(f, c):
    fr = f["front"]
    fr.rect(1, 3, 2, 4, "#1A1410")
    fr.rect(5, 3, 6, 4, "#1A1410")
    fr.set(2, 4, "#FFB040", True)
    fr.set(5, 4, "#FFB040", True)
    fr.rect(3, 5, 4, 5, "#2A2018")
    for x in range(1, 7, 2):
        fr.set(x, 7, "#2A2018")


def deco_ribs(f, c):
    fr = f["front"]
    fr.rect(0, 0, fr.w - 1, fr.h - 1, VEST)
    fr.rect(2, 1, fr.w - 3, fr.h - 3, "#1E1610")
    for y in range(2, fr.h - 3, 2):
        fr.rect(2, y, fr.w - 3, y, BONE)
    fr.rect(fr.w // 2 - 1 + 1, 1, fr.w // 2, fr.h - 3, BONE_D)
    for name in ("back", "left", "right"):
        f[name].rect(0, 0, f[name].w - 1, f[name].h - 1, VEST)
        f[name].rect(0, f[name].h - 2, f[name].w - 1, f[name].h - 2, "#2A1A0E")
    fr.rect(0, fr.h - 2, fr.w - 1, fr.h - 2, "#2A1A0E")
    fr.set(fr.w // 2, fr.h - 2, "#C8A03A")


def deco_helm(f, c):
    for name in ("front", "back", "left", "right"):
        f[name].rect(0, f[name].h - 1, f[name].w - 1, f[name].h - 1, darken(H(HELM), 0.35))


def deco_bone_limb(f, c):
    for name in ("front", "back", "left", "right"):
        fc = f[name]
        fc.rect(0, fc.h // 2, fc.w - 1, fc.h // 2, BONE_D)


def delver():
    m = Model("depths_lost_delver", 64, 64, bounds=(2, 3, 1))
    m.bone("root", None, [0, 0, 0])
    m.bone("right_leg", "root", [-2, 12, 0], [Cube([-3, 0, -1], [2, 12, 2], BONE, deco_bone_limb)])
    m.bone("left_leg", "root", [2, 12, 0], [Cube([1, 0, -1], [2, 12, 2], BONE, deco_bone_limb)])
    m.bone("body", "root", [0, 12, 0], [Cube([-4, 12, -2], [8, 12, 4], VEST, deco_ribs)])
    m.bone("head", "body", [0, 24, 0], [
        Cube([-4, 24, -4], [8, 8, 8], BONE, deco_skull),
        Cube([-4.5, 30, -4.5], [9, 3, 9], HELM, deco_helm),
        Cube([-1.5, 30.5, -5.5], [3, 2, 1], LAMP, glow=True)])
    m.bone("right_arm", "body", [-5, 22, 0], [Cube([-6, 12, -1], [2, 12, 2], BONE, deco_bone_limb)])
    m.bone("pickaxe", "right_arm", [-5, 13, 0], [
        Cube([-5.5, 12.5, -10], [1, 1, 11], WOOD),
        Cube([-9, 11.5, -11.5], [8, 2, 2], METAL)])
    m.bone("left_arm", "body", [5, 22, 0], [Cube([4, 12, -1], [2, 12, 2], BONE, deco_bone_limb)])
    m.bone("lantern", "left_arm", [5, 12, 0], [
        Cube([4, 8, -1], [2, 3, 2], "#FF9A3A", glow=True),
        Cube([4.5, 11, -0.5], [1, 1, 1], "#3A3A3A")])
    return m


def delver_anims():
    idle = {"body": {"position": [(0, [0, 0, 0]), (1.25, [0, 0.3, 0]), (2.5, [0, 0, 0])]},
            "right_arm": {"rotation": [(0, [-10, 0, 0]), (1.25, [-14, 0, 2]), (2.5, [-10, 0, 0])]},
            "left_arm": {"rotation": [(0, [-5, 0, 0]), (1.25, [-8, 0, -3]), (2.5, [-5, 0, 0])]},
            "head": {"rotation": [(0, [0, 0, 0]), (1.25, [4, 0, 3]), (2.5, [0, 0, 0])]}}
    walk = {"right_leg": {"rotation": [(0, [28, 0, 0]), (0.55, [-28, 0, 0]), (1.1, [28, 0, 0])]},
            "left_leg": {"rotation": [(0, [-28, 0, 0]), (0.55, [28, 0, 0]), (1.1, [-28, 0, 0])]},
            "right_arm": {"rotation": [(0, [-25, 0, 0]), (0.55, [5, 0, 0]), (1.1, [-25, 0, 0])]},
            "left_arm": {"rotation": [(0, [10, 0, 0]), (0.55, [-20, 0, 0]), (1.1, [10, 0, 0])]},
            "body": {"rotation": [(0, [5, 0, 0]), (1.1, [5, 0, 0])]}}
    attack = {"right_arm": {"rotation": [(0, [-10, 0, 0]), (0.25, [-160, 0, -8]), (0.4, [10, 0, 4]), (0.6, [-10, 0, 0])]},
              "body": {"rotation": [(0, [0, 0, 0]), (0.25, [-8, 10, 0]), (0.4, [10, -10, 0]), (0.6, [0, 0, 0])]}}
    flash = {"head": {"rotation": [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (0.8, [-20, 0, 0]), (1.0, [0, 0, 0])]},
             "left_arm": {"rotation": [(0, [-5, 0, 0]), (0.5, [-120, 0, 0]), (0.8, [-120, 0, 0]), (1.0, [-5, 0, 0])]}}
    return {"idle": anim(2.5, True, idle), "walk": anim(1.1, True, walk), "attack": anim(0.6, False, attack),
            "flash": anim(1.0, False, flash)}


def build_all(assets):
    for model, anims in ((worm_king(), worm_king_anims()), (burrower(), burrower_anims()), (delver(), delver_anims())):
        model.save(assets)
        save_anims(assets, model.ident, anims)
        print("  modelo", model.ident)
