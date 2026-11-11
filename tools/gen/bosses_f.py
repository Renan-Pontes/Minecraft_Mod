"""
Gerador de assets do modulo bosses_f (4 chefes do Overworld: Hidra do Pantano, Wyvern Rei dos Picos,
Basilisco do Deserto e Colosso das Ruinas): modelos GeckoLib (.geo.json) com box UV pintada por face
(+ glowmasks), animacoes, texturas dos itens de chefe, modelos de item/ovo, loot tables e tags.

Tambem imprime as posicoes de repouso (espaco do modelo Java, em px) das cabecas da Hidra e do nucleo do
Colosso — as caixas de acerto (PartEntity) no Java usam esses numeros.

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_f.py
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
MERGE = os.path.join(ROOT, "src/main/merge/bosses_f")
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
#  Simulacao da pose de repouso (mesma matematica do GeckoLib): pontos do modelo Bedrock -> espaco Java
# =====================================================================================
def _rot(v, ax, ang):
    c, s = math.cos(ang), math.sin(ang)
    x, y, z = v
    if ax == 0:
        return (x, y * c - z * s, y * s + z * c)
    if ax == 1:
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def rest_point(m, bone, p_bedrock):
    """Ponto (px, Bedrock) preso ao osso -> posicao (px) no espaco do modelo Java (x invertido), pose de repouso.
    GeckoLib: pivo e rotacoes X/Y invertidos; ordem Z*Y*X em volta do pivo; filho dentro do pai."""
    bones = {b["name"]: b for b in m.bones}
    p = (-p_bedrock[0], p_bedrock[1], p_bedrock[2])
    name = bone
    while name:
        b = bones[name]
        piv = (-b["pivot"][0], b["pivot"][1], b["pivot"][2])
        r = b.get("rotation", [0, 0, 0])
        v = tuple(p[i] - piv[i] for i in range(3))
        v = _rot(v, 0, math.radians(-r[0]))
        v = _rot(v, 1, math.radians(-r[1]))
        v = _rot(v, 2, math.radians(r[2]))
        p = tuple(v[i] + piv[i] for i in range(3))
        name = b.get("parent")
    return p


# ---------------- decoracoes extras ----------------
def scales(dark, light, seed, faces=("front", "back", "left", "right", "top")):
    """Escamas: fileiras desencontradas de meias-luas escuras com brilho claro em cima."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            for yy in range(f.h):
                off = 2 if (yy // 2) % 2 else 0
                for xx in range(f.w):
                    k = (xx + off) % 4
                    if yy % 2 == 1 and k in (0, 3):
                        f.set(xx, yy, H(dark))
                    elif yy % 2 == 0 and k == 1 and rnd.random() < 0.7:
                        f.set(xx, yy, H(light))
    return d


def bricks(mortar, light, seed, faces=SIDES + ("top",), bh=4, bw=6):
    """Alvenaria antiga: fiadas de tijolos com argamassa e lascas."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            for yy in range(f.h):
                row = yy // bh
                if yy % bh == bh - 1:
                    f.row(yy, H(mortar))
                    continue
                off = (bw // 2) if row % 2 else 0
                for xx in range(f.w):
                    if (xx + off) % bw == bw - 1:
                        f.set(xx, yy, H(mortar))
                    elif yy % bh == 0 and rnd.random() < 0.35:
                        f.set(xx, yy, H(light))
    return d


def moss(color, dark, density, seed, faces=("top", "front", "left", "right", "back"), top_rows=3):
    """Musgo: cobre o topo e escorre pelas laterais."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            for xx in range(f.w):
                if n == "top":
                    for yy in range(f.h):
                        if rnd.random() < density:
                            f.set(xx, yy, H(color) if rnd.random() < 0.7 else H(dark))
                else:
                    depth = int(rnd.random() * top_rows * (1.5 if rnd.random() < density else 0.4))
                    for yy in range(min(depth, f.h)):
                        f.set(xx, yy, H(color) if yy < depth - 1 else H(dark))
    return d


RUNE_PATS = (((0, 0), (1, 0), (2, 0), (1, 1), (1, 2)), ((0, 0), (0, 1), (0, 2), (1, 1), (2, 2)),
             ((0, 2), (1, 1), (2, 0), (0, 0), (2, 2)), ((1, 0), (0, 1), (2, 1), (1, 2)), ((0, 0), (2, 0), (1, 1), (0, 2), (2, 2)))


def runes(color, seed, faces=("front",), n=3):
    """Runas brilhantes (glowmask) em blocos de 3x3."""
    def d(fs):
        rnd = random.Random(seed)
        for name in faces:
            f = fs[name]
            if f.w < 4 or f.h < 4:
                continue
            for _ in range(n):
                x0 = rnd.randint(0, f.w - 3)
                y0 = rnd.randint(0, f.h - 3)
                for (dx, dy) in rnd.choice(RUNE_PATS):
                    f.set(x0 + dx, y0 + dy, H(color), True)
    return d


def teeth(color, faces=("front", "left", "right"), row=-1):
    def d(fs):
        for n in faces:
            f = fs[n]
            yy = f.h - 1 if row < 0 else row
            for xx in range(0, f.w, 2):
                f.set(xx, yy, H(color))
    return d


def fill_face(name, color, g=False):
    def d(fs):
        fs[name].rect(0, 0, fs[name].w - 1, fs[name].h - 1, H(color), g)
    return d


def stripes(color, rows, faces=("front",)):
    def d(fs):
        for n in faces:
            for r in rows:
                if r < fs[n].h:
                    fs[n].row(r, H(color))
    return d


# =====================================================================================
#  HIDRA DO PANTANO
# =====================================================================================
HYDRA_SLOTS = 5
HYDRA_BASE_X = (14, 7, 0, -7, -14)
HYDRA_FAN = (-30, -15, 0, 15, 30)


def build_hydra():
    m = Model("swamp_hydra")
    sc, sc_d, sc_l = "#3E5B2E", "#243619", "#6E8A44"
    belly, belly_d = "#9AA65E", "#6F7A3E"
    bone_c, eye, venom = "#D8D0A0", "#E8F04A", "#9BE04A"
    mouth, flesh, flesh_l, bud = "#5E1A24", "#8E2E2E", "#C2474A", "#E07A7A"
    char, ember = "#1C1612", "#FF7A1F"

    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 18, 0), "root")

    def torso(fs):
        scales(sc_d, sc_l, 11)(fs)
        fs["bottom"].fill(H(belly))
        speckle("#4E3A22", 0.03, 12, faces=("top",))(fs)
    m.cube("body", (-18, 14, -20), (36, 22, 40), P(sc, deco=torso))
    m.cube("body", (-14, 12, -17), (28, 4, 34), P(belly, deco=chain(band([0], belly_d), speckle(belly_d, 0.1, 13))))
    # crista de espinhos de osso nas costas
    for k, z in enumerate(range(-16, 20, 6)):
        m.cube("body", (-1.5, 36, z), (3, 4 + (k % 2) * 2, 3), P(bone_c, faces={"top": "#F4ECC4"}, deco=speckle("#9C9466", 0.2, 20 + k)))
    # ombros grossos onde nascem os pescocos
    m.cube("body", (-17, 28, -24), (34, 8, 8), P(sc, deco=scales(sc_d, sc_l, 14)))
    # pernas (Bedrock +X = esquerda da criatura)
    for name, x, z in (("leg_fl", 14, -12), ("leg_fr", -14, -12), ("leg_bl", 14, 13), ("leg_br", -14, 13)):
        m.bone(name, (x, 18, z), "body")
        m.cube(name, (x - 5, 2, z - 5), (10, 16, 10), P(sc, deco=chain(scales(sc_d, sc_l, 30 + x + z), band([-1, -2], sc_d))))
        m.cube(name, (x - 6, 0, z - 7), (12, 3, 12), P(sc_d, deco=speckle(belly_d, 0.15, 31 + z)))
        for j, dx in enumerate((-4, 0, 4)):
            m.cube(name, (x + dx - 1, 0, z - 9), (2, 2, 3), P(bone_c, deco=speckle("#A89E70", 0.3, 32 + j)))
    # cauda
    m.bone("tail1", (0, 26, 20), "body")
    m.cube("tail1", (-9, 18, 20), (18, 14, 20), P(sc, deco=chain(scales(sc_d, sc_l, 40), fill_face("bottom", belly))))
    m.bone("tail2", (0, 25, 40), "tail1")
    m.cube("tail2", (-6, 20, 40), (12, 10, 18), P(sc, deco=chain(scales(sc_d, sc_l, 41), fill_face("bottom", belly))))
    m.cube("tail2", (-1, 30, 44), (2, 3, 2), P(bone_c))
    m.cube("tail2", (-1, 30, 51), (2, 3, 2), P(bone_c))
    m.bone("tail3", (0, 24, 58), "tail2")
    m.cube("tail3", (-3.5, 21, 58), (7, 6, 16), P(sc, deco=scales(sc_d, sc_l, 42)))
    m.cube("tail3", (-2.5, 22, 74), (5, 4, 6), P(sc_d, deco=speckle(venom, 0.2, 43, g=True)))
    m.cube("tail3", (-0.5, 27, 62), (1, 3, 8), P(bone_c))

    # pescocos/cabecas por encaixe (5); os brotos e tocos ficam no corpo
    for i in range(HYDRA_SLOTS):
        x, fan = HYDRA_BASE_X[i], HYDRA_FAN[i]
        n = "neck_%d" % i
        m.bone(n, (x, 32, -18), "body", rotation=[28, fan, 0])
        m.cube(n, (x - 4.5, 30, -22.5), (9, 22, 9), P(sc, deco=chain(scales(sc_d, sc_l, 50 + i), fill_face("front", belly),
                                                                       stripes(belly_d, [0, 4, 8, 12, 16, 20]))))
        for y in (36, 44):
            m.cube(n, (x - 1, y, -14.5), (2, 3, 2), P(bone_c))
        nu = "neck_%d_upper" % i
        m.bone(nu, (x, 50, -18), n, rotation=[20, 0, 0])
        m.cube(nu, (x - 4, 49, -22), (8, 18, 8), P(sc, deco=chain(scales(sc_d, sc_l, 60 + i), fill_face("front", belly),
                                                                    stripes(belly_d, [1, 5, 9, 13]))))
        h = "head_%d" % i
        m.bone(h, (x, 66, -18), nu, rotation=[-48, 0, 0])

        def skull(fs, i=i):
            scales(sc_d, sc_l, 70 + i, faces=("top", "back"))(fs)
            r, l, t = fs["right"], fs["left"], fs["top"]
            r.rect(r.w - 5, 2, r.w - 4, 3, H(eye), True)
            l.rect(3, 2, 4, 3, H(eye), True)
            r.set(r.w - 5, 2, H("#1A1A08"), True)
            l.set(4, 2, H("#1A1A08"), True)
            t.rect(1, t.h - 5, 2, t.h - 4, H(eye), True)
            t.rect(t.w - 3, t.h - 5, t.w - 2, t.h - 4, H(eye), True)
            fs["bottom"].fill(H(mouth))
            teeth("#F2EDD0")(fs)
        m.cube(h, (x - 6, 63, -32), (12, 9, 16), P(sc, deco=skull))

        def snout(fs):
            fs["bottom"].fill(H(mouth))
            teeth("#F2EDD0")(fs)
            fs["top"].set(2, 1, H("#101008"))
            fs["top"].set(5, 1, H("#101008"))
        m.cube(h, (x - 4, 63, -38), (8, 6, 6), P(sc, deco=snout))
        for sx in (-1, 1):
            m.cube(h, (x + sx * 4 - 1, 71, -22), (2, 2, 8), P(bone_c, faces={"back": "#F4ECC4"}), rotation=[25, sx * 15, 0],
                   pivot=[x + sx * 4, 72, -18])
        m.cube(h, (x - 0.5, 72, -30), (1, 2, 10), P(bone_c))
        j = "jaw_%d" % i
        m.bone(j, (x, 63, -20), h)
        m.cube(j, (x - 5, 60, -37), (10, 3, 17), P(belly, deco=chain(fill_face("top", mouth), teeth("#F2EDD0", row=0),
                                                                      speckle(venom, 0.08, 80 + i, faces=("top",), g=True))))
        m.cube(j, (x - 1, 59, -37), (2, 1, 2), P(venom, deco=glow_faces(venom)))
        # toco aberto (cortada, regenerando) e toco cauterizado
        s = "stump_%d" % i
        m.bone(s, (x, 32, -18), "body", rotation=[20, fan, 0])
        m.cube(s, (x - 5, 30, -23), (10, 8, 10), P(sc, deco=chain(scales(sc_d, sc_l, 90 + i, faces=SIDES), fill_face("top", flesh),
                                                                    speckle(flesh_l, 0.4, 91 + i, faces=("top",)))))
        m.cube(s, (x - 3, 38, -21), (6, 4, 6), P(bud, deco=chain(speckle(flesh_l, 0.4, 92 + i), speckle("#FFD0D0", 0.1, 93 + i, g=True))))
        c = "seared_%d" % i
        m.bone(c, (x, 32, -18), "body", rotation=[20, fan, 0])
        m.cube(c, (x - 5, 30, -23), (10, 7, 10), P(char, deco=chain(speckle("#3A2E26", 0.3, 95 + i),
                                                                      cracks(ember, 96 + i, 2, faces=("front", "left", "right", "top")),
                                                                      speckle(ember, 0.25, 97 + i, faces=("top",), g=True))))
    m.save(bounds=(9, 7), seed=17)

    print("HIDRA - centro das cabecas (px, espaco Java, repouso):")
    for i in range(HYDRA_SLOTS):
        p = rest_point(m, "head_%d" % i, (HYDRA_BASE_X[i], 67.5, -29))
        print("  slot %d: %.2f, %.2f, %.2f" % (i, p[0], p[1], p[2]))

    a = {}
    idle = {
        "body": {"position": cyc(3.0, lambda p: [0, 0.5 * S(p), 0])},
        "tail1": {"rotation": cyc(3.0, lambda p: [0, 6 * S(p), 0])},
        "tail2": {"rotation": cyc(3.0, lambda p: [0, 8 * S(p - 0.15), 0])},
        "tail3": {"rotation": cyc(3.0, lambda p: [0, 10 * S(p - 0.3), 0])},
    }
    for i in range(HYDRA_SLOTS):
        ph = i * 0.21
        idle["neck_%d" % i] = {"rotation": cyc(3.0, lambda p, ph=ph: [4 * S(p + ph), 5 * S(p + ph + 0.1), 2 * S(p + ph)])}
    a["idle"] = anim(3.0, idle, loop=True)
    walk = {
        "leg_fl": {"rotation": cyc(1.6, lambda p: [22 * S(p), 0, 0])},
        "leg_br": {"rotation": cyc(1.6, lambda p: [22 * S(p), 0, 0])},
        "leg_fr": {"rotation": cyc(1.6, lambda p: [-22 * S(p), 0, 0])},
        "leg_bl": {"rotation": cyc(1.6, lambda p: [-22 * S(p), 0, 0])},
        "body": {"position": cyc(1.6, lambda p: [0, 1.2 * abs(S(p)), 0]), "rotation": cyc(1.6, lambda p: [0, 0, 3 * S(p)])},
        "tail1": {"rotation": cyc(1.6, lambda p: [0, 10 * S(p), 0])},
        "tail2": {"rotation": cyc(1.6, lambda p: [0, 12 * S(p - 0.2), 0])},
        "tail3": {"rotation": cyc(1.6, lambda p: [0, 14 * S(p - 0.4), 0])},
    }
    for i in range(HYDRA_SLOTS):
        ph = i * 0.18
        walk["neck_%d" % i] = {"rotation": cyc(1.6, lambda p, ph=ph: [6 * S(p * 2 + ph), 6 * S(p + ph), 0])}
    a["walk"] = anim(1.6, walk, loop=True)
    intro = {"body": {"position": keys([(0, [0, -6, 0]), (1.0, [0, -2, 0]), (2.0, [0, 1, 0]), (3.0, [0, 0, 0])])}}
    for i in range(HYDRA_SLOTS):
        intro["neck_%d" % i] = {"rotation": keys([(0, [70, 0, 0]), (0.8 + 0.1 * i, [55, 0, 0]), (1.8, [-18, (i - 2) * 6, 0]),
                                                   (2.6, [-20, (i - 2) * 7, 0]), (3.0, [0, 0, 0])])}
        intro["jaw_%d" % i] = {"rotation": keys([(0, [0, 0, 0]), (1.7, [0, 0, 0]), (2.0, [40, 0, 0]), (2.6, [42, 0, 0]), (3.0, [0, 0, 0])])}
    a["intro"] = anim(3.0, intro)
    death = {"root": {"rotation": keys([(0, [0, 0, 0]), (0.8, [0, 0, 6]), (2.0, [0, 0, 28]), (2.5, [0, 0, 30])]),
                      "position": keys([(0, [0, 0, 0]), (2.0, [0, -3, 0]), (2.5, [0, -4, 0])])},
             "tail1": {"rotation": keys([(0, [0, 0, 0]), (2.5, [10, 25, 0])])}}
    for i in range(HYDRA_SLOTS):
        death["neck_%d" % i] = {"rotation": keys([(0, [0, 0, 0]), (0.4 + 0.15 * i, [-25, 0, 0]), (1.6 + 0.1 * i, [70, (i - 2) * 12, 0]),
                                                   (2.5, [85, (i - 2) * 14, 0])])}
        death["jaw_%d" % i] = {"rotation": keys([(0, [0, 0, 0]), (0.6, [45, 0, 0]), (2.5, [20, 0, 0])])}
    a["death"] = anim(2.5, death)
    roar = {"body": {"position": cyc(2.0, lambda p: [0.4 * S(p * 12), 0, 0], steps=24)}}
    for i in range(HYDRA_SLOTS):
        roar["neck_%d" % i] = {"rotation": keys([(0, [0, 0, 0]), (0.4, [-22, (i - 2) * 8, 0]), (1.6, [-24, (i - 2) * 9, 0]), (2.0, [0, 0, 0])])}
        roar["jaw_%d" % i] = {"rotation": keys([(0, [0, 0, 0]), (0.4, [45, 0, 0]), (1.6, [45, 0, 0]), (2.0, [0, 0, 0])])}
    a["roar"] = anim(2.0, roar)
    # varrida de cauda: arma 0-0.5 s, golpe em 0.8 s (16 ticks = aquecimento)
    a["tail"] = anim(1.1, {
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, -35, 0]), (0.8, [0, 55, 0]), (0.95, [0, 50, 0]), (1.1, [0, 0, 0])])},
        "tail2": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, -25, 0]), (0.8, [0, 40, 0]), (1.1, [0, 0, 0])])},
        "tail3": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, -20, 0]), (0.8, [0, 35, 0]), (1.1, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, 8, 0]), (0.8, [0, -12, 0]), (1.1, [0, 0, 0])])},
    })
    # investida: abaixa (1 s = 20 ticks de preparo) e corre
    run = [(0, [0, 0, 0]), (0.8, [-20, 0, 0])] + [(1.0 + 0.2 * k, [30 * (1 if k % 2 == 0 else -1), 0, 0]) for k in range(4)] + [(1.9, [0, 0, 0])]
    runb = [(t, [-v[0], 0, 0]) for t, v in run]
    a["charge"] = anim(1.9, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.8, [8, 0, 0]), (1.7, [10, 0, 0]), (1.9, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.8, [0, -3, 0]), (1.7, [0, -2, 0]), (1.9, [0, 0, 0])])},
        "leg_fl": {"rotation": keys(run)}, "leg_br": {"rotation": keys(run)},
        "leg_fr": {"rotation": keys(runb)}, "leg_bl": {"rotation": keys(runb)},
    })
    # mergulho no lodo / emboscada (estados controlados pelo Java)
    a["submerge"] = anim(1.5, {"root": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 3, 0]), (1.5, [0, -60, 0])])}})
    a["hidden"] = anim(1.0, {"root": {"position": keys([(0, [0, -64, 0]), (1.0, [0, -64, 0])])}}, loop=True)
    a["emerge"] = anim(1.0, {"root": {"position": keys([(0, [0, -64, 0]), (0.35, [0, 6, 0]), (0.6, [0, 2, 0]), (1.0, [0, 0, 0])])}})
    for i in range(HYDRA_SLOTS):
        nu, h, j = "neck_%d_upper" % i, "head_%d" % i, "jaw_%d" % i
        # mordida: recua ate 0.45 s, avanca no 0.7 s (14 ticks = aquecimento da marca)
        a["bite_%d" % i] = anim(1.0, {
            nu: {"rotation": keys([(0, [0, 0, 0]), (0.45, [-28, 0, 0]), (0.7, [48, 0, 0]), (0.8, [44, 0, 0]), (1.0, [0, 0, 0])])},
            h: {"rotation": keys([(0, [0, 0, 0]), (0.45, [-12, 0, 0]), (0.7, [22, 0, 0]), (1.0, [0, 0, 0])])},
            j: {"rotation": keys([(0, [0, 0, 0]), (0.45, [38, 0, 0]), (0.66, [40, 0, 0]), (0.72, [0, 0, 0]), (1.0, [0, 0, 0])])},
        })
        # sopro pestilento: recua e abre a boca no 1.3 s (26 ticks)
        a["breath_%d" % i] = anim(1.8, {
            nu: {"rotation": keys([(0, [0, 0, 0]), (0.8, [-24, 0, 0]), (1.3, [26, 0, 0]), (1.6, [24, 0, 0]), (1.8, [0, 0, 0])])},
            j: {"rotation": keys([(0, [0, 0, 0]), (1.1, [10, 0, 0]), (1.3, [45, 0, 0]), (1.6, [45, 0, 0]), (1.8, [0, 0, 0])])},
        })
        a["spit_%d" % i] = anim(1.0, {
            nu: {"rotation": keys([(0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.55, [20, 0, 0]), (1.0, [0, 0, 0])])},
            h: {"rotation": keys([(0, [0, 0, 0]), (0.4, [-20, 0, 0]), (0.55, [10, 0, 0]), (1.0, [0, 0, 0])])},
            j: {"rotation": keys([(0, [0, 0, 0]), (0.4, [30, 0, 0]), (0.6, [30, 0, 0]), (0.8, [0, 0, 0])])},
        })
        a["regrow_%d" % i] = anim(1.0, {
            nu: {"rotation": keys([(0, [60, 0, 0]), (0.6, [-10, 0, 0]), (1.0, [0, 0, 0])])},
            j: {"rotation": keys([(0, [0, 0, 0]), (0.5, [40, 0, 0]), (1.0, [0, 0, 0])])},
        })
    save_anims("swamp_hydra", a)


# =====================================================================================
#  WYVERN REI DOS PICOS
# =====================================================================================
def build_wyvern():
    m = Model("wyvern_king")
    sc, sc_d, sc_l = "#2E4A6E", "#1A2C44", "#5C82B0"
    belly, belly_d = "#B8C4D0", "#8894A4"
    memb, memb_d, memb_l = "#3A5C88", "#24405F", "#7FA6D4"
    gold, gold_d, bolt, bolt_w = "#D9B648", "#9C7A22", "#7FDBFF", "#E8FBFF"
    horn = "#E8E2CC"

    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 24, 0), "root")

    def torso(fs):
        scales(sc_d, sc_l, 201)(fs)
        fs["bottom"].fill(H(belly))
        stripes(belly_d, [0, 3, 6, 9, 12, 15, 18, 21, 24], faces=("bottom",))(fs)
        cracks(bolt, 202, 2, faces=("left", "right"))(fs)
    m.cube("body", (-8, 18, -12), (16, 14, 26), P(sc, deco=torso))
    m.cube("body", (-6, 17, -10), (12, 2, 20), P(belly, deco=stripes(belly_d, [1], faces=SIDES)))
    for k, z in enumerate(range(-10, 14, 5)):
        m.cube("body", (-1, 32, z), (2, 3 + (k % 2), 3), P(gold, faces={"top": "#F4E08A"}, deco=speckle(gold_d, 0.2, 210 + k)))
    # pernas traseiras com garras
    for name, x in (("leg_l", 6), ("leg_r", -6)):
        m.bone(name, (x, 20, 6), "body")
        m.cube(name, (x - 3.5, 10, 2), (7, 12, 8), P(sc, deco=scales(sc_d, sc_l, 220 + x)))
        m.bone(name + "_foot", (x, 10, 6), name)
        m.cube(name + "_foot", (x - 2.5, 2, 3), (5, 9, 5), P(sc_d, deco=speckle(sc, 0.2, 222 + x)))
        m.cube(name + "_foot", (x - 3.5, 0, 0), (7, 2, 8), P(sc_d))
        for j, dx in enumerate((-3, 0, 3)):
            m.cube(name + "_foot", (x + dx - 0.5, 0, -2), (1, 2, 3), P(horn))
    # pescoco e cabeca coroada
    m.bone("neck", (0, 28, -12), "body", rotation=[-18, 0, 0])
    m.cube("neck", (-4, 25, -27), (8, 8, 16), P(sc, deco=chain(scales(sc_d, sc_l, 230), fill_face("bottom", belly))))
    for z in (-24, -19, -14):
        m.cube("neck", (-0.5, 33, z), (1, 2, 2), P(gold))
    m.bone("head", (0, 29, -26), "neck", rotation=[16, 0, 0])

    def skull(fs):
        scales(sc_d, sc_l, 240, faces=("top", "back"))(fs)
        r, l = fs["right"], fs["left"]
        r.rect(r.w - 4, 2, r.w - 3, 2, H(bolt_w), True)
        r.set(r.w - 5, 2, H(bolt), True)
        l.rect(2, 2, 3, 2, H(bolt_w), True)
        l.set(4, 2, H(bolt), True)
        fs["bottom"].fill(H(belly_d))
    m.cube("head", (-5, 26, -38), (10, 8, 12), P(sc, deco=skull))
    m.cube("head", (-4, 26, -45), (8, 5, 7), P(sc, deco=chain(teeth(horn), fill_face("bottom", belly_d),
                                                               lambda fs: fs["top"].set(1, 1, H("#0A0F18")) or fs["top"].set(6, 1, H("#0A0F18")))))
    # coroa: chifres grandes para tras e pontas douradas
    for sx in (-1, 1):
        m.cube("head", (sx * 3.5 - 1, 31, -30), (2, 2, 12), P(horn, faces={"back": "#FFFFFF"}, deco=speckle("#B8B09A", 0.2, 250 + sx)),
               rotation=[28, sx * -14, 0], pivot=[sx * 3.5, 32, -30])
        m.cube("head", (sx * 5.5 - 0.5, 30, -34), (1, 4, 1), P(gold, deco=glow_faces(gold)), rotation=[0, 0, sx * -20], pivot=[sx * 5.5, 30, -34])
    m.cube("head", (-2.5, 34, -34), (5, 2, 5), P(gold, deco=chain(speckle(gold_d, 0.3, 255), runes(bolt, 256, faces=("top",), n=1))))
    m.cube("head", (-0.5, 36, -33), (1, 3, 1), P(bolt, deco=glow_faces(bolt, core=bolt_w)))
    m.bone("jaw", (0, 27, -30), "head")
    m.cube("jaw", (-4, 23, -44), (8, 3, 14), P(belly, deco=chain(fill_face("top", "#4A1A24"), teeth(horn, row=0))))
    # asas: braco + membrana (+X Bedrock = esquerda)
    for side, sx in (("wing_l", 1), ("wing_r", -1)):
        m.bone(side, (sx * 8, 31, -6), "body")
        x0 = 8 if sx > 0 else -30
        m.cube(side, (x0, 30, -7), (22, 3, 3), P(sc, deco=chain(scales(sc_d, sc_l, 260 + sx), cracks(bolt, 261 + sx, 1, faces=("top",)))))

        def membrane(seed):
            def d(fs):
                rnd = random.Random(seed)
                for n, f in fs.items():
                    for yy in range(f.h):
                        for xx in range(f.w):
                            r = rnd.random()
                            if (xx % 7) == 3:
                                f.set(xx, yy, H(memb_d))
                            elif r < 0.07:
                                f.set(xx, yy, H(memb_l))
                    if n in ("top", "bottom"):
                        for xx in range(f.w):
                            if (xx * 3 + seed) % 5 == 0:
                                f.set(xx, f.h - 1, CLEAR)
                        for k in range(3):
                            yy = rnd.randint(2, max(2, f.h - 3))
                            xx = rnd.randint(1, max(1, f.w - 2))
                            f.set(xx, yy, H(bolt), True)
            return d
        m.cube(side, (x0, 30.5, -4), (22, 1, 20), P(memb, deco=membrane(270 + sx), noise=0.04))
        tip = side + "_tip"
        m.bone(tip, (sx * 30, 31, -6), side)
        x1 = 30 if sx > 0 else -50
        m.cube(tip, (x1, 30.25, -6.5), (20, 2, 2), P(sc, deco=scales(sc_d, sc_l, 280 + sx)))
        m.cube(tip, (x1, 30.5, -4.5), (20, 1, 16), P(memb, deco=membrane(290 + sx), noise=0.04))
        m.cube(tip, ((x1 + 19) if sx > 0 else (x1 - 1), 30, -8), (2, 3, 3), P(horn, faces={"front": "#FFFFFF"}))
    # cauda com lamina de raio
    m.bone("tail1", (0, 24, 14), "body")
    m.cube("tail1", (-5, 20, 14), (10, 8, 18), P(sc, deco=chain(scales(sc_d, sc_l, 300), fill_face("bottom", belly))))
    m.bone("tail2", (0, 24, 32), "tail1")
    m.cube("tail2", (-3, 21, 32), (6, 6, 18), P(sc, deco=scales(sc_d, sc_l, 301)))
    m.bone("tail3", (0, 24, 50), "tail2")
    m.cube("tail3", (-2, 22, 50), (4, 4, 14), P(sc, deco=scales(sc_d, sc_l, 302)))
    m.cube("tail3", (-0.5, 19, 58), (1, 10, 10), P(bolt, deco=chain(glow_faces(bolt, core=bolt_w), clear_corner_tail())))
    m.save(bounds=(9, 5), seed=23)

    a = {}
    a["idle"] = anim(3.0, {
        "body": {"position": cyc(3.0, lambda p: [0, 0.4 * S(p), 0])},
        "neck": {"rotation": cyc(3.0, lambda p: [3 * S(p), 4 * S(p * 0.5 + 0.1), 0])},
        "head": {"rotation": cyc(3.0, lambda p: [2 * S(p + 0.2), 0, 0])},
        "wing_l": {"rotation": cyc(3.0, lambda p: [0, 55, -60 + 3 * S(p)])},
        "wing_r": {"rotation": cyc(3.0, lambda p: [0, -55, 60 - 3 * S(p)])},
        "wing_l_tip": {"rotation": cyc(3.0, lambda p: [0, 70, 30])},
        "wing_r_tip": {"rotation": cyc(3.0, lambda p: [0, -70, -30])},
        "tail1": {"rotation": cyc(3.0, lambda p: [-6, 8 * S(p), 0])},
        "tail2": {"rotation": cyc(3.0, lambda p: [4, 10 * S(p - 0.15), 0])},
        "tail3": {"rotation": cyc(3.0, lambda p: [4, 12 * S(p - 0.3), 0])},
    }, loop=True)
    a["walk"] = anim(1.4, {
        "leg_l": {"rotation": cyc(1.4, lambda p: [26 * S(p), 0, 0])},
        "leg_r": {"rotation": cyc(1.4, lambda p: [-26 * S(p), 0, 0])},
        "body": {"position": cyc(1.4, lambda p: [0, 1.0 * abs(S(p)), 0]), "rotation": cyc(1.4, lambda p: [4, 0, 3 * S(p)])},
        "wing_l": {"rotation": cyc(1.4, lambda p: [10 * S(p), 50, -55])},
        "wing_r": {"rotation": cyc(1.4, lambda p: [-10 * S(p), -50, 55])},
        "wing_l_tip": {"rotation": cyc(1.4, lambda p: [0, 70, 30])},
        "wing_r_tip": {"rotation": cyc(1.4, lambda p: [0, -70, -30])},
        "neck": {"rotation": cyc(1.4, lambda p: [4 * S(p * 2), 0, 0])},
        "tail1": {"rotation": cyc(1.4, lambda p: [-6, 12 * S(p), 0])},
        "tail2": {"rotation": cyc(1.4, lambda p: [4, 14 * S(p - 0.2), 0])},
    }, loop=True)
    # voo: batida (asa esquerda sobe com z negativo, a direita com z positivo)
    a["fly"] = anim(1.0, {
        "body": {"rotation": cyc(1.0, lambda p: [6 + 3 * S(p), 0, 0]), "position": cyc(1.0, lambda p: [0, -2 * S(p), 0])},
        "wing_l": {"rotation": cyc(1.0, lambda p: [0, 0, -40 * S(p) - 5], steps=12)},
        "wing_r": {"rotation": cyc(1.0, lambda p: [0, 0, 40 * S(p) + 5], steps=12)},
        "wing_l_tip": {"rotation": cyc(1.0, lambda p: [0, 0, -25 * S(p - 0.12)], steps=12)},
        "wing_r_tip": {"rotation": cyc(1.0, lambda p: [0, 0, 25 * S(p - 0.12)], steps=12)},
        "leg_l": {"rotation": cyc(1.0, lambda p: [50, 0, 0])},
        "leg_r": {"rotation": cyc(1.0, lambda p: [50, 0, 0])},
        "neck": {"rotation": cyc(1.0, lambda p: [-6 + 3 * S(p + 0.25), 0, 0])},
        "tail1": {"rotation": cyc(1.0, lambda p: [-4 + 4 * S(p - 0.2), 0, 0])},
        "tail2": {"rotation": cyc(1.0, lambda p: [4 * S(p - 0.35), 0, 0])},
        "tail3": {"rotation": cyc(1.0, lambda p: [5 * S(p - 0.5), 0, 0])},
    }, loop=True)
    # mergulho: asas recolhidas, corpo apontando para baixo
    a["dive"] = anim(0.6, {
        "body": {"rotation": cyc(0.6, lambda p: [28, 0, 3 * S(p * 2)])},
        "wing_l": {"rotation": cyc(0.6, lambda p: [0, 60, -15 + 4 * S(p * 2)])},
        "wing_r": {"rotation": cyc(0.6, lambda p: [0, -60, 15 - 4 * S(p * 2)])},
        "wing_l_tip": {"rotation": cyc(0.6, lambda p: [0, 80, 0])},
        "wing_r_tip": {"rotation": cyc(0.6, lambda p: [0, -80, 0])},
        "neck": {"rotation": cyc(0.6, lambda p: [12, 0, 0])},
        "leg_l": {"rotation": cyc(0.6, lambda p: [70, 0, 0])},
        "leg_r": {"rotation": cyc(0.6, lambda p: [70, 0, 0])},
        "jaw": {"rotation": cyc(0.6, lambda p: [25, 0, 0])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "body": {"rotation": keys([(0, [10, 0, 0]), (1.2, [-25, 0, 0]), (2.4, [-28, 0, 0]), (3.0, [0, 0, 0])])},
        "wing_l": {"rotation": keys([(0, [0, 55, -60]), (1.0, [0, 0, -10]), (1.4, [0, 0, -70]), (2.4, [0, -10, -75]), (3.0, [0, 55, -60])])},
        "wing_r": {"rotation": keys([(0, [0, -55, 60]), (1.0, [0, 0, 10]), (1.4, [0, 0, 70]), (2.4, [0, 10, 75]), (3.0, [0, -55, 60])])},
        "wing_l_tip": {"rotation": keys([(0, [0, 70, 30]), (1.0, [0, 0, 0]), (2.4, [0, 0, -20]), (3.0, [0, 70, 30])])},
        "wing_r_tip": {"rotation": keys([(0, [0, -70, -30]), (1.0, [0, 0, 0]), (2.4, [0, 0, 20]), (3.0, [0, -70, -30])])},
        "neck": {"rotation": keys([(0, [10, 0, 0]), (1.4, [-30, 0, 0]), (2.4, [-34, 0, 0]), (3.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.4, [40, 0, 0]), (2.4, [42, 0, 0]), (3.0, [0, 0, 0])])},
    })
    a["death"] = anim(2.5, {
        "root": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 20]), (2.0, [0, 0, 80]), (2.5, [0, 0, 88])]),
                 "position": keys([(0, [0, 0, 0]), (2.0, [0, -2, 0]), (2.5, [0, -3, 0])])},
        "wing_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, -50]), (2.5, [0, 20, 10])])},
        "wing_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 50]), (2.5, [0, -30, 20])])},
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-30, 0, 0]), (2.5, [30, 20, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.8, [40, 0, 0]), (2.5, [15, 0, 0])])},
    })
    a["roar"] = anim(2.0, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-28, 0, 0]), (1.6, [-30, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.4, [42, 0, 0]), (1.6, [42, 0, 0]), (2.0, [0, 0, 0])])},
        "body": {"position": cyc(2.0, lambda p: [0.3 * S(p * 12), 0, 0], steps=24)},
    })
    # mordida (8 ticks = 0.4 s)
    a["bite"] = anim(0.8, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-18, 0, 0]), (0.4, [22, 0, 0]), (0.8, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.4, [10, 0, 0]), (0.8, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.25, [40, 0, 0]), (0.38, [40, 0, 0]), (0.42, [0, 0, 0]), (0.8, [0, 0, 0])])},
    })
    # cauda (14 ticks = 0.7 s)
    a["tail"] = anim(1.0, {
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -40, 0]), (0.7, [0, 60, 0]), (1.0, [0, 0, 0])])},
        "tail2": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -25, 0]), (0.7, [0, 40, 0]), (1.0, [0, 0, 0])])},
        "tail3": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -20, 0]), (0.7, [0, 40, 0]), (1.0, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, 12, 0]), (0.7, [0, -14, 0]), (1.0, [0, 0, 0])])},
    })
    # rajada: asas abrem bem e batem forte no 0.9 s (18 ticks)
    a["gust"] = anim(1.3, {
        "wing_l": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, -20, -75]), (0.9, [0, 25, 35]), (1.1, [0, 20, 30]), (1.3, [0, 0, 0])])},
        "wing_r": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, 20, 75]), (0.9, [0, -25, -35]), (1.1, [0, -20, -30]), (1.3, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-18, 0, 0]), (0.9, [10, 0, 0]), (1.3, [0, 0, 0])])},
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-12, 0, 0]), (0.9, [12, 0, 0]), (1.3, [0, 0, 0])])},
    })
    # sopro de trovao: carrega com a cabeca para tras e solta no 1.3 s (26 ticks)
    a["breath"] = anim(2.0, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-30, 0, 0]), (1.3, [18, 0, 0]), (1.8, [16, 0, 0]), (2.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-10, 0, 0]), (1.3, [12, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.0, [10, 0, 0]), (1.3, [48, 0, 0]), (1.8, [48, 0, 0]), (2.0, [0, 0, 0])])},
    })
    # chamado dos raios: cabeca para o ceu, asas abertas
    a["lightning"] = anim(2.0, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-45, 0, 0]), (1.6, [-48, 0, 0]), (2.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (1.6, [-25, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.5, [40, 0, 0]), (1.6, [40, 0, 0]), (2.0, [0, 0, 0])])},
        "wing_l": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, 0, -70]), (1.6, [0, 0, -72]), (2.0, [0, 0, 0])])},
        "wing_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [0, 0, 70]), (1.6, [0, 0, 72]), (2.0, [0, 0, 0])])},
    })
    save_anims("wyvern_king", a)


def clear_corner_tail():
    """Lamina da cauda em forma de raio: recorta dois cantos da placa."""
    def d(fs):
        for n in ("left", "right"):
            f = fs[n]
            for yy in range(f.h):
                for xx in range(f.w):
                    if (xx + yy) < 4 or (f.w - 1 - xx + f.h - 1 - yy) < 3:
                        f.set(xx, yy, CLEAR)
    return d


# =====================================================================================
#  BASILISCO DO DESERTO
# =====================================================================================
def build_basilisk():
    m = Model("desert_basilisk")
    sc, sc_d, sc_l = "#B88A4A", "#7C5A2C", "#E2C084"
    belly, belly_d = "#E8D8A8", "#BCA874"
    gold, gold_d, eye, eye_g = "#E8C04A", "#A47E22", "#D8FF5A", "#F8FFC0"
    stone, stone_d = "#8E8A7E", "#5E5A50"

    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 10, 0), "root")

    def torso(fs):
        scales(sc_d, sc_l, 401)(fs)
        fs["bottom"].fill(H(belly))
        stripes(belly_d, list(range(0, 40, 3)), faces=("bottom",))(fs)
        # faixas de "pedra" (escamas petrificadas) nas costas
        speckle(stone, 0.06, 402, faces=("top",))(fs)
    m.cube("body", (-11, 6, -18), (22, 13, 36), P(sc, deco=torso))
    for k, z in enumerate(range(-16, 18, 5)):
        m.cube("body", (-1, 19, z), (2, 4 + (k % 2) * 2, 3), P(stone, faces={"top": "#C8C2B0"}, deco=speckle(stone_d, 0.3, 410 + k)))
        for sx in (-1, 1):
            m.cube("body", (sx * 7 - 1, 18, z + 1), (2, 2, 2), P(stone_d))
    # 6 patas curtas e abertas
    for k, z in enumerate((-12, 0, 12)):
        for side, sx in (("l", 1), ("r", -1)):
            n = "leg_%s%d" % (side, k)
            m.bone(n, (sx * 11, 9, z), "body", rotation=[0, 0, sx * -20])
            x0 = 10 if sx > 0 else -16
            m.cube(n, (x0, 2, z - 3), (6, 8, 6), P(sc, deco=scales(sc_d, sc_l, 420 + k * 2 + sx)))
            m.cube(n, (x0 + (1 if sx > 0 else -1), 0, z - 4), (6, 2, 7), P(sc_d))
            for j, dz in enumerate((-5, -3)):
                m.cube(n, (x0 + 1 + j * 3 if sx > 0 else x0 + 1 + j * 3, 0, z + dz), (1, 1, 2), P("#F0E8D0"))
    # pescoco + cabeca coroada (olhos enormes)
    m.bone("neck", (0, 14, -18), "body")
    m.cube("neck", (-7, 8, -31), (14, 11, 14), P(sc, deco=chain(scales(sc_d, sc_l, 430), fill_face("bottom", belly))))
    for sx in (-1, 1):
        m.cube("neck", (sx * 7 - 1 + (1 if sx > 0 else -1), 10, -29), (2, 8, 10), P(gold, deco=chain(speckle(gold_d, 0.25, 431 + sx))),
               rotation=[0, sx * -12, 0], pivot=[sx * 7, 14, -24])
    m.bone("head", (0, 14, -30), "neck")

    def skull(fs):
        scales(sc_d, sc_l, 440, faces=("top", "back"))(fs)
        r, l = fs["right"], fs["left"]
        # olho grande nas laterais, perto da frente
        for f, fx in ((r, r.w - 7), (l, 2)):
            f.rect(fx, 2, fx + 4, 5, H("#20240A"))
            f.rect(fx + 1, 2, fx + 3, 4, H(eye), True)
            f.rect(fx + 2, 2, fx + 2, 5, H("#101204"), True)
            f.set(fx + 1, 2, H(eye_g), True)
        fs["bottom"].fill(H(belly_d))
    m.cube("head", (-8, 9, -46), (16, 10, 16), P(sc, deco=skull))
    m.cube("head", (-6, 9, -52), (12, 7, 6), P(sc, deco=chain(teeth("#F4EEDC"), fill_face("bottom", "#5A1A10"),
                                                               lambda fs: fs["top"].set(2, 1, H("#201408")) or fs["top"].set(9, 1, H("#201408")))))
    # coroa de pedra e ouro (o "rei das serpentes")
    for k, (x, h_, rz) in enumerate(((-6, 6, 20), (-3, 8, 8), (0, 10, 0), (3, 8, -8), (6, 6, -20))):
        m.cube("head", (x - 1, 19, -40), (2, h_, 2), P(gold, faces={"top": eye}, deco=chain(speckle(gold_d, 0.2, 450 + k),
                                                                                            lambda fs: fs["top"].fill(H(eye)))),
               rotation=[-15, 0, rz], pivot=[x, 19, -39])
    m.cube("head", (-7, 18, -42), (14, 2, 6), P(gold, deco=chain(speckle(gold_d, 0.3, 456), runes(eye, 457, faces=("front",), n=1))))
    # brilho dos olhos durante o Olhar (osso que aparece)
    m.bone("eye_glow", (0, 14, -40), "head")
    for sx in (-1, 1):
        m.cube("eye_glow", (sx * 8.2 - (0 if sx > 0 else 1) - 0.5, 11, -45), (1, 4, 5), P(eye_g, deco=glow_faces(eye, core=eye_g)))
    m.bone("jaw", (0, 10, -34), "head")
    m.cube("jaw", (-7, 6, -51), (14, 3, 17), P(belly, deco=chain(fill_face("top", "#5A1A10"), teeth("#F4EEDC", row=0))))
    # cauda longa com clava de espinhos
    m.bone("tail1", (0, 12, 18), "body")
    m.cube("tail1", (-8, 6, 18), (16, 11, 20), P(sc, deco=chain(scales(sc_d, sc_l, 460), fill_face("bottom", belly))))
    m.bone("tail2", (0, 11, 38), "tail1")
    m.cube("tail2", (-5.5, 7, 38), (11, 8, 20), P(sc, deco=chain(scales(sc_d, sc_l, 461), fill_face("bottom", belly))))
    m.bone("tail3", (0, 11, 58), "tail2")
    m.cube("tail3", (-3, 8, 58), (6, 5, 18), P(sc, deco=scales(sc_d, sc_l, 462)))
    m.bone("tail4", (0, 10, 76), "tail3")
    m.cube("tail4", (-3, 7, 76), (6, 6, 8), P(stone, deco=chain(speckle(stone_d, 0.3, 463), cracks(eye, 464, 1, faces=("top", "left", "right")))))
    for k, (dx, dy, rz) in enumerate(((-3, 10, 35), (3, 10, -35), (0, 13, 0), (-4, 7, 80), (4, 7, -80))):
        m.cube("tail4", (dx - 0.5, dy, 78), (1, 4, 1), P("#E8E0C8"), rotation=[0, 0, rz], pivot=[dx, dy, 79])
    m.save(bounds=(10, 4), seed=29)

    a = {}
    a["idle"] = anim(3.0, {
        "body": {"position": cyc(3.0, lambda p: [0, 0.3 * S(p), 0])},
        "neck": {"rotation": cyc(3.0, lambda p: [2 * S(p), 6 * S(p * 0.5), 0])},
        "head": {"rotation": cyc(3.0, lambda p: [2 * S(p + 0.3), -3 * S(p * 0.5), 0])},
        "jaw": {"rotation": cyc(3.0, lambda p: [max(0, 6 * S(p * 2)), 0, 0])},
        "tail1": {"rotation": cyc(3.0, lambda p: [0, 8 * S(p), 0])},
        "tail2": {"rotation": cyc(3.0, lambda p: [0, 10 * S(p - 0.15), 0])},
        "tail3": {"rotation": cyc(3.0, lambda p: [0, 12 * S(p - 0.3), 0])},
        "tail4": {"rotation": cyc(3.0, lambda p: [0, 14 * S(p - 0.45), 0])},
    }, loop=True)
    walk = {
        "body": {"rotation": cyc(1.2, lambda p: [0, 6 * S(p), 0])},
        "neck": {"rotation": cyc(1.2, lambda p: [0, -8 * S(p), 0])},
        "tail1": {"rotation": cyc(1.2, lambda p: [0, -12 * S(p - 0.1), 0])},
        "tail2": {"rotation": cyc(1.2, lambda p: [0, 16 * S(p - 0.25), 0])},
        "tail3": {"rotation": cyc(1.2, lambda p: [0, 18 * S(p - 0.4), 0])},
        "tail4": {"rotation": cyc(1.2, lambda p: [0, 20 * S(p - 0.55), 0])},
    }
    for k in range(3):
        for side, sgn in (("l", 1), ("r", -1)):
            ph = 0.5 * ((k + (0 if side == "l" else 1)) % 2)
            walk["leg_%s%d" % (side, k)] = {"rotation": cyc(1.2, lambda p, ph=ph, sgn=sgn: [30 * S(p + ph), 0, sgn * -20 + 10 * sgn * max(0, C(p + ph))])}
    a["walk"] = anim(1.2, walk, loop=True)
    a["intro"] = anim(3.0, {
        "root": {"position": keys([(0, [0, -20, 0]), (1.0, [0, -4, 0]), (1.4, [0, 2, 0]), (3.0, [0, 0, 0])])},
        "neck": {"rotation": keys([(0, [30, 0, 0]), (1.4, [-35, 0, 0]), (2.6, [-38, 0, 0]), (3.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.6, [45, 0, 0]), (2.6, [45, 0, 0]), (3.0, [0, 0, 0])])},
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (1.4, [-20, 30, 0]), (2.6, [-20, -30, 0]), (3.0, [0, 0, 0])])},
    })
    a["death"] = anim(2.5, {
        "root": {"rotation": keys([(0, [0, 0, 0]), (0.8, [0, 0, -10]), (2.0, [0, 0, 70]), (2.5, [0, 0, 80])]),
                 "position": keys([(0, [0, 0, 0]), (2.5, [0, -2, 0])])},
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, 0]), (2.5, [20, 30, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.6, [45, 0, 0]), (2.5, [20, 0, 0])])},
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (2.5, [0, -40, 0])])},
        "tail2": {"rotation": keys([(0, [0, 0, 0]), (2.5, [0, -30, 0])])},
    })
    a["roar"] = anim(2.0, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-30, 0, 0]), (1.6, [-32, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.4, [45, 0, 0]), (1.6, [45, 0, 0]), (2.0, [0, 0, 0])])},
        "body": {"position": cyc(2.0, lambda p: [0.3 * S(p * 12), 0, 0], steps=24)},
    })
    # mordida (10 ticks = 0.5 s)
    a["bite"] = anim(0.9, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.5, [18, 0, 0]), (0.9, [0, 0, 0])])},
        "body": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 0, 3]), (0.5, [0, 0, -4]), (0.9, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.3, [42, 0, 0]), (0.48, [42, 0, 0]), (0.52, [0, 0, 0]), (0.9, [0, 0, 0])])},
    })
    # chicote de cauda (14 ticks = 0.7 s): gira o corpo e varre
    a["tail"] = anim(1.1, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -18, 0]), (0.7, [0, 30, 0]), (1.1, [0, 0, 0])])},
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -30, 0]), (0.7, [0, 45, 0]), (1.1, [0, 0, 0])])},
        "tail2": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -25, 0]), (0.7, [0, 40, 0]), (1.1, [0, 0, 0])])},
        "tail3": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -25, 0]), (0.7, [0, 40, 0]), (1.1, [0, 0, 0])])},
        "tail4": {"rotation": keys([(0, [0, 0, 0]), (0.45, [0, -20, 0]), (0.7, [0, 45, 0]), (1.1, [0, 0, 0])])},
    })
    # olhar petrificante: ergue o corpo e encara (aquecimento 30 ticks = 1.5 s)
    a["gaze"] = anim(2.0, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-22, 0, 0]), (1.5, [-24, 0, 0]), (1.7, [-18, 0, 0]), (2.0, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.5, [0, 4, 0]), (1.7, [0, 4, 0]), (2.0, [0, 0, 0])])},
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.5, [14, 0, 0]), (2.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.4, [12, 0, 0]), (1.5, [6, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.5, [30, 0, 0]), (1.8, [30, 0, 0]), (2.0, [0, 0, 0])])},
    })
    # olhar total (2 s de aviso = 40 ticks): gira a cabeca em volta
    a["gaze_all"] = anim(2.6, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-26, 0, 0]), (2.0, [-26, 0, 0]), (2.6, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.6, [0, 5, 0]), (2.2, [0, 5, 0]), (2.6, [0, 0, 0])])},
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.6, [10, -40, 0]), (1.2, [10, 40, 0]), (1.8, [10, -30, 0]), (2.0, [14, 0, 0]), (2.6, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (1.9, [0, 0, 0]), (2.0, [40, 0, 0]), (2.4, [40, 0, 0]), (2.6, [0, 0, 0])])},
        "tail1": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 40, 0]), (2.0, [0, -40, 0]), (2.6, [0, 0, 0])])},
    })
    # cusparada de areia movedica (a cabeca bate no chao)
    a["quicksand"] = anim(1.2, {
        "neck": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (0.8, [30, 0, 0]), (1.2, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.5, [40, 0, 0]), (0.8, [10, 0, 0]), (1.2, [0, 0, 0])])},
    })
    # escavar / escondido / emergir (estados controlados pelo Java)
    a["burrow"] = anim(1.0, {
        "root": {"position": keys([(0, [0, 0, 0]), (0.2, [0, 3, -2]), (1.0, [0, -40, 6])]),
                 "rotation": keys([(0, [0, 0, 0]), (0.2, [-10, 0, 0]), (1.0, [35, 0, 0])])},
    })
    a["hidden"] = anim(1.0, {"root": {"position": keys([(0, [0, -48, 0]), (1.0, [0, -48, 0])])}}, loop=True)
    a["emerge"] = anim(0.9, {
        "root": {"position": keys([(0, [0, -48, 0]), (0.3, [0, 8, 0]), (0.55, [0, 2, 0]), (0.9, [0, 0, 0])]),
                 "rotation": keys([(0, [-40, 0, 0]), (0.3, [-25, 0, 0]), (0.9, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (0.3, [45, 0, 0]), (0.9, [0, 0, 0])])},
    })
    save_anims("desert_basilisk", a)


# =====================================================================================
#  COLOSSO DAS RUINAS (modelo a meia escala; o renderer dobra)
# =====================================================================================
def build_colossus():
    m = Model("ruin_colossus")
    st, st_d, st_l, mortar = "#8C8A80", "#5A5850", "#B4B2A6", "#46443C"
    mossc, moss_d = "#4E7A2E", "#2F4E1C"
    rune, rune_l, core, core_l, gold = "#3FE0D0", "#C8FFF8", "#3FE0D0", "#F2FFFC", "#D8B24A"
    vine = "#3C6A24"

    m.bone("root", (0, 0, 0))
    for side, sx in (("l", 1), ("r", -1)):
        leg = "leg_" + side
        m.bone(leg, (sx * 7, 24, 0), "root")
        x0 = 2.5 if sx > 0 else -11.5
        m.cube(leg, (x0, 12, -4.5), (9, 12, 9), P(st, deco=chain(bricks(mortar, st_l, 500 + sx), moss(mossc, moss_d, 0.25, 501 + sx))))
        shin = "shin_" + side
        m.bone(shin, (sx * 7, 12, 0), leg)
        x1 = 1.5 if sx > 0 else -12.5
        m.cube(shin, (x1, 3, -5.5), (11, 10, 11), P(st, deco=chain(bricks(mortar, st_l, 502 + sx), runes(rune, 503 + sx, n=1))))
        m.cube(shin, (x1 - 0.5, 0, -7.5), (12, 3, 14), P(st_d, deco=chain(bricks(mortar, st, 504 + sx, faces=SIDES, bh=3, bw=5),
                                                                            moss(mossc, moss_d, 0.4, 505 + sx, faces=("top",)))))
    m.bone("body", (0, 24, 0), "root")
    m.cube("body", (-10, 22, -6), (20, 6, 12), P(st_d, deco=bricks(mortar, st, 510)))

    def torso(fs):
        bricks(mortar, st_l, 511)(fs)
        moss(mossc, moss_d, 0.35, 512, faces=("top", "back", "left", "right"))(fs)
        runes(rune, 513, faces=("back", "left", "right"), n=2)(fs)
        cracks(rune, 514, 2, faces=("back",))(fs)
    m.cube("body", (-14, 28, -9), (28, 22, 18), P(st, deco=torso))
    # nucleo (atras das placas do peito)
    m.bone("core", (0, 39, -9), "body")
    m.cube("core", (-4, 35, -10.5), (8, 8, 2), P(core, deco=glow_faces(core, core=core_l)))
    m.cube("core", (-2, 37, -11.5), (4, 4, 1), P(core_l, deco=glow_faces(core_l)))
    for side, sx in (("l", 1), ("r", -1)):
        plate = "plate_" + side
        m.bone(plate, (sx * 12, 39, -10), "body")
        x0 = 0 if sx > 0 else -12
        m.cube(plate, (x0, 32, -12), (12, 14, 3), P(st_l, deco=chain(bricks(mortar, "#D0CEC2", 520 + sx, faces=("front",), bh=7, bw=12),
                                                                      runes(gold, 521 + sx, faces=("front",), n=1),
                                                                      lambda fs: fs["back"].fill(H(st_d)))))
    # cabeca: bloco com visor brilhante e coroa de coluna partida
    m.bone("head", (0, 50, -2), "body")

    def face(fs):
        bricks(mortar, st_l, 530, faces=("left", "right", "back", "top"))(fs)
        f = fs["front"]
        f.rect(1, 4, f.w - 2, 5, H("#141410"))
        f.rect(2, 4, f.w - 3, 4, H(rune), True)
        f.set(f.w // 2 - 1, 5, H(rune_l), True)
        f.set(f.w // 2, 5, H(rune_l), True)
        f.rect(4, 8, f.w - 5, 8, H(mortar))
        moss(mossc, moss_d, 0.6, 531, faces=("top",))(fs)
    m.cube("head", (-6, 50, -8), (12, 10, 12), P(st, deco=face))
    m.cube("head", (-3, 60, -3), (6, 3, 6), P(st_l, deco=chain(bricks(mortar, "#D0CEC2", 532, bh=3), moss(mossc, moss_d, 0.5, 533))))
    m.cube("head", (-2, 63, -2), (4, 4, 4), P(st_d, deco=cracks(rune, 534, 1, faces=SIDES)))
    # bracos longos (punhos perto do joelho)
    for side, sx in (("l", 1), ("r", -1)):
        arm = "arm_" + side
        m.bone(arm, (sx * 17, 46, 0), "body")
        x0 = 13 if sx > 0 else -25
        m.cube(arm, (x0, 40, -7), (12, 10, 14), P(st, deco=chain(bricks(mortar, st_l, 540 + sx), moss(mossc, moss_d, 0.5, 541 + sx),
                                                                   runes(gold, 542 + sx, faces=("left", "right"), n=1))))
        x1 = 15.5 if sx > 0 else -24.5
        m.cube(arm, (x1, 28, -4), (9, 13, 8), P(st_d, deco=bricks(mortar, st, 543 + sx)))
        fore = "forearm_" + side
        m.bone(fore, (sx * 20, 29, 0), arm)
        x2 = 14.5 if sx > 0 else -25.5
        m.cube(fore, (x2, 13, -5.5), (11, 16, 11), P(st, deco=chain(bricks(mortar, st_l, 544 + sx), cracks(rune, 545 + sx, 1, faces=("front", "left", "right")))))
        x3 = 13.5 if sx > 0 else -26.5
        m.cube(fore, (x3, 4, -6.5), (13, 9, 13), P(st_l, deco=chain(bricks(mortar, "#D0CEC2", 546 + sx, bh=3, bw=4), moss(mossc, moss_d, 0.2, 547 + sx))))
        # trepadeira pendurada
        m.cube(arm, (x0 + (11 if sx > 0 else 0), 30, -2), (1, 12, 1), P(vine, deco=speckle(moss_d, 0.3, 548 + sx)))
    # costas: coluna partida e entulho
    m.cube("body", (-12, 46, 5), (5, 14, 5), P(st_l, deco=chain(bricks(mortar, "#D0CEC2", 550, bh=5, bw=5), moss(mossc, moss_d, 0.4, 551))),
           rotation=[-12, 0, 10], pivot=[-10, 46, 7])
    m.cube("body", (6, 47, 6), (6, 6, 5), P(st_d, deco=moss(mossc, moss_d, 0.6, 552)), rotation=[0, 20, -8], pivot=[9, 47, 8])
    m.save(bounds=(6, 9), seed=31)

    p = rest_point(m, "core", (0, 39, -10.5))
    print("COLOSSO - nucleo (px, espaco Java, repouso): %.2f, %.2f, %.2f" % p)

    a = {}
    a["idle"] = anim(4.0, {
        "body": {"position": cyc(4.0, lambda p: [0, 0.4 * S(p), 0]), "rotation": cyc(4.0, lambda p: [1.5 * S(p), 0, 0])},
        "head": {"rotation": cyc(4.0, lambda p: [2 * S(p + 0.2), 6 * S(p * 0.5), 0])},
        "arm_l": {"rotation": cyc(4.0, lambda p: [2 * S(p), 0, -3])},
        "arm_r": {"rotation": cyc(4.0, lambda p: [-2 * S(p), 0, 3])},
    }, loop=True)
    a["walk"] = anim(2.4, {
        "leg_l": {"rotation": cyc(2.4, lambda p: [20 * S(p), 0, 0])},
        "leg_r": {"rotation": cyc(2.4, lambda p: [-20 * S(p), 0, 0])},
        "shin_l": {"rotation": cyc(2.4, lambda p: [max(0, 18 * S(p + 0.25)), 0, 0])},
        "shin_r": {"rotation": cyc(2.4, lambda p: [max(0, -18 * S(p + 0.25)), 0, 0])},
        "arm_l": {"rotation": cyc(2.4, lambda p: [-14 * S(p), 0, -3])},
        "arm_r": {"rotation": cyc(2.4, lambda p: [14 * S(p), 0, 3])},
        "body": {"position": cyc(2.4, lambda p: [0, -1.0 * abs(S(p)), 0]), "rotation": cyc(2.4, lambda p: [3, 2 * S(p), 2 * S(p)])},
    }, loop=True)
    kneel_pose = {
        "root": {"position": [0, -14, 0]},
        "leg_l": {"rotation": [-70, 0, 0]}, "leg_r": {"rotation": [-70, 0, 0]},
        "shin_l": {"rotation": [80, 0, 0]}, "shin_r": {"rotation": [80, 0, 0]},
        "body": {"rotation": [18, 0, 0]},
        "arm_l": {"rotation": [-30, 0, -10]}, "arm_r": {"rotation": [-30, 0, 10]},
        "forearm_l": {"rotation": [-20, 0, 0]}, "forearm_r": {"rotation": [-20, 0, 0]},
        "head": {"rotation": [-12, 0, 0]},
    }

    def pose_keys(length, pose, t_in=0.0, t_hold=None, wobble=0.0):
        out = {}
        for b, ch in pose.items():
            out[b] = {}
            for k, v in ch.items():
                if t_hold is None:
                    out[b][k] = cyc(length, lambda p, v=v: [v[0] + wobble * S(p), v[1], v[2]])
                else:
                    out[b][k] = keys([(0, [0, 0, 0]), (t_in, v), (t_hold, v), (length, [0, 0, 0])])
        return out
    a["kneel"] = anim(2.0, pose_keys(2.0, kneel_pose, wobble=1.5), loop=True)
    a["intro"] = anim(3.0, {
        "root": {"position": keys([(0, [0, -14, 0]), (1.2, [0, -12, 0]), (2.2, [0, 1, 0]), (3.0, [0, 0, 0])])},
        "leg_l": {"rotation": keys([(0, [-70, 0, 0]), (1.2, [-60, 0, 0]), (2.2, [0, 0, 0])])},
        "leg_r": {"rotation": keys([(0, [-70, 0, 0]), (1.2, [-60, 0, 0]), (2.2, [0, 0, 0])])},
        "shin_l": {"rotation": keys([(0, [80, 0, 0]), (1.2, [70, 0, 0]), (2.2, [0, 0, 0])])},
        "shin_r": {"rotation": keys([(0, [80, 0, 0]), (1.2, [70, 0, 0]), (2.2, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [30, 0, 0]), (1.2, [20, 0, 0]), (2.2, [-14, 0, 0]), (2.7, [-14, 0, 0]), (3.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [30, 0, 0]), (1.0, [20, 0, 0]), (2.2, [-20, 0, 0]), (3.0, [0, 0, 0])])},
        "arm_l": {"rotation": keys([(0, [-20, 0, 0]), (2.2, [-60, 0, -50]), (2.7, [-62, 0, -52]), (3.0, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [-20, 0, 0]), (2.2, [-60, 0, 50]), (2.7, [-62, 0, 52]), (3.0, [0, 0, 0])])},
    })
    a["death"] = anim(3.5, {
        "root": {"position": keys([(0, [0, 0, 0]), (1.0, [0, -6, 0]), (2.6, [0, -9, -4]), (3.5, [0, -10, -6])]),
                 "rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 0]), (2.6, [50, 0, 4]), (3.5, [84, 0, 6])])},
        "leg_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-70, 0, 0]), (3.5, [-40, 0, 0])])},
        "leg_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-70, 0, 0]), (3.5, [-40, 0, 0])])},
        "shin_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [80, 0, 0]), (3.5, [60, 0, 0])])},
        "shin_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [80, 0, 0]), (3.5, [60, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (2.0, [30, 0, 10]), (3.5, [40, 20, 15])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-40, 0, -20]), (3.5, [-150, 0, -30])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-40, 0, 20]), (3.5, [-150, 0, 30])])},
        "plate_l": {"rotation": keys([(0, [0, 0, 0]), (2.0, [0, -60, 0]), (3.5, [10, -80, 20])])},
        "plate_r": {"rotation": keys([(0, [0, 0, 0]), (2.0, [0, 60, 0]), (3.5, [10, 80, -20])])},
    })
    a["roar"] = anim(2.0, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-12, 0, 0]), (1.6, [-12, 0, 0]), (2.0, [0, 0, 0])]),
                 "position": cyc(2.0, lambda p: [0.3 * S(p * 12), 0, 0], steps=24)},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (1.6, [-25, 0, 0]), (2.0, [0, 0, 0])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-40, 0, -60]), (1.6, [-42, 0, -62]), (2.0, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-40, 0, 60]), (1.6, [-42, 0, 62]), (2.0, [0, 0, 0])])},
    })
    # pisao (22 ticks = 1.1 s)
    for side, other in (("l", "r"), ("r", "l")):
        a["stomp_" + side] = anim(1.5, {
            "leg_" + side: {"rotation": keys([(0, [0, 0, 0]), (0.7, [-55, 0, 0]), (0.95, [-55, 0, 0]), (1.1, [-8, 0, 0]), (1.5, [0, 0, 0])])},
            "shin_" + side: {"rotation": keys([(0, [0, 0, 0]), (0.7, [45, 0, 0]), (0.95, [45, 0, 0]), (1.1, [0, 0, 0])])},
            "body": {"rotation": keys([(0, [0, 0, 0]), (0.7, [-6, 0, 6 if side == "l" else -6]), (1.1, [6, 0, 0]), (1.5, [0, 0, 0])]),
                     "position": keys([(0, [0, 0, 0]), (0.7, [0, 1, 0]), (1.1, [0, -1.5, 0]), (1.5, [0, 0, 0])])},
            "arm_" + side: {"rotation": keys([(0, [0, 0, 0]), (0.7, [-20, 0, -25 if side == "l" else 25]), (1.1, [10, 0, 0]), (1.5, [0, 0, 0])])},
        })
    # arremesso (arranca em 0.6 s = 12 ticks, solta em 1.2 s = 24 ticks)
    a["throw"] = anim(1.6, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [30, 20, 0]), (0.7, [24, 15, 0]), (1.0, [-14, -20, 0]), (1.2, [16, 10, 0]), (1.6, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-40, 0, 0]), (0.7, [-40, 0, 0]), (1.0, [-170, 0, 10]), (1.2, [-60, 0, 0]), (1.6, [0, 0, 0])])},
        "forearm_r": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (1.0, [-40, 0, 0]), (1.2, [0, 0, 0])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-40, 0, 0]), (0.7, [-40, 0, 0]), (1.0, [-150, 0, -10]), (1.2, [-60, 0, 0]), (1.6, [0, 0, 0])])},
    })
    # punho cravado (26 ticks = 1.3 s), depois ajoelha (estado)
    a["fist"] = anim(1.6, {
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-175, 0, 15]), (1.3, [-50, 0, 0]), (1.6, [-45, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-175, 0, -15]), (1.3, [-50, 0, 0]), (1.6, [-45, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-16, 0, 0]), (1.3, [30, 0, 0]), (1.6, [26, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [10, 0, 0]), (1.6, [10, 0, 0])])},
    })
    # varredura baixa (24 ticks = 1.2 s)
    a["sweep"] = anim(1.6, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.8, [10, -55, 0]), (1.2, [12, 120, 0]), (1.4, [8, 140, 0]), (1.6, [0, 0, 0])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-30, 0, -80]), (1.2, [-30, 0, -85]), (1.6, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-30, 0, 80]), (1.2, [-30, 0, 85]), (1.6, [0, 0, 0])])},
    })
    # chuva de pedras: socos no peito
    a["rain"] = anim(1.6, {
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-80, -40, 0]), (0.45, [-60, -60, 0]), (0.7, [-80, -40, 0]), (0.85, [-60, -60, 0]), (1.6, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (0.45, [-80, 40, 0]), (0.6, [-60, 60, 0]), (0.85, [-80, 40, 0]), (1.0, [-60, 60, 0]), (1.6, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-30, 0, 0]), (1.4, [-30, 0, 0]), (1.6, [0, 0, 0])])},
    })
    # placas do peito (controlador proprio)
    a["core_closed"] = anim(1.0, {"plate_l": {"rotation": cyc(1.0, lambda p: [0, 0, 0])}, "plate_r": {"rotation": cyc(1.0, lambda p: [0, 0, 0])},
                                  "core": {"scale": cyc(1.0, lambda p: [1, 1, 1])}}, loop=True)
    a["core_open"] = anim(1.0, {"plate_l": {"rotation": cyc(1.0, lambda p: [0, -75 + 3 * S(p), 0])},
                                "plate_r": {"rotation": cyc(1.0, lambda p: [0, 75 - 3 * S(p), 0])},
                                "core": {"scale": cyc(1.0, lambda p: [1 + 0.08 * S(p), 1 + 0.08 * S(p), 1])}}, loop=True)
    a["core_cracked"] = anim(1.0, {"plate_l": {"rotation": cyc(1.0, lambda p: [0, -30 + 4 * S(p * 2), 0])},
                                   "plate_r": {"rotation": cyc(1.0, lambda p: [0, 30 - 4 * S(p * 2), 0])},
                                   "core": {"scale": cyc(1.0, lambda p: [1 + 0.05 * S(p * 2), 1 + 0.05 * S(p * 2), 1])}}, loop=True)
    save_anims("ruin_colossus", a)


# =====================================================================================
#  ITENS DE CHEFE (16x16)
# =====================================================================================
def item_tex(name, fn):
    c = Canvas(16, 16)
    fn(c)
    c.outline(H("#14100C"))
    c.save(os.path.join(ASSETS, "textures/item", name + ".png"))
    write_json(os.path.join(ASSETS, "models/item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})


def tex_hydra_fang(c):
    ivory, ivory_d, venom = H("#EDE6C4"), H("#B8AE84"), H("#8BE04A")
    for k in range(11):
        x = 4 + k // 2
        y = 2 + k
        w = max(1, 4 - k // 3)
        for dx in range(w):
            c.set(x + dx, y, mix(ivory, ivory_d, dx / max(1, w)))
    for (x, y) in ((8, 12), (9, 13), (9, 14), (8, 13)):
        c.set(x, y, venom)
    c.set(10, 14, H("#C8FF8A"))
    c.rect(3, 1, 7, 2, H("#6E8A44"))


def tex_hydra_heart(c):
    red, dark, vein = H("#8E1E2A"), H("#4E0A12"), H("#7BC043")
    c.circle(5.5, 6, 3.6, red)
    c.circle(10.5, 6, 3.6, red)
    for y in range(7, 14):
        for x in range(2, 14):
            if abs(x - 7.5) <= (13.5 - y) * 0.9:
                c.set(x, y, red)
    c.shade_sphere(8, 8, 7)
    for (x, y) in ((7, 5), (7, 6), (8, 7), (8, 8), (7, 9), (9, 9), (10, 10), (6, 10), (5, 11)):
        c.set(x, y, vein)
    c.set(5, 5, H("#E06070"))
    c.set(4, 6, dark)


def tex_wyvern_scale(c):
    base, dark, light, bolt = H("#3A5C88"), H("#1A2C44"), H("#7FA6D4"), H("#BFF0FF")
    for y in range(2, 15):
        half = int(5.5 * math.sin(math.pi * (y - 1) / 14)) + 1
        for x in range(8 - half, 8 + half):
            c.set(x, y, base)
    c.shade_sphere(7, 6, 7, 0.4, 0.4)
    for y in range(4, 13, 3):
        c.line(5, y, 10, y + 1, dark)
    c.line(8, 3, 6, 8, bolt)
    c.line(6, 8, 9, 8, bolt)
    c.line(9, 8, 7, 13, bolt)
    c.set(7, 4, light)


def tex_wyvern_horn(c):
    horn, horn_d, gold, bolt = H("#EAE4CE"), H("#A8A08A"), H("#D9B648"), H("#7FDBFF")
    pts = [(3, 13), (4, 12), (5, 10), (6, 8), (7, 6), (9, 4), (11, 3), (13, 2)]
    for k, (x, y) in enumerate(pts):
        w = max(1, 3 - k // 3)
        for dy in range(w):
            for dx in range(w):
                c.set(x + dx, y + dy, mix(horn, horn_d, (dx + dy) / 4))
    c.rect(2, 12, 5, 14, gold)
    c.set(3, 13, H("#F4E08A"))
    for (x, y) in ((10, 6), (12, 5), (13, 7), (8, 2)):
        c.set(x, y, bolt)


def tex_basilisk_scale(c):
    base, dark, light, stone = H("#C8984E"), H("#7C5A2C"), H("#F0D49A"), H("#8E8A7E")
    for y in range(2, 14):
        for x in range(2, 14):
            inside = abs(x + 0.5 - 8) + abs(y + 0.5 - 7.5) * 0.6 < 6.5 and 2 <= y <= 13
            if inside:
                c.set(x, y, base)
    c.shade_sphere(7, 6, 7)
    c.line(4, 7, 11, 7, dark)
    c.line(8, 3, 8, 12, dark)
    for (x, y) in ((5, 4), (10, 10), (6, 11)):
        c.set(x, y, stone)
    c.set(6, 4, light)


def tex_basilisk_eye(c):
    rim, rim_d, iris, glow, pupil = H("#8E8A7E"), H("#5E5A50"), H("#C8E84A"), H("#F4FFB0"), H("#141404")
    c.circle(8, 8, 6.8, rim)
    c.shade_sphere(8, 8, 6.8)
    c.circle(8, 8, 5.2, iris)
    c.circle(8, 8, 3.0, glow)
    for y in range(3, 14):
        c.set(8, y, pupil)
        if 5 <= y <= 11:
            c.set(7, y, pupil)
    c.set(6, 5, H("#FFFFFF"))
    c.set(3, 8, rim_d)
    c.set(12, 9, rim_d)


def tex_colossus_rune(c):
    st, st_d, st_l, rune = H("#8C8A80"), H("#5A5850"), H("#B4B2A6"), H("#3FE0D0")
    poly = [(3, 3), (12, 2), (14, 9), (9, 14), (2, 11)]
    for y in range(16):
        for x in range(16):
            # dentro do poligono convexo (teste de lado)
            ok = True
            for i in range(len(poly)):
                ax, ay = poly[i]
                bx, by = poly[(i + 1) % len(poly)]
                if (bx - ax) * (y + 0.5 - ay) - (by - ay) * (x + 0.5 - ax) < 0:
                    ok = False
                    break
            if ok:
                c.set(x, y, st)
    c.noise(0.12, 7)
    c.line(3, 4, 12, 3, st_l)
    c.line(4, 11, 9, 13, st_d)
    for (x, y) in ((7, 5), (8, 5), (9, 5), (8, 6), (8, 7), (8, 8), (6, 9), (7, 9), (9, 9), (10, 9), (8, 10)):
        c.set(x, y, rune)
    c.set(8, 8, H("#C8FFF8"))


def tex_colossus_core(c):
    st, core, core_l, gold = H("#6E6C62"), H("#3FE0D0"), H("#E2FFFB"), H("#D8B24A")
    c.circle(8, 8, 7, st)
    c.shade_sphere(8, 8, 7, 0.3, 0.5)
    c.circle(8, 8, 4.6, core)
    c.shade_sphere(8, 8, 4.6, 0.6, 0.3)
    c.circle(7, 7, 1.6, core_l)
    for (x, y) in ((8, 1), (8, 14), (1, 8), (14, 8)):
        c.set(x, y, gold)
        c.set(x + (1 if x < 8 else -1 if x > 8 else 0), y + (1 if y < 8 else -1 if y > 8 else 0), gold)


ITEMS = (
    ("swamp_hydra_fang", tex_hydra_fang), ("swamp_hydra_heart", tex_hydra_heart),
    ("wyvern_king_scale", tex_wyvern_scale), ("wyvern_king_horn", tex_wyvern_horn),
    ("desert_basilisk_scale", tex_basilisk_scale), ("desert_basilisk_eye", tex_basilisk_eye),
    ("ruin_colossus_rune", tex_colossus_rune), ("ruin_colossus_core", tex_colossus_core),
)


def venom_glob_texture():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 2.5:
                c.set(x, y, H("#E8FFB0"))
            elif d <= 4.5:
                c.set(x, y, mix(H("#B4F05A"), H("#5E9A2A"), (d - 2.5) / 2))
            elif d <= 6.2:
                c.set(x, y, (94, 154, 42, int(255 * (1 - (d - 4.5) / 1.7))))
    c.save(os.path.join(ASSETS, "textures/item", "swamp_hydra_venom_glob.png"))
    write_json(os.path.join(ASSETS, "models/item", "swamp_hydra_venom_glob.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/swamp_hydra_venom_glob" % NS}})


# =====================================================================================
#  DADOS: ovos, loot tables, tags
# =====================================================================================
BOSSES = ("swamp_hydra", "wyvern_king", "desert_basilisk", "ruin_colossus")


def item_entry(item, lo, hi, weight=1):
    e = {"type": "minecraft:item", "name": item, "weight": weight}
    if (lo, hi) != (1, 1):
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def pool(entries, rolls=1):
    return {"rolls": rolls, "bonus_rolls": 0, "entries": entries}


def loot(pools):
    return {"type": "minecraft:entity", "pools": pools}


def write_data():
    for b in BOSSES:
        write_json(os.path.join(ASSETS, "models/item", b + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    lt = os.path.join(DATA, "loot_table/entities")
    write_json(os.path.join(lt, "swamp_hydra.json"), loot([
        pool([item_entry("minecraft:slime_ball", 4, 9)]),
        pool([item_entry("minecraft:lily_pad", 2, 5)]),
        pool([item_entry("minecraft:fermented_spider_eye", 1, 3), item_entry("minecraft:spider_eye", 2, 4)]),
        pool([item_entry("minecraft:bone", 3, 7)]),
        pool([item_entry("minecraft:emerald", 2, 5)]),
    ]))
    write_json(os.path.join(lt, "wyvern_king.json"), loot([
        pool([item_entry("minecraft:phantom_membrane", 2, 5)]),
        pool([item_entry("minecraft:feather", 3, 7)]),
        pool([item_entry("minecraft:copper_ingot", 4, 10)]),
        pool([item_entry("minecraft:gold_ingot", 2, 5), item_entry("minecraft:diamond", 1, 1, 1)]),
    ]))
    write_json(os.path.join(lt, "desert_basilisk.json"), loot([
        pool([item_entry("minecraft:leather", 3, 7)]),
        pool([item_entry("minecraft:gold_ingot", 2, 5)]),
        pool([item_entry("minecraft:ender_eye", 1, 1, 1), item_entry("minecraft:spider_eye", 2, 4, 3)]),
        pool([item_entry("minecraft:sandstone", 4, 9), item_entry("minecraft:chiseled_sandstone", 2, 4)]),
    ]))
    write_json(os.path.join(lt, "ruin_colossus.json"), loot([
        pool([item_entry("minecraft:mossy_stone_bricks", 6, 12)]),
        pool([item_entry("minecraft:chiseled_stone_bricks", 2, 4)]),
        pool([item_entry("minecraft:iron_ingot", 4, 9)]),
        pool([item_entry("minecraft:emerald", 2, 5), item_entry("minecraft:diamond", 1, 2, 1)]),
    ]))
    ids = ["%s:%s" % (NS, b) for b in BOSSES]
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": ids})
    write_json(os.path.join(MERGE, "data/minecraft/tags/entity_type/can_breathe_under_water.json"),
               {"replace": False, "values": ["%s:swamp_hydra" % NS]})


def main():
    build_hydra()
    build_wyvern()
    build_basilisk()
    build_colossus()
    for name, fn in ITEMS:
        item_tex(name, fn)
    venom_glob_texture()
    write_data()
    print("bosses_f: assets gerados")


if __name__ == "__main__":
    main()
