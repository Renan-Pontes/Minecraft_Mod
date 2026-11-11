"""Moldes (.nbt) das plantas da cidade do jogador — módulo "settlements" (chamado por tools/gen/settlements.py).

Saída: data/isekai_mahou/structure/settlements/<estilo>_<peça>.nbt (estilo = kingdom / desert / nordic).
Convenções (iguais às de feature/settlements/build/*.java):
  - y=0 é o chão (substitui a camada do terreno); a ENTRADA de todo prédio fica no lado +Z (sul): o Java gira o molde
    para a porta olhar para a praça;
  - prédios cabem num terreno de 16 blocos (no máximo 13 x 13, sobra rua); a praça é do terreno do meio e deixa o
    coração (centro, y=1) livre;
  - muralha: trecho 9 x 3, portão 7 x 3 (vão em x=2..4, z=1, y=1..altura do portão do estilo: 4/4/3) e torre 5 x 5,
    simétricos em z (o mesmo molde serve nos dois lados da cidade);
  - o ar não é salvo (o Java limpa o canteiro antes), baús vêm VAZIOS (sem loot) e entidades não vão no molde
    (moradores/guardas chegam pela cidade).
Reaproveita a biblioteca de construção das cidades dos reinos (tools/gen/city_build.py: paletas, paredes, telhados...)
e as casinhas/horta dela como variantes.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
import city_build as cb  # noqa: E402
from city_build import (AIR, st, stairs, slab, log, lantern, wall_torch, campfire, ladder, leaves, mc,  # noqa: E402
                        candle, potted, chain)

NS = "isekai_mahou"
OUT = os.path.join(ROOT, "src/main/resources/data", NS, "structure", "settlements")
PAL = {"kingdom": cb.PLAINS, "desert": cb.DESERT, "nordic": cb.SNOWY}
STYLES = ["kingdom", "desert", "nordic"]
GATE_HEIGHT = {"kingdom": 4, "desert": 4, "nordic": 3}
BANNER = {
    "kingdom": ("blue", [("rhombus", "yellow"), ("circle", "blue"), ("stripe_top", "yellow"), ("border", "yellow")]),
    "desert": ("orange", [("gradient_up", "yellow"), ("circle", "yellow"), ("triangles_bottom", "brown"),
                          ("border", "yellow")]),
    "nordic": ("light_blue", [("straight_cross", "white"), ("triangles_top", "white"), ("circle", "cyan"),
                              ("border", "white")]),
}
BUILT = {}
SIGN_KEYS = set()


class SB(cb.B):
    """Molde de uma peça da cidade do jogador (paleta do estilo, placas com chaves próprias)."""

    def __init__(self, style, name, sx, sy, sz):
        super().__init__(name, sx, sy, sz, PAL[style])
        self.style = style
        self.rnd.seed(f"settlements/{style}/{name}")

    def _sign_nbt(self, keys, color, glow, both):
        msgs = []
        for k in keys:
            if k:
                SIGN_KEYS.add(k)
                msgs.append(json.dumps({"translate": f"sign.{NS}.settlement.{k}"}))
            else:
                msgs.append('""')
        msgs += ['""'] * (4 - len(msgs))
        front = {"messages": msgs, "color": color, "has_glowing_text": cb.Byte(1 if glow else 0)}
        back = dict(front) if both else {"messages": ['""'] * 4, "color": "black", "has_glowing_text": cb.Byte(0)}
        return {"front_text": front, "back_text": back, "is_waxed": cb.Byte(1)}


def clean(s):
    """Tira loot/marca de baú de cidade (os baús da cidade do jogador vêm vazios) e as entidades."""
    for pos, (state, nbt) in list(s.blocks.items()):
        if nbt and ("LootTable" in nbt or "CityChest" in nbt):
            n = {k: v for k, v in nbt.items() if k not in ("LootTable", "CityChest", "NeoForgeData")}
            s.blocks[pos] = (state, n or None)
    s.entities = []


def save(s, style, piece):
    cb.fix_connections(s)
    clean(s)
    used = [y for (x, y, z), (st_, _) in s.blocks.items() if st_ != AIR]
    s.size = (s.size[0], max(used + [1]) + 1, s.size[2])
    name = f"{style}_{piece}"
    n = s.save(os.path.join(OUT, name + ".nbt"))
    BUILT[name] = (s.size, n)
    return n


def capture(fn, P):
    """Roda um construtor do city_build e devolve o molde em vez de gravar em structure/city."""
    got = []
    old = cb.save
    cb.save = lambda s: got.append(s) or 0
    try:
        fn(P)
    finally:
        cb.save = old
    return got[0]


def label(s, x, y, z, facing, key, color="black", glow=True):
    s.sign(x, y, z, facing, ["", key], color=color, glow=glow)


def fill(s, x0, y0, z0, x1, y1, z1, state):
    s.fill(x0, y0, z0, x1, y1, z1, state)


def mixed(s, weighted):
    return s.pick(weighted)


# =================================================================================================
#  Praça (terreno do meio; coração em (6, 1, 6))
# =================================================================================================
def plaza(style):
    s = SB(style, "plaza", 13, 14, 13)
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
    # Pedra de Teleporte ao sul do coração (a pedra se liga ao lugar da cidade sozinha)
    s.set(6, 0, 10, P.trim)
    s.set(6, 1, 10, cb.TRAVEL_STONE)
    # bancos olhando para o coração
    cb.bench(s, 5, 2, "north", n=3)
    cb.bench(s, 2, 5, "west", n=3, along="z")
    cb.bench(s, 10, 5, "east", n=3, along="z")
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        cb.lamp_post(s, x, z, h=3)
    # estandartes do estilo dos dois lados da pedra
    for x in (4, 8):
        cb.flag_pole(s, x, 11, 1, 4, BANNER[style], "south")
    # canteiros/árvores/braseiros nas diagonais
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        if style == "kingdom":
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, leaves("minecraft:flowering_azalea_leaves"))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, z + dz) not in ((6, 3), (3, 6), (9, 6), (6, 9)):
                    s.set(x + dx, 0, z + dz, "minecraft:grass_block")
                    s.set(x + dx, 1, z + dz, s.rnd.choice(P.flowers))
        elif style == "desert":
            s.set(x, 0, z, "minecraft:sand")
            cb.tree(s, x, z, kind="palm", h=4)
        else:
            s.set(x, 0, z, "minecraft:cobblestone")
            s.set(x, 1, z, campfire(facing="south"))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                s.set(x + dx, 1, z + dz, slab("minecraft:cobblestone_slab"))
    s.standing_sign(6, 1, 12, "south", ["welcome", "welcome2"], color="yellow" if style != "nordic" else "white")
    return save(s, style, "plaza")


# =================================================================================================
#  Casas pequenas e horta (do city_build) + casa grande própria
# =================================================================================================
def houses_small(style):
    P = PAL[style]
    for i, fn in enumerate((cb.house_a, cb.house_b, cb.house_c)):
        s = capture(fn, P)
        save(s, style, "house_small" if i == 0 else f"house_small_{i}")


def farm(style):
    s = capture(cb.garden, PAL[style])
    save(s, style, "farm")


def house_large(style):
    save(capture(cb.house_d, PAL[style]), style, "house_large")
    # variante 1: sobrado com varanda, cozinha embaixo e quartos em cima
    s = SB(style, "house_large_1", 13, 26, 13)
    P = s.P
    r = s.rnd
    cb.yard(s)
    x0, z0, x1, z1 = 1, 1, 11, 8
    top = cb.shell(s, x0, z0, x1, z1, floors=2, fh=5)
    cb.entry(s, 6, 1, z1, "south")
    cb.windows(s, x0, z0, x1, z1, 2, skip=[(6, z1)])
    cb.windows(s, x0, z0, x1, z1, 7)
    ridge = cb.roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[min(1, len(P.roofs) - 1)])
    # lareira e chaminé
    s.set(2, 1, 2, campfire(facing="south"))
    cb.chimney(s, 2, 2, 2, max(ridge - 1, top + 2))
    s.set(3, 1, 2, st("smoker", facing="south", lit="false"))
    s.set(4, 1, 2, st("furnace", facing="south", lit="false"))
    s.barrel(5, 1, 2)
    s.set(5, 2, 2, potted("fern"))
    cb.table(s, 8, 1, 4, ("west", "east"))
    cb.table(s, 8, 1, 6, ("west", "east"))
    s.chest(10, 1, 2, "house", "west")
    cb.rug(s, 3, 4, 5, 6, 1, r.choice(["red", "brown", "green", "blue"]))
    cb.hang(s, 6, 4, 4)
    # escada de mão até o andar de cima
    for y in range(1, 6):
        s.set(10, y, 7, ladder("north"))
    s.set(9, 1, 7, "minecraft:crafting_table")
    # andar de cima: quartos
    for x, col in ((2, "red"), (4, "blue")):
        s.bed(x, 6, 3, col, "north")
    s.bed(8, 6, 3, r.choice(cb.BED_COLORS), "north")
    s.chest(6, 6, 2, "house", "south")
    cb.shelves(s, 2, 7, 3, 7, 6, 7)
    s.set(5, 6, 7, st("lectern", facing="north", has_book="false", powered="false"))
    cb.rug(s, 3, 5, 8, 6, 6, r.choice(["white", "light_gray", "yellow"]), border="brown")
    cb.hang(s, 6, 9, 5)
    # varanda na frente do andar de cima (piso em y=5, grade na beira, pilares embaixo)
    for x in range(4, 9):
        for z in (9, 10):
            s.set(x, 5, z, P.w("slab", type="top", waterlogged="false") if not P.flat else "minecraft:smooth_sandstone")
        s.set(x, 6, 10, P.fence)
    for x in (4, 8):
        s.set(x, 6, 9, P.fence)
        for y in range(1, 5):
            s.set(x, y, 10, P.post() if P.timber else (P.corner or P.base))
    s.door(6, 6, z1, "south")
    # jardim
    cb.path(s, 6, 9, 6, 12)
    cb.flower_bed(s, 1, 10, 4, 11)
    cb.flower_bed(s, 8, 10, 11, 11)
    cb.lamp_post(s, 5, 12, h=2)
    cb.bush(s, 0, 1, 9)
    cb.bush(s, 12, 1, 9)
    label(s, 7, 3, 9, "south", "house")
    return save(s, style, "house_large_1")


# =================================================================================================
#  Casa dos Construtores
# =================================================================================================
def builders_hall(style):
    s = SB(style, "builders_hall", 13, 22, 13)
    P = s.P
    cb.yard(s)
    x0, z0, x1, z1 = 1, 1, 11, 7
    top = cb.shell(s, x0, z0, x1, z1, floors=1, fh=5)
    for x in (5, 6, 7):
        for y in (1, 2, 3):
            s.set(x, y, z1, AIR)
    cb.windows(s, x0, z0, x1, z1, 2, skip=[(6, z1)])
    cb.roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[-1])
    s.set(2, 1, 2, "minecraft:crafting_table")
    s.set(3, 1, 2, "minecraft:crafting_table")
    s.set(4, 1, 2, st("stonecutter", facing="south"))
    s.set(8, 1, 2, "minecraft:smithing_table")
    s.set(9, 1, 2, st("anvil", facing="east"))
    s.set(10, 1, 2, st("grindstone", face="floor", facing="south"))
    s.set(10, 1, 4, st("lectern", facing="west", has_book="false", powered="false"))
    s.set(2, 1, 5, "minecraft:cartography_table")
    s.barrel(2, 1, 4)
    s.barrel(2, 1, 6)
    s.barrel(2, 2, 6)
    s.chest(10, 1, 6, "house", "west")
    cb.table(s, 6, 1, 4, ("west", "east"))
    s.set(6, 2, 4, st("white_carpet"))
    cb.hang(s, 4, 4, 4)
    cb.hang(s, 8, 4, 4)
    # pátio com material empilhado, andaime e guindaste
    for z in (9, 10):
        for x in (1, 2, 3):
            s.set(x, 1, z, log(P.tree_log, "x"))
        s.set(2, 2, z, log(P.tree_log, "x"))
    for x in (9, 10, 11):
        for z in (9, 10):
            s.set(x, 1, z, P.base2)
    s.set(10, 2, 9, P.base2)
    s.set(9, 2, 10, P.w("planks"))
    for y in range(1, 5):
        s.set(1, y, 12, st("scaffolding", distance=0, bottom="false", waterlogged="false"))
    for y in range(1, 7):
        s.set(12, y, 8, P.fence)
    for x in (9, 10, 11):
        s.set(x, 6, 8, P.fence)
    s.set(9, 5, 8, chain())
    s.set(9, 4, 8, chain())
    s.set(9, 3, 8, P.base2)
    s.set(12, 7, 8, lantern())
    cb.path(s, 6, 8, 6, 12)
    cb.path(s, 5, 8, 7, 8)
    label(s, 4, 3, 8, "south", "builders_hall")
    cb.lamp_post(s, 8, 12, h=2)
    return save(s, style, "builders_hall")


# =================================================================================================
#  Mercado
# =================================================================================================
GOODS = [
    ["minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block"],
    [potted("red_tulip"), potted("fern"), potted("blue_orchid")],
    [candle(2, "yellow"), candle(3, "white"), candle(1, "red")],
    [st("decorated_pot", facing="south", cracked="false", waterlogged="false"), "minecraft:honey_block",
     "minecraft:dried_kelp_block"],
]


def stall(s, x0, z0, x1, z1, front, colors, goods):
    P = s.P
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in (1, 2, 3):
            s.set(x, y, z, P.fence)
    cb.awning(s, x0, x1, z0, z1, 4, colors, along="x" if front in ("north", "south") else "z")
    if front in ("north", "south"):
        zc = z1 if front == "south" else z0
        zb = z0 if front == "south" else z1
        for x in range(x0 + 1, x1):
            s.set(x, 1, zc, P.w("planks") if not P.flat else "minecraft:cut_sandstone")
            s.set(x, 2, zc, s.rnd.choice(goods))
            s.barrel(x, 1, zb)
    else:
        xc = x1 if front == "east" else x0
        xb = x0 if front == "east" else x1
        for z in range(z0 + 1, z1):
            s.set(xc, 1, z, P.w("planks") if not P.flat else "minecraft:cut_sandstone")
            s.set(xc, 2, z, s.rnd.choice(goods))
            s.barrel(xb, 1, z)
    s.set((x0 + x1) // 2, 3, (z0 + z1) // 2, lantern(True))


def market(style):
    s = SB(style, "market", 13, 14, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12)
    for x, z in cb.perim(0, 0, 12, 12):
        s.set(x, 0, z, P.curb)
    aw = P.awnings
    stall(s, 1, 1, 5, 3, "south", aw[0], GOODS[0])
    stall(s, 7, 1, 11, 3, "south", aw[1], GOODS[1])
    stall(s, 1, 6, 3, 10, "east", aw[2], GOODS[2])
    stall(s, 9, 6, 11, 10, "west", aw[3], GOODS[3])
    # fonte/poço no meio
    for x, z in cb.perim(5, 6, 7, 8):
        s.set(x, 1, z, slab(P.stone_slab))
    s.set(6, 0, 7, cb.water())
    s.set(6, 1, 7, AIR)
    cb.lamp_double(s, 6, 11, axis="x", h=3)
    s.set(5, 1, 11, "minecraft:hay_block")
    s.set(7, 1, 11, st("composter", level=4))
    s.standing_sign(4, 1, 12, "south", ["market"], color="yellow")
    return save(s, style, "market")


# =================================================================================================
#  Quartel
# =================================================================================================
def barracks(style):
    s = SB(style, "barracks", 13, 22, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12, mix=[(P.path, 60), (P.pave_main, 40)])
    x0, z0, x1, z1 = 1, 1, 11, 6
    top = cb.shell(s, x0, z0, x1, z1, floors=1, fh=5, wall=P.civic, base=P.base2, posts=False,
                   corner=P.trim if not P.timber else None)
    cb.entry(s, 6, 1, z1, "south")
    cb.windows(s, x0, z0, x1, z1, 2, skip=[(6, z1)])
    if P.flat:
        cb.flat_roof(s, x0, z0, x1, z1, top)
    else:
        cb.roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roof_civic)
    for x in (2, 4, 8, 10):
        s.bed(x, 1, 3, "red" if style == "kingdom" else ("orange" if style == "desert" else "light_blue"), "north")
    s.chest(3, 1, 5, "house", "north")
    s.chest(9, 1, 5, "house", "north")
    s.set(6, 1, 2, st("anvil", facing="east"))
    s.barrel(5, 1, 2)
    s.barrel(7, 1, 2)
    s.wall_banner(6, 3, 2, "south", BANNER[style])
    cb.hang(s, 4, 4, 4)
    cb.hang(s, 8, 4, 4)
    # pátio de treino: bonecos de palha e alvos
    for x in (2, 4):
        s.set(x, 1, 9, P.fence)
        s.set(x, 2, 9, "minecraft:hay_block")
        s.set(x, 3, 9, st("carved_pumpkin", facing="south"))
    for z in (9, 11):
        s.set(11, 1, z, "minecraft:hay_block")
        s.set(11, 2, z, "minecraft:target")
    for x in range(7, 11):
        s.set(x, 0, 10, P.pave_accent)
    s.set(1, 1, 12, st("grindstone", face="floor", facing="east"))
    s.barrel(2, 1, 12)
    cb.lamp_post(s, 6, 11, h=3)
    cb.flag_pole(s, 0, 7, 1, 5, BANNER[style], "south")
    label(s, 4, 3, 7, "south", "barracks")
    return save(s, style, "barracks")


# =================================================================================================
#  Torre de Vigia (Reino e Nórdico)
# =================================================================================================
def watchtower(style):
    s = SB(style, "watchtower", 9, 22, 9)
    P = s.P
    cb.yard(s)
    nordic = style == "nordic"
    body = log("minecraft:spruce_log") if nordic else P.base2
    for y in range(0, 11):
        for x, z in cb.perim(2, 2, 6, 6):
            corner = x in (2, 6) and z in (2, 6)
            if nordic:
                s.set(x, y, z, log("minecraft:stripped_spruce_log") if corner else P.w("planks"))
            else:
                s.set(x, y, z, P.trim if corner and y % 4 == 0 else body)
    fill(s, 3, 0, 3, 5, 0, 5, P.floor)
    s.door(4, 1, 6, "south")
    for y in range(1, 12):
        s.set(4, y, 3, ladder("south"))
    for y in (5, 8):
        for x, z in ((4, 6), (2, 4), (6, 4)):
            s.set(x, y, z, P.window if not nordic else AIR)
    # plataforma
    fill(s, 1, 11, 1, 7, 11, 7, P.w("planks") if not P.flat else P.civic)
    s.set(4, 11, 3, ladder("south"))
    for x, z in cb.perim(1, 1, 7, 7):
        s.set(x, 12, z, P.fence)
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        for y in (13, 14):
            s.set(x, y, z, P.fence)
    cb.roof(s, 1, 1, 7, 7, 15, axis="x", mat=P.roof_civic, overhang=0)
    for x in range(1, 8):
        s.set(x, 15, 4, log(P.tree_log if not nordic else "minecraft:spruce_log", "x"))
    s.set(4, 14, 4, st("bell", attachment="ceiling", facing="north", powered="false"))
    s.set(2, 12, 2, lantern())
    s.set(6, 12, 6, lantern())
    s.wall_banner(4, 9, 7, "south", BANNER[style])
    cb.path(s, 4, 7, 4, 8)
    return save(s, style, "watchtower")


# =================================================================================================
#  Taverna (Reino e Nórdico)
# =================================================================================================
def tavern(style):
    s = SB(style, "tavern", 13, 26, 13)
    P = s.P
    r = s.rnd
    cb.yard(s)
    x0, z0, x1, z1 = 1, 1, 11, 8
    top = cb.shell(s, x0, z0, x1, z1, floors=2, fh=5)
    cb.entry(s, 6, 1, z1, "south")
    cb.windows(s, x0, z0, x1, z1, 2, skip=[(6, z1)])
    cb.windows(s, x0, z0, x1, z1, 7)
    ridge = cb.roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[0])
    s.set(10, 1, 2, campfire(facing="south"))
    cb.chimney(s, 10, 2, 2, max(ridge - 1, top + 2))
    # balcão e barris (chope)
    for x in range(2, 6):
        s.set(x, 1, 4, log(f"minecraft:stripped_{P.wood}_log", "x"))
        s.set(x, 2, 4, P.w("slab", type="bottom", waterlogged="false"))
        s.set(x, 1, 2, st("barrel", facing="south", open="false"))
        s.set(x, 2, 2, st("barrel", facing="south", open="false"))
    s.set(3, 3, 2, lantern())
    cb.table(s, 8, 1, 4, ("west", "east"))
    cb.table(s, 8, 1, 6, ("west", "east"))
    cb.table(s, 3, 1, 6, ("north", "east"))
    cb.hang(s, 4, 4, 5)
    cb.hang(s, 8, 4, 5)
    for y in range(1, 6):
        s.set(2, y, 7, ladder("north"))
    # quartos no andar de cima
    for x in (3, 5, 8, 10):
        s.bed(x, 6, 3, r.choice(cb.BED_COLORS), "north")
    for x in (4, 9):
        s.set(x, 6, 2, st("barrel", facing="up", open="false"))
    cb.rug(s, 3, 5, 10, 6, 6, P.carpet, border=P.carpet2)
    cb.hang(s, 6, 9, 5)
    # fachada
    s.wall_banner(4, 4, 9, "south", BANNER[style])
    label(s, 8, 3, 9, "south", "tavern")
    cb.path(s, 6, 9, 6, 12)
    cb.bench(s, 2, 11, "south", n=2)
    cb.bench(s, 9, 11, "south", n=2)
    s.barrel(1, 1, 10)
    s.barrel(11, 1, 10)
    cb.lamp_post(s, 5, 12, h=2)
    return save(s, style, "tavern")


# =================================================================================================
#  Templo (altar de Ascensão PÚBLICO completo no centro)
# =================================================================================================
def temple(style):
    s = SB(style, "temple", 13, 18, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12, mix=[(P.tile, 70), (P.pave_accent, 30)])
    for x, z, d in cb.disk(6, 6, 5.2):
        if 0 <= x < 13 and 0 <= z < 13:
            s.set(x, 0, z, P.trim if d > 4.6 else P.tile)
    cb.ascension_altar(s, 6, 1, 6)
    cols = [(0, 0), (12, 0), (0, 12), (12, 12), (0, 6), (12, 6), (6, 0), (3, 0), (9, 0), (0, 3), (12, 3), (0, 9), (12, 9)]
    nordic = style == "nordic"
    for x, z in cols:
        for y in range(1, 7):
            if nordic:
                s.set(x, y, z, log("minecraft:spruce_log"))
            elif style == "desert":
                s.set(x, y, z, "minecraft:chiseled_sandstone" if y in (1, 6) else "minecraft:cut_sandstone")
            else:
                s.set(x, y, z, P.trim if y in (1, 6) else "minecraft:quartz_pillar[axis=y]")
    # entablamento (aro de lajes) e lanternas
    for x, z in cb.perim(0, 0, 12, 12):
        s.set(x, 7, z, P.smooth_slab if not nordic else P.w("slab", type="bottom", waterlogged="false"))
    for x, z in cols:
        s.set(x, 7, z, P.trim if not nordic else log("minecraft:stripped_spruce_log"))
        s.set(x, 8, z, lantern(soul=True) if style != "desert" else lantern())
    # braseiros e estandartes no fundo
    for x, z in ((1, 1), (11, 1), (1, 11), (11, 11)):
        s.set(x, 1, z, campfire(soul=style == "nordic", facing="south"))
    for x in (3, 9):
        s.wall_banner(x, 4, 1, "south", BANNER[style])
    s.standing_sign(4, 1, 12, "south", ["temple", "temple2"], color="white")
    return save(s, style, "temple")


# =================================================================================================
#  Prefeitura (com torre do sino)
# =================================================================================================
def town_hall(style):
    s = SB(style, "town_hall", 13, 34, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12)
    x0, z0, x1, z1 = 1, 1, 11, 8
    top = cb.shell(s, x0, z0, x1, z1, floors=2, fh=5, wall=P.civic if not P.timber else None, posts=P.timber)
    s.double_door(5, 1, z1, "south", "east")
    s.set(4, 3, z1 + 1, wall_torch("south"))
    s.set(7, 3, z1 + 1, wall_torch("south"))
    cb.windows(s, x0, z0, x1, z1, 2, skip=[(5, z1), (6, z1)])
    cb.windows(s, x0, z0, x1, z1, 7)
    if P.flat:
        cb.flat_roof(s, x0, z0, x1, z1, top)
    else:
        cb.roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roof_civic)
    # torre do sino no meio do telhado
    tb = top + 1
    for y in range(top, tb + 8):
        for x, z in cb.perim(5, 3, 7, 5):
            belfry = tb + 4 <= y <= tb + 6 and not (x in (5, 7) and z in (3, 5))
            s.set(x, y, z, AIR if belfry else (P.civic if not P.timber else P.post()))
        if y < tb + 4:
            s.set(6, y, 4, AIR)
    s.set(6, tb + 6, 4, st("bell", attachment="ceiling", facing="north", powered="false"))
    fill(s, 5, tb + 7, 3, 7, tb + 7, 5, P.civic)
    if style == "desert":
        cb.dome(s, 6, 4, tb + 8, 1.6, P.dome)
    else:
        cb.spire(s, 6, 4, tb + 8, 2.2, P.roof_civic[2] if P.roof_civic else P.civic)
    # salão do conselho
    for x in range(3, 10):
        s.set(x, 1, 4, P.w("fence", P.wood2))
        s.set(x, 2, 4, P.w("pressure_plate", P.wood, powered="false"))
        s.set(x, 1, 3, stairs(mc(f"{P.wood}_stairs"), "north"))
        s.set(x, 1, 5, stairs(mc(f"{P.wood}_stairs"), "south"))
    s.set(2, 1, 4, stairs(mc(f"{P.wood}_stairs"), "west"))
    s.wall_banner(4, 3, 2, "south", BANNER[style])
    s.wall_banner(8, 3, 2, "south", BANNER[style])
    s.set(10, 1, 2, st("lectern", facing="west", has_book="false", powered="false"))
    cb.hang(s, 4, 4, 6)
    cb.hang(s, 8, 4, 6)
    for y in range(1, 6):
        s.set(10, y, 7, ladder("north"))
    # gabinete em cima
    cb.shelves(s, 2, 2, 5, 2, 6, 8)
    s.set(8, 6, 2, "minecraft:cartography_table")
    s.chest(9, 6, 2, "house", "south")
    cb.table(s, 6, 6, 5, ("north",))
    cb.rug(s, 3, 4, 8, 6, 6, P.carpet, border=P.carpet2)
    cb.hang(s, 6, 9, 4)
    s.wall_banner(3, 5, 9, "south", BANNER[style])
    s.wall_banner(8, 5, 9, "south", BANNER[style])
    label(s, 9, 3, 9, "south", "town_hall")
    for x, z in ((1, 11), (11, 11)):
        cb.lamp_post(s, x, z, h=3)
    return save(s, style, "town_hall")


# =================================================================================================
#  Castelo (Reino, nível 5)
# =================================================================================================
def castle(style):
    s = SB(style, "castle", 13, 40, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12, mix=P.cw)
    x0, z0, x1, z1 = 1, 1, 11, 11
    top = cb.shell(s, x0, z0, x1, z1, floors=3, fh=5, wall=P.civic, base=P.base2, posts=False, corner=P.trim)
    s.double_door(5, 1, z1, "south", "east")
    s.set(4, 3, z1, stairs(P.stone_stairs, "east", half="top"))
    s.set(5, 3, z1, P.trim)
    s.set(6, 3, z1, P.trim)
    s.set(7, 3, z1, stairs(P.stone_stairs, "west", half="top"))
    for f in range(3):
        cb.windows(s, x0, z0, x1, z1, 2 + f * 5, sides="new" if f == 0 else "nsew", h=2)
    # ameias no topo do torreão
    for x, z in cb.perim(x0, z0, x1, z1):
        if (x + z) % 2 == 0:
            s.set(x, top + 1, z, P.civic)
    # torreões nos cantos
    for cx, cz in ((1, 1), (11, 1), (1, 11), (11, 11)):
        for y in range(0, top + 5):
            for x, z in cb.perim(cx - 1, cz - 1, cx + 1, cz + 1):
                s.set(x, y, z, P.civic if y % 6 else P.trim)
        cb.spire(s, cx, cz, top + 5, 1.8, "minecraft:deepslate_tiles")
        s.set(cx, top + 3, cz - 1 if cz == 1 else cz + 1, P.window)
    # sala do trono
    for z in range(3, 11):
        s.set(5, 0, z, "minecraft:red_wool")
        s.set(6, 0, z, "minecraft:red_wool")
    for z in range(3, 11):
        s.set(5, 1, z, "minecraft:red_carpet")
        s.set(6, 1, z, "minecraft:red_carpet")
    s.set(5, 1, 2, "minecraft:gold_block")
    s.set(6, 1, 2, stairs("minecraft:quartz_stairs", "north"))
    s.set(7, 1, 2, "minecraft:gold_block")
    for x in (3, 9):
        s.wall_banner(x, 3, 2, "south", BANNER[style])
    for x in (3, 9):
        for z in (4, 8):
            for y in range(1, 5):
                s.set(x, y, z, "minecraft:quartz_pillar[axis=y]")
    cb.hang(s, 6, 4, 6)
    cb.hang(s, 4, 4, 9)
    cb.hang(s, 8, 4, 9)
    # escadas de mão por todos os andares até o telhado
    for y in range(1, top + 1):
        s.set(10, y, 9, ladder("west"))
    # arsenal (2º andar)
    for x in (3, 4):
        s.set(x, 6, 2, st("anvil", facing="east"))
    s.set(5, 6, 2, "minecraft:smithing_table")
    for x in range(6, 10):
        s.barrel(x, 6, 2)
    s.chest(2, 6, 6, "house", "east")
    cb.hang(s, 6, 9, 6)
    # quarto do Senhor (3º andar)
    s.bed(4, 11, 3, "blue", "north")
    s.bed(5, 11, 3, "blue", "north")
    cb.shelves(s, 7, 2, 9, 2, 11, 13)
    s.chest(2, 11, 4, "house", "east")
    cb.rug(s, 3, 5, 8, 9, 11, "blue", border="yellow")
    s.set(8, 11, 6, st("lectern", facing="west", has_book="false", powered="false"))
    cb.hang(s, 6, 14, 6)
    # estandartes na fachada
    for x in (3, 9):
        s.wall_banner(x, 8, 12, "south", BANNER[style])
    s.wall_banner(6, 10, 12, "south", BANNER[style])
    label(s, 9, 2, 12, "south", "castle")
    return save(s, style, "castle")


# =================================================================================================
#  Maravilhas do Desértico
# =================================================================================================
def obelisk(style):
    s = SB(style, "obelisk", 13, 22, 13)
    P = s.P
    cb.paving(s, 0, 0, 12, 12)
    for x, z in cb.perim(0, 0, 12, 12):
        s.set(x, 0, z, P.curb)
    for x, z, d in cb.disk(6, 6, 4.2):
        s.set(x, 0, z, "minecraft:chiseled_sandstone" if d > 3.6 else "minecraft:smooth_sandstone")
    # espelhos d'água dos dois lados
    for x0 in (1, 10):
        for x in (x0, x0 + 1):
            for z in range(4, 9):
                s.set(x, 0, z, cb.water())
    # pedestal
    fill(s, 4, 1, 4, 8, 1, 8, "minecraft:cut_sandstone")
    for x, z in ((4, 4), (8, 4), (4, 8), (8, 8)):
        s.set(x, 1, z, "minecraft:chiseled_sandstone")
        s.set(x, 2, z, st("sandstone_wall"))
        s.set(x, 3, z, lantern())
    # fuste 3x3 com faixas de hieróglifos
    for y in range(2, 14):
        for x in range(5, 8):
            for z in range(5, 8):
                edge_mid = (x == 6) != (z == 6)
                if y % 3 == 0:
                    b = "minecraft:orange_terracotta" if (x + z) % 2 else "minecraft:smooth_sandstone"
                elif edge_mid and y % 3 == 1:
                    b = "minecraft:chiseled_sandstone"
                else:
                    b = "minecraft:cut_sandstone"
                s.set(x, y, z, b)
    # pirâmide dourada no topo
    for x, z, f in ((6, 5, "south"), (6, 7, "north"), (5, 6, "east"), (7, 6, "west")):
        s.set(x, 14, z, stairs("minecraft:sandstone_stairs", f))
    for x, z in ((5, 5), (7, 5), (5, 7), (7, 7)):
        s.set(x, 14, z, slab("minecraft:smooth_sandstone_slab"))
    s.set(6, 14, 6, "minecraft:gold_block")
    s.set(6, 15, 6, "minecraft:gold_block")
    s.set(6, 16, 6, st("lightning_rod", facing="up", powered="false", waterlogged="false"))
    for x, z in ((2, 2), (10, 2), (2, 10), (10, 10)):
        s.set(x, 0, z, "minecraft:sand")
        cb.tree(s, x, z, kind="palm", h=5)
    s.standing_sign(6, 1, 12, "south", ["obelisk", "obelisk2"], color="yellow", glow=True)
    return save(s, style, "obelisk")


def pyramid(style):
    s = SB(style, "pyramid", 13, 12, 13)
    for k in range(7):
        for x in range(k, 13 - k):
            for z in range(k, 13 - k):
                edge = x in (k, 12 - k) or z in (k, 12 - k)
                if k == 6:
                    b = "minecraft:gold_block"
                elif edge:
                    b = "minecraft:smooth_sandstone" if k % 2 else "minecraft:cut_sandstone"
                else:
                    b = "minecraft:sandstone"
                s.set(x, k, z, b)
    s.set(6, 7, 6, "minecraft:gold_block")
    # degraus na face da frente (sul), acima da entrada
    for k in range(3, 6):
        for x in range(5, 8):
            s.set(x, k, 12 - k, stairs("minecraft:sandstone_stairs", "north"))
    # entrada e câmara
    for z in range(9, 13):
        for y in (1, 2):
            s.set(6, y, z, AIR)
    for y in (1, 2):
        s.set(5, y, 11, "minecraft:chiseled_sandstone")
        s.set(7, y, 11, "minecraft:chiseled_sandstone")
    for x in (5, 6, 7):
        s.set(x, 3, 11, "minecraft:chiseled_sandstone")
    for x in range(4, 9):
        for z in range(4, 9):
            for y in (1, 2, 3):
                s.set(x, y, z, AIR)
    for x, z in cb.perim(3, 3, 9, 9):
        for y in (1, 2, 3):
            b = ["minecraft:orange_terracotta", "minecraft:chiseled_sandstone", "minecraft:blue_terracotta"][(x + z + y) % 3]
            if (x, z) != (6, 9):
                s.set(x, y, z, b)
    s.set(6, 1, 9, AIR)
    s.set(6, 2, 9, AIR)
    s.set(6, 1, 5, "minecraft:gold_block")
    s.set(6, 2, 5, st("decorated_pot", facing="south", cracked="false", waterlogged="false"))
    for x, z in ((4, 4), (8, 4), (4, 8), (8, 8)):
        s.set(x, 1, z, st("decorated_pot", facing="south", cracked="false", waterlogged="false"))
    s.set(6, 3, 6, lantern(True, soul=False))
    s.set(5, 1, 7, candle(3, "orange"))
    s.set(7, 1, 7, candle(3, "orange"))
    s.set(4, 1, 12, campfire(facing="south"))
    s.set(8, 1, 12, campfire(facing="south"))
    return save(s, style, "pyramid")


def sphinx(style):
    s = SB(style, "sphinx", 13, 16, 13)
    fill(s, 0, 0, 0, 12, 0, 12, "minecraft:smooth_sandstone")
    for x, z in cb.perim(0, 0, 12, 12):
        s.set(x, 0, z, "minecraft:cut_sandstone")
    # corpo deitado (cauda ao norte, cabeça ao sul)
    fill(s, 4, 1, 1, 8, 4, 8, "minecraft:sandstone")
    fill(s, 5, 5, 2, 7, 5, 7, "minecraft:smooth_sandstone")
    fill(s, 4, 5, 1, 8, 5, 3, "minecraft:sandstone")
    for z in (1, 2):
        s.set(9, 1, z, stairs("minecraft:sandstone_stairs", "west"))
        s.set(3, 1, z, stairs("minecraft:sandstone_stairs", "east"))
    s.set(9, 2, 1, "minecraft:sandstone")
    s.set(10, 1, 1, "minecraft:sandstone_wall")
    # patas
    for x in (4, 5, 7, 8):
        for z in (9, 10, 11):
            s.set(x, 1, z, "minecraft:cut_sandstone")
        s.set(x, 1, 12, stairs("minecraft:sandstone_stairs", "north"))
    # peito e cabeça
    fill(s, 4, 2, 9, 8, 5, 9, "minecraft:sandstone")
    fill(s, 4, 6, 8, 8, 10, 11, "minecraft:sandstone")
    face = {
        (5, 8): "minecraft:black_terracotta", (7, 8): "minecraft:black_terracotta",
        (6, 7): "minecraft:smooth_sandstone", (6, 6): "minecraft:orange_terracotta",
    }
    for (x, y), b in face.items():
        s.set(x, y, 11, b)
    s.set(6, 7, 12, stairs("minecraft:sandstone_stairs", "north"))
    # toucado (nemes) listrado
    for y in range(5, 11):
        for x in (3, 9):
            for z in (8, 9, 10):
                if y >= 6 or z == 10:
                    s.set(x, y, z, "minecraft:blue_terracotta" if y % 2 else "minecraft:yellow_terracotta")
    for x in range(3, 10):
        for z in range(7, 12):
            if not (x in (3, 9) and z == 11):
                s.set(x, 11, z, "minecraft:blue_terracotta" if x % 2 else "minecraft:yellow_terracotta")
    s.set(6, 12, 11, "minecraft:gold_block")
    s.set(6, 4, 11, "minecraft:gold_block")
    s.set(6, 5, 11, "minecraft:gold_block")
    for x in (1, 11):
        s.set(x, 1, 11, "minecraft:chiseled_sandstone")
        s.set(x, 2, 11, campfire(facing="south"))
    return save(s, style, "sphinx")


# =================================================================================================
#  Muralha (trecho 9x3, portão 7x3, torre 5x5) — simétricos em z
# =================================================================================================
def wall_segment(style):
    s = SB(style, "wall_segment", 9, 14, 3)
    P = s.P
    if style == "kingdom":
        for x in range(9):
            for z in range(3):
                for y in range(0, 7):
                    s.set(x, y, z, s.pick(P.cw) if y > 0 else P.base2)
                if z in (0, 2):
                    s.set(x, 7, z, P.cw_top if x % 2 == 0 else slab(P.stone_slab))
        s.set(4, 7, 1, lantern())
    elif style == "desert":
        for x in range(9):
            for z in range(3):
                for y in range(0, 4):
                    s.set(x, y, z, s.pick(P.cw) if y > 0 else "minecraft:cut_sandstone")
                if z == 1:
                    s.set(x, 4, z, "minecraft:cut_sandstone" if x % 2 == 0 else slab("minecraft:cut_sandstone_slab"))
            s.set(x, 2, 0, "minecraft:chiseled_sandstone" if x == 4 else s.get(x, 2, 0))
            s.set(x, 2, 2, "minecraft:chiseled_sandstone" if x == 4 else s.get(x, 2, 2))
    else:
        for x in range(9):
            for y in range(0, 6):
                s.set(x, y, 1, log("minecraft:spruce_log"))
            s.set(x, 6, 1, P.fence if x % 2 == 0 else AIR)
            for z in (0, 2):
                s.set(x, 0, z, "minecraft:cobblestone")
        for x in (0, 4, 8):
            for z, f in ((0, "south"), (2, "north")):
                s.set(x, 1, z, stairs("minecraft:spruce_stairs", f))
                s.set(x, 2, z, log("minecraft:stripped_spruce_log", "z"))
        s.set(4, 3, 0, wall_torch("north"))
        s.set(4, 3, 2, wall_torch("south"))
    return save(s, style, "wall_segment")


def wall_gate(style):
    gh = GATE_HEIGHT[style]
    s = SB(style, "wall_gate", 7, 16, 3)
    P = s.P
    hole = {(x, y) for x in (2, 3, 4) for y in range(1, gh + 1)}
    if style == "kingdom":
        for x in range(7):
            for z in range(3):
                for y in range(0, 9):
                    if (x, y) in hole:
                        continue
                    s.set(x, y, z, P.trim if x in (0, 6) and y % 3 == 0 else s.pick(P.cw) if y > 0 else P.base2)
                if z in (0, 2):
                    s.set(x, 9, z, P.cw_top if x % 2 == 0 else slab(P.stone_slab))
        for z in (0, 2):
            s.set(2, gh, z, stairs(P.stone_stairs, "east", half="top"))
            s.set(4, gh, z, stairs(P.stone_stairs, "west", half="top"))
        for z in (0, 2):
            s.set(3, gh + 1, z, "minecraft:chiseled_stone_bricks")
        s.set(1, 9, 1, lantern())
        s.set(5, 9, 1, lantern())
        s.set(3, 9, 1, "minecraft:gold_block")
    elif style == "desert":
        for x in range(7):
            for z in range(3):
                for y in range(0, 7):
                    if (x, y) in hole:
                        continue
                    if x in (0, 1, 5, 6):
                        s.set(x, y, z, "minecraft:cut_sandstone" if y % 2 else "minecraft:smooth_sandstone")
                    else:
                        s.set(x, y, z, "minecraft:orange_terracotta" if y == gh + 1 else "minecraft:smooth_sandstone")
        for z in (0, 2):
            s.set(3, gh + 2, z, "minecraft:chiseled_sandstone")
        for x in (0, 6):
            for z in range(3):
                s.set(x, 7, z, "minecraft:chiseled_sandstone")
            s.set(x, 8, 1, lantern())
    else:
        for x in (0, 1, 5, 6):
            for z in range(3):
                for y in range(0, 7):
                    s.set(x, y, z, log("minecraft:spruce_log") if x in (0, 6) or z == 1 else "minecraft:spruce_planks")
                s.set(x, 7, z, P.fence if (x + z) % 2 == 0 else AIR)
        for x in (2, 3, 4):
            for z in range(3):
                s.set(x, gh + 1, z, log("minecraft:stripped_spruce_log", "x"))
                s.set(x, gh + 2, z, log("minecraft:spruce_log", "x") if z == 1 else "minecraft:spruce_planks")
                for y in range(gh + 3, 7):
                    s.set(x, y, z, "minecraft:spruce_planks")
            s.set(x, 7, 1, P.fence if x % 2 == 0 else AIR)
        s.set(0, 7, 1, campfire(facing="south"))
        s.set(6, 7, 1, campfire(facing="south"))
    # tochas no vão (presas aos pilares, não atrapalham a passagem nem a grade em z=1)
    for z in (0, 2):
        s.set(2, gh - 1 if gh > 2 else 2, z, wall_torch("east"))
        s.set(4, gh - 1 if gh > 2 else 2, z, wall_torch("west"))
    return save(s, style, "wall_gate")


def wall_tower(style):
    s = SB(style, "wall_tower", 5, 18, 5)
    P = s.P
    if style == "kingdom":
        for y in range(0, 10):
            for x in range(5):
                for z in range(5):
                    s.set(x, y, z, P.trim if (x in (0, 4) and z in (0, 4) and y % 3 == 0) else s.pick(P.cw))
        for x, z in cb.perim(0, 0, 4, 4):
            if (x + z) % 2 == 0:
                s.set(x, 10, z, P.cw_top)
        s.set(2, 10, 2, lantern())
        for x, z, f in ((2, 0, None), (0, 2, None), (4, 2, None), (2, 4, None)):
            s.set(x, 6, z, P.window)
    elif style == "desert":
        for y in range(0, 6):
            for x in range(5):
                for z in range(5):
                    s.set(x, y, z, "minecraft:cut_sandstone" if y % 3 else "minecraft:chiseled_sandstone")
        for x, z in cb.perim(0, 0, 4, 4):
            s.set(x, 6, z, "minecraft:smooth_sandstone" if (x + z) % 2 == 0 else slab("minecraft:smooth_sandstone_slab"))
        cb.dome(s, 2, 2, 6, 1.4, P.dome or "minecraft:cyan_terracotta", finial=False)
        s.set(2, 9, 2, "minecraft:gold_block")
    else:
        for y in range(0, 7):
            for x in range(5):
                for z in range(5):
                    corner = x in (0, 4) and z in (0, 4)
                    s.set(x, y, z, log("minecraft:spruce_log") if corner else "minecraft:spruce_planks")
        for x, z in cb.perim(0, 0, 4, 4):
            s.set(x, 7, z, P.fence)
        s.set(2, 7, 2, campfire(facing="south"))
        s.set(2, 6, 2, "minecraft:cobblestone")
    return save(s, style, "wall_tower")


# =================================================================================================
#  Tudo
# =================================================================================================
def build_all():
    BUILT.clear()
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".nbt"):
            os.remove(os.path.join(OUT, f))
    for style in STYLES:
        plaza(style)
        houses_small(style)
        farm(style)
        house_large(style)
        builders_hall(style)
        market(style)
        barracks(style)
        temple(style)
        town_hall(style)
        wall_segment(style)
        wall_gate(style)
        wall_tower(style)
        if style != "desert":
            watchtower(style)
            tavern(style)
        if style == "kingdom":
            castle(style)
        if style == "desert":
            obelisk(style)
            pyramid(style)
            sphinx(style)
    verify()
    return dict(BUILT)


def expected_templates():
    out = []
    for style in STYLES:
        keys = ["plaza", "house_small", "house_small_1", "house_small_2", "farm", "house_large", "house_large_1",
                "builders_hall", "market", "barracks", "temple", "town_hall", "wall_segment", "wall_gate", "wall_tower"]
        if style != "desert":
            keys += ["watchtower", "tavern"]
        if style == "kingdom":
            keys += ["castle"]
        if style == "desert":
            keys += ["obelisk", "pyramid", "sphinx"]
        out += [f"{style}_{k}" for k in keys]
    return out


def verify():
    """Lê de volta e confere tamanhos e regras do Java (vão do portão livre, praça sem nada no coração...)."""
    errors = []
    vb, _ = cb.vanilla_ids()
    known = cb.blockstate_props()
    mod_blocks = {cb.TRAVEL_STONE, cb.ASCENSION_CORE, cb.ASCENSION_PEDESTAL, cb.RUNE_STONE}
    seen = set()
    for name in BUILT:
        root = cb.read_nbt(os.path.join(OUT, name + ".nbt"))
        for p in root["palette"]:
            n = p["Name"]
            props = p.get("Properties", {})
            if n.startswith("minecraft:") and vb and n not in vb:
                errors.append(f"{name}: bloco inexistente {n}")
            if n.startswith(NS + ":") and n not in mod_blocks:
                errors.append(f"{name}: bloco do mod desconhecido {n}")
            key = (n, tuple(sorted(props.items())))
            if props and key not in seen:
                seen.add(key)
                errors.extend(f"{name}: {e}" for e in cb.check_props(n, props, known))
    for name, (size, n) in BUILT.items():
        style, piece = name.split("_", 1)
        sx, sy, sz = size
        if piece == "wall_segment" and (sx, sz) != (9, 3):
            errors.append(f"{name}: trecho tem de ser 9x3 ({sx}x{sz})")
        elif piece == "wall_gate" and (sx, sz) != (7, 3):
            errors.append(f"{name}: portão tem de ser 7x3 ({sx}x{sz})")
        elif piece == "wall_tower" and (sx, sz) != (5, 5):
            errors.append(f"{name}: torre tem de ser 5x5 ({sx}x{sz})")
        elif not piece.startswith("wall") and (sx > 13 or sz > 13):
            errors.append(f"{name}: maior que 13x13 ({sx}x{sz})")
        if n < 20:
            errors.append(f"{name}: só {n} blocos")
    for style in STYLES:
        path = os.path.join(OUT, f"{style}_wall_gate.nbt")
        root = cb.read_nbt(path)
        pal = root["palette"]
        gh = GATE_HEIGHT[style]
        for b in root["blocks"]:
            x, y, z = b["pos"]
            name = pal[b["state"]]["Name"]
            if x in (2, 3, 4) and 1 <= y <= gh and z == 1 and name != "minecraft:air":
                errors.append(f"{style}_wall_gate: bloco {name} no vão do portão ({x},{y},{z})")
        root = cb.read_nbt(os.path.join(OUT, f"{style}_plaza.nbt"))
        pal = root["palette"]
        names = set()
        for b in root["blocks"]:
            x, y, z = b["pos"]
            names.add(pal[b["state"]]["Name"])
            if (x, y, z) == (6, 1, 6):
                errors.append(f"{style}_plaza: algo no lugar do coração")
        if cb.TRAVEL_STONE not in names:
            errors.append(f"{style}_plaza: sem Pedra de Teleporte")
        root = cb.read_nbt(os.path.join(OUT, f"{style}_temple.nbt"))
        pal = root["palette"]
        cores = [b for b in root["blocks"] if pal[b["state"]]["Name"] == cb.ASCENSION_CORE]
        if len(cores) != 1 or "nbt" not in cores[0] or not cores[0]["nbt"].get("PublicAltar"):
            errors.append(f"{style}_temple: sem altar de Ascensão público")
    for e in errors:
        print("ERRO molde:", e)
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    b = build_all()
    for k, (size, n) in sorted(b.items()):
        print(f"{k:28s} {str(size):14s} {n:5d} blocos")
