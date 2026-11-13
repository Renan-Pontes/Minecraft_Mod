"""
Gera os assets e dados do modulo lore (Enciclopedia, Paginas Perdidas, Ruinas de Velmora).
Uso (na raiz do projeto):  py -I tools/gen/lore.py        (os textos saem de tools/gen/lore_lang.py)

Saidas:
  - textures/item/lore_page.png, textures/block/lore_stele_{front,blank,side}.png, textures/gui/tab/lore.png
  - models/item/lore_page.json, models/block/lore_stele{,_blank}.json, models/item/lore_stele.json,
    blockstates/lore_stele.json
  - data: loot_table/blocks/lore_stele.json, loot_table/chests/lore_ruin.json, loot_table/chests/lore_treasure.json,
    recipe/lore_stele.json
  - structure/lore_ruin/<variante>.nbt e structure/lore_ruin_sand/<variante>.nbt (6 ruinas pequenas x 2 paletas)
  - worldgen/structure/lore_ruin{,_sand}.json, worldgen/template_pool/lore_ruin{,_sand}/start.json,
    worldgen/structure_set/lore_ruins.json
  - merge: tags de bioma has_structure/lore_ruin{,_sand}, tag de estrutura lore_ruins e mineable/pickaxe
"""
import json
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, item_model  # noqa: E402
from structgen import Structure  # noqa: E402

NS = "isekai_mahou"
A = os.path.join(ROOT, "src", "main", "resources", "assets", NS)
D = os.path.join(ROOT, "src", "main", "resources", "data", NS)
M = os.path.join(ROOT, "src", "main", "merge", "lore", "data")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(c, rel):
    path = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    c.save(path)


# =====================================================================================================
#  Texturas
# =====================================================================================================
OUT = hex_rgba("2a1c10")
PAPER0 = hex_rgba("b89f6a")
PAPER1 = hex_rgba("d9c594")
PAPER2 = hex_rgba("ecdcae")
PAPER3 = hex_rgba("f7edc8")
INK = hex_rgba("5a4630")
RUNE0 = hex_rgba("2f8fa8")
RUNE1 = hex_rgba("6fd8e8")
RUNE2 = hex_rgba("d8fbff")
GOLD = hex_rgba("e8b84a")
STONE0 = hex_rgba("4c4e55")
STONE1 = hex_rgba("686b73")
STONE2 = hex_rgba("82858d")
STONE3 = hex_rgba("9a9da5")
MOSS = hex_rgba("5f7d3a")
MOSS2 = hex_rgba("7c9a48")


def tex_page():
    """Pagina rasgada de pergaminho, com linhas de tinta e um selo runico azul."""
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "...oooooooo.....",
        "..oCCCCCCCBo....",
        "..oCiiiiiCCBo...",
        "..oCCCCCCCCCo...",
        "..oCiiiCiiCCo...",
        "..oBCCCCCCCCo...",
        "..oCiiiiCrCCo...",
        "..oCCCCCrRrCo...",
        "..oCiiCCCrCBo...",
        "..oBCCCCCCCCo...",
        "..oCiiiiiCCo....",
        "..oCCBCCCBo.....",
        "...oCo.oCo......",
        "....o...o.......",
        "................",
    ], {"o": OUT, "C": PAPER2, "B": PAPER1, "i": INK, "r": RUNE1, "R": RUNE2})
    # canto dobrado e luz na borda de cima
    c.set(11, 2, PAPER0)
    c.set(12, 3, PAPER0)
    for x in range(4, 10):
        if c.get(x, 2) == PAPER2:
            c.set(x, 2, PAPER3)
    save(c, "item/lore_page")
    item_model(A, "lore_page")


def stone_base(seed, moss=True):
    c = Canvas(16, 16)
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            base = STONE1 if r > 0.35 else STONE2 if r > 0.12 else STONE0
            c.set(x, y, darken(base, rnd.random() * 0.08))
    # rachaduras
    for (x0, y0, x1, y1) in ((3, 0, 5, 4), (12, 9, 10, 15), (1, 11, 3, 13)):
        c.line(x0, y0, x1, y1, STONE0)
    if moss:
        for _ in range(9):
            x, y = rnd.randrange(16), 12 + rnd.randrange(4)
            c.set(x, y, MOSS if rnd.random() > 0.4 else MOSS2)
    return c


def tex_stele():
    # Frente gravada: moldura entalhada, tres linhas de runas azuis e um selo dourado no alto
    c = stone_base(11)
    for i in range(2, 14):
        c.set(i, 1, STONE3)
        c.set(i, 14, STONE0)
    for i in range(1, 15):
        c.set(2, i, STONE3)
        c.set(13, i, STONE0)
    c.from_rows([
        "....gg....",
        "...gGGg...",
        "....gg....",
        "..........",
        ".rRr.rr.R.",
        "..........",
        ".R.rRr.rr.",
        "..........",
        ".rr.R.rRr.",
        "..........",
        "..rRr.rr..",
    ], {"g": darken(GOLD, 0.25), "G": GOLD, "r": RUNE0, "R": RUNE1}, ox=3, oy=2)
    c.set(7, 3, RUNE2)
    save(c, "block/lore_stele_front")

    # Frente lisa (estela posta por jogador): so a moldura
    b = stone_base(12)
    for i in range(2, 14):
        b.set(i, 1, STONE3)
        b.set(i, 14, STONE0)
    for i in range(1, 15):
        b.set(2, i, STONE3)
        b.set(13, i, STONE0)
    save(b, "block/lore_stele_blank")

    s = stone_base(13, moss=False)
    for i in range(16):
        s.set(i, 0, STONE3)
        s.set(i, 15, STONE0)
    save(s, "block/lore_stele_side")


def tex_tab():
    """Icone da secao no menu B: livro aberto com uma runa."""
    c = Canvas(16, 16)
    c.from_rows([
        "................",
        "................",
        ".ooooooo.ooooooo",
        "oPPPPPPPoPPPPPPo",
        "oPiiiiPPoPPrPPPo",
        "oPPPPPPPoPrRrPPo",
        "oPiiiPPPoPPrPPPo",
        "oPPPPPPPoPPPPPPo",
        "oPiiiiPPoPiiiiPo",
        "oPPPPPPPoPPPPPPo",
        "oPiiPPPPoPiiiPPo",
        "oBBBBBBBoBBBBBBo",
        ".ooooooooooooooo",
        "......ogGo......",
        ".......oo.......",
        "................",
    ], {"o": OUT, "P": PAPER2, "B": PAPER0, "i": INK, "r": RUNE0, "R": RUNE1, "g": darken(GOLD, 0.2), "G": GOLD})
    save(c, "gui/tab/lore")


# =====================================================================================================
#  Modelos / blockstate
# =====================================================================================================
def models():
    def stele(front):
        side = f"{NS}:block/lore_stele_side"
        return {
            "parent": "minecraft:block/block",
            "textures": {"front": f"{NS}:block/{front}", "back": f"{NS}:block/lore_stele_blank", "side": side, "particle": side},
            "elements": [
                {   # base
                    "from": [1, 0, 4], "to": [15, 2, 12],
                    "faces": {d: {"texture": "#side"} for d in ("north", "south", "east", "west", "up", "down")},
                },
                {   # laje em pe
                    "from": [2, 2, 5], "to": [14, 16, 11],
                    "faces": {
                        "north": {"texture": "#front", "uv": [2, 0, 14, 14]},
                        "south": {"texture": "#back", "uv": [2, 0, 14, 14]},
                        "east": {"texture": "#side", "uv": [5, 0, 11, 14]},
                        "west": {"texture": "#side", "uv": [5, 0, 11, 14]},
                        "up": {"texture": "#side", "uv": [2, 5, 14, 11]},
                    },
                },
            ],
        }

    write_json(os.path.join(A, "models", "block", "lore_stele.json"), stele("lore_stele_front"))
    write_json(os.path.join(A, "models", "block", "lore_stele_blank.json"), stele("lore_stele_blank"))
    write_json(os.path.join(A, "models", "item", "lore_stele.json"), {"parent": f"{NS}:block/lore_stele_blank"})
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for inscribed in ("true", "false"):
            v = {"model": f"{NS}:block/lore_stele" + ("" if inscribed == "true" else "_blank")}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},inscribed={inscribed}"] = v
    write_json(os.path.join(A, "blockstates", "lore_stele.json"), {"variants": variants})


# =====================================================================================================
#  Dados: loot, receita, tags
# =====================================================================================================
def item(name, weight=1, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    fn = []
    if hi > 1 or lo > 1:
        fn.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        fn.extend(extra)
    if fn:
        e["functions"] = fn
    return e


def data():
    write_json(os.path.join(D, "loot_table", "blocks", "lore_stele.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:lore_stele"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/lore_stele"})

    # Bau das ruinas: uma pagina garantida + tralha de explorador (modesto: lore nao e atalho de poder)
    write_json(os.path.join(D, "loot_table", "chests", "lore_ruin.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1, "entries": [item(f"{NS}:lore_page")]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.3}],
             "entries": [item(f"{NS}:lore_page")]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                item("minecraft:paper", 6, 1, 4), item("minecraft:book", 4, 1, 2), item("minecraft:bone", 4, 1, 3),
                item("minecraft:candle", 3, 1, 2), item("minecraft:torch", 4, 2, 6), item("minecraft:bread", 4, 1, 3),
                item("minecraft:string", 3, 1, 3), item("minecraft:feather", 3, 1, 2), item("minecraft:ink_sac", 3, 1, 2),
                item("minecraft:iron_nugget", 4, 2, 6), item("minecraft:gold_nugget", 3, 2, 5),
                item("minecraft:emerald", 2, 1, 2), item(f"{NS}:mana_crystal", 2, 1, 2),
                item("minecraft:glass_bottle", 2), item("minecraft:compass", 1), item("minecraft:map", 1)]},
        ],
        "random_sequence": f"{NS}:chests/lore_ruin"})

    # Tesouro enterrado dos mapas: vale a caminhada, sem equipamento pronto
    write_json(os.path.join(D, "loot_table", "chests", "lore_treasure.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1, "entries": [item(f"{NS}:lore_page")]},
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 6}, "entries": [
                item("minecraft:gold_ingot", 5, 2, 5), item("minecraft:emerald", 5, 2, 5), item("minecraft:iron_ingot", 5, 3, 7),
                item(f"{NS}:mana_crystal", 4, 2, 4), item(f"{NS}:guild_coin", 4, 4, 10),
                item("minecraft:amethyst_shard", 3, 2, 5), item("minecraft:experience_bottle", 3, 2, 4),
                item("minecraft:lapis_lazuli", 3, 3, 7), item("minecraft:diamond", 1, 1, 2), item("minecraft:golden_apple", 1),
                item("minecraft:book", 2, 1, 1, [{"function": "minecraft:enchant_randomly"}]),
                item("minecraft:name_tag", 1), item("minecraft:saddle", 1)]},
        ],
        "random_sequence": f"{NS}:chests/lore_treasure"})

    # Estela lisa (decoracao): 1 tijolo talhado sobre 2 tijolos de pedra
    write_json(os.path.join(D, "recipe", "lore_stele.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["C", "S", "S"],
        "key": {"C": {"item": "minecraft:chiseled_stone_bricks"}, "S": {"item": "minecraft:stone_bricks"}},
        "result": {"id": f"{NS}:lore_stele", "count": 1}})

    write_json(os.path.join(M, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"{NS}:lore_stele"]})
    # Tag de estrutura para o Diario/rastreador apontarem a ruina mais proxima
    write_json(os.path.join(M, NS, "tags", "worldgen", "structure", "lore_ruins.json"),
               {"replace": False, "values": [f"{NS}:lore_ruin", f"{NS}:lore_ruin_sand"]})
    biome = os.path.join(M, NS, "tags", "worldgen", "biome", "has_structure")
    write_json(os.path.join(biome, "lore_ruin.json"), {"replace": False, "values": [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga",
        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_plains",
        "minecraft:snowy_taiga", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills",
        "minecraft:windswept_forest", "minecraft:swamp", "minecraft:cherry_grove", "minecraft:jungle",
        "minecraft:sparse_jungle", "minecraft:grove"]})
    write_json(os.path.join(biome, "lore_ruin_sand.json"), {"replace": False, "values": [
        "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"]})


# =====================================================================================================
#  Ruinas de Velmora (estruturas pequenas)
# =====================================================================================================
STONE = {
    "brick": ["minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"],
    "floor": ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
              "minecraft:cracked_stone_bricks"],
    "rubble": ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel"],
    "chisel": "minecraft:chiseled_stone_bricks",
    "slab": "minecraft:stone_brick_slab",
    "stairs": "minecraft:stone_brick_stairs",
    "wall": "minecraft:mossy_cobblestone_wall",
}
SAND = {
    "brick": ["minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone", "minecraft:smooth_sandstone"],
    "floor": ["minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:sand"],
    "rubble": ["minecraft:sandstone", "minecraft:sand", "minecraft:smooth_sandstone"],
    "chisel": "minecraft:chiseled_sandstone",
    "slab": "minecraft:sandstone_slab",
    "stairs": "minecraft:sandstone_stairs",
    "wall": "minecraft:sandstone_wall",
}
LOOT = f"{NS}:chests/lore_ruin"


class Ruin:
    """Estrutura + paleta + acaso fixo (a mesma semente da sempre a mesma ruina)."""

    def __init__(self, sx, sy, sz, pal, seed):
        self.s = Structure(sx, sy, sz)
        self.pal = pal
        self.rnd = random.Random(seed)
        self.sx, self.sy, self.sz = sx, sy, sz

    def brick(self):
        return self.rnd.choice(self.pal["brick"])

    def floor(self, x0, z0, x1, z1, holes=0.18):
        """Piso gasto na camada 0 (que fica no nivel do chao): parte dos blocos falta e o terreno aparece."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.rnd.random() >= holes:
                    self.s.set(x, 0, z, self.rnd.choice(self.pal["floor"]))

    def column(self, x, z, h, cap=None):
        for y in range(1, h + 1):
            self.s.set(x, y, z, self.brick())
        if cap:
            self.s.set(x, h + 1, z, cap)

    def wall(self, x0, z0, x1, z1, heights):
        """Parede em linha reta com alturas irregulares (lista percorrida em ciclo)."""
        cells = [(x, z) for x in range(min(x0, x1), max(x0, x1) + 1) for z in range(min(z0, z1), max(z0, z1) + 1)]
        for i, (x, z) in enumerate(cells):
            h = heights[i % len(heights)]
            for y in range(1, h + 1):
                self.s.set(x, y, z, self.brick())

    def rubble(self, n):
        for _ in range(n):
            x, z = self.rnd.randrange(self.sx), self.rnd.randrange(self.sz)
            if self.s.get(x, 1, z) is None:
                self.s.set(x, 1, z, self.rnd.choice(self.pal["rubble"]))

    def stele(self, x, y, z, facing):
        self.s.set(x, y, z, f"{NS}:lore_stele[facing={facing},inscribed=true]")

    def chest(self, x, y, z, facing):
        self.s.chest(x, y, z, LOOT, facing)

    def barrel(self, x, y, z):
        self.s.set(x, y, z, "minecraft:barrel[facing=up]", nbt={"LootTable": LOOT})

    def save(self, folder, name):
        return self.s.save(os.path.join(D, "structure", folder, name + ".nbt"))


def ruin_circle(pal, seed):
    """Circulo de pilares quebrados em volta da estela."""
    r = Ruin(9, 6, 9, pal, seed)
    r.floor(1, 1, 7, 7, 0.3)
    for x in range(3, 6):
        for z in range(3, 6):
            r.s.set(x, 0, z, pal["chisel"] if (x, z) == (4, 4) else pal["brick"][0])
    heights = [3, 1, 4, 2, 3, 1, 2, 4]
    ring = [(4, 0), (7, 1), (8, 4), (7, 7), (4, 8), (1, 7), (0, 4), (1, 1)]
    for (x, z), h in zip(ring, heights):
        r.s.set(x, 0, z, pal["brick"][0])
        r.column(x, z, h, pal["slab"] if h >= 3 else None)
    r.stele(4, 1, 4, "south")
    r.barrel(4, 0, 6)          # enterrado no piso, na frente da estela
    r.s.set(4, 1, 6, pal["slab"])
    r.rubble(5)
    return r


def ruin_archive(pal, seed):
    """Biblioteca pequena desabada: parede do fundo, estantes, atril e bau."""
    r = Ruin(9, 6, 7, pal, seed)
    r.floor(0, 0, 8, 6, 0.12)
    r.wall(0, 0, 8, 0, [4, 4, 3, 4, 5, 4, 2, 3, 4])      # fundo (norte)
    r.wall(0, 1, 0, 5, [3, 2, 3, 1, 1])                  # oeste
    r.wall(8, 1, 8, 4, [4, 3, 1, 2])                     # leste
    for x, h in ((1, 2), (2, 3), (6, 2), (7, 1)):
        for y in range(1, h + 1):
            r.s.set(x, y, 1, "minecraft:bookshelf" if r.rnd.random() > 0.25 else "minecraft:chiseled_bookshelf[facing=south]")
    r.s.set(3, 1, 1, "minecraft:cobweb")
    r.s.set(7, 2, 1, "minecraft:cobweb")
    r.stele(4, 1, 1, "south")
    r.s.set(5, 1, 3, "minecraft:lectern[facing=south]")
    r.chest(1, 1, 3, "east")
    r.s.set(2, 1, 5, pal["slab"])
    r.s.set(6, 1, 5, pal["stairs"] + "[facing=north]")
    r.rubble(6)
    return r


def ruin_watchpost(pal, seed):
    """Base de torre de vigia: quatro paredes rasgadas, bau dentro, estela ao lado da porta."""
    r = Ruin(7, 8, 8, pal, seed)
    r.floor(0, 0, 6, 6, 0.1)
    r.wall(0, 0, 6, 0, [6, 5, 6, 4, 5, 6, 3])
    r.wall(0, 1, 0, 5, [5, 4, 2, 3, 5])
    r.wall(6, 1, 6, 5, [4, 6, 5, 2, 3])
    r.wall(0, 6, 2, 6, [4, 3, 3])
    r.wall(4, 6, 6, 6, [2, 4, 5])
    r.s.set(3, 3, 6, pal["slab"] + "[type=top]")          # verga da porta
    for y in range(1, 5):
        r.s.set(1, y, 1, "minecraft:ladder[facing=south]")
    r.s.set(1, 5, 1, pal["slab"])
    r.s.set(2, 5, 1, pal["slab"])
    r.chest(5, 1, 1, "south")
    r.s.set(4, 1, 4, "minecraft:cobweb")
    r.stele(5, 1, 7, "south")
    r.s.set(5, 0, 7, pal["brick"][0])
    r.s.set(1, 1, 7, pal["wall"])
    r.rubble(4)
    return r


def ruin_shrine(pal, seed):
    """Arco de pedra com degraus ate a estela."""
    r = Ruin(7, 7, 9, pal, seed)
    r.floor(1, 0, 5, 8, 0.2)
    for z in range(0, 3):
        for x in range(2, 5):
            r.s.set(x, 0, z, pal["brick"][0])
            r.s.set(x, 1, z, pal["brick"][0] if z < 2 else pal["stairs"] + "[facing=north]")
    r.s.set(3, 1, 1, pal["chisel"])
    r.stele(3, 2, 1, "south")
    # arco na frente
    r.column(1, 5, 4)
    r.column(5, 5, 3)
    for x in range(1, 5):
        r.s.set(x, 5, 5, pal["brick"][0] if x != 3 else pal["chisel"])
    r.s.set(5, 4, 5, pal["stairs"] + "[facing=west,half=top]")
    r.s.set(0, 1, 5, pal["wall"])
    r.s.set(6, 1, 5, pal["wall"])
    r.s.set(2, 2, 0, "minecraft:candle[candles=2,lit=false]")
    r.s.set(4, 2, 0, "minecraft:candle[candles=3,lit=false]")
    r.barrel(5, 1, 1)
    r.rubble(5)
    return r


def ruin_camp(pal, seed):
    """Acampamento de explorador abandonado ao pe de um muro antigo."""
    r = Ruin(8, 5, 7, pal, seed)
    r.floor(0, 0, 7, 1, 0.25)
    r.wall(0, 0, 7, 0, [3, 4, 3, 2, 3, 1, 2, 3])
    r.stele(3, 1, 1, "south")
    r.s.set(3, 0, 1, pal["chisel"])
    r.s.set(4, 0, 4, pal["rubble"][0])
    r.s.set(4, 1, 4, "minecraft:campfire[lit=false]")
    r.s.set(2, 1, 4, "minecraft:spruce_stairs[facing=east]")
    r.s.set(6, 1, 4, "minecraft:spruce_slab")
    # barraca: duas aguas de la
    for z in (4, 5):
        r.s.set(0, 1, z, "minecraft:white_wool")
        r.s.set(1, 2, z, "minecraft:white_wool")
        r.s.set(0, 0, z, "minecraft:coarse_dirt")
    r.s.set(1, 1, 5, "minecraft:brown_carpet")
    r.barrel(6, 1, 2)
    r.s.set(5, 1, 2, "minecraft:lectern[facing=south]")
    r.s.set(7, 1, 5, "minecraft:hay_block")
    r.rubble(3)
    return r


def ruin_obelisk(pal, seed):
    """Obelisco quebrado; a estela na base e um bau escondido embaixo dela."""
    r = Ruin(7, 9, 7, pal, seed)
    r.floor(0, 0, 6, 6, 0.35)
    for x in range(2, 5):
        for z in range(2, 5):
            r.s.set(x, 0, z, pal["brick"][0])
            r.s.set(x, 1, z, pal["brick"][0] if (x, z) != (3, 4) else pal["stairs"] + "[facing=north]")
    for y in range(2, 7):
        r.s.set(3, y, 3, pal["chisel"] if y in (2, 5) else r.brick())
    r.s.set(3, 7, 3, pal["wall"])
    # o topo caiu ali ao lado
    r.s.set(5, 1, 0, pal["brick"][0])
    r.s.set(6, 1, 0, pal["chisel"])
    r.s.set(6, 1, 1, pal["slab"])
    r.stele(3, 1, 5, "south")
    r.s.set(3, 0, 5, "minecraft:chest[facing=south]", nbt={"LootTable": LOOT})   # debaixo da estela
    r.s.set(1, 1, 3, pal["wall"])
    r.s.set(5, 1, 3, pal["wall"])
    r.rubble(5)
    return r


VARIANTS = [("circle", ruin_circle), ("archive", ruin_archive), ("watchpost", ruin_watchpost),
            ("shrine", ruin_shrine), ("camp", ruin_camp), ("obelisk", ruin_obelisk)]

EXCLUSION = [
    {"other_set": f"{NS}:guild_city", "chunk_count": 10},
    {"other_set": f"{NS}:capital_city", "chunk_count": 13},
    {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
    {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13},
]


def structures():
    total = 0
    for folder, pal, base in (("lore_ruin", STONE, 100), ("lore_ruin_sand", SAND, 200)):
        for i, (name, fn) in enumerate(VARIANTS):
            total += fn(pal, base + i).save(folder, name)
        write_json(os.path.join(D, "worldgen", "template_pool", folder, "start.json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": f"{NS}:{folder}/{name}",
                "processors": "minecraft:empty", "projection": "rigid"}} for name, _ in VARIANTS]})
        write_json(os.path.join(D, "worldgen", "structure", folder + ".json"), {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{folder}",
            "step": "surface_structures",
            "spawn_overrides": {},
            "terrain_adaptation": "beard_thin",
            "start_pool": f"{NS}:{folder}/start",
            "size": 1,
            "start_height": {"absolute": -1},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False})
    # Um conjunto so: ruinas pequenas, mais comuns que as estruturas grandes, longe das cidades
    write_json(os.path.join(D, "worldgen", "structure_set", "lore_ruins.json"), {
        "structures": [{"structure": f"{NS}:lore_ruin", "weight": 3}, {"structure": f"{NS}:lore_ruin_sand", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": 24, "separation": 10, "salt": 583117204,
                      "exclusion_zones": EXCLUSION}})
    return total


def main():
    tex_page()
    tex_stele()
    tex_tab()
    models()
    data()
    n = structures()
    print(f"lore: texturas, modelos, dados e {len(VARIANTS) * 2} ruinas ({n} blocos)")


if __name__ == "__main__":
    main()
