"""
Gera os assets e dados do modulo ritual_witch (Caldeirao Ritual da Bruxa).
Uso (na raiz do projeto):  py -I tools/gen/ritual_witch.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/ritual_witch")
LANG = os.path.join(ROOT, "src/main/lang/ritual_witch")
T = (0, 0, 0, 0)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tex(name):
    return os.path.join(A, "textures", name + ".png")


# =====================================================================================
#  Itens: ferramentas e ingredientes
# =====================================================================================
def spoon():
    c = Canvas(16, 16)
    dark, mid, light = hex_rgba("4A311C"), hex_rgba("7A5532"), hex_rgba("A57A4A")
    # cabo diagonal (2 px de espessura)
    for i in range(9):
        x, y = 2 + i, 14 - i
        c.set(x, y, mid)
        c.set(x + 1, y, dark)
        c.set(x, y - 1, light if i % 3 else mid)
    # concha
    c.circle(12.0, 4.0, 3.1, dark)
    c.circle(12.0, 4.0, 2.4, hex_rgba("5B3A86"))
    c.circle(11.6, 3.6, 1.4, hex_rgba("8C5CC2"))
    c.set(11, 3, hex_rgba("D6B8FF"))
    # fita roxa e gema no cabo
    c.set(4, 12, hex_rgba("9B3FD0")); c.set(5, 12, hex_rgba("6E2399")); c.set(4, 11, hex_rgba("C27CF2"))
    c.set(2, 14, hex_rgba("3A2412"))
    c.outline(hex_rgba("1E140C"))
    c.save(tex("item/ritual_spoon"))
    item_model(A, "ritual_spoon", parent="minecraft:item/handheld")


def recipe_page():
    c = Canvas(16, 16)
    paper, shade, edge = hex_rgba("EDE0BC"), hex_rgba("D4C193"), hex_rgba("A88C5A")
    c.rect(3, 2, 12, 14, paper)
    # borda rasgada em cima
    for x in range(3, 13):
        if x % 3 == 0:
            c.set(x, 2, T)
    for y in range(2, 15):
        c.set(12, y, shade)
    for x in range(3, 13):
        c.set(x, 14, shade)
    ink = hex_rgba("5A3A6A")
    for y, (x0, x1) in zip((4, 6, 8, 10), ((4, 10), (4, 9), (4, 10), (4, 7))):
        for x in range(x0, x1 + 1):
            if (x + y) % 4 != 0:
                c.set(x, y, ink)
    # selo de cera roxo
    c.circle(10.5, 12.0, 2.2, hex_rgba("7A1FA3"))
    c.set(10, 11, hex_rgba("B661E0")); c.set(11, 12, hex_rgba("4E0F6B"))
    c.noise(0.05, seed=7)
    c.outline(edge)
    c.save(tex("item/witch_recipe_page"))
    item_model(A, "witch_recipe_page")


def recipe_book():
    c = Canvas(16, 16)
    rows = [
        "................",
        "..aaaaaaaaaaa...",
        "..abbbbbbbbbap..",
        "..abccccccccbap.",
        "..abcccmmcccbap.",
        "..abccmmccccbap.",
        "..abccmcccccbap.",
        "..abccmmccccbap.",
        "..abcccmmcccbap.",
        "..abccccccccbap.",
        "..abccccccccbap.",
        "..agbbbbbbbbgap.",
        "..aaaaaaaaaaapp.",
        "...ppppppppppp..",
        "................",
        "................",
    ]
    pal = {"a": hex_rgba("1E0B26"), "b": hex_rgba("4B1F5E"), "c": hex_rgba("5E2A75"), "m": hex_rgba("E8E0FF"),
           "g": hex_rgba("D4A93A"), "p": hex_rgba("E8DCC0")}
    c.from_rows(rows, pal)
    for x in range(3, 12):
        c.set(x, 2, hex_rgba("6E3A88"))
    c.set(3, 3, hex_rgba("D4A93A")); c.set(12, 3, hex_rgba("D4A93A"))
    c.set(9, 5, hex_rgba("FFF6C8"))  # estrela ao lado da lua
    c.noise(0.04, seed=3)
    c.save(tex("item/witch_recipe_book"))
    item_model(A, "witch_recipe_book")


def mandrake():
    c = Canvas(16, 16)
    rows = [
        "......l..l......",
        ".....lLl.Ll.....",
        "......lLLl......",
        ".......ll.......",
        "......rrrr......",
        ".....rRRRRr.....",
        ".....rReRer.....",
        ".....rRRRRr.....",
        "....rrRmmRrr....",
        "...r.rRRRRr.r...",
        "..r..rRRRRr..r..",
        ".....rRRRRr.....",
        "......rRRr......",
        ".....rr..rr.....",
        "....r......r....",
        "...r........r...",
    ]
    pal = {"l": hex_rgba("2F5E25"), "L": hex_rgba("6DB04E"), "r": hex_rgba("7A5530"), "R": hex_rgba("C9A06A"),
           "e": hex_rgba("2A1A0C"), "m": hex_rgba("5A3A1E")}
    c.from_rows(rows, pal)
    c.noise(0.06, seed=11)
    c.save(tex("item/witch_mandrake_root"))
    item_model(A, "witch_mandrake_root")


def moonpetal():
    c = Canvas(16, 16)
    base = hex_rgba("B8C8F5")
    for y in range(16):
        for x in range(16):
            # petala em forma de gota inclinada
            u, v = (x - 7.5) / 4.2, (y - 8.5) / 6.5
            uu = u + v * 0.35
            if uu * uu + v * v <= 1.0 and v > -1.0:
                t = max(0.0, min(1.0, (v + 1) / 2))
                c.set(x, y, mix(hex_rgba("EEF3FF"), hex_rgba("8C9EDB"), t))
    for y in range(4, 14):
        c.set(int(round(7.5 - (y - 8.5) * 0.3)), y, lighten(base, 0.6))
    for (x, y) in ((3, 3), (12, 4), (13, 12), (2, 11)):
        c.set(x, y, (255, 255, 255, 220))
    c.set(12, 3, (220, 230, 255, 160)); c.set(3, 2, (220, 230, 255, 160))
    c.outline(hex_rgba("5D6CA8"))
    c.save(tex("item/witch_moonpetal"))
    item_model(A, "witch_moonpetal")


def bat_wing():
    c = Canvas(16, 16)
    joint = (2.5, 3.5)
    tips = [(14.5, 4.5), (13.0, 10.5), (7.5, 14.0)]
    # contorno da membrana: articulacao -> pontas, com recortes (curvas para dentro) entre as pontas
    poly = [joint, tips[0], (11.5, 7.0), tips[1], (8.5, 10.5), tips[2], (4.0, 11.0)]

    def inside(px, py):
        n, ok = len(poly), False
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if (y1 > py) != (y2 > py) and px < (x2 - x1) * (py - y1) / (y2 - y1) + x1:
                ok = not ok
        return ok

    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                t = (x + y) / 28.0
                c.set(x, y, mix(hex_rgba("7A6488"), hex_rgba("4A3858"), t))
    bone = hex_rgba("C9BCD2")
    for tx, ty in tips:
        c.line(int(joint[0]), int(joint[1]), int(tx), int(ty), bone)
    c.line(2, 3, 14, 4, hex_rgba("E2D8EA"))  # braco (borda de cima)
    c.set(1, 2, hex_rgba("EDE6F2")); c.set(1, 3, hex_rgba("EDE6F2"))  # garra
    c.outline(hex_rgba("1A1220"))
    c.save(tex("item/witch_bat_wing"))
    item_model(A, "witch_bat_wing")


def toad_eye():
    c = Canvas(16, 16)
    # globo amarelado com veias
    c.circle(8, 8.5, 5.6, hex_rgba("E6E2B8"))
    c.shade_sphere(8, 8.5, 5.6, light=0.4, dark=0.5)
    for (x0, y0, x1, y1) in ((3, 7, 4, 9), (13, 10, 12, 8), (5, 13, 6, 12)):
        c.line(x0, y0, x1, y1, hex_rgba("B83A3A"))
    # iris dourada e pupila horizontal de sapo
    c.circle(8.5, 8.5, 3.2, hex_rgba("D99A1E"))
    c.circle(8.5, 8.5, 2.2, hex_rgba("F2C14E"))
    for x in range(6, 11):
        c.set(x, 8, hex_rgba("120C06"))
    c.set(7, 7, hex_rgba("120C06")); c.set(8, 7, hex_rgba("120C06")); c.set(9, 7, hex_rgba("120C06"))
    c.set(6, 6, (255, 255, 240, 255)); c.set(7, 6, (255, 255, 240, 200))
    c.outline(hex_rgba("4A3A18"))
    c.save(tex("item/witch_toad_eye"))
    item_model(A, "witch_toad_eye")


def grave_dust():
    c = Canvas(16, 16)
    rnd = random.Random(42)
    for y in range(16):
        for x in range(16):
            # monte de po (meia elipse)
            u, v = (x - 7.5) / 6.5, (y - 13.0) / 6.5
            if u * u + v * v <= 1.0 and y <= 14:
                t = (y - 7) / 8.0
                col = mix(hex_rgba("B5B0A8"), hex_rgba("5E5A54"), max(0.0, min(1.0, t)))
                c.set(x, y, col)
    for _ in range(8):
        x, y = rnd.randint(3, 12), rnd.randint(9, 13)
        if c.get(x, y)[3]:
            c.set(x, y, hex_rgba("E8E4D8"))
    for _ in range(6):
        x, y = rnd.randint(3, 12), rnd.randint(9, 13)
        if c.get(x, y)[3]:
            c.set(x, y, hex_rgba("3A3632"))
    c.set(4, 5, hex_rgba("9A958D")); c.set(11, 4, hex_rgba("9A958D")); c.set(8, 3, hex_rgba("B5B0A8"))
    c.outline(hex_rgba("2E2B28"))
    c.save(tex("item/witch_grave_dust"))
    item_model(A, "witch_grave_dust")


# =====================================================================================
#  Frascos: elixires, maldicoes, revelacoes
# =====================================================================================
GLASS = (200, 225, 240, 150)
GLASS_EDGE = hex_rgba("2B2F3A")


def elixir(name, liquid_hex, accent_hex):
    c = Canvas(16, 16)
    liquid, accent = hex_rgba(liquid_hex), hex_rgba(accent_hex)
    # corpo redondo de vidro
    c.circle(8.0, 10.5, 4.7, GLASS)
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3] and y >= 8:
                c.set(x, y, liquid if y > 8 else lighten(liquid, 0.35))
    c.shade_sphere(8.0, 10.5, 4.7, light=0.35, dark=0.45)
    # gargalo e rolha
    c.rect(7, 3, 8, 5, GLASS)
    c.rect(6, 1, 9, 2, hex_rgba("8A5A2E"))
    c.set(6, 1, hex_rgba("A9743F")); c.set(9, 2, hex_rgba("5E3A1C"))
    # fio colorido amarrado no gargalo
    c.set(6, 4, accent); c.set(9, 4, accent); c.set(10, 5, accent)
    # brilho do vidro
    c.set(5, 8, (255, 255, 255, 220)); c.set(5, 9, (255, 255, 255, 160)); c.set(6, 7, (255, 255, 255, 180))
    c.outline(GLASS_EDGE)
    c.save(tex("item/" + name))
    item_model(A, name)


def curse(name, liquid_hex, label_hex):
    c = Canvas(16, 16)
    liquid, label = hex_rgba(liquid_hex), hex_rgba(label_hex)
    dark_glass = (70, 60, 80, 200)
    rows = [
        "................",
        "......kkkk......",
        "......kKKk......",
        ".......gg.......",
        "......gggg......",
        ".....gggggg.....",
        "....gggggggg....",
        "....gggggggg....",
        "....gggggggg....",
        "....gggggggg....",
        "....gggggggg....",
        "....gggggggg....",
        "....gggggggg....",
        ".....gggggg.....",
        "................",
        "................",
    ]
    c.from_rows(rows, {"g": dark_glass, "k": hex_rgba("3A2A1E"), "K": hex_rgba("5A4230")})
    for y in range(7, 14):
        for x in range(4, 12):
            if c.get(x, y)[3]:
                t = (y - 7) / 7.0
                c.set(x, y, mix(lighten(liquid, 0.2), darken(liquid, 0.35), t))
    # rotulo com caveira
    c.rect(6, 8, 9, 11, label)
    sk = hex_rgba("EDE6D6")
    c.rect(7, 8, 8, 9, sk)
    c.set(6, 9, sk); c.set(9, 9, sk)
    c.set(7, 9, hex_rgba("1A1018")); c.set(8, 9, hex_rgba("1A1018"))
    c.set(7, 10, sk); c.set(8, 10, sk)
    # espinhos no ombro do frasco
    c.set(4, 5, dark_glass); c.set(11, 5, dark_glass)
    c.set(5, 6, (230, 230, 240, 200))
    c.outline(hex_rgba("140E18"))
    c.save(tex("item/" + name))
    item_model(A, name)


REVELATION = {
    1: ("B57EDC", "C9CED8", 1),
    2: ("4F7BFF", "C9CED8", 2),
    3: ("7A2FC0", "E2B84A", 2),
    4: ("2A4F9A", "E2B84A", 3),
    5: ("C06AA0", "F4D46A", 4),
}


def revelation(tier):
    liquid_hex, metal_hex, sparkles = REVELATION[tier]
    c = Canvas(16, 16)
    liquid, metal = hex_rgba(liquid_hex), hex_rgba(metal_hex)
    # frasco alto e fino
    for y in range(16):
        for x in range(16):
            u, v = (x - 7.5) / 3.3, (y - 10.0) / 4.8
            if u * u + v * v <= 1.0:
                t = max(0.0, min(1.0, (y - 6) / 9.0))
                c.set(x, y, mix(lighten(liquid, 0.45), darken(liquid, 0.3), t))
    c.rect(7, 3, 8, 5, GLASS)
    # tampa de metal (ornamentada nos tiers altos)
    c.rect(6, 2, 9, 2, metal)
    c.set(7, 1, metal); c.set(8, 1, metal)
    if tier >= 3:
        c.rect(6, 5, 9, 5, metal)
    if tier >= 4:
        c.set(5, 2, metal); c.set(10, 2, metal)
    if tier == 5:
        c.set(6, 0, metal); c.set(9, 0, metal); c.set(7, 0, hex_rgba("FF5A9A"))
    # estrela no corpo
    star = lighten(liquid, 0.85)
    c.set(7, 10, star); c.set(8, 10, star); c.set(7, 9, star); c.set(8, 11, star)
    c.set(6, 10, lighten(liquid, 0.6)); c.set(9, 10, lighten(liquid, 0.6))
    # brilho
    c.set(6, 7, (255, 255, 255, 220)); c.set(6, 8, (255, 255, 255, 140))
    c.outline(darken(liquid, 0.7))
    spots = [(2, 4), (13, 6), (12, 13), (3, 12), (13, 2)]
    for (x, y) in spots[:sparkles]:
        c.set(x, y, (255, 250, 220, 230))
    c.save(tex("item/revelation_potion_%d" % tier))
    item_model(A, "revelation_potion_%d" % tier)


# =====================================================================================
#  Bloco: Caldeirao Ritual
# =====================================================================================
def cauldron_textures():
    rnd = random.Random(9)
    iron, iron_l, iron_d = hex_rgba("2E2A35"), hex_rgba("4A4452"), hex_rgba("1C1922")
    rune, rune_l = hex_rgba("9D5BD6"), hex_rgba("D9A8FF")

    side = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if y <= 2:
                col = iron_l if y == 0 else hex_rgba("3A3442")
            elif y <= 5:
                col = hex_rgba("25212B")
            elif y <= 11:
                col = iron
            elif y <= 13:
                col = hex_rgba("26222C")
            else:
                col = hex_rgba("1E1B24")
            side.set(x, y, col)
    side.noise(0.08, seed=4)
    # rebites
    for x in (1, 5, 10, 14):
        side.set(x, 1, hex_rgba("6A6274"))
        side.set(x, 6, hex_rgba("5A5364"))
        side.set(x, 11, hex_rgba("4E4858"))
    # faixa de runas brilhantes (linhas 8-9)
    glyphs = ["x.x", ".x.", "xx.", ".xx", "x.x"]
    for i, x0 in enumerate((1, 4, 7, 10, 13)):
        g = glyphs[i % len(glyphs)]
        for dx, ch in enumerate(g):
            if x0 + dx < 16:
                side.set(x0 + dx, 8, rune_l if ch == "x" else rune)
                side.set(x0 + dx, 9, rune if ch == "x" else darken(rune, 0.5))
    for x in range(16):
        side.set(x, 7, darken(iron, 0.3)); side.set(x, 10, darken(iron, 0.3))
    # fuligem perto da base
    for _ in range(10):
        side.set(rnd.randint(0, 15), rnd.randint(12, 15), hex_rgba("141117"))
    side.save(tex("block/witch_cauldron_side"))

    top = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge <= 1:
                col = hex_rgba("4F4858") if edge == 0 else hex_rgba("3E3846")
            else:
                col = hex_rgba("221E28")
            top.set(x, y, col)
    top.noise(0.07, seed=8)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14), (8, 1), (8, 14), (1, 8), (14, 8)):
        top.set(x, y, hex_rgba("7A7086"))
    top.save(tex("block/witch_cauldron_top"))

    inner = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            inner.set(x, y, mix(hex_rgba("1F1B24"), hex_rgba("2B1F33"), (x + y) / 30.0))
    inner.noise(0.12, seed=12)
    for _ in range(6):
        inner.set(rnd.randint(1, 14), rnd.randint(1, 14), hex_rgba("3D2A4A"))
    inner.save(tex("block/witch_cauldron_inner"))

    bottom = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            bottom.set(x, y, hex_rgba("1A1720"))
    bottom.noise(0.1, seed=13)
    for x in (2, 13):
        for y in (2, 13):
            bottom.set(x, y, hex_rgba("3A3442"))
    bottom.save(tex("block/witch_cauldron_bottom"))


def brew_texture():
    """Textura animada (tons de cinza claros) do preparo; o renderer tinge com a cor da mistura."""
    frames = 16
    c = Canvas(16, 16 * frames)
    for f in range(frames):
        ph = 2 * math.pi * f / frames
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = 0.80 + 0.11 * math.sin(3 * a + r * 0.9 - ph) + 0.05 * math.sin(r * 1.7 + ph * 2)
                # bolhas que sobem e estouram
                for (bx, by, off) in ((4, 5, 0.0), (11, 9, 2.1), (6, 12, 4.2), (12, 3, 1.3)):
                    life = (math.sin(ph + off) + 1) / 2
                    if math.hypot(x - bx, y - by) < 0.6 + life * 1.1:
                        v += 0.12 * life
                v = max(0.55, min(1.0, v))
                g = int(255 * v)
                c.set(x, y + 16 * f, (g, g, g, 255))
    c.save(tex("block/witch_brew"))
    write_json(tex("block/witch_brew") + ".mcmeta", {"animation": {"frametime": 2, "interpolate": True}})


def cauldron_model():
    def face(t, cull=None, uv=None):
        f = {"texture": "#" + t}
        if cull:
            f["cullface"] = cull
        if uv:
            f["uv"] = uv
        return f

    els = []

    def box(fr, to, faces, name):
        els.append({"from": fr, "to": to, "faces": faces})  # name: so para leitura do script

    # Borda (aba) y 13-16
    box([0, 13, 0], [16, 16, 2], {"north": face("side", "north"), "south": face("inside"), "east": face("side", "east"),
                                   "west": face("side", "west"), "up": face("top", "up"), "down": face("bottom")}, "rim_n")
    box([0, 13, 14], [16, 16, 16], {"south": face("side", "south"), "north": face("inside"), "east": face("side", "east"),
                                     "west": face("side", "west"), "up": face("top", "up"), "down": face("bottom")}, "rim_s")
    box([0, 13, 2], [2, 16, 14], {"west": face("side", "west"), "east": face("inside"), "up": face("top", "up"),
                                   "down": face("bottom")}, "rim_w")
    box([14, 13, 2], [16, 16, 14], {"east": face("side", "east"), "west": face("inside"), "up": face("top", "up"),
                                     "down": face("bottom")}, "rim_e")
    # Pescoço (recuado 1 px) y 10-13
    box([1, 10, 1], [15, 13, 2], {"north": face("side"), "south": face("inside"), "east": face("side"), "west": face("side")}, "neck_n")
    box([1, 10, 14], [15, 13, 15], {"south": face("side"), "north": face("inside"), "east": face("side"), "west": face("side")}, "neck_s")
    box([1, 10, 2], [2, 13, 14], {"west": face("side"), "east": face("inside")}, "neck_w")
    box([14, 10, 2], [15, 13, 14], {"east": face("side"), "west": face("inside")}, "neck_e")
    # Bojo y 4-10
    box([0, 4, 0], [16, 10, 2], {"north": face("side", "north"), "south": face("inside"), "east": face("side", "east"),
                                  "west": face("side", "west"), "up": face("top"), "down": face("bottom")}, "belly_n")
    box([0, 4, 14], [16, 10, 16], {"south": face("side", "south"), "north": face("inside"), "east": face("side", "east"),
                                    "west": face("side", "west"), "up": face("top"), "down": face("bottom")}, "belly_s")
    box([0, 4, 2], [2, 10, 14], {"west": face("side", "west"), "east": face("inside"), "up": face("top"),
                                  "down": face("bottom")}, "belly_w")
    box([14, 4, 2], [16, 10, 14], {"east": face("side", "east"), "west": face("inside"), "up": face("top"),
                                    "down": face("bottom")}, "belly_e")
    # Fundo (o "up" é o chão de dentro)
    box([1, 2, 1], [15, 4, 15], {"north": face("side"), "south": face("side"), "east": face("side"), "west": face("side"),
                                  "up": face("inside"), "down": face("bottom")}, "base")
    # Pés
    for (x0, z0) in ((1, 1), (12, 1), (1, 12), (12, 12)):
        box([x0, 0, z0], [x0 + 3, 2, z0 + 3], {"north": face("side"), "south": face("side"), "east": face("side"),
                                                "west": face("side"), "down": face("bottom", "down")}, "leg")
    # Argolas laterais (fora do bloco: UV explícito)
    for (x0, x1, name) in ((-1, 0, "handle_w"), (16, 17, "handle_e")):
        box([x0, 6, 6.5], [x1, 9, 9.5], {
            "north": face("side", uv=[7, 7, 8, 10]), "south": face("side", uv=[7, 7, 8, 10]),
            "east": face("side", uv=[6, 7, 9, 10]), "west": face("side", uv=[6, 7, 9, 10]),
            "up": face("top", uv=[0, 6, 1, 9]), "down": face("bottom", uv=[0, 6, 1, 9])}, name)

    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "isekai_mahou:block/witch_cauldron_side",
            "side": "isekai_mahou:block/witch_cauldron_side",
            "top": "isekai_mahou:block/witch_cauldron_top",
            "inside": "isekai_mahou:block/witch_cauldron_inner",
            "bottom": "isekai_mahou:block/witch_cauldron_bottom",
        },
        "elements": els,
    }
    write_json(os.path.join(A, "models/block/witch_cauldron.json"), model)
    write_json(os.path.join(A, "models/item/witch_cauldron.json"), {"parent": "isekai_mahou:block/witch_cauldron"})
    write_json(os.path.join(A, "blockstates/witch_cauldron.json"), {"variants": {
        "lit=false": {"model": "isekai_mahou:block/witch_cauldron"},
        "lit=true": {"model": "isekai_mahou:block/witch_cauldron"}}})


# =====================================================================================
#  GUI: Livro de Receitas (256x256: livro 256x170 + icones)
# =====================================================================================
def book_gui():
    c = Canvas(256, 256)
    W, H = 256, 170
    cover, cover_d, cover_l = hex_rgba("3A1846"), hex_rgba("220E2A"), hex_rgba("552466")
    gold = hex_rgba("C9A23A")
    for y in range(H):
        for x in range(W):
            # cantos arredondados
            cx = min(x, W - 1 - x)
            cy = min(y, H - 1 - y)
            if cx < 3 and cy < 3 and (3 - cx) ** 2 + (3 - cy) ** 2 > 10:
                continue
            edge = min(cx, cy)
            col = cover_d if edge == 0 else (cover_l if edge == 1 and y < H // 2 else cover)
            c.set(x, y, col)
    c.noise(0.05, seed=21)
    # cantoneiras douradas
    for (x0, y0, sx, sy) in ((2, 2, 1, 1), (W - 3, 2, -1, 1), (2, H - 3, 1, -1), (W - 3, H - 3, -1, -1)):
        for i in range(6):
            c.set(x0 + sx * i, y0, gold)
            c.set(x0, y0 + sy * i, gold)
        c.set(x0 + sx, y0 + sy, lighten(gold, 0.4))

    paper, paper_e, spine = hex_rgba("EFE2C2"), hex_rgba("DCCB9E"), hex_rgba("C2AD7E")

    def page(x0, x1, spine_side):
        for y in range(6, 163):
            for x in range(x0, x1 + 1):
                d_edge = min(x - x0, x1 - x, y - 6, 162 - y)
                col = paper if d_edge > 2 else mix(paper_e, paper, d_edge / 3.0)
                ds = (x1 - x) if spine_side == "right" else (x - x0)
                if ds < 6:
                    col = mix(spine, col, ds / 6.0)
                c.set(x, y, col)

    page(8, 127, "right")
    page(128, 247, "left")
    # folhas empilhadas embaixo
    for x in range(10, 246):
        c.set(x, 163, hex_rgba("D8C79D"))
        c.set(x, 164, hex_rgba("BFA978"))
    for x in range(126, 130):
        for y in range(5, 165):
            c.set(x, y, mix(hex_rgba("8C7A55"), c.get(x, y), 0.5))
    # manchas suaves de papel velho
    rnd = random.Random(5)
    for _ in range(140):
        x, y = rnd.randint(10, 245), rnd.randint(8, 160)
        p = c.get(x, y)
        if p[3] and 124 > abs(x - 128) > 4:
            c.set(x, y, darken(p, 0.04))
    # vinhetas: luazinha e estrela nos cantos das paginas
    faded = (122, 90, 150, 120)
    for (x, y) in ((116, 12), (117, 11), (117, 13), (118, 12)):
        c.set(x, y, faded)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 1), (-1, 0)):
        c.set(238 + dx, 150 + dy, faded)
    # linha-guia sob o titulo da pagina direita ("Ingredientes")
    for x in range(138, 238):
        if x % 2 == 0:
            c.set(x, 22, (120, 92, 70, 70))

    # ---- Icones ----
    lit_col, dark_col = hex_rgba("F4EFC8"), hex_rgba("3C3A52")
    for k in range(8):
        t = math.cos(2 * math.pi * k / 8)
        for dim, oy in ((False, 172), (True, 184)):
            ox = k * 12
            for y in range(11):
                for x in range(11):
                    u, v = (x + 0.5 - 5.5) / 5.0, (y + 0.5 - 5.5) / 5.0
                    if u * u + v * v > 1.0:
                        continue
                    s = math.sqrt(max(0.0, 1 - v * v))
                    lit = (u < t * s) if k <= 4 else (u > -t * s)
                    col = lit_col if lit else dark_col
                    if lit and (x * 7 + y * 3) % 11 == 0:
                        col = darken(col, 0.12)  # crateras
                    if dim:
                        g = int(sum(col[:3]) / 3)
                        col = (g // 2 + 40, g // 2 + 36, g // 2 + 30, 120)
                    c.set(ox + x, oy + y, col)
            # contorno
            for y in range(11):
                for x in range(11):
                    u, v = (x + 0.5 - 5.5) / 5.0, (y + 0.5 - 5.5) / 5.0
                    d = u * u + v * v
                    if 1.0 < d <= 1.45 and not dim:
                        c.set(ox + x, oy + y, (60, 40, 70, 200))

    flame = [
        "....a....",
        "....a....",
        "...aba...",
        "...abba..",
        "..abbba..",
        "..abcba..",
        ".abccba..",
        ".abcccba.",
        ".abcdcba.",
        "..abcba..",
        "...aaa...",
    ]
    c.from_rows(flame, {"a": hex_rgba("B83A10"), "b": hex_rgba("F07A1A"), "c": hex_rgba("FFC23A"), "d": hex_rgba("FFF2B0")},
                ox=100, oy=172)
    c.from_rows(flame, {"a": (110, 96, 84, 200), "b": (150, 136, 120, 120), "c": (170, 160, 145, 90), "d": (190, 180, 165, 70)},
                ox=100, oy=184)
    spoon_rows = [
        "........ww.",
        ".......wWWw",
        ".......wPPw",
        "......wwPw.",
        ".....ww.w..",
        "....ww.....",
        "...ww......",
        "..ww.......",
        ".ww........",
        "ww.........",
        "w..........",
    ]
    c.from_rows(spoon_rows, {"w": hex_rgba("6B4A2B"), "W": hex_rgba("A57A4A"), "P": hex_rgba("6E3A9A")}, ox=112, oy=172)
    star = [
        ".....a.....",
        ".....a.....",
        "....aba....",
        "aaaabbbaaaa",
        ".abbbcbbba.",
        "..abbcbba..",
        "..abbbbba..",
        ".abba.abba.",
        ".aba...aba.",
        "aa.......aa",
        "...........",
    ]
    c.from_rows(star, {"a": hex_rgba("3A2A6A"), "b": hex_rgba("7A6AD0"), "c": hex_rgba("DCD6FF")}, ox=124, oy=172)
    c.save(tex("gui/witch_recipe_book"))


def mana_flow_icon():
    c = Canvas(18, 18)
    blue, light = hex_rgba("3F6BFF"), hex_rgba("9FC0FF")
    for i in range(40):
        a = i * 0.32
        r = 1.0 + i * 0.17
        x, y = 9 + math.cos(a) * r, 9 + math.sin(a) * r
        c.set(int(x), int(y), mix(light, blue, i / 40.0))
        c.set(int(x) + 1, int(y), mix(light, blue, i / 40.0))
    c.circle(9, 9, 2.2, hex_rgba("DDE8FF"))
    c.outline(hex_rgba("1A2A66"))
    c.save(tex("mob_effect/witch_mana_flow"))


# =====================================================================================
#  Dados: receitas do caldeirao, crafting, loot, tags
# =====================================================================================
M = "isekai_mahou:"
ALL = list(range(8))
WITCH_RECIPES = {
    # ---- Elixires ----
    "elixir_vitality": dict(ingredients=[M + "witch_mandrake_root", "minecraft:sweet_berries", "minecraft:apple"],
                            stirs=2, heat="low", result=M + "witch_elixir_vitality", count=3, xp=8, color="#E0465A", element="water"),
    "elixir_owl_eyes": dict(ingredients=[M + "witch_bat_wing", "minecraft:golden_carrot", "minecraft:spider_eye"],
                            stirs=1, heat="low", result=M + "witch_elixir_owl_eyes", count=3, xp=8, color="#F2C14E", element="shadow"),
    "elixir_tides": dict(ingredients=["minecraft:kelp", M + "witch_toad_eye", "minecraft:pufferfish"],
                         stirs=3, heat="low", result=M + "witch_elixir_tides", count=3, xp=10, color="#2FB8C9", element="water"),
    "elixir_feather": dict(ingredients=["minecraft:feather", "minecraft:rabbit_foot", M + "witch_moonpetal"],
                           stirs=2, heat="low", moon=[5, 6, 7, 0, 1], result=M + "witch_elixir_feather", count=3, xp=10,
                           color="#DDE7F5", element="water"),
    "elixir_mana": dict(ingredients=[M + "witch_moonpetal", "minecraft:glow_berries", "minecraft:lapis_lazuli", M + "witch_mandrake_root"],
                        stirs=3, heat="medium", moon=[6, 7, 0, 1], result=M + "witch_elixir_mana", count=3, xp=14, color="#5A7BFF",
                        element="water"),
    "elixir_shadow_veil": dict(ingredients=[M + "witch_bat_wing", "minecraft:ink_sac", "minecraft:fermented_spider_eye",
                                            "minecraft:phantom_membrane"],
                               stirs=4, heat="medium", moon=[3, 4, 5], result=M + "witch_elixir_shadow_veil", count=3, xp=15,
                               color="#3B2550", element="shadow"),
    "elixir_stone_skin": dict(ingredients=[M + "witch_grave_dust", "minecraft:iron_nugget", "minecraft:flint", M + "witch_mandrake_root"],
                              stirs=5, heat="high", moon=[1, 2, 3], result=M + "witch_elixir_stone_skin", count=3, xp=15,
                              color="#8C8A7E", element="water"),
    # ---- Maldicoes ----
    "curse_frailty": dict(ingredients=[M + "witch_bat_wing", "minecraft:spider_eye", "minecraft:bone_meal"],
                          stirs=2, heat="low", result=M + "bottled_curse_frailty", count=3, xp=8, color="#8E9A5B", element="shadow"),
    "curse_hex": dict(ingredients=[M + "witch_grave_dust", M + "witch_toad_eye", "minecraft:spider_eye", "minecraft:fermented_spider_eye"],
                      stirs=3, heat="medium", moon=[3, 4, 5], night=True, result=M + "bottled_curse_hex", count=3, xp=14,
                      color="#6A1F8A", element="shadow"),
    "curse_mire": dict(ingredients=["minecraft:slime_ball", M + "witch_mandrake_root", "minecraft:mud", M + "witch_toad_eye"],
                       stirs=4, heat="medium", result=M + "bottled_curse_mire", count=3, xp=12, color="#5B4632", element="shadow"),
    "curse_blight": dict(ingredients=[M + "witch_grave_dust", "minecraft:poisonous_potato", "minecraft:rotten_flesh", "minecraft:wither_rose"],
                         stirs=6, heat="high", moon=[4], night=True, result=M + "bottled_curse_blight", count=2, xp=20,
                         color="#4E6B1F", element="shadow"),
    # ---- Revelacoes ----
    "revelation_1": dict(ingredients=[M + "witch_mandrake_root", M + "witch_moonpetal", "minecraft:amethyst_shard"],
                         stirs=3, heat="low", moon=[6, 7, 0, 1, 2], night=True, result=M + "revelation_potion_1", count=1, xp=20,
                         color="#B57EDC"),
    "revelation_2": dict(ingredients=[M + "witch_toad_eye", M + "witch_moonpetal", M + "essence_water", "minecraft:ender_pearl"],
                         stirs=4, heat="medium", moon=[7, 0, 1], night=True, result=M + "revelation_potion_2", count=1, xp=30,
                         color="#4F7BFF"),
    "revelation_3": dict(ingredients=[M + "witch_grave_dust", M + "witch_bat_wing", M + "essence_shadow", "minecraft:ghast_tear",
                                      "minecraft:crying_obsidian"],
                         stirs=5, heat="high", moon=[4], night=True, result=M + "revelation_potion_3", count=1, xp=45, color="#7A2FC0"),
    "revelation_4": dict(ingredients=[M + "witch_moonpetal", M + "essence_water", M + "essence_shadow", "minecraft:echo_shard",
                                      "minecraft:heart_of_the_sea", M + "mana_crystal"],
                         stirs=7, heat="medium", moon=[0], night=True, result=M + "revelation_potion_4", count=1, xp=60,
                         color="#2A4F9A"),
    "revelation_5": dict(ingredients=[M + "guardian_core_water", M + "guardian_core_shadow", "minecraft:nether_star",
                                      "minecraft:dragon_breath", M + "witch_moonpetal", "minecraft:totem_of_undying"],
                         stirs=9, heat="high", moon=[4], night=True, result=M + "revelation_potion_5", count=1, xp=100,
                         color="#C06AA0"),
}


def data_files():
    for rid, r in WITCH_RECIPES.items():
        out = {"ingredients": r["ingredients"], "stirs": r["stirs"], "heat": r["heat"]}
        if "moon" in r:
            out["moon_phases"] = sorted(r["moon"])
        if r.get("night"):
            out["night"] = True
        out["result"] = {"id": r["result"], "count": r["count"]}
        out["xp"] = r["xp"]
        out["color"] = r["color"]
        if "element" in r:
            out["element"] = r["element"]
        write_json(os.path.join(D, "witch_recipes", rid + ".json"), out)

    # sequencias unicas (o loader ignora repetidas)
    seqs = [tuple(r["ingredients"]) for r in WITCH_RECIPES.values()]
    assert len(seqs) == len(set(seqs)), "sequencias de ingredientes repetidas"

    write_json(os.path.join(D, "recipe/witch_cauldron.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:cauldron"}, {"item": "minecraft:amethyst_shard"}, {"item": "minecraft:spider_eye"},
                        {"item": "minecraft:ink_sac"}],
        "result": {"id": M + "witch_cauldron", "count": 1}})
    write_json(os.path.join(D, "recipe/ritual_spoon.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["  B", " S ", "S  "],
        "key": {"B": {"item": "minecraft:bowl"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": M + "ritual_spoon", "count": 1}})
    write_json(os.path.join(D, "recipe/witch_recipe_book.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:ink_sac"}, {"item": "minecraft:feather"}],
        "result": {"id": M + "witch_recipe_book", "count": 1}})
    write_json(os.path.join(D, "recipe/witch_grave_dust.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:bone_meal"}, {"item": "minecraft:rotten_flesh"}, {"item": "minecraft:gravel"}],
        "result": {"id": M + "witch_grave_dust", "count": 2}})

    write_json(os.path.join(D, "loot_table/blocks/witch_cauldron.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": M + "witch_cauldron"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "isekai_mahou:blocks/witch_cauldron"})

    # Fontes de calor (tags proprias do modulo)
    write_json(os.path.join(D, "tags/block/witch_heat_low.json"), {"values": ["minecraft:campfire"]})
    write_json(os.path.join(D, "tags/block/witch_heat_medium.json"), {"values": ["minecraft:fire", "minecraft:soul_campfire"]})
    write_json(os.path.join(D, "tags/block/witch_heat_high.json"), {"values": ["minecraft:soul_fire", "minecraft:magma_block",
                                                                               "minecraft:lava"]})
    # Tag compartilhada (merge): caldeirao quebra com picareta
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [M + "witch_cauldron"]})


# =====================================================================================
#  Traducoes
# =====================================================================================
def lang():
    pt, en = {}, {}

    def k(key, p, e):
        pt[key] = p
        en[key] = e

    k("block.isekai_mahou.witch_cauldron", "Caldeirão Ritual", "Ritual Cauldron")
    k("item.isekai_mahou.ritual_spoon", "Colher Ritual", "Ritual Spoon")
    k("item.isekai_mahou.witch_recipe_page", "Página de Receita", "Recipe Page")
    k("item.isekai_mahou.witch_recipe_page.named", "Página de Receita: %s", "Recipe Page: %s")
    k("item.isekai_mahou.witch_recipe_page.torn", "Página de Receita Rasgada", "Torn Recipe Page")
    k("item.isekai_mahou.witch_recipe_book", "Livro de Receitas da Bruxa", "Witch's Recipe Book")
    k("item.isekai_mahou.witch_mandrake_root", "Raiz de Mandrágora", "Mandrake Root")
    k("item.isekai_mahou.witch_moonpetal", "Pétala Lunar", "Moonpetal")
    k("item.isekai_mahou.witch_bat_wing", "Asa de Morcego", "Bat Wing")
    k("item.isekai_mahou.witch_toad_eye", "Olho de Sapo", "Toad Eye")
    k("item.isekai_mahou.witch_grave_dust", "Pó de Túmulo", "Grave Dust")
    k("item.isekai_mahou.witch_elixir_vitality", "Elixir da Vitalidade", "Elixir of Vitality")
    k("item.isekai_mahou.witch_elixir_owl_eyes", "Elixir dos Olhos de Coruja", "Owl Eyes Elixir")
    k("item.isekai_mahou.witch_elixir_tides", "Elixir das Marés", "Elixir of the Tides")
    k("item.isekai_mahou.witch_elixir_feather", "Elixir da Pluma", "Featherfall Elixir")
    k("item.isekai_mahou.witch_elixir_mana", "Elixir de Mana", "Mana Elixir")
    k("item.isekai_mahou.witch_elixir_shadow_veil", "Elixir do Véu de Sombras", "Shadow Veil Elixir")
    k("item.isekai_mahou.witch_elixir_stone_skin", "Elixir da Pele de Pedra", "Stoneskin Elixir")
    k("item.isekai_mahou.bottled_curse_frailty", "Maldição Engarrafada: Fragilidade", "Bottled Curse: Frailty")
    k("item.isekai_mahou.bottled_curse_hex", "Maldição Engarrafada: Corrupção", "Bottled Curse: Hex")
    k("item.isekai_mahou.bottled_curse_mire", "Maldição Engarrafada: Lamaçal", "Bottled Curse: Mire")
    k("item.isekai_mahou.bottled_curse_blight", "Maldição Engarrafada: Praga", "Bottled Curse: Blight")
    roman = ["I", "II", "III", "IV", "V"]
    for i, r in enumerate(roman, 1):
        k("item.isekai_mahou.revelation_potion_%d" % i, "Poção de Revelação " + r, "Potion of Revelation " + r)
    k("entity.isekai_mahou.witch_curse_bottle", "Maldição Engarrafada", "Bottled Curse")
    k("effect.isekai_mahou.witch_mana_flow", "Fluxo de Mana", "Mana Flow")

    recipes = {
        "elixir_vitality": ("Elixir da Vitalidade", "Regenera, protege e sacia. Fácil de preparar em fogo baixo.",
                            "Elixir of Vitality", "Regenerates, shields and nourishes. Easy to brew over low heat."),
        "elixir_owl_eyes": ("Elixir dos Olhos de Coruja", "Enxergue no escuro e revele as criaturas à sua volta.",
                            "Owl Eyes Elixir", "See in the dark and reveal the creatures around you."),
        "elixir_tides": ("Elixir das Marés", "Respire e nade como as criaturas do mar.",
                         "Elixir of the Tides", "Breathe and swim like the creatures of the sea."),
        "elixir_feather": ("Elixir da Pluma", "Leve como uma pena: pule alto e caia devagar. Prefere luas claras.",
                           "Featherfall Elixir", "Light as a feather: jump high and fall slowly. Prefers bright moons."),
        "elixir_mana": ("Elixir de Mana", "Restaura a mana e acelera a regeneração dela. Prefere luas cheias.",
                        "Mana Elixir", "Restores mana and speeds up its regeneration. Prefers full moons."),
        "elixir_shadow_veil": ("Elixir do Véu de Sombras", "Desapareça nas sombras. Só se completa em luas escuras.",
                               "Shadow Veil Elixir", "Vanish into the shadows. Only completes under dark moons."),
        "elixir_stone_skin": ("Elixir da Pele de Pedra", "Pele dura como rocha, passos pesados. Exige fogo alto.",
                              "Stoneskin Elixir", "Skin hard as rock, heavy steps. Needs high heat."),
        "curse_frailty": ("Maldição da Fragilidade", "Enfraquece e expõe os inimigos atingidos.",
                          "Curse of Frailty", "Weakens and exposes the enemies it hits."),
        "curse_hex": ("Maldição da Corrupção", "Corrompe a carne dos inimigos. Só em noites de lua escura.",
                      "Hex Curse", "Corrupts the flesh of enemies. Only on dark-moon nights."),
        "curse_mire": ("Maldição do Lamaçal", "Prende os inimigos na lama e os deixa encharcados.",
                       "Mire Curse", "Traps enemies in mud and leaves them soaked."),
        "curse_blight": ("Maldição da Praga", "Veneno, fome e definhamento, com uma névoa que permanece. Só na lua nova.",
                         "Blight Curse", "Poison, hunger and withering, with a lingering mist. New moon only."),
        "revelation_1": ("Revelação I · Águas Despertas", "A primeira revelação: desperta a Água e ensina as primeiras Maldições.",
                         "Revelation I · Awakened Waters", "The first revelation: awakens Water and teaches the first Curses."),
        "revelation_2": ("Revelação II · Maré Lunar", "Pede luas quase cheias e a essência da Água.",
                         "Revelation II · Lunar Tide", "Requires near-full moons and the essence of Water."),
        "revelation_3": ("Revelação III · Sombra Desperta", "Só na lua nova, em fogo alto. Desperta a Sombra.",
                         "Revelation III · Shadow Awakened", "New moon only, over high heat. Awakens Shadow."),
        "revelation_4": ("Revelação IV · Eco do Abismo", "Só na lua cheia. Ingredientes das profundezas do mundo.",
                         "Revelation IV · Echo of the Abyss", "Full moon only. Ingredients from the depths of the world."),
        "revelation_5": ("Revelação V · Coroa da Bruxa",
                         "A revelação final: os Núcleos dos Guardiões da Água e da Sombra, sob a lua nova.",
                         "Revelation V · Witch's Crown",
                         "The final revelation: the Guardian Cores of Water and Shadow, under the new moon."),
    }
    for rid, (pn, pd, en_n, en_d) in recipes.items():
        assert rid in WITCH_RECIPES
        k("witch_recipe.isekai_mahou." + rid, pn, en_n)
        k("witch_recipe.isekai_mahou." + rid + ".desc", pd, en_d)

    for hid, pn, en_n in (("none", "Nenhum", "None"), ("low", "Baixo", "Low"), ("medium", "Médio", "Medium"), ("high", "Alto", "High")):
        k("witch_heat.isekai_mahou." + hid, pn, en_n)
    k("screen.isekai_mahou.witch_book.heat_none", "Sem calor: acenda fogo embaixo do caldeirão.",
      "No heat: light a fire under the cauldron.")
    k("screen.isekai_mahou.witch_book.heat_low", "Baixo: fogueira acesa embaixo do caldeirão.",
      "Low: a lit campfire under the cauldron.")
    k("screen.isekai_mahou.witch_book.heat_medium", "Médio: fogo ou fogueira de alma embaixo.",
      "Medium: fire or a soul campfire underneath.")
    k("screen.isekai_mahou.witch_book.heat_high", "Alto: fogo de alma, bloco de magma ou lava embaixo.",
      "High: soul fire, a magma block or lava underneath.")

    moons = [("Lua Cheia", "Full Moon"), ("Gibosa Minguante", "Waning Gibbous"), ("Quarto Minguante", "Last Quarter"),
             ("Lua Minguante", "Waning Crescent"), ("Lua Nova", "New Moon"), ("Lua Crescente", "Waxing Crescent"),
             ("Quarto Crescente", "First Quarter"), ("Gibosa Crescente", "Waxing Gibbous")]
    for i, (pn, en_n) in enumerate(moons):
        k("witch_moon.isekai_mahou.%d" % i, pn, en_n)

    msg = "message.isekai_mahou.witch."
    k(msg + "added", "%2$sº ingrediente: %1$s", "Ingredient #%2$s: %1$s")
    k(msg + "cleaned", "O caldeirão foi esvaziado.", "The cauldron was emptied.")
    k(msg + "cold", "O caldeirão está frio: acenda fogo embaixo dele.", "The cauldron is cold: light a fire under it.")
    k(msg + "discovered", "Receita descoberta e anotada no livro: %s", "Recipe discovered and written in your book: %s")
    k(msg + "page_known", "Você já conhece esta receita.", "You already know this recipe.")
    k(msg + "page_nothing", "Nada de novo nesta página: você já conhece tudo o que ela poderia ensinar.",
      "Nothing new on this page: you already know everything it could teach.")
    k(msg + "page_unreadable", "A tinta está borrada: esta receita não existe neste mundo.",
      "The ink is smudged: this recipe doesn't exist in this world.")
    k(msg + "ready", "Pronto: %s (%s doses). Use frascos de vidro.", "Ready: %s (%s doses). Use glass bottles.")
    k(msg + "recipe_learned", "Receita aprendida: %s", "Recipe learned: %s")
    k(msg + "revelation", "A Revelação %s inunda sua mente com segredos antigos...",
      "Revelation %s floods your mind with ancient secrets...")
    k(msg + "revelation_absorbed", "Você já absorveu a Revelação %s.", "You have already absorbed Revelation %s.")
    k(msg + "revelation_no_glyph", "(Nenhum glifo novo de Maldição neste grau.)", "(No new Curse glyph at this grade.)")
    k(msg + "revelation_subtitle", "Os segredos do caldeirão se revelam", "The cauldron's secrets unveil")
    k(msg + "revelation_title", "Revelação %s", "Revelation %s")
    k(msg + "revelation_too_strong", "Sua mente ainda não suporta esta revelação. Beba antes a Revelação %s.",
      "Your mind can't bear this revelation yet. Drink Revelation %s first.")
    k(msg + "ruined", "A mistura talhou! %s", "The brew curdled! %s")
    k(msg + "ruin.order", "Ingrediente fora de ordem.", "Ingredient out of order.")
    k(msg + "ruin.over_stirred", "Mexeu demais.", "Stirred too much.")
    k(msg + "ruin.heat_low", "O fogo estava fraco demais.", "The fire was too weak.")
    k(msg + "ruin.heat_high", "O fogo estava forte demais e queimou tudo.", "The fire was too strong and scorched it.")
    k(msg + "ruin.moon", "A lua de hoje não favorece esta receita.", "Tonight's moon does not favor this recipe.")
    k(msg + "ruin.night", "Esta receita só se completa sob o céu noturno.", "This recipe only completes under the night sky.")
    k(msg + "ruin.cold", "Ficou sem fogo e esfriou.", "It went without fire and cooled down.")
    k(msg + "sludge", "Só restou lodo preto. Limpe com um balde vazio.", "Only black sludge remains. Clean it with an empty bucket.")
    k(msg + "status_brewing", "Preparo: %s ingredientes, %s mexidas · Calor: %s", "Brewing: %s ingredients, %s stirs · Heat: %s")
    k(msg + "status_empty", "Caldeirão vazio: encha com um balde de água. Calor: %s", "Empty cauldron: fill it with a water bucket. Heat: %s")
    k(msg + "status_water", "Água pronta para os ingredientes. Calor: %s", "Water ready for ingredients. Heat: %s")
    k(msg + "status_ready", "Pronto: %s (%s doses). Use frascos de vidro.", "Ready: %s (%s doses). Use glass bottles.")
    k(msg + "stirred", "Mexida %s · Calor: %s", "Stir %s · Heat: %s")
    k(msg + "full", "O caldeirão já está cheio.", "The cauldron is already full.")
    k(msg + "use_bottles", "A poção está pronta: use frascos de vidro.", "The potion is ready: use glass bottles.")
    k(msg + "not_ready", "Ainda não há nada pronto para engarrafar.", "There is nothing ready to bottle yet.")
    k(msg + "need_water", "Encha o caldeirão com um balde de água primeiro.", "Fill the cauldron with a water bucket first.")

    k("command.isekai_mahou.witch.learnall", "%s receitas de bruxa aprendidas.", "%s witch recipes learned.")
    k("command.isekai_mahou.witch.rank", "Rank do ritual da Bruxa definido para %s.", "Witch ritual rank set to %s.")

    sc = "screen.isekai_mahou.witch_book."
    k(sc + "doses", "%s dose(s)", "%s dose(s)")
    k(sc + "empty", "Nenhuma receita conhecida ainda.", "No known recipes yet.")
    k(sc + "empty_hint", "Leia Páginas de Receita (clique direito) para anotá-las aqui. Páginas rasgadas aparecem em baús, "
                         "com bruxas e com o mercador errante. Preparos bem-sucedidos também são anotados.",
      "Read Recipe Pages (right click) to write them here. Torn pages show up in chests, on witches and with the "
      "wandering trader. Successful brews are written down too.")
    k(sc + "footer", "Página %s/%s · Receitas %s/%s", "Page %s/%s · Recipes %s/%s")
    k(sc + "heat", "Calor: %s", "Heat: %s")
    k(sc + "heat_guide", "Fontes de calor", "Heat sources")
    k(sc + "ingredients", "Ingredientes, em ordem:", "Ingredients, in order:")
    k(sc + "kind_curse", "Maldição Engarrafada", "Bottled Curse")
    k(sc + "kind_elixir", "Elixir", "Elixir")
    k(sc + "kind_revelation", "Revelação · grau %s", "Revelation · grade %s")
    k(sc + "night", "Só se completa à noite", "Only completes at night")
    k(sc + "stirs", "%s mexidas", "%s stirs")
    k(sc + "stirs_tip", "Mexa com a Colher Ritual depois dos ingredientes. Mexer demais estraga!",
      "Stir with the Ritual Spoon after the ingredients. Stirring too much ruins it!")
    k(sc + "today", "Hoje: %s", "Today: %s")
    k(sc + "moon_ok", "Permitida", "Allowed")
    k(sc + "moon_no", "Não permitida", "Not allowed")

    tt = "tooltip.isekai_mahou."
    k(tt + "bottled_curse", "Arremesse: amaldiçoa quem estiver a até %s blocos", "Throw: curses everyone within %s blocks")
    k(tt + "bottled_curse.lingering", "Deixa uma névoa amaldiçoada no chão", "Leaves a cursed mist on the ground")
    k(tt + "revelation.absorbed", "Já absorvida", "Already absorbed")
    k(tt + "revelation.awaken_shadow", "Desperta o elemento Sombra", "Awakens the Shadow element")
    k(tt + "revelation.awaken_water", "Desperta o elemento Água", "Awakens the Water element")
    k(tt + "revelation.glyphs", "Ensina os glifos de Maldição do grau %s", "Teaches the grade %s Curse glyphs")
    k(tt + "revelation.locked", "Bloqueada: beba antes a Revelação %s", "Locked: drink Revelation %s first")
    k(tt + "revelation.lore", "Um gole de verdade que não pode ser esquecida.", "A sip of truth that cannot be forgotten.")
    k(tt + "revelation.ready", "Pronta para ser bebida", "Ready to drink")
    k(tt + "revelation.tier", "Revelação de grau %s", "Revelation of grade %s")
    k(tt + "ritual_spoon", "Clique direito no Caldeirão Ritual para mexer.", "Right click the Ritual Cauldron to stir.")
    k(tt + "ritual_spoon.warn", "Conte as mexidas: mexer demais estraga o preparo.", "Count your stirs: stirring too much ruins the brew.")
    k(tt + "witch_elixir.mana", "Restaura %s%% da mana", "Restores %s%% of your mana")
    k(tt + "witch_elixir.reveal", "Revela as criaturas a até %s blocos", "Reveals creatures within %s blocks")
    k(tt + "witch_recipe_book", "Clique direito para ler as receitas conhecidas.", "Right click to read your known recipes.")
    k(tt + "witch_recipe_page.known", "Você já conhece esta receita", "You already know this recipe")
    k(tt + "witch_recipe_page.learn", "Clique direito para aprender", "Right click to learn")
    k(tt + "witch_recipe_page.torn", "Clique direito para decifrar uma receita desconhecida", "Right click to decipher an unknown recipe")

    assert pt.keys() == en.keys()
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))


def main():
    spoon()
    recipe_page()
    recipe_book()
    mandrake()
    moonpetal()
    bat_wing()
    toad_eye()
    grave_dust()
    elixir("witch_elixir_vitality", "E0465A", "F2D25A")
    elixir("witch_elixir_owl_eyes", "F2C14E", "5A3A1E")
    elixir("witch_elixir_tides", "2FB8C9", "E8F6FF")
    elixir("witch_elixir_feather", "DDE7F5", "8C9EDB")
    elixir("witch_elixir_mana", "5A7BFF", "B9C9FF")
    elixir("witch_elixir_shadow_veil", "3B2550", "9B5BD6")
    elixir("witch_elixir_stone_skin", "8C8A7E", "5A5650")
    curse("bottled_curse_frailty", "8E9A5B", "6E5A3A")
    curse("bottled_curse_hex", "6A1F8A", "3A1046")
    curse("bottled_curse_mire", "5B4632", "3A2A1A")
    curse("bottled_curse_blight", "4E6B1F", "2E3A12")
    for t in range(1, 6):
        revelation(t)
    cauldron_textures()
    brew_texture()
    cauldron_model()
    book_gui()
    mana_flow_icon()
    data_files()
    lang()
    print("ritual_witch: assets gerados")


if __name__ == "__main__":
    main()
