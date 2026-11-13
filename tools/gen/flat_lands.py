"""Gera os dados do módulo "flat_lands" (v1.6): biomas planos, Santuário Torii e Omikuji.

Uso (na raiz do projeto):  py -I tools/gen/flat_lands.py

Gera:
  - biomas data/isekai_mahou/worldgen/biome/{sakura_fields,flat_lands_golden_steppe,flat_lands_kingdom_meadows}.json
    (base = listas de features do vanilla lidas do jar do Minecraft; features NOVAS só do módulo no fim do passo 9,
    para não criar "ciclo de ordem de features")
  - placed features próprias (cerejeiras esparsas, tapete de pétalas, capim alto, acácias raras, flores do prado)
  - Santuário Torii: molde .nbt, structure, structure_set (random_spread_multi longe das cidades), template_pool,
    loot do baú; tag has_structure no merge
  - tags: #isekai_mahou:flat_lands, #isekai_mahou:sakura_offerings; tags do vanilla/NeoForge (merge);
    pacote interno flat_lands_city_tags (cidades/capitais aceitam #isekai_mahou:flat_lands)
  - Caixa do Omikuji (blockstate, modelo, texturas, loot, receita), Papel da Sorte, ícones dos efeitos
  - traduções src/main/lang/flat_lands/{pt_br,en_us}.json
"""
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte  # noqa: E402
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

NS = "isekai_mahou"
RES = os.path.join(ROOT, "src/main/resources")
D = os.path.join(RES, "data", NS)
A = os.path.join(RES, "assets", NS)
MERGE = os.path.join(ROOT, "src/main/merge/flat_lands")
LANG = os.path.join(ROOT, "src/main/lang/flat_lands")
PACK = os.path.join(RES, "flat_lands_city_tags")

SAKURA = f"{NS}:sakura_fields"
STEPPE = f"{NS}:flat_lands_golden_steppe"
MEADOWS = f"{NS}:flat_lands_kingdom_meadows"
FLAT = [SAKURA, STEPPE, MEADOWS]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tag(path, values):
    write_json(path, {"values": values})


# =================================================================================================
#  TRADUÇÕES
# =================================================================================================
PT, EN = {}, {}


def tr(key, pt, en):
    PT[key] = pt
    EN[key] = en


tr(f"biome.{NS}.sakura_fields", "Campos de Sakura", "Sakura Fields")
tr(f"biome.{NS}.flat_lands_golden_steppe", "Estepe Dourada", "Golden Steppe")
tr(f"biome.{NS}.flat_lands_kingdom_meadows", "Prados do Reino", "Kingdom Meadows")
tr(f"block.{NS}.sakura_omikuji_box", "Caixa do Omikuji", "Omikuji Box")
tr(f"item.{NS}.sakura_fortune_slip", "Papel da Sorte", "Fortune Slip")
tr(f"item.{NS}.sakura_fortune_slip.named", "Papel da Sorte: %s", "Fortune Slip: %s")
tr(f"item.{NS}.sakura_fortune_slip.blank", "Tire a sorte do dia numa Caixa do Omikuji (Santuário Torii).",
   "Draw your fortune of the day at an Omikuji Box (Torii Shrine).")
tr(f"item.{NS}.sakura_fortune_slip.curse",
   "Azar! Use este papel na Caixa do Omikuji para amarrá-lo e deixar a má sorte para trás.",
   "Bad luck! Use this slip on the Omikuji Box to tie it there and leave the misfortune behind.")
tr(f"item.{NS}.sakura_fortune_slip.keep", "Boa sorte: guarde como lembrança.", "Good fortune: keep it as a memento.")
tr(f"effect.{NS}.sakura_blessing", "Bênção da Sakura", "Sakura Blessing")
tr(f"effect.{NS}.sakura_misfortune", "Má Sorte", "Misfortune")
tr(f"pack.{NS}.flat_lands_city_tags", "Isekai Mahou: cidades nos biomas planos", "Isekai Mahou: cities in flat biomes")

FORTUNES = {
    "daikichi": ("大吉 Grande Bênção", "大吉 Great Blessing",
                 "As pétalas dançam a seu favor. Hoje tudo floresce!",
                 "The petals dance in your favor. Today everything blooms!"),
    "chukichi": ("中吉 Bênção Média", "中吉 Middle Blessing",
                 "Um bom vento sopra nas suas costas.", "A good wind blows at your back."),
    "kichi": ("吉 Bênção", "吉 Blessing",
              "A sorte sorri para quem segue em frente.", "Fortune smiles on those who keep going."),
    "shokichi": ("小吉 Pequena Bênção", "小吉 Small Blessing",
                 "Pequenas alegrias pelo caminho.", "Small joys along the way."),
    "suekichi": ("末吉 Bênção Futura", "末吉 Future Blessing",
                 "Hoje nada muda... a sorte chega mais tarde.", "Nothing changes today... luck comes later."),
    "kyo": ("凶 Azar", "凶 Misfortune",
            "Nuvens no céu. Amarre o papel no santuário.", "Clouds in the sky. Tie the slip at the shrine."),
    "daikyo": ("大凶 Grande Azar", "大凶 Great Misfortune",
               "Um corvo cruzou o seu caminho... amarre logo este papel!",
               "A crow crossed your path... tie this slip at once!"),
}
for fid, (pt, en, tpt, ten) in FORTUNES.items():
    tr(f"fortune.{NS}.sakura.{fid}", pt, en)
    tr(f"fortune.{NS}.sakura.{fid}.text", tpt, ten)

tr(f"message.{NS}.sakura.omikuji.drawn", "Omikuji: você tirou %s", "Omikuji: you drew %s")
tr(f"message.{NS}.sakura.omikuji.already", "Você já tirou a sorte hoje. Volte amanhã!",
   "You already drew your fortune today. Come back tomorrow!")
tr(f"message.{NS}.sakura.omikuji.hint",
   "Deixe uma oferenda (pepita de ouro, esmeralda ou moeda da guilda) para tirar a sorte do dia.",
   "Leave an offering (gold nugget, emerald or guild coin) to draw your fortune of the day.")
tr(f"message.{NS}.sakura.omikuji.keep_good", "Papel de boa sorte não se amarra: guarde com você!",
   "Good-luck slips are not tied: keep it with you!")
tr(f"message.{NS}.sakura.omikuji.tie_hint",
   "Dica: use o papel na Caixa do Omikuji para amarrá-lo e se livrar da má sorte.",
   "Tip: use the slip on the Omikuji Box to tie it and get rid of the misfortune.")
tr(f"message.{NS}.sakura.omikuji.tied", "Você amarrou o papel no santuário. A má sorte ficou para trás.",
   "You tied the slip at the shrine. The misfortune stays behind.")
tr(f"command.{NS}.flat_lands.reset", "Omikuji liberado de novo para %s jogador(es).", "Omikuji reset for %s player(s).")
tr(f"command.{NS}.flat_lands.unknown", "Sorte desconhecida: %s", "Unknown fortune: %s")

SIGN = [("~ Santuário ~", "~ Torii ~"), ("Torii", "Shrine"), ("Omikuji: deixe", "Omikuji: leave"),
        ("uma oferenda", "an offering")]
for i, (pt, en) in enumerate(SIGN, 1):
    tr(f"sign.{NS}.flat_lands.shrine.{i}", pt, en)


# =================================================================================================
#  BIOMAS
# =================================================================================================
def vanilla_biome(name):
    jars = glob.glob(os.path.join(ROOT, "build/moddev/artifacts/*client-extra*minecraft-resources.jar"))
    if not jars:
        sys.exit("jar de recursos do Minecraft não encontrado em build/moddev/artifacts")
    with zipfile.ZipFile(jars[0]) as z:
        return json.loads(z.read(f"data/minecraft/worldgen/biome/{name}.json"))


def placed(name, feature, placement):
    write_json(os.path.join(D, "worldgen/placed_feature", name + ".json"), {"feature": feature, "placement": placement})


def survive(sapling):
    return {"type": "minecraft:block_predicate_filter",
            "predicate": {"type": "minecraft:would_survive", "state": {"Name": sapling, "Properties": {"stage": "0"}}}}


def weighted_count(pairs):
    return {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list",
                                                  "distribution": [{"data": d, "weight": w} for d, w in pairs]}}


SQ = {"type": "minecraft:in_square"}
BIO = {"type": "minecraft:biome"}
DRY = {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}


def hm(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def gen_features():
    # Cerejeiras ESPARSAS (o Bosque de Cerejeiras do vanilla tem ~10 por chunk; aqui 0–2).
    placed("sakura_fields_trees", "minecraft:cherry_bees_005",
           [weighted_count([(0, 5), (1, 4), (2, 1)]), SQ, DRY, hm("OCEAN_FLOOR"), BIO, survive("minecraft:cherry_sapling")])
    # Tapete de pétalas rosas no chão.
    placed("sakura_fields_petals", "minecraft:flower_cherry",
           [{"type": "minecraft:noise_threshold_count", "noise_level": -0.8, "below_noise": 3, "above_noise": 6},
            SQ, hm("MOTION_BLOCKING"), BIO])
    # Capim alto em quantidade (a cor dourada vem do grass_color do bioma).
    placed("flat_lands_golden_steppe_tall_grass", "minecraft:patch_tall_grass",
           [{"type": "minecraft:count", "count": 5}, SQ, hm("MOTION_BLOCKING"), BIO])
    # Uma acácia solitária a cada ~7 chunks.
    placed("flat_lands_golden_steppe_trees", "minecraft:acacia",
           [{"type": "minecraft:rarity_filter", "chance": 7}, SQ, DRY, hm("OCEAN_FLOOR"), BIO,
            survive("minecraft:acacia_sapling")])
    # Flores do prado + canteiros variados (tulipas, alliums...).
    placed("flat_lands_kingdom_meadows_flowers", "minecraft:flower_meadow",
           [{"type": "minecraft:count", "count": 3}, SQ, hm("MOTION_BLOCKING"), BIO])
    placed("flat_lands_kingdom_meadows_bouquets", "minecraft:flower_flower_forest",
           [{"type": "minecraft:count", "count": 2}, SQ, hm("MOTION_BLOCKING"), BIO])


def make_biome(base, out_id, temperature, downfall, precipitation, effects, remove, add, extra_creatures=()):
    b = vanilla_biome(base)
    feats = [list(step) for step in b["features"]]
    feats[9] = [f for f in feats[9] if f not in remove] + [f"{NS}:{a}" for a in add]
    b["features"] = feats
    b["temperature"] = temperature
    b["downfall"] = downfall
    b["has_precipitation"] = precipitation
    eff = dict(b["effects"])
    eff.update(effects)
    b["effects"] = eff
    for c in extra_creatures:
        b["spawners"]["creature"].append(c)
    write_json(os.path.join(D, "worldgen/biome", out_id + ".json"), b)


def gen_biomes():
    make_biome("plains", "sakura_fields", 0.6, 0.7, True, {
        "grass_color": 11983713, "foliage_color": 11983713, "sky_color": 8103167,
        "water_color": 6141935, "water_fog_color": 6141935,
        "music": {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False,
                  "sound": "minecraft:music.overworld.cherry_grove"},
        # pétalas caindo no ar (partícula das folhas de cerejeira)
        "particle": {"options": {"type": "minecraft:cherry_leaves"}, "probability": 0.0012},
    }, remove={"minecraft:trees_plains"}, add=["sakura_fields_trees", "sakura_fields_petals"],
        extra_creatures=[{"type": "minecraft:rabbit", "maxCount": 3, "minCount": 2, "weight": 4}])
    make_biome("savanna", "flat_lands_golden_steppe", 1.0, 0.2, True, {
        "grass_color": 13742934, "foliage_color": 12558666, "sky_color": 7254527,
    }, remove={"minecraft:trees_savanna"},
        add=["flat_lands_golden_steppe_tall_grass", "flat_lands_golden_steppe_trees"])
    make_biome("sunflower_plains", "flat_lands_kingdom_meadows", 0.7, 0.6, True, {
        "sky_color": 8103167,
        "music": {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False,
                  "sound": "minecraft:music.overworld.meadow"},
    }, remove=set(), add=["flat_lands_kingdom_meadows_flowers", "flat_lands_kingdom_meadows_bouquets"])


def gen_tags():
    tag(os.path.join(D, "tags/worldgen/biome/flat_lands.json"), FLAT)
    tag(os.path.join(D, "tags/item/sakura_offerings.json"),
        ["minecraft:gold_nugget", "minecraft:emerald", {"id": f"{NS}:guild_coin", "required": False}])
    mc = os.path.join(MERGE, "data/minecraft/tags/worldgen/biome")
    tag(os.path.join(mc, "is_overworld.json"), FLAT)
    tag(os.path.join(mc, "stronghold_biased_to.json"), FLAT)
    tag(os.path.join(mc, "has_structure/village_plains.json"), [SAKURA, MEADOWS])
    tag(os.path.join(mc, "has_structure/village_savanna.json"), [STEPPE])
    for s in ("pillager_outpost", "mineshaft", "ruined_portal_standard"):
        tag(os.path.join(mc, f"has_structure/{s}.json"), FLAT)
    c = os.path.join(MERGE, "data/c/tags/worldgen/biome")
    tag(os.path.join(c, "is_overworld.json"), FLAT)
    tag(os.path.join(c, "is_plains.json"), FLAT)
    tag(os.path.join(c, "is_floral.json"), [SAKURA, MEADOWS])
    tag(os.path.join(c, "is_temperate/overworld.json"), [SAKURA, MEADOWS])
    tag(os.path.join(c, "is_dry/overworld.json"), [STEPPE])
    tag(os.path.join(c, "is_sparse_vegetation/overworld.json"), [STEPPE])
    tag(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"), [f"{NS}:sakura_omikuji_box"])
    tag(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/sakura_torii_shrine.json"),
        [SAKURA, MEADOWS, "minecraft:cherry_grove", "minecraft:meadow", "minecraft:flower_forest"])
    # Pacote interno (sempre ativo): as tags das cidades/capitais passam a aceitar os biomas planos.
    write_json(os.path.join(PACK, "pack.mcmeta"),
               {"pack": {"description": {"translate": f"pack.{NS}.flat_lands_city_tags"}, "pack_format": 48}})
    for city in ("guild_city", "capital_city", "capital_sahra", "capital_nordheim"):
        write_json(os.path.join(PACK, f"data/{NS}/tags/worldgen/biome/has_structure/{city}.json"),
                   {"replace": False, "values": [f"#{NS}:flat_lands"]})


# =================================================================================================
#  SANTUÁRIO TORII (x = leste, z = sul; entrada no SUL; y=0 = camada do chão, y=1 = onde se pisa)
# =================================================================================================
W, H, L = 15, 12, 25
CX = 7
RED = "minecraft:red_concrete"
BASE = "minecraft:polished_blackstone"
TOPSLAB = "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]"
TOPEND = "minecraft:polished_blackstone_slab[type=top,waterlogged=false]"
POST = "minecraft:stripped_mangrove_wood[axis=y]"
LEAVES = "minecraft:cherry_leaves[distance=7,persistent=true,waterlogged=false]"


def stairs(block, facing, half="bottom"):
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def torii(s, z, x0, x1, h):
    """Portal torii: pilares em x0/x1 (base preta), nuki (viga de baixo, passa dos pilares), kasagi (topo largo)."""
    for x in (x0, x1):
        s.set(x, 1, z, BASE)
        s.fill(x, 2, z, x, h, z, RED)
    s.fill(x0 - 1, h - 1, z, x1 + 1, h - 1, z, RED)       # nuki
    s.set(CX, h, z, RED)                                   # gakuzuka (escora do meio)
    s.fill(x0 - 1, h + 1, z, x1 + 1, h + 1, z, RED)       # shimaki
    s.fill(x0 - 1, h + 2, z, x1 + 1, h + 2, z, TOPSLAB)   # kasagi
    s.set(x0 - 2, h + 2, z, TOPEND)                        # pontas levantadas
    s.set(x1 + 2, h + 2, z, TOPEND)


def toro(s, x, z):
    """Lanterna de pedra (tōrō)."""
    s.set(x, 1, z, "minecraft:stone_bricks")
    s.set(x, 2, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    s.set(x, 3, z, "minecraft:lantern[hanging=false,waterlogged=false]")
    s.set(x, 4, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]")


def cherry_tree(s, x, z, h=4):
    s.fill(x, 1, z, x, h, z, "minecraft:cherry_log[axis=y]")
    for dy, r in ((h - 1, 2), (h, 2), (h + 1, 1)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r:
                    continue
                if s.get(x + dx, dy, z + dz) in (None, "minecraft:air"):
                    s.set(x + dx, dy, z + dz, LEAVES)


def gen_shrine():
    s = Structure(W, H, L)
    s.fill(0, 1, 0, W - 1, H - 1, L - 1, "minecraft:air")
    s.fill(0, 0, 0, W - 1, 0, L - 1, "minecraft:grass_block[snowy=false]")
    # Caminho (sandō): pedra polida no meio, cascalho nas bordas.
    s.fill(CX - 1, 0, 9, CX + 1, 0, L - 1, "minecraft:polished_andesite")
    for z in range(9, L):
        s.set(CX - 2, 0, z, "minecraft:gravel")
        s.set(CX + 2, 0, z, "minecraft:gravel")
    torii(s, 20, 3, 11, 6)
    torii(s, 12, 5, 9, 4)
    for z in (16, 10):
        toro(s, 4, z)
        toro(s, 10, z)
    # Salão (haiden) em x=2..12, z=1..7, com beiral do telhado até x=1..13, z=0..8.
    s.fill(2, 0, 1, 12, 0, 7, "minecraft:stone_bricks")
    s.fill(2, 1, 1, 12, 1, 7, "minecraft:cherry_planks")
    s.fill(CX - 1, 1, 8, CX + 1, 1, 8, stairs("cherry", "north"))
    for x, z in ((2, 1), (12, 1), (2, 7), (12, 7), (5, 7), (9, 7), (2, 4), (12, 4)):
        s.fill(x, 2, z, x, 4, z, POST)
    for x in range(3, 12):
        for y in range(2, 5):
            s.set(x, y, 1, "minecraft:white_concrete" if y == 3 else "minecraft:cherry_planks")
    for z in (2, 3, 5, 6):
        for y in range(2, 5):
            st = "minecraft:white_concrete" if y == 3 else "minecraft:cherry_planks"
            s.set(2, y, z, st)
            s.set(12, y, z, st)
    s.fill(2, 5, 1, 12, 5, 7, POST.replace("axis=y", "axis=x"))
    s.fill(3, 5, 2, 11, 5, 6, "minecraft:cherry_planks")
    # Telhado de duas águas (cumeeira no eixo x).
    for i, (zn, zs) in enumerate(((0, 8), (1, 7), (2, 6), (3, 5))):
        y = 6 + i
        s.fill(1, y, zn, 13, y, zn, stairs("dark_oak", "south"))
        s.fill(1, y, zs, 13, y, zs, stairs("dark_oak", "north"))
        if zs - zn > 1:
            for x in (2, 12):
                s.fill(x, y, zn + 1, x, y, zs - 1, "minecraft:cherry_planks")
    s.fill(1, 10, 4, 13, 10, 4, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]")
    # Dentro: altar, sino, lanternas, Caixa do Omikuji e o baú das oferendas.
    s.set(CX - 1, 2, 2, "minecraft:cherry_planks")
    s.set(CX + 1, 2, 2, "minecraft:cherry_planks")
    s.set(CX - 1, 3, 2, "minecraft:potted_cherry_sapling")
    s.set(CX + 1, 3, 2, "minecraft:potted_cherry_sapling")
    s.set(CX, 2, 2, "minecraft:cherry_planks")
    s.set(CX, 3, 2, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    s.set(CX, 4, 7, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    for x in (4, 10):
        s.set(x, 4, 4, "minecraft:lantern[hanging=true,waterlogged=false]")
    s.set(4, 2, 6, f"{NS}:sakura_omikuji_box[facing=south]")
    s.set(10, 2, 6, f"{NS}:sakura_omikuji_box[facing=south]")
    s.chest(3, 2, 3, f"{NS}:chests/sakura_torii_shrine", facing="east")
    # Corda para amarrar os papéis de azar (musubi-dokoro), ao lado do caminho.
    for x in (11, 13):
        s.fill(x, 1, 9, x, 2, 9, "minecraft:dark_oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    s.set(12, 2, 9, "minecraft:chain[axis=x,waterlogged=false]")
    s.set(12, 1, 9, "minecraft:white_carpet")
    # Cerejeiras e pétalas.
    for x, z in ((1, 17), (13, 17), (1, 23), (13, 23)):
        cherry_tree(s, x, z)
    for x, z in ((2, 14), (12, 14), (3, 22), (11, 22), (1, 11), (13, 13), (4, 18), (11, 15), (2, 20), (12, 19)):
        if s.get(x, 1, z) in (None, "minecraft:air"):
            s.set(x, 1, z, f"minecraft:pink_petals[facing=north,flower_amount={1 + (x + z) % 4}]")
    # Placa na entrada.
    msgs = [json.dumps({"translate": f"sign.{NS}.flat_lands.shrine.{i}"}) for i in range(1, 5)]
    text = {"messages": msgs, "color": "black", "has_glowing_text": Byte(0)}
    s.set(CX + 3, 1, L - 2, "minecraft:cherry_sign[rotation=0,waterlogged=false]",
          nbt={"front_text": text, "back_text": text, "is_waxed": Byte(1)})
    n = s.save(os.path.join(D, "structure/flat_lands/sakura_torii_shrine.nbt"), keep_air=True)
    print("santuário:", n, "blocos")

    write_json(os.path.join(D, "worldgen/structure/sakura_torii_shrine.json"), {
        "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/sakura_torii_shrine", "step": "surface_structures",
        "terrain_adaptation": "beard_thin", "spawn_overrides": {}, "start_pool": f"{NS}:flat_lands/sakura_torii_shrine",
        "size": 1, "start_height": {"absolute": 0}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(D, "worldgen/template_pool/flat_lands/sakura_torii_shrine.json"), {
        "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element", "location": f"{NS}:flat_lands/sakura_torii_shrine",
            "processors": "minecraft:empty", "projection": "rigid"}}]})
    write_json(os.path.join(D, "worldgen/structure_set/sakura_torii_shrine.json"), {
        "structures": [{"structure": f"{NS}:sakura_torii_shrine", "weight": 1}],
        "placement": {"type": f"{NS}:random_spread_multi", "spacing": 32, "separation": 11, "salt": 1946316001,
                      "exclusion_zones": [
                          {"other_set": "minecraft:villages", "chunk_count": 4},
                          {"other_set": f"{NS}:guild_city", "chunk_count": 10},
                          {"other_set": f"{NS}:capital_city", "chunk_count": 13},
                          {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
                          {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13}]}})


def item(name, w, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    if hi > 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def gen_loot_and_recipes():
    write_json(os.path.join(D, "loot_table/chests/sakura_torii_shrine.json"), {
        "type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                item("minecraft:gold_nugget", 12, 3, 8), item("minecraft:emerald", 10, 1, 3),
                item("minecraft:paper", 8, 2, 5), item("minecraft:cherry_sapling", 8, 1, 2),
                item("minecraft:pink_petals", 8, 2, 6), item(f"{NS}:essence_light", 6, 1, 2),
                item(f"{NS}:essence_wind", 5, 1, 2), item(f"{NS}:mana_crystal", 4, 1, 2),
                item("minecraft:golden_apple", 2)]},
            {"rolls": 1, "entries": [item(f"{NS}:sakura_omikuji_box", 3), {"type": "minecraft:empty", "weight": 7}]}]})
    write_json(os.path.join(D, "loot_table/blocks/sakura_omikuji_box.json"), {
        "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
                                              "entries": [{"type": "minecraft:item", "name": f"{NS}:sakura_omikuji_box"}],
                                              "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(D, "recipe/sakura_omikuji_box.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["CPC", "CGC", "SSS"],
        "key": {"C": {"item": "minecraft:cherry_planks"}, "P": {"item": "minecraft:paper"},
                "G": {"item": "minecraft:gold_nugget"}, "S": {"item": "minecraft:cherry_slab"}},
        "result": {"id": f"{NS}:sakura_omikuji_box", "count": 1}})


# =================================================================================================
#  ARTE
# =================================================================================================
VERM = hex_rgba("C8372D")
VERM_L = lighten(VERM, 0.25)
VERM_D = darken(VERM, 0.35)
WOOD = hex_rgba("4A2E22")
WOOD_L = lighten(WOOD, 0.2)
WOOD_D = darken(WOOD, 0.35)
GOLD = hex_rgba("F2C14E")
GOLD_D = darken(GOLD, 0.3)
PINK = hex_rgba("F7A8C8")
PINK_L = lighten(PINK, 0.4)
PINK_D = darken(PINK, 0.25)
PAPER = hex_rgba("F4EFE2")
PAPER_D = darken(PAPER, 0.15)


def tex_box_side(front):
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, VERM)
    c.noise(0.06, seed=11 + front)
    c.outline_rect(0, 0, 15, 15, WOOD)
    c.rect(0, 0, 15, 1, WOOD_L)
    c.rect(0, 14, 15, 15, WOOD_D)
    for y in range(2, 14):
        c.set(1, y, VERM_L)
        c.set(14, y, VERM_D)
    if front:
        # placa dourada com um "ideograma" estilizado (籤)
        c.rect(5, 3, 10, 12, GOLD_D)
        c.rect(6, 4, 9, 11, PAPER)
        for x, y in ((7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (7, 8), (8, 8), (6, 9), (8, 9), (9, 9),
                     (7, 10), (8, 10)):
            c.set(x, y, VERM_D)
    return c


def gen_art():
    tb = os.path.join(A, "textures/block")
    tex_box_side(False).save(os.path.join(tb, "sakura_omikuji_box_side.png"))
    tex_box_side(True).save(os.path.join(tb, "sakura_omikuji_box_front.png"))
    top = Canvas(16, 16)
    top.rect(0, 0, 15, 15, WOOD)
    top.noise(0.08, seed=5)
    top.outline_rect(0, 0, 15, 15, WOOD_D)
    top.rect(6, 6, 9, 9, (20, 12, 10, 255))   # buraco por onde sai o papel
    top.outline_rect(5, 5, 10, 10, GOLD_D)
    top.set(7, 5, PAPER)
    top.set(7, 4, PAPER_D)
    top.save(os.path.join(tb, "sakura_omikuji_box_top.png"))
    base = Canvas(16, 16)
    base.rect(0, 0, 15, 15, WOOD_D)
    base.noise(0.1, seed=7)
    for x in range(0, 16, 4):
        for y in range(16):
            base.set(x, y, darken(WOOD_D, 0.25))
    base.save(os.path.join(tb, "sakura_omikuji_box_base.png"))

    # Papel da Sorte: tira de papel dobrada com fita rosa.
    ti = os.path.join(A, "textures/item")
    s = Canvas(16, 16)
    rows = [
        "......pp........",
        ".....pPPp.......",
        "....pPPPPp......",
        "...pPPrPPPp.....",
        "...pPPPPrPPp....",
        "....pPrPPPPPp...",
        ".....pPPPrPPPp..",
        "......kKKkPPPp..",
        "......kKKkrPPp..",
        ".......pPPPPPp..",
        "........pPrPPp..",
        ".........pPPPp..",
        "..........pPp...",
        "...........p....",
    ]
    s.from_rows(rows, {"p": PAPER_D, "P": PAPER, "r": VERM, "k": PINK_D, "K": PINK}, oy=1)
    s.outline(darken(PAPER_D, 0.45))
    s.save(os.path.join(ti, "sakura_fortune_slip.png"))

    # Ícones dos efeitos (18×18): flor de sakura / nuvem de azar.
    te = os.path.join(A, "textures/mob_effect")
    f = Canvas(18, 18)
    import math
    for i in range(5):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        f.circle(9 + math.cos(a) * 4.2, 9 + math.sin(a) * 4.2, 3.2, PINK)
    f.shade_sphere(9, 9, 8, light=0.35, dark=0.3)
    for i in range(5):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        f.set(int(9 + math.cos(a) * 6.5), int(9 + math.sin(a) * 6.5), (0, 0, 0, 0))
        # vinco entre as pétalas (do centro para fora)
        b = a + math.pi / 5
        f.line(9, 9, int(round(9 + math.cos(b) * 6)), int(round(9 + math.sin(b) * 6)), PINK_D)
    f.circle(9, 9, 2, GOLD)
    f.set(8, 8, PINK_L)
    f.outline(darken(PINK, 0.5))
    f.save(os.path.join(te, "sakura_blessing.png"))
    m = Canvas(18, 18)
    cloud = hex_rgba("6E5A8E")
    for cx, cy, r in ((6, 8, 3.5), (10, 7, 4.2), (13, 9, 3), (9, 10, 3.5)):
        m.circle(cx, cy, r, cloud)
    m.shade_sphere(9, 8, 8, light=0.3, dark=0.35)
    m.line(9, 12, 7, 15, GOLD)
    m.line(7, 15, 10, 15, GOLD)
    m.line(10, 15, 8, 17, GOLD)
    m.set(5, 13, PINK_D)
    m.set(12, 14, PINK_D)
    m.outline(darken(cloud, 0.55))
    m.save(os.path.join(te, "sakura_misfortune.png"))

    # Modelos.
    bm = {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": f"{NS}:block/sakura_omikuji_box_side", "side": f"{NS}:block/sakura_omikuji_box_side",
                     "front": f"{NS}:block/sakura_omikuji_box_front", "top": f"{NS}:block/sakura_omikuji_box_top",
                     "base": f"{NS}:block/sakura_omikuji_box_base"},
        "elements": [
            {"from": [2, 0, 2], "to": [14, 2, 14], "faces": {d: {"texture": "#base"} for d in
                                                              ("north", "south", "east", "west", "up", "down")}},
            {"from": [4, 2, 4], "to": [12, 13, 12], "faces": {
                "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                "west": {"texture": "#side"}}},
            {"from": [3.5, 13, 3.5], "to": [12.5, 14, 12.5], "faces": {
                "north": {"texture": "#base"}, "south": {"texture": "#base"}, "east": {"texture": "#base"},
                "west": {"texture": "#base"}, "up": {"texture": "#top"}, "down": {"texture": "#base"}}},
        ],
    }
    write_json(os.path.join(A, "models/block/sakura_omikuji_box.json"), bm)
    write_json(os.path.join(A, "models/item/sakura_omikuji_box.json"), {"parent": f"{NS}:block/sakura_omikuji_box"})
    write_json(os.path.join(A, "blockstates/sakura_omikuji_box.json"), {"variants": {
        f"facing={d}": ({"model": f"{NS}:block/sakura_omikuji_box", "y": r} if r else {"model": f"{NS}:block/sakura_omikuji_box"})
        for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(os.path.join(A, "models/item/sakura_fortune_slip.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/sakura_fortune_slip"}})


def main():
    gen_features()
    gen_biomes()
    gen_tags()
    gen_shrine()
    gen_loot_and_recipes()
    gen_art()
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(PT.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))
    print("flat_lands: ok")


if __name__ == "__main__":
    main()
