"""Gera os assets/dados do modulo "farm" (Construcao Arcana, Plantas de Casa, fazendas magicas, Pedestal Elemental).

Uso (na raiz do projeto):  py -I tools/gen/farm.py      (as traducoes ficam em tools/gen/farm_lang.py)

Gera: texturas (varinha, 5 plantas, gerador, arvore acelerada, criadouro, lago com agua animada, 6 pedestais elementais,
canteiro de obras, circulo de runas do pedestal, tela das fazendas), modelos de bloco/item, blockstates, loot tables,
receitas (o pedestal tem receita cara com condicao de item existir) e as tags de ferramenta (merge).
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
MERGE = os.path.join(ROOT, "src/main/merge/farm")
NS = "isekai_mahou"
CLEAR = (0, 0, 0, 0)

ELEMENTS = {
    "fire": ("E8552B", "FFB347"),
    "water": ("2F7FE0", "8FD3FF"),
    "earth": ("7A9A3A", "C9D66B"),
    "wind": ("6FD9C2", "E6FFF8"),
    "light": ("F2CF4A", "FFF6C2"),
    "shadow": ("6A3FB0", "C9A4FF"),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(c, rel):
    c.save(os.path.join(A, "textures", rel + ".png"))


def jitter(c, x0, y0, x1, y1, base, seed, amount=0.08):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            f = (rnd.random() - 0.5) * 2 * amount
            c.set(x, y, lighten(base, f) if f > 0 else darken(base, -f))


def bricks(c, seed, dark, mid, light, mortar, rows=((0, 3), (4, 7), (8, 11), (12, 15))):
    """Tijolos alternados com rejunte e bisel."""
    rnd = random.Random(seed)
    for i, (y0, y1) in enumerate(rows):
        off = 0 if i % 2 == 0 else 4
        for bx in range(-8, 16, 8):
            x0, x1 = bx + off, bx + off + 7
            base = mix(mid, light if rnd.random() < 0.3 else dark, rnd.random() * 0.4)
            for y in range(y0, y1 + 1):
                for x in range(max(0, x0), min(15, x1) + 1):
                    f = (rnd.random() - 0.5) * 0.12
                    p = lighten(base, f) if f > 0 else darken(base, -f)
                    if y == y0 or x == x0:
                        p = lighten(p, 0.18)
                    if y == y1 or x == x1:
                        p = darken(p, 0.25)
                    c.set(x, y, p)
            for y in range(y0, y1 + 1):
                if 0 <= x1 <= 15:
                    c.set(x1, y, mortar)
        for x in range(16):
            c.set(x, y1, mortar)


def planks(c, seed, base):
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            row = y // 4
            p = lighten(base, 0.05) if row % 2 == 0 else base
            f = (rnd.random() - 0.5) * 0.12
            p = lighten(p, f) if f > 0 else darken(p, -f)
            if y % 4 == 3:
                p = darken(base, 0.35)
            if (x + row * 5) % 16 == 0:
                p = darken(base, 0.3)
            c.set(x, y, p)


# ================================================================================================
#  Itens
# ================================================================================================
def wand():
    c = Canvas(16, 16)
    wood0, wood1, wood2 = hex_rgba("4A2E18"), hex_rgba("7A4E2A"), hex_rgba("A8743F")
    gold0, gold1 = hex_rgba("9A6E1A"), hex_rgba("F2C46B")
    for i in range(10):
        x, y = 2 + i, 13 - i
        c.set(x, y, wood1)
        c.set(x + 1, y, wood0)
        c.set(x, y - 1, wood2) if i % 3 == 0 else None
    for x, y in ((4, 11), (5, 10), (8, 7), (9, 6)):
        c.set(x, y, gold1)
        c.set(x + 1, y, gold0)
    # Cristal (losango) com brilho
    g0, g1, g2, g3 = hex_rgba("1E6E9A"), hex_rgba("3FA8E0"), hex_rgba("7FD4FF"), hex_rgba("E6FAFF")
    gem = ["..a..", ".abc.", "abcdc", ".bcc.", "..b.."]
    pal = {"a": g1, "b": g2, "c": g1, "d": g3}
    c.from_rows(gem, pal, 10, 0)
    c.set(12, 1, g3)
    c.set(13, 2, g0)
    c.set(11, 4, g0)
    c.outline(hex_rgba("1A1020"))
    for x, y in ((15, 0), (8, 2), (14, 6)):
        c.set(x, y, hex_rgba("BFF0FF", 200))
    save(c, "item/construction_wand")


ICONS = {
    "small_house": ["...w...", "..www..", ".wwwww.", "wwwwwww", ".w.w.w.", ".w.ww..", ".wwwww."],
    "tower": ["w.w.w..", "wwwww..", ".www...", ".w.w...", ".www...", ".w.w...", ".www..."],
    "barn": ["...w...", ".wwwww.", "wwwwwww", "w.....w", "w.www.w", "w.w.w.w", "wwwwwww"],
    "bridge": [".......", "w.w.w.w", "wwwwwww", ".w...w.", "w.....w", ".......", "~~~~~~~"],
    "farm_plot": ["wwwwwww", "w.....w", "w,w,w,w", "w.....w", "w,w,w,w", "w.....w", "www.www"],
}


def blueprint(kind, accent):
    c = Canvas(16, 16)
    p0, p1, p2 = hex_rgba("163E7A"), hex_rgba("2259A8"), hex_rgba("3A78C8")
    edge = hex_rgba("D9E6F5")
    # folha
    for y in range(3, 14):
        for x in range(2, 14):
            c.set(x, y, p1 if (x + y) % 2 else p2)
    for x in range(2, 14):
        if x % 3 == 0:
            for y in range(4, 13):
                c.set(x, y, mix(p1, p2, 0.6))
    # rolos nas pontas
    for y in range(2, 15):
        c.set(1, y, edge)
        c.set(2, y, darken(edge, 0.25))
        c.set(14, y, edge)
        c.set(13, y, darken(edge, 0.25))
    c.set(1, 2, darken(edge, 0.4))
    c.set(14, 14, darken(edge, 0.4))
    white = hex_rgba("F4F8FF")
    pal = {"w": white, ",": hex_rgba("8FD46A"), "~": hex_rgba("7FD4FF")}
    c.from_rows(ICONS[kind], pal, 5, 5)
    # selo dourado com a cor da planta
    acc = hex_rgba(accent)
    c.set(11, 12, acc)
    c.set(12, 12, darken(acc, 0.3))
    c.set(11, 11, lighten(acc, 0.3))
    c.outline(hex_rgba("0D1A33"))
    save(c, f"item/blueprint_{kind}")


# ================================================================================================
#  Blocos
# ================================================================================================
DS0, DS1, DS2, DS3 = hex_rgba("1C1B22"), hex_rgba("2C2B35"), hex_rgba("3D3C49"), hex_rgba("54525F")


def generator_side(active):
    c = Canvas(16, 16)
    bricks(c, 11, DS1, DS2, DS3, DS0)
    # janela central com lava (esq.) e água (dir.)
    for y in range(3, 13):
        for x in range(3, 13):
            c.set(x, y, DS0)
    lava = [hex_rgba("B83A0F"), hex_rgba("E8701E"), hex_rgba("FFB347"), hex_rgba("FFE08A")]
    water = [hex_rgba("123E7A"), hex_rgba("2F6FC8"), hex_rgba("5FA8F0"), hex_rgba("BFE4FF")]
    rnd = random.Random(5)
    for y in range(4, 12):
        for x in range(4, 7):
            c.set(x, y, lava[min(3, rnd.randint(0, 2) + (1 if active else 0))])
        for x in range(9, 12):
            c.set(x, y, water[min(3, rnd.randint(0, 2) + (1 if active else 0))])
    # pedra nascendo no meio
    for y in range(6, 11):
        c.set(7, y, hex_rgba("8A8A92"))
        c.set(8, y, hex_rgba("6A6A72"))
    rune = hex_rgba("7FD4FF") if active else hex_rgba("4A5A78")
    for x, y in ((7, 4), (8, 4), (7, 12), (8, 12), (2, 7), (2, 8), (13, 7), (13, 8)):
        c.set(x, y, rune)
    save(c, "block/arcane_stone_generator_side" + ("_active" if active else ""))


def generator_top(active):
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, DS2, 21)
    c.outline_rect(0, 0, 15, 15, DS0)
    c.outline_rect(1, 1, 14, 14, DS3)
    for y in range(3, 13):
        for x in range(3, 13):
            c.set(x, y, DS0 if (x + y) % 2 == 0 else DS1)
    circle = hex_rgba("B896FF") if active else hex_rgba("5B4A80")
    for i in range(24):
        a = i / 24 * math.pi * 2
        c.set(int(round(7.5 + math.cos(a) * 4.5)), int(round(7.5 + math.sin(a) * 4.5)), circle)
    c.set(7, 7, lighten(circle, 0.5))
    c.set(8, 8, lighten(circle, 0.5))
    save(c, "block/arcane_stone_generator_top" + ("_active" if active else ""))


def generator_bottom():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, DS1, 33)
    c.outline_rect(0, 0, 15, 15, DS0)
    save(c, "block/arcane_stone_generator_bottom")


def planter_side():
    c = Canvas(16, 16)
    planks(c, 41, hex_rgba("7A5232"))
    gold = hex_rgba("D9A441")
    for x in range(16):
        c.set(x, 0, gold)
        c.set(x, 1, darken(gold, 0.35))
    for x in (3, 12):
        for y in range(3, 15):
            c.set(x, y, hex_rgba("4A2E18"))
    save(c, "block/accelerated_tree_side")


def planter_soil(active):
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, hex_rgba("3B2617"), 51, 0.15)
    rnd = random.Random(52)
    for _ in range(14):
        c.set(rnd.randint(1, 14), rnd.randint(1, 14), hex_rgba("5A3A22"))
    glow = hex_rgba("9BE07A") if active else hex_rgba("3E6A30")
    for i in range(20):
        a = i / 20 * math.pi * 2
        c.set(int(round(7.5 + math.cos(a) * 6)), int(round(7.5 + math.sin(a) * 6)), glow)
    for x, y in ((7, 1), (8, 14), (1, 8), (14, 7)):
        c.set(x, y, lighten(glow, 0.4))
    save(c, "block/accelerated_tree_soil" + ("_active" if active else ""))


def planter_base():
    c = Canvas(16, 16)
    bricks(c, 61, hex_rgba("3A3A40"), hex_rgba("55555E"), hex_rgba("70707A"), hex_rgba("26262B"))
    save(c, "block/accelerated_tree_base")


def hatchery_base():
    c = Canvas(16, 16)
    planks(c, 71, hex_rgba("9C7A4E"))
    straw = hex_rgba("E8C25A")
    rnd = random.Random(72)
    for _ in range(18):
        x, y = rnd.randint(0, 15), rnd.randint(0, 5)
        c.set(x, y, straw if rnd.random() < 0.6 else darken(straw, 0.25))
    save(c, "block/arcane_hatchery_side")


def hatchery_top():
    c = Canvas(16, 16)
    base = hex_rgba("D9B04A")
    rnd = random.Random(81)
    for y in range(16):
        for x in range(16):
            p = base
            if (x + y * 3 + rnd.randint(0, 2)) % 5 == 0:
                p = darken(base, 0.3)
            elif rnd.random() < 0.2:
                p = lighten(base, 0.25)
            c.set(x, y, p)
    # ninho com dois ovos
    c.circle(8, 8, 4.5, hex_rgba("8A6A2A"))
    c.circle(8, 8, 3.2, hex_rgba("B08A3A"))
    for cx, cy in ((7, 7), (10, 9)):
        c.set(cx, cy, hex_rgba("FFF6E6"))
        c.set(cx + 1, cy, hex_rgba("F0E2C8"))
        c.set(cx, cy + 1, hex_rgba("E6D2B0"))
    rune = hex_rgba("F2D06B")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, rune)
    save(c, "block/arcane_hatchery_top")


def hatchery_post():
    c = Canvas(16, 16)
    base = hex_rgba("7A5232")
    for y in range(16):
        for x in range(16):
            c.set(x, y, darken(base, 0.2) if x % 4 == 0 else lighten(base, 0.05 * ((y * 7 + x) % 3)))
    for y in (0, 15):
        for x in range(16):
            c.set(x, y, hex_rgba("D9A441"))
    save(c, "block/arcane_hatchery_post")


def pond_side():
    c = Canvas(16, 16)
    bricks(c, 91, hex_rgba("4A5560"), hex_rgba("64707C"), hex_rgba("84909C"), hex_rgba("2E363E"))
    rune = hex_rgba("4FA8E8")
    for x, y in ((4, 9), (5, 10), (6, 9), (10, 5), (11, 6), (12, 5)):
        c.set(x, y, rune)
    save(c, "block/magic_fish_pond_side")


def pond_rim():
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, hex_rgba("8A949E"), 101, 0.06)
    c.outline_rect(0, 0, 15, 15, hex_rgba("5A646E"))
    save(c, "block/magic_fish_pond_rim")


def pond_water():
    """Água mágica animada: 8 quadros (16x128) com ondas e brilhos."""
    frames = 8
    c = Canvas(16, 16 * frames)
    deep, mid, light, spark = hex_rgba("0F3E7A"), hex_rgba("1F64B8"), hex_rgba("4FA8E8"), hex_rgba("CFF2FF")
    for f in range(frames):
        ph = f / frames * math.pi * 2
        for y in range(16):
            for x in range(16):
                v = math.sin((x * 0.7) + ph) + math.sin((y * 0.55) - ph * 1.3) + math.sin((x + y) * 0.35 + ph * 0.5)
                t = (v + 3) / 6
                p = mix(deep, mid, min(1, t * 1.6)) if t < 0.6 else mix(mid, light, (t - 0.6) / 0.4)
                c.set(x, y + f * 16, p)
        rnd = random.Random(200 + f)
        for _ in range(3):
            c.set(rnd.randint(1, 14), rnd.randint(1, 14) + f * 16, spark)
    save(c, "block/magic_fish_pond_water")
    with open(os.path.join(A, "textures/block/magic_fish_pond_water.png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, fh, indent=2)


def pedestal_textures(el, dark_hex, light_hex):
    col, glow = hex_rgba(dark_hex), hex_rgba(light_hex)
    s0, s1, s2, s3 = hex_rgba("24222B"), hex_rgba("35333F"), hex_rgba("4A4756"), hex_rgba("615E6E")
    # base
    c = Canvas(16, 16)
    bricks(c, 300 + len(el), s1, s2, s3, s0)
    for x in range(16):
        c.set(x, 13, col if x % 3 else glow)
    save(c, f"block/elemental_pedestal_{el}_base")
    # coluna com veios de cristal
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, s2, 310, 0.08)
    for x in (0, 15):
        for y in range(16):
            c.set(x, y, s0)
    for x in (4, 11):
        for y in range(16):
            c.set(x, y, s1)
    rnd = random.Random(311 + len(el))
    x = 7
    for y in range(16):
        x = max(5, min(10, x + rnd.choice((-1, 0, 0, 1))))
        c.set(x, y, col)
        c.set(x + 1, y, glow if y % 4 == 0 else col)
    save(c, f"block/elemental_pedestal_{el}_column")
    # tampo com círculo
    c = Canvas(16, 16)
    jitter(c, 0, 0, 15, 15, s3, 320, 0.06)
    c.outline_rect(0, 0, 15, 15, s0)
    c.outline_rect(1, 1, 14, 14, s1)
    for i in range(28):
        a = i / 28 * math.pi * 2
        c.set(int(round(7.5 + math.cos(a) * 5)), int(round(7.5 + math.sin(a) * 5)), col)
    c.circle(8, 8, 2.2, glow)
    for i in range(4):
        a = i / 4 * math.pi * 2 + math.pi / 4
        c.set(int(round(7.5 + math.cos(a) * 3.5)), int(round(7.5 + math.sin(a) * 3.5)), glow)
    save(c, f"block/elemental_pedestal_{el}_top")


def essence_ring():
    c = Canvas(32, 32)
    white = (255, 255, 255, 255)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 13 <= d <= 15:
                c.set(x, y, (255, 255, 255, 230))
            elif 9.5 <= d <= 10.5:
                c.set(x, y, (255, 255, 255, 170))
    for i in range(8):
        a = i / 8 * math.pi * 2
        cx, cy = 16 + math.cos(a) * 11.8, 16 + math.sin(a) * 11.8
        c.circle(cx, cy, 1.4, white)
    for i in range(3):
        a0 = i / 3 * math.pi * 2
        for k in range(3):
            a1 = a0 + k * 0.01
            c.line(int(16 + math.cos(a1) * 10), int(16 + math.sin(a1) * 10),
                   int(16 + math.cos(a1 + 2.094) * 10), int(16 + math.sin(a1 + 2.094) * 10), (255, 255, 255, 140))
    save(c, "block/farm_essence_ring")


def site_board():
    c = Canvas(16, 16)
    p1, p2 = hex_rgba("2259A8"), hex_rgba("3A78C8")
    for y in range(16):
        for x in range(16):
            c.set(x, y, p1 if (x // 2 + y // 2) % 2 else p2)
    white = hex_rgba("F4F8FF")
    c.from_rows(ICONS["small_house"], {"w": white}, 4, 4)
    c.outline_rect(0, 0, 15, 15, hex_rgba("C9A23A"))
    c.set(1, 1, hex_rgba("8A8A92"))
    c.set(14, 1, hex_rgba("8A8A92"))
    save(c, "block/construction_site_board")


# ================================================================================================
#  GUI
# ================================================================================================
def gui():
    c = Canvas(256, 256)
    bg0, bg1, bg2, bg3 = hex_rgba("1C1626"), hex_rgba("2A2238"), hex_rgba("3A3050"), hex_rgba("6A5A8A")
    W, H = 176, 176
    for y in range(H):
        for x in range(W):
            c.set(x, y, bg1)
    rnd = random.Random(400)
    for _ in range(500):
        x, y = rnd.randint(4, W - 5), rnd.randint(4, H - 5)
        c.set(x, y, lighten(bg1, 0.06))
    c.outline_rect(0, 0, W - 1, H - 1, bg0)
    c.outline_rect(1, 1, W - 2, H - 2, bg3)
    c.outline_rect(2, 2, W - 3, H - 3, bg2)
    for x in range(3, W - 3):
        c.set(x, 3, lighten(bg2, 0.2))

    def inset(x0, y0, x1, y1):
        c.rect(x0, y0, x1, y1, bg0)
        for x in range(x0, x1 + 1):
            c.set(x, y1, bg3)
        for y in range(y0, y1 + 1):
            c.set(x1, y, bg3)

    def slot(x, y):
        inset(x - 1, y - 1, x + 16, y + 16)
        c.rect(x, y, x + 15, y + 15, hex_rgba("140F1C"))

    # barra de mana
    inset(11, 17, 22, 70)
    # entrada e combustível
    slot(44, 22)
    slot(44, 52)
    # rótulos visuais: losango de entrada, chama de combustível
    gold = hex_rgba("C9A23A")
    for d in range(3):
        c.set(38 - d, 30 - d, gold)
        c.set(38 - d, 30 + d, gold)
    flame = hex_rgba("B896FF")
    for (x, y) in ((36, 60), (37, 59), (38, 60), (37, 61), (37, 62)):
        c.set(x, y, flame)
    # seta (vazia)
    arrow_dark = hex_rgba("4A3F60")
    for y in range(16):
        for x in range(24):
            if (x < 15 and 5 <= y <= 10) or (x >= 15 and abs(y - 7.5) <= (23 - x) * 0.85):
                c.set(68 + x, 36 + y, arrow_dark)
    # seta cheia (u=176, v=0)
    arrow_full = hex_rgba("F2D06B")
    for y in range(16):
        for x in range(24):
            if (x < 15 and 5 <= y <= 10) or (x >= 15 and abs(y - 7.5) <= (23 - x) * 0.85):
                p = arrow_full if y > 6 else lighten(arrow_full, 0.3)
                c.set(176 + x, y, p)
    # saída 3x3
    for r in range(3):
        for col in range(3):
            slot(100 + col * 18, 18 + r * 18)
    # separador e inventário
    for x in range(7, W - 7):
        c.set(x, 86, bg2)
    for r in range(3):
        for col in range(9):
            slot(8 + col * 18, 94 + r * 18)
    for col in range(9):
        slot(8 + col * 18, 152)
    save(c, "gui/farm_machine")


# ================================================================================================
#  Modelos e blockstates
# ================================================================================================
def tex(name):
    return f"{NS}:block/{name}"


def sides(t, uv, cull=False):
    out = {}
    for d in ("north", "south", "east", "west"):
        f = {"texture": t, "uv": uv}
        if cull:
            f["cullface"] = d
        out[d] = f
    return out


def models():
    # Gerador: cubo
    for act in ("", "_active"):
        write_json(os.path.join(A, f"models/block/arcane_stone_generator{act}.json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"side": tex("arcane_stone_generator_side" + act), "top": tex("arcane_stone_generator_top" + act),
                         "bottom": tex("arcane_stone_generator_bottom")}})
    write_json(os.path.join(A, "blockstates/arcane_stone_generator.json"), {"variants": {
        "active=false": {"model": f"{NS}:block/arcane_stone_generator"},
        "active=true": {"model": f"{NS}:block/arcane_stone_generator_active"}}})
    write_json(os.path.join(A, "models/item/arcane_stone_generator.json"), {"parent": f"{NS}:block/arcane_stone_generator_active"})

    # Árvore Acelerada: base de pedra + vaso de madeira com terra
    for act in ("", "_active"):
        write_json(os.path.join(A, f"models/block/accelerated_tree{act}.json"), {
            "parent": "minecraft:block/block",
            "textures": {"particle": tex("accelerated_tree_side"), "base": tex("accelerated_tree_base"),
                         "side": tex("accelerated_tree_side"), "soil": tex("accelerated_tree_soil" + act)},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                    **sides("#base", [0, 12, 16, 16], True),
                    "up": {"texture": "#base", "uv": [0, 0, 16, 16]},
                    "down": {"texture": "#base", "uv": [0, 0, 16, 16], "cullface": "down"}}},
                {"from": [1, 4, 1], "to": [15, 9, 15], "faces": {
                    **sides("#side", [1, 0, 15, 5]),
                    "up": {"texture": "#soil", "uv": [1, 1, 15, 15]}}},
                {"from": [1, 9, 1], "to": [15, 10, 2], "faces": {**sides("#side", [1, 0, 15, 1]), "up": {"texture": "#side", "uv": [1, 0, 15, 1]}}},
                {"from": [1, 9, 14], "to": [15, 10, 15], "faces": {**sides("#side", [1, 0, 15, 1]), "up": {"texture": "#side", "uv": [1, 0, 15, 1]}}},
                {"from": [1, 9, 2], "to": [2, 10, 14], "faces": {**sides("#side", [2, 0, 14, 1]), "up": {"texture": "#side", "uv": [2, 0, 14, 1]}}},
                {"from": [14, 9, 2], "to": [15, 10, 14], "faces": {**sides("#side", [2, 0, 14, 1]), "up": {"texture": "#side", "uv": [2, 0, 14, 1]}}},
            ]})
    write_json(os.path.join(A, "blockstates/accelerated_tree.json"), {"variants": {
        "active=false": {"model": f"{NS}:block/accelerated_tree"},
        "active=true": {"model": f"{NS}:block/accelerated_tree_active"}}})
    write_json(os.path.join(A, "models/item/accelerated_tree.json"), {"parent": f"{NS}:block/accelerated_tree_active"})

    # Criadouro: base com ninho + gaiola de madeira
    post = {"texture": "#post", "uv": [0, 0, 2, 16]}
    elements = [{"from": [0, 0, 0], "to": [16, 5, 16], "faces": {
        **sides("#side", [0, 11, 16, 16], True),
        "up": {"texture": "#top", "uv": [0, 0, 16, 16]},
        "down": {"texture": "#side", "uv": [0, 0, 16, 16], "cullface": "down"}}}]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append({"from": [x, 5, z], "to": [x + 2, 14, z + 2], "faces": {d: dict(post) for d in ("north", "south", "east", "west")}})
    beam = {"texture": "#post", "uv": [0, 0, 16, 2]}
    for f, t in (([0, 14, 0], [16, 16, 2]), ([0, 14, 14], [16, 16, 16]), ([0, 14, 2], [2, 16, 14]), ([14, 14, 2], [16, 16, 14])):
        elements.append({"from": f, "to": t, "faces": {d: dict(beam) for d in ("north", "south", "east", "west", "up", "down")}})
    for act in ("", "_active"):
        write_json(os.path.join(A, f"models/block/arcane_hatchery{act}.json"), {
            "parent": "minecraft:block/block",
            "textures": {"particle": tex("arcane_hatchery_side"), "side": tex("arcane_hatchery_side"),
                         "top": tex("arcane_hatchery_top"), "post": tex("arcane_hatchery_post")},
            "elements": elements})
    write_json(os.path.join(A, "blockstates/arcane_hatchery.json"), {"variants": {
        "active=false": {"model": f"{NS}:block/arcane_hatchery"},
        "active=true": {"model": f"{NS}:block/arcane_hatchery_active"}}})
    write_json(os.path.join(A, "models/item/arcane_hatchery.json"), {"parent": f"{NS}:block/arcane_hatchery"})

    # Lago: tanque de pedra com água animada e borda
    pond = {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("magic_fish_pond_side"), "side": tex("magic_fish_pond_side"),
                     "rim": tex("magic_fish_pond_rim"), "water": tex("magic_fish_pond_water")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
                **sides("#side", [0, 4, 16, 16], True),
                "up": {"texture": "#water", "uv": [0, 0, 16, 16]},
                "down": {"texture": "#rim", "uv": [0, 0, 16, 16], "cullface": "down"}}},
            {"from": [0, 12, 0], "to": [16, 14, 2], "faces": {**sides("#side", [0, 2, 16, 4]), "up": {"texture": "#rim", "uv": [0, 0, 16, 2]}}},
            {"from": [0, 12, 14], "to": [16, 14, 16], "faces": {**sides("#side", [0, 2, 16, 4]), "up": {"texture": "#rim", "uv": [0, 14, 16, 16]}}},
            {"from": [0, 12, 2], "to": [2, 14, 14], "faces": {**sides("#side", [2, 2, 14, 4]), "up": {"texture": "#rim", "uv": [0, 2, 2, 14]}}},
            {"from": [14, 12, 2], "to": [16, 14, 14], "faces": {**sides("#side", [2, 2, 14, 4]), "up": {"texture": "#rim", "uv": [14, 2, 16, 14]}}},
        ]}
    write_json(os.path.join(A, "models/block/magic_fish_pond.json"), pond)
    write_json(os.path.join(A, "blockstates/magic_fish_pond.json"), {"variants": {
        "active=false": {"model": f"{NS}:block/magic_fish_pond"},
        "active=true": {"model": f"{NS}:block/magic_fish_pond"}}})
    write_json(os.path.join(A, "models/item/magic_fish_pond.json"), {"parent": f"{NS}:block/magic_fish_pond"})

    # Pedestal Elemental: base, coluna e tampo (um modelo por elemento)
    write_json(os.path.join(A, "models/block/elemental_pedestal_template.json"), {
        "parent": "minecraft:block/block",
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {
                **sides("#base", [1, 13, 15, 16]),
                "up": {"texture": "#top", "uv": [1, 1, 15, 15]},
                "down": {"texture": "#base", "uv": [1, 1, 15, 15], "cullface": "down"}}},
            {"from": [4, 3, 4], "to": [12, 11, 12], "faces": sides("#column", [4, 0, 12, 8])},
            {"from": [2, 11, 2], "to": [14, 13, 14], "faces": {
                **sides("#base", [2, 0, 14, 2]),
                "up": {"texture": "#top", "uv": [2, 2, 14, 14]},
                "down": {"texture": "#base", "uv": [2, 2, 14, 14]}}},
            {"from": [3, 13, 3], "to": [4, 15, 4], "faces": sides("#column", [7, 0, 8, 2])},
            {"from": [12, 13, 3], "to": [13, 15, 4], "faces": sides("#column", [7, 0, 8, 2])},
            {"from": [3, 13, 12], "to": [4, 15, 13], "faces": sides("#column", [7, 0, 8, 2])},
            {"from": [12, 13, 12], "to": [13, 15, 13], "faces": sides("#column", [7, 0, 8, 2])},
        ]})
    variants = {}
    for el in ELEMENTS:
        write_json(os.path.join(A, f"models/block/elemental_pedestal_{el}.json"), {
            "parent": f"{NS}:block/elemental_pedestal_template",
            "textures": {"particle": tex(f"elemental_pedestal_{el}_base"), "base": tex(f"elemental_pedestal_{el}_base"),
                         "column": tex(f"elemental_pedestal_{el}_column"), "top": tex(f"elemental_pedestal_{el}_top")}})
        for act in ("false", "true"):
            variants[f"active={act},element={el}"] = {"model": f"{NS}:block/elemental_pedestal_{el}"}
    write_json(os.path.join(A, "blockstates/elemental_pedestal.json"), {"variants": variants})
    els = list(ELEMENTS)
    write_json(os.path.join(A, "models/item/elemental_pedestal.json"), {
        "parent": f"{NS}:block/elemental_pedestal_fire",
        "overrides": [{"predicate": {f"{NS}:element": round(i / 10 - 0.001, 3) if i else 0.0},
                       "model": f"{NS}:block/elemental_pedestal_{el}"} for i, el in enumerate(els)][1:]})

    # Canteiro de Obras: estaca + placa com a planta
    write_json(os.path.join(A, "models/block/construction_site.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/oak_planks", "post": "minecraft:block/stripped_oak_log",
                     "board": tex("construction_site_board"), "wood": "minecraft:block/oak_planks"},
        "elements": [
            {"from": [6.5, 0, 6.5], "to": [9.5, 13, 9.5], "faces": {
                **sides("#post", [6, 3, 9, 16]), "up": {"texture": "#post", "uv": [6, 6, 9, 9]},
                "down": {"texture": "#post", "uv": [6, 6, 9, 9], "cullface": "down"}}},
            {"from": [2, 9, 7], "to": [14, 16, 8], "faces": {
                "north": {"texture": "#board", "uv": [2, 0, 14, 7]},
                "south": {"texture": "#board", "uv": [2, 0, 14, 7]},
                "east": {"texture": "#wood", "uv": [0, 0, 1, 7]}, "west": {"texture": "#wood", "uv": [0, 0, 1, 7]},
                "up": {"texture": "#wood", "uv": [2, 0, 14, 1]}, "down": {"texture": "#wood", "uv": [2, 0, 14, 1]}}},
            {"from": [2, 8.5, 6.5], "to": [14, 9, 8.5], "faces": {
                **sides("#wood", [2, 0, 14, 1]), "up": {"texture": "#wood", "uv": [2, 0, 14, 2]},
                "down": {"texture": "#wood", "uv": [2, 0, 14, 2]}}},
        ]})
    write_json(os.path.join(A, "blockstates/construction_site.json"), {"variants": {
        f"facing={f}": ({"model": f"{NS}:block/construction_site", "y": r} if r else {"model": f"{NS}:block/construction_site"})
        for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

    # Itens simples
    for iid in ["construction_wand"] + [f"blueprint_{k}" for k in ICONS]:
        parent = "minecraft:item/handheld" if iid == "construction_wand" else "minecraft:item/generated"
        write_json(os.path.join(A, f"models/item/{iid}.json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/{iid}"}})


# ================================================================================================
#  Dados
# ================================================================================================
def loot_tables():
    for bid in ("arcane_stone_generator", "accelerated_tree", "arcane_hatchery", "magic_fish_pond", "elemental_pedestal"):
        entry = {"type": "minecraft:item", "name": f"{NS}:{bid}"}
        if bid == "elemental_pedestal":
            entry["functions"] = [{"function": "minecraft:copy_state", "block": f"{NS}:elemental_pedestal", "properties": ["element"]}]
        write_json(os.path.join(D, f"loot_table/blocks/{bid}.json"), {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/{bid}",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [entry],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def shaped(name, pattern, key, result, count=1, extra=None):
    obj = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
           "result": {"id": result, "count": count}}
    if extra:
        obj.update(extra)
    write_json(os.path.join(D, f"recipe/{name}.json"), obj)


def shapeless(name, ingredients, result):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
        "result": {"id": result, "count": 1}})


def item(i):
    return {"item": i}


def tag(t):
    return {"tag": t}


def recipes():
    crystal = item(f"{NS}:mana_crystal")
    shaped("construction_wand", ["  M", " A ", "S  "],
           {"M": crystal, "A": item("minecraft:amethyst_shard"), "S": item("minecraft:stick")}, f"{NS}:construction_wand")
    extras = {
        "small_house": tag("minecraft:planks"),
        "tower": tag("minecraft:stone_bricks"),
        "barn": item("minecraft:hay_block"),
        "bridge": tag("minecraft:wooden_fences"),
        "farm_plot": item("minecraft:wheat_seeds"),
    }
    for k, extra in extras.items():
        shapeless(f"blueprint_{k}", [item("minecraft:paper"), item("minecraft:blue_dye"), crystal, extra], f"{NS}:blueprint_{k}")
    shaped("arcane_stone_generator", ["CMC", "LPW", "CCC"],
           {"C": item("minecraft:cobblestone"), "M": crystal, "L": item("minecraft:lava_bucket"),
            "W": item("minecraft:water_bucket"), "P": item("minecraft:iron_pickaxe")}, f"{NS}:arcane_stone_generator")
    shaped("accelerated_tree", ["GMG", "GSG", "PBP"],
           {"G": item("minecraft:glass"), "M": crystal, "S": tag("minecraft:saplings"), "P": tag("minecraft:planks"),
            "B": item("minecraft:bone_block")}, f"{NS}:accelerated_tree")
    shaped("arcane_hatchery", ["FMF", "FHF", "PPP"],
           {"F": tag("minecraft:wooden_fences"), "M": crystal, "H": item("minecraft:hay_block"), "P": tag("minecraft:planks")},
           f"{NS}:arcane_hatchery")
    shaped("magic_fish_pond", ["BMB", "BWB", "BRB"],
           {"B": tag("minecraft:stone_bricks"), "M": crystal, "W": item("minecraft:water_bucket"), "R": item("minecraft:fishing_rod")},
           f"{NS}:magic_fish_pond")
    # Pedestal Elemental: caminho caro (o normal é roubar o altar com Toque Suave)
    for el in ELEMENTS:
        brick, core = f"{NS}:elemental_brick_{el}", f"{NS}:guardian_core_{el}"
        write_json(os.path.join(D, f"recipe/elemental_pedestal_{el}.json"), {
            "neoforge:conditions": [{"type": "neoforge:item_exists", "item": brick}, {"type": "neoforge:item_exists", "item": core}],
            "type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": ["BCB", "BMB", "BBB"],
            "key": {"B": item(brick), "C": item(core), "M": item(f"{NS}:mana_crystal")},
            "result": {"id": f"{NS}:elemental_pedestal", "count": 1,
                       "components": {"minecraft:block_state": {"element": el}}}})


def tags():
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [
        f"{NS}:arcane_stone_generator", f"{NS}:magic_fish_pond", f"{NS}:elemental_pedestal"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"), {"values": [
        f"{NS}:accelerated_tree", f"{NS}:arcane_hatchery", f"{NS}:construction_site"]})


def main():
    wand()
    for k, (pt, _) in zip(ICONS, [("E8B26A", 0), ("B8C4D8", 0), ("D9634A", 0), ("9C7A4E", 0), ("8FD46A", 0)]):
        blueprint(k, pt)
    for act in (False, True):
        generator_side(act)
        generator_top(act)
        planter_soil(act)
    generator_bottom()
    planter_side()
    planter_base()
    hatchery_base()
    hatchery_top()
    hatchery_post()
    pond_side()
    pond_rim()
    pond_water()
    for el, (dk, lt) in ELEMENTS.items():
        pedestal_textures(el, dk, lt)
    essence_ring()
    site_board()
    gui()
    models()
    loot_tables()
    recipes()
    tags()
    print("farm: assets gerados")


if __name__ == "__main__":
    main()
