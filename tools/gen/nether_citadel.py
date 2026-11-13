"""
Gerador de assets/dados do modulo nether_citadel (v1.6 — Cidadela Demoniaca):
texturas (itens, blocos, armadura, efeitos, criaturas), modelos de item/bloco, modelos GeckoLib + animacoes
(tools/gen/nether_citadel_geo.py), estrutura .nbt + worldgen (Nether, random_spread), minerio de Adamantita, loot tables,
receitas, tags (merge), capitulo do diario e traducoes (src/main/lang/nether_citadel).

Rodar a partir da raiz do projeto:  py -I tools/gen/nether_citadel.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, darken  # noqa: E402
from structgen import Structure, Byte, Double, Float, Int  # noqa: E402
import depths_puzzles as PZ  # noqa: E402
import nether_citadel_geo  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/nether_citadel")
LANG = os.path.join(ROOT, "src/main/lang/nether_citadel")
NS = "isekai_mahou"
CLEAR = (0, 0, 0, 0)


def H(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if "structure_set" in os.path.basename(os.path.dirname(path)) and os.path.exists(path):
        # v1.0: o espacamento das estruturas e ajustado a mao (job "biomes"); regerar nao pode desfazer isso
        with open(path, encoding="utf-8") as f:
            old = json.load(f).get("placement", {})
        for k in ("spacing", "separation"):
            if k in old:
                obj["placement"][k] = old[k]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  TEXTURAS DE ITEM (16x16)
# =====================================================================================
ADA = H("#8E1A2A")       # adamantita: carmesim escuro metalico
ADA_L = H("#D24A5A")
ADA_D = H("#4A0A14")
ADA_X = H("#FFB0B8")
HANDLE = H("#3A2418")
HANDLE_L = H("#5E3A24")
BLAZE = H("#F2B233")


def save_item(c, name):
    c.save(os.path.join(ASSETS, f"textures/item/{name}.png"))


def tool_handle(c, n=7):
    """Cabo diagonal de vara de blaze (canto inferior esquerdo)."""
    for i in range(n):
        c.set(2 + i, 13 - i, BLAZE if i % 2 == 0 else H("#C77A1A"))
        c.set(1 + i, 13 - i, darken(BLAZE, 0.35))


def tex_raw():
    c = Canvas(16, 16)
    c.circle(8, 9, 5, ADA)
    c.circle(5, 6, 3, ADA)
    c.circle(11, 6, 2, ADA_D)
    c.shade_sphere(8, 8, 6)
    for (x, y) in ((6, 7), (9, 10), (10, 6), (5, 10)):
        c.set(x, y, ADA_X)
    c.outline(H("#1E0408"))
    c.noise(0.06, 3)
    save_item(c, "raw_adamantite")


def tex_ingot():
    c = Canvas(16, 16)
    rows = ["................",
            "................",
            "................",
            "................",
            ".......xxxxxx...",
            ".....xxhhhhhhx..",
            "...xxhhhhhhhhx..",
            "..xhhhhhhhhhmx..",
            "..xmmmhhhhhmmx..",
            "..xmmmmmmmmmdx..",
            "..xdddmmmmmdx...",
            "...xxdddddddx...",
            ".....xxxxxxx....",
            "................",
            "................",
            "................"]
    c.from_rows(rows, {"x": H("#1E0408"), "h": ADA_L, "m": ADA, "d": ADA_D})
    c.set(7, 6, ADA_X); c.set(8, 6, ADA_X); c.set(5, 7, ADA_X)
    save_item(c, "adamantite_ingot")


def tex_sword(name, blade, edge, guard, length=10):
    c = Canvas(16, 16)
    for i in range(length):
        x, y = 4 + i, 11 - i
        c.set(x, y, blade); c.set(x + 1, y, edge); c.set(x, y - 1, edge)
    for i in range(3):
        c.set(2 + i, 13 - i, HANDLE_L if i % 2 else HANDLE)
    c.set(3, 10, guard); c.set(4, 10, guard); c.set(5, 13, guard); c.set(5, 12, guard); c.set(2, 9, guard); c.set(6, 14, guard)
    c.set(1, 14, H("#1A1A1A"))
    c.outline(H("#14060A"))
    save_item(c, name)


def tex_pickaxe():
    c = Canvas(16, 16)
    tool_handle(c, 9)
    head = [(4, 2), (5, 2), (6, 2), (7, 2), (8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (12, 7), (13, 8), (13, 9), (13, 10)]
    for (x, y) in head:
        c.set(x, y, ADA); c.set(x, y + 1, ADA_D)
    for (x, y) in ((5, 2), (8, 3), (11, 5), (13, 9)):
        c.set(x, y, ADA_L)
    c.outline(H("#14060A"))
    save_item(c, "adamantite_pickaxe")


def tex_axe():
    c = Canvas(16, 16)
    tool_handle(c, 10)
    rows = ["......xxxx",
            ".....xhhhmx",
            "....xhmmmmdx",
            "....xhmmmmmdx",
            "....xmmmmmmdx",
            ".....xmmmxxdx",
            "......xddx.xx",
            ".......xx....."]
    c.from_rows(rows, {"x": H("#14060A"), "h": ADA_L, "m": ADA, "d": ADA_D}, ox=3, oy=1)
    c.set(9, 3, H("#FFB0B8"))
    save_item(c, "adamantite_axe")


def tex_armor_icon(name, rows):
    c = Canvas(16, 16)
    c.from_rows(rows, {"x": H("#14060A"), "h": ADA_L, "m": ADA, "d": ADA_D, "g": H("#D9A632"), "e": H("#FF6A1E")})
    c.noise(0.05, len(name))
    save_item(c, name)


def tex_armor_icons():
    tex_armor_icon("adamantite_helmet", [
        "................", "................", "................",
        "....xxxxxxxx....", "...xhhhhhhhmx...", "..xhmmmmmmmmdx..", "..xmgggggggmdx..",
        "..xmxeexxeexdx..", "..xmx......xdx..", "..xdx......xdx..", "...xx......xx...",
        "................", "................", "................", "................", "................"])
    tex_armor_icon("adamantite_chestplate", [
        "................", "..xxx.....xxx...", ".xhhmx...xmmdx..", ".xhmmmxxxmmmdx..",
        ".xmmmmggggmmdx..", "..xxmmmeemmdxx..", "....xmmmmmdx....", "....xhmmmmdx....",
        "....xmmgmmdx....", "....xmmmmmdx....", "....xmmmmmdx....", "....xddddddx....",
        "....xxxxxxxx....", "................", "................", "................"])
    tex_armor_icon("adamantite_leggings", [
        "................", "................", "...xxxxxxxxxx...", "...xhgggggggx...",
        "...xhmmmmmmdx...", "...xhmmxxmmdx...", "...xhmx..xmdx...", "...xhmx..xmdx...",
        "...xhmx..xmdx...", "...xhmx..xmdx...", "...xmdx..xmdx...", "...xmdx..xddx...",
        "...xxxx..xxxx...", "................", "................", "................"])
    tex_armor_icon("adamantite_boots", [
        "................", "................", "................", "................",
        "................", "...xxxx..xxxx...", "...xhmx..xhmx...", "...xhmx..xhmx...",
        "...xhmx..xhmx...", "..xhmmx..xhmmx..", ".xhmmdx..xhmmdx.", ".xggggx..xggggx.",
        ".xxxxxx..xxxxxx.", "................", "................", "................"])


def tex_antimagic_blade():
    """Espadao negro, largo e lascado (estilo grimorio de 5 folhas)."""
    c = Canvas(16, 16)
    dark, mid, edge = H("#1A1A20"), H("#2E2E38"), H("#5A5A6A")
    for i in range(11):
        x, y = 3 + i, 12 - i
        for w in range(-1, 2):
            c.set(x + w, y + w, mid if w == 0 else dark)
        c.set(x + 1, y - 1, edge if i % 3 else H("#8A8AA0"))
    for (x, y) in ((6, 7), (9, 4), (11, 2)):
        c.set(x, y, CLEAR)
    c.set(2, 13, H("#4A1A10")); c.set(1, 14, H("#4A1A10")); c.set(0, 15, H("#2A0A08"))
    for (x, y) in ((1, 11), (2, 12), (4, 14), (5, 15), (3, 12)):
        c.set(x, y, H("#3A3A48"))
    c.outline(H("#060608"))
    c.set(8, 7, H("#2AD86A"))  # brilho verde-antimagia no centro
    save_item(c, "demon_antimagic_blade")


def tex_sun_katana():
    c = Canvas(16, 16)
    for i in range(11):
        x, y = 4 + i, 11 - i
        c.set(x, y, H("#FF9A2A") if i > 3 else H("#E8E0D8"))
        c.set(x + 1, y, H("#FFD84A") if i > 3 else H("#FFFFFF"))
        if i % 3 == 0 and i > 3:
            c.set(x, y - 1, H("#FF5A1E"))
    c.set(3, 11, H("#1A1A1A")); c.set(4, 12, H("#1A1A1A")); c.set(2, 10, H("#B5121E")); c.set(5, 13, H("#B5121E"))
    for i in range(3):
        c.set(1 + i, 14 - i, H("#2A1A2A") if i % 2 else H("#B5121E"))
    c.outline(H("#1A0606"))
    save_item(c, "citadel_sun_katana")


def tex_sigil():
    c = Canvas(16, 16)
    c.circle(8, 9, 5, H("#5A0C14"))
    c.circle(8, 9, 4, H("#B5121E"))
    c.circle(8, 9, 2, H("#FFB03A"))
    c.shade_sphere(8, 9, 5, 0.3, 0.4)
    for (x, y) in ((4, 4), (3, 3), (3, 2), (12, 4), (13, 3), (13, 2)):
        c.set(x, y, H("#2A2024"))
    c.set(8, 9, H("#FFF0A0"))
    c.outline(H("#14060A"))
    save_item(c, "demon_lord_sigil")


# =====================================================================================
#  TEXTURAS DE BLOCO
# =====================================================================================
def save_block(c, name):
    c.save(os.path.join(ASSETS, f"textures/block/{name}.png"))


def netherrack_bg(seed):
    rnd = random.Random(seed)
    c = Canvas(16, 16)
    base = [H("#6E2E2E"), H("#5A2424"), H("#7E3A36"), H("#4E1E1E")]
    for y in range(16):
        for x in range(16):
            c.set(x, y, base[rnd.randrange(4)] if rnd.random() < 0.7 else base[0])
    return c


def tex_blocks():
    rnd = random.Random("ada_ore")
    c = netherrack_bg("ore")
    veins = [(3, 4), (4, 4), (4, 5), (5, 5), (10, 3), (11, 3), (11, 4), (12, 4), (6, 10), (7, 10), (7, 11), (8, 11),
             (12, 11), (12, 12), (13, 12), (2, 12), (3, 13)]
    for (x, y) in veins:
        c.set(x, y, ADA if rnd.random() < 0.6 else ADA_L)
        c.set(x, y + 1, H("#1E0408"))
    for (x, y) in ((4, 4), (11, 3), (7, 10), (12, 11)):
        c.set(x, y, ADA_X)
    save_block(c, "adamantite_ore")

    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, ADA)
    c.noise(0.07, 11)
    c.outline_rect(0, 0, 15, 15, ADA_D)
    c.outline_rect(1, 1, 14, 14, ADA_L)
    c.rect(7, 1, 8, 14, ADA_D); c.rect(1, 7, 14, 8, ADA_D)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, ADA_X)
    save_block(c, "adamantite_block")

    # Altar do Pacto: pedra-negra com runas vermelhas (lados) e circulo de pacto (topo)
    side = Canvas(16, 16)
    side.rect(0, 0, 15, 15, H("#2A2630"))
    side.noise(0.08, 5)
    side.rect(0, 12, 15, 15, H("#1E1A22"))
    side.rect(0, 0, 15, 1, H("#3A3440"))
    for (x, y) in ((3, 5), (4, 6), (3, 7), (7, 4), (8, 5), (8, 7), (7, 8), (12, 5), (11, 6), (12, 7)):
        side.set(x, y, H("#E0242C"))
    save_block(side, "demon_pact_altar_side")
    top = Canvas(16, 16)
    top.rect(0, 0, 15, 15, H("#1E1A22"))
    top.noise(0.06, 6)
    for a in range(48):
        ang = 2 * math.pi * a / 48
        top.set(int(round(7.5 + 6 * math.cos(ang))), int(round(7.5 + 6 * math.sin(ang))), H("#E0242C"))
    for k in range(5):
        a1 = 2 * math.pi * k / 5 - math.pi / 2
        a2 = 2 * math.pi * ((k + 2) % 5) / 5 - math.pi / 2
        top.line(int(round(7.5 + 6 * math.cos(a1))), int(round(7.5 + 6 * math.sin(a1))),
                 int(round(7.5 + 6 * math.cos(a2))), int(round(7.5 + 6 * math.sin(a2))), H("#B5121E"))
    top.circle(7, 7, 1, H("#FF6A3A"))
    save_block(top, "demon_pact_altar_top")

    # Forja Infernal: tijolos do Nether com boca de fogo e topo de lava
    fs = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            row = y // 4
            brick = ((x + (2 if row % 2 else 0)) % 8 == 0) or y % 4 == 0
            fs.set(x, y, H("#2A1014") if brick else H("#4A1A1E"))
    fs.noise(0.08, 7)
    fs.rect(4, 7, 11, 13, H("#1A0606"))
    fs.rect(5, 9, 10, 13, H("#FF6A1E"))
    fs.rect(6, 10, 9, 13, H("#FFC23A"))
    fs.rect(7, 11, 8, 12, H("#FFF0A0"))
    save_block(fs, "demon_infernal_forge_side")
    ft = Canvas(16, 16)
    ft.rect(0, 0, 15, 15, H("#2A1014"))
    ft.rect(2, 2, 13, 13, H("#D4501E"))
    rnd = random.Random("lava")
    for y in range(2, 14):
        for x in range(2, 14):
            if rnd.random() < 0.35:
                ft.set(x, y, H("#FFB03A") if rnd.random() < 0.6 else H("#FF6A1E"))
    ft.outline_rect(2, 2, 13, 13, H("#1A0606"))
    save_block(ft, "demon_infernal_forge_top")


def tex_armor_layers():
    for layer in (1, 2):
        c = Canvas(64, 32)
        c.rect(0, 0, 63, 31, ADA)
        c.noise(0.09, 20 + layer)
        rnd = random.Random("layer%d" % layer)
        for _ in range(40):
            c.set(rnd.randrange(64), rnd.randrange(32), ADA_L)
        if layer == 1:
            # capacete: viseira escura com olhos em brasa e coroa de chifres
            c.rect(8, 11, 15, 12, H("#14060A"))
            c.set(9, 11, H("#FF6A1E")); c.set(14, 11, H("#FF6A1E"))
            c.rect(8, 8, 15, 8, H("#D9A632"))
            c.rect(20, 20, 27, 20, H("#D9A632"))   # gola do peitoral
            c.rect(23, 22, 24, 28, ADA_D)
        else:
            c.rect(4, 20, 7, 20, H("#D9A632"))
        save_dir = os.path.join(ASSETS, "textures/models/armor")
        c.save(os.path.join(save_dir, f"adamantite_layer_{layer}.png"))


def tex_effects():
    c = Canvas(18, 18)
    c.circle(9, 10, 6, H("#5A0C14"))
    c.circle(9, 10, 4, H("#B0122A"))
    c.shade_sphere(9, 10, 6, 0.3, 0.4)
    for (x, y) in ((4, 5), (3, 4), (3, 3), (2, 2), (14, 5), (15, 4), (15, 3), (16, 2)):
        c.set(x, y, H("#E8D8C0"))
    c.set(7, 9, H("#FFD84A")); c.set(11, 9, H("#FFD84A"))
    c.outline(H("#14060A"))
    c.save(os.path.join(ASSETS, "textures/mob_effect/demon_pact.png"))
    c = Canvas(18, 18)
    for i in range(4):
        c.circle(4 + i * 3, 4 + i * 3, 2, H("#4A4048"))
        c.circle(4 + i * 3, 4 + i * 3, 1, CLEAR)
    c.circle(13, 13, 3, H("#3A0A12"))
    c.set(13, 13, H("#B0122A"))
    c.outline(H("#0A0406"))
    c.save(os.path.join(ASSETS, "textures/mob_effect/demon_debt.png"))


# =====================================================================================
#  MODELOS DE ITEM/BLOCO
# =====================================================================================
def item_model(item, parent="minecraft:item/generated", tex=None):
    write_json(os.path.join(ASSETS, f"models/item/{item}.json"),
               {"parent": parent, "textures": {"layer0": f"{NS}:item/{tex or item}"}})


def block_models():
    for b in ("adamantite_ore", "adamantite_block"):
        write_json(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write_json(os.path.join(ASSETS, f"models/block/{b}.json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
        write_json(os.path.join(ASSETS, f"models/item/{b}.json"), {"parent": f"{NS}:block/{b}"})

    def faces(tex_side, tex_top, tex_bottom):
        return {"north": {"texture": tex_side}, "south": {"texture": tex_side}, "east": {"texture": tex_side},
                "west": {"texture": tex_side}, "up": {"texture": tex_top}, "down": {"texture": tex_bottom}}

    write_json(os.path.join(ASSETS, "models/block/demon_pact_altar.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/demon_pact_altar_side", "side": f"{NS}:block/demon_pact_altar_side",
                     "top": f"{NS}:block/demon_pact_altar_top", "base": "minecraft:block/polished_blackstone"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 4, 15], "faces": faces("#base", "#base", "#base")},
            {"from": [3, 4, 3], "to": [13, 10, 13], "faces": faces("#side", "#base", "#base")},
            {"from": [2, 10, 2], "to": [14, 12, 14], "faces": faces("#base", "#top", "#base")}]})
    write_json(os.path.join(ASSETS, "models/block/demon_infernal_forge.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/demon_infernal_forge_side", "side": f"{NS}:block/demon_infernal_forge_side",
                     "top": f"{NS}:block/demon_infernal_forge_top", "base": "minecraft:block/nether_bricks"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 11, 16], "faces": faces("#side", "#top", "#base")},
            {"from": [1, 11, 1], "to": [15, 14, 15], "faces": faces("#base", "#top", "#base")}]})
    for b in ("demon_pact_altar", "demon_infernal_forge"):
        write_json(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write_json(os.path.join(ASSETS, f"models/item/{b}.json"), {"parent": f"{NS}:block/{b}"})


def item_models():
    for it in ("raw_adamantite", "adamantite_ingot", "adamantite_helmet", "adamantite_chestplate", "adamantite_leggings",
               "adamantite_boots", "demon_lord_sigil"):
        item_model(it)
    for it in ("adamantite_sword", "adamantite_pickaxe", "adamantite_axe", "demon_antimagic_blade", "citadel_sun_katana"):
        item_model(it, "minecraft:item/handheld")
    for e in ("demon_lord", "demon_knight", "demon_succubus"):
        write_json(os.path.join(ASSETS, f"models/item/{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  ESTRUTURA — Cidadela Demoniaca (47 x 34 x 55)
# =====================================================================================
AIR = "minecraft:air"
TRAVEL_STONE = NS + ":travel_stone"
W, HGT, D = 47, 34, 55
F = 3
# v1.0 — "nasce meio enterrada": o molde ganhou uma esplanada de ar em volta da fortaleza (A blocos a leste/oeste,
# AN ao norte, AS ao sul, na frente do portao) e UP blocos de ar acima; do portao sai uma estrada (pecas jigsaw).
A, AN, AS, UP = 10, 6, 14, 6
BW, BD, BH = W + 2 * A, D + AN + AS, HGT + UP
ROAD_W, ROAD_H, ROAD_L = 7, F + 9, 16
ROAD_POOL = NS + ":nether_citadel/road"


def mc(n):
    return n if ":" in n else "minecraft:" + n


def st(name, **props):
    name = mc(name)
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


class B(Structure):
    def __init__(self, name, sx, sy, sz):
        super().__init__(sx, sy, sz)
        self.rnd = random.Random("nether_citadel/" + name)

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return s
        return weighted[-1][0]

    def fillp(self, x0, y0, z0, x1, y1, z1, weighted):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, self.pick(weighted))

    def chest(self, x, y, z, loot, facing="north"):
        self.set(x, y, z, st("chest", facing=facing, type="single", waterlogged="false"),
                 nbt={"LootTable": f"{NS}:chests/{loot}"} if loot else None)

    def sign(self, x, y, z, state, keys):
        msgs = [json.dumps({"translate": k}) for k in keys] + ['""'] * (4 - len(keys))
        self.set(x, y, z, state, nbt={
            "front_text": {"messages": msgs, "color": "orange", "has_glowing_text": Byte(1)},
            "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)},
            "is_waxed": Byte(1)})

    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        nbt = {"PersistenceRequired": Byte(1), "Rotation": [Float(yaw), Float(0.0)]}
        if extra:
            nbt.update(extra)
        self.entity(x, y, z, f"{NS}:{eid}", nbt)

    def hanging_lantern(self, x, ytop, z, length, soul=True):
        for y in range(ytop - length + 1, ytop + 1):
            self.set(x, y, z, st("chain", axis="y", waterlogged="false"))
        self.set(x, ytop - length, z, st("soul_lantern" if soul else "lantern", hanging="true", waterlogged="false"))


WALL = [("minecraft:nether_bricks", 6), ("minecraft:cracked_nether_bricks", 2), ("minecraft:red_nether_bricks", 1)]
DARK = [("minecraft:polished_blackstone_bricks", 6), ("minecraft:cracked_polished_blackstone_bricks", 2),
        ("minecraft:blackstone", 1), ("minecraft:gilded_blackstone", 0.15)]
FOUND = [("minecraft:polished_blackstone", 4), ("minecraft:basalt[axis=y]", 2), ("minecraft:blackstone", 2)]


def citadel():
    s = B("keep", W, HGT, D)
    s.fill(0, F, 0, W - 1, HGT - 1, D - 1, AIR)
    s.fillp(0, 0, 0, W - 1, F - 1, D - 1, FOUND)
    s.fillp(0, F, 0, W - 1, F, D - 1, DARK)

    # ---------------- muralha externa (2 de espessura) com ameias
    top = F + 10
    for (x0, x1, z0, z1) in ((0, 1, 0, D - 1), (W - 2, W - 1, 0, D - 1), (0, W - 1, D - 2, D - 1)):
        s.fillp(x0, F + 1, z0, x1, top, z1, WALL)
        s.fillp(x0, F + 1, z0, x1, F + 1, z1, DARK)
    for x in range(0, W):
        if x % 2 == 0:
            s.set(x, top + 1, D - 1, "minecraft:nether_bricks")
    for z in range(0, D):
        if z % 2 == 0:
            s.set(0, top + 1, z, "minecraft:nether_bricks")
            s.set(W - 1, top + 1, z, "minecraft:nether_bricks")
    # torres dos cantos do sul (7x7) com braseiro no topo
    for (tx, tz) in ((0, D - 7), (W - 7, D - 7)):
        s.fillp(tx, F + 1, tz, tx + 6, F + 15, tz + 6, WALL)
        s.fill(tx + 1, F + 1, tz + 1, tx + 5, F + 14, tz + 5, AIR)
        s.fillp(tx, F + 15, tz, tx + 6, F + 15, tz + 6, DARK)
        for i in range(0, 7, 2):
            for (xx, zz) in ((tx + i, tz), (tx + i, tz + 6), (tx, tz + i), (tx + 6, tz + i)):
                s.set(xx, F + 16, zz, "minecraft:red_nether_bricks")
        s.set(tx + 3, F + 16, tz + 3, "minecraft:soul_soil")
        s.set(tx + 3, F + 17, tz + 3, "minecraft:soul_fire")
        for y in (F + 6, F + 11):
            s.set(tx + 3, y, tz + 6 if tz > 0 else tz, st("iron_bars", north="true", south="true", east="false", west="false", waterlogged="false"))

    # ---------------- portao (sul)
    gx0, gx1 = 20, 26
    s.fill(gx0, F + 1, D - 2, gx1, F + 7, D - 1, AIR)
    s.fill(gx0 - 1, F + 1, D - 2, gx0 - 1, F + 9, D - 1, "minecraft:chiseled_polished_blackstone")
    s.fill(gx1 + 1, F + 1, D - 2, gx1 + 1, F + 9, D - 1, "minecraft:chiseled_polished_blackstone")
    s.fill(gx0, F + 8, D - 2, gx1, F + 9, D - 1, "minecraft:red_nether_bricks")
    for x in range(gx0, gx1 + 1):
        s.set(x, F + 7, D - 1, st("iron_bars", east="true", west="true", north="false", south="false", waterlogged="false"))
    s.set(gx0 - 1, F + 10, D - 1, "minecraft:soul_soil"); s.set(gx0 - 1, F + 11, D - 1, "minecraft:soul_fire")
    s.set(gx1 + 1, F + 10, D - 1, "minecraft:soul_soil"); s.set(gx1 + 1, F + 11, D - 1, "minecraft:soul_fire")
    s.sign(gx0 - 2, F + 2, D - 3, st("crimson_wall_sign", facing="north", waterlogged="false"),
           [f"sign.{NS}.nether_citadel.warn_1", f"sign.{NS}.nether_citadel.warn_2", f"sign.{NS}.nether_citadel.warn_3"])
    s.set(gx0 - 2, F + 1, D - 4, TRAVEL_STONE)

    # ---------------- patio: caminho central + fonte de lava
    for z in range(31, D - 2):
        for x in range(21, 26):
            s.set(x, F, z, "minecraft:polished_blackstone" if x in (21, 25) else "minecraft:polished_blackstone_bricks")
        s.set(20, F, z, "minecraft:red_nether_bricks"); s.set(26, F, z, "minecraft:red_nether_bricks")
    fx, fz = 23, 42
    s.fill(fx - 3, F, fz - 3, fx + 3, F, fz + 3, "minecraft:polished_blackstone_bricks")
    for (x, z) in [(fx + dx, fz + dz) for dx in range(-3, 4) for dz in range(-3, 4) if max(abs(dx), abs(dz)) == 3]:
        s.set(x, F + 1, z, st("polished_blackstone_brick_wall", up="true", north="none", south="none", east="none", west="none", waterlogged="false"))
    s.fill(fx - 2, F - 1, fz - 2, fx + 2, F - 1, fz + 2, "minecraft:magma_block")
    s.fill(fx - 2, F, fz - 2, fx + 2, F, fz + 2, "minecraft:lava")
    s.fill(fx, F, fz, fx, F + 4, fz, "minecraft:chiseled_polished_blackstone")
    s.set(fx, F + 5, fz, "minecraft:soul_soil"); s.set(fx, F + 6, fz, "minecraft:soul_fire")
    # braseiros ao longo do caminho
    for z in (34, 38, 46, 50):
        for x in (19, 27):
            s.set(x, F + 1, z, "minecraft:polished_blackstone")
            s.set(x, F + 2, z, st("soul_campfire", lit="true", facing="north", signal_fire="false", waterlogged="false"))

    # ---------------- Forja Infernal (oeste do patio)
    ox0, ox1, oz0, oz1 = 3, 13, 33, 43
    s.fillp(ox0, F, oz0, ox1, F, oz1, [("minecraft:nether_bricks", 3), ("minecraft:polished_blackstone", 1)])
    for (x, z) in ((ox0, oz0), (ox1, oz0), (ox0, oz1), (ox1, oz1)):
        s.fill(x, F + 1, z, x, F + 5, z, "minecraft:polished_basalt[axis=y]")
    s.fill(ox0, F + 6, oz0, ox1, F + 6, oz1, st("nether_brick_slab", type="bottom", waterlogged="false"))
    s.set(8, F + 1, 37, f"{NS}:demon_infernal_forge")
    s.set(8, F + 1, 40, st("anvil", facing="east"))
    s.set(4, F + 1, 35, st("blast_furnace", facing="east", lit="true"))
    s.set(4, F + 1, 36, st("blast_furnace", facing="east", lit="false"))
    s.set(12, F + 1, 35, "minecraft:lava_cauldron")
    s.set(12, F + 1, 41, "minecraft:smithing_table")
    s.chest(4, F + 1, 41, "nether_citadel_armory", "east")
    s.set(4, F + 1, 40, st("barrel", facing="up", open="false"))
    s.set(12, F + 1, 36, "minecraft:magma_block")
    s.hanging_lantern(8, F + 5, 38, 2, soul=False)

    # ---------------- Santuario do Altar do Pacto (leste do patio)
    px0, px1, pz0, pz1 = 33, 43, 33, 43
    s.fillp(px0, F + 1, pz0, px1, F + 7, pz1, [("minecraft:red_nether_bricks", 5), ("minecraft:nether_bricks", 2)])
    s.fill(px0 + 1, F + 1, pz0 + 1, px1 - 1, F + 6, pz1 - 1, AIR)
    s.fillp(px0, F + 8, pz0, px1, F + 8, pz1, DARK)
    for x in range(px0 + 1, px1):
        for z in range(pz0 + 1, pz1):
            s.set(x, F, z, "minecraft:crying_obsidian" if (x + z) % 2 == 0 else "minecraft:obsidian")
    s.fill(px0, F + 1, 37, px0, F + 4, 39, AIR)  # porta (oeste)
    cx, cz = 38, 38
    s.set(cx, F + 1, cz, f"{NS}:demon_pact_altar")
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        s.set(cx + dx, F + 1, cz + dz, st("black_candle", candles="4", lit="true", waterlogged="false"))
    for (dx, dz) in ((-3, 0), (3, 0), (0, -3), (0, 3)):
        s.set(cx + dx, F, cz + dz, "minecraft:soul_soil")
        s.set(cx + dx, F + 1, cz + dz, "minecraft:soul_fire")
    s.hanging_lantern(cx, F + 6, cz, 2)
    for z in (35, 41):
        s.set(px0, F + 4, z, st("iron_bars", north="true", south="true", east="false", west="false", waterlogged="false"))
    s.sign(px0 - 1, F + 3, 36, st("crimson_wall_sign", facing="west", waterlogged="false"),
           [f"sign.{NS}.nether_citadel.pact_1", f"sign.{NS}.nether_citadel.pact_2", f"sign.{NS}.nether_citadel.pact_3"])

    # ---------------- quartel (sul-oeste) e deposito (sul-leste)
    for (bx0, bx1) in ((3, 16), (30, 43)):
        bz0, bz1 = 46, 51
        s.fillp(bx0, F + 1, bz0, bx1, F + 5, bz1, WALL)
        s.fill(bx0 + 1, F + 1, bz0 + 1, bx1 - 1, F + 4, bz1 - 1, AIR)
        s.fillp(bx0, F + 6, bz0, bx1, F + 6, bz1, DARK)
        dx = (bx0 + bx1) // 2
        s.fill(dx - 1, F + 1, bz0, dx + 1, F + 3, bz0, AIR)
        for x in range(bx0 + 2, bx1 - 1, 3):
            s.set(x, F + 1, bz1 - 1, st("barrel", facing="up", open="false"))
        s.hanging_lantern(dx, F + 5, bz0 + 3, 1)
    s.chest(4, F + 1, 47, "nether_citadel_armory", "east")
    s.chest(42, F + 1, 47, "nether_citadel_armory", "west")

    # ---------------- torreao / salao do trono (norte)
    kz1 = 30
    s.fillp(0, F + 1, 0, W - 1, F + 17, 1, WALL)
    s.fillp(0, F + 1, 0, 1, F + 17, kz1, WALL)
    s.fillp(W - 2, F + 1, 0, W - 1, F + 17, kz1, WALL)
    s.fillp(0, F + 1, kz1 - 1, W - 1, F + 17, kz1, WALL)
    s.fillp(0, F + 18, 0, W - 1, F + 18, kz1, DARK)
    for x in range(0, W, 2):
        s.set(x, F + 19, kz1, "minecraft:red_nether_bricks")
        s.set(x, F + 19, 0, "minecraft:red_nether_bricks")
    for z in range(0, kz1 + 1, 2):
        s.set(0, F + 19, z, "minecraft:red_nether_bricks")
        s.set(W - 1, F + 19, z, "minecraft:red_nether_bricks")
    # torres dos cantos do norte, mais altas
    for (tx, tz) in ((0, 0), (W - 7, 0)):
        s.fillp(tx, F + 1, tz, tx + 6, F + 24, tz + 6, WALL)
        s.fillp(tx, F + 24, tz, tx + 6, F + 24, tz + 6, DARK)
        for i in range(0, 7, 2):
            for (xx, zz) in ((tx + i, tz), (tx + i, tz + 6), (tx, tz + i), (tx + 6, tz + i)):
                s.set(xx, F + 25, zz, "minecraft:red_nether_bricks")
        s.set(tx + 3, F + 25, tz + 3, "minecraft:soul_soil")
        s.set(tx + 3, F + 26, tz + 3, "minecraft:soul_fire")
    # interior do salao (fica DENTRO das paredes: x 2..44, z 2..28; as torres norte invadem os cantos)
    s.fill(2, F + 1, 2, W - 3, F + 17, kz1 - 2, AIR)
    for x in range(2, W - 2):
        for z in range(2, kz1 - 1):
            if (0 <= x <= 6 or W - 7 <= x <= W - 1) and z <= 6:
                continue
            s.set(x, F, z, "minecraft:polished_blackstone" if (x + z) % 2 else "minecraft:polished_blackstone_bricks")
    for (tx, tz) in ((0, 0), (W - 7, 0)):
        s.fillp(tx + (2 if tx == 0 else 0), F + 1, tz + 2, tx + (6 if tx == 0 else 4), F + 17, tz + 6, WALL)
    # porta do salao
    s.fill(20, F + 1, kz1 - 1, 26, F + 8, kz1, AIR)
    s.fill(19, F + 1, kz1, 19, F + 10, kz1, "minecraft:chiseled_polished_blackstone")
    s.fill(27, F + 1, kz1, 27, F + 10, kz1, "minecraft:chiseled_polished_blackstone")
    s.fill(20, F + 9, kz1, 26, F + 10, kz1, "minecraft:red_nether_bricks")
    s.set(23, F + 10, kz1, "minecraft:gilded_blackstone")
    # pilares + lanternas
    for z in (8, 14, 20, 26):
        for x in (9, 36):
            s.fill(x, F + 1, z, x + 1, F + 16, z + 1, "minecraft:polished_basalt[axis=y]")
            s.fill(x, F + 1, z, x + 1, F + 1, z + 1, "minecraft:chiseled_polished_blackstone")
            s.fill(x, F + 16, z, x + 1, F + 17, z + 1, "minecraft:chiseled_polished_blackstone")
            s.set(x + (2 if x < 23 else -1), F + 7, z, "minecraft:shroomlight")
    for z in (11, 17, 23):
        for x in (14, 23, 32):
            s.hanging_lantern(x, F + 17, z, 4)
    # tapete vermelho com bordas douradas
    for z in range(9, kz1 - 1):
        for x in range(21, 26):
            s.set(x, F + 1, z, "minecraft:red_carpet" if 22 <= x <= 24 else "minecraft:yellow_carpet")
    # estandartes nas paredes
    for z in (10, 16, 22):
        s.set(2, F + 8, z, st("red_wall_banner", facing="east"))
        s.set(W - 3, F + 8, z, st("red_wall_banner", facing="west"))
    # estrado e trono
    s.fill(14, F + 1, 2, 32, F + 2, 7, "minecraft:polished_blackstone_bricks")
    for x in range(14, 33):
        s.set(x, F + 1, 8, st("polished_blackstone_brick_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"))
        s.set(x, F + 2, 7, st("polished_blackstone_brick_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"))
    s.set(23, F + 3, 3, st("blackstone_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"))
    s.set(22, F + 3, 3, "minecraft:gold_block"); s.set(24, F + 3, 3, "minecraft:gold_block")
    s.fill(22, F + 3, 2, 24, F + 9, 2, "minecraft:gilded_blackstone")
    s.fill(23, F + 4, 2, 23, F + 8, 2, "minecraft:crying_obsidian")
    s.set(21, F + 9, 2, "minecraft:red_nether_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    s.set(25, F + 9, 2, "minecraft:red_nether_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    s.set(21, F + 10, 2, "minecraft:red_nether_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    s.set(25, F + 10, 2, "minecraft:red_nether_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]")
    for x in (16, 30):
        s.set(x, F + 3, 4, "minecraft:polished_blackstone")
        s.set(x, F + 4, 4, st("soul_campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    s.chest(18, F + 3, 3, "nether_citadel_treasury", "south")
    s.chest(28, F + 3, 3, "nether_citadel_treasury", "south")

    # ---------------- guarnicao e chefe
    for (x, z) in ((21.5, 50.5), (25.5, 50.5), (19.5, 32.5), (27.5, 32.5)):
        s.mob(x, F + 1, z, "demon_knight", 180.0 if z > 40 else 0.0)
    for (x, z) in ((30.5, 40.5), (15.5, 44.5), (35.5, 37.5)):
        s.mob(x, F + 1, z, "demon_succubus", 90.0)
    s.mob(23.5, F + 1, 14.5, "demon_lord", 0.0, {"Lair": "nether_citadel"})

    # ---------------- v1.0: Camara do Selo (o antigo deposito, sudeste) — enigma de luz e espelhos
    for x in range(31, 43):
        for z in range(47, 51):
            s.set(x, F + 1, z, AIR)
    for x in range(31, 43):
        for z in range(47, 51):
            s.set(x, F, z, "minecraft:polished_blackstone" if (x + z) % 2 else "minecraft:polished_blackstone_bricks")
    PZ.put_core(s, 31, F + 1, 47, 12, facing="east")
    s.set(38, F + 1, 47, PZ.mirror(False))   # certo: leste -> sul
    s.set(38, F + 1, 50, PZ.mirror(True))    # certo: sul -> oeste
    s.set(33, F + 1, 50, PZ.mirror(False))   # certo: oeste -> norte
    s.set(33, F + 1, 48, PZ.lamp())
    for (x, z) in ((38, 47), (38, 50), (33, 50), (33, 48)):
        s.set(x, F, z, "minecraft:chiseled_polished_blackstone")
    PZ.cage(s, 41, F + 1, 49, f"{NS}:chests/nether_citadel_treasury", "west")
    s.sign(34, F + 3, 46 - 1, st("crimson_wall_sign", facing="north", waterlogged="false"),
           [f"sign.{NS}.nether_citadel.seal_1", f"sign.{NS}.nether_citadel.seal_2", f"sign.{NS}.nether_citadel.seal_3"])

    # ---------------- v1.0: contrafortes na muralha (a fortaleza era uma caixa lisa por fora)
    top = F + 10
    for z in range(34, D - 8, 6):
        for (x, face) in ((0, "west"), (W - 1, "east")):
            s.fill(x, top + 1, z, x, top + 2, z, "minecraft:red_nether_bricks")
            s.set(x, top + 3, z, st("nether_brick_wall", up="true", north="none", south="none", east="none", west="none",
                                    waterlogged="false"))

    big = with_apron(s)
    n = big.save(os.path.join(DATA, "structure/nether_citadel/keep.nbt"), keep_air=True)
    print("  estrutura nether_citadel/keep %dx%dx%d, %d blocos" % (BW, BH, BD, n))
    road()


def with_apron(s):
    """Copia a fortaleza para um molde maior com uma esplanada aberta em volta (ar explicito = espaco garantido)."""
    big = B("apron", BW, BH, BD)
    r = big.rnd
    ground = [("minecraft:blackstone", 5), ("minecraft:basalt[axis=y]", 3), ("minecraft:netherrack", 3),
              ("minecraft:soul_soil", 1), ("minecraft:gravel", 1), ("minecraft:magma_block", 0.3)]
    cxm, czm = (BW - 1) / 2.0, (BD - 1) / 2.0

    def inside(x, z):
        # contorno arredondado (superelipse): os cantos do molde ficam por conta do terreno
        return abs((x - cxm) / (BW / 2.0)) ** 4 + abs((z - czm) / (BD / 2.0)) ** 4 <= 1.0

    for x in range(BW):
        for z in range(BD):
            if not inside(x, z):
                continue
            for y in range(0, F):
                big.set(x, y, z, big.pick(FOUND))
            big.set(x, F, z, big.pick(ground))
            for y in range(F + 1, BH):
                big.set(x, y, z, AIR)
    # estrada do portao ate a borda sul
    gx = A + 23
    z_gate = AN + D
    for z in range(z_gate, BD):
        for x in range(gx - 3, gx + 4):
            edge = abs(x - gx) == 3
            big.set(x, F, z, "minecraft:red_nether_bricks" if edge else
                    ("minecraft:polished_blackstone" if abs(x - gx) == 2 else "minecraft:polished_blackstone_bricks"))
    for z in range(z_gate + 2, BD - 1, 5):
        for x in (gx - 4, gx + 4):
            big.set(x, F + 1, z, "minecraft:polished_blackstone")
            big.set(x, F + 2, z, st("soul_campfire", lit="true", facing="north", signal_fire="false", waterlogged="false"))
    # espinhos de basalto espalhados pela esplanada (alguns com fogo das almas no topo)
    spots = []
    for _ in range(400):
        x, z = r.randrange(1, BW - 1), r.randrange(1, BD - 1)
        in_keep = A - 3 <= x <= A + W + 2 and AN - 3 <= z <= AN + D + 2
        on_road = abs(x - gx) <= 6 and z >= z_gate - 1
        if in_keep or on_road or not inside(x, z) or any(abs(x - a) + abs(z - b) < 5 for a, b in spots):
            continue
        spots.append((x, z))
        if len(spots) >= 34:
            break
    for i, (x, z) in enumerate(spots):
        h = r.randint(2, 9)
        for y in range(F + 1, F + 1 + h):
            big.set(x, y, z, "minecraft:polished_basalt[axis=y]" if y > F + 1 else "minecraft:basalt[axis=y]")
        if h >= 5:
            for (dx, dz) in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                if r.random() < 0.6:
                    for y in range(F + 1, F + 1 + r.randint(1, h - 3)):
                        big.set(x + dx, y, z + dz, "minecraft:basalt[axis=y]")
        if i % 4 == 0 and h <= 5:
            big.set(x, F + h, z, "minecraft:soul_soil")
            big.set(x, F + h + 1, z, "minecraft:soul_fire")
    # a fortaleza por cima de tudo
    for (x, y, z), val in s.blocks.items():
        big.blocks[(x + A, y, z + AN)] = val
    for e in s.entities:
        px, py, pz = e["pos"]
        big.entities.append({"pos": [Double(px + A), Double(py), Double(pz + AN)],
                             "blockPos": [Int(int(px + A)), Int(int(py)), Int(int(pz + AN))], "nbt": e["nbt"]})
    # contrafortes por fora das muralhas (leste, oeste e norte)
    def buttress(x, z, dx, dz, facing):
        stairs = st("polished_blackstone_brick_stairs", facing=facing, half="bottom", shape="straight", waterlogged="false")
        big.fill(x, F + 1, z, x, F + 8, z, "minecraft:polished_blackstone_bricks")
        big.set(x, F + 9, z, stairs)
        big.fill(x + dx, F + 1, z + dz, x + dx, F + 4, z + dz, "minecraft:polished_blackstone_bricks")
        big.set(x + dx, F + 5, z + dz, stairs)
        big.set(x + dx, F, z + dz, "minecraft:polished_blackstone")

    for z in range(AN + 10, AN + D - 8, 6):
        buttress(A - 1, z, -1, 0, "east")
        buttress(A + W, z, 1, 0, "west")
    for x in range(A + 10, A + W - 8, 6):
        buttress(x, AN - 1, 0, -1, "south")
    # conector da estrada (vira calcamento) na borda sul, no eixo do portao
    big.jigsaw(gx, F, BD - 1, "south_up", f"{NS}:citadel_road_out", f"{NS}:citadel_road_in", ROAD_POOL,
               "minecraft:polished_blackstone_bricks", joint="aligned")
    return big


def road():
    """Trecho de estrada (7 x 16) que continua para o sul a partir do portao: tunel onde ha rocha, ponte onde ha vazio."""
    s = B("road", ROAD_W, ROAD_H, ROAD_L)
    s.fillp(0, 0, 0, ROAD_W - 1, F - 1, ROAD_L - 1, FOUND)
    for z in range(ROAD_L):
        for x in range(ROAD_W):
            edge = x in (0, ROAD_W - 1)
            s.set(x, F, z, "minecraft:red_nether_bricks" if edge else
                  ("minecraft:polished_blackstone" if x in (1, ROAD_W - 2) else
                   s.pick([("minecraft:polished_blackstone_bricks", 6), ("minecraft:cracked_polished_blackstone_bricks", 2)])))
    s.fill(0, F + 1, 0, ROAD_W - 1, ROAD_H - 1, ROAD_L - 1, AIR)
    # um arco no meio do trecho, com lampiao das almas
    az = ROAD_L // 2
    for x in (0, ROAD_W - 1):
        s.fill(x, F + 1, az, x, F + 5, az, "minecraft:polished_basalt[axis=y]")
        s.set(x, F + 1, az, "minecraft:chiseled_polished_blackstone")
    s.fill(0, F + 6, az, ROAD_W - 1, F + 6, az, "minecraft:red_nether_bricks")
    s.set(ROAD_W // 2, F + 6, az, "minecraft:gilded_blackstone")
    s.set(ROAD_W // 2, F + 5, az, st("soul_lantern", hanging="true", waterlogged="false"))
    for z in (2, ROAD_L - 3):
        for x in (0, ROAD_W - 1):
            s.set(x, F + 1, z, st("nether_brick_fence", north="false", south="false", east="false", west="false", waterlogged="false"))
    c = ROAD_W // 2
    s.jigsaw(c, F, 0, "north_up", f"{NS}:citadel_road_in", "minecraft:empty", "minecraft:empty",
             "minecraft:polished_blackstone_bricks", joint="aligned")
    s.jigsaw(c, F, ROAD_L - 1, "south_up", f"{NS}:citadel_road_out", f"{NS}:citadel_road_in", ROAD_POOL,
             "minecraft:polished_blackstone_bricks", joint="aligned")
    n = s.save(os.path.join(DATA, "structure/nether_citadel/road.nbt"), keep_air=True)
    print("  estrutura nether_citadel/road %d blocos" % n)


def worldgen():
    write_json(os.path.join(DATA, "worldgen/template_pool/nether_citadel/start.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                "location": f"{NS}:nether_citadel/keep",
                                                "processors": "minecraft:empty", "projection": "rigid"}}]})
    write_json(os.path.join(DATA, "worldgen/template_pool/nether_citadel/road.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                "location": f"{NS}:nether_citadel/road",
                                                "processors": "minecraft:empty", "projection": "rigid"}}]})
    spawns = [{"type": f"{NS}:demon_knight", "weight": 10, "minCount": 1, "maxCount": 2},
              {"type": f"{NS}:demon_succubus", "weight": 6, "minCount": 1, "maxCount": 1},
              {"type": "minecraft:wither_skeleton", "weight": 5, "minCount": 1, "maxCount": 2},
              {"type": "minecraft:blaze", "weight": 3, "minCount": 1, "maxCount": 1}]
    write_json(os.path.join(DATA, "worldgen/structure/nether_citadel.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/nether_citadel",
        "step": "surface_structures",
        # beard_box: abre espaco em volta e cria base solida embaixo (nada de cidadela flutuando sobre lava)
        "terrain_adaptation": "beard_box",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": spawns}},
        "start_pool": f"{NS}:nether_citadel/start",
        # v1.0: ate 4 trechos de estrada (64 blocos) saindo do portao
        "size": 4,
        "start_height": {"absolute": 33},
        "max_distance_from_center": 116,
        "use_expansion_hack": False})
    write_json(os.path.join(DATA, "worldgen/structure_set/nether_citadel.json"), {
        "structures": [{"structure": f"{NS}:nether_citadel", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 36, "separation": 14, "salt": 1946301663}})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/nether_citadel.json"),
               {"values": ["minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:soul_sand_valley",
                           "minecraft:warped_forest"]})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "nether_citadel.json"), {"values": [f"{NS}:nether_citadel"]})
    write_json(os.path.join(tags, "boss_lair.json"), {"values": [f"{NS}:nether_citadel"]})
    write_json(os.path.join(tags, "lair/nether_citadel.json"), {"values": [f"{NS}:nether_citadel"]})
    # Adamantita: como detritos antigos — escondida (some se encostar no ar), y 8–32, metade dos chunks.
    write_json(os.path.join(DATA, "worldgen/configured_feature/adamantite_ore.json"), {
        "type": "minecraft:scattered_ore",
        "config": {"size": 2, "discard_chance_on_air_exposure": 1.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:base_stone_nether"},
             "state": {"Name": f"{NS}:adamantite_ore"}}]}})
    write_json(os.path.join(DATA, "worldgen/placed_feature/adamantite_ore.json"), {
        "feature": f"{NS}:adamantite_ore",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                                    "min_inclusive": {"absolute": 8},
                                                                    "max_inclusive": {"absolute": 32}}},
                      {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, "neoforge/biome_modifier/nether_citadel_adamantite_ore.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_nether",
        "features": f"{NS}:adamantite_ore", "step": "underground_decoration"})


# =====================================================================================
#  LOOT, RECEITAS, TAGS
# =====================================================================================
def ie(name, lo=1, hi=1, weight=1, funcs=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    f = list(funcs or [])
    if hi > 1 or lo != 1:
        f.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if f:
        e["functions"] = f
    return e


def scroll(glyph, weight=1):
    return {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": weight,
            "functions": [{"function": "minecraft:set_components", "components": {f"{NS}:glyph": f"{NS}:{glyph}"}}]}


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def pool(entries, lo=1, hi=1):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi} if hi != lo else lo, "bonus_rolls": 0, "entries": entries}


def loot_tables():
    lt = os.path.join(DATA, "loot_table")
    survives = [{"condition": "minecraft:survives_explosion"}]
    for b in ("adamantite_block", "demon_pact_altar", "demon_infernal_forge"):
        write_json(os.path.join(lt, f"blocks/{b}.json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}], "conditions": survives}],
            "random_sequence": f"{NS}:blocks/{b}"})
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write_json(os.path.join(lt, "blocks/adamantite_ore.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:adamantite_ore", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{NS}:raw_adamantite", "functions": [{"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{NS}:blocks/adamantite_ore"})
    killed = [{"condition": "minecraft:killed_by_player"}]
    write_json(os.path.join(lt, "entities/demon_knight.json"), {"type": "minecraft:entity", "pools": [
        pool([ie("gold_nugget", 2, 6, 3), ie("quartz", 1, 3, 2), ie("blackstone", 1, 3, 1)]),
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:raw_adamantite")],
         "conditions": killed + [{"condition": "minecraft:random_chance", "chance": 0.06}]},
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:citadel_sun_katana")],
         "conditions": killed + [{"condition": "minecraft:random_chance", "chance": 0.015}]}],
        "random_sequence": f"{NS}:entities/demon_knight"})
    write_json(os.path.join(lt, "entities/demon_succubus.json"), {"type": "minecraft:entity", "pools": [
        pool([ie("blaze_powder", 1, 2, 3), ie("glowstone_dust", 1, 3, 2)]),
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie("ghast_tear")],
         "conditions": killed + [{"condition": "minecraft:random_chance", "chance": 0.3}]},
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:essence_shadow")],
         "conditions": killed + [{"condition": "minecraft:random_chance", "chance": 0.25}]}],
        "random_sequence": f"{NS}:entities/demon_succubus"})
    write_json(os.path.join(lt, "entities/demon_lord.json"), {"type": "minecraft:entity", "pools": []})
    ch = os.path.join(lt, "chests")
    glyphs = ["ignite", "ember", "combustion", "fire_wall", "overheat", "blue_flame", "curse", "chains", "life_drain",
              "devour", "nightmare", "fear", "steal_blessing", "pain_link"]
    write_json(os.path.join(ch, "nether_citadel_treasury.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("gold_ingot", 4, 10, 6), ie("gold_block", 1, 2, 2), ie("diamond", 1, 3, 3), ie("emerald", 2, 6, 3),
              ie("netherite_scrap", 1, 2, 2), ie(f"{NS}:raw_adamantite", 1, 3, 3), ie(f"{NS}:adamantite_ingot", 1, 2, 2),
              ie("golden_apple", 1, 2, 3), ie("ghast_tear", 1, 3, 2), ie(f"{NS}:mana_crystal", 2, 6, 3)], 4, 7),
        pool([ie(f"{NS}:essence_fire", 1, 3, 3), ie(f"{NS}:essence_shadow", 1, 3, 3), ie("book", 1, 1, 2, [ench(25, 39)]),
              ie("enchanted_golden_apple", 1, 1, 1), ie("netherite_upgrade_smithing_template", 1, 1, 1)], 1, 3),
        pool([scroll(g) for g in glyphs], 1, 2),
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:citadel_sun_katana", 1, 1, 1), {"type": "minecraft:empty", "weight": 6}]},
    ], "random_sequence": f"{NS}:chests/nether_citadel_treasury"})
    write_json(os.path.join(ch, "nether_citadel_armory.json"), {"type": "minecraft:chest", "pools": [
        pool([ie("iron_ingot", 3, 8, 5), ie("gold_ingot", 2, 5, 4), ie("blaze_rod", 1, 4, 4), ie("arrow", 8, 20, 3),
              ie("magma_cream", 1, 4, 3), ie("quartz", 4, 12, 3), ie("coal", 4, 10, 3)], 3, 6),
        pool([ie("iron_sword", 1, 1, 3, [ench(15, 30)]), ie("golden_sword", 1, 1, 2, [ench(20, 30)]),
              ie("iron_chestplate", 1, 1, 2, [ench(15, 30)]), ie("crossbow", 1, 1, 2, [ench(15, 25)]),
              ie("shield", 1, 1, 2)], 1, 2),
        pool([ie(f"{NS}:raw_adamantite", 1, 2, 2), ie(f"{NS}:essence_fire", 1, 2, 2), {"type": "minecraft:empty", "weight": 5}]),
    ], "random_sequence": f"{NS}:chests/nether_citadel_armory"})


def recipes():
    rc = os.path.join(DATA, "recipe")
    ingot, raw = f"{NS}:adamantite_ingot", f"{NS}:raw_adamantite"
    for kind, t in (("smelting", 200), ("blasting", 100)):
        write_json(os.path.join(rc, f"adamantite_ingot_from_{kind}.json"), {
            "type": f"minecraft:{kind}", "category": "misc", "ingredient": {"item": raw},
            "result": {"id": ingot}, "experience": 2.0, "cookingtime": t})
    write_json(os.path.join(rc, "adamantite_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["III", "III", "III"],
        "key": {"I": {"item": ingot}}, "result": {"id": f"{NS}:adamantite_block", "count": 1}})
    write_json(os.path.join(rc, "adamantite_ingot_from_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": f"{NS}:adamantite_block"}],
        "result": {"id": ingot, "count": 9}})

    def shaped(name, pattern, key, cat="equipment"):
        write_json(os.path.join(rc, f"{name}.json"), {"type": "minecraft:crafting_shaped", "category": cat,
                                                      "pattern": pattern, "key": key, "result": {"id": f"{NS}:{name}", "count": 1}})

    I, R = {"item": ingot}, {"item": "minecraft:blaze_rod"}
    shaped("adamantite_sword", ["I", "I", "R"], {"I": I, "R": R})
    shaped("adamantite_pickaxe", ["III", " R ", " R "], {"I": I, "R": R})
    shaped("adamantite_axe", ["II", "IR", " R"], {"I": I, "R": R})
    shaped("adamantite_helmet", ["III", "I I"], {"I": I})
    shaped("adamantite_chestplate", ["I I", "III", "III"], {"I": I})
    shaped("adamantite_leggings", ["III", "I I", "I I"], {"I": I})
    shaped("adamantite_boots", ["I I", "I I"], {"I": I})
    shaped("demon_pact_altar", ["GEG", "OTO", "BBB"],
           {"G": {"item": "minecraft:gold_ingot"}, "E": {"item": f"{NS}:essence_shadow"}, "O": {"item": "minecraft:crying_obsidian"},
            "T": {"item": "minecraft:ghast_tear"}, "B": {"item": "minecraft:polished_blackstone_bricks"}}, "misc")
    shaped("demon_infernal_forge", ["NMN", "RFR", "NNN"],
           {"N": {"item": "minecraft:nether_bricks"}, "M": {"item": "minecraft:magma_block"}, "R": R,
            "F": {"item": "minecraft:blast_furnace"}}, "misc")


def tags():
    blocks = [f"{NS}:{b}" for b in ("adamantite_ore", "adamantite_block", "demon_pact_altar", "demon_infernal_forge")]
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": blocks})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/needs_diamond_tool.json"),
               {"values": [f"{NS}:adamantite_ore", f"{NS}:adamantite_block"]})
    it = os.path.join(MERGE, "data/minecraft/tags/item")
    write_json(os.path.join(it, "swords.json"), {"values": [f"{NS}:adamantite_sword", f"{NS}:demon_antimagic_blade", f"{NS}:citadel_sun_katana"]})
    write_json(os.path.join(it, "pickaxes.json"), {"values": [f"{NS}:adamantite_pickaxe"]})
    write_json(os.path.join(it, "axes.json"), {"values": [f"{NS}:adamantite_axe"]})
    for tag, piece in (("head_armor", "helmet"), ("chest_armor", "chestplate"), ("leg_armor", "leggings"), ("foot_armor", "boots")):
        write_json(os.path.join(it, f"{tag}.json"), {"values": [f"{NS}:adamantite_{piece}"]})
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": [f"{NS}:demon_lord"]})
    opt = lambda i: {"id": i, "required": False}  # noqa: E731
    write_json(os.path.join(DATA, "tags/entity_type/citadel_demons.json"), {"values": [
        f"{NS}:demon_lord", f"{NS}:demon_knight", f"{NS}:demon_succubus",
        opt(f"{NS}:ember_imp"), opt(f"{NS}:summoned_imp"), opt(f"{NS}:summoned_demon"), opt(f"{NS}:patron_ember_lord"),
        opt(f"{NS}:abyss_sovereign"), "minecraft:blaze", "minecraft:ghast", "minecraft:wither_skeleton", "minecraft:wither"]})


def journal():
    loc = {"structure": f"{NS}:nether_citadel", "dimension": "minecraft:the_nether"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 63) para perto do fim (Ato V, antes do Crepúsculo); XP na escala dos vizinhos.
    write_json(os.path.join(DATA, "journal/nether_citadel.json"), {"chapters": [{
        "id": "nether_citadel", "order": 45, "color": "#D4242C", "intro": True,
        "icon": [f"{NS}:demon_lord_sigil", f"{NS}:adamantite_ingot", f"{NS}:demon_pact_altar"],
        "steps": [
            {"id": "enter", "condition": {"type": "flag", "flag": "nether_citadel.entered"}, "xp": 250,
             "icon": "minecraft:nether_bricks", "locate": loc},
            {"id": "adamantite", "condition": {"type": "flag", "flag": "nether_citadel.adamantite"}, "optional": True,
             "xp": 200, "icon": f"{NS}:raw_adamantite"},
            {"id": "forge", "condition": {"type": "flag", "flag": "nether_citadel.forge"}, "optional": True, "xp": 200,
             "icon": f"{NS}:demon_infernal_forge", "locate": loc},
            {"id": "pact", "condition": {"type": "flag", "flag": "nether_citadel.pact"}, "optional": True, "xp": 200,
             "icon": f"{NS}:demon_pact_altar", "locate": loc},
            {"id": "demon_lord", "condition": {"type": "bosses", "ids": ["demon_lord"], "min": 1}, "xp": 700,
             "icon": f"{NS}:demon_lord_sigil", "locate": loc,
             "rewards": {"items": [{"id": "minecraft:golden_apple", "count": 3}]}, "requires": [f"{NS}:demon_lord"]}],
        "rewards": {"xp": 2700, "items": [{"id": f"{NS}:mana_crystal", "count": 16}], "glyph_scrolls": 1, "glyph_tier": 4}}]})


# =====================================================================================
#  TRADUCOES
# =====================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def lang():
    t(f"sign.{NS}.nether_citadel.seal_1", "Câmara do Selo", "Seal Chamber")
    t(f"sign.{NS}.nether_citadel.seal_2", "A luz abre", "Light opens")
    t(f"sign.{NS}.nether_citadel.seal_3", "o que o fogo guarda", "what fire guards")
    for eid, pt, en in (("demon_lord", "Lorde Demônio", "Demon Lord"), ("demon_knight", "Cavaleiro Infernal", "Infernal Knight"),
                        ("demon_succubus", "Súcubo Feiticeira", "Succubus Sorceress")):
        t(f"entity.{NS}.{eid}", pt, en)
        t(f"item.{NS}.{eid}_spawn_egg", f"Ovo Gerador de {pt}", f"{en} Spawn Egg")
    t(f"bestiary.{NS}.demon_lord.desc", "Senhor da Cidadela Demoníaca. Começa como espadachim; termina como uma fera alada de fogo.",
      "Master of the Demon Citadel. He starts as a swordsman and ends as a winged beast of fire.")
    t(f"bestiary.{NS}.demon_knight.desc", "Guarda de armadura negra. O escudo segura flechas vindas da frente — dê a volta.",
      "Black-armored guard. Its shield stops arrows from the front — flank it.")
    t(f"bestiary.{NS}.demon_succubus.desc", "Feiticeira que encanta e puxa as vítimas. Frágil de perto.",
      "A sorceress who charms and pulls her victims. Fragile up close.")
    t(f"boss_entrance.{NS}.demon_lord", "Espada, Chamas e Legião", "Sword, Flame and Legion")
    t(f"boss.{NS}.demon_lord.subtitle", "Senhor da Cidadela Demoníaca", "Master of the Demon Citadel")
    t(f"boss.{NS}.demon_lord.phase1", "Chamas do Inferno", "Hellfire")
    t(f"boss.{NS}.demon_lord.phase2", "A Legião", "The Legion")
    t(f"boss.{NS}.demon_lord.phase3", "Forma Demoníaca", "Demonic Form")
    t(f"announce.{NS}.demon_lord.intro", "Mortais no meu salão? Ajoelhem-se... ou queimem.", "Mortals in my hall? Kneel... or burn.")
    t(f"announce.{NS}.demon_lord.phase1", "Sintam o fogo que nunca se apaga!", "Feel the fire that never dies!")
    t(f"announce.{NS}.demon_lord.phase2", "Legião! Ao seu senhor!", "Legion! To your lord!")
    t(f"announce.{NS}.demon_lord.phase3", "CHEGA DE FINGIR SER HUMANO!", "NO MORE PRETENDING TO BE HUMAN!")
    t(f"announce.{NS}.demon_lord.hellfire", "Que o chão se abra!", "Let the ground open!")
    t(f"announce.{NS}.demon_lord.summon", "Venham, meus diabretes!", "Come, my imps!")
    t(f"announce.{NS}.demon_lord.meteor", "O céu do Nether cai sobre vocês!", "The Nether sky falls upon you!")
    t(f"announce.{NS}.demon_lord.death", "Esta cidadela... ainda... é minha...", "This citadel... is still... mine...")
    t(f"attack.{NS}.demon_lord.cleave", "Lorde Demônio — Corte Amplo", "Demon Lord — Wide Cleave")
    t(f"attack.{NS}.demon_lord.thrust", "Lorde Demônio — Estocada", "Demon Lord — Thrust")
    t(f"attack.{NS}.demon_lord.hellfire", "Lorde Demônio — Pilares do Inferno", "Demon Lord — Hellfire Pillars")
    t(f"attack.{NS}.demon_lord.ring", "Lorde Demônio — Anel de Chamas", "Demon Lord — Ring of Flames")
    t(f"attack.{NS}.demon_lord.meteor", "Lorde Demônio — Chuva de Meteoros", "Demon Lord — Meteor Rain")
    t(f"message.{NS}.demon_lord.imp_shield", "Com 2+ diabretes vivos, o Lorde leva só metade do dano — mate os diabretes!",
      "With 2+ imps alive, the Lord takes half damage — kill the imps!")
    t(f"place.{NS}.nether_citadel", "Cidadela Demoníaca", "Demon Citadel")
    t(f"message.{NS}.nether_citadel.entered", "Você entrou na Cidadela Demoníaca. O ar cheira a enxofre... e a ouro.",
      "You entered the Demon Citadel. The air smells of brimstone... and gold.")
    t(f"sign.{NS}.nether_citadel.warn_1", "AJOELHE-SE", "KNEEL")
    t(f"sign.{NS}.nether_citadel.warn_2", "diante do", "before the")
    t(f"sign.{NS}.nether_citadel.warn_3", "Lorde Demônio", "Demon Lord")
    t(f"sign.{NS}.nether_citadel.pact_1", "Todo poder", "All power")
    t(f"sign.{NS}.nether_citadel.pact_2", "tem um preço.", "has a price.")
    t(f"sign.{NS}.nether_citadel.pact_3", "Pague-o.", "Pay it.")
    # blocos e itens
    t(f"block.{NS}.adamantite_ore", "Minério de Adamantita", "Adamantite Ore")
    t(f"block.{NS}.adamantite_block", "Bloco de Adamantita", "Block of Adamantite")
    t(f"block.{NS}.demon_pact_altar", "Altar do Pacto", "Pact Altar")
    t(f"block.{NS}.demon_infernal_forge", "Forja Infernal", "Infernal Forge")
    t(f"item.{NS}.raw_adamantite", "Adamantita Bruta", "Raw Adamantite")
    t(f"item.{NS}.adamantite_ingot", "Lingote de Adamantita", "Adamantite Ingot")
    t(f"item.{NS}.adamantite_sword", "Espada de Adamantita", "Adamantite Sword")
    t(f"item.{NS}.adamantite_pickaxe", "Picareta de Adamantita", "Adamantite Pickaxe")
    t(f"item.{NS}.adamantite_axe", "Machado de Adamantita", "Adamantite Axe")
    t(f"item.{NS}.adamantite_helmet", "Elmo de Adamantita", "Adamantite Helmet")
    t(f"item.{NS}.adamantite_chestplate", "Peitoral de Adamantita", "Adamantite Chestplate")
    t(f"item.{NS}.adamantite_leggings", "Calças de Adamantita", "Adamantite Leggings")
    t(f"item.{NS}.adamantite_boots", "Botas de Adamantita", "Adamantite Boots")
    t(f"tooltip.{NS}.adamantite_set", "Conjunto completo: imune a fogo e lava.", "Full set: immune to fire and lava.")
    t(f"item.{NS}.demon_antimagic_blade", "Lâmina Antimagia", "Anti-Magic Blade")
    t(f"item.{NS}.demon_antimagic_blade.desc", "Um espadão negro que veio de um grimório de cinco folhas.",
      "A black greatsword that came from a five-leaf grimoire.")
    t(f"item.{NS}.demon_antimagic_blade.use", "Usar: Corte Antimagia — destrói magias e projéteis à frente e desfaz buffs (5 s).",
      "Use: Anti-Magic Slash — destroys spells and projectiles ahead and strips buffs (5 s).")
    t(f"item.{NS}.demon_antimagic_blade.passive", "Golpes arrancam buffs. Na mão: magia inimiga causa 30% menos dano.",
      "Hits strip buffs. In hand: enemy magic deals 30% less damage.")
    t(f"item.{NS}.demon_antimagic_blade.cut", "Antimagia: %s anulado(s)", "Anti-magic: %s nullified")
    t(f"item.{NS}.citadel_sun_katana", "Katana do Sol", "Sun Katana")
    t(f"item.{NS}.citadel_sun_katana.desc", "A lâmina fica laranja como o nascer do sol. Demônios a temem.",
      "Its blade glows orange like sunrise. Demons fear it.")
    t(f"item.{NS}.citadel_sun_katana.use", "Usar: Investida Flamejante — avança 7 blocos cortando e queimando (7 s).",
      "Use: Flaming Dash — rush 7 blocks, cutting and burning (7 s).")
    t(f"item.{NS}.citadel_sun_katana.passive", "+6 de dano em demônios e mortos-vivos.", "+6 damage to demons and undead.")
    t(f"item.{NS}.demon_lord_sigil", "Selo do Lorde Demônio", "Demon Lord's Sigil")
    t(f"item.{NS}.demon_lord_sigil.desc", "O nome dele, gravado em brasa. Os demônios ainda obedecem.",
      "His name, branded in ember. Demons still obey it.")
    t(f"item.{NS}.demon_lord_sigil.use", "Ofereça no Altar do Pacto: pacto SEM Dívida Infernal.",
      "Offer it at the Pact Altar: a pact with NO Infernal Debt.")
    t(f"tooltip.{NS}.citadel_infusion.fire", "Infusão Infernal: Fogo (+2 dano, queima)", "Infernal Infusion: Fire (+2 damage, burns)")
    t(f"tooltip.{NS}.citadel_infusion.shadow", "Infusão Infernal: Sombra (+2 dano, murcha)", "Infernal Infusion: Shadow (+2 damage, withers)")
    # efeitos
    t(f"effect.{NS}.demon_pact", "Pacto Demoníaco", "Demonic Pact")
    t(f"effect.{NS}.demon_pact.description", "Dano +35%, poder mágico +30%, mais rápido. Preço: −3 corações e fome.",
      "Damage +35%, spell power +30%, faster. Price: −3 hearts and hunger.")
    t(f"effect.{NS}.demon_debt", "Dívida Infernal", "Infernal Debt")
    t(f"effect.{NS}.demon_debt.description", "Mais fraco, mana lenta, fome. O altar recusa novos pactos. Leite não cura.",
      "Weaker, slow mana, hunger. The altar refuses new pacts. Milk does not cure it.")
    # altar e forja
    a = f"block.{NS}.demon_pact_altar"
    t(f"{a}.hint", "Altar do Pacto: ofereça uma Essência das Trevas ou Lágrima de Ghast. Ganha poder por 5 min; paga com sangue, vida máxima, fome — e depois a Dívida.",
      "Pact Altar: offer a Shadow Essence or Ghast Tear. Gain power for 5 min; pay with blood, max health, hunger — and then the Debt.")
    t(f"{a}.hint_warlock", "Os demônios reconhecem um Bruxo: seu pacto dura 8 min e a dívida só 1 min.",
      "Demons recognize a Warlock: your pact lasts 8 min and the debt only 1 min.")
    t(f"{a}.in_debt", "Você ainda deve ao altar. Espere a Dívida Infernal acabar.", "You still owe the altar. Wait for the Infernal Debt to end.")
    t(f"{a}.already", "Você já tem um pacto ativo.", "You already have an active pact.")
    t(f"{a}.sealed", "O pacto está selado. O poder queima nas suas veias.", "The pact is sealed. Power burns in your veins.")
    t(f"{a}.sealed_warlock", "O pacto está selado — os demônios lhe dão condições de Bruxo.", "The pact is sealed — demons give you Warlock terms.")
    t(f"{a}.sealed_free", "O Selo do Lorde fala por você: pacto sem dívida.", "The Lord's Sigil speaks for you: a pact without debt.")
    t(f"{a}.debt", "O pacto terminou. Agora o altar cobra: Dívida Infernal.", "The pact is over. Now the altar collects: Infernal Debt.")
    fo = f"block.{NS}.demon_infernal_forge"
    t(f"{fo}.hint", "Forja Infernal: arma na mão principal, catalisador na outra (Essência do Fogo/Vara de Blaze = Fogo; Essência das Trevas/Rosa de Wither = Sombra) e %s níveis de XP.",
      "Infernal Forge: weapon in main hand, catalyst in off hand (Fire Essence/Blaze Rod = Fire; Shadow Essence/Wither Rose = Shadow) and %s XP levels.")
    t(f"{fo}.need_catalyst", "Segure um catalisador na outra mão (Essência do Fogo, Vara de Blaze, Essência das Trevas ou Rosa de Wither).",
      "Hold a catalyst in your off hand (Fire Essence, Blaze Rod, Shadow Essence or Wither Rose).")
    t(f"{fo}.need_xp", "A forja pede %s níveis de XP.", "The forge demands %s XP levels.")
    t(f"{fo}.same", "Esta arma já tem essa infusão.", "This weapon already has that infusion.")
    t(f"{fo}.done.fire", "%s agora arde com o Fogo Infernal.", "%s now burns with Infernal Fire.")
    t(f"{fo}.done.shadow", "%s agora sangra Sombra.", "%s now bleeds Shadow.")
    # comandos
    c = f"command.{NS}.nether_citadel"
    t(f"{c}.given", "Itens da Cidadela Demoníaca entregues.", "Demon Citadel items given.")
    t(f"{c}.not_found", "Nenhuma Cidadela Demoníaca encontrada perto (o mundo foi criado antes da v1.6? Só chunks novos do Nether têm).",
      "No Demon Citadel found nearby (world made before v1.6? Only new Nether chunks have them).")
    t(f"{c}.tp", "Teleportado para a Cidadela Demoníaca em %s %s %s.", "Teleported to the Demon Citadel at %s %s %s.")
    t(f"{c}.spawn_done", "%s invocado.", "%s summoned.")
    t(f"{c}.spawn_fail", "Não foi possível invocar o chefe aqui.", "Could not summon the boss here.")
    t(f"{c}.debt_cleared", "Dívida Infernal perdoada.", "Infernal Debt forgiven.")
    # diario
    j = f"journal.{NS}.nether_citadel"
    t(f"{j}.title", "A Cidadela Demoníaca", "The Demon Citadel")
    t(f"{j}.intro", "No Nether, entre rios de lava, ergue-se uma fortaleza de tijolos negros. Dizem que um Lorde Demônio governa de lá — e que guarda Adamantita.",
      "In the Nether, between rivers of lava, stands a fortress of black brick. They say a Demon Lord rules from there — and hoards Adamantite.")
    t(f"{j}.lore", "Muralhas, um pátio com a Forja Infernal e o Altar do Pacto, e um salão do trono onde o Lorde dorme sentado, esperando desafiantes.",
      "Walls, a courtyard with the Infernal Forge and the Pact Altar, and a throne hall where the Lord sleeps seated, waiting for challengers.")
    t(f"{j}.outro", "O trono está vazio. Pela primeira vez em séculos, a cidadela fica em silêncio.",
      "The throne is empty. For the first time in centuries, the citadel falls silent.")
    for sid, (pt, en, hpt, hen, lpt, len_, tpt, ten) in {
        "enter": ("Entre na Cidadela Demoníaca", "Enter the Demon Citadel",
                  "Vá ao Nether e siga a bússola do diário. A cidadela fica perto da altura do mar de lava, muitas vezes encravada na rocha.",
                  "Go to the Nether and follow the journal compass. The citadel sits near lava-sea height, often carved into the rock.",
                  "O portão do sul tem placas que mandam ajoelhar.", "The south gate has signs ordering you to kneel.",
                  "Nether", "Nether"),
        "adamantite": ("Consiga Adamantita", "Obtain Adamantite",
                       "Minério raro e escondido no Nether (y 8–32, nunca encostado no ar), ou nos baús da cidadela. Funda para ter lingotes.",
                       "Rare hidden ore in the Nether (y 8–32, never touching air), or in citadel chests. Smelt it into ingots.",
                       "Mais dura que netherite. Não é obrigatória — mas vale cada lingote.", "Tougher than netherite. Not required — but worth every ingot.",
                       "Opcional", "Optional"),
        "forge": ("Use a Forja Infernal", "Use the Infernal Forge",
                  "No pátio (oeste). Arma na mão, Essência do Fogo/Trevas na outra e 5 níveis de XP.",
                  "In the courtyard (west). Weapon in hand, Fire/Shadow Essence in the other, and 5 XP levels.",
                  "Fogo queima; Sombra murcha.", "Fire burns; Shadow withers.", "Pátio, oeste", "Courtyard, west"),
        "pact": ("Faça um pacto no Altar", "Make a pact at the Altar",
                 "No santuário do pátio (leste). Ofereça Essência das Trevas ou Lágrima de Ghast. Bruxos têm condições melhores.",
                 "In the courtyard shrine (east). Offer a Shadow Essence or Ghast Tear. Warlocks get better terms.",
                 "Todo poder tem um preço.", "All power has a price.", "Pátio, leste", "Courtyard, east"),
        "demon_lord": ("Derrote o Lorde Demônio", "Defeat the Demon Lord",
                       "Salão do trono (norte). 4 fases: espada, chamas, legião de diabretes (mate-os: eles o protegem) e a Forma Demoníaca.",
                       "Throne hall (north). 4 phases: sword, flames, imp legion (kill them: they shield him) and the Demonic Form.",
                       "Ele já foi humano. Já faz muito tempo.", "He was human once. A long time ago.", "Salão do trono", "Throne hall"),
    }.items():
        t(f"{j}.{sid}", pt, en)
        t(f"{j}.{sid}.hint", hpt, hen)
        t(f"{j}.{sid}.lore", lpt, len_)
        t(f"{j}.{sid}.tip", tpt, ten)

    os.makedirs(LANG, exist_ok=True)
    for idx, code in ((0, "pt_br"), (1, "en_us")):
        write_json(os.path.join(LANG, f"{code}.json"), {k: v[idx] for k, v in sorted(L.items())})


def main():
    print("nether_citadel: texturas")
    tex_raw(); tex_ingot()
    tex_sword("adamantite_sword", ADA, ADA_L, H("#D9A632"))
    tex_pickaxe(); tex_axe(); tex_armor_icons()
    tex_antimagic_blade(); tex_sun_katana(); tex_sigil()
    tex_blocks(); tex_armor_layers(); tex_effects()
    print("nether_citadel: modelos")
    block_models(); item_models()
    nether_citadel_geo.build_all(ASSETS)
    print("nether_citadel: estrutura e dados")
    citadel(); worldgen(); loot_tables(); recipes(); tags(); journal(); lang()
    print("nether_citadel: pronto (%d traducoes)" % len(L))


if __name__ == "__main__":
    main()
