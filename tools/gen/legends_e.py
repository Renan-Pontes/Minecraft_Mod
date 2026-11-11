"""
Assets do modulo "legends_e" — Magias de Nivel Supremo (rode da raiz:  py -I tools/gen/legends_e.py)

Texturas brancas com alfa (tingidas no renderer, somadas a cena):
  textures/entity/legends_e/supreme_ring_outer.png  256  anel externo do circulo supremo (runas, marcas, aros)
  textures/entity/legends_e/supreme_ring_mid.png    256  anel do meio (estrela de 12 pontas entrelacada)
  textures/entity/legends_e/supreme_sigil_core.png  128  selo central (heptagrama, olho)
  textures/entity/legends_e/supreme_rune_band.png   256x32 faixa de runas (repete na horizontal) — o "domo"
  textures/entity/legends_e/clock_face.png          256  mostrador do relogio da morte (numerais I..XII, aros)
  textures/entity/legends_e/goat_sigil.png          128  selo da Cabra Negra (cabeca de cabra, tentaculos, runas)
Modelos GeckoLib (geo + animacoes + textura + glowmask):
  dark_young (Filhote Sombrio), grasp_heart_hand (mao espectral + coracao)
Item: arcane_hourglass (textura + modelo), modelo do ovo do dark_young. Receita da Ampulheta Arcana.
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
D = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")
TEX = os.path.join(A, "textures", "entity", "legends_e")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


# =============================================================================================
#  Buffer de cobertura (alfa 0..1) com desenho suave por campo de distancia, so na caixa da forma
# =============================================================================================
class Mask:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = [[0.0] * w for _ in range(h)]

    def put(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h and v > self.a[y][x]:
            self.a[y][x] = v

    def line(self, p0, p1, width, strength=1.0):
        (ax, ay), (bx, by) = p0, p1
        x0, x1 = int(min(ax, bx) - width - 2), int(max(ax, bx) + width + 2)
        y0, y1 = int(min(ay, by) - width - 2), int(max(ay, by) + width + 2)
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                px, py = x + 0.5, y + 0.5
                t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
                d = math.hypot(px - (ax + dx * t), py - (ay + dy * t))
                self.put(x, y, clamp(width + 0.5 - d) * strength)

    def ring(self, cx, cy, r, width, strength=1.0):
        ext = r + width + 2
        for y in range(max(0, int(cy - ext)), min(self.h, int(cy + ext) + 1)):
            for x in range(max(0, int(cx - ext)), min(self.w, int(cx + ext) + 1)):
                d = abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r)
                if d < width + 1:
                    self.put(x, y, clamp(width + 0.5 - d) * strength)

    def disc(self, cx, cy, r, strength=1.0):
        for y in range(max(0, int(cy - r - 2)), min(self.h, int(cy + r + 3))):
            for x in range(max(0, int(cx - r - 2)), min(self.w, int(cx + r + 3))):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                self.put(x, y, clamp(r + 0.5 - d) * strength)

    def polyline(self, pts, width, strength=1.0, closed=False):
        n = len(pts)
        for i in range(n if closed else n - 1):
            self.line(pts[i], pts[(i + 1) % n], width, strength)

    def save(self, path, rgb=(255, 255, 255)):
        c = Canvas(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                v = self.a[y][x]
                if v > 0.003:
                    c.px[y][x] = (rgb[0], rgb[1], rgb[2], int(round(clamp(v) * 255)))
        c.save(path)


def polar(cx, cy, r, ang):
    return (cx + math.cos(ang) * r, cy + math.sin(ang) * r)


# Runas: tracos angulares numa grade 4x6 (cada runa e uma lista de segmentos em coordenadas 0..1)
RUNE_SHAPES = [
    [((0, 0), (0, 1)), ((0, 0.3), (1, 0)), ((0, 0.3), (1, 0.6))],
    [((0.5, 0), (0.5, 1)), ((0, 0.25), (1, 0.75))],
    [((0, 0), (1, 0.5)), ((1, 0.5), (0, 1)), ((0, 0), (0, 1))],
    [((0, 1), (0.5, 0)), ((0.5, 0), (1, 1)), ((0.2, 0.6), (0.8, 0.6))],
    [((0, 0), (1, 0)), ((0.5, 0), (0.5, 1)), ((0, 1), (1, 1))],
    [((0, 0), (1, 1)), ((1, 0), (0, 1)), ((0.5, 0), (0.5, 0.3))],
    [((0, 0), (0, 1)), ((1, 0), (1, 1)), ((0, 0.5), (1, 0.5))],
    [((0.5, 0), (0, 0.5)), ((0, 0.5), (0.5, 1)), ((0.5, 1), (1, 0.5)), ((1, 0.5), (0.5, 0))],
    [((0, 0), (1, 0)), ((1, 0), (0, 1)), ((0, 1), (1, 1))],
    [((0, 1), (0, 0)), ((0, 0), (1, 0.4)), ((0, 0.5), (1, 1))],
    [((0.5, 0), (0.5, 1)), ((0, 0.4), (0.5, 0)), ((1, 0.4), (0.5, 0))],
    [((0, 0), (0.5, 1)), ((0.5, 1), (1, 0)), ((0.25, 0.5), (0.75, 0.5))],
]


def rune_at(m, cx, cy, ang, size_t, size_r, rnd, width):
    """Desenha uma runa centrada em (cx,cy), alinhada a tangente do angulo `ang` (para aneis)."""
    shape = RUNE_SHAPES[rnd.randrange(len(RUNE_SHAPES))]
    tx, ty = -math.sin(ang), math.cos(ang)
    rx, ry = math.cos(ang), math.sin(ang)
    for (u0, v0), (u1, v1) in shape:
        p0 = (cx + tx * (u0 - 0.5) * size_t + rx * (0.5 - v0) * size_r, cy + ty * (u0 - 0.5) * size_t + ry * (0.5 - v0) * size_r)
        p1 = (cx + tx * (u1 - 0.5) * size_t + rx * (0.5 - v1) * size_r, cy + ty * (u1 - 0.5) * size_t + ry * (0.5 - v1) * size_r)
        m.line(p0, p1, width)


# =============================================================================================
#  Circulo Supremo
# =============================================================================================
def ring_outer():
    S = 256
    m = Mask(S, S)
    c = S / 2
    rnd = random.Random(7)
    for r, w in ((125.0, 1.4), (121.0, 0.7), (101.0, 0.7), (97.0, 1.2), (60.0, 0.6)):
        m.ring(c, c, r, w)
    # faixa de runas entre 101 e 121
    n = 40
    for i in range(n):
        a = math.tau * i / n
        x, y = polar(c, c, 111.0, a)
        rune_at(m, x, y, a, 11.0, 13.0, rnd, 0.55)
        # separador
        p0, p1 = polar(c, c, 103.5, a + math.pi / n), polar(c, c, 118.5, a + math.pi / n)
        m.line(p0, p1, 0.35, 0.7)
    # marcas finas entre 60 e 97 + triangulos apontando para dentro
    for i in range(72):
        a = math.tau * i / 72
        r0 = 90.0 if i % 6 else 80.0
        m.line(polar(c, c, r0, a), polar(c, c, 96.0, a), 0.45 if i % 6 else 0.8)
    for i in range(8):
        a = math.tau * i / 8 + math.tau / 16
        tip, l, r = polar(c, c, 64.0, a), polar(c, c, 78.0, a - 0.08), polar(c, c, 78.0, a + 0.08)
        m.polyline([tip, l, r], 0.7, closed=True)
        m.disc(*polar(c, c, 84.0, a), 2.2)
    m.save(os.path.join(TEX, "supreme_ring_outer.png"))


def ring_mid():
    S = 256
    m = Mask(S, S)
    c = S / 2
    rnd = random.Random(11)
    for r, w in ((124.0, 1.2), (118.0, 0.6), (40.0, 0.8)):
        m.ring(c, c, r, w)
    # estrela de 12 pontas entrelacada (dois hexagonos e um dodecagrama)
    pts = [polar(c, c, 117.0, math.tau * i / 12 - math.pi / 2) for i in range(12)]
    for i in range(12):
        m.line(pts[i], pts[(i + 5) % 12], 0.9)
    for k in range(2):
        hexa = [polar(c, c, 117.0, math.tau * (2 * i + k) / 12 - math.pi / 2) for i in range(6)]
        m.polyline(hexa, 0.7, closed=True)
    # pequenos circulos nos vertices com uma runa dentro
    for i in range(12):
        x, y = pts[i]
        m.ring(x, y, 6.5, 0.7)
        rune_at(m, x, y, math.tau * i / 12, 6.0, 7.0, rnd, 0.45)
    # anel interno de runas
    for i in range(16):
        a = math.tau * i / 16
        x, y = polar(c, c, 48.5, a)
        rune_at(m, x, y, a, 7.0, 8.0, rnd, 0.45)
    m.ring(c, c, 56.0, 0.5)
    m.save(os.path.join(TEX, "supreme_ring_mid.png"))


def sigil_core():
    S = 128
    m = Mask(S, S)
    c = S / 2
    for r, w in ((62.0, 1.1), (57.0, 0.5), (24.0, 0.7), (9.0, 0.8)):
        m.ring(c, c, r, w)
    # heptagrama {7/3}
    pts = [polar(c, c, 56.0, math.tau * i / 7 - math.pi / 2) for i in range(7)]
    for i in range(7):
        m.line(pts[i], pts[(i + 3) % 7], 0.8)
    for i in range(7):
        m.disc(*pts[i], 2.0)
    # "olho" no centro (amendoa + pupila)
    eye = []
    for i in range(33):
        t = i / 32.0
        x = c - 18 + 36 * t
        y = c - math.sin(math.pi * t) * 9
        eye.append((x, y))
    for i in range(32, -1, -1):
        t = i / 32.0
        eye.append((c - 18 + 36 * t, c + math.sin(math.pi * t) * 9))
    m.polyline(eye, 0.7)
    m.disc(c, c, 4.5)
    m.save(os.path.join(TEX, "supreme_sigil_core.png"))


def rune_band():
    W, H = 256, 32
    m = Mask(W, H)
    rnd = random.Random(23)
    m.line((0, 2.5), (W, 2.5), 0.8)
    m.line((0, 29.5), (W, 29.5), 0.8)
    m.line((0, 5.5), (W, 5.5), 0.35, 0.6)
    m.line((0, 26.5), (W, 26.5), 0.35, 0.6)
    n = 16
    for i in range(n):
        cx = (i + 0.5) * W / n
        rune_at(m, cx, 16.0, -math.pi / 2, 9.0, 14.0, rnd, 0.7)
        m.disc(i * W / n, 16.0, 1.2, 0.8)
    m.save(os.path.join(TEX, "supreme_rune_band.png"))


# =============================================================================================
#  Relogio da Morte (os 12 marcadores e o ponteiro sao desenhados no Java)
# =============================================================================================
ROMAN = ["XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"]


def roman_glyph(m, ch, x, y, ang, h, w, lw):
    """Letra romana (I, V, X) de altura h com base no ponto (x,y), 'em pe' apontando para o centro."""
    ux, uy = math.cos(ang), math.sin(ang)       # para fora (radial)
    tx, ty = -math.sin(ang), math.cos(ang)      # tangente

    def P(u, v):  # u ao longo da tangente (-0.5..0.5)*w, v radial 0..1 (para dentro)
        return (x + tx * u * w - ux * v * h, y + ty * u * w - uy * v * h)

    if ch == "I":
        m.line(P(0, 0), P(0, 1), lw)
    elif ch == "V":
        m.line(P(-0.5, 0), P(0, 1), lw)
        m.line(P(0.5, 0), P(0, 1), lw)
    elif ch == "X":
        m.line(P(-0.5, 0), P(0.5, 1), lw)
        m.line(P(0.5, 0), P(-0.5, 1), lw)


def clock_face():
    S = 256
    m = Mask(S, S)
    c = S / 2
    rnd = random.Random(31)
    for r, w in ((125.0, 1.6), (120.0, 0.6), (92.0, 0.9), (88.0, 0.4), (34.0, 0.8), (12.0, 1.0)):
        m.ring(c, c, r, w)
    # numerais romanos em pe, voltados para o centro
    for i, num in enumerate(ROMAN):
        a = math.tau * i / 12 - math.pi / 2
        widths = {"I": 4.0, "V": 7.0, "X": 7.0}
        total = sum(widths[ch] for ch in num) + 2.0 * (len(num) - 1)
        off = -total / 2
        for ch in num:
            wch = widths[ch]
            u = off + wch / 2
            bx, by = polar(c, c, 115.0, a)
            tx, ty = -math.sin(a), math.cos(a)
            roman_glyph(m, ch, bx + tx * u, by + ty * u, a, 18.0, wch, 0.9)
            off += wch + 2.0
    # minutos: 60 tracinhos finos entre 88 e 92
    for i in range(60):
        a = math.tau * i / 60
        m.line(polar(c, c, 86.0, a), polar(c, c, 91.0, a), 0.35, 0.8)
    # anel de runas entre 34 e 84 (fraco) + raios
    for i in range(24):
        a = math.tau * i / 24
        x, y = polar(c, c, 60.0, a)
        rune_at(m, x, y, a, 8.0, 10.0, rnd, 0.45)
    for i in range(12):
        a = math.tau * i / 12 - math.pi / 2
        m.line(polar(c, c, 36.0, a), polar(c, c, 46.0, a), 0.5, 0.8)
    m.save(os.path.join(TEX, "clock_face.png"))


# =============================================================================================
#  Selo da Cabra Negra
# =============================================================================================
def goat_sigil():
    S = 128
    m = Mask(S, S)
    c = S / 2
    rnd = random.Random(5)
    for r, w in ((62.0, 1.2), (57.0, 0.5), (47.0, 0.8)):
        m.ring(c, c, r, w)
    for i in range(18):
        a = math.tau * i / 18
        x, y = polar(c, c, 52.0, a)
        rune_at(m, x, y, a, 5.5, 6.5, rnd, 0.42)
    # cabeca de cabra: focinho alongado, chifres curvos para fora, olhos
    head = [(c - 10, c - 14), (c - 13, c - 4), (c - 9, c + 10), (c - 4, c + 24), (c + 4, c + 24), (c + 9, c + 10),
            (c + 13, c - 4), (c + 10, c - 14)]
    m.polyline(head, 1.1)
    for sgn in (-1, 1):
        horn = []
        for i in range(18):
            t = i / 17.0
            ang = math.pi * (0.55 + 1.15 * t)
            r = 10 + 16 * t
            horn.append((c + sgn * (8 + math.cos(ang) * -r * 0.9), c - 16 - math.sin(ang) * r * 0.85 + 10 * t))
        m.polyline(horn, 1.3 - 0.6 * 0.5)
        m.disc(c + sgn * 6, c - 3, 2.2)
        # orelhas
        m.line((c + sgn * 12, c - 8), (c + sgn * 22, c - 2), 0.9)
    # tentaculos saindo do queixo (barba de tentaculos)
    for k in range(5):
        pts = []
        base = c - 6 + k * 3
        for i in range(12):
            t = i / 11.0
            pts.append((base + math.sin(t * 5 + k) * 3 * t, c + 22 + t * 18))
        m.polyline(pts, 0.8 * (1.0 - 0.3), 0.9)
    m.save(os.path.join(TEX, "goat_sigil.png"))


# =============================================================================================
#  GeckoLib — utilitarios
# =============================================================================================
def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def cube(origin, size, uv, inflate=None, rotation=None, pivot=None):
    d = {"origin": [round(x, 3) for x in origin], "size": size, "uv": uv}
    if inflate:
        d["inflate"] = inflate
    if rotation:
        d["rotation"] = rotation
        d["pivot"] = pivot
    return d


def fmt_t(t):
    s = f"{t:.3f}".rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def kf(pairs):
    return {fmt_t(t): [round(x, 2) for x in v] for t, v in pairs}


def wave(length, steps, fn):
    """Amostra fn(t) em `steps` pontos ao longo de `length` segundos (fechando o laco)."""
    return kf([(length * i / steps, fn(length * i / steps)) for i in range(steps + 1)])


def geo(name, tw, th, bones, vw, vh, voff):
    write_json(os.path.join(A, "geo", "entity", name + ".geo.json"), {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry." + name, "texture_width": tw, "texture_height": th,
                            "visible_bounds_width": vw, "visible_bounds_height": vh, "visible_bounds_offset": voff},
            "bones": bones,
        }],
    })


def paint_box(c, u, v, w, h, d, fn):
    for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
        for yy in range(y, y + hh):
            for xx in range(x, x + ww):
                col = fn(k, xx - x, yy - y, ww, hh)
                if col is not None:
                    c.set(xx, yy, col)


# =============================================================================================
#  Filhote Sombrio (dark_young): arvore-cabra de tentaculos, bocas e cascos (~4 blocos)
# =============================================================================================
DY_W = DY_H = 128
UV = {
    "thigh": (0, 0, 5, 12, 5), "shin": (20, 0, 4, 12, 4), "hoof": (36, 0, 5, 4, 6),
    "lower": (0, 20, 14, 10, 12), "trunk": (52, 20, 12, 18, 10), "upper": (0, 50, 16, 8, 14),
    "lip": (60, 50, 6, 2, 2), "teeth": (60, 56, 6, 1, 1), "horn1": (76, 50, 3, 6, 3), "horn2": (88, 50, 3, 5, 3),
    "horn3": (100, 50, 2, 4, 2), "eye": (108, 50, 2, 2, 1), "t1": (0, 76, 4, 12, 4), "t2": (16, 76, 3, 10, 3),
    "t3": (28, 76, 2, 9, 2), "maw": (36, 76, 6, 3, 1), "spike": (52, 76, 2, 5, 2), "drool": (60, 76, 1, 4, 1),
}


def uv(name):
    u, v, _, _, _ = UV[name]
    return [u, v]


def dark_young_texture():
    c = Canvas(DY_W, DY_H)
    g = Canvas(DY_W, DY_H)
    rnd = random.Random(66)
    black = hex_rgba("#14101A")
    oil = hex_rgba("#241A30")
    sheen = hex_rgba("#4A2E66")
    green = hex_rgba("#7DFF4A")
    flesh = hex_rgba("#5A1020")
    gum = hex_rgba("#8C2238")
    tooth = hex_rgba("#E8E2D0")

    # casca oleosa preta com reflexos roxos; veios verticais que brilham fraco
    for name in ("thigh", "shin", "lower", "trunk", "upper", "t1", "t2", "t3", "spike"):
        u, v, w, h, d = UV[name]
        for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
            for yy in range(hh):
                for xx in range(ww):
                    r = rnd.random()
                    col = black if r < 0.55 else (oil if r < 0.88 else sheen)
                    if k not in ("top", "bottom") and ww > 3 and (xx * 7 + yy // 3) % 11 == 0 and rnd.random() < 0.6:
                        col = hex_rgba("#6A35A0")
                        g.set(x + xx, y + yy, hex_rgba("#9B4DFF", 150))
                    # ventosas nos tentaculos (pontos claros na face da frente)
                    if name in ("t1", "t2", "t3") and k == "front" and xx == ww // 2 and yy % 3 == 1:
                        col = hex_rgba("#5C3A78")
                    c.set(x + xx, y + yy, col)
    # cascos: queratina preta com borda cinza
    u, v, w, h, d = UV["hoof"]
    paint_box(c, u, v, w, h, d, lambda k, x, y, ww, hh: hex_rgba("#2E2A30") if (y == hh - 1 or k == "bottom")
              else (hex_rgba("#0C0A0E") if (x + y) % 3 else hex_rgba("#1C1820")))
    # chifres: osso escuro com aneis
    for name in ("horn1", "horn2", "horn3"):
        u, v, w, h, d = UV[name]
        paint_box(c, u, v, w, h, d, lambda k, x, y, ww, hh: hex_rgba("#3A3226") if y % 2 == 0 else hex_rgba("#5A4E3A"))
    # labios e gengiva
    u, v, w, h, d = UV["lip"]
    paint_box(c, u, v, w, h, d, lambda k, x, y, ww, hh: gum if (k in ("bottom", "top") or y == hh - 1) else flesh)
    u, v, w, h, d = UV["teeth"]
    paint_box(c, u, v, w, h, d, lambda k, x, y, ww, hh: tooth if x % 2 == 0 else hex_rgba("#BDB49C"))
    # garganta escura com brilho verde no fundo
    u, v, w, h, d = UV["maw"]
    for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
        for yy in range(hh):
            for xx in range(ww):
                col = hex_rgba("#1A0206") if (yy == 1 and 1 <= xx < ww - 1) else hex_rgba("#3A0610")
                c.set(x + xx, y + yy, col)
                if yy == 1 and 1 <= xx < ww - 1 and k == "front":
                    g.set(x + xx, y + yy, hex_rgba("#6BFF3A", 120))
    # olhos verde-amarelos brilhantes
    u, v, w, h, d = UV["eye"]
    for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
        for yy in range(hh):
            for xx in range(ww):
                col = hex_rgba("#E8FF6A") if k == "front" else hex_rgba("#7DFF4A")
                c.set(x + xx, y + yy, col)
                g.set(x + xx, y + yy, col)
    # baba esverdeada
    u, v, w, h, d = UV["drool"]
    for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
        for yy in range(hh):
            for xx in range(ww):
                c.set(x + xx, y + yy, green if yy < hh - 1 else hex_rgba("#B6FF8A"))
                g.set(x + xx, y + yy, hex_rgba("#7DFF4A", 200))
    c.save(os.path.join(A, "textures", "entity", "dark_young.png"))
    g.save(os.path.join(A, "textures", "entity", "dark_young_glowmask.png"))


def leg_bones(name, x, z, front):
    tilt = 12 if front else -12
    return [
        {"name": name, "parent": "root", "pivot": [x, 26, z], "rotation": [tilt, 0, 0], "cubes": [
            cube([x - 2.5, 14, z - 2.5], [5, 12, 5], uv("thigh")),
        ]},
        {"name": name + "_shin", "parent": name, "pivot": [x, 14, z], "rotation": [-2 * tilt, 0, 0], "cubes": [
            cube([x - 2, 3, z - 2], [4, 12, 4], uv("shin")),
            cube([x - 2.5, -1, z - 3], [5, 4, 6], uv("hoof")),
        ]},
    ]


def mouth_bones(name, y, side):
    """Boca na lateral do tronco: side 0 = frente (-z), 1 = esquerda (+x), -1 = direita (-x)."""
    rot = [0, 0, 0] if side == 0 else [0, -90 * side, 0]
    # o tronco tem 12 de largura e 10 de profundidade: as bocas laterais ficam mais para fora
    z = -5.2 if side == 0 else -6.2
    return [
        {"name": name, "parent": "body", "pivot": [0, y, 0], "rotation": rot, "cubes": [
            cube([-3, y + 0.5, z - 0.6], [6, 2, 2], uv("lip")),
            cube([-3, y + 0.1, z - 0.3], [6, 1, 1], uv("teeth")),
            cube([-3, y - 1.5, z + 0.2], [6, 3, 1], uv("maw")),
            cube([-0.5 + side * 1.5, y - 4.5, z - 0.4], [1, 4, 1], uv("drool")),
        ]},
        {"name": name + "_jaw", "parent": name, "pivot": [0, y - 0.5, z + 0.5], "cubes": [
            cube([-3, y - 2.5, z - 0.6], [6, 2, 2], uv("lip")),
            cube([-3, y - 1.1, z - 0.3], [6, 1, 1], uv("teeth")),
        ]},
    ]


TENTACLE_ANGLES = [0, 60, 120, 180, 240, 300]


def tentacle_bones(i):
    a = math.radians(TENTACLE_ANGLES[i])
    px, pz = round(math.sin(a) * 4.5, 2), round(-math.cos(a) * 4.0, 2)
    n = f"tentacle_{i + 1}"
    tilt = -38 - (i % 2) * 10
    return [
        {"name": n, "parent": "crown", "pivot": [px, 58, pz], "rotation": [tilt, TENTACLE_ANGLES[i], 0], "cubes": [
            cube([px - 2, 58, pz - 2], [4, 12, 4], uv("t1")),
        ]},
        {"name": n + "_b", "parent": n, "pivot": [px, 69, pz], "rotation": [-18, 0, 0], "cubes": [
            cube([px - 1.5, 69, pz - 1.5], [3, 10, 3], uv("t2")),
        ]},
        {"name": n + "_c", "parent": n + "_b", "pivot": [px, 78, pz], "rotation": [-22, 0, 0], "cubes": [
            cube([px - 1, 78, pz - 1], [2, 9, 2], uv("t3")),
        ]},
    ]


def arm_bones(side):
    n = "arm_l" if side > 0 else "arm_r"
    x = 7.5 * side
    return [
        {"name": n, "parent": "body", "pivot": [x, 46, 0], "rotation": [0, 0, 18 * side], "cubes": [
            cube([x - 2, 36, -2], [4, 10, 4], uv("t1")),
        ]},
        {"name": n + "_b", "parent": n, "pivot": [x, 36, 0], "rotation": [-8, 0, -10 * side], "cubes": [
            cube([x - 1.5, 26, -1.5], [3, 10, 3], uv("t2")),
        ]},
        {"name": n + "_c", "parent": n + "_b", "pivot": [x, 26, 0], "rotation": [-10, 0, -6 * side], "cubes": [
            cube([x - 1.5, 17, -1.5], [3, 9, 3], uv("t2")),
        ]},
        {"name": n + "_d", "parent": n + "_c", "pivot": [x, 17, 0], "rotation": [-14, 0, 8 * side], "cubes": [
            cube([x - 1, 9, -1], [2, 9, 2], uv("t3")),
        ]},
    ]


SKIRT_ANGLES = [30, 90, 150, 210, 270, 330]


def skirt_bones(i):
    """Tentáculos pendurados em volta da base do tronco (a "saia" da massa de tentáculos)."""
    a = math.radians(SKIRT_ANGLES[i])
    px, pz = round(math.sin(a) * 6.0, 2), round(-math.cos(a) * 5.0, 2)
    n = f"skirt_{i + 1}"
    return [
        {"name": n, "parent": "body", "pivot": [px, 30, pz], "rotation": [-24 - (i % 2) * 8, SKIRT_ANGLES[i], 0], "cubes": [
            cube([px - 1.5, 20, pz - 1.5], [3, 10, 3], uv("t2")),
        ]},
        {"name": n + "_b", "parent": n, "pivot": [px, 20, pz], "rotation": [14, 0, 0], "cubes": [
            cube([px - 1, 11, pz - 1], [2, 9, 2], uv("t3")),
        ]},
    ]


def dark_young_geo():
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    bones += leg_bones("leg_fl", 5.5, -4.5, True)
    bones += leg_bones("leg_fr", -5.5, -4.5, True)
    bones += leg_bones("leg_bl", 5.5, 4.5, False)
    bones += leg_bones("leg_br", -5.5, 4.5, False)
    bones.append({"name": "body", "parent": "root", "pivot": [0, 26, 0], "cubes": [
        cube([-7, 24, -6], [14, 10, 12], uv("lower")),
        cube([-6, 34, -5], [12, 18, 10], uv("trunk")),
        # espinhos de casca saindo do tronco
        cube([5, 40, -2], [2, 5, 2], uv("spike"), rotation=[0, 0, -35], pivot=[6, 40, -1]),
        cube([-7, 44, 1], [2, 5, 2], uv("spike"), rotation=[0, 0, 35], pivot=[-6, 44, 2]),
        cube([-1, 38, 4], [2, 5, 2], uv("spike"), rotation=[35, 0, 0], pivot=[0, 38, 5]),
    ]})
    bones.append({"name": "crown", "parent": "body", "pivot": [0, 52, 0], "cubes": [
        cube([-8, 52, -7], [16, 8, 14], uv("upper")),
        cube([-3.5, 55, -7.6], [2, 2, 1], uv("eye")),
        cube([1.5, 55, -7.6], [2, 2, 1], uv("eye")),
        cube([-1, 57.5, -7.6], [2, 2, 1], uv("eye")),
        cube([-5.5, 57, -7.4], [2, 2, 1], uv("eye")),
        cube([3.5, 57.3, -7.4], [2, 2, 1], uv("eye")),
    ]})
    for side in (1, -1):
        hn = "horn_l" if side > 0 else "horn_r"
        x = 6 * side
        bones.append({"name": hn, "parent": "crown", "pivot": [x, 59, -2], "rotation": [-20, 0, -30 * side], "cubes": [
            cube([x - 1.5, 59, -3.5], [3, 6, 3], uv("horn1")),
            cube([x - 1.5 + side * 2.5, 63, -4.5], [3, 5, 3], uv("horn2"), rotation=[0, 0, -45 * side], pivot=[x, 64, -3]),
            cube([x - 1 + side * 6, 64.5, -5.5], [2, 4, 2], uv("horn3"), rotation=[30, 0, -100 * side], pivot=[x + side * 6, 65, -4.5]),
        ]})
    for i in range(6):
        bones += tentacle_bones(i)
    for i in range(6):
        bones += skirt_bones(i)
    bones += arm_bones(1)
    bones += arm_bones(-1)
    bones += mouth_bones("mouth_front", 44, 0)
    bones += mouth_bones("mouth_left", 38, 1)
    bones += mouth_bones("mouth_right", 41, -1)
    geo("dark_young", DY_W, DY_H, bones, 5, 6, [0, 2.5, 0])


def dark_young_animations():
    """Animacoes do Filhote. ATENCAO: no GeckoLib a rotacao animada e SOMADA a pose de repouso do .geo; por isso
    todos os valores aqui sao deslocamentos a partir da pose (inclinacao dos tentaculos, pernas, bracos)."""
    S = math.sin
    tw = math.tau

    def tentacle_sway(length, amp, speed=1.0):
        out = {}
        for i in range(6):
            n = f"tentacle_{i + 1}"
            ph = i * 1.1
            out[n] = {"rotation": wave(length, 8, lambda t, ph=ph: [
                amp * S(tw * speed * t / length + ph), 0, amp * 0.6 * S(tw * speed * t / length + ph * 1.7)])}
            out[n + "_b"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * 1.2 * S(tw * speed * t / length + ph + 0.8), 0, 0])}
            out[n + "_c"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * 1.5 * S(tw * speed * t / length + ph + 1.6), 0, 0])}
        return out

    def arms(length, amp, speed=1.0):
        out = {}
        for side, n in ((1, "arm_l"), (-1, "arm_r")):
            ph = 0 if side > 0 else 2.0
            out[n] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * S(tw * speed * t / length + ph), 0, amp * 0.5 * S(tw * t / length + ph)])}
            out[n + "_b"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * S(tw * speed * t / length + ph + 0.9), 0, 0])}
            out[n + "_c"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * 1.2 * S(tw * speed * t / length + ph + 1.8), 0, 0])}
            out[n + "_d"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * 1.4 * S(tw * speed * t / length + ph + 2.7), 0, 0])}
        return out

    def skirt(length, amp, speed=1.0):
        out = {}
        for i in range(6):
            ph = i * 0.9
            out[f"skirt_{i + 1}"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * S(tw * speed * t / length + ph), 0, 0])}
            out[f"skirt_{i + 1}_b"] = {"rotation": wave(length, 8, lambda t, ph=ph: [amp * 1.4 * S(tw * speed * t / length + ph + 1.1), 0, 0])}
        return out

    def jaws(length, open_deg, speed=1.0):
        out = {}
        for k, n in enumerate(("mouth_front", "mouth_left", "mouth_right")):
            out[n + "_jaw"] = {"rotation": wave(length, 8, lambda t, k=k: [
                open_deg * (0.5 + 0.5 * S(tw * speed * t / length + k * 2.1)), 0, 0])}
        return out

    idle_len = 4.0
    idle = tentacle_sway(idle_len, 7)
    idle.update(arms(idle_len, 5))
    idle.update(jaws(idle_len, 18, 2))
    idle.update(skirt(idle_len, 6, 2))
    idle["body"] = {"rotation": wave(idle_len, 4, lambda t: [1.5 * S(tw * t / idle_len), 0, 1.0 * S(tw * t / idle_len + 1)]),
                    "position": wave(idle_len, 4, lambda t: [0, -0.4 + 0.4 * S(tw * t / idle_len), 0])}
    idle["crown"] = {"rotation": wave(idle_len, 4, lambda t: [0, 3 * S(tw * t / idle_len), 0])}

    walk_len = 1.2
    walk = tentacle_sway(walk_len, 10, 1.0)
    walk.update(arms(walk_len, 14, 1.0))
    walk.update(jaws(walk_len, 10, 1))
    walk.update(skirt(walk_len, 14, 1))
    for n, ph in (("leg_fl", 0.0), ("leg_br", 0.0), ("leg_fr", math.pi), ("leg_bl", math.pi)):
        walk[n] = {"rotation": wave(walk_len, 8, lambda t, ph=ph: [26 * S(tw * t / walk_len + ph), 0, 0])}
        walk[n + "_shin"] = {"rotation": wave(walk_len, 8, lambda t, ph=ph: [22 * max(0.0, S(tw * t / walk_len + ph + 1.2)), 0, 0])}
    walk["body"] = {"rotation": wave(walk_len, 8, lambda t: [4, 0, 4 * S(tw * t / walk_len)]),
                    "position": wave(walk_len, 8, lambda t: [0, 1.2 * abs(S(tw * t / walk_len)), 0])}

    # Pisao: empina (0-0.75 s), bate no chao em ~1.0 s (o golpe do servidor e em 20 ticks), assenta ate 1.4 s
    stomp = {
        "body": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-22, 0, 0]), (0.95, [14, 0, 0]), (1.1, [6, 0, 0]), (1.4, [0, 0, 0])]),
                 "position": kf([(0.0, [0, 0, 0]), (0.75, [0, 6, 2]), (0.95, [0, -2, -1]), (1.1, [0, -1, 0]), (1.4, [0, 0, 0])])},
        "leg_fl": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-82, 0, 0]), (0.95, [8, 0, 0]), (1.4, [0, 0, 0])])},
        "leg_fr": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-74, 0, 0]), (0.95, [10, 0, 0]), (1.4, [0, 0, 0])])},
        "leg_fl_shin": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-36, 0, 0]), (0.95, [14, 0, 0]), (1.4, [0, 0, 0])])},
        "leg_fr_shin": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-31, 0, 0]), (0.95, [14, 0, 0]), (1.4, [0, 0, 0])])},
        "arm_l": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-150, 0, 22]), (0.95, [-30, 0, 2]), (1.4, [0, 0, 0])])},
        "arm_r": {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [-150, 0, -22]), (0.95, [-30, 0, -2]), (1.4, [0, 0, 0])])},
    }
    for n in ("mouth_front", "mouth_left", "mouth_right"):
        stomp[n + "_jaw"] = {"rotation": kf([(0.0, [5, 0, 0]), (0.75, [35, 0, 0]), (1.0, [40, 0, 0]), (1.4, [5, 0, 0])])}
    for i in range(6):
        stomp[f"tentacle_{i + 1}"] = {"rotation": kf([(0.0, [0, 0, 0]), (0.75, [25, 0, 0]), (0.95, [-30, 0, 0]), (1.4, [0, 0, 0])])}

    # Rugido: bocas escancaradas, tentaculos abertos como uma coroa
    roar = {
        "body": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.6, [-10, 0, 3]), (0.8, [-12, 0, -3]), (1.0, [-10, 0, 3]),
                                 (1.2, [-12, 0, -3]), (1.4, [-10, 0, 2]), (2.0, [0, 0, 0])])},
        "crown": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-15, 0, 0]), (1.6, [-15, 0, 0]), (2.0, [0, 0, 0])])},
        "arm_l": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-40, 0, 57]), (1.6, [-45, 0, 62]), (2.0, [0, 0, 0])])},
        "arm_r": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-40, 0, -57]), (1.6, [-45, 0, -62]), (2.0, [0, 0, 0])])},
    }
    for n in ("mouth_front", "mouth_left", "mouth_right"):
        roar[n + "_jaw"] = {"rotation": kf([(0.0, [5, 0, 0]), (0.35, [55, 0, 0]), (1.6, [55, 0, 0]), (2.0, [5, 0, 0])])}
        roar[n] = {"scale": kf([(0.0, [1, 1, 1]), (0.35, [1.25, 1.25, 1.25]), (1.6, [1.25, 1.25, 1.25]), (2.0, [1, 1, 1])])}
    for i in range(6):
        n = f"tentacle_{i + 1}"
        roar[n] = {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.0, [-40, 0, 8]), (1.6, [-35, 0, -8]), (2.0, [0, 0, 0])])}
        roar[n + "_b"] = {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [28, 0, 0]), (1.6, [28, 0, 0]), (2.0, [0, 0, 0])])}
        roar[n + "_c"] = {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [37, 0, 0]), (1.6, [37, 0, 0]), (2.0, [0, 0, 0])])}

    # Nascimento: brota do chao (root sobe de -70), tentaculos se desenrolam de "em pe" ate a pose
    spawn = {
        "root": {"position": kf([(0.0, [0, -70, 0]), (0.8, [0, -38, 0]), (1.5, [0, -4, 0]), (1.8, [0, 1, 0]), (2.0, [0, 0, 0])]),
                 "rotation": kf([(0.0, [0, 0, -6]), (0.4, [0, 0, 6]), (0.8, [0, 0, -5]), (1.2, [0, 0, 4]), (1.6, [0, 0, -2]), (2.0, [0, 0, 0])])},
    }
    for i in range(6):
        n = f"tentacle_{i + 1}"
        bt = -38 - (i % 2) * 10
        spawn[n] = {"rotation": kf([(0.0, [-bt, 0, 0]), (1.2, [15, 0, 0]), (2.0, [0, 0, 0])])}
        spawn[n + "_c"] = {"rotation": kf([(0.0, [-68, 0, 0]), (1.4, [-28, 0, 0]), (2.0, [0, 0, 0])])}

    # Chicotada com o braco-tentaculo direito
    attack = {
        "arm_r": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-160, 0, -12]), (0.5, [-40, 0, 28]), (0.8, [0, 0, 0])])},
        "arm_r_b": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-32, 0, 0]), (0.5, [28, 0, 0]), (0.8, [0, 0, 0])])},
        "arm_r_c": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-40, 0, 0]), (0.5, [40, 0, 0]), (0.8, [0, 0, 0])])},
        "body": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-6, 12, 0]), (0.5, [8, -10, 0]), (0.8, [0, 0, 0])])},
        "mouth_front_jaw": {"rotation": kf([(0.0, [5, 0, 0]), (0.3, [40, 0, 0]), (0.8, [5, 0, 0])])},
    }

    anims = {
        "animation.dark_young.idle": {"loop": True, "animation_length": idle_len, "bones": idle},
        "animation.dark_young.walk": {"loop": True, "animation_length": walk_len, "bones": walk},
        "animation.dark_young.stomp": {"loop": False, "animation_length": 1.4, "bones": stomp},
        "animation.dark_young.roar": {"loop": False, "animation_length": 2.0, "bones": roar},
        "animation.dark_young.spawn": {"loop": "hold_on_last_frame", "animation_length": 2.0, "bones": spawn},
        "animation.dark_young.attack": {"loop": False, "animation_length": 0.8, "bones": attack},
    }
    write_json(os.path.join(A, "animations", "entity", "dark_young.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =============================================================================================
#  Agarrar o Coracao (grasp_heart_hand): mao espectral + coracao
# =============================================================================================
GH = 64
GUV = {"palm": (0, 0, 10, 12, 3), "f1": (26, 0, 2, 5, 2), "f2": (34, 0, 2, 4, 2), "thumb": (42, 0, 3, 5, 3),
       "fore": (0, 16, 8, 10, 3), "lobe": (22, 16, 5, 5, 5), "tip": (42, 16, 4, 4, 4), "aorta": (0, 30, 2, 3, 2),
       "vein": (8, 30, 1, 2, 1)}


def guv(n):
    return [GUV[n][0], GUV[n][1]]


def grasp_texture():
    c = Canvas(GH, GH)
    g = Canvas(GH, GH)
    rnd = random.Random(3)
    spirit = hex_rgba("#C9B8FF", 205)
    spirit_dark = hex_rgba("#7E6BC8", 215)
    spirit_light = hex_rgba("#F2ECFF", 225)
    for name in ("palm", "f1", "f2", "thumb", "fore"):
        u, v, w, h, d = GUV[name]
        for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
            for yy in range(hh):
                for xx in range(ww):
                    col = spirit
                    if name in ("f1", "f2", "thumb") and yy in (0, hh - 1):
                        col = spirit_dark   # juntas
                    if name == "palm" and k == "front" and (abs(xx - yy * 0.6 - 2) < 0.6 or abs(xx + yy * 0.4 - 8) < 0.6):
                        col = spirit_dark   # linhas da palma
                    if rnd.random() < 0.12:
                        col = spirit_light
                    if name == "fore":
                        # o antebraco some em fumaca para baixo
                        a = int(205 * clamp(yy / max(1, hh - 1) + 0.15))
                        col = (col[0], col[1], col[2], 255 - a if k not in ("top",) else 60)
                    c.set(x + xx, y + yy, col)
                    g.set(x + xx, y + yy, (220, 200, 255, min(col[3], 140)))
    red = hex_rgba("#C0182E")
    dark = hex_rgba("#6A0A18")
    bright = hex_rgba("#FF4A5A")
    for name in ("lobe", "tip", "aorta", "vein"):
        u, v, w, h, d = GUV[name]
        for k, (x, y, ww, hh) in faces(u, v, w, h, d).items():
            for yy in range(hh):
                for xx in range(ww):
                    r = rnd.random()
                    col = red if r < 0.6 else (dark if r < 0.85 else bright)
                    if name == "aorta":
                        col = hex_rgba("#8A2A3A") if yy else hex_rgba("#E87A8A")
                    if name == "vein":
                        col = hex_rgba("#3A4AA8")
                    if name in ("lobe", "tip") and (xx + 2 * yy) % 5 == 0:
                        col = hex_rgba("#3A4AA8")   # veias azuladas
                    c.set(x + xx, y + yy, col)
                    if col == bright:
                        g.set(x + xx, y + yy, hex_rgba("#FF6A7A", 160))
    c.save(os.path.join(A, "textures", "entity", "grasp_heart_hand.png"))
    g.save(os.path.join(A, "textures", "entity", "grasp_heart_hand_glowmask.png"))


FINGERS = [("index", -3.5, 5.0), ("middle", -1.0, 5.5), ("ring", 1.5, 5.0), ("pinky", 3.8, 4.2)]


def grasp_geo():
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "hand", "parent": "root", "pivot": [0, 4, 2], "cubes": [
                 cube([-4, -10, 0.5], [8, 10, 3], guv("fore")),
                 cube([-5, -1, 0.5], [10, 12, 3], guv("palm")),
             ]}]
    for n, x, _ in FINGERS:
        bones.append({"name": n, "parent": "hand", "pivot": [x, 11, 2], "cubes": [cube([x - 1, 11, 1], [2, 5, 2], guv("f1"))]})
        bones.append({"name": n + "_tip", "parent": n, "pivot": [x, 16, 2], "cubes": [cube([x - 1, 16, 1], [2, 4, 2], guv("f2"))]})
    bones.append({"name": "thumb", "parent": "hand", "pivot": [-5, 3, 2], "rotation": [0, 0, 40], "cubes": [
        cube([-6.5, 3, 0.5], [3, 5, 3], guv("thumb"))]})
    bones.append({"name": "thumb_tip", "parent": "thumb", "pivot": [-5, 8, 2], "cubes": [cube([-6, 8, 1], [2, 4, 2], guv("f2"))]})
    # coracao na frente da palma (-z)
    bones.append({"name": "heart", "parent": "root", "pivot": [0, 7, -4], "cubes": [
        cube([-4.2, 6.5, -6.5], [5, 5, 5], guv("lobe")),
        cube([-0.8, 6.5, -6.5], [5, 5, 5], guv("lobe")),
        cube([-2, 2.8, -6], [4, 4, 4], guv("tip"), rotation=[0, 0, 45], pivot=[0, 4.8, -4]),
        cube([-1.8, 11.2, -5], [2, 3, 2], guv("aorta")),
        cube([0.6, 11.0, -4.6], [2, 3, 2], guv("aorta"), rotation=[0, 0, -25], pivot=[1.6, 11, -3.6]),
        cube([-3.5, 9, -7.1], [1, 2, 1], guv("vein")),
        cube([2.4, 8, -7.1], [1, 2, 1], guv("vein")),
    ]})
    geo("grasp_heart_hand", GH, GH, bones, 2, 2.5, [0, 0.5, 0])


def grasp_animations():
    a = {
        "hand": {
            "position": kf([(0.0, [0, -2, 14]), (0.25, [0, -2, 14]), (0.75, [0, 0, 2]), (1.0, [0, 0, 0]), (1.25, [0, 0, -0.5]),
                            (1.6, [0, 2, 3])]),
            "scale": kf([(0.0, [0.2, 0.2, 0.2]), (0.25, [0.9, 0.9, 0.9]), (0.5, [1, 1, 1]), (1.35, [1.05, 1.05, 1.05]),
                         (1.7, [0.01, 0.01, 0.01])]),
            "rotation": kf([(0.0, [-20, 0, 0]), (0.75, [0, 0, 0]), (1.2, [6, 0, 0]), (1.3, [-4, 0, 2]), (1.4, [4, 0, -2]),
                            (1.7, [0, 0, 0])]),
        },
        "thumb": {"rotation": kf([(0.0, [0, 0, 30]), (0.8, [0, 0, 20]), (1.15, [-40, 0, -20]), (1.25, [-55, 0, -30]), (1.7, [-55, 0, -30])])},
        "thumb_tip": {"rotation": kf([(0.0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.2, [-45, 0, 0]), (1.7, [-45, 0, 0])])},
        "heart": {
            "position": kf([(0.0, [0, 0, -9]), (0.3, [0, 0, -9]), (0.6, [0, 0, -1]), (1.0, [0, 0, 0])]),
            "scale": kf([(0.0, [0.01, 0.01, 0.01]), (0.3, [0.4, 0.4, 0.4]), (0.5, [1.0, 1.0, 1.0]), (0.6, [1.18, 1.18, 1.18]),
                         (0.7, [0.95, 0.95, 0.95]), (0.8, [1.18, 1.18, 1.18]), (0.9, [0.95, 0.95, 0.95]),
                         (0.98, [1.2, 1.2, 1.2]), (1.05, [0.95, 0.95, 0.95]), (1.12, [1.2, 1.2, 1.2]),
                         (1.2, [0.8, 0.8, 0.8]), (1.25, [0.45, 0.6, 0.45]), (1.28, [0.01, 0.01, 0.01]), (1.7, [0.01, 0.01, 0.01])]),
        },
    }
    for k, (n, _, _) in enumerate(FINGERS):
        d = k * 0.03
        a[n] = {"rotation": kf([(0.0, [-30, 0, 0]), (0.8 + d, [-20, 0, 0]), (1.1 + d, [65, 0, 0]), (1.25, [85, 0, 0]),
                                (1.7, [85, 0, 0])])}
        a[n + "_tip"] = {"rotation": kf([(0.0, [-10, 0, 0]), (0.85 + d, [0, 0, 0]), (1.15 + d, [70, 0, 0]), (1.25, [95, 0, 0]),
                                         (1.7, [95, 0, 0])])}
    anims = {"animation.grasp_heart_hand.grasp": {"loop": "hold_on_last_frame", "animation_length": 1.7, "bones": a}}
    write_json(os.path.join(A, "animations", "entity", "grasp_heart_hand.animation.json"),
               {"format_version": "1.8.0", "animations": anims})


# =============================================================================================
#  Item: Ampulheta Arcana
# =============================================================================================
def hourglass():
    c = Canvas(16, 16)
    gold, gold_d, gold_l = hex_rgba("#E8B84A"), hex_rgba("#9A6A1E"), hex_rgba("#FFE9A0")
    glass = hex_rgba("#CFE8FF", 110)
    sand, sand_l = hex_rgba("#5AB4FF"), hex_rgba("#BDF0FF")
    # tampas
    for y in (1, 14):
        c.rect(3, y, 12, y, gold)
    c.rect(4, 0, 11, 0, gold_l)
    c.rect(4, 15, 11, 15, gold_d)
    # colunas laterais
    for y in range(2, 14):
        c.set(3, y, gold_d)
        c.set(12, y, gold)
    # vidro em forma de ampulheta
    for y in range(2, 14):
        half = int(round(4 - 3.2 * (1 - abs(y - 7.5) / 5.5)))
        half = max(1, half)
        for x in range(8 - half, 8 + half):
            c.set(x, y, glass)
    # areia azul (embaixo cheia, em cima pouca) + fio caindo
    for y in range(10, 14):
        half = max(1, int(round(4 - 3.2 * (1 - abs(y - 7.5) / 5.5))))
        for x in range(8 - half, 8 + half):
            c.set(x, y, sand if (x + y) % 3 else sand_l)
    for y in (2, 3, 4):
        half = max(1, int(round(4 - 3.2 * (1 - abs(y - 7.5) / 5.5))) - 1)
        for x in range(8 - half, 8 + half):
            if y >= 3:
                c.set(x, y, sand)
    for y in range(6, 10):
        c.set(7 if y % 2 else 8, y, sand_l)
    # runas brilhando no aro
    c.set(3, 5, hex_rgba("#BDF0FF"))
    c.set(12, 10, hex_rgba("#BDF0FF"))
    c.save(os.path.join(A, "textures", "item", "arcane_hourglass.png"))
    write_json(os.path.join(A, "models", "item", "arcane_hourglass.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/arcane_hourglass"}})
    write_json(os.path.join(A, "models", "item", "dark_young_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    write_json(os.path.join(D, "recipe", "arcane_hourglass.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["GCG", "DND", "GCG"],
        "key": {
            "G": {"item": "minecraft:gold_block"},
            "C": {"item": "isekai_mahou:mana_crystal"},
            "D": {"item": "minecraft:diamond"},
            "N": {"item": "minecraft:nether_star"},
        },
        "result": {"id": "isekai_mahou:arcane_hourglass", "count": 1},
    })


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    ring_outer()
    ring_mid()
    sigil_core()
    rune_band()
    clock_face()
    goat_sigil()
    dark_young_texture()
    dark_young_geo()
    dark_young_animations()
    grasp_texture()
    grasp_geo()
    grasp_animations()
    hourglass()
    print("legends_e: assets gerados")
