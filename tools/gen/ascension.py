"""Gera os assets/dados do modulo "ascension" (Ritual de Ascensao).

Uso (na raiz do projeto):  py -I tools/gen/ascension.py

Gera: texturas dos blocos, circulos magicos do chao, textura/brilho da Sombra, modelos de bloco/item,
blockstates, modelo GeckoLib (largo e slim) + animacoes, loot tables, receitas, tributos dos Circulos e a tag
de picareta (merge).

v0.9 ("Ascensao facil"): receitas mais baratas (pedra de tijolo no lugar de deepslate polido, 2 pedestais e 8 pedras
runicas por receita), tributos ~1/4 das essencias arcanas e sem diamante/estrela do Nether (trofeus de chefe da rota
por id, com reserva) e os selos "Tributo do Circulo N" (ascension_tribute_2..10: textura + modelo).

v1.0: textura do DOMO de runas da barreira da Tribulacao (misc/ascension_dome, ladrilho 64x64 que repete nos dois
eixos). So ela:  py -I tools/gen/ascension.py dome
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/ascension")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ------------------------------------------------------------------------------------------------
# Paleta
# ------------------------------------------------------------------------------------------------
ST0 = hex_rgba("1C1726")   # pedra mais escura (rejunte)
ST1 = hex_rgba("2B2438")
ST2 = hex_rgba("3A3150")
ST3 = hex_rgba("4A4064")
ST4 = hex_rgba("5B5078")
GD0 = hex_rgba("6B4F12")
GD1 = hex_rgba("8A6A1E")
GD2 = hex_rgba("C9A23A")
GD3 = hex_rgba("F2D06B")
AR0 = hex_rgba("3A1E7A")
AR1 = hex_rgba("6A3FD0")
AR2 = hex_rgba("9B6BFF")
AR3 = hex_rgba("C9AEFF")
AR4 = hex_rgba("F0E6FF")
RS0 = hex_rgba("2E2E3A")   # pedra runica (cinza azulado)
RS1 = hex_rgba("3D3D4C")
RS2 = hex_rgba("4F4F61")
RS3 = hex_rgba("63637A")


def stone_fill(c, x0, y0, x1, y1, base, seed, amount=0.07):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            f = (rnd.random() - 0.5) * 2 * amount
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))


def save_tex(c, rel):
    c.save(os.path.join(A, "textures", rel + ".png"))


# ------------------------------------------------------------------------------------------------
# Nucleo de Ascensao
# ------------------------------------------------------------------------------------------------
def core_side():
    c = Canvas(16, 16)
    # Faixa dourada da tampa (linhas 3-5) e linhas 0-2 iguais (para particulas)
    for y in range(0, 6):
        tone = [GD2, GD3, GD2, GD3, GD2, GD1][y]
        for x in range(16):
            col = tone
            if x in (0, 15):
                col = darken(tone, 0.35)
            c.set(x, y, col)
    c.set(7, 4, AR2); c.set(8, 4, AR3)
    c.set(3, 4, AR1); c.set(12, 4, AR1)
    # Coluna (linhas 6-11)
    stone_fill(c, 0, 6, 15, 11, ST2, 11)
    for y in range(6, 12):
        c.set(2, y, ST1); c.set(13, y, ST1)
        c.set(3, y, ST3); c.set(12, y, ST3)
        c.set(1, y, ST1); c.set(14, y, ST1)
    rune = [".##.", "#..#", ".##.", ".##.", "#..#", ".##."]
    for ry, row in enumerate(rune):
        for rx, ch in enumerate(row):
            if ch == "#":
                c.set(6 + rx, 6 + ry, AR3 if ry in (2, 3) else AR2)
    c.set(7, 8, AR4); c.set(8, 9, AR4)
    # Friso dourado (linha 12) e base em blocos (13-15)
    for x in range(16):
        c.set(x, 12, GD2 if 0 < x < 15 else GD1)
    stone_fill(c, 0, 13, 15, 15, ST2, 12)
    for x in range(16):
        c.set(x, 15, ST1)
    for y in (13, 14):
        c.set(4, y, ST0); c.set(11, y, ST0)
    for x in range(16):
        if c.get(x, 13) != ST0:
            c.set(x, 13, lighten(c.get(x, 13), 0.12))
    save_tex(c, "block/ascension_core_side")


def core_top():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST1, 21, 0.06)
    c.outline_rect(0, 0, 15, 15, ST0)
    c.outline_rect(1, 1, 14, 14, GD2)
    c.set(1, 1, GD3); c.set(14, 1, GD1); c.set(1, 14, GD1); c.set(14, 14, GD1)
    c.circle(8, 8, 6.0, GD2)
    c.circle(8, 8, 5.1, ST1)
    c.circle(8, 8, 3.9, AR0)
    c.circle(8, 8, 3.0, AR1)
    c.circle(8, 8, 2.0, AR2)
    c.circle(8, 8, 1.1, AR4)
    for (x, y) in ((8, 2), (7, 2), (8, 13), (7, 13), (2, 8), (2, 7), (13, 8), (13, 7)):
        c.set(x, y, AR3)
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.set(x, y, AR2)
    c.set(6, 6, AR3)
    save_tex(c, "block/ascension_core_top")


def core_bottom():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST2, 31, 0.08)
    for i in range(16):
        c.set(i, 0, ST0); c.set(i, 8, ST0); c.set(0, i, ST0); c.set(8, i, ST0)
    for (x0, y0) in ((1, 1), (9, 1), (1, 9), (9, 9)):
        for i in range(7):
            c.set(x0 + i, y0, lighten(c.get(x0 + i, y0), 0.15))
            c.set(x0, y0 + i, lighten(c.get(x0, y0 + i), 0.1))
    save_tex(c, "block/ascension_core_bottom")


# ------------------------------------------------------------------------------------------------
# Pedestal
# ------------------------------------------------------------------------------------------------
def pedestal_side():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST2, 41)
    # Tampa (linhas 4-5): ouro
    for x in range(16):
        c.set(x, 4, GD3 if 2 < x < 13 else GD2)
        c.set(x, 5, GD2 if 2 < x < 13 else GD1)
    for y in range(0, 4):
        for x in range(16):
            c.set(x, y, GD2 if y % 2 == 0 else GD1)
    # Coluna (linhas 6-13, colunas 4-11): pedra polida com caneluras e uma gema
    stone_fill(c, 4, 6, 11, 13, ST3, 42, 0.05)
    for y in range(6, 14):
        c.set(4, y, ST1); c.set(11, y, ST1)
        c.set(6, y, ST2); c.set(9, y, ST2)
        c.set(5, y, ST4)
    c.set(7, 9, AR2); c.set(8, 9, AR3); c.set(7, 10, AR1); c.set(8, 10, AR2)
    c.set(7, 8, ST1); c.set(8, 8, ST1); c.set(7, 11, ST1); c.set(8, 11, ST1)
    # Base (linhas 14-15)
    for x in range(16):
        c.set(x, 14, GD2 if 0 < x < 15 else GD1)
        c.set(x, 15, ST1)
    save_tex(c, "block/ascension_pedestal_side")


def pedestal_top():
    c = Canvas(16, 16)
    stone_fill(c, 0, 0, 15, 15, ST2, 51, 0.06)
    c.outline_rect(1, 1, 14, 14, ST1)
    c.outline_rect(2, 2, 13, 13, GD2)
    c.set(2, 2, GD3); c.set(13, 2, GD1); c.set(2, 13, GD1); c.set(13, 13, GD1)
    stone_fill(c, 3, 3, 12, 12, ST1, 52, 0.05)
    c.circle(8, 8, 4.6, GD1)
    c.circle(8, 8, 3.8, AR0)
    c.circle(8, 8, 3.0, AR1)
    c.circle(8, 8, 1.8, AR2)
    c.set(7, 7, AR4)
    for (x, y) in ((8, 3), (7, 3), (8, 12), (7, 12), (3, 8), (3, 7), (12, 8), (12, 7)):
        c.set(x, y, AR2)
    save_tex(c, "block/ascension_pedestal_top")


# ------------------------------------------------------------------------------------------------
# Bloco de Cristal de Mana (facetas tipo Voronoi)
# ------------------------------------------------------------------------------------------------
def mana_crystal_block():
    c = Canvas(16, 16)
    rnd = random.Random(61)
    seeds = [(rnd.uniform(0, 16), rnd.uniform(0, 16)) for _ in range(9)]
    tones = [AR1, AR2, mix(AR1, AR2, 0.5), AR0, AR2, mix(AR2, AR3, 0.4), AR1, mix(AR0, AR1, 0.5), AR2]
    for y in range(16):
        for x in range(16):
            ds = []
            for i, (sx, sy) in enumerate(seeds):
                # distancia em toro (a textura repete sem emenda)
                dx = min(abs(x + 0.5 - sx), 16 - abs(x + 0.5 - sx))
                dy = min(abs(y + 0.5 - sy), 16 - abs(y + 0.5 - sy))
                ds.append((dx * dx + dy * dy, i))
            ds.sort()
            d0, i0 = ds[0]
            d1, _ = ds[1]
            col = tones[i0]
            # brilho diagonal dentro de cada faceta (luz do canto superior esquerdo)
            sx, sy = seeds[i0]
            f = ((x - sx) + (y - sy)) / 16.0
            col = lighten(col, max(0.0, -f) * 0.6) if f < 0 else darken(col, min(0.4, f * 0.5))
            if math.sqrt(d1) - math.sqrt(d0) < 0.7:
                col = darken(AR0, 0.15)
            c.set(x, y, col)
    for (x, y) in ((3, 2), (11, 5), (6, 11), (13, 13), (1, 9)):
        c.set(x, y, AR4)
        c.set(x + 1, y, AR3)
    save_tex(c, "block/mana_crystal_block")


# ------------------------------------------------------------------------------------------------
# Pedra Runica
# ------------------------------------------------------------------------------------------------
def rune_bricks(c, seed):
    stone_fill(c, 0, 0, 15, 15, RS2, seed, 0.08)
    for x in range(16):
        c.set(x, 0, RS0); c.set(x, 8, RS0)
    for y in range(1, 8):
        c.set(0, y, RS0); c.set(8, y, RS0)
    for y in range(9, 16):
        c.set(4, y, RS0); c.set(12, y, RS0)
    for x in range(16):
        if c.get(x, 1) != RS0:
            c.set(x, 1, lighten(c.get(x, 1), 0.12))
        if c.get(x, 9) != RS0:
            c.set(x, 9, lighten(c.get(x, 9), 0.12))


def rune_stone_side():
    c = Canvas(16, 16)
    rune_bricks(c, 71)
    # Placa escavada no centro com a runa Algiz brilhando
    for y in range(3, 13):
        for x in range(4, 12):
            c.set(x, y, RS1)
    c.outline_rect(3, 2, 12, 13, RS0)
    rune = ["#..#..#", ".#.#.#.", "..###..", "...#...", "...#...", "...#...", "..#.#..", ".#...#."]
    for ry, row in enumerate(rune):
        for rx, ch in enumerate(row):
            if ch == "#":
                c.set(4 + rx, 4 + ry, AR2)
                if c.get(5 + rx, 5 + ry) == RS1:
                    c.set(5 + rx, 5 + ry, darken(RS1, 0.35))
    c.set(7, 6, AR4); c.set(7, 7, AR3); c.set(7, 8, AR3)
    save_tex(c, "block/rune_stone_side")


def rune_stone_top():
    c = Canvas(16, 16)
    rune_bricks(c, 72)
    c.circle(8, 8, 5.2, RS0)
    c.circle(8, 8, 4.4, RS1)
    for a in range(0, 360, 45):
        x = 8 + math.cos(math.radians(a)) * 4.6
        y = 8 + math.sin(math.radians(a)) * 4.6
        c.set(int(x), int(y), AR1)
    c.circle(8, 8, 2.2, AR1)
    c.circle(8, 8, 1.2, AR3)
    save_tex(c, "block/rune_stone_top")


# ------------------------------------------------------------------------------------------------
# Circulos do chao (brancos com alpha: o renderer tinge com a cor do ritual)
# ------------------------------------------------------------------------------------------------
def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / (vx * vx + vy * vy)))
    dx, dy = px - (ax + vx * t), py - (ay + vy * t)
    return math.hypot(dx, dy)


def line_alpha(d, w):
    """Alpha de uma linha de meia-largura w com borda suave."""
    if d <= w:
        return 1.0
    if d <= w + 1.0:
        return 1.0 - (d - w)
    return 0.0


def render_field(size, fn):
    c = Canvas(size, size)
    ss = 3  # supersampling
    for y in range(size):
        for x in range(size):
            acc = 0.0
            for sy in range(ss):
                for sx in range(ss):
                    acc += fn(x + (sx + 0.5) / ss, y + (sy + 0.5) / ss)
            a = max(0.0, min(1.0, acc / (ss * ss)))
            if a > 0.003:
                c.set(x, y, (255, 255, 255, int(round(a * 255))))
    return c


def ascension_circle():
    size = 128
    cx = cy = size / 2
    star = [(cx + math.cos(math.radians(90 + i * 45)) * 44, cy + math.sin(math.radians(90 + i * 45)) * 44) for i in range(8)]
    star_lines = [(star[i], star[(i + 3) % 8]) for i in range(8)]
    square = [(cx + math.cos(math.radians(45 + i * 90)) * 22, cy + math.sin(math.radians(45 + i * 90)) * 22) for i in range(4)]
    square_lines = [(square[i], square[(i + 1) % 4]) for i in range(4)]

    def fn(x, y):
        r = math.hypot(x - cx, y - cy)
        ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
        a = 0.0
        a = max(a, line_alpha(abs(r - 61.5), 0.9))
        a = max(a, line_alpha(abs(r - 54.5), 0.6))
        a = max(a, 0.18 * line_alpha(abs(r - 58), 3.5))                 # brilho da faixa das runas
        # faixa de runas: tracos radiais e pequenos aneis alternados
        if 55.5 < r < 60.5:
            k = int(ang // 10)
            mid = k * 10 + 5
            if k % 2 == 0:
                da = abs(ang - mid) * math.pi / 180 * r
                a = max(a, line_alpha(da, 0.45))
                # "cruzeta" de runa
                if abs(r - 58) < 0.6:
                    a = max(a, line_alpha(abs(ang - mid) * math.pi / 180 * r, 1.6))
            else:
                mx = cx + math.cos(math.radians(mid)) * 58
                my = cy + math.sin(math.radians(mid)) * 58
                a = max(a, line_alpha(abs(math.hypot(x - mx, y - my) - 1.4), 0.45))
        a = max(a, line_alpha(abs(r - 44), 0.7))
        for (p, q) in star_lines:
            a = max(a, 0.9 * line_alpha(seg_dist(x, y, p[0], p[1], q[0], q[1]), 0.45))
        for (px, py) in star:
            a = max(a, line_alpha(abs(math.hypot(x - px, y - py) - 3.0), 0.5))
            if math.hypot(x - px, y - py) < 1.2:
                a = 1.0
        a = max(a, line_alpha(abs(r - 22), 0.6))
        for (p, q) in square_lines:
            a = max(a, 0.8 * line_alpha(seg_dist(x, y, p[0], p[1], q[0], q[1]), 0.4))
        if r > 63.5:
            a = 0.0
        return a

    save_tex(render_field(size, fn), "misc/ascension_circle")


def ascension_sigil():
    size = 64
    cx = cy = size / 2
    tri1 = [(cx + math.cos(math.radians(-90 + i * 120)) * 25, cy + math.sin(math.radians(-90 + i * 120)) * 25) for i in range(3)]
    tri2 = [(cx + math.cos(math.radians(90 + i * 120)) * 25, cy + math.sin(math.radians(90 + i * 120)) * 25) for i in range(3)]
    lines = [(tri1[i], tri1[(i + 1) % 3]) for i in range(3)] + [(tri2[i], tri2[(i + 1) % 3]) for i in range(3)]

    def fn(x, y):
        r = math.hypot(x - cx, y - cy)
        a = 0.0
        a = max(a, line_alpha(abs(r - 30), 0.7))
        a = max(a, line_alpha(abs(r - 27), 0.45))
        a = max(a, 0.2 * line_alpha(abs(r - 28.5), 3.0))
        for (p, q) in lines:
            a = max(a, line_alpha(seg_dist(x, y, p[0], p[1], q[0], q[1]), 0.5))
        a = max(a, line_alpha(abs(r - 9.5), 0.5))
        if r < 3.2:
            a = 1.0
        a = max(a, 0.35 * line_alpha(r, 6.0) * (1 - r / 9.0) if r < 9 else 0.0)
        if r > 31.5:
            a = 0.0
        return a

    save_tex(render_field(size, fn), "misc/ascension_sigil")


def ascension_dome():
    """Ladrilho 64x64 (repete em U e V): faixa de 4 runas originais, filetes e treliça fraca — branco com alfa,
    tingido pela cor do vertice (dourado na introducao, violeta na Tribulacao)."""
    size = 64
    rnd = random.Random(1010)
    # Cada runa: tracos entre pontos de uma grade 3x4 dentro de uma celula 12x22 (seed fixa = sempre as mesmas).
    grid = [(gx * 5.0, gy * 7.0) for gy in range(4) for gx in range(3)]
    runes = []
    for k in range(4):
        strokes = []
        spine = rnd.choice([0, 1, 2])
        strokes.append((grid[spine], grid[9 + spine]))           # haste vertical
        for _ in range(4):
            a = rnd.randrange(12)
            # tracos curtos (vizinhos na grade) parecem runas; longos demais parecem letras
            near = [b for b in range(12) if b != a and abs(b % 3 - a % 3) <= 1 and abs(b // 3 - a // 3) <= 1]
            strokes.append((grid[a], grid[rnd.choice(near)]))
        dots = [grid[rnd.randrange(12)]]
        runes.append((strokes, dots))

    def wrapd(a, b, n):
        d = abs(a - b) % n
        return min(d, n - d)

    def fn(x, y):
        a = 0.0
        # filetes horizontais (acima e abaixo da faixa) e brilho da faixa
        a = max(a, line_alpha(wrapd(y, 13.5, size), 0.55))
        a = max(a, line_alpha(wrapd(y, 50.5, size), 0.55))
        a = max(a, 0.6 * line_alpha(wrapd(y, 10.5, size), 0.3))
        a = max(a, 0.6 * line_alpha(wrapd(y, 53.5, size), 0.3))
        if 15 < y < 49:
            a = max(a, 0.10)
        # trelica losangular fraca fora da faixa
        if y < 12 or y > 52:
            dd = min(wrapd(x + y, 0, 16), wrapd(x - y, 0, 16))
            a = max(a, 0.35 * line_alpha(dd * 0.7071, 0.35))
        # runas
        for k, (strokes, dots) in enumerate(runes):
            ox, oy = k * 16 + 3.0, 21.0
            if not (ox - 2 <= x <= ox + 13 and oy - 2 <= y <= oy + 24):
                continue
            for (p, q) in strokes:
                a = max(a, line_alpha(seg_dist(x, y, ox + p[0], oy + p[1], ox + q[0], oy + q[1]), 0.6))
            for (dx, dy) in dots:
                a = max(a, line_alpha(abs(math.hypot(x - ox - dx, y - oy - dy) - 1.6), 0.45))
        # divisoria vertical entre runas (pontilhada)
        if 16 < y < 48 and int(y) % 4 < 2:
            a = max(a, 0.55 * line_alpha(wrapd(x, 0.5, 16), 0.3))
        return min(1.0, a)

    save_tex(render_field(size, fn), "misc/ascension_dome")


# ------------------------------------------------------------------------------------------------
# Sombra: textura (layout de skin 64x64) e mascara de brilho
# ------------------------------------------------------------------------------------------------
SKIN_BOXES = [  # (u, v, w, h, d) — camada base do layout de skin de jogador
    (0, 0, 8, 8, 8),      # cabeca
    (16, 16, 8, 12, 4),   # corpo
    (40, 16, 4, 12, 4),   # braco direito
    (32, 48, 4, 12, 4),   # braco esquerdo
    (0, 16, 4, 12, 4),    # perna direita
    (16, 48, 4, 12, 4),   # perna esquerda
]


def box_faces(u, v, w, h, d):
    """Retangulos (x0, y0, x1, y1) inclusivos das 6 faces de um cubo em box-UV."""
    return {
        "top": (u + d, v, u + d + w - 1, v + d - 1),
        "bottom": (u + d + w, v, u + d + 2 * w - 1, v + d - 1),
        "right": (u, v + d, u + d - 1, v + d + h - 1),
        "front": (u + d, v + d, u + d + w - 1, v + d + h - 1),
        "left": (u + d + w, v + d, u + 2 * d + w - 1, v + d + h - 1),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1),
    }


def shadow_texture():
    c = Canvas(64, 64)
    base = hex_rgba("140D1E")
    rnd = random.Random(81)
    for (u, v, w, h, d) in SKIN_BOXES:
        for name, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    # mais claro em cima, escurecendo para baixo (silhueta "esfumaçada")
                    t = (y - y0) / max(1, (y1 - y0))
                    col = mix(hex_rgba("2A1D3C"), base, t)
                    f = (rnd.random() - 0.5) * 0.12
                    col = lighten(col, f) if f > 0 else darken(col, -f)
                    c.set(x, y, col)
    # rosto: olhos e uma boca sombria
    for (x, y) in ((9, 12), (10, 12), (13, 12), (14, 12)):
        c.set(x, y, AR3)
    c.set(10, 12, AR4); c.set(13, 12, AR4)
    for x in range(10, 14):
        c.set(x, 14, hex_rgba("07040B"))
    # rachaduras arcanas no corpo e nos bracos
    for (x, y) in ((22, 21), (23, 22), (23, 23), (22, 24), (24, 25), (25, 26), (21, 27), (20, 28)):
        c.set(x, y, AR1)
    for (x, y) in ((45, 22), (45, 23), (46, 24), (46, 25), (37, 54), (37, 55), (38, 56)):
        c.set(x, y, AR1)
    # capuz de fumaca na camada externa (chapeu): bordas de fumaca nas laterais da cabeca
    hat = box_faces(32, 0, 8, 8, 8)
    for name in ("right", "left", "back", "top"):
        x0, y0, x1, y1 = hat[name]
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if rnd.random() < (0.55 if name == "top" else 0.25):
                    c.set(x, y, (20, 12, 30, 170))
    save_tex(c, "entity/ascension_shadow")


def shadow_glow():
    c = Canvas(64, 64)
    white = (255, 255, 255, 255)
    soft = (200, 200, 200, 255)
    for (x, y) in ((9, 12), (10, 12), (13, 12), (14, 12)):
        c.set(x, y, white)
    for (x, y) in ((9, 13), (14, 13)):
        c.set(x, y, (120, 120, 120, 255))
    for (x, y) in ((22, 21), (23, 22), (23, 23), (22, 24), (24, 25), (25, 26), (21, 27), (20, 28)):
        c.set(x, y, soft)
    for (x, y) in ((45, 22), (45, 23), (46, 24), (46, 25), (37, 54), (37, 55), (38, 56)):
        c.set(x, y, soft)
    # coração arcano no peito
    for (x, y) in ((19, 23), (20, 23), (19, 24), (20, 24)):
        c.set(x, y, white)
    save_tex(c, "entity/ascension_shadow_glow")


# ------------------------------------------------------------------------------------------------
# Modelos de bloco / item / blockstates
# ------------------------------------------------------------------------------------------------
def faces(tex_side, tex_up=None, tex_down=None):
    f = {}
    for d in ("north", "south", "east", "west"):
        f[d] = {"texture": tex_side}
    if tex_up:
        f["up"] = {"texture": tex_up}
    if tex_down:
        f["down"] = {"texture": tex_down}
    return f


def block_models():
    write_json(os.path.join(A, "models/block/ascension_core.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/ascension_core_side",
            "side": f"{NS}:block/ascension_core_side",
            "top": f"{NS}:block/ascension_core_top",
            "bottom": f"{NS}:block/ascension_core_bottom",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                **faces("#side", "#bottom"), "down": {"texture": "#bottom", "cullface": "down"}}},
            {"from": [2, 4, 2], "to": [14, 10, 14], "faces": faces("#side")},
            {"from": [1, 10, 1], "to": [15, 13, 15], "faces": faces("#side", "#top", "#bottom")},
        ],
    })
    write_json(os.path.join(A, "models/block/ascension_pedestal.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/ascension_pedestal_side",
            "side": f"{NS}:block/ascension_pedestal_side",
            "top": f"{NS}:block/ascension_pedestal_top",
            "bottom": f"{NS}:block/ascension_core_bottom",
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 2, 15], "faces": {
                **faces("#side", "#top"), "down": {"texture": "#bottom", "cullface": "down"}}},
            {"from": [4, 2, 4], "to": [12, 10, 12], "faces": faces("#side")},
            {"from": [2, 10, 2], "to": [14, 12, 14], "faces": faces("#side", "#top", "#bottom")},
        ],
    })
    write_json(os.path.join(A, "models/block/mana_crystal_block.json"), {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"{NS}:block/mana_crystal_block"},
    })
    write_json(os.path.join(A, "models/block/rune_stone.json"), {
        "parent": "minecraft:block/cube_column",
        "textures": {"end": f"{NS}:block/rune_stone_top", "side": f"{NS}:block/rune_stone_side"},
    })
    for bid in ("ascension_core", "ascension_pedestal", "mana_crystal_block", "rune_stone"):
        write_json(os.path.join(A, f"blockstates/{bid}.json"), {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
        write_json(os.path.join(A, f"models/item/{bid}.json"), {"parent": f"{NS}:block/{bid}"})
    write_json(os.path.join(A, "models/item/ascension_shadow_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# ------------------------------------------------------------------------------------------------
# GeckoLib: modelo (largo e slim) e animacoes
# ------------------------------------------------------------------------------------------------
def cube(origin, size, uv, inflate=None):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    return c


def shadow_geo(slim):
    aw = 3 if slim else 4
    ay = 11.5 if slim else 12
    apy = 21.5 if slim else 22
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 24, 0], "cubes": [
            cube([-4, 12, -2], [8, 12, 4], [16, 16]),
            cube([-4, 12, -2], [8, 12, 4], [16, 32], 0.25)]},
        {"name": "head", "parent": "body", "pivot": [0, 24, 0], "cubes": [
            cube([-4, 24, -4], [8, 8, 8], [0, 0]),
            cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]},
        {"name": "right_arm", "parent": "body", "pivot": [-5, apy, 0], "cubes": [
            cube([-4 - aw, ay, -2], [aw, 12, 4], [40, 16]),
            cube([-4 - aw, ay, -2], [aw, 12, 4], [40, 32], 0.25)]},
        {"name": "left_arm", "parent": "body", "pivot": [5, apy, 0], "cubes": [
            cube([4, ay, -2], [aw, 12, 4], [32, 48]),
            cube([4, ay, -2], [aw, 12, 4], [48, 48], 0.25)]},
        {"name": "right_leg", "parent": "root", "pivot": [-1.9, 12, 0], "cubes": [
            cube([-3.9, 0, -2], [4, 12, 4], [0, 16]),
            cube([-3.9, 0, -2], [4, 12, 4], [0, 32], 0.25)]},
        {"name": "left_leg", "parent": "root", "pivot": [1.9, 12, 0], "cubes": [
            cube([-0.1, 0, -2], [4, 12, 4], [16, 48]),
            cube([-0.1, 0, -2], [4, 12, 4], [0, 48], 0.25)]},
    ]
    ident = "geometry.ascension_shadow_slim" if slim else "geometry.ascension_shadow"
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": ident,
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": bones,
        }],
    }


def kf(*pairs):
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t != 0 else "0.0": v for t, v in pairs}


def shadow_animations():
    anims = {
        "animation.ascension_shadow.idle": {
            "loop": True, "animation_length": 3.0,
            "bones": {
                "body": {"rotation": kf((0, [0, 0, 0]), (1.5, [2, 0, 0]), (3.0, [0, 0, 0]))},
                "head": {"rotation": kf((0, [0, 0, 0]), (1.5, [-4, 0, 0]), (3.0, [0, 0, 0]))},
                "right_arm": {"rotation": kf((0, [0, 0, 5]), (1.5, [-5, 0, 9]), (3.0, [0, 0, 5]))},
                "left_arm": {"rotation": kf((0, [0, 0, -5]), (1.5, [-5, 0, -9]), (3.0, [0, 0, -5]))},
                "root": {"position": kf((0, [0, 0, 0]), (1.5, [0, 0.4, 0]), (3.0, [0, 0, 0]))},
            },
        },
        "animation.ascension_shadow.walk": {
            "loop": True, "animation_length": 1.0,
            "bones": {
                "right_leg": {"rotation": kf((0, [32, 0, 0]), (0.5, [-32, 0, 0]), (1.0, [32, 0, 0]))},
                "left_leg": {"rotation": kf((0, [-32, 0, 0]), (0.5, [32, 0, 0]), (1.0, [-32, 0, 0]))},
                "right_arm": {"rotation": kf((0, [-28, 0, 4]), (0.5, [28, 0, 4]), (1.0, [-28, 0, 4]))},
                "left_arm": {"rotation": kf((0, [28, 0, -4]), (0.5, [-28, 0, -4]), (1.0, [28, 0, -4]))},
                "body": {"rotation": kf((0, [4, 3, 0]), (0.5, [4, -3, 0]), (1.0, [4, 3, 0]))},
            },
        },
        "animation.ascension_shadow.channel": {
            "loop": True, "animation_length": 1.0,
            "bones": {
                "right_arm": {"rotation": kf((0, [-95, 12, 0]), (0.5, [-105, 18, 0]), (1.0, [-95, 12, 0]))},
                "left_arm": {"rotation": kf((0, [-95, -12, 0]), (0.5, [-105, -18, 0]), (1.0, [-95, -12, 0]))},
                "body": {"rotation": kf((0, [-6, 0, 0]), (0.5, [-8, 0, 0]), (1.0, [-6, 0, 0]))},
                "head": {"rotation": kf((0, [6, 0, 0]), (0.5, [8, 0, 0]), (1.0, [6, 0, 0]))},
            },
        },
        "animation.ascension_shadow.release": {
            "loop": False, "animation_length": 0.5,
            "bones": {
                "right_arm": {"rotation": kf((0, [-150, 10, 0]), (0.15, [-80, 0, 0]), (0.5, [0, 0, 5]))},
                "left_arm": {"rotation": kf((0, [-150, -10, 0]), (0.15, [-80, 0, 0]), (0.5, [0, 0, -5]))},
                "body": {"rotation": kf((0, [-10, 0, 0]), (0.15, [12, 0, 0]), (0.5, [0, 0, 0]))},
            },
        },
        "animation.ascension_shadow.attack": {
            "loop": False, "animation_length": 0.5,
            "bones": {
                "right_arm": {"rotation": kf((0, [-130, 0, 10]), (0.2, [20, -10, -10]), (0.5, [0, 0, 5]))},
                "body": {"rotation": kf((0, [0, 20, 0]), (0.2, [6, -25, 0]), (0.5, [0, 0, 0]))},
            },
        },
        "animation.ascension_shadow.emerge": {
            "loop": False, "animation_length": 1.5,
            "bones": {
                "root": {"position": kf((0, [0, -30, 0]), (1.2, [0, 2, 0]), (1.5, [0, 0, 0]))},
                "right_arm": {"rotation": kf((0, [-170, 0, 20]), (1.2, [-170, 0, 20]), (1.5, [0, 0, 5]))},
                "left_arm": {"rotation": kf((0, [-170, 0, -20]), (1.2, [-170, 0, -20]), (1.5, [0, 0, -5]))},
                "head": {"rotation": kf((0, [30, 0, 0]), (1.0, [30, 0, 0]), (1.5, [0, 0, 0]))},
            },
        },
    }
    return {"format_version": "1.8.0", "animations": anims}


def geckolib_files():
    write_json(os.path.join(A, "geo/entity/ascension_shadow.geo.json"), shadow_geo(False))
    write_json(os.path.join(A, "geo/entity/ascension_shadow_slim.geo.json"), shadow_geo(True))
    write_json(os.path.join(A, "animations/entity/ascension_shadow.animation.json"), shadow_animations())


# ------------------------------------------------------------------------------------------------
# Dados: loot, receitas, tributos, tags
# ------------------------------------------------------------------------------------------------
def loot_tables():
    for bid in ("ascension_core", "ascension_pedestal", "mana_crystal_block", "rune_stone"):
        write_json(os.path.join(D, f"loot_table/blocks/{bid}.json"), {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/{bid}",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    def counted(item, lo, hi):
        return {"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}

    write_json(os.path.join(D, "loot_table/entities/ascension_shadow.json"), {
        "type": "minecraft:entity",
        "random_sequence": f"{NS}:entities/ascension_shadow",
        "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [counted(f"{NS}:essence_shadow", 1, 2)],
             "conditions": [{"condition": "minecraft:killed_by_player"}]},
            {"rolls": 1, "bonus_rolls": 0, "entries": [counted(f"{NS}:essence_arcane", 1, 3)]},
        ],
    })


def shaped(name, pattern, key, result, count=1, category="misc"):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


def shapeless(name, ingredients, result, count=1, category="misc"):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [{"item": i} for i in ingredients],
        "result": {"id": result, "count": count},
    })


def recipes():
    m = f"{NS}:mana_crystal"
    ea = f"{NS}:essence_arcane"
    shapeless("ascension_mana_crystal", ["minecraft:amethyst_shard", "minecraft:lapis_lazuli", "minecraft:redstone"], m)
    shapeless("ascension_essence_arcane", [m, "minecraft:glowstone_dust", "minecraft:amethyst_shard"], ea)
    shapeless("ascension_essence_fire", [ea, "minecraft:blaze_powder"], f"{NS}:essence_fire")
    shapeless("ascension_essence_water", [ea, "minecraft:prismarine_shard"], f"{NS}:essence_water")
    shapeless("ascension_essence_earth", [ea, "minecraft:mossy_cobblestone"], f"{NS}:essence_earth")
    shapeless("ascension_essence_wind", [ea, "minecraft:feather", "minecraft:phantom_membrane"], f"{NS}:essence_wind")
    shapeless("ascension_essence_light", [ea, "minecraft:glowstone"], f"{NS}:essence_light")
    shapeless("ascension_essence_shadow", [ea, "minecraft:ink_sac", "minecraft:obsidian"], f"{NS}:essence_shadow")

    # v0.9: tudo com pedra de tijolo (sem minerar deepslate) e rendendo mais. O 9x9 inteiro sai por ~8 cristais.
    shaped("ascension_core", [" E ", "MAM", "SSS"], {
        "E": ea, "M": m, "A": "minecraft:amethyst_shard", "S": "minecraft:stone_bricks"},
        f"{NS}:ascension_core", category="building")
    shaped("ascension_pedestal", [" M ", " S ", "SSS"], {"M": m, "S": "minecraft:stone_bricks"},
           f"{NS}:ascension_pedestal", count=2, category="building")
    shaped("mana_crystal_block", ["MMM", "MMM", "MMM"], {"M": m}, f"{NS}:mana_crystal_block", category="building")
    shapeless("ascension_mana_crystal_from_block", [f"{NS}:mana_crystal_block"], m, count=9)
    shaped("rune_stone", ["BAB", "BLB", "BBB"], {
        "B": "minecraft:stone_bricks", "A": "minecraft:amethyst_shard", "L": "minecraft:lapis_lazuli"},
        f"{NS}:rune_stone", count=8, category="building")
    shaped("rune_stone_from_crystal", ["BBB", "BMB", "BBB"], {"B": "minecraft:stone_bricks", "M": m},
           f"{NS}:rune_stone", count=8, category="building")


def tributes():
    """v0.9: ~1/4 das Essencias Arcanas da v0.8 (152 -> 38 no total) e nada de diamante/estrela do Nether: no lugar,
    trofeus dos chefes da rota (por id, com reserva caso o modulo do chefe nao exista). O selo "Tributo do Circulo N"
    (missoes "A Ascensao" do diario) dispensa tudo isto."""
    def item(i, n):
        return {"type": "item", "item": i, "count": n}

    def elem(n, lo, hi):
        return {"type": "elemental_essences", "count": n, "min_types": lo, "max_types": hi, "arcane_substitute": True}

    def anycore(n):
        return {"type": "any_guardian_core", "count": n}

    def trophy(n, items, bosses, fallback):
        return {"type": "boss_trophy", "count": n, "items": items, "bosses": bosses, "fallback": fallback}

    ea, mc = f"{NS}:essence_arcane", f"{NS}:mana_crystal"
    # Chefes do comeco da rota (Rei Slime, Rei Goblin, Lorde Orc, Minotauro...) e os do Overworld da v0.9.
    # Ordem = ordem de consumo: material puro (chifre) antes das coroas (que sao equipamento).
    early = trophy(1, [f"{NS}:minotaur_horn", f"{NS}:king_slime_crown", f"{NS}:goblin_king_crown"],
                   ["king_slime", "goblin_king", "orc_lord", "minotaur", "corrupted_ent", "corrupted_witch",
                    "renegade_archmage", "lich", "imprisoned_patron"], [item(mc, 12)])
    # Chefes do Overworld (bosses_f/bosses_g): SO o trofeu de cada um (1 por abate). Lista explicita: o prefixo
    # "<chefe>_*" pegaria tambem os materiais (escamas/presas, varios por abate) e o item visual da bola de veneno.
    overworld = [f"{NS}:swamp_hydra_heart", f"{NS}:wyvern_king_horn", f"{NS}:desert_basilisk_eye",
                 f"{NS}:ruin_colossus_core", f"{NS}:false_hydra_heart"]
    table = {
        2: [item(ea, 1), item(mc, 4)],
        3: [elem(1, 2, 2), item(ea, 2)],
        4: [elem(1, 2, 3), item(ea, 3), item(mc, 8)],
        5: [elem(2, 3, 3), item(ea, 4), early],
        6: [anycore(1), elem(2, 3, 3), item(ea, 4)],
        7: [anycore(1), elem(2, 4, 4), item(ea, 5), trophy(1, overworld, [], [anycore(1)])],
        8: [anycore(2), item(ea, 6), item(mc, 16)],
        9: [anycore(2), item(ea, 6), trophy(2, overworld, [], [anycore(2)])],
        10: [anycore(4), item(ea, 7),
             # O trofeu do Arauto (bloco decorativo) e gasto antes dos coracoes (que dao bonus permanente ao absorver).
             trophy(1, [f"{NS}:twilight_herald_trophy", f"{NS}:twilight_heart", f"{NS}:primordial_dragon_heart"],
                    ["primordial_dragon"], [anycore(2)])],
    }
    for n, reqs in table.items():
        write_json(os.path.join(D, f"ascension/circle_{n}.json"), {"circle": n, "tribute": reqs})


# ------------------------------------------------------------------------------------------------
# Selos "Tributo do Circulo N" (itens): medalhao dourado com gema da cor da faixa e o numero do Circulo
# ------------------------------------------------------------------------------------------------
SEAL_COLORS = {2: "7FE3FF", 3: "7FE3FF", 4: "7FE3FF", 5: "B98CFF", 6: "B98CFF", 7: "B98CFF",
               8: "FFD86B", 9: "FFD86B", 10: "FF7AC8"}
DIGITS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
}


def tribute_seal(n):
    c = Canvas(16, 16)
    gem = hex_rgba(SEAL_COLORS[n])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.0:
                # Aro dourado com luz no alto-esquerda; 8 "dentes" do selo
                ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                light = 0.5 - 0.5 * math.cos(ang + math.pi * 0.75)
                col = mix(GD1, GD3, light)
                if d > 6.3 and int((ang + math.pi) / (math.pi / 4)) % 2 == 0:
                    col = darken(col, 0.25)
                c.set(x, y, col)
            if d <= 5.2:
                c.set(x, y, mix(darken(gem, 0.45), gem, max(0.0, 1 - d / 5.2)))
    c.shade_sphere(8, 8, 5.2, light=0.35, dark=0.35)
    # Runa: estrela de 4 pontas no centro da gema
    rune = lighten(gem, 0.7)
    for i in range(-3, 4):
        c.set(7 + i, 7, rune)
        c.set(7, 7 + i, rune)
    for i in (-1, 1):
        c.set(7 + i, 7 + i, rune); c.set(7 + i, 7 - i, rune)
    c.set(7, 7, AR4)
    c.set(5, 4, (255, 255, 255, 255)); c.set(4, 5, lighten(gem, 0.85))
    # Numero do Circulo no canto de baixo-direita (com contorno escuro)
    text = str(n)
    w = len(text) * 4 - 1
    ox, oy = 16 - w - (0 if n >= 10 else 1), 10
    mask = set()
    for k, ch in enumerate(text):
        for ry, row in enumerate(DIGITS[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    mask.add((ox + k * 4 + rx, oy + ry))
    for (x, y) in list(mask):
        for ox2, oy2 in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)):
            q = (x + ox2, y + oy2)
            if q not in mask:
                c.set(q[0], q[1], ST0)
    for (x, y) in mask:
        c.set(x, y, AR4 if n < 10 else GD3)
    c.outline(darken(GD0, 0.3))
    return c


def tribute_items():
    for n in range(2, 11):
        save_tex(tribute_seal(n), f"item/ascension_tribute_{n}")
        write_json(os.path.join(A, f"models/item/ascension_tribute_{n}.json"), {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/ascension_tribute_{n}"}})


def merge_tags():
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [
        f"{NS}:ascension_core", f"{NS}:ascension_pedestal", f"{NS}:mana_crystal_block", f"{NS}:rune_stone"]})


def main():
    core_side(); core_top(); core_bottom()
    pedestal_side(); pedestal_top()
    mana_crystal_block()
    rune_stone_side(); rune_stone_top()
    ascension_circle(); ascension_sigil(); ascension_dome()
    shadow_texture(); shadow_glow()
    block_models()
    geckolib_files()
    loot_tables()
    recipes()
    tributes()
    tribute_items()
    merge_tags()
    print("ascension: assets gerados")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "dome":
        ascension_dome()
        print("ascension: domo gerado")
    else:
        main()
