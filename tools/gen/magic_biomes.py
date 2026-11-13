"""Gera os dados do módulo "magic_biomes" (1.0): biomas mágicos do Overworld, Nether, End e a Caverna de Cristal.

Uso (na raiz do projeto):  py -I tools/gen/magic_biomes.py

Gera:
  - 6 biomas em data/isekai_mahou/worldgen/biome/ (base = bioma do vanilla lido do jar do Minecraft; as features do
    módulo entram SEMPRE no fim do passo, numa ordem global fixa, para não criar "ciclo de ordem de features")
  - configured/placed features próprias (prefixo magic_biomes_)
  - tags de bioma (#isekai_mahou:magic_biomes no resources; tags do vanilla/NeoForge/estruturas no merge)
  - blocos: 3 aglomerados de cristal, Bloco de Cristal Luminoso, Folhas de Cristal (blockstate, modelo, textura,
    loot, receita, tags de ferramenta)
  - capítulo do diário (data/isekai_mahou/journal/magic_biomes.json)
  - traduções src/main/lang/magic_biomes/{pt_br,en_us}.json
"""
import copy
import glob
import json
import os
import random
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken  # noqa: E402

NS = "isekai_mahou"
RES = os.path.join(ROOT, "src/main/resources")
D = os.path.join(RES, "data", NS)
A = os.path.join(RES, "assets", NS)
MERGE = os.path.join(ROOT, "src/main/merge/magic_biomes")
LANG = os.path.join(ROOT, "src/main/lang/magic_biomes")
P = "magic_biomes_"  # prefixo das features

CRYSTAL_FOREST = f"{NS}:crystal_forest"
HAUNTED_SWAMP = f"{NS}:haunted_swamp"
TWILIGHT_GROVE = f"{NS}:twilight_grove"
CRYSTAL_CAVERN = f"{NS}:crystal_cavern"
EMBER_WASTES = f"{NS}:ember_crystal_wastes"
ASTRAL_GARDENS = f"{NS}:astral_gardens"
SURFACE = [CRYSTAL_FOREST, HAUNTED_SWAMP, TWILIGHT_GROVE]
OVERWORLD = SURFACE + [CRYSTAL_CAVERN]
ALL = OVERWORLD + [EMBER_WASTES, ASTRAL_GARDENS]

LUMINOUS = f"{NS}:luminous_crystal_cluster"
EMBER = f"{NS}:ember_crystal_cluster"
ASTRAL = f"{NS}:astral_crystal_cluster"
CRYSTAL_BLOCK = f"{NS}:luminous_crystal_block"
LEAVES = f"{NS}:luminous_leaves"


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


tr(f"biome.{NS}.crystal_forest", "Floresta de Cristal", "Crystal Forest")
tr(f"biome.{NS}.haunted_swamp", "Pântano Assombrado", "Haunted Swamp")
tr(f"biome.{NS}.twilight_grove", "Bosque Crepuscular", "Twilight Grove")
tr(f"biome.{NS}.crystal_cavern", "Caverna de Cristal Mágico", "Magic Crystal Cavern")
tr(f"biome.{NS}.ember_crystal_wastes", "Ermo de Cristal Ígneo", "Embercrystal Wastes")
tr(f"biome.{NS}.astral_gardens", "Jardins Astrais", "Astral Gardens")

tr(f"block.{NS}.luminous_crystal_cluster", "Aglomerado de Cristal Luminoso", "Luminous Crystal Cluster")
tr(f"block.{NS}.ember_crystal_cluster", "Aglomerado de Cristal Ígneo", "Embercrystal Cluster")
tr(f"block.{NS}.astral_crystal_cluster", "Aglomerado de Cristal Astral", "Astral Crystal Cluster")
tr(f"block.{NS}.luminous_crystal_block", "Bloco de Cristal Luminoso", "Luminous Crystal Block")
tr(f"block.{NS}.luminous_leaves", "Folhas de Cristal", "Crystal Leaves")

tr(f"hint.{NS}.magic_biomes.crystal_forest",
   "Floresta de Cristal: o ar daqui é carregado — sua mana volta um pouco mais rápido.",
   "Crystal Forest: the air here is charged — your mana returns a little faster.")
tr(f"hint.{NS}.magic_biomes.haunted_swamp",
   "Pântano Assombrado: espíritos e sombras rondam a névoa. À noite é pior.",
   "Haunted Swamp: spirits and shades roam the mist. It gets worse at night.")
tr(f"hint.{NS}.magic_biomes.twilight_grove",
   "Bosque Crepuscular: sob as copas é sempre fim de tarde. Os cristais violetas mostram o caminho.",
   "Twilight Grove: under the canopy it is always dusk. The violet crystals show the way.")
tr(f"hint.{NS}.magic_biomes.crystal_cavern",
   "Caverna de Cristal Mágico: os cristais iluminam tudo e a mana volta mais rápido aqui.",
   "Magic Crystal Cavern: the crystals light everything and mana returns faster here.")
tr(f"hint.{NS}.magic_biomes.ember_crystal_wastes",
   "Ermo de Cristal Ígneo: cristais em brasa crescem no chão e no teto do Nether.",
   "Embercrystal Wastes: glowing ember crystals grow on the Nether's floor and ceiling.")
tr(f"hint.{NS}.magic_biomes.astral_gardens",
   "Jardins Astrais: ilhas do End cobertas de ametista e cristais que brilham como estrelas.",
   "Astral Gardens: End islands covered in amethyst and crystals that shine like stars.")

tr(f"command.{NS}.magic_biomes.searching", "Procurando %s...", "Searching for %s...")
tr(f"command.{NS}.magic_biomes.found", "%s em %s, %s, %s (%s).", "%s at %s, %s, %s (%s).")
tr(f"command.{NS}.magic_biomes.not_found", "Nenhum(a) %s a até %s blocos (só nasce em terreno NOVO).",
   "No %s within %s blocks (it only generates in NEW terrain).")
tr(f"command.{NS}.magic_biomes.no_level", "A dimensão %s não está carregada.", "Dimension %s is not loaded.")
tr(f"command.{NS}.magic_biomes.where", "Bioma atual: %s (mágico: %s).", "Current biome: %s (magic: %s).")
tr(f"command.{NS}.magic_biomes.visits_reset", "Visitas aos biomas mágicos reiniciadas.", "Magic biome visits reset.")

# Diário
J = f"journal.{NS}.magic_biomes"
tr(f"{J}.title", "Terras Encantadas", "Enchanted Lands")
tr(f"{J}.intro", "Há lugares onde a mana vazou para a terra e nunca mais saiu.",
   "There are places where mana leaked into the land and never left.")
tr(f"{J}.lore",
   "Florestas de folhas de vidro, pântanos onde os mortos não descansam, cavernas que brilham sozinhas. "
   "São raros e só existem em terras ainda não exploradas.",
   "Forests with glass leaves, swamps where the dead do not rest, caverns that glow on their own. "
   "They are rare and only exist in lands not yet explored.")
STEPS = [
    ("crystal_forest", "Visite a Floresta de Cristal", "Visit the Crystal Forest",
     "Florestas frias. Procure copas azul-claras e pedras que brilham.",
     "Cool forests. Look for pale blue canopies and glowing rocks."),
    ("haunted_swamp", "Atravesse o Pântano Assombrado", "Cross the Haunted Swamp",
     "Um pântano cinzento, de água escura e árvores mortas com lanternas azuis.",
     "A grey swamp with dark water and dead trees with blue lanterns."),
    ("twilight_grove", "Entre no Bosque Crepuscular", "Enter the Twilight Grove",
     "Uma floresta escura de grama roxa. Fica no meio das Florestas Escuras.",
     "A dark forest with purple grass. It lies in the middle of Dark Forests."),
    ("crystal_cavern", "Ache uma Caverna de Cristal Mágico", "Find a Magic Crystal Cavern",
     "Debaixo de terras secas, bem fundo. Você vai ver o brilho antes de chegar.",
     "Beneath dry lands, deep down. You will see the glow before you arrive."),
    ("ember_crystal_wastes", "Chegue ao Ermo de Cristal Ígneo", "Reach the Embercrystal Wastes",
     "No Nether. Chão de pedra negra e cristais cor de brasa.",
     "In the Nether. Blackstone ground and ember-colored crystals."),
    ("astral_gardens", "Pise nos Jardins Astrais", "Set foot in the Astral Gardens",
     "Nas ilhas de fora do End, longe da ilha do dragão.",
     "On the outer End islands, far from the dragon's island."),
]
LORE = {
    "crystal_forest": ("As folhas tilintam com o vento. Dizem que cada árvore guarda um pouco da primeira mana do mundo.",
                       "The leaves chime in the wind. They say each tree keeps a little of the world's first mana.",
                       "Florestas frias", "Cool forests"),
    "haunted_swamp": ("Quem morreu aqui não foi embora. As lanternas azuis não foram acesas por gente viva.",
                      "Those who died here never left. The blue lanterns were not lit by the living.",
                      "Pântanos úmidos", "Wet swamps"),
    "twilight_grove": ("O sol nunca termina de se pôr debaixo destas copas.",
                       "The sun never finishes setting beneath this canopy.",
                       "Dentro de Florestas Escuras", "Inside Dark Forests"),
    "crystal_cavern": ("A mana escorreu pela pedra por eras e virou cristal. A caverna respira luz.",
                       "Mana seeped through the stone for ages and became crystal. The cavern breathes light.",
                       "Fundo, sob terras secas", "Deep under dry lands"),
    "ember_crystal_wastes": ("Até o fogo do Nether cristaliza quando a mana é demais.",
                             "Even the Nether's fire crystallizes when there is too much mana.",
                             "Nether: quente e seco", "Nether: hot and dry"),
    "astral_gardens": ("Jardins que ninguém plantou, virados para estrelas que não existem.",
                       "Gardens no one planted, facing stars that do not exist.",
                       "Ilhas de fora do End", "Outer End islands"),
}
for sid, pt, en, hpt, hen in STEPS:
    tr(f"{J}.{sid}", pt, en)
    tr(f"{J}.{sid}.hint", hpt, hen)
    tr(f"{J}.{sid}.lore", LORE[sid][0], LORE[sid][1])
    tr(f"{J}.{sid}.tip", LORE[sid][2], LORE[sid][3])


# =================================================================================================
#  FEATURES
# =================================================================================================
def vanilla_biome(name):
    jars = glob.glob(os.path.join(ROOT, "build/moddev/artifacts/*client-extra*minecraft-resources.jar"))
    if not jars:
        sys.exit("jar de recursos do Minecraft não encontrado em build/moddev/artifacts")
    with zipfile.ZipFile(jars[0]) as z:
        return json.loads(z.read(f"data/minecraft/worldgen/biome/{name}.json"))


def configured(name, ftype, config):
    write_json(os.path.join(D, "worldgen/configured_feature", P + name + ".json"), {"type": ftype, "config": config})
    return f"{NS}:{P}{name}"


def placed(name, feature, placement):
    write_json(os.path.join(D, "worldgen/placed_feature", P + name + ".json"), {"feature": feature, "placement": placement})
    return f"{NS}:{P}{name}"


SQ = {"type": "minecraft:in_square"}
BIO = {"type": "minecraft:biome"}
DRY = {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}
AIR = {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}
CAVE_RANGE = {"type": "minecraft:height_range",
              "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 0}, "max_inclusive": {"absolute": 256}}}


def hm(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def count(n):
    return {"type": "minecraft:count", "count": n}


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


def rarity(n):
    return {"type": "minecraft:rarity_filter", "chance": n}


def every_layer(n):
    return {"type": "minecraft:count_on_every_layer", "count": n}


def survive(sapling):
    return {"type": "minecraft:block_predicate_filter",
            "predicate": {"type": "minecraft:would_survive", "state": {"Name": sapling, "Properties": {"stage": "0"}}}}


def on_blocks(blocks):
    """Ar em cima de um dos blocos listados."""
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
        AIR, {"type": "minecraft:matching_blocks", "blocks": blocks, "offset": [0, -1, 0]}]}}


def scan(direction, steps=12):
    """Do ponto sorteado até o chão ("down") ou teto ("up") e um bloco para dentro do ar."""
    return [{"type": "minecraft:environment_scan", "direction_of_search": direction, "max_steps": steps,
             "target_condition": {"type": "minecraft:solid"}, "allowed_search_condition": AIR},
            {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": 1 if direction == "down" else -1}]


def simple(state):
    return {"type": "minecraft:simple_state_provider", "state": state}


def weighted(entries):
    return {"type": "minecraft:weighted_state_provider", "entries": [{"data": s, "weight": w} for s, w in entries]}


def cluster(block, facing="up"):
    return {"Name": block, "Properties": {"facing": facing, "waterlogged": "false"}}


def simple_block(state):
    return {"type": "minecraft:simple_block", "config": {"to_place": simple(state)}}


def patch(inner_feature, inner_placement, tries, xz, y):
    return {"feature": {"feature": inner_feature, "placement": inner_placement}, "tries": tries, "xz_spread": xz, "y_spread": y}


def veg_patch(surface, replaceable, ground, vegetation_state, chance, r_lo, r_hi, depth=1):
    return {"surface": surface, "replaceable": replaceable, "ground_state": ground, "depth": depth,
            "extra_bottom_block_chance": 0.0, "extra_edge_column_chance": 0.3, "vertical_range": 5,
            "vegetation_chance": chance,
            "vegetation_feature": {"feature": simple_block(vegetation_state), "placement": []},
            "xz_radius": uniform(r_lo, r_hi)}


def column(layers, direction="up"):
    return {"direction": direction, "allowed_placement": AIR, "prioritize_tip": True,
            "layers": [{"height": h, "provider": p} for h, p in layers]}


def tree(trunk, foliage, trunk_placer, foliage_placer, decorators=(), ignore_vines=True):
    return {"decorators": list(decorators), "dirt_provider": simple({"Name": "minecraft:dirt"}),
            "foliage_placer": foliage_placer, "foliage_provider": simple(foliage), "force_dirt": False,
            "ignore_vines": ignore_vines,
            "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
            "trunk_placer": trunk_placer, "trunk_provider": simple(trunk)}


LEAF_STATE = {"Name": LEAVES, "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}
SOIL = ["minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:moss_block",
        "minecraft:stone", CRYSTAL_BLOCK]

# Ordem GLOBAL das features do módulo dentro de um passo (a mesma em todos os biomas).
ORDER = []


def gen_features():
    f = {}

    def reg(name, feature, placement):
        f[name] = placed(name, feature, placement)
        ORDER.append(f[name])

    # ---------------- Floresta de Cristal ----------------
    birch = {"Name": "minecraft:birch_log", "Properties": {"axis": "y"}}
    blob = {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2}
    small = configured("crystal_tree", "minecraft:tree", tree(
        birch, LEAF_STATE, {"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 0}, blob))
    tall = configured("crystal_tree_tall", "minecraft:tree", tree(
        birch, LEAF_STATE, {"type": "minecraft:straight_trunk_placer", "base_height": 8, "height_rand_a": 3, "height_rand_b": 0},
        {"type": "minecraft:blob_foliage_placer", "height": 4, "offset": 0, "radius": 2}))
    sap = [survive("minecraft:birch_sapling")]
    trees = configured("crystal_trees", "minecraft:random_selector", {
        "default": {"feature": small, "placement": sap},
        "features": [{"chance": 0.3, "feature": {"feature": tall, "placement": sap}}]})
    reg("crystal_trees", trees, [count(6), SQ, DRY, hm("OCEAN_FLOOR"), BIO])
    # Pedras de cristal que brilham (como as pedras musgosas da taiga).
    rock = configured("crystal_rock", "minecraft:forest_rock", {"state": {"Name": CRYSTAL_BLOCK}})
    reg("crystal_rock", rock, [rarity(3), SQ, hm("MOTION_BLOCKING_NO_LEAVES"), BIO])
    # Aglomerados no chão.
    surf = configured("luminous_patch", "minecraft:random_patch",
                      patch(simple_block(cluster(LUMINOUS)), [on_blocks(SOIL)], 8, 5, 2))
    reg("crystal_forest_clusters", surf, [count(1), SQ, hm("MOTION_BLOCKING_NO_LEAVES"), BIO])

    # ---------------- Pântano Assombrado ----------------
    dead = configured("dead_tree", "minecraft:tree", tree(
        {"Name": "minecraft:dark_oak_log", "Properties": {"axis": "y"}}, {"Name": "minecraft:air"},
        {"type": "minecraft:forking_trunk_placer", "base_height": 4, "height_rand_a": 2, "height_rand_b": 2},
        {"type": "minecraft:acacia_foliage_placer", "offset": 0, "radius": 1},
        decorators=[{"type": "minecraft:trunk_vine"}], ignore_vines=False))
    reg("haunted_dead_trees", dead, [count(2), SQ, {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 1},
                                      hm("OCEAN_FLOOR"), BIO, survive("minecraft:oak_sapling")])
    # Postes com lanterna de almas: a luz azul no meio da névoa.
    post = configured("wisp_post", "minecraft:block_column", column([
        (uniform(2, 3), simple({"Name": "minecraft:dark_oak_fence"})),
        (1, simple({"Name": "minecraft:soul_lantern", "Properties": {"hanging": "false", "waterlogged": "false"}}))]))
    reg("haunted_wisp_posts", post, [rarity(5), SQ, hm("MOTION_BLOCKING_NO_LEAVES"),
                                      on_blocks(["minecraft:grass_block", "minecraft:dirt", "minecraft:mud"]), BIO])
    web = configured("cobweb_patch", "minecraft:random_patch",
                     patch(simple_block({"Name": "minecraft:cobweb"}),
                           [on_blocks(["minecraft:grass_block", "minecraft:dirt", "minecraft:mud"])], 5, 4, 1))
    reg("haunted_cobwebs", web, [rarity(4), SQ, hm("MOTION_BLOCKING_NO_LEAVES"), BIO])

    # ---------------- Bosque Crepuscular ----------------
    violet = configured("astral_patch", "minecraft:random_patch",
                        patch(simple_block(cluster(ASTRAL)), [on_blocks(SOIL)], 8, 5, 2))
    reg("twilight_clusters", violet, [count(1), SQ, hm("MOTION_BLOCKING_NO_LEAVES"), BIO])

    # ---------------- Caverna de Cristal Mágico ----------------
    veins = configured("crystal_veins", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 12, "targets": [
        {"state": {"Name": CRYSTAL_BLOCK}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
        {"state": {"Name": CRYSTAL_BLOCK}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]})
    reg("cavern_veins", veins, [count(14), SQ, CAVE_RANGE, BIO])
    calcite = simple({"Name": "minecraft:calcite"})
    floor = configured("cavern_floor", "minecraft:vegetation_patch",
                       veg_patch("floor", "#minecraft:moss_replaceable", calcite, cluster(LUMINOUS), 0.08, 2, 5))
    reg("cavern_floor", floor, [count(70), SQ, CAVE_RANGE] + scan("down") + [BIO])
    ceiling = configured("cavern_ceiling", "minecraft:vegetation_patch",
                         veg_patch("ceiling", "#minecraft:moss_replaceable", calcite, cluster(LUMINOUS, "down"), 0.06, 2, 4))
    reg("cavern_ceiling", ceiling, [count(60), SQ, CAVE_RANGE] + scan("up") + [BIO])
    pillar = configured("cavern_pillar", "minecraft:block_column", column([
        (uniform(1, 4), simple({"Name": CRYSTAL_BLOCK})), (1, simple(cluster(LUMINOUS)))]))
    reg("cavern_pillars", pillar, [count(10), SQ, CAVE_RANGE] + scan("down") + [BIO])
    # Cristais de mana de verdade, raros (o aglomerado do world_v09).
    mana = configured("cavern_mana", "minecraft:simple_block",
                      {"to_place": simple(cluster(f"{NS}:mana_crystal_cluster"))})
    reg("cavern_mana", mana, [count(3), SQ, CAVE_RANGE] + scan("down") + [BIO])

    # ---------------- Ermo de Cristal Ígneo (Nether) ----------------
    ember_ground = weighted([({"Name": "minecraft:blackstone"}, 5), ({"Name": "minecraft:smooth_basalt"}, 2)])
    efloor = configured("ember_floor", "minecraft:vegetation_patch",
                        veg_patch("floor", "#minecraft:base_stone_nether", ember_ground, cluster(EMBER), 0.07, 3, 6))
    reg("ember_floor", efloor, [every_layer(3), BIO])
    reg("ember_columns", "minecraft:small_basalt_columns", [every_layer(1), BIO])
    nether_range = {"type": "minecraft:height_range", "height": {
        "type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8}, "max_inclusive": {"below_top": 8}}}
    ehang = configured("ember_hanging", "minecraft:simple_block", {"to_place": simple(cluster(EMBER, "down"))})
    reg("ember_ceiling", ehang, [count(40), SQ, nether_range] + scan("up") + [BIO])

    # ---------------- Jardins Astrais (End) ----------------
    astral_ground = weighted([({"Name": "minecraft:amethyst_block"}, 4), ({"Name": "minecraft:calcite"}, 1)])
    afloor = configured("astral_floor", "minecraft:vegetation_patch",
                        veg_patch("floor", f"#{NS}:magic_biomes_end_ground", astral_ground, cluster(ASTRAL), 0.12, 2, 5))
    reg("astral_floor", afloor, [count(3), SQ, hm("MOTION_BLOCKING"), BIO])
    spire = configured("astral_spire", "minecraft:block_column", column([
        (uniform(2, 6), simple({"Name": "minecraft:amethyst_block"})), (1, simple(cluster(ASTRAL)))]))
    reg("astral_spires", spire, [count(2), SQ, hm("MOTION_BLOCKING"),
                                 on_blocks(["minecraft:end_stone", "minecraft:amethyst_block", "minecraft:calcite"]), BIO])
    return f


# =================================================================================================
#  BIOMAS
# =================================================================================================
def spawn(entity, weight, lo, hi):
    return {"type": entity, "weight": weight, "minCount": lo, "maxCount": hi}


def make_biome(base, out_id, step_adds, effects, remove=(), drop_effects=(), temperature=None, downfall=None,
               spawners=None, keep_step9=None):
    b = vanilla_biome(base)
    feats = [list(step) for step in b["features"]]
    while len(feats) < 11:
        feats.append([])
    if keep_step9 is not None:
        feats[9] = [x for x in feats[9] if x in keep_step9]
    for step in range(len(feats)):
        feats[step] = [x for x in feats[step] if x not in remove]
    for step, adds in step_adds.items():
        feats[step] += sorted(adds, key=ORDER.index)
    b["features"] = feats
    if temperature is not None:
        b["temperature"] = temperature
    if downfall is not None:
        b["downfall"] = downfall
    eff = {k: v for k, v in b["effects"].items() if k not in drop_effects}
    eff.update(effects)
    b["effects"] = eff
    if spawners:
        for cat, (mode, entries) in spawners.items():
            if mode == "set":
                b["spawners"][cat] = list(entries)
            else:
                b["spawners"].setdefault(cat, []).extend(entries)
    write_json(os.path.join(D, "worldgen/biome", out_id + ".json"), b)


def particle(kind, probability):
    return {"options": {"type": kind}, "probability": probability}


def gen_biomes(f):
    mood_often = {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 3000}

    make_biome("forest", "crystal_forest",
               {9: [f["crystal_trees"], f["crystal_rock"], f["crystal_forest_clusters"]]},
               {"sky_color": 0x86CFFF, "fog_color": 0xCFEFFF, "water_color": 0x54D8E6, "water_fog_color": 0x0E4A5C,
                "grass_color": 0x86D8C0, "foliage_color": 0x8FE3D8, "particle": particle("minecraft:glow", 0.003)},
               remove=["minecraft:trees_birch_and_oak"], temperature=0.5, downfall=0.7,
               spawners={"ambient": ("add", [spawn(f"{NS}:lumen_sprite", 8, 1, 2)]),
                         "creature": ("add", [spawn(f"{NS}:fairy", 6, 1, 2)]),
                         "monster": ("add", [spawn(f"{NS}:crystal_mole", 30, 1, 2)])})

    make_biome("swamp", "haunted_swamp",
               {9: [f["haunted_dead_trees"], f["haunted_wisp_posts"], f["haunted_cobwebs"]]},
               {"sky_color": 0x6E7F86, "fog_color": 0x55635C, "water_color": 0x3B5246, "water_fog_color": 0x16241C,
                "grass_color": 0x56633F, "foliage_color": 0x4D5A38, "mood_sound": mood_often,
                "particle": particle("minecraft:ash", 0.008)},
               drop_effects=["grass_color_modifier"], temperature=0.6, downfall=0.9,
               spawners={"creature": ("set", [spawn("minecraft:frog", 10, 2, 5)]),
                         "ambient": ("add", [spawn(f"{NS}:wandering_spirit", 8, 1, 1)]),
                         "monster": ("add", [spawn("minecraft:witch", 20, 1, 1), spawn("minecraft:bogged", 40, 2, 4),
                                             spawn(f"{NS}:creeping_shade", 40, 1, 2)])})

    make_biome("dark_forest", "twilight_grove",
               {9: [f["twilight_clusters"]]},
               {"sky_color": 0x7566B8, "fog_color": 0x9A8AD0, "water_color": 0x6A5AE0, "water_fog_color": 0x1E1650,
                "grass_color": 0x7B6DB8, "foliage_color": 0x8A5FC4, "particle": particle("minecraft:reverse_portal", 0.006)},
               drop_effects=["grass_color_modifier"], temperature=0.6, downfall=0.8,
               spawners={"creature": ("add", [spawn(f"{NS}:fairy", 8, 1, 3)]),
                         "monster": ("add", [spawn("minecraft:enderman", 30, 1, 2), spawn("minecraft:witch", 10, 1, 1),
                                             spawn(f"{NS}:creeping_shade", 25, 1, 1)])})

    make_biome("lush_caves", "crystal_cavern",
               {6: [f["cavern_veins"]],
                9: [f["cavern_floor"], f["cavern_ceiling"], f["cavern_pillars"], f["cavern_mana"]]},
               {"particle": particle("minecraft:glow", 0.004),
                "music": {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False,
                          "sound": "minecraft:music.overworld.dripstone_caves"}},
               remove=["minecraft:ore_clay"], keep_step9=["minecraft:glow_lichen"],
               spawners={"axolotls": ("set", []), "water_ambient": ("set", []),
                         "ambient": ("add", [spawn(f"{NS}:lumen_sprite", 8, 1, 2)]),
                         "monster": ("add", [spawn(f"{NS}:crystal_mole", 60, 1, 3)])})

    make_biome("nether_wastes", "ember_crystal_wastes",
               {7: [f["ember_floor"], f["ember_columns"], f["ember_ceiling"]]},
               {"fog_color": 0x5C2206, "particle": particle("minecraft:small_flame", 0.012)},
               spawners={"monster": ("set", [spawn("minecraft:zombified_piglin", 40, 2, 4), spawn("minecraft:magma_cube", 30, 2, 4),
                                             spawn("minecraft:blaze", 6, 1, 2), spawn("minecraft:enderman", 1, 1, 2),
                                             spawn(f"{NS}:salamander", 30, 1, 2), spawn(f"{NS}:ember_imp", 30, 2, 3)])})

    make_biome("end_highlands", "astral_gardens",
               {9: [f["astral_floor"], f["astral_spires"]]},
               {"fog_color": 0x9070D0, "particle": particle("minecraft:end_rod", 0.002)},
               spawners={"monster": ("add", [spawn(f"{NS}:void_bat", 3, 1, 2)])})


# =================================================================================================
#  TAGS
# =================================================================================================
def gen_tags():
    tag(os.path.join(D, "tags/worldgen/biome/magic_biomes.json"), ALL)
    tag(os.path.join(D, "tags/block/magic_biomes_end_ground.json"), ["minecraft:end_stone"])

    def mc(path, values):
        tag(os.path.join(MERGE, "data/minecraft/tags/worldgen/biome", path + ".json"), values)

    def c(path, values):
        tag(os.path.join(MERGE, "data/c/tags/worldgen/biome", path + ".json"), values)

    def structure(name, values):
        tag(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure", name + ".json"), values)

    forests = [CRYSTAL_FOREST, TWILIGHT_GROVE]
    mc("is_overworld", OVERWORLD)
    mc("is_forest", forests)
    mc("is_nether", [EMBER_WASTES])
    mc("is_end", [ASTRAL_GARDENS])
    mc("allows_surface_slime_spawns", [HAUNTED_SWAMP])
    mc("has_closer_water_fog", [HAUNTED_SWAMP])
    mc("water_on_map_outlines", [HAUNTED_SWAMP])
    mc("stronghold_biased_to", forests)
    mc("has_structure/mineshaft", OVERWORLD)
    mc("has_structure/ruined_portal_standard", forests)
    mc("has_structure/ruined_portal_swamp", [HAUNTED_SWAMP])
    mc("has_structure/swamp_hut", [HAUNTED_SWAMP])
    mc("has_structure/woodland_mansion", [TWILIGHT_GROVE])
    mc("has_structure/bastion_remnant", [EMBER_WASTES])
    mc("has_structure/end_city", [ASTRAL_GARDENS])
    c("is_overworld", OVERWORLD)
    c("is_forest", forests)
    c("is_swamp", [HAUNTED_SWAMP])
    c("is_spooky", [HAUNTED_SWAMP, TWILIGHT_GROVE])
    c("is_magical", ALL)
    c("is_cave", [CRYSTAL_CAVERN])
    c("is_underground", [CRYSTAL_CAVERN])
    c("is_nether", [EMBER_WASTES])
    c("is_end", [ASTRAL_GARDENS])
    c("is_outer_end_island", [ASTRAL_GARDENS])
    # Estruturas do mod que combinam com cada bioma (as que usam #is_forest/#is_overworld já entram sozinhas).
    structure("fairy_lake", [CRYSTAL_FOREST])
    structure("fey_ring", forests)
    structure("wandering_mage_camp", [CRYSTAL_FOREST])
    structure("druid_circle", [TWILIGHT_GROVE])
    structure("profane_altar", [HAUNTED_SWAMP, TWILIGHT_GROVE])
    structure("witch_hut_ancient", [HAUNTED_SWAMP])
    structure("shrine_shadow", [HAUNTED_SWAMP])
    structure("forgotten_crypt", [HAUNTED_SWAMP])
    structure("end_library", [ASTRAL_GARDENS])

    clusters = [LUMINOUS, EMBER, ASTRAL]
    tag(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), clusters + [CRYSTAL_BLOCK])
    tag(os.path.join(MERGE, "data/minecraft/tags/block/mineable/hoe.json"), [LEAVES])
    tag(os.path.join(MERGE, "data/minecraft/tags/block/leaves.json"), [LEAVES])
    tag(os.path.join(MERGE, "data/minecraft/tags/item/leaves.json"), [LEAVES])


# =================================================================================================
#  BLOCOS: modelos, loot, receita
# =================================================================================================
SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def loot(name, pools):
    write_json(os.path.join(D, "loot_table/blocks", name + ".json"),
               {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{name}"})


def item_entry(item, lo=1, hi=1, conditions=None):
    e = {"type": "minecraft:item", "name": item}
    fn = []
    if (lo, hi) != (1, 1):
        fn.append({"function": "minecraft:set_count", "add": False,
                   "count": {"type": "minecraft:uniform", "min": lo, "max": hi} if lo != hi else lo})
    fn.append({"function": "minecraft:explosion_decay"})
    e["functions"] = fn
    if conditions:
        e["conditions"] = conditions
    return e


def cluster_loot(name, common, rare, rare_chance):
    """Toque de Seda leva o aglomerado; sem ele saem os fragmentos (e às vezes o item raro)."""
    not_silk = {"condition": "minecraft:inverted", "term": SILK}
    loot(name, [
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{name}", "conditions": [SILK]},
            item_entry(common, 1, 2)]}]},
        {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_silk, {"condition": "minecraft:random_chance", "chance": rare_chance}],
         "entries": [item_entry(rare)]}])


def gen_blocks():
    for name in ("luminous_crystal_cluster", "ember_crystal_cluster", "astral_crystal_cluster"):
        m = f"{NS}:block/{name}"
        write_json(os.path.join(A, "blockstates", name + ".json"), {"variants": {
            "facing=down": {"model": m, "x": 180}, "facing=east": {"model": m, "x": 90, "y": 90},
            "facing=north": {"model": m, "x": 90}, "facing=south": {"model": m, "x": 90, "y": 180},
            "facing=up": {"model": m}, "facing=west": {"model": m, "x": 90, "y": 270}}})
        write_json(os.path.join(A, "models/block", name + ".json"),
                   {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": m}})
        write_json(os.path.join(A, "models/item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": m}})
    cluster_loot("luminous_crystal_cluster", "minecraft:glowstone_dust", f"{NS}:mana_crystal", 0.2)
    cluster_loot("ember_crystal_cluster", "minecraft:glowstone_dust", "minecraft:blaze_powder", 0.2)
    cluster_loot("astral_crystal_cluster", "minecraft:amethyst_shard", "minecraft:ender_pearl", 0.1)

    name = "luminous_crystal_block"
    write_json(os.path.join(A, "blockstates", name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write_json(os.path.join(A, "models/block", name + ".json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
    write_json(os.path.join(A, "models/item", name + ".json"), {"parent": f"{NS}:block/{name}"})
    loot(name, [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [item_entry(f"{NS}:{name}")]}])
    write_json(os.path.join(D, "recipe", "magic_biomes_luminous_crystal_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
        "key": {"#": {"item": LUMINOUS}}, "result": {"id": CRYSTAL_BLOCK, "count": 1}})

    name = "luminous_leaves"
    write_json(os.path.join(A, "blockstates", name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write_json(os.path.join(A, "models/block", name + ".json"),
               {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                "textures": {"all": f"{NS}:block/{name}"}})
    write_json(os.path.join(A, "models/item", name + ".json"), {"parent": f"{NS}:block/{name}"})
    shears = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}
    loot(name, [
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{name}",
             "conditions": [{"condition": "minecraft:any_of", "terms": [shears, SILK]}]},
            {"type": "minecraft:item", "name": "minecraft:glowstone_dust",
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.04}]}]}]}])


# =================================================================================================
#  ARTE
# =================================================================================================
CLUSTER_ROWS = [
    "................",
    ".......w........",
    "......lwm.......",
    "......lmm...w...",
    "..w...lmd..lw...",
    "..lw..lmd..lmm..",
    ".llm..lmd..lmd..",
    ".lmm.llmd.llmd..",
    ".lmd.lmmd.lmmd..",
    ".lmd.lmmdllmdd..",
    ".lmdllmmdlmmd...",
    "..mdlmmmdlmdd.l.",
    "..ddlmmddmdd.lm.",
    ".l.dmmmdddd.lmd.",
    "lmdddmddddddmdd.",
    ".ddd.dddd.dddd..",
]


def tex_cluster(name, base, seed):
    c = Canvas(16, 16)
    b = hex_rgba(base)
    c.from_rows(CLUSTER_ROWS, {"w": lighten(b, 0.85), "l": lighten(b, 0.45), "m": b, "d": darken(b, 0.35)})
    c.noise(0.05, seed)
    c.save(os.path.join(A, "textures/block", name + ".png"))


def tex_crystal_block():
    """Facetas: cada pixel pega o tom da semente mais próxima (com volta nas bordas, para emendar)."""
    rnd = random.Random(77)
    base = hex_rgba("#5fd8ea")
    seeds = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(-0.35, 0.45)) for _ in range(9)]

    def nearest(x, y):
        best, shade, idx = 1e9, 0.0, -1
        for i, (sx, sy, s) in enumerate(seeds):
            dx = min(abs(x - sx), 16 - abs(x - sx))
            dy = min(abs(y - sy), 16 - abs(y - sy))
            d = dx * dx + dy * dy
            if d < best:
                best, shade, idx = d, s, i
        return shade, idx

    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            s, i = nearest(x + 0.5, y + 0.5)
            col = lighten(base, s) if s > 0 else darken(base, -s)
            # aresta clara onde a faceta muda (brilho de cristal)
            if nearest((x + 1) % 16 + 0.5, y + 0.5)[1] != i or nearest(x + 0.5, (y + 1) % 16 + 0.5)[1] != i:
                col = lighten(col, 0.45)
            c.set(x, y, col)
    c.noise(0.04, 5)
    c.save(os.path.join(A, "textures/block", "luminous_crystal_block.png"))


def tex_leaves():
    rnd = random.Random(31)
    tones = [hex_rgba("#bff4f2"), hex_rgba("#8fe3e0"), hex_rgba("#62c4cf"), hex_rgba("#e9ffff")]
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            if r < 0.24:
                continue  # furo (folha recortada)
            c.set(x, y, tones[0] if r < 0.5 else tones[1] if r < 0.78 else tones[2] if r < 0.95 else tones[3])
    c.save(os.path.join(A, "textures/block", "luminous_leaves.png"))


def gen_art():
    tex_cluster("luminous_crystal_cluster", "#4fd2ea", 1)
    tex_cluster("ember_crystal_cluster", "#ff8a2b", 2)
    tex_cluster("astral_crystal_cluster", "#a66cf0", 3)
    tex_crystal_block()
    tex_leaves()


# =================================================================================================
#  DIÁRIO
# =================================================================================================
def gen_journal():
    dims = {"ember_crystal_wastes": "minecraft:the_nether", "astral_gardens": "minecraft:the_end"}
    icons = {"crystal_forest": LEAVES, "haunted_swamp": "minecraft:soul_lantern", "twilight_grove": ASTRAL,
             "crystal_cavern": LUMINOUS, "ember_crystal_wastes": EMBER, "astral_gardens": "minecraft:amethyst_block"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 66) para o começo do Ato II. Para não virar uma parede
    # (os biomas são raros e só nascem em terreno novo), o único passo obrigatório é achar UMA Terra Encantada
    # ("explore" — os textos dele ficam em tools/gen/journal_story.py); visitar cada bioma virou bônus.
    over = [sid for sid, *_ in STEPS if sid not in dims]
    steps = [{"id": "explore", "condition": {"type": "flag_prefix", "prefix": "magic_biomes.visit.", "min": 1}, "xp": 150,
              "icon": LUMINOUS,
              "locate": {"dimension": "minecraft:overworld", "any": [
                  {"biome": f"{NS}:{sid}", "unless": {"type": "flag", "flag": f"magic_biomes.visit.{sid}"}} for sid in over]}}]
    for sid, *_ in STEPS:
        step = {"id": sid, "condition": {"type": "flag", "flag": f"magic_biomes.visit.{sid}"}, "optional": True, "xp": 80,
                "icon": icons[sid],
                "locate": {"biome": f"{NS}:{sid}", "dimension": dims.get(sid, "minecraft:overworld")}}
        steps.append(step)
    write_json(os.path.join(D, "journal", "magic_biomes.json"), {"chapters": [{
        "id": "magic_biomes", "order": 21, "color": "#5FD8EA", "intro": True,
        "icon": [LUMINOUS, LEAVES, ASTRAL], "steps": steps,
        "rewards": {"xp": 500, "items": [{"id": CRYSTAL_BLOCK, "count": 8}]}}]})


def main():
    f = gen_features()
    gen_biomes(f)
    gen_tags()
    gen_blocks()
    gen_art()
    gen_journal()
    write_json(os.path.join(LANG, "pt_br.json"), PT)
    write_json(os.path.join(LANG, "en_us.json"), EN)
    print("magic_biomes: ok")


if __name__ == "__main__":
    main()
