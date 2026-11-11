"""Gera os assets/dados do modulo "automation" (Pedestal Runico, Bateria/Condensador de Mana, Coletor Arcano).

Uso (na raiz do projeto):  py -I tools/gen/automation.py

Gera: texturas dos 4 blocos (estados da bateria 0-4, condensador aceso/apagado, pedestal ativo), o circulo de
runas e o halo desenhados pelo renderer do pedestal, as telas (pedestal e condensador), modelos de bloco/item,
blockstates, loot tables (a bateria guarda a carga), receitas e a tag de picareta (merge).
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
MERGE = os.path.join(ROOT, "src/main/merge/automation")
NS = "isekai_mahou"

# ------------------------------------------------------------------------------------------------
# Paleta
# ------------------------------------------------------------------------------------------------
CLEAR = (0, 0, 0, 0)
ST0 = hex_rgba("1A1524")   # pedra runica: rejunte
ST1 = hex_rgba("292236")
ST2 = hex_rgba("383049")
ST3 = hex_rgba("483F5E")
ST4 = hex_rgba("5D5378")
GD0 = hex_rgba("5A400E")
GD1 = hex_rgba("8A6A1E")
GD2 = hex_rgba("C9A23A")
GD3 = hex_rgba("F2D06B")
AR0 = hex_rgba("2E1765")
AR1 = hex_rgba("5B33C0")
AR2 = hex_rgba("8E5CF5")
AR3 = hex_rgba("C2A4FF")
AR4 = hex_rgba("F0E6FF")
MT0 = hex_rgba("1E1F29")   # metal escuro
MT1 = hex_rgba("2F3140")
MT2 = hex_rgba("444759")
MT3 = hex_rgba("5D6178")
MT4 = hex_rgba("8187A3")
GL0 = hex_rgba("141020")   # vidro escuro
GL1 = hex_rgba("221A33")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save_tex(c, rel):
    c.save(os.path.join(A, "textures", rel + ".png"))


def jitter(c, x0, y0, x1, y1, base, seed, amount=0.07):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            f = (rnd.random() - 0.5) * 2 * amount
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))


def bevel(c, x0, y0, x1, y1, light, dark):
    for x in range(x0, x1 + 1):
        c.set(x, y0, light)
        c.set(x, y1, dark)
    for y in range(y0, y1 + 1):
        c.set(x0, y, light)
        c.set(x1, y, dark)


# ------------------------------------------------------------------------------------------------
# Pedestal Runico
# ------------------------------------------------------------------------------------------------
def brick_rows(c, seed, rows=((0, 3), (4, 7), (8, 11), (12, 15))):
    """Tijolos de pedra runica com rejunte, luz em cima e sombra embaixo."""
    rnd = random.Random(seed)
    for i, (y0, y1) in enumerate(rows):
        off = 0 if i % 2 == 0 else 4
        for bx in range(-8, 16, 8):
            x0, x1 = bx + off, bx + off + 7
            jitter(c, max(0, x0), y0, min(15, x1), y1, ST2, rnd.randint(0, 9999))
            for x in range(max(0, x0), min(15, x1) + 1):
                c.set(x, y0, ST3)
                c.set(x, y1, ST1)
            if 0 <= x1 <= 15:
                for y in range(y0, y1 + 1):
                    c.set(x1, y, ST0)
        for x in range(16):
            c.set(x, y1, ST0 if y1 % 4 == 3 else c.get(x, y1))


def pedestal_base_side():
    c = Canvas(16, 16)
    brick_rows(c, 11)
    # Faixa visivel (linhas 13-15): friso dourado em cima, pedra e sombra
    for x in range(16):
        c.set(x, 13, GD2 if x % 4 else GD3)
        c.set(x, 14, ST2 if x % 5 else ST3)
        c.set(x, 15, ST1)
    save_tex(c, "block/runic_pedestal_base_side")


def pedestal_base_top():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, ST2, 21, 0.06)
    # Lajotas em xadrez sutil
    for y in range(16):
        for x in range(16):
            if (x // 4 + y // 4) % 2 == 0:
                c.set(x, y, lighten(c.get(x, y), 0.05))
    bevel(c, 1, 1, 14, 14, GD2, GD0)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, GD3)
    save_tex(c, "block/runic_pedestal_base_top")


def pedestal_bottom():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, ST1, 31, 0.08)
    save_tex(c, "block/runic_pedestal_bottom")


def pedestal_column(active):
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, ST2, 41, 0.07)
    for y in range(16):
        c.set(4, y, ST3)
        c.set(11, y, ST1)
    # Canal de runas no meio da coluna (u 4..12): glifos verticais
    glow = AR3 if active else AR1
    core = AR4 if active else AR2
    rune_rows = [
        "..#..",
        ".###.",
        "..#..",
        ".....",
        ".#.#.",
        "..#..",
        ".#.#.",
        ".....",
        "#...#",
        ".###.",
        "..#..",
        ".....",
    ]
    for i, row in enumerate(rune_rows):
        for j, ch in enumerate(row):
            if ch == "#":
                c.set(6 + j, 2 + i, core if (i + j) % 3 == 0 else glow)
    # Faixas douradas em cima e embaixo
    for x in range(4, 12):
        c.set(x, 4, GD1)
        c.set(x, 12, GD1)
    save_tex(c, "block/runic_pedestal_column" + ("_active" if active else ""))


def pedestal_capital_side():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, ST2, 51, 0.06)
    # Linhas 2-4 (as visiveis): friso dourado + pontos de runa
    for x in range(16):
        c.set(x, 2, GD3 if x % 3 == 0 else GD2)
        c.set(x, 3, AR2 if x % 4 == 2 else ST3)
        c.set(x, 4, GD1)
    save_tex(c, "block/runic_pedestal_capital")


def circle_pixels(cx, cy, r0, r1):
    out = []
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                out.append((x, y))
    return out


def pedestal_top(active):
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, ST1 if not active else ST2, 61, 0.05)
    ring = AR2 if not active else AR3
    ring_hi = AR3 if not active else AR4
    gold = GD2 if not active else GD3
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 5.0 <= d <= 5.9:
                c.set(x, y, gold)
            elif 3.4 <= d <= 4.1:
                c.set(x, y, ring)
            elif d <= 1.3:
                c.set(x, y, ring_hi)
    # Quatro runas nos eixos e linhas do hexagrama
    for (x, y) in ((8, 3), (7, 12), (3, 7), (12, 8)):
        c.set(x, y, ring_hi)
    for a in range(6):
        ang = a * math.pi / 3
        x = int(round(cx - 0.5 + math.cos(ang) * 2.4))
        y = int(round(cy - 0.5 + math.sin(ang) * 2.4))
        c.set(x, y, ring)
    bevel(c, 2, 2, 13, 13, GD1, GD0)
    if active:
        # brilho extra no centro
        for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
            c.set(x, y, AR4)
    save_tex(c, "block/runic_pedestal_top" + ("_active" if active else ""))


def rune_ring_texture():
    """Circulo de runas 64x64 (branco: o renderer tinge com a cor do elemento)."""
    c = Canvas(64, 64)
    W = (255, 255, 255, 255)
    W2 = (255, 255, 255, 200)
    W3 = (255, 255, 255, 140)
    for (x, y) in circle_pixels(32, 32, 29.0, 31.0):
        c.set(x, y, W)
    for (x, y) in circle_pixels(32, 32, 20.0, 21.2):
        c.set(x, y, W2)
    # 12 runas entre os aneis
    glyphs = [
        ["#.#", ".#.", "#.#"], ["###", "#..", "###"], [".#.", "###", ".#."], ["#..", "##.", "###"],
        ["###", ".#.", ".#."], ["#.#", "###", "#.#"], ["##.", "#.#", "##."], [".##", "#..", ".##"],
        ["#.#", "#.#", ".#."], ["###", "#.#", "###"], ["..#", ".#.", "#.."], ["#..", ".#.", "..#"],
    ]
    for i, g in enumerate(glyphs):
        ang = i * math.pi * 2 / 12
        gx = 32 + math.cos(ang) * 25.2 - 1.5
        gy = 32 + math.sin(ang) * 25.2 - 1.5
        for yy, row in enumerate(g):
            for xx, ch in enumerate(row):
                if ch == "#":
                    c.set(int(round(gx)) + xx, int(round(gy)) + yy, W)
    # Hexagrama interno
    pts = [(32 + math.cos(a * math.pi / 3 - math.pi / 2) * 19, 32 + math.sin(a * math.pi / 3 - math.pi / 2) * 19) for a in range(6)]
    for tri in ((0, 2, 4), (1, 3, 5)):
        for k in range(3):
            x0, y0 = pts[tri[k]]
            x1, y1 = pts[tri[(k + 1) % 3]]
            steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2)
            for s in range(steps + 1):
                t = s / steps
                c.set(int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t), W3)
    for (x, y) in circle_pixels(32, 32, 0, 3.2):
        c.set(x, y, W2)
    save_tex(c, "block/runic_pedestal_ring")


def halo_texture():
    c = Canvas(32, 32)
    W = (255, 255, 255, 235)
    W2 = (255, 255, 255, 150)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 13.6 <= d <= 15.2:
                c.set(x, y, W)
            elif 11.0 <= d <= 11.8 and (int(math.degrees(math.atan2(y - 16, x - 16))) // 15) % 2 == 0:
                c.set(x, y, W2)
    for i in range(8):
        ang = i * math.pi / 4
        x = int(round(16 + math.cos(ang) * 14.4 - 0.5))
        y = int(round(16 + math.sin(ang) * 14.4 - 0.5))
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            c.set(x + dx, y + dy, (255, 255, 255, 255))
    save_tex(c, "block/runic_pedestal_halo")


# ------------------------------------------------------------------------------------------------
# Bateria de Mana
# ------------------------------------------------------------------------------------------------
def metal_plate(c, seed):
    jitter(c, 0, 0, 15, 15, MT2, seed, 0.05)
    bevel(c, 0, 0, 15, 15, MT3, MT0)
    bevel(c, 1, 1, 14, 14, MT3, MT1)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, GD3)
        c.set(x + (1 if x < 8 else -1), y, GD1)


def battery_side(level):
    c = Canvas(16, 16)
    metal_plate(c, 71)
    # Cantoneiras douradas
    for i in range(3):
        c.set(i, 0, GD2); c.set(0, i, GD2)
        c.set(15 - i, 0, GD2); c.set(15, i, GD2)
        c.set(i, 15, GD1); c.set(0, 15 - i, GD1)
        c.set(15 - i, 15, GD1); c.set(15, 15 - i, GD1)
    # Janela de vidro (x 4..11, y 2..13)
    x0, x1, y0, y1 = 4, 11, 2, 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, GL0 if (x + y) % 5 else GL1)
    bevel(c, x0 - 1, y0 - 1, x1 + 1, y1 + 1, MT0, MT4)
    h = y1 - y0 + 1
    fill = [0, 3, 6, 9, h][level]
    rnd = random.Random(80 + level)
    for y in range(y1 - fill + 1, y1 + 1):
        for x in range(x0, x1 + 1):
            depth = (y1 - y) / max(1, fill)
            base = mix(AR1, AR0, depth * 0.6) if (x - x0) > 1 else AR2
            c.set(x, y, base)
    if fill > 0:
        top = y1 - fill + 1
        for x in range(x0, x1 + 1):
            c.set(x, top, AR3 if level < 4 else AR4)
        for _ in range(2 + level):
            bx = rnd.randint(x0 + 1, x1 - 1)
            by = rnd.randint(top + 1, y1) if top + 1 <= y1 else top
            c.set(bx, by, AR4)
    # Reflexo no vidro
    for y in range(y0, y1 + 1, 1):
        if (y - y0) % 6 < 3:
            c.set(x0, y, lighten(c.get(x0, y), 0.35))
    # Marcas de nivel na lateral
    for k in range(1, 4):
        yy = y1 - k * 3
        c.set(x1 + 2, yy, GD2)
    save_tex(c, f"block/mana_battery_side_{level}")


def battery_top():
    c = Canvas(16, 16)
    metal_plate(c, 91)
    # Cristal no centro (losango)
    rows = [
        "...#...",
        "..###..",
        ".##o##.",
        "##ooo##",
        ".##o##.",
        "..###..",
        "...#...",
    ]
    pal = {"#": AR2, "o": AR4}
    c.from_rows(rows, pal, 4, 4)
    c.set(6, 6, AR3)
    for (x, y) in ((4, 7), (10, 7), (7, 4), (7, 10)):
        c.set(x, y, AR1)
    save_tex(c, "block/mana_battery_top")


def battery_bottom():
    c = Canvas(16, 16)
    metal_plate(c, 93)
    save_tex(c, "block/mana_battery_bottom")


# ------------------------------------------------------------------------------------------------
# Condensador de Mana
# ------------------------------------------------------------------------------------------------
def condenser_frame(c, seed):
    jitter(c, 0, 0, 15, 15, ST1, seed, 0.07)
    # Ladrilhos de ardosia
    for y in (0, 5, 10, 15):
        for x in range(16):
            c.set(x, y, ST0)
    for y in range(16):
        off = 0 if (y // 5) % 2 == 0 else 4
        c.set((off + 7) % 16, y, ST0)
        c.set((off + 15) % 16, y, ST0)
    for x in range(16):
        c.set(x, 1, ST3 if x % 2 else ST2)
    bevel(c, 0, 0, 15, 15, GD1, GD0)


def condenser_side():
    c = Canvas(16, 16)
    condenser_frame(c, 101)
    for x in range(2, 14):
        c.set(x, 7, GD2)
        c.set(x, 8, GD0)
    for x in (3, 7, 11):
        c.set(x, 7, AR2)
    save_tex(c, "block/mana_condenser_side")


def condenser_front(on):
    c = Canvas(16, 16)
    condenser_frame(c, 103)
    # Janela redonda com o cristal
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 9)
            if d <= 4.6:
                if on:
                    t = d / 4.6
                    c.set(x, y, mix(AR4, AR1, t))
                else:
                    c.set(x, y, GL0 if d > 1.6 else AR0)
            elif d <= 5.6:
                c.set(x, y, GD2 if y < 9 else GD1)
    if on:
        for (x, y) in ((6, 7), (9, 10), (7, 11), (10, 7)):
            c.set(x, y, AR3)
    else:
        for (x, y) in ((6, 7), (7, 7)):
            c.set(x, y, GL1)
    # Grade de saida em cima
    for x in range(4, 12, 2):
        c.set(x, 2, ST0)
        c.set(x, 3, ST0)
    save_tex(c, "block/mana_condenser_front" + ("_on" if on else ""))


def condenser_top():
    c = Canvas(16, 16)
    condenser_frame(c, 105)
    # Funil de entrada (quadrado afundado)
    for y in range(4, 12):
        for x in range(4, 12):
            c.set(x, y, ST0 if (x + y) % 2 else ST1)
    bevel(c, 3, 3, 12, 12, GD1, GD2)
    for (x, y) in ((7, 7), (8, 8)):
        c.set(x, y, AR1)
    save_tex(c, "block/mana_condenser_top")


def condenser_bottom():
    c = Canvas(16, 16)
    condenser_frame(c, 107)
    save_tex(c, "block/mana_condenser_bottom")


# ------------------------------------------------------------------------------------------------
# Coletor Arcano
# ------------------------------------------------------------------------------------------------
def collector_base():
    c = Canvas(16, 16)
    metal_plate(c, 111)
    # Linhas 0-3 (borda de cima) e 12-15 (base): friso dourado com runas
    for x in range(16):
        c.set(x, 0, GD3 if x % 4 == 0 else GD2)
        c.set(x, 3, GD0)
        c.set(x, 12, GD2)
        c.set(x, 15, MT0)
        if x % 4 == 2:
            c.set(x, 1, AR2)
            c.set(x, 13, AR2)
    save_tex(c, "block/arcane_collector_base")


def collector_body():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, GL0 if (x * 3 + y) % 7 else GL1)
    # Redemoinho (espiral) no vidro
    for i in range(70):
        t = i / 70 * math.pi * 3.2
        r = 0.6 + i / 70 * 5.2
        x = int(round(8 + math.cos(t) * r - 0.5))
        y = int(round(8 + math.sin(t) * r * 0.75 - 0.5))
        c.set(x, y, AR2 if i % 5 else AR3)
    c.set(7, 7, AR4)
    c.set(8, 8, AR4)
    for y in range(16):
        c.set(2, y, lighten(c.get(2, y), 0.25))
    for x in range(16):
        c.set(x, 4, MT1)
        c.set(x, 11, MT1)
    save_tex(c, "block/arcane_collector_body")


def collector_top():
    c = Canvas(16, 16)
    metal_plate(c, 121)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 5.4:
                ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                swirl = (ang + d * 0.9) % (math.pi / 2)
                c.set(x, y, AR1 if swirl < math.pi / 4 else AR0)
                if d <= 1.4:
                    c.set(x, y, AR4)
            elif d <= 6.4:
                c.set(x, y, GD2 if y < 8 else GD1)
    save_tex(c, "block/arcane_collector_top")


def collector_bottom():
    c = Canvas(16, 16)
    metal_plate(c, 123)
    save_tex(c, "block/arcane_collector_bottom")


# ------------------------------------------------------------------------------------------------
# Telas (256x256)
# ------------------------------------------------------------------------------------------------
P_BORDER = hex_rgba("08060C")
P_FILL_TOP = hex_rgba("2E2343")
P_FILL_BOT = hex_rgba("1E172C")
INSET_FILL = hex_rgba("110D19")
INSET_DARK = hex_rgba("06040A")
INSET_LIGHT = hex_rgba("54476F")
SLOT_FILL = hex_rgba("100C17")


def panel(c, w, h, seed):
    rnd = random.Random(seed)
    for y in range(h):
        base = mix(P_FILL_TOP, P_FILL_BOT, y / max(1, h - 1))
        for x in range(w):
            f = (rnd.random() - 0.5) * 0.06
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))
    # Moldura dourada
    for x in range(w):
        c.set(x, 0, P_BORDER); c.set(x, h - 1, P_BORDER)
        c.set(x, 1, GD2); c.set(x, h - 2, GD0)
        c.set(x, 2, GD1); c.set(x, h - 3, GD1)
    for y in range(h):
        c.set(0, y, P_BORDER); c.set(w - 1, y, P_BORDER)
        c.set(1, y, GD2); c.set(w - 2, y, GD0)
        c.set(2, y, GD1); c.set(w - 3, y, GD1)
    for (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        c.set(x, y, GD3)
    # Cantos com runa
    for (x, y) in ((4, 4), (w - 5, 4), (4, h - 5), (w - 5, h - 5)):
        c.set(x, y, AR2)


def inset(c, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, INSET_FILL)
    for x in range(x0, x1 + 1):
        c.set(x, y0, INSET_DARK); c.set(x, y1, INSET_LIGHT)
    for y in range(y0, y1 + 1):
        c.set(x0, y, INSET_DARK); c.set(x1, y, INSET_LIGHT)


def slot(c, x, y):
    """Moldura de slot 18x18 com o item em (x, y)."""
    x0, y0, x1, y1 = x - 1, y - 1, x + 16, y + 16
    for yy in range(y0, y1 + 1):
        for xx in range(x0, x1 + 1):
            c.set(xx, yy, SLOT_FILL)
    for xx in range(x0, x1 + 1):
        c.set(xx, y0, INSET_DARK); c.set(xx, y1, INSET_LIGHT)
    for yy in range(y0, y1 + 1):
        c.set(x0, yy, INSET_DARK); c.set(x1, yy, INSET_LIGHT)


def inventory(c, x, y, hotbar_y):
    for r in range(3):
        for col in range(9):
            slot(c, x + col * 18, y + r * 18)
    for col in range(9):
        slot(c, x + col * 18, hotbar_y)


def separator(c, x0, x1, y):
    for x in range(x0, x1 + 1):
        c.set(x, y, GD0 if x % 6 else GD1)
    c.set((x0 + x1) // 2, y, GD3)


def dotted_circle(c, cx, cy, r, color, every=2):
    n = int(2 * math.pi * r)
    for i in range(0, n, every):
        a = i / n * 2 * math.pi
        c.set(int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r)), color)


def gui_pedestal():
    c = Canvas(256, 256)
    W, H = 176, 236
    panel(c, W, H, 131)
    inset(c, 6, 15, 49, 100)      # coluna do pergaminho
    inset(c, 51, 14, 169, 100)    # configuracao
    # Linhas guia da configuracao
    for i in range(6):
        y = 18 + 14 * i + 10
        if i < 5:
            for x in range(54, 167, 2):
                c.set(x, y, hex_rgba("2A2140"))
    # Circulo runico em volta do slot do pergaminho (slot em 18,34; centro 26,42)
    dotted_circle(c, 26, 42, 15.5, GD1, 2)
    dotted_circle(c, 26, 42, 13.5, AR1, 3)
    for k in range(8):
        a = k * math.pi / 4
        c.set(int(round(26 + math.cos(a) * 15.5)), int(round(42 + math.sin(a) * 15.5)), GD3)
    slot(c, 18, 34)
    # Trilho da proxima conjuracao (9..46, 92..94)
    inset(c, 8, 91, 47, 96)
    # Barra de mana (9..166, 115..118)
    inset(c, 8, 114, 167, 119)
    separator(c, 6, 169, 142)
    inventory(c, 8, 154, 212)
    save_tex(c, "gui/automation_pedestal")


def gui_condenser():
    c = Canvas(256, 256)
    W, H = 176, 181
    panel(c, W, H, 141)
    inset(c, 24, 14, 89, 79 - 5)
    for r in range(3):
        for col in range(3):
            slot(c, 30 + col * 18, 20 + r * 18)
    # "Chama" (94..107, 37..52) com cantos dourados
    inset(c, 93, 36, 108, 53)
    for (x, y) in ((92, 35), (109, 35), (92, 54), (109, 54)):
        c.set(x, y, GD2)
    # Seta ate a reserva
    for x in range(112, 128):
        c.set(x, 44, GD1)
        c.set(x, 45, GD2)
    for k in range(4):
        c.set(127 - k, 44 - k, GD2)
        c.set(127 - k, 45 + k, GD2)
    # Reserva (134..143, 19..70)
    inset(c, 133, 18, 144, 71)
    for y in range(20, 70, 6):
        c.set(145, y, GD1)
    separator(c, 6, 169, 86)
    inventory(c, 8, 99, 157)
    save_tex(c, "gui/automation_condenser")


# ------------------------------------------------------------------------------------------------
# Modelos e blockstates
# ------------------------------------------------------------------------------------------------
def tex(name):
    return f"{NS}:block/{name}"


def side_faces(texture, uv):
    return {d: {"texture": texture, "uv": uv} for d in ("north", "south", "east", "west")}


def models():
    # Pedestal: base, coluna e tampo
    write_json(os.path.join(A, "models/block/runic_pedestal.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": tex("runic_pedestal_base_top"),
            "base_side": tex("runic_pedestal_base_side"),
            "base_top": tex("runic_pedestal_base_top"),
            "bottom": tex("runic_pedestal_bottom"),
            "column": tex("runic_pedestal_column"),
            "capital": tex("runic_pedestal_capital"),
            "top": tex("runic_pedestal_top"),
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {
                **side_faces("#base_side", [1, 13, 15, 16]),
                "up": {"texture": "#base_top", "uv": [1, 1, 15, 15]},
                "down": {"texture": "#bottom", "uv": [1, 1, 15, 15], "cullface": "down"}}},
            {"from": [4, 3, 4], "to": [12, 11, 12], "faces": side_faces("#column", [4, 4, 12, 12])},
            {"from": [2, 11, 2], "to": [14, 14, 14], "faces": {
                **side_faces("#capital", [2, 2, 14, 5]),
                "up": {"texture": "#top", "uv": [2, 2, 14, 14]},
                "down": {"texture": "#base_top", "uv": [2, 2, 14, 14]}}},
        ],
    })
    write_json(os.path.join(A, "models/block/runic_pedestal_active.json"), {
        "parent": f"{NS}:block/runic_pedestal",
        "textures": {"column": tex("runic_pedestal_column_active"), "top": tex("runic_pedestal_top_active")},
    })
    write_json(os.path.join(A, "blockstates/runic_pedestal.json"), {"variants": {
        "active=false": {"model": f"{NS}:block/runic_pedestal"},
        "active=true": {"model": f"{NS}:block/runic_pedestal_active"},
    }})
    write_json(os.path.join(A, "models/item/runic_pedestal.json"), {"parent": f"{NS}:block/runic_pedestal_active"})

    # Bateria: 5 niveis
    for lv in range(5):
        write_json(os.path.join(A, f"models/block/mana_battery_{lv}.json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"side": tex(f"mana_battery_side_{lv}"), "top": tex("mana_battery_top"), "bottom": tex("mana_battery_bottom")},
        })
    write_json(os.path.join(A, "blockstates/mana_battery.json"), {"variants": {
        f"charge={lv}": {"model": f"{NS}:block/mana_battery_{lv}"} for lv in range(5)}})
    write_json(os.path.join(A, "models/item/mana_battery.json"), {
        "parent": f"{NS}:block/mana_battery_0",
        "overrides": [{"predicate": {f"{NS}:charge": p}, "model": f"{NS}:block/mana_battery_{lv}"}
                      for lv, p in ((1, 0.0001), (2, 0.34), (3, 0.67), (4, 1.0))],
    })

    # Condensador: frente acesa/apagada, 4 direcoes
    for on in (False, True):
        name = "mana_condenser" + ("_on" if on else "")
        write_json(os.path.join(A, f"models/block/{name}.json"), {
            "parent": "minecraft:block/orientable_with_bottom",
            "textures": {"front": tex("mana_condenser_front" + ("_on" if on else "")), "side": tex("mana_condenser_side"),
                         "top": tex("mana_condenser_top"), "bottom": tex("mana_condenser_bottom")},
        })
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            v = {"model": f"{NS}:block/mana_condenser" + ("_on" if lit == "true" else "")}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},lit={lit}"] = v
    write_json(os.path.join(A, "blockstates/mana_condenser.json"), {"variants": variants})
    write_json(os.path.join(A, "models/item/mana_condenser.json"), {"parent": f"{NS}:block/mana_condenser"})

    # Coletor: base, corpo de vidro e borda
    write_json(os.path.join(A, "models/block/arcane_collector.json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": tex("arcane_collector_base"),
            "base": tex("arcane_collector_base"),
            "body": tex("arcane_collector_body"),
            "top": tex("arcane_collector_top"),
            "bottom": tex("arcane_collector_bottom"),
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                **{d: {"texture": "#base", "uv": [0, 12, 16, 16], "cullface": d} for d in ("north", "south", "east", "west")},
                "up": {"texture": "#bottom", "uv": [0, 0, 16, 16]},
                "down": {"texture": "#bottom", "uv": [0, 0, 16, 16], "cullface": "down"}}},
            {"from": [2, 4, 2], "to": [14, 12, 14], "faces": side_faces("#body", [2, 4, 14, 12])},
            {"from": [0, 12, 0], "to": [16, 16, 16], "faces": {
                **{d: {"texture": "#base", "uv": [0, 0, 16, 4], "cullface": d} for d in ("north", "south", "east", "west")},
                "up": {"texture": "#top", "uv": [0, 0, 16, 16], "cullface": "up"},
                "down": {"texture": "#bottom", "uv": [0, 0, 16, 16]}}},
        ],
    })
    write_json(os.path.join(A, "blockstates/arcane_collector.json"), {"variants": {"": {"model": f"{NS}:block/arcane_collector"}}})
    write_json(os.path.join(A, "models/item/arcane_collector.json"), {"parent": f"{NS}:block/arcane_collector"})


# ------------------------------------------------------------------------------------------------
# Dados: loot, receitas, tag de picareta
# ------------------------------------------------------------------------------------------------
def loot_tables():
    for bid in ("runic_pedestal", "mana_condenser", "arcane_collector", "mana_battery"):
        entry = {"type": "minecraft:item", "name": f"{NS}:{bid}"}
        if bid == "mana_battery":
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": [f"{NS}:battery_mana"]}]
        write_json(os.path.join(D, f"loot_table/blocks/{bid}.json"), {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/{bid}",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [entry],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })


def shaped(name, pattern, key, result, count=1, category="misc"):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


def recipes():
    m = f"{NS}:mana_crystal"
    ea = f"{NS}:essence_arcane"
    shaped("runic_pedestal", [" M ", "GEG", "SSS"], {
        "M": m, "G": "minecraft:gold_ingot", "E": ea, "S": "minecraft:polished_deepslate"},
        f"{NS}:runic_pedestal", category="misc")
    shaped("mana_battery", ["IGI", "MEM", "IGI"], {
        "I": "minecraft:iron_ingot", "G": "minecraft:glass", "M": m, "E": ea},
        f"{NS}:mana_battery", category="misc")
    shaped("mana_condenser", ["DMD", "EFE", "DRD"], {
        "D": "minecraft:cobbled_deepslate", "M": m, "E": f"{NS}:essence_fire", "F": "minecraft:furnace",
        "R": "minecraft:redstone"},
        f"{NS}:mana_condenser", category="misc")
    shaped("arcane_collector", ["WPW", "HMH", "III"], {
        "W": f"{NS}:essence_wind", "P": "minecraft:ender_pearl", "H": "minecraft:hopper", "M": m,
        "I": "minecraft:iron_ingot"},
        f"{NS}:arcane_collector", category="misc")


def tags():
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [
        f"{NS}:runic_pedestal", f"{NS}:mana_battery", f"{NS}:mana_condenser", f"{NS}:arcane_collector"]})


def main():
    pedestal_base_side()
    pedestal_base_top()
    pedestal_bottom()
    pedestal_column(False)
    pedestal_column(True)
    pedestal_capital_side()
    pedestal_top(False)
    pedestal_top(True)
    rune_ring_texture()
    halo_texture()
    for lv in range(5):
        battery_side(lv)
    battery_top()
    battery_bottom()
    condenser_side()
    condenser_front(False)
    condenser_front(True)
    condenser_top()
    condenser_bottom()
    collector_base()
    collector_body()
    collector_top()
    collector_bottom()
    gui_pedestal()
    gui_condenser()
    models()
    loot_tables()
    recipes()
    tags()
    print("automation: assets gerados")


if __name__ == "__main__":
    main()
