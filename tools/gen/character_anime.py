# -*- coding: utf-8 -*-
"""v1.7 — raças "casadas" com animes (textos). Aplica por cima de src/main/lang/character/{pt_br,en_us}.json.

Slime = Rimuru (Tensura: Predador + Grande Sábio, Despertar do Lorde Demônio) · Aracnídeo = Kumoko (Kumo desu ga: fios,
armadilhas, Olho Maligno, evolução até Arachne) · Dracônico = Dragon Slayer (Fairy Tail; sabor de Veldora) · Meio-Orc =
Geld (Tensura: Faminto, Desastre Orc) · Demônio = Anos Voldigoad (Maou Gakuin: Olhos da Destruição, Jio Graze) · Elfo =
Frieren (Mana Oculta).

Idempotente: pode rodar quantas vezes quiser. ATENÇÃO: o tools/gen/character.py regenera os JSON a partir dos próprios
dicionários (e já perde chaves de outros scripts) — depois dele, rode também este.

Uso: py -I tools/gen/character_anime.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LANG = os.path.join(ROOT, "src", "main", "lang", "character")

C = "character.isekai_mahou."
M = "message.isekai_mahou.character."
S = "screen.isekai_mahou.character."

# chave: (pt_br, en_us)
T = {
    # ------------------------------------------------------------------ raças (tela de escolha)
    "race.isekai_mahou.slime.desc": (
        "Reencarnado como uma gotinha de geleia — igual ao Rimuru, de Tensura. Regenera, quica e, com o Predador, devora "
        "criaturas para copiar as habilidades delas, guiado pela voz do Grande Sábio. Subestimado... até despertar como "
        "Lorde Demônio.",
        "Reincarnated as a little drop of jelly — just like Rimuru, from Tensura. Regenerates, bounces and, with Predator, "
        "devours creatures to copy their abilities, guided by the voice of the Great Sage. Underestimated... until it "
        "awakens as a Demon Lord."),
    "race.isekai_mahou.arachnid.desc": (
        "Renascida como a menor aranha do Grande Labirinto — igual à Kumoko, de Kumo desu ga. Escala paredes, lança "
        "fios, arma armadilhas e, evoluindo, ganha os Olhos Malignos. Começa como Pequena Taratect Menor... e termina "
        "como Arachne.",
        "Reborn as the smallest spider in the Great Labyrinth — just like Kumoko, from Kumo desu ga. Climbs walls, shoots "
        "threads, sets traps and, as it evolves, gains the Evil Eyes. Starts as a Small Lesser Taratect... and ends as an "
        "Arachne."),
    "race.isekai_mahou.dragonborn.desc": (
        "Sangue de dragão e a magia dos Dragon Slayers (Fairy Tail): comem o elemento da ancestralidade para recuperar "
        "vida e mana, rugem como dragões e, no auge, liberam a Dragon Force. Dizem que um dragão da tempestade, preso há "
        "séculos, ainda ri dentro dessa linhagem.",
        "Dragon blood and the magic of the Dragon Slayers (Fairy Tail): they eat their ancestry's element to recover "
        "health and mana, roar like dragons and, at their peak, unleash Dragon Force. They say a storm dragon, sealed for "
        "centuries, still laughs inside this bloodline."),
    "race.isekai_mahou.half_orc.desc": (
        "Sangue da Horda — e a fome de Geld, o Lorde Orc de Tensura. Quanto mais come e devora, mais forte fica; aguenta "
        "o que mataria qualquer outro e, no fim, desperta como o Desastre Orc.",
        "Blood of the Horde — and the hunger of Geld, the Orc Lord from Tensura. The more it eats and devours, the "
        "stronger it gets; it endures what would kill anyone else and, in the end, awakens as the Orc Disaster."),
    "race.isekai_mahou.demon.desc": (
        "Herdeiros do Rei Demônio da Tirania — Anos Voldigoad, de Maou Gakuin. Chifres, asas de sombra, o fogo do Abismo "
        "e Olhos Mágicos que destroem magias. Confiantes ao ponto do absurdo... e com motivo.",
        "Heirs of the Demon King of Tyranny — Anos Voldigoad, from Maou Gakuin. Horns, shadow wings, Abyssal fire and "
        "Magic Eyes that destroy spells. Confident to an absurd degree... and with good reason."),
    "race.isekai_mahou.elf.desc": (
        "Filhos das florestas antigas, de sentidos aguçados e mira impecável. Enxergam no escuro, a lua guia as suas "
        "flechas e, como Frieren, aprendem a esconder a própria mana.",
        "Children of the ancient forests, with keen senses and flawless aim. They see in the dark, the moon guides their "
        "arrows and, like Frieren, they learn to hide their own mana."),

    # ------------------------------------------------------------------ passivas
    C + "passive.slime": ("Predador", "Predator"),
    C + "passive.slime.lv1": (
        "Regenera +1 de vida a cada 5 s. Predador: abata criaturas (aranha, lobo, morcego, creeper, blaze, criaturas da "
        "água, zumbi) — com 3 amostras o Grande Sábio copia uma habilidade delas. Guarda 2.",
        "Regenerates +1 health every 5 s. Predator: slay creatures (spider, wolf, bat, creeper, blaze, water creatures, "
        "zombie) — after 3 samples the Great Sage copies one of their abilities. Holds 2."),
    C + "passive.slime.lv2": (
        "Regenera a cada 4 s, −25% de dano de queda e quica ao cair de alto (Shift = não quica). Guarda 3 habilidades.",
        "Regenerates every 4 s, −25% fall damage and bounces back when falling from high (Shift = no bounce). Holds 3 "
        "abilities."),
    C + "passive.slime.lv3": (
        "Regenera a cada 3 s, −50% de dano de queda e quem te golpeia gruda em você (Lentidão). Guarda 4 habilidades.",
        "Regenerates every 3 s, −50% fall damage and whoever hits you gets stuck to you (Slowness). Holds 4 abilities."),
    C + "passive.slime.lv4": (
        "Regenera +2 a cada 3 s, −75% de dano de queda, +2 de armadura e +20% de firmeza. Guarda 5 habilidades (6 no "
        "nível V).",
        "Regenerates +2 every 3 s, −75% fall damage, +2 armor and +20% steadiness. Holds 5 abilities (6 at level V)."),

    C + "passive.arachnid": ("Fios e Veneno", "Threads and Venom"),
    C + "passive.arachnid.lv1": (
        "Visão no escuro, escala paredes DEVAGAR (Shift gruda) e Fio de Aranha (Shift + tecla racial): um fio de 12 "
        "blocos te puxa até a parede — ou puxa o inimigo até você.",
        "Darkvision, climbs walls SLOWLY (Shift clings) and Spider Thread (Shift + racial key): a 12-block thread pulls "
        "you to the wall — or pulls the enemy to you."),
    C + "passive.arachnid.lv2": (
        "Escala mais rápido, golpes envenenam (Veneno I, 2 s), fio de 16 blocos e Armadilha de Fios: agache parado 2 s "
        "para deixar um fio invisível que prende e envenena (2 ao mesmo tempo).",
        "Climbs faster, hits poison (Poison I, 2 s), 16-block thread and Thread Trap: crouch still for 2 s to leave an "
        "invisible thread that roots and poisons (2 at a time)."),
    C + "passive.arachnid.lv3": (
        "Veneno por 3 s, fio de 20 blocos, 3 armadilhas e Olho Maligno da Paralisia: a cada 6 s o inimigo que você "
        "encara fica quase parado.",
        "Poison lasts 3 s, 20-block thread, 3 traps and Paralysis Evil Eye: every 6 s the enemy you stare at almost "
        "freezes."),
    C + "passive.arachnid.lv4": (
        "Escala rápido, Veneno II, +5% de velocidade, fio de 24 blocos e Olho Maligno da Maldição: a cada 4 s, Paralisia "
        "+ Corrompido.",
        "Climbs fast, Poison II, +5% speed, 24-block thread and Curse Evil Eye: every 4 s, Paralysis + Corrupted."),

    C + "passive.dragonborn": ("Dragon Slayer", "Dragon Slayer"),
    C + "passive.dragonborn.lv1": (
        "−15% de dano de fogo, +1 de queda segura e Devorar Elemento (Shift + tecla racial): coma o elemento da sua "
        "ancestralidade por perto para recuperar 3 de vida, 15% de mana e fome (recarga 30 s).",
        "−15% fire damage, +1 safe fall and Devour Element (Shift + racial key): eat your ancestry's element nearby to "
        "recover 3 health, 15% mana and hunger (30 s cooldown)."),
    C + "passive.dragonborn.lv2": (
        "+8% com magias da ancestralidade e −15% das magias inimigas desse elemento. Devorar: 4 de vida, 20% de mana e "
        "Saciado por 10 s (+15% com magias da ancestralidade).",
        "+8% with ancestry spells and −15% from enemy spells of that element. Devour: 4 health, 20% mana and Sated for "
        "10 s (+15% with ancestry spells)."),
    C + "passive.dragonborn.lv3": (
        "−25% de dano de fogo, +12% / −25% no elemento da ancestralidade e +2 de queda segura. Devorar: 5 de vida, 25% "
        "de mana, recarga 20 s.",
        "−25% fire damage, +12% / −25% in your ancestry's element and +2 safe fall. Devour: 5 health, 25% mana, 20 s "
        "cooldown."),
    C + "passive.dragonborn.lv4": (
        "+15% com magias da ancestralidade e +2 de armadura (escamas). Devorar: 6 de vida e 30% de mana.",
        "+15% with ancestry spells and +2 armor (scales). Devour: 6 health and 30% mana."),

    C + "passive.half_orc": ("Faminto", "Starved"),
    C + "passive.half_orc.lv1": (
        "+3% de dano corpo a corpo. Faminto: comer ou abater corpo a corpo dá Fome Voraz (+2% de dano corpo a corpo "
        "cada, 30 s; até 2) e abater devora a presa (+1 de vida e de fome).",
        "+3% melee damage. Starved: eating or a melee kill grants Ravenous Hunger (+2% melee damage each, 30 s; up to "
        "2) and a kill devours the prey (+1 health and hunger)."),
    C + "passive.half_orc.lv2": (
        "+5% de dano corpo a corpo, +4 de vida máxima e Fome Voraz até 3.",
        "+5% melee damage, +4 max health and Ravenous Hunger up to 3."),
    C + "passive.half_orc.lv3": (
        "+6% de dano corpo a corpo, +6 de vida, Fome Voraz até 4 e Implacável: 1× a cada 5 min um golpe fatal te deixa "
        "com 1 de vida. Abaixo de 35% de vida, +20% de dano corpo a corpo.",
        "+6% melee damage, +6 health, Ravenous Hunger up to 4 and Relentless: once every 5 min a fatal blow leaves you "
        "at 1 health. Below 35% health, +20% melee damage."),
    C + "passive.half_orc.lv4": (
        "+8% de dano corpo a corpo, +8 de vida máxima e Fome Voraz até 5.",
        "+8% melee damage, +8 max health and Ravenous Hunger up to 5."),

    C + "passive.demon": ("Sangue do Rei Demônio", "Demon King's Blood"),
    C + "passive.demon.lv4": (
        "+15% com magias de fogo, −25% de magias de fogo inimigas e Olhos Mágicos da Destruição: 15% de chance de "
        "destruir uma magia inimiga antes que ela te acerte (1× a cada 20 s).",
        "+15% with fire spells, −25% from enemy fire spells and Magic Eyes of Destruction: 15% chance to destroy an "
        "enemy spell before it hits you (once every 20 s)."),

    C + "passive.elf.lv3": (
        "+12% de dano com projéteis, +12% de regeneração de recurso e Mana Oculta: agachado, a sua mana some (monstros "
        "longe perdem o rastro; o 1º golpe surpreende).",
        "+12% projectile damage, +12% resource regeneration and Hidden Mana: while crouching your mana vanishes (distant "
        "monsters lose your trail; the first hit is a surprise)."),

    # ------------------------------------------------------------------ Transformações (nível V)
    C + "form.slime": ("Despertar do Lorde Demônio", "Demon Lord Awakening"),
    C + "form.slime.desc": (
        "Como Rimuru no Festival da Colheita: assume a forma humanoide de olhos dourados — regeneração enorme (3+ de "
        "vida por segundo), +20 de vida, imune a empurrões e a queda, pulo maior e −15% de dano; golpes fortes soltam "
        "gosmas-clone que explodem em ácido e cada criatura devorada é analisada na hora (1 amostra basta).",
        "Like Rimuru at the Harvest Festival: takes a golden-eyed humanoid form — huge regeneration (3+ health per "
        "second), +20 health, immune to knockback and falls, higher jumps and −15% damage; strong hits split off "
        "clone-blobs that burst into acid and every devoured creature is analyzed at once (1 sample is enough)."),
    C + "form.slime.power": ("Predação", "Predation"),
    C + "form.slime.power_desc": (
        "engole os inimigos menores em volta (puxa, dano alto e cura metade do dano) — quem morrer vira habilidade "
        "copiada; os grandes levam ácido e Lentidão.",
        "swallows the smaller enemies around you (pull, high damage and heals half of it) — whoever dies becomes a "
        "copied ability; big ones take acid and Slowness."),

    C + "form.arachnid": ("Arachne", "Arachne"),
    C + "form.arachnid.desc": (
        "A evolução final de Kumoko: corpo de aranha com oito patas — escala paredes rápido, +20% de velocidade e de "
        "ataque, golpes com Veneno II e Lentidão, cai 12 blocos sem se machucar, Fio de Aranha de 28 blocos a cada "
        "1,5 s, 4 armadilhas e o Olho Maligno a cada 2 s (às vezes Petrifica).",
        "Kumoko's final evolution: a spider body with eight legs — climbs walls fast, +20% speed and attack speed, hits "
        "with Poison II and Slowness, falls 12 blocks unharmed, a 28-block Spider Thread every 1.5 s, 4 traps and the "
        "Evil Eye every 2 s (sometimes Petrifies)."),

    C + "form.dragonborn": ("Dragon Force", "Dragon Force"),
    C + "form.dragonborn.desc": (
        "O auge de um Dragon Slayer: asas de dragão na cor da ancestralidade (VOO PLANADO, como élitros), +8 de "
        "armadura, +25% de dano corpo a corpo, 30% maior e −25% de dano recebido; os golpes corpo a corpo carregam o "
        "elemento e Devorar Elemento recarrega em 6 s. Planando, a tecla racial vira Mergulho Dracônico (olhando para "
        "baixo: cratera em área) ou Sopro Rasante (olhando reto).",
        "A Dragon Slayer's peak: dragon wings in your ancestry's color (GLIDING, like elytra), +8 armor, +25% melee "
        "damage, 30% larger and −25% damage taken; melee hits carry the element and Devour Element recharges in 6 s. "
        "While gliding, the racial key becomes Draconic Dive (looking down: area crater) or Strafing Breath (looking "
        "ahead)."),
    C + "form.dragonborn.power": ("Rugido do Dragão", "Dragon's Roar"),
    C + "form.dragonborn.power_desc": (
        "o rugido do Dragon Slayer: cone de 10 blocos no elemento da ancestralidade (fogo queima, água gela, terra "
        "petrifica, vento empurra, luz expõe, sombra corrompe).",
        "the Dragon Slayer's roar: a 10-block cone in your ancestry's element (fire burns, water chills, earth "
        "petrifies, wind pushes, light exposes, shadow corrupts)."),

    C + "form.half_orc": ("Desastre Orc", "Orc Disaster"),
    C + "form.half_orc.desc": (
        "A evolução de Geld, o Lorde Orc: aura rubra, +50% de dano corpo a corpo, +25% de velocidade de ataque, +15% de "
        "velocidade, 20% do dano corpo a corpo vira vida e a Fome Voraz vai até 8 — mas leva +10% de dano.",
        "Geld the Orc Lord's evolution: a crimson aura, +50% melee damage, +25% attack speed, +15% speed, 20% of melee "
        "damage heals you and Ravenous Hunger goes up to 8 — but you take +10% damage."),
    C + "form.half_orc.power": ("Fome Insaciável", "Insatiable Hunger"),
    C + "form.half_orc.power_desc": (
        "devora a força de quem está num raio de 8: dano, Fracos, Lentos e Expostos; 25% do dano vira vida, cada presa "
        "enche a Fome Voraz e os aliados ganham Força.",
        "devours the strength of everyone within 8 blocks: damage, Weak, Slow and Exposed; 25% of the damage heals you, "
        "each prey fills Ravenous Hunger and allies gain Strength."),

    C + "form.demon": ("Rei Demônio da Tirania", "Demon King of Tyranny"),
    C + "form.demon.desc": (
        "O poder de Anos Voldigoad: asas de sombra (VOO PLANADO, como élitros; mergulho = Queda do Abismo), imune a "
        "fogo, +30% de dano corpo a corpo e +20% de Poder Mágico; os golpes queimam com chamas negras, uma aura do "
        "Abismo fere quem chega perto e os Olhos da Destruição desfazem magias com o dobro de chance.",
        "The power of Anos Voldigoad: shadow wings (GLIDING, like elytra; dive = Abyssal Plunge), immune to fire, +30% "
        "melee damage and +20% Spell Power; your hits burn with black flames, an Abyssal aura hurts whoever gets close "
        "and the Eyes of Destruction undo spells with twice the chance."),
    C + "form.demon.power": ("Jio Graze", "Jio Graze"),
    C + "form.demon.power_desc": (
        "um sol negro voa até o ponto mirado (20 blocos) e explode num raio de 4,5: dano alto, Em Chamas e Corrompido.",
        "a black sun flies to the aimed spot (20 blocks) and explodes in a 4.5 radius: high damage, Burning and "
        "Corrupted."),

    # ------------------------------------------------------------------ feitos
    C + "feat.slime": (
        "Devorar %s coisas (comidas, ou criaturas que o Predador copia).",
        "Devour %s things (food, or creatures Predator can copy)."),
    C + "feat.arachnid": (
        "Sobreviver ao Labirinto: emboscar %s criaturas no subterrâneo (sem céu acima).",
        "Survive the Labyrinth: ambush %s creatures underground (no sky above)."),

    # ------------------------------------------------------------------ estágios de evolução
    C + "stage.arachnid.lv1": ("Pequena Taratect Menor", "Small Lesser Taratect"),
    C + "stage.arachnid.lv2": ("Pequena Taratect Venenosa", "Small Poison Taratect"),
    C + "stage.arachnid.lv3": ("Zoa Ele", "Zoa Ele"),
    C + "stage.arachnid.lv4": ("Zana Horowa", "Zana Horowa"),
    C + "stage.arachnid.lv5": ("Arachne", "Arachne"),
    C + "stage.slime.lv1": ("Slime", "Slime"),
    C + "stage.slime.lv2": ("Slime Nomeado", "Named Slime"),
    C + "stage.slime.lv3": ("Slime Demônio", "Demon Slime"),
    C + "stage.slime.lv4": ("Candidato a Lorde Demônio", "Demon Lord Candidate"),
    C + "stage.slime.lv5": ("Lorde Demônio", "Demon Lord"),
    C + "stage.half_orc.lv1": ("Orc", "Orc"),
    C + "stage.half_orc.lv2": ("Orc Superior", "High Orc"),
    C + "stage.half_orc.lv3": ("General Orc", "Orc General"),
    C + "stage.half_orc.lv4": ("Lorde Orc", "Orc Lord"),
    C + "stage.half_orc.lv5": ("Desastre Orc", "Orc Disaster"),

    # ------------------------------------------------------------------ traços (tela de escolha e aba)
    C + "trait.slime_predator": ("Predador", "Predator"),
    C + "trait.slime_predator.desc": (
        "Abata criaturas para copiar habilidades: Fio Pegajoso (aranha), Passo do Lobo Tempestade (lobo), Ultrassom "
        "(morcego), Explosão Gelatinosa (creeper), Resistência a Chamas (blaze), Respiração Aquática (criaturas da água) "
        "e Resistência à Podridão (zumbi). Cheio, esquece a mais antiga.",
        "Slay creatures to copy abilities: Sticky Thread (spider), Tempest Wolf Step (wolf), Ultrasound (bat), Jelly "
        "Burst (creeper), Flame Resistance (blaze), Water Breathing (water creatures) and Rot Resistance (zombie). When "
        "full, the oldest is forgotten."),
    C + "trait.slime_sage": ("Grande Sábio", "Great Sage"),
    C + "trait.slime_sage.desc": (
        "Uma voz analítica na sua cabeça: avisa quando uma análise começa ou termina e quando a vida fica crítica. "
        "Shift + tecla racial: relatório das habilidades copiadas.",
        "An analytical voice in your head: it speaks when an analysis starts or ends and when your health turns "
        "critical. Shift + racial key: report of your copied abilities."),
    C + "trait.arachnid_thread": ("Fio de Aranha", "Spider Thread"),
    C + "trait.arachnid_thread.desc": (
        "Shift + tecla racial: um fio te puxa até o bloco mirado (subir, cruzar abismos) ou puxa o inimigo mirado até "
        "você. Sem dano de queda logo depois.",
        "Shift + racial key: a thread pulls you to the aimed block (climb, cross chasms) or pulls the aimed enemy to "
        "you. No fall damage right after."),
    C + "trait.arachnid_trap": ("Armadilha de Fios", "Thread Trap"),
    C + "trait.arachnid_trap.desc": (
        "Do nível de raça II: agache parado 2 s para deixar um fio invisível no chão (dura 60 s). O 1º inimigo que pisar "
        "fica Enraizado e Envenenado.",
        "From race level II: crouch still for 2 s to leave an invisible thread on the ground (lasts 60 s). The first "
        "enemy to step on it is Rooted and Poisoned."),
    C + "trait.arachnid_evil_eye": ("Olho Maligno", "Evil Eye"),
    C + "trait.arachnid_evil_eye.desc": (
        "Do nível de raça III: o inimigo que você encara é Paralisado de tempos em tempos; do IV também Amaldiçoado; "
        "como Arachne, às vezes Petrificado.",
        "From race level III: the enemy you stare at is Paralyzed every few seconds; from IV also Cursed; as an Arachne, "
        "sometimes Petrified."),
    C + "trait.arachnid_eyes.desc": (
        "Quatro olhos a mais na testa: enxerga no escuro (tecla racial ou a aba liga/desliga).",
        "Four extra eyes on your forehead: see in the dark (racial key or the tab toggles it)."),
    C + "trait.dragon_eat": ("Devorar Elemento", "Devour Element"),
    C + "trait.dragon_eat.desc": (
        "Shift + tecla racial: coma o seu elemento por perto — fogo (fogo, lava, magma, fogueira), água (água ou chuva), "
        "terra (pedra, terra, ferro), vento (céu aberto), luz (sol ou luz forte), sombra (escuridão). Cura, recupera "
        "mana e fome.",
        "Shift + racial key: eat your element nearby — fire (fire, lava, magma, campfire), water (water or rain), earth "
        "(stone, dirt, iron), wind (open sky), light (sun or bright light), shadow (darkness). Heals, restores mana and "
        "hunger."),
    C + "trait.orc_starved": ("Faminto", "Starved"),
    C + "trait.orc_starved.desc": (
        "Comer ou abater corpo a corpo enche a Fome Voraz (+2% de dano corpo a corpo cada, 30 s). Abater devora a "
        "presa: +1 de vida e de fome.",
        "Eating or a melee kill fills Ravenous Hunger (+2% melee damage each, 30 s). A kill devours the prey: +1 health "
        "and hunger."),
    C + "trait.demon_destruction_eyes": ("Olhos da Destruição", "Eyes of Destruction"),
    C + "trait.demon_destruction_eyes.desc": (
        "Do nível de raça IV: 15% de chance de destruir uma magia inimiga antes que ela te acerte (1× a cada 20 s; 30% "
        "como Rei Demônio).",
        "From race level IV: 15% chance to destroy an enemy spell before it hits you (once every 20 s; 30% as Demon "
        "King)."),
    C + "trait.elf_conceal": ("Mana Oculta", "Hidden Mana"),
    C + "trait.elf_conceal.desc": (
        "Do nível de raça III: agachado, a sua mana some — monstros longe perdem o rastro e Detectar Mana não te vê. O "
        "1º golpe revela tudo num ataque surpresa (depois, 10 s para esconder de novo).",
        "From race level III: while crouching your mana vanishes — distant monsters lose your trail and Mana Sense can't "
        "see you. The first hit reveals everything in a surprise attack (then 10 s to hide again)."),

    # ------------------------------------------------------------------ habilidades copiadas (Predador)
    C + "mimic.spider": ("Fio Pegajoso", "Sticky Thread"),
    C + "mimic.spider.desc": ("escala paredes", "climbs walls"),
    C + "mimic.spider.source": ("Aranha", "Spider"),
    C + "mimic.wolf": ("Passo do Lobo Tempestade", "Tempest Wolf Step"),
    C + "mimic.wolf.desc": ("+6% de velocidade", "+6% speed"),
    C + "mimic.wolf.source": ("Lobo", "Wolf"),
    C + "mimic.bat": ("Ultrassom", "Ultrasound"),
    C + "mimic.bat.desc": ("enxerga no escuro", "sees in the dark"),
    C + "mimic.bat.source": ("Morcego", "Bat"),
    C + "mimic.creeper": ("Explosão Gelatinosa", "Jelly Burst"),
    C + "mimic.creeper.desc": ("abaixo de 30% de vida, explode em ácido (1× a cada 60 s)",
                               "below 30% health, bursts into acid (once every 60 s)"),
    C + "mimic.creeper.source": ("Creeper", "Creeper"),
    C + "mimic.blaze": ("Resistência a Chamas", "Flame Resistance"),
    C + "mimic.blaze.desc": ("−40% de dano de fogo", "−40% fire damage"),
    C + "mimic.blaze.source": ("Blaze ou Cubo de Magma", "Blaze or Magma Cube"),
    C + "mimic.aquatic": ("Respiração Aquática", "Water Breathing"),
    C + "mimic.aquatic.desc": ("respira debaixo d'água", "breathes underwater"),
    C + "mimic.aquatic.source": ("Afogado, Guardião ou Lula", "Drowned, Guardian or Squid"),
    C + "mimic.zombie": ("Resistência à Podridão", "Rot Resistance"),
    C + "mimic.zombie.desc": ("imune a Fome e Veneno", "immune to Hunger and Poison"),
    C + "mimic.zombie.source": ("Zumbi", "Zombie"),

    # ------------------------------------------------------------------ mensagens
    M + "sage": ("《Grande Sábio》 %s", "《Great Sage》 %s"),
    M + "sage_analyzing": ("Analisando: %s (%s/%s). Devore mais amostras.",
                           "Analyzing: %s (%s/%s). Devour more samples."),
    M + "sage_learned": ("Análise concluída. Habilidade adquirida: %s — %s.",
                         "Analysis complete. Ability acquired: %s — %s."),
    M + "sage_dropped": ("Capacidade cheia (%2$s): %1$s foi descartada.",
                         "Capacity full (%2$s): %1$s was discarded."),
    M + "sage_warning": ("Aviso: dano crítico. Recomendo recuar.", "Warning: critical damage. I recommend retreating."),
    M + "sage_report": ("Relatório do Predador — %s/%s habilidades:", "Predator report — %s/%s abilities:"),
    M + "sage_targets": ("  Ainda não copiados: %s", "  Not copied yet: %s"),
    M + "sage_all": ("  Todas as habilidades conhecidas foram copiadas.", "  Every known ability has been copied."),
    M + "slime_burst": ("Explosão Gelatinosa!", "Jelly Burst!"),
    M + "thread_cooldown": ("Fio de Aranha: %s s", "Spider Thread: %s s"),
    M + "thread_miss": ("Nada ao alcance para prender o fio.", "Nothing in reach to anchor the thread."),
    M + "trap_set": ("Armadilha de Fios armada (%s/%s).", "Thread Trap set (%s/%s)."),
    M + "evolved": ("Evolução: %s", "Evolution: %s"),
    M + "dragon_eat": ("Você devorou o elemento: %s!", "You devoured the element: %s!"),
    M + "dragon_eat_cooldown": ("Ainda digerindo... %s s", "Still digesting... %s s"),
    M + "dragon_eat_none": ("Nada para devorar aqui — procure %s.", "Nothing to devour here — look for %s."),
    M + "dragon_eat_where.fire": ("fogo, lava, magma ou uma fogueira acesa", "fire, lava, magma or a lit campfire"),
    M + "dragon_eat_where.water": ("água ou chuva", "water or rain"),
    M + "dragon_eat_where.earth": ("pedra, terra ou ferro", "stone, dirt or iron"),
    M + "dragon_eat_where.wind": ("o céu aberto", "the open sky"),
    M + "dragon_eat_where.light": ("o sol ou uma luz forte", "the sun or a bright light"),
    M + "dragon_eat_where.shadow": ("um lugar bem escuro", "a really dark place"),
    M + "orc_starved": ("Fome Voraz no máximo: +%s%% de dano corpo a corpo!", "Ravenous Hunger maxed: +%s%% melee damage!"),
    M + "demon_nullify": ("Olhos da Destruição — %s", "Eyes of Destruction — %s"),
    M + "demon_boast.0": ("\"Uma magia dessas não alcança um Rei Demônio.\"", "\"A spell like that can't reach a Demon King.\""),
    M + "demon_boast.1": ("\"Conheça o seu lugar.\"", "\"Know your place.\""),
    M + "demon_boast.2": ("\"Hm. Era só isso?\"", "\"Hm. Was that all?\""),
    M + "demon_boast.3": ("\"Nem precisei olhar duas vezes.\"", "\"I didn't even need a second look.\""),
    M + "demon_boss.0": ("\"Conheça o seu lugar.\"", "\"Know your place.\""),
    M + "demon_boss.1": ("\"Hm. Achei que duraria mais.\"", "\"Hm. I thought you'd last longer.\""),
    M + "demon_boss.2": ("\"Até a morte obedece a um Rei Demônio.\"", "\"Even death obeys a Demon King.\""),

    # ------------------------------------------------------------------ aba Raça
    S + "stage": ("Estágio: %s", "Stage: %s"),
    S + "predator": ("Predador: %s/%s habilidades", "Predator: %s/%s abilities"),
    S + "predator_none": ("Nenhuma ainda — abata criaturas para o Grande Sábio analisar.",
                          "None yet — slay creatures for the Great Sage to analyze."),
    S + "key_shift.arachnid": ("Shift + tecla racial: Fio de Aranha.", "Shift + racial key: Spider Thread."),
    S + "key_shift.slime": ("Shift + tecla racial: relatório do Grande Sábio.", "Shift + racial key: Great Sage report."),
    S + "key_shift.dragonborn": ("Shift + tecla racial: Devorar Elemento.", "Shift + racial key: Devour Element."),
}


def main():
    for i, name in enumerate(("pt_br.json", "en_us.json")):
        path = os.path.join(LANG, name)
        with open(path, "rb") as f:
            raw = f.read()
        nl = "\r\n" if b"\r\n" in raw else "\n"  # mantém o fim de linha do arquivo
        data = json.loads(raw.decode("utf-8"))
        for k, v in T.items():
            data[k] = v[i]
        with open(path, "w", encoding="utf-8", newline=nl) as f:
            json.dump(dict(sorted(data.items())), f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(name, len(data), "chaves")


if __name__ == "__main__":
    main()
