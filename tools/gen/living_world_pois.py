"""
"Mundo vivo" (só dados, sem Java): pequenos pontos de interesse e decorações baratas no mundo.

Parte 1 — 4 estruturas pequenas (jigsaw de 1 peça, como o Acampamento do Mago Errante):
  wayside_shrine  (Santuário à Beira da Estrada)
  wizard_hut      (Cabana do Feiticeiro)
  abandoned_camp  (Acampamento Abandonado)
  fallen_sword    (Espada Caída)
  -> .nbt em data/isekai_mahou/structure/living_world/, worldgen (structure, template_pool, structure_set com
     zonas de exclusão contra as cidades), loot tables, tags de bioma (pasta merge) e nomes (lang).

Parte 2 — decorações do mundo por biome modifier do NeoForge, só com features do vanilla:
  living_world_ruin_pillar, living_world_mossy_boulder, living_world_mana_outcrop, living_world_glow_mushrooms
  -> configured_feature, placed_feature, neoforge/biome_modifier, tags de bioma (merge) e a tag de blocos
     "living_world_built" (blocos de construção: perto deles a decoração não nasce — protege cidades e vilas).

Uso (na raiz do projeto):  py -X utf8 -I tools/gen/living_world_pois.py
Não mexe em nenhum arquivo de outro gerador.
"""
import json
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte  # noqa: E402

NS = "isekai_mahou"
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/living_world/data/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/living_world_pois")
AIR = "minecraft:cave_air"   # ar "explícito" (o structgen não salva minecraft:air): limpa grama/neve dentro da estrutura

written = []


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    written.append(os.path.relpath(path, ROOT))


# =====================================================================================
#  Ajudantes de blocos
# =====================================================================================
CARD = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def stairs(block, facing, half="bottom", shape="straight"):
    return f"minecraft:{block}[facing={facing},half={half},shape={shape},waterlogged=false]"


def slab(block, typ="bottom"):
    return f"minecraft:{block}[type={typ},waterlogged=false]"


def log(block, axis="y"):
    return f"minecraft:{block}[axis={axis}]"


def candle(color="", n=1, lit=True):
    name = f"minecraft:{color}_candle" if color else "minecraft:candle"
    return f"{name}[candles={n},lit={'true' if lit else 'false'},waterlogged=false]"


LANTERN = "minecraft:lantern[hanging=false,waterlogged=false]"
LANTERN_HANG = "minecraft:lantern[hanging=true,waterlogged=false]"
CHAIN_Y = "minecraft:chain[axis=y,waterlogged=false]"
COBWEB = "minecraft:cobweb"


def fill_empty(s, x0, y0, z0, x1, y1, z1, state=AIR, pred=None):
    """Preenche só as posições ainda não definidas."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                if s.get(x, y, z) is None and (pred is None or pred(x, y, z)):
                    s.set(x, y, z, state)


def _solid(state):
    if not state:
        return False
    return (any(k in state for k in ("bricks", "planks", "log", "stone", "wool", "deepslate", "cobble", "bookshelf"))
            and not any(k in state for k in ("slab", "stairs", "fence", "wall", "cave_air", "pane", "button")))


def connect(s, cells, block, kind):
    """Cercas/muros/vidraças com as conexões já calculadas (a peça de jigsaw não recalcula formas ao ser colocada).
    kind: 'fence' (true/false), 'wall' (none/low + up=true), 'pane' (true/false)."""
    cells = set(cells)
    for (x, y, z) in cells:
        props = []
        for d, (dx, dz) in CARD.items():
            n = (x + dx, y, z + dz)
            on = n in cells or _solid(s.get(*n))
            if kind == "wall":
                props.append(f"{d}={'low' if on else 'none'}")
            else:
                props.append(f"{d}={'true' if on else 'false'}")
        if kind == "wall":
            props.append("up=true")
        props.append("waterlogged=false")
        s.set(x, y, z, f"{block}[{','.join(props)}]")


def chest(s, x, y, z, table, facing="north"):
    s.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]",
          nbt={"LootTable": f"{NS}:{table}"})


def barrel(s, x, y, z, table=None, facing="up"):
    st = f"minecraft:barrel[facing={facing},open=false]"
    s.set(x, y, z, st, nbt={"LootTable": f"{NS}:{table}"} if table else None)


def aged_brick(rnd, moss=0.25, crack=0.18):
    r = rnd.random()
    if r < moss:
        return "minecraft:mossy_stone_bricks"
    if r < moss + crack:
        return "minecraft:cracked_stone_bricks"
    return "minecraft:stone_bricks"


def wild_ground(rnd, coarse=0.15, moss=0.12, path=0.0):
    r = rnd.random()
    if r < coarse:
        return "minecraft:coarse_dirt"
    if r < coarse + moss:
        return "minecraft:moss_block"
    if r < coarse + moss + path:
        return "minecraft:dirt_path"
    return "minecraft:grass_block[snowy=false]"


def ground_plants(s, rnd, cells, chance=0.35, flowers=("poppy", "dandelion", "azure_bluet", "oxeye_daisy")):
    """Plantinhas na camada y=1 sobre chão de terra/grama/musgo (só onde ainda está vazio)."""
    for (x, z) in cells:
        below = s.get(x, 0, z) or ""
        if s.get(x, 1, z) is not None or not any(k in below for k in ("grass_block", "moss_block", "coarse_dirt", "podzol")):
            continue
        r = rnd.random()
        if r < chance * 0.45:
            s.set(x, 1, z, "minecraft:short_grass")
        elif r < chance * 0.65:
            s.set(x, 1, z, "minecraft:fern")
        elif r < chance * 0.85 and "coarse" not in below:
            s.set(x, 1, z, "minecraft:" + rnd.choice(flowers))
        elif r < chance:
            s.set(x, 1, z, "minecraft:moss_carpet")


# =====================================================================================
#  1) Santuário à Beira da Estrada (7x8x7) — frente = +z (sul)
# =====================================================================================
def wayside_shrine():
    rnd = random.Random(7101)
    S, H = 7, 8
    s = Structure(S, H, S)
    # y=0: chão (anel de terra/grama/musgo) e base de pedra
    for x in range(S):
        for z in range(S):
            if 1 <= x <= 5 and 1 <= z <= 5:
                s.set(x, 0, z, aged_brick(rnd, 0.2, 0.15) if rnd.random() < 0.8 else "minecraft:cobblestone")
            else:
                s.set(x, 0, z, wild_ground(rnd, 0.12, 0.18))
    s.set(3, 0, 6, "minecraft:dirt_path")     # trilha que chega à frente
    s.set(2, 0, 6, "minecraft:gravel")
    # y=1: plataforma 5x5 de pedra envelhecida, degrau na frente
    for x in range(1, 6):
        for z in range(1, 6):
            s.set(x, 1, z, aged_brick(rnd, 0.3, 0.2))
    s.set(3, 1, 5, stairs("stone_brick_stairs", "north"))
    s.set(1, 1, 5, stairs("mossy_stone_brick_stairs", "north", shape="outer_right"))
    s.set(5, 1, 5, stairs("stone_brick_stairs", "north", shape="outer_left"))
    # nicho: parede do fundo (z=1) e laterais (x=1/5, z=2..3), y=2..4
    for y in range(2, 5):
        for x in range(1, 6):
            s.set(x, y, 1, aged_brick(rnd, 0.35, 0.2))
        for z in (2, 3):
            s.set(1, y, z, aged_brick(rnd, 0.35, 0.2))
            s.set(5, y, z, aged_brick(rnd, 0.35, 0.2))
    s.set(3, 3, 1, "minecraft:chiseled_stone_bricks")
    # colunas da frente (muro de tijolo de pedra ligado à parede lateral)
    connect(s, [(1, y, 4) for y in range(2, 5)], "minecraft:stone_brick_wall", "wall")
    connect(s, [(5, y, 4) for y in range(2, 5)], "minecraft:mossy_stone_brick_wall", "wall")
    # a "estátua": pedestal cinzelado + coluna + lanterna (cabeça luminosa)
    s.set(3, 2, 2, "minecraft:chiseled_stone_bricks")
    connect(s, [(3, 3, 2)], "minecraft:stone_brick_wall", "wall")
    s.set(3, 4, 2, LANTERN)
    # oferendas: baú pequeno diante da estátua, velas e vasos de flor
    chest(s, 3, 2, 3, "chests/living_world_wayside_shrine", facing="south")
    s.set(2, 2, 2, candle("white", 3, True))
    s.set(4, 2, 2, candle("", 2, False))
    s.set(2, 2, 3, "minecraft:potted_poppy")
    s.set(4, 2, 3, candle("yellow", 1, True))
    s.set(2, 2, 4, "minecraft:potted_cornflower")
    s.set(4, 2, 4, candle("white", 2, True))
    s.set(1, 2, 5, "minecraft:potted_azure_bluet")
    s.set(5, 2, 5, "minecraft:potted_dandelion")
    s.set(2, 4, 2, COBWEB)                      # teia no canto alto do nicho
    # telhado de duas águas em carvalho escuro (cumeeira ao longo de z, beiral de 1 bloco)
    for z in range(0, 6):
        s.set(0, 5, z, stairs("dark_oak_stairs", "east"))
        s.set(6, 5, z, stairs("dark_oak_stairs", "west"))
        for x in range(1, 6):
            s.set(x, 5, z, "minecraft:dark_oak_planks")
        s.set(1, 6, z, stairs("dark_oak_stairs", "east"))
        s.set(5, 6, z, stairs("dark_oak_stairs", "west"))
        for x in range(2, 5):
            s.set(x, 6, z, "minecraft:dark_oak_planks")
        s.set(2, 7, z, stairs("dark_oak_stairs", "east"))
        s.set(4, 7, z, stairs("dark_oak_stairs", "west"))
        s.set(3, 7, z, slab("spruce_slab"))
    # beiral da frente: degraus de cabeça para baixo e lanterna pendurada
    for x in (1, 5):
        s.set(x, 4, 5, stairs("dark_oak_stairs", "north", half="top"))
    s.set(3, 4, 5, LANTERN_HANG)
    # envelhecimento: musgo no telhado, trepadeiras no fundo
    for x in (0, 1, 5, 6):
        z = rnd.choice((0, 2, 4))
        if rnd.random() < 0.6:
            s.set(x, 6 if x in (0, 6) else 7, z, "minecraft:moss_carpet")
    for x in range(1, 6):
        for y in range(2, 5):
            if rnd.random() < 0.35:
                s.set(x, y, 0, "minecraft:vine[east=false,north=false,south=true,up=false,west=false]")
    # plantinhas em volta e limpeza (grama alta/neve) do resto da caixa
    ring = [(x, z) for x in range(S) for z in range(S) if not (1 <= x <= 5 and 1 <= z <= 5)]
    ground_plants(s, rnd, ring, 0.5)
    fill_empty(s, 0, 1, 0, S - 1, 4, S - 1)
    return s


# =====================================================================================
#  2) Cabana do Feiticeiro (9x11x9) — paredes x/z 1..7, chão em y=1, porta em +z
# =====================================================================================
def wizard_hut():
    rnd = random.Random(7102)
    S, H = 9, 11
    s = Structure(S, H, S)
    # y=0: fundação de pedregulho + chão em volta
    for x in range(S):
        for z in range(S):
            if 1 <= x <= 7 and 1 <= z <= 7:
                s.set(x, 0, z, "minecraft:mossy_cobblestone" if rnd.random() < 0.4 else "minecraft:cobblestone")
            else:
                s.set(x, 0, z, wild_ground(rnd, 0.12, 0.15) if rnd.random() < 0.85 else "minecraft:podzol[snowy=false]")
    # y=1: soco de pedra (borda) e assoalho de abeto
    for x in range(1, 8):
        for z in range(1, 8):
            edge = x in (1, 7) or z in (1, 7)
            if edge:
                s.set(x, 1, z, "minecraft:mossy_cobblestone" if rnd.random() < 0.45 else "minecraft:cobblestone")
            else:
                s.set(x, 1, z, "minecraft:spruce_planks")
    # paredes y=2..4: postes de carvalho escuro, enchimento de tábuas de abeto (algumas descascadas: torto/remendado)
    for y in range(2, 5):
        for x in range(1, 8):
            for z in range(1, 8):
                if not (x in (1, 7) or z in (1, 7)):
                    continue
                if (x, z) in ((1, 1), (7, 1), (1, 7), (7, 7), (1, 4), (7, 4)):
                    s.set(x, y, z, log("dark_oak_log"))
                else:
                    s.set(x, y, z, "minecraft:stripped_spruce_log[axis=y]" if rnd.random() < 0.15
                          else "minecraft:spruce_planks")
    # viga do topo (y=5)
    for x in range(1, 8):
        for z in (1, 7):
            s.set(x, 5, z, log("dark_oak_log", "x"))
    for z in range(2, 7):
        for x in (1, 7):
            s.set(x, 5, z, log("dark_oak_log", "z"))
    # janelas de vidro roxo
    for (x, z) in ((1, 3), (1, 5), (7, 5)):
        s.set(x, 3, z, "minecraft:purple_stained_glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
    for (x, z) in ((4, 1), (2, 7), (6, 7)):
        s.set(x, 3, z, "minecraft:purple_stained_glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
    # porta + degrau
    s.set(4, 2, 7, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    s.set(4, 3, 7, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    s.set(4, 1, 8, stairs("cobblestone_stairs", "north"))
    # lareira na parede leste (fornalha apagada) + chaminé de tijolo de pedra do lado de fora (x=8)
    s.set(7, 2, 3, "minecraft:furnace[facing=west,lit=false]")
    for y in range(1, 9):
        s.set(8, y, 3, aged_brick(rnd, 0.35, 0.25) if y < 8 else "minecraft:mossy_stone_bricks")
    s.set(8, 9, 3, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
    # telhado em pirâmide íngreme (chapéu de feiticeiro) de degraus de carvalho escuro
    def ring(d, y):
        lo, hi = d, S - 1 - d
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if not (x in (lo, hi) or z in (lo, hi)):
                    continue
                if (x, z) == (lo, lo):
                    st = stairs("dark_oak_stairs", "south", shape="outer_left")
                elif (x, z) == (hi, lo):
                    st = stairs("dark_oak_stairs", "south", shape="outer_right")
                elif (x, z) == (lo, hi):
                    st = stairs("dark_oak_stairs", "north", shape="outer_right")
                elif (x, z) == (hi, hi):
                    st = stairs("dark_oak_stairs", "north", shape="outer_left")
                elif x == lo:
                    st = stairs("dark_oak_stairs", "east")
                elif x == hi:
                    st = stairs("dark_oak_stairs", "west")
                elif z == lo:
                    st = stairs("dark_oak_stairs", "south")
                else:
                    st = stairs("dark_oak_stairs", "north")
                if s.get(x, y, z) is None:
                    s.set(x, y, z, st)
    ring(0, 5)    # beiral
    ring(1, 6)
    ring(2, 7)
    s.fill(3, 7, 3, 5, 7, 5, "minecraft:dark_oak_planks")   # forro
    ring(3, 8)
    s.set(4, 8, 4, "minecraft:dark_oak_planks")
    # ponta torta do chapéu
    s.set(4, 9, 4, stairs("dark_oak_stairs", "east"))
    s.set(5, 9, 4, "minecraft:dark_oak_planks")
    s.set(5, 10, 4, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    # um pouco de musgo no beiral
    for (x, z) in ((0, 2), (0, 6), (8, 7), (3, 0), (6, 8)):
        if rnd.random() < 0.8 and s.get(x, 6, z) is None:
            s.set(x, 6, z, "minecraft:moss_carpet")
    # ---- interior (x/z 2..6, piso y=1, de pé em y=2) ----
    s.set(2, 2, 2, "minecraft:bookshelf")
    s.set(2, 3, 2, "minecraft:bookshelf")
    s.set(3, 2, 2, "minecraft:bookshelf")
    s.set(3, 3, 2, "minecraft:bookshelf")
    s.set(2, 4, 2, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(3, 4, 2, "minecraft:small_amethyst_bud[facing=up,waterlogged=false]")
    s.set(5, 2, 2, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    s.set(6, 2, 2, "minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]",
          nbt={"Items": [{"Slot": Byte(0), "id": "minecraft:potion", "count": 1,
                          "components": {"minecraft:potion_contents": {"potion": "minecraft:night_vision"}}},
                         {"Slot": Byte(2), "id": "minecraft:potion", "count": 1,
                          "components": {"minecraft:potion_contents": {"potion": "minecraft:awkward"}}}]})
    s.set(6, 3, 2, COBWEB)
    s.set(6, 2, 4, "minecraft:water_cauldron[level=2]")
    s.set(2, 2, 4, "minecraft:purple_bed[facing=north,occupied=false,part=head]")
    s.set(2, 2, 5, "minecraft:purple_bed[facing=north,occupied=false,part=foot]")
    s.set(2, 2, 6, "minecraft:barrel[facing=up,open=false]")
    s.set(2, 3, 6, candle("purple", 2, True))
    chest(s, 6, 2, 6, "chests/living_world_wizard_hut", facing="west")
    s.set(6, 3, 6, "minecraft:potted_red_mushroom")
    s.set(5, 2, 6, "minecraft:flower_pot")
    for x in range(3, 6):
        for z in range(3, 7):
            if s.get(x, 2, z) is None and not (x == 4 and z == 6):
                s.set(x, 2, z, "minecraft:magenta_carpet" if (x, z) == (4, 4) else "minecraft:purple_carpet")
    s.set(4, 6, 4, LANTERN_HANG)
    s.set(3, 6, 3, COBWEB)
    # ---- lado de fora: poste com lanterna, barril de chuva, cogumelos/samambaias ----
    connect(s, [(6, 1, 8)], "minecraft:spruce_fence", "fence")
    s.set(6, 2, 8, LANTERN)
    s.set(0, 1, 6, "minecraft:barrel[facing=up,open=false]")
    s.set(0, 1, 5, "minecraft:potted_fern")
    plants = [(x, z) for x in range(S) for z in range(S) if x in (0, 8) or z in (0, 8)]
    ground_plants(s, rnd, plants, 0.5, flowers=("brown_mushroom", "red_mushroom", "lily_of_the_valley", "allium"))
    # ar explícito: interior inteiro + volta (limpa grama alta/neve/folhas)
    fill_empty(s, 2, 2, 2, 6, 6, 6)
    fill_empty(s, 0, 1, 0, S - 1, 4, S - 1)
    return s


# =====================================================================================
#  3) Acampamento Abandonado (11x5x11) — duas tendas de lã (uma caída), fogueira apagada, carroça quebrada
# =====================================================================================
def abandoned_camp():
    rnd = random.Random(7103)
    S, H = 11, 5
    s = Structure(S, H, S)
    # chão pisoteado
    for x in range(S):
        for z in range(S):
            s.set(x, 0, z, wild_ground(rnd, 0.22, 0.05, 0.10))
    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        s.set(5 + dx, 0, 5 + dz, rnd.choice(("minecraft:gravel", "minecraft:coarse_dirt", "minecraft:cobblestone")))
    # fogueira APAGADA com anel de pedras
    s.set(5, 1, 5, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set(5 + dx, 1, 5 + dz, slab(rnd.choice(("cobblestone_slab", "mossy_cobblestone_slab", "stone_slab"))))
    s.set(6, 1, 6, "minecraft:cobblestone_wall[up=true,north=none,east=none,south=none,west=none,waterlogged=false]")
    # tenda A (inteira): x0..4, z0..3, cumeeira em x=2 ao longo de z, aberta para +z (para a fogueira)
    for z in range(0, 4):
        s.set(0, 1, z, "minecraft:white_wool")
        s.set(4, 1, z, "minecraft:white_wool")
        s.set(1, 2, z, "minecraft:white_wool" if z % 2 else "minecraft:brown_wool")
        s.set(3, 2, z, "minecraft:white_wool" if z % 2 else "minecraft:brown_wool")
        s.set(2, 3, z, "minecraft:brown_wool")
    for x in (1, 2, 3):
        s.set(x, 1, 0, "minecraft:white_wool")
    s.set(2, 2, 0, "minecraft:white_wool")
    # estacas da frente
    connect(s, [(0, 2, 3)], "minecraft:spruce_fence", "fence")
    connect(s, [(4, 2, 3)], "minecraft:spruce_fence", "fence")
    # dentro: saco de dormir (tapetes), barril com o que sobrou
    s.set(2, 1, 1, "minecraft:white_carpet")
    s.set(2, 1, 2, "minecraft:red_carpet")
    s.set(2, 1, 3, "minecraft:red_carpet")
    barrel(s, 1, 1, 1, "chests/living_world_abandoned_camp")
    s.set(3, 1, 1, "minecraft:brown_carpet")
    s.set(2, 2, 2, COBWEB)
    # tenda B (desabada): x7..10, z6..10, cumeeira em z=8 ao longo de x, aberta para -x (longe da fogueira)
    for x in range(7, 11):
        if x != 8:
            s.set(x, 1, 6, "minecraft:light_gray_wool")
        s.set(x, 1, 10, "minecraft:light_gray_wool")
        if x >= 9:                       # metade da frente caiu
            s.set(x, 2, 7, "minecraft:light_gray_wool")
            s.set(x, 2, 9, "minecraft:green_wool")
            if x == 10:
                s.set(x, 3, 8, "minecraft:green_wool")
    for z in (7, 8, 9):
        s.set(10, 1, z, "minecraft:light_gray_wool")
    s.set(10, 2, 8, "minecraft:light_gray_wool")
    s.set(7, 1, 8, "minecraft:light_gray_carpet")    # lona caída no chão
    s.set(8, 1, 8, "minecraft:green_carpet")
    s.set(8, 1, 9, "minecraft:light_gray_carpet")
    s.set(7, 1, 9, "minecraft:green_carpet")
    s.set(9, 1, 8, "minecraft:brown_carpet")         # colchonete esquecido
    connect(s, [(7, 1, 7)], "minecraft:spruce_fence", "fence")   # estaca que ficou de pé
    # bancos de tronco em volta da fogueira
    s.set(3, 1, 5, log("spruce_log", "z"))
    s.set(3, 1, 6, log("spruce_log", "z"))
    s.set(6, 1, 3, log("stripped_oak_log", "x"))
    s.set(7, 1, 3, log("stripped_oak_log", "x"))
    # carroça quebrada (x8..10, z0..3): leito de lajes, uma ponta afundada, barril tombado, varais de cerca, rodas
    for (x, z) in ((8, 1), (9, 1), (8, 2)):
        s.set(x, 1, z, slab("spruce_slab", "top"))
    s.set(9, 1, 2, slab("spruce_slab", "bottom"))
    s.set(8, 2, 1, "minecraft:barrel[facing=up,open=false]")
    s.set(10, 1, 2, "minecraft:barrel[facing=east,open=false]")
    connect(s, [(7, 1, 1), (7, 1, 2)], "minecraft:spruce_fence", "fence")
    s.set(8, 1, 0, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
    s.set(9, 1, 0, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
    s.set(9, 1, 3, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]")
    s.set(10, 1, 0, "minecraft:hay_block[axis=x]")
    # estandarte sem placa num poste de cerca
    connect(s, [(1, 1, 8), (1, 2, 8)], "minecraft:spruce_fence", "fence")
    s.set(1, 3, 8, "minecraft:red_banner[rotation=4]")
    # restos: lanterna no chão, panela (caldeirão vazio), pilha de lenha
    s.set(4, 1, 7, LANTERN)
    s.set(6, 1, 4, "minecraft:cauldron")
    s.set(0, 1, 10, log("oak_log", "x"))
    s.set(1, 1, 10, log("oak_log", "x"))
    s.set(0, 2, 10, log("oak_log", "x"))
    # mato crescendo (abandonado há tempo)
    cells = [(x, z) for x in range(S) for z in range(S)]
    ground_plants(s, rnd, cells, 0.25, flowers=("dandelion", "poppy", "oxeye_daisy"))
    fill_empty(s, 0, 1, 0, S - 1, H - 1, S - 1)
    return s


# =====================================================================================
#  4) Espada Caída (9x15x9) — lâmina enterrada numa pequena cratera
# =====================================================================================
def fallen_sword():
    rnd = random.Random(7104)
    S, H = 9, 15
    s = Structure(S, H, S)
    c = 4
    # cratera: centro afundado (y=0) de terra grossa/pedra rachada, borda levantada (y=1)
    for x in range(S):
        for z in range(S):
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if d > 4.6:
                continue
            if d <= 2.6:
                s.set(x, 0, z, rnd.choice(("minecraft:coarse_dirt", "minecraft:coarse_dirt", "minecraft:gravel",
                                            "minecraft:cracked_stone_bricks", "minecraft:cobblestone", "minecraft:tuff")))
            else:
                s.set(x, 0, z, rnd.choice(("minecraft:coarse_dirt", "minecraft:rooted_dirt",
                                            "minecraft:grass_block[snowy=false]", "minecraft:moss_block")))
                if 3.3 <= d <= 4.6 and rnd.random() < 0.55:
                    s.set(x, 1, z, rnd.choice(("minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:cobblestone",
                                                "minecraft:mossy_cobblestone")))
    # lâmina (y=0..7): núcleo de ferro, fios de concreto; um dente lascado no fio
    for y in range(0, 8):
        s.set(c, y, c, "minecraft:iron_block")
        s.set(c - 1, y, c, "minecraft:light_gray_concrete" if y % 3 else "minecraft:white_concrete")
        if y != 5:
            s.set(c + 1, y, c, "minecraft:white_concrete" if y % 3 else "minecraft:light_gray_concrete")
    s.set(c + 1, 5, c, slab("smooth_stone_slab", "top"))
    s.set(c, 8, c, "minecraft:iron_block")
    # guarda (y=9): braços de ardósia polida, pontas e centro de ouro
    for x in range(1, 8):
        s.set(x, 9, c, "minecraft:polished_deepslate")
    s.set(1, 9, c, "minecraft:gold_block")
    s.set(7, 9, c, "minecraft:gold_block")
    s.set(c, 9, c, "minecraft:gold_block")
    s.set(c, 9, c - 1, "minecraft:gold_block")
    s.set(c, 9, c + 1, "minecraft:gold_block")
    s.set(2, 10, c, stairs("polished_deepslate_stairs", "west"))
    s.set(6, 10, c, stairs("polished_deepslate_stairs", "east"))
    s.set(2, 8, c, stairs("polished_deepslate_stairs", "west", half="top"))
    s.set(6, 8, c, stairs("polished_deepslate_stairs", "east", half="top"))
    # correntes penduradas da guarda
    s.set(1, 8, c, CHAIN_Y)
    s.set(1, 7, c, CHAIN_Y)
    s.set(7, 8, c, CHAIN_Y)
    # empunhadura (y=10..12): tronco de carvalho escuro "enrolado" com correntes
    for y in range(10, 13):
        s.set(c, y, c, log("dark_oak_log"))
    s.set(c, 11, c - 1, "minecraft:chain[axis=x,waterlogged=false]")
    s.set(c, 11, c + 1, "minecraft:chain[axis=x,waterlogged=false]")
    s.set(c - 1, 12, c, "minecraft:chain[axis=z,waterlogged=false]")
    s.set(c + 1, 10, c, "minecraft:chain[axis=z,waterlogged=false]")
    # pomo (y=13..14): cristal luminoso preso em ouro
    s.set(c, 13, c, f"{NS}:luminous_crystal_block")
    s.set(c - 1, 13, c, stairs("polished_deepslate_stairs", "east"))
    s.set(c + 1, 13, c, stairs("polished_deepslate_stairs", "west"))
    s.set(c, 14, c, "minecraft:gold_block")
    # ferrugem e destroços em volta (cobre oxidado), musgo
    debris = [(2, 2), (6, 6), (1, 5), (6, 2), (3, 7), (7, 4)]
    for i, (x, z) in enumerate(debris):
        if s.get(x, 1, z) is not None:
            continue
        s.set(x, 1, z, ("minecraft:oxidized_copper", slab("oxidized_cut_copper_slab"),
                        "minecraft:weathered_cut_copper", "minecraft:moss_carpet",
                        slab("oxidized_cut_copper_slab"), "minecraft:moss_carpet")[i])
    s.set(c - 1, 1, c + 1, "minecraft:moss_carpet")
    s.set(c + 1, 1, c - 1, "minecraft:moss_carpet")
    for y in (2, 4):
        s.set(c, y, c - 1, "minecraft:vine[east=false,north=false,south=true,up=false,west=false]")
    # baú meio enterrado ao pé da lâmina (o tampo fica rente ao chão)
    chest(s, c + 1, 0, c + 2, "chests/living_world_fallen_sword", facing="south")
    # limpa grama/neve dentro da cratera
    fill_empty(s, 0, 1, 0, S - 1, 2, S - 1,
               pred=lambda x, y, z: ((x - c) ** 2 + (z - c) ** 2) ** 0.5 <= 4.6)
    return s


# =====================================================================================
#  Loot (modesto: achados comuns)
# =====================================================================================
def item(name, weight, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    fns = []
    if hi > 1:
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        fns.extend(extra)
    if fns:
        e["functions"] = fns
    return e


def coin(weight, lo=1, hi=2):
    # esmeralda vira moeda da guilda (se o item existir)
    return item("minecraft:emerald", weight, lo, hi, [{"function": f"{NS}:item_if_present", "item": f"{NS}:guild_coin"}])


def scroll(weight, lo_t=1, hi_t=2):
    return item(f"{NS}:glyph_scroll", weight, extra=[{"function": f"{NS}:ruin_glyph_scroll",
                                                       "min_tier": lo_t, "max_tier": hi_t}])


def potion(pid, weight):
    return item("minecraft:potion", weight, extra=[{"function": "minecraft:set_potion", "id": pid}])


LOOT = {
    "wayside_shrine": ((2, 4), [
        item("minecraft:bread", 8, 1, 3), item("minecraft:apple", 7, 1, 3), item("minecraft:sweet_berries", 5, 2, 5),
        item("minecraft:honey_bottle", 2), item("minecraft:candle", 5, 1, 2), item("minecraft:poppy", 3, 1, 2),
        item("minecraft:gold_nugget", 5, 2, 6), coin(4, 1, 2), item(f"{NS}:mana_crystal", 4), scroll(1, 1, 2),
    ]),
    "wizard_hut": ((3, 5), [
        item("minecraft:book", 6, 1, 3), item("minecraft:paper", 5, 2, 5), item("minecraft:glass_bottle", 4, 1, 3),
        potion("minecraft:healing", 3), potion("minecraft:swiftness", 2), potion("minecraft:night_vision", 2),
        item("minecraft:redstone", 3, 1, 4), item("minecraft:glowstone_dust", 3, 1, 3), item("minecraft:lapis_lazuli", 3, 1, 4),
        item("minecraft:amethyst_shard", 4, 1, 3), item(f"{NS}:mana_crystal", 5, 1, 2),
        item(f"{NS}:essence_arcane", 3), scroll(3, 1, 2), coin(3, 1, 2), item("minecraft:candle", 3, 1, 2),
    ]),
    "abandoned_camp": ((3, 5), [
        item("minecraft:bread", 7, 1, 3), item("minecraft:cooked_porkchop", 4, 1, 2), item("minecraft:apple", 5, 1, 2),
        item("minecraft:carrot", 4, 1, 3), item("minecraft:potato", 4, 1, 3), item("minecraft:leather", 4, 1, 3),
        item("minecraft:string", 4, 1, 3), item("minecraft:arrow", 5, 4, 10), item("minecraft:torch", 5, 2, 6),
        item("minecraft:iron_nugget", 4, 2, 6), item("minecraft:lead", 2), item("minecraft:saddle", 1),
        coin(3, 1, 2), item(f"{NS}:mana_crystal", 2), scroll(1, 1, 2),
    ]),
    "fallen_sword": ((2, 4), [
        item("minecraft:iron_ingot", 8, 1, 4), item("minecraft:gold_ingot", 5, 1, 3), item("minecraft:iron_nugget", 6, 3, 8),
        item("minecraft:gold_nugget", 6, 3, 8), item("minecraft:copper_ingot", 4, 2, 5), item("minecraft:chain", 3, 1, 3),
        item("minecraft:iron_sword", 2, extra=[{"function": "minecraft:set_damage",
                                                 "damage": {"type": "minecraft:uniform", "min": 0.25, "max": 0.6}}]),
        coin(3, 1, 3), item(f"{NS}:mana_crystal", 3, 1, 2), scroll(2, 2, 2),
    ]),
}


def write_loot(pid):
    (lo, hi), entries = LOOT[pid]
    write_json(os.path.join(D, "loot_table/chests", f"living_world_{pid}.json"), {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "bonus_rolls": 0, "entries": entries}],
        "random_sequence": f"{NS}:chests/living_world_{pid}",
    })


# =====================================================================================
#  Worldgen das estruturas
# =====================================================================================
# Zonas de exclusão: as mesmas do Acampamento do Mago Errante (cidades da guilda e capitais) + aldeia com salão da
# guilda (assentamento de NPCs) e vilas do vanilla.
EXCLUSION = [
    {"other_set": f"{NS}:guild_city", "chunk_count": 10},
    {"other_set": f"{NS}:capital_city", "chunk_count": 13},
    {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
    {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13},
    {"other_set": f"{NS}:guild_hall_village", "chunk_count": 6},
    {"other_set": "minecraft:villages", "chunk_count": 4},
]

# id: (biomas, spacing, separation, salt)
POIS = {
    "wayside_shrine": (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                        "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:taiga", "minecraft:cherry_grove",
                        f"{NS}:flat_lands_kingdom_meadows", f"{NS}:sakura_fields"], 34, 12, 1946307201),
    "wizard_hut": (["minecraft:dark_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
                    "minecraft:taiga", "minecraft:old_growth_spruce_taiga", "minecraft:swamp",
                    f"{NS}:twilight_grove", f"{NS}:haunted_swamp", f"{NS}:crystal_forest"], 40, 14, 1946307202),
    "abandoned_camp": (["minecraft:plains", "minecraft:savanna", "minecraft:forest", "minecraft:taiga",
                        "minecraft:snowy_plains", "minecraft:meadow", "minecraft:windswept_hills",
                        f"{NS}:flat_lands_golden_steppe"], 32, 11, 1946307203),
    "fallen_sword": (["minecraft:plains", "minecraft:savanna", "minecraft:badlands", "minecraft:desert",
                      "minecraft:windswept_savanna", "minecraft:snowy_plains", "minecraft:meadow",
                      f"{NS}:flat_lands_golden_steppe", f"{NS}:flat_lands_kingdom_meadows"], 48, 18, 1946307204),
}

BUILDERS = {"wayside_shrine": wayside_shrine, "wizard_hut": wizard_hut,
            "abandoned_camp": abandoned_camp, "fallen_sword": fallen_sword}


def write_poi(pid):
    biomes, spacing, separation, salt = POIS[pid]
    s = BUILDERS[pid]()
    n = s.save(os.path.join(D, "structure/living_world", f"{pid}.nbt"))
    written.append(os.path.relpath(os.path.join(D, "structure/living_world", f"{pid}.nbt"), ROOT) + f"  ({n} blocos)")
    write_json(os.path.join(D, "worldgen/structure", f"{pid}.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/{pid}",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": f"{NS}:{pid}/start",
        "size": 1,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
    })
    write_json(os.path.join(D, "worldgen/template_pool", pid, "start.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": f"{NS}:living_world/{pid}",
            "processors": "minecraft:empty",
            "projection": "rigid"}}],
    })
    write_json(os.path.join(D, "worldgen/structure_set", f"{pid}.json"), {
        "structures": [{"structure": f"{NS}:{pid}", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": spacing, "separation": separation,
                      "salt": salt, "exclusion_zones": EXCLUSION},
    })
    write_json(os.path.join(MERGE, "tags/worldgen/biome/has_structure", f"{pid}.json"),
               {"replace": False, "values": biomes})
    write_loot(pid)


LANG_PT = {
    "wayside_shrine": ("um santuário à beira da estrada", "Santuário à Beira da Estrada"),
    "wizard_hut": ("a cabana de um feiticeiro", "Cabana do Feiticeiro"),
    "abandoned_camp": ("um acampamento abandonado", "Acampamento Abandonado"),
    "fallen_sword": ("uma espada gigante caída", "Espada Caída"),
}
LANG_EN = {
    "wayside_shrine": ("a wayside shrine", "Wayside Shrine"),
    "wizard_hut": ("a wizard's hut", "Wizard's Hut"),
    "abandoned_camp": ("an abandoned camp", "Abandoned Camp"),
    "fallen_sword": ("a giant fallen sword", "Fallen Sword"),
}


def write_lang():
    for code, table in (("pt_br", LANG_PT), ("en_us", LANG_EN)):
        out = {}
        for pid, (rumor, name) in table.items():
            out[f"rumor.{NS}.cities.structure.{pid}"] = rumor
            out[f"map.{NS}.structure.{pid}"] = name
            out[f"structure.{NS}.{pid}"] = name
        write_json(os.path.join(LANG, f"{code}.json"), out)


# =====================================================================================
#  Parte 2 — decorações (features do vanilla + biome modifiers)
# =====================================================================================
WILD_SOIL = ["minecraft:grass_block", "minecraft:dirt", "minecraft:podzol", "minecraft:coarse_dirt",
             "minecraft:rooted_dirt", "minecraft:moss_block"]

# Blocos "de construção": se algum estiver perto, a decoração não nasce (cidades, vilas, estradas, outras estruturas).
BUILT_TAG = f"{NS}:living_world_built"
BUILT_VALUES = [
    "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs", "#minecraft:wooden_fences",
    "#minecraft:wool", "#minecraft:beds", "#minecraft:doors",
    "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
    "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_slab", "minecraft:stone_brick_stairs",
    "minecraft:stone_brick_wall", "minecraft:cobblestone", "minecraft:cobblestone_slab", "minecraft:cobblestone_stairs",
    "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_slab", "minecraft:polished_andesite",
    "minecraft:polished_andesite_slab", "minecraft:andesite_slab", "minecraft:smooth_stone", "minecraft:smooth_stone_slab",
    "minecraft:smooth_sandstone", "minecraft:smooth_sandstone_slab", "minecraft:cut_sandstone", "minecraft:sandstone_slab",
    "minecraft:smooth_red_sandstone", "minecraft:bricks", "minecraft:dirt_path", "minecraft:glass",
    "minecraft:glass_pane", "minecraft:lantern", "minecraft:torch", "minecraft:wall_torch", "minecraft:hay_block",
    "minecraft:deepslate_bricks", "minecraft:polished_deepslate", "minecraft:farmland",
]

# 20 pontos (8 direções a 4 blocos, nível do chão e +1; 4 cardeais a 8 blocos no chão): barato e só roda nos chunks
# sorteados pela raridade.
WILD_OFFSETS = ([(dx * 4, dy, dz * 4) for dy in (-1, 1)
                 for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1))]
                + [(8, -1, 0), (-8, -1, 0), (0, -1, 8), (0, -1, -8)])


def wilderness_filter():
    return {"type": "minecraft:block_predicate_filter", "predicate": {
        "type": "minecraft:not", "predicate": {"type": "minecraft:any_of", "predicates": [
            {"type": "minecraft:matching_block_tag", "tag": BUILT_TAG, "offset": list(o)} for o in WILD_OFFSETS]}}}


def soil_below_filter(blocks=WILD_SOIL):
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
        {"type": "minecraft:replaceable"},
        {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": blocks}]}}


def surface_placement(rarity, extra):
    return ([{"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
             {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}]
            + extra + [{"type": "minecraft:biome"}])


def weighted(*pairs):
    return {"type": "minecraft:weighted_state_provider",
            "entries": [{"weight": w, "data": st} for (st, w) in pairs]}


def bs(name, **props):
    d = {"Name": name}
    if props:
        d["Properties"] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
    return d


WALL_POST = dict(up="true", north="none", east="none", south="none", west="none", waterlogged="false")

FEATURES = {
    # a) pilar antigo quebrado
    "ruin_pillar": {
        "configured": {"type": "minecraft:block_column", "config": {
            "direction": "up",
            "allowed_placement": {"type": "minecraft:replaceable"},
            "prioritize_tip": False,
            "layers": [
                {"height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2},
                 "provider": weighted((bs("minecraft:mossy_stone_bricks"), 3), (bs("minecraft:cracked_stone_bricks"), 2))},
                {"height": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2},
                 "provider": weighted((bs("minecraft:stone_bricks"), 3), (bs("minecraft:mossy_stone_bricks"), 1),
                                      (bs("minecraft:cracked_stone_bricks"), 1))},
                {"height": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 1},
                 "provider": weighted((bs("minecraft:chiseled_stone_bricks"), 2),
                                      (bs("minecraft:stone_brick_wall", **WALL_POST), 2),
                                      (bs("minecraft:mossy_stone_brick_wall", **WALL_POST), 1),
                                      (bs("minecraft:stone_brick_slab", type="bottom", waterlogged="false"), 1))},
            ]}},
        "rarity": 40,
        "extra": [soil_below_filter(), wilderness_filter()],
        "biomes": ["minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                   "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga",
                   "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                   "minecraft:meadow", "minecraft:windswept_hills", "minecraft:windswept_forest",
                   f"{NS}:crystal_forest", f"{NS}:twilight_grove"],
    },
    # b) pedregulho de musgo (como as pedras da taiga antiga)
    "mossy_boulder": {
        "configured": {"type": "minecraft:forest_rock", "config": {"state": bs("minecraft:mossy_cobblestone")}},
        "rarity": 14,
        "extra": [soil_below_filter(), wilderness_filter()],
        "biomes": ["minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                   "minecraft:old_growth_birch_forest", "minecraft:taiga", "minecraft:snowy_taiga",
                   "minecraft:meadow", "minecraft:windswept_hills", "minecraft:windswept_forest",
                   f"{NS}:crystal_forest"],
    },
    # c) afloramento de cristais de mana na pedra das montanhas
    "mana_outcrop": {
        "configured": {"type": "minecraft:random_patch", "config": {
            "tries": 12, "xz_spread": 3, "y_spread": 2,
            "feature": {
                "feature": {"type": "minecraft:simple_block", "config": {"to_place": {
                    "type": "minecraft:simple_state_provider",
                    "state": bs(f"{NS}:luminous_crystal_cluster", facing="up", waterlogged="false")}}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                        {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
                         "blocks": ["minecraft:stone", "minecraft:andesite", "minecraft:calcite", "minecraft:tuff",
                                    "minecraft:diorite", "minecraft:granite"]}]}}]}}},
        "rarity": 24,
        "extra": [wilderness_filter()],
        "biomes": ["minecraft:meadow", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                   "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:grove", "minecraft:snowy_slopes"],
    },
    # d) roda de cogumelos com pontinhos de líquen brilhante no chão da floresta escura
    "glow_mushrooms": {
        "configured": {"type": "minecraft:random_patch", "config": {
            "tries": 24, "xz_spread": 4, "y_spread": 2,
            "feature": {
                "feature": {"type": "minecraft:simple_block", "config": {"to_place": weighted(
                    (bs("minecraft:brown_mushroom"), 5), (bs("minecraft:red_mushroom"), 3),
                    (bs("minecraft:glow_lichen", down="true", up="false", north="false", south="false", east="false",
                        west="false", waterlogged="false"), 1))}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                        {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
                         "blocks": WILD_SOIL + ["minecraft:mycelium", "minecraft:mud"]},
                        {"type": "minecraft:would_survive", "state": bs("minecraft:brown_mushroom")}]}}]}}},
        "rarity": 6,
        "extra": [wilderness_filter()],
        "biomes": ["minecraft:dark_forest", "minecraft:swamp", f"{NS}:twilight_grove", f"{NS}:haunted_swamp"],
    },
}


def write_features():
    for fid, f in FEATURES.items():
        key = f"living_world_{fid}"
        write_json(os.path.join(D, "worldgen/configured_feature", f"{key}.json"), f["configured"])
        write_json(os.path.join(D, "worldgen/placed_feature", f"{key}.json"), {
            "feature": f"{NS}:{key}", "placement": surface_placement(f["rarity"], f["extra"])})
        write_json(os.path.join(MERGE, "tags/worldgen/biome", f"{key}.json"), {"replace": False, "values": f["biomes"]})
        write_json(os.path.join(D, "neoforge/biome_modifier", f"{key}.json"), {
            "type": "neoforge:add_features", "biomes": f"#{NS}:{key}",
            "features": f"{NS}:{key}", "step": "vegetal_decoration"})
    write_json(os.path.join(MERGE, "tags/block", "living_world_built.json"), {"replace": False, "values": BUILT_VALUES})


def main():
    for pid in POIS:
        write_poi(pid)
    write_lang()
    write_features()
    for p in written:
        print("  ", p)
    print(f"living_world_pois: {len(written)} arquivos")


if __name__ == "__main__":
    main()
