"""
Gerador de assets do modulo bosses_g (Falsa Hidra, Rei Mimico, Manticora + covis):
modelos GeckoLib (.geo.json) com box UV pintada por face (+ glowmasks), animacoes, texturas (espinho, itens), modelos de
itens/ovos, loot tables (entidades e baus dos covis), estruturas (.nbt + worldgen com random_spread_multi), tags e
traducoes (src/main/lang/bosses_g).

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_g.py
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
MERGE = os.path.join(ROOT, "src/main/merge/bosses_g")
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

    def save(self, bounds=(5, 5), seed=7):
        self.layout()
        self.paint(seed)
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json(bounds))
        self.tex.save(os.path.join(ASSETS, "textures/entity", self.ident + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", self.ident + "_glowmask.png"))


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


# =====================================================================================
#  FALSA HIDRA (corpo)
# =====================================================================================
HY_FLESH, HY_FLESH_D, HY_VEIN, HY_GUM = "#D8CDBF", "#A3968A", "#8E5A6A", "#6A2840"
HY_TOOTH, HY_EYE, HY_EYE2, HY_THROAT = "#F2EAD8", "#F4E27A", "#B89CFF", "#9B6BFF"
DIRT, DIRT_D, ROOTC, MOSS = "#5E4432", "#3E2C20", "#6A4A30", "#55602E"


def build_false_hydra():
    m = Model("false_hydra")
    m.bone("root", (0, 0, 0))
    # ---- monte de terra (so enterrada)
    m.bone("mound", (0, 0, 0), "root")

    def soil(seed):
        return chain(speckle(DIRT_D, 0.25, seed), speckle(ROOTC, 0.08, seed + 1), speckle(MOSS, 0.05, seed + 2,
                                                                                         faces=("top",)))
    m.cube("mound", (-26, 0, -22), (52, 5, 44), P(DIRT, deco=soil(1), noise=0.1))
    m.cube("mound", (-19, 5, -16), (38, 4, 32), P(DIRT, deco=soil(3), noise=0.1))
    m.cube("mound", (-11, 9, -9), (22, 3, 18), P(DIRT, deco=chain(soil(5), veins(HY_VEIN, 6, 2, faces=("top",))), noise=0.1))
    # pele aparecendo entre os torroes, com olhos semiabertos
    m.cube("mound", (-6, 11, -6), (12, 2, 10), P(HY_FLESH_D, deco=chain(veins(HY_VEIN, 7, 3),
                                                                         eyes(HY_EYE2, [(2, 3), (8, 5)], faces=("top",)))))
    for k, (x, z, s) in enumerate(((-20, -14, 6), (16, -12, 5), (-16, 12, 5), (18, 10, 6), (2, -18, 4))):
        m.cube("mound", (x, 3, z), (s, 3, s), P(DIRT_D, deco=soil(10 + k)))
    for k, (x, z, ry) in enumerate(((-24, 0, 30), (22, -4, -20), (4, 18, 70))):
        m.cube("mound", (x, 1, z), (10, 2, 2), P(ROOTC, deco=speckle(DIRT_D, 0.3, 20 + k)), rotation=[0, ry, 8], pivot=[x + 5, 2, z + 1])

    # ---- carne (o corpo que emerge)
    m.bone("flesh", (0, 0, 0), "root")

    def body_deco(fs):
        folds(HY_FLESH_D, step=5)(fs)
        veins(HY_VEIN, 31, 7)(fs)
        for n in SIDES:
            f = fs[n]
            f.row(f.h - 1, H(DIRT_D))
            f.row(f.h - 2, H(DIRT))
            f.scatter(H(DIRT), 0.08, 33 + len(n))
        eyes(HY_EYE, [(4, 3), (40, 4), (12, 7), (34, 8)], faces=("front",), core="#FFFFFF")(fs)
        eyes(HY_EYE2, [(6, 4), (22, 6), (32, 3)], faces=("left", "right"))(fs)
    m.cube("flesh", (-24, 0, -20), (48, 13, 40), P(HY_FLESH, deco=body_deco, noise=0.05))

    def hump_deco(fs):
        veins(HY_VEIN, 41, 6)(fs)
        eyes(HY_EYE, [(5, 5), (14, 9), (26, 4), (20, 18)], faces=("top",), core="#FFFFFF")(fs)
        eyes(HY_EYE2, [(8, 3), (24, 5)], faces=("front", "back"))(fs)
    m.cube("flesh", (-17, 13, -14), (34, 10, 28), P(HY_FLESH, deco=hump_deco, noise=0.05))
    m.cube("flesh", (-10, 23, -8), (12, 4, 12), P(HY_FLESH_D, deco=chain(veins(HY_VEIN, 43, 4),
                                                                            eyes(HY_EYE2, [(4, 4), (8, 8)], faces=("top",)))))
    m.cube("flesh", (1, 23, -2), (8, 3, 10), P(HY_FLESH, deco=chain(veins(HY_VEIN, 44, 3), eyes(HY_EYE, [(3, 4)], faces=("top",)))))
    m.cube("flesh", (-6, 27, -4), (6, 2, 6), P(HY_FLESH, deco=veins(HY_VEIN, 45, 2)))
    # caroços e dobras para quebrar a forma de caixa
    for k, (o, sz) in enumerate((((-27, 1, -12), (4, 9, 20)), ((23, 1, -9), (4, 8, 17)), ((-14, 1, 19), (26, 8, 4)),
                                 ((-21, 10, -18), (8, 6, 6)), ((13, 10, -17), (9, 5, 7)), ((-20, 13, 6), (6, 6, 10)),
                                 ((14, 13, 4), (6, 5, 12)), ((-6, 0, -23), (12, 3, 3)))):
        m.cube("flesh", o, sz, P(HY_FLESH if k % 2 else HY_FLESH_D, deco=chain(veins(HY_VEIN, 46 + k, 2),
                                                                                 eyes(HY_EYE2, [(1, 2)], faces=("front", "top")))))
    # boca vertical na frente (labio de cima + mandibula) com dentes e garganta brilhando
    m.bone("maw", (0, 13, -20), "flesh")
    m.cube("maw", (-9, 7, -22), (18, 7, 2), P(HY_THROAT, deco=glow_faces(HY_THROAT, core="#E2D2FF")))
    m.cube("maw", (-11, 14, -24), (22, 6, 5), P(HY_FLESH, deco=chain(veins(HY_VEIN, 51, 3),
                                                                      teeth(HY_TOOTH, HY_GUM, "front", True, 2),
                                                                      lambda fs: fs["bottom"].fill(H(HY_GUM)))))
    m.bone("jaw", (0, 9, -20), "maw")
    m.cube("jaw", (-10, 3, -24), (20, 6, 5), P(HY_FLESH, deco=chain(veins(HY_VEIN, 53, 3),
                                                                     teeth(HY_TOOTH, HY_GUM, "front", False, 2),
                                                                     lambda fs: fs["top"].fill(H(HY_GUM)))))
    # papo que infla quando ela canta
    m.bone("throat", (0, 6, -20), "flesh")
    m.cube("throat", (-6, 1, -23), (12, 5, 4), P(HY_FLESH_D, deco=chain(veins(HY_THROAT, 55, 4, faces=SIDES),
                                                                        eyes(HY_THROAT, [(3, 2), (8, 2)]))))
    # pescocos-toco que mergulham na terra (as cabecas "de verdade" saem longe)
    stumps = (("stump_0", (-14, 18, 4), [0, 0, 115]), ("stump_1", (14, 18, 4), [0, 0, -115]),
              ("stump_2", (-8, 20, 12), [-110, 0, 30]), ("stump_3", (8, 20, 12), [-110, 0, -30]),
              ("stump_4", (0, 22, 13), [-120, 0, 0]))
    for k, (name, piv, rot) in enumerate(stumps):
        m.bone(name, piv, "flesh", rotation=rot)
        m.cube(name, (piv[0] - 3, piv[1], piv[2] - 3), (6, 20, 6),
               P(HY_FLESH, deco=chain(rim(HY_FLESH_D), veins(HY_VEIN, 60 + k, 3), band([4, 10, 16], HY_FLESH_D),
                                      lambda fs: [fs[n].row(fs[n].h - 1, H(DIRT)) for n in SIDES])))
    m.save(bounds=(5, 4), seed=71)

    def stump_sway(length, amp, speed=1):
        out = {}
        for k, (name, piv, rot) in enumerate(stumps):
            out[name] = {"rotation": cyc(length, lambda p, k=k, r=rot: [r[0] + amp * S(p * speed + k * 0.2), r[1],
                                                                        r[2] + amp * C(p * speed + k * 0.3)], steps=16)}
        return out

    a = {}
    mound_pulse = {"mound": {"scale": cyc(2.4, lambda p: [1 + 0.015 * S(p), 1 + 0.06 * max(0, S(p)), 1 + 0.015 * S(p)], steps=12)}}
    a["buried"] = anim(2.4, dict(mound_pulse, flesh={"position": keys([(0, [0, -30, 0]), (2.4, [0, -30, 0])])}), loop=True)
    a["sleep"] = anim(3.0, dict(mound_pulse, flesh={"position": keys([(0, [0, -30, 0]), (3.0, [0, -30, 0])])}), loop=True)
    ex = {"flesh": {"position": cyc(2.0, lambda p: [0, 0.4 * S(p), 0]),
                    "scale": cyc(2.0, lambda p: [1 + 0.02 * S(p), 1 + 0.03 * S(p), 1 + 0.02 * S(p)])},
          "jaw": {"rotation": cyc(2.0, lambda p: [8 + 6 * S(p), 0, 0])},
          "throat": {"scale": cyc(2.0, lambda p: [1, 1 + 0.05 * S(p * 2), 1 + 0.08 * S(p * 2)])}}
    ex.update(stump_sway(2.0, 6))
    a["exposed"] = anim(2.0, ex, loop=True)
    sing = {"flesh": {"position": cyc(1.0, lambda p: [0, 1.0 + 0.6 * S(p), 0])},
            "jaw": {"rotation": cyc(1.0, lambda p: [26 + 6 * S(p * 2), 0, 0])},
            "maw": {"rotation": cyc(1.0, lambda p: [-8 + 3 * S(p), 2 * S(p * 2), 0])},
            "throat": {"scale": cyc(1.0, lambda p: [1.15 + 0.1 * S(p * 2), 1.2 + 0.2 * abs(S(p * 2)), 1.6 + 0.3 * abs(S(p * 2))], steps=16)}}
    sing.update(stump_sway(1.0, 12, 2))
    a["singing"] = anim(1.0, sing, loop=True)
    a["intro"] = anim(3.0, {
        "flesh": {"position": keys([(0, [0, -30, 0]), (1.2, [0, -24, 0]), (2.0, [0, 2, 0]), (2.3, [0, -1, 0]), (3.0, [0, 0, 0])])},
        "mound": {"scale": cyc(3.0, lambda p: [1 + 0.05 * S(p * 9), 1 + 0.1 * abs(S(p * 9)), 1 + 0.05 * S(p * 9)], steps=36)},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (2.0, [10, 0, 0]), (2.3, [38, 0, 0]), (2.8, [38, 0, 0]), (3.0, [10, 0, 0])])},
        "throat": {"scale": keys([(0, [1, 1, 1]), (2.2, [1.2, 1.3, 1.8]), (3.0, [1, 1, 1])])},
    })
    a["emerge"] = anim(0.7, {"flesh": {"position": keys([(0, [0, -30, 0]), (0.45, [0, 3, 0]), (0.7, [0, 0, 0])])},
                             "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.45, [30, 0, 0]), (0.7, [10, 0, 0])])}})
    a["burrow"] = anim(0.7, {"flesh": {"position": keys([(0, [0, 0, 0]), (0.2, [0, 2, 0]), (0.7, [0, -30, 0])])}})
    a["burrow_move"] = anim(0.9, {"mound": {"scale": cyc(0.9, lambda p: [1 - 0.4 * S(p * 0.5), 1 - 0.7 * S(p * 0.5), 1 - 0.4 * S(p * 0.5)], steps=12)}})
    a["erupt"] = anim(0.8, {"flesh": {"position": keys([(0, [0, -30, 0]), (0.25, [0, 8, 0]), (0.5, [0, -1, 0]), (0.8, [0, 0, 0])])},
                            "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.25, [45, 0, 0]), (0.8, [12, 0, 0])])}})
    a["bite"] = anim(0.8, {"maw": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-14, 0, 0]), (0.6, [10, 0, 0]), (0.8, [0, 0, 0])])},
                           "jaw": {"rotation": keys([(0, [8, 0, 0]), (0.4, [50, 0, 0]), (0.6, [0, 0, 0]), (0.8, [8, 0, 0])])},
                           "flesh": {"position": keys([(0, [0, 0, 0]), (0.4, [0, 1, 2]), (0.6, [0, 0, -3]), (0.8, [0, 0, 0])])}})
    scream = {"jaw": {"rotation": keys([(0, [8, 0, 0]), (0.2, [55, 0, 0]), (1.0, [55, 0, 0]), (1.2, [8, 0, 0])])},
              "flesh": {"position": cyc(1.2, lambda p: [0.5 * S(p * 12), 1.5, 0], steps=24)}}
    scream.update(stump_sway(1.2, 18, 4))
    a["scream"] = anim(1.2, scream)
    a["sing"] = anim(0.6, {"throat": {"scale": keys([(0, [1, 1, 1]), (0.6, [1.2, 1.3, 1.7])])},
                           "jaw": {"rotation": keys([(0, [8, 0, 0]), (0.6, [28, 0, 0])])}})
    a["death"] = anim(2.5, {
        "flesh": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 3, 0]), (1.6, [0, -6, 0]), (2.5, [0, -14, 0])]),
                  "scale": keys([(0, [1, 1, 1]), (1.6, [1.05, 0.8, 1.05]), (2.5, [1.1, 0.6, 1.1])])},
        "jaw": {"rotation": keys([(0, [10, 0, 0]), (0.5, [55, 0, 0]), (2.5, [40, 0, 0])])},
        "throat": {"scale": keys([(0, [1, 1, 1]), (0.5, [1.3, 1.4, 2.0]), (2.5, [0.8, 0.6, 0.8])])},
    })
    save_anims("false_hydra", a)


# =====================================================================================
#  CABECA DA FALSA HIDRA
# =====================================================================================
def build_hydra_head():
    m = Model("false_hydra_head")
    m.bone("root", (0, 0, 0))
    m.bone("neck_root", (0, 0, 0), "root")
    m.cube("neck_root", (-6, -1, -6), (12, 3, 12), P(DIRT, deco=chain(speckle(DIRT_D, 0.3, 1), speckle(ROOTC, 0.1, 2))))
    m.cube("neck_root", (-8, -1, -2), (3, 2, 4), P(DIRT_D, deco=speckle(DIRT, 0.3, 3)))
    m.cube("neck_root", (5, -1, -3), (3, 2, 5), P(DIRT_D, deco=speckle(DIRT, 0.3, 4)))

    def neck_deco(seed):
        def d(fs):
            veins(HY_VEIN, seed, 4)(fs)
            for n in SIDES:
                f = fs[n]
                for y in range(2, f.h, 5):
                    f.row(y, H(HY_FLESH_D))
            # olhinhos ao longo do pescoco (brilham)
            eyes(HY_EYE2, [(1, 6)], faces=("left",))(fs)
            eyes(HY_EYE2, [(3, 12)], faces=("right",))(fs)
        return d
    m.bone("neck1", (0, 1, 0), "neck_root")
    m.cube("neck1", (-4, 1, -4), (8, 20, 8), P(HY_FLESH, deco=neck_deco(11)))
    m.bone("neck2", (0, 21, 0), "neck1")
    m.cube("neck2", (-3.5, 21, -3.5), (7, 18, 7), P(HY_FLESH, deco=neck_deco(12)))
    m.bone("neck3", (0, 39, 0), "neck2")
    m.cube("neck3", (-3, 39, -3), (6, 15, 6), P(HY_FLESH, deco=neck_deco(13)))
    m.bone("head", (0, 54, 0), "neck3")

    def skull(fs):
        veins(HY_VEIN, 21, 3)(fs)
        f = fs["front"]
        f.rect(0, f.h - 2, f.w - 1, f.h - 1, H(HY_GUM))
        teeth(HY_TOOTH, HY_GUM, "front", True, 2)(fs)
        for n in ("left", "right"):
            g = fs[n]
            g.row(g.h - 1, H(HY_GUM))
            for x in range(0, g.w, 2):
                g.set(x, g.h - 1, H(HY_TOOTH))
        eyes(HY_EYE2, [(2, 2), (8, 2), (5, 4)], faces=("top",), core="#FFFFFF")(fs)
        eyes(HY_EYE, [(9, 2)], faces=("left", "right"))(fs)
    m.cube("head", (-5, 53, -14), (10, 8, 16), P(HY_FLESH, deco=skull, noise=0.04))
    m.cube("head", (-4, 61, -10), (8, 3, 11), P(HY_FLESH_D, deco=chain(veins(HY_VEIN, 23, 2), eyes(HY_EYE2, [(2, 3), (5, 6)], faces=("top",)))))
    # babados/espinhos na nuca
    for k, (x, rz) in enumerate(((-5, 25), (3, -25), (-1, 0))):
        m.cube("head", (x, 60, 0), (2, 7, 2), P(HY_FLESH_D, faces={"top": HY_EYE2}, deco=lambda fs: fs["top"].fill(H(HY_EYE2))),
               rotation=[-35, 0, rz], pivot=[x + 1, 60, 1])
    # v1.3 — presas humanas demais saindo do lábio e um babado de pele com olhos
    for k, x in enumerate((-4, -1.5, 1, 3.5)):
        m.cube("head", (x, 51, -14.5), (1, 3, 1), P(HY_TOOTH, deco=solid(HY_TOOTH)))
    for sx in (-1, 1):
        m.cube("head", (sx * 5 - (1 if sx < 0 else 0), 55, -6), (1, 6, 7), P(HY_FLESH_D, deco=chain(veins(HY_VEIN, 27 + sx, 2),
                                                                                                    eyes(HY_EYE2, [(3, 2)], faces=("left", "right")))),
               rotation=[0, sx * 25, sx * 10], pivot=[sx * 5, 58, -3])
    m.bone("jaw", (0, 54, 0), "head")

    def jaw_deco(fs):
        fs["top"].fill(H(HY_GUM))
        teeth(HY_TOOTH, HY_GUM, "front", False, 2)(fs)
        for n in ("left", "right"):
            g = fs[n]
            for x in range(0, g.w, 2):
                g.set(x, 0, H(HY_TOOTH))
    m.cube("jaw", (-4.5, 50, -14), (9, 3, 15), P(HY_FLESH, deco=chain(jaw_deco, veins(HY_VEIN, 25, 2, faces=("bottom", "left", "right")))))
    m.cube("jaw", (-3.5, 51, -12), (7, 1, 10), P(HY_THROAT, deco=glow_faces(HY_THROAT, core="#E2D2FF")))
    m.save(bounds=(3, 6), seed=81)

    a = {}

    def sway(length, amp, speed=1.0, jaw=4.0):
        return {
            "neck1": {"rotation": cyc(length, lambda p: [amp * 0.4 * S(p * speed), 0, amp * 0.5 * C(p * speed)], steps=16)},
            "neck2": {"rotation": cyc(length, lambda p: [amp * 0.6 * S(p * speed + 0.15), 0, amp * 0.7 * C(p * speed + 0.2)], steps=16)},
            "neck3": {"rotation": cyc(length, lambda p: [amp * 0.8 * S(p * speed + 0.3), 0, amp * 0.9 * C(p * speed + 0.4)], steps=16)},
            "head": {"rotation": cyc(length, lambda p: [8 + amp * 0.5 * S(p * speed + 0.45), 0, 0], steps=16)},
            "jaw": {"rotation": cyc(length, lambda p: [jaw + jaw * S(p * speed * 2), 0, 0], steps=16)},
        }
    a["idle"] = anim(3.2, sway(3.2, 6), loop=True)
    a["sing"] = anim(1.2, dict(sway(1.2, 14, 1, 14), head={"rotation": cyc(1.2, lambda p: [-20 + 6 * S(p), 0, 0], steps=12)}), loop=True)
    a["sunk"] = anim(1.0, {"neck_root": {"position": keys([(0, [0, -70, 0]), (1.0, [0, -70, 0])])}}, loop=True)
    a["bite"] = anim(1.0, {
        "neck2": {"rotation": keys([(0, [0, 0, 0]), (0.45, [-20, 0, 0]), (0.7, [30, 0, 0]), (1.0, [0, 0, 0])])},
        "neck3": {"rotation": keys([(0, [0, 0, 0]), (0.45, [-25, 0, 0]), (0.7, [35, 0, 0]), (1.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [8, 0, 0]), (0.45, [-15, 0, 0]), (0.7, [40, 0, 0]), (1.0, [8, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.45, [55, 0, 0]), (0.7, [0, 0, 0]), (1.0, [4, 0, 0])])},
    })
    a["lunge"] = anim(1.2, {
        "neck1": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-15, 0, 0]), (0.9, [35, 0, 0]), (1.2, [0, 0, 0])])},
        "neck2": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-25, 0, 0]), (0.9, [30, 0, 0]), (1.2, [0, 0, 0])])},
        "neck3": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-20, 0, 0]), (0.9, [25, 0, 0]), (1.2, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [8, 0, 0]), (0.6, [-20, 0, 0]), (0.9, [20, 0, 0]), (1.2, [8, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.6, [60, 0, 0]), (0.9, [0, 0, 0]), (1.2, [4, 0, 0])])},
    })
    a["death"] = anim(1.2, {
        "neck1": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 0, 10]), (1.2, [0, 0, 85])])},
        "neck2": {"rotation": keys([(0, [0, 0, 0]), (1.2, [10, 0, 20])])},
        "head": {"rotation": keys([(0, [8, 0, 0]), (1.2, [30, 0, 10])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.3, [60, 0, 0]), (1.2, [40, 0, 0])])},
    })
    save_anims("false_hydra_head", a)


# =====================================================================================
#  REI MIMICO
# =====================================================================================
MK_WOOD, MK_WOOD_D, MK_GOLD, MK_GOLD_D = "#7A2E1E", "#4E1A10", "#F2C14A", "#B8862A"
MK_TONGUE, MK_TONGUE_D, MK_EYE, MK_GEM, MK_INNER = "#D8507A", "#A8305A", "#FFE36A", "#E0304A", "#2A0A10"


def gold_trim(fs, sides=SIDES):
    for n in sides:
        f = fs[n]
        f.row(0, H(MK_GOLD))
        f.row(f.h - 1, H(MK_GOLD_D))
        f.col(0, H(MK_GOLD))
        f.col(f.w - 1, H(MK_GOLD_D))


def build_mimic_king():
    m = Model("mimic_king")
    m.bone("root", (0, 0, 0))
    for name, x, z in (("leg_fl", -13, -9), ("leg_fr", 13, -9), ("leg_bl", -13, 9), ("leg_br", 13, 9)):
        m.bone(name, (x, 10, z), "root")
        m.cube(name, (x - 3, 2, z - 3), (6, 9, 6), P(MK_WOOD_D, deco=chain(fur("#2E0E08", MK_WOOD, 5 + x, density=0.2),
                                                                         band([2], MK_GOLD_D))))
        for k, dx in enumerate((-2, 0, 2)):
            m.cube(name, (x + dx - 0.5, 0, z - 5), (1, 2, 3), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    m.bone("body", (0, 10, 0), "root")

    def base_deco(fs):
        planks(MK_WOOD_D, 11)(fs)
        gold_trim(fs)
        for n in SIDES:
            f = fs[n]
            for x in (6, f.w - 7):
                f.col(x, H(MK_GOLD_D))
        # fechadura dourada na frente (vira "nariz")
        f = fs["front"]
        f.rect(f.w // 2 - 3, 0, f.w // 2 + 2, 6, H(MK_GOLD))
        f.rect(f.w // 2 - 1, 2, f.w // 2, 4, H("#2A1A08"))
        teeth(MK_TOOTH_C, MK_INNER, "front", False, 3)(fs)
    m.cube("body", (-18, 10, -14), (36, 16, 28), P(MK_WOOD, deco=base_deco, noise=0.05))
    # interior da boca (escuro) com o olho que espia
    m.cube("body", (-16, 25, -12), (32, 1, 24), P(MK_INNER, deco=lambda fs: fs["top"].scatter(H("#5A1020"), 0.2, 3)))
    m.bone("eye", (0, 26, -6), "body")
    m.cube("eye", (-4, 25.5, -9), (8, 4, 6), P("#F4F0E0", deco=chain(
        lambda fs: [fs[n].rect(2, 0, 5, 3, H(MK_EYE), True) for n in ("front", "top")],
        lambda fs: [fs[n].rect(3, 1, 4, 2, H("#1A0000"), False) for n in ("front", "top")])))
    # lingua (fica dentro da boca; a animacao a lanca para frente)
    m.bone("tongue1", (0, 25, -4), "body")
    m.cube("tongue1", (-4, 24.3, -12), (8, 2, 10), P(MK_TONGUE, deco=chain(speckle(MK_TONGUE_D, 0.2, 31),
                                                                         lambda fs: fs["top"].col(fs["top"].w // 2, H(MK_TONGUE_D)))))
    # os outros gomos ficam escondidos dentro do bau e deslizam para fora (telescopio) quando a lingua e lancada
    m.bone("tongue2", (0, 25, -12), "tongue1")
    m.cube("tongue2", (-3.5, 23.6, -12), (7, 2, 20), P(MK_TONGUE, deco=chain(speckle(MK_TONGUE_D, 0.2, 32),
                                                                           lambda fs: fs["top"].col(fs["top"].w // 2, H(MK_TONGUE_D)))))
    m.bone("tongue3", (0, 25, -12), "tongue2")
    m.cube("tongue3", (-3, 23.2, -12), (6, 2, 18), P(MK_TONGUE, deco=speckle(MK_TONGUE_D, 0.2, 33)))
    m.cube("tongue3", (-3, 23.2, -15), (2, 2, 3), P(MK_TONGUE_D))
    m.cube("tongue3", (1, 23.2, -15), (2, 2, 3), P(MK_TONGUE_D))
    # tampa = mandibula de cima (dobradica atras)
    m.bone("lid", (0, 26, 14), "body")

    def lid_deco(fs):
        planks(MK_WOOD_D, 13)(fs)
        gold_trim(fs)
        f = fs["top"]
        for x in (6, f.w - 7):
            f.col(x, H(MK_GOLD))
        teeth(MK_TOOTH_C, MK_INNER, "front", True, 3)(fs)
        fs["bottom"].fill(H(MK_INNER))
        fs["bottom"].scatter(H("#5A1020"), 0.2, 7)
    m.cube("lid", (-18.5, 26, -14.5), (37, 8, 29), P(MK_WOOD, deco=lid_deco, noise=0.05))
    m.cube("lid", (-16, 34, -12), (32, 4, 24), P(MK_WOOD, deco=chain(planks(MK_WOOD_D, 15), gold_trim)))
    # dentes grandes nos cantos
    for k, x in enumerate((-16, -9, 7, 14)):
        m.cube("lid", (x, 22, -15), (2, 4, 2), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    for k, x in enumerate((-13, 11)):
        m.cube("body", (x, 26, -15), (2, 3, 2), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    # coroa
    m.bone("crown", (0, 38, 0), "lid")
    m.cube("crown", (-8, 38, -6), (16, 4, 12), P(MK_GOLD, deco=chain(rim(MK_GOLD_D), speckle("#FFF0A0", 0.08, 41))))
    for k, (x, z) in enumerate(((-8, -6), (6, -6), (-8, 4), (6, 4), (-1, -6))):
        m.cube("crown", (x, 42, z), (2, 4, 2), P(MK_GOLD, deco=lambda fs: fs["top"].fill(H("#FFF0A0"))))
    m.bone("crown_gems", (0, 40, 0), "crown")
    for k, (x, z) in enumerate(((-4, -6.5), (2, -6.5), (-8.5, -1), (7.5, -1))):
        w, d = (2, 1) if z < -6 else (1, 2)
        m.cube("crown_gems", (x, 39, z), (w, 2, d), P(MK_GEM, deco=glow_faces(MK_GEM, core="#FF90A0")))
    # ---- v1.3: mais detalhe ----
    # fileira completa de presas na tampa e no baú (boca de tubarão), e ouro escorrendo dos cantos
    for k, x in enumerate((-13, -5, -1, 3, 11)):
        m.cube("lid", (x, 23, -14.8), (1, 3, 1), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    for k, x in enumerate((-16, -7, -3, 1, 5, 15)):
        m.cube("body", (x, 26, -14.6), (1, 2, 1), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    for k, (x, z) in enumerate(((-18.6, -10), (17.6, -6), (-18.6, 6), (17.6, 9))):
        m.cube("body", (x, 14 + k % 2 * 3, z), (1, 8, 2), P(MK_GOLD, deco=chain(speckle("#FFF0A0", 0.2, 120 + k), lambda fs: fs["bottom"].fill(H(MK_GOLD_D)))))
    # fechadura-olho na frente da tampa (brilha) e cantoneiras douradas com rebites
    m.cube("lid", (-2, 27, -15.2), (4, 5, 1), P(MK_GOLD, deco=chain(rim(MK_GOLD_D), lambda fs: fs["front"].rect(1, 1, 2, 3, H(MK_EYE), True))))
    for sx in (-1, 1):
        for zz in (-14.8, 13.8):
            m.cube("lid", (sx * 18 - (1 if sx > 0 else 0), 26, zz), (1, 8, 1), P(MK_GOLD, deco=speckle(MK_GOLD_D, 0.3, 130)))
    # moedas e joias grudadas na língua (o "tesouro falso")
    m.cube("tongue2", (-1, 25.4, -2), (2, 1, 2), P(MK_GOLD, deco=glow_faces(MK_GOLD, core="#FFF0A0")))
    m.cube("tongue3", (0, 25, -6), (1, 1, 1), P(MK_GEM, deco=glow_faces(MK_GEM)))
    # coroa: ponta central alta e pérolas
    m.cube("crown", (-1, 42, -1), (2, 6, 2), P(MK_GOLD, deco=lambda fs: fs["top"].fill(H("#FFF0A0"))))
    m.cube("crown_gems", (-1, 47.5, -1), (2, 2, 2), P(MK_GEM, deco=glow_faces(MK_GEM, core="#FF90A0")))
    m.save(bounds=(4, 4), seed=91)

    a = {}
    legs = ("leg_fl", "leg_fr", "leg_bl", "leg_br")
    # disfarcado de bau: pernas recolhidas, caixa no chao, tampa fechada, so a respiracao denuncia
    sleep = {"body": {"position": cyc(4.0, lambda p: [0, -10 + 0.25 * S(p), 0])},
             "tongue1": {"position": keys([(0, [0, -2, 6]), (4.0, [0, -2, 6])])},
             "lid": {"rotation": keys([(0, [0, 0, 0]), (4.0, [0, 0, 0])])}}
    for n in legs:
        sleep[n] = {"scale": keys([(0, [1, 0.05, 1]), (4.0, [1, 0.05, 1])])}
    a["sleep"] = anim(4.0, sleep, loop=True)
    idle = {"lid": {"rotation": cyc(2.4, lambda p: [-8 - 5 * S(p), 0, 0])},
            "body": {"position": cyc(2.4, lambda p: [0, 0.3 * S(p), 0])},
            "eye": {"rotation": cyc(4.8, lambda p: [0, 18 * S(p), 0])},
            "tongue1": {"position": keys([(0, [0, 0, 4]), (2.4, [0, 0, 4])]), "rotation": cyc(2.4, lambda p: [0, 6 * S(p), 0])},
            "crown": {"rotation": cyc(2.4, lambda p: [0, 0, 2 * S(p)])}}
    a["idle"] = anim(2.4, idle, loop=True)
    walk = {"body": {"rotation": cyc(0.8, lambda p: [0, 0, 6 * S(p)]), "position": cyc(0.8, lambda p: [0, 1.5 * abs(S(p)), 0])},
            "lid": {"rotation": cyc(0.8, lambda p: [-10 - 8 * abs(S(p)), 0, 0])},
            "tongue1": {"position": keys([(0, [0, 0, 4]), (0.8, [0, 0, 4])])}}
    for k, n in enumerate(legs):
        sgn = 1 if k in (0, 3) else -1
        walk[n] = {"rotation": cyc(0.8, lambda p, s=sgn: [30 * s * S(p), 0, 0])}
    a["walk"] = anim(0.8, walk, loop=True)
    a["chew"] = anim(0.6, {"lid": {"rotation": cyc(0.6, lambda p: [-4 - 6 * abs(S(p)), 0, 0])},
                           "body": {"scale": cyc(0.6, lambda p: [1 + 0.05 * abs(S(p)), 1 - 0.03 * abs(S(p)), 1 + 0.05 * abs(S(p))])},
                           "tongue1": {"position": keys([(0, [0, -2, 6]), (0.6, [0, -2, 6])])}}, loop=True)
    a["maw"] = anim(0.5, {"lid": {"rotation": cyc(0.5, lambda p: [-72 + 3 * S(p * 2), 0, 0])},
                          "body": {"position": cyc(0.5, lambda p: [0.4 * S(p * 4), 0, 0])},
                          "tongue1": {"rotation": cyc(0.5, lambda p: [-20 + 10 * S(p * 2), 0, 0]), "position": keys([(0, [0, 0, 2]), (0.5, [0, 0, 2])])},
                          "eye": {"scale": keys([(0, [1.3, 1.3, 1.3]), (0.5, [1.3, 1.3, 1.3])])}}, loop=True)
    a["intro"] = anim(3.0, {
        "lid": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.3, [-60, 0, 0]), (2.2, [-55, 0, 0]), (2.6, [-62, 0, 0]), (3.0, [-10, 0, 0])])},
        "leg_fl": {"scale": keys([(0, [1, 0.05, 1]), (1.0, [1, 0.05, 1]), (1.3, [1, 1.1, 1]), (1.5, [1, 1, 1])])},
        "leg_fr": {"scale": keys([(0, [1, 0.05, 1]), (1.0, [1, 0.05, 1]), (1.3, [1, 1.1, 1]), (1.5, [1, 1, 1])])},
        "leg_bl": {"scale": keys([(0, [1, 0.05, 1]), (1.0, [1, 0.05, 1]), (1.3, [1, 1.1, 1]), (1.5, [1, 1, 1])])},
        "leg_br": {"scale": keys([(0, [1, 0.05, 1]), (1.0, [1, 0.05, 1]), (1.3, [1, 1.1, 1]), (1.5, [1, 1, 1])])},
        "body": {"position": keys([(0, [0, -10, 0]), (1.0, [0, -10, 0]), (1.3, [0, 2, 0]), (3.0, [0, 0, 0])]),
                 "rotation": cyc(3.0, lambda p: [0, 0, (5 if 0.4 < p < 0.75 else 0) * S(p * 12)], steps=36)},
        "crown": {"rotation": keys([(0, [0, 0, 0]), (1.3, [0, 0, -15]), (1.7, [0, 0, 10]), (2.1, [0, 0, -4]), (3.0, [0, 0, 0])])},
        "tongue1": {"rotation": keys([(0, [0, 0, 0]), (1.6, [-30, 0, 0]), (2.4, [-10, 0, 0]), (3.0, [0, 0, 0])]),
                    "position": keys([(0, [0, 0, 4]), (1.6, [0, 2, -6]), (3.0, [0, 0, 4])])},
        "tongue2": {"rotation": keys([(0, [0, 0, 0]), (1.6, [20, 0, 0]), (3.0, [0, 0, 0])])},
    })
    a["death"] = anim(2.5, {
        "lid": {"rotation": keys([(0, [-10, 0, 0]), (0.4, [-75, 0, 0]), (1.4, [-80, 0, 0]), (2.5, [-95, 0, 0])])},
        "root": {"rotation": keys([(0, [0, 0, 0]), (0.8, [0, 0, -8]), (1.8, [0, 0, 80]), (2.5, [0, 0, 88])]),
                 "position": keys([(0, [0, 0, 0]), (1.8, [6, 0, 0]), (2.5, [8, 0, 0])])},
        "tongue1": {"rotation": keys([(0, [0, 0, 0]), (2.5, [40, 0, 0])]), "position": keys([(0, [0, 0, 4]), (1.0, [0, 0, -4]), (2.5, [0, 0, -6])])},
        "crown": {"rotation": keys([(0, [0, 0, 0]), (1.8, [0, 0, 30]), (2.5, [10, 0, 40])]),
                  "position": keys([(0, [0, 0, 0]), (1.8, [4, 2, 0]), (2.5, [10, -6, 0])])},
    })
    a["bite"] = anim(0.7, {"lid": {"rotation": keys([(0, [-10, 0, 0]), (0.3, [-55, 0, 0]), (0.5, [2, 0, 0]), (0.7, [-10, 0, 0])])},
                           "body": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 1, 2]), (0.5, [0, 0, -4]), (0.7, [0, 0, 0])])}})
    a["tongue"] = anim(1.4, {
        "lid": {"rotation": keys([(0, [-10, 0, 0]), (0.5, [-45, 0, 0]), (1.2, [-45, 0, 0]), (1.4, [-10, 0, 0])])},
        "tongue1": {"position": keys([(0, [0, 0, 4]), (0.6, [0, 1, 2]), (0.85, [0, 1, -10]), (1.15, [0, 1, -10]), (1.4, [0, 0, 4])])},
        "tongue2": {"position": keys([(0, [0, 0, 0]), (0.6, [0, 0, 0]), (0.85, [0, 0, -20]), (1.15, [0, 0, -20]), (1.4, [0, 0, 0])]),
                    "rotation": keys([(0, [0, 0, 0]), (0.85, [0, 0, 0]), (1.0, [0, 12, 0]), (1.15, [0, -12, 0]), (1.4, [0, 0, 0])])},
        "tongue3": {"position": keys([(0, [0, 0, 0]), (0.6, [0, 0, 0]), (0.85, [0, 0, -18]), (1.15, [0, 0, -18]), (1.4, [0, 0, 0])])},
        "body": {"position": keys([(0, [0, 0, 0]), (0.6, [0, 0, 2]), (0.85, [0, 0, -2]), (1.4, [0, 0, 0])])},
    })
    a["spit"] = anim(0.8, {"lid": {"rotation": keys([(0, [-4, 0, 0]), (0.15, [-65, 0, 0]), (0.5, [-50, 0, 0]), (0.8, [-10, 0, 0])])},
                           "body": {"position": keys([(0, [0, 0, 0]), (0.15, [0, 0, 4]), (0.8, [0, 0, 0])]),
                                    "scale": keys([(0, [1.08, 0.95, 1.08]), (0.15, [0.96, 1.05, 0.96]), (0.8, [1, 1, 1])])}})
    a["gulp"] = anim(0.6, {"lid": {"rotation": keys([(0, [-45, 0, 0]), (0.2, [2, 0, 0]), (0.6, [-4, 0, 0])])},
                           "body": {"scale": keys([(0, [1, 1, 1]), (0.25, [1.12, 0.92, 1.12]), (0.6, [1.05, 0.97, 1.05])])}})
    a["choke"] = anim(1.0, {"lid": {"rotation": cyc(1.0, lambda p: [-30 * abs(S(p * 3)), 0, 0], steps=24)},
                            "body": {"position": cyc(1.0, lambda p: [0.8 * S(p * 6), 0, 0], steps=24)}})
    a["coins"] = anim(1.2, {"lid": {"rotation": keys([(0, [-10, 0, 0]), (0.5, [-58, 0, 0]), (1.0, [-58, 0, 0]), (1.2, [-10, 0, 0])])},
                            "body": {"position": cyc(1.2, lambda p: [0, 0, (1.2 if 0.4 < p < 0.85 else 0) * S(p * 10)], steps=24),
                                     "rotation": keys([(0, [0, 0, 0]), (0.5, [-6, 0, 0]), (0.8, [6, 0, 0]), (1.2, [0, 0, 0])])},
                            "tongue1": {"position": keys([(0, [0, 0, 4]), (0.8, [0, 2, -2]), (1.2, [0, 0, 4])])}})
    a["awaken"] = anim(1.5, {"lid": {"rotation": keys([(0, [-10, 0, 0]), (0.4, [-40, 0, 0]), (1.2, [-40, 0, 0]), (1.5, [-10, 0, 0])])},
                             "body": {"position": keys([(0, [0, 0, 0]), (0.4, [0, 3, 0]), (1.2, [0, 3, 0]), (1.5, [0, 0, 0])])},
                             "crown": {"position": keys([(0, [0, 0, 0]), (0.4, [0, 3, 0]), (1.2, [0, 3, 0]), (1.5, [0, 0, 0])]),
                                       "rotation": cyc(1.5, lambda p: [0, 25 * S(p), 0], steps=12)},
                             "eye": {"scale": keys([(0, [1, 1, 1]), (0.4, [1.4, 1.4, 1.4]), (1.5, [1, 1, 1])])}})
    leap = {"body": {"position": keys([(0, [0, 0, 0]), (0.25, [0, -4, 0]), (0.45, [0, 3, 0]), (1.0, [0, 0, 0])])},
            "lid": {"rotation": keys([(0, [-10, 0, 0]), (0.45, [-50, 0, 0]), (0.9, [-50, 0, 0]), (1.0, [-10, 0, 0])])}}
    for n in legs:
        leap[n] = {"rotation": keys([(0, [0, 0, 0]), (0.25, [0, 0, 0]), (0.45, [40 if "f" in n[-2] else -40, 0, 0]), (1.0, [0, 0, 0])])}
    a["leap"] = anim(1.0, leap)
    a["roar"] = anim(1.6, {"lid": {"rotation": keys([(0, [-10, 0, 0]), (0.3, [-70, 0, 0]), (1.3, [-70, 0, 0]), (1.6, [-10, 0, 0])])},
                           "body": {"position": cyc(1.6, lambda p: [0.6 * S(p * 14), 0, 0], steps=28)},
                           "tongue1": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-35, 0, 0]), (1.3, [-35, 0, 0]), (1.6, [0, 0, 0])]),
                                       "position": keys([(0, [0, 0, 4]), (0.3, [0, 3, -4]), (1.3, [0, 3, -4]), (1.6, [0, 0, 4])])},
                           "tongue2": {"rotation": cyc(1.6, lambda p: [10 * S(p * 6), 15 * S(p * 4), 0], steps=24)}})
    save_anims("mimic_king", a)


MK_TOOTH_C = "#F4ECD2"


# =====================================================================================
#  MIMICO DO COFRE
# =====================================================================================
def build_vault_mimic():
    m = Model("vault_mimic")
    wood, wood_d = "#8A5A2E", "#5A3A1A"
    m.bone("root", (0, 0, 0))
    for name, x, z in (("leg_fl", -5, -5), ("leg_fr", 5, -5), ("leg_bl", -5, 5), ("leg_br", 5, 5)):
        m.bone(name, (x, 3, z), "root")
        m.cube(name, (x - 1.5, 0, z - 1.5), (3, 3, 3), P(wood_d, deco=speckle("#3A2410", 0.3, 3)))
    m.bone("body", (0, 3, 0), "root")

    def base(fs):
        planks(wood_d, 3)(fs)
        for n in SIDES:
            f = fs[n]
            f.col(0, H("#9A9AA2"))
            f.col(f.w - 1, H("#6A6A72"))
            f.row(0, H("#9A9AA2"))
        f = fs["front"]
        f.rect(f.w // 2 - 1, 0, f.w // 2, 2, H("#C8C8D0"))
        teeth(MK_TOOTH_C, MK_INNER, "front", False, 2)(fs)
        fs["top"].fill(H(MK_INNER))
    m.cube("body", (-7, 3, -7), (14, 8, 14), P(wood, deco=base))
    m.cube("body", (-2, 10, -6), (4, 1, 6), P(MK_TONGUE, deco=speckle(MK_TONGUE_D, 0.3, 5)))
    m.bone("lid", (0, 11, 7), "body")

    def lid(fs):
        planks(wood_d, 4)(fs)
        for n in SIDES:
            f = fs[n]
            f.col(0, H("#9A9AA2"))
            f.col(f.w - 1, H("#6A6A72"))
            f.row(0, H("#9A9AA2"))
        teeth(MK_TOOTH_C, MK_INNER, "front", True, 2)(fs)
        fs["bottom"].fill(H(MK_INNER))
        eyes(MK_EYE, [(3, 4), (10, 4)], faces=("bottom",))(fs)
    m.cube("lid", (-7.5, 11, -7.5), (15, 5, 15), P(wood, deco=lid))
    m.bone("tongue", (0, 10.5, -4), "body")
    m.cube("tongue", (-1.5, 10, -10), (3, 1, 6), P(MK_TONGUE, deco=speckle(MK_TONGUE_D, 0.3, 6)))
    # v1.3 — presas de verdade saltando da borda, garras nas patas, fechadura-olho e moedas grudadas
    for k, x in enumerate((-6, -3, 1, 4)):
        m.cube("lid", (x, 9.5, -7.4), (1, 2, 1), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    for k, x in enumerate((-5, -1, 3)):
        m.cube("body", (x, 10, -7.3), (1, 2, 1), P(MK_TOOTH_C, deco=solid(MK_TOOTH_C)))
    m.cube("lid", (-1, 12, -8), (2, 3, 1), P("#C8C8D0", deco=lambda fs: fs["front"].set(0, 1, H(MK_EYE), True) or fs["front"].set(1, 1, H(MK_EYE), True)))
    for name, x, z in (("leg_fl", -5, -5), ("leg_fr", 5, -5), ("leg_bl", -5, 5), ("leg_br", 5, 5)):
        m.cube(name, (x - 1.5, 0, z - 2.5), (1, 1, 1), P(MK_TOOTH_C))
        m.cube(name, (x + 0.5, 0, z - 2.5), (1, 1, 1), P(MK_TOOTH_C))
    m.cube("body", (-6, 10.4, 1), (2, 1, 2), P(MK_GOLD, deco=glow_faces(MK_GOLD, core="#FFF0A0")))
    m.cube("body", (3, 10.4, -1), (2, 1, 2), P(MK_GOLD, deco=glow_faces(MK_GOLD, core="#FFF0A0")))
    m.save(bounds=(2, 2), seed=95)
    a = {}
    a["idle"] = anim(2.0, {"lid": {"rotation": cyc(2.0, lambda p: [-6 - 6 * abs(S(p)), 0, 0])},
                           "tongue": {"rotation": cyc(2.0, lambda p: [10 * S(p), 15 * S(p * 2), 0])}}, loop=True)
    walk = {"body": {"position": cyc(0.5, lambda p: [0, 2.5 * abs(S(p)), 0])},
            "lid": {"rotation": cyc(0.5, lambda p: [-14 * abs(S(p)), 0, 0])},
            "tongue": {"rotation": cyc(0.5, lambda p: [15 * S(p), 0, 0])}}
    for k, n in enumerate(("leg_fl", "leg_fr", "leg_bl", "leg_br")):
        walk[n] = {"rotation": cyc(0.5, lambda p, s=(1 if k in (0, 3) else -1): [35 * s * S(p), 0, 0])}
    a["walk"] = anim(0.5, walk, loop=True)
    a["bite"] = anim(0.5, {"lid": {"rotation": keys([(0, [-6, 0, 0]), (0.2, [-60, 0, 0]), (0.35, [2, 0, 0]), (0.5, [-6, 0, 0])])},
                           "body": {"position": keys([(0, [0, 0, 0]), (0.2, [0, 1, 1]), (0.35, [0, 0, -2]), (0.5, [0, 0, 0])])}})
    a["awaken"] = anim(0.8, {"lid": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-70, 0, 0]), (0.6, [-20, 0, 0]), (0.8, [-8, 0, 0])])},
                             "body": {"position": keys([(0, [0, -3, 0]), (0.3, [0, 3, 0]), (0.8, [0, 0, 0])])},
                             "tongue": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.8, [0, 0, 0])])}})
    a["death"] = anim(1.0, {"lid": {"rotation": keys([(0, [-6, 0, 0]), (0.3, [-80, 0, 0]), (1.0, [-95, 0, 0])])},
                            "root": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 90])]),
                                     "position": keys([(0, [0, 0, 0]), (1.0, [3, 0, 0])])}})
    save_anims("vault_mimic", a)


# =====================================================================================
#  MANTICORA
# =====================================================================================
MT_FUR, MT_FUR_D, MT_FUR_L, MT_MANE, MT_MANE_D = "#C08A4A", "#8A5A2A", "#E0B878", "#8A2A1A", "#5A1A10"
MT_SKIN, MT_WING, MT_WING_D, MT_TAIL, MT_TAIL_D = "#D7A27A", "#5A3A3A", "#3A2424", "#6A4A2E", "#3E2A18"
MT_SPIKE, MT_EYE = "#9BE04A", "#FF4A2A"


def build_manticore():
    m = Model("manticore")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 18, 0), "root")

    def torso(fs):
        fur(MT_FUR_D, MT_FUR_L, 3)(fs)
        fs["bottom"].fill(H(MT_FUR_L))
        for n in ("left", "right"):
            f = fs[n]
            for x in range(2, f.w, 5):
                for y in range(1, f.h - 3):
                    if (x + y) % 3 == 0:
                        f.set(x, y, H(MT_FUR_D))
    m.cube("body", (-8, 12, -12), (16, 13, 28), P(MT_FUR, deco=torso, noise=0.05))
    m.cube("body", (-6, 10, 4), (12, 4, 12), P(MT_FUR_L, deco=fur(MT_FUR, "#F0D098", 4)))
    # juba
    m.cube("body", (-10, 13, -18), (20, 17, 9), P(MT_MANE, deco=chain(fur(MT_MANE_D, "#B04030", 5, density=0.4),
                                                                      hem(MT_MANE_D, ragged=True, seed=6))))
    m.bone("head", (0, 24, -18), "body")

    def face(fs):
        f = fs["front"]
        # olhos vermelhos, nariz, boca com presas
        f.rect(2, 3, 3, 4, H(MT_EYE), True)
        f.rect(8, 3, 9, 4, H(MT_EYE), True)
        f.set(2, 3, H("#FFD0A0"), True)
        f.set(8, 3, H("#FFD0A0"), True)
        f.rect(1, 2, 4, 2, H("#6A3A20"))
        f.rect(7, 2, 10, 2, H("#6A3A20"))
        f.rect(5, 5, 6, 6, H("#8A4A3A"))
        f.rect(3, 8, 8, 8, H("#3A1010"))
        f.set(3, 9, H("#F4ECD2"))
        f.set(8, 9, H("#F4ECD2"))
        fs["top"].scatter(H(MT_MANE), 0.3, 7)
    m.cube("head", (-6, 18, -27), (12, 11, 10), P(MT_SKIN, deco=face, noise=0.03))
    m.cube("head", (-8, 27, -24), (16, 5, 8), P(MT_MANE, deco=fur(MT_MANE_D, "#B04030", 8, density=0.4)))
    for side, x in ((-1, -9), (1, 6)):
        m.cube("head", (x, 18, -24), (3, 10, 7), P(MT_MANE, deco=fur(MT_MANE_D, "#B04030", 9 + x, density=0.4)))
        m.cube("head", (x + (1 if side < 0 else 0), 29, -21), (2, 6, 2), P("#E8DCC0", deco=lambda fs: fs["top"].fill(H("#FFFFFF"))),
               rotation=[-25, 0, 20 * -side], pivot=[x + 1, 29, -20])
    m.bone("jaw", (0, 20, -19), "head")
    m.cube("jaw", (-5, 16, -27), (10, 3, 9), P(MT_SKIN, deco=chain(lambda fs: fs["top"].fill(H("#5A1A1A")),
                                                                    teeth("#F4ECD2", "#5A1A1A", "front", False, 2))))
    # pernas
    for name, x, z in (("leg_fl", -5, -9), ("leg_fr", 5, -9), ("leg_bl", -5, 11), ("leg_br", 5, 11)):
        m.bone(name, (x, 14, z), "body")
        m.cube(name, (x - 3, 2, z - 3), (6, 13, 6), P(MT_FUR, deco=fur(MT_FUR_D, MT_FUR_L, 10 + x + z)))
        m.cube(name, (x - 3.5, 0, z - 4.5), (7, 3, 8), P(MT_FUR_D, deco=chain(
            lambda fs: [fs["front"].set(xx, fs["front"].h - 1, H("#F4ECD2")) for xx in (1, 3, 5)])))
    # asas de morcego
    for name, sx in (("wing_left", 1), ("wing_right", -1)):
        px = 7 * sx
        m.bone(name, (px, 25, -6), "body")
        x0 = px if sx > 0 else px - 22
        m.cube(name, (x0, 24, -7), (22, 2, 2), P(MT_WING_D, deco=speckle("#2A1414", 0.3, 20)))

        def memb(fs, sx=sx):
            for n, f in fs.items():
                f.fill(H(MT_WING))
                for xx in range(0, f.w, 5):
                    f.col(xx, H(MT_WING_D))
                if n in ("top", "bottom"):
                    for xx in range(f.w):
                        cut = int(3 + 2 * abs(math.sin(xx * 0.6)))
                        for yy in range(f.h - cut, f.h):
                            f.set(xx, yy, CLEAR)
        m.cube(name, (x0, 25, -6), (22, 1, 16), P(MT_WING, deco=memb))
        for k in range(3):
            fx = x0 + 6 + k * 6 if sx > 0 else x0 + 16 - k * 6
            m.cube(name, (fx, 24.5, -5), (1, 1, 12 - k * 3), P(MT_WING_D))
    # cauda de escorpiao (segmentada; a animacao a curva por cima das costas)
    segs = (("tail1", (0, 23, 15), (-2.5, 21, 15), (5, 5, 9)),
            ("tail2", (0, 23.5, 24), (-2, 21.5, 24), (4, 4, 8)),
            ("tail3", (0, 23.5, 32), (-2, 21.5, 32), (4, 4, 8)),
            ("tail4", (0, 23.5, 40), (-1.5, 22, 40), (3, 3, 7)))
    parent = "body"
    for k, (name, piv, org, size) in enumerate(segs):
        m.bone(name, piv, parent)
        m.cube(name, org, size, P(MT_TAIL, deco=chain(band([0, size[1] - 1], MT_TAIL_D), speckle(MT_TAIL_D, 0.2, 30 + k))))
        parent = name
    m.bone("stinger", (0, 23.5, 47), "tail4")

    def bulb(fs):
        speckle(MT_TAIL_D, 0.25, 40)(fs)
        for n in SIDES + ("top",):
            fs[n].set(fs[n].w // 2, fs[n].h // 2, H(MT_SPIKE), True)
    m.cube("stinger", (-3, 20.5, 47), (6, 6, 6), P(MT_TAIL, deco=bulb))
    m.cube("stinger", (-0.5, 23, 53), (1, 1, 5), P(MT_SPIKE, deco=glow_faces(MT_SPIKE)))
    for k, (dx, dy, rx, rz) in enumerate(((-3, 4, -30, 30), (2, 4, -30, -30), (-3, -1, 30, 30), (2, -1, 30, -30), (-0.5, 6, -60, 0))):
        m.cube("stinger", (dx, 22 + dy, 49), (1, 4, 1), P(MT_SPIKE, deco=glow_faces(MT_SPIKE, core="#E8FFB0")),
               rotation=[rx, 0, rz], pivot=[dx + 0.5, 22 + dy, 49.5])
    # ---- v1.3: mais detalhe ----
    # espinhos de reserva ao longo da cauda (os do "leque" saem daqui)
    for k, (bone, z) in enumerate((("tail1", 17), ("tail1", 21), ("tail2", 26), ("tail2", 29), ("tail3", 34), ("tail3", 37), ("tail4", 42))):
        y = 26 if bone == "tail1" else 25.5 if bone != "tail4" else 25
        for sx in (-1, 1):
            m.cube(bone, (sx * 1.5 - 0.5, y - 1, z), (1, 3, 1), P(MT_SPIKE, deco=glow_faces(MT_SPIKE, core="#E8FFB0") if k % 2 else None),
                   rotation=[-35, 0, sx * -25], pivot=[sx * 1.5, y - 1, z + 0.5])
    # chifres de carneiro curvados e presas de sabre
    for sx in (-1, 1):
        m.cube("head", (sx * 6 - 1, 25, -22), (2, 2, 6), P("#E8DCC0", deco=speckle("#B8A880", 0.3, 60 + sx)), rotation=[20, sx * 30, 0], pivot=[sx * 6, 26, -20])
        m.cube("head", (sx * 8 - 1, 23, -18), (2, 4, 2), P("#E8DCC0", deco=lambda fs: fs["bottom"].fill(H("#FFFFFF"))),
               rotation=[30, 0, sx * 15], pivot=[sx * 8, 25, -17])
        m.cube("jaw", (sx * 3 - 0.5, 13, -27), (1, 4, 1), P("#F4ECD2"), rotation=[-10, 0, 0], pivot=[sx * 3, 16, -26.5])
        # garras retráteis brilhando nas patas da frente
        leg = "leg_fl" if sx < 0 else "leg_fr"
        lx = -5 if sx < 0 else 5
        for j, dx in enumerate((-2, 0, 2)):
            m.cube(leg, (lx + dx - 0.5, 0, -14.5), (1, 1, 2), P("#F4ECD2"))
        # cotovelo da asa com esporão
        wing = "wing_left" if sx > 0 else "wing_right"
        wx = 28 if sx > 0 else -30
        m.cube(wing, (wx, 23.5, -8), (2, 3, 2), P("#E8DCC0"))
    # juba mais volumosa (tufos) e cicatriz
    for k, (x, y) in enumerate(((-11, 20), (9, 20), (-9, 28), (7, 28), (-2, 30))):
        m.cube("body", (x, y, -17), (4, 4, 6), P(MT_MANE, deco=fur(MT_MANE_D, "#B04030", 70 + k, density=0.45)))
    m.cube("body", (-1, 25, -6), (2, 2, 18), P(MT_FUR_D, deco=speckle(MT_SPIKE, 0.15, 80)))
    m.save(bounds=(4, 4), seed=99)

    a = {}
    legs = ("leg_fl", "leg_fr", "leg_bl", "leg_br")
    tail_rest = {"tail1": [60, 0, 0], "tail2": [45, 0, 0], "tail3": [45, 0, 0], "tail4": [35, 0, 0], "stinger": [20, 0, 0]}
    wing_fold = {"wing_left": [0, -80, 25], "wing_right": [0, 80, -25]}

    def tail(length, amp=4, speed=1, extra=None):
        out = {}
        for k, (n, r) in enumerate(tail_rest.items()):
            e = (extra or {}).get(n, [0, 0, 0])
            out[n] = {"rotation": cyc(length, lambda p, r=r, k=k, e=e: [r[0] + e[0] + amp * S(p * speed + k * 0.15),
                                                                      r[1] + e[1] + amp * 0.8 * C(p * speed + k * 0.2), r[2] + e[2]], steps=12)}
        return out

    def wings(length, flap=0.0, speed=1, fold=True):
        out = {}
        for n, r in wing_fold.items():
            sgn = 1 if n == "wing_left" else -1
            base = r if fold else [0, 0, 10 * sgn]
            out[n] = {"rotation": cyc(length, lambda p, b=base, s=sgn: [b[0], b[1], b[2] + s * flap * S(p * speed)], steps=12)}
        return out

    sleep = {"body": {"position": keys([(0, [0, -9, 0]), (4.0, [0, -9, 0])]),
                      "rotation": cyc(4.0, lambda p: [0, 0, 0.8 * S(p)])},
             "head": {"rotation": keys([(0, [18, 0, 0]), (4.0, [18, 0, 0])])}}
    for n in legs:
        sleep[n] = {"rotation": keys([(0, [-80 if n[-2] == "f" else 80, 0, 0]), (4.0, [-80 if n[-2] == "f" else 80, 0, 0])])}
    sleep.update(tail(4.0, 2, extra={"tail1": [-50, 30, 0], "tail2": [-30, 20, 0], "tail3": [-30, 0, 0]}))
    sleep.update(wings(4.0))
    a["sleep"] = anim(4.0, sleep, loop=True)
    idle = {"body": {"position": cyc(2.4, lambda p: [0, 0.4 * S(p), 0])},
            "head": {"rotation": cyc(4.8, lambda p: [2 * S(p), 10 * S(p), 0])},
            "jaw": {"rotation": cyc(2.4, lambda p: [3 + 3 * S(p), 0, 0])}}
    idle.update(tail(2.4, 4))
    idle.update(wings(2.4, 3))
    a["idle"] = anim(2.4, idle, loop=True)
    walk = {"body": {"position": cyc(0.9, lambda p: [0, 0.8 * abs(S(p)), 0]), "rotation": cyc(0.9, lambda p: [0, 0, 2 * S(p)])},
            "head": {"rotation": cyc(0.9, lambda p: [3 * S(p * 2), 0, 0])}}
    for k, n in enumerate(legs):
        sgn = 1 if k in (0, 3) else -1
        walk[n] = {"rotation": cyc(0.9, lambda p, s=sgn: [28 * s * S(p), 0, 0])}
    walk.update(tail(0.9, 6))
    walk.update(wings(0.9, 4))
    a["walk"] = anim(0.9, walk, loop=True)
    tired = {"body": {"position": cyc(0.8, lambda p: [0, -2 + 0.6 * S(p), 0])},
             "head": {"rotation": cyc(0.8, lambda p: [22 + 3 * S(p), 0, 0])},
             "jaw": {"rotation": cyc(0.8, lambda p: [14 + 6 * S(p), 0, 0])}}
    tired.update(tail(0.8, 2, extra={"tail1": [-30, 0, 0], "tail2": [-20, 0, 0]}))
    tired.update(wings(0.8, 2, fold=False))
    a["tired"] = anim(0.8, tired, loop=True)
    fly = {"body": {"position": cyc(0.6, lambda p: [0, 1.5 * S(p), 0]), "rotation": keys([(0, [-8, 0, 0]), (0.6, [-8, 0, 0])])}}
    for n in legs:
        fly[n] = {"rotation": keys([(0, [45 if n[-2] == "f" else -45, 0, 0]), (0.6, [45 if n[-2] == "f" else -45, 0, 0])])}
    fly.update(tail(0.6, 6, 1))
    fly.update(wings(0.6, 45, 1, fold=False))
    a["fly"] = anim(0.6, fly, loop=True)
    intro = {"body": {"rotation": keys([(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.4, [-40, 0, 0]), (2.4, [-40, 0, 0]), (3.0, [0, 0, 0])]),
                      "position": keys([(0, [0, -9, 0]), (0.8, [0, 0, 0]), (1.4, [0, 6, 0]), (2.4, [0, 6, 0]), (3.0, [0, 0, 0])])},
             "head": {"rotation": keys([(0, [18, 0, 0]), (1.4, [-25, 0, 0]), (2.4, [-25, 0, 0]), (3.0, [0, 0, 0])])},
             "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.6, [40, 0, 0]), (2.4, [40, 0, 0]), (3.0, [4, 0, 0])])},
             "leg_fl": {"rotation": keys([(0, [-80, 0, 0]), (1.4, [-70, 0, 20]), (2.0, [-50, 0, 0]), (2.4, [-70, 0, 20]), (3.0, [0, 0, 0])])},
             "leg_fr": {"rotation": keys([(0, [-80, 0, 0]), (1.4, [-50, 0, -20]), (2.0, [-70, 0, 0]), (2.4, [-50, 0, -20]), (3.0, [0, 0, 0])])},
             "wing_left": {"rotation": keys([(0, [0, -80, 25]), (1.0, [0, -80, 25]), (1.6, [0, 0, 40]), (2.4, [0, 0, 30]), (3.0, [0, -80, 25])])},
             "wing_right": {"rotation": keys([(0, [0, 80, -25]), (1.0, [0, 80, -25]), (1.6, [0, 0, -40]), (2.4, [0, 0, -30]), (3.0, [0, 80, -25])])}}
    intro.update(tail(3.0, 10, 3))
    a["intro"] = anim(3.0, intro)
    death = {"root": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, 0, -6]), (1.6, [0, 0, 80]), (2.5, [0, 0, 88])]),
                      "position": keys([(0, [0, 0, 0]), (1.6, [6, -2, 0]), (2.5, [8, -4, 0])])},
             "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, 0]), (2.5, [20, 0, 10])])},
             "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.6, [45, 0, 0]), (2.5, [30, 0, 0])])},
             "wing_left": {"rotation": keys([(0, [0, -80, 25]), (1.0, [0, -20, 40]), (2.5, [0, -10, 70])])},
             "wing_right": {"rotation": keys([(0, [0, 80, -25]), (2.5, [0, 60, -10])])},
             "tail1": {"rotation": keys([(0, [60, 0, 0]), (2.5, [5, 30, 0])])},
             "tail2": {"rotation": keys([(0, [45, 0, 0]), (2.5, [5, 20, 0])])}}
    a["death"] = anim(2.5, death)
    a["swipe"] = anim(0.7, {"leg_fr": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-110, 0, -25]), (0.5, [-20, 0, 15]), (0.7, [0, 0, 0])])},
                            "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 8, 0]), (0.5, [6, -8, 0]), (0.7, [0, 0, 0])])},
                            "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.5, [10, 0, 0]), (0.7, [0, 0, 0])])},
                            "jaw": {"rotation": keys([(0, [3, 0, 0]), (0.3, [30, 0, 0]), (0.7, [3, 0, 0])])}})
    sting = tail(1.0, 0)
    for n, r in tail_rest.items():
        sting[n] = {"rotation": keys([(0, r), (0.55, [r[0] + 25, 0, 0]), (0.75, [r[0] - 35 if n != "tail1" else r[0] + 40, 0, 0]),
                                      (1.0, r)])}
    sting["body"] = {"rotation": keys([(0, [0, 0, 0]), (0.55, [-6, 0, 0]), (0.75, [8, 0, 0]), (1.0, [0, 0, 0])])}
    a["sting"] = anim(1.0, sting)
    fan = {}
    for n, r in tail_rest.items():
        fan[n] = {"rotation": keys([(0, r), (0.6, [r[0] + 20, 0, 0]), (0.95, [r[0] - 25, 0, 0]), (1.1, [r[0] - 25, 0, 0]), (1.4, r)])}
    fan["stinger"]["scale"] = keys([(0, [1, 1, 1]), (0.9, [1.4, 1.4, 1.4]), (1.0, [0.9, 0.9, 0.9]), (1.4, [1, 1, 1])])
    fan["body"] = {"rotation": keys([(0, [0, 0, 0]), (0.6, [-4, 0, 0]), (1.0, [5, 0, 0]), (1.4, [0, 0, 0])])}
    a["tail_fan"] = anim(1.4, fan)
    pounce = {"body": {"position": keys([(0, [0, 0, 0]), (0.6, [0, -4, 3]), (0.8, [0, 3, -4]), (1.2, [0, 0, 0])]),
                       "rotation": keys([(0, [0, 0, 0]), (0.6, [6, 0, 0]), (0.8, [-14, 0, 0]), (1.2, [0, 0, 0])])},
              "jaw": {"rotation": keys([(0, [3, 0, 0]), (0.8, [40, 0, 0]), (1.2, [3, 0, 0])])}}
    for n in legs:
        front = n[-2] == "f"
        pounce[n] = {"rotation": keys([(0, [0, 0, 0]), (0.6, [20 if front else -30, 0, 0]), (0.8, [-70 if front else 50, 0, 0]),
                                       (1.2, [0, 0, 0])])}
    a["pounce"] = anim(1.2, pounce)
    roar = {"head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-30, 0, 0]), (1.2, [-30, 0, 0]), (1.5, [0, 0, 0])])},
            "jaw": {"rotation": keys([(0, [3, 0, 0]), (0.4, [50, 0, 0]), (1.2, [50, 0, 0]), (1.5, [3, 0, 0])])},
            "body": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-12, 0, 0]), (1.2, [-12, 0, 0]), (1.5, [0, 0, 0])]),
                     "position": cyc(1.5, lambda p: [0.5 * S(p * 14), 0, 0], steps=28)},
            "wing_left": {"rotation": keys([(0, [0, -80, 25]), (0.4, [0, 0, 35]), (1.2, [0, 0, 35]), (1.5, [0, -80, 25])])},
            "wing_right": {"rotation": keys([(0, [0, 80, -25]), (0.4, [0, 0, -35]), (1.2, [0, 0, -35]), (1.5, [0, 80, -25])])}}
    a["roar"] = anim(1.5, roar)
    take = {"body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [10, 0, 0]), (0.8, [-15, 0, 0])])},
            "wing_left": {"rotation": keys([(0, [0, -80, 25]), (0.3, [0, 0, 60]), (0.55, [0, 0, -30]), (0.8, [0, 0, 40])])},
            "wing_right": {"rotation": keys([(0, [0, 80, -25]), (0.3, [0, 0, -60]), (0.55, [0, 0, 30]), (0.8, [0, 0, -40])])}}
    for n in legs:
        take[n] = {"rotation": keys([(0, [0, 0, 0]), (0.3, [25 if n[-2] == "f" else -25, 0, 0]), (0.8, [45 if n[-2] == "f" else -45, 0, 0])])}
    a["takeoff"] = anim(0.8, take)
    dive = {"body": {"rotation": keys([(0, [-8, 0, 0]), (0.4, [35, 0, 0]), (1.3, [35, 0, 0])])},
            "head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.3, [-20, 0, 0])])},
            "jaw": {"rotation": keys([(0, [3, 0, 0]), (0.4, [35, 0, 0]), (1.3, [35, 0, 0])])},
            "wing_left": {"rotation": keys([(0, [0, 0, 20]), (0.4, [0, -50, 10]), (1.3, [0, -50, 10])])},
            "wing_right": {"rotation": keys([(0, [0, 0, -20]), (0.4, [0, 50, -10]), (1.3, [0, 50, -10])])}}
    a["dive"] = anim(1.3, dive)
    rain = {}
    for n, r in tail_rest.items():
        rain[n] = {"rotation": keys([(0, r), (0.25, [r[0] - 20, 0, 0]), (0.5, r)])}
    rain["stinger"]["scale"] = keys([(0, [1, 1, 1]), (0.2, [1.3, 1.3, 1.3]), (0.5, [1, 1, 1])])
    a["rain"] = anim(0.5, rain)
    save_anims("manticore", a)


# =====================================================================================
#  TEXTURAS: espinho (formato de flecha do vanilla) e itens
# =====================================================================================
def spike_texture():
    c = Canvas(32, 32)
    bone, bone_d, tip, tip_g = H("#E8DCC0"), H("#A8987A"), H("#9BE04A"), H("#D8FF9A")
    # vista lateral (y 0..4): ponta verde a esquerda, haste de osso, farpas
    for x in range(0, 16):
        if x <= 3:
            c.set(x, 2, tip_g if x == 0 else tip)
            if x >= 2:
                c.set(x, 1, tip)
                c.set(x, 3, tip)
        else:
            c.set(x, 2, bone if x % 3 else bone_d)
    for x in (6, 9, 12):
        c.set(x, 1, bone_d)
        c.set(x + 1, 0, bone_d)
        c.set(x, 3, bone_d)
        c.set(x + 1, 4, bone_d)
    for x in (13, 14, 15):
        c.set(x, 1, H("#6A4A2E"))
        c.set(x, 3, H("#6A4A2E"))
    # corte de tras (cruz 5x5 em 0..4, 5..9)
    for i in range(5):
        c.set(2, 5 + i, bone_d)
        c.set(i, 7, bone_d)
    c.set(2, 7, bone)
    c.save(os.path.join(ASSETS, "textures/entity/manticore_spike.png"))


def item_textures():
    tex = os.path.join(ASSETS, "textures/item")
    # Coracao da Falsa Hidra: coracao palido com veias roxas e um olho no meio
    c = Canvas(16, 16)
    rows = ["................",
            "...xxx....xxx...",
            "..xffffx.xffffx.",
            ".xffvfffxfffvffx",
            ".xfvffffffffvffx",
            ".xffffwwwwffffvx",
            ".xfffwyyyywfffx.",
            "..xffwyppywffx..",
            "..xfvfwyywfvfx..",
            "...xffwwwwffx...",
            "....xfvfffvx....",
            ".....xffffx.....",
            "......xffx......",
            ".......xx.......",
            "................",
            "................"]
    c.from_rows(rows, {"x": H("#4A2E5A"), "f": H("#D8CDBF"), "v": H("#8E5A6A"), "w": H("#F2EAD8"),
                       "y": H("#B89CFF"), "p": H("#2A1040")})
    c.noise(0.06, 3)
    c.save(os.path.join(tex, "false_hydra_heart.png"))
    # Moeda Gananciosa: moeda de ouro com uma bocarra dentada
    c = Canvas(16, 16)
    c.circle(8, 8, 6.6, H("#B8862A"))
    c.circle(8, 8, 5.8, H("#F2C14A"))
    c.shade_sphere(8, 8, 6.5, 0.35, 0.3)
    rows = ["......",
            "wwwwww",
            "rrrrrr",
            "rmmmmr",
            "rrrrrr",
            "wwwwww"]
    c.from_rows(["w.w.w.", "rrrrrr", "rkkkkr", "rrrrrr", ".w.w.w"], {"w": H("#FFF6D0"), "r": H("#6A1A10"), "k": H("#D8507A")}, 5, 6)
    for x, y in ((6, 4), (9, 4)):
        c.set(x, y, H("#7A2E1E"))
    c.outline(H("#5A3A10"))
    c.save(os.path.join(tex, "greedy_coin.png"))
    # Ferrao da Manticora: lamina curva de ferrao (verde na ponta) com cabo de couro (na diagonal, como espada)
    c = Canvas(16, 16)
    blade = [(13, 1), (12, 2), (13, 2), (11, 3), (12, 3), (10, 4), (11, 4), (9, 5), (10, 5), (8, 6), (9, 6), (7, 7), (8, 7),
             (6, 8), (7, 8)]
    for (x, y) in blade:
        c.set(x, y, H("#E8DCC0"))
    for (x, y) in ((14, 0), (13, 1), (14, 1), (12, 2)):
        c.set(x, y, H("#9BE04A"))
    c.set(15, 0, H("#D8FF9A"))
    for (x, y) in ((10, 3), (8, 5), (6, 7)):
        c.set(x, y, H("#A8987A"))
    for (x, y) in ((4, 8), (5, 8), (6, 9), (7, 10), (5, 9), (6, 10)):
        c.set(x, y, H("#6A4A2E"))
    for (x, y) in ((4, 10), (3, 11), (2, 12), (5, 11), (4, 12), (3, 13)):
        c.set(x, y, H("#5A1A10"))
    c.set(1, 14, H("#C0862E"))
    c.set(2, 13, H("#C0862E"))
    c.outline(H("#2A1A10"))
    c.save(os.path.join(tex, "manticore_stinger.png"))


def item_models():
    models = os.path.join(ASSETS, "models/item")
    write_json(os.path.join(models, "false_hydra_heart.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/false_hydra_heart"}})
    write_json(os.path.join(models, "greedy_coin.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/greedy_coin"}})
    write_json(os.path.join(models, "manticore_stinger.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/manticore_stinger"}})
    for b in ("false_hydra", "false_hydra_head", "mimic_king", "vault_mimic", "manticore"):
        write_json(os.path.join(models, b + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  LOOT TABLES
# =====================================================================================
def item_entry(item, lo=1, hi=1, weight=1, fns=None):
    e = {"type": "minecraft:item", "name": item if ":" in item else "minecraft:" + item, "weight": weight}
    f = []
    if (lo, hi) != (1, 1):
        f.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fns:
        f += fns
    if f:
        e["functions"] = f
    return e


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def lpool(entries, rolls=1, hi=None):
    r = rolls if hi is None else {"type": "minecraft:uniform", "min": rolls, "max": hi}
    return {"rolls": r, "bonus_rolls": 0, "entries": entries}


def write_loot():
    ent = os.path.join(DATA, "loot_table/entities")
    write_json(os.path.join(ent, "false_hydra.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("bone", 4, 9)]),
        lpool([item_entry("amethyst_shard", 3, 7)]),
        lpool([item_entry("echo_shard", 1, 2, 1), item_entry("ender_pearl", 1, 3, 3)]),
        lpool([item_entry("rooted_dirt", 2, 5), item_entry("hanging_roots", 2, 5)]),
    ], "random_sequence": NS + ":entities/false_hydra"})
    write_json(os.path.join(ent, "false_hydra_head.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("bone", 0, 2), item_entry("rotten_flesh", 1, 2)]),
    ], "random_sequence": NS + ":entities/false_hydra_head"})
    write_json(os.path.join(ent, "mimic_king.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("gold_ingot", 6, 12)]),
        lpool([item_entry("emerald", 4, 9)]),
        lpool([item_entry("diamond", 1, 3)]),
        lpool([item_entry("golden_apple", 1, 2, 4), item_entry("enchanted_golden_apple", 1, 1, 1)]),
        lpool([item_entry("chest", 1, 2)]),
    ], "random_sequence": NS + ":entities/mimic_king"})
    write_json(os.path.join(ent, "vault_mimic.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("oak_planks", 1, 3), item_entry("gold_nugget", 1, 4)]),
    ], "random_sequence": NS + ":entities/vault_mimic"})
    write_json(os.path.join(ent, "manticore.json"), {"type": "minecraft:entity", "pools": [
        lpool([item_entry("leather", 4, 8)]),
        lpool([item_entry("phantom_membrane", 2, 4)]),
        lpool([item_entry("bone", 3, 6)]),
        lpool([item_entry("spider_eye", 2, 4), item_entry("arrow", 8, 16)]),
    ], "random_sequence": NS + ":entities/manticore"})

    ch = os.path.join(DATA, "loot_table/chests")
    write_json(os.path.join(ch, "false_hydra_lair.json"), {"type": "minecraft:chest", "pools": [
        lpool([item_entry("bread", 2, 5, 4), item_entry("potato", 2, 6, 3), item_entry("wheat", 3, 8, 3),
               item_entry("candle", 1, 3, 2), item_entry("paper", 2, 6, 2), item_entry("book", 1, 2, 2),
               item_entry("emerald", 1, 4, 3), item_entry("iron_ingot", 1, 4, 3), item_entry("lead", 1, 1, 1),
               item_entry("name_tag", 1, 1, 1), item_entry("bell", 1, 1, 1)], 3, 6),
        lpool([item_entry("book", 1, 1, 3, [ench(10, 25)]), item_entry("amethyst_shard", 2, 6, 3),
               item_entry("echo_shard", 1, 1, 1), item_entry("golden_apple", 1, 1, 1)], 1, 2),
        lpool([item_entry(NS + ":false_hydra_heart", 1, 1, 1), {"type": "minecraft:empty", "weight": 9}]),
    ], "random_sequence": NS + ":chests/false_hydra_lair"})
    write_json(os.path.join(ch, "mimic_vault.json"), {"type": "minecraft:chest", "pools": [
        lpool([item_entry("gold_ingot", 3, 9, 6), item_entry("gold_nugget", 8, 24, 6), item_entry("emerald", 2, 7, 5),
               item_entry("raw_gold", 2, 6, 3), item_entry("diamond", 1, 2, 2), item_entry("golden_carrot", 2, 6, 3),
               item_entry("lapis_lazuli", 4, 10, 3), item_entry("clock", 1, 1, 1), item_entry("golden_horse_armor", 1, 1, 1)], 3, 6),
        lpool([item_entry("golden_sword", 1, 1, 2, [ench(15, 30)]), item_entry("golden_helmet", 1, 1, 2, [ench(15, 30)]),
               item_entry("book", 1, 1, 3, [ench(15, 30)]), item_entry("golden_apple", 1, 2, 3),
               item_entry("enchanted_golden_apple", 1, 1, 1)], 1, 2),
        lpool([item_entry(NS + ":greedy_coin", 1, 1, 1), {"type": "minecraft:empty", "weight": 4}]),
    ], "random_sequence": NS + ":chests/mimic_vault"})
    write_json(os.path.join(ch, "manticore_den.json"), {"type": "minecraft:chest", "pools": [
        lpool([item_entry("bone", 3, 8, 6), item_entry("leather", 2, 5, 5), item_entry("arrow", 6, 16, 4),
               item_entry("rabbit_hide", 2, 5, 3), item_entry("iron_nugget", 4, 12, 3), item_entry("gold_nugget", 4, 12, 3),
               item_entry("saddle", 1, 1, 1), item_entry("spider_eye", 1, 3, 2)], 3, 6),
        lpool([item_entry("iron_horse_armor", 1, 1, 2), item_entry("golden_horse_armor", 1, 1, 1),
               item_entry("bow", 1, 1, 2, [ench(10, 25)]), item_entry("book", 1, 1, 2, [ench(10, 25)]),
               item_entry("diamond", 1, 2, 1)], 1, 2),
    ], "random_sequence": NS + ":chests/manticore_den"})


# =====================================================================================
#  ESTRUTURAS (covis)
# =====================================================================================
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte, Float, Int  # noqa: E402

STRUCT_OUT = os.path.join(DATA, "structure")
AIR = "minecraft:air"
TRAVEL_STONE = NS + ":travel_stone"


def mc(n):
    return n if ":" in n else "minecraft:" + n


def st(name, **props):
    name = mc(name)
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


class B(Structure):
    def __init__(self, name, sx, sy, sz):
        super().__init__(sx, sy, sz)
        self.name = name
        self.rnd = random.Random("bosses_g/" + name)

    def empty(self, x, y, z):
        v = self.get(x, y, z)
        return v is None or v == AIR

    def soft(self, x, y, z, state, nbt=None):
        if self.empty(x, y, z):
            self.set(x, y, z, state, nbt)

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return s
        return weighted[-1][0]

    def chest(self, x, y, z, loot, facing="north"):
        self.set(x, y, z, st("chest", facing=facing, type="single", waterlogged="false"),
                 nbt={"LootTable": f"{NS}:chests/{loot}"} if loot else None)

    def barrel(self, x, y, z, facing="up"):
        self.set(x, y, z, st("barrel", facing=facing, open="false"))

    def bed(self, x, y, z, color, facing):
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, st(f"{color}_bed", facing=facing, part="foot", occupied="false"))
        self.set(x + dx, y, z + dz, st(f"{color}_bed", facing=facing, part="head", occupied="false"))

    def sign(self, x, y, z, state, keys, color="black", glow=False):
        msgs = [json.dumps({"translate": k}) if k else '""' for k in keys] + ['""'] * (4 - len(keys))
        self.set(x, y, z, state, nbt={
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": Byte(1 if glow else 0)},
            "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)},
            "is_waxed": Byte(1)})

    def boss(self, x, y, z, eid, lair, yaw):
        self.entity(x, y, z, f"{NS}:{eid}", {"PersistenceRequired": Byte(1), "Lair": lair,
                                              "Rotation": [Float(yaw), Float(0.0)]})

    def jig(self, x, y, z, orientation, name, target="minecraft:empty", pool="minecraft:empty", final="minecraft:air"):
        self.set(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", nbt={
            "name": name, "target": target, "pool": pool, "final_state": final, "joint": "aligned",
            "placement_priority": Int(0), "selection_priority": Int(0)})


def save_structure(s, rel):
    n = s.save(os.path.join(STRUCT_OUT, rel + ".nbt"), keep_air=True)
    print("  estrutura %-34s %5d blocos" % (rel, n))


def disk(cx, cz, r):
    for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
        for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                yield x, z, d


ROCK = [("minecraft:stone", 40), ("minecraft:andesite", 8), ("minecraft:tuff", 8), ("minecraft:dirt", 8),
        ("minecraft:cobblestone", 4), ("minecraft:gravel", 3), ("minecraft:coal_ore", 3), ("minecraft:iron_ore", 2),
        ("minecraft:rooted_dirt", 4)]
HY = f"{NS}:false_hydra_lair/"
J_HY_WELL, J_HY_TOP, J_HY_BOT, J_HY_CAV = (f"{NS}:false_hydra_lair_well", f"{NS}:false_hydra_lair_shaft_top",
                                           f"{NS}:false_hydra_lair_shaft_bottom", f"{NS}:false_hydra_lair_cavern_top")
HY_F = 4


def ruined_house(s, x0, z0, w, d, door, seed, F, chest=False, sign_key=None):
    """Casinha abandonada: alicerce de pedra, paredes de madeira com buracos, telhado caído, cama e teias."""
    rnd = random.Random(seed)
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, F, z, s.pick([("minecraft:oak_planks", 5), ("minecraft:mossy_cobblestone", 1), ("minecraft:coarse_dirt", 1)]))
    for y in range(F + 1, F + 4):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    s.set(x, y, z, AIR)
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    s.set(x, y, z, "minecraft:oak_log")
                elif y == F + 1:
                    s.set(x, y, z, s.pick([("minecraft:cobblestone", 3), ("minecraft:mossy_cobblestone", 2)]))
                elif rnd.random() < 0.28:
                    s.set(x, y, z, AIR)
                else:
                    s.set(x, y, z, s.pick([("minecraft:oak_planks", 4), ("minecraft:stripped_oak_log[axis=y]", 1)]))
    # porta (vazia) e janelas
    dx, dz = door
    for y in (F + 1, F + 2):
        s.set(dx, y, dz, AIR)
    # telhado meio caido
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0, z1 + 1):
            if rnd.random() < 0.55:
                s.set(x, F + 4, z, st("oak_slab", type="bottom", waterlogged="false"))
    for x in range(x0, x1 + 1):
        if rnd.random() < 0.5:
            s.set(x, F + 5, (z0 + z1) // 2, "minecraft:oak_planks")
    # dentro
    bx, bz = x0 + 1, z0 + 1
    s.bed(bx, F + 1, bz, rnd.choice(("red", "white", "brown", "light_gray")), "south")
    s.set(x1 - 1, F + 1, z0 + 1, st("campfire", lit="false", signal_fire="false", waterlogged="false", facing="north"))
    s.set(x1 - 1, F + 3, z1 - 1, "minecraft:cobweb")
    s.set(x0 + 1, F + 3, z1 - 1, "minecraft:cobweb")
    s.set(x1 - 1, F + 1, z1 - 1, "minecraft:flower_pot")
    if chest:
        s.chest(x0 + 1, F + 1, z1 - 1, "false_hydra_lair", "east")
    else:
        s.barrel(x0 + 1, F + 1, z1 - 1)
    if sign_key:
        s.sign(x1 - 1, F + 2, z0 + 1, st("oak_wall_sign", facing="south", waterlogged="false"), sign_key)


def hydra_village():
    F = HY_F
    s = B("false_hydra_village", 41, F + 13, 41)
    c = 20
    # chao da vila (disco) + ar por cima
    for x, z, d in disk(c, c, 19.5):
        if not (0 <= x < 41 and 0 <= z < 41):
            continue
        for y in range(0, F):
            s.set(x, y, z, s.pick([("minecraft:dirt", 6), ("minecraft:stone", 3), ("minecraft:coarse_dirt", 1)]))
        top = s.pick([("minecraft:grass_block[snowy=false]", 8), ("minecraft:coarse_dirt", 3), ("minecraft:podzol[snowy=false]", 1),
                      ("minecraft:rooted_dirt", 1)])
        s.set(x, F, z, top)
        for y in range(F + 1, F + 13):
            s.set(x, y, z, AIR)
    # ruas em cruz
    for i in range(3, 38):
        for w in (-1, 0, 1):
            for (x, z) in ((i, c + w), (c + w, i)):
                if s.rnd.random() < 0.8:
                    s.set(x, F, z, s.pick([("minecraft:dirt_path", 5), ("minecraft:gravel", 2), ("minecraft:coarse_dirt", 2)]))
    # poço: anel de pedra, buraco 3x3 que desce (jigsaw no fundo), escada de mão no lado norte
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            ring = x in (c - 2, c + 2) or z in (c - 2, c + 2)
            for y in range(0, F + 1):
                s.set(x, y, z, s.pick([("minecraft:cobblestone", 3), ("minecraft:mossy_cobblestone", 2)]) if ring else AIR)
            if ring:
                s.set(x, F + 1, z, s.pick([("minecraft:cobblestone_wall", 1), ("minecraft:mossy_cobblestone_wall", 1)]) + "[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    for y in range(0, F + 1):
        s.set(c, y, c - 1, st("ladder", facing="south", waterlogged="false"))
    s.set(c, F + 1, c - 2, AIR)
    s.jig(c, 0, c, "down_north", J_HY_WELL, J_HY_TOP, HY + "shafts")
    for (x, z) in ((c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)):
        for y in (F + 2, F + 3):
            s.set(x, y, z, st("oak_fence", waterlogged="false", north="false", south="false", east="false", west="false"))
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            if s.rnd.random() < 0.7:
                s.set(x, F + 4, z, st("spruce_slab", type="bottom", waterlogged="false"))
    s.set(c, F + 3, c, st("chain", axis="y", waterlogged="false"))
    # mural de avisos (placas) e sino caído
    s.set(c + 4, F + 1, c - 4, "minecraft:spruce_log[axis=y]")
    s.set(c + 4, F + 2, c - 4, "minecraft:spruce_log[axis=y]")
    s.sign(c + 4, F + 2, c - 5, st("spruce_wall_sign", facing="north", waterlogged="false"),
           ["sign.isekai_mahou.false_hydra_lair.board_1", "sign.isekai_mahou.false_hydra_lair.board_2",
            "sign.isekai_mahou.false_hydra_lair.board_3"], color="white")
    s.sign(c + 3, F + 2, c - 4, st("spruce_wall_sign", facing="west", waterlogged="false"),
           ["sign.isekai_mahou.false_hydra_lair.missing_1", "sign.isekai_mahou.false_hydra_lair.missing_2",
            "sign.isekai_mahou.false_hydra_lair.missing_3"])
    s.set(c - 4, F + 1, c + 5, st("bell", attachment="floor", facing="east", powered="false"))
    # Pedra de Teleporte perto do poço
    s.set(c + 5, F + 1, c + 4, TRAVEL_STONE)
    # casas
    ruined_house(s, 4, 4, 8, 6, (8, 9), 1, F, chest=True, sign_key=["sign.isekai_mahou.false_hydra_lair.house_1",
                                                                     "sign.isekai_mahou.false_hydra_lair.house_2"])
    ruined_house(s, 29, 4, 7, 6, (31, 9), 2, F)
    ruined_house(s, 4, 30, 7, 7, (8, 30), 3, F, sign_key=["sign.isekai_mahou.false_hydra_lair.house_3",
                                                         "sign.isekai_mahou.false_hydra_lair.house_4"])
    ruined_house(s, 29, 29, 8, 7, (32, 29), 4, F, chest=True)
    ruined_house(s, 3, 16, 6, 6, (8, 18), 5, F)
    # mato morto, teias e cerca de uma horta abandonada
    for _ in range(40):
        x, z = s.rnd.randint(2, 38), s.rnd.randint(2, 38)
        if s.get(x, F, z) and "grass" in s.get(x, F, z) and s.get(x, F + 1, z) == AIR:
            s.set(x, F + 1, z, s.pick([("minecraft:short_grass", 4), ("minecraft:dead_bush", 2), ("minecraft:fern", 1)]))
    for x in range(24, 34):
        for z in (14, 18):
            if s.rnd.random() < 0.7:
                s.set(x, F + 1, z, st("oak_fence", waterlogged="false", north="false", south="false",
                                      east="true" if x < 33 else "false", west="true" if x > 24 else "false"))
    for x in range(25, 33):
        for z in range(15, 18):
            s.set(x, F, z, st("farmland", moisture="0"))
            if s.rnd.random() < 0.4:
                s.set(x, F + 1, z, "minecraft:dead_bush" if s.rnd.random() < 0.5 else st("wheat", age=str(s.rnd.randint(0, 3))))
    save_structure(s, "false_hydra_lair/village")


def hydra_shaft():
    H_ = 14
    s = B("false_hydra_shaft", 5, H_, 5)
    for y in range(H_):
        for x in range(5):
            for z in range(5):
                if x in (0, 4) or z in (0, 4):
                    s.set(x, y, z, s.pick(ROCK))
                else:
                    s.set(x, y, z, AIR)
        s.set(2, y, 1, st("ladder", facing="south", waterlogged="false"))
        if y % 5 == 2:
            s.set(1, y, 3, "minecraft:cobweb")
    s.set(3, 7, 3, st("soul_lantern", hanging="false", waterlogged="false"))
    s.jig(2, H_ - 1, 2, "up_north", J_HY_TOP)
    s.jig(2, 0, 2, "down_north", J_HY_BOT, J_HY_CAV, HY + "caverns")
    save_structure(s, "false_hydra_lair/shaft")


def mound(s, cx, cz, floor, r=1.6, h=2):
    for x, z, d in disk(cx, cz, r):
        if d <= r * 0.5 and h >= 2:
            s.set(x, floor + 2, z, "minecraft:rooted_dirt")
        s.set(x, floor + 1, z, s.pick([("minecraft:rooted_dirt", 3), ("minecraft:coarse_dirt", 2), ("minecraft:mud", 1)]))


def hydra_cavern():
    W, Hh = 41, 16
    s = B("false_hydra_cavern", W, Hh, W)
    cx, cz, floor = 20, 20, 1
    for x in range(W):
        for z in range(W):
            for y in range(Hh):
                s.set(x, y, z, s.pick(ROCK))
    # domo
    for x in range(W):
        for z in range(W):
            for y in range(floor + 1, Hh):
                v = ((x - cx) / 18.5) ** 2 + ((y - floor - 1) / 13.0) ** 2 + ((z - cz) / 18.5) ** 2
                if v <= 1.0:
                    s.set(x, y, z, AIR)
            if ((x - cx) / 18.5) ** 2 + ((z - cz) / 18.5) ** 2 <= 1.0:
                s.set(x, floor, z, s.pick([("minecraft:rooted_dirt", 5), ("minecraft:coarse_dirt", 4), ("minecraft:mud", 2),
                                           ("minecraft:moss_block", 2), ("minecraft:podzol[snowy=false]", 1)]))
    # entrada: buraco no teto + pilar de madeira com escada de mão até o chão
    for x in range(7, 10):
        for z in range(19, 22):
            for y in range(9, Hh):
                s.set(x, y, z, AIR)
    for y in range(floor + 1, Hh):
        s.set(8, y, 18, "minecraft:spruce_log[axis=y]")
        s.set(8, y, 19, st("ladder", facing="south", waterlogged="false"))
    s.jig(8, Hh - 1, 20, "up_north", J_HY_CAV)
    s.set(8, Hh - 1, 19, st("ladder", facing="south", waterlogged="false"))
    for (x, z) in ((7, 18), (9, 18)):
        for y in range(floor + 1, floor + 4):
            s.set(x, y, z, st("spruce_fence", waterlogged="false", north="false", south="false",
                              east="true" if x == 7 else "false", west="true" if x == 9 else "false"))
    s.set(9, floor + 6, 19, st("soul_lantern", hanging="false", waterlogged="false"))
    # montes de terra (o corpo esta debaixo de um deles: o do centro) e iscas
    for (x, z) in ((cx, cz + 2), (11, 12), (29, 11), (30, 28), (12, 29), (20, 9), (31, 20)):
        mound(s, x, z, floor, 1.8 if (x, z) == (cx, cz + 2) else 1.4)
    # raízes pendentes e líquen brilhante (luz fraca)
    for _ in range(90):
        x, z = s.rnd.randint(3, 37), s.rnd.randint(3, 37)
        for y in range(Hh - 2, floor + 3, -1):
            if s.get(x, y, z) == AIR and s.get(x, y + 1, z) not in (None, AIR):
                s.set(x, y, z, s.pick([("minecraft:hanging_roots[waterlogged=false]", 3),
                                       ("minecraft:glow_lichen[up=true,down=false,north=false,south=false,east=false,west=false,waterlogged=false]", 2)]))
                break
    # restos dos aldeões que "ninguém lembra"
    camps = ((12, 25, "red"), (27, 14, "white"), (27, 26, "light_blue"), (14, 14, "brown"))
    for k, (x, z, color) in enumerate(camps):
        s.bed(x, floor + 1, z, color, "east")
        s.set(x, floor + 1, z + 1, st("skeleton_skull", rotation=str(s.rnd.randint(0, 15))))
        s.set(x + 2, floor + 1, z, "minecraft:bone_block[axis=x]")
        s.set(x + 2, floor + 2, z, st("candle", candles=str(s.rnd.randint(1, 3)), lit="true", waterlogged="false"))
        s.set(x - 1, floor + 1, z - 1, st("flower_pot"))
        if k in (0, 2):
            s.chest(x + 1, floor + 1, z - 1, "false_hydra_lair", "south")
        else:
            s.barrel(x + 1, floor + 1, z - 1)
    for _ in range(30):
        x, z = s.rnd.randint(6, 34), s.rnd.randint(6, 34)
        if s.get(x, floor + 1, z) == AIR and math.hypot(x - cx, z - cz) > 4:
            s.set(x, floor + 1, z, s.pick([("minecraft:bone_block[axis=y]", 2), ("minecraft:skeleton_skull[rotation=3]", 1),
                                           ("minecraft:cobweb", 1), ("minecraft:dead_bush", 1)]))
    for (x, z) in ((16, 22), (24, 22), (20, 26), (20, 17)):
        s.set(x, floor + 1, z, st("candle", candles="4", lit="true", waterlogged="false"))
    s.boss(cx + 0.5, floor + 1, cz + 2.5, "false_hydra", "false_hydra_lair", 90.0)
    save_structure(s, "false_hydra_lair/cavern")


# ---------------- Cofre do Tesouro ----------------
MV = f"{NS}:mimic_vault/"
J_MV_GATE, J_MV_TOP, J_MV_BOT, J_MV_HALL = (f"{NS}:mimic_vault_gate", f"{NS}:mimic_vault_stair_top",
                                            f"{NS}:mimic_vault_stair_bottom", f"{NS}:mimic_vault_hall_top")
MV_F = 3
BLACK = [("minecraft:polished_blackstone_bricks", 8), ("minecraft:cracked_polished_blackstone_bricks", 2),
         ("minecraft:gilded_blackstone", 1)]


def vault_gate():
    F = MV_F
    s = B("mimic_vault_gate", 13, F + 9, 13)
    c = 6
    for x, z, d in disk(c, c, 6.4):
        if not (0 <= x < 13 and 0 <= z < 13):
            continue
        for y in range(0, F):
            s.set(x, y, z, s.pick([("minecraft:stone_bricks", 4), ("minecraft:cobblestone", 2), ("minecraft:dirt", 2)]))
        s.set(x, F, z, s.pick([("minecraft:stone_bricks", 4), ("minecraft:mossy_stone_bricks", 2), ("minecraft:cracked_stone_bricks", 2),
                               ("minecraft:gold_block", 0.3)]))
        for y in range(F + 1, F + 9):
            s.set(x, y, z, AIR)
    # pilares quebrados com ouro no topo
    for (x, z, h) in ((2, 2, 5), (10, 2, 3), (2, 10, 4), (10, 10, 6)):
        for y in range(F + 1, F + 1 + h):
            s.set(x, y, z, s.pick([("minecraft:stone_bricks", 3), ("minecraft:cracked_stone_bricks", 1), ("minecraft:chiseled_stone_bricks", 1)]))
        s.set(x, F + 1 + h, z, "minecraft:gold_block" if h >= 5 else st("stone_brick_slab", type="bottom", waterlogged="false"))
    # arco de entrada (norte)
    for y in range(F + 1, F + 6):
        s.set(4, y, 1, "minecraft:polished_blackstone_bricks")
        s.set(8, y, 1, "minecraft:polished_blackstone_bricks")
    for x in range(4, 9):
        s.set(x, F + 6, 1, "minecraft:polished_blackstone_bricks")
    s.set(6, F + 6, 1, "minecraft:gold_block")
    s.set(6, F + 7, 1, "minecraft:gilded_blackstone")
    s.sign(6, F + 5, 0, st("dark_oak_wall_sign", facing="north", waterlogged="false"),
           ["sign.isekai_mahou.mimic_vault.gate_1", "sign.isekai_mahou.mimic_vault.gate_2", "sign.isekai_mahou.mimic_vault.gate_3"],
           color="yellow", glow=True)
    # poço com escada de mão para o cofre
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            for y in range(0, F + 1):
                s.set(x, y, z, AIR)
    for y in range(0, F + 1):
        s.set(c, y, c - 1, st("ladder", facing="south", waterlogged="false"))
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            if x in (c - 2, c + 2) or z in (c - 2, c + 2):
                for y in range(0, F + 1):
                    s.set(x, y, z, "minecraft:polished_blackstone_bricks")
                if not (x == c and z == c - 2):
                    s.set(x, F + 1, z, st("polished_blackstone_brick_slab", type="bottom", waterlogged="false"))
    s.set(c - 2, F + 2, c - 2, st("lantern", hanging="false", waterlogged="false"))
    s.set(c + 2, F + 2, c + 2, st("lantern", hanging="false", waterlogged="false"))
    s.jig(c, 0, c, "down_north", J_MV_GATE, J_MV_TOP, MV + "stairs")
    s.set(10, F + 1, 7, TRAVEL_STONE)
    save_structure(s, "mimic_vault/gate")


def vault_stairs():
    H_ = 12
    s = B("mimic_vault_stairs", 5, H_, 5)
    for y in range(H_):
        for x in range(5):
            for z in range(5):
                if x in (0, 4) or z in (0, 4):
                    s.set(x, y, z, s.pick(BLACK) if (x + z + y) % 4 else "minecraft:gold_block" if y % 6 == 3 else s.pick(BLACK))
                else:
                    s.set(x, y, z, AIR)
        s.set(2, y, 1, st("ladder", facing="south", waterlogged="false"))
    s.set(3, 6, 3, st("lantern", hanging="false", waterlogged="false"))
    s.jig(2, H_ - 1, 2, "up_north", J_MV_TOP)
    s.jig(2, 0, 2, "down_north", J_MV_BOT, J_MV_HALL, MV + "halls")
    save_structure(s, "mimic_vault/stairs")


def vault_hall():
    W, Hh = 35, 14
    s = B("mimic_vault_hall", W, Hh, W)
    c = 17
    for x in range(W):
        for z in range(W):
            for y in range(Hh):
                shell = x in (0, W - 1) or z in (0, W - 1) or y in (0, Hh - 1)
                s.set(x, y, z, s.pick(BLACK) if shell else AIR)
            s.set(x, 0, z, s.pick([("minecraft:polished_blackstone", 6), ("minecraft:polished_blackstone_bricks", 3),
                                   ("minecraft:gold_block", 0.4)]))
    # tapete vermelho do portao ate o trono + borda dourada
    for z in range(4, 30):
        for x in range(c - 1, c + 2):
            s.set(x, 1, z, "minecraft:red_carpet")
        s.set(c - 2, 0, z, "minecraft:gold_block")
        s.set(c + 2, 0, z, "minecraft:gold_block")
    # pilares com ouro e lanternas penduradas
    for px in (6, 12, 22, 28):
        for pz in (8, 16, 24):
            for y in range(1, Hh - 1):
                s.set(px, y, pz, "minecraft:polished_blackstone_bricks" if y % 4 else "minecraft:gilded_blackstone")
            s.set(px, Hh - 2, pz, "minecraft:gold_block")
    for (x, z) in ((9, 12), (25, 12), (9, 20), (25, 20), (c, 10), (c, 22)):
        for y in range(Hh - 4, Hh - 1):
            s.set(x, y, z, st("chain", axis="y", waterlogged="false"))
        s.set(x, Hh - 5, z, st("lantern", hanging="true", waterlogged="false"))
    # entrada: pilar com escada de mão vindo do teto
    for y in range(1, Hh):
        s.set(6, y, 4, "minecraft:polished_blackstone_bricks")
        s.set(6, y, 5, st("ladder", facing="south", waterlogged="false"))
    s.jig(6, Hh - 1, 6, "up_north", J_MV_HALL)
    s.set(6, Hh - 1, 5, st("ladder", facing="south", waterlogged="false"))
    # montes de ouro
    for (x, z, r) in ((4, 28, 2.2), (30, 28, 2.4), (30, 4, 2.0), (12, 30, 1.6), (24, 30, 1.6)):
        for xx, zz, d in disk(x, z, r):
            if 1 <= xx < W - 1 and 1 <= zz < W - 1:
                s.set(xx, 1, zz, s.pick([("minecraft:gold_block", 3), ("minecraft:raw_gold_block", 2)]))
                if d < r * 0.5:
                    s.set(xx, 2, zz, "minecraft:gold_block")
    # baús ao longo das paredes (o Rei os desperta como mímicos) e barris
    chests = ((3, 10, "east"), (3, 15, "east"), (3, 20, "east"), (31, 10, "west"), (31, 15, "west"), (31, 20, "west"),
              (10, 3, "south"), (24, 3, "south"), (11, 31, "north"), (23, 31, "north"), (14, 26, "north"), (20, 26, "north"))
    for k, (x, z, f) in enumerate(chests):
        s.chest(x, 1, z, "mimic_vault" if k % 3 != 1 else None, f)
    for (x, z) in ((3, 12), (3, 18), (31, 12), (31, 18)):
        s.barrel(x, 1, z, "up")
        s.set(x, 2, z, st("candle", candles="3", lit="true", waterlogged="false"))
    # trono do Rei (no fundo, sul)
    for x in range(c - 4, c + 5):
        for z in range(28, 33):
            s.set(x, 1, z, "minecraft:polished_blackstone")
        s.set(x, 1, 27, st("polished_blackstone_stairs", facing="south", half="bottom", shape="straight", waterlogged="false"))
    for x in range(c - 2, c + 3):
        for y in range(2, 7):
            s.set(x, y, 32, "minecraft:gold_block" if (x + y) % 2 else "minecraft:gilded_blackstone")
    s.set(c, 7, 32, "minecraft:gold_block")
    s.sign(c, 4, 31, st("dark_oak_wall_sign", facing="north", waterlogged="false"),
           ["sign.isekai_mahou.mimic_vault.throne_1", "sign.isekai_mahou.mimic_vault.throne_2"], color="yellow", glow=True)
    s.boss(c + 0.5, 1, c + 2.5, "mimic_king", "mimic_vault", 180.0)
    save_structure(s, "mimic_vault/hall")


# ---------------- Toca da Manticora ----------------
MD_F = 3
TERRA = [("minecraft:terracotta", 3), ("minecraft:orange_terracotta", 3), ("minecraft:red_terracotta", 2),
         ("minecraft:brown_terracotta", 1), ("minecraft:yellow_terracotta", 1), ("minecraft:white_terracotta", 0.6)]


def manticore_den():
    F = MD_F
    W, Hh = 33, F + 18
    s = B("manticore_den", W, Hh, W)
    c = 16
    layers = ["minecraft:red_terracotta", "minecraft:orange_terracotta", "minecraft:terracotta", "minecraft:yellow_terracotta",
              "minecraft:orange_terracotta", "minecraft:brown_terracotta", "minecraft:white_terracotta", "minecraft:terracotta"]
    for x, z, d in disk(c, c, 16.4):
        if not (0 <= x < W and 0 <= z < W):
            continue
        for y in range(0, F):
            s.set(x, y, z, s.pick([("minecraft:red_sandstone", 3), ("minecraft:terracotta", 2), ("minecraft:red_sand", 1)]))
        ang = math.atan2(z - c, x - c)
        gate = abs(x - c) <= 2 and z > c          # abertura ao sul
        rim_h = int(9 + 4 * math.sin(ang * 3) + 2 * math.cos(ang * 7)) if d > 12.0 else 0
        if gate and d > 11.5:
            rim_h = 0
        if d > 12.0:
            for y in range(F, F + 1 + rim_h):
                s.set(x, y, z, layers[(y + int(d)) % len(layers)])
            for y in range(F + 1 + rim_h, Hh):
                s.set(x, y, z, AIR)
        else:
            s.set(x, F, z, s.pick([("minecraft:red_sand", 4), ("minecraft:sand", 2), ("minecraft:coarse_dirt", 2),
                                   ("minecraft:packed_mud", 1)]))
            for y in range(F + 1, Hh):
                s.set(x, y, z, AIR)
        if gate and d > 11.5:
            s.set(x, F, z, s.pick([("minecraft:red_sand", 3), ("minecraft:coarse_dirt", 1)]))
    # toca (caverna) na parede norte
    for x in range(c - 3, c + 4):
        for z in range(0, 6):
            for y in range(F + 1, F + 6):
                if math.hypot((x - c) / 3.5, (y - F - 1) / 5.0) <= 1.0:
                    s.set(x, y, z, AIR)
            s.set(x, F, z, s.pick([("minecraft:hay_block[axis=x]", 2), ("minecraft:coarse_dirt", 2), ("minecraft:red_sand", 1)]))
    s.set(c - 2, F + 1, 2, "minecraft:bone_block[axis=z]")
    s.set(c + 2, F + 1, 1, st("skeleton_skull", rotation="8"))
    s.chest(c + 2, F + 1, 3, "manticore_den", "south")
    s.set(c - 1, F + 1, 1, "minecraft:dead_bush")
    # ossadas (costelas) e espinhos cravados no chão da arena
    for (x, z, ax) in ((10, 20, "x"), (22, 11, "z"), (21, 21, "x")):
        for k in range(4):
            s.set(x + (k if ax == "x" else 0), F + 1, z + (k if ax == "z" else 0), f"minecraft:bone_block[axis={ax}]")
            if k % 2 == 0:
                s.set(x + (k if ax == "x" else 0), F + 2, z + (k if ax == "z" else 0), "minecraft:bone_block[axis=y]")
    for _ in range(16):
        x, z = s.rnd.randint(6, 26), s.rnd.randint(6, 26)
        if s.get(x, F + 1, z) == AIR and math.hypot(x - c, z - c) > 3.5 and math.hypot(x - c, z - c) < 11:
            s.set(x, F + 1, z, s.pick([("minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]", 3),
                                       ("minecraft:dead_bush", 2), ("minecraft:skeleton_skull[rotation=5]", 1)]))
    s.sign(c + 2, F + 1, 29, st("acacia_sign", rotation="8", waterlogged="false"),
           ["sign.isekai_mahou.manticore_den.warn_1", "sign.isekai_mahou.manticore_den.warn_2", "sign.isekai_mahou.manticore_den.warn_3"])
    s.set(c - 2, F + 1, 30, TRAVEL_STONE)
    s.boss(c + 0.5, F + 1, c + 0.5, "manticore", "manticore_den", 0.0)
    save_structure(s, "manticore_den/den")


# =====================================================================================
#  WORLDGEN + TAGS
# =====================================================================================
EXCLUSION = [{"other_set": "minecraft:villages", "chunk_count": 4},
             {"other_set": f"{NS}:guild_city", "chunk_count": 10},
             {"other_set": f"{NS}:capital_city", "chunk_count": 13},
             {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
             {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13}]

LAIRS = {
    # id: (biomas, tamanho jigsaw, start_height, adaptacao, etapa, espacamento, separacao, salt)
    "false_hydra_lair": (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:birch_forest",
                          "minecraft:dark_forest", "minecraft:taiga", "minecraft:meadow", "minecraft:old_growth_birch_forest"],
                         3, -HY_F, "none", "surface_structures", 52, 20, 1946300901),
    "mimic_vault": (["minecraft:desert", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:badlands",
                     "minecraft:wooded_badlands", "minecraft:plains", "minecraft:jungle", "minecraft:sparse_jungle",
                     "minecraft:taiga"],
                    3, -MV_F, "none", "surface_structures", 56, 22, 1946300902),
    "manticore_den": (["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:savanna",
                       "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:desert"],
                      1, -MD_F, "beard_box", "surface_structures", 48, 18, 1946300903),
}
POOLS = {
    "false_hydra_lair/start": ["false_hydra_lair/village"],
    "false_hydra_lair/shafts": ["false_hydra_lair/shaft"],
    "false_hydra_lair/caverns": ["false_hydra_lair/cavern"],
    "mimic_vault/start": ["mimic_vault/gate"],
    "mimic_vault/stairs": ["mimic_vault/stairs"],
    "mimic_vault/halls": ["mimic_vault/hall"],
    "manticore_den/start": ["manticore_den/den"],
}


def write_worldgen():
    for pool, locs in POOLS.items():
        write_json(os.path.join(DATA, f"worldgen/template_pool/{pool}.json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:{loc}",
                                                    "processors": "minecraft:empty", "projection": "rigid"}} for loc in locs]})
    for sid, (biomes, size, sh, adapt, step, spacing, sep, salt) in LAIRS.items():
        write_json(os.path.join(DATA, f"worldgen/structure/{sid}.json"), {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{sid}",
            "step": step,
            "terrain_adaptation": adapt,
            "spawn_overrides": {},
            "start_pool": f"{NS}:{sid}/start",
            "size": size,
            "start_height": {"absolute": sh},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False})
        write_json(os.path.join(DATA, f"worldgen/structure_set/{sid}.json"), {
            "structures": [{"structure": f"{NS}:{sid}", "weight": 1}],
            "placement": {"type": f"{NS}:random_spread_multi", "spacing": spacing, "separation": sep, "salt": salt,
                          "exclusion_zones": EXCLUSION}})
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"), {"values": biomes})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "boss_lair.json"), {"values": [f"{NS}:{sid}" for sid in LAIRS]})
    write_json(os.path.join(tags, "bosses_g_lairs.json"), {"values": [f"{NS}:{sid}" for sid in LAIRS]})
    for sid in LAIRS:
        write_json(os.path.join(tags, f"lair/{sid}.json"), {"values": [f"{NS}:{sid}"]})
    bosses = [f"{NS}:{b}" for b in ("false_hydra", "mimic_king", "manticore")]
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": bosses})


# =====================================================================================
#  TRADUCOES (pt_br principal, en_us obrigatorio)
# =====================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def write_lang():
    # entidades, ovos e itens
    for eid, pt, en in (("false_hydra", "Falsa Hidra", "False Hydra"), ("false_hydra_head", "Cabeça da Falsa Hidra", "False Hydra Head"),
                        ("mimic_king", "Rei Mímico", "Mimic King"), ("vault_mimic", "Mímico do Cofre", "Vault Mimic"),
                        ("manticore", "Manticora", "Manticore")):
        t(f"entity.isekai_mahou.{eid}", pt, en)
        t(f"item.isekai_mahou.{eid}_spawn_egg", f"Ovo Gerador de {pt}", f"{en} Spawn Egg")
    t("entity.isekai_mahou.manticore_spike", "Espinho de Manticora", "Manticore Spike")
    t("item.isekai_mahou.false_hydra_heart", "Coração da Falsa Hidra", "False Hydra Heart")
    t("item.isekai_mahou.false_hydra_heart.desc", "Ainda pulsa... e ainda canta baixinho.", "It still beats... and still hums softly.")
    t("item.isekai_mahou.false_hydra_heart.use", "Usar: 45 s invisível e veloz; monstros a 32 blocos esquecem você.",
      "Use: 45 s invisible and swift; monsters within 32 blocks forget you.")
    t("item.isekai_mahou.false_hydra_heart.used", "O canto ecoa... %s criaturas esqueceram você.", "The song echoes... %s creatures forgot you.")
    t("item.isekai_mahou.greedy_coin", "Moeda Gananciosa", "Greedy Coin")
    t("item.isekai_mahou.greedy_coin.desc", "Tem dentinhos. Às vezes morde de volta.", "It has tiny teeth. Sometimes it bites back.")
    t("item.isekai_mahou.greedy_coin.use", "Usar: 10 min de Sorte II e Herói da Vila.", "Use: 10 min of Luck II and Hero of the Village.")
    t("item.isekai_mahou.greedy_coin.used", "A moeda gira no ar... a sorte sorri para você!", "The coin spins in the air... fortune smiles at you!")
    t("item.isekai_mahou.manticore_stinger", "Ferrão da Manticora", "Manticore Stinger")
    t("item.isekai_mahou.manticore_stinger.desc", "Cada golpe envenena (Veneno II) e faz sangrar.", "Every hit poisons (Poison II) and causes bleeding.")

    # lugares (covis registrados no Places)
    t("place.isekai_mahou.false_hydra_lair", "Vila Esquecida", "The Forgotten Village")
    t("place.isekai_mahou.mimic_vault", "Cofre do Rei Mímico", "Vault of the Mimic King")
    t("place.isekai_mahou.manticore_den", "Toca da Manticora", "Manticore Den")

    # comando
    t("command.isekai_mahou.bosses_g.spawn_done", "%s foi invocado!", "%s has been summoned!")
    t("command.isekai_mahou.bosses_g.spawn_fail", "Não foi possível invocar o chefe aqui.", "Could not summon the boss here.")

    # ---------------- Falsa Hidra
    h = "false_hydra"
    t(f"boss.isekai_mahou.{h}.subtitle", "Aquilo que Ninguém Lembra", "That Which No One Remembers")
    t(f"boss.isekai_mahou.{h}.phase1", "O Coro Desperta", "The Choir Awakens")
    t(f"boss.isekai_mahou.{h}.phase2", "Canção Sem Fim", "Endless Song")
    t(f"announce.isekai_mahou.{h}.intro", "Shhh... você nunca esteve aqui. Ninguém nunca esteve aqui...",
      "Shhh... you were never here. No one was ever here...")
    t(f"announce.isekai_mahou.{h}.phase1", "Mais vozes... mais bocas... ninguém vai lembrar!", "More voices... more mouths... no one will remember!")
    t(f"announce.isekai_mahou.{h}.phase2", "CANTEM COMIGO! ESQUEÇAM TUDO!", "SING WITH ME! FORGET EVERYTHING!")
    t(f"announce.isekai_mahou.{h}.death", "Agora... alguém... vai lembrar... de mim...", "Now... someone... will remember... me...")
    t(f"announce.isekai_mahou.{h}.erupt", "Debaixo de você...", "Beneath you...")
    t(f"announce.isekai_mahou.{h}.sing", "♪ Durma... esqueça... ♪", "♪ Sleep... forget... ♪")
    t(f"announce.isekai_mahou.{h}.interrupted", "GRAAAH! Minha canção!", "GRAAAH! My song!")
    t(f"announce.isekai_mahou.{h}.forgotten", "Quem é você mesmo? Hihihi...", "Who are you again? Hehehe...")
    t(f"message.isekai_mahou.{h}.heads", "As cabeças somem quando você olha direto — vigie pelo canto do olho!",
      "The heads vanish when you look straight at them — watch from the corner of your eye!")
    t(f"message.isekai_mahou.{h}.buried", "O corpo está enterrado: golpes mal o arranham. Insista para fazê-lo emergir!",
      "The body is buried: blows barely scratch it. Keep hitting to force it out!")
    t(f"message.isekai_mahou.{h}.exposed", "O corpo emergiu! Agora é a hora de feri-lo!", "The body has surfaced! Now is the time to strike!")
    t(f"message.isekai_mahou.{h}.song", "Ela está cantando! Fira o CORPO (ou contrarie) antes que o canto termine!",
      "It is singing! Hurt the BODY (or counter it) before the song ends!")
    t(f"message.isekai_mahou.{h}.severed", "Cabeça cortada! O corpo sangra — mas ela volta a crescer.",
      "Head severed! The body bleeds — but it will grow back.")
    for i, (pt, en) in enumerate((("...o que eu estava fazendo mesmo?", "...what was I doing again?"),
                                   ("...quem... sou eu...?", "...who... am I...?"),
                                   ("...tinha alguém comigo... não tinha?", "...someone was with me... weren't they?"),
                                   ("...por que estou segurando isto?", "...why am I holding this?"))):
        t(f"message.isekai_mahou.{h}.wipe_{i}", pt, en)
    t(f"message.isekai_mahou.{h}.wipe_hint", "(o canto apagou sua memória por um instante)", "(the song wiped your memory for a moment)")
    t(f"attack.isekai_mahou.{h}.erupt", "Falsa Hidra — Erupção", "False Hydra — Eruption")
    t(f"attack.isekai_mahou.{h}.song", "Falsa Hidra — Canto do Esquecimento", "False Hydra — Song of Oblivion")

    # ---------------- Rei Mímico
    k = "mimic_king"
    t(f"boss.isekai_mahou.{k}.subtitle", "O Tesouro que Devora", "The Treasure That Devours")
    t(f"boss.isekai_mahou.{k}.phase1", "Ganância Sem Fundo", "Bottomless Greed")
    t(f"boss.isekai_mahou.{k}.phase2", "A Bocarra se Abre", "The Maw Opens")
    t(f"announce.isekai_mahou.{k}.intro", "Hehehe... mais um ladrãozinho veio buscar o MEU ouro?", "Hehehe... another little thief here for MY gold?")
    t(f"announce.isekai_mahou.{k}.phase1", "Meu tesouro! MEU! Chuva de ouro sobre vocês!", "My treasure! MINE! Gold rains upon you!")
    t(f"announce.isekai_mahou.{k}.phase2", "Já que gostam tanto de baús... ENTREM!", "Since you love chests so much... GET IN!")
    t(f"announce.isekai_mahou.{k}.death", "Meu... ouro... é... de vocês...", "My... gold... is... yours...")
    t(f"announce.isekai_mahou.{k}.choke", "Cof! COF! Indigesto!", "Cough! COUGH! Indigestible!")
    t(f"announce.isekai_mahou.{k}.devour", "Nhac! Que tesouro saboroso!", "Chomp! What a tasty treasure!")
    t(f"announce.isekai_mahou.{k}.greed", "Querem ouro? TOMEM OURO!", "You want gold? HAVE SOME GOLD!")
    t(f"announce.isekai_mahou.{k}.awaken", "Acordem, meus baús! Temos visitas!", "Wake up, my chests! We have guests!")
    t(f"announce.isekai_mahou.{k}.maw", "Venham... venham para dentro...", "Come... come inside...")
    t(f"message.isekai_mahou.{k}.devour", "Alguém foi devorado! Batam no Rei (7% da vida) para fazê-lo cuspir!",
      "Someone was devoured! Hit the King (7% of its health) to make it spit them out!")
    t(f"message.isekai_mahou.{k}.inside", "Você foi devorado! Bata de dentro — por dentro ele é macio!",
      "You were devoured! Hit from inside — it's soft in there!")
    t(f"message.isekai_mahou.{k}.maw", "Ele está sugando! Fique atrás de um pilar ou corra para longe da boca!",
      "It's pulling you in! Hide behind a pillar or run away from the maw!")
    t(f"attack.isekai_mahou.{k}.tongue", "Rei Mímico — Língua", "Mimic King — Tongue Lash")
    t(f"attack.isekai_mahou.{k}.coins", "Rei Mímico — Chuva de Moedas", "Mimic King — Coin Spray")
    t(f"attack.isekai_mahou.{k}.gold_rain", "Rei Mímico — Ouro do Céu", "Mimic King — Gold From the Sky")
    t(f"attack.isekai_mahou.{k}.awaken", "Rei Mímico — Despertar do Tesouro", "Mimic King — Treasure Awakening")
    t(f"attack.isekai_mahou.{k}.leap", "Rei Mímico — Pancada do Tesouro", "Mimic King — Treasure Slam")
    t(f"attack.isekai_mahou.{k}.maw", "Rei Mímico — Bocarra sem Fundo", "Mimic King — Bottomless Maw")

    # ---------------- Manticora
    m = "manticore"
    t(f"boss.isekai_mahou.{m}.subtitle", "Terror das Rochas Vermelhas", "Terror of the Red Rocks")
    t(f"boss.isekai_mahou.{m}.phase1", "Asas da Fera", "Wings of the Beast")
    t(f"boss.isekai_mahou.{m}.phase2", "Frenesi Venenoso", "Venomous Frenzy")
    t(f"announce.isekai_mahou.{m}.intro", "Carne fresca entrou na minha toca... que gentileza.", "Fresh meat walked into my den... how kind.")
    t(f"announce.isekai_mahou.{m}.phase1", "Vocês acham que o céu é seguro? O céu é MEU!", "You think the sky is safe? The sky is MINE!")
    t(f"announce.isekai_mahou.{m}.phase2", "GRRRAAAH! Vou espetar cada um de vocês!", "GRRRAAAH! I'll skewer every one of you!")
    t(f"announce.isekai_mahou.{m}.death", "A toca... é... de vocês...", "The den... is... yours...")
    t(f"announce.isekai_mahou.{m}.fan", "Espinhos!", "Spikes!")
    t(f"announce.isekai_mahou.{m}.roar", "TREMAM!", "TREMBLE!")
    t(f"announce.isekai_mahou.{m}.flight", "Olhem para cima, presas!", "Look up, prey!")
    t(f"announce.isekai_mahou.{m}.dive", "Peguei você!", "Got you!")
    t(f"message.isekai_mahou.{m}.frightened", "Você está Aterrorizado! Seus golpes na Manticora valem metade.",
      "You are Terrified! Your hits on the Manticore deal half damage.")
    t(f"message.isekai_mahou.{m}.tired", "A Manticora está cansada no chão — ataquem com tudo!", "The Manticore is exhausted on the ground — go all out!")
    t(f"attack.isekai_mahou.{m}.sting", "Manticora — Ferroada", "Manticore — Sting")
    t(f"attack.isekai_mahou.{m}.fan", "Manticora — Leque de Espinhos", "Manticore — Spike Fan")
    t(f"attack.isekai_mahou.{m}.pounce", "Manticora — Bote", "Manticore — Pounce")
    t(f"attack.isekai_mahou.{m}.roar", "Manticora — Rugido Aterrorizante", "Manticore — Terrifying Roar")
    t(f"attack.isekai_mahou.{m}.rain", "Manticora — Chuva de Espinhos", "Manticore — Spike Rain")
    t(f"attack.isekai_mahou.{m}.dive", "Manticora — Mergulho", "Manticore — Dive")

    # placas dos covis (linhas curtas)
    for key, pt, en in (
            ("false_hydra_lair.board_1", "Vila de ???", "Village of ???"),
            ("false_hydra_lair.board_2", "Ninguém lembra", "No one recalls"),
            ("false_hydra_lair.board_3", "o nome daqui.", "this place's name."),
            ("false_hydra_lair.missing_1", "DESAPARECIDOS:", "MISSING:"),
            ("false_hydra_lair.missing_2", "...?", "...?"),
            ("false_hydra_lair.missing_3", "(nomes riscados)", "(names scratched)"),
            ("false_hydra_lair.house_1", "Por que há", "Why are there"),
            ("false_hydra_lair.house_2", "duas camas?", "two beds?"),
            ("false_hydra_lair.house_3", "O poço canta", "The well sings"),
            ("false_hydra_lair.house_4", "à noite.", "at night."),
            ("mimic_vault.gate_1", "COFRE REAL", "ROYAL VAULT"),
            ("mimic_vault.gate_2", "Só o Rei", "Only the King"),
            ("mimic_vault.gate_3", "abre os baús.", "opens the chests."),
            ("mimic_vault.throne_1", "Tudo aqui", "Everything here"),
            ("mimic_vault.throne_2", "tem dentes.", "has teeth."),
            ("manticore_den.warn_1", "VOLTE!", "TURN BACK!"),
            ("manticore_den.warn_2", "Não olhe", "Don't look"),
            ("manticore_den.warn_3", "para cima.", "up.")):
        t(f"sign.isekai_mahou.{key}", pt, en)

    # v1.3 — Tesouro Falso, canto ao vivo
    t("announce.isekai_mahou.mimic_king.bait", "Olhem quanto tesouro! Peguem, peguem... hehehe.",
      "Look at all this treasure! Take it, take it... hehehe.")
    t("announce.isekai_mahou.mimic_king.bait_laugh", "HAHAHA! Ganância tem dentes!", "HAHAHA! Greed has teeth!")
    t("message.isekai_mahou.mimic_king.bait", "Tesouro Falso! O que solta fumaça ou pisca um olho vermelho é isca — não pegue.",
      "Fake Treasure! Anything smoking or blinking a red eye is bait — don't pick it up.")
    t("message.isekai_mahou.mimic_king.bait_bite", "Era falso! O tesouro mordeu você.", "It was fake! The treasure bit you.")
    t("attack.isekai_mahou.mimic_king.bait", "Tesouro Falso", "Fake Treasure")
    t("hud.isekai_mahou.false_hydra.song", "O canto apaga sua memória — FIRA O CORPO!", "The song is erasing your memory — HIT THE BODY!")

    lang = os.path.join(ROOT, "src/main/lang/bosses_g")
    write_json(os.path.join(lang, "pt_br.json"), {k: v[0] for k, v in sorted(L.items())})
    write_json(os.path.join(lang, "en_us.json"), {k: v[1] for k, v in sorted(L.items())})


def main():
    build_false_hydra()
    build_hydra_head()
    build_mimic_king()
    build_vault_mimic()
    build_manticore()
    spike_texture()
    item_textures()
    item_models()
    write_loot()
    hydra_village()
    hydra_shaft()
    hydra_cavern()
    vault_gate()
    vault_stairs()
    vault_hall()
    manticore_den()
    write_worldgen()
    write_lang()
    print("bosses_g: assets gerados")


if __name__ == "__main__":
    main()
