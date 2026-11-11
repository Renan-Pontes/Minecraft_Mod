"""
Gera TODOS os assets do modulo creatures_d (rode da raiz do projeto: py -I tools/gen/creatures_d.py):
  - modelos GeckoLib (.geo.json), animacoes (.animation.json), texturas 64x64/128x128 + glowmask
    dos 14 elementais/criaturas arcanas
  - modelos dos ovos de spawn
  - loot tables (essencia do elemento + drops tematicos)
  - biome modifiers (neoforge:add_spawns)
  - tags compartilhadas no merge do modulo
  - traducoes pt_br / en_us

As texturas usam UV de caixa (layout Bedrock) empacotado automaticamente: cada cubo recebe o seu
proprio retangulo e e pintado com cor base, gradiente, padrao (escamas, pelo, pedra, penas...) e
sombreamento por face. Detalhes (olhos, runas, rachaduras) sao pintados por cima, face a face.
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
MERGE = os.path.join(ROOT, "src/main/merge/creatures_d")
LANG = os.path.join(ROOT, "src/main/lang/creatures_d")
NS = "isekai_mahou"
TAU = 2 * math.pi


def H(h, a=255):
    return hex_rgba(h, a)


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  UV de caixa (Bedrock): retangulo (x, y, w, h) de cada face
#  "front" = face norte (-Z) = frente da criatura; "right" = lado -X; "left" = lado +X
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


SIDES = ("front", "back", "left", "right")
FACE_SHADE = {"top": 0.14, "front": 0.0, "left": -0.07, "right": -0.07, "back": -0.12, "bottom": -0.22}


def shade(col, face):
    f = FACE_SHADE[face]
    return lighten(col, f) if f > 0 else darken(col, -f)


def pattern_px(col, pattern, i, j, w, h, rnd, face):
    if pattern is None:
        return col
    r = rnd.random()
    if pattern == "scales":
        if j % 2 == 0 and (i + (j // 2)) % 2 == 0:
            return darken(col, 0.13)
        if j % 2 == 1 and (i + (j // 2)) % 2 == 1 and r < 0.5:
            return lighten(col, 0.08)
        return col
    if pattern == "fur":
        if r < 0.22:
            return darken(col, 0.12)
        if r < 0.32:
            return lighten(col, 0.08)
        return col
    if pattern == "stone":
        if r < 0.16:
            return darken(col, 0.18)
        if r < 0.28:
            return lighten(col, 0.1)
        return col
    if pattern == "feather":
        if j % 3 == 2:
            return darken(col, 0.18)
        if j % 3 == 0 and i % 2 == 0:
            return lighten(col, 0.1)
        return col
    if pattern == "smoke":
        a = col[3]
        a = int(a * (0.65 + 0.35 * r))
        return (col[0], col[1], col[2], a)
    if pattern == "water":
        wave = math.sin((i + j * 0.7) * 1.3)
        if wave > 0.75:
            return lighten(col, 0.22)
        if wave < -0.85:
            return darken(col, 0.1)
        return col
    if pattern == "sparkle":
        if r < 0.08:
            return lighten(col, 0.5)
        return col
    return col


class Model:
    """Monta bones/cubos, empacota as UVs e pinta a textura ao mesmo tempo."""

    def __init__(self, ident, tw=64, th=128):
        self.id = ident
        self.tw = tw
        self.th_max = th
        self.c = Canvas(tw, th)
        self.g = Canvas(tw, th)
        self.bones = []
        self.bmap = {}
        self.cx = 0
        self.cy = 0
        self.shelf = 0
        self.maxy = 0
        self.has_glow = False
        self.rnd = random.Random(sum(ord(ch) * (k + 1) for k, ch in enumerate(ident)))

    def bone(self, name, pivot, parent=None, rot=None):
        b = {"name": name, "pivot": [float(x) for x in pivot]}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = [float(x) for x in rot]
        b["cubes"] = []
        self.bones.append(b)
        self.bmap[name] = b
        return name

    def alloc(self, w, h, d):
        bw = max(1, 2 * (d + w))
        bh = max(1, d + h)
        if self.cx + bw > self.tw:
            self.cx = 0
            self.cy += self.shelf + 1
            self.shelf = 0
        u, v = self.cx, self.cy
        self.cx += bw + 1
        self.shelf = max(self.shelf, bh)
        self.maxy = max(self.maxy, v + bh)
        if self.maxy > self.th_max:
            raise SystemExit(f"{self.id}: textura estourou ({self.maxy} > {self.th_max})")
        return u, v

    def cube(self, bone, origin, size, col, inflate=0.0, col2=None, belly=None, top=None, pattern=None,
             alpha=None, fade=None, glow=False, edge=None, glow_alpha=None):
        w, h, d = size
        u, v = self.alloc(w, h, d)
        cube = {"origin": [float(x) for x in origin], "size": [float(x) for x in size], "uv": [u, v]}
        if inflate:
            cube["inflate"] = inflate
        self.bmap[bone]["cubes"].append(cube)
        f = box_faces(u, v, w, h, d)
        if glow:
            self.has_glow = True
        for face, (x, y, fw, fh) in f.items():
            if fw <= 0 or fh <= 0:
                continue
            for j in range(fh):
                for i in range(fw):
                    t = j / max(1, fh - 1)
                    base = col
                    if col2 is not None and face in SIDES:
                        base = mix(col, col2, t)
                    if top is not None and face == "top":
                        base = top
                    if belly is not None:
                        if face == "bottom":
                            base = belly
                        elif face in SIDES and fh >= 3 and j >= fh - max(1, fh // 3):
                            base = mix(base, belly, 0.75)
                    base = pattern_px(base, pattern, i, j, fw, fh, self.rnd, face)
                    base = shade(base, face)
                    if edge is not None and face in SIDES and (i == 0 or i == fw - 1):
                        base = mix(base, edge, 0.55)
                    a = base[3] if alpha is None else int(alpha * base[3] / 255) if pattern == "smoke" else alpha
                    if fade and face in SIDES:
                        a = int(a * (1 - fade * t))
                    px = (base[0], base[1], base[2], max(0, min(255, a)))
                    self.c.set(x + i, y + j, px)
                    if glow:
                        ga = px[3] if glow_alpha is None else glow_alpha
                        self.g.set(x + i, y + j, (px[0], px[1], px[2], ga))
        return f

    def px(self, rect, i, j, col, glow=False):
        x, y, w, h = rect
        if 0 <= i < w and 0 <= j < h:
            self.c.set(x + i, y + j, col)
            if glow:
                self.g.set(x + i, y + j, col)
                self.has_glow = True

    def hline(self, rect, j, col, glow=False, i0=0, i1=None):
        x, y, w, h = rect
        for i in range(i0, w if i1 is None else i1):
            self.px(rect, i, j, col, glow)

    def vline(self, rect, i, col, glow=False, j0=0, j1=None):
        x, y, w, h = rect
        for j in range(j0, h if j1 is None else j1):
            self.px(rect, i, j, col, glow)

    def speckle(self, rect, col, chance, glow=False, seed=0):
        x, y, w, h = rect
        r = random.Random(seed + x * 31 + y * 17)
        for j in range(h):
            for i in range(w):
                if r.random() < chance:
                    self.px(rect, i, j, col, glow)

    def save(self, visible=(2, 2, 0.5)):
        th = 16
        while th < self.maxy:
            th *= 2
        self.c.h = th
        self.c.px = self.c.px[:th]
        self.g.h = th
        self.g.px = self.g.px[:th]
        bones = []
        for b in self.bones:
            b = dict(b)
            if not b["cubes"]:
                del b["cubes"]
            bones.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.id, "texture_width": self.tw, "texture_height": th,
                            "visible_bounds_width": visible[0], "visible_bounds_height": visible[1],
                            "visible_bounds_offset": [0, visible[2], 0]},
            "bones": bones}]}
        save_json(os.path.join(A, "geo/entity", self.id + ".geo.json"), geo)
        self.c.save(os.path.join(A, "textures/entity", self.id + ".png"))
        gpath = os.path.join(A, "textures/entity", self.id + "_glowmask.png")
        if self.has_glow:
            self.g.save(gpath)
        elif os.path.exists(gpath):
            os.remove(gpath)


# =====================================================================================
#  Animacoes
# =====================================================================================
def r3(v):
    return [round(x, 3) for x in v]


def sampled(length, steps, fn):
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        out[str(round(t, 4))] = r3(fn(t))
    return out


def wave(L, amp, cycles=1.0, phase=0.0, base=0.0):
    return lambda t: base + amp * math.sin(TAU * cycles * t / L + phase)


def rotx(L, steps, f):
    return {"rotation": sampled(L, steps, lambda t: [f(t), 0, 0])}


def roty(L, steps, f):
    return {"rotation": sampled(L, steps, lambda t: [0, f(t), 0])}


def rotz(L, steps, f):
    return {"rotation": sampled(L, steps, lambda t: [0, 0, f(t)])}


def keys(*pairs):
    """keys((0, [x,y,z]), (0.2, [...]), ...) -> dict de keyframes."""
    return {str(round(t, 4)): r3(v) for t, v in pairs}


def anim(L, bones, loop=True):
    return {"loop": loop, "animation_length": L, "bones": bones}


def save_anims(ident, anims):
    save_json(os.path.join(A, "animations/entity", ident + ".animation.json"), {
        "format_version": "1.8.0",
        "animations": {f"animation.{ident}.{k}": v for k, v in anims.items()}})


def merge_bones(*dicts):
    out = {}
    for d in dicts:
        for k, v in d.items():
            out.setdefault(k, {}).update(v)
    return out


# =====================================================================================
#  1. Salamandra (fogo)
# =====================================================================================
def salamander():
    m = Model("salamander")
    body = H("D9542B"); back = H("8E2A12"); belly = H("FFB347"); glow = H("FFD84A"); claw = H("3A1A0E")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 4.5, 0], "root")
    f = m.cube("body", [-3.5, 2, -6], [7, 5, 12], back, col2=body, belly=belly, top=back, pattern="scales")
    # manchas brilhantes nas costas (brasas sob a pele)
    for (i, j) in ((1, 2), (5, 3), (2, 7), (4, 9), (1, 10), (5, 6)):
        m.px(f["top"], i, j, glow, True)
        m.px(f["top"], i, j + 1, H("FF8A2B"), True)
    for side in ("left", "right"):
        for (i, j) in ((2, 1), (6, 2), (9, 1)):
            m.px(f[side], i, j, H("FF8A2B"), True)
    m.bone("crest", [0, 7, 0], "body")
    for (o, s) in (([-0.5, 7, -5], [1, 2, 3]), ([-0.5, 7, -1], [1, 2, 3]), ([-0.5, 7, 3], [1, 1, 3])):
        m.cube("crest", o, s, H("FFB13B"), col2=H("FF5A1F"), glow=True)
    m.bone("head", [0, 5, -6], "body")
    f = m.cube("head", [-3, 2.5, -11], [6, 4, 5], back, col2=body, belly=belly, top=back, pattern="scales")
    s = m.cube("head", [-2, 2.5, -14], [4, 3, 3], body, belly=belly, top=back)
    # boca e narinas
    m.hline(s["front"], 2, H("5A160A"))
    m.px(s["front"], 1, 0, H("3A0A04")); m.px(s["front"], 2, 0, H("3A0A04"))
    for side in ("left", "right"):
        m.hline(s[side], 2, H("5A160A"))
        m.hline(f[side], 3, H("5A160A"), i0=0, i1=3)
    for x in (-3.5, 1.5):
        e = m.cube("head", [x, 5.5, -10], [2, 2, 2], H("FFE27A"), glow=True)
        for face in ("front", "left", "right"):
            m.px(e[face], 1 if face != "right" else 0, 0, H("2A1000"), True)
            m.px(e[face], 1 if face != "right" else 0, 1, H("2A1000"), True)
    m.bone("tail1", [0, 4.5, 6], "body")
    m.cube("tail1", [-2.5, 3, 6], [5, 3, 7], back, col2=body, belly=belly, top=back, pattern="scales")
    m.bone("tail2", [0, 4.5, 13], "tail1")
    m.cube("tail2", [-1.5, 3.5, 13], [3, 2, 6], back, col2=body, belly=belly, top=back, pattern="scales")
    m.bone("tail3", [0, 4.5, 19], "tail2")
    m.cube("tail3", [-1, 3.5, 19], [2, 2, 5], body, col2=H("FF7A2B"), top=back)
    m.bone("tail_flame", [0, 4.5, 24], "tail3")
    m.cube("tail_flame", [-1.5, 3, 23.5], [3, 3, 3], H("FFE27A"), col2=H("FF5A1F"), glow=True)
    for name, x, z in (("leg_fl", 3.5, -4), ("leg_fr", -3.5, -4), ("leg_bl", 3.5, 4), ("leg_br", -3.5, 4)):
        m.bone(name, [x, 4, z], "body")
        ox = 3.5 if x > 0 else -6.5
        f = m.cube(name, [ox, 0, z - 1.5], [3, 4, 3], back, col2=body, pattern="scales")
        for side in SIDES:
            m.hline(f[side], f[side][3] - 1, claw)
    m.save((2, 1.2, 0.3))

    L = 2.0
    idle = anim(L, {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.15 * math.sin(TAU * t / L), 0])},
        "head": {"rotation": sampled(L, 8, lambda t: [3 * math.sin(TAU * t / L), 6 * math.sin(TAU * t / L + 1), 0])},
        "tail1": roty(L, 8, wave(L, 8)), "tail2": roty(L, 8, wave(L, 10, phase=-0.9)),
        "tail3": roty(L, 8, wave(L, 14, phase=-1.8)),
        "tail_flame": {"scale": sampled(L, 8, lambda t: [1 + 0.15 * math.sin(TAU * 2 * t / L)] * 3)},
    })
    W = 0.8
    walk = anim(W, {
        "leg_fl": rotx(W, 8, wave(W, 32)), "leg_br": rotx(W, 8, wave(W, 32)),
        "leg_fr": rotx(W, 8, wave(W, -32)), "leg_bl": rotx(W, 8, wave(W, -32)),
        "body": {"rotation": sampled(W, 8, lambda t: [0, 6 * math.sin(TAU * t / W), 0])},
        "head": roty(W, 8, wave(W, -5)),
        "tail1": roty(W, 8, wave(W, -12)), "tail2": roty(W, 8, wave(W, -12, phase=-0.8)),
        "tail3": roty(W, 8, wave(W, -16, phase=-1.6)),
        "tail_flame": {"scale": sampled(W, 4, lambda t: [1 + 0.2 * math.sin(TAU * 2 * t / W)] * 3)},
    })
    attack = anim(0.5, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.12, [-18, 0, 0]), (0.22, [14, 0, 0]), (0.5, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.22, [0, 0, -2.5]), (0.5, [0, 0, 0]))},
        "body": {"position": keys((0, [0, 0, 0]), (0.22, [0, 0, -1]), (0.5, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.7, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-28, 0, 0]), (0.45, [12, 0, 0]), (0.7, [0, 0, 0]))},
        "crest": {"scale": keys((0, [1, 1, 1]), (0.3, [1.3, 1.5, 1.1]), (0.5, [1, 1, 1]))},
        "tail_flame": {"scale": keys((0, [1, 1, 1]), (0.3, [1.6, 1.6, 1.6]), (0.7, [1, 1, 1]))},
    }, loop=False)
    save_anims("salamander", {"idle": idle, "walk": walk, "attack": attack, "cast": cast})


# =====================================================================================
#  2. Diabrete de Brasa (fogo, voa)
# =====================================================================================
def ember_imp():
    m = Model("ember_imp")
    skin = H("B3261E"); dark = H("5A0F0B"); horn = H("2B1B17"); ember = H("FFB13B"); memb = H("7A1A12")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    f = m.cube("body", [-2.5, 4, -1.5], [5, 6, 3], skin, col2=dark, pattern="fur")
    # rachaduras de brasa no peito
    for (i, j) in ((2, 1), (1, 2), (2, 2), (3, 3), (2, 4), (1, 4)):
        m.px(f["front"], i, j, ember, True)
    m.px(f["front"], 2, 3, H("FFE27A"), True)
    m.bone("head", [0, 10, 0], "body")
    f = m.cube("head", [-3, 10, -2.5], [6, 5, 5], skin, col2=dark, pattern="fur")
    fr = f["front"]
    for (i, j) in ((1, 1), (4, 1)):
        m.px(fr, i, j, H("FFE27A"), True)
        m.px(fr, i + (1 if i < 3 else -1), j, H("FF8A2B"), True)
    m.hline(fr, 3, H("2A0504"), i0=1, i1=5)
    m.px(fr, 1, 4, H("FFF2E0")); m.px(fr, 4, 4, H("FFF2E0"))  # presinhas
    m.hline(fr, 0, darken(dark, 0.3))
    for x in (-3, 2):
        m.cube("head", [x, 15, -1], [1, 3, 1], horn, col2=H("6B4A3A"))
    for x in (-4, 3):
        m.cube("head", [x, 12, -0.5], [1, 2, 1], skin)
    m.bone("right_arm", [-2.5, 9.5, 0], "body")
    a = m.cube("right_arm", [-4.5, 5, -1], [2, 5, 2], skin, col2=dark)
    m.bone("left_arm", [2.5, 9.5, 0], "body")
    b = m.cube("left_arm", [2.5, 5, -1], [2, 5, 2], skin, col2=dark)
    for r in (a, b):
        for side in SIDES:
            m.hline(r[side], r[side][3] - 1, horn)
    m.bone("right_leg", [-1.25, 4, 0], "body")
    m.cube("right_leg", [-2.5, 0, -1], [2, 4, 2], dark, col2=horn)
    m.bone("left_leg", [1.25, 4, 0], "body")
    m.cube("left_leg", [0.5, 0, -1], [2, 4, 2], dark, col2=horn)

    def wing(bone, origin):
        w = m.cube(bone, origin, [9, 8, 0], memb, alpha=235)
        for face in ("front", "back"):
            x, y, fw, fh = w[face]
            # recorta a membrana em forma de asa de morcego (bordas em arco)
            for j in range(fh):
                for i in range(fw):
                    ii = i if (face == "front") == (origin[0] > 0) else fw - 1 - i
                    lim = fh - 1 - int(2.2 * abs(math.sin(ii * 0.75)))
                    if j > lim or (j == 0 and ii > 6):
                        m.c.set(x + i, y + j, (0, 0, 0, 0))
                    elif ii in (0, 3, 6) or j == 0:
                        m.c.set(x + i, y + j, horn)
                    elif j == lim:
                        m.c.set(x + i, y + j, H("FF5A1F"))
                        m.g.set(x + i, y + j, H("FF5A1F", 200))
                        m.has_glow = True
    m.bone("left_wing", [1, 9, 1.5], "body")
    wing("left_wing", [1, 3, 1.5])
    m.bone("right_wing", [-1, 9, 1.5], "body")
    wing("right_wing", [-10, 3, 1.5])
    m.bone("tail", [0, 4.5, 1.5], "body")
    m.cube("tail", [-0.5, 4, 1.5], [1, 1, 6], dark)
    m.bone("tail_tip", [0, 4.5, 7.5], "tail")
    m.cube("tail_tip", [-1, 3.5, 7.5], [2, 2, 2], H("FFE27A"), col2=H("FF5A1F"), glow=True)
    m.save((1.6, 1.4, 0.5))

    L = 0.5
    idle = anim(L, {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.5 * math.sin(TAU * t / L), 0])},
        "left_wing": roty(L, 8, wave(L, 38, base=-10)), "right_wing": roty(L, 8, wave(L, -38, base=10)),
        "tail": rotx(L, 8, wave(L, 10, phase=1)),
        "right_arm": rotz(L, 8, wave(L, 6, base=8)), "left_arm": rotz(L, 8, wave(L, -6, base=-8)),
        "right_leg": rotx(L, 8, wave(L, 8, base=10)), "left_leg": rotx(L, 8, wave(L, -8, base=10)),
    })
    F = 0.32
    fly = anim(F, {
        "body": {"rotation": sampled(F, 4, lambda t: [16, 0, 0]),
                 "position": sampled(F, 8, lambda t: [0, 0.4 * math.sin(TAU * t / F), 0])},
        "left_wing": roty(F, 8, wave(F, 48, base=-10)), "right_wing": roty(F, 8, wave(F, -48, base=10)),
        "tail": rotx(F, 8, wave(F, 14, base=-15)),
        "right_leg": rotx(F, 4, lambda t: 35), "left_leg": rotx(F, 4, lambda t: 30),
        "right_arm": rotx(F, 4, lambda t: 25), "left_arm": rotx(F, 4, lambda t: 25),
    })
    attack = anim(0.45, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-150, 0, 10]), (0.3, [30, 0, -10]), (0.45, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-120, 0, -10]), (0.35, [25, 0, 10]), (0.45, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [12, 0, 0]), (0.45, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.6, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-170, 0, 15]), (0.4, [-70, 0, 0]), (0.6, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-170, 0, -15]), (0.4, [-70, 0, 0]), (0.6, [0, 0, 0]))},
        "tail_tip": {"scale": keys((0, [1, 1, 1]), (0.25, [1.7, 1.7, 1.7]), (0.6, [1, 1, 1]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-15, 0, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    save_anims("ember_imp", {"idle": idle, "fly": fly, "attack": attack, "cast": cast})


# =====================================================================================
#  3. Ondina (agua, nada) - translucida
# =====================================================================================
def undine():
    m = Model("undine")
    skin = H("8FDDF2", 220); dress = H("2E8FD6", 235); dress2 = H("6FD0F0", 225)
    hair = H("1F5FBF", 240); hair2 = H("3FA9FF", 225); fin = H("BFF4FF", 160); pearl = H("F4F4FF")
    m.bone("root", [0, 12, 0])
    m.bone("body", [0, 14, 0], "root")
    f = m.cube("body", [-3, 14, -1.5], [6, 8, 3], dress, col2=dress2, pattern="water")
    # pele do colo e conchas
    for j in range(0, 3):
        m.hline(f["front"], j, skin, i0=1, i1=5)
    m.px(f["front"], 1, 3, pearl, True); m.px(f["front"], 4, 3, pearl, True)
    m.bone("head", [0, 22, 0], "body")
    f = m.cube("head", [-3, 22, -3], [6, 6, 6], skin, col2=lighten(skin, 0.1))
    fr = f["front"]
    for i in (1, 4):
        m.px(fr, i, 3, H("E8FFFF"), True)
        m.px(fr, i, 2, H("7FE8FF"), True)
    m.px(fr, 2, 5, H("4FA8C8")); m.px(fr, 3, 5, H("4FA8C8"))
    hr = m.cube("head", [-3, 22, -3], [6, 6, 6], hair, inflate=0.4, col2=hair2, pattern="water")
    # cabelo: franja em cima, rosto livre
    x, y, w, h = hr["front"]
    for j in range(2, h):
        for i in range(1, w - 1):
            m.c.set(x + i, y + j, (0, 0, 0, 0))
    for side in ("left", "right"):
        x, y, w, h = hr[side]
        for j in range(h):
            for i in range(w):
                if j >= h - 1 and i < 2:
                    m.c.set(x + i, y + j, (0, 0, 0, 0))
    t = m.cube("head", [-2.5, 28.2, -3.4], [5, 1, 1], pearl, glow=True)
    m.px(t["front"], 2, 0, H("7FE8FF"), True)
    m.bone("hair_back", [0, 27, 3], "head")
    m.cube("hair_back", [-3, 15, 2.6], [6, 12, 1], hair, col2=H("5FC8FF", 150), pattern="water", fade=0.4)
    m.bone("right_arm", [-3, 21, 0], "body")
    m.cube("right_arm", [-5, 13, -1], [2, 8, 2], skin, col2=lighten(skin, 0.15))
    m.bone("left_arm", [3, 21, 0], "body")
    m.cube("left_arm", [3, 13, -1], [2, 8, 2], skin, col2=lighten(skin, 0.15))
    m.bone("tail1", [0, 14, 0], "body")
    m.cube("tail1", [-2.5, 8, -1.5], [5, 6, 3], dress2, col2=H("2E8FD6", 210), pattern="scales")
    m.bone("tail2", [0, 8, 0], "tail1")
    m.cube("tail2", [-2, 4, -1], [4, 4, 2], H("2E8FD6", 210), col2=H("7FE8FF", 180), pattern="scales")
    m.bone("fin", [0, 4, 0], "tail2")
    fn = m.cube("fin", [-4, 0, 0], [8, 4, 0], fin, glow=True, glow_alpha=110)
    for face in ("front", "back"):
        x, y, w, h = fn[face]
        for j in range(h):
            for i in range(w):
                # entalhe no meio da barbatana e cantos de cima arredondados
                if i in (3, 4) and j == h - 1:
                    m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))
                elif j == 0 and i in (0, 7):
                    m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))
    m.save((1.6, 2.2, 0.9))

    L = 2.5
    idle = anim(L, {
        "root": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(TAU * t / L), 0])},
        "tail1": rotx(L, 8, wave(L, 7)), "tail2": rotx(L, 8, wave(L, 11, phase=-0.9)),
        "fin": rotx(L, 8, wave(L, 15, phase=-1.8)),
        "hair_back": rotx(L, 8, wave(L, 5, base=10, phase=-0.5)),
        "right_arm": rotz(L, 8, wave(L, 8, base=12)), "left_arm": rotz(L, 8, wave(L, -8, base=-12)),
        "head": {"rotation": sampled(L, 8, lambda t: [0, 0, 4 * math.sin(TAU * t / L + 1)])},
    })
    S = 1.2
    swim = anim(S, {
        "root": {"rotation": sampled(S, 4, lambda t: [75, 0, 0])},
        "head": rotx(S, 4, lambda t: -55),
        "tail1": rotx(S, 8, wave(S, 15)), "tail2": rotx(S, 8, wave(S, 22, phase=-0.8)),
        "fin": rotx(S, 8, wave(S, 28, phase=-1.6)),
        "hair_back": rotx(S, 8, wave(S, 6, base=35)),
        "right_arm": {"rotation": sampled(S, 8, lambda t: [8 + 25 * math.sin(TAU * t / S), 0, 10])},
        "left_arm": {"rotation": sampled(S, 8, lambda t: [8 + 25 * math.sin(TAU * t / S), 0, -10])},
    })
    attack = anim(0.5, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-60, 0, 20]), (0.28, [-95, 0, -5]), (0.5, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-60, 0, -20]), (0.28, [-95, 0, 5]), (0.5, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.28, [10, 0, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.8, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.35, [-165, 0, 25]), (0.55, [-75, 0, 0]), (0.8, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.35, [-165, 0, -25]), (0.55, [-75, 0, 0]), (0.8, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [-12, 0, 0]), (0.8, [0, 0, 0]))},
        "hair_back": {"rotation": keys((0, [10, 0, 0]), (0.4, [35, 0, 0]), (0.8, [10, 0, 0]))},
    }, loop=False)
    special = anim(1.0, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 80]), (0.8, [0, 0, 80]), (1.0, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -80]), (0.8, [0, 0, -80]), (1.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-22, 0, 0]), (0.8, [-22, 0, 0]), (1.0, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.4, [0, 1.5, 0]), (1.0, [0, 0, 0]))},
    }, loop=False)
    save_anims("undine", {"idle": idle, "swim": swim, "attack": attack, "cast": cast, "special": special})


# =====================================================================================
#  4. Serpente da Mare (agua, nada)
# =====================================================================================
def tide_serpent():
    m = Model("tide_serpent")
    back = H("1F5F73"); side = H("2E8C99"); belly = H("A6E3D2"); fin = H("45C4D4", 210); spot = H("7FF5E6")
    m.bone("root", [0, 5, 0])
    m.bone("seg2", [0, 5, 0], "root")
    f = m.cube("seg2", [-2.5, 2.5, 0], [5, 5, 8], back, col2=side, belly=belly, top=back, pattern="scales")
    for s in ("left", "right"):
        for i in (1, 4, 7):
            m.px(f[s], i, 2, spot, True)
    m.bone("seg1", [0, 5, 0], "seg2")
    f = m.cube("seg1", [-3, 2, -8], [6, 6, 8], back, col2=side, belly=belly, top=back, pattern="scales")
    for s in ("left", "right"):
        for i in (1, 4, 7):
            m.px(f[s], i, 2, spot, True)
    m.bone("head", [0, 5, -8], "seg1")
    f = m.cube("head", [-3.5, 3, -17], [7, 5, 9], back, col2=side, top=back, pattern="scales")
    fr = f["front"]
    m.hline(fr, 4, H("0E2A33"))
    for s in ("left", "right"):
        x, y, w, h = f[s]
        ei = 2 if s == "left" else w - 3
        m.px(f[s], ei, 1, H("C8FFF4"), True); m.px(f[s], ei + 1, 1, H("C8FFF4"), True)
        m.px(f[s], ei, 2, H("2FD8C0"), True)
        m.hline(f[s], 4, H("0E2A33"))
    m.px(f["top"], 2, 1, H("0E2A33")); m.px(f["top"], 4, 1, H("0E2A33"))
    m.bone("jaw", [0, 3, -9], "head")
    j = m.cube("jaw", [-3, 1, -16], [6, 2, 8], side, belly=belly, pattern="scales")
    for i in range(0, 6, 2):
        m.px(j["front"], i, 0, H("F4FFFA"))
    for s in ("left", "right"):
        for i in range(0, 8, 2):
            m.px(j[s], i, 0, H("F4FFFA"))
    for nm, x, ry in (("left_frill", 3.5, -20), ("right_frill", -3.5, 20)):
        m.bone(nm, [x, 6, -10], "head", rot=[0, ry, 0])
        fr2 = m.cube(nm, [x, 3, -12], [0, 7, 5], fin, glow=True, glow_alpha=90)
        for s in ("left", "right"):
            m.vline(fr2[s], 1, H("1F7F8C")); m.vline(fr2[s], 3, H("1F7F8C"))
    m.bone("seg3", [0, 5, 8], "seg2")
    f = m.cube("seg3", [-2, 3, 8], [4, 4, 8], back, col2=side, belly=belly, top=back, pattern="scales")
    for s in ("left", "right"):
        for i in (2, 6):
            m.px(f[s], i, 1, spot, True)
    m.bone("seg4", [0, 5, 16], "seg3")
    m.cube("seg4", [-1.5, 3.5, 16], [3, 3, 7], back, col2=side, belly=belly, top=back, pattern="scales")
    m.bone("tail_fin", [0, 5, 23], "seg4")
    tf = m.cube("tail_fin", [0, 0, 22], [0, 10, 7], fin, glow=True, glow_alpha=90)
    for s in ("left", "right"):
        x, y, w, h = tf[s]
        for jj in range(h):
            for ii in range(w):
                # cauda em forma de leque (corta os cantos de dentro)
                if ii < 2 and abs(jj - 4.5) > 1.5 + ii * 1.5:
                    m.c.set(x + ii, y + jj, (0, 0, 0, 0)); m.g.set(x + ii, y + jj, (0, 0, 0, 0))
        m.hline(tf[s], 4, H("1F7F8C")); m.hline(tf[s], 5, H("1F7F8C"))
    for bone, o, sz in (("seg1", [0, 8, -7], [0, 3, 6]), ("seg2", [0, 7.5, 1], [0, 3, 6]), ("seg3", [0, 7, 9], [0, 2, 6])):
        d = m.cube(bone, o, sz, fin, glow=True, glow_alpha=80)
        for s in ("left", "right"):
            m.vline(d[s], 1, H("1F7F8C")); m.vline(d[s], 4, H("1F7F8C"))
    m.cube("seg1", [3, 2.5, -6], [4, 0, 3], fin)
    m.cube("seg1", [-7, 2.5, -6], [4, 0, 3], fin)
    m.save((3, 1.5, 0.4))

    def body_wave(L, A, ph_step=0.9, cycles=1.0):
        return {
            "head": {"rotation": sampled(L, 12, lambda t: [0, 0.5 * A * math.sin(TAU * cycles * t / L + 2 * ph_step), 0])},
            "seg1": roty(L, 12, wave(L, A, cycles, ph_step)),
            "seg2": {"rotation": sampled(L, 12, lambda t: [2 * math.sin(TAU * cycles * t / L), 0.4 * A * math.sin(TAU * cycles * t / L), 0])},
            "seg3": roty(L, 12, wave(L, A, cycles, -ph_step)),
            "seg4": roty(L, 12, wave(L, A * 1.2, cycles, -2 * ph_step)),
            "tail_fin": roty(L, 12, wave(L, A * 1.4, cycles, -3 * ph_step)),
        }
    idle = anim(3.0, body_wave(3.0, 7))
    idle["bones"]["left_frill"] = roty(3.0, 6, wave(3.0, 6))
    idle["bones"]["right_frill"] = roty(3.0, 6, wave(3.0, -6))
    swim = anim(1.2, body_wave(1.2, 18))
    attack = anim(0.5, {
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.12, [38, 0, 0]), (0.28, [0, 0, 0]), (0.5, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.12, [-14, 0, 0]), (0.28, [6, 0, 0]), (0.5, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.25, [0, 0, -3]), (0.5, [0, 0, 0]))},
        "seg1": {"rotation": keys((0, [0, 0, 0]), (0.25, [-8, 0, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.9, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-22, 0, 0]), (0.7, [-10, 0, 0]), (0.9, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.35, [32, 0, 0]), (0.7, [32, 0, 0]), (0.9, [0, 0, 0]))},
        "left_frill": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -35, 0]), (0.7, [0, -35, 0]), (0.9, [0, 0, 0]))},
        "right_frill": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 35, 0]), (0.7, [0, 35, 0]), (0.9, [0, 0, 0]))},
        "seg1": {"rotation": keys((0, [0, 0, 0]), (0.3, [-12, 0, 0]), (0.9, [0, 0, 0]))},
    }, loop=False)
    save_anims("tide_serpent", {"idle": idle, "swim": swim, "attack": attack, "cast": cast})


# =====================================================================================
#  5. Golem de Musgo (terra)
# =====================================================================================
def moss_golem():
    m = Model("moss_golem", tw=128)
    stone = H("7A7F74"); stone2 = H("555A50"); moss = H("4E8A35"); moss_l = H("6FB04A"); moss_d = H("335F22")
    eye = H("C6FF7A"); pink = H("E07BB5"); yellow = H("FFD84A"); vine = H("3F7A2A")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 14, 0], "root")
    f = m.cube("body", [-7, 14, -4], [14, 12, 8], stone, col2=stone2, pattern="stone")
    for s in SIDES:
        m.speckle(f[s], moss_d, 0.12, seed=3)
        m.speckle(f[s], moss_l, 0.06, seed=9)
    # rachadura brilhante no peito (seiva viva)
    for (i, j) in ((6, 3), (7, 4), (6, 5), (7, 6), (8, 7), (7, 8)):
        m.px(f["front"], i, j, H("9FE860"), True)
    mc = m.cube("body", [-7.5, 22, -4.5], [15, 5, 9], moss, col2=moss_d, top=moss_l, pattern="fur")
    for s in SIDES:
        x, y, w, h = mc[s]
        r = random.Random(x + y)
        for i in range(w):
            cut = r.randint(1, 3)
            for jj in range(h - cut, h):
                if jj > 1:
                    m.c.set(x + i, y + jj, (0, 0, 0, 0))
    m.bone("head", [0, 26, -1], "body")
    f = m.cube("head", [-4, 26, -5], [8, 7, 7], stone, col2=stone2, pattern="stone")
    fr = f["front"]
    for i in (1, 2, 5, 6):
        m.px(fr, i, 3, eye, True)
    m.px(fr, 1, 2, darken(stone2, 0.3)); m.px(fr, 6, 2, darken(stone2, 0.3))
    m.hline(fr, 5, darken(stone2, 0.35), i0=2, i1=6)
    m.vline(fr, 4, darken(stone2, 0.2), j0=0, j1=2)
    hat = m.cube("head", [-4.5, 31, -5.5], [9, 3, 8], moss, col2=moss_d, top=moss_l, pattern="fur")
    for s in SIDES:
        x, y, w, h = hat[s]
        for i in range(w):
            if (i * 7) % 3 == 0:
                m.c.set(x + i, y + h - 1, (0, 0, 0, 0))
    m.cube("head", [1, 34, -3], [2, 2, 2], pink, top=lighten(pink, 0.3))
    m.cube("head", [-3, 34, -1], [2, 1, 2], yellow, top=lighten(yellow, 0.3))
    for nm, x, sx, ax in (("right_arm", -7, -11.5, -11), ("left_arm", 7, 6.5, 7)):
        m.bone(nm, [x, 24, 0], "body")
        a = m.cube(nm, [ax, 8, -2.5], [4, 17, 5], stone, col2=stone2, pattern="stone")
        for s in SIDES:
            m.speckle(a[s], moss_d, 0.1, seed=int(abs(x)) + 1)
            for i in range(a[s][2]):
                m.px(a[s], i, a[s][3] - 1, darken(stone2, 0.3))
        sh = m.cube(nm, [sx, 21, -3], [5, 5, 6], moss, col2=moss_d, top=moss_l, pattern="fur")
        for s in SIDES:
            x2, y2, w2, h2 = sh[s]
            for i in range(w2):
                if i % 2 == 0:
                    m.c.set(x2 + i, y2 + h2 - 1, (0, 0, 0, 0))
        vn = m.cube(nm, [ax + 0.5, 12, -2.6], [3, 8, 0], vine, alpha=255)
        for face in ("front", "back"):
            x2, y2, w2, h2 = vn[face]
            for jj in range(h2):
                for i in range(w2):
                    if (i + jj) % 3 == 1 or (i == 1 and jj > 5):
                        m.c.set(x2 + i, y2 + jj, (0, 0, 0, 0))
                    elif jj % 3 == 0:
                        m.c.set(x2 + i, y2 + jj, moss_l)
    for nm, x, lx in (("right_leg", -3.5, -6), ("left_leg", 3.5, 1)):
        m.bone(nm, [x, 14, 0], "root")
        lg = m.cube(nm, [lx, 0, -2.5], [5, 14, 5], stone, col2=stone2, pattern="stone")
        for s in SIDES:
            m.speckle(lg[s], moss_d, 0.1, seed=int(abs(x)) + 5)
            m.hline(lg[s], lg[s][3] - 1, darken(stone2, 0.3))
    vn = m.cube("body", [-5, 9, -4.1], [4, 6, 0], vine)
    for face in ("front", "back"):
        x2, y2, w2, h2 = vn[face]
        for jj in range(h2):
            for i in range(w2):
                if (i + jj * 2) % 3 == 0:
                    m.c.set(x2 + i, y2 + jj, (0, 0, 0, 0))
    m.save((2, 3, 1.1))

    L = 3.0
    idle = anim(L, {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.3 * math.sin(TAU * t / L), 0])},
        "right_arm": rotx(L, 8, wave(L, 3)), "left_arm": rotx(L, 8, wave(L, -3)),
        "head": {"rotation": sampled(L, 8, lambda t: [2 * math.sin(TAU * t / L), 5 * math.sin(TAU * t / L + 1.2), 0])},
    })
    W = 1.6
    walk = anim(W, {
        "right_leg": rotx(W, 8, wave(W, 24)), "left_leg": rotx(W, 8, wave(W, -24)),
        "right_arm": rotx(W, 8, wave(W, -20)), "left_arm": rotx(W, 8, wave(W, 20)),
        "body": {"rotation": sampled(W, 8, lambda t: [0, 0, 3 * math.sin(TAU * t / W)]),
                 "position": sampled(W, 8, lambda t: [0, 0.5 * abs(math.sin(TAU * t / W)), 0])},
    })
    attack = anim(0.7, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-115, 0, 10]), (0.45, [15, 0, 0]), (0.7, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 15, 0]), (0.45, [6, -12, 0]), (0.7, [0, 0, 0]))},
    }, loop=False)
    slam = anim(1.0, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-170, 0, -10]), (0.6, [-20, 0, 0]), (1.0, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-170, 0, 10]), (0.6, [-20, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [-12, 0, 0]), (0.6, [16, 0, 0]), (1.0, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.6, [0, -1.5, 0]), (1.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.45, [-15, 0, 0]), (0.6, [10, 0, 0]), (1.0, [0, 0, 0]))},
    }, loop=False)
    save_anims("moss_golem", {"idle": idle, "walk": walk, "attack": attack, "slam": slam})


# =====================================================================================
#  6. Toupeira de Cristal (terra)
# =====================================================================================
def crystal_mole():
    m = Model("crystal_mole")
    fur = H("4A3326"); fur2 = H("6B4A36"); pink = H("E8A0A8"); claw = H("E4DCCB")
    cr = H("9B6FD6"); cr2 = H("D6B8FF")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 4, 0], "root")
    m.cube("body", [-4, 1, -5], [8, 6, 11], fur2, col2=fur, top=fur, belly=fur2, pattern="fur")
    for nm, piv, rot, o, s in (("crystal_a", [-2, 7, -2], [-10, 0, 15], [-3, 7, -3], [2, 5, 2]),
                               ("crystal_b", [1, 7, 0], [5, 0, -12], [0, 7, -1], [2, 6, 2]),
                               ("crystal_c", [-1, 7, 3], [15, 0, 8], [-2, 7, 2], [2, 4, 2]),
                               ("crystal_d", [2.5, 7, 3], [10, 0, -20], [2, 7, 2.5], [1, 3, 1])):
        m.bone(nm, piv, "body", rot=rot)
        c = m.cube(nm, o, s, cr, col2=cr2, top=H("F2E6FF"))
        for side in SIDES:
            x, y, w, h = c[side]
            for j in range(min(2, h)):
                for i in range(w):
                    col = H("F2E6FF") if j == 0 else cr2
                    m.px(c[side], i, j, col, True)
        m.px(c["top"], 0, 0, H("FFFFFF"), True)
    m.bone("head", [0, 4, -5], "body")
    f = m.cube("head", [-3, 1.5, -10], [6, 5, 5], fur2, col2=fur, pattern="fur")
    m.px(f["front"], 1, 2, H("100A06")); m.px(f["front"], 4, 2, H("100A06"))
    m.px(f["front"], 1, 1, H("8A6A5A")); m.px(f["front"], 4, 1, H("8A6A5A"))
    m.cube("head", [-1.5, 1.5, -12], [3, 3, 2], fur2, col2=pink)
    n = m.cube("head", [-1, 3.5, -13], [2, 1, 1], pink, top=lighten(pink, 0.3))
    m.px(n["front"], 0, 0, darken(pink, 0.4)); m.px(n["front"], 1, 0, darken(pink, 0.4))
    for nm, x, hx in (("hand_l", 4, 4), ("hand_r", -4, -7)):
        m.bone(nm, [x, 3, -3], "body")
        m.cube(nm, [hx, 0, -6], [3, 2, 4], pink, col2=darken(pink, 0.15))
        m.cube(nm, [hx, 0, -7], [3, 1, 1], claw, top=lighten(claw, 0.2))
    for nm, x, fx in (("foot_l", 3, 2.5), ("foot_r", -3, -4.5)):
        m.bone(nm, [x, 2, 4], "body")
        m.cube(nm, [fx, 0, 3], [2, 2, 3], pink, col2=darken(pink, 0.15))
    m.bone("tail", [0, 3, 6], "body")
    m.cube("tail", [-0.5, 2.5, 6], [1, 1, 3], pink)
    m.save((1.4, 1.2, 0.4))

    L = 2.0
    idle = anim(L, {
        "head": {"rotation": sampled(L, 16, lambda t: [3 * math.sin(TAU * 4 * t / L), 8 * math.sin(TAU * t / L), 0])},
        "body": {"position": sampled(L, 8, lambda t: [0, 0.12 * math.sin(TAU * t / L), 0])},
        "tail": roty(L, 8, wave(L, 15, cycles=2)),
        "hand_l": rotx(L, 8, wave(L, 4)), "hand_r": rotx(L, 8, wave(L, -4)),
    })
    W = 0.6
    walk = anim(W, {
        "hand_l": rotx(W, 8, wave(W, 32)), "hand_r": rotx(W, 8, wave(W, -32)),
        "foot_l": rotx(W, 8, wave(W, -25)), "foot_r": rotx(W, 8, wave(W, 25)),
        "body": {"rotation": sampled(W, 8, lambda t: [0, 0, 4 * math.sin(TAU * t / W)])},
        "head": roty(W, 8, wave(W, -4)),
    })
    attack = anim(0.5, {
        "hand_r": {"rotation": keys((0, [0, 0, 0]), (0.15, [-75, 20, 0]), (0.28, [25, -25, 0]), (0.5, [0, 0, 0]))},
        "hand_l": {"rotation": keys((0, [0, 0, 0]), (0.2, [-60, -20, 0]), (0.33, [20, 25, 0]), (0.5, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-10, 8, 0]), (0.3, [5, -8, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    crystal_scale = keys((0, [1, 1, 1]), (0.3, [1.25, 1.35, 1.25]), (0.45, [0.9, 0.9, 0.9]), (0.7, [1, 1, 1]))
    cast = anim(0.7, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-14, 0, 0]), (0.45, [8, 0, 0]), (0.7, [0, 0, 0]))},
        "crystal_a": {"scale": crystal_scale}, "crystal_b": {"scale": crystal_scale},
        "crystal_c": {"scale": crystal_scale}, "crystal_d": {"scale": crystal_scale},
    }, loop=False)
    burrow = anim(0.75, {
        "root": {"position": keys((0, [0, 0, 0]), (0.2, [0, 0, 0]), (0.75, [0, -9, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [28, 0, 0]), (0.75, [28, 0, 0]))},
        "hand_l": {"rotation": sampled(0.75, 10, lambda t: [-40 * abs(math.sin(TAU * 3 * t / 0.75)), 0, 0])},
        "hand_r": {"rotation": sampled(0.75, 10, lambda t: [-40 * abs(math.cos(TAU * 3 * t / 0.75)), 0, 0])},
    }, loop=False)
    emerge = anim(0.6, {
        "root": {"position": keys((0, [0, -9, 0]), (0.35, [0, 1, 0]), (0.6, [0, 0, 0]))},
        "body": {"rotation": keys((0, [-30, 0, 0]), (0.35, [-12, 0, 0]), (0.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 20, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    save_anims("crystal_mole", {"idle": idle, "walk": walk, "attack": attack, "cast": cast,
                                "burrow": burrow, "emerge": emerge})


# =====================================================================================
#  7. Silfide (vento, voa) - translucida
# =====================================================================================
def sylph():
    m = Model("sylph")
    skin = H("D8FFF0", 225); dress = H("9FF0D0", 220); dress2 = H("E8FFF8", 200)
    hair = H("F2FFFB", 235); hair2 = H("B6F5DE", 220); wing = H("E8FFFA", 120); wedge = H("9FF0D0", 210)
    m.bone("root", [0, 10, 0])
    m.bone("body", [0, 14, 0], "root")
    f = m.cube("body", [-2.5, 12, -1.5], [5, 7, 3], dress, col2=dress2, pattern="water")
    m.hline(f["front"], 0, skin, i0=1, i1=4)
    m.px(f["front"], 2, 2, H("2FB89A"), True)
    m.bone("head", [0, 19, 0], "body")
    f = m.cube("head", [-2.5, 19, -2.5], [5, 5, 5], skin, col2=lighten(skin, 0.1))
    m.px(f["front"], 1, 2, H("2FB89A"), True); m.px(f["front"], 3, 2, H("2FB89A"), True)
    m.px(f["front"], 1, 1, H("C8FFF0"), True); m.px(f["front"], 3, 1, H("C8FFF0"), True)
    m.px(f["front"], 2, 4, H("8FCDB8"))
    hr = m.cube("head", [-2.5, 19, -2.5], [5, 5, 5], hair, inflate=0.4, col2=hair2, pattern="water")
    x, y, w, h = hr["front"]
    for j in range(2, h):
        for i in range(1, w - 1):
            m.c.set(x + i, y + j, (0, 0, 0, 0))
    m.bone("hair_tail", [0, 23, 2.5], "head")
    m.cube("hair_tail", [-1, 15, 2.6], [2, 8, 1], hair, col2=H("B6F5DE", 120), fade=0.5)
    m.bone("right_arm", [-2.5, 18.5, 0], "body")
    m.cube("right_arm", [-4.5, 12, -1], [2, 7, 2], skin)
    m.bone("left_arm", [2.5, 18.5, 0], "body")
    m.cube("left_arm", [2.5, 12, -1], [2, 7, 2], skin)
    m.bone("wisp1", [0, 12, 0], "body")
    m.cube("wisp1", [-2, 7, -1.5], [4, 5, 3], dress2, col2=H("C8F5E6", 150), pattern="smoke", fade=0.3)
    m.bone("wisp2", [0, 7, 0], "wisp1")
    m.cube("wisp2", [-1.5, 3, -1], [3, 4, 2], H("C8F5E6", 160), col2=H("E8FFF8", 100), pattern="smoke", fade=0.4)
    m.bone("wisp3", [0, 3, 0], "wisp2")
    m.cube("wisp3", [-0.5, 0, -0.5], [1, 3, 1], H("E8FFF8", 120), pattern="smoke", fade=0.6)

    def wing_plane(bone, origin, size):
        wp = m.cube(bone, origin, size, wing, glow=True, glow_alpha=70)
        for face in ("front", "back"):
            x, y, w, h = wp[face]
            cy = (h - 1) / 2
            for j in range(h):
                for i in range(w):
                    half = (h / 2) * math.sin(min(1.0, (i + 1) / w) * math.pi * 0.9 + 0.1)
                    if abs(j - cy) > half + 0.3:
                        m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))
                    elif abs(abs(j - cy) - half) < 0.8 or j == round(cy):
                        m.c.set(x + i, y + j, wedge); m.g.set(x + i, y + j, H("9FF0D0", 140))
    m.bone("left_wing", [1, 17, 1.5], "body")
    wing_plane("left_wing", [1, 14, 1.5], [8, 6, 0])
    m.bone("left_wing_low", [1, 14, 1.5], "body")
    wing_plane("left_wing_low", [1, 10, 1.6], [6, 4, 0])
    m.bone("right_wing", [-1, 17, 1.5], "body")
    wing_plane("right_wing", [-9, 14, 1.5], [8, 6, 0])
    m.bone("right_wing_low", [-1, 14, 1.5], "body")
    wing_plane("right_wing_low", [-7, 10, 1.6], [6, 4, 0])
    m.save((1.6, 2, 0.8))

    def flutter(L, amp, cyc):
        return {
            "left_wing": roty(L, 16, wave(L, amp, cyc, base=-15)), "right_wing": roty(L, 16, wave(L, -amp, cyc, base=15)),
            "left_wing_low": roty(L, 16, wave(L, amp * 0.8, cyc, -0.6, base=-10)),
            "right_wing_low": roty(L, 16, wave(L, -amp * 0.8, cyc, -0.6, base=10)),
        }
    L = 1.5
    idle = anim(L, merge_bones(flutter(L, 25, 4), {
        "root": {"position": sampled(L, 8, lambda t: [0, 1.0 * math.sin(TAU * t / L), 0])},
        "wisp1": {"rotation": sampled(L, 8, lambda t: [6 * math.sin(TAU * t / L), 0, 6 * math.cos(TAU * t / L)])},
        "wisp2": {"rotation": sampled(L, 8, lambda t: [9 * math.sin(TAU * t / L - 1), 0, 9 * math.cos(TAU * t / L - 1)])},
        "wisp3": {"rotation": sampled(L, 8, lambda t: [12 * math.sin(TAU * t / L - 2), 0, 12 * math.cos(TAU * t / L - 2)])},
        "hair_tail": rotx(L, 8, wave(L, 8, base=12)),
        "right_arm": rotz(L, 8, wave(L, 6, base=14)), "left_arm": rotz(L, 8, wave(L, -6, base=-14)),
    }))
    F = 1.0
    fly = anim(F, merge_bones(flutter(F, 32, 5), {
        "body": {"rotation": sampled(F, 4, lambda t: [18, 0, 0])},
        "wisp1": rotx(F, 8, wave(F, 6, base=18)), "wisp2": rotx(F, 8, wave(F, 9, phase=-1, base=14)),
        "wisp3": rotx(F, 8, wave(F, 12, phase=-2, base=10)),
        "hair_tail": rotx(F, 8, wave(F, 6, base=40)),
        "right_arm": rotx(F, 4, lambda t: 25), "left_arm": rotx(F, 4, lambda t: 25),
    }))
    attack = anim(0.5, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-120, 0, 30]), (0.3, [20, 0, -20]), (0.5, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 20, 0]), (0.3, [0, -20, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.8, {
        "root": {"rotation": keys((0, [0, 0, 0]), (0.8, [0, 360, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 70]), (0.6, [-90, 0, 10]), (0.8, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -70]), (0.6, [-90, 0, -10]), (0.8, [0, 0, 0]))},
    }, loop=False)
    special = anim(1.2, {
        "root": {"rotation": keys((0, [0, 0, 0]), (1.2, [0, 720, 0])),
                 "position": keys((0, [0, 0, 0]), (0.6, [0, 2, 0]), (1.2, [0, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, 0, 85]), (1.0, [0, 0, 85]), (1.2, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, 0, -85]), (1.0, [0, 0, -85]), (1.2, [0, 0, 0]))},
    }, loop=False)
    save_anims("sylph", {"idle": idle, "fly": fly, "attack": attack, "cast": cast, "special": special})


# =====================================================================================
#  8. Falcao da Tempestade (vento, voa)
# =====================================================================================
def storm_hawk():
    m = Model("storm_hawk")
    back = H("4A5A78"); dark = H("2B3550"); chest = H("DCE4F0"); tip = H("1E2638"); bolt = H("9FE8FF")
    beak = H("E3A72F"); eye = H("8FF4FF")
    m.bone("root", [0, 6, 0])
    m.bone("body", [0, 6, 0], "root")
    f = m.cube("body", [-3, 3, -5], [6, 5, 10], back, col2=dark, belly=chest, top=back, pattern="feather")
    m.cube("body", [-2.5, 3.5, -5.5], [5, 4, 1], chest, pattern="feather")
    for (i, j) in ((2, 2), (3, 3), (2, 4), (3, 5), (2, 6), (3, 7)):
        m.px(f["top"], i, j, bolt, True)
    m.bone("head", [0, 7, -5], "body")
    f = m.cube("head", [-2.5, 6, -9], [5, 5, 5], back, col2=dark, top=dark, pattern="feather")
    for s in ("left", "right"):
        ei = 1 if s == "left" else 3
        m.px(f[s], ei, 1, eye, True); m.px(f[s], ei, 2, H("2FB8E0"), True)
        m.hline(f[s], 3, chest, i0=0, i1=3)
    m.px(f["front"], 0, 1, eye, True); m.px(f["front"], 4, 1, eye, True)
    m.hline(f["front"], 4, chest, i0=1, i1=4)
    m.cube("head", [-1, 7, -11], [2, 2, 2], beak, top=lighten(beak, 0.25))
    m.cube("head", [-1, 6, -11], [2, 1, 1], darken(beak, 0.35))
    cr = m.cube("head", [0, 11, -8], [0, 3, 5], bolt, glow=True)
    for s in ("left", "right"):
        x, y, w, h = cr[s]
        for j in range(h):
            for i in range(w):
                if j < h - 1 - (i % 3):
                    m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))

    def wing(bone, origin, size, is_tip, positive):
        wf = m.cube(bone, origin, size, back if not is_tip else dark, col2=dark if not is_tip else tip,
                    belly=lighten(chest, -0.1), top=back if not is_tip else dark, pattern="feather")
        x, y, w, h = wf["top"]
        for j in range(h):
            for i in range(w):
                ii = i if positive else w - 1 - i
                if is_tip and j >= h - 1 - (ii % 2) and ii > 2:
                    m.c.set(x + i, y + j, (0, 0, 0, 0))
                elif is_tip and (ii + j) % 5 == 0:
                    m.px(wf["top"], i, j, bolt, True)
        if not is_tip:
            for i in range(w):
                ii = i if positive else w - 1 - i
                if ii % 3 == 1:
                    m.px(wf["top"], i, h - 2, H("6F82A8"))
    m.bone("left_wing", [3, 7.5, -2], "body")
    wing("left_wing", [3, 7, -4], [10, 1, 7], False, True)
    m.bone("left_wing_tip", [13, 7.5, -2], "left_wing")
    wing("left_wing_tip", [13, 7, -3], [8, 1, 6], True, True)
    m.bone("right_wing", [-3, 7.5, -2], "body")
    wing("right_wing", [-13, 7, -4], [10, 1, 7], False, False)
    m.bone("right_wing_tip", [-13, 7.5, -2], "right_wing")
    wing("right_wing_tip", [-21, 7, -3], [8, 1, 6], True, False)
    m.bone("tail", [0, 6.5, 5], "body")
    t = m.cube("tail", [-3, 6, 5], [6, 1, 7], dark, col2=tip, top=back, pattern="feather")
    x, y, w, h = t["top"]
    for i in range(w):
        m.px(t["top"], i, h - 1, chest if i % 2 else bolt, i % 2 == 0)
    for nm, lx in (("right_leg", -2.5), ("left_leg", 0.5)):
        m.bone(nm, [lx + 1, 4, 0], "body")
        lg = m.cube(nm, [lx, 0, -1], [2, 4, 2], beak, col2=darken(beak, 0.3))
        for s in SIDES:
            m.hline(lg[s], lg[s][3] - 1, H("2A2A2A"))
    m.save((3, 1.4, 0.4))

    def flap(L, amp, tip_amp, cyc=1.0, base=0.0):
        return {
            "left_wing": rotz(L, 12, wave(L, amp, cyc, base=base)),
            "right_wing": rotz(L, 12, wave(L, -amp, cyc, base=-base)),
            "left_wing_tip": rotz(L, 12, wave(L, tip_amp, cyc, -0.9)),
            "right_wing_tip": rotz(L, 12, wave(L, -tip_amp, cyc, -0.9)),
        }
    L = 1.2
    idle = anim(L, merge_bones(flap(L, 20, 14), {
        "body": {"position": sampled(L, 8, lambda t: [0, -0.6 * math.sin(TAU * t / L), 0])},
        "head": roty(L, 8, wave(L, 10)),
        "tail": rotx(L, 8, wave(L, 4)),
        "right_leg": rotx(L, 4, lambda t: 50), "left_leg": rotx(L, 4, lambda t: 50),
    }))
    F = 0.6
    fly = anim(F, merge_bones(flap(F, 40, 25), {
        "body": {"rotation": sampled(F, 4, lambda t: [6, 0, 0]),
                 "position": sampled(F, 8, lambda t: [0, -0.8 * math.sin(TAU * t / F), 0])},
        "tail": rotx(F, 8, wave(F, 6)),
        "right_leg": rotx(F, 4, lambda t: 70), "left_leg": rotx(F, 4, lambda t: 70),
    }))
    attack = anim(0.6, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [30, 0, 0]), (0.45, [-10, 0, 0]), (0.6, [0, 0, 0]))},
        "right_leg": {"rotation": keys((0, [50, 0, 0]), (0.25, [-85, 0, 0]), (0.45, [-30, 0, 0]), (0.6, [50, 0, 0]))},
        "left_leg": {"rotation": keys((0, [50, 0, 0]), (0.25, [-85, 0, 0]), (0.45, [-30, 0, 0]), (0.6, [50, 0, 0]))},
        "left_wing": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, 30, 30]), (0.45, [0, 0, -30]), (0.6, [0, 0, 0]))},
        "right_wing": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, -30, -30]), (0.45, [0, 0, 30]), (0.6, [0, 0, 0]))},
    }, loop=False)
    cast = anim(1.0, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-35, 0, 0]), (0.75, [-35, 0, 0]), (1.0, [0, 0, 0]))},
        "left_wing": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -25]), (0.75, [0, 0, -25]), (1.0, [0, 0, 0]))},
        "right_wing": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 25]), (0.75, [0, 0, 25]), (1.0, [0, 0, 0]))},
        "left_wing_tip": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -20]), (1.0, [0, 0, 0]))},
        "right_wing_tip": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 20]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (1.0, [0, 0, 0]))},
    }, loop=False)
    save_anims("storm_hawk", {"idle": idle, "fly": fly, "attack": attack, "cast": cast})


# =====================================================================================
#  9. Fada de Luz (luz, voa, pacifica)
# =====================================================================================
def lumen_sprite():
    m = Model("lumen_sprite")
    core = H("FFF6C8"); core2 = H("FFE27A"); wing = H("FFFBE0", 150)
    m.bone("root", [0, 4, 0])
    m.bone("core", [0, 4, 0], "root")
    f = m.cube("core", [-2.5, 1.5, -2.5], [5, 5, 5], core, col2=core2, glow=True)
    fr = f["front"]
    m.px(fr, 1, 2, H("8A5A10"), True); m.px(fr, 3, 2, H("8A5A10"), True)
    m.px(fr, 1, 1, H("FFFFFF"), True); m.px(fr, 3, 1, H("FFFFFF"), True)
    m.px(fr, 2, 3, H("E89A3A"), True)
    m.px(fr, 0, 3, H("FFB0A0"), True); m.px(fr, 4, 3, H("FFB0A0"), True)
    m.cube("core", [-2.5, 1.5, -2.5], [5, 5, 5], H("FFE9A0", 70), inflate=1.2, glow=True, glow_alpha=60)
    m.bone("halo", [0, 8.5, 0], "core")
    h = m.cube("halo", [-2, 8.5, -2], [4, 0, 4], H("FFE27A"), glow=True)
    for face in ("top", "bottom"):
        x, y, w, hh = h[face]
        for j in range(hh):
            for i in range(w):
                if 0 < i < w - 1 and 0 < j < hh - 1:
                    m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))

    def petal(bone, origin, size):
        p = m.cube(bone, origin, size, wing, glow=True, glow_alpha=90)
        for face in ("front", "back"):
            x, y, w, hh = p[face]
            for j in range(hh):
                for i in range(w):
                    if (i in (0, w - 1)) and (j in (0, hh - 1)):
                        m.c.set(x + i, y + j, (0, 0, 0, 0)); m.g.set(x + i, y + j, (0, 0, 0, 0))
                    elif i == j or i == w - 1 - j:
                        m.c.set(x + i, y + j, H("FFE27A", 220)); m.g.set(x + i, y + j, H("FFE27A", 160))
    for nm, piv, o, s in (("left_wing", [2, 5, 1], [2, 3, 1], [5, 4, 0]), ("left_wing2", [2, 3, 1], [2, 0.5, 1], [3, 3, 0]),
                          ("right_wing", [-2, 5, 1], [-7, 3, 1], [5, 4, 0]), ("right_wing2", [-2, 3, 1], [-5, 0.5, 1], [3, 3, 0])):
        m.bone(nm, piv, "core")
        petal(nm, o, s)
    m.save((1, 1, 0.3))

    def wings(L, amp, cyc):
        return {"left_wing": roty(L, 16, wave(L, amp, cyc, base=-10)), "right_wing": roty(L, 16, wave(L, -amp, cyc, base=10)),
                "left_wing2": roty(L, 16, wave(L, amp, cyc, -0.7, base=-5)), "right_wing2": roty(L, 16, wave(L, -amp, cyc, -0.7, base=5))}
    L = 1.6
    idle = anim(L, merge_bones(wings(L, 35, 4), {
        "core": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(TAU * t / L), 0]),
                 "rotation": sampled(L, 8, lambda t: [0, 0, 5 * math.sin(TAU * t / L)])},
        "halo": {"rotation": keys((0, [0, 0, 0]), (L, [0, 360, 0]))},
    }))
    F = 0.8
    fly = anim(F, merge_bones(wings(F, 45, 4), {
        "core": {"rotation": sampled(F, 4, lambda t: [15, 0, 0]),
                 "position": sampled(F, 8, lambda t: [0, 0.5 * math.sin(TAU * t / F), 0])},
        "halo": {"rotation": keys((0, [0, 0, 0]), (F, [0, 360, 0]))},
    }))
    special = anim(1.0, {
        "root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 360, 0])),
                 "position": keys((0, [0, 0, 0]), (0.3, [0, 3, 0]), (0.6, [0, 0.5, 0]), (0.8, [0, 1.5, 0]), (1.0, [0, 0, 0])),
                 "scale": keys((0, [1, 1, 1]), (0.3, [1.25, 1.25, 1.25]), (1.0, [1, 1, 1]))},
    }, loop=False)
    save_anims("lumen_sprite", {"idle": idle, "fly": fly, "special": special})


# =====================================================================================
#  10. Sentinela Solar (luz, flutua)
# =====================================================================================
def solar_sentinel():
    m = Model("solar_sentinel")
    sand = H("D9C08A"); sand2 = H("A88B55"); gold = H("E8B830"); gold_l = H("FFE27A"); ray = H("FFD24A")
    m.bone("root", [0, 12, 0])
    m.bone("core", [0, 13, 0], "root")
    f = m.cube("core", [-4, 9, -4], [8, 8, 8], sand, col2=sand2, top=gold, pattern="stone")
    for s in SIDES:
        m.hline(f[s], 0, gold); m.hline(f[s], 7, darken(gold, 0.2))
        m.vline(f[s], 0, gold); m.vline(f[s], 7, gold)
    fr = f["front"]
    # olho solar
    for j in range(2, 6):
        for i in range(2, 6):
            m.px(fr, i, j, H("FFF8D8"), True)
    for (i, j) in ((3, 3), (4, 3), (3, 4), (4, 4)):
        m.px(fr, i, j, H("FF9A2B"), True)
    m.px(fr, 3, 3, H("7A3A00"), True)
    for (i, j) in ((1, 3), (1, 4), (6, 3), (6, 4), (3, 1), (4, 1), (3, 6), (4, 6)):
        m.px(fr, i, j, gold_l, True)
    for s in ("back", "left", "right"):
        for (i, j) in ((3, 3), (4, 4), (3, 4), (4, 3)):
            m.px(f[s], i, j, gold_l, True)
    m.bone("crown", [0, 17, 0], "core")
    m.cube("crown", [-1, 17, -1], [2, 4, 2], gold, col2=gold_l, glow=True)
    m.cube("crown", [-3, 17, -1], [1, 2, 1], gold)
    m.cube("crown", [2, 17, -1], [1, 2, 1], gold)
    m.bone("ring", [0, 13, 3.5], "root")
    for o, s in (([-1, 18, 3], [2, 4, 1]), ([-1, 4, 3], [2, 4, 1]), ([5, 12, 3], [4, 2, 1]), ([-9, 12, 3], [4, 2, 1])):
        m.cube("ring", o, s, ray, col2=H("FFF2A8"), glow=True)
    m.bone("ring_diag", [0, 13, 3.5], "ring", rot=[0, 0, 45])
    for o, s in (([-1, 18, 3.2], [2, 3, 1]), ([-1, 5, 3.2], [2, 3, 1]), ([5, 12, 3.2], [3, 2, 1]), ([-8, 12, 3.2], [3, 2, 1])):
        m.cube("ring_diag", o, s, gold, col2=ray, glow=True)
    for nm, x, px_ in (("left_plate", 6, 6), ("right_plate", -6, -8)):
        m.bone(nm, [x, 13, 0], "root")
        p = m.cube(nm, [px_, 10, -2], [2, 6, 4], gold, col2=darken(gold, 0.25), pattern="stone")
        for s in SIDES:
            m.vline(p[s], p[s][2] // 2, gold_l, True)
    m.bone("base", [0, 9, 0], "core")
    m.cube("base", [-2, 5, -2], [4, 4, 4], sand2, col2=darken(sand2, 0.2), pattern="stone")
    m.cube("base", [-1, 2, -1], [2, 3, 2], gold, col2=ray, glow=True)
    m.save((1.8, 2, 0.8))

    L = 3.0
    idle = anim(L, {
        "root": {"position": sampled(L, 8, lambda t: [0, 1.0 * math.sin(TAU * t / L), 0])},
        "ring": {"rotation": keys((0, [0, 0, 0]), (L, [0, 0, -360]))},
        "left_plate": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(TAU * t / L + 1), 0])},
        "right_plate": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(TAU * t / L + 2.5), 0])},
        "base": {"rotation": keys((0, [0, 0, 0]), (L, [0, 360, 0]))},
    })
    W = 2.0
    move = anim(W, {
        "root": {"position": sampled(W, 8, lambda t: [0, 0.8 * math.sin(TAU * t / W), 0]),
                 "rotation": sampled(W, 4, lambda t: [8, 0, 0])},
        "ring": {"rotation": keys((0, [0, 0, 0]), (W, [0, 0, -360]))},
        "left_plate": {"position": sampled(W, 4, lambda t: [0, 0, 1])},
        "right_plate": {"position": sampled(W, 4, lambda t: [0, 0, 1])},
        "base": {"rotation": keys((0, [0, 0, 0]), (W, [0, 360, 0]))},
    })
    D_ = 4.0
    dormant = anim(D_, {
        "root": {"position": sampled(D_, 8, lambda t: [0, -6 + 0.2 * math.sin(TAU * t / D_), 0])},
        "ring": {"rotation": sampled(D_, 8, lambda t: [0, 0, 3 * math.sin(TAU * t / D_)])},
        "left_plate": {"position": sampled(D_, 4, lambda t: [1, -7, 0])},
        "right_plate": {"position": sampled(D_, 4, lambda t: [-1, -7, 0])},
        "crown": {"scale": sampled(D_, 4, lambda t: [1, 0.6, 1])},
    })
    cast = anim(1.5, {
        "ring": {"rotation": keys((0, [0, 0, 0]), (1.5, [0, 0, -1080]))},
        "left_plate": {"position": keys((0, [0, 0, 0]), (0.5, [-2, 0, -3]), (1.3, [-2, 0, -3]), (1.5, [0, 0, 0]))},
        "right_plate": {"position": keys((0, [0, 0, 0]), (0.5, [2, 0, -3]), (1.3, [2, 0, -3]), (1.5, [0, 0, 0]))},
        "core": {"scale": keys((0, [1, 1, 1]), (1.2, [1.12, 1.12, 1.12]), (1.35, [0.92, 0.92, 0.92]), (1.5, [1, 1, 1]))},
        "crown": {"scale": keys((0, [1, 1, 1]), (1.2, [1.4, 1.6, 1.4]), (1.5, [1, 1, 1]))},
    }, loop=False)
    attack = anim(0.5, {
        "left_plate": {"position": keys((0, [0, 0, 0]), (0.15, [4, 0, 0]), (0.5, [0, 0, 0]))},
        "right_plate": {"position": keys((0, [0, 0, 0]), (0.15, [-4, 0, 0]), (0.5, [0, 0, 0]))},
        "ring": {"scale": keys((0, [1, 1, 1]), (0.15, [1.35, 1.35, 1.35]), (0.5, [1, 1, 1]))},
    }, loop=False)
    save_anims("solar_sentinel", {"idle": idle, "move": move, "dormant": dormant, "cast": cast, "attack": attack})


# =====================================================================================
#  11. Sombra Rastejante (sombra)
# =====================================================================================
def creeping_shade():
    m = Model("creeping_shade")
    core = H("120A1C"); mid = H("24123A"); rim = H("3B1D5A"); violet = H("8A4FC8"); eye = H("F0D8FF"); mag = H("FF6AD5")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 5, 0], "root")
    f = m.cube("body", [-3.5, 3, -7], [7, 4, 14], mid, col2=core, top=rim, edge=rim, pattern="smoke", alpha=245)
    for (i, j) in ((3, 2), (3, 5), (2, 8), (4, 11)):
        m.px(f["top"], i, j, violet, True)
    fin = m.cube("body", [0, 7, -6], [0, 3, 11], rim, col2=H("24123A", 90), pattern="smoke", alpha=200)
    for s in ("left", "right"):
        x, y, w, h = fin[s]
        for j in range(h):
            for i in range(w):
                if j < h - 1 - ((i * 3) % 4) // 2 - (1 if i % 3 == 0 else 0) and j < 1 + (i % 3):
                    m.c.set(x + i, y + j, (0, 0, 0, 0))
    m.bone("head", [0, 5.5, -7], "body")
    f = m.cube("head", [-3, 3, -12], [6, 5, 5], mid, col2=core, top=rim, pattern="smoke", alpha=250)
    fr = f["front"]
    for (i, j) in ((1, 1), (4, 1)):
        m.px(fr, i, j, eye, True)
    for (i, j) in ((0, 2), (5, 2), (2, 2), (3, 2)):
        m.px(fr, i, j, mag, True)
    for i in range(1, 5):
        m.px(fr, i, 4, H("FFFFFF") if i % 2 else violet, i % 2 == 1)
    for nm, x, z, lx in (("leg_fl", 3, -5, 3), ("leg_fr", -3, -5, -5), ("leg_bl", 3, 5, 3), ("leg_br", -3, 5, -5)):
        m.bone(nm, [x, 5, z], "body")
        lg = m.cube(nm, [lx, 0, z - 1], [2, 5, 2], mid, col2=core, pattern="smoke", alpha=235)
        for s in SIDES:
            m.px(lg[s], 0, lg[s][3] - 1, violet, True)
    m.bone("tail1", [0, 5.5, 7], "body")
    m.cube("tail1", [-1, 4.5, 7], [2, 2, 6], mid, col2=core, pattern="smoke", alpha=230)
    m.bone("tail2", [0, 5.5, 13], "tail1")
    t2 = m.cube("tail2", [-0.5, 5, 13], [1, 1, 7], rim, pattern="smoke", alpha=200)
    m.px(t2["back"], 0, 0, violet, True)
    m.save((2, 1.2, 0.3))

    L = 2.4
    idle = anim(L, {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.2 * math.sin(TAU * t / L), 0])},
        "head": roty(L, 8, wave(L, 10)),
        "tail1": roty(L, 8, wave(L, 15)), "tail2": roty(L, 8, wave(L, 22, phase=-1)),
    })
    W = 0.9
    walk = anim(W, {
        "leg_fl": rotx(W, 8, wave(W, 30)), "leg_br": rotx(W, 8, wave(W, 30)),
        "leg_fr": rotx(W, 8, wave(W, -30)), "leg_bl": rotx(W, 8, wave(W, -30)),
        "body": {"position": sampled(W, 8, lambda t: [0, -0.6 + 0.3 * abs(math.sin(TAU * t / W)), 0])},
        "head": {"rotation": sampled(W, 8, lambda t: [6, 4 * math.sin(TAU * t / W), 0])},
        "tail1": roty(W, 8, wave(W, -14)), "tail2": roty(W, 8, wave(W, -20, phase=-1)),
    })
    attack = anim(0.5, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.12, [-20, 0, 0]), (0.25, [12, 0, 0]), (0.5, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.25, [0, 0, -2]), (0.5, [0, 0, 0]))},
        "leg_fl": {"rotation": keys((0, [0, 0, 0]), (0.2, [-45, 0, 0]), (0.5, [0, 0, 0]))},
        "leg_fr": {"rotation": keys((0, [0, 0, 0]), (0.25, [-45, 0, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    special = anim(0.6, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.4, [10, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_fl": {"rotation": keys((0, [0, 0, 0]), (0.15, [-65, 0, 0]), (0.45, [-20, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_fr": {"rotation": keys((0, [0, 0, 0]), (0.15, [-65, 0, 0]), (0.45, [-20, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_bl": {"rotation": keys((0, [0, 0, 0]), (0.15, [40, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_br": {"rotation": keys((0, [0, 0, 0]), (0.15, [40, 0, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.9, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 0, 0]), (0.7, [-40, 0, 0]), (0.9, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.7, [-15, 0, 0]), (0.9, [0, 0, 0]))},
        "tail1": {"rotation": keys((0, [0, 0, 0]), (0.3, [-35, 0, 0]), (0.9, [0, 0, 0]))},
    }, loop=False)
    save_anims("creeping_shade", {"idle": idle, "walk": walk, "attack": attack, "special": special, "cast": cast})


# =====================================================================================
#  12. Morcego do Vazio (sombra, voa)
# =====================================================================================
def void_bat():
    m = Model("void_bat")
    fur = H("1B1029"); fur2 = H("2A1840"); memb = H("3D1F66", 235); star = H("D9B8FF"); eye = H("FF5CF0")
    m.bone("root", [0, 6, 0])
    m.bone("body", [0, 6, 0], "root")
    m.cube("body", [-2.5, 3, -2], [5, 6, 4], fur2, col2=fur, pattern="fur")
    m.bone("head", [0, 9, 0], "body")
    f = m.cube("head", [-2.5, 9, -2.5], [5, 4, 5], fur2, col2=fur, pattern="fur")
    fr = f["front"]
    m.px(fr, 1, 1, eye, True); m.px(fr, 3, 1, eye, True)
    m.px(fr, 2, 2, H("4A2A6A"))
    m.px(fr, 1, 3, H("F4F0FF")); m.px(fr, 3, 3, H("F4F0FF"))
    for x in (1, -3):
        e = m.cube("head", [x, 13, -1], [2, 3, 1], fur2, col2=fur)
        m.px(e["front"], 0 if x > 0 else 1, 1, H("6A3A9A"))
    for nm, piv, o, s, tip, tp, to, ts in (
            ("left_wing", [2.5, 8, 0], [2.5, 2, 0], [8, 7, 0], "left_wing_tip", [10.5, 8, 0], [10.5, 3, 0], [6, 6, 0]),
            ("right_wing", [-2.5, 8, 0], [-10.5, 2, 0], [8, 7, 0], "right_wing_tip", [-10.5, 8, 0], [-16.5, 3, 0], [6, 6, 0])):
        m.bone(nm, piv, "body")
        w = m.cube(nm, o, s, memb, pattern="sparkle")
        m.bone(tip, tp, nm)
        t = m.cube(tip, to, ts, memb, pattern="sparkle")
        for rect in (w, t):
            for face in ("front", "back"):
                x, y, ww, hh = rect[face]
                r = random.Random(x * 7 + y)
                for j in range(hh):
                    for i in range(ww):
                        if j == 0:
                            m.c.set(x + i, y + j, fur)
                        elif j >= hh - 1 - (i % 3 == 1):
                            m.c.set(x + i, y + j, (0, 0, 0, 0))
                        elif r.random() < 0.07:
                            m.px(rect[face], i, j, star, True)
    m.cube("body", [-1.5, 1, -0.5], [1, 2, 1], fur)
    m.cube("body", [0.5, 1, -0.5], [1, 2, 1], fur)
    m.save((2.4, 1.4, 0.4))

    def flap(L, amp, base_root=0.0):
        return {
            "left_wing": roty(L, 12, wave(L, amp, base=-15)), "right_wing": roty(L, 12, wave(L, -amp, base=15)),
            "left_wing_tip": roty(L, 12, wave(L, amp * 0.7, phase=-0.8)), "right_wing_tip": roty(L, 12, wave(L, -amp * 0.7, phase=-0.8)),
            "root": {"rotation": sampled(L, 4, lambda t: [base_root, 0, 0]),
                     "position": sampled(L, 8, lambda t: [0, 0.6 * math.sin(TAU * t / L), 0])},
        }
    idle = anim(0.6, flap(0.6, 45, 15))
    fly = anim(0.4, flap(0.4, 60, 40))
    attack = anim(0.4, {
        "head": {"position": keys((0, [0, 0, 0]), (0.15, [0, 0, -1.5]), (0.4, [0, 0, 0])),
                 "rotation": keys((0, [0, 0, 0]), (0.15, [15, 0, 0]), (0.4, [0, 0, 0]))},
        "left_wing": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, -65, 0]), (0.4, [0, 0, 0]))},
        "right_wing": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 65, 0]), (0.4, [0, 0, 0]))},
    }, loop=False)
    cast = anim(0.7, {
        "left_wing": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 85, 0]), (0.5, [0, -40, 0]), (0.7, [0, 0, 0]))},
        "right_wing": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -85, 0]), (0.5, [0, 40, 0]), (0.7, [0, 0, 0]))},
        "left_wing_tip": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 60, 0]), (0.7, [0, 0, 0]))},
        "right_wing_tip": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -60, 0]), (0.7, [0, 0, 0]))},
    }, loop=False)
    save_anims("void_bat", {"idle": idle, "fly": fly, "attack": attack, "cast": cast})


# =====================================================================================
#  13. Espirito Errante (arcano, flutua) - translucido
# =====================================================================================
def wandering_spirit():
    m = Model("wandering_spirit")
    robe = H("CBB8FF", 190); robe2 = H("8E78D8", 170); rune = H("B98CFF"); inner = H("1A1030"); eye = H("F4ECFF")
    m.bone("root", [0, 8, 0])
    m.bone("body", [0, 12, 0], "root")
    f = m.cube("body", [-3, 10, -2], [6, 9, 4], robe, col2=robe2, pattern="smoke", alpha=200)
    for (i, j) in ((2, 2), (3, 2), (1, 4), (4, 4), (2, 6), (3, 6)):
        m.px(f["front"], i, j, rune, True)
    m.bone("skirt", [0, 10, 0], "body")
    sk = m.cube("skirt", [-4, 2, -3], [8, 8, 6], robe2, col2=H("CBB8FF", 60), pattern="smoke", alpha=190, fade=0.75)
    for s in SIDES:
        x, y, w, h = sk[s]
        for i in range(w):
            m.px(sk[s], i, 1, H("B98CFF", 200), True)
            if (i * 5) % 3 == 0:
                m.c.set(x + i, y + h - 1, (0, 0, 0, 0))
    m.bone("head", [0, 19, 0], "body")
    hd = m.cube("head", [-3.5, 19, -3.5], [7, 7, 7], robe, col2=robe2, pattern="smoke", alpha=210)
    fr = hd["front"]
    for j in range(1, 7):
        for i in range(1, 6):
            m.px(fr, i, j, inner)
    m.px(fr, 2, 3, eye, True); m.px(fr, 4, 3, eye, True)
    m.px(fr, 2, 4, H("B98CFF"), True); m.px(fr, 4, 4, H("B98CFF"), True)
    m.cube("head", [-1.5, 23, 3.5], [3, 3, 2], robe2, pattern="smoke", alpha=200)
    for nm, x, ax in (("right_arm", -3, -6), ("left_arm", 3, 3)):
        m.bone(nm, [x, 18, 0], "body")
        a = m.cube(nm, [ax, 11, -1.5], [3, 7, 3], robe, col2=robe2, pattern="smoke", alpha=200)
        for s in SIDES:
            m.hline(a[s], a[s][3] - 1, H("B98CFF", 220), True)
    m.bone("lantern", [-4.5, 11, 0], "right_arm")
    m.cube("lantern", [-5, 8, -0.5], [1, 3, 1], H("4A3F6B"))
    ln = m.cube("lantern", [-6, 4, -1.5], [3, 4, 3], H("D9B8FF"), col2=H("B98CFF"), glow=True)
    for s in SIDES:
        m.hline(ln[s], 0, H("4A3F6B")); m.hline(ln[s], 3, H("4A3F6B"))
        m.px(ln[s], 1, 1, H("FFFFFF"), True); m.px(ln[s], 1, 2, H("F4ECFF"), True)
    m.save((1.6, 2.2, 0.8))

    L = 3.0
    idle = anim(L, {
        "root": {"position": sampled(L, 8, lambda t: [0, 1.2 * math.sin(TAU * t / L), 0])},
        "skirt": {"rotation": sampled(L, 8, lambda t: [5 * math.sin(TAU * t / L + 1), 0, 4 * math.sin(TAU * t / L)])},
        "lantern": rotz(L, 8, wave(L, 10, phase=0.5)),
        "right_arm": rotx(L, 8, wave(L, 4, base=-15)), "left_arm": rotz(L, 8, wave(L, -5, base=-6)),
        "head": {"rotation": sampled(L, 8, lambda t: [4 * math.sin(TAU * t / L + 2), 10 * math.sin(TAU * t / L), 0])},
    })
    W = 2.0
    move = anim(W, {
        "root": {"position": sampled(W, 8, lambda t: [0, 1.0 * math.sin(TAU * t / W), 0])},
        "body": {"rotation": sampled(W, 4, lambda t: [12, 0, 0])},
        "skirt": {"rotation": sampled(W, 8, lambda t: [15 + 4 * math.sin(TAU * t / W), 0, 0])},
        "lantern": rotx(W, 8, wave(W, 8, base=10)),
        "right_arm": rotx(W, 4, lambda t: -10), "left_arm": rotx(W, 4, lambda t: 10),
    })
    cast = anim(0.8, {
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-125, 0, -10]), (0.6, [-110, 0, 0]), (0.8, [0, 0, 0]))},
        "right_arm": {"rotation": keys((0, [-15, 0, 0]), (0.3, [-70, 0, 15]), (0.6, [-70, 0, 15]), (0.8, [-15, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.8, [0, 0, 0]))},
        "skirt": {"rotation": keys((0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.8, [0, 0, 0]))},
    }, loop=False)
    save_anims("wandering_spirit", {"idle": idle, "move": move, "cast": cast})


# =====================================================================================
#  14. Golem Arcano (arcano)
# =====================================================================================
def arcane_golem():
    m = Model("arcane_golem", tw=128)
    stone = H("3E3A4C"); stone2 = H("2C2938"); light = H("57536A"); rune = H("B98CFF"); rune_l = H("E0C8FF")

    def runes(rect, seed, density=1):
        x, y, w, h = rect
        r = random.Random(seed)
        for _ in range(max(1, (w * h) // (40 // density))):
            i0 = r.randint(1, max(1, w - 3)); j0 = r.randint(1, max(1, h - 3))
            shape = r.choice(([(0, 0), (1, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (1, 1), (2, 1)],
                              [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)], [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)]))
            for (a, b) in shape:
                m.px(rect, i0 + a, j0 + b, rune if (a + b) % 2 else rune_l, True)

    m.bone("root", [0, 0, 0])
    for nm, x, lx in (("right_leg", -4, -7.5), ("left_leg", 4, 0.5)):
        m.bone(nm, [x, 16, 0], "root")
        lg = m.cube(nm, [lx, 0, -3.5], [7, 16, 7], stone, col2=stone2, top=light, pattern="stone")
        runes(lg["front"], int(x) + 40)
        for s in SIDES:
            m.hline(lg[s], 3, darken(stone2, 0.3))
    m.bone("body", [0, 16, 0], "root")
    w = m.cube("body", [-5, 16, -3], [10, 5, 6], stone2, col2=darken(stone2, 0.2), pattern="stone")
    m.hline(w["front"], 2, rune, True)
    ch = m.cube("body", [-9, 21, -5], [18, 13, 10], stone, col2=stone2, top=light, pattern="stone")
    for s in SIDES:
        runes(ch[s], sum(map(ord, s)) + 7)
        m.hline(ch[s], 0, light)
    # cavidade do nucleo no peito
    fr = ch["front"]
    for j in range(3, 10):
        for i in range(6, 12):
            edge = j in (3, 9) or i in (6, 11)
            m.px(fr, i, j, rune if edge else H("150F22"), edge)
    m.bone("core", [0, 27, -6], "body")
    co = m.cube("core", [-2, 25, -7], [4, 4, 2], rune_l, col2=rune, glow=True)
    m.px(co["front"], 1, 1, H("FFFFFF"), True); m.px(co["front"], 2, 2, H("FFFFFF"), True)
    m.bone("head", [0, 34, -1], "body")
    hd = m.cube("head", [-4.5, 34, -5.5], [9, 8, 9], stone, col2=stone2, top=light, pattern="stone")
    for i in range(1, 8):
        m.px(hd["front"], i, 3, rune_l, True)
    m.px(hd["front"], 4, 2, rune, True); m.px(hd["front"], 4, 4, rune, True)
    runes(hd["top"], 99)
    for nm, sx, ax, fx in (("right", -15, -14, -15), ("left", 9, 9, 8)):
        sb = nm + "_shoulder"
        m.bone(sb, [sx + 3, 35, 0], "body")
        sh = m.cube(sb, [sx, 33, -4], [6, 5, 8], light, col2=stone, pattern="stone")
        runes(sh["top"], len(nm) * 13)
        for s in SIDES:
            m.hline(sh[s], 2, rune, True)
        ab = nm + "_arm"
        m.bone(ab, [ax + 2.5, 32, 0], "body")
        ar = m.cube(ab, [ax, 15, -3], [5, 17, 6], stone, col2=stone2, pattern="stone")
        for s in SIDES:
            m.vline(ar[s], ar[s][2] // 2, rune, True, j0=3, j1=12)
        fb = nm + "_fist"
        m.bone(fb, [fx + 3.5, 15, 0], ab)
        fi = m.cube(fb, [fx, 8, -4], [7, 7, 8], light, col2=stone2, pattern="stone")
        runes(fi["front"], len(nm) * 31)
        for s in SIDES:
            m.hline(fi[s], fi[s][3] - 1, darken(stone2, 0.3))
    m.bone("orbit", [0, 40, 0], "body")
    for o in ([-12, 44, -1], [10, 46, 1], [-1, 49, 9]):
        sh = m.cube("orbit", o, [2, 3, 2], light, col2=rune)
        for s in SIDES:
            m.px(sh[s], 0, 1, rune_l, True); m.px(sh[s], 1, 1, rune_l, True)
    m.save((3, 3.4, 1.5))

    L = 4.0
    idle = anim(L, {
        "core": {"scale": sampled(L, 8, lambda t: [1 + 0.12 * (0.5 + 0.5 * math.sin(TAU * 2 * t / L))] * 3)},
        "orbit": {"rotation": keys((0, [0, 0, 0]), (L, [0, 360, 0]))},
        "right_shoulder": {"position": sampled(L, 8, lambda t: [0, 0.6 * math.sin(TAU * t / L), 0])},
        "left_shoulder": {"position": sampled(L, 8, lambda t: [0, 0.6 * math.sin(TAU * t / L + 1.5), 0])},
        "body": {"position": sampled(L, 8, lambda t: [0, 0.2 * math.sin(TAU * t / L), 0])},
        "head": roty(L, 8, wave(L, 6)),
    })
    W = 2.0
    walk = anim(W, {
        "right_leg": rotx(W, 8, wave(W, 20)), "left_leg": rotx(W, 8, wave(W, -20)),
        "right_arm": rotx(W, 8, wave(W, -15)), "left_arm": rotx(W, 8, wave(W, 15)),
        "body": {"rotation": sampled(W, 8, lambda t: [0, 0, 2 * math.sin(TAU * t / W)]),
                 "position": sampled(W, 8, lambda t: [0, 0.6 * abs(math.sin(TAU * t / W)), 0])},
        "orbit": {"rotation": keys((0, [0, 0, 0]), (W, [0, 360, 0]))},
    })
    slam = anim(1.0, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-165, 0, -15]), (0.6, [-15, 0, 0]), (1.0, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-165, 0, 15]), (0.6, [-15, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [-10, 0, 0]), (0.6, [14, 0, 0]), (1.0, [0, 0, 0]))},
        "core": {"scale": keys((0, [1, 1, 1]), (0.6, [1.5, 1.5, 1.5]), (1.0, [1, 1, 1]))},
    }, loop=False)
    attack = anim(0.7, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-100, 15, 0]), (0.4, [10, -10, 0]), (0.7, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 18, 0]), (0.4, [5, -15, 0]), (0.7, [0, 0, 0]))},
    }, loop=False)
    cast = anim(1.0, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-85, 10, 0]), (0.8, [-85, 10, 0]), (1.0, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-85, -10, 0]), (0.8, [-85, -10, 0]), (1.0, [0, 0, 0]))},
        "core": {"scale": keys((0, [1, 1, 1]), (0.7, [1.6, 1.6, 1.6]), (1.0, [1, 1, 1]))},
        "orbit": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 720, 0]))},
    }, loop=False)
    shield = anim(0.8, {
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-75, 35, 0]), (0.6, [-75, 35, 0]), (0.8, [0, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-75, -35, 0]), (0.6, [-75, -35, 0]), (0.8, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [12, 0, 0]), (0.8, [0, 0, 0]))},
        "core": {"scale": keys((0, [1, 1, 1]), (0.4, [1.8, 1.8, 1.8]), (0.8, [1, 1, 1]))},
    }, loop=False)
    save_anims("arcane_golem", {"idle": idle, "walk": walk, "slam": slam, "attack": attack, "cast": cast, "shield": shield})


# =====================================================================================
#  Dados: ovos, loot, spawns, tags, traducoes
# =====================================================================================
CREATURES = ["salamander", "ember_imp", "undine", "tide_serpent", "moss_golem", "crystal_mole", "sylph",
             "storm_hawk", "lumen_sprite", "solar_sentinel", "creeping_shade", "void_bat", "wandering_spirit",
             "arcane_golem"]


def item(name, cmin=1, cmax=1, looting=None, chance=None, player_kill=False):
    funcs = []
    if (cmin, cmax) != (1, 1):
        funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": cmin, "max": cmax}})
    if looting:
        funcs.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": name, **({"functions": funcs} if funcs else {})}]}
    conds = []
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                      "unenchanted_chance": chance,
                      "enchanted_chance": {"type": "minecraft:linear", "base": min(1.0, chance + 0.05), "per_level_above_first": 0.05}})
    if player_kill:
        conds.append({"condition": "minecraft:killed_by_player"})
    if conds:
        pool["conditions"] = conds
    return pool


ESS = lambda e: f"{NS}:essence_{e}"  # noqa: E731

LOOT = {
    "salamander": [item(ESS("fire"), 1, 1, 1), item("minecraft:magma_cream", 0, 1, 1), item("minecraft:blaze_powder", 1, 1, chance=0.15)],
    "ember_imp": [item(ESS("fire"), 1, 1, 1), item("minecraft:blaze_powder", 0, 1, 1), item("minecraft:gunpowder", 0, 2, 1)],
    "undine": [item(ESS("water"), 1, 2, 1), item("minecraft:prismarine_crystals", 0, 2, 1), item("minecraft:nautilus_shell", 1, 1, chance=0.06, player_kill=True)],
    "tide_serpent": [item(ESS("water"), 1, 2, 1), item("minecraft:prismarine_shard", 0, 2, 1), item("minecraft:cod", 0, 2, 1),
                     item("minecraft:heart_of_the_sea", 1, 1, chance=0.01, player_kill=True)],
    "moss_golem": [item(ESS("earth"), 1, 2, 1), item("minecraft:moss_block", 1, 3, 1), item("minecraft:cobblestone", 1, 3),
                   item("minecraft:flowering_azalea", 1, 1, chance=0.25)],
    "crystal_mole": [item(ESS("earth"), 1, 1, 1), item("minecraft:amethyst_shard", 1, 3, 1), item("minecraft:raw_iron", 0, 1, 1)],
    "sylph": [item(ESS("wind"), 1, 1, 1), item("minecraft:feather", 0, 2, 1), item("minecraft:wind_charge", 0, 2, 1)],
    "storm_hawk": [item(ESS("wind"), 1, 1, 1), item("minecraft:feather", 1, 3, 1), item("minecraft:breeze_rod", 1, 1, chance=0.08, player_kill=True)],
    "lumen_sprite": [item(ESS("light"), 1, 1, 1), item("minecraft:glowstone_dust", 0, 2, 1)],
    "solar_sentinel": [item(ESS("light"), 1, 2, 1), item("minecraft:gold_nugget", 2, 5, 2), item("minecraft:glowstone_dust", 0, 2, 1)],
    "creeping_shade": [item(ESS("shadow"), 1, 1, 1), item("minecraft:ink_sac", 0, 2, 1), item("minecraft:echo_shard", 1, 1, chance=0.03, player_kill=True)],
    "void_bat": [item(ESS("shadow"), 1, 1, 1), item("minecraft:ender_pearl", 1, 1, chance=0.12), item("minecraft:phantom_membrane", 0, 1, 1)],
    "wandering_spirit": [item(ESS("arcane"), 1, 2, 1), item(f"{NS}:mana_crystal", 1, 1, chance=0.2), item("minecraft:experience_bottle", 0, 1)],
    "arcane_golem": [item(f"{NS}:mana_crystal", 1, 3, 1), item(ESS("arcane"), 1, 2, 1), item("minecraft:amethyst_shard", 2, 4, 1)],
}

EGGS = {
    "salamander": (0xD9542B, 0xFFD84A), "ember_imp": (0x5A0F0B, 0xFF8A2B), "undine": (0x2E8FD6, 0xBFF4FF),
    "tide_serpent": (0x1F5F73, 0x7FF5E6), "moss_golem": (0x6E7368, 0x6FB04A), "crystal_mole": (0x4A3326, 0xB58CF0),
    "sylph": (0xC8F5E6, 0x6FD8B5), "storm_hawk": (0x3E4C68, 0x9FE8FF), "lumen_sprite": (0xFFF2B0, 0xFFC93A),
    "solar_sentinel": (0xD9C08A, 0xFFB300), "creeping_shade": (0x140B1E, 0x8A4FC8), "void_bat": (0x1B1029, 0xFF5CF0),
    "wandering_spirit": (0xCBB8FF, 0x6B4FC8), "arcane_golem": (0x3E3A4C, 0xB98CFF),
}

BADLANDS = ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"]
PEAKS = ["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes"]
SPAWNS = [
    # (arquivo, entidade, biomas, peso, min, max)
    ("salamander_overworld", "salamander", ["minecraft:desert"] + BADLANDS, 10, 1, 2),
    ("salamander_nether", "salamander", ["minecraft:nether_wastes", "minecraft:basalt_deltas", "minecraft:crimson_forest"], 12, 1, 3),
    ("ember_imp_overworld", "ember_imp", BADLANDS, 8, 1, 2),
    ("ember_imp_nether", "ember_imp", ["minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:soul_sand_valley",
                                       "minecraft:basalt_deltas"], 10, 1, 3),
    ("undine_waters", "undine", ["minecraft:river", "minecraft:ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
                                 "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean"], 2, 1, 2),
    ("tide_serpent_deep", "tide_serpent", ["minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_cold_ocean"], 7, 1, 1),
    ("tide_serpent_ocean", "tide_serpent", ["minecraft:ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean", "minecraft:cold_ocean"], 3, 1, 1),
    ("moss_golem_lush", "moss_golem", ["minecraft:lush_caves"], 15, 1, 1),
    ("moss_golem_jungle", "moss_golem", ["minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"], 6, 1, 1),
    ("crystal_mole_dripstone", "crystal_mole", ["minecraft:dripstone_caves"], 20, 1, 2),
    ("crystal_mole_caves", "crystal_mole", "#c:is_overworld", 3, 1, 1),
    ("sylph_mountains", "sylph", ["minecraft:meadow", "minecraft:grove", "minecraft:cherry_grove", "minecraft:windswept_hills",
                                  "minecraft:windswept_gravelly_hills"] + PEAKS, 10, 1, 2),
    ("storm_hawk_peaks", "storm_hawk", PEAKS + ["minecraft:windswept_hills"], 10, 1, 1),
    ("lumen_sprite_meadows", "lumen_sprite", ["minecraft:flower_forest", "minecraft:sunflower_plains", "minecraft:plains",
                                              "minecraft:meadow", "minecraft:cherry_grove"], 12, 1, 3),
    ("solar_sentinel_desert", "solar_sentinel", ["minecraft:desert"], 8, 1, 1),
    ("creeping_shade_dark_forest", "creeping_shade", ["minecraft:dark_forest"], 30, 1, 2),
    ("void_bat_deep_dark", "void_bat", ["minecraft:deep_dark"], 25, 1, 2),
    ("void_bat_depths", "void_bat", "#c:is_overworld", 4, 1, 2),
    ("wandering_spirit_overworld", "wandering_spirit", "#c:is_overworld", 3, 1, 1),
    ("arcane_golem_depths", "arcane_golem", "#c:is_overworld", 2, 1, 1),
]

NAMES = {
    "salamander": ("Salamandra", "Salamander"),
    "ember_imp": ("Diabrete de Brasa", "Ember Imp"),
    "undine": ("Ondina", "Undine"),
    "tide_serpent": ("Serpente da Maré", "Tide Serpent"),
    "moss_golem": ("Golem de Musgo", "Moss Golem"),
    "crystal_mole": ("Toupeira de Cristal", "Crystal Mole"),
    "sylph": ("Sílfide", "Sylph"),
    "storm_hawk": ("Falcão da Tempestade", "Storm Hawk"),
    "lumen_sprite": ("Fada de Luz", "Lumen Sprite"),
    "solar_sentinel": ("Sentinela Solar", "Solar Sentinel"),
    "creeping_shade": ("Sombra Rastejante", "Creeping Shade"),
    "void_bat": ("Morcego do Vazio", "Void Bat"),
    "wandering_spirit": ("Espírito Errante", "Wandering Spirit"),
    "arcane_golem": ("Golem Arcano", "Arcane Golem"),
}

EXTRA_LANG = {
    "entity.isekai_mahou.elemental_bolt": ("Projétil Elemental", "Elemental Bolt"),
    "message.isekai_mahou.lumen_sprite.fed": ("A Fada de Luz rodopia de alegria e deixa cair uma centelha de essência!",
                                              "The Lumen Sprite twirls with joy and drops a spark of essence!"),
    "message.isekai_mahou.lumen_sprite.full": ("A Fada de Luz ainda está satisfeita. Volte mais tarde.",
                                               "The Lumen Sprite is still content. Come back later."),
    "message.isekai_mahou.wandering_spirit.gift": ("O Espírito Errante inclina a cabeça em silêncio e deixa um presente...",
                                                   "The Wandering Spirit bows in silence and leaves a gift..."),
    "message.isekai_mahou.arcane_golem.awaken": ("As runas do Golem Arcano se acendem: o guardião despertou!",
                                                 "The Arcane Golem's runes flare: the guardian has awakened!"),
    "message.isekai_mahou.sylph.blessing": ("A Sílfide dança ao seu redor e o vento te empurra adiante.",
                                            "The Sylph dances around you and the wind carries you forward."),
}


def data():
    for c in CREATURES:
        save_json(os.path.join(A, "models/item", c + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        save_json(os.path.join(D, "loot_table/entities", c + ".json"), {
            "type": "minecraft:entity", "pools": LOOT[c], "random_sequence": f"{NS}:entities/{c}"})
    # Golem Arcano: 25% de deixar um Pergaminho de Glifo de um aumento avancado do nucleo (sempre existe)
    golem = json.load(open(os.path.join(D, "loot_table/entities/arcane_golem.json"), encoding="utf-8"))
    golem["pools"].append({
        "rolls": 1, "bonus_rolls": 0,
        "conditions": [{"condition": "minecraft:killed_by_player"},
                       {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                        "unenchanted_chance": 0.25,
                        "enchanted_chance": {"type": "minecraft:linear", "base": 0.3, "per_level_above_first": 0.05}}],
        "entries": [{"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": w,
                     "functions": [{"function": "minecraft:set_components", "components": {f"{NS}:glyph": f"{NS}:{g}"}}]}
                    for g, w in (("pierce", 4), ("ricochet", 4), ("chain", 3), ("echo", 2), ("persistent", 2), ("homing", 1))]})
    save_json(os.path.join(D, "loot_table/entities/arcane_golem.json"), golem)

    bm = os.path.join(D, "neoforge/biome_modifier")
    for fname, ent, biomes, w, mn, mx in SPAWNS:
        save_json(os.path.join(bm, fname + ".json"), {
            "type": "neoforge:add_spawns", "biomes": biomes,
            "spawners": [{"type": f"{NS}:{ent}", "weight": w, "minCount": mn, "maxCount": mx}]})

    def tag(path, ids):
        save_json(os.path.join(MERGE, "data/minecraft/tags/entity_type", path + ".json"),
                  {"replace": False, "values": [f"{NS}:{i}" for i in ids]})
    tag("can_breathe_under_water", ["undine", "tide_serpent", "moss_golem", "arcane_golem", "wandering_spirit", "solar_sentinel"])
    tag("aquatic", ["undine", "tide_serpent"])
    tag("fall_damage_immune", ["ember_imp", "sylph", "storm_hawk", "lumen_sprite", "void_bat", "wandering_spirit",
                               "solar_sentinel", "arcane_golem"])
    tag("freeze_hurts_extra_types", ["salamander", "ember_imp"])
    tag("freeze_immune_entity_types", ["wandering_spirit", "arcane_golem"])

    pt, en = {}, {}
    for c in CREATURES:
        p, e = NAMES[c]
        pt[f"entity.{NS}.{c}"] = p
        en[f"entity.{NS}.{c}"] = e
        pt[f"item.{NS}.{c}_spawn_egg"] = f"Ovo Gerador de {p}"
        en[f"item.{NS}.{c}_spawn_egg"] = f"{e} Spawn Egg"
    for k, (p, e) in EXTRA_LANG.items():
        pt[k] = p
        en[k] = e
    save_json(os.path.join(LANG, "pt_br.json"), pt)
    save_json(os.path.join(LANG, "en_us.json"), en)


if __name__ == "__main__":
    salamander()
    ember_imp()
    undine()
    tide_serpent()
    moss_golem()
    crystal_mole()
    sylph()
    storm_hawk()
    lumen_sprite()
    solar_sentinel()
    creeping_shade()
    void_bat()
    wandering_spirit()
    arcane_golem()
    data()
    print("creatures_d: assets gerados")
