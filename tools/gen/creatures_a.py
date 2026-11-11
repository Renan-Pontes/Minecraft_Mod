"""Gera os assets/dados do modulo "creatures_a" (Slimes e Goblins).

Uso (na raiz do projeto):  py -I tools/gen/creatures_a.py

Gera: modelos GeckoLib (.geo.json com UV por face) + texturas pintadas por codigo (com _glowmask onde brilha),
animacoes, texturas/modelos de item (coroas 3D usaveis na cabeca), loot tables, biome modifiers, receitas,
tags (merge) e as traducoes pt_br/en_us.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/creatures_a")
LANG = os.path.join(ROOT, "src/main/lang/creatures_a")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgba(c, a=None):
    if a is None:
        a = c[3] if len(c) > 3 else 255
    return (c[0], c[1], c[2], a)


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


# =================================================================================================
#  Construtor de modelos GeckoLib (UV por face, texturas pintadas por "pintores")
# =================================================================================================
SKIP = object()
FACE_KEYS = {
    "north": ("north", "front", "side", "all"),
    "south": ("south", "back", "side", "all"),
    "east": ("east", "lr", "side", "all"),
    "west": ("west", "lr", "side", "all"),
    "up": ("up", "cap", "all"),
    "down": ("down", "cap", "all"),
}


class TextureFull(Exception):
    pass


class Geo:
    def __init__(self, ident, tw=64, th=64, density=1):
        self.ident, self.tw, self.th, self.density = ident, tw, th, density
        self.tex = Canvas(tw, th)
        self.glow = Canvas(tw, th)
        self.has_glow = False
        self.bones = []
        self.px = self.py = self.row_h = 0
        self.cache = {}
        self.keep = []  # segura os pintores vivos: id() de objeto coletado pode ser reaproveitado

    def alloc(self, w, h):
        if self.px + w > self.tw:
            self.px, self.py, self.row_h = 0, self.py + self.row_h, 0
        if self.py + h > self.th or w > self.tw:
            raise TextureFull(self.ident)
        x, y = self.px, self.py
        self.px += w
        self.row_h = max(self.row_h, h)
        return x, y

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, faces, glow=False, inflate=None, rotation=None, pivot=None, density=None):
        dens = density or self.density
        w, h, d = size
        dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
        uv = {}
        for f in ("north", "east", "south", "west", "up", "down"):
            painter = None
            for k in FACE_KEYS[f]:
                if k in faces:
                    painter = faces[k]
                    break
            if painter is None or painter is SKIP:
                continue
            fw, fh = dims[f]
            if fw == 0 or fh == 0:
                continue
            tw, th = max(1, int(round(fw * dens))), max(1, int(round(fh * dens)))
            self.keep.append(painter)
            key = (id(painter), tw, th, f if getattr(painter, "per_face", False) else "")
            if key in self.cache:
                x, y = self.cache[key]
            else:
                x, y = self.alloc(tw, th)
                painter(self.tex, x, y, tw, th)
                self.cache[key] = (x, y)
                if glow:
                    self.has_glow = True
                    for yy in range(y, y + th):
                        for xx in range(x, x + tw):
                            self.glow.set(xx, yy, self.tex.get(xx, yy))
            uv[f] = {"uv": [x, y], "uv_size": [tw, th]}
        c = {"origin": [float(v) for v in origin], "size": [float(v) for v in size], "uv": uv}
        if inflate:
            c["inflate"] = inflate
        if rotation:
            c["rotation"] = [float(v) for v in rotation]
            c["pivot"] = [float(v) for v in (pivot or origin)]
        bone.setdefault("cubes", []).append(c)
        return c

    def save(self, bounds=(2, 2.5, 1.0)):
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.ident,
                    "texture_width": self.tw,
                    "texture_height": self.th,
                    "visible_bounds_width": bounds[0],
                    "visible_bounds_height": bounds[1],
                    "visible_bounds_offset": [0, bounds[2], 0],
                },
                "bones": self.bones,
            }],
        }
        write_json(os.path.join(A, "geo/entity", self.ident + ".geo.json"), geo)
        self.tex.save(os.path.join(A, "textures/entity", self.ident + ".png"))
        glow_path = os.path.join(A, "textures/entity", self.ident + "_glowmask.png")
        if self.has_glow:
            self.glow.save(glow_path)
        elif os.path.exists(glow_path):
            os.remove(glow_path)


def build(fn, ident, sizes=((64, 64), (128, 64), (128, 128)), **kw):
    """Tenta montar o modelo em texturas cada vez maiores ate caber."""
    for tw, th in sizes:
        try:
            g = Geo(ident, tw, th, **kw)
            bounds = fn(g)
            g.save(bounds) if bounds else g.save()
            return g
        except TextureFull:
            continue
    raise RuntimeError("textura nao coube: " + ident)


# ------------------------------------------------------------------------------------------------
#  Pintores
# ------------------------------------------------------------------------------------------------
def painter(fn, per_face=False):
    fn.per_face = per_face
    return fn


def shaded(base, light=0.18, dark=0.25, noise=0.05, seed=1, detail=None):
    """Vertical: topo mais claro, base mais escura, com ruido leve. detail(c, x, y, w, h) pinta por cima."""
    rnd = random.Random(seed)
    base = rgba(base)

    def p(c, x, y, w, h):
        for yy in range(h):
            t = yy / max(1, h - 1)
            col = lighten(base, light * (1 - t) * 0.9) if t < 0.5 else darken(base, dark * (t - 0.5) * 2)
            for xx in range(w):
                f = (rnd.random() - 0.5) * 2 * noise
                c.set(x + xx, y + yy, lighten(col, f) if f > 0 else darken(col, -f))
        if detail:
            detail(c, x, y, w, h)
    return painter(p)


def flat(col, noise=0.04, seed=2, detail=None):
    rnd = random.Random(seed)
    col = rgba(col)

    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                f = (rnd.random() - 0.5) * 2 * noise
                c.set(x + xx, y + yy, lighten(col, f) if f > 0 else darken(col, -f))
        if detail:
            detail(c, x, y, w, h)
    return painter(p)


def rows(colors, noise=0.04, seed=3, detail=None):
    """Faixas horizontais: colors[i] para a fracao i da altura (ex.: roupa em cima, pele embaixo)."""
    rnd = random.Random(seed)
    colors = [None if c is None else rgba(c) for c in colors]

    def p(c, x, y, w, h):
        for yy in range(h):
            col = colors[min(len(colors) - 1, int(yy * len(colors) / h))]
            for xx in range(w):
                if col is None:
                    continue
                f = (rnd.random() - 0.5) * 2 * noise
                c.set(x + xx, y + yy, lighten(col, f) if f > 0 else darken(col, -f))
        if detail:
            detail(c, x, y, w, h)
    return painter(p)


def jelly(base, alpha, light, dark, highlight=False, seed=4, detail=None, rim=True, radial=False):
    """Gelatina translucida: gradiente, borda um pouco mais opaca e brilho especular opcional."""
    rnd = random.Random(seed)

    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                if radial:
                    dx = (xx + 0.5 - w / 2) / (w / 2)
                    dy = (yy + 0.5 - h / 2) / (h / 2)
                    t = min(1.0, math.hypot(dx, dy))
                    col = mix(rgba(light), rgba(base), min(1.0, t * 1.2))
                    if t > 0.85:
                        col = mix(col, rgba(dark), (t - 0.85) / 0.15 * 0.6)
                else:
                    t = yy / max(1, h - 1)
                    col = mix(rgba(light), rgba(base), min(1.0, t * 1.6)) if t < 0.6 else mix(rgba(base), rgba(dark), (t - 0.6) / 0.4)
                a = alpha
                if rim and (xx == 0 or xx == w - 1 or (not radial and yy == h - 1)):
                    col = mix(col, rgba(dark), 0.35)
                    a = min(255, alpha + 35)
                f = (rnd.random() - 0.5) * 0.06
                col = lighten(col, f) if f > 0 else darken(col, -f)
                c.set(x + xx, y + yy, with_alpha(col, a))
        if highlight:
            hl = (255, 255, 255, 215)
            hl2 = (255, 255, 255, 140)
            sx, sy = x + max(1, w // 8), y + max(1, h // 6)
            for i, (ox, oy, col) in enumerate([(1, 0, hl), (2, 0, hl), (0, 1, hl), (0, 2, hl2), (3, 0, hl2), (1, 1, hl2)]):
                if (sx - x) + ox < w and (sy - y) + oy < h:
                    c.set(sx + ox, sy + oy, col)
        if detail:
            detail(c, x, y, w, h)
    return painter(p)


# =================================================================================================
#  SLIMES
# =================================================================================================
SLIME_PAL = {
    "slime": dict(base=(79, 168, 255), light=(170, 220, 255), dark=(36, 98, 190), alpha=165, core=(130, 196, 255), core_a=110,
                  eye=(22, 30, 60)),
    "fire_slime": dict(base=(255, 120, 44), light=(255, 196, 92), dark=(186, 48, 20), alpha=195, core=(255, 232, 120), core_a=255,
                       eye=(58, 12, 4)),
    "water_slime": dict(base=(63, 198, 232), light=(196, 246, 255), dark=(24, 118, 170), alpha=140, core=(31, 95, 191), core_a=190,
                        eye=(10, 38, 78)),
    "stone_slime": dict(base=(138, 132, 116), light=(186, 180, 162), dark=(84, 80, 70), alpha=220, core=(70, 70, 72), core_a=255,
                        eye=(26, 24, 20)),
    "wind_slime": dict(base=(159, 240, 208), light=(232, 255, 246), dark=(84, 186, 150), alpha=135, core=(214, 255, 240), core_a=110,
                       eye=(18, 66, 56)),
    "poison_jelly": dict(base=(155, 79, 216), light=(206, 160, 255), dark=(88, 38, 132), alpha=185, core=(110, 226, 80), core_a=235,
                         eye=(38, 8, 56)),
    "king_slime": dict(base=(58, 132, 255), light=(156, 204, 255), dark=(26, 70, 180), alpha=185, core=(120, 180, 255), core_a=110,
                       eye=(16, 20, 56)),
}

GOLD = (242, 201, 76)
GOLD_L = (255, 236, 150)
GOLD_D = (176, 128, 40)
RUBY = (226, 52, 60)
EMERALD = (58, 214, 112)
SAPPHIRE = (60, 120, 230)


def eye_painter(col, angry=False):
    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                c.set(x + xx, y + yy, rgba(col))
        # brilho no canto (olhar fofo)
        c.set(x, y, (255, 255, 255, 255))
        if h > 2:
            c.set(x + w - 1, y + h - 1, rgba(lighten(rgba(col), 0.25)))
        if angry:
            c.set(x + w - 1, y, rgba(darken(rgba(col), 0.3)))
    return painter(p)


def mouth_painter(col, kind="smile"):
    def p(c, x, y, w, h):
        d = rgba(col)
        if kind == "smile":       # "w" fofinho
            c.set(x, y, d); c.set(x + w - 1, y, d)
            for xx in range(1, w - 1):
                c.set(x + xx, y + h - 1, d)
        elif kind == "frown":
            for xx in range(1, w - 1):
                c.set(x + xx, y, d)
            c.set(x, y + h - 1, d); c.set(x + w - 1, y + h - 1, d)
        elif kind == "open":
            for yy in range(h):
                for xx in range(w):
                    c.set(x + xx, y + yy, d)
            c.set(x + 1, y + h - 1, (200, 70, 90, 255))
    return painter(p)


def brow_painter(col, slant):
    """Sobrancelha diagonal (slant=+1 desce para a direita, -1 para a esquerda)."""
    def p(c, x, y, w, h):
        for xx in range(w):
            t = xx / max(1, w - 1)
            yy = int(round((t if slant > 0 else 1 - t) * (h - 1)))
            c.set(x + xx, y + yy, rgba(col))
            if yy + 1 < h:
                c.set(x + xx, y + yy + 1, with_alpha(col, 160))
    return painter(p)


def flame_painter(seed=7):
    rnd = random.Random(seed)

    def p(c, x, y, w, h):
        for yy in range(h):
            t = yy / max(1, h - 1)
            col = mix((255, 245, 160, 255), (255, 150, 40, 255), min(1, t * 1.5)) if t < 0.7 else mix((255, 150, 40, 255), (220, 60, 20, 255), (t - 0.7) / 0.3)
            for xx in range(w):
                f = (rnd.random() - 0.5) * 0.12
                c.set(x + xx, y + yy, lighten(col, f) if f > 0 else darken(col, -f))
    return painter(p)


def rock_painter(seed, moss=False):
    rnd = random.Random(seed)
    g1, g2, g3 = (125, 125, 128), (98, 98, 102), (152, 152, 156)

    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                r = rnd.random()
                col = g3 if r < 0.2 else g2 if r < 0.45 else g1
                if moss and yy < max(1, h // 3) and rnd.random() < 0.7:
                    col = (94, 140, 58) if rnd.random() < 0.6 else (122, 170, 74)
                c.set(x + xx, y + yy, rgba(col))
    return painter(p)


def ring_painter(col):
    """Anel de vento (centro transparente, riscos espiralados)."""
    def p(c, x, y, w, h):
        cx, cy = w / 2, h / 2
        for yy in range(h):
            for xx in range(w):
                dx, dy = xx + 0.5 - cx, yy + 0.5 - cy
                r = math.hypot(dx, dy) / (w / 2)
                if 0.62 < r < 0.98:
                    ang = math.atan2(dy, dx)
                    streak = (math.sin(ang * 3 + r * 9) + 1) / 2
                    if streak > 0.45:
                        a = int(90 + 120 * streak * (1 - abs(r - 0.8) / 0.18))
                        c.set(x + xx, y + yy, with_alpha(lighten(rgba(col), 0.4 * streak), max(0, min(230, a))))
    return painter(p)


def leaf_painter():
    def p(c, x, y, w, h):
        g, gl, gd = (96, 196, 110, 255), (150, 230, 140, 255), (52, 130, 70, 255)
        for yy in range(h):
            half = (w / 2) * math.sin(math.pi * (yy + 0.5) / h) + 0.3
            for xx in range(w):
                if abs(xx + 0.5 - w / 2) <= half:
                    c.set(x + xx, y + yy, gl if xx < w / 2 else g)
        for yy in range(h):
            c.set(x + w // 2, y + yy, gd)
    return painter(p)


def bubble_painter(col, alpha=200):
    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                c.set(x + xx, y + yy, with_alpha(col, alpha))
        c.set(x, y, (255, 255, 255, 240))
    return painter(p)


def gem_painter(col):
    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                t = (xx + yy) / max(1, w + h - 2)
                c.set(x + xx, y + yy, rgba(mix(rgba(lighten(rgba(col), 0.5)), rgba(darken(rgba(col), 0.3)), t)))
        c.set(x, y, (255, 255, 255, 255))
    return painter(p)


def slime_model(ident):
    pal = SLIME_PAL[ident]
    king = ident == "king_slime"
    base, light, dark, alpha = pal["base"], pal["light"], pal["dark"], pal["alpha"]

    def fn(g):
        def spots(c, x, y, w, h):
            if ident == "poison_jelly":
                rnd = random.Random(11)
                for _ in range(max(2, w * h // 18)):
                    sx, sy = rnd.randrange(w), rnd.randrange(h)
                    c.set(x + sx, y + sy, (139, 224, 78, 230))
            if ident == "stone_slime":
                rnd = random.Random(12)
                for _ in range(max(2, w * h // 14)):
                    sx, sy = rnd.randrange(w), rnd.randrange(h)
                    c.set(x + sx, y + sy, (110, 106, 96, 235))

        front = jelly(base, alpha, light, dark, highlight=True, seed=1, detail=spots)
        side = jelly(base, alpha, light, dark, seed=2, detail=spots)
        top = jelly(base, alpha, light, dark, highlight=True, seed=3, radial=True, detail=spots)
        bottom = jelly(dark, min(255, alpha + 20), base, dark, seed=4, rim=False)
        upper = jelly(base, alpha, light, base, seed=5, detail=spots)
        upper_top = jelly(base, alpha, light, dark, highlight=True, seed=6, radial=True)
        core = jelly(pal["core"], pal["core_a"], lighten(rgba(pal["core"]), 0.35), darken(rgba(pal["core"]), 0.25), seed=7, rim=False)

        root = g.bone("root")
        body = g.bone("body", "root")
        g.cube(body, (-4, 0, -4), (8, 5, 8), {"front": front, "side": side, "up": top, "down": bottom})
        g.cube(body, (-3, 5, -3), (6, 2, 6), {"side": upper, "up": upper_top, "down": SKIP})
        if not king:
            g.cube(body, (-1, 7, -1), (2, 1, 2), {"side": upper, "up": upper_top, "down": SKIP})
        if ident == "water_slime":
            g.cube(body, (-0.5, 8, -0.5), (1, 1, 1), {"side": upper, "up": upper_top, "down": SKIP})

        core_bone = g.bone("core", "body", (0, 2, 0))
        if ident == "stone_slime":
            g.cube(core_bone, (-2.5, 0.5, -2.5), (5, 4, 5), {"all": rock_painter(21)})
        elif ident == "fire_slime":
            g.cube(core_bone, (-1.5, 1, -1.5), (3, 3, 3), {"all": flame_painter(22)}, glow=True)
        else:
            g.cube(core_bone, (-2, 0.5, -2), (4, 3, 4), {"all": core}, glow=False)

        angry = ident in ("fire_slime", "king_slime", "poison_jelly")
        eye = eye_painter(pal["eye"], angry)
        re = g.bone("right_eye", "body", (-2, 3, -4.06))
        g.cube(re, (-2.5, 2, -4.06), (1, 2, 0), {"north": eye})
        le = g.bone("left_eye", "body", (2, 3, -4.06))
        g.cube(le, (1.5, 2, -4.06), (1, 2, 0), {"north": eye})
        mouth = g.bone("mouth", "body", (0, 1.5, -4.06))
        if king:
            # bigode real no lugar da boca
            def mustache(c, x, y, w, h):
                d = rgba((40, 30, 60))
                for xx in range(w):
                    yy = 0 if (xx in (0, w - 1)) else (1 if xx not in (w // 2 - 1, w // 2) else 0)
                    c.set(x + xx, y + yy, d)
                    if 0 < xx < w - 1:
                        c.set(x + xx, y + min(h - 1, yy + 1), with_alpha(d, 200))
            g.cube(mouth, (-2, 1, -4.07), (4, 1, 0), {"north": painter(mustache)})
        else:
            g.cube(mouth, (-1, 1, -4.06), (2, 1, 0),
                   {"north": mouth_painter(pal["eye"], "frown" if ident == "fire_slime" else "open" if ident == "poison_jelly" else "smile")})
        if angry:
            brows = g.bone("brows", "body", (0, 4.5, -4.07))
            g.cube(brows, (-3, 4, -4.08), (2, 1, 0), {"north": brow_painter(pal["eye"], +1)})
            g.cube(brows, (1, 4, -4.08), (2, 1, 0), {"north": brow_painter(pal["eye"], -1)})

        # ---- extras por variedade
        if ident == "fire_slime":
            fl = g.bone("flicker", "body", (0, 7, 0))
            fp = flame_painter(31)
            g.cube(fl, (-1, 7, -1), (2, 3, 2), {"all": fp}, glow=True)
            g.cube(fl, (-3, 6, -1), (1, 2, 1), {"all": fp}, glow=True)
            g.cube(fl, (2, 6, 0), (1, 2, 1), {"all": fp}, glow=True)
            g.cube(fl, (0, 6, 2), (1, 2, 1), {"all": fp}, glow=True)
        elif ident == "water_slime":
            ex = g.bone("extra", "body", (0, 2, 0))
            bp = bubble_painter((230, 250, 255), 190)
            g.cube(ex, (1, 3, 1), (1, 1, 1), {"all": bp})
            g.cube(ex, (-3, 1, 0), (1, 1, 1), {"all": bp})
            g.cube(ex, (2, 1, -2), (1, 1, 1), {"all": bp})
        elif ident == "stone_slime":
            ex = g.bone("extra", "body", (0, 2, 0))
            g.cube(ex, (-4.5, 0.5, -2), (1, 2, 3), {"all": rock_painter(41, True)})
            g.cube(ex, (3.5, 2, 0), (1, 2, 2), {"all": rock_painter(42, True)})
            g.cube(ex, (-2, 5.5, 1), (3, 2, 2), {"all": rock_painter(43, True)})
            g.cube(ex, (1, 6.5, -2), (2, 1, 2), {"all": rock_painter(44, True)})
        elif ident == "wind_slime":
            sp = g.bone("spin", "body", (0, 2.5, 0))
            g.cube(sp, (-6, 2.5, -6), (12, 0, 12), {"up": ring_painter((200, 255, 236)), "down": ring_painter((180, 240, 220))})
            ex = g.bone("extra", "body", (0, 8, 0))
            lp = leaf_painter()
            g.cube(ex, (-1, 8, 0), (2, 3, 0), {"north": lp, "south": lp})
            g.cube(ex, (0, 8, -1), (0, 3, 2), {"east": lp, "west": lp})
        elif ident == "poison_jelly":
            ex = g.bone("extra", "body", (0, 6, 0))
            gp = bubble_painter((139, 224, 78), 235)
            g.cube(ex, (-2, 7, 1), (1, 1, 1), {"all": gp})
            g.cube(ex, (1, 7, -1), (1, 1, 1), {"all": gp})
            g.cube(ex, (0, 8, 0), (1, 1, 1), {"all": gp})
            drip = jelly(base, min(255, alpha + 30), light, dark, seed=9)
            g.cube(ex, (-4, 0, -4.5), (1, 2, 1), {"all": drip})
            g.cube(ex, (3, 0, -4.5), (1, 1, 1), {"all": drip})
            g.cube(ex, (4, 1, 1), (1, 2, 1), {"all": drip})
        elif king:
            crown = g.bone("crown", "body", (0, 7, 0))
            band = shaded(GOLD, light=0.3, dark=0.3, noise=0.03, seed=51, detail=lambda c, x, y, w, h: [
                c.set(x + xx, y + h - 1, rgba(GOLD_D)) for xx in range(w)])
            spike = shaded(GOLD_L, light=0.2, dark=0.35, noise=0.02, seed=52)
            g.cube(crown, (-3, 7, -3), (6, 2, 1), {"all": band})
            g.cube(crown, (-3, 7, 2), (6, 2, 1), {"all": band})
            g.cube(crown, (-3, 7, -2), (1, 2, 4), {"all": band})
            g.cube(crown, (2, 7, -2), (1, 2, 4), {"all": band})
            g.cube(crown, (-2, 7, -2), (4, 1, 4), {"all": flat((150, 24, 40), seed=53)})
            for o in [(-1, 9, -3, 2, 2, 1), (-3, 9, -3, 1, 1, 1), (2, 9, -3, 1, 1, 1), (-3, 9, 2, 1, 1, 1), (2, 9, 2, 1, 1, 1),
                      (-1, 9, 2, 2, 2, 1), (-3, 9, -1, 1, 2, 2), (2, 9, -1, 1, 2, 2)]:
                g.cube(crown, o[:3], o[3:], {"all": spike})
            g.cube(crown, (-0.5, 7.5, -3.06), (1, 1, 0), {"north": gem_painter(RUBY)}, glow=True)
            g.cube(crown, (-0.5, 10.2, -3.06), (1, 0.6, 0), {"north": gem_painter(SAPPHIRE)}, glow=True)
            g.cube(crown, (-3.06, 7.5, -0.5), (0, 1, 1), {"west": gem_painter(EMERALD), "east": gem_painter(EMERALD)}, glow=True)
            g.cube(crown, (2.06, 7.5, -0.5), (0, 1, 1), {"west": gem_painter(EMERALD), "east": gem_painter(EMERALD)}, glow=True)
        return (1.2, 1.2, 0.4)

    return build(fn, ident, density=2)


# =================================================================================================
#  GOBLINS
# =================================================================================================
SKIN = (94, 158, 58)
SKIN_L = (128, 192, 82)
SKIN_D = (63, 112, 40)
SKIN_DD = (42, 78, 27)
EYE_Y = (246, 214, 60)
PUPIL = (24, 22, 12)
TOOTH = (236, 230, 204)
EAR_IN = (196, 128, 104)
LEATHER = (122, 82, 50)
LEATHER_L = (160, 112, 70)
LEATHER_D = (78, 51, 30)
BELT = (58, 39, 22)
BUCKLE = (196, 170, 80)
CLOTH = (107, 74, 43)
NAIL = (60, 52, 40)
IRON = (150, 156, 164)
IRON_L = (196, 202, 210)
IRON_D = (92, 97, 104)
RED = (150, 36, 30)
RED_D = (100, 22, 20)
FUR_W = (236, 232, 222)
WOOD = (110, 76, 44)
WOOD_D = (78, 52, 30)


def face_painter(skin, skin_d, skin_dd, eye, mouth_col=(40, 26, 18), tusks=True, brow=True, glow_eyes=False):
    """Rosto de goblin 8x7: sobrancelha franzida, olhos amarelos com pupila, boca com presas."""
    def p(c, x, y, w, h):
        rnd = random.Random(61)
        for yy in range(h):
            for xx in range(w):
                col = skin if yy > 0 else skin_d
                f = (rnd.random() - 0.5) * 0.08
                c.set(x + xx, y + yy, rgba(lighten(rgba(col), f) if f > 0 else darken(rgba(col), -f)))
        if brow:
            for xx in (1, 2, 5, 6):
                c.set(x + xx, y + 1, rgba(skin_dd))
            c.set(x + 3, y + 2, rgba(skin_d)); c.set(x + 4, y + 2, rgba(skin_d))
        ey = y + 2
        c.set(x + 1, ey, rgba(eye)); c.set(x + 2, ey, rgba(PUPIL))
        c.set(x + w - 3, ey, rgba(PUPIL)); c.set(x + w - 2, ey, rgba(eye))
        # bochechas
        my = y + h - 2
        c.set(x + 1, my - 1, rgba(lighten(rgba(skin), 0.12))); c.set(x + w - 2, my - 1, rgba(lighten(rgba(skin), 0.12)))
        # boca
        for xx in range(1, w - 1):
            c.set(x + xx, my, rgba(mouth_col))
        if tusks:
            c.set(x + 2, my, rgba(TOOTH)); c.set(x + w - 3, my, rgba(TOOTH))
        for xx in range(w):
            c.set(x + xx, y + h - 1, rgba(skin_d))
    return painter(p)


def tunic_painter(main, light, dark, open_front=False, belt=True, necklace=None, emblem=None, seed=71, skin=SKIN):
    def p(c, x, y, w, h):
        rnd = random.Random(seed)
        for yy in range(h):
            for xx in range(w):
                col = main
                r = rnd.random()
                if r < 0.18:
                    col = light
                elif r < 0.32:
                    col = dark
                c.set(x + xx, y + yy, rgba(col))
        if open_front and w >= 5:
            mid = w // 2
            for yy in range(0, h - 2):
                for xx in (mid - 1, mid) if w % 2 == 0 else (mid - 1, mid, mid + 1):
                    c.set(x + xx, y + yy, rgba(skin if yy > 0 else darken(rgba(skin), 0.15)))
        if necklace:
            for xx in range(1, w - 1):
                if (xx % 2) == 0:
                    c.set(x + xx, y + 1 + (1 if 1 < xx < w - 2 else 0), rgba(necklace))
        if emblem:
            emblem(c, x, y, w, h)
        if belt:
            by = y + h - 2
            for xx in range(w):
                c.set(x + xx, by, rgba(BELT))
            c.set(x + w // 2, by, rgba(BUCKLE))
    return painter(p, per_face=True)


def humanoid(g, v):
    """Esqueleto comum dos goblins. v: dict com proporcoes, cores e extras."""
    skin, skin_d, skin_dd = v.get("skin", SKIN), v.get("skin_d", SKIN_D), v.get("skin_dd", SKIN_DD)
    tw, th, td = v["torso"]          # tamanho do tronco
    hip = v["hip"]                   # altura do quadril (= altura das pernas)
    head = v.get("head", (8, 7, 7))
    arm = v["arm"]                   # (larg, alt)
    leg = v["leg"]                   # (larg, prof)
    neck = hip + th

    skin_side = shaded(skin, 0.12, 0.2, seed=81)
    skin_top = flat(skin, seed=82, detail=lambda c, x, y, w, h: [c.set(x + xx, y + yy, rgba(skin_d))
                                                                 for (xx, yy) in [(1, 1), (w - 2, 2), (w // 2, h // 2)] if xx < w and yy < h])
    skin_bottom = flat(skin_d, seed=83)

    root = g.bone("root")
    body = g.bone("body", "root", (0, hip, 0))
    torso_front = v["tunic_front"]
    torso_side = v.get("tunic_side", torso_front)
    g.cube(body, (-tw / 2, hip, -td / 2), (tw, th, td),
           {"front": torso_front, "back": v.get("tunic_back", torso_side), "lr": torso_side,
            "up": flat(v.get("shoulder", skin_d), seed=84), "down": flat(CLOTH, seed=85)})
    if v.get("belly"):
        bw, bh = v["belly"]
        g.cube(body, (-bw / 2, hip + 0.5, -td / 2 - 1), (bw, bh, 1), {"all": v.get("belly_paint", torso_front)})
    skirt = v.get("skirt")
    if skirt:
        sy, sh, sp = skirt
        g.cube(body, (-tw / 2, sy, -td / 2 - 0.5), (tw, sh, td + 1),
               {"side": sp, "up": SKIP, "down": flat(darken(rgba(CLOTH), 0.2), seed=86)})

    # cabeca
    hw, hh, hd = head
    hb = g.bone("head", "body", (0, neck, 0))
    g.cube(hb, (-hw / 2, neck, -hd / 2), (hw, hh, hd),
           {"front": v.get("face", face_painter(skin, skin_d, skin_dd, v.get("eye", EYE_Y))),
            "side": skin_side, "up": skin_top, "down": skin_bottom})
    nose = shaded(skin, 0.2, 0.25, seed=87, detail=lambda c, x, y, w, h: c.set(x + w - 1, y + h - 1, rgba(skin_dd)))
    g.cube(hb, (-1, neck + 1, -hd / 2 - 2), (2, 3, 2), {"all": nose})
    if v.get("tusks", True):
        tooth = flat(TOOTH, seed=88)
        g.cube(hb, (-2.5, neck + 1, -hd / 2 - 0.5), (1, 1, 1), {"all": tooth})
        g.cube(hb, (1.5, neck + 1, -hd / 2 - 0.5), (1, 1, 1), {"all": tooth})

    # orelhas pontudas (planas, viradas para cima)
    ear_front = rows([skin, EAR_IN, EAR_IN], seed=89, detail=lambda c, x, y, w, h: [c.set(x + xx, y, rgba(skin_d)) for xx in range(w)])
    ear_side = shaded(skin, seed=90)
    ey = neck + 3
    rer = g.bone("right_ear", "head", (-hw / 2, ey + 1, 0), rotation=(0, -10, 22))
    g.cube(rer, (-hw / 2 - 3, ey, -0.5), (3, 3, 1), {"front": ear_front, "back": ear_side, "side": ear_side})
    g.cube(rer, (-hw / 2 - 5, ey + 1, -0.5), (2, 2, 1), {"front": ear_front, "back": ear_side, "side": ear_side})
    g.cube(rer, (-hw / 2 - 6, ey + 2, -0.5), (1, 1, 1), {"all": ear_side})
    ler = g.bone("left_ear", "head", (hw / 2, ey + 1, 0), rotation=(0, 10, -22))
    g.cube(ler, (hw / 2, ey, -0.5), (3, 3, 1), {"front": ear_front, "back": ear_side, "side": ear_side})
    g.cube(ler, (hw / 2 + 3, ey + 1, -0.5), (2, 2, 1), {"front": ear_front, "back": ear_side, "side": ear_side})
    g.cube(ler, (hw / 2 + 5, ey + 2, -0.5), (1, 1, 1), {"all": ear_side})

    # bracos
    aw, ah = arm
    sh_y = neck - 0.5
    arm_paint = v.get("arm_paint", rows([skin, skin, skin, LEATHER_D, skin, skin_d], seed=91))
    rx = -tw / 2 - aw
    ra = g.bone("right_arm", "body", (rx + aw / 2, sh_y, 0))
    g.cube(ra, (rx, sh_y - ah, -aw / 2), (aw, ah, aw), {"side": arm_paint, "up": flat(skin_d, seed=92), "down": flat(skin_dd, seed=93)})
    g.bone("right_item", "right_arm", (rx + aw / 2, sh_y - ah + 0.5, -0.5))
    la = g.bone("left_arm", "body", (tw / 2 + aw / 2, sh_y, 0))
    g.cube(la, (tw / 2, sh_y - ah, -aw / 2), (aw, ah, aw), {"side": arm_paint, "up": flat(skin_d, seed=92), "down": flat(skin_dd, seed=93)})
    g.bone("left_item", "left_arm", (tw / 2 + aw / 2, sh_y - ah + 0.5, -0.5))

    # pernas
    lw, ld = leg
    leg_paint = v.get("leg_paint", rows([CLOTH, CLOTH, skin, skin, LEATHER_D, skin], seed=94))
    foot = flat(skin_d, seed=95, detail=lambda c, x, y, w, h: [c.set(x + xx, y + h - 1, rgba(NAIL)) for xx in range(0, w, 2)])
    gap = v.get("leg_gap", 0)
    rl = g.bone("right_leg", "root", (-lw / 2 - gap / 2, hip, 0))
    g.cube(rl, (-lw - gap / 2, 0, -ld / 2), (lw, hip, ld), {"side": leg_paint, "up": SKIP, "down": flat(skin_dd, seed=96)})
    g.cube(rl, (-lw - gap / 2, 0, -ld / 2 - 1), (lw, 1, 1), {"all": v.get("foot", foot)})
    ll = g.bone("left_leg", "root", (lw / 2 + gap / 2, hip, 0))
    g.cube(ll, (gap / 2, 0, -ld / 2), (lw, hip, ld), {"side": leg_paint, "up": SKIP, "down": flat(skin_dd, seed=96)})
    g.cube(ll, (gap / 2, 0, -ld / 2 - 1), (lw, 1, 1), {"all": v.get("foot", foot)})
    return dict(root=root, body=body, head=hb, right_arm=ra, left_arm=la, neck=neck, hip=hip, tw=tw, td=td, hw=hw, hh=hh, hd=hd)


def tattered(col, seed):
    """Tanga/saia com a barra rasgada (pixels transparentes na ultima linha)."""
    rnd = random.Random(seed)

    def p(c, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                if yy == h - 1 and rnd.random() < 0.45:
                    continue
                f = (rnd.random() - 0.5) * 0.12
                c.set(x + xx, y + yy, rgba(lighten(rgba(col), f) if f > 0 else darken(rgba(col), -f)))
    return painter(p, per_face=True)


def goblin_base_variant():
    return dict(torso=(7, 6, 4), hip=6, arm=(2, 6), leg=(3, 3), leg_gap=0,
                tunic_front=tunic_painter(LEATHER, LEATHER_L, LEATHER_D, open_front=True, necklace=(230, 222, 196), seed=101),
                tunic_side=tunic_painter(LEATHER, LEATHER_L, LEATHER_D, seed=102),
                skirt=(4, 2, tattered(CLOTH, 103)))


def goblin_model():
    def fn(g):
        v = goblin_base_variant()
        b = humanoid(g, v)
        # faixa vermelha na cabeca (guerreiro)
        band = flat((170, 40, 32), seed=111)
        g.cube(b["head"], (-4.25, b["neck"] + 6, -3.75), (8.5, 1, 7.5), {"side": band, "up": SKIP, "down": SKIP})
        g.cube(b["head"], (-0.5, b["neck"] + 4.5, 3.6), (1, 2, 1), {"all": band})
        return (1.6, 1.6, 0.6)
    return build(fn, "goblin")


def goblin_archer_model():
    def fn(g):
        green, green_l, green_d = (60, 92, 42), (84, 122, 58), (40, 62, 28)
        v = goblin_base_variant()
        v["tunic_front"] = tunic_painter(green, green_l, green_d, open_front=False, seed=121)
        v["tunic_side"] = tunic_painter(green, green_l, green_d, seed=122)
        v["skirt"] = (4, 2, tattered(green_d, 123))
        v["arm_paint"] = rows([green, green, SKIN, LEATHER_D, SKIN, SKIN_D], seed=124)
        b = humanoid(g, v)
        neck = b["neck"]
        hood = shaded(green, 0.15, 0.3, seed=125)
        hood_in = flat(green_d, seed=126)
        # capuz aberto na frente (as orelhas atravessam pelos lados)
        g.cube(b["head"], (-4.5, neck + 2, -4), (9, 5.5, 8), {"front": SKIP, "side": hood, "up": hood, "down": SKIP})
        g.cube(b["head"], (-4.5, neck + 6.5, -4.5), (9, 1, 1), {"all": hood_in})
        g.cube(b["head"], (-1, neck + 6.5, 3.5), (2, 2, 2), {"all": hood})  # ponta do capuz
        g.cube(b["body"], (-3.5, neck - 3, 2), (7, 3, 1), {"all": hood})     # gola nas costas
        quiver = g.bone("quiver", "body", (1.5, b["hip"] + 4, 3), rotation=(0, 0, 25))
        q = rows([LEATHER, LEATHER, LEATHER_D, LEATHER], seed=127)
        g.cube(quiver, (0.5, b["hip"] + 1, 2), (2, 6, 2), {"side": q, "up": flat((40, 30, 20), seed=128), "down": flat(LEATHER_D, seed=129)})
        fl = rows([(240, 240, 240), (220, 60, 50), (120, 90, 60)], seed=130)
        g.cube(quiver, (0.75, b["hip"] + 7, 2.25), (1, 2, 1), {"all": fl})
        g.cube(quiver, (1.75, b["hip"] + 6.5, 2.75), (1, 2, 1), {"all": fl})
        return (1.6, 1.6, 0.6)
    return build(fn, "goblin_archer")


def goblin_shaman_model():
    def fn(g):
        robe, robe_l, robe_d = (92, 62, 120), (124, 90, 156), (60, 38, 82)
        v = goblin_base_variant()

        def runes(c, x, y, w, h):
            for yy in range(2, h - 2, 2):
                c.set(x + (yy % (w - 1)), y + yy, (120, 230, 220, 255))
        v["tunic_front"] = tunic_painter(robe, robe_l, robe_d, open_front=False, necklace=(236, 228, 200), seed=141, emblem=runes)
        v["tunic_side"] = tunic_painter(robe, robe_l, robe_d, seed=142)
        v["skirt"] = (1, 5, tattered(robe_d, 143))
        v["arm_paint"] = rows([robe, robe, robe, SKIN, SKIN, SKIN_D], seed=144)
        v["face"] = face_painter(SKIN, SKIN_D, SKIN_DD, (255, 120, 230), tusks=True)
        b = humanoid(g, v)
        neck = b["neck"]
        # mascara de cranio + penas
        bone_col = (230, 222, 196)

        def skull_front(c, x, y, w, h):
            for yy in range(h):
                for xx in range(w):
                    c.set(x + xx, y + yy, rgba(bone_col))
            c.set(x + 1, y + h - 1, (40, 30, 40, 255)); c.set(x + w - 2, y + h - 1, (40, 30, 40, 255))
            c.set(x + w // 2, y, (200, 190, 160, 255))
        g.cube(b["head"], (-2.5, neck + 7, -3), (5, 2, 4), {"front": painter(skull_front), "all": flat(bone_col, seed=145)})
        for i, (ox, hgt, col) in enumerate([(-2, 4, (200, 50, 40)), (0, 5, (40, 170, 160)), (2, 4, (240, 200, 60))]):
            g.cube(b["head"], (ox - 0.5, neck + 9, 0), (1, hgt, 1), {"all": rows([lighten(rgba(col), 0.2), col, darken(rgba(col), 0.3)], seed=146 + i)})
        # cajado com cranio e gema brilhante
        staff = g.bone("staff", "right_arm", (-4.5, 6, 0))
        g.cube(staff, (-5, 1, -0.5), (1, 16, 1), {"all": rows([WOOD, WOOD_D, WOOD, WOOD, WOOD_D], seed=150)})
        g.cube(staff, (-6, 17, -1.5), (3, 3, 3), {"front": painter(skull_front), "all": flat(bone_col, seed=151)})
        g.cube(staff, (-5, 20, -0.5), (1, 1, 1), {"all": gem_painter((120, 240, 220))}, glow=True)
        g.cube(staff, (-6.5, 14, -0.25), (1, 3, 0.5), {"all": rows([(40, 170, 160), (200, 50, 40)], seed=152)})
        return (1.8, 2.0, 0.8)
    return build(fn, "goblin_shaman")


def hobgoblin_model():
    def fn(g):
        skin, skin_d, skin_dd = (79, 122, 51), (54, 88, 34), (36, 60, 22)

        def plate(c, x, y, w, h):
            for yy in range(h):
                for xx in range(w):
                    col = IRON if (xx + yy) % 5 else IRON_L
                    if yy == 0:
                        col = IRON_L
                    c.set(x + xx, y + yy, rgba(col))
            for xx in range(1, w - 1, 3):
                c.set(x + xx, y + 1, rgba(IRON_D))
            # tabardo vermelho no meio
            mid = w // 2
            for yy in range(2, h - 2):
                c.set(x + mid, y + yy, rgba(RED)); c.set(x + mid - 1, y + yy, rgba(RED_D))
        v = dict(skin=skin, skin_d=skin_d, skin_dd=skin_dd, torso=(8, 7, 5), hip=6, arm=(3, 7), leg=(3, 3), leg_gap=1,
                 tunic_front=painter(lambda c, x, y, w, h: (plate(c, x, y, w, h), [c.set(x + xx, y + h - 2, rgba(BELT)) for xx in range(w)]), per_face=True),
                 tunic_side=rows([IRON, IRON_D, LEATHER_D, LEATHER, BELT, LEATHER], seed=161),
                 skirt=(3, 3, rows([IRON_D, IRON, IRON_D], seed=162)),
                 arm_paint=rows([LEATHER_D, skin, skin, skin, IRON, IRON_D, skin_d], seed=163),
                 leg_paint=rows([IRON_D, LEATHER_D, LEATHER, LEATHER_D, LEATHER_D, (50, 40, 30)], seed=164),
                 foot=flat((50, 40, 30), seed=165),
                 face=face_painter(skin, skin_d, skin_dd, (255, 160, 40)))
        b = humanoid(g, v)
        neck = b["neck"]
        helm = shaded(IRON, 0.25, 0.3, seed=166, detail=lambda c, x, y, w, h: [c.set(x + xx, y + h - 1, rgba(IRON_D)) for xx in range(w)])
        g.cube(b["head"], (-4.5, neck + 5, -4), (9, 3, 8), {"all": helm})
        g.cube(b["head"], (-0.5, neck + 3, -4.5), (1, 3, 1), {"all": flat(IRON_D, seed=167)})
        g.cube(b["head"], (-0.5, neck + 8, -2), (1, 1, 5), {"all": flat((170, 40, 32), seed=168)})  # crista
        pad = shaded(IRON, 0.3, 0.3, seed=169)
        g.cube(b["right_arm"], (-8, neck - 2, -2), (4, 2, 4), {"all": pad})
        g.cube(b["left_arm"], (4, neck - 2, -2), (4, 2, 4), {"all": pad})
        return (2.0, 2.2, 0.9)
    return build(fn, "hobgoblin")


def goblin_king_model():
    def fn(g):
        skin, skin_d, skin_dd = (85, 127, 51), (60, 92, 34), (40, 64, 22)
        velvet, velvet_d = (158, 31, 31), (100, 18, 18)

        def royal(c, x, y, w, h):
            for yy in range(h):
                for xx in range(w):
                    col = velvet if (xx + yy * 3) % 7 else velvet_d
                    c.set(x + xx, y + yy, rgba(col))
            # cinto de ouro e peitoral de osso
            for xx in range(w):
                c.set(x + xx, y + h - 2, rgba(GOLD_D))
            c.set(x + w // 2, y + h - 2, rgba(RUBY))
            for yy in range(1, h - 3, 2):
                c.set(x + 1, y + yy, rgba(FUR_W)); c.set(x + w - 2, y + yy, rgba(FUR_W))

        def ermine(c, x, y, w, h):
            rnd = random.Random(171)
            for yy in range(h):
                for xx in range(w):
                    c.set(x + xx, y + yy, rgba(FUR_W if rnd.random() > 0.12 else (30, 30, 30)))
        v = dict(skin=skin, skin_d=skin_d, skin_dd=skin_dd, torso=(9, 7, 6), hip=6, arm=(3, 8), leg=(3, 3), leg_gap=1,
                 head=(8, 7, 7),
                 tunic_front=painter(royal, per_face=True), tunic_side=painter(royal, per_face=True),
                 belly=(7, 5), belly_paint=shaded(skin, 0.2, 0.2, seed=172, detail=lambda c, x, y, w, h: c.set(x + w // 2, y + h // 2, rgba(skin_dd))),
                 skirt=(3, 3, rows([GOLD_D, velvet_d, velvet_d], seed=173)),
                 arm_paint=rows([velvet, skin, skin, skin, GOLD, GOLD_D, skin, skin_d], seed=174),
                 leg_paint=rows([velvet_d, velvet_d, (60, 40, 26), (60, 40, 26), (44, 30, 20), (44, 30, 20)], seed=175),
                 foot=flat((44, 30, 20), seed=176),
                 face=face_painter(skin, skin_d, skin_dd, (255, 70, 40)))
        b = humanoid(g, v)
        neck, hh = b["neck"], b["hh"]
        top = neck + hh
        # coroa
        crown = g.bone("crown", "head", (0, top, 0))
        band = shaded(GOLD, 0.3, 0.3, seed=177, detail=lambda c, x, y, w, h: c.set(x + w // 2, y + h // 2, rgba(RUBY)))
        spike = shaded(GOLD_L, 0.2, 0.35, seed=178)
        g.cube(crown, (-3.5, top, -3), (7, 2, 6), {"all": band, "up": flat(velvet, seed=179)})
        for o in [(-3.5, top + 2, -3, 1, 2, 1), (-0.5, top + 2, -3, 1, 3, 1), (2.5, top + 2, -3, 1, 2, 1),
                  (-3.5, top + 2, 2, 1, 2, 1), (-0.5, top + 2, 2, 1, 2, 1), (2.5, top + 2, 2, 1, 2, 1),
                  (-3.5, top + 2, -0.5, 1, 2, 1), (2.5, top + 2, -0.5, 1, 2, 1)]:
            g.cube(crown, o[:3], o[3:], {"all": spike})
        g.cube(crown, (-0.5, top + 0.5, -3.06), (1, 1, 0), {"north": gem_painter(RUBY)}, glow=True)
        # gola de arminho e capa
        g.cube(b["body"], (-5, neck - 1.5, -3.5), (10, 2, 7), {"all": painter(ermine)})
        cape = g.bone("cape", "body", (0, neck - 0.5, 3.1))
        g.cube(cape, (-4.5, 1, 3.1), (9, neck - 1.5, 0),
               {"south": rows([velvet, velvet, velvet, velvet_d], seed=180, detail=lambda c, x, y, w, h: [c.set(x + xx, y + h - 1, rgba(FUR_W)) for xx in range(w)]),
                "north": flat(velvet_d, seed=181)})
        # machado enorme na mao direita
        axe = g.bone("axe", "right_arm", (-5.5, 5, 0))
        g.cube(axe, (-6, 4.5, -13), (1, 1, 16), {"all": rows([WOOD, WOOD_D], seed=182)})
        blade = shaded(IRON, 0.25, 0.35, seed=183, detail=lambda c, x, y, w, h: [c.set(x, y + yy, rgba(IRON_L)) for yy in range(h)])
        g.cube(axe, (-6.25, 0, -16), (1.5, 10, 4), {"all": blade, "front": flat(IRON_L, seed=184)})
        g.cube(axe, (-6, 10, -14.5), (1, 2, 1), {"all": flat(IRON_D, seed=185)})
        g.cube(axe, (-6.5, 4, -12), (2, 2, 2), {"all": flat(GOLD_D, seed=186)})
        return (3.2, 3.0, 1.2)
    return build(fn, "goblin_king")


def wolf_rider_model():
    def fn(g):
        fur, fur_l, fur_d = (96, 90, 84), (132, 124, 116), (58, 54, 50)

        def furry(seed, base=fur):
            rnd = random.Random(seed)

            def p(c, x, y, w, h):
                for yy in range(h):
                    for xx in range(w):
                        r = rnd.random()
                        col = fur_l if r < 0.2 else fur_d if r < 0.4 else base
                        if yy == 0:
                            col = mix(rgba(col), rgba(fur_l), 0.3)
                        c.set(x + xx, y + yy, rgba(col))
            return painter(p)

        def wolf_face(c, x, y, w, h):
            furry(201)(c, x, y, w, h)
            c.set(x + 1, y + 2, (255, 200, 40, 255)); c.set(x + w - 2, y + 2, (255, 200, 40, 255))
            c.set(x + 1, y + 1, rgba(fur_d)); c.set(x + w - 2, y + 1, rgba(fur_d))

        def snout(c, x, y, w, h):
            furry(202, (150, 140, 130))(c, x, y, w, h)
            c.set(x + w // 2, y, (20, 20, 20, 255))
            for xx in range(w):
                c.set(x + xx, y + h - 1, (230, 230, 220, 255) if xx % 2 == 0 else (60, 30, 30, 255))

        def saddle(c, x, y, w, h):
            furry(203)(c, x, y, w, h)
            for yy in range(h // 4, h * 3 // 4):
                for xx in range(1, w - 1):
                    c.set(x + xx, y + yy, rgba(LEATHER if (xx + yy) % 4 else LEATHER_D))

        root = g.bone("root")
        wb = g.bone("wolf_body", "root", (0, 12, 0))
        g.cube(wb, (-3.5, 9, -7), (7, 7, 13), {"side": furry(204), "up": painter(saddle), "down": furry(205, fur_d)})
        g.cube(wb, (-4.5, 8.5, -8.5), (9, 8, 5), {"all": furry(206, fur_l)})  # juba
        wh = g.bone("wolf_head", "wolf_body", (0, 14, -8.5))
        g.cube(wh, (-3, 11, -13.5), (6, 6, 5), {"front": painter(wolf_face), "all": furry(207)})
        g.cube(wh, (-1.5, 11, -16.5), (3, 3, 3), {"front": painter(snout), "all": furry(208, (150, 140, 130))})
        g.cube(wh, (-3, 17, -11.5), (2, 2, 1), {"all": furry(209, fur_d)})
        g.cube(wh, (1, 17, -11.5), (2, 2, 1), {"all": furry(209, fur_d)})
        tail = g.bone("tail", "wolf_body", (0, 15, 6), rotation=(-40, 0, 0))
        g.cube(tail, (-1, 9, 5), (2, 7, 2), {"all": furry(210, fur_l)})
        leg = furry(211)
        paw = flat(fur_d, seed=212)
        for name, px, pz in [("front_right_leg", -2.5, -5), ("front_left_leg", 2.5, -5), ("back_right_leg", -2.5, 4), ("back_left_leg", 2.5, 4)]:
            lb = g.bone(name, "root", (px, 10, pz))
            g.cube(lb, (px - 1, 0, pz - 1), (2, 10, 2), {"side": leg, "up": SKIP, "down": paw})
        # cavaleiro goblin
        rider = g.bone("rider", "wolf_body", (0, 16, 0))
        body = g.bone("body", "rider", (0, 16, 0))
        g.cube(body, (-3, 16, -1.5), (6, 6, 3), {"front": tunic_painter(LEATHER, LEATHER_L, LEATHER_D, open_front=True, seed=213),
                                                  "all": tunic_painter(LEATHER, LEATHER_L, LEATHER_D, seed=214)})
        head = g.bone("head", "body", (0, 22, 0))
        g.cube(head, (-3.5, 22, -3), (7, 6, 6), {"front": face_painter(SKIN, SKIN_D, SKIN_DD, EYE_Y),
                                                  "side": shaded(SKIN, seed=215), "up": flat(SKIN, seed=216), "down": flat(SKIN_D, seed=217)})
        g.cube(head, (-1, 23, -5), (2, 2, 2), {"all": shaded(SKIN, seed=218)})
        hel = shaded(LEATHER_D, 0.2, 0.3, seed=219)
        g.cube(head, (-4, 26, -3.5), (8, 2.5, 7), {"all": hel})
        ear_front = rows([SKIN, EAR_IN], seed=220)
        rer = g.bone("right_ear", "head", (-3.5, 25.5, 0), rotation=(0, -10, 22))
        g.cube(rer, (-6.5, 24.5, -0.5), (3, 2, 1), {"front": ear_front, "all": shaded(SKIN, seed=221)})
        g.cube(rer, (-8.5, 25.5, -0.5), (2, 1, 1), {"all": shaded(SKIN, seed=221)})
        ler = g.bone("left_ear", "head", (3.5, 25.5, 0), rotation=(0, 10, -22))
        g.cube(ler, (3.5, 24.5, -0.5), (3, 2, 1), {"front": ear_front, "all": shaded(SKIN, seed=221)})
        g.cube(ler, (6.5, 25.5, -0.5), (2, 1, 1), {"all": shaded(SKIN, seed=221)})
        armp = rows([LEATHER, SKIN, SKIN, SKIN_D], seed=222)
        ra = g.bone("right_arm", "body", (-4, 21.5, 0))
        g.cube(ra, (-5, 16, -1), (2, 5.5, 2), {"all": armp})
        spear = g.bone("spear", "right_arm", (-4, 16.5, 0))
        g.cube(spear, (-4.5, 16, -14), (1, 1, 17), {"all": rows([WOOD, WOOD_D], seed=223)})
        g.cube(spear, (-5, 15.5, -17), (2, 2, 3), {"all": shaded(IRON, 0.3, 0.3, seed=224)})
        g.cube(spear, (-4.5, 16, -10.5), (1, 2, 1), {"all": flat((180, 40, 32), seed=225)})
        la = g.bone("left_arm", "body", (4, 21.5, 0), rotation=(-35, 0, 0))
        g.cube(la, (3, 16, -1), (2, 5.5, 2), {"all": armp})
        legp = rows([CLOTH, SKIN, SKIN, LEATHER_D], seed=226)
        rl = g.bone("right_leg", "rider", (-3, 17, 0), rotation=(-20, 0, 18))
        g.cube(rl, (-4.5, 12, -1), (2, 5, 2), {"all": legp})
        ll = g.bone("left_leg", "rider", (3, 17, 0), rotation=(-20, 0, -18))
        g.cube(ll, (2.5, 12, -1), (2, 5, 2), {"all": legp})
        return (2.4, 2.4, 0.9)
    return build(fn, "goblin_wolf_rider", sizes=((64, 64), (128, 64), (128, 128)))


# =================================================================================================
#  ANIMACOES
# =================================================================================================
def kf(d):
    return {f"{t:.2f}" if isinstance(t, float) else str(t): v for t, v in d.items()}


def anim(length, bones, loop=True):
    a = {"animation_length": length, "bones": bones}
    if loop:
        a["loop"] = True
    return a


def slime_animations():
    s = "math.sin(query.anim_time * 180)"
    anims = {
        "animation.mahou_slime.idle": anim(2.0, {
            "body": {"scale": [f"1 + {s} * 0.035", f"1 - {s} * 0.045", f"1 + {s} * 0.035"]},
            "core": {"position": [0, f"{s} * 0.4", 0], "rotation": [0, "query.anim_time * 45", 0]},
            "right_eye": {"scale": kf({0.0: [1, 1, 1], 1.6: [1, 1, 1], 1.68: [1, 0.15, 1], 1.76: [1, 1, 1]})},
            "left_eye": {"scale": kf({0.0: [1, 1, 1], 1.6: [1, 1, 1], 1.68: [1, 0.15, 1], 1.76: [1, 1, 1]})},
            "spin": {"rotation": [0, "query.anim_time * 180", 0]},
            "flicker": {"scale": [f"1 + math.sin(query.anim_time * 720) * 0.08", f"1 + math.sin(query.anim_time * 540) * 0.2", 1]},
            "extra": {"position": [0, f"{s} * 0.25", 0]},
            "crown": {"rotation": [0, 0, f"{s} * 2"]},
        }),
        "animation.mahou_slime.air": anim(1.0, {
            "body": {"scale": [0.92, 1.1, 0.92]},
            "right_eye": {"scale": [1.2, 1.25, 1]},
            "left_eye": {"scale": [1.2, 1.25, 1]},
            "mouth": {"scale": [1, 1.6, 1]},
            "spin": {"rotation": [0, "query.anim_time * 540", 0]},
            "flicker": {"scale": [1, 1.35, 1]},
            "crown": {"position": [0, 0.6, 0]},
        }),
        "animation.king_slime.leap": anim(0.6, {
            "body": {"scale": kf({0.0: [1, 1, 1], 0.15: [1.25, 0.7, 1.25], 0.35: [0.85, 1.25, 0.85], 0.6: [1, 1, 1]})},
            "crown": {"position": kf({0.0: [0, 0, 0], 0.35: [0, 1.2, 0], 0.6: [0, 0, 0]})},
        }, loop=False),
        "animation.king_slime.slam": anim(0.7, {
            "body": {"scale": kf({0.0: [1.4, 0.55, 1.4], 0.2: [0.9, 1.15, 0.9], 0.4: [1.08, 0.94, 1.08], 0.7: [1, 1, 1]})},
            "crown": {"position": kf({0.0: [0, -1.5, 0], 0.2: [0, 1.5, 0], 0.45: [0, 0, 0]}),
                      "rotation": kf({0.2: [0, 0, 8], 0.45: [0, 0, -4], 0.7: [0, 0, 0]})},
            "right_eye": {"scale": kf({0.0: [1, 0.3, 1], 0.3: [1, 1, 1]})},
            "left_eye": {"scale": kf({0.0: [1, 0.3, 1], 0.3: [1, 1, 1]})},
        }, loop=False),
        "animation.king_slime.split": anim(0.8, {
            "body": {"scale": kf({0.0: [1, 1, 1], 0.2: [1.2, 0.85, 0.9], 0.4: [0.9, 1.1, 1.2], 0.6: [1.1, 0.95, 0.95], 0.8: [1, 1, 1]})},
            "crown": {"rotation": kf({0.0: [0, 0, 0], 0.2: [0, 0, -10], 0.4: [0, 0, 10], 0.8: [0, 0, 0]})},
        }, loop=False),
    }
    write_json(os.path.join(A, "animations/entity/mahou_slime.animation.json"), {"format_version": "1.8.0", "animations": anims})


def goblin_animations():
    w = "math.sin(query.anim_time * 450)"
    r = "math.sin(query.anim_time * 720)"
    anims = {
        "animation.goblin.idle": anim(2.0, {
            "body": {"rotation": ["math.sin(query.anim_time * 180) * 1.5", 0, 0]},
            "right_arm": {"rotation": ["math.sin(query.anim_time * 180) * 3", 0, "4 + math.sin(query.anim_time * 180) * 2"]},
            "left_arm": {"rotation": ["-math.sin(query.anim_time * 180) * 3", 0, "-4 - math.sin(query.anim_time * 180) * 2"]},
            "right_ear": {"rotation": kf({0.0: [0, 0, 0], 1.2: [0, 0, 0], 1.3: [0, 0, 12], 1.45: [0, 0, 0]})},
            "left_ear": {"rotation": kf({0.0: [0, 0, 0], 0.5: [0, 0, 0], 0.6: [0, 0, -12], 0.75: [0, 0, 0]})},
            "cape": {"rotation": ["4 + math.sin(query.anim_time * 180) * 2", 0, 0]},
        }),
        "animation.goblin.walk": anim(0.8, {
            "right_leg": {"rotation": [f"{w} * 32", 0, 0]},
            "left_leg": {"rotation": [f"-{w} * 32", 0, 0]},
            "right_arm": {"rotation": [f"-{w} * 28", 0, 4]},
            "left_arm": {"rotation": [f"{w} * 28", 0, -4]},
            "body": {"position": [0, f"math.abs({w}) * 0.6", 0], "rotation": [3, f"{w} * 4", 0]},
            "right_ear": {"rotation": [0, 0, f"{w} * 6"]},
            "left_ear": {"rotation": [0, 0, f"-{w} * 6"]},
            "cape": {"rotation": [f"12 + {w} * 5", 0, 0]},
        }),
        "animation.goblin.run": anim(0.5, {
            "right_leg": {"rotation": [f"{r} * 48", 0, 0]},
            "left_leg": {"rotation": [f"-{r} * 48", 0, 0]},
            "right_arm": {"rotation": [f"-25 - {r} * 40", 0, 8]},
            "left_arm": {"rotation": [f"-25 + {r} * 40", 0, -8]},
            "body": {"position": [0, f"math.abs({r}) * 0.9", 0], "rotation": [14, 0, 0]},
            "right_ear": {"rotation": [-20, 0, f"-10 + {r} * 8"]},
            "left_ear": {"rotation": [-20, 0, f"10 - {r} * 8"]},
            "cape": {"rotation": [f"35 + {r} * 8", 0, 0]},
        }),
        "animation.goblin.attack": anim(0.5, {
            "right_arm": {"rotation": kf({0.0: [0, 0, 0], 0.15: [-155, 10, 0], 0.3: [-35, -10, 0], 0.5: [0, 0, 0]})},
            "left_arm": {"rotation": kf({0.0: [0, 0, 0], 0.15: [20, 0, -10], 0.3: [-20, 0, 0], 0.5: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.15: [-8, 10, 0], 0.3: [14, -10, 0], 0.5: [0, 0, 0]})},
        }, loop=False),
        "animation.goblin.aim": anim(1.0, {
            "right_arm": {"rotation": [-90, -6, 0]},
            "left_arm": {"rotation": [-90, 30, 0]},
        }),
        "animation.goblin.cast_loop": anim(1.0, {
            "right_arm": {"rotation": [f"-150 + {r} * 6", 0, 12]},
            "left_arm": {"rotation": [f"-150 - {r} * 6", 0, -12]},
            "body": {"rotation": [-6, 0, 0]},
        }),
        "animation.goblin.cast": anim(0.6, {
            "right_arm": {"rotation": kf({0.0: [-150, 0, 10], 0.2: [-95, 0, 0], 0.6: [0, 0, 0]})},
            "left_arm": {"rotation": kf({0.0: [-150, 0, -10], 0.2: [-80, 20, 0], 0.6: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [-8, 0, 0], 0.2: [10, 0, 0], 0.6: [0, 0, 0]})},
        }, loop=False),
        "animation.goblin.block": anim(0.5, {
            "left_arm": {"rotation": kf({0.0: [0, 0, 0], 0.08: [-55, 35, 0], 0.35: [-55, 35, 0], 0.5: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.08: [-6, 0, 0], 0.5: [0, 0, 0]})},
        }, loop=False),
        "animation.goblin.steal": anim(0.6, {
            "right_arm": {"rotation": kf({0.0: [0, 0, 0], 0.15: [-60, 0, 0], 0.35: [-110, -20, 0], 0.6: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.15: [25, 0, 0], 0.4: [0, 0, 0]})},
            "right_ear": {"rotation": kf({0.3: [0, 0, 0], 0.4: [0, 0, 15], 0.6: [0, 0, 0]})},
            "left_ear": {"rotation": kf({0.3: [0, 0, 0], 0.4: [0, 0, -15], 0.6: [0, 0, 0]})},
        }, loop=False),
        "animation.goblin.leap": anim(0.7, {
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.15: [22, 0, 0], 0.4: [-10, 0, 0], 0.7: [0, 0, 0]}),
                     "position": kf({0.0: [0, 0, 0], 0.15: [0, -1.5, 0], 0.4: [0, 0, 0]})},
            "right_leg": {"rotation": kf({0.0: [0, 0, 0], 0.15: [-30, 0, 0], 0.4: [20, 0, 0], 0.7: [0, 0, 0]})},
            "left_leg": {"rotation": kf({0.0: [0, 0, 0], 0.15: [-30, 0, 0], 0.4: [20, 0, 0], 0.7: [0, 0, 0]})},
            "right_arm": {"rotation": kf({0.0: [0, 0, 0], 0.15: [30, 0, 10], 0.4: [-170, 0, 5], 0.7: [-170, 0, 5]})},
            "left_arm": {"rotation": kf({0.0: [0, 0, 0], 0.15: [30, 0, -10], 0.4: [-160, 0, -5], 0.7: [-160, 0, -5]})},
        }, loop=False),
        "animation.goblin.slam": anim(0.7, {
            "right_arm": {"rotation": kf({0.0: [-170, 0, 5], 0.12: [-25, 0, 0], 0.7: [0, 0, 0]})},
            "left_arm": {"rotation": kf({0.0: [-160, 0, -5], 0.12: [-20, 0, 0], 0.7: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.12: [28, 0, 0], 0.7: [0, 0, 0]}),
                     "position": kf({0.0: [0, 0, 0], 0.12: [0, -1.2, 0], 0.7: [0, 0, 0]})},
        }, loop=False),
        "animation.goblin.summon": anim(1.4, {
            "right_arm": {"rotation": kf({0.0: [0, 0, 0], 0.25: [-170, 0, 12], 1.0: [-170, 0, 12], 1.4: [0, 0, 0]})},
            "left_arm": {"rotation": kf({0.0: [0, 0, 0], 0.25: [0, 0, -65], 1.0: [0, 0, -65], 1.4: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.25: [-12, 0, 0], 1.0: [-12, 0, 0], 1.4: [0, 0, 0]})},
            "cape": {"rotation": kf({0.0: [4, 0, 0], 0.25: [25, 0, 0], 1.0: [20, 0, 0], 1.4: [4, 0, 0]})},
        }, loop=False),
        "animation.goblin.cleave": anim(0.6, {
            "right_arm": {"rotation": kf({0.0: [-95, 70, 0], 0.25: [-95, -60, 0], 0.6: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 25, 0], 0.25: [6, -30, 0], 0.6: [0, 0, 0]})},
            "left_arm": {"rotation": kf({0.0: [0, 0, -20], 0.25: [-20, 0, -10], 0.6: [0, 0, 0]})},
        }, loop=False),
    }
    write_json(os.path.join(A, "animations/entity/goblin.animation.json"), {"format_version": "1.8.0", "animations": anims})


def rider_animations():
    w = "math.sin(query.anim_time * 450)"
    r = "math.sin(query.anim_time * 720)"
    rc = "math.cos(query.anim_time * 720)"
    anims = {
        "animation.goblin.idle": anim(2.0, {
            "tail": {"rotation": [0, 0, "math.sin(query.anim_time * 360) * 14"]},
            "wolf_head": {"rotation": ["math.sin(query.anim_time * 180) * 3", 0, 0]},
            "wolf_body": {"position": [0, "math.sin(query.anim_time * 180) * 0.2", 0]},
            "right_ear": {"rotation": kf({0.0: [0, 0, 0], 1.2: [0, 0, 0], 1.3: [0, 0, 12], 1.45: [0, 0, 0]})},
            "left_ear": {"rotation": kf({0.0: [0, 0, 0], 0.5: [0, 0, 0], 0.6: [0, 0, -12], 0.75: [0, 0, 0]})},
        }),
        "animation.goblin.walk": anim(0.8, {
            "front_right_leg": {"rotation": [f"{w} * 28", 0, 0]},
            "back_left_leg": {"rotation": [f"{w} * 28", 0, 0]},
            "front_left_leg": {"rotation": [f"-{w} * 28", 0, 0]},
            "back_right_leg": {"rotation": [f"-{w} * 28", 0, 0]},
            "wolf_body": {"position": [0, f"math.abs({w}) * 0.4", 0]},
            "tail": {"rotation": [f"{w} * 6", 0, f"{w} * 10"]},
            "rider": {"rotation": [f"{w} * 3", 0, 0]},
        }),
        "animation.goblin.run": anim(0.5, {
            "front_right_leg": {"rotation": [f"{r} * 50", 0, 0]},
            "front_left_leg": {"rotation": [f"{r} * 44", 0, 0]},
            "back_right_leg": {"rotation": [f"-{r} * 50", 0, 0]},
            "back_left_leg": {"rotation": [f"-{r} * 44", 0, 0]},
            "wolf_body": {"rotation": [f"{rc} * 6", 0, 0], "position": [0, f"math.abs({r}) * 1.2", 0]},
            "wolf_head": {"rotation": [f"-{rc} * 6", 0, 0]},
            "tail": {"rotation": [f"25 + {r} * 10", 0, 0]},
            "rider": {"rotation": [f"8 - {rc} * 6", 0, 0]},
            "right_ear": {"rotation": [-25, 0, -10]},
            "left_ear": {"rotation": [-25, 0, 10]},
        }),
        "animation.goblin.attack": anim(0.5, {
            "wolf_head": {"rotation": kf({0.0: [0, 0, 0], 0.12: [-20, 0, 0], 0.25: [18, 0, 0], 0.5: [0, 0, 0]})},
            "right_arm": {"rotation": kf({0.0: [0, 0, 0], 0.12: [20, 0, 0], 0.25: [-35, 0, 0], 0.5: [0, 0, 0]}),
                          "position": kf({0.0: [0, 0, 0], 0.12: [0, 0, 2], 0.25: [0, 0, -3], 0.5: [0, 0, 0]})},
            "body": {"rotation": kf({0.0: [0, 0, 0], 0.25: [12, 0, 0], 0.5: [0, 0, 0]})},
        }, loop=False),
    }
    write_json(os.path.join(A, "animations/entity/goblin_wolf_rider.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =================================================================================================
#  ITENS (texturas 16x16 + modelos)
# =================================================================================================
def gel_item(name, base, extra=None):
    c = Canvas(16, 16)
    base = rgba(base)
    # gota de gel achatada
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.2, (y + 0.5 - 9.5) / 5.0
            if dx * dx + dy * dy <= 1.0 or (abs(x + 0.5 - 8) < 2.2 - (y - 2) * 0.0 and 3 <= y <= 6 and abs(x + 0.5 - 8) < (y - 2) * 0.9):
                c.set(x, y, base)
    c.shade_sphere(8, 9, 6.5, light=0.55, dark=0.45)
    c.set(5, 7, (255, 255, 255, 255)); c.set(6, 6, (255, 255, 255, 255)); c.set(5, 8, (255, 255, 255, 200))
    if extra:
        extra(c)
    c.outline(rgba(darken(base, 0.55)))
    c.save(os.path.join(A, "textures/item", name + ".png"))


def items():
    gel_item("slime_gel", (79, 168, 255))
    gel_item("fire_slime_gel", (255, 122, 46), lambda c: [c.set(x, y, col) for (x, y, col) in
                                                          [(8, 2, (255, 240, 140, 255)), (8, 3, (255, 200, 60, 255)), (9, 3, (255, 160, 40, 255)),
                                                           (7, 4, (255, 170, 50, 255)), (10, 5, (255, 220, 90, 255))]])
    gel_item("water_slime_gel", (63, 198, 232), lambda c: [c.set(x, y, (230, 250, 255, 255)) for (x, y) in [(10, 10), (9, 12), (11, 8)]])
    gel_item("stone_slime_gel", (138, 132, 116), lambda c: [c.set(x, y, col) for (x, y, col) in
                                                            [(9, 10, (90, 90, 94, 255)), (10, 10, (120, 120, 124, 255)), (7, 12, (100, 100, 104, 255)),
                                                             (11, 12, (94, 140, 58, 255))]])
    gel_item("wind_slime_gel", (159, 240, 208), lambda c: [c.set(x, y, (255, 255, 255, 255)) for (x, y) in
                                                           [(6, 10), (7, 11), (8, 11), (9, 10), (9, 9), (8, 8)]])
    gel_item("poison_jelly_gel", (155, 79, 216), lambda c: [c.set(x, y, (139, 224, 78, 255)) for (x, y) in [(10, 9), (8, 12), (11, 11), (6, 11)]])

    # orelha de goblin
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "..............o.",
        ".............oSo",
        "............oSSo",
        "...........oSSo.",
        "..........oSPSo.",
        ".........oSPPSo.",
        "........oSPPSo..",
        ".......oSPPPSo..",
        "......oSSPPSo...",
        ".....oSSPPSSo...",
        "....oSSSPSSo....",
        "...oSSSSSSo.....",
        "...oDSSSSo......",
        "....oDDDo.......",
        ".....ooo........",
    ], {"o": rgba(darken(rgba(SKIN), 0.6)), "S": rgba(SKIN), "P": rgba(EAR_IN), "D": rgba(SKIN_D)})
    c.noise(0.06, 5)
    c.save(os.path.join(A, "textures/item/goblin_ear.png"))

    # adaga tosca (lamina de pedra lascada, cabo enrolado)
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "............o...",
        "...........oLo..",
        "..........oLGo..",
        ".........oLGGo..",
        "........oLGGo...",
        ".......oLGGo....",
        "......oLGGo.....",
        "..o..oLGGo......",
        "..oBoGGGo.......",
        "...oBBGo........",
        "....oWWo........",
        "...oWWoBo.......",
        "..oWWo..o.......",
        ".oKKo...........",
        "..oo............",
    ], {"o": (40, 36, 34, 255), "L": (190, 190, 186, 255), "G": (130, 130, 128, 255), "B": rgba(LEATHER_D),
        "W": rgba(LEATHER), "K": rgba(LEATHER_D)})
    c.save(os.path.join(A, "textures/item/crude_dagger.png"))

    # porrete goblin (madeira com pregos)
    c = Canvas(16, 16)
    c.from_rows([
        "..........oooo..",
        ".........oLLWWo.",
        "........oLWNWWo.",
        "........oWWWWNo.",
        ".......oLWNWWWo.",
        ".......oWWWWWo..",
        "......oLWWWNo...",
        ".....oWWWWoo....",
        "....oWWWoo......",
        "...oWWDo........",
        "..oWWDo.........",
        "..oKKo..........",
        ".oKKo...........",
        "oKKo............",
        "ooo.............",
        "................",
    ], {"o": (40, 28, 18, 255), "L": rgba(lighten(rgba(WOOD), 0.25)), "W": rgba(WOOD), "D": rgba(WOOD_D),
        "N": (200, 200, 205, 255), "K": rgba(LEATHER_D)})
    c.save(os.path.join(A, "textures/item/goblin_club.png"))

    # fetiche goblin (osso + penas + conta)
    c = Canvas(16, 16)
    c.from_rows([
        "....o...........",
        "...oRo..o.......",
        "...oRo.oTo......",
        "...oRooTo.......",
        "....oRoTo.......",
        ".....ooo........",
        "....oBBBo.......",
        "...oBWWBo.......",
        "...oBWWBo.......",
        "....oBBo........",
        ".....oGo........",
        "....oBBBo.......",
        "...oBWWWBo......",
        "...oBWoWBo......",
        "....oBBBo.......",
        ".....ooo........",
    ], {"o": (50, 40, 36, 255), "R": (200, 50, 40, 255), "T": (40, 170, 160, 255), "B": (210, 200, 172, 255),
        "W": (240, 234, 214, 255), "G": (120, 240, 220, 255)})
    c.save(os.path.join(A, "textures/item/goblin_fetish.png"))

    # machado do rei goblin
    c = Canvas(16, 16)
    c.from_rows([
        "......ooooo.....",
        ".....oLLLIIo....",
        "....oLIIIIIIo...",
        "....oLIIIIIIIo..",
        "....oLIIIIDIIo..",
        ".....oLIIoDDIo..",
        "......ooooDDo...",
        ".........oGGo...",
        "........oWWo....",
        ".......oWWo.....",
        "......oWWo......",
        ".....oWWo.......",
        "....oGGo........",
        "...oWWo.........",
        "..oWWo..........",
        "..ooo...........",
    ], {"o": (36, 34, 34, 255), "L": rgba(IRON_L), "I": rgba(IRON), "D": rgba(IRON_D), "G": rgba(GOLD),
        "W": rgba(WOOD)})
    c.save(os.path.join(A, "textures/item/goblin_king_greataxe.png"))

    # texturas das coroas (usadas pelo modelo 3D)
    def crown_tex(name, main, light, dark, gem, gem2):
        c = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                col = main
                if y in (0, 4):
                    col = light
                elif y in (3, 7):
                    col = dark
                c.set(x, y, rgba(col))
        for x in range(1, 16, 4):
            c.set(x, 1, rgba(gem)); c.set(x + 1, 2, rgba(gem2))
        # pontas (linhas 8-11): gradiente
        for y in range(8, 12):
            for x in range(16):
                c.set(x, y, rgba(mix(rgba(light), rgba(main), (y - 8) / 3)))
        # gemas (12-15)
        for x in range(0, 4):
            for y in range(12, 16):
                c.set(x, y, rgba(gem))
        for x in range(4, 8):
            for y in range(12, 16):
                c.set(x, y, rgba(gem2))
        c.set(0, 12, (255, 255, 255, 255)); c.set(4, 12, (255, 255, 255, 255))
        for x in range(8, 16):
            for y in range(12, 16):
                c.set(x, y, (150, 24, 40, 255))
        c.noise(0.04, 7)
        c.save(os.path.join(A, "textures/item", name + ".png"))
    crown_tex("king_slime_crown", GOLD, GOLD_L, GOLD_D, RUBY, SAPPHIRE)
    crown_tex("goblin_king_crown", (196, 150, 60), (230, 196, 110), (130, 92, 36), RUBY, EMERALD)

    # modelos simples
    for name in ["slime_gel", "fire_slime_gel", "water_slime_gel", "stone_slime_gel", "wind_slime_gel", "poison_jelly_gel",
                 "goblin_ear", "goblin_fetish"]:
        write_json(os.path.join(A, "models/item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    for name in ["crude_dagger", "goblin_club", "goblin_king_greataxe"]:
        write_json(os.path.join(A, "models/item", name + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
    for name in ["king_slime_crown", "goblin_king_crown"]:
        write_json(os.path.join(A, "models/item", name + ".json"), crown_model(name))
    for ent in ENTITIES:
        write_json(os.path.join(A, "models/item", ent + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def crown_model(name):
    """Coroa 3D (elementos de bloco). A transformacao "head" a coloca em cima da cabeca."""
    t = f"{NS}:item/{name}"
    band_uv = [0, 0, 16, 4]
    elems = []

    def box(frm, to, uv, faces=("north", "south", "east", "west", "up", "down")):
        elems.append({"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#t"} for f in faces}})
    # aro (4 paredes)
    box([2, 0, 2], [14, 3, 3], band_uv)
    box([2, 0, 13], [14, 3, 14], band_uv)
    box([2, 0, 3], [3, 3, 13], band_uv)
    box([13, 0, 3], [14, 3, 13], band_uv)
    # forro de veludo
    box([3, 0, 3], [13, 1, 13], [8, 12, 16, 16], ("up",))
    # pontas
    tip = [0, 8, 2, 11]
    for frm, to in [([7, 3, 2], [9, 7, 3]), ([2, 3, 2], [4, 5, 3]), ([12, 3, 2], [14, 5, 3]),
                    ([7, 3, 13], [9, 6, 14]), ([2, 3, 13], [4, 5, 14]), ([12, 3, 13], [14, 5, 14]),
                    ([2, 3, 7], [3, 5, 9]), ([13, 3, 7], [14, 5, 9])]:
        box(frm, to, tip)
    # gemas
    box([7, 1, 1.5], [9, 2.5, 2], [0, 12, 2, 14], ("north", "east", "west", "up", "down"))
    box([7, 1, 14], [9, 2.5, 14.5], [4, 12, 6, 14], ("south", "east", "west", "up", "down"))
    return {
        "parent": "minecraft:block/block",
        "textures": {"t": t, "particle": t},
        "elements": elems,
        "display": {
            "head": {"rotation": [0, 0, 0], "translation": [0, 14.5, 0], "scale": [1.15, 1.15, 1.15]},
            "gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 4, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 4, 0], "scale": [0.45, 0.45, 0.45]},
        },
    }


# =================================================================================================
#  DADOS: loot, spawns, receitas, tags
# =================================================================================================
ENTITIES = ["slime", "fire_slime", "water_slime", "stone_slime", "wind_slime", "poison_jelly", "king_slime",
            "goblin", "goblin_archer", "goblin_shaman", "goblin_wolf_rider", "hobgoblin", "goblin_king"]


def item_entry(name, mn, mx, looting=None, conditions=None, weight=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else f"{NS}:{name}", "functions": []}
    if (mn, mx) != (1, 1):
        e["functions"].append({"function": "minecraft:set_count",
                               "count": mn if mn == mx else {"type": "minecraft:uniform", "min": mn, "max": mx}})
    if looting:
        e["functions"].append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                               "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    if not e["functions"]:
        del e["functions"]
    if conditions:
        e["conditions"] = conditions
    if weight:
        e["weight"] = weight
    return e


def pool(*entries, rolls=1, conditions=None):
    p = {"rolls": rolls, "bonus_rolls": 0, "entries": list(entries)}
    if conditions:
        p["conditions"] = conditions
    return p


KILLED = {"condition": "minecraft:killed_by_player"}
SIZE1 = {"condition": "minecraft:entity_properties", "entity": "this",
         "predicate": {"type_specific": {"type": "minecraft:slime", "size": 1}}}


def chance(unenchanted, per_level):
    return {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
            "unenchanted_chance": unenchanted,
            "enchanted_chance": {"type": "minecraft:linear", "base": unenchanted + per_level, "per_level_above_first": per_level}}


def loot(name, pools):
    write_json(os.path.join(D, "loot_table/entities", name + ".json"),
               {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{name}"})


def loot_tables():
    slimes = {"slime": ("slime_gel", "essence_water", 0.02), "fire_slime": ("fire_slime_gel", "essence_fire", 0.04),
              "water_slime": ("water_slime_gel", "essence_water", 0.04), "stone_slime": ("stone_slime_gel", "essence_earth", 0.04),
              "wind_slime": ("wind_slime_gel", "essence_wind", 0.04), "poison_jelly": ("poison_jelly_gel", "essence_shadow", 0.04)}
    for ent, (gel, essence, ch) in slimes.items():
        pools = [pool(item_entry(gel, 0, 2, looting=1), conditions=[SIZE1])]
        if ent != "slime":
            pools.append(pool(item_entry("minecraft:slime_ball", 0, 1, looting=1), conditions=[SIZE1]))
        pools.append(pool(item_entry(essence, 1, 1), conditions=[KILLED, chance(ch, 0.01)]))
        loot(ent, pools)
    loot("king_slime", [
        pool(item_entry("king_slime_crown", 1, 1)),
        pool(item_entry("slime_gel", 6, 12, looting=2)),
        pool(item_entry("water_slime_gel", 1, 3), item_entry("poison_jelly_gel", 1, 3), rolls=2),
        pool(item_entry("essence_water", 1, 2)),
        pool(item_entry("mana_crystal", 1, 3)),
        pool(item_entry("minecraft:slime_block", 1, 2)),
    ])
    ear = item_entry("goblin_ear", 0, 1, looting=1)
    loot("goblin", [pool(ear, conditions=[KILLED]),
                    pool(item_entry("minecraft:gold_nugget", 0, 2, looting=1)),
                    pool(item_entry("minecraft:emerald", 1, 1), conditions=[KILLED, chance(0.03, 0.01)])])
    loot("goblin_archer", [pool(ear, conditions=[KILLED]),
                           pool(item_entry("minecraft:arrow", 0, 3, looting=1)),
                           pool(item_entry("minecraft:string", 0, 1, looting=1))])
    loot("goblin_shaman", [pool(ear, conditions=[KILLED]),
                           pool(item_entry("goblin_fetish", 1, 1), conditions=[KILLED, chance(0.35, 0.05)]),
                           pool(item_entry("essence_arcane", 1, 1), conditions=[KILLED, chance(0.06, 0.02)]),
                           pool(item_entry("minecraft:glass_bottle", 0, 1))])
    loot("goblin_wolf_rider", [pool(ear, conditions=[KILLED]),
                               pool(item_entry("minecraft:leather", 0, 2, looting=1)),
                               pool(item_entry("minecraft:bone", 0, 2, looting=1))])
    loot("hobgoblin", [pool(item_entry("goblin_ear", 1, 2, looting=1), conditions=[KILLED]),
                       pool(item_entry("minecraft:iron_nugget", 1, 4, looting=1)),
                       pool(item_entry("minecraft:gold_nugget", 0, 3, looting=1)),
                       pool(item_entry("crude_dagger", 1, 1), conditions=[chance(0.05, 0.02)])])
    loot("goblin_king", [
        pool(item_entry("goblin_king_crown", 1, 1)),
        pool(item_entry("goblin_king_greataxe", 1, 1)),
        pool(item_entry("minecraft:gold_ingot", 3, 6, looting=2)),
        pool(item_entry("minecraft:emerald", 2, 5, looting=2)),
        pool(item_entry("goblin_ear", 2, 4)),
        pool(item_entry("mana_crystal", 1, 3)),
        pool(item_entry("essence_earth", 1, 2)),
    ])


def biomes(*items_):
    vals = list(items_)
    return vals[0] if len(vals) == 1 else {"type": "neoforge:or", "values": vals}


def spawn(name, biome_set, entries):
    write_json(os.path.join(D, "neoforge/biome_modifier", "creatures_a_" + name + ".json"), {
        "type": "neoforge:add_spawns",
        "biomes": biome_set,
        "spawners": [{"type": f"{NS}:{e}", "weight": w, "minCount": mn, "maxCount": mx} for (e, w, mn, mx) in entries],
    })


def spawns():
    spawn("slime", biomes("#c:is_swamp", "#minecraft:is_forest", "#c:is_plains"), [("slime", 22, 1, 3)])
    spawn("fire_slime", biomes("#c:is_desert", "#minecraft:is_badlands", "#minecraft:is_savanna"), [("fire_slime", 18, 1, 3)])
    spawn("fire_slime_nether", biomes("minecraft:nether_wastes", "minecraft:basalt_deltas"), [("fire_slime", 12, 1, 3)])
    spawn("water_slime", biomes("#c:is_swamp", "#minecraft:is_river", "#minecraft:is_beach", "minecraft:lush_caves"),
          [("water_slime", 18, 1, 3)])
    spawn("stone_slime", biomes("#minecraft:is_overworld"), [("stone_slime", 8, 1, 2)])
    spawn("wind_slime", biomes("#minecraft:is_mountain", "#c:is_windswept", "#minecraft:is_hill", "minecraft:meadow",
                               "minecraft:cherry_grove"), [("wind_slime", 20, 1, 3)])
    spawn("poison_jelly", biomes("#c:is_swamp", "#minecraft:is_jungle"), [("poison_jelly", 16, 1, 2)])
    spawn("king_slime", biomes("#c:is_swamp"), [("king_slime", 2, 1, 1)])
    goblin_lands = biomes("#c:is_plains", "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna")
    spawn("goblins", goblin_lands, [("goblin", 15, 2, 5), ("goblin_archer", 8, 1, 3), ("goblin_shaman", 3, 1, 1),
                                    ("hobgoblin", 3, 1, 1)])
    spawn("goblin_riders", biomes("#c:is_plains", "#minecraft:is_taiga", "#minecraft:is_savanna"), [("goblin_wolf_rider", 4, 1, 2)])


def recipes():
    def shapeless(name, ingredients, result, count=1):
        write_json(os.path.join(D, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": i if ":" in i else f"{NS}:{i}"} for i in ingredients],
            "result": {"id": result, "count": count}})
    shapeless("slime_ball_from_slime_gel", ["slime_gel"], "minecraft:slime_ball")
    shapeless("magma_cream_from_fire_slime_gel", ["fire_slime_gel"], "minecraft:magma_cream")
    shapeless("fermented_spider_eye_from_poison_jelly_gel", ["poison_jelly_gel", "minecraft:sugar", "minecraft:brown_mushroom"],
              "minecraft:fermented_spider_eye")


def tags():
    def tag(path, values):
        write_json(os.path.join(MERGE, path), {"values": values})
    tag("data/minecraft/tags/entity_type/can_breathe_under_water.json", [f"{NS}:water_slime"])
    tag("data/minecraft/tags/entity_type/frog_food.json", [f"{NS}:slime", f"{NS}:water_slime", f"{NS}:wind_slime"])
    tag("data/c/tags/entity_type/bosses.json", [f"{NS}:king_slime", f"{NS}:goblin_king"])


# =================================================================================================
#  TRADUCOES
# =================================================================================================
def lang():
    names = {
        "slime": ("Slime Azul", "Blue Slime"),
        "fire_slime": ("Slime de Fogo", "Fire Slime"),
        "water_slime": ("Slime de Água", "Water Slime"),
        "stone_slime": ("Slime de Pedra", "Stone Slime"),
        "wind_slime": ("Slime de Vento", "Wind Slime"),
        "poison_jelly": ("Gelatina Venenosa", "Poison Jelly"),
        "king_slime": ("Rei Slime", "King Slime"),
        "goblin": ("Goblin", "Goblin"),
        "goblin_archer": ("Goblin Arqueiro", "Goblin Archer"),
        "goblin_shaman": ("Xamã Goblin", "Goblin Shaman"),
        "goblin_wolf_rider": ("Cavaleiro de Lobo Goblin", "Goblin Wolf Rider"),
        "hobgoblin": ("Hobgoblin", "Hobgoblin"),
        "goblin_king": ("Rei Goblin", "Goblin King"),
    }
    items_ = {
        "slime_gel": ("Gel de Slime", "Slime Gel", "Gelatina azul e fresquinha. Vira bola de slime na bancada.",
                      "Cool blue jelly. Crafts into a slimeball."),
        "fire_slime_gel": ("Gel de Slime de Fogo", "Fire Slime Gel", "Morno ao toque. Queima muito tempo na fornalha e vira creme de magma.",
                           "Warm to the touch. Burns for a long time and crafts into magma cream."),
        "water_slime_gel": ("Gel de Slime de Água", "Water Slime Gel", "Comer: respirar debaixo d'água por 45 s e apagar o fogo.",
                            "Eat: water breathing for 45 s and puts out fire."),
        "stone_slime_gel": ("Gel de Slime de Pedra", "Stone Slime Gel", "Comer: Resistência por 30 s (mas pesa no estômago).",
                            "Eat: Resistance for 30 s (but it sits heavy)."),
        "wind_slime_gel": ("Gel de Slime de Vento", "Wind Slime Gel", "Comer: Velocidade e Salto por 30 s.",
                           "Eat: Speed and Jump Boost for 30 s."),
        "poison_jelly_gel": ("Gel de Gelatina Venenosa", "Poison Jelly Gel", "Tóxico. Com açúcar e cogumelo marrom vira olho de aranha fermentado.",
                             "Toxic. With sugar and a brown mushroom it becomes a fermented spider eye."),
        "king_slime_crown": ("Coroa do Rei Slime", "King Slime's Crown", "Troféu real e gelatinoso. Pode ser usada na cabeça.",
                             "A royal, gelatinous trophy. Can be worn on the head."),
        "goblin_ear": ("Orelha de Goblin", "Goblin Ear", None, None),
        "goblin_fetish": ("Fetiche Goblin", "Goblin Fetish", "Amuleto de osso e penas de um xamã goblin. Ainda formiga de magia.",
                          "A bone-and-feather charm from a goblin shaman. It still tingles with magic."),
        "crude_dagger": ("Adaga Tosca", "Crude Dagger", None, None),
        "goblin_club": ("Porrete Goblin", "Goblin Club", None, None),
        "goblin_king_greataxe": ("Machado do Rei Goblin", "Goblin King's Greataxe", None, None),
        "goblin_king_crown": ("Coroa do Rei Goblin", "Goblin King's Crown", "Pesada, torta e roubada de alguém. Pode ser usada na cabeça.",
                              "Heavy, crooked and stolen from someone. Can be worn on the head."),
    }
    extra = {
        "boss.isekai_mahou.king_slime.subtitle": ("Soberano Gelatinoso do Pântano", "Gelatinous Sovereign of the Swamp"),
        "boss.isekai_mahou.king_slime.phase": ("A geleia real ferve de raiva!", "The royal jelly boils with rage!"),
        "boss.isekai_mahou.goblin_king.subtitle": ("Senhor da Fortaleza Goblin", "Lord of the Goblin Fortress"),
        "boss.isekai_mahou.goblin_king.phase": ("A Fúria do Rei!", "The King's Fury!"),
        "announce.isekai_mahou.king_slime.slam": ("Esmagamento Real!", "Royal Crush!"),
        "announce.isekai_mahou.king_slime.split": ("Súditos, dividam-se!", "Subjects, divide!"),
        "announce.isekai_mahou.king_slime.rage": ("Vocês ousam ferir a Coroa Gelatinosa?!", "You dare wound the Jelly Crown?!"),
        "announce.isekai_mahou.king_slime.defeated": ("O Rei Slime foi derrotado!", "The King Slime has been defeated!"),
        "announce.isekai_mahou.goblin_king.leap": ("Pulo Esmagador!", "Crushing Leap!"),
        "announce.isekai_mahou.goblin_king.summon": ("Às armas, meus súditos!", "To arms, my subjects!"),
        "announce.isekai_mahou.goblin_king.rage": ("Grraah! Vocês vão pagar com ouro e sangue!", "Grraah! You'll pay in gold and blood!"),
        "announce.isekai_mahou.goblin_king.defeated": ("O Rei Goblin caiu!", "The Goblin King has fallen!"),
    }
    pt, en = {}, {}
    for ent, (p, e) in names.items():
        pt[f"entity.{NS}.{ent}"] = p
        en[f"entity.{NS}.{ent}"] = e
        pt[f"item.{NS}.{ent}_spawn_egg"] = f"Ovo Gerador de {p}"
        en[f"item.{NS}.{ent}_spawn_egg"] = f"{e} Spawn Egg"
    for it, (p, e, tp, te) in items_.items():
        pt[f"item.{NS}.{it}"] = p
        en[f"item.{NS}.{it}"] = e
        if tp:
            pt[f"tooltip.{NS}.{it}"] = tp
            en[f"tooltip.{NS}.{it}"] = te
    for k, (p, e) in extra.items():
        pt[k] = p
        en[k] = e
    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


# =================================================================================================
if __name__ == "__main__":
    for s in SLIME_PAL:
        slime_model(s)
    goblin_model()
    goblin_archer_model()
    goblin_shaman_model()
    hobgoblin_model()
    goblin_king_model()
    wolf_rider_model()
    slime_animations()
    goblin_animations()
    rider_animations()
    items()
    loot_tables()
    spawns()
    recipes()
    tags()
    lang()
    print("creatures_a: assets gerados")
