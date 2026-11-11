"""Traducoes do pacote de tecnicas de anime do class_ranger (incluidas por class_ranger.py em write_lang)."""

# glifo -> ((nome PT, descricao PT), (nome EN, descricao EN))
GLYPHS = {
    "ranger_sacred_arrow": (
        ("Flecha Sagrada",
         "Efeito de flecha (origem do Ranger; homenagem à sacerdotisa arqueira que atravessou o poço do tempo). Luz rosa "
         "que PURIFICA: 7 de dano de Luz; criaturas impuras (mortos-vivos, Corrompidas) levam ×2,5, pegam fogo e perdem "
         "todos os efeitos bons. No impacto, uma onda de purificação (raio 3, Expandir) tira efeitos ruins dos aliados e "
         "cura um pouco. Em você ou num aliado: purifica e cura."),
        ("Sacred Arrow",
         "Arrow effect (Ranger origin; a tribute to the priestess archer who crossed the well of time). Pink light that "
         "PURIFIES: 7 Light damage; impure creatures (undead, Corrupted) take ×2.5, catch fire and lose every good effect. "
         "On impact, a purification wave (radius 3, Expand) clears bad effects from allies and heals a little. On you or "
         "an ally: purifies and heals.")),
    "ranger_triple_shot": (
        ("Disparo Triplo Élfico",
         "Forma (origem do Ranger; homenagem ao arqueiro élfico que atira correndo e saltando). Três rajadas seguidas de "
         "flechas mágicas (sem gastar flechas), cada uma mirando SOZINHA num inimigo diferente à sua frente (cone de 40°, "
         "32 blocos). Em movimento — correndo, no ar ou montado — cada rajada tem +25% de poder, e no ar você cai devagar "
         "para continuar atirando. Dividir soma flechas em cada rajada (custo por cópia)."),
        ("Elven Triple Shot",
         "Form (Ranger origin; a tribute to the elven archer who shoots while running and leaping). Three quick volleys "
         "of magic arrows (no arrows spent), each one aiming ON ITS OWN at a different enemy ahead (40° cone, 32 blocks). "
         "While moving — sprinting, airborne or mounted — each volley has +25% power, and in the air you float down to keep "
         "shooting. Split adds arrows to each volley (per-copy cost).")),
    "ranger_conjured_chain": (
        ("Correntes Conjuradas",
         "Efeito (origem do Ranger; homenagem ao caçador das correntes). Num inimigo: PRISÃO — 4 de dano, preso no lugar "
         "por uma corrente por 5 s (Estender), lento e sem conseguir se teletransportar, Enraizado e Silenciado por um "
         "instante — e ROUBO: tira 1 efeito bom (+1 por Amplificar) e passa para você; de jogadores rouba 10% da mana. Em "
         "você ou num aliado: CORRENTE SAGRADA — cura 6 e tira 2 efeitos ruins (+1 por Amplificar)."),
        ("Conjured Chains",
         "Effect (Ranger origin; a tribute to the chain hunter). On an enemy: PRISON — 4 damage, held in place by a chain "
         "for 5 s (Extend), slowed and unable to teleport, Rooted and Silenced for a moment — and THEFT: takes 1 good "
         "effect (+1 per Amplify) and gives it to you; steals 10% mana from players. On you or an ally: HOLY CHAIN — heals "
         "6 and clears 2 bad effects (+1 per Amplify).")),
    "ranger_bullet_line": (
        ("Tiro de Linha de Bala",
         "Forma (origem do Ranger; homenagem à atiradora de elite do jogo virtual). Mira com a LINHA DE BALA vermelha "
         "(o alvo vê a linha!) por 0,8 s (Acelerar encurta) e o tiro sai instantâneo, sem errar: acerta o alvo onde ele "
         "estiver, a não ser que um bloco fique no caminho. +2% de poder por bloco de distância (até ×2); Perfurar "
         "atravessa inimigos na linha; Dividir trava em mais alvos (custo por cópia). Alcance 96."),
        ("Bullet Line Shot",
         "Form (Ranger origin; a tribute to the virtual-game sniper). Aims with the red BULLET LINE (the target sees it!) "
         "for 0.8 s (Accelerate shortens it) and the shot fires instantly, never missing: it hits the target wherever it "
         "is, unless a block is in the way. +2% power per block of distance (up to ×2); Pierce goes through enemies on the "
         "line; Split locks onto more targets (per-copy cost). Range 96.")),
    "ranger_wolf_god": (
        ("Despertar da Deusa Loba",
         "Efeito (origem do Ranger, rank V; homenagem à loba gigante da floresta). O seu Lobo (chamado se precisar) "
         "desperta como a fera divina: branco, GIGANTE (×2,5), +80 de vida, mordida +14 em área, armadura e velocidade; "
         "uiva a cada 5 s aterrorizando os inimigos e dá Regeneração a você por perto. 30 s (Estender). Num inimigo: ela "
         "já desperta atacando."),
        ("Awakening of the Wolf Goddess",
         "Effect (Ranger origin, rank V; a tribute to the giant forest wolf). Your Wolf (called if needed) awakens as the "
         "divine beast: white, GIANT (×2.5), +80 health, +14 area bites, armor and speed; howls every 5 s to terrify "
         "enemies and grants you Regeneration nearby. 30 s (Extend). On an enemy: she awakens already attacking it.")),
}

PT = {
    "entity.isekai_mahou.ranger_chain_tether": "Corrente Conjurada",
    "entity.isekai_mahou.ranger_bullet_line": "Linha de Bala",
    "legend.isekai_mahou.ranger_bullet_line_hecate": "Hécate II: Linha de Bala",
    "legend.isekai_mahou.ranger_bullet_line_hecate.desc": "A Linha de Bala com Perfurar dobrado, Flecha Perfurante, poder amplificado e crítico: "
                                                          "um tiro que atravessa a fila inteira.",
    "legend.isekai_mahou.ranger_sacred_purification": "Flecha Sagrada: Purificação",
    "legend.isekai_mahou.ranger_sacred_purification.desc": "Uma Flecha Sagrada guiada, ampliada e amplificada: a onda de luz limpa o grupo "
                                                           "e queima os impuros.",
    "legend.isekai_mahou.ranger_wolf_goddess_fury": "Fúria da Deusa Loba",
    "legend.isekai_mahou.ranger_wolf_goddess_fury.desc": "Chama a matilha e desperta a Deusa Loba amplificada: a fera gigante da floresta "
                                                         "luta ao seu lado.",
    "announce.isekai_mahou.class_ranger.wolf_god": "DESPERTAR DA DEUSA LOBA!",
    "message.isekai_mahou.class_ranger.chain_bound": "Corrente: preso por %1$s s · %2$s efeito(s) roubado(s).",
    "message.isekai_mahou.class_ranger.wolf_god": "A Deusa Loba despertou por %s s!",
    "screen.isekai_mahou.class_ranger.reward_legends": "5 Lendas",
}
EN = {
    "entity.isekai_mahou.ranger_chain_tether": "Conjured Chain",
    "entity.isekai_mahou.ranger_bullet_line": "Bullet Line",
    "legend.isekai_mahou.ranger_bullet_line_hecate": "Hecate II: Bullet Line",
    "legend.isekai_mahou.ranger_bullet_line_hecate.desc": "The Bullet Line with double Pierce, Piercing Arrow, amplified power and critical: "
                                                          "a shot through the whole line.",
    "legend.isekai_mahou.ranger_sacred_purification": "Sacred Arrow: Purification",
    "legend.isekai_mahou.ranger_sacred_purification.desc": "A guided, enlarged and amplified Sacred Arrow: the wave of light cleanses the "
                                                           "party and burns the impure.",
    "legend.isekai_mahou.ranger_wolf_goddess_fury": "Fury of the Wolf Goddess",
    "legend.isekai_mahou.ranger_wolf_goddess_fury.desc": "Calls the pack and awakens the amplified Wolf Goddess: the giant beast of the "
                                                         "forest fights by your side.",
    "announce.isekai_mahou.class_ranger.wolf_god": "AWAKENING OF THE WOLF GODDESS!",
    "message.isekai_mahou.class_ranger.chain_bound": "Chain: bound for %1$s s · %2$s effect(s) stolen.",
    "message.isekai_mahou.class_ranger.wolf_god": "The Wolf Goddess awakened for %s s!",
    "screen.isekai_mahou.class_ranger.reward_legends": "5 Legends",
}
