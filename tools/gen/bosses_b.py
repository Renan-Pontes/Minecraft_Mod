"""
Gerador de assets do modulo bosses_b (Soberano do Abismo, Serafim, Arauto do Crepusculo):
modelos GeckoLib (.geo.json, formato Bedrock 1.12.0), animacoes (1.8.0), texturas box-UV pintadas por face
(+ _glowmask), projeteis, Coracao do Crepusculo, trofeu (bloco), ovos, loot tables e tags (merge).

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_b.py
So as animacoes (sem reescrever modelos/texturas/dados):  py -I tools/gen/bosses_b.py anims

Convencoes (Bedrock/GeckoLib): frente do modelo = -Z; -X = lado DIREITO da criatura; y para cima a partir dos pes.
Rotacoes (graus): X negativo levanta um braco para a frente; Z positivo levanta para fora o lado direito (-X),
Z negativo levanta para fora o lado esquerdo (+X); X positivo inclina o tronco/cabeca para a frente.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken, item_model  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/bosses_b")
NS = "isekai_mahou"
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Faces de um cubo (box UV) e pintura
# =====================================================================================
class Face:
    """Retangulo de uma face na textura. fx/fy relativos ao canto superior esquerdo da face."""

    def __init__(self, model, name, x, y, w, h, cube):
        self.m, self.name, self.x, self.y, self.w, self.h, self.cube = model, name, x, y, w, h, cube

    def set(self, fx, fy, c, glow=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, C(c))
            # Pintar sem brilho apaga o brilho antigo do pixel (a mascara acompanha a textura).
            self.m.glow.set(self.x + fx, self.y + fy, C(c) if glow else CLEAR)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def rect(self, x0, y0, x1, y1, c, glow=False):
        for yy in range(max(0, y0), min(self.h - 1, y1) + 1):
            for xx in range(max(0, x0), min(self.w - 1, x1) + 1):
                self.set(xx, yy, c, glow)

    def fill(self, c, glow=False):
        self.rect(0, 0, self.w - 1, self.h - 1, c, glow)

    def tint(self, fn):
        """Recolore os pixels pintados (o brilho continua onde ja estava)."""
        for yy in range(self.h):
            for xx in range(self.w):
                p = self.get(xx, yy)
                if p[3] > 0:
                    q = fn(p, xx, yy)
                    self.m.tex.set(self.x + xx, self.y + yy, q)
                    if self.m.glow.get(self.x + xx, self.y + yy)[3] > 0:
                        self.m.glow.set(self.x + xx, self.y + yy, q)

    def noise(self, amount, seed):
        rnd = random.Random(seed)

        def f(p, xx, yy):
            r = (rnd.random() - 0.5) * 2 * amount
            return lighten(p, r) if r > 0 else darken(p, -r)
        self.tint(f)

    def border(self, c, sides="tblr"):
        for xx in range(self.w):
            if "t" in sides:
                self.set(xx, 0, c)
            if "b" in sides:
                self.set(xx, self.h - 1, c)
        for yy in range(self.h):
            if "l" in sides:
                self.set(0, yy, c)
            if "r" in sides:
                self.set(self.w - 1, yy, c)

    def side_of(self, fx):
        """Para faces que atravessam o meio do corpo: -1 = lado DIREITO (-X), +1 = lado ESQUERDO (+X)."""
        x0 = self.cube["origin"][0]
        sx = self.cube["size"][0]
        if self.name in ("front", "top", "bottom"):
            x = x0 + fx + 0.5
        elif self.name == "back":
            x = x0 + sx - fx - 0.5
        elif self.name == "right":
            x = x0
        else:
            x = x0 + sx
        return -1 if x < 0 else 1


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0, tag=None):
        c = {"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size], "paint": paint,
             "inflate": inflate, "tag": tag}
        self.cubes.append(c)
        return c

    # ---------------- empacotamento ----------------
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
            "top": Face(self, "top", u + d, v, w, d, c),
            "bottom": Face(self, "bottom", u + d + w, v, w, d, c),
            "right": Face(self, "right", u, v + d, d, h, c),
            "front": Face(self, "front", u + d, v + d, w, h, c),
            "left": Face(self, "left", u + d + w, v + d, d, h, c),
            "back": Face(self, "back", u + 2 * d + w, v + d, w, h, c),
        }

    def paint(self, ctx=None):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            fs = self.faces_of(c)
            c["paint"](fs, c, ctx)

    # ---------------- saida ----------------
    def geo_json(self, bounds=(6, 6, 2.5)):
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
                    "visible_bounds_offset": [0, bounds[2], 0],
                },
                "bones": bones,
            }],
        }

    def save_geo(self, bounds=(6, 6, 2.5)):
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json(bounds))

    def save_texture(self, name=None):
        name = name or self.ident
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# =====================================================================================
#  Pinceis prontos (paint(fs, cube, ctx))
# =====================================================================================
def shade_faces(fs, base, seed, noise=0.05, grad=0.16):
    """Preenche as 6 faces com a cor base, iluminacao por face, gradiente vertical e ruido."""
    for name, f in fs.items():
        c = C(base)
        if name == "top":
            c = lighten(c, 0.12)
        elif name == "bottom":
            c = darken(c, 0.30)
        elif name == "back":
            c = darken(c, 0.08)
        elif name in ("left", "right"):
            c = darken(c, 0.05)
        f.fill(c)
        if name in ("front", "back", "left", "right") and f.h > 2 and grad:
            hh = f.h
            f.tint(lambda p, xx, yy: lighten(p, grad * 0.4 * (1 - yy / (hh * 0.35))) if yy < hh * 0.35
                   else darken(p, grad * (yy - hh * 0.35) / (hh * 0.65)))
        if noise:
            f.noise(noise, seed + len(name) * 13)


def solid(base, seed=1, noise=0.05, grad=0.16):
    def p(fs, c, ctx):
        shade_faces(fs, base, seed, noise, grad)
    return p


def chain(*painters):
    def p(fs, c, ctx):
        for q in painters:
            if q:
                q(fs, c, ctx)
    return p


def glow_all(color):
    def p(fs, c, ctx):
        for f in fs.values():
            f.fill(C(color), True)
    return p


# =====================================================================================
#  Animacoes
# =====================================================================================
def num(v):
    return round(v, 3)


def keys(points):
    """{tempo: [x,y,z]} com tempos formatados."""
    return {f"{num(t)}": [num(a) for a in v] for t, v in sorted(points.items())}


def wave(length, fn, steps=12):
    """Amostra fn(fase 0..1) -> [x,y,z] em 'steps' pontos (loop suave: o ultimo == o primeiro)."""
    pts = {}
    for i in range(steps + 1):
        ph = i / steps
        pts[length * ph] = fn(ph)
    return keys(pts)


def sinv(ph, amp, off=0.0, mult=1):
    return off + amp * math.sin(2 * math.pi * ph * mult)


def cosv(ph, amp, off=0.0, mult=1):
    return off + amp * math.cos(2 * math.pi * ph * mult)


class Anims:
    def __init__(self, ident):
        self.ident = ident
        self.anims = {}

    def add(self, name, length, loop, bones):
        a = {"animation_length": num(length), "bones": {}}
        if loop is True:
            a["loop"] = True
        elif loop == "hold":
            a["loop"] = "hold_on_last_frame"
        for bone, chans in bones.items():
            bj = {}
            for ch, val in chans.items():
                bj[ch] = keys(val) if isinstance(val, dict) and val and not isinstance(next(iter(val)), str) else val
            a["bones"][bone] = bj
        self.anims[f"animation.{self.ident}.{name}"] = a

    def save(self, file_ident=None):
        write_json(os.path.join(ASSETS, "animations/entity", (file_ident or self.ident) + ".animation.json"),
                   {"format_version": "1.8.0", "animations": self.anims})


def K(*pairs):
    """K(t0, [x,y,z], t1, [x,y,z], ...) -> dict de keyframes."""
    d = {}
    for i in range(0, len(pairs), 2):
        d[pairs[i]] = pairs[i + 1]
    return d


# =====================================================================================
#  Decoracoes comuns
# =====================================================================================
def plate(base, seed, edge_dark=0.45, trim=None, noise=0.06):
    """Placa de armadura: sombreada, contorno escuro e filete claro no topo."""
    def p(fs, c, ctx):
        shade_faces(fs, base, seed, noise)
        for n, f in fs.items():
            f.border(darken(C(base), edge_dark))
            if trim and n in ("front", "back", "left", "right") and f.h >= 4:
                for xx in range(1, f.w - 1):
                    f.set(xx, 1, C(trim))
    return p


def cloth(base, seed, folds=True, tatter=0, noise=0.05):
    """Tecido: dobras verticais e (opcional) barra rasgada embaixo."""
    def p(fs, c, ctx):
        shade_faces(fs, base, seed, noise, grad=0.22)
        rnd = random.Random(seed)
        for n in ("front", "back", "left", "right"):
            f = fs[n]
            if folds:
                for xx in range(1, f.w, 3):
                    for yy in range(f.h):
                        f.set(xx, yy, darken(f.get(xx, yy), 0.18))
            if tatter:
                for xx in range(f.w):
                    cut = rnd.randint(0, tatter)
                    for k in range(cut):
                        f.set(xx, f.h - 1 - k, CLEAR)
        if tatter:
            fs["bottom"].fill(CLEAR)
    return p


def vgrad_glow(top, bottom, glow_from=None):
    """Gradiente vertical (base escura -> ponta clara); pixels acima de glow_from (fracao da altura) brilham."""
    def p(fs, c, ctx):
        for n, f in fs.items():
            for yy in range(f.h):
                if n in ("top", "bottom"):
                    t = 1.0 if n == "top" else 0.0
                else:
                    t = 1 - yy / max(1, f.h - 1)
                col = mix(C(bottom), C(top), t)
                if n in ("left", "right", "back"):
                    col = darken(col, 0.1)
                g = glow_from is not None and t >= glow_from
                for xx in range(f.w):
                    f.set(xx, yy, col, g)
    return p


# =====================================================================================
#  SOBERANO DO ABISMO
# =====================================================================================
AB_ARM = "#261c35"
AB_ARM_HI = "#43345c"
AB_TRIM = "#7d5bbd"
AB_RUNE = "#c47dff"
AB_EYE = "#f3c4ff"
AB_CLOTH = "#2d1342"
AB_LINING = "#4b1030"
AB_HORN = "#1c1724"
AB_HORN_TIP = "#c3aee6"
AB_GOLD = "#8e6fd0"


def ab_rune_line(face_names, y, color=AB_RUNE):
    def p(fs, c, ctx):
        for n in face_names:
            f = fs[n]
            yy = y if y >= 0 else f.h + y
            for xx in range(f.w):
                if (xx + yy) % 3 != 0:
                    f.set(xx, yy, C(color), True)
    return p


def ab_torso_front(fs, c, ctx):
    f = fs["front"]
    w, h = f.w, f.h
    # Placas do peitoral, filete violeta e o olho abissal brilhando no centro
    for xx in range(w):
        f.set(xx, 5, darken(f.get(xx, 5), 0.35))
        f.set(xx, 10, darken(f.get(xx, 10), 0.35))
    f.rect(w // 2, 1, w // 2, h - 2, darken(C(AB_ARM), 0.5))
    cx, cy = w // 2, 7
    for dx in range(-3, 3):
        edge = abs(dx + 0.5) >= 2
        f.set(cx + dx, cy if edge else cy - 1, C(AB_RUNE), True)
        f.set(cx + dx, cy if edge else cy + 1, C(AB_RUNE), True)
    f.rect(cx - 1, cy, cx, cy, C(AB_EYE), True)
    for xx in (2, w - 3):
        f.rect(xx, 2, xx, 3, C(AB_TRIM))
    for n in ("left", "right"):
        s = fs[n]
        for yy in range(3, s.h - 2, 3):
            s.rect(1, yy, s.w - 2, yy, darken(C(AB_ARM), 0.35))


def ab_belt(fs, c, ctx):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        f.rect(0, 1, f.w - 1, 1, C(AB_TRIM))
        f.rect(0, f.h - 2, f.w - 1, f.h - 2, darken(C(AB_TRIM), 0.3))
    f = fs["front"]
    cx = f.w // 2
    f.rect(cx - 2, 2, cx + 1, 3, C(AB_GOLD))
    f.rect(cx - 1, 2, cx, 3, C(AB_RUNE), True)


def ab_head(fs, c, ctx):
    f = fs["front"]
    f.rect(1, 3, f.w - 2, 4, C("#07040b"))
    f.rect(f.w // 2 - 1, 4, f.w // 2, f.h - 2, C("#07040b"))
    for n in ("left", "right", "back"):
        s = fs[n]
        s.rect(0, s.h - 3, s.w - 1, s.h - 3, C(AB_TRIM))


def ab_mask(fs, c, ctx):
    for f in fs.values():
        f.fill(C("#3b3248"))
    f = fs["front"]
    f.noise(0.06, 77)
    f.border(C("#1a1424"))
    f.rect(1, 2, 2, 2, C(AB_EYE), True)
    f.rect(f.w - 3, 2, f.w - 2, 2, C(AB_EYE), True)
    f.set(1, 3, C(AB_RUNE), True)
    f.set(f.w - 2, 3, C(AB_RUNE), True)
    f.rect(f.w // 2, 0, f.w // 2, 1, C(AB_RUNE), True)
    f.rect(2, f.h - 2, f.w - 3, f.h - 2, C("#120c18"))


def ab_crown(fs, c, ctx):
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        f.rect(0, 0, f.w - 1, 0, C(AB_TRIM))
        for xx in range(1, f.w - 1, 3):
            f.set(xx, 1, C(AB_RUNE), True)
    fs["front"].rect(fs["front"].w // 2 - 1, 0, fs["front"].w // 2, 1, C(AB_EYE), True)


def ab_tabard(seed):
    def p(fs, c, ctx):
        cloth(AB_CLOTH, seed, folds=False, tatter=2)(fs, c, ctx)
        for n in ("front", "back"):
            f = fs[n]
            f.rect(0, 0, 0, f.h - 3, C(AB_TRIM))
            f.rect(f.w - 1, 0, f.w - 1, f.h - 3, C(AB_TRIM))
            cx = f.w // 2
            g = n == "front"
            for yy in range(4, 11):
                f.set(cx - 1, yy, C(AB_RUNE), g)
                f.set(cx, yy, C(AB_RUNE), g)
            f.rect(cx - 2, 6, cx + 1, 8, C(AB_RUNE), g)
            f.rect(cx - 1, 7, cx, 7, C(AB_EYE), g)
    return p


def ab_cape(fs, c, ctx):
    cloth("#1a0b28", 31, folds=True, tatter=4)(fs, c, ctx)
    inner = fs["front"]
    for yy in range(inner.h):
        for xx in range(inner.w):
            p = inner.get(xx, yy)
            if p[3] > 0:
                inner.set(xx, yy, mix(p, C(AB_LINING), 0.6))
    outer = fs["back"]
    for xx in range(outer.w):
        outer.set(xx, 1, C(AB_TRIM))
    for yy in range(6, outer.h - 6, 7):
        for xx in range(2, outer.w - 2):
            if (xx + yy) % 4 == 0 and outer.get(xx, yy)[3] > 0:
                outer.set(xx, yy, C(AB_RUNE), True)
    cx = outer.w // 2
    for yy in range(10, 22):
        outer.set(cx, yy, C(AB_TRIM))
        outer.set(cx - 1, yy, C(AB_TRIM))
    outer.rect(cx - 3, 14, cx + 2, 15, C(AB_TRIM))
    outer.rect(cx - 1, 14, cx, 15, C(AB_EYE), True)


def ab_pauldron(fs, c, ctx):
    plate(AB_ARM_HI, 41, trim=AB_TRIM)(fs, c, ctx)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for xx in range(1, f.w - 1, 2):
            f.set(xx, f.h - 2, C(AB_RUNE), True)
    t = fs["top"]
    t.rect(1, t.h // 2, t.w - 2, t.h // 2, C(AB_TRIM))


def ab_sleeve(fs, c, ctx):
    cloth(AB_CLOTH, 51)(fs, c, ctx)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        f.rect(0, f.h - 5, f.w - 1, f.h - 1, darken(C(AB_ARM_HI), 0.1))
        f.rect(0, f.h - 5, f.w - 1, f.h - 5, C(AB_TRIM))
        f.set(f.w // 2, f.h - 3, C(AB_RUNE), True)


def ab_claw(fs, c, ctx):
    vgrad_glow("#2a2235", "#d8c8f0")(fs, c, ctx)


def ab_horn(fs, c, ctx):
    vgrad_glow("#4a3d5e", AB_HORN)(fs, c, ctx)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for yy in range(1, f.h, 2):
            f.set(0, yy, darken(f.get(0, yy), 0.3))


def ab_horn_tip(fs, c, ctx):
    vgrad_glow(AB_HORN_TIP, "#4a3d5e", glow_from=0.55)(fs, c, ctx)


def ab_leg(fs, c, ctx):
    plate(AB_ARM, 61)(fs, c, ctx)
    for n in ("front", "left", "right"):
        f = fs[n]
        for yy in (5, 11):
            f.rect(0, yy, f.w - 1, yy, darken(C(AB_ARM), 0.4))
        f.rect(1, 7, f.w - 2, 8, C(AB_ARM_HI))
    fs["front"].set(fs["front"].w // 2, 8, C(AB_RUNE), True)


def orb_paint(outer, inner, rim):
    def p(fs, c, ctx):
        for f in fs.values():
            f.fill(C(outer), True)
            if f.w > 2 and f.h > 2:
                f.rect(1, 1, f.w - 2, f.h - 2, C(inner), True)
            f.set(0, 0, C(rim), True)
            f.set(f.w - 1, f.h - 1, C(rim), True)
    return p


def build_abyss():
    m = Model("abyss_sovereign")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 22, 0], "root")
    m.cube("body", [-6, 22, -3.5], [12, 6, 7], chain(plate("#2b2036", 11), ab_belt))
    m.cube("body", [-8, 28, -4.5], [16, 14, 9], chain(plate(AB_ARM, 12, trim=AB_TRIM), ab_torso_front))
    m.cube("body", [-5, 42, -3.5], [10, 2, 7], plate(AB_ARM_HI, 13, trim=AB_TRIM))
    m.bone("head", [0, 43, 0], "body")
    m.cube("head", [-4.5, 43, -4.5], [9, 10, 9], chain(plate(AB_ARM, 21), ab_head))
    m.cube("head", [-3.5, 45, -5.5], [7, 6, 1], ab_mask)
    m.bone("crown", [0, 52, 0], "head")
    m.cube("crown", [-5, 52, -5], [10, 2, 10], chain(plate("#3a2c52", 22), ab_crown))
    spike = vgrad_glow(AB_HORN_TIP, AB_HORN, glow_from=0.7)
    for x, h in ((-0.5, 5), (-3, 3), (2, 3)):
        m.cube("crown", [x, 54, -5], [1, h, 1], spike)
    for x in (-5, 4):
        m.cube("crown", [x, 54, -1], [1, 3, 1], spike)
    m.cube("crown", [-2.5, 54, 4], [1, 3, 1], spike)
    m.cube("crown", [1.5, 54, 4], [1, 3, 1], spike)
    # Chifres: saem para fora e para tras, com a ponta curvando de volta
    m.bone("horn_r", [-4.5, 53, 0], "crown", rotation=[-15, 0, -30])
    m.cube("horn_r", [-6.5, 53, -1.5], [3, 5, 3], ab_horn)
    m.cube("horn_r", [-6, 57.5, -1], [2, 4, 2], ab_horn)
    m.bone("horn_r_tip", [-5, 61, 0], "horn_r", rotation=[-25, 0, 35])
    m.cube("horn_r_tip", [-5.5, 61, -0.5], [1, 5, 1], ab_horn_tip)
    m.bone("horn_l", [4.5, 53, 0], "crown", rotation=[-15, 0, 30])
    m.cube("horn_l", [3.5, 53, -1.5], [3, 5, 3], ab_horn)
    m.cube("horn_l", [4, 57.5, -1], [2, 4, 2], ab_horn)
    m.bone("horn_l_tip", [5, 61, 0], "horn_l", rotation=[-25, 0, -35])
    m.cube("horn_l_tip", [4.5, 61, -0.5], [1, 5, 1], ab_horn_tip)
    # Bracos com ombreiras de espinho, mangas e manoplas com garras
    for side, sx in (("r", -1), ("l", 1)):
        arm = m.bone("arm_" + side, [sx * 10, 39, 0], "body")
        x0 = -14 if sx < 0 else 7
        m.cube(arm, [x0, 37, -4.5], [7, 6, 9], ab_pauldron)
        m.cube(arm, [x0 + 2.5, 43, -1], [2, 4, 2], spike)
        ax = -12.5 if sx < 0 else 7.5
        m.cube(arm, [ax, 24, -2.5], [5, 14, 5], ab_sleeve)
        gx = -13 if sx < 0 else 7
        m.cube(arm, [gx, 20, -3], [6, 5, 6], plate(AB_ARM_HI, 70 + sx, trim=AB_TRIM))
        for k in range(3):
            m.cube(arm, [gx + 0.5 + k * 2, 17, -2.5], [1, 3, 1], ab_claw)
    # Cetro na mao direita
    m.bone("scepter", [-10, 22, -4], "arm_r")
    m.cube("scepter", [-11, 4, -5], [2, 36, 2], chain(plate("#1d1626", 81, edge_dark=0.3),
                                                      ab_rune_line(("front", "back", "left", "right"), 6),
                                                      ab_rune_line(("front", "back", "left", "right"), 20)))
    m.cube("scepter", [-12.5, 40, -6.5], [5, 2, 5], plate(AB_GOLD, 82))
    m.cube("scepter", [-12, 42, -6], [4, 4, 4], orb_paint("#b56cff", "#f2d8ff", "#7a2fd0"))
    for px, pz in ((-13, -4.5), (-8, -4.5), (-10.5, -7.5), (-10.5, -2.5)):
        m.cube("scepter", [px, 42, pz], [1, 5, 1], vgrad_glow(AB_HORN_TIP, AB_GOLD, glow_from=0.8))
    m.cube("scepter", [-10.5, 1, -4.5], [1, 3, 1], ab_claw)
    # Tabardo e capa
    m.bone("tabard_f", [0, 23, -4], "body")
    m.cube("tabard_f", [-4, 4, -5], [8, 19, 1], ab_tabard(91))
    m.bone("tabard_b", [0, 23, 4], "body")
    m.cube("tabard_b", [-5, 3, 4], [10, 20, 1], ab_tabard(92))
    m.bone("cape", [0, 42, 5], "body")
    m.cube("cape", [-9, 2, 5], [18, 40, 1], ab_cape)
    # Pernas com grevas e botas pontudas
    for side, sx in (("r", -1), ("l", 1)):
        leg = m.bone("leg_" + side, [sx * 3.5, 22, 0], "root")
        lx = -6 if sx < 0 else 1
        m.cube(leg, [lx, 4, -3], [5, 18, 6], ab_leg)
        m.cube(leg, [lx - 0.5, 0, -4], [6, 4, 7], plate(AB_ARM_HI, 100 + sx, trim=AB_TRIM))
        m.cube(leg, [lx + 0.5, 0, -5], [4, 2, 1], plate(AB_ARM_HI, 102 + sx))
    # Fogos-fatuos de sombra girando em volta
    m.bone("wisps", [0, 30, 0], "root")
    wisp = orb_paint("#9a55f0", "#e0b8ff", "#6a2fc0")
    for pos in ((12, 30, -1), (-9, 35, 9), (-6, 26, -12), (8, 40, 8), (-13, 22, 2)):
        m.cube("wisps", list(pos), [2, 2, 2], wisp)
    # v1.3: núcleo do vazio pulsando no peito e espinhos extras na coroa (a silhueta fica mais ameaçadora)
    m.cube("body", [-1.5, 35, -5.2], [3, 3, 1], orb_paint("#b56cff", "#f2d8ff", "#7a2fd0"))
    for x in (-4.5, 3.5):
        m.cube("crown", [x, 54, -4], [1, 4, 1], spike)
    m.layout()
    m.paint()
    m.save_geo((7, 7, 2.5))
    m.save_texture()
    return m


def anims_abyss():
    a = Anims("abyss_sovereign")
    a.add("idle", 4.0, True, {
        "body": {"rotation": wave(4.0, lambda p: [sinv(p, 1.5), 0, 0])},
        "head": {"rotation": wave(4.0, lambda p: [sinv(p, 2.0), sinv(p, 4), 0])},
        "arm_r": {"rotation": wave(4.0, lambda p: [sinv(p, 2.5), 0, cosv(p, 2.0, 3)])},
        "arm_l": {"rotation": wave(4.0, lambda p: [-sinv(p, 2.5), 0, -cosv(p, 2.0, 3)])},
        "cape": {"rotation": wave(4.0, lambda p: [sinv(p, 3, 6), 0, sinv(p, 1.5, mult=2)])},
        "tabard_f": {"rotation": wave(4.0, lambda p: [sinv(p, -2, -2), 0, 0])},
        "tabard_b": {"rotation": wave(4.0, lambda p: [sinv(p, 2, 3), 0, 0])},
        "wisps": {"rotation": wave(4.0, lambda p: [0, 360 * p, 0], 8),
                  "position": wave(4.0, lambda p: [0, sinv(p, 1.5, mult=2), 0])},
    })
    a.add("move", 1.6, True, {
        "root": {"position": wave(1.6, lambda p: [0, abs(sinv(p, 1.2)), 0])},
        "body": {"rotation": wave(1.6, lambda p: [6, sinv(p, 4), 0])},
        "leg_r": {"rotation": wave(1.6, lambda p: [sinv(p, 26), 0, 0])},
        "leg_l": {"rotation": wave(1.6, lambda p: [-sinv(p, 26), 0, 0])},
        "arm_r": {"rotation": wave(1.6, lambda p: [-sinv(p, 12), 0, 4])},
        "arm_l": {"rotation": wave(1.6, lambda p: [sinv(p, 12), 0, -4])},
        "cape": {"rotation": wave(1.6, lambda p: [cosv(p, 5, 16, 2), 0, 0])},
        "tabard_f": {"rotation": wave(1.6, lambda p: [sinv(p, 8, -6), 0, 0])},
        "tabard_b": {"rotation": wave(1.6, lambda p: [-sinv(p, 8, -8), 0, 0])},
        "wisps": {"rotation": wave(1.6, lambda p: [0, 360 * p, 0], 8)},
    })
    a.add("death", 4.0, "hold", {
        "root": {"position": K(0, [0, 0, 0], 0.8, [0, -4, 0], 2.5, [0, -7, 0], 4.0, [0, -14, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-15, 0, 0], 1.2, [30, 0, 0], 4.0, [45, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [-30, 0, 0], 1.4, [35, 0, 10], 4.0, [40, 0, 15])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-40, 0, 30], 1.4, [10, 0, 10], 4.0, [20, 0, 5])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-40, 0, -30], 1.4, [10, 0, -10], 4.0, [20, 0, -5])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 1.2, [-70, 0, 0], 4.0, [-80, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 1.2, [10, 0, 0], 4.0, [-10, 0, 0])},
        "wisps": {"scale": K(0, [1, 1, 1], 2.0, [0.6, 0.6, 0.6], 3.5, [0, 0, 0])},
    })
    a.add("intro", 3.5, False, {
        "root": {"position": K(0, [0, -8, 0], 0.8, [0, -8, 0], 1.6, [0, 0, 0])},
        "body": {"rotation": K(0, [35, 0, 0], 0.8, [35, 0, 0], 1.6, [-10, 0, 0], 2.2, [-15, 0, 0], 3.5, [0, 0, 0])},
        "head": {"rotation": K(0, [30, 0, 0], 1.0, [30, 0, 0], 1.8, [-25, 0, 0], 2.6, [-20, 0, 0], 3.5, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [-60, 0, 0], 0.8, [-60, 0, 0], 1.6, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [10, 0, 10], 1.6, [10, 0, 10], 2.2, [-170, 0, 15], 2.7, [-170, 0, 15], 3.0, [-40, 0, 5], 3.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [10, 0, -10], 1.6, [-20, 0, -50], 2.7, [-20, 0, -60], 3.5, [0, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 3.5, [0, 720, 0]), "scale": K(0, [0, 0, 0], 1.6, [1, 1, 1])},
    })
    a.add("phase", 2.5, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-18, 0, 0], 2.0, [-18, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-30, 0, 0], 2.0, [-30, 0, 0], 2.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-30, 0, 75], 2.0, [-30, 0, 80], 2.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [-30, 0, -75], 2.0, [-30, 0, -80], 2.5, [0, 0, 0])},
        "cape": {"rotation": K(0, [0, 0, 0], 0.6, [40, 0, 0], 2.0, [35, 0, 0], 2.5, [5, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 2.5, [0, 1080, 0]), "scale": K(0, [1, 1, 1], 0.6, [1.8, 1.8, 1.8], 2.5, [1, 1, 1])},
    })
    a.add("slam", 1.2, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.45, [-170, 0, 10], 0.6, [-35, 0, 0], 0.9, [-35, 0, 0], 1.2, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.45, [-12, 0, 0], 0.6, [20, 0, 0], 0.9, [20, 0, 0], 1.2, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.45, [-10, 0, 0], 0.6, [10, 0, 0], 1.2, [0, 0, 0])},
    })
    a.add("claw", 0.8, False, {
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.25, [-100, -35, -40], 0.4, [-70, 45, 10], 0.8, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.25, [0, -20, 0], 0.4, [8, 25, 0], 0.8, [0, 0, 0])},
    })
    a.add("teleport", 1.5, False, {
        "root": {"position": K(0, [0, 0, 0], 0.3, [0, -40, 0], 0.69, [0, -40, 0], 0.7, [0, -30, 0], 0.95, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.3, [30, 0, 0], 0.7, [30, 0, 0], 0.95, [-10, 0, 0], 1.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.3, [-60, 0, 40], 0.95, [-60, 0, 40], 1.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.3, [-60, 0, -40], 0.95, [-60, 0, -40], 1.5, [0, 0, 0])},
    })
    a.add("cast", 2.0, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-85, 0, 5], 1.6, [-85, 0, 5], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-70, 10, -15], 0.8, [-95, 0, -10], 1.6, [-80, 0, -10], 2.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-6, 0, 0], 1.6, [-6, 0, 0], 2.0, [0, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 2.0, [0, 540, 0])},
    })
    a.add("summon", 2.0, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-165, 0, 20], 1.0, [-30, 0, 15], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-165, 0, -20], 1.0, [-30, 0, -15], 2.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-15, 0, 0], 1.0, [15, 0, 0], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-25, 0, 0], 1.0, [10, 0, 0], 2.0, [0, 0, 0])},
    })
    a.add("eclipse", 3.0, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 2.0, [-170, 0, 35], 2.6, [-170, 0, 40], 3.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 2.0, [-170, 0, -35], 2.6, [-170, 0, -40], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 2.0, [-35, 0, 0], 2.6, [-35, 0, 0], 3.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 2.0, [-12, 0, 0], 3.0, [0, 0, 0])},
        "cape": {"rotation": K(0, [0, 0, 0], 2.0, [45, 0, 0], 3.0, [5, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 3.0, [0, 1440, 0]), "scale": K(0, [1, 1, 1], 2.0, [2, 2, 2], 3.0, [1, 1, 1])},
    })
    a.add("eruption", 2.7, False, {
        "root": {"position": K(0, [0, 0, 0], 0.6, [0, 1, 0], 0.9, [0, -3, 0], 1.6, [0, -3, 0], 2.7, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-175, 0, 5], 0.9, [-25, 0, 0], 1.8, [-25, 0, 0], 2.7, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-175, 0, -5], 0.9, [-25, 0, 0], 1.8, [-25, 0, 0], 2.7, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-15, 0, 0], 0.9, [28, 0, 0], 1.8, [28, 0, 0], 2.7, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 0.9, [-30, 0, 0], 1.8, [-30, 0, 0], 2.7, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 0.9, [15, 0, 0], 1.8, [15, 0, 0], 2.7, [0, 0, 0])},
    })
    # v0.7 - Mare Sombria (60 ticks): ergue o cetro (1,1 s), crava no chao em t24 (1,25 s) e segura a pose enquanto
    # a onda corre ate a borda; levanta em 3,0 s. Na fase 3 a segunda mare (t64) usa a animacao "slam" (impacto em 0,6 s).
    a.add("tide", 3.0, False, {
        "root": {"position": K(0, [0, 0, 0], 1.1, [0, 1, 0], 1.25, [0, -2.5, 0], 2.5, [0, -2.2, 0], 3.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.1, [-14, 0, 0], 1.25, [26, 0, 0], 2.5, [22, 0, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.1, [-28, 0, 0], 1.25, [12, 0, 0], 2.5, [8, 0, 0], 3.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.9, [-165, 0, 12], 1.1, [-170, 0, 10], 1.25, [-38, 0, 4], 2.5, [-36, 0, 4],
                                3.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.9, [-165, 0, -12], 1.1, [-170, 0, -10], 1.25, [-38, 0, -4], 2.5, [-36, 0, -4],
                                3.0, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 1.1, [0, 0, 0], 1.25, [-34, 0, 0], 2.5, [-30, 0, 0], 3.0, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 1.1, [0, 0, 0], 1.25, [16, 0, 0], 2.5, [14, 0, 0], 3.0, [0, 0, 0])},
        "cape": {"rotation": K(0, [0, 0, 0], 1.1, [40, 0, 0], 1.3, [10, 0, 0], 1.8, [30, 0, 6], 2.3, [12, 0, -6],
                               3.0, [0, 0, 0])},
        "tabard_f": {"rotation": K(0, [0, 0, 0], 1.25, [-20, 0, 0], 2.5, [-14, 0, 0], 3.0, [0, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 3.0, [0, 1080, 0]),
                  "scale": K(0, [1, 1, 1], 1.1, [1.8, 1.8, 1.8], 1.3, [0.6, 0.6, 0.6], 2.5, [1.2, 1.2, 1.2], 3.0, [1, 1, 1])},
    })
    # v0.7 - Queda do Soberano (50 ticks): agacha 0,6 s, salta (bracos ao alto), cai com o cetro em ~1,6 s e se ergue.
    a.add("leap", 2.5, False, {
        "root": {"position": K(0, [0, 0, 0], 0.55, [0, -4, 0], 0.65, [0, 1.5, 0], 1.5, [0, 0.5, 0], 1.6, [0, -3.5, 0],
                               2.1, [0, -3, 0], 2.5, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.55, [26, 0, 0], 0.65, [-20, 0, 0], 1.5, [8, 0, 0], 1.6, [32, 0, 0],
                               2.1, [26, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.55, [-18, 0, 0], 0.65, [-30, 0, 0], 1.5, [10, 0, 0], 1.6, [-10, 0, 0], 2.5, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 0.55, [-55, 0, 0], 0.65, [18, 0, 0], 1.4, [-35, 0, 0], 1.6, [-42, 0, 0],
                                2.1, [-38, 0, 0], 2.5, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 0.55, [-55, 0, 0], 0.65, [22, 0, 0], 1.4, [-20, 0, 0], 1.6, [20, 0, 0],
                                2.1, [16, 0, 0], 2.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.55, [35, 0, 25], 0.65, [-170, 0, 20], 1.45, [-175, 0, 10], 1.6, [-30, 0, 6],
                                2.1, [-34, 0, 6], 2.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.55, [35, 0, -25], 0.65, [-170, 0, -20], 1.45, [-175, 0, -10], 1.6, [-30, 0, -6],
                                2.1, [-34, 0, -6], 2.5, [0, 0, 0])},
        "cape": {"rotation": K(0, [0, 0, 0], 0.55, [10, 0, 0], 0.7, [55, 0, 0], 1.5, [40, 0, 0], 1.65, [5, 0, 0], 2.5, [0, 0, 0])},
        "tabard_f": {"rotation": K(0, [0, 0, 0], 0.7, [-35, 0, 0], 1.6, [-10, 0, 0], 2.5, [0, 0, 0])},
        "tabard_b": {"rotation": K(0, [0, 0, 0], 0.7, [35, 0, 0], 1.6, [10, 0, 0], 2.5, [0, 0, 0])},
        "wisps": {"rotation": K(0, [0, 0, 0], 2.5, [0, 900, 0]),
                  "scale": K(0, [1, 1, 1], 0.6, [0.5, 0.5, 0.5], 1.6, [2.0, 2.0, 2.0], 2.5, [1, 1, 1])},
    })
    a.save()


# =====================================================================================
#  Asas (penas / membrana) — pintura por distancia da raiz
# =====================================================================================
def wing_t(f, fx, root_x, width):
    """0 na raiz da asa, 1 na ponta, para a coluna fx da face f (front/back/top/bottom)."""
    x0 = f.cube["origin"][0]
    sx = f.cube["size"][0]
    if f.name in ("front", "top", "bottom"):
        x = x0 + fx + 0.5
    elif f.name == "back":
        x = x0 + sx - fx - 0.5
    else:
        x = x0 if f.name == "right" else x0 + sx
    return min(1.0, abs(x - root_x) / max(1.0, width))


def feather_wing(root_x, base, line, edge, tip_glow, seed, glow=True):
    """Asa de penas: coberteiras perto da raiz, remiges longas, borda de ataque dourada e pontas recortadas."""
    def p(fs, c, ctx):
        w = c["size"][0]
        rnd = random.Random(seed)
        notch = [rnd.randint(0, 2) for _ in range(64)]
        for n, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    if n in ("left", "right"):
                        f.set(xx, yy, darken(C(base), 0.15))
                        continue
                    t = wing_t(f, xx, root_x, w)
                    col = mix(C(base), lighten(C(base), 0.3), 0.3 * (1 - t))
                    if n in ("top", "bottom"):
                        f.set(xx, yy, C(edge) if n == "top" else darken(col, 0.2))
                        continue
                    hh = f.h
                    covert = yy < hh * (0.45 - 0.25 * t)
                    if covert:
                        if (xx + (yy // 2) * 2) % 4 == 0:
                            col = mix(col, C(line), 0.6)
                        if yy % 3 == 2:
                            col = darken(col, 0.06)
                    else:
                        if (xx % 3) == 0:
                            col = mix(col, C(line), 0.75)
                        col = darken(col, 0.10 * (yy / hh))
                    if yy == 0:
                        col = C(edge)
                    g = False
                    if glow and t > 0.82 and not covert:
                        col = mix(col, C(tip_glow), 0.6)
                        g = (xx + yy) % 2 == 0
                    cut = notch[(xx // 2) % 64] + (2 if t > 0.6 else 0)
                    if yy >= hh - cut and (xx % 3) != 0:
                        f.set(xx, yy, CLEAR)
                        continue
                    f.set(xx, yy, col, g)
        if c["size"][1] > 1:
            fs["bottom"].fill(CLEAR)
    return p


def membrane_wing(root_x, membrane, bone_col, rune, seed, glow=True):
    """Asa de membrana rasgada (morcego/demonio): ossos em leque, buracos e barra recortada."""
    def p(fs, c, ctx):
        w = c["size"][0]
        rnd = random.Random(seed)
        holes = set()
        for _ in range(int(w * c["size"][1] / 70)):
            hx, hy = rnd.randint(2, w - 3), rnd.randint(3, c["size"][1] - 3)
            for dx in (0, 1):
                holes.add((hx + dx, hy))
        for n, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    if n in ("left", "right", "top", "bottom"):
                        f.set(xx, yy, C(bone_col))
                        continue
                    t = wing_t(f, xx, root_x, w)
                    col = mix(C(membrane), darken(C(membrane), 0.4), t * 0.6)
                    col = darken(col, 0.15 * (yy / f.h))
                    # Ossos em leque partindo da raiz (canto superior)
                    bone_hit = False
                    for k in range(1, 5):
                        by = int(k * f.h / 4.5 * t) if t > 0 else 0
                        if abs(yy - by) <= 0 and t > 0.05:
                            bone_hit = True
                    if yy == 0 or bone_hit:
                        col = C(bone_col)
                    # Coordenada "de pintura" independente da face (para buracos coerentes)
                    key = (int(t * w), yy)
                    edge_cut = int(2 + 2.5 * math.sin(t * 9.0 + seed)) if t > 0.15 else 0
                    if key in holes or yy >= f.h - max(0, edge_cut):
                        f.set(xx, yy, CLEAR)
                        continue
                    g = False
                    if glow and yy == 1 and int(t * w) % 4 == 0:
                        col = C(rune)
                        g = True
                    f.set(xx, yy, col, g)
    return p


# =====================================================================================
#  SERAFIM
# =====================================================================================
SE_GOLD = "#e6b445"
SE_GOLD_HI = "#ffe08a"
SE_GOLD_LO = "#9c6d1e"
SE_WHITE = "#f6f2e7"
SE_LINE = "#d6cdb8"
SE_GLOW = "#fff1a8"
SE_EYE = "#fffbe0"


def se_breastplate(fs, c, ctx):
    plate(SE_GOLD, 201, edge_dark=0.35, trim=SE_GOLD_HI)(fs, c, ctx)
    f = fs["front"]
    cx, cy = f.w // 2, 5
    # Sol radiante no peito
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if dx * dx + dy * dy <= 4:
                f.set(cx + dx, cy + dy, C(SE_GLOW), True)
    for ang in range(0, 360, 45):
        r = 3.6
        f.set(int(round(cx + math.cos(math.radians(ang)) * r)), int(round(cy + math.sin(math.radians(ang)) * r)), C(SE_GOLD_HI), True)
    for yy in (9, 11):
        f.rect(1, yy, f.w - 2, yy, C(SE_GOLD_LO))
    for n in ("left", "right"):
        s = fs[n]
        s.rect(s.w // 2, 2, s.w // 2, s.h - 2, C(SE_GOLD_LO))


def se_robe(seed, hem_glow=False, belt=False):
    def p(fs, c, ctx):
        cloth(SE_WHITE, seed, folds=True)(fs, c, ctx)
        for n in ("front", "back", "left", "right"):
            f = fs[n]
            if belt:
                f.rect(0, 1, f.w - 1, 2, C(SE_GOLD))
                f.rect(0, 1, f.w - 1, 1, C(SE_GOLD_HI))
            if hem_glow:
                for yy in range(f.h - 3, f.h):
                    for xx in range(f.w):
                        k = (yy - (f.h - 3)) / 3
                        f.set(xx, yy, mix(f.get(xx, yy), C(SE_GLOW), 0.4 + 0.5 * k), (xx + yy) % 2 == 0 or k > 0.5)
                f.rect(0, f.h - 4, f.w - 1, f.h - 4, C(SE_GOLD))
        if belt:
            fr = fs["front"]
            fr.rect(fr.w // 2 - 1, 1, fr.w // 2, 2, C(SE_GLOW), True)
        if hem_glow:
            fs["bottom"].fill(C(SE_GLOW), True)
    return p


def se_helm(fs, c, ctx):
    plate(SE_GOLD, 211, edge_dark=0.3)(fs, c, ctx)
    f = fs["front"]
    # Mascara branca lisa com tres olhos de luz
    f.rect(1, 2, f.w - 2, f.h - 2, C("#fbf7ec"))
    f.rect(1, f.h - 2, f.w - 2, f.h - 2, C("#e6dcc6"))
    f.rect(2, 4, 3, 4, C(SE_EYE), True)
    f.rect(f.w - 4, 4, f.w - 3, 4, C(SE_EYE), True)
    f.rect(f.w // 2 - 1, 2, f.w // 2, 2, C(SE_GLOW), True)
    f.rect(f.w // 2 - 1, 6, f.w // 2, 6, C("#cfc3a8"))
    for n in ("left", "right"):
        s = fs[n]
        s.rect(1, 3, s.w - 2, 3, C(SE_GOLD_HI))
        s.set(s.w // 2, 5, C(SE_GOLD_LO))


def se_sleeve(fs, c, ctx):
    cloth(SE_WHITE, 221)(fs, c, ctx)


def build_seraph():
    m = Model("seraph")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 24, 0], "root")
    m.cube("body", [-6.5, 26, -4], [13, 13, 8], se_breastplate)
    m.cube("body", [-4, 39, -3], [8, 2, 6], plate(SE_GOLD_HI, 202))
    m.bone("robe", [0, 26, 0], "body")
    m.cube("robe", [-5.5, 20, -3.5], [11, 6, 7], se_robe(231, belt=True))
    m.cube("robe", [-5, 10, -3.5], [10, 10, 7], se_robe(232))
    m.cube("robe", [-4, 2, -3], [8, 8, 6], se_robe(233, hem_glow=True))
    m.bone("head", [0, 41, 0], "body")
    m.cube("head", [-4, 41, -4], [8, 9, 8], se_helm)
    m.cube("head", [-0.5, 49, -3], [1, 4, 7], vgrad_glow(SE_GOLD_HI, SE_GOLD, glow_from=0.75))
    m.bone("halo", [0, 56, 0], "head")
    halo = orb_paint("#ffd75a", "#fff6c8", "#ffe9a0")
    m.cube("halo", [-6, 55.5, -6], [12, 1, 1], halo)
    m.cube("halo", [-6, 55.5, 5], [12, 1, 1], halo)
    m.cube("halo", [-6, 55.5, -5], [1, 1, 10], halo)
    m.cube("halo", [5, 55.5, -5], [1, 1, 10], halo)
    # v1.3: gema solar no peito e uma segunda auréola em pé, atrás da cabeça (o "olho" do Julgamento)
    m.cube("body", [-1, 33, -4.6], [2, 2, 1], orb_paint("#fff1a8", "#ffffff", "#ffd75a"))
    m.cube("halo", [-4, 46, 5.5], [8, 1, 1], halo)
    m.cube("halo", [-4, 55, 5.5], [8, 1, 1], halo)
    m.cube("halo", [-5, 47, 5.5], [1, 8, 1], halo)
    m.cube("halo", [4, 47, 5.5], [1, 8, 1], halo)
    for side, sx in (("r", -1), ("l", 1)):
        arm = m.bone("arm_" + side, [sx * 8, 37, 0], "body")
        m.cube(arm, [-11 if sx < 0 else 6, 35, -3], [5, 4, 6], plate(SE_GOLD, 240 + sx, trim=SE_GOLD_HI))
        for k in range(3):
            fx = (-10.5 + k * 1.5) if sx < 0 else (6.5 + k * 1.5)
            m.cube(arm, [fx, 39, -1], [1, 3 + k % 2, 2], vgrad_glow(SE_GOLD_HI, SE_GOLD, glow_from=0.7))
        m.cube(arm, [-10.5 if sx < 0 else 6.5, 24, -2], [4, 12, 4], se_sleeve)
        m.cube(arm, [-11 if sx < 0 else 6, 21, -2.5], [5, 4, 5], plate(SE_GOLD, 243 + sx, trim=SE_GOLD_HI))
    # Lanca de luz na mao direita
    m.bone("lance", [-8.5, 23, -3], "arm_r")
    m.cube("lance", [-9, 2, -3.5], [1, 46, 1], vgrad_glow(SE_GOLD_HI, SE_GOLD_LO))
    m.cube("lance", [-10, 48, -4], [3, 7, 2], orb_paint("#fff1a8", "#ffffff", "#ffd75a"))
    m.cube("lance", [-9, 55, -3.5], [1, 3, 1], glow_all("#ffffff"))
    m.cube("lance", [-11, 46, -3.5], [5, 1, 1], plate(SE_GOLD_HI, 251))
    m.cube("lance", [-9.5, 0, -4], [2, 2, 2], plate(SE_GOLD, 252))
    # Seis asas: par de cima (levantado), do meio (aberto) e de baixo (caido)
    wing_specs = (
        ("1", [-26, 34, 4.0], [24, 14, 1], [-2, 37, 4.5], [0, -15, 35]),
        ("2", [-30, 25, 4.8], [28, 10, 1], [-2, 31, 5.3], [0, -20, 8]),
        ("3", [-22, 14, 5.6], [20, 12, 1], [-2, 25, 6.1], [0, -15, -30]),
    )
    for k, origin, size, pivot, rot in wing_specs:
        m.bone("wing_r" + k, pivot, "body", rotation=rot)
        m.cube("wing_r" + k, origin, size, feather_wing(-2, "#fbf8ef", SE_LINE, SE_GOLD_HI, SE_GLOW, 260 + int(k)))
        lo = [-(origin[0] + size[0]), origin[1], origin[2]]
        m.bone("wing_l" + k, [-pivot[0], pivot[1], pivot[2]], "body", rotation=[rot[0], -rot[1], -rot[2]])
        m.cube("wing_l" + k, lo, size, feather_wing(2, "#fbf8ef", SE_LINE, SE_GOLD_HI, SE_GLOW, 270 + int(k)))
    m.layout()
    m.paint()
    m.save_geo((8, 7, 2.5))
    m.save_texture()
    return m


def wing_rest(k):
    return {"1": 35, "2": 8, "3": -30}[k]


def wing_flap(amp, length, extra_x=0.0, sweep=0.0):
    """Canais das 6 asas batendo (fase defasada entre os pares)."""
    out = {}
    for k, ph0 in (("1", 0.0), ("2", 0.08), ("3", 0.16)):
        rz = wing_rest(k)
        out["wing_r" + k] = {"rotation": wave(length, lambda p, rz=rz, ph0=ph0: [extra_x, -15 - sweep, rz + sinv(p + ph0, amp)])}
        out["wing_l" + k] = {"rotation": wave(length, lambda p, rz=rz, ph0=ph0: [extra_x, 15 + sweep, -(rz + sinv(p + ph0, amp))])}
    return out


def wings_pose(t_pose):
    """Pose fixa das asas: lista de (tempo, z_extra, y_extra)."""
    out = {}
    for k in ("1", "2", "3"):
        rz = wing_rest(k)
        base_y = {"1": -15, "2": -20, "3": -15}[k]
        out["wing_r" + k] = {"rotation": {t: [0, base_y + yy, rz + zz] for t, zz, yy in t_pose}}
        out["wing_l" + k] = {"rotation": {t: [0, -(base_y + yy), -(rz + zz)] for t, zz, yy in t_pose}}
    return out


def anims_seraph():
    a = Anims("seraph")
    idle = {
        "root": {"position": wave(3.0, lambda p: [0, sinv(p, 1.5), 0])},
        "body": {"rotation": wave(3.0, lambda p: [sinv(p, 1.5, mult=1), 0, 0])},
        "robe": {"rotation": wave(3.0, lambda p: [sinv(p, 3, 2), 0, cosv(p, 2)])},
        "halo": {"rotation": wave(3.0, lambda p: [0, 360 * p, 0], 8)},
        "arm_r": {"rotation": wave(3.0, lambda p: [sinv(p, 3, -8), 0, 6])},
        "arm_l": {"rotation": wave(3.0, lambda p: [-sinv(p, 3), 0, cosv(p, 3, -10)])},
    }
    idle.update(wing_flap(8, 3.0))
    a.add("idle", 3.0, True, idle)
    move = {
        "root": {"position": wave(1.0, lambda p: [0, sinv(p, 1.2), 0])},
        "body": {"rotation": wave(1.0, lambda p: [12, 0, 0])},
        "robe": {"rotation": wave(1.0, lambda p: [sinv(p, 4, 16), 0, 0])},
        "halo": {"rotation": wave(1.0, lambda p: [0, 360 * p, 0], 8)},
        "arm_r": {"rotation": wave(1.0, lambda p: [10, 0, 8])},
        "arm_l": {"rotation": wave(1.0, lambda p: [10, 0, -8])},
    }
    move.update(wing_flap(22, 1.0, sweep=8))
    a.add("move", 1.0, True, move)
    death = {
        "root": {"position": K(0, [0, 0, 0], 1.0, [0, 2, 0], 4.0, [0, -10, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [-20, 0, 0], 4.0, [40, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-30, 0, 0], 4.0, [35, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 1.0, [-40, 0, 60], 4.0, [10, 0, 10])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 1.0, [-40, 0, -60], 4.0, [10, 0, -10])},
        "halo": {"position": K(0, [0, 0, 0], 4.0, [0, -6, -4]), "scale": K(0, [1, 1, 1], 3.0, [1, 1, 1], 4.0, [0, 0, 0])},
    }
    death.update(wings_pose([(0, 0, 0), (1.0, 15, 10), (4.0, -45, 25)]))
    a.add("death", 4.0, "hold", death)
    intro = {
        "root": {"position": K(0, [0, -6, 0], 1.5, [0, -2, 0], 2.0, [0, 2, 0], 3.5, [0, 0, 0])},
        "body": {"rotation": K(0, [20, 0, 0], 1.5, [20, 0, 0], 2.0, [-15, 0, 0], 3.5, [0, 0, 0])},
        "head": {"rotation": K(0, [30, 0, 0], 1.5, [30, 0, 0], 2.0, [-25, 0, 0], 3.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [-60, 0, -20], 1.5, [-60, 0, -20], 2.0, [-30, 0, 70], 3.0, [-30, 0, 70], 3.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-60, 0, 20], 1.5, [-60, 0, 20], 2.0, [-30, 0, -70], 3.0, [-30, 0, -70], 3.5, [0, 0, 0])},
        "halo": {"scale": K(0, [0, 0, 0], 1.6, [0, 0, 0], 2.0, [1.4, 1.4, 1.4], 2.6, [1, 1, 1])},
    }
    intro.update(wings_pose([(0, -40, 70), (1.5, -40, 70), (2.0, 25, -10), (3.0, 20, -10), (3.5, 0, 0)]))
    a.add("intro", 3.5, False, intro)
    phase = {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 2.0, [-15, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-25, 0, 0], 2.0, [-25, 0, 0], 2.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-20, 0, 80], 2.0, [-20, 0, 80], 2.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [-20, 0, -80], 2.0, [-20, 0, -80], 2.5, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 0.5, [1.6, 1.6, 1.6], 2.0, [1.6, 1.6, 1.6], 2.5, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 2.5, [0, 720, 0])},
    }
    phase.update(wings_pose([(0, 0, 0), (0.5, 30, -15), (0.9, -10, 0), (1.3, 30, -15), (1.7, -10, 0), (2.1, 25, -10), (2.5, 0, 0)]))
    a.add("phase", 2.5, False, phase)
    a.add("throw", 1.7, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.3, [-150, 0, 10], 0.5, [-60, 0, 0], 0.9, [-60, 0, 0], 1.7, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.3, [-8, -15, 0], 0.5, [10, 10, 0], 1.7, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.3, [-50, 0, -30], 1.7, [0, 0, 0])},
    })
    a.add("ray", 2.3, False, {
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-90, 0, -5], 1.9, [-90, 0, -5], 2.3, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-20, 0, 40], 1.9, [-20, 0, 40], 2.3, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-5, 0, 0], 1.9, [-5, 0, 0], 2.3, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [5, 0, 0], 2.3, [0, 0, 0])},
    })
    beams = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, 88], 6.0, [0, 0, 88], 6.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, -88], 6.0, [0, 0, -88], 6.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-15, 0, 0], 6.0, [-15, 0, 0], 6.5, [0, 0, 0])},
        "halo": {"rotation": K(0, [0, 0, 0], 6.5, [0, 1440, 0]), "scale": K(0, [1, 1, 1], 1.0, [1.5, 1.5, 1.5], 6.0, [1.5, 1.5, 1.5], 6.5, [1, 1, 1])},
    }
    beams.update(wings_pose([(0, 0, 0), (1.0, 20, -25), (6.0, 20, -25), (6.5, 0, 0)]))
    a.add("beams", 6.5, False, beams)
    judg = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-170, 0, 15], 2.0, [-170, 0, 15], 2.2, [-60, 0, 10], 3.6, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-170, 0, -15], 2.0, [-170, 0, -15], 2.2, [-60, 0, -10], 3.6, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [-25, 0, 0], 2.0, [-25, 0, 0], 2.2, [10, 0, 0], 3.6, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 1.8, [1.8, 1.8, 1.8], 2.2, [1, 1, 1])},
    }
    judg.update(wings_pose([(0, 0, 0), (0.4, 25, -10), (2.0, 25, -10), (2.2, -15, 15), (3.6, 0, 0)]))
    a.add("judgment", 3.6, False, judg)
    gust = {"body": {"rotation": K(0, [0, 0, 0], 0.3, [-10, 0, 0], 0.5, [12, 0, 0], 1.2, [0, 0, 0])}}
    gust.update(wings_pose([(0, 0, 0), (0.3, 15, -45), (0.5, -10, 55), (0.8, -5, 30), (1.2, 0, 0)]))
    a.add("gust", 1.2, False, gust)
    dive = {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 0.6, [60, 0, 0], 1.2, [60, 0, 0], 1.7, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-160, 0, 0], 0.6, [-100, 0, 0], 1.2, [-100, 0, 0], 1.7, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-40, 0, 0], 1.2, [-40, 0, 0], 1.7, [0, 0, 0])},
    }
    dive.update(wings_pose([(0, 0, 0), (0.5, 30, -10), (0.6, -5, -50), (1.2, -5, -50), (1.7, 0, 0)]))
    a.add("dive", 1.7, False, dive)
    summ = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-140, 0, 40], 1.6, [-140, 0, 40], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-140, 0, -40], 1.6, [-140, 0, -40], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-20, 0, 0], 2.0, [0, 0, 0])},
    }
    summ.update(wings_pose([(0, 0, 0), (0.6, 25, -15), (1.6, 25, -15), (2.0, 0, 0)]))
    a.add("summon", 2.0, False, summ)
    pray = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-65, 0, -25], 2.6, [-65, 0, -25], 3.2, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [-65, 0, 25], 2.6, [-65, 0, 25], 3.2, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [22, 0, 0], 2.6, [22, 0, 0], 3.2, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [8, 0, 0], 2.6, [8, 0, 0], 3.2, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 2.4, [2.0, 2.0, 2.0], 2.6, [1.2, 1.2, 1.2], 3.2, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 3.2, [0, 900, 0])},
    }
    pray.update(wings_pose([(0, 0, 0), (0.5, -25, 55), (2.6, -25, 55), (3.2, 0, 0)]))
    a.add("pray", 3.2, False, pray)
    # v0.7 - Julgamento Cruzado (62 ticks): bracos em cruz, asas abertas; em t26 e t44 os bracos cruzam no peito
    # (cada cruz de luz desce) e voltam a abrir; no fim relaxa (pousa exausto).
    cross = {
        "root": {"position": K(0, [0, 0, 0], 0.5, [0, 1.5, 0], 2.5, [0, 1.5, 0], 3.1, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-8, 0, 0], 1.25, [-10, 0, 0], 1.32, [12, 0, 0], 1.6, [-8, 0, 0],
                               2.15, [-10, 0, 0], 2.22, [12, 0, 0], 2.6, [6, 0, 0], 3.1, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-18, 0, 0], 1.25, [-20, 0, 0], 1.32, [8, 0, 0], 1.6, [-16, 0, 0],
                               2.15, [-18, 0, 0], 2.22, [10, 0, 0], 3.1, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [0, 0, 88], 1.25, [0, 0, 90], 1.32, [-90, 0, -22], 1.6, [0, 0, 88],
                                2.15, [0, 0, 90], 2.22, [-90, 0, -22], 2.6, [-40, 0, 10], 3.1, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [0, 0, -88], 1.25, [0, 0, -90], 1.32, [-90, 0, 22], 1.6, [0, 0, -88],
                                2.15, [0, 0, -90], 2.22, [-90, 0, 22], 2.6, [-40, 0, -10], 3.1, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 0.5, [1.8, 1.8, 1.8], 1.25, [2.0, 2.0, 2.0], 1.32, [1.2, 1.2, 1.2], 2.15, [2.0, 2.0, 2.0],
                            2.22, [1.2, 1.2, 1.2], 3.1, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 3.1, [0, 1080, 0])},
    }
    cross.update(wings_pose([(0, 0, 0), (0.5, 35, -30), (1.25, 35, -30), (1.32, -10, 10), (1.6, 35, -30), (2.15, 35, -30),
                             (2.22, -10, 10), (2.6, 10, 0), (3.1, 0, 0)]))
    a.add("cross", 3.1, False, cross)
    # v0.7 - Pousado e exausto (laco do controlador principal): curvado, bracos caidos, asas arriadas, aureola fraca.
    rest = {
        "body": {"rotation": wave(3.0, lambda p: [22 + sinv(p, 2.5), 0, 0])},
        "head": {"rotation": wave(3.0, lambda p: [20 + sinv(p, 3), sinv(p, 4), 0])},
        "robe": {"rotation": wave(3.0, lambda p: [8, 0, sinv(p, 1.5)])},
        "arm_r": {"rotation": wave(3.0, lambda p: [14 + sinv(p, 2), 0, 8])},
        "arm_l": {"rotation": wave(3.0, lambda p: [14 + sinv(p, 2), 0, -8])},
        "halo": {"rotation": wave(3.0, lambda p: [0, 120 * p, 0], 6), "position": wave(3.0, lambda p: [0, -1.5, 0]),
                 "scale": wave(3.0, lambda p: [0.72 + sinv(p, 0.04)] * 3)},
    }
    rest.update(wings_pose([(0, -32, 25), (1.5, -27, 21), (3.0, -32, 25)]))
    a.add("rest", 3.0, True, rest)
    a.save()


# =====================================================================================
#  ARAUTO DO CREPUSCULO (duas texturas: postura de luz / de sombra)
# =====================================================================================
HE_LIGHT = "#eee6d4"
HE_GOLD = "#e0b54a"
HE_GOLD_HI = "#ffe08a"
HE_DARK = "#1e1529"
HE_DARK_HI = "#352650"
HE_VIOLET = "#6d3fa8"
HE_GLOW_L = "#ffeaa0"
HE_GLOW_D = "#c58cff"


def he_glow(ctx, side):
    """Cor e brilho das runas: so o lado da postura atual acende."""
    if side > 0:
        return (C(HE_GLOW_L), True) if ctx == "light" else (C("#a8935c"), False)
    return (C(HE_GLOW_D), True) if ctx == "shadow" else (C("#4a3366"), False)


def split_shade(light, dark, seed, noise=0.05, light_trim=None, dark_trim=None):
    """Pinta cada pixel com a cor do lado (esquerdo = luz, direito = sombra) + sombreado + filetes."""
    def p(fs, c, ctx):
        rnd = random.Random(seed)
        for n, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    side = f.side_of(xx)
                    col = C(light) if side > 0 else C(dark)
                    if n == "top":
                        col = lighten(col, 0.1)
                    elif n == "bottom":
                        col = darken(col, 0.3)
                    elif n in ("left", "right", "back"):
                        col = darken(col, 0.06)
                    if n not in ("top", "bottom") and f.h > 2:
                        col = darken(col, 0.18 * yy / (f.h - 1))
                    r = (rnd.random() - 0.5) * 2 * noise
                    col = lighten(col, r) if r > 0 else darken(col, -r)
                    f.set(xx, yy, col)
            if n in ("front", "back", "left", "right") and f.h > 3:
                for xx in range(f.w):
                    side = f.side_of(xx)
                    tc = light_trim if side > 0 else dark_trim
                    if tc:
                        f.set(xx, 0, C(tc))
                        f.set(xx, f.h - 1, darken(C(tc), 0.25))
            # Costura do meio (frente/costas/topo)
            if n in ("front", "back", "top", "bottom"):
                for xx in range(f.w - 1):
                    if f.side_of(xx) != f.side_of(xx + 1):
                        for yy in range(f.h):
                            f.set(xx, yy, C("#8a5a9e"))
    return p


def he_runes(faces, rows, step=3):
    def p(fs, c, ctx):
        for n in faces:
            f = fs[n]
            for r in rows:
                yy = r if r >= 0 else f.h + r
                for xx in range(1, f.w - 1):
                    if (xx + yy) % step == 0:
                        col, g = he_glow(ctx, f.side_of(xx))
                        f.set(xx, yy, col, g)
    return p


def he_head(fs, c, ctx):
    f = fs["front"]
    # Metade direita (sombra) com olho violeta; metade esquerda (luz) mascara marfim com olho dourado
    for xx in range(f.w):
        side = f.side_of(xx)
        for yy in range(2, f.h - 1):
            if side > 0:
                f.set(xx, yy, C("#fbf6ea") if yy < f.h - 2 else C("#ddd2bc"))
            else:
                f.set(xx, yy, C("#140d1c") if yy < f.h - 2 else C("#24183a"))
    for xx in range(f.w):
        col, g = he_glow(ctx, f.side_of(xx))
        if xx in (1, 2) or xx in (f.w - 3, f.w - 2):
            f.set(xx, 4, col, g)
    f.set(f.w // 2 - 1, 6, C("#5a3a70"))
    f.set(f.w // 2, 6, C("#c9b38a"))


def he_crown_ray(fs, c, ctx):
    col, g = he_glow(ctx, 1)
    vgrad_glow(HE_GOLD_HI, HE_GOLD, glow_from=0.6 if g else None)(fs, c, ctx)


def he_horn(tip):
    def p(fs, c, ctx):
        col, g = he_glow(ctx, -1)
        if tip:
            vgrad_glow("#d7b8ff" if g else "#6b5a80", "#3a2a52", glow_from=0.5 if g else None)(fs, c, ctx)
        else:
            vgrad_glow("#3a2a52", "#120c1a")(fs, c, ctx)
    return p


def he_halo_bar(fs, c, ctx):
    for n, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                side = f.side_of(xx)
                col, g = he_glow(ctx, side)
                base = C("#ffd75a") if side > 0 else C("#7a3fd0")
                f.set(xx, yy, mix(base, col, 0.5), g)


def he_orb(light):
    def p(fs, c, ctx):
        on = (ctx == "light") == light
        if light:
            orb_paint("#ffe27a", "#ffffff", "#ffc93c")(fs, c, ctx) if on else solid("#b8a46a", 301, noise=0.02, grad=0)(fs, c, ctx)
        else:
            orb_paint("#9a55f0", "#e8c8ff", "#6a2fc0")(fs, c, ctx) if on else solid("#3a2a55", 302, noise=0.02, grad=0)(fs, c, ctx)
    return p


def he_torso(fs, c, ctx):
    split_shade(HE_LIGHT, HE_DARK, 401, light_trim=HE_GOLD, dark_trim=HE_VIOLET)(fs, c, ctx)
    f = fs["front"]
    cx, cy = f.w // 2, 6
    # Emblema do eclipse: sol de um lado, lua do outro
    for dx in range(-3, 3):
        for dy in range(-3, 4):
            if (dx + 0.5) ** 2 + dy * dy <= 9:
                xx = cx + dx
                col, g = he_glow(ctx, f.side_of(xx))
                f.set(xx, cy + dy, col if abs(dy) < 3 else darken(col, 0.3), g)
    for yy in (10, 12):
        for xx in range(1, f.w - 1):
            f.set(xx, yy, darken(f.get(xx, yy), 0.3))


def he_robe(seed, hem=False):
    def p(fs, c, ctx):
        split_shade("#e8dfcb", "#24183a", seed, light_trim=HE_GOLD, dark_trim=HE_VIOLET)(fs, c, ctx)
        if hem:
            rnd = random.Random(seed)
            for n in ("front", "back", "left", "right"):
                f = fs[n]
                for xx in range(f.w):
                    side = f.side_of(xx)
                    if side < 0:
                        for k in range(rnd.randint(0, 3)):
                            f.set(xx, f.h - 1 - k, CLEAR)
                    else:
                        col, g = he_glow(ctx, 1)
                        f.set(xx, f.h - 1, col, g)
                        f.set(xx, f.h - 2, mix(f.get(xx, f.h - 2), col, 0.5))
        he_runes(("front", "back"), [3], step=4)(fs, c, ctx)
    return p


def he_arm_light(fs, c, ctx):
    plate(HE_LIGHT, 411, edge_dark=0.25, trim=HE_GOLD)(fs, c, ctx)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        col, g = he_glow(ctx, 1)
        f.set(f.w // 2, f.h - 3, col, g)


def he_arm_dark(fs, c, ctx):
    plate(HE_DARK_HI, 412, edge_dark=0.5, trim=HE_VIOLET)(fs, c, ctx)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        col, g = he_glow(ctx, -1)
        f.set(f.w // 2, f.h - 3, col, g)


def build_herald():
    m = Model("twilight_herald")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 26, 0], "root")
    m.cube("body", [-7, 26, -4], [14, 14, 8], he_torso)
    m.cube("body", [-4.5, 40, -3], [9, 2, 6], split_shade(HE_GOLD, HE_VIOLET, 402))
    m.bone("robe", [0, 26, 0], "body")
    m.cube("robe", [-6, 20, -3.5], [12, 6, 7], split_shade(HE_GOLD, HE_VIOLET, 403, light_trim=HE_GOLD_HI, dark_trim="#a070e0"))
    m.cube("robe", [-6, 10, -4], [12, 10, 8], he_robe(404))
    m.cube("robe", [-5, 0, -3.5], [10, 10, 7], he_robe(405, hem=True))
    m.bone("head", [0, 42, 0], "body")
    m.cube("head", [-4, 42, -4], [8, 9, 8], chain(split_shade(HE_GOLD, HE_DARK_HI, 406), he_head))
    m.bone("crown", [0, 51, 0], "head")
    for pos, size in (([1.5, 51, -4.5], [1, 3, 1]), ([3, 51, -3], [1, 4, 1]), ([3.5, 51, 0.5], [1, 3, 1]), ([1.5, 51, 3], [1, 2, 1])):
        m.cube("crown", pos, size, he_crown_ray)
    m.bone("horn", [-3, 51, 0], "crown", rotation=[-10, 0, -25])
    m.cube("horn", [-4.5, 51, -1], [2, 5, 2], he_horn(False))
    m.bone("horn_tip", [-3.5, 56, 0], "horn", rotation=[-20, 0, 40])
    m.cube("horn_tip", [-4, 56, -0.5], [1, 4, 1], he_horn(True))
    m.bone("halo", [0, 47, 7], "head")
    for pos, size in (([-7, 54, 6.5], [14, 1, 1]), ([-7, 40, 6.5], [14, 1, 1]), ([6, 41, 6.5], [1, 13, 1]),
                      ([-7, 41, 6.5], [1, 13, 1]), ([-5, 52, 6.5], [2, 2, 1]), ([3, 52, 6.5], [2, 2, 1]),
                      ([-5, 41, 6.5], [2, 2, 1]), ([3, 41, 6.5], [2, 2, 1])):
        m.cube("halo", pos, size, he_halo_bar)
    # v1.3: a auréola quadrada vira um RELÓGIO — ponteiros de luz no centro
    m.cube("halo", [-0.5, 47, 6.5], [1, 5, 1], he_halo_bar)
    m.cube("halo", [0.5, 47, 6.5], [3, 1, 1], he_halo_bar)
    m.cube("halo", [-1, 46.5, 6.2], [2, 2, 1], he_orb(True))
    m.cube("body", [-1, 34, -4.6], [1, 2, 1], he_orb(False))
    m.cube("body", [0, 34, -4.6], [1, 2, 1], he_orb(True))
    # Braco da sombra (direito) com garras; braco da luz (esquerdo) dourado
    m.bone("arm_r", [-8.5, 38, 0], "body")
    m.cube("arm_r", [-12, 36, -3.5], [6, 5, 7], he_arm_dark)
    m.cube("arm_r", [-11, 41, -1], [2, 3, 2], he_horn(True))
    m.cube("arm_r", [-11.5, 23, -2], [4, 14, 4], cloth("#24183a", 413))
    m.cube("arm_r", [-12, 20, -2.5], [5, 4, 5], he_arm_dark)
    for k in range(3):
        m.cube("arm_r", [-11.5 + k * 1.5, 17, -2], [1, 3, 1], vgrad_glow("#2a2235", "#d8c8f0"))
    m.bone("arm_l", [8.5, 38, 0], "body")
    m.cube("arm_l", [6, 36, -3.5], [6, 5, 7], he_arm_light)
    m.cube("arm_l", [7.5, 23, -2], [4, 14, 4], cloth("#e8dfcb", 414))
    m.cube("arm_l", [7, 20, -2.5], [5, 4, 5], he_arm_light)
    # Orbes flutuando junto das maos
    m.bone("orb_light", [12, 22, -3], "body")
    m.cube("orb_light", [10.5, 20.5, -4.5], [3, 3, 3], he_orb(True))
    m.bone("orb_shadow", [-12, 22, -3], "body")
    m.cube("orb_shadow", [-13.5, 20.5, -4.5], [3, 3, 3], he_orb(False))
    # Asas: membrana rasgada (sombra, direita) e penas (luz, esquerda)
    m.bone("wing_r", [-2, 37, 4.5], "body", rotation=[0, -15, 20])
    m.cube("wing_r", [-30, 21, 4], [28, 18, 1], lambda fs, c, ctx: membrane_wing(-2, "#2a1840", "#120a1c", he_glow(ctx, -1)[0], 501, glow=he_glow(ctx, -1)[1])(fs, c, ctx))
    m.bone("wing_r2", [-2, 29, 5.2], "body", rotation=[0, -15, -25])
    m.cube("wing_r2", [-20, 17, 4.7], [18, 12, 1], lambda fs, c, ctx: membrane_wing(-2, "#2a1840", "#120a1c", he_glow(ctx, -1)[0], 502, glow=he_glow(ctx, -1)[1])(fs, c, ctx))
    m.bone("wing_l", [2, 37, 4.5], "body", rotation=[0, 15, -20])
    m.cube("wing_l", [2, 21, 4], [28, 18, 1], lambda fs, c, ctx: feather_wing(2, "#f7f1e2", "#d2c6aa", HE_GOLD_HI, HE_GLOW_L, 503, glow=ctx == "light")(fs, c, ctx))
    m.bone("wing_l2", [2, 29, 5.2], "body", rotation=[0, 15, 25])
    m.cube("wing_l2", [2, 17, 4.7], [18, 12, 1], lambda fs, c, ctx: feather_wing(2, "#f7f1e2", "#d2c6aa", HE_GOLD_HI, HE_GLOW_L, 504, glow=ctx == "light")(fs, c, ctx))
    m.layout()
    m.save_geo((8, 7, 2.5))
    for stance in ("light", "shadow"):
        m.paint(stance)
        m.save_texture("twilight_herald_" + stance)
    return m


def herald_wings(t_pose):
    """(tempo, z_extra, y_extra) para as duas asas de cada lado."""
    rest = {"wing_r": (20, -15), "wing_r2": (-25, -15), "wing_l": (-20, 15), "wing_l2": (25, 15)}
    out = {}
    for b, (rz, ry) in rest.items():
        s = 1 if b.startswith("wing_r") else -1
        out[b] = {"rotation": {t: [0, ry + s * yy, rz + s * zz] for t, zz, yy in t_pose}}
    return out


def herald_flap(amp, length):
    rest = {"wing_r": (20, -15, 0.0), "wing_r2": (-25, -15, 0.1), "wing_l": (-20, 15, 0.0), "wing_l2": (25, 15, 0.1)}
    out = {}
    for b, (rz, ry, ph0) in rest.items():
        s = 1 if b.startswith("wing_r") else -1
        out[b] = {"rotation": wave(length, lambda p, rz=rz, ry=ry, s=s, ph0=ph0: [0, ry, rz + s * sinv(p + ph0, amp)])}
    return out


def anims_herald():
    a = Anims("twilight_herald")
    idle = {
        "root": {"position": wave(4.0, lambda p: [0, sinv(p, 1.8), 0])},
        "body": {"rotation": wave(4.0, lambda p: [sinv(p, 1.5), 0, 0])},
        "robe": {"rotation": wave(4.0, lambda p: [sinv(p, 3, 3), 0, cosv(p, 2)])},
        "halo": {"rotation": wave(4.0, lambda p: [0, 0, 360 * p], 8)},
        "orb_light": {"position": wave(4.0, lambda p: [0, sinv(p, 1.5, mult=2), 0])},
        "orb_shadow": {"position": wave(4.0, lambda p: [0, -sinv(p, 1.5, mult=2), 0])},
        "arm_r": {"rotation": wave(4.0, lambda p: [sinv(p, 3, -10), 0, 8])},
        "arm_l": {"rotation": wave(4.0, lambda p: [-sinv(p, 3, 10), 0, -8])},
    }
    idle.update(herald_flap(7, 4.0))
    a.add("idle", 4.0, True, idle)
    move = {
        "root": {"position": wave(1.2, lambda p: [0, sinv(p, 1.0), 0])},
        "body": {"rotation": wave(1.2, lambda p: [12, 0, 0])},
        "robe": {"rotation": wave(1.2, lambda p: [sinv(p, 4, 18), 0, 0])},
        "halo": {"rotation": wave(1.2, lambda p: [0, 0, 360 * p], 8)},
        "arm_r": {"rotation": wave(1.2, lambda p: [15, 0, 10])},
        "arm_l": {"rotation": wave(1.2, lambda p: [15, 0, -10])},
    }
    move.update(herald_flap(20, 1.2))
    a.add("move", 1.2, True, move)
    death = {
        "root": {"position": K(0, [0, 0, 0], 7.0, [0, 4, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [-25, 0, 0], 7.0, [-30, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-35, 0, 0], 7.0, [-40, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 1.0, [-30, 0, 85], 7.0, [-45, 0, 90])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 1.0, [-30, 0, -85], 7.0, [-45, 0, -90])},
        "halo": {"rotation": K(0, [0, 0, 0], 7.0, [0, 0, 2520]),
                 "scale": K(0, [1, 1, 1], 3.0, [1.6, 1.6, 1.6], 6.0, [2.2, 2.2, 2.2], 6.8, [0, 0, 0])},
        "orb_light": {"position": K(0, [0, 0, 0], 3.0, [6, 12, 0], 6.5, [0, 30, 0]), "scale": K(0, [1, 1, 1], 6.5, [0, 0, 0])},
        "orb_shadow": {"position": K(0, [0, 0, 0], 3.0, [-6, 12, 0], 6.5, [0, 30, 0]), "scale": K(0, [1, 1, 1], 6.5, [0, 0, 0])},
    }
    death.update(herald_wings([(0, 0, 0), (1.0, 25, -15), (5.0, 25, -15), (7.0, -50, 30)]))
    a.add("death", 7.0, "hold", death)
    intro = {
        "root": {"position": K(0, [0, -4, 0], 1.6, [0, -4, 0], 2.4, [0, 2, 0], 3.5, [0, 0, 0])},
        "body": {"rotation": K(0, [25, 0, 0], 1.6, [25, 0, 0], 2.4, [-15, 0, 0], 3.5, [0, 0, 0])},
        "head": {"rotation": K(0, [35, 0, 0], 1.6, [35, 0, 0], 2.4, [-20, 0, 0], 3.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [-50, 0, -30], 1.6, [-50, 0, -30], 2.4, [-20, 0, 75], 3.0, [-20, 0, 75], 3.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-50, 0, 30], 1.6, [-50, 0, 30], 2.4, [-20, 0, -75], 3.0, [-20, 0, -75], 3.5, [0, 0, 0])},
        "halo": {"scale": K(0, [0, 0, 0], 1.8, [0, 0, 0], 2.4, [1.5, 1.5, 1.5], 3.0, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 3.5, [0, 0, 720])},
    }
    intro.update(herald_wings([(0, -40, 60), (1.6, -40, 60), (2.4, 25, -15), (3.0, 20, -10), (3.5, 0, 0)]))
    a.add("intro", 3.5, False, intro)
    phase = {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 2.0, [-15, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-25, 0, 0], 2.0, [-25, 0, 0], 2.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-20, 0, 80], 2.0, [-20, 0, 80], 2.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [-20, 0, -80], 2.0, [-20, 0, -80], 2.5, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 0.5, [1.7, 1.7, 1.7], 2.0, [1.7, 1.7, 1.7], 2.5, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 2.5, [0, 0, 1080])},
    }
    phase.update(herald_wings([(0, 0, 0), (0.5, 25, -15), (2.0, 25, -15), (2.5, 0, 0)]))
    a.add("phase", 2.5, False, phase)
    a.add("shift", 1.2, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [0, 360, 0], 1.2, [0, 360, 0])},
        "halo": {"rotation": K(0, [0, 0, 0], 1.2, [0, 0, 540]), "scale": K(0, [1, 1, 1], 0.5, [1.4, 1.4, 1.4], 1.2, [1, 1, 1])},
        "orb_light": {"position": K(0, [0, 0, 0], 0.6, [0, 6, 0], 1.2, [0, 0, 0])},
        "orb_shadow": {"position": K(0, [0, 0, 0], 0.6, [0, 6, 0], 1.2, [0, 0, 0])},
    })
    a.add("sweep", 1.0, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.3, [-100, 45, 30], 0.5, [-80, -50, -10], 1.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.3, [0, 25, 0], 0.5, [8, -30, 0], 1.0, [0, 0, 0])},
    })
    a.add("cast_light", 1.7, False, {
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-95, 0, -5], 1.3, [-95, 0, -5], 1.7, [0, 0, 0])},
        "orb_light": {"position": K(0, [0, 0, 0], 0.4, [-2, 6, -8], 1.3, [-2, 6, -8], 1.7, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [0, -12, 0], 1.7, [0, 0, 0])},
    })
    a.add("cast_shadow", 2.0, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-95, 0, 5], 1.6, [-95, 0, 5], 2.0, [0, 0, 0])},
        "orb_shadow": {"position": K(0, [0, 0, 0], 0.4, [2, 6, -8], 1.6, [2, 6, -8], 2.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [0, 12, 0], 2.0, [0, 0, 0])},
    })
    beams = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, 88], 5.0, [0, 0, 88], 5.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, -88], 5.0, [0, 0, -88], 5.5, [0, 0, 0])},
        "halo": {"rotation": K(0, [0, 0, 0], 5.5, [0, 0, 1800]), "scale": K(0, [1, 1, 1], 1.0, [1.5, 1.5, 1.5], 5.0, [1.5, 1.5, 1.5], 5.5, [1, 1, 1])},
    }
    beams.update(herald_wings([(0, 0, 0), (1.0, 20, -20), (5.0, 20, -20), (5.5, 0, 0)]))
    a.add("beams", 5.5, False, beams)
    a.add("judgment", 3.0, False, {
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-170, 0, -15], 1.7, [-170, 0, -15], 1.9, [-60, 0, -10], 3.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-120, 0, 30], 1.7, [-120, 0, 30], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [-25, 0, 0], 1.7, [-25, 0, 0], 1.9, [10, 0, 0], 3.0, [0, 0, 0])},
    })
    a.add("teleport", 1.4, False, {
        "root": {"position": K(0, [0, 0, 0], 0.25, [0, -6, 0], 0.59, [0, -6, 0], 0.6, [0, 8, 0], 0.9, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.25, [0, 180, 0], 0.6, [0, 360, 0], 0.61, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 0.25, [0, 0, 0], 0.6, [0, 0, 0], 0.9, [1, 1, 1])},
    })
    summ = {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-140, 0, 40], 1.6, [-140, 0, 40], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-140, 0, -40], 1.6, [-140, 0, -40], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-20, 0, 0], 2.0, [0, 0, 0])},
    }
    summ.update(herald_wings([(0, 0, 0), (0.6, 25, -15), (1.6, 25, -15), (2.0, 0, 0)]))
    a.add("summon", 2.0, False, summ)
    dom = {
        "root": {"position": K(0, [0, 0, 0], 1.5, [0, 3, 0], 3.0, [0, 3, 0], 3.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-70, 0, -30], 1.5, [-70, 0, -30], 1.7, [-20, 0, 85], 3.0, [-20, 0, 85], 3.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-70, 0, 30], 1.5, [-70, 0, 30], 1.7, [-20, 0, -85], 3.0, [-20, 0, -85], 3.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [15, 0, 0], 1.5, [15, 0, 0], 1.7, [-30, 0, 0], 3.0, [-30, 0, 0], 3.5, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 1.5, [0.6, 0.6, 0.6], 1.7, [2.6, 2.6, 2.6], 3.0, [2.2, 2.2, 2.2], 3.5, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 3.5, [0, 0, 1440])},
        "orb_light": {"position": K(0, [0, 0, 0], 1.5, [-10, 8, -6], 1.7, [6, 14, 0], 3.0, [6, 14, 0], 3.5, [0, 0, 0])},
        "orb_shadow": {"position": K(0, [0, 0, 0], 1.5, [10, 8, -6], 1.7, [-6, 14, 0], 3.0, [-6, 14, 0], 3.5, [0, 0, 0])},
    }
    dom.update(herald_wings([(0, 0, 0), (1.5, -20, 40), (1.7, 30, -20), (3.0, 30, -20), (3.5, 0, 0)]))
    a.add("domain", 3.5, False, dom)
    a.add("nova", 2.8, False, {
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-170, 0, 10], 0.8, [-30, 0, 20], 2.4, [-30, 0, 20], 2.8, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-170, 0, -10], 0.8, [-30, 0, -20], 2.4, [-30, 0, -20], 2.8, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-12, 0, 0], 0.8, [18, 0, 0], 2.4, [18, 0, 0], 2.8, [0, 0, 0])},
        "halo": {"scale": K(0, [1, 1, 1], 0.8, [2, 2, 2], 1.4, [1, 1, 1])},
    })
    # v0.7 - Ruptura Crepuscular: bracos abertos com as palmas para o chao; a cada explosao do tabuleiro
    # (t30, t54, t78) empurra as maos para baixo e o halo pisca.
    beats = (1.5, 2.7, 3.9)
    rup_root, rup_body, rup_arm_r, rup_arm_l, rup_ol, rup_os, rup_halo, rup_wings = (
        {0: [0, 0, 0], 0.8: [0, 1.5, 0]}, {0: [0, 0, 0], 0.8: [-6, 0, 0]},
        {0: [0, 0, 0], 0.8: [-20, 0, 72]}, {0: [0, 0, 0], 0.8: [-20, 0, -72]},
        {0: [0, 0, 0], 0.8: [4, 8, 0]}, {0: [0, 0, 0], 0.8: [-4, 8, 0]},
        {0: [1, 1, 1], 0.8: [1.6, 1.6, 1.6]}, [(0, 0, 0), (0.8, 28, -18)])
    for b in beats:
        pre, hit, back = b - 0.05, b + 0.02, b + 0.4
        rup_root.update({pre: [0, 1.5, 0], hit: [0, -1, 0], back: [0, 1, 0]})
        rup_body.update({pre: [-8, 0, 0], hit: [18, 0, 0], back: [-4, 0, 0]})
        rup_arm_r.update({pre: [-24, 0, 75], hit: [22, 0, 34], back: [-20, 0, 70]})
        rup_arm_l.update({pre: [-24, 0, -75], hit: [22, 0, -34], back: [-20, 0, -70]})
        rup_ol.update({pre: [4, 9, 0], hit: [2, -5, 0], back: [4, 8, 0]})
        rup_os.update({pre: [-4, 9, 0], hit: [-2, -5, 0], back: [-4, 8, 0]})
        rup_halo.update({pre: [1.7, 1.7, 1.7], hit: [2.4, 2.4, 2.4], back: [1.6, 1.6, 1.6]})
        rup_wings += [(pre, 30, -20), (hit, -10, 5), (back, 28, -18)]
    end = 4.6
    for d, v in ((rup_root, [0, 0, 0]), (rup_body, [0, 0, 0]), (rup_arm_r, [0, 0, 0]), (rup_arm_l, [0, 0, 0]),
                 (rup_ol, [0, 0, 0]), (rup_os, [0, 0, 0]), (rup_halo, [1, 1, 1])):
        d[end] = v
    rup_wings.append((end, 0, 0))
    rup = {
        "root": {"position": rup_root},
        "body": {"rotation": rup_body},
        "head": {"rotation": K(0, [0, 0, 0], 0.8, [16, 0, 0], 4.3, [22, 0, 0], end, [0, 0, 0])},
        "arm_r": {"rotation": rup_arm_r},
        "arm_l": {"rotation": rup_arm_l},
        "orb_light": {"position": rup_ol},
        "orb_shadow": {"position": rup_os},
        "halo": {"rotation": K(0, [0, 0, 0], end, [0, 0, 1440]), "scale": rup_halo},
    }
    rup.update(herald_wings(rup_wings))
    a.add("rupture", end, False, rup)
    # v0.7 - Sentenca do Crepusculo (40 ticks): as duas esferas se fundem a frente do peito e sao lancadas
    # em t18 (0,9 s) com um empurrao das duas maos.
    verd = {
        "body": {"rotation": K(0, [0, 0, 0], 0.7, [-8, 0, 0], 0.9, [14, 0, 0], 1.3, [8, 0, 0], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.7, [6, 0, 0], 0.9, [-6, 0, 0], 2.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-80, 24, 0], 0.85, [-75, 20, 0], 0.95, [-105, 0, 0], 1.4, [-95, 0, 0], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.5, [-80, -24, 0], 0.85, [-75, -20, 0], 0.95, [-105, 0, 0], 1.4, [-95, 0, 0], 2.0, [0, 0, 0])},
        "orb_light": {"position": K(0, [0, 0, 0], 0.5, [-8, 10, -6], 0.85, [-11.5, 12, -8], 0.95, [-11.5, 12, -16], 1.6, [-11.5, 12, -16],
                                    2.0, [0, 0, 0]),
                      "scale": K(0, [1, 1, 1], 0.85, [1.6, 1.6, 1.6], 0.95, [0, 0, 0], 1.6, [0, 0, 0], 2.0, [1, 1, 1])},
        "orb_shadow": {"position": K(0, [0, 0, 0], 0.5, [8, 10, -6], 0.85, [11.5, 12, -8], 0.95, [11.5, 12, -16], 1.6, [11.5, 12, -16],
                                     2.0, [0, 0, 0]),
                       "scale": K(0, [1, 1, 1], 0.85, [1.6, 1.6, 1.6], 0.95, [0, 0, 0], 1.6, [0, 0, 0], 2.0, [1, 1, 1])},
        "halo": {"scale": K(0, [1, 1, 1], 0.85, [1.5, 1.5, 1.5], 0.95, [2.2, 2.2, 2.2], 1.4, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 2.0, [0, 0, 720])},
    }
    verd.update(herald_wings([(0, 0, 0), (0.8, 20, -15), (0.95, -15, 10), (1.4, 5, 0), (2.0, 0, 0)]))
    a.add("verdict", 2.0, False, verd)
    # v0.7 - Pousado e exausto (laco do controlador principal).
    rest = {
        "body": {"rotation": wave(4.0, lambda p: [20 + sinv(p, 2.5), 0, 0])},
        "head": {"rotation": wave(4.0, lambda p: [24 + sinv(p, 3), sinv(p, 5), 0])},
        "robe": {"rotation": wave(4.0, lambda p: [8, 0, sinv(p, 1.5)])},
        "arm_r": {"rotation": wave(4.0, lambda p: [12 + sinv(p, 2), 0, 10])},
        "arm_l": {"rotation": wave(4.0, lambda p: [12 + sinv(p, 2), 0, -10])},
        "orb_light": {"position": wave(4.0, lambda p: [0, -6 + sinv(p, 0.6), 0])},
        "orb_shadow": {"position": wave(4.0, lambda p: [0, -6 - sinv(p, 0.6), 0])},
        "halo": {"rotation": wave(4.0, lambda p: [0, 0, 90 * p], 6), "scale": wave(4.0, lambda p: [0.8 + sinv(p, 0.05)] * 3)},
    }
    rest.update(herald_wings([(0, -30, 25), (2.0, -26, 21), (4.0, -30, 25)]))
    a.add("rest", 4.0, True, rest)
    a.save()


# =====================================================================================
#  PROJETEIS
# =====================================================================================
def holed_shell(base, rim, seed):
    """Casca escura com furos (o nucleo brilhante aparece por dentro)."""
    def p(fs, c, ctx):
        rnd = random.Random(seed)
        for f in fs.values():
            for yy in range(f.h):
                for xx in range(f.w):
                    edge = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                    if not edge and rnd.random() < 0.45:
                        f.set(xx, yy, CLEAR)
                    else:
                        f.set(xx, yy, C(rim) if edge and (xx + yy) % 2 == 0 else C(base), (xx + yy) % 3 == 0)
    return p


def build_bolts():
    # Orbe abissal: nucleo violeta com casca furada girando e tres espinhos
    m = Model("abyssal_orb")
    m.bone("root", [0, 0, 0])
    m.bone("core", [0, 5.5, 0], "root")
    m.cube("core", [-2, 3.5, -2], [4, 4, 4], orb_paint("#b56cff", "#f6e0ff", "#8a3fe0"))
    m.bone("shell", [0, 5.5, 0], "root")
    m.cube("shell", [-3, 2.5, -3], [6, 6, 6], holed_shell("#1b0f2a", "#6a35b0", 601))
    spike = glow_all("#d9a8ff")
    m.cube("shell", [-4.5, 5, -0.5], [9, 1, 1], spike)
    m.cube("shell", [-0.5, 1, -0.5], [1, 9, 1], spike)
    m.cube("shell", [-0.5, 5, -4.5], [1, 1, 9], spike)
    m.layout()
    m.paint()
    m.save_geo((2, 2, 0.3))
    m.save_texture()
    a = Anims("abyssal_orb")
    a.add("spin", 1.0, True, {
        "shell": {"rotation": wave(1.0, lambda p: [360 * p, 720 * p, 0], 8)},
        "core": {"scale": wave(1.0, lambda p: [1 + sinv(p, 0.15, mult=2)] * 3)},
    })
    a.anims = {"animation.boss_bolt.spin": a.anims["animation.abyssal_orb.spin"]}
    a.save()

    # Lanca celestial: ponta dupla (fica certa nos dois sentidos), toda de luz
    m = Model("celestial_lance")
    m.bone("root", [0, 0, 0])
    m.bone("lance", [0, 4.8, 0], "root")
    m.cube("lance", [-0.5, 4.3, -9], [1, 1, 18], glow_all("#fff6c8"))
    head = orb_paint("#ffe27a", "#ffffff", "#ffc93c")
    m.cube("lance", [-1, 3.8, -13], [2, 2, 4], head)
    m.cube("lance", [-1, 3.8, 9], [2, 2, 4], head)
    m.cube("lance", [-0.5, 4.3, -15], [1, 1, 2], glow_all("#ffffff"))
    m.cube("lance", [-0.5, 4.3, 13], [1, 1, 2], glow_all("#ffffff"))
    ring = glow_all("#ffd75a")
    m.cube("lance", [-1.5, 3.3, -9.5], [3, 3, 1], ring)
    m.cube("lance", [-1.5, 3.3, 8.5], [3, 3, 1], ring)
    m.layout()
    m.paint()
    m.save_geo((3, 2, 0.3))
    m.save_texture()
    a = Anims("celestial_lance")
    a.add("spin", 0.5, True, {"lance": {"rotation": wave(0.5, lambda p: [0, 0, 360 * p], 8)}})
    a.anims = {"animation.boss_bolt.spin": a.anims["animation.celestial_lance.spin"]}
    a.save()

    # Orbe do crepusculo: metade luz, metade sombra, com aneis cruzados
    m = Model("twilight_orb")
    m.bone("root", [0, 0, 0])
    m.bone("core", [0, 6.4, 0], "root")

    def split_core(fs, c, ctx):
        for f in fs.values():
            for yy in range(f.h):
                for xx in range(f.w):
                    side = f.side_of(xx)
                    edge = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                    col = ("#ffe9a0" if not edge else "#ffc93c") if side > 0 else ("#b07aff" if not edge else "#5a2a9a")
                    f.set(xx, yy, C(col), True)
    m.cube("core", [-2.5, 4, -2.5], [5, 5, 5], split_core)
    m.bone("rings", [0, 6.4, 0], "root")
    m.cube("rings", [-4.5, 6, -0.5], [9, 1, 1], glow_all("#ffe27a"))
    m.cube("rings", [-0.5, 6, -4.5], [1, 1, 9], glow_all("#c58cff"))
    m.cube("rings", [-0.5, 2, -0.5], [1, 9, 1], glow_all("#f0d0ff"))
    m.layout()
    m.paint()
    m.save_geo((2, 2, 0.4))
    m.save_texture()
    a = Anims("twilight_orb")
    a.add("spin", 1.2, True, {
        "rings": {"rotation": wave(1.2, lambda p: [0, 360 * p, 360 * p], 8)},
        "core": {"rotation": wave(1.2, lambda p: [0, -360 * p, 0], 8)},
    })
    a.anims = {"animation.boss_bolt.spin": a.anims["animation.twilight_orb.spin"]}
    a.save()


# =====================================================================================
#  ITENS
# =====================================================================================
def build_items():
    # Coracao do Crepusculo: metade dourada, metade violeta, com uma fenda de luz no meio
    rows = [
        "................",
        "...oooo..oooo...",
        "..oLLLLo oDDDDo.",
        ".oLWLLLLoDDDdDDo",
        ".oLWLLLLgDDDDdDo",
        ".oLLLLLLgDDDDDDo",
        ".oLLLLLLgDDDDDDo",
        "..oLLLLLgDDDDDo.",
        "...oLLLLgDDDDo..",
        "....oLLLgDDDo...",
        ".....oLLgDDo....",
        "......oLgDo.....",
        ".......ogo......",
        "........o.......",
        "................",
        "................",
    ]
    pal = {"o": C("#2a1438"), "L": C("#ffd75a"), "W": C("#fff6c8"), "D": C("#7a3fd0"), "d": C("#c58cff"),
           "g": C("#fff0b0")}
    cv = Canvas(16, 16)
    cv.from_rows([r.replace(" ", ".") for r in rows], pal)
    # Sombreado: luz mais clara em cima, violeta mais escuro embaixo
    for y in range(16):
        for x in range(16):
            p = cv.get(x, y)
            if p[3] == 0 or p == pal["o"] or p == pal["g"]:
                continue
            cv.set(x, y, darken(p, 0.25 * max(0, (y - 5) / 7)))
    cv.save(os.path.join(ASSETS, "textures/item/twilight_heart.png"))
    item_model(ASSETS, "twilight_heart")
    for egg in ("abyss_sovereign_spawn_egg", "seraph_spawn_egg", "twilight_herald_spawn_egg"):
        write_json(os.path.join(ASSETS, "models/item", egg + ".json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  TROFEU (bloco)
# =====================================================================================
def build_trophy():
    # Base: pedra escura com filetes dourado e violeta
    base = Canvas(16, 16)
    base.rect(0, 0, 15, 15, C("#2c2536"))
    base.noise(0.08, 701)
    for x in range(16):
        base.set(x, 0, C("#e0b54a") if x < 8 else C("#7a3fd0"))
        base.set(x, 15, C("#16111c"))
        base.set(x, 1, darken(C("#e0b54a"), 0.3) if x < 8 else darken(C("#7a3fd0"), 0.3))
    for y in range(4, 13, 4):
        for x in range(1, 15):
            if (x + y) % 5 == 0:
                base.set(x, y, C("#4a3d5e"))
    base.set(7, 8, C("#ffe27a"))
    base.set(8, 8, C("#c58cff"))
    base.save(os.path.join(ASSETS, "textures/block/twilight_herald_trophy_base.png"))

    # Estatueta (atlas 16x16): regioes descritas em 'uv' abaixo
    st = Canvas(16, 16)
    L, LG, D, DV = C("#eee6d4"), C("#e0b54a"), C("#241a32"), C("#7a3fd0")

    def split_rect(x0, y0, x1, y1, left_light):
        for y in range(y0, y1):
            for x in range(x0, x1):
                light = (x - x0 < (x1 - x0) / 2) == left_light
                st.set(x, y, darken(L if light else D, 0.12 * (y - y0) / max(1, y1 - y0 - 1)))
    split_rect(0, 0, 4, 7, False)      # frente do manto: esquerda = lado direito (sombra)
    split_rect(4, 0, 8, 7, True)       # costas
    st.rect(8, 0, 11, 6, D)            # lado da sombra
    st.rect(12, 0, 15, 6, L)           # lado da luz
    # Cinto: dourado do lado da luz, violeta do lado da sombra
    for x in range(0, 15):
        light = (2 <= x < 4) or (4 <= x < 6) or x >= 12
        st.set(x, 2, LG if light else DV)
    split_rect(0, 7, 3, 10, False)     # rosto
    st.set(0, 8, C("#c58cff"))
    st.set(2, 8, C("#ffe27a"))
    split_rect(3, 7, 6, 10, True)      # nuca
    st.rect(6, 7, 8, 9, D)
    st.rect(9, 7, 11, 9, L)
    split_rect(12, 7, 16, 11, True)    # topo
    for y in range(10, 15):            # asa de penas
        for x in range(0, 5):
            st.set(x, y, C("#fbf8ef") if x % 2 else C("#ddd3bd"))
        st.set(4, y, LG)
    for y in range(10, 15):            # asa de membrana
        for x in range(5, 10):
            st.set(x, y, C("#2a1840") if (x + y) % 3 else C("#120a1c"))
    for x in range(10, 16):            # aureola
        for y in range(11, 16):
            st.set(x, y, C("#ffd75a") if x < 13 else C("#9a55f0"))
    st.save(os.path.join(ASSETS, "textures/block/twilight_herald_trophy_statue.png"))

    def box(frm, to, tex, uvs):
        faces = {}
        for face, uv in uvs.items():
            faces[face] = {"uv": uv, "texture": tex}
        return {"from": frm, "to": to, "faces": faces}

    full = [0, 0, 16, 16]
    pedestal = {f: full for f in ("north", "south", "east", "west", "up", "down")}
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "isekai_mahou:block/twilight_herald_trophy_base",
            "base": "isekai_mahou:block/twilight_herald_trophy_base",
            "statue": "isekai_mahou:block/twilight_herald_trophy_statue",
        },
        "elements": [
            box([2, 0, 2], [14, 3, 14], "#base", {"north": [2, 0, 14, 3], "south": [2, 0, 14, 3], "east": [2, 0, 14, 3],
                                                  "west": [2, 0, 14, 3], "up": [2, 2, 14, 14], "down": [2, 2, 14, 14]}),
            box([3, 3, 3], [13, 4, 13], "#base", {"north": [3, 0, 13, 1], "south": [3, 0, 13, 1], "east": [3, 0, 13, 1],
                                                  "west": [3, 0, 13, 1], "up": [3, 3, 13, 13]}),
            box([6, 4, 6], [10, 11, 10], "#statue", {"north": [0, 0, 4, 7], "south": [4, 0, 8, 7], "east": [8, 0, 11, 7],
                                                     "west": [12, 0, 15, 7], "up": [12, 7, 16, 11]}),
            box([6.5, 11, 6.5], [9.5, 14, 9.5], "#statue", {"north": [0, 7, 3, 10], "south": [3, 7, 6, 10], "east": [6, 7, 9, 10],
                                                            "west": [9, 7, 12, 10], "up": [12, 7, 15, 10]}),
            box([1.5, 7, 8], [6, 12, 9], "#statue", {"north": [0, 10, 4.5, 15], "south": [0, 10, 4.5, 15], "west": [0, 10, 1, 15],
                                                     "up": [0, 10, 4.5, 11], "down": [0, 14, 4.5, 15]}),
            box([10, 7, 8], [14.5, 12, 9], "#statue", {"north": [5, 10, 9.5, 15], "south": [5, 10, 9.5, 15], "east": [5, 10, 6, 15],
                                                       "up": [5, 10, 9.5, 11], "down": [5, 14, 9.5, 15]}),
            box([5.5, 11, 9.5], [10.5, 15.5, 10], "#statue", {"north": [10, 11, 15, 15.5], "south": [10, 11, 15, 15.5]}),
        ],
    }
    write_json(os.path.join(ASSETS, "models/block/twilight_herald_trophy.json"), model)
    write_json(os.path.join(ASSETS, "models/item/twilight_herald_trophy.json"), {"parent": "isekai_mahou:block/twilight_herald_trophy"})
    write_json(os.path.join(ASSETS, "blockstates/twilight_herald_trophy.json"), {"variants": {
        "facing=north": {"model": "isekai_mahou:block/twilight_herald_trophy"},
        "facing=east": {"model": "isekai_mahou:block/twilight_herald_trophy", "y": 90},
        "facing=south": {"model": "isekai_mahou:block/twilight_herald_trophy", "y": 180},
        "facing=west": {"model": "isekai_mahou:block/twilight_herald_trophy", "y": 270},
    }})


# =====================================================================================
#  DADOS: loot tables e tags (merge)
# =====================================================================================
def count_fn(lo, hi, looting=True):
    fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                    "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    return fns


def item_pool(name, lo=1, hi=1, looting=False, conditions=None):
    entry = {"type": "minecraft:item", "name": name}
    if lo != 1 or hi != 1:
        entry["functions"] = count_fn(lo, hi, looting)
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if conditions:
        pool["conditions"] = conditions
    return pool


def scroll_pool(glyphs, chance=None):
    entries = []
    for g, w in glyphs:
        entries.append({"type": "minecraft:item", "name": "isekai_mahou:glyph_scroll", "weight": w,
                        "functions": [{"function": "minecraft:set_components",
                                       "components": {"isekai_mahou:glyph": "isekai_mahou:" + g}}]})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": entries}
    conds = [{"condition": "minecraft:killed_by_player"}]
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance", "chance": chance})
    pool["conditions"] = conds
    return pool


def build_data():
    lt = os.path.join(DATA, "loot_table")
    write_json(os.path.join(lt, "entities/abyss_sovereign.json"), {
        "type": "minecraft:entity",
        "pools": [
            item_pool("isekai_mahou:guardian_core_shadow"),
            item_pool("isekai_mahou:essence_shadow", 4, 8, True),
            item_pool("isekai_mahou:mana_crystal", 4, 8, True),
            item_pool("minecraft:crying_obsidian", 2, 5, True),
            scroll_pool([("devour", 3), ("nightmare", 3), ("pain_link", 2), ("steal_blessing", 2)]),
            scroll_pool([("possession", 1)], 0.5),
        ],
        "random_sequence": "isekai_mahou:entities/abyss_sovereign",
    })
    write_json(os.path.join(lt, "entities/seraph.json"), {
        "type": "minecraft:entity",
        "pools": [
            item_pool("isekai_mahou:guardian_core_light"),
            item_pool("isekai_mahou:essence_light", 4, 8, True),
            item_pool("isekai_mahou:mana_crystal", 4, 8, True),
            item_pool("minecraft:gold_ingot", 4, 9, True),
            scroll_pool([("sanctuary", 3), ("prism", 3), ("exorcise", 2), ("sacred_barrier", 2)]),
            scroll_pool([("resurrection", 1)], 0.5),
        ],
        "random_sequence": "isekai_mahou:entities/seraph",
    })
    write_json(os.path.join(lt, "entities/twilight_herald.json"), {
        "type": "minecraft:entity",
        "pools": [
            item_pool("isekai_mahou:twilight_heart"),
            item_pool("isekai_mahou:twilight_herald_trophy"),
            item_pool("isekai_mahou:essence_light", 6, 10, True),
            item_pool("isekai_mahou:essence_shadow", 6, 10, True),
            item_pool("isekai_mahou:essence_arcane", 4, 6, True),
            item_pool("isekai_mahou:mana_crystal", 10, 16, True),
            item_pool("minecraft:nether_star"),
            scroll_pool([("law_absolute", 1)]),
        ],
        "random_sequence": "isekai_mahou:entities/twilight_herald",
    })
    write_json(os.path.join(lt, "blocks/twilight_herald_trophy.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "isekai_mahou:twilight_herald_trophy"}]}],
        "random_sequence": "isekai_mahou:blocks/twilight_herald_trophy",
    })
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"values": [
        "isekai_mahou:abyss_sovereign", "isekai_mahou:seraph", "isekai_mahou:twilight_herald"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/entity_type/redirectable_projectile.json"), {"values": [
        "isekai_mahou:abyssal_orb", "isekai_mahou:twilight_orb"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [
        "isekai_mahou:twilight_herald_trophy"]})


if __name__ == "__main__":
    if "anims" in sys.argv[1:]:
        # So as animacoes (nao reescreve modelos, texturas nem dados).
        anims_abyss()
        anims_seraph()
        anims_herald()
        print("bosses_b: animacoes geradas")
        sys.exit(0)
    build_abyss()
    anims_abyss()
    build_seraph()
    anims_seraph()
    build_herald()
    anims_herald()
    build_bolts()
    build_items()
    build_trophy()
    build_data()
    print("bosses_b: assets gerados")
