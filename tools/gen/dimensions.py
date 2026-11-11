"""Gera os assets e dados do modulo "dimensions" (Plano Elemental, Abismo, Santuario Celestial, Fenda do Crepusculo).

Uso (na raiz do projeto):  py -I tools/gen/dimensions.py [--only parte,parte]
  partes: textures, models, loot, recipes, tags, worldgen, structures, arena, chests, lang
  (v1.0: "arena" gera so a arena GRANDE da Fenda — twilight_arena.nbt — para o Dragao Primordial)
  (v0.9: para mexer só nas receitas/traduções use  --only recipes,lang)

Gera:
  - texturas dos blocos (16x16, portais animados), do ceu (orbe/eclipse) e o icone da Fenda;
  - blockstates, modelos de bloco/item, loot tables dos blocos, receitas;
  - dimension_type, dimension, noise, noise_settings, biomas, configured/placed features;
  - estruturas de chefe (.nbt + structure + structure_set + template_pool) e os hubs de chegada (.nbt);
  - loot tables dos baus, tags (merge) e as traducoes pt_br/en_us.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
from structgen import Structure, Byte  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/dimensions")
LANG = os.path.join(ROOT, "src/main/lang/dimensions")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rid(path):
    return f"{NS}:{path}"


# =================================================================================================
# Texturas
# =================================================================================================
def tile_noise(seed, size=16, cells=4):
    """Ruido de valor tileavel (0..1) com interpolacao suave."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    step = size / cells
    for y in range(size):
        for x in range(size):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = gx - int(gx), gy - int(gy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def ramp(dark, base, light, v):
    v = max(0.0, min(1.0, v))
    return mix(dark, base, v * 2) if v < 0.5 else mix(base, light, (v - 0.5) * 2)


def noisy_fill(c, dark, base, light, seed, fine=0.35):
    n1 = tile_noise(seed, 16, 4)
    n2 = tile_noise(seed + 101, 16, 8)
    rnd = random.Random(seed + 7)
    for y in range(16):
        for x in range(16):
            v = (1 - fine) * n1[y][x] + fine * n2[y][x] + (rnd.random() - 0.5) * 0.12
            c.set(x, y, ramp(dark, base, light, v))


def random_walk(c, color, seed, count, length, wrap=True, edge=None):
    rnd = random.Random(seed)
    for _ in range(count):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
        for _ in range(length):
            if edge is not None:
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ex, ey = (x + ox) % 16, (y + oy) % 16
                    if c.get(ex, ey) not in (color,):
                        if rnd.random() < 0.35:
                            c.set(ex, ey, edge)
            c.set(x % 16, y % 16, color)
            if rnd.random() < 0.35:
                dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)])
            x, y = (x + dx) % 16, (y + dy) % 16


def sparkles(c, color, seed, n, core=None):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c.set(x, y, color)
        if core and rnd.random() < 0.5:
            c.set((x + 1) % 16, y, lighten(color, 0.3))


def bricks(c, mortar, dark, base, light, seed, row_h=4, brick_w=8, offset_rows=True, highlight=0.18):
    n = tile_noise(seed, 16, 8)
    rnd = random.Random(seed)
    for row in range(16 // row_h):
        y0 = row * row_h
        off = (brick_w // 2) if (offset_rows and row % 2) else 0
        tone = rnd.uniform(-0.12, 0.12)
        for y in range(y0, y0 + row_h):
            for x in range(16):
                lx = (x + off) % brick_w
                if y == y0 + row_h - 1 or lx == brick_w - 1:
                    c.set(x, y, mortar)
                    continue
                v = 0.5 + (n[y][x] - 0.5) * 0.7 + tone
                col = ramp(dark, base, light, v)
                if y == y0 or lx == 0:
                    col = lighten(col, highlight)
                elif y == y0 + row_h - 2 or lx == brick_w - 2:
                    col = darken(col, 0.1)
                c.set(x, y, col)


def save_block(c, name):
    c.save(os.path.join(A, "textures/block", name + ".png"))


# ---- paletas
FS = [hex_rgba("5E6488"), hex_rgba("8C93BA"), hex_rgba("B9C0E0")]   # pedra flutuante (lavanda azulada)
FS_GLINT = hex_rgba("BFF6FF")
EMB = [hex_rgba("1E1414"), hex_rgba("3A2524"), hex_rgba("553634")]
LAVA = [hex_rgba("B5301A"), hex_rgba("FF7A2A"), hex_rgba("FFD24A")]
SKY_SOIL = [hex_rgba("4E4562"), hex_rgba("6F6587"), hex_rgba("8E85A6")]
SKY_GRASS = [hex_rgba("2F9A86"), hex_rgba("4FC8A6"), hex_rgba("8AF0CF")]
STORM = [hex_rgba("8A93A6"), hex_rgba("B8C0CF"), hex_rgba("E6EBF3")]
ABY = [hex_rgba("0E0C16"), hex_rgba("1C1828"), hex_rgba("2C2640")]
ABY_SPECK = hex_rgba("5A2E8A")
VOID = [hex_rgba("1A0830"), hex_rgba("4A1A80"), hex_rgba("9A4AE6"), hex_rgba("F0D8FF")]
MOSS = [hex_rgba("10262E"), hex_rgba("1E3F4A"), hex_rgba("2F5E68")]
MOSS_GLOW = hex_rgba("4FE8D6")
MARBLE = [hex_rgba("C9CDD9"), hex_rgba("E8EBF2"), hex_rgba("FAFBFF")]
GOLD = [hex_rgba("9A6A12"), hex_rgba("D8A83A"), hex_rgba("FFE28A")]
CLOUD = [hex_rgba("E9DFC9"), hex_rgba("FBF6EA"), hex_rgba("FFFFFF")]
RADIANT = [hex_rgba("E8B83A"), hex_rgba("FFE88A"), hex_rgba("FFF8D8"), hex_rgba("FFFFFF")]
TWI = [hex_rgba("2A1438"), hex_rgba("4E2560"), hex_rgba("7A3A78")]
ELEM = {"fire": hex_rgba("FF6A2B"), "water": hex_rgba("3FA9FF"), "earth": hex_rgba("C08A48"), "wind": hex_rgba("9FF0D0"),
        "light": hex_rgba("FFE27A"), "shadow": hex_rgba("8A4FC8")}


def tex_floating_stone():
    c = Canvas(16, 16)
    noisy_fill(c, *FS, seed=11)
    random_walk(c, darken(FS[0], 0.25), 12, 2, 6)
    sparkles(c, FS_GLINT, 13, 4)
    save_block(c, "floating_stone")


def tex_floating_stone_bricks():
    c = Canvas(16, 16)
    bricks(c, darken(FS[0], 0.35), FS[0], FS[1], FS[2], 21)
    sparkles(c, FS_GLINT, 22, 2)
    save_block(c, "floating_stone_bricks")


def tex_ember_rock():
    c = Canvas(16, 16)
    noisy_fill(c, *EMB, seed=31, fine=0.5)
    random_walk(c, LAVA[1], 32, 3, 5, edge=LAVA[0])
    random_walk(c, LAVA[1], 34, 3, 3, edge=LAVA[0])
    rnd = random.Random(33)
    for y in range(16):
        for x in range(16):
            if c.get(x, y) == LAVA[1] and rnd.random() < 0.3:
                c.set(x, y, LAVA[2])
    save_block(c, "ember_rock")


def tex_sky_soil():
    c = Canvas(16, 16)
    noisy_fill(c, *SKY_SOIL, seed=41, fine=0.55)
    rnd = random.Random(42)
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c.set(x, y, lighten(SKY_SOIL[2], 0.15))
        c.set((x + 1) % 16, y, darken(SKY_SOIL[0], 0.1))
    save_block(c, "sky_soil")
    return c


def tex_sky_grass(soil):
    top = Canvas(16, 16)
    noisy_fill(top, *SKY_GRASS, seed=51, fine=0.6)
    rnd = random.Random(52)
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        top.set(x, y, lighten(SKY_GRASS[2], 0.2))
    for _ in range(3):
        x, y = rnd.randrange(16), rnd.randrange(16)
        top.set(x, y, hex_rgba("E8FFF8"))
    save_block(top, "sky_grass_top")
    side = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            side.set(x, y, soil.get(x, y))
    for x in range(16):
        h = 3 + (rnd.random() < 0.5) + (rnd.random() < 0.25)
        for y in range(h):
            side.set(x, y, ramp(SKY_GRASS[0], SKY_GRASS[1], SKY_GRASS[2], 0.75 - y * 0.15 + rnd.uniform(-0.1, 0.1)))
        side.set(x, h, darken(SKY_GRASS[0], 0.15))
    save_block(side, "sky_grass_side")


def puffs(c, cols, seed, n, rmin, rmax):
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            c.set(x, y, cols[1])
    for _ in range(n):
        cx, cy, r = rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(rmin, rmax)
        for y in range(16):
            for x in range(16):
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        d = math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy)
                        if d <= r:
                            t = d / r
                            col = cols[2] if t < 0.55 and (y + 0.5 - cy - oy) < 0 else cols[1]
                            if t > 0.85 and (y + 0.5 - cy - oy) > 0:
                                col = cols[0]
                            c.set(x, y, col)


def tex_clouds():
    c = Canvas(16, 16)
    puffs(c, STORM, 61, 7, 3.0, 5.0)
    save_block(c, "storm_cloud")
    c = Canvas(16, 16)
    puffs(c, CLOUD, 62, 8, 3.0, 5.5)
    sparkles(c, hex_rgba("FFF1B8"), 63, 3)
    save_block(c, "cloud_block")


def tex_abyssal():
    c = Canvas(16, 16)
    noisy_fill(c, *ABY, seed=71)
    sparkles(c, ABY_SPECK, 72, 6)
    random_walk(c, darken(ABY[0], 0.4), 73, 2, 5)
    save_block(c, "abyssal_stone")
    c = Canvas(16, 16)
    bricks(c, hex_rgba("07060C"), ABY[0], ABY[1], ABY[2], 74, highlight=0.12)
    sparkles(c, ABY_SPECK, 75, 3)
    save_block(c, "abyssal_stone_bricks")


def crystal_facets(c, cols, seed, lines=5):
    """Facetas de cristal: regioes de Voronoi com tons diferentes e arestas claras."""
    rnd = random.Random(seed)
    pts = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.random()) for _ in range(lines)]
    for y in range(16):
        for x in range(16):
            best, second, tone = 99, 99, 0
            for (px, py, t) in pts:
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        d = math.hypot(x + 0.5 - px - ox, y + 0.5 - py - oy)
                        if d < best:
                            second, best, tone = best, d, t
                        elif d < second:
                            second = d
            v = 0.25 + tone * 0.5 + (y / 16.0) * -0.15
            col = ramp(cols[0], cols[1], cols[2], v)
            if second - best < 0.9:
                col = lighten(cols[2], 0.25)
            c.set(x, y, col)


def tex_crystals():
    c = Canvas(16, 16)
    crystal_facets(c, VOID[:3], 81, 6)
    sparkles(c, VOID[3], 82, 3, core=True)
    save_block(c, "void_crystal")
    c = Canvas(16, 16)
    crystal_facets(c, RADIANT[:3], 83, 6)
    sparkles(c, RADIANT[3], 84, 5, core=True)
    save_block(c, "radiant_crystal")


def tex_moss():
    c = Canvas(16, 16)
    noisy_fill(c, *MOSS, seed=91, fine=0.6)
    rnd = random.Random(92)
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c.set(x, y, MOSS_GLOW)
        if rnd.random() < 0.5:
            c.set(x, (y + 1) % 16, darken(MOSS_GLOW, 0.3))
    save_block(c, "abyssal_moss")


def tex_marble():
    c = Canvas(16, 16)
    noisy_fill(c, *MARBLE, seed=101, fine=0.25)
    random_walk(c, MARBLE[0], 102, 2, 9)
    random_walk(c, GOLD[1], 103, 1, 7)
    save_block(c, "celestial_marble")
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, MARBLE[1])
    n = tile_noise(104, 16, 4)
    for y in range(16):
        for x in range(16):
            tx, ty = x % 8, y % 8
            if tx == 7 or ty == 7:
                c.set(x, y, MARBLE[0])
            else:
                col = ramp(MARBLE[0], MARBLE[1], MARBLE[2], 0.45 + n[y][x] * 0.3)
                if tx == 0 or ty == 0:
                    col = lighten(col, 0.4)
                c.set(x, y, col)
    for (x, y) in ((7, 7), (15, 7), (7, 15), (15, 15)):
        c.set(x, y, GOLD[2])
    save_block(c, "celestial_marble_bricks")


def tex_twilight_stone():
    c = Canvas(16, 16)
    n = tile_noise(111, 16, 4)
    for y in range(16):
        for x in range(16):
            g = ((x + y) % 16) / 15.0
            v = 0.3 + 0.4 * n[y][x] + 0.2 * math.sin(g * math.pi)
            c.set(x, y, ramp(*TWI, v))
    # Tiles de 8x8 com junta
    for i in range(16):
        c.set(i, 7, darken(TWI[0], 0.3))
        c.set(7, i, darken(TWI[0], 0.3))
        c.set(i, 15, darken(TWI[0], 0.3))
        c.set(15, i, darken(TWI[0], 0.3))
    sparkles(c, hex_rgba("FFC8F0"), 112, 3)
    sparkles(c, hex_rgba("E8D0FF"), 113, 2)
    save_block(c, "twilight_stone")


def tex_portal_frames():
    # Moldura elemental: tijolos de pedra flutuante + gema de 4 cores no centro
    c = Canvas(16, 16)
    bricks(c, darken(FS[0], 0.4), darken(FS[0], 0.1), FS[1], FS[2], 121, row_h=8, brick_w=16, offset_rows=False)
    c.outline_rect(0, 0, 15, 15, darken(FS[0], 0.45))
    gem = [("fire", (7, 4)), ("water", (11, 7)), ("earth", (7, 11)), ("wind", (4, 7))]
    for name, (gx, gy) in gem:
        col = ELEM[name]
        c.rect(gx - 1, gy - 1, gx + 1, gy + 1, darken(col, 0.35))
        c.set(gx, gy, lighten(col, 0.3))
        c.set(gx - 1, gy - 1, lighten(col, 0.5))
    c.rect(7, 7, 8, 8, hex_rgba("FFFFFF"))
    save_block(c, "elemental_portal_frame_side")
    c = Canvas(16, 16)
    bricks(c, darken(FS[0], 0.4), darken(FS[0], 0.1), FS[1], FS[2], 122, row_h=8, brick_w=8)
    for i, name in enumerate(("fire", "water", "earth", "wind")):
        a = i * math.pi / 2 - math.pi / 4
        for r in (4.5, 5.5):
            x, y = int(7.5 + math.cos(a) * r), int(7.5 + math.sin(a) * r)
            c.set(x, y, ELEM[name])
    c.circle(8, 8, 2.2, darken(FS[0], 0.3))
    c.circle(8, 8, 1.2, hex_rgba("E8FFFF"))
    save_block(c, "elemental_portal_frame_top")
    # Moldura sombria: obsidiana com runa roxa
    obs = [hex_rgba("08040E"), hex_rgba("140A22"), hex_rgba("26173A")]
    c = Canvas(16, 16)
    noisy_fill(c, *obs, seed=123, fine=0.5)
    c.outline_rect(0, 0, 15, 15, hex_rgba("05020A"))
    rune = ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."]
    for ry, row in enumerate(rune):
        for rx, ch in enumerate(row):
            if ch == "#":
                c.set(5 + rx, 5 + ry, hex_rgba("B46AFF") if (rx + ry) % 2 == 0 else hex_rgba("7A3AC8"))
    sparkles(c, hex_rgba("5A2E8A"), 124, 3)
    save_block(c, "abyss_portal_frame_side")
    c = Canvas(16, 16)
    noisy_fill(c, *obs, seed=125, fine=0.5)
    c.outline_rect(0, 0, 15, 15, hex_rgba("05020A"))
    for i in range(12):
        a = i * math.pi * 2 / 12
        c.set(int(7.5 + math.cos(a) * 5), int(7.5 + math.sin(a) * 5), hex_rgba("9A4AE6"))
    c.circle(8, 8, 1.6, hex_rgba("D8A8FF"))
    save_block(c, "abyss_portal_frame_top")


def portal_anim(name, palette, seed, frames=32, alpha=210, arms=3):
    c = Canvas(16, 16 * frames)
    rnd = random.Random(seed)
    stars = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(0, 6.28)) for _ in range(10)]
    for f in range(frames):
        t = f / frames * math.tau
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = 0.5 + 0.5 * math.sin(a * arms + r * 0.75 - t)
                w = 0.5 + 0.5 * math.sin(r * 0.9 - t * 2 + a)
                idx = (v * (len(palette) - 1))
                i0 = int(idx)
                i1 = min(len(palette) - 1, i0 + 1)
                col = mix(palette[i0], palette[i1], idx - i0)
                col = lighten(col, 0.25 * w) if w > 0.7 else col
                c.set(x, y + f * 16, (col[0], col[1], col[2], alpha))
        for (sx, sy, ph) in stars:
            b = 0.5 + 0.5 * math.sin(t * 2 + ph)
            if b > 0.6:
                c.set(int(sx), int(sy) + f * 16, (255, 255, 255, 240))
    save_block(c, name)
    with open(os.path.join(A, "textures/block", name + ".png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": 2, "interpolate": True}}, fh, indent=2)


def tex_portals():
    portal_anim("elemental_portal", [hex_rgba("FF7A3A"), hex_rgba("FFD27A"), hex_rgba("6FE8C8"), hex_rgba("3FA9FF"),
                                     hex_rgba("A8E8FF"), hex_rgba("C08A48"), hex_rgba("FF7A3A")], 131, alpha=200, arms=4)
    portal_anim("abyss_portal", [hex_rgba("05020A"), hex_rgba("2A0A4A"), hex_rgba("6A1AAA"), hex_rgba("C46AFF"),
                                 hex_rgba("2A0A4A"), hex_rgba("05020A")], 132, alpha=225, arms=2)


def tex_beacon():
    # Base: marmore com faixa dourada
    c = Canvas(16, 16)
    noisy_fill(c, *MARBLE, seed=141, fine=0.3)
    for x in range(16):
        c.set(x, 12, GOLD[1])
        c.set(x, 13, GOLD[2] if x % 4 == 0 else GOLD[1])
        c.set(x, 15, MARBLE[0])
    save_block(c, "celestial_beacon_base")
    # Coluna: ouro canelado
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = x % 4
            col = [GOLD[2], GOLD[1], GOLD[1], GOLD[0]][k]
            if y in (0, 15):
                col = GOLD[0]
            c.set(x, y, col)
    sparkles(c, hex_rgba("FFFFFF"), 142, 2)
    save_block(c, "celestial_beacon_column")
    # Topo: lente de cristal com aro de ouro (apagada / acesa)
    for active in (False, True):
        c = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                c.set(x, y, GOLD[1] if (x + y) % 2 else GOLD[2])
        c.outline_rect(0, 0, 15, 15, GOLD[0])
        lens = RADIANT if active else [hex_rgba("8A9AB0"), hex_rgba("B8C8D8"), hex_rgba("E0ECF4"), hex_rgba("FFFFFF")]
        c.circle(8, 8, 6.0, GOLD[0])
        c.circle(8, 8, 5.2, lens[0])
        c.circle(8, 8, 4.2, lens[1])
        c.circle(8, 8, 2.6, lens[2])
        c.circle(7, 7, 1.2, lens[3])
        save_block(c, "celestial_beacon_top_active" if active else "celestial_beacon_top")
    # Particula do feixe
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8
            if d < 1:
                a = int(255 * (1 - d) ** 1.5)
                c.set(x, y, (255, 246, 200, a))
    save_block(c, "celestial_beam")


def tex_rift():
    def starfield(c, seed, ox=0, oy=0, w=16, h=16):
        rnd = random.Random(seed)
        for y in range(h):
            for x in range(w):
                v = rnd.random()
                c.set(ox + x, oy + y, mix(hex_rgba("0A0614"), hex_rgba("2A0E3A"), v * 0.6))
        for _ in range(9):
            c.set(ox + rnd.randrange(w), oy + rnd.randrange(h), rnd.choice([hex_rgba("FFFFFF"), hex_rgba("FFB8E8"), hex_rgba("C8A8FF")]))
    c = Canvas(16, 16)
    starfield(c, 151)
    save_block(c, "twilight_rift")
    # Icone do item: rasgo vertical brilhante
    c = Canvas(16, 16)
    rnd = random.Random(152)
    for y in range(1, 15):
        half = 1.0 + 2.6 * math.sin(math.pi * (y - 1) / 13.0)
        cx = 7.5 + math.sin(y * 1.3) * 0.8
        for x in range(16):
            d = abs(x + 0.5 - cx)
            if d <= half + 1:
                if d <= half - 0.6:
                    v = rnd.random()
                    col = mix(hex_rgba("0A0614"), hex_rgba("3A1050"), v)
                    if rnd.random() < 0.12:
                        col = hex_rgba("FFFFFF")
                elif d <= half + 0.2:
                    col = hex_rgba("FF8AD8") if y % 3 else hex_rgba("FFD27A")
                else:
                    col = hex_rgba("8A3AC8")
                c.set(x, y, col)
    c.save(os.path.join(A, "textures/item/twilight_rift.png"))


def tex_altar():
    c = Canvas(16, 16)
    bricks(c, darken(FS[0], 0.35), FS[0], FS[1], FS[2], 161, row_h=4, brick_w=8)
    for x in range(16):
        c.set(x, 0, GOLD[1])
        c.set(x, 15, darken(FS[0], 0.3))
    save_block(c, "convergence_altar_base")
    c = Canvas(16, 16)
    noisy_fill(c, *FS, seed=162, fine=0.3)
    for i, name in enumerate(("fire", "water", "earth", "wind")):
        x = 2 + i * 4
        for y in range(3, 13):
            col = ELEM[name] if y % 3 else lighten(ELEM[name], 0.4)
            c.set(x, y, col)
            c.set(x + 1, y, darken(ELEM[name], 0.3))
    for x in range(16):
        c.set(x, 0, GOLD[1])
        c.set(x, 15, GOLD[0])
    save_block(c, "convergence_altar_column")
    c = Canvas(16, 16)
    noisy_fill(c, darken(FS[0], 0.2), FS[0], FS[1], seed=163, fine=0.3)
    c.outline_rect(0, 0, 15, 15, GOLD[0])
    c.outline_rect(1, 1, 14, 14, GOLD[1])
    for i, name in enumerate(("fire", "water", "earth", "wind", "light", "shadow")):
        a = -math.pi / 2 + i * math.pi / 3
        gx, gy = int(round(7.5 + math.cos(a) * 4.6)), int(round(7.5 + math.sin(a) * 4.6))
        c.rect(gx, gy, gx + 1, gy + 1, ELEM[name])
        c.set(gx, gy, lighten(ELEM[name], 0.5))
    c.circle(8, 8, 2.0, hex_rgba("F0E8FF"))
    c.circle(8, 8, 1.0, hex_rgba("FFFFFF"))
    save_block(c, "convergence_altar_top")


def tex_environment():
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16
            if d < 1:
                a = (1 - d) ** 2.2
                core = max(0.0, 1 - d * 3)
                v = min(1.0, a + core * 0.6)
                c.set(x, y, (255, 255, 255, int(255 * v)))
    c.save(os.path.join(A, "textures/environment/sky_orb.png"))
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if d <= 14.0:
                c.set(x, y, (255, 255, 255, 255))
            elif d <= 15.0:
                c.set(x, y, (255, 255, 255, int(255 * (15.0 - d))))
    c.save(os.path.join(A, "textures/environment/eclipse_disc.png"))


def gen_textures():
    tex_floating_stone()
    tex_floating_stone_bricks()
    tex_ember_rock()
    soil = tex_sky_soil()
    tex_sky_grass(soil)
    tex_clouds()
    tex_abyssal()
    tex_crystals()
    tex_moss()
    tex_marble()
    tex_twilight_stone()
    tex_portal_frames()
    tex_portals()
    tex_beacon()
    tex_rift()
    tex_altar()
    tex_environment()


# =================================================================================================
# Modelos, blockstates, loot de blocos
# =================================================================================================
CUBE_ALL = ["floating_stone", "floating_stone_bricks", "ember_rock", "sky_soil", "storm_cloud", "abyssal_stone",
            "abyssal_stone_bricks", "void_crystal", "abyssal_moss", "celestial_marble", "celestial_marble_bricks",
            "cloud_block", "radiant_crystal", "twilight_stone"]
ROTATED = {"floating_stone", "ember_rock", "sky_soil", "storm_cloud", "abyssal_stone", "abyssal_moss", "celestial_marble", "cloud_block"}
ITEM_BLOCKS = CUBE_ALL + ["sky_grass", "elemental_portal_frame", "abyss_portal_frame", "celestial_beacon", "twilight_rift", "convergence_altar"]


def model(name, obj):
    write_json(os.path.join(A, "models/block", name + ".json"), obj)


def blockstate(name, obj):
    write_json(os.path.join(A, "blockstates", name + ".json"), obj)


def item_model_block(name, parent=None):
    write_json(os.path.join(A, "models/item", name + ".json"), {"parent": parent or rid("block/" + name)})


def pillar_shape(tex_base, tex_column, tex_top, column_to, top_from):
    """Base 16x4x16, coluna 10x(column_to-4)x10, tampo 14x(16-top_from)x14 (altar e farol)."""
    side_base = {"uv": [0, 12, 16, 16], "texture": "#base"}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex_base, "base": tex_base, "column": tex_column, "top": tex_top},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#base", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#base"},
                "north": dict(side_base, cullface="north"), "south": dict(side_base, cullface="south"),
                "east": dict(side_base, cullface="east"), "west": dict(side_base, cullface="west")}},
            {"from": [3, 4, 3], "to": [13, column_to, 13], "faces": {
                f: {"uv": [3, 16 - column_to, 13, 12], "texture": "#column"} for f in ("north", "south", "east", "west")}},
            {"from": [1, top_from, 1], "to": [15, 16, 15], "faces": {
                "up": {"uv": [1, 1, 15, 15], "texture": "#top"},
                "down": {"uv": [1, 1, 15, 15], "texture": "#base"},
                **{f: {"uv": [1, 0, 15, 16 - top_from], "texture": "#column"} for f in ("north", "south", "east", "west")}}},
        ],
    }


def gen_models():
    for name in CUBE_ALL:
        model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": rid("block/" + name)}})
        if name in ROTATED:
            m = rid("block/" + name)
            blockstate(name, {"variants": {"": [{"model": m}, {"model": m, "y": 90}, {"model": m, "y": 180}, {"model": m, "y": 270}]}})
        else:
            blockstate(name, {"variants": {"": {"model": rid("block/" + name)}}})
    model("sky_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": rid("block/sky_grass_top"), "side": rid("block/sky_grass_side"), "bottom": rid("block/sky_soil")}})
    m = rid("block/sky_grass")
    blockstate("sky_grass", {"variants": {"": [{"model": m}, {"model": m, "y": 90}, {"model": m, "y": 180}, {"model": m, "y": 270}]}})
    for frame in ("elemental_portal_frame", "abyss_portal_frame"):
        model(frame, {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": rid(f"block/{frame}_top"), "side": rid(f"block/{frame}_side"), "bottom": rid(f"block/{frame}_top")}})
        blockstate(frame, {"variants": {"": {"model": rid("block/" + frame)}}})
    for portal in ("elemental_portal", "abyss_portal"):
        tex = rid("block/" + portal)
        model(portal + "_ns", {"render_type": "minecraft:translucent", "textures": {"particle": tex, "portal": tex}, "elements": [
            {"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "south": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]})
        model(portal + "_ew", {"render_type": "minecraft:translucent", "textures": {"particle": tex, "portal": tex}, "elements": [
            {"from": [6, 0, 0], "to": [10, 16, 16], "faces": {
                "east": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "west": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]})
        blockstate(portal, {"variants": {"axis=x": {"model": rid(f"block/{portal}_ns")}, "axis=z": {"model": rid(f"block/{portal}_ew")}}})
    # Farol celestial (apagado/aceso)
    for active in (False, True):
        name = "celestial_beacon_active" if active else "celestial_beacon"
        model(name, pillar_shape(rid("block/celestial_beacon_base"), rid("block/celestial_beacon_column"),
                                 rid("block/celestial_beacon_top_active" if active else "block/celestial_beacon_top"), 12, 12))
    blockstate("celestial_beacon", {"variants": {"active=false": {"model": rid("block/celestial_beacon")},
                                                 "active=true": {"model": rid("block/celestial_beacon_active")}}})
    # Coluna de luz e Fenda: invisiveis no modelo (desenhadas por partículas/renderer), so a particula de quebra
    model("celestial_portal", {"textures": {"particle": rid("block/celestial_beam")}})
    blockstate("celestial_portal", {"variants": {"": {"model": rid("block/celestial_portal")}}})
    model("twilight_rift", {"textures": {"particle": rid("block/twilight_rift")}})
    blockstate("twilight_rift", {"variants": {"": {"model": rid("block/twilight_rift")}}})
    # Altar
    model("convergence_altar", pillar_shape(rid("block/convergence_altar_base"), rid("block/convergence_altar_column"),
                                            rid("block/convergence_altar_top"), 11, 11))
    blockstate("convergence_altar", {"variants": {"": {"model": rid("block/convergence_altar")}}})
    # Itens
    for name in ITEM_BLOCKS:
        if name == "twilight_rift":
            write_json(os.path.join(A, "models/item/twilight_rift.json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": rid("item/twilight_rift")}})
        else:
            item_model_block(name)


def gen_block_loot():
    def simple(name):
        return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": rid(name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": rid("blocks/" + name)}
    for name in ITEM_BLOCKS:
        if name == "sky_grass":
            continue
        write_json(os.path.join(D, "loot_table/blocks", name + ".json"), simple(name))
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write_json(os.path.join(D, "loot_table/blocks/sky_grass.json"), {
        "type": "minecraft:block", "random_sequence": rid("blocks/sky_grass"),
        "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": rid("sky_grass"), "conditions": [silk]},
            {"type": "minecraft:item", "name": rid("sky_soil"), "conditions": [{"condition": "minecraft:survives_explosion"}]}]}]}]})


def gen_recipes():
    def shaped(name, pattern, key, count=1, category="misc"):
        k = {}
        for ch, item in key.items():
            k[ch] = {"tag": item[1:]} if item.startswith("#") else {"item": item}
        write_json(os.path.join(D, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": k,
            "result": {"id": rid(name), "count": count}})
    # v0.9: os portais viraram ESTRUTURAS (world_v09) com o portal adormecido pronto. Moldura elemental, moldura
    # sombria, Farol Celestial e Fenda do Crepúsculo não têm mais receita (os blocos seguem funcionando em mundos
    # antigos e por comando/criativo). Apaga as receitas antigas se ainda existirem.
    for removed in ("elemental_portal_frame", "abyss_portal_frame", "celestial_beacon", "twilight_rift"):
        old = os.path.join(D, "recipe", removed + ".json")
        if os.path.exists(old):
            os.remove(old)
    shaped("convergence_altar", ["FMW", "BDB", "EMA"], {
        "F": rid("essence_fire"), "W": rid("essence_water"), "E": rid("essence_earth"), "A": rid("essence_wind"),
        "M": rid("mana_crystal"), "B": rid("floating_stone_bricks"), "D": "minecraft:diamond_block"})
    for bricks_id, stone in (("floating_stone_bricks", "floating_stone"), ("abyssal_stone_bricks", "abyssal_stone"),
                             ("celestial_marble_bricks", "celestial_marble")):
        shaped(bricks_id, ["SS", "SS"], {"S": rid(stone)}, 4, "building")


def gen_tags():
    def tag(path, values):
        write_json(os.path.join(MERGE, "data", path + ".json"), {"values": values})
    pick = ["floating_stone", "floating_stone_bricks", "ember_rock", "abyssal_stone", "abyssal_stone_bricks", "void_crystal",
            "celestial_marble", "celestial_marble_bricks", "radiant_crystal", "twilight_stone", "elemental_portal_frame",
            "abyss_portal_frame", "celestial_beacon", "twilight_rift", "convergence_altar"]
    tag("minecraft/tags/block/mineable/pickaxe", [rid(n) for n in pick])
    tag("minecraft/tags/block/mineable/shovel", [rid("sky_grass"), rid("sky_soil")])
    tag("minecraft/tags/block/mineable/hoe", [rid("abyssal_moss")])
    tag("minecraft/tags/block/needs_iron_tool", [rid(n) for n in ("elemental_portal_frame", "abyss_portal_frame", "celestial_beacon",
                                                                    "convergence_altar")])
    tag("minecraft/tags/block/needs_diamond_tool", [rid("twilight_rift")])
    # Grama/terra do ceu contam como terra: flores, mudas e arvores crescem nelas.
    tag("minecraft/tags/block/dirt", [rid("sky_grass"), rid("sky_soil")])
    tag("minecraft/tags/block/animals_spawnable_on", [rid("sky_grass")])
    # Pedras das dimensoes recebem minerios do vanilla e servem de "pedra" para rochedos/musgo.
    tag("minecraft/tags/block/base_stone_overworld", [rid("floating_stone"), rid("abyssal_stone"), rid("celestial_marble")])
    tag("minecraft/tags/block/stone_ore_replaceables", [rid("floating_stone"), rid("abyssal_stone")])
    tag("minecraft/tags/item/stone_tool_materials", [rid("floating_stone"), rid("abyssal_stone")])
    tag("minecraft/tags/item/stone_crafting_materials", [rid("floating_stone"), rid("abyssal_stone")])
    biomes = {
        "is_elemental_plane": ["ember_isles", "suspended_sea", "earthen_peaks", "storm_skies"],
        "is_abyss": ["abyssal_depths", "void_hollows"],
        "is_celestial": ["crystal_city", "cloud_gardens"],
        "is_twilight_rift": ["twilight_rift"],
    }
    for t, ids in biomes.items():
        tag(f"{NS}/tags/worldgen/biome/{t}", [rid(i) for i in ids])
    for struct, ids in STRUCTURE_BIOMES.items():
        tag(f"{NS}/tags/worldgen/biome/has_structure/{struct}", [rid(i) for i in ids])


# =================================================================================================
# Mundo: dimensoes, ruido, biomas, features
# =================================================================================================
def noise_df(noise, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": rid(noise), "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def dmax(a, b):
    return {"type": "minecraft:max", "argument1": a, "argument2": b}


def dmin(a, b):
    return {"type": "minecraft:min", "argument1": a, "argument2": b}


def ygrad(y0, y1, v0, v1):
    return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}


def island_density(prefix, center, mask_mul, mask_off, up, down, detail_amp, height_amp, unit=24.0):
    """Ilhas flutuantes: "mascara" 2D decide onde ha ilha e o quao grossa ela e; o perfil vertical e assimetrico
    (topo achatado: cai rapido acima do centro; fundo pendurado: afina devagar para baixo)."""
    t = add(ygrad(0, 256, -center / unit, (256 - center) / unit), mul(height_amp, noise_df(prefix + "_island_height")))
    p = add(mul(up, dmax(t, 0.0)), mul(-down, dmin(t, 0.0)))
    dens = add(add(mask_off, mul(mask_mul, noise_df(prefix + "_island_mask"))),
               add(mul(detail_amp, noise_df(prefix + "_island_detail", 1.0, 1.0)), mul(-1.0, p)))
    return {"type": "minecraft:interpolated", "argument": dens}


def router(final, temperature=0.0, vegetation=0.0):
    return {"barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": temperature, "vegetation": vegetation, "continents": 0.0, "erosion": 0.0, "depth": 0.0,
            "ridges": 0.0, "initial_density_without_jaggedness": 0.0, "final_density": final,
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0}


def block(name, props=None):
    d = {"Name": name}
    if props:
        d["Properties"] = props
    return d


def rule_block(name, props=None):
    return {"type": "minecraft:block", "result_state": block(name, props)}


def cond(if_true, then):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": [rid(i) for i in ids]}


def floor_top():
    return {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False, "secondary_depth_range": 0, "surface_type": "floor"}


def floor_deep(extra=0):
    return {"type": "minecraft:stone_depth", "offset": extra, "add_surface_depth": True, "secondary_depth_range": 0, "surface_type": "floor"}


def ceiling_deep():
    return {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True, "secondary_depth_range": 0, "surface_type": "ceiling"}


def noise_thr(noise, lo, hi=1.7976931348623157e308):
    return {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi}


WATER = block("minecraft:water", {"level": "0"})


def gen_noises():
    noises = {
        "elemental_island_mask": (-7, [1.0, 1.0, 0.5]),
        "elemental_island_height": (-8, [1.0, 0.5]),
        "elemental_island_detail": (-5, [1.0, 0.6, 0.4]),
        "elemental_temperature": (-9, [1.0, 0.8, 0.4]),
        "elemental_humidity": (-9, [1.0, 0.8, 0.4]),
        "celestial_island_mask": (-7, [1.0, 0.8, 0.5]),
        "celestial_island_height": (-8, [1.0, 0.5]),
        "celestial_island_detail": (-5, [1.0, 0.7, 0.3]),
        "celestial_temperature": (-8, [1.0, 0.8, 0.4]),
        "abyss_temperature": (-8, [1.0, 0.8, 0.4]),
    }
    for name, (oct_, amps) in noises.items():
        write_json(os.path.join(D, "worldgen/noise", name + ".json"), {"firstOctave": oct_, "amplitudes": amps})


def gen_dimension_types():
    base = {"ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "piglin_safe": False, "respawn_anchor_works": False,
            "has_raids": False, "min_y": 0, "height": 256, "logical_height": 256,
            "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
            "monster_spawn_block_light_limit": 0}
    write_json(os.path.join(D, "dimension_type/elemental_plane.json"), dict(base, **{
        "has_skylight": True, "has_ceiling": False, "ambient_light": 0.15, "fixed_time": 6000, "bed_works": True,
        "infiniburn": "#minecraft:infiniburn_overworld", "effects": rid("elemental_plane")}))
    write_json(os.path.join(D, "dimension_type/abyss.json"), dict(base, **{
        "has_skylight": False, "has_ceiling": True, "ambient_light": 0.06, "fixed_time": 18000, "bed_works": False,
        "height": 128, "logical_height": 128, "infiniburn": "#minecraft:infiniburn_nether", "effects": "minecraft:the_nether"}))
    write_json(os.path.join(D, "dimension_type/celestial_sanctuary.json"), dict(base, **{
        "has_skylight": True, "has_ceiling": False, "ambient_light": 0.2, "fixed_time": 6000, "bed_works": True,
        "infiniburn": "#minecraft:infiniburn_overworld", "effects": rid("celestial_sanctuary")}))
    write_json(os.path.join(D, "dimension_type/twilight_rift.json"), dict(base, **{
        "has_skylight": True, "has_ceiling": False, "ambient_light": 0.1, "fixed_time": 12500, "bed_works": False,
        "infiniburn": "#minecraft:infiniburn_end", "effects": rid("twilight_rift")}))


def params(t=(-1.0, 1.0), h=(-1.0, 1.0)):
    return {"temperature": list(t), "humidity": list(h), "continentalness": 0.0, "erosion": 0.0, "weirdness": 0.0,
            "depth": 0.0, "offset": 0.0}


def gen_dimensions():
    write_json(os.path.join(D, "dimension/elemental_plane.json"), {
        "type": rid("elemental_plane"),
        "generator": {"type": "minecraft:noise", "settings": rid("elemental_plane"), "biome_source": {
            "type": "minecraft:multi_noise", "biomes": [
                {"biome": rid("ember_isles"), "parameters": params((0.0, 1.0), (-1.0, 0.0))},
                {"biome": rid("suspended_sea"), "parameters": params((-1.0, 0.0), (0.0, 1.0))},
                {"biome": rid("earthen_peaks"), "parameters": params((-1.0, 0.0), (-1.0, 0.0))},
                {"biome": rid("storm_skies"), "parameters": params((0.0, 1.0), (0.0, 1.0))}]}}})
    write_json(os.path.join(D, "dimension/abyss.json"), {
        "type": rid("abyss"),
        "generator": {"type": "minecraft:noise", "settings": rid("abyss"), "biome_source": {
            "type": "minecraft:multi_noise", "biomes": [
                {"biome": rid("abyssal_depths"), "parameters": params((-1.0, 0.15))},
                {"biome": rid("void_hollows"), "parameters": params((0.15, 1.0))}]}}})
    write_json(os.path.join(D, "dimension/celestial_sanctuary.json"), {
        "type": rid("celestial_sanctuary"),
        "generator": {"type": "minecraft:noise", "settings": rid("celestial_sanctuary"), "biome_source": {
            "type": "minecraft:multi_noise", "biomes": [
                {"biome": rid("crystal_city"), "parameters": params((0.05, 1.0))},
                {"biome": rid("cloud_gardens"), "parameters": params((-1.0, 0.05))}]}}})
    write_json(os.path.join(D, "dimension/twilight_rift.json"), {
        "type": rid("twilight_rift"),
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": rid("twilight_rift"), "layers": [{"block": "minecraft:air", "height": 1}],
            "lakes": False, "features": False, "structure_overrides": []}}})


def gen_noise_settings():
    common = {"sea_level": 0, "disable_mob_generation": False, "aquifers_enabled": False, "ore_veins_enabled": False,
              "legacy_random_source": False, "default_fluid": WATER, "spawn_target": []}
    # ---------------- Plano Elemental
    ep_surface = seq(
        cond(biome_is("ember_isles"), seq(
            cond(floor_deep(), rule_block(rid("ember_rock"))),
            cond(ceiling_deep(), rule_block("minecraft:basalt", {"axis": "y"})))),
        cond(biome_is("suspended_sea"), seq(
            cond(floor_top(), rule_block(rid("sky_grass"))),
            cond(floor_deep(), rule_block(rid("sky_soil"))),
            cond(ceiling_deep(), rule_block("minecraft:prismarine")))),
        cond(biome_is("storm_skies"), seq(
            cond(floor_top(), rule_block(rid("sky_grass"))),
            cond(floor_deep(), rule_block(rid("sky_soil"))))),
        cond(biome_is("earthen_peaks"), seq(
            cond(floor_top(), seq(cond(noise_thr("minecraft:surface", 0.12), rule_block("minecraft:moss_block")),
                                  rule_block(rid("sky_grass")))),
            cond(floor_deep(), rule_block(rid("sky_soil"))),
            cond(noise_thr("minecraft:calcite", -0.0125, 0.0125), rule_block("minecraft:calcite")),
            cond(noise_thr("minecraft:packed_ice", -0.04, 0.04), rule_block("minecraft:tuff")))),
    )
    write_json(os.path.join(D, "worldgen/noise_settings/elemental_plane.json"), dict(common, **{
        "default_block": block(rid("floating_stone")),
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 1, "size_vertical": 1},
        "noise_router": router(island_density("elemental", 100.0, 4.5, -1.0, 3.0, 0.6, 0.4, 1.5),
                               noise_df("elemental_temperature"), noise_df("elemental_humidity")),
        "surface_rule": ep_surface}))
    # ---------------- Santuario Celestial (ilhas mais altas, bordas e fundos de nuvem)
    cs_surface = seq(
        cond(biome_is("cloud_gardens"), seq(
            cond(floor_top(), rule_block(rid("sky_grass"))),
            cond(floor_deep(), rule_block(rid("cloud_block"))),
            cond(ceiling_deep(), rule_block(rid("cloud_block"))))),
        cond(biome_is("crystal_city"), seq(
            cond(floor_top(), seq(cond(noise_thr("minecraft:surface", 0.25), rule_block(rid("cloud_block"))),
                                  rule_block(rid("celestial_marble")))),
            cond(ceiling_deep(), rule_block(rid("cloud_block"))))),
    )
    write_json(os.path.join(D, "worldgen/noise_settings/celestial_sanctuary.json"), dict(common, **{
        "default_block": block(rid("celestial_marble")),
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 1, "size_vertical": 1},
        "noise_router": router(island_density("celestial", 140.0, 4.0, -0.85, 4.0, 0.8, 0.3, 1.0),
                               noise_df("celestial_temperature")),
        "surface_rule": cs_surface}))
    # ---------------- Abismo (cavernas estilo Nether, mar abissal de agua no fundo; vazios mais abertos)
    base = add("minecraft:nether/base_3d_noise", mul(-0.25, dmax(noise_df("abyss_temperature"), 0.0)))
    final = {"type": "minecraft:squeeze", "argument": mul(0.64, {"type": "minecraft:interpolated", "argument": {
        "type": "minecraft:blend_density", "argument": add(2.5, mul(ygrad(-8, 24, 0.0, 1.0), add(-2.5, add(0.9375, mul(
            ygrad(104, 128, 1.0, 0.0), add(-0.9375, base))))))}})}
    ab_surface = seq(
        cond({"type": "minecraft:vertical_gradient", "random_name": rid("abyss_bedrock_floor"),
              "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, rule_block("minecraft:bedrock")),
        cond({"type": "minecraft:not", "invert": {"type": "minecraft:vertical_gradient", "random_name": rid("abyss_bedrock_roof"),
              "true_at_and_below": {"below_top": 5}, "false_at_and_above": {"below_top": 0}}}, rule_block("minecraft:bedrock")),
        cond(biome_is("abyssal_depths"), seq(
            cond(floor_top(), seq(cond(noise_thr("minecraft:nether_state_selector", 0.0), rule_block(rid("abyssal_moss"))),
                                  rule_block(rid("abyssal_stone")))))),
        cond(biome_is("void_hollows"), seq(
            cond(floor_top(), seq(cond(noise_thr("minecraft:surface", 0.35), rule_block("minecraft:crying_obsidian")),
                                  cond(noise_thr("minecraft:surface", 0.1), rule_block("minecraft:obsidian")),
                                  rule_block(rid("abyssal_stone")))))),
    )
    write_json(os.path.join(D, "worldgen/noise_settings/abyss.json"), dict(common, **{
        "sea_level": 24, "default_block": block(rid("abyssal_stone")),
        "noise": {"min_y": 0, "height": 128, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": router(final, noise_df("abyss_temperature")),
        "surface_rule": ab_surface}))


# ---------------- Features
def p_count(n):
    return {"type": "minecraft:count", "count": n}


def p_square():
    return {"type": "minecraft:in_square"}


def p_height(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
            "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def p_heightmap(kind="WORLD_SURFACE_WG"):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def p_biome():
    return {"type": "minecraft:biome"}


def p_rarity(n):
    return {"type": "minecraft:rarity_filter", "chance": n}


def p_survive(state):
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": block(state)}}


def p_below_is(*blocks):
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks",
            "blocks": list(blocks), "offset": [0, -1, 0]}}


def p_scan(direction, target_face):
    return {"type": "minecraft:environment_scan", "direction_of_search": direction, "max_steps": 16,
            "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            "target_condition": {"type": "minecraft:has_sturdy_face", "direction": target_face}}


def p_offset(y):
    return {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": y}


def simple_state(name, props=None):
    return {"type": "minecraft:simple_state_provider", "state": block(name, props)}


def ore(targets, size, discard=0.0):
    return {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": discard, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": t}, "state": block(s)} for t, s in targets]}}


def spring(fluid, valid, below=True):
    return {"type": "minecraft:spring_feature", "config": {"state": block(fluid, {"falling": "true"}), "requires_block_below": below,
            "rock_count": 4, "hole_count": 1, "valid_blocks": valid}}


def column(layers, direction="up"):
    return {"type": "minecraft:block_column", "config": {
        "direction": direction, "prioritize_tip": True,
        "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
        "layers": [{"height": h, "provider": simple_state(*s) if isinstance(s, tuple) else simple_state(s)} for h, s in layers]}}


def uni(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


CONFIGURED = {
    "ep_ore_magma": ore([(rid("ember_rock"), "minecraft:magma_block"), (rid("floating_stone"), "minecraft:magma_block")], 20),
    "ep_ore_prismarine": ore([(rid("floating_stone"), "minecraft:prismarine")], 18),
    "ep_ore_sea_lantern": ore([(rid("floating_stone"), "minecraft:sea_lantern"), ("minecraft:prismarine", "minecraft:sea_lantern")], 4),
    "ep_water_lake": {"type": "minecraft:lake", "config": {"fluid": simple_state("minecraft:water", {"level": "0"}),
                                                           "barrier": simple_state(rid("sky_soil"))}},
    "ep_waterfall": spring("minecraft:water", [rid("floating_stone"), rid("sky_soil"), "minecraft:prismarine"], below=False),
    "ep_lava_fall": spring("minecraft:lava", [rid("ember_rock"), rid("floating_stone"), "minecraft:basalt"]),
    "ep_ember_spire": column([(uni(2, 5), rid("ember_rock")), (1, "minecraft:magma_block"), (1, "minecraft:fire")]),
    "ep_storm_cloud": ore([("minecraft:air", rid("storm_cloud"))], 52),
    "ab_ore_void_crystal": ore([(rid("abyssal_stone"), rid("void_crystal"))], 7),
    "ab_void_crystal_hanging": column([(uni(1, 4), rid("void_crystal"))], "down"),
    "ab_void_crystal_spike": column([(uni(1, 3), rid("void_crystal"))], "up"),
    "ab_floating_crystal": ore([("minecraft:air", rid("void_crystal"))], 6),
    "ab_glow_lichen": {"type": "minecraft:multiface_growth", "config": {
        "block": "minecraft:glow_lichen", "can_be_placed_on": [rid("abyssal_stone"), rid("abyssal_moss")],
        "can_place_on_ceiling": True, "can_place_on_floor": False, "can_place_on_wall": True,
        "chance_of_spreading": 0.5, "search_range": 20}},
    "cs_ore_radiant": ore([(rid("celestial_marble"), rid("radiant_crystal"))], 6),
    "cs_ore_gold": ore([(rid("celestial_marble"), "minecraft:gold_ore")], 9),
    "cs_ore_diamond": ore([(rid("celestial_marble"), "minecraft:diamond_ore")], 5),
    "cs_cloud_puff": ore([("minecraft:air", rid("cloud_block"))], 52),
    "cs_crystal_spire": column([(uni(2, 6), rid("celestial_marble")), (uni(1, 2), rid("radiant_crystal"))]),
    "cs_marble_ruin": {"type": "minecraft:forest_rock", "config": {"state": block(rid("celestial_marble_bricks"))}},
}

# placed feature -> (configured, placement, biomes, step)
PLACED = [
    ("ep_water_lake", rid("ep_water_lake"), [p_rarity(3), p_square(), p_heightmap(), p_biome()], ["suspended_sea"], 1),
    ("ep_amethyst_geode", "minecraft:amethyst_geode", [p_rarity(5), p_square(), p_height(50, 140), p_biome()], ["earthen_peaks"], 2),
    ("ep_ore_magma", rid("ep_ore_magma"), [p_count(10), p_square(), p_height(20, 170), p_biome()], ["ember_isles"], 6),
    ("ep_ore_prismarine", rid("ep_ore_prismarine"), [p_count(6), p_square(), p_height(20, 170), p_biome()], ["suspended_sea"], 6),
    ("ep_ore_sea_lantern", rid("ep_ore_sea_lantern"), [p_count(4), p_square(), p_height(20, 170), p_biome()], ["suspended_sea"], 6),
    ("ep_ore_iron", "minecraft:ore_iron", [p_count(14), p_square(), p_height(20, 170), p_biome()],
     ["ember_isles", "suspended_sea", "earthen_peaks", "storm_skies"], 6),
    ("ep_ore_copper", "minecraft:ore_copper_small", [p_count(10), p_square(), p_height(20, 170), p_biome()],
     ["ember_isles", "suspended_sea", "earthen_peaks", "storm_skies"], 6),
    ("ep_ore_gold", "minecraft:ore_gold", [p_count(6), p_square(), p_height(20, 170), p_biome()],
     ["ember_isles", "earthen_peaks"], 6),
    ("ep_ore_lapis", "minecraft:ore_lapis", [p_count(4), p_square(), p_height(20, 170), p_biome()],
     ["suspended_sea", "storm_skies"], 6),
    ("ep_ore_diamond", "minecraft:ore_diamond_small", [p_count(5), p_square(), p_height(20, 170), p_biome()], ["earthen_peaks"], 6),
    ("ep_ore_emerald", "minecraft:ore_emerald", [p_count(8), p_square(), p_height(20, 170), p_biome()], ["earthen_peaks"], 6),
    ("ep_storm_clouds", rid("ep_storm_cloud"), [p_count(3), p_square(), p_height(150, 230), p_biome()], ["storm_skies"], 7),
    ("ep_waterfall", rid("ep_waterfall"), [p_count(24), p_square(), p_height(40, 170), p_biome()], ["suspended_sea", "storm_skies"], 8),
    ("ep_lava_fall", rid("ep_lava_fall"), [p_count(8), p_square(), p_height(40, 170), p_biome()], ["ember_isles"], 8),
    ("ep_ember_spires", rid("ep_ember_spire"), [p_count(5), p_square(), p_heightmap(), p_below_is(rid("ember_rock")), p_biome()],
     ["ember_isles"], 9),
    ("ep_trees_storm", "minecraft:birch", [p_count(2), p_square(), p_heightmap("OCEAN_FLOOR"), p_survive("minecraft:birch_sapling"),
                                           p_biome()], ["storm_skies"], 9),
    ("ep_trees_sea", "minecraft:fancy_oak", [p_rarity(2), p_square(), p_heightmap("OCEAN_FLOOR"), p_survive("minecraft:oak_sapling"),
                                             p_biome()], ["suspended_sea"], 9),
    ("ep_trees_earthen", "minecraft:azalea_tree", [p_count(2), p_square(), p_heightmap("OCEAN_FLOOR"),
                                                   p_survive("minecraft:oak_sapling"), p_biome()], ["earthen_peaks"], 9),
    ("ep_boulders", "minecraft:forest_rock", [p_count(2), p_square(), p_heightmap("MOTION_BLOCKING"), p_biome()], ["earthen_peaks"], 9),
    ("ep_patch_grass", "minecraft:patch_grass", [p_count(6), p_square(), p_heightmap(), p_biome()],
     ["suspended_sea", "earthen_peaks", "storm_skies"], 9),
    ("ep_flowers", "minecraft:flower_default", [p_count(2), p_square(), p_heightmap(), p_biome()], ["suspended_sea", "storm_skies"], 9),
    ("ep_waterlily", "minecraft:patch_waterlily", [p_count(3), p_square(), p_heightmap(), p_biome()], ["suspended_sea"], 9),

    ("ab_ore_void_crystal", rid("ab_ore_void_crystal"), [p_count(8), p_square(), p_height(10, 120), p_biome()],
     ["abyssal_depths", "void_hollows"], 6),
    ("ab_ore_gold", "minecraft:ore_gold", [p_count(6), p_square(), p_height(10, 120), p_biome()], ["abyssal_depths", "void_hollows"], 6),
    ("ab_ore_redstone", "minecraft:ore_redstone", [p_count(6), p_square(), p_height(10, 120), p_biome()], ["abyssal_depths"], 6),
    ("ab_ore_diamond", "minecraft:ore_diamond_small", [p_count(4), p_square(), p_height(10, 120), p_biome()],
     ["abyssal_depths", "void_hollows"], 6),
    ("ab_void_crystal_hanging", rid("ab_void_crystal_hanging"), [p_count(40), p_square(), p_height(20, 120), p_scan("up", "down"),
                                                                 p_offset(-1), p_biome()], ["abyssal_depths", "void_hollows"], 7),
    ("ab_void_crystal_spikes", rid("ab_void_crystal_spike"), [p_count(30), p_square(), p_height(20, 120), p_scan("down", "up"),
                                                              p_offset(1), p_biome()], ["void_hollows"], 7),
    ("ab_floating_crystals", rid("ab_floating_crystal"), [p_count(6), p_square(), p_height(30, 110), p_biome()], ["void_hollows"], 7),
    ("ab_glow_lichen", rid("ab_glow_lichen"), [p_count(80), p_square(), p_height(10, 120), p_biome()], ["abyssal_depths"], 7),
    ("ab_cave_vines", "minecraft:cave_vine", [p_count(60), p_square(), p_height(20, 120), p_scan("up", "down"), p_offset(-1), p_biome()],
     ["abyssal_depths"], 9),

    ("cs_ore_radiant", rid("cs_ore_radiant"), [p_count(5), p_square(), p_height(60, 220), p_biome()], ["crystal_city", "cloud_gardens"], 6),
    ("cs_ore_gold", rid("cs_ore_gold"), [p_count(10), p_square(), p_height(60, 220), p_biome()], ["crystal_city", "cloud_gardens"], 6),
    ("cs_ore_diamond", rid("cs_ore_diamond"), [p_count(3), p_square(), p_height(60, 220), p_biome()], ["crystal_city"], 6),
    ("cs_cloud_puffs", rid("cs_cloud_puff"), [p_count(4), p_square(), p_height(180, 245), p_biome()], ["crystal_city", "cloud_gardens"], 7),
    ("cs_crystal_spires", rid("cs_crystal_spire"), [p_count(5), p_square(), p_heightmap(), p_below_is(rid("celestial_marble")), p_biome()],
     ["crystal_city"], 9),
    ("cs_marble_ruins", rid("cs_marble_ruin"), [p_count(2), p_square(), p_heightmap("MOTION_BLOCKING"), p_biome()], ["crystal_city"], 9),
    ("cs_cherry_trees", "minecraft:cherry", [p_count(1), p_square(), p_heightmap("OCEAN_FLOOR"), p_survive("minecraft:cherry_sapling"),
                                             p_biome()], ["cloud_gardens"], 9),
    ("cs_patch_grass", "minecraft:patch_grass", [p_count(5), p_square(), p_heightmap(), p_biome()], ["cloud_gardens"], 9),
    ("cs_flowers", "minecraft:flower_cherry", [p_count(3), p_square(), p_heightmap(), p_biome()], ["cloud_gardens"], 9),
]


def gen_features():
    for name, cfg in CONFIGURED.items():
        write_json(os.path.join(D, "worldgen/configured_feature", name + ".json"), cfg)
    for name, feature, placement, _, _ in PLACED:
        write_json(os.path.join(D, "worldgen/placed_feature", name + ".json"), {"feature": feature, "placement": placement})


def features_for(biome):
    """Lista de 11 etapas. A ordem segue a lista PLACED (global), o que evita o erro de "feature order cycle"."""
    steps = [[] for _ in range(11)]
    for name, _, _, biomes, step in PLACED:
        if biome in biomes:
            steps[step].append(rid(name))
    return steps


def spawn(entity, weight, lo, hi):
    return {"type": entity, "weight": weight, "minCount": lo, "maxCount": hi}


def music(sound):
    return {"sound": sound, "min_delay": 12000, "max_delay": 24000, "replace_current_music": False}


def mood():
    return {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}


BIOMES = {
    "ember_isles": dict(temp=2.0, sky=0xFF8A5C, fog=0x7A2E1E, water=0xE07A3A, water_fog=0x5A2010, grass=0x8A6A3B, foliage=0x9A5A2A,
                        particle=("minecraft:ash", 0.04), music="minecraft:music.nether.basalt_deltas",
                        additions=("minecraft:ambient.basalt_deltas.additions", 0.0111),
                        monster=[spawn("minecraft:magma_cube", 20, 1, 3), spawn("minecraft:blaze", 6, 1, 1)]),
    "suspended_sea": dict(temp=0.7, sky=0x6FC8FF, fog=0xA8E2FF, water=0x2FB8FF, water_fog=0x0A4A7A, grass=0x5FD8B0, foliage=0x4FC0A0,
                          particle=("minecraft:falling_water", 0.01), music="minecraft:music.overworld.meadow",
                          water_ambient=[spawn("minecraft:tropical_fish", 15, 2, 5)]),
    "earthen_peaks": dict(temp=0.8, sky=0xD9B27A, fog=0xE6D2A8, water=0x4F9A6A, water_fog=0x1A3A2A, grass=0x7AA548, foliage=0x6A9A3A,
                          particle=("minecraft:spore_blossom_air", 0.008), music="minecraft:music.overworld.jagged_peaks"),
    "storm_skies": dict(temp=0.5, sky=0x7A8AA8, fog=0x9AA6BC, water=0x5A7AA8, water_fog=0x1A2A4A, grass=0x7ACFA0, foliage=0x6AB890,
                        particle=("minecraft:white_ash", 0.03), music="minecraft:music.overworld.stony_peaks",
                        additions=("minecraft:weather.rain.above", 0.004)),
    "abyssal_depths": dict(temp=0.4, sky=0x000000, fog=0x120C24, water=0x1A2A5A, water_fog=0x050A1A,
                           particle=("minecraft:mycelium", 0.03), music="minecraft:music.nether.soul_sand_valley",
                           ambient="minecraft:ambient.soul_sand_valley.loop", nether_mood="minecraft:ambient.soul_sand_valley.mood",
                           monster=[spawn("minecraft:enderman", 6, 1, 2), spawn("minecraft:skeleton", 10, 1, 3),
                                    spawn("minecraft:spider", 8, 1, 2)]),
    "void_hollows": dict(temp=0.4, sky=0x000000, fog=0x1E0C30, water=0x3A1A6A, water_fog=0x0A0518,
                         particle=("minecraft:reverse_portal", 0.008), music="minecraft:music.nether.warped_forest",
                         ambient="minecraft:ambient.warped_forest.loop", nether_mood="minecraft:ambient.warped_forest.mood",
                         monster=[spawn("minecraft:enderman", 20, 2, 4)]),
    "crystal_city": dict(temp=0.7, sky=0xBFD8FF, fog=0xF4ECFF, water=0x9FE8FF, water_fog=0x3A6A8A, grass=0xA8F0C8, foliage=0x98E8C0,
                         particle=("minecraft:end_rod", 0.003), music="minecraft:music.overworld.cherry_grove"),
    "cloud_gardens": dict(temp=0.7, sky=0xA8D8FF, fog=0xFFF8E8, water=0x7FD8FF, water_fog=0x2A5A7A, grass=0x9AF0A0, foliage=0xF0B8E0,
                          particle=("minecraft:cherry_leaves", 0.01), music="minecraft:music.overworld.meadow"),
    "twilight_rift": dict(temp=0.5, sky=0x9A4AA8, fog=0x3A1A4A, water=0x6A2A8A, water_fog=0x1A0A2A,
                          particle=("minecraft:witch", 0.002), music="minecraft:music.dragon"),
}


def gen_biomes():
    for name, b in BIOMES.items():
        effects = {"sky_color": b["sky"], "fog_color": b["fog"], "water_color": b["water"], "water_fog_color": b["water_fog"],
                   "mood_sound": mood(), "music": music(b["music"])}
        if "grass" in b:
            effects["grass_color"] = b["grass"]
            effects["foliage_color"] = b["foliage"]
        if "particle" in b:
            effects["particle"] = {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]}
        if "additions" in b:
            effects["additions_sound"] = {"sound": b["additions"][0], "tick_chance": b["additions"][1]}
        if "ambient" in b:
            effects["ambient_sound"] = b["ambient"]
            effects["mood_sound"] = {"sound": b["nether_mood"], "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
        spawners = {k: [] for k in ("monster", "creature", "ambient", "axolotls", "underground_water_creature",
                                    "water_creature", "water_ambient", "misc")}
        spawners["monster"] = b.get("monster", [])
        spawners["water_ambient"] = b.get("water_ambient", [])
        write_json(os.path.join(D, "worldgen/biome", name + ".json"), {
            "has_precipitation": False, "temperature": b["temp"], "downfall": 0.4 if name == "suspended_sea" else 0.0,
            "effects": effects, "spawners": spawners, "spawn_costs": {}, "carvers": {},
            "features": features_for(name)})


# =================================================================================================
# Estruturas
# =================================================================================================
STRUCTURE_BIOMES = {
    "guardian_temple_fire": ["ember_isles"],
    "guardian_temple_water": ["suspended_sea"],
    "guardian_temple_earth": ["earthen_peaks"],
    "guardian_temple_wind": ["storm_skies"],
    "abyss_throne": ["abyssal_depths", "void_hollows"],
    "celestial_cathedral": ["crystal_city", "cloud_gardens"],
    "twilight_arena": ["twilight_rift"],
}
# id -> (altura absoluta do inicio, espacamento, separacao, salt); twilight_arena nao tem set (e colocada na 1a entrada)
STRUCTURE_PLACEMENT = {
    "guardian_temple_fire": (85, 18, 6, 718293501),
    "guardian_temple_water": (85, 18, 6, 718293502),
    "guardian_temple_earth": (85, 18, 6, 718293503),
    "guardian_temple_wind": (85, 18, 6, 718293504),
    "abyss_throne": (31, 16, 6, 718293505),
    "celestial_cathedral": (126, 16, 6, 718293506),
    "twilight_arena": (52, None, None, None),
}
BOSS_ENTITY = {"guardian_temple_fire": "ignis", "guardian_temple_water": "tide_guardian", "guardian_temple_earth": "primordial_golem",
               "guardian_temple_wind": "zephyr", "abyss_throne": "abyss_sovereign", "celestial_cathedral": "seraph",
               "twilight_arena": "twilight_herald"}


def boss(s, sid, x, y, z):
    s.entity(x, y, z, rid(BOSS_ENTITY[sid]), extra={"PersistenceRequired": Byte(1)})


def dist(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)


def cone(s, cx, cz, top_y, radius, depth, fill, top=None, seed=0, power=1.5, under=None):
    """Base de ilha flutuante: cone invertido irregular abaixo de top_y."""
    rnd = random.Random(seed)
    jitter = {}
    for y in range(top_y, top_y - depth - 1, -1):
        if y < 0:
            break
        t = (top_y - y) / max(1, depth)
        r = radius * (1 - t ** power)
        for x in range(s.size[0]):
            for z in range(s.size[2]):
                key = (x, z)
                if key not in jitter:
                    jitter[key] = rnd.uniform(-1.2, 0.6)
                if dist(x, z, cx, cz) <= r + jitter[key] * (0.3 + t):
                    st = fill
                    if top is not None and y == top_y:
                        st = top
                    elif under is not None and y <= top_y - depth + 2:
                        st = under
                    s.set(x, y, z, st)


def disk(s, cx, cz, y, r, state, rmin=-1.0):
    for x in range(s.size[0]):
        for z in range(s.size[2]):
            d = dist(x, z, cx, cz)
            if rmin < d <= r:
                s.set(x, y, z, state)


def air_dome(s, cx, cz, y0, y1, r):
    for y in range(y0, y1 + 1):
        for x in range(s.size[0]):
            for z in range(s.size[2]):
                if dist(x, z, cx, cz) <= r and s.get(x, y, z) is None:
                    s.set(x, y, z, "minecraft:air")


def ring_points(cx, cz, r, n, offset_deg=0.0):
    pts = []
    for k in range(n):
        a = math.radians(offset_deg + k * 360.0 / n)
        pts.append((int(round(cx + math.cos(a) * r)), int(round(cz + math.sin(a) * r)), a))
    return pts


TEMPLE = {
    "fire": dict(base=rid("ember_rock"), base_top="minecraft:blackstone", under="minecraft:basalt[axis=y]",
                 floor="minecraft:polished_blackstone_bricks", floor2="minecraft:cracked_polished_blackstone_bricks",
                 rim="minecraft:nether_bricks", ring="minecraft:magma_block", wall="minecraft:nether_brick_wall[up=true]",
                 pillar="minecraft:polished_basalt[axis=y]", pbase="minecraft:polished_blackstone", cap="minecraft:magma_block",
                 light="minecraft:fire", dais="minecraft:chiseled_polished_blackstone", dais_top="minecraft:gilded_blackstone"),
    "water": dict(base=rid("floating_stone"), base_top="minecraft:prismarine", under="minecraft:prismarine",
                  floor="minecraft:prismarine_bricks", floor2="minecraft:prismarine", rim="minecraft:dark_prismarine",
                  ring="minecraft:water", wall="minecraft:prismarine_wall[up=true]",
                  pillar="minecraft:prismarine", pbase="minecraft:dark_prismarine", cap="minecraft:dark_prismarine",
                  light="minecraft:sea_lantern", dais="minecraft:dark_prismarine", dais_top="minecraft:sea_lantern"),
    "earth": dict(base=rid("floating_stone"), base_top="minecraft:moss_block", under="minecraft:tuff",
                  floor="minecraft:mossy_stone_bricks", floor2="minecraft:stone_bricks", rim="minecraft:chiseled_stone_bricks",
                  ring="minecraft:moss_block", wall="minecraft:mossy_stone_brick_wall[up=true]",
                  pillar="minecraft:stone_bricks", pbase="minecraft:mossy_cobblestone", cap="minecraft:calcite",
                  light="minecraft:amethyst_cluster[facing=up]", dais="minecraft:polished_andesite", dais_top="minecraft:amethyst_block"),
    "wind": dict(base=rid("floating_stone"), base_top=rid("sky_grass"), under=rid("storm_cloud"),
                 floor="minecraft:quartz_bricks", floor2="minecraft:smooth_quartz", rim="minecraft:smooth_quartz",
                 ring="minecraft:light_blue_stained_glass", wall="minecraft:diorite_wall[up=true]",
                 pillar="minecraft:quartz_pillar[axis=y]", pbase="minecraft:chiseled_quartz_block", cap=rid("storm_cloud"),
                 light="minecraft:end_rod[facing=up]", dais="minecraft:smooth_quartz", dais_top="minecraft:diamond_block"),
}


def guardian_temple(el):
    sid = f"guardian_temple_{el}"
    P = TEMPLE[el]
    S = Structure(31, 26, 31)
    c, FY = 15, 10
    rnd = random.Random({"fire": 401, "water": 402, "earth": 403, "wind": 404}[el])
    cone(S, c, c, FY - 1, 14.5, 9, P["base"], top=P["base_top"], seed=len(el) * 31, under=P["under"])
    # Chao do templo
    for x in range(31):
        for z in range(31):
            d = dist(x, z, c, c)
            if d > 14.5:
                continue
            st = P["floor"] if rnd.random() > 0.2 else P["floor2"]
            if d > 13.5:
                st = P["rim"]
            elif 7.5 < d <= 8.5:
                st = P["ring"]
            S.set(x, FY, z, st)
            if P["ring"] == "minecraft:water" and 7.5 < d <= 8.5:
                S.set(x, FY - 1, z, P["dais"])
    # Mureta com entrada ao sul
    for x in range(31):
        for z in range(31):
            d = dist(x, z, c, c)
            if 13.5 < d <= 14.5 and not (z > c and abs(x - c) <= 2):
                S.set(x, FY + 1, z, P["wall"])
    # Patamar de entrada ao sul (a plataforma vai ate a borda do template)
    for z in range(27, 31):
        for x in range(c - 2, c + 3):
            S.set(x, FY, z, P["rim"])
            S.set(x, FY - 1, z, P["base"])
    # Pilares com luzes
    for (px, pz, a) in ring_points(c, c, 11, 8, 22.5):
        S.set(px, FY + 1, pz, P["pbase"])
        for y in range(FY + 2, FY + 7):
            S.set(px, y, pz, P["pillar"])
        S.set(px, FY + 7, pz, P["cap"])
        if el == "wind":
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                S.set(px + dx, FY + 7, pz + dz, rid("storm_cloud"))
            S.set(px, FY + 8, pz, rid("storm_cloud"))
        elif el == "fire":
            S.set(px, FY + 8, pz, "minecraft:fire")
        else:
            S.set(px, FY + 8, pz, P["light"])
    # Estrado central em dois degraus
    disk(S, c, c, FY + 1, 3.5, P["dais"])
    disk(S, c, c, FY + 2, 2.2, P["dais"])
    S.set(c, FY + 2, c, P["dais_top"])
    # Detalhes de cada elemento
    if el == "fire":
        for (px, pz, _) in ring_points(c, c, 5.5, 4, 45):
            S.set(px, FY, pz, "minecraft:magma_block")
        # Veios de magma espalhados pela base da ilha
        for k in range(40):
            x, z, y = rnd.randrange(2, 29), rnd.randrange(2, 29), rnd.randrange(1, FY - 1)
            if S.get(x, y, z) is not None:
                S.set(x, y, z, "minecraft:magma_block")
    elif el == "water":
        for (px, pz, _) in ring_points(c, c, 5.5, 4, 45):
            S.set(px, FY, pz, "minecraft:sea_lantern")
    elif el == "earth":
        for k in range(24):
            x, z = rnd.randrange(3, 28), rnd.randrange(3, 28)
            if dist(x, z, c, c) < 13 and dist(x, z, c, c) > 4 and S.get(x, FY + 1, z) is None:
                S.set(x, FY + 1, z, "minecraft:moss_carpet")
        for (px, pz, _) in ring_points(c, c, 5.5, 4, 45):
            S.set(px, FY + 1, pz, "minecraft:amethyst_cluster[facing=up]")
    else:
        for k in range(10):
            x, z, y = rnd.randrange(0, 31), rnd.randrange(0, 31), rnd.randrange(FY + 3, FY + 12)
            if dist(x, z, c, c) > 15:
                for dx in range(-1, 2):
                    for dz in range(-1, 1):
                        S.set(x + dx, y, z + dz, rid("storm_cloud"))
    # Tesouro atras do estrado (norte) e o Guardiao no centro
    S.chest(c - 2, FY + 1, c - 6, rid(f"chests/{sid}"), facing="south")
    S.chest(c + 2, FY + 1, c - 6, rid(f"chests/{sid}"), facing="south")
    boss(S, sid, c + 0.5, FY + 3, c + 0.5)
    air_dome(S, c, c, FY + 1, 25, 14.5)
    return sid, S


def abyss_throne():
    sid = "abyss_throne"
    S = Structure(33, 22, 41)
    bricks_ = rid("abyssal_stone_bricks")
    S.hollow_box(1, 0, 1, 31, 20, 39, bricks_)
    rnd = random.Random(5)
    # Chao com faixas de obsidiana e cruzamentos de obsidiana chorona
    for x in range(2, 31):
        for z in range(2, 39):
            st = bricks_
            if x in (12, 20) or z % 6 == 0:
                st = "minecraft:obsidian"
            if x in (12, 20) and z % 6 == 0:
                st = "minecraft:crying_obsidian"
            S.set(x, 1, z, st)
    for z in range(10, 38):
        for x in range(15, 18):
            S.set(x, 2, z, "minecraft:purple_carpet")
    # Pilares com faixa de cristal e correntes com lanternas das almas
    for x in (7, 25):
        for z in range(8, 38, 6):
            for y in range(2, 20):
                S.set(x, y, z, "minecraft:polished_blackstone" if y not in (9, 10) else rid("void_crystal"))
            S.set(x, 19, z, rid("void_crystal"))
    for z in range(12, 38, 8):
        for y in (17, 18, 19):
            S.set(16, y, z, "minecraft:chain[axis=y]")
        S.set(16, 16, z, "minecraft:soul_lantern[hanging=true]")
    # Arandelas de cristal nas paredes
    for z in range(4, 38, 5):
        S.set(1, 7, z, rid("void_crystal"))
        S.set(31, 7, z, rid("void_crystal"))
    # Estrado do trono (3 degraus) ao norte
    for (y, z1, x0, x1) in ((2, 9, 10, 22), (3, 7, 12, 20), (4, 5, 14, 18)):
        for x in range(x0, x1 + 1):
            for z in range(2, z1 + 1):
                S.set(x, y, z, bricks_)
    # Trono
    S.set(16, 5, 3, "minecraft:obsidian")
    for y in range(5, 10):
        S.set(16, y, 2, "minecraft:crying_obsidian")
    S.set(15, 6, 2, "minecraft:crying_obsidian")
    S.set(17, 6, 2, "minecraft:crying_obsidian")
    S.set(15, 5, 3, "minecraft:polished_blackstone")
    S.set(17, 5, 3, "minecraft:polished_blackstone")
    S.set(16, 10, 2, rid("void_crystal"))
    for x in (14, 18):
        S.set(x, 5, 4, rid("void_crystal"))
        S.set(x, 6, 4, rid("void_crystal"))
    S.chest(11, 3, 5, rid(f"chests/{sid}"), facing="south")
    S.chest(21, 3, 5, rid(f"chests/{sid}"), facing="south")
    boss(S, sid, 16.5, 3.0, 8.5)
    # Entradas: portal grande ao sul (com tunel ate a borda) e duas laterais
    for x in range(13, 20):
        for y in range(2, 9):
            for z in (39, 40):
                S.set(x, y, z, "minecraft:air")
    for z in range(19, 22):
        for y in range(2, 6):
            for x in (0, 1, 31, 32):
                S.set(x, y, z, "minecraft:air")
    for x in range(12, 21):
        S.set(x, 9, 39, rid("void_crystal") if x in (12, 20) else bricks_)
    return sid, S


def celestial_cathedral():
    sid = "celestial_cathedral"
    S = Structure(29, 36, 45)
    marble, mbricks = rid("celestial_marble"), rid("celestial_marble_bricks")
    cx, FY = 14, 8
    # Base de nuvem (elipse)
    rnd = random.Random(9)
    for y in range(FY - 1, -1, -1):
        t = (FY - 1 - y) / (FY - 1)
        rx, rz = 15 * (1 - t ** 1.4), 22 * (1 - t ** 1.4)
        for x in range(29):
            for z in range(45):
                if ((x - cx) / max(rx, 0.5)) ** 2 + ((z - 22) / max(rz, 0.5)) ** 2 <= 1.0 + rnd.uniform(-0.15, 0.05):
                    S.set(x, y, z, rid("cloud_block") if y < FY - 2 else marble)
    # Piso (retangulo da nave + elipse do adro)
    for x in range(1, 28):
        for z in range(0, 44):
            if (2 <= x <= 26 and z <= 42) or ((x - cx) / 14.5) ** 2 + ((z - 22) / 22.5) ** 2 <= 1.0:
                S.set(x, FY, z, mbricks)
    for z in range(8, 40, 6):
        S.set(cx, FY, z, rid("radiant_crystal"))
    for z in range(4, 8):
        for x in range(cx - 1, cx + 2):
            S.set(x, FY, z, "minecraft:gold_block" if x == cx else mbricks)
    # Paredes com janelas
    for z in range(6, 41):
        for y in range(FY + 1, FY + 13):
            for x in (4, 24):
                window = (z % 4 in (1, 2)) and FY + 4 <= y <= FY + 9 and 8 <= z <= 38
                S.set(x, y, z, ("minecraft:yellow_stained_glass" if y > FY + 7 else "minecraft:white_stained_glass") if window else marble)
    for x in range(4, 25):
        for y in range(FY + 1, FY + 13):
            S.set(x, y, 40, marble if not (12 <= x <= 16 and FY + 5 <= y <= FY + 10) else "minecraft:yellow_stained_glass")
            door = 12 <= x <= 16 and y <= FY + 7
            S.set(x, y, 6, "minecraft:air" if door else marble)
    # Interior vazio (corta nuvens/terreno que sobrepoem)
    for x in range(5, 24):
        for z in range(7, 40):
            for y in range(FY + 1, FY + 13):
                if S.get(x, y, z) is None:
                    S.set(x, y, z, "minecraft:air")
    # Colunas da nave
    for x in (8, 20):
        for z in range(10, 38, 6):
            for y in range(FY + 1, FY + 12):
                S.set(x, y, z, "minecraft:quartz_pillar[axis=y]")
            S.set(x, FY + 12, z, "minecraft:gold_block")
            S.set(x, FY + 11, z, rid("radiant_crystal"))
    # Telhado escalonado com cumeeira dourada e claraboia
    for step, (y, x0, x1) in enumerate(((FY + 13, 3, 25), (FY + 14, 5, 23), (FY + 15, 7, 21), (FY + 16, 9, 19), (FY + 17, 11, 17))):
        for x in range(x0, x1 + 1):
            for z in range(5, 42):
                S.set(x, y, z, mbricks)
    for z in range(5, 42):
        S.set(cx, FY + 18, z, "minecraft:gold_block" if z % 3 else "minecraft:yellow_stained_glass")
        S.set(cx - 1, FY + 17, z, "minecraft:yellow_stained_glass" if z % 4 == 0 else mbricks)
        S.set(cx + 1, FY + 17, z, "minecraft:yellow_stained_glass" if z % 4 == 0 else mbricks)
    # Torre sineira na fachada (sul) com o cristal radiante no topo
    for y in range(FY + 1, FY + 24):
        for x in range(cx - 2, cx + 3):
            for z in range(1, 6):
                edge = x in (cx - 2, cx + 2) or z in (1, 5)
                if edge:
                    S.set(x, y, z, marble if not (y % 5 == 0 and x == cx) else "minecraft:white_stained_glass")
                else:
                    S.set(x, y, z, "minecraft:air")
    for x in range(cx - 2, cx + 3):
        for z in range(1, 6):
            S.set(x, FY + 24, z, mbricks)
    for i, (y, r) in enumerate(((FY + 25, 2), (FY + 26, 1), (FY + 27, 0))):
        for x in range(cx - r, cx + r + 1):
            for z in range(3 - r, 3 + r + 1):
                S.set(x, y, z, "minecraft:gold_block" if r == 0 else mbricks)
    S.set(cx, FY + 28, 3, rid("radiant_crystal"))
    S.set(cx, FY + 23, 3, "minecraft:bell[attachment=ceiling,facing=south]")
    # Passagem pela base da torre ate a porta da nave
    for z in range(1, 6):
        for x in range(cx - 1, cx + 2):
            for y in range(FY + 1, FY + 4):
                S.set(x, y, z, "minecraft:air")
    # Altar ao norte (dois degraus) e o Serafim
    for (y, z0, x0, x1) in ((FY + 1, 33, 8, 20), (FY + 2, 35, 10, 18)):
        for x in range(x0, x1 + 1):
            for z in range(z0, 40):
                S.set(x, y, z, mbricks)
    S.set(cx, FY + 3, 37, "minecraft:gold_block")
    S.set(cx, FY + 4, 37, rid("radiant_crystal"))
    S.chest(10, FY + 3, 37, rid(f"chests/{sid}"), facing="east")
    S.chest(18, FY + 3, 37, rid(f"chests/{sid}"), facing="west")
    boss(S, sid, cx + 0.5, FY + 2.0, 33.5)
    return sid, S


# Arena da Fenda (v1.0, bosses_e): GRANDE e firme para o Dragao Primordial com 6 jogadores. Raio 43,5 (a BossArena do
# nucleo sela ate 48), muralha de ameias de 2-3 blocos na borda (ninguem cai andando; o resgate do vazio cuida do
# resto), piso de blocos do Minecraft (o terreno temporario de cada fase do dragao so repinta blocos vanilla numa
# area protegida), dois aneis de pilares de COBERTURA (linha de visao: Olhar da Gorgona e Sifao), ponte ao sul com a
# Fenda de saida. O centro (pes do Arauto) continua em (46, FY, 46) -> DimKind.TWILIGHT (tx, ty, tz) = (46, 18, 46).
ARENA_R = 43.5


def twilight_arena():
    sid = "twilight_arena"
    S = Structure(93, 33, 104)
    c, FY = 46, 18
    R = ARENA_R
    tw = rid("twilight_stone")
    rnd = random.Random(7711)
    cone(S, c, c, FY - 1, R + 0.5, 17, tw, seed=77, power=1.35, under="minecraft:obsidian")
    light_floor = ("minecraft:smooth_quartz", "minecraft:quartz_bricks", "minecraft:calcite")
    dark_floor = ("minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks", "minecraft:deepslate_tiles")
    for x in range(93):
        for z in range(93):
            d = dist(x, z, c, c)
            if d > R:
                continue
            light = x < c
            k = rnd.random()
            st = (light_floor if light else dark_floor)[0 if k < 0.55 else 1 if k < 0.85 else 2]
            if x == c:
                st = tw
            if d > R - 1.5:
                st = tw
            elif 9.5 < d <= 10.5:
                st = "minecraft:gold_block" if light else "minecraft:crying_obsidian"
            elif 25.5 < d <= 26.5:
                st = "minecraft:chiseled_quartz_block" if light else "minecraft:chiseled_polished_blackstone"
            elif d <= 4.5:
                st = tw
            S.set(x, FY, z, st)
            S.set(x, FY - 1, z, tw if d > R - 1.5 else "minecraft:obsidian")
    # Muralha de ameias na borda (2 blocos + ameia alternada), com a entrada ao sul
    for x in range(93):
        for z in range(93):
            d = dist(x, z, c, c)
            if not (R - 1.5 < d <= R):
                continue
            if z > c and abs(x - c) <= 2:
                continue
            S.set(x, FY + 1, z, tw)
            S.set(x, FY + 2, z, tw)
            ang = math.atan2(z - c, x - c)
            if int(round((ang + math.pi) * R / 2.0)) % 2 == 0:
                S.set(x, FY + 3, z, tw)
    for (px, pz, a) in ring_points(c, c, R - 0.8, 24, 7.5):
        S.set(px, FY + 3, pz, "minecraft:end_rod[facing=up]" if math.cos(a) < 0 else rid("void_crystal"))
    # Pontos do "yin-yang" no centro
    S.set(c - 2, FY, c, rid("void_crystal"))
    S.set(c + 2, FY, c, rid("radiant_crystal"))

    def pillar(px, pz, height, light):
        body = rid("celestial_marble") if light else rid("abyssal_stone_bricks")
        cap = rid("radiant_crystal") if light else rid("void_crystal")
        for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            h = height if (dx, dz) == (0, 0) else height - 2
            for y in range(FY + 1, FY + 1 + h):
                S.set(px + dx, y, pz + dz, body)
        S.set(px, FY + 1 + height, pz, cap)

    # Anel interno (8 pilares baixos) e externo (12 pilares altos): cobertura de linha de visao
    for (px, pz, a) in ring_points(c, c, 17, 8, 22.5):
        pillar(px, pz, 6, math.cos(a) < 0)
    for (px, pz, a) in ring_points(c, c, 34, 12, 0.0):
        if pz > c and abs(px - c) <= 4:
            continue
        pillar(px, pz, 10, math.cos(a) < 0)
    # Ponte de entrada ao sul e a Fenda de saida
    bridge0, bridge1 = c + 42, 103
    for z in range(bridge0, bridge1):
        for x in range(c - 2, c + 3):
            S.set(x, FY, z, tw)
            S.set(x, FY - 1, z, "minecraft:obsidian")
        if z > c + 43:
            for x in (c - 3, c + 3):
                S.set(x, FY + 1, z, tw)
                if z % 2 == 0:
                    S.set(x, FY + 2, z, "minecraft:end_rod[facing=up]")
    for x in range(c - 3, c + 4):
        S.set(x, FY, bridge1 - 1, tw)
        S.set(x, FY + 1, bridge1 - 1, tw)
    rift_z = bridge1 - 3
    for x in (c - 2, c + 2):
        for y in range(FY + 1, FY + 5):
            S.set(x, y, rift_z, tw)
        S.set(x, FY + 5, rift_z, rid("radiant_crystal") if x < c else rid("void_crystal"))
    S.set(c, FY + 1, rift_z, rid("twilight_rift"))
    # Tesouro ao norte e o Arauto no centro
    S.chest(c - 3, FY + 1, c - 38, rid(f"chests/{sid}"), facing="south")
    S.chest(c + 3, FY + 1, c - 38, rid(f"chests/{sid}"), facing="south")
    boss(S, sid, c + 0.5, FY + 1.0, c + 0.5)
    # Limpa o que houver acima do piso (terreno da dimensao, pilares da arena antiga da v0.8)
    air_dome(S, c, c, FY + 1, FY + 14, R + 0.5)
    return sid, S


def elemental_hub():
    S = Structure(21, 22, 21)
    c, FY = 10, 10
    cone(S, c, c, FY - 1, 9.5, 9, rid("floating_stone"), seed=201)
    quad = {(1, -1): rid("ember_rock"), (1, 1): "minecraft:prismarine_bricks", (-1, 1): "minecraft:moss_block", (-1, -1): "minecraft:calcite"}
    for x in range(21):
        for z in range(21):
            d = dist(x, z, c, c)
            if d > 9.5:
                continue
            st = rid("floating_stone_bricks")
            if d > 8.5:
                st = rid("floating_stone")
            elif 6.5 < d <= 7.5:
                st = quad[(1 if x >= c else -1, 1 if z >= c else -1)]
            elif 2.5 < d <= 3.5:
                st = "minecraft:amethyst_block"
            S.set(x, FY, z, st)
    S.set(c, FY + 1, c, rid("convergence_altar"))
    lights = {(1, -1): "minecraft:shroomlight", (1, 1): "minecraft:sea_lantern",
              (-1, 1): "minecraft:ochre_froglight[axis=y]", (-1, -1): "minecraft:verdant_froglight[axis=y]"}
    for (sx, sz), light in lights.items():
        x, z = c + sx * 5, c + sz * 5
        S.set(x, FY + 1, z, rid("floating_stone_bricks"))
        S.set(x, FY + 2, z, rid("floating_stone_bricks"))
        S.set(x, FY + 3, z, light)
    for (px, pz, _) in ring_points(c, c, 9, 12, 15):
        S.set(px, FY + 1, pz, "minecraft:end_rod[facing=up]")
    # Portal de volta (moldura 4x5 acesa) ao norte
    frame, portal = rid("elemental_portal_frame"), rid("elemental_portal[axis=x]")
    z = 2
    for x in range(8, 12):
        S.set(x, FY + 1, z, frame)
        S.set(x, FY + 5, z, frame)
    for y in range(FY + 2, FY + 5):
        S.set(8, y, z, frame)
        S.set(11, y, z, frame)
        S.set(9, y, z, portal)
        S.set(10, y, z, portal)
    air_dome(S, c, c, FY + 1, 21, 10.5)
    return S


def abyss_hub():
    S = Structure(19, 13, 19)
    br = rid("abyssal_stone_bricks")
    S.fill(0, 0, 0, 18, 0, 18, rid("abyssal_stone"))
    S.hollow_box(0, 1, 0, 18, 12, 18, br)
    for x in range(1, 18):
        for z in range(1, 18):
            d = dist(x, z, 9, 9)
            st = br
            if 2.5 < d <= 3.5:
                st = "minecraft:obsidian"
            elif d <= 1.0:
                st = rid("void_crystal")
            S.set(x, 1, z, st)
    for (x, z) in ((1, 1), (17, 1), (1, 17), (17, 17)):
        S.set(x, 2, z, rid("void_crystal"))
        S.set(x, 3, z, rid("void_crystal"))
    for (x, z) in ((5, 14), (13, 14), (5, 6), (13, 6)):
        S.set(x, 2, z, "minecraft:polished_blackstone_wall[up=true]")
        S.set(x, 3, z, "minecraft:soul_lantern[hanging=false]")
    # Tuneis (leste, oeste, sul) para alcancar as cavernas
    for i in range(8, 11):
        for y in range(2, 5):
            S.set(18, y, i, "minecraft:air")
            S.set(0, y, i, "minecraft:air")
            S.set(i, y, 18, "minecraft:air")
    frame, portal = rid("abyss_portal_frame"), rid("abyss_portal[axis=x]")
    z = 3
    for x in range(8, 12):
        S.set(x, 2, z, frame)
        S.set(x, 6, z, frame)
    for y in range(3, 6):
        S.set(8, y, z, frame)
        S.set(11, y, z, frame)
        S.set(9, y, z, portal)
        S.set(10, y, z, portal)
    return S


def celestial_hub():
    S = Structure(21, 16, 21)
    c, FY = 10, 6
    cone(S, c, c, FY - 1, 10.0, 5, rid("cloud_block"), seed=301, power=1.3)
    for x in range(21):
        for z in range(21):
            d = dist(x, z, c, c)
            if d > 9.5:
                continue
            st = rid("celestial_marble_bricks")
            if d > 8.5:
                st = rid("cloud_block")
            elif 4.6 < d <= 5.4:
                st = "minecraft:gold_block"
            elif d <= 1.5:
                st = rid("radiant_crystal")
            S.set(x, FY, z, st)
    for (px, pz, _) in ring_points(c, c, 7.5, 8, 22.5):
        for y in range(FY + 1, FY + 5):
            S.set(px, y, pz, rid("celestial_marble"))
        S.set(px, FY + 5, pz, rid("radiant_crystal"))
    rnd = random.Random(302)
    for _ in range(14):
        a = rnd.uniform(0, math.tau)
        x, z = int(round(c + math.cos(a) * 9)), int(round(c + math.sin(a) * 9))
        S.set(x, FY + 1, z, rid("cloud_block"))
    # Farol de volta (aceso) com a coluna de luz
    S.set(c, FY, 5, "minecraft:gold_block")
    S.set(c, FY + 1, 5, rid("celestial_beacon[active=true]"))
    for y in range(FY + 2, FY + 6):
        S.set(c, y, 5, rid("celestial_portal"))
    air_dome(S, c, c, FY + 1, 15, 10.5)
    return S


def structure_json(sid):
    start, _, _, _ = STRUCTURE_PLACEMENT[sid]
    return {"type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/{sid}", "step": "surface_structures",
            "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": []}},
            "terrain_adaptation": "none", "start_pool": rid(f"{sid}/start"), "size": 1,
            "start_height": {"absolute": start}, "max_distance_from_center": 80, "use_expansion_hack": False}


def gen_twilight_arena():
    """So a arena da Fenda (bosses_e/v1.0):  py -I tools/gen/dimensions.py --only arena"""
    sid, s = twilight_arena()
    s.save(os.path.join(D, "structure", sid + ".nbt"), keep_air=True)


def gen_structures():
    built = [guardian_temple(e) for e in ("fire", "water", "earth", "wind")]
    built += [abyss_throne(), celestial_cathedral(), twilight_arena()]
    for sid, s in built:
        s.save(os.path.join(D, "structure", sid + ".nbt"), keep_air=True)
        write_json(os.path.join(D, "worldgen/structure", sid + ".json"), structure_json(sid))
        write_json(os.path.join(D, "worldgen/template_pool", sid, "start.json"), {
            "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": rid(sid),
                "processors": "minecraft:empty", "projection": "rigid"}}]})
        _, spacing, separation, salt = STRUCTURE_PLACEMENT[sid]
        if spacing:
            write_json(os.path.join(D, "worldgen/structure_set", sid + ".json"), {
                "structures": [{"structure": rid(sid), "weight": 1}],
                "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
    elemental_hub().save(os.path.join(D, "structure/elemental_plane_hub.nbt"), keep_air=True)
    abyss_hub().save(os.path.join(D, "structure/abyss_hub.nbt"), keep_air=True)
    celestial_hub().save(os.path.join(D, "structure/celestial_hub.nbt"), keep_air=True)


# ---------------- Baus
def entry(item, weight, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": item, "weight": weight}
    funcs = []
    if hi > 1:
        funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        funcs += extra
    if funcs:
        e["functions"] = funcs
    return e


def scroll(glyph, weight):
    return entry(rid("glyph_scroll"), weight, extra=[{"function": "minecraft:set_components",
                                                      "components": {rid("glyph"): rid(glyph)}}])


def book(weight):
    return entry("minecraft:book", weight, extra=[{"function": "minecraft:enchant_randomly"}])


def chest(name, main, glyphs, rolls=(3, 6)):
    pools = [
        {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0, "entries": main},
        {"rolls": 1, "bonus_rolls": 0.0, "entries": [scroll(g, w) for g, w in glyphs]},
        {"rolls": {"type": "minecraft:uniform", "min": 0, "max": 1}, "bonus_rolls": 0.0, "entries": [book(1)]},
    ]
    write_json(os.path.join(D, "loot_table/chests", name + ".json"),
               {"type": "minecraft:chest", "pools": pools, "random_sequence": rid("chests/" + name)})


def gen_chests():
    common = [entry(rid("mana_crystal"), 15, 1, 3), entry(rid("essence_arcane"), 10, 1, 3), entry("minecraft:gold_ingot", 10, 2, 6),
              entry("minecraft:diamond", 4, 1, 2)]
    extra = {
        "fire": [entry("minecraft:blaze_rod", 8, 1, 3), entry("minecraft:magma_cream", 6, 1, 4)],
        "water": [entry("minecraft:prismarine_crystals", 8, 2, 5), entry("minecraft:heart_of_the_sea", 1)],
        "earth": [entry("minecraft:amethyst_shard", 8, 2, 6), entry("minecraft:emerald", 6, 1, 3)],
        "wind": [entry("minecraft:phantom_membrane", 6, 1, 2), entry("minecraft:feather", 8, 2, 6)],
    }
    glyphs = {
        "fire": [("combustion", 2), ("overheat", 2), ("blue_flame", 1), ("overload", 1)],
        "water": [("drown", 2), ("local_rain", 2), ("water_mirror", 1), ("echo", 1)],
        "earth": [("tremor", 2), ("weight", 2), ("fortress", 1), ("chain", 1)],
        "wind": [("tornado", 2), ("vacuum", 2), ("sonar", 1), ("homing", 1)],
    }
    for el in ("fire", "water", "earth", "wind"):
        chest(f"guardian_temple_{el}", [entry(rid(f"essence_{el}"), 20, 2, 5)] + common + extra[el], glyphs[el])
    chest("abyss_throne", [entry(rid("essence_shadow"), 20, 2, 6), entry(rid("void_crystal"), 12, 2, 6),
                           entry("minecraft:crying_obsidian", 8, 2, 5), entry("minecraft:ender_pearl", 10, 1, 4),
                           entry("minecraft:netherite_scrap", 2, 1, 2)] + common,
          [("devour", 2), ("pain_link", 2), ("nightmare", 2), ("steal_blessing", 1), ("vampiric", 1)])
    chest("celestial_cathedral", [entry(rid("essence_light"), 20, 2, 6), entry(rid("radiant_crystal"), 12, 2, 6),
                                  entry("minecraft:gold_block", 6, 1, 2), entry("minecraft:golden_apple", 6, 1, 2),
                                  entry("minecraft:enchanted_golden_apple", 1)] + common,
          [("sacred_barrier", 2), ("exorcise", 2), ("prism", 2), ("sanctuary", 1), ("critical", 1)])
    chest("twilight_arena", [entry(rid("essence_light"), 12, 3, 8), entry(rid("essence_shadow"), 12, 3, 8),
                             entry("minecraft:netherite_ingot", 4, 1, 2), entry("minecraft:nether_star", 2),
                             entry("minecraft:totem_of_undying", 3), entry(rid("mana_crystal"), 15, 4, 10),
                             entry("minecraft:diamond_block", 4, 1, 2)],
          [("overload", 2), ("echo", 2), ("chain", 2), ("persistent", 1), ("split", 1)], rolls=(5, 8))


# =================================================================================================
# Traducoes
# =================================================================================================
def gen_lang():
    pt = {}
    en = {}

    def t(key, p, e):
        pt[key] = p
        en[key] = e

    blocks = {
        "floating_stone": ("Pedra Flutuante", "Floating Stone"),
        "floating_stone_bricks": ("Tijolos de Pedra Flutuante", "Floating Stone Bricks"),
        "ember_rock": ("Rocha de Brasa", "Ember Rock"),
        "sky_grass": ("Grama do Céu", "Sky Grass"),
        "sky_soil": ("Terra do Céu", "Sky Soil"),
        "storm_cloud": ("Nuvem de Tempestade", "Storm Cloud"),
        "abyssal_stone": ("Pedra Abissal", "Abyssal Stone"),
        "abyssal_stone_bricks": ("Tijolos de Pedra Abissal", "Abyssal Stone Bricks"),
        "void_crystal": ("Cristal do Vazio", "Void Crystal"),
        "abyssal_moss": ("Musgo Abissal", "Abyssal Moss"),
        "celestial_marble": ("Mármore Celestial", "Celestial Marble"),
        "celestial_marble_bricks": ("Tijolos de Mármore Celestial", "Celestial Marble Bricks"),
        "cloud_block": ("Nuvem Sólida", "Solid Cloud"),
        "radiant_crystal": ("Cristal Radiante", "Radiant Crystal"),
        "twilight_stone": ("Pedra do Crepúsculo", "Twilight Stone"),
        "elemental_portal_frame": ("Moldura de Portal Elemental", "Elemental Portal Frame"),
        "elemental_portal": ("Portal Elemental", "Elemental Portal"),
        "abyss_portal_frame": ("Moldura de Portal Sombrio", "Shadow Portal Frame"),
        "abyss_portal": ("Portal Sombrio", "Shadow Portal"),
        "celestial_beacon": ("Farol Celestial", "Celestial Beacon"),
        "celestial_portal": ("Escada de Luz", "Stairway of Light"),
        "twilight_rift": ("Fenda do Crepúsculo", "Twilight Rift"),
        "convergence_altar": ("Altar da Convergência", "Convergence Altar"),
    }
    for k, (p, e) in blocks.items():
        t(f"block.{NS}.{k}", p, e)
    biomes = {
        "ember_isles": ("Ilhas de Brasa", "Ember Isles"),
        "suspended_sea": ("Mar Suspenso", "Suspended Sea"),
        "earthen_peaks": ("Picos Terrosos", "Earthen Peaks"),
        "storm_skies": ("Céus da Tempestade", "Storm Skies"),
        "abyssal_depths": ("Profundezas Abissais", "Abyssal Depths"),
        "void_hollows": ("Ocos do Vazio", "Void Hollows"),
        "crystal_city": ("Cidade de Cristal", "Crystal City"),
        "cloud_gardens": ("Jardins de Nuvem", "Cloud Gardens"),
        "twilight_rift": ("Fenda do Crepúsculo", "Twilight Rift"),
    }
    for k, (p, e) in biomes.items():
        t(f"biome.{NS}.{k}", p, e)
    dims = {
        "elemental_plane": ("Plano Elemental", "Elemental Plane"),
        "abyss": ("Abismo", "Abyss"),
        "celestial_sanctuary": ("Santuário Celestial", "Celestial Sanctuary"),
        "twilight_rift": ("Fenda do Crepúsculo", "Twilight Rift"),
    }
    for k, (p, e) in dims.items():
        t(f"dimension.{NS}.{k}", p, e)
    t(f"cutscene.{NS}.dimensions.portal_open.elemental_plane", "O Portal Elemental se abre", "The Elemental Portal opens")
    t(f"cutscene.{NS}.dimensions.portal_open.abyss", "O Portal Sombrio desperta", "The Shadow Portal awakens")
    t(f"cutscene.{NS}.dimensions.portal_open.celestial_sanctuary", "A Escada de Luz desce do céu", "The Stairway of Light descends")
    t(f"cutscene.{NS}.dimensions.arrive.elemental_plane", "Onde os quatro elementos existem em estado puro",
      "Where the four elements exist in their pure state")
    t(f"cutscene.{NS}.dimensions.arrive.abyss", "A escuridão aqui tem dono", "The darkness here has a master")
    t(f"cutscene.{NS}.dimensions.arrive.celestial_sanctuary", "Cidades de cristal acima das nuvens", "Crystal cities above the clouds")
    t(f"cutscene.{NS}.dimensions.arrive.twilight_rift", "Entre a Luz e a Sombra, tudo termina", "Between Light and Shadow, everything ends")
    t(f"cutscene.{NS}.dimensions.convergence", "Convergência: %s + %s", "Convergence: %s + %s")
    msgs = {
        "need_circle": ("É preciso o %sº Círculo para abrir o caminho para %s.", "You need Circle %s to open the way to %s."),
        "frame_incomplete": ("A moldura está incompleta: monte um retângulo (interior mínimo 2×3).",
                             "The frame is incomplete: build a rectangle (inner space at least 2×3)."),
        "need_mana": ("Mana insuficiente: o portal exige %s de mana.", "Not enough mana: the portal requires %s mana."),
        "beacon_already": ("O Farol Celestial já está aceso.", "The Celestial Beacon is already lit."),
        "beacon_gold": ("O farol precisa estar sobre um bloco de ouro.", "The beacon must stand on a block of gold."),
        "beacon_sky": ("O farol precisa ver o céu aberto.", "The beacon must see the open sky."),
        "beacon_noon": ("A luz só responde ao meio-dia.", "The light only answers at noon."),
        "beacon_blocked": ("Há blocos no caminho do feixe de luz.", "Something is blocking the beam of light."),
        "altar_wrong_place": ("A Convergência só pode ser feita no %s.", "The Convergence can only be performed in the %s."),
        "convergence_failed": ("A Convergência falhou: %s", "The Convergence failed: %s"),
        "protected": ("Uma magia antiga protege este lugar.", "An ancient magic protects this place."),
        "rift_denied": ("A Fenda do Crepúsculo te repele. Ainda falta:", "The Twilight Rift repels you. You still need:"),
        "req_circle": ("Alcançar o %sº Círculo", "Reach Circle %s"),
        "req_boss": ("Derrotar: %s", "Defeat: %s"),
        "herald_returns": ("O Arauto do Crepúsculo retornou à arena...", "The Twilight Herald has returned to the arena..."),
        "arena_expanded": ("A arena da Fenda do Crepúsculo se expandiu: o Dragão Primordial precisa de espaço...",
                           "The Twilight Rift arena has expanded: the Primordial Dragon needs room..."),
        "portal_active": ("Este portal já está aberto.", "This portal is already open."),
        "site_protected": ("Uma magia antiga prende este portal à sua estrutura.",
                           "An ancient magic binds this portal to its structure."),
        "dormant": ("%s — portal adormecido. %s", "%s — dormant portal. %s"),
        "beacon_dormant": ("O Farol dorme. %s — %s", "The Beacon sleeps. %s — %s"),
        "beacon_now": ("é meio-dia agora!", "it is noon now!"),
        "beacon_wait": ("faltam ~%s h para o meio-dia", "~%s h until noon"),
    }
    for k, (p, e) in msgs.items():
        t(f"message.{NS}.dimensions.{k}", p, e)
    # Dicas dos itens (DimBlockItem): como ativar cada portal/farol/fenda/altar
    tips = {
        "leads_to": ("Caminho para: %s", "Leads to: %s"),
        "found_in": ("Encontrado em: %s (Mundo Normal)", "Found at: %s (Overworld)"),
        "route_hint": ("Não se fabrica — a rota principal (J) revela onde fica", "Cannot be crafted — the main route (J) reveals where it is"),
        "frame_shape": ("Também acende numa moldura retangular (interior 2×3 ou maior)",
                        "Also lights in a rectangular frame (inside 2×3 or larger)"),
        "frame_mana": ("Desperte clicando com %s: %sº Círculo e %s de mana", "Awaken it by clicking with %s: Circle %s and %s mana"),
        "frame_offer": ("Desperte oferecendo %s: %sº Círculo", "Awaken it by offering %s: Circle %s"),
        "beacon_place": ("Precisa estar sobre ouro, a céu aberto", "Must stand on gold under the open sky"),
        "beacon_noon": ("Ao meio-dia, clique com %s: %sº Círculo", "At noon, click it with %s: Circle %s"),
        "rift_awaken": ("Atravesse com o %sº Círculo, depois de derrotar %s e %s",
                        "Step through at Circle %s after defeating %s and %s"),
        "rift_req": ("Exige o %sº Círculo e ter derrotado:", "Requires Circle %s and having defeated:"),
        "bullet": (" • %s", " • %s"),
        "altar_use": ("Funde dois elementos em Mestre num novo elemento", "Fuses two Master elements into a new element"),
        "altar_place": ("Só funciona no %s", "Only works in the %s"),
    }
    for k, (p, e) in tips.items():
        t(f"tooltip.{NS}.dimensions.{k}", p, e)
    t(f"boss.{NS}.dimensions.abyss_sovereign", "Soberano do Abismo", "Abyss Sovereign")
    t(f"boss.{NS}.dimensions.seraph", "Serafim", "Seraph")
    t(f"screen.{NS}.convergence.title", "Ritual de Convergência", "Convergence Ritual")
    t(f"screen.{NS}.convergence.hint", "Escolha dois elementos em Mestre", "Choose two elements at Master")
    t(f"screen.{NS}.convergence.choose", "Selecione dois elementos no hexágono. Os marcados com ✖ ainda não chegaram ao nó Mestre.",
      "Select two elements on the hexagon. Those marked with ✖ have not reached the Master node yet.")
    t(f"screen.{NS}.convergence.result", "Fusão resultante:", "Resulting fusion:")
    t(f"screen.{NS}.convergence.perform", "Convergir", "Converge")
    t(f"convergence.{NS}.req.place", "Estar no %s", "Be in the %s")
    t(f"convergence.{NS}.req.new", "%s ainda não despertado", "%s not yet awakened")
    t(f"convergence.{NS}.req.master", "%s no nó Mestre", "%s at the Master node")
    t(f"convergence.{NS}.req.items", "%s× %s (%s/%s)", "%s× %s (%s/%s)")
    # Estruturas do Mundo Normal com o portal adormecido (world_v09) e os hubs no Places (Viagem)
    sites = {
        "elemental_plane": (("Portão Elemental", "Elemental Gate"),
                            ("Ruínas de um círculo de pedra com 4 obeliscos, em campos e florestas.",
                             "Ruins of a stone circle with 4 obelisks, in plains and forests.")),
        "abyss": (("Fenda Abissal", "Abyssal Chasm"),
                  ("Um cânion profundo rasgado na terra; o portal sombrio dorme no fundo.",
                   "A deep canyon torn into the land; the shadow portal sleeps at the bottom.")),
        "celestial_sanctuary": (("Escadaria Celeste", "Celestial Stairway"),
                                ("Um templo no topo das montanhas, com o Farol apagado sobre ouro.",
                                 "A mountaintop temple with the unlit Beacon resting on gold.")),
        "twilight_rift": (("Ruptura do Crepúsculo", "Twilight Rupture"),
                          ("Uma ilha de pedra rachada onde a Fenda rasga o céu.",
                           "A cracked stone island where the Rift tears the sky.")),
    }
    for k, ((np, ne), (wp, we)) in sites.items():
        t(f"site.{NS}.dimensions.{k}", np, ne)
        t(f"site.{NS}.dimensions.where.{k}", wp, we)
    hubs = {
        "elemental_plane": ("Plano Elemental — Altar da Convergência", "Elemental Plane — Convergence Altar"),
        "abyss": ("Abismo — Salão das Sombras", "Abyss — Hall of Shadows"),
        "celestial_sanctuary": ("Santuário Celestial — Jardim das Nuvens", "Celestial Sanctuary — Cloud Garden"),
        "twilight_rift": ("Fenda do Crepúsculo — Arena", "Twilight Rift — Arena"),
    }
    for k, (p, e) in hubs.items():
        t(f"place.{NS}.dimensions.hub.{k}", p, e)
    cmds = {
        "header": ("✦ Dimensões e portais ✦", "✦ Dimensions and portals ✦"),
        "line": ("%s — %sº Círculo — %s visita(s)", "%s — Circle %s — %s visit(s)"),
        "site_found": ("  ✔ Portal: %s em %s %s", "  ✔ Portal: %s at %s %s"),
        "coords": ("%s %s %s", "%s %s %s"),
        "distance": ("(a %s blocos)", "(%s blocks away)"),
        "other_world": ("(em outra dimensão)", "(in another dimension)"),
        "tp_hover": ("Clique para sugerir o /tp", "Click to suggest /tp"),
        "site_more": ("    +%s outro(s) já visitado(s)", "    +%s more already visited"),
        "site_seen": ("  ✔ Portal: %s já visitado", "  ✔ Portal: %s already visited"),
        "site_unknown": ("  ? Portal: %s — ainda não encontrado. %s", "  ? Portal: %s — not found yet. %s"),
        "path_opened": ("  ✦ Você já abriu este caminho", "  ✦ You have already opened this path"),
        "awaken": ("  Despertar: %s", "  Awaken: %s"),
        "missing_req": ("    ✖ %s", "    ✖ %s"),
        "route_hint": ("A rota principal (J) revela onde fica cada portal — siga os capítulos do Diário.",
                       "The main route (J) reveals where each portal is — follow the Journal chapters."),
        "unknown": ("Dimensão desconhecida.", "Unknown dimension."),
        "missing": ("A dimensão %s não está carregada no servidor.", "The %s dimension is not loaded on this server."),
        "not_inside": ("Você não está numa dimensão do Isekai Mahou.", "You are not in an Isekai Mahou dimension."),
        "rebuilt": ("Ponto de chegada de %s reconstruído.", "%s arrival point rebuilt."),
        "rebuild_failed": ("Não foi possível reconstruir o ponto de chegada de %s.", "Could not rebuild the %s arrival point."),
    }
    for k, (p, e) in cmds.items():
        t(f"command.{NS}.dimensions.{k}", p, e)
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))


PARTS = {
    "textures": [lambda: gen_textures()],
    "models": [lambda: gen_models()],
    "loot": [lambda: gen_block_loot()],
    "recipes": [lambda: gen_recipes()],
    "tags": [lambda: gen_tags()],
    "worldgen": [lambda: gen_noises(), lambda: gen_dimension_types(), lambda: gen_dimensions(),
                 lambda: gen_noise_settings(), lambda: gen_features(), lambda: gen_biomes()],
    "structures": [lambda: gen_structures()],
    "arena": [lambda: gen_twilight_arena()],
    "chests": [lambda: gen_chests()],
    "lang": [lambda: gen_lang()],
}


def main():
    only = None
    if "--only" in sys.argv:
        i = sys.argv.index("--only")
        only = [x.strip() for x in sys.argv[i + 1].split(",") if x.strip()] if i + 1 < len(sys.argv) else []
        unknown = [x for x in only if x not in PARTS]
        if unknown:
            raise SystemExit("partes desconhecidas: " + ", ".join(unknown) + " (use: " + ", ".join(PARTS) + ")")
    for name, steps in PARTS.items():
        if only is None or name in only:
            for step in steps:
                step()
    print("dimensions: " + ("assets e dados gerados" if only is None else "gerado: " + ", ".join(only)))


if __name__ == "__main__":
    main()
