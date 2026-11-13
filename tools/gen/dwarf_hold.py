"""Dados do módulo "dwarf_hold" (v1.6): Cidade Anã Perdida, Mithril, Forja Rúnica, anões e tesouros.

Uso (na raiz do projeto):  py -I tools/gen/dwarf_hold.py        (a arte fica no tools/gen/dwarf_hold_art.py)

Gera:
  - molde data/isekai_mahou/structure/dwarf_hold/city.nbt (63x20x63, peça única) + worldgen (structure, structure_set
    isekai_mahou:random_spread_multi, template_pool, processor_list de desgaste)
  - minério de Mithril (configured/placed feature + biome modifier, só deepslate abaixo de y 0)
  - loot tables (baús da cidade, blocos, relíquia rara), receitas, tags (merge), capítulo do Diário
  - traduções src/main/lang/dwarf_hold/{pt_br,en_us}.json
No fim CONFERE o molde (anões, forja, baús, portas do cofre, centro livre para o /mahou dwarf_hold tp). Erro = código 1.

Molde: x = leste, z = sul, y = cima. y=0 alicerce, y=1 piso, pisa-se em y=2 (FLOOR = 2 = DwarfCommands.HALL_FLOOR).
O centro do molde (31, 2, 31) fica no corredor central do Grande Salão (o tp usa o centro da caixa da estrutura).
"""
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from structgen import Structure, Byte, Int  # noqa: E402
from world_v09 import fix_connections  # noqa: E402
import depths_puzzles as PZ  # noqa: E402

NS = "isekai_mahou"
RES = os.path.join(ROOT, "src/main/resources")
D = os.path.join(RES, "data", NS)
MERGE = os.path.join(ROOT, "src/main/merge/dwarf_hold")
LANG = os.path.join(ROOT, "src/main/lang/dwarf_hold")
ERRORS = []


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


def mc(n):
    return n if ":" in n else "minecraft:" + n


# =================================================================================================
#  MOLDE DA CIDADE
# =================================================================================================
SX, SY, SZ = 63, 20, 63
FLOOR = 2
AIR = "minecraft:air"
DB, DT, PD, CD = mc("deepslate_bricks"), mc("deepslate_tiles"), mc("polished_deepslate"), mc("chiseled_deepslate")
COB = mc("cobbled_deepslate")
BB, PB, GB = mc("polished_blackstone_bricks"), mc("polished_blackstone"), mc("gilded_blackstone")
GOLD = mc("gold_block")
FORGE = f"{NS}:dwarf_rune_forge"
ORE = f"{NS}:deepslate_mithril_ore"


class City(Structure):
    def __init__(self):
        super().__init__(SX, SY, SZ)
        self.rnd = random.Random("dwarf_hold/city")

    def room(self, x0, z0, x1, z1, top, wall=DB, floor=DT, ceil=DT):
        """Sala fechada: alicerce (y0), piso (y1), paredes até top-1, teto em top e top+1; interior em ar explícito."""
        self.fill(x0, 0, z0, x1, 0, z1, DB)
        self.fill(x0, 1, z0, x1, 1, z1, floor)
        self.walls(x0, 2, z0, x1, top - 1, z1, wall)
        self.fill(x0, top, z0, x1, top + 1, z1, ceil)
        self.fill(x0 + 1, 2, z0 + 1, x1 - 1, top - 1, z1 - 1, AIR)

    def opening(self, x0, z0, x1, z1, y0, y1, frame=CD):
        """Abre uma passagem (ar) e põe moldura de ardósia cinzelada em volta."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    self.set(x, y, z, AIR)
        if frame:
            if x0 == x1:  # parede no eixo z
                for y in range(y0, y1 + 2):
                    self.set(x0, y, z0 - 1, frame)
                    self.set(x0, y, z1 + 1, frame)
                for z in range(z0 - 1, z1 + 2):
                    self.set(x0, y1 + 1, z, frame)
            else:
                for y in range(y0, y1 + 2):
                    self.set(x0 - 1, y, z0, frame)
                    self.set(x1 + 1, y, z0, frame)
                for x in range(x0 - 1, x1 + 2):
                    self.set(x, y1 + 1, z0, frame)

    def chest(self, x, y, z, table, facing="south"):
        self.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]",
                 nbt={"LootTable": f"{NS}:chests/{table}"})

    def barrel(self, x, y, z, table=None, facing="up"):
        self.set(x, y, z, f"minecraft:barrel[facing={facing},open=false]", nbt={"LootTable": f"{NS}:chests/{table}"} if table else None)

    def hanging_lantern(self, x, z, top, length=2, soul=False):
        for y in range(top - length, top):
            self.set(x, y, z, "minecraft:chain[axis=y,waterlogged=false]")
        self.set(x, top - length - 1, z, f"minecraft:{'soul_' if soul else ''}lantern[hanging=true,waterlogged=false]")

    def brazier(self, x, z):
        self.set(x, FLOOR, z, CD)
        self.set(x, FLOOR + 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")

    def pillar(self, x, z, top, w=2):
        for dx in range(w):
            for dz in range(w):
                self.set(x + dx, FLOOR, z + dz, CD)
                for y in range(FLOOR + 1, top - 1):
                    self.set(x + dx, y, z + dz, PD)
                self.set(x + dx, top - 1, z + dz, CD)

    def statue(self, cx, cz, face="south", y=FLOOR):
        """Estátua de um rei anão: pedestal, pernas, tronco largo, barba, elmo de ouro com chifres e um machado."""
        f = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[face]
        side = (f[1], f[0])  # eixo dos ombros
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                self.set(cx + dx, y, cz + dz, CD)
        for s in (-1, 1):
            self.set(cx + side[0] * s, y + 1, cz + side[1] * s, BB)
            self.set(cx + side[0] * s, y + 2, cz + side[1] * s, BB)
        for dy in range(3, 6):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (dx, dz) == (0, 0) or abs(dx) + abs(dz) == 1 or dy < 5:
                        self.set(cx + dx, y + dy, cz + dz, PB)
        self.set(cx, y + 6, cz, PD)                                       # cabeça
        self.set(cx + f[0], y + 6, cz + f[1], mc("polished_deepslate_wall"))
        self.set(cx + f[0], y + 5, cz + f[1], mc("deepslate_tile_wall"))  # barba
        self.set(cx + f[0], y + 4, cz + f[1], mc("deepslate_tile_wall"))
        self.set(cx, y + 7, cz, GOLD)                                     # elmo
        for s in (-1, 1):
            self.set(cx + side[0] * s, y + 7, cz + side[1] * s, mc("bone_block[axis=y]"))
        # machado de pedra apoiado
        ax, az = cx + side[0] * 2, cz + side[1] * 2
        for dy in range(1, 5):
            self.set(ax, y + dy, az, mc("polished_blackstone_wall"))
        self.set(ax, y + 5, az, mc("iron_block"))
        self.set(ax + f[0], y + 5, az + f[1], mc("iron_bars[waterlogged=false]"))
        self.set(ax - f[0], y + 5, az - f[1], mc("iron_bars[waterlogged=false]"))


def build_hall(s):
    # Grande Salão: x 15..47, z 15..45, teto em y 15
    s.room(15, 15, 47, 45, 15, wall=DB, floor=DT, ceil=DT)
    # piso: borda de ardósia polida, corredor central de blackstone com a linha dourada das runas
    for x in range(16, 47):
        for z in range(16, 45):
            if x in (16, 46) or z in (16, 44):
                s.set(x, 1, z, PD)
    for x in range(29, 34):
        for z in range(16, 45):
            s.set(x, 1, z, BB)
    for z in range(16, 45):
        s.set(31, 1, z, GB if z % 4 else CD)
    for (x, z) in ((30, 30), (32, 30), (30, 32), (32, 32), (31, 29), (31, 33), (29, 31), (33, 31)):
        s.set(x, 1, z, CD)
    s.set(31, 1, 31, mc("gold_block"))
    # colunas 2x2 e lustres
    for z in (20, 26, 32, 38):
        s.pillar(19, z, 15)
        s.pillar(42, z, 15)
    for x in (24, 31, 38):
        for z in (22, 30, 38):
            s.hanging_lantern(x, z, 15, length=3 if x == 31 else 2)
    # mesas compridas (laje de carvalho escuro) com bancos de escada de abeto
    for tx in (25, 37):
        for z in range(21, 41):
            s.set(tx, FLOOR, z, mc("dark_oak_slab[type=top,waterlogged=false]"))
            s.set(tx - 1, FLOOR, z, mc("spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
            s.set(tx + 1, FLOOR, z, mc("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
        for z in (23, 28, 33, 38):
            s.set(tx, FLOOR + 1, z, mc("candle[candles=3,lit=true,waterlogged=false]"))
        s.set(tx, FLOOR + 1, 30, mc("lantern[hanging=false,waterlogged=false]"))
    # braseiros ao longo do corredor central
    for z in (19, 27, 35, 42):
        s.brazier(28, z)
        s.brazier(34, z)
    # estrado e trono do Rei da Montanha (norte)
    s.fill(26, FLOOR, 16, 36, FLOOR, 19, PD)
    s.fill(28, FLOOR + 1, 16, 34, FLOOR + 1, 18, PD)
    for x in range(26, 37):
        s.set(x, FLOOR, 20, mc("polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
    for x in range(28, 35):
        s.set(x, FLOOR + 1, 19, mc("polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
    s.set(31, FLOOR + 2, 17, mc("polished_blackstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
    s.set(30, FLOOR + 2, 17, GOLD)
    s.set(32, FLOOR + 2, 17, GOLD)
    for y in range(FLOOR + 2, FLOOR + 6):
        s.set(31, y, 16, GB if y < FLOOR + 5 else GOLD)
    s.set(30, FLOOR + 5, 16, GOLD)
    s.set(32, FLOOR + 5, 16, GOLD)
    s.set(31, FLOOR + 6, 16, mc("gold_block"))
    s.set(30, FLOOR + 3, 17, mc("lantern[hanging=false,waterlogged=false]"))
    s.set(32, FLOOR + 3, 17, mc("lantern[hanging=false,waterlogged=false]"))
    s.statue(23, 18, "south")
    s.statue(39, 18, "south")
    # runas na parede norte (atrás do trono) e faixas nas laterais
    for x in range(26, 37, 2):
        s.set(x, 9, 15, GB)
    for z in range(18, 44, 3):
        s.set(15, 6, z, GB)
        s.set(47, 6, z, GB)
    # portão de pedra (sul): arco de 7 de largura com grade erguida
    s.opening(28, 45, 34, 45, FLOOR, 9, frame=None)
    for x in range(27, 36):
        s.set(x, 10, 45, CD)
        s.set(x, 11, 45, GB if x % 2 else CD)
    for y in range(FLOOR, 11):
        s.set(27, y, 45, CD)
        s.set(35, y, 45, CD)
    for x in range(28, 35):
        s.set(x, 9, 45, mc("iron_bars[waterlogged=false]"))
    # passagens para as alas (oeste: mercado, leste: forja) e para o cofre (norte, com porta de ferro)
    s.opening(15, 28, 15, 32, FLOOR, 5)
    s.opening(47, 28, 47, 32, FLOOR, 5)


def build_entrance(s):
    # Salão do Portão: x 25..37, z 45..61, teto em y 12; a ponta sul desabou (entulho) — escave para entrar
    s.room(25, 45, 37, 61, 12, wall=DB, floor=DT, ceil=DT)
    s.opening(28, 45, 34, 45, FLOOR, 9, frame=None)
    for z in range(46, 61):
        for x in range(29, 34):
            s.set(x, 1, z, BB)
        s.set(31, 1, z, GB if z % 3 else CD)
    s.statue(27, 52, "east")
    s.statue(35, 52, "west")
    for z in (49, 56):
        s.hanging_lantern(31, z, 12, 2)
    for z in (47, 59):
        s.brazier(26, z)
        s.brazier(36, z)
    # entulho do desabamento
    rnd = s.rnd
    for x in range(26, 37):
        for z in range(58, 61):
            h = rnd.randint(0, 3) + (61 - z)
            for y in range(FLOOR, FLOOR + h):
                s.set(x, y, z, rnd.choice([COB, COB, mc("gravel"), mc("tuff"), DB]))
    s.opening(29, 61, 33, 61, FLOOR, 6, frame=None)
    for x in range(29, 34):
        for y in range(FLOOR, 7):
            s.set(x, y, 61, rnd.choice([COB, mc("gravel"), COB, mc("tuff")]))


def build_market(s):
    # Mercado: x 1..15, z 17..43, teto em y 10
    s.room(1, 17, 15, 43, 10, wall=DB, floor=PD, ceil=DT)
    s.opening(15, 28, 15, 32, FLOOR, 5)
    for x in range(2, 15):
        for z in range(18, 43):
            if (x + z) % 2 == 0:
                s.set(x, 1, z, DT)
    wools = ["red", "yellow", "blue", "green", "orange", "cyan"]
    for i, z in enumerate((20, 26, 34, 40)):
        for side, (cx, face) in enumerate(((3, "east"), (12, "west"))):
            wool = wools[(i * 2 + side) % len(wools)]
            # balcão de barris, postes de cerca e toldo de lã
            for dz in (-1, 0, 1):
                s.set(cx + (1 if face == "east" else -1), FLOOR, z + dz, mc("spruce_slab[type=top,waterlogged=false]"))
                s.barrel(cx, FLOOR, z + dz, "dwarf_hold_market" if dz == 0 else None)
            for dz in (-2, 2):
                px = cx + (1 if face == "east" else -1)
                for y in range(FLOOR, FLOOR + 3):
                    s.set(px, y, z + dz, mc("spruce_fence[waterlogged=false]"))
            for dz in range(-2, 3):
                for dx in (0, 1) if face == "east" else (0, -1):
                    s.set(cx + dx, FLOOR + 3, z + dz, mc(f"{wool}_wool"))
            s.set(cx + (1 if face == "east" else -1), FLOOR + 1, z, mc("lantern[hanging=false,waterlogged=false]"))
    for z in (23, 30, 37):
        s.hanging_lantern(8, z, 10, 2)
    s.chest(13, FLOOR, 42, "dwarf_hold_market", "north")
    s.chest(2, FLOOR, 18, "dwarf_hold_market", "south")
    s.entity(8.5, FLOOR, 30.5, f"{NS}:dwarf_merchant", {"PersistenceRequired": Byte(1)})
    # passagem para as minas (norte)
    s.opening(6, 17, 8, 17, FLOOR, 4)


def build_forge(s):
    # Forja: x 47..61, z 17..43, teto em y 11; canal de lava no leste atrás de grades
    s.room(47, 17, 61, 43, 11, wall=DB, floor=DT, ceil=DT)
    s.opening(47, 28, 47, 32, FLOOR, 5)
    for z in range(18, 43):
        s.set(60, 1, z, mc("lava[level=0]"))
        s.set(59, FLOOR, z, mc("iron_bars[waterlogged=false]"))
        s.set(59, FLOOR + 1, z, mc("iron_bars[waterlogged=false]"))
        if z % 4 == 0:
            s.set(61, 5, z, mc("magma_block"))
    for x in range(49, 58, 2):
        s.set(x, FLOOR, 18, mc("blast_furnace[facing=south,lit=true]"))
        s.set(x + 1, FLOOR, 18, mc("smooth_stone"))
        s.set(x + 1, FLOOR + 1, 18, mc("lantern[hanging=false,waterlogged=false]"))
    s.set(51, FLOOR, 27, mc("anvil[facing=east]"))
    s.set(51, FLOOR, 33, mc("chipped_anvil[facing=east]"))
    s.set(49, FLOOR, 36, mc("smithing_table"))
    s.set(49, FLOOR, 37, mc("grindstone[face=floor,facing=east]"))
    s.set(49, FLOOR, 38, mc("water_cauldron[level=3]"))
    s.set(55, FLOOR, 30, FORGE + "[facing=east]")
    for (x, z) in ((54, 29), (56, 29), (54, 31), (56, 31)):
        s.set(x, 1, z, GB)
    s.set(57, FLOOR, 41, mc("coal_block"))
    s.set(57, FLOOR + 1, 41, mc("coal_block"))
    s.set(56, FLOOR, 41, mc("raw_iron_block"))
    s.set(58, FLOOR, 40, mc("iron_block"))
    s.chest(49, FLOOR, 19, "dwarf_hold_forge", "south")
    s.chest(57, FLOOR, 42, "dwarf_hold_forge", "north")
    for z in (23, 30, 37):
        s.hanging_lantern(53, z, 11, 2)
    s.entity(53.5, FLOOR, 32.5, f"{NS}:dwarf_smith", {"PersistenceRequired": Byte(1)})
    s.opening(53, 17, 54, 17, FLOOR, 4)


def build_vault(s):
    # Cofre Selado: x 22..40, z 2..15 (parede sul = parede norte do salão), teto em y 9
    s.room(22, 2, 40, 15, 9, wall=BB, floor=GB, ceil=BB)
    for x in range(23, 40):
        for z in range(3, 15):
            if (x + z) % 3 == 0:
                s.set(x, 1, z, GOLD if (x * 7 + z) % 5 == 0 else PB)
    # porta de ferro + botões (fora e dentro)
    s.set(25, FLOOR, 15, mc("iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]"))
    s.set(25, FLOOR + 1, 15, mc("iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]"))
    s.set(26, FLOOR + 1, 16, mc("stone_button[face=wall,facing=south,powered=false]"))
    s.set(24, FLOOR + 1, 14, mc("stone_button[face=wall,facing=north,powered=false]"))
    s.set(25, FLOOR + 2, 15, CD)
    # tesouros: pilhas de ouro e os baús do fundo
    for (x, z, h) in ((24, 4, 2), (25, 4, 1), (24, 5, 1), (38, 4, 2), (37, 4, 1), (38, 5, 1), (31, 3, 1)):
        for y in range(FLOOR, FLOOR + h):
            s.set(x, y, z, GOLD if y == FLOOR else mc("raw_gold_block"))
    for x in (29, 31, 33):
        s.set(x, FLOOR, 3, CD)
        s.chest(x, FLOOR + 1, 3, "dwarf_hold_vault", "south")
    s.set(31, FLOOR, 8, CD)
    s.set(31, FLOOR + 1, 8, mc("amethyst_cluster[facing=up,waterlogged=false]"))
    for x in (26, 36):
        s.hanging_lantern(x, 7, 9, 1, soul=True)
    # v1.0 — enigma das alavancas: uma grade rúnica separa o tesouro (fundo) da antecâmara; 5 alavancas num painel
    # acendem as 5 lâmpadas engastadas na grade. Todas acesas = a grade inteira some.
    for x in range(23, 40):
        for y in range(FLOOR, 9):
            s.set(x, y, 6, PZ.GATE)
    for x in (27, 29, 31, 33, 35):
        s.set(x, FLOOR + 3, 6, PZ.lamp())
    for x in range(29, 34):
        s.set(x, FLOOR, 9, CD)
        s.set(x, FLOOR + 1, 9, PD)
        s.set(x, FLOOR + 1, 10, PZ.wall_lever("south"))
    PZ.put_core(s, 31, FLOOR + 2, 9, 9)
    # guardiões do cofre
    s.spawner(23, FLOOR, 7, f"{NS}:gargoyle")
    s.spawner(39, FLOOR, 7, "minecraft:skeleton")
    s.set(23, 1, 7, CD)
    s.set(39, 1, 7, CD)


def build_mines(s):
    # Minas Antigas: x 1..21, z 1..17 (parede sul = parede norte do mercado), teto em y 7, paredes rústicas com veios
    s.room(1, 1, 21, 17, 7, wall=COB, floor=mc("deepslate"), ceil=mc("deepslate"))
    rnd = s.rnd
    rough = [mc("deepslate"), COB, mc("tuff"), mc("deepslate"), COB]
    for (x, y, z), (st, _n) in list(s.blocks.items()):
        if x <= 21 and z <= 16 and st == COB:
            s.set(x, y, z, rnd.choice(rough))
    ores = [ORE] * 6 + [mc("deepslate_iron_ore")] * 7 + [mc("deepslate_coal_ore")] * 6 + [mc("deepslate_gold_ore")] * 3
    walls = [(x, y, 1) for x in range(2, 21) for y in range(3, 6)] + [(1, y, z) for z in range(2, 16) for y in range(3, 6)]
    rnd.shuffle(walls)
    for o, (x, y, z) in zip(ores, walls):
        s.set(x, y, z, o)
    # escoras de madeira, trilhos e o carrinho com baú
    for x in (5, 10, 15, 20):
        for z in (2, 15):
            for y in range(FLOOR, 6):
                s.set(x, y, z, mc("dark_oak_log[axis=y]"))
        for z in range(2, 16):
            s.set(x, 6, z, mc("dark_oak_planks"))
        s.set(x, 5, 9, mc("lantern[hanging=true,waterlogged=false]"))
    for x in range(3, 20):
        s.set(x, FLOOR, 9, mc("rail[shape=east_west,waterlogged=false]"))
    s.entity(12.5, FLOOR, 9.5, "minecraft:chest_minecart", {"LootTable": f"{NS}:chests/dwarf_hold_mine"})
    s.chest(19, FLOOR, 3, "dwarf_hold_mine", "south")
    # desabamento parcial
    for (x, z) in ((3, 4), (4, 4), (3, 5), (17, 13), (18, 13), (18, 14)):
        for y in range(FLOOR, FLOOR + rnd.randint(1, 3)):
            s.set(x, y, z, rnd.choice([COB, mc("gravel")]))
    s.opening(6, 17, 8, 17, FLOOR, 4)


def build_quarters(s):
    # Alojamentos: x 41..61, z 1..17 (parede sul = parede norte da forja), teto em y 8
    s.room(41, 1, 61, 17, 8, wall=DB, floor=mc("spruce_planks"), ceil=DT)
    for i, x in enumerate(range(43, 60, 3)):
        s.set(x, FLOOR, 3, mc("red_bed[facing=north,occupied=false,part=head]"))
        s.set(x, FLOOR, 4, mc("red_bed[facing=north,occupied=false,part=foot]"))
        s.set(x + 1, FLOOR, 2, mc("barrel[facing=up,open=false]"))
        if i % 2 == 0:
            s.set(x + 1, FLOOR + 1, 2, mc("candle[candles=2,lit=true,waterlogged=false]"))
    for z in range(9, 14):
        s.set(50, FLOOR, z, mc("spruce_slab[type=top,waterlogged=false]"))
        s.set(49, FLOOR, z, mc("spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
        s.set(51, FLOOR, z, mc("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
    s.set(50, FLOOR + 1, 11, mc("lantern[hanging=false,waterlogged=false]"))
    for z in range(6, 16):
        s.set(60, FLOOR, z, mc("bookshelf"))
        s.set(60, FLOOR + 1, z, mc("bookshelf") if z % 3 else mc("chiseled_bookshelf[facing=west]"))
    s.set(42, FLOOR, 15, mc("furnace[facing=east,lit=false]"))
    s.set(42, FLOOR, 14, mc("crafting_table"))
    s.barrel(42, FLOOR, 13, "dwarf_hold_quarters", "east")
    s.chest(59, FLOOR, 16, "dwarf_hold_quarters", "north")
    for x in (46, 55):
        s.hanging_lantern(x, 9, 8, 1)
    s.opening(53, 17, 54, 17, FLOOR, 4)


def build_city():
    s = City()
    # as alas primeiro: as paredes do Grande Salão (compartilhadas) e as passagens dele ficam por cima
    build_vault(s)
    build_mines(s)
    build_quarters(s)
    build_market(s)
    build_forge(s)
    build_entrance(s)
    build_hall(s)
    # a parede norte do salão cobriu a porta do cofre: recoloca porta e botão de fora
    s.set(25, FLOOR, 15, mc("iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]"))
    s.set(25, FLOOR + 1, 15, mc("iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]"))
    s.set(26, FLOOR + 1, 16, mc("stone_button[face=wall,facing=south,powered=false]"))
    s.set(24, FLOOR + 1, 14, mc("stone_button[face=wall,facing=north,powered=false]"))
    fix_connections(s)
    path = os.path.join(D, "structure/dwarf_hold/city.nbt")
    n = s.save(path, keep_air=True)
    check_city(s)
    print(f"city.nbt: {SX}x{SY}x{SZ}, {n} blocos, {len(s.entities)} entidades")


def check_city(s):
    def err(m):
        ERRORS.append("city: " + m)
    ids = [e["nbt"]["id"] for e in s.entities]
    for need in (f"{NS}:dwarf_merchant", f"{NS}:dwarf_smith"):
        if need not in ids:
            err("falta " + need)
    if not any(st.startswith(FORGE) for st, _ in s.blocks.values()):
        err("sem Forja Rúnica")
    for y in (FLOOR, FLOOR + 1):
        if s.get(31, y, 31) != AIR:
            err(f"centro (31,{y},31) não está livre: {s.get(31, y, 31)}")
    if s.get(31, 1, 31) in (None, AIR):
        err("centro sem chão")
    if not (s.get(25, FLOOR, 15) or "").startswith("minecraft:iron_door"):
        err("porta do cofre sumiu")
    if not any(st.startswith(f"{NS}:depths_puzzle_core") for st, _ in s.blocks.values()):
        err("cofre sem Selo Rúnico (enigma das alavancas)")
    if sum(1 for st, _ in s.blocks.values() if st.startswith("minecraft:lever")) != 5:
        err("o painel do cofre precisa de 5 alavancas")
    vault = [p for p, (st, nbt) in s.blocks.items() if nbt and nbt.get("LootTable", "").endswith("dwarf_hold_vault")]
    if len(vault) != 3:
        err(f"{len(vault)} baús do cofre (esperado 3)")
    for e in s.entities:
        x, y, z = e["pos"]
        if s.get(int(x), int(y), int(z)) not in (AIR, None) and not s.get(int(x), int(y), int(z)).startswith("minecraft:rail"):
            err(f"entidade {e['nbt']['id']} dentro de bloco {s.get(int(x), int(y), int(z))}")


# =================================================================================================
#  WORLDGEN
# =================================================================================================
EXCLUSIONS = [{"other_set": "minecraft:ancient_cities", "chunk_count": 5},
              {"other_set": "minecraft:trial_chambers", "chunk_count": 3}]


def gen_worldgen():
    write_json(os.path.join(D, "worldgen/processor_list/dwarf_hold_age.json"), {"processors": [{
        "processor_type": "minecraft:rule", "rules": [
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": DB, "probability": 0.15},
             "location_predicate": {"predicate_type": "minecraft:always_true"},
             "output_state": {"Name": mc("cracked_deepslate_bricks")}},
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": DT, "probability": 0.08},
             "location_predicate": {"predicate_type": "minecraft:always_true"},
             "output_state": {"Name": mc("cracked_deepslate_tiles")}},
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": PD, "probability": 0.05},
             "location_predicate": {"predicate_type": "minecraft:always_true"},
             "output_state": {"Name": mc("cobbled_deepslate")}}]}]})
    write_json(os.path.join(D, "worldgen/template_pool/dwarf_hold/city.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                               "location": f"{NS}:dwarf_hold/city",
                                               "processors": f"{NS}:dwarf_hold_age", "projection": "rigid"}}]})
    write_json(os.path.join(D, "worldgen/structure/dwarf_hold.json"), {
        "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/dwarf_hold", "step": "underground_structures",
        "terrain_adaptation": "none",
        # cidade segura: monstros não nascem naturalmente dentro dela (só os geradores do cofre)
        "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}},
        "start_pool": f"{NS}:dwarf_hold/city", "size": 1, "start_height": {"absolute": -47},
        "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(D, "worldgen/structure_set/dwarf_hold.json"), {
        "structures": [{"structure": f"{NS}:dwarf_hold", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": 40, "separation": 14, "salt": 1716000062,
                      "exclusion_zones": EXCLUSIONS}})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/dwarf_hold.json"),
               {"values": ["#minecraft:is_overworld"]})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/dwarf_hold.json"), {"values": [f"{NS}:dwarf_hold"]})
    # Mithril: veios pequenos e raros só no deepslate, abaixo de y 0 (mais comum lá embaixo)
    write_json(os.path.join(D, "worldgen/configured_feature/mithril_ore.json"), {
        "type": "minecraft:ore", "config": {"size": 4, "discard_chance_on_air_exposure": 0.5, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
             "state": {"Name": ORE}}]}})
    write_json(os.path.join(D, "worldgen/placed_feature/mithril_ore.json"), {
        "feature": f"{NS}:mithril_ore", "placement": [
            {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -64},
                                                          "max_inclusive": {"absolute": 0}, "plateau": 16}},
            {"type": "minecraft:biome"}]})
    write_json(os.path.join(D, "neoforge/biome_modifier/dwarf_hold_mithril_ore.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"{NS}:mithril_ore",
        "step": "underground_ores"})


# =================================================================================================
#  LOOT
# =================================================================================================
def cnt(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def item(name, w=1, lo=1, hi=1, fns=None):
    e = {"type": "minecraft:item", "name": mc(name), "weight": w}
    f = [cnt(lo, hi)] if (lo, hi) != (1, 1) else []
    if fns:
        f += fns
    if f:
        e["functions"] = f
    return e


def worn(name, w, lo=0.3, hi=0.8):
    return item(name, w, fns=[{"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": lo, "max": hi}}])


def book(w):
    return item("minecraft:book", w, fns=[{"function": "minecraft:enchant_with_levels",
                                            "levels": {"type": "minecraft:uniform", "min": 15, "max": 30}}])


def ess(el, w, lo=1, hi=2):
    return item(f"{NS}:essence_{el}", w, lo, hi)


def pool(lo, hi, entries, chance=None):
    p = {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "bonus_rolls": 0, "entries": entries}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p


M = NS + ":"
RELIC = {"type": "minecraft:loot_table", "value": f"{NS}:dwarf_hold/relic", "weight": 1}
EMPTY = {"type": "minecraft:empty", "weight": 1}
LOOT = {
    "dwarf_hold_market": [pool(3, 6, [item("emerald", 6, 2, 6), item("bread", 4, 2, 5), item("cooked_mutton", 3, 1, 4),
                                       item(M + "mithril_nugget", 5, 2, 6), item(M + "raw_mithril", 3, 1, 2),
                                       item("lantern", 2, 1, 3), ess("earth", 3), ess("fire", 2), item("gold_ingot", 3, 1, 4),
                                       item("torch", 3, 4, 12), item("honey_bottle", 2, 1, 2)])],
    "dwarf_hold_forge": [pool(4, 7, [item("iron_ingot", 6, 2, 8), item("coal", 5, 4, 12), item(M + "mithril_nugget", 5, 3, 8),
                                      item(M + "mithril_ingot", 2, 1, 2), item(M + "raw_mithril", 3, 1, 3), ess("fire", 3),
                                      item("gold_ingot", 2, 1, 3), item("lava_bucket", 1)]),
                         pool(1, 1, [worn(M + "mithril_pickaxe", 2), worn(M + "mithril_sword", 2), worn(M + "mithril_axe", 2),
                                     worn(M + "mithril_helmet", 1), worn(M + "mithril_boots", 1), {**EMPTY, "weight": 6}])],
    "dwarf_hold_vault": [pool(5, 8, [item("gold_ingot", 6, 3, 9), item("gold_block", 2), item("diamond", 4, 1, 3),
                                      item("emerald", 5, 3, 8), item(M + "mithril_ingot", 5, 1, 3), item(M + "raw_mithril", 3, 1, 3),
                                      ess("earth", 2), ess("fire", 2), ess("light", 2), ess("arcane", 2), book(3),
                                      item("golden_apple", 2), item(M + "mana_crystal", 3, 2, 5), item("experience_bottle", 3, 3, 8)]),
                         pool(1, 1, [item(M + "hero_shield", 2), item(M + "appraisal_monocle", 3), item(M + "mithril_war_axe", 3),
                                     item(M + "mithril_chestplate", 2), item(M + "mithril_block", 2), {**EMPTY, "weight": 6}]),
                         pool(1, 1, [RELIC], chance=0.15)],
    "dwarf_hold_mine": [pool(3, 5, [item(M + "raw_mithril", 3, 1, 2), item("raw_iron", 6, 2, 6), item("coal", 6, 3, 10),
                                     item("torch", 4, 4, 12), item("rail", 3, 4, 10), item(M + "mithril_nugget", 4, 2, 5),
                                     worn("iron_pickaxe", 2), item("bread", 2, 1, 3), item("tnt", 1, 1, 2)])],
    "dwarf_hold_quarters": [pool(3, 5, [item("bread", 5, 2, 4), item("honey_bottle", 3, 1, 2), item("book", 2, 1, 3),
                                         item("emerald", 3, 1, 4), item("candle", 2, 1, 3), item(M + "mithril_nugget", 2, 1, 3),
                                         item("clock", 1), ess("earth", 1)])],
}
SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def self_drop(bid):
    return {"type": "minecraft:block", "random_sequence": f"{NS}:blocks/{bid}", "pools": [{
        "rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def gen_loot():
    for name, pools in LOOT.items():
        write_json(os.path.join(D, f"loot_table/chests/{name}.json"),
                   {"type": "minecraft:chest", "pools": pools, "random_sequence": f"{NS}:chests/{name}"})
    # relíquia rara da raça Anã (do módulo character; só se existir)
    write_json(os.path.join(D, "loot_table/dwarf_hold/relic.json"), {
        "neoforge:conditions": [{"type": "neoforge:item_exists", "item": f"{NS}:relic_dwarven_rune"}],
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:relic_dwarven_rune"}]}]})
    fortune = {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}
    write_json(os.path.join(D, "loot_table/blocks/deepslate_mithril_ore.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/deepslate_mithril_ore", "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": ORE, "conditions": [SILK]},
                {"type": "minecraft:item", "name": f"{NS}:raw_mithril",
                 "functions": [fortune, {"function": "minecraft:explosion_decay"}]}]}]},
            # 0,5%: a Runa Anã (relíquia) escondida no veio
            {"rolls": 1, "bonus_rolls": 0, "entries": [RELIC],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.005}]}]})
    for b in ("mithril_block", "raw_mithril_block", "dwarf_rune_forge"):
        write_json(os.path.join(D, f"loot_table/blocks/{b}.json"), self_drop(b))


# =================================================================================================
#  RECEITAS
# =================================================================================================
def ing(i):
    return {"tag": i[1:]} if i.startswith("#") else {"item": mc(i)}


def shaped(name, pattern, key, result, count=1, category="equipment"):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items()}, "result": {"id": mc(result), "count": count}})


def shapeless(name, items, result, count=1):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [ing(i) for i in items],
        "result": {"id": mc(result), "count": count}})


def cook(name, kind, inp, result, xp, time):
    write_json(os.path.join(D, f"recipe/{name}.json"), {
        "type": f"minecraft:{kind}", "category": "misc", "ingredient": ing(inp), "result": {"id": mc(result)},
        "experience": xp, "cookingtime": time})


def gen_recipes():
    I, N, R = M + "mithril_ingot", M + "mithril_nugget", M + "raw_mithril"
    for src, tag in ((R, "raw"), (ORE, "ore")):
        cook(f"mithril_ingot_from_smelting_{tag}", "smelting", src, I, 1.0, 200)
        cook(f"mithril_ingot_from_blasting_{tag}", "blasting", src, I, 1.0, 100)
    shapeless("mithril_ingot_from_nuggets", [N] * 9, I)
    shapeless("mithril_nugget_from_ingot", [I], N, 9)
    shapeless("mithril_block_from_ingots", [I] * 9, M + "mithril_block")
    shapeless("mithril_ingot_from_block", [M + "mithril_block"], I, 9)
    shapeless("raw_mithril_block_from_raw", [R] * 9, M + "raw_mithril_block")
    shapeless("raw_mithril_from_block", [M + "raw_mithril_block"], R, 9)
    S = "stick"
    shaped("mithril_sword", ["M", "M", "S"], {"M": I, "S": S}, M + "mithril_sword")
    shaped("mithril_pickaxe", ["MMM", " S ", " S "], {"M": I, "S": S}, M + "mithril_pickaxe", category="equipment")
    shaped("mithril_axe", ["MM", "MS", " S"], {"M": I, "S": S}, M + "mithril_axe")
    shaped("mithril_shovel", ["M", "S", "S"], {"M": I, "S": S}, M + "mithril_shovel")
    shaped("mithril_hoe", ["MM", " S", " S"], {"M": I, "S": S}, M + "mithril_hoe")
    shaped("mithril_war_axe", ["MMM", "MSM", " S "], {"M": I, "S": S}, M + "mithril_war_axe")
    shaped("mithril_helmet", ["MMM", "M M"], {"M": I}, M + "mithril_helmet")
    shaped("mithril_chestplate", ["M M", "MMM", "MMM"], {"M": I}, M + "mithril_chestplate")
    shaped("mithril_leggings", ["MMM", "M M", "M M"], {"M": I}, M + "mithril_leggings")
    shaped("mithril_boots", ["M M", "M M"], {"M": I}, M + "mithril_boots")
    shaped("dwarf_rune_forge", ["MMM", " A ", "PBP"], {"M": I, "A": "anvil", "P": "polished_deepslate", "B": "blast_furnace"},
           M + "dwarf_rune_forge", category="misc")


# =================================================================================================
#  TAGS (merge)
# =================================================================================================
def gen_tags():
    blocks = [ORE, M + "mithril_block", M + "raw_mithril_block", FORGE]
    t = os.path.join(MERGE, "data")
    write_json(os.path.join(t, "minecraft/tags/block/mineable/pickaxe.json"), {"values": blocks})
    write_json(os.path.join(t, "minecraft/tags/block/needs_iron_tool.json"), {"values": [ORE, M + "mithril_block", M + "raw_mithril_block"]})
    write_json(os.path.join(t, "minecraft/tags/block/beacon_base_blocks.json"), {"values": [M + "mithril_block"]})
    write_json(os.path.join(t, "c/tags/block/ores.json"), {"values": [ORE]})
    write_json(os.path.join(t, "c/tags/item/ores.json"), {"values": [ORE]})
    write_json(os.path.join(t, "c/tags/item/ingots.json"), {"values": [M + "mithril_ingot"]})
    write_json(os.path.join(t, "c/tags/item/nuggets.json"), {"values": [M + "mithril_nugget"]})
    write_json(os.path.join(t, "c/tags/item/raw_materials.json"), {"values": [M + "raw_mithril"]})
    write_json(os.path.join(t, "c/tags/item/tools/shield.json"), {"values": [M + "hero_shield"]})
    write_json(os.path.join(t, "minecraft/tags/item/swords.json"), {"values": [M + "mithril_sword"]})
    write_json(os.path.join(t, "minecraft/tags/item/pickaxes.json"), {"values": [M + "mithril_pickaxe"]})
    write_json(os.path.join(t, "minecraft/tags/item/axes.json"), {"values": [M + "mithril_axe", M + "mithril_war_axe"]})
    write_json(os.path.join(t, "minecraft/tags/item/shovels.json"), {"values": [M + "mithril_shovel"]})
    write_json(os.path.join(t, "minecraft/tags/item/hoes.json"), {"values": [M + "mithril_hoe"]})
    write_json(os.path.join(t, "minecraft/tags/item/head_armor.json"), {"values": [M + "mithril_helmet"]})
    write_json(os.path.join(t, "minecraft/tags/item/chest_armor.json"), {"values": [M + "mithril_chestplate"]})
    write_json(os.path.join(t, "minecraft/tags/item/leg_armor.json"), {"values": [M + "mithril_leggings"]})
    write_json(os.path.join(t, "minecraft/tags/item/foot_armor.json"), {"values": [M + "mithril_boots"]})
    write_json(os.path.join(t, "minecraft/tags/item/trimmable_armor.json"),
               {"values": [M + f"mithril_{p}" for p in ("helmet", "chestplate", "leggings", "boots")]})
    write_json(os.path.join(t, "minecraft/tags/item/enchantable/durability.json"), {"values": [M + "hero_shield"]})
    write_json(os.path.join(t, "minecraft/tags/item/beacon_payment_items.json"), {"values": [M + "mithril_ingot"]})
    write_json(os.path.join(t, "curios/tags/item/charm.json"), {"values": [M + "appraisal_monocle"]})


# =================================================================================================
#  DIÁRIO
# =================================================================================================
def gen_journal():
    loc = {"structure": f"{NS}:dwarf_hold"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 62) para o meio da história (Ato IV); XP na escala dos vizinhos.
    # Obrigatório só o essencial (achar, negociar, gravar uma runa); minerar 3 minérios virou bônus.
    write_json(os.path.join(D, "journal/dwarf_hold.json"), {"chapters": [{
        "id": "dwarf_hold", "order": 34, "color": "#E0A040", "intro": True,
        "icon": [M + "mithril_ingot", M + "dwarf_rune_forge", M + "hero_shield"],
        "steps": [
            {"id": "enter", "condition": {"type": "flag", "flag": "dwarf_hold.entered"}, "xp": 200,
             "icon": M + "mithril_block", "locate": loc},
            {"id": "trade", "condition": {"type": "flag", "flag": "dwarf_hold.trade"}, "xp": 100, "icon": "minecraft:emerald",
             "locate": loc},
            {"id": "mithril", "condition": {"type": "counter", "key": "dwarf_hold.mithril", "min": 3}, "optional": True, "xp": 200,
             "icon": M + "raw_mithril"},
            {"id": "rune", "condition": {"type": "flag", "flag": "dwarf_hold.rune"}, "xp": 250, "icon": M + "dwarf_rune_forge",
             "locate": loc},
            {"id": "treasure", "condition": {"type": "any", "conditions": [
                {"type": "item", "item": M + "hero_shield"}, {"type": "item", "item": M + "appraisal_monocle"}]},
             "optional": True, "xp": 400, "icon": M + "hero_shield", "locate": loc}],
        "rewards": {"xp": 1500, "items": [{"id": M + "mithril_ingot", "count": 4}], "glyph_scrolls": 1, "glyph_tier": 3}}]})


# =================================================================================================
#  TRADUÇÕES
# =================================================================================================
PT, EN = {}, {}


def tr(k, pt, en):
    PT[k] = pt
    EN[k] = en


def gen_lang():
    i, b, e = f"item.{NS}.", f"block.{NS}.", f"entity.{NS}."
    tr(b + "deepslate_mithril_ore", "Minério de Mithril de Ardósia", "Deepslate Mithril Ore")
    tr(b + "mithril_block", "Bloco de Mithril", "Block of Mithril")
    tr(b + "raw_mithril_block", "Bloco de Mithril Bruto", "Block of Raw Mithril")
    tr(b + "dwarf_rune_forge", "Forja Rúnica", "Rune Forge")
    tr(i + "raw_mithril", "Mithril Bruto", "Raw Mithril")
    tr(i + "mithril_ingot", "Lingote de Mithril", "Mithril Ingot")
    tr(i + "mithril_nugget", "Pepita de Mithril", "Mithril Nugget")
    tr(i + "mithril_sword", "Espada de Mithril", "Mithril Sword")
    tr(i + "mithril_pickaxe", "Picareta de Mithril", "Mithril Pickaxe")
    tr(i + "mithril_axe", "Machado de Mithril", "Mithril Axe")
    tr(i + "mithril_shovel", "Pá de Mithril", "Mithril Shovel")
    tr(i + "mithril_hoe", "Enxada de Mithril", "Mithril Hoe")
    tr(i + "mithril_war_axe", "Machado de Guerra Anão", "Dwarven War Axe")
    tr(i + "mithril_helmet", "Elmo de Mithril", "Mithril Helmet")
    tr(i + "mithril_chestplate", "Peitoral de Mithril", "Mithril Chestplate")
    tr(i + "mithril_leggings", "Calças de Mithril", "Mithril Leggings")
    tr(i + "mithril_boots", "Botas de Mithril", "Mithril Boots")
    tr(i + "hero_shield", "Escudo do Herói", "Hero's Shield")
    tr(i + "appraisal_monocle", "Monóculo de Avaliação", "Appraisal Monocle")
    tr(i + "dwarf_merchant_spawn_egg", "Ovo Gerador de Comerciante Anão", "Dwarf Merchant Spawn Egg")
    tr(i + "dwarf_smith_spawn_egg", "Ovo Gerador de Ferreiro Anão", "Dwarf Smith Spawn Egg")
    tr(e + "dwarf_merchant", "Comerciante Anão", "Dwarf Merchant")
    tr(e + "dwarf_smith", "Ferreiro Anão", "Dwarf Smith")

    t = f"tooltip.{NS}.dwarf_hold."
    tr(t + "mithril_set", "Bênção da Montanha (conjunto completo)", "Blessing of the Mountain (full set)")
    tr(t + "mithril_set.desc", "Pressa I abaixo de y 0. Runas neste equipamento valem ×1,5.",
       "Haste I below y 0. Runes on this gear are ×1.5 stronger.")
    tr(t + "hero_shield.desc", "\"Eu não ataco. Eu protejo — e devolvo.\"", "\"I don't attack. I protect — and give it back.\"")
    tr(t + "hero_shield.reflect", "Devolve %s%% do dano de golpes corpo a corpo bloqueados", "Reflects %s%% of blocked melee damage")
    tr(t + "hero_shield.rage", "Escudo da Ira: com vida ≤ %s%%", "Wrath Shield: at ≤ %s%% health")
    tr(t + "hero_shield.rage.desc", "devolve %s%% e incendeia quem bate (custa %s de mana por golpe)",
       "reflects %s%% and burns the attacker (costs %s mana per hit)")
    tr(t + "appraisal_monocle.desc", "\"Avaliação!\" — todo isekai começa assim.", "\"Appraisal!\" — every isekai starts like this.")
    tr(t + "appraisal_monocle.use", "Talismã: mostra o nível de ameaça, vida e fraquezas do alvo e o valor dos itens.",
       "Charm: shows the target's threat rank, health and weaknesses, and item values.")
    tr(t + "rune", "Runa: %s", "Rune: %s")

    r = f"rune.{NS}.dwarf_hold."
    runes = {
        "ember": ("Runa da Brasa", "Ember Rune", "+1,5 de dano", "+1.5 attack damage", "+1 de armadura", "+1 armor",
                  "+1 de dano", "+1 attack damage"),
        "tide": ("Runa da Maré", "Tide Rune", "+0,5 de alcance de ataque", "+0.5 attack reach", "+2 de vida máxima",
                 "+2 max health", "+1 de alcance de mineração", "+1 block reach"),
        "mountain": ("Runa da Montanha", "Mountain Rune", "+0,5 de repulsão", "+0.5 knockback", "+1 de resistência de armadura",
                     "+1 armor toughness", "+4 de eficiência de mineração", "+4 mining efficiency"),
        "gale": ("Runa do Vendaval", "Gale Rune", "+0,2 de velocidade de ataque", "+0.2 attack speed",
                 "+3% de velocidade", "+3% movement speed", "+2 de eficiência de mineração", "+2 mining efficiency"),
        "dawn": ("Runa da Aurora", "Dawn Rune", "+1 de sorte", "+1 luck", "+0,5 de sorte", "+0.5 luck", "+1 de sorte", "+1 luck"),
        "shade": ("Runa da Sombra", "Shade Rune", "+25% de dano em área (varrer)", "+25% sweeping damage",
                  "+10% de velocidade agachado", "+10% sneaking speed", "+50% de mineração submerso", "+50% underwater mining"),
        "arcane": ("Runa Arcana", "Arcane Rune", "+4% de poder mágico", "+4% spell power", "+10 de mana máxima", "+10 max mana",
                   "+5% de regeneração de mana", "+5% mana regen"),
    }
    for rid, (pn, en, pw, ew, pa, ea, pt_, et) in runes.items():
        tr(r + rid, pn, en)
        tr(r + rid + ".weapon", "Arma: " + pw, "Weapon: " + ew)
        tr(r + rid + ".armor", "Armadura/escudo: " + pa, "Armor/shield: " + ea)
        tr(r + rid + ".tool", "Ferramenta: " + pt_, "Tool: " + et)

    m = f"message.{NS}.dwarf_hold."
    f = m + "forge."
    tr(f + "need_essence", "Segure uma Essência na mão secundária para escolher a runa.",
       "Hold an Essence in your off hand to choose the rune.")
    tr(f + "same", "Esta peça já tem a %s.", "This piece already bears the %s.")
    tr(f + "need_mithril", "A gravação custa %s Lingotes de Mithril.", "Engraving costs %s Mithril Ingots.")
    tr(f + "need_xp", "A gravação custa %s níveis de experiência.", "Engraving costs %s experience levels.")
    tr(f + "done", "%s gravada em %s!", "%s engraved on %s!")
    tr(f + "replaced", "A runa antiga se apaga... %s gravada em %s!", "The old rune fades... %s engraved on %s!")
    tr(f + "mithril_bonus", " (×1,5 no Mithril)", " (×1.5 on Mithril)")
    tr(f + "help.title", "Forja Rúnica", "Rune Forge")
    tr(f + "help", "Mão principal: arma, armadura, escudo ou ferramenta. Mão secundária: uma Essência. Custo: %s Lingotes de "
                   "Mithril e %s níveis. Uma runa por peça (gravar outra substitui).",
       "Main hand: weapon, armor, shield or tool. Off hand: an Essence. Cost: %s Mithril Ingots and %s levels. "
       "One rune per piece (engraving another replaces it).")
    tr(f + "help.line", "%s · %s · %s · %s", "%s · %s · %s · %s")
    tr(f + "help.mithril", "Equipamento de Mithril amplifica as runas em 50%.", "Mithril gear amplifies runes by 50%.")
    tr(m + "kin", "\"Um parente da montanha! Para você, preço de família.\" (−25%)",
       "\"A kinsman of the mountain! Family prices for you.\" (−25%)")
    tr(m + "greet.merchant", "\"Mithril, essências, coisas raras... tudo tem preço, forasteiro.\"",
       "\"Mithril, essences, rare things... everything has a price, outsider.\"")
    tr(m + "greet.smith", "\"Traga Mithril e eu faço dele uma lenda.\"", "\"Bring me Mithril and I'll make it a legend.\"")
    tr(m + "hit", "\"Ei! Guarde esse ferro para os monstros!\"", "\"Hey! Save that steel for the monsters!\"")
    tr(m + "found", "Cidade Anã Perdida", "Lost Dwarven City")
    tr(m + "found.title_sub", "Os salões de Khaz-Durim", "The halls of Khaz-Durim")
    tr(m + "found.sub", "Você encontrou a Cidade Anã Perdida! Procure o Comerciante, o Ferreiro e a Forja Rúnica.",
       "You found the Lost Dwarven City! Look for the Merchant, the Smith and the Rune Forge.")

    h = f"hud.{NS}.dwarf_hold.appraisal."
    tr(h + "title", "✦ Avaliação", "✦ Appraisal")
    tr(h + "rank", "Ameaça: %s", "Threat: %s")
    tr(h + "player", "Aventureiro", "Adventurer")
    tr(h + "level_hidden", "Nível de XP: %s", "XP level: %s")
    tr(h + "health", "Vida: %s", "Health: %s")
    tr(h + "stats", "Armadura %s · Ataque %s", "Armor %s · Attack %s")
    tr(h + "weak", "Fraco: %s", "Weak: %s")
    tr(h + "resist", "Resiste: %s", "Resists: %s")
    tr(h + "immune", "Imune: %s", "Immune: %s")
    tr(h + "neutral", "Sem fraquezas elementais", "No elemental weaknesses")
    tr(h + "fire_immune", "Imune a fogo", "Fire immune")
    tr(h + "value", "Avaliação: ~%s esmeraldas", "Appraisal: ~%s emeralds")
    tr(h + "value.none", "Avaliação: sem valor comercial", "Appraisal: no market value")

    c = f"command.{NS}.dwarf_hold."
    tr(c + "give", "Kit do dwarf_hold entregue (Mithril, forja e tesouros).", "dwarf_hold kit given (Mithril, forge and treasures).")
    tr(c + "none", "Nenhuma Cidade Anã Perdida encontrada por perto.", "No Lost Dwarven City found nearby.")
    tr(c + "found", "Cidade Anã Perdida: %s %s %s (%s blocos) — clique para teleportar",
       "Lost Dwarven City: %s %s %s (%s blocks) — click to teleport")
    tr(c + "tp", "Teleportado para o Grande Salão da Cidade Anã.", "Teleported to the Great Hall of the Dwarven City.")
    tr(c + "rune", "%s gravada em %s.", "%s engraved on %s.")
    tr(c + "rune.fail", "Runa desconhecida ou o item da mão não aceita runas.", "Unknown rune or the held item can't take runes.")

    j = f"journal.{NS}.dwarf_hold."
    tr(j + "title", "A Cidade Anã Perdida", "The Lost Dwarven City")
    tr(j + "intro", "Dizem que os anões nunca foram embora. Só desceram mais fundo do que alguém teve coragem de cavar.",
       "They say the dwarves never left. They just dug deeper than anyone dared to follow.")
    tr(j + "lore", "Bem abaixo do deepslate, em torno de y −45, há salões de pedra que nenhum mapa mostra. Lá ainda se forja o "
                   "Mithril — e ainda se gravam runas.",
       "Far below the deepslate, around y −45, lie stone halls no map shows. Mithril is still forged there — and runes "
       "still engraved.")
    tr(j + "outro", "Você fez negócio com os anões e carrega as runas deles. Para um anão, isso é quase ser da família.",
       "You traded with the dwarves and carry their runes. To a dwarf, that's almost family.")
    steps = {
        "enter": ("Encontre a Cidade Anã Perdida", "Find the Lost Dwarven City",
                  "Fica no deepslate (y ≈ −45). O portão sul desabou: escave pelo entulho ou chegue por uma caverna.",
                  "It lies in the deepslate (y ≈ −45). The south gate collapsed: dig through the rubble or come in from a cave.",
                  "O portão de pedra ainda está de pé. A grade nunca foi baixada.",
                  "The stone gate still stands. The portcullis was never lowered.", "y ≈ −45"),
        "trade": ("Negocie com um anão", "Trade with a dwarf",
                  "O Comerciante fica no mercado (ala oeste); o Ferreiro, na forja (ala leste). Anões de raça ganham desconto.",
                  "The Merchant is in the market (west wing); the Smith, in the forge (east wing). Dwarf-race players get a discount.",
                  "Anões não esquecem um bom freguês. Nem um mau.", "Dwarves never forget a good customer. Or a bad one.",
                  "Mercado / Forja"),
        "mithril": ("Minere 3 Minérios de Mithril", "Mine 3 Mithril Ores",
                    "Só no deepslate, abaixo de y 0 (mais comum perto de y −48). Picareta de ferro ou melhor. As Minas Antigas da "
                    "cidade ainda têm alguns veios.",
                    "Only in deepslate, below y 0 (most common near y −48). Iron pickaxe or better. The city's Old Mines still "
                    "have a few veins.",
                    "Mais leve que o aço, mais duro que o diamante, e canta quando encontra uma runa.",
                    "Lighter than steel, harder than diamond, and it sings when it meets a rune.", "y < 0"),
        "rune": ("Grave uma runa na Forja Rúnica", "Engrave a rune at the Rune Forge",
                 "Mão principal: a peça. Mão secundária: uma Essência (cada essência é uma runa). Custa 2 Lingotes de Mithril e "
                 "3 níveis. Clique sem nada na mão para ver todas as runas.",
                 "Main hand: the piece. Off hand: an Essence (each essence is a rune). Costs 2 Mithril Ingots and 3 levels. "
                 "Click empty-handed to see every rune.",
                 "Uma runa não é enfeite. É uma promessa gravada em metal.", "A rune is no ornament. It's a promise carved in metal.",
                 "Forja"),
        "treasure": ("Conquiste um tesouro anão", "Claim a dwarven treasure",
                     "O Escudo do Herói e o Monóculo de Avaliação aparecem no Cofre Selado (porta de ferro atrás do trono — "
                     "cuidado com os guardiões) ou com os anões, por um preço alto.",
                     "The Hero's Shield and the Appraisal Monocle can be found in the Sealed Vault (iron door behind the "
                     "throne — beware its guardians) or bought from the dwarves at a steep price.",
                     "O rei guardou ali o que nem os anões sabiam forjar duas vezes.",
                     "The king kept there what not even dwarves could forge twice.", "Cofre"),
    }
    for sid, (pn, en, ph, eh, pl, el, tip) in steps.items():
        tr(j + sid, pn, en)
        tr(j + sid + ".hint", ph, eh)
        tr(j + sid + ".lore", pl, el)
        tr(j + sid + ".tip", tip, tip.replace("Mercado / Forja", "Market / Forge").replace("Forja", "Forge").replace("Cofre", "Vault"))

    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(PT.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))


def main():
    build_city()
    gen_worldgen()
    gen_loot()
    gen_recipes()
    gen_tags()
    gen_journal()
    gen_lang()
    if ERRORS:
        print("ERROS:")
        for e in ERRORS:
            print("  " + e)
        sys.exit(1)
    print("OK: molde, worldgen, loot, receitas, tags, diário e traduções do dwarf_hold.")


if __name__ == "__main__":
    main()
