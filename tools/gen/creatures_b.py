"""
Gerador de assets do modulo creatures_b (humanoides): modelos GeckoLib (.geo.json), animacoes,
texturas 'box UV' pintadas por face (+ glowmasks), itens, ovos, loot tables, biome modifiers e receitas.

Rodar a partir da raiz do projeto:  py -I tools/gen/creatures_b.py
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
MERGE = os.path.join(ROOT, "src/main/merge/creatures_b")
NS = "isekai_mahou"


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
        """Aplica fn(cor, fx, fy) -> cor em toda a face."""
        for yy in range(self.h):
            for xx in range(self.w):
                self.set(xx, yy, fn(self.get(xx, yy), xx, yy))


class P:
    """Pintura de um cubo: cor base, cores por face, ruido e decoracao (deco(faces_dict))."""

    def __init__(self, base, faces=None, noise=0.06, deco=None, grad=True):
        self.base = H(base) if isinstance(base, str) else base
        self.faces = {k: (H(v) if isinstance(v, str) else v) for k, v in (faces or {}).items()}
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
        b["cubes"] = []
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0, rotation=None, pivot=None):
        c = {"bone": bone, "origin": list(origin), "size": [int(s) for s in size], "paint": paint,
             "inflate": inflate, "rotation": rotation, "pivot": pivot}
        self.cubes.append(c)
        return c

    # ---------------- empacotamento de UV ----------------
    def _pack(self):
        items = []
        for i, c in enumerate(self.cubes):
            w, h, d = c["size"]
            items.append((i, 2 * (d + w), d + h))
        order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
        for W in (64, 128, 256):
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
            Hh = 16
            while Hh < used:
                Hh *= 2
            if Hh <= W:
                return W, Hh, pos
        raise RuntimeError("modelo grande demais: " + self.ident)

    def layout(self):
        self.tw, self.th, pos = self._pack()
        for i, c in enumerate(self.cubes):
            c["uv"] = pos[i]

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

    # ---------------- saida ----------------
    def geo_json(self):
        bones = []
        for b in self.bones:
            bb = {k: v for k, v in b.items() if k != "cubes"}
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
                    "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": bones,
            }],
        }

    def save_geo(self):
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json())

    def save_texture(self, name=None, glow=False):
        name = name or self.ident
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        if glow:
            self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# ---------------- decoracoes reutilizaveis ----------------
def chain(*fns):
    def d(fs):
        for fn in fns:
            if fn:
                fn(fs)
    return d


def eyes(xs, y, color, glow=True, face="front", pupil=None):
    def d(fs):
        f = fs[face]
        for x in xs:
            f.set(x, y, H(color), glow)
            if pupil:
                f.set(x, y + 1, H(pupil))
    return d


def band(rows, color, faces=("front", "back", "left", "right")):
    def d(fs):
        for n in faces:
            for r in rows:
                fs[n].row(r if r >= 0 else fs[n].h + r, H(color))
    return d


def bottom_rows(n, color, faces=("front", "back", "left", "right", "bottom")):
    def d(fs):
        for name in faces:
            f = fs[name]
            if name == "bottom":
                f.fill(darken(H(color), 0.25))
                continue
            for r in range(n):
                f.row(f.h - 1 - r, H(color) if r < n - 1 else lighten(H(color), 0.12))
    return d


def top_rows(n, color, faces=("front", "back", "left", "right", "top")):
    def d(fs):
        for name in faces:
            f = fs[name]
            if name == "top":
                f.fill(lighten(H(color), 0.08))
                continue
            for r in range(n):
                f.row(r, H(color))
    return d


def speckle(color, density, seed, faces=("front", "back", "left", "right", "top")):
    def d(fs):
        for n in faces:
            fs[n].scatter(H(color), density, seed + len(n))
    return d


def scales(color, faces=("front", "back", "left", "right", "top"), step=2):
    """Padrao de escamas: pontinhos escuros em xadrez."""
    def d(fs):
        for n in faces:
            f = fs[n]
            for yy in range(f.h):
                for xx in range(f.w):
                    if (xx + (yy // step) % 2) % step == 0 and yy % step == 0:
                        f.set(xx, yy, H(color))
    return d


def fur(color_dark, color_light, seed, faces=("front", "back", "left", "right", "top")):
    """Pelo: tracinhos verticais claros/escuros."""
    def d(fs):
        rnd = random.Random(seed)
        for n in faces:
            f = fs[n]
            for xx in range(f.w):
                for yy in range(f.h):
                    r = rnd.random()
                    if r < 0.16:
                        f.set(xx, yy, H(color_dark))
                        f.set(xx, yy + 1, H(color_dark))
                    elif r < 0.24:
                        f.set(xx, yy, H(color_light))
    return d


def front_rect(x0, y0, x1, y1, color, glow=False, face="front"):
    def d(fs):
        fs[face].rect(x0, y0, x1, y1, H(color), glow)
    return d


def edge_light(color, face="front"):
    """Fio de luz na borda de baixo (gume de lamina)."""
    def d(fs):
        fs[face].row(fs[face].h - 1, H(color))
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
    return {fmt(t): [float(x) for x in v] for t, v in pairs}


S = lambda p: math.sin(2 * math.pi * p)  # noqa: E731
C = lambda p: math.cos(2 * math.pi * p)  # noqa: E731


def humanoid_anims(walk_len=1.0, leg=30, arm=25, run_len=0.6, run_leg=48, run_arm=40, bob=0.5, lean=8,
                   tails=(), arms=("right_arm", "left_arm"), legs=("right_leg", "left_leg"), still_arm=None):
    a = {}
    ra, la = arms
    rl, ll = legs

    def tail_bones(length, amp, speed=1):
        out = {}
        for i, t in enumerate(tails):
            out[t] = {"rotation": cyc(length, lambda p, k=i: [0, amp * (1 + 0.4 * k) * S(speed * p - 0.12 * k), 0])}
        return out

    idle = {
        "body": {"rotation": cyc(3.0, lambda p: [1.2 * S(p), 0, 0])},
        ra: {"rotation": cyc(3.0, lambda p: [-2 * S(p), 0, 2 + 2 * S(p)])},
        la: {"rotation": cyc(3.0, lambda p: [-2 * S(p), 0, -2 - 2 * S(p)])},
    }
    idle.update(tail_bones(3.0, 6))
    a["idle"] = {"loop": True, "animation_length": 3.0, "bones": idle}

    walk = {
        rl: {"rotation": cyc(walk_len, lambda p: [leg * S(p), 0, 0])},
        ll: {"rotation": cyc(walk_len, lambda p: [-leg * S(p), 0, 0])},
        ra: {"rotation": cyc(walk_len, lambda p: [-arm * S(p), 0, 2])},
        la: {"rotation": cyc(walk_len, lambda p: [arm * S(p), 0, -2])},
        "body": {"position": cyc(walk_len, lambda p: [0, bob * abs(C(p)), 0], steps=8)},
    }
    if still_arm:
        walk[still_arm] = {"rotation": cyc(walk_len, lambda p: [-4 * S(p), 0, 0])}
    walk.update(tail_bones(walk_len, 10, speed=1))
    a["walk"] = {"loop": True, "animation_length": walk_len, "bones": walk}

    run = {
        rl: {"rotation": cyc(run_len, lambda p: [run_leg * S(p), 0, 0])},
        ll: {"rotation": cyc(run_len, lambda p: [-run_leg * S(p), 0, 0])},
        ra: {"rotation": cyc(run_len, lambda p: [-run_arm * S(p), 0, 4])},
        la: {"rotation": cyc(run_len, lambda p: [run_arm * S(p), 0, -4])},
        "body": {"rotation": cyc(run_len, lambda p: [lean, 0, 0], steps=2),
                 "position": cyc(run_len, lambda p: [0, bob * 1.6 * abs(C(p)), 0])},
    }
    if still_arm:
        run[still_arm] = {"rotation": cyc(run_len, lambda p: [-15 - 8 * S(p), 0, 0])}
    run.update(tail_bones(run_len, 14, speed=1))
    a["run"] = {"loop": True, "animation_length": run_len, "bones": run}
    return a


def chop(arm="right_arm", length=0.6, wind=-150, strike=-15, twist=14):
    return {"loop": False, "animation_length": length, "bones": {
        arm: {"rotation": keys([(0, [0, 0, 0]), (length * 0.35, [wind, 0, -8]), (length * 0.6, [strike, 0, 4]),
                                (length, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (length * 0.35, [-4, twist, 0]), (length * 0.6, [6, -twist, 0]),
                                   (length, [0, 0, 0])])},
    }}


def slam2(length=1.1, arms=("right_arm", "left_arm"), wind=0.45, hit=0.62):
    b = {}
    for a in arms:
        b[a] = {"rotation": keys([(0, [0, 0, 0]), (length * wind, [-170, 0, 0]), (length * hit, [-40, 0, 0]),
                                  (length * 0.85, [-40, 0, 0]), (length, [0, 0, 0])])}
    b["body"] = {"rotation": keys([(0, [0, 0, 0]), (length * wind, [-12, 0, 0]), (length * hit, [22, 0, 0]),
                                   (length * 0.85, [18, 0, 0]), (length, [0, 0, 0])])}
    return {"loop": False, "animation_length": length, "bones": b}


def roar(length=2.0, arms=("right_arm", "left_arm"), head="head"):
    b = {
        arms[0]: {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, 55]), (length - 0.3, [-35, 0, 60]), (length, [0, 0, 0])])},
        arms[1]: {"rotation": keys([(0, [0, 0, 0]), (0.3, [-30, 0, -55]), (length - 0.3, [-35, 0, -60]), (length, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-14, 0, 0]), (length - 0.3, [-16, 0, 0]), (length, [0, 0, 0])]),
                 "position": cyc(length, lambda p: [0.25 * S(p * 14), 0, 0], steps=28)},
    }
    if head:
        b[head] = {"position": keys([(0, [0, 0, 0]), (0.3, [0, 0.5, 0.5]), (length - 0.3, [0, 0.5, 0.5]), (length, [0, 0, 0])])}
    return {"loop": False, "animation_length": length, "bones": b}


def save_anims(ident, anims):
    out = {"format_version": "1.8.0", "animations": {}}
    for n, a in anims.items():
        out["animations"]["animation.%s.%s" % (ident, n)] = a
    write_json(os.path.join(ASSETS, "animations/entity", ident + ".animation.json"), out)


# =====================================================================================
#  KOBOLD
# =====================================================================================
def build_kobold():
    m = Model("kobold")
    sc, scd, belly, horn = "#A3482D", "#7C3220", "#D9A15C", "#E6DCC0"
    leather, brass, wax = "#6A4426", "#C9A23A", "#EFE6CF"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 7, 0), "root")
    m.cube("body", (-3, 7, -1.5), (6, 6, 3), P(sc, deco=chain(
        scales(scd, faces=("back", "left", "right")),
        front_rect(1, 1, 4, 5, belly),
        lambda fs: [fs["front"].row(r, darken(H(belly), 0.15)) for r in (2, 4)],
        lambda fs: [fs["front"].set(i, i, H(leather)) for i in range(6)],
        band([-1], leather))))
    m.bone("head", (0, 13, -0.5), "body")
    m.cube("head", (-2.5, 13, -3), (5, 4, 5), P(sc, deco=chain(
        scales(scd, faces=("back", "left", "right", "top")),
        lambda fs: fs["front"].row(0, H(scd)),
        eyes((1, 3), 1, "#FFD34A"),
        lambda fs: [fs["front"].set(x, 2, H("#2A120A")) for x in (1, 3)])))
    m.cube("head", (-1.5, 13, -6), (3, 2, 3), P(sc, faces={"bottom": belly}, deco=chain(
        lambda fs: [fs["front"].set(x, 0, H("#2A120A")) for x in (0, 2)],
        lambda fs: fs["front"].row(1, H("#F2EBD9")),
        lambda fs: fs["left"].row(1, H("#F2EBD9")), lambda fs: fs["right"].row(1, H("#F2EBD9")))))
    m.cube("head", (-2.5, 16.6, -0.5), (1, 1, 3), P(horn, noise=0.03), rotation=[-20, 0, 0], pivot=[-2, 17, 0])
    m.cube("head", (1.5, 16.6, -0.5), (1, 1, 3), P(horn, noise=0.03), rotation=[-20, 0, 0], pivot=[2, 17, 0])
    # capacete de mineiro com vela
    m.cube("head", (-2.5, 17, -3), (5, 1, 5), P(brass, deco=lambda fs: fs["front"].row(0, lighten(H(brass), 0.3))), inflate=0.25)
    m.cube("head", (-0.5, 18.2, -2), (1, 2, 1), P(wax, noise=0.02, deco=lambda fs: (
        fs["top"].fill(H("#FFB43A")), fs["top"].set(0, 0, H("#FFE38A"), True),
        [fs[n].set(0, 0, H("#FFC94A"), True) for n in ("front", "back", "left", "right")])))
    m.bone("tail", (0, 8, 1.5), "body")
    m.cube("tail", (-1, 7, 1.5), (2, 2, 4), P(sc, deco=scales(scd)))
    m.bone("tail_tip", (0, 7.5, 5.5), "tail")
    m.cube("tail_tip", (-0.5, 7, 5.5), (1, 1, 3), P(scd))
    m.bone("right_arm", (-3.5, 12.5, 0), "body")
    m.cube("right_arm", (-5, 7, -1), (2, 6, 2), P(sc, deco=chain(bottom_rows(1, horn, faces=("front", "left", "right", "back")),
                                                                band([1], leather))))
    m.bone("right_hand", (-4, 7.5, -0.5), "right_arm")
    m.bone("left_arm", (3.5, 12.5, 0), "body")
    m.cube("left_arm", (3, 7, -1), (2, 6, 2), P(sc, deco=chain(bottom_rows(1, horn, faces=("front", "left", "right", "back")),
                                                              band([1], leather))))
    for side, x in (("right_leg", -2.5), ("left_leg", 0.5)):
        m.bone(side, (x + 1, 7, 0), "root")
        m.cube(side, (x, 2, -1), (2, 5, 2), P(sc, deco=scales(scd)))
        m.cube(side, (x, 0, -2), (2, 2, 3), P(scd, deco=lambda fs: fs["front"].row(1, H(horn))))
    m.layout()
    m.paint(11)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=0.7, leg=35, arm=30, run_len=0.45, bob=0.3, tails=("tail", "tail_tip"))
    a["attack"] = chop(length=0.5, wind=-140)
    a["mine"] = {"loop": True, "animation_length": 0.6, "bones": {
        "right_arm": {"rotation": keys([(0, [-20, 0, 0]), (0.25, [-140, 0, -6]), (0.4, [-40, 0, 0]), (0.6, [-20, 0, 0])])},
        "body": {"rotation": keys([(0, [6, 0, 0]), (0.25, [-4, 6, 0]), (0.4, [12, -4, 0]), (0.6, [6, 0, 0])])},
    }}
    save_anims("kobold", a)


# =====================================================================================
#  ORC / ORC BERSERKER (mesma malha)
# =====================================================================================
def orc_model(v):
    m = Model("orc")
    skin, skd = v["skin"], v["skin_dark"]
    leather, leather_d = "#6B4A2B", "#47301A"
    iron, tusk, cloth = "#A3A9AF", "#EFE8CF", v["cloth"]
    war = v.get("paint")
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 13, 0), "root")

    def chest(fs):
        f = fs["front"]
        # peitoral marcado
        f.rect(1, 2, 3, 2, darken(H(skin), 0.18))
        f.rect(6, 2, 8, 2, darken(H(skin), 0.18))
        f.col(4, darken(H(skin), 0.1))
        f.col(5, darken(H(skin), 0.1))
        if v.get("straps"):
            for i in range(10):
                f.set(i, i, H(leather))
                f.set(9 - i, i, H(leather))
            fs["back"].tint(lambda c, x, y: H(leather) if x in (y, 9 - y) else c)
        if war:
            for r in (1, 4, 7):
                f.rect(1, r, 3, r, H(war))
                f.rect(6, r, 8, r, H(war))
            for n in ("left", "right"):
                fs[n].row(3, H(war))
        if v.get("necklace"):
            for x in range(1, 9):
                f.set(x, 0 if x in (1, 8) else (1 if x in (2, 7) else 2), H("#E9E1C6"))
            f.set(4, 3, H("#E9E1C6"))
            f.set(5, 3, H("#E9E1C6"))
    m.cube("body", (-5, 13, -3), (10, 10, 6), P(skin, deco=chest))
    m.cube("body", (-4.5, 11, -2.5), (9, 3, 5), P(leather, deco=chain(
        lambda fs: fs["front"].rect(3, 0, 5, 2, H(iron)), lambda fs: fs["front"].set(4, 1, H(leather_d)),
        band([0], leather_d))))
    m.cube("body", (-2.5, 5, -3), (5, 6, 1), P(cloth, deco=chain(
        lambda fs: fs["front"].row(5, darken(H(cloth), 0.3)),
        lambda fs: [fs["front"].set(x, 5, (0, 0, 0, 0)) for x in (1, 3)])))
    m.cube("body", (-2.5, 6, 2), (5, 5, 1), P(cloth))
    m.bone("head", (0, 23, -1), "body")

    def face(fs):
        f = fs["front"]
        f.rect(1, 2, 2, 2, darken(H(skin), 0.4))
        f.rect(4, 2, 5, 2, darken(H(skin), 0.4))
        f.set(2, 3, H(v["eye"]), v.get("glow_eyes", False))
        f.set(4, 3, H(v["eye"]), v.get("glow_eyes", False))
        f.set(1, 3, H("#F2F2E0"))
        f.set(5, 3, H("#F2F2E0"))
        f.set(3, 4, darken(H(skin), 0.25))
        f.set(3, 5, darken(H(skin), 0.25))
        if war:
            f.col(3, H(war))
            f.rect(0, 4, 1, 4, H(war))
            f.rect(5, 4, 6, 4, H(war))
        fs["top"].scatter(darken(H(skin), 0.12), 0.25, 3)
    m.cube("head", (-3.5, 23, -5), (7, 7, 6), P(skin, deco=face))
    m.cube("head", (-4, 22, -5.5), (8, 3, 6), P(skd, deco=chain(
        lambda fs: fs["front"].row(0, darken(H(skd), 0.35)),
        lambda fs: [fs["front"].set(x, 0, H(tusk)) for x in (1, 6)])))
    m.cube("head", (-3, 25, -6), (1, 2, 1), P(tusk, noise=0.03), rotation=[0, 0, 10], pivot=[-2.5, 25, -5.5])
    m.cube("head", (2, 25, -6), (1, 2, 1), P(tusk, noise=0.03), rotation=[0, 0, -10], pivot=[2.5, 25, -5.5])
    m.cube("head", (-5.5, 26, -3), (2, 2, 1), P(skin), rotation=[0, 0, -15], pivot=[-3.5, 27, -2.5])
    m.cube("head", (3.5, 26, -3), (2, 2, 1), P(skin), rotation=[0, 0, 15], pivot=[3.5, 27, -2.5])
    m.cube("head", (-1, 30, -2), (2, 2, 3), P(v["hair"], noise=0.1))
    # bracos
    m.bone("right_arm", (-6, 22, 0), "body")
    m.cube("right_arm", (-9, 12, -2), (4, 11, 4), P(skin, deco=chain(bottom_rows(3, leather), band([-3], leather_d),
                                                                     (band([4], war) if war else None))))
    m.bone("weapon", (-7, 12, 0), "right_arm")
    m.cube("weapon", (-7.5, 11.5, -5), (1, 1, 6), P(leather_d))
    m.cube("weapon", (-7.5, 7, -13), (1, 6, 8), P(iron, deco=chain(
        lambda fs: [fs[n].row(0, darken(H(iron), 0.35)) for n in ("left", "right")],
        lambda fs: [fs[n].row(fs[n].h - 1, lighten(H(iron), 0.45)) for n in ("left", "right")],
        lambda fs: [fs[n].scatter(H("#7A4B2A"), 0.08, 5) for n in ("left", "right")])))
    m.bone("left_arm", (6, 22, 0), "body")
    m.cube("left_arm", (5, 12, -2), (4, 11, 4), P(skin, deco=chain(bottom_rows(3, leather), band([-3], leather_d),
                                                                   (band([4], war) if war else None))))
    m.cube("left_arm", (4.5, 20, -2.5), (5, 4, 5), P(v["pad"], deco=chain(band([0], lighten(H(v["pad"]), 0.25)),
                                                                          speckle(darken(H(v["pad"]), 0.3), 0.1, 9))), inflate=0.2)
    m.cube("left_arm", (6.5, 24, -0.5), (1, 2, 1), P(tusk))
    for side, x in (("right_leg", -4.5), ("left_leg", 0.5)):
        m.bone(side, (x + 2, 11, 0), "root")
        m.cube(side, (x, 0, -2), (4, 11, 4), P(v["pants"], deco=chain(bottom_rows(4, "#3A2A1C"),
                                                                       band([3], leather_d))))
    m.layout()
    m.paint(23)
    return m


ORC = dict(skin="#6E9A3E", skin_dark="#4E7330", cloth="#8B2E2E", eye="#E8C93A", hair="#1F1A14",
           pad="#8A8F95", pants="#5C4632", straps=True)
BERSERKER = dict(skin="#5E7F31", skin_dark="#405A22", cloth="#3C2A1F", eye="#FF3B2A", glow_eyes=True, hair="#7A1712",
                 pad="#5A3A26", pants="#3E3024", paint="#B3241B", necklace=True)


def build_orcs():
    m = orc_model(ORC)
    m.save_geo()
    m.save_texture()
    b = orc_model(BERSERKER)
    b.save_texture("orc_berserker", glow=True)
    a = humanoid_anims(walk_len=1.0, leg=28, arm=22, run_len=0.6, bob=0.6)
    a["attack"] = chop(length=0.6)
    a["roar"] = roar(1.5)
    save_anims("orc", a)


# =====================================================================================
#  ORC LORD
# =====================================================================================
def build_orc_lord():
    m = Model("orc_lord")
    skin, skd, gold, black = "#557A2E", "#3D5A20", "#D8B13A", "#4B4F55"
    cape, leather, tusk, fur_c = "#7A1E1E", "#4A321C", "#F0E9D0", "#5A4636"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 15, 0), "root")

    def chest(fs):
        f = fs["front"]
        f.rect(0, 0, f.w - 1, 5, H(black))
        f.row(0, H(gold))
        f.row(5, H(gold))
        f.rect(5, 1, 7, 4, H(gold))
        f.set(6, 2, H("#B3241B"), True)
        f.rect(2, 7, 4, 7, darken(H(skin), 0.2))
        f.rect(8, 7, 10, 7, darken(H(skin), 0.2))
        for n in ("left", "right", "back"):
            fs[n].rect(0, 0, fs[n].w - 1, 5, H(black))
            fs[n].row(5, H(gold))
    m.cube("body", (-6.5, 15, -4), (13, 13, 8), P(skin, deco=chest))
    m.cube("body", (-6, 13, -3.5), (12, 4, 7), P(leather, deco=chain(
        lambda fs: fs["front"].rect(4, 0, 7, 3, H(gold)), lambda fs: fs["front"].rect(5, 1, 6, 2, H("#7A5A12")))))
    m.cube("body", (-3.5, 5, -4), (7, 8, 1), P(cape, deco=chain(lambda fs: fs["front"].col(0, H(gold)),
                                                                lambda fs: fs["front"].col(6, H(gold)),
                                                                lambda fs: fs["front"].rect(2, 2, 4, 4, H(black)))))
    m.bone("cape", (0, 28, 4), "body")
    m.cube("cape", (-6, 6, 4), (12, 22, 1), P(cape, deco=chain(
        lambda fs: fs["back"].tint(lambda c, x, y: darken(c, 0.12) if x % 3 == 0 else c),
        lambda fs: fs["back"].row(fs["back"].h - 1, H(gold)),
        lambda fs: fs["back"].rect(4, 4, 7, 8, H(black)), lambda fs: fs["back"].rect(5, 5, 6, 7, H(gold)))))
    m.bone("head", (0, 28, -1), "body")

    def face(fs):
        f = fs["front"]
        f.rect(1, 3, 3, 3, darken(H(skin), 0.45))
        f.rect(5, 3, 7, 3, darken(H(skin), 0.45))
        f.set(2, 4, H("#FF4A2A"), True)
        f.set(6, 4, H("#FF4A2A"), True)
        f.set(4, 5, darken(H(skin), 0.25))
        f.rect(0, 6, 1, 6, H("#9B2B20"))
        f.rect(7, 6, 8, 6, H("#9B2B20"))
    m.cube("head", (-4.5, 28, -6), (9, 9, 8), P(skin, deco=face))
    m.cube("head", (-5, 27, -6.5), (10, 4, 8), P(skd, deco=lambda fs: fs["front"].row(0, darken(H(skd), 0.35))))
    m.cube("head", (-4.5, 29.5, -7.2), (1, 3, 1), P(tusk, noise=0.03), rotation=[0, 0, 12], pivot=[-4, 29.5, -6.7])
    m.cube("head", (3.5, 29.5, -7.2), (1, 3, 1), P(tusk, noise=0.03), rotation=[0, 0, -12], pivot=[4, 29.5, -6.7])
    # coroa de ferro negro com chifres
    m.cube("head", (-5, 36, -6.5), (10, 2, 9), P(black, deco=chain(band([0], gold), lambda fs: fs["front"].set(4, 1, H("#B3241B"), True),
                                                                   lambda fs: fs["front"].set(5, 1, H("#B3241B"), True))), inflate=0.2)
    for x in (-4.5, -1, 2.5):
        m.cube("head", (x, 38, -6.5), (2, 3, 1), P(gold, noise=0.04))
    m.cube("head", (-8, 36, -3), (3, 2, 2), P(tusk), rotation=[0, 0, -30], pivot=[-5, 37, -2])
    m.cube("head", (5, 36, -3), (3, 2, 2), P(tusk), rotation=[0, 0, 30], pivot=[5, 37, -2])
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        px = 8 * sx
        m.bone(side, (px, 26, 0), "body")
        ox = -12 if sx < 0 else 7
        m.cube(side, (ox, 12, -2.5), (5, 15, 5), P(skin, deco=chain(bottom_rows(4, black), band([-4], gold))))
        m.cube(side, (ox - 0.5, 24, -3), (6, 5, 6), P(black, deco=chain(band([0, 4], gold), speckle("#2E3135", 0.15, 4))),
               inflate=0.3)
        m.cube(side, (ox + 2, 29, -1), (2, 3, 2), P(gold), rotation=[0, 0, -20 * sx], pivot=[ox + 3, 29, 0])
        m.cube(side, (ox + 1.5, 27, -4), (2, 2, 1), P(fur_c))
    m.bone("weapon", (-9.5, 13, 0), "right_arm")
    m.cube("weapon", (-10, 12.5, -7), (1, 1, 9), P(leather))
    m.cube("weapon", (-10.5, 6, -19), (2, 9, 12), P(black, deco=chain(
        lambda fs: [fs[n].row(fs[n].h - 1, lighten(H("#B9C0C7"), 0.3)) for n in ("left", "right")],
        lambda fs: [fs[n].row(fs[n].h - 2, H("#8E959C")) for n in ("left", "right")],
        lambda fs: [fs[n].rect(2, 1, 4, 3, H(gold)) for n in ("left", "right")],
        lambda fs: [fs[n].set(3, 2, H("#B3241B"), True) for n in ("left", "right")])))
    for side, x in (("right_leg", -5.5), ("left_leg", 0.5)):
        m.bone(side, (x + 2.5, 13, 0), "root")
        m.cube(side, (x, 0, -2.5), (5, 13, 5), P(leather, deco=chain(bottom_rows(4, black), band([4], gold), band([-4], gold))))
    m.layout()
    m.paint(31)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=1.2, leg=26, arm=18, run_len=0.75, bob=0.7, lean=10)
    a["attack"] = chop(length=0.75, wind=-160)
    a["slam"] = slam2(1.2)
    a["warcry"] = roar(2.0)
    a["intro"] = roar(3.0)
    a["walk"]["bones"]["cape"] = {"rotation": cyc(1.2, lambda p: [8 + 4 * S(p), 0, 0])}
    a["run"]["bones"]["cape"] = {"rotation": cyc(0.75, lambda p: [22 + 6 * S(p), 0, 0])}
    save_anims("orc_lord", a)


# =====================================================================================
#  OGRE
# =====================================================================================
def build_ogre():
    m = Model("ogre")
    skin, skd, hide, wood, wood_d = "#B79A6A", "#8F7448", "#6E5233", "#7B5530", "#4F3418"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 14, 0), "root")

    def belly(fs):
        f = fs["front"]
        for y in range(f.h):
            for x in range(f.w):
                dx, dy = (x - f.w / 2 + 0.5) / (f.w / 2), (y - f.h * 0.55) / (f.h * 0.6)
                if dx * dx + dy * dy < 0.8:
                    f.set(x, y, lighten(f.get(x, y), 0.08))
        f.set(f.w // 2, int(f.h * 0.55), darken(H(skin), 0.4))
        for n in ("front", "left", "right", "back"):
            g = fs[n]
            g.rect(0, g.h - 4, g.w - 1, g.h - 1, H(hide))
            g.row(g.h - 4, darken(H(hide), 0.3))
        fs["front"].scatter(darken(H(skin), 0.15), 0.05, 2)
    m.cube("body", (-9, 14, -6), (18, 16, 12), P(skin, deco=belly))
    m.cube("body", (-8, 30, -4), (16, 8, 9), P(skin, deco=chain(
        lambda fs: fs["front"].rect(2, 2, 6, 2, darken(H(skin), 0.15)),
        lambda fs: fs["front"].rect(9, 2, 13, 2, darken(H(skin), 0.15)),
        speckle("#8A6A4A", 0.08, 5))))
    m.cube("body", (-4, 6, -6.5), (8, 9, 1), P(hide, deco=chain(speckle("#4A3420", 0.2, 6),
                                                                lambda fs: [fs["front"].set(x, 8, (0, 0, 0, 0)) for x in (1, 4, 6)])))
    m.bone("head", (0, 38, -2), "body")

    def face(fs):
        f = fs["front"]
        f.rect(1, 2, 3, 2, darken(H(skin), 0.5))
        f.rect(5, 2, 7, 2, darken(H(skin), 0.5))
        f.set(2, 3, H("#F4F0D0"))
        f.set(5, 3, H("#F4F0D0"))
        f.set(3, 3, H("#3A1E0E"))
        f.set(6, 3, H("#3A1E0E"))
        f.rect(2, 6, 6, 6, darken(H(skin), 0.45))
        f.set(5, 5, H("#F4F0D0"))
        fs["top"].scatter(darken(H(skin), 0.1), 0.3, 8)
    m.cube("head", (-4.5, 38, -6), (9, 8, 7), P(skin, deco=face))
    m.cube("head", (-1.5, 40, -8), (3, 3, 2), P(skd, deco=lambda fs: [fs["front"].set(x, 2, H("#3A1E0E")) for x in (0, 2)]))
    m.cube("head", (-5.5, 41, -3), (1, 3, 2), P(skin))
    m.cube("head", (4.5, 41, -3), (1, 3, 2), P(skin))
    m.cube("head", (-1, 46, -3), (2, 2, 2), P("#2B2018"))
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (10 * sx, 36, 0), "body")
        ox = -16 if sx < 0 else 10
        m.cube(side, (ox, 16, -3), (6, 21, 6), P(skin, deco=chain(speckle(skd, 0.08, 3), band([3], hide))))
        m.cube(side, (ox - 0.5, 11, -3.5), (7, 6, 7), P(skin, deco=lambda fs: [fs[n].row(fs[n].h - 1, darken(H(skin), 0.3))
                                                                              for n in ("front", "left", "right", "back")]))
    m.bone("club", (-13, 13, 0), "right_arm")
    m.cube("club", (-14, 12, -10), (2, 2, 12), P(wood, deco=chain(band([0], wood_d), speckle(wood_d, 0.12, 12))))
    m.cube("club", (-15, 11, -24), (4, 4, 14), P(wood, deco=chain(
        speckle(wood_d, 0.18, 13), lambda fs: fs["front"].fill(H("#A47A4A")),
        lambda fs: [fs["front"].set(x, y, H("#6B4A28")) for x, y in ((1, 1), (2, 2), (1, 2))])))
    for (x, y, z) in ((-16, 13, -20), (-11, 12, -16), (-14, 15, -18), (-13, 10, -22)):
        m.cube("club", (x, y, z), (1, 1, 1), P("#8A9096", noise=0.02))
    for side, x in (("right_leg", -7.5), ("left_leg", 0.5)):
        m.bone(side, (x + 3.5, 14, 0), "root")
        m.cube(side, (x, 2, -3.5), (7, 12, 7), P(skin, deco=chain(top_rows(5, hide), speckle(skd, 0.08, 4))))
        m.cube(side, (x - 0.5, 0, -4.5), (8, 2, 8), P(skd))
    m.layout()
    m.paint(41)
    m.save_geo()
    m.save_texture()
    a = humanoid_anims(walk_len=1.5, leg=22, arm=14, run_len=1.0, run_leg=32, run_arm=20, bob=1.2, lean=6,
                       still_arm="right_arm")
    a["slam"] = slam2(1.3, arms=("right_arm", "left_arm"), wind=0.5, hit=0.66)
    a["roar"] = roar(1.6)
    save_anims("ogre", a)


# =====================================================================================
#  TROLL
# =====================================================================================
def build_troll():
    m = Model("troll")
    skin, skd, moss, moss_d, nail = "#7E8C76", "#5E6B57", "#4E7A2A", "#335219", "#D9D3B8"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 16, 0), "root")
    m.cube("body", (-6, 16, -4), (12, 15, 8), P(skin, deco=chain(
        speckle(skd, 0.12, 21), lambda fs: fs["front"].rect(2, 3, 9, 3, darken(H(skin), 0.15)),
        lambda fs: [fs["front"].row(r, darken(H(skin), 0.1)) for r in (6, 8, 10)],
        bottom_rows(3, "#5A4A36"))))
    m.cube("body", (-6.5, 27, -2), (13, 7, 9), P(moss, deco=chain(fur(moss_d, "#6D9A3D", 22), speckle("#8AA34A", 0.05, 3))))
    m.bone("head", (0, 31, -5), "body")

    def face(fs):
        f = fs["front"]
        f.rect(0, 1, 7, 1, H(moss_d))
        f.set(1, 2, H("#E8D84A"), True)
        f.set(6, 2, H("#E8D84A"), True)
        f.set(2, 2, darken(H(skin), 0.5))
        f.set(5, 2, darken(H(skin), 0.5))
        f.rect(1, 6, 6, 6, darken(H(skin), 0.5))
        f.set(1, 5, H(nail))
        f.set(6, 5, H(nail))
    m.cube("head", (-4, 29, -11), (8, 8, 7), P(skin, deco=chain(face, speckle(skd, 0.1, 30, faces=("left", "right", "top")))))
    m.cube("head", (-1, 30, -15), (2, 5, 4), P(skd, deco=lambda fs: fs["front"].set(0, 4, H("#3A2A1E"))),
           rotation=[-15, 0, 0], pivot=[0, 34, -11])
    m.cube("head", (-4.5, 36, -11.5), (9, 2, 8), P(moss, deco=fur(moss_d, "#6D9A3D", 31)), inflate=0.2)
    m.cube("head", (-6, 33, -8), (2, 3, 1), P(skin), rotation=[0, 30, -20], pivot=[-4, 34, -8])
    m.cube("head", (4, 33, -8), (2, 3, 1), P(skin), rotation=[0, -30, 20], pivot=[4, 34, -8])
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (7.5 * sx, 29, -1), "body")
        ox = -11 if sx < 0 else 6
        m.cube(side, (ox, 8, -3.5), (5, 22, 5), P(skin, deco=chain(
            speckle(skd, 0.12, 40 + sx), top_rows(4, moss, faces=("front", "back", "left", "right")),
            lambda fs: [fs[n].row(fs[n].h - 1, H(nail)) for n in ("front",)],
            lambda fs: [fs["front"].set(x, fs["front"].h - 2, H(nail)) for x in (0, 2, 4)])))
    for side, x in (("right_leg", -5.5), ("left_leg", 0.5)):
        m.bone(side, (x + 2.5, 16, 0), "root")
        m.cube(side, (x, 0, -2.5), (5, 16, 5), P(skin, deco=chain(
            top_rows(6, "#5A4A36"), speckle(skd, 0.12, 50),
            lambda fs: [fs["front"].set(xx, fs["front"].h - 1, H(nail)) for xx in (0, 2, 4)])))
    m.layout()
    m.paint(51)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=1.3, leg=24, arm=30, run_len=0.8, run_leg=38, run_arm=45, bob=0.9, lean=10)
    a["attack"] = {"loop": False, "animation_length": 0.7, "bones": {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-120, 0, -50]), (0.42, [-30, 0, 30]), (0.7, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-20, 0, -10]), (0.7, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.25, [0, 20, 0]), (0.42, [10, -20, 0]), (0.7, [0, 0, 0])])},
    }}
    a["roar"] = roar(1.4)
    save_anims("troll", a)


# =====================================================================================
#  LIZARDMAN
# =====================================================================================
def build_lizardman():
    m = Model("lizardman")
    sc, scd, belly, crest, wood, iron = "#4F8A3A", "#33602A", "#D7C46A", "#D2552A", "#7B5530", "#B9BFC5"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 12, 0), "root")
    m.cube("body", (-4, 12, -2.5), (8, 11, 5), P(sc, deco=chain(
        scales(scd, faces=("back", "left", "right")),
        lambda fs: fs["front"].rect(2, 0, 5, fs["front"].h - 1, H(belly)),
        lambda fs: [fs["front"].row(r, darken(H(belly), 0.2)) for r in (2, 5, 8)],
        lambda fs: [fs["front"].set(i, 9 - i, H("#5A3A22")) for i in range(8)],
        band([-2], "#5A3A22"))))
    m.cube("body", (-3, 7, -2.8), (6, 5, 1), P("#6A4A2A", deco=speckle("#3E2A18", 0.2, 3)))
    m.bone("tail", (0, 13, 2.5), "body")
    m.cube("tail", (-1.5, 10.5, 2.5), (3, 3, 7), P(sc, deco=scales(scd)))
    m.bone("tail2", (0, 11.5, 9.5), "tail")
    m.cube("tail2", (-1, 10, 9.5), (2, 2, 6), P(sc, deco=scales(scd)))
    m.bone("tail3", (0, 10.5, 15.5), "tail2")
    m.cube("tail3", (-0.5, 9.5, 15.5), (1, 1, 5), P(scd))
    m.bone("head", (0, 23, 0), "body")

    def skull(fs):
        for n in ("left", "right"):
            f = fs[n]
            f.set(f.w - 2 if n == "right" else 1, 1, H("#F2C230"), True)
            f.set(f.w - 2 if n == "right" else 1, 2, H("#1A1A10"))
        fs["front"].set(0, 1, H("#F2C230"), True)
        fs["front"].set(5, 1, H("#F2C230"), True)
    m.cube("head", (-3, 23, -3), (6, 5, 6), P(sc, deco=chain(scales(scd, faces=("top", "back")), skull)))
    m.cube("head", (-2, 23, -9), (4, 3, 6), P(sc, faces={"bottom": belly}, deco=chain(
        lambda fs: [fs[n].row(2, H("#F4EEDC")) for n in ("left", "right")],
        lambda fs: [fs["front"].set(x, 0, H("#1A1A10")) for x in (0, 3)],
        lambda fs: fs["front"].row(2, darken(H(sc), 0.3)))))
    m.cube("head", (-0.5, 27, -4), (1, 4, 8), P(crest, deco=lambda fs: [fs[n].tint(
        lambda c, x, y: darken(c, 0.25) if x % 2 else c) for n in ("left", "right")]))
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (4.5 * sx, 22, 0), "body")
        ox = -7 if sx < 0 else 4
        m.cube(side, (ox, 11, -1.5), (3, 11, 3), P(sc, deco=chain(scales(scd), bottom_rows(1, "#E8E0C8", faces=("front",)))))
    m.bone("spear", (-5.5, 12, 0), "right_arm")
    m.cube("spear", (-6, 11.5, -17), (1, 1, 25), P(wood, deco=band([0], "#5A3A1C")))
    m.cube("spear", (-6, 10.5, -22), (1, 3, 5), P(iron, deco=lambda fs: [fs[n].col(0, lighten(H(iron), 0.4)) for n in ("left", "right")]))
    m.cube("spear", (-6.2, 10, -17.5), (1, 2, 1), P(crest), inflate=0.1)
    for side, x in (("right_leg", -3.5), ("left_leg", 0.5)):
        m.bone(side, (x + 1.5, 12, 0), "root")
        m.cube(side, (x, 1, -1.5), (3, 11, 3), P(sc, deco=scales(scd)))
        m.cube(side, (x, 0, -4), (3, 1, 5), P(scd, deco=lambda fs: fs["front"].fill(H("#E8E0C8"))))
    m.layout()
    m.paint(61)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=0.9, leg=30, arm=20, run_len=0.55, bob=0.5, tails=("tail", "tail2", "tail3"),
                       still_arm="right_arm")
    a["attack"] = {"loop": False, "animation_length": 0.55, "bones": {
        "right_arm": {"rotation": keys([(0, [-15, 0, 0]), (0.18, [-70, 0, 0]), (0.3, [-85, 0, 0]), (0.55, [-15, 0, 0])]),
                      "position": keys([(0, [0, 0, 0]), (0.18, [0, 0, 2]), (0.3, [0, 0, -4]), (0.55, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.18, [0, 10, 0]), (0.3, [8, -10, 0]), (0.55, [0, 0, 0])])},
    }}
    a["swim"] = {"loop": True, "animation_length": 1.0, "bones": {
        "body": {"rotation": cyc(1.0, lambda p: [70, 6 * S(p), 0])},
        "right_leg": {"rotation": cyc(1.0, lambda p: [70 + 25 * S(p), 0, 0])},
        "left_leg": {"rotation": cyc(1.0, lambda p: [70 - 25 * S(p), 0, 0])},
        "tail": {"rotation": cyc(1.0, lambda p: [0, 25 * S(p), 0])},
        "tail2": {"rotation": cyc(1.0, lambda p: [0, 30 * S(p - 0.1), 0])},
        "tail3": {"rotation": cyc(1.0, lambda p: [0, 35 * S(p - 0.2), 0])},
        "left_arm": {"rotation": cyc(1.0, lambda p: [-20 + 30 * S(p), 0, -20])},
    }}
    save_anims("lizardman", a)


# =====================================================================================
#  MINOTAUR
# =====================================================================================
def build_minotaur():
    m = Model("minotaur")
    fur_c, fur_d, skin, horn, hoof = "#6B4326", "#4A2C17", "#8C5A36", "#E6DCC2", "#2A2018"
    gold, leather, iron = "#D4AF37", "#4A321C", "#9EA6AE"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 18, 0), "root")
    m.cube("body", (-6, 18, -4), (12, 6, 8), P(skin, deco=chain(
        lambda fs: [fs[n].rect(0, 0, fs[n].w - 1, 2, H(leather)) for n in ("front", "back", "left", "right")],
        lambda fs: fs["front"].rect(4, 0, 7, 2, H(gold)))))

    def chest(fs):
        f = fs["front"]
        f.rect(2, 3, 6, 3, darken(H(skin), 0.2))
        f.rect(9, 3, 13, 3, darken(H(skin), 0.2))
        for r in (6, 8, 10):
            f.rect(5, r, 10, r, darken(H(skin), 0.12))
        f.rect(0, 0, f.w - 1, 2, H(fur_c))
        for n in ("back", "left", "right", "top"):
            fs[n].tint(lambda c, x, y: H(fur_c) if y < 6 else c)
        fur(fur_d, "#8A5A34", 71, faces=("back", "top"))(fs)
    m.cube("body", (-8, 24, -5), (16, 12, 10), P(skin, deco=chest))
    m.cube("body", (-4, 9, -4.5), (8, 9, 1), P(leather, deco=chain(speckle("#2E1E10", 0.15, 7), band([0], gold, faces=("front",)))))
    m.bone("head", (0, 35, -3), "body")

    def face(fs):
        f = fs["front"]
        f.rect(0, 2, 1, 2, H("#1C0F08"))
        f.rect(6, 2, 7, 2, H("#1C0F08"))
        f.set(1, 3, H("#E8322A"), True)
        f.set(6, 3, H("#E8322A"), True)
        f.rect(2, 0, 5, 1, H(fur_d))
    m.cube("head", (-4, 34, -9), (8, 8, 9), P(fur_c, deco=chain(fur(fur_d, "#8A5A34", 80, faces=("top", "left", "right", "back")), face)))
    m.cube("head", (-3, 33, -12), (6, 5, 3), P("#9C6A44", deco=chain(
        lambda fs: [fs["front"].set(x, 2, H("#1C0F08")) for x in (1, 4)],
        lambda fs: fs["front"].row(4, darken(H("#9C6A44"), 0.3)))))
    m.cube("head", (-1, 31.5, -12.5), (2, 2, 1), P(gold, noise=0.03, deco=lambda fs: fs["front"].set(0, 0, (0, 0, 0, 0))))
    for sx in (-1, 1):
        x1 = -8 if sx < 0 else 4
        m.cube("head", (x1, 39, -6), (4, 2, 2), P(horn, deco=lambda fs: [fs[n].col(0, darken(H(horn), 0.15)) for n in ("front", "back")]))
        x2 = -10 if sx < 0 else 8
        m.cube("head", (x2, 39, -6), (2, 5, 2), P(horn), rotation=[0, 0, -15 * sx], pivot=[x2 + 1, 39, -5])
        x3 = -10.5 if sx < 0 else 9.5
        m.cube("head", (x3, 43.5, -6.5), (1, 3, 1), P("#F6F0DE"), rotation=[-10, 0, -25 * sx], pivot=[x3 + 0.5, 43.5, -6])
        x4 = -6.5 if sx < 0 else 4.5
        m.cube("head", (x4, 37, -2), (2, 1, 2), P(fur_c), rotation=[0, 0, 25 * sx], pivot=[x4 + 1, 37.5, -1])
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (10 * sx, 34, 0), "body")
        ox = -14 if sx < 0 else 8
        m.cube(side, (ox, 18, -3), (6, 17, 6), P(skin, deco=chain(
            top_rows(5, fur_c, faces=("front", "back", "left", "right", "top")), band([-4, -3], gold),
            band([-2], darken(H(gold), 0.3)), fur(fur_d, "#8A5A34", 90 + sx, faces=("top",)))))
    m.bone("axe", (-11, 19, 0), "right_arm")
    m.cube("axe", (-12, 18, -18), (2, 2, 22), P(leather, deco=chain(speckle("#2E1E10", 0.2, 3), band([0, 1], gold))))
    m.cube("axe", (-11.5, 9, -21), (1, 20, 6), P(iron, deco=chain(
        lambda fs: [fs[n].tint(lambda c, x, y, h=fs[n].h: lighten(c, 0.35) if x == 0 else (darken(c, 0.2) if x >= fs[n].w - 1 else c))
                    for n in ("left", "right")],
        lambda fs: [fs[n].rect(1, 9, 4, 10, H(gold)) for n in ("left", "right")],
        lambda fs: [fs[n].tint(lambda c, x, y, h=fs[n].h: (0, 0, 0, 0) if (x >= 4 and (y < 3 or y > h - 4)) else c)
                    for n in ("left", "right")])))
    m.cube("axe", (-11.5, 17.5, -24), (1, 3, 3), P(iron, deco=lambda fs: [fs[n].col(0, lighten(H(iron), 0.4)) for n in ("left", "right")]))
    for side, x in (("right_leg", -7), ("left_leg", 1)):
        m.bone(side, (x + 3, 18, 0), "root")
        m.cube(side, (x, 3, -3), (6, 15, 6), P(fur_c, deco=fur(fur_d, "#8A5A34", 100 + int(x))))
        m.cube(side, (x - 0.5, 0, -4), (7, 3, 7), P(hoof, noise=0.04, deco=lambda fs: fs["front"].col(3, H("#140E0A"))))
    m.layout()
    m.paint(71)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=1.2, leg=26, arm=16, run_len=0.7, run_leg=40, run_arm=30, bob=0.8, lean=10,
                       still_arm="right_arm")
    a["attack"] = {"loop": False, "animation_length": 0.8, "bones": {
        "right_arm": {"rotation": keys([(0, [-20, 0, 0]), (0.3, [-95, -40, -70]), (0.5, [-75, 50, 40]), (0.8, [-20, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [0, 30, 0]), (0.5, [5, -30, 0]), (0.8, [0, 0, 0])])},
    }}
    a["charge_prep"] = {"loop": False, "animation_length": 1.25, "bones": {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.3, [25, 0, 0]), (1.25, [25, 0, 0])])},
        "right_leg": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-25, 0, 0]), (0.45, [10, 0, 0]), (0.6, [-25, 0, 0]),
                                        (0.75, [10, 0, 0]), (0.9, [-25, 0, 0]), (1.05, [10, 0, 0]), (1.25, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [-20, 0, 0]), (0.3, [-10, 0, -30]), (1.25, [-10, 0, -30])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.3, [-10, 0, 30]), (1.25, [-10, 0, 30])])},
    }}
    a["charge"] = {"loop": True, "animation_length": 0.5, "bones": {
        "body": {"rotation": cyc(0.5, lambda p: [30, 0, 0], steps=2), "position": cyc(0.5, lambda p: [0, 1.0 * abs(C(p)), 0])},
        "right_leg": {"rotation": cyc(0.5, lambda p: [55 * S(p), 0, 0])},
        "left_leg": {"rotation": cyc(0.5, lambda p: [-55 * S(p), 0, 0])},
        "right_arm": {"rotation": cyc(0.5, lambda p: [10, 0, -35], steps=2)},
        "left_arm": {"rotation": cyc(0.5, lambda p: [10, 0, 35], steps=2)},
    }}
    a["stunned"] = {"loop": True, "animation_length": 1.0, "bones": {
        "body": {"rotation": cyc(1.0, lambda p: [8 + 4 * S(p), 0, 8 * C(p)])},
        "right_arm": {"rotation": cyc(1.0, lambda p: [5, 0, -6 + 4 * S(p)])},
        "left_arm": {"rotation": cyc(1.0, lambda p: [5, 0, 6 - 4 * S(p)])},
    }}
    a["stomp"] = {"loop": False, "animation_length": 0.9, "bones": {
        "right_leg": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-60, 0, 0]), (0.5, [5, 0, 0]), (0.9, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-10, 0, 0]), (0.5, [12, 0, 0]), (0.9, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [-20, 0, 0]), (0.35, [-60, 0, -20]), (0.5, [-10, 0, 0]), (0.9, [-20, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-60, 0, 20]), (0.5, [-10, 0, 0]), (0.9, [0, 0, 0])])},
    }}
    a["roar"] = roar(2.5)
    save_anims("minotaur", a)


# =====================================================================================
#  WEREWOLF
# =====================================================================================
def build_werewolf():
    m = Model("werewolf")
    fc, fd, fl, claw, nose = "#5C564F", "#3B3631", "#8A847A", "#E8E2D0", "#1A1614"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 15, 0), "root")
    m.cube("body", (-4, 15, -2.5), (8, 6, 5), P(fc, deco=chain(fur(fd, fl, 1), lambda fs: fs["front"].rect(2, 0, 5, 5, H(fl)),
                                                               bottom_rows(2, "#3E2C1E", faces=("front", "back", "left", "right")))))
    m.cube("body", (-5.5, 21, -4), (11, 9, 7), P(fc, deco=chain(fur(fd, fl, 2), lambda fs: fs["front"].rect(3, 2, 7, 8, H(fl)),
                                                                lambda fs: fs["front"].scatter(H(fc), 0.25, 3))))
    m.cube("body", (-4.5, 27, -2), (9, 4, 5), P(fd, deco=fur("#2A2622", fc, 3)), inflate=0.3)
    m.bone("tail", (0, 18, 2.5), "body")
    m.cube("tail", (-1.5, 15.5, 2.5), (3, 3, 9), P(fc, deco=chain(fur(fd, fl, 4), lambda fs: fs["back"].fill(H(fl)))),
           rotation=[-25, 0, 0], pivot=[0, 18, 2.5])
    m.bone("head", (0, 29, -3), "body")

    def face(fs):
        f = fs["front"]
        f.set(1, 2, H("#FFD84A"), True)
        f.set(5, 2, H("#FFD84A"), True)
        f.set(2, 2, H("#FFF2A0"), True)
        f.set(4, 2, H("#FFF2A0"), True)
        f.rect(1, 1, 2, 1, H(fd))
        f.rect(4, 1, 5, 1, H(fd))
    m.cube("head", (-3.5, 28, -8), (7, 6, 6), P(fc, deco=chain(fur(fd, fl, 5, faces=("top", "back", "left", "right")), face)))
    m.cube("head", (-2, 28, -13), (4, 3, 5), P(fl, deco=chain(
        lambda fs: fs["front"].rect(1, 0, 2, 0, H(nose)),
        lambda fs: [fs[n].row(2, H(claw)) for n in ("left", "right", "front")],
        lambda fs: [fs[n].set(x, 2, H("#7A1E1E")) for n in ("left", "right") for x in (1, 3)])))
    m.cube("head", (-3.5, 34, -5), (2, 3, 1), P(fd, deco=lambda fs: fs["front"].set(0, 1, H("#8A5A5A"))))
    m.cube("head", (1.5, 34, -5), (2, 3, 1), P(fd, deco=lambda fs: fs["front"].set(1, 1, H("#8A5A5A"))))
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (6.5 * sx, 28, 0), "body")
        ox = -8.5 if sx < 0 else 5.5
        m.cube(side, (ox, 13, -1.5), (3, 15, 3), P(fc, deco=chain(fur(fd, fl, 10 + sx),
                                                                   lambda fs: [fs[n].row(fs[n].h - 1, H(claw)) for n in ("front", "left", "right")])))
    for side, x in (("right_leg", -4.5), ("left_leg", 0.5)):
        m.bone(side, (x + 2, 15, 0), "root")
        m.cube(side, (x, 2, -1.5), (4, 13, 4), P(fc, deco=chain(fur(fd, fl, 20 + int(x)), top_rows(3, "#3E2C1E", faces=("front", "back", "left", "right")))))
        m.cube(side, (x, 0, -4), (4, 2, 6), P(fd, deco=lambda fs: [fs["front"].set(xx, 1, H(claw)) for xx in (0, 2, 3)]))
    m.layout()
    m.paint(81)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=0.9, leg=30, arm=26, run_len=0.45, run_leg=55, run_arm=55, bob=0.6, lean=22, tails=("tail",))
    a["attack"] = {"loop": False, "animation_length": 0.55, "bones": {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.18, [-130, 0, -30]), (0.3, [-20, 0, 20]), (0.55, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-20, 0, -10]), (0.38, [-130, 0, 30]), (0.48, [-20, 0, -20]), (0.55, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.18, [10, 15, 0]), (0.38, [14, -15, 0]), (0.55, [0, 0, 0])])},
    }}
    a["howl"] = {"loop": False, "animation_length": 2.2, "bones": {
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-25, 0, 0]), (1.9, [-28, 0, 0]), (2.2, [0, 0, 0])])},
        "head": {"position": keys([(0, [0, 0, 0]), (0.35, [0, 1, 1]), (1.9, [0, 1, 1]), (2.2, [0, 0, 0])])},
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-20, 0, 35]), (1.9, [-20, 0, 40]), (2.2, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.35, [-20, 0, -35]), (1.9, [-20, 0, -40]), (2.2, [0, 0, 0])])},
        "tail": {"rotation": cyc(2.2, lambda p: [10, 15 * S(p * 4), 0], steps=16)},
    }}
    save_anims("werewolf", a)


# =====================================================================================
#  CULTIST (3 escolas: sombra, fogo, arcano)
# =====================================================================================
CULT = {
    "cultist": dict(robe="#2E1C40", trim="#8A4FC8", glow="#C77DFF", inner="#140A1E"),
    "cultist_fire": dict(robe="#4A1510", trim="#FF6A2B", glow="#FFB347", inner="#1E0806"),
    "cultist_arcane": dict(robe="#1B2350", trim="#5ED3F3", glow="#9FF3FF", inner="#0A0E24"),
}


def cultist_model(v):
    m = Model("cultist")
    robe, trim, glow, inner = v["robe"], v["trim"], v["glow"], v["inner"]
    hand = "#B9AFA6"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 12, 0), "root")

    def torso(fs):
        f = fs["front"]
        f.col(3, H(trim))
        f.col(4, H(trim))
        f.rect(2, 3, 5, 6, H(inner))
        for (x, y) in ((3, 3), (4, 3), (2, 4), (5, 4), (3, 5), (4, 5), (3, 6), (4, 6)):
            f.set(x, y, H(glow), True)
        band([-1], trim)(fs)
    m.cube("body", (-4, 12, -2), (8, 12, 4), P(robe, deco=torso))
    m.cube("body", (-4.5, 3, -2.5), (9, 9, 5), P(robe, deco=chain(
        bottom_rows(2, trim, faces=("front", "back", "left", "right")),
        lambda fs: fs["front"].col(4, darken(H(robe), 0.35)),
        lambda fs: [fs[n].tint(lambda c, x, y: darken(c, 0.15) if x % 3 == 1 else c) for n in ("left", "right", "back")])))
    m.bone("head", (0, 24, 0), "body")

    def hood(fs):
        f = fs["front"]
        f.rect(1, 1, 6, 7, H(inner))
        f.rect(2, 2, 5, 7, darken(H(inner), 0.4))
        f.set(2, 4, H(glow), True)
        f.set(5, 4, H(glow), True)
        f.row(0, H(trim))
        f.col(0, H(trim))
        f.col(7, H(trim))
    m.cube("head", (-4, 24, -4), (8, 8, 8), P(robe, deco=hood), inflate=0.3)
    m.cube("head", (-2, 29, 3), (4, 3, 3), P(robe), rotation=[25, 0, 0], pivot=[0, 30, 3])
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (5 * sx, 22, 0), "body")
        ox = -8 if sx < 0 else 4
        m.cube(side, (ox, 12, -2), (4, 12, 4), P(robe, deco=chain(
            bottom_rows(2, hand, faces=("front", "back", "left", "right", "bottom")), band([-3], trim))))
    for side, x in (("right_leg", -4), ("left_leg", 0)):
        m.bone(side, (x + 2, 12, 0), "root")
        m.cube(side, (x, 0, -2), (4, 12, 4), P(darken(H(robe), 0.4), deco=bottom_rows(3, "#221A14")))
    m.layout()
    m.paint(91)
    return m


def build_cultist():
    first = True
    for name, v in CULT.items():
        m = cultist_model(v)
        if first:
            m.save_geo()
            first = False
        m.save_texture(name, glow=True)
    a = humanoid_anims(walk_len=1.1, leg=18, arm=14, run_len=0.7, run_leg=30, run_arm=20, bob=0.3, lean=6)
    a["cast"] = {"loop": True, "animation_length": 1.0, "bones": {
        "right_arm": {"rotation": cyc(1.0, lambda p: [-120 + 8 * S(p), 0, 20 + 6 * C(p)])},
        "left_arm": {"rotation": cyc(1.0, lambda p: [-120 + 8 * S(p), 0, -20 - 6 * C(p)])},
        "body": {"rotation": cyc(1.0, lambda p: [-6, 0, 0], steps=2)},
    }}
    a["release"] = {"loop": False, "animation_length": 0.45, "bones": {
        "right_arm": {"rotation": keys([(0, [-120, 0, 20]), (0.15, [-90, 0, 0]), (0.45, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [-120, 0, -20]), (0.15, [-90, 0, 0]), (0.45, [0, 0, 0])])},
    }}
    save_anims("cultist", a)


# =====================================================================================
#  WANDERING MAGE
# =====================================================================================
def build_wandering_mage():
    m = Model("wandering_mage")
    robe, trim, skin, beard, hat = "#2F5D8A", "#E8C25A", "#E2B48C", "#EDEDE6", "#28456B"
    wood, crystal, bag = "#6B4A2B", "#6FE3FF", "#7A5634"
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 12, 0), "root")
    m.cube("body", (-4, 12, -2), (8, 12, 4), P(robe, deco=chain(
        lambda fs: fs["front"].col(3, H(trim)), lambda fs: fs["front"].col(4, H(trim)),
        band([2], "#5A3A20"), lambda fs: fs["front"].rect(5, 3, 6, 4, H(bag)))))
    m.cube("body", (-4.5, 2, -2.5), (9, 10, 5), P(robe, deco=chain(
        bottom_rows(2, trim, faces=("front", "back", "left", "right")),
        lambda fs: [fs[n].tint(lambda c, x, y: darken(c, 0.12) if x % 3 == 2 else c) for n in ("left", "right", "back", "front")])))
    m.cube("body", (-3, 14, 2), (6, 8, 3), P(bag, deco=chain(band([1], "#4A321C"), lambda fs: fs["back"].rect(2, 3, 3, 4, H(trim)))))
    m.cube("body", (-3.5, 21, 2.5), (7, 2, 2), P("#D9C8A0", deco=lambda fs: fs["left"].fill(H("#B23A2A"))))
    m.bone("head", (0, 24, 0), "body")

    def face(fs):
        f = fs["front"]
        f.row(2, H(beard))
        f.set(2, 3, H("#3A6EA5"))
        f.set(5, 3, H("#3A6EA5"))
        f.set(1, 3, H("#FFFFFF"))
        f.set(6, 3, H("#FFFFFF"))
        f.rect(3, 4, 4, 5, darken(H(skin), 0.12))
        f.rect(1, 6, 6, 7, H(beard))
        for n in ("left", "right"):
            fs[n].rect(0, 3, fs[n].w - 1, 7, H(beard))
            fs[n].rect(0, 0, fs[n].w - 1, 2, H("#D8D8D0"))
        fs["back"].fill(H("#D8D8D0"))
    m.cube("head", (-4, 24, -4), (8, 8, 8), P(skin, deco=face))
    m.cube("head", (-3, 18, -4.5), (6, 6, 2), P(beard, noise=0.04, deco=lambda fs: fs["front"].tint(
        lambda c, x, y: darken(c, 0.08) if (x + y) % 3 == 0 else c)))
    m.cube("head", (-6, 31.5, -6), (12, 1, 12), P(hat, deco=chain(band([0], trim), lambda fs: fs["top"].tint(
        lambda c, x, y: H(trim) if (x, y) in ((2, 2), (9, 3), (3, 9), (8, 8)) else c))))
    m.cube("head", (-4, 32.5, -4), (8, 4, 8), P(hat, deco=chain(band([0], trim), lambda fs: fs["front"].set(3, 2, H(trim), True),
                                                                lambda fs: fs["front"].set(4, 2, H("#FFF4C0"), True))))
    m.cube("head", (-3, 36.5, -2.5), (6, 4, 6), P(hat, deco=lambda fs: fs["left"].set(2, 1, H(trim))))
    m.cube("head", (-2, 40, -0.5), (4, 3, 4), P(hat), rotation=[-15, 0, 0], pivot=[0, 40, 1])
    m.cube("head", (-1, 42, 2), (2, 2, 3), P(hat, deco=lambda fs: fs["back"].fill(H(trim))), rotation=[-35, 0, 0], pivot=[0, 42.5, 2])
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (5 * sx, 22, 0), "body")
        ox = -8 if sx < 0 else 4
        m.cube(side, (ox, 12, -2), (4, 12, 4), P(robe, deco=chain(bottom_rows(2, skin, faces=("front", "back", "left", "right", "bottom")),
                                                                   band([-3, -4], trim))))
    m.bone("staff", (-6, 13, -3), "right_arm")
    m.cube("staff", (-6.5, 1, -3.5), (1, 28, 1), P(wood, deco=band([8, 9], "#4A321C")))
    m.cube("staff", (-7.5, 29, -4.5), (3, 2, 3), P(wood, deco=band([0], trim)))

    def gem(fs):
        for n, f in fs.items():
            f.tint(lambda c, x, y: c)
            for yy in range(f.h):
                for xx in range(f.w):
                    f.set(xx, yy, f.get(xx, yy), True)
            f.set(0, 0, H("#E6FFFF"), True)
    m.cube("staff", (-7.5, 31, -4.5), (3, 4, 3), P(crystal, noise=0.1, deco=gem))
    for side, x in (("right_leg", -4), ("left_leg", 0)):
        m.bone(side, (x + 2, 12, 0), "root")
        m.cube(side, (x, 0, -2), (4, 12, 4), P("#3A3A4A", deco=bottom_rows(3, "#4A321C")))
    m.layout()
    m.paint(101)
    m.save_geo()
    m.save_texture(glow=True)
    a = humanoid_anims(walk_len=1.2, leg=20, arm=16, run_len=0.6, run_leg=40, run_arm=30, bob=0.3, lean=8, still_arm="right_arm")
    a["cast"] = {"loop": False, "animation_length": 0.8, "bones": {
        "right_arm": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-150, 0, 0]), (0.5, [-150, 0, 0]), (0.8, [0, 0, 0])])},
        "left_arm": {"rotation": keys([(0, [0, 0, 0]), (0.25, [-60, 0, -30]), (0.8, [0, 0, 0])])},
    }}
    save_anims("wandering_mage", a)


# =====================================================================================
#  ITENS (16x16)
# =====================================================================================
def tex_item(name, fn):
    c = Canvas(16, 16)
    fn(c)
    c.save(os.path.join(ASSETS, "textures/item", name + ".png"))


def draw_tusk(c):
    pts = [(4, 13), (5, 12), (6, 11), (7, 10), (8, 9), (9, 8), (10, 6), (10, 5), (11, 4), (11, 3)]
    for i, (x, y) in enumerate(pts):
        w = 2 if i < 6 else 1
        for dx in range(w + 1):
            c.set(x + dx, y, H("#EFE8CF"))
            c.set(x + dx, y + 1, H("#D8CFAE"))
    c.rect(3, 12, 6, 14, H("#B9AE88"))
    c.rect(3, 13, 4, 14, H("#8A7D5A"))
    c.set(11, 2, H("#FFFFFF"))
    c.set(10, 4, H("#FFFFFF"))
    c.outline(H("#3C3424"))


def draw_hide(c):
    rows = ["......####......",
            "...##########...",
            "..############..",
            ".##############.",
            ".##############.",
            "..############..",
            "..############..",
            "..###########...",
            "...##########...",
            "...#########....",
            "..###########...",
            ".#############..",
            ".####....#####..",
            "..##......###..."]
    c.from_rows(rows, {"#": H("#6F7E5E")}, 0, 1)
    c.noise(0.12, 4)
    for (x, y) in ((4, 4), (9, 6), (6, 9), (11, 3), (8, 12), (3, 7)):
        c.set(x, y, H("#4E7A2A"))
        c.set(x + 1, y, H("#6D9A3D"))
    c.outline(H("#2C3424"))


def draw_scale(c):
    c.circle(8, 8, 6, H("#4F8A3A"))
    c.shade_sphere(8, 8, 6, 0.4, 0.5)
    for y in range(4, 13, 3):
        for x in range(4 + (y % 2), 13, 3):
            c.set(x, y, H("#33602A"))
            c.set(x + 1, y, H("#7CB85A"))
    c.rect(7, 2, 8, 3, H("#D7C46A"))
    c.outline(H("#1E3A16"))


def draw_minotaur_horn(c):
    pts = [(3, 13), (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (11, 4), (12, 3), (12, 2)]
    for i, (x, y) in enumerate(pts):
        w = 3 if i < 4 else (2 if i < 9 else 1)
        for dx in range(w):
            col = H("#E6DCC2") if i > 3 else H("#8C7A5E")
            c.set(x + dx, y, col)
            c.set(x + dx, y + 1, darken(col, 0.15))
    c.set(12, 1, H("#FFFFFF"))
    c.rect(2, 13, 5, 14, H("#6B4326"))
    c.outline(H("#2A2018"))


def draw_fang(c):
    c.line(4, 3, 11, 3, H("#3B3631"))
    for i in range(3):
        x = 5 + i * 3
        c.set(x, 4, H("#E8E2D0"))
        c.set(x + 1, 4, H("#E8E2D0"))
        c.set(x, 5, H("#E8E2D0"))
        c.set(x + 1, 5, H("#D2CBB5"))
        c.set(x, 6, H("#D2CBB5"))
    c.rect(6, 6, 9, 10, H("#F2EEE2"))
    c.rect(7, 11, 8, 12, H("#E0D9C4"))
    c.set(7, 13, H("#C9C1A8"))
    c.set(6, 7, H("#FFFFFF"))
    c.set(6, 8, H("#FFFFFF"))
    c.outline(H("#2A2520"))


def draw_ogre_tooth(c):
    c.rect(5, 3, 10, 9, H("#E9DFC0"))
    c.rect(6, 10, 9, 12, H("#D9CDA6"))
    c.rect(7, 13, 8, 13, H("#C4B88E"))
    c.rect(5, 3, 6, 9, H("#F7F0DA"))
    c.rect(9, 5, 10, 9, H("#C9BC92"))
    c.set(8, 6, H("#A89A6E"))
    c.set(7, 8, H("#A89A6E"))
    c.outline(H("#3A3222"))


def draw_cleaver(c):
    for i in range(6):
        c.set(2 + i, 13 - i, H("#47301A"))
        c.set(3 + i, 13 - i, H("#6B4A2B"))
    c.set(2, 14, H("#D8B13A"))
    blade = ["....######",
             "...#######",
             "..########",
             ".#########",
             "##########",
             "#########.",
             "########..",
             "######...."]
    c.from_rows(blade, {"#": H("#4B4F55")}, 6, 1)
    for y in range(1, 9):
        for x in range(6, 16):
            p = c.get(x, y)
            if p[3] and (x + y) >= 19:
                c.set(x, y, H("#B9C0C7"))
    c.set(10, 3, H("#D8B13A"))
    c.set(9, 4, H("#B3241B"))
    c.outline(H("#1C1E22"))


def draw_labrys(c):
    for i in range(12):
        c.set(2 + i, 14 - i, H("#4A321C"))
    c.set(1, 15, H("#D4AF37"))
    # lamina dupla em volta da cabeca (11,3)
    for y in range(0, 8):
        for x in range(7, 16):
            dx, dy = x - 11, y - 3.5
            d1 = (dx - 2.5) ** 2 / 4.0 + dy ** 2 / 14.0
            d2 = (dx + 2.5) ** 2 / 4.0 + dy ** 2 / 14.0
            if (d1 < 1.0 and dx > 0.5) or (d2 < 1.0 and dx < -0.5):
                edge = d1 > 0.55 if dx > 0 else d2 > 0.55
                c.set(x, y, H("#D7DDE2") if edge else H("#9EA6AE"))
    c.rect(10, 3, 12, 4, H("#D4AF37"))
    c.outline(H("#1E2226"))


def draw_horn_item(c):
    pts = [(3, 11), (4, 10), (5, 10), (6, 9), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4)]
    for i, (x, y) in enumerate(pts):
        r = 1 + i // 3
        c.circle(x + 0.5, y + 0.5, r * 0.75 + 0.5, H("#E6D9B4"))
    c.circle(12.5, 4.5, 2.6, H("#C9A23A"))
    c.circle(12.5, 4.5, 1.2, H("#3A2A10"))
    for x in range(4, 10):
        c.set(x, 10 - (x - 4) // 2 + 1, H("#7A1E1E"))
    c.rect(2, 11, 4, 12, H("#C9A23A"))
    c.outline(H("#2E2614"))


def build_items():
    tex_item("orc_tusk", draw_tusk)
    tex_item("troll_hide", draw_hide)
    tex_item("lizard_scale", draw_scale)
    tex_item("minotaur_horn", draw_minotaur_horn)
    tex_item("werewolf_fang", draw_fang)
    tex_item("ogre_tooth", draw_ogre_tooth)
    tex_item("orc_lord_cleaver", draw_cleaver)
    tex_item("minotaur_labrys", draw_labrys)
    tex_item("orc_war_horn", draw_horn_item)
    for i in ("orc_tusk", "troll_hide", "lizard_scale", "minotaur_horn", "werewolf_fang", "ogre_tooth", "orc_war_horn"):
        item_model(ASSETS, i)
    for i in ("orc_lord_cleaver", "minotaur_labrys"):
        item_model(ASSETS, i, parent="minecraft:item/handheld")
    for e in ENTITIES:
        write_json(os.path.join(ASSETS, "models/item", e + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


ENTITIES = ["kobold", "orc", "orc_berserker", "orc_lord", "ogre", "troll", "lizardman", "minotaur", "werewolf",
            "cultist", "wandering_mage"]


# =====================================================================================
#  DADOS: loot tables, biome modifiers, receitas
# =====================================================================================
def item_entry(item, mn=1, mx=1, looting=0.0, weight=None):
    e = {"type": "minecraft:item", "name": item, "functions": []}
    if mn != 1 or mx != 1:
        e["functions"].append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": mn, "max": mx}})
    if looting:
        e["functions"].append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                               "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    if not e["functions"]:
        del e["functions"]
    if weight:
        e["weight"] = weight
    return e


def pool(entries, rolls=1, chance=None, player=False, looting_bonus=0.0):
    p = {"rolls": rolls, "bonus_rolls": 0, "entries": entries}
    cond = []
    if player:
        cond.append({"condition": "minecraft:killed_by_player"})
    if chance is not None:
        if looting_bonus:
            cond.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                         "unenchanted_chance": chance,
                         "enchanted_chance": {"type": "minecraft:linear", "base": chance + looting_bonus, "per_level_above_first": looting_bonus}})
        else:
            cond.append({"condition": "minecraft:random_chance", "chance": chance})
    if cond:
        p["conditions"] = cond
    return p


def loot(name, pools):
    write_json(os.path.join(DATA, "loot_table/entities", name + ".json"),
               {"type": "minecraft:entity", "pools": pools, "random_sequence": NS + ":entities/" + name})


M = "minecraft:"
I = NS + ":"


def build_loot():
    loot("kobold", [
        pool([item_entry(M + "coal", 0, 2, 1)]),
        pool([item_entry(M + "raw_copper", 1, 2, 1, weight=6), item_entry(M + "raw_iron", 1, 1, 1, weight=3),
              item_entry(M + "raw_gold", 1, 1, 0, weight=1)], chance=0.35, looting_bonus=0.05),
    ])
    loot("orc", [
        pool([item_entry(I + "orc_tusk", 0, 2, 1)]),
        pool([item_entry(M + "leather", 0, 1, 1)]),
        pool([item_entry(M + "iron_nugget", 1, 3, 1)], chance=0.5),
        pool([item_entry(M + "emerald")], chance=0.06, player=True, looting_bonus=0.02),
    ])
    loot("orc_berserker", [
        pool([item_entry(I + "orc_tusk", 1, 2, 1)]),
        pool([item_entry(M + "leather", 0, 2, 1)]),
        pool([item_entry(M + "bone", 0, 2, 1)]),
        pool([item_entry(I + "orc_war_horn")], chance=0.04, player=True, looting_bonus=0.01),
    ])
    loot("orc_lord", [
        pool([item_entry(I + "orc_lord_cleaver")]),
        pool([item_entry(I + "orc_tusk", 3, 5, 1)]),
        pool([item_entry(M + "gold_ingot", 2, 5, 1)]),
        pool([item_entry(M + "emerald", 3, 7, 2)]),
        pool([item_entry(I + "mana_crystal", 1, 2)]),
        pool([item_entry(I + "essence_earth", 1, 2)]),
    ])
    loot("ogre", [
        pool([item_entry(I + "ogre_tooth", 1, 2, 1)]),
        pool([item_entry(M + "leather", 1, 3, 1)]),
        pool([item_entry(M + "bone", 1, 3, 1)]),
        pool([item_entry(M + "emerald", 1, 2)], chance=0.2, player=True, looting_bonus=0.05),
    ])
    loot("troll", [
        pool([item_entry(I + "troll_hide", 1, 2, 1)]),
        pool([item_entry(M + "bone", 0, 2, 1)]),
        pool([item_entry(M + "moss_block", 1, 2)], chance=0.4),
    ])
    loot("lizardman", [
        pool([item_entry(I + "lizard_scale", 1, 3, 1)]),
        pool([item_entry(M + "cod", 0, 1, 1, weight=2), item_entry(M + "salmon", 0, 1, 1, weight=1)]),
        pool([item_entry(I + "essence_water")], chance=0.08, player=True, looting_bonus=0.02),
    ])
    loot("minotaur", [
        pool([item_entry(I + "minotaur_labrys")]),
        pool([item_entry(I + "minotaur_horn", 2, 2)]),
        pool([item_entry(M + "leather", 3, 6, 1)]),
        pool([item_entry(M + "gold_ingot", 3, 6, 1)]),
        pool([item_entry(I + "mana_crystal", 2, 3)]),
        pool([item_entry(I + "essence_earth", 2, 3)]),
    ])
    loot("werewolf", [
        pool([item_entry(I + "werewolf_fang", 1, 2, 1)]),
        pool([item_entry(M + "leather", 0, 2, 1)]),
        pool([item_entry(I + "essence_shadow")], chance=0.2, player=True, looting_bonus=0.05),
    ])
    loot("cultist", [
        pool([item_entry(M + "paper", 0, 2, 1)]),
        pool([item_entry(M + "book")], chance=0.1),
        pool([item_entry(I + "essence_shadow")], chance=0.25, player=True, looting_bonus=0.05),
        pool([item_entry(I + "essence_arcane")], chance=0.12, player=True, looting_bonus=0.03),
    ])
    loot("wandering_mage", [
        pool([item_entry(I + "essence_arcane")], chance=0.25),
        pool([item_entry(M + "glass_bottle", 1, 2)]),
    ])


def spawn_mod(name, biomes, entries):
    write_json(os.path.join(DATA, "neoforge/biome_modifier", name + ".json"),
               {"type": "neoforge:add_spawns", "biomes": biomes,
                "spawners": [{"type": I + e, "weight": w, "minCount": a, "maxCount": b} for e, w, a, b in entries]})


def anyof(*tags):
    return {"type": "neoforge:or", "values": list(tags)}


def build_spawns():
    # Kobolds: cavernas de todo o Overworld, menos o Abismo Profundo e os Campos de Cogumelo (sem monstros no vanilla).
    spawn_mod("kobold_spawns", {"type": "neoforge:and", "values": [
        "#minecraft:is_overworld",
        {"type": "neoforge:not", "value": ["minecraft:deep_dark", "minecraft:mushroom_fields"]}]},
        [("kobold", 25, 2, 4)])
    spawn_mod("orc_spawns", anyof("#c:is_plains", "#c:is_savanna", "#c:is_forest", "#c:is_taiga"),
              [("orc", 14, 2, 3), ("orc_berserker", 5, 1, 2)])
    spawn_mod("ogre_spawns", anyof("#c:is_mountain", "#c:is_windswept", "#c:is_hill", "#c:is_plateau"),
              [("ogre", 6, 1, 1)])
    spawn_mod("troll_spawns", anyof("#c:is_taiga", "#c:is_snowy", "#c:is_mountain", "#c:is_river"),
              [("troll", 6, 1, 1)])
    spawn_mod("lizardman_spawns", "#c:is_swamp", [("lizardman", 30, 2, 4)])
    spawn_mod("werewolf_spawns", anyof("#c:is_forest", "#c:is_taiga", "#c:is_plains"), [("werewolf", 8, 1, 1)])
    spawn_mod("cultist_spawns", anyof("#c:is_spooky", "#c:is_forest", "#c:is_plains", "#c:is_swamp"),
              [("cultist", 4, 1, 2)])


def build_recipes():
    rd = os.path.join(DATA, "recipe")
    write_json(os.path.join(rd, "orc_war_horn.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["T T", "TGT", " L "],
        "key": {"T": {"item": I + "orc_tusk"}, "G": {"item": M + "gold_ingot"}, "L": {"item": M + "leather"}},
        "result": {"id": I + "orc_war_horn", "count": 1}})
    write_json(os.path.join(rd, "troll_hide_to_leather.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": I + "troll_hide"}], "result": {"id": M + "leather", "count": 2}})
    write_json(os.path.join(rd, "ogre_tooth_to_bone_meal.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": I + "ogre_tooth"}], "result": {"id": M + "bone_meal", "count": 6}})
    write_json(os.path.join(rd, "lizard_scale_to_turtle_scute.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": I + "lizard_scale"}] * 4, "result": {"id": M + "turtle_scute", "count": 1}})


def build_tags():
    # Homem-lagarto respira debaixo d'agua; Lorde Orc e Minotauro sao chefes (tag comum c:bosses).
    write_json(os.path.join(MERGE, "data/minecraft/tags/entity_type/can_breathe_under_water.json"),
               {"replace": False, "values": [I + "lizardman"]})
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"),
               {"replace": False, "values": [I + "orc_lord", I + "minotaur"]})


if __name__ == "__main__":
    build_kobold()
    build_orcs()
    build_orc_lord()
    build_ogre()
    build_troll()
    build_lizardman()
    build_minotaur()
    build_werewolf()
    build_cultist()
    build_wandering_mage()
    build_items()
    build_loot()
    build_spawns()
    build_recipes()
    build_tags()
    print("creatures_b: assets gerados")
