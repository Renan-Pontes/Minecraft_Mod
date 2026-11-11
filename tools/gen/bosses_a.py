"""
Gerador de assets do modulo bosses_a (Guardioes Elementais): modelos GeckoLib (.geo.json), animacoes,
texturas 'box UV' pintadas por face (+ glowmasks), modelos dos ovos e loot tables.

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_a.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
TRANSPARENT = (0, 0, 0, 0)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def C(pal, c):
    """Cor: chave da paleta, '#hex' ou tupla RGBA."""
    if isinstance(c, tuple):
        return c
    if c in pal:
        return pal[c]
    return hex_rgba(c)


# =====================================================================================
#  Modelo com box UV + pintura por face
# =====================================================================================
class Face:
    def __init__(self, model, x, y, w, h):
        self.m, self.x, self.y, self.w, self.h = model, x, y, w, h

    def set(self, fx, fy, c, g=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, c)
            if g:
                self.m.glow.set(self.x + fx, self.y + fy, c)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def rect(self, x0, y0, x1, y1, c, g=False):
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                self.set(xx, yy, c, g)

    def fill(self, c, g=False):
        self.rect(0, 0, self.w - 1, self.h - 1, c, g)

    def tint(self, fn):
        for yy in range(self.h):
            for xx in range(self.w):
                self.set(xx, yy, fn(self.get(xx, yy), xx, yy))


class P:
    """Pintura de um cubo: cor base (chave da paleta), cores por face, ruido e decoracao deco(fs, pal, rnd)."""

    def __init__(self, base, faces=None, noise=0.06, deco=None, grad=True, glow=False):
        self.base = base
        self.faces = faces or {}
        self.noise = noise
        self.deco = deco
        self.grad = grad
        self.glow = glow


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0, rotation=None, pivot=None):
        self.cubes.append({"bone": bone, "origin": list(origin), "size": [int(s) for s in size], "paint": paint,
                           "inflate": inflate, "rotation": rotation, "pivot": pivot})

    def layout(self):
        items = []
        for i, c in enumerate(self.cubes):
            w, h, d = c["size"]
            items.append((i, 2 * (d + w), d + h))
        order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
        for W in (64, 128, 256, 512):
            x = y = row_h = 0
            pos = {}
            ok = True
            for i, rw, rh in order:
                if rw > W:
                    ok = False
                    break
                if x + rw > W:
                    x = 0
                    y += row_h
                    row_h = 0
                pos[i] = (x, y)
                x += rw
                row_h = max(row_h, rh)
            if not ok:
                continue
            used = y + row_h
            Hh = 16
            while Hh < used:
                Hh *= 2
            if Hh <= W:
                self.tw, self.th = W, Hh
                for i, c in enumerate(self.cubes):
                    c["uv"] = pos[i]
                return
        raise RuntimeError("modelo grande demais: " + self.ident)

    def faces_of(self, c):
        u, v = c["uv"]
        w, h, d = c["size"]
        return {
            "top": Face(self, u + d, v, w, d),
            "bottom": Face(self, u + d + w, v, w, d),
            "right": Face(self, u, v + d, d, h),
            "front": Face(self, u + d, v + d, w, h),
            "left": Face(self, u + d + w, v + d, d, h),
            "back": Face(self, u + 2 * d + w, v + d, w, h),
        }

    def paint(self, pal, seed=7):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            p = c["paint"]
            fs = self.faces_of(c)
            rnd = random.Random(seed * 1000 + i * 31)
            for name, f in fs.items():
                base = C(pal, p.faces.get(name, p.base))
                if base[3] > 0:
                    if name == "top":
                        base = lighten(base, 0.10)
                    elif name == "bottom":
                        base = darken(base, 0.28)
                    elif name == "back":
                        base = darken(base, 0.08)
                    elif name in ("left", "right"):
                        base = darken(base, 0.05)
                f.fill(base, p.glow)
                if base[3] > 0 and p.grad and name in ("front", "back", "left", "right") and f.h > 2:
                    f.tint(lambda col, xx, yy, hh=f.h: darken(col, 0.16 * (yy / (hh - 1))) if yy > hh * 0.4
                           else lighten(col, 0.05 * (1 - yy / (hh * 0.4 + 1e-6))))
                if base[3] > 0 and p.noise > 0:
                    f.tint(lambda col, xx, yy: (lighten(col, r) if (r := (rnd.random() - 0.5) * 2 * p.noise) > 0
                                                else darken(col, -r)))
                if p.glow:
                    for yy in range(f.h):
                        for xx in range(f.w):
                            self.glow.set(f.x + xx, f.y + yy, f.get(xx, yy))
            if p.deco:
                p.deco(fs, pal, rnd)

    def geo_json(self, vis_w, vis_h, vis_y):
        bones = []
        for b in self.bones:
            bb = dict(b)
            cubes = []
            for c in self.cubes:
                if c["bone"] != b["name"]:
                    continue
                cj = {"origin": c["origin"], "size": c["size"], "uv": list(c["uv"])}
                if c["inflate"]:
                    cj["inflate"] = c["inflate"]
                if c["rotation"]:
                    cj["rotation"] = c["rotation"]
                    cj["pivot"] = c["pivot"] or c["origin"]
                cubes.append(cj)
            if cubes:
                bb["cubes"] = cubes
            bones.append(bb)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.ident,
                    "texture_width": self.tw, "texture_height": self.th,
                    "visible_bounds_width": vis_w, "visible_bounds_height": vis_h, "visible_bounds_offset": [0, vis_y, 0],
                },
                "bones": bones,
            }],
        }

    def save_geo(self, vis_w, vis_h, vis_y):
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json(vis_w, vis_h, vis_y))

    def save_texture(self, name=None, glow=True):
        name = name or self.ident
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        if glow:
            self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# =====================================================================================
#  Decoracoes reutilizaveis
# =====================================================================================
def chain(*fns):
    def d(fs, pal, rnd):
        for fn in fns:
            if fn:
                fn(fs, pal, rnd)
    return d


def cracks(color, core, density=1.0, faces=("front", "back", "left", "right", "top"), glow=True, rim="lava_dark"):
    """Veios de lava/energia: linhas continuas com galhos, nucleo claro e borda escura (brilham no glowmask)."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            n = max(1, int(f.w * f.h / 70 * density))
            path = set()
            for _ in range(n):
                x, y = rnd.uniform(0, f.w), rnd.uniform(0, f.h)
                ang = rnd.uniform(0.6, 2.5)
                length = rnd.randint(4, max(5, (f.w + f.h) // 2))
                for _ in range(length):
                    path.add((int(x), int(y)))
                    ang += rnd.uniform(-0.6, 0.6)
                    x += math.cos(ang)
                    y += math.sin(ang)
                    if rnd.random() < 0.08:
                        bx, by, ba = x, y, ang + rnd.choice((-1.1, 1.1))
                        for _ in range(rnd.randint(2, 4)):
                            bx += math.cos(ba)
                            by += math.sin(ba)
                            path.add((int(bx), int(by)))
                    if not (0 <= x < f.w and 0 <= y < f.h):
                        break
            path = {(px, py) for px, py in path if 0 <= px < f.w and 0 <= py < f.h}
            if rim and rim in pal:
                for px, py in path:
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        q = (px + ox, py + oy)
                        if q not in path and rnd.random() < 0.55:
                            f.set(q[0], q[1], darken(pal[rim], 0.35))
            for px, py in path:
                f.set(px, py, C(pal, core) if rnd.random() < 0.3 else C(pal, color), glow)
    return d


def speckle(color, density, faces=("front", "back", "left", "right", "top"), glow=False):
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            for yy in range(f.h):
                for xx in range(f.w):
                    if rnd.random() < density:
                        f.set(xx, yy, C(pal, color), glow)
    return d


def vgrad(bottom, top, faces=("front", "back", "left", "right"), glow=False, top_face=None):
    """Gradiente vertical (chamas, penas): cor de baixo -> cor de cima."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            for yy in range(f.h):
                t = 1 - yy / max(1, f.h - 1)
                c = mix(C(pal, bottom), C(pal, top), t)
                for xx in range(f.w):
                    f.set(xx, yy, c, glow)
        if top_face:
            fs["top"].fill(C(pal, top_face), glow)
    return d


def eyes(xs, y, color, face="front", h=1):
    def d(fs, pal, rnd):
        f = fs[face]
        for x in xs:
            for k in range(h):
                f.set(x, y + k, C(pal, color), True)
    return d


def row(face, fy, color, glow=False, x0=0, x1=None):
    def d(fs, pal, rnd):
        f = fs[face]
        for xx in range(x0, (f.w - 1 if x1 is None else x1) + 1):
            f.set(xx, fy if fy >= 0 else f.h + fy, C(pal, color), glow)
    return d


def scales(light, dark, faces=("front", "back", "left", "right", "top")):
    """Escamas em fileiras alternadas."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            for yy in range(f.h):
                off = (yy // 2) % 2
                for xx in range(f.w):
                    if (xx + off * 2) % 4 == 0 and yy % 2 == 0:
                        f.set(xx, yy, C(pal, light))
                    elif (xx + off * 2) % 4 == 2 and yy % 2 == 1:
                        f.set(xx, yy, C(pal, dark))
    return d


def spots(color, faces=("left", "right"), step=3, fy=None, glow=True):
    """Fileira de pintas brilhantes ao longo das laterais."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            yy = f.h // 3 if fy is None else fy
            for xx in range(1, f.w - 1, step):
                f.set(xx, yy, C(pal, color), glow)
    return d


def bricks(line, faces=("front", "back", "left", "right", "top"), bw=6, bh=4):
    """Juntas de pedra (blocos irregulares)."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            for yy in range(f.h):
                for xx in range(f.w):
                    off = (yy // bh) % 2 * (bw // 2)
                    if yy % bh == bh - 1 or (xx + off) % bw == bw - 1:
                        if rnd.random() < 0.85:
                            f.set(xx, yy, C(pal, line))
    return d


def moss_top(moss, light, faces=("front", "back", "left", "right")):
    """Musgo no topo e escorrendo pelas laterais."""
    def d(fs, pal, rnd):
        fs["top"].tint(lambda col, xx, yy: C(pal, light) if rnd.random() < 0.3 else C(pal, moss))
        for name in faces:
            f = fs[name]
            for xx in range(f.w):
                drip = rnd.randint(1, max(1, min(4, f.h // 3)))
                for yy in range(drip):
                    f.set(xx, yy, C(pal, moss) if rnd.random() < 0.8 else C(pal, light))
    return d


def rune_circle(color, face="front", cxf=0.5, cyf=0.5, rf=0.32):
    """Runa brilhante (Golem): dois aneis, triangulo inscrito e marcas nos pontos cardeais."""
    def d(fs, pal, rnd):
        f = fs[face]
        cx, cy = f.w * cxf - 0.5, f.h * cyf - 0.5
        r = min(f.w, f.h) * rf
        tri = [(cx + math.cos(a) * r * 0.78, cy + math.sin(a) * r * 0.78) for a in (-math.pi / 2, math.pi / 6, math.pi * 5 / 6)]

        def on_seg(px, py, a, b):
            ax, ay = a
            bx, by = b
            l2 = (bx - ax) ** 2 + (by - ay) ** 2
            t = max(0, min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / l2))
            return math.hypot(px - (ax + t * (bx - ax)), py - (ay + t * (by - ay))) < 0.55

        for yy in range(f.h):
            for xx in range(f.w):
                dist = math.hypot(xx - cx, yy - cy)
                hit = abs(dist - r) < 0.55 or (r > 6 and abs(dist - (r - 2)) < 0.45)
                hit = hit or any(on_seg(xx, yy, tri[i], tri[(i + 1) % 3]) for i in range(3))
                hit = hit or (dist < 1.0)
                for a in (0, math.pi / 2, math.pi, math.pi * 1.5):
                    mx, my = cx + math.cos(a) * (r + 2), cy + math.sin(a) * (r + 2)
                    if abs(xx - mx) < 0.6 and abs(yy - my) < 0.6:
                        hit = True
                if hit:
                    f.set(xx, yy, C(pal, color), True)
    return d


def feathers(light, mid, dark, faces=("front", "back"), width=3, glow_tip=None):
    """Penas verticais: claras em cima, escuras na ponta, com a haste."""
    def d(fs, pal, rnd):
        for name in faces:
            f = fs[name]
            for xx in range(f.w):
                k = xx % width
                tip = f.h - 1 - ((xx // width) % 2) * 2 - rnd.randint(0, 1)
                for yy in range(f.h):
                    if yy > tip:
                        f.set(xx, yy, TRANSPARENT)
                        continue
                    t = yy / max(1, f.h - 1)
                    c = mix(C(pal, light), C(pal, mid), min(1, t * 1.3))
                    if t > 0.75:
                        c = mix(c, C(pal, dark), (t - 0.75) * 4)
                    if k == 0:
                        c = darken(c, 0.18)
                    f.set(xx, yy, c)
                if glow_tip and tip >= 0:
                    f.set(xx, tip, C(pal, glow_tip), True)
    return d


# =====================================================================================
#  Animacoes
# =====================================================================================
def fmt(t):
    s = ("%.4f" % t).rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


class Anim:
    def __init__(self, name, length, loop=False):
        self.name, self.length, self.loop = name, length, loop
        self.bones = {}

    def ch(self, bone, channel, frames):
        self.bones.setdefault(bone, {})[channel] = {fmt(t): [round(float(x), 3) for x in v] for t, v in frames}
        return self

    def rot(self, bone, frames):
        return self.ch(bone, "rotation", frames)

    def pos(self, bone, frames):
        return self.ch(bone, "position", frames)

    def scl(self, bone, frames):
        return self.ch(bone, "scale", frames)

    def json(self):
        return {"loop": self.loop, "animation_length": self.length, "bones": self.bones}


def wave(length, fn, steps=16):
    return [(length * i / steps, fn(length * i / steps)) for i in range(steps + 1)]


def S(t, length, k=1, ph=0.0):
    return math.sin(2 * math.pi * k * t / length + ph)


def spin_frames(length, turns, axis=1, steps_per_turn=4):
    n = max(4, int(abs(turns) * steps_per_turn))
    out = []
    for i in range(n + 1):
        t = length * i / n
        v = [0, 0, 0]
        v[axis] = 360 * turns * i / n
        out.append((t, v))
    return out


def save_anims(ident, anims):
    data = {"format_version": "1.8.0", "animations": {f"animation.{ident}.{a.name}": a.json() for a in anims}}
    write_json(os.path.join(ASSETS, "animations/entity", ident + ".animation.json"), data)


Z = [0, 0, 0]
ONE = [1, 1, 1]


# =====================================================================================
#  IGNIS — titã de rocha vulcânica com lava nas rachaduras
# =====================================================================================
IGNIS_RED = {
    "rock_dark": hex_rgba("#241c1a"), "rock": hex_rgba("#3a2e2a"), "rock_light": hex_rgba("#56453c"),
    "lava_core": hex_rgba("#ffe36b"), "lava": hex_rgba("#ff8a1f"), "lava_dark": hex_rgba("#c8380f"),
    "flame_bottom": hex_rgba("#fff4a0"), "flame_mid": hex_rgba("#ffb02e"), "flame_top": hex_rgba("#ff5a14"),
    "eye": hex_rgba("#fff6b0"), "ember": hex_rgba("#ff6a1a"),
}
IGNIS_BLUE = {
    "rock_dark": hex_rgba("#181a24"), "rock": hex_rgba("#272b3a"), "rock_light": hex_rgba("#3b4257"),
    "lava_core": hex_rgba("#eafcff"), "lava": hex_rgba("#4fc3ff"), "lava_dark": hex_rgba("#1f5fd1"),
    "flame_bottom": hex_rgba("#f0ffff"), "flame_mid": hex_rgba("#7fd8ff"), "flame_top": hex_rgba("#3f7bff"),
    "eye": hex_rgba("#e0ffff"), "ember": hex_rgba("#5fb8ff"),
}


def ignis_model():
    m = Model("ignis")
    rock = lambda dens=1.0, extra=None: P("rock", noise=0.08, deco=chain(cracks("lava", "lava_core", dens),
                                                                           speckle("rock_light", 0.05), extra))
    flame = P("flame_mid", noise=0.0, glow=True, deco=vgrad("flame_bottom", "flame_top", glow=True, top_face="flame_top"))

    m.bone("root", [0, 0, 0])
    m.bone("hips", [0, 28, 0], "root")
    m.cube("hips", [-9, 26, -5], [18, 7, 10], rock(0.8))
    m.cube("hips", [-8, 20, -6], [16, 7, 1], P("rock_dark", deco=cracks("lava_dark", "lava", 0.6)))
    m.cube("hips", [-8, 20, 5], [16, 7, 1], P("rock_dark", deco=cracks("lava_dark", "lava", 0.6)))
    for side, sx in (("left", 1), ("right", -1)):
        m.bone("leg_" + side, [6 * sx, 27, 0], "hips")
        m.cube("leg_" + side, [2 if sx > 0 else -10, 14, -4], [8, 13, 8], rock(1.0))
        m.bone("shin_" + side, [6 * sx, 14, 0], "leg_" + side)
        m.cube("shin_" + side, [1.5 if sx > 0 else -10.5, 3, -4.5], [9, 11, 9], rock(1.2))
        m.cube("shin_" + side, [1 if sx > 0 else -11, 0, -7], [10, 4, 11], P("rock_dark", deco=cracks("lava", "lava_core", 0.7)))

    m.bone("body", [0, 30, 0], "root")
    m.cube("body", [-8, 31, -5], [16, 9, 10], rock(1.3))
    m.bone("chest", [0, 40, 0], "body")
    m.cube("chest", [-13, 40, -7.5], [26, 15, 15], rock(1.2))
    # Núcleo de magma no peito
    core = P("lava", noise=0.0, glow=True, deco=lambda fs, pal, rnd: [
        fs["front"].rect(2, 2, 4, 4, pal["lava_core"], True), fs["front"].rect(3, 1, 3, 5, pal["lava_core"], True),
        fs["front"].rect(1, 3, 5, 3, pal["lava_core"], True)])
    m.cube("chest", [-3.5, 45, -8.5], [7, 7, 1], core)
    for x, rz in ((-1.5, 0), (-7, -15), (4, 15)):
        m.cube("chest", [x, 46 if x != -1.5 else 44, 6], [3, 8 if x == -1.5 else 7, 3], rock(0.6), rotation=[-35, 0, rz],
               pivot=[x + 1.5, 46, 7])
    m.bone("head", [0, 55, -2], "chest")
    m.cube("head", [-4, 54, -5], [8, 3, 7], rock(0.8))
    m.cube("head", [-5.5, 56, -8], [11, 10, 10], P("rock", noise=0.07, deco=chain(
        cracks("lava", "lava_core", 0.5, faces=("back", "left", "right", "top")), speckle("rock_light", 0.05),
        eyes([2, 3, 7, 8], 4, "eye", h=2), row("front", 6, "rock_dark"), row("front", 3, "rock_dark", x0=1, x1=9))))
    m.cube("head", [-6, 63, -8.5], [12, 2, 3], P("rock_dark", deco=speckle("rock_light", 0.1)))
    m.bone("jaw", [0, 58, -3], "head")
    m.cube("jaw", [-4.5, 55, -8.5], [9, 3, 8], P("rock_dark", deco=chain(row("front", 0, "lava_core", True, 1, 7),
                                                                        row("top", 0, "lava", True))))
    for sx in (1, -1):
        m.cube("head", [5 if sx > 0 else -8, 63, -4], [3, 6, 3], P("rock_dark", deco=speckle("rock_light", 0.1)),
               rotation=[-20, 0, -25 * sx], pivot=[6.5 * sx, 63, -2.5])
        m.cube("head", [7.5 if sx > 0 else -9.5, 67, -3.5], [2, 4, 2], P("lava_dark", glow=False,
                                                                         deco=vgrad("lava_dark", "lava", glow=True)),
               rotation=[-20, 0, -40 * sx], pivot=[8.5 * sx, 67, -2.5])
    m.bone("crown", [0, 66, -3], "head")
    for o, s in (([-1.5, 66, -5], [3, 8, 3]), ([-4.5, 66, -4], [3, 6, 3]), ([1.5, 66, -4], [3, 6, 3]),
                 ([-6, 65, -2], [2, 5, 2]), ([4, 65, -2], [2, 5, 2]), ([-1, 66, -1], [2, 5, 2])):
        m.cube("crown", o, s, flame)

    for side, sx in (("left", 1), ("right", -1)):
        arm = "arm_" + side
        m.bone(arm, [15 * sx, 52, 0], "chest")
        m.cube(arm, [12 if sx > 0 else -24, 47, -7], [12, 10, 13], rock(1.0, speckle("rock_dark", 0.08)))
        m.cube(arm, [14 if sx > 0 else -21, 57, -3], [7, 5, 6], flame)
        m.cube(arm, [14.5 if sx > 0 else -23.5, 36, -4.5], [9, 12, 9], rock(1.3))
        fa = "forearm_" + side
        m.bone(fa, [19 * sx, 37, 0], arm)
        m.cube(fa, [13.5 if sx > 0 else -24.5, 22, -5.5], [11, 15, 11], rock(1.6))
        m.cube(fa, [13 if sx > 0 else -25, 14, -6], [12, 9, 12], P("rock_dark", noise=0.08, deco=chain(
            cracks("lava", "lava_core", 1.8), row("front", 0, "rock_light"))))
        for k in range(3):
            kx = (14 + k * 3.5) if sx > 0 else (-24 + k * 3.5)
            m.cube(fa, [kx, 17, -7.5], [2, 3, 2], P("rock_light", deco=speckle("lava", 0.2, glow=True)))

    m.bone("embers", [0, 40, 0], "root")
    for o, s in (([16, 62, 6], [3, 3, 3]), ([-19, 60, 5], [3, 3, 3]), ([0, 70, 8], [2, 2, 2]), ([-10, 66, -9], [2, 2, 2])):
        m.cube("embers", o, s, P("ember", noise=0.0, glow=True, deco=speckle("lava_core", 0.3, glow=True)))
    return m


def ignis_anims():
    A = []
    L = 3.0
    a = Anim("idle", L, True)
    a.rot("chest", wave(L, lambda t: [S(t, L) * 2, 0, 0]))
    a.rot("head", wave(L, lambda t: [-S(t, L) * 2, S(t, L, 1, 1.2) * 6, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 3, 0, 4 + S(t, L) * 2]))
    a.rot("arm_right", wave(L, lambda t: [S(t, L) * 3, 0, -4 - S(t, L) * 2]))
    a.rot("forearm_left", wave(L, lambda t: [-8 - S(t, L) * 3, 0, 0]))
    a.rot("forearm_right", wave(L, lambda t: [-8 - S(t, L) * 3, 0, 0]))
    a.rot("jaw", wave(L, lambda t: [2 + S(t, L, 2) * 2, 0, 0]))
    a.pos("body", wave(L, lambda t: [0, S(t, L) * 0.4, 0]))
    a.scl("crown", wave(L, lambda t: [1, 1 + 0.12 * S(t, L, 3), 1]))
    a.rot("embers", spin_frames(L, 1))
    a.pos("embers", wave(L, lambda t: [0, S(t, L, 2) * 1.5, 0]))
    A.append(a)

    L = 1.6
    a = Anim("walk", L, True)
    a.rot("leg_left", wave(L, lambda t: [-S(t, L) * 24, 0, 0]))
    a.rot("leg_right", wave(L, lambda t: [S(t, L) * 24, 0, 0]))
    a.rot("shin_left", wave(L, lambda t: [max(0, S(t, L)) * 26, 0, 0]))
    a.rot("shin_right", wave(L, lambda t: [max(0, -S(t, L)) * 26, 0, 0]))
    a.pos("body", wave(L, lambda t: [0, abs(S(t, L)) * 0.9 - 0.6, 0]))
    a.rot("chest", wave(L, lambda t: [5, S(t, L) * 5, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 16, 0, 5]))
    a.rot("arm_right", wave(L, lambda t: [-S(t, L) * 16, 0, -5]))
    a.rot("forearm_left", wave(L, lambda t: [-12 + S(t, L) * 6, 0, 0]))
    a.rot("forearm_right", wave(L, lambda t: [-12 - S(t, L) * 6, 0, 0]))
    a.rot("embers", spin_frames(L, 1))
    a.scl("crown", wave(L, lambda t: [1, 1 + 0.1 * S(t, L, 2), 1]))
    A.append(a)

    L = 2.0
    a = Anim("roar", L, True)
    a.rot("chest", [(0, Z), (0.3, [-18, 0, 0]), (1.2, [-20, 0, 0]), (1.6, [8, 0, 0]), (2.0, Z)])
    a.rot("head", [(0, Z), (0.3, [-25, 0, 0]), (1.2, [-28, 0, 0]), (1.6, [10, 0, 0]), (2.0, Z)])
    a.rot("jaw", [(0, Z), (0.3, [28, 0, 0]), (1.3, [28, 0, 0]), (1.6, [5, 0, 0]), (2.0, Z)])
    a.rot("arm_left", [(0, Z), (0.3, [-25, 0, 45]), (1.2, [-30, 0, 50]), (1.6, [-10, 0, 10]), (2.0, Z)])
    a.rot("arm_right", [(0, Z), (0.3, [-25, 0, -45]), (1.2, [-30, 0, -50]), (1.6, [-10, 0, -10]), (2.0, Z)])
    a.rot("forearm_left", [(0, Z), (0.3, [-35, 0, 0]), (1.2, [-35, 0, 0]), (2.0, Z)])
    a.rot("forearm_right", [(0, Z), (0.3, [-35, 0, 0]), (1.2, [-35, 0, 0]), (2.0, Z)])
    a.scl("crown", [(0, ONE), (0.3, [1.3, 1.6, 1.3]), (1.2, [1.4, 1.7, 1.4]), (2.0, ONE)])
    a.rot("embers", spin_frames(L, 2))
    A.append(a)

    L = 3.0
    a = Anim("death", L, False)
    a.rot("chest", [(0, Z), (0.6, [-22, 0, 0]), (1.6, [30, 0, 0]), (2.4, [45, 0, 0]), (3.0, [48, 0, 0])])
    a.rot("head", [(0, Z), (0.6, [-30, 0, 0]), (1.6, [30, 0, 0]), (3.0, [40, 0, 0])])
    a.rot("jaw", [(0, Z), (0.6, [30, 0, 0]), (1.6, [10, 0, 0]), (3.0, [25, 0, 0])])
    a.rot("arm_left", [(0, Z), (0.6, [-60, 0, 30]), (1.6, [15, 0, 12]), (3.0, [20, 0, 15])])
    a.rot("arm_right", [(0, Z), (0.6, [-60, 0, -30]), (1.6, [15, 0, -12]), (3.0, [20, 0, -15])])
    a.pos("body", [(0, Z), (0.6, Z), (1.6, [0, -10, 0]), (2.4, [0, -14, 0]), (3.0, [0, -14, 0])])
    a.pos("hips", [(0, Z), (0.6, Z), (1.6, [0, -10, 0]), (3.0, [0, -10, 0])])
    a.rot("leg_left", [(0, Z), (0.6, Z), (1.6, [-80, 0, -10]), (3.0, [-80, 0, -10])])
    a.rot("leg_right", [(0, Z), (0.6, Z), (1.6, [-80, 0, 10]), (3.0, [-80, 0, 10])])
    a.rot("shin_left", [(0, Z), (0.6, Z), (1.6, [80, 0, 0]), (3.0, [80, 0, 0])])
    a.rot("shin_right", [(0, Z), (0.6, Z), (1.6, [80, 0, 0]), (3.0, [80, 0, 0])])
    a.scl("crown", [(0, ONE), (0.6, [1.4, 1.8, 1.4]), (2.2, [0.6, 0.3, 0.6]), (3.0, [0.1, 0.05, 0.1])])
    a.scl("embers", [(0, ONE), (1.5, ONE), (3.0, [0.01, 0.01, 0.01])])
    A.append(a)

    a = Anim("punch", 1.1)
    a.rot("arm_right", [(0, Z), (0.35, [30, 20, -10]), (0.5, [-95, 0, 0]), (0.75, [-90, 0, 0]), (1.1, Z)])
    a.rot("forearm_right", [(0, Z), (0.35, [-60, 0, 0]), (0.5, [-5, 0, 0]), (0.75, [-5, 0, 0]), (1.1, Z)])
    a.rot("chest", [(0, Z), (0.35, [0, -25, 0]), (0.5, [10, 20, 0]), (0.75, [8, 18, 0]), (1.1, Z)])
    a.rot("arm_left", [(0, Z), (0.35, [-30, 0, 10]), (0.5, [20, 0, 10]), (1.1, Z)])
    A.append(a)

    a = Anim("slam", 1.8)
    for side, s in (("left", 1), ("right", -1)):
        a.rot("arm_" + side, [(0, Z), (0.6, [-170, 0, 10 * s]), (0.75, [-175, 0, 10 * s]), (0.9, [-45, 0, 5 * s]),
                              (1.4, [-40, 0, 5 * s]), (1.8, Z)])
        a.rot("forearm_" + side, [(0, Z), (0.6, [-20, 0, 0]), (0.9, [-30, 0, 0]), (1.4, [-30, 0, 0]), (1.8, Z)])
        a.rot("leg_" + side, [(0, Z), (0.6, [5, 0, 0]), (0.9, [-20, 0, 0]), (1.4, [-20, 0, 0]), (1.8, Z)])
        a.rot("shin_" + side, [(0, Z), (0.9, [25, 0, 0]), (1.4, [25, 0, 0]), (1.8, Z)])
    a.rot("chest", [(0, Z), (0.6, [-15, 0, 0]), (0.9, [35, 0, 0]), (1.4, [30, 0, 0]), (1.8, Z)])
    a.rot("jaw", [(0, Z), (0.6, [10, 0, 0]), (0.9, [25, 0, 0]), (1.4, [10, 0, 0]), (1.8, Z)])
    a.pos("body", [(0, Z), (0.6, [0, 1, 0]), (0.9, [0, -4, 0]), (1.4, [0, -4, 0]), (1.8, Z)])
    a.pos("hips", [(0, Z), (0.9, [0, -3, 0]), (1.4, [0, -3, 0]), (1.8, Z)])
    A.append(a)

    a = Anim("fireball", 1.6)
    a.rot("arm_right", [(0, Z), (0.5, [-80, 0, 0]), (0.6, [-95, 0, 0]), (0.8, [-70, 0, 0]), (0.95, [-95, 0, 0]),
                        (1.1, [-70, 0, 0]), (1.3, [-95, 0, 0]), (1.6, Z)])
    a.rot("forearm_right", [(0, Z), (0.5, [-20, 0, 0]), (0.6, [0, 0, 0]), (0.8, [-20, 0, 0]), (0.95, Z),
                            (1.1, [-20, 0, 0]), (1.3, Z), (1.6, Z)])
    a.rot("arm_left", [(0, Z), (0.5, [-40, 0, 20]), (1.3, [-40, 0, 20]), (1.6, Z)])
    a.rot("chest", [(0, Z), (0.5, [0, 15, 0]), (1.3, [5, 15, 0]), (1.6, Z)])
    A.append(a)

    # O corpo inteiro varre o leque pelo código (gira o Ignis); a cabeça só abaixa e abre a boca
    a = Anim("breath", 2.2)
    a.rot("chest", [(0, Z), (0.4, [-15, 0, 0]), (0.6, [15, 0, 0]), (1.9, [15, 0, 0]), (2.2, Z)])
    a.rot("head", [(0, Z), (0.4, [-15, 0, 0]), (0.6, [12, 0, 0]), (1.9, [12, 0, 0]), (2.2, Z)])
    a.rot("jaw", [(0, Z), (0.4, [5, 0, 0]), (0.6, [32, 0, 0]), (1.9, [32, 0, 0]), (2.2, Z)])
    a.rot("arm_left", [(0, Z), (0.6, [20, 0, 15]), (1.9, [20, 0, 15]), (2.2, Z)])
    a.rot("arm_right", [(0, Z), (0.6, [20, 0, -15]), (1.9, [20, 0, -15]), (2.2, Z)])
    a.scl("crown", [(0, ONE), (0.6, [1.2, 1.4, 1.2]), (1.9, [1.2, 1.4, 1.2]), (2.2, ONE)])
    A.append(a)

    a = Anim("meteor", 3.2)
    a.rot("arm_left", [(0, Z), (0.8, [-175, 0, 20]), (2.8, [-175, 0, 22]), (3.2, Z)])
    a.rot("arm_right", [(0, Z), (0.8, [-175, 0, -20]), (2.8, [-175, 0, -22]), (3.2, Z)])
    a.rot("forearm_left", [(0, Z), (0.8, [-10, 0, 0]), (2.8, [-10, 0, 0]), (3.2, Z)])
    a.rot("forearm_right", [(0, Z), (0.8, [-10, 0, 0]), (2.8, [-10, 0, 0]), (3.2, Z)])
    a.rot("chest", [(0, Z), (0.8, [-20, 0, 0]), (2.8, [-20, 0, 0]), (3.2, Z)])
    a.rot("head", [(0, Z), (0.8, [-35, 0, 0]), (2.8, [-35, 0, 0]), (3.2, Z)])
    a.rot("jaw", [(0, Z), (0.8, [15, 0, 0]), (2.8, [15, 0, 0]), (3.2, Z)])
    a.scl("crown", [(0, ONE), (0.8, [1.3, 1.6, 1.3]), (2.8, [1.3, 1.6, 1.3]), (3.2, ONE)])
    a.rot("embers", spin_frames(3.2, 3))
    A.append(a)

    a = Anim("eruption", 2.2)
    a.rot("chest", [(0, Z), (0.5, [-10, 0, 0]), (0.7, [45, 0, 0]), (1.5, [45, 0, 0]), (1.6, [-15, 0, 0]), (2.2, Z)])
    for side, s in (("left", 1), ("right", -1)):
        a.rot("arm_" + side, [(0, Z), (0.5, [-150, 0, 15 * s]), (0.7, [-60, 0, 10 * s]), (1.5, [-60, 0, 10 * s]),
                              (1.6, [-120, 0, 40 * s]), (2.2, Z)])
    a.pos("body", [(0, Z), (0.7, [0, -5, 0]), (1.5, [0, -5, 0]), (1.6, [0, 1, 0]), (2.2, Z)])
    a.pos("hips", [(0, Z), (0.7, [0, -3, 0]), (1.5, [0, -3, 0]), (2.2, Z)])
    a.rot("leg_left", [(0, Z), (0.7, [-25, 0, 0]), (1.5, [-25, 0, 0]), (2.2, Z)])
    a.rot("leg_right", [(0, Z), (0.7, [-25, 0, 0]), (1.5, [-25, 0, 0]), (2.2, Z)])
    a.rot("shin_left", [(0, Z), (0.7, [30, 0, 0]), (1.5, [30, 0, 0]), (2.2, Z)])
    a.rot("shin_right", [(0, Z), (0.7, [30, 0, 0]), (1.5, [30, 0, 0]), (2.2, Z)])
    A.append(a)

    a = Anim("supernova", 4.0)
    shake = [(0.5 + i * 0.1, [30, 2 * (1 if i % 2 else -1), 0]) for i in range(26)]
    a.rot("chest", [(0, Z)] + shake + [(3.1, [-25, 0, 0]), (3.6, [-25, 0, 0]), (4.0, Z)])
    a.rot("arm_left", [(0, Z), (0.5, [-70, 0, -35]), (3.0, [-70, 0, -35]), (3.1, [-150, 0, 60]), (3.6, [-150, 0, 60]), (4.0, Z)])
    a.rot("arm_right", [(0, Z), (0.5, [-70, 0, 35]), (3.0, [-70, 0, 35]), (3.1, [-150, 0, -60]), (3.6, [-150, 0, -60]), (4.0, Z)])
    a.rot("head", [(0, Z), (0.5, [25, 0, 0]), (3.0, [25, 0, 0]), (3.1, [-30, 0, 0]), (3.6, [-30, 0, 0]), (4.0, Z)])
    a.rot("jaw", [(0, Z), (3.0, Z), (3.1, [30, 0, 0]), (3.6, [30, 0, 0]), (4.0, Z)])
    a.pos("body", [(0, Z), (0.5, [0, -6, 0]), (3.0, [0, -6, 0]), (3.1, Z), (4.0, Z)])
    a.pos("hips", [(0, Z), (0.5, [0, -4, 0]), (3.0, [0, -4, 0]), (3.1, Z), (4.0, Z)])
    for side in ("left", "right"):
        a.rot("leg_" + side, [(0, Z), (0.5, [-30, 0, 0]), (3.0, [-30, 0, 0]), (3.1, Z), (4.0, Z)])
        a.rot("shin_" + side, [(0, Z), (0.5, [40, 0, 0]), (3.0, [40, 0, 0]), (3.1, Z), (4.0, Z)])
    a.scl("crown", [(0, ONE), (0.5, [0.7, 0.6, 0.7]), (3.0, [0.7, 0.6, 0.7]), (3.1, [1.8, 2.0, 1.8]), (3.6, [1.6, 1.8, 1.6]), (4.0, ONE)])
    a.rot("embers", spin_frames(4.0, 4))
    A.append(a)
    A.extend(ignis_anims_v07())
    return A


def mirror(frames):
    """Espelha rotações (y e z trocam de sinal) para o lado direito."""
    return [(t, [v[0], -v[1], -v[2]]) for t, v in frames]


def both(a, bone, frames):
    """Aplica a rotação no osso _left e a espelhada no _right."""
    a.rot(bone + "_left", frames)
    a.rot(bone + "_right", mirror(frames))


def ignis_anims_v07():
    A = []
    # Apresentação (3.5 s, segura no fim): ajoelhado e apagado -> ergue-se -> ruge -> bate os punhos no chão (2.6 s)
    L = 3.5
    a = Anim("intro", L)
    a.pos("body", [(0, [0, -12, 0]), (0.8, [0, -12, 0]), (1.6, [0, -2, 0]), (2.0, [0, 1, 0]), (2.4, [0, 1, 0]),
                   (2.6, [0, -4, 0]), (3.0, [0, -1, 0]), (3.5, Z)])
    a.pos("hips", [(0, [0, -9, 0]), (0.8, [0, -9, 0]), (1.6, [0, -1, 0]), (2.0, Z), (2.6, [0, -3, 0]), (3.5, Z)])
    both(a, "leg", [(0, [-75, 0, -8]), (0.8, [-75, 0, -8]), (1.6, [-15, 0, -4]), (2.0, Z), (2.6, [-18, 0, 0]), (3.5, Z)])
    both(a, "shin", [(0, [78, 0, 0]), (0.8, [78, 0, 0]), (1.6, [20, 0, 0]), (2.0, Z), (2.6, [22, 0, 0]), (3.5, Z)])
    a.rot("chest", [(0, [40, 0, 0]), (0.8, [38, 0, 0]), (1.6, [-8, 0, 0]), (2.0, [-22, 0, 0]), (2.4, [-22, 0, 0]),
                    (2.6, [30, 0, 0]), (3.0, [10, 0, 0]), (3.5, Z)])
    a.rot("head", [(0, [35, 0, 0]), (0.8, [30, 0, 0]), (1.2, Z), (1.6, [-20, 0, 0]), (2.0, [-30, 0, 0]), (2.4, [-30, 0, 0]),
                   (2.6, [15, 0, 0]), (3.5, Z)])
    a.rot("jaw", [(0, Z), (1.6, Z), (2.0, [30, 0, 0]), (2.4, [30, 0, 0]), (2.6, [8, 0, 0]), (3.5, Z)])
    both(a, "arm", [(0, [-30, 0, 8]), (0.8, [-30, 0, 8]), (1.6, [-40, 0, 40]), (2.0, [-30, 0, 55]), (2.4, [-165, 0, 15]),
                    (2.6, [-45, 0, 5]), (3.0, [-30, 0, 5]), (3.5, Z)])
    both(a, "forearm", [(0, [-30, 0, 0]), (1.6, [-30, 0, 0]), (2.0, [-40, 0, 0]), (2.4, [-10, 0, 0]), (2.6, [-20, 0, 0]), (3.5, Z)])
    a.scl("crown", [(0, [0.2, 0.1, 0.2]), (0.8, [0.3, 0.2, 0.3]), (1.6, ONE), (2.0, [1.5, 1.9, 1.5]), (2.4, [1.5, 1.9, 1.5]),
                    (2.6, [1.2, 1.3, 1.2]), (3.5, ONE)])
    a.scl("embers", [(0, [0.01, 0.01, 0.01]), (1.0, [0.01, 0.01, 0.01]), (1.8, ONE), (3.5, ONE)])
    a.rot("embers", spin_frames(L, 3))
    A.append(a)

    # Troca de fase (2.5 s): encolhe tremendo e explode em chamas, braços abertos
    L = 2.5
    a = Anim("phase", L)
    tremble = [(0.4 + i * 0.1, [38 if i % 2 else 32, 0, 0]) for i in range(6)]
    a.rot("chest", [(0, Z), (0.4, [35, 0, 0])] + tremble + [(1.3, [-25, 0, 0]), (2.0, [-25, 0, 0]), (2.5, Z)])
    a.rot("head", [(0, Z), (0.4, [25, 0, 0]), (1.0, [25, 0, 0]), (1.3, [-35, 0, 0]), (2.0, [-35, 0, 0]), (2.5, Z)])
    a.rot("jaw", [(0, Z), (1.0, Z), (1.3, [32, 0, 0]), (2.0, [32, 0, 0]), (2.5, Z)])
    both(a, "arm", [(0, Z), (0.4, [-20, 0, -10]), (1.0, [-30, 0, -5]), (1.3, [-140, 0, 70]), (2.0, [-140, 0, 70]), (2.5, Z)])
    both(a, "forearm", [(0, Z), (0.4, [-70, 0, 0]), (1.0, [-75, 0, 0]), (1.3, [-10, 0, 0]), (2.0, [-10, 0, 0]), (2.5, Z)])
    a.pos("body", [(0, Z), (0.4, [0, -4, 0]), (1.0, [0, -5, 0]), (1.3, [0, 1, 0]), (2.0, [0, 1, 0]), (2.5, Z)])
    both(a, "leg", [(0, Z), (0.4, [-15, 0, 0]), (1.0, [-15, 0, 0]), (1.3, Z), (2.5, Z)])
    both(a, "shin", [(0, Z), (0.4, [20, 0, 0]), (1.0, [20, 0, 0]), (1.3, Z), (2.5, Z)])
    a.scl("crown", [(0, ONE), (0.4, [0.6, 0.5, 0.6]), (1.0, [0.5, 0.4, 0.5]), (1.3, [2.0, 2.4, 2.0]), (2.0, [1.8, 2.2, 1.8]), (2.5, ONE)])
    a.scl("embers", [(0, ONE), (1.0, [0.5, 0.5, 0.5]), (1.3, [1.6, 1.6, 1.6]), (2.0, [1.4, 1.4, 1.4]), (2.5, ONE)])
    a.rot("embers", spin_frames(L, 3))
    A.append(a)

    # Salto anti-trapaça (2.3 s): agacha, salta com os braços para cima, cai socando o chão
    L = 2.3
    a = Anim("leap", L)
    a.pos("body", [(0, Z), (0.35, [0, -6, 0]), (0.45, [0, 2, 0]), (1.3, [0, 1, 0]), (1.5, [0, -5, 0]), (1.9, [0, -4, 0]), (L, Z)])
    both(a, "leg", [(0, Z), (0.35, [-40, 0, 0]), (0.45, [20, 0, 0]), (1.3, [-10, 0, 0]), (1.5, [-35, 0, 0]), (1.9, [-30, 0, 0]), (L, Z)])
    both(a, "shin", [(0, Z), (0.35, [60, 0, 0]), (0.45, Z), (1.3, [30, 0, 0]), (1.5, [50, 0, 0]), (1.9, [45, 0, 0]), (L, Z)])
    both(a, "arm", [(0, Z), (0.35, [30, 0, 15]), (0.45, [-170, 0, 15]), (1.3, [-175, 0, 15]), (1.5, [-40, 0, 5]), (1.9, [-40, 0, 5]), (L, Z)])
    a.rot("chest", [(0, Z), (0.35, [25, 0, 0]), (0.45, [-20, 0, 0]), (1.3, [-15, 0, 0]), (1.5, [35, 0, 0]), (1.9, [30, 0, 0]), (L, Z)])
    a.scl("crown", [(0, ONE), (0.45, [1.4, 1.8, 1.4]), (1.5, [1.6, 2.0, 1.6]), (L, ONE)])
    A.append(a)

    # Chuva de Brasas (2.8 s): braços erguidos; a cada onda (0.4/1.0/1.6/2.2 s) empurra as chamas para o céu
    L = 2.8
    a = Anim("ember_rain", L)
    up, push = [-150, 0, 30], [-178, 0, 18]
    keys = [(0, Z), (0.3, up)]
    for w in (0.4, 1.0, 1.6, 2.2):
        keys += [(w, push), (w + 0.3, up)]
    keys.append((L, Z))
    both(a, "arm", sorted(keys, key=lambda k: k[0]))
    both(a, "forearm", [(0, Z), (0.3, [-25, 0, 0]), (2.5, [-25, 0, 0]), (L, Z)])
    chest = [(0, Z), (0.3, [-12, 0, 0])]
    for w in (0.4, 1.0, 1.6, 2.2):
        chest += [(w, [-22, 0, 0]), (w + 0.3, [-12, 0, 0])]
    a.rot("chest", sorted(chest + [(L, Z)], key=lambda k: k[0]))
    a.rot("head", [(0, Z), (0.3, [-30, 0, 0]), (2.5, [-30, 0, 0]), (L, Z)])
    a.rot("jaw", [(0, Z), (0.3, [20, 0, 0]), (2.5, [20, 0, 0]), (L, Z)])
    crown = [(0, ONE), (0.3, [1.2, 1.4, 1.2])]
    for w in (0.4, 1.0, 1.6, 2.2):
        crown += [(w, [1.6, 2.1, 1.6]), (w + 0.3, [1.2, 1.4, 1.2])]
    a.scl("crown", sorted(crown + [(L, ONE)], key=lambda k: k[0]))
    a.rot("embers", spin_frames(L, 5))
    A.append(a)

    # Investida Flamejante (2.2 s): agacha com os braços para trás (até 0.8 s), corre (0.8–1.5 s), freia
    L = 2.2
    a = Anim("flame_charge", L)
    a.rot("chest", [(0, Z), (0.6, [35, 0, 0]), (0.8, [45, 0, 0]), (1.5, [45, 0, 0]), (1.8, [10, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.6, [-25, 0, 0]), (1.5, [-25, 0, 0]), (L, Z)])
    a.rot("jaw", [(0, Z), (0.6, [18, 0, 0]), (1.5, [18, 0, 0]), (L, Z)])
    both(a, "arm", [(0, Z), (0.6, [40, 0, 25]), (0.8, [60, 0, 20]), (1.5, [60, 0, 20]), (1.8, [-20, 0, 10]), (L, Z)])
    both(a, "forearm", [(0, Z), (0.6, [-30, 0, 0]), (1.5, [-30, 0, 0]), (L, Z)])
    a.pos("body", [(0, Z), (0.6, [0, -4, 0]), (0.8, [0, -3, 0]), (1.5, [0, -3, 0]), (L, Z)])
    run_l, run_r = [(0, Z), (0.6, [-25, 0, 0]), (0.8, [-25, 0, 0])], [(0, Z), (0.6, [-25, 0, 0]), (0.8, [-25, 0, 0])]
    t, k = 0.8, 0
    while t < 1.5:
        t = round(t + 0.1, 3)
        v = 40 if k % 2 == 0 else -30
        run_l.append((min(t, 1.5), [-v, 0, 0]))
        run_r.append((min(t, 1.5), [v, 0, 0]))
        k += 1
    run_l += [(1.8, [-15, 0, 0]), (L, Z)]
    run_r += [(1.8, [-15, 0, 0]), (L, Z)]
    a.rot("leg_left", run_l)
    a.rot("leg_right", run_r)
    both(a, "shin", [(0, Z), (0.6, [35, 0, 0]), (0.8, [25, 0, 0]), (1.5, [25, 0, 0]), (L, Z)])
    a.scl("crown", [(0, ONE), (0.6, [1.4, 1.8, 1.4]), (1.5, [1.5, 1.2, 1.5]), (L, ONE)])
    A.append(a)
    return A


# =====================================================================================
#  MARÉ — naga das profundezas
# =====================================================================================
TIDE = {
    "scale_dark": hex_rgba("#134c58"), "scale": hex_rgba("#1f7a8c"), "scale_light": hex_rgba("#2bb3b1"),
    "belly": hex_rgba("#9fe3d2"), "belly_dark": hex_rgba("#6cc7b8"),
    "skin": hex_rgba("#4f9fb5"), "skin_light": hex_rgba("#79c3d3"),
    "fin": hex_rgba("#5fd3e6"), "fin_light": hex_rgba("#a8f0ff"), "fin_dark": hex_rgba("#2f9fc0"),
    "glow": hex_rgba("#7ff5ff"), "eye": hex_rgba("#d8ffff"),
    "coral": hex_rgba("#ff8f7a"), "coral_light": hex_rgba("#ffc2b0"),
    "gold": hex_rgba("#d9b45a"), "gold_dark": hex_rgba("#a07a2a"), "hair": hex_rgba("#2a5f7a"),
}


def fin_rays(fs, pal, rnd):
    for name in ("left", "right", "front", "back"):
        f = fs[name]
        for xx in range(f.w):
            if xx % 2 == 0:
                for yy in range(f.h):
                    f.set(xx, yy, pal["fin_light"])
        for xx in range(f.w):
            f.set(xx, 0, pal["glow"], True)


def tide_model():
    m = Model("tide_guardian")
    scaly = lambda extra=None: P("scale", noise=0.06, deco=chain(scales("scale_light", "scale_dark"),
                                                                  spots("glow"), extra))
    belly = lambda fs, pal, rnd: [fs["bottom"].fill(pal["belly"]),
                                  [fs["bottom"].set(xx, yy, pal["belly_dark"]) for yy in range(fs["bottom"].h)
                                   for xx in range(fs["bottom"].w) if yy % 3 == 0]]
    m.bone("root", [0, 0, 0])
    m.bone("coil", [0, 5, 0], "root")
    m.cube("coil", [-7, 0, -6], [14, 10, 13], scaly(chain(belly, lambda fs, pal, rnd: [
        fs["front"].rect(3, 0, 10, 9, pal["belly"]),
        [fs["front"].set(xx, yy, pal["belly_dark"]) for yy in range(10) for xx in range(3, 11) if yy % 3 == 2]])))
    prev = "coil"
    segs = [("tail1", [0, 5, 6], [-6, 0.5, 6], [12, 9, 11]), ("tail2", [0, 5, 17], [-5, 1, 17], [10, 8, 10]),
            ("tail3", [0, 4.5, 27], [-4, 1.5, 27], [8, 6, 10]), ("tail4", [0, 4, 37], [-3, 2, 37], [6, 5, 9])]
    for name, piv, o, s in segs:
        m.bone(name, piv, prev)
        m.cube(name, o, s, scaly(belly))
        m.cube(name, [-0.5, o[1] + s[1], o[2] + 1], [1, 2, s[2] - 2], P("fin", noise=0.03, deco=fin_rays))
        prev = name
    m.bone("tail_fin", [0, 4, 46], "tail4")
    m.cube("tail_fin", [-0.5, 0.5, 45], [1, 9, 9], P("fin", noise=0.03, deco=fin_rays))
    m.cube("tail_fin", [-6, 4, 46], [12, 1, 7], P("fin", noise=0.03, deco=chain(fin_rays, speckle("fin_light", 0.2, faces=("top",)))))

    m.bone("waist", [0, 9, -1], "root")
    m.cube("waist", [-5.5, 9, -4.5], [11, 10, 8], P("scale", noise=0.05, deco=chain(
        scales("scale_light", "scale_dark"), lambda fs, pal, rnd: fs["front"].rect(2, 0, 8, 9, pal["belly"]),
        row("front", 2, "belly_dark", x0=2, x1=8), row("front", 5, "belly_dark", x0=2, x1=8))))
    m.bone("torso", [0, 19, -1], "waist")
    m.cube("torso", [-7, 19, -5], [14, 13, 9], P("skin", noise=0.05, deco=chain(
        lambda fs, pal, rnd: [fs["front"].rect(4, 3, 9, 12, pal["skin_light"]), fs["front"].rect(5, 5, 8, 5, pal["glow"], True),
                              fs["front"].rect(6, 4, 7, 9, pal["glow"], True)],
        speckle("scale_light", 0.06, faces=("left", "right", "back")),
        row("front", 0, "gold"), row("back", 0, "gold"))))
    m.cube("torso", [-0.5, 18, 4], [1, 14, 4], P("fin", noise=0.03, deco=fin_rays))
    m.bone("head", [0, 32, -1.5], "torso")
    m.cube("head", [-2.5, 31, -3.5], [5, 3, 5], P("skin"))
    m.cube("head", [-4.5, 33, -6], [9, 9, 9], P("skin", noise=0.05, deco=chain(
        eyes([1, 2, 6, 7], 4, "eye"), row("front", 3, "scale_dark", x0=1, x1=7),
        row("front", 7, "skin_light", x0=3, x1=5),
        lambda fs, pal, rnd: [fs["front"].set(4, 1, pal["glow"], True), fs["front"].set(4, 2, pal["glow"], True)],
        speckle("scale_light", 0.1, faces=("left", "right", "top", "back")))))
    m.cube("head", [-0.5, 40, -4], [1, 6, 9], P("fin", noise=0.03, deco=fin_rays))
    for sx in (1, -1):
        m.cube("head", [4.5 if sx > 0 else -5.5, 35, -3], [1, 5, 6], P("fin", noise=0.03, deco=fin_rays),
               rotation=[0, -25 * sx, 0], pivot=[5 * sx, 35, -3])
    for o in ([-4, 42, -5], [2, 42, -5], [-1, 44, -2.5]):
        m.cube("head", o, [2, 3, 2], P("coral", noise=0.05, deco=speckle("coral_light", 0.3)))
    m.cube("head", [-4, 26, 3], [8, 9, 2], P("hair", noise=0.07, deco=chain(
        lambda fs, pal, rnd: [fs["back"].set(xx, yy, pal["scale_dark"]) for yy in range(9) for xx in range(8) if xx % 2],
        speckle("glow", 0.04, faces=("back",), glow=True))))

    for side, sx in (("left", 1), ("right", -1)):
        arm = "arm_" + side
        m.bone(arm, [7.5 * sx, 30, -1], "torso")
        m.cube(arm, [7 if sx > 0 else -12, 28, -3.5], [5, 4, 6], P("scale", deco=chain(scales("scale_light", "scale_dark"),
                                                                                         row("front", -1, "gold"))))
        m.cube(arm, [7.5 if sx > 0 else -11.5, 21, -2.5], [4, 9, 4], P("skin", deco=spots("glow", step=4)))
        m.cube(arm, [11.5 if sx > 0 else -12.5, 21, -1], [1, 8, 4], P("fin", noise=0.03, deco=fin_rays))
        fa = "forearm_" + side
        m.bone(fa, [9.5 * sx, 21, -0.5], arm)
        m.cube(fa, [7.5 if sx > 0 else -11.5, 12, -2.5], [4, 9, 4], P("skin", deco=chain(row("front", 1, "gold"), row("back", 1, "gold"),
                                                                                           row("left", 1, "gold"), row("right", 1, "gold"))))
        m.cube(fa, [7.5 if sx > 0 else -11.5, 9, -3], [4, 3, 5], P("skin_light", deco=row("front", -1, "scale_dark")))
    m.bone("trident", [-9.5, 10.5, -1], "forearm_right")
    m.cube("trident", [-10, 2, -2], [1, 38, 1], P("gold", noise=0.05, deco=speckle("gold_dark", 0.2)))
    m.cube("trident", [-12.5, 38, -2], [6, 1, 1], P("gold", deco=row("front", 0, "gold_dark")))
    for x, h in ((-12.5, 5), (-10, 7), (-7.5, 5)):
        m.cube("trident", [x, 39, -2], [1, h, 1], P("gold", deco=vgrad("gold", "glow", glow=True, top_face="glow")))
    return m


def tide_anims():
    A = []
    tails = ["tail1", "tail2", "tail3", "tail4", "tail_fin"]
    L = 3.0
    a = Anim("idle", L, True)
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 1, -i * 0.8) * (6 + i * 2), 0]))
    a.rot("waist", wave(L, lambda t: [S(t, L) * 2, 0, S(t, L, 1, 0.5) * 3]))
    a.rot("torso", wave(L, lambda t: [S(t, L) * 2, 0, 0]))
    a.rot("head", wave(L, lambda t: [0, S(t, L, 1, 1.0) * 6, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 4, 0, 6]))
    a.rot("arm_right", wave(L, lambda t: [S(t, L) * 4, 0, -6]))
    a.rot("forearm_left", wave(L, lambda t: [-15, 0, 0]))
    a.rot("forearm_right", wave(L, lambda t: [-15, 0, 0]))
    a.pos("waist", wave(L, lambda t: [0, S(t, L) * 0.5, 0]))
    A.append(a)

    L = 1.4
    a = Anim("walk", L, True)
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 1, -i * 1.0) * (12 + i * 3), 0]))
    a.rot("coil", wave(L, lambda t: [0, S(t, L) * 8, 0]))
    a.rot("waist", wave(L, lambda t: [8, -S(t, L) * 5, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 10, 0, 8]))
    a.rot("arm_right", wave(L, lambda t: [-S(t, L) * 10, 0, -8]))
    a.rot("head", wave(L, lambda t: [-6, S(t, L) * 4, 0]))
    A.append(a)

    L = 1.2
    a = Anim("swim", L, True)
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 1, -i * 1.1) * (16 + i * 4), 0]))
    a.rot("coil", wave(L, lambda t: [0, S(t, L) * 10, 0]))
    a.rot("waist", wave(L, lambda t: [32, -S(t, L) * 6, 0]))
    a.rot("head", wave(L, lambda t: [-28, 0, 0]))
    a.rot("arm_left", wave(L, lambda t: [40 + S(t, L) * 10, 0, 20]))
    a.rot("arm_right", wave(L, lambda t: [40 - S(t, L) * 10, 0, -20]))
    A.append(a)

    L = 2.0
    a = Anim("roar", L, True)
    a.rot("torso", [(0, Z), (0.3, [-20, 0, 0]), (1.3, [-22, 0, 0]), (1.7, [5, 0, 0]), (2.0, Z)])
    a.rot("head", [(0, Z), (0.3, [-30, 0, 0]), (1.3, [-30, 0, 0]), (2.0, Z)])
    a.rot("arm_left", [(0, Z), (0.3, [-150, 0, 25]), (1.3, [-150, 0, 28]), (2.0, Z)])
    a.rot("arm_right", [(0, Z), (0.3, [-150, 0, -25]), (1.3, [-150, 0, -28]), (2.0, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 3, -i * 0.9) * (10 + i * 3), 0]))
    A.append(a)

    L = 3.0
    a = Anim("death", L, False)
    a.rot("waist", [(0, Z), (0.6, [-15, 0, 0]), (1.8, [55, 0, 10]), (3.0, [65, 0, 12])])
    a.rot("torso", [(0, Z), (0.6, [-20, 0, 0]), (1.8, [20, 0, 0]), (3.0, [25, 0, 0])])
    a.rot("head", [(0, Z), (0.6, [-35, 0, 0]), (1.8, [35, 0, 0]), (3.0, [40, 0, 0])])
    a.rot("arm_left", [(0, Z), (0.6, [-120, 0, 30]), (1.8, [30, 0, 30]), (3.0, [35, 0, 35])])
    a.rot("arm_right", [(0, Z), (0.6, [-120, 0, -30]), (1.8, [30, 0, -30]), (3.0, [35, 0, -35])])
    a.pos("waist", [(0, Z), (0.6, Z), (1.8, [0, -5, 0]), (3.0, [0, -7, 0])])
    for i, t_ in enumerate(tails):
        a.rot(t_, [(0, Z), (0.6, [0, 15 * (1 if i % 2 else -1), 0]), (1.8, [0, 8, 0]), (3.0, [0, 4, 0])])
    A.append(a)

    a = Anim("tail_sweep", 1.2)
    a.rot("coil", [(0, Z), (0.4, [0, -40, 0]), (0.6, [0, 120, 0]), (0.9, [0, 100, 0]), (1.2, Z)])
    for i, t_ in enumerate(tails[:-1]):
        a.rot(t_, [(0, Z), (0.4, [0, -20, 0]), (0.6, [0, 50 - i * 5, 0]), (0.9, [0, 30, 0]), (1.2, Z)])
    a.rot("waist", [(0, Z), (0.4, [0, 25, 0]), (0.6, [0, -100, 0]), (0.9, [0, -90, 0]), (1.2, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-40, 0, 40]), (0.6, [-20, 0, 50]), (1.2, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-40, 0, -40]), (0.6, [-20, 0, -50]), (1.2, Z)])
    A.append(a)

    a = Anim("tidal_wave", 1.8)
    for side, s in (("left", 1), ("right", -1)):
        a.rot("arm_" + side, [(0, Z), (0.6, [-160, 0, 20 * s]), (0.8, [-70, 0, 10 * s]), (1.3, [-70, 0, 10 * s]), (1.8, Z)])
        a.rot("forearm_" + side, [(0, Z), (0.6, [-20, 0, 0]), (0.8, [0, 0, 0]), (1.8, Z)])
    a.rot("torso", [(0, Z), (0.6, [-15, 0, 0]), (0.8, [20, 0, 0]), (1.3, [20, 0, 0]), (1.8, Z)])
    a.rot("waist", [(0, Z), (0.6, [-8, 0, 0]), (0.8, [12, 0, 0]), (1.3, [12, 0, 0]), (1.8, Z)])
    a.rot("head", [(0, Z), (0.6, [-20, 0, 0]), (0.8, [5, 0, 0]), (1.8, Z)])
    A.append(a)

    a = Anim("water_jets", 1.5)
    a.rot("arm_right", [(0, Z), (0.4, [-60, 0, 0]), (0.5, [-95, 0, 0]), (0.7, [-60, 0, 0]), (0.85, [-95, 0, 0]),
                        (1.05, [-60, 0, 0]), (1.2, [-95, 0, 0]), (1.5, Z)])
    a.rot("forearm_right", [(0, Z), (0.4, [-30, 0, 0]), (0.5, Z), (0.7, [-30, 0, 0]), (0.85, Z),
                            (1.05, [-30, 0, 0]), (1.2, Z), (1.5, Z)])
    a.rot("trident", [(0, Z), (0.4, [-90, 0, 0]), (1.2, [-90, 0, 0]), (1.5, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-30, 0, 20]), (1.2, [-30, 0, 20]), (1.5, Z)])
    a.rot("torso", [(0, Z), (0.5, [5, 15, 0]), (1.2, [5, 15, 0]), (1.5, Z)])
    A.append(a)

    # Redemoinho (1.8 s): gira os braços enquanto os funis são marcados e os solta para baixo em 1.4 s (tick 28)
    a = Anim("whirlpool", 1.8)
    a.rot("arm_left", [(0, Z), (0.25, [-90, 30, 20]), (0.45, [-90, -30, 30]), (0.65, [-90, 30, 20]), (0.85, [-90, -30, 30]),
                       (1.05, [-90, 30, 20]), (1.3, [-115, 0, 25]), (1.4, [-30, 0, 40]), (1.6, [-30, 0, 40]), (1.8, Z)])
    a.rot("arm_right", [(0, Z), (0.25, [-90, -30, -20]), (0.45, [-90, 30, -30]), (0.65, [-90, -30, -20]), (0.85, [-90, 30, -30]),
                        (1.05, [-90, -30, -20]), (1.3, [-115, 0, -25]), (1.4, [-30, 0, -40]), (1.6, [-30, 0, -40]), (1.8, Z)])
    a.rot("torso", [(0, Z), (1.3, [-12, 0, 0]), (1.4, [15, 0, 0]), (1.6, [15, 0, 0]), (1.8, Z)])
    a.rot("waist", wave(1.8, lambda t: [0, 25 * math.sin(t * 8) * (1 - t / 1.8), 0], 18))
    A.append(a)

    a = Anim("geyser", 2.0)
    a.rot("arm_right", [(0, Z), (0.5, [-170, 0, -10]), (0.6, [-40, 0, 0]), (1.25, [-40, 0, 0]), (1.3, [-120, 0, -20]),
                        (1.7, [-120, 0, -20]), (2.0, Z)])
    a.rot("trident", [(0, Z), (0.5, Z), (0.6, [-30, 0, 0]), (1.25, [-30, 0, 0]), (1.3, Z), (2.0, Z)])
    a.rot("torso", [(0, Z), (0.5, [-10, 0, 0]), (0.6, [25, 0, 0]), (1.25, [25, 0, 0]), (1.3, [-15, 0, 0]), (2.0, Z)])
    a.rot("arm_left", [(0, Z), (0.6, [-40, 0, 30]), (1.3, [-140, 0, 30]), (1.7, [-140, 0, 30]), (2.0, Z)])
    A.append(a)

    # Tempestade das Marés (2.9 s): tridente ao céu; a cada raio marcado (0.5/1.1/1.6 s) o tridente pulsa
    L = 2.9
    a = Anim("storm_call", L)
    a.rot("arm_right", [(0, Z), (0.4, [-175, 0, -5]), (0.5, [-160, 0, -5]), (0.6, [-178, 0, -5]), (1.1, [-160, 0, -5]),
                        (1.2, [-178, 0, -5]), (1.6, [-160, 0, -5]), (1.7, [-178, 0, -5]), (2.7, [-175, 0, -5]), (L, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-120, 0, 40]), (2.7, [-125, 0, 40]), (L, Z)])
    a.rot("head", [(0, Z), (0.4, [-35, 0, 0]), (2.7, [-35, 0, 0]), (L, Z)])
    a.rot("torso", [(0, Z), (0.4, [-15, 0, 0]), (2.7, [-15, 0, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 4, -i * 0.9) * (8 + i * 3), 0]))
    A.append(a)
    A.extend(tide_anims_v07(tails))
    return A


def tide_anims_v07(tails):
    A = []
    # Apresentação (3.5 s): enrolada sobre a cauda, se ergue, abre os braços, ergue o tridente e o crava (2.6 s)
    L = 3.5
    a = Anim("intro", L)
    a.pos("waist", [(0, [0, -8, 0]), (0.8, [0, -8, 0]), (1.8, [0, 2, 0]), (2.4, [0, 2, 0]), (2.6, [0, -2, 0]), (3.5, Z)])
    a.rot("torso", [(0, [50, 0, 0]), (0.8, [48, 0, 0]), (1.8, [-12, 0, 0]), (2.2, [-15, 0, 0]), (2.6, [25, 0, 0]), (3.0, [8, 0, 0]), (L, Z)])
    a.rot("head", [(0, [30, 0, 0]), (0.8, [30, 0, 0]), (1.8, [-25, 0, 0]), (2.2, [-30, 0, 0]), (2.6, [10, 0, 0]), (L, Z)])
    a.rot("arm_left", [(0, [10, 0, -25]), (0.8, [10, 0, -25]), (1.8, [-60, 0, 65]), (2.2, [-70, 0, 70]), (2.6, [-40, 0, 40]), (L, Z)])
    a.rot("arm_right", [(0, [10, 0, 25]), (0.8, [10, 0, 25]), (1.8, [-60, 0, -65]), (2.2, [-175, 0, -5]), (2.6, [-45, 0, 0]),
                        (3.0, [-30, 0, 0]), (L, Z)])
    a.rot("trident", [(0, Z), (2.2, Z), (2.6, [-35, 0, 0]), (3.0, [-20, 0, 0]), (L, Z)])
    a.rot("coil", [(0, [0, -60, 0]), (0.8, [0, -40, 0]), (1.8, [0, 20, 0]), (2.6, [0, -10, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 3, -i * 0.9) * (14 + i * 4) * (1 - t / L) + (25 if t < 0.8 else 0), 0], 28))
    A.append(a)

    # Troca de fase (2.5 s): se encolhe com os braços cruzados e explode para cima, cauda chicoteando
    L = 2.5
    a = Anim("phase", L)
    a.rot("torso", [(0, Z), (0.4, [30, 0, 0]), (1.0, [32, 0, 0]), (1.3, [-25, 0, 0]), (2.0, [-25, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.4, [25, 0, 0]), (1.0, [25, 0, 0]), (1.3, [-35, 0, 0]), (2.0, [-35, 0, 0]), (L, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-70, 30, -35]), (1.0, [-70, 30, -35]), (1.3, [-150, 0, 40]), (2.0, [-150, 0, 40]), (L, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-70, -30, 35]), (1.0, [-70, -30, 35]), (1.3, [-150, 0, -40]), (2.0, [-150, 0, -40]), (L, Z)])
    a.pos("waist", [(0, Z), (0.4, [0, -3, 0]), (1.0, [0, -3, 0]), (1.3, [0, 2, 0]), (2.0, [0, 2, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 5, -i * 0.9) * (10 + i * 4), 0], 25))
    A.append(a)

    # Salto anti-trapaça (2.3 s): comprime a cauda, salta com o tridente erguido e cai cravando-o
    L = 2.3
    a = Anim("leap", L)
    a.pos("waist", [(0, Z), (0.35, [0, -4, 0]), (0.45, [0, 3, 0]), (1.3, [0, 2, 0]), (1.5, [0, -3, 0]), (1.9, [0, -2, 0]), (L, Z)])
    a.rot("torso", [(0, Z), (0.35, [30, 0, 0]), (0.45, [-20, 0, 0]), (1.3, [-15, 0, 0]), (1.5, [35, 0, 0]), (1.9, [25, 0, 0]), (L, Z)])
    a.rot("arm_left", [(0, Z), (0.35, [20, 0, 30]), (0.45, [-160, 0, 20]), (1.3, [-160, 0, 20]), (1.5, [-40, 0, 30]), (L, Z)])
    a.rot("arm_right", [(0, Z), (0.35, [20, 0, -30]), (0.45, [-170, 0, -10]), (1.3, [-170, 0, -10]), (1.5, [-40, 0, 0]), (L, Z)])
    a.rot("trident", [(0, Z), (1.3, Z), (1.5, [-60, 0, 0]), (1.9, [-50, 0, 0]), (L, Z)])
    a.rot("coil", [(0, Z), (0.35, [0, 30, 0]), (0.45, [0, -10, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, [(0, Z), (0.35, [0, 25 + i * 6, 0]), (0.45, [0, -10, 0]), (1.3, [0, 6, 0]), (1.5, [0, -8, 0]), (L, Z)])
    A.append(a)

    # Prisão de Bolha (1.7 s): junta as mãos formando a bolha e a empurra para a frente (0.4 s = tick 8)
    L = 1.7
    a = Anim("bubble_prison", L)
    a.rot("arm_left", [(0, Z), (0.3, [-80, -25, 25]), (0.4, [-95, 0, 10]), (1.0, [-95, 0, 10]), (L, Z)])
    a.rot("arm_right", [(0, Z), (0.3, [-80, 25, -25]), (0.4, [-95, 0, -10]), (1.0, [-95, 0, -10]), (L, Z)])
    a.rot("forearm_left", [(0, Z), (0.3, [-60, 0, 0]), (0.4, Z), (1.0, Z), (L, Z)])
    a.rot("forearm_right", [(0, Z), (0.3, [-60, 0, 0]), (0.4, Z), (1.0, Z), (L, Z)])
    a.rot("trident", [(0, Z), (0.3, [-40, 0, 0]), (1.0, [-40, 0, 0]), (L, Z)])
    a.rot("torso", [(0, Z), (0.3, [-10, 0, 0]), (0.4, [15, 0, 0]), (1.0, [12, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.3, [-10, 0, 0]), (0.4, [5, 0, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [0, S(t, L, 2, -i * 0.9) * (8 + i * 3), 0], 17))
    A.append(a)
    return A


# =====================================================================================
#  GOLEM PRIMORDIAL — colosso de pedra com musgo e cristais
# =====================================================================================
GOLEM = {
    "stone_dark": hex_rgba("#4a4744"), "stone": hex_rgba("#6b6762"), "stone_light": hex_rgba("#8a857e"),
    "moss": hex_rgba("#4f7a3a"), "moss_light": hex_rgba("#6f9f4a"), "moss_dark": hex_rgba("#355a28"),
    "dirt": hex_rgba("#6b4a2e"), "crystal": hex_rgba("#8cff6b"), "crystal_light": hex_rgba("#e0ffd0"),
    "crystal_dark": hex_rgba("#3fbf4a"), "rune": hex_rgba("#ffcf5a"), "eye": hex_rgba("#ffe680"),
    "shell": hex_rgba("#7d7870"), "shell_dark": hex_rgba("#57534d"),
}


def golem_model():
    m = Model("primordial_golem")
    stone = lambda extra=None, moss=True: P("stone", noise=0.07, deco=chain(
        bricks("stone_dark"), speckle("stone_light", 0.05), moss_top("moss", "moss_light") if moss else None, extra))
    crystal = P("crystal", noise=0.04, glow=True, deco=vgrad("crystal_dark", "crystal_light", glow=True, top_face="crystal_light"))

    m.bone("root", [0, 0, 0])
    for side, sx in (("left", 1), ("right", -1)):
        leg = "leg_" + side
        m.bone(leg, [7 * sx, 18, 0], "root")
        m.cube(leg, [2.5 if sx > 0 else -12.5, 0, -6], [10, 18, 11], stone(moss=False))
        m.cube(leg, [2 if sx > 0 else -13, 0, -8], [11, 4, 3], P("stone_dark", deco=speckle("dirt", 0.2)))
    m.bone("body", [0, 18, 0], "root")
    m.cube("body", [-11, 16, -7], [22, 8, 14], stone(moss=False))
    m.bone("torso", [0, 24, 0], "body")
    m.cube("torso", [-12, 24, -8], [24, 10, 16], stone(moss=False))
    m.cube("torso", [-17, 33, -11], [34, 20, 21], stone(rune_circle("rune", cyf=0.72, rf=0.22)))
    m.cube("torso", [-14, 50, 2], [28, 7, 10], stone())
    m.cube("torso", [-15, 53, -9], [30, 2, 18], P("moss", noise=0.1, deco=speckle("moss_light", 0.25)))
    for o, s, r in (([-10, 52, 4], [3, 9, 3], [-20, 0, 15]), ([6, 53, 2], [4, 11, 4], [-15, 0, -18]),
                    ([-2, 54, 5], [3, 7, 3], [-30, 0, 0]), ([12, 50, 6], [2, 6, 2], [-10, 0, -35]),
                    ([-15, 49, 5], [2, 5, 2], [-10, 0, 30])):
        m.cube("torso", o, s, crystal, rotation=r, pivot=[o[0] + s[0] / 2, o[1], o[2] + s[2] / 2])
    m.bone("head", [0, 47, -9], "torso")
    m.cube("head", [-6, 44, -18], [12, 10, 10], P("stone", noise=0.07, deco=chain(
        eyes([2, 3, 8, 9], 4, "eye", h=2), row("front", 7, "stone_dark", x0=3, x1=8), speckle("moss", 0.08, faces=("top",)))))
    m.cube("head", [-7, 51, -19], [14, 3, 4], P("stone_dark", deco=moss_top("moss", "moss_light")))
    m.cube("head", [-5, 42, -17], [10, 3, 8], P("stone_dark", deco=speckle("stone_light", 0.1)))
    for side, sx in (("left", 1), ("right", -1)):
        arm = "arm_" + side
        m.bone(arm, [18 * sx, 48, -1], "torso")
        m.cube(arm, [16 if sx > 0 else -29, 42, -9], [13, 13, 16], stone())
        m.cube(arm, [18 if sx > 0 else -28, 27, -5], [10, 15, 10], stone(moss=False))
        fa = "forearm_" + side
        m.bone(fa, [23 * sx, 28, -1], arm)
        m.cube(fa, [17 if sx > 0 else -30, 8, -7], [13, 20, 13], stone(row("front", 6, "rune", True, 3, 9)))
        m.cube(fa, [16 if sx > 0 else -31, 0, -8], [15, 9, 15], P("stone_dark", noise=0.08, deco=chain(
            bricks("stone"), speckle("dirt", 0.12))))
        m.cube(fa, [24 if sx > 0 else -27, 18, 4], [3, 6, 3], crystal, rotation=[20, 0, -20 * sx], pivot=[25.5 * sx, 18, 5.5])

    m.bone("shell", [0, 20, 0], "body")
    shell = lambda extra=None: P("shell", noise=0.06, deco=chain(bricks("shell_dark", bw=8, bh=5), speckle("stone_light", 0.05),
                                                                 extra))
    m.bone("shell_back", [0, 20, 0], "shell")
    m.cube("shell_back", [-22, 4, 10], [44, 52, 3], shell(rune_circle("rune", face="back")))
    m.bone("shell_left", [0, 20, 0], "shell")
    m.cube("shell_left", [22, 4, -20], [3, 52, 30], shell(rune_circle("rune", face="left")))
    m.bone("shell_right", [0, 20, 0], "shell")
    m.cube("shell_right", [-25, 4, -20], [3, 52, 30], shell(rune_circle("rune", face="right")))
    m.bone("shell_front", [0, 20, 0], "shell")
    m.cube("shell_front", [-22, 4, -23], [44, 48, 3], shell(rune_circle("rune", face="front")))
    m.bone("shell_top", [0, 20, 0], "shell")
    m.cube("shell_top", [-25, 56, -23], [50, 4, 36], shell(moss_top("moss", "moss_light")))
    return m


def golem_anims():
    A = []
    L = 4.0
    a = Anim("idle", L, True)
    a.rot("torso", wave(L, lambda t: [S(t, L) * 1.5, 0, 0]))
    a.pos("torso", wave(L, lambda t: [0, S(t, L) * 0.4, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 2, 0, 2]))
    a.rot("arm_right", wave(L, lambda t: [S(t, L) * 2, 0, -2]))
    a.rot("head", wave(L, lambda t: [0, S(t, L, 1, 1.0) * 5, 0]))
    A.append(a)

    L = 2.0
    a = Anim("walk", L, True)
    a.rot("leg_left", wave(L, lambda t: [-S(t, L) * 18, 0, 0]))
    a.rot("leg_right", wave(L, lambda t: [S(t, L) * 18, 0, 0]))
    a.rot("body", wave(L, lambda t: [0, 0, S(t, L) * 3]))
    a.pos("body", wave(L, lambda t: [0, -abs(S(t, L)) * 1.5, 0]))
    a.rot("torso", wave(L, lambda t: [6, S(t, L) * 4, 0]))
    a.rot("arm_left", wave(L, lambda t: [S(t, L) * 12, 0, 3]))
    a.rot("arm_right", wave(L, lambda t: [-S(t, L) * 12, 0, -3]))
    A.append(a)

    L = 2.5
    a = Anim("roar", L, True)
    a.rot("torso", [(0, Z), (0.4, [-20, 0, 0]), (1.6, [-22, 0, 0]), (1.8, [10, 0, 0]), (2.1, [5, 0, 0]), (2.5, Z)])
    a.rot("head", [(0, Z), (0.4, [-30, 0, 0]), (1.6, [-30, 0, 0]), (2.5, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-150, 0, 30]), (1.6, [-150, 0, 32]), (1.8, [-70, 0, -20]), (2.1, [-60, 0, -15]), (2.5, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-150, 0, -30]), (1.6, [-150, 0, -32]), (1.8, [-70, 0, 20]), (2.1, [-60, 0, 15]), (2.5, Z)])
    A.append(a)

    L = 3.0
    a = Anim("death", L, False)
    a.rot("torso", [(0, Z), (0.6, [-15, 0, 0]), (1.6, [25, 0, 5]), (3.0, [50, 0, 8])])
    a.pos("body", [(0, Z), (0.6, Z), (1.6, [0, -6, 0]), (3.0, [0, -12, 0])])
    a.rot("arm_left", [(0, Z), (0.6, [-60, 0, 20]), (1.6, [-20, 0, 30]), (3.0, [10, 0, 50])])
    a.rot("arm_right", [(0, Z), (0.6, [-60, 0, -20]), (1.6, [-20, 0, -30]), (3.0, [10, 0, -50])])
    a.rot("head", [(0, Z), (0.6, [-25, 0, 0]), (3.0, [40, 0, 0])])
    a.rot("leg_left", [(0, Z), (1.6, [-45, 0, -10]), (3.0, [-60, 0, -12])])
    a.rot("leg_right", [(0, Z), (1.6, [-45, 0, 10]), (3.0, [-60, 0, 12])])
    A.append(a)

    L = 2.0
    a = Anim("shield", L, True)
    a.rot("torso", wave(L, lambda t: [25 + S(t, L), 0, 0]))
    a.rot("arm_left", wave(L, lambda t: [-70, 0, -30]))
    a.rot("arm_right", wave(L, lambda t: [-70, 0, 30]))
    a.rot("forearm_left", wave(L, lambda t: [-40, 0, 0]))
    a.rot("forearm_right", wave(L, lambda t: [-40, 0, 0]))
    a.rot("head", wave(L, lambda t: [30, 0, 0]))
    a.pos("body", wave(L, lambda t: [0, -4 + S(t, L) * 0.3, 0]))
    a.rot("leg_left", wave(L, lambda t: [-20, 0, 0]))
    a.rot("leg_right", wave(L, lambda t: [-20, 0, 0]))
    A.append(a)

    L = 1.5
    a = Anim("stunned", L, True)
    a.rot("torso", wave(L, lambda t: [15, S(t, L) * 8, S(t, L, 1, 1.5) * 6]))
    a.rot("head", wave(L, lambda t: [20, S(t, L, 1, 0.5) * 15, 0]))
    a.rot("arm_left", wave(L, lambda t: [10, 0, 15 + S(t, L) * 4]))
    a.rot("arm_right", wave(L, lambda t: [10, 0, -15 - S(t, L) * 4]))
    a.pos("body", wave(L, lambda t: [0, -3, 0]))
    A.append(a)

    a = Anim("smash", 1.4)
    for side, s in (("left", 1), ("right", -1)):
        a.rot("arm_" + side, [(0, Z), (0.5, [-160, 0, 10 * s]), (0.75, [-50, 0, 5 * s]), (1.1, [-50, 0, 5 * s]), (1.4, Z)])
    a.rot("torso", [(0, Z), (0.5, [-15, 0, 0]), (0.75, [35, 0, 0]), (1.1, [32, 0, 0]), (1.4, Z)])
    a.pos("body", [(0, Z), (0.75, [0, -4, 0]), (1.1, [0, -4, 0]), (1.4, Z)])
    A.append(a)

    a = Anim("quake", 2.1)
    a.pos("body", [(0, Z), (0.4, [0, -5, 0]), (0.55, [0, 2, 0]), (1.1, [0, -6, 0]), (1.6, [0, -3, 0]), (2.1, Z)])
    for side, s in (("left", 1), ("right", -1)):
        a.rot("leg_" + side, [(0, Z), (0.4, [-30, 0, 0]), (0.55, [10, 0, 0]), (1.1, [-25, 0, 0]), (2.1, Z)])
        a.rot("arm_" + side, [(0, Z), (0.4, [30, 0, 20 * s]), (0.55, [-170, 0, 15 * s]), (1.1, [-30, 0, 10 * s]),
                              (1.5, [-90, 0, 10 * s]), (1.6, [-30, 0, 10 * s]), (2.1, Z)])
    a.rot("torso", [(0, Z), (0.4, [15, 0, 0]), (0.55, [-15, 0, 0]), (1.1, [30, 0, 0]), (1.6, [25, 0, 0]), (2.1, Z)])
    A.append(a)

    a = Anim("rock_throw", 2.0)
    a.rot("arm_right", [(0, Z), (0.5, [-60, 0, -10]), (0.6, [-60, 0, -10]), (1.0, [-175, 0, -5]), (1.3, [-60, 0, 0]),
                        (1.6, [-40, 0, 0]), (2.0, Z)])
    a.rot("forearm_right", [(0, Z), (0.5, [-30, 0, 0]), (1.0, [-60, 0, 0]), (1.3, Z), (2.0, Z)])
    a.rot("arm_left", [(0, Z), (1.0, [-80, 0, 30]), (1.3, [20, 0, 20]), (2.0, Z)])
    a.rot("torso", [(0, Z), (0.5, [30, -15, 0]), (1.0, [-20, 10, 0]), (1.3, [25, -10, 0]), (2.0, Z)])
    A.append(a)

    a = Anim("earth_spike", 1.5)
    a.rot("arm_left", [(0, Z), (0.4, [-150, 0, 10]), (0.6, [-40, 0, 5]), (0.8, [-40, 0, 5]), (1.0, Z), (1.5, Z)])
    a.rot("arm_right", [(0, Z), (0.8, Z), (0.9, [-150, 0, -10]), (1.1, [-40, 0, -5]), (1.3, [-40, 0, -5]), (1.5, Z)])
    a.rot("torso", [(0, Z), (0.4, [-10, 0, 0]), (0.6, [25, 10, 0]), (0.9, [-5, 0, 0]), (1.1, [25, -10, 0]), (1.5, Z)])
    A.append(a)

    # Agarrão de Raízes (1.6 s): abre os braços e pisa/afunda as mãos no chão em 0.9 s (tick 18, quando o círculo enche)
    a = Anim("root_grasp", 1.6)
    a.rot("arm_left", [(0, Z), (0.6, [-60, 0, 60]), (0.9, [-20, 0, 35]), (1.3, [-20, 0, 35]), (1.6, Z)])
    a.rot("arm_right", [(0, Z), (0.6, [-60, 0, -60]), (0.9, [-20, 0, -35]), (1.3, [-20, 0, -35]), (1.6, Z)])
    a.rot("leg_left", [(0, Z), (0.7, [-40, 0, 0]), (0.9, Z), (1.6, Z)])
    a.rot("torso", [(0, Z), (0.6, [-10, 0, 0]), (0.9, [20, 0, 0]), (1.3, [20, 0, 0]), (1.6, Z)])
    a.pos("body", [(0, Z), (0.9, [0, -3, 0]), (1.3, [0, -3, 0]), (1.6, Z)])
    A.append(a)

    a = Anim("charge", 2.5)
    a.rot("torso", [(0, Z), (0.5, [40, 0, 0]), (2.0, [42, 0, 0]), (2.5, Z)])
    a.rot("head", [(0, Z), (0.5, [-20, 0, 0]), (2.0, [-20, 0, 0]), (2.5, Z)])
    a.rot("arm_left", [(0, Z), (0.5, [50, 0, 15]), (2.0, [50, 0, 15]), (2.5, Z)])
    a.rot("arm_right", [(0, Z), (0.5, [50, 0, -15]), (2.0, [50, 0, -15]), (2.5, Z)])
    run_l = [(0, Z), (0.35, [-20, 0, 0]), (0.7, [10, 0, 0])]
    run_r = [(0, Z), (0.35, [10, 0, 0]), (0.7, [-20, 0, 0])]
    t = 0.7
    k = 0
    while t < 2.0:
        t = round(t + 0.15, 3)
        v = 35 if k % 2 == 0 else -35
        run_l.append((min(t, 2.0), [-v, 0, 0]))
        run_r.append((min(t, 2.0), [v, 0, 0]))
        k += 1
    run_l.append((2.5, Z))
    run_r.append((2.5, Z))
    a.rot("leg_left", run_l)
    a.rot("leg_right", run_r)
    A.append(a)
    A.extend(golem_anims_v07())
    return A


def golem_anims_v07():
    A = []
    # Apresentação (3.5 s): adormecido e curvado -> levanta devagar -> ergue os punhos -> esmaga o chão (2.6 s)
    L = 3.5
    a = Anim("intro", L)
    a.pos("body", [(0, [0, -6, 0]), (0.8, [0, -6, 0]), (1.8, Z), (2.2, [0, 1, 0]), (2.6, [0, -5, 0]), (3.0, [0, -2, 0]), (L, Z)])
    a.rot("torso", [(0, [45, 0, 0]), (0.8, [44, 0, 0]), (1.8, [-5, 0, 0]), (2.2, [-18, 0, 0]), (2.6, [35, 0, 0]), (3.0, [12, 0, 0]), (L, Z)])
    a.rot("head", [(0, [35, 0, 0]), (0.8, [35, 0, 0]), (1.4, [10, 0, 0]), (1.8, [-15, 0, 0]), (2.2, [-25, 0, 0]), (2.6, [15, 0, 0]), (L, Z)])
    both(a, "arm", [(0, [-45, 0, 10]), (0.8, [-45, 0, 10]), (1.8, [-20, 0, 25]), (2.2, [-170, 0, 15]), (2.6, [-50, 0, 5]),
                    (3.0, [-40, 0, 5]), (L, Z)])
    both(a, "forearm", [(0, [-20, 0, 0]), (1.8, [-20, 0, 0]), (2.2, [-15, 0, 0]), (2.6, [-30, 0, 0]), (L, Z)])
    both(a, "leg", [(0, [-25, 0, 0]), (0.8, [-25, 0, 0]), (1.8, Z), (2.6, [-15, 0, 0]), (L, Z)])
    A.append(a)

    # Troca de fase (2.5 s): ergue os punhos e esmurra o chão duas vezes
    L = 2.5
    a = Anim("phase", L)
    both(a, "arm", [(0, Z), (0.5, [-160, 0, 25]), (0.8, [-55, 0, 8]), (1.3, [-165, 0, 25]), (1.6, [-55, 0, 8]), (2.0, [-50, 0, 8]), (L, Z)])
    a.rot("torso", [(0, Z), (0.5, [-20, 0, 0]), (0.8, [32, 0, 0]), (1.3, [-22, 0, 0]), (1.6, [35, 0, 0]), (2.0, [30, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.5, [-30, 0, 0]), (0.8, [10, 0, 0]), (1.3, [-30, 0, 0]), (1.6, [10, 0, 0]), (L, Z)])
    a.pos("body", [(0, Z), (0.8, [0, -4, 0]), (1.3, [0, 1, 0]), (1.6, [0, -5, 0]), (2.0, [0, -4, 0]), (L, Z)])
    A.append(a)

    # Salto anti-trapaça (2.3 s)
    L = 2.3
    a = Anim("leap", L)
    a.pos("body", [(0, Z), (0.35, [0, -6, 0]), (0.45, [0, 2, 0]), (1.3, [0, 1, 0]), (1.5, [0, -6, 0]), (1.9, [0, -5, 0]), (L, Z)])
    a.rot("torso", [(0, Z), (0.35, [25, 0, 0]), (0.45, [-15, 0, 0]), (1.3, [-10, 0, 0]), (1.5, [35, 0, 0]), (1.9, [30, 0, 0]), (L, Z)])
    both(a, "arm", [(0, Z), (0.35, [40, 0, 15]), (0.45, [-160, 0, 20]), (1.3, [-165, 0, 20]), (1.5, [-50, 0, 5]), (1.9, [-45, 0, 5]), (L, Z)])
    both(a, "leg", [(0, Z), (0.35, [-25, 0, 0]), (0.45, [15, 0, 0]), (1.3, [-20, 0, 0]), (1.5, [-25, 0, 0]), (L, Z)])
    A.append(a)

    # Pilares Ascendentes (2.2 s): ergue os punhos e golpeia o chão em 0.9 s (tick 18), segurando enquanto os pilares sobem
    L = 2.2
    a = Anim("rising_pillars", L)
    both(a, "arm", [(0, Z), (0.6, [-170, 0, 10]), (0.75, [-175, 0, 12]), (0.9, [-45, 0, 5]), (1.6, [-45, 0, 5]), (L, Z)])
    both(a, "forearm", [(0, Z), (0.6, [-15, 0, 0]), (0.9, [-25, 0, 0]), (1.6, [-25, 0, 0]), (L, Z)])
    a.rot("torso", [(0, Z), (0.6, [-15, 0, 0]), (0.9, [35, 0, 0]), (1.6, [32, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.6, [-20, 0, 0]), (0.9, [10, 0, 0]), (L, Z)])
    a.pos("body", [(0, Z), (0.6, [0, 1, 0]), (0.9, [0, -5, 0]), (1.6, [0, -4, 0]), (L, Z)])
    both(a, "leg", [(0, Z), (0.9, [-15, 0, 0]), (1.6, [-15, 0, 0]), (L, Z)])
    A.append(a)

    # Arremesso de Placa (2.0 s): agarra o chão (0.4 s), ergue a placa (0.9 s), traz ao peito e arremessa (1.3 s)
    L = 2.0
    a = Anim("slab_throw", L)
    both(a, "arm", [(0, Z), (0.4, [-40, 0, 20]), (0.9, [-175, 0, 15]), (1.0, [-175, 0, 15]), (1.25, [-115, 0, 12]),
                    (1.35, [-75, 0, 5]), (1.6, [-60, 0, 5]), (L, Z)])
    both(a, "forearm", [(0, Z), (0.4, [-10, 0, 0]), (0.9, [-20, 0, 0]), (1.25, [-30, 0, 0]), (1.35, Z), (L, Z)])
    a.rot("torso", [(0, Z), (0.4, [40, 0, 0]), (0.9, [-10, 0, 0]), (1.0, [-12, 0, 0]), (1.25, [-5, 0, 0]), (1.35, [22, 0, 0]),
                    (1.6, [15, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.4, [20, 0, 0]), (0.9, [-10, 0, 0]), (1.35, [5, 0, 0]), (L, Z)])
    a.pos("body", [(0, Z), (0.4, [0, -5, 0]), (0.9, Z), (1.35, [0, -2, 0]), (L, Z)])
    a.rot("leg_left", [(0, Z), (0.4, [-20, 0, 0]), (1.25, [-10, 0, 0]), (1.35, [-30, 0, 0]), (L, Z)])
    a.rot("leg_right", [(0, Z), (0.4, [-20, 0, 0]), (1.35, [15, 0, 0]), (L, Z)])
    A.append(a)
    return A


# =====================================================================================
#  ZÉFIRO — djinn-águia dos ventos
# =====================================================================================
ZEPHYR = {
    "feather_white": hex_rgba("#eef8ff"), "feather": hex_rgba("#c9e8ff"), "feather_blue": hex_rgba("#8fc8f0"),
    "feather_dark": hex_rgba("#5a8fc8"), "skin": hex_rgba("#8fd3e0"), "skin_light": hex_rgba("#b8eef5"),
    "wind": hex_rgba("#9ff0d0"), "wind_light": hex_rgba("#e0fff5"), "gold": hex_rgba("#ffd76b"),
    "gold_dark": hex_rgba("#c99a2e"), "eye": hex_rgba("#e8fff8"), "sash": hex_rgba("#3f6fb8"), "sash_light": hex_rgba("#5f8fd8"),
    "beak": hex_rgba("#f2c14e"),
}


def zephyr_model():
    m = Model("zephyr")
    wisp = P("wind", noise=0.02, glow=True, deco=vgrad("wind", "wind_light", glow=True))
    m.bone("root", [0, 0, 0])
    m.bone("tail", [0, 22, 0], "root")
    m.cube("tail", [-4.5, 14, -3.5], [9, 9, 7], P("skin", noise=0.05, deco=chain(vgrad("wind", "skin"), speckle("wind_light", 0.08, glow=True))))
    m.bone("tail2", [0, 14, 0], "tail")
    m.cube("tail2", [-3, 7, -2.5], [6, 8, 5], P("wind", noise=0.03, deco=vgrad("wind_light", "wind", glow=True)))
    m.bone("tail3", [0, 7, 0], "tail2")
    m.cube("tail3", [-1.5, 0, -1.5], [3, 7, 3], wisp)
    m.bone("body", [0, 23, 0], "root")
    m.cube("body", [-5, 23, -3.5], [10, 4, 7], P("sash", deco=chain(row("front", 0, "gold"), row("front", -1, "gold"),
                                                                   speckle("sash_light", 0.15))))
    m.bone("torso", [0, 27, 0], "body")
    m.cube("torso", [-4.5, 27, -3], [9, 5, 6], P("skin", deco=row("front", 2, "skin_light", x0=3, x1=5)))
    m.cube("torso", [-6.5, 32, -4], [13, 9, 8], P("skin", noise=0.05, deco=chain(
        lambda fs, pal, rnd: [fs["front"].rect(2, 1, 10, 4, pal["skin_light"])], speckle("wind", 0.05, glow=True))))
    m.cube("torso", [-7.5, 39, -4.5], [15, 3, 9], P("feather_white", deco=feathers("feather_white", "feather", "feather_blue",
                                                                                 faces=("front", "back", "left", "right"), width=2)))
    m.cube("torso", [-1.5, 35, -4.5], [3, 3, 1], P("gold", glow=True, deco=lambda fs, pal, rnd: fs["front"].set(1, 1, pal["wind_light"], True)))
    m.bone("head", [0, 41, -0.5], "torso")
    m.cube("head", [-3.5, 41, -4], [7, 8, 7], P("feather_white", noise=0.04, deco=chain(
        eyes([1, 5], 3, "eye"), row("front", 2, "feather_dark", x0=0, x1=2), row("front", 2, "feather_dark", x0=4, x1=6),
        speckle("feather", 0.2, faces=("left", "right", "back", "top")))))
    m.cube("head", [-1.5, 43, -6], [3, 3, 2], P("beak", deco=row("bottom", 0, "gold_dark")))
    m.cube("head", [-1, 42, -7], [2, 2, 1], P("gold_dark"))
    m.cube("head", [-0.5, 48, -3], [1, 6, 7], P("feather", deco=chain(
        feathers("feather_white", "feather_blue", "feather_dark", faces=("left", "right"), width=2, glow_tip="wind"))))
    for sx in (1, -1):
        m.cube("head", [3.5 if sx > 0 else -6.5, 44, -1], [3, 1, 5], P("feather_blue", deco=speckle("wind", 0.2, glow=True)),
               rotation=[0, 0, 25 * sx], pivot=[3.5 * sx, 44, 1.5])
    for side, sx in (("left", 1), ("right", -1)):
        arm = "arm_" + side
        m.bone(arm, [7.5 * sx, 38, -0.5], "torso")
        m.cube(arm, [6.5 if sx > 0 else -10.5, 37, -2.5], [4, 3, 5], P("gold", deco=speckle("gold_dark", 0.2)))
        m.cube(arm, [7 if sx > 0 else -10, 30, -1.5], [3, 8, 3], P("skin"))
        fa = "forearm_" + side
        m.bone(fa, [8.5 * sx, 30, -0.5], arm)
        m.cube(fa, [7 if sx > 0 else -10, 22, -1.5], [3, 8, 3], P("skin", deco=row("front", -1, "skin_light")))
        m.cube(fa, [6.5 if sx > 0 else -10.5, 24, -2], [4, 3, 4], P("gold", deco=chain(row("front", 1, "wind", True), speckle("gold_dark", 0.2))))
        m.cube(fa, [7 if sx > 0 else -10, 20, -2], [3, 2, 3], P("gold_dark", deco=row("front", -1, "wind_light", True)))
        wing = "wing_" + side
        m.bone(wing, [3 * sx, 38, 3.5], "torso")
        m.cube(wing, [3 if sx > 0 else -19, 37, 3], [16, 3, 2], P("feather", deco=speckle("feather_white", 0.3)))
        m.cube(wing, [4 if sx > 0 else -19, 25, 3.5], [15, 12, 1], P("feather", deco=feathers(
            "feather_white", "feather", "feather_blue", glow_tip=None)))
        tip = "wing_" + side + "_tip"
        m.bone(tip, [19 * sx, 38, 4], wing)
        m.cube(tip, [19 if sx > 0 else -32, 37, 3], [13, 2, 2], P("feather", deco=speckle("feather_white", 0.3)))
        m.cube(tip, [19 if sx > 0 else -33, 22, 3.5], [14, 15, 1], P("feather_blue", deco=feathers(
            "feather", "feather_blue", "feather_dark", glow_tip="wind")))
    return m


def zephyr_anims():
    A = []
    tails = ["tail", "tail2", "tail3"]

    def flap(a, L, amp, base, k=1):
        a.rot("wing_left", wave(L, lambda t: [0, 0, base + S(t, L, k) * amp]))
        a.rot("wing_right", wave(L, lambda t: [0, 0, -(base + S(t, L, k) * amp)]))
        a.rot("wing_left_tip", wave(L, lambda t: [0, 0, S(t, L, k, -0.7) * amp * 0.6]))
        a.rot("wing_right_tip", wave(L, lambda t: [0, 0, -S(t, L, k, -0.7) * amp * 0.6]))

    L = 1.2
    a = Anim("idle", L, True)
    flap(a, L, 25, 15)
    a.pos("root", wave(L, lambda t: [0, S(t, L, 1, -1.2) * 1.5, 0]))
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [S(t, L, 1, -i * 0.8) * (8 + i * 3), S(t, L, 1, -i) * 6, 0]))
    a.rot("arm_left", wave(L, lambda t: [-10 + S(t, L) * 3, 0, 10]))
    a.rot("arm_right", wave(L, lambda t: [-10 + S(t, L) * 3, 0, -10]))
    A.append(a)

    # Só "fly": o Zéfiro nunca anda (no chão, depois do mergulho, usa "exposed")
    L = 0.9
    for name in ("fly",):
        a = Anim(name, L, True)
        flap(a, L, 35, 10)
        a.rot("torso", wave(L, lambda t: [25, 0, 0]))
        a.rot("head", wave(L, lambda t: [-20, 0, 0]))
        a.rot("tail", wave(L, lambda t: [40 + S(t, L) * 6, 0, 0]))
        a.rot("tail2", wave(L, lambda t: [15 + S(t, L, 1, -0.8) * 10, 0, 0]))
        a.rot("tail3", wave(L, lambda t: [10 + S(t, L, 1, -1.6) * 14, 0, 0]))
        a.rot("arm_left", wave(L, lambda t: [30, 0, 15]))
        a.rot("arm_right", wave(L, lambda t: [30, 0, -15]))
        a.pos("root", wave(L, lambda t: [0, S(t, L, 1, -1.2) * 1.0, 0]))
        A.append(a)

    L = 2.0
    a = Anim("roar", L, True)
    a.rot("wing_left", [(0, Z), (0.3, [0, 0, 50]), (1.4, [0, 0, 55]), (1.6, [0, 0, -10]), (1.8, [0, 0, 40]), (2.0, Z)])
    a.rot("wing_right", [(0, Z), (0.3, [0, 0, -50]), (1.4, [0, 0, -55]), (1.6, [0, 0, 10]), (1.8, [0, 0, -40]), (2.0, Z)])
    a.rot("wing_left_tip", [(0, Z), (0.3, [0, 0, 20]), (1.4, [0, 0, 20]), (2.0, Z)])
    a.rot("wing_right_tip", [(0, Z), (0.3, [0, 0, -20]), (1.4, [0, 0, -20]), (2.0, Z)])
    a.rot("torso", [(0, Z), (0.3, [-20, 0, 0]), (1.4, [-20, 0, 0]), (2.0, Z)])
    a.rot("head", [(0, Z), (0.3, [-30, 0, 0]), (1.4, [-30, 0, 0]), (2.0, Z)])
    a.rot("arm_left", [(0, Z), (0.3, [-100, 0, 60]), (1.4, [-100, 0, 60]), (2.0, Z)])
    a.rot("arm_right", [(0, Z), (0.3, [-100, 0, -60]), (1.4, [-100, 0, -60]), (2.0, Z)])
    A.append(a)

    L = 3.0
    a = Anim("death", L, False)
    a.rot("wing_left", [(0, Z), (0.6, [0, 0, 50]), (1.8, [0, 0, -40]), (3.0, [0, 0, -45])])
    a.rot("wing_right", [(0, Z), (0.6, [0, 0, -50]), (1.8, [0, 0, 40]), (3.0, [0, 0, 45])])
    a.rot("torso", [(0, Z), (0.6, [-25, 0, 0]), (1.8, [30, 0, 0]), (3.0, [35, 0, 0])])
    a.rot("head", [(0, Z), (0.6, [-35, 0, 0]), (1.8, [40, 0, 0]), (3.0, [45, 0, 0])])
    a.rot("arm_left", [(0, Z), (0.6, [-120, 0, 50]), (1.8, [20, 0, 20]), (3.0, [25, 0, 20])])
    a.rot("arm_right", [(0, Z), (0.6, [-120, 0, -50]), (1.8, [20, 0, -20]), (3.0, [25, 0, -20])])
    a.pos("root", [(0, Z), (0.6, [0, 2, 0]), (1.8, [0, -4, 0]), (3.0, [0, -10, 0])])
    a.scl("root", [(0, ONE), (1.8, ONE), (3.0, [0.2, 0.2, 0.2])])
    A.append(a)

    # Rasante (1.4 s): recua com as asas para trás (até 0.5 s), mergulha (0.5–0.75), passa rente com as garras
    # (0.75–1.0) e sobe de volta
    a = Anim("talon", 1.4)
    a.rot("torso", [(0, Z), (0.5, [-12, 0, 0]), (0.75, [45, 0, 0]), (1.0, [30, 0, 0]), (1.4, Z)])
    a.rot("head", [(0, Z), (0.5, [-10, 0, 0]), (0.75, [-35, 0, 0]), (1.0, [-25, 0, 0]), (1.4, Z)])
    a.rot("arm_left", [(0, Z), (0.5, [-60, 0, 40]), (0.75, [-130, 0, 10]), (0.85, [-20, 0, 30]), (1.0, [-20, 0, 30]), (1.4, Z)])
    a.rot("arm_right", [(0, Z), (0.5, [-60, 0, -40]), (0.75, [-130, 0, -10]), (0.85, [-20, 0, -30]), (1.0, [-20, 0, -30]), (1.4, Z)])
    a.rot("wing_left", [(0, Z), (0.5, [0, 40, 10]), (0.75, [0, 55, -5]), (1.0, [0, 45, 0]), (1.2, [0, 0, 50]), (1.4, Z)])
    a.rot("wing_right", [(0, Z), (0.5, [0, -40, -10]), (0.75, [0, -55, 5]), (1.0, [0, -45, 0]), (1.2, [0, 0, -50]), (1.4, Z)])
    a.rot("tail", [(0, Z), (0.5, [20, 0, 0]), (0.75, [55, 0, 0]), (1.0, [50, 0, 0]), (1.4, Z)])
    A.append(a)

    a = Anim("gale", 1.5)
    a.rot("wing_left", [(0, Z), (0.5, [0, -30, 60]), (0.7, [0, 50, -10]), (1.0, [0, 45, -5]), (1.5, Z)])
    a.rot("wing_right", [(0, Z), (0.5, [0, 30, -60]), (0.7, [0, -50, 10]), (1.0, [0, -45, 5]), (1.5, Z)])
    a.rot("torso", [(0, Z), (0.5, [-15, 0, 0]), (0.7, [20, 0, 0]), (1.0, [18, 0, 0]), (1.5, Z)])
    a.rot("arm_left", [(0, Z), (0.5, [-40, 0, 40]), (0.7, [-90, 0, 0]), (1.0, [-90, 0, 0]), (1.5, Z)])
    a.rot("arm_right", [(0, Z), (0.5, [-40, 0, -40]), (0.7, [-90, 0, 0]), (1.0, [-90, 0, 0]), (1.5, Z)])
    A.append(a)

    a = Anim("wind_blades", 1.5)
    a.rot("arm_right", [(0, Z), (0.4, [-150, 0, -30]), (0.5, [-40, 0, 20]), (0.9, [-60, 0, 0]), (1.0, [-150, 0, -30]),
                        (1.1, [-40, 0, 20]), (1.5, Z)])
    a.rot("arm_left", [(0, Z), (0.5, Z), (0.7, [-150, 0, 30]), (0.8, [-40, 0, -20]), (1.1, [-60, 0, 0]), (1.5, Z)])
    a.rot("torso", [(0, Z), (0.4, [0, -20, 0]), (0.5, [0, 20, 0]), (0.7, [0, 20, 0]), (0.8, [0, -20, 0]),
                    (1.0, [0, -20, 0]), (1.1, [0, 20, 0]), (1.5, Z)])
    A.append(a)

    a = Anim("tornado", 2.0)
    a.rot("tail", wave(2.0, lambda t: [10, 70 * math.sin(t * 12) * min(1, t * 2) * (1 - t / 2.0), 0], 24))
    a.rot("tail2", wave(2.0, lambda t: [10, 50 * math.sin(t * 12 - 0.8) * min(1, t * 2) * (1 - t / 2.0), 0], 24))
    a.rot("torso", wave(2.0, lambda t: [0, 30 * math.sin(t * 6) * (1 - t / 2.0), 0], 24))
    a.rot("arm_left", [(0, Z), (0.4, [-90, 0, 70]), (1.6, [-90, 0, 70]), (2.0, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-90, 0, -70]), (1.6, [-90, 0, -70]), (2.0, Z)])
    a.rot("wing_left", [(0, Z), (0.4, [0, 0, 45]), (1.6, [0, 0, 45]), (2.0, Z)])
    a.rot("wing_right", [(0, Z), (0.4, [0, 0, -45]), (1.6, [0, 0, -45]), (2.0, Z)])
    A.append(a)

    a = Anim("dive", 2.6)
    a.rot("wing_left", [(0, Z), (0.2, [0, 0, 60]), (0.4, [0, 0, -10]), (0.6, [0, 0, 60]), (0.8, [0, 70, -20]),
                        (1.7, [0, 70, -20]), (1.75, [0, 0, -30]), (2.2, [0, 0, -30]), (2.6, Z)])
    a.rot("wing_right", [(0, Z), (0.2, [0, 0, -60]), (0.4, [0, 0, 10]), (0.6, [0, 0, -60]), (0.8, [0, -70, 20]),
                         (1.7, [0, -70, 20]), (1.75, [0, 0, 30]), (2.2, [0, 0, 30]), (2.6, Z)])
    a.rot("torso", [(0, Z), (0.6, [-10, 0, 0]), (0.8, [70, 0, 0]), (1.7, [70, 0, 0]), (1.75, [10, 0, 0]), (2.2, [15, 0, 0]), (2.6, Z)])
    a.rot("arm_left", [(0, Z), (0.8, [-160, 0, 5]), (1.7, [-160, 0, 5]), (1.75, [-30, 0, 40]), (2.2, [-30, 0, 40]), (2.6, Z)])
    a.rot("arm_right", [(0, Z), (0.8, [-160, 0, -5]), (1.7, [-160, 0, -5]), (1.75, [-30, 0, -40]), (2.2, [-30, 0, -40]), (2.6, Z)])
    a.rot("head", [(0, Z), (0.8, [-50, 0, 0]), (1.7, [-50, 0, 0]), (1.75, [20, 0, 0]), (2.2, [20, 0, 0]), (2.6, Z)])
    A.append(a)

    a = Anim("vacuum", 2.8)
    tremble = [(0.4 + i * 0.1, [25 + (2 if i % 2 else -2), 0, 0]) for i in range(16)]
    a.rot("torso", [(0, Z)] + tremble + [(2.1, [-25, 0, 0]), (2.5, [-25, 0, 0]), (2.8, Z)])
    a.rot("wing_left", [(0, Z), (0.4, [0, 60, 0]), (2.0, [0, 65, 0]), (2.1, [0, -20, 70]), (2.5, [0, -20, 70]), (2.8, Z)])
    a.rot("wing_right", [(0, Z), (0.4, [0, -60, 0]), (2.0, [0, -65, 0]), (2.1, [0, 20, -70]), (2.5, [0, 20, -70]), (2.8, Z)])
    a.rot("wing_left_tip", [(0, Z), (0.4, [0, 40, 0]), (2.0, [0, 40, 0]), (2.1, Z), (2.8, Z)])
    a.rot("wing_right_tip", [(0, Z), (0.4, [0, -40, 0]), (2.0, [0, -40, 0]), (2.1, Z), (2.8, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-60, 0, -40]), (2.0, [-60, 0, -40]), (2.1, [-140, 0, 70]), (2.5, [-140, 0, 70]), (2.8, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-60, 0, 40]), (2.0, [-60, 0, 40]), (2.1, [-140, 0, -70]), (2.5, [-140, 0, -70]), (2.8, Z)])
    a.rot("head", [(0, Z), (0.4, [20, 0, 0]), (2.0, [20, 0, 0]), (2.1, [-30, 0, 0]), (2.5, [-30, 0, 0]), (2.8, Z)])
    A.append(a)
    A.extend(zephyr_anims_v07(tails))
    return A


def zephyr_anims_v07(tails):
    A = []
    # Apresentação (3.5 s): casulo de asas girando no ar -> abre as asas e ruge (1.6 s) -> batida de asas (2.6 s)
    L = 3.5
    a = Anim("intro", L)
    # Balanço do casulo (começa e termina em 0: sem giro brusco na transição para "idle")
    a.rot("root", wave(L, lambda t: [0, 40 * math.sin(2 * math.pi * t / 0.8) * max(0.0, 1 - t / 1.6), 0], 28))
    a.pos("root", [(0, [0, -4, 0]), (1.6, [0, 2, 0]), (2.4, [0, 3, 0]), (2.6, [0, 0, 0]), (L, Z)])
    a.rot("wing_left", [(0, [0, 70, -10]), (1.4, [0, 70, -10]), (1.6, [0, 0, 60]), (2.4, [0, 0, 65]), (2.6, [0, 0, -15]),
                        (2.9, [0, 0, 35]), (L, Z)])
    a.rot("wing_right", [(0, [0, -70, 10]), (1.4, [0, -70, 10]), (1.6, [0, 0, -60]), (2.4, [0, 0, -65]), (2.6, [0, 0, 15]),
                         (2.9, [0, 0, -35]), (L, Z)])
    a.rot("wing_left_tip", [(0, [0, 50, 0]), (1.4, [0, 50, 0]), (1.6, [0, 0, 25]), (2.6, [0, 0, -10]), (L, Z)])
    a.rot("wing_right_tip", [(0, [0, -50, 0]), (1.4, [0, -50, 0]), (1.6, [0, 0, -25]), (2.6, [0, 0, 10]), (L, Z)])
    a.rot("torso", [(0, [30, 0, 0]), (1.4, [30, 0, 0]), (1.6, [-20, 0, 0]), (2.4, [-22, 0, 0]), (2.6, [15, 0, 0]), (L, Z)])
    a.rot("head", [(0, [35, 0, 0]), (1.4, [30, 0, 0]), (1.6, [-30, 0, 0]), (2.4, [-30, 0, 0]), (2.6, [5, 0, 0]), (L, Z)])
    a.rot("arm_left", [(0, [-70, 30, -35]), (1.4, [-70, 30, -35]), (1.6, [-100, 0, 60]), (2.4, [-100, 0, 60]), (2.6, [-40, 0, 30]), (L, Z)])
    a.rot("arm_right", [(0, [-70, -30, 35]), (1.4, [-70, -30, 35]), (1.6, [-100, 0, -60]), (2.4, [-100, 0, -60]), (2.6, [-40, 0, -30]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [S(t, L, 2, -i * 0.8) * (10 + i * 4), S(t, L, 3, -i) * (12 + i * 4), 0], 28))
    A.append(a)

    # Troca de fase (2.5 s): se fecha nas asas tremendo e explode em ventania
    L = 2.5
    a = Anim("phase", L)
    a.rot("wing_left", [(0, Z), (0.4, [0, 65, 0]), (1.0, [0, 70, 0]), (1.3, [0, -20, 70]), (2.0, [0, -20, 70]), (L, Z)])
    a.rot("wing_right", [(0, Z), (0.4, [0, -65, 0]), (1.0, [0, -70, 0]), (1.3, [0, 20, -70]), (2.0, [0, 20, -70]), (L, Z)])
    a.rot("wing_left_tip", [(0, Z), (0.4, [0, 40, 0]), (1.0, [0, 40, 0]), (1.3, [0, 0, 25]), (2.0, [0, 0, 25]), (L, Z)])
    a.rot("wing_right_tip", [(0, Z), (0.4, [0, -40, 0]), (1.0, [0, -40, 0]), (1.3, [0, 0, -25]), (2.0, [0, 0, -25]), (L, Z)])
    tremble = [(0.4 + i * 0.1, [28 if i % 2 else 22, 0, 0]) for i in range(6)]
    a.rot("torso", [(0, Z), (0.4, [25, 0, 0])] + tremble + [(1.3, [-25, 0, 0]), (2.0, [-25, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (0.4, [25, 0, 0]), (1.0, [25, 0, 0]), (1.3, [-35, 0, 0]), (2.0, [-35, 0, 0]), (L, Z)])
    a.rot("arm_left", [(0, Z), (0.4, [-60, 0, -40]), (1.0, [-60, 0, -40]), (1.3, [-140, 0, 70]), (2.0, [-140, 0, 70]), (L, Z)])
    a.rot("arm_right", [(0, Z), (0.4, [-60, 0, 40]), (1.0, [-60, 0, 40]), (1.3, [-140, 0, -70]), (2.0, [-140, 0, -70]), (L, Z)])
    A.append(a)

    # Exposto (loop 1.6 s): caído depois do mergulho — curvado, asas arrastando no chão, ofegante
    L = 1.6
    a = Anim("exposed", L, True)
    a.rot("torso", wave(L, lambda t: [32 + S(t, L) * 4, 0, 0]))
    a.rot("head", wave(L, lambda t: [28 + S(t, L, 1, 0.6) * 5, S(t, L, 1, 1.2) * 6, 0]))
    a.rot("wing_left", wave(L, lambda t: [0, 10, -28 + S(t, L) * 3]))
    a.rot("wing_right", wave(L, lambda t: [0, -10, 28 - S(t, L) * 3]))
    a.rot("wing_left_tip", wave(L, lambda t: [0, 0, -22]))
    a.rot("wing_right_tip", wave(L, lambda t: [0, 0, 22]))
    a.rot("arm_left", wave(L, lambda t: [25, 0, 12 + S(t, L) * 2]))
    a.rot("arm_right", wave(L, lambda t: [25, 0, -12 - S(t, L) * 2]))
    a.pos("root", wave(L, lambda t: [0, -1.5 + S(t, L) * 0.4, 0]))
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [55 - i * 10, S(t, L, 1, -i) * 5, 0]))
    A.append(a)

    # Cortina de Ventos (5.5 s): ergue os braços (até 1.0 s = tick 20), abre-os e "rege" o vento girando
    L = 5.5
    a = Anim("wind_curtain", L)

    def conduct(sign):
        def f(t):
            if t <= 1.0:
                k = t / 1.0
                return [-150 * k, 0, 30 * k * sign]
            if t >= 5.0:
                k = (L - t) / 0.5
                return [-90 * k, 0, 80 * k * sign]
            return [-90 + S(t, 1.0) * 12, S(t, 1.0, 1, 1.0) * 15 * sign, (80 + S(t, 1.0, 1, 0.5) * 8) * sign]
        return f
    a.rot("arm_left", wave(L, conduct(1), 44))
    a.rot("arm_right", wave(L, conduct(-1), 44))
    a.rot("wing_left", [(0, Z), (1.0, [0, 0, 55]), (5.0, [0, 0, 55]), (L, Z)])
    a.rot("wing_right", [(0, Z), (1.0, [0, 0, -55]), (5.0, [0, 0, -55]), (L, Z)])
    a.rot("wing_left_tip", wave(L, lambda t: [0, 0, 20 * min(1, t) * min(1, (L - t) * 2) + S(t, 1.0) * 6], 44))
    a.rot("wing_right_tip", wave(L, lambda t: [0, 0, -20 * min(1, t) * min(1, (L - t) * 2) - S(t, 1.0) * 6], 44))
    a.rot("torso", [(0, Z), (1.0, [-15, 0, 0]), (5.0, [-12, 0, 0]), (L, Z)])
    a.rot("head", [(0, Z), (1.0, [-25, 0, 0]), (5.0, [-20, 0, 0]), (L, Z)])
    for i, t_ in enumerate(tails):
        a.rot(t_, wave(L, lambda t, i=i: [10, S(t, 0.8, 1, -i * 0.9) * (25 + i * 10) * min(1, t) * min(1, (L - t) * 2), 0], 44))
    A.append(a)
    return A


# =====================================================================================
#  Funis translúcidos: tornado e redemoinho
# =====================================================================================
def tornado_model():
    m = Model("zephyr_tornado")

    def streaks(fs, pal, rnd):
        for name in ("top", "bottom"):
            fs[name].fill(TRANSPARENT)
        for name in ("front", "back", "left", "right"):
            f = fs[name]
            f.fill(TRANSPARENT)
            for _ in range(max(2, f.w // 2)):
                x0 = rnd.randrange(f.w)
                c = (255, 255, 255, rnd.randint(70, 160)) if rnd.random() < 0.7 else (159, 240, 208, rnd.randint(90, 170))
                for yy in range(f.h):
                    f.set((x0 + yy) % f.w, yy, c)
                    if rnd.random() < 0.5:
                        f.set((x0 + yy + 1) % f.w, yy, (c[0], c[1], c[2], c[3] // 2))

    for i in range(7):
        s = 6 + 4 * i
        y = 6 * i
        name = "l%d" % i
        m.bone(name, [0, y, 0])
        m.cube(name, [-s / 2, y, -s / 2], [s, 6, s], P((0, 0, 0, 0), noise=0.0, grad=False, deco=streaks))
    return m


def tornado_anims():
    a = Anim("spin", 1.0, True)
    for i in range(7):
        a.rot("l%d" % i, spin_frames(1.0, 2 if i < 3 else 1))
        a.pos("l%d" % i, wave(1.0, lambda t, i=i: [S(t, 1.0, 1, i * 0.6) * i * 0.4, 0, S(t, 1.0, 1, i * 0.6 + 1.57) * i * 0.4]))
    return [a]


def whirlpool_model():
    m = Model("tide_whirlpool")

    def swirl(inner):
        def d(fs, pal, rnd):
            for name in ("front", "back", "left", "right"):
                fs[name].fill(TRANSPARENT)
            for name in ("top", "bottom"):
                f = fs[name]
                cx, cy = f.w / 2 - 0.5, f.h / 2 - 0.5
                R = f.w / 2
                for yy in range(f.h):
                    for xx in range(f.w):
                        dx, dy = xx - cx, yy - cy
                        r = math.hypot(dx, dy)
                        if r > R:
                            f.set(xx, yy, TRANSPARENT)
                            continue
                        th = math.atan2(dy, dx)
                        s = math.sin(3 * th + r * 0.45)
                        edge = 1 - r / R
                        if s > 0.65:
                            c = (235, 250, 255, int(110 + 120 * min(1, edge * 3)))
                        elif s > 0.1:
                            c = (120, 205, 245, int(90 + 90 * min(1, edge * 3)))
                        else:
                            c = (30, 95, 170, int(70 + 80 * min(1, edge * 3)))
                        if inner and r < R * 0.35:
                            c = (10, 40, 90, 200)
                        f.set(xx, yy, c)
        return d

    # Modelo com metade do tamanho real (o renderer escala 2x): raio 6 blocos = 192 px de diametro
    sizes = [96, 72, 48, 24]
    for i, s in enumerate(sizes):
        name = "r%d" % i
        m.bone(name, [0, -i, 0])
        m.cube(name, [-s / 2, -i, -s / 2], [s, 1, s], P((0, 0, 0, 0), noise=0.0, grad=False, deco=swirl(i == len(sizes) - 1)))
    return m


def whirlpool_anims():
    a = Anim("spin", 2.0, True)
    for i in range(4):
        a.rot("r%d" % i, spin_frames(2.0, i + 1))
    return [a]


def bubble_model():
    """Bolha da Prisão de Bolha: um cubo de 1 bloco (o renderer desenha 2.2x) com uma bolha redonda pintada em cada
    face — borda clara, miolo quase transparente e um brilho no alto; de qualquer ângulo parece uma esfera."""
    m = Model("tide_bubble")

    def sphere_faces(fs, pal, rnd):
        for name, f in fs.items():
            cx, cy = (f.w - 1) / 2.0, (f.h - 1) / 2.0
            R = f.w / 2.0
            for yy in range(f.h):
                for xx in range(f.w):
                    r = math.hypot(xx - cx, yy - cy) / R
                    if r > 1.0:
                        f.set(xx, yy, TRANSPARENT)
                        continue
                    rim = max(0.0, (r - 0.72) / 0.28)
                    a = int(55 + 170 * rim ** 1.5)
                    c = (int(150 + 80 * rim), int(225 + 25 * rim), 255, a)
                    # Faixas de "água" girando dentro da bolha
                    if math.sin((xx + yy) * 0.9 + r * 6) > 0.85 and r < 0.8:
                        c = (200, 245, 255, 110)
                    f.set(xx, yy, c)
            # Brilho no alto à esquerda
            for (hx, hy, ha) in ((0.30, 0.28, 235), (0.36, 0.24, 200), (0.26, 0.34, 190), (0.33, 0.33, 150)):
                f.set(int(hx * f.w), int(hy * f.h), (255, 255, 255, ha))

    m.bone("bubble", [0, 8, 0])
    m.cube("bubble", [-8, 0, -8], [16, 16, 16], P((0, 0, 0, 0), noise=0.0, grad=False, deco=sphere_faces))
    return m


def bubble_anims():
    a = Anim("wobble", 1.2, True)
    a.scl("bubble", [(0, ONE), (0.3, [1.05, 0.95, 1.05]), (0.6, ONE), (0.9, [0.96, 1.05, 0.96]), (1.2, ONE)])
    a.pos("bubble", wave(1.2, lambda t: [0, S(t, 1.2) * 0.4, 0]))
    return [a]


def curtain_texture():
    """Textura da Cortina de Ventos (64x32, repete na horizontal): listras de vento translúcidas, brancas e verde-água,
    que ondulam; o renderer a faz correr ao longo da parede."""
    W, H = 64, 32
    c = Canvas(W, H)
    rnd = random.Random(77)
    for yy in range(H):
        for xx in range(W):
            c.set(xx, yy, (220, 255, 240, 34))
    n = 28
    for k in range(n):
        # Distribuídas por toda a altura (uma por faixa, com um pouco de sorte)
        y0 = (k + rnd.random()) * H / n
        amp = rnd.uniform(0.6, 2.4)
        ph = rnd.uniform(0, math.pi * 2)
        length = rnd.randint(14, 40)
        x0 = rnd.randrange(W)
        mint = rnd.random() < 0.35
        base = (159, 240, 208) if mint else (255, 255, 255)
        alpha = rnd.randint(110, 210)
        for i in range(length):
            x = (x0 + i) % W
            y = int(y0 + amp * math.sin(2 * math.pi * x / W * 2 + ph)) % H
            fade = min(1.0, (i + 1) / 4.0, (length - i) / 4.0)
            a = int(alpha * fade)
            if a <= c.get(x, y)[3]:
                continue
            c.set(x, y, base + (a,))
            if a > 120:
                c.set(x, (y + 1) % H, base + (a // 3,))
    c.save(os.path.join(ASSETS, "textures/entity", "zephyr_curtain.png"))


# =====================================================================================
#  Itens e loot
# =====================================================================================
BOSSES = {"ignis": "fire", "tide_guardian": "water", "primordial_golem": "earth", "zephyr": "wind"}


def count_fn(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def looting(hi):
    return {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
            "count": {"type": "minecraft:uniform", "min": 0, "max": hi}}


def pool(name, functions=None):
    e = {"type": "minecraft:item", "name": name}
    if functions:
        e["functions"] = functions
    return {"rolls": 1, "bonus_rolls": 0, "entries": [e]}


def items_and_loot():
    for boss, el in BOSSES.items():
        write_json(os.path.join(ASSETS, "models/item", boss + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        write_json(os.path.join(DATA, "loot_table/entities", boss + ".json"), {
            "type": "minecraft:entity",
            "pools": [
                pool("isekai_mahou:guardian_core_" + el),
                pool("isekai_mahou:essence_" + el, [count_fn(4, 8), looting(2)]),
                pool("isekai_mahou:essence_arcane", [count_fn(2, 4), looting(1)]),
                pool("isekai_mahou:mana_crystal", [count_fn(3, 6), looting(2)]),
            ],
        })


def main():
    m = ignis_model()
    m.layout()
    m.save_geo(7, 6, 2.3)
    m.paint(IGNIS_RED, seed=11)
    m.save_texture("ignis")
    m.paint(IGNIS_BLUE, seed=11)
    m.save_texture("ignis_blue")
    save_anims("ignis", ignis_anims())

    m = tide_model()
    m.layout()
    m.save_geo(7, 5, 1.6)
    m.paint(TIDE, seed=23)
    m.save_texture()
    save_anims("tide_guardian", tide_anims())

    m = golem_model()
    m.layout()
    m.save_geo(8, 7, 3.0)
    m.paint(GOLEM, seed=37)
    m.save_texture()
    save_anims("primordial_golem", golem_anims())

    m = zephyr_model()
    m.layout()
    m.save_geo(7, 5, 1.6)
    m.paint(ZEPHYR, seed=41)
    m.save_texture()
    save_anims("zephyr", zephyr_anims())

    m = tornado_model()
    m.layout()
    m.save_geo(4, 4, 1.5)
    m.paint({}, seed=5)
    m.save_texture(glow=False)
    save_anims("zephyr_tornado", tornado_anims())

    m = whirlpool_model()
    m.layout()
    m.save_geo(7, 2, 0)
    m.paint({}, seed=9)
    m.save_texture(glow=False)
    save_anims("tide_whirlpool", whirlpool_anims())

    m = bubble_model()
    m.layout()
    m.save_geo(2, 2, 0.5)
    m.paint({}, seed=13)
    m.save_texture(glow=False)
    save_anims("tide_bubble", bubble_anims())

    curtain_texture()

    items_and_loot()
    print("bosses_a: assets gerados")


if __name__ == "__main__":
    main()
