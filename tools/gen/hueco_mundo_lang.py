"""Traducoes (pt_br + en_us) do modulo hueco_mundo. Chamado por tools/gen/hueco_mundo.py."""
import os

from hueco_mundo_lib import ROOT, write_json

M = "message.isekai_mahou.hueco_mundo."
G = "glyph.isekai_mahou."

L = {
    # ---------------------------------------------------------------- blocos
    "block.isekai_mahou.hollow_sand": ("Areia Oca", "Hollow Sand"),
    "block.isekai_mahou.hollow_sandstone": ("Arenito Oco", "Hollow Sandstone"),
    "block.isekai_mahou.hollow_sandstone_bricks": ("Tijolos de Arenito Oco", "Hollow Sandstone Bricks"),
    "block.isekai_mahou.hollow_crystal": ("Cristal de Quartzo Lunar", "Moon Quartz Crystal"),
    "block.isekai_mahou.garganta_altar": ("Altar da Garganta", "Garganta Altar"),
    "block.isekai_mahou.garganta_altar.hint": (
        "À noite, ofereça uma Essência das Sombras ou um Fragmento de Máscara: o céu se rasga até o Mundo Oco.",
        "At night, offer a Shadow Essence or a Mask Fragment: the sky tears open to the Hollow World."),
    "block.isekai_mahou.fey_glowcap": ("Chapéu-Luz", "Glowcap"),
    "block.isekai_mahou.fey_ring_stone": ("Pedra do Círculo das Fadas", "Fairy Ring Stone"),
    "block.isekai_mahou.fey_ring_stone.hint": (
        "À noite, ofereça Essência da Luz, Pó de Fada ou uma Pétala-da-Lua: o Círculo leva ao Reino das Fadas.",
        "At night, offer Light Essence, Fairy Dust or a Moonpetal: the Ring leads to the Fey Realm."),
    "block.isekai_mahou.moonpetal": ("Pétala-da-Lua", "Moonpetal"),
    # ---------------------------------------------------------------- itens
    "item.isekai_mahou.hollow_mask_fragment": ("Fragmento de Máscara de Vazio", "Hollow Mask Fragment"),
    "item.isekai_mahou.menos_core": ("Núcleo de Menos", "Menos Core"),
    "item.isekai_mahou.arrancar_soul_shard": ("Fragmento de Alma de Arrancar", "Arrancar Soul Shard"),
    "item.isekai_mahou.arrancar_soul_shard.desc": (
        "Shinigami e Quincy absorvem: XP e recurso cheio. Material da Chave da Garganta.",
        "Shinigami and Quincy absorb it: XP and a full resource bar. Ingredient of the Garganta Key."),
    "item.isekai_mahou.fey_dust": ("Pó de Fada", "Fairy Dust"),
    "item.isekai_mahou.bramble_heart": ("Coração de Espinheiro", "Bramble Heart"),
    "item.isekai_mahou.reishi_crystal": ("Cristal de Reishi", "Reishi Crystal"),
    "item.isekai_mahou.reishi_crystal.desc": (
        "Restaura 30% do recurso da classe (Shinigami e Quincy: 50% e Força).",
        "Restores 30% of your class resource (Shinigami and Quincy: 50% and Strength)."),
    "item.isekai_mahou.garganta_key": ("Chave da Garganta", "Garganta Key"),
    "item.isekai_mahou.garganta_key.desc": (
        "Rasga uma Garganta à sua frente: do Mundo Normal ao Mundo Oco, ou de volta. Some ao usar.",
        "Tears a Garganta in front of you: from the Overworld to the Hollow World, or back. Consumed on use."),
    "item.isekai_mahou.decay_axe": ("Machado da Decadência", "Axe of Decay"),
    "item.isekai_mahou.decay_axe.desc": (
        "Do Rei Vazio. Todo golpe envelhece o alvo: definhar e lentidão.",
        "From the Hollow King. Every hit ages the target: wither and slowness."),
    "item.isekai_mahou.tide_sand_blade": ("Lâmina da Maré de Areia", "Sand Tide Blade"),
    "item.isekai_mahou.tide_sand_blade.desc": (
        "Da Imperatriz das Areias. Botão direito: Onda Areia-Azul em linha (dano, empurrão, cegueira).",
        "From the Sand Empress. Right-click: a Blue-Sand Wave in a line (damage, knockback, blindness)."),
    "item.isekai_mahou.queen_wings": ("Asas da Rainha", "Queen's Wings"),
    "item.isekai_mahou.queen_wings.desc": (
        "Da Rainha dos Espinhos. Abra as asas: 30 s de queda lenta, salto e velocidade. Conserte com Pó de Fada.",
        "From the Thorn Queen. Spread them: 30 s of slow falling, jump and speed. Repair with Fairy Dust."),
    # ---------------------------------------------------------------- criaturas
    "entity.isekai_mahou.hollow_crawler": ("Vazio Rastejante", "Hollow Crawler"),
    "entity.isekai_mahou.hollow_brute": ("Vazio Brutamontes", "Hollow Brute"),
    "entity.isekai_mahou.hollow_wraith": ("Vazio Alado", "Winged Hollow"),
    "entity.isekai_mahou.menos_giant": ("Menos Gigante", "Menos Giant"),
    "entity.isekai_mahou.arrancar": ("Arrancar", "Arrancar"),
    "entity.isekai_mahou.arrancar.name.0": ("Grael, a Pantera Azul", "Grael, the Blue Panther"),
    "entity.isekai_mahou.arrancar.name.1": ("Ulvar, o Lamento Verde", "Ulvar, the Green Lament"),
    "entity.isekai_mahou.arrancar.name.2": ("Nelva, a Lança de Cervo", "Nelva, the Stag Lance"),
    "entity.isekai_mahou.arrancar.name.3": ("Varg, o Lobo Solitário", "Varg, the Lone Wolf"),
    "entity.isekai_mahou.arrancar.name.4": ("Kessra, o Louva-a-Deus", "Kessra, the Mantis"),
    "entity.isekai_mahou.fey_pixie": ("Fada Travessa", "Mischievous Pixie"),
    "entity.isekai_mahou.bramble_hound": ("Cão de Espinhos", "Bramble Hound"),
    "entity.isekai_mahou.hollow_king": ("Rei Vazio", "Hollow King"),
    "entity.isekai_mahou.sand_empress": ("Imperatriz das Areias", "Sand Empress"),
    "entity.isekai_mahou.thorn_queen": ("Rainha dos Espinhos", "Thorn Queen"),
    "entity.isekai_mahou.realm_gate": ("Portal do Reino", "Realm Gate"),
    "entity.isekai_mahou.hm_bolt": ("Rajada Espiritual", "Spirit Bolt"),
    # ---------------------------------------------------------------- dimensoes, biomas e lugares
    "dimension.isekai_mahou.hollow_world": ("Mundo Oco", "Hollow World"),
    "dimension.isekai_mahou.fey_realm": ("Reino das Fadas", "Fey Realm"),
    "biome.isekai_mahou.hollow_dunes": ("Dunas Brancas", "White Dunes"),
    "biome.isekai_mahou.quartz_forest": ("Floresta de Quartzo", "Quartz Forest"),
    "biome.isekai_mahou.bone_wastes": ("Ermo dos Ossos", "Bone Wastes"),
    "biome.isekai_mahou.fey_glade": ("Clareira das Fadas", "Fey Glade"),
    "biome.isekai_mahou.glowcap_hollow": ("Vale dos Chapéus-Luz", "Glowcap Hollow"),
    "place.isekai_mahou.hueco_mundo.hub_hollow_world": ("Mirante da Garganta", "Garganta Lookout"),
    "place.isekai_mahou.hueco_mundo.hub_fey_realm": ("Portão da Corte", "Court Gate"),
    # v1.3: o antigo Palácio da Noite Eterna virou o Portão de Las Noches (o lugar inteiro se chama Las Noches).
    "place.isekai_mahou.hueco_mundo.hollow_palace": ("Las Noches", "Las Noches"),
    "place.isekai_mahou.hueco_mundo.fey_court": ("Corte das Fadas", "Fey Court"),
    "cutscene.isekai_mahou.hueco_mundo.arrive.hollow_world": (
        "Areia branca, lua que nunca se põe... e algo faminto observando.",
        "White sand, a moon that never sets... and something hungry watching."),
    "cutscene.isekai_mahou.hueco_mundo.arrive.fey_realm": (
        "O crepúsculo que nunca termina. Cuidado com as risadinhas.",
        "The dusk that never ends. Beware the giggles."),
    # ---------------------------------------------------------------- mensagens
    M + "hollow_world.need_night": ("A Garganta só se abre sob a lua: volte à noite.",
                                    "The Garganta only opens under the moon: come back at night."),
    M + "hollow_world.need_offering": ("Ofereça uma Essência das Sombras ou um Fragmento de Máscara de Vazio.",
                                       "Offer a Shadow Essence or a Hollow Mask Fragment."),
    M + "hollow_world.ritual": ("O céu começa a rachar...", "The sky begins to crack..."),
    M + "hollow_world.opened": ("A Garganta se abriu! Entre no rasgo.", "The Garganta is open! Step into the rift."),
    M + "fey_realm.need_night": ("As fadas só dançam à noite.", "The fairies only dance at night."),
    M + "fey_realm.need_offering": ("Ofereça Essência da Luz, Pó de Fada ou uma Pétala-da-Lua.",
                                    "Offer Light Essence, Fairy Dust or a Moonpetal."),
    M + "fey_realm.ritual": ("Risadinhas ecoam ao redor do círculo...", "Giggles echo around the ring..."),
    M + "fey_realm.opened": ("O Círculo das Fadas se abriu!", "The Fairy Ring is open!"),
    M + "gate_already_open": ("Já há um portal aberto aqui.", "There is already a gate open here."),
    M + "return_open": ("O caminho de volta se abriu.", "The way back is open."),
    M + "shortcut_wrong_dim": ("Só dá para rasgar a Garganta no Mundo Normal ou dentro do Mundo Oco.",
                               "A Garganta can only be torn in the Overworld or inside the Hollow World."),
    M + "shortcut_cooldown": ("O atalho ainda está se recompondo: %s s.", "The shortcut is still recovering: %s s."),
    M + "not_in_realm": ("Você não está em nenhum dos reinos.", "You are not in one of the realms."),
    M + "rebuilt": ("Lugares do reino reconstruídos.", "Realm places rebuilt."),
    M + "boss_returns": ("%s voltou ao seu trono!", "%s has returned to the throne!"),
    M + "reishi_spiritual": ("O reishi flui por você! (+50%% e Força)", "Reishi flows through you! (+50%% and Strength)"),
    M + "reishi_other": ("O reishi restaura um pouco do seu poder.", "The reishi restores some of your power."),
    M + "soul_shard_other": ("Só Shinigami e Quincy absorvem almas. (Serve de material.)",
                             "Only Shinigami and Quincy can absorb souls. (It is still a crafting material.)"),
    M + "soul_shard_absorbed": ("Você absorveu a alma do Arrancar!", "You absorbed the Arrancar's soul!"),
    M + "shortcut_learned.shinigami": (
        "Sua Zanpakutō aprendeu a abrir o Portão Senkai: um glifo que rasga o caminho até o Mundo Oco (Grimório).",
        "Your Zanpakuto learned to open the Senkai Gate: a glyph that tears a path to the Hollow World (Grimoire)."),
    M + "shortcut_learned.quincy": (
        "Você aprendeu a Ponte de Reishi: um glifo que rasga o caminho até o Mundo Oco (Grimório).",
        "You learned the Reishi Bridge: a glyph that tears a path to the Hollow World (Grimoire)."),
    # ---------------------------------------------------------------- chefes
    "boss.isekai_mahou.hollow_king.subtitle": ("Rei do Portão de Las Noches", "King of the Gate of Las Noches"),
    "boss.isekai_mahou.hollow_king.phase1": ("Ressurreição — Coroa do Fim", "Resurrection — Crown of the End"),
    "boss.isekai_mahou.hollow_king.phase2": ("Respira", "Respira"),
    "announce.isekai_mahou.hollow_king.intro": ("Ajoelhem-se. Tudo envelhece diante do rei.", "Kneel. All things age before the king."),
    "announce.isekai_mahou.hollow_king.phase1": ("Ressurreição! Coroa do Fim!", "Resurrection! Crown of the End!"),
    "announce.isekai_mahou.hollow_king.phase2": ("Respirem a minha decadência.", "Breathe in my decay."),
    "announce.isekai_mahou.hollow_king.respira": ("RESPIRA!", "RESPIRA!"),
    "announce.isekai_mahou.hollow_king.death": ("Até... um rei... vira pó...", "Even... a king... turns to dust..."),
    "message.isekai_mahou.hollow_king.resurrect": ("O Rei virou só ossos: está mais rápido!", "The King is bare bones now: he is faster!"),
    "message.isekai_mahou.hollow_king.respira": ("Respira! Corra para as zonas verdes!", "Respira! Run to the green zones!"),
    # v1.3: golpes novos (Ruína do Trono, Maremoto das Dunas, Valsa das Pétalas).
    "announce.isekai_mahou.hollow_king.ruin": ("Curvem-se diante da RUÍNA DO TRONO!", "Bow before the RUIN OF THE THRONE!"),
    "message.isekai_mahou.hollow_king.ruin": ("Ruína do Trono: as ondas saem do machado — pise onde a onda JÁ passou!",
                                               "Ruin of the Throne: waves spread from the axe — step where the wave ALREADY passed!"),
    "announce.isekai_mahou.sand_empress.tsunami": ("Maremoto das Dunas!", "Dune Tsunami!"),
    "message.isekai_mahou.sand_empress.tsunami": ("Uma muralha de areia vem aí: corra para a brecha verde!",
                                                   "A wall of sand is coming: run to the green gap!"),
    "announce.isekai_mahou.thorn_queen.waltz": ("Dancem comigo... a Valsa das Pétalas!", "Dance with me... the Petal Waltz!"),
    "message.isekai_mahou.thorn_queen.waltz": ("Valsa das Pétalas: os braços de pétalas giram — pule por cima ou afaste-se!",
                                                "Petal Waltz: the petal arms are spinning — jump over them or back off!"),
    "message.isekai_mahou.hollow_king.breath": ("Sopro da Senescência: saia do cone!", "Breath of Senescence: get out of the cone!"),
    "message.isekai_mahou.hollow_king.legion": ("A Legião de Ossos desperta!", "The Bone Legion awakens!"),
    "message.isekai_mahou.hollow_king.pools": ("Poças da Decadência seguem você — não pare!", "Pools of Decay follow you — keep moving!"),
    "message.isekai_mahou.hollow_king.exhausted": ("O Rei está exausto: ataque agora!", "The King is exhausted: strike now!"),
    "boss.isekai_mahou.sand_empress.subtitle": ("Senhora do Jardim das Areias", "Mistress of the Sand Garden"),
    "boss.isekai_mahou.sand_empress.phase1": ("Ressurreição — Tubarão das Dunas", "Resurrection — Dune Shark"),
    "boss.isekai_mahou.sand_empress.phase2": ("Tempestade Real", "Royal Storm"),
    "announce.isekai_mahou.sand_empress.intro": ("Afoguem-se nas minhas areias.", "Drown in my sands."),
    "announce.isekai_mahou.sand_empress.phase1": ("Ressurreição: Tubarão das Dunas!", "Resurrection: Dune Shark!"),
    "announce.isekai_mahou.sand_empress.phase2": ("Que a tempestade engula vocês!", "Let the storm swallow you!"),
    "announce.isekai_mahou.sand_empress.storm": ("Tempestade Real!", "Royal Storm!"),
    "announce.isekai_mahou.sand_empress.death": ("A maré... recua...", "The tide... recedes..."),
    "message.isekai_mahou.sand_empress.dive": ("Ela mergulhou na areia — fuja do círculo que te segue!",
                                               "She dove into the sand — run from the circle chasing you!"),
    "message.isekai_mahou.sand_empress.storm": ("Tempestade de areia: fique perto dos aliados!", "Sandstorm: stay close to your allies!"),
    "message.isekai_mahou.sand_empress.cascade": ("Cascata das Dunas: saia dos círculos!", "Dune Cascade: leave the circles!"),
    "message.isekai_mahou.sand_empress.whirl": ("Redemoinho: não deixe ele te puxar para o centro!",
                                                "Whirlpool: don't let it drag you to the center!"),
    "message.isekai_mahou.sand_empress.cross": ("Marés em cruz: fique entre as linhas!", "Crossing tides: stand between the lines!"),
    "boss.isekai_mahou.thorn_queen.subtitle": ("Morgwen, Soberana da Corte das Fadas", "Morgwen, Sovereign of the Fey Court"),
    "boss.isekai_mahou.thorn_queen.phase1": ("Chuva de Sementes", "Seed Rain"),
    "boss.isekai_mahou.thorn_queen.phase2": ("Veredito da Lua", "Moon's Verdict"),
    "announce.isekai_mahou.thorn_queen.intro": ("Quem pisa na minha corte sem ser convidado?", "Who treads my court uninvited?"),
    "announce.isekai_mahou.thorn_queen.phase1": ("Floresçam, minhas sementes!", "Bloom, my seeds!"),
    "announce.isekai_mahou.thorn_queen.phase2": ("A lua dará o veredito!", "The moon shall pass its verdict!"),
    "announce.isekai_mahou.thorn_queen.verdict": ("Veredito da Lua!", "Moon's Verdict!"),
    "announce.isekai_mahou.thorn_queen.death": ("O crepúsculo... enfim... termina...", "The dusk... at last... ends..."),
    "message.isekai_mahou.thorn_queen.charm": ("Encanto da Rainha: afaste-se das marcas!", "Queen's Charm: move away from the marks!"),
    "message.isekai_mahou.thorn_queen.court": ("A Corte Travessa veio defender a rainha!", "The Mischievous Court defends its queen!"),
    "message.isekai_mahou.thorn_queen.seeds": ("Sementes caem ao seu redor: não fique parado!", "Seeds fall around you: don't stand still!"),
    "message.isekai_mahou.thorn_queen.verdict": ("Veredito da Lua: entre nos círculos verdes ou vire estátua!",
                                                 "Moon's Verdict: get into the green circles or turn to stone!"),
    # ---------------------------------------------------------------- glifos
    G + "hollow_cero": ("Cero", "Cero"),
    G + "hollow_cero.desc": ("Efeito (Sombra): feixe concentrado da mão até o alvo — dano alto num alvo só, pequena explosão e empurrão.",
                             "Effect (Shadow): a focused beam from your hand to the target — heavy single-target damage, a small blast and knockback."),
    G + "hollow_gran_cero": ("Cero Real", "Royal Cero"),
    G + "hollow_gran_cero.desc": ("Efeito lendário (Sombra, Círculo 7): o Cero do Rei — feixe dourado enorme e explosão que fere todos em volta (Expandir aumenta).",
                                  "Legendary effect (Shadow, Circle 7): the King's Cero — a huge golden beam and a blast hitting everyone around (Expand widens it)."),
    G + "hollow_sonido": ("Sonído", "Sonido"),
    G + "hollow_sonido.desc": ("Efeito (Vento): passo instantâneo — o alvo dispara para a frente até 7 blocos (Amplificar alcança mais), deixando pós-imagens. Em Si Mesmo: esquiva.",
                               "Effect (Wind): instant step — the target dashes up to 7 blocks forward (Amplify goes further), leaving afterimages. On Self: a dodge."),
    G + "hollow_hierro": ("Pele de Ferro", "Iron Skin"),
    G + "hollow_hierro.desc": ("Efeito (Terra): a pele endurece — Resistência (II com Amplificar) por 10 s, um pouco mais lento.",
                               "Effect (Earth): the skin hardens — Resistance (II with Amplify) for 10 s, slightly slower."),
    G + "sand_tide": ("Maré de Areia", "Sand Tide"),
    G + "sand_tide.desc": ("Efeito (Terra): uma onda de areia branca atinge o alvo — dano, empurrão e cegueira curta.",
                           "Effect (Earth): a wave of white sand hits the target — damage, knockback and brief blindness."),
    G + "garganta_senkai": ("Portão Senkai", "Senkai Gate"),
    G + "garganta_senkai.desc": ("Efeito de origem do Shinigami: rasga um portal para o Mundo Oco onde a magia acerta (em Si Mesmo, à sua frente); dentro dele, abre a volta. Recarga de 2 min.",
                                 "Shinigami origin effect: tears a gate to the Hollow World where the spell lands (on Self, in front of you); inside it, opens the way back. 2 min cooldown."),
    G + "garganta_reishi": ("Ponte de Reishi", "Reishi Bridge"),
    G + "garganta_reishi.desc": ("Efeito de origem do Quincy: tece uma ponte de reishi até o Mundo Oco onde a magia acerta (em Si Mesmo, à sua frente); dentro dele, abre a volta. Recarga de 2 min.",
                                 "Quincy origin effect: weaves a reishi bridge to the Hollow World where the spell lands (on Self, in front of you); inside it, opens the way back. 2 min cooldown."),
    G + "visored_mask": ("Máscara Interior", "Inner Mask"),
    G + "visored_mask.desc": ("Efeito de origem do Shinigami (Sombra): puxa a máscara do Vazio interior — Força II, Velocidade e Resistência por 12 s; depois, fome.",
                              "Shinigami origin effect (Shadow): pulls on the inner Hollow's mask — Strength II, Speed and Resistance for 12 s; hunger afterwards."),
    G + "reishi_rend": ("Rasgo de Reishi", "Reishi Rend"),
    G + "reishi_rend.desc": ("Efeito de origem do Quincy (Luz): reishi que desfaz espíritos — dano dobrado em Vazios; cada acerto devolve 2% do recurso ao Quincy.",
                             "Quincy origin effect (Light): reishi that unmakes spirits — double damage to Hollows; each hit returns 2% of the resource to the Quincy."),
    G + "fey_thornbind": ("Laço de Espinhos", "Thornbind"),
    G + "fey_thornbind.desc": ("Efeito (Terra): roseira brota sob o alvo — dano, enraizado e sangramento.",
                               "Effect (Earth): a rosebush bursts under the target — damage, root and bleeding."),
    G + "fey_charm": ("Encanto Feérico", "Fey Charm"),
    G + "fey_charm.desc": ("Efeito (Luz): enfeitiça o alvo — monstros esquecem você e atacam o monstro mais próximo; jogadores ficam tontos. Chefes só ficam lentos.",
                           "Effect (Light): bewitches the target — monsters forget you and attack the nearest monster; players get dizzy. Bosses are only slowed."),
}

EGGS = {
    "hollow_crawler": ("Vazio Rastejante", "Hollow Crawler"), "hollow_brute": ("Vazio Brutamontes", "Hollow Brute"),
    "hollow_wraith": ("Vazio Alado", "Winged Hollow"), "menos_giant": ("Menos Gigante", "Menos Giant"),
    "arrancar": ("Arrancar", "Arrancar"), "fey_pixie": ("Fada Travessa", "Mischievous Pixie"),
    "bramble_hound": ("Cão de Espinhos", "Bramble Hound"), "hollow_king": ("Rei Vazio", "Hollow King"),
    "sand_empress": ("Imperatriz das Areias", "Sand Empress"), "thorn_queen": ("Rainha dos Espinhos", "Thorn Queen"),
}


def build_all():
    pt, en = {}, {}
    for k, (a, b) in L.items():
        pt[k], en[k] = a, b
    for e, (a, b) in EGGS.items():
        pt["item.isekai_mahou.%s_spawn_egg" % e] = "Ovo de " + a
        en["item.isekai_mahou.%s_spawn_egg" % e] = b + " Spawn Egg"
    d = os.path.join(ROOT, "src/main/lang/hueco_mundo")
    write_json(os.path.join(d, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(d, "en_us.json"), dict(sorted(en.items())))
    print("  traducoes: %d chaves" % len(pt))
