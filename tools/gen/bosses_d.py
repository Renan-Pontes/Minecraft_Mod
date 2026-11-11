"""
Gerador de assets do modulo bosses_d (Chefes das Provacoes):
  - trial_avatar  "Avatar Elemental": humanoide flutuante (~2.8 blocos), nucleo de cristal no peito, placas de
    armadura, aureola atras da cabeca, fragmentos orbitando e circulo de aura no chao.
    Uma textura por elemento base (fire, water, earth, wind, light, shadow) + arcane. O brilho (_glowmask) e
    pintado em BRANCO/cinza: o renderer tinge com a cor do elemento (fusao = cor do pai B sobre a textura do pai A).
  - trial_sentinel "Sentinela da Provacao": cavaleiro-construto com escudo de torre e alabarda (textura neutra,
    tingida pelo elemento no renderer; brilho branco tingido).
  - animacoes (.animation.json), ovos, loot tables e a tag c:bosses (merge).

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_d.py

Convencoes (Bedrock/GeckoLib): frente do modelo = -Z; -X = lado DIREITO da criatura; y para cima a partir dos pes.
Rotacoes (graus): X negativo levanta um braco para a frente; Z positivo levanta para fora o braco direito (-X),
Z negativo levanta para fora o esquerdo (+X); X positivo inclina tronco/cabeca para a frente.
Ossos animados NUNCA tem rotacao inicial no .geo (rotacoes fixas ficam em ossos-filho nao animados).
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
MERGE = os.path.join(ROOT, "src/main/merge/bosses_d")
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def grey(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)


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
            # O brilho acompanha o pixel: pintar sem brilho apaga o brilho antigo daquele ponto.
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

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0):
        c = {"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size], "paint": paint,
             "inflate": inflate}
        self.cubes.append(c)
        return c

    def mirror_cube(self, bone, origin, size, paint):
        """Cubo espelhado em X (lado esquerdo a partir do direito)."""
        return self.cube(bone, [-(origin[0] + size[0]), origin[1], origin[2]], size, paint)

    def layout(self):
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
            if not ok:
                continue
            used = y + row_h
            hh = 16
            while hh < used:
                hh *= 2
            if hh <= W:
                self.tw, self.th = W, hh
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

    def paint(self, ctx):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for c in self.cubes:
            c["paint"](self.faces_of(c), c, ctx)

    def geo_json(self, bounds):
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

    def save_geo(self, bounds):
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json(bounds))

    def save_texture(self, name):
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# =====================================================================================
#  Pinceis
# =====================================================================================
def shade_faces(fs, base, seed, noise=0.05, grad=0.16):
    """Cor base por face (luz de cima), gradiente vertical e ruido."""
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


GLOW_HI = grey(255)
GLOW_MID = grey(225)
GLOW_LO = grey(190)


def glow_px(f, x, y, strength=1.0):
    f.set(x, y, grey(170 + 85 * strength), True)


def armor(key, seed, trim=True, edge=0.45):
    """Placa de armadura com contorno escuro e filete de metal no topo."""
    def p(fs, c, ctx):
        base = C(ctx[key])
        shade_faces(fs, base, seed, 0.06)
        for n, f in fs.items():
            f.border(darken(base, edge))
            if trim and n in ("front", "back", "left", "right") and f.h >= 4 and f.w >= 3:
                for xx in range(1, f.w - 1):
                    f.set(xx, 1, C(ctx["trim"]))
    return p


def under(seed):
    def p(fs, c, ctx):
        shade_faces(fs, ctx["under"], seed, 0.05, 0.22)
    return p


def cloth(seed, hem_glow=True):
    def p(fs, c, ctx):
        base = C(ctx["cloth"])
        shade_faces(fs, base, seed, 0.05, 0.22)
        for n in ("front", "back", "left", "right"):
            f = fs[n]
            for xx in range(1, f.w, 3):
                for yy in range(f.h):
                    f.set(xx, yy, darken(f.get(xx, yy), 0.18))
            # Barra rasgada embaixo + bainha brilhante
            rnd = random.Random(seed + len(n))
            for xx in range(f.w):
                cut = rnd.randint(0, 2)
                for k in range(cut):
                    f.set(xx, f.h - 1 - k, CLEAR)
                if hem_glow and n in ("front", "back"):
                    glow_px(f, xx, f.h - 1 - cut - 1, 0.6)
        fs["bottom"].fill(CLEAR)
    return p


def glow_solid(level=1.0, edge=0.7):
    """Cubo inteiro brilhando (cristal, aureola, fragmentos, aura): miolo claro, borda um pouco mais escura."""
    def p(fs, c, ctx):
        for f in fs.values():
            for yy in range(f.h):
                for xx in range(f.w):
                    e = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                    glow_px(f, xx, yy, level * (edge if e and f.w > 2 and f.h > 2 else 1.0))
    return p


def crystal_core(fs, c, ctx):
    for n, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                # Faceta: canto superior esquerdo mais claro
                t = (xx + yy) / max(1, f.w + f.h - 2)
                glow_px(f, xx, yy, 1.0 - 0.35 * t)


# ---------------- motivos por elemento (linhas brilhantes) ----------------
def motif(f, kind, seed, density=1.0):
    """Desenha o motivo do elemento numa face (pixels de brilho)."""
    rnd = random.Random(seed)
    w, h = f.w, f.h
    if w < 3 or h < 3:
        return
    if kind == "veins":          # fogo: veias de lava ramificadas
        for _ in range(max(1, int(2 * density))):
            x = rnd.randint(1, w - 2)
            for yy in range(h - 1, 0, -1):
                glow_px(f, x, yy, 0.6 + 0.4 * (1 - yy / h))
                if rnd.random() < 0.45:
                    x = max(1, min(w - 2, x + rnd.choice((-1, 1))))
    elif kind == "waves":        # agua: ondas horizontais
        for row in range(2, h - 1, 4):
            for xx in range(w):
                yy = row + (1 if (xx // 2) % 2 else 0)
                if 0 < yy < h - 1:
                    glow_px(f, xx, yy, 0.75)
    elif kind == "cracks":       # terra: rachaduras ambar em zigue-zague
        x, y = rnd.randint(1, w - 2), 1
        while y < h - 1:
            glow_px(f, x, y, 0.8)
            y += 1
            x = max(1, min(w - 2, x + rnd.choice((-1, 0, 1))))
            if rnd.random() < 0.25:
                glow_px(f, max(1, x - 1), y, 0.6)
    elif kind == "swirl":        # vento: tracos em espiral
        cx, cy = w / 2, h / 2
        for i in range(int(18 * density)):
            a = i * 0.55
            r = 0.6 + i * 0.22
            xx, yy = int(cx + math.cos(a) * r), int(cy + math.sin(a) * r)
            if 0 < xx < w - 1 and 0 < yy < h - 1:
                glow_px(f, xx, yy, 0.7)
    elif kind == "rays":         # luz: cruz/sol
        cx, cy = w // 2, h // 2
        for xx in range(1, w - 1):
            glow_px(f, xx, cy, 0.65)
        for yy in range(1, h - 1):
            glow_px(f, cx, yy, 0.65)
        for k in range(1, min(w, h) // 2):
            for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                if k % 2 == 0:
                    xx, yy = cx + sx * k, cy + sy * k
                    if 0 < xx < w - 1 and 0 < yy < h - 1:
                        glow_px(f, xx, yy, 0.5)
    elif kind == "eyes":         # sombra: runas em forma de olho
        for row in range(2, h - 2, 4):
            col = 2 + (row // 4) % 2 * 2
            while col < w - 2:
                glow_px(f, col, row, 0.85)
                glow_px(f, col - 1, row, 0.5)
                glow_px(f, col + 1, row, 0.5)
                col += 5
    else:                        # arcano: pontos runicos em grade
        for yy in range(2, h - 1, 3):
            for xx in range(1 + (yy // 3) % 2, w - 1, 3):
                glow_px(f, xx, yy, 0.7)


def armor_motif(key, seed, faces=("front",), density=1.0):
    def p(fs, c, ctx):
        armor(key, seed)(fs, c, ctx)
        for n in faces:
            motif(fs[n], ctx["motif"], seed + len(n) * 7, density)
    return p


def chest_plate(fs, c, ctx):
    armor("armor", 501)(fs, c, ctx)
    f = fs["front"]
    cx = f.w // 2
    # Encaixe escuro do cristal no centro + motivo saindo dele
    sock = darken(C(ctx["armor"]), 0.6)
    f.rect(cx - 2, 2, cx + 2, 6, sock)
    motif(f, ctx["motif"], 503, 1.2)
    f.rect(cx - 2, 2, cx + 2, 6, sock)
    for xx in (cx - 3, cx + 3):
        for yy in range(2, 7):
            glow_px(f, xx, yy, 0.55)
    for xx in range(cx - 2, cx + 3):
        glow_px(f, xx, 1, 0.55)
        glow_px(f, xx, 7, 0.55)


def head_paint(fs, c, ctx):
    armor("armor", 520, trim=False)(fs, c, ctx)
    f = fs["front"]
    f.rect(1, 2, f.w - 2, f.h - 2, C(ctx["mask"]))
    f.border(darken(C(ctx["armor"]), 0.5))


def visor_paint(fs, c, ctx):
    shade_faces(fs, ctx["mask"], 530, 0.04)
    f = fs["front"]
    f.border(darken(C(ctx["mask"]), 0.45))
    # Olhos: duas fendas brilhantes
    for xx in (1, 2):
        glow_px(f, xx, 1, 1.0)
    for xx in (f.w - 3, f.w - 2):
        glow_px(f, xx, 1, 1.0)
    glow_px(f, f.w // 2, 2, 0.5)


def crest_paint(fs, c, ctx):
    armor("armor_hi", 540)(fs, c, ctx)
    for n in ("left", "right"):
        f = fs[n]
        for xx in range(f.w):
            glow_px(f, xx, 0, 0.7)


def pauldron_paint(seed):
    def p(fs, c, ctx):
        armor("armor_hi", seed)(fs, c, ctx)
        for n in ("front", "back", "left", "right"):
            f = fs[n]
            for xx in range(f.w):
                glow_px(f, xx, f.h - 1, 0.6)
        motif(fs["top"], ctx["motif"], seed + 3, 0.6)
    return p


def gauntlet_paint(seed):
    def p(fs, c, ctx):
        armor("armor", seed)(fs, c, ctx)
        for n in ("front", "left", "right", "back"):
            f = fs[n]
            mid = f.w // 2
            for yy in range(2, f.h - 1):
                if yy % 2 == 0:
                    glow_px(f, mid, yy, 0.75)
    return p


def hand_paint(seed):
    def p(fs, c, ctx):
        shade_faces(fs, ctx["under"], seed, 0.05)
        f = fs["bottom"]
        f.fill(C(ctx["under"]))
        glow_px(f, f.w // 2, f.h // 2, 1.0)
        glow_px(f, f.w // 2 - 1, f.h // 2, 0.7)
    return p


def belt_paint(fs, c, ctx):
    shade_faces(fs, ctx["trim"], 560, 0.05)
    for n, f in fs.items():
        f.border(darken(C(ctx["trim"]), 0.4))
    f = fs["front"]
    cx = f.w // 2
    f.rect(cx - 1, 0, cx, f.h - 1, darken(C(ctx["trim"]), 0.3))
    glow_px(f, cx - 1, 0, 0.9)
    glow_px(f, cx, 1, 0.9)


def greave_paint(seed):
    def p(fs, c, ctx):
        armor("armor", seed)(fs, c, ctx)
        motif(fs["front"], ctx["motif"], seed + 1, 0.8)
    return p


def foot_paint(seed):
    def p(fs, c, ctx):
        armor("armor_hi", seed)(fs, c, ctx)
        f = fs["bottom"]
        glow_px(f, f.w // 2, f.h // 2, 0.9)
    return p


def tasset_paint(seed, glow_motif=True):
    def p(fs, c, ctx):
        armor("armor_hi", seed)(fs, c, ctx)
        if glow_motif:
            f = fs["front"]
            for yy in range(1, f.h - 1, 2):
                glow_px(f, f.w // 2, yy, 0.6)
    return p


# =====================================================================================
#  AVATAR ELEMENTAL
# =====================================================================================
PALETTES = {
    "fire": {"armor": "#3b2420", "armor_hi": "#5d3529", "trim": "#d98a35", "cloth": "#8e2416",
             "under": "#24130f", "mask": "#4a2a21", "motif": "veins"},
    "water": {"armor": "#1f4d70", "armor_hi": "#3a78a3", "trim": "#c2e7f2", "cloth": "#184b8a",
              "under": "#0f2a43", "mask": "#2b5f86", "motif": "waves"},
    "earth": {"armor": "#6b5841", "armor_hi": "#8c7457", "trim": "#c9a24f", "cloth": "#4f6b2e",
              "under": "#382d23", "mask": "#7a654e", "motif": "cracks"},
    "wind": {"armor": "#bcd8cc", "armor_hi": "#e6f4ee", "trim": "#6fb497", "cloth": "#5fae8b",
             "under": "#7e9a8e", "mask": "#d3ebe1", "motif": "swirl"},
    "light": {"armor": "#e6dcc0", "armor_hi": "#fbf6e8", "trim": "#d6a43a", "cloth": "#efe8d2",
              "under": "#c2b48d", "mask": "#f7efd6", "motif": "rays"},
    "shadow": {"armor": "#231b2f", "armor_hi": "#3b2e4f", "trim": "#7050a6", "cloth": "#3a1450",
               "under": "#120d18", "mask": "#2c2339", "motif": "eyes"},
    "arcane": {"armor": "#2a2650", "armor_hi": "#443e7c", "trim": "#c9b6ff", "cloth": "#4b2c7e",
               "under": "#191630", "mask": "#39336b", "motif": "runes"},
}


def build_avatar():
    m = Model("trial_avatar")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 17, 0], "root")
    m.cube("body", [-5, 16, -3], [10, 2, 6], belt_paint)
    m.cube("body", [-4.5, 18, -2.5], [9, 3, 5], under(410))
    m.cube("body", [-6, 21, -3.5], [12, 11, 7], armor_motif("armor", 420, faces=("back", "left", "right"), density=0.7))
    m.cube("body", [-6.5, 23, -4.5], [13, 8, 1], chest_plate)
    m.cube("body", [-5.5, 23, 3.5], [11, 8, 1], armor_motif("armor_hi", 430, faces=("back",)))
    m.cube("body", [-4, 32, -3], [8, 2, 6], armor("armor_hi", 440))
    # Nucleo de cristal (o osso "core" pulsa; o filho gira 45 graus para virar um losango)
    m.bone("core", [0, 28.5, -5.5], "body")
    m.bone("core_gem", [0, 28.5, -5.5], "core", rotation=[0, 0, 45])
    m.cube("core_gem", [-1.5, 27, -6.5], [3, 3, 2], crystal_core)
    # Cabeca: elmo sem rosto, viseira com olhos brilhantes, crista
    m.bone("head", [0, 33, 0], "body")
    m.cube("head", [-1.5, 33, -1.5], [3, 2, 3], under(450))
    m.cube("head", [-3.5, 35, -3.5], [7, 7, 7], head_paint)
    m.cube("head", [-3, 36, -4.5], [6, 3, 1], visor_paint)
    m.cube("head", [-0.5, 41, -3.5], [1, 4, 6], crest_paint)
    m.cube("head", [-4.5, 35, -2.5], [1, 4, 5], armor("armor_hi", 455))
    m.mirror_cube("head", [-4.5, 35, -2.5], [1, 4, 5], armor("armor_hi", 456))
    # Aureola atras da cabeca (o osso "crown" gira; o filho fixo a deixa em losango)
    m.bone("crown", [0, 39, 5], "head")
    m.bone("crown_ring", [0, 39, 5], "crown", rotation=[0, 0, 45])
    halo = glow_solid(1.0, 0.75)
    m.cube("crown_ring", [-5, 43, 4.5], [10, 1, 1], halo)
    m.cube("crown_ring", [-5, 34, 4.5], [10, 1, 1], halo)
    m.cube("crown_ring", [-5, 35, 4.5], [1, 8, 1], halo)
    m.cube("crown_ring", [4, 35, 4.5], [1, 8, 1], halo)
    # Bracos
    for side, sx in (("r", -1), ("l", 1)):
        arm = m.bone("arm_" + side, [sx * 7, 31, 0], "body")
        fore = m.bone("forearm_" + side, [sx * 8, 23, 0], arm)
        cubes = (
            (arm, [-11.5, 28, -3.5], [5, 4, 7], pauldron_paint(600 + sx)),
            (arm, [-11, 32, -2.5], [3, 2, 5], armor("armor", 610 + sx)),
            (arm, [-10, 23, -2], [4, 6, 4], under(620 + sx)),
            (fore, [-10.5, 16, -2.5], [5, 7, 5], gauntlet_paint(630 + sx)),
            (fore, [-10, 13, -2], [4, 3, 4], hand_paint(640 + sx)),
        )
        for bone, origin, size, paint in cubes:
            if sx < 0:
                m.cube(bone, origin, size, paint)
            else:
                m.mirror_cube(bone, origin, size, paint)
    # Manto
    m.bone("cape", [0, 33, 4.5], "body")
    m.cube("cape", [-5, 12, 4.5], [10, 20, 1], cloth(700))
    # Tassetes (saiote de placas)
    m.bone("tasset", [0, 17, 0], "body")
    m.cube("tasset", [-4.5, 9, -3.5], [9, 7, 1], tasset_paint(710))
    m.cube("tasset", [-5.5, 10, -2.5], [1, 6, 5], tasset_paint(711, False))
    m.mirror_cube("tasset", [-5.5, 10, -2.5], [1, 6, 5], tasset_paint(712, False))
    # Pernas (penduradas: o Avatar flutua)
    for side, sx in (("r", -1), ("l", 1)):
        leg = m.bone("leg_" + side, [sx * 2.5, 17, 0], "body")
        cubes = (
            ([-4.5, 10, -2], [4, 7, 4], under(720 + sx)),
            ([-5, 4, -2.5], [5, 6, 5], greave_paint(730 + sx)),
            ([-4, 2, -2], [3, 2, 3], foot_paint(740 + sx)),
        )
        for origin, size, paint in cubes:
            if sx < 0:
                m.cube(leg, origin, size, paint)
            else:
                m.mirror_cube(leg, origin, size, paint)
    # Fragmentos que orbitam o corpo
    m.bone("orbit", [0, 24, 0], "root")
    shard = glow_solid(0.95, 0.7)
    m.cube("orbit", [-1, 22, -13], [2, 4, 2], shard)
    m.cube("orbit", [10.3, 25, 5.5], [2, 4, 2], shard)
    m.cube("orbit", [-12.3, 19, 5.5], [2, 4, 2], shard)
    # Circulo de aura no chao (dois quadrados, um girado 45 graus)
    m.bone("aura", [0, 0, 0])
    ring = glow_solid(0.8, 0.85)
    m.cube("aura", [-10, 0, -10], [20, 1, 1], ring)
    m.cube("aura", [-10, 0, 9], [20, 1, 1], ring)
    m.cube("aura", [-10, 0, -9], [1, 1, 18], ring)
    m.cube("aura", [9, 0, -9], [1, 1, 18], ring)
    m.bone("aura_inner", [0, 0, 0], "aura", rotation=[0, 45, 0])
    inner = glow_solid(0.65, 0.85)
    m.cube("aura_inner", [-7, 0, -7], [14, 1, 1], inner)
    m.cube("aura_inner", [-7, 0, 6], [14, 1, 1], inner)
    m.cube("aura_inner", [-7, 0, -6], [1, 1, 12], inner)
    m.cube("aura_inner", [6, 0, -6], [1, 1, 12], inner)
    m.layout()
    m.save_geo((4, 4, 1.5))
    for el, pal in PALETTES.items():
        m.paint(pal)
        m.save_texture("trial_avatar_" + el)
    return m


# =====================================================================================
#  Animacoes
# =====================================================================================
def num(v):
    return round(v, 3)


def keys(points):
    return {f"{num(t)}": [num(a) for a in v] for t, v in sorted(points.items())}


def wave(length, fn, steps=12):
    pts = {}
    for i in range(steps + 1):
        ph = i / steps
        pts[length * ph] = fn(ph)
    return keys(pts)


def sinv(ph, amp, off=0.0, mult=1):
    return off + amp * math.sin(2 * math.pi * ph * mult)


def cosv(ph, amp, off=0.0, mult=1):
    return off + amp * math.cos(2 * math.pi * ph * mult)


def K(*pairs):
    d = {}
    for i in range(0, len(pairs), 2):
        d[pairs[i]] = pairs[i + 1]
    return keys(d)


class Anims:
    def __init__(self, ident):
        self.ident = ident
        self.anims = {}

    def add(self, name, length, loop, bones):
        a = {"animation_length": num(length), "bones": bones}
        if loop is True:
            a["loop"] = True
        elif loop == "hold":
            a["loop"] = "hold_on_last_frame"
        self.anims[f"animation.{self.ident}.{name}"] = a

    def save(self):
        write_json(os.path.join(ASSETS, "animations/entity", self.ident + ".animation.json"),
                   {"format_version": "1.8.0", "animations": self.anims})


def spin(length, axis, turns=1.0, steps=8):
    def f(p):
        v = [0, 0, 0]
        v[axis] = 360 * turns * p
        return v
    return wave(length, f, steps)


def pulse(length, lo, hi, mult=1):
    return wave(length, lambda p: [lo + (hi - lo) * (0.5 + 0.5 * math.sin(2 * math.pi * p * mult))] * 3)


def ambient(length, speed=1.0):
    """Canais que giram sempre: aureola, fragmentos, aura e o pulso do nucleo."""
    return {
        "crown": {"rotation": spin(length, 2, speed)},
        "orbit": {"rotation": spin(length, 1, speed)},
        "aura": {"rotation": spin(length, 1, -0.5 * speed)},
        "core": {"scale": pulse(length, 0.9, 1.2, max(1, int(round(length))))},
    }


def avatar_anims():
    a = Anims("trial_avatar")
    # ---------------- base ----------------
    idle = {
        "root": {"position": wave(3.0, lambda p: [0, sinv(p, 1.2, 2.5), 0])},
        "body": {"rotation": wave(3.0, lambda p: [sinv(p, 2), 0, 0])},
        "head": {"rotation": wave(3.0, lambda p: [sinv(p, 2, mult=1), cosv(p, 4), 0])},
        "arm_r": {"rotation": wave(3.0, lambda p: [sinv(p, 3, -4), 0, 9])},
        "arm_l": {"rotation": wave(3.0, lambda p: [sinv(p, 3, -4, 1), 0, -9])},
        "forearm_r": {"rotation": wave(3.0, lambda p: [sinv(p, 3, -12), 0, 0])},
        "forearm_l": {"rotation": wave(3.0, lambda p: [cosv(p, 3, -12), 0, 0])},
        "leg_r": {"rotation": wave(3.0, lambda p: [sinv(p, 3, 8), 0, 3])},
        "leg_l": {"rotation": wave(3.0, lambda p: [cosv(p, 3, 5), 0, -3])},
        "cape": {"rotation": wave(3.0, lambda p: [sinv(p, 3, 6), 0, cosv(p, 2)])},
    }
    idle.update(ambient(3.0, 1.0))
    a.add("idle", 3.0, True, idle)

    flt = {
        "root": {"position": wave(2.0, lambda p: [0, sinv(p, 1.5, 3.0), 0])},
        "body": {"rotation": wave(2.0, lambda p: [sinv(p, 2, 6), 0, 0])},
        "head": {"rotation": wave(2.0, lambda p: [-4, 0, 0])},
        "arm_r": {"rotation": wave(2.0, lambda p: [sinv(p, 4, -28), 0, 16])},
        "arm_l": {"rotation": wave(2.0, lambda p: [cosv(p, 4, -28), 0, -16])},
        "forearm_r": {"rotation": wave(2.0, lambda p: [-35, 0, 0])},
        "forearm_l": {"rotation": wave(2.0, lambda p: [-35, 0, 0])},
        "leg_r": {"rotation": wave(2.0, lambda p: [sinv(p, 4, 14), 0, 4])},
        "leg_l": {"rotation": wave(2.0, lambda p: [cosv(p, 4, 9), 0, -4])},
        "cape": {"rotation": wave(2.0, lambda p: [sinv(p, 4, 12), 0, 0])},
    }
    flt.update(ambient(2.0, 1.0))
    a.add("float", 2.0, True, flt)

    walk = {
        "root": {"position": wave(1.2, lambda p: [0, sinv(p, 1.0, 2.5, 2), 0])},
        "body": {"rotation": wave(1.2, lambda p: [14, sinv(p, 3), 0])},
        "head": {"rotation": wave(1.2, lambda p: [-10, 0, 0])},
        "arm_r": {"rotation": wave(1.2, lambda p: [sinv(p, 8, 22), 0, 12])},
        "arm_l": {"rotation": wave(1.2, lambda p: [sinv(p, -8, 22), 0, -12])},
        "forearm_r": {"rotation": wave(1.2, lambda p: [-15, 0, 0])},
        "forearm_l": {"rotation": wave(1.2, lambda p: [-15, 0, 0])},
        "leg_r": {"rotation": wave(1.2, lambda p: [sinv(p, 6, 28), 0, 3])},
        "leg_l": {"rotation": wave(1.2, lambda p: [sinv(p, -6, 28), 0, -3])},
        "cape": {"rotation": wave(1.2, lambda p: [sinv(p, 5, 28, 2), 0, 0])},
    }
    walk.update(ambient(1.2, 1.5))
    a.add("walk", 1.2, True, walk)

    # ---------------- apresentacao / fases / morte ----------------
    a.add("intro", 3.5, False, {
        "root": {"position": K(0, [0, -8, 0], 1.4, [0, -6, 0], 2.0, [0, 5, 0], 3.5, [0, 2, 0])},
        "body": {"rotation": K(0, [40, 0, 0], 1.4, [35, 0, 0], 2.0, [-18, 0, 0], 2.8, [-12, 0, 0], 3.5, [0, 0, 0])},
        "head": {"rotation": K(0, [45, 0, 0], 1.4, [40, 0, 0], 2.0, [-30, 0, 0], 2.8, [-20, 0, 0], 3.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [-70, 0, -45], 1.4, [-70, 0, -45], 2.0, [-25, 0, 95], 2.8, [-25, 0, 95], 3.5, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [-70, 0, 45], 1.4, [-70, 0, 45], 2.0, [-25, 0, -95], 2.8, [-25, 0, -95], 3.5, [0, 0, -9])},
        "leg_r": {"rotation": K(0, [-60, 0, 0], 1.4, [-55, 0, 0], 2.0, [20, 0, 4], 3.5, [8, 0, 3])},
        "leg_l": {"rotation": K(0, [-60, 0, 0], 1.4, [-55, 0, 0], 2.0, [20, 0, -4], 3.5, [5, 0, -3])},
        "core": {"scale": K(0, [0.3, 0.3, 0.3], 1.4, [0.6, 0.6, 0.6], 1.9, [2.2, 2.2, 2.2], 2.6, [1, 1, 1])},
        "crown": {"scale": K(0, [0, 0, 0], 1.6, [0, 0, 0], 2.0, [1.5, 1.5, 1.5], 2.6, [1, 1, 1]),
                  "rotation": K(0, [0, 0, 0], 3.5, [0, 0, 540])},
        "orbit": {"scale": K(0, [0, 0, 0], 1.8, [0, 0, 0], 2.3, [1.3, 1.3, 1.3], 3.0, [1, 1, 1]),
                  "rotation": K(0, [0, 0, 0], 3.5, [0, 720, 0])},
        "aura": {"scale": K(0, [0.2, 1, 0.2], 1.8, [0.5, 1, 0.5], 2.2, [1.4, 1, 1.4], 3.0, [1, 1, 1]),
                 "rotation": K(0, [0, 0, 0], 3.5, [0, -360, 0])},
    })
    a.add("phase", 2.5, False, {
        "root": {"position": K(0, [0, 2, 0], 0.8, [0, 0, 0], 1.0, [0, 7, 0], 2.0, [0, 7, 0], 2.5, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [28, 0, 0], 1.0, [-22, 0, 0], 2.0, [-18, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.8, [30, 0, 0], 1.0, [-35, 0, 0], 2.0, [-30, 0, 0], 2.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.8, [-55, 0, -40], 1.0, [-20, 0, 105], 2.0, [-20, 0, 100], 2.5, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.8, [-55, 0, 40], 1.0, [-20, 0, -105], 2.0, [-20, 0, -100], 2.5, [0, 0, -9])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [0.7, 0.7, 0.7], 1.0, [2.4, 2.4, 2.4], 2.0, [1.8, 1.8, 1.8], 2.5, [1, 1, 1])},
        "crown": {"rotation": K(0, [0, 0, 0], 2.5, [0, 0, 720]),
                  "scale": K(0, [1, 1, 1], 1.0, [1.7, 1.7, 1.7], 2.0, [1.5, 1.5, 1.5], 2.5, [1, 1, 1])},
        "orbit": {"rotation": K(0, [0, 0, 0], 2.5, [0, 1080, 0]),
                  "scale": K(0, [1, 1, 1], 1.0, [1.6, 1.6, 1.6], 2.5, [1, 1, 1])},
        "aura": {"scale": K(0, [1, 1, 1], 1.0, [1.6, 1, 1.6], 2.5, [1, 1, 1])},
    })
    a.add("death", 3.5, "hold", {
        "root": {"position": K(0, [0, 2, 0], 1.0, [0, 7, 0], 2.6, [0, 5, 0], 3.5, [0, -4, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-25, 0, 0], 2.6, [-20, 0, 6], 3.5, [40, 0, 10])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-40, 0, 0], 2.6, [-35, 0, 0], 3.5, [30, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.6, [-35, 0, 85], 2.6, [-40, 0, 90], 3.5, [10, 0, 20])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.6, [-35, 0, -85], 2.6, [-40, 0, -90], 3.5, [10, 0, -20])},
        "leg_r": {"rotation": K(0, [8, 0, 3], 1.0, [25, 0, 5], 3.5, [35, 0, 6])},
        "leg_l": {"rotation": K(0, [5, 0, -3], 1.0, [20, 0, -5], 3.5, [30, 0, -6])},
        "core": {"scale": K(0, [1, 1, 1], 2.4, [2.6, 2.6, 2.6], 2.8, [0, 0, 0])},
        "crown": {"scale": K(0, [1, 1, 1], 1.6, [1.3, 1.3, 1.3], 2.2, [0, 0, 0]),
                  "rotation": K(0, [0, 0, 0], 2.2, [0, 0, 900])},
        "orbit": {"scale": K(0, [1, 1, 1], 1.2, [1.8, 1.8, 1.8], 1.7, [0, 0, 0]),
                  "rotation": K(0, [0, 0, 0], 1.7, [0, 900, 0])},
        "aura": {"scale": K(0, [1, 1, 1], 2.0, [1.5, 1, 1.5], 2.8, [0, 1, 0])},
        "cape": {"rotation": K(0, [6, 0, 0], 1.0, [40, 0, 0], 3.5, [10, 0, 0])},
    })

    # ---------------- golpes ----------------
    a.add("cast", 1.3, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.35, [-4, 18, 0], 0.6, [6, -12, 0], 1.3, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.35, [-60, 0, 25], 0.6, [-95, 0, 2], 0.9, [-92, 0, 2], 1.3, [0, 0, 9])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.35, [-40, 0, 0], 0.6, [0, 0, 0], 1.3, [-10, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.35, [-25, 0, -30], 0.9, [-25, 0, -30], 1.3, [0, 0, -9])},
        "core": {"scale": K(0, [1, 1, 1], 0.55, [1.5, 1.5, 1.5], 0.7, [1, 1, 1])},
    })
    a.add("barrage", 1.8, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.35, [0, 15, 0], 0.65, [0, -15, 0], 1.0, [0, 15, 0], 1.3, [0, -10, 0], 1.8, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.35, [-95, 0, 4], 0.55, [-30, 0, 15], 1.0, [-95, 0, 4], 1.25, [-30, 0, 15], 1.8, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.35, [-30, 0, -15], 0.65, [-95, 0, -4], 0.9, [-30, 0, -15], 1.3, [-95, 0, -4], 1.8, [0, 0, -9])},
        "core": {"scale": pulse(1.8, 1.0, 1.5, 4)},
    })
    a.add("slam", 1.5, False, {
        "root": {"position": K(0, [0, 2, 0], 0.7, [0, 6, 0], 0.9, [0, -2, 0], 1.2, [0, -2, 0], 1.5, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.7, [-18, 0, 0], 0.9, [32, 0, 0], 1.2, [30, 0, 0], 1.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.7, [-15, 0, 0], 0.9, [10, 0, 0], 1.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.7, [-170, 0, 12], 0.9, [-45, 0, 12], 1.2, [-45, 0, 12], 1.5, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.7, [-170, 0, -12], 0.9, [-45, 0, -12], 1.2, [-45, 0, -12], 1.5, [0, 0, -9])},
        "leg_r": {"rotation": K(0, [8, 0, 3], 0.7, [-10, 0, 3], 0.9, [30, 0, 3], 1.5, [8, 0, 3])},
        "leg_l": {"rotation": K(0, [5, 0, -3], 0.7, [-10, 0, -3], 0.9, [30, 0, -3], 1.5, [5, 0, -3])},
    })
    a.add("dash", 2.0, False, {
        "root": {"position": K(0, [0, 2, 0], 0.8, [0, 0, 0], 1.0, [0, 3, 0], 1.5, [0, 3, 0], 2.0, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [-12, 0, 0], 0.9, [38, 0, 0], 1.5, [38, 0, 0], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.9, [-30, 0, 0], 1.5, [-30, 0, 0], 2.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.8, [35, 0, 25], 0.9, [-95, 0, 0], 1.5, [-95, 0, 0], 2.0, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.8, [35, 0, -25], 0.9, [40, 0, -20], 1.5, [40, 0, -20], 2.0, [0, 0, -9])},
        "leg_r": {"rotation": K(0, [8, 0, 3], 0.9, [45, 0, 3], 1.5, [45, 0, 3], 2.0, [8, 0, 3])},
        "leg_l": {"rotation": K(0, [5, 0, -3], 0.9, [50, 0, -3], 1.5, [50, 0, -3], 2.0, [5, 0, -3])},
        "cape": {"rotation": K(0, [6, 0, 0], 0.9, [55, 0, 0], 1.5, [55, 0, 0], 2.0, [6, 0, 0])},
    })
    chan = {
        "root": {"position": K(0, [0, 2, 0], 0.6, [0, 7, 0], 3.9, [0, 7, 0], 4.5, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-12, 0, 0], 3.9, [-12, 0, 0], 4.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-25, 0, 0], 3.9, [-25, 0, 0], 4.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.6, [-150, 0, 35], 2.2, [-140, 0, 45], 3.9, [-150, 0, 35], 4.5, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.6, [-150, 0, -35], 2.2, [-140, 0, -45], 3.9, [-150, 0, -35], 4.5, [0, 0, -9])},
        "leg_r": {"rotation": K(0, [8, 0, 3], 0.6, [15, 0, 8], 3.9, [15, 0, 8], 4.5, [8, 0, 3])},
        "leg_l": {"rotation": K(0, [5, 0, -3], 0.6, [15, 0, -8], 3.9, [15, 0, -8], 4.5, [5, 0, -3])},
        "core": {"scale": pulse(4.5, 1.2, 1.8, 6)},
        "crown": {"rotation": spin(4.5, 2, 3.0), "scale": K(0, [1, 1, 1], 0.6, [1.6, 1.6, 1.6], 3.9, [1.6, 1.6, 1.6], 4.5, [1, 1, 1])},
        "orbit": {"rotation": spin(4.5, 1, 4.0), "scale": K(0, [1, 1, 1], 0.6, [1.7, 1.7, 1.7], 3.9, [1.7, 1.7, 1.7], 4.5, [1, 1, 1])},
        "aura": {"rotation": spin(4.5, 1, -2.0), "scale": K(0, [1, 1, 1], 0.6, [1.8, 1, 1.8], 3.9, [1.8, 1, 1.8], 4.5, [1, 1, 1])},
    }
    a.add("channel", 4.5, False, chan)
    a.add("summon", 2.0, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [10, 0, 0], 1.0, [-14, 0, 0], 1.6, [-14, 0, 0], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-20, 0, 0], 1.6, [-20, 0, 0], 2.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.5, [-30, 0, 60], 1.0, [-125, 0, 40], 1.6, [-125, 0, 40], 2.0, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.5, [-30, 0, -60], 1.0, [-125, 0, -40], 1.6, [-125, 0, -40], 2.0, [0, 0, -9])},
        "core": {"scale": K(0, [1, 1, 1], 1.0, [1.7, 1.7, 1.7], 2.0, [1, 1, 1])},
        "orbit": {"rotation": spin(2.0, 1, 2.0)},
    })
    a.add("meteor", 2.8, False, {
        "root": {"position": K(0, [0, 2, 0], 0.5, [0, 5, 0], 2.0, [0, 5, 0], 2.8, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 2.0, [-15, 0, 0], 2.3, [15, 0, 0], 2.8, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-35, 0, 0], 2.0, [-35, 0, 0], 2.3, [5, 0, 0], 2.8, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.5, [-175, 0, 8], 2.0, [-175, 0, 8], 2.3, [-60, 0, 10], 2.8, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.5, [-175, 0, -8], 2.0, [-175, 0, -8], 2.3, [-60, 0, -10], 2.8, [0, 0, -9])},
        "crown": {"scale": K(0, [1, 1, 1], 0.5, [1.5, 1.5, 1.5], 2.0, [1.5, 1.5, 1.5], 2.8, [1, 1, 1]),
                  "rotation": spin(2.8, 2, 2.0)},
        "core": {"scale": pulse(2.8, 1.1, 1.6, 4)},
    })
    a.add("nova", 3.0, False, {
        "root": {"position": K(0, [0, 2, 0], 1.2, [0, 0, 0], 1.4, [0, 5, 0], 2.0, [0, 1, 0], 2.3, [0, 6, 0], 3.0, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.2, [26, 0, 0], 1.4, [-18, 0, 0], 2.1, [24, 0, 0], 2.3, [-20, 0, 0], 3.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 1.2, [-60, 0, -40], 1.4, [-20, 0, 110], 2.1, [-60, 0, -40], 2.3, [-20, 0, 115], 3.0, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 1.2, [-60, 0, 40], 1.4, [-20, 0, -110], 2.1, [-60, 0, 40], 2.3, [-20, 0, -115], 3.0, [0, 0, -9])},
        "core": {"scale": K(0, [1, 1, 1], 1.2, [0.7, 0.7, 0.7], 1.4, [2.2, 2.2, 2.2], 2.1, [0.7, 0.7, 0.7], 2.3, [2.4, 2.4, 2.4], 3.0, [1, 1, 1])},
        "aura": {"scale": K(0, [1, 1, 1], 1.4, [1.6, 1, 1.6], 2.1, [1, 1, 1], 2.3, [2.0, 1, 2.0], 3.0, [1, 1, 1])},
    })
    a.add("wave", 1.7, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.55, [0, 45, 0], 1.0, [8, -45, 0], 1.3, [8, -40, 0], 1.7, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.55, [-80, 0, 40], 1.0, [-85, 0, 10], 1.3, [-80, 0, 10], 1.7, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.55, [-20, 0, -45], 1.0, [-60, 0, -20], 1.7, [0, 0, -9])},
        "cape": {"rotation": K(0, [6, 0, 0], 1.0, [30, 0, -10], 1.7, [6, 0, 0])},
    })
    a.add("beam", 5.5, False, {
        "root": {"position": K(0, [0, 2, 0], 1.3, [0, 5, 0], 5.0, [0, 5, 0], 5.5, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [10, 0, 0], 1.3, [-6, 0, 0], 5.0, [-6, 0, 0], 5.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.3, [-15, 0, 0], 5.0, [-15, 0, 0], 5.5, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.6, [-90, 0, -12], 1.2, [-90, 0, -12], 1.4, [-10, 0, 92], 5.0, [-10, 0, 92], 5.5, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.6, [-90, 0, 12], 1.2, [-90, 0, 12], 1.4, [-10, 0, -92], 5.0, [-10, 0, -92], 5.5, [0, 0, -9])},
        "core": {"scale": K(0, [1, 1, 1], 1.2, [1.9, 1.9, 1.9], 5.0, [1.7, 1.7, 1.7], 5.5, [1, 1, 1])},
        "crown": {"rotation": spin(5.5, 2, 4.0)},
    })
    a.add("breath", 2.8, False, {
        "root": {"position": K(0, [0, 2, 0], 0.4, [0, 3, 0], 2.4, [0, 3, 0], 2.8, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-10, 0, 0], 0.6, [10, 0, 0], 2.4, [10, 0, 0], 2.8, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [12, 0, 0], 2.4, [12, 0, 0], 2.8, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.4, [-60, 0, 30], 0.6, [-88, 0, -12], 2.4, [-88, 0, -12], 2.8, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.4, [-60, 0, -30], 0.6, [-88, 0, 12], 2.4, [-88, 0, 12], 2.8, [0, 0, -9])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.6, [-10, 0, 0], 2.4, [-10, 0, 0], 2.8, [0, 0, 0])},
        "forearm_l": {"rotation": K(0, [0, 0, 0], 0.6, [-10, 0, 0], 2.4, [-10, 0, 0], 2.8, [0, 0, 0])},
        "core": {"scale": pulse(2.8, 1.2, 1.6, 8)},
        "cape": {"rotation": K(0, [6, 0, 0], 0.6, [35, 0, 0], 2.4, [35, 0, 0], 2.8, [6, 0, 0])},
    })
    a.add("pillar", 2.2, False, {
        "root": {"position": K(0, [0, 2, 0], 0.5, [0, 6, 0], 0.8, [0, -2, 0], 1.4, [0, -2, 0], 2.2, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 0.8, [42, 0, 0], 1.4, [40, 0, 0], 2.2, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.5, [-150, 0, 20], 0.8, [-30, 0, 25], 1.4, [-30, 0, 25], 2.2, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.5, [-150, 0, -20], 0.8, [-30, 0, -25], 1.4, [-30, 0, -25], 2.2, [0, 0, -9])},
        "leg_r": {"rotation": K(0, [8, 0, 3], 0.8, [-25, 0, 8], 1.4, [-25, 0, 8], 2.2, [8, 0, 3])},
        "leg_l": {"rotation": K(0, [5, 0, -3], 0.8, [-25, 0, -8], 1.4, [-25, 0, -8], 2.2, [5, 0, -3])},
        "aura": {"scale": K(0, [1, 1, 1], 0.8, [1.8, 1, 1.8], 1.4, [1.5, 1, 1.5], 2.2, [1, 1, 1])},
    })
    a.add("vortex", 2.3, False, {
        "root": {"position": K(0, [0, 2, 0], 0.3, [0, 5, 0], 1.6, [0, 5, 0], 2.3, [0, 2, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.3, [0, -30, 0], 1.6, [0, 720, 0], 2.3, [0, 720, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.3, [-10, 0, 80], 1.6, [-10, 0, 80], 2.3, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.3, [-10, 0, -80], 1.6, [-10, 0, -80], 2.3, [0, 0, -9])},
        "cape": {"rotation": K(0, [6, 0, 0], 0.3, [50, 0, 0], 1.6, [50, 0, 0], 2.3, [6, 0, 0])},
        "orbit": {"rotation": spin(2.3, 1, 5.0), "scale": K(0, [1, 1, 1], 0.3, [1.8, 1.8, 1.8], 1.6, [1.8, 1.8, 1.8], 2.3, [1, 1, 1])},
    })
    a.add("blink", 1.7, False, {
        "root": {"scale": K(0, [1, 1, 1], 0.35, [1, 1, 1], 0.45, [0.05, 1.4, 0.05], 0.55, [0.05, 1.4, 0.05], 0.65, [1, 1, 1])},
        "body": {"rotation": K(0, [0, 0, 0], 0.35, [22, 0, 0], 0.65, [10, 0, 0], 0.95, [-10, -25, 0], 1.15, [25, 30, 0], 1.7, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.35, [-30, 0, -20], 0.95, [-125, 0, 45], 1.15, [-30, 0, -25], 1.7, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.35, [-30, 0, 20], 0.95, [-40, 0, -50], 1.15, [-20, 0, -20], 1.7, [0, 0, -9])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.95, [-40, 0, 0], 1.15, [0, 0, 0], 1.7, [0, 0, 0])},
    })
    a.add("chains", 2.2, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-10, 0, 0], 0.8, [15, 0, 0], 1.1, [-18, 0, 0], 1.6, [-15, 0, 0], 2.2, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 9], 0.4, [-40, 0, 30], 0.8, [-95, 0, 0], 1.1, [30, 0, 22], 1.6, [25, 0, 20], 2.2, [0, 0, 9])},
        "arm_l": {"rotation": K(0, [0, 0, -9], 0.4, [-40, 0, -30], 0.8, [-95, 0, 0], 1.1, [30, 0, -22], 1.6, [25, 0, -20], 2.2, [0, 0, -9])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.8, [0, 0, 0], 1.1, [-60, 0, 0], 2.2, [0, 0, 0])},
        "forearm_l": {"rotation": K(0, [0, 0, 0], 0.8, [0, 0, 0], 1.1, [-60, 0, 0], 2.2, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [1.6, 1.6, 1.6], 1.2, [1, 1, 1])},
    })
    a.save()


# =====================================================================================
#  SENTINELA DA PROVACAO (textura neutra; o renderer tinge pelo elemento)
# =====================================================================================
SENT = {"armor": "#d9d6cf", "armor_hi": "#f1efe9", "trim": "#9c958a", "cloth": "#8f8a80",
        "under": "#4a4846", "mask": "#3a3836", "motif": "runes"}


def sent_plate(key, seed, rune=False):
    def p(fs, c, ctx):
        armor(key, seed)(fs, c, ctx)
        if rune:
            f = fs["front"]
            for yy in range(2, f.h - 1, 2):
                glow_px(f, f.w // 2, yy, 0.7)
    return p


def sent_helm(fs, c, ctx):
    armor("armor_hi", 820, trim=False)(fs, c, ctx)
    f = fs["front"]
    f.rect(1, 2, f.w - 2, 3, darken(C(ctx["under"]), 0.4))
    for xx in range(1, f.w - 1):
        glow_px(f, xx, 2, 1.0 if xx in (1, 2, f.w - 3, f.w - 2) else 0.55)
    f.rect(f.w // 2, 4, f.w // 2, f.h - 2, darken(C(ctx["armor"]), 0.35))


def sent_shield(fs, c, ctx):
    shade_faces(fs, ctx["armor"], 840, 0.06)
    f = fs["front"]
    f.border(C(ctx["trim"]))
    f.border(darken(C(ctx["trim"]), 0.3), "tb")
    cx, cy = f.w // 2, f.h // 2
    # Emblema: losango runico brilhante + cruz
    for yy in range(f.h):
        for xx in range(f.w):
            d = abs(xx + 0.5 - cx) + abs(yy + 0.5 - cy)
            if 2.5 <= d <= 3.5:
                glow_px(f, xx, yy, 0.9)
            elif d < 1.5:
                glow_px(f, xx, yy, 1.0)
    for yy in range(1, f.h - 1):
        if abs(yy + 0.5 - cy) > 4:
            glow_px(f, cx, yy, 0.45)
    b = fs["back"]
    b.fill(darken(C(ctx["under"]), 0.1))
    b.border(C(ctx["trim"]))


def sent_shaft(fs, c, ctx):
    shade_faces(fs, "#5b4a3a", 850, 0.06)
    for f in fs.values():
        for yy in range(0, f.h, 5):
            f.set(0, yy, C(ctx["trim"]))


def sent_blade(fs, c, ctx):
    shade_faces(fs, ctx["armor_hi"], 860, 0.04)
    for n in ("left", "right"):
        f = fs[n]
        for yy in range(f.h):
            glow_px(f, 0, yy, 0.6)


def build_sentinel():
    m = Model("trial_sentinel")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 14, 0], "root")
    m.cube("body", [-4.5, 12, -2.5], [9, 3, 5], sent_plate("trim", 800))
    m.cube("body", [-5.5, 15, -3.5], [11, 11, 7], sent_plate("armor", 801))
    m.cube("body", [-6, 18, -4.5], [12, 7, 1], sent_plate("armor_hi", 802))
    m.bone("core", [0, 21.5, -5], "body")
    m.cube("core", [-1.5, 20, -5.5], [3, 3, 1], crystal_core)
    m.bone("head", [0, 26, 0], "body")
    m.cube("head", [-3, 26, -3.5], [6, 7, 7], sent_helm)
    m.cube("head", [-0.5, 33, -3], [1, 3, 7], crest_paint)
    for side, sx in (("r", -1), ("l", 1)):
        arm = m.bone("arm_" + side, [sx * 7, 25, 0], "body")
        cubes = (
            ([-11, 23, -3.5], [5, 4, 7], sent_plate("armor_hi", 810 + sx, rune=True)),
            ([-10, 14, -2], [4, 9, 4], under(812 + sx)),
            ([-10.5, 11, -2.5], [5, 3, 5], sent_plate("armor", 814 + sx)),
        )
        for origin, size, paint in cubes:
            if sx < 0:
                m.cube(arm, origin, size, paint)
            else:
                m.mirror_cube(arm, origin, size, paint)
    # Alabarda na mao direita (osso proprio para girar no golpe)
    m.bone("weapon", [-8, 12.5, 0], "arm_r")
    m.cube("weapon", [-8.5, 0, -0.5], [1, 30, 1], sent_shaft)
    m.cube("weapon", [-9, 23, -4.5], [2, 7, 4], sent_blade)
    m.cube("weapon", [-8.5, 30, -0.5], [1, 3, 1], glow_solid(1.0, 0.8))
    # Escudo de torre no braco esquerdo, cobrindo a frente
    m.bone("shield", [8, 18, -3], "arm_l")
    m.cube("shield", [0, 5, -8], [10, 18, 1], sent_shield)
    for side, sx in (("r", -1), ("l", 1)):
        leg = m.bone("leg_" + side, [sx * 3, 14, 0], "root")
        cubes = (
            ([-5, 7, -2.5], [4, 7, 5], under(870 + sx)),
            ([-5.5, 0, -3], [5, 7, 6], sent_plate("armor", 872 + sx, rune=True)),
        )
        for origin, size, paint in cubes:
            if sx < 0:
                m.cube(leg, origin, size, paint)
            else:
                m.mirror_cube(leg, origin, size, paint)
    m.layout()
    m.paint(SENT)
    m.save_geo((3, 3, 1.2))
    m.save_texture("trial_sentinel")


def sentinel_anims():
    a = Anims("trial_sentinel")
    a.add("idle", 2.5, True, {
        "body": {"rotation": wave(2.5, lambda p: [sinv(p, 1.5), 0, 0]), "position": wave(2.5, lambda p: [0, sinv(p, 0.3), 0])},
        "head": {"rotation": wave(2.5, lambda p: [0, sinv(p, 6), 0])},
        "arm_r": {"rotation": wave(2.5, lambda p: [sinv(p, 2, -10), 0, 4])},
        "arm_l": {"rotation": wave(2.5, lambda p: [cosv(p, 2, -30), 0, -4])},
        "core": {"scale": pulse(2.5, 0.9, 1.15, 2)},
    })
    a.add("walk", 1.2, True, {
        "body": {"rotation": wave(1.2, lambda p: [6, sinv(p, 4), 0]), "position": wave(1.2, lambda p: [0, abs(sinv(p, 0.8)), 0])},
        "leg_r": {"rotation": wave(1.2, lambda p: [sinv(p, 26), 0, 0])},
        "leg_l": {"rotation": wave(1.2, lambda p: [sinv(p, -26), 0, 0])},
        "arm_r": {"rotation": wave(1.2, lambda p: [sinv(p, -14, -10), 0, 4])},
        "arm_l": {"rotation": wave(1.2, lambda p: [-30, 0, -4])},
        "core": {"scale": pulse(1.2, 0.9, 1.15, 1)},
    })
    a.add("swing", 1.0, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-5, 30, 0], 0.55, [12, -30, 0], 1.0, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-140, 0, 30], 0.55, [-40, 0, -20], 0.75, [-30, 0, -15], 1.0, [0, 0, 0])},
        "weapon": {"rotation": K(0, [0, 0, 0], 0.4, [-30, 0, 0], 0.55, [-70, 0, 0], 1.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-30, 0, 0], 0.4, [-35, 0, -10], 1.0, [-30, 0, 0])},
    })
    a.add("slam", 1.7, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [-20, 0, 0], 1.0, [35, 0, 0], 1.35, [30, 0, 0], 1.7, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.8, [-175, 0, 5], 1.0, [-50, 0, 5], 1.35, [-50, 0, 5], 1.7, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-30, 0, 0], 0.8, [-160, 0, -5], 1.0, [-50, 0, -5], 1.35, [-50, 0, -5], 1.7, [-30, 0, 0])},
        "weapon": {"rotation": K(0, [0, 0, 0], 0.8, [0, 0, 0], 1.0, [-80, 0, 0], 1.35, [-80, 0, 0], 1.7, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 1.0, [-25, 0, 0], 1.35, [-25, 0, 0], 1.7, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 1.0, [20, 0, 0], 1.35, [20, 0, 0], 1.7, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [1.6, 1.6, 1.6], 1.0, [1, 1, 1])},
    })
    a.add("charge", 2.4, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [25, 0, 0], 0.9, [30, 0, 0], 2.0, [30, 0, 0], 2.4, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-30, 0, 0], 0.8, [-70, 0, 15], 2.0, [-70, 0, 15], 2.4, [-30, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.8, [30, 0, 20], 2.0, [30, 0, 20], 2.4, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 0.9, [35, 0, 0], 1.1, [-35, 0, 0], 1.3, [35, 0, 0], 1.5, [-35, 0, 0], 1.7, [35, 0, 0], 1.9, [-35, 0, 0], 2.4, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 0.9, [-35, 0, 0], 1.1, [35, 0, 0], 1.3, [-35, 0, 0], 1.5, [35, 0, 0], 1.7, [-35, 0, 0], 1.9, [35, 0, 0], 2.4, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [1.7, 1.7, 1.7], 2.0, [1.4, 1.4, 1.4], 2.4, [1, 1, 1])},
    })
    a.add("stunned", 2.0, True, {
        "body": {"rotation": wave(2.0, lambda p: [22, sinv(p, 8), cosv(p, 6)])},
        "head": {"rotation": wave(2.0, lambda p: [25, sinv(p, 12, mult=2), 0])},
        "arm_r": {"rotation": wave(2.0, lambda p: [15, 0, 10])},
        "arm_l": {"rotation": wave(2.0, lambda p: [10, 0, -25])},
        "shield": {"rotation": wave(2.0, lambda p: [0, 0, -25])},
        "core": {"scale": pulse(2.0, 0.4, 0.7, 4)},
    })
    a.add("block", 0.6, False, {
        "arm_l": {"rotation": K(0, [-30, 0, 0], 0.1, [-75, 0, 20], 0.4, [-75, 0, 20], 0.6, [-30, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.1, [-8, 0, 0], 0.6, [0, 0, 0])},
        "shield": {"position": K(0, [0, 0, 0], 0.1, [0, 0, -1.5], 0.6, [0, 0, 0])},
    })
    a.add("spawn", 2.0, False, {
        "root": {"position": K(0, [0, -10, 0], 0.8, [0, -6, 0], 1.4, [0, 0, 0])},
        "body": {"rotation": K(0, [45, 0, 0], 0.8, [40, 0, 0], 1.4, [-10, 0, 0], 2.0, [0, 0, 0])},
        "head": {"rotation": K(0, [40, 0, 0], 1.2, [35, 0, 0], 1.6, [-15, 0, 0], 2.0, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [-80, 0, 0], 0.8, [-70, 0, 0], 1.4, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [10, 0, 0], 1.4, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [-20, 0, 0], 1.4, [-100, 0, 20], 1.7, [-100, 0, 20], 2.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-10, 0, 0], 1.4, [-60, 0, 0], 2.0, [-30, 0, 0])},
        "core": {"scale": K(0, [0, 0, 0], 1.2, [0.4, 0.4, 0.4], 1.6, [2.0, 2.0, 2.0], 2.0, [1, 1, 1])},
    })
    a.add("death", 2.5, "hold", {
        "root": {"position": K(0, [0, 0, 0], 1.2, [0, 0, 0], 2.5, [0, -8, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 1.6, [50, 0, 10], 2.5, [80, 0, 10])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-25, 0, 0], 2.5, [30, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.5, [-40, 0, 40], 2.5, [10, 0, 30])},
        "arm_l": {"rotation": K(0, [-30, 0, 0], 0.5, [-40, 0, -40], 2.5, [10, 0, -30])},
        "leg_r": {"rotation": K(0, [0, 0, 0], 1.2, [-70, 0, 0], 2.5, [-80, 0, 0])},
        "leg_l": {"rotation": K(0, [0, 0, 0], 1.2, [-60, 0, 0], 2.5, [-80, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 1.0, [2.0, 2.0, 2.0], 1.4, [0, 0, 0])},
    })
    a.save()


# =====================================================================================
#  Itens, loot e tags
# =====================================================================================
def build_items():
    for egg in ("trial_avatar_spawn_egg", "trial_sentinel_spawn_egg"):
        write_json(os.path.join(ASSETS, "models/item", egg + ".json"), {"parent": "minecraft:item/template_spawn_egg"})


def item_pool(name, lo=1, hi=1, conditions=None):
    entry = {"type": "minecraft:item", "name": name}
    if lo != 1 or hi != 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if conditions:
        pool["conditions"] = conditions
    return pool


def build_data():
    lt = os.path.join(DATA, "loot_table/entities")
    # Essencias do elemento saem pelo codigo (o elemento muda por instancia); aqui so o que e comum.
    write_json(os.path.join(lt, "trial_avatar.json"), {
        "type": "minecraft:entity",
        "pools": [
            item_pool("isekai_mahou:mana_crystal", 2, 4),
            item_pool("isekai_mahou:essence_arcane", 1, 2),
        ],
        "random_sequence": "isekai_mahou:entities/trial_avatar",
    })
    write_json(os.path.join(lt, "trial_sentinel.json"), {
        "type": "minecraft:entity",
        "pools": [
            item_pool("isekai_mahou:mana_crystal", 0, 1),
            item_pool("minecraft:iron_ingot", 1, 2, [{"condition": "minecraft:killed_by_player"}]),
        ],
        "random_sequence": "isekai_mahou:entities/trial_sentinel",
    })
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"values": ["isekai_mahou:trial_avatar"]})


if __name__ == "__main__":
    build_avatar()
    avatar_anims()
    build_sentinel()
    sentinel_anims()
    build_items()
    build_data()
    print("bosses_d: assets gerados")
