"""
Gera os assets do modulo origins_a (Magias de Origem: Runas, Maldicoes, Metamorfose).
Uso (na raiz do projeto):  py -I tools/gen/origins_a.py

Saidas:
  assets/isekai_mahou/textures/entity/origin_{explosive_seal,teleport_circle,teleport_star,ward,ward_wall}.png
      circulos de runa em branco/cinza com alfa (o renderer tinge com a cor da magia)
  assets/isekai_mahou/geo/entity/familiar_{crow,cat}.geo.json        (GeckoLib, Bedrock 1.12.0, UV de caixa)
  assets/isekai_mahou/animations/entity/familiar_{crow,cat}.animation.json
  assets/isekai_mahou/textures/entity/familiar_{crow,cat}.png (+ _glowmask: olhos brilhantes)
  assets/isekai_mahou/textures/mob_effect/<efeito>.png               (icones 18x18)
  assets/isekai_mahou/models/item/familiar_{crow,cat}_spawn_egg.json
  src/main/lang/origins_a/{pt_br,en_us}.json
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "origins_a")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


def tex(kind, name):
    return os.path.join(ASSETS, "textures", kind, name + ".png")


# =====================================================================================================
#  CIRCULOS DE RUNA (mascara de alfa -> branco tingivel)
# =====================================================================================================
class Mask:
    """Mascara de intensidade 0..1 com desenho por distancia (anti-alias simples) e brilho (halo)."""

    def __init__(self, size):
        self.n = size
        self.v = [[0.0] * size for _ in range(size)]

    def put(self, x, y, a):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.v[y][x] = max(self.v[y][x], min(1.0, a))

    def ring(self, r, width, a=1.0):
        c = self.n / 2
        for y in range(self.n):
            for x in range(self.n):
                d = math.hypot(x + 0.5 - c, y + 0.5 - c)
                k = 1.0 - abs(d - r) / (width / 2 + 0.5)
                if k > 0:
                    self.put(x, y, a * min(1.0, k * 1.5))

    def segment(self, x0, y0, x1, y1, width, a=1.0):
        """Linha com espessura (distancia ao segmento)."""
        minx, maxx = int(min(x0, x1) - width - 1), int(max(x0, x1) + width + 1)
        miny, maxy = int(min(y0, y1) - width - 1), int(max(y0, y1) + width + 1)
        dx, dy = x1 - x0, y1 - y0
        ll = dx * dx + dy * dy or 1e-6
        for y in range(max(0, miny), min(self.n, maxy + 1)):
            for x in range(max(0, minx), min(self.n, maxx + 1)):
                px, py = x + 0.5, y + 0.5
                t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / ll))
                d = math.hypot(px - (x0 + t * dx), py - (y0 + t * dy))
                k = 1.0 - d / (width / 2 + 0.5)
                if k > 0:
                    self.put(x, y, a * min(1.0, k * 1.5))

    def polygon(self, r, sides, step, rot, width, a=1.0):
        """Estrela/poligono inscrito: liga o vertice i ao i+step."""
        c = self.n / 2
        pts = [(c + r * math.cos(rot + 2 * math.pi * i / sides), c + r * math.sin(rot + 2 * math.pi * i / sides))
               for i in range(sides)]
        for i in range(sides):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + step) % sides]
            self.segment(x0, y0, x1, y1, width, a)

    def rune_band(self, r_in, r_out, count, seed, a=1.0, gap=0.25):
        """Faixa de runas tangentes ao circulo (mapeamento inverso: sem buracos)."""
        rnd = random.Random(seed)
        glyphs = [rune_bitmap(rnd) for _ in range(count)]
        c = self.n / 2
        band = r_out - r_in
        for y in range(self.n):
            for x in range(self.n):
                dx, dy = x + 0.5 - c, y + 0.5 - c
                d = math.hypot(dx, dy)
                if d < r_in or d >= r_out:
                    continue
                ang = (math.atan2(dy, dx) + 2 * math.pi) % (2 * math.pi)
                f = ang / (2 * math.pi) * count
                gi = int(f) % count
                local = f - int(f)
                if local < gap / 2 or local > 1 - gap / 2:
                    continue
                gx = int((local - gap / 2) / (1 - gap) * 5)
                gy = int((d - r_in) / band * 5)
                if 0 <= gx < 5 and 0 <= gy < 5 and glyphs[gi][4 - gy][gx]:
                    self.put(x, y, a)

    def dots(self, r, count, size, rot=0.0, a=1.0):
        c = self.n / 2
        for i in range(count):
            ang = rot + 2 * math.pi * i / count
            cx, cy = c + r * math.cos(ang), c + r * math.sin(ang)
            for y in range(int(cy - size - 1), int(cy + size + 2)):
                for x in range(int(cx - size - 1), int(cx + size + 2)):
                    k = 1.0 - math.hypot(x + 0.5 - cx, y + 0.5 - cy) / (size + 0.5)
                    if k > 0:
                        self.put(x, y, a * min(1.0, k * 2))

    def glow(self, radius=2, strength=0.45):
        """Halo: soma um borrão da mascara (o brilho em volta das linhas)."""
        n = self.n
        src = [row[:] for row in self.v]
        for y in range(n):
            for x in range(n):
                acc, cnt = 0.0, 0
                for oy in range(-radius, radius + 1):
                    for ox in range(-radius, radius + 1):
                        xx, yy = x + ox, y + oy
                        if 0 <= xx < n and 0 <= yy < n:
                            acc += src[yy][xx]
                            cnt += 1
                self.v[y][x] = max(src[y][x], min(1.0, acc / cnt * strength * 2.2))

    def to_canvas(self, core=(255, 255, 255), halo=(200, 200, 215)):
        cv = Canvas(self.n, self.n)
        for y in range(self.n):
            for x in range(self.n):
                a = self.v[y][x]
                if a <= 0.02:
                    continue
                col = mix(with_alpha(halo, 255), with_alpha(core, 255), min(1.0, a * 1.3))
                cv.set(x, y, with_alpha(col, int(round(255 * min(1.0, a)))))
        return cv


def rune_bitmap(rnd):
    """Runa 5x5 aleatoria mas 'legivel': hastes verticais + tracos."""
    g = [[0] * 5 for _ in range(5)]
    col = rnd.choice([1, 2, 3])
    for y in range(5):
        g[y][col] = 1
    for _ in range(rnd.randint(2, 3)):
        kind = rnd.randint(0, 3)
        y = rnd.randint(0, 4)
        if kind == 0:
            for x in range(5):
                g[y][x] = 1
        elif kind == 1:
            for i in range(3):
                xx, yy = col + i, y - i
                if 0 <= xx < 5 and 0 <= yy < 5:
                    g[yy][xx] = 1
        elif kind == 2:
            for i in range(3):
                xx, yy = col - i, y - i
                if 0 <= xx < 5 and 0 <= yy < 5:
                    g[yy][xx] = 1
        else:
            x = rnd.choice([0, 4])
            for yy in range(max(0, y - 1), min(5, y + 2)):
                g[yy][x] = 1
    return g


def gen_rune_textures():
    # --- Selo Explosivo (64): anel duplo, triangulo de fogo e runas ---
    m = Mask(64)
    m.ring(30.5, 1.6)
    m.ring(26.0, 1.0, 0.8)
    m.rune_band(19.5, 25.0, 10, seed=11, a=0.9)
    m.ring(18.5, 1.0, 0.8)
    m.polygon(17.5, 3, 1, -math.pi / 2, 1.4)
    m.polygon(17.5, 3, 1, math.pi / 2, 1.0, 0.7)
    m.ring(6.0, 1.4)
    m.dots(30.5, 6, 1.4, rot=math.pi / 6)
    m.dots(0.0, 1, 2.0)
    m.glow(2, 0.5)
    m.to_canvas(halo=(255, 210, 190)).save(tex("entity", "origin_explosive_seal"))

    # --- Circulo de Teleporte (128): anel externo com runas e marcas ---
    m = Mask(128)
    m.ring(62.0, 2.0)
    m.ring(57.0, 1.2, 0.85)
    m.rune_band(46.5, 55.5, 16, seed=23, a=0.95)
    m.ring(45.0, 1.4, 0.9)
    for i in range(16):
        ang = 2 * math.pi * i / 16
        c = 64
        m.segment(c + 57 * math.cos(ang), c + 57 * math.sin(ang), c + 62 * math.cos(ang), c + 62 * math.sin(ang), 1.2, 0.9)
    m.dots(62.0, 8, 2.2, rot=math.pi / 8)
    m.glow(2, 0.5)
    m.to_canvas(halo=(200, 205, 255)).save(tex("entity", "origin_teleport_circle"))

    # --- Estrela interna do Circulo (64): hexagrama + circulo central ---
    m = Mask(64)
    m.polygon(30.0, 6, 2, -math.pi / 2, 1.6)
    m.ring(30.5, 1.2, 0.8)
    m.ring(15.0, 1.4)
    m.polygon(15.0, 4, 1, math.pi / 4, 1.0, 0.8)
    m.dots(30.0, 6, 1.6, rot=-math.pi / 2)
    m.dots(0.0, 1, 3.0)
    m.glow(2, 0.5)
    m.to_canvas(halo=(220, 210, 255)).save(tex("entity", "origin_teleport_star"))

    # --- Ward (128): circulo de protecao com octograma e duas faixas de runas ---
    m = Mask(128)
    m.ring(62.5, 2.2)
    m.rune_band(53.0, 60.5, 20, seed=37, a=0.9)
    m.ring(52.0, 1.4)
    m.polygon(51.0, 8, 3, -math.pi / 2, 1.5)
    m.ring(34.0, 1.2, 0.85)
    m.rune_band(26.0, 33.0, 12, seed=41, a=0.8)
    m.ring(25.0, 1.2, 0.85)
    m.polygon(24.0, 4, 1, 0, 1.2)
    m.polygon(24.0, 4, 1, math.pi / 4, 1.2)
    m.ring(8.0, 1.6)
    m.dots(62.5, 8, 2.4)
    m.glow(2, 0.45)
    m.to_canvas(halo=(255, 240, 200)).save(tex("entity", "origin_ward"))

    # --- Parede do Ward (64x64, repete na horizontal): degrade para cima, linhas e runas subindo ---
    n = 64
    cv = Canvas(n, n)
    rnd = random.Random(5)
    glyphs = [rune_bitmap(rnd) for _ in range(4)]
    for y in range(n):
        fall = (y / (n - 1)) ** 1.6          # 0 no topo, 1 na base
        for x in range(n):
            a = 0.10 + 0.55 * fall
            if y >= n - 3:
                a = 0.95                      # base brilhante
            if x % 16 == 0:
                a = max(a, 0.35 * fall + 0.1)  # nervuras verticais
            cv.set(x, y, (255, 255, 255, int(255 * min(1.0, a))))
    # runas espalhadas (o renderer rola a textura para cima)
    for i, g in enumerate(glyphs):
        ox, oy = 4 + i * 16, 10 + (i % 2) * 24
        for gy in range(5):
            for gx in range(5):
                if g[gy][gx]:
                    for sy in range(2):
                        for sx in range(2):
                            cv.set(ox + gx * 2 + sx, oy + gy * 2 + sy, (255, 255, 255, 230))
    cv.save(tex("entity", "origin_ward_wall"))


# =====================================================================================================
#  FAMILIARES (GeckoLib): modelo + UV de caixa automatico + textura pintada pelo modelo + glowmask
# =====================================================================================================
class Geo:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []   # (bone_index, origin, size, paint)

    def bone(self, name, parent, pivot, cubes, rotation=None):
        b = {"name": name, "pivot": pivot}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = rotation
        self.bones.append(b)
        for (origin, size, paint) in cubes:
            self.cubes.append((len(self.bones) - 1, origin, size, paint))

    def pack(self):
        items = []
        for i, (_, origin, size, paint) in enumerate(self.cubes):
            w, h, d = (int(math.ceil(s)) for s in size)
            items.append((i, 2 * (d + w), d + h))
        for tw, th in ((64, 32), (64, 64), (128, 64)):
            uv, x, y, shelf, ok = {}, 0, 0, 0, True
            for (i, bw, bh) in sorted(items, key=lambda t: (-t[2], -t[1])):
                if x + bw > tw:
                    x, y, shelf = 0, y + shelf, 0
                if y + bh > th or bw > tw:
                    ok = False
                    break
                uv[i] = (x, y)
                x += bw
                shelf = max(shelf, bh)
            if ok:
                return tw, th, uv
        raise RuntimeError("modelo grande demais para a textura")

    def build(self, seed):
        tw, th, uv = self.pack()
        canvas, glow = Canvas(tw, th), Canvas(tw, th)
        rnd = random.Random(seed)
        bones = [dict(b, cubes=[]) for b in self.bones]
        for i, (bi, origin, size, paint) in enumerate(self.cubes):
            u, v = uv[i]
            bones[bi]["cubes"].append({"origin": origin, "size": size, "uv": [u, v]})
            paint_box(canvas, glow, u, v, [int(math.ceil(s)) for s in size], paint, rnd)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": tw, "texture_height": th,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones}]}
        return geo, canvas, glow


def paint_box(c, g, u, v, size, p, rnd):
    """Pinta as 6 faces: topo claro, base escura, laterais em degrade + pelagem/penas, marcas e brilho."""
    w, h, d = size
    base = p["color"]
    top, bottom = lighten(base, p.get("top", 0.18)), darken(base, 0.3)
    c.rect(u + d, v, u + d + w - 1, v + d - 1, top)
    c.rect(u + d + w, v, u + d + 2 * w - 1, v + d - 1, p.get("belly", bottom))
    for yy in range(h):
        t = yy / max(1, h - 1)
        col = lighten(base, 0.12 * (1 - t)) if t < 0.5 else darken(base, 0.25 * (t - 0.5) * 2)
        band = p.get("band")
        if band and yy >= h - band[0]:
            col = band[1]
        c.rect(u, v + d + yy, u + 2 * (d + w) - 1, v + d + yy, col)
    # pelagem/penas: pixels claros e escuros espalhados (textura menos chapada)
    fleck = p.get("fleck")
    if fleck:
        light, dark, amount = fleck
        for _ in range(max(1, int((2 * (d + w) * (h + d)) * amount))):
            x = rnd.randint(u, u + 2 * (d + w) - 1)
            y = rnd.randint(v, v + d + h - 1)
            if c.get(x, y)[3] > 0:
                c.set(x, y, light if rnd.random() < 0.5 else dark)
    # brilho azulado/arroxeado nas penas do corvo (faixa diagonal no topo e laterais)
    sheen = p.get("sheen")
    if sheen:
        for xx in range(2 * (d + w)):
            for yy in range(d + h):
                if (xx + yy) % 5 == 0 and c.get(u + xx, v + yy)[3] > 0 and rnd.random() < 0.7:
                    c.set(u + xx, v + yy, sheen)
    # penas: listras ao longo da asa (topo e base), pontas mais escuras
    feathers = p.get("feathers")
    if feathers:
        light, dark = feathers
        for face_u in (u + d, u + d + w):
            for xx in range(w):
                for yy in range(d):
                    col = mix(base, light, 0.35) if xx % 2 == 0 else base
                    if yy == d - 1 or xx == w - 1:
                        col = dark
                    c.set(face_u + xx, v + yy, col)
    # marcas na face da frente (norte): olhos, nariz...
    fx, fy = u + d, v + d
    for (ex, ey, col) in p.get("front", []):
        c.set(fx + ex, fy + ey, col)
    for (ex, ey, col) in p.get("glow_front", []):
        c.set(fx + ex, fy + ey, col)
        g.set(fx + ex, fy + ey, col)
    # marcas no topo (ex.: parte interna das orelhas)
    for (ex, ey, col) in p.get("top_marks", []):
        c.set(u + d + ex, v + ey, col)


def crow():
    black = rgb("#1C1C26")
    body = {"color": black, "fleck": (rgb("#2C2C3C"), rgb("#121218"), 0.12), "sheen": rgb("#3A3570")}
    head = {"color": rgb("#202030"), "fleck": (rgb("#2E2E40"), rgb("#141418"), 0.1),
            "glow_front": [(0, 1, rgb("#D8A8FF")), (2, 1, rgb("#D8A8FF"))]}
    beak = {"color": rgb("#3C3C46"), "top": 0.3, "band": (1, rgb("#24242A"))}
    wing = {"color": rgb("#191922"), "feathers": (rgb("#4A4580"), rgb("#0E0E14")), "sheen": rgb("#2E3460")}
    tip = {"color": rgb("#15151C"), "feathers": (rgb("#3A3A66"), rgb("#0A0A0E"))}
    tail = {"color": rgb("#17171F"), "feathers": (rgb("#3A3A66"), rgb("#0A0A0E"))}
    leg = {"color": rgb("#34343A"), "band": (1, rgb("#1E1E22"))}
    g = Geo("familiar_crow")
    g.bone("root", None, [0, 0, 0], [])
    g.bone("body", "root", [0, 4, 0], [([-2, 2, -3], [4, 4, 7], body)])
    g.bone("head", "body", [0, 6, -3], [([-1.5, 5, -6], [3, 3, 3], head), ([-0.5, 5.5, -8], [1, 1, 2], beak)])
    g.bone("beak_item", "head", [0, 5.5, -8.5], [])
    g.bone("left_wing", "body", [2, 5.5, -1], [([2, 5, -2.5], [6, 1, 5], wing)])
    g.bone("left_tip", "left_wing", [8, 5.5, -1], [([8, 5, -2], [4, 1, 4], tip)])
    g.bone("right_wing", "body", [-2, 5.5, -1], [([-8, 5, -2.5], [6, 1, 5], wing)])
    g.bone("right_tip", "right_wing", [-8, 5.5, -1], [([-12, 5, -2], [4, 1, 4], tip)])
    g.bone("tail", "body", [0, 4.5, 4], [([-1.5, 4, 4], [3, 1, 4], tail)])
    g.bone("legs", "body", [0, 2, 0], [([-1.5, 0, -0.5], [1, 2, 1], leg), ([0.5, 0, -0.5], [1, 2, 1], leg)])
    return g


def cat():
    fur = rgb("#1A1A20")
    body = {"color": fur, "fleck": (rgb("#26262E"), rgb("#101014"), 0.1), "belly": rgb("#24242C")}
    eye = rgb("#8CFF6A")
    head = {"color": rgb("#1E1E26"), "fleck": (rgb("#2A2A34"), rgb("#121216"), 0.08),
            "glow_front": [(1, 1, eye), (3, 1, eye)],
            "front": [(1, 2, rgb("#0C3A12")), (3, 2, rgb("#0C3A12")), (2, 3, rgb("#141418"))]}
    muzzle = {"color": rgb("#26262E"), "front": [(1, 0, rgb("#C77A8C")), (0, 1, rgb("#3A3A44")), (2, 1, rgb("#3A3A44"))]}
    ear = {"color": rgb("#1C1C24"), "front": [(0, 0, rgb("#5A3046"))]}
    collar = {"color": rgb("#6A2A8A"), "top": 0.3, "fleck": (rgb("#8A44AA"), rgb("#4A1A66"), 0.15)}
    bell = {"color": rgb("#E8C04A"), "top": 0.4, "band": (1, rgb("#A07818"))}
    leg = {"color": rgb("#1C1C22"), "band": (1, rgb("#2A2A32")), "fleck": (rgb("#26262E"), rgb("#101014"), 0.08)}
    tail = {"color": rgb("#18181E"), "fleck": (rgb("#24242C"), rgb("#0E0E12"), 0.1)}
    g = Geo("familiar_cat")
    g.bone("root", None, [0, 0, 0], [])
    g.bone("body", "root", [0, 7, 0], [([-2.5, 5, -5], [5, 4, 10], body)])
    g.bone("head", "body", [0, 9, -5], [([-2.5, 8, -9], [5, 4, 4], head), ([-1.5, 8, -10], [3, 2, 1], muzzle),
                                        ([-2, 12, -7], [1, 1, 2], ear), ([1, 12, -7], [1, 1, 2], ear)])
    g.bone("collar", "head", [0, 8, -6], [([-2.5, 7.5, -6.5], [5, 1, 2], collar), ([-0.5, 6.5, -7], [1, 1, 1], bell)])
    g.bone("tail1", "body", [0, 8.5, 5], [([-0.5, 8, 5], [1, 1, 6], tail)])
    g.bone("tail2", "tail1", [0, 8.5, 11], [([-0.5, 8, 11], [1, 1, 5], tail)])
    g.bone("leg_fl", "body", [1.5, 5, -3.5], [([0.5, 0, -4.5], [2, 5, 2], leg)])
    g.bone("leg_fr", "body", [-1.5, 5, -3.5], [([-2.5, 0, -4.5], [2, 5, 2], leg)])
    g.bone("leg_bl", "body", [1.5, 5, 3.5], [([0.5, 0, 2.5], [2, 5, 2], leg)])
    g.bone("leg_br", "body", [-1.5, 5, 3.5], [([-2.5, 0, 2.5], [2, 5, 2], leg)])
    return g


def wave(length, amp, phase=0.0, steps=8, axis=0, base=(0, 0, 0)):
    """Keyframes de uma senoide (graus ou pixels) num eixo, em loop."""
    kf = {}
    for i in range(steps + 1):
        t = length * i / steps
        vec = list(base)
        vec[axis] = round(base[axis] + amp * math.sin(2 * math.pi * i / steps + phase), 3)
        kf[f"{round(t, 4)}"] = vec
    return kf


def keys(*pairs):
    """Keyframes explicitos: keys((0, [x,y,z]), (0.2, [..]), ...)."""
    return {f"{round(t, 4)}": list(v) for (t, v) in pairs}


def anims(ident, spec):
    out = {}
    for name, (length, loop, bones) in spec.items():
        a = {"animation_length": length, "bones": bones}
        if loop:
            a["loop"] = True
        out[f"animation.{ident}.{name}"] = a
    return {"format_version": "1.8.0", "animations": out}


def gen_familiars():
    for name, g, seed in (("familiar_crow", crow(), 7), ("familiar_cat", cat(), 13)):
        geo, canvas, glow = g.build(seed)
        write_json(os.path.join(ASSETS, "geo", "entity", name + ".geo.json"), geo)
        canvas.save(tex("entity", name))
        glow.save(tex("entity", name + "_glowmask"))

    pi = math.pi
    crow_a = anims("familiar_crow", {
        "fly": (0.5, True, {
            "root": {"position": wave(0.5, 0.8, phase=pi / 2, axis=1)},
            "left_wing": {"rotation": wave(0.5, 40, axis=2)},
            "right_wing": {"rotation": wave(0.5, -40, axis=2)},
            "left_tip": {"rotation": wave(0.5, 20, phase=-0.8, axis=2)},
            "right_tip": {"rotation": wave(0.5, -20, phase=-0.8, axis=2)},
            "tail": {"rotation": wave(0.5, 6, phase=pi / 2)},
            "legs": {"rotation": keys((0, [40, 0, 0]))}}),
        "hover": (1.2, True, {
            "root": {"position": wave(1.2, 1.0, axis=1)},
            "left_wing": {"rotation": wave(1.2, 22, axis=2, base=(0, 0, 8))},
            "right_wing": {"rotation": wave(1.2, -22, axis=2, base=(0, 0, -8))},
            "left_tip": {"rotation": wave(1.2, 12, phase=-0.8, axis=2)},
            "right_tip": {"rotation": wave(1.2, -12, phase=-0.8, axis=2)},
            "head": {"rotation": wave(1.2, 8, phase=0.6, axis=1)},
            "tail": {"rotation": wave(1.2, 8)},
            "legs": {"rotation": keys((0, [25, 0, 0]))}}),
        "peck": (0.4, False, {
            "head": {"rotation": keys((0, [0, 0, 0]), (0.12, [55, 0, 0]), (0.25, [10, 0, 0]), (0.4, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.12, [18, 0, 0]), (0.4, [0, 0, 0]))}}),
    })
    cat_a = anims("familiar_cat", {
        "idle": (3.0, True, {
            "body": {"scale": wave(3.0, 0.02, axis=1, base=(1, 1, 1))},
            "tail1": {"rotation": wave(3.0, 18, axis=1, base=(35, 0, 0))},
            "tail2": {"rotation": wave(3.0, 12, phase=-0.7, axis=1, base=(-20, 0, 0))},
            "head": {"rotation": wave(3.0, 5, phase=0.5, axis=2)}}),
        "walk": (0.7, True, {
            "leg_fl": {"rotation": wave(0.7, 32)}, "leg_br": {"rotation": wave(0.7, 32)},
            "leg_fr": {"rotation": wave(0.7, 32, phase=pi)}, "leg_bl": {"rotation": wave(0.7, 32, phase=pi)},
            "tail1": {"rotation": wave(0.35, 10, axis=1, steps=4, base=(25, 0, 0))},
            "tail2": {"rotation": wave(0.35, 8, phase=-0.7, axis=1, steps=4, base=(-10, 0, 0))},
            "head": {"rotation": wave(0.35, 3, steps=4)}}),
        "sit": (3.0, True, {
            "root": {"position": keys((0, [0, -1.5, 0]))},
            "body": {"rotation": keys((0, [-28, 0, 0]))},
            "head": {"rotation": wave(3.0, 6, axis=1, base=(28, 0, 0))},
            "leg_fl": {"rotation": keys((0, [28, 0, 0]))},
            "leg_fr": {"rotation": keys((0, [28, 0, 0]))},
            "leg_bl": {"rotation": keys((0, [-62, 0, 0]))},
            "leg_br": {"rotation": keys((0, [-62, 0, 0]))},
            "tail1": {"rotation": wave(3.0, 15, axis=1, base=(-5, 40, 0))},
            "tail2": {"rotation": wave(3.0, 10, phase=-0.7, axis=1, base=(0, 30, 0))}}),
        "hiss": (0.8, False, {
            "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 1.2, 0]), (0.65, [0, 1.2, 0]), (0.8, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-6, 0, 0]), (0.65, [-6, 0, 0]), (0.8, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [18, 0, 0]), (0.4, [14, 0, 4]), (0.65, [18, 0, -4]), (0.8, [0, 0, 0]))},
            "tail1": {"rotation": keys((0, [35, 0, 0]), (0.15, [80, 0, 0]), (0.65, [80, 0, 0]), (0.8, [35, 0, 0]))},
            "tail2": {"rotation": keys((0, [-20, 0, 0]), (0.15, [-30, 0, 0]), (0.65, [-30, 0, 0]), (0.8, [-20, 0, 0]))},
            "leg_fl": {"rotation": keys((0, [0, 0, 0]), (0.15, [-10, 0, 0]), (0.65, [-10, 0, 0]), (0.8, [0, 0, 0]))},
            "leg_fr": {"rotation": keys((0, [0, 0, 0]), (0.15, [-10, 0, 0]), (0.65, [-10, 0, 0]), (0.8, [0, 0, 0]))}}),
        "scratch": (0.4, False, {
            "leg_fl": {"rotation": keys((0, [0, 0, 0]), (0.1, [-95, 0, -10]), (0.22, [-20, 0, 0]), (0.4, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.1, [12, 0, 0]), (0.4, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.1, [-8, 0, 0]), (0.4, [0, 0, 0]))}}),
    })
    write_json(os.path.join(ASSETS, "animations", "entity", "familiar_crow.animation.json"), crow_a)
    write_json(os.path.join(ASSETS, "animations", "entity", "familiar_cat.animation.json"), cat_a)


# =====================================================================================================
#  ICONES DOS EFEITOS (18x18)
# =====================================================================================================
def icon(rows, palette, name):
    c = Canvas(18, 18)
    c.from_rows(rows, palette, 1, 1)
    c.outline(rgb("#101010", 200))
    c.save(tex("mob_effect", name))


def gen_effect_icons():
    k = rgb("#1A1A1A")
    icon([
        "................",
        "..g..........g..",
        "..gg........gg..",
        "..gGg......gGg..",
        "..gGGggggggGGg..",
        "...gGGGGGGGGg...",
        "...gGGGGGGGGg...",
        "..gGGyGGGGyGGg..",
        "..gGGkGGGGkGGg..",
        "..gGGGGGGGGGGg..",
        "...gGGGwwGGGg...",
        "....gGwwwwGg....",
        "....gwwkkwwg....",
        ".....gwwwwg.....",
        "......gwwg......",
        ".......gg.......",
    ], {"g": rgb("#5E5E66"), "G": rgb("#9A9AA6"), "w": rgb("#E4E4EC"), "y": rgb("#FFD24A"), "k": k}, "wolf_form")
    icon([
        "................",
        "...bb......bb...",
        "..bBBb....bBBb..",
        "..bBBb....bBBb..",
        "...bb..bb..bb...",
        "......bBBb......",
        "......bBBb......",
        ".......bb.......",
        "....bbbbbbbb....",
        "...bBBBBBBBBb...",
        "..bBBBBBBBBBBb..",
        "..bBBBBBBBBBBb..",
        "..bBBBBBBBBBBb..",
        "...bBBBBBBBBb...",
        "....bbbbbbbb....",
        "................",
    ], {"b": rgb("#5A3A22"), "B": rgb("#A2744A")}, "bear_form")
    icon([
        "................",
        "..........ffF...",
        "........ffFFF...",
        "......ffFFFFb...",
        ".....fFFFFFb....",
        "....fFFFFFb.....",
        "...fFFFFFbb.....",
        "..fFFFFbbb......",
        "..fFFFbb..y.....",
        ".fFFbbb..yyw....",
        ".fFbb...yyww....",
        ".fbb....ywwk....",
        "..b......ww.....",
        "................",
        "................",
        "................",
    ], {"f": rgb("#5A4632"), "F": rgb("#9C7E5A"), "b": rgb("#3A2C20"), "y": rgb("#E8B040"), "w": rgb("#F0E6D2"),
        "k": k}, "falcon_form")
    icon([
        "................",
        "................",
        "........dd......",
        "......ddDd......",
        "....ddDDDDdd....",
        "...dDDDDDDDDd...",
        "..dDDkDDDDDDDd..",
        ".dDDDDDDDDDDDDd.",
        ".wwwDDDDDDDDDDd.",
        "..wwwwwDDDDDDdd.",
        "....wwwwwwDDd.dd",
        "........wwwd..dD",
        "..............dd",
        "................",
        "................",
        "................",
    ], {"d": rgb("#2E5E92"), "D": rgb("#6A9FD8"), "w": rgb("#DDEBFA"), "k": k}, "dolphin_form")
    icon([
        "................",
        ".l....l..l....l.",
        "..l...l..l...l..",
        "...l..l..l..l...",
        "....l.bBBb.l....",
        "llll.bBBBBb.llll",
        "....lbBrrBbl....",
        "...l.bBBBBb.l...",
        "..l..bBBBBb..l..",
        ".l..bBBBBBBb..l.",
        "l...bBBBBBBb...l",
        "....bBBBBBBb....",
        ".....bBBBBb.....",
        "......bbbb......",
        "................",
        "................",
    ], {"l": rgb("#3A2C2C"), "b": rgb("#2A1E1E"), "B": rgb("#5A4444"), "r": rgb("#FF3030")}, "spider_form")
    icon([
        "................",
        "..p....p....p...",
        "..pP...pP...pP..",
        "...pP...pP...pP.",
        "...pP...pP...pP.",
        "....pP...pP...pP",
        "....pP...pP...pP",
        ".....pP...pP...p",
        ".....pP...pP...p",
        "......pP...pP...",
        "......pP...pP...",
        ".......pP...pP..",
        ".......pP...pP..",
        "........p....p..",
        "................",
        "................",
    ], {"p": rgb("#6A3FB0"), "P": rgb("#D2B4FF")}, "elemental_beast_form")
    icon([
        "................",
        "......bbbb......",
        ".....bBBBBb.....",
        ".....bBxBxb.....",
        ".....bBBBBb..s..",
        "......bBBb..s...",
        "...bbbbBBbbbs...",
        "..bBBBBBBBBBBb..",
        "...bbbBBBBbbb...",
        ".....bBrBBb.....",
        ".....bBBBBb.....",
        ".....bBBBBb.....",
        "....bBBbbBBb....",
        "....bBb..bBb....",
        "....bbb..bbb....",
        "................",
    ], {"b": rgb("#5A4228"), "B": rgb("#B08A58"), "x": k, "r": rgb("#C01830"), "s": rgb("#D0D0D8")}, "voodoo_bound")
    icon([
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkwwwwwwkk...",
        "..kwwwggggwwwk..",
        ".kwwwgGGGGgwwwk.",
        ".kwwgGGkkGGgwwk.",
        ".kwwgGGkkGGgwwk.",
        ".kwwwgGGGGgwwwk.",
        "..kwwwggggwwwk..",
        "...kkwwwwwwkk...",
        ".....kkkkkk.....",
        "................",
        "................",
        "................",
    ], {"k": rgb("#1E3A1E"), "w": rgb("#E8F0DC"), "g": rgb("#2E9A3A"), "G": rgb("#6CFF6A")}, "evil_eye")


# =====================================================================================================
#  MODELOS DE ITEM (ovos)
# =====================================================================================================
def gen_items():
    for name in ("familiar_crow_spawn_egg", "familiar_cat_spawn_egg"):
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================================
#  TRADUCOES
# =====================================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def gen_lang():
    G = "glyph.isekai_mahou."
    # ---------------- Runas (Mago) ----------------
    t(G + "explosive_seal", "Selo Explosivo", "Explosive Seal")
    t(G + "explosive_seal.desc", "Grava uma runa-armadilha no chão que explode quando um inimigo pisa (não fere aliados nem quebra blocos).",
      "Engraves a trap rune on the ground that explodes when an enemy steps on it (spares allies and blocks).")
    t(G + "teleport_circle", "Círculo de Teleporte", "Teleport Circle")
    t(G + "teleport_circle.desc", "Desenha um círculo fixo no chão. Com dois círculos, pisar num leva ao outro. Conjure sobre um círculo seu para desfazê-lo.",
      "Draws a fixed circle on the ground. With two circles, stepping on one takes you to the other. Cast on your own circle to undo it.")
    t(G + "return_rune", "Runa de Retorno", "Return Rune")
    t(G + "return_rune.desc", "Concentre-se por 3s (sem se mover nem levar dano) para voltar ao último círculo ou à sua cama. Invertida, bane um inimigo para longe.",
      "Focus for 3s (without moving or taking damage) to return to your last circle or your bed. Inverted, it banishes an enemy far away.")
    t(G + "ward", "Ward", "Ward")
    t(G + "ward.desc", "Ergue uma barreira por 10 minutos: monstros são empurrados para fora e projéteis inimigos se desfazem nela.",
      "Raises a barrier for 10 minutes: monsters are pushed out and enemy projectiles shatter on it.")
    t(G + "temp_enchant", "Encantamento Temporário", "Temporary Enchantment")
    t(G + "temp_enchant.desc", "Encanta o item da mão por 5 minutos com Afiação, Fogo, Golpe... conforme o elemento da magia e o tipo de item.",
      "Enchants your held item for 5 minutes with Sharpness, Fire, Knockback... depending on the spell element and item type.")
    # ---------------- Maldições (Bruxa) ----------------
    t(G + "hex", "Hex", "Hex")
    t(G + "hex.desc", "Amaldiçoa o alvo com Fraqueza, Lentidão e uma leve Corrupção.",
      "Curses the target with Weakness, Slowness and a light Corruption.")
    t(G + "voodoo_doll", "Boneco de Vodu", "Voodoo Doll")
    t(G + "voodoo_doll.desc", "Liga você ao alvo por 60s: o próximo golpe que você receberia vai para ele (Amplificar: mais golpes e mais dano).",
      "Binds you to the target for 60s: the next hit you would take goes to it instead (Amplify: more hits and more damage).")
    t(G + "evil_eye", "Mau-Olhado", "Evil Eye")
    t(G + "evil_eye.desc", "Azar: o alvo erra golpes, colhe menos e fica Exposto (recebe mais dano).",
      "Bad luck: the target misses attacks, harvests less and becomes Exposed (takes more damage).")
    t(G + "frogify", "Virar Sapo", "Frogify")
    t(G + "frogify.desc", "Transforma um mob comum (não chefe, até 20 corações) num sapo por 20s. Depois ele volta exatamente como era.",
      "Turns a common mob (not a boss, up to 20 hearts) into a frog for 20s. Then it returns exactly as it was.")
    t(G + "familiar", "Familiar", "Familiar")
    t(G + "familiar.desc", "Em si mesmo: invoca um Corvo por 10 minutos (agachado: um Gato). O corvo bica inimigos e traz itens; o gato espanta creepers e phantoms.",
      "On yourself: summons a Crow for 10 minutes (sneaking: a Cat). The crow pecks enemies and fetches items; the cat scares creepers and phantoms.")
    # ---------------- Metamorfose (Druida) ----------------
    t(G + "wolf_form", "Forma de Lobo", "Wolf Form")
    t(G + "wolf_form.desc", "Vira um lobo: pequeno, rápido e de mordida forte contra feridos; fareja monstros por perto. Conjure de novo para voltar.",
      "Become a wolf: small, fast, with a strong bite on wounded foes; sniffs out nearby monsters. Cast again to change back.")
    t(G + "bear_form", "Forma de Urso", "Bear Form")
    t(G + "bear_form.desc", "Vira um urso: grande, resistente e com patadas que empurram e atrasam. Conjure de novo para voltar.",
      "Become a bear: big, tough, with swipes that knock back and slow. Cast again to change back.")
    t(G + "falcon_form", "Forma de Falcão", "Falcon Form")
    t(G + "falcon_form.desc", "Vira um falcão: no ar, plane segurando para frente e pule para bater as asas (4 vezes por voo). Sem dano de queda.",
      "Become a falcon: in the air, glide by holding forward and jump to flap your wings (4 times per flight). No fall damage.")
    t(G + "dolphin_form", "Forma de Golfinho", "Dolphin Form")
    t(G + "dolphin_form.desc", "Vira um golfinho: nada muito rápido, respira e enxerga debaixo d'água, mas é lento em terra.",
      "Become a dolphin: swims very fast, breathes and sees underwater, but is slow on land.")
    t(G + "spider_form", "Forma de Aranha", "Spider Form")
    t(G + "spider_form.desc", "Vira uma aranha: escala paredes (agachado para ficar grudado), enxerga no escuro e envenena com a mordida.",
      "Become a spider: climb walls (sneak to cling), see in the dark and poison with your bite.")
    t(G + "elemental_beast_form", "Fera Elemental", "Elemental Beast")
    t(G + "elemental_beast_form.desc", "Vira uma fera enorme do elemento da magia, com golpes e passivas do elemento (fogo: imune a fogo; terra: resistência; vento: sem queda...).",
      "Become a huge beast of the spell's element, with elemental strikes and passives (fire: fireproof; earth: resistance; wind: no fall damage...).")

    # ---------------- Efeitos ----------------
    E = "effect.isekai_mahou."
    t(E + "wolf_form", "Forma de Lobo", "Wolf Form")
    t(E + "bear_form", "Forma de Urso", "Bear Form")
    t(E + "falcon_form", "Forma de Falcão", "Falcon Form")
    t(E + "dolphin_form", "Forma de Golfinho", "Dolphin Form")
    t(E + "spider_form", "Forma de Aranha", "Spider Form")
    t(E + "elemental_beast_form", "Fera Elemental", "Elemental Beast")
    t(E + "voodoo_bound", "Boneco de Vodu", "Voodoo Bound")
    t(E + "evil_eye", "Mau-Olhado", "Evil Eye")

    # ---------------- Entidades e itens ----------------
    t("entity.isekai_mahou.origin_explosive_seal", "Selo Explosivo", "Explosive Seal")
    t("entity.isekai_mahou.origin_teleport_circle", "Círculo de Teleporte", "Teleport Circle")
    t("entity.isekai_mahou.origin_ward", "Ward", "Ward")
    t("entity.isekai_mahou.familiar_crow", "Corvo Familiar", "Familiar Crow")
    t("entity.isekai_mahou.familiar_cat", "Gato Familiar", "Familiar Cat")
    t("item.isekai_mahou.familiar_crow_spawn_egg", "Ovo Gerador de Corvo Familiar", "Familiar Crow Spawn Egg")
    t("item.isekai_mahou.familiar_cat_spawn_egg", "Ovo Gerador de Gato Familiar", "Familiar Cat Spawn Egg")

    # ---------------- Mensagens ----------------
    M = "message.isekai_mahou.origins_a."
    t(M + "circle_first", "Círculo de Teleporte desenhado. Desenhe outro para ligá-los.",
      "Teleport Circle drawn. Draw another one to link them.")
    t(M + "circle_linked", "Círculos de Teleporte ligados! Pise num para ir ao outro.",
      "Teleport Circles linked! Step on one to travel to the other.")
    t(M + "circle_removed", "Círculo de Teleporte desfeito.", "Teleport Circle undone.")
    t(M + "recall_start", "Runa de Retorno: concentre-se... (não se mova)", "Return Rune: focusing... (don't move)")
    t(M + "recall_moved", "Você se moveu: a Runa de Retorno se desfez.", "You moved: the Return Rune faded.")
    t(M + "recall_hurt", "O golpe quebrou sua concentração: a Runa de Retorno se desfez.",
      "The hit broke your focus: the Return Rune faded.")
    t(M + "recall_circle", "Você voltou ao seu Círculo de Teleporte.", "You returned to your Teleport Circle.")
    t(M + "recall_home", "Você voltou para casa.", "You returned home.")
    t(M + "recall_spawn", "Sem cama nem círculo: você voltou ao ponto de nascimento do mundo.",
      "No bed or circle: you returned to the world spawn.")
    t(M + "enchant_ok", "%s recebeu um encantamento temporário (%s min).", "%s received a temporary enchantment (%s min).")
    t(M + "enchant_fail", "Segure uma arma ou ferramenta para encantar.", "Hold a weapon or tool to enchant.")
    t(M + "enchant_faded", "Um encantamento temporário se dissipou.", "A temporary enchantment faded.")
    t(M + "voodoo_bound", "Vodu: %s está ligado a você (%s golpe(s)).", "Voodoo: %s is bound to you (%s hit(s)).")
    t(M + "voodoo_left", "O boneco absorveu o golpe! Restam %s.", "The doll took the hit! %s left.")
    t(M + "voodoo_spent", "O Boneco de Vodu se desfez.", "The Voodoo Doll came apart.")
    t(M + "frogify_resist", "%s resistiu à maldição!", "%s resisted the curse!")
    t(M + "familiar_crow", "Um Corvo Familiar atende ao seu chamado (%s min).", "A Familiar Crow answers your call (%s min).")
    t(M + "familiar_cat", "Um Gato Familiar atende ao seu chamado (%s min).", "A Familiar Cat answers your call (%s min).")
    t(M + "shape_enter", "Metamorfose: %s", "Shapeshift: %s")
    t(M + "shape_leave", "Você voltou à forma humana.", "You returned to your human form.")
    t("tooltip.isekai_mahou.origins_a.temp_enchant", "Encantamento temporário: %s", "Temporary enchantment: %s")

    pt = {k: v[0] for k, v in L.items()}
    en = {k: v[1] for k, v in L.items()}
    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


if __name__ == "__main__":
    gen_rune_textures()
    gen_familiars()
    gen_effect_icons()
    gen_items()
    gen_lang()
    print("origins_a: assets gerados")
