"""Traducoes do pacote de tecnicas de anime do class_enchanter (importado por class_enchanter_lang.py)."""

PT = {}
EN = {}


def both(key, pt, en):
    PT[key] = pt
    EN[key] = en


# ---------------------------------------------------------------- glifos
GLYPHS = {
    "eight_gates": (
        "Oito Portões Internos", "Eight Inner Gates",
        "Transformação (origem do Encantador, só em você). Cada conjuração abre o PRÓXIMO portão (+1 por Amplificar): "
        "Abertura, Descanso, Vida, Dor, Limite, Visão, Assombro e Morte. Cada portão dá muito poder (dano corpo a corpo "
        "+10% → +160%, velocidade, ataque, técnicas +6% por portão) e custa vida ao abrir (4% → 15%); do 4º em diante a "
        "vida drena por segundo e do 6º os socos soltam uma onda em área. Limite: Lição 3 portões; Encantador 3 + rank (os "
        "8 no rank V). O 8º dura 10 s e explode em volta; ao fechar você fica com 1 de vida, exausto, e os portões travam "
        "por 60 s — nunca mata. 20 s (Estender). Inverter: sela os portões do inimigo (fraqueza, lentidão, Exposto).",
        "Transformation (Enchanter origin, self only). Each cast opens the NEXT gate (+1 per Amplify): Opening, Healing, "
        "Life, Pain, Limit, View, Wonder and Death. Each gate grants huge power (melee damage +10% → +160%, speed, attack, "
        "techniques +6% per gate) and costs health to open (4% → 15%); from the 4th on, health drains every second and "
        "from the 6th your punches release an area shockwave. Limit: Lesson 3 gates; Enchanter 3 + rank (all 8 at rank V). "
        "The 8th lasts 10 s and explodes around you; when it closes you are left at 1 health, exhausted, and the gates "
        "lock for 60 s — it never kills. 20 s (Extend). Invert: seals the enemy's gates (weakness, slowness, Exposed)."),
    "kaioken": (
        "Kaioken", "Kaioken",
        "Transformação (origem do Encantador, só em você). Uma aura vermelha multiplica o corpo: cada conjuração sobe um "
        "degrau (+1 por Amplificar) — ×2, ×3 (rank III) e ×10 (rank V). Dano corpo a corpo ×2/×3/×10, técnicas ×1,5/×2/×5,5 "
        "e mais velocidade; custa 5%/8%/15% da vida ao subir e DRENA 2%/4%/10% da vida por segundo. Se a vida chegar a 1, o "
        "corpo não aguenta: o Kaioken quebra e vem a exaustão. 15 s (×10: 5 s; Estender). Inverter: superaquece o inimigo "
        "(dano e Queimando).",
        "Transformation (Enchanter origin, self only). A red aura multiplies the body: each cast climbs a step (+1 per "
        "Amplify) — ×2, ×3 (rank III) and ×10 (rank V). Melee damage ×2/×3/×10, techniques ×1.5/×2/×5.5 and more speed; "
        "costs 5%/8%/15% of health to climb and DRAINS 2%/4%/10% of health per second. If health reaches 1, the body gives "
        "out: Kaioken breaks and exhaustion follows. 15 s (×10: 5 s; Extend). Invert: overheats the enemy (damage and "
        "Burning)."),
    "enchanter_gear_second": (
        "Segunda Marcha", "Second Gear",
        "Transformação (origem do Encantador, só em você; homenagem ao pirata de borracha). O sangue bombeado mais rápido "
        "vira vapor: velocidade +35%, ataque +40%, dano corpo a corpo +25%, pulo e queda segura (Amplificar soma). Custa 4% "
        "da vida e mais 1% a cada 2 s. 20 s (Estender; o Encantador dura mais). Troca a Quarta Marcha. Inverter: jato de "
        "vapor escaldante (dano e cegueira).",
        "Transformation (Enchanter origin, self only; a tribute to the rubber pirate). Blood pumped faster turns into "
        "steam: speed +35%, attack speed +40%, melee damage +25%, jump and safe fall (Amplify adds). Costs 4% of health "
        "plus 1% every 2 s. 20 s (Extend; longer for the Enchanter). Replaces Fourth Gear. Invert: a jet of scalding "
        "steam (damage and blindness)."),
    "enchanter_gear_fourth": (
        "Quarta Marcha: Homem-Mola", "Fourth Gear: Boundman",
        "Transformação (origem do Encantador, só em você). O corpo INFLA: tamanho ×1,6, dano corpo a corpo +120%, armadura "
        "+8, firmeza, +20 de vida (cura na hora), alcance +1,5 blocos e pulo de mola; cada soco é um canhão que acerta "
        "todos em volta (60%). Precisa de 15% do recurso e drena 3% por segundo; ao acabar, exaustão. 20 s (Estender). "
        "Troca a Segunda Marcha. Inverter: um soco de canhão no inimigo (dano alto e arremesso).",
        "Transformation (Enchanter origin, self only). The body INFLATES: size ×1.6, melee damage +120%, armor +8, "
        "toughness, +20 health (healed at once), reach +1.5 blocks and a spring jump; every punch is a cannon that hits "
        "everyone around (60%). Needs 15% of your resource and drains 3% per second; exhaustion afterwards. 20 s (Extend). "
        "Replaces Second Gear. Invert: a cannon punch on the enemy (high damage and launch)."),
    "ultra_instinct": (
        "Instinto Superior", "Ultra Instinct",
        "Transformação (origem do Encantador; em você ou num aliado). Aura prateada: o corpo esquiva SOZINHO de 3 ataques "
        "(+2 por Amplificar; Encantador +1 +rank/2, máx. 12) — golpes, projéteis e magias com autor —, desviando para o "
        "lado. Depois de esquivar, o próximo golpe em 2 s é um contra-ataque (+50%). Chefes acertam 50% das vezes. 8 s "
        "(Estender; Encantador +10% por rank). Inverter: turva os instintos do inimigo (náusea, lentidão, fraqueza).",
        "Transformation (Enchanter origin; on you or an ally). Silver aura: the body dodges 3 attacks ON ITS OWN (+2 per "
        "Amplify; Enchanter +1 +rank/2, max 12) — hits, projectiles and spells with an author — sidestepping them. After "
        "a dodge, the next hit within 2 s is a counter (+50%). Bosses still land 50% of the time. 8 s (Extend; Enchanter "
        "+10% per rank). Invert: clouds the enemy's instincts (nausea, slowness, weakness)."),
}
for k, (pt, en, dpt, den) in GLYPHS.items():
    both(f"glyph.isekai_mahou.{k}", pt, en)
    both(f"glyph.isekai_mahou.{k}.desc", dpt, den)

# ---------------------------------------------------------------- efeitos
for k, (pt, en) in {
    "enchanter_eight_gates": ("Portões Abertos", "Open Gates"),
    "enchanter_kaioken": ("Kaioken", "Kaioken"),
    "enchanter_gear_second": ("Segunda Marcha", "Second Gear"),
    "enchanter_gear_fourth": ("Quarta Marcha", "Fourth Gear"),
    "enchanter_ultra_instinct": ("Instinto Superior", "Ultra Instinct"),
}.items():
    both(f"effect.isekai_mahou.{k}", pt, en)

# ---------------------------------------------------------------- lendas
both("legend.isekai_mahou.enchanter_death_gate", "Portão da Morte: Noite do Dragão", "Gate of Death: Night of the Dragon")
both("legend.isekai_mahou.enchanter_death_gate.desc",
     "Abre quatro portões de uma vez, com Força: no rank V do Rito chega ao Oitavo Portão — 10 s de poder máximo e a onda "
     "que derruba tudo em volta. Depois, 1 de vida.",
     "Opens four gates at once, with Might: at Rite rank V it reaches the Eighth Gate — 10 s of maximum power and the "
     "shockwave that topples everything around. Then, 1 health.")
both("legend.isekai_mahou.enchanter_kaioken_x10", "Kaioken ×10: Punho do Rei", "Kaioken ×10: King's Fist")
both("legend.isekai_mahou.enchanter_kaioken_x10.desc",
     "Sobe o Kaioken três degraus de uma vez (×10 no rank V) junto com a Sobrecarga. Cinco segundos para acabar a luta.",
     "Climbs Kaioken three steps at once (×10 at rank V) together with Overdrive. Five seconds to end the fight.")
both("legend.isekai_mahou.enchanter_boundman", "Quarta Marcha: Homem-Mola", "Fourth Gear: Boundman")
both("legend.isekai_mahou.enchanter_boundman.desc",
     "O corpo inflado de músculos com a Égide por cima e mais tempo: punhos de canhão que acertam todos em volta.",
     "The muscle-inflated body with Aegis on top and extra time: cannon fists that hit everyone around.")

# ---------------------------------------------------------------- anuncios
both("announce.isekai_mahou.enchanter.gate", "PORTÃO %s: %s!", "GATE %s: %s!")
both("announce.isekai_mahou.enchanter.kaioken", "KAIOKEN ×%s!", "KAIOKEN ×%s!")
both("announce.isekai_mahou.enchanter.gear_second", "SEGUNDA MARCHA!", "SECOND GEAR!")
both("announce.isekai_mahou.enchanter.gear_fourth", "QUARTA MARCHA: HOMEM-MOLA!", "FOURTH GEAR: BOUNDMAN!")
both("announce.isekai_mahou.enchanter.ultra", "INSTINTO SUPERIOR", "ULTRA INSTINCT")

# ---------------------------------------------------------------- mensagens
GATES = (("Portão da Abertura", "Gate of Opening"), ("Portão do Descanso", "Gate of Healing"), ("Portão da Vida", "Gate of Life"),
         ("Portão da Dor", "Gate of Pain"), ("Portão do Limite", "Gate of Limit"), ("Portão da Visão", "Gate of View"),
         ("Portão do Assombro", "Gate of Wonder"), ("Portão da Morte", "Gate of Death"))
for i, (pt, en) in enumerate(GATES, 1):
    both(f"message.isekai_mahou.enchanter.gate_name.{i}", pt, en)
msgs = {
    "gate_open": ("Portão %s — %s aberto! (−%s% de vida)", "Gate %s — %s opened! (−%s% health)"),
    "gates_cap": ("Seu corpo só aguenta %s portões por enquanto (suba o Rito de Inscrição).",
                  "Your body can only take %s gates for now (raise the Rite of Inscription)."),
    "gates_max": ("Todos os portões já estão abertos.", "Every gate is already open."),
    "gates_locked": ("Os portões estão travados: o corpo precisa de mais %s s.", "The gates are locked: your body needs %s more s."),
    "gates_end": ("Os portões se fecharam: exaustão.", "The gates closed: exhaustion."),
    "death_gate_end": ("O Portão da Morte se fechou: o corpo chegou ao limite (1 de vida). Portões travados por 60 s.",
                       "The Gate of Death closed: your body hit its limit (1 health). Gates locked for 60 s."),
    "kaioken_on": ("Kaioken ×%s: corpo a corpo ×%s, técnicas ×%s — a vida drena %s% por segundo.",
                   "Kaioken ×%s: melee ×%s, techniques ×%s — health drains %s% per second."),
    "kaioken_cap": ("Seu corpo não passa do Kaioken ×%s (suba o Rito).", "Your body can't go past Kaioken ×%s (raise the Rite)."),
    "kaioken_break": ("O corpo não aguenta! O Kaioken se desfez.", "Your body gives out! Kaioken breaks."),
    "gear_second_on": ("Segunda Marcha: o sangue virou vapor por %s s.", "Second Gear: your blood turned to steam for %s s."),
    "gear_fourth_on": ("Quarta Marcha: corpo inflado por %s s — cada soco é um canhão!",
                       "Fourth Gear: inflated body for %s s — every punch is a cannon!"),
    "gear_fourth_tired": ("Recurso insuficiente para a Quarta Marcha (precisa de 15%).",
                          "Not enough resource for Fourth Gear (needs 15%)."),
    "gear_fourth_out": ("Sem recurso: a Quarta Marcha se desfez.", "Out of resource: Fourth Gear deflates."),
    "gear_fourth_end": ("A Quarta Marcha acabou: o corpo murcha, exausto.", "Fourth Gear ended: your body deflates, exhausted."),
    "ultra_on": ("Instinto Superior: %s esquivas automáticas por %s s.", "Ultra Instinct: %s automatic dodges for %s s."),
    "ultra_dodge": ("Esquiva! (%s restantes) — contra-ataque pronto.", "Dodge! (%s left) — counter ready."),
    "ultra_end": ("O Instinto Superior se apagou.", "Ultra Instinct faded."),
    "self_only_transform": ("Esta transformação só funciona em você mesmo.", "This transformation only works on yourself."),
}
for k, (pt, en) in msgs.items():
    both(f"message.isekai_mahou.enchanter.{k}", pt, en)
