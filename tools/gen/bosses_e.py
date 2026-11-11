"""
Gerador de assets do modulo bosses_e (Dragao Primordial, o chefe final da v0.9):
  - primordial_dragon.geo.json: dragao com ~23 blocos de envergadura no jogo (o modelo e desenhado a 1/2,5 da escala
    e o renderer aumenta 2,5x): cabeca com chifres em degraus, mandibula, pescoco em 2 partes, tronco, quadril,
    4 patas com garras, cauda em 4 segmentos com lamina, asas em 3 segmentos com membranas (borda recortada) e o
    Nucleo Primordial no peito.
  - 5 texturas, uma por FASE (fire, water, earth, storm, twilight), cada uma com o seu _glowmask BRANCO/cinza
    (o renderer tinge pelo elemento da fase e faz a transicao entre as escamas de uma fase e da outra).
  - primordial_shade: textura escura dos Clones de Sombra (mesmo modelo).
  - animacoes (.animation.json) do dragao (os clones usam as mesmas).
  - item primordial_dragon_heart (16x16), modelo do ovo, loot table e tag c:bosses (merge).
  - v1.0: animacoes novas do dragao (gaze, siphon, chains, curse, swap) e a ANCORA DAS CORRENTES
    (primordial_chain.geo.json + textura/_glowmask + animacoes idle/rise + ovo).

Rodar a partir da raiz do projeto:  py -I tools/gen/bosses_e.py

Convencoes (Bedrock/GeckoLib): frente do modelo = -Z; -X = lado DIREITO da criatura; y para cima a partir dos pes.
Z positivo ergue a asa/braco DIREITO (-X) para fora/cima; Z negativo ergue o ESQUERDO (+X); X positivo inclina
tronco/cabeca/mandibula para a frente (para baixo). Ossos animados NAO tem rotacao inicial no .geo.
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
MERGE = os.path.join(ROOT, "src/main/merge/bosses_e")
CLEAR = (0, 0, 0, 0)
IDENT = "primordial_dragon"


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
#  Faces de um cubo (box UV) e modelo
# =====================================================================================
class Face:
    def __init__(self, model, name, x, y, w, h, cube):
        self.m, self.name, self.x, self.y, self.w, self.h, self.cube = model, name, x, y, w, h, cube

    def set(self, fx, fy, c, glow=None):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, C(c))
            self.m.glow.set(self.x + fx, self.y + fy, grey(glow) if glow is not None else CLEAR)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def clear(self, fx, fy):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, CLEAR)
            self.m.glow.set(self.x + fx, self.y + fy, CLEAR)

    def fill(self, c):
        for yy in range(self.h):
            for xx in range(self.w):
                self.set(xx, yy, c)


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

    def cube(self, bone, origin, size, paint, side=0, inflate=0.0):
        """side: -1 = lado direito (-X), +1 = esquerdo (+X), 0 = centro (usado na pintura do Crepusculo)."""
        c = {"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size], "paint": paint,
             "side": side, "inflate": inflate}
        self.cubes.append(c)
        return c

    def pair(self, bone_r, bone_l, origin, size, paint, inflate=0.0):
        """Cubo do lado direito (-X) e o espelho no esquerdo (+X), cada um com a sua area na textura."""
        self.cube(bone_r, origin, size, paint, -1, inflate)
        self.cube(bone_l, [-(origin[0] + size[0]), origin[1], origin[2]], size, paint, 1, inflate)

    def layout(self, width=256):
        items = []
        for i, c in enumerate(self.cubes):
            w, h, d = c["size"]
            items.append((i, max(1, 2 * (d + w)), max(1, d + h)))
        order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
        x = y = row_h = 0
        pos = {}
        for i, rw, rh in order:
            if x + rw > width:
                x = 0
                y += row_h
                row_h = 0
            pos[i] = (x, y)
            x += rw
            row_h = max(row_h, rh)
        used = y + row_h
        if used > width:
            raise RuntimeError(f"textura cheia: {used}px de altura")
        self.tw, self.th = width, width
        for i, c in enumerate(self.cubes):
            c["uv"] = pos[i]
        print(f"  layout {self.ident}: {len(self.cubes)} cubos, {used}/{width} px de altura")

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

    def paint(self, pal):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for c in self.cubes:
            c["paint"](self.faces_of(c), c, pal)

    def geo_json(self):
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
                    "visible_bounds_width": 12, "visible_bounds_height": 6,
                    "visible_bounds_offset": [0, 2, 0],
                },
                "bones": bones,
            }],
        }

    def save_geo(self):
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), self.geo_json())

    def save_texture(self, name):
        self.tex.save(os.path.join(ASSETS, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", name + "_glowmask.png"))


# =====================================================================================
#  Paletas das 5 fases (+ clone de sombra)
# =====================================================================================
def pal(**kw):
    return {k: (v if k == "motif" else C(v)) for k, v in kw.items()}


PHASES = {
    "fire": pal(scale="#6a1a12", hi="#9a2c18", dark="#2a0806", belly="#d0742c", belly_dark="#8a3a14", horn="#2c2422",
                horn_hi="#4a3c38", membrane="#7a2014", membrane_dark="#4a0e08", vein="#ff9a3a", claw="#1e1a18",
                spine="#3a1612", eye="#ffd27a", accent="#ffb347", motif="crack"),
    "water": pal(scale="#1d4f7a", hi="#3f86b8", dark="#0e2640", belly="#c4ecff", belly_dark="#7ab6d8", horn="#dff6ff",
                 horn_hi="#ffffff", membrane="#25689a", membrane_dark="#123c60", vein="#aef0ff", claw="#eafaff",
                 spine="#a8e8ff", eye="#e8fdff", accent="#8fe3ff", motif="crystal"),
    "earth": pal(scale="#5a4428", hi="#80653a", dark="#2c2014", belly="#a89060", belly_dark="#6e5a36", horn="#4c4238",
                 horn_hi="#6e6254", membrane="#6a5232", membrane_dark="#3e2e1a", vein="#ffc04a", claw="#38322c",
                 spine="#7a7066", eye="#ffcf5a", accent="#5f8a3a", motif="vein"),
    "storm": pal(scale="#1f2a4c", hi="#3c4e80", dark="#0e1428", belly="#c8d4ec", belly_dark="#8090b4", horn="#e6ecf8",
                 horn_hi="#ffffff", membrane="#30406e", membrane_dark="#18224a", vein="#9ff4ff", claw="#d8e0ee",
                 spine="#d4ecff", eye="#fff27a", accent="#7fdbff", motif="bolt"),
    "twilight": pal(scale="#2c1a46", hi="#4e3274", dark="#140a24", belly="#e8dcb0", belly_dark="#a8945a", horn="#f2e6b8",
                    horn_hi="#ffffff", membrane="#3a2058", membrane_dark="#1e0e34", vein="#ffe6a0", claw="#1a1424",
                    spine="#d8b860", eye="#ffffff", accent="#c06aa0", motif="rune",
                    # lado da LUZ (esquerdo, +X)
                    l_scale="#d8c48a", l_hi="#f4e6b8", l_dark="#8a7440", l_membrane="#e6d49a", l_membrane_dark="#a8904e"),
    "shade": pal(scale="#140c1e", hi="#2a1a3c", dark="#06030a", belly="#2a1a3a", belly_dark="#120a1c", horn="#1c1028",
                 horn_hi="#3a2450", membrane="#1a0e28", membrane_dark="#08040e", vein="#b070e0", claw="#0a0610",
                 spine="#2a1640", eye="#ffffff", accent="#8a4fc8", motif="rune"),
}


def side_pal(p, c, key):
    """No Crepusculo o lado esquerdo (+X) e a LUZ dourada; o direito a SOMBRA."""
    if c["side"] > 0 and ("l_" + key) in p:
        return p["l_" + key]
    return p[key]


def h01(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# =====================================================================================
#  Pinceis
# =====================================================================================
def scales_paint(seed, glow_motif=True, rough=False):
    """Escamas em fileiras alternadas (2 px de altura, 3 de largura), luz de cima, motivo brilhante da fase."""
    def p(fs, c, P):
        base, hi, dark = side_pal(P, c, "scale"), side_pal(P, c, "hi"), side_pal(P, c, "dark")
        for name, f in fs.items():
            shade = {"top": 0.12, "bottom": -0.32, "back": -0.06}.get(name, -0.03)
            for yy in range(f.h):
                for xx in range(f.w):
                    row = yy // 2
                    off = (row % 2) * 2
                    cx = (xx + off) % 4
                    col = base
                    if yy % 2 == 0 and cx == 0:
                        col = hi
                    elif yy % 2 == 1 and cx == 3:
                        col = dark
                    elif cx == 1 and yy % 2 == 0:
                        col = mix(base, hi, 0.4)
                    n = h01(xx + f.x, yy + f.y, seed) - 0.5
                    col = lighten(col, n * 0.12) if n > 0 else darken(col, -n * 0.14)
                    if rough and h01(xx, yy, seed + 9) > 0.93:
                        col = darken(col, 0.3)
                    if f.h > 4 and name in ("front", "back", "left", "right"):
                        t = yy / max(1, f.h - 1)
                        col = darken(col, max(0.0, (t - 0.55) * 0.5))
                    col = lighten(col, shade) if shade > 0 else darken(col, -shade)
                    f.set(xx, yy, col)
            if glow_motif and name in ("top", "left", "right", "front", "back"):
                motif(f, P, seed + len(name) * 31)
    return p


def motif(f, P, seed):
    """Detalhe brilhante por fase: rachaduras de magma, cristais, veios ambar, raios, runas."""
    kind = P["motif"]
    rnd = random.Random(seed)
    area = f.w * f.h
    if area < 12:
        return
    vein = P["vein"]
    if kind == "crack":
        for _ in range(max(1, area // 70)):
            x, y = rnd.randrange(f.w), rnd.randrange(f.h)
            for _ in range(rnd.randint(3, 7)):
                f.set(x, y, mix(vein, (255, 255, 200, 255), 0.2), 235)
                x += rnd.choice((-1, 0, 1))
                y += rnd.choice((0, 1, 1))
    elif kind == "crystal":
        for _ in range(max(1, area // 90)):
            x, y = rnd.randrange(f.w), rnd.randrange(f.h)
            f.set(x, y, (255, 255, 255, 255), 255)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                f.set(x + dx, y + dy, vein, 200)
    elif kind == "vein":
        for _ in range(max(1, area // 80)):
            x, y = rnd.randrange(f.w), rnd.randrange(f.h)
            for _ in range(rnd.randint(2, 5)):
                f.set(x, y, vein, 215)
                x += rnd.choice((-1, 1))
                y += rnd.choice((-1, 0, 1))
        for _ in range(max(1, area // 60)):
            x, y = rnd.randrange(f.w), rnd.randrange(f.h)
            f.set(x, y, P["accent"])
    elif kind == "bolt":
        for _ in range(max(1, area // 110)):
            x, y = rnd.randrange(f.w), 0
            for _ in range(f.h):
                f.set(x, y, mix(vein, (255, 255, 255, 255), 0.3), 245)
                x += rnd.choice((-1, 1)) if rnd.random() < 0.6 else 0
                y += 1
                if rnd.random() < 0.15:
                    break
    elif kind == "rune":
        for _ in range(max(1, area // 120)):
            x, y = rnd.randrange(max(1, f.w - 3)), rnd.randrange(max(1, f.h - 3))
            shape = rnd.choice(((0, 0, 1, 0, 2, 0, 1, 1, 1, 2), (0, 0, 0, 1, 0, 2, 1, 1, 2, 2), (0, 1, 1, 0, 2, 1, 1, 2)))
            for i in range(0, len(shape), 2):
                f.set(x + shape[i], y + shape[i + 1], vein, 230)


def belly_paint(seed):
    """Placas da barriga em faixas (lado de baixo e frente)."""
    def p(fs, c, P):
        belly, bdark = P["belly"], P["belly_dark"]
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    band = (yy if name in ("front", "back", "left", "right") else xx) % 4
                    col = bdark if band == 3 else (lighten(belly, 0.1) if band == 0 else belly)
                    n = h01(xx, yy, seed) - 0.5
                    col = lighten(col, n * 0.1) if n > 0 else darken(col, -n * 0.12)
                    f.set(xx, yy, col)
            if P["motif"] in ("crack", "vein") and name == "bottom":
                motif(f, P, seed + 7)
    return p


def horn_paint(seed):
    def p(fs, c, P):
        h, hh = P["horn"], P["horn_hi"]
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    ring = (yy + xx // 2) % 3 == 0
                    col = mix(h, hh, 0.35) if name == "top" else h
                    if ring:
                        col = darken(col, 0.22)
                    f.set(xx, yy, col)
    return p


def claw_paint(fs, c, P):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.set(xx, yy, lighten(P["claw"], 0.15) if yy == 0 else P["claw"])


def teeth_paint(fs, c, P):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.set(xx, yy, (236, 230, 214, 255) if xx % 2 == 0 else (196, 188, 170, 255))


def eye_paint(fs, c, P):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.set(xx, yy, P["eye"], 255 if yy % 2 == 0 else 225)
        if f.w >= 3 and f.h >= 2:
            f.set(f.w // 2, f.h // 2, (20, 10, 10, 255))


def core_paint(fs, c, P):
    """Nucleo Primordial no peito: gema facetada brilhando inteira."""
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                edge = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                col = P["accent"] if edge else mix(P["vein"], (255, 255, 255, 255), 0.45)
                f.set(xx, yy, col, 210 if edge else 255)


def spine_paint(seed):
    def p(fs, c, P):
        sp = P["spine"]
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    tip = yy == 0 and name not in ("top", "bottom")
                    col = mix(sp, P["vein"], 0.6) if tip or name == "top" else darken(sp, 0.12 * (yy / max(1, f.h)))
                    f.set(xx, yy, col, 230 if (tip or name == "top") else None)
    return p


def bone_paint(seed):
    """Ossos das asas: couro escuro com nodulos."""
    def p(fs, c, P):
        base = side_pal(P, c, "dark")
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    col = lighten(base, 0.18) if name == "top" else base
                    if (xx + yy * 3) % 7 == 0:
                        col = lighten(col, 0.2)
                    f.set(xx, yy, col)
    return p


def membrane_paint(seed, scallop_edge=True):
    """Membrana: gradiente, veios irradiando do braco e borda de tras recortada (transparente)."""
    def p(fs, c, P):
        m, md = side_pal(P, c, "membrane"), side_pal(P, c, "membrane_dark")
        vein = P["vein"]
        for name in ("top", "bottom"):
            f = fs[name]
            for yy in range(f.h):
                t = yy / max(1, f.h - 1)
                for xx in range(f.w):
                    col = mix(m, md, 0.15 + 0.6 * t)
                    if name == "bottom":
                        col = darken(col, 0.18)
                    n = h01(xx + f.x, yy + f.y, seed) - 0.5
                    col = lighten(col, n * 0.08) if n > 0 else darken(col, -n * 0.08)
                    f.set(xx, yy, col)
                # dobra do braco (frente) mais escura
                f.set(0, yy, darken(m, 0.3))
            # veios: linhas da frente (yy=0) para tras, abrindo em leque
            rays = max(2, f.w // 6)
            for r in range(rays):
                x0 = (r + 0.5) * f.w / rays
                spread = (r - (rays - 1) / 2) * 0.12
                for yy in range(f.h):
                    xx = int(round(x0 + spread * yy))
                    if 0 <= xx < f.w:
                        f.set(xx, yy, mix(vein, m, 0.45), 165 if P["motif"] != "crack" else 200)
            if scallop_edge:
                depth = max(2, f.h // 7)
                period = max(4, f.w // 4)
                for xx in range(f.w):
                    ph = (xx % period) / period
                    cut = int(round(depth * (1 - math.sin(math.pi * ph))))
                    for k in range(cut):
                        f.clear(xx, f.h - 1 - k)
    return p


def glow_solid(fs, c, P):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.set(xx, yy, P["vein"], 255)


# =====================================================================================
#  Modelo
# =====================================================================================
def build_dragon():
    m = Model(IDENT)
    S = scales_paint
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 20, 2], "root")
    m.bone("core", [0, 16, -12], "body")
    m.bone("hips", [0, 20, 12], "body")
    m.bone("neck1", [0, 24, -12], "body")
    m.bone("neck2", [0, 25, -21], "neck1")
    m.bone("head", [0, 26, -30], "neck2")
    m.bone("jaw", [0, 22.5, -40], "head")
    m.bone("wing_r", [-8, 28, -6], "body")
    m.bone("wing_r_mid", [-30, 28, -6], "wing_r")
    m.bone("wing_r_tip", [-54, 28, -6], "wing_r_mid")
    m.bone("wing_l", [8, 28, -6], "body")
    m.bone("wing_l_mid", [30, 28, -6], "wing_l")
    m.bone("wing_l_tip", [54, 28, -6], "wing_l_mid")
    m.bone("arm_r", [-7, 17, -8], "body")
    m.bone("forearm_r", [-7, 10, -8], "arm_r")
    m.bone("arm_l", [7, 17, -8], "body")
    m.bone("forearm_l", [7, 10, -8], "arm_l")
    m.bone("leg_r", [-7, 18, 16], "hips")
    m.bone("shin_r", [-8, 10, 17], "leg_r")
    m.bone("leg_l", [7, 18, 16], "hips")
    m.bone("shin_l", [8, 10, 17], "leg_l")
    m.bone("tail1", [0, 22, 24], "hips")
    m.bone("tail2", [0, 21, 36], "tail1")
    m.bone("tail3", [0, 20.5, 48], "tail2")
    m.bone("tail4", [0, 20, 60], "tail3")

    # ---- tronco ----
    m.cube("body", [-9, 13, -12], [18, 15, 24], S(11))
    m.cube("body", [-7, 12, -11], [14, 2, 21], belly_paint(12))
    m.cube("body", [-9.5, 23, -10], [19, 4, 18], S(13, rough=True))          # ombros/dorso mais largos
    for z in (-10, -4, 2, 8):
        m.cube("body", [-1.5, 28, z], [3, 4, 3], spine_paint(14 + z))
        m.cube("body", [-1, 32, z + 0.5], [2, 2, 2], spine_paint(20 + z))
    m.cube("core", [-2.5, 13.5, -13.5], [5, 5, 2], core_paint)
    m.cube("core", [-1.5, 14.5, -14.5], [3, 3, 1], core_paint)
    # ---- quadril ----
    m.cube("hips", [-8, 14, 12], [16, 13, 12], S(21))
    m.cube("hips", [-6, 13, 13], [12, 2, 10], belly_paint(22))
    for z in (14, 20):
        m.cube("hips", [-1.5, 27, z], [3, 3, 3], spine_paint(23 + z))
    # ---- pescoco e cabeca ----
    m.cube("neck1", [-5, 19, -22], [10, 10, 10], S(31))
    m.cube("neck1", [-4, 18.5, -21], [8, 2, 8], belly_paint(32))
    m.cube("neck1", [-1, 29, -19], [2, 3, 3], spine_paint(33))
    m.cube("neck2", [-4, 20, -31], [8, 9, 10], S(34))
    m.cube("neck2", [-3, 19.5, -30], [6, 2, 8], belly_paint(35))
    m.cube("neck2", [-1, 29, -28], [2, 3, 3], spine_paint(36))
    m.cube("head", [-6, 22, -41], [12, 10, 12], S(41))
    m.cube("head", [-4, 22, -52], [8, 6, 11], S(42))
    m.cube("head", [-3.5, 21, -51], [7, 1, 9], teeth_paint)
    m.cube("head", [-4.5, 26.5, -52.5], [9, 2, 4], S(43))                      # focinho com narinas
    m.pair("head", "head", [-6.5, 30, -41], [4, 2, 6], S(44))                  # sobrancelhas
    m.pair("head", "head", [-6.2, 27, -40], [1, 2, 4], eye_paint)
    # chifres em degraus (para tras e para cima), um par grande e um par lateral
    m.pair("head", "head", [-6, 30, -33], [3, 3, 4], horn_paint(45))
    m.pair("head", "head", [-6, 32, -30], [2, 3, 4], horn_paint(46))
    m.pair("head", "head", [-6, 34, -27], [2, 2, 4], horn_paint(47))
    m.pair("head", "head", [-5.5, 35.5, -24], [1, 2, 3], horn_paint(48))
    m.pair("head", "head", [-8, 26, -37], [2, 2, 3], horn_paint(49))
    m.pair("head", "head", [-10, 26.5, -35], [2, 2, 3], horn_paint(50))
    m.cube("head", [-1, 32, -39], [2, 3, 2], spine_paint(51))
    m.cube("head", [-1, 32, -35], [2, 2, 2], spine_paint(52))
    m.cube("jaw", [-4, 19, -51], [8, 3, 11], S(53))
    m.cube("jaw", [-3.5, 22, -50], [7, 1, 8], teeth_paint)
    m.cube("jaw", [-3, 18, -44], [6, 1, 4], belly_paint(54))

    # ---- asas (3 segmentos por lado, cada um com braco/osso e membrana de espessura 0) ----
    m.pair("wing_r", "wing_l", [-30, 27, -8], [22, 3, 4], bone_paint(61))
    m.pair("wing_r", "wing_l", [-30, 28, -4], [22, 0, 20], membrane_paint(62))
    m.pair("wing_r_mid", "wing_l_mid", [-54, 27.5, -7.5], [24, 2, 3], bone_paint(63))
    m.pair("wing_r_mid", "wing_l_mid", [-54, 28, -4.5], [24, 0, 26], membrane_paint(64))
    m.pair("wing_r_mid", "wing_l_mid", [-56, 27.5, -10], [3, 2, 3], claw_paint)               # garra do polegar
    m.pair("wing_r_tip", "wing_l_tip", [-74, 27.8, -6.5], [20, 2, 2], bone_paint(65))
    m.pair("wing_r_tip", "wing_l_tip", [-74, 28, -5], [20, 0, 18], membrane_paint(66))
    m.pair("wing_r_tip", "wing_l_tip", [-79, 27.5, -6.5], [5, 2, 2], claw_paint)

    # ---- patas da frente ----
    m.pair("arm_r", "arm_l", [-10, 9, -11], [6, 10, 7], S(71))
    m.pair("forearm_r", "forearm_l", [-9.5, 2, -10], [5, 8, 5], S(72))
    m.pair("forearm_r", "forearm_l", [-10.5, 0, -14], [7, 3, 8], S(73))
    for x in (-10, -7.5, -5):
        m.pair("forearm_r", "forearm_l", [x, 0, -16], [1, 2, 2], claw_paint)
    # ---- patas de tras ----
    m.pair("leg_r", "leg_l", [-11, 9, 11], [7, 11, 10], S(81))
    m.pair("shin_r", "shin_l", [-10, 3, 15], [5, 8, 5], S(82))
    m.pair("shin_r", "shin_l", [-11, 0, 10], [7, 3, 9], S(83))
    for x in (-10.5, -8, -5.5):
        m.pair("shin_r", "shin_l", [x, 0, 8], [1, 2, 2], claw_paint)

    # ---- cauda ----
    m.cube("tail1", [-6, 16, 24], [12, 10, 12], S(91))
    m.cube("tail1", [-1, 26, 26], [2, 3, 3], spine_paint(92))
    m.cube("tail1", [-1, 26, 32], [2, 2, 3], spine_paint(93))
    m.cube("tail2", [-4.5, 17, 36], [9, 8, 12], S(94))
    m.cube("tail2", [-1, 25, 38], [2, 3, 3], spine_paint(95))
    m.cube("tail3", [-3.5, 17.5, 48], [7, 6, 12], S(96))
    m.cube("tail3", [-1, 23.5, 50], [2, 2, 3], spine_paint(97))
    m.cube("tail4", [-2.5, 18, 60], [5, 4, 12], S(98))
    m.cube("tail4", [-0.5, 15, 66], [1, 10, 10], spine_paint(99))            # barbatana vertical
    m.cube("tail4", [-6, 19.5, 68], [12, 1, 6], horn_paint(100))             # lamina em pa

    m.layout(256)
    m.save_geo()
    for name, P in PHASES.items():
        m.paint(P)
        m.save_texture("primordial_shade" if name == "shade" else f"{IDENT}_{name}")
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


def sinv(ph, amp, off=0.0, mult=1, shift=0.0):
    return off + amp * math.sin(2 * math.pi * (ph * mult + shift))


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


def flap(length, up, down, mid_lag=0.12, tip_lag=0.22, mult=1, fold=0.0):
    """Batida de asas: asa (Z), meio e ponta atrasados (onda que corre pela asa)."""
    amp = (up - down) / 2
    off = (up + down) / 2
    return {
        "wing_r": {"rotation": wave(length, lambda p: [0, fold, sinv(p, amp, off, mult, 0.25)], 16)},
        "wing_l": {"rotation": wave(length, lambda p: [0, -fold, -sinv(p, amp, off, mult, 0.25)], 16)},
        "wing_r_mid": {"rotation": wave(length, lambda p: [0, 0, sinv(p, amp * 0.55, 0, mult, 0.25 - mid_lag)], 16)},
        "wing_l_mid": {"rotation": wave(length, lambda p: [0, 0, -sinv(p, amp * 0.55, 0, mult, 0.25 - mid_lag)], 16)},
        "wing_r_tip": {"rotation": wave(length, lambda p: [0, 0, sinv(p, amp * 0.4, 0, mult, 0.25 - tip_lag)], 16)},
        "wing_l_tip": {"rotation": wave(length, lambda p: [0, 0, -sinv(p, amp * 0.4, 0, mult, 0.25 - tip_lag)], 16)},
    }


def folded(length, breath=2.0):
    """Asas dobradas (no chao): braco para cima, meio para baixo ao lado do corpo, ponta para cima."""
    return {
        "wing_r": {"rotation": wave(length, lambda p: [0, 12, sinv(p, breath, 72)])},
        "wing_l": {"rotation": wave(length, lambda p: [0, -12, -sinv(p, breath, 72)])},
        "wing_r_mid": {"rotation": wave(length, lambda p: [0, 0, -150])},
        "wing_l_mid": {"rotation": wave(length, lambda p: [0, 0, 150])},
        "wing_r_tip": {"rotation": wave(length, lambda p: [0, 0, 140])},
        "wing_l_tip": {"rotation": wave(length, lambda p: [0, 0, -140])},
    }


def tail_sway(length, amp=6, mult=1, pitch=0.0):
    return {f"tail{i}": {"rotation": wave(length, lambda p, i=i: [pitch * (i / 4), sinv(p, amp, 0, mult, -0.08 * i), 0])}
            for i in range(1, 5)}


def core_pulse(length, lo=0.9, hi=1.25, mult=None):
    m = mult or max(1, int(round(length / 1.0)))
    return {"core": {"scale": wave(length, lambda p: [lo + (hi - lo) * (0.5 + 0.5 * math.sin(2 * math.pi * p * m))] * 3)}}


def merge(*ds):
    out = {}
    for d in ds:
        for k, v in d.items():
            out.setdefault(k, {}).update(v)
    return out


def legs_tucked(length):
    return {
        "arm_r": {"rotation": wave(length, lambda p: [sinv(p, 4, 35), 0, 0])},
        "arm_l": {"rotation": wave(length, lambda p: [sinv(p, 4, 35, shift=0.2), 0, 0])},
        "forearm_r": {"rotation": wave(length, lambda p: [-45, 0, 0])},
        "forearm_l": {"rotation": wave(length, lambda p: [-45, 0, 0])},
        "leg_r": {"rotation": wave(length, lambda p: [sinv(p, 4, 55), 0, 0])},
        "leg_l": {"rotation": wave(length, lambda p: [sinv(p, 4, 55, shift=0.3), 0, 0])},
        "shin_r": {"rotation": wave(length, lambda p: [20, 0, 0])},
        "shin_l": {"rotation": wave(length, lambda p: [20, 0, 0])},
    }


def legs_dangle(length):
    return {
        "arm_r": {"rotation": wave(length, lambda p: [sinv(p, 6, 10), 0, 4])},
        "arm_l": {"rotation": wave(length, lambda p: [sinv(p, 6, 10, shift=0.3), 0, -4])},
        "forearm_r": {"rotation": wave(length, lambda p: [sinv(p, 5, -20), 0, 0])},
        "forearm_l": {"rotation": wave(length, lambda p: [sinv(p, 5, -20, shift=0.3), 0, 0])},
        "leg_r": {"rotation": wave(length, lambda p: [sinv(p, 6, 18), 0, 3])},
        "leg_l": {"rotation": wave(length, lambda p: [sinv(p, 6, 18, shift=0.4), 0, -3])},
        "shin_r": {"rotation": wave(length, lambda p: [sinv(p, 4, 10), 0, 0])},
        "shin_l": {"rotation": wave(length, lambda p: [sinv(p, 4, 10, shift=0.4), 0, 0])},
    }


def dragon_anims():
    a = Anims(IDENT)
    # ---------------- base (controlador principal) ----------------
    L = 3.0
    a.add("idle", L, True, merge(folded(L), tail_sway(L, 7), core_pulse(L), {
        "body": {"rotation": wave(L, lambda p: [sinv(p, 1.5), 0, 0]), "position": wave(L, lambda p: [0, sinv(p, 0.4), 0])},
        "neck1": {"rotation": wave(L, lambda p: [sinv(p, 3, -6), sinv(p, 4, 0, shift=0.2), 0])},
        "neck2": {"rotation": wave(L, lambda p: [sinv(p, 3, 4), 0, 0])},
        "head": {"rotation": wave(L, lambda p: [sinv(p, 3, 6), 0, 0])},
        "jaw": {"rotation": wave(L, lambda p: [max(0.0, sinv(p, 6, 1)), 0, 0])},
    }))
    L = 1.6
    a.add("walk", L, True, merge(folded(L, 4), tail_sway(L, 12, 1), core_pulse(L, mult=2), {
        "body": {"rotation": wave(L, lambda p: [0, 0, sinv(p, 3)]), "position": wave(L, lambda p: [0, sinv(p, 1.0, 0, 2), 0])},
        "neck1": {"rotation": wave(L, lambda p: [-4, sinv(p, 5), 0])},
        "head": {"rotation": wave(L, lambda p: [6, sinv(p, -4), 0])},
        "arm_r": {"rotation": wave(L, lambda p: [sinv(p, 26), 0, 0])},
        "arm_l": {"rotation": wave(L, lambda p: [sinv(p, -26), 0, 0])},
        "forearm_r": {"rotation": wave(L, lambda p: [max(0.0, sinv(p, -24, shift=0.1)), 0, 0])},
        "forearm_l": {"rotation": wave(L, lambda p: [max(0.0, sinv(p, 24, shift=0.1)), 0, 0])},
        "leg_r": {"rotation": wave(L, lambda p: [sinv(p, -24), 0, 0])},
        "leg_l": {"rotation": wave(L, lambda p: [sinv(p, 24), 0, 0])},
        "shin_r": {"rotation": wave(L, lambda p: [max(0.0, sinv(p, 22, shift=0.15)), 0, 0])},
        "shin_l": {"rotation": wave(L, lambda p: [max(0.0, sinv(p, -22, shift=0.15)), 0, 0])},
    }))
    L = 2.0
    a.add("hover", L, True, merge(flap(L, 34, -26), legs_dangle(L), tail_sway(L, 8, 1, 6), core_pulse(L), {
        "root": {"position": wave(L, lambda p: [0, sinv(p, 2.5, 0, 1, 0.5), 0])},
        "body": {"rotation": wave(L, lambda p: [sinv(p, 3, -4, 1, 0.5), 0, 0])},
        "neck1": {"rotation": wave(L, lambda p: [sinv(p, 3, -8), 0, 0])},
        "neck2": {"rotation": wave(L, lambda p: [sinv(p, 2, 6), 0, 0])},
        "head": {"rotation": wave(L, lambda p: [sinv(p, 3, 10), 0, 0])},
    }))
    L = 1.2
    a.add("fly", L, True, merge(flap(L, 30, -30, fold=8), legs_tucked(L), tail_sway(L, 5, 1, -4), core_pulse(L), {
        "root": {"position": wave(L, lambda p: [0, sinv(p, 1.5, 0, 1, 0.5), 0])},
        "body": {"rotation": wave(L, lambda p: [8, 0, sinv(p, 2)])},
        "neck1": {"rotation": wave(L, lambda p: [-10, 0, 0])},
        "neck2": {"rotation": wave(L, lambda p: [-4, 0, 0])},
        "head": {"rotation": wave(L, lambda p: [8, 0, 0])},
    }))
    L = 2.0
    a.add("exhausted", L, True, merge(tail_sway(L, 3, 1, 8), core_pulse(L, 0.7, 1.0), {
        "body": {"rotation": wave(L, lambda p: [10, 0, 0]), "position": wave(L, lambda p: [0, sinv(p, 0.8, -3), 0])},
        "neck1": {"rotation": wave(L, lambda p: [sinv(p, 3, 28), 0, 0])},
        "neck2": {"rotation": wave(L, lambda p: [14, 0, 0])},
        "head": {"rotation": wave(L, lambda p: [12, sinv(p, 4), 0])},
        "jaw": {"rotation": wave(L, lambda p: [sinv(p, 8, 12), 0, 0])},
        "wing_r": {"rotation": wave(L, lambda p: [0, 6, sinv(p, 3, -18)])},
        "wing_l": {"rotation": wave(L, lambda p: [0, -6, -sinv(p, 3, -18)])},
        "wing_r_mid": {"rotation": wave(L, lambda p: [0, 0, -30])},
        "wing_l_mid": {"rotation": wave(L, lambda p: [0, 0, 30])},
        "wing_r_tip": {"rotation": wave(L, lambda p: [0, 0, -20])},
        "wing_l_tip": {"rotation": wave(L, lambda p: [0, 0, 20])},
        "arm_r": {"rotation": wave(L, lambda p: [-20, 0, 6])},
        "arm_l": {"rotation": wave(L, lambda p: [-20, 0, -6])},
        "forearm_r": {"rotation": wave(L, lambda p: [30, 0, 0])},
        "forearm_l": {"rotation": wave(L, lambda p: [30, 0, 0])},
    }))
    a.add("death", 8.0, "hold", {
        "root": {"rotation": K(0, [0, 0, 0], 2.5, [10, 0, -8], 4.0, [-10, 0, 20], 5.0, [5, 0, 62], 8.0, [5, 0, 70])},
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [-25, 0, 0], 2.5, [10, 0, 0], 5.0, [0, 0, 0], 8.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 1.0, [-45, 0, 0], 2.5, [-20, 15, 0], 5.0, [35, 25, 0], 8.0, [40, 30, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 1.0, [-20, 0, 0], 5.0, [25, 10, 0], 8.0, [28, 10, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-25, 0, 0], 5.0, [30, 0, 10], 8.0, [32, 0, 12])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.8, [40, 0, 0], 2.0, [30, 0, 0], 5.0, [22, 0, 0], 8.0, [25, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 20], 1.0, [0, 0, 60], 2.5, [0, 0, -10], 5.0, [0, 0, -40], 8.0, [0, 0, -45])},
        "wing_l": {"rotation": K(0, [0, 0, -20], 1.0, [0, 0, -60], 2.5, [0, 0, 10], 5.0, [0, 0, 70], 8.0, [0, 0, 75])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 2.5, [0, 0, -20], 5.0, [0, 0, -35], 8.0, [0, 0, -38])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 2.5, [0, 0, 20], 5.0, [0, 0, 10], 8.0, [0, 0, 10])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 5.0, [-30, 0, 20], 8.0, [-30, 0, 22])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 5.0, [-10, 0, -10], 8.0, [-10, 0, -10])},
        "tail1": {"rotation": K(0, [0, 0, 0], 2.5, [-10, 20, 0], 5.0, [5, -15, 0], 8.0, [5, -18, 0])},
        "tail2": {"rotation": K(0, [0, 0, 0], 2.5, [0, 20, 0], 5.0, [5, -20, 0], 8.0, [5, -22, 0])},
        "tail3": {"rotation": K(0, [0, 0, 0], 5.0, [0, -15, 0], 8.0, [0, -16, 0])},
        "core": {"scale": K(0, [1, 1, 1], 1.0, [1.6, 1.6, 1.6], 2.5, [1.2, 1.2, 1.2], 5.5, [1.8, 1.8, 1.8], 7.5, [0.2, 0.2, 0.2],
                            8.0, [0, 0, 0])},
    })

    # ---------------- acoes (controlador "action") ----------------
    a.add("intro", 4.5, False, merge(tail_sway(4.5, 10, 2), {
        "root": {"position": K(0, [0, -6, 0], 1.6, [0, -4, 0], 2.2, [0, 6, 0], 4.5, [0, 0, 0])},
        "body": {"rotation": K(0, [30, 0, 0], 1.6, [26, 0, 0], 2.2, [-28, 0, 0], 3.6, [-22, 0, 0], 4.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [40, 0, 0], 1.6, [35, 0, 0], 2.2, [-40, 0, 0], 3.6, [-35, 0, 0], 4.5, [0, 0, 0])},
        "head": {"rotation": K(0, [30, 0, 0], 1.6, [28, 0, 0], 2.2, [-30, 0, 0], 3.6, [-25, 0, 0], 4.5, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 2.1, [0, 0, 0], 2.4, [45, 0, 0], 3.6, [40, 0, 0], 4.0, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, -40, 80], 1.6, [0, -40, 75], 2.2, [0, 0, 50], 2.6, [0, 0, -10], 3.6, [0, 0, 30], 4.5, [0, 0, 10])},
        "wing_l": {"rotation": K(0, [0, 40, -80], 1.6, [0, 40, -75], 2.2, [0, 0, -50], 2.6, [0, 0, 10], 3.6, [0, 0, -30], 4.5, [0, 0, -10])},
        "wing_r_mid": {"rotation": K(0, [0, 0, -140], 1.6, [0, 0, -130], 2.3, [0, 0, 0], 4.5, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 140], 1.6, [0, 0, 130], 2.3, [0, 0, 0], 4.5, [0, 0, 0])},
        "core": {"scale": K(0, [0.3, 0.3, 0.3], 1.8, [0.6, 0.6, 0.6], 2.2, [2.4, 2.4, 2.4], 3.0, [1, 1, 1])},
    }))
    a.add("phase", 4.0, False, merge(flap(4.0, 45, -25, mult=4), tail_sway(4.0, 14, 3), {
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [-30, 0, 0], 3.2, [-26, 0, 0], 4.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.8, [-40, 0, 0], 1.2, [-35, 12, 0], 2.0, [-35, -12, 0], 3.2, [-30, 0, 0], 4.0, [0, 0, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 0.8, [-15, 0, 0], 3.2, [-12, 0, 0], 4.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.8, [-25, 0, 0], 3.2, [-20, 0, 0], 4.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.9, [45, 0, 0], 3.0, [42, 0, 0], 3.4, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.8, [-60, 0, 10], 3.2, [-55, 0, 10], 4.0, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.8, [-60, 0, -10], 3.2, [-55, 0, -10], 4.0, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [0.6, 0.6, 0.6], 1.2, [2.6, 2.6, 2.6], 3.2, [2.0, 2.0, 2.0], 4.0, [1, 1, 1])},
    }))
    a.add("roar", 2.5, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-14, 0, 0], 2.0, [-12, 0, 0], 2.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.5, [-30, 0, 0], 2.0, [-28, 0, 0], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [10, 0, 0], 0.6, [-20, 0, 0], 2.0, [-18, 0, 0], 2.5, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.6, [45, 0, 0], 2.0, [42, 0, 0], 2.4, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.6, [0, 0, 40], 2.0, [0, 0, 35], 2.5, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.6, [0, 0, -40], 2.0, [0, 0, -35], 2.5, [0, 0, 0])},
    })
    a.add("bite", 1.1, False, {
        "root": {"position": K(0, [0, 0, 0], 0.35, [0, 0, 3], 0.55, [0, 0, -6], 1.1, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.35, [-25, 0, 0], 0.55, [25, 0, 0], 0.8, [20, 0, 0], 1.1, [0, 0, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 0.35, [-10, 0, 0], 0.55, [10, 0, 0], 1.1, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.35, [-15, 0, 0], 0.55, [-5, 0, 0], 1.1, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.35, [45, 0, 0], 0.52, [45, 0, 0], 0.6, [0, 0, 0], 1.1, [0, 0, 0])},
    })
    a.add("claw", 1.2, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.4, [-12, 15, 6], 0.65, [6, -20, -4], 1.2, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-95, 0, 25], 0.62, [30, 0, -10], 0.8, [25, 0, -8], 1.2, [0, 0, 0])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.4, [-30, 0, 0], 0.62, [10, 0, 0], 1.2, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.4, [-40, 0, -15], 0.8, [-10, 0, 0], 1.2, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.4, [-15, 20, 0], 0.65, [10, -15, 0], 1.2, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.5, [25, 0, 0], 0.9, [0, 0, 0])},
    })
    a.add("tail_swipe", 1.5, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [0, 25, 0], 0.85, [0, -55, 0], 1.5, [0, 0, 0])},
        "tail1": {"rotation": K(0, [0, 0, 0], 0.5, [0, -35, 0], 0.85, [0, 55, 0], 1.5, [0, 0, 0])},
        "tail2": {"rotation": K(0, [0, 0, 0], 0.5, [0, -30, 0], 0.9, [0, 50, 0], 1.5, [0, 0, 0])},
        "tail3": {"rotation": K(0, [0, 0, 0], 0.5, [0, -25, 0], 0.95, [0, 45, 0], 1.5, [0, 0, 0])},
        "tail4": {"rotation": K(0, [0, 0, 0], 0.5, [0, -20, 0], 1.0, [0, 40, 0], 1.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.5, [0, -20, 0], 0.85, [0, 30, 0], 1.5, [0, 0, 0])},
    })
    a.add("breath", 3.0, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.8, [-10, 0, 0], 1.0, [6, 0, 0], 2.6, [6, 0, 0], 3.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.8, [-35, 0, 0], 1.0, [10, 0, 0], 2.6, [12, 0, 0], 3.0, [0, 0, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 0.8, [-15, 0, 0], 1.0, [5, 0, 0], 2.6, [5, 0, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.8, [-20, 0, 0], 1.0, [-8, 0, 0], 2.6, [-8, 0, 0], 3.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.8, [15, 0, 0], 1.0, [42, 0, 0], 2.6, [42, 0, 0], 2.9, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.9, [2.0, 2.0, 2.0], 2.6, [1.6, 1.6, 1.6], 3.0, [1, 1, 1])},
    })
    a.add("dive", 3.5, False, merge(tail_sway(3.5, 4, 3), {
        "body": {"rotation": K(0, [0, 0, 0], 0.7, [-25, 0, 0], 1.5, [-10, 0, 0], 1.6, [30, 0, 0], 2.3, [32, 0, 0], 2.6, [-10, 0, 0],
                              3.5, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.35, [0, 0, 45], 0.7, [0, 0, -30], 1.0, [0, 0, 40], 1.5, [0, 0, 20], 1.6, [0, 30, 10],
                                2.3, [0, 30, 10], 2.6, [0, 0, 50], 3.5, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.35, [0, 0, -45], 0.7, [0, 0, 30], 1.0, [0, 0, -40], 1.5, [0, 0, -20], 1.6, [0, -30, -10],
                                2.3, [0, -30, -10], 2.6, [0, 0, -50], 3.5, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 1.6, [0, 0, -40], 2.3, [0, 0, -40], 2.6, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 1.6, [0, 0, 40], 2.3, [0, 0, 40], 2.6, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 1.6, [-20, 0, 0], 2.3, [-20, 0, 0], 2.6, [20, 0, 0], 3.5, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 1.6, [30, 0, 0], 2.3, [35, 0, 0], 2.6, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 1.6, [50, 0, 0], 2.3, [50, 0, 0], 2.5, [-40, 0, 0], 3.5, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 1.6, [50, 0, 0], 2.3, [50, 0, 0], 2.5, [-40, 0, 0], 3.5, [0, 0, 0])},
    }))
    a.add("land", 1.6, False, merge(flap(1.6, 55, -20, mult=2), {
        "body": {"rotation": K(0, [-20, 0, 0], 1.2, [-10, 0, 0], 1.6, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [-40, 0, 0], 1.2, [-20, 0, 0], 1.6, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [-40, 0, 0], 1.2, [-20, 0, 0], 1.6, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [-30, 0, 0], 1.2, [-10, 0, 0], 1.6, [0, 0, 0])},
        "leg_l": {"rotation": K(0, [-30, 0, 0], 1.2, [-10, 0, 0], 1.6, [0, 0, 0])},
        "root": {"position": K(0, [0, 0, 0], 1.3, [0, -3, 0], 1.6, [0, 0, 0])},
    }))
    a.add("takeoff", 1.4, False, merge(flap(1.4, 60, -40, mult=2), {
        "body": {"rotation": K(0, [10, 0, 0], 0.3, [15, 0, 0], 0.6, [-25, 0, 0], 1.4, [0, 0, 0])},
        "root": {"position": K(0, [0, -3, 0], 0.4, [0, -4, 0], 0.7, [0, 4, 0], 1.4, [0, 0, 0])},
        "leg_r": {"rotation": K(0, [20, 0, 0], 0.6, [-30, 0, 0], 1.4, [40, 0, 0])},
        "leg_l": {"rotation": K(0, [20, 0, 0], 0.6, [-30, 0, 0], 1.4, [40, 0, 0])},
    }))
    a.add("gust", 1.5, False, {
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.5, [0, -30, 70], 0.8, [0, 25, -30], 1.5, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.5, [0, 30, -70], 0.8, [0, -25, 30], 1.5, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 0.5, [0, 0, 20], 0.85, [0, 0, -25], 1.5, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 0.5, [0, 0, -20], 0.85, [0, 0, 25], 1.5, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 0.8, [10, 0, 0], 1.5, [0, 0, 0])},
    })
    # ---- golpes por elemento ----
    a.add("meteor_call", 3.0, False, merge(flap(3.0, 50, -10, mult=3), {
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-35, 0, 0], 2.5, [-30, 0, 0], 3.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.6, [-50, 0, 0], 2.5, [-45, 0, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-30, 0, 0], 2.5, [-25, 0, 0], 3.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.7, [45, 0, 0], 2.4, [40, 0, 0], 2.8, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [2.2, 2.2, 2.2], 2.5, [1.8, 1.8, 1.8], 3.0, [1, 1, 1])},
    }))
    a.add("tidal_roar", 3.0, False, {
        "neck1": {"rotation": K(0, [0, 0, 0], 0.6, [-20, -35, 0], 1.6, [-10, 35, 0], 2.4, [-10, 30, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-15, -10, 0], 1.6, [0, 10, 0], 3.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.6, [40, 0, 0], 2.4, [38, 0, 0], 2.8, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.6, [0, -40, 55], 1.4, [0, 30, -20], 2.4, [0, 20, -10], 3.0, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.6, [0, 40, -55], 1.4, [0, -30, 20], 2.4, [0, -20, 10], 3.0, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-10, -10, 0], 1.6, [5, 10, 0], 3.0, [0, 0, 0])},
    })
    a.add("quake_stomp", 2.4, False, {
        "root": {"position": K(0, [0, 0, 0], 0.7, [0, 5, 0], 1.0, [0, -2, 0], 1.3, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.7, [-40, 0, 0], 1.0, [12, 0, 0], 1.8, [8, 0, 0], 2.4, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.7, [-90, 0, 10], 1.0, [10, 0, 0], 2.4, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.7, [-90, 0, -10], 1.0, [10, 0, 0], 2.4, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.7, [-30, 0, 0], 1.0, [25, 0, 0], 2.4, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.9, [40, 0, 0], 1.8, [30, 0, 0], 2.2, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.7, [0, 0, 60], 1.0, [0, 0, -10], 2.4, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.7, [0, 0, -60], 1.0, [0, 0, 10], 2.4, [0, 0, 0])},
    })
    a.add("storm_call", 3.0, False, merge(flap(3.0, 65, 20, mult=6), {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-20, 0, 0], 2.6, [-18, 0, 0], 3.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.5, [-45, 0, 0], 2.6, [-40, 0, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.5, [-35, 0, 0], 2.6, [-30, 0, 0], 3.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.6, [35, 0, 0], 2.6, [35, 0, 0], 2.9, [0, 0, 0])},
    }))
    a.add("summon", 2.0, False, merge(flap(2.0, 55, -15, mult=2), {
        "body": {"rotation": K(0, [0, 0, 0], 0.5, [-15, 0, 0], 1.6, [-12, 0, 0], 2.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.5, [30, 0, 0], 1.6, [28, 0, 0], 1.9, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.6, [2.4, 2.4, 2.4], 2.0, [1, 1, 1])},
    }))
    a.add("domain", 3.0, False, {
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [20, 0, 0], 1.8, [-30, 0, 0], 2.6, [-25, 0, 0], 3.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 1.0, [35, 0, 0], 1.8, [-45, 0, 0], 2.6, [-40, 0, 0], 3.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 1.8, [45, 0, 0], 2.6, [40, 0, 0], 3.0, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 1.0, [0, -70, 40], 1.8, [0, 10, 75], 2.6, [0, 10, 70], 3.0, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 1.0, [0, 70, -40], 1.8, [0, -10, -75], 2.6, [0, -10, -70], 3.0, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, -60], 1.8, [0, 0, 10], 3.0, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, 60], 1.8, [0, 0, -10], 3.0, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 1.0, [0.5, 0.5, 0.5], 1.8, [3.0, 3.0, 3.0], 3.0, [1, 1, 1])},
    })
    # A Aniquilacao Primordial carrega 6 s: asas abertas ao maximo, peito estufado, nucleo cada vez maior.
    a.add("ultimate", 7.5, False, {
        "root": {"position": K(0, [0, 0, 0], 1.0, [0, 6, 0], 6.0, [0, 10, 0], 6.3, [0, 2, 0], 7.5, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [-30, 0, 0], 6.0, [-38, 0, 0], 6.3, [20, 0, 0], 7.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 1.0, [-50, 0, 0], 6.0, [-55, 0, 0], 6.3, [25, 0, 0], 7.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.0, [-25, 0, 0], 6.0, [-30, 0, 0], 6.3, [10, 0, 0], 7.5, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 1.0, [20, 0, 0], 5.8, [30, 0, 0], 6.2, [50, 0, 0], 7.0, [40, 0, 0], 7.5, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, 55], 3.0, [0, 0, 60], 6.0, [0, 0, 70], 6.3, [0, 0, -20], 7.5, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, -55], 3.0, [0, 0, -60], 6.0, [0, 0, -70], 6.3, [0, 0, 20], 7.5, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, 10], 6.0, [0, 0, 15], 6.3, [0, 0, -10], 7.5, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 1.0, [0, 0, -10], 6.0, [0, 0, -15], 6.3, [0, 0, 10], 7.5, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 1.0, [1.8, 1.8, 1.8], 3.0, [2.6, 2.6, 2.6], 6.0, [3.6, 3.6, 3.6], 6.2, [0.4, 0.4, 0.4],
                            7.5, [1, 1, 1])},
    })
    # ---------------- v1.0: controle ----------------
    # Olhar da Gorgona: ergue a cabeca, abaixa devagar encarando o grupo, asas meio abertas e paradas; o flash no fim.
    a.add("gaze", 4.0, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-18, 0, 0], 3.4, [-14, 0, 0], 3.6, [4, 0, 0], 4.0, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.6, [-40, 0, 0], 3.3, [-18, 0, 0], 3.5, [12, 0, 0], 4.0, [0, 0, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 0.6, [-10, 0, 0], 3.3, [6, 0, 0], 4.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [-20, 0, 0], 3.3, [18, 0, 0], 3.5, [26, 0, 0], 4.0, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 3.4, [0, 0, 0], 3.55, [30, 0, 0], 3.9, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.6, [0, -20, 48], 3.4, [0, -20, 44], 3.6, [0, 10, 70], 4.0, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.6, [0, 20, -48], 3.4, [0, 20, -44], 3.6, [0, -10, -70], 4.0, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 0.6, [0, 0, -30], 3.4, [0, 0, -28], 4.0, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 0.6, [0, 0, 30], 3.4, [0, 0, 28], 4.0, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 3.4, [1.6, 1.6, 1.6], 3.55, [2.8, 2.8, 2.8], 4.0, [1, 1, 1])},
    })
    # Sifao Primordial: arqueia para tras, boca escancarada sugando, asas abertas tremendo; o nucleo incha.
    a.add("siphon", 6.5, False, merge(tail_sway(6.5, 5, 6), {
        "root": {"position": K(0, [0, 0, 0], 1.0, [0, 3, 0], 5.8, [0, 3, 0], 6.5, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 1.0, [-26, 0, 0], 5.8, [-24, 0, 0], 6.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 1.0, [-20, 0, 0], 1.5, [8, 0, 0], 5.8, [10, 0, 0], 6.5, [0, 0, 0])},
        "neck2": {"rotation": K(0, [0, 0, 0], 1.5, [10, 0, 0], 5.8, [12, 0, 0], 6.5, [0, 0, 0])},
        "head": {"rotation": wave(6.5, lambda p: [10 + (0 if p < 0.15 or p > 0.9 else math.sin(p * 60) * 2.5), 0, 0], 26)},
        "jaw": {"rotation": K(0, [0, 0, 0], 1.0, [20, 0, 0], 1.5, [50, 0, 0], 5.8, [50, 0, 0], 6.4, [0, 0, 0])},
        "wing_r": {"rotation": wave(6.5, lambda p: [0, -10, 0 if p < 0.12 or p > 0.92 else 62 + math.sin(p * 80) * 4], 26)},
        "wing_l": {"rotation": wave(6.5, lambda p: [0, 10, 0 if p < 0.12 or p > 0.92 else -62 - math.sin(p * 80) * 4], 26)},
        "core": {"scale": K(0, [1, 1, 1], 1.5, [1.6, 1.6, 1.6], 5.8, [3.0, 3.0, 3.0], 6.1, [0.6, 0.6, 0.6], 6.5, [1, 1, 1])},
    }))
    # Correntes do Abismo: empina e crava as duas garras no chao.
    a.add("chains", 2.2, False, {
        "root": {"position": K(0, [0, 0, 0], 0.7, [0, 4, 0], 1.0, [0, -1, 0], 1.3, [0, 0, 0])},
        "body": {"rotation": K(0, [0, 0, 0], 0.7, [-34, 0, 0], 1.0, [14, 0, 0], 1.8, [10, 0, 0], 2.2, [0, 0, 0])},
        "arm_r": {"rotation": K(0, [0, 0, 0], 0.7, [-100, 0, 18], 1.0, [24, 0, -6], 1.8, [20, 0, 0], 2.2, [0, 0, 0])},
        "arm_l": {"rotation": K(0, [0, 0, 0], 0.7, [-100, 0, -18], 1.0, [24, 0, 6], 1.8, [20, 0, 0], 2.2, [0, 0, 0])},
        "forearm_r": {"rotation": K(0, [0, 0, 0], 0.7, [-40, 0, 0], 1.0, [20, 0, 0], 2.2, [0, 0, 0])},
        "forearm_l": {"rotation": K(0, [0, 0, 0], 0.7, [-40, 0, 0], 1.0, [20, 0, 0], 2.2, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.7, [-25, 0, 0], 1.0, [22, 0, 0], 2.2, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.8, [38, 0, 0], 1.6, [30, 0, 0], 2.0, [0, 0, 0])},
    })
    # Selo/Maldicao do Esquecimento: inclina a cabeca, as asas se fecham para a frente como um manto.
    a.add("curse", 2.5, False, {
        "body": {"rotation": K(0, [0, 0, 0], 0.6, [-8, 0, 0], 2.0, [-6, 0, 0], 2.5, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.6, [-20, 0, 12], 2.0, [-18, 0, 10], 2.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.6, [8, 0, 22], 2.0, [10, 0, 20], 2.5, [0, 0, 0])},
        "jaw": {"rotation": K(0, [0, 0, 0], 0.8, [18, 0, 0], 2.0, [14, 0, 0], 2.4, [0, 0, 0])},
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.6, [0, -60, 30], 2.0, [0, -55, 28], 2.5, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.6, [0, 60, -30], 2.0, [0, 55, -28], 2.5, [0, 0, 0])},
        "wing_r_mid": {"rotation": K(0, [0, 0, 0], 0.6, [0, -30, 0], 2.0, [0, -28, 0], 2.5, [0, 0, 0])},
        "wing_l_mid": {"rotation": K(0, [0, 0, 0], 0.6, [0, 30, 0], 2.0, [0, 28, 0], 2.5, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.8, [0.6, 0.6, 0.6], 1.6, [2.0, 2.0, 2.0], 2.5, [1, 1, 1])},
    })
    # Inversao do Destino: estalo rapido das asas e um giro do pescoco.
    a.add("swap", 1.8, False, {
        "wing_r": {"rotation": K(0, [0, 0, 0], 0.4, [0, -40, 70], 0.6, [0, 40, -10], 1.8, [0, 0, 0])},
        "wing_l": {"rotation": K(0, [0, 0, 0], 0.4, [0, 40, -70], 0.6, [0, -40, 10], 1.8, [0, 0, 0])},
        "neck1": {"rotation": K(0, [0, 0, 0], 0.4, [-20, -30, 0], 0.7, [-10, 30, 0], 1.2, [-5, 0, 0], 1.8, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.4, [0, 0, -15], 0.7, [0, 0, 15], 1.8, [0, 0, 0])},
        "core": {"scale": K(0, [1, 1, 1], 0.55, [2.4, 2.4, 2.4], 0.8, [1, 1, 1])},
    })
    a.save()


# =====================================================================================
#  v1.0 — Ancora das Correntes do Abismo
# =====================================================================================
CHAIN_PAL = pal(obs="#140c1e", obs_hi="#2c1c40", obs_dark="#07040b", vein="#b070e0", link="#5c5c6a", link_hi="#a2a2b0",
                link_dark="#26262e", eye="#f0d8ff", rubble="#2a2630", rubble_hi="#403a48", motif="rune")


def obsidian_paint(seed):
    """Obsidiana chorona: preto-violeta com brilho nas arestas e veios violeta que brilham (glowmask)."""
    def p(fs, c, P):
        rnd = random.Random(seed)
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    n = h01(xx + f.x, yy + f.y, seed)
                    col = P["obs"] if n < 0.7 else (P["obs_hi"] if n < 0.92 else P["obs_dark"])
                    if name == "top" or xx == 0:
                        col = lighten(col, 0.1)
                    f.set(xx, yy, col)
            if f.w * f.h >= 8:
                for _ in range(max(1, f.w * f.h // 40)):
                    x, y = rnd.randrange(f.w), rnd.randrange(f.h)
                    for _ in range(rnd.randint(2, 6)):
                        f.set(x, y, P["vein"], 225)
                        x += rnd.choice((-1, 0, 1))
                        y += rnd.choice((0, 1, 1))
    return p


def link_paint(fs, c, P):
    """Elos de corrente: blocos claros/escuros alternados a cada 2 px, com uma runa brilhando de vez em quando."""
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                k = (xx + yy) // 2 % 2
                col = P["link"] if k == 0 else P["link_dark"]
                if yy == 0 and name != "bottom":
                    col = P["link_hi"]
                f.set(xx, yy, col)
                if (xx * 7 + yy * 3) % 11 == 0 and name in ("front", "back", "top"):
                    f.set(xx, yy, P["vein"], 210)


def chain_eye_paint(fs, c, P):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                edge = xx in (0, f.w - 1) or yy in (0, f.h - 1)
                f.set(xx, yy, P["vein"] if edge else P["eye"], 220 if edge else 255)
        if f.w >= 3 and f.h >= 3:
            f.set(f.w // 2, f.h // 2, (30, 8, 40, 255), 120)


def rubble_paint(seed):
    def p(fs, c, P):
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    n = h01(xx + f.x, yy + f.y, seed)
                    col = P["rubble"] if n < 0.65 else P["rubble_hi"]
                    if name == "top" and h01(xx, yy, seed + 3) > 0.9:
                        f.set(xx, yy, P["vein"], 180)
                        continue
                    f.set(xx, yy, col if name == "top" else darken(col, 0.15))
    return p


def build_chain():
    m = Model("primordial_chain")
    m.bone("root", [0, 0, 0])
    m.bone("base", [0, 0, 0], "root")
    m.bone("shaft", [0, 3, 0], "root")
    m.bone("eye", [0, 22, 0], "shaft")
    m.bone("ring1", [0, 14, 0], "root")
    m.bone("ring2", [0, 26, 0], "root")
    m.bone("hang", [0, 13, 0], "ring1")
    # base: entulho rachado e 4 espinhos
    m.cube("base", [-8, 0, -8], [16, 3, 16], rubble_paint(501))
    for i, (x, z) in enumerate(((-8, -8), (5, -8), (-8, 5), (5, 5))):
        m.cube("base", [x, 3, z], [3, 5 + (i % 2) * 2, 3], obsidian_paint(510 + i))
    # obelisco afinando
    m.cube("shaft", [-3, 3, -3], [6, 24, 6], obsidian_paint(520))
    m.cube("shaft", [-2, 27, -2], [4, 6, 4], obsidian_paint(521))
    m.cube("shaft", [-1, 33, -1], [2, 5, 2], obsidian_paint(522))
    m.cube("eye", [-2, 20, -3.5], [4, 4, 1], chain_eye_paint)
    m.cube("eye", [-2, 20, 2.5], [4, 4, 1], chain_eye_paint)
    # anel de baixo (quadrado de elos) e o de cima, menor
    m.cube("ring1", [-7, 13, -7], [14, 2, 2], link_paint)
    m.cube("ring1", [-7, 13, 5], [14, 2, 2], link_paint)
    m.cube("ring1", [-7, 13, -5], [2, 2, 10], link_paint)
    m.cube("ring1", [5, 13, -5], [2, 2, 10], link_paint)
    m.cube("ring2", [-5, 25, -5], [10, 2, 2], link_paint)
    m.cube("ring2", [-5, 25, 3], [10, 2, 2], link_paint)
    m.cube("ring2", [-5, 25, -3], [2, 2, 6], link_paint)
    m.cube("ring2", [3, 25, -3], [2, 2, 6], link_paint)
    # elos pendurados nos cantos do anel de baixo
    for (x, z) in ((-7, -7), (6, -7), (-7, 6), (6, 6)):
        m.cube("hang", [x, 9, z], [1, 4, 1], link_paint)
        m.cube("hang", [x, 6, z], [1, 2, 1], link_paint)
    m.layout(128)
    geo = m.geo_json()
    geo["minecraft:geometry"][0]["description"].update({"visible_bounds_width": 3, "visible_bounds_height": 4,
                                                         "visible_bounds_offset": [0, 1.5, 0]})
    write_json(os.path.join(ASSETS, "geo/entity", "primordial_chain.geo.json"), geo)
    m.paint(CHAIN_PAL)
    m.save_texture("primordial_chain")


def chain_anims():
    a = Anims("primordial_chain")
    L = 2.4
    a.add("idle", L, True, {
        "ring1": {"rotation": K(0, [0, 0, 0], L / 2, [0, 180, 0], L, [0, 360, 0]),
                  "position": wave(L, lambda p: [0, sinv(p, 0.6), 0])},
        "ring2": {"rotation": K(0, [0, 0, 0], L / 2, [0, -180, 0], L, [0, -360, 0]),
                  "position": wave(L, lambda p: [0, sinv(p, 0.5, 0, 1, 0.3), 0])},
        "hang": {"rotation": wave(L, lambda p: [sinv(p, 6), 0, sinv(p, 6, 0, 1, 0.25)])},
        "eye": {"scale": wave(L, lambda p: [1.0 + 0.15 * (0.5 + 0.5 * math.sin(2 * math.pi * p * 2))] * 3)},
        "shaft": {"position": wave(L, lambda p: [0, sinv(p, 0.25, 0, 2), 0])},
    })
    a.add("rise", 0.8, False, {
        "root": {"position": K(0, [0, -38, 0], 0.55, [0, 2, 0], 0.8, [0, 0, 0]),
                 "scale": K(0, [0.6, 0.6, 0.6], 0.55, [1.08, 1.08, 1.08], 0.8, [1, 1, 1])},
        "ring1": {"rotation": K(0, [0, 0, 0], 0.8, [0, 540, 0])},
        "ring2": {"rotation": K(0, [0, 0, 0], 0.8, [0, -540, 0])},
    })
    a.save()


# =====================================================================================
#  Itens, loot e tags
# =====================================================================================
def heart_item():
    """Coracao do Dragao Primordial: coracao facetado com as 6 cores dos elementos e brilho central."""
    c = Canvas(16, 16)
    rows = [
        "................",
        "...aa.....bb....",
        "..aAAa...bBBb...",
        ".aAAAAa.bBBBBb..",
        ".aAAAAAbBBBBBb..",
        ".aAAAAAWBBBBBb..",
        ".aAAAAWWWBBBBb..",
        "..fFFFWWWCCCc...",
        "..fFFFFWCCCCc...",
        "...fFFFdCCCc....",
        "....fFdDDCc.....",
        ".....eEDDc......",
        "......eEd.......",
        ".......e........",
        "................",
        "................",
    ]
    P = {
        "a": C("#8a2a10"), "A": C("#ff6a2b"),     # fogo
        "b": C("#1a4f8a"), "B": C("#3fa9ff"),     # agua
        "c": C("#5a3a8a"), "C": C("#a070e0"),     # sombra
        "d": C("#6a4a20"), "D": C("#c08a3c"),     # terra
        "e": C("#b0a040"), "E": C("#ffe27a"),     # luz
        "f": C("#2f7a5a"), "F": C("#9ff0d0"),     # vento
        "W": C("#ffffff"),
    }
    c.from_rows(rows, P)
    c.outline(C("#1a0e14"))
    c.set(7, 6, C("#fffbe0"))
    c.set(6, 3, C("#ffd0a0"))
    c.set(10, 3, C("#d0f0ff"))
    c.save(os.path.join(ASSETS, "textures/item/primordial_dragon_heart.png"))
    write_json(os.path.join(ASSETS, "models/item/primordial_dragon_heart.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/primordial_dragon_heart"}})
    write_json(os.path.join(ASSETS, "models/item/primordial_dragon_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    write_json(os.path.join(ASSETS, "models/item/primordial_chain_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def item_pool(name, lo=1, hi=1, conditions=None):
    entry = {"type": "minecraft:item", "name": name}
    if lo != 1 or hi != 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if conditions:
        pool["conditions"] = conditions
    return pool


def build_data():
    pools = [item_pool(f"isekai_mahou:essence_{e}", 6, 10) for e in ("fire", "water", "earth", "wind", "light", "shadow")]
    pools += [item_pool("isekai_mahou:essence_arcane", 8, 14), item_pool("isekai_mahou:mana_crystal", 12, 20),
              item_pool("minecraft:nether_star", 1, 2), item_pool("minecraft:dragon_breath", 4, 8),
              item_pool("minecraft:diamond_block", 2, 4), item_pool("minecraft:netherite_ingot", 1, 2,
                                                                     [{"condition": "minecraft:killed_by_player"}])]
    write_json(os.path.join(DATA, "loot_table/entities/primordial_dragon.json"), {
        "type": "minecraft:entity", "pools": pools, "random_sequence": "isekai_mahou:entities/primordial_dragon"})
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"values": ["isekai_mahou:primordial_dragon"]})


if __name__ == "__main__":
    build_dragon()
    dragon_anims()
    build_chain()
    chain_anims()
    heart_item()
    build_data()
    print("bosses_e: assets gerados")
