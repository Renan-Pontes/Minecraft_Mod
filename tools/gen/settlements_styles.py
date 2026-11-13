"""Onda 5 (city_styles) — os 4 estilos novos das cidades do jogador: Oriental, Élfico, Sombrio (gótico) e Anão.

Uso (na raiz do projeto):  py -I tools/gen/settlements_styles.py

Gera, SEM tocar nos arquivos dos 3 estilos de origem:
  - moldes .nbt  structure/settlements/<estilo>_<peça>.nbt  (praça, casas, fazenda, casa dos construtores, mercado,
    quartel, templo, prefeitura, torre de vigia, taverna, muralha antiga) e os lotes pequenos
    structure/settlements/<estilo>_lot_<smithy|stable|well|garden>.nbt  (ver Blueprint.template no Java);
  - texturas dos NPCs (settlement_npc/<papel>_<estilo>_<n>.png) e do campeão (city_champion_<estilo>.png), recoloridas
    a partir de um estilo de origem;
  - a gravura de cada estilo na Carta de Fundação (textures/gui/settlements/style_<estilo>.png).
Reaproveita tools/gen/settlements_build.py e city_build.py (paletas novas + um telhado com a cara de cada estilo):
  Oriental = beiral curvo e pontas da cumeeira viradas para cima; Sombrio = telhado íngreme com agulhas e fogo de alma;
  Élfico = folhas tomando o telhado; Anão = pedra com faixa e telhado de cobre.
As traduções ficam em tools/gen/settlements_styles_lang.py.
"""
import colorsys
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
import city_build as cb  # noqa: E402
import settlements_build as sb  # noqa: E402
from city_build import campfire, leaves, slab, stairs  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
NEW = ["oriental", "elven", "gothic", "dwarven"]

ROOF_VERDIGRIS = ("minecraft:waxed_oxidized_cut_copper_stairs", "minecraft:waxed_oxidized_cut_copper_slab",
                  "minecraft:waxed_oxidized_cut_copper")
ROOF_WARPED = ("minecraft:warped_stairs", "minecraft:warped_slab", "minecraft:warped_planks")
ROOF_CRIMSON = ("minecraft:crimson_stairs", "minecraft:crimson_slab", "minecraft:crimson_planks")
ROOF_BLACKSTONE = ("minecraft:polished_blackstone_brick_stairs", "minecraft:polished_blackstone_brick_slab",
                   "minecraft:polished_blackstone_bricks")
ROOF_COPPER = ("minecraft:waxed_cut_copper_stairs", "minecraft:waxed_cut_copper_slab", "minecraft:waxed_cut_copper")


def derive(src_pal, name, **kw):
    d = dict(src_pal.__dict__)
    d.pop("name")
    d.update(kw)
    return cb.Pal(name, **d)


ORIENTAL = derive(
    cb.PLAINS, "oriental", timber=True, steep=False, shutters=False,
    base="minecraft:andesite", base2="minecraft:polished_andesite", wall="minecraft:white_terracotta",
    post_log="minecraft:stripped_mangrove_log", beam_log="minecraft:stripped_dark_oak_log",
    civic="minecraft:white_terracotta", civic2="minecraft:polished_andesite", trim="minecraft:smooth_red_sandstone",
    floor="minecraft:spruce_planks", floor2="minecraft:dark_oak_planks", tile="minecraft:polished_andesite",
    wood="dark_oak", wood2="mangrove",
    roofs=[cb.ROOF_DEEPSLATE, cb.ROOF_DARK_OAK, cb.ROOF_DEEPSLATE], roof_civic=cb.ROOF_DEEPSLATE,
    window="minecraft:white_stained_glass_pane",
    pave=[("minecraft:polished_andesite", 40), ("minecraft:andesite", 30), ("minecraft:stone_bricks", 15),
          ("minecraft:smooth_stone", 15)],
    pave_main="minecraft:smooth_stone", pave_accent="minecraft:polished_andesite", curb="minecraft:andesite",
    rim="minecraft:polished_andesite",
    stone_stairs="minecraft:polished_andesite_stairs", stone_slab="minecraft:polished_andesite_slab",
    stone_wall="minecraft:andesite_wall",
    tree="oak", tree_log="minecraft:cherry_log", leaves="minecraft:cherry_leaves",
    flowers=["minecraft:pink_tulip", "minecraft:allium", "minecraft:azure_bluet", "minecraft:oxeye_daisy",
             "minecraft:white_tulip", "minecraft:red_tulip"],
    fence="minecraft:dark_oak_fence",
    awnings=[("red", "white"), ("pink", "white"), ("white", "red"), ("yellow", "red"), ("black", "red"),
             ("orange", "white")],
    glass=["red", "white", "pink", "yellow"], chimney="minecraft:andesite", carpet="red", carpet2="white",
    cw=[("minecraft:white_terracotta", 70), ("minecraft:calcite", 15), ("minecraft:andesite", 15)],
    cw_top="minecraft:deepslate_tiles", cw_wall="minecraft:andesite_wall",
)

ELVEN = derive(
    cb.PLAINS, "elven", timber=True, steep=False, shutters=False,
    base="minecraft:mossy_cobblestone", base2="minecraft:mossy_stone_bricks", wall="minecraft:birch_planks",
    post_log="minecraft:oak_log", beam_log="minecraft:stripped_oak_log",
    civic="minecraft:mossy_stone_bricks", civic2="minecraft:polished_diorite", trim="minecraft:chiseled_stone_bricks",
    floor="minecraft:birch_planks", floor2="minecraft:oak_planks", tile="minecraft:polished_diorite",
    wood="birch", wood2="oak",
    roofs=[ROOF_VERDIGRIS, ROOF_WARPED, ROOF_VERDIGRIS], roof_civic=ROOF_VERDIGRIS,
    pave=[("minecraft:mossy_stone_bricks", 35), ("minecraft:stone_bricks", 30), ("minecraft:mossy_cobblestone", 20),
          ("minecraft:cobblestone", 15)],
    pave_main="minecraft:stone_bricks", pave_accent="minecraft:mossy_stone_bricks", curb="minecraft:mossy_cobblestone",
    rim="minecraft:polished_diorite",
    stone_stairs="minecraft:mossy_stone_brick_stairs", stone_slab="minecraft:mossy_stone_brick_slab",
    stone_wall="minecraft:mossy_stone_brick_wall",
    tree="birch", tree_log="minecraft:birch_log", leaves="minecraft:azalea_leaves",
    flowers=["minecraft:lily_of_the_valley", "minecraft:azure_bluet", "minecraft:blue_orchid", "minecraft:oxeye_daisy",
             "minecraft:white_tulip", "minecraft:cornflower"],
    fence="minecraft:birch_fence",
    awnings=[("lime", "white"), ("green", "white"), ("white", "lime"), ("light_blue", "white"), ("yellow", "white"),
             ("cyan", "white")],
    glass=["lime", "white", "light_blue", "yellow"], chimney="minecraft:mossy_cobblestone", carpet="lime",
    carpet2="white",
    cw=[("minecraft:mossy_stone_bricks", 45), ("minecraft:stone_bricks", 35), ("minecraft:mossy_cobblestone", 20)],
    cw_top="minecraft:stone_bricks", cw_wall="minecraft:mossy_stone_brick_wall",
)

GOTHIC = derive(
    cb.SNOWY, "gothic", timber=True, steep=True, shutters=False,
    base="minecraft:cobbled_deepslate", base2="minecraft:polished_deepslate", wall="minecraft:deepslate_bricks",
    post_log="minecraft:polished_basalt", beam_log="minecraft:stripped_dark_oak_log",
    civic="minecraft:deepslate_bricks", civic2="minecraft:polished_blackstone_bricks",
    trim="minecraft:chiseled_deepslate",
    floor="minecraft:dark_oak_planks", floor2="minecraft:dark_oak_planks", tile="minecraft:polished_deepslate",
    wood="dark_oak", wood2="spruce",
    roofs=[ROOF_CRIMSON, ROOF_BLACKSTONE, ROOF_CRIMSON], roof_civic=ROOF_CRIMSON,
    window="minecraft:purple_stained_glass_pane",
    pave=[("minecraft:cobbled_deepslate", 35), ("minecraft:deepslate_bricks", 35),
          ("minecraft:cracked_deepslate_bricks", 10), ("minecraft:cobblestone", 10), ("minecraft:polished_deepslate", 10)],
    pave_main="minecraft:polished_deepslate", pave_accent="minecraft:deepslate_tiles", curb="minecraft:cobbled_deepslate",
    rim="minecraft:polished_deepslate",
    stone_stairs="minecraft:deepslate_brick_stairs", stone_slab="minecraft:deepslate_brick_slab",
    stone_wall="minecraft:deepslate_brick_wall", smooth_slab="minecraft:polished_deepslate_slab",
    tree="oak", tree_log="minecraft:dark_oak_log", leaves="minecraft:dark_oak_leaves",
    flowers=["minecraft:allium", "minecraft:lily_of_the_valley", "minecraft:azure_bluet", "minecraft:fern"],
    fence="minecraft:dark_oak_fence",
    awnings=[("purple", "black"), ("black", "purple"), ("red", "black"), ("gray", "black"), ("magenta", "black"),
             ("purple", "gray")],
    glass=["purple", "magenta", "red", "black"], chimney="minecraft:deepslate_bricks", carpet="purple",
    carpet2="black",
    cw=[("minecraft:deepslate_bricks", 60), ("minecraft:cobbled_deepslate", 25), ("minecraft:cracked_deepslate_bricks", 15)],
    cw_top="minecraft:deepslate_bricks", cw_wall="minecraft:deepslate_brick_wall",
)

DWARVEN = derive(
    cb.SNOWY, "dwarven", timber=False, steep=False, shutters=False,
    base="minecraft:cobbled_deepslate", base2="minecraft:polished_deepslate", wall="minecraft:stone_bricks",
    post_log=None, beam_log="minecraft:stripped_spruce_log", corner="minecraft:polished_deepslate",
    band="minecraft:waxed_cut_copper",
    civic="minecraft:stone_bricks", civic2="minecraft:deepslate_bricks", trim="minecraft:chiseled_stone_bricks",
    floor="minecraft:polished_andesite", floor2="minecraft:spruce_planks", tile="minecraft:polished_deepslate",
    wood="spruce", wood2="dark_oak",
    roofs=[ROOF_COPPER, cb.ROOF_DEEPSLATE, ROOF_COPPER], roof_civic=ROOF_COPPER,
    pave=[("minecraft:stone_bricks", 40), ("minecraft:cobblestone", 25), ("minecraft:andesite", 15),
          ("minecraft:deepslate_bricks", 10), ("minecraft:cracked_stone_bricks", 10)],
    pave_main="minecraft:polished_deepslate", pave_accent="minecraft:polished_andesite",
    curb="minecraft:cobbled_deepslate", rim="minecraft:polished_deepslate",
    stone_wall="minecraft:cobbled_deepslate_wall",
    tree="spruce",
    flowers=["minecraft:dandelion", "minecraft:poppy", "minecraft:azure_bluet", "minecraft:oxeye_daisy"],
    awnings=[("yellow", "brown"), ("orange", "brown"), ("brown", "yellow"), ("red", "yellow"), ("gray", "yellow"),
             ("yellow", "white")],
    glass=["orange", "yellow", "red", "white"], chimney="minecraft:deepslate_bricks", carpet="yellow",
    carpet2="brown",
    cw=[("minecraft:deepslate_bricks", 45), ("minecraft:stone_bricks", 40), ("minecraft:cracked_stone_bricks", 15)],
    cw_top="minecraft:deepslate_bricks", cw_wall="minecraft:cobbled_deepslate_wall",
)

PALS = {"oriental": ORIENTAL, "elven": ELVEN, "gothic": GOTHIC, "dwarven": DWARVEN}
BANNERS = {
    "oriental": ("red", [("circle", "white"), ("stripe_top", "black"), ("stripe_bottom", "black"), ("border", "black")]),
    "elven": ("lime", [("flower", "white"), ("gradient_up", "green"), ("triangles_top", "white"), ("border", "white")]),
    "gothic": ("purple", [("skull", "black"), ("gradient", "black"), ("triangles_bottom", "black"), ("border", "black")]),
    "dwarven": ("yellow", [("bricks", "brown"), ("rhombus", "brown"), ("circle", "yellow"), ("border", "brown")]),
}


# =================================================================================================
#  Telhado com a cara do estilo (embrulha cb.roof)
# =================================================================================================
_roof = cb.roof


def roof(s, x0, z0, x1, z1, y0, axis=None, mat=None, overhang=1, gable=None, gable_window=True, end_overhang=None):
    top = _roof(s, x0, z0, x1, z1, y0, axis=axis, mat=mat, overhang=overhang, gable=gable, gable_window=gable_window,
                end_overhang=end_overhang)
    P = s.P
    if P.flat or P.name not in PALS:
        return top
    ax = axis or ("x" if (x1 - x0) >= (z1 - z0) else "z")
    stair_b, slab_b, full_b = mat or P.roofs[0]
    e0, e1 = end_overhang or (overhang, overhang)
    if ax == "x":
        a0, a1, c0, c1 = x0 - e0, x1 + e1, z0 - overhang, z1 + overhang
        lo_out, hi_out = "west", "east"

        def at(a, c):
            return a, c
    else:
        a0, a1, c0, c1 = z0 - e0, z1 + e1, x0 - overhang, x1 + overhang
        lo_out, hi_out = "north", "south"

        def at(a, c):
            return c, a

    def inside(x, z):
        return 0 <= x < s.size[0] and 0 <= z < s.size[2]

    def is_block(x, y, z, name):
        v = s.get(x, y, z)
        return v is not None and cb.base_name(v) == name

    if P.name == "oriental":
        # beiral: a fiada de baixo abre em laje e os 4 cantos sobem meio bloco
        for a in range(a0, a1 + 1):
            for c in (c0, c1):
                x, z = at(a, c)
                if inside(x, z) and is_block(x, y0, z, stair_b):
                    s.set(x, y0, z, slab(slab_b, "top" if a in (a0, a1) else "bottom"))
        # pontas da cumeeira viradas para cima
        for a, out in ((a0, lo_out), (a1, hi_out)):
            for c in range(c0, c1 + 1):
                x, z = at(a, c)
                if inside(x, z) and is_block(x, top, z, slab_b):
                    s.set(x, top, z, stairs(stair_b, out))
    elif P.name == "gothic":
        # agulhas nas pontas da cumeeira
        for a in (a0, a1):
            for c in range(c0, c1 + 1):
                x, z = at(a, c)
                if inside(x, z) and is_block(x, top, z, slab_b):
                    s.set(x, top, z, full_b)
                    s.set(x, top + 1, z, "minecraft:deepslate_brick_wall")
                    s.set(x, top + 2, z, "minecraft:iron_bars")
    elif P.name == "elven":
        # folhas tomando as águas do telhado (mais na beira)
        lv = leaves("minecraft:flowering_azalea_leaves")
        lv2 = leaves("minecraft:azalea_leaves")
        k = 0
        while c0 + k < c1 - k and k < 3:
            for a in range(a0, a1 + 1):
                for c in (c0 + k, c1 - k):
                    x, z = at(a, c)
                    if not inside(x, z) or not is_block(x, y0 + k, z, stair_b):
                        continue
                    if s.rnd.random() < (0.34 if k == 0 else 0.16) and s.empty(x, y0 + k + 1, z):
                        s.set(x, y0 + k + 1, z, lv if s.rnd.random() < 0.3 else lv2)
            k += 1
    return top


SOUL = {"minecraft:lantern": "minecraft:soul_lantern", "minecraft:torch": "minecraft:soul_torch",
        "minecraft:wall_torch": "minecraft:soul_wall_torch", "minecraft:campfire": "minecraft:soul_campfire"}


def restyle(s, style):
    """Acabamento por estilo depois de o molde estar montado."""
    for pos, (state, nbt) in list(s.blocks.items()):
        n = cb.base_name(state)
        if style == "gothic" and n in SOUL:
            s.blocks[pos] = (SOUL[n] + state[len(n):], nbt)
        elif style == "elven" and n == "minecraft:oak_leaves":
            s.blocks[pos] = ("minecraft:azalea_leaves" + state[len(n):], nbt)


_save = sb.save


def save(s, style, piece):
    if style in PALS:
        restyle(s, style)
    return _save(s, style, piece)


# =================================================================================================
#  Praça (igual à de settlements_build, com os canteiros de cada estilo)
# =================================================================================================
def plaza(style):
    s = sb.SB(style, "plaza", 13, 14, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12)
    for x, z, d in cb.disk(6, 6, 3.3):
        if 0 <= x < 13 and 0 <= z < 13:
            s.set(x, 0, z, P.pave_accent if d > 1.6 else P.trim)
    for k in range(13):
        if abs(k - 6) > 3:
            s.set(6, 0, k, P.pave_main)
            s.set(k, 0, 6, P.pave_main)
    for x, z in cb.perim(0, 0, 12, 12):
        s.set(x, 0, z, P.curb)
    s.set(6, 0, 10, P.trim)
    s.set(6, 1, 10, cb.TRAVEL_STONE)
    cb.bench(s, 5, 2, "north", n=3)
    cb.bench(s, 2, 5, "west", n=3, along="z")
    cb.bench(s, 10, 5, "east", n=3, along="z")
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        cb.lamp_post(s, x, z, h=3)
    for x in (4, 8):
        cb.flag_pole(s, x, 11, 1, 4, BANNERS[style], "south")
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        if style == "oriental":
            # cerejeira baixa num canteiro de pétalas
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, cb.log("minecraft:cherry_log"))
            s.set(x, 2, z, cb.log("minecraft:cherry_log"))
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (dx, dz) != (0, 0) or True:
                        s.soft(x + dx, 3, z + dz, leaves("minecraft:cherry_leaves"))
            s.soft(x, 4, z, leaves("minecraft:cherry_leaves"))
        elif style == "elven":
            s.set(x, 0, z, "minecraft:moss_block")
            s.set(x, 1, z, leaves("minecraft:flowering_azalea_leaves"))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, z + dz) not in ((6, 3), (3, 6), (9, 6), (6, 9)):
                    s.set(x + dx, 0, z + dz, "minecraft:moss_block")
                    s.set(x + dx, 1, z + dz, s.rnd.choice(P.flowers))
        else:
            # braseiro (o do Sombrio vira fogo de alma no acabamento; o do Anão é a forja da praça)
            s.set(x, 0, z, P.base)
            s.set(x, 1, z, campfire(facing="south"))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                s.set(x + dx, 1, z + dz, slab(P.stone_slab))
    s.standing_sign(6, 1, 12, "south", ["welcome", "welcome2"], color="yellow" if style != "gothic" else "white")
    return save(s, style, "plaza")


def lots(style):
    P = PALS[style]
    for key, fn in (("smithy", cb.smithy), ("stable", cb.stable), ("well", cb.well), ("garden", cb.garden)):
        save(sb.capture(fn, P), style, "lot_" + key)


def build_structures():
    sb.PAL.update(PALS)
    sb.BANNER.update(BANNERS)
    sb.GATE_HEIGHT.update({k: 4 for k in NEW})
    cb.roof = roof
    sb.save = save
    old_styles = sb.STYLES
    sb.STYLES = NEW
    sb.BUILT.clear()
    try:
        for style in NEW:
            plaza(style)
            sb.houses_small(style)
            sb.farm(style)
            sb.house_large(style)
            sb.builders_hall(style)
            sb.market(style)
            sb.barracks(style)
            sb.temple(style)
            sb.town_hall(style)
            sb.wall_segment(style)
            sb.wall_gate(style)
            sb.wall_tower(style)
            sb.watchtower(style)
            sb.tavern(style)
            lots(style)
        sb.verify()
    finally:
        cb.roof = _roof
        sb.save = _save
        sb.STYLES = old_styles
    return dict(sb.BUILT)


# =================================================================================================
#  Texturas de NPC e campeão: recolore as de um estilo de origem
# =================================================================================================
# estilo novo: (estilo de origem, matiz de origem em graus, matiz novo, saturação ×, brilho ×, brilho dos metais ×)
RECOLOR = {
    "oriental": ("kingdom", 221, 4, 1.05, 0.98, 1.0),
    "elven": ("kingdom", 221, 128, 0.85, 1.05, 1.08),
    "gothic": ("kingdom", 221, 272, 0.9, 0.72, 0.62),
    "dwarven": ("nordic", 201, 28, 1.25, 0.95, 0.85),
}
ROLES = {"builder": 2, "resident": 4, "guard": 2, "merchant": 2, "militia": 2}


def recolor(src, dst, hue0, hue1, sat, val, metal):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            d = abs(h * 360 - hue0)
            d = min(d, 360 - d)
            if s > 0.18 and d < 42:
                # o pano/tinta da cor do estilo muda de cor
                h, s, v = hue1 / 360, min(1, s * sat), min(1, v * val)
            elif s < 0.16 and 0.2 < v < 0.93:
                # metais e cinzas (armadura, pedra): mais claros ou mais escuros conforme o estilo
                v = min(1, v * metal)
            else:
                continue
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    im.save(dst)


def npc_textures():
    n = 0
    npc = os.path.join(A, "textures/entity/settlement_npc")
    ent = os.path.join(A, "textures/entity")
    for style, (base, h0, h1, sat, val, metal) in RECOLOR.items():
        for role, count in ROLES.items():
            for v in range(count):
                recolor(os.path.join(npc, f"{role}_{base}_{v}.png"), os.path.join(npc, f"{role}_{style}_{v}.png"),
                        h0, h1, sat, val, metal)
                n += 1
        recolor(os.path.join(ent, f"city_champion_{base}.png"), os.path.join(ent, f"city_champion_{style}.png"),
                h0, h1, sat, val, metal)
        n += 1
    return n


# =================================================================================================
#  Gravuras da Carta de Fundação (128 x 80)
# =================================================================================================
def rgb(h):
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def sky(d, top, bot, W=128, H=80):
    t, b = rgb(top), rgb(bot)
    for y in range(H):
        k = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(round(t[i] + (b[i] - t[i]) * k) for i in range(3)) + (255,))


def pagoda(d, cx, base, tiers, w, body, roofc, trim):
    """Torre de telhados curvos: cada andar um corpo e um beiral que sobe nas pontas."""
    y = base
    for i in range(tiers):
        bw = w - i * 4
        d.rectangle([cx - bw // 2, y - 7, cx + bw // 2, y], fill=rgb(body))
        d.line([(cx - bw // 2, y - 7), (cx - bw // 2, y)], fill=rgb(trim))
        d.line([(cx + bw // 2, y - 7), (cx + bw // 2, y)], fill=rgb(trim))
        ew = bw // 2 + 5
        d.polygon([(cx - ew, y - 8), (cx + ew, y - 8), (cx + bw // 2 - 1, y - 12), (cx - bw // 2 + 1, y - 12)],
                  fill=rgb(roofc))
        d.point([(cx - ew, y - 9), (cx + ew, y - 9), (cx - ew - 1, y - 10), (cx + ew + 1, y - 10)], fill=rgb(roofc))
        y -= 12
    d.line([(cx, y), (cx, y - 5)], fill=rgb("E8B84A"))


def torii(d, cx, base, w, h, red="C9302A", dark="2A1A1A"):
    d.rectangle([cx - w // 2, base - h, cx - w // 2 + 1, base], fill=rgb(red))
    d.rectangle([cx + w // 2 - 1, base - h, cx + w // 2, base], fill=rgb(red))
    d.rectangle([cx - w // 2 - 2, base - h + 3, cx + w // 2 + 2, base - h + 4], fill=rgb(red))
    d.rectangle([cx - w // 2 - 4, base - h - 1, cx + w // 2 + 4, base - h], fill=rgb(dark))
    d.point([(cx - w // 2 - 5, base - h - 2), (cx + w // 2 + 5, base - h - 2)], fill=rgb(dark))


def blob(d, cx, cy, r, cols, rnd, n=26):
    for _ in range(n):
        a, rr = rnd.random() * 6.283, rnd.random() * r
        x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr * 0.75
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=rgb(rnd.choice(cols)))


def frame(d, W=128, H=80):
    d.rectangle([0, 0, W - 1, H - 1], outline=rgb("2A2016"))


def art_oriental():
    im = Image.new("RGBA", (128, 80))
    d = ImageDraw.Draw(im)
    rnd = random.Random("art/oriental")
    sky(d, "F08A6A", "FFE2C0")
    d.ellipse([84, 8, 108, 32], fill=rgb("E8423A"))
    for x in range(128):   # montanhas ao longe
        h = 44 + int(7 * math.sin(x / 17.0)) + int(3 * math.sin(x / 5.0 + 2))
        d.line([(x, h), (x, 80)], fill=rgb("B58AA0"))
    d.rectangle([0, 62, 128, 80], fill=rgb("7FA860"))
    d.rectangle([0, 66, 128, 80], fill=rgb("6A9450"))
    pagoda(d, 64, 62, 3, 30, "F2EDE4", "3A3A46", "A8322A")
    torii(d, 26, 68, 16, 16)
    for tx, ty in ((104, 50), (116, 56), (8, 54)):
        d.line([(tx, ty + 12), (tx, ty)], fill=rgb("5A3A30"), width=2)
        blob(d, tx, ty - 2, 9, ["F5B8D0", "F090B8", "FFD8E8", "E87AA8"], rnd)
    for _ in range(26):   # pétalas
        d.point((rnd.randrange(128), rnd.randrange(20, 70)), fill=rgb("FFD8E8"))
    d.rectangle([40, 70, 88, 72], fill=rgb("C8C0B0"))
    frame(d)
    return im


def art_elven():
    im = Image.new("RGBA", (128, 80))
    d = ImageDraw.Draw(im)
    rnd = random.Random("art/elven")
    sky(d, "1E5A4A", "A8E8B0")
    for x in range(128):
        h = 40 + int(6 * math.sin(x / 13.0 + 1))
        d.line([(x, h), (x, 80)], fill=rgb("2E7A52"))
    d.rectangle([0, 64, 128, 80], fill=rgb("3E8A4A"))
    # árvores-casa: troncos claros, copas grandes, janelas acesas e pontes
    for tx, th, r in ((24, 40, 15), (64, 50, 19), (102, 38, 14)):
        d.rectangle([tx - 3, 66 - th, tx + 3, 68], fill=rgb("E8DCC0"))
        d.line([(tx - 3, 66 - th), (tx - 3, 68)], fill=rgb("B8A888"))
        blob(d, tx, 64 - th, r, ["4CB86A", "3A9A56", "6FD88A", "2E7A46"], rnd, n=60)
        for wy in range(66 - th + 10, 62, 9):
            d.rectangle([tx - 1, wy, tx + 1, wy + 2], fill=rgb("FFF2A8"))
        d.polygon([(tx - 6, 62), (tx + 6, 62), (tx, 55)], fill=rgb("4AB8A8"))
    d.line([(27, 46), (61, 42)], fill=rgb("C8B890"))
    d.line([(67, 44), (99, 48)], fill=rgb("C8B890"))
    for _ in range(22):   # lumes
        x, y = rnd.randrange(128), rnd.randrange(8, 70)
        d.point((x, y), fill=rgb("F8FFC0"))
        d.point((x + 1, y), fill=rgb("C8F0A0"))
    frame(d)
    return im


def art_gothic():
    im = Image.new("RGBA", (128, 80))
    d = ImageDraw.Draw(im)
    rnd = random.Random("art/gothic")
    sky(d, "140C24", "5A3A72")
    d.ellipse([88, 6, 112, 30], fill=rgb("E8E0F0"))
    d.ellipse([94, 4, 116, 26], fill=rgb("2A1C40"))   # lua minguante
    for _ in range(30):
        d.point((rnd.randrange(128), rnd.randrange(34)), fill=rgb("D8C8F0"))
    d.rectangle([0, 64, 128, 80], fill=rgb("1E1A28"))
    stone, dark, glow = "3A3848", "22202C", "5AE8F0"
    # catedral: nave e torres com agulhas
    d.rectangle([44, 40, 84, 66], fill=rgb(stone))
    d.polygon([(44, 40), (84, 40), (64, 22)], fill=rgb("5A2A48"))
    for tx, th in ((40, 50), (88, 50), (64, 60)):
        d.rectangle([tx - 4, 66 - th + 14, tx + 4, 66], fill=rgb(stone))
        d.polygon([(tx - 5, 66 - th + 14), (tx + 5, 66 - th + 14), (tx, 66 - th - 4)], fill=rgb("5A2A48"))
        d.line([(tx, 66 - th - 4), (tx, 66 - th - 8)], fill=rgb("9A98A8"))
        d.rectangle([tx - 1, 66 - th + 20, tx, 66 - th + 25], fill=rgb(glow))
    d.ellipse([60, 44, 68, 52], fill=rgb("B060E0"))   # rosácea
    d.rectangle([62, 58, 66, 66], fill=rgb(dark))
    for hx in (12, 24, 106, 118):   # casas de telhado pontudo
        d.rectangle([hx - 5, 56, hx + 5, 66], fill=rgb(stone))
        d.polygon([(hx - 6, 56), (hx + 6, 56), (hx, 42)], fill=rgb("4A2440"))
        d.point((hx, 61), fill=rgb(glow))
    for bx, by in ((30, 18), (50, 12), (74, 16)):   # morcegos
        d.line([(bx - 2, by), (bx, by + 1), (bx + 2, by)], fill=rgb("0C0814"))
    frame(d)
    return im


def art_dwarven():
    im = Image.new("RGBA", (128, 80))
    d = ImageDraw.Draw(im)
    rnd = random.Random("art/dwarven")
    sky(d, "4A5A7A", "E8B878")
    # a montanha
    d.polygon([(0, 80), (0, 46), (30, 20), (52, 34), (78, 8), (104, 30), (128, 22), (128, 80)], fill=rgb("5A5A66"))
    d.polygon([(78, 8), (70, 18), (76, 17), (82, 22), (88, 18)], fill=rgb("E8ECF4"))
    d.polygon([(30, 20), (25, 26), (31, 25), (36, 28)], fill=rgb("E8ECF4"))
    for _ in range(70):
        x, y = rnd.randrange(128), rnd.randrange(30, 80)
        d.point((x, y), fill=rgb(rnd.choice(["4A4A56", "6A6A78"])))
    # o grande portão com o brilho da forja
    d.rectangle([48, 40, 80, 72], fill=rgb("3A3A44"))
    d.rectangle([52, 46, 76, 72], fill=rgb("F0902A"))
    d.rectangle([56, 52, 72, 72], fill=rgb("FFD060"))
    d.rectangle([48, 40, 80, 43], fill=rgb("C87840"))
    for px_ in (46, 80):
        d.rectangle([px_, 38, px_ + 2, 72], fill=rgb("2E2E38"))
        d.rectangle([px_ - 1, 36, px_ + 3, 38], fill=rgb("C87840"))
    d.rectangle([63, 46, 65, 72], fill=rgb("3A3A44"))
    d.rectangle([0, 72, 128, 80], fill=rgb("44444E"))
    for cx_, cy_ in ((20, 56), (104, 52)):   # chaminés soltando fumaça
        d.rectangle([cx_, cy_, cx_ + 3, cy_ + 10], fill=rgb("2E2E38"))
        for i in range(5):
            d.ellipse([cx_ - 1 + i, cy_ - 4 - i * 3, cx_ + 4 + i, cy_ - i * 3], fill=rgb("9A9AA6"))
    for _ in range(12):
        d.point((rnd.randrange(50, 78), rnd.randrange(30, 46)), fill=rgb("FFB040"))
    frame(d)
    return im


def gui():
    out = os.path.join(A, "textures/gui/settlements")
    for style, fn in (("oriental", art_oriental), ("elven", art_elven), ("gothic", art_gothic), ("dwarven", art_dwarven)):
        fn().save(os.path.join(out, f"style_{style}.png"))


def main():
    built = build_structures()
    n = npc_textures()
    gui()
    for k, (size, cnt) in sorted(built.items()):
        print(f"{k:30s} {str(size):14s} {cnt:5d} blocos")
    print(f"settlements_styles: {len(built)} moldes, {n} texturas, {len(NEW)} gravuras")


if __name__ == "__main__":
    main()
