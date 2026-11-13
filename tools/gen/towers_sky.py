"""
Ilhas Voadoras (modulo towers, v1.0) — "Legal! Ilhas voadoras tambem pff": tres moldes de ilha no ceu, com ruinas e baus.
Importado por tools/gen/towers.py (nao rodar sozinho).

  tower_sky_island_shrine  23 x 24 x 23   ilhota com um circulo de pilares quebrados e um altar com bau
  tower_sky_island_ruins   33 x 29 x 33   templo em ruinas (colunata, altar, cripta escondida embaixo do piso) e um portal
  tower_sky_island_keep    43 x 36 x 43   torre de vigia redonda meio desabada (cofre no andar de cima), casa caida e lago

Toda ilha tem no CENTRO o poco 3x3 da Corrente Ascendente (o Java — feature/towers/SkySites — levita quem entra na
coluna por baixo e pousa o jogador em "centro + 2"): por isso o aro do poco fica livre no meio de cada lado.
As posicoes do poco/altura do chao estao repetidas em SkySites.LAYOUTS: se mudar aqui, mude la.

Os ids comecam com "tower_" de proposito: o modulo ambience toca a musica de torre em toda estrutura cujo id contem
"tower" (as ilhas sao da mesma familia das Torres de Desafio). Os baus continuam em chests/sky_island*.
"""
import math
import os
import random

from structgen import Structure, Byte
from towers_struct import AIR, NS, TEMPLATE_DIR, bake_shapes, island, meadow, tree, well, _pick

BRICKS = [("minecraft:stone_bricks", 5), ("minecraft:mossy_stone_bricks", 3), ("minecraft:cracked_stone_bricks", 2)]
GRASS = "minecraft:grass_block[snowy=false]"
LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]"
QP = "minecraft:quartz_pillar[axis=y]"


def chest(s, x, y, z, loot, facing):
    s.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]", nbt={"LootTable": f"{NS}:chests/{loot}"})


def mob(s, x, y, z, entity):
    s.entity(x + 0.5, y, z + 0.5, f"{NS}:{entity}", {"PersistenceRequired": Byte(1)})


def column(s, x, y, z, h, full=5):
    """Coluna de quartzo; inteira ganha capitel, quebrada termina no toco."""
    for i in range(h):
        s.set(x, y + i, z, QP)
    if h >= full:
        s.set(x, y + h, z, "minecraft:chiseled_quartz_block")


def clear_above(s, x0, z0, x1, z1, y0, y1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                s.set(x, y, z, AIR)


# =====================================================================================
#  1. Santuario pequeno
# =====================================================================================
def shrine():
    n, c, top = 23, 11, 10
    s = Structure(n, top + 14, n)
    rnd = random.Random("sky/shrine")
    cols = island(s, c, c, top, 9, 10, "sky/shrine", solid=[(c, c, 4.5)])
    well(s, c, c, top, 0, "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", LANTERN)
    # circulo de pilares quebrados
    for i, h in enumerate((5, 3, 5, 2, 4, 1)):
        ang = math.radians(30 + 60 * i)
        x, z = c + round(6 * math.cos(ang)), c + round(6 * math.sin(ang))
        if (x, z) in cols:
            s.set(x, top, z, _pick(rnd, BRICKS))
            column(s, x, top + 1, z, h)
    # altar ao norte, com o bau na frente
    for dx in (-1, 0, 1):
        s.set(c + dx, top, c - 4, _pick(rnd, BRICKS))
        s.set(c + dx, top, c - 3, _pick(rnd, BRICKS))
    s.set(c - 1, top + 1, c - 4, "minecraft:chiseled_stone_bricks")
    s.set(c + 1, top + 1, c - 4, "minecraft:chiseled_stone_bricks")
    s.set(c - 1, top + 2, c - 4, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    s.set(c + 1, top + 2, c - 4, "minecraft:candle[candles=2,lit=true,waterlogged=false]")
    chest(s, c, top + 1, c - 4, "sky_island", "south")
    # um pedaco de coluna caida e uma cerejeira
    for dx in range(3):
        if (c + 3 + dx, c + 5) in cols:
            s.set(c + 3 + dx, top + 1, c + 5, "minecraft:quartz_pillar[axis=x]")
    if (c - 5, c - 1) in cols:
        tree(s, c - 5, top + 1, c - 1, "minecraft:cherry_log", "minecraft:cherry_leaves", 4, seed=1)
    meadow(s, cols, top, "sky/shrine/meadow", density=0.3, keep_clear=[(c, c, 3.5)])
    return s


# =====================================================================================
#  2. Templo em ruinas
# =====================================================================================
def ruins():
    n, c, top = 33, 16, 13
    s = Structure(n, top + 16, n)
    rnd = random.Random("sky/ruins")
    cols = island(s, c, c, top, 14, 13, "sky/ruins", solid=[(c, c, 4.5), (c, c - 8, 7.0), (c, c + 9, 3.5)])
    well(s, c, c, top, 0, "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", LANTERN)

    # ---------------- templo (norte): piso, colunata, parede dos fundos, altar
    for x in range(c - 4, c + 5):
        for z in range(c - 11, c - 3):
            s.set(x, top, z, _pick(rnd, BRICKS))
    heights = {(-4, -11): 5, (-4, -9): 5, (-4, -7): 3, (-4, -5): 5, (4, -11): 5, (4, -9): 2, (4, -7): 5, (4, -5): 1}
    for (dx, dz), h in heights.items():
        column(s, c + dx, top + 1, c + dz, h)
    # vigas entre colunas inteiras vizinhas
    slab = "minecraft:stone_brick_slab[type=bottom,waterlogged=false]"
    s.set(c - 4, top + 6, c - 10, slab)
    for x in range(c - 3, c + 4):
        if x != c + 2:
            s.set(x, top + 6, c - 11, slab)
    for i, x in enumerate(range(c - 3, c + 4)):
        for y in range(top + 1, top + 1 + (4, 4, 3, 1, 2, 4, 3)[i]):
            s.set(x, y, c - 11, _pick(rnd, BRICKS))
    s.set(c, top + 3, c - 11, AIR)                        # janelinha
    s.set(c, top + 1, c - 9, "minecraft:chiseled_stone_bricks")
    s.set(c, top + 2, c - 9, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    chest(s, c, top + 1, c - 8, "sky_island", "south")
    for dx in (-2, 2):
        s.set(c + dx, top + 1, c - 9, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")

    # ---------------- cripta escondida embaixo do piso (alcapao + escada de mao)
    for x in range(c - 2, c + 3):
        for z in range(c - 9, c - 4):
            for y in range(top - 5, top):
                wall_cell = x in (c - 2, c + 2) or z in (c - 9, c - 5) or y in (top - 5, top - 1)
                s.set(x, y, z, _pick(rnd, BRICKS) if wall_cell else AIR)
    for y in range(top - 4, top):
        s.set(c + 1, y, c - 6, "minecraft:ladder[facing=west,waterlogged=false]")
    s.set(c + 1, top, c - 6, "minecraft:spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")
    chest(s, c - 1, top - 4, c - 7, "sky_island", "east")
    s.set(c - 1, top - 4, c - 8, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    s.set(c, top - 4, c - 8, "minecraft:cobweb")

    # ---------------- portal quebrado ao sul
    for dx in (-2, 2):
        for y in range(top + 1, top + 5):
            s.set(c + dx, y, c + 9, _pick(rnd, BRICKS))
        s.set(c + dx, top, c + 9, _pick(rnd, BRICKS))
    for dx in (-2, -1, 0, 2):
        s.set(c + dx, top + 5, c + 9, "minecraft:chiseled_stone_bricks" if dx == 0 else _pick(rnd, BRICKS))
    s.set(c + 1, top + 1, c + 10, "minecraft:mossy_stone_bricks")  # o pedaco que caiu

    # ---------------- jardim
    for i, (dx, dz, log, leaves) in enumerate(((-9, 3, "minecraft:oak_log", "minecraft:oak_leaves"),
                                              (9, 4, "minecraft:birch_log", "minecraft:birch_leaves"))):
        if cols.get((c + dx, c + dz), 0) >= 3:
            tree(s, c + dx, top + 1, c + dz, log, leaves, 4 + i, seed=10 + i)
    meadow(s, cols, top, "sky/ruins/meadow", density=0.28, keep_clear=[(c, c, 3.5)])
    mob(s, c - 2, top + 1, c - 6, "harpy")
    mob(s, c + 6, top + 1, c + 2, "harpy")
    return s


# =====================================================================================
#  3. Torre de vigia desabada
# =====================================================================================
def keep():
    n, c, top = 43, 21, 16
    s = Structure(n, top + 20, n)
    rnd = random.Random("sky/keep")
    kx, kz = c, c - 11
    cols = island(s, c, c, top, 18, 16, "sky/keep",
                  solid=[(c, c, 4.5), (kx, kz, 7.5), (c - 8, c + 7, 5.0), (c + 9, c + 7, 4.5)])
    well(s, c, c, top, 0, "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", LANTERN)

    # ---------------- torre redonda (raio 5), parede de altura irregular; o arco norte fica inteiro (segura o andar)
    for x in range(kx - 6, kx + 7):
        for z in range(kz - 6, kz + 7):
            d = math.hypot(x - kx, z - kz)
            if d < 4.5:
                s.set(x, top, z, _pick(rnd, BRICKS))
                for y in range(top + 1, top + 14):
                    s.set(x, y, z, AIR)
            elif d < 5.6:
                ang = math.atan2(z - kz, x - kx)
                h = int(7.5 + 4.0 * math.sin(2 * ang + 1.3) + rnd.uniform(-1, 1))
                if z < kz - 1:
                    h = max(h, 9)
                h = max(3, min(12, h))
                s.set(x, top, z, _pick(rnd, BRICKS))
                for y in range(top + 1, top + 1 + h):
                    s.set(x, y, z, _pick(rnd, BRICKS))
    # porta ao sul, frestas a leste e oeste
    for y in (top + 1, top + 2):
        s.set(kx, y, kz + 5, AIR)
    s.set(kx, top + 3, kz + 5, "minecraft:chiseled_stone_bricks")
    for dx in (-5, 5):
        s.set(kx + dx, top + 3, kz, AIR)
        s.set(kx + dx, top + 4, kz, AIR)
    # andar de cima: so a metade norte sobrou
    for x in range(kx - 4, kx + 5):
        for z in range(kz - 4, kz + 1):
            if math.hypot(x - kx, z - kz) < 4.5 and not (z == kz and rnd.random() < 0.4):
                s.set(x, top + 6, z, "minecraft:spruce_planks")
    for y in range(top + 1, top + 7):
        s.set(kx, y, kz - 4, "minecraft:ladder[facing=south,waterlogged=false]")
    chest(s, kx - 2, top + 7, kz - 3, "sky_island_vault", "south")
    s.set(kx + 2, top + 7, kz - 3, "minecraft:barrel[facing=up,open=false]")
    s.set(kx + 3, top + 7, kz - 2, LANTERN)
    # terreo
    chest(s, kx + 3, top + 1, kz, "sky_island", "west")
    s.set(kx - 3, top + 1, kz - 1, "minecraft:bookshelf")
    s.set(kx - 3, top + 2, kz - 1, "minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,"
                                   "slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]")
    s.set(kx - 3, top + 1, kz + 1, "minecraft:cobweb")
    s.set(kx + 2, top + 1, kz + 3, LANTERN)
    # caminho do poco ate a porta
    for z in range(kz + 6, c - 2):
        s.set(c, top, z, _pick(rnd, BRICKS))

    # ---------------- casa caida (sudoeste)
    hx, hz = c - 11, c + 4
    for x in range(hx, hx + 6):
        for z in range(hz, hz + 6):
            if (x, z) not in cols:
                continue
            edge = x in (hx, hx + 5) or z in (hz, hz + 5)
            s.set(x, top, z, "minecraft:cobblestone" if edge else "minecraft:spruce_planks")
            if edge and not (x == hx + 5 and hz + 1 <= z <= hz + 3):
                h = (3, 2, 3, 1, 2, 0)[(x + 2 * z) % 6]
                for y in range(top + 1, top + 1 + h):
                    s.set(x, y, z, _pick(rnd, BRICKS))
    chest(s, hx + 1, top + 1, hz + 1, "sky_island", "east")
    s.set(hx + 1, top + 1, hz + 3, "minecraft:cobweb")
    s.set(hx + 3, top + 1, hz + 4, "minecraft:barrel[facing=up,open=false]")

    # ---------------- lago (sudeste): agua contida, 1 bloco de fundo
    px, pz = c + 9, c + 7
    for x in range(px - 3, px + 4):
        for z in range(pz - 3, pz + 4):
            if math.hypot(x - px, z - pz) < 2.6 and cols.get((x, z), 0) >= 3:
                s.set(x, top, z, "minecraft:water[level=0]")
                s.set(x, top - 1, z, "minecraft:clay" if rnd.random() < 0.4 else "minecraft:dirt")
    for (x, z) in ((px - 1, pz), (px + 1, pz + 1)):
        if s.get(x, top, z) == "minecraft:water[level=0]":
            s.set(x, top + 1, z, "minecraft:lily_pad")

    for i, (dx, dz, log, leaves) in enumerate(((10, -4, "minecraft:oak_log", "minecraft:oak_leaves"),
                                              (-12, -3, "minecraft:birch_log", "minecraft:birch_leaves"),
                                              (3, 12, "minecraft:cherry_log", "minecraft:cherry_leaves"))):
        if cols.get((c + dx, c + dz), 0) >= 3 and s.get(c + dx, top + 1, c + dz) is None:
            tree(s, c + dx, top + 1, c + dz, log, leaves, 4 + i % 2, seed=20 + i)
    meadow(s, cols, top, "sky/keep/meadow", density=0.26, keep_clear=[(c, c, 3.5)])
    mob(s, kx - 1, top + 1, kz + 1, "gargoyle")
    mob(s, kx + 1, top + 7, kz - 2, "gargoyle")
    mob(s, c - 7, top + 1, c + 2, "harpy")
    mob(s, c + 6, top + 1, c + 4, "harpy")
    return s


ISLANDS = (("tower_sky_island_shrine", shrine, 5), ("tower_sky_island_ruins", ruins, 3), ("tower_sky_island_keep", keep, 2))


def build_islands():
    return [(name, fn()) for name, fn, _ in ISLANDS]


def build_all(data_dir):
    for name, s in build_islands():
        bake_shapes(s)
        n = s.save(os.path.join(data_dir, f"structure/{TEMPLATE_DIR}/{name}.nbt"), keep_air=True)
        print(f"  estrutura {TEMPLATE_DIR}/{name}: {n} blocos, molde {s.size}")
