"""
Gera os assets e dados do modulo ritual_mage (Pesquisa Arcana do Mago).
Uso (na raiz do projeto):  py -I tools/gen/ritual_mage.py

Saidas:
  data/isekai_mahou/mage_phenomena/*.json         fenomenos observaveis com a Lente Arcana
  data/isekai_mahou/recipe/*.json, loot_table      receitas e drop da Mesa de Pesquisa
  assets/isekai_mahou/{textures,models,blockstates}  itens, bloco e telas
  src/main/lang/ritual_mage/{pt_br,en_us}.json     traducoes (UI + fenomenos + teses)
  src/main/merge/ritual_mage/.../mineable/axe.json
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "ritual_mage")
MERGE = os.path.join(ROOT, "src", "main", "merge", "ritual_mage")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# =====================================================================================================
#  FENOMENOS
#  (id, categoria, elementos, raridade, icones, condicoes, nome_pt, nome_en, desc_pt, desc_en)
# =====================================================================================================
P = []


def ph(pid, cat, elements, rarity, icons, cond, name_pt, name_en, desc_pt, desc_en):
    P.append(dict(id=pid, cat=cat, elements=elements, rarity=rarity, icons=icons, cond=cond,
                  name_pt=name_pt, name_en=name_en, desc_pt=desc_pt, desc_en=desc_en))


def egg(mob, fallback):
    return ["isekai_mahou:" + mob + "_spawn_egg", fallback]


# ---------------------------------------------------------------- Blocos
B = "block"
ph("lava", B, ["fire"], "common", ["minecraft:lava_bucket"], {"blocks": ["minecraft:lava"]},
   "Lava Viva", "Living Lava",
   "Rocha derretida que pulsa com o calor do fundo do mundo. Observe um lago de lava.",
   "Molten rock pulsing with the heat of the world's depths. Observe a lava pool.")
ph("magma_block", B, ["magma"], "uncommon", ["minecraft:magma_block"], {"blocks": ["minecraft:magma_block"]},
   "Bloco de Magma", "Magma Block",
   "Pedra que guarda brasas por dentro: Fogo e Terra fundidos. Comum no Nether e no fundo do mar.",
   "Stone holding embers within: Fire and Earth fused. Common in the Nether and on the sea floor.")
ph("obsidian", B, ["earth", "fire"], "uncommon", ["minecraft:obsidian"], {"blocks": ["minecraft:obsidian"]},
   "Obsidiana", "Obsidian",
   "Nasce quando a água encontra a lava. Dura como a vontade de um arquimago.",
   "Born when water meets lava. As hard as an archmage's will.")
ph("crying_obsidian", B, ["abyss"], "rare", ["minecraft:crying_obsidian"], {"blocks": ["minecraft:crying_obsidian"]},
   "Obsidiana Chorosa", "Crying Obsidian",
   "Lágrimas violetas escorrem da pedra. Procure em portais em ruínas e bastiões.",
   "Violet tears flow from the stone. Look in ruined portals and bastions.")
ph("ice", B, ["water", "wind"], "common", ["minecraft:packed_ice"], {"blocks": ["minecraft:ice", "minecraft:packed_ice"]},
   "Gelo", "Ice",
   "Água adormecida pelo vento frio. Lagos congelados e biomas nevados.",
   "Water lulled to sleep by the cold wind. Frozen lakes and snowy biomes.")
ph("blue_ice", B, ["ice"], "rare", ["minecraft:blue_ice"], {"blocks": ["minecraft:blue_ice"]},
   "Gelo Azul", "Blue Ice",
   "Gelo tão antigo que virou elemento próprio. Icebergs e picos gelados.",
   "Ice so ancient it became an element of its own. Icebergs and frozen peaks.")
ph("amethyst", B, ["crystal"], "uncommon", ["minecraft:amethyst_shard"],
   {"blocks": ["minecraft:budding_amethyst", "minecraft:amethyst_block", "minecraft:amethyst_cluster",
               "minecraft:large_amethyst_bud", "minecraft:medium_amethyst_bud", "minecraft:small_amethyst_bud"]},
   "Geodo de Ametista", "Amethyst Geode",
   "Cristais que cantam com a luz presa na terra. Geodos no subterrâneo.",
   "Crystals singing with light trapped in the earth. Underground geodes.")
ph("sculk", B, ["shadow"], "uncommon", ["minecraft:sculk"],
   {"blocks": ["minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst", "minecraft:sculk_sensor",
               "minecraft:calibrated_sculk_sensor", "minecraft:sculk_shrieker"]},
   "Sculk", "Sculk",
   "Uma massa viva que se alimenta de almas e escuta cada passo. Cavernas profundas.",
   "A living mass that feeds on souls and hears every step. Deep caves.")
ph("ancient_debris", B, ["fire", "earth"], "rare", ["minecraft:ancient_debris"], {"blocks": ["minecraft:ancient_debris"]},
   "Destroços Ancestrais", "Ancient Debris",
   "Metal forjado pelo próprio Nether. Raro, nas profundezas do Nether.",
   "Metal forged by the Nether itself. Rare, deep in the Nether.")
ph("prismarine", B, ["water", "light"], "uncommon", ["minecraft:prismarine_shard"],
   {"blocks": ["minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:sea_lantern"]},
   "Prismarinho", "Prismarine",
   "Pedra marinha que muda de cor com as marés. Monumentos oceânicos.",
   "Sea stone that shifts color with the tides. Ocean monuments.")
ph("moss", B, ["nature"], "common", ["minecraft:moss_block"],
   {"blocks": ["minecraft:moss_block", "minecraft:moss_carpet", "minecraft:azalea", "minecraft:flowering_azalea"]},
   "Musgo", "Moss",
   "Vida paciente que cobre a pedra. Cavernas verdejantes e florestas úmidas.",
   "Patient life covering stone. Lush caves and damp forests.")
ph("powder_snow", B, ["wind", "water"], "uncommon", ["minecraft:powder_snow_bucket"], {"blocks": ["minecraft:powder_snow"]},
   "Neve em Pó", "Powder Snow",
   "Neve tão leve que engole quem pisa nela. Encostas nevadas.",
   "Snow so light it swallows whoever steps on it. Snowy slopes.")
ph("glowstone", B, ["light"], "common", ["minecraft:glowstone_dust"], {"blocks": ["minecraft:glowstone"]},
   "Pedra Luminosa", "Glowstone",
   "Luz cristalizada pendurada no teto do Nether.",
   "Crystallized light hanging from the Nether's ceiling.")
ph("soul_fire", B, ["hellfire"], "uncommon", ["minecraft:soul_lantern"],
   {"blocks": ["minecraft:soul_fire", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:soul_campfire",
               "minecraft:soul_lantern", "minecraft:soul_torch", "minecraft:soul_wall_torch"]},
   "Fogo das Almas", "Soul Fire",
   "Chamas azuis alimentadas por almas presas na areia. Vale das Almas.",
   "Blue flames fed by souls trapped in sand. Soul Sand Valley.")
ph("dripstone", B, ["earth"], "common", ["minecraft:pointed_dripstone"],
   {"blocks": ["minecraft:pointed_dripstone", "minecraft:dripstone_block"]},
   "Espeleotema", "Dripstone",
   "A terra pinga gota a gota, esculpindo lanças de pedra. Cavernas de espeleotemas.",
   "Earth dripping drop by drop, carving stone spears. Dripstone caves.")
ph("enchanting_table", B, ["arcane"], "uncommon", ["minecraft:enchanting_table"], {"blocks": ["minecraft:enchanting_table"]},
   "Mesa de Encantamentos", "Enchanting Table",
   "Os antigos já gravavam mana pura em livros. Estude uma mesa de encantamentos.",
   "The ancients already etched pure mana into books. Study an enchanting table.")
ph("beacon", B, ["light"], "rare", ["minecraft:beacon"], {"blocks": ["minecraft:beacon"]},
   "Sinalizador", "Beacon",
   "Um pilar de luz que toca o céu, movido por uma estrela.",
   "A pillar of light touching the sky, powered by a star.")
ph("end_portal_frame", B, ["void"], "rare", ["minecraft:ender_eye"],
   {"blocks": ["minecraft:end_portal_frame", "minecraft:end_portal"]},
   "Portal do End", "End Portal",
   "Uma moldura que costura este mundo ao Vazio. Escondida nas fortalezas.",
   "A frame stitching this world to the Void. Hidden in strongholds.")
ph("conduit", B, ["abyss"], "epic", ["minecraft:conduit"], {"blocks": ["minecraft:conduit"]},
   "Conduíte", "Conduit",
   "O coração do mar pulsando dentro de uma gaiola de prismarinho.",
   "The heart of the sea beating inside a prismarine cage.")
ph("spawner", B, ["shadow", "arcane"], "rare", ["minecraft:spawner"],
   {"blocks": ["minecraft:spawner", "minecraft:trial_spawner"]},
   "Gerador de Monstros", "Monster Spawner",
   "Uma gaiola que conjura monstros sem parar. Masmorras e câmaras de provação.",
   "A cage that conjures monsters endlessly. Dungeons and trial chambers.")
ph("mana_crystal_block", B, ["arcane"], "uncommon", ["isekai_mahou:mana_crystal_block", "isekai_mahou:mana_crystal"],
   {"blocks": ["isekai_mahou:mana_crystal_block"]},
   "Cristal de Mana", "Mana Crystal",
   "Mana pura solidificada. Usada nos círculos de Ascensão.",
   "Solidified pure mana. Used in Ascension circles.")
ph("void_crystal", B, ["void"], "rare", ["isekai_mahou:void_crystal", "minecraft:amethyst_cluster"],
   {"blocks": ["isekai_mahou:void_crystal"]},
   "Cristal do Vazio", "Void Crystal",
   "Cristal negro que absorve a luz ao redor. Cresce no Abismo.",
   "Black crystal that drinks the light around it. Grows in the Abyss.")
ph("ember_rock", B, ["magma"], "uncommon", ["isekai_mahou:ember_rock", "minecraft:magma_cream"],
   {"blocks": ["isekai_mahou:ember_rock"]},
   "Rocha de Brasa", "Ember Rock",
   "Pedra incandescente das Ilhas de Brasa do Plano Elemental.",
   "Glowing stone from the Ember Isles of the Elemental Plane.")

# ---------------------------------------------------------------- Criaturas
C = "creature"
ph("blaze", C, ["fire"], "uncommon", ["minecraft:blaze_rod"], {"entities": ["minecraft:blaze"]},
   "Blaze", "Blaze",
   "Um espírito de fogo que gira em volta de bastões ardentes. Fortalezas do Nether.",
   "A fire spirit spinning around burning rods. Nether fortresses.")
ph("enderman", C, ["void"], "uncommon", ["minecraft:ender_pearl"], {"entities": ["minecraft:enderman"]},
   "Enderman", "Enderman",
   "Atravessa o espaço como quem dá um passo. Não o encare por muito tempo.",
   "Crosses space like taking a step. Don't stare at it for long.")
ph("warden", C, ["shadow", "earth"], "epic", ["minecraft:echo_shard"], {"entities": ["minecraft:warden"]},
   "Warden", "Warden",
   "O guardião cego das profundezas. Observe-o de longe e em silêncio.",
   "The blind guardian of the depths. Observe it from afar, in silence.")
ph("slime", C, ["nature"], "common", ["minecraft:slime_ball"], {"entities": ["minecraft:slime"]},
   "Slime", "Slime",
   "Geleia viva que se divide quando ferida. Pântanos e cavernas profundas.",
   "Living jelly that splits when hurt. Swamps and deep caves.")
ph("magma_cube", C, ["magma"], "uncommon", ["minecraft:magma_cream"], {"entities": ["minecraft:magma_cube"]},
   "Cubo de Magma", "Magma Cube",
   "O primo ardente do slime. Deltas de basalto e fortalezas.",
   "The slime's burning cousin. Basalt deltas and fortresses.")
ph("phantom", C, ["shadow", "wind"], "uncommon", ["minecraft:phantom_membrane"], {"entities": ["minecraft:phantom"]},
   "Phantom", "Phantom",
   "Assombra o céu de quem não dorme.",
   "Haunts the sky of those who do not sleep.")
ph("ghast", C, ["fire", "wind"], "uncommon", ["minecraft:ghast_tear"], {"entities": ["minecraft:ghast"]},
   "Ghast", "Ghast",
   "Uma criatura flutuante que chora fogo. Céus do Nether.",
   "A floating creature that weeps fire. Nether skies.")
ph("iron_golem", C, ["earth"], "common", ["minecraft:iron_ingot"], {"entities": ["minecraft:iron_golem"]},
   "Golem de Ferro", "Iron Golem",
   "Metal animado pela vontade de proteger. Vilas.",
   "Metal animated by the will to protect. Villages.")
ph("allay", C, ["astral"], "rare", ["minecraft:allay_spawn_egg"], {"entities": ["minecraft:allay"]},
   "Allay", "Allay",
   "Um espírito azul que ama música. Postos avançados e mansões.",
   "A blue spirit that loves music. Outposts and mansions.")
ph("breeze", C, ["wind"], "rare", ["minecraft:breeze_rod"], {"entities": ["minecraft:breeze"]},
   "Breeze", "Breeze",
   "Vento que ganhou corpo e vontade. Câmaras de provação.",
   "Wind that gained a body and a will. Trial chambers.")
ph("evoker", C, ["arcane"], "rare", ["minecraft:totem_of_undying"], {"entities": ["minecraft:evoker"]},
   "Evocador", "Evoker",
   "Um mago illager que conjura presas e vexes. Mansões e invasões.",
   "An illager mage conjuring fangs and vexes. Mansions and raids.")
ph("wither_skeleton", C, ["shadow", "fire"], "uncommon", ["minecraft:wither_skeleton_skull"],
   {"entities": ["minecraft:wither_skeleton"]},
   "Esqueleto Wither", "Wither Skeleton",
   "Ossos negros que apodrecem tudo o que tocam. Fortalezas do Nether.",
   "Black bones that wither all they touch. Nether fortresses.")
ph("guardian", C, ["water"], "uncommon", ["minecraft:prismarine_crystals"], {"entities": ["minecraft:guardian"]},
   "Guardião", "Guardian",
   "Um peixe de um olho só que dispara feixes. Monumentos oceânicos.",
   "A one-eyed fish that fires beams. Ocean monuments.")
ph("shulker", C, ["gravity"], "rare", ["minecraft:shulker_shell"], {"entities": ["minecraft:shulker"]},
   "Shulker", "Shulker",
   "Seus projéteis anulam o peso do corpo. Cidades do End.",
   "Its shots cancel the body's weight. End cities.")
ph("glow_squid", C, ["water", "light"], "uncommon", ["minecraft:glow_ink_sac"], {"entities": ["minecraft:glow_squid"]},
   "Lula Brilhante", "Glow Squid",
   "Luz viva nadando no escuro das águas subterrâneas.",
   "Living light swimming in the dark of underground waters.")
ph("snow_golem", C, ["ice"], "common", ["minecraft:snowball"], {"entities": ["minecraft:snow_golem"]},
   "Golem de Neve", "Snow Golem",
   "Neve e uma abóbora: o mais simples dos golems.",
   "Snow and a pumpkin: the simplest of golems.")
ph("witch", C, ["shadow", "water"], "common", ["minecraft:glass_bottle"], {"entities": ["minecraft:witch"]},
   "Bruxa", "Witch",
   "Alquimista solitária dos pântanos. Cuidado com as poções.",
   "Lonely alchemist of the swamps. Beware the potions.")
ph("strider", C, ["fire"], "common", ["minecraft:warped_fungus_on_a_stick"], {"entities": ["minecraft:strider"]},
   "Lavagante", "Strider",
   "Caminha sobre a lava como se fosse um lago calmo.",
   "Walks on lava as if it were a calm lake.")
ph("tensura_slime", C, ["water"], "common", ["isekai_mahou:slime_gel", "minecraft:slime_ball"],
   {"entities": ["isekai_mahou:slime"]},
   "Slime Azul", "Blue Slime",
   "Um slime amistoso deste mundo. Dizem que pode até aprender magia.",
   "A friendly slime of this world. Some say it can even learn magic.")
ph("goblin", C, ["earth"], "common", egg("goblin", "minecraft:stone_sword"), {"entities": ["isekai_mahou:goblin"]},
   "Goblin", "Goblin",
   "Pequeno, astuto e sempre em bando. Vilas goblin.",
   "Small, cunning and always in packs. Goblin villages.")
ph("salamander", C, ["fire"], "uncommon", egg("salamander", "minecraft:fire_charge"),
   {"entities": ["isekai_mahou:salamander"]},
   "Salamandra", "Salamander",
   "O espírito menor do Fogo, nascido nas brasas.",
   "The lesser spirit of Fire, born in embers.")
ph("undine", C, ["water"], "uncommon", egg("undine", "minecraft:heart_of_the_sea"),
   {"entities": ["isekai_mahou:undine"]},
   "Ondina", "Undine",
   "O espírito menor da Água, que dança nas ondas.",
   "The lesser spirit of Water, dancing in the waves.")
ph("sylph", C, ["wind"], "uncommon", egg("sylph", "minecraft:feather"),
   {"entities": ["isekai_mahou:sylph"]},
   "Sílfide", "Sylph",
   "O espírito menor do Vento, rápido demais para os olhos.",
   "The lesser spirit of Wind, too fast for the eye.")
ph("fairy", C, ["light", "nature"], "uncommon", egg("fairy", "minecraft:glow_berries"),
   {"entities": ["isekai_mahou:fairy"]},
   "Fada", "Fairy",
   "Brilha entre as flores perto de lagos encantados.",
   "Glows among flowers near enchanted lakes.")
ph("wandering_spirit", C, ["arcane"], "uncommon", egg("wandering_spirit", "isekai_mahou:essence_arcane"),
   {"entities": ["isekai_mahou:wandering_spirit"]},
   "Espírito Errante", "Wandering Spirit",
   "Mana sem dono vagando pela noite.",
   "Ownerless mana wandering through the night.")
ph("storm_hawk", C, ["lightning"], "rare", egg("storm_hawk", "minecraft:lightning_rod"),
   {"entities": ["isekai_mahou:storm_hawk"]},
   "Falcão da Tempestade", "Storm Hawk",
   "Ave que mergulha entre os raios.",
   "A bird that dives between lightning bolts.")

# ---------------------------------------------------------------- Biomas
BI = "biome"
ph("desert", BI, ["sand"], "common", ["minecraft:dead_bush"], {"biomes": ["minecraft:desert", "#minecraft:is_badlands"]},
   "Deserto", "Desert",
   "Terra que o vento seca grão por grão. Desertos e mesetas.",
   "Earth dried grain by grain by the wind. Deserts and badlands.")
ph("peaks", BI, ["wind", "earth"], "uncommon", ["minecraft:goat_horn"],
   {"biomes": ["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks"]},
   "Picos", "Peaks",
   "Onde a terra toca o vento. Suba aos picos mais altos.",
   "Where earth touches the wind. Climb the highest peaks.")
ph("deep_dark", BI, ["shadow"], "rare", ["minecraft:sculk_shrieker"], {"biomes": ["minecraft:deep_dark"]},
   "Escuridão Profunda", "Deep Dark",
   "Um silêncio tão denso que se pode tocar. Muito abaixo da superfície.",
   "A silence so dense you can touch it. Far below the surface.")
ph("mushroom_fields", BI, ["nature"], "rare", ["minecraft:red_mushroom"], {"biomes": ["minecraft:mushroom_fields"]},
   "Campos de Cogumelos", "Mushroom Fields",
   "Uma ilha onde nenhum monstro nasce. Procure no meio do oceano.",
   "An island where no monster spawns. Search in the middle of the ocean.")
ph("deep_ocean", BI, ["abyss"], "uncommon", ["minecraft:nautilus_shell"], {"biomes": ["#minecraft:is_deep_ocean"]},
   "Oceano Profundo", "Deep Ocean",
   "Água tão funda que a luz desiste no caminho.",
   "Water so deep that light gives up on the way.")
ph("jungle", BI, ["nature"], "common", ["minecraft:cocoa_beans"], {"biomes": ["#minecraft:is_jungle"]},
   "Selva", "Jungle",
   "A natureza em seu estado mais exuberante.",
   "Nature at its most lavish.")
ph("swamp", BI, ["water", "earth"], "common", ["minecraft:lily_pad"],
   {"biomes": ["minecraft:swamp", "minecraft:mangrove_swamp"]},
   "Pântano", "Swamp",
   "Água parada e terra mole, onde as bruxas fazem morada.",
   "Still water and soft earth, where witches make their home.")
ph("cherry_grove", BI, ["light", "nature"], "uncommon", ["minecraft:pink_petals"], {"biomes": ["minecraft:cherry_grove"]},
   "Bosque de Cerejeiras", "Cherry Grove",
   "Pétalas cor-de-rosa dançando na luz das montanhas.",
   "Pink petals dancing in the mountain light.")
ph("ice_spikes", BI, ["ice"], "rare", ["minecraft:packed_ice"], {"biomes": ["minecraft:ice_spikes"]},
   "Espinhos de Gelo", "Ice Spikes",
   "Torres de gelo que rasgam a planície nevada.",
   "Towers of ice tearing through the snowy plain.")
ph("lush_caves", BI, ["nature", "light"], "uncommon", ["minecraft:glow_berries"], {"biomes": ["minecraft:lush_caves"]},
   "Cavernas Verdejantes", "Lush Caves",
   "Um jardim escondido sob as raízes das azaleias.",
   "A garden hidden beneath azalea roots.")
ph("basalt_deltas", BI, ["magma"], "uncommon", ["minecraft:basalt"], {"biomes": ["minecraft:basalt_deltas"]},
   "Deltas de Basalto", "Basalt Deltas",
   "Colunas de basalto e lava a céu aberto no Nether.",
   "Basalt columns and open lava in the Nether.")
ph("soul_sand_valley", BI, ["hellfire"], "uncommon", ["minecraft:soul_sand"], {"biomes": ["minecraft:soul_sand_valley"]},
   "Vale das Almas", "Soul Sand Valley",
   "Um vale de areia que geme. Fogo azul e ossos gigantes.",
   "A valley of moaning sand. Blue fire and giant bones.")
ph("crimson_forest", BI, ["fire", "nature"], "uncommon", ["minecraft:crimson_fungus"], {"biomes": ["minecraft:crimson_forest"]},
   "Floresta Carmesim", "Crimson Forest",
   "Fungos vermelhos que crescem no calor do Nether.",
   "Red fungi growing in the Nether's heat.")
ph("warped_forest", BI, ["void"], "uncommon", ["minecraft:warped_fungus"], {"biomes": ["minecraft:warped_forest"]},
   "Floresta Distorcida", "Warped Forest",
   "Uma floresta azul onde os endermen se sentem em casa.",
   "A blue forest where endermen feel at home.")

# ---------------------------------------------------------------- Ceu
S = "sky"
ph("rain", S, ["water"], "common", ["minecraft:water_bucket"], {"weather": "rain", "sky": True},
   "Chuva", "Rain",
   "Olhe para o céu aberto enquanto chove.",
   "Look at the open sky while it rains.")
ph("thunderstorm", S, ["lightning"], "rare", ["minecraft:lightning_rod"], {"weather": "thunder", "sky": True},
   "Tempestade com Raios", "Thunderstorm",
   "Fogo e Vento rasgando as nuvens. Olhe para o céu durante uma tempestade.",
   "Fire and Wind tearing the clouds. Look at the sky during a thunderstorm.")
ph("snowfall", S, ["ice"], "uncommon", ["minecraft:snowball"], {"weather": "snow", "sky": True},
   "Nevasca", "Snowfall",
   "Olhe para o céu enquanto neva num bioma frio.",
   "Look at the sky while it snows in a cold biome.")
ph("full_moon", S, ["light"], "rare", ["minecraft:clock"], {"moon": [0], "sky": True, "clear": True},
   "Lua Cheia", "Full Moon",
   "A lua inteira derrama luz prateada. Olhe para ela numa noite limpa.",
   "The whole moon pours silver light. Look at it on a clear night.")
ph("new_moon", S, ["shadow"], "rare", ["minecraft:ink_sac"], {"moon": [4], "sky": True, "clear": True},
   "Lua Nova", "New Moon",
   "A noite sem lua, quando a Sombra está mais forte. Olhe para o céu.",
   "The moonless night, when Shadow is strongest. Look at the sky.")
ph("sunset", S, ["twilight"], "uncommon", ["minecraft:orange_dye"], {"time": [12000, 13400], "sky": True, "clear": True},
   "Crepúsculo", "Dusk",
   "O instante em que a Luz e a Sombra se tocam. Olhe para o céu ao pôr do sol.",
   "The moment Light and Shadow touch. Look at the sky at sunset.")
ph("zenith_sun", S, ["sun"], "uncommon", ["minecraft:sunflower"], {"time": [5000, 7000], "sky": True, "clear": True},
   "Sol a Pino", "Zenith Sun",
   "O sol no ponto mais alto do céu. Olhe para cima ao meio-dia.",
   "The sun at the top of the sky. Look up at noon.")
ph("starry_night", S, ["astral"], "uncommon", ["minecraft:firework_star"], {"time": [13400, 22600], "sky": True, "clear": True},
   "Noite Estrelada", "Starry Night",
   "Constelações que os antigos usavam como mapa de mana. Olhe para o céu à noite.",
   "Constellations the ancients used as a mana map. Look at the night sky.")

# ---------------------------------------------------------------- Dimensoes
D = "dimension"
ph("nether", D, ["hellfire", "fire"], "uncommon", ["minecraft:netherrack"], {"dimensions": ["minecraft:the_nether"]},
   "O Nether", "The Nether",
   "Um mundo de fogo e cinzas. Use a lente em qualquer lugar do Nether.",
   "A world of fire and ash. Use the lens anywhere in the Nether.")
ph("the_end", D, ["void", "astral"], "rare", ["minecraft:end_stone"], {"dimensions": ["minecraft:the_end"]},
   "O End", "The End",
   "Ilhas flutuando no Vazio sob um céu sem estrelas.",
   "Islands floating in the Void under a starless sky.")
ph("elemental_plane", D, ["fire", "water", "earth", "wind"], "epic",
   ["isekai_mahou:elemental_portal_frame", "isekai_mahou:essence_fire"],
   {"dimensions": ["isekai_mahou:elemental_plane"]},
   "Plano Elemental", "Elemental Plane",
   "Onde os quatro elementos existem em estado puro.",
   "Where the four elements exist in their pure state.")
ph("abyss", D, ["abyss", "shadow"], "epic", ["isekai_mahou:abyssal_stone", "isekai_mahou:essence_shadow"],
   {"dimensions": ["isekai_mahou:abyss"]},
   "O Abismo", "The Abyss",
   "As profundezas onde a Sombra nasce.",
   "The depths where Shadow is born.")
ph("celestial_sanctuary", D, ["light", "astral"], "epic", ["isekai_mahou:celestial_marble", "isekai_mahou:essence_light"],
   {"dimensions": ["isekai_mahou:celestial_sanctuary"]},
   "Santuário Celestial", "Celestial Sanctuary",
   "Uma cidade de cristal acima das nuvens.",
   "A crystal city above the clouds.")
ph("twilight_rift", D, ["twilight"], "legendary", ["isekai_mahou:twilight_rift", "minecraft:end_crystal"],
   {"dimensions": ["isekai_mahou:twilight_rift"]},
   "Fenda do Crepúsculo", "Twilight Rift",
   "O lugar entre a Luz e a Sombra, onde tudo termina.",
   "The place between Light and Shadow, where everything ends.")

# ---------------------------------------------------------------- Estruturas
ST = "structure"
ph("village", ST, ["earth", "light"], "common", ["minecraft:bell"], {"structures": ["#minecraft:village"]},
   "Vila", "Village",
   "A vida simples dos aldeões também tem sua magia. Entre numa vila.",
   "Villagers' simple life has its own magic. Enter a village.")
ph("fortress", ST, ["fire", "shadow"], "uncommon", ["minecraft:nether_bricks"], {"structures": ["minecraft:fortress"]},
   "Fortaleza do Nether", "Nether Fortress",
   "Corredores de tijolo negro sobre mares de lava.",
   "Black brick corridors over seas of lava.")
ph("bastion", ST, ["fire", "earth"], "rare", ["minecraft:gilded_blackstone"], {"structures": ["minecraft:bastion_remnant"]},
   "Bastião", "Bastion",
   "As ruínas douradas dos piglins.",
   "The golden ruins of the piglins.")
ph("ancient_city", ST, ["shadow", "void"], "epic", ["minecraft:sculk_catalyst"], {"structures": ["minecraft:ancient_city"]},
   "Cidade Ancestral", "Ancient City",
   "Uma civilização engolida pela Escuridão Profunda. Pise com cuidado.",
   "A civilization swallowed by the Deep Dark. Tread carefully.")
ph("stronghold", ST, ["arcane", "void"], "rare", ["minecraft:mossy_stone_bricks"], {"structures": ["minecraft:stronghold"]},
   "Fortaleza", "Stronghold",
   "Os antigos guardaram aqui o caminho para o End.",
   "The ancients kept the way to the End here.")
ph("monument", ST, ["water", "abyss"], "rare", ["minecraft:sponge"], {"structures": ["minecraft:monument"]},
   "Monumento Oceânico", "Ocean Monument",
   "Um templo submerso vigiado por guardiões.",
   "A sunken temple watched by guardians.")
ph("end_city", ST, ["void", "astral"], "epic", ["minecraft:purpur_block"], {"structures": ["minecraft:end_city"]},
   "Cidade do End", "End City",
   "Torres de púrpura nas ilhas distantes do End.",
   "Purpur towers on the End's far islands.")
ph("mansion", ST, ["shadow"], "rare", ["minecraft:dark_oak_log"], {"structures": ["minecraft:mansion"]},
   "Mansão da Floresta", "Woodland Mansion",
   "O covil dos illagers no meio da floresta escura.",
   "The illagers' lair deep in the dark forest.")
ph("trial_chambers", ST, ["wind", "arcane"], "rare", ["minecraft:trial_key"], {"structures": ["minecraft:trial_chambers"]},
   "Câmaras de Provação", "Trial Chambers",
   "Salões subterrâneos feitos para testar aventureiros.",
   "Underground halls made to test adventurers.")
ph("desert_pyramid", ST, ["sand"], "uncommon", ["minecraft:chiseled_sandstone"], {"structures": ["minecraft:desert_pyramid"]},
   "Pirâmide do Deserto", "Desert Pyramid",
   "Um templo enterrado na areia, cheio de armadilhas.",
   "A temple buried in sand, full of traps.")

# ---------------------------------------------------------------- Chefes
BO = "boss"


def boss(pid, ns, mob, elements, rarity, icons, name_pt, name_en, desc_pt, desc_en):
    ph(pid, BO, elements, rarity, icons,
       {"entities": [ns + ":" + mob], "memory": ["boss." + ns + "." + mob, "boss." + mob, "kill." + ns + "." + mob],
        "repeatable": False},
       name_pt, name_en, desc_pt, desc_en)


boss("ender_dragon", "minecraft", "ender_dragon", ["void", "shadow"], "legendary", ["minecraft:dragon_head"],
     "Dragão do End", "Ender Dragon",
     "Observe o dragão em voo ou registre a lembrança depois de derrotá-lo.",
     "Observe the dragon in flight or record the memory after defeating it.")
boss("wither", "minecraft", "wither", ["shadow", "hellfire"], "legendary", ["minecraft:nether_star"],
     "Wither", "Wither",
     "Observe o Wither ou registre a lembrança depois de derrotá-lo.",
     "Observe the Wither or record the memory after defeating it.")
boss("elder_guardian", "minecraft", "elder_guardian", ["abyss", "water"], "epic", ["minecraft:wet_sponge"],
     "Guardião Ancião", "Elder Guardian",
     "O senhor dos monumentos oceânicos. Observe-o ou derrote-o.",
     "Lord of the ocean monuments. Observe it or defeat it.")
boss("goblin_king", "isekai_mahou", "goblin_king", ["earth"], "epic", ["isekai_mahou:goblin_king_spawn_egg", "minecraft:golden_helmet"],
     "Rei Goblin", "Goblin King",
     "O líder das vilas goblin. Observe-o ou derrote-o.",
     "Leader of the goblin villages. Observe it or defeat it.")
boss("ignis", "isekai_mahou", "ignis", ["fire", "magma"], "legendary", ["isekai_mahou:guardian_core_fire"],
     "Ignis", "Ignis",
     "O Guardião do Fogo. Observe-o em combate ou registre a lembrança da vitória.",
     "Guardian of Fire. Observe it in battle or record the memory of victory.")
boss("tide_guardian", "isekai_mahou", "tide_guardian", ["water", "abyss"], "legendary", ["isekai_mahou:guardian_core_water"],
     "Maré", "Tide",
     "O Guardião da Água. Observe-o em combate ou registre a lembrança da vitória.",
     "Guardian of Water. Observe it in battle or record the memory of victory.")
boss("primordial_golem", "isekai_mahou", "primordial_golem", ["earth", "crystal"], "legendary", ["isekai_mahou:guardian_core_earth"],
     "Golem Primordial", "Primordial Golem",
     "O Guardião da Terra. Observe-o em combate ou registre a lembrança da vitória.",
     "Guardian of Earth. Observe it in battle or record the memory of victory.")
boss("zephyr", "isekai_mahou", "zephyr", ["wind", "lightning"], "legendary", ["isekai_mahou:guardian_core_wind"],
     "Zéfiro", "Zephyr",
     "O Guardião do Vento. Observe-o em combate ou registre a lembrança da vitória.",
     "Guardian of Wind. Observe it in battle or record the memory of victory.")
boss("abyss_sovereign", "isekai_mahou", "abyss_sovereign", ["shadow", "void"], "legendary", ["isekai_mahou:guardian_core_shadow"],
     "Soberano do Abismo", "Abyss Sovereign",
     "O senhor das profundezas. Observe-o ou registre a lembrança da vitória.",
     "Lord of the depths. Observe it or record the memory of victory.")
boss("seraph", "isekai_mahou", "seraph", ["light", "purity"], "legendary", ["isekai_mahou:guardian_core_light"],
     "Serafim", "Seraph",
     "O guardião do céu. Observe-o ou registre a lembrança da vitória.",
     "Guardian of the sky. Observe it or record the memory of victory.")
boss("twilight_herald", "isekai_mahou", "twilight_herald", ["twilight", "light", "shadow"], "legendary",
     ["isekai_mahou:twilight_rift", "minecraft:end_crystal"],
     "Arauto do Crepúsculo", "Twilight Herald",
     "O fim de todas as coisas. Registre a lembrança da batalha final.",
     "The end of all things. Record the memory of the final battle.")

CATEGORY_ORDER = ["block", "creature", "biome", "sky", "dimension", "structure", "boss"]
NOTES = {"common": 1, "uncommon": 2, "rare": 2, "epic": 3, "legendary": 4}

# =====================================================================================================
#  TESES (espelho de Thesis.java: nomes/descricoes)
# =====================================================================================================
THESES = [
    ("mana_primordial", "Mana Primordial", "Primordial Mana",
     "Toda magia nasce da mesma fonte. Ligue duas runas arcanas e prove que a mana flui.",
     "All magic springs from one source. Link two arcane runes and prove that mana flows."),
    ("ignis", "Tratado do Fogo", "Treatise on Fire",
     "O fogo consome para criar. Um Mago que conclui esta tese desperta o Fogo.",
     "Fire consumes in order to create. A Mage who completes this thesis awakens Fire."),
    ("aqua", "Tratado da Água", "Treatise on Water",
     "A água molda até a pedra. Um Mago que conclui esta tese desperta a Água.",
     "Water shapes even stone. A Mage who completes this thesis awakens Water."),
    ("terra", "Tratado da Terra", "Treatise on Earth",
     "A terra sustenta tudo. Um Mago que conclui esta tese desperta a Terra.",
     "Earth upholds everything. A Mage who completes this thesis awakens Earth."),
    ("ventus", "Tratado do Vento", "Treatise on Wind",
     "O vento não tem forma e por isso alcança tudo. Um Mago que conclui esta tese desperta o Vento.",
     "Wind has no shape, so it reaches everything. A Mage who completes this thesis awakens Wind."),
    ("dualitas", "Da Dualidade", "On Duality",
     "Luz e Sombra parecem opostas, mas uma define a outra. Ligue as duas.",
     "Light and Shadow seem opposed, yet each defines the other. Link them."),
    ("convergentia", "Da Convergência", "On Convergence",
     "Do Magma ao Gelo: os quatro elementos clássicos se tocam e se fundem.",
     "From Magma to Ice: the four classic elements touch and fuse."),
    ("planorum", "Dos Outros Planos", "On Other Planes",
     "O Fogo Infernal do Nether e o Vazio do End: mundos ligados pela Sombra.",
     "The Nether's Hellfire and the End's Void: worlds bound by Shadow."),
    ("astralis", "Do Céu Astral", "On the Astral Sky",
     "Estrelas e crepúsculo escrevem um mapa de mana no céu.",
     "Stars and dusk write a map of mana across the sky."),
    ("magnum_opus", "Magnum Opus", "Magnum Opus",
     "A grande teoria arcana: os seis elementos num único circuito.",
     "The great arcane theory: all six elements in a single circuit."),
]


# =====================================================================================================
#  TRADUCOES
# =====================================================================================================
def build_lang():
    pt, en = {}, {}

    def t(key, vpt, ven):
        pt[key] = vpt
        en[key] = ven

    # Itens e bloco
    t("item.isekai_mahou.arcane_lens", "Lente Arcana", "Arcane Lens")
    t("item.isekai_mahou.arcane_lens.tooltip", "Segure o botão direito para observar um fenômeno.",
      "Hold right-click to observe a phenomenon.")
    t("item.isekai_mahou.arcane_lens.tooltip2", "Mire em blocos, criaturas, no céu ou só observe o lugar.",
      "Aim at blocks, creatures, the sky, or just study the place.")
    t("item.isekai_mahou.research_note", "Nota de Pesquisa", "Research Note")
    t("item.isekai_mahou.research_note.named", "Nota de Pesquisa: %s", "Research Note: %s")
    t("item.isekai_mahou.research_note.phenomenon", "Fenômeno: %s", "Phenomenon: %s")
    t("item.isekai_mahou.research_note.hint", "Coloque na Mesa de Pesquisa para escrever Teses.",
      "Place it in a Research Table to write Theses.")
    t("item.isekai_mahou.mage_atlas", "Atlas Arcano", "Arcane Atlas")
    t("item.isekai_mahou.mage_atlas.tooltip", "Fenômenos registrados: %s/%s", "Recorded phenomena: %s/%s")
    t("item.isekai_mahou.mage_atlas.tooltip2", "Teses concluídas: %s/%s", "Completed theses: %s/%s")
    t("block.isekai_mahou.research_table", "Mesa de Pesquisa", "Research Table")
    t("block.isekai_mahou.research_table.tooltip", "Conclua Teses ligando runas feitas de Notas de Pesquisa.",
      "Complete Theses by linking runes made from Research Notes.")
    t("block.isekai_mahou.research_table.tooltip2", "Estantes por perto liberam as teses avançadas.",
      "Nearby bookshelves unlock advanced theses.")

    # Mensagens
    t("message.isekai_mahou.mage.discovered", "✦ Novo fenômeno no Atlas: %s", "✦ New phenomenon in the Atlas: %s")
    t("message.isekai_mahou.mage.discovered_detail", "%s · %s · +%s nota(s), +%s XP",
      "%s · %s · +%s note(s), +%s XP")
    t("message.isekai_mahou.mage.restudied", "Você aprofundou o estudo de %s (+1 nota).",
      "You deepened your study of %s (+1 note).")
    t("message.isekai_mahou.mage.cooldown", "Você já estudou %s hoje. Volte amanhã.",
      "You already studied %s today. Come back tomorrow.")
    t("message.isekai_mahou.mage.nothing", "Nada de novo para observar aqui.", "Nothing new to observe here.")
    t("message.isekai_mahou.mage.thesis_locked", "Esta tese exige %s tese(s) concluída(s).",
      "This thesis requires %s completed thesis(es).")
    t("message.isekai_mahou.mage.thesis_known", "Você já concluiu esta tese.", "You already completed this thesis.")
    t("message.isekai_mahou.mage.need_paper", "Falta papel na mesa (%s necessário).", "Not enough paper on the table (%s needed).")
    t("message.isekai_mahou.mage.need_shelves", "Faltam estantes perto da mesa (%s/%s).",
      "Not enough bookshelves near the table (%s/%s).")
    t("message.isekai_mahou.mage.need_notes", "As notas na mesa não bastam para as runas colocadas.",
      "The notes on the table are not enough for the placed runes.")
    t("message.isekai_mahou.mage.circuit_invalid", "O circuito rúnico ainda não está completo.",
      "The rune circuit is not complete yet.")
    t("message.isekai_mahou.mage.thesis_complete", "✦ Tese concluída: %s!", "✦ Thesis completed: %s!")
    t("message.isekai_mahou.mage.thesis_xp", "+%s XP mágico · %s ponte(s) rúnica(s)", "+%s magic XP · %s rune bridge(s)")
    t("message.isekai_mahou.mage.glyphs_learned", "Runas aprendidas: %s", "Runes learned: %s")
    t("message.isekai_mahou.mage.rank", "Pesquisa Arcana: %s/%s teses.", "Arcane Research: %s/%s theses.")
    t("message.isekai_mahou.mage.bonus_items", "Recompensa extra: %s", "Bonus reward: %s")
    t("announce.isekai_mahou.mage_thesis", "Tese concluída: %s", "Thesis completed: %s")
    t("command.isekai_mahou.mage.reveal", "Todos os fenômenos foram revelados no Atlas.", "All phenomena were revealed in the Atlas.")
    t("command.isekai_mahou.mage.reset", "A pesquisa arcana foi reiniciada.", "Arcane research was reset.")
    t("command.isekai_mahou.mage.notes", "Você recebeu %s nota(s) de %s.", "You received %s note(s) of %s.")
    t("command.isekai_mahou.mage.ritual", "Itens do ritual do Mago entregues.", "Mage ritual items given.")
    t("command.isekai_mahou.mage.bad_element", "Elemento desconhecido: %s", "Unknown element: %s")

    # Tela da mesa
    t("screen.isekai_mahou.research_table.theses", "Teses", "Theses")
    t("screen.isekai_mahou.research_table.rank", "Teses: %s/%s", "Theses: %s/%s")
    t("screen.isekai_mahou.research_table.notes", "Notas", "Notes")
    t("screen.isekai_mahou.research_table.paper", "Papel", "Paper")
    t("screen.isekai_mahou.research_table.palette", "Runas das notas", "Runes from notes")
    t("screen.isekai_mahou.research_table.palette_empty", "Coloque notas na mesa.", "Put notes on the table.")
    t("screen.isekai_mahou.research_table.complete", "Concluir Tese", "Complete Thesis")
    t("screen.isekai_mahou.research_table.clear", "Limpar", "Clear")
    t("screen.isekai_mahou.research_table.reroll", "Rearranjar", "Reshuffle")
    t("screen.isekai_mahou.research_table.reroll.tooltip", "Gera um novo arranjo da grade. Custa 1 papel.",
      "Generates a new grid layout. Costs 1 paper.")
    t("screen.isekai_mahou.research_table.choose", "Escolha uma tese na lista.", "Choose a thesis from the list.")
    t("screen.isekai_mahou.research_table.reward", "Recompensa: %s XP", "Reward: %s XP")
    t("screen.isekai_mahou.research_table.bridges", "Pontes: %s (+%s%% XP)", "Bridges: %s (+%s%% XP)")
    t("screen.isekai_mahou.research_table.shelves", "Estantes %s/%s", "Shelves %s/%s")
    t("screen.isekai_mahou.research_table.paper_need", "Papel %s/%s", "Paper %s/%s")
    t("screen.isekai_mahou.research_table.any", "Qualquer", "Any")
    t("screen.isekai_mahou.research_table.req", "%s %s/%s", "%s %s/%s")
    t("screen.isekai_mahou.research_table.selected", "Runa: %s", "Rune: %s")
    t("screen.isekai_mahou.research_table.status.open", "Circuito aberto: ligue a entrada à saída.",
      "Open circuit: link the entry to the exit.")
    t("screen.isekai_mahou.research_table.status.conflict", "Runas incompatíveis lado a lado!", "Incompatible runes side by side!")
    t("screen.isekai_mahou.research_table.status.floating", "Há runas soltas fora do circuito.", "Some runes are outside the circuit.")
    t("screen.isekai_mahou.research_table.status.reqs", "Faltam notas exigidas pela tese.", "The thesis needs more notes.")
    t("screen.isekai_mahou.research_table.status.notes", "Notas insuficientes na mesa.", "Not enough notes on the table.")
    t("screen.isekai_mahou.research_table.status.paper", "Falta papel na mesa.", "Not enough paper on the table.")
    t("screen.isekai_mahou.research_table.status.shelves", "Faltam estantes perto da mesa.", "Not enough bookshelves nearby.")
    t("screen.isekai_mahou.research_table.status.ready", "Circuito estável! Pronto para concluir.", "Stable circuit! Ready to complete.")
    t("screen.isekai_mahou.research_table.status.locked", "Tese bloqueada.", "Thesis locked.")
    t("screen.isekai_mahou.research_table.status.done", "Tese já concluída.", "Thesis already completed.")
    t("screen.isekai_mahou.research_table.completed_banner", "✦ Tese concluída! ✦", "✦ Thesis completed! ✦")
    t("screen.isekai_mahou.research_table.cell.entry", "Runa de Entrada: %s", "Entry Rune: %s")
    t("screen.isekai_mahou.research_table.cell.exit", "Runa de Saída: %s", "Exit Rune: %s")
    t("screen.isekai_mahou.research_table.cell.fixed", "Runa Fixa: %s", "Fixed Rune: %s")
    t("screen.isekai_mahou.research_table.cell.fixed_hint", "Use-a como ponte para ganhar XP extra.",
      "Use it as a bridge for bonus XP.")
    t("screen.isekai_mahou.research_table.cell.blocked", "Fissura: nada pode ser escrito aqui.", "Fissure: nothing can be written here.")
    t("screen.isekai_mahou.research_table.cell.placed", "Runa: %s (clique direito remove)", "Rune: %s (right-click removes)")
    t("screen.isekai_mahou.research_table.cell.compat", "Combina com: %s", "Matches: %s")
    t("screen.isekai_mahou.research_table.compat_any", "todas as runas", "every rune")
    t("screen.isekai_mahou.research_table.compat_bases", "os 6 elementos base", "the 6 base elements")
    t("screen.isekai_mahou.research_table.thesis.requires", "Requer %s tese(s) concluída(s)", "Requires %s completed thesis(es)")
    t("screen.isekai_mahou.research_table.thesis.notes", "Notas: %s", "Notes: %s")
    t("screen.isekai_mahou.research_table.thesis.costs", "Papel: %s · Estantes: %s", "Paper: %s · Bookshelves: %s")
    t("screen.isekai_mahou.research_table.thesis.circuit", "Circuito: %s → %s", "Circuit: %s → %s")
    t("screen.isekai_mahou.research_table.thesis.awakens", "Mago: desperta %s", "Mage: awakens %s")
    t("screen.isekai_mahou.research_table.thesis.runes", "Ensina Runas de nível %s", "Teaches tier %s Runes")
    t("screen.isekai_mahou.research_table.thesis.essence", "Recompensa: Essência Arcana e Cristal de Mana",
      "Reward: Arcane Essence and Mana Crystal")
    t("screen.isekai_mahou.research_table.thesis.done", "✔ Concluída", "✔ Completed")
    t("screen.isekai_mahou.research_table.thesis.available", "Disponível", "Available")
    t("screen.isekai_mahou.research_table.help", "Como funciona", "How it works")
    t("screen.isekai_mahou.research_table.help1", "1. Escolha uma tese e coloque notas e papel na mesa.",
      "1. Choose a thesis and put notes and paper on the table.")
    t("screen.isekai_mahou.research_table.help2", "2. Escolha uma runa à direita e clique na grade para escrevê-la.",
      "2. Pick a rune on the right and click the grid to write it.")
    t("screen.isekai_mahou.research_table.help3", "3. Ligue a runa de entrada (moldura dourada) à de saída (moldura prateada) por células vizinhas.",
      "3. Link the entry rune (gold frame) to the exit rune (silver frame) through adjacent cells.")
    t("screen.isekai_mahou.research_table.help4", "4. Vizinhas precisam combinar: mesmo elemento, dois elementos base, ou uma fusão e seus pais.",
      "4. Neighbors must match: same element, two base elements, or a fusion and its parents.")
    t("screen.isekai_mahou.research_table.help5", "5. Arcano combina com tudo. Runas fixas usadas como ponte dão +15%% XP.",
      "5. Arcane matches everything. Fixed runes used as bridges give +15%% XP.")

    # Atlas
    t("screen.isekai_mahou.mage_atlas.title", "Atlas Arcano", "Arcane Atlas")
    t("screen.isekai_mahou.mage_atlas.progress", "%s/%s observados", "%s/%s observed")
    t("screen.isekai_mahou.mage_atlas.page", "%s/%s", "%s/%s")
    t("screen.isekai_mahou.mage_atlas.observed", "✔ Observado", "✔ Observed")
    t("screen.isekai_mahou.mage_atlas.unobserved", "✖ Ainda não observado", "✖ Not observed yet")
    t("screen.isekai_mahou.mage_atlas.elements", "Elementos:", "Elements:")
    t("screen.isekai_mahou.mage_atlas.notes", "Notas na 1ª observação: %s", "Notes on first observation: %s")
    t("screen.isekai_mahou.mage_atlas.select", "Escolha um fenômeno para ver os detalhes.",
      "Pick a phenomenon to see its details.")
    t("screen.isekai_mahou.mage_atlas.empty", "Nenhum fenômeno conhecido.", "No known phenomena.")
    t("screen.isekai_mahou.mage_atlas.theses_progress", "Teses concluídas: %s/%s", "Completed theses: %s/%s")
    t("screen.isekai_mahou.mage_atlas.thesis_locked", "Bloqueada", "Locked")
    t("screen.isekai_mahou.mage_atlas.total", "Total: %s/%s fenômenos", "Total: %s/%s phenomena")
    t("screen.isekai_mahou.mage_atlas.unknown", "???", "???")

    for cat, vpt, ven in [("all", "Todos", "All"), ("block", "Blocos", "Blocks"), ("creature", "Criaturas", "Creatures"),
                          ("biome", "Biomas", "Biomes"), ("sky", "Céu", "Sky"), ("dimension", "Dimensões", "Dimensions"),
                          ("structure", "Estruturas", "Structures"), ("boss", "Chefes", "Bosses"), ("theses", "Teses", "Theses")]:
        t("mage_category.isekai_mahou." + cat, vpt, ven)
    for r, vpt, ven in [("common", "Comum", "Common"), ("uncommon", "Incomum", "Uncommon"), ("rare", "Raro", "Rare"),
                        ("epic", "Épico", "Epic"), ("legendary", "Lendário", "Legendary")]:
        t("mage_rarity.isekai_mahou." + r, vpt, ven)

    for p in P:
        t("phenomenon.isekai_mahou." + p["id"], p["name_pt"], p["name_en"])
        t("phenomenon.isekai_mahou." + p["id"] + ".desc", p["desc_pt"], p["desc_en"])
    t("phenomenon.isekai_mahou.unknown", "Fenômeno Desconhecido", "Unknown Phenomenon")

    for tid, npt, nen, dpt, den in THESES:
        t("thesis.isekai_mahou." + tid, npt, nen)
        t("thesis.isekai_mahou." + tid + ".desc", dpt, den)

    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


# =====================================================================================================
#  DADOS
# =====================================================================================================
def build_data():
    out_dir = os.path.join(DATA, "mage_phenomena")
    os.makedirs(out_dir, exist_ok=True)
    # Nao apaga a pasta: outros modulos (ex.: structures_b) tambem escrevem fenomenos aqui.
    supply = {}
    for i, p in enumerate(P):
        obj = {"category": p["cat"], "elements": p["elements"], "rarity": p["rarity"], "icon": p["icons"], "order": i}
        obj.update(p["cond"])
        write_json(os.path.join(out_dir, p["id"] + ".json"), obj)
        if not any(s.startswith("isekai_mahou:") for s in p["cond"].get("entities", []) + p["cond"].get("blocks", [])
                   + p["cond"].get("dimensions", [])):
            n = NOTES[p["rarity"]]
            for k in range(n):
                e = p["elements"][k % len(p["elements"])]
                supply[e] = supply.get(e, 0) + 1
    print("fenomenos:", len(P), "| notas da 1a observacao (so vanilla):", dict(sorted(supply.items())))

    # Receitas
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "arcane_lens.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" A ", "GPG", " S "],
        "key": {"A": {"item": "minecraft:amethyst_shard"}, "G": {"item": "minecraft:gold_ingot"},
                "P": {"item": "minecraft:glass_pane"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": "isekai_mahou:arcane_lens", "count": 1}})
    write_json(os.path.join(rec, "mage_atlas.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:lapis_lazuli"}, {"item": "minecraft:ink_sac"},
                        {"item": "minecraft:feather"}],
        "result": {"id": "isekai_mahou:mage_atlas", "count": 1}})
    write_json(os.path.join(rec, "research_table.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BAC", "WWW", "S S"],
        "key": {"B": {"item": "minecraft:book"}, "A": {"item": "minecraft:amethyst_shard"},
                "C": {"item": "minecraft:candle"}, "W": {"tag": "minecraft:wooden_slabs"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": "isekai_mahou:research_table", "count": 1}})

    write_json(os.path.join(DATA, "loot_table", "blocks", "research_table.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": "isekai_mahou:research_table"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "isekai_mahou:blocks/research_table"})
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
               {"replace": False, "values": ["isekai_mahou:research_table"]})


# =====================================================================================================
#  MODELOS
# =====================================================================================================
def face_uv(fr, to, d):
    """UV padrao do Minecraft para a face, deslocado para caber em 0..16.
    Sem isso, elementos acima de y=16 (vela, pena, tinteiro) ganham V negativo e amostram
    a textura vizinha no atlas de blocos."""
    if d == "down":
        u = [fr[0], 16 - to[2], to[0], 16 - fr[2]]
    elif d == "up":
        u = [fr[0], fr[2], to[0], to[2]]
    elif d == "north":
        u = [16 - to[0], 16 - to[1], 16 - fr[0], 16 - fr[1]]
    elif d == "south":
        u = [fr[0], 16 - to[1], to[0], 16 - fr[1]]
    elif d == "west":
        u = [fr[2], 16 - to[1], to[2], 16 - fr[1]]
    else:
        u = [16 - to[2], 16 - to[1], 16 - fr[2], 16 - fr[1]]
    shifted = False
    for a, b in ((0, 2), (1, 3)):
        lo, hi = min(u[a], u[b]), max(u[a], u[b])
        shift = -lo if lo < 0 else (16 - hi if hi > 16 else 0)
        if shift:
            u[a] += shift
            u[b] += shift
            shifted = True
    return u if shifted else None


def box(fr, to, tex, cull_down=False, faces=("north", "south", "east", "west", "up", "down"), uv_up=None, rot_up=0):
    f = {}
    for d in faces:
        face = {"texture": tex}
        uv = face_uv(fr, to, d)
        if uv:
            face["uv"] = uv
        if d == "down" and cull_down:
            face["cullface"] = "down"
        if d == "up" and uv_up:
            face["uv"] = uv_up
        if d == "up" and rot_up:
            face["rotation"] = rot_up
        f[d] = face
    return {"from": fr, "to": to, "faces": f}


def build_models():
    els = []
    # tampo
    els.append(box([0, 12, 0], [16, 15, 16], "#wood", faces=("north", "south", "east", "west", "down")))
    els[-1]["faces"]["up"] = {"texture": "#top"}
    # pernas
    for x, z in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        els.append(box([x, 0, z], [x + 2, 12, z + 2], "#wood", cull_down=True))
    # prateleira de baixo com rolos
    els.append(box([2, 3, 2], [14, 4, 14], "#wood"))
    els.append(box([3, 4, 4], [12, 5.5, 5.5], "#scroll"))
    els.append(box([4, 4, 7], [13, 5.5, 8.5], "#scroll"))
    # livro aberto
    els.append(box([3, 15, 4], [13, 15.5, 11], "#cover"))
    # a face de cima gira 180° para o texto ficar de frente para quem está na frente da mesa
    els.append(box([3.5, 15.5, 4.5], [12.5, 16.25, 10.5], "#book", uv_up=[0, 3, 16, 13], rot_up=180))
    # vela
    els.append(box([12.5, 15, 12], [14.5, 18, 14], "#candle"))
    els.append(box([13.25, 18, 12.75], [13.75, 18.75, 13.25], "#wick"))
    # pergaminho enrolado
    els.append(box([2, 15, 12.25], [9, 16.25, 13.75], "#scroll"))
    # tinteiro com pena
    els.append(box([13, 15, 1.5], [15, 17, 3.5], "#ink"))
    els.append(box([13.75, 17, 2.25], [14.25, 20, 2.75], "#quill"))
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "isekai_mahou:block/research_table_wood",
            "top": "isekai_mahou:block/research_table_top",
            "wood": "isekai_mahou:block/research_table_wood",
            "book": "isekai_mahou:block/research_table_book",
            "cover": "isekai_mahou:block/research_table_cover",
            "candle": "isekai_mahou:block/research_table_candle",
            "wick": "isekai_mahou:block/research_table_wick",
            "scroll": "isekai_mahou:block/research_table_scroll",
            "ink": "isekai_mahou:block/research_table_ink",
            "quill": "isekai_mahou:block/research_table_quill",
        },
        "elements": els,
    }
    write_json(os.path.join(ASSETS, "models", "block", "research_table.json"), model)
    write_json(os.path.join(ASSETS, "blockstates", "research_table.json"), {"variants": {
        "facing=north": {"model": "isekai_mahou:block/research_table"},
        "facing=east": {"model": "isekai_mahou:block/research_table", "y": 90},
        "facing=south": {"model": "isekai_mahou:block/research_table", "y": 180},
        "facing=west": {"model": "isekai_mahou:block/research_table", "y": 270},
    }})
    write_json(os.path.join(ASSETS, "models", "item", "research_table.json"), {"parent": "isekai_mahou:block/research_table"})
    write_json(os.path.join(ASSETS, "models", "item", "arcane_lens.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/arcane_lens"}})
    write_json(os.path.join(ASSETS, "models", "item", "mage_atlas.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/mage_atlas"}})
    write_json(os.path.join(ASSETS, "models", "item", "research_note.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/research_note",
                                                                   "layer1": "isekai_mahou:item/research_note_rune"}})


# =====================================================================================================
#  TEXTURAS
# =====================================================================================================
OUTLINE = (34, 22, 16, 255)
PARCH = rgb("ead9b0")
PARCH_D = rgb("cdb98c")
PARCH_DD = rgb("a88f62")
INK = rgb("3b2a1a")
GOLD = rgb("d6ac4a")
GOLD_L = rgb("f3d98a")
GOLD_D = rgb("8f6a28")
WOOD = rgb("5c3a22")
WOOD_L = rgb("80542f")
WOOD_D = rgb("3a2414")
WOOD_DD = rgb("24150b")


def tex_path(kind, name):
    return os.path.join(ASSETS, "textures", kind, name + ".png")


def wood_plank(c, x0, y0, w, h, seed=3, base=WOOD, seam=4):
    rnd = random.Random(seed)
    for y in range(y0, y0 + h):
        row_shift = rnd.random() * 0.08
        for x in range(x0, x0 + w):
            col = base
            if (y - y0) % seam == seam - 1:
                col = darken(base, 0.35)
            else:
                g = math.sin((x + rnd.random() * 0.8) * 0.9 + (y // seam) * 2.1) * 0.06 + row_shift - 0.04
                col = lighten(base, g) if g > 0 else darken(base, -g)
            c.set(x, y, col)
    # pregos/juntas verticais alternadas
    for row in range(h // seam + 1):
        yy = y0 + row * seam
        xx = x0 + (5 if row % 2 else 11) % max(1, w)
        for k in range(seam - 1):
            if y0 <= yy + k < y0 + h and x0 <= xx < x0 + w:
                c.set(xx, yy + k, darken(base, 0.3))


def item_textures():
    # ---------------- Lente Arcana
    c = Canvas(16, 16)
    # cabo
    for i in range(5):
        c.set(10 + i, 10 + i, rgb("6b4226"))
        c.set(11 + i, 10 + i, rgb("8a5a34"))
        c.set(10 + i, 11 + i, rgb("4a2c18"))
    c.set(10, 10, GOLD)
    c.set(11, 11, GOLD_D)
    # vidro
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 6.5, y + 0.5 - 6.5)
            if d <= 4.2:
                t = d / 4.2
                c.set(x, y, mix(rgb("b9a4ff"), rgb("5a3fb8"), t))
            elif d <= 5.6:
                ang = math.atan2(y + 0.5 - 6.5, x + 0.5 - 6.5)
                k = (math.cos(ang + 2.4) + 1) / 2
                c.set(x, y, mix(rgb("9c7330"), rgb("f1cf72"), k))
    c.set(4, 4, rgb("ffffff"))
    c.set(5, 4, rgb("e8e2ff"))
    c.set(4, 5, rgb("e8e2ff"))
    # runa sutil no vidro
    for x, y in [(7, 6), (6, 7), (8, 7), (7, 8), (7, 7)]:
        c.set(x, y, rgb("e6dcff"))
    c.outline(OUTLINE)
    c.save(tex_path("item", "arcane_lens"))

    # ---------------- Atlas Arcano
    c = Canvas(16, 16)
    cover = rgb("2b4f9a")
    c.rect(2, 1, 13, 14, cover)
    c.rect(2, 1, 3, 14, darken(cover, 0.35))        # lombada
    c.rect(13, 2, 14, 14, PARCH)                     # paginas
    for y in range(3, 14, 2):
        c.set(14, y, PARCH_DD)
    c.rect(4, 1, 13, 1, lighten(cover, 0.25))
    c.rect(4, 14, 13, 14, darken(cover, 0.25))
    for (x, y) in [(4, 2), (12, 2), (4, 13), (12, 13)]:
        c.set(x, y, GOLD)
    # emblema: circulo arcano dourado
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8.5, y + 0.5 - 7.5)
            if 2.3 <= d <= 3.3:
                c.set(x, y, GOLD)
    c.set(8, 7, GOLD_L)
    c.set(8, 4, GOLD_L)
    c.set(8, 10, GOLD_D)
    c.set(5, 7, GOLD_D)
    c.set(11, 7, GOLD_L)
    c.noise(0.05, 7)
    c.outline(OUTLINE)
    c.save(tex_path("item", "mage_atlas"))

    # ---------------- Nota de Pesquisa (papel + runa tingida pela cor do elemento)
    c = Canvas(16, 16)
    c.rect(3, 2, 12, 13, PARCH)
    c.rect(12, 3, 12, 13, PARCH_D)
    c.rect(3, 13, 12, 13, PARCH_D)
    # canto dobrado
    c.set(12, 2, (0, 0, 0, 0))
    c.set(11, 2, PARCH_D)
    c.set(12, 3, PARCH_DD)
    for y, x0, x1 in [(4, 4, 9), (6, 4, 10)]:
        for x in range(x0, x1 + 1):
            if (x + y) % 4 != 0:
                c.set(x, y, rgb("8b7350"))
    c.noise(0.04, 11)
    c.outline(rgb("5a4228"))
    c.save(tex_path("item", "research_note"))

    r = Canvas(16, 16)
    rune = ["..#..",
            ".#.#.",
            "#.#.#",
            ".#.#.",
            "..#.."]
    r.from_rows(rune, {"#": (255, 255, 255, 255)}, 5, 8)
    r.set(7, 10, (200, 200, 200, 255))
    r.save(tex_path("item", "research_note_rune"))


def block_textures():
    c = Canvas(16, 16)
    wood_plank(c, 0, 0, 16, 16, seed=5)
    c.noise(0.04, 5)
    c.save(tex_path("block", "research_table_wood"))

    c = Canvas(16, 16)
    wood_plank(c, 0, 0, 16, 16, seed=9, base=rgb("4e3019"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.3 <= d <= 7.2:
                c.set(x, y, GOLD)
            elif 4.6 <= d <= 5.0:
                c.set(x, y, GOLD_D)
    for (x, y) in [(8, 1), (1, 8), (14, 8), (8, 14), (3, 3), (12, 3), (3, 12), (12, 12)]:
        c.set(x, y, GOLD_L)
    c.save(tex_path("block", "research_table_top"))

    c = Canvas(16, 16, rgb("2b4f9a"))
    c.noise(0.06, 3)
    c.outline_rect(0, 0, 15, 15, GOLD_D)
    c.save(tex_path("block", "research_table_cover"))

    c = Canvas(16, 16, PARCH)
    c.rect(7, 0, 8, 15, PARCH_DD)
    c.rect(0, 0, 0, 15, PARCH_D)
    c.rect(15, 0, 15, 15, PARCH_D)
    for y in range(4, 13, 2):
        for x in range(1, 6):
            if (x * 3 + y) % 5:
                c.set(x, y, rgb("6e5638"))
    # diagrama na pagina direita: circulo vermelho com runa
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 11.5, y + 0.5 - 8)
            if 2.2 <= d <= 3.0:
                c.set(x, y, rgb("a3302a"))
    c.set(11, 8, rgb("3a5bd0"))
    c.noise(0.03, 4)
    c.save(tex_path("block", "research_table_book"))

    c = Canvas(16, 16, rgb("efe3c4"))
    for y in range(16):
        c.set(0, y, rgb("d8c79f"))
        c.set(15, y, rgb("d8c79f"))
    for x in (3, 9, 13):
        for y in range(0, 5 + x % 4):
            c.set(x, y, rgb("fff6dc"))
    c.save(tex_path("block", "research_table_candle"))

    c = Canvas(16, 16, rgb("2a1c12"))
    c.rect(0, 0, 15, 3, rgb("ffb347"))
    c.save(tex_path("block", "research_table_wick"))

    c = Canvas(16, 16, PARCH)
    for x in range(16):
        c.set(x, 0, PARCH_D)
        c.set(x, 15, PARCH_DD)
    c.rect(6, 0, 9, 15, rgb("9e2b25"))
    c.rect(6, 0, 6, 15, rgb("7a1f1a"))
    c.noise(0.04, 8)
    c.save(tex_path("block", "research_table_scroll"))

    c = Canvas(16, 16, rgb("1d2347"))
    c.rect(0, 0, 15, 2, rgb("111111"))
    c.rect(2, 4, 3, 12, rgb("4a5aa8"))
    c.noise(0.05, 2)
    c.save(tex_path("block", "research_table_ink"))

    c = Canvas(16, 16, rgb("f2f2ee"))
    for y in range(16):
        c.set(7, y, rgb("c8c8c0"))
        c.set(8, y, rgb("c8c8c0"))
    c.save(tex_path("block", "research_table_quill"))


# ---------------------------------------------------------------- Runas (GUI)
ELEMENTS = ["arcane", "fire", "water", "earth", "wind", "light", "shadow", "magma", "steam", "lightning", "sun",
            "hellfire", "nature", "ice", "purity", "abyss", "sand", "crystal", "gravity", "astral", "void", "twilight"]
ECOLOR = {"arcane": "B98CFF", "fire": "FF6A2B", "water": "3FA9FF", "earth": "A0743C", "wind": "9FF0D0",
          "light": "FFE27A", "shadow": "8A4FC8", "magma": "FF4500", "steam": "D8E6EE", "lightning": "7FDBFF",
          "sun": "FFB300", "hellfire": "C0182E", "nature": "4FBF4A", "ice": "A8E8FF", "purity": "E8F8FF",
          "abyss": "2A4F9A", "sand": "E3C77A", "crystal": "E07AF0", "gravity": "6B5BA8", "astral": "9DB4FF",
          "void": "5A2A8A", "twilight": "C06AA0"}
SYMBOLS = {
    "arcane": ["....#....", "...#.#...", "..#...#..", ".#.###.#.", "#..#.#..#", ".#.###.#.", "..#...#..", "...#.#...", "....#...."],
    "fire": ["....#....", "....#....", "...###...", "...#.#...", "..##.##..", "..#...#..", ".##...##.", ".#.....#.", ".#######."],
    "water": ["....#....", "....#....", "...#.#...", "...#.#...", "..#...#..", ".#.....#.", ".#.....#.", "..#...#..", "...###..."],
    "earth": [".........", ".#######.", ".#.....#.", ".#.###.#.", ".#.#.#.#.", ".#.###.#.", ".#.....#.", ".#######.", "........."],
    "wind": [".........", ".#####...", "......#..", "..####.#.", ".#....#..", ".#.###...", "..#......", "...#####.", "........."],
    "light": ["....#....", ".#..#..#.", "..#...#..", "...###...", "####.####", "...###...", "..#...#..", ".#..#..#.", "....#...."],
    "shadow": ["...####..", "..##.....", ".##......", ".#.......", ".#.......", ".#.......", ".##......", "..##.....", "...####.."],
    "magma": ["....#....", "...#.#...", "..#...#..", ".#.....#.", "#########", ".#.#.#.#.", "..#.#.#..", ".........", "........."],
    "steam": [".#...#...", "..#...#..", ".#...#...", "..#...#..", ".........", "#########", ".#.....#.", "..#####..", "........."],
    "lightning": [".....##..", "....##...", "...##....", "..#####..", "....##...", "...##....", "..##.....", ".##......", ".#......."],
    "sun": ["#...#...#", ".#.....#.", "...###...", "..#####..", "#.#####.#", "..#####..", "...###...", ".#.....#.", "#...#...#"],
    "hellfire": ["....#....", "...#.#...", "..#.#.#..", "..#.#.#..", ".#.###.#.", ".#.###.#.", "#..###..#", ".#.....#.", "..#####.."],
    "nature": ["......##.", "....###.#", "...##..#.", "..#...#..", ".#...#.#.", ".#..#..#.", ".#.#..#..", "..###.#..", ".#..##..."],
    "ice": ["....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."],
    "purity": ["....#....", "...#.#...", "..#...#..", ".#..#..#.", ".#.###.#.", ".#..#..#.", "..#...#..", "...###...", "........."],
    "abyss": ["..#####..", ".#.....#.", "#..###..#", "#.#...#.#", "#.#.#.#.#", "#.#..##.#", "#..#....#", ".#..####.", "..#......"],
    "sand": ["#########", ".#.....#.", "..#...#..", "...#.#...", "....#....", "...#.#...", "..#.#.#..", ".#.###.#.", "#########"],
    "crystal": ["...###...", "..#.#.#..", ".#..#..#.", "#########", ".#..#..#.", "..#.#.#..", "...#.#...", "....#....", "........."],
    "gravity": ["....#....", "....#....", "....#....", ".#..#..#.", "..#.#.#..", "...###...", "....#....", ".........", "#########"],
    "astral": ["#...#...#", "....#....", "...#.#...", "..#...#..", "###...###", "..#...#..", "...#.#...", "....#....", "#...#...#"],
    "void": ["..#####..", ".#.....#.", "#.......#", "#.......#", "#...#...#", "#.......#", "#.......#", ".#.....#.", "..#####.."],
    "twilight": ["..#####..", ".###...#.", "####....#", "####....#", "####....#", "####....#", "####....#", ".###...#.", "..#####.."],
}


def rune_disc(c, ox, oy, col, sym):
    """Disco de pedra 16x16 com aro e simbolo na cor do elemento."""
    base = mix(rgb("1c1a26"), col, 0.28)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.6:
                if d >= 6.6:
                    k = (math.cos(math.atan2(y - 8, x - 8) + 2.3) + 1) / 2
                    c.set(ox + x, oy + y, mix(darken(col, 0.35), lighten(col, 0.25), k))
                else:
                    shade = 0.12 * (1 - d / 6.6)
                    c.set(ox + x, oy + y, lighten(base, shade))
    bright = lighten(col, 0.35)
    for sy, row in enumerate(sym):
        for sx, ch in enumerate(row):
            if ch == "#":
                c.set(ox + 3 + sx + 1, oy + 3 + sy + 1, darken(col, 0.6))  # sombra
    for sy, row in enumerate(sym):
        for sx, ch in enumerate(row):
            if ch == "#":
                c.set(ox + 3 + sx, oy + 3 + sy, bright)


def runes_texture():
    c = Canvas(256, 64)
    for i, e in enumerate(ELEMENTS):
        rune_disc(c, (i % 16) * 16, (i // 16) * 16, rgb(ECOLOR[e]), SYMBOLS[e])
    # Linha 2 (y=32): especiais
    # 0: fissura (celula bloqueada) 19x19 em (0,32)
    for y in range(19):
        for x in range(19):
            c.set(x, 32 + y, mix(rgb("15131c"), rgb("2a2636"), ((x * 7 + y * 13) % 10) / 30))
    crack = [(3, 2), (4, 3), (5, 4), (5, 5), (6, 6), (7, 7), (8, 7), (9, 8), (10, 9), (10, 10), (11, 11), (12, 12),
             (13, 13), (14, 14), (15, 16), (8, 8), (7, 9), (6, 11), (5, 12), (4, 14), (11, 8), (13, 7), (15, 6), (16, 4)]
    for x, y in crack:
        c.set(x, 32 + y, rgb("050407"))
        c.set(x + 1, 32 + y, rgb("3e3850"))
    # 1: moldura de entrada (dourada) 19x19 em (20,32)
    # 2: moldura de saida (prateada) 19x19 em (40,32)
    for idx, (light, dark) in enumerate([(GOLD_L, GOLD_D), (rgb("f0f4ff"), rgb("7d86a8"))]):
        ox = 20 + idx * 20
        for i in range(19):
            for (x, y) in [(i, 0), (i, 18), (0, i), (18, i)]:
                c.set(ox + x, 32 + y, light if (x + y) % 2 == 0 else dark)
            for (x, y) in [(i, 1), (i, 17), (1, i), (17, i)]:
                if 1 <= i <= 17:
                    c.set(ox + x, 32 + y, dark)
        for (x, y) in [(0, 0), (18, 0), (0, 18), (18, 18)]:
            c.set(ox + x, 32 + y, rgb("ffffff"))
    # 3: cadeado (runa fixa) 7x7 em (60,32)
    lock = ["..###..", ".#...#.", ".#...#.", "#######", "##.#.##", "###.###", "#######"]
    c.from_rows(lock, {"#": rgb("c9cbd6")}, 60, 32)
    c.set(63, 37, rgb("2a2636"))
    # 4: marcador de selecao 18x18 em (70,32)
    for i in range(18):
        for (x, y) in [(i, 0), (i, 17), (0, i), (17, i)]:
            c.set(70 + x, 32 + y, (255, 255, 255, 255) if (i // 3) % 2 == 0 else (255, 224, 130, 255))
    # 5: X de conflito 9x9 em (90,32)
    for i in range(9):
        c.set(90 + i, 32 + i, rgb("ff3b3b"))
        c.set(90 + 8 - i, 32 + i, rgb("ff3b3b"))
        if i < 8:
            c.set(91 + i, 32 + i, rgb("9b1010"))
            c.set(90 + 7 - i, 32 + i + 1, rgb("9b1010"))
    c.save(tex_path("gui", "mage_runes"))


def slot(c, x, y, size=18):
    """Moldura de slot em tom de pergaminho (x,y = canto da moldura)."""
    c.rect(x, y, x + size - 1, y + size - 1, rgb("7a6044"))
    c.rect(x, y, x + size - 2, y, rgb("4a3622"))
    c.rect(x, y, x, y + size - 2, rgb("4a3622"))
    c.rect(x + 1, y + size - 1, x + size - 1, y + size - 1, rgb("f6ead0"))
    c.rect(x + size - 1, y + 1, x + size - 1, y + size - 1, rgb("f6ead0"))


def parchment(c, x0, y0, x1, y1, seed, base=PARCH, edge=PARCH_D):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            e = min(x - x0, x1 - x, y - y0, y1 - y)
            col = base
            if e < 4:
                col = mix(edge, base, e / 4)
            f = (rnd.random() - 0.5) * 0.07
            col = lighten(col, f) if f > 0 else darken(col, -f)
            c.set(x, y, col)


def frame_border(c, x0, y0, x1, y1, outer, light, dark):
    c.outline_rect(x0, y0, x1, y1, outer)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, light)
    c.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, light)
    c.rect(x0 + 1, y1 - 1, x1 - 1, y1 - 1, dark)
    c.rect(x1 - 1, y0 + 1, x1 - 1, y1 - 1, dark)


def table_gui():
    W, H = 340, 232
    c = Canvas(512, 256)
    wood_plank(c, 0, 0, W, H, seed=21, base=WOOD, seam=6)
    frame_border(c, 0, 0, W - 1, H - 1, WOOD_DD, WOOD_L, WOOD_D)
    # cantos dourados
    for (x, y, sx, sy) in [(2, 2, 1, 1), (W - 3, 2, -1, 1), (2, H - 3, 1, -1), (W - 3, H - 3, -1, -1)]:
        for i in range(6):
            c.set(x + sx * i, y, GOLD)
            c.set(x, y + sy * i, GOLD)
        c.set(x + sx, y + sy, GOLD_L)
    # area de pergaminho
    parchment(c, 4, 15, W - 5, H - 5, 5)
    c.outline_rect(4, 15, W - 5, H - 5, WOOD_D)
    # lista de teses
    parchment(c, 6, 17, 109, 131, 6, base=PARCH_D, edge=PARCH_DD)
    c.outline_rect(6, 17, 109, 131, rgb("6b5236"))
    for i in range(1, 10):
        y = 19 + i * 11 - 1
        for x in range(9, 107):
            if x % 2 == 0:
                c.set(x, y, rgb("b49d70"))
    # tabuleiro de pedra
    sx0, sy0 = 111, 16
    c.rect(sx0, sy0, sx0 + 101, sy0 + 101, rgb("26283a"))
    c.outline_rect(sx0, sy0, sx0 + 101, sy0 + 101, rgb("12131d"))
    c.outline_rect(sx0 + 1, sy0 + 1, sx0 + 100, sy0 + 100, GOLD_D)
    rnd = random.Random(9)
    for r in range(5):
        for col in range(5):
            x, y = 114 + col * 19, 19 + r * 19
            base = mix(rgb("3a3e52"), rgb("454a62"), rnd.random())
            c.rect(x, y, x + 18, y + 18, base)
            c.rect(x, y, x + 18, y, darken(base, 0.45))
            c.rect(x, y, x, y + 18, darken(base, 0.45))
            c.rect(x + 1, y + 18, x + 18, y + 18, lighten(base, 0.18))
            c.rect(x + 18, y + 1, x + 18, y + 18, lighten(base, 0.18))
            for k in range(6):
                px, py = x + 2 + rnd.randrange(15), y + 2 + rnd.randrange(15)
                c.set(px, py, darken(base, 0.15) if k % 2 else lighten(base, 0.08))
            c.set(x + 9, y + 9, lighten(base, 0.12))
    # painel direito
    parchment(c, 214, 17, W - 7, 133, 7)
    c.outline_rect(214, 17, W - 7, 133, rgb("6b5236"))
    for i in range(14):
        x, y = 218 + (i % 7) * 16, 96 + (i // 7) * 16
        c.rect(x, y, x + 15, y + 15, rgb("c4ad80"))
        c.outline_rect(x, y, x + 15, y + 15, rgb("a88f62"))
    # separador
    for x in range(6, W - 6):
        c.set(x, 137, GOLD_D if x % 3 else GOLD)
    # slots do jogador
    for r in range(3):
        for col in range(9):
            slot(c, 7 + col * 18, 149 + r * 18)
    for col in range(9):
        slot(c, 7 + col * 18, 207)
    # notas e papel
    for r in range(3):
        for col in range(6):
            slot(c, 177 + col * 18, 149 + r * 18)
    slot(c, 299, 149)
    # silhueta de papel no slot de papel
    for y in range(152, 164):
        for x in range(303, 313):
            if (x, y) not in [(312, 152)]:
                c.set(x, y, mix(rgb("7a6044"), PARCH, 0.25))
    c.save(tex_path("gui", "mage_research_table"))


def atlas_gui():
    W, H = 292, 184
    c = Canvas(512, 256)
    cover = rgb("23345e")
    rnd = random.Random(31)
    for y in range(H):
        for x in range(W):
            f = (rnd.random() - 0.5) * 0.10
            col = lighten(cover, f) if f > 0 else darken(cover, -f)
            c.set(x, y, col)
    frame_border(c, 0, 0, W - 1, H - 1, rgb("0e1630"), lighten(cover, 0.2), darken(cover, 0.4))
    # cantoneiras douradas
    for (x, y, sx, sy) in [(1, 1, 1, 1), (W - 2, 1, -1, 1), (1, H - 2, 1, -1), (W - 2, H - 2, -1, -1)]:
        for i in range(9):
            for j in range(9 - i):
                c.set(x + sx * i, y + sy * j, GOLD if (i + j) < 7 else GOLD_D)
        c.set(x + sx * 2, y + sy * 2, GOLD_L)
    # lombada
    c.rect(140, 0, 151, H - 1, darken(cover, 0.35))
    for y in range(6, H - 6, 12):
        c.rect(141, y, 150, y + 1, GOLD_D)
    # paginas (pilha + folha de cima)
    for k in range(3, 0, -1):
        c.rect(8 - k, 6 + k, 143, 177 + k, PARCH_DD if k % 2 else PARCH_D)
        c.rect(148, 6 + k, 283 + k, 177 + k, PARCH_DD if k % 2 else PARCH_D)
    parchment(c, 8, 6, 143, 177, 41)
    parchment(c, 148, 6, 283, 177, 42)
    # sombra da dobra
    for x in range(132, 144):
        t = (x - 132) / 11
        for y in range(6, 178):
            c.set(x, y, mix(c.get(x, y), rgb("8a7148"), t * 0.6))
    for x in range(148, 160):
        t = 1 - (x - 148) / 11
        for y in range(6, 178):
            c.set(x, y, mix(c.get(x, y), rgb("8a7148"), t * 0.6))
    # Sprites extras
    # aba (marcador) 24x20 normal em (300,0) e selecionada em (300,22)
    for idx, col in enumerate([rgb("6e2a2a"), rgb("a53c33")]):
        oy = idx * 22
        c.rect(300, oy, 323, oy + 19, col)
        c.outline_rect(300, oy, 323, oy + 19, darken(col, 0.5))
        c.rect(301, oy + 1, 322, oy + 1, lighten(col, 0.25))
        c.rect(302, oy + 17, 321, oy + 17, GOLD_D if idx == 0 else GOLD)
    # casa de fenomeno 18x18 em (330,0) e destacada em (330,20)
    c.rect(330, 0, 347, 17, rgb("d9c69a"))
    c.outline_rect(330, 0, 347, 17, rgb("a08a5e"))
    c.rect(331, 1, 346, 1, rgb("c2ad80"))
    c.rect(330, 20, 347, 37, rgb("f3e6c4"))
    c.outline_rect(330, 20, 347, 37, GOLD)
    c.outline_rect(331, 21, 346, 36, GOLD_L)
    # setas 10x9: esquerda (360,0), direita (372,0); destacadas y+10
    larrow = ["....##....", "...###....", "..####....", ".#########", "##########", ".#########", "..####....", "...###....", "....##...."]
    rarrow = [row[::-1] for row in larrow]
    for idx, col in enumerate([rgb("7a5a32"), rgb("c0392b")]):
        c.from_rows(larrow, {"#": col}, 360, idx * 10)
        c.from_rows(rarrow, {"#": col}, 372, idx * 10)
    c.save(tex_path("gui", "mage_atlas"))


def main():
    build_lang()
    build_data()
    build_models()
    item_textures()
    block_textures()
    runes_texture()
    table_gui()
    atlas_gui()
    print("ok: ritual_mage")


if __name__ == "__main__":
    main()
