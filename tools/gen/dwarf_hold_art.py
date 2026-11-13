"""Arte do módulo "dwarf_hold" (v1.6): texturas, modelos de item/bloco e os anões (GeckoLib).

Uso (na raiz do projeto):  py -I tools/gen/dwarf_hold_art.py

- Mithril: minério, bloco, bloco bruto, lingote, pepita, bruto, ferramentas, armadura (ícones e camadas do corpo) —
  feitos RECOLORINDO as texturas do vanilla (lidas do jar do cliente no cache do Gradle) para a paleta prata-azulada
  do Mithril, então combinam com o estilo do jogo. Sem o jar, o script para com erro.
- Machado de Guerra, Monóculo de Avaliação, ícone do Escudo do Herói: pixel art própria.
- Escudo do Herói 3D (mesmo espaço/transformações do escudo vanilla; construtor de modelo do tools/gen/armory.py).
- Forja Rúnica: modelo de bloco em peças (pedestal de ardósia + bigorna com runas que brilham).
- Anões (Comerciante e Ferreiro): .geo.json, textura e animações (construtor do tools/gen/creatures_b.py).
"""
import glob
import json
import math
import os
import random
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402
from world_v09 import read_png  # noqa: E402
import armory as AR  # noqa: E402
import creatures_b as CB  # noqa: E402

NS = "isekai_mahou"
A = os.path.join(ROOT, "src/main/resources/assets", NS)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =================================================================================================
#  Leitura das texturas do vanilla
# =================================================================================================
_JAR = None


def jar():
    global _JAR
    if _JAR is None:
        hits = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar")))
        if not hits:
            sys.exit("ERRO: jar do cliente do Minecraft não encontrado no cache do Gradle")
        _JAR = zipfile.ZipFile(hits[-1])
    return _JAR


def vanilla(path):
    """Canvas com a textura assets/minecraft/textures/<path>.png."""
    w, h, px = read_png(jar().read(f"assets/minecraft/textures/{path}.png"))
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            c.set(x, y, tuple(px[y][x]))
    return c


# Paleta do Mithril: prata com reflexo azul-gelo (escuro -> claro)
MITHRIL = [hex_rgba(h) for h in ("1c2638", "34465f", "566f8f", "7f9cbd", "a9c4e0", "d2e4f6", "f4faff")]
RAW = [hex_rgba(h) for h in ("2a2e38", "454c5c", "667086", "8a96ac", "aebccf", "d0dbe8", "eef3f8")]


def lum(p):
    return (0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) / 255.0


def ramp(pal, t):
    t = max(0.0, min(1.0, t))
    f = t * (len(pal) - 1)
    i = int(f)
    if i >= len(pal) - 1:
        return pal[-1]
    return mix(pal[i], pal[i + 1], f - i)


def recolor(c, pal=MITHRIL, select=None, lo=None, hi=None):
    """Recolore os pixels (todos, ou os que `select(p)` aceita) pela luminância, esticada para lo..hi."""
    pts = [(x, y) for y in range(c.h) for x in range(c.w) if c.get(x, y)[3] > 0 and (select is None or select(c.get(x, y)))]
    if not pts:
        return c
    ls = [lum(c.get(x, y)) for x, y in pts]
    lo = min(ls) if lo is None else lo
    hi = max(ls) if hi is None else hi
    for (x, y), l in zip(pts, ls):
        a = c.get(x, y)[3]
        r = ramp(pal, (l - lo) / max(1e-6, hi - lo))
        c.set(x, y, (r[0], r[1], r[2], a))
    return c


def is_diamond(p):
    """Pixels "de diamante" (ciano) nas texturas do vanilla."""
    return p[3] > 0 and p[2] > p[0] + 18 and p[1] > p[0] + 10


# =================================================================================================
#  Mithril
# =================================================================================================
def gen_mithril():
    tex_i = os.path.join(A, "textures/item")
    tex_b = os.path.join(A, "textures/block")
    recolor(vanilla("item/iron_ingot")).save(os.path.join(tex_i, "mithril_ingot.png"))
    recolor(vanilla("item/iron_nugget")).save(os.path.join(tex_i, "mithril_nugget.png"))
    recolor(vanilla("item/raw_iron"), RAW).save(os.path.join(tex_i, "raw_mithril.png"))
    recolor(vanilla("block/raw_iron_block"), RAW).save(os.path.join(tex_b, "raw_mithril_block.png"))
    blk = recolor(vanilla("block/iron_block"))
    # runa anã gravada no centro do bloco (brilho azul suave)
    for (x, y) in ((7, 4), (8, 4), (7, 5), (6, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 9), (7, 10), (8, 11)):
        blk.set(x, y, hex_rgba("bfe8ff"))
    blk.save(os.path.join(tex_b, "mithril_block.png"))
    # minério: ardósia + manchas do minério de diamante recoloridas
    ore = vanilla("block/deepslate_diamond_ore")
    recolor(ore, MITHRIL, select=is_diamond)
    ore.save(os.path.join(tex_b, "deepslate_mithril_ore.png"))
    for t in ("sword", "pickaxe", "axe", "shovel", "hoe"):
        recolor(vanilla(f"item/diamond_{t}"), select=is_diamond).save(os.path.join(tex_i, f"mithril_{t}.png"))
    for t in ("helmet", "chestplate", "leggings", "boots"):
        recolor(vanilla(f"item/diamond_{t}"), select=is_diamond).save(os.path.join(tex_i, f"mithril_{t}.png"))
    arm = os.path.join(A, "textures/models/armor")
    for layer in (1, 2):
        recolor(vanilla(f"models/armor/diamond_layer_{layer}"), select=is_diamond).save(
            os.path.join(arm, f"mithril_layer_{layer}.png"))
    war_axe().save(os.path.join(tex_i, "mithril_war_axe.png"))

    for b in ("deepslate_mithril_ore", "mithril_block", "raw_mithril_block"):
        write_json(os.path.join(A, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write_json(os.path.join(A, f"models/block/{b}.json"), {"parent": "minecraft:block/cube_all",
                                                               "textures": {"all": f"{NS}:block/{b}"}})
        write_json(os.path.join(A, f"models/item/{b}.json"), {"parent": f"{NS}:block/{b}"})
    for i in ("mithril_ingot", "mithril_nugget", "raw_mithril", "mithril_helmet", "mithril_chestplate", "mithril_leggings",
              "mithril_boots"):
        write_json(os.path.join(A, f"models/item/{i}.json"), {"parent": "minecraft:item/generated",
                                                              "textures": {"layer0": f"{NS}:item/{i}"}})
    for i in ("mithril_sword", "mithril_pickaxe", "mithril_axe", "mithril_shovel", "mithril_hoe", "mithril_war_axe"):
        write_json(os.path.join(A, f"models/item/{i}.json"), {"parent": "minecraft:item/handheld",
                                                              "textures": {"layer0": f"{NS}:item/{i}"}})
    for egg in ("dwarf_merchant_spawn_egg", "dwarf_smith_spawn_egg"):
        write_json(os.path.join(A, f"models/item/{egg}.json"), {"parent": "minecraft:item/template_spawn_egg"})


def war_axe():
    """Machado de Guerra Anão: lâmina dupla larga de Mithril, cabo com tiras de couro e runa azul."""
    c = Canvas(16, 16)
    wood, wood_d = hex_rgba("6b4a2b"), hex_rgba("43301c")
    leather = hex_rgba("8a5a3a")
    # cabo na diagonal (de baixo-esquerda para cima-direita)
    for i in range(12):
        x, y = 1 + i, 14 - i
        c.set(x, y, wood)
        c.set(x + 1, y, wood_d)
    for i in (1, 3, 5):
        c.set(1 + i, 14 - i, leather)
    c.set(1, 15, hex_rgba("d9a441"))
    c.set(0, 15, hex_rgba("8f5a12"))
    # lâminas em leque nos dois lados do topo
    rows = [
        "......HHH.......",
        "....HLLLMD......",
        "...HLLMMMDD.....",
        "..HLLMMMMDD.....",
        "..LLMMRMMMD.....",
        "..LMMMMMMMD.....",
        "...MMMMMMD......",
        "....DMMMD.......",
    ]
    pal = {"H": MITHRIL[6], "L": MITHRIL[5], "M": MITHRIL[3], "D": MITHRIL[1], "R": hex_rgba("6fe3ff")}
    c.from_rows(rows, pal, ox=5, oy=0)
    c.from_rows(["..DD", ".DMM", "DMML", "DML.", ".D.."], pal, ox=12, oy=5)
    c.outline(hex_rgba("10161f"))
    return c


# =================================================================================================
#  Monóculo de Avaliação e ícone do Escudo do Herói
# =================================================================================================
def monocle():
    c = Canvas(16, 16)
    gold, gold_l, gold_d = hex_rgba("f5cc4a"), hex_rgba("fff6b0"), hex_rgba("8f5a12")
    lens, lens_l = hex_rgba("9fd8ff", 200), hex_rgba("e8f8ff", 230)
    cx, cy, r = 9, 6, 5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r - 0.6:
                c.set(x, y, lens)
            elif d <= r + 0.5:
                c.set(x, y, gold_l if (x - cx) + (y - cy) < -2 else gold if (x - cx) + (y - cy) < 3 else gold_d)
    for (x, y) in ((7, 4), (8, 3), (7, 5)):
        c.set(x, y, lens_l)
    c.set(11, 8, hex_rgba("c8f0ff", 220))
    # corrente pendurada
    for i, (x, y) in enumerate(((5, 10), (4, 11), (4, 12), (3, 13), (3, 14), (2, 15))):
        c.set(x, y, gold if i % 2 == 0 else gold_d)
    # runa de avaliação no aro
    c.set(9, 1, hex_rgba("6fe3ff"))
    c.outline(hex_rgba("3a2410"))
    return c


def hero_shield_icon():
    c = Canvas(16, 16)
    face, face_l, face_d = MITHRIL[4], MITHRIL[5], MITHRIL[2]
    rim, rim_d = hex_rgba("f5cc4a"), hex_rgba("8f5a12")
    gem, gem_l, gem_d = hex_rgba("5fd85a"), hex_rgba("c8ffb8"), hex_rgba("2a8a2a")
    for y in range(1, 16):
        if y <= 9:
            half = 6
        else:
            half = max(0, 6 - (y - 9))
        for x in range(8 - half, 8 + half):
            edge = x in (8 - half, 8 + half - 1) or y == 1
            c.set(x, y, rim if edge else (face_l if x < 6 else face if x < 10 else face_d))
    for x in range(3, 13):
        c.set(x, 1, rim)
    c.set(2, 1, rim_d)
    c.set(13, 1, rim_d)
    # gema verde no centro
    c.from_rows([".l.", "lgG", ".G."], {"l": gem_l, "g": gem, "G": gem_d}, ox=7, oy=6)
    for (x, y) in ((8, 3), (8, 4), (5, 7), (11, 7), (8, 11)):
        c.set(x, y, hex_rgba("bfe8ff"))
    c.outline(hex_rgba("1c2638"))
    return c


def hero_shield_model():
    """Escudo do Herói 3D: escudo de Mithril (formato de brasão), aro de ouro e a gema verde no centro."""
    MITH = AR.M("metal", "f4faff", "d2e4f6", "8fa8c8", "3a4c66")
    RIM = AR.GOLD
    GEM = AR.GEM_GREEN
    RUNE = AR.RUNE_BLUE
    m = AR.Model("hero_shield", atlas=64)
    m.box((-5.2, -5.6, 0.6), (5.2, 10.2, 1.0), AR.DARK_OAK)
    m.box((-1.0, -3.0, -4.0), (1.0, 3.0, 0.6), AR.LEATHER)
    m.box((-1.2, -3.2, -4.6), (1.2, 3.2, -4.0), AR.DARK_IRON)
    m.box((-5.6, -4.0, 1.0), (5.6, 10.6, 2.0), MITH)
    m.cbox(0, -4.0, 1.5, 7.9, 7.9, 1.0, MITH, rot=("z", 45, (0, -4.0, 1.5)))
    m.box((-6.0, 10.4, 1.9), (6.0, 11.2, 2.5), RIM)
    m.box((-6.0, -4.0, 1.9), (-5.3, 11.2, 2.5), RIM)
    m.box((5.3, -4.0, 1.9), (6.0, 11.2, 2.5), RIM)
    AR.seg(m, (-5.65, -4.0), (0.0, -9.65), 0.7, RIM, depth=0.6, cz=2.2)
    AR.seg(m, (5.65, -4.0), (0.0, -9.65), 0.7, RIM, depth=0.6, cz=2.2)
    # moldura da gema e a gema verde (brilha)
    m.cbox(0, 3.0, 2.3, 4.2, 4.2, 0.6, RIM, rot=("z", 45, (0, 3.0, 2.3)))
    m.cbox(0, 3.0, 2.8, 2.6, 2.6, 0.8, GEM, rot=("z", 45, (0, 3.0, 2.8)))
    # runas anãs nas pontas
    for (x, y) in ((-3.6, 8.4), (3.6, 8.4), (0, -6.4), (-3.6, -1.6), (3.6, -1.6)):
        m.cbox(x, y, 2.15, 1.0, 1.0, 0.3, RUNE)
    return m


def gen_treasures():
    tex_i = os.path.join(A, "textures/item")
    monocle().save(os.path.join(tex_i, "appraisal_monocle.png"))
    hero_shield_icon().save(os.path.join(tex_i, "hero_shield.png"))
    write_json(os.path.join(A, "models/item/appraisal_monocle.json"), {"parent": "minecraft:item/generated",
                                                                      "textures": {"layer0": f"{NS}:item/appraisal_monocle"}})
    held, atlas = hero_shield_model().build(f"{NS}:item/dwarf_hold/hero_shield", seed=zlib.crc32(b"hero_shield") & 0xFFFF)
    atlas.save(os.path.join(tex_i, "dwarf_hold/hero_shield.png"))
    held["display"] = AR.DISPLAY["shield"]
    write_json(os.path.join(A, "models/item/dwarf_hold/hero_shield_held.json"), held)
    write_json(os.path.join(A, "models/item/dwarf_hold/hero_shield_blocking_held.json"),
               {"parent": f"{NS}:item/dwarf_hold/hero_shield_held", "display": AR.DISPLAY["shield_blocking"]})
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/hero_shield"}}

    def wrapper(held_name, overrides=None):
        o = {"loader": "neoforge:separate_transforms", "gui_light": "front",
             "textures": {"particle": f"{NS}:item/hero_shield"},
             "base": {"parent": f"{NS}:item/dwarf_hold/{held_name}"}, "perspectives": {"gui": flat, "fixed": flat}}
        if overrides:
            o["overrides"] = overrides
        return o
    write_json(os.path.join(A, "models/item/dwarf_hold/hero_shield_blocking.json"), wrapper("hero_shield_blocking_held"))
    write_json(os.path.join(A, "models/item/hero_shield.json"), wrapper("hero_shield_held", [
        {"predicate": {"blocking": 1}, "model": f"{NS}:item/dwarf_hold/hero_shield_blocking"}]))


# =================================================================================================
#  Forja Rúnica (bloco)
# =================================================================================================
RUNE_COLORS = ["ff7a3a", "4aa8ff", "c08a4a", "9af0c8", "ffe27a", "9a6ae0", "c08cff"]


def forge_textures():
    tex_b = os.path.join(A, "textures/block")
    # topo: bigorna de ardósia polida com um círculo rúnico
    top = vanilla("block/polished_deepslate")
    recolor(top, [hex_rgba(h) for h in ("15151a", "24242b", "34343d", "474752", "5c5c68")])
    pts = [(8 + round(5 * math.cos(a / 16 * 6.283)), 8 + round(5 * math.sin(a / 16 * 6.283))) for a in range(16)]
    for i, (x, y) in enumerate(pts):
        top.set(x, y, hex_rgba(RUNE_COLORS[i % len(RUNE_COLORS)]))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        top.set(x, y, hex_rgba("6fe3ff"))
    for (x, y) in ((8, 5), (5, 8), (10, 8), (8, 10)):
        top.set(x, y, hex_rgba("bfe8ff"))
    top.save(os.path.join(tex_b, "dwarf_rune_forge_top.png"))
    # lados da bigorna: metal escuro com runas gravadas brilhando
    side = vanilla("block/anvil")
    recolor(side, [hex_rgba(h) for h in ("12141a", "22262e", "343a46", "4c5464", "6a7486")])
    rnd = random.Random(7)
    for x in range(1, 15, 3):
        col = hex_rgba(rnd.choice(RUNE_COLORS))
        for y in (6, 7, 9):
            if rnd.random() < 0.8:
                side.set(x, y, col)
        side.set(x + 1, 8, col)
    side.save(os.path.join(tex_b, "dwarf_rune_forge_side.png"))
    # brasa do braseiro no pedestal (o magma do vanilla é animado: usa só o primeiro quadro)
    ember = vanilla("block/magma")
    e = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            e.set(x, y, ember.get(x, y))
    e.save(os.path.join(tex_b, "dwarf_rune_forge_ember.png"))


def forge_model():
    def el(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), glow=False):
        f = {}
        for face in faces:
            if face in ("up", "down"):
                uv = [frm[0], frm[2], to[0], to[2]]
            elif face in ("north", "south"):
                uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
            else:
                uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
            f[face] = {"uv": uv, "texture": tex}
        e = {"from": list(frm), "to": list(to), "faces": f}
        if glow:
            e["shade"] = False
            e["neoforge_data"] = {"block_light": 12, "sky_light": 12}
        return e
    elements = [
        el((3, 0, 2), (13, 4, 14), "#base"),                    # pé largo
        el((4, 4, 3), (12, 5, 13), "#tiles"),
        el((5, 5, 4), (11, 10, 12), "#base"),                   # coluna
        el((5.5, 6, 3.9), (10.5, 9, 4), "#ember", faces=("north",), glow=True),   # boca do braseiro (frente)
        el((5.5, 6, 12), (10.5, 9, 12.1), "#ember", faces=("south",), glow=True),
        el((3, 10, 1), (13, 11, 15), "#side"),                  # base da bigorna
        el((2, 11, 0), (14, 15, 16), "#side"),                  # mesa da bigorna
        el((2.01, 15, 0.01), (13.99, 15.01, 15.99), "#top", faces=("up",), glow=True),
        el((6, 11, -2), (10, 14, 0), "#side"),                  # chifre da bigorna
    ]
    model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
             "textures": {"particle": f"{NS}:block/dwarf_rune_forge_side", "base": "minecraft:block/polished_deepslate",
                          "tiles": "minecraft:block/deepslate_tiles", "side": f"{NS}:block/dwarf_rune_forge_side",
                          "top": f"{NS}:block/dwarf_rune_forge_top", "ember": f"{NS}:block/dwarf_rune_forge_ember"},
             "elements": elements}
    write_json(os.path.join(A, "models/block/dwarf_rune_forge.json"), model)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(os.path.join(A, "blockstates/dwarf_rune_forge.json"),
               {"variants": {f"facing={f}": ({"model": f"{NS}:block/dwarf_rune_forge", "y": r} if r else
                                             {"model": f"{NS}:block/dwarf_rune_forge"}) for f, r in rot.items()}})
    write_json(os.path.join(A, "models/item/dwarf_rune_forge.json"), {"parent": f"{NS}:block/dwarf_rune_forge"})


# =================================================================================================
#  Anões (GeckoLib)
# =================================================================================================
H = CB.H
P = CB.P


def dwarf(ident, tunic, tunic_d, trim, beard, beard_d, skin, extra):
    """Anão baixo e largo (≈1,4 bloco): pernas curtas, tronco largo, barba longa trançada, braços fortes."""
    m = CB.Model(ident)
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 7, 0), "root")
    belt = "#4a2e18"

    def torso(fs):
        fs["front"].row(6, H(belt))
        fs["front"].set(4, 6, H("#d9a441"))
        fs["front"].set(5, 6, H("#d9a441"))
        for n in ("left", "right", "back"):
            fs[n].row(6, H(belt))
        fs["front"].col(0, H(trim))
        fs["front"].col(9, H(trim))
    m.cube("body", (-5, 7, -3), (10, 9, 6), P(tunic, deco=torso))
    m.cube("body", (-5.5, 6, -3.5), (11, 2, 7), P(tunic_d, deco=CB.band([0], trim)))   # barra da túnica
    m.bone("head", (0, 16, 0), "body")

    def face(fs):
        f = fs["front"]
        f.row(2, H(darken(H(skin), 0.25)))         # sobrancelhas grossas
        f.set(2, 3, H("#1a1a1a"))
        f.set(5, 3, H("#1a1a1a"))
        f.set(1, 3, H("#ffffff"))
        f.set(6, 3, H("#ffffff"))
        f.rect(3, 4, 4, 5, darken(H(skin), 0.15))  # nariz grande
        f.rect(0, 5, 7, 7, H(beard))
        f.rect(3, 6, 4, 6, H("#7a2a1a"))           # boca no meio da barba
        for n in ("left", "right"):
            fs[n].rect(0, 4, fs[n].w - 1, 7, H(beard))
        fs["back"].rect(0, 3, 7, 7, H(beard_d))
    m.cube("head", (-4, 16, -4), (8, 8, 8), P(skin, deco=face))

    def beard_deco(fs):
        fs["front"].tint(lambda c, x, y: darken(c, 0.12) if (x + y) % 3 == 0 else c)
        fs["front"].row(fs["front"].h - 3, H("#d9a441"))  # argolas de ouro nas tranças
    m.cube("head", (-3.5, 9, -4.6), (7, 8, 2), P(beard, noise=0.05, deco=beard_deco))
    m.cube("head", (-2, 6, -4.4), (4, 3, 1), P(beard_d))
    for side, sx in (("right_arm", -1), ("left_arm", 1)):
        m.bone(side, (6 * sx, 15, 0), "body")
        ox = -9 if sx < 0 else 5
        m.cube(side, (ox, 7, -2), (4, 9, 4), P(tunic, deco=CB.chain(CB.bottom_rows(3, skin, faces=("front", "back", "left", "right", "bottom")),
                                                                  CB.band([5], trim))))
    for side, x in (("right_leg", -4.5), ("left_leg", 0.5)):
        m.bone(side, (x + 2, 7, 0), "root")
        m.cube(side, (x, 0, -2), (4, 7, 4), P("#4a3a2e", deco=CB.bottom_rows(3, "#2e2016")))
    extra(m)
    m.layout()
    m.paint(zlib.crc32(ident.encode()) & 0xFFFF)
    m.save_geo()
    m.save_texture()
    return m


def merchant_extra(m):
    # chapéu de pele com pena e mochila de mercador
    m.cube("head", (-4.5, 23, -4.5), (9, 3, 9), P("#6b4426", deco=CB.band([0], "#d9a441")))
    m.cube("head", (-3.5, 26, -3.5), (7, 2, 7), P("#7a5230"))
    m.cube("head", (3, 25, -1), (1, 5, 2), P("#c83a2a"), rotation=[0, 0, -20], pivot=[3.5, 25, 0])
    m.cube("body", (-4, 8, 3), (8, 8, 4), P("#7a5634", deco=CB.chain(CB.band([1, 6], "#4a321c"),
                                                                       lambda fs: fs["back"].rect(3, 3, 4, 4, H("#d9a441")))))
    m.cube("body", (-3.5, 16, 3.5), (7, 2, 3), P("#d9c8a0"))


def smith_extra(m):
    # elmo de ferro com chifres curtos, avental de couro e o martelo na mão direita
    m.cube("head", (-4.5, 21, -4.5), (9, 4, 9), P("#6a7486", deco=CB.chain(CB.band([3], "#3a4050"),
                                                                           lambda fs: fs["front"].set(4, 0, H("#6fe3ff"), True))))
    m.cube("head", (-1, 24.5, -1), (2, 2, 2), P("#8a96ac"))
    for sx in (-1, 1):
        m.cube("head", (5.5 if sx > 0 else -7.5, 22, -1), (2, 2, 2), P("#e6dcc0"), rotation=[0, 0, -25 * sx],
               pivot=[5.5 if sx > 0 else -5.5, 23, 0])
    m.cube("body", (-4, 6.5, -3.6), (8, 9, 1), P("#6b4228", deco=CB.band([0], "#3e2414")))
    m.bone("hammer", (-7, 8, 0), "right_arm")
    m.cube("hammer", (-7.5, 0, -2.5), (1, 9, 1), P("#5e4226"))
    m.cube("hammer", (-9, -1.5, -4), (4, 3, 4), P("#9aa6bc", deco=lambda fs: fs["front"].set(1, 1, H("#6fe3ff"), True)))


def dwarf_anims(ident, work):
    a = CB.humanoid_anims(walk_len=0.9, leg=34, arm=22, run_len=0.5, run_leg=50, run_arm=34, bob=0.4, lean=6)
    a["work"] = work
    CB.save_anims(ident, a)


def gen_dwarves():
    dwarf("dwarf_merchant", "#8a2a2a", "#5e1a1a", "#d9a441", "#b0602a", "#7a3e18", "#e2a87c", merchant_extra)
    wave = {"loop": False, "animation_length": 1.2, "bones": {
        "left_arm": {"rotation": CB.keys([(0, [0, 0, 0]), (0.25, [-150, 0, -10]), (0.45, [-150, 0, 15]), (0.65, [-150, 0, -10]),
                                         (0.85, [-150, 0, 15]), (1.2, [0, 0, 0])])},
        "head": {"rotation": CB.keys([(0, [0, 0, 0]), (0.3, [-8, 0, 6]), (1.2, [0, 0, 0])])}}}
    dwarf_anims("dwarf_merchant", wave)
    dwarf("dwarf_smith", "#4a4a52", "#2e2e36", "#c06a2a", "#2a2420", "#16120f", "#c8906a", smith_extra)
    dwarf_anims("dwarf_smith", CB.chop("right_arm", length=0.7, wind=-150, strike=-20, twist=10))


def main():
    gen_mithril()
    gen_treasures()
    forge_textures()
    forge_model()
    gen_dwarves()
    print("OK: arte do dwarf_hold gerada.")


if __name__ == "__main__":
    main()
