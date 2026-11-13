"""
Gerador de assets/dados do modulo depths (v1.6 — Profundezas):
texturas (itens, blocos), modelos de item/bloco, modelos GeckoLib + animacoes (tools/gen/depths_geo.py),
estrutura jigsaw "Masmorra em Camadas" (entrada + 4 andares x 2 variantes + ninho do Verme-Rei), Geodo de Mana
(feature), spawns, loot tables, receitas, tags (merge), capitulo do diario e traducoes (src/main/lang/depths).

Rodar a partir da raiz do projeto:  py -I tools/gen/depths.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402
from structgen import Structure, Byte, Float, Int  # noqa: E402
import depths_geo  # noqa: E402
import depths_puzzles as PZ  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/depths")
LANG = os.path.join(ROOT, "src/main/lang/depths")
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
#  TEXTURAS
# =====================================================================================
M_OUT = H("#0E2A3A")
M_HI = H("#E8FFFF")
M_L = H("#9AF0FF")
M_M = H("#5AE0FF")
M_D = H("#2A8AB0")
M_DD = H("#1A5878")


def save_item(c, name):
    c.save(os.path.join(ASSETS, f"textures/item/{name}.png"))


def save_block(c, name):
    c.save(os.path.join(ASSETS, f"textures/block/{name}.png"))


def tex_shard():
    c = Canvas(16, 16)
    rows = ["................",
            "...........x....",
            "..........xhx...",
            ".........xhlmx..",
            "........xhlmmx..",
            ".......xhlmmdx..",
            "......xhlmmdx...",
            ".....xhlmmdx....",
            "....xhlmmdx.....",
            "....xlmmdx......",
            "...xlmmdx.......",
            "...xmmdx........",
            "...xmdx.........",
            "....xx..........",
            "................",
            "................"]
    c.from_rows(rows, {"x": M_OUT, "h": M_HI, "l": M_L, "m": M_M, "d": M_D})
    for (x, y) in ((13, 1), (2, 6), (12, 9)):
        c.set(x, y, M_HI)
    save_item(c, "depths_pure_mana_shard")


def tex_fang():
    c = Canvas(16, 16)
    rows = ["................",
            "................",
            "...xxxx.........",
            "..xffffxx.......",
            "..xfbbbbbxx.....",
            "...xxbbbbbbx....",
            ".....xxbbbbbx...",
            ".......xbbbbx...",
            "........xbbbbx..",
            "........xbbbsx..",
            ".........xbbsx..",
            ".........xbbsx..",
            "..........xbsx..",
            "..........xbx...",
            "...........x....",
            "................"]
    c.from_rows(rows, {"x": H("#2A1A10"), "f": H("#7A3A2A"), "b": H("#EDE3C8"), "s": H("#B8A888")})
    c.set(6, 5, H("#FFFFFF")); c.set(8, 6, H("#FFFFFF"))
    c.set(11, 9, M_M); c.set(11, 10, M_L)
    save_item(c, "depths_worm_king_fang")


def tex_dragonslayer():
    """Espadao 32x32: lamina larga de ferro bruto em diagonal, ponta romba, rebites e cabo de couro."""
    c = Canvas(32, 32)
    iron, iron_l, iron_d, edge = H("#5A5A62"), H("#8A8A94"), H("#34343A"), H("#B0B0BA")
    for i in range(22):
        x, y = 8 + i, 23 - i
        for w in range(-2, 3):
            col = iron if abs(w) < 2 else iron_d
            c.set(x + w, y + w, col)
            c.set(x + w + 1, y + w, col)
        c.set(x + 2, y - 2, edge if i % 4 else iron_l)
        c.set(x + 3, y - 2, iron_l)
    # ponta romba (quadrada)
    for (x, y) in ((29, 1), (30, 1), (30, 2), (29, 2), (28, 0)):
        c.set(x, y, iron_d)
    # rebites e manchas de sangue seco
    for (x, y) in ((11, 20), (15, 16), (19, 12), (23, 8)):
        c.set(x, y, iron_l)
        c.set(x + 1, y + 1, iron_d)
    for (x, y) in ((17, 15), (18, 15), (21, 11), (13, 19)):
        c.set(x, y, H("#5A1A14"))
    # guarda
    for i in range(-4, 5):
        c.set(8 + i, 23 + i, H("#2A2A30"))
        c.set(9 + i, 23 + i, H("#44444C"))
    # cabo
    for i in range(7):
        x, y = 6 - i, 25 + i
        c.set(x, y, H("#3A2418") if i % 2 else H("#5E3A24"))
        c.set(x + 1, y, H("#2A180E"))
        c.set(x, y - 1, H("#4A2C1A"))
    c.set(0, 31, H("#2A2A30")); c.set(1, 31, H("#44444C"))
    c.outline(H("#101014"))
    c.noise(0.04, 7)
    save_item(c, "depths_dragonslayer")


def tex_goddess_knife():
    """Adaga negra com runas azuis brilhantes (hieroglifos da deusa) e cabo escuro."""
    c = Canvas(16, 16)
    black, black_l, rune = H("#1A1A24"), H("#34344A"), H("#4AB8FF")
    for i in range(8):
        x, y = 6 + i, 9 - i
        c.set(x, y, black)
        c.set(x + 1, y, black_l)
        c.set(x, y - 1, black_l if i < 7 else black)
    for (x, y) in ((8, 7), (10, 5), (12, 3), (9, 6)):
        c.set(x, y, rune)
    c.set(13, 1, H("#8AD8FF"))
    for (x, y) in ((4, 9), (5, 10), (6, 11), (3, 8), (7, 12)):
        c.set(x, y, H("#C8A03A"))
    for i in range(4):
        c.set(4 - i + 1, 11 + i, H("#3A2A4A") if i % 2 else H("#5A3A6A"))
    c.set(1, 15, H("#C8A03A"))
    c.outline(H("#06060A"))
    save_item(c, "depths_goddess_knife")


def crystal_bg(seed, base, light, dark, spark=True):
    rnd = random.Random(seed)
    c = Canvas(16, 16, base)
    # facetas: faixas diagonais claras/escuras
    for k in range(-16, 32, 5):
        for i in range(16):
            x, y = k + i, i
            if 0 <= x < 16:
                c.set(x, y, light)
                if x + 1 < 16:
                    c.set(x + 1, y, lighten(base, 0.15))
    for k in range(-14, 32, 7):
        for i in range(16):
            x, y = k + 16 - i, i
            if 0 <= x < 16:
                c.set(x, y, dark)
    c.noise(0.07, seed)
    if spark:
        for _ in range(5):
            c.set(rnd.randrange(16), rnd.randrange(16), M_HI)
    return c


def tex_blocks():
    save_block(crystal_bg(11, H("#3A9AC8"), M_L, M_DD), "depths_mana_geode_block")
    c = crystal_bg(23, H("#2A7AA8"), H("#6AC8F0"), H("#123E58"), spark=False)
    for (x, y) in ((3, 3), (11, 4), (6, 10), (12, 12), (2, 13)):
        c.set(x, y, M_HI)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.set(x + dx, y + dy, M_L)
    save_block(c, "depths_budding_mana_crystal")
    # broto (cross): cristais baixos
    c = Canvas(16, 16)
    for (x0, h, w) in ((5, 6, 2), (8, 8, 2), (10, 5, 2)):
        for y in range(16 - h, 16):
            for x in range(x0, x0 + w):
                c.set(x, y, M_M if x == x0 else M_D)
        c.set(x0, 16 - h, M_HI)
    save_block(c, "depths_mana_bud")
    # aglomerado (cross): cristais altos
    c = Canvas(16, 16)
    for (x0, h, w) in ((2, 8, 2), (5, 13, 3), (9, 15, 3), (13, 9, 2)):
        for y in range(16 - h, 16):
            for x in range(x0, x0 + w):
                c.set(x, y, M_L if x == x0 else (M_M if x == x0 + 1 else M_D))
        c.set(x0, 16 - h, M_HI)
        if h > 10:
            c.set(x0 + 1, 16 - h + 1, M_HI)
    save_block(c, "depths_mana_cluster")


# =====================================================================================
#  MODELOS DE ITEM/BLOCO
# =====================================================================================
def block_models():
    bs, bm, im = (os.path.join(ASSETS, p) for p in ("blockstates", "models/block", "models/item"))
    for b in ("depths_mana_geode_block", "depths_budding_mana_crystal"):
        write_json(os.path.join(bs, f"{b}.json"), {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write_json(os.path.join(bm, f"{b}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
        write_json(os.path.join(im, f"{b}.json"), {"parent": f"{NS}:block/{b}"})
    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
           "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
    for b in ("depths_mana_bud", "depths_mana_cluster"):
        write_json(os.path.join(bs, f"{b}.json"), {"variants": {
            f"facing={f}": dict({"model": f"{NS}:block/{b}"}, **r) for f, r in rot.items()}})
        write_json(os.path.join(bm, f"{b}.json"), {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{b}"},
                                                   "render_type": "minecraft:cutout"})
        write_json(os.path.join(im, f"{b}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{b}"}})


def item_models():
    im = os.path.join(ASSETS, "models/item")
    for i in ("depths_pure_mana_shard", "depths_worm_king_fang"):
        write_json(os.path.join(im, f"{i}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{i}"}})
    write_json(os.path.join(im, "depths_goddess_knife.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/depths_goddess_knife"}})
    # Matadora: textura 32x32 e escala MAIOR na mao (o espadao fica enorme em terceira pessoa).
    write_json(os.path.join(im, "depths_dragonslayer.json"), {
        "parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/depths_dragonslayer"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 9, 1.5], "scale": [1.7, 1.7, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 9, 1.5], "scale": [1.7, 1.7, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 5.2, 1.13], "scale": [1.1, 1.1, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 5.2, 1.13], "scale": [1.1, 1.1, 0.68]}}})
    for e in ("depths_worm_king", "depths_burrower", "depths_lost_delver"):
        write_json(os.path.join(im, f"{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  ESTRUTURA — Masmorra em Camadas
# =====================================================================================
AIR = "minecraft:air"
TRAVEL_STONE = NS + ":travel_stone"
LADDER = "minecraft:ladder[facing=south,waterlogged=false]"
FW, FH = 31, 9          # andar: 3x3 salas de 9x9 (paredes em 0/10/20/30), piso y0, teto y8
LW, LH = 33, 18         # ninho do Verme-Rei
EW, EH, EG = 11, 34, 20  # entrada: EG = nivel do chao (topo do terreno); v1.0: mais alta por causa do obelisco


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
        self.rnd = random.Random("depths/" + name)

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return mc(s)
        return mc(weighted[-1][0])

    def fillp(self, x0, y0, z0, x1, y1, z1, weighted):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, self.pick(weighted))

    def chest(self, x, y, z, loot, facing="north", barrel=False):
        state = st("barrel", facing="up", open="false") if barrel else st("chest", facing=facing, type="single", waterlogged="false")
        self.set(x, y, z, state, nbt={"LootTable": f"{NS}:chests/{loot}"})

    def sign(self, x, y, z, facing, keys, color="white"):
        msgs = [json.dumps({"translate": k}) for k in keys] + ['""'] * (4 - len(keys))
        self.set(x, y, z, st("spruce_wall_sign", facing=facing, waterlogged="false"), nbt={
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": Byte(1)},
            "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)},
            "is_waxed": Byte(1)})

    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        nbt = {"PersistenceRequired": Byte(1), "Rotation": [Float(yaw), Float(0.0)]}
        if extra:
            nbt.update(extra)
        self.entity(x, y, z, eid if ":" in eid else f"{NS}:{eid}", nbt)

    def spawner(self, x, y, z, eid):
        # Regras próprias de luz: funciona com luz de bloco até 11 (lampiões/líquen por perto não travam o gerador).
        self.set(x, y, z, "minecraft:spawner", nbt={
            "SpawnData": {"entity": {"id": eid},
                          "custom_spawn_rules": {"block_light_limit": {"min_inclusive": Int(0), "max_inclusive": Int(11)}}},
            "Delay": Int(40), "MinSpawnDelay": Int(240), "MaxSpawnDelay": Int(640), "SpawnCount": Int(2),
            "MaxNearbyEntities": Int(4), "RequiredPlayerRange": Int(14), "SpawnRange": Int(4)})

    def arrow_trap(self, x, z):
        """Placa de pressão em cima de um ejetor virado para cima, no piso (y0): a flecha sai por baixo de quem pisa."""
        self.set(x, 0, z, st("dispenser", facing="up", triggered="false"),
                 nbt={"Items": [{"Slot": Byte(0), "id": "minecraft:arrow", "count": Int(16)}]})
        self.set(x, 1, z, st("stone_pressure_plate", powered="false"))


THEMES = {
    1: {"wall": [("stone_bricks", 6), ("cracked_stone_bricks", 2), ("mossy_stone_bricks", 2), ("cobblestone", 1), ("andesite", 1)],
        "floor": [("stone_bricks", 4), ("cobblestone", 3), ("gravel", 1), ("mossy_cobblestone", 1)],
        "ceil": [("stone", 4), ("cobblestone", 2), ("andesite", 1)],
        "accent": "chiseled_stone_bricks", "light": st("lantern", hanging="true", waterlogged="false"),
        "spawners": ["minecraft:zombie", "minecraft:skeleton", f"{NS}:depths_lost_delver"],
        "mobs": ["minecraft:zombie", f"{NS}:depths_lost_delver", "minecraft:skeleton"], "sign": "oak"},
    2: {"wall": [("tuff_bricks", 5), ("tuff", 3), ("polished_tuff", 1), ("cobblestone", 1)],
        "floor": [("tuff", 3), ("gravel", 2), ("cobblestone", 2), ("coarse_dirt", 1)],
        "ceil": [("tuff", 3), ("stone", 2)],
        "accent": "chiseled_tuff_bricks", "light": st("lantern", hanging="true", waterlogged="false"),
        "spawners": ["minecraft:cave_spider", "minecraft:spider", f"{NS}:depths_burrower"],
        "mobs": ["minecraft:spider", f"{NS}:depths_burrower", "minecraft:zombie"]},
    3: {"wall": [("deepslate_bricks", 5), ("cracked_deepslate_bricks", 2), ("cobbled_deepslate", 2), ("deepslate", 1)],
        "floor": [("cobbled_deepslate", 3), ("deepslate", 2), ("gravel", 1), ("tuff", 1)],
        "ceil": [("deepslate", 4), ("cobbled_deepslate", 1)],
        "accent": "chiseled_deepslate", "light": st("lantern", hanging="true", waterlogged="false"),
        "spawners": [f"{NS}:depths_lost_delver", f"{NS}:depths_burrower", "minecraft:skeleton"],
        "mobs": [f"{NS}:depths_lost_delver", f"{NS}:depths_burrower", "minecraft:skeleton"]},
    4: {"wall": [("deepslate_tiles", 5), ("cracked_deepslate_tiles", 2), ("polished_blackstone_bricks", 2), ("sculk", 0.6)],
        "floor": [("deepslate_tiles", 3), ("polished_deepslate", 2), ("sculk", 1)],
        "ceil": [("deepslate", 3), ("blackstone", 1)],
        "accent": "chiseled_polished_blackstone", "light": st("soul_lantern", hanging="true", waterlogged="false"),
        "spawners": ["minecraft:skeleton", f"{NS}:depths_lost_delver", "minecraft:stray", f"{NS}:depths_burrower"],
        "mobs": [f"{NS}:depths_lost_delver", "minecraft:stray", f"{NS}:depths_burrower", "minecraft:skeleton"]},
}


def room_interior(i, j):
    return 10 * i + 1, 10 * j + 1, 10 * i + 9, 10 * j + 9


def room_center(i, j):
    return 10 * i + 5, 10 * j + 5


def floor_ends(n):
    """(sala de chegada, sala de descida): alternam entre cantos para os andares ficarem empilhados."""
    return ((0, 0), (2, 2)) if n % 2 == 1 else ((2, 2), (0, 0))


def maze_edges(rnd, start, skip=()):
    """Labirinto entre as salas (arvore + 2 atalhos). As salas de 'skip' (enigmas) entram no fim como becos: UMA porta."""
    rooms = [(i, j) for i in range(3) for j in range(3) if (i, j) not in skip]
    seen, stack, edges = {start}, [start], set()
    while stack:
        cur = stack[-1]
        nbrs = [(cur[0] + dx, cur[1] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        nbrs = [r for r in nbrs if r in rooms and r not in seen]
        if not nbrs:
            stack.pop()
            continue
        nxt = rnd.choice(nbrs)
        edges.add(tuple(sorted((cur, nxt))))
        seen.add(nxt)
        stack.append(nxt)
    all_edges = [tuple(sorted(((i, j), (i + dx, j + dz)))) for i in range(3) for j in range(3)
                 for dx, dz in ((1, 0), (0, 1)) if i + dx < 3 and j + dz < 3]
    extra = [e for e in all_edges if e not in edges and e[0] not in skip and e[1] not in skip]
    rnd.shuffle(extra)
    edges.update(extra[:2])
    leaf_door = {}
    for r in skip:
        nbrs = [(r[0] + dx, r[1] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        nbrs = [n for n in nbrs if n in rooms]
        n = rnd.choice(nbrs)
        edges.add(tuple(sorted((r, n))))
        leaf_door[r] = n
    return (edges, leaf_door) if skip else edges


def open_door(s, a, b, th):
    (i0, j0), (i1, j1) = a, b
    if i0 != i1:
        x = 10 * max(i0, i1)
        z0 = 10 * j0 + 4
        s.fill(x, 1, z0, x, 4, z0 + 2, AIR)
        s.set(x, 5, z0 + 1, mc(th["accent"]))
    else:
        z = 10 * max(j0, j1)
        x0 = 10 * i0 + 4
        s.fill(x0, 1, z, x0 + 2, 4, z, AIR)
        s.set(x0 + 1, 5, z, mc(th["accent"]))


def free_spots(s, room, avoid=()):
    """Posições do piso livres na sala (longe das portas e do centro, se pedido)."""
    x0, z0, x1, z1 = room_interior(*room)
    out = []
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x, z) in avoid or s.get(x, 1, z) not in (None, AIR):
                continue
            if abs(x - (x0 + 4)) <= 1 or abs(z - (z0 + 4)) <= 1:
                continue
            out.append((x, z))
    s.rnd.shuffle(out)
    return out


def deco_room(s, n, room):
    """Detalhes por andar: teias, ossos, liquens, trilhos, sculk."""
    x0, z0, x1, z1 = room_interior(*room)
    r = s.rnd
    corners = [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]
    for (x, z) in corners:
        if s.get(x, 1, z) not in (None, AIR):
            continue
        if n <= 2 and r.random() < 0.7:
            s.set(x, 7, z, "minecraft:cobweb")
            if n == 2:
                s.set(x, 6, z, "minecraft:cobweb")
                if r.random() < 0.5:
                    s.set(x, 1, z, "minecraft:cobweb")
        elif n >= 3 and r.random() < 0.5:
            s.set(x, 1, z, st("bone_block", axis="y"))
            if r.random() < 0.5:
                s.set(x, 2, z, st("skeleton_skull", rotation=str(r.randrange(16))))
    for (x, z) in free_spots(s, room)[:2]:
        if n in (2, 3):
            s.set(x, 1, z, st("glow_lichen", down="true", up="false", north="false", south="false", east="false",
                              west="false", waterlogged="false"))
        elif n == 4:
            s.set(x, 0, z, "minecraft:sculk")
            s.set(x, 1, z, st("sculk_vein", down="true", up="false", north="false", south="false", east="false",
                              west="false", waterlogged="false"))
        elif r.random() < 0.5:
            s.set(x, 1, z, st("candle", candles=str(r.randint(1, 3)), lit="true", waterlogged="false"))
    if n == 3 and r.random() < 0.6:
        # trilhos abandonados atravessando a sala
        zc = z0 + 2
        for x in range(x0, x1 + 1):
            if s.get(x, 1, zc) in (None, AIR) and r.random() < 0.85:
                s.set(x, 1, zc, st("rail", shape="east_west", waterlogged="false"))
        for x in (x0, x1):
            s.fill(x, 1, z0, x, 6, z0, st("oak_log", axis="y"))
        s.fill(x0, 7, z0, x1, 7, z0, st("oak_planks"))


def ladder_up(s, room, th):
    """Chegada: escada de mão do teto ao chão, presa a um pilar (lado norte)."""
    cx, cz = room_center(*room)
    s.fill(cx, 1, cz - 1, cx, FH - 2, cz - 1, mc(th["accent"]))
    s.fill(cx, 1, cz, cx, FH - 2, cz, LADDER)
    s.jigsaw(cx, FH - 1, cz, "up_north", f"{NS}:depths_up", "minecraft:empty", "minecraft:empty", LADDER, joint="aligned")


def ladder_down(s, room, th, pool):
    """Descida: buraco no piso com escada de mão (o conector jigsaw vira o primeiro degrau)."""
    cx, cz = room_center(*room)
    s.fill(cx, 0, cz - 1, cx, 3, cz - 1, mc(th["accent"]))
    s.set(cx, 4, cz - 1, th["light"].replace("hanging=true", "hanging=false"))
    s.fill(cx, 1, cz, cx, 2, cz, LADDER)
    s.jigsaw(cx, 0, cz, "down_north", f"{NS}:depths_down", f"{NS}:depths_up", pool, LADDER, joint="aligned")
    # mureta em volta do buraco (aberta ao sul)
    for (dx, dz) in ((-1, 0), (1, 0), (-1, -1), (1, -1)):
        s.set(cx + dx, 1, cz + dz, st("cobblestone_wall", up="true", north="none", south="none", east="none", west="none",
                                      waterlogged="false"))


def build_room(s, n, room, kind, th):
    x0, z0, x1, z1 = room_interior(*room)
    cx, cz = room_center(*room)
    r = s.rnd
    loot = f"depths_floor_{n}"
    if kind == "spawner":
        s.set(cx, 0, cz, mc(th["accent"]))
        s.spawner(cx, 1, cz, r.choice(th["spawners"]))
        for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            s.set(cx + dx, 1, cz + dz, "minecraft:cobweb" if n <= 2 else st("bone_block", axis="y"))
        if r.random() < 0.4:
            x, z = free_spots(s, room)[0]
            s.chest(x, 1, z, loot, r.choice(("north", "south", "east", "west")))
    elif kind == "treasure":
        s.fill(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, mc(th["accent"]))
        if n >= 3 and r.random() < 0.3:
            s.mob(cx + 0.5, 1, cz + 0.5, "mimic", r.choice((0.0, 90.0, 180.0, 270.0)), {"Disguised": Byte(1)})
            x, z = free_spots(s, room)[0]
            s.chest(x, 1, z, loot, "south")
        else:
            s.chest(cx, 1, cz, loot, r.choice(("north", "south", "east", "west")))
        if n == 4:
            s.set(cx - 1, 1, cz - 1, "minecraft:gold_block")
        for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if r.random() < 0.6:
                s.set(cx + dx, 1, cz + dz, st("candle", candles="3", lit="true", waterlogged="false"))
        if n >= 2:
            for (x, z) in free_spots(s, room)[:2 + n]:
                s.arrow_trap(x, z)
    elif kind == "trap":
        for (x, z) in free_spots(s, room)[:8 + n * 2]:
            if n >= 3 and r.random() < 0.3:
                s.set(x, 0, z, "minecraft:magma_block")
            else:
                s.arrow_trap(x, z)
        s.chest(cx, 1, cz, loot, "south")
        s.set(cx, 0, cz, mc(th["accent"]))
    elif kind == "ambush":
        spots = free_spots(s, room)
        for k in range(min(len(spots), 2 + n // 2)):
            x, z = spots[k]
            s.mob(x + 0.5, 1, z + 0.5, r.choice(th["mobs"]), r.uniform(0, 360))
        s.set(cx, 1, cz, st("bone_block", axis="y"))
        s.set(cx, 2, cz, st("skeleton_skull", rotation=str(r.randrange(16))))
    elif kind == "special":
        special_room(s, n, room, th)
    elif kind == "puzzle":
        puzzle_room(s, n, room, th)


# v1.0 — quebra-cabecas: cada andar tem UMA sala de enigma (tipo diferente por andar e por variante do andar)
PUZZLES = {"a": {1: "levers", 2: "tiles", 3: "light", 4: "sequence"},
           "b": {1: "tiles", 2: "sequence", 3: "levers", 4: "light"}}


def puzzle_room(s, n, room, th):
    cx, cz = room_center(*room)
    accent = mc(th["accent"])
    loot = f"{NS}:chests/depths_puzzle" + ("_deep" if n >= 3 else "")
    kind = PUZZLES[s.var][n]
    if kind == "levers":
        PZ.levers_room(s, cx, 1, cz, loot, accent, n=5 if n >= 3 else 4)
    elif kind == "tiles":
        PZ.tiles_room(s, cx, 1, cz, loot, accent, s.rnd)
    elif kind == "light":
        PZ.light_room(s, cx, 1, cz, loot, accent)
    else:
        PZ.sequence_room(s, cx, 1, cz, loot, accent, FH - 1)
    x0, z0, x1, z1 = room_interior(*room)
    for (x, z) in ((x0, z0), (x1, z1), (x0, z1), (x1, z0)):
        s.set(x, FH - 2, z, th["light"])
    s.puzzle = room


# v1.0 r9 — Enigmas de raciocinio (feature/depths/puzzle/enigma): duas salas-beco por andar, em corrente
# (a 1a, ao ser resolvida, destrava e da uma pista da 2a). O tipo e sorteado pelo Java SEM repetir na masmorra
# (Slot 0..7 = 8 tipos); o nivel cresce com a profundidade.
ENIGMA_ROOMS = ((2, 0), (0, 2))
ENIGMA_TIERS = {1: (1, 1), 2: (1, 2), 3: (2, 3), 4: (3, 3)}


def enigma_room(s, n, room, nbr, idx, th):
    """Sala-beco do enigma: o Selo fica na fileira da porta (coluna 1, sem tapar a porta) apontando para dentro.
    Igual ao Frame do Java: u anda para a direita de quem entra (u0 = 1), v para dentro; area u 0..8 x v 1..7."""
    x0, z0, x1, z1 = room_interior(*room)
    (i, j), (ni, nj) = room, nbr
    if ni < i:
        facing, cx, cz = "east", x0, z0 + 1
    elif ni > i:
        facing, cx, cz = "west", x1, z1 - 1
    elif nj < j:
        facing, cx, cz = "south", x1 - 1, z0
    else:
        facing, cx, cz = "north", x0 + 1, z1
    tier = ENIGMA_TIERS[n][idx]
    s.set(cx, 1, cz, f"{NS}:depths_puzzle_core[facing={facing},solved=false]", nbt={"Enigma": {
        "Type": "random", "Tier": Int(tier), "U0": Int(1), "Slot": Int((n - 1) * 2 + idx),
        "Chain": f"depths_f{n}", "Role": Int(idx + 1)}})
    for (x, z) in ((x0, z0), (x1, z1), (x0, z1), (x1, z0)):
        s.set(x, FH - 2, z, th["light"])


def special_room(s, n, room, th):
    x0, z0, x1, z1 = room_interior(*room)
    cx, cz = room_center(*room)
    r = s.rnd
    if n == 1:
        # Biblioteca esquecida: estantes nas paredes, pergaminhos no baú.
        for x in range(x0, x1 + 1):
            for y in (1, 2, 3):
                if r.random() < 0.8:
                    s.set(x, y, z0, "minecraft:bookshelf")
                if r.random() < 0.8:
                    s.set(x, y, z1, "minecraft:bookshelf")
        s.set(cx, 1, cz, st("lectern", facing="south", has_book="false", powered="false"))
        s.chest(cx + 2, 1, cz, "depths_floor_1", "west")
        s.set(cx - 2, 1, cz, st("candle", candles="4", lit="true", waterlogged="false"))
        for (x, z) in free_spots(s, room)[:4]:
            s.set(x, 7, z, "minecraft:cobweb")
    elif n == 2:
        # Acampamento abandonado de aventureiros.
        s.set(cx, 1, cz, st("campfire", lit="false", facing="north", signal_fire="false", waterlogged="false"))
        s.fill(cx + 2, 1, cz - 1, cx + 2, 1, cz + 1, "minecraft:white_carpet")
        s.fill(cx - 2, 1, cz - 1, cx - 2, 1, cz + 1, "minecraft:brown_carpet")
        s.chest(cx, 1, cz + 3, "depths_floor_2", barrel=True)
        s.set(cx + 1, 1, cz + 3, "minecraft:barrel[facing=up,open=false]")
        s.mob(cx - 1.5, 1, cz - 2.5, "depths_lost_delver", 180.0)
        for (x, z) in free_spots(s, room)[:6]:
            s.set(x, 1 + r.randrange(6), z, "minecraft:cobweb")
    elif n == 3:
        # Gruta de cristal: paredes de Geodo de Mana, um Cristal Germinante no pedestal (fonte renovável!).
        for x in range(x0, x1 + 1):
            for y in range(1, 8):
                for z in (z0, z1):
                    if r.random() < 0.55:
                        s.set(x, y, z, f"{NS}:depths_mana_geode_block")
        s.set(cx, 0, cz, f"{NS}:depths_mana_geode_block")
        s.set(cx, 1, cz, f"{NS}:depths_budding_mana_crystal")
        s.set(cx, 2, cz, st(f"{NS}:depths_mana_cluster", facing="up", waterlogged="false"))
        for (dx, dz) in ((-2, -2), (2, 2), (-2, 2), (2, -2)):
            s.set(cx + dx, 1, cz + dz, st(f"{NS}:depths_mana_cluster", facing="up", waterlogged="false"))
        for (dx, dz) in ((-3, 0), (3, 0), (0, 3)):
            s.set(cx + dx, 1, cz + dz, st("amethyst_cluster", facing="up", waterlogged="false"))
        s.chest(cx, 1, cz - 3, "depths_floor_3", "south")
    else:
        # Salão dos Mímicos: três "baús", só um é verdadeiro.
        spots = [(cx - 3, cz), (cx, cz), (cx + 3, cz)]
        real = r.randrange(3)
        for k, (x, z) in enumerate(spots):
            s.set(x, 0, z, "minecraft:gold_block")
            if k == real:
                s.chest(x, 1, z, "depths_floor_4", "south")
            else:
                s.mob(x + 0.5, 1, z + 0.5, "mimic", 0.0, {"Disguised": Byte(1)})
        s.sign(cx, 3, z0, "south", [f"sign.{NS}.depths.mimic_1", f"sign.{NS}.depths.mimic_2", f"sign.{NS}.depths.mimic_3"])


def floor_piece(n, var):
    th = THEMES[n]
    s = B(f"floor{n}_{var}", FW, FH, FW)
    s.var, s.puzzle = var, None
    s.fillp(0, 0, 0, FW - 1, 0, FW - 1, th["floor"])
    s.fillp(0, FH - 1, 0, FW - 1, FH - 1, FW - 1, th["ceil"])
    s.fillp(0, 1, 0, FW - 1, FH - 2, FW - 1, th["wall"])
    rooms = [(i, j) for i in range(3) for j in range(3)]
    for room in rooms:
        x0, z0, x1, z1 = room_interior(*room)
        s.fill(x0, 1, z0, x1, FH - 2, z1, AIR)
    a, d = floor_ends(n)
    edges, leaf_door = maze_edges(s.rnd, a, skip=ENIGMA_ROOMS)
    for e in edges:
        open_door(s, e[0], e[1], th)
    ladder_up(s, a, th)
    nxt = f"{NS}:depths_dungeon/floor{n + 1}" if n < 4 else f"{NS}:depths_dungeon/lair"
    ladder_down(s, d, th, nxt)
    # placas: nome do andar na chegada, aviso na descida (presas ao pilar, face leste)
    ax, az = room_center(*a)
    s.sign(ax + 1, 3, az - 1, "east", [f"sign.{NS}.depths.floor_{n}_1", f"sign.{NS}.depths.floor_{n}_2"])
    s.set(ax + 1, FH - 2, az + 1, th["light"])
    dx, dz = room_center(*d)
    s.sign(dx + 1, 3, dz - 1, "east", [f"sign.{NS}.depths.down_1", f"sign.{NS}.depths.down_{'lair' if n == 4 else 'next'}"])
    kinds = ["spawner", "treasure", "puzzle", "trap", "special"]
    s.rnd.shuffle(kinds)
    others = [rm for rm in rooms if rm not in (a, d) and rm not in ENIGMA_ROOMS]
    for room, kind in zip(others, kinds):
        build_room(s, n, room, kind, th)
    for idx, room in enumerate(ENIGMA_ROOMS):
        enigma_room(s, n, room, leaf_door[room], idx, th)
    for room in rooms:
        if room != s.puzzle and room not in ENIGMA_ROOMS:  # salas de enigma ficam limpas
            deco_room(s, n, room)
    name = f"floor{n}_{var}"
    cnt = s.save(os.path.join(DATA, f"structure/depths_dungeon/{name}.nbt"), keep_air=True)
    print("  estrutura depths_dungeon/%s %d blocos" % (name, cnt))


def entrance():
    s = B("entrance", EW, EH, EW)
    G = EG
    cx = cz = 5
    disc = [(x, z) for x in range(EW) for z in range(EW) if (x - cx) ** 2 + (z - cz) ** 2 <= 5.4 ** 2]
    # limpa o ar acima da ruína e faz a fundação
    for (x, z) in disc:
        for y in range(G + 1, EH):
            s.set(x, y, z, AIR)
        for y in range(G - 3, G):
            s.set(x, y, z, s.pick([("stone", 3), ("cobblestone", 2), ("mossy_cobblestone", 1)]))
        s.set(x, G, z, s.pick([("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2), ("stone_bricks", 3), ("mossy_cobblestone", 1)]))
    # poço: anel de pedra até o fundo, interior 3x3 com escada de mão
    s.fillp(3, 0, 3, 7, G - 1, 7, [("stone_bricks", 4), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("cobblestone", 1)])
    s.fill(4, 1, 4, 6, G, 6, AIR)
    s.fill(4, 0, 4, 6, 0, 6, "minecraft:stone_bricks")
    s.fill(cx, 0, cz - 1, cx, G + 3, cz - 1, "minecraft:mossy_stone_bricks")
    s.fill(cx, 1, cz, cx, G + 1, cz, LADDER)
    s.jigsaw(cx, 0, cz, "down_north", f"{NS}:depths_down", f"{NS}:depths_up", f"{NS}:depths_dungeon/floor1", LADDER, joint="aligned")
    # luz embutida na parede do poço
    for y in range(3, G - 2, 6):
        s.set(3, y, cz, "minecraft:shroomlight")
        s.set(7, y + 3, cz, "minecraft:shroomlight")
    # borda do poço em degraus (lajes) e pilar com lampião
    for x in range(3, 8):
        for z in range(3, 8):
            if (x in (3, 7) or z in (3, 7)) and (x, z) != (cx, cz - 1):
                s.set(x, G + 1, z, st("stone_brick_slab", type="bottom", waterlogged="false"))
    s.set(cx, G + 4, cz - 1, st("lantern", hanging="false", waterlogged="false"))
    s.sign(cx, G + 2, cz - 2, "north", [f"sign.{NS}.depths.entrance_1", f"sign.{NS}.depths.entrance_2",
                                       f"sign.{NS}.depths.entrance_3"], "orange")
    # pilares quebrados nos cantos + arco parcial
    for (px, pz) in ((1, 1), (9, 1), (1, 9), (9, 9)):
        h = s.rnd.randint(3, 6)
        s.fillp(px, G + 1, pz, px, G + h, pz, [("stone_bricks", 3), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2)])
        s.set(px, G + h + 1, pz, st("stone_brick_slab", type="bottom", waterlogged="false"))
    for x in range(1, 10):
        if s.rnd.random() < 0.6:
            s.set(x, G + 6, 1, s.pick([("stone_bricks", 2), ("mossy_stone_bricks", 2)]))
    # v1.0 — obelisco rachado com fogueira de sinal no topo: a fumaça alta mostra a masmorra de longe
    ox, oz = 9, 9
    bricks = [("stone_bricks", 3), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2)]
    for (dx, dz) in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
        s.fillp(ox + dx, G - 3, oz + dz, ox + dx, G + 2, oz + dz, bricks)
    s.fillp(ox, G + 3, oz, ox, G + 10, oz, bricks)
    for y in (G + 3, G + 7):
        s.set(ox, y, oz, "minecraft:chiseled_stone_bricks")
    s.set(ox - 1, G + 3, oz, st("stone_brick_stairs", facing="east", half="bottom", shape="straight", waterlogged="false"))
    s.set(ox, G + 3, oz - 1, st("stone_brick_stairs", facing="south", half="bottom", shape="straight", waterlogged="false"))
    s.set(ox, G + 11, oz, "minecraft:hay_block[axis=y]")
    s.set(ox, G + 12, oz, st("campfire", lit="true", facing="north", signal_fire="true", waterlogged="false"))
    for y in range(G + 1, EH):
        for (dx, dz) in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
            if s.get(ox + dx, y, oz + dz) is None:
                s.set(ox + dx, y, oz + dz, AIR)
    for (x, z) in ((2, 3), (8, 7), (3, 8)):
        s.set(x, G + 1, z, st("mossy_cobblestone_wall", up="true", north="none", south="none", east="none", west="none",
                              waterlogged="false"))
    s.set(8, G + 1, 4, TRAVEL_STONE)
    cnt = s.save(os.path.join(DATA, "structure/depths_dungeon/entrance.nbt"), keep_air=True)
    print("  estrutura depths_dungeon/entrance %d blocos" % cnt)


def lair():
    s = B("lair", LW, LH, LW)
    rock = [("deepslate", 5), ("tuff", 2), ("cobbled_deepslate", 2), ("stone", 1)]
    s.fillp(0, 0, 0, LW - 1, LH - 1, LW - 1, rock)
    c = 16
    R = 14.5

    def radius(y):
        if y <= 9:
            return R
        return math.sqrt(max(0.0, R * R - ((y - 9) * 2.1) ** 2))

    for y in range(2, LH - 1):
        rr = radius(y)
        for x in range(LW):
            for z in range(LW):
                if (x - c) ** 2 + (z - c) ** 2 <= rr * rr:
                    s.set(x, y, z, AIR)
    for x in range(LW):
        for z in range(LW):
            d2 = (x - c) ** 2 + (z - c) ** 2
            if d2 <= R * R:
                s.set(x, 1, z, s.pick([("deepslate_tiles", 3), ("cobbled_deepslate", 3), ("rooted_dirt", 1),
                                       ("coarse_dirt", 2), ("tuff", 1)]) if d2 > 16 else
                      s.pick([("rooted_dirt", 2), ("coarse_dirt", 2), ("mud", 1)]))
    # estalactites no teto e estalagmites junto às paredes
    for _ in range(26):
        a = s.rnd.random() * math.pi * 2
        rr = s.rnd.uniform(2.0, 12.0)
        x, z = int(round(c + math.cos(a) * rr)), int(round(c + math.sin(a) * rr))
        top = LH - 2
        while top > 2 and s.get(x, top, z) != AIR:
            top -= 1
        if top <= 6:
            continue
        ln = s.rnd.randint(1, 3)
        parts = (["base", "frustum", "tip"] if ln == 3 else ["frustum", "tip"] if ln == 2 else ["tip"])
        for k, th in enumerate(parts):
            s.set(x, top - k, z, st("pointed_dripstone", thickness=th, vertical_direction="down", waterlogged="false"))
    for k in range(12):
        a = math.pi * 2 * k / 12 + 0.2
        x, z = int(round(c + math.cos(a) * 12.5)), int(round(c + math.sin(a) * 12.5))
        if (x, z) in ((6, 6),):
            continue
        h = s.rnd.randint(1, 3)
        parts = (["base", "frustum", "tip"] if h == 3 else ["frustum", "tip"] if h == 2 else ["tip"])
        for i, th in enumerate(parts):
            s.set(x, 2 + i, z, st("pointed_dripstone", thickness=th, vertical_direction="up", waterlogged="false"))
    # luz: froglights nas paredes e cristais de mana no chão
    for k in range(10):
        a = math.pi * 2 * k / 10
        for y in (4, 8):
            # primeiro bloco de rocha saindo do centro: a luz fica na superfície da parede
            rr = 12.0
            while rr < 16.5:
                x, z = int(round(c + math.cos(a) * rr)), int(round(c + math.sin(a) * rr))
                if 0 <= x < LW and 0 <= z < LW and s.get(x, y, z) != AIR:
                    s.set(x, y, z, "minecraft:ochre_froglight[axis=y]")
                    break
                rr += 0.5
        x, z = int(round(c + math.cos(a + 0.3) * 13.6)), int(round(c + math.sin(a + 0.3) * 13.6))
        s.set(x, 1, z, f"{NS}:depths_mana_geode_block")
        s.set(x, 2, z, st(f"{NS}:depths_mana_cluster", facing="up", waterlogged="false"))
    # ossadas de aventureiros
    for _ in range(8):
        a = s.rnd.random() * math.pi * 2
        rr = s.rnd.uniform(6, 12)
        x, z = int(round(c + math.cos(a) * rr)), int(round(c + math.sin(a) * rr))
        if s.get(x, 2, z) == AIR:
            s.set(x, 2, z, st("bone_block", axis=s.rnd.choice("xyz")) if s.rnd.random() < 0.6 else
                  st("skeleton_skull", rotation=str(s.rnd.randrange(16))))
    # poço da escada (chega do andar 4) no canto noroeste
    lx, lz = 6, 6
    s.fill(lx, 2, lz, lx, LH - 2, lz, LADDER)
    s.fill(lx, 1, lz - 1, lx, LH - 2, lz - 1, "minecraft:polished_deepslate")
    s.fill(lx - 1, 2, lz, lx - 1, LH - 2, lz, "minecraft:polished_deepslate")
    s.fill(lx + 1, 2, lz, lx + 1, LH - 2, lz, "minecraft:polished_deepslate")
    s.jigsaw(lx, LH - 1, lz, "up_north", f"{NS}:depths_up", "minecraft:empty", "minecraft:empty", LADDER, joint="aligned")
    for y in range(3, LH - 2):
        if s.get(lx, y, lz + 1) != AIR:
            s.set(lx, y, lz + 1, AIR)
    s.set(lx + 2, 2, lz + 1, TRAVEL_STONE)
    # tesouro do Verme-Rei numa alcova ao sul
    s.fill(13, 2, 29, 19, 5, 31, AIR)
    s.fill(13, 1, 29, 19, 1, 31, "minecraft:polished_deepslate")
    s.chest(14, 2, 31, "depths_worm_hoard", "north")
    s.chest(18, 2, 31, "depths_worm_hoard", "north")
    s.set(16, 2, 31, "minecraft:gold_block")
    s.set(16, 3, 31, st(f"{NS}:depths_mana_cluster", facing="up", waterlogged="false"))
    s.set(15, 2, 30, st("skeleton_skull", rotation="8"))
    s.set(17, 2, 30, st("candle", candles="4", lit="true", waterlogged="false"))
    # o próprio Verme-Rei, adormecido sob o monte de terra no centro
    s.mob(c + 0.5, 2, c + 0.5, "depths_worm_king", 0.0, {"Lair": "depths_dungeon"})
    cnt = s.save(os.path.join(DATA, "structure/depths_dungeon/lair.nbt"), keep_air=True)
    print("  estrutura depths_dungeon/lair %d blocos" % cnt)


def worldgen():
    tp = os.path.join(DATA, "worldgen/template_pool/depths_dungeon")

    def pool_json(locs):
        return {"fallback": "minecraft:empty", "elements": [
            {"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:depths_dungeon/{l}",
                                      "processors": "minecraft:empty", "projection": "rigid"}} for l in locs]}

    write_json(os.path.join(tp, "entrance.json"), pool_json(["entrance"]))
    for n in range(1, 5):
        write_json(os.path.join(tp, f"floor{n}.json"), pool_json([f"floor{n}_a", f"floor{n}_b"]))
    write_json(os.path.join(tp, "lair.json"), pool_json(["lair"]))
    spawns = [{"type": "minecraft:zombie", "weight": 6, "minCount": 1, "maxCount": 2},
              {"type": "minecraft:skeleton", "weight": 6, "minCount": 1, "maxCount": 2},
              {"type": "minecraft:spider", "weight": 3, "minCount": 1, "maxCount": 1},
              {"type": "minecraft:cave_spider", "weight": 2, "minCount": 1, "maxCount": 2},
              {"type": f"{NS}:depths_lost_delver", "weight": 5, "minCount": 1, "maxCount": 1},
              {"type": f"{NS}:depths_burrower", "weight": 4, "minCount": 1, "maxCount": 2}]
    write_json(os.path.join(DATA, "worldgen/structure/depths_dungeon.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/depths_dungeon",
        "step": "underground_structures",
        "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": spawns}},
        "start_pool": f"{NS}:depths_dungeon/entrance",
        "size": 7,
        # base do molde da entrada fica 20 blocos abaixo do chão: o poço desce e a ruína fica na superfície
        "start_height": {"absolute": -EG},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": False})
    write_json(os.path.join(DATA, "worldgen/structure_set/depths_dungeon.json"), {
        "structures": [{"structure": f"{NS}:depths_dungeon", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": 34, "separation": 12, "salt": 1716000161,
                      "exclusion_zones": [
                          {"other_set": "minecraft:villages", "chunk_count": 4},
                          {"other_set": f"{NS}:guild_city", "chunk_count": 10},
                          {"other_set": f"{NS}:capital_city", "chunk_count": 13},
                          {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
                          {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13},
                          {"other_set": "minecraft:ancient_cities", "chunk_count": 3},
                          {"other_set": "minecraft:trial_chambers", "chunk_count": 3}]}})
    biomes = ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna",
              "#minecraft:is_badlands", "#minecraft:is_hill", "minecraft:plains", "minecraft:sunflower_plains",
              "minecraft:snowy_plains", "minecraft:desert", "minecraft:meadow", "minecraft:cherry_grove",
              "minecraft:dripstone_caves", "minecraft:lush_caves"]
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/depths_dungeon.json"), {"values": biomes})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "depths_dungeon.json"), {"values": [f"{NS}:depths_dungeon"]})
    write_json(os.path.join(tags, "boss_lair.json"), {"values": [f"{NS}:depths_dungeon"]})
    write_json(os.path.join(tags, "lair/depths_dungeon.json"), {"values": [f"{NS}:depths_dungeon"]})

    # Geodo de Mana gigante: como o de ametista, porém maior, com Cristal de Mana Germinante e aglomerados.
    def state(name, **props):
        o = {"Name": name}
        if props:
            o["Properties"] = props
        return {"type": "minecraft:simple_state_provider", "state": o}

    write_json(os.path.join(DATA, "worldgen/configured_feature/depths_mana_geode.json"), {
        "type": "minecraft:geode",
        "config": {
            "blocks": {
                "filling_provider": state("minecraft:air"),
                "inner_layer_provider": state(f"{NS}:depths_mana_geode_block"),
                "alternate_inner_layer_provider": state(f"{NS}:depths_budding_mana_crystal"),
                "middle_layer_provider": state("minecraft:calcite"),
                "outer_layer_provider": state("minecraft:smooth_basalt"),
                "inner_placements": [
                    {"Name": f"{NS}:depths_mana_bud", "Properties": {"facing": "up", "waterlogged": "false"}},
                    {"Name": f"{NS}:depths_mana_cluster", "Properties": {"facing": "up", "waterlogged": "false"}}],
                "cannot_replace": "#minecraft:features_cannot_replace",
                "invalid_blocks": "#minecraft:geode_invalid_blocks"},
            "layers": {"filling": 2.0, "inner_layer": 2.6, "middle_layer": 3.6, "outer_layer": 4.6},
            "crack": {"generate_crack_chance": 0.9, "base_crack_size": 2.0, "crack_point_offset": 2},
            "use_potential_placements_chance": 0.4,
            "use_alternate_layer0_chance": 0.1,
            "placements_require_layer0_alternate": True,
            "outer_wall_distance": {"type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 6},
            "distribution_points": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 5},
            "point_offset": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3},
            "min_gen_offset": -14,
            "max_gen_offset": 14,
            "noise_multiplier": 0.05,
            "invalid_blocks_threshold": 1}})
    write_json(os.path.join(DATA, "worldgen/placed_feature/depths_mana_geode.json"), {
        "feature": f"{NS}:depths_mana_geode",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 36}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                                    "min_inclusive": {"absolute": -56},
                                                                    "max_inclusive": {"absolute": 20}}},
                      {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, "neoforge/biome_modifier/depths_mana_geode.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": f"{NS}:depths_mana_geode", "step": "local_modifications"})
    # Criaturas das profundezas: o predicado de spawn só deixa nascer abaixo de y 0.
    write_json(os.path.join(DATA, "neoforge/biome_modifier/depths_spawns.json"), {
        "type": "neoforge:add_spawns", "biomes": f"#{NS}:has_structure/depths_dungeon",
        "spawners": [{"type": f"{NS}:depths_burrower", "weight": 10, "minCount": 1, "maxCount": 2},
                     {"type": f"{NS}:depths_lost_delver", "weight": 12, "minCount": 1, "maxCount": 1}]})


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


def empty(w):
    return {"type": "minecraft:empty", "weight": w}


def loot_tables():
    lt = os.path.join(DATA, "loot_table")
    survives = [{"condition": "minecraft:survives_explosion"}]
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write_json(os.path.join(lt, "blocks/depths_mana_geode_block.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:depths_mana_geode_block"}],
         "conditions": survives}], "random_sequence": f"{NS}:blocks/depths_mana_geode_block"})
    for b in ("depths_budding_mana_crystal", "depths_mana_bud"):
        write_json(os.path.join(lt, f"blocks/{b}.json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}], "conditions": [silk]}],
            "random_sequence": f"{NS}:blocks/{b}"})
    write_json(os.path.join(lt, "blocks/depths_mana_cluster.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:depths_mana_cluster", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{NS}:mana_crystal", "functions": [
                {"function": "minecraft:set_count", "count": 2},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]},
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:depths_pure_mana_shard"}],
         "conditions": [{"condition": "minecraft:inverted", "term": silk},
                        {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                         "chances": [0.05, 0.07, 0.09, 0.12]}]}],
        "random_sequence": f"{NS}:blocks/depths_mana_cluster"})
    killed = [{"condition": "minecraft:killed_by_player"}]

    def chance(c):
        return killed + [{"condition": "minecraft:random_chance", "chance": c}]

    write_json(os.path.join(lt, "entities/depths_burrower.json"), {"type": "minecraft:entity", "pools": [
        pool([ie("clay_ball", 0, 2, 3), ie("rotten_flesh", 0, 1, 2)]),
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:mana_crystal")], "conditions": chance(0.3)},
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:depths_pure_mana_shard")], "conditions": chance(0.02)}],
        "random_sequence": f"{NS}:entities/depths_burrower"})
    write_json(os.path.join(lt, "entities/depths_lost_delver.json"), {"type": "minecraft:entity", "pools": [
        pool([ie("bone", 0, 2, 4), ie("coal", 0, 2, 3), ie("torch", 1, 3, 3)], 1, 2),
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie("raw_iron", 1, 2, 4), ie("raw_gold", 1, 1, 1)], "conditions": chance(0.4)},
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie("emerald")], "conditions": chance(0.05)},
        {"rolls": 1, "bonus_rolls": 0, "entries": [ie(f"{NS}:depths_pure_mana_shard")], "conditions": chance(0.03)},
        {"rolls": 1, "bonus_rolls": 0, "entries": [scroll("excavate"), scroll("rock"), scroll("sonar")], "conditions": chance(0.02)}],
        "random_sequence": f"{NS}:entities/depths_lost_delver"})
    write_json(os.path.join(lt, "entities/depths_worm_king.json"), {"type": "minecraft:entity", "pools": []})

    ch = os.path.join(lt, "chests")
    shard = f"{NS}:depths_pure_mana_shard"
    tables = {
        "depths_floor_1": [
            pool([ie("bread", 1, 3, 5), ie("coal", 2, 6, 5), ie("iron_ingot", 1, 3, 4), ie("torch", 4, 10, 4), ie("bone", 2, 5, 4),
                  ie("gold_nugget", 2, 8, 3), ie("string", 1, 3, 3), ie("arrow", 4, 10, 3), ie("raw_iron", 1, 3, 3)], 3, 6),
            pool([ie(f"{NS}:essence_earth", 1, 1, 3), ie(f"{NS}:mana_crystal", 1, 2, 3), ie("name_tag", 1, 1, 1),
                  ie("iron_pickaxe", 1, 1, 1, [ench(5, 15)]), empty(3)]),
            pool([scroll(g) for g in ("rock", "stalagmite", "raise_pillar", "excavate", "sonar", "reveal", "smoke")] + [empty(8)])],
        "depths_floor_2": [
            pool([ie("iron_ingot", 2, 5, 5), ie("gold_ingot", 1, 3, 4), ie("string", 2, 6, 4), ie("spider_eye", 1, 3, 3),
                  ie("ender_pearl", 1, 1, 2), ie("redstone", 2, 8, 3), ie("lapis_lazuli", 2, 8, 3), ie(f"{NS}:mana_crystal", 1, 3, 4)], 3, 6),
            pool([ie(f"{NS}:essence_earth", 1, 2, 3), ie(f"{NS}:essence_shadow", 1, 1, 2), ie(f"{NS}:essence_arcane", 1, 1, 1),
                  ie("golden_apple", 1, 1, 1), ie("iron_chestplate", 1, 1, 1, [ench(10, 20)])], 1, 2),
            pool([scroll(g) for g in ("stoneskin", "tremor", "weight", "darkness", "shadow_step", "sonar", "reveal", "echo",
                                      "homing")] + [empty(4)])],
        "depths_floor_3": [
            pool([ie("gold_ingot", 2, 5, 5), ie("diamond", 1, 2, 3), ie("emerald", 2, 5, 4), ie("amethyst_shard", 3, 8, 4),
                  ie(f"{NS}:mana_crystal", 2, 5, 5), ie("raw_gold", 2, 4, 3), ie("lapis_lazuli", 4, 10, 3)], 3, 6),
            pool([ie(f"{NS}:essence_earth", 1, 2, 3), ie(f"{NS}:essence_arcane", 1, 1, 2), ie(shard, 1, 1, 3),
                  ie("book", 1, 1, 2, [ench(20, 30)]), ie("golden_apple", 1, 1, 2), ie("diamond_pickaxe", 1, 1, 1, [ench(15, 25)])], 1, 2),
            pool([scroll(g) for g in ("fortress", "sand_tide", "tremor", "quake", "crystal", "split", "pierce", "critical",
                                      "persistent")] + [empty(2)])],
        "depths_floor_4": [
            pool([ie("diamond", 2, 4, 5), ie("emerald", 3, 8, 5), ie("gold_block", 1, 2, 2), ie(f"{NS}:mana_crystal", 4, 8, 5),
                  ie(shard, 1, 2, 3), ie("netherite_scrap", 1, 1, 1)], 4, 7),
            pool([ie(f"{NS}:essence_arcane", 1, 2, 3), ie(f"{NS}:essence_earth", 2, 3, 3), ie("golden_apple", 1, 2, 3),
                  ie("enchanted_golden_apple", 1, 1, 1), ie("book", 1, 1, 2, [ench(30, 39)]),
                  ie("diamond_sword", 1, 1, 1, [ench(25, 30)])], 1, 2),
            pool([scroll(g) for g in ("fortress", "quake", "crystal", "overload", "echo", "persistent", "fortune", "critical")], 1, 2),
            pool([ie(f"{NS}:relic_dwarven_rune", 1, 1, 1), ie(f"{NS}:depths_goddess_knife", 1, 1, 1),
                  ie(f"{NS}:depths_dragonslayer", 1, 1, 1), empty(30)])],
        "depths_worm_hoard": [
            pool([ie("diamond", 3, 6, 5), ie("emerald", 6, 12, 5), ie("gold_ingot", 6, 12, 4), ie(f"{NS}:mana_crystal", 8, 16, 5),
                  ie(shard, 2, 4, 4), ie("amethyst_block", 2, 4, 2)], 5, 8),
            pool([ie(f"{NS}:essence_earth", 2, 4, 3), ie(f"{NS}:essence_arcane", 1, 3, 3), ie("enchanted_golden_apple", 1, 1, 1),
                  ie("golden_apple", 2, 3, 3), ie("book", 1, 1, 2, [ench(30, 39)]),
                  ie("diamond_chestplate", 1, 1, 1, [ench(30, 39)])], 2, 3),
            pool([scroll(g) for g in ("fortress", "quake", "crystal", "overload", "echo", "persistent", "fortune")], 2, 2),
            pool([ie(f"{NS}:depths_dragonslayer", 1, 1, 2), ie(f"{NS}:depths_goddess_knife", 1, 1, 2),
                  ie(f"{NS}:relic_dwarven_rune", 1, 1, 1), empty(4)])],
    }
    # v1.0 — premio das salas de enigma (andares 1-2 e 3-4)
    tables["depths_puzzle"] = [
        pool([ie(f"{NS}:mana_crystal", 2, 5, 5), ie("gold_ingot", 2, 5, 4), ie("emerald", 2, 5, 4), ie("lapis_lazuli", 4, 9, 3),
              ie("amethyst_shard", 2, 6, 3), ie("experience_bottle", 2, 5, 3)], 3, 5),
        pool([ie(f"{NS}:essence_arcane", 1, 2, 3), ie(f"{NS}:essence_earth", 1, 2, 3), ie("golden_apple", 1, 1, 2),
              ie("book", 1, 1, 2, [ench(15, 25)]), ie(shard, 1, 1, 1)], 1, 2),
        pool([scroll(g) for g in ("sonar", "reveal", "stoneskin", "tremor", "echo", "homing", "split", "pierce")]),
        pool([ie(f"{NS}:blank_recipe_scroll", 1, 1, 1), empty(5)])]
    tables["depths_puzzle_deep"] = [
        pool([ie(f"{NS}:mana_crystal", 4, 8, 5), ie("diamond", 1, 3, 4), ie("emerald", 4, 8, 4), ie("gold_ingot", 4, 8, 3),
              ie("experience_bottle", 4, 8, 3), ie(shard, 1, 2, 3)], 4, 6),
        pool([ie(f"{NS}:essence_arcane", 1, 3, 3), ie(f"{NS}:essence_earth", 2, 3, 2), ie("golden_apple", 1, 2, 3),
              ie("book", 1, 1, 2, [ench(25, 35)]), ie("enchanted_golden_apple", 1, 1, 1)], 2, 2),
        pool([scroll(g) for g in ("fortress", "quake", "crystal", "overload", "echo", "persistent", "fortune", "critical")]),
        pool([ie(f"{NS}:blank_recipe_scroll", 1, 1, 2), ie(f"{NS}:abyss_shard", 1, 2, 1), empty(5)])]
    for name, pools in tables.items():
        write_json(os.path.join(ch, f"{name}.json"), {"type": "minecraft:chest", "pools": pools,
                                                      "random_sequence": f"{NS}:chests/{name}"})


def recipes():
    rc = os.path.join(DATA, "recipe")
    fang = {"item": f"{NS}:depths_worm_king_fang"}
    write_json(os.path.join(rc, "depths_dragonslayer.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" B ", " B ", "FNF"],
        "key": {"B": {"item": "minecraft:iron_block"}, "N": {"item": "minecraft:netherite_ingot"}, "F": fang},
        "result": {"id": f"{NS}:depths_dragonslayer", "count": 1}})
    write_json(os.path.join(rc, "depths_goddess_knife.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["S", "G", "F"],
        "key": {"S": {"item": f"{NS}:depths_pure_mana_shard"}, "G": {"item": "minecraft:gold_ingot"}, "F": fang},
        "result": {"id": f"{NS}:depths_goddess_knife", "count": 1}})
    write_json(os.path.join(rc, "depths_mana_geode_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [{"item": f"{NS}:mana_crystal"}] * 4 + [{"item": "minecraft:amethyst_block"}],
        "result": {"id": f"{NS}:depths_mana_geode_block", "count": 2}})


def tags():
    blocks = [f"{NS}:{b}" for b in ("depths_mana_geode_block", "depths_budding_mana_crystal", "depths_mana_bud", "depths_mana_cluster")]
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": blocks})
    write_json(os.path.join(MERGE, "data/minecraft/tags/item/swords.json"),
               {"values": [f"{NS}:depths_dragonslayer", f"{NS}:depths_goddess_knife"]})
    write_json(os.path.join(MERGE, "data/c/tags/entity_type/bosses.json"), {"replace": False, "values": [f"{NS}:depths_worm_king"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/entity_type/undead.json"), {"values": [f"{NS}:depths_lost_delver"]})


def journal():
    loc = {"structure": f"{NS}:depths_dungeon", "dimension": "minecraft:overworld"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 61) para o meio da história (Ato IV); XP na escala dos vizinhos.
    write_json(os.path.join(DATA, "journal/depths.json"), {"chapters": [{
        "id": "depths", "order": 32, "color": "#C8873E", "intro": True,
        "icon": [f"{NS}:depths_worm_king_fang", f"{NS}:depths_pure_mana_shard", f"{NS}:depths_dragonslayer"],
        "steps": [
            {"id": "enter", "condition": {"type": "flag", "flag": "depths.entered"}, "xp": 150,
             "icon": "minecraft:mossy_stone_bricks", "locate": loc},
            {"id": "floor3", "condition": {"type": "flag", "flag": "depths.floor3"}, "xp": 300,
             "icon": "minecraft:deepslate_bricks", "locate": loc},
            {"id": "geode", "condition": {"type": "flag", "flag": "depths.geode"}, "optional": True, "xp": 150,
             "icon": f"{NS}:depths_mana_cluster"},
            {"id": "puzzle", "condition": {"type": "flag", "flag": "depths.puzzle"}, "optional": True, "xp": 150,
             "icon": f"{NS}:depths_puzzle_core"},
            {"id": "worm_king", "condition": {"type": "bosses", "ids": ["depths_worm_king"], "min": 1}, "xp": 600,
             "icon": f"{NS}:depths_worm_king_fang", "locate": loc,
             "rewards": {"items": [{"id": "minecraft:golden_apple", "count": 2}]}, "requires": [f"{NS}:depths_worm_king"]},
            {"id": "treasure", "condition": {"type": "flag", "flag": "depths.treasure"}, "optional": True, "xp": 250,
             "icon": f"{NS}:depths_goddess_knife"}],
        "rewards": {"xp": 1900, "items": [{"id": f"{NS}:mana_crystal", "count": 12}], "glyph_scrolls": 1, "glyph_tier": 3}}]})


# =====================================================================================
#  TRADUCOES
# =====================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def lang():
    for eid, pt, en in (("depths_worm_king", "Verme-Rei das Profundezas", "Worm King of the Depths"),
                        ("depths_burrower", "Verme Cavador", "Burrowing Worm"),
                        ("depths_lost_delver", "Explorador Perdido", "Lost Delver")):
        t(f"entity.{NS}.{eid}", pt, en)
        t(f"item.{NS}.{eid}_spawn_egg", f"Ovo Gerador de {pt}", f"{en} Spawn Egg")
    t(f"bestiary.{NS}.depths_worm_king.desc",
      "Um verme do tamanho de uma torre que devora geodos de mana — os cristais cresceram na própria carapaça. Some na terra e irrompe embaixo da presa.",
      "A tower-sized worm that devours mana geodes — the crystals grew into its own shell. It vanishes underground and bursts out beneath its prey.")
    t(f"bestiary.{NS}.depths_burrower.desc", "Larva das profundezas. Mergulha no chão e morde de baixo para cima. Depois de sair, demora para cavar de novo.",
      "A larva of the depths. It dives into the ground and bites from below. After surfacing, it takes a while to dig again.")
    t(f"bestiary.{NS}.depths_lost_delver.desc", "Um aventureiro que nunca voltou. O lampião do capacete ainda cega quem o encara.",
      "An adventurer who never came back. Its helmet lamp still blinds whoever stares at it.")
    # chefe
    b = "depths_worm_king"
    t(f"boss_entrance.{NS}.{b}", "Aquele que Devora a Terra", "The One Who Devours the Earth")
    t(f"boss.{NS}.{b}.subtitle", "Senhor do Ninho das Profundezas", "Lord of the Deep Nest")
    t(f"boss.{NS}.{b}.phase1", "A Ninhada", "The Brood")
    t(f"boss.{NS}.{b}.phase2", "Fúria das Profundezas", "Fury of the Depths")
    t(f"announce.{NS}.{b}.intro", "*a terra inteira treme... algo enorme acordou*", "*the whole earth shakes... something huge has awoken*")
    t(f"announce.{NS}.{b}.phase1", "*o chão se enche de pequenos tremores — a ninhada desperta*", "*the ground fills with small tremors — the brood awakens*")
    t(f"announce.{NS}.{b}.phase2", "*o teto range; pedras começam a cair*", "*the ceiling groans; stones begin to fall*")
    t(f"announce.{NS}.{b}.summon", "*vermes cavadores saem da terra!*", "*burrowing worms crawl out of the earth!*")
    t(f"announce.{NS}.{b}.drip", "*estalactites se soltam do teto!*", "*stalactites break loose from the ceiling!*")
    t(f"announce.{NS}.{b}.death", "*o gigante desaba, e a caverna finalmente fica em silêncio*", "*the giant collapses, and the cave finally falls silent*")
    for a, pt, en in (("erupt", "Irrupção", "Eruption"), ("bite", "Mordida", "Bite"), ("spit", "Cuspe Ácido", "Acid Spit"),
                      ("slam", "Pancada Sísmica", "Seismic Slam"), ("drip", "Chuva de Estalactites", "Stalactite Rain")):
        t(f"attack.{NS}.{b}.{a}", f"Verme-Rei — {pt}", f"Worm King — {en}")
    t(f"message.{NS}.{b}.buried", "Enterrado, ele não sente golpes — corra do círculo marcado e bata quando ele sair!",
      "While buried it can't be hurt — run from the marked circle and strike when it surfaces!")
    t(f"message.{NS}.{b}.erupt", "O chão marcado vai explodir: saia do círculo!", "The marked ground will burst: leave the circle!")
    t(f"message.{NS}.{b}.larvae", "Vermes Cavadores mordem por baixo — mate-os quando estiverem fora da terra.",
      "Burrowing Worms bite from below — kill them while they're above ground.")
    t(f"place.{NS}.depths_dungeon", "Ninho do Verme-Rei", "Worm King's Nest")
    # blocos e itens
    t(f"block.{NS}.depths_mana_geode_block", "Bloco de Geodo de Mana", "Mana Geode Block")
    t(f"block.{NS}.depths_budding_mana_crystal", "Cristal de Mana Germinante", "Budding Mana Crystal")
    t(f"block.{NS}.depths_mana_bud", "Broto de Mana", "Mana Bud")
    t(f"block.{NS}.depths_mana_cluster", "Aglomerado de Mana", "Mana Cluster")
    t(f"item.{NS}.depths_pure_mana_shard", "Fragmento de Mana Pura", "Pure Mana Shard")
    t(f"item.{NS}.depths_pure_mana_shard.desc", "Mana tão concentrada que virou pedra. Raro nos geodos e nas profundezas.",
      "Mana so concentrated it turned to stone. Rare in geodes and the depths.")
    t(f"item.{NS}.depths_pure_mana_shard.use", "Usar: recupera 40% da mana máxima (recarga 30 s).", "Use: restores 40% of max mana (30 s cooldown).")
    t(f"item.{NS}.depths_pure_mana_shard.full", "Sua mana já está cheia.", "Your mana is already full.")
    t(f"item.{NS}.depths_worm_king_fang", "Presa do Verme-Rei", "Worm King's Fang")
    t(f"item.{NS}.depths_worm_king_fang.desc", "Dura como diamante, ainda morna. Ferreiros pagariam fortunas.",
      "Hard as diamond, still warm. Smiths would pay fortunes for it.")
    t(f"item.{NS}.depths_worm_king_fang.use", "Material: Matadora de Dragões e Faca da Deusa.", "Material: Dragonslayer and Goddess Knife.")
    t(f"item.{NS}.depths_dragonslayer", "Matadora de Dragões", "Dragonslayer")
    t(f"item.{NS}.depths_dragonslayer.desc", "Grande demais para ser chamada de espada. Grossa, pesada, bruta — um pedaço de ferro.",
      "Too big to be called a sword. Thick, heavy, rough — a heap of raw iron.")
    t(f"item.{NS}.depths_dragonslayer.use", "Segurar 1 s e soltar: Corte do Espadachim Negro — arco de 200°, 16 de dano e empurrão (10 s).",
      "Hold 1 s and release: Black Swordsman's Cleave — 200° arc, 16 damage and knockback (10 s).")
    t(f"item.{NS}.depths_dragonslayer.passive", "Golpes cheios cortam em arco. +50% em chefes e gigantes. Pesada: −10% de velocidade.",
      "Full swings cleave in an arc. +50% vs bosses and giants. Heavy: −10% speed.")
    t(f"item.{NS}.depths_goddess_knife", "Faca da Deusa", "Goddess Knife")
    t(f"item.{NS}.depths_goddess_knife.desc", "Forjada para um único aventureiro: cresce junto com ele.",
      "Forged for a single adventurer: it grows along with them.")
    t(f"item.{NS}.depths_goddess_knife.use", "Segurar e soltar: Argonauta — carregue até 3 s; o próximo golpe causa +50% por segundo (12 s).",
      "Hold and release: Argonaut — charge up to 3 s; your next hit deals +50% per second (12 s).")
    t(f"item.{NS}.depths_goddess_knife.falna", "Falna: +%s de dano (cresce com seu nível e Círculo)",
      "Falna: +%s damage (grows with your level and Circle)")
    t(f"item.{NS}.depths_goddess_knife.charged", "Argonauta carregado: próximo golpe +%s%%", "Argonaut charged: next hit +%s%%")
    # mensagens da masmorra
    m = f"message.{NS}.depths"
    t(f"{m}.entered", "Você entrou na Masmorra em Camadas. Quanto mais fundo, melhor o tesouro — e pior o que guarda.",
      "You entered the Layered Dungeon. The deeper you go, the better the treasure — and the worse its guardians.")
    t(f"{m}.floor", "Andar %s", "Floor %s")
    t(f"{m}.lair", "Ninho do Verme-Rei", "Worm King's Nest")
    for n, pt, en in ((1, "Galerias Esquecidas", "Forgotten Galleries"), (2, "Ninho de Teias", "Web Nest"),
                      (3, "Minas de Cristal", "Crystal Mines"), (4, "Salões Abissais", "Abyssal Halls"),
                      (5, "O chão aqui respira...", "The ground here breathes...")):
        t(f"{m}.floor.{n}", pt, en)
    t(f"{m}.geode", "Um Geodo de Mana! Procure o Cristal Germinante: com Toque Suave você leva a fonte para casa.",
      "A Mana Geode! Look for the Budding Crystal: with Silk Touch you can take the source home.")
    # v1.0 — quebra-cabecas
    for bid, pt, en in (("depths_puzzle_core", "Selo Rúnico", "Rune Seal"), ("depths_rune_lamp", "Lâmpada Rúnica", "Rune Lamp"),
                        ("depths_rune_tile", "Placa Rúnica", "Rune Plate"), ("depths_rune_stone", "Pedra Rúnica", "Rune Stone"),
                        ("depths_light_mirror", "Espelho de Luz", "Light Mirror"), ("depths_puzzle_gate", "Grade Selada", "Sealed Grate")):
        t(f"block.{NS}.{bid}", pt, en)
    p = f"{m}.puzzle"
    t(f"{p}.hint.levers", "Cada alavanca inverte algumas lâmpadas. Acenda todas.", "Each lever flips some lamps. Light them all.")
    t(f"{p}.hint.tiles", "Acenda todas as placas sem pisar duas vezes na mesma nem sair da grade.",
      "Light every plate without stepping on one twice or leaving the grid.")
    t(f"{p}.hint.light", "Gire os espelhos (botão direito) até a luz acertar a lâmpada.", "Turn the mirrors (right-click) until the light hits the lamp.")
    t(f"{p}.hint.sequence", "Observe a ordem das runas e toque nelas na mesma ordem.", "Watch the order of the runes, then touch them in that order.")
    t(f"{p}.hint.none", "O selo está adormecido: faltam peças do enigma por perto.", "The seal sleeps: puzzle pieces are missing nearby.")
    t(f"{p}.solved", "Selo rompido! A grade se abriu.", "Seal broken! The grate has opened.")
    t(f"{p}.already", "Este selo já foi rompido.", "This seal is already broken.")
    t(f"{p}.wrong", "Runa errada — recomece.", "Wrong rune — start over.")
    t(f"{p}.reset", "As placas se apagaram — recomece.", "The plates went dark — start over.")
    t(f"hint.{NS}.depths_puzzle", "Sala de enigma: toque no Selo Rúnico para uma dica. Resolvido, a grade do baú some.",
      "Puzzle room: touch the Rune Seal for a tip. Solve it and the grate around the chest vanishes.")
    # v1.0 — afixos por andar e cronometro
    for aid, pt, en, dpt, den in (
            ("fortified", "Fortificado", "Fortified", "monstros com +50% de vida", "monsters have +50% health"),
            ("frenzied", "Frenético", "Frenzied", "monstros 25% mais rápidos", "monsters move 25% faster"),
            ("brutal", "Brutal", "Brutal", "monstros causam +35% de dano", "monsters deal +35% damage"),
            ("volatile", "Volátil", "Volatile", "monstros estouram ao morrer — afaste-se", "monsters burst when they die — step back"),
            ("vampiric", "Vampírico", "Vampiric", "monstros se curam quando ferem você", "monsters heal when they hurt you"),
            ("draining", "Drenante", "Draining", "cada golpe dos monstros rouba mana", "every monster hit steals mana")):
        t(f"affix.{NS}.depths.{aid}", pt, en)
        t(f"affix.{NS}.depths.{aid}.desc", dpt, den)
    t(f"{m}.affixes", "Afixos do andar: %s", "Floor affixes: %s")
    t(f"{m}.run.done", "Verme-Rei derrotado! Tempo da descida: %s (seu recorde: %s).", "Worm King defeated! Descent time: %s (your record: %s).")
    t(f"{m}.run.record", "Verme-Rei derrotado em %s — NOVO RECORDE!", "Worm King defeated in %s — NEW RECORD!")
    t(f"hint.{NS}.depths_affixes", "Cada andar tem afixos que mudam os monstros (mudam a cada 7 dias). Mais afixos, um pouco mais de XP.",
      "Each floor has affixes that change its monsters (they rotate every 7 days). More affixes, a bit more XP.")
    # placas
    s = f"sign.{NS}.depths"
    t(f"{s}.entrance_1", "MASMORRA", "DUNGEON")
    t(f"{s}.entrance_2", "Quem desce", "Those who descend")
    t(f"{s}.entrance_3", "nem sempre sobe", "don't always return")
    for n, pt, en in ((1, "Galerias", "Galleries"), (2, "Ninho de Teias", "Web Nest"), (3, "Minas de Cristal", "Crystal Mines"),
                      (4, "Salões Abissais", "Abyssal Halls")):
        t(f"{s}.floor_{n}_1", f"Andar {n}", f"Floor {n}")
        t(f"{s}.floor_{n}_2", pt, en)
    t(f"{s}.down_1", "Descida", "Way down")
    t(f"{s}.down_next", "Próximo andar", "Next floor")
    t(f"{s}.down_lair", "Algo ENORME", "Something HUGE")
    t(f"{s}.mimic_1", "Só um", "Only one")
    t(f"{s}.mimic_2", "é verdadeiro.", "is real.")
    t(f"{s}.mimic_3", "Escolha bem.", "Choose wisely.")
    # comandos
    c = f"command.{NS}.depths"
    t(f"{c}.given", "Itens das Profundezas entregues.", "Depths items given.")
    t(f"{c}.not_found", "Nenhuma Masmorra em Camadas encontrada perto (só chunks gerados depois da v1.6 têm).",
      "No Layered Dungeon found nearby (only chunks generated after v1.6 have them).")
    t(f"{c}.tp", "Teleportado para a Masmorra em Camadas em %s %s %s.", "Teleported to the Layered Dungeon at %s %s %s.")
    t(f"{c}.where", "Andar atual na masmorra: %s (-1 = fora; 0 = entrada; 5 = ninho).", "Current dungeon floor: %s (-1 = outside; 0 = entrance; 5 = nest).")
    t(f"{c}.spawn_done", "%s invocado.", "%s summoned.")
    t(f"{c}.spawn_fail", "Não foi possível invocar o chefe aqui.", "Could not summon the boss here.")
    t(f"{c}.geode_done", "Geodo de Mana gerado em %s %s %s.", "Mana Geode generated at %s %s %s.")
    t(f"{c}.geode_fail", "O geodo não coube aqui (água, lava ou bedrock no caminho).", "The geode didn't fit here (water, lava or bedrock in the way).")
    t(f"{c}.affix_floor", "Andar %s — %s", "Floor %s — %s")
    t(f"{c}.record", "Melhor tempo: %s · masmorras concluídas: %s · descida atual: %s", "Best time: %s · dungeons cleared: %s · current descent: %s")
    t(f"{c}.puzzle_none", "Nenhum Selo Rúnico carregado por perto (ou molde de teste ausente).", "No loaded Rune Seal nearby (or demo template missing).")
    t(f"{c}.puzzle_demo", "Sala de enigma de teste (%s) montada à sua frente.", "Demo puzzle room (%s) built in front of you.")
    t(f"{c}.puzzle_at", "Selo Rúnico (%s) em %s %s %s.", "Rune Seal (%s) at %s %s %s.")
    # diario
    j = f"journal.{NS}.depths"
    t(f"{j}.title", "As Profundezas", "The Depths")
    t(f"{j}.intro", "Ruínas com um poço escuro aparecem pelas florestas e planícies. Dizem que lá embaixo há uma masmorra em camadas — e que no fundo dorme um verme do tamanho de uma torre.",
      "Ruins with a dark well appear across forests and plains. They say a layered dungeon lies below — and a tower-sized worm sleeps at the bottom.")
    t(f"{j}.lore", "Cada andar é mais perigoso que o anterior, com armadilhas, geradores de monstros e mímicos. Em compensação, os baús ficam cada vez mais ricos.",
      "Each floor is more dangerous than the last, with traps, monster spawners and mimics. In exchange, the chests grow richer and richer.")
    t(f"{j}.outro", "O Verme-Rei caiu. As profundezas continuam lá embaixo — mas agora são suas.",
      "The Worm King has fallen. The depths remain below — but now they are yours.")
    for sid, (pt, en, hpt, hen, lpt, len_, tpt, ten) in {
        "enter": ("Entre na Masmorra em Camadas", "Enter the Layered Dungeon",
                  "Procure uma ruína com um poço e escada de mão (florestas, planícies, colinas). Siga a bússola do diário e desça.",
                  "Look for a ruin with a well and a ladder (forests, plains, hills). Follow the journal compass and climb down.",
                  "A placa diz: quem desce nem sempre sobe.", "The sign says: those who descend don't always return.",
                  "Superfície", "Surface"),
        "floor3": ("Chegue ao Andar 3", "Reach Floor 3",
                   "Cada andar tem uma escada de descida no canto oposto ao da chegada. Cuidado com placas de pressão: são armadilhas de flechas.",
                   "Each floor has a way down in the corner opposite the arrival. Beware of pressure plates: they are arrow traps.",
                   "Nas Minas de Cristal há uma gruta com um Cristal de Mana Germinante.", "The Crystal Mines hold a grotto with a Budding Mana Crystal.",
                   "Andar 3", "Floor 3"),
        "geode": ("Encontre um Geodo de Mana", "Find a Mana Geode",
                  "Geodos gigantes de cristal azul aparecem bem fundo no subterrâneo (y −56 a 20). Quebre um Aglomerado de Mana.",
                  "Giant blue crystal geodes appear deep underground (y −56 to 20). Break a Mana Cluster.",
                  "Aglomerados dão Cristais de Mana e, às vezes, um Fragmento de Mana Pura.", "Clusters drop Mana Crystals and sometimes a Pure Mana Shard.",
                  "Opcional", "Optional"),
        "worm_king": ("Derrote o Verme-Rei das Profundezas", "Defeat the Worm King of the Depths",
                      "No ninho, abaixo do Andar 4. Fuja dos círculos marcados quando ele mergulhar e bata quando ele sair da terra. Mate as larvas.",
                      "In the nest below Floor 4. Run from the marked circles when it dives and strike when it surfaces. Kill the larvae.",
                      "Cristais de mana cresceram na carapaça dele.", "Mana crystals grew on its shell.", "Ninho", "Nest"),
        "puzzle": ("Rompa um Selo Rúnico", "Break a Rune Seal",
                   "Cada andar da masmorra (e também a Cidadela Demoníaca, a Biblioteca do Fim e a Cidade Anã) tem uma sala de enigma. Toque no Selo Rúnico para uma dica; resolvido, a grade do baú some.",
                   "Every dungeon floor (and also the Demon Citadel, the End Library and the Dwarven City) has a puzzle room. Touch the Rune Seal for a tip; once solved, the grate around the chest vanishes.",
                   "Alavancas, placas, espelhos e runas: os antigos gostavam de testar a cabeça de quem entrava.",
                   "Levers, plates, mirrors and runes: the ancients liked to test the wits of those who entered.",
                   "Opcional", "Optional"),
        "treasure": ("Empunhe um tesouro das profundezas", "Wield a treasure of the depths",
                     "A Matadora de Dragões e a Faca da Deusa caem do Verme-Rei, aparecem nos baús mais fundos ou são forjadas com a Presa dele.",
                     "The Dragonslayer and the Goddess Knife drop from the Worm King, appear in the deepest chests, or are forged from its Fang.",
                     "Uma é grande demais; a outra cresce com você.", "One is too big; the other grows with you.", "Opcional", "Optional"),
    }.items():
        t(f"{j}.{sid}", pt, en)
        t(f"{j}.{sid}.hint", hpt, hen)
        t(f"{j}.{sid}.lore", lpt, len_)
        t(f"{j}.{sid}.tip", tpt, ten)

    os.makedirs(LANG, exist_ok=True)
    for idx, code in ((0, "pt_br"), (1, "en_us")):
        write_json(os.path.join(LANG, f"{code}.json"), {k: v[idx] for k, v in sorted(L.items())})


def main():
    print("depths: texturas")
    tex_shard(); tex_fang(); tex_dragonslayer(); tex_goddess_knife(); tex_blocks()
    print("depths: modelos")
    block_models(); item_models()
    PZ.assets(ASSETS)
    depths_geo.build_all(ASSETS)
    print("depths: estruturas e dados")
    entrance()
    for n in range(1, 5):
        for var in ("a", "b"):
            floor_piece(n, var)
    PZ.demo_rooms(DATA)
    lair(); worldgen(); loot_tables(); recipes(); tags(); journal(); lang()
    print("depths: pronto (%d traducoes)" % len(L))


if __name__ == "__main__":
    main()
