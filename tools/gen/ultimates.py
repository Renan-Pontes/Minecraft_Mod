"""
Assets do modulo "ultimates" (Meteoro e as novas magias de G).  Rode da raiz:  py -I tools/gen/ultimates.py

  GeckoLib:
  - meteor             rocha incandescente irregular (blocos girados), rachaduras brilhantes (_glowmask) + animacao "spin"
  - spectral_colossus  gigante espectral (~3 blocos no modelo, o renderer escala x4 = ~12 blocos): elmo com crista,
                       ombreiras, espada longa na mao direita, escudo no antebraco esquerdo. Textura em tons de
                       cinza/branco (o renderer tinge com a cor do elemento e desenha translucido e emissivo);
                       brilho (_glowmask) nos olhos, nucleo do peito, fio da espada, runa do escudo e runas das ombreiras.
                       Animacoes: idle, spawn, despawn, slash, slam, guard.
  Texturas soltas (textures/entity/ultimates/, brancas e tingidas no renderer):
  - glow.png        32x32  brilho radial
  - fire_tail.png   32x64  rastro de fogo do meteoro (v=0 cabeca forte, v=1 cauda some)
  - sky_sigil.png   128x128 circulo runico da fenda no ceu (Chuva de Meteoros)
  - halo.png        64x64  aureola da Transcendencia
  - wing.png        64x64  asa de penas de luz (raiz na esquerda)
  Icone do efeito: textures/mob_effect/transcendence.png (18x18).

Convencoes (Bedrock/GeckoLib): frente = -Z; -X = lado DIREITO da criatura; y para cima a partir dos pes.
X negativo levanta o braco (que aponta para baixo) para a frente; Z positivo abre o braco direito (-X) para fora.
Ossos animados nunca tem rotacao inicial (rotacoes fixas so em ossos-filho nao animados).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, mix, lighten, darken  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX_ENT = os.path.join(ASSETS, "textures", "entity")
TEX_ULT = os.path.join(TEX_ENT, "ultimates")
CLEAR = (0, 0, 0, 0)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def grey(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)


# =====================================================================================
#  Modelo (box UV) — versao enxuta do gerador do bosses_d
# =====================================================================================
class Face:
    def __init__(self, model, name, x, y, w, h):
        self.m, self.name, self.x, self.y, self.w, self.h = model, name, x, y, w, h

    def set(self, fx, fy, c, glow=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, c)
            self.m.glow.set(self.x + fx, self.y + fy, c if glow else CLEAR)

    def glow_px(self, fx, fy, c):
        """Pixel brilhante (desenha na textura e na mascara)."""
        self.set(fx, fy, c, True)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

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

    def cube(self, bone, origin, size, paint, inflate=0.0):
        c = {"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size], "paint": paint,
             "inflate": inflate}
        self.cubes.append(c)
        return c

    def mirror(self, bone, origin, size, paint):
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
            c["paint"](self.faces_of(c), i)

    def save(self, bounds):
        self.layout()
        self.paint()
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
        geo = {
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
        write_json(os.path.join(ASSETS, "geo", "entity", self.ident + ".geo.json"), geo)
        self.tex.save(os.path.join(TEX_ENT, self.ident + ".png"))
        self.glow.save(os.path.join(TEX_ENT, self.ident + "_glowmask.png"))


# =====================================================================================
#  Animacoes
# =====================================================================================
def num(v):
    return round(v, 3)


def keys(points):
    return {f"{num(t)}": [num(a) for a in v] for t, v in sorted(points.items())}


def wave(length, fn, steps=12):
    return keys({length * i / steps: fn(i / steps) for i in range(steps + 1)})


def sinv(ph, amp, off=0.0, mult=1):
    return off + amp * math.sin(2 * math.pi * ph * mult)


def K(*pairs):
    return keys({pairs[i]: pairs[i + 1] for i in range(0, len(pairs), 2)})


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
        write_json(os.path.join(ASSETS, "animations", "entity", self.ident + ".animation.json"),
                   {"format_version": "1.8.0", "animations": self.anims})


# =====================================================================================
#  Meteoro
# =====================================================================================
ROCK_DARK = (38, 30, 28, 255)
ROCK_MID = (66, 52, 46, 255)
ROCK_HI = (98, 80, 70, 255)
EMBER = (150, 54, 22, 255)


def rock_paint(seed):
    def p(fs, idx):
        rnd = random.Random(seed * 31 + idx)
        for name, f in fs.items():
            for yy in range(f.h):
                for xx in range(f.w):
                    t = rnd.random()
                    c = ROCK_MID if t < 0.55 else ROCK_DARK if t < 0.85 else ROCK_HI
                    # Calor que vem de dentro: as bordas das faces ficam em brasa escura
                    edge = min(xx, yy, f.w - 1 - xx, f.h - 1 - yy)
                    if edge == 0 and rnd.random() < 0.35:
                        c = mix(c, EMBER, 0.55)
                    f.set(xx, yy, c)
            # Rachaduras: linhas quase retas que se ramificam e brilham (amarelo no meio, laranja nas pontas)
            n_cracks = max(1, (f.w * f.h) // 70)
            for _ in range(n_cracks):
                x, y = rnd.randrange(f.w), rnd.randrange(f.h)
                dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (1, -1)))
                length = rnd.randint(3, 3 + max(f.w, f.h) // 2)
                for k in range(length):
                    mid = 0.25 < k / length < 0.75
                    f.glow_px(x, y, (255, 222, 110, 255) if mid else (255, 120, 30, 255))
                    for ox, oy in ((dy, dx), (-dy, -dx)):
                        if 0 <= x + ox < f.w and 0 <= y + oy < f.h and rnd.random() < 0.5:
                            q = f.get(x + ox, y + oy)
                            if q[3] and q[0] < 200:
                                f.set(x + ox, y + oy, mix(q, EMBER, 0.65))
                    if rnd.random() < 0.3:
                        dx, dy = (dx + rnd.choice((-1, 1))) if dy else dx, dy if not dx else (dy + rnd.choice((-1, 0, 1)))
                        dx, dy = max(-1, min(1, dx)), max(-1, min(1, dy))
                        if dx == 0 and dy == 0:
                            dx = 1
                    x += dx
                    y += dy
                    if not (0 <= x < f.w and 0 <= y < f.h):
                        break
    return p


def build_meteor():
    m = Model("meteor")
    m.bone("rock", [0, 0, 0])
    m.cube("rock", [-6, -6, -6], [12, 12, 12], rock_paint(1))
    # Lascas giradas (ossos-filho com rotacao fixa) quebram a silhueta de cubo
    chunks = [
        ([25, 40, 10], [-5, -4, -7], [10, 8, 4]),
        ([-30, 15, 35], [-7, -5, -4], [4, 9, 9]),
        ([10, -35, -20], [3, -5, -4], [5, 10, 8]),
        ([45, 0, 30], [-4, 4, -5], [8, 4, 9]),
        ([-20, 60, -15], [-4, -8, -3], [8, 4, 7]),
        ([35, 25, 0], [-3, -3, 4], [7, 7, 4]),
    ]
    for i, (rot, origin, size) in enumerate(chunks):
        b = m.bone(f"chunk_{i}", [0, 0, 0], parent="rock", rotation=rot)
        m.cube(b, origin, size, rock_paint(10 + i))
    # Pedrinhas soltas nas quinas
    for i, (o, s) in enumerate([([5, 3, 3], [3, 3, 3]), ([-8, -2, 2], [3, 3, 3]), ([2, -8, -3], [3, 3, 3]),
                                ([-3, 6, 2], [3, 3, 3])]):
        m.cube("rock", o, s, rock_paint(30 + i))
    m.save((3, 3, -1.5))

    a = Anims("meteor")
    a.add("spin", 1.6, True, {
        "rock": {"rotation": wave(1.6, lambda p: [360 * p, 180 * p, 90 * math.sin(2 * math.pi * p) * 0.2], 8)},
    })
    a.save()


# =====================================================================================
#  Colosso espectral
# =====================================================================================
def spectral(base=205, edge=0.35, seed=0, runes=False, glow_faces=()):
    """Placa espectral: cinza claro com contorno, gradiente e (opcional) runas brilhantes."""
    def p(fs, idx):
        rnd = random.Random(seed * 17 + idx)
        for name, f in fs.items():
            b = base + (14 if name == "top" else -30 if name == "bottom" else -8 if name in ("left", "right") else 0)
            for yy in range(f.h):
                for xx in range(f.w):
                    v = b - (yy / max(1, f.h - 1)) * 26 + (rnd.random() - 0.5) * 10
                    if xx == 0 or yy == 0 or xx == f.w - 1 or yy == f.h - 1:
                        v = v * (1 - edge) + 255 * edge * 0.2
                    f.set(xx, yy, grey(v))
            # filete claro no topo de faces laterais (contorno "de luz")
            if name in ("front", "back", "left", "right") and f.h >= 3:
                for xx in range(f.w):
                    f.set(xx, 0, grey(245))
            if name in glow_faces:
                for yy in range(f.h):
                    for xx in range(f.w):
                        f.glow_px(xx, yy, grey(255))
            if runes and name in ("front", "left", "right", "back") and f.w >= 4 and f.h >= 4:
                for _ in range(max(1, (f.w * f.h) // 30)):
                    x0, y0 = rnd.randrange(1, f.w - 2), rnd.randrange(1, f.h - 2)
                    shape = rnd.choice([[(0, 0), (1, 0), (1, 1)], [(0, 0), (0, 1), (1, 1)], [(0, 1), (1, 0)],
                                        [(0, 0), (1, 1)], [(0, 0), (1, 0), (0, 1)]])
                    for dx, dy in shape:
                        f.glow_px(x0 + dx, y0 + dy, grey(235))
    return p


def eyes_paint(fs, idx):
    for name, f in fs.items():
        f.fill(grey(60))
        if name == "front":
            for xx in range(f.w):
                if xx not in (2, 3):
                    f.glow_px(xx, 0, grey(255))


def core_paint(fs, idx):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.glow_px(xx, yy, grey(255 if (xx + yy) % 2 == 0 else 225))


def blade_paint(fs, idx):
    """Lamina: corpo claro, fio (topo e base) brilhando, sulco central com runas."""
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                f.set(xx, yy, grey(220 - 12 * abs(yy - f.h / 2)))
        if name in ("top", "bottom"):
            for yy in range(f.h):
                for xx in range(f.w):
                    f.glow_px(xx, yy, grey(255))
        if name in ("left", "right"):
            for xx in range(f.w):
                f.glow_px(xx, 0, grey(255))
                f.glow_px(xx, f.h - 1, grey(255))
                if xx % 3 == 1 and f.h >= 4:
                    f.glow_px(xx, f.h // 2, grey(230))


def shield_paint(fs, idx):
    for name, f in fs.items():
        for yy in range(f.h):
            for xx in range(f.w):
                ring = max(abs(xx - (f.w - 1) / 2), abs(yy - (f.h - 1) / 2))
                v = 210 - ring * 3
                f.set(xx, yy, grey(v))
        if name in ("left", "right") and f.w >= 8:
            cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
            for yy in range(f.h):
                for xx in range(f.w):
                    d = math.hypot(xx - cx, yy - cy)
                    if abs(d - f.w * 0.33) < 0.6 or (abs(xx - cx) < 0.6 and d < f.w * 0.33) or \
                            (abs(yy - cy) < 0.6 and d < f.w * 0.33):
                        f.glow_px(xx, yy, grey(250))
            for xx in range(f.w):
                f.set(xx, 0, grey(240))
                f.set(xx, f.h - 1, grey(240))


def build_colossus():
    m = Model("spectral_colossus")
    m.bone("root", [0, 0, 0])
    m.bone("hips", [0, 20, 0], "root")
    m.cube("hips", [-5, 18, -3], [10, 5, 6], spectral(200, seed=1))
    m.cube("hips", [-4, 12, -4], [8, 7, 1], spectral(215, seed=2, runes=True))
    m.cube("hips", [-4, 12, 3], [8, 7, 1], spectral(190, seed=3))

    for side, sx in (("r", -1), ("l", 1)):
        leg = m.bone(f"leg_{side}", [3 * sx, 19, 0], "hips")
        def add(origin, size, paint):
            if sx < 0:
                m.cube(leg, origin, size, paint)
            else:
                m.mirror(leg, origin, size, paint)
        add([-5.5, 10, -2.5], [5, 9, 5], spectral(200, seed=10 + sx))
        add([-5.5, 7, -3.5], [5, 4, 1], spectral(230, seed=12 + sx, runes=True))
        add([-5, 3, -2], [4, 7, 4], spectral(190, seed=14 + sx))
        add([-6, 0, -4], [6, 3, 7], spectral(175, seed=16 + sx))

    torso = m.bone("torso", [0, 22, 0], "hips")
    m.cube(torso, [-5, 22, -3], [10, 6, 6], spectral(195, seed=20))
    m.cube(torso, [-8, 28, -4.5], [16, 9, 9], spectral(215, seed=21, runes=True))
    m.cube(torso, [-1.5, 31, -5.5], [3, 3, 1], core_paint)
    m.cube(torso, [-1, 26, 4.5], [2, 11, 2], spectral(230, seed=22, runes=True))
    m.cube(torso, [-2, 37, -2], [4, 2, 4], spectral(180, seed=23))

    head = m.bone("head", [0, 38, 0], torso)
    m.cube(head, [-4, 38, -4], [8, 8, 8], spectral(220, seed=30))
    m.cube(head, [-3, 41, -4.6], [6, 1, 1], eyes_paint)
    m.cube(head, [-0.5, 45, -4], [1, 4, 9], spectral(240, seed=31, runes=True))
    m.cube(head, [-5, 44, -1], [1, 4, 2], spectral(235, seed=32))
    m.cube(head, [4, 44, -1], [1, 4, 2], spectral(235, seed=33))

    # Braco direito com a espada (lamina para a frente, -Z)
    arm_r = m.bone("arm_r", [-8, 35, 0], torso)
    m.cube(arm_r, [-13, 33, -4], [7, 5, 8], spectral(225, seed=40, runes=True))
    m.cube(arm_r, [-12, 26, -2.5], [5, 8, 5], spectral(200, seed=41))
    fore_r = m.bone("forearm_r", [-9.5, 26, 0], arm_r)
    m.cube(fore_r, [-12, 18, -3], [5, 8, 6], spectral(210, seed=42, runes=True))
    m.cube(fore_r, [-11.5, 15, -2.5], [4, 3, 5], spectral(185, seed=43))
    sword = m.bone("sword", [-9.5, 16.5, 0], fore_r)
    m.cube(sword, [-10.5, 15.5, -1], [2, 2, 3], spectral(170, seed=50))
    m.cube(sword, [-12.5, 14.5, -5.5], [6, 4, 1], spectral(235, seed=51, runes=True))
    m.cube(sword, [-10, 14.5, -27.5], [1, 4, 22], blade_paint)
    m.cube(sword, [-10, 15, -30.5], [1, 3, 3], blade_paint)

    # Braco esquerdo com o escudo
    arm_l = m.bone("arm_l", [8, 35, 0], torso)
    m.cube(arm_l, [6, 33, -4], [7, 5, 8], spectral(225, seed=60, runes=True))
    m.cube(arm_l, [7, 26, -2.5], [5, 8, 5], spectral(200, seed=61))
    fore_l = m.bone("forearm_l", [9.5, 26, 0], arm_l)
    m.cube(fore_l, [7, 18, -3], [5, 8, 6], spectral(210, seed=62, runes=True))
    m.cube(fore_l, [7.5, 15, -2.5], [4, 3, 5], spectral(185, seed=63))
    shield = m.bone("shield", [12, 22, 0], fore_l)
    m.cube(shield, [12, 15.5, -5.5], [1, 12, 11], shield_paint)
    m.cube(shield, [13, 19.5, -2], [1, 4, 4], core_paint)

    m.save((6, 14, 6))
    colossus_anims()


def colossus_anims():
    a = Anims("spectral_colossus")
    L = 4.0
    idle = {
        "root": {"position": wave(L, lambda p: [0, sinv(p, 0.5), 0])},
        "torso": {"rotation": wave(L, lambda p: [sinv(p, 1.5, 3), sinv(p, 2, mult=1) * 0.5, 0])},
        "head": {"rotation": wave(L, lambda p: [sinv(p, 1.5, -3), sinv(p, 4, mult=1), 0])},
        "arm_r": {"rotation": wave(L, lambda p: [sinv(p, 3, -28), 0, 6])},
        "forearm_r": {"rotation": wave(L, lambda p: [sinv(p, 2, -10), 0, 0])},
        "sword": {"rotation": wave(L, lambda p: [sinv(p, 2, 50), 0, 0])},
        "arm_l": {"rotation": wave(L, lambda p: [sinv(p, 3, -18, 1), 0, -8])},
        "forearm_l": {"rotation": wave(L, lambda p: [sinv(p, 2, -25), 0, 0])},
        "leg_r": {"rotation": wave(L, lambda p: [sinv(p, 1.5, -3), 0, 2])},
        "leg_l": {"rotation": wave(L, lambda p: [sinv(p, 1.5, 3), 0, -2])},
    }
    a.add("idle", L, True, idle)

    # Surge do chao: escala e sobe, curvado, depois se ergue com os bracos abertos
    a.add("spawn", 1.5, False, {
        "root": {"scale": K(0.0, [0.15, 0.15, 0.15], 0.6, [0.8, 0.8, 0.8], 1.0, [1.05, 1.05, 1.05], 1.5, [1, 1, 1]),
                 "position": K(0.0, [0, -26, 0], 0.7, [0, -6, 0], 1.1, [0, 1, 0], 1.5, [0, 0, 0])},
        "torso": {"rotation": K(0.0, [40, 0, 0], 0.8, [25, 0, 0], 1.2, [-8, 0, 0], 1.5, [3, 0, 0])},
        "head": {"rotation": K(0.0, [30, 0, 0], 0.9, [10, 0, 0], 1.25, [-15, 0, 0], 1.5, [-3, 0, 0])},
        "arm_r": {"rotation": K(0.0, [-10, 0, 10], 1.0, [-40, 0, 55], 1.3, [-60, 0, 40], 1.5, [-28, 0, 6])},
        "arm_l": {"rotation": K(0.0, [-10, 0, -10], 1.0, [-40, 0, -55], 1.3, [-60, 0, -40], 1.5, [-18, 0, -8])},
        "sword": {"rotation": K(0.0, [80, 0, 0], 1.5, [50, 0, 0])},
    })
    # Desfaz-se: encolhe e afunda, curvando-se
    a.add("despawn", 1.5, "hold", {
        "root": {"scale": K(0.0, [1, 1, 1], 0.5, [1.04, 1.04, 1.04], 1.5, [0.1, 0.1, 0.1]),
                 "position": K(0.0, [0, 0, 0], 1.5, [0, -22, 0])},
        "torso": {"rotation": K(0.0, [3, 0, 0], 1.5, [40, 0, 0])},
        "head": {"rotation": K(0.0, [-3, 0, 0], 1.5, [30, 0, 0])},
        "arm_r": {"rotation": K(0.0, [-28, 0, 6], 1.5, [-5, 0, 20])},
        "arm_l": {"rotation": K(0.0, [-18, 0, -8], 1.5, [-5, 0, -20])},
    })
    # Corte em arco: ergue a espada por cima do ombro direito e varre da direita para a esquerda (golpe aos 0,5 s)
    a.add("slash", 1.2, False, {
        "torso": {"rotation": K(0.0, [3, 0, 0], 0.35, [-6, 38, 0], 0.5, [8, -10, 0], 0.62, [12, -42, 0],
                                0.85, [8, -30, 0], 1.2, [3, 0, 0])},
        "head": {"rotation": K(0.0, [-3, 0, 0], 0.35, [-5, -25, 0], 0.62, [0, 25, 0], 1.2, [-3, 0, 0])},
        "arm_r": {"rotation": K(0.0, [-28, 0, 6], 0.35, [-150, 0, 55], 0.5, [-95, 0, 10], 0.62, [-70, 0, -45],
                                0.85, [-45, 0, -25], 1.2, [-28, 0, 6])},
        "forearm_r": {"rotation": K(0.0, [-10, 0, 0], 0.35, [-30, 0, 0], 0.62, [-5, 0, 0], 1.2, [-10, 0, 0])},
        "sword": {"rotation": K(0.0, [50, 0, 0], 0.35, [20, 0, 0], 0.62, [70, 0, 0], 1.2, [50, 0, 0])},
        "arm_l": {"rotation": K(0.0, [-18, 0, -8], 0.35, [-30, 0, -35], 0.62, [-10, 0, -15], 1.2, [-18, 0, -8])},
        "leg_r": {"rotation": K(0.0, [0, 0, 2], 0.5, [-14, 0, 6], 0.85, [-8, 0, 3], 1.2, [0, 0, 2])},
        "leg_l": {"rotation": K(0.0, [0, 0, -2], 0.5, [10, 0, -4], 1.2, [0, 0, -2])},
    })
    # Pisao + golpe duplo no chao (impacto aos 0,8 s)
    a.add("slam", 1.4, False, {
        "root": {"position": K(0.0, [0, 0, 0], 0.4, [0, 2.5, 0], 0.8, [0, -2, 0], 1.0, [0, -1, 0], 1.4, [0, 0, 0])},
        "torso": {"rotation": K(0.0, [3, 0, 0], 0.4, [-14, 0, 0], 0.8, [28, 0, 0], 1.0, [25, 0, 0], 1.4, [3, 0, 0])},
        "head": {"rotation": K(0.0, [-3, 0, 0], 0.4, [-15, 0, 0], 0.8, [-10, 0, 0], 1.4, [-3, 0, 0])},
        "arm_r": {"rotation": K(0.0, [-28, 0, 6], 0.4, [-172, 0, 12], 0.8, [-55, 0, 4], 1.0, [-50, 0, 4], 1.4, [-28, 0, 6])},
        "arm_l": {"rotation": K(0.0, [-18, 0, -8], 0.4, [-172, 0, -12], 0.8, [-55, 0, -4], 1.0, [-50, 0, -4], 1.4, [-18, 0, -8])},
        "sword": {"rotation": K(0.0, [50, 0, 0], 0.4, [0, 0, 0], 0.8, [90, 0, 0], 1.4, [50, 0, 0])},
        "leg_r": {"rotation": K(0.0, [0, 0, 2], 0.4, [-40, 0, 4], 0.75, [-10, 0, 2], 0.8, [4, 0, 2], 1.4, [0, 0, 2])},
        "leg_l": {"rotation": K(0.0, [0, 0, -2], 0.4, [8, 0, -2], 0.8, [6, 0, -2], 1.4, [0, 0, -2])},
    })
    # Guarda: o escudo vem para a frente do dono
    a.add("guard", 0.8, False, {
        "torso": {"rotation": K(0.0, [3, 0, 0], 0.15, [6, -18, 0], 0.55, [6, -18, 0], 0.8, [3, 0, 0])},
        "arm_l": {"rotation": K(0.0, [-18, 0, -8], 0.15, [-85, 30, 10], 0.55, [-85, 30, 10], 0.8, [-18, 0, -8])},
        "forearm_l": {"rotation": K(0.0, [-25, 0, 0], 0.15, [-45, 0, 0], 0.55, [-45, 0, 0], 0.8, [-25, 0, 0])},
        "shield": {"scale": K(0.0, [1, 1, 1], 0.15, [1.15, 1.15, 1.15], 0.3, [1, 1, 1])},
    })
    a.save()


# =====================================================================================
#  Texturas soltas
# =====================================================================================
def glow_tex():
    S = 32
    c = Canvas(S, S)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - S / 2, y + 0.5 - S / 2) / (S / 2)
            a = clamp(1 - d) ** 2.2
            c.set(x, y, (255, 255, 255, int(a * 255)))
    c.save(os.path.join(TEX_ULT, "glow.png"))


class PNoise:
    def __init__(self, px, py, seed):
        rnd = random.Random(seed)
        self.px, self.py = px, py
        self.g = [[rnd.random() for _ in range(px)] for _ in range(py)]

    def value(self, u, v):
        x, y = u * self.px, v * self.py
        x0, y0 = int(math.floor(x)) % self.px, int(math.floor(y)) % self.py
        x1, y1 = (x0 + 1) % self.px, (y0 + 1) % self.py
        fx, fy = x - math.floor(x), y - math.floor(y)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.g[y0][x0] + (self.g[y0][x1] - self.g[y0][x0]) * sx
        b = self.g[y1][x0] + (self.g[y1][x1] - self.g[y1][x0]) * sx
        return a + (b - a) * sy


def fire_tail():
    W, H = 32, 64
    c = Canvas(W, H)
    layers = [PNoise(4, 6, 3), PNoise(8, 12, 4), PNoise(16, 24, 5)]
    for y in range(H):
        v = y / (H - 1)
        for x in range(W):
            u = x / (W - 1)
            n = sum(l.value(u, v) * w for l, w in zip(layers, (0.55, 0.3, 0.15)))
            across = 1 - abs(u - 0.5) * 2               # 1 no meio, 0 nas bordas
            width = 1 - v * 0.55                         # afina para a cauda
            core = clamp((across - (1 - width) * 0.85) / max(0.05, width)) ** 0.7
            a = clamp(core * 1.35 - v * 0.8 + (n - 0.5) * 0.9)
            a = a ** 1.2
            heat = clamp(core * (1 - v) * 1.4 + (n - 0.5) * 0.3)
            col = (255, int(150 + 105 * heat), int(80 + 175 * heat ** 2))
            c.set(x, y, (col[0], col[1], col[2], int(a * 255)))
    c.save(os.path.join(TEX_ULT, "fire_tail.png"))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def sky_sigil():
    S = 128
    c = Canvas(S, S)
    cx = cy = S / 2
    segs = []
    # Estrela de 7 pontas (heptagrama) e raios para fora: o "olho" da fenda por onde as rochas caem
    pts = [(cx + math.cos(math.radians(90 + 360 * i / 7)) * 44, cy - math.sin(math.radians(90 + 360 * i / 7)) * 44)
           for i in range(7)]
    for i in range(7):
        segs.append((pts[i], pts[(i + 3) % 7], 1.0))
    for i in range(28):
        a = math.radians(360 * i / 28)
        r0, r1 = (48, 56) if i % 2 == 0 else (50, 54)
        segs.append(((cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1), 0.6))
    rnd = random.Random(7)
    for i in range(14):
        a0 = math.radians(360 * i / 14 + 12)
        rad = (math.cos(a0), math.sin(a0))
        tang = (-rad[1], rad[0])
        ox, oy = cx + rad[0] * 60, cy + rad[1] * 60
        shape = rnd.choice([[(-2, -1.5), (0, 1.5), (2, -1.5)], [(-2, 1.5), (-2, -1.5), (2, -1.5)],
                            [(-2, 0), (2, 0), (0, 1.5)], [(-1.5, -1.5), (1.5, 1.5), (-1.5, 1.5)]])
        for j in range(len(shape) - 1):
            (u0, v0), (u1, v1) = shape[j], shape[j + 1]
            segs.append(((ox + tang[0] * u0 + rad[0] * v0, oy + tang[1] * u0 + rad[1] * v0),
                         (ox + tang[0] * u1 + rad[0] * v1, oy + tang[1] * u1 + rad[1] * v1), 0.45))
    rings = [(63, 1.1), (57, 0.7), (46.5, 0.9), (22, 0.8), (12, 0.6)]
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > 64.5:
                continue
            a = 0.0
            for r, w in rings:
                a = max(a, clamp(w + 0.5 - abs(d - r)))
            for (p0, p1, w) in segs:
                if a >= 1.0:
                    break
                a = max(a, clamp(w + 0.5 - seg_dist(px, py, p0[0], p0[1], p1[0], p1[1])))
            # miolo: redemoinho brilhante (de onde saem as rochas)
            swirl = 0.0
            if d < 22:
                ang = math.atan2(py - cy, px - cx)
                swirl = (0.5 + 0.5 * math.sin(ang * 3 + d * 0.5)) * (1 - d / 22) * 0.8
            fill = 0.08 + (0.08 if 46 < d < 57 else 0)
            alpha = clamp(max(a, fill, swirl))
            c.set(x, y, (255, 255, 255, int(alpha * 255)))
    c.save(os.path.join(TEX_ULT, "sky_sigil.png"))


def halo():
    S = 64
    c = Canvas(S, S)
    cx = cy = S / 2
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            a = clamp(1.8 + 0.5 - abs(d - 26)) * 1.0
            a = max(a, clamp(0.8 + 0.5 - abs(d - 21)) * 0.8)
            # pontas de luz (raios) entre os aneis
            ang = math.atan2(py - cy, px - cx)
            spokes = (0.5 + 0.5 * math.cos(ang * 12)) ** 8
            if 21 < d < 26:
                a = max(a, spokes * 0.9)
            # brilho suave em volta do anel
            a = max(a, clamp(1 - abs(d - 25) / 7) ** 2 * 0.45)
            c.set(x, y, (255, 255, 255, int(clamp(a) * 255)))
    c.save(os.path.join(TEX_ULT, "halo.png"))


def wing():
    """Asa de luz: raiz no canto esquerdo (x=0, y~14); penas longas que descem para a direita."""
    S = 64
    c = Canvas(S, S)
    feathers = []
    # (angulo em graus a partir da horizontal para baixo, comprimento, largura)
    for i in range(9):
        ang = -18 + i * 11
        length = 60 - i * 3.2
        feathers.append((math.radians(ang), length, 3.6 - i * 0.12))
    rx, ry = 2.0, 14.0
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            a = 0.0
            edge = 0.0
            for (ang, length, w) in feathers:
                ex, ey = rx + math.cos(ang) * length, ry + math.sin(ang) * length
                d = seg_dist(px, py, rx, ry, ex, ey)
                t = clamp(((px - rx) * (ex - rx) + (py - ry) * (ey - ry)) / (length * length))
                ww = w * (0.55 + 0.45 * math.sin(math.pi * min(1.0, t * 1.15)))
                cov = clamp(ww + 0.5 - d)
                if cov > 0:
                    a = max(a, cov * (0.45 + 0.55 * t))
                    if abs(d - ww) < 0.8:
                        edge = max(edge, 1 - abs(d - ww) / 0.8)
            # arco superior (o "osso" da asa) mais brilhante
            dbone = seg_dist(px, py, rx, ry, rx + 46, ry - 12)
            a = max(a, clamp(1.6 + 0.5 - dbone))
            alpha = clamp(max(a * 0.85, edge * 0.95))
            c.set(x, y, (255, 255, 255, int(alpha * 255)))
    c.save(os.path.join(TEX_ULT, "wing.png"))


def effect_icon():
    S = 18
    c = Canvas(S, S)
    gold = (255, 226, 120, 255)
    white = (255, 255, 255, 255)
    violet = (185, 140, 255, 255)
    # aureola
    for x in range(5, 13):
        c.set(x, 2, gold)
    c.set(4, 3, gold); c.set(13, 3, gold)
    for x in range(5, 13):
        c.set(x, 4, darken(gold, 0.25))
    # figura com asas
    for y in range(6, 15):
        c.set(8, y, white); c.set(9, y, white)
    c.set(8, 5, white); c.set(9, 5, white)
    for i in range(6):
        c.set(7 - i, 7 + i // 2, lighten(violet, 0.2))
        c.set(10 + i, 7 + i // 2, lighten(violet, 0.2))
        c.set(7 - i, 8 + i // 2, violet)
        c.set(10 + i, 8 + i // 2, violet)
    for y in range(9, 13):
        c.set(2, y, darken(violet, 0.2)); c.set(15, y, darken(violet, 0.2))
    c.set(8, 16, gold); c.set(9, 16, gold)
    c.save(os.path.join(ASSETS, "textures", "mob_effect", "transcendence.png"))


def main():
    os.makedirs(TEX_ULT, exist_ok=True)
    build_meteor()
    build_colossus()
    glow_tex()
    fire_tail()
    sky_sigil()
    halo()
    wing()
    effect_icon()
    print("ultimates: assets gerados")


if __name__ == "__main__":
    main()
