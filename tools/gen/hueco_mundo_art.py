"""
Texturas e modelos (blocos, itens, portais, ceu) do modulo hueco_mundo. Chamado por tools/gen/hueco_mundo.py.
Tudo desenhado aqui (pixel art procedural), nada copiado.
"""
import math
import os
import random

from hueco_mundo_lib import ASSETS, write_json, H, CLEAR
from pixelart import Canvas, mix, lighten, darken

NS = "isekai_mahou"


def save(c, rel):
    c.save(os.path.join(ASSETS, "textures", rel + ".png"))


def speck(c, colors, density, seed, x0=0, y0=0, x1=None, y1=None):
    rnd = random.Random(seed)
    x1 = c.w - 1 if x1 is None else x1
    y1 = c.h - 1 if y1 is None else y1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if rnd.random() < density:
                c.set(x, y, H(rnd.choice(colors)))


def fill(c, color, seed, amount=0.05):
    c.rect(0, 0, c.w - 1, c.h - 1, H(color))
    c.noise(amount, seed)


# =====================================================================================
#  Blocos
# =====================================================================================
def tex_hollow_sand():
    c = Canvas(16, 16)
    fill(c, "#E9ECF2", 11, 0.03)
    speck(c, ["#D6DAE4", "#F8F9FD", "#CDD2DE"], 0.22, 12)
    speck(c, ["#BFC5D3"], 0.03, 13)
    save(c, "block/hollow_sand")


def tex_hollow_sandstone():
    side = Canvas(16, 16)
    bands = ["#E6E9F0", "#E6E9F0", "#DADEE6", "#E1E4EB", "#E1E4EB", "#E1E4EB", "#D3D7E0", "#DCDFE7",
             "#DCDFE7", "#DCDFE7", "#CFD3DD", "#D8DBE4", "#D8DBE4", "#D2D6DF", "#C8CDD8", "#BCC1CE"]
    for y, col in enumerate(bands):
        side.rect(0, y, 15, y, H(col))
    side.noise(0.03, 21)
    speck(side, ["#C4C9D5"], 0.05, 22, 0, 4, 15, 15)
    save(side, "block/hollow_sandstone")
    top = Canvas(16, 16)
    fill(top, "#E6E9F0", 23, 0.025)
    top.outline_rect(0, 0, 15, 15, H("#D6DAE3"))
    speck(top, ["#DADEE6", "#F1F3F8"], 0.12, 24, 1, 1, 14, 14)
    save(top, "block/hollow_sandstone_top")
    bot = Canvas(16, 16)
    fill(bot, "#D2D6DF", 25, 0.04)
    speck(bot, ["#C3C8D4", "#DDE0E8"], 0.18, 26)
    save(bot, "block/hollow_sandstone_bottom")


def tex_bricks():
    c = Canvas(16, 16)
    mortar = H("#B4BAC8")
    c.rect(0, 0, 15, 15, mortar)
    rnd = random.Random(31)
    for row in range(4):
        y0 = row * 4
        off = 0 if row % 2 == 0 else 4
        for bx in range(-1, 3):
            x0 = bx * 8 + off
            base = H(rnd.choice(["#E4E7EE", "#DDE1E9", "#E8EBF2", "#D9DDE6"]))
            for y in range(y0, y0 + 3):
                for x in range(x0, x0 + 7):
                    if 0 <= x < 16:
                        k = lighten(base, 0.08) if y == y0 else (darken(base, 0.08) if y == y0 + 2 else base)
                        c.set(x, y, k)
    c.noise(0.025, 32)
    save(c, "block/hollow_sandstone_bricks")


def tex_crystal():
    c = Canvas(16, 16)
    pal = [H("#5E8FBF"), H("#8FC4F2"), H("#C8E8FF"), H("#F2FAFF")]

    def shard(cx, base_y, h, w):
        for y in range(h):
            yy = base_y - y
            half = max(0, round(w * (1 - y / h)))
            for dx in range(-half, half + 1):
                t = (dx + half) / max(1, 2 * half)
                col = pal[0] if t < 0.25 else pal[1] if t < 0.6 else pal[2]
                c.set(cx + dx, yy, col)
            if half == 0:
                c.set(cx, yy, pal[3])
        c.set(cx, base_y - h + 1, pal[3])
        c.set(cx, base_y - h + 2, pal[3])
        for y in range(2, h - 2):
            c.set(cx, base_y - y, pal[2] if y % 3 else pal[3])

    shard(8, 15, 14, 2)
    shard(4, 15, 9, 1)
    shard(12, 15, 10, 1)
    shard(6, 15, 5, 1)
    shard(11, 15, 4, 1)
    save(c, "block/hollow_crystal")


def tex_altar_hollow():
    side = Canvas(16, 16)
    fill(side, "#17151D", 41, 0.06)
    side.rect(0, 0, 15, 1, H("#EDE8DC"))
    side.rect(0, 14, 15, 15, H("#C9C2B0"))
    side.rect(0, 2, 15, 2, H("#8C8678"))
    for x in (2, 7, 12):
        side.rect(x, 4, x + 1, 12, H("#24212C"))
    rnd = random.Random(42)
    x = 8
    for y in range(3, 14):
        side.set(x, y, H("#9FD8FF"))
        if rnd.random() < 0.4:
            side.set(x + 1, y, H("#5E8FBF"))
        x = max(3, min(12, x + rnd.choice((-1, 0, 1))))
    save(side, "block/garganta_altar_side")
    top = Canvas(16, 16)
    fill(top, "#1C1A22", 43, 0.05)
    top.outline_rect(0, 0, 15, 15, H("#EDE8DC"))
    top.outline_rect(1, 1, 14, 14, H("#8C8678"))
    # Rasgo denteado no centro (a Garganta em miniatura).
    for y in range(3, 13):
        w = 1 if y in (3, 12) else 2 if y in (4, 11) else 3
        off = (1 if y % 3 == 0 else 0) - (1 if y % 4 == 0 else 0)
        for x in range(8 - w + off, 8 + w + off):
            top.set(x, y, H("#030305"))
        top.set(8 - w + off - 1, y, H("#9FD8FF"))
        top.set(8 + w + off, y, H("#9FD8FF"))
    save(top, "block/garganta_altar_top")
    bot = Canvas(16, 16)
    fill(bot, "#121016", 44, 0.05)
    save(bot, "block/garganta_altar_bottom")


def tex_altar_fey():
    side = Canvas(16, 16)
    fill(side, "#6E7268", 51, 0.07)
    speck(side, ["#4F7A3A", "#5E8A3A", "#3E6A2E"], 0.18, 52, 0, 0, 15, 5)
    speck(side, ["#5F635A", "#7E8276"], 0.15, 53, 0, 6, 15, 15)
    side.rect(0, 15, 15, 15, H("#4C5048"))
    for (x, y) in ((4, 8), (5, 9), (4, 10), (11, 8), (10, 9), (11, 10), (7, 11), (8, 11), (8, 7)):
        side.set(x, y, H("#F29AD8"))
    save(side, "block/fey_ring_stone_side")
    top = Canvas(16, 16)
    fill(top, "#73776C", 54, 0.06)
    speck(top, ["#4F7A3A", "#5E8A3A"], 0.12, 55)
    for i in range(24):
        a = 2 * math.pi * i / 24
        top.set(round(7.5 + math.cos(a) * 5.5), round(7.5 + math.sin(a) * 5.5), H("#F6C8F0"))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (7, 5), (10, 8), (7, 10), (5, 7)):
        top.set(x, y, H("#FFD6F5"))
    save(top, "block/fey_ring_stone_top")
    bot = Canvas(16, 16)
    fill(bot, "#5A5E55", 56, 0.06)
    save(bot, "block/fey_ring_stone_bottom")


def tex_glowcap():
    c = Canvas(16, 16)
    fill(c, "#E86CB8", 61, 0.05)
    rnd = random.Random(62)
    for _ in range(9):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        c.rect(x, y, x + 1, y + 1, H("#FFD6F0"))
        c.set(x, y, H("#FFFFFF"))
    speck(c, ["#C94F9A"], 0.08, 63)
    save(c, "block/fey_glowcap")


def tex_moonpetal():
    c = Canvas(16, 16)
    stem = H("#4E8A3E")
    for y in range(8, 16):
        c.set(8 if y > 11 else 7, y, stem)
    c.set(6, 12, H("#5FA04A"))
    c.set(5, 11, H("#5FA04A"))
    c.set(9, 13, H("#5FA04A"))
    c.set(10, 12, H("#5FA04A"))
    petals = [(7, 2), (6, 3), (8, 3), (5, 4), (9, 4), (4, 5), (10, 5), (5, 6), (9, 6), (6, 7), (8, 7)]
    for (x, y) in petals:
        c.set(x, y, H("#A8D8FF"))
    for (x, y) in ((6, 4), (8, 4), (6, 6), (8, 6), (7, 3), (7, 7), (5, 5), (9, 5)):
        c.set(x, y, H("#D8EEFF"))
    c.set(7, 5, H("#FFFFFF"))
    c.set(7, 4, H("#F4FAFF"))
    c.set(6, 5, H("#F4FAFF"))
    c.set(8, 5, H("#F4FAFF"))
    c.set(7, 6, H("#F4FAFF"))
    save(c, "block/moonpetal")


# =====================================================================================
#  Itens
# =====================================================================================
def item(rows, pal, name, seed=0, noise=0.0):
    c = Canvas(16, 16)
    c.from_rows(rows, {k: H(v) for k, v in pal.items()})
    if noise:
        c.noise(noise, seed)
    save(c, "item/" + name)


def axe():
    """Machado da Decadencia: lamina de meia-lua dourada (lado de cima/esquerda) e cabo de osso na diagonal."""
    c = Canvas(16, 16)
    for i in range(12):
        x, y = 2 + i, 14 - i
        c.set(x, y, H("#EDE8DC") if i % 3 else H("#C9C2B0"))
        c.set(x + 1, y, H("#8C8678"))
    c.set(1, 15, H("#1C1B22"))
    c.set(2, 15, H("#1C1B22"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 9.0, y - 5.0)
            side = x + y
            if d <= 5.6 and 6 <= side <= 14:
                edge = d > 4.6
                c.set(x, y, H("#F2D27A") if edge else (H("#C9A23A") if (x * 7 + y * 3) % 5 else H("#8A6A1E")))
    for (x, y) in ((12, 6), (13, 6), (13, 7), (14, 7), (14, 8)):
        c.set(x, y, H("#8A6A1E"))
    for (x, y) in ((7, 6), (8, 5), (9, 4)):
        c.set(x, y, H("#5A4410"))
    c.set(6, 3, H("#FF3550"))
    save(c, "item/decay_axe")


def tex_items():
    item([
        "................",
        "......wwww......",
        "....wwWWWWww....",
        "...wWWWWWWWWw...",
        "..wWWbbWWbbWWw..",
        "..wWbkkWWkkbWw..",
        "..wWbkrWWkrbWw..",
        "..wWWbbWWbbWW...",
        "..wWWWWwwWWWW...",
        "...wWrWWWWr.....",
        "...wWrWWWW......",
        "....wWtWtW......",
        ".....wtwt.......",
        "................",
        "................",
        "................"],
        {"w": "#C9C2B0", "W": "#F4F0E6", "b": "#8C8678", "k": "#0A0A0E", "r": "#FF3550", "t": "#DCD6C8"},
        "hollow_mask_fragment", 1, 0.04)
    item([
        "................",
        ".....kkkkkk.....",
        "...kkKKKKKKkk...",
        "..kKKKKKKKKKKk..",
        "..kKKrrrrrrKKk..",
        ".kKKrrRRRRrrKKk.",
        ".kKrrRRwwRRrrKk.",
        ".kKrRRwwwwRRrKk.",
        ".kKrRRwwwwRRrKk.",
        ".kKrrRRwwRRrrKk.",
        ".kKKrrRRRRrrKKk.",
        "..kKKrrrrrrKKk..",
        "..kKKKKKKKKKKk..",
        "...kkKKKKKKkk...",
        ".....kkkkkk.....",
        "................"],
        {"k": "#050507", "K": "#1C1B22", "r": "#9A1A2E", "R": "#FF3550", "w": "#FFD0D8"},
        "menos_core", 2, 0.03)
    item([
        "................",
        "........g.......",
        ".......gGg......",
        "......gGWGg.....",
        "......gGWGg.....",
        ".....gGGWGGg....",
        ".....gGGWGGg....",
        "....gGGGWGGGg...",
        "....gGGGGGGdg...",
        ".....gGGGGdg....",
        ".....gdGGGdg....",
        "......gdGdg.....",
        "......gddg......",
        ".......gg.......",
        "................",
        "................"],
        {"g": "#1E7A4A", "G": "#55FF8A", "W": "#DFFFE8", "d": "#2FB862"},
        "arrancar_soul_shard", 3, 0.03)
    item([
        "................",
        "........w.......",
        "...w...wPw......",
        "..wPw...w...w...",
        "...w.......wPw..",
        "......p.....w...",
        ".....pPp........",
        "......p...p.....",
        "..p......pPp....",
        ".pPp......p.....",
        "..p...pp.....p..",
        ".....pPPpp..pPp.",
        "....pPPPPPp..p..",
        "...pPPWPPPPp....",
        "..ppPPPPPPPpp...",
        "................"],
        {"p": "#C94F9A", "P": "#F29AD8", "w": "#FFFFFF", "W": "#FFE6FA"},
        "fey_dust", 4, 0.02)
    item([
        "................",
        "...t.......t....",
        "..tRRt...tRRt...",
        ".tRRRRt.tRRRRt..",
        ".RRrRRRtRRRRRR..",
        "tRrRRRRRRRRRRRt.",
        ".RRRRRRRRRRRRR..",
        ".tRRRRRRRRRRRt..",
        "..RRRRRRRRRRR...",
        "..tRRRRRRRRRt...",
        "...RRRRRRRRR....",
        "....tRRRRRt.....",
        ".....RRRRR......",
        "......tRt.......",
        ".......t........",
        "................"],
        {"t": "#3E6A2E", "R": "#C23A5A", "r": "#FF8FA8"},
        "bramble_heart", 5, 0.05)
    item([
        "................",
        "......cccc......",
        ".....cCWWCc.....",
        "....cCCWWCCc....",
        "...cCCCWWCCCc...",
        "...cCCCWWCCCc...",
        "...cCCCCCCCCc...",
        "...cBCCCCCCBc...",
        "...cBBCCCCBBc...",
        "...cBBBCCBBBc...",
        "...cBBBBBBBBc...",
        "....cBBBBBBc....",
        ".....cBBBBc.....",
        "......cccc......",
        "................",
        "................"],
        {"c": "#3F7FBF", "C": "#BFE6FF", "W": "#FFFFFF", "B": "#8FD8FF"},
        "reishi_crystal", 6, 0.02)
    item([
        "................",
        "...wwwww........",
        "..wWWWWWw.......",
        ".wWkWWWkWw......",
        ".wWkWWWkWw......",
        ".wWWWkWWWw......",
        ".wWWWkWWWw......",
        "..wWWkWWw.......",
        "...wwkww........",
        ".....kbb........",
        ".....kb.........",
        ".....kbbb.......",
        ".....kb.........",
        ".....kbb........",
        ".....kb.........",
        "................"],
        {"w": "#C9C2B0", "W": "#F4F0E6", "k": "#0A0A12", "b": "#9FD8FF"},
        "garganta_key", 7, 0.03)
    axe()
    item([
        "................",
        "...........WW...",
        "..........WSSa..",
        ".........WSSSa..",
        "........WSSSa...",
        ".......WSSSa....",
        "......WSSSa.....",
        ".....WSSSa......",
        "....WSSSa.......",
        "...kWSSa........",
        "..kgkSa.........",
        "...kgk..........",
        "..k.kg..........",
        ".k...k..........",
        "k...............",
        "................"],
        {"W": "#FFFDF6", "S": "#E8D7A8", "a": "#3FA7C9", "k": "#2A2730", "g": "#C9A23A"},
        "tide_sand_blade", 9, 0.02)
    item([
        "................",
        ".pp..........pp.",
        "pPPp........pPPp",
        "pPWPp......pPWPp",
        "pPPWPp....pPWPPp",
        ".pPPWPp..pPWPPp.",
        ".pPPPPPkkPPPPPp.",
        "..pPPPPkkPPPPp..",
        "..pPPPpkkpPPPp..",
        "...pPPpkkpPPp...",
        "...pPp.kk.pPp...",
        "....pp.kk.pp....",
        "....p..kk..p....",
        "................",
        "................",
        "................"],
        {"p": "#C94F9A", "P": "#F6C8F0", "W": "#FFFFFF", "k": "#3E6A2E"},
        "queen_wings", 10, 0.02)


# =====================================================================================
#  Portais, projetil e ceu
# =====================================================================================
def radial(size, fn):
    c = Canvas(size, size)
    m = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - m, y - m) / m
            c.set(x, y, fn(d, x, y))
    return c


def tex_effects():
    # Orbe dos projeteis (branco: o renderer tinge pela cor do tipo).
    orb = radial(16, lambda d, x, y: (255, 255, 255, max(0, min(255, int(255 * (1 - d) ** 1.4 * 1.6)))) if d < 1 else CLEAR)
    save(orb, "entity/hm_bolt")

    # Garganta: rasgo em losango denteado (interior negro com "estrelas" frias).
    w, h = 32, 64
    rnd = random.Random(71)
    jag = [rnd.uniform(-1.6, 1.6) for _ in range(h)]
    rift = Canvas(w, h)
    edge = Canvas(w, h)
    for y in range(h):
        t = y / (h - 1)
        half = (w / 2 - 1) * math.sin(math.pi * t) ** 0.8 + (jag[y] if 2 < y < h - 3 else 0)
        for x in range(w):
            dx = abs(x + 0.5 - w / 2)
            if dx <= half - 1:
                k = dx / max(1.0, half)
                base = mix(H("#020203"), H("#1A1030"), k * 0.8)
                rift.set(x, y, base)
            elif dx <= half + 2.5:
                a = max(0, 1 - (dx - half + 1) / 3.5)
                edge.set(x, y, (255, 255, 255, int(255 * a)))
                if dx <= half:
                    rift.set(x, y, H("#0A0614"))
    for _ in range(26):
        x, y = rnd.randint(8, 23), rnd.randint(10, 53)
        if rift.get(x, y)[3] > 0:
            rift.set(x, y, H(rnd.choice(["#9FD8FF", "#E8F4FF", "#6E5AB8"])))
    save(rift, "entity/garganta_rift")
    save(edge, "entity/garganta_rift_edge")

    # Circulo das Fadas: anel de runas + petalas (branco/rosa, tingido no render).
    ring = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5)
            if 26 <= d <= 30:
                a = 1 - abs(d - 28) / 2.2
                ring.set(x, y, (255, 240, 252, max(0, int(255 * a))))
            elif 19 <= d <= 20.5:
                ring.set(x, y, (255, 214, 245, 200))
    for i in range(12):
        a = 2 * math.pi * i / 12
        cx, cy = 31.5 + math.cos(a) * 23.5, 31.5 + math.sin(a) * 23.5
        for k in range(-2, 3):
            ring.set(round(cx + math.cos(a + 1.57) * k), round(cy + math.sin(a + 1.57) * k), (255, 255, 255, 255))
        ring.set(round(cx + math.cos(a) * 1.5), round(cy + math.sin(a) * 1.5), (255, 255, 255, 220))
        ring.set(round(cx - math.cos(a) * 1.5), round(cy - math.sin(a) * 1.5), (255, 255, 255, 220))
    for i in range(6):
        a = 2 * math.pi * i / 6 + 0.26
        for r in range(4, 18):
            ring.set(round(31.5 + math.cos(a) * r), round(31.5 + math.sin(a) * r), (255, 200, 240, 110))
    save(ring, "entity/fey_ring_gate")

    beam = Canvas(16, 64)
    for y in range(64):
        for x in range(16):
            dx = abs(x - 7.5) / 7.5
            a = (1 - dx) ** 1.5 * (y / 63) ** 1.3
            beam.set(x, y, (255, 255, 255, int(255 * a)))
    save(beam, "entity/fey_beam")

    # Lua crescente (desenhada branca; o ceu tinge) e halo suave.
    moon = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            d1 = math.hypot(x - 31.5, y - 31.5)
            d2 = math.hypot(x - 42.0, y - 25.0)
            if d1 <= 26 and d2 > 22:
                edge_a = min(1.0, (26 - d1) / 1.5, (d2 - 22) / 1.5)
                shade = 0.82 + 0.18 * (1 - (x / 63))
                v = int(255 * shade)
                moon.set(x, y, (v, v, min(255, v + 6), int(255 * edge_a)))
    rnd = random.Random(73)
    for _ in range(14):
        x, y = rnd.randint(8, 40), rnd.randint(14, 56)
        p = moon.get(x, y)
        if p[3] > 200:
            moon.set(x, y, darken(p, 0.12))
    save(moon, "environment/hm_crescent_moon")
    halo = radial(64, lambda d, x, y: (255, 255, 255, max(0, int(255 * (1 - d) ** 2.2))) if d < 1 else CLEAR)
    save(halo, "environment/hm_halo")


# =====================================================================================
#  Modelos e blockstates
# =====================================================================================
def block_model(name, obj):
    write_json(os.path.join(ASSETS, "models/block", name + ".json"), obj)


def item_model(name, obj):
    write_json(os.path.join(ASSETS, "models/item", name + ".json"), obj)


def blockstate(name, model=None):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
               {"variants": {"": {"model": "%s:block/%s" % (NS, model or name)}}})


def altar_model(name, tex):
    t = {"side": "%s:block/%s_side" % (NS, tex), "top": "%s:block/%s_top" % (NS, tex),
         "bottom": "%s:block/%s_bottom" % (NS, tex), "particle": "%s:block/%s_side" % (NS, tex)}

    def el(f, to):
        return {"from": f, "to": to, "faces": {
            "down": {"texture": "#bottom", "cullface": "down"} if f[1] == 0 else {"texture": "#bottom"},
            "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"},
            "west": {"texture": "#side"}, "east": {"texture": "#side"}}}
    block_model(name, {"parent": "minecraft:block/block", "textures": t, "elements": [
        el([1, 0, 1], [15, 4, 15]),
        el([3, 4, 3], [13, 10, 13]),
        el([2, 10, 2], [14, 12, 14]),
    ]})


def models():
    blockstate("hollow_sand")
    block_model("hollow_sand", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/hollow_sand"}})
    blockstate("hollow_sandstone")
    block_model("hollow_sandstone", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": NS + ":block/hollow_sandstone", "top": NS + ":block/hollow_sandstone_top",
        "bottom": NS + ":block/hollow_sandstone_bottom"}})
    blockstate("hollow_sandstone_bricks")
    block_model("hollow_sandstone_bricks", {"parent": "minecraft:block/cube_all",
                                            "textures": {"all": NS + ":block/hollow_sandstone_bricks"}})
    blockstate("hollow_crystal")
    block_model("hollow_crystal", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                   "textures": {"cross": NS + ":block/hollow_crystal"}})
    blockstate("moonpetal")
    block_model("moonpetal", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                              "textures": {"cross": NS + ":block/moonpetal"}})
    blockstate("fey_glowcap")
    block_model("fey_glowcap", {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/fey_glowcap"}})
    blockstate("garganta_altar")
    altar_model("garganta_altar", "garganta_altar")
    blockstate("fey_ring_stone")
    altar_model("fey_ring_stone", "fey_ring_stone")

    for b in ("hollow_sand", "hollow_sandstone", "hollow_sandstone_bricks", "fey_glowcap", "garganta_altar", "fey_ring_stone"):
        item_model(b, {"parent": "%s:block/%s" % (NS, b)})
    for b in ("hollow_crystal", "moonpetal"):
        item_model(b, {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:block/%s" % (NS, b)}})
    for i in ("hollow_mask_fragment", "menos_core", "arrancar_soul_shard", "fey_dust", "bramble_heart", "reishi_crystal",
              "garganta_key", "queen_wings"):
        item_model(i, {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, i)}})
    for i in ("decay_axe", "tide_sand_blade"):
        item_model(i, {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, i)}})
    for e in ("hollow_crawler", "hollow_brute", "hollow_wraith", "menos_giant", "arrancar", "fey_pixie", "bramble_hound",
              "hollow_king", "sand_empress", "thorn_queen"):
        item_model(e + "_spawn_egg", {"parent": "minecraft:item/template_spawn_egg"})


def build_all():
    tex_hollow_sand()
    tex_hollow_sandstone()
    tex_bricks()
    tex_crystal()
    tex_altar_hollow()
    tex_altar_fey()
    tex_glowcap()
    tex_moonpetal()
    tex_items()
    tex_effects()
    models()
    print("  texturas/modelos: 8 blocos, 10 itens, 10 ovos, portais e ceu")
