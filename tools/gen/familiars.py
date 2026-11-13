"""
Gera os assets do modulo familiars (Familiares: 10 formas x 3 estagios).
Uso (na raiz do projeto):  py -I tools/gen/familiars.py

Saidas:
  assets/isekai_mahou/geo/entity/fam_<forma>.geo.json              (GeckoLib, Bedrock 1.12.0, UV de caixa)
  assets/isekai_mahou/animations/entity/fam_<forma>.animation.json (idle, move, sit, alert, happy, find)
  assets/isekai_mahou/textures/entity/fam_<forma>_<1|2|3>.png (+ _glowmask)
  assets/isekai_mahou/textures/item/bond_incense.png + models/item/bond_incense.json
  assets/isekai_mahou/textures/gui/tab/familiar.png
  data/isekai_mahou/recipe/bond_incense.json
  src/main/lang/familiars/{pt_br,en_us}.json

Cada forma tem UM modelo; os enfeites de evolucao sao ossos "s2_*" / "s3_*" (o renderer so mostra a partir do
estagio 2 / 3) e cada estagio tem a sua textura (filhote mais claro, ultimo estagio com runas brilhantes).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix, item_model  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "familiars")
PI = math.pi


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def tone(c, stage):
    """Filhote mais claro/apagado; ultimo estagio um pouco mais vivo."""
    if stage == 1:
        return mix(c, (255, 255, 255, 255), 0.14)
    if stage == 3:
        avg = (c[0] + c[1] + c[2]) / 3.0
        return tuple(max(0, min(255, int(v + (v - avg) * 0.22))) if i < 3 else v for i, v in enumerate(c))
    return c


# =====================================================================================================
#  Modelo (UV de caixa) + pintura
# =====================================================================================================
def box(x, y, z, w, h, d, paint):
    return ([x, y, z], [w, h, d], paint)


class Geo:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []   # (indice do osso, origem, tamanho, pintura)

    def bone(self, name, parent, pivot, cubes=(), rotation=None):
        b = {"name": name, "pivot": pivot}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = rotation
        self.bones.append(b)
        for (origin, size, paint) in cubes:
            self.cubes.append((len(self.bones) - 1, origin, size, paint))

    def names(self):
        return {b["name"] for b in self.bones}

    def pack(self):
        """Empacota as caixas; cubos com a mesma chave "share" e tamanho dividem o mesmo pedaco da textura."""
        rects, key_of = {}, {}
        for i, (_, _, size, paint) in enumerate(self.cubes):
            w, h, d = (int(math.ceil(s)) for s in size)
            key = (paint.get("share"), w, h, d) if paint.get("share") else ("#", i)
            key_of[i] = key
            rects.setdefault(key, (2 * (d + w), d + h))
        for tw, th in ((64, 32), (64, 64), (128, 64), (128, 128)):
            at, x, y, shelf, ok = {}, 0, 0, 0, True
            for key, (bw, bh) in sorted(rects.items(), key=lambda t: (-t[1][1], -t[1][0], str(t[0]))):
                if x + bw > tw:
                    x, y, shelf = 0, y + shelf, 0
                if y + bh > th or bw > tw:
                    ok = False
                    break
                at[key] = (x, y)
                x += bw
                shelf = max(shelf, bh)
            if ok:
                return tw, th, {i: at[key_of[i]] for i in key_of}
        raise RuntimeError("modelo grande demais para a textura: " + self.ident)

    def build(self, seed):
        tw, th, uv = self.pack()
        canvas, glow = Canvas(tw, th), Canvas(tw, th)
        bones = [dict(b, cubes=[]) for b in self.bones]
        painted = set()
        for i, (bi, origin, size, paint) in enumerate(self.cubes):
            u, v = uv[i]
            bones[bi]["cubes"].append({"origin": origin, "size": size, "uv": [u, v]})
            if (u, v) in painted:
                continue
            painted.add((u, v))
            paint_box(canvas, glow, u, v, [int(math.ceil(s)) for s in size], paint, random.Random(seed * 131 + u * 7 + v))
        for b in bones:
            if not b["cubes"]:
                del b["cubes"]
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": tw, "texture_height": th,
                            "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 0.75, 0]},
            "bones": bones}]}
        return geo, canvas, glow


def paint_box(c, g, u, v, size, p, rnd):
    """Pinta as 6 faces: topo claro, base escura, laterais em degrade; pelagem, barriga, marcas, runas e brilho."""
    w, h, d = size
    base = p["color"]
    top, bottom = lighten(base, p.get("top", 0.16)), darken(base, 0.28)
    full_w, full_h = 2 * (d + w), d + h
    c.rect(u + d, v, u + d + w - 1, v + d - 1, top)
    c.rect(u + d + w, v, u + d + 2 * w - 1, v + d - 1, p.get("belly", bottom))
    belly_rows = p.get("belly_rows", 0)
    for yy in range(h):
        t = yy / max(1, h - 1)
        col = lighten(base, 0.10 * (1 - 2 * t)) if t < 0.5 else darken(base, 0.22 * (t - 0.5) * 2)
        band = p.get("band")
        if band and yy >= h - band[0]:
            col = band[1]
        if belly_rows and yy >= h - belly_rows and "belly" in p:
            col = p["belly"]
        c.rect(u, v + d + yy, u + full_w - 1, v + d + yy, col)
    fleck = p.get("fleck")
    if fleck:
        light, dark, amount = fleck
        for _ in range(max(1, int(full_w * full_h * amount))):
            x, y = rnd.randint(u, u + full_w - 1), rnd.randint(v, v + full_h - 1)
            if c.get(x, y)[3] > 0:
                c.set(x, y, light if rnd.random() < 0.5 else dark)
    stripes = p.get("stripes")
    if stripes:
        # listras nas faces de cima/baixo (penas, membranas de asa)
        for face_u in (u + d, u + d + w):
            for xx in range(w):
                for yy in range(d):
                    if xx % 2 == 1:
                        c.set(face_u + xx, v + yy, stripes)
    fx, fy = u + d, v + d
    for (ex, ey, col) in p.get("front", []):
        c.set(fx + ex, fy + ey, col)
    for (ex, ey, col) in p.get("glow_front", []):
        c.set(fx + ex, fy + ey, col)
        g.set(fx + ex, fy + ey, col)
    for (ex, ey, col) in p.get("top_marks", []):
        c.set(u + d + ex, v + ey, col)
    for (ex, ey, col) in p.get("glow_top", []):
        c.set(u + d + ex, v + ey, col)
        g.set(u + d + ex, v + ey, col)
    runes = p.get("runes")
    if runes:
        col, amount = runes
        for _ in range(max(1, int(full_w * h * amount))):
            x, y = rnd.randint(u, u + full_w - 1), rnd.randint(v + d, v + full_h - 1)
            if c.get(x, y)[3] > 0:
                c.set(x, y, col)
                g.set(x, y, col)
    if p.get("glow"):
        for yy in range(full_h):
            for xx in range(full_w):
                px = c.get(u + xx, v + yy)
                if px[3] > 0:
                    g.set(u + xx, v + yy, px)


# =====================================================================================================
#  As 10 formas  (x: esquerda +, y: cima, frente = -z)
# =====================================================================================================
def quad_legs(g, parent, x_in, x_out, zf, zb, h, w, d_back, paint, pivot_y):
    """Quatro patas (leg_fl/fr/bl/br) de um quadrupede."""
    g.bone("leg_fl", parent, [x_in + w / 2, pivot_y, zf + 0.5], [box(x_in, 0, zf, w, h, 1, paint)])
    g.bone("leg_fr", parent, [x_out + w / 2, pivot_y, zf + 0.5], [box(x_out, 0, zf, w, h, 1, paint)])
    g.bone("leg_bl", parent, [x_in + w / 2, pivot_y, zb + d_back / 2], [box(x_in, 0, zb, w, h, d_back, paint)])
    g.bone("leg_br", parent, [x_out + w / 2, pivot_y, zb + d_back / 2], [box(x_out, 0, zb, w, h, d_back, paint)])


def ember_fox(stage):
    fur, cream, dark = tone(rgb("#E8722A"), stage), rgb("#FFE6C0"), rgb("#4A2618")
    flame, hot = rgb("#FFB13B"), rgb("#FFF0A0")
    runes = {"runes": (hot, 0.05)} if stage >= 3 else {}
    body = dict({"color": fur, "belly": cream, "belly_rows": 1, "fleck": (lighten(fur, 0.15), darken(fur, 0.15), 0.08)}, **runes)
    head = {"color": fur, "fleck": (lighten(fur, 0.15), darken(fur, 0.15), 0.06),
            "glow_front": [(1, 1, hot), (3, 1, hot)], "front": [(0, 3, cream), (1, 3, cream), (3, 3, cream), (4, 3, cream), (2, 3, cream)]}
    snout = {"color": cream, "front": [(0, 0, dark), (1, 0, dark)]}
    ear = {"color": dark, "top": 0.05, "front": [(0, 1, fur)]}
    leg = {"color": dark, "band": (1, darken(dark, 0.2))}
    tail = {"color": fur, "share": "tail", "fleck": (lighten(fur, 0.2), darken(fur, 0.1), 0.1)}
    tip = {"color": flame, "share": "tip", "glow": True, "top": 0.5, "fleck": (hot, rgb("#FF7A2A"), 0.25)}
    g = Geo("fam_ember_fox")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 6, 0], [box(-2, 4, -4, 4, 4, 8, body)])
    g.bone("head", "body", [0, 7, -4], [box(-2.5, 6, -8, 5, 4, 4, head), box(-1, 6, -10, 2, 2, 2, snout),
                                        box(-2.5, 10, -6, 1, 2, 1, ear), box(1.5, 10, -6, 1, 2, 1, ear)])
    tail_cubes = [box(-1, 6, 4, 2, 2, 5, tail), box(-1.5, 5.5, 9, 3, 3, 3, tip)]
    g.bone("tail", "body", [0, 7, 4], tail_cubes, rotation=[10, 0, 0])
    quad_legs(g, "body", 1, -2, -3.5, 2.5, 4, 1, 1, leg, 4)
    g.bone("s2_tail_l", "body", [0, 7, 4], tail_cubes, rotation=[14, -32, 0])
    g.bone("s2_tail_r", "body", [0, 7, 4], tail_cubes, rotation=[14, 32, 0])
    for i, (pitch, yaw) in enumerate(((34, 16), (26, 50), (12, 72))):
        g.bone("s3_tail_l%d" % i, "body", [0, 7, 4], tail_cubes, rotation=[pitch, -yaw, 0])
        g.bone("s3_tail_r%d" % i, "body", [0, 7, 4], tail_cubes, rotation=[pitch, yaw, 0])
    g.bone("s3_mane", "body", [0, 7, -4], [box(-3, 5, -4.5, 6, 5, 1, {"color": flame, "glow": True, "fleck": (hot, rgb("#FF7A2A"), 0.3)})])
    return g


def tide_otter(stage):
    fur, cream, dark = tone(rgb("#3F86B8"), stage), rgb("#E8F6F2"), rgb("#1E3A56")
    teal, pearl = rgb("#7FF0E0"), rgb("#F4FFFF")
    runes = {"runes": (teal, 0.05)} if stage >= 3 else {}
    body = dict({"color": fur, "belly": cream, "belly_rows": 1, "fleck": (lighten(fur, 0.14), darken(fur, 0.14), 0.06)}, **runes)
    head = {"color": fur, "front": [(0, 1, dark), (3, 1, dark), (0, 2, cream), (1, 2, cream), (2, 2, cream), (3, 2, cream)]}
    snout = {"color": cream, "front": [(0, 0, dark), (1, 0, dark)]}
    ear = {"color": darken(fur, 0.2)}
    leg = {"color": darken(fur, 0.25), "band": (1, dark)}
    tail = {"color": fur, "fleck": (lighten(fur, 0.14), darken(fur, 0.14), 0.06)}
    fin = {"color": teal, "glow": True, "top": 0.4, "fleck": (pearl, rgb("#3FC8C0"), 0.2)}
    g = Geo("fam_tide_otter")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 3.5, 0], [box(-2, 2, -5, 4, 3, 10, body)])
    g.bone("head", "body", [0, 4, -5], [box(-2, 3, -8, 4, 3, 3, head), box(-1, 3, -9, 2, 2, 1, snout),
                                        box(-2, 6, -6, 1, 1, 1, ear), box(1, 6, -6, 1, 1, 1, ear)])
    g.bone("tail", "body", [0, 3.5, 5], [box(-1, 2, 5, 2, 2, 6, tail)])
    g.bone("tail2", "tail", [0, 3, 11], [box(-1, 2.5, 11, 2, 1, 3, tail)])
    quad_legs(g, "body", 1, -2, -4, 3, 2, 1, 1, leg, 2)
    g.bone("s2_fin", "body", [0, 5, 0], [box(-0.5, 5, -3, 1, 2, 6, fin)])
    g.bone("s2_shell", "head", [0, 3, -6], [box(-1, 2, -6.5, 2, 1, 1, {"color": pearl, "glow": True})])
    g.bone("s3_crest", "head", [0, 6, -6], [box(-3, 5, -7, 1, 2, 2, fin), box(2, 5, -7, 1, 2, 2, fin)])
    g.bone("s3_tailfin", "tail2", [0, 3, 13], [box(-2.5, 2.5, 13, 5, 1, 3, fin)])
    g.bone("s3_orb", "root", [0, 9, 0], [box(-1, 8, -1, 2, 2, 2, {"color": teal, "glow": True, "top": 0.6})])
    return g


def stone_tortoise(stage):
    stone, skin, moss = tone(rgb("#8A8272"), stage), tone(rgb("#9AAE6A"), stage), rgb("#6FA05A")
    dark, crystal, shine = rgb("#2A2A22"), rgb("#8CF0C0"), rgb("#E0FFF0")
    runes = {"runes": (crystal, 0.04)} if stage >= 3 else {}
    shell = dict({"color": stone, "belly": rgb("#D8CFA8"), "fleck": (lighten(stone, 0.18), darken(stone, 0.22), 0.16),
                  "band": (1, darken(stone, 0.3))}, **runes)
    cap = {"color": lighten(stone, 0.08), "fleck": (moss, darken(stone, 0.2), 0.3)}
    head = {"color": skin, "front": [(0, 1, dark), (2, 1, dark)], "fleck": (lighten(skin, 0.15), darken(skin, 0.15), 0.08)}
    leg = {"color": darken(skin, 0.1), "band": (1, darken(skin, 0.35))}
    gem = {"color": crystal, "glow": True, "top": 0.5, "fleck": (shine, rgb("#4FC89A"), 0.2)}
    rock = {"color": darken(stone, 0.1), "fleck": (lighten(stone, 0.2), darken(stone, 0.3), 0.2)}
    g = Geo("fam_stone_tortoise")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 3, 0], [box(-3, 2, -4, 6, 4, 8, shell), box(-2, 6, -3, 4, 1, 6, cap)])
    g.bone("head", "body", [0, 3.5, -4], [box(-1.5, 2, -7, 3, 3, 3, head)])
    g.bone("tail", "body", [0, 2.5, 4], [box(-0.5, 2, 4, 1, 1, 2, leg)])
    g.bone("leg_fl", "body", [3, 2, -3], [box(2, 0, -4, 2, 2, 2, leg)])
    g.bone("leg_fr", "body", [-3, 2, -3], [box(-4, 0, -4, 2, 2, 2, leg)])
    g.bone("leg_bl", "body", [3, 2, 3], [box(2, 0, 2, 2, 2, 2, leg)])
    g.bone("leg_br", "body", [-3, 2, 3], [box(-4, 0, 2, 2, 2, 2, leg)])
    g.bone("s2_crystals", "body", [0, 7, 0], [box(-1.5, 7, -2, 1, 2, 1, gem), box(0.5, 7, 0, 1, 3, 1, gem), box(-0.5, 7, 2, 1, 1, 1, gem)])
    g.bone("s3_peak", "body", [0, 7, 0], [box(-1, 7, -1.5, 2, 4, 2, gem), box(-4, 4, -2, 1, 2, 1, rock), box(3, 4, -2, 1, 2, 1, rock),
                                          box(-4, 4, 1, 1, 2, 1, rock), box(3, 4, 1, 1, 2, 1, rock),
                                          box(1, 7, 1.5, 1, 2, 1, {"color": moss, "top": 0.3})])
    return g


def gale_owl(stage):
    feather, wing_c, white = tone(rgb("#CFE2DA"), stage), tone(rgb("#6FA89A"), stage), rgb("#F6FFFB")
    beak, glow, dark = rgb("#F2B84B"), rgb("#A8FFE8"), rgb("#2C4A44")
    runes = {"runes": (glow, 0.05)} if stage >= 3 else {}
    body = dict({"color": feather, "fleck": (white, darken(feather, 0.15), 0.14),
                 "front": [(1, 2, wing_c), (3, 2, wing_c), (2, 3, wing_c), (1, 4, wing_c), (3, 4, wing_c)]}, **runes)
    head = {"color": feather, "fleck": (white, darken(feather, 0.12), 0.08),
            "front": [(0, 1, white), (1, 1, white), (2, 1, white), (0, 3, white), (1, 3, white), (2, 3, white), (0, 2, white), (2, 2, white),
                      (3, 1, white), (4, 1, white), (5, 1, white), (3, 3, white), (4, 3, white), (5, 3, white), (3, 2, white), (5, 2, white)],
            "glow_front": [(1, 2, glow), (4, 2, glow)]}
    wing = {"color": wing_c, "share": "wing", "stripes": darken(wing_c, 0.22), "top": 0.2}
    tipp = {"color": darken(wing_c, 0.12), "share": "wtip", "stripes": glow if stage >= 2 else darken(wing_c, 0.3), "glow": stage >= 3}
    tail = {"color": wing_c, "stripes": darken(wing_c, 0.25)}
    foot = {"color": beak, "band": (1, darken(beak, 0.3))}
    g = Geo("fam_gale_owl")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 5, 0], [box(-2.5, 2, -2, 5, 6, 4, body)])
    g.bone("head", "body", [0, 8, 0], [box(-3, 8, -2.5, 6, 5, 5, head), box(-0.5, 9, -3.5, 1, 1, 1, {"color": beak}),
                                       box(-3, 13, -1, 1, 2, 1, {"color": wing_c}), box(2, 13, -1, 1, 2, 1, {"color": wing_c})])
    g.bone("wing_l", "body", [2.5, 7, 0], [box(2.5, 6, -1.5, 5, 1, 4, wing)])
    g.bone("wing_r", "body", [-2.5, 7, 0], [box(-7.5, 6, -1.5, 5, 1, 4, wing)])
    g.bone("tail", "body", [0, 2.5, 2], [box(-1.5, 2, 2, 3, 1, 3, tail)])
    g.bone("legs", "body", [0, 2, 0], [box(-1.5, 0, -0.5, 1, 2, 1, foot), box(0.5, 0, -0.5, 1, 2, 1, foot)])
    g.bone("s2_tip_l", "wing_l", [7.5, 6.5, 0], [box(7.5, 6, -1, 3, 1, 3, tipp)])
    g.bone("s2_tip_r", "wing_r", [-7.5, 6.5, 0], [box(-10.5, 6, -1, 3, 1, 3, tipp)])
    g.bone("s3_tail", "tail", [0, 2, 5], [box(-2.5, 1.5, 5, 5, 1, 4, {"color": wing_c, "stripes": glow, "glow": True})])
    g.bone("s3_crown", "head", [0, 13, 0], [box(-1, 13, -1.5, 2, 2, 1, {"color": glow, "glow": True, "top": 0.5})])
    return g


def dawn_hare(stage):
    fur, pink, gold = tone(rgb("#FBE9C8"), stage), rgb("#F7B0A8"), rgb("#FFD86B")
    amber, white, shine = rgb("#E8743C"), rgb("#FFFFFF"), rgb("#FFF6C8")
    runes = {"runes": (gold, 0.05)} if stage >= 3 else {}
    body = dict({"color": fur, "belly": white, "belly_rows": 1, "fleck": (white, darken(fur, 0.1), 0.08)}, **runes)
    head = {"color": fur, "fleck": (white, darken(fur, 0.1), 0.05),
            "front": [(0, 1, amber), (3, 1, amber), (1, 2, pink), (2, 2, pink), (1, 3, white), (2, 3, white)]}
    ear = {"color": fur, "share": "ear", "front": [(0, 1, pink), (0, 2, pink), (0, 3, pink)]}
    leg = {"color": darken(fur, 0.08), "band": (1, white)}
    horn = {"color": gold, "glow": True, "top": 0.5, "fleck": (shine, rgb("#E0A030"), 0.2)}
    g = Geo("fam_dawn_hare")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 5, 0], [box(-2, 3, -3, 4, 4, 6, body)])
    g.bone("head", "body", [0, 7, -3], [box(-2, 6, -6, 4, 4, 4, head)])
    g.bone("ear_l", "head", [1.5, 10, -4], [box(1, 10, -4.5, 1, 5, 1, ear)], rotation=[0, 0, -8])
    g.bone("ear_r", "head", [-1.5, 10, -4], [box(-2, 10, -4.5, 1, 5, 1, ear)], rotation=[0, 0, 8])
    g.bone("tail", "body", [0, 6, 3], [box(-1, 5, 3, 2, 2, 2, {"color": white, "top": 0.0, "glow": stage >= 2})])
    quad_legs(g, "body", 1, -2, -2.5, 1, 3, 1, 2, leg, 3)
    g.bone("s2_star", "head", [0, 10, -5], [box(-0.5, 10, -5.5, 1, 1, 1, horn)])
    g.bone("s2_ruff", "body", [0, 6, -3], [box(-2.5, 4, -3.5, 5, 3, 1, {"color": white, "fleck": (shine, fur, 0.2)})])
    g.bone("s3_antlers", "head", [0, 10, -6], [
        box(0.5, 10, -6.5, 1, 2, 1, horn), box(1.5, 11, -6.5, 1, 1, 1, horn), box(2.5, 11, -6.5, 1, 3, 1, horn), box(0.5, 12, -6.5, 1, 2, 1, horn),
        box(-1.5, 10, -6.5, 1, 2, 1, horn), box(-2.5, 11, -6.5, 1, 1, 1, horn), box(-3.5, 11, -6.5, 1, 3, 1, horn), box(-1.5, 12, -6.5, 1, 2, 1, horn)])
    return g


def shade_cat(stage):
    fur, light, inner = tone(rgb("#2C2644"), stage), rgb("#463C70"), rgb("#7A56C0")
    eye, wisp = rgb("#D0B0FF"), rgb("#9A7CFF")
    runes = {"runes": (wisp, 0.05)} if stage >= 3 else {}
    body = dict({"color": fur, "fleck": (light, darken(fur, 0.2), 0.08)}, **runes)
    head = {"color": fur, "fleck": (light, darken(fur, 0.2), 0.05),
            "glow_front": [(1, 1, eye), (3, 1, eye)], "front": [(2, 2, inner), (1, 3, light), (3, 3, light)]}
    ear = {"color": darken(fur, 0.1), "share": "ear", "front": [(0, 1, inner)]}
    leg = {"color": darken(fur, 0.12), "band": (1, light)}
    tail = {"color": fur, "share": "tail", "fleck": (light, darken(fur, 0.2), 0.1)}
    tail2 = {"color": fur, "share": "tail2", "band": (1, wisp) if stage >= 2 else (1, light)}
    smoke = {"color": wisp, "glow": True, "top": 0.4, "fleck": (eye, rgb("#5A3CB0"), 0.3)}
    g = Geo("fam_shade_cat")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 6, 0], [box(-2, 4, -4, 4, 3, 8, body)])
    g.bone("head", "body", [0, 7, -4], [box(-2.5, 6, -8, 5, 4, 4, head), box(-2.5, 10, -6.5, 1, 2, 2, ear), box(1.5, 10, -6.5, 1, 2, 2, ear)])
    g.bone("tail", "body", [0, 6.5, 4], [box(-0.5, 6, 4, 1, 1, 5, tail)], rotation=[25, -10, 0])
    g.bone("tail2", "tail", [0, 6.5, 9], [box(-0.5, 6, 9, 1, 1, 4, tail2)], rotation=[20, 0, 0])
    quad_legs(g, "body", 1, -2, -3.5, 2.5, 4, 1, 1, leg, 4)
    g.bone("s2_collar", "head", [0, 6, -5], [box(-3, 5.5, -5, 6, 1, 1, {"color": inner, "top": 0.3}), box(-0.5, 4.5, -5.5, 1, 1, 1, smoke)])
    g.bone("s2_wisps", "body", [0, 7, -3], [box(-3, 6, -3, 1, 2, 2, smoke), box(2, 6, -3, 1, 2, 2, smoke)])
    g.bone("s3_tailb", "body", [0, 6.5, 4], [box(-0.5, 6, 4, 1, 1, 5, tail)], rotation=[25, 22, 0])
    g.bone("s3_tailb2", "s3_tailb", [0, 6.5, 9], [box(-0.5, 6, 9, 1, 1, 4, tail2), box(-1, 5.5, 13, 2, 2, 2, smoke)], rotation=[20, 0, 0])
    g.bone("s3_tip", "tail2", [0, 6.5, 13], [box(-1, 5.5, 13, 2, 2, 2, smoke)])
    g.bone("s3_ears", "head", [0, 12, -6], [box(-2.5, 12, -6, 1, 1, 1, smoke), box(1.5, 12, -6, 1, 1, 1, smoke)])
    return g


def rune_wisp(stage):
    core, shell, gold = tone(rgb("#E4D4FF"), stage), tone(rgb("#B98CFF"), stage), rgb("#FFD86B")
    dark, white = rgb("#3A2460"), rgb("#FFFFFF")
    corec = {"color": core, "glow": True, "top": 0.4, "fleck": (white, shell, 0.2), "front": [(0, 1, dark), (3, 1, dark)]}
    fl = {"color": shell, "glow": True, "fleck": (core, rgb("#7A4CD8"), 0.25)}
    mote = {"color": gold, "glow": True, "share": "mote", "top": 0.5}
    plate = {"color": shell, "glow": True, "share": "plate", "fleck": (white, rgb("#7A4CD8"), 0.3)}
    plate2 = {"color": shell, "glow": True, "share": "plate2", "fleck": (white, rgb("#7A4CD8"), 0.3)}
    spike = {"color": white, "glow": True, "share": "spike", "top": 0.0}
    g = Geo("fam_rune_wisp")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 6, 0], [box(-2, 4, -2, 4, 4, 4, corec)])
    g.bone("flame", "body", [0, 4, 0], [box(-1, 2, -1, 2, 2, 2, fl), box(-0.5, 0, -0.5, 1, 2, 1, fl)])
    g.bone("ring", "root", [0, 6, 0], [box(-5, 5.5, -0.5, 1, 1, 1, mote), box(4, 5.5, -0.5, 1, 1, 1, mote),
                                       box(-0.5, 5.5, -5, 1, 1, 1, mote), box(-0.5, 5.5, 4, 1, 1, 1, mote)])
    g.bone("s2_ring", "root", [0, 6, 0], [box(-7, 5, -1, 1, 2, 2, plate), box(6, 5, -1, 1, 2, 2, plate),
                                          box(-1, 5, -7, 2, 2, 1, plate2), box(-1, 5, 6, 2, 2, 1, plate2)], rotation=[0, 0, 30])
    g.bone("s3_crown", "body", [0, 8, 0], [box(-0.5, 8, -0.5, 1, 3, 1, spike), box(-2.5, 8, -2.5, 1, 1, 1, mote), box(1.5, 8, -2.5, 1, 1, 1, mote),
                                           box(-2.5, 8, 1.5, 1, 1, 1, mote), box(1.5, 8, 1.5, 1, 1, 1, mote)])
    g.bone("s3_ring2", "root", [0, 6, 0], [box(-9, 5.5, -0.5, 1, 1, 1, mote), box(8, 5.5, -0.5, 1, 1, 1, mote),
                                           box(-0.5, 5.5, -9, 1, 1, 1, mote), box(-0.5, 5.5, 8, 1, 1, 1, mote)], rotation=[0, 0, -30])
    return g


def verdant_sprite(stage):
    green, face, leaf = tone(rgb("#8FD86A"), stage), tone(rgb("#F2F6CC"), stage), rgb("#4FA83C")
    dark, pink, gold = rgb("#2A4A22"), rgb("#FF9AC8"), rgb("#FFE27A")
    runes = {"runes": (gold, 0.06)} if stage >= 3 else {}
    body = dict({"color": green, "belly": face, "belly_rows": 1, "fleck": (lighten(green, 0.2), darken(green, 0.15), 0.08)}, **runes)
    head = {"color": face, "top_marks": [(x, z, green) for x in range(5) for z in range(5) if (x + z) % 2 == 0],
            "front": [(1, 2, dark), (3, 2, dark), (0, 3, pink), (4, 3, pink)], "band": (1, lighten(green, 0.3))}
    limb = {"color": green, "share": "limb", "band": (1, darken(green, 0.25))}
    blade = {"color": leaf, "share": "blade", "stripes": lighten(leaf, 0.25), "top": 0.2}
    wingp = {"color": lighten(leaf, 0.15), "share": "lwing", "front": [(1, 0, leaf), (1, 1, leaf), (1, 2, leaf), (1, 3, leaf)]}
    petal = {"color": pink, "share": "petal", "glow": True, "top": 0.35}
    g = Geo("fam_verdant_sprite")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 4, 0], [box(-1.5, 2, -1.5, 3, 4, 3, body)])
    g.bone("head", "body", [0, 6, 0], [box(-2.5, 6, -2.5, 5, 5, 5, head)])
    g.bone("leaf", "head", [0, 11, 0], [box(-0.5, 11, -0.5, 1, 2, 1, {"color": dark, "top": 0.3}),
                                        box(0.5, 12, -1, 3, 1, 2, blade), box(-3.5, 13, -1, 3, 1, 2, blade)])
    g.bone("arm_l", "body", [1.5, 5.5, 0], [box(1.5, 3, -0.5, 1, 3, 1, limb)])
    g.bone("arm_r", "body", [-1.5, 5.5, 0], [box(-2.5, 3, -0.5, 1, 3, 1, limb)])
    g.bone("legs", "body", [0, 2, 0], [box(-1.5, 0, -0.5, 1, 2, 1, limb), box(0.5, 0, -0.5, 1, 2, 1, limb)])
    g.bone("s2_bud", "head", [0, 13, 0], [box(-1, 13, -1, 2, 2, 2, {"color": pink, "top": 0.3, "fleck": (lighten(pink, 0.3), rgb("#E0609A"), 0.2)})])
    g.bone("s2_wing_l", "body", [1, 5, 1.5], [box(1, 3, 1.5, 3, 4, 1, wingp)])
    g.bone("s2_wing_r", "body", [-1, 5, 1.5], [box(-4, 3, 1.5, 3, 4, 1, wingp)])
    g.bone("s3_petals", "head", [0, 14, 0], [box(-3, 14, -1, 2, 1, 2, petal), box(1, 14, -1, 2, 1, 2, petal),
                                             box(-1, 14, -3, 2, 1, 2, petal), box(-1, 14, 1, 2, 1, 2, petal),
                                             box(-0.5, 15, -0.5, 1, 1, 1, {"color": gold, "glow": True})])
    g.bone("s3_collar", "body", [0, 5.5, 0], [box(-2, 5, -2, 4, 1, 4, {"color": leaf, "stripes": gold, "top": 0.2})])
    return g


def bone_raven(stage):
    black, bone, ghost = tone(rgb("#262430"), stage), tone(rgb("#EDE6D2"), stage), rgb("#7CF0D0")
    dark, sheen = rgb("#0E0E14"), rgb("#3A3A66")
    runes = {"runes": (ghost, 0.05)} if stage >= 3 else {}
    body = dict({"color": black, "fleck": (sheen, dark, 0.14)}, **runes)
    skull = {"color": bone, "fleck": (lighten(bone, 0.3), darken(bone, 0.15), 0.08),
             "front": [(0, 2, dark), (2, 2, dark), (1, 2, darken(bone, 0.3))], "glow_front": [(0, 1, ghost), (2, 1, ghost)]}
    beak = {"color": darken(bone, 0.12), "band": (1, darken(bone, 0.35))}
    wing = {"color": black, "share": "wing", "stripes": sheen, "top": 0.1}
    spectral = {"color": ghost, "glow": True, "share": "spec", "stripes": rgb("#D8FFF4")}
    plume = {"color": ghost, "glow": True, "stripes": rgb("#D8FFF4")}
    rib = {"color": bone, "share": "rib", "top": 0.3}
    g = Geo("fam_bone_raven")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 5, 0], [box(-2, 3, -3, 4, 4, 7, body)])
    g.bone("head", "body", [0, 7, -3], [box(-1.5, 6, -6, 3, 3, 3, skull), box(-0.5, 6, -8, 1, 1, 2, beak)])
    g.bone("wing_l", "body", [2, 6.5, 0], [box(2, 6, -2.5, 6, 1, 5, wing)])
    g.bone("wing_r", "body", [-2, 6.5, 0], [box(-8, 6, -2.5, 6, 1, 5, wing)])
    g.bone("tail", "body", [0, 5.5, 4], [box(-1.5, 5, 4, 3, 1, 4, {"color": black, "stripes": sheen})])
    g.bone("legs", "body", [0, 3, 0], [box(-1.5, 1, -0.5, 1, 2, 1, {"color": bone, "share": "leg"}), box(0.5, 1, -0.5, 1, 2, 1, {"color": bone, "share": "leg"})])
    g.bone("s2_crest", "head", [0, 9, -5], [box(-0.5, 9, -5.5, 1, 1, 2, {"color": bone, "top": 0.3})])
    g.bone("s2_ribs", "body", [0, 4.5, 0], [box(-2.5, 4, -2, 5, 1, 1, rib), box(-2.5, 4, 0, 5, 1, 1, rib), box(-2.5, 4, 2, 5, 1, 1, rib)])
    g.bone("s3_plume", "tail", [0, 5.5, 8], [box(-2.5, 5, 8, 5, 1, 4, plume)])
    g.bone("s3_wtip_l", "wing_l", [8, 6.5, 0], [box(8, 6, -2, 3, 1, 4, spectral)])
    g.bone("s3_wtip_r", "wing_r", [-8, 6.5, 0], [box(-11, 6, -2, 3, 1, 4, spectral)])
    g.bone("s3_horns", "head", [0, 9, -4], [box(-1.5, 9, -4.5, 1, 2, 1, {"color": bone, "share": "horn"}), box(0.5, 9, -4.5, 1, 2, 1, {"color": bone, "share": "horn"})])
    return g


def fortune_drake(stage):
    jade, gold, light = tone(rgb("#3FA878"), stage), rgb("#F2C84B"), rgb("#FFF0A8")
    dark, eye = rgb("#1E4A36"), rgb("#FFE27A")
    runes = {"runes": (light, 0.05)} if stage >= 3 else {}
    body = dict({"color": jade, "belly": gold, "belly_rows": 1, "fleck": (lighten(jade, 0.2), darken(jade, 0.2), 0.12)}, **runes)
    head = {"color": jade, "fleck": (lighten(jade, 0.2), darken(jade, 0.2), 0.08), "glow_front": [(0, 1, eye), (3, 1, eye)]}
    snout = {"color": lighten(jade, 0.1), "front": [(0, 0, dark), (2, 0, dark)], "band": (1, gold)}
    horn = {"color": gold, "share": "horn", "top": 0.4}
    wing = {"color": gold, "share": "wing", "stripes": jade, "top": 0.2}
    wing2 = {"color": gold, "share": "wing2", "stripes": light, "glow": True}
    tail = {"color": jade, "belly": gold, "fleck": (lighten(jade, 0.2), darken(jade, 0.2), 0.1)}
    leg = {"color": darken(jade, 0.15), "share": "leg", "band": (1, gold)}
    gem = {"color": rgb("#FF6A8A"), "glow": True, "top": 0.5, "fleck": (rgb("#FFD0DC"), rgb("#C8305A"), 0.2)}
    coin = {"color": gold, "glow": True, "top": 0.5, "front": [(0, 0, light), (1, 1, rgb("#C8922A"))]}
    spike = {"color": gold, "share": "spike", "top": 0.5}
    g = Geo("fam_fortune_drake")
    g.bone("root", None, [0, 0, 0])
    g.bone("body", "root", [0, 5, 0], [box(-2, 3, -4, 4, 4, 8, body)])
    g.bone("head", "body", [0, 7, -4], [box(-2, 6, -8, 4, 4, 4, head), box(-1.5, 6, -10, 3, 2, 2, snout),
                                        box(-2, 10, -6, 1, 2, 1, horn), box(1, 10, -6, 1, 2, 1, horn)])
    g.bone("wing_l", "body", [2, 6.5, -1], [box(2, 6, -3, 6, 1, 5, wing)])
    g.bone("wing_r", "body", [-2, 6.5, -1], [box(-8, 6, -3, 6, 1, 5, wing)])
    g.bone("tail", "body", [0, 5, 4], [box(-1, 4, 4, 2, 2, 4, tail)])
    g.bone("tail2", "tail", [0, 5, 8], [box(-0.5, 4.5, 8, 1, 1, 4, {"color": jade})])
    g.bone("legs", "body", [0, 3, 0], [box(-2, 1, -3, 1, 2, 1, leg), box(1, 1, -3, 1, 2, 1, leg), box(-2, 1, 2, 1, 2, 1, leg), box(1, 1, 2, 1, 2, 1, leg)])
    g.bone("s2_spikes", "body", [0, 7, 0], [box(-0.5, 7, -3, 1, 1, 1, spike), box(-0.5, 7, -1, 1, 1, 1, spike), box(-0.5, 7, 1, 1, 1, 1, spike)])
    g.bone("s2_coin", "head", [0, 6, -6], [box(-1, 4, -6.5, 2, 2, 1, coin)])
    g.bone("s3_horns", "head", [0, 12, -5], [box(-2, 12, -5, 1, 2, 1, horn), box(1, 12, -5, 1, 2, 1, horn), box(-0.5, 10, -7.5, 1, 1, 1, gem)])
    g.bone("s3_wing2_l", "wing_l", [8, 6.5, -1], [box(8, 6, -2.5, 3, 1, 4, wing2)])
    g.bone("s3_wing2_r", "wing_r", [-8, 6.5, -1], [box(-11, 6, -2.5, 3, 1, 4, wing2)])
    g.bone("s3_gem", "tail2", [0, 5, 12], [box(-1, 4, 12, 2, 2, 2, gem)])
    return g


# =====================================================================================================
#  Animacoes (nomes curtos, iguais em todas as formas)
# =====================================================================================================
def wave(length, amp, phase=0.0, steps=8, axis=0, base=(0, 0, 0), cycles=1):
    """Keyframes de uma senoide (graus ou pixels) num eixo, em loop."""
    kf = {}
    for i in range(steps * cycles + 1):
        t = length * i / (steps * cycles)
        vec = list(base)
        vec[axis] = round(base[axis] + amp * math.sin(2 * PI * i / steps + phase), 3)
        kf[f"{round(t, 4)}"] = vec
    return kf


def keys(*pairs):
    return {f"{round(t, 4)}": list(v) for (t, v) in pairs}


def anim_file(names, spec):
    """So entram os ossos que o modelo tem (cada forma tem um esqueleto um pouco diferente)."""
    out = {}
    for name, (length, loop, bones) in spec.items():
        kept = {b: ch for b, ch in bones.items() if b in names}
        a = {"animation_length": length, "bones": kept}
        if loop:
            a["loop"] = True
        out[name] = a
    return {"format_version": "1.8.0", "animations": out}


def walker_anims(names, extra_tails=(), slow=False):
    step = 1.0 if slow else 0.6
    swing = 18 if slow else 32
    sway = {t: {"rotation": wave(3.0, 6, phase=i * 0.9, axis=1)} for i, t in enumerate(extra_tails)}
    idle = {"body": {"scale": wave(3.0, 0.02, axis=1, base=(1, 1, 1))},
            "tail": {"rotation": wave(3.0, 14, axis=1)},
            "tail2": {"rotation": wave(3.0, 10, phase=-0.7, axis=1)},
            "head": {"rotation": wave(3.0, 4, phase=0.5, axis=2)},
            "ear_l": {"rotation": wave(3.0, 5, axis=2)}, "ear_r": {"rotation": wave(3.0, -5, axis=2)},
            "s3_orb": {"position": wave(3.0, 0.8, axis=1)}}
    idle.update(sway)
    move = {"leg_fl": {"rotation": wave(step, swing)}, "leg_br": {"rotation": wave(step, swing)},
            "leg_fr": {"rotation": wave(step, swing, phase=PI)}, "leg_bl": {"rotation": wave(step, swing, phase=PI)},
            "tail": {"rotation": wave(step, 9, axis=1)}, "tail2": {"rotation": wave(step, 7, phase=-0.7, axis=1)},
            "head": {"rotation": wave(step, 3)},
            "ear_l": {"rotation": wave(step, 8)}, "ear_r": {"rotation": wave(step, 8)},
            "s3_orb": {"position": wave(step, 0.5, axis=1)}}
    move.update({t: {"rotation": wave(step, 5, phase=i * 0.9, axis=1)} for i, t in enumerate(extra_tails)})
    if slow:
        sit = {"root": {"position": keys((0, [0, -1.6, 0]))},
               "head": {"rotation": wave(4.0, 3, axis=1), "position": keys((0, [0, 0, 1.5]))},
               "leg_fl": {"rotation": keys((0, [0, 0, -50]))}, "leg_fr": {"rotation": keys((0, [0, 0, 50]))},
               "leg_bl": {"rotation": keys((0, [0, 0, -50]))}, "leg_br": {"rotation": keys((0, [0, 0, 50]))}}
        sit_len = 4.0
    else:
        sit = {"root": {"position": keys((0, [0, -1.2, 0]))},
               "body": {"rotation": keys((0, [-22, 0, 0]))},
               "head": {"rotation": wave(3.0, 6, axis=1, base=(22, 0, 0))},
               "leg_fl": {"rotation": keys((0, [22, 0, 0]))}, "leg_fr": {"rotation": keys((0, [22, 0, 0]))},
               "leg_bl": {"rotation": keys((0, [-58, 0, 0]))}, "leg_br": {"rotation": keys((0, [-58, 0, 0]))},
               "tail": {"rotation": wave(3.0, 14, axis=1, base=(-10, 0, 0))},
               "tail2": {"rotation": wave(3.0, 10, phase=-0.7, axis=1)},
               "ear_l": {"rotation": wave(3.0, 4, axis=2)}, "ear_r": {"rotation": wave(3.0, -4, axis=2)},
               "s3_orb": {"position": wave(3.0, 0.8, axis=1)}}
        sit.update(sway)
        sit_len = 3.0
    alert = {"root": {"position": keys((0, [0, 0, 0]), (0.12, [0, 1.2, 0]), (0.3, [0, 0, 0]))},
             "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.35, [-16, 18, 0]), (0.55, [-16, -18, 0]), (0.8, [0, 0, 0]))},
             "tail": {"rotation": keys((0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.65, [35, 0, 0]), (0.8, [0, 0, 0]))},
             "ear_l": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 0, 6]), (0.65, [-12, 0, 6]), (0.8, [0, 0, 0]))},
             "ear_r": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 0, -6]), (0.65, [-12, 0, -6]), (0.8, [0, 0, 0]))}}
    happy = {"root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 2, 0]), (0.3, [0, 0, 0]), (0.5, [0, 2, 0]), (0.7, [0, 0, 0]))},
             "tail": {"rotation": wave(1.0, 28, axis=1, steps=4, cycles=4)},
             "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-8, 0, 10]), (0.5, [-8, 0, -10]), (0.75, [-8, 0, 10]), (1.0, [0, 0, 0]))}}
    find = {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [7, 0, 0]), (1.0, [7, 0, 0]), (1.2, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [18, 0, 0]), (0.4, [10, 0, 0]), (0.6, [18, 0, 0]), (0.8, [10, 0, 0]), (1.0, [18, 0, 0]), (1.2, [0, 0, 0]))},
            "tail": {"rotation": keys((0, [0, 0, 0]), (0.2, [30, 0, 0]), (1.0, [30, 0, 0]), (1.2, [0, 0, 0]))}}
    return anim_file(names, {"idle": (3.0, True, idle), "move": (step, True, move), "sit": (sit_len, True, sit),
                             "alert": (0.8, False, alert), "happy": (1.0, False, happy), "find": (1.2, False, find)})


def flyer_anims(names):
    idle = {"root": {"position": wave(1.6, 0.8, axis=1, cycles=1)},
            "wing_l": {"rotation": wave(0.8, 26, axis=2, base=(0, 0, 6), cycles=2)},
            "wing_r": {"rotation": wave(0.8, -26, axis=2, base=(0, 0, -6), cycles=2)},
            "head": {"rotation": wave(1.6, 7, phase=0.6, axis=1)},
            "tail": {"rotation": wave(1.6, 6)}, "tail2": {"rotation": wave(1.6, 8, phase=-0.7, axis=1)},
            "legs": {"rotation": keys((0, [20, 0, 0]))}}
    move = {"root": {"position": wave(0.5, 0.5, phase=PI / 2, axis=1)},
            "body": {"rotation": keys((0, [14, 0, 0]))},
            "head": {"rotation": keys((0, [-10, 0, 0]))},
            "wing_l": {"rotation": wave(0.5, 40, axis=2)}, "wing_r": {"rotation": wave(0.5, -40, axis=2)},
            "tail": {"rotation": wave(0.5, 5, phase=PI / 2)}, "tail2": {"rotation": wave(0.5, 6, axis=1)},
            "legs": {"rotation": keys((0, [40, 0, 0]))}}
    alert = {"root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 1.5, 0]), (0.65, [0, 1.5, 0]), (0.8, [0, 0, 0]))},
             "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 35, 0]), (0.4, [-12, -35, 0]), (0.65, [-12, 20, 0]), (0.8, [0, 0, 0]))},
             "wing_l": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 0, -30]), (0.65, [0, 0, -30]), (0.8, [0, 0, 0]))},
             "wing_r": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 0, 30]), (0.65, [0, 0, 30]), (0.8, [0, 0, 0]))}}
    happy = {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 360, 0])),
                      "position": keys((0, [0, 0, 0]), (0.5, [0, 2, 0]), (1.0, [0, 0, 0]))}}
    find = {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [24, 0, 0]), (1.0, [24, 0, 0]), (1.2, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [10, 0, 0]), (0.4, [22, 0, 0]), (0.6, [10, 0, 0]), (0.8, [22, 0, 0]), (1.2, [0, 0, 0]))},
            "wing_l": {"rotation": wave(1.2, 40, axis=2, steps=4, cycles=3)}, "wing_r": {"rotation": wave(1.2, -40, axis=2, steps=4, cycles=3)}}
    return anim_file(names, {"idle": (1.6, True, idle), "move": (0.5, True, move), "alert": (0.8, False, alert),
                             "happy": (1.0, False, happy), "find": (1.2, False, find)})


def wisp_anims(names):
    def spin(length, turns=1, sign=1):
        return keys((0, [0, 0, 0]), (length, [0, 360 * turns * sign, 0]))

    idle = {"root": {"position": wave(4.0, 1.0, axis=1, cycles=2)},
            "body": {"scale": wave(4.0, 0.06, axis=1, base=(1, 1, 1), cycles=2)},
            "flame": {"rotation": wave(4.0, 12, axis=0, cycles=2), "scale": wave(4.0, 0.15, axis=1, base=(1, 1, 1), cycles=4)},
            "ring": {"rotation": spin(4.0)}, "s2_ring": {"rotation": spin(4.0, sign=-1)}, "s3_ring2": {"rotation": spin(4.0, turns=2)}}
    move = {"root": {"position": wave(2.0, 0.6, axis=1, cycles=2)},
            "body": {"rotation": keys((0, [14, 0, 0]))},
            "flame": {"rotation": keys((0, [-35, 0, 0])), "scale": wave(2.0, 0.2, axis=1, base=(1, 1.2, 1), cycles=4)},
            "ring": {"rotation": spin(2.0, turns=2)}, "s2_ring": {"rotation": spin(2.0, turns=2, sign=-1)}, "s3_ring2": {"rotation": spin(2.0, turns=3)}}
    alert = {"body": {"scale": keys((0, [1, 1, 1]), (0.15, [1.35, 1.35, 1.35]), (0.3, [1, 1, 1]), (0.45, [1.35, 1.35, 1.35]), (0.8, [1, 1, 1]))},
             "ring": {"rotation": spin(0.8, turns=2)}}
    happy = {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 720, 0])),
                      "position": keys((0, [0, 0, 0]), (0.5, [0, 2.5, 0]), (1.0, [0, 0, 0]))}}
    find = {"root": {"position": keys((0, [0, 0, 0]), (0.3, [0, 0, -3]), (0.6, [0, 0, 0]), (0.9, [0, 0, -3]), (1.2, [0, 0, 0]))},
            "body": {"scale": keys((0, [1, 1, 1]), (0.3, [0.85, 0.85, 1.3]), (0.6, [1, 1, 1]), (0.9, [0.85, 0.85, 1.3]), (1.2, [1, 1, 1]))},
            "ring": {"rotation": spin(1.2, turns=2)}}
    return anim_file(names, {"idle": (4.0, True, idle), "move": (2.0, True, move), "alert": (0.8, False, alert),
                             "happy": (1.0, False, happy), "find": (1.2, False, find)})


def sprite_anims(names):
    idle = {"root": {"position": wave(2.0, 0.8, axis=1)},
            "arm_l": {"rotation": wave(2.0, 10)}, "arm_r": {"rotation": wave(2.0, 10, phase=PI)},
            "leaf": {"rotation": wave(2.0, 8, phase=0.5, axis=2)},
            "legs": {"rotation": wave(2.0, 8)},
            "head": {"rotation": wave(2.0, 4, phase=1.0, axis=2)},
            "s2_wing_l": {"rotation": wave(0.5, 22, axis=1, base=(0, -15, 0), steps=4, cycles=4)},
            "s2_wing_r": {"rotation": wave(0.5, -22, axis=1, base=(0, 15, 0), steps=4, cycles=4)}}
    move = {"root": {"position": wave(0.8, 0.5, axis=1)},
            "body": {"rotation": keys((0, [18, 0, 0]))}, "head": {"rotation": keys((0, [-14, 0, 0]))},
            "arm_l": {"rotation": keys((0, [35, 0, -10]))}, "arm_r": {"rotation": keys((0, [35, 0, 10]))},
            "legs": {"rotation": wave(0.8, 6, base=(30, 0, 0))},
            "leaf": {"rotation": wave(0.8, 6, axis=0, base=(20, 0, 0))},
            "s2_wing_l": {"rotation": wave(0.2, 28, axis=1, base=(0, -15, 0), steps=4, cycles=4)},
            "s2_wing_r": {"rotation": wave(0.2, -28, axis=1, base=(0, 15, 0), steps=4, cycles=4)}}
    alert = {"root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 1.5, 0]), (0.65, [0, 1.5, 0]), (0.8, [0, 0, 0]))},
             "arm_l": {"rotation": keys((0, [0, 0, 0]), (0.15, [-150, 0, 0]), (0.3, [-120, 0, 0]), (0.45, [-150, 0, 0]), (0.65, [-125, 0, 0]), (0.8, [0, 0, 0]))},
             "arm_r": {"rotation": keys((0, [0, 0, 0]), (0.15, [-150, 0, 0]), (0.3, [-120, 0, 0]), (0.45, [-150, 0, 0]), (0.65, [-125, 0, 0]), (0.8, [0, 0, 0]))},
             "leaf": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 0, 20]), (0.4, [0, 0, -20]), (0.8, [0, 0, 0]))}}
    happy = {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 360, 0])),
                      "position": keys((0, [0, 0, 0]), (0.5, [0, 2, 0]), (1.0, [0, 0, 0]))},
             "arm_l": {"rotation": keys((0, [0, 0, 0]), (0.2, [-160, 0, 0]), (0.8, [-160, 0, 0]), (1.0, [0, 0, 0]))},
             "arm_r": {"rotation": keys((0, [0, 0, 0]), (0.2, [-160, 0, 0]), (0.8, [-160, 0, 0]), (1.0, [0, 0, 0]))}}
    find = {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [14, 0, 0]), (1.0, [14, 0, 0]), (1.2, [0, 0, 0]))},
            "arm_l": {"rotation": keys((0, [0, 0, 0]), (0.2, [-95, 0, 0]), (0.5, [-80, 0, 0]), (0.8, [-95, 0, 0]), (1.0, [-95, 0, 0]), (1.2, [0, 0, 0]))},
            "leaf": {"rotation": keys((0, [0, 0, 0]), (0.2, [25, 0, 0]), (1.0, [25, 0, 0]), (1.2, [0, 0, 0]))}}
    return anim_file(names, {"idle": (2.0, True, idle), "move": (0.8, True, move), "alert": (0.8, False, alert),
                             "happy": (1.0, False, happy), "find": (1.2, False, find)})


FOX_TAILS = ["s2_tail_l", "s2_tail_r"] + ["s3_tail_%s%d" % (s, i) for i in range(3) for s in "lr"]
FORMS = [
    ("ember_fox", ember_fox, lambda n: walker_anims(n, FOX_TAILS)),
    ("tide_otter", tide_otter, lambda n: walker_anims(n)),
    ("stone_tortoise", stone_tortoise, lambda n: walker_anims(n, slow=True)),
    ("gale_owl", gale_owl, flyer_anims),
    ("dawn_hare", dawn_hare, lambda n: walker_anims(n)),
    ("shade_cat", shade_cat, lambda n: walker_anims(n, ["s3_tailb"])),
    ("rune_wisp", rune_wisp, wisp_anims),
    ("verdant_sprite", verdant_sprite, sprite_anims),
    ("bone_raven", bone_raven, flyer_anims),
    ("fortune_drake", fortune_drake, flyer_anims),
]


def gen_models():
    for idx, (fid, build, anims) in enumerate(FORMS):
        geo_json = None
        for stage in (1, 2, 3):
            g = build(stage)
            geo, canvas, glow = g.build(17 + idx)
            if geo_json is None:
                geo_json = geo
                write_json(os.path.join(ASSETS, "geo", "entity", "fam_%s.geo.json" % fid), geo)
                write_json(os.path.join(ASSETS, "animations", "entity", "fam_%s.animation.json" % fid), anims(g.names()))
            elif geo != geo_json:
                raise RuntimeError("a geometria de %s mudou entre estagios (o UV precisa ser o mesmo)" % fid)
            canvas.save(os.path.join(ASSETS, "textures", "entity", "fam_%s_%d.png" % (fid, stage)))
            glow.save(os.path.join(ASSETS, "textures", "entity", "fam_%s_%d_glowmask.png" % (fid, stage)))


# =====================================================================================================
#  Item, icone da aba e receita
# =====================================================================================================
def gen_item():
    pal = {"s": rgb("#8A5A34"), "S": rgb("#B07A48"), "d": rgb("#5A3820"), "r": rgb("#C8405A"), "R": rgb("#F06A80"),
           "e": rgb("#FFB13B"), "E": rgb("#FFF0A0"), "m": rgb("#D8D0E8", 200), "M": rgb("#B98CFF", 170), "g": rgb("#FFD86B")}
    rows = [
        "..........M.....",
        ".........m.M....",
        "........m..m....",
        ".........mM.....",
        "..........m.m...",
        ".........Em.....",
        "........eEe.....",
        ".......SeS.eE...",
        "......SsSs.Se...",
        ".....SsSsdSs....",
        "....rRrRrRs.....",
        "...rRgRrRd......",
        "...SrRrRd.......",
        "..SsSsSd........",
        ".SsdSsd.........",
        ".dd.dd..........",
    ]
    assert all(len(r) == 16 for r in rows) and len(rows) == 16
    c = Canvas(16, 16)
    c.from_rows(rows, pal)
    c.save(os.path.join(ASSETS, "textures", "item", "bond_incense.png"))
    item_model(ASSETS, "bond_incense")

    tab = {"o": rgb("#2A2140"), "p": rgb("#E8C8FF"), "P": rgb("#B98CFF"), "g": rgb("#FFD86B")}
    rows = [
        "................",
        "....oo....oo....",
        "...oppo..oppo...",
        "...oPpo..oPpo...",
        ".oo.oo....oo.oo.",
        "oppo........oppo",
        "oPpo..oooo..oPpo",
        ".oo..oppppo..oo.",
        "....oppppppo....",
        "...oppPppPppo...",
        "...opPPppPPpo...",
        "...oPPPPPPPPo...",
        "....oPPooPPo....",
        ".....oo..oo.....",
        "................",
        "................",
    ]
    assert all(len(r) == 16 for r in rows) and len(rows) == 16
    c = Canvas(16, 16)
    c.from_rows(rows, tab)
    c.save(os.path.join(ASSETS, "textures", "gui", "tab", "familiar.png"))

    # Receita sem forma: materiais comuns + um pouco de magia (o difícil é o nível, não o item)
    write_json(os.path.join(DATA, "recipe", "bond_incense.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "isekai_mahou:essence_arcane"}, {"item": "isekai_mahou:mana_crystal"},
                        {"item": "minecraft:feather"}, {"item": "minecraft:bone"}, {"item": "minecraft:honeycomb"}],
        "result": {"id": "isekai_mahou:bond_incense", "count": 1}})


# =====================================================================================================
#  Textos
# =====================================================================================================
T = {}


def t(key, pt, en):
    T[key] = (pt, en)


def gen_lang():
    t("item.isekai_mahou.bond_incense", "Incenso do Vínculo", "Bond Incense")
    t("item.isekai_mahou.bond_incense.desc", "Segure para queimar perto de uma fogueira acesa: desperta o seu Familiar.",
      "Hold to burn near a lit campfire: awakens your Familiar.")
    t("item.isekai_mahou.bond_incense.desc2", "Requer classe escolhida e nível %s. Com um Familiar, renova o vínculo e a forma dele.",
      "Requires a chosen class and level %s. With a Familiar, renews the bond and its form.")
    t("entity.isekai_mahou.familiar_spirit", "Familiar", "Familiar")
    t("key.isekai_mahou.familiar_call", "Chamar/guardar o Familiar", "Call/dismiss Familiar")
    t("commands.isekai_mahou.familiars.reset", "Familiar apagado (os itens da bolsa voltaram para você).",
      "Familiar reset (stash items were returned to you).")
    t("commands.isekai_mahou.familiars.fail", "Não deu: sem familiar desperto, já desperto ou forma desconhecida.",
      "Failed: no awakened familiar, already awakened, or unknown form.")

    # ---- formas: nome por estágio + descrição
    forms = {
        "ember_fox": (("Filhote de Brasa", "Ember Kit"), ("Raposa de Brasa", "Ember Fox"), ("Kitsune das Nove Chamas", "Nine-Flame Kitsune"),
                      "Nasce de quem resolve tudo no calor da hora: fogo, pólvora, forja e estrada.",
                      "Born from those who settle things in the heat of the moment: fire, powder, forge and road."),
        "tide_otter": (("Lontrinha da Maré", "Tide Pup"), ("Lontra das Marés", "Tide Otter"), ("Lontra Rainha das Correntes", "Current Queen Otter"),
                       "Nasce de quem vive perto da água: pesca, nada, mistura poções e leva a vida com calma.",
                       "Born from those who live by the water: fishing, swimming, brewing and taking it easy."),
        "stone_tortoise": (("Casquinho de Pedra", "Pebble Shell"), ("Tartaruga de Pedra", "Stone Tortoise"), ("Tartaruga-Montanha", "Mountain Tortoise"),
                           "Nasce de quem cava fundo e aguenta pancada: mineiros, ferreiros e muralhas ambulantes.",
                           "Born from those who dig deep and take a beating: miners, smiths and walking walls."),
        "gale_owl": (("Corujinha da Brisa", "Breeze Owlet"), ("Coruja do Vendaval", "Gale Owl"), ("Coruja da Tempestade", "Storm Owl"),
                     "Nasce de quem não para quieto: viajantes, arqueiros e quem prefere ver o mundo de cima.",
                     "Born from those who never sit still: travelers, archers and those who like the view from above."),
        "dawn_hare": (("Lebrinha da Aurora", "Dawn Leveret"), ("Lebre da Aurora", "Dawn Hare"), ("Lebre de Galhada Solar", "Sun-Antler Hare"),
                      "Nasce de quem cuida: luz, meditação, boas trocas e criação de animais.",
                      "Born from those who care: light, meditation, fair trades and raising animals."),
        "shade_cat": (("Gatinho das Sombras", "Shade Kitten"), ("Gato das Sombras", "Shade Cat"), ("Nekomata da Meia-Noite", "Midnight Nekomata"),
                      "Nasce de quem anda nas sombras: passos leves, golpes certeiros e nenhuma pressa.",
                      "Born from those who walk in the shadows: light steps, sure strikes and no hurry."),
        "rune_wisp": (("Centelha Rúnica", "Rune Spark"), ("Fogo-Fátuo Rúnico", "Rune Wisp"), ("Astro Rúnico", "Rune Star"),
                      "Nasce de quem estuda: glifos, estantes, encantamentos e longas meditações.",
                      "Born from those who study: glyphs, bookshelves, enchantments and long meditations."),
        "verdant_sprite": (("Brotinho", "Seedling"), ("Broto Verdejante", "Verdant Sprout"), ("Kodama da Floresta", "Forest Kodama"),
                           "Nasce de quem planta e cria: colheitas, rebanhos e a paciência da floresta.",
                           "Born from those who plant and raise: harvests, herds and the patience of the forest."),
        "bone_raven": (("Corvinho de Osso", "Bone Fledgling"), ("Corvo de Osso", "Bone Raven"), ("Corvo Psicopompo", "Psychopomp Raven"),
                       "Nasce de quem conhece a morte de perto: quem cai muitas vezes e quem caça os que já caíram.",
                       "Born from those who know death up close: who fall often, and who hunt the already fallen."),
        "fortune_drake": (("Dragonete da Sorte", "Lucky Drakeling"), ("Dragonete da Fortuna", "Fortune Drake"), ("Dragão do Tesouro", "Hoard Dragon"),
                          "Nasce de quem vive atrás de glória e ouro: chefes derrotados, baús abertos e apostas altas.",
                          "Born from those who chase glory and gold: fallen bosses, opened chests and high stakes."),
    }
    for fid, (s1, s2, s3, dpt, den) in forms.items():
        for i, (pt, en) in enumerate((s1, s2, s3), start=1):
            t("familiars.isekai_mahou.form.%s.%d" % (fid, i), pt, en)
        t("familiars.isekai_mahou.form.%s.desc" % fid, dpt, den)

    # ---- ajudas
    t("familiars.isekai_mahou.help.fetch", "Busca: traz para você os itens caídos por perto e espanta o frio.",
      "Fetch: brings nearby dropped items to you and keeps the cold away.")
    t("familiars.isekai_mahou.help.soothe", "Acalento: apaga o fogo, devolve fôlego debaixo d'água e cura bem de leve fora de combate.",
      "Soothe: puts out fire, gives back breath underwater and heals very lightly out of combat.")
    t("familiars.isekai_mahou.help.ore", "Faro de minério: de tempos em tempos (ou com um carinho) aponta o minério mais próximo.",
      "Ore sense: from time to time (or when petted) points at the nearest ore.")
    t("familiars.isekai_mahou.help.watch", "Vigia: avisa de longe quem está caçando você e você ainda não viu.",
      "Watch: warns you from afar about whoever is hunting you that you haven't seen yet.")
    t("familiars.isekai_mahou.help.mend", "Luz mansa: cura leve fora de combate (e, crescido, tira veneno).",
      "Gentle light: light healing out of combat (and, once grown, cures poison).")
    t("familiars.isekai_mahou.help.ambush", "Sexto sentido: avisa de creepers, inimigos invisíveis e de quem chega pelas costas.",
      "Sixth sense: warns of creepers, invisible enemies and anyone coming from behind.")
    t("familiars.isekai_mahou.help.mana", "Centelha: fora de combate, devolve aos poucos o seu recurso — só até a metade.",
      "Spark: out of combat, slowly restores your resource — only up to half.")
    t("familiars.isekai_mahou.help.forage", "Colheita: quando a fome aperta, traz um petisco de tempos em tempos.",
      "Forage: when hunger bites, brings you a snack from time to time.")
    t("familiars.isekai_mahou.help.death_guide", "Guia das almas: mostra o caminho até a sua última morte e avisa de mortos-vivos.",
      "Soul guide: shows the way to your last death and warns of the undead.")
    t("familiars.isekai_mahou.help.treasure", "Faro de tesouro: aponta baús de saque ainda fechados (e, crescido, geradores de monstros).",
      "Treasure sense: points at unopened loot chests (and, once grown, monster spawners).")

    # ---- mensagens
    t("familiars.isekai_mahou.msg.awakened", "Um vínculo nasceu: %s desperta ao seu lado.", "A bond is born: %s awakens by your side.")
    t("familiars.isekai_mahou.msg.evolved", "Seu familiar cresceu: %s agora é %s!", "Your familiar has grown: %s is now %s!")
    t("familiars.isekai_mahou.msg.shifted", "Você mudou, e seu familiar mudou com você: agora é %s.",
      "You changed, and your familiar changed with you: it is now %s.")
    t("familiars.isekai_mahou.msg.called", "%s voltou para o seu lado.", "%s is back by your side.")
    t("familiars.isekai_mahou.msg.dismissed", "%s foi descansar.", "%s went to rest.")
    t("familiars.isekai_mahou.msg.need_call", "Seu familiar precisa estar por perto para abrir a bolsa.",
      "Your familiar must be nearby to open its bag.")
    t("familiars.isekai_mahou.msg.nothing", "Seu familiar não achou nada por aqui.", "Your familiar found nothing around here.")
    t("familiars.isekai_mahou.msg.no_death", "Não há nenhuma morte sua para lembrar.", "There is no death of yours to remember.")
    t("familiars.isekai_mahou.msg.death_elsewhere", "Você caiu em outra dimensão: daqui ele não alcança.",
      "You fell in another dimension: it can't reach from here.")
    t("familiars.isekai_mahou.msg.death_trail", "Você caiu a %s blocos, naquela direção.", "You fell %s blocks away, that way.")
    t("familiars.isekai_mahou.ritual.need_class", "Escolha uma classe antes: o familiar nasce de quem você é.",
      "Choose a class first: the familiar is born from who you are.")
    t("familiars.isekai_mahou.ritual.need_level", "Você ainda é um mistério até para si: chegue ao nível %s.",
      "You are still a mystery even to yourself: reach level %s.")
    t("familiars.isekai_mahou.ritual.need_fire", "O incenso precisa de uma fogueira acesa por perto.", "The incense needs a lit campfire nearby.")
    t("familiars.isekai_mahou.ritual.same", "O vínculo se renova. %s continua sendo a sua cara.", "The bond is renewed. %s is still just like you.")
    t("familiars.isekai_mahou.stash", "Bolsa de %s", "%s's Bag")

    # ---- aba
    s = "screen.isekai_mahou.familiars."
    t(s + "tab", "Familiar", "Familiar")
    t(s + "none", "Você ainda não despertou um Familiar.", "You haven't awakened a Familiar yet.")
    t(s + "far", "Longe demais", "Too far away")
    t(s + "resting", "Descansando", "Resting")
    t(s + "call", "Chamar", "Call")
    t(s + "dismiss", "Guardar", "Dismiss")
    t(s + "help_on", "Ajuda: ligada", "Help: on")
    t(s + "help_off", "Ajuda: desligada", "Help: off")
    t(s + "stash", "Abrir a bolsa", "Open bag")
    t(s + "tip", "No mundo: clique nele para um carinho; agachado, abre a bolsa.", "In the world: click it to pet; sneaking opens the bag.")
    t(s + "name", "Dê um nome (Enter)", "Give it a name (Enter)")
    t(s + "stage", "Estágio %s/%s", "Stage %s/%s")
    t(s + "help_title", "O que ele faz por você", "What it does for you")
    t(s + "bond", "Vínculo", "Bond")
    t(s + "next_bond", "Vínculo %s/%s (tempo juntos, níveis, carinho)", "Bond %s/%s (time together, levels, petting)")
    t(s + "next_level", "Próximo estágio: você no nível %s", "Next stage: you at level %s")
    t(s + "max_stage", "Forma final. O vínculo de vocês é completo.", "Final form. Your bond is complete.")
    t(s + "why", "Por que esta forma", "Why this form")
    t(s + "drift", "Ele anda inquieto: você está cada vez mais com cara de %s (%s/%s).",
      "It is restless: you are looking more and more like a %s (%s/%s).")
    t(s + "reason.class", "Sua classe: %s", "Your class: %s")
    t(s + "reason.race", "Sua raça: %s", "Your race: %s")
    t(s + "reason.element", "Seu elemento mais usado: %s", "Your most used element: %s")
    feats = {
        "kills": ("Você luta muito", "You fight a lot"), "bosses": ("Você derruba chefes", "You take down bosses"),
        "mining": ("Você vive minerando", "You are always mining"), "harvest": ("Você colhe muito", "You harvest a lot"),
        "breed": ("Você cria animais", "You raise animals"), "brew": ("Você mistura poções", "You brew potions"),
        "study": ("Você estuda magia", "You study magic"), "meditate": ("Você medita", "You meditate"),
        "fish": ("Você pesca", "You fish"), "swim": ("Você vive na água", "You live in the water"),
        "flight": ("Você vive no ar", "You live in the air"), "explore": ("Você viaja longe", "You travel far"),
        "deaths": ("Você já morreu bastante", "You have died quite a bit"), "undead": ("Você caça mortos-vivos", "You hunt the undead"),
        "chests": ("Você abre todo baú que vê", "You open every chest you see"), "trades": ("Você negocia bem", "You trade well"),
        "sneak": ("Você anda agachado", "You sneak around"), "tank": ("Você aguenta pancada", "You take a beating"),
        "enchant": ("Você encanta itens", "You enchant items"), "glyphs": ("Você coleciona glifos", "You collect glyphs"),
    }
    for fid, (pt, en) in feats.items():
        t(s + "feat." + fid, pt, en)

    # ---- dicas de primeira vez (uma linha, uma vez)
    h = "hint.isekai_mahou."
    t(h + "familiar_ritual", "Você já pode despertar um Familiar: faça um Incenso do Vínculo e queime-o perto de uma fogueira.",
      "You can now awaken a Familiar: craft a Bond Incense and burn it near a campfire.")
    t(h + "familiar_incense", "Incenso do Vínculo: segure o botão direito perto de uma fogueira acesa.",
      "Bond Incense: hold right-click near a lit campfire.")
    t(h + "familiar_awakened", "Seu Familiar nasceu do seu jeito de jogar. Veja a aba Familiar no menu.",
      "Your Familiar was born from how you play. See the Familiar tab in the menu.")
    t(h + "familiar_shifted", "Familiares mudam com o dono: classe, raça, elemento e feitos moldam a forma.",
      "Familiars change with their owner: class, race, element and deeds shape the form.")
    t(h + "familiar_stash", "Agachado + clique no Familiar: abre a bolsa dele.", "Sneak + click the Familiar: opens its bag.")
    t(h + "familiar_help_fetch", "Sua raposa trouxe os itens caídos até você.", "Your fox brought the dropped items to you.")
    t(h + "familiar_help_soothe", "Sua lontra cuidou de você.", "Your otter took care of you.")
    t(h + "familiar_help_mend", "Sua lebre cura você aos poucos fora de combate.", "Your hare slowly heals you out of combat.")
    t(h + "familiar_help_mana", "Seu fogo-fátuo devolve um pouco de energia fora de combate.", "Your wisp restores a little energy out of combat.")
    t(h + "familiar_help_forage", "Seu broto trouxe um petisco: a fome diminui.", "Your sprout brought a snack: hunger eases.")
    t(h + "familiar_help_watch", "Sua coruja avisou: o contorno brilhante é quem está caçando você.",
      "Your owl warned you: the glowing outline is whoever is hunting you.")
    t(h + "familiar_help_ambush", "Seu gato sentiu uma emboscada: olhe o contorno brilhante.", "Your cat sensed an ambush: look for the glowing outline.")
    t(h + "familiar_help_death_guide", "Seu corvo aponta o perigo e o caminho até a sua última morte.",
      "Your raven points at danger and at the way to your last death.")
    t(h + "familiar_help_ore", "Sua tartaruga farejou minério: siga o brilho.", "Your tortoise smelled ore: follow the sparkle.")
    t(h + "familiar_help_treasure", "Seu dragonete farejou tesouro: siga o brilho.", "Your drake smelled treasure: follow the sparkle.")

    # ---- v1.0 (onda 4): receita de apoio que o dono ensina ao familiar (FamiliarSpell)
    t("screen.isekai_mahou.familiars.spell.button", "Magia de apoio",
      "Support spell")
    t("screen.isekai_mahou.familiars.spell.back", "Voltar",
      "Back")
    t("screen.isekai_mahou.familiars.spell.title", "Magia de apoio",
      "Support spell")
    t("screen.isekai_mahou.familiars.spell.about", "Ensine UMA magia de apoio: o familiar usa em você sozinho, mais fraca e com recarga. Só cura, limpeza, proteção, luz e achar coisas — ele não luta por você.",
      "Teach ONE support spell: the familiar casts it on you by itself, weaker and on a recharge. Only healing, cleansing, protection, light and finding things — it does not fight for you.")
    t("screen.isekai_mahou.familiars.spell.none", "Nenhuma magia ensinada.",
      "No spell taught.")
    t("screen.isekai_mahou.familiars.spell.ready", "Pronta.",
      "Ready.")
    t("screen.isekai_mahou.familiars.spell.wait", "Recarregando: %s s",
      "Recharging: %s s")
    t("screen.isekai_mahou.familiars.spell.forget", "Esquecer",
      "Forget")
    t("screen.isekai_mahou.familiars.spell.choose", "Ensinar uma das suas magias",
      "Teach one of your spells")
    t("screen.isekai_mahou.familiars.spell.empty", "Nenhuma das suas magias serve. Monte no Grimório uma magia só de apoio (ex.: Si Mesmo + Cura, Escudo, Luz ou Detectar) e volte aqui.",
      "None of your spells fits. Build a support-only spell in the Grimoire (e.g. Self + Heal, Shield, Light or Detect) and come back.")
    t("screen.isekai_mahou.familiars.spell.when.hurt", "Usa quando você está ferido.",
      "Used when you are hurt.")
    t("screen.isekai_mahou.familiars.spell.when.cleanse", "Usa quando você está em chamas ou com um efeito ruim.",
      "Used when you are on fire or under a bad effect.")
    t("screen.isekai_mahou.familiars.spell.when.danger", "Usa quando você está apanhando.",
      "Used when you are taking hits.")
    t("screen.isekai_mahou.familiars.spell.when.falling", "Usa quando você está caindo.",
      "Used when you are falling.")
    t("screen.isekai_mahou.familiars.spell.when.underwater", "Usa quando o seu ar está acabando.",
      "Used when you are running out of air.")
    t("screen.isekai_mahou.familiars.spell.when.dark", "Usa quando está escuro.",
      "Used when it is dark.")
    t("screen.isekai_mahou.familiars.spell.when.always", "Usa sempre que recarrega, fora das cidades.",
      "Used whenever it recharges, outside cities.")
    t("familiars.isekai_mahou.spell.taught", "%s aprendeu: %s",
      "%s learned: %s")
    t("familiars.isekai_mahou.spell.forgot", "%s esqueceu a magia de apoio.",
      "%s forgot its support spell.")
    t("familiars.isekai_mahou.spell.refused", "O familiar não aprende isso: %s",
      "The familiar cannot learn that: %s")
    t("familiars.isekai_mahou.spell.no.empty", "a magia está vazia.",
      "the spell is empty.")
    t("familiars.isekai_mahou.spell.no.complex", "só magias simples (um estágio, sem leis, gatilhos, ultimates ou transformações).",
      "simple spells only (one stage, no laws, triggers, ultimates or transformations).")
    t("familiars.isekai_mahou.spell.no.count", "use de 1 a %s efeitos.",
      "use 1 to %s effects.")
    t("familiars.isekai_mahou.spell.no.harm", "%s não é magia de apoio.",
      "%s is not a support spell.")
    t("hint.isekai_mahou.familiar_spell", "Seu Familiar aprendeu uma magia de apoio: ele usa em você sozinho quando for preciso.",
      "Your Familiar learned a support spell: it casts it on you by itself when needed.")
    t("hint.isekai_mahou.familiar_spell_cast", "Seu Familiar usou a magia que você ensinou. Ela recarrega sozinha.",
      "Your Familiar used the spell you taught it. It recharges on its own.")

    write_json(os.path.join(LANG, "pt_br.json"), {k: v[0] for k, v in sorted(T.items())})
    write_json(os.path.join(LANG, "en_us.json"), {k: v[1] for k, v in sorted(T.items())})


if __name__ == "__main__":
    gen_models()
    gen_item()
    gen_lang()
    print("familiars: %d formas x 3 estagios, %d textos" % (len(FORMS), len(T)))
