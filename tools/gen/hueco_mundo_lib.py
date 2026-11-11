"""
Biblioteca de modelos GeckoLib do modulo hueco_mundo (copiada do gerador do bosses_g para nao depender dele):
modelos com box UV pintada por face (+ glowmask), decoracoes reutilizaveis e animacoes.
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
MERGE = os.path.join(ROOT, "src/main/merge/hueco_mundo")
NS = "isekai_mahou"
CLEAR = (0, 0, 0, 0)


def H(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Modelos com box UV + pintura por face
# =====================================================================================
class Face:
    """Retangulo de uma face do cubo na textura. (fx, fy) relativos ao canto superior esquerdo da face."""

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

    def row(self, fy, c, g=False):
        self.rect(0, fy, self.w - 1, fy, c, g)

    def col(self, fx, c, g=False):
        self.rect(fx, 0, fx, self.h - 1, c, g)

    def fill(self, c):
        self.rect(0, 0, self.w - 1, self.h - 1, c)

    def scatter(self, c, density, seed, g=False):
        rnd = random.Random(seed)
        for yy in range(self.h):
            for xx in range(self.w):
                if rnd.random() < density:
                    self.set(xx, yy, c, g)

    def tint(self, fn):
        for yy in range(self.h):
            for xx in range(self.w):
                self.set(xx, yy, fn(self.get(xx, yy), xx, yy))


class P:
    """Pintura de um cubo: cor base, cores por face, ruido e decoracao (deco(faces_dict))."""

    def __init__(self, base, faces=None, noise=0.06, deco=None, grad=True):
        self.base = H(base)
        self.faces = {k: H(v) for k, v in (faces or {}).items()}
        self.noise = noise
        self.deco = deco
        self.grad = grad


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

    def cube(self, bone, origin, size, paint, inflate=0.0, rotation=None, pivot=None, share=None):
        """share: outro cubo do mesmo tamanho cuja regiao de textura e reaproveitada."""
        c = {"bone": bone, "origin": list(origin), "size": [int(s) for s in size], "paint": paint,
             "inflate": inflate, "rotation": rotation, "pivot": pivot, "share": share}
        self.cubes.append(c)
        return c

    def _pack(self):
        items = []
        for i, c in enumerate(self.cubes):
            if c["share"] is not None:
                continue
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
            used = y + row_h
            if not ok:
                continue
            hh = 16
            while hh < used:
                hh *= 2
            if hh <= W:
                return W, hh, pos
        raise RuntimeError("modelo grande demais: " + self.ident)

    def layout(self):
        self.tw, self.th, pos = self._pack()
        for i, c in enumerate(self.cubes):
            if c["share"] is None:
                c["uv"] = pos[i]
        for c in self.cubes:
            if c["share"] is not None:
                c["uv"] = c["share"]["uv"]

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

    def paint(self, seed=7):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            if c["share"] is not None:
                continue
            p = c["paint"]
            fs = self.faces_of(c)
            for name, f in fs.items():
                base = p.faces.get(name, p.base)
                if name == "top":
                    base = lighten(base, 0.10)
                elif name == "bottom":
                    base = darken(base, 0.28)
                elif name == "back":
                    base = darken(base, 0.08)
                elif name in ("left", "right"):
                    base = darken(base, 0.05)
                f.fill(base)
                if p.grad and name in ("front", "back", "left", "right") and f.h > 2:
                    f.tint(lambda col, xx, yy, hh=f.h: darken(col, 0.16 * (yy / (hh - 1))) if yy > hh * 0.4
                           else lighten(col, 0.05 * (1 - yy / (hh * 0.4 + 1e-6))))
                if p.noise > 0:
                    rnd = random.Random(seed * 1000 + i * 17 + len(name))
                    f.tint(lambda col, xx, yy: (lighten(col, r) if (r := (rnd.random() - 0.5) * 2 * p.noise) > 0
                                                else darken(col, -r)))
            if p.deco:
                p.deco(fs)

    def geo_json(self, bounds=(5, 5)):
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
                    "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                    "visible_bounds_offset": [0, bounds[1] / 2.0, 0],
                },
                "bones": bones,
            }],
        }

    def save(self, bounds=(5, 5), seed=7, tex_name=None, geo=True):
        self.layout()
        self.paint(seed)
        if geo:
            write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json(bounds))
        name = tex_name or self.ident
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# ---------------- decoracoes reutilizaveis ----------------
def chain(*fns):
    def d(fs):
        for fn in fns:
            if fn:
                fn(fs)
    return d


SIDES = ("front", "back", "left", "right")


def band(rows, color, faces=SIDES, g=False):
    def d(fs):
        for n in faces:
            for r in rows:
                fs[n].row(r if r >= 0 else fs[n].h + r, H(color), g)
    return d


def hem(color, ragged=False, faces=SIDES, seed=3):
    """Barra (ultimas linhas) com borda rasgada opcional (pixels transparentes)."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            f.row(f.h - 2, H(color))
            f.row(f.h - 1, darken(H(color), 0.25))
            if ragged:
                for xx in range(f.w):
                    k = rnd.random()
                    if k < 0.35:
                        f.set(xx, f.h - 1, CLEAR)
                    if k < 0.12:
                        f.set(xx, f.h - 2, CLEAR)
    return d


def folds(color, step=3, faces=SIDES, start=0):
    def d(fs):
        for n in faces:
            f = fs[n]
            for xx in range(start, f.w, step):
                for yy in range(f.h):
                    if (yy + xx) % 5 != 0:
                        f.set(xx, yy, H(color))
    return d


def speckle(color, density, seed, faces=("front", "back", "left", "right", "top"), g=False):
    def d(fs):
        for n in faces:
            fs[n].scatter(H(color), density, seed + len(n), g)
    return d


def bark(dark, light, seed, faces=("front", "back", "left", "right")):
    """Casca de arvore: sulcos verticais escuros e cristas claras."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            for xx in range(f.w):
                r = rnd.random()
                if r < 0.33:
                    off = rnd.randint(0, 3)
                    for yy in range(f.h):
                        if (yy + off) % 7 != 0:
                            f.set(xx, yy, H(dark))
                elif r < 0.45:
                    for yy in range(f.h):
                        if rnd.random() < 0.6:
                            f.set(xx, yy, H(light))
    return d


def cracks(color, seed, n_cracks=3, faces=("front",), g=True):
    """Rachaduras brilhantes (glowmask) em zigue-zague."""
    def d(fs):
        rnd = random.Random(seed)
        for name in faces:
            f = fs[name]
            for _ in range(n_cracks):
                x = rnd.randint(1, max(1, f.w - 2))
                y = rnd.randint(0, max(0, f.h // 3))
                for _ in range(rnd.randint(f.h // 3, max(f.h // 3 + 1, f.h - 1))):
                    f.set(x, y, H(color), g)
                    y += 1
                    x += rnd.choice((-1, 0, 0, 1))
                    x = max(0, min(f.w - 1, x))
                    if y >= f.h:
                        break
    return d


def rim(color, faces=SIDES, top=True, bottom=True):
    def d(fs):
        for n in faces:
            f = fs[n]
            if top:
                f.row(0, H(color))
            if bottom:
                f.row(f.h - 1, H(color))
    return d


def solid(color, g=False, faces=("front", "back", "left", "right", "top", "bottom")):
    def d(fs):
        for n in faces:
            fs[n].rect(0, 0, fs[n].w - 1, fs[n].h - 1, H(color), g)
    return d


def glow_faces(color, faces=("front", "back", "left", "right", "top", "bottom"), core=None):
    """Cubo inteiro brilhante (gemas, orbes), com nucleo mais claro opcional."""
    def d(fs):
        for n in faces:
            f = fs[n]
            for yy in range(f.h):
                for xx in range(f.w):
                    edge = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                    c = darken(H(color), 0.18) if edge and f.w > 2 and f.h > 2 else H(color)
                    if core and not edge:
                        c = H(core)
                    f.set(xx, yy, c, True)
    return d


# =====================================================================================
#  Animacoes
# =====================================================================================
def fmt(t):
    s = ("%.4f" % t).rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def cyc(length, fn, steps=8):
    out = {}
    for i in range(steps + 1):
        ph = i / steps
        out[fmt(length * ph)] = [round(v, 3) + 0.0 for v in fn(ph)]
    return out


def keys(pairs):
    return {fmt(t): [float(x) for x in v] for t, v in sorted(pairs, key=lambda kv: kv[0])}


def S(p):
    return math.sin(2 * math.pi * p)


def C(p):
    return math.cos(2 * math.pi * p)


def anim(length, bones, loop=False):
    return {"loop": loop, "animation_length": length, "bones": bones}


def spin(length, axis=1, turns=1, steps=8):
    """Rotacao continua (graus) num eixo."""
    def fn(p):
        v = [0, 0, 0]
        v[axis] = 360 * turns * p
        return v
    return cyc(length, fn, steps)


def save_anims(ident, anims):
    out = {"format_version": "1.8.0", "animations": {}}
    for n, a in anims.items():
        out["animations"]["animation.%s.%s" % (ident, n)] = a
    write_json(os.path.join(ASSETS, "animations/entity", ident + ".animation.json"), out)




# =====================================================================================
#  Decoracoes extras
# =====================================================================================
def eyes(color, positions, faces=("front",), core=None):
    """Olhos 2x2 brilhantes (glowmask) nas posicoes (fx, fy) de cada face."""
    def d(fs):
        for n in faces:
            f = fs[n]
            for (x, y) in positions:
                f.rect(x, y, x + 1, y + 1, H(color), True)
                if core:
                    f.set(x, y, H(core), True)
    return d


def teeth(color, gum, face="front", at_bottom=True, step=2):
    """Fileira de dentes (triangulinhos) na borda de cima ou de baixo de uma face."""
    def d(fs):
        f = fs[face]
        for x in range(0, f.w, step):
            if at_bottom:
                f.set(x, f.h - 1, H(color))
                if f.h > 2:
                    f.set(x, f.h - 2, H(color))
                f.set(min(f.w - 1, x + 1), f.h - 1, H(gum))
            else:
                f.set(x, 0, H(color))
                if f.h > 2:
                    f.set(x, 1, H(color))
                f.set(min(f.w - 1, x + 1), 0, H(gum))
    return d


def veins(color, seed, n=5, faces=SIDES + ("top",)):
    def d(fs):
        rnd = random.Random(seed)
        for name in faces:
            f = fs[name]
            for _ in range(n):
                x, y = rnd.randint(0, f.w - 1), rnd.randint(0, f.h - 1)
                for _ in range(rnd.randint(3, 9)):
                    f.set(x, y, H(color))
                    x += rnd.choice((-1, 0, 1))
                    y += rnd.choice((-1, 0, 1, 1))
                    if not (0 <= x < f.w and 0 <= y < f.h):
                        break
    return d


def planks(dark, seed, faces=SIDES):
    """Tabuas horizontais (frestas escuras a cada 4 px, emendas desencontradas)."""
    def d(fs):
        rnd = random.Random(seed)
        for name in faces:
            f = fs[name]
            for y in range(3, f.h, 4):
                f.row(y, H(dark))
            for y0 in range(0, f.h, 4):
                x = rnd.randint(2, 9)
                while x < f.w:
                    for y in range(y0, min(f.h, y0 + 3)):
                        f.set(x, y, H(dark))
                    x += rnd.randint(8, 14)
    return d


def fur(dark, light, seed, faces=SIDES + ("top",), density=0.25):
    def d(fs):
        rnd = random.Random(seed)
        for name in faces:
            f = fs[name]
            for y in range(f.h):
                for x in range(f.w):
                    r = rnd.random()
                    if r < density * 0.5:
                        f.set(x, y, H(dark))
                    elif r < density:
                        f.set(x, y, H(light))
    return d
