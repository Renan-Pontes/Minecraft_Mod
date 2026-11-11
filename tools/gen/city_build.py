"""Gera as CIDADES do Isekai Mahou (trabalho "city_build" da v0.8 + A3 da v0.9): os templates .nbt das peças da cidade
nas 3 paletas (plains = Reino de Aldória, desert = Império de Sahra, snowy = Reino de Nordheim), o loot dos baús, o
worldgen (structure / structure_set das cidades e das 3 capitais), as tags e as traduções. Depois de gerar, LÊ OS .nbt
DE VOLTA e confere tamanhos, blocos, entidades, loot, traduções e o Altar de Ascensão pronto (padrão e NBT).
v0.9: city_heart com {Kingdom}; Altar de Ascensão público ({PublicAltar:1b}) na Igreja e na torre da Academia;
Pedra de Teleporte (isekai_mahou:travel_stone) na praça. Ver docs/v09/API_worldgen.md.

Uso (na raiz do projeto):   py -I tools/gen/city_build.py [--preview NOME_DA_PECA] [--check-only]

A cidade é montada em Java (feature/cities/world): muralha, ruas, praça e lotes numa grade; cada lote recebe um
destes templates (data/isekai_mahou/structure/city/<paleta>/<peça>.nbt). TAMANHOS (iguais aos de CityPlanner.java):
  lote pequeno  S = 11 x 11   casas, lojas, beco, estábulo, horta, poço
  lote grande   L = 22 x 22   Guilda, taverna, mercado, academia, igreja, quartel, mansão, parque
  praça         51 x 51       centro da cidade: chafariz + city_heart + quadro do Reino
  castelo       51 x 49       só na capital, ao norte da praça
  portão        15 x 9        (ao longo da muralha x para fora); torres 9 x 9
Convenções: x = leste, z = sul, y = cima. A FRENTE de toda peça é o lado SUL (z máximo): o Java gira a peça para a
frente dar para a rua (as torres/portões: o sul é o lado de FORA da muralha). O piso fica em y=0 = nível da rua,
exceto no beco (porão: a rua fica em y=5, ver GROUND_OFFSET, igual a CityPlanner.GROUND_OFFSET).
Escadas: "facing" = lado alto; camas = do pé para a cabeceira; tochas/placas/estandartes de parede = para onde
apontam (o apoio fica do lado oposto). O ar NÃO é salvo (o Java já limpa o volume da cidade antes das peças), exceto
onde o template cava abaixo da rua (porão do beco).
"""
import gzip
import json
import math
import os
import random
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte, Double, Float, Int  # noqa: E402

NS = "isekai_mahou"
D = os.path.join(ROOT, "src/main/resources/data", NS)
STRUCT_OUT = os.path.join(D, "structure", "city")
LOOT_OUT = os.path.join(D, "loot_table", "chests", "city")
LANG_DIR = os.path.join(ROOT, "src/main/lang/cities_world")
MCSRC = os.path.join(ROOT, "build/mcsrc/net/minecraft/world")

S_LOT, L_LOT = 11, 22
PLAZA = 51
CASTLE = (51, 49)
GATE = (15, 9)
TOWER = 9
GROUND_OFFSET = {"alley": 5}
WALK = 8            # altura em que se anda em cima da muralha (o Java faz o corpo da muralha em y1..7)


def city_radius(rings):
    """Meia largura da cidade até a ponta das torres (igual a CityGeometry.towerOut no Java)."""
    block_end = 3 + 1 + (rings - 1) * 27 + 21
    return block_end + 5 + 1 + 8


# =================================================================================================
#  ESTADOS DE BLOCO
# =================================================================================================
DIRV = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}
AIR = "minecraft:air"


def mc(n):
    return n if ":" in n else "minecraft:" + n


def st(name, **props):
    name = mc(name)
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def stairs(block, facing, half="bottom", shape="straight"):
    return st(block, facing=facing, half=half, shape=shape, waterlogged="false")


def slab(block, t="bottom"):
    return st(block, type=t, waterlogged="false")


def log(block, axis="y"):
    return st(block, axis=axis)


def lantern(hanging=False, soul=False):
    return st("soul_lantern" if soul else "lantern", hanging="true" if hanging else "false", waterlogged="false")


def wall_torch(facing, soul=False):
    return st("soul_wall_torch" if soul else "wall_torch", facing=facing)


def campfire(lit=True, soul=False, facing="north"):
    return st("soul_campfire" if soul else "campfire", facing=facing, lit="true" if lit else "false",
              signal_fire="false", waterlogged="false")


def ladder(facing):
    return st("ladder", facing=facing, waterlogged="false")


def candle(n=1, color=None, lit=True):
    return st(f"{color}_candle" if color else "candle", candles=n, lit="true" if lit else "false",
              waterlogged="false")


def pane(color=None):
    return mc(f"{color}_stained_glass_pane" if color else "glass_pane")


def leaves(block):
    return st(block, distance=1, persistent="true", waterlogged="false")


def trapdoor(wood, facing, half="bottom", is_open=False):
    return st(f"{wood}_trapdoor", facing=facing, half=half, open="true" if is_open else "false", powered="false",
              waterlogged="false")


def fence_gate(wood, facing):
    return st(f"{wood}_fence_gate", facing=facing, in_wall="false", open="false", powered="false")


def chain():
    return st("chain", axis="y", waterlogged="false")


def water():
    return st("water", level=0)


def plate(wood):
    return st(f"{wood}_pressure_plate", powered="false")


def wall_skull(kind, facing):
    name = {"skeleton": "skeleton_wall_skull", "wither": "wither_skeleton_wall_skull", "zombie": "zombie_wall_head",
            "creeper": "creeper_wall_head", "dragon": "dragon_wall_head", "piglin": "piglin_wall_head"}[kind]
    return st(name, facing=facing)


def potted(plant):
    return mc("potted_" + plant)


ROT16 = {"south": 0, "west": 4, "north": 8, "east": 12}

# Estandartes (padrões do registro minecraft:banner_pattern).
GUILD_BANNER = ("blue", [("border", "yellow"), ("cross", "light_blue"), ("circle", "yellow"),
                         ("curly_border", "blue")])
KINGDOM_BANNER = ("blue", [("rhombus", "yellow"), ("circle", "blue"), ("triangles_top", "yellow"),
                           ("border", "yellow")])
# v0.9: cada reino tem o seu estandarte (as peças usam P.banner, o da paleta = do reino).
SAHRA_BANNER = ("orange", [("gradient_up", "yellow"), ("circle", "yellow"), ("triangles_bottom", "red"),
                           ("border", "yellow")])
NORDHEIM_BANNER = ("light_blue", [("straight_cross", "white"), ("flower", "cyan"), ("triangles_top", "white"),
                                  ("border", "white")])
ACADEMY_BANNER = ("purple", [("globe", "light_blue"), ("border", "black"), ("stripe_bottom", "magenta")])
CHURCH_BANNER = ("white", [("circle", "yellow"), ("straight_cross", "orange"), ("circle", "yellow"),
                           ("border", "yellow")])
UNDER_BANNER = ("black", [("skull", "red"), ("border", "gray")])

ROOF_DARK_OAK = ("minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_planks")
ROOF_SPRUCE = ("minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_planks")
ROOF_BRICK = ("minecraft:brick_stairs", "minecraft:brick_slab", "minecraft:bricks")
ROOF_DEEPSLATE = ("minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab", "minecraft:deepslate_tiles")


# Altar de Ascensão PRONTO (blocos do módulo ascension) e Pedra de Teleporte (módulo travel), por id.
ASCENSION_CORE = f"{NS}:ascension_core"
ASCENSION_PEDESTAL = f"{NS}:ascension_pedestal"
RUNE_STONE = f"{NS}:rune_stone"
TRAVEL_STONE = f"{NS}:travel_stone"
# Padrão do AscensionPattern (feature/ascension), relativo ao núcleo e na mesma altura dele, completo até o Círculo 10:
# pedestais em cruz a 2 e nas diagonais a 3; Pedras Rúnicas nas diagonais a 2, em cruz a 3 e no anel (4,±2)/(±2,4).
# Os 4 cantos (±4,±4) ficam sem cristal (v0.9: o anel 9x9 não usa mais Blocos de Cristal de Mana).
ALTAR_PEDESTALS = [(2, 0), (-2, 0), (0, 2), (0, -2), (3, 3), (3, -3), (-3, 3), (-3, -3)]
ALTAR_RUNES = [(2, 2), (2, -2), (-2, 2), (-2, -2), (3, 0), (-3, 0), (0, 3), (0, -3),
               (4, 2), (4, -2), (-4, 2), (-4, -2), (2, 4), (-2, 4), (2, -4), (-2, -4)]
ALTAR_FREE_CORNERS = [(4, 4), (4, -4), (-4, 4), (-4, -4)]
# "PublicAltar" no topo (lido pelo BlockEntity do núcleo) e em NeoForgeData (getPersistentData sobrevive no mundo
# mesmo se o BlockEntity não gravar a chave).
ALTAR_TEMPLATES = ("church", "academy")


def public_altar_nbt():
    return {"PublicAltar": Byte(1), "NeoForgeData": {"PublicAltar": Byte(1)}}


# =================================================================================================
#  PALETAS (por bioma do centro da cidade — escolhidas em CityPalette.java; o reino vem da paleta)
# =================================================================================================
class Pal:
    def __init__(self, name, **kw):
        self.name = name
        self.__dict__.update(kw)

    def post(self, axis="y"):
        return log(self.post_log, axis) if self.timber else self.corner

    def beam(self, axis):
        return log(self.beam_log, axis) if self.timber else self.band

    def w(self, kind, wood=None, **props):
        """Bloco de madeira da paleta: w("fence"), w("stairs", facing=...), w("planks", wood=P.wood2)..."""
        return st(f"{wood or self.wood}_{kind}", **props)


PLAINS = Pal(
    "plains", kingdom="aldoria", banner=KINGDOM_BANNER, timber=True, flat=False, steep=False, shutters=True,
    base="minecraft:cobblestone", base2="minecraft:stone_bricks", wall="minecraft:white_terracotta",
    post_log="minecraft:stripped_dark_oak_log", beam_log="minecraft:stripped_dark_oak_log", corner=None, band=None,
    civic="minecraft:stone_bricks", civic2="minecraft:polished_andesite", trim="minecraft:chiseled_stone_bricks",
    floor="minecraft:spruce_planks", floor2="minecraft:dark_oak_planks", tile="minecraft:polished_andesite",
    wood="spruce", wood2="dark_oak",
    roofs=[ROOF_DARK_OAK, ROOF_BRICK, ROOF_SPRUCE], roof_civic=ROOF_DEEPSLATE, roof_flat=None,
    window="minecraft:glass_pane", path="minecraft:dirt_path", ground="minecraft:grass_block",
    pave=[("minecraft:stone_bricks", 50), ("minecraft:cobblestone", 14), ("minecraft:andesite", 14),
          ("minecraft:mossy_stone_bricks", 10), ("minecraft:cracked_stone_bricks", 8),
          ("minecraft:polished_andesite", 4)],
    pave_main="minecraft:polished_andesite", pave_accent="minecraft:stone_bricks", curb="minecraft:cobblestone",
    rim="minecraft:polished_andesite",
    stone_stairs="minecraft:stone_brick_stairs", stone_slab="minecraft:stone_brick_slab",
    stone_wall="minecraft:stone_brick_wall", smooth_slab="minecraft:smooth_stone_slab",
    tree="oak", tree_log="minecraft:oak_log", leaves="minecraft:oak_leaves",
    flowers=["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium",
             "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip"],
    fence="minecraft:spruce_fence",
    awnings=[("red", "white"), ("blue", "white"), ("yellow", "white"), ("green", "white"), ("orange", "white"),
             ("purple", "white")],
    glass=["yellow", "orange", "white", "light_blue"], chimney="minecraft:bricks",
    dome=None, carpet="red", carpet2="blue",
    cw=[("minecraft:stone_bricks", 70), ("minecraft:mossy_stone_bricks", 12), ("minecraft:cracked_stone_bricks", 10),
        ("minecraft:cobblestone", 8)],
    cw_top="minecraft:stone_bricks", cw_wall="minecraft:stone_brick_wall",
)

DESERT = Pal(
    "desert", kingdom="sahra", banner=SAHRA_BANNER, timber=False, flat=True, steep=False, shutters=False,
    base="minecraft:cut_sandstone", base2="minecraft:cut_sandstone", wall="minecraft:smooth_sandstone",
    post_log=None, beam_log="minecraft:stripped_jungle_log", corner="minecraft:cut_sandstone",
    band="minecraft:cut_sandstone",
    civic="minecraft:smooth_sandstone", civic2="minecraft:cut_sandstone", trim="minecraft:chiseled_sandstone",
    floor="minecraft:smooth_sandstone", floor2="minecraft:jungle_planks", tile="minecraft:cut_sandstone",
    wood="jungle", wood2="acacia",
    roofs=[None], roof_civic=None, roof_flat="minecraft:smooth_sandstone",
    window="minecraft:glass_pane", path="minecraft:smooth_sandstone", ground="minecraft:sand",
    pave=[("minecraft:smooth_sandstone", 42), ("minecraft:sandstone", 25), ("minecraft:cut_sandstone", 25),
          ("minecraft:chiseled_sandstone", 3), ("minecraft:smooth_red_sandstone", 5)],
    pave_main="minecraft:cut_sandstone", pave_accent="minecraft:smooth_sandstone", curb="minecraft:sandstone",
    rim="minecraft:cut_sandstone",
    stone_stairs="minecraft:sandstone_stairs", stone_slab="minecraft:smooth_sandstone_slab",
    stone_wall="minecraft:sandstone_wall", smooth_slab="minecraft:smooth_sandstone_slab",
    tree="palm", tree_log="minecraft:jungle_log", leaves="minecraft:jungle_leaves",
    flowers=["minecraft:orange_tulip", "minecraft:red_tulip", "minecraft:dandelion", "minecraft:poppy",
             "minecraft:allium", "minecraft:pink_tulip"],
    fence="minecraft:jungle_fence",
    awnings=[("orange", "white"), ("red", "yellow"), ("cyan", "white"), ("yellow", "orange"), ("purple", "yellow"),
             ("lime", "white")],
    glass=["orange", "yellow", "red", "light_blue"], chimney="minecraft:sandstone",
    dome="minecraft:cyan_terracotta", dome2="minecraft:light_blue_terracotta", carpet="orange", carpet2="cyan",
    cw=[("minecraft:sandstone", 40), ("minecraft:cut_sandstone", 35), ("minecraft:smooth_sandstone", 20),
        ("minecraft:chiseled_sandstone", 5)],
    cw_top="minecraft:smooth_sandstone", cw_wall="minecraft:sandstone_wall",
)

SNOWY = Pal(
    "snowy", kingdom="nordheim", banner=NORDHEIM_BANNER, timber=True, flat=False, steep=True, shutters=True,
    base="minecraft:cobblestone", base2="minecraft:stone_bricks", wall="minecraft:spruce_planks",
    post_log="minecraft:spruce_log", beam_log="minecraft:stripped_spruce_log", corner=None, band=None,
    civic="minecraft:stone_bricks", civic2="minecraft:cobblestone", trim="minecraft:chiseled_stone_bricks",
    floor="minecraft:spruce_planks", floor2="minecraft:spruce_planks", tile="minecraft:polished_andesite",
    wood="spruce", wood2="dark_oak",
    roofs=[ROOF_DARK_OAK, ROOF_DEEPSLATE, ROOF_SPRUCE], roof_civic=ROOF_DEEPSLATE, roof_flat=None,
    window="minecraft:glass_pane", path="minecraft:dirt_path", ground="minecraft:grass_block",
    pave=[("minecraft:cobblestone", 35), ("minecraft:stone_bricks", 35), ("minecraft:andesite", 10),
          ("minecraft:mossy_cobblestone", 10), ("minecraft:cracked_stone_bricks", 10)],
    pave_main="minecraft:stone_bricks", pave_accent="minecraft:polished_andesite", curb="minecraft:cobblestone",
    rim="minecraft:polished_andesite",
    stone_stairs="minecraft:stone_brick_stairs", stone_slab="minecraft:stone_brick_slab",
    stone_wall="minecraft:cobblestone_wall", smooth_slab="minecraft:smooth_stone_slab",
    tree="spruce", tree_log="minecraft:spruce_log", leaves="minecraft:spruce_leaves",
    flowers=["minecraft:cornflower", "minecraft:azure_bluet", "minecraft:lily_of_the_valley", "minecraft:allium",
             "minecraft:oxeye_daisy", "minecraft:blue_orchid"],
    fence="minecraft:spruce_fence",
    awnings=[("blue", "white"), ("red", "white"), ("light_gray", "white"), ("cyan", "white"), ("brown", "white"),
             ("green", "white")],
    glass=["light_blue", "white", "cyan", "blue"], chimney="minecraft:cobblestone",
    dome=None, carpet="blue", carpet2="red",
    cw=[("minecraft:stone_bricks", 50), ("minecraft:cobblestone", 25), ("minecraft:mossy_cobblestone", 10),
        ("minecraft:cracked_stone_bricks", 10), ("minecraft:andesite", 5)],
    cw_top="minecraft:stone_bricks", cw_wall="minecraft:cobblestone_wall",
)

PALETTES = [PLAINS, DESERT, SNOWY]


# =================================================================================================
#  CONSTRUTOR DE TEMPLATES
# =================================================================================================
class B(Structure):
    """Structure com a paleta e atalhos para portas, camas, baús, NPCs, placas e estandartes."""

    def __init__(self, name, sx, sy, sz, P):
        super().__init__(sx, sy, sz)
        self.name = name
        self.P = P
        self.rnd = random.Random(f"city/{P.name}/{name}")

    def empty(self, x, y, z):
        v = self.get(x, y, z)
        return v is None or v == AIR

    def soft(self, x, y, z, state, nbt=None):
        if self.empty(x, y, z):
            self.set(x, y, z, state, nbt)

    def air(self, x0, y0, z0, x1, y1, z1):
        """Ar explícito (cava o que o Java colocou) só onde nada foi definido."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if self.get(x, y, z) is None:
                        self.set(x, y, z, AIR)

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return s
        return weighted[-1][0]

    # ---------------------------------------------------------------- portas, camas, contêineres
    def door(self, x, y, z, facing, wood=None, hinge="left"):
        wood = wood or self.P.wood
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, st(f"{wood}_door", facing=facing, half=half, hinge=hinge, open="false",
                                       powered="false"))

    def double_door(self, x, y, z, facing, along, wood=None):
        """Duas portas lado a lado (along = direção da 2ª porta)."""
        dx, dz = DIRV[along]
        self.door(x, y, z, facing, wood, "left" if along == CW[facing] else "right")
        self.door(x + dx, y, z + dz, facing, wood, "right" if along == CW[facing] else "left")

    def bed(self, x, y, z, color, facing):
        dx, dz = DIRV[facing]
        self.set(x, y, z, st(f"{color}_bed", facing=facing, part="foot", occupied="false"))
        self.set(x + dx, y, z + dz, st(f"{color}_bed", facing=facing, part="head", occupied="false"))

    @staticmethod
    def _city_nbt(loot):
        # LootTable: o jogo sorteia ao abrir. CityChest: marca de "baú de prédio da cidade" (crime ao abrir à vista
        # de NPC). O jogo descarta chaves desconhecidas do topo ao carregar o BlockEntity; a cópia em NeoForgeData
        # (BlockEntity.getPersistentData()) é a que sobrevive no mundo.
        return {"LootTable": f"{NS}:chests/city/{loot}", "CityChest": Byte(1),
                "NeoForgeData": {"CityChest": Byte(1)}}

    def chest(self, x, y, z, loot, facing="north"):
        self.set(x, y, z, st("chest", facing=facing, type="single", waterlogged="false"), nbt=self._city_nbt(loot))

    def barrel(self, x, y, z, loot=None, facing="up"):
        self.set(x, y, z, st("barrel", facing=facing, open="false"), nbt=self._city_nbt(loot) if loot else None)

    # ---------------------------------------------------------------- entidades
    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        nbt = {"PersistenceRequired": Byte(1), "Rotation": [Float(yaw), Float(0.0)]}
        if extra:
            nbt.update(extra)
        self.entity(x, y, z, mc(eid), nbt)

    def guard(self, x, y, z, yaw=0.0):
        self.mob(x, y, z, f"{NS}:city_guard", yaw)

    def citizen(self, x, y, z, yaw=0.0):
        self.mob(x, y, z, f"{NS}:citizen", yaw)

    def merchant(self, x, y, z, profession, yaw=0.0):
        self.mob(x, y, z, f"{NS}:city_merchant", yaw, {"Profession": profession})

    def adventurer(self, x, y, z, yaw=0.0):
        self.mob(x, y, z, f"{NS}:adventurer", yaw)

    def cat(self, x, y, z, variant="tabby", yaw=0.0):
        self.mob(x, y, z, "minecraft:cat", yaw, {"variant": f"minecraft:{variant}"})

    def horse(self, x, y, z, variant, yaw=0.0):
        self.mob(x, y, z, "minecraft:horse", yaw, {"Variant": Int(variant), "Tame": Byte(0)})

    def armor_stand(self, x, y, z, yaw, head=None, chest=None, legs=None, feet=None, hand=None, offhand=None,
                    dye=None):
        def it(i, dyed=False):
            if not i:
                return {}
            d = {"id": mc(i), "count": Int(1)}
            if dyed and dye is not None:
                d["components"] = {"minecraft:dyed_color": {"rgb": Int(dye)}}
            return d
        self.entity(x, y, z, "minecraft:armor_stand", {
            "Rotation": [Float(yaw), Float(0.0)], "ShowArms": Byte(1),
            "ArmorItems": [it(feet, True), it(legs, True), it(chest, True), it(head, True)],
            "HandItems": [it(hand), it(offhand)]})

    # ---------------------------------------------------------------- placas e estandartes
    def _sign_nbt(self, keys, color, glow, both):
        msgs = [json.dumps({"translate": f"sign.{NS}.city.{k}"}) if k else '""' for k in keys]
        msgs += ['""'] * (4 - len(msgs))
        front = {"messages": msgs, "color": color, "has_glowing_text": Byte(1 if glow else 0)}
        back = dict(front) if both else {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)}
        return {"front_text": front, "back_text": back, "is_waxed": Byte(1)}

    def sign(self, x, y, z, facing, keys, color="black", glow=False, wood=None):
        """Placa de parede (apoio do lado oposto a facing)."""
        self.set(x, y, z, st(f"{wood or self.P.wood}_wall_sign", facing=facing, waterlogged="false"),
                 nbt=self._sign_nbt(keys, color, glow, False))

    def standing_sign(self, x, y, z, facing, keys, color="black", glow=False, wood=None):
        self.set(x, y, z, st(f"{wood or self.P.wood}_sign", rotation=ROT16[facing], waterlogged="false"),
                 nbt=self._sign_nbt(keys, color, glow, False))

    def shop_sign(self, x, y, z, facing, keys, color="black", glow=False, wood=None):
        """Placa pendurada de loja saindo da fachada: facing = o plano da placa (texto dos dois lados); o apoio
        fica num dos lados perpendiculares (a parede)."""
        self.set(x, y, z, st(f"{wood or self.P.wood}_wall_hanging_sign", facing=facing, waterlogged="false"),
                 nbt=self._sign_nbt(keys, color, glow, True))

    def hanging_sign(self, x, y, z, facing, keys, color="black", glow=False, wood=None):
        """Placa pendurada por correntes num bloco de cima."""
        self.set(x, y, z, st(f"{wood or self.P.wood}_hanging_sign", attached="false", rotation=ROT16[facing],
                             waterlogged="false"),
                 nbt=self._sign_nbt(keys, color, glow, True))

    def wall_banner(self, x, y, z, facing, banner):
        color, patterns = banner
        self.set(x, y, z, st(f"{color}_wall_banner", facing=facing),
                 nbt={"patterns": [{"pattern": mc(p), "color": c} for p, c in patterns]})

    def banner(self, x, y, z, facing, banner):
        color, patterns = banner
        self.set(x, y, z, st(f"{color}_banner", rotation=ROT16[facing]),
                 nbt={"patterns": [{"pattern": mc(p), "color": c} for p, c in patterns]})


# ---------------------------------------------------------------- conexões de cercas/painéis/muros
NON_FULL = ("slab", "stairs", "fence", "pane", "bars", "lantern", "torch", "carpet", "button", "plate", "chain",
            "candle", "rod", "cluster", "bud", "roots", "vine", "sign", "banner", "pot", "chest", "campfire",
            "bell", "water", "lava", "air", "door", "ladder", "bed", "skull", "head", "rail", "snow", "egg",
            "cobweb", "leaves", "cauldron", "anvil", "lectern", "brewing", "grindstone", "composter",
            "flower", "poppy", "dandelion", "tulip", "allium", "orchid", "daisy", "cornflower", "lily", "bush",
            "grass", "fern", "sapling", "mushroom", "wall", "torchflower", "sugar_cane", "end_rod", "glass",
            "enchanting", "stonecutter", "table_", "heart", "board", "counter", "bluet", "berry", "wheat", "carrots",
            "potatoes", "beetroots", "trapdoor", "gate", "hay", "target", "loom")


def base_name(state):
    return state.split("[", 1)[0] if state else ""


def is_full(state):
    if not state or state == AIR:
        return False
    n = base_name(state).split(":", 1)[1]
    if n in ("mud_bricks", "hay_block", "target", "bookshelf", "crafting_table", "cartography_table",
             "fletching_table", "smithing_table", "barrel", "loom"):
        return True
    if n.endswith("_glass") or n == "glass":
        return True
    return not any(k in n for k in NON_FULL)


def is_wall(n):
    n = n.split(":", 1)[1] if ":" in n else n
    return n.endswith("_wall") and not any(k in n for k in ("banner", "torch", "sign", "skull", "head", "fan"))


def is_fence(n):
    return n.endswith("_fence")


def is_pane(n):
    return n.endswith("_pane") or n == "minecraft:iron_bars"


def with_props(state, props):
    name = base_name(state)
    cur = {}
    if "[" in state:
        for kv in state[:-1].split("[", 1)[1].split(","):
            if kv:
                k, v = kv.split("=")
                cur[k] = v
    cur.update(props)
    return name + "[" + ",".join(f"{k}={v}" for k, v in cur.items()) + "]"


def _gate_axis_ok(gate_state, d):
    f = "north"
    if "facing=" in gate_state:
        f = gate_state.split("facing=", 1)[1].split(",")[0].split("]")[0]
    return (f in ("east", "west")) != (d in ("east", "west"))


def fix_connections(s):
    """Liga cercas, painéis, grades e muros (o jogo também recalcula ao colocar; aqui fica certo já no .nbt)."""
    for (x, y, z), (state, nbt) in list(s.blocks.items()):
        n = base_name(state)
        fence, pan, wal = is_fence(n), is_pane(n), is_wall(n)
        if not (fence or pan or wal):
            continue
        props, con = {}, {}
        for d, (dx, dz) in DIRV.items():
            ns = s.get(x + dx, y, z + dz)
            nn = base_name(ns) if ns else ""
            if fence:
                nether = "nether_brick" in n
                ok = bool(ns) and ((is_fence(nn) and ("nether_brick" in nn) == nether) or is_full(ns)
                                   or ("fence_gate" in nn and _gate_axis_ok(ns, d)))
                props[d] = "true" if ok else "false"
            elif pan:
                ok = bool(ns) and (is_pane(nn) or is_wall(nn) or is_full(ns))
                props[d] = "true" if ok else "false"
            else:
                ok = bool(ns) and (is_wall(nn) or is_pane(nn) or is_full(ns)
                                   or ("fence_gate" in nn and _gate_axis_ok(ns, d)))
                props[d] = "low" if ok else "none"
            con[d] = ok
        if wal:
            above = s.get(x, y + 1, z)
            straight = (con["north"] and con["south"] and not con["east"] and not con["west"]) or \
                       (con["east"] and con["west"] and not con["north"] and not con["south"])
            props["up"] = "true" if (above and above != AIR) or not straight else "false"
        if "waterlogged" not in state:
            props["waterlogged"] = "false"
        s.blocks[(x, y, z)] = (with_props(state, props), nbt)


BUILT = []          # (paleta, nome, Structure, blocos salvos)


def save(s):
    """Ajusta a altura ao que foi usado, liga cercas e grava em structure/city/<paleta>/<nome>.nbt."""
    fix_connections(s)
    top = max([y for (_, y, _) in s.blocks] + [int(e["pos"][1]) + 2 for e in s.entities] + [1])
    s.size = (s.size[0], top + 1, s.size[2])
    n = s.save(os.path.join(STRUCT_OUT, s.P.name, s.name + ".nbt"), keep_air=True)
    BUILT.append((s.P.name, s.name, s, n))
    return n


# =================================================================================================
#  PRIMITIVAS DE CONSTRUÇÃO
# =================================================================================================
def perim(x0, z0, x1, z1):
    for x in range(x0, x1 + 1):
        yield x, z0
        if z1 != z0:
            yield x, z1
    for z in range(z0 + 1, z1):
        yield x0, z
        if x1 != x0:
            yield x1, z


def disk(cx, cz, r):
    for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
        for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                yield x, z, d


def post_positions(length):
    """Posições dos pilares numa parede de 'length' blocos: pontas + divisões de ~4 (simétricas)."""
    n = max(1, round((length - 1) / 4))
    return sorted({round(i * (length - 1) / n) for i in range(n + 1)})


def yard(s, block=None):
    """Chão do lote inteiro (y=0)."""
    for x in range(s.size[0]):
        for z in range(s.size[2]):
            s.set(x, 0, z, block or s.P.ground)


def paving(s, x0, z0, x1, z1, y=0, mix=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, y, z, s.pick(mix or s.P.pave))


def path(s, x0, z0, x1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            s.set(x, 0, z, s.P.path)


def flower_bed(s, x0, z0, x1, z1, y=0, flowers=None):
    """Canteiro: grama + flores pequenas em TODA casa (flor pequena impede árvore aleatória de nascer ali)."""
    fl = flowers or s.P.flowers
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            s.set(x, y, z, "minecraft:grass_block")
            s.set(x, y + 1, z, s.rnd.choice(fl))


def bush(s, x, y, z):
    s.set(x, y, z, leaves(s.P.leaves if s.P.tree != "palm" else "minecraft:azalea_leaves"))


def lamp_post(s, x, z, y=1, h=3, soul=False, fence=None):
    for dy in range(h):
        s.set(x, y + dy, z, fence or s.P.fence)
    s.set(x, y + h, z, lantern(False, soul))


def lamp_double(s, x, z, axis="x", y=1, h=4):
    """Poste com dois braços e lanternas penduradas (praças)."""
    for dy in range(h):
        s.set(x, y + dy, z, s.P.fence)
    s.set(x, y + h, z, lantern())
    for sgn in (-1, 1):
        ax, az = (sgn, 0) if axis == "x" else (0, sgn)
        s.set(x + ax, y + h - 1, z + az, s.P.fence)
        s.set(x + ax, y + h - 2, z + az, lantern(True))


def hang(s, x, y, z, chain_len=0, soul=False):
    """Lanterna pendurada em y (o teto fica em y + chain_len + 1)."""
    for k in range(chain_len):
        s.set(x, y + 1 + k, z, chain())
    s.set(x, y, z, lantern(True, soul))


def table(s, x, y, z, chairs=(), wood=None, top=None):
    P = s.P
    s.set(x, y, z, P.w("fence", wood or P.wood2))
    s.set(x, y + 1, z, top or plate(wood or P.wood))
    for d in chairs:
        dx, dz = DIRV[d]
        s.set(x + dx, y, z + dz, stairs(mc(f"{P.wood}_stairs"), d))


def chair(s, x, y, z, back):
    s.set(x, y, z, stairs(mc(f"{s.P.wood}_stairs"), back))


def rug(s, x0, z0, x1, z1, y, color, border=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            s.soft(x, y, z, mc(f"{border if (edge and border) else color}_carpet"))


def shelves(s, x0, z0, x1, z1, y0, y1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y1 + 1):
                s.set(x, y, z, "minecraft:bookshelf")


def awning(s, x0, x1, z0, z1, y, colors, along="x"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k = (x - x0) if along == "x" else (z - z0)
            s.set(x, y, z, mc(f"{colors[k % 2]}_wool"))


def chimney(s, x, z, y0, y1, smoke=True):
    for y in range(y0, y1 + 1):
        s.set(x, y, z, s.P.chimney)
    if smoke:
        s.set(x, y1 + 1, z, campfire())


def tree(s, x, z, y=1, h=None, kind=None):
    """Árvore com folhas persistentes (não somem). kind: oak / spruce / palm / birch."""
    P = s.P
    kind = kind or P.tree
    r = s.rnd
    if kind == "palm":
        h = h or r.choice((5, 6))
        for dy in range(h):
            s.set(x, y + dy, z, log("minecraft:jungle_log"))
        lv = leaves("minecraft:jungle_leaves")
        top = y + h
        s.set(x, top, z, lv)
        for dx, dz in DIRV.values():
            s.soft(x + dx, top, z + dz, lv)
            s.soft(x + 2 * dx, top, z + 2 * dz, lv)
            s.soft(x + 3 * dx, top - 1, z + 3 * dz, lv)
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            s.soft(x + dx, top - 1, z + dz, lv)
        s.soft(x, top + 1, z, lv)
        return
    if kind == "spruce":
        h = h or r.choice((6, 7))
        lv = leaves("minecraft:spruce_leaves")
        for dy in range(h):
            s.set(x, y + dy, z, log("minecraft:spruce_log"))
        for i, dy in enumerate(range(2, h + 1)):
            rad = [2, 1, 2, 1, 1, 0, 0][min(i, 6)] if h - dy > 1 else 0
            for lx, lz, d in disk(x, z, rad + 0.5):
                if (lx, lz) != (x, z) or dy >= h:
                    if not (rad == 2 and abs(lx - x) == 2 and abs(lz - z) == 2):
                        s.soft(lx, y + dy, lz, lv)
        s.soft(x, y + h, z, lv)
        s.soft(x, y + h + 1, z, lv)
        return
    logb = "minecraft:birch_log" if kind == "birch" else P.tree_log
    lv = leaves("minecraft:birch_leaves" if kind == "birch" else P.leaves)
    h = h or r.choice((4, 5))
    for dy in range(h):
        s.set(x, y + dy, z, log(logb))
    for dy, rad in ((h - 2, 2), (h - 1, 2), (h, 1), (h + 1, 1)):
        for lx, lz, d in disk(x, z, rad + 0.4):
            corner = abs(lx - x) == rad and abs(lz - z) == rad
            if corner and (dy >= h or r.random() < 0.5):
                continue
            s.soft(lx, y + dy, lz, lv)


def bench(s, x, z, facing, n=2, along="x", y=1):
    """Banco de escadas: quem senta olha para o lado OPOSTO a facing (o encosto fica em facing)."""
    for i in range(n):
        bx, bz = (x + i, z) if along == "x" else (x, z + i)
        s.set(bx, y, bz, stairs(mc(f"{s.P.wood}_stairs"), facing))


# ---------------------------------------------------------------- paredes, janelas, portas e telhados
def shell(s, x0, z0, x1, z1, floors=1, fh=5, wall=None, base=None, lower=None, ceiling=True, floor=None,
          floor2=None, posts=True, corner=None, band=None):
    """Paredes de um prédio retangular: 'floors' andares de altura fh (paredes y=k*fh+1 .. (k+1)*fh-1, vigas/forro
    em y=(k+1)*fh). Pisos internos em y=0 e entre andares. Retorna o y do topo (onde começa o telhado).
    posts=False: sem pilares de enxaimel (prédios de pedra); corner/band trocam os cantos e as faixas dos andares."""
    P = s.P
    wall = wall or P.wall
    base = base or P.base
    top = floors * fh
    px = set(post_positions(x1 - x0 + 1)) if posts else {0, x1 - x0}
    pz = set(post_positions(z1 - z0 + 1)) if posts else {0, z1 - z0}
    timber = P.timber and posts
    corner_b = corner or (P.post() if timber or not P.corner else P.corner)
    for y in range(0, top + 1):
        for x, z in perim(x0, z0, x1, z1):
            is_corner = x in (x0, x1) and z in (z0, z1)
            along_x = z in (z0, z1)
            k = (x - x0) if along_x else (z - z0)
            if y == 0:
                blk = base
            elif y % fh == 0:
                blk = band or (corner_b if is_corner else P.beam("x" if along_x else "z"))
            elif timber and (is_corner or (k in (px if along_x else pz))):
                blk = P.post()
            elif is_corner:
                blk = corner_b
            elif y == 1:
                blk = base
            elif lower and y < fh:
                blk = lower
            else:
                blk = wall
            s.set(x, y, z, blk)
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, floor or P.floor)
    for f in range(1, floors + (1 if ceiling else 0)):
        s.fill(x0 + 1, f * fh, z0 + 1, x1 - 1, f * fh, z1 - 1, floor2 or P.floor2)
    return top


def window(s, x, y, z, out, h=2, glass=None):
    P = s.P
    for dy in range(h):
        s.set(x, y + dy, z, glass or P.window)
    if P.shutters:
        dx, dz = DIRV[out]
        lat = ((1, 0), (-1, 0)) if out in ("north", "south") else ((0, 1), (0, -1))
        for lx, lz in lat:
            for dy in range(h):
                tx, tz = x + lx + dx, z + lz + dz
                if 0 <= tx < s.size[0] and 0 <= tz < s.size[2]:
                    s.soft(tx, y + dy, tz, trapdoor(P.wood, out, is_open=True))


def windows(s, x0, z0, x1, z1, y, sides="nsew", skip=(), h=2, glass=None):
    """Janelas no meio dos vãos entre pilares de cada parede (pula as posições em skip e vizinhas)."""
    skipset = set()
    for sx, sz in skip:
        for d in (-1, 0, 1):
            skipset.add((sx + d, sz))
            skipset.add((sx, sz + d))
    for side in sides:
        if side in "ns":
            z = z0 if side == "n" else z1
            out = "north" if side == "n" else "south"
            pos = post_positions(x1 - x0 + 1)
            for a, b in zip(pos, pos[1:]):
                if b - a >= 3:
                    x = x0 + (a + b) // 2
                    if (x, z) not in skipset:
                        window(s, x, y, z, out, h, glass)
        else:
            x = x0 if side == "w" else x1
            out = "west" if side == "w" else "east"
            pos = post_positions(z1 - z0 + 1)
            for a, b in zip(pos, pos[1:]):
                if b - a >= 3:
                    z = z0 + (a + b) // 2
                    if (x, z) not in skipset:
                        window(s, x, y, z, out, h, glass)


def entry(s, x, y, z, out, wood=None, torches=True):
    """Porta numa parede externa + tochas dos lados (por fora)."""
    s.door(x, y, z, out, wood)
    if torches:
        dx, dz = DIRV[out]
        lat = ((1, 0), (-1, 0)) if out in ("north", "south") else ((0, 1), (0, -1))
        for lx, lz in lat:
            tx, tz = x + lx + dx, z + lz + dz
            if 0 <= tx < s.size[0] and 0 <= tz < s.size[2]:
                s.soft(tx, y + 2, tz, wall_torch(out))


def flat_roof(s, x0, z0, x1, z1, y0):
    """Telhado plano (deserto): laje, parapeito com ameias e vigas de madeira saindo das paredes."""
    P = s.P
    s.fill(x0, y0, z0, x1, y0, z1, P.roof_flat)
    for x, z in perim(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        s.set(x, y0 + 1, z, P.civic2 if corner or (x + z) % 2 == 0 else slab("minecraft:cut_sandstone_slab"))
    for x in range(x0 + 1, x1, 3):
        for z, d in ((z0 - 1, "z"), (z1 + 1, "z")):
            if 0 <= z < s.size[2]:
                s.soft(x, y0 - 1, z, log(P.beam_log, d))
    for z in range(z0 + 1, z1, 3):
        for x, d in ((x0 - 1, "x"), (x1 + 1, "x")):
            if 0 <= x < s.size[0]:
                s.soft(x, y0 - 1, z, log(P.beam_log, d))
    return y0 + 1


def roof(s, x0, z0, x1, z1, y0, axis=None, mat=None, overhang=1, gable=None, gable_window=True, end_overhang=None):
    """Telhado sobre as paredes [x0..x1]x[z0..z1] cujo topo está em y0. Duas águas (planície), íngreme 2:1 (neve)
    ou plano com parapeito (deserto). end_overhang=(início, fim) troca o beiral das pontas da cumeeira (0 = o
    telhado termina rente à empena, ex.: encostado numa torre). Retorna o y mais alto."""
    P = s.P
    if P.flat:
        return flat_roof(s, x0, z0, x1, z1, y0)
    if axis is None:
        axis = "x" if (x1 - x0) >= (z1 - z0) else "z"
    stair_b, slab_b, full_b = mat or P.roofs[0]
    rise = 2 if P.steep else 1
    gw = gable or P.wall
    e0, e1 = end_overhang or (overhang, overhang)
    if axis == "x":
        a0, a1, c0, c1, ends = x0 - e0, x1 + e1, z0 - overhang, z1 + overhang, (x0, x1)

        def put(a, y, c, b, soft=False):
            if 0 <= a < s.size[0] and 0 <= c < s.size[2]:
                (s.soft if soft else s.set)(a, y, c, b)
        lo, hi = "south", "north"
    else:
        a0, a1, c0, c1, ends = z0 - e0, z1 + e1, x0 - overhang, x1 + overhang, (z0, z1)

        def put(a, y, c, b, soft=False):
            if 0 <= c < s.size[0] and 0 <= a < s.size[2]:
                (s.soft if soft else s.set)(c, y, a, b)
        lo, hi = "east", "west"
    k = 0
    while c0 + k < c1 - k:
        y = y0 + k * rise
        for a in range(a0, a1 + 1):
            put(a, y, c0 + k, stairs(stair_b, lo))
            put(a, y, c1 - k, stairs(stair_b, hi))
            if rise == 2 and k > 0:
                put(a, y - 1, c0 + k, full_b)
                put(a, y - 1, c1 - k, full_b)
        k += 1
    if c0 + k == c1 - k:
        y = y0 + k * rise
        for a in range(a0, a1 + 1):
            if rise == 2:
                put(a, y - 1, c0 + k, full_b)
            put(a, y, c0 + k, full_b)
            put(a, y + 1, c0 + k, slab(slab_b))
        top = y + 1
        mid = c0 + k
    else:
        y = y0 + (k - 1) * rise + 1
        for a in range(a0, a1 + 1):
            put(a, y, c0 + k - 1, slab(slab_b))
            put(a, y, c0 + k, slab(slab_b))
        top = y
        mid = c0 + k - 1
    # Empenas: o triângulo de parede nas duas pontas (com pilar no meio nas paletas de enxaimel).
    for e in ends:
        for c in range(c0 + overhang, c1 - overhang + 1):
            kk = min(c - c0, c1 - c)
            ytop = y0 + kk * rise - 1 - (1 if rise == 2 and kk > 0 else 0)
            for y in range(y0, ytop + 1):
                blk = P.post() if (P.timber and c == mid) else gw
                put(e, y, c, blk, soft=True)
        if gable_window:
            kk = min(mid - c0, c1 - mid)
            if kk * rise >= 4:
                wc = mid + (1 if P.timber else 0)      # no enxaimel o meio da empena é o pilar
                put(e, y0 + 1, wc, P.window)
                put(e, y0 + 2, wc, P.window)
    return top


def dome(s, cx, cz, y0, r, block, finial=True):
    """Cúpula (deserto): meia esfera oca de raio r com remate dourado."""
    for x, z, d in disk(cx, cz, r + 0.5):
        for dy in range(0, int(r) + 2):
            dd = math.sqrt(d * d + dy * dy)
            if r - 0.5 < dd <= r + 0.5:
                s.set(x, y0 + dy, z, block)
    top = y0 + int(round(r)) + 1
    if finial:
        s.set(int(cx), top, int(cz), "minecraft:gold_block")
        s.set(int(cx), top + 1, int(cz), st("lightning_rod", facing="up", powered="false", waterlogged="false"))
    return top + 1


def spire(s, cx, cz, y0, r, block):
    """Pináculo cônico escalonado (torres)."""
    y = y0
    rr = r
    while rr >= 0.5:
        for x, z, d in disk(cx, cz, rr):
            s.set(x, y, z, block)
        y += 1
        rr -= 0.6
    s.set(int(cx), y, int(cz), block)
    s.set(int(cx), y + 1, int(cz), st("lightning_rod", facing="up", powered="false", waterlogged="false"))
    return y + 1


def ascension_altar(s, cx, y, cz):
    """Altar de Ascensão completo (núcleo público + pedestais + Pedras Rúnicas), núcleo em (cx, y, cz)."""
    s.set(cx, y, cz, ASCENSION_CORE, nbt=public_altar_nbt())
    for dx, dz in ALTAR_PEDESTALS:
        s.set(cx + dx, y, cz + dz, ASCENSION_PEDESTAL)
    for dx, dz in ALTAR_RUNES:
        s.set(cx + dx, y, cz + dz, RUNE_STONE)


def flag_pole(s, x, z, y, h, banner, facing="south"):
    for dy in range(h):
        s.set(x, y + dy, z, s.P.fence)
    s.banner(x, y + h, z, facing, banner)


# =================================================================================================
#  CASAS (lotes S 11x11, frente = sul, porta em z=7..8, quintal na frente)
# =================================================================================================
BED_COLORS = ["red", "blue", "green", "yellow", "brown", "light_blue", "orange", "white", "cyan", "purple"]


def house_a(P):
    """Casinha térrea com lareira, cama, mesa e jardim na frente."""
    s = B("house_a", S_LOT, 24, S_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 1, 1, 9, 7
    top = shell(s, x0, z0, x1, z1, floors=1, fh=5)
    entry(s, 5, 1, z1, "south")
    windows(s, x0, z0, x1, z1, 2, skip=[(5, z1)])
    ridge = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[0])
    # Lareira no canto nordeste com chaminé atravessando o telhado.
    s.set(8, 1, 2, campfire(facing="south"))
    chimney(s, 8, 2, 2, max(ridge - 2, top + 2))
    s.bed(2, 1, 3, r.choice(BED_COLORS), "north")
    s.set(3, 1, 2, "minecraft:crafting_table")
    s.set(7, 1, 2, st("furnace", facing="south", lit="false"))
    s.chest(2, 1, 6, "house", "east")
    table(s, 6, 1, 4, ("west", "east"))
    s.barrel(8, 1, 6)
    s.set(8, 2, 6, potted(r.choice(["red_tulip", "fern", "dandelion", "blue_orchid"])))
    rug(s, 3, 4, 4, 5, 1, r.choice(["red", "brown", "green", "orange"]))
    hang(s, 5, 4, 4)
    # Jardim e caminho.
    path(s, 5, 8, 5, 10)
    flower_bed(s, 1, 9, 3, 9)
    flower_bed(s, 7, 9, 9, 9)
    lamp_post(s, 4, 10, h=2)
    for z in (2, 3):
        s.set(10, 1, z, log(P.tree_log, "x"))
    bush(s, 0, 1, 2)
    save(s)


def house_b(P):
    """Sobrado de 2 andares: cozinha embaixo, quartos em cima, escada interna."""
    s = B("house_b", S_LOT, 26, S_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 1, 1, 9, 8
    top = shell(s, x0, z0, x1, z1, floors=2, fh=4)
    entry(s, 5, 1, z1, "south")
    windows(s, x0, z0, x1, z1, 2, skip=[(5, z1), (2, z1)])
    windows(s, x0, z0, x1, z1, 5, h=2)
    roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[1 % len(P.roofs)])
    # Escada encostada na parede oeste subindo para o norte (degraus z7..4, piso de cima em y4).
    for i, z in enumerate((7, 6, 5, 4)):
        s.set(2, 1 + i, z, stairs(mc(f"{P.wood}_stairs"), "north"))
        for y in range(1, 1 + i):
            s.set(2, y, z, P.w("planks"))
    for z in (5, 6):
        s.set(2, 4, z, AIR)
    for z in (5, 6, 7):
        s.set(3, 5, z, P.fence)
    s.set(2, 5, 7, P.fence)
    # Térreo: cozinha.
    s.set(8, 1, 2, st("smoker", facing="south", lit="false"))
    s.set(7, 1, 2, st("furnace", facing="south", lit="false"))
    s.barrel(6, 1, 2, "house", "south")
    s.set(5, 1, 2, "minecraft:crafting_table")
    table(s, 6, 1, 5, ("west", "east"))
    s.set(8, 1, 7, st("composter", level=3))
    hang(s, 6, 3, 4)
    # Andar de cima: dois quartos.
    s.bed(5, 5, 3, r.choice(BED_COLORS), "north")
    s.bed(8, 5, 3, r.choice(BED_COLORS), "north")
    s.chest(7, 5, 2, "house", "south")
    s.barrel(8, 5, 7)
    s.set(8, 6, 7, candle(2, "white"))
    s.set(6, 5, 7, st("lectern", facing="south", has_book="false", powered="false"))
    rug(s, 5, 5, 8, 6, 5, r.choice(["light_blue", "lime", "pink", "yellow"]))
    hang(s, 6, 7, 5)
    path(s, 5, 9, 5, 10)
    flower_bed(s, 1, 9, 3, 10)
    flower_bed(s, 7, 9, 9, 10)
    bush(s, 10, 1, 4)
    bush(s, 0, 1, 6)
    s.citizen(6.5, 1, 6.5, YAW["east"])
    save(s)


def house_c(P):
    """Padaria: balcão com vitrine, forno, sacas de trigo; toldo e placa de loja."""
    s = B("house_c", S_LOT, 24, S_LOT, P)
    r = s.rnd
    yard(s)
    paving(s, 0, 8, 10, 10)
    x0, z0, x1, z1 = 1, 1, 9, 7
    top = shell(s, x0, z0, x1, z1, floors=1, fh=5)
    entry(s, 3, 1, z1, "south", torches=False)
    for x in (6, 7, 8):
        s.set(x, 2, z1, P.window)
        s.set(x, 3, z1, P.window)
    windows(s, x0, z0, x1, z1, 2, sides="nwe")
    ridge = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[-1])
    colors = r.choice(P.awnings)
    awning(s, 5, 9, 8, 8, 4, colors)
    s.shop_sign(2, 3, 8, "east", ["bakery.1", "bakery.2"])
    # Forno a lenha: fornalha + defumador + fogueira com chaminé.
    s.set(8, 1, 2, campfire(facing="south"))
    chimney(s, 8, 2, 2, max(ridge - 2, top + 2))
    s.set(7, 1, 2, st("smoker", facing="south", lit="true"))
    s.set(6, 1, 2, st("furnace", facing="south", lit="true"))
    s.set(2, 1, 2, st("hay_block", axis="y"))
    s.set(2, 2, 2, st("hay_block", axis="x"))
    s.set(3, 1, 2, st("hay_block", axis="y"))
    for x in (5, 6, 7, 8):
        s.barrel(x, 1, 5, "market" if x == 6 else None, "south")
    s.set(5, 2, 5, "minecraft:cake")
    s.set(8, 2, 5, potted("dandelion"))
    s.chest(2, 1, 5, "house", "east")
    s.bed(4, 1, 3, r.choice(BED_COLORS), "north")
    hang(s, 6, 4, 4)
    s.citizen(6.5, 1, 3.5, YAW["south"])
    s.barrel(10, 1, 9)
    s.set(0, 1, 9, st("hay_block", axis="y"))
    lamp_post(s, 0, 10, h=2)
    save(s)


def house_d(P):
    """Sobrado estreito de 3 andares com varanda na frente (empena virada para a rua)."""
    s = B("house_d", S_LOT, 32, S_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 2, 1, 8, 8
    top = shell(s, x0, z0, x1, z1, floors=3, fh=4)
    entry(s, 5, 1, z1, "south")
    windows(s, x0, z0, x1, z1, 2, sides="nwe")
    windows(s, x0, z0, x1, z1, 5, sides="nwe")
    windows(s, x0, z0, x1, z1, 9, sides="nswe")
    window(s, 3, 2, z1, "south")
    window(s, 7, 2, z1, "south")
    roof(s, x0, z0, x1, z1, top, axis="z", mat=P.roofs[0])
    # Varanda do 2º andar: piso, guarda-corpo, porta e vasos.
    s.door(5, 5, z1, "south")
    for x in range(3, 8):
        for z in (9, 10):
            s.set(x, 4, z, slab(mc(f"{P.wood}_slab"), "top"))
        s.set(x, 5, 10, P.fence)
    s.set(3, 5, 9, P.fence)
    s.set(7, 5, 9, P.fence)
    s.set(4, 6, 10, potted("red_tulip"))
    s.set(6, 6, 10, potted("azure_bluet"))
    # Escada de mão na parede leste atravessando os pisos.
    for y in range(1, top):
        s.set(7, y, 2, ladder("west"))
    # Térreo: sala.
    table(s, 4, 1, 4, ("north", "south"))
    s.set(3, 1, 7, st("furnace", facing="east", lit="false"))
    s.barrel(3, 1, 2, "house", "south")
    rug(s, 4, 5, 6, 6, 1, r.choice(["brown", "gray", "red"]))
    hang(s, 5, 3, 5)
    # 2º andar: quarto.
    s.bed(3, 5, 3, r.choice(BED_COLORS), "north")
    s.chest(6, 5, 2, "house", "south")
    s.set(3, 5, 7, P.w("planks"))
    s.set(3, 6, 7, candle(1, "white"))
    hang(s, 5, 7, 5)
    # 3º andar: estúdio.
    shelves(s, 3, 2, 5, 2, 9, 10)
    s.set(4, 9, 5, st("lectern", facing="south", has_book="false", powered="false"))
    s.bed(3, 9, 7, r.choice(BED_COLORS), "north")
    hang(s, 5, 11, 5)
    path(s, 5, 9, 5, 10)
    flower_bed(s, 0, 9, 1, 10)
    flower_bed(s, 9, 9, 10, 10)
    s.barrel(9, 1, 2)
    s.barrel(9, 2, 2)
    bush(s, 0, 1, 3)
    save(s)


def well(P):
    """Pracinha com poço coberto, bancos, árvore e lampião."""
    s = B("well", S_LOT, 16, S_LOT, P)
    paving(s, 0, 0, 10, 10)
    c = 5
    for x, z in perim(c - 1, c - 1, c + 1, c + 1):
        s.set(x, 1, z, P.base2)
    s.set(c, 0, c, water())
    s.set(c, 1, c, water())
    for x, z in ((c - 1, c - 1), (c + 1, c - 1), (c - 1, c + 1), (c + 1, c + 1)):
        s.set(x, 2, z, P.fence)
        s.set(x, 3, z, P.fence)
    for x in range(c - 2, c + 3):
        s.set(x, 4, c - 1, stairs(mc(f"{P.wood}_stairs"), "south") if not P.flat else slab(P.smooth_slab))
        s.set(x, 4, c + 1, stairs(mc(f"{P.wood}_stairs"), "north") if not P.flat else slab(P.smooth_slab))
        s.set(x, 4, c, slab(mc(f"{P.wood}_slab")) if not P.flat else slab(P.smooth_slab))
    s.set(c, 3, c, lantern(True))
    bench(s, c - 1, 8, "south", 3)
    bench(s, c - 1, 2, "north", 3)
    flower_bed(s, 0, 0, 1, 1)
    flower_bed(s, 9, 9, 10, 10)
    s.set(9, 0, 1, "minecraft:grass_block")
    tree(s, 9, 1)
    lamp_post(s, 1, 9)
    s.citizen(3.5, 1, 6.5, YAW["east"])
    s.citizen(7.5, 1, 4.5, YAW["west"])
    s.cat(2.5, 1, 4.5, "calico")
    save(s)


# =================================================================================================
#  LOJAS (lotes S)
# =================================================================================================
def smithy(P):
    """Ferraria aberta: forja, bigorna, rebolo, mesa de ferraria, armaduras à mostra e o Ferreiro."""
    s = B("smithy", S_LOT, 22, S_LOT, P)
    yard(s)
    paving(s, 0, 0, 10, 10, mix=[(P.base2, 5), (P.base, 3), ("minecraft:andesite", 2)])
    # Paredes de pedra nos fundos e nos lados (até y4); frente aberta com viga.
    for y in range(1, 5):
        for x in range(1, 10):
            s.set(x, y, 1, P.base if y < 4 else P.beam("x"))
        for z in range(2, 7):
            s.set(1, y, z, P.base if y < 4 else P.beam("z"))
            s.set(9, y, z, P.base if y < 4 else P.beam("z"))
    for y in range(1, 5):
        s.set(1, y, 6, P.post())
        s.set(9, y, 6, P.post())
    for x in range(2, 9):
        s.set(x, 4, 6, P.beam("x"))
    s.fill(2, 4, 2, 8, 4, 5, P.floor2)
    roof(s, 1, 1, 9, 6, 4, axis="x", mat=P.roofs[1 % len(P.roofs)], gable=P.base)
    # Forja: alto-forno, fornalha, caldeirões de lava e água, chaminé.
    s.set(2, 1, 2, st("blast_furnace", facing="south", lit="true"))
    s.set(3, 1, 2, "minecraft:lava_cauldron")
    s.set(4, 1, 2, st("blast_furnace", facing="south", lit="true"))
    s.set(3, 2, 2, P.chimney)
    chimney(s, 3, 2, 3, 9 if not P.steep else 13)
    s.set(6, 1, 2, st("water_cauldron", level=3))
    s.set(8, 1, 2, st("grindstone", face="floor", facing="north"))
    s.set(8, 1, 4, "minecraft:smithing_table")
    s.set(5, 1, 4, st("anvil", facing="east"))
    s.chest(8, 1, 5, "smithy", "west")
    s.barrel(2, 1, 5, None, "east")
    s.barrel(2, 2, 5, None, "east")
    s.set(2, 1, 4, "minecraft:coal_block")
    hang(s, 5, 3, 4)
    s.armor_stand(3.5, 1, 7.5, YAW["south"], head="iron_helmet", chest="iron_chestplate", legs="iron_leggings",
                  hand="iron_sword")
    s.armor_stand(7.5, 1, 7.5, YAW["south"], head="chainmail_helmet", chest="chainmail_chestplate", hand="iron_axe")
    s.set(5, 1, 8, st("anvil", facing="south"))
    s.merchant(5.5, 1, 3.5, "blacksmith", YAW["south"])
    # Placa pendurada saindo do pilar da frente, virada para a rua.
    s.shop_sign(10, 3, 6, "south", ["smithy"], wood=P.wood2)
    lamp_post(s, 0, 9, h=2)
    save(s)


def alchemy(P):
    """Loja de alquimia: balcão com suportes de poção, caldeirões, ervas e o Alquimista."""
    s = B("alchemy", S_LOT, 24, S_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 1, 1, 9, 7
    top = shell(s, x0, z0, x1, z1, floors=1, fh=5)
    entry(s, 5, 1, z1, "south", torches=False)
    windows(s, x0, z0, x1, z1, 2, skip=[(5, z1)], glass=pane("purple"))
    ridge = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[0])
    if not P.flat:
        s.set(5, ridge, 4, st("amethyst_cluster", facing="up", waterlogged="false"))
    s.shop_sign(6, 3, 8, "east", ["alchemy"])
    # Balcão com suportes de poção.
    for x in (2, 3, 4, 6, 7, 8):
        s.set(x, 1, 4, P.w("planks", P.wood2))
    s.set(3, 2, 4, st("brewing_stand", has_bottle_0="true", has_bottle_1="false", has_bottle_2="true"))
    s.set(7, 2, 4, st("brewing_stand", has_bottle_0="false", has_bottle_1="true", has_bottle_2="true"))
    s.set(2, 2, 4, candle(3, "purple"))
    s.set(8, 2, 4, potted("crimson_fungus"))
    # Prateleiras de ingredientes no fundo.
    shelves(s, 2, 2, 3, 2, 1, 2)
    s.barrel(4, 1, 2, "alchemy", "south")
    s.set(5, 1, 2, st("water_cauldron", level=2))
    s.barrel(6, 1, 2, None, "south")
    s.chest(8, 1, 2, "alchemy", "south")
    s.set(7, 1, 2, "minecraft:bookshelf")
    for x, plant in ((2, "red_mushroom"), (3, "brown_mushroom"), (7, "warped_fungus")):
        s.set(x, 3, 2, potted(plant))
    s.set(5, 4, 2, st("hanging_roots", waterlogged="false"))
    s.set(3, 4, 6, st("hanging_roots", waterlogged="false"))
    s.set(2, 1, 6, "minecraft:decorated_pot")
    s.set(8, 1, 6, st("amethyst_cluster", facing="up", waterlogged="false"))
    rug(s, 3, 5, 7, 6, 1, "purple", "magenta")
    hang(s, 5, 4, 4, soul=True)
    s.merchant(5.5, 1, 3.5, "alchemist", YAW["south"])
    # Horta de ervas na frente.
    path(s, 5, 8, 5, 10)
    flower_bed(s, 1, 9, 3, 10, flowers=["minecraft:allium", "minecraft:lily_of_the_valley", "minecraft:blue_orchid",
                                         "minecraft:azure_bluet"])
    for x in (7, 8, 9):
        for z in (9, 10):
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, st("sweet_berry_bush", age=r.choice((2, 3))))
    save(s)


def jeweler(P):
    """Joalheria: vitrines de vidro com cristais, o Joalheiro e o cofre."""
    s = B("jeweler", S_LOT, 22, S_LOT, P)
    yard(s)
    paving(s, 0, 8, 10, 10)
    x0, z0, x1, z1 = 2, 1, 8, 7
    top = shell(s, x0, z0, x1, z1, floors=1, fh=5, wall=P.base2 if P.timber else None)
    entry(s, 5, 1, z1, "south")
    for x in (3, 7):
        s.set(x, 2, z1, pane("light_blue"))
        s.set(x, 3, z1, pane("light_blue"))
    windows(s, x0, z0, x1, z1, 2, sides="we")
    roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roof_civic or P.roofs[0])
    s.shop_sign(4, 3, 8, "east", ["jeweler"])
    # Vitrines: bloco + vidro com "joias" em cima.
    gems = [st("amethyst_cluster", facing="up", waterlogged="false"), pane("yellow"), pane("lime"),
            st("amethyst_cluster", facing="up", waterlogged="false")]
    for i, x in enumerate((3, 4, 6, 7)):
        s.set(x, 1, 4, "minecraft:polished_diorite" if P.name != "desert" else "minecraft:smooth_quartz")
        s.set(x, 2, 4, gems[i])
    s.chest(4, 1, 2, "jeweler", "south")
    s.barrel(6, 1, 2, "jeweler", "south")
    s.set(3, 1, 2, "minecraft:decorated_pot")
    s.set(7, 1, 2, "minecraft:decorated_pot")
    s.set(5, 1, 2, st("lectern", facing="south", has_book="false", powered="false"))
    rug(s, 3, 5, 7, 6, 1, "purple", "black")
    hang(s, 5, 4, 3)
    hang(s, 5, 4, 5)
    s.merchant(5.5, 1, 3.5, "jeweler", YAW["south"])
    lamp_post(s, 1, 9, h=2)
    lamp_post(s, 9, 9, h=2)
    flower_bed(s, 0, 2, 0, 6)
    flower_bed(s, 10, 2, 10, 6)
    save(s)


def tailor(P):
    """Alfaiataria: teares, rolos de tecido, roupas nos manequins e o Alfaiate."""
    s = B("tailor", S_LOT, 24, S_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 1, 1, 9, 7
    top = shell(s, x0, z0, x1, z1, floors=1, fh=5)
    entry(s, 3, 1, z1, "south", torches=False)
    for x in (6, 7, 8):
        s.set(x, 2, z1, P.window)
        s.set(x, 3, z1, P.window)
    windows(s, x0, z0, x1, z1, 2, sides="nwe")
    roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[-1])
    awning(s, 5, 9, 8, 8, 4, r.choice(P.awnings))
    s.shop_sign(2, 3, 8, "east", ["tailor"])
    s.set(2, 1, 2, st("loom", facing="south"))
    s.set(3, 1, 2, st("loom", facing="south"))
    for i, col in enumerate(["red", "blue", "yellow", "green", "purple", "white"]):
        x = 5 + i % 4
        y = 1 + i // 4
        s.set(x, y, 2, mc(f"{col}_wool"))
    s.chest(8, 1, 3, "house", "west")
    s.set(5, 1, 4, P.w("planks", P.wood2))
    s.set(6, 1, 4, P.w("planks", P.wood2))
    s.set(5, 2, 4, mc("white_carpet"))
    s.set(6, 2, 4, mc("light_blue_carpet"))
    s.armor_stand(2.5, 1, 5.5, YAW["east"], chest="leather_chestplate", legs="leather_leggings", dye=0x3B5BA5)
    s.armor_stand(8.5, 1, 5.5, YAW["west"], head="leather_helmet", chest="leather_chestplate", dye=0xA0332B)
    rug(s, 3, 5, 7, 6, 1, r.choice(["pink", "light_blue", "lime"]))
    hang(s, 5, 4, 4)
    s.merchant(5.5, 1, 3.5, "tailor", YAW["south"])
    paving(s, 0, 8, 10, 10)
    path(s, 3, 8, 3, 10)
    save(s)


def alley(P):
    """Beco do Submundo: viela estreita entre muros altos, caixotes, teias e uma escada escondida atrás dos barris
    que desce ao porão do Receptador (quadro do Submundo, baús e o "fence"). Rua em y=5 (GROUND_OFFSET)."""
    s = B("alley", S_LOT, 24, S_LOT, P)
    G = GROUND_OFFSET["alley"]
    dark = "minecraft:polished_blackstone_bricks" if P.name != "desert" else "minecraft:smooth_red_sandstone"
    # ---- Porão (y0..4): paredes, piso, teto (o teto é a base da viela).
    for y in range(0, G):
        for x, z in perim(0, 0, 10, 10):
            s.set(x, y, z, P.base if (x + y + z) % 3 else dark)
    s.fill(1, 0, 1, 9, 0, 9, P.w("planks", P.wood2))
    s.fill(0, G - 1, 0, 10, G - 1, 10, P.base2)
    s.air(1, 1, 1, 9, G - 2, 9)
    # ---- Viela (rua em y=G): muros dos prédios vizinhos dos dois lados, fundo fechado.
    floor_mix = ([("minecraft:sandstone", 4), ("minecraft:cut_sandstone", 3), ("minecraft:smooth_sandstone", 3)]
                 if P.name == "desert" else
                 [("minecraft:cracked_stone_bricks", 3), ("minecraft:mossy_cobblestone", 3), (P.base, 4)])
    for x in range(0, 11):
        for z in range(0, 11):
            s.set(x, G, z, s.pick(floor_mix))
    for y in range(G + 1, G + 8):
        for z in range(0, 11):
            for x in (0, 1, 9, 10):
                s.set(x, y, z, P.base if y < G + 3 else (P.wall if P.timber else P.base2))
        for x in range(2, 9):
            s.set(x, y, 0, P.base if y < G + 3 else (P.wall if P.timber else P.base2))
    for z in range(0, 11):
        for x in (0, 1, 9, 10):
            s.set(x, G + 8, z, slab(P.stone_slab))
    # Varal com lanternas de alma atravessando a viela.
    for x in range(2, 9):
        s.set(x, G + 6, 5, P.fence)
    s.set(5, G + 5, 5, lantern(True, soul=True))
    s.set(2, G + 2, 3, st("cobweb"))
    s.set(8, G + 5, 1, st("cobweb"))
    # Caixotes e lixo.
    for x, z, h in ((2, 2, 2), (3, 2, 1), (2, 3, 1), (8, 8, 1)):
        for y in range(h):
            s.barrel(x, G + 1 + y, z, None, "up")
    s.set(3, G + 1, 3, st("composter", level=5))
    s.set(8, G + 1, 9, "minecraft:decorated_pot")
    s.wall_banner(2, G + 4, 1, "south", UNDER_BANNER)
    # Escada escondida (x7..8): entra pelo sul e desce para o norte, escondida atrás de barris; guarda-corpo no
    # fundo para ninguém cair no vão; placa de aviso e tocha de alma na parede.
    for i, z in enumerate((6, 5, 4, 3)):
        yy = G - 1 - i
        for x in (7, 8):
            s.set(x, yy, z, stairs(mc(f"{P.wood2}_stairs"), "south"))
            for y in range(yy + 1, G + 1):
                s.set(x, y, z, AIR)
    s.set(7, G + 1, 2, P.fence)
    s.set(8, G + 1, 2, P.fence)
    s.barrel(6, G + 1, 5, None, "up")
    s.barrel(6, G + 2, 5, None, "up")
    s.barrel(6, G + 1, 4, None, "up")
    s.sign(8, G + 2, 1, "south", ["alley.1", "alley.2"], color="red")
    s.set(7, G + 3, 1, st("soul_wall_torch", facing="south"))
    # ---- Porão do Receptador.
    table(s, 4, 1, 4, ("west",))
    s.set(4, 2, 4, candle(2, "black"))
    s.merchant(4.5, 1, 2.5, "fence", YAW["south"])
    s.set(1, 1, 3, f"{NS}:faction_board[facing=east,faction=underworld]")
    s.chest(1, 1, 6, "underworld", "east")
    s.chest(1, 1, 7, "underworld", "east")
    s.barrel(9, 1, 9, "underworld", "west")
    s.barrel(9, 2, 9, None, "west")
    s.barrel(9, 1, 8, None, "west")
    s.bed(5, 1, 9, "black", "west")
    s.set(2, 1, 9, st("cobweb"))
    rug(s, 3, 5, 6, 7, 1, "red", "black")
    table(s, 4, 1, 6, ("north", "east"), top=candle(1, "red"))
    s.armor_stand(9.5, 1, 4.5, YAW["west"], head="leather_helmet", chest="chainmail_chestplate", hand="iron_sword")
    s.wall_banner(5, 3, 1, "south", UNDER_BANNER)
    s.set(2, 3, 2, st("soul_wall_torch", facing="east"))
    s.set(8, 3, 9, st("soul_wall_torch", facing="north"))
    hang(s, 5, 3, 5, soul=True)
    save(s)


def stable(P):
    """Estábulo: baias com cavalos, feno, cochos e o cavalariço."""
    s = B("stable", S_LOT, 22, S_LOT, P)
    r = s.rnd
    yard(s)
    for x in range(0, 11):
        for z in range(0, 10):
            s.set(x, 0, z, s.pick([(P.path, 5), ("minecraft:coarse_dirt" if P.name != "desert" else "minecraft:sand", 3),
                                   (P.ground, 2)]))
    x0, z0, x1, z1 = 1, 1, 9, 7
    # Estrutura aberta: postes, paredes baixas de tábua nos fundos, telhado.
    for y in range(1, 5):
        for x in range(x0, x1 + 1):
            s.set(x, y, z0, P.w("planks") if y < 4 else P.beam("x"))
        for z in range(z0, z1 + 1):
            s.set(x0, y, z, P.w("planks") if y < 4 else P.beam("z"))
            s.set(x1, y, z, P.w("planks") if y < 4 else P.beam("z"))
    for x in (x0, 5, x1):
        for y in range(1, 5):
            s.set(x, y, z1, P.post())
    for x in range(x0, x1 + 1):
        s.set(x, 4, z1, P.beam("x"))
    s.fill(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, P.floor2)
    roof(s, x0, z0, x1, z1, 4, axis="x", mat=P.roofs[0])
    # Duas baias (x2..4 e x6..8) com portões, cochos e feno.
    for x in range(2, 9):
        for z in range(2, 7):
            s.set(x, 0, z, s.pick([("minecraft:coarse_dirt" if P.name != "desert" else "minecraft:sand", 3),
                                   (P.path, 1)]))
    for z in range(2, 7):
        s.set(5, 1, z, P.fence)
    s.set(5, 1, 7, P.post())
    for x in (2, 3, 4, 6, 7, 8):
        s.set(x, 1, z1, P.fence)
    s.set(3, 1, z1, fence_gate(P.wood, "south"))
    s.set(7, 1, z1, fence_gate(P.wood, "south"))
    s.set(2, 1, 2, st("hay_block", axis="y"))
    s.set(8, 1, 2, st("hay_block", axis="y"))
    s.set(4, 1, 2, st("water_cauldron", level=3))
    s.set(6, 1, 2, st("water_cauldron", level=3))
    s.set(2, 2, 2, st("hay_block", axis="x"))
    hang(s, 3, 3, 4)
    hang(s, 7, 3, 4)
    s.horse(3.5, 1, 4.5, r.choice((0, 1, 2, 256, 513)), YAW["south"])
    s.horse(7.5, 1, 4.5, r.choice((3, 4, 5, 258, 770)), YAW["south"])
    # Pátio: feno, carroça de feno, baú e o cavalariço.
    s.chest(10, 1, 4, "stable", "west")
    s.set(10, 1, 5, st("hay_block", axis="z"))
    s.set(10, 1, 6, st("hay_block", axis="z"))
    s.set(10, 2, 5, st("hay_block", axis="z"))
    s.set(0, 1, 8, st("composter", level=4))
    s.set(1, 1, 9, st("water_cauldron", level=3))
    s.citizen(5.5, 1, 8.5, YAW["north"])
    s.standing_sign(9, 1, 9, "south", ["stable"])
    lamp_post(s, 0, 10, h=2)
    save(s)


def garden(P):
    """Horta / fazendinha: canteiros com plantações, canal de água, espantalho e o barracão."""
    s = B("garden", S_LOT, 18, S_LOT, P)
    r = s.rnd
    yard(s)
    crops = [("wheat", 7), ("carrots", 7), ("potatoes", 7), ("beetroots", 3)]
    for z in range(1, 8):
        for x in range(1, 10):
            if x == 5:
                s.set(x, 0, z, water())
                continue
            s.set(x, 0, z, st("farmland", moisture=7))
            crop, age = crops[(x > 5) * 2 + (z > 4)]
            s.set(x, 1, z, st(crop, age=age))
    for x in range(0, 11):
        s.set(x, 1, 0, P.fence)
    for z in range(0, 9):
        s.set(0, 1, z, P.fence)
        s.set(10, 1, z, P.fence)
    for x in range(0, 11):
        s.set(x, 0, 8, P.path)
    s.set(5, 0, 8, P.w("planks"))
    # Espantalho.
    s.armor_stand(2.5, 1, 4.5, YAW["south"], head="carved_pumpkin", chest="leather_chestplate", dye=0x6B4A2B)
    s.set(2, 0, 4, "minecraft:grass_block")
    s.set(2, 1, 4, AIR)
    # Barracão (3x2) com baú, compostor e ferramentas.
    for x in (7, 8, 9):
        s.set(x, 0, 9, P.base)
        s.set(x, 0, 10, P.base)
    for x, z in ((7, 10), (9, 10)):
        for y in (1, 2):
            s.set(x, y, z, P.post())
    for x in (7, 8, 9):
        s.set(x, 3, 9, slab(mc(f"{P.wood}_slab")))
        s.set(x, 3, 10, slab(mc(f"{P.wood}_slab")))
    s.chest(8, 1, 10, "garden", "north")
    s.set(9, 1, 9, st("composter", level=6))
    s.set(7, 1, 9, st("hay_block", axis="y"))
    s.set(8, 2, 9, lantern(True))
    s.barrel(1, 1, 9)
    s.barrel(1, 1, 10)
    s.set(2, 1, 10, "minecraft:pumpkin")
    s.set(3, 1, 10, "minecraft:melon")
    lamp_post(s, 5, 10, h=2)
    s.citizen(4.5, 1, 9.5, YAW["north"])
    save(s)


# =================================================================================================
#  PRÉDIOS GRANDES (lotes L 22x22)
# =================================================================================================
def guild_hall(P):
    """Salão da Guilda dos Aventureiros: balcão com a Recepcionista, quadros de missões e da Guilda, canto da
    taverna com lareira e troféus; no andar de cima o escritório do Mestre da Guilda e a sala de reuniões."""
    s = B("guild_hall", L_LOT, 34, L_LOT, P)
    r = s.rnd
    paving(s, 0, 0, 21, 21)
    x0, z0, x1, z1 = 1, 1, 20, 15
    top = shell(s, x0, z0, x1, z1, floors=2, fh=6, lower=P.base2 if P.timber else None,
                floor=P.floor, floor2=P.floor2)
    # Entrada em arco (4 de largura) e alpendre com colunas.
    for x in range(9, 13):
        for y in (1, 2, 3):
            s.set(x, y, z1, AIR)
    s.set(9, 4, z1, stairs(mc(f"{P.wood2}_stairs"), "west", "top"))
    s.set(12, 4, z1, stairs(mc(f"{P.wood2}_stairs"), "east", "top"))
    s.sign(10, 5, z1 + 1, "south", ["guild_hall.1", "guild_hall.2"], color="yellow", glow=True)
    s.sign(11, 5, z1 + 1, "south", ["guild_hall.3", "guild_hall.4"], color="yellow", glow=True)
    windows(s, x0, z0, x1, z1, 2, skip=[(10, z1), (11, z1)], h=3)
    windows(s, x0, z0, x1, z1, 8, h=2)
    for x in (7, 14):
        for y in range(1, 6):
            s.set(x, y, 18, P.post())
    for x in range(6, 16):
        for z in (16, 17, 18, 19):
            s.set(x, 6, z, slab(mc(f"{P.wood2}_slab")) if not P.flat else slab(P.smooth_slab))
    for x in (8, 13):
        s.set(x, 5, 17, lantern(True))
    for x in (8, 13):
        s.wall_banner(x, 4, z1 + 1, "south", GUILD_BANNER)
    for x in range(9, 13):
        for z in range(16, 22):
            s.set(x, 0, z, P.pave_main)
    # Pátio: mastros com estandartes, bancos, canteiros e postes.
    flag_pole(s, 3, 19, 1, 4, GUILD_BANNER)
    flag_pole(s, 18, 19, 1, 4, GUILD_BANNER)
    bench(s, 5, 20, "south", 2)
    bench(s, 15, 20, "south", 2)
    flower_bed(s, 1, 17, 2, 17)
    flower_bed(s, 19, 17, 20, 17)
    lamp_post(s, 0, 21)
    lamp_post(s, 21, 21)
    s.adventurer(16.5, 1, 18.5, YAW["west"])
    # ---------------- Térreo
    # Balcão de recepção (bloco da guilda) com a Recepcionista atrás.
    for x in range(6, 16):
        s.set(x, 1, 6, f"{NS}:guild_counter[facing=south]")
    s.mob(10.5, 1, 4.5, f"{NS}:guild_receptionist", YAW["south"])
    # Escritório atrás do balcão: estantes, arquivo, baú.
    shelves(s, 9, 2, 10, 2, 1, 2)
    shelves(s, 15, 2, 17, 2, 1, 3)
    s.chest(12, 1, 2, "guild", "south")
    s.barrel(13, 1, 2, None, "south")
    s.barrel(14, 1, 2, None, "south")
    s.set(11, 1, 2, st("lectern", facing="south", has_book="false", powered="false"))
    s.set(18, 1, 2, "minecraft:cartography_table")
    s.set(19, 1, 2, "minecraft:fletching_table")
    s.set(19, 1, 4, st("anvil", facing="north"))
    s.set(13, 2, 2, candle(3, "yellow"))
    # Escada para o andar de cima (degraus x8..3, sobe para oeste).
    for i, x in enumerate(range(8, 2, -1)):
        for z in (2, 3):
            s.set(x, 1 + i, z, stairs(mc(f"{P.wood2}_stairs"), "west"))
            for y in range(1, 1 + i):
                s.set(x, y, z, P.w("planks", P.wood2))
    for x in (4, 5):
        for z in (2, 3):
            s.set(x, 6, z, AIR)
    s.barrel(7, 1, 4, None, "up")
    s.barrel(8, 1, 4, None, "up")
    # Ala oeste: quadros de missões e da Guilda.
    s.set(2, 1, 9, f"{NS}:quest_board[facing=east]")
    s.set(2, 1, 10, f"{NS}:quest_board[facing=east]")
    s.set(2, 1, 12, f"{NS}:faction_board[facing=east,faction=guild]")
    for z in (8, 11, 13):
        shelves(s, 2, z, 2, z, 1, 2)
        s.set(2, 3, z, lantern())
    s.set(4, 1, 14, st("lectern", facing="north", has_book="false", powered="false"))
    s.wall_banner(2, 4, 10, "east", GUILD_BANNER)
    bench(s, 5, 10, "east", 3, along="z")
    # Ala leste: canto da taverna, lareira e troféus.
    table(s, 16, 1, 9, ("west", "east"))
    table(s, 16, 1, 13, ("west", "east"))
    for z in (10, 11, 12):
        for y in range(1, 6):
            s.set(19, y, z, P.chimney)
    s.set(19, 1, 11, campfire(facing="west"))
    s.set(19, 2, 11, AIR)
    s.set(18, 0, 11, P.tile)
    s.set(18, 4, 11, wall_skull("dragon", "west"))
    s.set(19, 3, 8, wall_skull("skeleton", "west"))
    s.set(19, 3, 14, wall_skull("zombie", "west"))
    s.set(19, 4, 13, wall_skull("creeper", "west"))
    s.armor_stand(18.5, 1, 7.5, YAW["west"], head="iron_helmet", chest="iron_chestplate", legs="iron_leggings",
                  feet="iron_boots", hand="iron_sword", offhand="shield")
    s.armor_stand(18.5, 1, 14.5, YAW["west"], head="golden_helmet", chest="chainmail_chestplate", hand="bow")
    s.barrel(15, 1, 14, "tavern", "up")
    # Tapete do arco até o balcão e lustres.
    for z in range(7, 15):
        for x in (10, 11):
            s.set(x, 1, z, mc(f"{P.carpet}_carpet"))
        for x in (9, 12):
            s.soft(x, 1, z, mc("yellow_carpet"))
    for x, z in ((6, 10), (10, 10), (15, 10), (10, 4)):
        hang(s, x, 4, z, 1)
    s.adventurer(4.5, 1, 11.5, YAW["west"])
    s.adventurer(14.5, 1, 11.5, YAW["east"])
    s.cat(17.5, 1, 11.5, "black", YAW["west"])
    # ---------------- Andar de cima (piso y6)
    for z in range(2, 15):
        for y in range(7, 12):
            s.set(11, y, z, P.w("planks", P.wood2) if z != 8 else AIR)
    s.door(11, 7, 8, "east", P.wood2)
    # Escritório do Mestre (leste).
    shelves(s, 12, 2, 19, 2, 7, 9)
    for x in (15, 16, 17):
        s.set(x, 7, 6, slab(mc(f"{P.wood2}_slab"), "top"))
    s.set(15, 8, 6, candle(3, "white"))
    s.set(17, 8, 6, potted("fern"))
    s.chest(19, 7, 4, "guild", "west")
    s.barrel(19, 7, 5, None, "west")
    s.set(13, 7, 13, "minecraft:cartography_table")
    s.set(19, 7, 13, st("lectern", facing="west", has_book="false", powered="false"))
    s.armor_stand(18.5, 7, 8.5, YAW["west"], head="iron_helmet", chest="golden_chestplate", hand="golden_sword")
    rug(s, 13, 8, 18, 12, 7, P.carpet2, "yellow")
    s.wall_banner(12, 9, 14, "north", GUILD_BANNER)
    s.wall_banner(18, 9, 14, "north", GUILD_BANNER)
    s.mob(16.5, 7, 4.5, f"{NS}:guild_master", YAW["south"])
    # Sala de reuniões e dormitório (oeste).
    for x in range(4, 9):
        table(s, x, 7, 9, ())
    for x in range(4, 9):
        chair(s, x, 7, 8, "north")
        chair(s, x, 7, 10, "south")
    for x in (2, 4, 6, 8):
        s.bed(x, 7, 14, BED_COLORS[x % len(BED_COLORS)], "north")
    s.chest(9, 7, 14, "house", "north")
    # Guarda-corpo em volta do vão da escada.
    for x in (3, 4, 5, 6):
        s.set(x, 7, 4, P.fence)
    s.set(6, 7, 2, P.fence)
    s.set(6, 7, 3, P.fence)
    s.adventurer(7.5, 7, 11.5, YAW["north"])
    for x, z in ((6, 7), (15, 10)):
        hang(s, x, 11, z)
    # Telhado, chaminé da lareira e mastros com o estandarte da Guilda nas pontas da cumeeira (cúpula no deserto).
    rt = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roof_civic or None)
    chimney(s, 19, 11, top, max(rt - 1, top + 3))
    if P.flat:
        dome(s, 10.5, 8, rt, 4, P.dome)
    else:
        for x in (x0, x1 - 1):
            flag_pole(s, x, 8, rt + 1, 2, GUILD_BANNER)
    save(s)


def tavern(P):
    """Taverna do Grifo Bêbado: balcão com o Taverneiro, mesas, lareira, cozinha; quartos em cima;
    jardim de cerveja na frente."""
    s = B("tavern", L_LOT, 30, L_LOT, P)
    r = s.rnd
    paving(s, 0, 0, 21, 21)
    x0, z0, x1, z1 = 1, 1, 18, 13
    top = shell(s, x0, z0, x1, z1, floors=2, fh=5)
    s.double_door(9, 1, z1, "south", "east", P.wood2)
    windows(s, x0, z0, x1, z1, 2, skip=[(9, z1), (10, z1)])
    windows(s, x0, z0, x1, z1, 7)
    s.sign(9, 3, z1 + 1, "south", ["tavern.1", "tavern.2"], color="brown")
    s.sign(10, 3, z1 + 1, "south", ["tavern.3", "tavern.4"], color="brown")
    s.set(8, 3, z1 + 1, wall_torch("south"))
    s.set(11, 3, z1 + 1, wall_torch("south"))
    # ---- Térreo
    # Balcão do bar (troncos) com o Taverneiro; barris na parede.
    for x in range(3, 11):
        s.set(x, 1, 5, log(mc(f"stripped_{P.wood2}_log"), "x"))
    s.set(3, 2, 5, candle(2, "orange"))
    s.set(9, 2, 5, potted("dandelion"))
    for x in range(2, 12):
        for y in (1, 2):
            s.barrel(x, y, 2, "tavern" if (x, y) in ((4, 1), (8, 1)) else None, "south")
    s.merchant(6.5, 1, 3.5, "innkeeper", YAW["south"])
    # Cozinha (leste do balcão).
    s.set(13, 1, 2, st("smoker", facing="south", lit="true"))
    s.set(14, 1, 2, st("furnace", facing="south", lit="true"))
    s.set(16, 1, 2, campfire(facing="south"))
    s.set(15, 1, 2, "minecraft:crafting_table")
    s.set(17, 1, 2, st("hay_block", axis="y"))
    s.barrel(13, 1, 4, "market", "up")
    s.set(14, 1, 4, st("water_cauldron", level=3))
    # Mesas com cadeiras.
    for tx, tz in ((4, 8), (8, 8), (12, 8), (4, 11), (12, 11)):
        table(s, tx, 1, tz, ("west", "east"))
    s.set(4, 2, 8, candle(1, "white"))
    # Lareira na parede oeste.
    for z in (8, 9, 10):
        for y in range(1, 5):
            s.set(2, y, z, P.chimney)
    s.set(2, 1, 9, campfire(facing="east"))
    s.set(2, 2, 9, AIR)
    # Canto do bardo.
    s.set(7, 1, 12, "minecraft:note_block")
    s.set(8, 1, 12, st("jukebox", has_record="false"))
    # Escada para os quartos (x16..17, sobe para o norte).
    for i, z in enumerate((12, 11, 10, 9, 8)):
        for x in (16, 17):
            s.set(x, 1 + i, z, stairs(mc(f"{P.wood}_stairs"), "north"))
            for y in range(1, 1 + i):
                s.set(x, y, z, P.w("planks"))
    for x in (16, 17):
        for z in (9, 10):
            s.set(x, 5, z, AIR)
    for x, z in ((6, 8), (10, 10), (13, 6)):
        hang(s, x, 3, z, 1)
    s.adventurer(8.5, 1, 9.5, YAW["north"])
    s.adventurer(5.5, 1, 11.5, YAW["east"])
    s.citizen(12.5, 1, 9.5, YAW["west"])
    s.cat(3.5, 1, 10.5, "red", YAW["east"])
    # ---- Andar de cima: corredor (z8..9) e 4 quartos.
    wall_b = P.w("planks")
    for x in range(2, 16):
        for y in range(6, 10):
            s.set(x, y, 7, wall_b)
            s.set(x, y, 10, wall_b)
    for z in list(range(2, 7)) + list(range(11, 13)):
        for y in range(6, 10):
            s.set(8, y, z, wall_b)
            s.set(15, y, z, wall_b)
    for z in range(2, 13):
        for y in range(6, 10):
            if z in (8, 9):
                continue
            s.set(15, y, z, wall_b)
    for x, z, f in ((5, 7, "north"), (12, 7, "north"), (5, 10, "south"), (12, 10, "south")):
        s.door(x, 6, z, f)
    for (bx, bz, f), (cx, cz) in (((3, 3, "north"), (6, 2)), ((10, 3, "north"), (13, 2)),
                                  ((3, 11, "south"), (6, 12)), ((10, 11, "south"), (13, 12))):
        s.bed(bx, 6, bz, r.choice(BED_COLORS), f)
        s.chest(cx, 6, cz, "house", "south" if cz == 2 else "north")
        rug(s, bx + 1, min(bz, bz + (1 if f == "south" else -1)), bx + 3,
            max(bz, bz + (1 if f == "south" else -1)), 6, r.choice(["red", "green", "light_blue", "yellow"]))
    for x, z in ((5, 4), (12, 4), (5, 11), (12, 11), (10, 8)):
        hang(s, x, 9, z)
    rt = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[0])
    chimney(s, 2, 9, 5, max(rt - 2, top + 2))
    chimney(s, 16, 2, 2, max(rt - 3, top + 2))
    # ---- Jardim de cerveja na frente.
    for x in range(0, 22):
        for z in range(14, 22):
            s.set(x, 0, z, s.pick([(P.path, 3), (P.base2, 2), (P.ground, 1)]) if not P.flat else
                  s.pick(P.pave))
    for x in range(9, 11):
        for z in range(14, 22):
            s.set(x, 0, z, P.pave_main)
    for tx, tz in ((3, 16), (6, 19), (14, 16), (17, 19)):
        table(s, tx, 1, tz, ("north", "south"))
    for x, z in ((1, 15), (20, 15)):
        s.barrel(x, 1, z, None, "up")
        s.barrel(x, 2, z, None, "up")
    s.set(20, 0, 20, "minecraft:grass_block")
    tree(s, 20, 20)
    lamp_post(s, 8, 21)
    lamp_post(s, 11, 21)
    flower_bed(s, 0, 21, 5, 21)
    flower_bed(s, 13, 21, 17, 21)
    for z in (8, 12):
        lamp_post(s, 20, z, h=2)
    s.barrel(20, 1, 3, None, "up")
    s.barrel(20, 1, 4, None, "up")
    s.barrel(21, 1, 3, None, "up")
    s.set(20, 1, 6, st("hay_block", axis="y"))
    s.adventurer(6.5, 1, 17.5, YAW["east"])
    save(s)


def market(P):
    """Mercado: barracas com toldos listrados ao redor de um bebedouro; o Verdureiro e vendedores."""
    s = B("market", L_LOT, 16, L_LOT, P)
    r = s.rnd
    paving(s, 0, 0, 21, 21)

    def stall(x0, z0, front, goods, colors, who=None):
        """Barraca 6x4: postes nos cantos, toldo listrado, balcão na frente e mercadorias."""
        x1, z1 = x0 + 5, z0 + 3
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in (1, 2, 3):
                s.set(x, y, z, P.fence)
        awning(s, x0, x1, z0, z1, 4, colors)
        zc = z1 if front == "south" else z0
        zb = z0 if front == "south" else z1
        for i, x in enumerate(range(x0 + 1, x1)):
            s.set(x, 1, zc, goods[i % len(goods)])
        s.barrel(x0 + 1, 1, zb, "market" if r.random() < 0.5 else None, "up")
        s.barrel(x1 - 1, 1, zb, None, "up")
        s.set(x0 + 2, 1, zb, goods[-1])
        hang(s, x0 + 3, 3, (z0 + z1) // 2)
        zm = (z0 + z1) // 2 + (0 if front == "south" else 1)
        if who == "grocer":
            s.merchant(x0 + 3.5, 1, zm + 0.5, "grocer", YAW[front])
        elif who == "citizen":
            s.citizen(x0 + 3.5, 1, zm + 0.5, YAW[front])

    produce = ["minecraft:pumpkin", st("hay_block", axis="y"), "minecraft:melon", st("composter", level=8)]
    fish = [st("barrel", facing="up", open="true"), "minecraft:dried_kelp_block", st("water_cauldron", level=3),
            "minecraft:packed_ice" if P.name == "snowy" else st("barrel", facing="up", open="true")]
    cloth = ["minecraft:white_wool", "minecraft:red_wool", st("loom", facing="south"), "minecraft:blue_wool"]
    pottery = ["minecraft:decorated_pot", "minecraft:flower_pot", "minecraft:terracotta", potted("cactus")]
    bread = [st("hay_block", axis="x"), "minecraft:cake", st("barrel", facing="up", open="false"),
             st("smoker", facing="south", lit="false")]
    stall(1, 1, "south", produce, P.awnings[0], "grocer")
    stall(8, 1, "south", fish, P.awnings[1], "citizen")
    stall(15, 1, "south", cloth, P.awnings[2], None)
    stall(1, 12, "north", pottery, P.awnings[3], None)
    stall(15, 12, "north", bread, P.awnings[4], "citizen")
    # Bebedouro central com poste.
    for x, z in perim(9, 7, 12, 10):
        s.set(x, 1, z, P.rim)
    for x in (10, 11):
        for z in (8, 9):
            s.set(x, 0, z, P.base2)
            s.set(x, 1, z, water())
    lamp_double(s, 10, 6, "x")
    for x in (8, 13):
        bench(s, x, 8, "west" if x == 8 else "east", 2, along="z")
    # Carroça de mercadorias e caixotes soltos.
    s.set(6, 1, 9, st("hay_block", axis="x"))
    s.set(5, 1, 9, st("hay_block", axis="x"))
    s.set(5, 2, 9, "minecraft:pumpkin")
    s.barrel(16, 1, 8, None, "up")
    s.barrel(17, 1, 8, None, "up")
    s.barrel(16, 2, 8, None, "up")
    # Portal de entrada com placa pendurada.
    for x in (8, 13):
        for y in range(1, 5):
            s.set(x, y, 20, P.post())
    for x in range(8, 14):
        s.set(x, 5, 20, P.beam("x"))
    s.hanging_sign(10, 4, 20, "south", ["market"], color="black")
    s.hanging_sign(11, 4, 20, "south", ["market"], color="black")
    lamp_post(s, 0, 21)
    lamp_post(s, 21, 21)
    s.citizen(11.5, 1, 15.5, YAW["north"])
    s.cat(4.5, 1, 7.5, "ragdoll")
    save(s)


def academy(P):
    """Academia Arcana: biblioteca de 2 andares (com o Escriba e o quadro da Academia) e a torre redonda:
    sala de encantamento, estudo, laboratório e observatório no topo."""
    s = B("academy", L_LOT, 48, L_LOT, P)
    r = s.rnd
    yard(s)
    stone = P.civic
    # ---- Biblioteca (x1..10, z5..15)
    x0, z0, x1, z1 = 1, 5, 10, 15
    top = shell(s, x0, z0, x1, z1, floors=2, fh=6, wall=stone if not P.timber else P.wall,
                lower=stone if P.timber else None)
    entry(s, 5, 1, z1, "south", P.wood2)
    windows(s, x0, z0, x1, z1, 2, sides="ws", skip=[(5, z1)], glass=pane("light_blue"))
    windows(s, x0, z0, x1, z1, 8, sides="wsn", glass=pane("light_blue"))
    s.shop_sign(4, 3, z1 + 1, "east", ["academy.1", "academy.2"], color="purple")
    roof(s, x0, z0, x1, z1, top, axis="z", mat=P.roof_civic or None)
    # Estantes, mesas de leitura, o Escriba e o quadro da Academia.
    shelves(s, 2, 6, 2, 13, 1, 4)
    for z in (8, 11):
        shelves(s, 4, z, 6, z, 1, 3)
    for z in (7, 10, 13):
        table(s, 8, 1, z, ("west",), top=candle(r.choice((1, 2, 3)), "white"))
    s.merchant(5.5, 1, 13.5, "scribe", YAW["south"])
    s.set(7, 1, 14, f"{NS}:faction_board[facing=north,faction=academy]")
    s.set(3, 1, 14, st("lectern", facing="east", has_book="false", powered="false"))
    s.chest(9, 1, 6, "academy", "west")
    s.wall_banner(2, 5, 10, "east", ACADEMY_BANNER)
    hang(s, 5, 5, 10)
    # Escada para o 2º andar da biblioteca (x9..9, sobe para o norte).
    for i, z in enumerate((14, 13, 12, 11, 10, 9)):
        s.set(9, 1 + i, z, stairs(mc(f"{P.wood}_stairs"), "north"))
        for y in range(1, 1 + i):
            s.set(9, y, z, P.w("planks"))
    for z in (10, 11):
        s.set(9, 6, z, AIR)
    # 2º andar da biblioteca.
    shelves(s, 2, 6, 7, 6, 7, 10)
    shelves(s, 2, 7, 2, 14, 7, 9)
    table(s, 5, 7, 10, ("west", "east"), top=candle(2, "purple"))
    s.chest(3, 7, 13, "academy", "east")
    s.set(6, 7, 14, st("lectern", facing="north", has_book="false", powered="false"))
    rug(s, 4, 9, 7, 12, 7, "purple", "magenta")
    hang(s, 5, 11, 9)
    # ---- Torre redonda (centro 15,9; raio 5)
    cx, cz, R = 15, 9, 5
    TOP = 24
    for x, z, d in disk(cx, cz, R + 0.5):
        s.set(x, 0, z, P.tile if d <= R - 0.5 else stone)
        for y in range(1, TOP + 1):
            if d > R - 0.5:
                s.set(x, y, z, stone if (y % 6) else P.trim)
            elif y % 6 == 0:
                s.set(x, y, z, P.floor2)
    # Passagem biblioteca <-> torre.
    for y in (1, 2):
        for z in (8, 9, 10):
            s.set(10, y, z, AIR)
    # Janelas da torre (4 por andar) e escada de mão na parede leste.
    for fl in range(4):
        y = fl * 6 + 2
        for dx, dz, out in ((0, -R, "north"), (R, 0, "east"), (0, R, "south")):
            s.set(cx + dx, y, cz + dz, pane("purple" if fl % 2 else "light_blue"))
            s.set(cx + dx, y + 1, cz + dz, pane("purple" if fl % 2 else "light_blue"))
    # Escada de mão na parede leste, fora do círculo do altar.
    for y in range(1, TOP):
        s.set(cx + R - 1, y, cz - 1, ladder("west"))
    # Térreo: Salão da Ascensão — Altar de Ascensão PRONTO e público. O padrão 9x9 sem os cantos cabe exatamente no
    # círculo da torre (as Pedras Rúnicas de fora encostam na parede).
    for x, z, d in disk(cx, cz, R - 0.5):
        if d > 3.5:
            s.set(x, 0, z, P.trim)
    s.set(cx, 0, cz, "minecraft:crying_obsidian")
    ascension_altar(s, cx, 1, cz)
    hang(s, cx, 5, cz, soul=True)
    hang(s, cx + 2, 5, cz - 3, soul=True)
    hang(s, cx - 2, 5, cz + 3, soul=True)
    s.sign(cx - R + 1, 3, cz, "east", ["altar.1", "altar.2", "altar.3"], color="purple", glow=True)
    s.mob(cx + 1 + 0.5, 1, cz + 3 + 0.5, f"{NS}:wandering_mage", YAW["north"])
    # 2º andar: sala de encantamento (mesa no centro e anel de estantes a 2 blocos, vão a oeste) e estudos.
    s.set(cx, 7, cz, "minecraft:enchanting_table")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2 and not (dx == -2 and abs(dz) <= 1):
                for y in (7, 8):
                    s.set(cx + dx, y, cz + dz, "minecraft:bookshelf")
    for dx, dz in ((2, 2), (-2, -2), (2, -2), (-2, 2)):
        s.set(cx + dx, 9, cz + dz, candle(3, "purple"))
    s.set(cx - 3, 7, cz - 2, st("lectern", facing="east", has_book="false", powered="false"))
    s.chest(cx - 3, 7, cz + 1, "academy", "east")
    table(s, cx + 3, 7, cz + 1, ("north",), top=candle(2, "white"))
    hang(s, cx, 11, cz)
    # 3º andar: laboratório.
    s.set(cx - 1, 13, cz - 2, st("brewing_stand", has_bottle_0="true", has_bottle_1="true", has_bottle_2="false"))
    s.set(cx + 1, 13, cz - 2, st("water_cauldron", level=3))
    s.barrel(cx - 3, 13, cz, "alchemy", "east")
    s.set(cx, 13, cz + 3, st("amethyst_cluster", facing="up", waterlogged="false"))
    hang(s, cx, 17, cz, soul=True)
    # 4º andar: observatório.
    rug(s, cx - 2, cz - 2, cx + 2, cz + 2, 19, "black", "yellow")
    s.set(cx, 19, cz, "minecraft:amethyst_block")
    s.set(cx, 20, cz, st("amethyst_cluster", facing="up", waterlogged="false"))
    s.set(cx + 2, 19, cz - 1, st("lightning_rod", facing="up", powered="false", waterlogged="false"))
    hang(s, cx - 2, 23, cz, soul=True)
    # Telhado da torre: pináculo ou cúpula.
    if P.flat:
        dome(s, cx, cz, TOP + 1, R, P.dome)
    else:
        spire(s, cx, cz, TOP + 1, R + 1.2, (P.roof_civic or ROOF_DEEPSLATE)[2])
    # ---- Pátio: caminho, círculo de runas, canteiros, estandartes.
    path(s, 5, 16, 5, 21)
    for x, z, d in disk(15, 18, 2.6):
        s.set(x, 0, z, P.trim if d > 1.5 else "minecraft:crying_obsidian" if d < 0.5 else P.tile)
    s.set(15, 1, 18, st("amethyst_cluster", facing="up", waterlogged="false"))
    for dx, dz in ((3, 0), (-3, 0), (0, 3)):
        s.set(15 + dx, 0, 18 + dz, P.tile)
        lamp_post(s, 15 + dx, 18 + dz, h=1, soul=True)
    flower_bed(s, 1, 17, 3, 20, flowers=["minecraft:allium", "minecraft:cornflower", "minecraft:blue_orchid",
                                         "minecraft:azure_bluet"])
    flower_bed(s, 7, 17, 9, 20, flowers=["minecraft:allium", "minecraft:cornflower", "minecraft:blue_orchid"])
    flag_pole(s, 11, 20, 1, 4, ACADEMY_BANNER)
    flag_pole(s, 20, 15, 1, 4, ACADEMY_BANNER)
    lamp_post(s, 0, 21, soul=True)
    s.citizen(13.5, 1, 20.5, YAW["north"])
    save(s)


def church(P):
    """Igreja da Aurora (no deserto, Templo do Sol com cúpula): nave larga com o ALTAR DE ASCENSÃO público no meio
    (padrão 9x9 pronto: é só trazer o tributo), estrado com o Sacerdote, vitrais e rosácea, bancos perto da entrada,
    torre do sino na frente (entrada), sacristia com o baú, cemitério e o quadro da Igreja."""
    s = B("church", L_LOT, 48, L_LOT, P)
    yard(s)
    stone = P.civic
    x0, z0, x1, z1 = 4, 1, 16, 15          # nave 13x15; a torre fica na frente (z15..19) e divide a parede z=15
    ax, az = 10, 8                         # núcleo do altar: o padrão ocupa x6..14, z4..12
    tx0, tz0, tx1, tz1 = 8, 15, 12, 19     # torre do sino (entrada)
    sx0, sz0, sx1, sz1 = 16, 3, 20, 8      # sacristia (divide a parede leste da nave)
    H = 9
    top = shell(s, x0, z0, x1, z1, floors=1, fh=H + 1, wall=stone, base=P.base2, floor=P.tile,
                floor2=P.w("planks", P.wood2), posts=False, corner=P.base2, band=P.trim)
    # Contrafortes (menos onde a sacristia encosta) e vitrais altos entre eles.
    for z in (4, 8, 12):
        for x, f in ((x0 - 1, "east"), (x1 + 1, "west")):
            if x > x1 and sz0 <= z <= sz1:
                continue
            for y in range(1, 6):
                s.set(x, y, z, P.base2)
            s.set(x, 6, z, stairs(P.stone_stairs, f))
    for z in (6, 10, 13):
        for x in (x0, x1):
            if x == x1 and sz0 <= z <= sz1:
                continue
            for y in range(3, 7):
                s.set(x, y, z, pane(P.glass[(y + z) % len(P.glass)]))
    # Rosácea sobre o altar-mor (parede norte).
    for x in range(ax - 2, ax + 3):
        for y in range(5, 9):
            if x in (ax - 2, ax + 2) and y in (5, 8):
                continue
            s.set(x, y, z0, pane("yellow" if (abs(x - ax) <= 1 and y in (6, 7)) else "orange"))
    # Telhado da nave: termina rente à torre (sem beiral na frente); no deserto, terraço com cúpula sobre o altar.
    if P.flat:
        flat_roof(s, x0, z0, x1, z1, top)
        roof_top = dome(s, ax, az, top + 1, 4, P.dome)
    else:
        roof_top = roof(s, x0, z0, x1, z1, top, axis="z", mat=P.roof_civic, gable=stone, end_overhang=(1, 0))
    # Torre do sino (desenhada depois do telhado: limpa o que o telhado deixou dentro dela); sempre acima da cumeeira.
    TH = max(16, roof_top + 3)
    for y in range(0, TH + 1):
        for x, z in perim(tx0, tz0, tx1, tz1):
            s.set(x, y, z, P.trim if (y and y % 6 == 0) else stone)
        for x in range(tx0 + 1, tx1):
            for z in range(tz0 + 1, tz1):
                s.set(x, y, z, P.tile if y == 0 else P.w("planks", P.wood2) if y == 6 else AIR)
    # Sineira aberta para a frente e para os lados (o lado norte encosta no telhado da nave).
    for y in (TH - 4, TH - 3, TH - 2):
        for x in range(tx0 + 1, tx1):
            s.set(x, y, tz1, AIR)
        for z in range(tz0 + 1, tz1):
            s.set(tx0, y, z, AIR)
            s.set(tx1, y, z, AIR)
    s.fill(tx0, TH, tz0, tx1, TH, tz1, stone)
    s.set(ax, TH - 1, 17, st("bell", attachment="ceiling", facing="north", powered="false"))
    s.set(ax + 1, TH - 1, 16, lantern(True))
    if P.flat:
        dome(s, ax, 17, TH + 1, 2, P.dome)
    else:
        spire(s, ax, 17, TH + 1, 2.6, (P.roof_civic or ROOF_DEEPSLATE)[2])
    # Entrada (porta na torre) e passagem larga para a nave.
    entry(s, ax, 1, tz1, "south", P.wood2)
    for x in range(tx0 + 1, tx1):
        for y in (1, 2, 3):
            s.set(x, y, tz0, AIR)
    s.sign(ax - 1, 4, tz1 + 1, "south", ["church.1", "church.2"], color="orange", glow=True)
    s.sign(ax + 1, 4, tz1 + 1, "south", ["church.3"], color="orange", glow=True)
    s.wall_banner(tx0, 5, tz1 + 1, "south", CHURCH_BANNER)
    s.wall_banner(tx1, 5, tz1 + 1, "south", CHURCH_BANNER)
    # Estrado do altar-mor (norte) com o Sacerdote, a mesa de quartzo e velas.
    for x in range(x0 + 1, x1):
        for z in (2, 3):
            s.set(x, 1, z, P.civic2 if P.civic2 != stone else P.trim)
    for x in (ax - 1, ax, ax + 1):
        s.set(x, 1, 4, stairs(P.stone_stairs, "north"))
        s.set(x, 2, 2, "minecraft:chiseled_quartz_block")
    s.set(ax - 1, 3, 2, candle(4, "yellow"))
    s.set(ax + 1, 3, 2, candle(4, "white"))
    s.set(x0 + 2, 2, 2, candle(3, "orange"))
    s.set(x1 - 2, 2, 2, candle(3, "orange"))
    s.wall_banner(ax - 3, 6, z0 + 1, "south", CHURCH_BANNER)
    s.wall_banner(ax + 3, 6, z0 + 1, "south", CHURCH_BANNER)
    s.merchant(ax + 0.5, 2, 3.5, "priest", YAW["south"])
    # Altar de Ascensão PRONTO e público no meio da nave: piso com moldura, colunas com lanternas nos cantos livres.
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if max(abs(dx), abs(dz)) == 4:
                s.set(ax + dx, 0, az + dz, P.trim)
    s.set(ax, 0, az, "minecraft:crying_obsidian")
    ascension_altar(s, ax, 1, az)
    for dx, dz in ALTAR_FREE_CORNERS:
        s.set(ax + dx, 1, az + dz, P.trim)
        s.set(ax + dx, 2, az + dz, P.stone_wall)
        s.set(ax + dx, 3, az + dz, lantern(False, True))
    s.sign(x0 + 1, 3, az, "east", ["altar.1", "altar.2", "altar.3"], color="purple", glow=True)
    # Bancos perto da entrada (olhando para o altar), tapete no corredor, quadro da Igreja e água benta.
    for z in (13, 14):
        for x in (ax - 4, ax - 3, ax - 2, ax + 2, ax + 3, ax + 4):
            s.set(x, 1, z, stairs(mc(f"{P.wood2}_stairs"), "south"))
        s.soft(ax, 1, z, mc("yellow_carpet"))
    s.set(x0 + 1, 1, 14, f"{NS}:faction_board[facing=east,faction=church]")
    s.set(x1 - 1, 1, 14, st("water_cauldron", level=3))
    for x, z in ((ax - 3, 6), (ax + 3, 6), (ax - 3, 10), (ax + 3, 10), (ax, az)):
        hang(s, x, H - 1, z, 1)
    # Sacristia com o baú da Igreja (porta na parede leste da nave).
    stop = shell(s, sx0, sz0, sx1, sz1, floors=1, fh=5, wall=stone, base=P.base2, posts=False, corner=P.base2,
                 band=P.trim)
    s.door(x1, 1, 5, "east", P.wood2)
    s.chest(sx1 - 1, 1, 4, "church", "west")
    s.barrel(sx1 - 1, 1, 7, None, "west")
    s.set(sx0 + 1, 1, 7, st("brewing_stand", has_bottle_0="true", has_bottle_1="true", has_bottle_2="true"))
    s.set(sx0 + 1, 1, 4, st("lectern", facing="east", has_book="false", powered="false"))
    hang(s, sx0 + 2, 4, 5)
    window(s, sx0 + 2, 2, sz1, "south")
    roof(s, sx0 + 1, sz0, sx1, sz1, stop, axis="x", mat=P.roof_civic or None, gable=stone, end_overhang=(0, 1))
    # Cemitério cercado a oeste (lápides com flores).
    for z in range(3, 15, 3):
        s.set(1, 1, z, P.stone_wall)
        s.set(1, 2, z, slab(P.stone_slab))
        s.set(1, 0, z + 1, "minecraft:grass_block")
        s.set(1, 1, z + 1, s.rnd.choice(P.flowers))
    for x in range(0, 3):
        s.set(x, 1, 1, P.fence)
        if x != 1:
            s.set(x, 1, 16, P.fence)
    for z in range(2, 16):
        s.soft(0, 1, z, P.fence)
    s.set(1, 0, 16, P.path)
    s.set(2, 0, 19, "minecraft:grass_block")
    tree(s, 2, 19)
    # Frente: caminho, canteiros e postes; jardim lateral com banco.
    path(s, ax - 1, tz1 + 1, ax + 1, L_LOT - 1)
    flower_bed(s, 5, 20, 6, 21)
    flower_bed(s, 14, 20, 15, 21)
    lamp_post(s, tx0 - 1, 21)
    lamp_post(s, tx1 + 1, 21)
    flower_bed(s, 18, 11, 20, 13)
    lamp_post(s, 19, 16)
    bench(s, 18, 18, "east", 2, along="z")
    s.citizen(17.5, 1, 15.5, YAW["west"])
    save(s)


def barracks(P):
    """Quartel da Guarda: arsenal, refeitório e beliches; pátio de treino com alvos e bonecos."""
    s = B("barracks", L_LOT, 30, L_LOT, P)
    yard(s)
    stone = P.civic
    x0, z0, x1, z1 = 1, 1, 20, 9
    top = shell(s, x0, z0, x1, z1, floors=2, fh=5, wall=stone if P.timber else None, base=P.base2,
                lower=stone if P.timber else None)
    s.double_door(10, 1, z1, "south", "east", P.wood2)
    windows(s, x0, z0, x1, z1, 2, skip=[(10, z1), (11, z1)])
    windows(s, x0, z0, x1, z1, 6)
    s.sign(10, 3, z1 + 1, "south", ["barracks.1", "barracks.2"], color="blue")
    s.wall_banner(8, 3, z1 + 1, "south", P.banner)
    s.wall_banner(13, 3, z1 + 1, "south", P.banner)
    roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roof_civic or None)
    # Arsenal (oeste).
    for i, x in enumerate((2.5, 4.5, 6.5)):
        s.armor_stand(x, 1, 2.5, YAW["south"], head="iron_helmet", chest="iron_chestplate", legs="iron_leggings",
                      feet="iron_boots", hand="iron_sword" if i != 1 else "crossbow", offhand="shield")
    s.chest(2, 1, 8, "barracks", "east")
    s.set(5, 1, 8, st("anvil", facing="east"))
    s.set(6, 1, 8, st("grindstone", face="floor", facing="east"))
    s.barrel(8, 1, 2, None, "south")
    s.barrel(8, 2, 2, None, "south")
    # Escrivaninha do sargento.
    s.set(10, 1, 4, slab(mc(f"{P.wood2}_slab"), "top"))
    s.set(11, 1, 4, slab(mc(f"{P.wood2}_slab"), "top"))
    s.set(10, 2, 4, candle(1, "white"))
    chair(s, 10, 1, 3, "north")
    # Refeitório (leste).
    for x in range(13, 19):
        table(s, x, 1, 5, ())
        chair(s, x, 1, 4, "north")
        chair(s, x, 1, 6, "south")
    s.set(19, 1, 2, st("smoker", facing="west", lit="true"))
    s.barrel(19, 1, 3, "market", "west")
    # Escada (x13..17, z8, sobe para leste).
    for i, x in enumerate(range(14, 19)):
        s.set(x, 1 + i, 8, stairs(mc(f"{P.wood}_stairs"), "east"))
        for y in range(1, 1 + i):
            s.set(x, y, 8, P.w("planks"))
    for x in (16, 17):
        s.set(x, 5, 8, AIR)
    for x, z in ((5, 5), (10, 6), (15, 3)):
        hang(s, x, 4, z)
    # Beliches em cima.
    for x in (3, 5, 7, 9, 11):
        s.bed(x, 6, 3, "blue", "north")
        s.bed(x, 6, 7, "blue", "south")
    s.chest(13, 6, 2, "barracks", "south")
    s.barrel(13, 6, 3, None, "up")
    for x, z in ((6, 5), (12, 5)):
        hang(s, x, 9, z)
    # Pátio de treino.
    for x in range(0, 22):
        for z in range(10, 22):
            s.set(x, 0, z, s.pick([(P.path, 6), (P.base, 2), ("minecraft:coarse_dirt" if not P.flat else
                                                               "minecraft:sand", 2)]))
    for x in range(9, 13):
        for z in range(10, 22):
            s.set(x, 0, z, P.pave_main)
    for z in (12, 15, 18):
        s.set(21, 1, z, st("hay_block", axis="y"))
        s.set(21, 2, z, st("target", power=0))
    for x in (14, 15):
        s.set(x, 1, 20, P.fence)
    for x in (3.5, 6.5):
        s.armor_stand(x, 1, 13.5, YAW["north"], head="leather_helmet", chest="leather_chestplate", dye=0x8B5A2B)
    # Boneco de palha com cabeça de abóbora.
    s.set(2, 1, 17, st("hay_block", axis="y"))
    s.set(2, 2, 17, st("hay_block", axis="y"))
    s.set(2, 3, 17, st("carved_pumpkin", facing="east"))
    s.armor_stand(4.5, 1, 18.5, YAW["east"], hand="iron_sword", offhand="shield")
    flag_pole(s, 8, 21, 1, 4, P.banner)
    flag_pole(s, 13, 21, 1, 4, P.banner)
    lamp_post(s, 0, 21)
    lamp_post(s, 17, 11)
    s.guard(10.5, 1, 11.5, YAW["south"])
    s.guard(5.5, 1, 15.5, YAW["west"])
    s.guard(15.5, 1, 16.5, YAW["east"])
    save(s)


def manor(P, lord=True):
    """Mansão: hall com escadaria, sala de audiências (o Lorde/Lady), jantar; quartos e escritório em cima;
    jardim formal com chafariz na frente."""
    name = "manor" if lord else "noble_manor"
    s = B(name, L_LOT, 34, L_LOT, P)
    r = s.rnd
    yard(s)
    x0, z0, x1, z1 = 3, 2, 18, 13
    top = shell(s, x0, z0, x1, z1, floors=2, fh=6, lower=P.base2 if P.timber else None)
    s.double_door(10, 1, z1, "south", "east", P.wood2)
    windows(s, x0, z0, x1, z1, 2, skip=[(10, z1), (11, z1)])
    windows(s, x0, z0, x1, z1, 8)
    rt = roof(s, x0, z0, x1, z1, top, axis="x", mat=P.roofs[1 % len(P.roofs)])
    # Torreões nos cantos da frente.
    for tx in (x0 - 1, x1 - 1):
        for y in range(0, top + 4):
            for x, z in perim(tx, z1 - 1, tx + 2, z1 + 1):
                s.set(x, y, z, P.base2 if y < top + 3 else P.civic2)
        s.set(tx + 1, top + 3, z1, P.base2)
        if P.flat:
            dome(s, tx + 1, z1, top + 4, 1, P.dome, finial=False)
        else:
            spire(s, tx + 1, z1, top + 4, 2.2, (P.roofs[1 % len(P.roofs)] or ROOF_DEEPSLATE)[2])
        window(s, tx + 1, 3, z1 + 1, "south", glass=pane())
        window(s, tx + 1, 8, z1 + 1, "south", glass=pane())
    if lord:
        s.sign(10, 3, z1 + 1, "south", ["manor.1", "manor.2"], color="blue")
        s.wall_banner(9, 4, z1 + 1, "south", P.banner)
        s.wall_banner(12, 4, z1 + 1, "south", P.banner)
    # Hall e escadaria (x10..11, sobe para o norte até o piso y6).
    for i, z in enumerate((9, 8, 7, 6, 5, 4)):
        for x in (10, 11):
            s.set(x, 1 + i, z, stairs(mc(f"{P.wood2}_stairs"), "north"))
            for y in range(1, 1 + i):
                s.set(x, y, z, P.w("planks", P.wood2))
    for x in (10, 11):
        for z in (5, 6):
            s.set(x, 6, z, AIR)
    for z in range(5, 9):
        s.set(9, 7, z, P.fence)
        s.set(12, 7, z, P.fence)
    for z in range(10, 13):
        for x in (10, 11):
            s.set(x, 1, z, mc(f"{P.carpet}_carpet"))
    # Sala de audiências (leste) com trono.
    for z in range(3, 13):
        for y in range(1, 6):
            if z not in (7, 8):
                s.set(13, y, z, P.w("planks", P.wood2))
    rug(s, 14, 4, 16, 11, 1, P.carpet, "yellow")
    s.set(17, 1, 7, stairs(mc(f"{P.wood2}_stairs"), "east"))
    s.set(17, 1, 6, "minecraft:chiseled_quartz_block" if P.name != "snowy" else P.trim)
    s.set(17, 1, 8, "minecraft:chiseled_quartz_block" if P.name != "snowy" else P.trim)
    s.wall_banner(17, 4, 7, "west", P.banner)
    s.armor_stand(17.5, 1, 3.5, YAW["west"], head="iron_helmet", chest="golden_chestplate", hand="iron_sword")
    hang(s, 15, 5, 7, 0)
    # Sala de jantar (oeste).
    for z in range(3, 13):
        for y in range(1, 6):
            if z not in (7, 8):
                s.set(8, y, z, P.w("planks", P.wood2))
    for z in range(5, 11):
        table(s, 6, 1, z, (), top=plate(P.wood) if z % 2 else candle(2, "white"))
        chair(s, 5, 1, z, "west")
        chair(s, 7, 1, z, "east")
    s.barrel(4, 1, 3, "manor" if lord else "house", "south")
    s.set(5, 1, 3, st("smoker", facing="south", lit="false"))
    hang(s, 6, 5, 7, 0)
    # Andar de cima: quarto (leste), escritório (oeste).
    s.bed(15, 7, 4, "red", "north")
    s.bed(16, 7, 4, "red", "north")
    s.chest(17, 7, 3, "manor" if lord else "house", "west")
    s.barrel(17, 7, 6, None, "west")
    rug(s, 14, 6, 17, 11, 7, "red" if P.carpet != "red" else "purple")
    shelves(s, 4, 3, 8, 3, 7, 9)
    s.set(5, 7, 7, slab(mc(f"{P.wood2}_slab"), "top"))
    s.set(6, 7, 7, slab(mc(f"{P.wood2}_slab"), "top"))
    s.set(5, 8, 7, candle(2, "white"))
    s.set(4, 7, 11, st("lectern", facing="north", has_book="false", powered="false"))
    s.chest(4, 7, 9, "house", "east")
    for x, z in ((6, 8), (15, 8)):
        hang(s, x, 11, z)
    # Jardim formal: sebes, caminho, chafariz e pilares do portão.
    for z in range(14, 22):
        for x in (10, 11):
            s.set(x, 0, z, P.pave_main)
    for x in range(1, 21):
        if x not in (9, 10, 11, 12):
            bush(s, x, 1, 21)
    for z in range(15, 21):
        bush(s, 1, 1, z)
        bush(s, 20, 1, z)
    for x, z in perim(4, 16, 7, 19):
        s.set(x, 1, z, P.rim)
    for x in (5, 6):
        for z in (17, 18):
            s.set(x, 1, z, water())
    flower_bed(s, 14, 16, 18, 19)
    flower_bed(s, 3, 14, 8, 14)
    flower_bed(s, 13, 14, 18, 14)
    for x in (8, 13):
        s.set(x, 1, 21, P.base2)
        s.set(x, 2, 21, P.base2)
        s.set(x, 3, 21, lantern())
    lamp_post(s, 9, 15)
    lamp_post(s, 12, 15)
    if lord:
        s.mob(15.5, 1, 7.5, f"{NS}:city_lord", YAW["west"])
        s.guard(9.5, 1, 15.5, YAW["south"])
    else:
        s.citizen(15.5, 1, 7.5, YAW["west"])
        s.citizen(6.5, 1, 15.5, YAW["east"])
    s.cat(13.5, 1, 18.5, "white")
    save(s)


def park(P):
    """Parque: gramado com caminhos, coreto no centro, lago com vitórias-régias, árvores e bancos."""
    s = B("park", L_LOT, 20, L_LOT, P)
    for x in range(22):
        for z in range(22):
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, s.rnd.choice(P.flowers) if s.rnd.random() < 0.18 else "minecraft:short_grass")
    for i in range(22):
        for j in (10, 11):
            s.set(i, 0, j, P.path)
            s.set(i, 1, j, AIR)
            s.set(j, 0, i, P.path)
            s.set(j, 1, i, AIR)
    # Coreto.
    for x in range(7, 15):
        for z in range(7, 15):
            s.set(x, 0, z, P.tile)
            s.set(x, 1, z, AIR)
    for x, z in ((7, 7), (14, 7), (7, 14), (14, 14)):
        for y in range(1, 5):
            s.set(x, y, z, P.post())
    for x in range(7, 15):
        for z in range(7, 15):
            if x in (7, 14) or z in (7, 14):
                s.set(x, 5, z, P.beam("x" if z in (7, 14) else "z"))
    roof(s, 7, 7, 14, 14, 5, axis="x", mat=P.roofs[1 % len(P.roofs)], gable_window=False)
    if P.flat:
        dome(s, 10.5, 10.5, 7, 2, P.dome)
    bench(s, 9, 8, "north", 4)
    bench(s, 9, 13, "south", 4)
    hang(s, 10, 4, 10) if not P.flat else s.set(10, 4, 10, lantern(True))
    # Lago com vitórias-régias.
    for x, z, d in disk(4.5, 4.5, 3.2):
        if d > 2.3:
            s.set(x, 0, z, P.rim)
            s.set(x, 1, z, AIR)
        else:
            s.set(x, 0, z, water())
            s.set(x, 1, z, "minecraft:lily_pad" if s.rnd.random() < 0.3 else AIR)
    # Árvores e postes.
    for tx, tz in ((17, 4), (4, 17), (17, 17), (15, 2)):
        s.set(tx, 1, tz, AIR)
        tree(s, tx, tz)
    for x, z in ((9, 0), (12, 21), (0, 12), (21, 9)):
        s.set(x, 1, z, AIR)
        lamp_post(s, x, z)
    bench(s, 3, 9, "north", 2)
    bench(s, 17, 12, "south", 2)
    s.citizen(11.5, 1, 9.5, YAW["south"])
    s.citizen(3.5, 1, 12.5, YAW["east"])
    s.cat(17.5, 1, 9.5, "siamese")
    save(s)


# =================================================================================================
#  PRAÇA (51x51) — chafariz com o city_heart, quadro do Reino, jardins, bancos, estandartes
# =================================================================================================
def plaza(P, capital=False):
    name = "plaza_capital" if capital else "plaza"
    s = B(name, PLAZA, 24, PLAZA, P)
    c = PLAZA // 2
    R = 7 if capital else 5
    # Piso: avenidas cruzando, anéis em volta do chafariz, mosaico no resto.
    for x in range(PLAZA):
        for z in range(PLAZA):
            dx, dz = x - c, z - c
            d = math.hypot(dx, dz)
            if d <= R + 2.6:
                blk = P.pave_accent if d > R + 1.5 else P.pave_main
            elif abs(dx) <= 3 or abs(dz) <= 3:
                w = abs(dx) if abs(dx) <= 3 else abs(dz)
                blk = P.pave_main if w <= 1 else (P.pave_accent if w == 2 else P.curb)
            elif abs(int(round(d)) - 14) == 0 or abs(int(round(d)) - 21) == 0:
                blk = P.pave_accent
            else:
                blk = s.pick(P.pave)
            s.set(x, 0, z, blk)
    # Chafariz: borda, água com luzes no fundo, coluna central com o coração da cidade.
    for x, z, d in disk(c, c, R + 0.5):
        if d > R - 0.5:
            s.set(x, 1, z, P.rim)
        else:
            s.set(x, 0, z, P.base2)
            s.set(x, 1, z, water())
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2), (3, 3), (-3, -3), (3, -3), (-3, 3)):
        if math.hypot(dx, dz) < R - 1:
            s.set(c + dx, 0, c + dz, "minecraft:sea_lantern")
    if capital:
        # Chafariz em dois níveis.
        for x, z, d in disk(c, c, 2.5):
            s.set(x, 1, z, P.rim)
            s.set(x, 2, z, P.rim if d > 1.5 else water())
        heart_y = 5
        s.set(c, 1, c, P.base2)
        s.set(c, 2, c, P.trim)
        s.set(c, 3, c, P.trim)
        s.set(c, 4, c, P.trim)
    else:
        heart_y = 4
        s.set(c, 1, c, P.base2)
        s.set(c, 2, c, P.trim)
        s.set(c, 3, c, P.trim)
    # Kingdom (v0.9): o reino da paleta; também em NeoForgeData, que sobrevive mesmo se o BlockEntity não gravar.
    s.set(c, heart_y, c, f"{NS}:city_heart", nbt={
        "Radius": Int(city_radius(3 if capital else 2)), "CityType": "capital" if capital else "city",
        "Palette": P.name, "Kingdom": P.kingdom, "NeoForgeData": {"Kingdom": P.kingdom}})
    for dx, dz in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
        if s.rnd.random() < 0.6:
            s.set(c + dx * 3, 2, c + dz * 2, "minecraft:lily_pad")
    # Bancos olhando para o chafariz (nas diagonais, longe das avenidas).
    for sx, sz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        bx = c + sx * (R + 4)
        bz = c + sz * (R + 4)
        bench(s, bx - (0 if sx > 0 else 1), bz, "north" if sz < 0 else "south", 2)
        bench(s, bx, bz - (0 if sz > 0 else 1) + (sz * -2), "west" if sx < 0 else "east", 2, along="z")
    # Postes duplos nas diagonais e lampiões nos cantos.
    for sx, sz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        lamp_double(s, c + sx * (R + 2), c + sz * (R + 2), "x" if sx * sz > 0 else "z")
        lamp_post(s, c + sx * 23, c + sz * 23)
    # Jardins dos quadrantes.
    qs = {"nw": (c - 14, c - 14), "ne": (c + 14, c - 14), "sw": (c - 14, c + 14), "se": (c + 14, c + 14)}
    for key, (qx, qz) in qs.items():
        for x in range(qx - 4, qx + 5):
            for z in range(qz - 4, qz + 5):
                edge = x in (qx - 4, qx + 4) or z in (qz - 4, qz + 4)
                if edge:
                    s.set(x, 1, z, slab(P.stone_slab))
                    s.set(x, 0, z, P.pave_accent)
        if key == "se":
            # Quiosque do Reino com o quadro de missões do Reino.
            for x in range(qx - 3, qx + 4):
                for z in range(qz - 3, qz + 4):
                    s.set(x, 0, z, P.tile)
            for x, z in ((qx - 2, qz - 2), (qx + 2, qz - 2), (qx - 2, qz + 2), (qx + 2, qz + 2)):
                for y in range(1, 4):
                    s.set(x, y, z, P.post())
            roof(s, qx - 2, qz - 2, qx + 2, qz + 2, 4, axis="x", mat=P.roofs[0], gable_window=False)
            s.set(qx, 1, qz - 1, f"{NS}:faction_board[facing=north,faction=kingdom]")
            hang(s, qx, 3, qz, 0)
            s.standing_sign(qx - 3, 1, qz - 4, "north", [f"{name}.1", f"{name}.2"], color="blue")
            bench(s, qx - 1, qz + 1, "south", 3)
            s.guard(qx + 0.5, 1, qz - 3 + 0.5, YAW["north"])
        elif key == "ne":
            # Estátua do fundador num pedestal.
            for x in range(qx - 3, qx + 4):
                for z in range(qz - 3, qz + 4):
                    s.set(x, 0, z, "minecraft:grass_block")
                    s.set(x, 1, z, s.rnd.choice(P.flowers))
            for x in range(qx - 1, qx + 2):
                for z in range(qz - 1, qz + 2):
                    s.set(x, 1, z, P.trim if (x, z) == (qx, qz) else P.base2)
            s.set(qx, 2, qz, P.civic2)
            s.armor_stand(qx + 0.5, 3, qz + 0.5, YAW["south"], head="iron_helmet", chest="iron_chestplate",
                          legs="iron_leggings", feet="iron_boots", hand="iron_sword", offhand="shield")
        elif key == "nw":
            # Pedra de Teleporte (módulo travel) num círculo de pedra com postes de luz azul e placa.
            for x in range(qx - 3, qx + 4):
                for z in range(qz - 3, qz + 4):
                    d = math.hypot(x - qx, z - qz)
                    if d <= 1.5:
                        s.set(x, 0, z, P.trim)
                    elif d <= 3.2:
                        s.set(x, 0, z, P.pave_accent)
                    else:
                        s.set(x, 0, z, "minecraft:grass_block")
                        s.set(x, 1, z, s.rnd.choice(P.flowers))
            s.set(qx, 1, qz, TRAVEL_STONE)
            for dx, dz in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
                lamp_post(s, qx + dx, qz + dz, h=2, soul=True)
            s.standing_sign(qx + 3, 1, qz, "east", ["travel.1", "travel.2"], color="light_blue", glow=True)
        else:
            for x in range(qx - 3, qx + 4):
                for z in range(qz - 3, qz + 4):
                    s.set(x, 0, z, "minecraft:grass_block")
                    s.set(x, 1, z, s.rnd.choice(P.flowers))
            s.set(qx, 1, qz, AIR)
            tree(s, qx, qz)
            s.set(qx - 2, 1, qz + 2, AIR)
            tree(s, qx - 2, qz + 2, kind="birch" if P.tree == "oak" else None)
    # Mastros com estandartes do Reino nas entradas das avenidas.
    for a in (c - 5, c + 5):
        for (x, z, f) in ((a, 1, "north"), (a, PLAZA - 2, "south"), (1, a, "west"), (PLAZA - 2, a, "east")):
            flag_pole(s, x, z, 1, 5 if capital else 4, P.banner, f)
    # Floreiras ao longo das avenidas.
    for k in (9, 16):
        for sgn in (-1, 1):
            for (x, z) in ((c - 5, c + sgn * k), (c + 5, c + sgn * k), (c + sgn * k, c - 5), (c + sgn * k, c + 5)):
                s.set(x, 0, z, "minecraft:grass_block")
                s.set(x, 1, z, leaves(P.leaves if P.tree != "palm" else "minecraft:azalea_leaves"))
    if capital:
        # Obeliscos com lanternas nos cantos da praça real.
        for sx, sz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            ox, oz = c + sx * 20, c + sz * 20
            for y in range(1, 6):
                s.set(ox, y, oz, P.trim if y in (1, 5) else P.base2)
            s.set(ox, 6, oz, lantern())
        s.guard(c + 4.5, 1, c + 9.5, YAW["south"])
        s.guard(c - 3.5, 1, c - 8.5, YAW["north"])
        s.citizen(c - 9.5, 1, c + 4.5, YAW["east"])
    else:
        s.guard(c + 4.5, 1, c - 9.5, YAW["north"])
    s.citizen(c + 9.5, 1, c + 4.5, YAW["west"])
    s.citizen(c - 6.5, 1, c - 10.5, YAW["south"])
    s.citizen(c + 2.5, 1, c + 12.5, YAW["north"])
    s.cat(c - 12.5, 1, c + 6.5, "tabby")
    save(s)


# =================================================================================================
#  MURALHA: PORTÃO E TORRES (encaixam no corpo de muralha do Java: y1..7, anda-se em y=8)
# =================================================================================================
def city_wall_block(s):
    return s.pick(s.P.cw)


def tower(P, kind="tower"):
    """Torre 9x9 da muralha (lado de dentro em z=0, para fora em z=8). kind: tower, tower_guard, tower_corner."""
    s = B(kind, TOWER, 20, TOWER, P)
    corner = kind == "tower_corner"
    s.fill(0, 0, 0, 8, 0, 8, P.cw_top)
    for y in range(1, 13):
        for x, z in perim(0, 0, 8, 8):
            s.set(x, y, z, city_wall_block(s))
    s.fill(1, 7, 1, 7, 7, 7, P.cw_top)
    s.fill(0, 12, 0, 8, 12, 8, P.cw_top)
    for x, z in perim(0, 0, 8, 8):
        if (x + z) % 2 == 0:
            s.set(x, 13, z, city_wall_block(s))
    for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
        s.set(x, 13, z, city_wall_block(s))
        s.set(x, 14, z, lantern())
    # Escada de mão (parede oeste) atravessando o piso e o teto.
    for y in range(1, 13):
        s.set(1, y, 4, ladder("east"))
    # Saídas para o passeio da muralha (y8..9).
    sides = [(0, 1), (1, 0)] if corner else [(0, 1), (8, 1)]
    for x, z in sides:
        s.set(x, WALK, z, AIR)
        s.set(x, WALK + 1, z, AIR)
    # Seteiras nas faces de fora.
    slits = [(8, 4), (4, 8)] if corner else [(4, 8), (0, 6), (8, 6)]
    for x, z in slits:
        for y in (3, 4, 9, 10):
            s.set(x, y, z, AIR)
    if not corner:
        s.door(4, 1, 0, "north", P.wood2)
        s.set(4, 3, 0, city_wall_block(s))
    # Interior: guarita embaixo, alojamento em cima.
    s.barrel(7, 1, 7, "tower", "up")
    s.barrel(7, 1, 6, None, "up")
    s.set(6, 1, 7, st("hay_block", axis="y"))
    s.armor_stand(6.5, 1, 2.5, YAW["south"], head="iron_helmet", chest="chainmail_chestplate", hand="bow")
    hang(s, 4, 6, 4)
    s.bed(6, 8, 6, "blue", "north")
    s.barrel(3, 8, 7, None, "up")
    hang(s, 4, 11, 4)
    flag_pole(s, 4, 4, 13, 3, P.banner)
    if kind == "tower_guard":
        s.guard(6.5, 13, 6.5, YAW["south"])
    save(s)


def gatehouse(P):
    """Portão da cidade (15 ao longo da muralha x 9 para fora): passagem de 5, duas torres ocas com escada de mão,
    passeio por cima ligando os passeios da muralha, grade levantada, estandartes, placa e guardas."""
    s = B("gatehouse", GATE[0], 20, GATE[1], P)
    W, Dp = GATE
    TH = 12
    s.fill(0, 0, 0, W - 1, 0, Dp - 1, P.cw_top)
    for x in range(5, 10):
        for z in range(0, Dp):
            s.set(x, 0, z, P.pave_main if 6 <= x <= 8 else P.pave_accent)
    for tx0 in (0, 10):
        tx1 = tx0 + 4
        for y in range(1, TH + 1):
            for x, z in perim(tx0, 0, tx1, 7):
                s.set(x, y, z, city_wall_block(s))
        s.fill(tx0 + 1, 7, 1, tx1 - 1, 7, 6, P.cw_top)
        s.fill(tx0, TH, 0, tx1, TH, 7, P.cw_top)
        for x, z in perim(tx0, 0, tx1, 7):
            if (x + z) % 2 == 0:
                s.set(x, TH + 1, z, city_wall_block(s))
        lx = tx0 + 1 if tx0 == 0 else tx1 - 1
        for y in range(1, TH + 1):
            s.set(lx, y, 1, ladder("south"))
        # porta para a rua de dentro, saídas para os passeios, seteiras laterais
        s.door(tx0 + 2, 1, 0, "north", P.wood2)
        for x in (tx0, tx1):
            s.set(x, WALK, 1, AIR)
            s.set(x, WALK + 1, 1, AIR)
        ox = tx0 if tx0 == 0 else tx1
        for y in (3, 4, 9, 10):
            s.set(ox, y, 5, AIR)
        s.barrel(tx0 + 2, 1, 6, "tower", "up")
        s.armor_stand(tx0 + 2.5, 1, 4.5, YAW["east"] if tx0 == 0 else YAW["west"], head="iron_helmet",
                      chest="iron_chestplate", hand="crossbow")
        hang(s, tx0 + 2, 6, 3)
        s.bed(tx0 + 2, WALK, 5, "blue", "north")
        s.set(tx0 + 2, TH + 1, 4, lantern())
        s.wall_banner(tx0 + 2, 10, 8, "south", P.banner)
    # Passagem: arco, teto e passeio por cima.
    for z in range(0, 8):
        s.set(5, 5, z, stairs(P.stone_stairs, "west", "top"))
        s.set(9, 5, z, stairs(P.stone_stairs, "east", "top"))
        for x in range(5, 10):
            s.set(x, 6, z, city_wall_block(s))
            s.set(x, 7, z, P.cw_top)
    for x in range(5, 10):
        s.set(x, WALK, 7, city_wall_block(s) if x % 2 == 1 else AIR)
        s.set(x, WALK, 0, P.cw_wall)
    s.set(5, WALK + 1, 7, lantern())
    s.set(9, WALK + 1, 7, lantern())
    for x in (6, 7, 8):
        s.set(x, 5, 6, "minecraft:iron_bars")
    hang(s, 7, 5, 3)
    s.set(4, 3, 8, wall_torch("south"))
    s.set(10, 3, 8, wall_torch("south"))
    s.sign(7, 6, 8, "south", ["gate.1", "gate.2"], color="yellow", glow=True)
    s.guard(6.0, 1, 7.5, YAW["south"])
    s.guard(8.5, WALK, 3.5, YAW["south"])
    save(s)


# =================================================================================================
#  CASTELO DA CAPITAL (51 x 49, frente = sul): muralha, 4 torres, portão, pátio e o castelo com a sala do trono
# =================================================================================================
def castle(P):
    """Castelo Real da capital (51 x 49, frente = sul): muralha de 3 com passeio e ameias, 4 torres com pináculo,
    portão com grade, pátio (poço, estábulo, ferraria, bonecos), jardim real, alojamento dos guardas e o castelo
    de 2 andares altos — sala do trono com o Lorde e a guarda; aposentos, salão de jantar e sala de guerra."""
    W, Dp = CASTLE
    s = B("castle", W, 50, Dp, P)
    stone = P.civic
    trim = P.trim
    for x in range(W):
        for z in range(Dp):
            s.set(x, 0, z, s.pick(P.pave))
    # ---- Muralha: anel de 3 (ameias por fora, passeio no meio em y10, parapeito por dentro).
    WX0, WZ0, WX1, WZ1 = 1, 1, 49, 45
    for ring in (0, 1, 2):
        for x, z in perim(WX0 + ring, WZ0 + ring, WX1 - ring, WZ1 - ring):
            for y in range(1, 10):
                s.set(x, y, z, city_wall_block(s))
            if ring == 0 and (x + z) % 2 == 0:
                s.set(x, 10, z, city_wall_block(s))
            elif ring == 2:
                s.set(x, 10, z, P.cw_wall)
    # ---- Torres dos cantos (7x7, ocas, escada de mão, porta para o pátio, saídas para o passeio, pináculo).
    for tx, tz in ((0, 0), (W - 7, 0), (0, WZ1 - 5), (W - 7, WZ1 - 5)):
        for y in range(1, 16):
            for x, z in perim(tx, tz, tx + 6, tz + 6):
                s.set(x, y, z, city_wall_block(s))
            for x in range(tx + 1, tx + 6):
                for z in range(tz + 1, tz + 6):
                    s.set(x, y, z, P.cw_top if y == 9 else AIR)
        s.fill(tx + 1, 0, tz + 1, tx + 5, 0, tz + 5, P.cw_top)
        s.fill(tx, 16, tz, tx + 6, 16, tz + 6, P.cw_top)
        for x, z in perim(tx, tz, tx + 6, tz + 6):
            if (x + z) % 2 == 0:
                s.set(x, 17, z, city_wall_block(s))
        west = tx == 0
        north = tz == 0
        lx = tx + 1 if west else tx + 5
        for y in range(1, 17):
            s.set(lx, y, tz + 3, ladder("east" if west else "west"))
        # Porta para o pátio na face que olha para o centro (ao lado da muralha que segue em z).
        face_x = tx + 6 if west else tx
        s.door(face_x, 1, tz + 5 if north else tz + 1, "east" if west else "west", P.wood2)
        # Saídas para o passeio da muralha (o anel do meio; anda-se em y10) nas duas faces que encostam nela.
        walk_z = WZ0 + 1 if north else WZ1 - 1
        walk_x = WX0 + 1 if west else WX1 - 1
        for x, z in ((face_x, walk_z), (walk_x, tz + 6 if north else tz)):
            s.set(x, 10, z, AIR)
            s.set(x, 11, z, AIR)
        if P.flat:
            dome(s, tx + 3, tz + 3, 18, 2, P.dome)
        else:
            spire(s, tx + 3, tz + 3, 18, 3.6, (P.roof_civic or ROOF_DEEPSLATE)[2])
        hang(s, tx + 3, 9, tz + 3)
        s.barrel(tx + 3 + (1 if west else -1), 1, tz + 3 + (1 if north else -1), "tower", "up")
    # ---- Portão (x19..31, z39..47), passagem x23..27, sala da grade por cima, torreões dos lados.
    gx0, gx1, gz0, gz1 = 19, 31, 39, 47
    for y in range(1, 15):
        for x in range(gx0, gx1 + 1):
            for z in range(gz0, gz1 + 1):
                wall = x in (gx0, gx1, 22, 28) or z in (gz0, gz1)
                s.set(x, y, z, city_wall_block(s) if wall else AIR)
    for x in range(gx0 + 1, gx1):
        for z in range(gz0 + 1, gz1):
            s.set(x, 0, z, P.cw_top)
    for x in range(23, 28):
        for z in range(gz0, Dp):
            s.set(x, 0, z, P.pave_main)
        for z in range(gz0, gz1 + 1):
            for y in range(1, 7):
                s.set(x, y, z, AIR)
            s.set(x, 7, z, city_wall_block(s))
    for z in range(gz0, gz1 + 1):
        s.set(23, 6, z, stairs(P.stone_stairs, "west", "top"))
        s.set(27, 6, z, stairs(P.stone_stairs, "east", "top"))
    for x in (24, 25, 26):
        s.set(x, 6, gz1 - 1, "minecraft:iron_bars")
    s.fill(gx0, 14, gz0, gx1, 14, gz1, P.cw_top)
    for x, z in perim(gx0, gz0, gx1, gz1):
        if (x + z) % 2 == 0:
            s.set(x, 15, z, city_wall_block(s))
    for gtx, dx in ((gx0 + 1, 1), (gx1 - 1, -1)):
        for y in range(1, 15):
            s.set(gtx, y, gz0 + 1, ladder("south"))
        s.door(gtx + dx, 1, gz0, "north", P.wood2)
    for x in (21, 29):
        s.wall_banner(x, 11, gz1 + 1, "south", P.banner)
        s.set(x, 4, gz1 + 1, wall_torch("south"))
    s.sign(25, 8, gz1 + 1, "south", ["castle.1", "castle.2"], color="yellow", glow=True)
    hang(s, 25, 5, 43, 0)
    s.guard(22.5, 1, 48.5, YAW["south"])
    s.guard(28.5, 1, 48.5, YAW["south"])
    s.guard(25.5, 15, 43.5, YAW["south"])
    # ---- Pátio: poço, árvores, bonecos de treino, estábulo (oeste) e ferraria (leste).
    for x, z in perim(23, 32, 27, 36):
        s.set(x, 1, z, P.rim)
    for x in range(24, 27):
        for z in range(33, 36):
            s.set(x, 0, z, P.base2)
            s.set(x, 1, z, water())
    lamp_double(s, 25, 30, "x")
    for tx, tz in ((14, 33), (36, 33), (14, 40), (36, 40)):
        s.set(tx, 0, tz, "minecraft:grass_block")
        tree(s, tx, tz)
    for x in range(4, 11):
        for z in range(27, 38):
            s.set(x, 0, z, s.pick([(P.path, 3), ("minecraft:coarse_dirt" if not P.flat else "minecraft:sand", 2)]))
        for z in range(27, 31):
            s.set(x, 5, z, slab(mc(f"{P.wood2}_slab")) if not P.flat else slab(P.smooth_slab))
    for z in range(27, 38):
        if z not in (32, 33, 34):
            s.set(11, 1, z, P.fence)
    for y in range(1, 5):
        s.set(10, y, 30, P.post())
    s.set(4, 1, 27, st("hay_block", axis="y"))
    s.set(5, 1, 27, st("water_cauldron", level=3))
    s.set(6, 1, 27, st("hay_block", axis="x"))
    s.horse(5.5, 1, 29.5, 769, YAW["east"])
    s.horse(7.5, 1, 34.5, 1, YAW["south"])
    s.chest(4, 1, 36, "stable", "east")
    for x in range(40, 47):
        for z in range(27, 31):
            s.set(x, 5, z, slab(mc(f"{P.wood2}_slab")) if not P.flat else slab(P.smooth_slab))
    for y in range(1, 5):
        s.set(40, y, 30, P.post())
    s.set(45, 1, 27, st("blast_furnace", facing="south", lit="true"))
    s.set(44, 1, 27, "minecraft:lava_cauldron")
    s.set(43, 1, 28, st("anvil", facing="east"))
    s.set(46, 1, 29, st("grindstone", face="floor", facing="west"))
    s.chest(46, 1, 30, "barracks", "west")
    for x in (31.5, 33.5):
        s.armor_stand(x, 1, 37.5, YAW["north"], head="leather_helmet", chest="leather_chestplate", dye=0x2E4C9A)
    s.guard(32.5, 1, 39.5, YAW["north"])
    # ---- O castelo (x10..40, z4..24): 2 andares altos, pilastras, janelas altas.
    kx0, kz0, kx1, kz1 = 10, 4, 40, 24
    top = shell(s, kx0, kz0, kx1, kz1, floors=2, fh=7, wall=stone, base=P.base2, floor=P.tile,
                floor2=P.w("planks", P.wood2), posts=False, corner=trim, band=trim)
    for y in range(1, top):
        for x, z in perim(kx0, kz0, kx1, kz1):
            if (z in (kz0, kz1) and (x - kx0) % 6 == 0) or (x in (kx0, kx1) and (z - kz0) % 5 == 0):
                s.set(x, y, z, trim)
    windows(s, kx0, kz0, kx1, kz1, 2, skip=[(24, kz1), (25, kz1), (26, kz1)], h=3,
            glass=pane(P.glass[3]) if P.name != "desert" else pane("orange"))
    windows(s, kx0, kz0, kx1, kz1, 9, h=3)
    # Entrada com portas duplas sob arco, degraus e estandartes.
    for x in (24, 25, 26):
        for y in (1, 2, 3, 4):
            s.set(x, y, kz1, AIR)
    s.double_door(24, 1, kz1, "south", "east", P.wood2)
    s.set(24, 5, kz1, stairs(P.stone_stairs, "west", "top"))
    s.set(26, 5, kz1, stairs(P.stone_stairs, "east", "top"))
    for x in range(22, 29):
        for z in (kz1 + 1, kz1 + 2):
            s.set(x, 0, z, P.pave_main)
    s.sign(25, 6, kz1 + 1, "south", ["castle.1", "castle.2"], color="yellow", glow=True)
    for x in (22, 28):
        s.wall_banner(x, 5, kz1 + 1, "south", P.banner)
    # Sala do trono: colunas, tapete, estrado, trono, estandartes, lustres, o Lorde e a guarda.
    pillar = log("minecraft:quartz_pillar") if P.name != "snowy" else P.base2
    for x in (16, 34):
        for z in (8, 12, 16, 20):
            for y in range(1, 7):
                s.set(x, y, z, trim if y in (1, 6) else pillar)
    for z in range(9, kz1):
        for x in (24, 25, 26):
            s.set(x, 1, z, mc(f"{P.carpet}_carpet") if x == 25 else mc("yellow_carpet"))
    for x in range(20, 31):
        for z in (5, 6, 7):
            s.set(x, 1, z, P.civic2 if P.civic2 != stone else trim)
    for x in range(22, 29):
        s.set(x, 1, 8, stairs(P.stone_stairs, "north"))
    s.set(25, 2, 6, stairs(mc(f"{P.wood2}_stairs"), "north"))
    for x in (24, 26):
        s.set(x, 2, 6, "minecraft:chiseled_quartz_block")
        s.set(x, 3, 6, candle(3, "yellow"))
    for x in (22, 25, 28):
        s.wall_banner(x, 5, kz0 + 1, "south", P.banner)
    s.mob(25.5, 2, 7.5, f"{NS}:city_lord", YAW["south"])
    for gx, gz, f in ((21.5, 15.5, "east"), (29.5, 15.5, "west"), (21.5, 10.5, "east"), (29.5, 10.5, "west")):
        s.guard(gx, 1, gz, YAW[f])
    for x, z in ((20, 12), (30, 12), (20, 19), (30, 19), (25, 14)):
        hang(s, x, 5, z, 1)
    for z in (11, 17):
        s.wall_banner(kx0 + 1, 5, z, "east", P.banner)
        s.wall_banner(kx1 - 1, 5, z, "west", P.banner)
    # Escada para o andar de cima (canto noroeste, sobe para o norte).
    for i, z in enumerate((13, 12, 11, 10, 9, 8, 7)):
        for x in (11, 12):
            s.set(x, 1 + i, z, stairs(mc(f"{P.wood2}_stairs"), "north"))
            for y in range(1, 1 + i):
                s.set(x, y, z, P.w("planks", P.wood2))
    for x in (11, 12):
        for z in (8, 9, 10):
            s.set(x, 7, z, AIR)
    for z in range(8, 12):
        s.set(13, 8, z, P.fence)
    # Andar de cima: aposentos reais (oeste), salão de jantar (centro), sala de guerra (leste).
    for x in (20, 30):
        for z in range(kz0 + 1, kz1):
            for y in range(8, 14):
                s.set(x, y, z, stone if z not in (13, 14) else AIR)
    s.bed(16, 8, 6, "red", "north")
    s.bed(17, 8, 6, "red", "north")
    s.chest(18, 8, 5, "castle", "west")
    s.chest(14, 8, 5, "castle", "east")
    s.barrel(19, 8, 9, None, "west")
    s.barrel(19, 9, 9, None, "west")
    s.armor_stand(15.5, 8, 18.5, YAW["east"], head="golden_helmet", chest="golden_chestplate",
                  legs="golden_leggings", feet="golden_boots", hand="golden_sword")
    rug(s, 14, 9, 19, 16, 8, "purple" if P.carpet == "red" else "red", "yellow")
    for x in range(22, 29):
        table(s, x, 8, 14, (), top=candle(2, "white") if x % 3 == 0 else plate(P.wood))
        chair(s, x, 8, 13, "north")
        chair(s, x, 8, 15, "south")
    s.barrel(22, 8, 6, "manor", "south")
    s.set(23, 8, 6, st("smoker", facing="south", lit="true"))
    shelves(s, 31, 5, 39, 5, 8, 11)
    s.set(35, 8, 13, "minecraft:cartography_table")
    s.set(34, 8, 13, st("lectern", facing="south", has_book="false", powered="false"))
    s.chest(39, 8, 20, "castle", "west")
    s.armor_stand(37.5, 8, 18.5, YAW["west"], head="diamond_helmet", chest="iron_chestplate", hand="iron_sword")
    for x, z in ((16, 12), (25, 10), (25, 18), (35, 12)):
        hang(s, x, 13, z)
    s.citizen(26.5, 8, 17.5, YAW["north"])
    # Telhado, torreões nos cantos do castelo e mastro (ou cúpula) no meio.
    rt = roof(s, kx0, kz0, kx1, kz1, top, axis="x", mat=P.roof_civic, gable=stone)
    for tx, tz in ((kx0 - 1, kz1 - 1), (kx1 - 1, kz1 - 1), (kx0 - 1, kz0 - 1), (kx1 - 1, kz0 - 1)):
        for y in range(1, top + 5):
            for x, z in perim(tx, tz, tx + 2, tz + 2):
                s.set(x, y, z, stone if y % 6 else trim)
        if P.flat:
            dome(s, tx + 1, tz + 1, top + 5, 1, P.dome, finial=False)
        else:
            spire(s, tx + 1, tz + 1, top + 5, 2.2, (P.roof_civic or ROOF_DEEPSLATE)[2])
    if P.flat:
        dome(s, 25, 14, rt, 5, P.dome)
    else:
        flag_pole(s, 25, 14, rt + 1, 3, P.banner)
    # Jardim real (oeste) com lago e árvores; alojamento dos guardas (leste) com camas sob um telheiro.
    for x in range(4, 9):
        for z in range(4, 24):
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, s.rnd.choice(P.flowers))
    for z in (8, 19):
        s.set(6, 1, z, AIR)
        tree(s, 6, z)
    for x, z, d in disk(6, 13.5, 1.6):
        s.set(x, 0, z, water())
        s.set(x, 1, z, AIR)
    for x in range(42, 47):
        for z in range(4, 24):
            s.set(x, 0, z, P.tile)
        for z in range(4, 18):
            s.set(x, 5, z, slab(mc(f"{P.wood2}_slab")) if not P.flat else slab(P.smooth_slab))
    for z in (6, 9, 12, 15):
        s.bed(45, 1, z, "blue", "east")
    for z in (4, 10, 17):
        for y in range(1, 5):
            s.set(42, y, z, P.post())
    s.chest(46, 1, 17, "barracks", "west")
    hang(s, 44, 4, 10)
    lamp_post(s, 42, 20)
    s.guard(43.5, 1, 21.5, YAW["west"])
    save(s)


# =================================================================================================
#  LISTA DE PEÇAS E O QUE CADA UMA PRECISA TER (conferido na leitura de volta)
# =================================================================================================
BUILDERS = [
    (plaza, {}), (lambda P: plaza(P, True), {}), (castle, {}), (gatehouse, {}), (tower, {}),
    (lambda P: tower(P, "tower_guard"), {}), (lambda P: tower(P, "tower_corner"), {}),
    (guild_hall, {}), (tavern, {}), (market, {}), (academy, {}), (church, {}), (barracks, {}), (manor, {}),
    (lambda P: manor(P, False), {}), (park, {}),
    (smithy, {}), (alchemy, {}), (jeweler, {}), (tailor, {}), (alley, {}), (stable, {}), (garden, {}),
    (house_a, {}), (house_b, {}), (house_c, {}), (house_d, {}), (well, {}),
]

EXPECTED_SIZE = {
    "plaza": (PLAZA, PLAZA), "plaza_capital": (PLAZA, PLAZA), "castle": CASTLE, "gatehouse": GATE,
    "tower": (TOWER, TOWER), "tower_guard": (TOWER, TOWER), "tower_corner": (TOWER, TOWER),
    **{n: (L_LOT, L_LOT) for n in ("guild_hall", "tavern", "market", "academy", "church", "barracks", "manor",
                                   "noble_manor", "park")},
    **{n: (S_LOT, S_LOT) for n in ("smithy", "alchemy", "jeweler", "tailor", "alley", "stable", "garden",
                                   "house_a", "house_b", "house_c", "house_d", "well")},
}
LOTS = {n for n, sz in EXPECTED_SIZE.items() if sz in ((L_LOT, L_LOT), (S_LOT, S_LOT))} | {"plaza", "plaza_capital"}

# Conteúdo obrigatório: blocos (nome base ou "nome[prop=valor]") e entidades (id + Profession opcional).
REQUIRED = {
    "guild_hall": {"blocks": [f"{NS}:guild_counter", f"{NS}:quest_board", f"{NS}:faction_board[faction=guild]"],
                   "entities": [f"{NS}:guild_receptionist", f"{NS}:guild_master", f"{NS}:adventurer"]},
    "plaza": {"blocks": [f"{NS}:city_heart", f"{NS}:faction_board[faction=kingdom]", TRAVEL_STONE],
              "entities": [f"{NS}:city_guard", f"{NS}:citizen"]},
    "plaza_capital": {"blocks": [f"{NS}:city_heart", f"{NS}:faction_board[faction=kingdom]", TRAVEL_STONE],
                      "entities": [f"{NS}:city_guard", f"{NS}:citizen"]},
    "academy": {"blocks": [f"{NS}:faction_board[faction=academy]", "minecraft:enchanting_table", ASCENSION_CORE],
                "entities": [f"{NS}:city_merchant:scribe"]},
    "church": {"blocks": [f"{NS}:faction_board[faction=church]", "minecraft:bell", ASCENSION_CORE],
               "entities": [f"{NS}:city_merchant:priest"]},
    "alley": {"blocks": [f"{NS}:faction_board[faction=underworld]"], "entities": [f"{NS}:city_merchant:fence"]},
    "smithy": {"entities": [f"{NS}:city_merchant:blacksmith"]},
    "alchemy": {"entities": [f"{NS}:city_merchant:alchemist"]},
    "jeweler": {"entities": [f"{NS}:city_merchant:jeweler"]},
    "tailor": {"entities": [f"{NS}:city_merchant:tailor"]},
    "tavern": {"entities": [f"{NS}:city_merchant:innkeeper", f"{NS}:adventurer"]},
    "market": {"entities": [f"{NS}:city_merchant:grocer"]},
    "manor": {"entities": [f"{NS}:city_lord", f"{NS}:city_guard"]},
    "castle": {"entities": [f"{NS}:city_lord", f"{NS}:city_guard"]},
    "barracks": {"entities": [f"{NS}:city_guard"]},
    "gatehouse": {"entities": [f"{NS}:city_guard"]},
    "tower_guard": {"entities": [f"{NS}:city_guard"]},
}

MOD_BLOCKS = {f"{NS}:{b}" for b in ("city_heart", "guild_counter", "quest_board", "faction_board", "ascension_core",
                                    "ascension_pedestal", "rune_stone", "travel_stone")}
MOD_ENTITIES = {f"{NS}:{e}" for e in ("city_guard", "citizen", "city_merchant", "city_lord", "guild_receptionist",
                                      "guild_master", "adventurer", "wandering_mage")}
PROFESSIONS = {"blacksmith", "alchemist", "scribe", "jeweler", "innkeeper", "priest", "fence", "grocer", "tailor"}


# =================================================================================================
#  LOOT DOS BAÚS (chests/city/<nome>) — itens de outros módulos em sub-tabelas condicionais
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


def potion(pid, w=1, kind="potion"):
    return item(kind, w, fns=[{"function": "minecraft:set_potion", "id": f"minecraft:{pid}"}])


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def ess(element, w, lo=1, hi=2):
    return item(f"{NS}:essence_{element}", w, lo, hi)


def sub(name, w):
    return {"type": "minecraft:loot_table", "value": f"{NS}:chests/city/sub/{name}", "weight": w}


def empty(w):
    return {"type": "minecraft:empty", "weight": w}


def pool(lo, hi, entries):
    rolls = lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}
    return {"rolls": rolls, "bonus_rolls": 0, "entries": entries}


ALL_BASE = ["arcane", "fire", "water", "earth", "wind", "light", "shadow"]
CRYSTAL = f"{NS}:mana_crystal"

CHESTS = {
    "guild": [pool(2, 4, [sub("coins", 5), item("emerald", 3, 1, 3), item(CRYSTAL, 3, 1, 2), ess("arcane", 2),
                          item("map", 2), item("paper", 2, 2, 5), item("book", 2), item("gold_ingot", 2, 1, 2),
                          potion("healing", 2), item("name_tag", 1)]),
              pool(1, 1, [sub("scroll_low", 3), sub("scroll_mid", 2)]),
              pool(0, 1, [sub("ring", 1), sub("amulet", 1), sub("armory_t1", 1), empty(6)])],
    "tavern": [pool(3, 6, [item("bread", 6, 1, 4), item("cooked_beef", 3, 1, 3), item("cooked_mutton", 3, 1, 3),
                           item("baked_potato", 3, 1, 4), item("pumpkin_pie", 2, 1, 2), item("honey_bottle", 2, 1, 2),
                           item("cookie", 2, 2, 6), item("apple", 3, 1, 3), item("sweet_berries", 2, 2, 6),
                           potion("water", 2), sub("coins", 2), item("emerald", 1)]),
               pool(0, 1, [potion("regeneration", 1), potion("strength", 1), empty(4)])],
    "market": [pool(3, 6, [item("wheat", 4, 2, 6), item("carrot", 3, 1, 4), item("potato", 3, 1, 4),
                           item("beetroot", 2, 1, 4), item("melon_slice", 2, 2, 6), item("pumpkin", 1),
                           item("bread", 4, 1, 3), item("egg", 2, 1, 4), item("sugar", 2, 1, 3),
                           item("cod", 2, 1, 3), item("salmon", 2, 1, 2), item("emerald", 1, 1, 2),
                           sub("coins", 2)])],
    "smithy": [pool(3, 5, [item("iron_ingot", 5, 1, 4), item("iron_nugget", 4, 3, 9), item("coal", 5, 2, 8),
                           item("gold_nugget", 2, 2, 6), item("iron_sword", 2, fns=[ench(5, 15)]),
                           item("iron_axe", 2), item("iron_pickaxe", 1, fns=[ench(5, 15)]),
                           item("iron_helmet", 1), item("iron_chestplate", 1), item("shield", 2),
                           item("chainmail_boots", 1), item("flint", 2, 1, 3), sub("coins", 2)]),
               pool(0, 1, [sub("armory_t1", 2), item("diamond", 1), empty(5)])],
    "alchemy": [pool(3, 5, [potion("healing", 3), potion("swiftness", 2), potion("fire_resistance", 2),
                            potion("night_vision", 2), potion("regeneration", 2), potion("water_breathing", 1),
                            potion("leaping", 1), potion("healing", 1, "splash_potion"),
                            item("glass_bottle", 3, 1, 4), item("nether_wart", 2, 1, 3), item("redstone", 2, 1, 4),
                            item("glowstone_dust", 2, 1, 4), item("blaze_powder", 1), item("spider_eye", 2, 1, 2),
                            item("sugar", 2, 1, 3)]),
                pool(1, 2, [ess("arcane", 2), ess("fire", 1), ess("water", 1), ess("earth", 1), ess("wind", 1),
                            ess("light", 1), ess("shadow", 1), item(CRYSTAL, 2, 1, 2)])],
    "academy": [pool(2, 4, [item("book", 4, 1, 3), item("paper", 4, 2, 6), item("ink_sac", 2, 1, 3),
                            item("feather", 2, 1, 3), item("lapis_lazuli", 3, 2, 6),
                            item("book", 2, fns=[{"function": "minecraft:enchant_randomly"}]),
                            item("experience_bottle", 2, 1, 4), item("amethyst_shard", 2, 1, 4),
                            item(CRYSTAL, 3, 1, 3), ess("arcane", 3, 1, 3)]),
                pool(1, 2, [sub("scroll_low", 3), sub("scroll_mid", 3), sub("scroll_high", 1)]),
                pool(0, 1, [sub("focus", 1), sub("amulet", 1), empty(6)])],
    "church": [pool(2, 4, [item("candle", 3, 1, 4), item("white_candle", 2, 1, 3), potion("healing", 3),
                           potion("regeneration", 2), item("golden_carrot", 2, 1, 3), item("bread", 3, 1, 3),
                           item("gold_nugget", 3, 2, 8), item("gold_ingot", 1, 1, 2), item("book", 2),
                           item("glow_ink_sac", 1, 1, 2), item("golden_apple", 1), sub("coins", 2)]),
               pool(1, 1, [ess("light", 3, 1, 2), item(CRYSTAL, 1), sub("scroll_light", 2)])],
    "barracks": [pool(3, 5, [item("arrow", 5, 4, 12), item("bread", 4, 1, 4), item("cooked_porkchop", 3, 1, 3),
                             item("iron_sword", 2), item("bow", 2), item("crossbow", 1), item("shield", 2),
                             item("iron_helmet", 1), item("chainmail_chestplate", 1), item("leather_boots", 2),
                             item("torch", 3, 2, 6), potion("healing", 1), item("iron_ingot", 2, 1, 3),
                             sub("coins", 2)]),
                 pool(0, 1, [sub("armory_t1", 1), item("iron_chestplate", 1, fns=[ench(5, 15)]), empty(4)])],
    "manor": [pool(3, 6, [item("gold_ingot", 5, 1, 4), item("gold_nugget", 4, 3, 9), item("emerald", 4, 1, 4),
                          item("diamond", 1, 1, 2), item("golden_apple", 1), item("clock", 1), item("book", 2),
                          item("paper", 2, 1, 4), item("cake", 1), item("golden_carrot", 2, 1, 4),
                          item("name_tag", 1), sub("coins", 5)]),
              pool(1, 1, [sub("scroll_mid", 2), sub("ring", 1), sub("amulet", 1), ess("arcane", 2, 1, 3)])],
    "castle": [pool(4, 7, [item("gold_ingot", 6, 2, 6), item("gold_block", 1), item("emerald", 5, 2, 6),
                           item("diamond", 3, 1, 3), item("golden_apple", 2, 1, 2), item("enchanted_golden_apple", 1),
                           item("diamond_sword", 1, fns=[ench(20, 30)]), item("golden_helmet", 1, fns=[ench(10, 25)]),
                           item("experience_bottle", 3, 3, 8), sub("coins", 6)]),
               pool(1, 2, [sub("scroll_mid", 3), sub("scroll_high", 2)]),
               pool(1, 1, [sub("ring", 1), sub("amulet", 1), sub("armory_t1", 1), item(CRYSTAL, 2, 2, 4)])],
    "jeweler": [pool(3, 5, [item("emerald", 5, 1, 4), item("gold_nugget", 5, 3, 12), item("gold_ingot", 3, 1, 3),
                            item("amethyst_shard", 4, 2, 6), item("diamond", 1), item("lapis_lazuli", 3, 2, 8),
                            item("quartz", 3, 2, 6), item("prismarine_crystals", 1, 1, 3), sub("coins", 3)]),
                pool(0, 1, [sub("ring", 2), sub("amulet", 1), item(CRYSTAL, 2), empty(4)])],
    "house": [pool(3, 6, [item("bread", 5, 1, 4), item("apple", 3, 1, 3), item("cooked_mutton", 2, 1, 3),
                          item("wheat", 3, 2, 6), item("carrot", 2, 1, 3), item("potato", 2, 1, 3),
                          item("paper", 2, 1, 3), item("book", 1), item("torch", 3, 2, 6), item("emerald", 1, 1, 2),
                          item("iron_ingot", 1, 1, 2), sub("coins", 2), item("leather", 2, 1, 3),
                          item("string", 2, 1, 3), item("white_wool", 2, 1, 3), item("candle", 1, 1, 2), empty(2)]),
              pool(0, 1, [sub("scroll_low", 1), empty(5)])],
    "underworld": [pool(3, 6, [item("gold_nugget", 4, 3, 12), item("emerald", 4, 1, 4), item("diamond", 1),
                               item("ender_pearl", 2, 1, 2), item("gunpowder", 3, 1, 4), item("tnt", 1),
                               item("iron_sword", 2, fns=[ench(5, 20)]), item("crossbow", 1, fns=[ench(5, 15)]),
                               item("arrow", 2, 4, 10), potion("invisibility", 1), potion("poison", 1, "splash_potion"),
                               potion("weakness", 1, "splash_potion"), item("name_tag", 1), item("lead", 1),
                               sub("coins", 5)]),
                   pool(1, 1, [ess("shadow", 2, 1, 3), sub("scroll_shadow", 2), sub("ring", 1)])],
    "stable": [pool(2, 4, [item("saddle", 3), item("lead", 3, 1, 2), item("wheat", 4, 3, 8), item("hay_block", 2, 1, 2),
                           item("apple", 3, 1, 4), item("golden_carrot", 1, 1, 2), item("leather_horse_armor", 2),
                           item("iron_horse_armor", 1), item("golden_horse_armor", 1), item("carrot", 2, 1, 4)])],
    "tower": [pool(2, 4, [item("arrow", 5, 3, 10), item("bread", 4, 1, 3), item("torch", 4, 2, 6), item("bow", 1),
                          item("iron_nugget", 2, 2, 6), item("cooked_cod", 2, 1, 3), item("spyglass", 1),
                          item("leather_helmet", 1), sub("coins", 1)])],
    "garden": [pool(3, 6, [item("wheat_seeds", 5, 2, 8), item("carrot", 4, 1, 4), item("potato", 4, 1, 4),
                           item("beetroot_seeds", 3, 2, 6), item("bone_meal", 4, 2, 6), item("wheat", 3, 2, 6),
                           item("pumpkin_seeds", 2, 1, 3), item("melon_seeds", 2, 1, 3), item("iron_hoe", 1),
                           item("sweet_berries", 2, 2, 5), item("oak_sapling", 1, 1, 2)])],
}

# Sub-tabelas: item de outro módulo (só carrega se o item existir) ou sorteio de pergaminho de glifo (função do
# structures_c); se faltar, só a sub-tabela fica vazia — o baú continua funcionando.
SUBTABLES = {
    "coins": {"cond": "guild_coin", "pools": [pool(1, 1, [item(f"{NS}:guild_coin", 1, 1, 6)])]},
    "ring": {"cond": "mana_ring", "pools": [pool(1, 1, [item(f"{NS}:mana_ring", 1)])]},
    "amulet": {"cond": "arcane_amulet", "pools": [pool(1, 1, [item(f"{NS}:arcane_amulet", 1)])]},
    "armory_t1": {"cond": "mage_staff_t1", "pools": [pool(1, 1, [
        item(f"{NS}:mage_staff_t1", 1), item(f"{NS}:witch_wand_t1", 1), item(f"{NS}:pact_blade_t1", 1),
        item(f"{NS}:druid_staff_t1", 1), item(f"{NS}:reaper_scythe_t1", 1)])]},
    "focus": {"cond": "focus_fire", "pools": [pool(1, 1, [item(f"{NS}:focus_{e}", 1) for e in
                                                          ("fire", "water", "earth", "wind", "light", "shadow")])]},
    "scroll_low": {"cond": "glyph_scroll", "pools": [pool(1, 1, [
        {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": 1,
         "functions": [{"function": f"{NS}:rpg_glyph_scroll", "elements": ALL_BASE, "min_tier": 1, "max_tier": 2}]}])]},
    "scroll_mid": {"cond": "glyph_scroll", "pools": [pool(1, 1, [
        {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": 1,
         "functions": [{"function": f"{NS}:rpg_glyph_scroll", "elements": ALL_BASE, "min_tier": 2, "max_tier": 3}]}])]},
    "scroll_high": {"cond": "glyph_scroll", "pools": [pool(1, 1, [
        {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": 1,
         "functions": [{"function": f"{NS}:rpg_glyph_scroll", "elements": ALL_BASE, "min_tier": 3, "max_tier": 4}]}])]},
    "scroll_light": {"cond": "glyph_scroll", "pools": [pool(1, 1, [
        {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": 1,
         "functions": [{"function": f"{NS}:rpg_glyph_scroll", "elements": ["light"], "min_tier": 1, "max_tier": 3}]}])]},
    "scroll_shadow": {"cond": "glyph_scroll", "pools": [pool(1, 1, [
        {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": 1,
         "functions": [{"function": f"{NS}:rpg_glyph_scroll", "elements": ["shadow"], "min_tier": 1, "max_tier": 3}]}])]},
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def gen_loot():
    for name, pools in CHESTS.items():
        write_json(os.path.join(LOOT_OUT, f"{name}.json"), {
            "type": "minecraft:chest", "pools": pools, "random_sequence": f"{NS}:chests/city/{name}"})
    for name, spec in SUBTABLES.items():
        write_json(os.path.join(LOOT_OUT, "sub", f"{name}.json"), {
            "neoforge:conditions": [{"type": "neoforge:item_exists", "item": f"{NS}:{spec['cond']}"}],
            "type": "minecraft:chest", "pools": spec["pools"]})


# =================================================================================================
#  WORLDGEN, TAGS
# =================================================================================================
CITY_BIOMES = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna", "minecraft:savanna_plateau",
               "minecraft:forest", "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:taiga",
               "minecraft:snowy_taiga", "minecraft:desert", "minecraft:snowy_plains",
               # v0.9: mais biomas (paletas: prado/cerejeiras/bétulas/savana/selva esparsa → planície; badlands →
               # deserto — ver CityPalette.forBiome)
               "minecraft:meadow", "minecraft:cherry_grove", "minecraft:old_growth_birch_forest",
               "minecraft:windswept_savanna", "minecraft:sparse_jungle", "minecraft:wooded_badlands"]
CAPITAL_BIOMES = CITY_BIOMES + ["minecraft:dark_forest", "minecraft:old_growth_pine_taiga",
                                "minecraft:old_growth_spruce_taiga", "minecraft:swamp", "minecraft:jungle",
                                "minecraft:badlands", "minecraft:windswept_forest"]
# As capitais de Sahra e Nordheim aceitam qualquer bioma de terra (o reino SEMPRE existe); o anel puxa cada uma para o
# bioma do reino (preferred_biomes) e a paleta é a do reino em qualquer bioma ("kingdom" na estrutura).
KINGDOM_CAPITAL_BIOMES = CAPITAL_BIOMES + ["minecraft:eroded_badlands", "minecraft:ice_spikes",
                                           "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                                           "minecraft:mangrove_swamp", "minecraft:bamboo_jungle"]
CAPITAL_PREFERRED = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:birch_forest",
                     "minecraft:flower_forest"]
SAHRA_PREFERRED = ["minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands"]
NORDHEIM_PREFERRED = ["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes"]

# Salts dos conjuntos (os de cidade/Aldória são os da v0.8: mudar o salt muda o mundo inteiro).
SALT_CITY, SALT_ALDORIA, SALT_SAHRA, SALT_NORDHEIM = 1946300801, 1946300802, 1946300803, 1946300804
# Zona das cidades comuns em volta das capitais (chunks): nenhuma cidade comum encosta numa capital.
CITY_VS_CAPITAL = 16


def gen_worldgen():
    # "spawn_overrides" vazio (o campo é OBRIGATÓRIO no codec do 1.21.1): quem impede monstros de nascer na cidade é a
    # zona segura do Places (núcleo A1), que respeita a config CITY_PROTECTION (§2.5).
    # "step": strongholds = depois das vilas (surface_structures): a cidade apaga a vila que cair no lugar dela.
    common = {"type": f"{NS}:guild_city", "step": "strongholds", "terrain_adaptation": "beard_box", "spawn_overrides": {}}
    capital = {**common, "capital": True, "size": 3, "max_slope": 64, "max_water": 0.5}
    # v0.9: mais cidades — grade 48/28 (era 64/24) e terreno mais tolerante (declive 36, água 0,3; bioma ruim 0,55 no
    # Java, sem contar rio/praia).
    write_json(os.path.join(D, "worldgen/structure/guild_city.json"), {
        **common, "biomes": f"#{NS}:has_structure/guild_city", "capital": False, "size": 2, "max_slope": 36,
        "max_water": 0.3})
    write_json(os.path.join(D, "worldgen/structure/capital_city.json"), {
        **capital, "biomes": f"#{NS}:has_structure/capital_city", "kingdom": "aldoria"})
    write_json(os.path.join(D, "worldgen/structure/capital_sahra.json"), {
        **capital, "biomes": f"#{NS}:has_structure/capital_sahra", "kingdom": "sahra"})
    write_json(os.path.join(D, "worldgen/structure/capital_nordheim.json"), {
        **capital, "biomes": f"#{NS}:has_structure/capital_nordheim", "kingdom": "nordheim"})
    # Cidades comuns: random_spread_multi longe das 3 capitais (exclusões de mão única: as capitais não olham para
    # ninguém, a cidade olha para as capitais, as outras estruturas do mod olham para as duas).
    write_json(os.path.join(D, "worldgen/structure_set/guild_city.json"), {
        "structures": [{"structure": f"{NS}:guild_city", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": 48, "separation": 28, "salt": SALT_CITY,
                      "exclusion_zones": [{"other_set": f"{NS}:{c}", "chunk_count": CITY_VS_CAPITAL}
                                          for c in ("capital_city", "capital_sahra", "capital_nordheim")]}})
    # Aldória: perto do spawn (~260–620 blocos), como na v0.8 (mundos salvos continuam com ela no mesmo lugar).
    write_json(os.path.join(D, "worldgen/structure_set/capital_city.json"), {
        "structures": [{"structure": f"{NS}:capital_city", "weight": 1}],
        "placement": {"type": "minecraft:concentric_rings", "distance": 6, "spread": 1, "count": 1,
                      "salt": SALT_ALDORIA, "preferred_biomes": CAPITAL_PREFERRED}})
    # Sahra e Nordheim: kingdom_ring (concentric_rings com 6 candidatos a cada 60°; usa o 1º candidato da lista que
    # achou o bioma do reino). Aldória fica no candidato 0; Sahra ~120° (2, 1, 3) e Nordheim ~240° (4, 5, 0): cada
    # reino numa direção. Distância: 4·d ± 1,25·d chunks → Sahra ~1300–2500 blocos, Nordheim ~1600–3000.
    write_json(os.path.join(D, "worldgen/structure_set/capital_sahra.json"), {
        "structures": [{"structure": f"{NS}:capital_sahra", "weight": 1}],
        "placement": {"type": f"{NS}:kingdom_ring", "distance": 30, "spread": 6, "count": 6, "salt": SALT_SAHRA,
                      "preferred_biomes": SAHRA_PREFERRED, "candidates": [2, 1, 3]}})
    write_json(os.path.join(D, "worldgen/structure_set/capital_nordheim.json"), {
        "structures": [{"structure": f"{NS}:capital_nordheim", "weight": 1}],
        "placement": {"type": f"{NS}:kingdom_ring", "distance": 36, "spread": 6, "count": 6, "salt": SALT_NORDHEIM,
                      "preferred_biomes": NORDHEIM_PREFERRED, "candidates": [4, 5, 0]}})
    write_json(os.path.join(D, "tags/worldgen/biome/has_structure/guild_city.json"), {"values": CITY_BIOMES})
    write_json(os.path.join(D, "tags/worldgen/biome/has_structure/capital_city.json"), {"values": CAPITAL_BIOMES})
    write_json(os.path.join(D, "tags/worldgen/biome/has_structure/capital_sahra.json"),
               {"values": KINGDOM_CAPITAL_BIOMES})
    write_json(os.path.join(D, "tags/worldgen/biome/has_structure/capital_nordheim.json"),
               {"values": KINGDOM_CAPITAL_BIOMES})
    write_json(os.path.join(D, "tags/worldgen/structure/city.json"),
               {"values": [f"{NS}:guild_city", f"{NS}:capital_city", f"{NS}:capital_sahra",
                           f"{NS}:capital_nordheim"]})


# =================================================================================================
#  TRADUÇÕES DAS PLACAS (src/main/lang/cities_world)
# =================================================================================================
SIGNS = {
    "guild_hall.1": ("Guilda dos", "Adventurers'"), "guild_hall.2": ("Aventureiros", "Guild"),
    "guild_hall.3": ("~ Missões ~", "~ Quests ~"), "guild_hall.4": ("Bem-vindos!", "Welcome!"),
    "tavern.1": ("Taverna do", "The Drunken"), "tavern.2": ("Grifo Bêbado", "Griffin"),
    "tavern.3": ("Comida • Bebida", "Food • Drink"), "tavern.4": ("Quartos", "Rooms"),
    "market": ("Mercado", "Market"),
    "smithy": ("Ferraria", "Smithy"),
    "alchemy": ("Alquimia", "Alchemist"),
    "jeweler": ("Joalheria", "Jeweler"),
    "tailor": ("Alfaiataria", "Tailor"),
    "bakery.1": ("Padaria", "Bakery"), "bakery.2": ("Pão Quente", "Fresh Bread"),
    "stable": ("Estábulo", "Stables"),
    "academy.1": ("Academia", "Arcane"), "academy.2": ("Arcana", "Academy"),
    "church.1": ("Igreja da", "Church of"), "church.2": ("Aurora", "the Dawn"), "church.3": ("✦", "✦"),
    "barracks.1": ("Quartel da", "Guard"), "barracks.2": ("Guarda", "Barracks"),
    "manor.1": ("Mansão", "Lord's"), "manor.2": ("do Lorde", "Manor"),
    "castle.1": ("Castelo", "Royal"), "castle.2": ("Real", "Castle"),
    "plaza.1": ("Praça", "Central"), "plaza.2": ("Central", "Square"),
    "plaza_capital.1": ("Praça", "Royal"), "plaza_capital.2": ("Real", "Square"),
    "gate.1": ("Bem-vindo,", "Welcome,"), "gate.2": ("viajante!", "traveler!"),
    "alley.1": ("Silêncio.", "Quiet."), "alley.2": ("Só convidados", "Invite only"),
    "altar.1": ("Altar de", "Ascension"), "altar.2": ("Ascensão", "Altar"), "altar.3": ("~ público ~", "~ public ~"),
    "travel.1": ("Pedra de", "Travel"), "travel.2": ("Teleporte", "Stone"),
}

# Textos do núcleo de worldgen (core/worldgen: /mahou worldgen kingdoms) — as traduções do A3 ficam todas aqui.
WORLDGEN_LANG = {
    "worldgen.isekai_mahou.kingdom.aldoria": ("Reino de Aldória", "Kingdom of Aldoria"),
    "worldgen.isekai_mahou.kingdom.sahra": ("Império de Sahra", "Sahra Empire"),
    "worldgen.isekai_mahou.kingdom.nordheim": ("Reino de Nordheim", "Kingdom of Nordheim"),
    "command.isekai_mahou.worldgen.kingdoms.header": (
        "Capitais dos reinos neste mundo (ponto previsto — confirme com /locate structure):",
        "Kingdom capitals in this world (predicted spot — confirm with /locate structure):"),
    "command.isekai_mahou.worldgen.kingdoms.entry": ("%s: %s — a %s blocos", "%s: %s — %s blocks away"),
    "command.isekai_mahou.worldgen.kingdoms.missing": ("%s: não existe neste mundo", "%s: not in this world"),
    "command.isekai_mahou.worldgen.kingdoms.wrong_dim": ("Os reinos ficam no Mundo Normal (Overworld).",
                                                          "The kingdoms are in the Overworld."),
}


def gen_lang():
    for idx, lang in ((0, "pt_br"), (1, "en_us")):
        out = {f"sign.{NS}.city.{k}": v[idx] for k, v in sorted(SIGNS.items())}
        out.update({k: v[idx] for k, v in sorted(WORLDGEN_LANG.items())})
        write_json(os.path.join(LANG_DIR, f"{lang}.json"), out)


# =================================================================================================
#  LEITURA DE VOLTA DOS .nbt E CONFERÊNCIA
# =================================================================================================
def read_nbt(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    pos = [0]

    def rd(fmt):
        v = struct.unpack_from(fmt, data, pos[0])
        pos[0] += struct.calcsize(fmt)
        return v[0]

    def rstr():
        n = rd(">H")
        s = data[pos[0]:pos[0] + n].decode("utf-8")
        pos[0] += n
        return s

    def payload(t):
        if t == 1:
            return rd(">b")
        if t == 2:
            return rd(">h")
        if t == 3:
            return rd(">i")
        if t == 4:
            return rd(">q")
        if t == 5:
            return rd(">f")
        if t == 6:
            return rd(">d")
        if t == 7:
            n = rd(">i")
            v = data[pos[0]:pos[0] + n]
            pos[0] += n
            return v
        if t == 8:
            return rstr()
        if t == 9:
            et = rd(">b")
            n = rd(">i")
            return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = rd(">b")
                if tt == 0:
                    return out
                k = rstr()
                out[k] = payload(tt)
        if t == 11:
            n = rd(">i")
            return [rd(">i") for _ in range(n)]
        if t == 12:
            n = rd(">i")
            return [rd(">q") for _ in range(n)]
        raise ValueError(f"tag desconhecida {t}")

    t = rd(">b")
    rstr()
    return payload(t)


RESOURCES_JAR = os.path.join(ROOT, "build/moddev/artifacts/neoforge-21.1.265-client-extra-aka-minecraft-resources.jar")
H4 = ["north", "south", "east", "west"]
# Blocos desenhados por BlockEntity (o blockstate .json não lista as propriedades): valores aceitos.
BE_PROPS = {
    "_wall_sign": {"facing": H4}, "_sign": {"rotation": [str(i) for i in range(16)]},
    "_wall_hanging_sign": {"facing": H4},
    "_hanging_sign": {"rotation": [str(i) for i in range(16)], "attached": ["true", "false"]},
    "_wall_banner": {"facing": H4}, "_banner": {"rotation": [str(i) for i in range(16)]},
    "chest": {"facing": H4, "type": ["single", "left", "right"]},
    "_bed": {"facing": H4, "part": ["head", "foot"], "occupied": ["true", "false"]},
    "_wall_skull": {"facing": H4}, "_wall_head": {"facing": H4},
}


def blockstate_props():
    """Propriedades/valores de cada bloco do vanilla tiradas dos blockstates .json (variants e multipart)."""
    import zipfile
    out = {}
    if not os.path.exists(RESOURCES_JAR):
        return out
    with zipfile.ZipFile(RESOURCES_JAR) as z:
        for n in z.namelist():
            if not (n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")):
                continue
            block = "minecraft:" + n.rsplit("/", 1)[1][:-5]
            js = json.loads(z.read(n))
            props = {}

            def add(k, v):
                props.setdefault(k, set()).update(str(v).split("|"))
            for key in js.get("variants", {}):
                for kv in key.split(","):
                    if "=" in kv:
                        add(*kv.split("=", 1))
            for part in js.get("multipart", []):
                when = part.get("when", {})
                for cond in when.get("OR", when.get("AND", [when])):
                    for k, v in cond.items():
                        add(k, v)
            # O multipart só lista os valores "ligados": completa booleanos e lados de muro.
            for k, vals in props.items():
                if vals & {"true", "false"}:
                    vals |= {"true", "false"}
                if vals & {"low", "tall"}:
                    vals |= {"none", "low", "tall"}
            out[block] = props
    return out


def check_props(name, props, known):
    """Lista de problemas de uma entrada de paleta (nome de propriedade/valor que o bloco não aceita)."""
    probs = []
    table = dict(known.get(name, {}))
    for suffix, extra in BE_PROPS.items():
        short = name.split(":", 1)[1]
        if short == suffix or (suffix.startswith("_") and short.endswith(suffix)):
            table.update({k: set(v) for k, v in extra.items()})
    for k, v in props.items():
        if k in table and v not in table[k]:
            probs.append(f"{name}: valor inválido {k}={v} (aceita {sorted(table[k])})")
    return probs


def vanilla_items(blocks):
    """Ids de itens do vanilla (registerItem("...") + itens de bloco) para conferir o loot."""
    try:
        src = open(os.path.join(MCSRC, "item/Items.java"), encoding="utf-8").read()
        refs = open(os.path.join(MCSRC, "..", "references", "Items.java"), encoding="utf-8").read()
    except OSError:
        return set()
    return ({"minecraft:" + m for m in re.findall(r'registerItem\(\s*"([a-z0-9_]+)"', src)}
            | {"minecraft:" + m for m in re.findall(r'createKey\("([a-z0-9_]+)"\)', refs)} | blocks)


def loot_items(node, out):
    """Todos os nomes de item citados numa loot table (recursivo)."""
    if isinstance(node, dict):
        if node.get("type") == "minecraft:item":
            out.add(node["name"])
        for v in node.values():
            loot_items(v, out)
    elif isinstance(node, list):
        for v in node:
            loot_items(v, out)
    return out


def vanilla_ids():
    blocks, entities = set(), set()
    try:
        src = open(os.path.join(MCSRC, "level/block/Blocks.java"), encoding="utf-8").read()
        blocks = {"minecraft:" + m for m in re.findall(r'register\(\s*"([a-z0-9_]+)"', src)}
        # Alguns blocos são registrados por chave (net.minecraft.references.Blocks.createKey("..."))
        refs = os.path.join(MCSRC, "..", "references", "Blocks.java")
        if os.path.exists(refs):
            blocks |= {"minecraft:" + m for m in re.findall(r'createKey\("([a-z0-9_]+)"\)',
                                                            open(refs, encoding="utf-8").read())}
        src = open(os.path.join(MCSRC, "entity/EntityType.java"), encoding="utf-8").read()
        entities = {"minecraft:" + m for m in re.findall(r'register\(\s*"([a-z0-9_]+)"', src)}
    except OSError:
        pass
    return blocks, entities


def check_template_v09(name, pal, root, names):
    """v0.9: Altar de Ascensão completo e público na Igreja/Academia; reino certo no city_heart; Pedra de Teleporte
    com 2 blocos livres em cima."""
    errs = []
    at = {}
    nbt_at = {}
    for b in root["blocks"]:
        if b["state"] < len(names):
            at[tuple(b["pos"])] = base_name(names[b["state"]])
            if b.get("nbt"):
                nbt_at[tuple(b["pos"])] = b["nbt"]
    cores = [p for p, n in at.items() if n == ASCENSION_CORE]
    if name in ALTAR_TEMPLATES and len(cores) != 1:
        errs.append(f"esperava 1 Altar de Ascensão, achei {len(cores)}")
    for (x, y, z) in cores:
        nb = nbt_at.get((x, y, z), {})
        if nb.get("PublicAltar") != 1 or nb.get("NeoForgeData", {}).get("PublicAltar") != 1:
            errs.append(f"núcleo do altar em {(x, y, z)} sem PublicAltar:1b")
        for parts, want in ((ALTAR_PEDESTALS, ASCENSION_PEDESTAL), (ALTAR_RUNES, RUNE_STONE)):
            for dx, dz in parts:
                got = at.get((x + dx, y, z + dz))
                if got != want:
                    errs.append(f"altar: ({dx},{dz}) deveria ser {want}, é {got}")
        # Nada sólido logo acima das peças (o jogador precisa pôr itens nos pedestais e ver o círculo).
        for dx, dz in ALTAR_PEDESTALS + [(0, 0)]:
            above = at.get((x + dx, y + 1, z + dz))
            if above not in (None, "minecraft:air"):
                errs.append(f"altar: bloco {above} em cima de ({dx},{dz})")
    for p, n in at.items():
        if n == f"{NS}:city_heart":
            nb = nbt_at.get(p, {})
            if nb.get("Kingdom") != pal.kingdom or nb.get("NeoForgeData", {}).get("Kingdom") != pal.kingdom:
                errs.append(f"city_heart sem Kingdom={pal.kingdom}")
        if n == TRAVEL_STONE:
            x, y, z = p
            for dy in (1, 2):
                if at.get((x, y + dy, z)) not in (None, "minecraft:air"):
                    errs.append(f"Pedra de Teleporte sem espaço livre em cima ({at.get((x, y + dy, z))})")
    return errs


def check_all():
    vb, ve = vanilla_ids()
    known_props = blockstate_props()
    seen_states = set()
    lang = json.load(open(os.path.join(LANG_DIR, "pt_br.json"), encoding="utf-8"))
    lang_en = json.load(open(os.path.join(LANG_DIR, "en_us.json"), encoding="utf-8"))
    errors, total_blocks, total_entities = [], 0, 0
    for pal in PALETTES:
        for name, (ex, ez) in EXPECTED_SIZE.items():
            path = os.path.join(STRUCT_OUT, pal.name, name + ".nbt")
            tag = f"{pal.name}/{name}"
            if not os.path.exists(path):
                errors.append(f"{tag}: .nbt não existe")
                continue
            root = read_nbt(path)
            sx, sy, sz = root["size"]
            if (sx, sz) != (ex, ez):
                errors.append(f"{tag}: tamanho {sx}x{sz}, esperado {ex}x{ez}")
            if sy > 64:
                errors.append(f"{tag}: alto demais ({sy})")
            palette = root["palette"]
            names = []
            for p in palette:
                n = p["Name"]
                props = p.get("Properties", {})
                names.append(n + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else ""))
                if n.startswith("minecraft:") and vb and n not in vb:
                    errors.append(f"{tag}: bloco inexistente {n}")
                if n.startswith("minecraft:") and props and names[-1] not in seen_states:
                    seen_states.add(names[-1])
                    errors.extend(f"{tag}: {p}" for p in check_props(n, props, known_props))
                if n.startswith(NS + ":") and n not in MOD_BLOCKS:
                    errors.append(f"{tag}: bloco do mod fora do catálogo {n}")
            ground = set()
            present_blocks = set()
            for b in root["blocks"]:
                x, y, z = b["pos"]
                if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
                    errors.append(f"{tag}: bloco fora do volume {b['pos']}")
                st_i = b["state"]
                if st_i >= len(palette):
                    errors.append(f"{tag}: índice de paleta inválido")
                    continue
                full = names[st_i]
                present_blocks.add(full)
                present_blocks.add(palette[st_i]["Name"])
                if y == GROUND_OFFSET.get(name, 0):
                    ground.add((x, z))          # ar explícito no chão também é de propósito (ex.: vão da escada)
                nbt = b.get("nbt")
                if nbt and "LootTable" in nbt:
                    lt = nbt["LootTable"]
                    lp = os.path.join(D, "loot_table", lt.split(":", 1)[1] + ".json")
                    if not os.path.exists(lp):
                        errors.append(f"{tag}: loot table inexistente {lt}")
                    if nbt.get("CityChest") != 1 or nbt.get("NeoForgeData", {}).get("CityChest") != 1:
                        errors.append(f"{tag}: baú sem CityChest em {b['pos']}")
                if nbt and "front_text" in nbt:
                    for m in nbt["front_text"]["messages"] + nbt["back_text"]["messages"]:
                        j = json.loads(m)
                        if isinstance(j, dict) and "translate" in j:
                            if j["translate"] not in lang or j["translate"] not in lang_en:
                                errors.append(f"{tag}: placa sem tradução {j['translate']}")
            total_blocks += len(root["blocks"])
            errors.extend(f"{tag}: {e}" for e in check_template_v09(name, pal, root, names))
            if name in LOTS:
                missing = [(x, z) for x in range(sx) for z in range(sz) if (x, z) not in ground]
                if missing:
                    errors.append(f"{tag}: {len(missing)} colunas sem chão (ex.: {missing[:4]})")
            ents = []
            for e in root["entities"]:
                nb = e["nbt"]
                eid = nb["id"]
                ents.append(eid)
                if "Profession" in nb:
                    ents.append(f"{eid}:{nb['Profession']}")
                    if nb["Profession"] not in PROFESSIONS:
                        errors.append(f"{tag}: profissão desconhecida {nb['Profession']}")
                if eid.startswith("minecraft:") and ve and eid not in ve:
                    errors.append(f"{tag}: entidade inexistente {eid}")
                if eid.startswith(NS + ":") and eid not in MOD_ENTITIES:
                    errors.append(f"{tag}: entidade do mod fora do catálogo {eid}")
                if eid != "minecraft:armor_stand" and nb.get("PersistenceRequired") != 1:
                    errors.append(f"{tag}: {eid} sem PersistenceRequired")
                ex_, ey_, ez_ = e["pos"]
                if not (0 <= ex_ < sx and 0 <= ey_ < sy and 0 <= ez_ < sz):
                    errors.append(f"{tag}: entidade fora do volume {eid} {e['pos']}")
            total_entities += len(ents)
            req = REQUIRED.get(name, {})
            for rb in req.get("blocks", []):
                if "[" in rb:
                    bn, prop = rb[:-1].split("[")
                    ok = any(n.startswith(bn + "[") and prop in n for n in present_blocks)
                else:
                    ok = rb in present_blocks
                if not ok:
                    errors.append(f"{tag}: falta o bloco {rb}")
            for re_ in req.get("entities", []):
                if re_ not in ents:
                    errors.append(f"{tag}: falta a entidade {re_}")
    for name in CHESTS:
        if not os.path.exists(os.path.join(LOOT_OUT, f"{name}.json")):
            errors.append(f"loot chests/city/{name} não gerado")
    vi = vanilla_items(vb)
    for tables in (CHESTS.values(), [s["pools"] for s in SUBTABLES.values()]):
        for pools in tables:
            for it in sorted(loot_items(pools, set())):
                if it.startswith("minecraft:") and vi and it not in vi:
                    errors.append(f"loot: item inexistente {it}")
    for pools in list(CHESTS.values()) + [s["pools"] for s in SUBTABLES.values()]:
        for p in pools:
            for e in p["entries"]:
                if e["type"] == "minecraft:loot_table":
                    sp = os.path.join(D, "loot_table", e["value"].split(":", 1)[1] + ".json")
                    if not os.path.exists(sp):
                        errors.append(f"sub-tabela inexistente {e['value']}")
    return errors, total_blocks, total_entities


# =================================================================================================
#  PRÉVIA ASCII
# =================================================================================================
def char_of(state):
    if state is None:
        return " "
    if state == AIR:
        return "."
    n = base_name(state).split(":", 1)[1]
    table_ = [("heart", "H"), ("counter", "K"), ("quest", "Q"), ("board", "F"), ("chest", "C"), ("barrel", "b"),
              ("bed", "B"), ("door", "D"), ("ladder", "#"), ("stairs", "/"), ("slab", "_"), ("fence", "+"),
              ("wall", "w"), ("pane", "|"), ("lantern", "*"), ("torch", "*"), ("campfire", "F"), ("water", "~"),
              ("carpet", "-"), ("log", "O"), ("planks", "="), ("leaves", "%"), ("bookshelf", "L"), ("sign", "s"),
              ("banner", "^"), ("grass", "\""), ("path", ":")]
    for k, ch in table_:
        if k in n:
            return ch
    return "X"


def preview(s, layers):
    sx, sy, sz = s.size
    print(f"\n=== {s.P.name}/{s.name} {s.size} ===")
    for y in layers:
        if y >= sy:
            continue
        print(f"-- y={y}")
        for z in range(sz):
            print("".join(char_of(s.get(x, y, z)) for x in range(sx)))


def main():
    check_only = "--check-only" in sys.argv
    show = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    if not check_only:
        for P in PALETTES:
            for fn, _ in BUILDERS:
                fn(P)
        gen_loot()
        gen_worldgen()
        gen_lang()
        for pal, name, s, n in BUILT:
            if pal == "plains":
                print(f"{name:14s} {str(s.size):14s} {n:6d} blocos  {len(s.entities):3d} entidades")
            if show and name == show:
                preview(s, sorted({0, 1, 2, 5, 7, 8, 13}))
        print(f"{len(BUILT)} templates gravados em {STRUCT_OUT}")
    errors, nb, ne = check_all()
    print(f"Leitura de volta: {len(PALETTES) * len(EXPECTED_SIZE)} templates, {nb} blocos, {ne} entidades/marcas")
    for e in errors:
        print("ERRO:", e)
    if errors:
        sys.exit(1)
    print("OK — todos os templates conferidos")


if __name__ == "__main__":
    main()
