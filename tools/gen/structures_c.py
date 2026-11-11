"""Gera os dados do modulo "structures_c" (estruturas de RPG do Overworld).

Uso (na raiz do projeto):  py -I tools/gen/structures_c.py [--preview]

Gera:
  - estruturas .nbt em data/isekai_mahou/structure/<estrutura>/... (pecas de jigsaw e variantes)
  - worldgen: structure, structure_set, template_pool, processor_list
  - loot tables dos baus: chests/<estrutura>[_sufixo] + sub-tabelas condicionais structures_c/* (itens de outros
    modulos ficam em sub-tabelas com "neoforge:conditions", entao a falta de um item nunca quebra o bau inteiro)
  - tags no merge: has_structure/<id> (biomas), tags de estrutura (mapas de explorador e rpg_structures)
Com --preview imprime cortes ASCII de cada estrutura (para conferir o desenho).

Convencoes dos templates: x = leste, z = sul, y = cima. "facing" das escadas = lado alto; camas = do pe para a
cabeceira; tochas/placas/estandartes de parede = direcao para onde apontam (o apoio fica do lado oposto).
Pecas com piso de terreno: o piso fica em y=0 (o jigsaw poe y=1 na primeira camada de ar acima do chao).
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte, Float, Int  # noqa: E402

D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/structures_c")
NS = "isekai_mahou"
STRUCT_OUT = os.path.join(D, "structure")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =================================================================================================
#  ESTADOS DE BLOCO
# =================================================================================================
DIRV = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
# yaw do Minecraft: 0 = olhando para o sul, 90 = oeste, 180 = norte, -90 = leste
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


def fence_gate(block, facing):
    return st(block, facing=facing, in_wall="false", open="false", powered="false")


def candle(n=1, color=None, lit=True):
    return st(f"{color}_candle" if color else "candle", candles=n, lit="true" if lit else "false",
              waterlogged="false")


def skull(kind="skeleton", rotation=0):
    name = "skeleton_skull" if kind == "skeleton" else f"{kind}_head" if kind in ("zombie", "creeper", "dragon", "piglin") \
        else f"{kind}_skull"
    return st(name, rotation=rotation)


def wall_skull(kind, facing):
    name = "skeleton_wall_skull" if kind == "skeleton" else f"{kind}_wall_skull"
    return st(name, facing=facing)


def egg(n):
    return st("turtle_egg", eggs=n, hatch=0)


def water():
    return st("water", level=0)


def pane(color=None):
    return mc(f"{color}_stained_glass_pane" if color else "glass_pane")


# =================================================================================================
#  CONSTRUTOR DE TEMPLATES
# =================================================================================================
class B(Structure):
    """Structure com atalhos para portas, camas, baus, mobs, placas, estandartes e jigsaws."""

    def __init__(self, name, sx, sy, sz):
        super().__init__(sx, sy, sz)
        self.name = name
        self.rnd = random.Random("structures_c/" + name)

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def empty(self, x, y, z):
        v = self.get(x, y, z)
        return v is None or v == AIR

    def soft(self, x, y, z, state, nbt=None):
        """Coloca só onde está vazio (nada definido ou ar)."""
        if self.empty(x, y, z):
            self.set(x, y, z, state, nbt)

    def air(self, x0, y0, z0, x1, y1, z1):
        """Ar explícito (limpa o terreno) só nas posições ainda não definidas."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if self.get(x, y, z) is None:
                        self.set(x, y, z, AIR)

    def pick(self, weighted):
        """weighted: [(estado, peso), ...]"""
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return s
        return weighted[-1][0]

    def door(self, x, y, z, wood, facing, hinge="left"):
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, st(f"{wood}_door", facing=facing, half=half, hinge=hinge, open="false",
                                       powered="false"))

    def bed(self, x, y, z, color, facing):
        dx, dz = DIRV[facing]
        self.set(x, y, z, st(f"{color}_bed", facing=facing, part="foot", occupied="false"))
        self.set(x + dx, y, z + dz, st(f"{color}_bed", facing=facing, part="head", occupied="false"))

    def chest(self, x, y, z, loot, facing="north"):
        self.set(x, y, z, st("chest", facing=facing, type="single", waterlogged="false"), nbt={"LootTable": loot})

    def barrel(self, x, y, z, loot=None, facing="up"):
        self.set(x, y, z, st("barrel", facing=facing, open="false"), nbt={"LootTable": loot} if loot else None)

    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        nbt = {"PersistenceRequired": Byte(1), "Rotation": [Float(yaw), Float(0.0)]}
        if extra:
            nbt.update(extra)
        self.entity(x, y, z, mc(eid), nbt)

    def villager(self, x, y, z, profession, vtype="plains", yaw=0.0):
        self.mob(x, y, z, "minecraft:villager", yaw, {
            "VillagerData": {"profession": mc(profession), "level": Int(1), "type": mc(vtype)}})

    def armor_stand(self, x, y, z, yaw, head=None, chest=None, legs=None, feet=None, hand=None):
        def it(i):
            return {"id": mc(i), "count": Int(1)} if i else {}
        self.entity(x, y, z, "minecraft:armor_stand", {
            "Rotation": [Float(yaw), Float(0.0)], "ShowArms": Byte(1),
            "ArmorItems": [it(feet), it(legs), it(chest), it(head)], "HandItems": [it(hand), {}]})

    def sign(self, x, y, z, state, keys, color="black", glow=False):
        msgs = [json.dumps({"translate": k}) if k else '""' for k in keys] + ['""'] * (4 - len(keys))
        self.set(x, y, z, state, nbt={
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": Byte(1 if glow else 0)},
            "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)},
            "is_waxed": Byte(1)})

    def banner(self, x, y, z, state, patterns):
        self.set(x, y, z, state, nbt={"patterns": [{"pattern": mc(p), "color": c} for p, c in patterns]})

    def jig(self, x, y, z, orientation, name, target="minecraft:empty", pool="minecraft:empty",
            final="minecraft:air", joint="rollable", priority=0):
        self.set(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", nbt={
            "name": name, "target": target, "pool": pool, "final_state": final, "joint": joint,
            "placement_priority": Int(priority), "selection_priority": Int(0)})


# ---------------------------------------------------------------- conexões de cercas/painéis/muros
NON_FULL = ("slab", "stairs", "fence", "pane", "bars", "lantern", "torch", "carpet", "button", "plate", "chain",
            "candle", "rod", "cluster", "bud", "roots", "vine", "sign", "banner", "pot", "chest", "campfire",
            "bell", "water", "lava", "air", "door", "ladder", "bed", "skull", "head", "rail", "snow", "egg",
            "cobweb", "leaves", "cauldron", "anvil", "lectern", "brewing", "grindstone", "composter", "dripstone",
            "flower", "poppy", "dandelion", "tulip", "allium", "orchid", "daisy", "cornflower", "lily", "bush",
            "grass", "fern", "sapling", "mushroom", "jigsaw", "spawner", "wall", "propagule",
            "torchflower", "sugar_cane", "kelp", "seagrass", "pickle", "end_rod", "glass")


def base_name(state):
    return state.split("[", 1)[0] if state else ""


def is_full(state):
    if not state:
        return False
    n = base_name(state).split(":", 1)[1]
    if n in ("mud_bricks", "red_mushroom_block", "brown_mushroom_block", "mushroom_stem"):
        return True
    if n.endswith("_wall") or n.endswith("_glass") or n == "glass":
        return n.endswith("_glass") or n == "glass"
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


def fix_connections(s):
    """Peças de jigsaw não recalculam formas ao serem colocadas: liga cercas, painéis, grades e muros aqui."""
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


def _gate_axis_ok(gate_state, d):
    """Um portão liga numa cerca/muro vizinho se o eixo dele for perpendicular à direção da ligação."""
    f = "north"
    if "facing=" in gate_state:
        f = gate_state.split("facing=", 1)[1].split(",")[0].split("]")[0]
    gate_axis_x = f in ("east", "west")
    dir_axis_x = d in ("east", "west")
    return gate_axis_x != dir_axis_x


def save(s, rel, keep_air=True):
    fix_connections(s)
    n = s.save(os.path.join(STRUCT_OUT, rel + ".nbt"), keep_air=keep_air)
    BUILT.append((rel, s, n))
    return n


BUILT = []


def disk(cx, cz, r):
    for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
        for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                yield x, z, d


def loot(name):
    return f"{NS}:chests/{name}"


# =================================================================================================
#  VILA DA GUILDA DOS AVENTUREIROS (jigsaw: praça → ruas → Guilda + casas)
# =================================================================================================
GHV = f"{NS}:guild_hall_village/"
J_PLAZA, J_ROAD_IN, J_ROAD_OUT, J_BUILDING = (f"{NS}:ghv_plaza", f"{NS}:ghv_road_entry", f"{NS}:ghv_road_exit",
                                               f"{NS}:ghv_building")
GUILD_BANNER = [("border", "yellow"), ("cross", "light_blue"), ("circle", "yellow"), ("curly_border", "blue")]


def ghv_plaza():
    s = B("ghv_plaza", 17, 9, 17)
    r, c = s.rnd, 8
    for x, z, d in disk(c, c, 8.6):
        if d <= 3.6:
            blk = "minecraft:stone_bricks"
        elif d <= 4.6 or d >= 7.6:
            blk = "minecraft:polished_andesite"
        elif x == c or z == c:
            blk = "minecraft:smooth_stone"
        else:
            blk = s.pick([("minecraft:stone_bricks", 50), ("minecraft:cobblestone", 14), ("minecraft:andesite", 14),
                          ("minecraft:mossy_stone_bricks", 10), ("minecraft:cracked_stone_bricks", 8),
                          ("minecraft:mossy_cobblestone", 4)])
        s.set(x, 0, z, blk)
    # Chafariz: borda de andesito polido, água e uma estátua com lanterna do mar.
    for x, z, d in disk(c, c, 3.6):
        s.set(x, 1, z, "minecraft:polished_andesite" if d > 2.6 else water())
    s.set(c, 1, c, "minecraft:polished_andesite")
    s.set(c, 2, c, "minecraft:chiseled_stone_bricks")
    s.set(c, 3, c, "minecraft:sea_lantern")
    s.set(c, 4, c, slab("stone_brick_slab"))
    # Postes de luz nas diagonais.
    for x, z in ((4, 4), (12, 4), (4, 12), (12, 12)):
        for y in (1, 2, 3):
            s.set(x, y, z, "minecraft:spruce_fence")
        s.set(x, 4, z, lantern())
    # Bancos (de costas para fora, olhando para o chafariz).
    for x in (5, 6, 10, 11):
        s.set(x, 1, 2, stairs("spruce_stairs", "north"))
        s.set(x, 1, 14, stairs("spruce_stairs", "south"))
    # Canteiros de flores a leste e oeste.
    flowers = ["minecraft:poppy", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy",
               "minecraft:azure_bluet", "minecraft:red_tulip"]
    for x in (2, 14):
        for z in (5, 6, 10, 11):
            s.set(x, 0, z, "minecraft:grass_block")
            s.set(x, 1, z, r.choice(flowers))
    # Saídas: norte = rua da Guilda (prioridade), as outras = ruas das casas.
    s.jig(c, 1, 0, "north_up", J_PLAZA, J_ROAD_IN, GHV + "hall_roads", priority=1)
    s.jig(c, 1, 16, "south_up", J_PLAZA, J_ROAD_IN, GHV + "roads")
    s.jig(0, 1, c, "west_up", J_PLAZA, J_ROAD_IN, GHV + "roads")
    s.jig(16, 1, c, "east_up", J_PLAZA, J_ROAD_IN, GHV + "roads")
    for x, z, d in disk(c, c, 8.6):
        s.air(x, 1, z, x, 8, z)
    s.mob(11.5, 1, 12.5, f"{NS}:adventurer", 135.0)
    s.mob(5.5, 1, 9.5, "minecraft:cat", -90.0, {"variant": "minecraft:tabby"})
    save(s, "guild_hall_village/plaza")


def ghv_road(hall):
    name = "hall_road" if hall else "road"
    s = B("ghv_" + name, 5, 6, 8)
    for z in range(8):
        for x in range(5):
            if 1 <= x <= 3:
                s.set(x, 0, z, "minecraft:dirt_path")
                s.air(x, 1, z, x, 3, z)
            elif s.rnd.random() < 0.3:
                s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 3), ("minecraft:gravel", 1)]))
    if hall:
        # Estandartes da Guilda guiando até o salão.
        for x, z, rot in ((0, 3, 12), (4, 3, 4)):
            for y in (1, 2, 3):
                s.set(x, y, z, "minecraft:dark_oak_fence")
            s.banner(x, 4, z, st("blue_banner", rotation=rot), GUILD_BANNER)
    else:
        for y in (1, 2, 3):
            s.set(4, y, 4, "minecraft:spruce_fence")
        s.set(4, 4, 4, lantern())
    s.jig(2, 1, 7, "south_up", J_ROAD_IN)
    s.jig(2, 1, 0, "north_up", J_ROAD_OUT, J_BUILDING, GHV + ("hall" if hall else "houses"))
    save(s, "guild_hall_village/" + name)


def gable_x(s, x0, x1, z0, z1, y0, stair, ridge_slab, fill=None, gx=None):
    """Telhado de duas águas com cumeeira ao longo de X: fileiras z0+k (escada p/ sul) e z1-k (p/ norte)."""
    k = 0
    while z0 + k < z1 - k:
        for x in range(x0, x1 + 1):
            s.set(x, y0 + k, z0 + k, stairs(stair, "south"))
            s.set(x, y0 + k, z1 - k, stairs(stair, "north"))
        k += 1
    top = y0 + k
    if z0 + k == z1 - k:     # largura ímpar: cumeeira de blocos + laje
        for x in range(x0, x1 + 1):
            s.set(x, top, z0 + k, fill or "minecraft:spruce_planks")
            s.set(x, top + 1, z0 + k, slab(ridge_slab))
    else:
        for x in range(x0, x1 + 1):
            s.set(x, top, z0 + k - 1, slab(ridge_slab))
            s.set(x, top, z0 + k, slab(ridge_slab))
    if gx and fill:
        # Empena: preenche o triângulo nas paredes das pontas.
        for xg in gx:
            kk = 1
            while z0 + kk <= z1 - kk:
                for z in range(z0 + kk, z1 - kk + 1):
                    s.set(xg, y0 + kk - 1, z, fill)
                kk += 1
    return top


def gable_z(s, z0, z1, x0, x1, y0, stair, ridge_slab, fill=None, gz=None):
    """Igual a gable_x, mas com a cumeeira ao longo de Z (águas para leste/oeste)."""
    k = 0
    while x0 + k < x1 - k:
        for z in range(z0, z1 + 1):
            s.set(x0 + k, y0 + k, z, stairs(stair, "east"))
            s.set(x1 - k, y0 + k, z, stairs(stair, "west"))
        k += 1
    top = y0 + k
    if x0 + k == x1 - k:
        for z in range(z0, z1 + 1):
            s.set(x0 + k, top, z, fill or "minecraft:spruce_planks")
            s.set(x0 + k, top + 1, z, slab(ridge_slab))
    else:
        for z in range(z0, z1 + 1):
            s.set(x0 + k - 1, top, z, slab(ridge_slab))
            s.set(x0 + k, top, z, slab(ridge_slab))
    if gz and fill:
        for zg in gz:
            kk = 1
            while x0 + kk <= x1 - kk:
                for x in range(x0 + kk, x1 - kk + 1):
                    s.set(x, y0 + kk - 1, zg, fill)
                kk += 1
    return top


def ghv_hall():
    """Salão da Guilda dos Aventureiros: 2 andares em enxaimel, balcão, quadro de missões, sala do Mestre,
    taverna com lareira, dormitório e um campanário. Frente (entrada) para o sul."""
    s = B("ghv_guild_hall", 25, 27, 23)
    r = s.rnd
    X0, X1, Z0, Z1 = 1, 23, 1, 18
    POST = log("minecraft:stripped_dark_oak_log")
    BX, BZ = log("minecraft:stripped_dark_oak_log", "x"), log("minecraft:stripped_dark_oak_log", "z")
    PLASTER = "minecraft:white_terracotta"
    posts_x = (1, 5, 10, 14, 19, 23)
    posts_z = (1, 6, 10, 14, 18)
    win_x = (3, 7, 8, 16, 17, 21)
    win_z = (3, 4, 8, 12, 16)

    s.fill(X0, 0, Z0, X1, 0, Z1, "minecraft:stone_bricks")
    s.fill(2, 0, 2, 22, 0, 17, "minecraft:spruce_planks")
    # Paredes externas (térreo y1-5, viga y6, andar de cima y7-10, viga y11).
    for y in range(1, 12):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                on_x_wall = z in (Z0, Z1)
                post = (on_x_wall and x in posts_x) or (not on_x_wall and z in posts_z)
                if y in (6, 11):
                    blk = POST if post and x in (X0, X1) and z in (Z0, Z1) else (BX if on_x_wall else BZ)
                elif post:
                    blk = POST
                elif y == 1:
                    blk = "minecraft:stone_bricks"
                else:
                    blk = PLASTER
                    win = (x in win_x) if on_x_wall else (z in win_z)
                    if win and (y in (2, 3, 4) or y in (8, 9)):
                        if not (x == X1 and 11 <= z <= 15):
                            blk = pane()
                s.set(x, y, z, blk)
    # Entrada em arco (3 de largura) na frente.
    for x in (11, 12, 13):
        for y in (1, 2, 3):
            s.set(x, y, Z1, AIR)
    s.set(11, 4, Z1, stairs("dark_oak_stairs", "west", "top"))
    s.set(12, 4, Z1, slab("dark_oak_slab", "top"))
    s.set(13, 4, Z1, stairs("dark_oak_stairs", "east", "top"))
    s.sign(12, 5, Z1 + 1, st("dark_oak_wall_sign", facing="south", waterlogged="false"),
           ["sign.isekai_mahou.guild_hall_village.1", "sign.isekai_mahou.guild_hall_village.2",
            "sign.isekai_mahou.guild_hall_village.3"],
           color="yellow", glow=True)
    # Varanda com colunas, telhado de lajes, bancos e estandartes.
    s.fill(6, 0, 19, 18, 0, 20, "minecraft:polished_andesite")
    for x in (6, 18):
        for y in range(1, 6):
            s.set(x, y, 20, POST)
    for x in range(5, 20):
        for z in (19, 20, 21):
            s.set(x, 6, z, slab("dark_oak_slab"))
    for x in (8, 16):
        s.set(x, 5, 20, lantern(True))
    for x in (8, 9, 15, 16):
        s.set(x, 1, 20, stairs("spruce_stairs", "north"))
    for x in (9, 15):
        s.banner(x, 4, 19, st("blue_wall_banner", facing="south"), GUILD_BANNER)
    s.set(12, 0, 21, "minecraft:dirt_path")
    s.set(12, 0, 22, "minecraft:dirt_path")
    for x in (11, 13):
        s.set(x, 0, 21, "minecraft:dirt_path")
    s.jig(12, 1, 22, "south_up", J_BUILDING)

    # ---------------- Térreo
    # Divisória z=7 (fundos: escritório e escada) e x=11 (entre escritório e escada).
    for y in range(1, 6):
        for x in range(2, 23):
            s.set(x, y, 7, POST if x in (5, 11, 17) else ("minecraft:spruce_planks" if y > 1 else "minecraft:stone_bricks"))
        for z in range(2, 7):
            s.set(11, y, z, "minecraft:spruce_planks" if y > 1 else "minecraft:stone_bricks")
    s.door(4, 1, 7, "dark_oak", "south", "left")
    for x in (19, 20):
        for y in (1, 2, 3):
            s.set(x, y, 7, AIR)
    s.set(19, 4, 7, stairs("dark_oak_stairs", "west", "top"))
    s.set(20, 4, 7, stairs("dark_oak_stairs", "east", "top"))
    # Balcão de recepção (bloco do módulo guild) com a Recepcionista atrás.
    for x in range(8, 17):
        s.set(x, 1, 10, f"{NS}:guild_counter[facing=south]")
    s.barrel(8, 1, 8)
    s.barrel(16, 1, 8)
    s.set(9, 1, 8, st("lectern", facing="south", has_book="false", powered="false"))
    s.set(15, 1, 8, "minecraft:bookshelf")
    s.set(15, 2, 8, candle(3, "yellow"))
    s.mob(12.5, 1, 9.5, f"{NS}:guild_receptionist", YAW["south"])
    # Quadros de missões na parede oeste + estante/atril ao lado.
    s.set(2, 1, 12, f"{NS}:quest_board[facing=east]")
    s.set(2, 1, 13, f"{NS}:quest_board[facing=east]")
    for z in (11, 14):
        s.set(2, 1, z, "minecraft:bookshelf")
        s.set(2, 2, z, "minecraft:bookshelf")
        s.set(2, 3, z, lantern())
    s.set(2, 1, 16, st("lectern", facing="east", has_book="false", powered="false"))
    # Tapete vermelho da entrada até o balcão.
    for z in range(11, 18):
        for x in (11, 12, 13):
            s.set(x, 1, z, "minecraft:red_carpet" if x == 12 else "minecraft:yellow_carpet")
    # Taverna: mesas (cerca + placa) e cadeiras.
    for tx, tz in ((16, 13), (16, 16), (19, 16)):
        s.set(tx, 1, tz, "minecraft:dark_oak_fence")
        s.set(tx, 2, tz, "minecraft:spruce_pressure_plate[powered=false]")
        s.set(tx - 1, 1, tz, stairs("spruce_stairs", "west"))
        s.set(tx + 1, 1, tz, stairs("spruce_stairs", "east"))
    # Lareira de tijolos com chaminé até acima do telhado.
    for y in range(1, 21):
        for z in (12, 13, 14):
            s.set(22, y, z, "minecraft:bricks")
    s.set(22, 1, 13, campfire(facing="west"))
    s.set(22, 2, 13, AIR)
    s.set(22, 3, 13, AIR)
    s.set(22, 21, 13, campfire(facing="west"))
    s.set(21, 0, 13, "minecraft:polished_andesite")
    for z in (11, 15):
        s.set(20, 1, z, stairs("spruce_stairs", "west"))
    s.set(20, 1, 13, "minecraft:red_carpet")
    # Lustres (corrente + lanterna).
    for x in (6, 12, 18):
        s.set(x, 5, 13, st("chain", axis="y", waterlogged="false"))
        s.set(x, 4, 13, lantern(True))
    s.set(6, 5, 4, st("chain", axis="y", waterlogged="false"))
    s.set(6, 4, 4, lantern(True))
    s.set(16, 5, 4, st("chain", axis="y", waterlogged="false"))
    s.set(16, 4, 4, lantern(True))
    # Escritório do Mestre da Guilda.
    for x in range(2, 11):
        for y in (1, 2, 3, 4):
            if x != 6 or y > 1:
                s.set(x, y, 2, "minecraft:bookshelf")
    s.set(6, 1, 2, "minecraft:chiseled_bookshelf[facing=south,slot_0_occupied=false,slot_1_occupied=false,"
                   "slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]")
    s.banner(4, 4, 3, st("blue_wall_banner", facing="south"), GUILD_BANNER)
    s.banner(8, 4, 3, st("blue_wall_banner", facing="south"), GUILD_BANNER)
    for x in (5, 6, 7):
        s.set(x, 1, 4, slab("dark_oak_slab", "top"))
    s.set(5, 2, 4, candle(2, "white"))
    s.set(7, 2, 4, "minecraft:potted_fern")
    s.set(5, 1, 5, stairs("spruce_stairs", "south"))
    s.set(7, 1, 5, stairs("spruce_stairs", "south"))
    s.chest(10, 1, 3, loot("guild_hall_village"), "west")
    s.set(2, 1, 6, st("anvil", facing="east"))
    s.armor_stand(9.5, 1, 5.5, YAW["west"], head="iron_helmet", chest="iron_chestplate", legs="iron_leggings",
                  feet="iron_boots", hand="iron_sword")
    for x in range(3, 10):
        for z in (4, 5):
            if s.empty(x, 1, z):
                s.set(x, 1, z, "minecraft:blue_carpet")
    s.mob(6.5, 1, 3.5, f"{NS}:guild_master", YAW["south"])
    # Escada para o andar de cima (sobe para leste) e depósito.
    for i, x in enumerate(range(13, 18)):
        for z in (2, 3):
            s.set(x, 1 + i, z, stairs("dark_oak_stairs", "east"))
            for y in range(1, 1 + i):
                s.set(x, y, z, "minecraft:dark_oak_planks")
    for z in range(2, 6):
        s.barrel(22, 1, z, loot("guild_hall_village_house") if z == 3 else None, "west")
    s.barrel(22, 2, 2)
    s.barrel(21, 1, 2)
    s.set(18, 1, 6, "minecraft:crafting_table")

    # ---------------- Andar de cima (piso y6) — dormitório e sala de estudos
    s.fill(2, 6, 2, 22, 6, 17, "minecraft:dark_oak_planks")
    for x in range(13, 18):
        for z in (2, 3):
            s.set(x, 6, z, AIR)
    for z in (2, 3):
        # último degrau no nível do piso de cima (sem pulo de 1 bloco ao sair da escada)
        s.set(18, 6, z, stairs("dark_oak_stairs", "east"))
    for x in range(13, 18):
        s.set(x, 7, 4, "minecraft:dark_oak_fence")
    s.set(12, 7, 2, "minecraft:dark_oak_fence")
    s.set(12, 7, 3, "minecraft:dark_oak_fence")
    for i, x in enumerate((3, 5, 7, 9)):
        color = "blue" if i % 2 == 0 else "light_blue"
        s.bed(x, 7, 16, color, "south")
        s.bed(x, 7, 3, color, "north")
    for x in (4, 6, 8):
        s.barrel(x, 7, 17)
        s.set(x, 8, 17, candle(1, "white"))
    s.chest(10, 7, 17, loot("guild_hall_village_house"), "north")
    for z in range(8, 12):
        for y in (7, 8, 9):
            s.set(22, y, z, "minecraft:bookshelf")
    s.set(20, 7, 9, "minecraft:cartography_table")
    s.set(19, 7, 16, st("lectern", facing="north", has_book="false", powered="false"))
    s.set(21, 7, 16, "minecraft:bookshelf")
    s.set(21, 8, 16, candle(4, "orange"))
    s.set(16, 7, 10, "minecraft:dark_oak_fence")
    s.set(16, 8, 10, "minecraft:spruce_pressure_plate[powered=false]")
    s.set(15, 7, 10, stairs("spruce_stairs", "west"))
    s.set(17, 7, 10, stairs("spruce_stairs", "east"))
    for x in range(12, 20):
        for z in range(12, 15):
            s.soft(x, 7, z, "minecraft:light_blue_carpet")
    s.mob(12.5, 7, 12.5, f"{NS}:adventurer", YAW["south"])
    s.mob(17.5, 1, 15.5, f"{NS}:adventurer", YAW["west"])
    s.mob(4.5, 1, 12.5, f"{NS}:adventurer", YAW["west"])
    s.mob(19.5, 1, 13.5, "minecraft:cat", YAW["east"], {"variant": "minecraft:black"})
    # Forro (y11) e lanternas penduradas.
    s.fill(2, 11, 2, 22, 11, 17, "minecraft:dark_oak_planks")
    for x, z in ((6, 9), (12, 9), (18, 9), (6, 14), (12, 14), (18, 14)):
        s.set(x, 10, z, lantern(True))
    s.air(2, 1, 2, 22, 10, 17)

    # ---------------- Telhado (cumeeira leste-oeste), empenas e campanário
    gable_x(s, 0, 24, 0, 19, 11, "deepslate_tile_stairs", "deepslate_tile_slab")
    for xg in (X0, X1):
        for k in range(1, 10):
            for z in range(k + 1, 19 - k):
                if 1 <= z <= 18:
                    s.set(xg, 11 + k, z, POST if z in (9, 10) else PLASTER)
        for z in (9, 10):
            s.set(xg, 14, z, pane())
            s.set(xg, 15, z, pane())
    for x in range(2, 23):
        for k in range(1, 10):
            for z in range(k + 1, 19 - k):
                s.soft(x, 11 + k, z, AIR)
    for y in range(11, 21):
        for z in (12, 13, 14):
            s.set(22, y, z, "minecraft:bricks")
    s.set(22, 21, 13, campfire(facing="west"))
    for x in (11, 12, 13):
        for z in (8, 9, 10, 11):
            s.set(x, 21, z, "minecraft:dark_oak_planks")
        s.set(x, 20, 8, "minecraft:dark_oak_planks")
        s.set(x, 20, 11, "minecraft:dark_oak_planks")
    for x, z in ((11, 8), (13, 8), (11, 11), (13, 11)):
        s.set(x, 22, z, "minecraft:dark_oak_fence")
        s.set(x, 23, z, "minecraft:dark_oak_fence")
    for x in range(10, 15):
        for z in range(7, 13):
            s.set(x, 24, z, slab("deepslate_tile_slab"))
    for x in (11, 12, 13):
        for z in (8, 9, 10, 11):
            s.set(x, 24, z, "minecraft:dark_oak_planks")
    for x in (11, 12, 13):
        for z in (9, 10):
            s.set(x, 25, z, slab("deepslate_tile_slab"))
    s.set(12, 23, 9, st("bell", attachment="ceiling", facing="north", powered="false"))
    s.air(11, 22, 8, 13, 23, 11)
    save(s, "guild_hall_village/guild_hall")


def ghv_cottage(name, wood, roof):
    s = B("ghv_" + name, 11, 11, 11)
    r = s.rnd
    planks = f"minecraft:{wood}_planks"
    logb = log(f"minecraft:{wood}_log")
    s.fill(1, 0, 1, 9, 0, 7, "minecraft:cobblestone")
    s.fill(2, 0, 2, 8, 0, 6, planks)
    for y in range(1, 5):
        for x in range(1, 10):
            for z in range(1, 8):
                if x in (1, 9) or z in (1, 7):
                    corner = x in (1, 9) and z in (1, 7)
                    s.set(x, y, z, logb if corner or (x == 5 and z in (1, 7)) else
                          ("minecraft:cobblestone" if y == 1 else planks))
    for x, z in ((3, 7), (7, 7), (3, 1), (7, 1), (1, 4), (9, 4)):
        s.set(x, 2, z, pane())
        s.set(x, 3, z, pane())
    s.set(5, 1, 7, AIR)
    s.door(5, 1, 7, wood, "south", "left")
    s.set(5, 3, 7, planks)
    for x in range(1, 10):
        s.set(x, 5, 1, planks)
        s.set(x, 5, 7, planks)
    gable_x(s, 0, 10, 0, 8, 5, f"{roof}_stairs", f"{roof}_slab", planks, gx=(1, 9))
    s.air(2, 1, 2, 8, 9, 6)
    for x in range(2, 9):
        for k in range(1, 5):
            for z in range(k + 1, 8 - k):
                s.soft(x, 4 + k, z, AIR)
    # Interior
    s.bed(2, 1, 3, r.choice(["red", "green", "yellow", "brown"]), "north")
    s.set(8, 1, 2, "minecraft:crafting_table")
    s.set(7, 1, 2, st("furnace", facing="south", lit="false"))
    s.chest(8, 1, 5, loot("guild_hall_village_house"), "west")
    s.barrel(2, 1, 6)
    s.set(2, 2, 6, r.choice(["minecraft:potted_poppy", "minecraft:potted_dandelion", "minecraft:potted_blue_orchid"]))
    s.set(5, 3, 2, wall_torch("south"))
    for x in (4, 5, 6):
        for z in (3, 4, 5):
            s.set(x, 1, z, mc(r.choice(["brown", "orange", "red"]) + "_carpet"))
    # Caminho e jardim na frente.
    for z in (8, 9, 10):
        s.set(5, 0, z, "minecraft:dirt_path")
    for x, fl in ((3, "minecraft:poppy"), (7, "minecraft:oxeye_daisy"), (2, "minecraft:cornflower"),
                  (8, "minecraft:red_tulip")):
        s.set(x, 0, 9, "minecraft:grass_block")
        s.set(x, 1, 9, fl)
    s.jig(5, 1, 10, "south_up", J_BUILDING)
    save(s, "guild_hall_village/" + name)


def ghv_smithy():
    s = B("ghv_smithy", 11, 9, 11)
    s.fill(0, 0, 1, 10, 0, 8, "minecraft:cobblestone")
    for x in range(1, 10):
        for z in range(2, 8):
            s.set(x, 0, z, s.pick([("minecraft:stone_bricks", 5), ("minecraft:cobblestone", 3),
                                   ("minecraft:andesite", 2)]))
    for y in range(1, 5):
        for x in range(1, 10):
            s.set(x, y, 1, "minecraft:cobblestone" if x not in (1, 9) else log("minecraft:spruce_log"))
        for z in range(2, 5):
            if y <= 3:
                s.set(1, y, z, "minecraft:cobblestone")
                s.set(9, y, z, "minecraft:cobblestone")
    for x in (1, 9):
        for y in range(1, 5):
            s.set(x, y, 8, log("minecraft:spruce_log"))
    for x in range(0, 11):
        for z in range(1, 9):
            s.set(x, 5, z, slab("spruce_slab"))
        s.set(x, 5, 9, stairs("spruce_stairs", "north"))
    for y in range(5, 8):
        s.set(2, y, 1, "minecraft:cobblestone")
    s.set(2, 8, 1, campfire(facing="south"))
    s.set(2, 1, 2, st("blast_furnace", facing="south", lit="false"))
    s.set(3, 1, 2, st("furnace", facing="south", lit="false"))
    s.set(4, 1, 2, "minecraft:lava_cauldron")
    s.set(6, 1, 2, st("water_cauldron", level=3))
    s.set(8, 1, 2, st("grindstone", face="floor", facing="north"))
    s.set(8, 1, 4, "minecraft:smithing_table")
    s.set(6, 1, 4, st("anvil", facing="east"))
    s.chest(8, 1, 6, "minecraft:chests/village/village_weaponsmith", "west")
    s.barrel(2, 1, 6)
    s.barrel(2, 2, 6)
    s.barrel(2, 1, 5)
    s.set(5, 4, 4, lantern(True))
    s.air(2, 1, 2, 8, 4, 7)
    s.armor_stand(4.5, 1, 6.5, YAW["south"], head="iron_helmet", chest="chainmail_chestplate", hand="iron_axe")
    s.villager(5.5, 1, 4.5, "weaponsmith", "plains", YAW["south"])
    for z in (9, 10):
        s.set(5, 0, z, "minecraft:dirt_path")
    s.jig(5, 1, 10, "south_up", J_BUILDING)
    save(s, "guild_hall_village/smithy")


def tent(s, ox, oz, along, length, color, opening_end=True):
    """Barraca em A, 5 de largura. along='z': corte transversal em x (ox..ox+4), comprimento oz..oz+len-1,
    fundo fechado em oz, abertura em oz+len-1. along='x': corte em z (oz..oz+4), fundo em ox."""
    w = mc(f"{color}_wool")
    for v in range(length):
        for u, y in ((0, 1), (4, 1), (1, 2), (3, 2), (2, 3)):
            x, z = (ox + u, oz + v) if along == "z" else (ox + v, oz + u)
            s.set(x, y, z, w)
        for u, y in ((1, 1), (2, 1), (3, 1), (2, 2)):
            x, z = (ox + u, oz + v) if along == "z" else (ox + v, oz + u)
            if v == 0:
                s.set(x, y, z, w)
            else:
                s.soft(x, y, z, AIR)
    # Mastros saindo da cumeeira nas duas pontas.
    for v in (0, length - 1):
        x, z = (ox + 2, oz + v) if along == "z" else (ox + v, oz + 2)
        s.set(x, 4, z, "minecraft:spruce_fence")


def ghv_adventurer_camp():
    s = B("ghv_adventurer_camp", 13, 7, 13)
    r = s.rnd
    for x, z, d in disk(6, 6, 6.2):
        if r.random() < 0.55:
            s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 3), ("minecraft:dirt_path", 2), ("minecraft:gravel", 1)]))
    tent(s, 4, 0, "z", 4, "green")
    tent(s, 0, 4, "x", 4, "white")
    # barraca leste: fundo no leste (x=12), abre para oeste
    w = "minecraft:red_wool"
    for v in range(4):
        x = 12 - v
        for u, y in ((0, 1), (4, 1), (1, 2), (3, 2), (2, 3)):
            s.set(x, y, 4 + u, w)
        for u, y in ((1, 1), (2, 1), (3, 1), (2, 2)):
            if v == 0:
                s.set(x, y, 4 + u, w)
            else:
                s.soft(x, y, 4 + u, AIR)
    s.bed(6, 1, 2, "green", "north")
    s.bed(2, 1, 6, "white", "west")
    s.bed(10, 1, 6, "red", "east")
    s.chest(5, 1, 1, loot("guild_hall_village_house"), "south")
    s.barrel(7, 1, 1)
    for x, z, d in disk(6, 6, 1.6):
        s.set(x, 0, z, "minecraft:cobblestone")
    s.set(6, 0, 6, "minecraft:coarse_dirt")
    s.set(6, 1, 6, campfire())
    for x in (5, 6, 7):
        s.set(x, 1, 9, log("minecraft:stripped_spruce_log", "x"))
    s.set(4, 1, 8, log("minecraft:spruce_log"))
    s.set(8, 1, 8, log("minecraft:spruce_log"))
    s.set(1, 1, 10, st("hay_block", axis="y"))
    s.set(1, 2, 10, "minecraft:target[power=0]")
    s.armor_stand(11.5, 1, 10.5, YAW["west"], head="leather_helmet", chest="leather_chestplate", hand="wooden_sword")
    for z in (10, 11, 12):
        s.set(6, 0, z, "minecraft:dirt_path")
    s.air(0, 1, 0, 12, 5, 12)
    s.mob(6.5, 1, 8.5, f"{NS}:adventurer", YAW["north"])
    s.mob(3.5, 1, 10.5, f"{NS}:adventurer", YAW["east"])
    s.jig(6, 1, 12, "south_up", J_BUILDING)
    save(s, "guild_hall_village/adventurer_camp")


def ghv_market():
    s = B("ghv_market", 13, 7, 11)
    r = s.rnd
    for x in range(0, 13):
        for z in range(0, 10):
            s.set(x, 0, z, s.pick([("minecraft:dirt_path", 5), ("minecraft:packed_mud", 2), ("minecraft:gravel", 1),
                                   ("minecraft:coarse_dirt", 2)]))
    for (x0, x1, c1, c2) in ((1, 5, "red", "white"), (7, 11, "yellow", "white")):
        for x, z in ((x0, 2), (x1, 2), (x0, 6), (x1, 6)):
            for y in (1, 2, 3):
                s.set(x, y, z, "minecraft:oak_fence")
        for x in range(x0, x1 + 1):
            for z in range(1, 8):
                s.set(x, 4, z, mc(f"{c1 if (x - x0) % 2 == 0 else c2}_wool"))
    s.set(2, 1, 5, st("composter", level=5))
    s.set(3, 1, 5, st("hay_block", axis="y"))
    s.set(4, 1, 5, "minecraft:pumpkin")
    s.set(2, 1, 3, "minecraft:melon")
    s.barrel(4, 1, 3, loot("guild_hall_village_house"))
    s.set(3, 1, 3, st("hay_block", axis="x"))
    s.set(3, 2, 5, "minecraft:potted_red_tulip")
    s.barrel(8, 1, 5, None, "north")
    s.set(9, 1, 5, st("water_cauldron", level=3))
    s.set(10, 1, 5, st("smoker", facing="south", lit="false"))
    s.barrel(10, 1, 3)
    s.barrel(8, 1, 3)
    s.set(9, 1, 3, "minecraft:dried_kelp_block")
    s.set(3, 3, 4, lantern(True))
    s.set(9, 3, 4, lantern(True))
    s.air(0, 1, 0, 12, 5, 9)
    s.villager(3.5, 1, 4.5, "farmer", "plains", YAW["south"])
    s.villager(9.5, 1, 4.5, "fisherman", "plains", YAW["south"])
    s.set(6, 0, 10, "minecraft:dirt_path")
    s.jig(6, 1, 10, "south_up", J_BUILDING)
    save(s, "guild_hall_village/market")


def ghv_road_end():
    s = B("ghv_road_end", 5, 6, 5)
    s.set(2, 0, 2, "minecraft:dirt_path")
    s.set(2, 0, 3, "minecraft:dirt_path")
    s.set(2, 0, 4, "minecraft:dirt_path")
    for y in (1, 2, 3):
        s.set(2, y, 1, "minecraft:spruce_fence")
    s.set(2, 4, 1, lantern())
    s.set(1, 1, 2, stairs("spruce_stairs", "west"))
    s.set(3, 1, 2, stairs("spruce_stairs", "east"))
    for x, fl in ((0, "minecraft:poppy"), (4, "minecraft:dandelion"), (1, "minecraft:allium"), (3, "minecraft:azure_bluet")):
        s.set(x, 0, 1 if x in (1, 3) else 2, "minecraft:grass_block")
        s.set(x, 1, 1 if x in (1, 3) else 2, fl)
    s.air(0, 1, 0, 4, 4, 4)
    s.jig(2, 1, 4, "south_up", J_BUILDING)
    save(s, "guild_hall_village/road_end")


def build_guild_hall_village():
    ghv_plaza()
    ghv_road(True)
    ghv_road(False)
    ghv_hall()
    ghv_cottage("cottage_oak", "oak", "spruce")
    ghv_cottage("cottage_spruce", "spruce", "dark_oak")
    ghv_smithy()
    ghv_adventurer_camp()
    ghv_market()
    ghv_road_end()


# =================================================================================================
#  VILA GOBLIN (procedural, 2 variantes)
# =================================================================================================
GOB_GROUND = [("minecraft:coarse_dirt", 30), ("minecraft:dirt_path", 25), ("minecraft:packed_mud", 12),
              ("minecraft:podzol", 10), ("minecraft:gravel", 5), (None, 40)]
GOBLIN_BANNER = [("skull", "black"), ("border", "black"), ("triangles_bottom", "brown")]


class Occ:
    """Mapa de ocupação 2D para espalhar coisas sem sobrepor."""

    def __init__(self):
        self.cells = set()

    def free(self, x0, z0, x1, z1, margin=1):
        for x in range(x0 - margin, x1 + margin + 1):
            for z in range(z0 - margin, z1 + margin + 1):
                if (x, z) in self.cells:
                    return False
        return True

    def take(self, x0, z0, x1, z1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.cells.add((x, z))


def _face_toward(dx, dz, invert=False):
    if invert:
        dx, dz = -dx, -dz
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def _rot(facing):
    return {"south": 0, "west": 4, "north": 8, "east": 12}[facing]


def round_hut(s, cx, cz, rad, to_center, kind, loot_table=None):
    """Cabana redonda de madeira/barro com telhado cônico de palha. A porta aponta para o centro da vila."""
    r = s.rnd
    walls = [(log("minecraft:spruce_log"), 4), ("minecraft:packed_mud", 3), ("minecraft:spruce_planks", 2),
             ("minecraft:mud_bricks", 1), (log("minecraft:stripped_spruce_log"), 1)]
    if kind == "shaman":
        walls = [(log("minecraft:dark_oak_log"), 4), ("minecraft:mud_bricks", 3), ("minecraft:mossy_cobblestone", 2)]
    for x, z, d in disk(cx, cz, rad + 0.5):
        s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 2), ("minecraft:packed_mud", 1), ("minecraft:podzol", 1)]))
        for y in (1, 2, 3):
            s.set(x, y, z, s.pick(walls) if d >= rad - 0.5 else AIR)
    # Porta (2 de altura) no ponto da parede mais próximo do centro.
    dx, dz = to_center
    px, pz = round(cx + dx * rad), round(cz + dz * rad)
    for y in (1, 2):
        s.set(px, y, pz, AIR)
    s.set(px, 3, pz, log("minecraft:spruce_log", "x" if abs(dz) > abs(dx) else "z"))
    # Telhado cônico oco de palha.
    y, rr = 4, rad + 1.0
    thatch = [(st("hay_block", axis="y"), 8), ("minecraft:moss_block", 1)]
    if kind == "shaman":
        thatch = [(st("hay_block", axis="y"), 6), ("minecraft:moss_block", 2), ("minecraft:mangrove_roots", 1)]
    while rr > 0.7:
        for x, z, d in disk(cx, cz, rr + 0.45):
            s.set(x, y, z, s.pick(thatch) if d > rr - 1.15 else AIR)
        y += 1
        rr -= 0.95
    s.set(round(cx), y, round(cz), st("hay_block", axis="y"))
    s.set(round(cx), y + 1, round(cz), "minecraft:spruce_fence")
    if r.random() < 0.6 or kind == "shaman":
        s.set(round(cx), y + 2, round(cz), skull("skeleton", r.randrange(16)))
    # Interior.
    ix, iz = round(cx - dx * (rad - 1.2)), round(cz - dz * (rad - 1.2))
    if kind == "shaman":
        s.set(round(cx), 1, round(cz), st("water_cauldron", level=3))
        s.set(round(cx + 1), 1, round(cz - 1), st("brewing_stand", has_bottle_0="false", has_bottle_1="false",
                                                     has_bottle_2="false"))
        for ox, oz in ((-1, 1), (1, 1), (-1, -1)):
            s.soft(round(cx + ox), 1, round(cz + oz), candle(r.randint(1, 4), "purple"))
        s.set(round(cx - 2), 1, round(cz), "minecraft:spruce_fence")
        s.set(round(cx - 2), 2, round(cz), skull("skeleton", 4))
        s.soft(round(cx + 2), 1, round(cz + 1), st("amethyst_cluster", facing="up", waterlogged="false"))
        s.soft(round(cx), 1, round(cz + 2), lantern(soul=True))
        if loot_table:
            s.chest(ix, 1, iz, loot_table, _face_toward(dx, dz))
    else:
        s.soft(ix, 1, iz, st("hay_block", axis="y"))
        s.soft(ix, 2, iz, "minecraft:brown_carpet")
        sx, sz = round(cx + dz * (rad - 1.2)), round(cz - dx * (rad - 1.2))
        if loot_table:
            s.soft(sx, 1, sz, st("chest", facing=_face_toward(cx - sx, cz - sz), type="single", waterlogged="false"),
                   {"LootTable": loot_table})
        else:
            s.soft(sx, 1, sz, r.choice(["minecraft:bone_block", "minecraft:pumpkin", "minecraft:melon",
                                        st("barrel", facing="up", open="false")]))
        tx, tz = round(cx - dz * (rad - 1.2)), round(cz + dx * (rad - 1.2))
        s.soft(tx, 1, tz, r.choice(["minecraft:cobweb", skull("skeleton", r.randrange(16)), "minecraft:bone_block"]))


def shack(s, x0, z0, door):
    """Barraco quadrado 5x5 de troncos e tábuas com telhado de palha meio torto."""
    r = s.rnd
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            s.set(x, 0, z, "minecraft:coarse_dirt")
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            for y in (1, 2, 3):
                if edge:
                    corner = x in (x0, x0 + 4) and z in (z0, z0 + 4)
                    s.set(x, y, z, log("minecraft:spruce_log") if corner else
                          s.pick([("minecraft:spruce_planks", 5), ("minecraft:mossy_cobblestone", 2),
                                  ("minecraft:cobblestone", 1), (log("minecraft:oak_log", "x"), 1)]))
                else:
                    s.set(x, y, z, AIR)
    for x in range(x0 - 1, x0 + 6):
        for z in range(z0 - 1, z0 + 6):
            s.set(x, 4, z, s.pick([(st("hay_block", axis="y"), 6), (slab("spruce_slab"), 2),
                                   ("minecraft:moss_block", 1)]))
    for x in range(x0 + 1, x0 + 4):
        for z in range(z0 + 1, z0 + 4):
            s.set(x, 5, z, st("hay_block", axis="y") if r.random() < 0.8 else slab("spruce_slab"))
    dx, dz = DIRV[door]
    px, pz = x0 + 2 + dx * 2, z0 + 2 + dz * 2
    s.set(px, 1, pz, AIR)
    s.set(px, 2, pz, AIR)
    s.soft(x0 + 2 - dx, 1, z0 + 2 - dz, st("hay_block", axis="y"))
    s.soft(x0 + 1, 1, z0 + 1, st("barrel", facing="up", open="false"))
    s.soft(x0 + 3, 1, z0 + 1 if door != "north" else z0 + 3,
           r.choice(["minecraft:bone_block", "minecraft:cobweb", "minecraft:pumpkin"]))


def cage(s, x0, z0, prisoner=True):
    """Jaula de grades 3x3 com um aldeão preso (resgate)."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            s.set(x, 0, z, "minecraft:cobblestone")
            for y in (1, 2):
                s.set(x, y, z, AIR if (x == x0 + 1 and z == z0 + 1) else "minecraft:iron_bars")
            s.set(x, 3, z, slab("spruce_slab"))
    if prisoner:
        s.villager(x0 + 1.5, 1, z0 + 1.5, "none", "plains", 0.0)


def spit_fire(s, cx, cz, big=False):
    rad = 2.2 if big else 1.5
    for x, z, d in disk(cx, cz, rad):
        s.set(x, 0, z, "minecraft:cobblestone" if d > rad - 1 else "minecraft:coarse_dirt")
        s.set(x, 1, z, AIR)
    s.set(cx, 1, cz, campfire())
    if big:
        for x in (cx - 1, cx + 1):
            s.set(x, 1, cz, "minecraft:spruce_fence")
            s.set(x, 2, cz, "minecraft:spruce_fence")
        for x in (cx - 1, cx, cx + 1):
            s.set(x, 3, cz, "minecraft:spruce_fence")


def goblin_banner_pole(s, x, z, rot=0):
    for y in (1, 2, 3):
        s.set(x, y, z, "minecraft:spruce_fence")
    s.banner(x, 4, z, st("green_banner", rotation=rot), GOBLIN_BANNER)


def goblin_village(variant):
    s = B("goblin_village_" + variant, 39, 13, 39)
    r, c = s.rnd, 19
    occ = Occ()
    for x, z, d in disk(c, c, 18.0):
        g = s.pick(GOB_GROUND)
        if g:
            s.set(x, 0, z, g)
    # Grande fogueira central com espeto, troncos de assento e totem.
    spit_fire(s, c, c, big=True)
    occ.take(c - 3, c - 3, c + 3, c + 3)
    for ang in range(0, 360, 60):
        a = math.radians(ang + r.uniform(-8, 8))
        lx, lz = round(c + math.cos(a) * 4.2), round(c + math.sin(a) * 4.2)
        s.set(lx, 1, lz, log("minecraft:spruce_log", "x" if abs(math.sin(a)) > 0.7 else "z"))
        occ.take(lx, lz, lx, lz)
    tx, tz = c + 6, c - 2
    for y, blk in enumerate([log("minecraft:spruce_log"), log("minecraft:spruce_log"),
                             st("carved_pumpkin", facing="west"), log("minecraft:stripped_spruce_log"),
                             st("jack_o_lantern", facing="west")], start=1):
        s.set(tx, y, tz, blk)
    s.set(tx, 6, tz, skull("skeleton", 12))
    for d in ("north", "south"):
        s.banner(tx + DIRV[d][0], 4, tz + DIRV[d][1], st("green_wall_banner", facing=d), GOBLIN_BANNER)
    occ.take(tx - 1, tz - 1, tx + 1, tz + 1)
    # Cabanas em anel ao redor do fogo; a primeira é a do xamã.
    n = 7
    base = r.random() * math.tau
    chest_huts = {1, 4}
    for i in range(n):
        a = base + i * math.tau / n + r.uniform(-0.2, 0.2)
        dist = r.uniform(11.5, 13.5)
        hx, hz = c + math.cos(a) * dist, c + math.sin(a) * dist
        to_c = (-math.cos(a), -math.sin(a))
        rad = 3.4 if i == 0 else r.choice([2.4, 2.6, 2.9])
        if i == 0:
            round_hut(s, hx, hz, rad, to_c, "shaman", loot("goblin_village"))
            s.mob(round(hx) + 0.5, 1, round(hz) + 1.5, f"{NS}:goblin_shaman", YAW[_face_toward(*to_c)])
        elif i in (3, 6) and r.random() < 0.6:
            x0, z0 = round(hx) - 2, round(hz) - 2
            shack(s, x0, z0, _face_toward(*to_c))
            rad = 3.2
        else:
            round_hut(s, hx, hz, rad, to_c, "straw", loot("goblin_village") if i in chest_huts else None)
        occ.take(round(hx - rad - 1), round(hz - rad - 1), round(hx + rad + 1), round(hz + rad + 1))
        # Fogueira pequena na porta de algumas cabanas.
        if r.random() < 0.5:
            fx, fz = round(hx + to_c[0] * (rad + 2.5)), round(hz + to_c[1] * (rad + 2.5))
            if occ.free(fx - 1, fz - 1, fx + 1, fz + 1, 0):
                spit_fire(s, fx, fz)
                occ.take(fx - 1, fz - 1, fx + 1, fz + 1)

    def spot(w=1, tries=80, rmin=5, rmax=16):
        for _ in range(tries):
            a = r.random() * math.tau
            d = r.uniform(rmin, rmax)
            x, z = round(c + math.cos(a) * d), round(c + math.sin(a) * d)
            if occ.free(x, z, x + w - 1, z + w - 1):
                occ.take(x, z, x + w - 1, z + w - 1)
                return x, z
        return None

    # Jaula com um prisioneiro (missão de resgate improvisada).
    p = spot(3, rmin=6, rmax=9)
    if p:
        cage(s, p[0], p[1])
    # Pilhas de coisas roubadas, ossos e estandartes.
    for _ in range(9):
        p = spot()
        if not p:
            continue
        x, z = p
        kind = r.random()
        if kind < 0.3:
            s.set(x, 1, z, st("hay_block", axis="y"))
            s.soft(x, 2, z, r.choice([st("hay_block", axis="x"), "minecraft:pumpkin"]))
        elif kind < 0.5:
            s.set(x, 1, z, "minecraft:bone_block")
            s.soft(x, 2, z, skull("skeleton", r.randrange(16)))
        elif kind < 0.7:
            s.set(x, 1, z, st("barrel", facing="up", open="false"))
            s.set(x, 2, z, r.choice(["minecraft:melon", "minecraft:pumpkin", st("carved_pumpkin", facing="south")]))
        elif kind < 0.85:
            goblin_banner_pole(s, x, z, r.randrange(16))
        else:
            s.set(x, 1, z, st("composter", level=7))
    # Paliçada de cercas com duas entradas e caveiras em estacas.
    gates = [r.random() * math.tau]
    gates.append(gates[0] + math.pi + r.uniform(-0.6, 0.6))
    for x, z, d in disk(c, c, 18.4):
        if d < 17.4:
            continue
        a = math.atan2(z - c, x - c)
        if any(abs((a - g + math.pi) % math.tau - math.pi) < 0.17 for g in gates):
            continue
        if r.random() < 0.78 and (x, z) not in occ.cells:
            s.set(x, 1, z, "minecraft:spruce_fence")
            s.set(x, 2, z, "minecraft:spruce_fence")
            if r.random() < 0.08:
                s.set(x, 3, z, skull("skeleton", r.randrange(16)))
    for g in gates:
        for side in (-0.2, 0.2):
            x, z = round(c + math.cos(g + side) * 18), round(c + math.sin(g + side) * 18)
            for y in (1, 2, 3):
                s.set(x, y, z, log("minecraft:spruce_log"))
            s.set(x, 4, z, "minecraft:torch")
    # Goblins espalhados (os arqueiros perto da paliçada).
    for _ in range(6):
        p = spot(1, rmin=3, rmax=10)
        if p:
            s.mob(p[0] + 0.5, 1, p[1] + 0.5, f"{NS}:goblin", r.uniform(-180, 180))
    for _ in range(2):
        p = spot(1, rmin=14, rmax=16.5)
        if p:
            s.mob(p[0] + 0.5, 1, p[1] + 0.5, f"{NS}:goblin_archer", r.uniform(-180, 180))
    save(s, "goblin_village/village_" + variant)


# =================================================================================================
#  FORTALEZA GOBLIN (paliçada, torres, portão, pátio e salão do trono do Rei Goblin)
# =================================================================================================
def palisade_log(s):
    return s.pick([(log("minecraft:spruce_log"), 7), (log("minecraft:dark_oak_log"), 2),
                   (log("minecraft:stripped_spruce_log"), 1)])


def watch_tower(s, x0, z0, doors):
    """Torre 7x7 de troncos com plataforma (y10), parapeito e telhado piramidal; escada de mão por dentro."""
    for x in range(x0, x0 + 7):
        for z in range(z0, z0 + 7):
            edge = x in (x0, x0 + 6) or z in (z0, z0 + 6)
            s.set(x, 0, z, "minecraft:cobblestone")
            for y in range(1, 10):
                s.set(x, y, z, palisade_log(s) if edge else AIR)
            s.set(x, 10, z, "minecraft:spruce_planks")
            if edge:
                s.set(x, 11, z, "minecraft:spruce_fence")
    for x, z in ((x0, z0), (x0 + 6, z0), (x0, z0 + 6), (x0 + 6, z0 + 6)):
        for y in (11, 12, 13, 14):
            s.set(x, y, z, log("minecraft:spruce_log"))
    for k in range(4):
        for x in range(x0 - 1 + k, x0 + 8 - k):
            for z in range(z0 - 1 + k, z0 + 8 - k):
                if x in (x0 - 1 + k, x0 + 7 - k) or z in (z0 - 1 + k, z0 + 7 - k):
                    if x == x0 - 1 + k:
                        f = "east"
                    elif x == x0 + 7 - k:
                        f = "west"
                    elif z == z0 - 1 + k:
                        f = "south"
                    else:
                        f = "north"
                    s.set(x, 14 + k, z, stairs("dark_oak_stairs", f))
    s.set(x0 + 3, 17, z0 + 3, "minecraft:dark_oak_planks")
    s.set(x0 + 3, 18, z0 + 3, "minecraft:dark_oak_fence")
    s.set(x0 + 3, 19, z0 + 3, skull("skeleton", 0))
    s.air(x0 + 1, 11, z0 + 1, x0 + 5, 13, z0 + 5)
    s.set(x0 + 5, 11, z0 + 5, lantern())
    # Escada de mão no canto noroeste, com buraco no piso da plataforma.
    lx, lz = x0 + 1, z0 + 1
    for y in range(1, 11):
        s.set(lx, y, lz, ladder("south"))
    # Portas nas duas paredes voltadas para o pátio (doors = deslocamentos locais).
    for dx, dz in doors:
        s.set(x0 + dx, 1, z0 + dz, AIR)
        s.set(x0 + dx, 2, z0 + dz, AIR)
    s.set(x0 + 5, 9, z0 + 4, wall_torch("west"))


def build_goblin_fortress():
    s = B("goblin_fortress", 47, 21, 47)
    r = s.rnd
    # Pátio: chão batido (o beard_box aplaina o terreno; o ar explícito limpa o resto).
    for x in range(3, 44):
        for z in range(3, 44):
            s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 35), ("minecraft:dirt_path", 25),
                                   ("minecraft:packed_mud", 15), ("minecraft:gravel", 5), ("minecraft:dirt", 20)]))
    # Torres nos cantos.
    watch_tower(s, 0, 0, [(6, 5), (5, 6)])
    watch_tower(s, 40, 0, [(0, 5), (1, 6)])
    watch_tower(s, 0, 40, [(6, 1), (5, 0)])
    watch_tower(s, 40, 40, [(0, 1), (1, 0)])
    # Paliçada (troncos y1-6 + estacas de cerca) e passarela interna (y4).
    lines = [("x", 3, 7, 39, 1), ("x", 43, 7, 39, -1), ("z", 3, 7, 39, 1), ("z", 43, 7, 39, -1)]
    for axis, fixed, a0, a1, inward in lines:
        for a in range(a0, a1 + 1):
            x, z = (fixed, a) if axis == "x" else (a, fixed)
            for y in range(1, 7):
                s.set(x, y, z, palisade_log(s))
            if a % 2 == 0:
                s.set(x, 7, z, "minecraft:spruce_fence")
            for w in (1, 2):
                wx, wz = (fixed + inward * w, a) if axis == "x" else (a, fixed + inward * w)
                s.set(wx, 4, wz, "minecraft:spruce_planks" if w == 1 else slab("spruce_slab", "top"))
            if a % 4 == 1:
                px, pz = (fixed + inward * 2, a) if axis == "x" else (a, fixed + inward * 2)
                for y in (1, 2, 3):
                    s.set(px, y, pz, "minecraft:spruce_fence")
    # Escadas de mão para a passarela.
    for x, z, f, sx, sz in ((13, 6, "south", 13, 5), (33, 6, "south", 33, 5), (6, 23, "east", 5, 23),
                            (40, 23, "west", 41, 23), (13, 40, "north", 13, 41)):
        for y in range(1, 5):
            s.set(sx, y, sz, log("minecraft:spruce_log"))
            s.set(x, y, z, ladder(f))
    # Portão sul.
    for x in range(21, 26):
        for y in range(1, 9):
            if 22 <= x <= 24 and y <= 4:
                s.set(x, y, 43, AIR)
            elif x in (21, 25):
                s.set(x, y, 43, log("minecraft:dark_oak_log"))
            elif y in (5, 6):
                s.set(x, y, 43, log("minecraft:spruce_log", "x"))
    for x in (21, 25):
        s.set(x, 9, 43, skull("skeleton", 0))
        s.banner(x, 5, 44, st("green_wall_banner", facing="south"), GOBLIN_BANNER)
    for x in (22, 23, 24):
        s.set(x, 4, 43, "minecraft:spruce_fence")
        for z in (43, 44, 45):
            s.set(x, 0, z, "minecraft:dirt_path")
    for x in (19, 27):
        s.set(x, 0, 45, "minecraft:coarse_dirt")
        s.set(x, 1, 45, "minecraft:spruce_fence")
        s.set(x, 2, 45, "minecraft:spruce_fence")
        s.set(x, 3, 45, skull("skeleton", 0))

    # ---------------- Salão do trono (norte do pátio)
    X0, X1, Z0, Z1 = 15, 31, 6, 20
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            s.set(x, 0, z, s.pick([("minecraft:cobblestone", 3), ("minecraft:mossy_cobblestone", 2),
                                   ("minecraft:stone_bricks", 2), ("minecraft:cracked_stone_bricks", 1)]))
            edge = x in (X0, X1) or z in (Z0, Z1)
            for y in range(1, 8):
                if edge:
                    pil = (x in (15, 19, 23, 27, 31) and z in (Z0, Z1)) or (z in (6, 10, 14, 17, 20) and x in (X0, X1))
                    s.set(x, y, z, log("minecraft:spruce_log") if pil else
                          s.pick([("minecraft:cobblestone", 5), ("minecraft:mossy_cobblestone", 4),
                                  ("minecraft:andesite", 1)]))
                else:
                    s.set(x, y, z, AIR)
    for x in (22, 23, 24):
        for y in (1, 2, 3, 4):
            s.set(x, y, Z1, AIR)
    s.set(22, 5, Z1, stairs("spruce_stairs", "west", "top"))
    s.set(24, 5, Z1, stairs("spruce_stairs", "east", "top"))
    s.set(23, 5, Z1, slab("spruce_slab", "top"))
    for z in (8, 12, 16):
        for x in (X0, X1):
            s.set(x, 3, z, "minecraft:iron_bars")
            s.set(x, 4, z, "minecraft:iron_bars")
    for z in range(Z0, Z1 + 1):
        s.set(X0, 8, z, "minecraft:spruce_planks")
        s.set(X1, 8, z, "minecraft:spruce_planks")
    gable_z(s, Z0 - 1, Z1 + 1, X0 - 1, X1 + 1, 8, "dark_oak_stairs", "dark_oak_slab", "minecraft:spruce_planks",
            gz=(Z0, Z1))
    for x in range(X0 + 1, X1):
        for y in range(8, 17):
            for z in range(Z0 + 1, Z1):
                s.soft(x, y, z, AIR)
    s.banner(23, 11, Z1 + 1, st("green_wall_banner", facing="south"), GOBLIN_BANNER)
    s.set(21, 9, Z1 + 1, wall_skull("skeleton", "south"))
    s.set(25, 9, Z1 + 1, wall_skull("skeleton", "south"))
    # Pilares internos com tochas e tapete.
    for z in (9, 13, 17):
        for x in (18, 28):
            for y in range(1, 8):
                s.set(x, y, z, log("minecraft:spruce_log"))
            s.set(x + (1 if x == 18 else -1), 4, z, wall_torch("east" if x == 18 else "west"))
    for z in range(11, 20):
        for x in (22, 23, 24):
            s.set(x, 1, z, "minecraft:red_carpet" if x == 23 else "minecraft:green_carpet")
    # Estrado e trono.
    for x in range(19, 28):
        for z in (7, 8, 9):
            s.set(x, 1, z, "minecraft:mossy_stone_bricks")
        s.set(x, 1, 10, stairs("stone_brick_stairs", "north"))
    for x in range(21, 26):
        for z in (7, 8):
            s.set(x, 2, z, "minecraft:polished_andesite")
        s.set(x, 2, 9, stairs("polished_andesite_stairs", "north"))
    s.set(23, 3, 8, stairs("dark_oak_stairs", "north"))
    s.set(23, 3, 7, "minecraft:gold_block")
    s.set(23, 4, 7, "minecraft:gold_block")
    s.set(23, 5, 7, skull("wither_skeleton", 0))
    s.set(22, 3, 8, "minecraft:dark_oak_fence")
    s.set(24, 3, 8, "minecraft:dark_oak_fence")
    s.set(22, 3, 7, log("minecraft:stripped_dark_oak_log"))
    s.set(24, 3, 7, log("minecraft:stripped_dark_oak_log"))
    s.chest(20, 2, 7, loot("goblin_fortress_throne"), "south")
    s.chest(26, 2, 7, loot("goblin_fortress_throne"), "south")
    s.set(19, 2, 8, "minecraft:bone_block")
    s.set(27, 2, 8, "minecraft:bone_block")
    s.set(19, 3, 8, skull("skeleton", 0))
    s.set(27, 3, 8, skull("skeleton", 0))
    for x in (19, 27):
        s.banner(x, 6, 7, st("green_wall_banner", facing="south"), GOBLIN_BANNER)
    for x in (17, 29):
        s.set(x, 1, 8, campfire(facing="south"))
    s.mob(23.5, 1, 13.5, f"{NS}:goblin_king", YAW["south"])
    s.mob(20.5, 1, 18.5, f"{NS}:hobgoblin", YAW["south"])
    s.mob(26.5, 1, 18.5, f"{NS}:hobgoblin", YAW["south"])

    # ---------------- Pátio: alojamentos, fogueira, jaulas, canil, treino, caixotes
    for bx in (6, 34):
        x0, x1 = bx, bx + 6
        for x in range(x0, x1 + 1):
            for z in range(25, 38):
                edge = x in (x0, x1) or z in (25, 37)
                s.set(x, 0, z, "minecraft:spruce_planks" if not edge else "minecraft:cobblestone")
                for y in (1, 2, 3):
                    if edge:
                        corner = x in (x0, x1) and z in (25, 37)
                        s.set(x, y, z, log("minecraft:spruce_log") if corner or z % 4 == 1 else
                              s.pick([("minecraft:spruce_planks", 4), ("minecraft:mud_bricks", 2),
                                      ("minecraft:mossy_cobblestone", 1)]))
                    else:
                        s.set(x, y, z, AIR)
        gable_z(s, 24, 38, x0, x1, 4, "spruce_stairs", "spruce_slab", "minecraft:spruce_planks", gz=(25, 37))
        for x in range(x0 + 1, x1):
            for z in range(26, 37):
                for y in (4, 5, 6):
                    s.soft(x, y, z, AIR)
        door_x = x1 if bx == 6 else x0
        for z in (30, 31):
            s.set(door_x, 1, z, AIR)
            s.set(door_x, 2, z, AIR)
        bed_x = x0 + 1 if bx == 6 else x1 - 1
        for z in (27, 29, 33, 35):
            s.set(bed_x, 1, z, st("hay_block", axis="z"))
            s.set(bed_x, 2, z, "minecraft:brown_carpet")
        other_x = x1 - 1 if bx == 6 else x0 + 1
        s.barrel(other_x, 1, 27, loot("goblin_fortress"))
        s.barrel(other_x, 1, 35)
        s.set(other_x, 1, 34, "minecraft:crafting_table")
        s.set(x0 + 3, 6, 31, lantern(True))
    spit_fire(s, 23, 30, big=True)
    s.set(26, 1, 30, st("water_cauldron", level=3))
    for x, z, ax in ((20, 28, "z"), (20, 32, "z"), (26, 33, "x"), (23, 33, "x")):
        s.set(x, 1, z, log("minecraft:spruce_log", ax))
    cage(s, 8, 10)
    cage(s, 8, 15, prisoner=False)
    s.set(9, 1, 16, "minecraft:bone_block")
    # Canil do lobo atroz (dá para domar e levar embora).
    for x in range(34, 41):
        for z in range(8, 17):
            if x in (34, 40) or z in (8, 16):
                s.set(x, 1, z, "minecraft:spruce_fence")
            else:
                s.set(x, 0, z, "minecraft:coarse_dirt")
    s.set(37, 1, 16, fence_gate("spruce_fence_gate", "south"))
    s.set(36, 1, 10, "minecraft:bone_block")
    s.set(38, 1, 14, st("water_cauldron", level=2))
    s.set(35, 1, 9, st("hay_block", axis="y"))
    s.mob(37.5, 1, 12.5, f"{NS}:dire_wolf", YAW["south"])
    # Campo de treino e caixotes.
    for x in (9, 11):
        s.set(x, 1, 40, st("hay_block", axis="y"))
        s.set(x, 2, 40, st("target", power=0))
    s.armor_stand(30.5, 1, 39.5, YAW["north"], head="leather_helmet", chest="leather_chestplate")
    for x, z, y in ((33, 38, 1), (34, 38, 1), (33, 39, 1), (33, 38, 2)):
        s.barrel(x, y, z, loot("goblin_fortress") if (x, z, y) == (34, 38, 1) else None)
    goblin_banner_pole(s, 20, 23, 0)
    goblin_banner_pole(s, 26, 23, 0)
    s.air(3, 1, 3, 43, 17, 43)
    # Ocupantes.
    for x, z in ((12, 22), (30, 23), (18, 36), (28, 37), (23, 26), (10, 33), (16, 28), (30, 30)):
        s.mob(x + 0.5, 1, z + 0.5, f"{NS}:goblin", r.uniform(-180, 180))
    for x, z in ((3, 3), (43, 3), (3, 43), (43, 43)):
        s.mob(x + 0.5, 11, z + 0.5, f"{NS}:goblin_archer", YAW["south"] if z < 20 else YAW["north"])
    s.mob(25.5, 1, 32.5, f"{NS}:goblin_shaman", YAW["west"])
    s.mob(23.5, 1, 40.5, f"{NS}:goblin_wolf_rider", YAW["south"])
    save(s, "goblin_fortress/fortress")


# =================================================================================================
#  ACAMPAMENTO ORC (tendas de couro, totens, fogueira de guerra; 2 variantes)
# =================================================================================================
ORC_BANNER = [("stripe_middle", "black"), ("skull", "white"), ("border", "black")]


def tipi(s, cx, cz, color, door):
    w = mc(f"{color}_wool")
    for x, z, d in disk(cx, cz, 2.6):
        s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 2), ("minecraft:podzol", 1)]))
    for y, rr in ((1, 2.6), (2, 2.1), (3, 1.5), (4, 0.9)):
        for x, z, d in disk(cx, cz, rr + 0.4):
            if rr < 1.0:
                s.set(x, y, z, w)
            elif d > rr - 1.0:
                s.set(x, y, z, s.pick([(w, 6), ("minecraft:brown_wool", 2), ("minecraft:black_wool", 1)]))
            else:
                s.set(x, y, z, AIR)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)):
        s.set(cx + dx, 5, cz + dz, "minecraft:spruce_fence")
    s.set(cx, 6, cz, "minecraft:spruce_fence")
    dx, dz = DIRV[door]
    for y in (1, 2):
        s.set(cx + dx * 2, y, cz + dz * 2, AIR)
    s.banner(cx + dx * 3 + dz, 1, cz + dz * 3 + dx, st("red_banner", rotation=_rot(door)), ORC_BANNER)
    s.set(cx, 1, cz, "minecraft:brown_carpet")


def orc_totem(s, x, z, facing):
    blocks = [log("minecraft:stripped_dark_oak_log"), "minecraft:bone_block", st("carved_pumpkin", facing=facing),
              log("minecraft:stripped_dark_oak_log"), "minecraft:bone_block"]
    s.set(x, 0, z, "minecraft:cobblestone")
    for y, b in enumerate(blocks, start=1):
        s.set(x, y, z, b)
    s.set(x, 6, z, skull("skeleton", _rot(facing)))
    for d in ("east", "west"):
        s.banner(x + DIRV[d][0], 4, z, st("red_wall_banner", facing=d), ORC_BANNER)


def orc_camp(variant):
    s = B("orc_camp_" + variant, 37, 14, 37)
    r, c = s.rnd, 18
    for x, z, d in disk(c, c, 17.5):
        g = s.pick([("minecraft:coarse_dirt", 30), ("minecraft:dirt_path", 15), ("minecraft:packed_mud", 15),
                    ("minecraft:gravel", 8), ("minecraft:rooted_dirt", 7), (None, 35)])
        if g:
            s.set(x, 0, z, g)
    # Tenda do Lorde Orc (grande, em A, ao norte).
    X0, X1, Z0, Z1 = 13, 23, 3, 13
    for z in range(Z0, Z1 + 1):
        for x in range(X0, X1 + 1):
            s.set(x, 0, z, s.pick([("minecraft:spruce_planks", 3), ("minecraft:coarse_dirt", 1)]))
        for k in range(6):
            for x in (X0 + k, X1 - k):
                s.set(x, 1 + k, z, s.pick([("minecraft:brown_wool", 6), ("minecraft:black_wool", 2),
                                          ("minecraft:brown_terracotta", 1)]))
            for x in range(X0 + k + 1, X1 - k):
                y = 1 + k
                if z in (Z0, Z1):
                    s.set(x, y, z, s.pick([("minecraft:brown_wool", 6), ("minecraft:black_wool", 2)]))
                else:
                    s.set(x, y, z, AIR)
    for z in range(Z0 - 1, Z1 + 2):
        s.set(18, 7, z, log("minecraft:spruce_log", "z"))
    for x in (17, 18, 19):
        for y in (1, 2, 3):
            s.set(x, y, Z1, AIR)
    for x in (16, 20):
        s.banner(x, 1, Z1 + 1, st("red_banner", rotation=0), ORC_BANNER)
    for z in range(Z0 + 1, Z1):
        for x in range(X0 + 1, X1):
            s.set(x, 1, z, s.pick([("minecraft:brown_carpet", 4), ("minecraft:black_carpet", 1), (AIR, 2)]))
    # Trono de ossos.
    s.set(18, 1, 5, stairs("dark_oak_stairs", "north"))
    s.set(18, 1, 4, "minecraft:bone_block")
    s.set(18, 2, 4, "minecraft:bone_block")
    s.set(18, 3, 4, skull("skeleton", 0))
    s.set(17, 1, 5, "minecraft:bone_block")
    s.set(19, 1, 5, "minecraft:bone_block")
    s.chest(15, 1, 5, loot("orc_camp"), "east")
    s.barrel(21, 1, 4)
    for x in (15, 21):
        s.set(x, 1, 10, "minecraft:spruce_fence")
        s.set(x, 2, 10, "minecraft:torch")
    s.armor_stand(20.5, 1, 7.5, YAW["west"], head="iron_helmet", hand="iron_axe")
    s.armor_stand(16.5, 1, 7.5, YAW["east"], chest="chainmail_chestplate", hand="iron_sword")
    s.mob(18.5, 1, 8.5, f"{NS}:orc_lord", YAW["south"])
    occ = Occ()
    occ.take(X0 - 1, Z0 - 1, X1 + 1, Z1 + 2)
    # Fogueira de guerra com caldeirão.
    fx, fz = c, c + 3
    spit_fire(s, fx, fz, big=True)
    s.set(fx + 3, 0, fz, "minecraft:cobblestone")
    s.set(fx + 3, 1, fz, st("water_cauldron", level=3))
    occ.take(fx - 4, fz - 4, fx + 4, fz + 4)
    for ang in (20, 110, 200, 290):
        a = math.radians(ang)
        lx, lz = round(fx + math.cos(a) * 4.3), round(fz + math.sin(a) * 4.3)
        s.soft(lx, 1, lz, log("minecraft:stripped_spruce_log", "x" if abs(math.cos(a)) < 0.7 else "z"))
    # Tipis ao redor.
    angs = [math.radians(a + r.uniform(-12, 12)) for a in (150, 200, 250, 290, 340, 30)]
    r.shuffle(angs)
    placed = 0
    for a in angs:
        if placed >= 4:
            break
        d = r.uniform(11.5, 13.5)
        tx, tz = round(c + math.cos(a) * d), round(c + math.sin(a) * d)
        if not occ.free(tx - 3, tz - 3, tx + 3, tz + 3):
            continue
        door = _face_toward(c - tx, c - tz)
        tipi(s, tx, tz, r.choice(["brown", "brown", "black", "white"]), door)
        occ.take(tx - 4, tz - 4, tx + 4, tz + 4)
        dx, dz = DIRV[door]
        if placed == 0:
            s.chest(tx - dx, 1, tz - dz, loot("orc_camp"), door)
        else:
            s.set(tx - dx, 1, tz - dz, st("hay_block", axis="y"))
        placed += 1
    # Totens.
    for tx, tz in ((c - 7, c + 1), (c + 7, c + 1), (c, c + 12)):
        if occ.free(tx - 1, tz, tx + 1, tz, 0):
            orc_totem(s, tx, tz, "south")
            occ.take(tx - 1, tz - 1, tx + 1, tz + 1)

    def spot(rmin, rmax):
        for _ in range(200):
            a = r.random() * math.tau
            d = r.uniform(rmin, rmax)
            x, z = round(c + math.cos(a) * d), round(c + math.sin(a) * d)
            if occ.free(x, z, x, z):
                occ.take(x, z, x, z)
                return x, z
        return None

    # Racks de armas e pilhas de ossos/barris/feno.
    for _ in range(3):
        p = spot(6, 15)
        if p:
            s.armor_stand(p[0] + 0.5, 1, p[1] + 0.5, r.uniform(-180, 180), hand=r.choice(["iron_axe", "stone_axe"]),
                          head=r.choice([None, "leather_helmet"]))
    for _ in range(6):
        p = spot(5, 16)
        if p:
            k = r.random()
            if k < 0.4:
                s.set(p[0], 1, p[1], "minecraft:bone_block")
                s.soft(p[0], 2, p[1], skull("skeleton", r.randrange(16)))
            elif k < 0.7:
                s.set(p[0], 1, p[1], st("barrel", facing="up", open="false"))
            else:
                s.set(p[0], 1, p[1], st("hay_block", axis="y"))
    # Barricada de estacas com duas entradas.
    gates = [math.radians(90 + r.uniform(-20, 20)), math.radians(270 + r.uniform(-30, 30))]
    for x, z, d in disk(c, c, 17.6):
        if d < 16.6:
            continue
        a = math.atan2(z - c, x - c)
        if any(abs((a - g + math.pi) % math.tau - math.pi) < 0.2 for g in gates):
            continue
        if r.random() < 0.55:
            s.set(x, 1, z, log("minecraft:spruce_log", "x" if abs(math.sin(a)) > 0.7 else "z"))
            s.set(x, 2, z, "minecraft:dark_oak_fence")
    for g in gates:
        for side in (-0.24, 0.24):
            x, z = round(c + math.cos(g + side) * 17), round(c + math.sin(g + side) * 17)
            for y in (1, 2, 3):
                s.set(x, y, z, "minecraft:dark_oak_fence")
            s.set(x, 4, z, skull("skeleton", r.randrange(16)))
    # Orcs (os berserkers primeiro, perto da fogueira de guerra).
    for _ in range(2):
        p = spot(5, 15)
        if p:
            s.mob(p[0] + 0.5, 1, p[1] + 0.5, f"{NS}:orc_berserker", r.uniform(-180, 180))
    for _ in range(4):
        p = spot(4, 15)
        if p:
            s.mob(p[0] + 0.5, 1, p[1] + 0.5, f"{NS}:orc", r.uniform(-180, 180))
    for x, z, d in disk(c, c, 17.6):
        s.air(x, 1, z, x, 9, z)
    save(s, "orc_camp/camp_" + variant)


# =================================================================================================
#  COVIL DE KOBOLDS (jigsaw vertical: entrada na superfície → poço → mina subterrânea)
# =================================================================================================
KD = f"{NS}:kobold_den/"
J_KD_ENT, J_KD_TOP, J_KD_BOT, J_KD_MINE = (f"{NS}:kobold_den_entrance", f"{NS}:kobold_den_shaft_top",
                                            f"{NS}:kobold_den_shaft_bottom", f"{NS}:kobold_den_mine_top")
KD_FLOOR = 6          # 6 camadas de fundação abaixo do piso (start_height = -6): não flutua em encostas
ROCK_SHELL = [("minecraft:stone", 52), ("minecraft:andesite", 10), ("minecraft:cobblestone", 7),
              ("minecraft:tuff", 4), ("minecraft:diorite", 3), ("minecraft:coal_ore", 8), ("minecraft:iron_ore", 6),
              ("minecraft:copper_ore", 5), ("minecraft:gold_ore", 1.5), ("minecraft:redstone_ore[lit=false]", 1.5),
              ("minecraft:lapis_ore", 1), ("minecraft:diamond_ore", 0.3)]
RICH_SHELL = [("minecraft:stone", 30), ("minecraft:iron_ore", 18), ("minecraft:coal_ore", 15),
              ("minecraft:copper_ore", 15), ("minecraft:gold_ore", 5), ("minecraft:redstone_ore[lit=false]", 5),
              ("minecraft:lapis_ore", 4), ("minecraft:diamond_ore", 1.2)]
GEODE_SHELL = [("minecraft:amethyst_block", 5), ("minecraft:calcite", 3), ("minecraft:smooth_basalt", 2)]
GLOW_LICHEN = st("glow_lichen", down="true", up="false", north="false", south="false", east="false", west="false",
                 waterlogged="false")


def kobold_entrance():
    s = B("kobold_den_entrance", 11, KD_FLOOR + 7, 11)
    F = KD_FLOOR
    for x in range(1, 10):
        for z in range(1, 10):
            for y in range(0, F):
                s.set(x, y, z, s.pick([("minecraft:cobblestone", 4), ("minecraft:stone", 2), ("minecraft:andesite", 1)]))
            s.set(x, F, z, s.pick([("minecraft:spruce_planks", 4), ("minecraft:cobblestone", 2),
                                   ("minecraft:coarse_dirt", 2), ("minecraft:gravel", 1)]))
    # Boca do poço (3x3) com escada de mão no lado norte.
    for x in (4, 5, 6):
        for z in (4, 5, 6):
            for y in range(0, F + 1):
                s.set(x, y, z, AIR)
    for y in range(0, F + 1):
        s.set(5, y, 4, ladder("south"))
    s.jig(5, 0, 5, "down_north", J_KD_ENT, J_KD_TOP, KD + "shafts", joint="aligned")
    # Cavalete de madeira com roldana.
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        for y in range(F + 1, F + 6):
            s.set(x, y, z, log("minecraft:spruce_log"))
    top = F + 6
    for x in range(3, 8):
        s.set(x, top, 3, log("minecraft:spruce_log", "x"))
        s.set(x, top, 7, log("minecraft:spruce_log", "x"))
        if 4 <= x <= 6:
            s.set(x, top, 5, log("minecraft:spruce_log", "x"))
    for z in (4, 5, 6):
        s.set(3, top, z, log("minecraft:spruce_log", "z"))
        s.set(7, top, z, log("minecraft:spruce_log", "z"))
    for y in range(F + 3, top):
        s.set(5, y, 5, st("chain", axis="y", waterlogged="false"))
    s.set(5, F + 2, 5, lantern(True))
    s.set(3, top - 1, 5, lantern(True))
    s.set(7, top - 1, 5, lantern(True))
    # Parapeito nos lados leste, oeste e sul (o norte fica livre para a escada).
    for x in (4, 5, 6):
        s.set(x, F + 1, 7, "minecraft:spruce_fence")
    for z in (4, 5, 6):
        s.set(3, F + 1, z, "minecraft:spruce_fence")
        s.set(7, F + 1, z, "minecraft:spruce_fence")
    # Trilho com vagoneta, pilhas de minério e barris.
    for z in (8, 9):
        s.set(5, F + 1, z, st("rail", shape="north_south", waterlogged="false"))
    s.entity(5.5, F + 1, 9.5, "minecraft:minecart")
    for x, z, y, b in ((8, 1, 1, "minecraft:iron_ore"), (9, 1, 1, "minecraft:coal_ore"), (8, 2, 1, "minecraft:copper_ore"),
                       (9, 2, 1, "minecraft:cobblestone"), (9, 1, 2, "minecraft:coal_ore"), (1, 8, 1, "minecraft:gravel")):
        s.set(x, F + y, z, b)
    s.barrel(1, F + 1, 1, loot("kobold_den"))
    s.barrel(1, F + 1, 2)
    s.barrel(2, F + 1, 1)
    s.set(1, F + 2, 1, "minecraft:torch")
    s.set(1, F + 1, 9, "minecraft:crafting_table")
    s.air(1, F + 1, 1, 9, F + 6, 9)
    s.mob(2.5, F + 1, 6.5, f"{NS}:kobold", YAW["east"])
    save(s, "kobold_den/entrance")


def kobold_shaft():
    H = 12
    s = B("kobold_den_shaft", 5, H, 5)
    for y in range(H):
        for x in range(5):
            for z in range(5):
                if x in (0, 4) or z in (0, 4):
                    corner = x in (0, 4) and z in (0, 4)
                    if corner:
                        s.set(x, y, z, log("minecraft:spruce_log"))
                    elif y % 4 == 0:
                        s.set(x, y, z, "minecraft:spruce_planks")
                    else:
                        s.set(x, y, z, s.pick(ROCK_SHELL))
                else:
                    s.set(x, y, z, AIR)
        s.set(2, y, 1, ladder("south"))
    s.set(3, 6, 2, wall_torch("west"))
    s.jig(2, H - 1, 2, "up_north", J_KD_TOP, joint="aligned")
    s.jig(2, 0, 2, "down_north", J_KD_BOT, J_KD_MINE, KD + "mines", joint="aligned")
    save(s, "kobold_den/shaft")


def kobold_mine():
    s = B("kobold_den_mine", 37, 9, 37)
    r = s.rnd
    air = set()
    geode = set()

    def carve(x0, y0, z0, x1, y1, z1, tag=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    s.set(x, y, z, AIR)
                    air.add((x, y, z))
                    if tag is not None:
                        tag.add((x, y, z))

    carve(11, 1, 12, 25, 6, 25)       # câmara principal
    carve(17, 7, 12, 19, 8, 14)       # boca do poço no teto
    carve(26, 1, 17, 34, 3, 19)       # túnel leste
    carve(2, 1, 17, 10, 3, 19)        # túnel oeste
    carve(17, 1, 26, 19, 3, 31)       # túnel sul
    carve(4, 1, 3, 6, 3, 16)          # ramal norte (a partir do oeste)
    carve(29, 1, 20, 31, 3, 32)       # ramal sul (a partir do leste)
    for x, z, d in disk(18, 32.4, 3.4):
        carve(x, 1, z, x, 4 if d < 2.4 else 3, z, geode)
    # Pisos.
    for (x, y, z) in list(air):
        if y == 1 and s.get(x, 0, z) is None:
            if (x, y, z) in geode:
                s.set(x, 0, z, s.pick([("minecraft:calcite", 3), ("minecraft:amethyst_block", 2)]))
            else:
                s.set(x, 0, z, s.pick([("minecraft:stone", 4), ("minecraft:cobblestone", 3), ("minecraft:gravel", 2),
                                       ("minecraft:andesite", 1), ("minecraft:coarse_dirt", 1)]))
    # Escada de mão do poço até o chão + jigsaw de encaixe.
    for y in range(1, 9):
        s.set(18, y, 12, ladder("south"))
    s.jig(18, 8, 13, "up_north", J_KD_MINE, joint="aligned")
    # Câmara: pilares, vigas, lanternas, fogueira, ninhos, depósito e "tesouro" de kobold.
    for x, z in ((14, 15), (22, 15), (14, 22), (22, 22)):
        for y in range(1, 7):
            s.set(x, y, z, log("minecraft:spruce_log"))
    for x in range(15, 22):
        s.set(x, 6, 15, log("minecraft:spruce_log", "x"))
        s.set(x, 6, 22, log("minecraft:spruce_log", "x"))
    for x, z in ((16, 15), (20, 15), (16, 22), (20, 22)):
        s.set(x, 5, z, lantern(True))
    for x in (17, 18, 19):
        for z in (18, 19, 20):
            s.set(x, 0, z, "minecraft:cobblestone")
    s.set(18, 1, 19, campfire())
    s.set(16, 1, 19, log("minecraft:stripped_spruce_log", "z"))
    s.set(20, 1, 19, log("minecraft:stripped_spruce_log", "z"))
    for z in (14, 17, 20, 23):
        s.set(12, 1, z, st("hay_block", axis="z"))
        s.set(12, 2, z, r.choice(["minecraft:brown_carpet", "minecraft:orange_carpet"]))
    for z in (13, 14, 15):
        s.barrel(24, 1, z)
    s.barrel(24, 2, 14)
    s.barrel(24, 1, 16, loot("kobold_den"), "west")
    s.set(24, 1, 18, "minecraft:crafting_table")
    s.set(24, 1, 19, st("furnace", facing="west", lit="false"))
    s.set(24, 1, 21, st("anvil", facing="north"))
    s.set(17, 1, 24, "minecraft:raw_copper_block")
    s.set(18, 1, 24, "minecraft:gold_ore")
    s.set(19, 1, 24, "minecraft:exposed_copper")
    s.set(18, 2, 24, st("amethyst_cluster", facing="up", waterlogged="false"))
    # Escoras de mina nos túneis (cercas + viga de tábuas), trilhos e tochas.
    supports = [("x", 28, 17, 19), ("x", 32, 17, 19), ("x", 4, 17, 19), ("x", 8, 17, 19),
                ("z", 28, 17, 19), ("z", 6, 4, 6), ("z", 11, 4, 6), ("z", 24, 29, 31), ("z", 29, 29, 31)]
    for axis, at, a, b in supports:
        for w in range(a, b + 1):
            x, z = (at, w) if axis == "x" else (w, at)
            s.set(x, 3, z, "minecraft:spruce_planks")
        for w in (a, b):
            x, z = (at, w) if axis == "x" else (w, at)
            s.set(x, 1, z, "minecraft:spruce_fence")
            s.set(x, 2, z, "minecraft:spruce_fence")
    for x in range(26, 34):
        s.set(x, 1, 18, st("rail", shape="east_west", waterlogged="false"))
    for x in range(3, 11):
        s.set(x, 1, 18, st("rail", shape="east_west", waterlogged="false"))
    for z in range(26, 31):
        s.set(18, 1, z, st("rail", shape="north_south", waterlogged="false"))
    for z in range(4, 16):
        s.set(5, 1, z, st("rail", shape="north_south", waterlogged="false"))
    for z in range(21, 32):
        s.set(30, 1, z, st("rail", shape="north_south", waterlogged="false"))
    for x, z, f in ((30, 17, "south"), (6, 19, "north"), (17, 27, "east"), (4, 10, "east"), (31, 25, "west"),
                    (26, 19, "north"), (10, 17, "south")):
        s.set(x, 2, z, wall_torch(f))
    s.entity(31.5, 1, 18.5, "minecraft:chest_minecart", {"LootTable": loot("kobold_den")})
    s.entity(18.5, 1, 29.5, "minecraft:minecart")
    # Gruta de ametista no fim do túnel sul, com o baú do tesouro dos kobolds.
    for (x, y, z) in geode:
        if y == 1 and math.hypot(x - 18, z - 32.4) > 2.2 and r.random() < 0.45:
            s.set(x, 1, z, st(r.choice(["amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud"]),
                              facing="up", waterlogged="false"))
    s.chest(18, 1, 34, loot("kobold_den"), "north")
    # Líquen brilhante no chão dos túneis.
    for (x, y, z) in list(air):
        if y == 1 and s.get(x, 1, z) == AIR and (x, y, z) not in geode and r.random() < 0.06 \
                and not (11 <= x <= 25 and 12 <= z <= 25):
            s.set(x, 1, z, GLOW_LICHEN)
    # Casca de rocha com minérios expostos (as frentes de escavação são mais ricas).
    for (x, y, z) in list(air):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    nx, ny, nz = x + dx, y + dy, z + dz
                    if not s.inside(nx, ny, nz) or s.get(nx, ny, nz) is not None:
                        continue
                    if (x, y, z) in geode:
                        s.set(nx, ny, nz, s.pick(GEODE_SHELL))
                    elif nx >= 35 or nx <= 1 or nz <= 2 or (nz >= 33 and nx >= 28):
                        s.set(nx, ny, nz, s.pick(RICH_SHELL))
                    else:
                        s.set(nx, ny, nz, s.pick(ROCK_SHELL))
    for x, z in ((15, 18), (21, 21), (18, 16), (27, 18), (5, 8), (18, 28), (30, 26)):
        s.mob(x + 0.5, 1, z + 0.5, f"{NS}:kobold", r.uniform(-180, 180))
    save(s, "kobold_den/mine")


def build_kobold_den():
    kobold_entrance()
    kobold_shaft()
    kobold_mine()


# =================================================================================================
#  LABIRINTO DO MINOTAURO (labirinto procedural 10x10 células, arena central; 3 variantes)
# =================================================================================================
def labyrinth(variant, seed):
    s = B("minotaur_labyrinth_" + variant, 41, 10, 41)
    rnd = random.Random(seed)
    s.rnd = rnd
    N, CS, SIZE, WALL_H = 10, 4, 41, 7
    arena = {(i, j) for i in range(3, 7) for j in range(3, 7)}

    def lo(i):
        return 1 + CS * i

    def nbrs(c):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + dx, c[1] + dz)
            if 0 <= n[0] < N and 0 <= n[1] < N:
                yield n

    passages = set()
    entry = (rnd.randrange(N), N - 1)
    exit_ = (rnd.randrange(N), 0)
    stack, seen = [entry], {entry}
    while stack:
        cur = stack[-1]
        opts = [n for n in nbrs(cur) if n not in seen and n not in arena]
        if opts:
            n = rnd.choice(opts)
            passages.add(frozenset((cur, n)))
            seen.add(n)
            stack.append(n)
        else:
            stack.pop()
    ring = [(i, j) for i in range(N) for j in range(N) if (i, j) not in arena]
    added = 0
    while added < 7:
        c = rnd.choice(ring)
        n = rnd.choice(list(nbrs(c)))
        if n in arena or frozenset((c, n)) in passages:
            continue
        passages.add(frozenset((c, n)))
        added += 1
    # Duas entradas para a arena (sul e norte).
    ai = rnd.randrange(3, 7)
    passages.add(frozenset(((ai, 6), (ai, 7))))
    bi = rnd.randrange(3, 7)
    passages.add(frozenset(((bi, 3), (bi, 2))))

    carved = set()

    def carve_col(x, z):
        carved.add((x, z))

    for i in range(N):
        for j in range(N):
            for x in range(lo(i), lo(i) + 3):
                for z in range(lo(j), lo(j) + 3):
                    carve_col(x, z)
    for p in passages:
        (a, b) = sorted(p)
        if a[0] != b[0]:
            wx = CS * (max(a[0], b[0]))
            for z in range(lo(a[1]), lo(a[1]) + 3):
                carve_col(wx, z)
        else:
            wz = CS * (max(a[1], b[1]))
            for x in range(lo(a[0]), lo(a[0]) + 3):
                carve_col(x, wz)
    for x in range(13, 28):
        for z in range(13, 28):
            carve_col(x, z)
    for x in range(lo(entry[0]), lo(entry[0]) + 3):
        carve_col(x, SIZE - 1)
    for x in range(lo(exit_[0]), lo(exit_[0]) + 3):
        carve_col(x, 0)

    floor = [("minecraft:stone_bricks", 40), ("minecraft:cracked_stone_bricks", 15), ("minecraft:gravel", 12),
             ("minecraft:cobblestone", 15), ("minecraft:mossy_cobblestone", 10), ("minecraft:coarse_dirt", 5)]
    for x in range(SIZE):
        for z in range(SIZE):
            if (x, z) in carved:
                s.set(x, 0, z, s.pick(floor))
                for y in range(1, 10):
                    s.set(x, y, z, AIR)
            else:
                s.set(x, 0, z, "minecraft:stone_bricks")
                for y in range(1, WALL_H + 1):
                    s.set(x, y, z, "minecraft:stone_bricks")
                pillar = x % CS == 0 and z % CS == 0
                s.set(x, WALL_H + 1, z, "minecraft:chiseled_stone_bricks" if pillar else slab("stone_brick_slab"))
    # Portais de entrada/saída com pilares mais altos e caveiras.
    for (ci, zz, f) in ((entry[0], SIZE - 1, 0), (exit_[0], 0, 8)):
        for px in (CS * ci, CS * (ci + 1)):
            for y in range(1, 9):
                s.set(px, y, zz, "minecraft:chiseled_stone_bricks" if y in (4, 8) else "minecraft:stone_bricks")
            s.set(px, 9, zz, skull("skeleton", f))

    # Arena central.
    for x in range(13, 28):
        for z in range(13, 28):
            d = math.hypot(x - 20, z - 20)
            if d < 1.5:
                b = "minecraft:chiseled_stone_bricks"
            elif 3.5 <= d < 4.5 or 6.5 <= d < 7.2:
                b = "minecraft:red_nether_bricks"
            else:
                b = s.pick([("minecraft:polished_andesite", 6), ("minecraft:stone_bricks", 3),
                            ("minecraft:cracked_stone_bricks", 1)])
            s.set(x, 0, z, b)
    for x, z in ((15, 16), (25, 16), (15, 25), (25, 25)):
        for y in range(1, 6):
            s.set(x, y, z, "minecraft:chiseled_stone_bricks" if y == 3 else "minecraft:stone_bricks")
        s.set(x, 6, z, campfire(soul=True))
    for x in range(17, 24):
        s.set(x, 1, 13, "minecraft:polished_andesite")
        s.set(x, 1, 14, "minecraft:polished_andesite")
        s.set(x, 1, 15, stairs("stone_brick_stairs", "north"))
    s.chest(20, 2, 13, loot("minotaur_labyrinth"), "south")
    for x in (18, 22):
        s.set(x, 2, 13, "minecraft:bone_block")
        s.set(x, 3, 13, skull("skeleton", 0))
    s.set(19, 2, 14, candle(3, "red"))
    s.set(21, 2, 14, candle(2, "red"))
    for _ in range(10):
        x, z = rnd.randrange(13, 28), rnd.randrange(16, 28)
        if s.get(x, 1, z) == AIR and math.hypot(x - 20, z - 20) > 2.5:
            s.set(x, 1, z, rnd.choice(["minecraft:bone_block", skull("skeleton", rnd.randrange(16)),
                                       "minecraft:cobweb", "minecraft:moss_carpet"]))
    s.mob(20.5, 1, 20.5, f"{NS}:minotaur", YAW["south"])

    # Becos sem saída: tesouros pequenos, restos de aventureiros e um mímico.
    def links(c):
        return sum(1 for p in passages if c in p)

    dead = [c for c in ring if links(c) == 1 and c not in (entry, exit_)]
    rnd.shuffle(dead)
    dead.sort(key=lambda c: -abs(c[1] - entry[1]) - abs(c[0] - entry[0]))
    for k, c in enumerate(dead):
        cx, cz = lo(c[0]) + 1, lo(c[1]) + 1
        if k == 0:
            s.mob(cx + 0.5, 1, cz + 0.5, f"{NS}:mimic", YAW["south"])
        elif k <= 4:
            s.chest(cx, 1, cz, loot("minotaur_labyrinth_cache"), rnd.choice(["north", "south", "east", "west"]))
            s.set(cx + 1, 1, cz, lantern(soul=True))
        elif k <= 10:
            s.set(cx, 1, cz, "minecraft:bone_block")
            s.set(cx, 2, cz, skull("skeleton", rnd.randrange(16)))
            s.set(cx - 1, 1, cz + 1, "minecraft:cobweb")
        else:
            s.set(cx, 1, cz, lantern(soul=True))
    # Tochas de alma nas paredes e teias pelos cantos.
    for (i, j) in ring:
        if rnd.random() < 0.35:
            north_open = frozenset(((i, j), (i, j - 1))) in passages or j == 0
            if not north_open:
                x, z = lo(i) + 1, lo(j)
                if s.get(x, 3, z) == AIR and s.get(x, 3, z - 1) not in (None, AIR):
                    s.set(x, 3, z, wall_torch("south", soul=True))
        if rnd.random() < 0.25:
            x, z = lo(i) + rnd.choice((0, 2)), lo(j) + rnd.choice((0, 2))
            if s.get(x, 1, z) == AIR:
                if rnd.random() < 0.5:
                    s.set(x, rnd.choice((1, 6)), z, "minecraft:cobweb")
                else:
                    s.set(x, 1, z, "minecraft:moss_carpet")
    save(s, "minotaur_labyrinth/maze_" + variant)


def build_labyrinth():
    for v, seed in (("a", 1337), ("b", 4242), ("c", 9001)):
        labyrinth(v, seed)


# =================================================================================================
#  NINHO DE HARPIAS (pico rochoso com ninho gigante e dois ninhos menores)
# =================================================================================================
ROCK = [("minecraft:stone", 5), ("minecraft:andesite", 2), ("minecraft:calcite", 2), ("minecraft:tuff", 1),
        ("minecraft:cobblestone", 1)]
NEST = [(log("minecraft:spruce_log", "x"), 3), (log("minecraft:spruce_log", "z"), 3), (log("minecraft:oak_log", "x"), 2),
        (log("minecraft:dark_oak_log", "z"), 2), ("minecraft:mangrove_roots", 4), (st("hay_block", axis="y"), 3),
        (log("minecraft:stripped_birch_log", "z"), 1)]
DOWN = [("minecraft:white_wool", 5), ("minecraft:light_gray_wool", 2), ("minecraft:brown_wool", 1),
        (st("hay_block", axis="y"), 2)]


def small_nest(s, cx, cz, base):
    for y in range(0, base):
        for x, z, d in disk(cx, cz, 2.8 - 0.25 * (base - y)):
            s.soft(x, y, z, s.pick(ROCK))
    for x, z, d in disk(cx, cz, 2.6):
        s.set(x, base, z, s.pick(NEST) if d > 1.5 else s.pick(DOWN))
        if d > 1.6:
            s.set(x, base + 1, z, s.pick(NEST))
        else:
            s.set(x, base + 1, z, AIR)
    s.set(cx, base + 1, cz, egg(s.rnd.randint(2, 4)))
    s.set(cx + 1, base + 1, cz, "minecraft:white_carpet")
    for x, z, d in disk(cx, cz, 2.6):
        s.air(x, base + 2, z, x, base + 4, z)


def harpy_nest():
    s = B("harpy_nest", 23, 15, 23)
    r, c = s.rnd, 11
    # Esporão de rocha.
    for y in range(0, 6):
        R = 7.0 - 0.8 * y
        for x, z, d in disk(c, c, R + 1.0):
            if d <= R + r.uniform(-0.6, 0.6):
                s.set(x, y, z, s.pick(ROCK))
    # Ninho gigante: base, parede trançada, forro de penas, borda.
    for x, z, d in disk(c, c, 4.6):
        s.set(x, 5, z, s.pick(NEST))
    for x, z, d in disk(c, c, 5.4):
        s.set(x, 6, z, s.pick(NEST) if d > 3.4 else s.pick(DOWN))
    for x, z, d in disk(c, c, 5.8):
        if d > 3.8:
            s.set(x, 7, z, s.pick(NEST))
    for x, z, d in disk(c, c, 6.2):
        if d > 4.6 and r.random() < 0.75:
            s.set(x, 8, z, s.pick(NEST))
    for x, z, d in disk(c, c, 4.6):
        s.air(x, 7, z, x, 13, z)
    for k in range(9):
        a = r.random() * math.tau
        dd = r.uniform(6.2, 7.4)
        x, z = round(c + math.cos(a) * dd), round(c + math.sin(a) * dd)
        y = r.choice((7, 8))
        s.set(x, y, z, r.choice(["minecraft:spruce_fence", log("minecraft:stripped_oak_log",
                                                                 "x" if abs(math.cos(a)) > 0.7 else "z")]))
    # Ovos, penas, ossos e o tesouro brilhante escondido no forro.
    for x, z, n in ((c - 1, c - 1, 4), (c + 1, c + 2, 3), (c - 2, c + 1, 2), (c + 2, c - 2, 3)):
        s.set(x, 7, z, egg(n))
    for x, z, d in disk(c, c, 3.4):
        if s.get(x, 7, z) == AIR and r.random() < 0.3:
            s.set(x, 7, z, r.choice(["minecraft:white_carpet", "minecraft:light_gray_carpet"]))
    s.set(c + 3, 7, c, "minecraft:bone_block")
    s.set(c, 7, c - 3, skull("skeleton", 3))
    s.chest(c + 2, 6, c + 1, loot("harpy_nest"), "west")
    s.set(c - 3, 6, c, "minecraft:gold_block")
    # Ninhos menores nas encostas.
    small_nest(s, c - 7, c + 4, 2)
    small_nest(s, c + 6, c - 6, 3)
    # Penas grudadas na rocha.
    for _ in range(14):
        x, z = r.randrange(2, 21), r.randrange(2, 21)
        for y in range(6, -1, -1):
            b = s.get(x, y, z)
            if b and b != AIR and "wool" not in b:
                if any(k in b for k in ("stone", "andesite", "calcite", "tuff", "cobble")):
                    s.set(x, y, z, r.choice(["minecraft:white_wool", "minecraft:snow_block", "minecraft:white_wool"]))
                break
    s.mob(c + 0.5, 7, c + 0.5, f"{NS}:harpy", 0.0)
    s.mob(c - 1.5, 7, c + 2.5, f"{NS}:harpy", 120.0)
    s.mob(c - 6.5, 3.2, c + 4.5, f"{NS}:harpy", -60.0)
    s.mob(c + 6.5, 4.2, c - 5.5, f"{NS}:harpy", 45.0)
    save(s, "harpy_nest/nest")


# =================================================================================================
#  ALDEIA DOS HOMENS-LAGARTO (palafitas no pântano)
# =================================================================================================
LZ_WATER, LZ_DECK = 4, 6     # superfície da água (start_height = -4) e altura dos decks


def lizardman_village():
    s = B("lizardman_village", 41, 15, 41)
    r, c = s.rnd, 20
    deck = {}

    def deck_cell(x, z, mat="minecraft:mangrove_planks"):
        if 0 <= x < 41 and 0 <= z < 41:
            deck[(x, z)] = mat

    for x in range(c - 8, c + 9):
        for z in range(c - 8, c + 9):
            if abs(x - c) + abs(z - c) <= 11:
                deck_cell(x, z)
    huts = []
    base = r.random() * math.tau
    for i in range(5):
        a = base + i * math.tau / 5 + r.uniform(-0.15, 0.15)
        hx, hz = round(c + math.cos(a) * 14), round(c + math.sin(a) * 14)
        hx, hz = max(4, min(36, hx)), max(4, min(36, hz))
        huts.append((hx, hz))
        for x in range(hx - 3, hx + 4):
            for z in range(hz - 3, hz + 4):
                if abs(x - hx) + abs(z - hz) <= 5:
                    deck_cell(x, z)
        # passarela de 3 de largura até a praça
        L = math.hypot(hx - c, hz - c)
        ux, uz = (hx - c) / L, (hz - c) / L
        t = 0.0
        while t <= L:
            px, pz = c + ux * t, c + uz * t
            for off in (-2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0):
                deck_cell(round(px - uz * off), round(pz + ux * off), "minecraft:mangrove_planks")
            t += 0.5
    # Doca ao sul da praça (um degrau abaixo) para os barcos.
    dock = set()
    for x in (c - 1, c, c + 1):
        for z in range(c + 9, c + 13):
            dock.add((x, z))
    for (x, z), mat in deck.items():
        s.set(x, LZ_DECK, z, mat)
    for (x, z) in dock:
        if (x, z) not in deck:
            s.set(x, LZ_DECK - 1, z, "minecraft:mangrove_planks")
    for x in (c - 1, c, c + 1):
        if (x, c + 9) not in deck:
            s.set(x, LZ_DECK - 1, c + 9, stairs("mangrove_stairs", "north"))
    # Palafitas.
    for (x, z) in list(deck) + list(dock):
        if (x % 3 == 0 and z % 3 == 0) or ((x, z) in dock and z == c + 12):
            top = LZ_DECK - 1 if (x, z) in deck else LZ_DECK - 2
            for y in range(0, top + 1):
                s.set(x, y, z, log("minecraft:mangrove_log"))
    # Parapeito nas bordas (com aberturas para a doca e para as escadas de mão).
    openings = {(x, c + 8) for x in (c - 1, c, c + 1)}
    border = []
    for (x, z) in deck:
        if any((x + dx, z + dz) not in deck for dx, dz in DIRV.values()):
            border.append((x, z))
    ladders = 0
    r.shuffle(border)
    for (x, z) in border:
        if (x, z) in openings:
            continue
        hut_core = any(abs(x - hx) <= 2 and abs(z - hz) <= 2 for hx, hz in huts)
        if hut_core:
            continue
        if ladders < 3 and math.hypot(x - c, z - c) > 9:
            for dname, (dx, dz) in DIRV.items():
                if (x + dx, z + dz) not in deck and 0 <= x + dx < 41 and 0 <= z + dz < 41:
                    for y in range(LZ_WATER, LZ_DECK + 1):
                        s.set(x + dx, y, z + dz, ladder(dname))
                    for y in range(0, LZ_DECK):
                        s.set(x, y, z, log("minecraft:mangrove_log"))
                    ladders += 1
                    break
            continue
        s.set(x, LZ_DECK + 1, z, "minecraft:mangrove_fence")
        if r.random() < 0.06:
            s.set(x, LZ_DECK + 2, z, lantern())
    # Redes de pesca (teias) penduradas sob a borda.
    for (x, z) in border[:8]:
        if s.get(x, LZ_DECK - 1, z) is None:
            s.set(x, LZ_DECK - 1, z, "minecraft:cobweb")
    # Cabanas: paredes de tijolo de barro, telhado piramidal de palha.
    for k, (hx, hz) in enumerate(huts):
        door = _face_toward(c - hx, c - hz)
        dx, dz = DIRV[door]
        for x in range(hx - 2, hx + 3):
            for z in range(hz - 2, hz + 3):
                edge = abs(x - hx) == 2 or abs(z - hz) == 2
                for y in range(LZ_DECK + 1, LZ_DECK + 4):
                    if edge:
                        corner = abs(x - hx) == 2 and abs(z - hz) == 2
                        s.set(x, y, z, log("minecraft:mangrove_log") if corner else
                              s.pick([("minecraft:mud_bricks", 5), ("minecraft:packed_mud", 2),
                                      ("minecraft:mangrove_planks", 1)]))
                    else:
                        s.set(x, y, z, AIR)
        for y in (LZ_DECK + 1, LZ_DECK + 2):
            s.set(hx + dx * 2, y, hz + dz * 2, AIR)
        for side in (-1, 1):
            wx, wz = hx + dz * 2 * side, hz + dx * 2 * side
            s.set(wx, LZ_DECK + 2, wz, "minecraft:mangrove_fence")
        y0 = LZ_DECK + 4
        for x in range(hx - 3, hx + 4):
            for z in range(hz - 3, hz + 4):
                h = max(abs(x - hx), abs(z - hz))
                if h >= 2:
                    s.set(x, y0, z, s.pick([(st("hay_block", axis="y"), 6), ("minecraft:moss_block", 1)]))
                else:
                    s.set(x, y0, z, AIR)
                if 1 <= h <= 2:
                    s.set(x, y0 + 1, z, s.pick([(st("hay_block", axis="y"), 6), ("minecraft:moss_block", 1)]))
                elif h == 0:
                    s.set(x, y0 + 1, z, AIR)
                if h <= 1:
                    s.set(x, y0 + 2, z, st("hay_block", axis="y"))
        s.set(hx, y0 + 3, hz, "minecraft:mangrove_fence")
        s.set(hx, y0 + 1, hz, lantern(True))
        ix, iz = hx - dx, hz - dz
        s.set(ix - dz, LZ_DECK + 1, iz - dx, st("hay_block", axis="y"))
        s.set(ix - dz, LZ_DECK + 2, iz - dx, "minecraft:green_carpet")
        if k in (0, 2):
            s.chest(ix + dz, LZ_DECK + 1, iz + dx, loot("lizardman_village"), door)
        else:
            s.barrel(ix + dz, LZ_DECK + 1, iz + dx)
        s.set(hx - dx, LZ_DECK + 1, hz - dz, r.choice([st("water_cauldron", level=3), "minecraft:dried_kelp_block",
                                                         st("barrel", facing="up", open="false")]))
        s.mob(hx + 0.5, LZ_DECK + 1, hz + 0.5, f"{NS}:lizardman", YAW[door])
    # Praça: ídolo do deus-lagarto, fogueiras e coisas de pesca.
    for y, b in enumerate(["minecraft:mossy_cobblestone", "minecraft:green_terracotta", "minecraft:green_terracotta",
                           st("lime_glazed_terracotta", facing="south")], start=LZ_DECK + 1):
        s.set(c, y, c, b)
    s.set(c, LZ_DECK + 5, c, skull("dragon", 0))
    for x in (c - 1, c + 1):
        s.set(x, LZ_DECK + 1, c, "minecraft:moss_block")
        s.set(x, LZ_DECK + 2, c, "minecraft:moss_carpet")
    for x in (c - 4, c + 4):
        s.set(x, LZ_DECK, c + 3, "minecraft:mud_bricks")
        s.set(x, LZ_DECK + 1, c + 3, campfire())
    s.barrel(c - 4, LZ_DECK + 1, c - 4)
    s.set(c + 4, LZ_DECK + 1, c - 4, "minecraft:dried_kelp_block")
    s.set(c + 4, LZ_DECK + 2, c - 4, "minecraft:dried_kelp_block")
    s.set(c - 3, LZ_DECK + 1, c - 5, st("water_cauldron", level=3))
    for x, z in ((c - 6, c - 2), (c + 6, c + 2)):
        s.set(x, LZ_DECK + 1, z, "minecraft:mangrove_fence")
        s.set(x, LZ_DECK + 2, z, "minecraft:mangrove_fence")
        s.set(x, LZ_DECK + 3, z, lantern())
    for (x, z) in deck:
        s.air(x, LZ_DECK + 1, z, x, LZ_DECK + 4, z)
    s.entity(c + 3.5, LZ_WATER + 1, c + 11.5, "minecraft:boat", {"Type": "mangrove", "Rotation": [Float(0.0), Float(0.0)]})
    s.entity(c - 3.5, LZ_WATER + 1, c + 12.5, "minecraft:boat", {"Type": "mangrove", "Rotation": [Float(30.0), Float(0.0)]})
    for x, z, yaw in ((c + 2, c - 2, 45.0), (c - 2, c + 3, 180.0), (c + 5, c + 5, -90.0)):
        s.mob(x + 0.5, LZ_DECK + 1, z + 0.5, f"{NS}:lizardman", yaw)
    save(s, "lizardman_village/village")


# =================================================================================================
#  PONTE DO TROLL (ponte de pedra em arco sobre o rio; o troll mora embaixo)
# =================================================================================================
TB_WATER = 6     # superfície da água (start_height = -6)


def troll_bridge():
    s = B("troll_bridge", 35, 17, 9)
    r = s.rnd
    deck = [("minecraft:stone_bricks", 6), ("minecraft:cobblestone", 2), ("minecraft:andesite", 1),
            ("minecraft:mossy_stone_bricks", 1)]
    found = [("minecraft:cobblestone", 4), ("minecraft:stone", 3), ("minecraft:mossy_cobblestone", 2),
             ("minecraft:stone_bricks", 2)]

    def top(x):
        # Cabeceiras 1 acima da água (piso = mar + 1, igual a uma margem baixa); rampa de escadas até o arco.
        xp = min(x, 34 - x)
        return 7 if xp <= 1 else (6 + xp if xp <= 6 else 13)

    def intrados(x):
        return round(7 + 5 * math.sqrt(max(0.0, 1 - ((x - 17) / 7.5) ** 2)))

    for x in range(35):
        t = top(x)
        xp = min(x, 34 - x)
        for z in range(9):
            if z in (0, 8):
                s.set(x, t, z, "minecraft:stone_bricks")
                s.set(x, t + 1, z, "minecraft:stone_brick_wall")
            elif 2 <= xp <= 6:
                s.set(x, t, z, stairs("stone_brick_stairs", "east" if x < 17 else "west"))
            else:
                s.set(x, t, z, s.pick(deck))
            if xp <= 6:
                for y in range(3, t):
                    s.set(x, y, z, s.pick(found))
            elif xp <= 9:
                for y in range(0, t):
                    s.set(x, y, z, "minecraft:stone_bricks" if y > 3 else s.pick(found))
            else:
                iy = intrados(x)
                for y in range(iy, t):
                    s.set(x, y, z, "minecraft:stone_bricks" if y == iy else s.pick(found))
        if xp in (7, 9):
            for z in (0, 8):
                s.set(x, t, z, "minecraft:chiseled_stone_bricks")
    # Lanternas no parapeito.
    for x in (0, 34):
        for z in (0, 8):
            s.set(x, top(x) + 2, z, lantern())
    s.set(17, 15, 0, lantern())
    s.set(17, 15, 8, lantern())
    # Placas de "pedágio" nas duas cabeceiras.
    keys = ["sign.isekai_mahou.troll_bridge.1", "sign.isekai_mahou.troll_bridge.2", "sign.isekai_mahou.troll_bridge.3"]
    s.sign(2, top(2) + 2, 0, st("spruce_sign", rotation=4, waterlogged="false"), keys)
    s.sign(32, top(32) + 2, 8, st("spruce_sign", rotation=12, waterlogged="false"), keys)
    # Covil do troll: plataforma sob o arco, ossos, ninho de feno e o baú com o "pedágio" acumulado.
    for x in range(10, 14):
        for z in range(1, 8):
            s.set(x, TB_WATER, z, s.pick([("minecraft:mossy_cobblestone", 4), ("minecraft:gravel", 2),
                                          ("minecraft:cobblestone", 2), ("minecraft:mud", 1)]))
            for y in range(TB_WATER + 1, intrados(x)):
                s.set(x, y, z, AIR)
    s.set(10, 7, 1, "minecraft:bone_block")
    s.set(10, 8, 1, skull("skeleton", 4))
    s.set(11, 7, 1, "minecraft:bone_block")
    s.chest(10, 7, 4, loot("troll_bridge"), "east")
    s.set(10, 7, 6, st("hay_block", axis="z"))
    s.set(11, 7, 6, st("hay_block", axis="z"))
    s.set(10, 7, 7, "minecraft:brown_wool")
    s.set(11, 8, 6, "minecraft:brown_carpet")
    s.set(13, 7, 2, "minecraft:bone_block")
    s.set(13, 7, 7, skull("skeleton", 10))
    for x, z in ((12, 1), (13, 6), (12, 7), (14, 3)):
        s.set(x, intrados(x) - 1, z, "minecraft:cobweb")
    s.armor_stand(12.5, 7, 7.5, YAW["north"], head="iron_helmet", chest="chainmail_chestplate")
    s.mob(12.5, 7, 4.5, f"{NS}:troll", YAW["east"])
    # Vão livre sobre o tabuleiro: numa margem um pouco mais alta a cabeceira não fica enterrada no barranco.
    for x in range(35):
        for z in range(9):
            s.air(x, top(x) + 1, z, x, min(top(x) + 4, 16), z)
    save(s, "troll_bridge/bridge")


# =================================================================================================
#  TORRE DO NECROMANTE RENEGADO (torre sombria de 5 andares com cultistas e glifos de sombra)
# =================================================================================================
def renegade_tower():
    s = B("renegade_tower", 19, 37, 19)
    r, c = s.rnd, 9
    WALL = [("minecraft:polished_blackstone_bricks", 10), ("minecraft:blackstone", 3),
            ("minecraft:deepslate_bricks", 2), ("minecraft:cracked_deepslate_bricks", 1),
            ("minecraft:gilded_blackstone", 0.15)]
    R_OUT, R_IN, TOP = 5.5, 4.5, 30
    floors = (6, 12, 18, 24)
    # Pátio maldito.
    for x, z, d in disk(c, c, 9.3):
        if d > 6.6:
            g = s.pick([("minecraft:coarse_dirt", 4), ("minecraft:soul_soil", 3), ("minecraft:blackstone", 2),
                        ("minecraft:gravel", 1), ("minecraft:podzol", 2), (None, 3)])
            if g:
                s.set(x, 0, z, g)
    for x, z, d in disk(c, c, 6.6):
        if d > R_OUT:
            s.set(x, 0, z, "minecraft:polished_blackstone")
            s.set(x, 1, z, slab("polished_blackstone_slab"))
    # Corpo da torre.
    for x, z, d in disk(c, c, R_OUT):
        for y in range(0, TOP + 1):
            if d > R_IN:
                s.set(x, y, z, s.pick(WALL))
            elif y == 0:
                s.set(x, y, z, s.pick([("minecraft:polished_blackstone", 3), ("minecraft:deepslate_tiles", 2)]))
            elif y in floors:
                s.set(x, y, z, "minecraft:dark_oak_planks")
            elif y == TOP:
                s.set(x, y, z, "minecraft:polished_blackstone_bricks")
            else:
                s.set(x, y, z, AIR)
        if d > R_IN:
            idx = int((math.atan2(z - c, x - c) + math.pi) / math.tau * 16)
            if idx % 2 == 0:
                s.set(x, TOP + 1, z, s.pick(WALL))
                s.set(x, TOP + 2, z, "minecraft:polished_blackstone_bricks")
            else:
                s.set(x, TOP + 1, z, slab("polished_blackstone_brick_slab"))
    # Faixas decorativas.
    for x, z, d in disk(c, c, R_OUT):
        if d > R_IN:
            for y in (5, 11, 17, 23, 29):
                s.set(x, y, z, "minecraft:polished_deepslate")
    # Janelas estreitas roxas.
    for f in range(5):
        y0 = 6 * f
        for x, z in ((c, c - 5), (c, c + 5), (c - 5, c), (c + 5, c)):
            if f == 0 and z == c + 5:
                continue
            s.set(x, y0 + 2, z, pane("purple"))
            s.set(x, y0 + 3, z, pane("purple"))
    # Porta e caminho.
    s.door(c, 1, c + 5, "crimson", "south")
    for z in (c + 6, c + 7, c + 8, c + 9):
        s.set(c, 0, z, "minecraft:polished_blackstone")
    for x in (c - 1, c + 1):
        s.set(x, 3, c + 6, wall_torch("south", soul=True))
    # Escadas em lances alternados (oeste subindo para o norte / leste subindo para o sul).
    for f in range(5):
        y0 = 6 * f
        if f % 2 == 0:
            sx, zs, facing, rail_x = 6, [11 - i for i in range(6)], "north", 7
        else:
            sx, zs, facing, rail_x = 12, [6 + i for i in range(6)], "south", 11
        # 6 degraus: o último fica no nível do piso de cima (sem pulo de 1 bloco no fim do lance)
        for i, z in enumerate(zs):
            s.set(sx, y0 + 1 + i, z, stairs("polished_blackstone_brick_stairs", facing))
            for y in range(y0 + 1, y0 + 1 + i):
                s.set(sx, y, z, "minecraft:polished_blackstone_bricks")
        for z in range(7, 11):
            s.set(sx, y0 + 6, z, AIR)
            s.set(rail_x, y0 + 7, z, "minecraft:dark_oak_fence" if f < 4 else "minecraft:polished_blackstone_brick_wall")

    def stair_zone(x, z):
        return (x in (5, 6, 7) or x in (11, 12, 13)) and 6 <= z <= 11

    # Térreo: círculo ritual.
    s.set(c, 0, c, "minecraft:crying_obsidian")
    for x, z, d in disk(c, c, 3.2):
        if d > 2.3 and s.get(x, 1, z) == AIR and not stair_zone(x, z):
            s.set(x, 1, z, "minecraft:purple_carpet" if (x + z) % 2 == 0 else "minecraft:black_carpet")
    for x, z in ((7, 7), (11, 7), (11, 11), (8, 12)):
        s.set(x, 1, z, candle(r.randint(2, 4), "black" if (x + z) % 2 else "purple"))
    s.set(c, 5, c, st("chain", axis="y", waterlogged="false"))
    s.set(c, 4, c, lantern(True, soul=True))
    s.banner(c, 4, c - 4, st("purple_wall_banner", facing="south"),
             [("skull", "black"), ("gradient", "black"), ("border", "black")])
    s.mob(8.5, 1, 10.5, f"{NS}:cultist", YAW["north"])
    s.mob(10.5, 1, 8.5, f"{NS}:cultist", YAW["west"])
    # 1º andar: biblioteca.
    y0 = 7
    for x, z, d in disk(c, c, R_IN):
        if d > 3.4 and not stair_zone(x, z) and not (abs(x - c) <= 1 and abs(z - c) >= 3) \
                and not (abs(z - c) <= 1 and abs(x - c) >= 3):
            for y in (y0, y0 + 1, y0 + 2):
                s.set(x, y, z, "minecraft:bookshelf")
            if r.random() < 0.25:
                s.set(x, y0 + 3, z, candle(r.randint(1, 3), "purple"))
    s.set(10, y0, 8, st("lectern", facing="south", has_book="false", powered="false"))
    s.chest(8, y0, 12, loot("renegade_tower"), "north")
    s.set(c, 11, c, lantern(True, soul=True))
    s.mob(9.5, y0, 9.5, f"{NS}:cultist", YAW["south"])
    # 2º andar: laboratório.
    y0 = 13
    s.set(8, y0, 8, st("brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false"))
    s.set(10, y0, 8, st("brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false"))
    s.set(c, y0, c, campfire(soul=True))
    s.set(9, y0, 7, st("water_cauldron", level=2))
    s.barrel(11, y0, 13)
    s.barrel(11, y0 + 1, 13)
    s.barrel(7, y0, 12)
    s.chest(10, y0, 5, loot("renegade_tower"), "south")
    s.set(8, y0 - 1, 10, "minecraft:crying_obsidian")
    s.set(10, y0 - 1, 10, "minecraft:crying_obsidian")
    # 3º andar: cripta com gerador de esqueletos (servos do necromante).
    y0 = 19
    s.spawner(c, y0, c, "minecraft:skeleton")
    for x, z in ((8, 12), (10, 12)):
        s.set(x, y0, z, "minecraft:bone_block")
        s.set(x, y0 + 1, z, skull("skeleton", 0))
    for x, z in ((8, 8), (10, 10), (8, 10)):
        s.set(x, y0 + 3, z, "minecraft:cobweb")
    for x, z in ((7, 12), (11, 6)):
        s.set(x, y0 + 4, z, st("chain", axis="y", waterlogged="false"))
        s.set(x, y0 + 3, z, st("chain", axis="y", waterlogged="false"))
    s.set(9, y0, 5, lantern(soul=True))
    # 4º andar: santuário com altar.
    y0 = 25
    for x in (8, 9, 10):
        s.set(x, y0, 5, "minecraft:polished_blackstone_bricks")
    s.set(9, y0 + 1, 5, campfire(soul=True))
    s.set(8, y0 + 1, 5, candle(4, "black"))
    s.set(10, y0 + 1, 5, candle(4, "purple"))
    s.banner(9, y0 + 3, 5, st("purple_wall_banner", facing="south"),
             [("skull", "black"), ("gradient", "black"), ("border", "black")])
    s.chest(9, y0, 12, loot("renegade_tower_sanctum"), "north")
    s.set(c, 29, c, lantern(True, soul=True))
    for x, z in ((7, 9), (11, 9), (9, 10)):
        s.set(x, y0 - 1, z, "minecraft:crying_obsidian")
    s.mob(8.5, y0, 9.5, f"{NS}:cultist", YAW["north"])
    s.mob(10.5, y0, 8.5, f"{NS}:cultist", YAW["north"])
    # Topo: pináculo e lanternas de alma nas ameias.
    s.set(c, TOP + 1, c, "minecraft:crying_obsidian")
    s.set(c, TOP + 2, c, "minecraft:polished_blackstone_wall")
    s.set(c, TOP + 3, c, "minecraft:polished_blackstone_wall")
    s.set(c, TOP + 4, c, lantern(soul=True))
    for x, z in ((c, c - 5), (c, c + 5), (c - 5, c), (c + 5, c)):
        if s.get(x, TOP + 2, z) == "minecraft:polished_blackstone_bricks":
            s.set(x, TOP + 3, z, lantern(soul=True))
    # Túmulos, árvores mortas e cerca de grades quebrada no pátio.
    graves = 0
    for _ in range(40):
        if graves >= 5:
            break
        a = r.random() * math.tau
        d = r.uniform(7.0, 8.2)
        x, z = round(c + math.cos(a) * d), round(c + math.sin(a) * d)
        if abs(x - c) <= 2 and z > c:
            continue
        if s.get(x, 1, z) is not None or s.get(x, 1, z + 1) is not None:
            continue
        s.set(x, 1, z, "minecraft:cobbled_deepslate_wall")
        s.set(x, 0, z + 1, "minecraft:coarse_dirt")
        s.set(x, 1, z + 1, r.choice(["minecraft:dead_bush", skull("skeleton", r.randrange(16)), AIR]))
        graves += 1
    for tx, tz in ((c - 7, c - 5), (c + 6, c + 6)):
        if s.get(tx, 1, tz) is None:
            s.set(tx, 0, tz, "minecraft:coarse_dirt")
            for y in range(1, 5):
                s.set(tx, y, tz, log("minecraft:dark_oak_log"))
            s.set(tx + 1, 3, tz, log("minecraft:dark_oak_log", "x"))
            s.set(tx - 1, 4, tz, log("minecraft:dark_oak_log", "x"))
            s.set(tx, 4, tz + 1, log("minecraft:dark_oak_log", "z"))
    for x, z, d in disk(c, c, 9.1):
        if d > 8.4 and not (abs(x - c) <= 1 and z > c) and r.random() < 0.55 and s.get(x, 1, z) is None:
            s.set(x, 1, z, "minecraft:iron_bars")
            if r.random() < 0.5:
                s.set(x, 2, z, "minecraft:iron_bars")
    for _ in range(3):
        x, z = r.randrange(1, 18), r.randrange(1, 18)
        if s.get(x, 0, z) == "minecraft:soul_soil" and s.get(x, 1, z) is None:
            s.set(x, 1, z, "minecraft:wither_rose")
    for x, z, d in disk(c, c, 9.3):
        if d > R_OUT:
            s.air(x, 1, z, x, 6, z)
    save(s, "renegade_tower/tower")


# =================================================================================================
#  WORLDGEN (structure, structure_set, template_pool, processor_list) + TAGS
# =================================================================================================
def rule(block, out, p):
    return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": p},
            "location_predicate": {"predicate_type": "minecraft:always_true"},
            "output_state": {"Name": out}}


PROCESSORS = {
    "guild_hall_village_aging": [rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.06),
                                 rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.05),
                                 rule("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.1)],
    # Rua sobre a água vira tábua (igual às vilas do vanilla).
    "guild_hall_village_road": [{
        "input_predicate": {"predicate_type": "minecraft:block_match", "block": "minecraft:dirt_path"},
        "location_predicate": {"predicate_type": "minecraft:block_match", "block": "minecraft:water"},
        "output_state": {"Name": "minecraft:oak_planks"}}],
    "goblin_fortress_decay": [rule("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.2),
                              rule("minecraft:spruce_planks", "minecraft:mossy_cobblestone", 0.02)],
    "minotaur_labyrinth_decay": [rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.18),
                                 rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.15)],
    "renegade_tower_decay": [rule("minecraft:polished_blackstone_bricks",
                                  "minecraft:cracked_polished_blackstone_bricks", 0.15)],
}


def el(loc, w=1, proj="rigid", proc=None):
    return (loc, w, proj, proc)


POOLS = {
    "guild_hall_village/start": ([el("guild_hall_village/plaza", proc="guild_hall_village_aging")], None),
    "guild_hall_village/hall_roads": ([el("guild_hall_village/hall_road", proj="terrain_matching",
                                          proc="guild_hall_village_road")], None),
    "guild_hall_village/roads": ([el("guild_hall_village/road", proj="terrain_matching",
                                     proc="guild_hall_village_road")], None),
    "guild_hall_village/hall": ([el("guild_hall_village/guild_hall", proc="guild_hall_village_aging")],
                                "guild_hall_village/road_ends"),
    "guild_hall_village/houses": ([el("guild_hall_village/cottage_oak", 3, proc="guild_hall_village_aging"),
                                   el("guild_hall_village/cottage_spruce", 2, proc="guild_hall_village_aging"),
                                   el("guild_hall_village/smithy", 2, proc="guild_hall_village_aging"),
                                   el("guild_hall_village/adventurer_camp", 2),
                                   el("guild_hall_village/market", 2)], "guild_hall_village/road_ends"),
    "guild_hall_village/road_ends": ([el("guild_hall_village/road_end")], None),
    "goblin_village/start": ([el("goblin_village/village_a"), el("goblin_village/village_b")], None),
    "goblin_fortress/start": ([el("goblin_fortress/fortress", proc="goblin_fortress_decay")], None),
    "orc_camp/start": ([el("orc_camp/camp_a"), el("orc_camp/camp_b")], None),
    "kobold_den/start": ([el("kobold_den/entrance")], None),
    "kobold_den/shafts": ([el("kobold_den/shaft")], None),
    "kobold_den/mines": ([el("kobold_den/mine")], None),
    "minotaur_labyrinth/start": ([el("minotaur_labyrinth/maze_a", proc="minotaur_labyrinth_decay"),
                                  el("minotaur_labyrinth/maze_b", proc="minotaur_labyrinth_decay"),
                                  el("minotaur_labyrinth/maze_c", proc="minotaur_labyrinth_decay")], None),
    "harpy_nest/start": ([el("harpy_nest/nest")], None),
    "lizardman_village/start": ([el("lizardman_village/village")], None),
    "renegade_tower/start": ([el("renegade_tower/tower", proc="renegade_tower_decay")], None),
}

PLAINS_LIKE = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:savanna",
               "minecraft:savanna_plateau", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:meadow"]
# id: biomas, tamanho jigsaw, start_height, heightmap, adaptação, etapa, dist. máx., espaçamento, separação, salt,
#     exclusão de vilas (chunks)
STRUCTURES = {
    "guild_hall_village": (PLAINS_LIKE, 3, 0, "WORLD_SURFACE_WG", "beard_thin", "surface_structures", 64,
                           45, 15, 1946300101, 4),
    "goblin_village": (["minecraft:plains", "minecraft:forest", "minecraft:birch_forest", "minecraft:flower_forest",
                        "minecraft:taiga", "minecraft:savanna", "minecraft:meadow", "minecraft:windswept_forest"],
                       1, 0, "WORLD_SURFACE_WG", "beard_thin", "surface_structures", 40, 32, 10, 1946300102, 4),
    "goblin_fortress": (["minecraft:forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga",
                         "minecraft:old_growth_pine_taiga", "minecraft:dark_forest", "minecraft:windswept_forest",
                         "minecraft:plains", "minecraft:birch_forest"],
                        1, 0, "WORLD_SURFACE_WG", "beard_box", "surface_structures", 48, 60, 20, 1946300103, 6),
    "orc_camp": (["minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:badlands",
                  "minecraft:wooded_badlands", "minecraft:plains", "minecraft:snowy_plains"],
                 1, 0, "WORLD_SURFACE_WG", "beard_thin", "surface_structures", 40, 40, 12, 1946300104, 4),
    "kobold_den": (["minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
                    "minecraft:meadow", "minecraft:grove", "minecraft:taiga", "minecraft:forest",
                    "minecraft:old_growth_pine_taiga", "minecraft:stony_shore"],
                   3, -KD_FLOOR, "WORLD_SURFACE_WG", "none", "underground_structures", 64, 36, 12, 1946300105, 0),
    "minotaur_labyrinth": (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna",
                            "minecraft:savanna_plateau", "minecraft:meadow", "minecraft:desert", "minecraft:badlands"],
                           1, 0, "WORLD_SURFACE_WG", "beard_box", "surface_structures", 40, 64, 22, 1946300106, 5),
    "harpy_nest": (["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks",
                    "minecraft:snowy_slopes"],
                   1, 0, "WORLD_SURFACE_WG", "beard_thin", "surface_structures", 32, 24, 8, 1946300107, 0),
    "lizardman_village": (["minecraft:swamp", "minecraft:mangrove_swamp"],
                          1, -LZ_WATER, "WORLD_SURFACE_WG", "none", "surface_structures", 40, 24, 8, 1946300108, 0),
    # tipo próprio (TrollBridgeStructure): tamanho/altura/heightmap/dist. do jigsaw não se aplicam
    "troll_bridge": (["minecraft:river", "minecraft:frozen_river"],
                     None, None, None, "none", "surface_structures", None, 14, 5, 1946300109, 0),
    "renegade_tower": (["minecraft:dark_forest", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga",
                        "minecraft:swamp", "minecraft:snowy_taiga"],
                       1, 0, "WORLD_SURFACE_WG", "beard_thin", "surface_structures", 32, 44, 14, 1946300110, 4),
}


# Estruturas com tipo próprio em Java (no lugar do jigsaw): campos extras do JSON.
#  - troll_bridge: o jigsaw gira a peça ao acaso; o tipo próprio mede o rio e gira a ponte para atravessá-lo.
CUSTOM_TYPES = {
    "troll_bridge": {"type": f"{NS}:troll_bridge", "template": f"{NS}:troll_bridge/bridge", "water_line": TB_WATER},
}
# Arquivos de versões antigas do gerador que não existem mais (apagados para não sobrar lixo nos dados).
STALE = ["worldgen/template_pool/troll_bridge/start.json", "worldgen/processor_list/troll_bridge_decay.json"]


def gen_worldgen():
    for name, rules in PROCESSORS.items():
        write_json(os.path.join(D, f"worldgen/processor_list/{name}.json"),
                   {"processors": [{"processor_type": "minecraft:rule", "rules": rules}]})
    for pool, (elements, fallback) in POOLS.items():
        write_json(os.path.join(D, f"worldgen/template_pool/{pool}.json"), {
            "fallback": f"{NS}:{fallback}" if fallback else "minecraft:empty",
            "elements": [{"weight": w, "element": {
                "element_type": "minecraft:single_pool_element", "location": f"{NS}:{loc}",
                "processors": f"{NS}:{proc}" if proc else "minecraft:empty", "projection": proj}}
                for loc, w, proj, proc in elements]})
    for stale in STALE:
        path = os.path.join(D, stale)
        if os.path.exists(path):
            os.remove(path)
            if not os.listdir(os.path.dirname(path)):
                os.rmdir(os.path.dirname(path))
    for sid, (biomes, size, sh, hm, adapt, step, dist, spacing, sep, salt, excl) in STRUCTURES.items():
        base = {"biomes": f"#{NS}:has_structure/{sid}", "step": step, "terrain_adaptation": adapt,
                "spawn_overrides": {}}
        if sid in CUSTOM_TYPES:
            write_json(os.path.join(D, f"worldgen/structure/{sid}.json"), {**CUSTOM_TYPES[sid], **base})
        else:
            write_json(os.path.join(D, f"worldgen/structure/{sid}.json"), {
                "type": "minecraft:jigsaw", **base,
                "start_pool": f"{NS}:{sid}/start",
                "size": size,
                "start_height": {"absolute": sh},
                "project_start_to_heightmap": hm,
                "max_distance_from_center": dist,
                "use_expansion_hack": False})
        placement = {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}
        if excl:
            placement["exclusion_zone"] = {"other_set": "minecraft:villages", "chunk_count": excl}
        write_json(os.path.join(D, f"worldgen/structure_set/{sid}.json"), {
            "structures": [{"structure": f"{NS}:{sid}", "weight": 1}], "placement": placement})
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"), {"values": biomes})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "on_guild_maps.json"), {"values": [f"{NS}:guild_hall_village"]})
    write_json(os.path.join(tags, "on_labyrinth_maps.json"), {"values": [f"{NS}:minotaur_labyrinth"]})
    write_json(os.path.join(tags, "rpg_structures.json"), {"values": [f"{NS}:{sid}" for sid in STRUCTURES]})


# =================================================================================================
#  LOOT TABLES
# =================================================================================================
def cnt(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def dmg(lo=0.2, hi=0.8):
    return {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def item(name, w=1, lo=1, hi=1, fns=None):
    e = {"type": "minecraft:item", "name": mc(name), "weight": w}
    f = [cnt(lo, hi)] if (lo, hi) != (1, 1) else []
    if fns:
        f += fns
    if f:
        e["functions"] = f
    return e


def ess(element, w, lo=1, hi=2):
    return item(f"{NS}:essence_{element}", w, lo, hi)


def scroll(w, elements=None, min_tier=1, max_tier=2, legendary=False, kinds=None):
    fn = {"function": f"{NS}:rpg_glyph_scroll", "min_tier": min_tier, "max_tier": max_tier}
    if elements:
        fn["elements"] = elements
    if kinds:
        fn["kinds"] = kinds
    if legendary:
        fn["allow_legendary"] = True
    return {"type": "minecraft:item", "name": f"{NS}:glyph_scroll", "weight": w, "functions": [fn]}


def sub(name, w):
    return {"type": "minecraft:loot_table", "value": f"{NS}:structures_c/{name}", "weight": w}


def empty(w):
    return {"type": "minecraft:empty", "weight": w}


def pool(lo, hi, entries):
    rolls = lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}
    return {"rolls": rolls, "bonus_rolls": 0, "entries": entries}


BOOK = item("book", 2, fns=[{"function": "minecraft:enchant_randomly"}])
ALL_BASE = ["arcane", "fire", "water", "earth", "wind", "light", "shadow"]

CHESTS = {
    "guild_hall_village": [
        pool(1, 2, [scroll(3, ["arcane"], 1, 2), scroll(2, ALL_BASE, 1, 1)]),
        pool(2, 4, [sub("guild_coins", 4), item("emerald", 3, 1, 3), item(f"{NS}:mana_crystal", 2, 1, 2),
                    ess("arcane", 2), item("gold_ingot", 2, 1, 3), item("map", 1), item("book", 2, 1, 2),
                    item("name_tag", 1), item("potion", 1, fns=[{"function": "minecraft:set_potion",
                                                                 "id": "minecraft:healing"}])]),
        pool(0, 1, [sub("mana_ring", 1), sub("arcane_amulet", 1), empty(6)]),
    ],
    "guild_hall_village_house": [
        pool(3, 6, [item("bread", 5, 1, 4), item("apple", 3, 1, 3), item("cooked_mutton", 2, 1, 3),
                    item("wheat", 3, 2, 6), item("carrot", 2, 1, 3), item("potato", 2, 1, 3), item("paper", 2, 1, 3),
                    item("book", 1), item("torch", 3, 2, 6), item("emerald", 1, 1, 2), item("iron_ingot", 1, 1, 2),
                    sub("guild_coins", 2), item("leather", 2, 1, 3), item("string", 2, 1, 3), item("feather", 2, 1, 3),
                    item("arrow", 2, 2, 8), empty(2)]),
        pool(0, 1, [scroll(1, ALL_BASE, 1, 1), empty(4)]),
    ],
    "goblin_village": [
        pool(3, 6, [item("rotten_flesh", 6, 1, 4), item("bone", 5, 1, 3), item("leather", 4, 1, 3),
                    item("wheat", 3, 2, 5), item("carrot", 3, 1, 3), item("potato", 2, 1, 3),
                    item("iron_nugget", 4, 2, 8), item("gold_nugget", 4, 1, 6), item("arrow", 3, 2, 8),
                    item("string", 3, 1, 3), item("stone_axe", 2, fns=[dmg()]), item("iron_sword", 1, fns=[dmg()]),
                    item("bow", 1, fns=[dmg()]), sub("goblin_ears", 3), sub("guild_coins", 2), item("emerald", 1, 1, 2),
                    empty(2)]),
        pool(0, 1, [scroll(2, ["arcane", "fire", "earth"], 1, 1), item(f"{NS}:mana_crystal", 1), empty(5)]),
    ],
    "goblin_fortress": [
        pool(4, 7, [item("iron_ingot", 5, 1, 4), item("gold_ingot", 4, 1, 3), item("iron_nugget", 3, 3, 9),
                    item("gold_nugget", 3, 3, 9), item("emerald", 3, 1, 4), item("arrow", 4, 4, 12),
                    item("iron_sword", 2, fns=[ench(5, 15)]), item("crossbow", 2, fns=[ench(5, 15)]),
                    item("iron_helmet", 1, fns=[dmg()]), item("chainmail_chestplate", 1, fns=[dmg()]),
                    item("bread", 3, 1, 4), item("cooked_porkchop", 3, 1, 3), item("saddle", 1),
                    sub("goblin_ears", 3), sub("guild_coins", 3), item("leather", 3, 1, 4), item("diamond", 1),
                    empty(2)]),
        pool(1, 1, [scroll(3, ["arcane", "fire", "earth", "wind"], 1, 3), item(f"{NS}:mana_crystal", 2, 1, 2),
                    ess("earth", 1), ess("fire", 1)]),
    ],
    "goblin_fortress_throne": [
        pool(4, 6, [item("gold_ingot", 5, 2, 6), item("gold_nugget", 4, 6, 18), item("emerald", 4, 2, 6),
                    item("diamond", 3, 1, 3), item("golden_apple", 2), item("golden_sword", 2, fns=[ench(10, 25)]),
                    item("golden_helmet", 1, fns=[ench(10, 25)]), item("gold_block", 1), sub("guild_coins", 4),
                    item(f"{NS}:mana_crystal", 2, 2, 3), BOOK]),
        pool(1, 2, [scroll(4, ALL_BASE, 2, 3), scroll(1, ALL_BASE, 3, 4)]),
        pool(1, 1, [sub("mana_ring", 1), sub("arcane_amulet", 1), ess("arcane", 2, 2, 3)]),
    ],
    "orc_camp": [
        pool(3, 6, [item("beef", 5, 1, 4), item("cooked_beef", 4, 1, 3), item("leather", 5, 2, 5),
                    item("bone", 4, 2, 5), item("iron_ingot", 3, 1, 3), item("iron_axe", 2, fns=[dmg()]),
                    item("stone_axe", 2, fns=[dmg()]), item("chainmail_chestplate", 1, fns=[dmg()]),
                    item("gold_nugget", 3, 2, 8), item("emerald", 1, 1, 2), sub("orc_tusks", 3),
                    sub("guild_coins", 2), empty(2)]),
        pool(0, 1, [scroll(2, ["fire", "earth"], 1, 2), ess("fire", 1), empty(4)]),
    ],
    "kobold_den": [
        pool(4, 8, [item("raw_iron", 6, 2, 6), item("raw_copper", 6, 3, 10), item("raw_gold", 3, 1, 3),
                    item("coal", 6, 3, 10), item("redstone", 3, 2, 8), item("lapis_lazuli", 3, 2, 6),
                    item("amethyst_shard", 3, 2, 6), item("iron_pickaxe", 2, fns=[dmg()]), item("torch", 4, 4, 12),
                    item("rail", 3, 4, 12), item("diamond", 1, 1, 2), item("emerald", 1, 1, 2),
                    item(f"{NS}:mana_crystal", 2, 1, 2), empty(2)]),
        pool(0, 1, [scroll(2, ["earth", "arcane"], 1, 2), ess("earth", 2), empty(3)]),
    ],
    "minotaur_labyrinth": [
        pool(5, 8, [item("gold_ingot", 5, 3, 8), item("diamond", 4, 2, 4), item("emerald", 4, 3, 8),
                    item("golden_apple", 3, 1, 2), item("enchanted_golden_apple", 1), item("iron_ingot", 3, 3, 8),
                    item("diamond_sword", 2, fns=[ench(20, 30)]), item("diamond_chestplate", 1, fns=[ench(20, 30)]),
                    item("book", 3, fns=[{"function": "minecraft:enchant_randomly"}]),
                    item("experience_bottle", 3, 3, 8), item(f"{NS}:mana_crystal", 3, 2, 4), ess("arcane", 2, 2, 3),
                    sub("guild_coins", 4), item("totem_of_undying", 1)]),
        pool(2, 3, [scroll(5, ALL_BASE, 2, 4), scroll(1, ALL_BASE, 4, 4, kinds=["effect", "form"])]),
        pool(1, 1, [sub("mana_ring", 1), sub("arcane_amulet", 1), item("gold_block", 1)]),
    ],
    "minotaur_labyrinth_cache": [
        pool(2, 4, [item("bone", 4, 1, 4), item("rotten_flesh", 3, 1, 3), item("gold_nugget", 3, 2, 6),
                    item("iron_ingot", 2, 1, 2), item("arrow", 2, 2, 6), item("bread", 2, 1, 2), item("torch", 2, 2, 4),
                    item("emerald", 1), sub("guild_coins", 2), empty(2)]),
        pool(0, 1, [scroll(2, ALL_BASE, 1, 2), item(f"{NS}:mana_crystal", 1), empty(3)]),
    ],
    "harpy_nest": [
        pool(3, 6, [item("feather", 6, 3, 9), sub("harpy_feathers", 4), item("gold_nugget", 4, 3, 9),
                    item("gold_ingot", 2, 1, 2), item("emerald", 3, 1, 3), item("iron_nugget", 2, 2, 6),
                    item("amethyst_shard", 2, 1, 4), item("string", 3, 1, 4), item("phantom_membrane", 1),
                    item("music_disc_cat", 1), item("golden_horse_armor", 1), item("egg", 2, 1, 3)]),
        pool(1, 1, [scroll(3, ["wind"], 1, 3), ess("wind", 2)]),
    ],
    "lizardman_village": [
        pool(3, 6, [item("cod", 5, 2, 6), item("salmon", 4, 1, 4), item("cooked_cod", 3, 1, 3),
                    item("tropical_fish", 2, 1, 2), item("pufferfish", 1), item("clay_ball", 4, 2, 8),
                    item("slime_ball", 3, 1, 3), item("lily_pad", 3, 1, 4), item("prismarine_shard", 2, 1, 4),
                    item("kelp", 2, 2, 6), item("emerald", 2, 1, 3), item("nautilus_shell", 1),
                    sub("guild_coins", 2), sub("lizard_scales", 3), item("fishing_rod", 1, fns=[dmg()])]),
        pool(0, 1, [scroll(3, ["water", "earth"], 1, 2), ess("water", 2), empty(2)]),
    ],
    "troll_bridge": [
        pool(4, 7, [item("gold_ingot", 4, 1, 3), item("gold_nugget", 4, 4, 12), item("emerald", 4, 2, 5),
                    item("iron_ingot", 3, 1, 3), item("bone", 4, 2, 6), item("rotten_flesh", 3, 2, 6),
                    item("iron_helmet", 1, fns=[dmg()]), item("iron_sword", 1, fns=[dmg()]),
                    item("leather_boots", 1, fns=[dmg()]), sub("guild_coins", 5), sub("troll_hides", 2), item("diamond", 1),
                    item("map", 1)]),
        pool(0, 1, [scroll(2, ["earth", "arcane"], 1, 2), ess("earth", 1), empty(3)]),
    ],
    "renegade_tower": [
        pool(1, 3, [scroll(1, ["shadow"], 1, 3)]),
        pool(3, 5, [item("book", 4, 1, 3), item("paper", 3, 1, 4), item("ink_sac", 3, 1, 3), item("bone", 3, 1, 4),
                    item("spider_eye", 2, 1, 2), item("fermented_spider_eye", 2), item("glass_bottle", 2, 1, 3),
                    item("redstone", 2, 1, 4), item("glowstone_dust", 2, 1, 4), item("soul_torch", 2, 1, 4),
                    item("black_candle", 2, 1, 3), ess("shadow", 3), item(f"{NS}:mana_crystal", 2, 1, 2),
                    item("ender_pearl", 1), item("experience_bottle", 2, 1, 3)]),
    ],
    "renegade_tower_sanctum": [
        pool(2, 3, [scroll(1, ["shadow"], 2, 4)]),
        pool(0, 1, [scroll(1, ["shadow"], 5, 5, legendary=True), empty(9)]),
        pool(3, 5, [ess("shadow", 5, 2, 4), item(f"{NS}:mana_crystal", 3, 2, 3), item("diamond", 2, 1, 2),
                    item("ender_pearl", 2, 1, 2), item("experience_bottle", 3, 3, 6), item("golden_apple", 1), BOOK,
                    item("crying_obsidian", 2, 1, 3)]),
        pool(0, 1, [sub("arcane_amulet", 1), sub("mana_ring", 1), empty(3)]),
    ],
}

# Sub-tabelas com itens de OUTROS módulos: carregadas só se o item existir (neoforge:item_exists).
SUBTABLES = {
    "guild_coins": ("guild_coin", 1, 5),
    "goblin_ears": ("goblin_ear", 1, 3),
    "orc_tusks": ("orc_tusk", 1, 2),
    "harpy_feathers": ("harpy_feather", 1, 4),
    "mana_ring": ("mana_ring", 1, 1),
    "arcane_amulet": ("arcane_amulet", 1, 1),
    "lizard_scales": ("lizard_scale", 1, 3),
    "troll_hides": ("troll_hide", 1, 2),
}


def gen_loot():
    for name, pools in CHESTS.items():
        write_json(os.path.join(D, f"loot_table/chests/{name}.json"), {
            "type": "minecraft:chest", "pools": pools, "random_sequence": f"{NS}:chests/{name}"})
    for name, (it, lo, hi) in SUBTABLES.items():
        write_json(os.path.join(D, f"loot_table/structures_c/{name}.json"), {
            "neoforge:conditions": [{"type": "neoforge:item_exists", "item": f"{NS}:{it}"}],
            "type": "minecraft:chest",
            "pools": [pool(1, 1, [item(f"{NS}:{it}", 1, lo, hi)])]})


# =================================================================================================
#  PRÉVIA ASCII
# =================================================================================================
def char_of(state):
    if state is None:
        return " "
    if state == AIR:
        return "."
    n = base_name(state).split(":", 1)[1]
    table = [("jigsaw", "J"), ("chest", "C"), ("barrel", "b"), ("bed", "B"), ("door", "D"), ("ladder", "H"),
             ("stairs", "/"), ("slab", "_"), ("fence", "+"), ("wall", "#"), ("pane", "|"), ("bars", "|"),
             ("lantern", "*"), ("torch", "*"), ("campfire", "F"), ("water", "~"), ("carpet", "-"), ("log", "O"),
             ("planks", "="), ("hay", "h"), ("wool", "w"), ("glass", "|"), ("rail", "r"), ("ore", "$"),
             ("skull", "@"), ("bone", "%"), ("spawner", "S"), ("counter", "K"), ("quest", "Q"), ("mud", "m")]
    for k, ch in table:
        if k in n:
            return ch
    return "X"


def preview(rel, s, layers):
    sx, sy, sz = s.size
    print(f"\n=== {rel} {s.size} ===")
    for y in layers:
        if y >= sy:
            continue
        print(f"-- y={y}")
        for z in range(sz):
            print("".join(char_of(s.get(x, y, z)) for x in range(sx)))


def main():
    show = "--preview" in sys.argv
    build_guild_hall_village()
    goblin_village("a")
    goblin_village("b")
    build_goblin_fortress()
    orc_camp("a")
    orc_camp("b")
    build_kobold_den()
    build_labyrinth()
    harpy_nest()
    lizardman_village()
    troll_bridge()
    renegade_tower()
    gen_worldgen()
    gen_loot()
    for rel, s, n in BUILT:
        print(f"{rel}: {s.size} {n} blocos, {len(s.entities)} entidades")
        if show:
            preview(rel, s, sorted({0, 1, 2, s.size[1] // 2}))


if __name__ == "__main__":
    main()
