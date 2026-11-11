"""
Gerador de assets do modulo class_ranger (Ranger, v1.0).
Rodar a partir da raiz do projeto:  py -I tools/gen/class_ranger.py

Gera:
  - companheiros GeckoLib (.geo.json + animacoes + 3 texturas por estagio Filhote/Adulto/Alfa, cada uma com _glowmask):
      ranger_wolf   Lobo do Ranger (marcas de espirito que brilham mais a cada estagio)
      ranger_eagle  Aguia Batedora (marrom -> aguia-real -> aguia da tempestade); ranger_spirit_eagle = textura fantasma
      ranger_steed  Corcel do Ranger (ja selado; baio -> alazao com manta de folhas -> garanhao espiritual branco)
  - Traje Selvagem: 4 graus (scout, tracker, warden, beastlord) com o framework do tools/gen/outfits.py
      geo/item/armor/ranger_garb_<grau>.geo.json + textura + _glowmask (graus II-IV) + camadas vanilla de reserva
      icones 16x16 das 16 pecas + modelos de item
  - Arcos do Ranger: ranger_bow (kit) e ranger_bow_t1..t4 com modelo 3D na mao (framework do tools/gen/armory.py),
    estados de puxada e icone 2D
  - ovos (modelo template), icone da aba "Trilha do Ranger", traducoes src/main/lang/class_ranger/{pt_br,en_us}.json

Convencoes (Bedrock/GeckoLib): frente = -Z; -X = lado DIREITO da criatura; y para cima a partir dos pes.
"""
import json
import math
import os
import random
import sys
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken, item_model  # noqa: E402
import outfits as OF  # noqa: E402  (so o framework de roupas: nada e gerado no import)
import armory as AR  # noqa: E402  (so o framework de armas 3D)

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
LANG_DIR = os.path.join(ROOT, "src/main/lang/class_ranger")
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Modelo de entidade (box UV automatico) e pintura por face
# =====================================================================================
class Face:
    def __init__(self, model, name, x, y, w, h):
        self.m, self.name, self.x, self.y, self.w, self.h = model, name, x, y, w, h

    def set(self, fx, fy, c, glow=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, C(c))
            self.m.glow.set(self.x + fx, self.y + fy, C(c) if glow else CLEAR)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def is_glow(self, fx, fy):
        return self.m.glow.get(self.x + fx, self.y + fy)[3] > 0

    def rect(self, x0, y0, x1, y1, c, glow=False):
        for yy in range(max(0, y0), min(self.h - 1, y1) + 1):
            for xx in range(max(0, x0), min(self.w - 1, x1) + 1):
                self.set(xx, yy, c, glow)

    def fill(self, c, glow=False):
        self.rect(0, 0, self.w - 1, self.h - 1, c, glow)

    def tint(self, fn):
        for yy in range(self.h):
            for xx in range(self.w):
                p = self.get(xx, yy)
                if p[3] > 0 and not self.is_glow(xx, yy):
                    self.m.tex.set(self.x + xx, self.y + yy, fn(p, xx, yy))

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


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []

    def bone(self, name, pivot, parent=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0):
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "inflate": inflate})

    def mirror(self, bone, origin, size, paint, inflate=0.0):
        self.cube(bone, [-(origin[0] + size[0]), origin[1], origin[2]], size, paint, inflate)

    def layout(self):
        items = [(i, 2 * (c["size"][2] + c["size"][0]), c["size"][2] + c["size"][1]) for i, c in enumerate(self.cubes)]
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
            if not ok:
                continue
            used = y + row_h
            hh = 32
            while hh < used:
                hh *= 2
            if hh <= W:
                self.tw, self.th = W, hh
                for i, c in enumerate(self.cubes):
                    c["uv"] = pos[i]
                return
        raise RuntimeError("modelo grande demais: " + self.ident)

    def faces(self, c):
        u, v = c["uv"]
        w, h, d = c["size"]
        return {
            "top": Face(self, "top", u + d, v, w, d),
            "bottom": Face(self, "bottom", u + d + w, v, w, d),
            "right": Face(self, "right", u, v + d, d, h),
            "front": Face(self, "front", u + d, v + d, w, h),
            "left": Face(self, "left", u + d + w, v + d, d, h),
            "back": Face(self, "back", u + 2 * d + w, v + d, w, h),
        }

    def paint(self, seed=1):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            c["paint"](self.faces(c), seed + i * 17)

    def save(self, bounds):
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
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                                "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                                "visible_bounds_offset": [0, bounds[2], 0]},
                "bones": bones}]})

    def save_texture(self, name):
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# =====================================================================================
#  Pinceis (leem a paleta do estagio atual em PAL)
# =====================================================================================
PAL = {}


def col(key):
    return C(PAL[key])


def shade(fs, base, seed, noise=0.05, grad=0.18):
    for name, f in fs.items():
        c = C(base)
        if name == "top":
            c = lighten(c, 0.12)
        elif name == "bottom":
            c = darken(c, 0.3)
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


def solid(key, edge=0.3, noise=0.05, grad=0.18):
    def p(fs, seed):
        base = col(key)
        shade(fs, base, seed, noise, grad)
        if edge:
            for f in fs.values():
                if f.w >= 3 and f.h >= 3:
                    f.border(darken(base, edge))
    return p


def glowing(key, edge=0.25):
    def p(fs, seed):
        rnd = random.Random(seed)
        base = col(key)
        for f in fs.values():
            for yy in range(f.h):
                for xx in range(f.w):
                    e = (xx in (0, f.w - 1) or yy in (0, f.h - 1)) and f.w > 2 and f.h > 2
                    f.set(xx, yy, darken(base, edge) if e else lighten(base, 0.15 * rnd.random()), True)
    return p


def fur(key, marks=0, belly=None):
    """Pelagem: mechas verticais, pontas claras; 'marks' = linhas de espirito que brilham (0..3)."""
    def p(fs, seed):
        base = col(key)
        shade(fs, base, seed, 0.08, 0.16)
        rnd = random.Random(seed)
        for n, f in fs.items():
            if n in ("top", "bottom"):
                continue
            for xx in range(0, f.w, 2):
                for yy in range(f.h):
                    if rnd.random() < 0.35:
                        f.set(xx, yy, darken(f.get(xx, yy), 0.14))
                    elif rnd.random() < 0.08:
                        f.set(xx, yy, lighten(f.get(xx, yy), 0.18))
        if belly:
            fs["bottom"].fill(col(belly))
            fs["bottom"].noise(0.05, seed)
        if marks:
            spirit = col("mark")
            for n in ("left", "right", "top")[:max(1, marks)]:
                f = fs[n]
                if f.w < 4 or f.h < 3:
                    continue
                for _ in range(marks):
                    x = rnd.randint(1, max(1, f.w - 3))
                    for yy in range(1, f.h - 1):
                        f.set(x, yy, spirit, True)
                        if rnd.random() < 0.5:
                            x = max(1, min(f.w - 2, x + rnd.choice((-1, 1))))
                            f.set(x, yy, lighten(spirit, 0.3), True)
    return p


def feathers(key, edge_key, tip_glow=0):
    """Penas em escamas; tip_glow > 0 = pontas que brilham (aguia da tempestade / espirito)."""
    def p(fs, seed):
        base = col(key)
        shade(fs, base, seed, 0.06, 0.12)
        edge = col(edge_key)
        for n, f in fs.items():
            for yy in range(1, f.h, 2):
                for xx in range((yy // 2) % 2, f.w, 2):
                    f.set(xx, yy, darken(f.get(xx, yy), 0.15))
            if n in ("top", "bottom", "front", "back") and f.h >= 2:
                for xx in range(f.w):
                    f.set(xx, f.h - 1, edge, tip_glow > 0 and xx % 2 == 0)
        if tip_glow > 1:
            g = col("mark")
            for n in ("top", "bottom"):
                f = fs[n]
                for xx in range(0, f.w, 3):
                    f.set(xx, f.h - 1, g, True)
    return p


def combine(*painters):
    def p(fs, seed):
        for q in painters:
            q(fs, seed)
    return p


def anim_file(ident, anims):
    out = {}
    for name, a in anims.items():
        entry = {"animation_length": a["length"], "bones": a["bones"]}
        if a.get("loop"):
            entry["loop"] = a["loop"]
        out["animation." + ident + "." + name] = entry
    write_json(os.path.join(ASSETS, "animations/entity", ident + ".animation.json"), {"format_version": "1.8.0", "animations": out})


def kf(*pairs):
    d = {}
    for i in range(0, len(pairs), 2):
        d[str(round(float(pairs[i]), 4))] = [round(float(v), 3) for v in pairs[i + 1]]
    return d


def wave(length, amp, axis=0, phase=0.0, steps=8, offset=None):
    d = {}
    for i in range(steps + 1):
        t = length * i / steps
        v = [0.0, 0.0, 0.0]
        v[axis] = amp * math.sin(2 * math.pi * (i / steps) + phase)
        if offset:
            v = [v[k] + offset[k] for k in range(3)]
        d[str(round(t, 4))] = [round(x, 3) for x in v]
    return d


# =====================================================================================
#  LOBO DO RANGER
# =====================================================================================
WOLF_STAGES = [
    # Filhote: cinza-pardo, poucas marcas
    dict(fur="#7A6E62", dark="#4A4038", light="#C8BCA8", mark="#7BD86A", eye="#E8D060", nose="#1E1A18", marks=1),
    # Adulto: cinza-chumbo com marcas verdes de espirito
    dict(fur="#5C5C66", dark="#30303A", light="#B4B4C2", mark="#8FF08A", eye="#9AFF8A", nose="#141418", marks=2),
    # Alfa: prateado com runas esmeralda e olhos dourados
    dict(fur="#D4D8DE", dark="#7E8692", light="#FFFFFF", mark="#3FFFB0", eye="#FFD84A", nose="#2A2E36", marks=3),
]


def wolf_model():
    m = Model("ranger_wolf")
    m.bone("body", [0, 10, 0])
    m.bone("mane", [0, 12, -5], "body")
    m.bone("head", [0, 13, -9], "body")
    m.bone("jaw", [0, 11, -14], "head")
    m.bone("tail", [0, 14, 8], "body")
    m.bone("tail_tip", [0, 8, 9], "tail")
    for name, x, z in (("leg_fl", -2, -4), ("leg_fr", 2, -4), ("leg_bl", -2, 5), ("leg_br", 2, 5)):
        m.bone(name, [x, 8, z], "body")
        m.cube(name, [x - 1, 0, z - 1], [2, 8, 2], lambda fs, s: fur("fur")(fs, s))
        m.cube(name, [x - 1.5, 0, z - 1.5], [3, 1, 3], solid("dark", 0.3))

    m.cube("body", [-3.5, 8, -6], [7, 7, 14], lambda fs, s: fur("fur", PAL["marks"], "light")(fs, s))
    m.cube("mane", [-4.5, 8, -9], [9, 9, 6], lambda fs, s: fur("light" if PAL["marks"] == 3 else "fur", max(1, PAL["marks"] - 1))(fs, s))
    m.cube("mane", [-2, 16, -8], [4, 2, 5], lambda fs, s: fur("dark")(fs, s))

    def head_paint(fs, seed):
        fur("fur")(fs, seed)
        f = fs["front"]
        f.set(1, 2, col("eye"), True)
        f.set(f.w - 2, 2, col("eye"), True)
        f.set(1, 1, darken(col("fur"), 0.5))
        f.set(f.w - 2, 1, darken(col("fur"), 0.5))
        if PAL["marks"] >= 2:
            for xx in (0, f.w - 1):
                f.set(xx, 3, col("mark"), True)
        if PAL["marks"] == 3:
            fs["top"].set(fs["top"].w // 2, 1, col("mark"), True)
            fs["top"].set(fs["top"].w // 2, 2, col("mark"), True)

    m.cube("head", [-3, 11, -14], [6, 6, 5], head_paint)
    m.cube("head", [-1.5, 11, -17], [3, 3, 3], combine(solid("light", 0.3), lambda fs, s: (fs["front"].set(1, 0, col("nose")),
                                                                                         fs["top"].set(1, 0, col("nose")))))
    m.cube("head", [-3, 17, -11], [2, 3, 1], solid("dark", 0.2))
    m.cube("head", [1, 17, -11], [2, 3, 1], solid("dark", 0.2))
    m.cube("jaw", [-1.5, 10, -17], [3, 1, 3], solid("light", 0.3))

    def tail_paint(fs, seed):
        fur("fur")(fs, seed)

    def tip_paint(fs, seed):
        fur("light")(fs, seed)
        if PAL["marks"] >= 2:
            for f in fs.values():
                for xx in range(f.w):
                    f.set(xx, f.h - 1, col("mark"), True)

    m.cube("tail", [-1.5, 8, 8], [3, 6, 3], tail_paint)
    m.cube("tail_tip", [-1, 4, 8.5], [2, 4, 2], tip_paint)
    m.layout()
    m.save([1.6, 1.6, 0.6])
    for stage, pal in enumerate(WOLF_STAGES):
        PAL.clear()
        PAL.update(pal)
        m.paint(41)
        m.save_texture("ranger_wolf_%d" % stage)
    anim_file("ranger_wolf", {
        "idle": {"loop": True, "length": 2.4, "bones": {
            "body": {"scale": kf(0, [1, 1, 1], 1.2, [1.02, 1.03, 1], 2.4, [1, 1, 1])},
            "tail": {"rotation": wave(2.4, 8, 2, offset=[35, 0, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 1.0, [3, 5, 0], 1.6, [0, -4, 0], 2.4, [0, 0, 0])}}},
        "walk": {"loop": True, "length": 0.8, "bones": {
            "leg_fl": {"rotation": wave(0.8, 26, 0)}, "leg_br": {"rotation": wave(0.8, 26, 0)},
            "leg_fr": {"rotation": wave(0.8, 26, 0, math.pi)}, "leg_bl": {"rotation": wave(0.8, 26, 0, math.pi)},
            "tail": {"rotation": wave(0.8, 12, 2, offset=[45, 0, 0])},
            "body": {"position": wave(0.8, 0.3, 1, steps=8)}}},
        "run": {"loop": True, "length": 0.45, "bones": {
            "leg_fl": {"rotation": wave(0.45, 50, 0)}, "leg_fr": {"rotation": wave(0.45, 50, 0, 0.6)},
            "leg_bl": {"rotation": wave(0.45, 50, 0, math.pi)}, "leg_br": {"rotation": wave(0.45, 50, 0, math.pi + 0.6)},
            "body": {"rotation": wave(0.45, 5, 0)},
            "tail": {"rotation": kf(0, [65, 0, 0], 0.45, [65, 0, 0])},
            "head": {"rotation": kf(0, [8, 0, 0], 0.45, [8, 0, 0])}}},
        "sit": {"loop": True, "length": 2.0, "bones": {
            "body": {"rotation": kf(0, [-22, 0, 0], 2.0, [-22, 0, 0]), "position": kf(0, [0, -2.5, 0], 2.0, [0, -2.5, 0])},
            "leg_bl": {"rotation": kf(0, [-68, 0, 0], 2.0, [-68, 0, 0])},
            "leg_br": {"rotation": kf(0, [-68, 0, 0], 2.0, [-68, 0, 0])},
            "leg_fl": {"rotation": kf(0, [22, 0, 0], 2.0, [22, 0, 0])},
            "leg_fr": {"rotation": kf(0, [22, 0, 0], 2.0, [22, 0, 0])},
            "head": {"rotation": kf(0, [20, 0, 0], 1.0, [18, 6, 0], 2.0, [20, 0, 0])},
            "tail": {"rotation": kf(0, [80, 0, 0], 2.0, [80, 0, 0])}}},
        "bite": {"length": 0.35, "bones": {
            "head": {"rotation": kf(0, [0, 0, 0], 0.1, [12, 0, 0], 0.35, [0, 0, 0]),
                     "position": kf(0, [0, 0, 0], 0.1, [0, -0.5, -2], 0.35, [0, 0, 0])},
            "jaw": {"rotation": kf(0, [0, 0, 0], 0.05, [32, 0, 0], 0.15, [0, 0, 0])}}},
        "howl": {"length": 1.6, "bones": {
            "head": {"rotation": kf(0, [0, 0, 0], 0.3, [-55, 0, 0], 1.3, [-55, 0, 0], 1.6, [0, 0, 0])},
            "jaw": {"rotation": kf(0, [0, 0, 0], 0.3, [18, 0, 0], 1.3, [18, 0, 0], 1.6, [0, 0, 0])},
            "body": {"rotation": kf(0, [0, 0, 0], 0.3, [-10, 0, 0], 1.3, [-10, 0, 0], 1.6, [0, 0, 0])}}},
    })


# =====================================================================================
#  AGUIA BATEDORA (+ textura da Aguia Espiritual)
# =====================================================================================
EAGLE_STAGES = [
    dict(body="#6E4A2A", dark="#3A2A1A", chest="#E8D8B8", nape="#8A6038", beak="#E0B030", eye="#F0C020", mark="#F0C020", tips=0),
    dict(body="#4A2E18", dark="#24160C", chest="#C8A070", nape="#D8A040", beak="#F0C030", eye="#FFD84A", mark="#FFE27A", tips=0),
    dict(body="#E4ECF4", dark="#7E8EA0", chest="#FFFFFF", nape="#B8C8D8", beak="#FFD84A", eye="#7AF0FF", mark="#7AF0FF", tips=2),
]
SPIRIT_EAGLE = dict(body="#FFE8A0", dark="#C8A040", chest="#FFF6D0", nape="#FFF0B8", beak="#FFFFFF", eye="#FFFFFF",
                    mark="#9AF0FF", tips=2)


def eagle_model():
    m = Model("ranger_eagle")
    m.bone("body", [0, 6, 0])
    m.bone("head", [0, 8, -3.5], "body")
    m.bone("wing_r", [-2.5, 7.5, -1], "body")
    m.bone("wing_r_tip", [-10.5, 7.5, -1], "wing_r")
    m.bone("wing_l", [2.5, 7.5, -1], "body")
    m.bone("wing_l_tip", [10.5, 7.5, -1], "wing_l")
    m.bone("tail", [0, 5.5, 4.5], "body")
    m.bone("legs", [0, 4, 0], "body")

    glow_all = lambda: PAL.get("spirit", False)  # noqa: E731

    def body_paint(fs, seed):
        (glowing("body") if glow_all() else feathers("body", "dark"))(fs, seed)

    def chest_paint(fs, seed):
        (glowing("chest") if glow_all() else solid("chest", 0.15))(fs, seed)

    def wing_paint(fs, seed):
        (glowing("body") if glow_all() else feathers("body", "dark", PAL["tips"]))(fs, seed)

    def tip_paint(fs, seed):
        (glowing("dark") if glow_all() else feathers("dark", "mark" if PAL["tips"] else "dark", PAL["tips"]))(fs, seed)

    def head_paint(fs, seed):
        if glow_all():
            glowing("nape")(fs, seed)
        else:
            shade(fs, col("nape"), seed, 0.04, 0.1)
        f = fs["front"]
        f.set(0, 1, col("eye"), True)
        f.set(f.w - 1, 1, col("eye"), True)
        for n in ("left", "right"):
            fs[n].set(fs[n].w - 1, 1, col("eye"), True)

    m.cube("body", [-2.5, 3, -3.5], [5, 5, 8], body_paint)
    m.cube("body", [-2, 3.5, -4], [4, 4, 1], chest_paint)
    m.cube("head", [-1.5, 7, -6.5], [3, 3, 3], head_paint)
    m.cube("head", [-1, 10, -5.5], [2, 1, 3], lambda fs, s: (glowing("nape") if glow_all() else feathers("nape", "dark"))(fs, s))
    m.cube("head", [-0.5, 8, -8], [1, 1, 2], lambda fs, s: (glowing("beak") if glow_all() else solid("beak", 0.3))(fs, s))
    m.cube("head", [-0.5, 7, -8], [1, 1, 1], lambda fs, s: (glowing("beak") if glow_all() else solid("dark", 0.2))(fs, s))
    m.cube("wing_r", [-10.5, 7, -2.5], [8, 1, 6], wing_paint)
    m.cube("wing_r_tip", [-16.5, 7, -2], [6, 1, 5], tip_paint)
    m.mirror("wing_l", [-10.5, 7, -2.5], [8, 1, 6], wing_paint)
    m.mirror("wing_l_tip", [-16.5, 7, -2], [6, 1, 5], tip_paint)
    m.cube("tail", [-2, 5, 4.5], [4, 1, 5], lambda fs, s: (glowing("dark") if glow_all() else feathers("dark", "chest", PAL["tips"]))(fs, s))
    m.cube("tail", [-3, 5, 8.5], [6, 1, 2], lambda fs, s: (glowing("chest") if glow_all() else feathers("chest", "dark"))(fs, s))
    m.cube("legs", [-1.5, 1, -0.5], [1, 3, 1], lambda fs, s: solid("beak", 0.3)(fs, s))
    m.cube("legs", [0.5, 1, -0.5], [1, 3, 1], lambda fs, s: solid("beak", 0.3)(fs, s))
    m.cube("legs", [-2, 0, -1.5], [2, 1, 2], lambda fs, s: solid("dark", 0.2)(fs, s))
    m.cube("legs", [0, 0, -1.5], [2, 1, 2], lambda fs, s: solid("dark", 0.2)(fs, s))
    m.layout()
    m.save([2.2, 1.2, 0.4])
    for stage, pal in enumerate(EAGLE_STAGES):
        PAL.clear()
        PAL.update(pal)
        m.paint(51)
        m.save_texture("ranger_eagle_%d" % stage)
    PAL.clear()
    PAL.update(SPIRIT_EAGLE)
    PAL["spirit"] = True
    m.paint(53)
    m.save_texture("ranger_spirit_eagle")
    folded_r = [0, 75, -10]
    folded_l = [0, -75, 10]
    anim_file("ranger_eagle", {
        "fly": {"loop": True, "length": 0.5, "bones": {
            "wing_r": {"rotation": wave(0.5, 55, 2)},
            "wing_l": {"rotation": wave(0.5, -55, 2)},
            "wing_r_tip": {"rotation": wave(0.5, 25, 2, -0.6)},
            "wing_l_tip": {"rotation": wave(0.5, -25, 2, -0.6)},
            "body": {"position": wave(0.5, 0.6, 1, math.pi)},
            "tail": {"rotation": wave(0.5, 6, 0)}}},
        "glide": {"loop": True, "length": 1.4, "bones": {
            "wing_r": {"rotation": kf(0, [0, 0, -8], 0.7, [0, 0, -2], 1.4, [0, 0, -8])},
            "wing_l": {"rotation": kf(0, [0, 0, 8], 0.7, [0, 0, 2], 1.4, [0, 0, 8])},
            "wing_r_tip": {"rotation": kf(0, [0, 0, -6], 1.4, [0, 0, -6])},
            "wing_l_tip": {"rotation": kf(0, [0, 0, 6], 1.4, [0, 0, 6])},
            "body": {"rotation": kf(0, [10, 0, 0], 1.4, [10, 0, 0])}}},
        "perch": {"loop": True, "length": 3.0, "bones": {
            "wing_r": {"rotation": kf(0, folded_r, 3.0, folded_r)},
            "wing_l": {"rotation": kf(0, folded_l, 3.0, folded_l)},
            "wing_r_tip": {"rotation": kf(0, [0, 30, 0], 3.0, [0, 30, 0])},
            "wing_l_tip": {"rotation": kf(0, [0, -30, 0], 3.0, [0, -30, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 1.0, [0, 35, 0], 1.6, [0, 35, 0], 2.2, [0, -30, 0], 3.0, [0, 0, 0])},
            "tail": {"rotation": kf(0, [-12, 0, 0], 3.0, [-12, 0, 0])}}},
        "dive": {"length": 0.6, "bones": {
            "body": {"rotation": kf(0, [0, 0, 0], 0.15, [45, 0, 0], 0.45, [45, 0, 0], 0.6, [0, 0, 0])},
            "wing_r": {"rotation": kf(0, [0, 0, 0], 0.15, [0, -60, 20], 0.45, [0, -60, 20], 0.6, [0, 0, 0])},
            "wing_l": {"rotation": kf(0, [0, 0, 0], 0.15, [0, 60, -20], 0.45, [0, 60, -20], 0.6, [0, 0, 0])},
            "legs": {"rotation": kf(0, [0, 0, 0], 0.3, [-60, 0, 0], 0.6, [0, 0, 0])}}},
        "dive_hold": {"loop": True, "length": 0.5, "bones": {
            "body": {"rotation": kf(0, [0, 0, 0], 0.5, [0, 0, 0])},
            "wing_r": {"rotation": kf(0, [0, -55, 22], 0.25, [0, -60, 18], 0.5, [0, -55, 22])},
            "wing_l": {"rotation": kf(0, [0, 55, -22], 0.25, [0, 60, -18], 0.5, [0, 55, -22])},
            "legs": {"rotation": kf(0, [-70, 0, 0], 0.5, [-70, 0, 0])}}},
        "screech": {"length": 0.7, "bones": {
            "head": {"rotation": kf(0, [0, 0, 0], 0.15, [-35, 0, 0], 0.5, [-35, 0, 0], 0.7, [0, 0, 0])},
            "wing_r": {"rotation": kf(0, [0, 0, 0], 0.2, [0, 0, 40], 0.5, [0, 0, 40], 0.7, [0, 0, 0])},
            "wing_l": {"rotation": kf(0, [0, 0, 0], 0.2, [0, 0, -40], 0.5, [0, 0, -40], 0.7, [0, 0, 0])}}},
    })


# =====================================================================================
#  CORCEL DO RANGER
# =====================================================================================
STEED_STAGES = [
    dict(coat="#8A5A32", dark="#4A2E18", mane="#2A1A10", hoof="#3A3030", leather="#6A4428", blanket="#3E7A2E",
         trim="#C8B070", mark="#9AF07A", eye="#1A1410", glow_mane=False, glow_eye=False),
    dict(coat="#7A3E1E", dark="#40200E", mane="#1E120A", hoof="#2A2424", leather="#5A3820", blanket="#2E6A3A",
         trim="#E8C050", mark="#9AF07A", eye="#2A1A10", glow_mane=False, glow_eye=False),
    dict(coat="#E6EAEC", dark="#9AA4AC", mane="#6BFFB0", hoof="#8AF0C8", leather="#3A2A1A", blanket="#1E5A3A",
         trim="#FFD84A", mark="#6BFFB0", eye="#6BFFB0", glow_mane=True, glow_eye=True),
]


def steed_model():
    m = Model("ranger_steed")
    m.bone("body", [0, 12, 8])
    m.bone("neck", [0, 19, -8], "body")
    m.bone("head", [0, 27, -11], "neck")
    m.bone("tail", [0, 20, 10], "body")
    for name, x, z in (("leg_fl", -3, -7), ("leg_fr", 3, -7), ("leg_bl", -3, 7), ("leg_br", 3, 7)):
        m.bone(name, [x, 11, z], "body")
        m.cube(name, [x - 1.5, 2, z - 1.5], [3, 9, 3], lambda fs, s: solid("coat", 0.25)(fs, s))
        m.cube(name, [x - 2, 0, z - 2], [4, 2, 4], lambda fs, s: (glowing("hoof") if PAL["glow_mane"] else solid("hoof", 0.3))(fs, s))
        m.cube(name, [x - 1.75, 2, z - 1.75], [3, 2, 3], lambda fs, s: solid("mane", 0.25)(fs, s), inflate=0.25)

    def coat_paint(fs, seed):
        shade(fs, col("coat"), seed, 0.05, 0.14)
        rnd = random.Random(seed)
        for n in ("left", "right", "top"):
            f = fs[n]
            for _ in range(f.w * f.h // 18):
                f.set(rnd.randrange(f.w), rnd.randrange(f.h), lighten(col("coat"), 0.12))

    m.cube("body", [-5, 11, -10], [10, 10, 20], coat_paint)

    def blanket_paint(fs, seed):
        shade(fs, col("blanket"), seed, 0.04, 0.1)
        for n, f in fs.items():
            if f.h >= 3:
                for xx in range(f.w):
                    f.set(xx, f.h - 1, col("trim"))
            if n in ("left", "right") and f.w >= 4 and f.h >= 4:
                # folhas bordadas (brilham no Adulto/Alfa)
                lit = PAL["mark"] != "#9AF07A" or PAL["trim"] != "#C8B070"
                for k, xx in enumerate(range(1, f.w - 1, 3)):
                    f.set(xx, f.h - 3, col("mark"), lit)
                    f.set(xx, f.h - 4 if k % 2 else f.h - 2, col("mark"), lit)

    m.cube("body", [-5.5, 15, -4], [11, 7, 9], blanket_paint)
    m.cube("body", [-4, 22, -3], [8, 1, 7], lambda fs, s: solid("leather", 0.3)(fs, s))
    m.cube("body", [-1, 22, -4], [2, 2, 1], lambda fs, s: solid("leather", 0.3)(fs, s))
    m.cube("body", [-6, 13, 0], [1, 7, 1], lambda fs, s: solid("leather", 0.2)(fs, s))
    m.cube("body", [5, 13, 0], [1, 7, 1], lambda fs, s: solid("leather", 0.2)(fs, s))
    m.cube("body", [-6.5, 12, -0.5], [2, 1, 2], lambda fs, s: solid("trim", 0.3)(fs, s))
    m.cube("body", [4.5, 12, -0.5], [2, 1, 2], lambda fs, s: solid("trim", 0.3)(fs, s))

    m.cube("neck", [-2, 17, -13], [4, 11, 5], coat_paint)

    def mane_paint(fs, seed):
        if PAL["glow_mane"]:
            glowing("mane", 0.2)(fs, seed)
        else:
            fur("mane")(fs, seed)

    m.cube("neck", [-1, 18, -8.5], [2, 12, 2], mane_paint)

    def head_paint(fs, seed):
        shade(fs, col("coat"), seed, 0.04, 0.1)
        for n in ("left", "right"):
            f = fs[n]
            f.set(1, 1, col("eye"), PAL["glow_eye"])
            f.set(f.w - 2, 1, col("eye"), PAL["glow_eye"])
        fs["front"].rect(1, 2, fs["front"].w - 2, 2, lighten(col("coat"), 0.35))

    m.cube("head", [-2.5, 23, -18], [5, 5, 7], head_paint)
    m.cube("head", [-2, 23, -21], [4, 4, 3], combine(solid("dark", 0.2), lambda fs, s: (fs["front"].set(1, 2, C("#141010")),
                                                                                      fs["front"].set(2, 2, C("#141010")))))
    m.cube("head", [-2, 28, -13], [1, 2, 1], lambda fs, s: solid("coat", 0.2)(fs, s))
    m.cube("head", [1, 28, -13], [1, 2, 1], lambda fs, s: solid("coat", 0.2)(fs, s))
    m.cube("head", [-1, 27, -12], [2, 2, 2], mane_paint)
    m.cube("head", [-3, 24.5, -17], [6, 1, 1], lambda fs, s: solid("leather", 0.2)(fs, s))
    m.cube("head", [-3, 23.5, -20], [6, 1, 1], lambda fs, s: solid("leather", 0.2)(fs, s))

    def tail_paint(fs, seed):
        mane_paint(fs, seed)

    m.cube("tail", [-1.5, 9, 10], [3, 11, 3], tail_paint)
    m.layout()
    m.save([2.6, 2.6, 1.0])
    for stage, pal in enumerate(STEED_STAGES):
        PAL.clear()
        PAL.update(pal)
        m.paint(61)
        m.save_texture("ranger_steed_%d" % stage)
    anim_file("ranger_steed", {
        "idle": {"loop": True, "length": 3.0, "bones": {
            "body": {"scale": kf(0, [1, 1, 1], 1.5, [1.01, 1.015, 1], 3.0, [1, 1, 1])},
            "neck": {"rotation": kf(0, [0, 0, 0], 1.5, [4, 0, 0], 3.0, [0, 0, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 1.0, [0, 6, 0], 2.0, [0, -6, 0], 3.0, [0, 0, 0])},
            "tail": {"rotation": wave(3.0, 8, 2, offset=[10, 0, 0])}}},
        "walk": {"loop": True, "length": 1.0, "bones": {
            "leg_fl": {"rotation": wave(1.0, 22, 0)}, "leg_br": {"rotation": wave(1.0, 22, 0)},
            "leg_fr": {"rotation": wave(1.0, 22, 0, math.pi)}, "leg_bl": {"rotation": wave(1.0, 22, 0, math.pi)},
            "neck": {"rotation": wave(1.0, 4, 0, 0, 8, [4, 0, 0])},
            "tail": {"rotation": wave(1.0, 6, 2, offset=[18, 0, 0])}}},
        "gallop": {"loop": True, "length": 0.5, "bones": {
            "leg_fl": {"rotation": wave(0.5, 48, 0)}, "leg_fr": {"rotation": wave(0.5, 48, 0, 0.7)},
            "leg_bl": {"rotation": wave(0.5, 48, 0, math.pi)}, "leg_br": {"rotation": wave(0.5, 48, 0, math.pi + 0.7)},
            "body": {"rotation": wave(0.5, 4, 0), "position": wave(0.5, 0.8, 1, math.pi / 2)},
            "neck": {"rotation": wave(0.5, 8, 0, 0.4, 8, [12, 0, 0])},
            "tail": {"rotation": kf(0, [55, 0, 0], 0.25, [62, 0, 0], 0.5, [55, 0, 0])}}},
        "rear": {"length": 1.2, "bones": {
            "body": {"rotation": kf(0, [0, 0, 0], 0.3, [-38, 0, 0], 0.9, [-38, 0, 0], 1.2, [0, 0, 0])},
            "leg_bl": {"rotation": kf(0, [0, 0, 0], 0.3, [38, 0, 0], 0.9, [38, 0, 0], 1.2, [0, 0, 0])},
            "leg_br": {"rotation": kf(0, [0, 0, 0], 0.3, [38, 0, 0], 0.9, [38, 0, 0], 1.2, [0, 0, 0])},
            "leg_fl": {"rotation": kf(0, [0, 0, 0], 0.3, [-50, 0, 0], 0.6, [-20, 0, 0], 0.9, [-50, 0, 0], 1.2, [0, 0, 0])},
            "leg_fr": {"rotation": kf(0, [0, 0, 0], 0.3, [-20, 0, 0], 0.6, [-50, 0, 0], 0.9, [-20, 0, 0], 1.2, [0, 0, 0])},
            "neck": {"rotation": kf(0, [0, 0, 0], 0.3, [-15, 0, 0], 0.9, [-15, 0, 0], 1.2, [0, 0, 0])}}},
    })


# =====================================================================================
#  TRAJE SELVAGEM (framework do outfits.py)
# =====================================================================================
HD, BD, RA, LA, RL, LL, RB, LB = OF.HD, OF.BD, OF.RA, OF.LA, OF.RL, OF.LL, OF.RB, OF.LB
pal = OF.pal
fc, OMIT = OF.fc, OF.OMIT
GARB_T = {
    # I Batedor: verde-musgo, couro, pele de lobo cinza, penas pardas
    "scout": OF.theme(pal("1A2A12", "2A421C", "3C5E28", "547E38", "76A04E"), pal("5A5048", "7A6E62", "9A8E7E", "BAAE9C", "D8CCBA"),
                      pal("3A2E22", "5A4632", "7A6044", "9A7C58", "BA9A70"), OF.LEATHER_STD, pal("3A2A1A", "6E4A2A", "9A7048", "C8A070", "E8D8B8"),
                      OF.SILVER, pal("1A4A10", "2E8A1E", "7AE85A", "D8FFC8"), "9AF07A"),
    # II Rastreador: verde-floresta com pele de lobo escura e penas de águia-real
    "tracker": OF.theme(pal("0E2414", "163A1E", "22562C", "34763E", "52A05C"), pal("30303A", "4A4A56", "66667A", "8A8A9C", "B4B4C2"),
                        pal("4A3A22", "7A6036", "A8864A", "D4AE66", "F2D898"), pal("22160E", "3A2618", "543822", "6E4C30", "8C6644"),
                        pal("24160C", "4A2E18", "8A5A30", "D8A040", "F0D080"), OF.SILVER_M, pal("1A5A10", "36A020", "9AF07A", "E0FFD0"), "9AF07A"),
    # III Guardião da Mata: verde-azulado, pele prateada, prata e runas turquesa
    "warden": OF.theme(pal("0C2A2A", "164242", "225E5A", "347E78", "52A89E"), pal("7E8692", "A0A8B2", "C4CAD2", "E2E6EA", "FFFFFF"),
                       pal("3A4A5A", "5E7286", "8AA0B4", "B8CCDC", "E8F4FF"), pal("22160E", "3A2618", "543822", "6E4C30", "8C6644"),
                       pal("6A7A8A", "9AAABA", "C8D4E0", "E4ECF4", "FFFFFF"), pal("3A4A5A", "5E7286", "8AA0B4", "B8CCDC", "E8F4FF"),
                       pal("0A4A42", "14907E", "7AE8D0", "D8FFF6"), "7AE8D0"),
    # IV Senhor das Feras: esmeralda profundo, pele branca de alfa, ouro e penas que brilham
    "beastlord": OF.theme(pal("06301C", "0C4A2C", "146C40", "22925A", "3ABE7A"), pal("A8B0B8", "C8D0D6", "E2E6EA", "F4F6F8", "FFFFFF"),
                          OF.GOLD_B, pal("2A1A0E", "44301A", "604428", "7E5C38", "9E784C"),
                          pal("7A6420", "B0922E", "E0C04A", "FFE07A", "FFF6C8"), OF.GOLD_B, pal("0A5A2A", "18A04A", "6AFF9A", "D8FFE6"), "6AFF9A"),
}
GARB_ORDER = ("scout", "tracker", "warden", "beastlord")


def garb_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = OF.v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    lit = lv > 0
    M["hood"] = lambda r: OF.cloth(r, MAIN, period=3, base=2, top=0.10, bottom=0.12)

    def hood_front(r):
        OF.cloth(r, MAIN, period=3, base=2)
        for j in range(2, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)
        for i in range(1, r.w - 1):
            r.set(i, 1, MAIN[1])

    M["hood_front"] = hood_front
    # pele de lobo (manto nos ombros e gola): mechas com pontas claras e barra desfiada
    M["pelt"] = lambda r: OF.fur(r, SEC, ragged=2, seed=7)
    M["pelt_top"] = lambda r: OF.fur(r, SEC)
    M["pelt_in"] = lambda r: OF.leather(r, LEATH, seams=False)

    def tunic(r, front):
        OF.cloth(r, MAIN, period=4, base=2)
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, LEATH[3] if j % 2 else MAIN[0])
                r.set(cx, j, MAIN[0] if j % 2 else LEATH[3])
            if lit:
                OF.rune_glyph(r, cx - 1, max(0, r.h - 6), GL, lv)

    M["tunic_front"] = lambda r: tunic(r, True)
    M["tunic"] = lambda r: tunic(r, False)

    def jerkin(r, front):
        OF.leather(r, LEATH, seams=False)
        r.row(0, LEATH[3])
        if front:
            cx = r.w // 2
            for j in range(r.h):
                if j % 2 == 0:
                    r.set(cx - 2, j, TRIM[2])
                    r.set(cx + 1, j, TRIM[2])

    M["jerkin_front"] = lambda r: jerkin(r, True)
    M["jerkin"] = lambda r: jerkin(r, False)

    def skirt(r):
        OF.cloth(r, MAIN, period=3, base=2, top=0.03, bottom=0.12)
        OF.leaf_hem(r, MAIN[3], 2)

    M["tunic_skirt"] = skirt
    M["tunic_in"] = lambda r: OF.cloth(r, MAIN, period=4, base=1, folds=False)

    def cape_out(r):
        OF.cloth(r, MAIN, period=4, base=2, top=0.06, bottom=0.14)
        # pegadas de lobo e penas bordadas (brilham nos graus altos)
        cx = r.w // 2
        put = r.glow if lv >= 2 else r.set
        cc = GL if lv >= 2 else MAIN[3]
        for j in range(5, r.h - 5, 5):
            for dx in (-1, 1):
                put(cx + dx, j, cc)
            put(cx, j + 1, cc)
            put(cx - 1, j + 2, cc)
            put(cx + 1, j + 2, cc)
        OF.leaf_hem(r, MAIN[3], 3)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (OF.cloth(r, SEC, period=4, base=1, folds=False), OF.leaf_hem(r, SEC[2], 3))
    M["cape_edge"] = lambda r: (r.fill(MAIN[1]), r.noise(0.03))

    def quiver(r):
        OF.leather(r, LEATH, seams=False)
        r.row(0, TRIM[3])
        r.row(-1, TRIM[1])
        for j in range(2, r.h - 1, 3):
            r.row(j, LEATH[3])
        if lit:
            r.glow(r.w // 2, r.h // 2, GEM[2])

    M["quiver"] = quiver
    M["quiver_top"] = lambda r: (r.fill(LEATH[0]), r.noise(0.02))
    M["shaft"] = lambda r: (r.fill(LEATH[3]), r.noise(0.02))
    M["fletch"] = lambda r: OF.feather(r, ACC, tip=GL if lv >= 2 else None, lit=lv >= 2)
    M["plume"] = lambda r: OF.feather(r, ACC, tip=GL if lv >= 1 else ACC[4], lit=lv >= 1)
    M["bracer"] = lambda r: (OF.leather(r, LEATH, seams=False), r.row(0, TRIM[3]), r.row(-1, TRIM[1]))

    def armguard(r):
        OF.leather(r, LEATH, seams=False)
        r.col(0, TRIM[2])
        r.col(-1, TRIM[2])
        (r.glow if lit else r.set)(r.w // 2, r.h // 2, GEM[2] if lit else TRIM[4])

    M["armguard"] = armguard
    M["sleeve"] = lambda r: OF.cloth(r, MAIN, period=3, base=2)
    M["trousers"] = lambda r: (OF.leather(r, LEATH, seams=False), r.folds(LEATH[1], LEATH[3], 4))
    M["boot"] = lambda r: (OF.leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))
    M["boot_cuff"] = lambda r: OF.fur(r, SEC)

    def fang(r):
        r.fill(ACC[4] if lv < 3 else TRIM[4])
        r.row(-1, ACC[3])
        r.noise(0.02)

    M["fang"] = fang
    M["ear"] = lambda r: OF.fur(r, SEC)

    def snout(r):
        OF.fur(r, SEC)
        if r.w >= 3 and r.h >= 2:
            (r.glow if lit else r.set)(1, 0, GL if lit else SEC[0])
            (r.glow if lit else r.set)(r.w - 2, 0, GL if lit else SEC[0])

    M["snout"] = snout
    return M


def garb_model(name, lv):
    m = OF.Model(name)
    OF.skeleton(m)
    # capuz com focinho e orelhas de lobo (o grau IV ganha presas e uma tiara de penas)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="hood_front", s="hood", ew="hood", u="hood", d=OMIT), inflate=0.75)
    m.box(HD, [-3.4, 31.2, -4.9], [6.8, 1.6, 2.2], fc(side="pelt_top", u="pelt_top@4x2", d="pelt_in@4x2"), inflate=0.1)
    m.box(HD, [-2.2, 32.3, -4.4], [1.6, 1.8, 1.0], fc(all="ear"))
    m.box(HD, [0.6, 32.3, -4.4], [1.6, 1.8, 1.0], fc(all="ear"))
    if lv >= 1:
        for x in (-2.6, 1.8):
            m.box(HD, [x, 30.0, -5.2], [0.8, 1.4, 0.5], fc(all="fang@1x2"))
    m.bone("hoodTail", [0, 30.5, 4.4], parent=HD)
    m.box("hoodTail", [-2.0, 26.8, 4.2], [4.0, 3.8, 1.4], fc(side="hood", u="hood@3x2", d="hood@3x2"))
    m.box("hoodTail", [-1.2, 24.8, 4.6], [2.4, 2.2, 1.0], fc(side="pelt", u="pelt_top@2x1", d=OMIT))
    if lv >= 3:
        OF.ring(m, HD, -4.8, 4.8, -4.8, 4.8, 30.4, 0.6, 0.25, side="o_metal_flat", cap="o_metal_flat")
        for k, x in enumerate((-3.6, -1.8, 1.8, 3.6)):
            OF.gem(m, HD, [x, 31.6, -5.15], [0.6, 2.2 - 0.4 * abs(k - 1.5), 0.3], "fletch@1x3")

    # túnica + gibão + manto de pele de lobo nos ombros + cinto + alça da aljava
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="tunic_front", s="tunic", ew="tunic", u="tunic", d="dark@2x2"), inflate=0.45)
    m.box(BD, [-4, 13.5, -2], [8, 8.5, 4], fc(n="jerkin_front", s="jerkin", ew="jerkin", u=OMIT, d=OMIT), inflate=0.7)
    m.box(BD, [-4.8, 20.4, -2.8], [9.6, 3.8, 5.6], fc(n="pelt", s="pelt", ew="pelt", u="pelt_top@4x4", d="pelt_in@2x2"))
    OF.gem(m, BD, [2.4, 22.2, -3.05], [1.2, 1.2, 0.4], "o_gem@2x2" if lv >= 1 else "trim_plate@2x2")
    m.box(BD, [-4, 12.0, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2", d="leather_top@2x2"), inflate=0.95)
    OF.strap_diag(m, BD, "strap", -3.0, 1, width=0.7)
    # abas da túnica (frente e costas) presas ao tronco
    m.box(BD, [-3.2, 8.8, -2.75], [6.4, 3.4, 0.5], fc(n="tunic_skirt", s="tunic_in@4x4", ew="tunic_in@1x3", u=OMIT, d=OMIT),
          rot=[-4, 0, 0], pivot=[0, 12.2, -2.5])
    m.box(BD, [-3.6, 8.4, 2.25], [7.2, 3.8, 0.5], fc(s="tunic_skirt", n="tunic_in@4x4", ew="tunic_in@1x3", u=OMIT, d=OMIT),
          rot=[4, 0, 0], pivot=[0, 12.2, 2.5])
    # capa (osso garbCape) com a aljava presa: os dois balançam juntos
    m.bone("garbCape", [0, 24.0, 2.9], parent=BD)
    bottom = OF.v9_cape_lengths(lv, 5.0)
    m.box("garbCape", [-4.5, bottom, 2.9], [9.0, 24.0 - bottom, 0.6], fc(s="cape_out", n="cape_in", ew="cape_edge@1x8", u="cape_edge@2x1", d=OMIT))
    q = "garbCape"
    m.box(q, [-3.4, 12.5, 3.5], [2.8, 9.2, 2.4], fc(side="quiver", u="quiver_top@2x2", d="quiver@2x2"), rot=[0, 0, 22], pivot=[-2.0, 17.0, 4.7])
    for k, (dx, dh) in enumerate(((0.0, 0.0), (-0.9, 0.7), (0.8, 0.4))):
        x = -2.0 + dx
        m.box(q, [x - 0.2, 21.4, 4.5 + 0.2 * k], [0.4, 2.2 + dh, 0.4], fc(all="shaft@1x2"), rot=[0, 0, 22], pivot=[-2.0, 17.0, 4.7])
        m.box(q, [x - 0.55, 23.0 + dh, 4.45 + 0.2 * k], [1.1, 1.7, 0.5], fc(ns="fletch@2x3", ew="fletch@1x3", u="fletch@1x1", d=OMIT),
              rot=[0, 0, 22], pivot=[-2.0, 17.0, 4.7])

    # braços: mangas, braçadeiras; protetor de arqueiro no esquerdo; penas de águia no ombro esquerdo (osso plume)
    m.box(RA, [-8, 15.5, -2], [4, 8.5, 4], fc(side="sleeve", u="tunic@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 4.2, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.7)
    m.mirror(RA, LA)
    m.box(LA, [4, 12.2, -2.2], [4, 5.0, 4.4], fc(side="armguard", u="leather_top@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RA, [-8.9, 21.4, -2.6], [5.0, 2.6, 5.2], fc(side="pelt", u="pelt_top@3x3", d="pelt_in@2x2"), rot=[0, 0, -12], pivot=[-6.5, 23, 0])
    m.bone("plume", [6.6, 24.0, 0.0], parent=LA)
    n_feathers = 2 + lv
    for k in range(n_feathers):
        z = -1.6 + 3.2 * k / max(1, n_feathers - 1)
        m.box("plume", [6.2, 23.2, z - 0.45], [0.4, 3.2 + 0.5 * (k % 2), 0.9], fc(ew="plume@1x4", ns="plume@1x4", u=OMIT, d=OMIT),
              rot=[0, 0, -18 - 6 * k], pivot=[6.4, 23.4, z])
    if lv >= 2:
        # broche de gema prendendo a pele no ombro direito
        OF.gem(m, RA, [-7.2, 23.6, -2.9], [1.0, 1.0, 0.4], "o_gem@2x2")

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)
    m.box(RB, [-3.9, 0, -2], [4, 6, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 5.2, -2], [4, 1.8, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.9)
    m.box(RB, [-3.9, 2.4, -2], [4, 0.6, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.7)
    if lv == 3:
        m.box(RB, [-4.75, 3.0, -0.6], [0, 2.4, 3.0], fc(ew="o_wing@6x5"))
    m.mirror(RB, LB)
    return m


def garb_wing(M, T):
    TRIM, GL = T["pal"]["TRIM"], T["GLOW"]

    def wing(r):
        shape = ["..WWWW", ".WWGGW", "WWGGW.", "WGGW..", "GW...."]
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "W":
                    r.set(i, j, TRIM[3])
                elif ch == "G":
                    r.glow(i, j, GL)
    M["o_wing"] = wing


def garb_layers(tid, main, trim, dark):
    c1 = Canvas(64, 32)
    rnd = random.Random(len(tid))
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.08:
                    c = trim
                c1.set(x, y, c)
    c1.save(os.path.join(ASSETS, "textures/models/armor", "ranger_garb_%s_layer_1.png" % tid))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(ASSETS, "textures/models/armor", "ranger_garb_%s_layer_2.png" % tid))


# ícones 16x16 das peças (letras = tons da paleta do grau)
GARB_ICONS = {
    "helmet": [
        "................",
        "...ee......ee...",
        "..eEe......eEe..",
        "..eEEeeeeeeEEe..",
        "..eEEEEEEEEEEe..",
        ".kmmmmmmmmmmmmk.",
        ".kmllmmmmmmllmk.",
        ".kml........lmk.",
        ".kml........lmk.",
        ".km..........mk.",
        ".km.w......w.mk.",
        "..k..........k..",
        "................",
        "................",
        "................",
        "................"],
    "chestplate": [
        "................",
        ".EEEEE....EEEEE.",
        "EEEEEEEEEEEEEEEE",
        "EEEmmmmmmmmmmEEE",
        ".kmmmfggfmmmmmk.",
        ".kmmmffffmmmmmk.",
        "..kmmmfgfmmmmk..",
        "..kmmmffffmmmk..",
        "..kmmmmfgfmmmk..",
        "..kffffffffffk..",
        "..khhhhGhhhhhk..",
        "..kmmmmmmmmmmk..",
        "..kmlmlmlmlmlk..",
        "..k.m.m.m.m.mk..",
        "................",
        "................"],
    "leggings": [
        "................",
        "..kffffffffffk..",
        "..khhhhGhhhhhk..",
        "..kfffffffffffk.",
        "..kffff..ffffk..",
        "..kfef....fefk..",
        "..kfef....fefk..",
        "..kfff....fffk..",
        "..kfef....fefk..",
        "..kfef....fefk..",
        "..kfff....fffk..",
        "..kfef....fefk..",
        "..kkkk....kkkk..",
        "................",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "...EEEE..EEEE...",
        "...EEEE..EEEE...",
        "...kffk..kffk...",
        "...kffk..kffk...",
        "...khhk..khhk...",
        "...kffk..kffk...",
        "..kfffk..kfffk..",
        ".kffffk..kffffk.",
        ".kkkkkk..kkkkkk.",
        "................",
        "................",
        "................"],
}
GARB_ICON_GEM = {"helmet": (7, 6), "chestplate": (7, 5), "leggings": (7, 2), "boots": (4, 8)}


def garb_icon(tid, piece, lv):
    th = GARB_T[tid]
    P, G = th["pal"], th["GEM"]
    cols = {"k": darken(P["MAIN"][0], 0.5), "m": P["MAIN"][2], "l": P["MAIN"][3], "e": P["SEC"][1], "E": P["SEC"][3],
            "f": P["LEATH"][2], "g": P["TRIM"][2], "h": P["LEATH"][1], "G": P["TRIM"][4], "w": P["ACC"][3]}
    c = Canvas(16, 16)
    for y, row in enumerate(GARB_ICONS[piece]):
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, cols[ch])
    c.outline(darken(P["MAIN"][0], 0.6))
    gx, gy = GARB_ICON_GEM[piece]
    if lv >= 1:
        c.set(gx, gy, G[2] if lv == 1 else G[3])
        if lv >= 2:
            for dx, dy in ((1, 0), (-1, 0)):
                if c.get(gx + dx, gy + dy)[3] > 0:
                    c.set(gx + dx, gy + dy, G[1] if lv == 2 else G[2])
    if lv == 3:
        OF.sparkle(c, (14, 1), lighten(th["GLOW"], 0.2))
    elif lv >= 1:
        OF.tier_pip(c, (14, 1), G)
    name = "ranger_%s_%s" % (tid, piece)
    c.save(os.path.join(ASSETS, "textures/item", name + ".png"))
    item_model(ASSETS, name)


def build_garb():
    old_a = OF.A
    OF.A = ASSETS  # o build() do outfits grava em OF.A (mesma pasta de assets)
    try:
        for lv, tid in enumerate(GARB_ORDER):
            th = GARB_T[tid]
            mats = garb_mats(th["pal"], th, lv)
            if lv == 3:
                garb_wing(mats, th)
            _, glow = OF.build(garb_model("ranger_garb_" + tid, lv), mats)
            if glow != (lv > 0):
                raise RuntimeError("ranger_garb_%s: glowmask=%s" % (tid, glow))
            P = th["pal"]
            garb_layers(tid, P["MAIN"][2], P["TRIM"][2], P["MAIN"][0])
            for piece in ("helmet", "chestplate", "leggings", "boots"):
                garb_icon(tid, piece, lv)
    finally:
        OF.A = old_a


# =====================================================================================
#  ARCOS DO RANGER (framework do armory.py): recurvo com pontas de cabeça de águia
# =====================================================================================
BOW_TIERS = [
    # (madeira, pontas, empunhadura, corda, gema, runa)
    (AR.WALNUT, AR.BONE, AR.LEATHER, AR.STRING, None, None),
    (AR.OAK, AR.BONE, AR.VINE_WRAP, AR.STRING, None, None),
    (AR.DARK_OAK, AR.SILVER, AR.VINE_WRAP, AR.STRING, AR.GEM_GREEN, None),
    (AR.EBONY, AR.SILVER, AR.SOUL_WRAP, AR.STRING, AR.GEM_CYAN, AR.RUNE_GREEN),
    (AR.IVORY, AR.GOLD, AR.VINE_WRAP, AR.GLOW_WIND, AR.GEM_GREEN, AR.GLOW_WIND),
]
RECURVE = [(0, 3.0), (-22.5, 3.5), (-45, 2.6), (-22.5, 1.6)]
RECURVE_DRAWN = [(0, 3.0), (-22.5, 3.5), (-67.5, 2.6), (-45, 1.6)]


def ranger_bow(t, state=-1):
    name = ("ranger_bow" if t == 0 else "ranger_bow_t%d" % t) + ("" if state < 0 else "_pulling_%d" % state)
    m = AR.Model(name, atlas=32)
    G = AR.BOW_G
    wood, tipm, grip, stringm, gemm, rune = BOW_TIERS[t]
    cz = 8.0
    segs = RECURVE_DRAWN if state == 2 else RECURVE
    tip = AR.limbs(m, G, segs, (1.3, 1.15, 0.95, 0.8), (wood, wood, wood, tipm), 1.2, cz)
    AR.seg(m, AR.uv(G, -1.8, 0.05), AR.uv(G, 1.8, 0.05), 1.9, grip, 1.6, cz)
    rx, ry = AR.uv(G, 0.9, 0.6)
    m.cbox(rx, ry, cz, 0.7, 0.7, 1.0, tipm, rot=("z", 45, (rx, ry, cz)))
    # pontas em cabeça de águia (bico virado para fora)
    for s in (1, -1):
        px, py = AR.uv(G, tip[0] * s, tip[1])
        m.cbox(px, py, cz, 1.2, 1.2, 1.4, tipm, rot=("z", 45, (px, py, cz)))
        bx, by = AR.uv(G, (tip[0] + 0.7) * s, tip[1] - 0.2)
        m.cbox(bx, by, cz, 0.6, 0.6, 0.8, AR.GOLD if t >= 3 else AR.BONE, rot=("z", 45, (bx, by, cz)))
    if gemm is not None:
        gx, gy = AR.uv(G, 0, 0.75)
        m.cbox(gx, gy, cz, 0.9, 0.9, 1.8, gemm, rot=("z", 45, (gx, gy, cz)))
    if rune is not None:
        for s in (1, -1):
            a, b = AR.uv(G, 3.6 * s, -0.25), AR.uv(G, 5.4 * s, -1.0)
            AR.seg(m, a, b, 1.3, rune, 1.3, cz)
    if t >= 1:
        # penas presas no meio dos braços (mais penas a cada grau)
        for s in (1, -1)[: (1 if t < 2 else 2)]:
            a = AR.uv(G, 2.2 * s, -0.3)
            b = AR.uv(G, 2.2 * s, -2.4)
            AR.seg(m, a, b, 0.9, AR.FEATHER_GREEN if t >= 3 else AR.FEATHER, 0.25, cz)
    if state < 0:
        AR.string_lines(m, G, tip, tip[1], stringm, cz)
    else:
        delta = (0.0, 22.5, 45.0)[state]
        nock = tip[1] - tip[0] * math.tan(math.radians(delta))
        AR.string_lines(m, G, tip, nock, stringm, cz)
        head = AR.IRON if t < 3 else (AR.GLOW_WIND if t == 4 else AR.RUNE_GREEN)
        AR.arrow_model(m, G, nock, nock + AR.BOW_ARROW, AR.OAK, head, AR.FEATHER_GREEN if t >= 2 else AR.FEATHER, cz)
    return m


def build_held(model, name):
    try:
        held, atlas = model.build(AR.ns("item/class_ranger/" + name), seed=zlib.crc32(name.encode()) & 0xFFFF)
    except RuntimeError:
        model.atlas = 64
        held, atlas = model.build(AR.ns("item/class_ranger/" + name), seed=zlib.crc32(name.encode()) & 0xFFFF)
    held["display"] = AR.VANILLA_BOW
    atlas.save(os.path.join(ASSETS, "textures/item/class_ranger", name + ".png"))
    write_json(os.path.join(ASSETS, "models/item/class_ranger", name + "_held.json"), held)


def wrapper(icon_id, held_name, overrides=None):
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": AR.ns("item/" + icon_id)}}
    obj = {"loader": "neoforge:separate_transforms", "gui_light": "front", "textures": {"particle": AR.ns("item/" + icon_id)},
           "base": {"parent": AR.ns("item/class_ranger/" + held_name + "_held")}, "perspectives": {"gui": flat, "fixed": flat}}
    if overrides:
        obj["overrides"] = overrides
    return obj


def build_bows():
    for t in range(5):
        iid = "ranger_bow" if t == 0 else "ranger_bow_t%d" % t
        build_held(ranger_bow(t), iid)
        wood, tipm, grip, stringm, gemm, rune = BOW_TIERS[t]
        AR.icon_bow(max(1, t), wood, tipm, grip, stringm, gemm).save(os.path.join(ASSETS, "textures/item", iid + ".png"))
        overrides = []
        for pred, st in AR.BOW_STATES:
            name = "%s_pulling_%d" % (iid, st)
            build_held(ranger_bow(t, st), name)
            write_json(os.path.join(ASSETS, "models/item/class_ranger", name + ".json"), wrapper(iid, name))
            overrides.append({"predicate": pred, "model": AR.ns("item/class_ranger/" + name)})
        write_json(os.path.join(ASSETS, "models/item", iid + ".json"), wrapper(iid, iid, overrides))


# =====================================================================================
#  Ovos e ícone da aba
# =====================================================================================
def build_misc():
    for egg in ("ranger_wolf_spawn_egg", "ranger_eagle_spawn_egg", "ranger_steed_spawn_egg"):
        write_json(os.path.join(ASSETS, "models/item", egg + ".json"), {"parent": "minecraft:item/template_spawn_egg"})
    # aba "Trilha do Ranger": pegada de lobo + pena sobre folha
    c = Canvas(16, 16)
    leaf = [C("#2E5A1A"), C("#4F8A2F"), C("#7AB84A"), C("#B8E890")]
    for y in range(16):
        for x in range(16):
            d = abs(x - 8) + abs(y - 8) * 0.8
            if d < 7.2:
                c.set(x, y, leaf[1] if (x + y) % 5 else leaf[0])
    for i in range(2, 14):
        c.set(i, 15 - i, leaf[3])
    paw = C("#F2E6C8")
    for (x, y) in ((6, 8), (7, 8), (8, 8), (6, 9), (7, 9), (8, 9), (7, 10)):
        c.set(x, y, paw)
    for (x, y) in ((4, 6), (6, 5), (8, 5), (10, 6)):
        c.set(x, y, paw)
        c.set(x, y + 1, paw)
    c.outline(C("#10200A"))
    c.save(os.path.join(ASSETS, "textures/gui/tab/ranger_trail.png"))


# =====================================================================================
#  Traduções (pt_br principal + en_us)
# =====================================================================================
GLYPHS_LANG = {
    "ranger_explosive_arrow": (
        ("Flecha Explosiva", "A ponta detona ao acertar uma criatura ou o chão: dano em área (5) com queda pela distância e "
                             "empurrão, sem quebrar blocos. Expandir aumenta o raio."),
        ("Explosive Arrow", "The tip detonates on hitting a creature or the ground: area damage (5) that falls off with distance "
                            "plus knockback, without breaking blocks. Expand widens the blast.")),
    "ranger_chain_arrow": (
        ("Flecha em Corrente", "Depois do acerto, flechas espectrais saltam para até 3 inimigos próximos (+1 por Amplificar), "
                               "cada salto com 80% do anterior."),
        ("Chain Arrow", "After the hit, spectral arrows leap to up to 3 nearby enemies (+1 per Amplify), each jump dealing 80% "
                        "of the previous one.")),
    "ranger_net_arrow": (
        ("Flecha-Rede", "Abre uma rede encantada: o alvo e quem estiver perto ficam enraizados e lentos; voadores são puxados "
                        "para o chão. Também arma a rede onde acertar no chão."),
        ("Net Arrow", "Unfolds an enchanted net: the target and anyone nearby are rooted and slowed; flyers are dragged to the "
                      "ground. It also sets the net where it hits the ground.")),
    "ranger_piercing_arrow": (
        ("Flecha Perfurante", "Golpe pesado (6,5) que deixa o alvo Exposto e atravessa: quem estiver atrás dele na linha do "
                              "tiro (10 blocos) leva 70%."),
        ("Piercing Arrow", "A heavy blow (6.5) that leaves the target Exposed and punches through: anyone behind it on the "
                           "line of fire (10 blocks) takes 70%.")),
    "ranger_arrow_rain": (
        ("Chuva de Flechas", "Meio segundo depois do acerto, 10 flechas espectrais (+3 por Amplificar) caem num círculo de 3 "
                             "blocos. Cada flecha fere 1,6; cada inimigo leva no máximo 3."),
        ("Arrow Rain", "Half a second after the hit, 10 spectral arrows (+3 per Amplify) rain down in a 3-block circle. Each "
                       "arrow deals 1.6; each enemy takes at most 3.")),
    "ranger_hunters_mark": (
        ("Marca do Caçador", "Fere um pouco e marca o alvo por 12 s: ele brilha através das paredes e leva +25% de dano seu e "
                             "dos seus companheiros (+10% por Amplificar). Os companheiros caçam os marcados primeiro."),
        ("Hunter’s Mark", "Deals a little damage and marks the target for 12 s: it glows through walls and takes +25% damage "
                               "from you and your companions (+10% per Amplify). Companions hunt marked prey first.")),
    "ranger_pack_call": (
        ("Chamado da Matilha", "Em si mesmo: chama o Lobo (e a Águia, a partir do rank I da Trilha), cura 30% e dá Regeneração. "
                               "Num inimigo: os companheiros aparecem e atacam o alvo com Força e Velocidade."),
        ("Call of the Pack", "On yourself: summons the Wolf (and the Eagle, from Trail rank I), heals 30% and grants "
                             "Regeneration. On an enemy: your companions appear and attack it with Strength and Speed.")),
    "ranger_steed_call": (
        ("Chamado do Corcel", "Em si mesmo: o Corcel do Ranger surge selado e você já monta (montado: galope com Velocidade e "
                              "Salto). Num inimigo: o Corcel investe, ferindo e empurrando tudo no caminho."),
        ("Call of the Steed", "On yourself: the Ranger’s Steed appears saddled and you mount it (while riding: a gallop "
                              "with Speed and Jump Boost). On an enemy: the Steed charges, hurting and knocking back everything "
                              "on its path.")),
    "ranger_seeking": (
        ("Flecha Guiada", "Aumento: a flecha persegue o inimigo mais próximo, voa mais rápido e pode acertar crítico."),
        ("Seeking Arrow", "Augment: the arrow chases the nearest enemy, flies faster and can land critical hits.")),
    "ranger_quickdraw": (
        ("Saque Rápido", "Aumento: +2 flechas em leque e mais velocidade, cada uma com menos poder (paga o custo por cópia)."),
        ("Quickdraw", "Augment: +2 arrows in a fan and more speed, each one weaker (pays the per-copy cost).")),
    "ranger_thousand_feathers": (
        ("Tempestade das Mil Penas", "Forma G (ultimate): uma Águia Espiritual gigante circula sobre a área enquanto 30 + "
                                     "4×Círculo flechas desabam (efeitos a 35%); no fim ela mergulha e os efeitos acertam o "
                                     "centro com poder total."),
        ("Storm of a Thousand Feathers", "G form (ultimate): a giant Spirit Eagle circles the area while 30 + 4×Circle arrows "
                                         "rain down (effects at 35%); at the end it dives and the effects strike the center "
                                         "at full power.")),
}

GARB_NAMES = {
    "scout": (("do Batedor", "Scout’s"), "Traje do Batedor", "Scout’s Garb",
              "Couro curtido e pele de lobo: leve para a trilha, quente para a vigília.",
              "Tanned leather and wolf pelt: light for the trail, warm for the night watch."),
    "tracker": (("do Rastreador", "Tracker’s"), "Traje do Rastreador", "Tracker’s Garb",
                "Penas de águia-real costuradas no ombro: nenhuma pegada escapa.",
                "Golden eagle feathers stitched to the shoulder: no track escapes."),
    "warden": (("do Guardião da Mata", "Forest Warden’s"), "Traje do Guardião da Mata", "Forest Warden’s Garb",
               "Runas turquesa da mata antiga sussurram onde a presa se esconde.",
               "Turquoise runes of the old forest whisper where the prey hides."),
    "beastlord": (("do Senhor das Feras", "Beastlord’s"), "Traje do Senhor das Feras", "Beastlord’s Garb",
                  "Pele branca de alfa e penas que brilham: as feras curvam a cabeça.",
                  "White alpha pelt and glowing feathers: the beasts bow their heads."),
}
PIECES_LANG = {"helmet": ("Capuz de Lobo", "Wolf Hood"), "chestplate": ("Gibão com Manto de Pele", "Jerkin and Pelt Mantle"),
               "leggings": ("Calças de Couro", "Leather Trousers"), "boots": ("Botas de Trilha", "Trail Boots")}

BOW_LANG = [
    (("Arco do Ranger", "Ranger’s Bow"), ("Arco recurvo de nogueira com pontas de cabeça de águia.",
                                              "A walnut recurve bow with eagle-head tips.")),
    (("Arco do Batedor", "Scout’s Bow"), ("Cada pena presa nele é uma caçada lembrada.",
                                              "Every feather tied to it is a hunt remembered.")),
    (("Arco do Rastreador", "Tracker’s Bow"), ("A gema verde pulsa quando a presa está perto.",
                                                   "The green gem pulses when prey is near.")),
    (("Arco do Guardião da Mata", "Forest Warden’s Bow"), ("Ébano e runas: o espírito da mata devolve parte das flechas.",
                                                               "Ebony and runes: the forest spirit returns some of the arrows.")),
    (("Arco do Senhor das Feras", "Beastlord’s Bow"), ("A corda é de vento vivo; as feras respondem ao seu estalo.",
                                                            "Its string is living wind; the beasts answer its snap.")),
]

RANKS_LANG = [("Batedor", "Scout"), ("Rastreador", "Tracker"), ("Guardião da Mata", "Forest Warden"),
              ("Mestre das Feras", "Beastmaster"), ("Senhor das Feras", "Lord of Beasts")]

PT = {
    "entity.isekai_mahou.ranger_wolf": "Lobo do Ranger",
    "entity.isekai_mahou.ranger_eagle": "Águia Batedora",
    "entity.isekai_mahou.ranger_steed": "Corcel do Ranger",
    "entity.isekai_mahou.ranger_spirit_eagle": "Águia Espiritual",
    "item.isekai_mahou.ranger_wolf_spawn_egg": "Ovo Gerador de Lobo do Ranger",
    "item.isekai_mahou.ranger_eagle_spawn_egg": "Ovo Gerador de Águia Batedora",
    "item.isekai_mahou.ranger_steed_spawn_egg": "Ovo Gerador de Corcel do Ranger",
    "legend.isekai_mahou.ranger_huntress_lament": "Lamento da Caçadora",
    "legend.isekai_mahou.ranger_hecate_shot": "Disparo Hécate",
    "tooltip.isekai_mahou.ranger_bow.stats": "Puxada: %1$s s · Flechas +%2$s%% de dano",
    "tooltip.isekai_mahou.ranger_bow.save": "%s%% de chance de a flecha voltar à aljava",
    "tooltip.isekai_mahou.ranger_bow.spell": "A forma Flecha do Grimório dispara por este arco.",
    "tooltip.isekai_mahou.ranger_garb.tier": "Grau %1$s · %2$s (Círculo %3$s)",
    "tooltip.isekai_mahou.ranger_garb.set": "Conjunto: Defesa %1$s · +%2$s Foco · +%3$s%% velocidade",
    "tooltip.isekai_mahou.ranger_garb.full": "Traje completo: flechas do Ranger +%s%% de dano",
    "message.isekai_mahou.class_ranger.steed_not_yours": "Este corcel responde apenas ao seu Ranger.",
    "message.isekai_mahou.class_ranger.stay": "%s: Fica!",
    "message.isekai_mahou.class_ranger.follow": "%s: Segue!",
    "message.isekai_mahou.class_ranger.resting": "%1$s está se recuperando na mata... (%2$s s)",
    "message.isekai_mahou.class_ranger.fell": "%1$s caiu! Volta em %2$s s.",
    "message.isekai_mahou.class_ranger.trail_missing": "Ainda falta para concluir este rank da Trilha do Ranger:",
    "message.isekai_mahou.class_ranger.rank_up": "Trilha do Ranger: você agora é %1$s! (+%2$s XP)",
    "message.isekai_mahou.class_ranger.garb": "Círculo %1$s: você recebeu o %2$s!",
    "message.isekai_mahou.class_ranger.garb_equipped": "O traje já foi vestido (as peças antigas foram para o inventário).",
    "message.isekai_mahou.class_ranger.garb_inventory": "As peças foram para o inventário.",
    "toast.isekai_mahou.class_ranger.rank_up": "Rank da Trilha: %s",
    "toast.isekai_mahou.class_ranger.garb": "%s",
    "toast.isekai_mahou.class_ranger.garb_sub": "Traje Selvagem · Círculo %s",
    "banner.isekai_mahou.class_ranger.rank_up": "A mata reconhece um novo %s!",
    "trail.isekai_mahou.class_ranger.req.arrow_kill": "Abater %s criaturas com flechas",
    "trail.isekai_mahou.class_ranger.req.long_kill": "Abater %s criaturas a 20+ blocos",
    "trail.isekai_mahou.class_ranger.req.companion_kill": "Seus companheiros abatem %s criaturas",
    "trail.isekai_mahou.class_ranger.req.mark_kill": "Abater %s alvos com a sua Marca",
    "trail.isekai_mahou.class_ranger.req.great_prey": "Abater %s grandes presas (chefes ou 150+ de vida)",
    "trail.isekai_mahou.class_ranger.req.ride_dist": "Cavalgar %s blocos no Corcel",
    "trail.isekai_mahou.class_ranger.need_class": "Só um Ranger percorre a Trilha.",
    "trail.isekai_mahou.class_ranger.need_previous": "Conclua o rank anterior primeiro.",
    "trail.isekai_mahou.class_ranger.need_level": "Nível %s",
    "trail.isekai_mahou.class_ranger.need_circle": "Círculo %s",
    "trail.isekai_mahou.class_ranger.reward_alpha": "Companheiros mais fortes",
    "command.isekai_mahou.class_ranger.rank": "Trilha do Ranger concluída até o rank %s.",
    "screen.isekai_mahou.class_ranger.tab": "Trilha do Ranger",
    "screen.isekai_mahou.class_ranger.companions": "Companheiros",
    "screen.isekai_mahou.class_ranger.wolf_role": "Luta ao seu lado e caça os marcados.",
    "screen.isekai_mahou.class_ranger.eagle_role": "Bate o terreno e marca os inimigos.",
    "screen.isekai_mahou.class_ranger.steed_role": "Montaria forte, já selada.",
    "screen.isekai_mahou.class_ranger.unlock_rank": "Chega no rank %s da Trilha.",
    "screen.isekai_mahou.class_ranger.bonuses": "Bônus de classe",
    "screen.isekai_mahou.class_ranger.bonus.fast": "Flechas 25% mais rápidas.",
    "screen.isekai_mahou.class_ranger.bonus.eye": "Olho de Caçador: flechas +%s%% (mais de longe).",
    "screen.isekai_mahou.class_ranger.bonus.mounted": "Arqueiro a cavalo: +15% montado.",
    "screen.isekai_mahou.class_ranger.bonus.feet": "Pés Leves: +6% de velocidade.",
    "screen.isekai_mahou.class_ranger.bonus.pack": "Companheiros crescem com você e dão XP.",
    "screen.isekai_mahou.class_ranger.stage": "%1$s · Nível %2$s",
    "screen.isekai_mahou.class_ranger.stage.0": "Filhote",
    "screen.isekai_mahou.class_ranger.stage.1": "Adulto",
    "screen.isekai_mahou.class_ranger.stage.2": "Alfa",
    "screen.isekai_mahou.class_ranger.locked": "Bloqueado",
    "screen.isekai_mahou.class_ranger.title": "Trilha do Ranger",
    "screen.isekai_mahou.class_ranger.subtitle": "Feitos de caçador abrem glifos, arcos e companheiros.",
    "screen.isekai_mahou.class_ranger.done": "Concluído",
    "screen.isekai_mahou.class_ranger.next": "Próximo",
    "screen.isekai_mahou.class_ranger.req_level": "Nível %1$s · Círculo %2$s",
    "screen.isekai_mahou.class_ranger.reward": "Recompensa:",
    "screen.isekai_mahou.class_ranger.reward_bow": "Arco grau %s",
    "screen.isekai_mahou.class_ranger.reward_legends": "2 Lendas",
    "screen.isekai_mahou.class_ranger.reward_xp": "+%s XP",
    "screen.isekai_mahou.class_ranger.claim": "Concluir Rank",
}
EN = {
    "entity.isekai_mahou.ranger_wolf": "Ranger’s Wolf",
    "entity.isekai_mahou.ranger_eagle": "Scout Eagle",
    "entity.isekai_mahou.ranger_steed": "Ranger’s Steed",
    "entity.isekai_mahou.ranger_spirit_eagle": "Spirit Eagle",
    "item.isekai_mahou.ranger_wolf_spawn_egg": "Ranger’s Wolf Spawn Egg",
    "item.isekai_mahou.ranger_eagle_spawn_egg": "Scout Eagle Spawn Egg",
    "item.isekai_mahou.ranger_steed_spawn_egg": "Ranger’s Steed Spawn Egg",
    "legend.isekai_mahou.ranger_huntress_lament": "Lament of the Huntress",
    "legend.isekai_mahou.ranger_hecate_shot": "Hecate Shot",
    "tooltip.isekai_mahou.ranger_bow.stats": "Draw: %1$s s · Arrows +%2$s%% damage",
    "tooltip.isekai_mahou.ranger_bow.save": "%s%% chance for the arrow to return to the quiver",
    "tooltip.isekai_mahou.ranger_bow.spell": "The Grimoire’s Arrow form shoots through this bow.",
    "tooltip.isekai_mahou.ranger_garb.tier": "Tier %1$s · %2$s (Circle %3$s)",
    "tooltip.isekai_mahou.ranger_garb.set": "Set: Defense %1$s · +%2$s Focus · +%3$s%% speed",
    "tooltip.isekai_mahou.ranger_garb.full": "Full set: Ranger arrows +%s%% damage",
    "message.isekai_mahou.class_ranger.steed_not_yours": "This steed only answers to its Ranger.",
    "message.isekai_mahou.class_ranger.stay": "%s: Stay!",
    "message.isekai_mahou.class_ranger.follow": "%s: Follow!",
    "message.isekai_mahou.class_ranger.resting": "%1$s is recovering in the woods... (%2$s s)",
    "message.isekai_mahou.class_ranger.fell": "%1$s has fallen! Back in %2$s s.",
    "message.isekai_mahou.class_ranger.trail_missing": "This Ranger’s Trail rank still needs:",
    "message.isekai_mahou.class_ranger.rank_up": "Ranger’s Trail: you are now a %1$s! (+%2$s XP)",
    "message.isekai_mahou.class_ranger.garb": "Circle %1$s: you received the %2$s!",
    "message.isekai_mahou.class_ranger.garb_equipped": "The garb is already on (old pieces went to your inventory).",
    "message.isekai_mahou.class_ranger.garb_inventory": "The pieces went to your inventory.",
    "toast.isekai_mahou.class_ranger.rank_up": "Trail rank: %s",
    "toast.isekai_mahou.class_ranger.garb": "%s",
    "toast.isekai_mahou.class_ranger.garb_sub": "Wild Garb · Circle %s",
    "banner.isekai_mahou.class_ranger.rank_up": "The forest acknowledges a new %s!",
    "trail.isekai_mahou.class_ranger.req.arrow_kill": "Slay %s creatures with arrows",
    "trail.isekai_mahou.class_ranger.req.long_kill": "Slay %s creatures from 20+ blocks",
    "trail.isekai_mahou.class_ranger.req.companion_kill": "Your companions slay %s creatures",
    "trail.isekai_mahou.class_ranger.req.mark_kill": "Slay %s targets bearing your Mark",
    "trail.isekai_mahou.class_ranger.req.great_prey": "Slay %s great prey (bosses or 150+ health)",
    "trail.isekai_mahou.class_ranger.req.ride_dist": "Ride %s blocks on the Steed",
    "trail.isekai_mahou.class_ranger.need_class": "Only a Ranger walks the Trail.",
    "trail.isekai_mahou.class_ranger.need_previous": "Complete the previous rank first.",
    "trail.isekai_mahou.class_ranger.need_level": "Level %s",
    "trail.isekai_mahou.class_ranger.need_circle": "Circle %s",
    "trail.isekai_mahou.class_ranger.reward_alpha": "Stronger companions",
    "command.isekai_mahou.class_ranger.rank": "Ranger’s Trail completed up to rank %s.",
    "screen.isekai_mahou.class_ranger.tab": "Ranger’s Trail",
    "screen.isekai_mahou.class_ranger.companions": "Companions",
    "screen.isekai_mahou.class_ranger.wolf_role": "Fights by your side and hunts marked prey.",
    "screen.isekai_mahou.class_ranger.eagle_role": "Scouts the land and marks enemies.",
    "screen.isekai_mahou.class_ranger.steed_role": "A strong mount, already saddled.",
    "screen.isekai_mahou.class_ranger.unlock_rank": "Arrives at Trail rank %s.",
    "screen.isekai_mahou.class_ranger.bonuses": "Class bonuses",
    "screen.isekai_mahou.class_ranger.bonus.fast": "Arrows fly 25% faster.",
    "screen.isekai_mahou.class_ranger.bonus.eye": "Hunter’s Eye: arrows +%s%% (more from afar).",
    "screen.isekai_mahou.class_ranger.bonus.mounted": "Horse archer: +15% while riding.",
    "screen.isekai_mahou.class_ranger.bonus.feet": "Light Feet: +6% movement speed.",
    "screen.isekai_mahou.class_ranger.bonus.pack": "Companions grow with you and grant XP.",
    "screen.isekai_mahou.class_ranger.stage": "%1$s · Level %2$s",
    "screen.isekai_mahou.class_ranger.stage.0": "Cub",
    "screen.isekai_mahou.class_ranger.stage.1": "Adult",
    "screen.isekai_mahou.class_ranger.stage.2": "Alpha",
    "screen.isekai_mahou.class_ranger.locked": "Locked",
    "screen.isekai_mahou.class_ranger.title": "Ranger’s Trail",
    "screen.isekai_mahou.class_ranger.subtitle": "Hunter’s deeds unlock glyphs, bows and companions.",
    "screen.isekai_mahou.class_ranger.done": "Completed",
    "screen.isekai_mahou.class_ranger.next": "Next",
    "screen.isekai_mahou.class_ranger.req_level": "Level %1$s · Circle %2$s",
    "screen.isekai_mahou.class_ranger.reward": "Reward:",
    "screen.isekai_mahou.class_ranger.reward_bow": "Bow tier %s",
    "screen.isekai_mahou.class_ranger.reward_legends": "2 Legends",
    "screen.isekai_mahou.class_ranger.reward_xp": "+%s XP",
    "screen.isekai_mahou.class_ranger.claim": "Complete Rank",
}


def write_lang():
    pt, en = dict(PT), dict(EN)
    # pacote de tecnicas de anime (Flecha Sagrada, Correntes, Linha de Bala, Deusa Loba...)
    from class_ranger_anime_lang import GLYPHS as ANIME_GLYPHS, PT as ANIME_PT, EN as ANIME_EN
    pt.update(ANIME_PT)
    en.update(ANIME_EN)
    glyphs = dict(GLYPHS_LANG)
    glyphs.update(ANIME_GLYPHS)
    for gid, ((pn, pd), (en_n, en_d)) in glyphs.items():
        pt["glyph.isekai_mahou." + gid] = pn
        pt["glyph.isekai_mahou." + gid + ".desc"] = pd
        en["glyph.isekai_mahou." + gid] = en_n
        en["glyph.isekai_mahou." + gid + ".desc"] = en_d
    for tid, ((suf_pt, pre_en), name_pt, name_en, flav_pt, flav_en) in GARB_NAMES.items():
        pt["outfit.isekai_mahou.ranger_garb." + tid] = name_pt
        en["outfit.isekai_mahou.ranger_garb." + tid] = name_en
        pt["tooltip.isekai_mahou.ranger_garb." + tid] = flav_pt
        en["tooltip.isekai_mahou.ranger_garb." + tid] = flav_en
        for piece, (ppt, pen) in PIECES_LANG.items():
            pt["item.isekai_mahou.ranger_%s_%s" % (tid, piece)] = "%s %s" % (ppt, suf_pt)
            en["item.isekai_mahou.ranger_%s_%s" % (tid, piece)] = "%s %s" % (pre_en, pen)
    for t, ((npt, nen), (fpt, fen)) in enumerate(BOW_LANG):
        iid = "ranger_bow" if t == 0 else "ranger_bow_t%d" % t
        pt["item.isekai_mahou." + iid] = npt
        en["item.isekai_mahou." + iid] = nen
        pt["tooltip.isekai_mahou.ranger_bow.flavor.%d" % t] = fpt
        en["tooltip.isekai_mahou.ranger_bow.flavor.%d" % t] = fen
    for n, (rpt, ren) in enumerate(RANKS_LANG, 1):
        pt["trail.isekai_mahou.class_ranger.rank.%d" % n] = rpt
        en["trail.isekai_mahou.class_ranger.rank.%d" % n] = ren
    write_json(os.path.join(LANG_DIR, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG_DIR, "en_us.json"), dict(sorted(en.items())))


def main():
    wolf_model()
    eagle_model()
    steed_model()
    build_garb()
    build_bows()
    build_misc()
    write_lang()
    print("class_ranger: assets gerados")


if __name__ == "__main__":
    main()
