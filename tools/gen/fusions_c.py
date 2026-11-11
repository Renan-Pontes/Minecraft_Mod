"""
Assets do modulo "fusions_c" (Misturas C). Rode da raiz do projeto:  py -I tools/gen/fusions_c.py

Texturas de efeito (brancas/neutras, tingidas no renderer) em textures/entity/:
  fc_glow.png    32x32   brilho radial suave
  fc_beam.png    32x64   perfil de feixe (claro no meio) com estrias que repetem em V
  fc_ring.png    64x16   faixa de onda de choque (v=0 borda forte, v=1 some)
  fc_noise.png   64x64   nuvem/plasma (ruido periodico) — vapor, poeira, superficie de estrela
  fc_rune.png   128x128  circulo runico (fonte, caixao de gelo, supernova)
  fc_cracks.png  64x64   rachaduras radiais (aviso do geiser)
  fc_petals.png  32x32   atlas 2x2: petala rosa, petala amarela, bolinha de polen, folha
Icone do efeito:  textures/mob_effect/pollen_drowsy.png (18x18)
Modelos GeckoLib (geo + animacao + textura + _glowmask):
  mini_volcano            vulcao em camadas com cratera de lava e rios de lava descendo
  world_root              raiz gigante retorcida em 5 segmentos com espinhos, musgo e seiva brilhante
  sacred_spring_fountain  fonte de marmore octogonal, pilar, taca, lotus de luz, jatos e aureola

Convencoes GeckoLib/Bedrock: frente = -Z; y para cima a partir do chao; 16 px = 1 bloco.
Ossos animados nao tem rotacao inicial (rotacoes fixas ficam em ossos-filho nao animados).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX = os.path.join(ASSETS, "textures", "entity")
CLEAR = (0, 0, 0, 0)


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


# =====================================================================================
#  Ruido periodico
# =====================================================================================
class PNoise:
    def __init__(self, period, seed):
        rnd = random.Random(seed)
        self.p = period
        self.g = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def v(self, x, y):
        p = self.p
        x0, y0 = int(math.floor(x)) % p, int(math.floor(y)) % p
        x1, y1 = (x0 + 1) % p, (y0 + 1) % p
        fx, fy = x - math.floor(x), y - math.floor(y)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.g[y0][x0] + (self.g[y0][x1] - self.g[y0][x0]) * sx
        b = self.g[y1][x0] + (self.g[y1][x1] - self.g[y1][x0]) * sx
        return a + (b - a) * sy


def fbm(layers, u, v):
    total, amp, norm = 0.0, 1.0, 0.0
    for n in layers:
        total += amp * n.v(u * n.p, v * n.p)
        norm += amp
        amp *= 0.5
    return total / norm


def white(a):
    return (255, 255, 255, int(clamp(a) * 255))


# =====================================================================================
#  Texturas de efeito
# =====================================================================================
def tex_glow():
    S = 32
    c = Canvas(S, S)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - S / 2, y + 0.5 - S / 2) / (S / 2)
            a = clamp(1 - d) ** 2.2
            c.set(x, y, white(a))
    c.save(os.path.join(TEX, "fc_glow.png"))


def tex_beam():
    W, H = 32, 64
    layers = [PNoise(4, 11), PNoise(8, 12)]
    c = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            across = abs(x + 0.5 - W / 2) / (W / 2)
            prof = clamp(1 - across) ** 1.6
            streak = 0.65 + 0.35 * fbm(layers, x / W * 0.25, y / H)
            c.set(x, y, white(prof * streak))
    c.save(os.path.join(TEX, "fc_beam.png"))


def tex_ring():
    W, H = 64, 16
    layers = [PNoise(8, 21), PNoise(16, 22)]
    c = Canvas(W, H)
    for y in range(H):
        for x in range(W):
            t = y / (H - 1)
            edge = clamp(1 - t) ** 1.8
            n = 0.7 + 0.3 * fbm(layers, x / W, t * 0.5)
            c.set(x, y, white(edge * n))
    c.save(os.path.join(TEX, "fc_ring.png"))


def tex_noise():
    S = 64
    layers = [PNoise(4, 31), PNoise(8, 32), PNoise(16, 33)]
    c = Canvas(S, S)
    for y in range(S):
        for x in range(S):
            n = fbm(layers, x / S, y / S)
            a = clamp((n - 0.28) * 1.9)
            c.set(x, y, white(a))
    c.save(os.path.join(TEX, "fc_noise.png"))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def tex_rune():
    S = 128
    c = Canvas(S, S)
    cx = cy = S / 2
    segs = []
    # Dois aneis finos + anel de marcas + hexagono + triangulos
    R1, R2, R3 = 60.0, 52.0, 36.0
    for k in range(6):
        a0 = math.pi * 2 * k / 6 + math.pi / 6
        a1 = math.pi * 2 * (k + 1) / 6 + math.pi / 6
        segs.append((cx + math.cos(a0) * R3, cy + math.sin(a0) * R3, cx + math.cos(a1) * R3, cy + math.sin(a1) * R3, 1.0))
    for k in range(3):
        for off in (0, math.pi / 3):
            a0 = math.pi * 2 * k / 3 + off
            a1 = math.pi * 2 * (k + 1) / 3 + off
            segs.append((cx + math.cos(a0) * R2, cy + math.sin(a0) * R2, cx + math.cos(a1) * R2, cy + math.sin(a1) * R2, 0.8))
    rnd = random.Random(41)
    glyphs = []
    for k in range(24):
        a = math.pi * 2 * k / 24
        r0, r1 = 54.5, 58.5
        glyphs.append((a, rnd.randint(0, 3)))
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            a = 0.0
            a = max(a, clamp(1.2 - abs(d - R1)))
            a = max(a, clamp(0.9 - abs(d - 56.5)) * 0.6)
            a = max(a, clamp(1.0 - abs(d - R3 + 4)) * 0.7)
            a = max(a, clamp(1.1 - abs(d - 12)) * 0.9)
            for (ax, ay, bx, by, w) in segs:
                a = max(a, clamp(w + 0.5 - seg_dist(px, py, ax, ay, bx, by)) * 0.95)
            # Runas: tracinhos no anel externo
            if 53 < d < 60:
                ang = math.atan2(py - cy, px - cx) % (math.pi * 2)
                k = int(ang / (math.pi * 2) * 24)
                local = ang * 24 / (math.pi * 2) - k
                kind = glyphs[k][1]
                rr = (d - 53) / 7
                if kind == 0 and 0.35 < local < 0.45:
                    a = max(a, 0.9)
                if kind == 1 and abs(rr - 0.5) < 0.12 and 0.2 < local < 0.8:
                    a = max(a, 0.85)
                if kind == 2 and abs(local - 0.5) < 0.08 + 0.3 * rr and abs(local - 0.5) > 0.3 * rr - 0.04:
                    a = max(a, 0.8)
                if kind == 3 and (abs(local - 0.3) < 0.06 or abs(local - 0.7) < 0.06):
                    a = max(a, 0.85)
            if a > 0.01:
                c.set(x, y, white(a))
    c.save(os.path.join(TEX, "fc_rune.png"))


def tex_cracks():
    S = 64
    c = Canvas(S, S)
    cx = cy = S / 2
    rnd = random.Random(51)
    segs = []

    def branch(x, y, ang, length, width, depth):
        steps = max(2, int(length / 3))
        for _ in range(steps):
            ang += rnd.uniform(-0.45, 0.45)
            nx, ny = x + math.cos(ang) * 3, y + math.sin(ang) * 3
            segs.append((x, y, nx, ny, width))
            if depth > 0 and rnd.random() < 0.18:
                branch(nx, ny, ang + rnd.choice((-1, 1)) * rnd.uniform(0.5, 1.1), length * 0.45, width * 0.7, depth - 1)
            x, y = nx, ny
            width *= 0.93

    for k in range(9):
        branch(cx, cy, math.pi * 2 * k / 9 + rnd.uniform(-0.2, 0.2), 30, 1.6, 2)
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            a = 0.0
            for (ax, ay, bx, by, w) in segs:
                a = max(a, clamp(w + 0.4 - seg_dist(px, py, ax, ay, bx, by)))
            d = math.hypot(px - cx, py - cy) / (S / 2)
            a *= clamp(1.15 - d)
            a = max(a, clamp(1 - d * 3) * 0.8)
            if a > 0.01:
                c.set(x, y, white(a))
    c.save(os.path.join(TEX, "fc_cracks.png"))


def tex_petals():
    c = Canvas(32, 32)
    # Celula 0: petala rosa (gota)
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - 8) / 5.0, (y + 0.5 - 9) / 7.0
            d = u * u + v * v * (1.0 + 0.6 * max(0, -v))
            if d < 1:
                base = mix(C("#FFB0D8"), C("#FF7AB8"), clamp((y - 3) / 12))
                c.set(x, y, lighten(base, 0.25 * (1 - d)))
    # Celula 1: petala amarela
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - 8) / 4.5, (y + 0.5 - 8) / 6.5
            d = u * u + v * v
            if d < 1:
                base = mix(C("#FFF2A0"), C("#FFC830"), clamp((y - 2) / 12))
                c.set(16 + x, y, lighten(base, 0.3 * (1 - d)))
    # Celula 2: bolinhas de polen
    rnd = random.Random(61)
    for _ in range(7):
        bx, by, br = rnd.uniform(4, 12), rnd.uniform(4, 12), rnd.uniform(1.4, 2.6)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - bx, y + 0.5 - by)
                if d < br:
                    c.set(x, 16 + y, lighten(C("#FFD84A"), 0.4 * (1 - d / br)))
    # Celula 3: folha verde com nervura
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - 8) / 3.8, (y + 0.5 - 8) / 7.2
            if u * u + v * v < 1:
                col = C("#6FCF5A") if abs(x + 0.5 - 8) > 0.7 else C("#B8F0A0")
                c.set(16 + x, 16 + y, darken(col, 0.15 * clamp((y - 4) / 10)))
    c.save(os.path.join(TEX, "fc_petals.png"))


def icon_drowsy():
    c = Canvas(18, 18)
    # Flor amarela com miolo e "zZ"
    for k in range(5):
        a = math.pi * 2 * k / 5 - math.pi / 2
        px, py = 7 + math.cos(a) * 3.4, 10 + math.sin(a) * 3.4
        for y in range(18):
            for x in range(18):
                if math.hypot(x + 0.5 - px, y + 0.5 - py) < 2.4:
                    c.set(x, y, C("#FFD84A"))
    for y in range(18):
        for x in range(18):
            if math.hypot(x + 0.5 - 7, y + 0.5 - 10) < 1.9:
                c.set(x, y, C("#C86A10"))
    c.outline(C("#5A3A08"))
    z = C("#E8F0FF")
    for x in range(11, 16):
        c.set(x, 1, z)
        c.set(x, 5, z)
    for i in range(4):
        c.set(15 - i, 2 + i, z)
    for x in range(13, 16):
        c.set(x, 7, z)
        c.set(x, 9, z)
    c.set(14, 8, z)
    c.save(os.path.join(ASSETS, "textures", "mob_effect", "pollen_drowsy.png"))


# =====================================================================================
#  Modelos GeckoLib
# =====================================================================================
class Model:
    """Cubos com UV por face (cada face aponta para uma regiao da textura; regioes podem ser compartilhadas)."""

    def __init__(self, ident, tw, th):
        self.ident, self.tw, self.th = ident, tw, th
        self.bones = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return b

    @staticmethod
    def cube(bone, origin, size, side, top, bottom=None, inflate=0.0, rot=None, pivot=None):
        """side/top/bottom = (u, v, w, h) regioes da textura."""
        bottom = bottom or top
        uv = {}
        for f in ("north", "south", "east", "west"):
            uv[f] = {"uv": [side[0], side[1]], "uv_size": [side[2], side[3]]}
        uv["up"] = {"uv": [top[0], top[1]], "uv_size": [top[2], top[3]]}
        uv["down"] = {"uv": [bottom[0], bottom[1]], "uv_size": [bottom[2], bottom[3]]}
        c = {"origin": [float(v) for v in origin], "size": [float(v) for v in size], "uv": uv}
        if inflate:
            c["inflate"] = inflate
        if rot:
            c["rotation"] = [float(v) for v in rot]
            c["pivot"] = [float(v) for v in (pivot or origin)]
        bone["cubes"].append(c)
        return c

    def save(self, bounds):
        bones = []
        for b in self.bones:
            bb = dict(b)
            if not bb["cubes"]:
                del bb["cubes"]
            bones.append(bb)
        write_json(os.path.join(ASSETS, "geo", "entity", self.ident + ".geo.json"), {
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


class Paint:
    """Textura + mascara de brilho do mesmo tamanho."""

    def __init__(self, w, h):
        self.tex = Canvas(w, h)
        self.glow = Canvas(w, h)

    def region(self, rx, ry, rw, rh, fn):
        """fn(u, v, x, y) -> (cor, brilho_ou_None) com u, v em 0..1."""
        for y in range(rh):
            for x in range(rw):
                col, g = fn((x + 0.5) / rw, (y + 0.5) / rh, x, y)
                self.tex.set(rx + x, ry + y, C(col))
                self.glow.set(rx + x, ry + y, C(g) if g is not None else CLEAR)

    def save(self, name):
        self.tex.save(os.path.join(TEX, name + ".png"))
        self.glow.save(os.path.join(TEX, name + "_glowmask.png"))


def num(v):
    return round(v, 3)


def keys(points):
    return {f"{num(t)}": [num(a) for a in v] for t, v in sorted(points.items())}


def wave(length, fn, steps=12):
    return keys({length * i / steps: fn(i / steps) for i in range(steps + 1)})


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


def ease_out(p):
    return 1 - (1 - p) ** 3


# -------------------------------------------------------------------------------------
#  Vulcao
# -------------------------------------------------------------------------------------
def build_volcano():
    TW = TH = 128
    p = Paint(TW, TH)
    rock = [PNoise(4, 101), PNoise(8, 102), PNoise(16, 103)]
    veins = [PNoise(6, 104), PNoise(12, 105)]

    # A (0,0,64,16): lateral de rocha basaltica com rachaduras de lava
    def side(u, v, x, y):
        n = fbm(rock, u, v * 0.25)
        base = mix(C("#2A1C18"), C("#5A3E30"), clamp(n * 1.3 - 0.15))
        base = darken(base, 0.25 * v)
        crack = fbm(veins, u * 1.0, v * 0.4)
        if abs(crack - 0.5) < 0.035:
            hot = mix(C("#FF6A10"), C("#FFC040"), clamp(1 - abs(crack - 0.5) / 0.035))
            return hot, hot
        if y == 0:
            base = lighten(base, 0.12)
        return base, None
    p.region(0, 0, 64, 16, side)

    # B (0,16,64,64): topo de cinza com veios de lava
    def top(u, v, x, y):
        n = fbm(rock, u, v)
        base = mix(C("#3A2A24"), C("#6A5248"), clamp(n * 1.2 - 0.1))
        vein = fbm(veins, u, v)
        if abs(vein - 0.5) < 0.03:
            hot = mix(C("#FF4500"), C("#FFB040"), clamp(1 - abs(vein - 0.5) / 0.03))
            return hot, hot
        return base, None
    p.region(0, 16, 64, 64, top)

    # C (64,0,32,32): lago de lava borbulhante (todo brilhante)
    def pool(u, v, x, y):
        n = fbm(rock, u * 2, v * 2)
        col = mix(C("#FF3A00"), C("#FFE070"), clamp(n * 1.6 - 0.3))
        return col, col
    p.region(64, 0, 32, 32, pool)

    # D (64,32,32,16): rio de lava escorrendo
    def stream(u, v, x, y):
        n = fbm(veins, u * 0.5, v)
        col = mix(C("#E83000"), C("#FFC850"), clamp(n * 1.5 - 0.2))
        return col, col
    p.region(64, 32, 32, 16, stream)

    # E (96,0,32,32): pedregulho escuro
    def boulder(u, v, x, y):
        n = fbm(rock, u + 0.5, v + 0.5)
        col = mix(C("#1E1612"), C("#4A3A32"), clamp(n * 1.4 - 0.2))
        if x in (0, 31) or y in (0, 31):
            col = darken(col, 0.3)
        return col, None
    p.region(96, 0, 32, 32, boulder)

    # F (64,48,32,16): rocha incandescente perto da cratera
    def hotrock(u, v, x, y):
        n = fbm(rock, u, v + 0.3)
        base = mix(C("#5A2010"), C("#B04018"), clamp(n * 1.3 - 0.1))
        g = mix(C("#000000"), C("#FF5A10"), clamp((1 - v) * 0.8)) if v < 0.5 else None
        return base, g
    p.region(64, 48, 32, 16, hotrock)
    p.save("mini_volcano")

    SIDE, TOP, POOL, STREAM, BOULDER, HOT = (0, 0, 64, 16), (0, 16, 64, 64), (64, 0, 32, 32), (64, 32, 32, 16), (96, 0, 32, 32), (64, 48, 32, 16)
    m = Model("mini_volcano", TW, TH)
    root = m.bone("root", [0, 0, 0])
    body = m.bone("body", [0, 0, 0], "root")
    tiers = [(72, 8), (60, 10), (48, 10), (36, 10)]
    y = 0
    rnd = random.Random(111)
    for i, (w, h) in enumerate(tiers):
        # Camadas giradas alternadamente (ficam menos quadradas: octogono)
        m.cube(body, [-w / 2, y, -w / 2], [w, h, w], SIDE, TOP)
        w2 = w * 0.78
        m.cube(body, [-w2 / 2, y, -w2 / 2], [w2, h, w2], SIDE, TOP, rot=[0, 45, 0], pivot=[0, y, 0])
        y += h
    rim_y = y
    crater = m.bone("crater", [0, rim_y, 0], "root")
    # Borda da cratera: 4 paredes em volta do lago
    W, T, H = 30, 6, 9
    m.cube(crater, [-W / 2, rim_y, -W / 2], [W, H, T], HOT, TOP)
    m.cube(crater, [-W / 2, rim_y, W / 2 - T], [W, H, T], HOT, TOP)
    m.cube(crater, [-W / 2, rim_y, -W / 2 + T], [T, H, W - 2 * T], HOT, TOP)
    m.cube(crater, [W / 2 - T, rim_y, -W / 2 + T], [T, H, W - 2 * T], HOT, TOP)
    pool = m.bone("lava_pool", [0, rim_y + 5, 0], "crater")
    m.cube(pool, [-(W - 2 * T) / 2, rim_y + 3, -(W - 2 * T) / 2], [W - 2 * T, 3, W - 2 * T], POOL, POOL)
    # Rios de lava descendo pelas encostas (finos, levemente para fora)
    rivers = m.bone("rivers", [0, 0, 0], "body")
    for k in range(5):
        a = math.pi * 2 * k / 5 + 0.3
        for i, (w, h) in enumerate(tiers):
            r = w / 2 + 0.2
            m.cube(rivers, [math.cos(a) * r - 2, sum(t[1] for t in tiers[:i]), math.sin(a) * r - 2],
                   [4, h, 4], STREAM, POOL, inflate=0.1, rot=[0, -math.degrees(a), 0],
                   pivot=[math.cos(a) * r, 0, math.sin(a) * r])
    # Pedregulhos em volta da base
    rocks = m.bone("rocks", [0, 0, 0], "root")
    for k in range(9):
        a = rnd.uniform(0, math.pi * 2)
        r = rnd.uniform(36, 44)
        s = rnd.randint(5, 9)
        m.cube(rocks, [math.cos(a) * r - s / 2, 0, math.sin(a) * r - s / 2], [s, s * 0.8, s], BOULDER, BOULDER,
               rot=[rnd.uniform(-15, 15), rnd.uniform(0, 90), rnd.uniform(-15, 15)], pivot=[math.cos(a) * r, 0, math.sin(a) * r])
    m.save((6, 5, 2))

    an = Anims("mini_volcano")
    an.add("emerge", 1.0, "hold", {
        "root": {
            "position": wave(1.0, lambda q: [math.sin(q * 40) * 1.2 * (1 - q), -46 * (1 - ease_out(q)), math.cos(q * 37) * 1.2 * (1 - q)]),
            "scale": wave(1.0, lambda q: [0.7 + 0.3 * ease_out(q)] * 3),
        },
    })
    an.add("idle", 2.0, True, {
        "lava_pool": {"scale": wave(2.0, lambda q: [1 + 0.06 * math.sin(q * math.pi * 4), 1 + 0.4 * abs(math.sin(q * math.pi * 3)), 1 + 0.06 * math.sin(q * math.pi * 4)])},
        "crater": {"position": wave(2.0, lambda q: [0, 0.3 * math.sin(q * math.pi * 2), 0])},
        "body": {"scale": wave(2.0, lambda q: [1 + 0.008 * math.sin(q * math.pi * 2), 1, 1 + 0.008 * math.sin(q * math.pi * 2)])},
    })
    an.add("erupt", 0.5, False, {
        "body": {"scale": wave(0.5, lambda q: [1 - 0.05 * math.sin(q * math.pi), 1 + 0.1 * math.sin(q * math.pi), 1 - 0.05 * math.sin(q * math.pi)], 8)},
        "crater": {"position": wave(0.5, lambda q: [0, 3 * math.sin(q * math.pi), 0], 8)},
        "lava_pool": {"scale": wave(0.5, lambda q: [1 + 0.2 * math.sin(q * math.pi), 1 + 2.5 * math.sin(q * math.pi), 1 + 0.2 * math.sin(q * math.pi)], 8)},
    })
    an.add("collapse", 1.0, "hold", {
        "root": {
            "position": wave(1.0, lambda q: [math.sin(q * 50) * 1.5, -40 * q * q, math.cos(q * 45) * 1.5]),
            "rotation": wave(1.0, lambda q: [8 * q, 0, -6 * q]),
            "scale": wave(1.0, lambda q: [1 + 0.1 * q, 1 - 0.45 * q, 1 + 0.1 * q]),
        },
        "lava_pool": {"scale": wave(1.0, lambda q: [1, 1 + 3 * math.sin(q * math.pi), 1])},
    })
    an.save()


# -------------------------------------------------------------------------------------
#  Raiz do Mundo
# -------------------------------------------------------------------------------------
def build_root():
    TW = TH = 64
    p = Paint(TW, TH)
    bark = [PNoise(4, 201), PNoise(8, 202)]

    # A (0,0,32,32): casca com sulcos verticais e veios de seiva verde
    def bark_side(u, v, x, y):
        groove = 0.5 + 0.5 * math.sin(u * math.pi * 10 + fbm(bark, u, v) * 5)
        n = fbm(bark, u, v * 0.5)
        col = mix(C("#3A2414"), C("#7A5232"), clamp(groove * 0.6 + n * 0.5 - 0.1))
        if abs(fbm(bark, u * 0.7 + 0.3, v) - 0.5) < 0.02:
            g = C("#7CFF6A")
            return g, g
        return col, None
    p.region(0, 0, 32, 32, bark_side)

    # B (32,0,16,16): corte da raiz (aneis)
    def rings(u, v, x, y):
        d = math.hypot(u - 0.5, v - 0.5)
        col = mix(C("#C89A60"), C("#8A6038"), 0.5 + 0.5 * math.sin(d * 40))
        if d > 0.45:
            col = C("#3A2414")
        return col, None
    p.region(32, 0, 16, 16, rings)

    # C (48,0,16,16): espinho
    def thorn(u, v, x, y):
        col = mix(C("#D8C8A0"), C("#5A4028"), v)
        return col, None
    p.region(48, 0, 16, 16, thorn)

    # D (32,16,16,16): musgo
    def moss(u, v, x, y):
        n = fbm(bark, u * 2, v * 2)
        col = mix(C("#2F6A22"), C("#7ACF48"), clamp(n * 1.5 - 0.2))
        return col, None
    p.region(32, 16, 16, 16, moss)

    # E (0,32,32,16): terra revirada
    def dirt(u, v, x, y):
        n = fbm(bark, u * 2 + 0.4, v * 2)
        col = mix(C("#3A2A1C"), C("#6A4E34"), clamp(n * 1.4 - 0.2))
        return col, None
    p.region(0, 32, 32, 16, dirt)

    # F (48,16,16,16): seiva brilhante
    def sap(u, v, x, y):
        col = mix(C("#5AFF7A"), C("#E0FFB0"), clamp(1 - math.hypot(u - 0.5, v - 0.5) * 2))
        return col, col
    p.region(48, 16, 16, 16, sap)
    p.save("world_root")

    BARK, RINGS, THORN, MOSS, DIRT, SAP = (0, 0, 32, 32), (32, 0, 16, 16), (48, 0, 16, 16), (32, 16, 16, 16), (0, 32, 32, 16), (48, 16, 16, 16)
    m = Model("world_root", TW, TH)
    root = m.bone("root", [0, 0, 0])
    ground = m.bone("ground", [0, 0, 0], "root")
    rnd = random.Random(222)
    for k in range(10):
        a = math.pi * 2 * k / 10 + rnd.uniform(-0.2, 0.2)
        r = rnd.uniform(9, 14)
        s = rnd.randint(4, 7)
        m.cube(ground, [math.cos(a) * r - s / 2, 0, math.sin(a) * r - s / 2], [s, rnd.randint(2, 4), s], DIRT, DIRT,
               rot=[rnd.uniform(-25, 25), rnd.uniform(0, 90), rnd.uniform(-25, 25)], pivot=[math.cos(a) * r, 0, math.sin(a) * r])
    # Corrente de segmentos: cada um é filho do anterior (curvam juntos)
    segs = [(14, 12), (12, 12), (10, 12), (8, 11), (5, 10)]
    parent = "root"
    y = -6
    names = ["seg0", "seg1", "seg2", "seg3", "tip"]
    for i, (w, h) in enumerate(segs):
        b = m.bone(names[i], [0, y, 0], parent)
        m.cube(b, [-w / 2, y, -w / 2], [w, h, w], BARK, RINGS)
        # Espinhos (filhos fixos com rotação) e musgo
        fixed = m.bone(names[i] + "_deco", [0, y + h / 2, 0], names[i], rotation=[0, rnd.uniform(0, 90), 0])
        for k in range(3):
            a = math.pi * 2 * k / 3 + i
            m.cube(fixed, [math.cos(a) * w / 2 - 1, y + h * 0.4, math.sin(a) * w / 2 - 1], [2, 2, 5], THORN, THORN,
                   rot=[30, math.degrees(-a) + 90, 0], pivot=[math.cos(a) * w / 2, y + h * 0.4, math.sin(a) * w / 2])
        if i < 3:
            m.cube(fixed, [-w / 2 - 0.5, y + 2, -w / 2 + 1], [w + 1, 3, w - 2], MOSS, MOSS)
            m.cube(fixed, [-1, y + 1, -w / 2 - 0.4], [2, h - 2, 1], SAP, SAP)
        parent = names[i]
        y += h
    # Raizinhas laterais na base
    side = m.bone("rootlets", [0, 0, 0], "seg0", rotation=[0, 25, 0])
    for k in range(4):
        a = math.pi * 2 * k / 4
        m.cube(side, [math.cos(a) * 8 - 2, -4, math.sin(a) * 8 - 2], [4, 4, 12], BARK, RINGS,
               rot=[20, math.degrees(-a) + 90, 0], pivot=[math.cos(a) * 7, -2, math.sin(a) * 7])
    m.save((4, 5, 2))

    an = Anims("world_root")
    an.add("emerge", 0.5, "hold", {
        "seg0": {"position": wave(0.5, lambda q: [0, -52 * (1 - ease_out(q)), 0], 8),
                 "rotation": wave(0.5, lambda q: [12 * (1 - q), 0, 0], 8)},
        "seg1": {"rotation": wave(0.5, lambda q: [-25 * (1 - q), 0, 0], 8)},
        "seg2": {"rotation": wave(0.5, lambda q: [-20 * (1 - q), 0, 0], 8)},
        "ground": {"scale": wave(0.5, lambda q: [0.3 + 0.7 * ease_out(q), 1, 0.3 + 0.7 * ease_out(q)], 8)},
    })
    # Contorcendo em volta do preso (os segmentos de cima se fecham e soltam)
    an.add("squeeze", 1.0, True, {
        "seg1": {"rotation": wave(1.0, lambda q: [10 * math.sin(q * math.pi * 2), 18 * math.sin(q * math.pi * 2), 6 * math.cos(q * math.pi * 2)])},
        "seg2": {"rotation": wave(1.0, lambda q: [35 + 12 * math.sin(q * math.pi * 2 + 1), 30 * math.sin(q * math.pi * 2), 0])},
        "seg3": {"rotation": wave(1.0, lambda q: [45 + 15 * math.sin(q * math.pi * 2 + 2), 25 * math.sin(q * math.pi * 2 + 1), 0])},
        "tip": {"rotation": wave(1.0, lambda q: [50 + 20 * math.sin(q * math.pi * 2 + 3), 0, 15 * math.sin(q * math.pi * 4)])},
        "seg0": {"scale": wave(1.0, lambda q: [1 + 0.05 * math.sin(q * math.pi * 4), 1, 1 + 0.05 * math.sin(q * math.pi * 4)])},
    })
    # Desaba: a raiz se dobra até o chão e afunda
    an.add("slam", 0.7, "hold", {
        "seg1": {"rotation": wave(0.7, lambda q: [55 * ease_out(min(1, q * 2)), 0, 0], 10)},
        "seg2": {"rotation": wave(0.7, lambda q: [40 * ease_out(min(1, q * 2)), 0, 0], 10)},
        "seg3": {"rotation": wave(0.7, lambda q: [30 * ease_out(min(1, q * 2)), 0, 0], 10)},
        "seg0": {"position": wave(0.7, lambda q: [0, -52 * max(0, (q - 0.45) / 0.55) ** 2, 0], 10)},
        "ground": {"scale": wave(0.7, lambda q: [1 - 0.6 * max(0, q - 0.5) * 2, 1, 1 - 0.6 * max(0, q - 0.5) * 2], 10)},
    })
    an.save()


# -------------------------------------------------------------------------------------
#  Fonte Sagrada
# -------------------------------------------------------------------------------------
def build_spring():
    TW = TH = 64
    p = Paint(TW, TH)
    stone = [PNoise(4, 301), PNoise(8, 302)]

    # A (0,0,32,16): marmore com veios dourados
    def marble(u, v, x, y):
        n = fbm(stone, u, v)
        col = mix(C("#E8E4DC"), C("#FFFFFF"), clamp(n * 1.2 - 0.1))
        vein = abs(math.sin((u + v * 0.6) * 9 + n * 6))
        if vein < 0.06:
            g = C("#FFD86A")
            return g, (255, 220, 120, 140)
        if y == 0 or y == 15:
            col = C("#D8C890")
        return col, None
    p.region(0, 0, 32, 16, marble)

    # B (0,16,32,32): água benta (brilha)
    def water(u, v, x, y):
        n = fbm(stone, u * 2, v * 2)
        col = mix(C("#6FC8FF"), C("#E8F8FF"), clamp(n * 1.5 - 0.3))
        col = (col[0], col[1], col[2], 200)
        return col, col
    p.region(0, 16, 32, 32, water)

    # C (32,0,16,16): ouro
    def gold(u, v, x, y):
        col = mix(C("#FFE9A8"), C("#C8961E"), v)
        return col, None
    p.region(32, 0, 16, 16, gold)

    # D (48,0,16,16): pétala de luz (brilha)
    def petal(u, v, x, y):
        d = math.hypot(u - 0.5, v - 0.6)
        col = mix(C("#FFFFFF"), C("#FFE9A8"), clamp(d * 1.8))
        return col, col
    p.region(48, 0, 16, 16, petal)

    # E (32,16,32,16): pedra da base (mais escura) com runas douradas
    def base(u, v, x, y):
        n = fbm(stone, u + 0.5, v)
        col = mix(C("#B8B0A0"), C("#D8D0C0"), clamp(n * 1.3 - 0.1))
        if 5 <= y <= 10 and (x % 6) in (1, 2) and (y + x) % 3 != 0:
            g = C("#FFE070")
            return g, g
        return col, None
    p.region(32, 16, 32, 16, base)
    p.save("sacred_spring_fountain")

    MARBLE, WATER, GOLD, PETAL, BASE = (0, 0, 32, 16), (0, 16, 32, 32), (32, 0, 16, 16), (48, 0, 16, 16), (32, 16, 32, 16)
    m = Model("sacred_spring_fountain", TW, TH)
    root = m.bone("root", [0, 0, 0])
    basin = m.bone("basin", [0, 0, 0], "root")
    # Bacia octogonal: dois quadrados girados + borda
    m.cube(basin, [-14, 0, -14], [28, 3, 28], BASE, MARBLE)
    m.cube(basin, [-14, 0, -14], [28, 3, 28], BASE, MARBLE, rot=[0, 45, 0], pivot=[0, 0, 0])
    for rot in (0, 45):
        for (ox, oz, sx, sz) in ((-13, -13, 26, 2), (-13, 11, 26, 2), (-13, -11, 2, 22), (11, -11, 2, 22)):
            m.cube(basin, [ox, 3, oz], [sx, 5, sz], MARBLE, MARBLE, rot=[0, rot, 0] if rot else None, pivot=[0, 0, 0])
    water = m.bone("water", [0, 6, 0], "root")
    m.cube(water, [-11, 5, -11], [22, 1, 22], WATER, WATER)
    m.cube(water, [-11, 5, -11], [22, 1, 22], WATER, WATER, rot=[0, 45, 0], pivot=[0, 5, 0])
    pillar = m.bone("pillar", [0, 0, 0], "root")
    m.cube(pillar, [-3, 3, -3], [6, 18, 6], MARBLE, MARBLE)
    m.cube(pillar, [-4, 3, -4], [8, 2, 8], GOLD, GOLD)
    m.cube(pillar, [-4, 19, -4], [8, 2, 8], GOLD, GOLD)
    bowl = m.bone("bowl", [0, 21, 0], "pillar")
    m.cube(bowl, [-7, 21, -7], [14, 2, 14], MARBLE, MARBLE)
    m.cube(bowl, [-7, 23, -7], [14, 2, 1], MARBLE, MARBLE)
    m.cube(bowl, [-7, 23, 6], [14, 2, 1], MARBLE, MARBLE)
    m.cube(bowl, [-7, 23, -6], [1, 2, 12], MARBLE, MARBLE)
    m.cube(bowl, [6, 23, -6], [1, 2, 12], MARBLE, MARBLE)
    m.cube(bowl, [-6, 23.5, -6], [12, 1, 12], WATER, WATER)
    # Lótus de luz: pétalas em ossos-filho com rotação fixa, girando no osso "lotus"
    lotus = m.bone("lotus", [0, 25, 0], "bowl")
    for k in range(8):
        a = 360 * k / 8
        pb = m.bone(f"petal{k}", [0, 25, 0], "lotus", rotation=[0, a, 0])
        m.cube(pb, [-1.5, 25, -0.5], [3, 8, 1], PETAL, PETAL, rot=[-35 if k % 2 == 0 else -20, 0, 0], pivot=[0, 25, 0])
    m.cube(lotus, [-1.5, 25, -1.5], [3, 3, 3], PETAL, PETAL)
    # Jatos de água saindo da taça para a bacia
    jets = m.bone("jets", [0, 22, 0], "root")
    for k in range(4):
        a = math.pi * 2 * k / 4 + math.pi / 4
        x, z = math.cos(a) * 8, math.sin(a) * 8
        m.cube(jets, [x - 1, 6, z - 1], [2, 16, 2], WATER, WATER, inflate=0.1)
    # Auréola flutuando acima
    halo = m.bone("halo", [0, 38, 0], "root")
    for (ox, oz, sx, sz) in ((-6, -6, 12, 1), (-6, 5, 12, 1), (-6, -5, 1, 10), (5, -5, 1, 10)):
        m.cube(halo, [ox, 38, oz], [sx, 1, sz], GOLD, GOLD)
    m.save((3, 4, 1.5))

    an = Anims("sacred_spring_fountain")
    an.add("rise", 0.8, "hold", {
        "root": {"position": wave(0.8, lambda q: [0, -42 * (1 - ease_out(q)), 0], 10),
                 "scale": wave(0.8, lambda q: [0.7 + 0.3 * ease_out(q)] * 3, 10)},
        "halo": {"scale": wave(0.8, lambda q: [ease_out(q)] * 3, 10)},
    })
    an.add("idle", 4.0, True, {
        "lotus": {"rotation": wave(4.0, lambda q: [0, 360 * q, 0], 16),
                  "scale": wave(4.0, lambda q: [1 + 0.08 * math.sin(q * math.pi * 8)] * 3, 16)},
        "halo": {"rotation": wave(4.0, lambda q: [0, -360 * q, 0], 16),
                 "position": wave(4.0, lambda q: [0, 1.5 * math.sin(q * math.pi * 4), 0], 16)},
        "water": {"position": wave(4.0, lambda q: [0, 0.25 * math.sin(q * math.pi * 8), 0], 16)},
        "jets": {"scale": wave(4.0, lambda q: [1, 1 + 0.06 * math.sin(q * math.pi * 16), 1], 16)},
    })
    an.add("sink", 0.8, "hold", {
        "root": {"position": wave(0.8, lambda q: [0, -42 * q * q, 0], 10)},
        "halo": {"scale": wave(0.8, lambda q: [1 - q] * 3, 10)},
    })
    an.save()


def main():
    tex_glow()
    tex_beam()
    tex_ring()
    tex_noise()
    tex_rune()
    tex_cracks()
    tex_petals()
    icon_drowsy()
    build_volcano()
    build_root()
    build_spring()
    print("fusions_c: assets gerados")


if __name__ == "__main__":
    main()
