"""
Gera os assets do modulo fusions_a:
  - blocos temporarios: blockstates, modelos e texturas (molten_pool usa a lava animada do vanilla)
  - icones 18x18 dos efeitos de status
  - Ent invocado: modelo GeckoLib (.geo.json), animacoes e textura 128x128 (+ _glowmask) e modelo do ovo
Uso (na raiz do projeto):  py -I tools/gen/fusions_a.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..")
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
CLEAR = (0, 0, 0, 0)


def write_json(rel, data):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


# =====================================================================================
#  Blocos
# =====================================================================================
def blockstate(bid):
    write_json(f"blockstates/{bid}.json", {"variants": {"": {"model": f"isekai_mahou:block/{bid}"}}})


def cube_model(bid, texture, render_type=None):
    m = {"parent": "minecraft:block/cube_all", "textures": {"all": texture}}
    if render_type:
        m["render_type"] = render_type
    write_json(f"models/block/{bid}.json", m)


def molten_pool_model():
    tex = "minecraft:block/lava_still"
    face = lambda uv, cull=None: dict({"uv": uv, "texture": "#lava"}, **({"cullface": cull} if cull else {}))
    write_json("models/block/molten_pool.json", {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": tex, "lava": tex},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 2, 16],
            "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False},
            "faces": {
                "up": face([0, 0, 16, 16]),
                "down": face([0, 0, 16, 16], "down"),
                "north": face([0, 14, 16, 16]),
                "south": face([0, 14, 16, 16]),
                "east": face([0, 14, 16, 16]),
                "west": face([0, 14, 16, 16]),
            },
        }],
    })


def tex_magma_prison():
    rnd = random.Random(11)
    c = Canvas(16, 16)
    base = [hex_rgba("#120B1E"), hex_rgba("#1B1030"), hex_rgba("#251742"), hex_rgba("#0C0814")]
    for y in range(16):
        for x in range(16):
            c.set(x, y, base[rnd.randrange(4)] if rnd.random() < 0.55 else base[1])
    # manchas roxas de obsidiana
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        c.set(x, y, hex_rgba("#3B2466"))
        c.set((x + 1) % 16, y, hex_rgba("#2E1B52"))
    # veios de magma (passeio aleatorio)
    for start in ((1, 3), (9, 0), (4, 12), (14, 8)):
        x, y = start
        for _ in range(9):
            core = rnd.random() < 0.4
            c.set(x, y, hex_rgba("#FFC23A") if core else hex_rgba("#FF6A00"))
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = c.get(x + ox, y + oy)
                if p[0] < 60 and rnd.random() < 0.35:
                    c.set(x + ox, y + oy, hex_rgba("#7A2A0A"))
            dx, dy = rnd.choice(((1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)))
            x, y = (x + dx) % 16, (y + dy) % 16
    c.save(os.path.join(A, "textures/block/magma_prison.png"))


def tex_frost_ice():
    rnd = random.Random(21)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                c.set(x, y, (225, 245, 255, 215))
            else:
                t = (x + y) / 30.0
                col = mix((170, 220, 255, 120), (140, 200, 250, 140), t)
                c.set(x, y, col)
    # riscos diagonais de brilho
    for k in (3, 4, 9, 10):
        for i in range(16):
            x, y = k + i, i
            if 0 < x < 15 and 0 < y < 15 and rnd.random() < 0.8:
                c.set(x, y, (235, 250, 255, 170))
    # cristais de geada nos cantos
    for (cx, cy) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        for ox, oy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            x, y = cx + ox, cy + oy
            if 0 <= x < 16 and 0 <= y < 16:
                c.set(x, y, (255, 255, 255, 235))
    for _ in range(8):
        c.set(1 + rnd.randrange(14), 1 + rnd.randrange(14), (255, 255, 255, 200))
    c.save(os.path.join(A, "textures/block/frost_ice.png"))


def tex_slick_ice():
    rnd = random.Random(31)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            col = mix(hex_rgba("#7DB2F5"), hex_rgba("#5C93E6"), ((x * 7 + y * 3) % 16) / 16.0)
            c.set(x, y, col)
    c.noise(0.06, 3)
    for k in (2, 8, 13):
        for i in range(16):
            x, y = (k + i) % 16, i
            if rnd.random() < 0.85:
                c.set(x, y, hex_rgba("#B9D8FF"))
    # rachaduras
    x, y = 3, 15
    for _ in range(12):
        c.set(x, y, hex_rgba("#3E6FC4"))
        x, y = x + rnd.choice((0, 1)), y - 1
    for (x, y) in ((11, 3), (12, 3), (11, 4), (5, 9), (13, 12)):
        c.set(x, y, hex_rgba("#FFFFFF"))
    c.save(os.path.join(A, "textures/block/slick_ice.png"))


def tex_sinking_sand():
    rnd = random.Random(41)
    c = Canvas(16, 16)
    pal = [hex_rgba("#DBC892"), hex_rgba("#D2BC82"), hex_rgba("#E6D6A4"), hex_rgba("#C9B074")]
    for y in range(16):
        for x in range(16):
            c.set(x, y, pal[rnd.randrange(4)])
    # redemoinho escuro (areia sendo sugada)
    for i in range(70):
        a = i * 0.32
        r = 0.6 + i * 0.1
        x, y = int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))
        if 0 <= x < 16 and 0 <= y < 16:
            c.set(x, y, hex_rgba("#A88A52") if i % 3 else hex_rgba("#8E7140"))
    c.set(7, 7, hex_rgba("#6E5530"))
    c.set(8, 8, hex_rgba("#6E5530"))
    c.set(8, 7, hex_rgba("#7E6338"))
    c.save(os.path.join(A, "textures/block/sinking_sand.png"))


def tex_glass_prison():
    rnd = random.Random(51)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, (255, 226, 150, 60))
    frame = hex_rgba("#D9B25E")
    dark = hex_rgba("#A9833A")
    for i in range(16):
        c.set(i, 0, frame); c.set(i, 15, dark); c.set(0, i, frame); c.set(15, i, dark)
    # bolhas de ar presas no vidro derretido
    for _ in range(5):
        x, y = 2 + rnd.randrange(12), 2 + rnd.randrange(12)
        c.set(x, y, (255, 250, 225, 150))
    for i in range(5):
        c.set(3 + i, 3 + i, (255, 248, 220, 140))
        c.set(4 + i, 3 + i, (255, 248, 220, 100))
    c.set(11, 10, (255, 255, 240, 170)); c.set(12, 11, (255, 255, 240, 170))
    c.save(os.path.join(A, "textures/block/glass_prison.png"))


def blocks():
    for bid in ("molten_pool", "magma_prison", "frost_ice", "slick_ice", "sinking_sand", "glass_prison"):
        blockstate(bid)
    molten_pool_model()
    cube_model("magma_prison", "isekai_mahou:block/magma_prison")
    cube_model("frost_ice", "isekai_mahou:block/frost_ice", "minecraft:translucent")
    cube_model("slick_ice", "isekai_mahou:block/slick_ice")
    cube_model("sinking_sand", "isekai_mahou:block/sinking_sand")
    cube_model("glass_prison", "isekai_mahou:block/glass_prison", "minecraft:translucent")
    tex_magma_prison()
    tex_frost_ice()
    tex_slick_ice()
    tex_sinking_sand()
    tex_glass_prison()


# =====================================================================================
#  Icones de efeito (18x18)
# =====================================================================================
CHEST = [
    "..................",
    "..................",
    "...###......###...",
    "..#####....#####..",
    "..##############..",
    "..##############..",
    "...############...",
    "....##########....",
    "....##########....",
    "....##########....",
    "....##########....",
    "....##########....",
    "....##########....",
    ".....########.....",
    "..................",
]


def icon_chest(name, base, dark, light, accent, seed):
    rnd = random.Random(seed)
    c = Canvas(18, 18)
    c.from_rows(CHEST, {"#": base}, 0, 1)
    for y in range(18):
        for x in range(18):
            p = c.get(x, y)
            if p[3] == 0:
                continue
            if x <= 8 and y < 8:
                c.set(x, y, light if rnd.random() < 0.5 else base)
            elif y > 11 or x > 12:
                c.set(x, y, dark)
    # detalhes (veios/cristais)
    for _ in range(7):
        x, y = 5 + rnd.randrange(8), 5 + rnd.randrange(8)
        if c.get(x, y)[3]:
            c.set(x, y, accent)
            if c.get(x + 1, y)[3]:
                c.set(x + 1, y, accent)
    c.outline(darken(dark, 0.5))
    c.save(os.path.join(A, f"textures/mob_effect/{name}.png"))


def icon_overcharge():
    rows = [
        "...........####...",
        "..........####....",
        ".........####.....",
        "........####......",
        ".......####.......",
        "......####........",
        ".....#########....",
        "......#######.....",
        "........####......",
        ".......####.......",
        "......####........",
        ".....###..........",
        "....###...........",
        "...##.............",
        "..#...............",
    ]
    c = Canvas(18, 18)
    c.from_rows(rows, {"#": hex_rgba("#FFE45A")}, 0, 1)
    for y in range(18):
        for x in range(18):
            p = c.get(x, y)
            if p[3] and (x + y) % 5 == 0:
                c.set(x, y, hex_rgba("#FFFFFF"))
    c.outline(hex_rgba("#2A8FD8"))
    c.save(os.path.join(A, "textures/mob_effect/overcharge.png"))


def icon_solar_crown():
    c = Canvas(18, 18)
    gold, dgold, lgold = hex_rgba("#FFC83A"), hex_rgba("#C98A12"), hex_rgba("#FFF0A0")
    # raios
    for a in range(8):
        ang = a * math.pi / 4
        for r in range(6, 9):
            c.set(int(round(9 + math.cos(ang) * r)) - 0, int(round(7 + math.sin(ang) * r * 0.8)), hex_rgba("#FF9A1F"))
    # coroa
    c.rect(4, 9, 13, 13, gold)
    for x in (4, 8, 9, 13):
        c.rect(x, 6, x, 8, gold)
    c.set(6, 8, gold); c.set(11, 8, gold)
    c.rect(4, 13, 13, 13, dgold)
    c.set(8, 11, hex_rgba("#FF4A2A")); c.set(9, 11, hex_rgba("#FF4A2A"))
    c.set(5, 11, hex_rgba("#3AC8FF")); c.set(12, 11, hex_rgba("#3AC8FF"))
    for x in range(4, 14):
        c.set(x, 9, lgold)
    c.outline(hex_rgba("#5A3A08"))
    c.save(os.path.join(A, "textures/mob_effect/solar_crown.png"))


def icon_photosynthesis():
    c = Canvas(18, 18)
    leaf, dleaf, vein = hex_rgba("#58C84A"), hex_rgba("#2F8A2A"), hex_rgba("#A8F07A")
    for y in range(3, 18):
        for x in range(0, 15):
            # folha em forma de gota inclinada
            u, v = x - 6.5, y - 10.5
            ru = u * 0.8 + v * 0.6
            rv = -u * 0.6 + v * 0.8
            if (ru / 4.0) ** 2 + (rv / 6.6) ** 2 <= 1.0:
                c.set(x, y, leaf if ru < 0.8 else dleaf)
    for i in range(11):
        c.set(2 + i // 2 + i % 2, 16 - i, vein)
    c.outline(hex_rgba("#1A4A16"))
    # sol por cima, no canto, com raios
    sun = hex_rgba("#FFE45A")
    for a in range(8):
        ang = a * math.pi / 4
        c.set(int(round(13.5 + math.cos(ang) * 4.4 - 0.5)), int(round(4.5 + math.sin(ang) * 4.4 - 0.5)), hex_rgba("#FFB300"))
    c.circle(13.5, 4.5, 3.1, sun)
    c.set(12, 3, hex_rgba("#FFFFFF"))
    c.save(os.path.join(A, "textures/mob_effect/photosynthesis.png"))


def icon_bubble():
    c = Canvas(18, 18)
    c.circle(9, 9, 7.5, (150, 220, 255, 150))
    c.circle(9, 9, 6.3, (190, 236, 255, 90))
    c.shade_sphere(9, 9, 7.5, 0.5, 0.3)
    for (x, y) in ((5, 5), (6, 4), (5, 6), (6, 5)):
        c.set(x, y, (255, 255, 255, 240))
    c.set(12, 13, (255, 255, 255, 200))
    c.outline((60, 140, 200, 255))
    c.save(os.path.join(A, "textures/mob_effect/protective_bubble.png"))


def icon_holy_flame():
    rows = [
        "........#.........",
        ".......##.........",
        ".......###........",
        "......####..#.....",
        "......#####.##....",
        ".....#######.#....",
        "....#########.....",
        "....##########....",
        "...###########....",
        "...############...",
        "...############...",
        "...############...",
        "....##########....",
        ".....########.....",
        "......######......",
    ]
    c = Canvas(18, 18)
    c.from_rows(rows, {"#": hex_rgba("#FFB300")}, 0, 2)
    for y in range(18):
        for x in range(18):
            if c.get(x, y)[3]:
                d = math.hypot(x - 8.5, (y - 12) * 1.2)
                if d < 2.6:
                    c.set(x, y, hex_rgba("#FFFFFF"))
                elif d < 4.2:
                    c.set(x, y, hex_rgba("#FFF3A0"))
    c.outline(hex_rgba("#8A4A00"))
    c.save(os.path.join(A, "textures/mob_effect/holy_flame.png"))


def icon_ice_statue():
    c = Canvas(18, 18)
    ice, dice, lice = (170, 225, 255, 255), (110, 175, 230, 255), (230, 248, 255, 255)
    c.rect(3, 2, 14, 16, ice)
    for i in range(12):
        c.set(3 + i, 2, lice)
        c.set(3, 2 + i, lice)
    c.rect(14, 2, 14, 16, dice)
    c.rect(3, 16, 14, 16, dice)
    # silhueta presa dentro
    fig = hex_rgba("#4A6A8A")
    c.rect(8, 5, 9, 6, fig)
    c.rect(7, 7, 10, 11, fig)
    c.rect(6, 7, 6, 10, fig); c.rect(11, 7, 11, 10, fig)
    c.rect(7, 12, 7, 14, fig); c.rect(10, 12, 10, 14, fig)
    c.set(5, 4, (255, 255, 255, 255)); c.set(12, 13, (255, 255, 255, 255))
    c.outline(hex_rgba("#2A4A6A"))
    c.save(os.path.join(A, "textures/mob_effect/ice_statue.png"))


def icon_spore_sleep():
    c = Canvas(18, 18)
    cap, dcap, spot, stem = hex_rgba("#9C6BD8"), hex_rgba("#6B45A8"), hex_rgba("#E8D8FF"), hex_rgba("#EDE3CF")
    for y in range(7, 13):
        for x in range(1, 12):
            if ((x - 6) / 5.5) ** 2 + ((y - 12) / 5.0) ** 2 <= 1.0:
                c.set(x, y, cap if y < 11 else dcap)
    c.set(4, 9, spot); c.set(7, 8, spot); c.set(8, 10, spot)
    c.rect(5, 12, 7, 16, stem)
    # "z z"
    z = hex_rgba("#FFFFFF")
    for (ox, oy, s) in ((11, 1, 4), (14, 6, 3)):
        for i in range(s):
            c.set(ox + i, oy, z); c.set(ox + i, oy + s - 1, z)
            c.set(ox + s - 1 - i, oy + i, z)
    c.outline(hex_rgba("#2A1A40"))
    c.save(os.path.join(A, "textures/mob_effect/spore_sleep.png"))


def icon_bewildered():
    c = Canvas(18, 18)
    sand, light = hex_rgba("#E3C77A"), hex_rgba("#FFF3C4")
    for i in range(80):
        a = i * 0.28
        r = 0.4 + i * 0.09
        x, y = int(round(8.5 + math.cos(a) * r)), int(round(8.5 + math.sin(a) * r))
        c.set(x, y, light if i % 4 == 0 else sand)
    c.set(8, 8, hex_rgba("#7A5A20")); c.set(9, 9, hex_rgba("#7A5A20"))
    c.outline(hex_rgba("#6A4A18"))
    c.save(os.path.join(A, "textures/mob_effect/bewildered.png"))


def icons():
    icon_chest("magma_armor", hex_rgba("#3A2418"), hex_rgba("#1E120C"), hex_rgba("#5A3A28"), hex_rgba("#FF7A1A"), 5)
    icon_chest("ice_armor", hex_rgba("#8FD0FF"), hex_rgba("#5A9AD8"), hex_rgba("#D8F2FF"), hex_rgba("#FFFFFF"), 6)
    icon_overcharge()
    icon_solar_crown()
    icon_photosynthesis()
    icon_bubble()
    icon_holy_flame()
    icon_ice_statue()
    icon_spore_sleep()
    icon_bewildered()


# =====================================================================================
#  Ent invocado (GeckoLib)
# =====================================================================================
TW, TH = 128, 128


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(c, rect, col):
    x, y, w, h = rect
    if w > 0 and h > 0:
        c.rect(x, y, x + w - 1, y + h - 1, col)


def bark(c, rect, base, seed, knots=True):
    """Casca: veios verticais escuros/claros e nós."""
    rnd = random.Random(seed)
    x0, y0, w, h = rect
    dark, light = darken(base, 0.35), lighten(base, 0.18)
    for x in range(x0, x0 + w):
        col_mode = rnd.random()
        for y in range(y0, y0 + h):
            if col_mode < 0.18:
                c.set(x, y, dark if rnd.random() < 0.85 else base)
            elif col_mode < 0.32:
                c.set(x, y, light if rnd.random() < 0.7 else base)
            else:
                c.set(x, y, base if rnd.random() < 0.8 else (dark if rnd.random() < 0.5 else light))
    if knots and w >= 4 and h >= 4:
        for _ in range(max(1, (w * h) // 60)):
            kx, ky = x0 + 1 + rnd.randrange(w - 2), y0 + 1 + rnd.randrange(h - 2)
            c.set(kx, ky, darken(base, 0.55))
            c.set(kx + 1, ky, darken(base, 0.45))
            c.set(kx, ky - 1, light)


def leaves(c, rect, seed, flowers=True):
    rnd = random.Random(seed)
    x0, y0, w, h = rect
    pal = [hex_rgba("#3F8F2A"), hex_rgba("#4FA83A"), hex_rgba("#2E6E22"), hex_rgba("#62BF48"), hex_rgba("#245A1C")]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c.set(x, y, pal[rnd.randrange(len(pal))] if rnd.random() < 0.7 else pal[0])
    if flowers:
        for _ in range(max(1, (w * h) // 40)):
            fx, fy = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
            c.set(fx, fy, hex_rgba("#FF9AC8") if rnd.random() < 0.6 else hex_rgba("#FFF07A"))


def paint_bark_box(c, u, v, w, h, d, base, seed):
    f = faces(u, v, w, h, d)
    for k, r in f.items():
        if k in ("top", "bottom"):
            # anéis do tronco nas pontas
            fill(c, r, darken(base, 0.1))
            x, y, ww, hh = r
            cx, cy = x + ww / 2.0, y + hh / 2.0
            for yy in range(y, y + hh):
                for xx in range(x, x + ww):
                    dd = math.hypot((xx + 0.5 - cx) / max(1, ww / 2), (yy + 0.5 - cy) / max(1, hh / 2))
                    if int(dd * 4) % 2 == 0:
                        c.set(xx, yy, lighten(hex_rgba("#B88A5A"), 0.05))
                    else:
                        c.set(xx, yy, hex_rgba("#8A6238"))
        else:
            bark(c, r, base, seed * 31 + sum(map(ord, k)))
    return f


def paint_leaf_box(c, u, v, w, h, d, seed):
    f = faces(u, v, w, h, d)
    for k, r in f.items():
        leaves(c, r, seed + len(k))
    return f


def ent_texture():
    c = Canvas(TW, TH)
    glow = Canvas(TW, TH)
    trunk = hex_rgba("#6B4A2E")
    darkbark = hex_rgba("#4E3420")
    # tronco inferior 12x10x8 @ (0,0)
    paint_bark_box(c, 0, 0, 12, 10, 8, trunk, 1)
    # peito 16x12x10 @ (0,18)
    f = paint_bark_box(c, 0, 18, 16, 12, 10, trunk, 2)
    # veios de seiva brilhando no peito
    fx, fy, fw, fh = f["front"]
    sap = hex_rgba("#9CFF6A")
    pts = [(fx + 8, fy + 2), (fx + 7, fy + 3), (fx + 7, fy + 4), (fx + 6, fy + 5), (fx + 6, fy + 6), (fx + 5, fy + 7),
           (fx + 8, fy + 5), (fx + 9, fy + 6), (fx + 10, fy + 7), (fx + 10, fy + 8)]
    for (x, y) in pts:
        c.set(x, y, sap)
        glow.set(x, y, sap)
    # musgo pendurado no peito
    rnd = random.Random(9)
    for x in range(fx, fx + fw):
        for y in range(fy, fy + 2 + rnd.randrange(2)):
            c.set(x, y, hex_rgba("#4FA83A") if rnd.random() < 0.6 else hex_rgba("#3F8F2A"))
    # cabeça 10x9x9 @ (52,0)
    f = paint_bark_box(c, 52, 0, 10, 9, 9, hex_rgba("#5E4128"), 3)
    hx, hy, hw, hh = f["front"]
    # rosto: cavidades dos olhos, olhos brilhantes, boca rachada
    for (x, y) in ((hx + 2, hy + 3), (hx + 3, hy + 3), (hx + 6, hy + 3), (hx + 7, hy + 3)):
        c.set(x, y - 1, hex_rgba("#2A1A0E"))
        c.set(x, y, hex_rgba("#D8FF5A"))
        glow.set(x, y, hex_rgba("#D8FF5A"))
    for (x, y) in ((hx + 2, hy + 4), (hx + 7, hy + 4)):
        c.set(x, y, hex_rgba("#2A1A0E"))
    for x in range(hx + 3, hx + 7):
        c.set(x, hy + 6, hex_rgba("#21140A"))
    c.set(hx + 2, hy + 7, hex_rgba("#21140A"))
    c.set(hx + 7, hy + 7, hex_rgba("#21140A"))
    # sobrancelha 11x2x2 @ (90,0)
    paint_bark_box(c, 90, 0, 11, 2, 2, darkbark, 4)
    # nariz 2x3x2 @ (116,0)
    paint_bark_box(c, 116, 0, 2, 3, 2, darkbark, 5)
    # barba de musgo 8x5x1 @ (90,4)
    leaves(c, (90, 4, 18, 6), 6, flowers=False)
    # copa 18x7x15 @ (0,40) e topo 12x4x10 @ (66,40)
    paint_leaf_box(c, 0, 40, 18, 7, 15, 7)
    paint_leaf_box(c, 66, 40, 12, 4, 10, 8)
    # folhagem dos ombros 6x5x6 @ (52,18)
    paint_leaf_box(c, 52, 18, 6, 5, 6, 10)
    # braço 5x17x5 @ (0,62), punho 7x6x7 @ (20,62)
    paint_bark_box(c, 0, 62, 5, 17, 5, trunk, 11)
    paint_bark_box(c, 20, 62, 7, 6, 7, darkbark, 12)
    # perna 6x14x6 @ (48,62), raiz-pé 8x2x3 @ (72,62)
    paint_bark_box(c, 48, 62, 6, 14, 6, trunk, 13)
    paint_bark_box(c, 72, 62, 8, 2, 3, darkbark, 14)
    c.save(os.path.join(A, "textures/entity/summoned_ent.png"))
    glow.save(os.path.join(A, "textures/entity/summoned_ent_glowmask.png"))


def cube(origin, size, uv, inflate=None):
    d = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        d["inflate"] = inflate
    return d


def ent_geo():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 14, 0], "cubes": [
            cube([-6, 14, -4], [12, 10, 8], [0, 0]),
            cube([-8, 24, -5], [16, 12, 10], [0, 18]),
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 36, -1], "cubes": [
            cube([-5, 36, -6], [10, 9, 9], [52, 0]),
            cube([-5.5, 42, -7], [11, 2, 2], [90, 0]),
            cube([-1, 38, -8], [2, 3, 2], [116, 0]),
            cube([-4, 32, -7], [8, 5, 1], [90, 4]),
        ]},
        {"name": "canopy", "parent": "head", "pivot": [0, 45, 0], "cubes": [
            cube([-9, 44, -8], [18, 7, 15], [0, 40]),
            cube([-6, 51, -5], [12, 4, 10], [66, 40]),
        ]},
        {"name": "left_shoulder", "parent": "body", "pivot": [8, 34, 0], "cubes": [
            cube([7, 34, -3], [6, 5, 6], [52, 18]),
        ]},
        {"name": "right_shoulder", "parent": "body", "pivot": [-8, 34, 0], "cubes": [
            cube([-13, 34, -3], [6, 5, 6], [52, 18]),
        ]},
        {"name": "right_arm", "parent": "body", "pivot": [-10.5, 33, 0], "cubes": [
            cube([-13, 18, -2.5], [5, 17, 5], [0, 62]),
            cube([-14, 12, -3.5], [7, 6, 7], [20, 62]),
        ]},
        {"name": "left_arm", "parent": "body", "pivot": [10.5, 33, 0], "cubes": [
            cube([8, 18, -2.5], [5, 17, 5], [0, 62]),
            cube([7, 12, -3.5], [7, 6, 7], [20, 62]),
        ]},
        {"name": "right_leg", "parent": "root", "pivot": [-4, 14, 0], "cubes": [
            cube([-7, 0, -3], [6, 14, 6], [48, 62]),
            cube([-8, 0, -6], [8, 2, 3], [72, 62]),
        ]},
        {"name": "left_leg", "parent": "root", "pivot": [4, 14, 0], "cubes": [
            cube([1, 0, -3], [6, 14, 6], [48, 62]),
            cube([0, 0, -6], [8, 2, 3], [72, 62]),
        ]},
    ]
    write_json("geo/entity/summoned_ent.geo.json", {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.summoned_ent",
                "texture_width": TW, "texture_height": TH,
                "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 2, 0],
            },
            "bones": bones,
        }],
    })


def kf(pairs):
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t != int(t) else f"{t:.1f}": v for t, v in pairs}


def ent_animations():
    anims = {
        "animation.summoned_ent.idle": {
            "loop": True, "animation_length": 4.0,
            "bones": {
                "body": {"rotation": kf([(0.0, [0, 0, 0]), (2.0, [2, 0, 0]), (4.0, [0, 0, 0])]),
                         "position": kf([(0.0, [0, 0, 0]), (2.0, [0, -0.5, 0]), (4.0, [0, 0, 0])])},
                "canopy": {"rotation": kf([(0.0, [0, 0, -2]), (2.0, [1, 0, 2]), (4.0, [0, 0, -2])])},
                "right_arm": {"rotation": kf([(0.0, [0, 0, 3]), (2.0, [-4, 0, 5]), (4.0, [0, 0, 3])])},
                "left_arm": {"rotation": kf([(0.0, [0, 0, -3]), (2.0, [-4, 0, -5]), (4.0, [0, 0, -3])])},
                "left_shoulder": {"rotation": kf([(0.0, [0, 0, 0]), (2.0, [0, 0, 4]), (4.0, [0, 0, 0])])},
                "right_shoulder": {"rotation": kf([(0.0, [0, 0, 0]), (2.0, [0, 0, -4]), (4.0, [0, 0, 0])])},
            },
        },
        "animation.summoned_ent.walk": {
            "loop": True, "animation_length": 1.6,
            "bones": {
                "right_leg": {"rotation": kf([(0.0, [22, 0, 0]), (0.8, [-22, 0, 0]), (1.6, [22, 0, 0])])},
                "left_leg": {"rotation": kf([(0.0, [-22, 0, 0]), (0.8, [22, 0, 0]), (1.6, [-22, 0, 0])])},
                "right_arm": {"rotation": kf([(0.0, [-18, 0, 4]), (0.8, [18, 0, 4]), (1.6, [-18, 0, 4])])},
                "left_arm": {"rotation": kf([(0.0, [18, 0, -4]), (0.8, [-18, 0, -4]), (1.6, [18, 0, -4])])},
                "body": {"rotation": kf([(0.0, [3, 0, -3]), (0.4, [3, 0, 0]), (0.8, [3, 0, 3]), (1.2, [3, 0, 0]), (1.6, [3, 0, -3])]),
                         "position": kf([(0.0, [0, 0, 0]), (0.4, [0, 1, 0]), (0.8, [0, 0, 0]), (1.2, [0, 1, 0]), (1.6, [0, 0, 0])])},
                "canopy": {"rotation": kf([(0.0, [0, 0, 3]), (0.8, [0, 0, -3]), (1.6, [0, 0, 3])])},
            },
        },
        "animation.summoned_ent.attack": {
            "loop": False, "animation_length": 0.8,
            "bones": {
                "right_arm": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-165, 0, -10]), (0.45, [-20, 0, 0]), (0.8, [0, 0, 0])])},
                "left_arm": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-165, 0, 10]), (0.45, [-20, 0, 0]), (0.8, [0, 0, 0])])},
                "body": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.45, [14, 0, 0]), (0.8, [0, 0, 0])])},
                "head": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-8, 0, 0]), (0.45, [6, 0, 0]), (0.8, [0, 0, 0])])},
            },
        },
        "animation.summoned_ent.stomp": {
            "loop": False, "animation_length": 1.0,
            "bones": {
                "body": {"position": kf([(0.0, [0, 0, 0]), (0.4, [0, 3, 0]), (0.6, [0, -1.5, 0]), (1.0, [0, 0, 0])]),
                         "rotation": kf([(0.0, [0, 0, 0]), (0.4, [-6, 0, 0]), (0.6, [8, 0, 0]), (1.0, [0, 0, 0])])},
                "right_leg": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-45, 0, 0]), (0.6, [5, 0, 0]), (1.0, [0, 0, 0])])},
                "right_arm": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-30, 0, 35]), (0.6, [0, 0, 10]), (1.0, [0, 0, 0])])},
                "left_arm": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-30, 0, -35]), (0.6, [0, 0, -10]), (1.0, [0, 0, 0])])},
            },
        },
        "animation.summoned_ent.summon": {
            "loop": "hold_on_last_frame", "animation_length": 1.5,
            "bones": {
                "root": {"position": kf([(0.0, [0, -46, 0]), (0.6, [0, -22, 0]), (1.2, [0, 0, 0]), (1.5, [0, 0, 0])]),
                         "rotation": kf([(0.0, [0, 0, -4]), (0.3, [0, 0, 4]), (0.6, [0, 0, -4]), (0.9, [0, 0, 3]), (1.2, [0, 0, 0])])},
                "right_arm": {"rotation": kf([(0.0, [-170, 0, 0]), (1.0, [-120, 0, 15]), (1.5, [0, 0, 0])])},
                "left_arm": {"rotation": kf([(0.0, [-170, 0, 0]), (1.0, [-120, 0, -15]), (1.5, [0, 0, 0])])},
                "head": {"rotation": kf([(0.0, [20, 0, 0]), (1.2, [-10, 0, 0]), (1.5, [0, 0, 0])])},
            },
        },
    }
    write_json("animations/entity/summoned_ent.animation.json", {"format_version": "1.8.0", "animations": anims})


def ent():
    ent_texture()
    ent_geo()
    ent_animations()
    # Ovo do Ent (cores no Java: DeferredSpawnEggItem)
    write_json("models/item/summoned_ent_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


if __name__ == "__main__":
    blocks()
    icons()
    ent()
    print("fusions_a: assets gerados")
