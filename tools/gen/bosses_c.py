"""
Gerador de assets do modulo bosses_c (minichefes das estruturas de classe):
modelos GeckoLib (.geo.json) com box UV pintada por face (+ glowmasks), animacoes, texturas dos projeteis,
modelos dos ovos, loot tables (itens do vanilla; os itens de outros modulos caem pelo Java, por id) e tags.

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_c.py
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
MERGE = os.path.join(ROOT, "src/main/merge/bosses_c")
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
#  ARQUIMAGO RENEGADO
# =====================================================================================
def magic_ring_top(color, inner, rune, g=True):
    """Desenha um circulo magico (anel + runas) na face de cima; o resto fica transparente."""
    def d(fs):
        for n in ("front", "back", "left", "right", "bottom"):
            fs[n].fill(CLEAR)
        f = fs["top"]
        f.fill(CLEAR)
        cx, cy = f.w / 2.0, f.h / 2.0
        R = f.w / 2.0 - 0.5
        for yy in range(f.h):
            for xx in range(f.w):
                dist = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
                ang = math.atan2(yy + 0.5 - cy, xx + 0.5 - cx)
                if R - 1.2 <= dist <= R:
                    f.set(xx, yy, H(color), g)
                elif R - 4.2 <= dist <= R - 3.2:
                    f.set(xx, yy, H(inner), g)
                elif R - 3.0 < dist < R - 1.4:
                    # runas entre os aneis
                    k = int((ang + math.pi) / (2 * math.pi) * 24)
                    if k % 2 == 0 and (xx + yy) % 2 == 0:
                        f.set(xx, yy, H(rune), g)
                elif R - 9 <= dist <= R - 8.2:
                    f.set(xx, yy, H(inner), g)
        # hexagrama interno
        r2 = R - 4.4
        pts = [(cx + r2 * math.cos(a), cy + r2 * math.sin(a)) for a in [math.pi / 2 + i * math.pi / 3 for i in range(6)]]
        for tri in ((0, 2, 4), (1, 3, 5)):
            for i in range(3):
                x0, y0 = pts[tri[i]]
                x1, y1 = pts[tri[(i + 1) % 3]]
                steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
                for s in range(steps + 1):
                    t = s / steps
                    f.set(int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t), H(rune), g)
    return d


def build_archmage():
    m = Model("renegade_archmage")
    robe, robe_d, trim, skin, beard, hat = "#3E2470", "#25144A", "#D9B44A", "#DDBB9C", "#E6E4EE", "#2A1856"
    rune, eye, wood = "#C9A2FF", "#E8C8FF", "#4A2E1C"
    m.bone("root", (0, 0, 0))
    m.bone("robe", (0, 14, 0), "root")

    def skirt(fs):
        for n in SIDES:
            f = fs[n]
            for xx in range(1, f.w, 3):
                for yy in range(f.h - 2):
                    if (yy + xx) % 4:
                        f.set(xx, yy, darken(H(robe), 0.18))
        f = fs["front"]
        f.col(f.w // 2, H(trim))
        for yy in range(1, f.h - 3, 3):
            f.set(f.w // 2 - 1, yy, H(rune), True)
            f.set(f.w // 2 + 1, yy + 1, H(rune), True)
    m.cube("robe", (-4.5, 1, -3), (9, 13, 6), P(robe, deco=chain(skirt, hem(trim, ragged=True, seed=5))))
    m.bone("body", (0, 14, 0), "root")

    def torso(fs):
        f = fs["front"]
        # abertura do manto em V com bordado dourado
        for yy in range(f.h):
            w = max(0, 3 - yy // 3)
            for xx in range(f.w // 2 - w, f.w // 2 + w):
                f.set(xx, yy, darken(H(robe), 0.35))
            f.set(f.w // 2 - w - 1, yy, H(trim))
            f.set(f.w // 2 + w, yy, H(trim))
        f.row(f.h - 3, H("#5A3A1E"))
        f.rect(f.w // 2 - 1, f.h - 3, f.w // 2, f.h - 3, H(trim))
        fs["back"].row(fs["back"].h - 3, H("#5A3A1E"))
        for n in ("left", "right"):
            fs[n].row(fs[n].h - 3, H("#5A3A1E"))
    m.cube("body", (-4, 14, -2.5), (8, 11, 5), P(robe, deco=torso))
    # ombreiras / mantelete
    m.cube("body", (-5, 21, -3), (10, 4, 6), P(robe_d, deco=chain(band([0], trim), band([-1], darken(H(trim), 0.2)),
                                                                     lambda fs: fs["top"].fill(lighten(H(robe_d), 0.1)))),
           inflate=0.25)
    # capa
    m.bone("cape", (0, 24.5, 2.5), "body")
    m.cube("cape", (-4.5, 3, 2.6), (9, 21, 1), P(robe_d, deco=chain(
        folds(darken(H(robe_d), 0.25), step=3, faces=("back", "front")),
        hem(trim, ragged=True, faces=("back", "front", "left", "right"), seed=9),
        lambda fs: [fs["back"].set(4 + dx, 8 + dy, H(rune), True) for dx, dy in
                    ((0, 0), (-1, 1), (1, 1), (0, 2), (-2, 2), (2, 2), (0, 3), (0, 4))])))
    m.bone("head", (0, 25, 0), "body")

    def face(fs):
        f = fs["front"]
        f.row(0, darken(H(skin), 0.15))
        f.rect(1, 2, 2, 2, darken(H(skin), 0.35))
        f.rect(4, 2, 5, 2, darken(H(skin), 0.35))
        f.set(1, 3, H(eye), True)
        f.set(2, 3, H(rune), True)
        f.set(4, 3, H(rune), True)
        f.set(5, 3, H(eye), True)
        f.set(3, 4, darken(H(skin), 0.2))
        f.rect(0, 5, 6, 6, H(beard))
        f.set(3, 5, darken(H(skin), 0.4))
        for n in ("left", "right"):
            fs[n].rect(0, 4, fs[n].w - 1, 6, H(beard))
            fs[n].rect(0, 0, fs[n].w - 1, 1, H("#BFBCCB"))
        fs["back"].rect(0, 0, 6, 6, H("#BFBCCB"))
    m.cube("head", (-3.5, 25, -3.5), (7, 7, 7), P(skin, deco=face, noise=0.03))
    # barba longa
    m.cube("head", (-3, 20, -4.2), (6, 6, 1), P(beard, deco=chain(
        lambda fs: [fs["front"].set(x, fs["front"].h - 1, CLEAR) for x in (0, 5)],
        lambda fs: fs["front"].col(2, darken(H(beard), 0.12)),
        lambda fs: fs["front"].set(3, 4, darken(H(beard), 0.15)))), rotation=[8, 0, 0], pivot=[0, 25, -4])
    # chapeu pontudo
    m.bone("hat", (0, 31.5, 0), "head")
    m.cube("hat", (-6, 31.5, -6), (12, 1, 12), P(hat, deco=chain(rim(darken(H(hat), 0.3)),
                                                               lambda fs: fs["top"].tint(lambda c, x, y: H(trim) if (x + y) % 11 == 0 else c))))
    m.cube("hat", (-4, 32.5, -4), (8, 3, 8), P(hat, deco=chain(band([1], trim), lambda fs: [fs["front"].set(x, 1, H(rune), True) for x in (3, 4)])))
    m.cube("hat", (-3, 35.2, -2.8), (6, 3, 6), P(hat, deco=speckle(trim, 0.05, 4)), rotation=[-8, 0, 0], pivot=[0, 35, 0])
    m.cube("hat", (-2, 37.8, -1.2), (4, 3, 4), P(hat, deco=speckle(rune, 0.06, 6, g=True)), rotation=[-18, 0, 0], pivot=[0, 38, 0])
    m.cube("hat", (-1, 40, 1.2), (2, 3, 2), P(hat, faces={"top": rune}, deco=lambda fs: fs["top"].set(0, 0, H(rune), True)),
           rotation=[-36, 0, 0], pivot=[0, 40, 1.5])
    # bracos
    for side, x0, sx in (("right_arm", -7.5, -5), ("left_arm", 4.5, 5)):
        m.bone(side, (sx, 23.5, 0), "body")
        m.cube(side, (x0, 14, -2), (3, 10, 4), P(robe, deco=chain(hem(trim, faces=SIDES),
                                                                  band([0], robe_d))))
        hx = x0 + 0.5
        m.cube(side, (hx, 12, -1.5), (2, 2, 3), P(skin, noise=0.03,
                                                    deco=(lambda fs: fs["bottom"].fill(H(rune)) if side == "left_arm" else None)))
    # cajado na mao direita
    m.bone("staff", (-6.5, 13, 0), "right_arm")
    m.cube("staff", (-7, 0, -0.5), (1, 31, 1), P(wood, noise=0.02, deco=chain(
        lambda fs: [fs[n].set(0, y, H(trim)) for n in SIDES for y in (4, 12, 20)])))
    m.cube("staff", (-8, 29, -1.5), (3, 2, 3), P(trim, deco=rim(darken(H(trim), 0.3))))
    m.cube("staff", (-8, 31, -1.5), (3, 3, 3), P("#B98CFF", deco=glow_faces("#B98CFF", core="#F2E2FF")))
    m.cube("staff", (-9, 30.5, -0.5), (1, 4, 1), P(trim), rotation=[0, 0, 12], pivot=[-8.5, 31, 0])
    m.cube("staff", (-5, 30.5, -0.5), (1, 4, 1), P(trim), rotation=[0, 0, -12], pivot=[-4.5, 31, 0])
    # escudo arcano: 3 circulos magicos girando (so aparece com o escudo ativo)
    m.bone("shield", (0, 17, 0), "root")
    m.bone("ring_a", (0, 17, 0), "shield")
    ring = m.cube("ring_a", (-14, 17, -14), (28, 0, 28), P(CLEAR, noise=0, grad=False,
                                                         deco=magic_ring_top("#C9A2FF", "#8E5BEA", "#F3E6FF")))
    m.bone("ring_b_tilt", (0, 17, 0), "shield", rotation=[62, 0, 0])
    m.bone("ring_b", (0, 17, 0), "ring_b_tilt")
    m.cube("ring_b", (-14, 17, -14), (28, 0, 28), P(CLEAR), share=ring)
    m.bone("ring_c_tilt", (0, 17, 0), "shield", rotation=[-62, 0, 0])
    m.bone("ring_c", (0, 17, 0), "ring_c_tilt")
    m.cube("ring_c", (-14, 17, -14), (28, 0, 28), P(CLEAR), share=ring)
    m.save(bounds=(4, 4), seed=21)

    a = {}
    a["idle"] = anim(3.0, {
        "body": {"rotation": cyc(3.0, lambda p: [1.5 * S(p), 0, 0]), "position": cyc(3.0, lambda p: [0, 0.4 * S(p), 0])},
        "robe": {"rotation": cyc(3.0, lambda p: [2 * S(p + 0.25), 0, 1 * S(p)])},
        "right_arm": {"rotation": cyc(3.0, lambda p: [-12 + 2 * S(p), 0, 3])},
        "left_arm": {"rotation": cyc(3.0, lambda p: [-4 * S(p), 0, -4 - 2 * S(p)])},
        "cape": {"rotation": cyc(3.0, lambda p: [4 + 2 * S(p), 0, 0])},
        "hat": {"rotation": cyc(3.0, lambda p: [0, 0, 1.5 * S(p)])},
    }, loop=True)
    a["walk"] = anim(1.2, {
        "body": {"position": cyc(1.2, lambda p: [0, 0.5 * abs(C(p)), 0]), "rotation": cyc(1.2, lambda p: [4, 2 * S(p), 0])},
        "robe": {"rotation": cyc(1.2, lambda p: [7 * S(p), 0, 2 * C(p)])},
        "right_arm": {"rotation": cyc(1.2, lambda p: [-12 - 10 * S(p), 0, 3])},
        "left_arm": {"rotation": cyc(1.2, lambda p: [14 * S(p), 0, -4])},
        "cape": {"rotation": cyc(1.2, lambda p: [14 + 5 * S(p), 0, 0])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-165, 0, 10]), (2.0, [-160, 0, 12]), (2.3, [-35, 0, 0]), (3.0, [-12, 0, 3])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-40, 0, -70]), (2.0, [-45, 0, -75]), (2.4, [-10, 0, -10]), (3.0, [0, 0, -4])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-10, 0, 0]), (2.0, [-12, 0, 0]), (2.3, [10, 0, 0]), (3.0, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.8, [0, 2, 0]), (2.0, [0, 2.5, 0]), (2.3, [0, -0.5, 0]), (3.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.8, [-20, 0, 0]), (2.0, [-22, 0, 0]), (2.4, [6, 0, 0]), (3.0, [0, 0, 0])])},
        "cape": {"rotation": keys([(0, [0, 0, 0]), (0.8, [30, 0, 0]), (2.0, [35, 0, 0]), (3.0, [4, 0, 0])])},
    })
    a["death"] = anim(2.4, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-12, 0, 0]), (1.6, [38, 0, 6]), (2.4, [62, 0, 8])]),
                 "position": keys([(0, [0, 0, 0]), (1.6, [0, -6, 0]), (2.4, [0, -11, -2])])},
        "robe": {"scale": keys([(0, [1, 1, 1]), (1.6, [1.1, 0.6, 1.1]), (2.4, [1.2, 0.25, 1.2])]),
                 "position": keys([(0, [0, 0, 0]), (2.4, [0, -1, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-25, 0, 0]), (2.4, [30, 15, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-60, 0, 20]), (2.4, [-10, 0, 30])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-60, 0, -20]), (2.4, [-5, 0, -35])])},
        "staff": {"rotation": keys([(0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [0, 0, 70])])},
    })
    a["cast"] = anim(0.9, {
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-95, 10, 0]), (0.65, [-92, 0, 0]), (0.9, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-70, 0, 10]), (0.65, [-80, 0, 8]), (0.9, [-12, 0, 3])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-4, -12, 0]), (0.5, [6, 10, 0]), (0.9, [0, 0, 0])])},
    })
    a["shield"] = anim(1.0, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-20, 0, 85]), (0.75, [-20, 0, 80]), (1.0, [-12, 0, 3])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-20, 0, -85]), (0.75, [-20, 0, -80]), (1.0, [0, 0, -4])])},
        "body": {"position": keys([(0, [0, 0, 0]), (0.35, [0, 1.5, 0]), (1.0, [0, 0, 0])])},
        "shield": {"scale": keys([(0, [0.2, 0.2, 0.2]), (0.35, [1.15, 1.15, 1.15]), (0.6, [1, 1, 1])])},
    })
    a["summon"] = anim(1.5, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, 25]), (1.1, [-165, 0, 30]), (1.5, [-12, 0, 3])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, -25]), (1.1, [-165, 0, -30]), (1.5, [0, 0, -4])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-25, 0, 0]), (1.1, [-25, 0, 0]), (1.5, [0, 0, 0])])},
        "body": {"position": keys([(0, [0, 0, 0]), (0.5, [0, 2, 0]), (1.1, [0, 2, 0]), (1.5, [0, 0, 0])])},
    })
    a["nova"] = anim(1.2, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [18, 0, 0]), (0.8, [-14, 0, 0]), (1.2, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.6, [0, -2, 0]), (0.8, [0, 1, 0]), (1.2, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, -20]), (0.8, [-90, 0, 70]), (1.2, [-12, 0, 3])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, 20]), (0.8, [-90, 0, -70]), (1.2, [0, 0, -4])])},
    })
    a["teleport"] = anim(0.4, {
        "root": {"scale": keys([(0, [1, 1, 1]), (0.15, [0.3, 1.6, 0.3]), (0.25, [1.25, 0.8, 1.25]), (0.4, [1, 1, 1])])},
    })
    # Barragem Arcana (v0.7): ergue o cajado acima da cabeca enquanto as linhas enchem e o crava para a frente
    # no instante do disparo (1.2 s = 24 ticks de aviso).
    a["barrage"] = anim(1.6, {
        "right_arm": {"rotation": keys([(0, [-12, 0, 3]), (0.4, [-175, 0, 15]), (1.0, [-172, 0, 12]), (1.2, [-78, 0, 0]), (1.6, [-12, 0, 3])])},
        "left_arm": {"rotation": keys([(0, [0, 0, -4]), (0.4, [-80, -20, -20]), (1.0, [-85, -20, -25]), (1.2, [-95, 0, 0]), (1.6, [0, 0, -4])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-10, 0, 0]), (1.0, [-12, 0, 0]), (1.2, [12, 0, 0]), (1.6, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.4, [0, 1.5, 0]), (1.0, [0, 2, 0]), (1.2, [0, -0.5, 0]), (1.6, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-15, 0, 0]), (1.0, [-15, 0, 0]), (1.2, [8, 0, 0]), (1.6, [0, 0, 0])])},
        "cape": {"rotation": keys([(0, [4, 0, 0]), (0.4, [25, 0, 0]), (1.0, [28, 0, 0]), (1.2, [42, 0, 0]), (1.6, [4, 0, 0])])},
        "staff": {"rotation": keys([(0, [0, 0, 0]), (0.4, [0, 0, 0]), (1.0, [0, 90, 0]), (1.2, [0, 140, 0]), (1.6, [0, 0, 0])])},
    })
    a["shield_spin"] = anim(4.0, {
        "ring_a": {"rotation": spin(4.0, 1, 1)},
        "ring_b": {"rotation": spin(4.0, 1, -1)},
        "ring_c": {"rotation": spin(4.0, 1, 2)},
    }, loop=True)
    save_anims("renegade_archmage", a)


# =====================================================================================
#  BRUXA CORROMPIDA
# =====================================================================================
def build_witch():
    m = Model("corrupted_witch")
    robe, robe_d, skin, skin_d, hair = "#3B2A4A", "#241830", "#8FA678", "#6C8058", "#1E1A22"
    hat, band_c, buckle, eye, potion = "#2B1F38", "#6B2F8C", "#C9A23A", "#B6FF4A", "#7CFF4A"
    wood, straw = "#5A3A20", "#C9A85A"
    m.bone("root", (0, 0, 0))
    for side, x0 in (("right_leg", -3), ("left_leg", 0)):
        m.bone(side, (x0 + 1.5, 12, 0), "root")
        m.cube(side, (x0, 0, -1.5), (3, 12, 3), P("#2A2230", deco=chain(
            band([-1, -2], "#151018"), lambda fs: fs["front"].set(1, 9, H("#4A3A28")),
            band([8], "#3A2A20"))))
    m.bone("body", (0, 12, 0), "root")
    m.bone("skirt", (0, 13, 0), "body")
    m.cube("skirt", (-4.5, 3, -3), (9, 10, 6), P(robe, deco=chain(
        folds(darken(H(robe), 0.25), step=2), hem(band_c, ragged=True, seed=11),
        speckle("#5BD83A", 0.03, 12, faces=SIDES, g=True))))

    def torso(fs):
        f = fs["front"]
        # xale com franjas
        for yy in range(4):
            f.rect(yy, yy, f.w - 1 - yy, yy, H(band_c))
        f.set(3, 4, H(buckle))
        f.row(f.h - 1, darken(H(robe), 0.35))
        # colar de ossos
        for xx in (1, 3, 5):
            f.set(xx, 5, H("#E8E0C8"))
        for n in ("left", "right", "back"):
            fs[n].rect(0, 0, fs[n].w - 1, 2, H(band_c))
    m.cube("body", (-3.5, 13, -2), (7, 9, 4), P(robe, deco=torso))
    # corcunda
    m.cube("body", (-3, 18, 1.5), (6, 4, 2), P(robe_d, deco=folds(darken(H(robe_d), 0.2), step=2)))
    m.bone("head", (0, 22, -0.5), "body")

    def face(fs):
        f = fs["front"]
        f.rect(0, 0, f.w - 1, 1, H(hair))
        f.set(0, 2, H(hair))
        f.set(f.w - 1, 2, H(hair))
        f.rect(1, 3, 2, 3, H("#1A2412"))
        f.rect(4, 3, 5, 3, H("#1A2412"))
        f.set(1, 3, H(eye), True)
        f.set(5, 3, H(eye), True)
        f.set(2, 2, darken(H(skin), 0.3))
        f.set(4, 2, darken(H(skin), 0.3))
        f.rect(2, 6, 4, 6, H("#3A2030"))
        f.set(3, 6, H("#E8E0A0"))
        f.set(5, 5, H(skin_d))
        f.set(1, 5, darken(H(skin_d), 0.2))
        for n in ("left", "right"):
            fs[n].rect(0, 0, fs[n].w - 1, fs[n].h - 1, H(hair))
            fs[n].rect(0, 2, 1, 5, H(skin)) if n == "left" else fs[n].rect(fs[n].w - 2, 2, fs[n].w - 1, 5, H(skin))
        fs["back"].fill(H(hair))
        fs["back"].scatter(lighten(H(hair), 0.15), 0.2, 5)
    m.cube("head", (-3.5, 22, -4), (7, 8, 7), P(skin, deco=face, noise=0.05))
    # cabelo comprido atras
    m.cube("head", (-3.5, 15, 2.6), (7, 8, 1), P(hair, deco=chain(
        lambda fs: [fs["back"].set(x, fs["back"].h - 1, CLEAR) for x in (0, 2, 5)],
        speckle("#3A2E44", 0.2, 3, faces=("back",)))))
    m.bone("nose", (0, 25, -4), "head")
    m.cube("nose", (-0.5, 23, -6.5), (1, 3, 3), P(skin_d, deco=lambda fs: fs["bottom"].set(0, 0, H("#4A6A3A"))),
           rotation=[-20, 0, 0], pivot=[0, 25, -4])
    m.cube("nose", (-1, 22.5, -6.8), (1, 1, 1), P("#5A7A3A"))
    # chapeu torto
    m.bone("hat", (0, 29.5, -0.5), "head")
    m.cube("hat", (-6.5, 29.5, -7), (13, 1, 13), P(hat, deco=chain(rim(darken(H(hat), 0.35)),
                                                                 lambda fs: fs["top"].scatter(lighten(H(hat), 0.12), 0.08, 9))))
    m.cube("hat", (-4, 30.5, -4.5), (8, 3, 8), P(hat, deco=chain(band([0, 1], band_c),
                                                               lambda fs: fs["front"].rect(3, 0, 4, 1, H(buckle)))))
    m.cube("hat", (-3, 33, -3.2), (6, 3, 6), P(hat, deco=speckle("#5BD83A", 0.05, 7, g=True)), rotation=[-10, 0, 4], pivot=[0, 33, -0.5])
    m.cube("hat", (-2, 35.4, -1.2), (4, 3, 4), P(hat), rotation=[-24, 0, 8], pivot=[0, 35.5, -0.5])
    m.cube("hat", (-1, 37.4, 1.8), (2, 3, 2), P(hat, faces={"top": band_c}), rotation=[-48, 0, 14], pivot=[0, 37.5, 1.5])
    # bracos finos
    for side, x0, sx in (("right_arm", -5.5, -4.5), ("left_arm", 3.5, 4.5)):
        m.bone(side, (sx, 21, 0), "body")
        m.cube(side, (x0, 13, -1.5), (2, 8, 3), P(robe, deco=chain(hem(band_c, faces=SIDES), band([0], robe_d))))
        m.cube(side, (x0, 11, -1), (2, 2, 2), P(skin, deco=lambda fs: fs["bottom"].fill(H("#2A3A1A"))))
    # garrafa de maldicao na mao direita
    m.bone("bottle", (-4.5, 10, -0.5), "right_arm")
    m.cube("bottle", (-5.5, 8, -1.5), (2, 3, 2), P(potion, deco=glow_faces(potion, faces=("front", "back", "left", "right"), core="#D8FF9A")))
    m.cube("bottle", (-5, 11, -1), (1, 1, 1), P("#8A6A4A"))
    # vassoura (so aparece voando)
    m.bone("broom", (0, 10, 0), "root")
    m.cube("broom", (-0.5, 9.5, -15), (1, 1, 26), P(wood, deco=lambda fs: [fs[n].set(x, 0, darken(H(wood), 0.3)) for n in ("top", "left", "right")
                                                                          for x in range(0, 26, 4)]))
    m.cube("broom", (-2, 8, 11), (4, 4, 7), P(straw, deco=chain(
        folds(darken(H(straw), 0.25), step=2, faces=("top", "bottom", "left", "right")),
        lambda fs: [fs[n].row(1, H(band_c)) for n in ("left", "right")],
        lambda fs: fs["front"].fill(darken(H(straw), 0.2)))))
    m.cube("broom", (-1, 10.5, -15.5), (2, 2, 1), P("#7CFF4A", deco=glow_faces("#7CFF4A")))
    m.save(bounds=(4, 4), seed=31)

    a = {}
    a["idle"] = anim(2.5, {
        "body": {"rotation": cyc(2.5, lambda p: [10 + 2 * S(p), 0, 1.5 * S(p)])},
        "head": {"rotation": cyc(2.5, lambda p: [-8 + 3 * S(p * 2), 4 * S(p), 0])},
        "right_arm": {"rotation": cyc(2.5, lambda p: [-25 + 4 * S(p), 0, 4])},
        "left_arm": {"rotation": cyc(2.5, lambda p: [-15 - 6 * S(p), 0, -6])},
        "skirt": {"rotation": cyc(2.5, lambda p: [-8 + 2 * S(p), 0, 0])},
        "hat": {"rotation": cyc(2.5, lambda p: [0, 0, 2 * S(p)])},
    }, loop=True)
    a["walk"] = anim(1.0, {
        "right_leg": {"rotation": cyc(1.0, lambda p: [28 * S(p), 0, 0])},
        "left_leg": {"rotation": cyc(1.0, lambda p: [-28 * S(p), 0, 0])},
        "right_arm": {"rotation": cyc(1.0, lambda p: [-25 - 18 * S(p), 0, 4])},
        "left_arm": {"rotation": cyc(1.0, lambda p: [18 * S(p), 0, -6])},
        "body": {"rotation": cyc(1.0, lambda p: [12, 3 * S(p), 0]), "position": cyc(1.0, lambda p: [0, 0.5 * abs(C(p)), 0])},
        "skirt": {"rotation": cyc(1.0, lambda p: [-10 + 4 * S(p), 0, 0])},
    }, loop=True)
    a["fly"] = anim(2.0, {
        "root": {"position": cyc(2.0, lambda p: [0, 0.8 * S(p), 0])},
        "right_leg": {"rotation": cyc(2.0, lambda p: [-80, 8, 0])},
        "left_leg": {"rotation": cyc(2.0, lambda p: [-80, -8, 0])},
        "skirt": {"rotation": cyc(2.0, lambda p: [-62 + 3 * S(p), 0, 0])},
        "body": {"rotation": cyc(2.0, lambda p: [-4 + 2 * S(p), 0, 2 * S(p)])},
        "right_arm": {"rotation": cyc(2.0, lambda p: [-55 + 4 * S(p), 10, 6])},
        "left_arm": {"rotation": cyc(2.0, lambda p: [-60 - 4 * S(p), -10, -6])},
        "broom": {"rotation": cyc(2.0, lambda p: [2 * S(p), 0, 3 * S(p)])},
        "hat": {"rotation": cyc(2.0, lambda p: [12, 0, 4 * S(p * 2)])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "head": {"rotation": cyc(3.0, lambda p: [-30 + 6 * S(p * 9), 6 * S(p * 3), 0], steps=27)},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-150, 0, 25]), (2.5, [-150, 0, 30]), (3.0, [-25, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-150, 0, -25]), (2.5, [-145, 0, -30]), (3.0, [-15, 0, -6])])},
        "body": {"rotation": cyc(3.0, lambda p: [-6 + 2 * S(p * 9), 0, 0], steps=27)},
    })
    a["death"] = anim(2.0, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (1.4, [-70, 0, 10]), (2.0, [-85, 0, 12])]),
                 "position": keys([(0, [0, 0, 0]), (1.4, [0, -6, 2]), (2.0, [0, -10, 4])])},
        "right_leg": {"rotation": keys([(0, [0, 0, 0]), (2.0, [-60, 0, 10])])},
        "left_leg": {"rotation": keys([(0, [0, 0, 0]), (2.0, [-50, 0, -10])])},
        "hat": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-30, 0, 20]), (2.0, [-70, 0, 60])]),
                "position": keys([(0, [0, 0, 0]), (2.0, [-4, -2, 4])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (2.0, [-150, 0, 60])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (2.0, [-140, 0, -60])])},
    })
    a["throw"] = anim(0.7, {
        "right_arm": {"rotation": keys([(0, [-25, 0, 4]), (0.25, [-170, 0, 10]), (0.4, [-50, 0, 0]), (0.7, [-25, 0, 4])])},
        "body": {"rotation": keys([(0, [10, 0, 0]), (0.25, [-2, 15, 0]), (0.4, [18, -10, 0]), (0.7, [10, 0, 0])])},
    })
    a["summon"] = anim(1.5, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-140, 0, 40]), (1.1, [-140, 0, 45]), (1.5, [-25, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-140, 0, -40]), (1.1, [-140, 0, -45]), (1.5, [-15, 0, -6])])},
        "head": {"rotation": cyc(1.5, lambda p: [-20 + 5 * S(p * 6), 0, 0], steps=12)},
    })
    a["drink"] = anim(1.2, {
        "right_arm": {"rotation": keys([(0, [-25, 0, 4]), (0.3, [-115, 0, 30]), (0.9, [-120, 0, 32]), (1.2, [-25, 0, 4])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.9, [-32, 0, 0]), (1.2, [0, 0, 0])])},
    })
    # Caldeirao Venenoso (v0.7): curvada, mexe um caldeirao invisivel em circulos e no fim joga o conteudo para o alto.
    def stir(side):
        sx = 1 if side == "right" else -1

        def fn(p):
            if p < 0.8:
                q = p / 0.8
                return [-62 + 10 * S(q * 3), 14 * sx * C(q * 3), 6 * sx]
            k = (p - 0.8) / 0.2
            return [-62 - 95 * k, 0, (6 + 20 * k) * sx]
        return fn
    a["cauldron"] = anim(1.0, {
        "right_arm": {"rotation": cyc(1.0, stir("right"), steps=20)},
        "left_arm": {"rotation": cyc(1.0, stir("left"), steps=20)},
        "body": {"rotation": cyc(1.0, lambda p: [24 + 3 * S(p * 3), 0, 2 * S(p * 3)] if p < 0.8 else [24 - 40 * (p - 0.8) / 0.2, 0, 0], steps=20)},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.2, [22, 0, 0]), (0.8, [22, 0, 0]), (1.0, [-20, 0, 0])])},
        "hat": {"rotation": cyc(1.0, lambda p: [0, 0, 5 * S(p * 4)], steps=16)},
        "skirt": {"rotation": cyc(1.0, lambda p: [-12 + 3 * S(p * 3), 0, 0], steps=12)},
    })
    save_anims("corrupted_witch", a)


# =====================================================================================
#  PATRONO APRISIONADO
# =====================================================================================
def build_patron():
    m = Model("imprisoned_patron")
    skin, skin_d, skin_l, horn, hoof = "#7A1622", "#4E0C15", "#A3313A", "#2A2224", "#151012"
    iron, iron_d, fire, eye, cloth = "#5E5E66", "#3A3A42", "#FF6A2B", "#FFC23A", "#2A1A1A"
    m.bone("root", (0, 0, 0))
    for side, x0, sx in (("right_leg", -6.5, -4), ("left_leg", 1.5, 4)):
        m.bone(side, (sx, 20, 0), "root")
        m.cube(side, (x0, 4, -2.5), (5, 16, 5), P(skin, deco=chain(
            cracks(fire, 40 + int(sx), 2, faces=("front",)),
            lambda fs: fs["front"].rect(1, 4, 3, 5, H(skin_d)))))
        m.cube(side, (x0 - 0.5, 0, -3), (6, 4, 6), P(hoof, deco=chain(rim(lighten(H(hoof), 0.15), bottom=False),
                                                                     lambda fs: fs["front"].col(2, H("#0A0808")))))
        ank = side.replace("leg", "ankle")
        m.bone(ank, (sx, 6, 0), side)
        m.cube(ank, (x0 - 0.5, 5, -3), (6, 2, 6), P(iron, deco=chain(rim(iron_d), speckle("#7A5A40", 0.15, 3))), inflate=0.15)
    m.bone("body", (0, 20, 0), "root")
    m.cube("body", (-6, 18, -3.5), (12, 5, 7), P(cloth, deco=chain(band([0], "#5A3A1A"),
                                                                 lambda fs: fs["front"].rect(5, 0, 6, 0, H("#C9A23A")))))
    m.cube("body", (-3, 11, -4), (6, 8, 1), P("#3A1A1A", deco=hem("#5A2020", ragged=True, faces=("front", "back"), seed=2)))

    def chest(fs):
        f = fs["front"]
        # musculos marcados
        f.rect(2, 2, 6, 2, H(skin_d))
        f.rect(9, 2, 13, 2, H(skin_d))
        f.col(7, darken(H(skin), 0.2))
        f.col(8, darken(H(skin), 0.2))
        for yy in (6, 9):
            f.rect(5, yy, 10, yy, H(skin_d))
        # selo brilhante no peito
        for dx, dy in ((7, 4), (8, 4), (6, 5), (9, 5), (7, 6), (8, 6), (7, 3), (8, 7)):
            f.set(dx, dy, H(fire), True)
        fs["back"].tint(lambda c, x, y: H(skin_d) if x in (7, 8) else c)
    m.cube("body", (-8, 23, -4.5), (16, 14, 9), P(skin, deco=chain(chest, cracks(fire, 77, 3, faces=("back", "left", "right")))))
    # espinhos nas costas
    for i, y in enumerate((26, 30, 34)):
        m.cube("body", (-1, y, 4), (2, 2, 3), P(horn, faces={"front": "#3A3034"}), rotation=[35, 0, 0], pivot=[0, y, 4.5])
    # asas rasgadas
    for side, x0, sx, ry in (("right_wing", -17, -4, -25), ("left_wing", 4, 4, 25)):
        m.bone(side, (sx, 34, 4.5), "body", rotation=[10, ry, 0])

        def wing(fs, mirror=(side == "left_wing")):
            for n in ("front", "back"):
                f = fs[n]
                f.fill(H("#3A0A12"))
                rnd = random.Random(7 + len(side))
                for xx in range(f.w):
                    for yy in range(f.h):
                        if yy > f.h - 4 + (xx % 3) and rnd.random() < 0.8:
                            f.set(xx, yy, CLEAR)
                        elif rnd.random() < 0.05:
                            f.set(xx, yy, CLEAR)
                f.row(0, H(horn))
                for k in (3, 7, 10):
                    xx = (f.w - 1 - k) if not mirror else k
                    for yy in range(0, f.h - 2):
                        f.set(xx, yy, H("#2A1A1E"))
            for n in ("top", "bottom", "left", "right"):
                fs[n].fill(H(horn))
        m.cube(side, (x0, 25, 4.5), (13, 12, 1), P("#3A0A12", noise=0.04, deco=wing, grad=False))
    m.bone("tail", (0, 19, 3.5), "body", rotation=[-35, 0, 0])
    m.cube("tail", (-1, 18, 3.5), (2, 2, 10), P(skin, deco=cracks(fire, 5, 1, faces=("top",))))
    m.bone("tail_tip", (0, 19, 13.5), "tail")
    m.cube("tail_tip", (-0.5, 18.5, 13.5), (1, 1, 6), P(skin_d))
    m.cube("tail_tip", (-1.5, 18, 19), (3, 2, 3), P(horn), rotation=[0, 45, 0], pivot=[0, 19, 20.5])
    m.bone("head", (0, 37, -1), "body")

    def face(fs):
        f = fs["front"]
        f.rect(0, 0, f.w - 1, 1, H(skin_d))
        f.rect(1, 3, 3, 3, H("#1A0505"))
        f.rect(5, 3, 7, 3, H("#1A0505"))
        f.rect(1, 2, 3, 2, darken(H(skin), 0.35))
        f.rect(5, 2, 7, 2, darken(H(skin), 0.35))
        f.set(2, 3, H(eye), True)
        f.set(3, 3, H(fire), True)
        f.set(5, 3, H(fire), True)
        f.set(6, 3, H(eye), True)
        f.set(4, 4, H(skin_d))
        f.set(4, 5, H(skin_d))
        f.rect(1, 7, 7, 8, H("#1A0505"))
        for xx in (1, 3, 5, 7):
            f.set(xx, 7, H("#E8E0C8"))
        f.set(2, 8, H("#E8E0C8"))
        f.set(6, 8, H("#E8E0C8"))
        f.set(4, 8, H(fire), True)
    m.cube("head", (-4.5, 37, -6), (9, 9, 9), P(skin, deco=chain(face, cracks(fire, 9, 2, faces=("top", "back")))))
    # coleira de ferro com elo partido
    m.cube("head", (-5, 35.5, -6.5), (10, 2, 10), P(iron, deco=chain(rim(iron_d), speckle("#7A5A40", 0.12, 8))), inflate=0.2)
    m.cube("head", (-1, 32.5, -7), (2, 3, 1), P(iron_d))
    # chifres curvos
    for sx, x0, rz in ((-1, -8, 30), (1, 4, -30)):
        m.cube("head", (x0, 44, -3), (4, 3, 3), P(horn, deco=rim("#3A3034")), rotation=[0, 0, rz], pivot=[sx * 4, 44.5, -1.5])
        m.cube("head", (x0 + (-2 if sx < 0 else 2.5), 46.5, -2.5), (2, 5, 2), P(horn, faces={"top": "#4A4044"}),
               rotation=[-15, 0, rz * 0.6], pivot=[sx * 6, 47, -1.5])
        m.cube("head", (x0 + (-2.5 if sx < 0 else 3.5), 51, -1.5), (1, 4, 1), P("#4A4044", faces={"top": "#8A8084"}),
               rotation=[-35, 0, rz * 0.2], pivot=[sx * 6.5, 51, -1])
    # bracos enormes
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        x_sh = -13.5 if sx < 0 else 8.5
        m.bone(side, (sx * 9.5, 34, 0), "body")
        m.cube(side, (x_sh, 31, -3.5), (5, 6, 7), P(skin_d, deco=chain(cracks(fire, 13 + sx, 1, faces=("top", "front")),
                                                                      lambda fs: fs["top"].scatter(H(horn), 0.15, 4))), inflate=0.2)
        m.cube(side, (x_sh + 0.5, 22, -2), (4, 10, 4), P(skin, deco=cracks(fire, 17 + sx, 1, faces=("front", "back"))))
        fa = side.replace("arm", "forearm")
        m.bone(fa, (sx * 11, 22.5, 0), side)
        m.cube(fa, (x_sh, 13, -2.5), (5, 10, 5), P(skin, deco=chain(cracks(fire, 21 + sx, 2, faces=("front", "left", "right")),
                                                                   lambda fs: fs["front"].row(0, H(skin_d)))))
        m.cube(fa, (x_sh, 10, -2.5), (5, 3, 5), P(skin_d, deco=lambda fs: [fs["front"].set(x, fs["front"].h - 1, H("#E8E0C8"))
                                                                          for x in (0, 2, 4)]))
        cuff = side.replace("arm", "cuff")
        m.bone(cuff, (sx * 11, 15, 0), fa)
        m.cube(cuff, (x_sh - 0.5, 14, -3), (6, 3, 6), P(iron, deco=chain(rim(iron_d), speckle("#7A5A40", 0.15, 6),
                                                                        lambda fs: fs["front"].set(2, 1, H("#2A2A30")))), inflate=0.15)
    m.save(bounds=(5, 5), seed=41)

    a = {}
    a["idle"] = anim(3.0, {
        "body": {"rotation": cyc(3.0, lambda p: [2 * S(p), 2 * S(p * 0.5), 0]), "position": cyc(3.0, lambda p: [0, 0.4 * S(p), 0])},
        "right_arm": {"rotation": cyc(3.0, lambda p: [-8 + 6 * S(p), 0, 12 + 6 * S(p)])},
        "left_arm": {"rotation": cyc(3.0, lambda p: [-8 - 6 * S(p), 0, -12 - 6 * S(p)])},
        "right_forearm": {"rotation": cyc(3.0, lambda p: [-15 - 5 * S(p), 0, 0])},
        "left_forearm": {"rotation": cyc(3.0, lambda p: [-15 + 5 * S(p), 0, 0])},
        "head": {"rotation": cyc(3.0, lambda p: [6 * S(p), 10 * S(p * 0.5), 0])},
        "right_wing": {"rotation": cyc(3.0, lambda p: [0, -6 * S(p), 0])},
        "left_wing": {"rotation": cyc(3.0, lambda p: [0, 6 * S(p), 0])},
        "tail": {"rotation": cyc(3.0, lambda p: [4 * S(p), 15 * S(p), 0])},
    }, loop=True)
    a["walk"] = anim(1.4, {
        "right_leg": {"rotation": cyc(1.4, lambda p: [24 * S(p), 0, 0])},
        "left_leg": {"rotation": cyc(1.4, lambda p: [-24 * S(p), 0, 0])},
        "right_arm": {"rotation": cyc(1.4, lambda p: [-16 * S(p), 0, 8])},
        "left_arm": {"rotation": cyc(1.4, lambda p: [16 * S(p), 0, -8])},
        "body": {"position": cyc(1.4, lambda p: [0, 0.8 * abs(C(p)), 0]), "rotation": cyc(1.4, lambda p: [6, 3 * S(p), 2 * S(p)])},
        "tail": {"rotation": cyc(1.4, lambda p: [0, 20 * S(p), 0])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [30, 0, 70]), (1.0, [20, 0, 60]), (1.4, [35, 0, 75]), (2.4, [-40, 0, 80]), (3.0, [-8, 0, 12])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [30, 0, -70]), (1.0, [20, 0, -60]), (1.4, [35, 0, -75]), (2.4, [-40, 0, -80]), (3.0, [-8, 0, -12])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [12, 0, 0]), (1.4, [16, 0, 0]), (2.0, [-15, 0, 0]), (2.6, [-18, 0, 0]), (3.0, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.4, [20, 0, 0]), (2.0, [-35, 0, 0]), (2.6, [-38, 0, 0]), (3.0, [0, 0, 0])])},
        "right_wing": {"rotation": keys([(0, [0, 0, 0]), (2.0, [0, -40, 0]), (2.6, [0, -45, 0]), (3.0, [0, 0, 0])])},
        "left_wing": {"rotation": keys([(0, [0, 0, 0]), (2.0, [0, 40, 0]), (2.6, [0, 45, 0]), (3.0, [0, 0, 0])])},
    })
    a["death"] = anim(2.5, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-15, 0, 0]), (1.4, [25, 0, 0]), (2.5, [55, 0, 5])]),
                 "position": keys([(0, [0, 0, 0]), (1.4, [0, -6, 0]), (2.5, [0, -14, -3])])},
        "right_leg": {"rotation": keys([(0, [0, 0, 0]), (1.4, [-70, 0, 10]), (2.5, [-85, 0, 12])]),
                      "position": keys([(0, [0, 0, 0]), (2.5, [0, -4, -2])])},
        "left_leg": {"rotation": keys([(0, [0, 0, 0]), (1.4, [-70, 0, -10]), (2.5, [-85, 0, -12])]),
                     "position": keys([(0, [0, 0, 0]), (2.5, [0, -4, -2])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-40, 0, 0]), (2.5, [35, 0, 10])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-30, 0, 15])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-30, 0, -15])])},
    })
    a["swipe"] = anim(0.8, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-150, -20, 40]), (0.5, [-40, 30, -20]), (0.8, [0, 0, 0])])},
        "right_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-40, 0, 0]), (0.5, [-5, 0, 0]), (0.8, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-6, 20, 0]), (0.5, [10, -25, 0]), (0.8, [0, 0, 0])])},
    })
    a["cast"] = anim(0.9, {
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-160, 0, -10]), (0.55, [-80, 0, 0]), (0.9, [0, 0, 0])])},
        "left_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-50, 0, 0]), (0.55, [0, 0, 0]), (0.9, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-8, -15, 0]), (0.55, [8, 15, 0]), (0.9, [0, 0, 0])])},
    })
    a["grip"] = anim(1.0, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-95, 0, 0]), (0.6, [-90, 0, 0]), (0.8, [-20, 0, 10]), (1.0, [0, 0, 0])])},
        "right_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [0, 0, 0]), (0.6, [-10, 0, 0]), (0.8, [-70, 0, 0]), (1.0, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.35, [10, 0, 0]), (0.8, [-12, 0, 0]), (1.0, [0, 0, 0])])},
    })
    a["roar"] = anim(1.5, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-18, 0, 0]), (1.2, [-20, 0, 0]), (1.5, [0, 0, 0])]),
                 "position": cyc(1.5, lambda p: [0.3 * S(p * 12), 0, 0], steps=24)},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 0]), (1.2, [-32, 0, 0]), (1.5, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 70]), (1.2, [-35, 0, 75]), (1.5, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, -70]), (1.2, [-35, 0, -75]), (1.5, [0, 0, 0])])},
        "right_wing": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, -45, 0]), (1.2, [0, -50, 0]), (1.5, [0, 0, 0])])},
        "left_wing": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 45, 0]), (1.2, [0, 50, 0]), (1.5, [0, 0, 0])])},
    })
    a["nova"] = anim(1.4, {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.7, [20, 0, 0]), (0.9, [-10, 0, 0]), (1.4, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.7, [0, -3, 0]), (0.9, [0, 1, 0]), (1.4, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.7, [-170, 0, 20]), (0.9, [-20, 0, 60]), (1.4, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.7, [-170, 0, -20]), (0.9, [-20, 0, -60]), (1.4, [0, 0, 0])])},
    })
    # Corrente Infernal (v0.7): gira o braco direito para tras puxando a corrente e chicoteia para a frente
    # no fim do aviso (1.1 s = 22 ticks).
    a["chain_lash"] = anim(1.3, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-160, -30, 50]), (0.9, [-170, -35, 55]), (1.1, [-40, 40, -30]), (1.3, [0, 0, 0])])},
        "right_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-60, 0, 0]), (0.9, [-65, 0, 0]), (1.1, [0, 0, 0]), (1.3, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-20, 0, -40]), (1.1, [10, 0, -20]), (1.3, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-8, 30, 0]), (0.9, [-10, 35, 0]), (1.1, [12, -30, 0]), (1.3, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.6, [-15, 10, 0]), (1.1, [10, -10, 0]), (1.3, [0, 0, 0])])},
        "right_wing": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, -35, 0]), (1.1, [0, 10, 0]), (1.3, [0, 0, 0])])},
        "left_wing": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, 35, 0]), (1.1, [0, -10, 0]), (1.3, [0, 0, 0])])},
        "tail": {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, -25, 0]), (1.1, [0, 30, 0]), (1.3, [0, 0, 0])])},
    })
    save_anims("imprisoned_patron", a)


# =====================================================================================
#  ENT CORROMPIDO
# =====================================================================================
def build_ent():
    m = Model("corrupted_ent")
    bark_c, bark_d, bark_l, moss, leaf, leaf_d = "#4A3526", "#2E2018", "#6A5038", "#4E6A2A", "#3E5A26", "#26381A"
    corr, corr_g, fung = "#7A2F9A", "#C66BFF", "#B4FF6A"
    m.bone("root", (0, 0, 0))
    for side, x0, sx in (("right_leg", -9, -5), ("left_leg", 1, 5)):
        m.bone(side, (sx, 20, 0), "root")
        m.cube(side, (x0, 4, -4), (8, 16, 8), P(bark_c, deco=chain(bark(bark_d, bark_l, 3 + sx),
                                                                   speckle(moss, 0.06, 4 + sx, faces=("front", "left", "right", "top")))))
        m.cube(side, (x0 - 1.5, 0, -6), (11, 4, 10), P(bark_d, deco=chain(bark("#1E1610", bark_c, 8 + sx, faces=SIDES + ("top",)),
                                                                          speckle(moss, 0.12, 9, faces=("top",)))))
        for k, (dx, dz, ry) in enumerate(((-2, -8.5, 20), (4, -8.5, -15), (sx * 3, 3, 40 * sx))):
            m.cube(side, (x0 + 3 + dx, 0, dz), (2, 2, 5), P(bark_d, deco=speckle(moss, 0.2, k)), rotation=[0, ry, 0],
                   pivot=[x0 + 4 + dx, 1, dz + 2.5])
    m.bone("body", (0, 20, 0), "root")

    def trunk_low(fs):
        bark(bark_d, bark_l, 21)(fs)
        cracks(corr_g, 22, 3, faces=("front", "back"))(fs)
        speckle(moss, 0.05, 23)(fs)
    m.cube("body", (-9, 20, -7), (18, 20, 14), P(bark_c, deco=trunk_low))
    m.bone("trunk_upper", (0, 40, 0), "body")

    def face(fs):
        bark(bark_d, bark_l, 31)(fs)
        f = fs["front"]
        # olhos ocos brilhando
        for ex in (4, 13):
            f.rect(ex, 5, ex + 2, 7, H("#120A10"))
            f.set(ex + 1, 6, H(corr_g), True)
            f.set(ex, 6, H(corr), True)
            f.set(ex + 2, 6, H(corr), True)
            f.set(ex + 1, 5, H(corr), True)
        # sobrancelhas de casca
        f.rect(3, 3, 7, 3, H(bark_d))
        f.rect(12, 3, 16, 3, H(bark_d))
        # boca rachada
        for xx in range(5, 15):
            yy = 11 + (1 if xx % 3 == 0 else 0)
            f.set(xx, yy, H("#120A10"))
            f.set(xx, yy + 1, H(corr), True)
        f.set(9, 13, H(corr_g), True)
        f.set(10, 13, H(corr_g), True)
        # nariz/no
        f.rect(9, 8, 10, 9, H(bark_l))
        cracks(corr_g, 33, 2, faces=("left", "right", "back"))(fs)
        speckle(moss, 0.08, 34, faces=("top", "left", "right", "back"))(fs)
    m.cube("trunk_upper", (-10, 40, -8), (20, 16, 16), P(bark_c, deco=face))
    m.bone("crown", (0, 56, 0), "trunk_upper")

    def leaves(seed):
        def d(fs):
            rnd = random.Random(seed)
            for n, f in fs.items():
                for yy in range(f.h):
                    for xx in range(f.w):
                        r = rnd.random()
                        if r < 0.22:
                            f.set(xx, yy, H(leaf_d))
                        elif r < 0.32:
                            f.set(xx, yy, lighten(H(leaf), 0.15))
                        elif r < 0.37:
                            f.set(xx, yy, H(corr))
                        elif r < 0.39:
                            f.set(xx, yy, H(corr_g), True)
                if n in SIDES:
                    for xx in range(f.w):
                        if rnd.random() < 0.4:
                            f.set(xx, f.h - 1, CLEAR)
        return d
    m.cube("crown", (-14, 56, -11), (28, 10, 22), P(leaf, deco=leaves(41), noise=0.08))
    m.cube("crown", (-10, 66, -8), (20, 6, 16), P(leaf, deco=leaves(42), noise=0.08))
    m.cube("crown", (-17, 58, -5), (4, 7, 10), P(leaf, deco=leaves(43)))
    m.cube("crown", (13, 58, -5), (4, 7, 10), P(leaf, deco=leaves(44)))
    for k, (x, rz, rx) in enumerate(((-9, 25, -10), (7, -30, 8), (0, 5, 25))):
        m.cube("crown", (x, 64, -1), (2, 10, 2), P(bark_d, deco=bark("#1A120C", bark_c, 50 + k)), rotation=[rx, 0, rz], pivot=[x + 1, 64, 0])
    # flores de fungo corrompido (so na fase 2)
    m.bone("blossoms", (0, 56, 0), "crown")
    for k, (x, y, z) in enumerate(((-11, 66, -9), (6, 66, -9), (-3, 72, -5), (9, 63, 6), (-13, 63, 4), (2, 72, 3))):
        m.cube("blossoms", (x, y, z), (3, 2, 3), P(corr_g, deco=glow_faces(corr_g, core="#F2C8FF")))
        m.cube("blossoms", (x + 1, y + 2, z + 1), (1, 1, 1), P(fung, deco=glow_faces(fung)))
    # bracos de galho
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        x0 = -16 if sx < 0 else 10
        m.bone(side, (sx * 11, 51, 0), "trunk_upper")
        m.cube(side, (x0, 36, -3.5), (6, 16, 7), P(bark_c, deco=chain(bark(bark_d, bark_l, 60 + sx), speckle(moss, 0.08, 61))),
               rotation=[0, 0, -8 * sx], pivot=[sx * 11, 51, 0])
        fa = side.replace("arm", "forearm")
        m.bone(fa, (sx * 14, 37, 0), side)
        m.cube(fa, (x0 + 0.5, 22, -3), (5, 15, 6), P(bark_c, deco=chain(bark(bark_d, bark_l, 62 + sx),
                                                                        cracks(corr_g, 63 + sx, 1, faces=("front",)))))
        for k, dz in enumerate((-3, -0.5, 2)):
            m.cube(fa, (x0 + 1.5, 15, dz), (2, 7, 2), P(bark_d, faces={"bottom": corr}, deco=lambda fs: fs["bottom"].fill(H(corr))),
                   rotation=[(k - 1) * 18, 0, 6 * sx], pivot=[x0 + 2.5, 22, dz + 1])
        # galho espinhoso saindo do ombro
        m.cube(side, (x0 + (0 if sx < 0 else 4), 50, -1), (2, 7, 2), P(bark_d, faces={"top": corr_g},
                                                                       deco=lambda fs: fs["top"].set(0, 0, H(corr_g), True)),
               rotation=[0, 0, 35 * -sx], pivot=[x0 + 1, 50, 0])
    m.save(bounds=(6, 6), seed=51)

    a = {}
    a["idle"] = anim(4.0, {
        "body": {"rotation": cyc(4.0, lambda p: [1.2 * S(p), 0, 2 * S(p)])},
        "crown": {"rotation": cyc(4.0, lambda p: [1.5 * S(p + 0.2), 0, 3 * S(p + 0.25)])},
        "right_arm": {"rotation": cyc(4.0, lambda p: [-6 + 3 * S(p), 0, 4 + 2 * S(p)])},
        "left_arm": {"rotation": cyc(4.0, lambda p: [-6 - 3 * S(p), 0, -4 - 2 * S(p)])},
        "right_forearm": {"rotation": cyc(4.0, lambda p: [-12 + 3 * S(p), 0, 0])},
        "left_forearm": {"rotation": cyc(4.0, lambda p: [-12 - 3 * S(p), 0, 0])},
    }, loop=True)
    a["walk"] = anim(2.0, {
        "right_leg": {"rotation": cyc(2.0, lambda p: [18 * S(p), 0, 0])},
        "left_leg": {"rotation": cyc(2.0, lambda p: [-18 * S(p), 0, 0])},
        "right_arm": {"rotation": cyc(2.0, lambda p: [-12 * S(p) - 6, 0, 4])},
        "left_arm": {"rotation": cyc(2.0, lambda p: [12 * S(p) - 6, 0, -4])},
        "body": {"rotation": cyc(2.0, lambda p: [4, 0, 3 * S(p)]), "position": cyc(2.0, lambda p: [0, 1.0 * abs(C(p)), 0])},
        "crown": {"rotation": cyc(2.0, lambda p: [0, 0, -3 * S(p)])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "body": {"rotation": keys([(0, [25, 0, 0]), (1.2, [10, 0, 0]), (2.0, [-12, 0, 0]), (2.6, [-14, 0, 0]), (3.0, [0, 0, 0])]),
                 "position": keys([(0, [0, -8, 0]), (1.2, [0, -2, 0]), (2.0, [0, 1, 0]), (3.0, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [-40, 0, 0]), (1.2, [-20, 0, 30]), (2.0, [-60, 0, 60]), (2.6, [-60, 0, 62]), (3.0, [-6, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [-40, 0, 0]), (1.2, [-20, 0, -30]), (2.0, [-60, 0, -60]), (2.6, [-60, 0, -62]), (3.0, [-6, 0, -4])])},
        "crown": {"rotation": cyc(3.0, lambda p: [0, 0, 4 * S(p * 6) * (1 if p > 0.6 else 0.3)], steps=24)},
    })
    a["death"] = anim(2.5, {
        "root": {"rotation": keys([(0, [0, 0, 0]), (0.6, [6, 0, 0]), (1.6, [-45, 0, 6]), (2.2, [-86, 0, 8]), (2.5, [-82, 0, 8])]),
                 "position": keys([(0, [0, 0, 0]), (2.2, [0, 0, 6]), (2.5, [0, 0, 6])])},
        "crown": {"rotation": keys([(0, [0, 0, 0]), (2.2, [10, 0, 0]), (2.5, [14, 0, 4])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-150, 0, 40])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (2.5, [-140, 0, -40])])},
    })
    a["slam"] = anim(1.1, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-160, 0, 0]), (0.68, [-35, 0, 0]), (0.9, [-35, 0, 0]), (1.1, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-160, 0, 0]), (0.68, [-35, 0, 0]), (0.9, [-35, 0, 0]), (1.1, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.68, [16, 0, 0]), (0.9, [14, 0, 0]), (1.1, [0, 0, 0])])},
    })
    a["roots"] = anim(1.3, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-110, 0, 10]), (0.65, [-50, 0, 0]), (1.0, [-50, 0, 0]), (1.3, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-110, 0, -10]), (0.65, [-50, 0, 0]), (1.0, [-50, 0, 0]), (1.3, [0, 0, 0])])},
        "right_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.65, [-40, 0, 0]), (1.0, [-40, 0, 0]), (1.3, [0, 0, 0])])},
        "left_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.65, [-40, 0, 0]), (1.0, [-40, 0, 0]), (1.3, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.65, [24, 0, 0]), (1.0, [24, 0, 0]), (1.3, [0, 0, 0])])},
    })
    a["spores"] = anim(1.2, {
        "crown": {"rotation": cyc(1.2, lambda p: [3 * S(p * 5), 0, 7 * S(p * 6)], steps=24)},
        "body": {"rotation": cyc(1.2, lambda p: [0, 0, 2 * S(p * 6)], steps=24)},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 40]), (0.9, [-30, 0, 42]), (1.2, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, -40]), (0.9, [-30, 0, -42]), (1.2, [0, 0, 0])])},
    })
    a["roar"] = anim(2.0, {
        "trunk_upper": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-16, 0, 0]), (1.6, [-18, 0, 0]), (2.0, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-50, 0, 70]), (1.6, [-55, 0, 75]), (2.0, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-50, 0, -70]), (1.6, [-55, 0, -75]), (2.0, [0, 0, 0])])},
        "crown": {"rotation": cyc(2.0, lambda p: [0, 0, 5 * S(p * 10)], steps=30)},
        "body": {"position": cyc(2.0, lambda p: [0.4 * S(p * 14), 0, 0], steps=28)},
    })
    # Raizes em Linha (v0.7): ergue os bracos e os crava no chao a frente (0.8 s = 16 ticks), segurando enquanto
    # a fileira de raizes corre pela terra.
    a["root_line"] = anim(1.4, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, 15]), (0.8, [-35, 0, 5]), (1.1, [-38, 0, 5]), (1.4, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-170, 0, -15]), (0.8, [-35, 0, -5]), (1.1, [-38, 0, -5]), (1.4, [0, 0, 0])])},
        "right_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (0.8, [-20, 0, 0]), (1.1, [-20, 0, 0]), (1.4, [0, 0, 0])])},
        "left_forearm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (0.8, [-20, 0, 0]), (1.1, [-20, 0, 0]), (1.4, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.8, [28, 0, 0]), (1.1, [30, 0, 0]), (1.4, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.8, [0, -2, 0]), (1.1, [0, -2, 0]), (1.4, [0, 0, 0])])},
        "crown": {"rotation": cyc(1.4, lambda p: [0, 0, (6 if 0.55 < p < 0.85 else 2) * S(p * 10)], steps=28)},
    })
    save_anims("corrupted_ent", a)


# =====================================================================================
#  RAIZ DO ENT
# =====================================================================================
def build_root():
    m = Model("corrupted_ent_root")
    bark_c, bark_d, corr, corr_g = "#3E2C20", "#22170F", "#7A2F9A", "#C66BFF"
    m.bone("root", (0, 0, 0))
    spikes = (("spike_a", (0, 0, 0), (-1.5, -2, -1.5), (3, 16, 3), None),
              ("spike_b", (2, 0, 1), (1, -2, 0), (2, 12, 2), [12, 0, -22]),
              ("spike_c", (-2, 0, 1), (-3, -2, 0), (2, 11, 2), [-8, 0, 24]),
              ("spike_d", (0, 0, -2), (-1, -2, -3), (2, 10, 2), [-24, 0, 4]))
    for k, (name, piv, org, size, rot) in enumerate(spikes):
        m.bone(name, piv, "root", rotation=rot)
        m.cube(name, org, size, P(bark_c, deco=chain(bark(bark_d, "#5A4232", 70 + k), cracks(corr_g, 71 + k, 1, faces=("front", "left")))))
        tip_w = max(1, size[0] - 1)
        m.cube(name, (org[0] + (size[0] - tip_w) / 2.0, org[1] + size[1], org[2] + (size[2] - tip_w) / 2.0), (tip_w, 4, tip_w),
               P(bark_d, faces={"top": corr}, deco=lambda fs: fs["top"].fill(H(corr_g)) or [fs[n].set(0, 0, H(corr), True) for n in SIDES]))
        # espinhos laterais
        m.cube(name, (org[0] - 1, org[1] + size[1] * 0.6, org[2] + 0.5), (1, 1, 1), P(corr, deco=glow_faces(corr_g)))
    m.save(bounds=(2, 2), seed=61)
    sp = ("spike_a", "spike_b", "spike_c", "spike_d")
    a = {}
    a["emerge"] = anim(1.0, {n: {"position": cyc(1.0, lambda p, k=k: [0, -15 + 1.2 * abs(S(p * 2 + k * 0.2)), 0])} for k, n in enumerate(sp)}, loop=True)
    a["erupt"] = anim(0.3, {n: {"position": keys([(0, [0, -15, 0]), (0.12 + 0.03 * k, [0, 1.5, 0]), (0.3, [0, 0, 0])])} for k, n in enumerate(sp)})
    a["retract"] = anim(0.6, {n: {"position": keys([(0, [0, 0, 0]), (0.15, [0, 0.8, 0]), (0.6, [0, -17, 0])])} for n in sp})
    save_anims("corrupted_ent_root", a)


# =====================================================================================
#  LICH
# =====================================================================================
def build_lich():
    m = Model("lich")
    bone_c, bone_d, robe, robe_d, gold, soul = "#E6E0CC", "#A89F86", "#2A2A3A", "#17171F", "#B8963A", "#5FD8E8"
    soul_l, staff_c = "#C8FBFF", "#3A2A22"
    m.bone("root", (0, 0, 0))
    m.bone("robe", (0, 16, 0), "root")

    def wisps(fs):
        rnd = random.Random(4)
        for n in SIDES:
            f = fs[n]
            for xx in range(f.w):
                cut = rnd.randint(0, f.h - 1)
                for yy in range(cut, f.h):
                    f.set(xx, yy, CLEAR)
                if cut > 0:
                    f.set(xx, cut - 1, H(soul), True)
        fs["bottom"].fill(CLEAR)
    m.cube("robe", (-4.5, 4, -3), (9, 12, 6), P(robe, deco=chain(folds(darken(H(robe), 0.3), step=2), hem(robe_d, ragged=True, seed=14),
                                                               lambda fs: fs["front"].col(4, H(gold)))))
    m.cube("robe", (-3.5, 0, -2), (7, 4, 4), P(robe_d, deco=wisps, grad=False))
    m.bone("body", (0, 16, 0), "root")

    def ribs(fs):
        f = fs["front"]
        # manto aberto mostrando as costelas e a alma
        f.rect(2, 1, 5, 8, H("#0E0E14"))
        for yy in (2, 4, 6):
            f.rect(2, yy, 5, yy, H(bone_c))
        f.col(3, H(bone_d))
        f.rect(3, 3, 4, 5, H(soul), True)
        f.set(3, 4, H(soul_l), True)
        f.col(1, H(gold))
        f.col(6, H(gold))
        f.row(f.h - 2, H("#3A2A1A"))
        f.set(3, f.h - 2, H(gold))
        f.set(4, f.h - 2, H(gold))
    m.cube("body", (-4, 16, -2.5), (8, 11, 5), P(robe, deco=ribs))
    # ombreiras de osso
    for x0, rz in ((-6, 12), (3, -12)):
        m.cube("body", (x0, 24, -3), (3, 3, 6), P(bone_c, deco=chain(rim(bone_d), speckle(bone_d, 0.1, 5))), rotation=[0, 0, rz],
               pivot=[x0 + 1.5, 25, 0], inflate=0.1)
    # gola alta
    m.cube("body", (-4.5, 25, 1.5), (9, 7, 2), P(robe_d, deco=chain(rim(gold, bottom=False), folds(darken(H(robe_d), 0.2), step=2))),
           rotation=[-12, 0, 0], pivot=[0, 26, 2])
    m.bone("head", (0, 27, 0), "body")

    def skull(fs):
        f = fs["front"]
        f.rect(1, 2, 2, 3, H("#0E0E14"))
        f.rect(4, 2, 5, 3, H("#0E0E14"))
        f.set(1, 3, H(soul), True)
        f.set(5, 3, H(soul), True)
        f.set(2, 2, H(soul_l), True)
        f.set(4, 2, H(soul_l), True)
        f.set(3, 4, H("#1A1A20"))
        f.rect(1, 6, 5, 6, H("#1A1A20"))
        for xx in (1, 2, 4, 5):
            f.set(xx, 6, H(bone_c))
        for n in ("left", "right"):
            fs[n].set(2, 3, H(bone_d))
            fs[n].set(3, 3, H(bone_d))
        fs["top"].scatter(H(bone_d), 0.12, 3)
        fs["back"].scatter(H(bone_d), 0.1, 4)
    m.cube("head", (-3.5, 27, -3.5), (7, 7, 7), P(bone_c, deco=skull, noise=0.04))
    m.bone("jaw", (0, 27.5, -1), "head")
    m.cube("jaw", (-3, 25.5, -3.8), (6, 2, 4), P(bone_c, deco=lambda fs: [fs["front"].set(x, 0, H(bone_d)) for x in (1, 4)]))
    # coroa
    m.cube("head", (-4, 33.5, -4), (8, 2, 8), P(gold, deco=chain(rim(darken(H(gold), 0.3)),
                                                               lambda fs: fs["front"].rect(3, 0, 4, 1, H(soul), True))), inflate=0.15)
    for k, (x, z) in enumerate(((-4, -4), (-0.5, -4.4), (3, -4), (-4, 3), (3, 3))):
        m.cube("head", (x, 35.5, z), (1, 3 if k != 1 else 4, 1), P(gold, faces={"top": soul}, deco=lambda fs: fs["top"].set(0, 0, H(soul_l), True)))
    # bracos
    for side, x0, sx in (("right_arm", -7.5, -5), ("left_arm", 4.5, 5)):
        m.bone(side, (sx, 25, 0), "body")
        m.cube(side, (x0, 15, -2), (3, 10, 4), P(robe, deco=chain(hem(robe_d, ragged=True, seed=20 + sx), band([0], robe_d))))
        m.cube(side, (x0 + 0.5, 12, -1), (2, 3, 2), P(bone_c, deco=chain(lambda fs: fs["bottom"].fill(H(bone_d)),
                                                                        lambda fs: fs["front"].col(1, H(bone_d)))))
        if side == "left_arm":
            m.cube(side, (x0 + 0.5, 10.5, -1.5), (2, 1, 3), P(soul, deco=glow_faces(soul, core=soul_l)))
    # cajado de caveira
    m.bone("staff", (-6, 13.5, 0), "right_arm")
    m.cube("staff", (-6.5, 0, -0.5), (1, 30, 1), P(staff_c, deco=lambda fs: [fs[n].set(0, y, H(bone_c)) for n in SIDES for y in (6, 7, 16, 17)]))
    m.cube("staff", (-7.5, 29, -1.5), (3, 3, 3), P(bone_c, deco=lambda fs: (fs["front"].set(0, 1, H("#0E0E14")), fs["front"].set(2, 1, H("#0E0E14")),
                                                                           fs["front"].set(0, 1, H(soul), True), fs["front"].set(2, 1, H(soul), True))))
    m.cube("staff", (-7, 32, -1), (2, 2, 2), P(soul, deco=glow_faces(soul, core=soul_l)))
    m.cube("staff", (-8.5, 30, -0.5), (1, 5, 1), P(bone_d), rotation=[0, 0, 18], pivot=[-8, 30, 0])
    m.cube("staff", (-4.5, 30, -0.5), (1, 5, 1), P(bone_d), rotation=[0, 0, -18], pivot=[-4, 30, 0])
    m.save(bounds=(4, 4), seed=71)

    a = {}
    a["idle"] = anim(3.0, {
        "root": {"position": cyc(3.0, lambda p: [0, 2.0 + 1.2 * S(p), 0])},
        "robe": {"rotation": cyc(3.0, lambda p: [3 * S(p + 0.2), 0, 2 * S(p)])},
        "head": {"rotation": cyc(3.0, lambda p: [3 * S(p), 6 * S(p * 0.5), 0])},
        "jaw": {"rotation": cyc(3.0, lambda p: [4 + 4 * S(p * 2), 0, 0])},
        "right_arm": {"rotation": cyc(3.0, lambda p: [-14 + 3 * S(p), 0, 4])},
        "left_arm": {"rotation": cyc(3.0, lambda p: [-20 - 5 * S(p), 10, -8])},
    }, loop=True)
    a["walk"] = anim(2.0, {
        "root": {"position": cyc(2.0, lambda p: [0, 2.0 + 1.0 * S(p), 0])},
        "body": {"rotation": cyc(2.0, lambda p: [8, 0, 2 * S(p)])},
        "robe": {"rotation": cyc(2.0, lambda p: [18 + 4 * S(p), 0, 3 * S(p)])},
        "right_arm": {"rotation": cyc(2.0, lambda p: [-14 - 6 * S(p), 0, 4])},
        "left_arm": {"rotation": cyc(2.0, lambda p: [6 * S(p) - 10, 0, -8])},
    }, loop=True)
    a["intro"] = anim(3.0, {
        "root": {"position": keys([(0, [0, -6, 0]), (1.0, [0, 2, 0]), (2.0, [0, 5, 0]), (3.0, [0, 2, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-60, 0, 30]), (2.0, [-170, 0, 10]), (2.5, [-170, 0, 10]), (3.0, [-14, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-60, 0, -60]), (2.0, [-70, 0, -80]), (2.5, [-70, 0, -80]), (3.0, [-20, 10, -8])])},
        "head": {"rotation": keys([(0, [30, 0, 0]), (1.0, [10, 0, 0]), (2.0, [-25, 0, 0]), (2.5, [-25, 0, 0]), (3.0, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [0, 0, 0]), (2.0, [30, 0, 0]), (2.5, [32, 0, 0]), (3.0, [4, 0, 0])])},
    })
    collapse = {
        "head": {"position": keys([(0, [0, 0, 0]), (0.3, [0, 2, -1]), (1.2, [1, -24, -2])]),
                 "rotation": keys([(0, [0, 0, 0]), (1.2, [70, 30, 20])])},
        "body": {"position": keys([(0, [0, 0, 0]), (1.2, [0, -12, 0])]),
                 "rotation": keys([(0, [0, 0, 0]), (1.2, [30, 0, 10])])},
        "robe": {"scale": keys([(0, [1, 1, 1]), (1.2, [1.3, 0.2, 1.3])]), "position": keys([(0, [0, 0, 0]), (1.2, [0, -2, 0])])},
        "root": {"position": keys([(0, [0, 2, 0]), (1.2, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-40, 0, 70])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-40, 0, -70])])},
        "staff": {"rotation": keys([(0, [0, 0, 0]), (1.2, [80, 0, 0])])},
    }
    a["death"] = anim(1.5, collapse)
    a["collapse"] = anim(1.5, collapse)

    def rev(track):
        out = {}
        for ch, kv in track.items():
            items = sorted(((float(t), v) for t, v in kv.items()))
            T = 1.2
            out[ch] = keys([(T - t + 0.3, v) for t, v in items] + [(0, items[-1][1])])
        return out
    a["reform"] = anim(1.5, {b: rev(tr) for b, tr in collapse.items()})
    a["cast"] = anim(0.8, {
        "right_arm": {"rotation": keys([(0, [-14, 0, 4]), (0.3, [-120, 0, 8]), (0.5, [-75, 0, 0]), (0.8, [-14, 0, 4])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-6, -10, 0]), (0.5, [6, 8, 0]), (0.8, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.4, [25, 0, 0]), (0.8, [4, 0, 0])])},
    })
    a["summon"] = anim(1.6, {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-160, 0, 20]), (1.2, [-160, 0, 25]), (1.6, [-14, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-160, 0, -30]), (1.2, [-160, 0, -35]), (1.6, [-20, 10, -8])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.5, [-30, 0, 0]), (1.2, [-30, 0, 0]), (1.6, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.5, [30, 0, 0]), (1.2, [30, 0, 0]), (1.6, [4, 0, 0])])},
        "root": {"position": keys([(0, [0, 2, 0]), (0.5, [0, 5, 0]), (1.2, [0, 5, 0]), (1.6, [0, 2, 0])])},
    })
    a["drain"] = anim(2.0, {
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-95, 0, 0]), (1.7, [-92, 0, 4]), (2.0, [-20, 10, -8])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (1.7, [-12, 0, 0]), (2.0, [0, 0, 0])])},
        "jaw": {"rotation": cyc(2.0, lambda p: [12 + 8 * S(p * 4), 0, 0], steps=16)},
    })
    a["wail"] = anim(1.4, {
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-35, 0, 0]), (1.1, [-38, 0, 0]), (1.4, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.3, [45, 0, 0]), (1.1, [45, 0, 0]), (1.4, [4, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-20, 0, 80]), (1.1, [-25, 0, 85]), (1.4, [-14, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-20, 0, -80]), (1.1, [-25, 0, -85]), (1.4, [-20, 10, -8])])},
        "body": {"position": cyc(1.4, lambda p: [0.3 * S(p * 14), 0, 0], steps=28)},
    })
    # Circulo Necrotico (v0.7): abre os bracos com as palmas para baixo (o anel aparece) e os ergue devagar,
    # flutuando mais alto, mandibula aberta, puxando as almas (1.0 s = 20 ticks de conjuracao).
    a["necrotic"] = anim(1.5, {
        "right_arm": {"rotation": keys([(0, [-14, 0, 4]), (0.4, [-40, 0, 60]), (1.0, [-150, 0, 35]), (1.2, [-150, 0, 35]), (1.5, [-14, 0, 4])])},
        "left_arm": {"rotation": keys([(0, [-20, 10, -8]), (0.4, [-40, 0, -60]), (1.0, [-150, 0, -35]), (1.2, [-150, 0, -35]), (1.5, [-20, 10, -8])])},
        "root": {"position": keys([(0, [0, 2, 0]), (0.4, [0, 2.5, 0]), (1.0, [0, 5, 0]), (1.2, [0, 5, 0]), (1.5, [0, 2, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (0.4, [15, 0, 0]), (1.0, [-30, 0, 0]), (1.2, [-30, 0, 0]), (1.5, [0, 0, 0])])},
        "jaw": {"rotation": keys([(0, [4, 0, 0]), (0.4, [10, 0, 0]), (1.0, [35, 0, 0]), (1.2, [35, 0, 0]), (1.5, [4, 0, 0])])},
        "robe": {"rotation": cyc(1.5, lambda p: [6 * S(p * 3), 0, 4 * S(p * 4)], steps=24)},
    })
    save_anims("lich", a)


# =====================================================================================
#  FILACTERIO DO LICH
# =====================================================================================
def build_phylactery():
    m = Model("lich_phylactery")
    stone, stone_d, gold, bone_c, soul, soul_l = "#3A3A46", "#24242C", "#B8963A", "#E6E0CC", "#5FD8E8", "#C8FBFF"
    m.bone("root", (0, 0, 0))
    m.bone("cage", (0, 0, 0), "root")

    def runes(fs):
        for n in SIDES:
            f = fs[n]
            for xx in range(1, f.w - 1, 2):
                f.set(xx, 1, H(soul), True)
    m.cube("cage", (-4.5, 0, -4.5), (9, 2, 9), P(stone, deco=chain(rim(gold, bottom=False), runes)))
    m.cube("cage", (-3.5, 2, -3.5), (7, 1, 7), P(stone_d, deco=rim(gold)))
    for x, z in ((-4, -4), (3, -4), (-4, 3), (3, 3)):
        m.cube("cage", (x, 3, z), (1, 11, 1), P(bone_c, deco=lambda fs: [fs[n].set(0, y, H("#A89F86")) for n in SIDES for y in (2, 5, 8)]))
    m.cube("cage", (-3.5, 14, -3.5), (7, 1, 7), P(gold, deco=rim(darken(H(gold), 0.3))))
    m.cube("cage", (-1.5, 15, -1.5), (3, 3, 3), P(bone_c, deco=lambda fs: (fs["front"].set(0, 1, H(soul), True), fs["front"].set(2, 1, H(soul), True))))
    m.bone("gem", (0, 8.5, 0), "cage")
    m.cube("gem", (-2, 6, -2), (4, 5, 4), P(soul, deco=glow_faces(soul, core=soul_l)))
    m.cube("gem", (-1, 5, -1), (2, 1, 2), P(soul, deco=glow_faces(soul)))
    m.cube("gem", (-1, 11, -1), (2, 1, 2), P(soul, deco=glow_faces(soul)))
    m.bone("orbit", (0, 8.5, 0), "cage")
    for k, (x, z) in enumerate(((-6, -0.5), (5, -0.5), (-0.5, 5))):
        m.cube("orbit", (x, 8 + k, z), (1, 1, 1), P(soul_l, deco=glow_faces(soul_l)))
    m.save(bounds=(2, 2), seed=81)
    save_anims("lich_phylactery", {
        "idle": anim(3.0, {
            "gem": {"rotation": spin(3.0, 1, 1), "position": cyc(3.0, lambda p: [0, 0.6 * S(p), 0])},
            "orbit": {"rotation": spin(3.0, 1, -1)},
        }, loop=True),
    })


# =====================================================================================
#  TEXTURAS DOS PROJETEIS (orbe/billboard 16x16)
# =====================================================================================
def bolt_texture(name, core, mid, outer, spikes=False, seed=1):
    c = Canvas(16, 16)
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 2.2:
                c.set(x, y, H(core))
            elif d <= 4.2:
                c.set(x, y, mix(H(core), H(mid), (d - 2.2) / 2.0))
            elif d <= 6.6:
                a = int(255 * (1 - (d - 4.2) / 2.6) ** 1.3)
                col = H(outer)
                c.set(x, y, (col[0], col[1], col[2], max(0, a)))
    if spikes:
        for k in range(8):
            ang = k * math.pi / 4 + rnd.random() * 0.3
            for r in range(5, 8):
                x = int(8 + math.cos(ang) * r)
                y = int(8 + math.sin(ang) * r)
                col = H(mid)
                c.set(x, y, (col[0], col[1], col[2], 200 - (r - 5) * 60))
    c.save(os.path.join(ASSETS, "textures/entity", name + ".png"))


# =====================================================================================
#  ITENS, LOOT TABLES, TAGS
# =====================================================================================
BOSSES = ("renegade_archmage", "corrupted_witch", "imprisoned_patron", "corrupted_ent", "lich")


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
    write_json(os.path.join(lt, "renegade_archmage.json"), loot([
        pool([item_entry("minecraft:ender_pearl", 1, 3)]),
        pool([item_entry("minecraft:lapis_lazuli", 4, 9)]),
        pool([item_entry("minecraft:book", 1, 3, 3), item_entry("minecraft:writable_book", 1, 1, 1)]),
        pool([item_entry("minecraft:amethyst_shard", 2, 5)]),
    ]))
    write_json(os.path.join(lt, "corrupted_witch.json"), loot([
        pool([item_entry("minecraft:glowstone_dust", 2, 5), item_entry("minecraft:redstone", 2, 5),
              item_entry("minecraft:spider_eye", 1, 3), item_entry("minecraft:sugar", 2, 4)], rolls=3),
        pool([item_entry("minecraft:nether_wart", 2, 5)]),
        pool([item_entry("minecraft:glass_bottle", 2, 4)]),
        pool([item_entry("minecraft:stick", 2, 4), item_entry("minecraft:wheat", 3, 6)]),
    ]))
    write_json(os.path.join(lt, "imprisoned_patron.json"), loot([
        pool([item_entry("minecraft:blaze_powder", 2, 5)]),
        pool([item_entry("minecraft:chain", 2, 5)]),
        pool([item_entry("minecraft:gold_ingot", 2, 4), item_entry("minecraft:gold_nugget", 6, 14)]),
        pool([item_entry("minecraft:obsidian", 1, 3)]),
    ]))
    write_json(os.path.join(lt, "corrupted_ent.json"), loot([
        pool([item_entry("minecraft:dark_oak_log", 4, 9), item_entry("minecraft:oak_log", 4, 9)]),
        pool([item_entry("minecraft:moss_block", 2, 5)]),
        pool([item_entry("minecraft:bone_meal", 4, 9)]),
        pool([item_entry("minecraft:golden_apple", 1, 1, 1), item_entry("minecraft:apple", 2, 4, 4)]),
    ]))
    write_json(os.path.join(lt, "lich.json"), loot([
        pool([item_entry("minecraft:bone", 4, 9)]),
        pool([item_entry("minecraft:bone_block", 1, 3)]),
        pool([item_entry("minecraft:soul_soil", 2, 5), item_entry("minecraft:soul_sand", 2, 5)]),
        pool([item_entry("minecraft:lapis_lazuli", 3, 7)]),
    ]))
    ids = ["%s:%s" % (NS, b) for b in BOSSES]
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": ids})
    lich = ["%s:lich" % NS]
    for t in ("undead", "inverted_healing_and_harm", "sensitive_to_smite", "ignores_poison_and_regen"):
        write_json(os.path.join(MERGE, "data/minecraft/tags/entity_type", t + ".json"), {"replace": False, "values": lich})


def main():
    build_archmage()
    build_witch()
    build_patron()
    build_ent()
    build_root()
    build_lich()
    build_phylactery()
    bolt_texture("class_boss_bolt_arcane", "#FFFFFF", "#D2B4FF", "#8E5BEA", spikes=True, seed=1)
    bolt_texture("class_boss_bolt_illusion", "#F4EEFF", "#C9B8F0", "#9C88D8", seed=2)
    bolt_texture("class_boss_bolt_soul", "#F0FFFF", "#8AF0FF", "#2A9AB0", spikes=True, seed=3)
    bolt_texture("class_boss_bolt_hellfire", "#FFE2B0", "#C0182E", "#1A0508", spikes=True, seed=4)
    write_data()
    print("bosses_c: assets gerados")


if __name__ == "__main__":
    main()
