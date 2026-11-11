"""
Gerador de assets do modulo legends_c (Tecnicas Malditas):
  - malevolent_shrine: o SANTUARIO MALIGNO (GeckoLib). Modelado em 1/4 da escala (o renderer desenha x4: ~11 blocos de
    altura). Templo de madeira escura sobre um monte de cranios empilhados, pilares, BOCARRA com dentes na fachada
    (mandibulas animadas), bocas menores nas laterais, telhado em pagode de tres andares com pontas viradas para cima,
    CHIFRES curvos de osso, olhos vermelhos no topo, talismas pendurados e veu vermelho (glowmask).
    Animacoes: open (ergue-se do chao, abre a boca, chifres crescem), idle (respira), close (afunda).
  - textures/entity/legends_c/swirl.png: redemoinho em tons de cinza (cascas energySwirl, tingidas pela cor).
  - textures/mob_effect/mugen.png: icone do efeito Infinito (18x18).

Rodar a partir da raiz do projeto:  py -I tools/gen/legends_c.py

Convencoes (Bedrock/GeckoLib): frente do modelo = -Z; y para cima a partir dos pes. Rotacao X positiva inclina para a
frente (a mandibula de baixo cai com X positivo; a de cima sobe com X negativo). Z positivo dobra para +X um osso que
aponta para cima. Ossos animados NUNCA tem rotacao inicial no .geo.
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
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Modelo (box UV) e pintura
# =====================================================================================
class Face:
    def __init__(self, model, name, x, y, w, h):
        self.m, self.name, self.x, self.y, self.w, self.h = model, name, x, y, w, h

    def set(self, fx, fy, c, glow=None):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, C(c))
            self.m.glow.set(self.x + fx, self.y + fy, C(glow) if glow is not None else CLEAR)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def is_glow(self, fx, fy):
        return self.m.glow.get(self.x + fx, self.y + fy)[3] > 0

    def rect(self, x0, y0, x1, y1, c, glow=None):
        for yy in range(max(0, y0), min(self.h - 1, y1) + 1):
            for xx in range(max(0, x0), min(self.w - 1, x1) + 1):
                self.set(xx, yy, c, glow)

    def fill(self, c, glow=None):
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

    def border(self, c):
        for xx in range(self.w):
            self.set(xx, 0, c)
            self.set(xx, self.h - 1, c)
        for yy in range(self.h):
            self.set(0, yy, c)
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
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "inflate": inflate})

    def mirror(self, bone, origin, size, paint):
        self.cube(bone, [-(origin[0] + size[0]), origin[1], origin[2]], size, paint)

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
            "top": Face(self, "top", u + d, v, w, d),
            "bottom": Face(self, "bottom", u + d + w, v, w, d),
            "right": Face(self, "right", u, v + d, d, h),
            "front": Face(self, "front", u + d, v + d, w, h),
            "left": Face(self, "left", u + d + w, v + d, d, h),
            "back": Face(self, "back", u + 2 * d + w, v + d, w, h),
        }

    def paint(self):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            c["paint"](self.faces_of(c), c, 1000 + i * 17)

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
                "description": {
                    "identifier": "geometry." + self.ident,
                    "texture_width": self.tw, "texture_height": self.th,
                    "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                    "visible_bounds_offset": [0, bounds[2], 0],
                },
                "bones": bones,
            }],
        })
        self.tex.save(os.path.join(ASSETS, "textures/entity", self.ident + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", self.ident + "_glowmask.png"))


# =====================================================================================
#  Paleta e pinceis do santuario
# =====================================================================================
WOOD = "2b1712"
WOOD_DK = "1a0d0a"
WOOD_HI = "4a2a1e"
LACQUER = "8e1118"
LACQUER_DK = "4f080c"
GOLD = "c9a03c"
TILE = "231a22"
TILE_HI = "3a2c38"
BONE = "d8cdb0"
BONE_DK = "9c8f70"
BONE_SH = "6b604a"
TOOTH = "efe6cf"
MAW = "2a0306"
GLOW_RED = "ff2a1e"
GLOW_ORANGE = "ff7a2a"
PAPER = "d9c9a0"


def shade(fs, base, seed, noise=0.05, grad=0.18):
    for name, f in fs.items():
        c = C(base)
        if name == "top":
            c = lighten(c, 0.10)
        elif name == "bottom":
            c = darken(c, 0.35)
        elif name in ("left", "right"):
            c = darken(c, 0.08)
        elif name == "back":
            c = darken(c, 0.12)
        f.fill(c)
        if name in ("front", "back", "left", "right") and f.h > 2 and grad:
            hh = f.h
            f.tint(lambda p, xx, yy: darken(p, grad * yy / max(1, hh - 1)))
        if noise:
            f.noise(noise, seed + len(name) * 13)


def wood(fs, c, seed):
    shade(fs, WOOD, seed, 0.07)
    rnd = random.Random(seed)
    for n, f in fs.items():
        if n in ("top", "bottom"):
            continue
        # Veios verticais e tabuas
        for xx in range(0, f.w, 3):
            for yy in range(f.h):
                if rnd.random() < 0.7:
                    f.set(xx, yy, darken(f.get(xx, yy), 0.25))
        for xx in range(1, f.w, 7):
            for yy in range(f.h):
                if rnd.random() < 0.5:
                    f.set(xx, yy, C(WOOD_HI))
        f.border(C(WOOD_DK))


def pillar(fs, c, seed):
    shade(fs, LACQUER, seed, 0.06, 0.25)
    for n, f in fs.items():
        if n in ("top", "bottom"):
            continue
        f.border(C(LACQUER_DK))
        # Faixas douradas e de madeira escura
        for yy in (1, f.h - 2, f.h // 2):
            for xx in range(f.w):
                f.set(xx, yy, C(GOLD))
        for yy in range(f.h - 1, f.h):
            for xx in range(f.w):
                f.set(xx, yy, C(WOOD_DK))


def lacquer_beam(fs, c, seed):
    shade(fs, LACQUER, seed, 0.05, 0.1)
    rnd = random.Random(seed)
    for n, f in fs.items():
        f.border(C(LACQUER_DK))
        if n in ("front", "back", "left", "right") and f.h >= 2:
            for xx in range(2, f.w - 2, 4):
                f.set(xx, 0, C(GOLD))
            # Runas malditas brilhando no lintel
            for xx in range(3, f.w - 3, 6):
                if rnd.random() < 0.8:
                    f.set(xx, f.h // 2, C(GLOW_RED), C(GLOW_RED))


def tiles(fs, c, seed):
    shade(fs, TILE, seed, 0.06, 0.0)
    for n, f in fs.items():
        if n == "top":
            # Fileiras de telhas
            for yy in range(0, f.h, 2):
                for xx in range(f.w):
                    f.set(xx, yy, darken(f.get(xx, yy), 0.3))
            for xx in range(0, f.w, 3):
                for yy in range(f.h):
                    if yy % 2 == 1:
                        f.set(xx, yy, C(TILE_HI))
        elif n != "bottom":
            for xx in range(f.w):
                f.set(xx, 0, C(TILE_HI))
                f.set(xx, f.h - 1, C(GOLD) if xx % 4 == 0 else darken(C(TILE), 0.4))
        else:
            f.fill(C(WOOD_DK))
            for xx in range(0, f.w, 2):
                for yy in range(f.h):
                    f.set(xx, yy, C(WOOD))


def tile_tip(fs, c, seed):
    shade(fs, TILE_HI, seed, 0.05, 0)
    for f in fs.values():
        f.border(C(GOLD))


def skull_icon(f, x, y, glow_eyes=False):
    """Cranio 4x4 numa face: testa, orbitas escuras, dentes."""
    rows = [".bb.", "bdbd", "bbbb", ".tt."]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "b":
                f.set(x + i, y + j, C(BONE))
            elif ch == "d":
                if glow_eyes:
                    f.set(x + i, y + j, C(GLOW_RED), C(GLOW_RED))
                else:
                    f.set(x + i, y + j, C("1a0f0a"))
            elif ch == "t":
                f.set(x + i, y + j, C(BONE_DK))


def skull_pile(fs, c, seed):
    """Monte de cranios empilhados: osso sombreado com cranios em fileiras desencontradas."""
    shade(fs, BONE_SH, seed, 0.08, 0.2)
    rnd = random.Random(seed)
    for n, f in fs.items():
        if n == "bottom":
            continue
        step = 5
        for yy in range(0, f.h, 4):
            off = (yy // 4) % 2 * 2
            for xx in range(off, f.w - 3, step):
                if rnd.random() < 0.88:
                    skull_icon(f, xx, yy, glow_eyes=rnd.random() < 0.06)
        f.noise(0.06, seed + 99)


def skull_block(fs, c, seed):
    """Um cranio grande (3x3x3): frente com orbitas e dentes."""
    shade(fs, BONE, seed, 0.05, 0.15)
    f = fs["front"]
    f.set(0, 1, C("1a0f0a"))
    f.set(2, 1, C("1a0f0a"))
    f.set(1, 2, C(BONE_DK))
    f.set(0, 2, C(TOOTH))
    f.set(2, 2, C(TOOTH))
    rnd = random.Random(seed)
    if rnd.random() < 0.4:
        f.set(0, 1, C(GLOW_RED), C(GLOW_RED))
        f.set(2, 1, C(GLOW_RED), C(GLOW_RED))


def bone(fs, c, seed):
    shade(fs, BONE, seed, 0.06, 0.25)
    rnd = random.Random(seed)
    for n, f in fs.items():
        if n in ("top", "bottom"):
            continue
        # Aneis do chifre
        for yy in range(1, f.h, 2):
            for xx in range(f.w):
                f.set(xx, yy, darken(f.get(xx, yy), 0.18))
        if rnd.random() < 0.5 and f.w > 1:
            f.set(rnd.randrange(f.w), rnd.randrange(f.h), C(BONE_SH))


def horn_tip(fs, c, seed):
    shade(fs, "f2ead6", seed, 0.04, 0.3)
    fs["top"].fill(C("fff8e8"))


def tooth(fs, c, seed):
    shade(fs, TOOTH, seed, 0.03, 0.35)
    for n in ("front", "back", "left", "right"):
        f = fs[n]
        for xx in range(f.w):
            f.set(xx, f.h - 1, C("fffaf0"))


def lip(fs, c, seed):
    """Labio/gengiva da bocarra: carne escura com borda de osso."""
    shade(fs, "5a0d12", seed, 0.08, 0.2)
    for n, f in fs.items():
        f.border(C("2e0508"))


def maw(fs, c, seed):
    """Interior da boca: vermelho profundo brilhando (garganta acesa)."""
    rnd = random.Random(seed)
    for n, f in fs.items():
        f.fill(C(MAW))
        if f.w >= 3 and f.h >= 3:
            cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
            for yy in range(f.h):
                for xx in range(f.w):
                    d = math.hypot((xx - cx) / max(1, cx), (yy - cy) / max(1, cy))
                    if d < 0.85:
                        col = mix(C(GLOW_ORANGE), C(GLOW_RED), min(1, d * 1.3))
                        if rnd.random() < 0.15:
                            col = lighten(col, 0.25)
                        f.set(xx, yy, col, col)


def eye(fs, c, seed):
    for n, f in fs.items():
        f.fill(C(GLOW_RED), C(GLOW_RED))
    f = fs["front"]
    f.set(f.w // 2, 0, C("ffd0a0"), C("ffd0a0"))


def talisman(fs, c, seed):
    """Talisma de papel com runa vermelha que brilha."""
    shade(fs, PAPER, seed, 0.06, 0.25)
    rnd = random.Random(seed)
    for n in ("front", "back"):
        f = fs[n]
        for yy in range(1, f.h - 1):
            if rnd.random() < 0.75:
                xx = rnd.randrange(max(1, f.w))
                f.set(xx, yy, C(GLOW_RED), C(GLOW_RED))
        f.set(0, 0, C(LACQUER_DK))


def veil(fs, c, seed):
    """Veu vermelho translucido brilhando (todo no glowmask)."""
    rnd = random.Random(seed)
    for n, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                k = 0.55 + 0.45 * (1 - yy / max(1, f.h - 1))
                col = mix(C(LACQUER_DK), C(GLOW_RED), k * (0.7 + 0.3 * rnd.random()))
                f.set(xx, yy, col, col)


def gold(fs, c, seed):
    shade(fs, GOLD, seed, 0.06, 0.25)


def stone_step(fs, c, seed):
    shade(fs, "4d4448", seed, 0.08, 0.15)
    for f in fs.values():
        f.border(C("201a1c"))


# =====================================================================================
#  Santuario
# =====================================================================================
def shrine():
    m = Model("malevolent_shrine")
    m.bone("root", [0, 0, 0])

    # ---------------- base de cranios ----------------
    m.bone("base", [0, 0, 0], "root")
    m.cube("base", [-20, 0, -18], [40, 4, 36], skull_pile)
    m.cube("base", [-16, 4, -14], [32, 3, 28], skull_pile)
    m.cube("base", [-6, 0, -23], [12, 2, 5], stone_step)
    m.cube("base", [-5, 2, -21], [10, 2, 3], stone_step)
    # Pilhas de cranios grandes nos cantos da frente
    m.bone("skulls", [0, 0, 0], "root")
    for (x, y, z) in ((-19, 4, -18), (-16, 4, -18), (-19, 4, -15), (-17.5, 7, -16.5),
                      (-19, 4, 13), (-16, 4, 15), (-17.5, 7, 13.5)):
        m.cube("skulls", [x, y, z], [3, 3, 3], skull_block)
        m.cube("skulls", [-(x + 3), y, z], [3, 3, 3], skull_block)

    # ---------------- salao ----------------
    m.bone("hall", [0, 7, 0], "root")
    m.cube("hall", [-11, 7, -9], [22, 12, 18], wood)
    # Bocas menores nas laterais (com dentes)
    for side in (-1, 1):
        x = 10.6 if side > 0 else -11.6
        m.cube("hall", [x, 10, -5], [1, 5, 10], maw)
        for k in range(5):
            m.cube("hall", [x + (0.3 if side > 0 else -0.3), 14, -4.5 + k * 2], [1, 1, 1], tooth)
            m.cube("hall", [x + (0.3 if side > 0 else -0.3), 10, -3.5 + k * 2], [1, 1, 1], tooth)
    # Pilares (frente e fundo)
    m.cube("hall", [-14, 7, -14], [3, 13, 3], pillar)
    m.cube("hall", [11, 7, -14], [3, 13, 3], pillar)
    m.cube("hall", [-14, 7, 10], [3, 13, 3], pillar)
    m.cube("hall", [11, 7, 10], [3, 13, 3], pillar)
    # Lintel vermelho
    m.cube("hall", [-15, 19, -15], [30, 2, 28], lacquer_beam)

    # ---------------- bocarra da fachada ----------------
    m.cube("hall", [-8, 9, -10], [16, 8, 1], maw)
    m.bone("jaw", [0, 10, -9], "hall")
    m.cube("jaw", [-9, 7, -12], [18, 3, 3], lip)
    for k in range(8):
        m.cube("jaw", [-7.5 + k * 2, 10, -11.5], [1, 2, 1], tooth)
    m.bone("jaw_upper", [0, 16, -9], "hall")
    m.cube("jaw_upper", [-9, 16, -12], [18, 3, 3], lip)
    for k in range(8):
        m.cube("jaw_upper", [-7 + k * 2, 14, -11.5], [1, 2, 1], tooth)
    # Presas maiores nos cantos
    m.cube("jaw_upper", [-8, 12, -11.5], [1, 4, 1], tooth)
    m.cube("jaw_upper", [7, 12, -11.5], [1, 4, 1], tooth)

    # ---------------- talismas pendurados ----------------
    m.bone("talismans", [0, 19, -14], "hall")
    for x in (-13, -10.5, 9.5, 12):
        m.cube("talismans", [x, 13, -14.6], [1, 6, 0], talisman)
    # Veu vermelho entre os pilares do fundo e nas laterais
    # Veu vermelho: tiras penduradas (com vaos: as bocas laterais aparecem entre elas)
    m.bone("veil", [0, 19, 0], "hall")
    rnd = random.Random(5)
    for x in range(-11, 11, 3):
        drop = rnd.randint(0, 3)
        m.cube("veil", [x, 9 + drop, 11.5], [2, 10 - drop, 0], veil)
    for z in (-12, -8, 6, 9):
        drop = rnd.randint(0, 3)
        m.cube("veil", [-13.5, 9 + drop, z], [0, 10 - drop, 2], veil)
        m.cube("veil", [13.5, 9 + drop, z], [0, 10 - drop, 2], veil)

    # ---------------- telhado em pagode ----------------
    m.bone("roof", [0, 21, 0], "root")
    m.cube("roof", [-18, 21, -17], [36, 3, 34], tiles)
    for (x, z) in ((-20, -19), (17, -19), (-20, 16), (17, 16)):
        m.cube("roof", [x, 23, z], [3, 2, 3], tile_tip)
        m.cube("roof", [x + (0 if x < 0 else 1), 25, z + (0 if z < 0 else 1)], [2, 1, 2], gold)
    m.cube("roof", [-9, 24, -8], [18, 5, 16], wood)
    m.cube("roof", [-10, 26, -9], [20, 1, 18], lacquer_beam)
    m.cube("roof", [-13, 29, -12], [26, 3, 24], tiles)
    for (x, z) in ((-15, -14), (12, -14), (-15, 11), (12, 11)):
        m.cube("roof", [x, 31, z], [3, 2, 3], tile_tip)
    m.cube("roof", [-6, 32, -5], [12, 4, 10], wood)
    m.cube("roof", [-9, 36, -8], [18, 2, 16], tiles)
    m.cube("roof", [-1, 38, -7], [2, 2, 14], gold)
    m.cube("roof", [-1, 40, -1], [2, 5, 2], gold)
    m.cube("roof", [-2, 45, -2], [4, 1, 4], gold)

    # Olhos vermelhos no andar de cima
    m.bone("eyes", [0, 34, -5], "roof")
    m.cube("eyes", [-4, 33, -5.6], [2, 1, 1], eye)
    m.cube("eyes", [2, 33, -5.6], [2, 1, 1], eye)

    # ---------------- chifres curvos ----------------
    for side, name in ((1, "horn_l"), (-1, "horn_r")):
        bx = 12.5 * side
        m.bone(name, [bx, 31, 0], "roof")
        m.cube(name, [bx - 1.5, 31, -1.5], [3, 6, 3], bone)
        segs = [(6, 3, 2, 22), (6, 2, 2, 24), (5, 2, 1, 26), (3, 1, 1, 28)]
        parent = name
        y = 37
        for i, (h, w, d, rot) in enumerate(segs):
            seg = f"{name}_{i}"
            m.bone(seg, [bx, y, 0], parent, rotation=[0, 0, rot * side])
            paint = horn_tip if i == len(segs) - 1 else bone
            m.cube(seg, [bx - w / 2, y, -d / 2], [w, h, d], paint)
            parent = seg
            y += h

    m.layout()
    m.paint()
    m.save((4, 4, 1.5))
    return m


def shrine_anims():
    def num(v):
        return round(v, 3)

    def keys(points):
        return {f"{num(t)}": [num(a) for a in v] for t, v in sorted(points.items())}

    anims = {}
    # Ergue-se do chao tremendo, abre a boca, os chifres crescem
    shake = {}
    for i in range(0, 16):
        t = i * 0.1
        shake[t] = [0, 0, (1.4 if i % 2 == 0 else -1.4) * (1 - t / 1.6)]
    shake[1.6] = [0, 0, 0]
    anims["animation.malevolent_shrine.open"] = {
        "animation_length": 2.0,
        "bones": {
            "root": {
                "position": keys({0.0: [0, -46, 0], 0.5: [0, -30, 0], 1.0: [0, -10, 0], 1.35: [0, 1.5, 0], 1.6: [0, 0, 0]}),
                "rotation": keys(shake),
            },
            "jaw": {"rotation": keys({0.0: [0, 0, 0], 1.2: [0, 0, 0], 1.6: [34, 0, 0], 2.0: [22, 0, 0]})},
            "jaw_upper": {"rotation": keys({0.0: [0, 0, 0], 1.2: [0, 0, 0], 1.6: [-10, 0, 0], 2.0: [-5, 0, 0]})},
            "horn_l": {"scale": keys({0.0: [0.1, 0.1, 0.1], 1.1: [0.3, 0.3, 0.3], 1.7: [1.1, 1.1, 1.1], 2.0: [1, 1, 1]})},
            "horn_r": {"scale": keys({0.0: [0.1, 0.1, 0.1], 1.1: [0.3, 0.3, 0.3], 1.7: [1.1, 1.1, 1.1], 2.0: [1, 1, 1]})},
            "eyes": {"scale": keys({0.0: [0, 0, 0], 1.5: [0, 0, 0], 1.7: [1.6, 1.6, 1.6], 2.0: [1, 1, 1]})},
            "veil": {"scale": keys({0.0: [1, 0.2, 1], 1.4: [1, 0.4, 1], 2.0: [1, 1, 1]})},
        },
    }
    # Respira: boca abre e fecha devagar, talismas balancam, olhos pulsam
    idle_jaw, idle_up, idle_tal, idle_eye, idle_horn, idle_veil = {}, {}, {}, {}, {}, {}
    for i in range(0, 17):
        p = i / 16
        t = 4.0 * p
        s = math.sin(2 * math.pi * p)
        idle_jaw[t] = [24 + 6 * s, 0, 0]
        idle_up[t] = [-5 - 2.5 * s, 0, 0]
        idle_tal[t] = [5 * math.sin(4 * math.pi * p), 0, 3 * math.cos(2 * math.pi * p)]
        e = 1 + 0.25 * max(0, math.sin(6 * math.pi * p))
        idle_eye[t] = [e, e, e]
        h = 1 + 0.03 * s
        idle_horn[t] = [h, h, h]
        v = 1 + 0.06 * math.sin(4 * math.pi * p)
        idle_veil[t] = [1, v, 1]
    anims["animation.malevolent_shrine.idle"] = {
        "animation_length": 4.0,
        "loop": True,
        "bones": {
            "jaw": {"rotation": keys(idle_jaw)},
            "jaw_upper": {"rotation": keys(idle_up)},
            "talismans": {"rotation": keys(idle_tal)},
            "eyes": {"scale": keys(idle_eye)},
            "horn_l": {"scale": keys(idle_horn)},
            "horn_r": {"scale": keys(idle_horn)},
            "veil": {"scale": keys(idle_veil)},
        },
    }
    # Fecha a boca e afunda no chao
    shake2 = {}
    for i in range(0, 15):
        t = i * 0.1
        shake2[t] = [0, 0, (1.2 if i % 2 == 0 else -1.2)]
    shake2[1.5] = [0, 0, 0]
    anims["animation.malevolent_shrine.close"] = {
        "animation_length": 1.5,
        "loop": "hold_on_last_frame",
        "bones": {
            "root": {
                "position": keys({0.0: [0, 0, 0], 0.3: [0, 1, 0], 0.9: [0, -20, 0], 1.5: [0, -50, 0]}),
                "rotation": keys(shake2),
            },
            "jaw": {"rotation": keys({0.0: [24, 0, 0], 0.3: [0, 0, 0]})},
            "jaw_upper": {"rotation": keys({0.0: [-5, 0, 0], 0.3: [0, 0, 0]})},
            "eyes": {"scale": keys({0.0: [1, 1, 1], 0.4: [0, 0, 0]})},
        },
    }
    write_json(os.path.join(ASSETS, "animations/entity", "malevolent_shrine.animation.json"),
               {"format_version": "1.8.0", "animations": anims})


# =====================================================================================
#  Texturas soltas
# =====================================================================================
def swirl_texture():
    """Redemoinho cinza 64x64 (repete na horizontal) para as cascas energySwirl."""
    n = 64
    c = Canvas(n, n)
    rnd = random.Random(77)
    blobs = [(rnd.random() * n, rnd.random() * n, 3 + rnd.random() * 9, rnd.random()) for _ in range(26)]
    for y in range(n):
        for x in range(n):
            v = 0.0
            for (bx, by, r, w) in blobs:
                dx = min(abs(x - bx), n - abs(x - bx))
                dy = min(abs(y - by), n - abs(y - by))
                d = math.hypot(dx, dy)
                ang = math.atan2(dy, dx)
                v += w * max(0.0, 1 - d / (r * 2.2)) * (0.6 + 0.4 * math.sin(ang * 3 + d * 0.8))
            v += 0.25 * math.sin((x + y * 0.5) * 2 * math.pi / n * 3)
            v = max(0.0, min(1.0, v * 0.9)) ** 1.8
            g = int(255 * v)
            c.set(x, y, (g, g, g, 255))
    c.save(os.path.join(ASSETS, "textures/entity/legends_c", "swirl.png"))


def mugen_icon():
    """Icone 18x18 do efeito Infinito: esfera azul-clara com o simbolo do infinito."""
    c = Canvas(18, 18)
    c.circle(9, 9, 8, C("1d3f8f"))
    c.circle(9, 9, 7, C("3d74e0"))
    c.shade_sphere(9, 9, 7, 0.5, 0.35)
    # Simbolo do infinito (lemniscata)
    for i in range(80):
        t = i / 80 * 2 * math.pi
        den = 1 + math.sin(t) ** 2
        x = 9 + 5.6 * math.cos(t) / den
        y = 9 + 5.6 * math.sin(t) * math.cos(t) / den
        c.set(int(round(x - 0.5)), int(round(y - 0.5)), C("eaf6ff"))
    c.set(4, 4, C("ffffff"))
    c.set(5, 4, C("cfe8ff"))
    c.outline(C("0d1f4a"))
    c.save(os.path.join(ASSETS, "textures/mob_effect", "mugen.png"))


if __name__ == "__main__":
    m = shrine()
    shrine_anims()
    swirl_texture()
    mugen_icon()
    print("legends_c: santuario", m.tw, "x", m.th, "cubos", len(m.cubes))
