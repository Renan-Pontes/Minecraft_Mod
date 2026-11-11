"""
Gera os assets do modulo creatures_e (rode da raiz do projeto: py -I tools/gen/creatures_e.py):
  - 12 criaturas GeckoLib: .geo.json (UV de caixa empacotado automaticamente), .animation.json,
    textura pintada por material + _glowmask (partes que brilham)
  - texturas dos projeteis (dimensional_bolt) e dos 8 drops (16x16) + modelos de item e dos ovos
  - loot tables, biome modifiers (so nos biomas das dimensoes, via tags proprias com "required": false)
  - tags compartilhadas no merge (celestial_beings, unholy, aquatic, arthropod, freeze_hurts_extra_types)
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/creatures_e")
T = (0, 0, 0, 0)


def H(h, a=255):
    return hex_rgba(h, a)


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


def tone(c, f):
    """f > 0 clareia, f < 0 escurece."""
    return lighten(c, f) if f >= 0 else darken(c, -f)


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


# =====================================================================================
#  Materiais: pintam cada face de um cubo (sombreamento + padrao) e dizem o que brilha
# =====================================================================================
class Mat:
    """Material base: cor solida com variacao, sombreamento por face e borda escura."""
    glow = False
    alpha = 255
    shading = 1.0
    edge = 0.12
    noise = 0.07

    def __init__(self, *cols, glow=None, alpha=None, noise=None, edge=None, shading=None):
        self.cols = [H(c) if isinstance(c, str) else c for c in cols] or [H("#888888")]
        if glow is not None:
            self.glow = glow
        if alpha is not None:
            self.alpha = alpha
        if noise is not None:
            self.noise = noise
        if edge is not None:
            self.edge = edge
        if shading is not None:
            self.shading = shading

    def prepare(self, w, h, face, rnd):
        return None

    def base(self, x, y, w, h, face, rnd, ctx):
        c = self.cols[0]
        if len(self.cols) > 1 and rnd.random() < 0.3:
            c = rnd.choice(self.cols[1:])
        return c, False

    def paint(self, cv, gl, rect, face, rnd):
        x0, y0, w, h = rect
        if w <= 0 or h <= 0:
            return
        ctx = self.prepare(w, h, face, rnd)
        for y in range(h):
            for x in range(w):
                c, g = self.base(x, y, w, h, face, rnd, ctx)
                if c is None:
                    continue
                if self.shading:
                    if face == "up":
                        f = 0.12
                    elif face == "down":
                        f = -0.3
                    else:
                        t = y / max(1, h - 1)
                        f = 0.08 - 0.24 * t
                    c = tone(c, f * self.shading)
                if self.edge and w >= 4 and h >= 4 and (x in (0, w - 1) or y in (0, h - 1)):
                    c = darken(c, self.edge)
                if self.noise:
                    c = tone(c, (rnd.random() - 0.5) * 2 * self.noise)
                a = c[3] if c[3] < 255 else self.alpha
                c = with_alpha(c, a)
                cv.set(x0 + x, y0 + y, c)
                if self.glow or g:
                    gl.set(x0 + x, y0 + y, with_alpha(c, 255))


class Magma(Mat):
    """Rocha escura com rachaduras incandescentes (as rachaduras brilham)."""
    noise = 0.1

    def __init__(self, rock="#3a2620", rock2="#26170f", hot="#ff7a1a", hotter="#ffd04a", density=30):
        super().__init__(rock, rock2)
        self.hot, self.hotter, self.density = H(hot), H(hotter), density

    def prepare(self, w, h, face, rnd):
        cracks = set()
        for _ in range(max(1, w * h // self.density)):
            x, y = rnd.randrange(w), rnd.randrange(h)
            for _ in range(rnd.randint(2, 6)):
                cracks.add((x, y))
                x = max(0, min(w - 1, x + rnd.choice((-1, 0, 1))))
                y = max(0, min(h - 1, y + rnd.choice((-1, 0, 1, 1))))
        return cracks

    def base(self, x, y, w, h, face, rnd, ctx):
        if (x, y) in ctx:
            return mix(self.hot, self.hotter, rnd.random() * 0.8), True
        return super().base(x, y, w, h, face, rnd, ctx)


class Flame(Mat):
    """Chama: amarelo no topo, laranja no meio, vermelho embaixo. Brilha inteira."""
    glow = True
    edge = 0.0
    shading = 0.0
    noise = 0.06

    def __init__(self, top="#fff1a8", mid="#ff9a1a", bottom="#d9361a", alpha=255):
        super().__init__(top)
        self.top, self.mid, self.bottom, self.alpha = H(top), H(mid), H(bottom), alpha

    def base(self, x, y, w, h, face, rnd, ctx):
        if face == "up":
            return mix(self.top, self.mid, rnd.random() * 0.3), False
        if face == "down":
            return self.bottom, False
        t = y / max(1, h - 1) + (rnd.random() - 0.5) * 0.25
        t = max(0.0, min(1.0, t))
        c = mix(self.top, self.mid, t * 2) if t < 0.5 else mix(self.mid, self.bottom, (t - 0.5) * 2)
        return c, False


class Water(Mat):
    """Agua viva: gradiente azul com reflexos claros em diagonal (semitransparente)."""
    edge = 0.06
    noise = 0.04

    def __init__(self, light="#7fd0ff", deep="#1f5fbf", shine="#e0f8ff", alpha=200):
        super().__init__(light)
        self.light, self.deep, self.shine, self.alpha = H(light), H(deep), H(shine), alpha

    def base(self, x, y, w, h, face, rnd, ctx):
        t = y / max(1, h - 1) if face not in ("up", "down") else (0.0 if face == "up" else 1.0)
        c = mix(self.light, self.deep, t)
        # ondulacoes: linhas onduladas claras a cada 5 linhas
        if int(1.6 * math.sin(x * 0.8 + (2 if face in ("east", "west") else 0)) + y + 8) % 5 == 0:
            c = mix(c, self.shine, 0.55)
        return c, False


class Cloud(Mat):
    """Nuvem/vento: branco-azulado macio com redemoinhos claros (semitransparente)."""
    edge = 0.04
    noise = 0.05

    def __init__(self, base="#eef6f8", shade_c="#c3d4dc", swirl="#ffffff", alpha=170):
        super().__init__(base)
        self.b, self.s, self.sw, self.alpha = H(base), H(shade_c), H(swirl), alpha

    def base(self, x, y, w, h, face, rnd, ctx):
        v = math.sin((x * 0.9 + y * 0.6) + rnd.random() * 0.6)
        c = mix(self.b, self.s, max(0.0, -v) * 0.7)
        if (x - y * 2) % 7 == 0:
            c = mix(c, self.sw, 0.8)
        return c, False


class Rock(Mat):
    """Pedra: pintas claras/escuras e rachaduras escuras."""
    noise = 0.08

    def __init__(self, base="#7d7a73", light="#9b978e", dark="#55524c", cracks=True):
        super().__init__(base)
        self.b, self.l, self.d, self.cracks = H(base), H(light), H(dark), cracks

    def prepare(self, w, h, face, rnd):
        cracks = set()
        if self.cracks and w * h > 20:
            for _ in range(max(1, w * h // 60)):
                x, y = rnd.randrange(w), rnd.randrange(h)
                for _ in range(rnd.randint(2, 5)):
                    cracks.add((x, y))
                    x = max(0, min(w - 1, x + rnd.choice((-1, 1))))
                    y = max(0, min(h - 1, y + rnd.choice((0, 1))))
        return cracks

    def base(self, x, y, w, h, face, rnd, ctx):
        if (x, y) in ctx:
            return darken(self.d, 0.25), False
        r = rnd.random()
        return (self.l if r < 0.15 else self.d if r < 0.3 else self.b), False


class Mossy(Rock):
    """Pedra com musgo por cima (no topo e escorrendo nas laterais)."""

    def __init__(self, rock="#77736a", moss="#4f8a2e", moss2="#6aa83a"):
        super().__init__(rock)
        self.m, self.m2 = H(moss), H(moss2)

    def base(self, x, y, w, h, face, rnd, ctx):
        drip = (hash((x, w)) % 3) + 1
        if face == "up" or (face not in ("down",) and y < drip):
            return (self.m2 if rnd.random() < 0.35 else self.m), False
        return super().base(x, y, w, h, face, rnd, ctx)


class Crystal(Mat):
    """Cristal facetado: listras diagonais claras e bordas brilhantes."""
    edge = 0.0
    noise = 0.04

    def __init__(self, color, alpha=225, glow=False, facet=0.35):
        super().__init__(color, glow=glow, alpha=alpha)
        self.c, self.facet = H(color), facet

    def base(self, x, y, w, h, face, rnd, ctx):
        c = self.c
        if (x - y) % 4 == 0:
            c = lighten(c, self.facet)
        elif (x + y) % 5 == 0:
            c = darken(c, 0.15)
        if x in (0, w - 1) or y in (0, h - 1):
            c = lighten(c, 0.25)
        return c, False


class Feather(Mat):
    """Penas: faixas horizontais e ponta de outra cor nas linhas de baixo."""
    edge = 0.08

    def __init__(self, base, tip=None, line=None):
        super().__init__(base)
        self.b = H(base)
        self.tip = H(tip) if tip else self.b
        self.line = H(line) if line else darken(self.b, 0.18)

    def base(self, x, y, w, h, face, rnd, ctx):
        if face in ("up", "down"):
            c = self.line if x % 3 == 0 else self.b
            t = y / max(1, h - 1)
            return mix(c, self.tip, max(0.0, t - 0.5) * 1.6), False
        t = y / max(1, h - 1)
        c = self.line if y % 2 == 1 and (x + y) % 3 else self.b
        return mix(c, self.tip, max(0.0, t - 0.45) * 1.8), False


class Scales(Mat):
    """Escamas sobrepostas (bordas escuras, brilho no meio)."""
    noise = 0.05

    def __init__(self, base, hi=None):
        super().__init__(base)
        self.b = H(base)
        self.hi = H(hi) if hi else lighten(self.b, 0.25)

    def base(self, x, y, w, h, face, rnd, ctx):
        r = y % 3
        cc = (x + (y // 3) % 2 * 2) % 4
        if r == 2 or cc == 0:
            return darken(self.b, 0.22), False
        if r == 0 and cc == 2:
            return self.hi, False
        return self.b, False


class Membrane(Mat):
    """Membrana de asa com nervuras."""
    edge = 0.15

    def __init__(self, base, vein=None, alpha=240):
        super().__init__(base, alpha=alpha)
        self.b = H(base)
        self.v = H(vein) if vein else darken(self.b, 0.3)

    def base(self, x, y, w, h, face, rnd, ctx):
        if x % 5 == 0 or (face in ("up", "down") and y % 4 == 0):
            return self.v, False
        return self.b, False


class Chitin(Mat):
    """Carapaca brilhante: faixa de brilho no alto e pintas que brilham (opcional)."""
    noise = 0.05

    def __init__(self, base, sheen="#5a3a88", spots=None, spot_rate=0.0):
        super().__init__(base)
        self.b, self.sheen = H(base), H(sheen)
        self.spots, self.rate = (H(spots) if spots else None), spot_rate

    def base(self, x, y, w, h, face, rnd, ctx):
        if self.spots and rnd.random() < self.rate:
            return self.spots, True
        c = self.b
        if face == "up" and (x + y) % 6 == 0:
            c = mix(c, self.sheen, 0.6)
        elif face not in ("up", "down") and y == 1:
            c = mix(c, self.sheen, 0.5)
        return c, False


class Gold(Mat):
    """Ouro polido: brilho no canto superior e sombra embaixo."""
    noise = 0.04
    edge = 0.2

    def __init__(self, base="#e8b83a", hi="#fff0a0", lo="#a87420"):
        super().__init__(base)
        self.b, self.hi, self.lo = H(base), H(hi), H(lo)

    def base(self, x, y, w, h, face, rnd, ctx):
        if face == "up" or (x + y) % 7 == 0 or y == 0:
            return self.hi, False
        if y == h - 1:
            return self.lo, False
        return self.b, False


class Cloth(Mat):
    """Tecido: dobras verticais."""
    noise = 0.03
    edge = 0.08

    def __init__(self, base="#f2eee2", fold=None):
        super().__init__(base)
        self.b = H(base)
        self.f = H(fold) if fold else darken(self.b, 0.1)

    def base(self, x, y, w, h, face, rnd, ctx):
        if face not in ("up", "down") and x % 3 == 1:
            return self.f, False
        return self.b, False


class Curls(Mat):
    """Cachos (cabelo dourado do querubim)."""
    noise = 0.05

    def __init__(self, base="#f2c84b", dark="#c7962a", light="#ffe89a"):
        super().__init__(base)
        self.b, self.d, self.l = H(base), H(dark), H(light)

    def base(self, x, y, w, h, face, rnd, ctx):
        k = (x + (y % 2) * 2) % 4
        return (self.l if k == 0 else self.d if k == 2 else self.b), False


# =====================================================================================
#  Modelo GeckoLib (formato Bedrock 1.12.0) com UV de caixa empacotado
# =====================================================================================
class Cube:
    def __init__(self, origin, size, mat, inflate=0.0, rotation=None, pivot=None, deco=None):
        self.origin, self.size, self.mat = origin, size, mat
        self.inflate, self.rotation, self.pivot = inflate, rotation, pivot
        self.deco = deco or []
        self.uv = None

    def box(self):
        w, h, d = (max(1, int(math.floor(s))) for s in self.size)
        return w, h, d

    def faces(self):
        u, v = self.uv
        w, h, d = self.box()
        return {
            "up": (u + d, v, w, d),
            "down": (u + d + w, v, w, d),
            "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h),
        }


class Bone:
    def __init__(self, model, name, pivot, parent=None, rotation=None):
        self.model, self.name, self.pivot, self.parent, self.rotation = model, name, pivot, parent, rotation
        self.cubes = []

    def box(self, origin, size, mat, inflate=0.0, rotation=None, pivot=None, deco=None):
        self.cubes.append(Cube(origin, size, mat, inflate, rotation, pivot, deco))
        return self


class Model:
    def __init__(self, mid, tw, th):
        self.id, self.tw, self.th = mid, tw, th
        self.bones = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = Bone(self, name, list(pivot), parent, rotation)
        self.bones.append(b)
        return b

    def pack(self):
        cubes = [c for b in self.bones for c in b.cubes]
        order = sorted(cubes, key=lambda c: -(c.box()[2] + c.box()[1]))
        x = y = shelf = 0
        for c in order:
            w, h, d = c.box()
            bw, bh = 2 * (w + d), d + h
            if x + bw > self.tw:
                x, y, shelf = 0, y + shelf, 0
            if y + bh > self.th or bw > self.tw:
                raise SystemExit(f"{self.id}: textura {self.tw}x{self.th} pequena demais")
            c.uv = (x, y)
            x += bw
            shelf = max(shelf, bh)

    def paint(self, seed):
        rnd = random.Random(seed)
        cv = Canvas(self.tw, self.th)
        gl = Canvas(self.tw, self.th)
        for b in self.bones:
            for c in b.cubes:
                for face, rect in c.faces().items():
                    c.mat.paint(cv, gl, rect, face, rnd)
                for face, painter in c.deco:
                    painter(cv, gl, c.faces()[face])
        return cv, gl

    def geo(self):
        bones = []
        for b in self.bones:
            jb = {"name": b.name, "pivot": [round(v, 3) for v in b.pivot]}
            if b.parent:
                jb["parent"] = b.parent
            if b.rotation:
                jb["rotation"] = b.rotation
            cubes = []
            for c in b.cubes:
                jc = {"origin": [round(v, 3) for v in c.origin], "size": list(c.size), "uv": list(c.uv)}
                if c.inflate:
                    jc["inflate"] = c.inflate
                if c.rotation:
                    jc["rotation"] = c.rotation
                    jc["pivot"] = c.pivot or [c.origin[0] + c.size[0] / 2, c.origin[1], c.origin[2] + c.size[2] / 2]
                cubes.append(jc)
            if cubes:
                jb["cubes"] = cubes
            bones.append(jb)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.id,
                    "texture_width": self.tw,
                    "texture_height": self.th,
                    "visible_bounds_width": 6,
                    "visible_bounds_height": 5,
                    "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": bones,
            }],
        }

    def write(self, anims, seed=1, glow=True):
        self.pack()
        cv, gl = self.paint(seed)
        cv.save(os.path.join(A, "textures/entity", self.id + ".png"))
        if glow:
            gl.save(os.path.join(A, "textures/entity", self.id + "_glowmask.png"))
        save_json(os.path.join(A, "geo/entity", self.id + ".geo.json"), self.geo())
        out = {}
        for name, a in anims.items():
            out["animation." + self.id + "." + name] = a
        save_json(os.path.join(A, "animations/entity", self.id + ".animation.json"), {"format_version": "1.8.0", "animations": out})


def mirror(origin, size):
    """Espelha um cubo no eixo X (lado esquerdo <-> direito)."""
    return [-(origin[0] + size[0]), origin[1], origin[2]]


# ---------------- Decoracoes de face (olhos, viseira, nucleo...) ----------------
def dots(points, glow=True):
    """points: (x, y, w, h, cor) relativos a face."""
    def p(cv, gl, rect):
        x0, y0, fw, fh = rect
        for (x, y, w, h, col) in points:
            c = H(col) if isinstance(col, str) else col
            for j in range(h):
                for i in range(w):
                    if 0 <= x + i < fw and 0 <= y + j < fh:
                        cv.set(x0 + x + i, y0 + y + j, c)
                        if glow:
                            gl.set(x0 + x + i, y0 + y + j, c)
    return p


def iris(color, pupil="#120818", ring=None, glow=True):
    """Iris grande centralizada (face frontal do Olho Vigia)."""
    def p(cv, gl, rect):
        x0, y0, w, h = rect
        cx, cy = w / 2, h / 2
        r = min(w, h) * 0.36
        for y in range(h):
            for x in range(w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    if d <= r * 0.42 and abs(x + 0.5 - cx) <= r * 0.2:
                        c = H(pupil)
                    else:
                        c = mix(H(color), H(ring or "#ffffff"), max(0.0, (d / r) - 0.6) * 1.2)
                    cv.set(x0 + x, y0 + y, c)
                    if glow and c != H(pupil):
                        gl.set(x0 + x, y0 + y, c)
                elif d <= r + 1:
                    cv.set(x0 + x, y0 + y, darken(H(color), 0.5))
    return p


# =====================================================================================
#  Animacoes
# =====================================================================================
def fmt(t):
    return f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0"


def keys(points):
    """[(tempo, [x, y, z]), ...] -> keyframes."""
    out = {}
    for t, v in points:
        k = fmt(t)
        if "." not in k:
            k += ".0"
        out[k] = [round(float(a), 2) for a in v]
    return out


def wave(length, fn, n=16):
    """Amostra fn(t) -> [x, y, z] ao longo da animacao (loops sem emenda se fn for periodica)."""
    return keys([(length * i / n, fn(length * i / n)) for i in range(n + 1)])


def sn(t, period, phase=0.0):
    return math.sin(2 * math.pi * t / period + phase)


def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


def once(length, bones):
    return {"loop": False, "animation_length": length, "bones": bones}


def rot(points):
    return {"rotation": keys(points)}


def pos(points):
    return {"position": keys(points)}


def chan(**kw):
    """chan(rotation=[...], position=[...], scale=[...]) com listas de (t, vec)."""
    return {k: keys(v) for k, v in kw.items()}


Z3 = [0, 0, 0]


def mz(v):
    """Espelha uma rotacao/posicao para o lado -X (inverte Y e Z da rotacao)."""
    return [v[0], -v[1], -v[2]]


def mpts(points):
    return [(t, mz(v)) for t, v in points]


# Convencoes (GeckoLib/Blockbench): rotacao X negativa leva um membro que aponta para baixo para a FRENTE;
# X positiva inclina o tronco para frente. Partes do lado +X: Z negativa = para cima/para fora (lado -X: Z positiva).


def arms_pair(m, parent, side_bones):
    """Cria braco + antebraco nos dois lados. side_bones(s) devolve as listas de cubos do lado +X."""
    for s, suffix in ((1, "left"), (-1, "right")):
        spec = side_bones()
        up = m.bone("arm_" + suffix, [spec["pivot"][0] * s, spec["pivot"][1], spec["pivot"][2]], parent)
        for (o, sz, mat, kw) in spec["upper"]:
            up.box(o if s > 0 else mirror(o, sz), sz, mat, **mirror_kw(kw, s))
        if spec.get("lower"):
            fp = spec["fpivot"]
            lo = m.bone("forearm_" + suffix, [fp[0] * s, fp[1], fp[2]], "arm_" + suffix)
            for (o, sz, mat, kw) in spec["lower"]:
                lo.box(o if s > 0 else mirror(o, sz), sz, mat, **mirror_kw(kw, s))


def mirror_kw(kw, s):
    kw = dict(kw)
    if s < 0:
        if kw.get("rotation"):
            r = kw["rotation"]
            kw["rotation"] = [r[0], -r[1], -r[2]]
        if kw.get("pivot"):
            p = kw["pivot"]
            kw["pivot"] = [-p[0], p[1], p[2]]
    return kw


def C(o, sz, mat, **kw):
    return (o, sz, mat, kw)


# =====================================================================================
#  PLANO ELEMENTAL
# =====================================================================================
def greater_fire_elemental():
    m = Model("greater_fire_elemental", 128, 128)
    magma, flame = Magma(), Flame()
    deep = Flame("#ffb347", "#ff5a00", "#9a1a0a")
    core = Flame("#ffffff", "#fff1a8", "#ffb347")
    m.bone("root", [0, 0, 0])
    v = m.bone("vortex", [0, 6, 0], "root")
    v.box([-3, 0, -3], [6, 6, 6], deep)
    v.box([-5, 4, -5], [10, 5, 10], flame)
    v.box([-7, 3, -1], [2, 5, 2], flame, rotation=[0, 0, 25], pivot=[-6, 3, 0])
    v.box([5, 3, -1], [2, 5, 2], flame, rotation=[0, 0, -25], pivot=[6, 3, 0])
    v.box([-1, 3, 5], [2, 5, 2], flame, rotation=[25, 0, 0], pivot=[0, 3, 6])
    v.box([-1, 3, -7], [2, 5, 2], flame, rotation=[-25, 0, 0], pivot=[0, 3, -6])
    b = m.bone("body", [0, 14, 0], "root")
    b.box([-5, 9, -4], [10, 9, 8], magma)
    b.box([-8, 18, -5], [16, 12, 10], magma)
    b.box([-3, 21, -6], [6, 6, 1], core)
    h = m.bone("head", [0, 30, 0], "body")
    h.box([-4, 30, -5], [8, 8, 8], magma,
          deco=[("north", dots([(1, 3, 2, 1, "#fff1a8"), (5, 3, 2, 1, "#fff1a8"), (2, 6, 4, 1, "#ff9a1a")]))])
    h.box([-6, 34, -2], [2, 5, 2], magma, rotation=[0, 0, 25])
    h.box([4, 34, -2], [2, 5, 2], magma, rotation=[0, 0, -25])
    c = m.bone("crown", [0, 38, 0], "head")
    c.box([-3, 38, -4], [6, 4, 6], flame)
    c.box([-2, 41, -3], [4, 4, 4], flame)
    c.box([-1, 44, -2], [2, 3, 2], flame)
    arms_pair(m, "body", lambda: {
        "pivot": [10, 28, 0],
        "upper": [C([8, 25, -4], [6, 6, 8], magma), C([9, 17, -3], [5, 9, 6], magma)],
        "fpivot": [11.5, 17, 0],
        "lower": [C([8, 7, -4], [7, 10, 8], magma), C([9, 4, -3], [5, 3, 6], flame)],
    })
    r = m.bone("rocks", [0, 24, 0], "root")
    r.box([11, 33, 6], [3, 3, 3], magma)
    r.box([-14, 21, 5], [2, 2, 2], magma)
    r.box([-3, 41, 9], [3, 3, 3], magma)
    r.box([9, 14, -10], [2, 2, 2], magma)

    sway = lambda t, s: [0, 0, s * (-4 - 3 * sn(t, 2))]
    anims = {
        "idle": loop(4.0, {
            "body": {"position": wave(4.0, lambda t: [0, 0.6 * sn(t, 2), 0]), "rotation": wave(4.0, lambda t: [1.5 * sn(t, 4), 0, 0])},
            "vortex": {"rotation": wave(4.0, lambda t: [0, 180 * t, 0])},
            "arm_left": {"rotation": wave(4.0, lambda t: sway(t, 1))},
            "arm_right": {"rotation": wave(4.0, lambda t: sway(t, -1))},
            "forearm_left": {"rotation": wave(4.0, lambda t: [-10 + 4 * sn(t, 2, 1), 0, 0])},
            "forearm_right": {"rotation": wave(4.0, lambda t: [-10 + 4 * sn(t, 2, 1), 0, 0])},
            "crown": {"scale": wave(4.0, lambda t: [1, 1 + 0.15 * sn(t, 0.5), 1])},
            "rocks": {"rotation": wave(4.0, lambda t: [0, -90 * t, 0]), "position": wave(4.0, lambda t: [0, sn(t, 2), 0])},
        }),
        "walk": loop(1.6, {
            "body": {"rotation": wave(1.6, lambda t: [8 + 2 * sn(t, 0.8), 0, 0]), "position": wave(1.6, lambda t: [0, 0.8 * sn(t, 0.8), 0])},
            "vortex": {"rotation": wave(1.6, lambda t: [-8, 450 * t, 0])},
            "arm_left": {"rotation": wave(1.6, lambda t: [25 * sn(t, 1.6), 0, -5])},
            "arm_right": {"rotation": wave(1.6, lambda t: [-25 * sn(t, 1.6), 0, 5])},
            "forearm_left": {"rotation": wave(1.6, lambda t: [-15, 0, 0])},
            "forearm_right": {"rotation": wave(1.6, lambda t: [-15, 0, 0])},
            "crown": {"scale": wave(1.6, lambda t: [1, 1 + 0.2 * sn(t, 0.4), 1])},
            "rocks": {"rotation": wave(1.6, lambda t: [0, -225 * t, 0])},
        }),
        "attack": once(0.9, {
            "arm_right": rot([(0, Z3), (0.3, [35, 0, 10]), (0.45, [-85, 0, -5]), (0.65, [-70, 0, 0]), (0.9, Z3)]),
            "forearm_right": rot([(0, Z3), (0.3, [-40, 0, 0]), (0.45, [-5, 0, 0]), (0.9, Z3)]),
            "body": rot([(0, Z3), (0.3, [0, -15, 0]), (0.45, [6, 20, 0]), (0.9, Z3)]),
        }),
        "cast": once(1.5, {
            "arm_right": rot([(0, Z3), (0.4, [-60, 0, 0]), (0.6, [-95, 0, 5]), (0.75, [-60, 0, 0]), (1.1, [-95, 0, 5]), (1.3, [-40, 0, 0]), (1.5, Z3)]),
            "arm_left": rot([(0, Z3), (0.6, [-50, 0, 0]), (0.85, [-95, 0, -5]), (1.0, [-60, 0, 0]), (1.5, Z3)]),
            "body": rot([(0, Z3), (0.6, [-5, 0, 0]), (1.5, Z3)]),
        }),
        "nova": once(1.7, {
            "arm_left": rot([(0, Z3), (0.8, [-170, 0, -20]), (0.9, [-170, 0, -20]), (1.05, [-20, 0, -70]), (1.4, [-10, 0, -40]), (1.7, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.8, [-170, 0, -20]), (0.9, [-170, 0, -20]), (1.05, [-20, 0, -70]), (1.4, [-10, 0, -40]), (1.7, Z3)])),
            "body": chan(position=[(0, Z3), (0.8, [0, 2, 0]), (1.0, [0, -2.5, 0]), (1.4, [0, -1, 0]), (1.7, Z3)],
                         rotation=[(0, Z3), (0.8, [-10, 0, 0]), (1.0, [12, 0, 0]), (1.7, Z3)]),
            "vortex": chan(scale=[(0, [1, 1, 1]), (0.9, [0.9, 0.9, 0.9]), (1.0, [1.5, 1.3, 1.5]), (1.7, [1, 1, 1])]),
            "crown": chan(scale=[(0, [1, 1, 1]), (0.8, [1, 1.6, 1]), (1.0, [1.3, 0.7, 1.3]), (1.7, [1, 1, 1])]),
        }),
    }
    m.write(anims, seed=11)


def greater_water_elemental():
    m = Model("greater_water_elemental", 128, 128)
    water = Water(alpha=190)
    deep = Water("#4fb0ff", "#123f8f", alpha=215)
    foam = Mat("#e8faff", "#cfefff", alpha=230, noise=0.04, edge=0.05)
    core = Mat("#dffaff", "#bff4ff", alpha=255, edge=0.0, shading=0.3)
    m.bone("root", [0, 0, 0])
    w = m.bone("wave", [0, 4, 0], "root")
    w.box([-3, 0, -3], [6, 4, 6], deep)
    w.box([-5, 3, -5], [10, 4, 10], water)
    w.box([-6, 6, -6], [12, 2, 12], foam)
    b = m.bone("body", [0, 14, 0], "root")
    b.box([-4, 8, -3], [8, 9, 6], water)
    b.box([-7, 17, -4], [14, 11, 8], water)
    b.box([-2, 21, -5], [4, 4, 2], core)
    h = m.bone("head", [0, 28, 0], "body")
    h.box([-4, 28, -4], [8, 8, 8], water,
          deco=[("north", dots([(1, 3, 2, 2, "#e0fbff"), (5, 3, 2, 2, "#e0fbff"), (3, 6, 2, 1, "#9fdcff")], glow=False))])
    h.box([-1, 35, -4], [2, 4, 9], foam, rotation=[-15, 0, 0], pivot=[0, 35, 0])
    h.box([-6, 30, -1], [2, 5, 4], foam, rotation=[0, 0, 15])
    h.box([4, 30, -1], [2, 5, 4], foam, rotation=[0, 0, -15])
    arms_pair(m, "body", lambda: {
        "pivot": [9, 26, 0],
        "upper": [C([7, 23, -3], [5, 5, 6], water), C([8, 16, -2], [4, 8, 4], water)],
        "fpivot": [10, 16, 0],
        "lower": [C([7, 6, -3], [6, 10, 6], water), C([7, 14, -3], [6, 2, 6], foam, inflate=0.4),
                  C([12, 7, -1], [2, 8, 2], foam, rotation=[0, 0, -10])],
    })
    d = m.bone("droplets", [0, 22, 0], "root")
    for o in ([9, 31, 5], [-11, 26, -6], [2, 38, 8], [-8, 16, 8]):
        d.box(o, [2, 2, 2], deep)

    sway = lambda t, s: [3 * sn(t, 2), 0, s * (-5 - 3 * sn(t, 2))]
    anims = {
        "idle": loop(4.0, {
            "body": {"position": wave(4.0, lambda t: [0, 0.8 * sn(t, 2), 0]), "rotation": wave(4.0, lambda t: [0, 0, 1.5 * sn(t, 4)])},
            "wave": {"rotation": wave(4.0, lambda t: [0, 180 * t, 0]), "scale": wave(4.0, lambda t: [1 + 0.06 * sn(t, 1), 1, 1 + 0.06 * sn(t, 1)])},
            "arm_left": {"rotation": wave(4.0, lambda t: sway(t, 1))},
            "arm_right": {"rotation": wave(4.0, lambda t: sway(t, -1))},
            "forearm_left": {"rotation": wave(4.0, lambda t: [-12 + 5 * sn(t, 2, 1), 0, 0])},
            "forearm_right": {"rotation": wave(4.0, lambda t: [-12 + 5 * sn(t, 2, 1), 0, 0])},
            "droplets": {"rotation": wave(4.0, lambda t: [0, -90 * t, 0]), "position": wave(4.0, lambda t: [0, 1.2 * sn(t, 2), 0])},
        }),
        "walk": loop(1.6, {
            "body": {"rotation": wave(1.6, lambda t: [8, 0, 3 * sn(t, 1.6)]), "position": wave(1.6, lambda t: [0, 0.8 * sn(t, 0.8), 0])},
            "wave": {"rotation": wave(1.6, lambda t: [-10, 450 * t, 0])},
            "arm_left": {"rotation": wave(1.6, lambda t: [25 * sn(t, 1.6), 0, -6])},
            "arm_right": {"rotation": wave(1.6, lambda t: [-25 * sn(t, 1.6), 0, 6])},
            "droplets": {"rotation": wave(1.6, lambda t: [0, -225 * t, 0])},
        }),
        "swim": loop(1.6, {
            "body": {"rotation": wave(1.6, lambda t: [35, 0, 0]), "position": wave(1.6, lambda t: [0, 0.6 * sn(t, 1.6), 0])},
            "wave": {"rotation": wave(1.6, lambda t: [25, 450 * t, 0]), "scale": wave(1.6, lambda t: [1, 1.2, 1])},
            "arm_left": {"rotation": wave(1.6, lambda t: [-50 + 45 * sn(t, 1.6), 0, -30])},
            "arm_right": {"rotation": wave(1.6, lambda t: [-50 + 45 * sn(t, 1.6), 0, 30])},
            "droplets": {"rotation": wave(1.6, lambda t: [0, -225 * t, 0])},
        }),
        "attack": once(0.9, {
            "arm_left": rot([(0, Z3), (0.35, [-150, 0, 10]), (0.45, [-30, 0, 10]), (0.9, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.35, [-150, 0, 10]), (0.45, [-30, 0, 10]), (0.9, Z3)])),
            "body": rot([(0, Z3), (0.35, [-8, 0, 0]), (0.5, [15, 0, 0]), (0.9, Z3)]),
        }),
        "cast": once(1.0, {
            "arm_right": rot([(0, Z3), (0.3, [20, 0, 10]), (0.5, [-90, 0, 0]), (0.7, [-80, 0, 0]), (1.0, Z3)]),
            "forearm_right": rot([(0, Z3), (0.3, [-50, 0, 0]), (0.5, Z3), (1.0, Z3)]),
            "body": rot([(0, Z3), (0.3, [0, -15, 0]), (0.5, [5, 15, 0]), (1.0, Z3)]),
        }),
        "whirlpool": once(3.0, {
            "body": rot([(0, Z3), (0.5, Z3), (2.9, [0, 1440, 0]), (3.0, [0, 1440, 0])]),
            "arm_left": rot([(0, Z3), (0.5, [0, 0, -80]), (2.8, [0, 0, -80]), (3.0, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.5, [0, 0, -80]), (2.8, [0, 0, -80]), (3.0, Z3)])),
            "wave": chan(scale=[(0, [1, 1, 1]), (1.0, [1.7, 1.3, 1.7]), (2.8, [1.7, 1.3, 1.7]), (3.0, [1, 1, 1])],
                         rotation=[(0, Z3), (3.0, [0, -1080, 0])]),
        }),
    }
    m.write(anims, seed=12, glow=False)


def greater_earth_elemental():
    m = Model("greater_earth_elemental", 256, 128)
    rock = Rock("#7d776c", "#9c968a", "#57524a")
    dark = Rock("#5c574f", "#706a60", "#3e3a34")
    moss = Mossy()
    amber = Crystal("#ffb340", alpha=255, glow=True)
    m.bone("root", [0, 0, 0])
    for s, suffix in ((1, "left"), (-1, "right")):
        lg = m.bone("leg_" + suffix, [5 * s, 14, 0], "root")
        o, sz = [2, 0, -4], [7, 14, 8]
        lg.box(o if s > 0 else mirror(o, sz), sz, rock)
        o, sz = [1, 0, -7], [9, 3, 4]
        lg.box(o if s > 0 else mirror(o, sz), sz, dark)
    b = m.bone("body", [0, 22, 0], "root")
    b.box([-8, 14, -5], [16, 8, 10], dark)
    b.box([-12, 22, -8], [24, 16, 16], rock)
    b.box([-12, 38, -8], [24, 2, 16], moss)
    b.box([-9, 36, 2], [8, 6, 8], dark)
    b.box([2, 37, 1], [7, 7, 7], rock)
    b.box([-6, 40, 3], [3, 8, 3], amber, rotation=[0, 0, 15])
    b.box([4, 41, 2], [3, 6, 3], amber, rotation=[10, 0, -20])
    b.box([-1, 39, 5], [2, 5, 2], amber, rotation=[-15, 0, 0])
    h = m.bone("head", [0, 34, -8], "body")
    h.box([-5, 30, -14], [10, 9, 8], rock,
          deco=[("north", dots([(2, 4, 2, 1, "#ffb340"), (6, 4, 2, 1, "#ffb340")])),
                ("north", dots([(3, 7, 4, 1, "#3a2410")], glow=False))])
    h.box([-6, 37, -15], [12, 2, 3], dark)
    arms_pair(m, "body", lambda: {
        "pivot": [14, 36, 0],
        "upper": [C([12, 30, -6], [9, 10, 12], rock), C([12, 40, -6], [9, 2, 12], moss), C([13, 20, -4], [7, 10, 8], rock)],
        "fpivot": [16.5, 20, 0],
        "lower": [C([12, 6, -6], [9, 14, 12], rock), C([12, 1, -6], [9, 5, 12], dark),
                  C([20, 10, -2], [3, 5, 3], amber, rotation=[0, 0, -30])],
    })

    anims = {
        "idle": loop(3.0, {
            "body": {"rotation": wave(3.0, lambda t: [2 * sn(t, 3), 0, 0]), "position": wave(3.0, lambda t: [0, 0.4 * sn(t, 3), 0])},
            "arm_left": {"rotation": wave(3.0, lambda t: [2 * sn(t, 3), 0, -3 + 1.5 * sn(t, 3)])},
            "arm_right": {"rotation": wave(3.0, lambda t: [2 * sn(t, 3), 0, 3 - 1.5 * sn(t, 3)])},
            "forearm_left": {"rotation": wave(3.0, lambda t: [-6, 0, 0])},
            "forearm_right": {"rotation": wave(3.0, lambda t: [-6, 0, 0])},
        }),
        "walk": loop(2.0, {
            "leg_left": {"rotation": wave(2.0, lambda t: [22 * sn(t, 2), 0, 0])},
            "leg_right": {"rotation": wave(2.0, lambda t: [-22 * sn(t, 2), 0, 0])},
            "body": {"rotation": wave(2.0, lambda t: [6, 0, 3 * sn(t, 2)]), "position": wave(2.0, lambda t: [0, -0.8 * abs(sn(t, 2)), 0])},
            "arm_left": {"rotation": wave(2.0, lambda t: [-16 * sn(t, 2), 0, -3])},
            "arm_right": {"rotation": wave(2.0, lambda t: [16 * sn(t, 2), 0, 3])},
            "forearm_left": {"rotation": wave(2.0, lambda t: [-10, 0, 0])},
            "forearm_right": {"rotation": wave(2.0, lambda t: [-10, 0, 0])},
        }),
        "attack": once(1.1, {
            "arm_left": rot([(0, Z3), (0.45, [-160, 0, 10]), (0.6, [-40, 0, 5]), (0.8, [-30, 0, 5]), (1.1, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.45, [-160, 0, 10]), (0.6, [-40, 0, 5]), (0.8, [-30, 0, 5]), (1.1, Z3)])),
            "forearm_left": rot([(0, Z3), (0.45, [-30, 0, 0]), (0.6, Z3), (1.1, Z3)]),
            "forearm_right": rot([(0, Z3), (0.45, [-30, 0, 0]), (0.6, Z3), (1.1, Z3)]),
            "body": rot([(0, Z3), (0.45, [-10, 0, 0]), (0.6, [20, 0, 0]), (0.8, [18, 0, 0]), (1.1, Z3)]),
        }),
        "cast": once(1.4, {
            "body": rot([(0, Z3), (0.4, [25, 0, 0]), (0.6, [-10, 0, 0]), (0.8, [12, 0, 0]), (1.4, Z3)]),
            "arm_left": rot([(0, Z3), (0.4, [-20, 0, 0]), (0.6, [-170, 0, 10]), (0.8, [-100, 0, 0]), (1.0, [-60, 0, 0]), (1.4, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.4, [-20, 0, 0]), (0.6, [-170, 0, 10]), (0.8, [-100, 0, 0]), (1.0, [-60, 0, 0]), (1.4, Z3)])),
        }),
        "quake": once(1.8, {
            "arm_left": rot([(0, Z3), (0.8, [-170, 0, -15]), (1.0, [20, 0, -30]), (1.3, [15, 0, -25]), (1.8, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.8, [-170, 0, -15]), (1.0, [20, 0, -30]), (1.3, [15, 0, -25]), (1.8, Z3)])),
            "body": chan(rotation=[(0, Z3), (0.8, [-12, 0, 0]), (1.0, [25, 0, 0]), (1.3, [20, 0, 0]), (1.8, Z3)],
                         position=[(0, Z3), (0.8, [0, 2, 0]), (1.0, [0, -3, 0]), (1.3, [0, -2.5, 0]), (1.8, Z3)]),
            "leg_left": rot([(0, Z3), (1.0, [-20, 0, -10]), (1.4, [-15, 0, -8]), (1.8, Z3)]),
            "leg_right": rot([(0, Z3), (1.0, [-20, 0, 10]), (1.4, [-15, 0, 8]), (1.8, Z3)]),
        }),
    }
    m.write(anims, seed=13)


def greater_air_elemental():
    m = Model("greater_air_elemental", 128, 128)
    cloud = Cloud(alpha=165)
    dense = Cloud("#f5fbfc", "#cbdbe2", alpha=215)
    core = Mat("#e6fff8", "#bffff0", glow=True, alpha=255, edge=0.0, shading=0.2)
    m.bone("root", [0, 0, 0])
    m.bone("vortex", [0, 0, 0], "root")
    for i, (y, w) in enumerate(((0, 2), (4, 4), (8, 6), (12, 8), (16, 10)), start=1):
        r = m.bone(f"ring{i}", [0, y, 0], "vortex")
        r.box([-w / 2, y, -w / 2], [w, 4, w], cloud)
        if w >= 4:
            r.box([-w / 2, y, -w / 2], [w, 4, w], cloud, rotation=[0, 45, 0], pivot=[0, y, 0])
    b = m.bone("body", [0, 24, 0], "root")
    b.box([-6, 20, -4], [12, 10, 8], dense)
    b.box([-2, 23, -5], [4, 4, 1], core)
    h = m.bone("head", [0, 30, 0], "body")
    h.box([-4, 30, -4], [8, 7, 8], dense, deco=[("north", dots([(1, 3, 2, 1, "#7ff0d0"), (5, 3, 2, 1, "#7ff0d0")]))])
    h.box([-3, 37, -1], [2, 4, 2], cloud)
    h.box([1, 37, 0], [2, 3, 2], cloud)
    h.box([-3, 33, 4], [6, 3, 6], cloud, rotation=[-20, 0, 0], pivot=[0, 34, 4])
    arms_pair(m, "body", lambda: {
        "pivot": [7, 28, 0],
        "upper": [C([6, 20, -2], [4, 9, 4], dense)],
        "fpivot": [8, 20, 0],
        "lower": [C([6, 12, -2], [4, 8, 4], cloud), C([5, 9, -3], [6, 4, 6], cloud)],
    })

    def rings(length, k):
        return {f"ring{i}": {"rotation": wave(length, (lambda i: lambda t: [0, 360 * turns * t / length, 0])(i))}
                for i, turns in zip(range(1, 6), [4 * k, 3 * k, 3 * k, 2 * k, k])}

    idle = rings(2.0, 1)
    idle.update({
        "body": {"position": wave(2.0, lambda t: [0, sn(t, 2), 0]), "rotation": wave(2.0, lambda t: [2 * sn(t, 2), 0, 0])},
        "arm_left": {"rotation": wave(2.0, lambda t: [5 * sn(t, 2), 0, -10 - 5 * sn(t, 2)])},
        "arm_right": {"rotation": wave(2.0, lambda t: [5 * sn(t, 2), 0, 10 + 5 * sn(t, 2)])},
        "forearm_left": {"rotation": wave(2.0, lambda t: [-10 + 8 * sn(t, 2, 1), 0, 0])},
        "forearm_right": {"rotation": wave(2.0, lambda t: [-10 + 8 * sn(t, 2, 1), 0, 0])},
    })
    fly = rings(1.0, 1)
    fly.update({
        "body": {"rotation": wave(1.0, lambda t: [15, 0, 0]), "position": wave(1.0, lambda t: [0, 0.6 * sn(t, 1), 0])},
        "vortex": {"rotation": wave(1.0, lambda t: [20, 0, 0])},
        "arm_left": {"rotation": wave(1.0, lambda t: [25 + 5 * sn(t, 1), 0, -12])},
        "arm_right": {"rotation": wave(1.0, lambda t: [25 + 5 * sn(t, 1), 0, 12])},
    })
    cyc = {f"ring{i}": chan(scale=[(0, [1, 1, 1]), (0.5, [1.5, 1.2, 1.5]), (3.3, [1.5, 1.2, 1.5]), (3.5, [1, 1, 1])]) for i in range(1, 6)}
    cyc.update({
        "body": rot([(0, Z3), (0.5, Z3), (3.5, [0, 1440, 0])]),
        "arm_left": rot([(0, Z3), (0.5, [-170, 0, -20]), (3.3, [-170, 0, -20]), (3.5, Z3)]),
        "arm_right": rot(mpts([(0, Z3), (0.5, [-170, 0, -20]), (3.3, [-170, 0, -20]), (3.5, Z3)])),
    })
    anims = {
        "idle": loop(2.0, idle),
        "fly": loop(1.0, fly),
        "attack": once(0.7, {
            "arm_right": rot([(0, Z3), (0.2, [-120, 0, 30]), (0.35, [-20, 0, -40]), (0.7, Z3)]),
            "body": rot([(0, Z3), (0.2, [0, -20, 0]), (0.35, [0, 25, 0]), (0.7, Z3)]),
        }),
        "cast": once(0.8, {
            "arm_left": rot([(0, Z3), (0.25, [20, 0, -10]), (0.4, [-90, 0, 0]), (0.8, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.25, [20, 0, -10]), (0.4, [-90, 0, 0]), (0.8, Z3)])),
            "body": chan(position=[(0, Z3), (0.4, [0, 0, 2]), (0.8, Z3)]),
        }),
        "cyclone": once(3.5, cyc),
    }
    m.write(anims, seed=14)


def lesser_phoenix():
    m = Model("lesser_phoenix", 64, 64)
    red = Feather("#d9361a", "#ff9a1a")
    orange = Feather("#ff7a1a", "#ffd23f")
    tip = Feather("#ff9a1a", "#fff1a8")
    flame = Flame()
    beak = Gold("#ffcc33", "#fff3a0", "#b07a10")
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, 8, 0], "root")
    b.box([-3, 5, -5], [6, 6, 10], red)
    b.box([-2, 4, -5], [4, 3, 6], orange)
    n = m.bone("neck", [0, 10, -4], "body")
    n.box([-2, 9, -8], [4, 5, 4], red)
    h = m.bone("head", [0, 13, -8], "neck")
    eye = dots([(1, 1, 1, 1, "#fff1a8"), (2, 1, 1, 1, "#1a0500")], glow=False)
    h.box([-2, 12, -11], [4, 4, 5], orange, deco=[("east", eye), ("west", eye)])
    h.box([-1, 12, -13], [2, 2, 2], beak)
    c = m.bone("crest", [0, 16, -9], "head")
    c.box([-1, 16, -10], [2, 3, 4], flame)
    c.box([-1, 17, -8], [2, 4, 3], flame, rotation=[25, 0, 0], pivot=[0, 17, -7])
    c.box([-1, 15, -6], [2, 2, 3], flame)
    for s, suffix in ((1, "left"), (-1, "right")):
        w = m.bone("wing_" + suffix, [3 * s, 10, -2], "body")
        o, sz = [3, 9, -5], [8, 1, 8]
        w.box(o if s > 0 else mirror(o, sz), sz, red)
        wt = m.bone("wing_" + suffix + "_tip", [11 * s, 10, -2], "wing_" + suffix)
        o, sz = [11, 9, -4], [8, 1, 7]
        wt.box(o if s > 0 else mirror(o, sz), sz, tip)
    t = m.bone("tail", [0, 8, 5], "body")
    t.box([-2, 7, 5], [4, 1, 6], red)
    tt = m.bone("tail_tip", [0, 8, 11], "tail")
    tt.box([-3, 7, 11], [2, 1, 9], flame)
    tt.box([-1, 7, 11], [2, 1, 12], flame)
    tt.box([1, 7, 11], [2, 1, 9], flame)
    lg = m.bone("legs", [0, 5, 0], "body")
    lg.box([-2, 1, -1], [1, 4, 1], beak)
    lg.box([1, 1, -1], [1, 4, 1], beak)
    lg.box([-2, 0, -2], [1, 1, 2], beak)
    lg.box([1, 0, -2], [1, 1, 2], beak)

    def flap(length, amp, base, period):
        return {
            "wing_left": {"rotation": wave(length, lambda t: [0, 0, -base - amp * sn(t, period)])},
            "wing_right": {"rotation": wave(length, lambda t: [0, 0, base + amp * sn(t, period)])},
            "wing_left_tip": {"rotation": wave(length, lambda t: [0, 0, -0.5 * amp * sn(t, period, -0.8)])},
            "wing_right_tip": {"rotation": wave(length, lambda t: [0, 0, 0.5 * amp * sn(t, period, -0.8)])},
        }

    idle = flap(1.0, 30, 10, 1.0)
    idle.update({
        "body": {"position": wave(1.0, lambda t: [0, sn(t, 1.0, math.pi), 0]), "rotation": wave(1.0, lambda t: [3 * sn(t, 1.0), 0, 0])},
        "tail": {"rotation": wave(1.0, lambda t: [5 * sn(t, 1.0), 0, 0])},
        "tail_tip": {"rotation": wave(1.0, lambda t: [6 * sn(t, 1.0, -1), 0, 0])},
        "legs": {"rotation": wave(1.0, lambda t: [20, 0, 0])},
        "crest": {"scale": wave(1.0, lambda t: [1, 1 + 0.15 * sn(t, 0.25), 1])},
    })
    fly = flap(0.6, 45, 5, 0.6)
    fly.update({
        "body": {"rotation": wave(0.6, lambda t: [10, 0, 0]), "position": wave(0.6, lambda t: [0, 0.8 * sn(t, 0.6, math.pi), 0])},
        "tail": {"rotation": wave(0.6, lambda t: [5 + 5 * sn(t, 0.6), 0, 0])},
        "legs": {"rotation": wave(0.6, lambda t: [60, 0, 0])},
        "crest": {"scale": wave(0.6, lambda t: [1, 1 + 0.2 * sn(t, 0.3), 1])},
    })
    anims = {
        "idle": loop(1.0, idle),
        "fly": loop(0.6, fly),
        "attack": once(0.6, {
            "body": rot([(0, Z3), (0.2, [-30, 0, 0]), (0.35, [20, 0, 0]), (0.6, Z3)]),
            "legs": rot([(0, Z3), (0.2, [-60, 0, 0]), (0.35, [10, 0, 0]), (0.6, Z3)]),
            "wing_left": rot([(0, Z3), (0.2, [0, 0, -60]), (0.35, [0, 0, 10]), (0.6, Z3)]),
            "wing_right": rot([(0, Z3), (0.2, [0, 0, 60]), (0.35, [0, 0, -10]), (0.6, Z3)]),
        }),
        "cast": once(1.1, {
            "neck": rot([(0, Z3), (0.4, [-20, 0, 0]), (0.5, [15, 0, 0]), (0.7, [-20, 0, 0]), (0.8, [15, 0, 0]), (1.1, Z3)]),
            "wing_left": rot([(0, Z3), (0.3, [0, 0, -40]), (0.9, [0, 0, -40]), (1.1, Z3)]),
            "wing_right": rot([(0, Z3), (0.3, [0, 0, 40]), (0.9, [0, 0, 40]), (1.1, Z3)]),
            "tail": rot([(0, Z3), (0.3, [15, 0, 0]), (0.9, [15, 0, 0]), (1.1, Z3)]),
        }),
        "dive": once(1.5, {
            "body": rot([(0, Z3), (0.4, [25, 0, 0]), (1.2, [25, 0, 0]), (1.35, [-15, 0, 0]), (1.5, Z3)]),
            "wing_left": rot([(0, Z3), (0.4, [0, 0, 30]), (1.2, [0, 0, 30]), (1.35, [0, 0, -40]), (1.5, Z3)]),
            "wing_right": rot([(0, Z3), (0.4, [0, 0, -30]), (1.2, [0, 0, -30]), (1.35, [0, 0, 40]), (1.5, Z3)]),
            "wing_left_tip": rot([(0, Z3), (0.4, [0, 0, 40]), (1.2, [0, 0, 40]), (1.5, Z3)]),
            "wing_right_tip": rot([(0, Z3), (0.4, [0, 0, -40]), (1.2, [0, 0, -40]), (1.5, Z3)]),
            "legs": rot([(0, Z3), (0.4, [-70, 0, 0]), (1.2, [-70, 0, 0]), (1.5, Z3)]),
        }),
        "rebirth": once(3.0, {
            "body": chan(scale=[(0, [1, 1, 1]), (0.5, [0.45, 0.45, 0.45]), (2.7, [0.45, 0.45, 0.45]), (2.85, [1.3, 1.3, 1.3]), (3.0, [1, 1, 1])],
                         rotation=[(0, Z3), (0.5, [0, 0, 0]), (2.7, [0, 720, 0]), (3.0, [0, 720, 0])]),
            "wing_left": rot([(0, Z3), (0.5, [0, 0, 70]), (2.7, [0, 0, 70]), (2.85, [0, 0, -70]), (3.0, Z3)]),
            "wing_right": rot([(0, Z3), (0.5, [0, 0, -70]), (2.7, [0, 0, -70]), (2.85, [0, 0, 70]), (3.0, Z3)]),
        }),
    }
    m.write(anims, seed=15, glow=False)


def storm_wyvern():
    m = Model("storm_wyvern", 128, 128)
    scale = Scales("#34405e", "#5a6f9c")
    dark = Scales("#262f47", "#3f4f75")
    belly = Scales("#8fa3c4", "#b9c8e0")
    horn = Mat("#d9d2c0", "#b8ae96", noise=0.05)
    mem = Membrane("#4a6fa8", "#2a3f66")
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, 14, 0], "root")
    b.box([-6, 9, -9], [12, 10, 18], scale)
    b.box([-5, 8, -8], [10, 2, 15], belly)
    for z in (-6, -1, 4):
        b.box([-1, 19, z], [2, 2, 3], horn)
    n = m.bone("neck", [0, 16, -9], "body")
    n.box([-3, 13, -16], [6, 6, 8], scale)
    n.box([-1, 19, -14], [2, 2, 3], horn)
    h = m.bone("head", [0, 17, -16], "neck")
    h.box([-4, 14, -24], [8, 7, 9], scale, deco=[("north", dots([(0, 2, 2, 1, "#7fdbff"), (6, 2, 2, 1, "#7fdbff")]))])
    h.box([-3, 14, -29], [6, 4, 5], scale, deco=[("north", dots([(1, 1, 1, 1, "#10131c"), (4, 1, 1, 1, "#10131c")], glow=False))])
    h.box([-4, 20, -20], [2, 2, 7], horn, rotation=[25, 0, 0], pivot=[-3, 20, -18])
    h.box([2, 20, -20], [2, 2, 7], horn, rotation=[25, 0, 0], pivot=[3, 20, -18])
    j = m.bone("jaw", [0, 14, -22], "head")
    j.box([-3, 12, -29], [6, 2, 8], belly, deco=[("north", dots([(0, 0, 6, 1, "#f0ecdc")], glow=False))])
    for s, suffix in ((1, "left"), (-1, "right")):
        w = m.bone("wing_" + suffix, [6 * s, 18, -4], "body")
        for o, sz, mat in (([6, 17, -5], [12, 2, 3], scale), ([6, 17, -2], [12, 1, 12], mem)):
            w.box(o if s > 0 else mirror(o, sz), sz, mat)
        wt = m.bone("wing_" + suffix + "_tip", [18 * s, 18, -4], "wing_" + suffix)
        for o, sz, mat in (([18, 17, -5], [14, 2, 2], scale), ([18, 17, -3], [14, 1, 13], mem), ([17, 19, -6], [1, 2, 1], horn)):
            wt.box(o if s > 0 else mirror(o, sz), sz, mat)
        lg = m.bone("leg_" + suffix, [4 * s, 10, 3], "body")
        for o, sz, mat in (([3, 3, 1], [4, 8, 5], scale), ([2, 0, -2], [6, 3, 6], dark), ([2, 0, -3], [6, 1, 1], horn)):
            lg.box(o if s > 0 else mirror(o, sz), sz, mat)
    t1 = m.bone("tail", [0, 13, 9], "body")
    t1.box([-3, 10, 9], [6, 6, 8], scale)
    t2 = m.bone("tail2", [0, 13, 17], "tail")
    t2.box([-2, 11, 17], [4, 4, 8], scale)
    t3 = m.bone("tail3", [0, 13, 25], "tail2")
    t3.box([-1, 12, 25], [2, 2, 9], scale)
    t3.box([-3, 12, 32], [6, 1, 5], mem)

    def flap(length, amp, base, period):
        return {
            "wing_left": {"rotation": wave(length, lambda t: [0, 0, -base - amp * sn(t, period)])},
            "wing_right": {"rotation": wave(length, lambda t: [0, 0, base + amp * sn(t, period)])},
            "wing_left_tip": {"rotation": wave(length, lambda t: [0, 0, -0.6 * amp * sn(t, period, -1)])},
            "wing_right_tip": {"rotation": wave(length, lambda t: [0, 0, 0.6 * amp * sn(t, period, -1)])},
        }

    def tail(length, amp):
        return {
            "tail": {"rotation": wave(length, lambda t: [3 * sn(t, length), 0, amp * sn(t, length)])},
            "tail2": {"rotation": wave(length, lambda t: [0, 0, amp * 1.3 * sn(t, length, -0.8)])},
            "tail3": {"rotation": wave(length, lambda t: [0, 0, amp * 1.6 * sn(t, length, -1.6)])},
        }

    idle = flap(1.4, 35, 10, 1.4)
    idle.update(tail(1.4, 6))
    idle.update({
        "body": {"position": wave(1.4, lambda t: [0, 2 * sn(t, 1.4, math.pi), 0])},
        "neck": {"rotation": wave(1.4, lambda t: [4 * sn(t, 1.4, 1), 0, 0])},
        "leg_left": {"rotation": wave(1.4, lambda t: [30, 0, 0])},
        "leg_right": {"rotation": wave(1.4, lambda t: [30, 0, 0])},
    })
    fly = flap(1.0, 45, 5, 1.0)
    fly.update(tail(1.0, 4))
    fly.update({
        "body": {"rotation": wave(1.0, lambda t: [5, 0, 0]), "position": wave(1.0, lambda t: [0, 1.5 * sn(t, 1.0, math.pi), 0])},
        "leg_left": {"rotation": wave(1.0, lambda t: [65, 0, 0])},
        "leg_right": {"rotation": wave(1.0, lambda t: [65, 0, 0])},
    })
    anims = {
        "idle": loop(1.4, idle),
        "fly": loop(1.0, fly),
        "attack": once(0.7, {
            "neck": rot([(0, Z3), (0.2, [-25, 0, 0]), (0.35, [25, 0, 0]), (0.7, Z3)]),
            "jaw": rot([(0, Z3), (0.2, [35, 0, 0]), (0.35, Z3), (0.7, Z3)]),
            "body": chan(position=[(0, Z3), (0.35, [0, 0, -4]), (0.7, Z3)]),
        }),
        "roar": once(1.5, {
            "neck": rot([(0, Z3), (0.4, [-35, 0, 0]), (1.2, [-35, 0, 0]), (1.5, Z3)]),
            "jaw": rot([(0, Z3), (0.4, [40, 0, 0]), (1.2, [40, 0, 0]), (1.5, Z3)]),
            "wing_left": rot([(0, Z3), (0.4, [0, 0, -50]), (1.2, [0, 0, -50]), (1.5, Z3)]),
            "wing_right": rot([(0, Z3), (0.4, [0, 0, 50]), (1.2, [0, 0, 50]), (1.5, Z3)]),
            "body": rot([(0, Z3), (0.4, [-15, 0, 0]), (1.2, [-15, 0, 0]), (1.5, Z3)]),
        }),
        "dive": once(1.7, {
            "body": rot([(0, Z3), (0.5, [20, 0, 0]), (1.2, [20, 0, 0]), (1.4, [-10, 0, 0]), (1.7, Z3)]),
            "wing_left": rot([(0, Z3), (0.5, [0, 0, 25]), (1.2, [0, 0, 25]), (1.4, [0, 0, -30]), (1.7, Z3)]),
            "wing_right": rot([(0, Z3), (0.5, [0, 0, -25]), (1.2, [0, 0, -25]), (1.4, [0, 0, 30]), (1.7, Z3)]),
            "wing_left_tip": rot([(0, Z3), (0.5, [0, 0, 35]), (1.2, [0, 0, 35]), (1.7, Z3)]),
            "wing_right_tip": rot([(0, Z3), (0.5, [0, 0, -35]), (1.2, [0, 0, -35]), (1.7, Z3)]),
            "leg_left": rot([(0, Z3), (0.5, [-40, 0, 0]), (1.2, [-40, 0, 0]), (1.7, Z3)]),
            "leg_right": rot([(0, Z3), (0.5, [-40, 0, 0]), (1.2, [-40, 0, 0]), (1.7, Z3)]),
            "jaw": rot([(0, Z3), (0.5, [25, 0, 0]), (1.2, [25, 0, 0]), (1.7, Z3)]),
        }),
    }
    m.write(anims, seed=16)


# =====================================================================================
#  ABISMO
# =====================================================================================
def void_crawler():
    m = Model("void_crawler", 128, 64)
    chitin = Chitin("#1d1528", "#5a3a88")
    shell = Chitin("#241a33", "#7a4ab8", spots="#b066ff", spot_rate=0.07)
    legc = Chitin("#16101f", "#4a2f70")
    fang = Mat("#cfc6e0", "#a89cc0", noise=0.04)
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, 7, 0], "root")
    b.box([-5, 4, -6], [10, 6, 10], chitin)
    h = m.bone("head", [0, 7, -6], "body")
    eyes = [(2, 1, 1, 1, "#d08aff"), (5, 1, 1, 1, "#d08aff"), (1, 3, 1, 1, "#a04bff"), (6, 3, 1, 1, "#a04bff"),
            (3, 2, 1, 1, "#e8c6ff"), (4, 2, 1, 1, "#e8c6ff")]
    h.box([-4, 4, -12], [8, 6, 6], chitin, deco=[("north", dots(eyes))])
    for s, suffix in ((1, "left"), (-1, "right")):
        md = m.bone("mandible_" + suffix, [2 * s, 5, -12], "head")
        o, sz = [1, 3, -15], [2, 2, 4]
        md.box(o if s > 0 else mirror(o, sz), sz, fang)
    a = m.bone("abdomen", [0, 8, 4], "body")
    a.box([-6, 4, 4], [12, 8, 12], shell)
    a.box([-1, 8, 16], [2, 2, 3], chitin)
    for s, side in ((1, "l"), (-1, "r")):
        for i, z in enumerate((-5, -1, 3), start=1):
            up = m.bone(f"leg_{side}{i}", [5 * s, 7, z + 1], "body", rotation=[0, 0, -25 * s])
            o, sz = [5, 6, z], [8, 2, 2]
            up.box(o if s > 0 else mirror(o, sz), sz, legc)
            lo = m.bone(f"leg_{side}{i}_low", [13 * s, 7, z + 1], f"leg_{side}{i}")
            o, sz = [12, -3, z], [2, 11, 2]
            lo.box(o if s > 0 else mirror(o, sz), sz, legc)

    def gait(length, amp, lift):
        out = {}
        for s, side in ((1, "l"), (-1, "r")):
            for i in (1, 2, 3):
                ph = 0 if ((i % 2 == 1) == (s > 0)) else math.pi
                fn = (lambda ph, s: lambda t: [0, amp * sn(t, length, ph), -s * lift * max(0.0, sn(t, length, ph + math.pi / 2))])(ph, s)
                out[f"leg_{side}{i}"] = {"rotation": wave(length, fn)}
        return out

    walk = gait(0.6, 18, 15)
    walk.update({"body": {"position": wave(0.6, lambda t: [0, 0.4 * abs(sn(t, 0.6)), 0])},
                 "abdomen": {"rotation": wave(0.6, lambda t: [3 * sn(t, 0.3), 0, 0])}})
    idle = gait(3.0, 3, 0)
    idle.update({
        "abdomen": {"rotation": wave(3.0, lambda t: [4 * sn(t, 1.5), 0, 0]),
                    "scale": wave(3.0, lambda t: [1 + 0.03 * sn(t, 1.5), 1 + 0.03 * sn(t, 1.5), 1])},
        "mandible_left": {"rotation": wave(3.0, lambda t: [0, -10 * max(0.0, sn(t, 1.0)), 0])},
        "mandible_right": {"rotation": wave(3.0, lambda t: [0, 10 * max(0.0, sn(t, 1.0)), 0])},
    })
    anims = {
        "idle": loop(3.0, idle),
        "walk": loop(0.6, walk),
        "attack": once(0.5, {
            "body": rot([(0, Z3), (0.15, [-15, 0, 0]), (0.25, [12, 0, 0]), (0.5, Z3)]),
            "mandible_left": rot([(0, Z3), (0.15, [0, -30, 0]), (0.25, [0, 10, 0]), (0.5, Z3)]),
            "mandible_right": rot([(0, Z3), (0.15, [0, 30, 0]), (0.25, [0, -10, 0]), (0.5, Z3)]),
            "leg_l1": rot([(0, Z3), (0.15, [0, 0, -40]), (0.3, Z3), (0.5, Z3)]),
            "leg_r1": rot([(0, Z3), (0.15, [0, 0, 40]), (0.3, Z3), (0.5, Z3)]),
        }),
        "pounce": once(1.1, {
            "body": chan(rotation=[(0, Z3), (0.3, [10, 0, 0]), (0.45, [-20, 0, 0]), (0.8, [5, 0, 0]), (1.1, Z3)],
                         position=[(0, Z3), (0.3, [0, -2, 0]), (0.5, Z3), (1.1, Z3)]),
            "leg_l1": rot([(0, Z3), (0.45, [0, -20, -50]), (0.9, Z3), (1.1, Z3)]),
            "leg_r1": rot([(0, Z3), (0.45, [0, 20, 50]), (0.9, Z3), (1.1, Z3)]),
            "leg_l2": rot([(0, Z3), (0.45, [0, -10, -30]), (0.9, Z3), (1.1, Z3)]),
            "leg_r2": rot([(0, Z3), (0.45, [0, 10, 30]), (0.9, Z3), (1.1, Z3)]),
        }),
        "blink": once(0.5, {
            "body": chan(scale=[(0, [1, 1, 1]), (0.25, [0.2, 1.6, 0.2]), (0.3, [1.3, 0.7, 1.3]), (0.5, [1, 1, 1])]),
        }),
    }
    m.write(anims, seed=21)


def watcher_eye():
    m = Model("watcher_eye", 128, 128)
    white = Mat("#e8e0f0", "#d8cce6", noise=0.04, edge=0.1)
    lid = Chitin("#2a1838", "#5a2a7a")
    flesh = Chitin("#3a1f4a", "#6a3a88")
    shard = Crystal("#a04bff", alpha=255, glow=True)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 10, 0], "root")
    h = m.bone("head", [0, 10, 0], "body")
    veins = dots([(0, 2, 2, 1, "#c04a6a"), (12, 5, 2, 1, "#c04a6a"), (1, 11, 2, 1, "#c04a6a"), (11, 12, 3, 1, "#c04a6a")], glow=False)
    h.box([-7, 3, -7], [14, 14, 14], white, deco=[("north", veins)])
    # cornea saltada (da volume ao olho) com a iris, e laterais arredondadas
    h.box([-5, 5, -8], [10, 10, 1], white, deco=[("north", iris("#a04bff", ring="#f0d8ff"))])
    h.box([-8, 5, -5], [1, 10, 10], white)
    h.box([7, 5, -5], [1, 10, 10], white)
    m.bone("lid_top", [0, 16, 2], "head").box([-8, 15, -8], [16, 3, 15], lid)
    m.bone("lid_bottom", [0, 4, 2], "head").box([-8, 2, -8], [16, 3, 15], lid)
    back = (1, 2, 3, 4)
    for i, (x, y) in zip(back, ((4, 13), (-4, 13), (4, 7), (-4, 7))):
        a = m.bone(f"tendril_{i}", [x, y, 7], "body")
        a.box([x - 1, y - 1, 7], [2, 2, 7], flesh)
        bb = m.bone(f"tendril_{i}b", [x, y, 14], f"tendril_{i}")
        bb.box([x - 1, y - 1, 14], [2, 2, 7], flesh)
        cc = m.bone(f"tendril_{i}c", [x, y, 21], f"tendril_{i}b")
        cc.box([x - 0.5, y - 0.5, 21], [1, 1, 5], flesh)
    for i, x in ((5, 3), (6, -3)):
        a = m.bone(f"tendril_{i}", [x, 3, 0], "body")
        a.box([x - 1, -4, -1], [2, 7, 2], flesh)
        bb = m.bone(f"tendril_{i}b", [x, -4, 0], f"tendril_{i}")
        bb.box([x - 0.5, -9, -0.5], [1, 5, 1], flesh)
    s = m.bone("shards", [0, 10, 0], "body")
    for o in ([7, 9, 7], [-9, 9, 7], [7, 9, -9], [-9, 9, -9]):
        s.box(o, [2, 3, 2], shard)

    def drift(length, amp):
        out = {}
        for i in back:
            ph = i * 1.3
            out[f"tendril_{i}"] = {"rotation": wave(length, (lambda ph: lambda t: [amp * sn(t, length, ph), 0, amp * 0.6 * sn(t, length, ph + 1)])(ph))}
            out[f"tendril_{i}b"] = {"rotation": wave(length, (lambda ph: lambda t: [amp * 1.4 * sn(t, length, ph - 0.8), 0, 0])(ph))}
            out[f"tendril_{i}c"] = {"rotation": wave(length, (lambda ph: lambda t: [amp * 1.8 * sn(t, length, ph - 1.6), 0, 0])(ph))}
        for i in (5, 6):
            out[f"tendril_{i}"] = {"rotation": wave(length, (lambda i: lambda t: [amp * 0.8 * sn(t, length, i), 0, 0])(i))}
            out[f"tendril_{i}b"] = {"rotation": wave(length, (lambda i: lambda t: [amp * sn(t, length, i - 1), 0, 0])(i))}
        return out

    idle = drift(3.0, 10)
    idle.update({
        "body": {"position": wave(3.0, lambda t: [0, 1.2 * sn(t, 3.0), 0])},
        "shards": {"rotation": wave(3.0, lambda t: [0, 120 * t, 0])},
        "lid_top": {"position": keys([(0, Z3), (2.5, Z3), (2.65, [0, -5, 0]), (2.8, Z3), (3.0, Z3)])},
        "lid_bottom": {"position": keys([(0, Z3), (2.5, Z3), (2.65, [0, 4, 0]), (2.8, Z3), (3.0, Z3)])},
    })
    fly = drift(2.0, 16)
    fly.update({
        "body": {"rotation": wave(2.0, lambda t: [10, 0, 0]), "position": wave(2.0, lambda t: [0, 0.8 * sn(t, 2.0), 0])},
        "shards": {"rotation": wave(2.0, lambda t: [0, 180 * t, 0])},
    })
    anims = {
        "idle": loop(3.0, idle),
        "fly": loop(2.0, fly),
        "attack": once(0.6, {
            "tendril_5": rot([(0, Z3), (0.15, [30, 0, 0]), (0.3, [-70, 0, 0]), (0.6, Z3)]),
            "tendril_6": rot([(0, Z3), (0.15, [30, 0, 0]), (0.3, [-70, 0, 0]), (0.6, Z3)]),
            "body": chan(position=[(0, Z3), (0.3, [0, 0, -2]), (0.6, Z3)]),
        }),
        "beam": once(2.3, {
            "lid_top": pos([(0, Z3), (0.2, [0, 2, 0]), (2.1, [0, 2, 0]), (2.3, Z3)]),
            "lid_bottom": pos([(0, Z3), (0.2, [0, -2, 0]), (2.1, [0, -2, 0]), (2.3, Z3)]),
            "shards": rot([(0, Z3), (2.0, [0, 1080, 0]), (2.3, [0, 1080, 0])]),
            "head": chan(scale=[(0, [1, 1, 1]), (1.9, [1.12, 1.12, 1.12]), (2.05, [0.95, 0.95, 0.95]), (2.3, [1, 1, 1])]),
            "body": chan(position=[(0, Z3), (2.0, Z3), (2.1, [0, 0, 3]), (2.3, Z3)]),
        }),
        "gaze": once(1.1, {
            "head": chan(scale=[(0, [1, 1, 1]), (0.4, [1.2, 1.2, 1.2]), (0.8, [1.2, 1.2, 1.2]), (1.1, [1, 1, 1])]),
            "lid_top": pos([(0, Z3), (0.3, [0, 3, 0]), (0.9, [0, 3, 0]), (1.1, Z3)]),
            "lid_bottom": pos([(0, Z3), (0.3, [0, -3, 0]), (0.9, [0, -3, 0]), (1.1, Z3)]),
            "shards": rot([(0, Z3), (1.1, [0, -360, 0])]),
        }),
    }
    m.write(anims, seed=22)


def young_kraken():
    m = Model("young_kraken", 128, 64)
    skin = Chitin("#2b3d6e", "#4a68a8", spots="#6be0d0", spot_rate=0.05)
    limb = Chitin("#33497f", "#5a7fc0", spots="#e0b0c8", spot_rate=0.04)
    eye = Mat("#ffd84a", "#ffe680", glow=True, edge=0.0, noise=0.03)
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, 12, 0], "root")
    slit = dots([(1, 0, 1, 3, "#140c04")], glow=False)
    b.box([-7, 12, -4], [1, 3, 3], eye, deco=[("east", slit), ("west", slit)])
    b.box([6, 12, -4], [1, 3, 3], eye, deco=[("east", slit), ("west", slit)])
    b.box([-1, 10, -7], [2, 2, 2], limb)
    mt = m.bone("mantle", [0, 14, 2], "body")
    mt.box([-6, 10, -6], [12, 9, 12], skin)
    mt.box([-5, 19, -4], [10, 6, 10], skin, rotation=[-15, 0, 0], pivot=[0, 19, 1])
    mt.box([-3, 24, -1], [6, 4, 6], skin, rotation=[-25, 0, 0], pivot=[0, 24, 2])
    angles = []
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        px, pz = round(4.2 * math.cos(a), 2), round(4.2 * math.sin(a), 2)
        sa, ca = math.sin(a), math.cos(a)
        t1 = m.bone(f"tentacle_{i}", [px, 11, pz], "body", rotation=[round(35 * sa, 2), 0, round(-35 * ca, 2)])
        t1.box([px - 1, 4, pz - 1], [2, 7, 2], limb)
        t2 = m.bone(f"tentacle_{i}b", [px, 4, pz], f"tentacle_{i}", rotation=[round(25 * sa, 2), 0, round(-25 * ca, 2)])
        t2.box([px - 1, -2, pz - 1], [2, 6, 2], limb)
        t3 = m.bone(f"tentacle_{i}c", [px, -2, pz], f"tentacle_{i}b", rotation=[round(30 * sa, 2), 0, round(-30 * ca, 2)])
        t3.box([px - 0.5, -6, pz - 0.5], [1, 4, 1], limb)
        angles.append((sa, ca))
    for s, suffix in ((1, "left"), (-1, "right")):
        ar = m.bone("arm_" + suffix, [2 * s, 11, -5], "body", rotation=[-45, 0, 0])
        o, sz = [1, 1, -6], [2, 10, 2]
        ar.box(o if s > 0 else mirror(o, sz), sz, limb)
        ab = m.bone("arm_" + suffix + "b", [2 * s, 1, -5], "arm_" + suffix, rotation=[-30, 0, 0])
        o, sz = [1, -7, -6], [2, 8, 2]
        ab.box(o if s > 0 else mirror(o, sz), sz, limb)
        o, sz = [0.5, -9, -6.5], [3, 2, 3]
        ab.box(o if s > 0 else mirror(o, sz), sz, skin)

    def tentacles(length, amp, extra=0.0):
        out = {}
        for i, (sa, ca) in enumerate(angles):
            ph = i * 0.8
            for suffix, k, d in (("", 1.0, 0), ("b", 1.3, -0.9), ("c", 1.6, -1.8)):
                fn = (lambda sa, ca, k, d, ph: lambda t: [(extra + amp * k * sn(t, length, ph + d)) * sa, 0,
                                                         -(extra + amp * k * sn(t, length, ph + d)) * ca])(sa, ca, k, d, ph)
                out[f"tentacle_{i}{suffix}"] = {"rotation": wave(length, fn)}
        return out

    idle = tentacles(3.0, 6)
    idle.update({
        "mantle": {"scale": wave(3.0, lambda t: [1 + 0.04 * sn(t, 1.5), 1 - 0.03 * sn(t, 1.5), 1 + 0.04 * sn(t, 1.5)])},
        "arm_left": {"rotation": wave(3.0, lambda t: [10 * sn(t, 3.0), 0, 0])},
        "arm_right": {"rotation": wave(3.0, lambda t: [10 * sn(t, 3.0, 1.5), 0, 0])},
    })
    walk = tentacles(2.0, 15)
    walk.update({
        "body": {"position": wave(2.0, lambda t: [0, abs(sn(t, 2.0)), 0]), "rotation": wave(2.0, lambda t: [5, 0, 3 * sn(t, 2.0)])},
        "arm_left": {"rotation": wave(2.0, lambda t: [15 * sn(t, 2.0), 0, 0])},
        "arm_right": {"rotation": wave(2.0, lambda t: [-15 * sn(t, 2.0), 0, 0])},
    })
    swim = tentacles(1.2, 12, extra=-15)
    swim.update({
        "body": {"rotation": wave(1.2, lambda t: [55, 0, 0])},
        "mantle": {"scale": keys([(0, [1, 1, 1]), (0.3, [1.15, 0.9, 1.15]), (0.6, [0.9, 1.1, 0.9]), (1.2, [1, 1, 1])])},
        "arm_left": {"rotation": wave(1.2, lambda t: [30, 0, 0])},
        "arm_right": {"rotation": wave(1.2, lambda t: [30, 0, 0])},
    })
    anims = {
        "idle": loop(3.0, idle),
        "walk": loop(2.0, walk),
        "swim": loop(1.2, swim),
        "attack": once(0.7, {
            "arm_left": rot([(0, Z3), (0.25, [-50, 0, 0]), (0.4, [30, 0, 0]), (0.7, Z3)]),
            "arm_right": rot([(0, Z3), (0.25, [-50, 0, 0]), (0.4, [30, 0, 0]), (0.7, Z3)]),
            "arm_leftb": rot([(0, Z3), (0.25, [-30, 0, 0]), (0.4, [20, 0, 0]), (0.7, Z3)]),
            "arm_rightb": rot([(0, Z3), (0.25, [-30, 0, 0]), (0.4, [20, 0, 0]), (0.7, Z3)]),
        }),
        "grab": once(2.9, {
            "arm_left": rot([(0, Z3), (0.3, [-40, 0, 0]), (0.4, [20, 0, -10]), (2.7, [20, 0, -10]), (2.8, [-60, 0, 0]), (2.9, Z3)]),
            "arm_right": rot([(0, Z3), (0.3, [-40, 0, 0]), (0.4, [20, 0, 10]), (2.7, [20, 0, 10]), (2.8, [-60, 0, 0]), (2.9, Z3)]),
            "arm_leftb": rot([(0, Z3), (0.4, [40, 0, 0]), (2.7, [40, 0, 0]), (2.9, Z3)]),
            "arm_rightb": rot([(0, Z3), (0.4, [40, 0, 0]), (2.7, [40, 0, 0]), (2.9, Z3)]),
            "mantle": chan(scale=[(0, [1, 1, 1]), (0.9, [1.08, 0.94, 1.08]), (1.4, [0.95, 1.05, 0.95]), (1.9, [1.08, 0.94, 1.08]),
                                  (2.4, [0.95, 1.05, 0.95]), (2.9, [1, 1, 1])]),
        }),
        "ink": once(0.6, {
            "mantle": chan(scale=[(0, [1, 1, 1]), (0.15, [1.25, 0.8, 1.25]), (0.3, [0.85, 1.15, 0.85]), (0.6, [1, 1, 1])]),
            "body": chan(position=[(0, Z3), (0.3, [0, 2, 0]), (0.6, Z3)]),
        }),
        "cast": once(0.9, {
            "mantle": chan(scale=[(0, [1, 1, 1]), (0.4, [1.1, 0.9, 1.1]), (0.5, [0.9, 1.1, 0.9]), (0.9, [1, 1, 1])]),
            "body": chan(position=[(0, Z3), (0.5, [0, 0, 2]), (0.9, Z3)]),
        }),
    }
    m.write(anims, seed=23)


# =====================================================================================
#  SANTUARIO CELESTIAL
# =====================================================================================
def angel_sentinel():
    m = Model("angel_sentinel", 128, 128)
    cloth = Cloth("#f2eee2")
    gold = Gold()
    feather = Feather("#ffffff", "#e8e0c8", line="#dcd6c8")
    halo = Mat("#fff3b0", "#ffe27a", glow=True, edge=0.0, shading=0.0, noise=0.03)
    shaft = Gold("#d9a830", "#ffe9a0", "#8a5a10")
    blade = Crystal("#fff4c0", alpha=255, glow=True)
    m.bone("root", [0, 0, 0])
    for s, suffix in ((1, "left"), (-1, "right")):
        lg = m.bone("leg_" + suffix, [2 * s, 15, 0], "root")
        o, sz = [0, 0, -2], [4, 15, 4]
        lg.box(o if s > 0 else mirror(o, sz), sz, cloth)
        o2 = [0, 0, -2]
        lg.box(o2 if s > 0 else mirror(o2, [4, 7, 4]), [4, 7, 4], gold, inflate=0.25)
    b = m.bone("body", [0, 22, 0], "root")
    b.box([-5, 12, -3], [10, 8, 6], cloth)
    b.box([-4, 20, -2], [8, 12, 4], cloth)
    b.box([-4, 24, -2], [8, 8, 4], gold, inflate=0.3)
    b.box([-4, 20, -2], [8, 1, 4], gold, inflate=0.35)
    b.box([-1, 12, -3.5], [2, 8, 1], gold)
    h = m.bone("head", [0, 32, 0], "body")
    h.box([-4, 32, -4], [8, 8, 8], gold, deco=[("north", dots([(1, 3, 6, 1, "#fff4c0"), (3, 4, 2, 2, "#fff4c0")]))])
    h.box([-1, 40, -3], [2, 3, 7], cloth)
    h.box([-5, 35, -1], [1, 3, 4], feather)
    h.box([4, 35, -1], [1, 3, 4], feather)
    hl = m.bone("halo", [0, 44, 0], "head")
    for o, sz in (([-4, 43, -4], [8, 1, 1]), ([-4, 43, 3], [8, 1, 1]), ([-4, 43, -3], [1, 1, 6]), ([3, 43, -3], [1, 1, 6])):
        hl.box(o, sz, halo)
    for s, suffix in ((1, "left"), (-1, "right")):
        ar = m.bone("arm_" + suffix, [5 * s, 30, 0], "body")
        for o, sz, mat, inf in (([4, 28, -3], [5, 4, 6], gold, 0), ([5, 18, -2], [3, 11, 4], cloth, 0), ([5, 17, -2], [3, 5, 4], gold, 0.2)):
            ar.box(o if s > 0 else mirror(o, sz), sz, mat, inflate=inf)
        w = m.bone("wing_" + suffix, [2 * s, 29, 2], "body", rotation=[0, 0, -20 * s])
        for o, sz in (([2, 19, 2], [13, 12, 1]), ([6, 13, 2], [9, 6, 1])):
            w.box(o if s > 0 else mirror(o, sz), sz, feather)
        wt = m.bone("wing_" + suffix + "_tip", [15 * s, 29, 2], "wing_" + suffix, rotation=[0, 0, -15 * s])
        o, sz = [15, 17, 2], [10, 13, 1]
        wt.box(o if s > 0 else mirror(o, sz), sz, feather)
    sp = m.bone("spear", [-6.5, 18, 0], "arm_right")
    sp.box([-7, 2, -0.5], [1, 38, 1], shaft)
    sp.box([-7.5, 40, -1], [2, 7, 2], blade)
    sp.box([-8.5, 39, -1], [4, 1, 2], gold)

    def wings(length, amp, period):
        return {
            "wing_left": {"rotation": wave(length, lambda t: [0, 0, -amp * sn(t, period)])},
            "wing_right": {"rotation": wave(length, lambda t: [0, 0, amp * sn(t, period)])},
            "wing_left_tip": {"rotation": wave(length, lambda t: [0, 0, -0.7 * amp * sn(t, period, -0.7)])},
            "wing_right_tip": {"rotation": wave(length, lambda t: [0, 0, 0.7 * amp * sn(t, period, -0.7)])},
        }

    idle = wings(2.5, 8, 2.5)
    idle.update({
        "body": {"position": wave(2.5, lambda t: [0, sn(t, 2.5), 0])},
        "halo": {"rotation": wave(2.5, lambda t: [0, 144 * t, 0])},
        "arm_left": {"rotation": wave(2.5, lambda t: [2 * sn(t, 2.5), 0, -3])},
        "arm_right": {"rotation": wave(2.5, lambda t: [-10, 0, 3])},
        "leg_left": {"rotation": wave(2.5, lambda t: [8 + 3 * sn(t, 2.5), 0, 0])},
        "leg_right": {"rotation": wave(2.5, lambda t: [6 + 3 * sn(t, 2.5, 1), 0, 0])},
    })
    fly = wings(1.0, 30, 1.0)
    fly.update({
        "body": {"rotation": wave(1.0, lambda t: [15, 0, 0]), "position": wave(1.0, lambda t: [0, 0.8 * sn(t, 1.0, math.pi), 0])},
        "halo": {"rotation": wave(1.0, lambda t: [0, 360 * t, 0])},
        "leg_left": {"rotation": wave(1.0, lambda t: [20, 0, 0])},
        "leg_right": {"rotation": wave(1.0, lambda t: [25, 0, 0])},
        "arm_right": {"rotation": wave(1.0, lambda t: [-10, 0, 3])},
    })
    anims = {
        "idle": loop(2.5, idle),
        "fly": loop(1.0, fly),
        "attack": once(0.7, {
            "arm_right": rot([(0, Z3), (0.25, [20, 0, 0]), (0.4, [-45, 0, 0]), (0.55, [-40, 0, 0]), (0.7, Z3)]),
            "spear": rot([(0, Z3), (0.25, [90, 0, 0]), (0.4, [125, 0, 0]), (0.55, [125, 0, 0]), (0.7, Z3)]),
            "body": chan(position=[(0, Z3), (0.4, [0, 0, -2]), (0.7, Z3)], rotation=[(0, Z3), (0.25, [0, -15, 0]), (0.4, [8, 10, 0]), (0.7, Z3)]),
        }),
        "cast": once(1.0, {
            "arm_left": rot([(0, Z3), (0.4, [-160, 0, -10]), (0.6, [-60, 0, 0]), (1.0, Z3)]),
            "body": rot([(0, Z3), (0.4, [-8, 15, 0]), (0.6, [10, -10, 0]), (1.0, Z3)]),
        }),
        "judgment": once(2.0, {
            "arm_right": rot([(0, Z3), (0.5, [-170, 0, 0]), (1.3, [-170, 0, 0]), (1.45, [-40, 0, 0]), (2.0, Z3)]),
            "arm_left": rot([(0, Z3), (0.5, [-120, 0, -30]), (1.3, [-120, 0, -30]), (1.45, [-20, 0, -20]), (2.0, Z3)]),
            "wing_left": rot([(0, Z3), (0.5, [0, 0, -45]), (1.3, [0, 0, -45]), (2.0, Z3)]),
            "wing_right": rot([(0, Z3), (0.5, [0, 0, 45]), (1.3, [0, 0, 45]), (2.0, Z3)]),
            "body": chan(position=[(0, Z3), (0.5, [0, 3, 0]), (1.3, [0, 3, 0]), (1.45, Z3), (2.0, Z3)]),
            "halo": chan(scale=[(0, [1, 1, 1]), (0.5, [1.4, 1, 1.4]), (1.3, [1.4, 1, 1.4]), (1.45, [1.8, 1, 1.8]), (2.0, [1, 1, 1])]),
        }),
        "aegis": once(1.0, {
            "arm_left": rot([(0, Z3), (0.4, [-90, 0, 40]), (0.8, [-90, 0, 40]), (1.0, Z3)]),
            "arm_right": rot([(0, Z3), (0.4, [-90, 0, -40]), (0.8, [-90, 0, -40]), (1.0, Z3)]),
            "wing_left": rot([(0, Z3), (0.4, [0, 0, 20]), (0.8, [0, 0, 20]), (1.0, Z3)]),
            "wing_right": rot([(0, Z3), (0.4, [0, 0, -20]), (0.8, [0, 0, -20]), (1.0, Z3)]),
            "halo": chan(scale=[(0, [1, 1, 1]), (0.4, [1.5, 1, 1.5]), (1.0, [1, 1, 1])]),
        }),
    }
    m.write(anims, seed=31)


def cherub():
    m = Model("cherub", 64, 64)
    skin = Mat("#ffe0cc", "#ffd2bc", noise=0.03, edge=0.06)
    cloth = Cloth("#ffffff")
    hair = Curls()
    feather = Feather("#ffffff", "#f0e8d8", line="#e6e0d6")
    halo = Mat("#fff3b0", "#ffe27a", glow=True, edge=0.0, shading=0.0, noise=0.03)
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, 6, 0], "root")
    b.box([-3, 3, -3], [6, 6, 5], skin)
    b.box([-3, 3, -3], [6, 3, 5], cloth, inflate=0.25)
    h = m.bone("head", [0, 9, 0], "body")
    face = dots([(2, 3, 1, 2, "#3a6fd8"), (5, 3, 1, 2, "#3a6fd8"), (1, 5, 1, 1, "#ffb0b0"), (6, 5, 1, 1, "#ffb0b0"),
                 (3, 5, 2, 1, "#d98080")], glow=False)
    h.box([-4, 9, -4], [8, 7, 7], skin, deco=[("north", face)])
    h.box([-4, 15, -4], [8, 2, 7], hair)
    h.box([-5, 11, -3], [1, 5, 6], hair)
    h.box([4, 11, -3], [1, 5, 6], hair)
    h.box([-4, 10, 3], [8, 6, 1], hair)
    hl = m.bone("halo", [0, 19, 0], "head")
    for o, sz in (([-3, 18, -3], [6, 1, 1]), ([-3, 18, 2], [6, 1, 1]), ([-3, 18, -2], [1, 1, 4]), ([2, 18, -2], [1, 1, 4])):
        hl.box(o, sz, halo)
    for s, suffix in ((1, "left"), (-1, "right")):
        w = m.bone("wing_" + suffix, [2 * s, 8, 2], "body", rotation=[0, 0, -15 * s])
        o, sz = [2, 5, 2], [7, 6, 1]
        w.box(o if s > 0 else mirror(o, sz), sz, feather)
        ar = m.bone("arm_" + suffix, [3 * s, 8, 0], "body")
        o, sz = [3, 4, -1], [2, 4, 2]
        ar.box(o if s > 0 else mirror(o, sz), sz, skin)
        lg = m.bone("leg_" + suffix, [1.5 * s, 3, 0], "body")
        o, sz = [0, 0, -1], [2, 3, 2]
        lg.box(o if s > 0 else mirror(o, sz), sz, skin)

    def base(length, extra_x=0):
        return {
            "wing_left": {"rotation": wave(length, lambda t: [0, 0, -25 * sn(t, 0.25)], n=32)},
            "wing_right": {"rotation": wave(length, lambda t: [0, 0, 25 * sn(t, 0.25)], n=32)},
            "body": {"position": wave(length, lambda t: [0, 0.6 * sn(t, 1.0), 0]), "rotation": wave(length, lambda t: [extra_x, 0, 0])},
            "leg_left": {"rotation": wave(length, lambda t: [10 * sn(t, 1.0), 0, 0])},
            "leg_right": {"rotation": wave(length, lambda t: [-10 * sn(t, 1.0), 0, 0])},
            "arm_left": {"rotation": wave(length, lambda t: [-10 + 8 * sn(t, 1.0), 0, -10])},
            "arm_right": {"rotation": wave(length, lambda t: [-10 - 8 * sn(t, 1.0), 0, 10])},
            "halo": {"rotation": wave(length, lambda t: [0, 360 * t / length, 0])},
        }

    anims = {
        "idle": loop(1.0, base(1.0)),
        "fly": loop(1.0, base(1.0, 12)),
        "heal": once(0.8, {
            "arm_left": rot([(0, Z3), (0.3, [-150, 0, -20]), (0.6, [-150, 0, -20]), (0.8, Z3)]),
            "arm_right": rot([(0, Z3), (0.3, [-150, 0, 20]), (0.6, [-150, 0, 20]), (0.8, Z3)]),
            "halo": chan(scale=[(0, [1, 1, 1]), (0.4, [1.3, 1, 1.3]), (0.8, [1, 1, 1])]),
        }),
        "bless": once(1.0, {
            "body": rot([(0, Z3), (1.0, [0, 360, 0])]),
            "arm_left": rot([(0, Z3), (0.2, [0, 0, -80]), (0.8, [0, 0, -80]), (1.0, Z3)]),
            "arm_right": rot([(0, Z3), (0.2, [0, 0, 80]), (0.8, [0, 0, 80]), (1.0, Z3)]),
        }),
        "flash": once(0.6, {
            "body": chan(scale=[(0, [1, 1, 1]), (0.2, [1.3, 1.3, 1.3]), (0.6, [1, 1, 1])]),
            "arm_left": rot([(0, Z3), (0.2, [0, 0, -80]), (0.6, Z3)]),
            "arm_right": rot([(0, Z3), (0.2, [0, 0, 80]), (0.6, Z3)]),
        }),
    }
    m.write(anims, seed=32, glow=False)


def crystal_golem():
    m = Model("crystal_golem", 128, 128)
    marble = Mat("#ece8f4", "#dcd6ea", "#f6f4fa", noise=0.04, edge=0.12)
    cmarble = Crystal("#e6dcf6", alpha=215, facet=0.2)
    crystal = Crystal("#d07af0", alpha=230)
    core = Mat("#ff7af0", "#ffb0f8", glow=True, edge=0.0, shading=0.0)
    m.bone("root", [0, 0, 0])
    for s, suffix in ((1, "left"), (-1, "right")):
        lg = m.bone("leg_" + suffix, [4 * s, 16, 0], "root")
        o, sz = [1, 0, -3], [6, 16, 6]
        lg.box(o if s > 0 else mirror(o, sz), sz, marble)
        o, sz = [3, 9, -4], [2, 3, 1]
        lg.box(o if s > 0 else mirror(o, sz), sz, crystal)
    b = m.bone("body", [0, 20, 0], "root")
    b.box([-6, 16, -4], [12, 6, 8], marble)
    b.box([-9, 22, -6], [18, 14, 12], cmarble)
    b.box([-3, 26, -7], [6, 6, 1], core)
    for o, sz, r in (([-10, 35, -2], [4, 8, 4], [0, 0, 20]), ([-6, 36, 1], [3, 6, 3], [10, 0, -10]),
                     ([6, 35, -2], [4, 9, 4], [0, 0, -20]), ([3, 36, 2], [3, 7, 3], [-10, 0, 10]),
                     ([-2, 30, 6], [4, 6, 3], [-30, 0, 0])):
        b.box(o, sz, crystal, rotation=r)
    h = m.bone("head", [0, 36, -2], "body")
    h.box([-4, 36, -6], [8, 7, 8], marble,
          deco=[("north", dots([(3, 3, 2, 2, "#ff7af0"), (2, 3, 1, 2, "#c050c0"), (5, 3, 1, 2, "#c050c0")]))])
    h.box([-1, 43, -4], [2, 5, 2], crystal, rotation=[0, 0, 10])
    h.box([1, 43, -2], [2, 4, 2], crystal, rotation=[0, 0, -15])
    arms_pair(m, "body", lambda: {
        "pivot": [11, 33, 0],
        "upper": [C([9, 30, -4], [7, 7, 8], marble), C([10, 20, -3], [5, 10, 6], cmarble)],
        "fpivot": [12.5, 20, 0],
        "lower": [C([9, 8, -4], [7, 12, 8], marble), C([10, 5, -3], [5, 3, 6], crystal),
                  C([15, 12, -1], [3, 6, 3], crystal, rotation=[0, 0, -25])],
    })
    anims = {
        "idle": loop(3.0, {
            "body": {"rotation": wave(3.0, lambda t: [1.5 * sn(t, 3), 0, 0]), "position": wave(3.0, lambda t: [0, 0.3 * sn(t, 3), 0])},
            "arm_left": {"rotation": wave(3.0, lambda t: [2 * sn(t, 3), 0, -2])},
            "arm_right": {"rotation": wave(3.0, lambda t: [2 * sn(t, 3), 0, 2])},
        }),
        "walk": loop(1.8, {
            "leg_left": {"rotation": wave(1.8, lambda t: [25 * sn(t, 1.8), 0, 0])},
            "leg_right": {"rotation": wave(1.8, lambda t: [-25 * sn(t, 1.8), 0, 0])},
            "arm_left": {"rotation": wave(1.8, lambda t: [-20 * sn(t, 1.8), 0, -2])},
            "arm_right": {"rotation": wave(1.8, lambda t: [20 * sn(t, 1.8), 0, 2])},
            "body": {"rotation": wave(1.8, lambda t: [4, 0, 2 * sn(t, 1.8)]), "position": wave(1.8, lambda t: [0, -0.6 * abs(sn(t, 1.8)), 0])},
        }),
        "attack": once(1.1, {
            "arm_left": rot([(0, Z3), (0.45, [-150, 0, 15]), (0.6, [-35, 0, 5]), (0.8, [-30, 0, 5]), (1.1, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.45, [-150, 0, 15]), (0.6, [-35, 0, 5]), (0.8, [-30, 0, 5]), (1.1, Z3)])),
            "body": rot([(0, Z3), (0.45, [-10, 0, 0]), (0.6, [18, 0, 0]), (1.1, Z3)]),
        }),
        "cast": once(1.2, {
            "arm_left": rot([(0, Z3), (0.6, [-20, 0, -60]), (0.7, [-10, 0, -90]), (1.0, [-10, 0, -60]), (1.2, Z3)]),
            "arm_right": rot(mpts([(0, Z3), (0.6, [-20, 0, -60]), (0.7, [-10, 0, -90]), (1.0, [-10, 0, -60]), (1.2, Z3)])),
            "body": chan(rotation=[(0, Z3), (0.6, [-12, 0, 0]), (0.7, [6, 0, 0]), (1.2, Z3)],
                         scale=[(0, [1, 1, 1]), (0.6, [1.05, 1.05, 1.05]), (0.7, [0.97, 0.97, 0.97]), (1.2, [1, 1, 1])]),
        }),
        "reflect": once(0.4, {
            "body": rot([(0, Z3), (0.1, [-8, 0, 0]), (0.4, Z3)]),
            "head": chan(scale=[(0, [1, 1, 1]), (0.1, [1.1, 1.1, 1.1]), (0.4, [1, 1, 1])]),
        }),
    }
    m.write(anims, seed=33)


# =====================================================================================
#  Texturas dos projeteis (dimensional_bolt)
# =====================================================================================
def orb(path, inner, outer, edge, rays=None, seed=1):
    rnd = random.Random(seed)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d > 7.5:
                continue
            t = d / 7.5
            col = mix(H(inner), H(outer), min(1.0, t * 1.3)) if t < 0.75 else mix(H(outer), H(edge), (t - 0.75) * 4)
            a = 255 if t < 0.6 else int(255 * max(0.0, 1 - (t - 0.6) / 0.4))
            col = tone(col, (rnd.random() - 0.5) * 0.12)
            c.set(x, y, with_alpha(col, a))
    if rays:
        for i in range(8):
            ang = i * math.pi / 4 + 0.3
            for r in range(5, 8):
                c.set(int(8 + math.cos(ang) * r), int(8 + math.sin(ang) * r), with_alpha(H(rays), 200 - (r - 5) * 50))
    c.save(path)


def bolt_textures():
    base = os.path.join(A, "textures/entity/creatures_e")
    orb(os.path.join(base, "bolt_magma.png"), "#fff1a8", "#ff7a1a", "#5a1a08", rays="#ffb347", seed=1)
    orb(os.path.join(base, "bolt_phoenix.png"), "#ffffff", "#ffd23f", "#ff5a00", rays="#ff9a1a", seed=2)
    orb(os.path.join(base, "bolt_water.png"), "#e0f8ff", "#3fa9ff", "#123f8f", seed=3)
    orb(os.path.join(base, "bolt_light_orb.png"), "#ffffff", "#fff4c0", "#ffd86b", rays="#fffbe0", seed=5)
    # rajada: orbe de ar com espiral de vento por cima
    g = Canvas(16, 16)
    for i in range(80):
        tt = i / 80
        r = 1.5 + tt * 6.0
        ang = tt * math.pi * 3.5
        g.set(int(8 + math.cos(ang) * r), int(8 + math.sin(ang) * r), with_alpha(mix(H("#ffffff"), H("#9ff0d0"), tt), 255))
    base_c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.5:
                t = d / 7.5
                base_c.set(x, y, with_alpha(mix(H("#ffffff"), H("#9ff0d0"), t), int(200 * (1 - t) + 30)))
            if g.get(x, y)[3]:
                base_c.set(x, y, g.get(x, y))
    base_c.save(os.path.join(base, "bolt_gust.png"))
    # lanca de luz (ponta a direita = frente do voo)
    s = Canvas(32, 8)
    for x in range(32):
        t = x / 31
        a = int(60 + 195 * t) if x < 24 else 255
        s.set(x, 3, with_alpha(mix(H("#ffd86b"), H("#ffffff"), t), a))
        s.set(x, 4, with_alpha(mix(H("#e8b83a"), H("#fff4c0"), t), a))
        if x % 3 == 0 and x < 22:
            s.set(x, 2, with_alpha(H("#fff4c0"), int(a * 0.4)))
            s.set(x, 5, with_alpha(H("#fff4c0"), int(a * 0.4)))
    for i, x in enumerate(range(24, 32)):
        hh = max(0, 3 - i // 2)
        for y in range(4 - hh, 4 + hh):
            s.set(x, y, H("#ffffff") if abs(y - 3.5) < 1 else H("#fff4c0"))
    s.save(os.path.join(base, "bolt_light_spear.png"))
    # estilhaco de cristal
    k = Canvas(16, 8)
    for x in range(16):
        hh = int(round(3.4 * math.sin(math.pi * (x + 0.5) / 16)))
        for y in range(4 - hh, 4 + hh):
            col = H("#f0b0ff") if y < 4 else H("#c060e0")
            if (x + y) % 4 == 0:
                col = lighten(col, 0.4)
            k.set(x, y, with_alpha(col, 235))
    k.save(os.path.join(base, "bolt_crystal_shard.png"))
    st = Canvas(16, 16)
    st.rect(0, 0, 15, 15, H("#6f7a5a"))
    st.noise(0.2, seed=9)
    st.save(os.path.join(base, "bolt_boulder.png"))


# =====================================================================================
#  Itens (16x16)
# =====================================================================================
def item_tex(name, c):
    c.save(os.path.join(A, "textures/item", name + ".png"))
    save_json(os.path.join(A, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/" + name}})


def feather_icon(main, mid, tip, quill, seed):
    """Pena na diagonal (base embaixo a esquerda): lado de cima claro, lado de baixo escuro, barbas a cada 3 px."""
    c = Canvas(16, 16)
    widths = [1, 1, 2, 2, 3, 3, 3, 3, 3, 2, 2, 1]
    for i in range(12):
        cx, cy = 3 + i, 12 - i
        col = mix(H(main), H(mid), i / 7) if i < 8 else mix(H(mid), H(tip), (i - 7) / 4)
        w = widths[i]
        for a in range(w + 1):
            for b in range(w + 1 - a):
                if a == 0 and b == 0:
                    continue
                k = 0.12 if i % 3 == 0 else 0.0
                c.set(cx - a, cy - b, tone(col, 0.12 - k))
                c.set(cx + a, cy + b, tone(col, -0.12 - k))
        c.set(cx, cy, lighten(H(quill), 0.3))
    c.line(1, 14, 3, 12, H(quill))
    c.outline(darken(H(main), 0.55))
    c.noise(0.03, seed=seed)
    return c


def items():
    # Estilhaco Elemental: cristal com as 4 cores elementais
    c = Canvas(16, 16)
    cols = {"tl": H("#ff7a1a"), "tr": H("#3fa9ff"), "bl": H("#a0743c"), "br": H("#9ff0d0")}
    for y in range(16):
        for x in range(16):
            if abs(x - 7.5) * 0.9 + abs(y - 7.5) * 0.62 <= 5.2:
                key = ("t" if y < 8 else "b") + ("l" if x < 8 else "r")
                col = cols[key]
                if (x + y) % 4 == 0:
                    col = lighten(col, 0.35)
                c.set(x, y, col)
    c.shade_sphere(8, 7, 7, light=0.35, dark=0.3)
    c.outline(H("#2a1f2e"))
    c.set(6, 4, H("#ffffff"))
    c.set(5, 5, H("#ffffff"))
    item_tex("elemental_shard", c)
    item_tex("phoenix_feather", feather_icon("#c0201a", "#ff7a1a", "#ffe58a", "#ffd23f", 2))
    item_tex("angel_feather", feather_icon("#e8e4dc", "#ffffff", "#fff8e0", "#e8b83a", 3))
    # Escama de Tempestade: escama azul-escura com raio
    c = Canvas(16, 16)
    for y in range(2, 15):
        half = int(6 * math.sin(math.pi * (y - 1) / 15) + 0.5)
        for x in range(8 - half, 8 + half):
            c.set(x, y, mix(H("#5a6f9c"), H("#262f47"), (y - 2) / 12))
    c.shade_sphere(8, 7, 7, light=0.3, dark=0.35)
    for (x, y) in ((9, 3), (8, 4), (8, 5), (7, 6), (6, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10), (6, 11)):
        c.set(x, y, H("#7fdbff"))
    c.outline(H("#141a2a"))
    item_tex("storm_scale", c)
    # Quitina do Vazio: placa curva roxa com pintas brilhantes
    c = Canvas(16, 16)
    for y in range(3, 14):
        for x in range(2, 14):
            if (x - 8) ** 2 / 36 + (y - 9) ** 2 / 25 <= 1 and (x - 8) ** 2 / 20 + (y - 12) ** 2 / 9 > 1:
                c.set(x, y, H("#2a1d3a"))
    c.shade_sphere(7, 6, 7, light=0.4, dark=0.3)
    for (x, y) in ((5, 6), (9, 5), (11, 8), (7, 8), (4, 9)):
        c.set(x, y, H("#b066ff"))
    c.outline(H("#0e0a14"))
    item_tex("void_chitin", c)
    # Lente do Vigia
    c = Canvas(16, 16)
    c.circle(8, 8, 7, H("#2a1838"))
    c.circle(8, 8, 5.6, H("#e8e0f0"))
    c.circle(8, 8, 3.6, H("#a04bff"))
    c.circle(8, 8, 1.6, H("#120818"))
    c.shade_sphere(8, 8, 6, light=0.25, dark=0.25)
    c.set(6, 6, H("#ffffff"))
    c.set(5, 7, H("#f0d8ff"))
    c.outline(H("#0e0a14"))
    item_tex("watcher_lens", c)
    # Tinta do Kraken: bolsa de tinta azul-escura com pontos bioluminescentes
    c = Canvas(16, 16)
    c.circle(8, 9, 5.5, H("#1c2a4e"))
    c.circle(8, 4, 2.2, H("#2b3d6e"))
    c.rect(7, 3, 9, 5, H("#2b3d6e"))
    c.shade_sphere(8, 9, 5.5, light=0.35, dark=0.4)
    for (x, y) in ((6, 8), (10, 10), (8, 12), (5, 11)):
        c.set(x, y, H("#6be0d0"))
    c.outline(H("#080c18"))
    item_tex("kraken_ink", c)
    # Cristal Vivo: aglomerado de cristais magenta com nucleo brilhante
    c = Canvas(16, 16)
    for (x0, x1, top, col) in ((3, 6, 5, "#c060e0"), (6, 10, 1, "#e07af0"), (10, 13, 4, "#b050d0")):
        for x in range(x0, x1):
            for y in range(top + abs(x - (x0 + x1 - 1) / 2.0).__int__(), 14):
                c.set(x, y, H(col) if (x + y) % 5 else lighten(H(col), 0.35))
    c.rect(6, 9, 9, 12, H("#ffb0f8"))
    c.set(7, 10, H("#ffffff"))
    c.outline(H("#3a1440"))
    item_tex("living_crystal", c)
    # ovos
    for eid in ("greater_fire_elemental", "greater_water_elemental", "greater_earth_elemental", "greater_air_elemental",
                "lesser_phoenix", "storm_wyvern", "void_crawler", "watcher_eye", "young_kraken",
                "angel_sentinel", "cherub", "crystal_golem"):
        save_json(os.path.join(A, "models/item", eid + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  Dados: loot tables, biome modifiers, tags
# =====================================================================================
def entry(item, lo, hi, looting=1.0, chance=None, player=False):
    e = {"type": "minecraft:item", "name": item,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}
    if looting:
        e["functions"].append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                               "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [e]}
    conds = []
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                      "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.1, "per_level_above_first": 0.1}})
    if player:
        conds.append({"condition": "minecraft:killed_by_player"})
    if conds:
        pool["conditions"] = conds
    return pool


def loot(eid, pools):
    save_json(os.path.join(D, "loot_table/entities", eid + ".json"),
              {"type": "minecraft:entity", "random_sequence": "isekai_mahou:entities/" + eid, "pools": pools})


def data():
    ess = lambda e: "isekai_mahou:essence_" + e
    shard = "isekai_mahou:elemental_shard"
    loot("greater_fire_elemental", [entry(ess("fire"), 2, 4, 2), entry(shard, 1, 1, 1, chance=0.35, player=True),
                                    entry("minecraft:blaze_powder", 1, 3), entry("minecraft:magma_cream", 0, 2)])
    loot("greater_water_elemental", [entry(ess("water"), 2, 4, 2), entry(shard, 1, 1, 1, chance=0.35, player=True),
                                     entry("minecraft:prismarine_crystals", 1, 3), entry("minecraft:prismarine_shard", 0, 2)])
    loot("greater_earth_elemental", [entry(ess("earth"), 3, 5, 2), entry(shard, 1, 2, 1, chance=0.4, player=True),
                                     entry("minecraft:amethyst_shard", 1, 3), entry("minecraft:raw_iron", 0, 2)])
    loot("greater_air_elemental", [entry(ess("wind"), 2, 4, 2), entry(shard, 1, 1, 1, chance=0.35, player=True),
                                   entry("minecraft:breeze_rod", 1, 2), entry("minecraft:phantom_membrane", 0, 1)])
    loot("lesser_phoenix", [entry(ess("fire"), 2, 3, 2), entry(ess("light"), 1, 2, 1),
                            entry("isekai_mahou:phoenix_feather", 1, 1, 1, player=True), entry("minecraft:blaze_powder", 0, 2)])
    loot("storm_wyvern", [entry(ess("wind"), 2, 4, 2), entry(ess("fire"), 1, 2, 1),
                          entry("isekai_mahou:storm_scale", 1, 3, 1, player=True), entry(shard, 1, 1, 1, chance=0.25, player=True)])
    loot("void_crawler", [entry(ess("shadow"), 2, 3, 1), entry("isekai_mahou:void_chitin", 1, 2, 1, player=True),
                          entry("minecraft:ender_pearl", 0, 1), entry("minecraft:spider_eye", 0, 1)])
    loot("watcher_eye", [entry(ess("shadow"), 2, 4, 2), entry("isekai_mahou:watcher_lens", 1, 1, 0, chance=0.35, player=True),
                         entry("minecraft:ender_eye", 0, 1)])
    loot("young_kraken", [entry(ess("water"), 2, 3, 1), entry(ess("shadow"), 1, 2, 1),
                          entry("isekai_mahou:kraken_ink", 1, 3, 1, player=True), entry("minecraft:ink_sac", 1, 3)])
    loot("angel_sentinel", [entry(ess("light"), 3, 4, 2), entry("isekai_mahou:angel_feather", 1, 2, 1, player=True),
                            entry(shard, 1, 1, 1, chance=0.2, player=True), entry("minecraft:gold_nugget", 2, 5)])
    loot("cherub", [entry(ess("light"), 1, 2, 1), entry("isekai_mahou:angel_feather", 1, 1, 0, chance=0.4, player=True)])
    loot("crystal_golem", [entry(ess("light"), 2, 3, 1), entry(ess("earth"), 2, 3, 1),
                           entry("isekai_mahou:living_crystal", 1, 2, 1, player=True), entry("minecraft:amethyst_shard", 2, 4)])

    # Spawns: so nos biomas das dimensoes. Cada bioma tem uma tag propria com "required": false,
    # entao se o modulo das dimensoes ainda nao tiver o bioma, a tag fica vazia e nada quebra.
    spawns = {
        "ember_isles": [("greater_fire_elemental", 30, 1, 2), ("lesser_phoenix", 10, 1, 1)],
        "suspended_sea": [("greater_water_elemental", 30, 1, 2)],
        "earthen_peaks": [("greater_earth_elemental", 25, 1, 1)],
        "storm_skies": [("greater_air_elemental", 30, 1, 2), ("storm_wyvern", 10, 1, 1)],
        "abyssal_depths": [("void_crawler", 40, 1, 3), ("young_kraken", 15, 1, 1)],
        "void_hollows": [("watcher_eye", 30, 1, 2)],
        "crystal_city": [("angel_sentinel", 30, 1, 2), ("crystal_golem", 15, 1, 1)],
        "cloud_gardens": [("cherub", 20, 2, 3)],
    }
    for biome, lst in spawns.items():
        save_json(os.path.join(D, "tags/worldgen/biome/creatures_e", biome + ".json"),
                  {"replace": False, "values": [{"id": "isekai_mahou:" + biome, "required": False}]})
        save_json(os.path.join(D, "neoforge/biome_modifier", "creatures_e_" + biome + ".json"), {
            "type": "neoforge:add_spawns",
            "biomes": "#isekai_mahou:creatures_e/" + biome,
            "spawners": [{"type": "isekai_mahou:" + e, "weight": w, "minCount": lo, "maxCount": hi} for e, w, lo, hi in lst],
        })

    def tag(path, values):
        save_json(os.path.join(MERGE, path), {"values": ["isekai_mahou:" + v if isinstance(v, str) else v for v in values]})

    tag("data/isekai_mahou/tags/entity_type/celestial_beings.json",
        ["angel_sentinel", "cherub", "crystal_golem", {"id": "isekai_mahou:seraph", "required": False}])
    tag("data/isekai_mahou/tags/entity_type/unholy.json", ["void_crawler", "watcher_eye", "young_kraken"])
    tag("data/minecraft/tags/entity_type/aquatic.json", ["young_kraken", "greater_water_elemental"])
    tag("data/minecraft/tags/entity_type/arthropod.json", ["void_crawler"])
    tag("data/minecraft/tags/entity_type/freeze_hurts_extra_types.json", ["greater_fire_elemental", "lesser_phoenix"])


def main():
    for fn in (greater_fire_elemental, greater_water_elemental, greater_earth_elemental, greater_air_elemental,
               lesser_phoenix, storm_wyvern, void_crawler, watcher_eye, young_kraken,
               angel_sentinel, cherub, crystal_golem):
        fn()
        print("modelo ok:", fn.__name__)
    bolt_textures()
    items()
    data()
    print("assets do creatures_e gerados")


if __name__ == "__main__":
    main()
