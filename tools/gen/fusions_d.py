"""
Gerador de assets do modulo fusions_d (Misturas D, v0.8). Rodar da raiz do projeto:  py -I tools/gen/fusions_d.py

  - textures/entity/fd_white.png                     branco (base dos quads translucidos coloridos)
  - textures/entity/fd_black_flame.png               labaredas negras/carmesim, repetivel (Pilar de Chamas Negras)
  - textures/entity/fd_kraken_tentacle.png (+_glow)  tentaculo do Kraken (UV do KrakenTentacleModel)
  - geo/animations/textures fd_abyssal_maw           Bocarra Abissal (GeckoLib): anel de gengivas, 4 mandibulas com
                                                     presas, garganta e olhos que brilham
  - textures/mob_effect/fd_soul_burn.png             icone do Queimar Alma
  - data damage_type fd_dimension_slash + tag bypasses_armor (merge)

Convencao GeckoLib (ver bosses_d.py): rotacao X positiva no arquivo inclina o topo de um osso vertical para -Z.
As mandibulas sao modeladas no lado -Z (norte) e giradas para os 4 lados por ossos-pai ESTATICOS; os ossos
animados (jaw_*) nao tem rotacao inicial.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, mix as _mix, lighten, darken  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/fusions_d")
CLEAR = (0, 0, 0, 0)


def H(h, a=255):
    """Cor RGBA a partir de 0xRRGGBB (ou tupla pronta)."""
    if isinstance(h, tuple):
        return h
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def mix(c1, c2, t):
    """Mistura com t limitado a 0..1 (nunca sai do intervalo de cor)."""
    return _mix(c1, c2, max(0.0, min(1.0, t)))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def jitter(c, rnd, amount):
    r = (rnd.random() - 0.5) * 2 * amount
    return lighten(c, r) if r > 0 else darken(c, -r)


# =====================================================================================
#  Texturas simples
# =====================================================================================
def white():
    c = Canvas(16, 16, (255, 255, 255, 255))
    c.save(os.path.join(ASSETS, "textures/entity/fd_white.png"))


def black_flame():
    """Labaredas que se repetem nos dois eixos (a textura rola no cilindro)."""
    W, Hh = 32, 64
    c = Canvas(W, Hh)
    tau = math.pi * 2
    for y in range(Hh):
        for x in range(W):
            fx, fy = x / W, y / Hh
            v = math.sin(tau * (fx * 3) + 2.4 * math.sin(tau * fy * 2 + tau * fx)) * 0.5 + 0.5
            v = 0.7 * v + 0.3 * (math.sin(tau * (fx * 5 + fy * 3)) * 0.5 + 0.5)
            if v > 0.82:
                col = H(0xE0303E, 230)
            elif v > 0.7:
                col = H(0x7A0A18, 235)
            elif v > 0.45:
                col = H(0x0A0206, 245)
            elif v > 0.3:
                col = H(0x1E0408, 170)
            else:
                col = H(0x080104, 60)
            c.set(x, y, col)
    c.save(os.path.join(ASSETS, "textures/entity/fd_black_flame.png"))


def soul_burn_icon():
    c = Canvas(18, 18)
    rnd = random.Random(5)
    # Chama azul em forma de gota, mais clara no centro
    for y in range(18):
        for x in range(18):
            dx = (x - 8.5) / 6.0
            t = (y - 2) / 14.0
            if t < 0 or t > 1:
                continue
            half = math.sin(t * math.pi * 0.85 + 0.15) * (0.35 + 0.65 * t)
            wob = 0.12 * math.sin(y * 1.3)
            if abs(dx + wob * (1 - t)) <= half:
                k = 1 - abs(dx) / max(half, 0.01)
                col = mix(H(0x1A4FB8), H(0x8FF0FF), k * 0.8 + 0.2 * t)
                c.set(x, y, jitter(col, rnd, 0.06))
    # "Rosto" da alma: dois olhos e boca escuros
    for (x, y) in ((7, 11), (10, 11), (8, 14), (9, 14)):
        c.set(x, y, H(0x061030))
    c.outline(H(0x0A1A40))
    c.save(os.path.join(ASSETS, "textures/mob_effect/fd_soul_burn.png"))


# =====================================================================================
#  Tentaculo do Kraken (layout = KrakenTentacleModel.TEX / SIZE / LEN)
# =====================================================================================
SIZE = [12, 11, 10, 9, 8, 7, 6, 5, 4, 3]
TEX = [(0, 0), (48, 0), (0, 20), (40, 20), (76, 20), (0, 40), (28, 40), (108, 20), (104, 0), (92, 0)]
LEN = 8


def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def kraken():
    tex = Canvas(128, 64)
    glow = Canvas(128, 64)
    rnd = random.Random(41)
    skin, belly, back = H(0x2C3F86), H(0xC98AAE), H(0x172552)
    for i, s in enumerate(SIZE):
        u, v = TEX[i]
        tip = i / (len(SIZE) - 1)
        for name, (x0, y0, w, h) in box_faces(u, v, s, LEN, s).items():
            base = {"front": belly, "back": back}.get(name, skin)
            base = mix(base, H(0x3A2A6A) if name != "front" else H(0xE0A8C8), tip * 0.35)
            for yy in range(h):
                for xx in range(w):
                    col = base
                    if name in ("left", "right", "back") and h > 2:
                        col = darken(col, 0.15 * (yy / max(1, h - 1)))
                    tex.set(x0 + xx, y0 + yy, jitter(col, rnd, 0.07))
            if name == "front" and w >= 3:
                # Ventosas: aneis claros com centro escuro, em fileira
                step = 4 if w >= 6 else 3
                for cy in range(1, h - 1, step):
                    cx = w // 2
                    for (ox, oy) in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
                        if 0 <= cx + ox < w and 0 <= cy + oy < h:
                            tex.set(x0 + cx + ox, y0 + cy + oy, H(0xF2C6DC))
                    tex.set(x0 + cx, y0 + cy, H(0x6A2848))
                    if w >= 8:
                        for sx in (1, w - 2):
                            if cy + 2 < h:
                                tex.set(x0 + sx, y0 + cy + 2, H(0xE8B4CC))
            if name in ("left", "right", "back"):
                # Pintas bioluminescentes (camada que brilha)
                for _ in range(max(1, w * h // 14)):
                    xx, yy = rnd.randrange(w), rnd.randrange(h)
                    c = H(0x5FF8FF) if rnd.random() < 0.7 else H(0xB070FF)
                    tex.set(x0 + xx, y0 + yy, c)
                    glow.set(x0 + xx, y0 + yy, c)
    tex.save(os.path.join(ASSETS, "textures/entity/fd_kraken_tentacle.png"))
    glow.save(os.path.join(ASSETS, "textures/entity/fd_kraken_tentacle_glow.png"))


# =====================================================================================
#  Bocarra Abissal (GeckoLib) — box UV empacotada + pintura por face
# =====================================================================================
class Face:
    def __init__(self, m, x, y, w, h):
        self.m, self.x, self.y, self.w, self.h = m, x, y, w, h

    def set(self, fx, fy, c, g=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, c)
            if g:
                self.m.glow.set(self.x + fx, self.y + fy, c)

    def fill(self, c, rnd, noise=0.07, grad=0.0):
        for yy in range(self.h):
            for xx in range(self.w):
                col = darken(c, grad * yy / max(1, self.h - 1)) if grad else c
                self.set(xx, yy, jitter(col, rnd, noise))


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

    def cube(self, bone, origin, size, paint, rotation=None, pivot=None, inflate=0.0):
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "rotation": rotation, "pivot": pivot, "inflate": inflate})

    def layout(self):
        items = []
        for i, c in enumerate(self.cubes):
            w, h, d = c["size"]
            items.append((i, 2 * (d + w), d + h))
        order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
        for W in (64, 128, 256):
            x = y = row = 0
            pos = {}
            ok = True
            for i, rw, rh in order:
                if rw > W:
                    ok = False
                    break
                if x + rw > W:
                    x, y, row = 0, y + row, 0
                pos[i] = (x, y)
                x += rw
                row = max(row, rh)
            if not ok:
                continue
            hh = 16
            while hh < y + row:
                hh *= 2
            if hh <= W:
                self.tw, self.th = W, hh
                for i, c in enumerate(self.cubes):
                    c["uv"] = pos[i]
                return
        raise RuntimeError("modelo grande demais")

    def faces(self, c):
        u, v = c["uv"]
        w, h, d = c["size"]
        return {
            "top": Face(self, u + d, v, w, d), "bottom": Face(self, u + d + w, v, w, d),
            "right": Face(self, u, v + d, d, h), "front": Face(self, u + d, v + d, w, h),
            "left": Face(self, u + d + w, v + d, d, h), "back": Face(self, u + 2 * d + w, v + d, w, h),
        }

    def save(self):
        self.layout()
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            c["paint"](self.faces(c), random.Random(100 + i))
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
                if c["rotation"]:
                    cj["rotation"] = c["rotation"]
                    cj["pivot"] = c["pivot"] or c["origin"]
                cubes.append(cj)
            if cubes:
                bb["cubes"] = cubes
            bones.append(bb)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": 6, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones}]}
        write_json(os.path.join(ASSETS, "geo/entity", self.ident + ".geo.json"), geo)
        self.tex.save(os.path.join(ASSETS, "textures/entity", self.ident + ".png"))
        self.glow.save(os.path.join(ASSETS, "textures/entity", self.ident + "_glowmask.png"))


FLESH, GUM, SCALE, SCALE_D, TOOTH, THROAT = H(0x8A3458), H(0x3A2A66), H(0x1E3466), H(0x101C3C), H(0xEDE4CC), H(0x07040C)


def paint_gum(fs, rnd):
    for n, f in fs.items():
        base = {"top": lighten(GUM, 0.15), "bottom": darken(GUM, 0.3), "back": FLESH}.get(n, GUM)
        f.fill(base, rnd, 0.08, 0.2 if n in ("front", "left", "right") else 0)
    # Verrugas brilhantes na face de fora
    f = fs["front"]
    for _ in range(3):
        f.set(rnd.randrange(f.w), rnd.randrange(f.h), H(0x55E8FF), True)


def paint_throat(fs, rnd):
    for n, f in fs.items():
        f.fill(THROAT if n == "top" else darken(FLESH, 0.6), rnd, 0.05)
    f = fs["top"]
    # Brilho fundo no centro da garganta
    cx, cy = f.w // 2, f.h // 2
    for yy in range(f.h):
        for xx in range(f.w):
            d = math.hypot(xx - cx, yy - cy)
            if d < 3:
                f.set(xx, yy, H(0x2A60C0) if d < 1.6 else H(0x163070), True)


def paint_jaw(fs, rnd):
    for n, f in fs.items():
        if n == "back":       # face de dentro (+Z, para o centro): carne
            f.fill(FLESH, rnd, 0.09, 0.25)
            for yy in range(1, f.h, 3):
                for xx in range(f.w):
                    if rnd.random() < 0.3:
                        f.set(xx, yy, darken(FLESH, 0.25))
        elif n == "front":    # face de fora: escamas
            f.fill(SCALE, rnd, 0.06)
            for yy in range(f.h):
                for xx in range(f.w):
                    if (xx + (yy // 2) % 2) % 3 == 0 and yy % 2 == 0:
                        f.set(xx, yy, SCALE_D)
            for yy in range(2, f.h - 2, 5):
                f.set(f.w // 2, yy, H(0x5FF8FF), True)
        else:
            f.fill(SCALE_D if n != "top" else SCALE, rnd, 0.06)


def paint_tooth(fs, rnd):
    for n, f in fs.items():
        f.fill(TOOTH if n != "bottom" else darken(TOOTH, 0.2), rnd, 0.05)
    f = fs["back"]
    f.set(0, 0, H(0xFFFFFF))


def paint_eye(fs, rnd):
    for n, f in fs.items():
        f.fill(H(0x101828), rnd, 0.03)
    f = fs["front"]
    for yy in range(f.h):
        for xx in range(f.w):
            f.set(xx, yy, H(0x7FFFF0), True)
    f.set(f.w // 2, f.h // 2, H(0x0A2030), False)


def maw():
    m = Model("fd_abyssal_maw")
    m.bone("root", (0, 0, 0))
    # Garganta escura e anel de gengivas (8 blocos girados em volta)
    m.cube("root", (-11, -2, -11), (22, 3, 22), paint_throat)
    for k in range(8):
        m.cube("root", (-6, 0, -18), (12, 7, 6), paint_gum, rotation=[0, k * 45 + 22.5, 0], pivot=[0, 0, 0])
    # Olhos no anel (entre as mandíbulas)
    for k in range(4):
        m.cube("root", (-1.5, 4, -19), (3, 2, 1), paint_eye, rotation=[0, k * 90 + 22.5, 0], pivot=[0, 0, 0])
    # 4 mandíbulas: pai estático girado, filho animado sem rotação inicial
    for side, ang in (("n", 0), ("e", -90), ("s", 180), ("w", 90)):
        m.bone("arm_" + side, (0, 0, 0), parent="root", rotation=[0, ang, 0])
        jaw = m.bone("jaw_" + side, (0, 5, -13), parent="arm_" + side)
        m.cube(jaw, (-7, 5, -16), (14, 20, 4), paint_jaw)
        m.cube(jaw, (-5, 25, -15), (10, 6, 3), paint_jaw)
        m.cube(jaw, (-2, 31, -14), (4, 4, 2), paint_jaw)
        # Presas na face de dentro, em duas fileiras
        for row, (y, n) in enumerate(((9, 4), (17, 3), (26, 2))):
            for t in range(n):
                x = -1 if n == 1 else round(-6 + t * 10 / (n - 1))
                m.cube(jaw, (x, y, -12), (2, 4 - row, 4 - row), paint_tooth)
    m.save()


def kf(pairs):
    return {("%.2f" % t).rstrip("0").rstrip(".") if t else "0": {"vector": v} for t, v in pairs}


def jaws(pairs):
    out = {}
    for s in "nesw":
        out["jaw_" + s] = {"rotation": kf([(t, [a, 0, 0]) for t, a in pairs])}
    return out


def maw_anims():
    anims = {}
    em = jaws([(0, 10), (0.4, 30), (0.7, 45)])
    em["root"] = {"position": kf([(0, [0, -56, 0]), (0.45, [0, 4, 0]), (0.7, [0, 0, 0])])}
    anims["emerge"] = {"loop": False, "animation_length": 0.7, "bones": em}
    op = jaws([(0, 45), (0.5, 52), (1.0, 45)])
    op["root"] = {"scale": kf([(0, [1, 1, 1]), (0.5, [1.04, 0.97, 1.04]), (1.0, [1, 1, 1])])}
    anims["open"] = {"loop": True, "animation_length": 1.0, "bones": op}
    # Fechada: X ~ -28 encosta as pontas das 4 mandibulas (30 px de altura, base a 13 px do centro) numa cupula
    bi = jaws([(0, 50), (0.12, -34), (0.3, -28)])
    bi["root"] = {"position": kf([(0, [0, 0, 0]), (0.12, [0, -3, 0]), (0.3, [0, 0, 0])])}
    anims["bite"] = {"loop": False, "animation_length": 0.3, "bones": bi}
    ch = jaws([(0, -28), (0.25, -16), (0.5, -28)])
    ch["root"] = {"position": kf([(0, [0, 0, 0]), (0.25, [0, 1.5, 0]), (0.5, [0, 0, 0])]),
                  "scale": kf([(0, [1, 1, 1]), (0.25, [1.08, 0.94, 1.08]), (0.5, [1, 1, 1])])}
    anims["chew"] = {"loop": True, "animation_length": 0.5, "bones": ch}
    sp = jaws([(0, -28), (0.1, 75), (0.4, 55)])
    sp["root"] = {"position": kf([(0, [0, 0, 0]), (0.1, [0, 5, 0]), (0.4, [0, 0, 0])])}
    anims["spit"] = {"loop": False, "animation_length": 0.4, "bones": sp}
    sk = jaws([(0, 55), (0.35, -20), (0.7, -28)])
    sk["root"] = {"position": kf([(0, [0, 0, 0]), (0.25, [0, 2, 0]), (0.7, [0, -60, 0])])}
    anims["sink"] = {"loop": False, "animation_length": 0.7, "bones": sk}
    out = {"format_version": "1.8.0", "animations": {}}
    for n, a in anims.items():
        out["animations"]["animation.fd_abyssal_maw." + n] = a
    write_json(os.path.join(ASSETS, "animations/entity/fd_abyssal_maw.animation.json"), out)


# =====================================================================================
#  Dados
# =====================================================================================
def data():
    write_json(os.path.join(DATA, "damage_type/fd_dimension_slash.json"),
               {"message_id": "isekai_mahou.fd_dimension_slash", "scaling": "never", "exhaustion": 0.0})
    write_json(os.path.join(MERGE, "data/minecraft/tags/damage_type/bypasses_armor.json"),
               {"values": ["isekai_mahou:fd_dimension_slash"]})


if __name__ == "__main__":
    white()
    black_flame()
    soul_burn_icon()
    kraken()
    maw()
    maw_anims()
    data()
    print("fusions_d: assets gerados")
