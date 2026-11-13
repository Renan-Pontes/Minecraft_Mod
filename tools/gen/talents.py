"""
Modulo talents (release 1.0): gera as ARVORES DE TALENTOS (data/isekai_mahou/talents/<classe>.json), as traducoes
(src/main/lang/talents/{pt_br,en_us}.json) e o icone da aba (textures/gui/tab/talents.png).
Uso (na raiz do projeto):
    py -I tools/gen/talents.py            gera tudo e imprime a TABELA DE ORCAMENTO (pontos x niveis da arvore)
    py -I tools/gen/talents.py --totals   tambem imprime o maximo que cada passiva alcanca (para conferir os tetos)

DESENHO (igual para as 16 classes; o sabor e as mecanicas mudam por classe):
  3 ramos x 7 fileiras + Maestria. Cada ramo recebe 11 nos, nesta ordem:
      a | b c | N1 | d e | X ou Y | f N2 | K            (+ Maestria, gerada sozinha)
    a..f = nos pequenos (5 niveis)      N1, N2 = NOTAVEIS (2 niveis, bem mais fortes)
    X/Y  = ESCOLHA (1 nivel, um OU outro)   K = no FINAL do ramo (1 nivel, muda como a classe joga)
    Maestria = repetivel (50 niveis, ganho que diminui): o destino dos pontos que sobram.
  Cada fileira pede nivel (TIER_LEVEL) e pontos gastos nas fileiras ACIMA do mesmo ramo (TIER_GATE).
  2 especializacoes por classe: uma ASSINATURA (efeitos ganhos ao escolher) + uma fileira exclusiva de 4 nos
  (3 + 3 + 2 + 1 niveis) comprada com os mesmos pontos.
  Os ids a, b, c, d, e de cada ramo sao os 5 nos da arvore antiga (v1.0 interna): quem ja tinha pontos neles nao perde.

Os efeitos sao escritos em forma curta "chave:valor[,chave:valor]" (valor POR NIVEL do no):
  - atributos (ATTR): hp armor tough atk aspd speed kbres luck fall dig sneak mana regen sp
  - passivas (o resto): ver feature/talents/TalentStats.java  (ex.: crit_chance, spell_damage.fire, on_hit.poison, echo)
  - "barrier:N" vira a passiva + a absorcao maxima que ela precisa.
A descricao de cada no NAO e escrita aqui: o jogo monta a partir dos efeitos.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou/talents")
LANG = os.path.join(ROOT, "src/main/lang/talents")
ICON = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui/tab/talents.png")

# ---------------------------------------------------------------------------------------------------
#  ORCAMENTO. Pontos: 1 por nivel (era 1 a cada 2 ate 13/11/2026) (Talents.LEVELS_PER_POINT); nivel maximo 150 (200 depois do Dragao Primordial).
# ---------------------------------------------------------------------------------------------------
LAYOUT = 2
LEVELS_PER_POINT = 1
LEVEL_CAP, HIDDEN_CAP = 150, 200
# posicao no ramo -> (fileira, tipo, niveis)
SLOTS = [(0, "small", 5), (1, "small", 5), (1, "small", 5), (2, "notable", 2), (3, "small", 5), (3, "small", 5),
         (4, "choice", 1), (4, "choice", 1), (5, "small", 5), (5, "notable", 2), (6, "capstone", 1)]
TIER_LEVEL = [1, 6, 14, 26, 36, 50, 70]
TIER_GATE = [0, 3, 6, 9, 15, 20, 28]      # pontos nas fileiras acima, no mesmo ramo
MASTERY_TIER, MASTERY_MAX, MASTERY_LEVEL, MASTERY_GATE, MASTERY_CURVE = 7, 50, 20, 5, 0.95
# fileira da especializacao: (niveis, tipo, nivel minimo, pontos na propria fileira)
SPEC_SLOTS = [(3, "small", 40, 0), (3, "small", 40, 2), (2, "notable", 50, 4), (1, "capstone", 60, 7)]

ATTR = {
    "hp": ("minecraft:generic.max_health", "add_value"),
    "armor": ("minecraft:generic.armor", "add_value"),
    "tough": ("minecraft:generic.armor_toughness", "add_value"),
    "atk": ("minecraft:generic.attack_damage", "add_multiplied_base"),
    "aspd": ("minecraft:generic.attack_speed", "add_multiplied_base"),
    "speed": ("minecraft:generic.movement_speed", "add_multiplied_base"),
    "kbres": ("minecraft:generic.knockback_resistance", "add_value"),
    "luck": ("minecraft:generic.luck", "add_value"),
    "fall": ("minecraft:generic.safe_fall_distance", "add_value"),
    "dig": ("minecraft:player.block_break_speed", "add_multiplied_base"),
    "sneak": ("minecraft:player.sneaking_speed", "add_multiplied_base"),
    "mana": ("isekai_mahou:max_mana", "add_value"),
    "regen": ("isekai_mahou:mana_regen", "add_value"),
    "sp": ("isekai_mahou:spell_power", "add_value"),
    "maxabs": ("minecraft:generic.max_absorption", "add_value"),
}

STATS = {
    "ranged_damage", "summon_damage", "crit_chance", "crit_damage", "lucky", "lifesteal", "mana_on_hit", "mana_on_kill",
    "heal_on_kill", "kill_speed", "damage_resist", "magic_resist", "projectile_resist", "melee_resist", "fall_resist",
    "fire_resist", "dodge", "mana_shield", "thorns", "second_wind", "regen_health", "healing_received",
    "low_health_power", "execute", "first_strike", "backstab", "night_power", "day_power", "undead_damage",
    # release 1.0
    "all_damage", "melee_damage", "spell_damage_all", "boss_damage", "near_damage", "far_damage", "afflicted_damage",
    "high_mana_power", "full_health_power", "aim_power", "run_power", "low_health_resist", "full_health_resist",
    "boss_resist", "combo", "kill_fury", "crit_fury", "parry_power", "revenge", "dodge_power", "surge", "ult_power",
    "summon_mark", "reckless", "wild", "fifth_hit", "echo", "cull", "kill_burst", "plague", "crit_mana", "crit_heal",
    "crit_cdr", "kill_cdr", "charge_on_kill", "cast_refund", "heal_on_cast", "cast_ward", "guard_fortify",
    "guard_reflect", "parry_heal", "parry_mana", "summon_bond", "summon_lifesteal", "cheat_death", "barrier",
}
ELEMENTS = ("fire", "water", "earth", "wind", "light", "shadow")
ON_HIT = ("burn", "poison", "slowness", "weakness", "wither", "bleed", "chill", "shock")

# Tetos (copiados de TalentCombat: mude os dois juntos). Grupo "resist" = soma de todas as reducoes de dano;
# grupo "bonus" = soma de todos os bonus de dano que se somam.
CAPS = {"dodge": 0.25, "crit_chance": 0.5, "crit_damage": 1.0, "lucky": 0.15, "echo": 0.3, "cast_refund": 0.3, "cull": 0.12,
        "mana_shield": 0.5, "crit_cdr": 2.0, "kill_cdr": 5.0, "charge_on_kill": 5.0, "barrier": 20.0, "reckless": 0.3,
        "wild": 0.3, "plague": 1.0, "cheat_death": 0.5}
GROUP_CAPS = {"resist": 0.5, "bonus": 1.0}
RESIST_KEYS = {"damage_resist", "magic_resist", "projectile_resist", "melee_resist", "fall_resist", "fire_resist",
               "low_health_resist", "full_health_resist", "boss_resist", "guard_fortify", "cast_ward", "summon_bond"}
BONUS_KEYS = {"all_damage", "melee_damage", "ranged_damage", "boss_damage", "near_damage", "far_damage",
              "afflicted_damage", "high_mana_power", "full_health_power", "low_health_power", "execute", "first_strike",
              "backstab", "night_power", "day_power", "undead_damage", "aim_power", "run_power", "kill_fury", "crit_fury",
              "parry_power", "revenge", "dodge_power", "ult_power", "reckless"}

# Icone do no = item que lembra o 1o efeito dele (a tela desenha o item)
NODE_ICON = {
    "hp": "minecraft:golden_apple", "armor": "minecraft:iron_chestplate", "tough": "minecraft:netherite_scrap",
    "atk": "minecraft:iron_sword", "aspd": "minecraft:golden_sword", "speed": "minecraft:sugar", "kbres": "minecraft:anvil",
    "luck": "minecraft:rabbit_foot", "fall": "minecraft:feather", "dig": "minecraft:iron_pickaxe",
    "sneak": "minecraft:leather_boots", "mana": "minecraft:lapis_lazuli", "regen": "minecraft:glowstone_dust",
    "sp": "minecraft:amethyst_shard", "ranged_damage": "minecraft:bow", "summon_damage": "minecraft:bone",
    "crit_chance": "minecraft:spyglass", "crit_damage": "minecraft:diamond_sword", "lucky": "minecraft:gold_nugget",
    "lifesteal": "minecraft:ghast_tear", "mana_on_hit": "minecraft:prismarine_crystals",
    "mana_on_kill": "minecraft:experience_bottle", "heal_on_kill": "minecraft:glistering_melon_slice",
    "kill_speed": "minecraft:golden_boots", "damage_resist": "minecraft:shield", "magic_resist": "minecraft:ender_eye",
    "projectile_resist": "minecraft:arrow", "melee_resist": "minecraft:chainmail_chestplate",
    "fall_resist": "minecraft:feather", "fire_resist": "minecraft:magma_cream", "dodge": "minecraft:phantom_membrane",
    "mana_shield": "isekai_mahou:mana_crystal", "thorns": "minecraft:cactus", "second_wind": "minecraft:totem_of_undying",
    "regen_health": "minecraft:sweet_berries", "healing_received": "minecraft:honey_bottle",
    "low_health_power": "minecraft:redstone", "execute": "minecraft:iron_axe", "first_strike": "minecraft:crossbow",
    "backstab": "minecraft:stone_sword", "night_power": "minecraft:clock", "day_power": "minecraft:sunflower",
    "undead_damage": "minecraft:rotten_flesh",
    "on_hit.burn": "minecraft:flint_and_steel", "on_hit.poison": "minecraft:spider_eye", "on_hit.slowness": "minecraft:cobweb",
    "on_hit.weakness": "minecraft:fermented_spider_eye", "on_hit.wither": "minecraft:wither_rose",
    "on_hit.bleed": "minecraft:red_dye", "on_hit.chill": "minecraft:snowball", "on_hit.shock": "minecraft:copper_ingot",
    "on_struck.burn": "minecraft:magma_block", "on_struck.poison": "minecraft:sweet_berries",
    "on_struck.slowness": "minecraft:honey_block", "on_struck.weakness": "minecraft:iron_bars",
    "on_struck.wither": "minecraft:wither_rose", "on_struck.bleed": "minecraft:pointed_dripstone",
    "on_struck.chill": "minecraft:blue_ice", "on_struck.shock": "minecraft:lightning_rod",
    # release 1.0
    "all_damage": "minecraft:nether_star", "melee_damage": "minecraft:netherite_sword",
    "spell_damage_all": "minecraft:enchanted_book", "boss_damage": "minecraft:dragon_head", "near_damage": "minecraft:mace",
    "far_damage": "minecraft:spectral_arrow", "afflicted_damage": "minecraft:poisonous_potato",
    "high_mana_power": "minecraft:lapis_block", "full_health_power": "minecraft:golden_carrot",
    "aim_power": "minecraft:target", "run_power": "minecraft:rabbit_hide", "low_health_resist": "minecraft:turtle_helmet",
    "full_health_resist": "minecraft:diamond_chestplate", "boss_resist": "minecraft:netherite_chestplate",
    "combo": "minecraft:blaze_powder", "kill_fury": "minecraft:blaze_rod", "crit_fury": "minecraft:fire_charge",
    "parry_power": "minecraft:diamond_axe", "revenge": "minecraft:red_banner", "dodge_power": "minecraft:wind_charge",
    "surge": "minecraft:glowstone", "ult_power": "minecraft:beacon", "summon_mark": "minecraft:name_tag",
    "reckless": "minecraft:tnt", "wild": "minecraft:heart_of_the_sea", "fifth_hit": "minecraft:bell",
    "echo": "minecraft:echo_shard", "cull": "minecraft:netherite_axe", "kill_burst": "minecraft:gunpowder",
    "plague": "minecraft:dragon_breath", "crit_mana": "minecraft:diamond", "crit_heal": "minecraft:melon_slice",
    "crit_cdr": "minecraft:recovery_compass", "kill_cdr": "minecraft:compass", "charge_on_kill": "minecraft:end_crystal",
    "cast_refund": "minecraft:ender_pearl", "heal_on_cast": "minecraft:glow_berries", "cast_ward": "minecraft:shulker_shell",
    "guard_fortify": "minecraft:iron_door", "guard_reflect": "minecraft:prismarine_shard",
    "parry_heal": "minecraft:honeycomb", "parry_mana": "minecraft:quartz", "summon_bond": "minecraft:lead",
    "summon_lifesteal": "minecraft:sculk_catalyst", "cheat_death": "minecraft:totem_of_undying",
    "barrier": "minecraft:tinted_glass",
}
for _el in ELEMENTS:
    NODE_ICON["spell_damage." + _el] = "isekai_mahou:essence_" + _el


def parse(spec):
    """'chave:valor,...' -> [(chave, valor)] (confere as chaves)."""
    out = []
    for part in spec.split(","):
        key, val = part.strip().split(":")
        if key not in ATTR:
            base = key.split(".")[0]
            ok = key in STATS or (base == "spell_damage" and key.split(".")[1] in ELEMENTS) \
                or (base in ("on_hit", "on_struck") and key.split(".")[1] in ON_HIT)
            if not ok:
                sys.exit("efeito desconhecido: " + key)
        out.append((key, float(val)))
    return out


def effects(spec):
    out = []
    for key, amount in parse(spec):
        if key in ATTR:
            attr, op = ATTR[key]
            out.append({"attribute": attr, "amount": amount, "op": op})
        else:
            out.append({"stat": key, "amount": amount})
            if key == "barrier":
                attr, op = ATTR["maxabs"]
                out.append({"attribute": attr, "amount": amount, "op": op})
    return out


def first_key(spec):
    return spec.split(",")[0].split(":")[0].strip()


def mastery_scale(rank):
    return (1.0 - MASTERY_CURVE ** rank) / (1.0 - MASTERY_CURVE)


# =====================================================================================================
#  As 16 classes. Ramo: (id, cor, PT, EN, efeito da Maestria, [11 nos: (id, PT, EN, efeitos)])  — ordem em SLOTS.
#  Especializacao: (id, cor, icone, PT, EN, descricao PT, descricao EN, "como joga" PT, EN, ASSINATURA,
#                   [4 nos da fileira exclusiva: (id, PT, EN, efeitos)])            — id = <classe>_<nome>
# =====================================================================================================
CLASSES = {}

CLASSES["mage"] = dict(
    branches=[
        ("study", "6C7BFF", "Estudo Arcano", "Arcane Study", "mana:4", [
            ("tome_reading", "Leitura Constante", "Constant Reading", "mana:8"),
            ("sharp_mind", "Mente Afiada", "Sharp Mind", "regen:0.02"),
            ("clean_formulas", "Fórmulas Limpas", "Clean Formulas", "sp:0.01"),
            ("thesis_defended", "Tese Defendida", "Defended Thesis", "cast_refund:0.05,mana:15"),
            ("deep_well", "Poço Profundo", "Deep Well", "mana:10,regen:0.01"),
            ("living_archive", "Arquivo Vivo", "Living Archive", "mana_on_kill:2,sp:0.006"),
            ("brimming_reserve", "Reserva Cheia", "Brimming Reserve", "high_mana_power:0.10"),
            ("quick_mind", "Mente Veloz", "Quick Mind", "kill_cdr:1.5"),
            ("margin_notes", "Notas de Margem", "Margin Notes", "spell_damage_all:0.01"),
            ("mind_library", "Biblioteca Mental", "Mind Library", "crit_mana:4,crit_chance:0.02"),
            ("arcane_echo", "Eco Arcano", "Arcane Echo", "echo:0.20"),
        ]),
        ("elements", "FF8A3D", "Elementalista", "Elementalist", "spell_damage_all:0.004", [
            ("trained_spark", "Faísca Treinada", "Trained Spark", "spell_damage.fire:0.015"),
            ("trained_tide", "Maré Treinada", "Trained Tide", "spell_damage.water:0.015"),
            ("trained_stone", "Pedra Treinada", "Trained Stone", "spell_damage.earth:0.015"),
            ("chain_reaction", "Reação em Cadeia", "Chain Reaction", "afflicted_damage:0.06"),
            ("trained_gale", "Vendaval Treinado", "Trained Gale", "spell_damage.wind:0.015"),
            ("convergence", "Convergência", "Convergence", "crit_chance:0.01,crit_damage:0.03"),
            ("glass_cannon", "Canhão de Vidro", "Glass Cannon", "reckless:0.14"),
            ("long_range", "Tiro de Longe", "Long Range", "far_damage:0.12"),
            ("light_and_shadow", "Luz e Sombra", "Light and Shadow", "spell_damage.light:0.012,spell_damage.shadow:0.012"),
            ("overload", "Sobrecarga", "Overload", "surge:0.02"),
            ("cataclysm", "Cataclismo", "Cataclysm", "kill_burst:0.20,ult_power:0.15"),
        ]),
        ("ward", "7FD6E8", "Barreira", "Warding", "hp:0.5", [
            ("reinforced_robes", "Manto Reforçado", "Reinforced Robes", "hp:1"),
            ("counterspell", "Contrafeitiço", "Counterspell", "magic_resist:0.015"),
            ("mana_skin", "Pele de Mana", "Mana Skin", "mana_shield:0.02"),
            ("runic_barrier", "Barreira Rúnica", "Runic Barrier", "barrier:3"),
            ("composure", "Compostura", "Composure", "kbres:0.03,armor:0.3"),
            ("arcane_aegis", "Égide Arcana", "Arcane Aegis", "second_wind:1.5,damage_resist:0.006"),
            ("casting_shield", "Escudo ao Conjurar", "Casting Shield", "cast_ward:0.12"),
            ("untouched_mantle", "Manto Intacto", "Untouched Mantle", "full_health_resist:0.12"),
            ("protective_circle", "Círculo de Proteção", "Protective Circle", "damage_resist:0.008"),
            ("lifeline", "Fio de Vida", "Lifeline", "heal_on_cast:1,low_health_resist:0.05"),
            ("time_anchor", "Âncora Temporal", "Time Anchor", "cheat_death:0.35"),
        ]),
    ],
    subclasses=[
        ("mage_archmage", "6C7BFF", "minecraft:enchanted_book", "Erudito Arcano", "Arcane Scholar",
         "O mago de biblioteca: uma reserva enorme de mana que nunca seca.",
         "The library mage: a huge mana pool that never runs dry.",
         "Mantenha a mana cheia: acima de 75%% suas magias batem mais forte, e parte delas sai pela metade do preço.",
         "Keep your mana full: above 75%% your spells hit harder, and some of them cost half.",
         "mana:120,regen:0.15,mana_on_kill:6,high_mana_power:0.12,cast_refund:0.15", [
             ("endless_notes", "Notas Infinitas", "Endless Notes", "mana:15"),
             ("speed_reading", "Leitura Dinâmica", "Speed Reading", "regen:0.03"),
             ("arcane_font", "Fonte Arcana", "Arcane Font", "high_mana_power:0.05,crit_mana:3"),
             ("omniscience", "Onisciência", "Omniscience", "echo:0.10,spell_damage_all:0.08"),
         ]),
        ("mage_battlemage", "FF6A4D", "minecraft:blaze_rod", "Mago de Batalha", "Battlemage",
         "Magia na linha de frente: bate mais forte e paga o dano recebido com mana.",
         "Magic on the front line: hits harder and pays for damage taken with mana.",
         "Lute de perto: a até 5 blocos suas magias batem mais forte, e cada conjuração cura você.",
         "Fight up close: within 5 blocks your spells hit harder, and every cast heals you.",
         "sp:0.08,mana_shield:0.15,armor:4,near_damage:0.15,heal_on_cast:1.5", [
             ("steel_and_spell", "Aço e Feitiço", "Steel and Spell", "near_damage:0.03"),
             ("arcane_cuirass", "Couraça Arcana", "Arcane Cuirass", "armor:1,hp:1"),
             ("mystic_blade", "Lâmina Mística", "Mystic Blade", "revenge:0.08,cast_ward:0.05"),
             ("eye_of_the_storm", "Olho do Furacão", "Eye of the Storm", "surge:0.03,low_health_resist:0.15"),
         ]),
    ])

CLASSES["witch"] = dict(
    branches=[
        ("hexes", "B45CFF", "Maldições", "Hexes", "all_damage:0.004", [
            ("evil_eye", "Mau-Olhado", "Evil Eye", "on_hit.weakness:0.03"),
            ("thorn_tongue", "Língua de Espinho", "Thorn Tongue", "on_hit.poison:0.03"),
            ("cold_hands", "Mãos Frias", "Cold Hands", "on_hit.slowness:0.03"),
            ("greedy_eye", "Olho Gordo", "Greedy Eye", "afflicted_damage:0.07"),
            ("spite", "Rancor", "Spite", "execute:0.03"),
            ("grand_hex", "Grande Praga", "Grand Hex", "spell_damage.shadow:0.015,crit_chance:0.006"),
            ("slow_rot", "Praga Lenta", "Slow Rot", "on_hit.wither:0.25"),
            ("bloodletting", "Sangria", "Bloodletting", "on_hit.bleed:0.25"),
            ("ill_omen", "Agouro", "Ill Omen", "afflicted_damage:0.015"),
            ("rag_doll", "Boneco de Pano", "Rag Doll", "combo:0.02,crit_fury:0.05"),
            ("epidemic", "Epidemia", "Epidemic", "plague:1,afflicted_damage:0.10"),
        ]),
        ("cauldron", "5FD08A", "Caldeirão", "Cauldron", "hp:0.5", [
            ("right_herb", "Erva Certa", "The Right Herb", "healing_received:0.02"),
            ("low_fire", "Fogo Brando", "Low Fire", "regen_health:0.2"),
            ("keep_stirring", "Mexer Sem Parar", "Keep Stirring", "regen:0.02"),
            ("thick_broth", "Caldo Grosso", "Thick Broth", "heal_on_cast:0.8,hp:2"),
            ("full_pantry", "Despensa Cheia", "Full Pantry", "mana:8"),
            ("house_elixir", "Elixir da Casa", "House Elixir", "heal_on_kill:0.4,hp:0.6"),
            ("bitter_tonic", "Tônico Amargo", "Bitter Tonic", "low_health_resist:0.15"),
            ("sweet_syrup", "Xarope Doce", "Sweet Syrup", "barrier:6"),
            ("rolling_boil", "Fervura", "Rolling Boil", "mana_on_kill:2"),
            ("secret_recipe", "Receita Secreta", "Secret Recipe", "cast_refund:0.05,healing_received:0.05"),
            ("elixir_of_life", "Elixir da Vida", "Elixir of Life", "cheat_death:0.4"),
        ]),
        ("night", "5A6BD8", "Noite", "Night", "mana:4", [
            ("moon_bath", "Banho de Lua", "Moon Bath", "night_power:0.02"),
            ("black_cat", "Gato Preto", "Black Cat", "luck:0.2,dodge:0.005"),
            ("swift_broom", "Vassoura Ligeira", "Swift Broom", "speed:0.01,fall:1"),
            ("cats_shadow", "Sombra do Gato", "Cat's Shadow", "dodge_power:0.10,dodge:0.02"),
            ("dark_tide", "Maré Escura", "Dark Tide", "spell_damage.water:0.015"),
            ("witching_hour", "Hora das Bruxas", "Witching Hour", "night_power:0.02,sp:0.006"),
            ("moons_daughter", "Filha da Lua", "Moon's Daughter", "night_power:0.12"),
            ("day_and_night", "Dia e Noite", "Day and Night", "all_damage:0.05"),
            ("low_flight", "Voo Rasante", "Low Flight", "run_power:0.015"),
            ("sabbath", "Sabá", "Sabbath", "kill_fury:0.04,mana_on_kill:3"),
            ("endless_midnight", "Meia-Noite Eterna", "Endless Midnight", "ult_power:0.25,charge_on_kill:2"),
        ]),
    ],
    subclasses=[
        ("witch_hexer", "B45CFF", "minecraft:fermented_spider_eye", "Bruxa das Pragas", "Hexweaver",
         "Cada golpe carrega uma praga: o inimigo enfraquece, apodrece e cai.",
         "Every hit carries a curse: the enemy weakens, rots and falls.",
         "Encha o inimigo de pragas: alvos amaldiçoados levam muito mais dano e, ao morrer, passam as pragas adiante.",
         "Pile curses on the enemy: cursed targets take far more damage and, when they die, pass the curses on.",
         "on_hit.weakness:0.25,on_hit.poison:0.25,execute:0.15,afflicted_damage:0.20,plague:0.5", [
             ("dirty_nails", "Unhas Sujas", "Dirty Nails", "on_hit.wither:0.05"),
             ("sharp_tongue", "Língua Ferina", "Sharp Tongue", "afflicted_damage:0.03"),
             ("bad_omen", "Mau Agouro", "Bad Omen", "execute:0.08,crit_fury:0.05"),
             ("family_curse", "Maldição de Família", "Family Curse", "cull:0.08,kill_cdr:1"),
         ]),
        ("witch_apothecary", "5FD08A", "minecraft:honey_bottle", "Boticária", "Apothecary",
         "Dona das poções: toda cura rende mais e o corpo se refaz sozinho.",
         "Mistress of potions: every heal goes further and the body mends itself.",
         "Conjure para ficar de pé: cada magia cura você e, fora de combate, uma barreira se refaz.",
         "Cast to stay standing: every spell heals you and, out of combat, a barrier rebuilds itself.",
         "healing_received:0.25,regen_health:1.5,heal_on_kill:2,heal_on_cast:2,barrier:8", [
             ("fresh_herbs", "Ervas Frescas", "Fresh Herbs", "heal_on_cast:0.4"),
             ("sturdy_flask", "Frasco Reforçado", "Sturdy Flask", "hp:2"),
             ("antidote", "Antídoto", "Antidote", "low_health_resist:0.08,full_health_power:0.05"),
             ("panacea", "Panaceia", "Panacea", "second_wind:6,cast_ward:0.15"),
         ]),
    ])

CLASSES["warlock"] = dict(
    branches=[
        ("pact", "D43C3C", "Pacto", "Pact", "all_damage:0.004", [
            ("blood_ink", "Tinta de Sangue", "Blood Ink", "lifesteal:0.01"),
            ("bargain", "Barganha", "Bargain", "sp:0.01"),
            ("price_of_pain", "Preço da Dor", "Price of Pain", "low_health_power:0.03"),
            ("blood_interest", "Juros de Sangue", "Blood Interest", "crit_heal:1.5,crit_chance:0.02"),
            ("debt_collected", "Dívida Cobrada", "Debt Collected", "execute:0.03"),
            ("true_name", "Nome Verdadeiro", "True Name", "crit_damage:0.04,lifesteal:0.005"),
            ("reckless_pact", "Pacto Temerário", "Reckless Pact", "reckless:0.15"),
            ("safety_clause", "Cláusula de Segurança", "Safety Clause", "low_health_resist:0.15"),
            ("fine_print", "Letras Miúdas", "Fine Print", "all_damage:0.008"),
            ("collection", "Cobrança", "Collection", "kill_fury:0.05"),
            ("pawned_soul", "Alma Penhorada", "Pawned Soul", "cull:0.10,heal_on_kill:2"),
        ]),
        ("black_flame", "FF7A2E", "Chama Negra", "Black Flame", "spell_damage_all:0.004", [
            ("pact_embers", "Brasas do Pacto", "Pact Embers", "spell_damage.fire:0.015"),
            ("umbra", "Umbra", "Umbra", "spell_damage.shadow:0.015"),
            ("searing_mark", "Marca em Brasa", "Searing Mark", "on_hit.wither:0.03"),
            ("living_ember", "Brasa Viva", "Living Ember", "on_hit.burn:0.15,afflicted_damage:0.05"),
            ("fearless_of_fire", "Sem Medo do Fogo", "Fearless of Fire", "fire_resist:0.04"),
            ("hellfire", "Fogo do Inferno", "Hellfire", "crit_chance:0.01,spell_damage.fire:0.008"),
            ("consuming_flame", "Chama que Consome", "Consuming Flame", "spell_damage.fire:0.12"),
            ("devouring_void", "Vazio que Engole", "Devouring Void", "spell_damage.shadow:0.12"),
            ("abyssal_soot", "Fuligem do Abismo", "Abyssal Soot", "spell_damage_all:0.01"),
            ("flare", "Labareda", "Flare", "surge:0.02"),
            ("flames_of_hell", "Chamas do Inferno", "Flames of Hell", "kill_burst:0.25,echo:0.10"),
        ]),
        ("tribute", "9A5BD0", "Tributo", "Tribute", "hp:0.5", [
            ("offering", "Oferenda", "Offering", "mana_on_kill:2"),
            ("thick_blood", "Sangue Grosso", "Thick Blood", "hp:1"),
            ("soul_tax", "Imposto de Alma", "Soul Tax", "heal_on_kill:0.4"),
            ("tithe", "Dízimo", "Tithe", "charge_on_kill:1,mana_on_kill:3"),
            ("patron_gift", "Dádiva do Patrono", "Patron's Gift", "mana:8,regen:0.01"),
            ("eternal_contract", "Contrato Eterno", "Eternal Contract", "second_wind:1.5,damage_resist:0.006"),
            ("patrons_favor", "Favor do Patrono", "Patron's Favor", "cast_refund:0.12"),
            ("patrons_shelter", "Abrigo do Patrono", "Patron's Shelter", "cast_ward:0.12"),
            ("sacrifice", "Sacrifício", "Sacrifice", "lifesteal:0.006"),
            ("patrons_voice", "Voz do Patrono", "Patron's Voice", "ult_power:0.10,boss_damage:0.04"),
            ("soul_contract", "Contrato de Alma", "Soul Contract", "cheat_death:0.35"),
        ]),
    ],
    subclasses=[
        ("warlock_fiend", "FF5A2E", "minecraft:blaze_powder", "Pactário Infernal", "Fiend Pactbound",
         "Pacto de fogo e sangue: quanto mais perto da morte, mais forte a chama.",
         "A pact of fire and blood: the closer to death, the stronger the flame.",
         "Lute no limite: com a vida baixa você bate mais e apanha menos, e cada golpe que leva fortalece o seu próximo.",
         "Fight on the edge: at low health you hit harder and take less, and every hit you take powers your next one.",
         "spell_damage.fire:0.12,lifesteal:0.08,low_health_power:0.15,low_health_resist:0.15,revenge:0.10", [
             ("boiling_blood", "Sangue Fervente", "Boiling Blood", "low_health_power:0.03"),
             ("scorched_hide", "Couro Chamuscado", "Scorched Hide", "fire_resist:0.08,hp:1"),
             ("infernal_fury", "Fúria Infernal", "Infernal Fury", "kill_fury:0.05,crit_heal:1"),
             ("ember_heart", "Coração de Brasa", "Ember Heart", "kill_burst:0.20,on_struck.burn:0.5"),
         ]),
        ("warlock_voidbound", "7A4BD8", "minecraft:ender_eye", "Arauto do Vazio", "Voidbound Herald",
         "Serve ao que mora no escuro: sombras mais fundas e magia alheia escorrendo.",
         "Serves what lives in the dark: deeper shadows, and foreign magic slides off.",
         "Mantenha distância: de longe suas magias batem mais forte e podem ecoar, acertando duas vezes.",
         "Keep your distance: from afar your spells hit harder and may echo, striking twice.",
         "spell_damage.shadow:0.12,mana_on_kill:8,magic_resist:0.15,echo:0.15,far_damage:0.10", [
             ("whispers", "Sussurros", "Whispers", "spell_damage.shadow:0.02"),
             ("empty_gaze", "Olhar Vazio", "Empty Gaze", "far_damage:0.03"),
             ("void_hunger", "Fome do Vazio", "Void Hunger", "crit_mana:4,crit_chance:0.02"),
             ("event_horizon", "Horizonte de Eventos", "Event Horizon", "echo:0.10,kill_cdr:1.5"),
         ]),
    ])

CLASSES["druid"] = dict(
    branches=[
        ("wild", "8A6B3D", "Selvagem", "Wild", "hp:0.5", [
            ("thick_bark", "Casca Grossa", "Thick Bark", "armor:0.4"),
            ("firm_roots", "Raízes Firmes", "Firm Roots", "kbres:0.03"),
            ("living_sap", "Seiva Viva", "Living Sap", "regen_health:0.2"),
            ("oakskin", "Casca de Carvalho", "Oakskin", "full_health_resist:0.06,hp:2"),
            ("beast_heart", "Coração de Fera", "Beast Heart", "hp:1"),
            ("elder_trunk", "Tronco Ancião", "Elder Trunk", "damage_resist:0.008,thorns:0.02"),
            ("bramble", "Espinheiro", "Bramble", "on_struck.poison:0.6"),
            ("deep_root", "Raiz Profunda", "Deep Root", "guard_fortify:0.15"),
            ("moss", "Musgo", "Moss", "healing_received:0.02"),
            ("cornered_beast", "Fera Acuada", "Cornered Beast", "low_health_resist:0.07,revenge:0.06"),
            ("mother_tree", "Árvore-Mãe", "Mother Tree", "cheat_death:0.4,regen_health:1"),
        ]),
        ("grove", "3FB25A", "Bosque", "Grove", "summon_damage:0.006", [
            ("stone_hand", "Mão de Pedra", "Stone Hand", "spell_damage.earth:0.015"),
            ("wellspring", "Nascente", "Wellspring", "spell_damage.water:0.015"),
            ("pack", "Matilha", "Pack", "summon_damage:0.02"),
            ("call_of_the_woods", "Chamado da Mata", "Call of the Woods", "summon_mark:0.08"),
            ("pollen", "Pólen", "Pollen", "on_hit.slowness:0.03"),
            ("grove_heart", "Coração do Bosque", "Heart of the Grove", "summon_damage:0.02,sp:0.006"),
            ("feral_pack", "Matilha Feroz", "Feral Pack", "summon_damage:0.12"),
            ("hand_of_the_earth", "Mão da Terra", "Hand of the Earth", "spell_damage.earth:0.12"),
            ("seeds", "Sementes", "Seeds", "spell_damage_all:0.01"),
            ("green_bond", "Laço Verde", "Green Bond", "summon_bond:0.05,summon_lifesteal:0.03"),
            ("wrath_of_the_forest", "Fúria da Floresta", "Wrath of the Forest", "kill_fury:0.08,summon_damage:0.10"),
        ]),
        ("cycles", "E8C84A", "Ciclos", "Cycles", "mana:4", [
            ("sunleaf", "Folha ao Sol", "Sunleaf", "day_power:0.02"),
            ("dew", "Orvalho", "Dew", "healing_received:0.02"),
            ("seasons", "Estações", "Seasons", "regen:0.02"),
            ("spring", "Primavera", "Spring", "heal_on_cast:0.8,mana:10"),
            ("harvest", "Colheita", "Harvest", "mana_on_kill:2"),
            ("equinox", "Equinócio", "Equinox", "day_power:0.012,night_power:0.012"),
            ("summer_solstice", "Solstício de Verão", "Summer Solstice", "day_power:0.12"),
            ("winter_solstice", "Solstício de Inverno", "Winter Solstice", "night_power:0.12"),
            ("gentle_rain", "Chuva Mansa", "Gentle Rain", "mana:8"),
            ("autumn", "Outono", "Autumn", "cast_refund:0.05,mana_on_kill:2"),
            ("eternal_cycle", "Ciclo Eterno", "Eternal Cycle", "kill_cdr:2,charge_on_kill:2"),
        ]),
    ],
    subclasses=[
        ("druid_warden", "8A6B3D", "minecraft:oak_log", "Guardião do Bosque", "Grove Warden",
         "Vira a própria floresta: casca dura, espinhos e um corpo que não para de brotar.",
         "Becomes the forest itself: hard bark, thorns and a body that never stops growing back.",
         "Seja a muralha: quem bate em você fica lento, e o que você bloqueia volta para o atacante.",
         "Be the wall: whoever hits you is slowed, and what you block goes back to the attacker.",
         "hp:8,thorns:0.25,regen_health:1.5,guard_reflect:0.5,on_struck.slowness:0.5,full_health_resist:0.10", [
             ("trunk_rings", "Anéis do Tronco", "Trunk Rings", "hp:2"),
             ("resin", "Resina", "Resin", "armor:1"),
             ("living_roots", "Raízes Vivas", "Living Roots", "guard_fortify:0.08,parry_heal:2"),
             ("heart_of_oak", "Coração de Carvalho", "Heart of Oak", "boss_resist:0.15,barrier:8"),
         ]),
        ("druid_beastcaller", "3FB25A", "minecraft:bone", "Senhor das Feras", "Beastcaller",
         "Corre com a matilha: as feras batem muito mais e cada caça alimenta o grupo.",
         "Runs with the pack: beasts hit far harder and every hunt feeds the group.",
         "Aponte o alvo e a matilha faz o resto: o que você acerta fica marcado, e o dano das feras cura você.",
         "Point at the target and the pack does the rest: what you hit is marked, and the beasts' damage heals you.",
         "summon_damage:0.20,speed:0.08,heal_on_kill:2,summon_mark:0.20,summon_lifesteal:0.10", [
             ("fangs", "Presas", "Fangs", "summon_damage:0.03"),
             ("keen_nose", "Faro", "Keen Nose", "speed:0.01,crit_chance:0.01"),
             ("the_hunt", "Caçada", "The Hunt", "summon_mark:0.06,kill_speed:1.5"),
             ("alpha_of_the_woods", "Alfa da Mata", "Alpha of the Woods", "summon_bond:0.15,kill_fury:0.06"),
         ]),
    ])

CLASSES["necromancer"] = dict(
    branches=[
        ("bones", "D8D2B8", "Ossos", "Bones", "summon_damage:0.006", [
            ("bone_craft", "Ofício dos Ossos", "Bonecraft", "summon_damage:0.02"),
            ("marrow", "Tutano", "Marrow", "hp:1"),
            ("legion", "Legião", "Legion", "summon_damage:0.02"),
            ("attack_order", "Ordem de Ataque", "Attack Order", "summon_mark:0.08"),
            ("grave_armor", "Armadura de Cova", "Grave Armor", "armor:0.4"),
            ("bone_lord", "Senhor dos Ossos", "Bone Lord", "summon_damage:0.02,damage_resist:0.006"),
            ("horde", "Horda", "Horde", "summon_damage:0.12"),
            ("bone_guard", "Guarda de Ossos", "Bone Guard", "summon_bond:0.12"),
            ("sharpened_bones", "Ossos Afiados", "Sharpened Bones", "boss_damage:0.012"),
            ("tribute_of_the_dead", "Tributo dos Mortos", "Tribute of the Dead", "summon_lifesteal:0.04"),
            ("arise", "Erga-se", "Arise", "kill_fury:0.08,charge_on_kill:3"),
        ]),
        ("death", "4A8A8A", "Morte", "Death", "all_damage:0.004", [
            ("tomb_chill", "Frio da Tumba", "Tomb Chill", "spell_damage.shadow:0.015"),
            ("rot", "Podridão", "Rot", "on_hit.wither:0.03"),
            ("reap", "Ceifar", "Reap", "execute:0.03"),
            ("chilling_touch", "Toque Gélido", "Chilling Touch", "on_hit.chill:0.15,afflicted_damage:0.05"),
            ("dusk", "Crepúsculo", "Dusk", "night_power:0.02"),
            ("death_mark", "Marca da Morte", "Death Mark", "crit_chance:0.01,execute:0.02"),
            ("swift_reaping", "Ceifa Rápida", "Swift Reaping", "kill_cdr:1.5"),
            ("mercy_stroke", "Golpe de Misericórdia", "Mercy Stroke", "execute:0.15"),
            ("shroud", "Mortalha", "Shroud", "spell_damage_all:0.01"),
            ("omen", "Presságio", "Omen", "crit_fury:0.06,crit_mana:3"),
            ("deaths_scythe", "Foice da Morte", "Death's Scythe", "cull:0.10,kill_burst:0.15"),
        ]),
        ("beyond", "7C5BD0", "Além", "Beyond", "hp:0.5", [
            ("soul_sip", "Gole de Alma", "Soul Sip", "lifesteal:0.01"),
            ("soul_harvest", "Colheita de Almas", "Soul Harvest", "mana_on_kill:2"),
            ("cold_blood", "Sangue Frio", "Cold Blood", "magic_resist:0.015"),
            ("half_dead", "Meio Morto", "Half Dead", "low_health_resist:0.07,hp:2"),
            ("phylactery", "Filactério", "Phylactery", "mana:8,regen:0.01"),
            ("undying", "Imorredouro", "Undying", "second_wind:2,heal_on_kill:0.3"),
            ("tomb_skin", "Pele de Túmulo", "Tomb Skin", "full_health_resist:0.12"),
            ("soul_feast", "Banquete de Almas", "Soul Feast", "heal_on_kill:2.5"),
            ("veil", "Véu", "Veil", "dodge:0.008"),
            ("beyond_the_grave", "Além-Túmulo", "Beyond the Grave", "ult_power:0.10,cast_ward:0.05"),
            ("true_phylactery", "Filactério Verdadeiro", "True Phylactery", "cheat_death:0.5"),
        ]),
    ],
    subclasses=[
        ("necromancer_lichborn", "4AC8C8", "minecraft:wither_skeleton_skull", "Herdeiro do Lich", "Lichborn",
         "Troca a carne por poder: sombras mais fortes e uma morte que demora a pegar.",
         "Trades flesh for power: stronger shadows and a death that is slow to take hold.",
         "O mago dos mortos: com a mana cheia suas magias batem mais forte e podem ecoar, acertando duas vezes.",
         "The mage of the dead: with full mana your spells hit harder and may echo, striking twice.",
         "spell_damage.shadow:0.12,second_wind:8,mana:100,echo:0.15,high_mana_power:0.12", [
             ("eternal_cold", "Frio Eterno", "Eternal Cold", "spell_damage.shadow:0.02"),
             ("kept_soul", "Alma Guardada", "Kept Soul", "mana:15"),
             ("gaze_of_death", "Olhar da Morte", "Gaze of Death", "afflicted_damage:0.05,on_hit.chill:0.10"),
             ("lich_crown", "Coroa do Lich", "Lich Crown", "surge:0.03,cast_refund:0.10"),
         ]),
        ("necromancer_gravelord", "D8D2B8", "minecraft:skeleton_skull", "Senhor das Covas", "Gravelord",
         "General dos mortos: o exército bate por ele e cada baixa do inimigo o sustenta.",
         "General of the dead: the army hits for him and every enemy loss sustains him.",
         "O general: enquanto o exército luta você apanha menos, e o dano dele vira vida para você.",
         "The general: while the army fights you take less damage, and its damage becomes your health.",
         "summon_damage:0.25,heal_on_kill:2,hp:6,summon_bond:0.15,summon_lifesteal:0.10", [
             ("recruits", "Recrutas", "Recruits", "summon_damage:0.03"),
             ("war_banner", "Estandarte", "War Banner", "hp:2"),
             ("funeral_march", "Marcha Fúnebre", "Funeral March", "summon_mark:0.08,kill_speed:1.5"),
             ("shadow_monarch", "Monarca das Sombras", "Shadow Monarch", "ult_power:0.20,kill_fury:0.06"),
         ]),
    ])

CLASSES["smith"] = dict(
    branches=[
        ("forge", "FF7A2E", "Forja", "Forge", "melee_damage:0.005", [
            ("hot_iron", "Ferro Quente", "Hot Iron", "on_hit.burn:0.03"),
            ("bellows", "Fole", "Bellows", "spell_damage.fire:0.015"),
            ("soot", "Fuligem", "Soot", "fire_resist:0.05"),
            ("red_hot_iron", "Ferro em Brasa", "Red-Hot Iron", "afflicted_damage:0.07"),
            ("tempering", "Têmpera", "Tempering", "atk:0.015"),
            ("white_heat", "Calor Branco", "White Heat", "crit_damage:0.04,on_hit.burn:0.02"),
            ("sledge_blow", "Golpe de Malho", "Sledge Blow", "fifth_hit:0.5"),
            ("fine_edge", "Fio Fino", "Fine Edge", "crit_chance:0.08"),
            ("sparks", "Faíscas", "Sparks", "melee_damage:0.012"),
            ("living_forge", "Forja Viva", "Living Forge", "kill_fury:0.05"),
            ("heart_of_the_forge", "Coração da Forja", "Heart of the Forge", "kill_burst:0.25,on_struck.burn:0.5"),
        ]),
        ("anvil", "A8B4C0", "Bigorna", "Anvil", "hp:0.5", [
            ("heavy_arm", "Braço Pesado", "Heavy Arm", "atk:0.015"),
            ("thick_plate", "Chapa Grossa", "Thick Plate", "armor:0.4"),
            ("rivets", "Rebites", "Rivets", "tough:0.3"),
            ("folded_steel", "Aço Dobrado", "Folded Steel", "guard_fortify:0.06,armor:1"),
            ("planted_feet", "Pés Plantados", "Planted Feet", "kbres:0.03"),
            ("masterwork", "Obra-Prima", "Masterwork", "damage_resist:0.008,atk:0.006"),
            ("anvil_shield", "Escudo-Bigorna", "Anvil Shield", "guard_reflect:0.6"),
            ("counter_hammer", "Contra-Martelo", "Counter-Hammer", "parry_power:0.5"),
            ("apron_leather", "Couro de Avental", "Apron Leather", "melee_resist:0.01"),
            ("dead_weight", "Peso Morto", "Dead Weight", "full_health_resist:0.06,revenge:0.05"),
            ("unbreakable", "Inquebrável", "Unbreakable", "cheat_death:0.35,boss_resist:0.10"),
        ]),
        ("craft", "E8C84A", "Ofício", "Craft", "mana:4", [
            ("pick_hand", "Mão de Picareta", "Pick Hand", "dig:0.02"),
            ("ore_sense", "Faro de Minério", "Ore Sense", "luck:0.2"),
            ("forge_stamina", "Fôlego de Forja", "Forge Stamina", "mana:8"),
            ("calloused_hands", "Mãos Calejadas", "Calloused Hands", "mana_on_hit:1,hp:2"),
            ("second_shift", "Segundo Turno", "Second Shift", "regen:0.02"),
            ("guild_seal", "Selo da Guilda", "Guild Seal", "mana_on_hit:0.3,hp:0.6"),
            ("workshop_rhythm", "Ritmo de Oficina", "Workshop Rhythm", "combo:0.04"),
            ("overtime", "Hora Extra", "Overtime", "kill_cdr:1.5"),
            ("good_coal", "Carvão Bom", "Good Coal", "mana_on_kill:2"),
            ("special_order", "Encomenda Especial", "Special Order", "boss_damage:0.05,crit_mana:3"),
            ("master_artisan", "Mestre Artesão", "Master Artisan", "ult_power:0.20,charge_on_kill:2"),
        ]),
    ],
    subclasses=[
        ("smith_weaponmaster", "FF7A2E", "minecraft:netherite_sword", "Mestre de Armas", "Weaponmaster",
         "Forja para matar: cada arma que faz corta mais fundo e acerta onde dói.",
         "Forges to kill: every weapon he makes cuts deeper and lands where it hurts.",
         "Bata sem parar no mesmo alvo: cada golpe seguido dói mais e todo 5º golpe é um crítico pesado.",
         "Keep hitting the same target: each hit in a row hurts more and every 5th hit is a heavy critical.",
         "atk:0.10,crit_chance:0.08,crit_damage:0.20,fifth_hit:0.5,combo:0.03", [
             ("keen_edge", "Gume", "Keen Edge", "melee_damage:0.03"),
             ("balance", "Balanço", "Balance", "aspd:0.02"),
             ("clean_cut", "Corte Limpo", "Clean Cut", "crit_fury:0.06,crit_heal:1"),
             ("legendary_blade", "Lâmina Lendária", "Legendary Blade", "boss_damage:0.12,crit_cdr:1"),
         ]),
        ("smith_ironwall", "A8B4C0", "minecraft:netherite_chestplate", "Muralha de Ferro", "Ironwall",
         "Forja para durar: uma parede ambulante que devolve o golpe a quem bate.",
         "Forges to last: a walking wall that returns the blow to whoever strikes.",
         "Bloqueie e apare: a guarda devolve o golpe ao atacante e deixa você ainda mais duro por alguns segundos.",
         "Block and parry: your guard returns the blow to the attacker and makes you even tougher for a few seconds.",
         "armor:5,tough:3,thorns:0.25,guard_reflect:0.6,guard_fortify:0.15", [
             ("extra_plate", "Chapa Extra", "Extra Plate", "armor:1"),
             ("master_rivet", "Rebite Mestre", "Master Rivet", "hp:2"),
             ("rampart", "Muralha", "Rampart", "parry_heal:3,parry_power:0.25"),
             ("walking_fortress", "Fortaleza Ambulante", "Walking Fortress", "boss_resist:0.15,on_struck.weakness:0.5"),
         ]),
    ])

CLASSES["rogue"] = dict(
    branches=[
        ("shadows", "8E5BD6", "Sombras", "Shadows", "crit_damage:0.006", [
            ("soft_step", "Passo Macio", "Soft Step", "sneak:0.03"),
            ("backstab", "Punhalada", "Backstab", "backstab:0.04"),
            ("keen_eye", "Olho Vivo", "Keen Eye", "crit_chance:0.01"),
            ("sucker_punch", "Golpe Surpresa", "Sucker Punch", "first_strike:0.08,crit_mana:3"),
            ("ambush", "Emboscada", "Ambush", "first_strike:0.04"),
            ("assassinate", "Assassinato", "Assassinate", "crit_damage:0.04,backstab:0.02"),
            ("hidden_blade", "Lâmina Oculta", "Hidden Blade", "aim_power:0.12"),
            ("hit_and_run", "Corre e Corta", "Hit and Run", "run_power:0.12"),
            ("vital_point", "Ponto Vital", "Vital Point", "boss_damage:0.012"),
            ("killer_shadow", "Sombra Assassina", "Killer Shadow", "crit_fury:0.07"),
            ("death_sentence", "Sentença", "Death Sentence", "cull:0.10,crit_cdr:1"),
        ]),
        ("agility", "5FD0C0", "Agilidade", "Agility", "hp:0.5", [
            ("light_feet", "Pés Leves", "Light Feet", "speed:0.01"),
            ("tumble", "Rolamento", "Tumble", "fall:1,fall_resist:0.05"),
            ("slippery", "Escorregadio", "Slippery", "dodge:0.01"),
            ("riposte", "Contra-Ataque", "Riposte", "dodge_power:0.12,dodge:0.01"),
            ("quick_hands", "Mãos Rápidas", "Quick Hands", "aspd:0.015"),
            ("ghost", "Fantasma", "Ghost", "dodge:0.008,kill_speed:0.6"),
            ("blade_dance", "Dança das Lâminas", "Blade Dance", "combo:0.04"),
            ("smoke", "Fumaça", "Smoke", "full_health_resist:0.12"),
            ("short_breath", "Fôlego Curto", "Short Breath", "mana_on_hit:0.4"),
            ("reflexes", "Reflexos", "Reflexes", "parry_power:0.2,parry_mana:6"),
            ("untouchable", "Intocável", "Untouchable", "dodge:0.05,cheat_death:0.25"),
        ]),
        ("dirty", "7FB24A", "Golpes Sujos", "Dirty Tricks", "all_damage:0.004", [
            ("blade_venom", "Veneno na Lâmina", "Blade Venom", "on_hit.poison:0.03"),
            ("light_fingers", "Mão Leve", "Light Fingers", "luck:0.2"),
            ("cheap_shot", "Golpe Baixo", "Cheap Shot", "on_hit.slowness:0.03"),
            ("open_wound", "Ferida Aberta", "Open Wound", "on_hit.bleed:0.15,afflicted_damage:0.05"),
            ("finisher", "Finalizador", "Finisher", "execute:0.03"),
            ("cutthroat", "Corta-Gargantas", "Cutthroat", "mana_on_kill:2,lifesteal:0.006"),
            ("strong_venom", "Veneno Forte", "Strong Venom", "afflicted_damage:0.12"),
            ("sticky_fingers", "Mão Leve Demais", "Sticky Fingers", "charge_on_kill:2"),
            ("sand_in_the_eyes", "Areia nos Olhos", "Sand in the Eyes", "on_hit.weakness:0.03"),
            ("no_honor", "Sem Honra", "No Honor", "kill_fury:0.05"),
            ("underworld_king", "Rei do Submundo", "King of the Underworld", "plague:1,kill_cdr:2"),
        ]),
    ],
    subclasses=[
        ("rogue_assassin", "8E5BD6", "minecraft:iron_sword", "Assassino", "Assassin",
         "Um golpe, pelas costas, no alvo que ainda não sabe que a luta começou.",
         "One strike, from behind, on a target that doesn't yet know the fight has started.",
         "Abra a luta pelas costas e termine rápido: o primeiro golpe é enorme e inimigos comuns quase mortos caem na hora.",
         "Open from behind and finish fast: the first strike is huge and common enemies near death drop at once.",
         "backstab:0.30,first_strike:0.25,crit_damage:0.25,cull:0.06,crit_cdr:1", [
             ("shadow_step", "Passo Sombrio", "Shadow Step", "backstab:0.04"),
             ("poisoned_edge", "Fio Envenenado", "Poisoned Edge", "crit_chance:0.015"),
             ("mark_for_death", "Marcado para Morrer", "Marked for Death", "boss_damage:0.06,kill_fury:0.04"),
             ("one_strike", "Um Golpe", "One Strike", "fifth_hit:0.6,aim_power:0.10"),
         ]),
        ("rogue_trickster", "7FB24A", "minecraft:rabbit_foot", "Trapaceiro", "Trickster",
         "Nunca está onde o golpe cai: sorte, veneno e pernas rápidas.",
         "Never where the blow lands: luck, poison and quick legs.",
         "Esquive e revide: cada esquiva arma um contra-golpe, e seus golpes saem imprevisíveis — fracos ou muito fortes.",
         "Dodge and strike back: every dodge primes a counter, and your hits land unpredictably — weak or very strong.",
         "dodge:0.10,luck:2,on_hit.poison:0.20,kill_speed:4,dodge_power:0.25,wild:0.10", [
             ("dirty_trick", "Truque Sujo", "Dirty Trick", "on_hit.weakness:0.05"),
             ("rabbits_foot", "Pé de Coelho", "Rabbit's Foot", "dodge:0.01"),
             ("torn_pocket", "Bolso Furado", "Torn Pocket", "charge_on_kill:1.5,mana_on_kill:3"),
             ("the_big_con", "O Grande Golpe", "The Big Con", "lucky:0.05,revenge:0.12"),
         ]),
    ])

CLASSES["knight"] = dict(
    branches=[
        ("bulwark", "9AA8C0", "Baluarte", "Bulwark", "hp:0.5", [
            ("shield_arm", "Braço do Escudo", "Shield Arm", "armor:0.4"),
            ("iron_vigor", "Vigor de Ferro", "Iron Vigor", "hp:1"),
            ("brace", "Firmar", "Brace", "kbres:0.03"),
            ("raised_shield", "Escudo Erguido", "Raised Shield", "guard_fortify:0.07,parry_heal:1.5"),
            ("guard_drill", "Treino de Guarda", "Guard Drill", "melee_resist:0.015"),
            ("bastion", "Bastião", "Bastion", "damage_resist:0.008,tough:0.3"),
            ("rampart", "Muralha", "Rampart", "guard_reflect:0.6"),
            ("counterblow", "Contra-Golpe", "Counterblow", "parry_power:0.6"),
            ("closed_helm", "Elmo Fechado", "Closed Helm", "projectile_resist:0.015"),
            ("stand_firm", "Firme e Forte", "Stand Firm", "full_health_resist:0.06,boss_resist:0.04"),
            ("eternal_bastion", "Bastião Eterno", "Eternal Bastion", "cheat_death:0.4,on_struck.weakness:0.5"),
        ]),
        ("runes", "7FA8FF", "Runas", "Runes", "all_damage:0.004", [
            ("etching", "Gravura", "Etching", "spell_damage.light:0.015"),
            ("runic_edge", "Fio Rúnico", "Runic Edge", "atk:0.015"),
            ("inscription", "Inscrição", "Inscription", "sp:0.01"),
            ("marking_rune", "Runa de Marca", "Marking Rune", "combo:0.02,melee_damage:0.03"),
            ("rune_reserve", "Reserva Rúnica", "Rune Reserve", "mana:8"),
            ("living_rune", "Runa Viva", "Living Rune", "crit_chance:0.01,spell_damage.light:0.008"),
            ("runic_blade", "Lâmina Rúnica", "Runic Blade", "melee_damage:0.12"),
            ("runic_word", "Verbo Rúnico", "Runic Word", "spell_damage_all:0.12"),
            ("rune_of_breath", "Runa de Fôlego", "Rune of Breath", "mana_on_hit:0.4"),
            ("twin_rune", "Runa Dupla", "Twin Rune", "echo:0.05,crit_mana:3"),
            ("legendary_sword", "Espada Lendária", "Legendary Sword", "fifth_hit:0.6,ult_power:0.15"),
        ]),
        ("oath", "FFD86B", "Juramento", "Oath", "mana:4", [
            ("dawn_vow", "Voto da Aurora", "Vow of Dawn", "day_power:0.02"),
            ("purge", "Purgar", "Purge", "undead_damage:0.03"),
            ("mercy", "Misericórdia", "Mercy", "healing_received:0.02"),
            ("vow_of_courage", "Voto de Coragem", "Vow of Courage", "boss_damage:0.05,boss_resist:0.03"),
            ("duty", "Dever", "Duty", "mana_on_hit:0.4"),
            ("last_line", "Última Linha", "Last Line", "second_wind:2,low_health_power:0.02"),
            ("vow_of_wrath", "Voto de Ira", "Vow of Wrath", "revenge:0.15"),
            ("vow_of_faith", "Voto de Fé", "Vow of Faith", "heal_on_cast:2"),
            ("vigil", "Vigília", "Vigil", "regen_health:0.2"),
            ("given_word", "Palavra Dada", "Given Word", "kill_fury:0.04,heal_on_kill:1"),
            ("final_oath", "Juramento Final", "Final Oath", "low_health_resist:0.20,low_health_power:0.20"),
        ]),
    ],
    subclasses=[
        ("knight_paladin", "FFD86B", "minecraft:golden_sword", "Paladino da Aurora", "Dawn Paladin",
         "A espada da luz: mortos-vivos queimam e as curas rendem mais nele.",
         "The sword of light: the undead burn and healing works better on him.",
         "A luz cura quem luta: cada técnica usada e cada golpe aparado devolvem vida.",
         "The light heals those who fight: every technique used and every parried blow gives health back.",
         "spell_damage.light:0.12,undead_damage:0.25,healing_received:0.20,heal_on_cast:2,parry_heal:4", [
             ("morning_light", "Luz da Manhã", "Morning Light", "spell_damage.light:0.02"),
             ("blessing", "Bênção", "Blessing", "healing_received:0.03"),
             ("judgment", "Julgamento", "Judgment", "execute:0.08,crit_heal:1"),
             ("daybreak", "Aurora", "Daybreak", "cast_ward:0.15,ult_power:0.20"),
         ]),
        ("knight_vanguard", "9AA8C0", "minecraft:shield", "Vanguarda Rúnica", "Runic Vanguard",
         "O primeiro a entrar e o último a cair: armadura, vida e guarda de sobra.",
         "First in, last to fall: armor, health and guard to spare.",
         "Segure a linha: aparar arma um contra-golpe pesado, e cada bloqueio endurece a sua guarda.",
         "Hold the line: a parry primes a heavy counterblow, and every block hardens your guard.",
         "armor:4,hp:8,melee_resist:0.12,parry_power:0.6,guard_fortify:0.15", [
             ("front_line", "Linha de Frente", "Front Line", "hp:2"),
             ("heavy_shield", "Escudo Pesado", "Heavy Shield", "armor:1"),
             ("charge", "Investida", "Charge", "near_damage:0.05,revenge:0.06"),
             ("last_wall", "Última Muralha", "Last Wall", "guard_reflect:0.6,boss_resist:0.12"),
         ]),
    ])

CLASSES["enchanter"] = dict(
    branches=[
        ("charms", "5FE3C0", "Encantos", "Charms", "mana:4", [
            ("living_ink", "Tinta Viva", "Living Ink", "sp:0.01"),
            ("weave", "Trama", "Weave", "regen:0.02"),
            ("reserve", "Reserva", "Reserve", "mana:8"),
            ("double_seal", "Selo Duplo", "Double Seal", "surge:0.015,mana:10"),
            ("resonance", "Ressonância", "Resonance", "spell_damage.light:0.015"),
            ("greater_charm", "Encanto Maior", "Greater Charm", "sp:0.008,mana_on_kill:1.5"),
            ("sharp_charm", "Encanto Afiado", "Sharp Charm", "spell_damage_all:0.10"),
            ("guardian_charm", "Encanto Guardião", "Guardian Charm", "cast_ward:0.12"),
            ("silver_thread", "Fio de Prata", "Silver Thread", "crit_chance:0.008"),
            ("living_weave", "Trama Viva", "Living Weave", "cast_refund:0.05,heal_on_cast:0.8"),
            ("magnum_opus", "Grande Obra", "Magnum Opus", "ult_power:0.25,charge_on_kill:2"),
        ]),
        ("antimagic", "9A9AB0", "Anti-Magia", "Anti-Magic", "hp:0.5", [
            ("null_skin", "Pele Nula", "Null Skin", "magic_resist:0.02"),
            ("dispel", "Dissipar", "Dispel", "mana_shield:0.02"),
            ("grounding", "Aterrar", "Grounding", "kbres:0.03"),
            ("sealed_body", "Corpo Fechado", "Sealed Body", "full_health_resist:0.06,hp:2"),
            ("quiet_mind", "Mente Quieta", "Quiet Mind", "damage_resist:0.01"),
            ("null_field", "Campo Nulo", "Null Field", "magic_resist:0.015,second_wind:1.5"),
            ("return_to_sender", "Devolver ao Remetente", "Return to Sender", "guard_reflect:0.6"),
            ("ignore_pain", "Ignorar a Dor", "Ignore Pain", "low_health_resist:0.15"),
            ("silence", "Silêncio", "Silence", "boss_resist:0.01"),
            ("nullify", "Anular", "Nullify", "guard_fortify:0.07,parry_mana:8"),
            ("dead_zone", "Zona Morta", "Dead Zone", "cheat_death:0.35,on_struck.weakness:0.5"),
        ]),
        ("gauntlets", "E8A84A", "Punhos", "Gauntlets", "melee_damage:0.005", [
            ("iron_knuckles", "Nós de Ferro", "Iron Knuckles", "atk:0.015"),
            ("tempo", "Ritmo", "Tempo", "aspd:0.015"),
            ("footwork", "Jogo de Pés", "Footwork", "speed:0.01"),
            ("one_two", "Um-Dois", "One-Two", "combo:0.025"),
            ("tailwind", "Vento a Favor", "Tailwind", "spell_damage.wind:0.015"),
            ("iron_body", "Corpo de Ferro", "Iron Body", "crit_chance:0.01,hp:0.6"),
            ("iron_fist", "Punho de Ferro", "Iron Fist", "fifth_hit:0.5"),
            ("thousand_punches", "Mil Socos", "Thousand Punches", "combo:0.04"),
            ("warm_up", "Aquecimento", "Warm-Up", "melee_damage:0.012"),
            ("open_gate", "Portão Aberto", "Open Gate", "kill_fury:0.05,crit_fury:0.04"),
            ("eighth_gate", "Oitavo Portão", "Eighth Gate", "low_health_power:0.25,reckless:0.10"),
        ]),
    ],
    subclasses=[
        ("enchanter_nullifier", "9A9AB0", "minecraft:ender_eye", "Anulador", "Nullifier",
         "A Anti-Magia em pessoa: feitiços batem nele e morrem.",
         "Anti-Magic in person: spells hit him and die.",
         "O anti-mago: magia mal arranha você, chefes batem mais fraco, e o que você bloqueia volta para quem atacou.",
         "The anti-mage: magic barely scratches you, bosses hit softer, and what you block goes back to the attacker.",
         "magic_resist:0.25,mana_shield:0.15,second_wind:6,guard_reflect:0.6,boss_resist:0.10", [
             ("vacuum", "Vácuo", "Vacuum", "magic_resist:0.02"),
             ("dead_skin", "Pele Morta", "Dead Skin", "hp:2"),
             ("counter_spell", "Contra-Feitiço", "Counter-Spell", "parry_power:0.3,parry_mana:8"),
             ("absolute_zero", "Zero Absoluto", "Absolute Zero", "cast_ward:0.15,on_struck.chill:0.6"),
         ]),
        ("enchanter_heavenly", "E8A84A", "minecraft:golden_chestplate", "Corpo Celestial", "Heavenly Body",
         "A Restrição Celestial levada ao limite: menos magia, um corpo absurdo.",
         "The Heavenly Restriction pushed to the limit: less magic, an absurd body.",
         "Só o corpo: cole no inimigo e não pare de bater — de perto e em sequência, cada soco dói mais.",
         "Body only: stick to the enemy and never stop hitting — up close and in a row, each punch hurts more.",
         "atk:0.12,aspd:0.10,speed:0.08,combo:0.04,near_damage:0.12", [
             ("muscle", "Músculo", "Muscle", "melee_damage:0.03"),
             ("burst_step", "Explosão", "Burst Step", "speed:0.01,aspd:0.01"),
             ("instinct", "Instinto", "Instinct", "dodge_power:0.12,dodge:0.015"),
             ("perfect_body", "Corpo Perfeito", "Perfect Body", "fifth_hit:0.6,low_health_resist:0.12"),
         ]),
    ])

CLASSES["shinigami"] = dict(
    branches=[
        ("zanjutsu", "D9483B", "Zanjutsu", "Zanjutsu", "melee_damage:0.005", [
            ("kata", "Kata", "Kata", "atk:0.015"),
            ("iai", "Iai", "Iai", "first_strike:0.04"),
            ("soul_edge", "Fio da Alma", "Soul Edge", "crit_chance:0.01"),
            ("twin_cut", "Corte Duplo", "Twin Cut", "combo:0.025"),
            ("resolve", "Determinação", "Resolve", "low_health_power:0.03"),
            ("soul_cut", "Corte da Alma", "Soul Cut", "crit_damage:0.04,atk:0.006"),
            ("lightning_draw", "Saque Fulminante", "Lightning Draw", "first_strike:0.20"),
            ("flowing_cut", "Corte Contínuo", "Flowing Cut", "combo:0.04"),
            ("spiritual_pressure", "Pressão Espiritual", "Spiritual Pressure", "melee_damage:0.012"),
            ("battle_thirst", "Sede de Luta", "Battle Thirst", "kill_fury:0.05,crit_heal:1"),
            ("getsuga", "Getsuga", "Getsuga", "fifth_hit:0.7,kill_burst:0.15"),
        ]),
        ("hoho", "7FD6E8", "Hohō", "Hohō", "hp:0.5", [
            ("flash_step", "Passo Rápido", "Flash Step", "speed:0.01"),
            ("flicker", "Lampejo", "Flicker", "aspd:0.015"),
            ("afterimage", "Imagem Residual", "Afterimage", "dodge:0.01"),
            ("utsusemi", "Utsusemi", "Utsusemi", "dodge_power:0.12,dodge:0.01"),
            ("light_landing", "Pouso Leve", "Light Landing", "fall:1,fall_resist:0.05"),
            ("flash_master", "Mestre do Lampejo", "Flash Master", "kill_speed:0.6,dodge:0.006"),
            ("offensive_shunpo", "Shunpo Ofensivo", "Offensive Shunpo", "run_power:0.12"),
            ("defensive_shunpo", "Shunpo Defensivo", "Defensive Shunpo", "full_health_resist:0.12"),
            ("ghost_step", "Passo Fantasma", "Ghost Step", "backstab:0.02"),
            ("senka", "Senka", "Senka", "backstab:0.10,crit_cdr:0.5"),
            ("flash_god", "Deus do Lampejo", "Flash God", "kill_cdr:2,dodge:0.05"),
        ]),
        ("kido", "9A5BD0", "Kidō", "Kidō", "mana:4", [
            ("incantation", "Encantação", "Incantation", "sp:0.01"),
            ("reiryoku", "Reiryoku", "Reiryoku", "mana:8"),
            ("bakudo", "Bakudō", "Bakudō", "on_hit.slowness:0.03"),
            ("double_incantation", "Encantação Dupla", "Double Incantation", "surge:0.015,cast_refund:0.03"),
            ("hado", "Hadō", "Hadō", "spell_damage.shadow:0.015"),
            ("eishohaki", "Eishōhaki", "Eishōhaki", "regen:0.02,crit_chance:0.006"),
            ("hado_90", "Hadō 90", "Hadō 90", "spell_damage_all:0.10"),
            ("bakudo_81", "Bakudō 81", "Bakudō 81", "cast_ward:0.12"),
            ("dense_reiatsu", "Reiatsu Denso", "Dense Reiatsu", "mana_on_hit:0.4"),
            ("spirit_seal", "Selo Espiritual", "Spirit Seal", "afflicted_damage:0.06"),
            ("bankai", "Bankai", "Bankai", "ult_power:0.30,charge_on_kill:2"),
        ]),
    ],
    subclasses=[
        ("shinigami_kenpachi", "D9483B", "minecraft:iron_sword", "Kenpachi", "Kenpachi",
         "Só a lâmina e a vontade de lutar: quanto pior a luta, melhor ele fica.",
         "Just the blade and the will to fight: the worse the fight, the better he gets.",
         "Sem defesa, só ataque: você bate mais e apanha mais — e cada golpe que leva deixa o seu próximo mais forte.",
         "No defense, only attack: you hit harder and get hit harder — and every blow you take powers your next one.",
         "atk:0.12,low_health_power:0.20,lifesteal:0.06,revenge:0.15,reckless:0.12", [
             ("eyepatch", "Tapa-Olho", "Eyepatch", "melee_damage:0.03"),
             ("bloodthirst", "Sede de Sangue", "Bloodthirst", "lifesteal:0.01"),
             ("bells", "Sinos", "Bells", "kill_fury:0.05,low_health_resist:0.06"),
             ("nozarashi", "Nozarashi", "Nozarashi", "fifth_hit:0.7,crit_heal:1.5"),
         ]),
        ("shinigami_kido_master", "9A5BD0", "minecraft:amethyst_shard", "Mestre de Kidō", "Kidō Master",
         "As artes demoníacas acima da espada: mais poder, mais reiryoku, inimigos presos.",
         "The demon arts above the sword: more power, more reiryoku, bound enemies.",
         "Encadeie Kidō: cada técnica usada aumenta o dano das próximas, e seus Kidō podem ecoar.",
         "Chain your Kidō: every technique used raises the damage of the next ones, and your Kidō may echo.",
         "sp:0.08,mana:100,on_hit.slowness:0.20,surge:0.03,echo:0.12", [
             ("short_chant", "Canto Curto", "Short Chant", "regen:0.03"),
             ("reiryoku_reserve", "Reserva de Reiryoku", "Reiryoku Reserve", "mana:15"),
             ("double_kido", "Kidō Duplo", "Double Kidō", "cast_refund:0.05,crit_mana:3"),
             ("kurohitsugi", "Kurohitsugi", "Kurohitsugi", "spell_damage_all:0.10,afflicted_damage:0.10"),
         ]),
    ])

CLASSES["quincy"] = dict(
    branches=[
        ("bow", "A8DFFF", "Arco", "Bow", "all_damage:0.004", [
            ("steady_aim", "Mira Firme", "Steady Aim", "ranged_damage:0.015"),
            ("heilig_pfeil", "Heilig Pfeil", "Heilig Pfeil", "spell_damage.light:0.015"),
            ("weak_spot", "Ponto Fraco", "Weak Spot", "crit_chance:0.01"),
            ("long_sight", "Mira Longa", "Long Sight", "far_damage:0.06"),
            ("long_shot", "Tiro Longo", "Long Shot", "first_strike:0.04"),
            ("licht_regen", "Licht Regen", "Licht Regen", "crit_damage:0.04,ranged_damage:0.008"),
            ("still_archer", "Arqueiro Imóvel", "Still Archer", "aim_power:0.12"),
            ("moving_archer", "Arqueiro Móvel", "Moving Archer", "run_power:0.12"),
            ("holy_arrow", "Flecha Sagrada", "Holy Arrow", "spell_damage_all:0.01"),
            ("rain_of_light", "Chuva de Luz", "Rain of Light", "combo:0.02,crit_mana:3"),
            ("arrow_of_judgment", "Flecha do Julgamento", "Arrow of Judgment", "fifth_hit:0.7,boss_damage:0.10"),
        ]),
        ("blut", "5A8AD8", "Blut", "Blut", "hp:0.5", [
            ("blut_vene", "Blut Vene", "Blut Vene", "armor:0.4"),
            ("reishi_veins", "Veias de Reishi", "Reishi Veins", "damage_resist:0.01"),
            ("blut_arterie", "Blut Arterie", "Blut Arterie", "sp:0.01"),
            ("reishi_skin", "Pele de Reishi", "Reishi Skin", "barrier:3"),
            ("discipline", "Disciplina", "Discipline", "hp:1"),
            ("static_blood", "Sangue Estático", "Static Blood", "projectile_resist:0.02,magic_resist:0.012"),
            ("blut_vene_anhaben", "Blut Vene Anhaben", "Blut Vene Anhaben", "full_health_resist:0.12"),
            ("full_blut_arterie", "Blut Arterie Pleno", "Full Blut Arterie", "reckless:0.12"),
            ("quincy_pride", "Orgulho Quincy", "Quincy Pride", "boss_resist:0.01"),
            ("cold_blood", "Sangue Frio", "Cold Blood", "low_health_resist:0.07,revenge:0.05"),
            ("auswahlen", "Auswählen", "Auswählen", "cheat_death:0.35,ult_power:0.10"),
        ]),
        ("reishi", "7FE8D0", "Reishi", "Reishi", "mana:4", [
            ("gathering", "Coleta de Reishi", "Reishi Gathering", "mana_on_hit:0.4"),
            ("hirenkyaku", "Hirenkyaku", "Hirenkyaku", "speed:0.01"),
            ("absorption", "Absorção", "Absorption", "regen:0.02"),
            ("reishi_field", "Campo de Reishi", "Reishi Field", "high_mana_power:0.05,mana:10"),
            ("spirit_reserve", "Reserva Espiritual", "Spirit Reserve", "mana:8"),
            ("sklaverei", "Sklaverei", "Sklaverei", "mana_on_kill:2,lifesteal:0.006"),
            ("total_harvest", "Colheita Total", "Total Harvest", "charge_on_kill:2"),
            ("dense_reishi", "Reishi Denso", "Dense Reishi", "cast_refund:0.12"),
            ("reishi_step", "Passo de Reishi", "Reishi Step", "dodge:0.008"),
            ("letzt_stil", "Letzt Stil", "Letzt Stil", "kill_fury:0.05"),
            ("vollstandig", "Vollständig", "Vollständig", "ult_power:0.30,kill_cdr:1.5"),
        ]),
    ],
    subclasses=[
        ("quincy_sternritter", "A8DFFF", "minecraft:spectral_arrow", "Sternritter", "Sternritter",
         "Cavaleiro das estrelas: flechas de luz que não erram o ponto fraco.",
         "Knight of the stars: arrows of light that never miss the weak spot.",
         "Atire de longe: alvos a 12 blocos ou mais levam muito mais dano, e cada crítico acelera o massacre.",
         "Shoot from afar: targets 12+ blocks away take far more damage, and every critical speeds up the slaughter.",
         "ranged_damage:0.15,crit_chance:0.08,spell_damage.light:0.10,far_damage:0.15,crit_fury:0.10", [
             ("schrift", "Schrift", "Schrift", "crit_damage:0.04"),
             ("eagle_eye", "Olho de Águia", "Eagle Eye", "far_damage:0.03"),
             ("heilig_bogen", "Heilig Bogen", "Heilig Bogen", "aim_power:0.06,crit_cdr:0.5"),
             ("falling_star", "Estrela Cadente", "Falling Star", "echo:0.15,boss_damage:0.10"),
         ]),
        ("quincy_blut_guard", "5A8AD8", "minecraft:diamond_chestplate", "Guardião do Blut", "Blut Guardian",
         "O sangue como armadura: aguenta o golpe e ainda colhe reishi de quem bate.",
         "Blood as armor: takes the blow and still gathers reishi from the fight.",
         "Sangue como armadura: uma barreira que se refaz fora de combate e dá choque em quem bate em você.",
         "Blood as armor: a barrier that rebuilds out of combat and shocks whoever hits you.",
         "armor:4,damage_resist:0.06,mana_on_hit:2,barrier:8,on_struck.shock:0.5", [
             ("steel_veins", "Veias de Aço", "Steel Veins", "armor:1"),
             ("steady_pulse", "Pulso Firme", "Steady Pulse", "hp:2"),
             ("blood_reflex", "Reflexo de Sangue", "Blood Reflex", "guard_fortify:0.08,parry_mana:8"),
             ("immortal_blood", "Sangue Imortal", "Immortal Blood", "low_health_resist:0.15,heal_on_kill:2"),
         ]),
    ])

CLASSES["gunslinger"] = dict(
    branches=[
        ("trigger", "D9A441", "Gatilho", "Trigger", "ranged_damage:0.005", [
            ("quick_draw", "Saque Rápido", "Quick Draw", "aspd:0.015"),
            ("dead_eye", "Olho Morto", "Dead Eye", "crit_chance:0.01"),
            ("caliber", "Calibre", "Caliber", "ranged_damage:0.015"),
            ("sure_shot", "Tiro Certeiro", "Sure Shot", "first_strike:0.08,crit_mana:3"),
            ("fan_the_hammer", "Leque de Tiros", "Fan the Hammer", "spell_damage.wind:0.015"),
            ("bullseye", "Na Mosca", "Bullseye", "crit_damage:0.04,crit_chance:0.006"),
            ("sixth_bullet", "Sexta Bala", "Sixth Bullet", "fifth_hit:0.6"),
            ("burst_fire", "Rajada", "Burst Fire", "combo:0.04"),
            ("long_barrel", "Cano Longo", "Long Barrel", "far_damage:0.015"),
            ("steady_hand", "Mão Firme", "Steady Hand", "aim_power:0.06"),
            ("silver_bullet", "Bala de Prata", "Silver Bullet", "boss_damage:0.15,crit_cdr:1"),
        ]),
        ("powder", "FF6A2E", "Pólvora", "Powder", "all_damage:0.004", [
            ("hot_lead", "Chumbo Quente", "Hot Lead", "spell_damage.fire:0.015"),
            ("incendiary", "Incendiária", "Incendiary", "on_hit.burn:0.03"),
            ("powder_keg", "Barril de Pólvora", "Powder Keg", "sp:0.01"),
            ("black_powder", "Pólvora Negra", "Black Powder", "afflicted_damage:0.07"),
            ("smoke_screen", "Cortina de Fumaça", "Smoke Screen", "projectile_resist:0.02"),
            ("dragon_breath", "Sopro de Dragão", "Dragon's Breath", "spell_damage.fire:0.012,execute:0.02"),
            ("explosive_rounds", "Munição Explosiva", "Explosive Rounds", "kill_burst:0.20"),
            ("piercing_rounds", "Munição Perfurante", "Piercing Rounds", "boss_damage:0.10"),
            ("fuse", "Estopim", "Fuse", "all_damage:0.008"),
            ("crossfire", "Fogo Cruzado", "Crossfire", "kill_fury:0.05"),
            ("dynamite", "Dinamite", "Dynamite", "kill_burst:0.25,charge_on_kill:2"),
        ]),
        ("duel", "C8B89A", "Duelo", "Duel", "hp:0.5", [
            ("high_noon", "Meio-Dia", "High Noon", "first_strike:0.04"),
            ("roll", "Rolar", "Roll", "dodge:0.01"),
            ("spurs", "Esporas", "Spurs", "speed:0.01"),
            ("roll_and_shoot", "Rolar e Atirar", "Roll and Shoot", "dodge_power:0.12,dodge:0.01"),
            ("grit", "Garra", "Grit", "hp:1"),
            ("last_bullet", "Última Bala", "Last Bullet", "low_health_power:0.025,mana_on_kill:1.5"),
            ("ice_in_the_veins", "Gelo nas Veias", "Ice in the Veins", "full_health_power:0.12"),
            ("cornered", "Encurralado", "Cornered", "low_health_resist:0.15"),
            ("quick_holster", "Coldre Rápido", "Quick Holster", "mana_on_hit:0.4"),
            ("eye_to_eye", "Olho no Olho", "Eye to Eye", "near_damage:0.06,revenge:0.05"),
            ("sundown_duel", "Duelo ao Pôr do Sol", "Sundown Duel", "cheat_death:0.3,kill_cdr:2"),
        ]),
    ],
    subclasses=[
        ("gunslinger_deadeye", "D9A441", "minecraft:spyglass", "Atirador de Elite", "Deadeye",
         "Um tiro, um alvo: o primeiro disparo decide a luta.",
         "One shot, one target: the first bullet decides the fight.",
         "Agache e mire: agachado e de longe, cada tiro é quase uma sentença.",
         "Crouch and aim: sneaking and from afar, every shot is close to a sentence.",
         "crit_chance:0.10,crit_damage:0.30,first_strike:0.20,aim_power:0.20,far_damage:0.10", [
             ("scope", "Luneta", "Scope", "far_damage:0.03"),
             ("held_breath", "Respiração Presa", "Held Breath", "aim_power:0.03"),
             ("one_shot", "Um Tiro", "One Shot", "boss_damage:0.06,crit_cdr:0.5"),
             ("headshot", "Tiro na Cabeça", "Headshot", "cull:0.08,fifth_hit:0.5"),
         ]),
        ("gunslinger_outlaw", "FF6A2E", "minecraft:fire_charge", "Fora da Lei", "Outlaw",
         "Atira correndo e some na fumaça: fogo, pernas e nenhuma regra.",
         "Shoots on the run and vanishes in smoke: fire, legs and no rules.",
         "Nunca pare: correndo você atira mais forte, e cada abate adianta as suas recargas.",
         "Never stop: while sprinting you shoot harder, and every kill advances your cooldowns.",
         "speed:0.08,dodge:0.08,spell_damage.fire:0.10,kill_speed:3,run_power:0.20,kill_cdr:1.5", [
             ("worn_boots", "Botas Gastas", "Worn Boots", "run_power:0.03"),
             ("hand_on_holster", "Mão no Coldre", "Hand on the Holster", "aspd:0.02"),
             ("shootout", "Tiroteio", "Shootout", "kill_fury:0.05,near_damage:0.05"),
             ("wanted", "Procurado", "Wanted", "dodge_power:0.20,charge_on_kill:2"),
         ]),
    ])

CLASSES["ranger"] = dict(
    branches=[
        ("archery", "6BCB5A", "Arqueria", "Archery", "ranged_damage:0.005", [
            ("firm_draw", "Puxada Firme", "Firm Draw", "ranged_damage:0.015"),
            ("fletching", "Empenagem", "Fletching", "crit_chance:0.01"),
            ("read_the_wind", "Ler o Vento", "Read the Wind", "spell_damage.wind:0.015"),
            ("distant_shot", "Tiro Distante", "Distant Shot", "far_damage:0.06"),
            ("pin_down", "Cravar", "Pin Down", "on_hit.slowness:0.03"),
            ("heartseeker", "Busca-Coração", "Heartseeker", "crit_damage:0.04,ranged_damage:0.008"),
            ("from_the_bushes", "Tocaia", "From the Bushes", "aim_power:0.12"),
            ("running_shot", "Tiro em Movimento", "Running Shot", "run_power:0.12"),
            ("serrated_tip", "Ponta Serrilhada", "Serrated Tip", "on_hit.bleed:0.03"),
            ("twin_arrow", "Flecha Dupla", "Twin Arrow", "echo:0.05,crit_mana:3"),
            ("killing_arrow", "Flecha Mortal", "Killing Arrow", "cull:0.10,first_strike:0.15"),
        ]),
        ("companions", "C89A5A", "Companheiros", "Companions", "summon_damage:0.006", [
            ("bond", "Vínculo", "Bond", "summon_damage:0.02"),
            ("pack_tactics", "Tática de Matilha", "Pack Tactics", "summon_damage:0.02"),
            ("shared_meal", "Refeição Dividida", "Shared Meal", "heal_on_kill:0.4"),
            ("sic_em", "Pega!", "Sic 'Em!", "summon_mark:0.08"),
            ("whistle", "Assobio", "Whistle", "regen:0.02"),
            ("alpha", "Alfa", "Alpha", "summon_damage:0.02,hp:0.6"),
            ("sharp_fangs", "Presas Afiadas", "Sharp Fangs", "summon_damage:0.12"),
            ("guardians", "Guardiões", "Guardians", "summon_bond:0.12"),
            ("good_feed", "Ração Boa", "Good Feed", "healing_received:0.02"),
            ("hunting_bond", "Laço de Caça", "Hunting Bond", "summon_lifesteal:0.04"),
            ("legendary_pack", "Matilha Lendária", "Legendary Pack", "kill_fury:0.08,summon_damage:0.10"),
        ]),
        ("trail", "5AB8A8", "Trilha", "Trail", "hp:0.5", [
            ("trailblazer", "Desbravador", "Trailblazer", "speed:0.01"),
            ("soft_fall", "Queda Macia", "Soft Fall", "fall:1,fall_resist:0.05"),
            ("forager", "Coletor", "Forager", "regen_health:0.2"),
            ("fresh_tracks", "Rastro Fresco", "Fresh Tracks", "first_strike:0.06,kill_speed:1"),
            ("camouflage", "Camuflagem", "Camouflage", "dodge:0.01"),
            ("hunters_mark", "Marca do Caçador", "Hunter's Mark", "execute:0.02,first_strike:0.02"),
            ("monster_hunter", "Caçador de Monstros", "Monster Hunter", "boss_damage:0.10"),
            ("survivor", "Sobrevivente", "Survivor", "low_health_resist:0.15"),
            ("campfire", "Acampamento", "Campfire", "hp:1"),
            ("snare", "Armadilha", "Snare", "afflicted_damage:0.06"),
            ("apex_predator", "Predador Supremo", "Apex Predator", "kill_cdr:2,charge_on_kill:2"),
        ]),
    ],
    subclasses=[
        ("ranger_sharpshooter", "6BCB5A", "minecraft:bow", "Atirador Preciso", "Sharpshooter",
         "O arco acima de tudo: a primeira flecha chega antes do inimigo perceber.",
         "The bow above all: the first arrow arrives before the enemy notices.",
         "Mantenha distância e insista no mesmo alvo: de longe, cada flecha seguida dói mais que a anterior.",
         "Keep your distance and stay on one target: from afar, each arrow in a row hurts more than the last.",
         "ranged_damage:0.15,first_strike:0.20,crit_damage:0.20,far_damage:0.15,combo:0.03", [
             ("taut_string", "Corda Tensa", "Taut String", "ranged_damage:0.03"),
             ("fair_wind", "Vento a Favor", "Fair Wind", "far_damage:0.03"),
             ("calm_aim", "Mira Calma", "Calm Aim", "aim_power:0.06,crit_fury:0.05"),
             ("phantom_arrow", "Flecha Fantasma", "Phantom Arrow", "echo:0.15,boss_damage:0.10"),
         ]),
        ("ranger_beastmaster", "C89A5A", "minecraft:lead", "Mestre das Feras", "Beastmaster",
         "Luta em dupla: os companheiros batem muito mais e a caça sustenta os dois.",
         "Fights as a pair: companions hit far harder and the hunt sustains both.",
         "Lute em dupla: marque o alvo com um golpe e seus companheiros o destroem — e protegem você enquanto lutam.",
         "Fight as a pair: mark a target with a hit and your companions tear it apart — and shield you while they fight.",
         "summon_damage:0.25,heal_on_kill:2,regen_health:1,summon_mark:0.20,summon_bond:0.12", [
             ("old_wolf", "Lobo Velho", "Old Wolf", "summon_damage:0.03"),
             ("hawk_eye", "Olho de Gavião", "Hawk Eye", "crit_chance:0.015"),
             ("paired_hunt", "Caça em Dupla", "Paired Hunt", "summon_lifesteal:0.05,kill_speed:1.5"),
             ("one_heart", "Um Só Coração", "One Heart", "kill_fury:0.06,cheat_death:0.25"),
         ]),
    ])

CLASSES["gambler"] = dict(
    branches=[
        ("luck", "E04A8C", "Sorte", "Luck", "crit_damage:0.006", [
            ("lucky_coin", "Moeda da Sorte", "Lucky Coin", "luck:0.2"),
            ("loaded_dice", "Dados Viciados", "Loaded Dice", "lucky:0.01"),
            ("hot_streak", "Maré de Sorte", "Hot Streak", "crit_chance:0.01"),
            ("double_dice", "Dado Duplo", "Double Dice", "wild:0.06"),
            ("high_stakes", "Aposta Alta", "High Stakes", "crit_damage:0.03"),
            ("jackpot", "Jackpot", "Jackpot", "lucky:0.006,crit_damage:0.025"),
            ("all_on_red", "Tudo no Vermelho", "All on Red", "wild:0.15"),
            ("safe_play", "Jogo Seguro", "Safe Play", "crit_chance:0.08"),
            ("streak", "Sequência", "Streak", "combo:0.008"),
            ("high_tide", "Maré Alta", "High Tide", "crit_fury:0.07"),
            ("triple_seven", "Sete Sete Sete", "Triple Seven", "fifth_hit:0.7,crit_cdr:1"),
        ]),
        ("bluff", "8A7BD8", "Blefe", "Bluff", "hp:0.5", [
            ("poker_face", "Cara de Pôquer", "Poker Face", "magic_resist:0.015"),
            ("sleight", "Prestidigitação", "Sleight of Hand", "dodge:0.01"),
            ("fold_in_time", "Desistir a Tempo", "Fold in Time", "speed:0.01"),
            ("marked_card", "Carta Marcada", "Marked Card", "dodge_power:0.12,dodge:0.01"),
            ("ace_up_sleeve", "Ás na Manga", "Ace Up the Sleeve", "second_wind:1"),
            ("house_edge", "Vantagem da Casa", "House Edge", "damage_resist:0.008,dodge:0.006"),
            ("full_bluff", "Blefe Total", "Full Bluff", "low_health_power:0.20"),
            ("cold_table", "Mesa Fria", "Cold Table", "full_health_resist:0.12"),
            ("straight_face", "Cara Lavada", "Straight Face", "boss_resist:0.01"),
            ("switch_the_cards", "Trocar as Cartas", "Switch the Cards", "revenge:0.06,parry_mana:8"),
            ("joker", "Coringa", "Joker", "cheat_death:0.5"),
        ]),
        ("bank", "E8C84A", "Banca", "Bank", "mana:4", [
            ("ante", "Aposta Inicial", "Ante", "mana:8"),
            ("payout", "Pagamento", "Payout", "mana_on_kill:2"),
            ("interest", "Juros", "Interest", "regen:0.02"),
            ("dividends", "Rendimento", "Dividends", "crit_mana:4,mana:10"),
            ("chips", "Fichas", "Chips", "sp:0.01"),
            ("all_in", "All In", "All In", "low_health_power:0.025,sp:0.006"),
            ("full_bank", "Banca Cheia", "Full Bank", "high_mana_power:0.12"),
            ("open_casino", "Cassino Aberto", "Open Casino", "cast_refund:0.12"),
            ("tip", "Gorjeta", "Tip", "heal_on_kill:0.4"),
            ("house_bonus", "Bônus da Casa", "House Bonus", "charge_on_kill:1,kill_fury:0.03"),
            ("sure_jackpot", "Jackpot Garantido", "Sure Jackpot", "ult_power:0.30,kill_cdr:1.5"),
        ]),
    ],
    subclasses=[
        ("gambler_high_roller", "E04A8C", "minecraft:gold_ingot", "Grande Apostador", "High Roller",
         "Tudo ou nada: golpes de sorte que dobram o dano e apostas melhores com a vida no fim.",
         "All or nothing: lucky hits that double damage, and better bets when health runs out.",
         "Tudo ou nada: cada golpe é uma aposta — pode sair fraco ou MUITO forte — e os críticos pagam em vida.",
         "All or nothing: every hit is a bet — it may land weak or VERY strong — and criticals pay out in health.",
         "lucky:0.08,crit_damage:0.25,low_health_power:0.15,wild:0.20,crit_heal:2", [
             ("double_down", "Aposta Dobrada", "Double Down", "wild:0.02"),
             ("hot_hand", "Mão Quente", "Hot Hand", "crit_chance:0.015"),
             ("on_the_line", "Na Risca", "On the Line", "low_health_resist:0.07,crit_fury:0.06"),
             ("break_the_bank", "Quebrar a Banca", "Break the Bank", "lucky:0.04,kill_burst:0.20"),
         ]),
        ("gambler_cardsharp", "8A7BD8", "minecraft:paper", "Mestre das Cartas", "Cardsharp",
         "A casa sempre ganha: sai ileso, com sorte no bolso e a banca cheia.",
         "The house always wins: walks away unharmed, luck in pocket and the bank full.",
         "A casa sempre ganha: esquive e revide, e veja parte das suas técnicas sair pela metade do preço.",
         "The house always wins: dodge and strike back, and watch some of your techniques cost half.",
         "dodge:0.08,luck:2,mana_on_kill:6,sp:0.05,cast_refund:0.15,dodge_power:0.20", [
             ("stacked_deck", "Baralho Viciado", "Stacked Deck", "crit_chance:0.015"),
             ("fast_hand", "Mão Rápida", "Fast Hand", "aspd:0.02"),
             ("ace_of_spades", "Ás de Espadas", "Ace of Spades", "combo:0.02,crit_mana:3"),
             ("royal_flush", "Royal Flush", "Royal Flush", "echo:0.12,crit_cdr:1"),
         ]),
    ])

CLASSES["steelball"] = dict(
    branches=[
        ("spin", "E8C84A", "Rotação", "Spin", "ranged_damage:0.005", [
            ("rotation", "Rotação", "Rotation", "ranged_damage:0.015"),
            ("golden_ratio", "Proporção Áurea", "Golden Ratio", "crit_chance:0.01"),
            ("torque", "Torque", "Torque", "spell_damage.earth:0.015"),
            ("perfect_spin", "Giro Perfeito", "Perfect Spin", "combo:0.025"),
            ("rebound", "Ricochete", "Rebound", "spell_damage.wind:0.015"),
            ("golden_spin", "Rotação Dourada", "Golden Spin", "crit_damage:0.04,sp:0.008"),
            ("heavy_ball", "Esfera Pesada", "Heavy Ball", "near_damage:0.12"),
            ("long_throw", "Arremesso Longo", "Long Throw", "far_damage:0.12"),
            ("vibration", "Vibração", "Vibration", "on_hit.slowness:0.03"),
            ("golden_rectangle", "Retângulo Áureo", "Golden Rectangle", "crit_fury:0.06,crit_mana:3"),
            ("infinite_spin", "Rotação Infinita", "Infinite Spin", "fifth_hit:0.7,echo:0.10"),
        ]),
        ("body", "B4C0CC", "Corpo", "Body", "hp:0.5", [
            ("hardened_body", "Corpo Enrijecido", "Hardened Body", "armor:0.4"),
            ("surgeon_hands", "Mãos de Cirurgião", "Surgeon's Hands", "healing_received:0.02"),
            ("steel_nerve", "Nervo de Aço", "Steel Nerve", "hp:1"),
            ("hardened_skin", "Pele Enrijecida", "Hardened Skin", "full_health_resist:0.06,hp:2"),
            ("relaxed_muscle", "Músculo Solto", "Relaxed Muscle", "regen_health:0.2"),
            ("zeppeli_tradition", "Tradição Zeppeli", "Zeppeli Tradition", "second_wind:1.5,damage_resist:0.006"),
            ("anesthesia", "Anestesia", "Anesthesia", "low_health_resist:0.15"),
            ("suture", "Sutura", "Suture", "heal_on_kill:2.5"),
            ("posture", "Postura", "Posture", "kbres:0.03"),
            ("lesson_four", "Lição 4", "Lesson 4", "revenge:0.06,guard_fortify:0.05"),
            ("final_lesson", "Lição Final", "Final Lesson", "cheat_death:0.4"),
        ]),
        ("ride", "A87B4A", "Cavalgada", "Ride", "mana:4", [
            ("firm_saddle", "Sela Firme", "Firm Saddle", "kbres:0.03"),
            ("gallop", "Galope", "Gallop", "speed:0.01"),
            ("long_road", "Estrada Longa", "Long Road", "mana:8"),
            ("galloping_charge", "Carga a Galope", "Galloping Charge", "run_power:0.07"),
            ("breath", "Fôlego", "Breath", "regen:0.02"),
            ("final_stretch", "Reta Final", "Final Stretch", "kill_speed:0.6,mana_on_kill:1.5"),
            ("long_race", "Corrida Longa", "Long Race", "kill_cdr:1.5"),
            ("sprint_start", "Arrancada", "Sprint Start", "first_strike:0.18"),
            ("stirrup", "Estribo", "Stirrup", "mana_on_hit:0.4"),
            ("stage_won", "Etapa Vencida", "Stage Won", "kill_fury:0.04,charge_on_kill:1"),
            ("finish_line", "Linha de Chegada", "Finish Line", "ult_power:0.30,boss_damage:0.10"),
        ]),
    ],
    subclasses=[
        ("steelball_executioner", "E8C84A", "minecraft:iron_axe", "Carrasco Real", "Royal Executioner",
         "A Rotação como sentença: quem já está ferido não levanta de novo.",
         "The Spin as a sentence: whoever is already wounded does not get up again.",
         "Termine o serviço: inimigos comuns quase mortos caem na hora, e cada execução deixa você mais forte.",
         "Finish the job: common enemies near death drop at once, and every execution makes you stronger.",
         "execute:0.25,crit_chance:0.08,ranged_damage:0.10,cull:0.08,kill_fury:0.06", [
             ("the_sentence", "Sentença", "The Sentence", "execute:0.04"),
             ("hangmans_hand", "Mão do Carrasco", "Hangman's Hand", "ranged_damage:0.03"),
             ("no_appeal", "Sem Apelação", "No Appeal", "boss_damage:0.06,crit_cdr:0.5"),
             ("royal_duty", "Dever Real", "Royal Duty", "kill_burst:0.20,kill_cdr:1.5"),
         ]),
        ("steelball_surgeon", "B4C0CC", "minecraft:glistering_melon_slice", "Cirurgião da Rotação", "Spin Surgeon",
         "A Rotação como remédio: o corpo se conserta no meio da luta.",
         "The Spin as medicine: the body mends itself in the middle of the fight.",
         "A Rotação cura: críticos devolvem vida e, quanto mais ferido você está, mais difícil é derrubar você.",
         "The Spin heals: criticals give health back and, the more wounded you are, the harder you are to bring down.",
         "healing_received:0.25,regen_health:1.5,hp:6,crit_heal:2,low_health_resist:0.15", [
             ("scalpel", "Bisturi", "Scalpel", "crit_chance:0.015"),
             ("stitches", "Pontos", "Stitches", "hp:2"),
             ("diagnosis", "Diagnóstico", "Diagnosis", "afflicted_damage:0.06,on_hit.weakness:0.10"),
             ("zeppeli_miracle", "Milagre de Zeppeli", "Zeppeli Miracle", "heal_on_kill:2,barrier:8"),
         ]),
    ])

CLASSES["hollowfied"] = dict(
    branches=[
        ("hunger", "D43C3C", "Fome", "Hunger", "all_damage:0.004", [
            ("devour", "Devorar", "Devour", "heal_on_kill:0.4"),
            ("soul_hunger", "Fome de Almas", "Soul Hunger", "lifesteal:0.01"),
            ("gorge", "Empanturrar", "Gorge", "mana_on_kill:2"),
            ("bite", "Mordida", "Bite", "on_hit.bleed:0.15,crit_heal:1"),
            ("predator", "Predador", "Predator", "execute:0.03"),
            ("gluttony", "Gula", "Gluttony", "lifesteal:0.008,hp:0.6"),
            ("devour_all", "Devorar Tudo", "Devour All", "heal_on_kill:3"),
            ("blind_hunger", "Fome Cega", "Blind Hunger", "reckless:0.15"),
            ("blood_scent", "Faro de Sangue", "Blood Scent", "afflicted_damage:0.015"),
            ("hunting_frenzy", "Frenesi de Caça", "Hunting Frenzy", "kill_fury:0.06"),
            ("endless_hunger", "Fome Sem Fim", "Endless Hunger", "cull:0.10,charge_on_kill:3"),
        ]),
        ("hierro", "E6DFCF", "Hierro", "Hierro", "hp:0.5", [
            ("tough_hide", "Couro Duro", "Tough Hide", "armor:0.4"),
            ("bone_mask", "Máscara de Osso", "Bone Mask", "tough:0.3"),
            ("dense_reiatsu", "Reiatsu Denso", "Dense Reiatsu", "magic_resist:0.015"),
            ("dense_hierro", "Hierro Denso", "Dense Hierro", "full_health_resist:0.06,hp:2"),
            ("regeneration", "Regeneração", "Regeneration", "regen_health:0.2"),
            ("steel_skin", "Pele de Aço", "Steel Skin", "damage_resist:0.008,thorns:0.02"),
            ("bone_spikes", "Espinhos de Osso", "Bone Spikes", "guard_reflect:0.6"),
            ("instant_regeneration", "Regeneração Instantânea", "Instant Regeneration", "low_health_resist:0.15"),
            ("husk", "Casca", "Husk", "boss_resist:0.01"),
            ("cracked_mask", "Máscara Rachada", "Cracked Mask", "revenge:0.07,second_wind:2"),
            ("absolute_hierro", "Hierro Absoluto", "Absolute Hierro", "cheat_death:0.4,on_struck.bleed:0.5"),
        ]),
        ("instinct", "8A5BD0", "Instinto", "Instinct", "melee_damage:0.005", [
            ("claws", "Garras", "Claws", "atk:0.015"),
            ("sonido", "Sonído", "Sonído", "speed:0.01"),
            ("frenzy", "Frenesi", "Frenzy", "low_health_power:0.03"),
            ("twin_claw", "Garra Dupla", "Twin Claw", "combo:0.025"),
            ("cero_core", "Núcleo de Cero", "Cero Core", "spell_damage.shadow:0.015"),
            ("apex", "Ápice", "Apex", "crit_chance:0.01,kill_speed:0.6"),
            ("lone_hunter", "Caçador Solitário", "Lone Hunter", "near_damage:0.12"),
            ("charged_cero", "Cero Carregado", "Charged Cero", "spell_damage_all:0.12"),
            ("howl", "Uivo", "Howl", "melee_damage:0.012"),
            ("deadly_sonido", "Sonído Mortal", "Deadly Sonído", "dodge_power:0.10,backstab:0.08"),
            ("resurreccion", "Resurrección", "Resurrección", "ult_power:0.30,kill_cdr:1.5"),
        ]),
    ],
    subclasses=[
        ("hollowfied_devourer", "D43C3C", "minecraft:rotten_flesh", "Devorador", "Devourer",
         "Come para viver: cada golpe e cada abate enchem o buraco no peito.",
         "Eats to live: every hit and every kill fills the hole in the chest.",
         "Coma para viver: seus golpes curam, e inimigos comuns quase mortos são devorados na hora.",
         "Eat to live: your hits heal you, and common enemies near death are devoured on the spot.",
         "lifesteal:0.10,heal_on_kill:3,hp:8,cull:0.06,crit_heal:2", [
             ("jaws", "Mandíbula", "Jaws", "lifesteal:0.01"),
             ("bottomless_stomach", "Estômago Sem Fundo", "Bottomless Stomach", "hp:2"),
             ("feast", "Banquete", "Feast", "kill_fury:0.05,mana_on_kill:3"),
             ("vast_appetite", "Vasto Apetite", "Vast Appetite", "kill_burst:0.20,charge_on_kill:2"),
         ]),
        ("hollowfied_berserker", "8A5BD0", "minecraft:bone", "Fúria Vazia", "Hollow Rage",
         "Só instinto: mais rápido, mais forte e pior ainda quando está morrendo.",
         "Pure instinct: faster, stronger and worse still when dying.",
         "Fúria pura: você causa e recebe mais dano, e cada golpe que leva alimenta o seu próximo.",
         "Pure rage: you deal and take more damage, and every blow you take feeds your next one.",
         "low_health_power:0.25,atk:0.10,speed:0.06,reckless:0.15,revenge:0.15", [
             ("long_claws", "Garras Longas", "Long Claws", "melee_damage:0.03"),
             ("no_pain", "Sem Dor", "No Pain", "low_health_resist:0.03"),
             ("war_howl", "Uivo de Guerra", "War Howl", "kill_fury:0.05,kill_speed:1.5"),
             ("final_rage", "Fúria Final", "Final Rage", "fifth_hit:0.6,cheat_death:0.25"),
         ]),
    ])

# =====================================================================================================
#  Textos fixos (tela, avisos, descricao das passivas). '%' solto quebra a traducao: use %s, %N$s ou %%.
# =====================================================================================================
S = "screen.isekai_mahou.talents."
M = "message.isekai_mahou.talents."
T = "talent.isekai_mahou."
TEXT = [
    (S + "tab", "Talentos", "Talents"),
    (S + "title", "Talentos — %s", "Talents — %s"),
    (S + "none", "Sua classe ainda não tem árvore de talentos.", "Your class has no talent tree yet."),
    (S + "points", "Pontos livres: %s", "Free points: %s"),
    (S + "points_spent", "gastos %s de %s", "spent %s of %s"),
    (S + "view_tree", "Árvore", "Tree"),
    (S + "view_subclass", "Subclasse", "Subclass"),
    (S + "view_summary", "Bônus", "Bonuses"),
    (S + "branch_points", "%s pts", "%s pts"),
    (S + "tier_level", "Nv %s", "Lv %s"),
    (S + "or", "ou", "or"),
    (S + "kind.small", "Talento", "Talent"),
    (S + "kind.notable", "Talento notável", "Notable talent"),
    (S + "kind.choice", "Escolha: um ou outro", "Choice: one or the other"),
    (S + "kind.capstone", "Talento final do ramo", "Branch capstone"),
    (S + "kind.mastery", "Maestria (repetível)", "Mastery (repeatable)"),
    (S + "kind.spec_capstone", "Talento final da especialização", "Specialization capstone"),
    (S + "rank", "Nível %s/%s", "Rank %s/%s"),
    (S + "now", "Agora:", "Now:"),
    (S + "next", "Próximo nível:", "Next rank:"),
    (S + "at_max", "No máximo:", "At max rank:"),
    (S + "mastery_note", "Cada nível rende um pouco menos que o anterior. Serve para os pontos que sobram.",
     "Each rank gives a little less than the one before. A home for spare points."),
    (S + "need_level", "Requer nível %s", "Requires level %s"),
    (S + "need_node", "Requer %s no nível %s", "Requires %s at rank %s"),
    (S + "need_branch", "Requer %s pontos nas fileiras acima de %s (você tem %s)",
     "Requires %s points in the rows above in %s (you have %s)"),
    (S + "need_spec_points", "Requer %s pontos nesta fileira (você tem %s)", "Requires %s points in this row (you have %s)"),
    (S + "need_spec", "Só para a especialização %s", "Only for the %s specialization"),
    (S + "rival", "Escolha: este OU %s", "Choice: this OR %s"),
    (S + "rival_taken", "Bloqueado: você escolheu %s", "Locked: you chose %s"),
    (S + "click", "Clique para gastar 1 ponto (Shift: até 5)", "Click to spend 1 point (Shift: up to 5)"),
    (S + "click_one", "Clique para gastar 1 ponto", "Click to spend 1 point"),
    (S + "maxed", "Nível máximo", "Max rank"),
    (S + "no_points", "Sem pontos livres (1 ponto por nível)", "No free points (1 point per level)"),
    (S + "side_title", "Bônus ativos", "Active bonuses"),
    (S + "side_empty", "Nenhum ponto gasto ainda. Você ganha 1 ponto por nível.",
     "No points spent yet. You earn 1 point per level."),
    (S + "side_sub_open", "Especialização disponível!", "Specialization available!"),
    (S + "side_sub_locked", "Especialização: Círculo %s", "Specialization: Circle %s"),
    (S + "side_cap", "(teto %s)", "(cap %s)"),
    (S + "sub_none", "Esta classe não tem especializações.", "This class has no specializations."),
    (S + "sub_chosen", "Sua especialização. Só o Altar do Recomeço desfaz essa escolha.",
     "Your specialization. Only the Altar of New Beginnings can undo this choice."),
    (S + "sub_pick", "Escolha UMA especialização. A escolha é permanente.",
     "Choose ONE specialization. The choice is permanent."),
    (S + "sub_locked", "As especializações abrem no Círculo %s (você está no Círculo %s).",
     "Specializations unlock at Circle %s (you are at Circle %s)."),
    (S + "sub_play", "Como joga", "How it plays"),
    (S + "sub_signature", "Assinatura (ganha ao escolher)", "Signature (granted on pick)"),
    (S + "sub_row", "Talentos exclusivos", "Exclusive talents"),
    (S + "sub_row_hint", "Comprados com os mesmos pontos da árvore.", "Bought with the same points as the tree."),
    (S + "sub_btn_locked", "Círculo %s", "Circle %s"),
    (S + "sub_btn_confirm", "Confirmar (permanente)", "Confirm (permanent)"),
    (S + "sub_btn_pick", "Escolher", "Choose"),
    (M + "point", "Ponto de talento novo! Livres: %s (menu B → Talentos)", "New talent point! Free: %s (B menu → Talents)"),
    (M + "subclass_open", "Especialização liberada! Escolha a sua no menu B → Talentos → Especialização.",
     "Specialization unlocked! Choose yours in the B menu → Talents → Specialization."),
    (M + "subclass_chosen", "Especialização escolhida: %s", "Specialization chosen: %s"),
    (M + "refund", "A árvore de talentos mudou: %s pontos voltaram para você. Gaste de novo no menu B → Talentos.",
     "The talent tree changed: %s points were refunded. Spend them again in the B menu → Talents."),
    (M + "cheat_death", "Você recusou a morte! (de novo em 5 min)", "You refused death! (again in 5 min)"),
    ("hint.isekai_mahou.talents.unspent", "Você tem pontos de talento livres: menu B → Personagem → Talentos.",
     "You have free talent points: B menu → Character → Talents."),
    ("hint.isekai_mahou.talents.subclass", "Especialização liberada: menu B → Talentos → Especialização.",
     "Specialization unlocked: B menu → Talents → Specialization."),
    (T + "fx.attribute", "%s %s", "%s %s"),
    (T + "stat.spell_damage", "%1$s de dano com magias de %2$s", "%1$s %2$s spell damage"),
    (T + "stat.on_hit", "%1$s de chance de %2$s ao acertar", "%1$s chance to %2$s on hit"),
    (T + "stat.on_struck", "Quem acerta você corpo a corpo: %1$s de chance de %2$s",
     "Melee attackers who hit you: %1$s chance to %2$s"),
    (T + "onhit.burn", "incendiar", "ignite"),
    (T + "onhit.poison", "envenenar", "poison"),
    (T + "onhit.slowness", "causar lentidão", "slow"),
    (T + "onhit.weakness", "enfraquecer", "weaken"),
    (T + "onhit.wither", "causar decomposição", "wither"),
    (T + "onhit.bleed", "causar sangramento", "cause bleeding"),
    (T + "onhit.chill", "resfriar", "chill"),
    (T + "onhit.shock", "eletrizar", "shock"),
    (T + "stat.ranged_damage", "%s de dano com projéteis", "%s projectile damage"),
    (T + "stat.summon_damage", "%s de dano das invocações e companheiros", "%s summon and companion damage"),
    (T + "stat.crit_chance", "%s de chance de crítico", "%s critical chance"),
    (T + "stat.crit_damage", "%s de dano crítico", "%s critical damage"),
    (T + "stat.lucky", "%s de chance de golpe de sorte (dano dobrado)", "%s chance of a lucky hit (double damage)"),
    (T + "stat.lifesteal", "%s do dano causado vira vida", "%s of damage dealt heals you"),
    (T + "stat.mana_on_hit", "%s de recurso ao acertar", "%s resource on hit"),
    (T + "stat.mana_on_kill", "%s de recurso ao abater", "%s resource on kill"),
    (T + "stat.heal_on_kill", "%s de vida ao abater", "%s health on kill"),
    (T + "stat.kill_speed", "%s s de Velocidade ao abater", "%s s of Speed on kill"),
    (T + "stat.damage_resist", "%s de redução de todo dano", "%s damage reduction"),
    (T + "stat.magic_resist", "%s de resistência a magia", "%s magic resistance"),
    (T + "stat.projectile_resist", "%s de resistência a projéteis", "%s projectile resistance"),
    (T + "stat.melee_resist", "%s de resistência a golpes corpo a corpo", "%s melee resistance"),
    (T + "stat.fall_resist", "%s de resistência a queda", "%s fall damage resistance"),
    (T + "stat.fire_resist", "%s de resistência a fogo", "%s fire resistance"),
    (T + "stat.dodge", "%s de chance de esquivar", "%s dodge chance"),
    (T + "stat.mana_shield", "%s do dano recebido é pago com recurso", "%s of damage taken is paid with resource"),
    (T + "stat.thorns", "%s do dano corpo a corpo volta ao atacante", "%s of melee damage taken is reflected"),
    (T + "stat.second_wind", "Segundo Fôlego: %s de absorção com a vida baixa (a cada 2 min)",
     "Second Wind: %s absorption at low health (every 2 min)"),
    (T + "stat.regen_health", "%s de vida a cada 5 s fora de combate", "%s health every 5 s out of combat"),
    (T + "stat.healing_received", "%s de cura recebida", "%s healing received"),
    (T + "stat.low_health_power", "%s de dano com a vida abaixo de 35%%", "%s damage while below 35%% health"),
    (T + "stat.execute", "%s de dano em alvos abaixo de 30%% de vida", "%s damage to targets below 30%% health"),
    (T + "stat.first_strike", "%s de dano em alvos com a vida cheia", "%s damage to targets at full health"),
    (T + "stat.backstab", "%s de dano corpo a corpo pelas costas", "%s melee damage from behind"),
    (T + "stat.night_power", "%s de dano à noite", "%s damage at night"),
    (T + "stat.day_power", "%s de dano de dia", "%s damage during the day"),
    (T + "stat.undead_damage", "%s de dano em mortos-vivos", "%s damage to undead"),
    # ---- release 1.0: números
    (T + "stat.all_damage", "%s de todo dano causado", "%s all damage dealt"),
    (T + "stat.melee_damage", "%s de dano corpo a corpo", "%s melee damage"),
    (T + "stat.spell_damage_all", "%s de dano com todas as magias e técnicas", "%s damage with all spells and techniques"),
    (T + "stat.boss_damage", "%s de dano em chefes", "%s damage to bosses"),
    (T + "stat.near_damage", "%s de dano em alvos a até 5 blocos", "%s damage to targets within 5 blocks"),
    (T + "stat.far_damage", "%s de dano em alvos a 12 blocos ou mais", "%s damage to targets 12+ blocks away"),
    (T + "stat.afflicted_damage", "%s de dano em alvos em chamas ou com efeito negativo",
     "%s damage to burning or debuffed targets"),
    (T + "stat.high_mana_power", "%s de dano com o recurso acima de 75%%", "%s damage while above 75%% resource"),
    (T + "stat.full_health_power", "%s de dano com a vida acima de 90%%", "%s damage while above 90%% health"),
    (T + "stat.aim_power", "%s de dano agachado (mira firme)", "%s damage while sneaking (steady aim)"),
    (T + "stat.run_power", "%s de dano correndo", "%s damage while sprinting"),
    (T + "stat.low_health_resist", "%s de redução de dano com a vida abaixo de 35%%",
     "%s damage reduction while below 35%% health"),
    (T + "stat.full_health_resist", "%s de redução de dano com a vida acima de 90%%",
     "%s damage reduction while above 90%% health"),
    (T + "stat.boss_resist", "%s de redução do dano de chefes", "%s reduction of boss damage"),
    # ---- release 1.0: mecânicas
    (T + "stat.combo", "Sequência: cada golpe seguido no mesmo alvo dá %s de dano (até 5 vezes)",
     "Combo: each hit in a row on the same target adds %s damage (up to 5 times)"),
    (T + "stat.kill_fury", "Ao abater: %s de dano por 8 s (acumula 3 vezes)", "On kill: %s damage for 8 s (stacks 3 times)"),
    (T + "stat.crit_fury", "Ao acertar um crítico: %s de dano por 5 s", "On a critical hit: %s damage for 5 s"),
    (T + "stat.parry_power", "Ao aparar um golpe: seu próximo ataque causa %s de dano",
     "After a parry: your next attack deals %s damage"),
    (T + "stat.revenge", "Ao ser atingido: %s de dano por 4 s", "When you are hit: %s damage for 4 s"),
    (T + "stat.dodge_power", "Ao esquivar de um golpe: %s de dano por 5 s", "After dodging a hit: %s damage for 5 s"),
    (T + "stat.surge", "Sobrecarga: cada magia ou técnica usada dá %s de dano por 6 s (até 5 vezes)",
     "Overload: each spell or technique used adds %s damage for 6 s (up to 5 times)"),
    (T + "stat.ult_power", "Depois de usar a suprema (G): %s de dano por 15 s", "After your ultimate (G): %s damage for 15 s"),
    (T + "stat.summon_mark", "O alvo que você acerta fica marcado por 8 s: suas invocações causam %s de dano nele",
     "The target you hit is marked for 8 s: your summons deal %s damage to it"),
    (T + "stat.reckless", "Temerário: %s de dano causado, mas você recebe metade disso a mais",
     "Reckless: %s damage dealt, but you take half that much more"),
    (T + "stat.wild", "Aposta (%s): cada golpe sai um pouco mais fraco ou MUITO mais forte",
     "Gamble (%s): each hit lands a little weaker or MUCH stronger"),
    (T + "stat.fifth_hit", "Todo 5º golpe é crítico, com %s de dano crítico", "Every 5th hit is a critical with %s critical damage"),
    (T + "stat.echo", "Eco: %s de chance de a magia acertar de novo com metade da força",
     "Echo: %s chance for a spell hit to strike again at half strength"),
    (T + "stat.cull", "Execução: inimigos comuns abaixo de %s de vida morrem com o seu golpe",
     "Execution: common enemies below %s health die to your hit"),
    (T + "stat.kill_burst", "Ao abater: explosão de %s da vida máxima do morto nos inimigos em volta",
     "On kill: a blast for %s of the victim's max health hits nearby enemies"),
    (T + "stat.plague", "Ao abater um alvo com efeito negativo: %s de chance de os efeitos passarem aos inimigos em volta",
     "On killing a debuffed target: %s chance its effects spread to nearby enemies"),
    (T + "stat.crit_mana", "Ao acertar um crítico: %s de recurso", "On a critical hit: %s resource"),
    (T + "stat.crit_heal", "Ao acertar um crítico: %s de vida", "On a critical hit: %s health"),
    (T + "stat.crit_cdr", "Ao acertar um crítico: adianta suas recargas em %s s (1 vez por segundo)",
     "On a critical hit: your cooldowns advance by %s s (once per second)"),
    (T + "stat.kill_cdr", "Ao abater: adianta suas recargas em %s s", "On kill: your cooldowns advance by %s s"),
    (T + "stat.charge_on_kill", "Ao abater: %s de carga da suprema", "On kill: %s ultimate charge"),
    (T + "stat.cast_refund", "%s de chance de a magia devolver metade do custo", "%s chance for a spell to refund half its cost"),
    (T + "stat.heal_on_cast", "Ao usar magia ou técnica: %s de vida (1 vez por segundo)",
     "On using a spell or technique: %s health (once per second)"),
    (T + "stat.cast_ward", "Ao usar magia ou técnica: %s de redução de dano por 4 s",
     "On using a spell or technique: %s damage reduction for 4 s"),
    (T + "stat.guard_fortify", "Ao bloquear ou aparar: %s de redução de dano por 5 s",
     "On block or parry: %s damage reduction for 5 s"),
    (T + "stat.guard_reflect", "Ao bloquear ou aparar: devolve %s do dano segurado ao atacante",
     "On block or parry: returns %s of the damage held to the attacker"),
    (T + "stat.parry_heal", "Ao aparar: %s de vida", "On parry: %s health"),
    (T + "stat.parry_mana", "Ao aparar: %s de recurso", "On parry: %s resource"),
    (T + "stat.summon_bond", "Enquanto suas invocações lutam: %s de redução de dano",
     "While your summons are fighting: %s damage reduction"),
    (T + "stat.summon_lifesteal", "%s do dano das suas invocações vira vida para você",
     "%s of your summons' damage heals you"),
    (T + "stat.cheat_death", "Recusar a Morte: o golpe fatal é anulado e você fica com %s da vida (a cada 5 min)",
     "Refuse Death: the killing blow is cancelled and you are left at %s health (every 5 min)"),
    (T + "stat.barrier", "Barreira: fora de combate você refaz %s de absorção", "Barrier: out of combat you rebuild %s absorption"),
]


def check_percent(text, key):
    import re
    rest = re.sub(r"%(\d+\$)?s|%%", "", text)
    if "%" in rest:
        sys.exit("'%' solto em " + key + ": " + text)


def tab_icon():
    """Tres nos ligados (uma arvore pequena), dourado sobre contorno escuro."""
    c = Canvas(16, 16)
    link = (138, 112, 56, 255)
    gold = (255, 216, 107, 255)
    violet = (185, 140, 255, 255)
    c.line(8, 4, 4, 11, link)
    c.line(8, 4, 12, 11, link)
    c.line(7, 4, 3, 11, link)
    c.line(9, 4, 13, 11, link)
    c.circle(8, 4, 2.6, gold)
    c.shade_sphere(8, 4, 2.6)
    c.circle(4, 12, 2.3, violet)
    c.shade_sphere(4, 12, 2.3)
    c.circle(12, 12, 2.3, gold)
    c.shade_sphere(12, 12, 2.3)
    c.outline((24, 18, 38, 255))
    c.save(ICON)


def add_totals(totals, spec, scale):
    for key, amount in parse(spec):
        totals[key] = totals.get(key, 0.0) + amount * scale


def best_of(a, b):
    """Para o teto: de dois caminhos exclusivos, o pior caso de cada passiva."""
    out = dict(a)
    for k, v in b.items():
        out[k] = max(out.get(k, 0.0), v)
    return out


def main():
    pt, en = {}, {}

    def put(key, p, n):
        if key in pt:
            sys.exit("chave repetida: " + key)
        check_percent(p, key)
        check_percent(n, key)
        pt[key] = p
        en[key] = n

    for key, p, n in TEXT:
        put(key, p, n)

    # confere que as fileiras sao alcancaveis (o maximo de pontos acima de cada fileira cobre o que ela pede)
    above = 0
    for tier in range(len(TIER_LEVEL)):
        if TIER_GATE[tier] > above:
            sys.exit("fileira %d inalcancavel: pede %d, max acima %d" % (tier, TIER_GATE[tier], above))
        ranks = [m for (t, k, m) in SLOTS if t == tier]
        kinds = [k for (t, k, m) in SLOTS if t == tier]
        above += max(ranks) if "choice" in kinds else sum(ranks)
    branch_ranks = above
    spec_ranks = sum(s[0] for s in SPEC_SLOTS)
    acc = 0
    for (m, kind, lvl, gate) in SPEC_SLOTS:
        if gate > acc:
            sys.exit("fileira de especializacao inalcancavel")
        acc += m

    os.makedirs(DATA, exist_ok=True)
    sub_ids = set()
    worst = {}       # passiva -> (maior total, classe)
    budget = []
    for cls, spec in CLASSES.items():
        ids = set()
        branches, nodes = [], []
        class_totals = {}

        def node_id(nid):
            if nid in ids:
                sys.exit("%s: id de no repetido: %s" % (cls, nid))
            ids.add(nid)
            return nid

        if len(spec["branches"]) != 3 or len(spec["subclasses"]) != 2:
            sys.exit(cls + ": esperava 3 ramos e 2 especializacoes")
        for b, (bid, color, bpt, ben, mastery_fx, bnodes) in enumerate(spec["branches"]):
            if len(bnodes) != len(SLOTS):
                sys.exit("%s/%s: esperava %d nos, veio %d" % (cls, bid, len(SLOTS), len(bnodes)))
            branches.append({"id": bid, "color": color})
            put("%s%s.branch.%s" % (T, cls, bid), bpt, ben)
            choice_ids = [bnodes[i][0] for i, s in enumerate(SLOTS) if s[1] == "choice"]
            choice_totals = []
            for i, (nid, npt, nen, fx) in enumerate(bnodes):
                tier, kind, mx = SLOTS[i]
                node = {"id": node_id(nid), "branch": b, "tier": tier, "max": mx, "level": TIER_LEVEL[tier], "kind": kind,
                        "branch_points": TIER_GATE[tier], "icon": NODE_ICON[first_key(fx)], "effects": effects(fx)}
                if kind == "choice":
                    node["exclusive"] = [c for c in choice_ids if c != nid]
                    t = {}
                    add_totals(t, fx, mx)
                    choice_totals.append(t)
                else:
                    add_totals(class_totals, fx, mx)
                nodes.append(node)
                put("%s%s.%s" % (T, cls, nid), npt, nen)
            pick = {}
            for t in choice_totals:
                pick = best_of(pick, t)
            for k, v in pick.items():
                class_totals[k] = class_totals.get(k, 0.0) + v
            mid = node_id("mastery_" + bid)
            nodes.append({"id": mid, "branch": b, "tier": MASTERY_TIER, "max": MASTERY_MAX, "level": MASTERY_LEVEL,
                          "kind": "mastery", "branch_points": MASTERY_GATE, "curve": MASTERY_CURVE,
                          "icon": "minecraft:experience_bottle", "effects": effects(mastery_fx)})
            put("%s%s.%s" % (T, cls, mid), "Maestria: " + bpt, ben + " Mastery")
            add_totals(class_totals, mastery_fx, mastery_scale(MASTERY_MAX))

        subs = []
        sub_totals = {}
        for sid, color, icon, spt, sen, dpt, den, ppt, pen, fx, row in spec["subclasses"]:
            if not sid.startswith(cls + "_") or sid in sub_ids:
                sys.exit("id de especializacao ruim: " + sid)
            if len(row) != len(SPEC_SLOTS):
                sys.exit(sid + ": esperava %d nos na fileira" % len(SPEC_SLOTS))
            sub_ids.add(sid)
            subs.append({"id": sid, "color": color, "icon": icon, "effects": effects(fx)})
            put("subclass.isekai_mahou." + sid, spt, sen)
            put("subclass.isekai_mahou." + sid + ".desc", dpt, den)
            put("subclass.isekai_mahou." + sid + ".play", ppt, pen)
            t = {}
            add_totals(t, fx, 1)
            for i, (nid, npt, nen, nfx) in enumerate(row):
                mx, kind, lvl, gate = SPEC_SLOTS[i]
                nodes.append({"id": node_id(nid), "branch": 0, "tier": i, "max": mx, "level": lvl, "kind": kind,
                              "branch_points": gate, "subclass": sid, "icon": NODE_ICON[first_key(nfx)],
                              "effects": effects(nfx)})
                put("%s%s.%s" % (T, cls, nid), npt, nen)
                add_totals(t, nfx, mx)
            sub_totals = best_of(sub_totals, t)
        for k, v in sub_totals.items():
            class_totals[k] = class_totals.get(k, 0.0) + v
        class_totals["@resist"] = sum(v for k, v in class_totals.items() if k in RESIST_KEYS)
        class_totals["@bonus"] = sum(v for k, v in class_totals.items() if k in BONUS_KEYS)
        for k, v in class_totals.items():
            if k not in worst or v > worst[k][0]:
                worst[k] = (v, cls)

        with open(os.path.join(DATA, cls + ".json"), "w", encoding="utf-8") as f:
            json.dump({"layout": LAYOUT, "branches": branches, "nodes": nodes, "subclasses": subs}, f, indent=2,
                      ensure_ascii=False)
            f.write("\n")
        budget.append((cls, 3 * branch_ranks, spec_ranks, 3 * MASTERY_MAX, len(nodes)))

    os.makedirs(LANG, exist_ok=True)
    for name, data in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    tab_icon()
    print("talents: %d classes, %d especializacoes, %d chaves de traducao" % (len(CLASSES), len(sub_ids), len(pt)))

    # ---- tabela de orcamento
    p150, p200 = LEVEL_CAP // LEVELS_PER_POINT, HIDDEN_CAP // LEVELS_PER_POINT
    print("\nORCAMENTO (1 ponto a cada %d niveis): %d pontos no nivel %d, %d no nivel %d" %
          (LEVELS_PER_POINT, p150, LEVEL_CAP, p200, HIDDEN_CAP))
    print("%-13s %5s %6s %6s %7s %7s %9s %5s" % ("classe", "ramos", "espec.", "total", "%@" + str(LEVEL_CAP),
                                                 "%@" + str(HIDDEN_CAP), "maestria", "nos"))
    for cls, tree, srow, mastery, count in budget:
        total = tree + srow
        print("%-13s %5d %6d %6d %6d%% %6d%% %9d %5d" % (cls, tree, srow, total, round(100.0 * p150 / total),
                                                        round(100.0 * p200 / total), mastery, count))
    print("por ramo: %d niveis; no final pede %d pontos acima (3 finais = %d pontos > %d do nivel %d)" %
          (branch_ranks, TIER_GATE[-1], 3 * (TIER_GATE[-1] + 1), p150, LEVEL_CAP))

    if "--totals" in sys.argv:
        print("\nMAXIMO POSSIVEL de cada passiva (arvore cheia + melhor escolha + Maestria 50 + melhor especializacao):")
        for k in sorted(worst):
            v, cls = worst[k]
            cap = GROUP_CAPS.get(k[1:]) if k.startswith("@") else CAPS.get(k)
            note = "" if cap is None else ("   teto %g%s" % (cap, "  <-- CORTADO NO TETO" if v > cap + 1e-9 else ""))
            print("  %-22s %8.3f  (%s)%s" % (k, v, cls, note))


if __name__ == "__main__":
    main()
