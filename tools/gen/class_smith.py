"""
Gera os assets e dados do modulo class_smith (Ferreiro / Forja Arcana).
Uso (na raiz do projeto):  py -I tools/gen/class_smith.py

Saidas (todas com nomes do modulo):
  textures/item/class_smith/forge_parts.png        atlas 64x64 das pecas da Lamina Forjada (tingido em tempo real)
  textures/item/smith_forged_blade.png             icone/particula da Lamina Forjada (o modelo e montado no renderer)
  models/item/smith_forged_blade.json              builtin/entity com as transformacoes (mao em y=8)
  smith_hammer + smith_hammer_t1..t4               modelo 3D "elements" (models/item/class_smith/*_held.json + atlas),
                                                   icone 2D no inventario (separate_transforms), receita do kit
  smith_star_metal                                 icone + modelo
  smith_arcane_anvil                               bloco (modelo elements + atlas, blockstate com 4 direcoes), item, loot,
                                                   receita, picareta (merge)
  roupa do Ferreiro (4 graus)                      geo/item/armor/outfit_smith[_grau].geo.json + textura + glowmask
                                                   (helpers do tools/gen/outfits.py), icones 16x16 das 16 pecas,
                                                   camadas vanilla de reserva, receitas do grau I, tags de armadura
  smith_star_hammer (entidade)                     geo + animacao + textura + glowmask (GeckoLib)
  textures/entity/smith_spark.png                  fagulha
  textures/gui/tab/smith_forge.png                 icone da aba "Forja" do menu B
  merge: minecraft:swords, c:tools/melee_weapon, enchantable/durability, mineable/pickaxe, *_armor
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)

from pixelart import Canvas, lighten, darken, mix, item_model  # noqa: E402
import outfits as O  # noqa: E402
import armory as AR  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/class_smith")
NS = "isekai_mahou"
T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================================
#  Primitivas de icone: retangulo girado com sombreamento (luz no canto de cima a esquerda)
# =====================================================================================================
def rot_rect(c, cx, cy, length, width, ang_deg, pal3, outline=None, shade=True):
    """Preenche um retangulo centrado (cx,cy) com 'length' ao longo do angulo e 'width' na perpendicular.
    pal3 = (escuro, medio, claro)."""
    a = math.radians(ang_deg)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    hit = set()
    for y in range(c.h):
        for x in range(c.w):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            u = px * ux + py * uy
            v = px * vx + py * vy
            if abs(u) <= length / 2 and abs(v) <= width / 2:
                hit.add((x, y))
                if shade:
                    t = v / max(0.5, width / 2)
                    col = pal3[2] if t < -0.35 else (pal3[0] if t > 0.45 else pal3[1])
                else:
                    col = pal3[1]
                c.set(x, y, col)
    if outline is not None:
        for (x, y) in list(hit):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in hit and c.get(x + dx, y + dy)[3] == 0:
                    c.set(x + dx, y + dy, outline)
    return hit


def sparkle(c, x, y, col):
    c.set(x, y, lighten(col, 0.5))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if c.get(x + dx, y + dy)[3] == 0:
            c.set(x + dx, y + dy, col)


# ------------------------------------------------------------------ paletas
WOOD3 = (rgb("4E3420"), rgb("7A5232"), rgb("A3764A"))
LEATH3 = (rgb("3A2214"), rgb("5E3A22"), rgb("8A5A36"))
IRON3 = (rgb("50545E"), rgb("9AA0AC"), rgb("DCE2EA"))
STEEL3 = (rgb("3C4250"), rgb("7C8698"), rgb("C8D2E2"))
GOLD3 = (rgb("8A5A10"), rgb("D49A26"), rgb("FFE08A"))
STAR3 = (rgb("2C3A8A"), rgb("5C78E8"), rgb("B8C8FF"))
DIAM3 = (rgb("2A8A84"), rgb("5FD8CC"), rgb("C8FFF8"))
OUT = rgb("1A120C")


def hammer_icon(tier):
    c = Canvas(16, 16)
    handle = WOOD3 if tier < 2 else LEATH3
    head = [IRON3, IRON3, STEEL3, DIAM3, STAR3][tier]
    rot_rect(c, 6.2, 9.8, 12.5, 1.9, -45, handle, outline=OUT)
    # cabeca perpendicular ao cabo, no alto a direita
    rot_rect(c, 10.6, 5.4, 8.6, 4.6, 45, head, outline=OUT)
    # faces de bater (mais claras nas pontas)
    for (x, y) in ((13, 2), (14, 3), (7, 8), (8, 9)):
        if c.get(x, y)[3] > 0 and c.get(x, y) != OUT:
            c.set(x, y, lighten(head[2], 0.3))
    if tier >= 1:
        # pomo / anel
        c.set(1, 14, GOLD3[1] if tier >= 3 else IRON3[1])
        c.set(2, 14, GOLD3[2] if tier >= 3 else IRON3[2])
    if tier == 2:
        for (x, y) in ((10, 6), (11, 5)):
            c.set(x, y, rgb("7FD8FF"))
    if tier == 3:
        for (x, y) in ((9, 7), (12, 4)):
            c.set(x, y, GOLD3[2])
    if tier == 4:
        c.set(10, 5, rgb("FFFFFF"))
        c.set(11, 5, rgb("9FB8FF"))
        c.set(10, 6, rgb("9FB8FF"))
        sparkle(c, 14, 13, rgb("8FA8FF"))
        sparkle(c, 2, 2, rgb("8FA8FF"))
    return c


def blade_icon():
    c = Canvas(16, 16)
    rot_rect(c, 9.6, 6.4, 10.5, 2.6, -45, IRON3, outline=OUT)
    rot_rect(c, 4.7, 11.3, 4.8, 1.6, -45, LEATH3, outline=OUT)
    rot_rect(c, 6.3, 9.7, 5.2, 1.4, 45, GOLD3, outline=OUT)
    c.set(2, 13, GOLD3[1]); c.set(3, 13, GOLD3[2]); c.set(2, 14, GOLD3[0])
    c.set(14, 1, IRON3[2])
    return c


def star_metal_icon():
    c = Canvas(16, 16)
    rows = [
        "................",
        "................",
        "................",
        "................",
        "......hhhhhh....",
        ".....hllllllh...",
        "....hlmmmmmmlh..",
        "...hlmmmmmmmmdo.",
        "..hlmmmmmmmmddo.",
        "..ommmmmmmmddo..",
        "..odddddddddo...",
        "...oooooooooo...",
        "................",
        "................",
        "................",
        "................",
    ]
    palette = {"h": rgb("E8EEFF"), "l": rgb("B8C8FF"), "m": rgb("6A84F0"), "d": rgb("33429A"), "o": rgb("141C40")}
    c.from_rows(rows, palette)
    for (x, y) in ((6, 7), (9, 8), (11, 7)):
        c.set(x, y, rgb("FFFFFF"))
    sparkle(c, 13, 3, rgb("9FB8FF"))
    sparkle(c, 3, 13, rgb("9FB8FF"))
    return c


def spark_texture():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8
            if d < 1:
                a = int(255 * (1 - d) ** 1.6)
                c.set(x, y, (255, 255, 255, a))
    for i in range(16):
        k = abs(i + 0.5 - 8) / 8
        a = int(200 * max(0, 1 - k) ** 2)
        p = c.get(i, 8)
        c.set(i, 8, (255, 255, 255, max(p[3], a)))
        p = c.get(8, i)
        c.set(8, i, (255, 255, 255, max(p[3], a)))
    return c


def tab_icon():
    c = Canvas(16, 16)
    # bigorna
    rows = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..oooooooooooo..",
        ".ohhhhhhhhhhhmo.",
        "..ommmmmmmmmdo..",
        "....ommmmmdo....",
        ".....ommmdo.....",
        "....ommmmmdo....",
        "...oddddddddo...",
        "...oooooooooo...",
    ]
    c.from_rows(rows, {"o": OUT, "h": rgb("C8CED8"), "m": rgb("7A808C"), "d": rgb("4A4E58")})
    # martelo em cima, batendo
    rot_rect(c, 9.5, 4.0, 7.0, 1.4, -30, WOOD3, outline=OUT)
    rot_rect(c, 5.6, 4.6, 4.4, 2.6, 60, IRON3, outline=OUT)
    for (x, y, col) in ((3, 7, "FFB040"), (12, 6, "FF8A2A"), (2, 5, "FFD080")):
        c.set(x, y, rgb(col))
    return c


# =====================================================================================================
#  Atlas das pecas da Lamina Forjada (64x64, tons claros: o renderer tinge com a cor do material)
# =====================================================================================================
def forge_parts():
    c = Canvas(64, 64)
    rnd = random.Random(7)

    def region(u, v, w, h, fn):
        for y in range(h):
            for x in range(w):
                c.set(u + x, v + y, fn(x, y))

    # METAL polido: escovado vertical claro com reflexos
    region(0, 0, 16, 32, lambda x, y: (lambda g: (g, g, g, 255))(
        int(200 + 30 * math.sin(x * 1.3 + y * 0.05) + rnd.randint(-6, 6))))
    # EDGE: fio quase branco com faixa clara
    region(16, 0, 16, 32, lambda x, y: (lambda g: (g, g, g, 255))(min(255, 238 + rnd.randint(-8, 10))))
    # LEATHER: couro com tramas diagonais (cor propria)
    region(32, 0, 16, 16, lambda x, y: (lambda k: rgb(["5E3A22", "7A4C2C", "8E5C36"][k]))((x + y) % 3))
    # WOOD: veios
    region(32, 16, 16, 16, lambda x, y: (lambda k: rgb(["6E4A2C", "8A6038", "9E7044", "7A5232"][k]))(
        (x + (y // 5)) % 4 if rnd.random() > 0.1 else 0))
    # GOLD
    region(48, 0, 16, 16, lambda x, y: rgb("FFE08A") if (x + y) % 7 == 0 else (rgb("E8B030") if y % 4 else rgb("C48A1E")))
    # RUNE: branco com tracos (brilha tingido)
    def rune(x, y):
        if (x % 4 == 1 and y % 5 != 4) or (y % 5 == 2 and x % 4 != 3):
            return (255, 255, 255, 255)
        return (200, 200, 210, 255)
    region(48, 16, 16, 16, rune)
    # MATTE: aspero e escuro
    region(0, 32, 16, 32, lambda x, y: (lambda g: (g, g, g, 255))(150 + rnd.randint(-18, 18)))
    # GEM
    def gem(x, y):
        d = (x + y) / 30
        g = int(255 - 90 * d)
        return (g, g, g, 255)
    region(16, 32, 16, 16, gem)
    # WRAP: voltas do cordao (cinza claro, tingido pela empunhadura)
    region(32, 32, 16, 16, lambda x, y: (lambda g: (g, g, g, 255))(180 if (x + 2 * y) % 4 else 120))
    c.save(os.path.join(A, "textures/item/class_smith/forge_parts.png"))


# =====================================================================================================
#  Martelos (modelo 3D "elements" com o atlas do armory) e Lamina Forjada (builtin/entity)
# =====================================================================================================
BLADE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 1, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, -1, -0.4], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.1, -1, -0.4], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}

HAMMER_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 1.5, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, -0.5, -0.4], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}

HAMMER_IDS = ["smith_hammer", "smith_hammer_t1", "smith_hammer_t2", "smith_hammer_t3", "smith_hammer_t4"]


def hammer_model(tier):
    m = AR.Model(HAMMER_IDS[tier], atlas=32)
    wood = AR.OAK if tier < 2 else AR.DARK_OAK
    head = [AR.IRON, AR.IRON, AR.STEEL, AR.SILVER, AR.M("metal", "e0e8ff", "9fb2ff", "5c74e0", "2c3a8a")][tier]
    face = [AR.DARK_IRON, AR.IRON, AR.STEEL, AR.GOLD, AR.M("metal", "ffffff", "d8e0ff", "a8b8ff", "5c74e0")][tier]
    # cabo (mao em y ~8)
    m.box((7.2, -1.0, 7.2), (8.8, 18.0, 8.8), wood)
    if tier >= 1:
        m.box((6.9, 3.5, 6.9), (9.1, 10.5, 9.1), AR.LEATHER)
        m.box((6.7, -2.0, 6.7), (9.3, -0.5, 9.3), AR.GOLD if tier >= 3 else AR.DARK_IRON)
    # cabeca: bloco com faces de bater
    hx0, hx1 = 3.0 - 0.3 * tier, 13.0 + 0.3 * tier
    m.box((hx0 + 1.0, 16.0, 6.0), (hx1 - 1.0, 21.5, 10.0), head)
    m.box((hx0, 15.6, 5.6), (hx0 + 1.0, 21.9, 10.4), face)
    m.box((hx1 - 1.0, 15.6, 5.6), (hx1, 21.9, 10.4), face)
    # cinta/runa no meio
    if tier == 2:
        m.box((7.0, 16.6, 5.7), (9.0, 20.9, 10.3), AR.RUNE_BLUE)
    if tier == 3:
        m.box((6.5, 15.8, 5.8), (7.3, 21.7, 10.2), AR.GOLD)
        m.box((8.7, 15.8, 5.8), (9.5, 21.7, 10.2), AR.GOLD)
        m.box((7.4, 17.8, 5.6), (8.6, 19.6, 6.0), AR.GEM_RED)
    if tier == 4:
        m.box((6.6, 17.0, 5.6), (9.4, 20.4, 10.4), AR.GEM_PALE_BLUE)
        # espigoes estelares em cima
        m.box((7.4, 21.5, 7.4), (8.6, 24.0, 8.6), AR.M("metal", "ffffff", "d8e0ff", "a8b8ff", "5c74e0"))
    return m


def build_hammers():
    for tier, iid in enumerate(HAMMER_IDS):
        model = hammer_model(tier)
        tex = NS + ":item/class_smith/" + iid
        try:
            held, atlas = model.build(tex, seed=11 + tier)
        except RuntimeError:
            model.atlas = 64
            held, atlas = model.build(tex, seed=11 + tier)
        held["display"] = HAMMER_DISPLAY
        atlas.save(os.path.join(A, "textures/item/class_smith", iid + ".png"))
        write_json(os.path.join(A, "models/item/class_smith", iid + "_held.json"), held)
        hammer_icon(tier).save(os.path.join(A, "textures/item", iid + ".png"))
        flat = {"parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/" + iid}}
        write_json(os.path.join(A, "models/item", iid + ".json"), {
            "loader": "neoforge:separate_transforms",
            "gui_light": "front",
            "textures": {"particle": NS + ":item/" + iid},
            "base": {"parent": NS + ":item/class_smith/" + iid + "_held"},
            "perspectives": {"gui": flat, "fixed": flat},
        })


def build_blade_item():
    blade_icon().save(os.path.join(A, "textures/item/smith_forged_blade.png"))
    write_json(os.path.join(A, "models/item/smith_forged_blade.json"), {
        "parent": "minecraft:builtin/entity",
        "gui_light": "side",
        "textures": {"particle": NS + ":item/smith_forged_blade"},
        "display": BLADE_DISPLAY,
    })
    star_metal_icon().save(os.path.join(A, "textures/item/smith_star_metal.png"))
    item_model(A, "smith_star_metal")


# =====================================================================================================
#  Bigorna Arcana (bloco)
# =====================================================================================================
def build_anvil():
    m = AR.Model("smith_arcane_anvil", atlas=64)
    dark = AR.DARK_IRON
    iron = AR.M("metal", "e8ecf2", "b0b6c0", "7a808c", "3e424c")
    rune = AR.M("glow", "fff0d0", "ffb040", "e8742c", "7a2a08", glow=True)
    gem = AR.M("gem", "ffffff", "ffd080", "ff8a2a", "a0400a", glow=True)
    m.box((2, 0, 2), (14, 3, 14), dark)
    m.box((3, 3, 3), (13, 4, 13), dark)
    m.box((3, 4, 4), (13, 5, 12), iron)
    m.box((4, 5, 6), (12, 10, 10), iron)
    m.box((2, 10, 3), (14, 16, 13), iron)
    m.box((0, 12, 5), (2, 15.5, 11), iron)       # chifre
    m.box((14, 11, 4), (16, 16, 12), iron)       # calcanhar
    m.box((3, 12.5, 2.8), (13, 13.5, 3.0), rune)  # runas acesas nas laterais
    m.box((3, 12.5, 13.0), (13, 13.5, 13.2), rune)
    m.box((4.5, 16.0, 6), (11.5, 16.2, 10), rune)  # brasa da mesa
    m.box((7, 1, 1.8), (9, 3, 2.0), gem)
    m.box((7, 1, 14.0), (9, 3, 14.2), gem)
    model, atlas = m.build(NS + ":block/smith_arcane_anvil", seed=5)
    model["parent"] = "minecraft:block/block"
    atlas.save(os.path.join(A, "textures/block/smith_arcane_anvil.png"))
    write_json(os.path.join(A, "models/block/smith_arcane_anvil.json"), model)
    write_json(os.path.join(A, "models/item/smith_arcane_anvil.json"), {"parent": NS + ":block/smith_arcane_anvil"})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": NS + ":block/smith_arcane_anvil"}
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    write_json(os.path.join(A, "blockstates/smith_arcane_anvil.json"), {"variants": variants})
    write_json(os.path.join(D, "loot_table/blocks/smith_arcane_anvil.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":smith_arcane_anvil"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


# =====================================================================================================
#  Roupa do Ferreiro (GeckoLib, 4 graus) — helpers do outfits.py
# =====================================================================================================
HD, BD, RA, LA, RL, LL, RB, LB = O.HD, O.BD, O.RA, O.LA, O.RL, O.LL, O.RB, O.LB
fc, OMIT, pal = O.fc, O.OMIT, O.pal

APRON = pal("2A170C", "45281A", "64402A", "86583A", "A8784E")
LINEN = pal("8A7E66", "B4A88C", "D8CEB2", "EEE6D0", "FFFAEA")
SOOT = pal("1E1A18", "2E2826", "403835", "57504A", "6E6660")
BANDANA = pal("4A0C0C", "781A16", "A42A20", "C8463A", "E8725E")
BRASS = pal("4A3410", "7A5A1C", "A8822E", "D4AE4A", "F2D888")
STEELP = pal("30323A", "50545E", "7A808C", "A8AEB8", "E0E4EA")
GOLDP = pal("6A4508", "A06C12", "D49A26", "F5C84A", "FFF0A8")
STARP = pal("1A2050", "2C3A8A", "4A60C8", "7C93FF", "C8D4FF")
EMBER = pal("5A1002", "A0300A", "E8742C", "FFB040", "FFE8A0")
LEATHER_D = pal("1A120C", "2A1E14", "3C2C1E", "52402C", "6A543C")

SMITH_T = {
    0: O.theme(APRON, LINEN, STEELP, LEATHER_D, BRASS, STEELP, pal("4A2004", "A0400A", "FF8A2A", "FFD8A8"), "FF8A2A"),
    1: O.theme(APRON, LINEN, STEELP, LEATHER_D, BRASS, BRASS, pal("4A2004", "A0400A", "FF8A2A", "FFD8A8"), "FF9A3A"),
    2: O.theme(pal("24140A", "3C2414", "5A3A22", "7A5634", "9A7450"), pal("1E1C22", "34323A", "4C4A54", "6A6874", "8E8C98"),
               GOLDP, LEATHER_D, GOLDP, STEELP, pal("4A3004", "A07010", "FFD24A", "FFF0B0"), "FFC94A"),
    3: O.theme(pal("161220", "262036", "3A3050", "524670", "70628E"), pal("10121E", "1C2034", "2C3250", "424A72", "5C6694"),
               STARP, LEATHER_D, EMBER, STARP, pal("1A2470", "3A50D0", "8FA8FF", "E0E8FF"), "8FA8FF"),
}
TIER_NAMES = {0: "outfit_smith", 1: "outfit_smith_adept", 2: "outfit_smith_master", 3: "outfit_smith_archmage"}


def smith_mats(lv):
    th = SMITH_T[lv]
    P = th["pal"]
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = O.v9_common(M, P, th, lv)
    MT = th["METAL"]
    GL = th["GLOW"]
    lit = lv > 0
    put = lambda r: O.put(r, lit)

    M["shirt"] = lambda r: O.cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.14)
    M["cuff"] = lambda r: (O.cloth(r, SEC, period=2, base=3, folds=True), r.row(0, SEC[4]), r.row(-1, SEC[1]) if r.h > 1 else None)

    def apron(r, front, low=False):
        O.leather(r, MAIN, seams=False, speckle=0.12)
        r.col(0, MAIN[1]); r.col(-1, MAIN[1])
        # costura dupla nas bordas
        for j in range(1, r.h - 1, 2):
            r.set(1, j, MAIN[3]); r.set(r.w - 2, j, MAIN[3])
        # queimaduras de fagulha
        for _ in range(max(1, r.w * r.h // 22)):
            i, j = r.rnd.randrange(r.w), r.rnd.randrange(r.h)
            r.set(i, j, darken(MAIN[0], 0.3))
        if front and not low:
            cx = r.w // 2
            # bolso com martelo bordado
            for i in range(cx - 2, cx + 2):
                r.set(i, r.h - 3, MAIN[3])
            if lv >= 1:
                O.sigil(r, cx - 1 + (r.w % 2), max(2, r.h // 2 - 1), GL, glow=True)
            if lv >= 3:
                for j in range(2, r.h - 2, 3):
                    r.glow(2, j, GL); r.glow(r.w - 3, j, GL)
        if low:
            O.trim_rows(r, ACC if lv >= 2 else MAIN, 1)
            if lv >= 2:
                for k, i in enumerate(range(2, r.w - 2, 3)):
                    O.rune_glyph(r, i, r.h - 6, GL, k, glow=True)

    M["apron_front"] = lambda r: apron(r, True)
    M["apron_low"] = lambda r: apron(r, True, low=True)
    M["apron_in"] = lambda r: (O.leather(r, MAIN, base=1, seams=False), None)
    M["apron_edge"] = lambda r: (r.fill(MAIN[1]), r.noise(0.03))

    def bandana(r, top=False):
        O.cloth(r, BANDANA, period=3, base=2, folds=not top)
        for _ in range(r.w * r.h // 9):
            r.set(r.rnd.randrange(r.w), r.rnd.randrange(r.h), BANDANA[3])
    M["bandana"] = lambda r: bandana(r)
    M["bandana_top"] = lambda r: bandana(r, True)
    M["strap"] = lambda r: (r.fill(LEATH[2]), r.row(0, LEATH[3]) if r.h > 1 else None, r.noise(0.04))

    def lens(r):
        r.fill(rgb("2A1A10"))
        for j in range(r.h):
            for i in range(r.w):
                if 0 < i < r.w - 1 and 0 < j < r.h - 1:
                    (r.glow if lit else r.set)(i, j, lighten(GL, 0.2) if (i + j) % 3 else GL)
        r.set(0, 0, ACC[3])
    M["lens"] = lens
    M["lens_rim"] = lambda r: (r.fill(ACC[2]), r.row(0, ACC[4]), r.noise(0.03))

    def bracer(r):
        O.leather(r, LEATH, seams=False)
        r.row(0, TRIM[3]); r.row(-1, TRIM[1])
        for i in range(1, r.w - 1, 2):
            r.set(i, r.h // 2, TRIM[4])
        if lv >= 3:
            r.glow(r.w // 2, r.h // 2, GL)
    M["bracer"] = bracer

    def gauntlet(r):
        O.plate(r, MT, TRIM if lv >= 2 else None, rivets=True)
        if lv >= 2:
            for i in range(1, r.w - 1, 2):
                put(r)(i, r.h - 2, GL)
    M["gauntlet"] = gauntlet
    M["trousers"] = lambda r: O.cloth(r, SOOT, period=3, base=2, top=0.04, bottom=0.10)
    M["kneecop"] = lambda r: (O.plate(r, MT, rivets=False), r.set(r.w // 2, r.h // 2, MT[4]))
    M["boot"] = lambda r: (O.leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))

    def boot_front(r):
        O.leather(r, LEATH, seams=False)
        r.row(-1, LEATH[0])
        cx = r.w // 2
        for j in range(1, r.h - 1, 2):
            r.set(cx - 1, j, ACC[3]); r.set(cx, j, ACC[3])
    M["boot_front"] = boot_front
    M["boot_cuff"] = lambda r: (r.fill(LEATH[3]), r.row(0, LEATH[4]), r.row(-1, LEATH[1]), r.noise(0.04))
    M["toecap"] = lambda r: O.plate(r, MT, rivets=False, shine=True)
    M["pouch"] = lambda r: (O.leather(r, LEATH, seams=True), r.row(0, ACC[3]))
    M["tool_handle"] = lambda r: (r.fill(rgb("7A5232")), r.col(0, rgb("A3764A")), r.noise(0.04))
    M["tool_head"] = lambda r: O.metal(r, STEELP, rivets=False)

    def helm(r, front=False):
        O.plate(r, MT, TRIM, rivets=True, ridge=False)
        if front and lv >= 2:
            cx = r.w // 2
            r.glow(cx - 1, 1, GL); r.glow(cx, 1, lighten(GL, 0.3))
    M["helm"] = lambda r: helm(r)
    M["helm_front"] = lambda r: helm(r, True)
    M["helm_top"] = lambda r: (O.plate(r, MT, rivets=False, shine=False), r.col(r.w // 2, MT[4]))

    def cape_out(r):
        O.cloth(r, MAIN if lv < 3 else P["SEC"], period=4, base=2, top=0.06, bottom=0.14)
        r.col(0, ACC[2]); r.col(-1, ACC[2])
        O.trim_rows(r, ACC, 2)
        cx = r.w // 2
        # martelo e bigorna nas costas
        for j in range(3, min(r.h - 5, 10)):
            r.set(cx - 1, j, ACC[3]); r.set(cx, j, ACC[2])
        for i in range(cx - 3, cx + 3):
            r.set(i, 3, ACC[3]); r.set(i, 4, ACC[2])
        if lv >= 2:
            O.sigil(r, cx - 1 + (r.w % 2), r.h - 8, GL)
        if lv >= 3:
            for i in range(0, r.w, 2):
                r.glow(i, r.h - 1, lighten(GL, 0.2) if i % 4 else EMBER[3])
    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (O.cloth(r, MAIN, period=4, base=1, folds=False), O.trim_rows(r, ACC, 1))
    M["cape_edge"] = lambda r: (r.fill(ACC[2]), r.noise(0.03))
    return M


def smith_model(name, lv):
    m = O.Model(name)
    O.skeleton(m)
    # ---------------- cabeca: bandana + oculos de forja (I-II), meio-elmo (III), coroa de forja (IV)
    if lv <= 1:
        m.box(HD, [-4, 27.6, -4], [8, 4.4, 8], fc(side="bandana", u="bandana_top", d=OMIT), inflate=0.55)
        # nos da bandana atras
        m.box(HD, [-1.2, 28.0, 4.4], [2.4, 1.6, 0.8], fc(all="bandana"))
        m.box(HD, [-1.6, 25.2, 4.6], [1.2, 2.8, 0.4], fc(all="bandana"), rot=[12, 0, 8], pivot=[-1, 28, 4.8])
        m.box(HD, [0.4, 25.6, 4.6], [1.2, 2.4, 0.4], fc(all="bandana"), rot=[12, 0, -10], pivot=[1, 28, 4.8])
    else:
        m.box(HD, [-4, 27.2, -4], [8, 4.8, 8], fc(side="helm", n="helm_front", u="helm_top", d=OMIT), inflate=0.85)
        m.box(HD, [-3.2, 32.6, -3.2], [6.4, 0.9, 6.4], fc(side="helm_top", u="helm_top", d=OMIT))
    # tira + lentes (os oculos ficam na testa)
    m.box(HD, [-4, 29.0, -4], [8, 1.1, 8], fc(side="strap", u=OMIT, d=OMIT), inflate=0.9 if lv >= 2 else 0.7)
    for sx in (-3.4, 0.6):
        m.box(HD, [sx, 28.6, -5.15], [2.8, 2.0, 0.7], fc(n="lens", s="lens_rim@2x2", ew="lens_rim@1x2", u="lens_rim@2x1",
                                                        d="lens_rim@2x1"))
    if lv == 3:
        O.crown_points(m, HD, -4.6, 4.6, -4.6, 4.6, 32.4, 0.6, [-3.0, 0.0, 3.0], [-2.5, 2.5], 1.1, 1.6,
                       mat="o_point", gem_mat="o_gem@1x1", tall={("x", 0.0): 2.8})
        O.gem(m, HD, [0, 33.4, -5.2], [1.4, 1.4, 0.5])

    # ---------------- tronco: camisa de linho, avental de couro, cinto com bolsa (e martelo nos graus II+)
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(side="shirt", u="shirt", d="dark@2x2"), inflate=0.5)
    m.box(BD, [-3.5, 12.4, -2.85], [7, 10.6, 0.45], fc(n="apron_front", s="apron_in", ew="apron_edge@1x8", u="apron_edge@2x1",
                                                      d=OMIT))
    # alcas do avental (pescoco)
    m.box(BD, [-3.4, 22.8, -2.9], [0.8, 1.4, 5.8], fc(all="strap"))
    m.box(BD, [2.6, 22.8, -2.9], [0.8, 1.4, 5.8], fc(all="strap"))
    m.box(BD, [-4, 12.2, -2], [8, 1.6, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2", d="leather_top@2x2"),
          inflate=1.0)
    m.box(BD, [-5.4, 10.2, -1.2], [1.4, 2.6, 2.4], fc(all="pouch"))
    if lv >= 1:
        # martelinho pendurado no cinto (lado esquerdo)
        m.box(BD, [4.6, 7.8, -0.3], [0.6, 4.6, 0.6], fc(all="tool_handle"))
        m.box(BD, [4.0, 11.6, -1.0], [1.8, 1.3, 2.0], fc(all="tool_head"))
    if lv >= 1:
        O.gem(m, BD, [0, 13.0, -3.2], [1.3, 1.3, 0.5])
    O.waist(m, "Chest")
    # avental continua abaixo da cintura (painel da frente) + abas curtas de couro dos lados/costas
    m.box("chestSkirtFront", [-3.5, 3.2, -3.0], [7, 8.8, 0.45], fc(n="apron_low", s="apron_in", ew="apron_edge@1x8",
                                                                   u=OMIT, d="apron_edge@2x1"),
          rot=[-3, 0, 0], pivot=[0, 12, -2.75])
    m.box("chestSkirtBack", [-3.2, 9.0, 2.6], [6.4, 3.0, 0.45], fc(s="apron_edge@4x2", n="apron_in", ew="apron_edge@1x2",
                                                                    u=OMIT, d="apron_edge@2x1"),
          rot=[3, 0, 0], pivot=[0, 12, 2.75])
    m.box("chestSkirtRight", [-4.6, 8.6, -1.8], [0.45, 3.4, 3.6], fc(e="apron_edge@2x2", w="apron_in", ns="apron_edge@1x2",
                                                                      u=OMIT, d=OMIT),
          rot=[0, 0, 3], pivot=[-4.4, 12, 0])
    m.mirror("chestSkirtRight", "chestSkirtLeft")
    if lv >= 2:
        O.cape(m, 24.0, 6.5 - 1.5 * (lv - 2), 3.0, 9.0, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)

    # ---------------- bracos: manga arregaçada, braçadeira de couro (luvas de placa nos graus III-IV)
    m.box(RA, [-8, 18, -2], [4, 6, 4], fc(side="shirt", u="shirt", d=OMIT), inflate=0.45)
    m.box(RA, [-8, 17.0, -2], [4, 1.3, 4], fc(side="cuff", u="cuff@2x2", d="cuff@2x2"), inflate=0.7)
    if lv <= 1:
        m.box(RA, [-8, 12, -2], [4, 4.6, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.6)
    else:
        m.box(RA, [-8, 12, -2], [4, 5.0, 4], fc(side="gauntlet", u="o_metal_flat@2x2", d="dark@2x2"), inflate=0.7)
    if lv >= 1:
        m.box(RA, [-8, 16.2, -2], [4, 0.8, 4], fc(side="o_rune_band" if lv >= 2 else "trim_flat",
                                                 u="o_metal_flat@2x2", d="o_metal_flat@2x2"), inflate=0.75)
    m.mirror(RA, LA)
    if lv >= 2:
        O.pauldrons(m, lv - 1, gem_mat="o_gem@2x2" if lv >= 3 else None, gem_size=1.2,
                    spikes=2 if lv == 3 else 0, spike_len=1.6, spike_tip="o_gem@1x1" if lv == 3 else None)

    # ---------------- pernas e botas pesadas
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    if lv >= 1:
        m.box(RL, [-3.5, 4.4, -2.85], [3.2, 2.4, 0.9], fc(n="kneecop", ew="kneecop", u="trim_flat@2x1", d="trim_flat@2x1", s=OMIT))
    m.mirror(RL, LL)
    m.box(RB, [-3.9, 0, -2], [4, 5.4, 4], fc(side="boot", n="boot_front", u=OMIT, d="sole@2x2"), inflate=0.6)
    m.box(RB, [-3.9, 4.8, -2], [4, 1.4, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RB, [-3.7, -0.5, -3.4], [3.6, 2.2, 1.5], fc(n="toecap", ew="toecap", u="toecap", d="sole@2x2", s=OMIT))
    m.mirror(RB, LB)
    return m


# icones 16x16 das pecas (por grau: cores trocadas + gema/brilho)
PIECE_ROWS = {
    "helmet": [
        "................",
        "................",
        "....oooooooo....",
        "...obbbbbbbbo...",
        "..obbBbbbBbbbo..",
        "..obbbbbbbbbbo..",
        "..ossssssssssoo.",
        "..oLLoossoLLoko.",
        "..oLLoossoLLok..",
        "..ooo....oooo...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "..ooo......ooo..",
        ".occco....occco.",
        ".occcsoooosccco.",
        ".occcsaaaascccoo",
        ".ooccaaaaaaccoo.",
        "..ooaaaGaaaaoo..",
        "...oaaaaaaaao...",
        "...oaaaaaaaao...",
        "...obbbBbbbbo...",
        "...oaaaaaaaao...",
        "...oaaapaaaao...",
        "...oaaaaaaaao...",
        "...oooooooooo...",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "................",
        "...oooooooooo...",
        "...obbbBbbbbo...",
        "...oaaaaaaaao...",
        "...oaaaooaaao...",
        "...otttootttoo..",
        "...otttootttoo..",
        "...okkkookkko...",
        "...otttootttoo..",
        "...otttootttoo..",
        "...otttootttoo..",
        "...oooo..oooo...",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...oooo..oooo...",
        "...occco.occco..",
        "...obbbo.obbbo..",
        "...obbbo.obbbo..",
        "...obbbo.obbbo..",
        "..obbbbo.obbbbo.",
        ".ommbbbo.ommbbbo",
        ".ommmmmo.ommmmmo",
        ".ooooooo.ooooooo",
        "................",
        "................",
    ],
}


def piece_icon(piece, lv):
    th = SMITH_T[lv]
    P = th["pal"]
    MT = th["METAL"]
    G = th["GEM"]
    cols = {
        "o": rgb("140C08"),
        "b": BANDANA[2] if (piece == "helmet" and lv <= 1) else (MT[2] if piece == "helmet" else P["LEATH"][2]),
        "B": BANDANA[3] if (piece == "helmet" and lv <= 1) else MT[3],
        "s": P["LEATH"][2],
        "L": rgb("FFB040") if lv == 0 else th["GLOW"],
        "k": P["ACC"][2],
        "c": P["SEC"][2] if lv < 2 else MT[2],
        "a": P["MAIN"][2],
        "G": G[2],
        "p": P["MAIN"][3],
        "t": SOOT[2],
        "m": MT[3],
    }
    c = Canvas(16, 16)
    for y, row in enumerate(PIECE_ROWS[piece]):
        for x, ch in enumerate((row + "." * 16)[:16]):
            if ch != ".":
                c.set(x, y, cols[ch])
    # sombreamento simples: borda de baixo/direita mais escura
    for y in range(16):
        for x in range(16):
            p = c.get(x, y)
            if p[3] and p != cols["o"] and (c.get(x + 1, y) == cols["o"] or c.get(x, y + 1) == cols["o"]):
                c.set(x, y, darken(p, 0.18))
    if lv >= 1:
        sparkle(c, 14, 1, th["GLOW"]) if lv == 3 else c.set(14, 1, G[2])
    if lv >= 2:
        c.set(1, 14, G[3] if lv == 3 else G[2])
    return c


def build_outfit():
    pieces = ("helmet", "chestplate", "leggings", "boots")
    tier_ids = {0: None, 1: "adept", 2: "master", 3: "archmage"}
    for lv in range(4):
        name = TIER_NAMES[lv]
        m = smith_model(name, lv)
        O.build(m, smith_mats(lv))
        tid = tier_ids[lv]
        for piece in pieces:
            iid = "smith_" + piece if tid is None else "smith_" + tid + "_" + piece
            piece_icon(piece, lv).save(os.path.join(A, "textures/item", iid + ".png"))
            item_model(A, iid)
        # camadas vanilla de reserva (so aparecem se o GeckoLib faltar)
        th = SMITH_T[lv]
        mat = "smith_outfit_" + ("apprentice" if tid is None else tid)
        for layer, (w, h) in ((1, (64, 32)), (2, (64, 32))):
            c = Canvas(w, h)
            base = th["pal"]["MAIN"][2] if layer == 1 else SOOT[2]
            rr = random.Random(lv * 7 + layer)
            for y in range(h):
                for x in range(w):
                    c.set(x, y, lighten(base, (rr.random() - 0.5) * 0.12) if rr.random() > 0.5 else darken(base, rr.random() * 0.1))
            c.save(os.path.join(A, "textures/models/armor", mat + "_layer_" + str(layer) + ".png"))


def outfit_recipes():
    # grau I: avental de couro e ferro, na bancada
    shapes = {
        "helmet": (["LIL", "R R"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "R": "minecraft:red_wool"}),
        "chestplate": (["L L", "LIL", "LLL"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot"}),
        "leggings": (["LIL", "L L", "L L"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot"}),
        "boots": (["L L", "I I"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot"}),
    }
    for piece, (pattern, keys) in shapes.items():
        write_json(os.path.join(D, "recipe/smith_" + piece + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": pattern,
            "key": {k: {"item": v} for k, v in keys.items()},
            "result": {"id": NS + ":smith_" + piece, "count": 1},
        })


# =====================================================================================================
#  Martelo da Forja Estelar (entidade GeckoLib)
# =====================================================================================================
def build_entity_geo(m, mats, name):
    """Igual ao outfits.build, mas grava em geo/entity, textures/entity (+ glowmask)."""
    keys = {}
    for b in m.bones:
        for c in b["cubes"]:
            for f, mat in c["faces"].items():
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                keys[k] = (k, k[1], k[2])
    pos = None
    W = Hh = 0
    for cand in ((64, 64), (128, 64), (128, 128), (256, 128), (256, 256)):
        pos = O.pack(list(keys.values()), *cand)
        if pos is not None:
            W, Hh = cand
            break
    if pos is None:
        raise RuntimeError("atlas nao coube: " + name)
    cv, gl = Canvas(W, Hh), Canvas(W, Hh)
    for k, (x, y) in sorted(pos.items(), key=lambda kv: str(kv[0])):
        seed = sum(ord(ch) * (i + 7) for i, ch in enumerate(f"{name}:{k}"))
        mats[k[0]](O.Region(cv, gl, x, y, k[1], k[2], seed))
    bones = []
    for b in m.bones:
        jb = {"name": b["name"]}
        if "parent" in b:
            jb["parent"] = b["parent"]
        jb["pivot"] = [O.r4(v) for v in b["pivot"]]
        if "rotation" in b:
            jb["rotation"] = [O.r4(v) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            jc = {"origin": [O.r4(v) for v in c["origin"]], "size": [O.r4(v) for v in c["size"]]}
            if c["inflate"]:
                jc["inflate"] = O.r4(c["inflate"])
            if c["rotation"] and any(abs(v) > 1e-6 for v in c["rotation"]):
                jc["pivot"] = [O.r4(v) for v in (c["pivot"] or c["origin"])]
                jc["rotation"] = [O.r4(v) for v in c["rotation"]]
            uv = {}
            for f in O.FACES:
                mat = c["faces"].get(f)
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                x, y = pos[k]
                uv[f] = {"uv": [x, y], "uv_size": [k[1], k[2]]}
            if uv:
                jc["uv"] = uv
                cubes.append(jc)
        if cubes:
            jb["cubes"] = cubes
        bones.append(jb)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + name, "texture_width": W, "texture_height": Hh,
                        "visible_bounds_width": 8, "visible_bounds_height": 10, "visible_bounds_offset": [0, 3, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo/entity", name + ".geo.json"), geo)
    cv.save(os.path.join(A, "textures/entity", name + ".png"))
    gl.save(os.path.join(A, "textures/entity", name + "_glowmask.png"))


def star_hammer():
    m = O.Model("smith_star_hammer")
    m.bone("hammer", [0, 72, 0])
    m.bone("runes", [0, 12, 0], parent="hammer")
    # cabo + empunhadura + pomo
    m.box("hammer", [-3, 24, -3], [6, 46, 6], fc(side="handle", u="handle_end", d="handle_end"))
    for y in range(44, 66, 5):
        m.box("hammer", [-3.5, y, -3.5], [7, 2, 7], fc(side="wrap", u="wrap@4x4", d="wrap@4x4"))
    m.box("hammer", [-4.5, 70, -4.5], [9, 5, 9], fc(side="metal_dark", u="metal_top", d="metal_dark"))
    m.box("hammer", [-1.5, 75, -1.5], [3, 3, 3], fc(all="core"))
    # cabeca colossal (face de bater embaixo)
    m.box("hammer", [-20, 0, -12], [40, 24, 24], fc(side="head", u="head_top", d="head_face", e="head_face", w="head_face"))
    m.box("hammer", [-22, -1, -13], [3, 26, 26], fc(all="head_face"))
    m.box("hammer", [19, -1, -13], [3, 26, 26], fc(all="head_face"))
    m.box("hammer", [-6, 24, -7], [12, 4, 14], fc(side="metal_dark", u="metal_top", d="metal_dark"))
    # runas e nucleo estelar (brilham)
    m.box("runes", [-16, 8, -12.3], [32, 8, 0.3], fc(n="rune_band", s=OMIT, ew=OMIT, u=OMIT, d=OMIT))
    m.box("runes", [-16, 8, 12.0], [32, 8, 0.3], fc(s="rune_band", n=OMIT, ew=OMIT, u=OMIT, d=OMIT))
    m.box("runes", [-4, 7, -12.8], [8, 10, 0.8], fc(all="core"))
    m.box("runes", [-4, 7, 12.0], [8, 10, 0.8], fc(all="core"))

    ST = STARP
    def head(r):
        O.plate(r, ST, None, rivets=True, ridge=False, shine=True)
        for _ in range(r.w * r.h // 40):
            i, j = r.rnd.randrange(r.w), r.rnd.randrange(r.h)
            r.set(i, j, lighten(ST[3], 0.3))
    def face(r):
        r.fill(ST[3])
        for j in range(r.h):
            for i in range(r.w):
                if (i + j) % 5 == 0:
                    r.set(i, j, ST[4])
        r.noise(0.03)
    def rune_band(r):
        r.fill(ST[0])
        for k, i in enumerate(range(1, r.w - 3, 5)):
            O.rune_glyph(r, i, 2, rgb("8FA8FF") if k % 2 else rgb("FFB040"), k, glow=True)
    def core(r):
        for j in range(r.h):
            for i in range(r.w):
                d = math.hypot(i + 0.5 - r.w / 2, j + 0.5 - r.h / 2) / max(1, max(r.w, r.h) / 2)
                r.glow(i, j, mix(rgb("FFFFFF"), rgb("6A84F0"), min(1, d)))
    mats = {
        "handle": lambda r: (r.fill(rgb("3A2A1E")), [r.col(i, rgb("4E3A2A")) for i in range(0, r.w, 3)], r.noise(0.05)),
        "handle_end": lambda r: (r.fill(rgb("2A1E14")), r.noise(0.04)),
        "wrap": lambda r: O.wraps(r, pal("2A1608", "5A2E14", "8A4A22", "B06A34")),
        "metal_dark": lambda r: O.metal(r, ST),
        "metal_top": lambda r: (r.fill(ST[2]), r.noise(0.04)),
        "head": head,
        "head_top": lambda r: (O.plate(r, ST, None, rivets=False, shine=False)),
        "head_face": face,
        "rune_band": rune_band,
        "core": core,
    }
    build_entity_geo(m, mats, "smith_star_hammer")
    anim = {"format_version": "1.8.0", "animations": {"animation.smith_star_hammer.slam": {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "hammer": {
                "rotation": {"0.0": [-55, 0, 0], "0.6": [-72, 0, 0],
                             "0.82": {"vector": [0, 0, 0], "easing": "easeInQuad"}, "0.9": [4, 0, 0], "1.0": [-12, 0, 0]},
                "position": {"0.0": [0, 18, 0], "0.6": [0, 30, 0],
                             "0.82": {"vector": [0, 0, 0], "easing": "easeInQuad"}, "0.9": [0, -1, 0], "1.0": [0, 6, 0]},
            },
            "runes": {"scale": {"0.0": [1, 1, 1], "0.82": [1.18, 1.18, 1.18], "1.0": [1, 1, 1]}},
        }}}}
    write_json(os.path.join(A, "animations/entity/smith_star_hammer.animation.json"), anim)


# =====================================================================================================
#  Dados: receitas e tags
# =====================================================================================================
def data():
    write_json(os.path.join(D, "recipe/smith_arcane_anvil.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["EAE", " C ", "III"],
        "key": {"E": {"item": NS + ":essence_fire"}, "A": {"item": "minecraft:anvil"}, "C": {"item": NS + ":mana_crystal"},
                "I": {"item": "minecraft:iron_block"}},
        "result": {"id": NS + ":smith_arcane_anvil", "count": 1},
    })
    write_json(os.path.join(D, "recipe/smith_hammer.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["IBI", " S ", " S "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "B": {"item": "minecraft:iron_block"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": NS + ":smith_hammer", "count": 1},
    })
    write_json(os.path.join(D, "recipe/smith_star_metal.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["SAS", "ANA", "SAS"],
        "key": {"S": {"item": "minecraft:netherite_scrap"}, "A": {"item": NS + ":essence_arcane"},
                "N": {"item": "minecraft:nether_star"}},
        "result": {"id": NS + ":smith_star_metal", "count": 2},
    })
    write_json(os.path.join(D, "recipe/smith_forged_blade.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["I", "I", "S"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:leather"}},
        "result": {"id": NS + ":smith_forged_blade", "count": 1},
    })
    outfit_recipes()
    weapons = ["smith_forged_blade"] + HAMMER_IDS
    def tag(path, values):
        write_json(os.path.join(MERGE, path), {"replace": False, "values": values})
    tag("data/minecraft/tags/item/swords.json", [NS + ":" + w for w in weapons])
    tag("data/c/tags/item/tools/melee_weapon.json", [NS + ":" + w for w in weapons])
    tag("data/minecraft/tags/item/enchantable/durability.json", [NS + ":" + w for w in weapons])
    tag("data/minecraft/tags/block/mineable/pickaxe.json", [NS + ":smith_arcane_anvil"])
    pieces = {"head_armor": "helmet", "chest_armor": "chestplate", "leg_armor": "leggings", "foot_armor": "boots"}
    for t, piece in pieces.items():
        vals = [NS + ":smith_" + piece] + [NS + ":smith_" + tid + "_" + piece for tid in ("adept", "master", "archmage")]
        tag("data/minecraft/tags/item/" + t + ".json", vals)


def main():
    forge_parts()
    build_blade_item()
    build_hammers()
    build_anvil()
    build_outfit()
    star_hammer()
    spark_texture().save(os.path.join(A, "textures/entity/smith_spark.png"))
    tab_icon().save(os.path.join(A, "textures/gui/tab/smith_forge.png"))
    data()
    print("class_smith: assets gerados")


if __name__ == "__main__":
    main()
