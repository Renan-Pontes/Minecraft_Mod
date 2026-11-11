"""
Gera os assets do modulo forms_b (formas: Zona, Muralha, Runa, Orbita, Pulso, Torre, Invocacao, Ilusao, Portal).

  py -I tools/gen/forms_b.py

- Texturas de efeito (circulos magicos, aneis, orbes, portal): brancas com alfa suave (anti-serrilhado por
  funcoes de distancia), tingidas em jogo pela cor da magia.
- Bloco spell_barrier (vidro magico translucido tingido pelo elemento): textura, modelo, blockstate, item.
- Modelos GeckoLib (.geo.json + .animation.json + textura) da torre (cristal), do espirito invocado e da ilusao
  (humanoide com UV de skin de jogador, versao larga e "slim").
"""
import json
import math
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX_ENTITY = os.path.join(ASSETS, "textures", "entity")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
GEO = os.path.join(ASSETS, "geo", "entity")
ANIM = os.path.join(ASSETS, "animations", "entity")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


# =====================================================================================
#  Campo de alfa anti-serrilhado (para as texturas brilhantes)
# =====================================================================================
class Field:
    def __init__(self, n):
        self.n = n
        self.a = [[0.0] * n for _ in range(n)]
        self.v = [[1.0] * n for _ in range(n)]

    def add(self, fn, alpha=1.0, bright=1.0):
        """fn(x, y) -> cobertura 0..1 (x, y no centro do pixel). Combina pelo maximo."""
        for y in range(self.n):
            for x in range(self.n):
                c = fn(x + 0.5, y + 0.5) * alpha
                if c <= 0:
                    continue
                if c > self.a[y][x]:
                    old = self.a[y][x]
                    self.v[y][x] = bright if old <= 0 else (self.v[y][x] * old + bright * (c - old)) / c
                    self.a[y][x] = c

    def save(self, path):
        c = Canvas(self.n, self.n)
        for y in range(self.n):
            for x in range(self.n):
                a = clamp01(self.a[y][x])
                if a <= 0.004:
                    continue
                v = int(255 * clamp01(self.v[y][x]))
                c.set(x, y, (v, v, v, int(round(a * 255))))
        c.save(path)


def ring(cx, cy, r, w):
    return lambda x, y: clamp01(w / 2 + 0.5 - abs(math.hypot(x - cx, y - cy) - r))


def soft_ring(cx, cy, r, sigma):
    return lambda x, y: math.exp(-((math.hypot(x - cx, y - cy) - r) / sigma) ** 2)


def disc(cx, cy, r):
    return lambda x, y: clamp01(r + 0.5 - math.hypot(x - cx, y - cy))


def seg(x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0
    ll = dx * dx + dy * dy

    def f(x, y):
        t = 0.0 if ll == 0 else max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / ll))
        d = math.hypot(x - (x0 + dx * t), y - (y0 + dy * t))
        return clamp01(w / 2 + 0.5 - d)
    return f


def polygon(cx, cy, r, sides, rot, w):
    pts = [(cx + math.cos(rot + i * 2 * math.pi / sides) * r, cy + math.sin(rot + i * 2 * math.pi / sides) * r)
           for i in range(sides)]
    segs = [seg(pts[i][0], pts[i][1], pts[(i + 1) % sides][0], pts[(i + 1) % sides][1], w) for i in range(sides)]
    return lambda x, y: max(s(x, y) for s in segs), pts


def fill_disc(cx, cy, r, alpha_fn):
    return lambda x, y: alpha_fn(math.hypot(x - cx, y - cy)) if math.hypot(x - cx, y - cy) <= r + 0.5 else 0.0


def rune_marks(f, cx, cy, radius, count, size, w, seed, bright=1.0, alpha=1.0):
    """Pequenos 'caracteres' runicos (2-3 tracos aleatorios) em volta de um circulo."""
    rnd = random.Random(seed)
    for i in range(count):
        a = 2 * math.pi * i / count
        px, py = cx + math.cos(a) * radius, cy + math.sin(a) * radius
        tx, ty = -math.sin(a), math.cos(a)  # tangente
        nx, ny = math.cos(a), math.sin(a)   # normal
        for _ in range(rnd.choice((2, 3))):
            s0 = (rnd.uniform(-1, 1), rnd.uniform(-1, 1))
            s1 = (rnd.uniform(-1, 1), rnd.uniform(-1, 1))
            x0 = px + (tx * s0[0] + nx * s0[1]) * size
            y0 = py + (ty * s0[0] + ny * s0[1]) * size
            x1 = px + (tx * s1[0] + nx * s1[1]) * size
            y1 = py + (ty * s1[0] + ny * s1[1]) * size
            f.add(seg(x0, y0, x1, y1, w), alpha, bright)


# =====================================================================================
#  Texturas de efeito
# =====================================================================================
def zone_circle():
    n, c = 128, 64.0
    f = Field(n)
    f.add(fill_disc(c, c, 60, lambda d: 0.07 + 0.10 * (d / 60) ** 3), 1.0, 1.0)  # brilho de fundo
    f.add(ring(c, c, 61, 2.4))
    f.add(ring(c, c, 55, 1.4), 0.9)
    rune_marks(f, c, c, 58, 28, 2.2, 1.0, seed=7, bright=0.95)
    f.add(ring(c, c, 47, 1.8))
    for i in range(72):  # marcas de grau
        a = 2 * math.pi * i / 72
        l0, l1 = (48.5, 51.5) if i % 3 else (48.5, 53.5)
        f.add(seg(c + math.cos(a) * l0, c + math.sin(a) * l0, c + math.cos(a) * l1, c + math.sin(a) * l1, 0.9), 0.75)
    tri1, pts1 = polygon(c, c, 47, 3, -math.pi / 2, 1.6)
    tri2, pts2 = polygon(c, c, 47, 3, math.pi / 2, 1.6)
    f.add(tri1)
    f.add(tri2)
    for (px, py) in pts1 + pts2:
        f.add(disc(px, py, 2.6))
        f.add(ring(px, py, 5.0, 1.0), 0.8)
    f.add(ring(c, c, 24, 1.5))
    f.add(ring(c, c, 20, 1.0), 0.8)
    rune_marks(f, c, c, 22, 12, 1.4, 0.9, seed=11, bright=0.9, alpha=0.9)
    hexa, _ = polygon(c, c, 12, 6, 0, 1.2)
    f.add(hexa, 0.9)
    f.add(disc(c, c, 3.5))
    f.save(os.path.join(TEX_ENTITY, "spell_zone_circle.png"))


def zone_inner():
    n, c = 64, 32.0
    f = Field(n)
    f.add(ring(c, c, 30, 1.6))
    sq1, p1 = polygon(c, c, 29.5, 4, 0, 1.3)
    sq2, p2 = polygon(c, c, 29.5, 4, math.pi / 4, 1.3)
    f.add(sq1, 0.95)
    f.add(sq2, 0.95)
    f.add(ring(c, c, 15, 1.3))
    for i in range(8):
        a = 2 * math.pi * i / 8 + math.pi / 8
        f.add(disc(c + math.cos(a) * 22, c + math.sin(a) * 22, 1.6))
    star, _ = polygon(c, c, 9, 8, 0, 1.0)
    f.add(star, 0.85)
    f.add(disc(c, c, 2.6))
    f.save(os.path.join(TEX_ENTITY, "spell_zone_inner.png"))


def pulse_core():
    n, c = 64, 32.0
    f = Field(n)
    f.add(fill_disc(c, c, 29, lambda d: 0.25 * math.exp(-(d / 10) ** 2)))
    f.add(ring(c, c, 29, 2.0))
    f.add(ring(c, c, 22, 1.2), 0.9)
    f.add(ring(c, c, 12, 1.6))
    for i in range(12):
        a = 2 * math.pi * i / 12
        f.add(seg(c + math.cos(a) * 13.5, c + math.sin(a) * 13.5, c + math.cos(a) * 20.5, c + math.sin(a) * 20.5, 1.2), 0.9)
    for i in range(4):  # pontas de seta para fora
        a = 2 * math.pi * i / 4 + math.pi / 4
        tip = (c + math.cos(a) * 28, c + math.sin(a) * 28)
        l = (c + math.cos(a - 0.14) * 23, c + math.sin(a - 0.14) * 23)
        r = (c + math.cos(a + 0.14) * 23, c + math.sin(a + 0.14) * 23)
        f.add(seg(l[0], l[1], tip[0], tip[1], 1.2))
        f.add(seg(r[0], r[1], tip[0], tip[1], 1.2))
    f.add(disc(c, c, 5.0))
    f.save(os.path.join(TEX_ENTITY, "spell_pulse_core.png"))


def soft_ring_tex():
    n, c = 64, 32.0
    f = Field(n)
    f.add(soft_ring(c, c, 28.5, 2.6), 0.55)
    f.add(ring(c, c, 28.5, 1.6))
    f.save(os.path.join(TEX_ENTITY, "spell_pulse_ring.png"))


def rune_tex():
    n, c = 64, 32.0
    f = Field(n)
    f.add(ring(c, c, 29, 1.5))
    f.add(ring(c, c, 25, 1.0), 0.8)
    rune_marks(f, c, c, 27, 10, 1.3, 0.9, seed=23, bright=0.9)
    # Runa central (estilo "algiz" dupla)
    f.add(seg(32, 13, 32, 51, 1.6))
    f.add(seg(32, 25, 22, 15, 1.4))
    f.add(seg(32, 25, 42, 15, 1.4))
    f.add(seg(32, 39, 23, 48, 1.4))
    f.add(seg(32, 39, 41, 48, 1.4))
    f.add(seg(26, 32, 38, 32, 1.2), 0.9)
    f.add(disc(32, 32, 2.2))
    f.add(disc(32, 11, 1.6))
    f.add(disc(32, 53, 1.6))
    f.save(os.path.join(TEX_ENTITY, "spell_rune.png"))


def orb_tex():
    n, c = 32, 16.0
    f = Field(n)
    f.add(lambda x, y: math.exp(-(math.hypot(x - c, y - c) / 7.5) ** 2))
    f.add(disc(c, c, 3.0), 1.0, 1.0)
    f.save(os.path.join(TEX_ENTITY, "spell_orb.png"))


def portal_ring():
    n, c = 64, 32.0
    f = Field(n)
    f.add(soft_ring(c, c, 28.5, 3.0), 0.45)
    f.add(ring(c, c, 28.5, 3.6))
    f.add(ring(c, c, 24, 1.2), 0.7)
    for i in range(16):
        a = 2 * math.pi * i / 16
        f.add(seg(c + math.cos(a) * 25, c + math.sin(a) * 25, c + math.cos(a) * 26.8, c + math.sin(a) * 26.8, 1.4), 0.9)
    for i in range(4):
        a = 2 * math.pi * i / 4
        f.add(disc(c + math.cos(a) * 28.5, c + math.sin(a) * 28.5, 2.4), 1.0, 1.0)
    f.save(os.path.join(TEX_ENTITY, "spell_portal_ring.png"))


def portal_swirl():
    n, c = 64, 32.0
    f = Field(n)

    def swirl(x, y):
        d = math.hypot(x - c, y - c)
        if d > 30.5:
            return 0.0
        th = math.atan2(y - c, x - c)
        arms = 0.5 + 0.5 * math.cos(3 * th + d * 0.32)
        fall = clamp01(1 - d / 30.5)
        return clamp01(arms ** 2.2 * (0.35 + 0.65 * fall) + 0.18 * fall)
    f.add(swirl)
    f.add(lambda x, y: 0.8 * math.exp(-(math.hypot(x - c, y - c) / 6.0) ** 2))
    f.save(os.path.join(TEX_ENTITY, "spell_portal_swirl.png"))


# =====================================================================================
#  Bloco: barreira magica
# =====================================================================================
def barrier_block():
    c = Canvas(16, 16)
    rnd = random.Random(5)
    for y in range(16):
        for x in range(16):
            v = 225 + rnd.randint(-12, 12)
            c.set(x, y, (v, v, v, 70 + rnd.randint(-8, 8)))
    # Losangos rúnicos internos
    for i in range(16):
        for (x, y) in ((i, (i + 8) % 16), (i, (24 - i) % 16)):
            if 1 <= x <= 14 and 1 <= y <= 14:
                c.set(x, y, (240, 240, 240, 130))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.set(x, y, (255, 255, 255, 200))
    # Moldura brilhante
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, (255, 255, 255, 215))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, (255, 255, 255, 255))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, (250, 250, 250, 170))
    c.save(os.path.join(TEX_BLOCK, "spell_barrier.png"))

    faces = {}
    for d in ("down", "up", "north", "south", "west", "east"):
        faces[d] = {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d, "tintindex": 0}
    write_json(os.path.join(ASSETS, "models", "block", "spell_barrier.json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"all": "isekai_mahou:block/spell_barrier", "particle": "isekai_mahou:block/spell_barrier"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces}],
    })
    write_json(os.path.join(ASSETS, "blockstates", "spell_barrier.json"),
               {"variants": {"": {"model": "isekai_mahou:block/spell_barrier"}}})
    write_json(os.path.join(ASSETS, "models", "item", "spell_barrier.json"),
               {"parent": "isekai_mahou:block/spell_barrier"})


# =====================================================================================
#  GeckoLib: ajudantes de UV em caixa e de animação
# =====================================================================================
def box_faces(u, v, w, h, d):
    """Regiões da UV em caixa (formato Bedrock/Java): {nome: (x, y, largura, altura)}."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


class Packer:
    """Empacota as caixas de UV em prateleiras numa textura."""
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.x = self.y = self.row = 0

    def place(self, w, h, d):
        fw, fh = 2 * (w + d), d + h
        if self.x + fw > self.w:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + fh > self.h:
            raise ValueError("textura pequena demais para as caixas")
        uv = (self.x, self.y)
        self.x += fw
        self.row = max(self.row, fh)
        return uv


def paint_box(c, u, v, w, h, d, base, edge=None, shade=True, face_fn=None):
    """Pinta todas as faces de uma caixa: topo claro, lados médios, base escura, borda opcional."""
    for name, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        mult = {"top": 1.1, "bottom": 0.72, "north": 1.0, "south": 0.88, "east": 0.94, "west": 0.94}[name] if shade else 1.0
        for y in range(fh):
            for x in range(fw):
                col = tuple(min(255, int(ch * mult)) for ch in base[:3]) + (base[3],)
                if edge and (x == 0 or y == 0 or x == fw - 1 or y == fh - 1):
                    col = edge
                if face_fn:
                    col = face_fn(name, x, y, fw, fh, col)
                if col is not None:
                    c.set(fx + x, fy + y, col)


def keys(fn, length, step):
    """Keyframes {tempo: [x, y, z]} amostrando fn(t) de 0 a length."""
    out = {}
    t = 0.0
    while t <= length + 1e-6:
        out[f"{round(t, 3)}"] = [round(v, 3) for v in fn(t)]
        t += step
    return out


def geo(identifier, tw, th, bones, bounds=(3, 3)):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{identifier}",
                "texture_width": tw,
                "texture_height": th,
                "visible_bounds_width": bounds[0],
                "visible_bounds_height": bounds[1],
                "visible_bounds_offset": [0, bounds[1] / 2, 0],
            },
            "bones": bones,
        }],
    }


# =====================================================================================
#  Torre (cristal flutuante)
# =====================================================================================
def turret():
    tw = th = 64
    pk = Packer(tw, th)
    tex = Canvas(tw, th)
    crystal_cubes = [  # (origin, size)
        ([-3, 7, -3], [6, 11, 6]),
        ([-2, 18, -2], [4, 4, 4]),
        ([-1, 22, -1], [2, 3, 2]),
        ([-2, 3, -2], [4, 4, 4]),
        ([-1, 0, -1], [2, 3, 2]),
    ]
    shard_cubes = [
        ([6, 10, -1], [2, 5, 2]),
        ([-8, 10, -1], [2, 5, 2]),
        ([-1, 10, 6], [2, 5, 2]),
        ([-1, 10, -8], [2, 5, 2]),
    ]

    def crystal_face(name, x, y, fw, fh, col):
        # Faceta: faixa de brilho diagonal + base mais escura
        if name in ("north", "south", "east", "west"):
            if (x + fh - y) % max(4, fw + 2) in (0, 1):
                return (255, 255, 255, 240)
            if y > fh * 0.7:
                return tuple(int(ch * 0.85) for ch in col[:3]) + (col[3],)
        return col

    cubes_c = []
    for origin, size in crystal_cubes:
        u, v = pk.place(*size)
        paint_box(tex, u, v, size[0], size[1], size[2], (225, 228, 235, 235), edge=(170, 175, 190, 245),
                  face_fn=crystal_face)
        cubes_c.append({"origin": origin, "size": size, "uv": [u, v]})
    cubes_s = []
    for origin, size in shard_cubes:
        u, v = pk.place(*size)
        paint_box(tex, u, v, size[0], size[1], size[2], (245, 245, 250, 240), edge=(200, 200, 215, 245))
        cubes_s.append({"origin": origin, "size": size, "uv": [u, v]})
    tex.save(os.path.join(TEX_ENTITY, "spell_turret.png"))

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "crystal", "parent": "root", "pivot": [0, 12, 0], "rotation": [0, 45, 0], "cubes": cubes_c},
        {"name": "shards", "parent": "root", "pivot": [0, 12, 0], "cubes": cubes_s},
    ]
    write_json(os.path.join(GEO, "spell_turret.geo.json"), geo("spell_turret", tw, th, bones, (2, 2)))

    L = 4.0
    anims = {
        "animation.spell_turret.idle": {
            "loop": True,
            "animation_length": L,
            "bones": {
                "crystal": {
                    "rotation": {"0.0": [0, 0, 0], "4.0": [0, 360, 0]},
                    "position": keys(lambda t: (0, 1.2 * math.sin(2 * math.pi * t / L), 0), L, 0.25),
                },
                "shards": {
                    "rotation": {"0.0": [0, 0, 0], "4.0": [0, -360, 0]},
                    "position": keys(lambda t: (0, -0.8 * math.sin(2 * math.pi * t / L), 0), L, 0.25),
                },
            },
        },
        "animation.spell_turret.shoot": {
            "loop": False,
            "animation_length": 0.4,
            "bones": {
                "crystal": {"scale": {"0.0": [1, 1, 1], "0.1": [1.25, 1.25, 1.25], "0.4": [1, 1, 1]}},
                "shards": {"scale": {"0.0": [1, 1, 1], "0.1": [1.45, 1.1, 1.45], "0.4": [1, 1, 1]}},
            },
        },
    }
    write_json(os.path.join(ANIM, "spell_turret.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =====================================================================================
#  Espírito invocado
# =====================================================================================
def conjurer():
    tw = th = 64
    pk = Packer(tw, th)
    tex = Canvas(tw, th)
    robe = (230, 232, 240, 235)
    robe_edge = (185, 188, 205, 240)

    def place(size, base, edge, face_fn=None):
        u, v = pk.place(*size)
        paint_box(tex, u, v, size[0], size[1], size[2], base, edge, face_fn=face_fn)
        return [u, v]

    def head_face(name, x, y, fw, fh, col):
        if name == "north":
            # Rosto escuro dentro do capuz, com olhos brilhantes
            if 1 <= x <= fw - 2 and 1 <= y <= fh - 1:
                if y == 3 and x in (1, 4):
                    return (255, 255, 255, 255)
                if y == 3 and x in (2, 3):
                    return (60, 60, 75, 250)
                return (45, 45, 60, 250)
        return col

    def torso_face(name, x, y, fw, fh, col):
        if name == "north":
            # Runa no peito
            if (x == fw // 2 or x == fw // 2 - 1) and 1 <= y <= fh - 2:
                return (255, 255, 255, 250)
            if y == 3 and 1 <= x <= fw - 2:
                return (255, 255, 255, 250)
        if y >= fh - 1:
            return (205, 208, 222, 235)
        return col

    def tail_face(name, x, y, fw, fh, col):
        a = int(col[3] * (1 - 0.45 * y / max(1, fh - 1)))
        return col[:3] + (a,)

    def hand_face(name, x, y, fw, fh, col):
        if name in ("north", "south", "east", "west") and y >= fh - 2:
            return (250, 250, 255, 245)
        if name == "bottom":
            return (250, 250, 255, 245)
        return col

    head = place([6, 6, 6], robe, robe_edge, head_face)
    peak = place([3, 2, 3], robe, robe_edge)
    torso = place([6, 7, 4], robe, robe_edge, torso_face)
    skirt = place([5, 4, 3], robe, robe_edge)
    tail = place([3, 3, 2], robe, None, tail_face)
    tip = place([1, 2, 1], (240, 240, 250, 140), None)
    arm_r = place([2, 5, 2], robe, robe_edge, hand_face)
    arm_l = place([2, 5, 2], robe, robe_edge, hand_face)
    orb = place([2, 2, 2], (255, 255, 255, 255), (235, 235, 255, 255))
    tex.save(os.path.join(TEX_ENTITY, "spell_conjurer.png"))

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": [
            {"origin": [-3, 8, -2], "size": [6, 7, 4], "uv": torso},
            {"origin": [-2.5, 4, -1.5], "size": [5, 4, 3], "uv": skirt},
        ]},
        {"name": "tail", "parent": "body", "pivot": [0, 4, 0], "cubes": [
            {"origin": [-1.5, 1, -1], "size": [3, 3, 2], "uv": tail},
            {"origin": [-0.5, -1, -0.5], "size": [1, 2, 1], "uv": tip},
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 15, 0], "cubes": [
            {"origin": [-3, 15, -3], "size": [6, 6, 6], "uv": head},
            {"origin": [-1.5, 21, -0.5], "size": [3, 2, 3], "uv": peak},
        ]},
        {"name": "right_arm", "parent": "body", "pivot": [-4, 14, 0], "cubes": [
            {"origin": [-5, 9, -1], "size": [2, 5, 2], "uv": arm_r},
        ]},
        {"name": "left_arm", "parent": "body", "pivot": [4, 14, 0], "cubes": [
            {"origin": [3, 9, -1], "size": [2, 5, 2], "uv": arm_l},
        ]},
        {"name": "orb", "parent": "root", "pivot": [0, 12, -5], "cubes": [
            {"origin": [-1, 11, -6], "size": [2, 2, 2], "uv": orb},
        ]},
    ]
    write_json(os.path.join(GEO, "spell_conjurer.geo.json"), geo("spell_conjurer", tw, th, bones, (2, 2)))

    L = 3.0
    w = 2 * math.pi / L
    anims = {
        "animation.spell_conjurer.idle": {
            "loop": True,
            "animation_length": L,
            "bones": {
                "body": {"rotation": keys(lambda t: (2 * math.sin(w * t), 0, 3 * math.sin(w * t)), L, 0.25)},
                "tail": {"rotation": keys(lambda t: (8 + 6 * math.sin(w * t + 1.2), 0, 10 * math.sin(w * t + 1.8)), L, 0.25)},
                "head": {"rotation": keys(lambda t: (3 * math.sin(w * t + 0.6), 0, 0), L, 0.25)},
                "right_arm": {"rotation": keys(lambda t: (-15 - 8 * math.sin(w * t), 0, 8), L, 0.25)},
                "left_arm": {"rotation": keys(lambda t: (-15 - 8 * math.sin(w * t + math.pi), 0, -8), L, 0.25)},
                "orb": {
                    "position": keys(lambda t: (0, 1.2 * math.sin(w * t), 0), L, 0.25),
                    "rotation": {"0.0": [0, 0, 0], "3.0": [0, 360, 0]},
                },
            },
        },
        "animation.spell_conjurer.cast": {
            "loop": False,
            "animation_length": 0.6,
            "bones": {
                "right_arm": {"rotation": {"0.0": [0, 0, 0], "0.15": [-100, 0, 0], "0.45": [-100, 0, 0], "0.6": [0, 0, 0]}},
                "left_arm": {"rotation": {"0.0": [0, 0, 0], "0.15": [-100, 0, 0], "0.45": [-100, 0, 0], "0.6": [0, 0, 0]}},
                "orb": {
                    "position": {"0.0": [0, 0, 0], "0.15": [0, 1, -3], "0.6": [0, 0, 0]},
                    "scale": {"0.0": [1, 1, 1], "0.15": [1.8, 1.8, 1.8], "0.6": [1, 1, 1]},
                },
                "head": {"rotation": {"0.0": [0, 0, 0], "0.15": [-12, 0, 0], "0.6": [0, 0, 0]}},
            },
        },
    }
    write_json(os.path.join(ANIM, "spell_conjurer.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =====================================================================================
#  Ilusão (humanoide com UV de skin de jogador)
# =====================================================================================
def illusion_geo(slim):
    aw = 3 if slim else 4
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 24, 0], "cubes": [
            {"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]},
            {"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 32], "inflate": 0.25},
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 24, 0], "cubes": [
            {"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]},
            {"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [32, 0], "inflate": 0.5},
        ]},
        {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0], "cubes": [
            {"origin": [-4 - aw, 12, -2], "size": [aw, 12, 4], "uv": [40, 16]},
            {"origin": [-4 - aw, 12, -2], "size": [aw, 12, 4], "uv": [40, 32], "inflate": 0.25},
        ]},
        {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0], "cubes": [
            {"origin": [4, 12, -2], "size": [aw, 12, 4], "uv": [32, 48]},
            {"origin": [4, 12, -2], "size": [aw, 12, 4], "uv": [48, 48], "inflate": 0.25},
        ]},
        {"name": "right_leg", "parent": "root", "pivot": [-1.9, 12, 0], "cubes": [
            {"origin": [-3.9, 0, -2], "size": [4, 12, 4], "uv": [0, 16]},
            {"origin": [-3.9, 0, -2], "size": [4, 12, 4], "uv": [0, 32], "inflate": 0.25},
        ]},
        {"name": "left_leg", "parent": "root", "pivot": [1.9, 12, 0], "cubes": [
            {"origin": [-0.1, 0, -2], "size": [4, 12, 4], "uv": [16, 48]},
            {"origin": [-0.1, 0, -2], "size": [4, 12, 4], "uv": [0, 48], "inflate": 0.25},
        ]},
    ]
    name = "spell_illusion_slim" if slim else "spell_illusion"
    write_json(os.path.join(GEO, name + ".geo.json"), geo(name, 64, 64, bones, (2, 3)))


def illusion_texture():
    """Silhueta sombria no formato de skin (usada quando o dono não é jogador)."""
    c = Canvas(64, 64)
    body = (78, 74, 92, 255)
    rim = (112, 106, 130, 255)
    deep = (52, 48, 64, 255)

    def shadow(name, x, y, fw, fh, col):
        if x == 0 or x == fw - 1:
            return rim
        if name == "bottom":
            return deep
        return col

    def face(name, x, y, fw, fh, col):
        if name == "north":
            if y == 4 and x in (1, 2, 5, 6):
                return (235, 235, 255, 255)
            if y == 3 and x in (1, 2, 5, 6):
                return (150, 145, 175, 255)
            if y >= 6 and 2 <= x <= 5:
                return deep
        return shadow(name, x, y, fw, fh, col)

    def robe(name, x, y, fw, fh, col):
        if name == "north" and x in (fw // 2 - 1, fw // 2) and y >= 2:
            return deep
        if name == "north" and y == 6:
            return rim
        return shadow(name, x, y, fw, fh, col)

    paint_box(c, 0, 0, 8, 8, 8, body, None, face_fn=face)
    paint_box(c, 16, 16, 8, 12, 4, body, None, face_fn=robe)
    for (u, v) in ((40, 16), (32, 48)):
        paint_box(c, u, v, 4, 12, 4, body, None, face_fn=shadow)
    for (u, v) in ((0, 16), (16, 48)):
        paint_box(c, u, v, 4, 12, 4, deep, None, face_fn=shadow)

    # Capuz na camada externa da cabeça: cobre tudo menos a frente
    def hood(name, x, y, fw, fh, col):
        if name == "north":
            if x in (0, fw - 1) or y == 0:
                return (60, 56, 74, 255)
            return None
        if name == "bottom":
            return None
        return (60, 56, 74, 255) if (x + y) % 5 else (70, 66, 86, 255)
    paint_box(c, 32, 0, 8, 8, 8, (60, 56, 74, 255), None, shade=False, face_fn=hood)

    # Manto (camada externa do corpo): só nas costas e laterais, esfarrapado embaixo
    def cloak(name, x, y, fw, fh, col):
        if name in ("south", "east", "west"):
            if y >= fh - 2 and (x * 7 + y) % 3 == 0:
                return None
            return (56, 52, 70, 255)
        return None
    paint_box(c, 16, 32, 8, 12, 4, (56, 52, 70, 255), None, shade=False, face_fn=cloak)
    c.save(os.path.join(TEX_ENTITY, "spell_illusion.png"))


def illusion_anims():
    L = 3.0
    w = 2 * math.pi / L
    W = 1.0
    ww = 2 * math.pi / W
    anims = {
        "animation.spell_illusion.idle": {
            "loop": True,
            "animation_length": L,
            "bones": {
                "right_arm": {"rotation": keys(lambda t: (2 * math.sin(w * t), 0, 3 + 2.5 * math.sin(w * t)), L, 0.25)},
                "left_arm": {"rotation": keys(lambda t: (-2 * math.sin(w * t), 0, -3 - 2.5 * math.sin(w * t)), L, 0.25)},
                "body": {"position": keys(lambda t: (0, 0.15 * math.sin(2 * w * t), 0), L, 0.25)},
            },
        },
        "animation.spell_illusion.walk": {
            "loop": True,
            "animation_length": W,
            "bones": {
                "right_leg": {"rotation": keys(lambda t: (30 * math.sin(ww * t), 0, 0), W, 0.125)},
                "left_leg": {"rotation": keys(lambda t: (-30 * math.sin(ww * t), 0, 0), W, 0.125)},
                "right_arm": {"rotation": keys(lambda t: (-25 * math.sin(ww * t), 0, 3), W, 0.125)},
                "left_arm": {"rotation": keys(lambda t: (25 * math.sin(ww * t), 0, -3), W, 0.125)},
            },
        },
    }
    write_json(os.path.join(ANIM, "spell_illusion.animation.json"), {"format_version": "1.8.0", "animations": anims})


def main():
    zone_circle()
    zone_inner()
    pulse_core()
    soft_ring_tex()
    rune_tex()
    orb_tex()
    portal_ring()
    portal_swirl()
    barrier_block()
    turret()
    conjurer()
    illusion_geo(False)
    illusion_geo(True)
    illusion_texture()
    illusion_anims()
    print("forms_b: assets gerados")


if __name__ == "__main__":
    main()
