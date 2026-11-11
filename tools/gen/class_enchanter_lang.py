"""Traducoes do modulo class_enchanter (usadas por tools/gen/class_enchanter.py)."""

TIER_IDS = ("novice", "adept", "master", "sovereign")
PIECES = ("helmet", "chestplate", "leggings", "boots")

PT = {}
EN = {}


def both(key, pt, en):
    PT[key] = pt
    EN[key] = en


# ---------------------------------------------------------------- itens
both("item.isekai_mahou.enchanter_gauntlets", "Manoplas do Encantador", "Enchanter's Gauntlets")
both("item.isekai_mahou.enchanter_gauntlets.desc",
     "Manoplas leves de quem luta com o próprio corpo encantado. Socos rápidos que conduzem os encantamentos.",
     "Light gauntlets for those who fight with their own enchanted body. Quick punches that conduct enchantments.")
both("item.isekai_mahou.enchanter_gauntlets_t1", "Manoplas Rúnicas", "Runic Gauntlets")
both("item.isekai_mahou.enchanter_gauntlets_t2", "Manoplas do Inscritor", "Inscriber's Gauntlets")
both("item.isekai_mahou.enchanter_gauntlets_t3", "Manoplas de Mithril Encantado", "Enchanted Mithril Gauntlets")
both("item.isekai_mahou.enchanter_gauntlets_t4", "Manoplas do Soberano", "Sovereign's Gauntlets")
both("tooltip.isekai_mahou.enchanter_gauntlets.conduct",
     "Punhos Condutores: nas mãos do Encantador, cada golpe estende os encantamentos dele em 1 s.",
     "Conducting Fists: in an Enchanter's hands, every hit extends their enchantments by 1 s.")
both("tooltip.isekai_mahou.enchanter_gauntlets.twin", "Com a outra mão vazia, você usa as duas manoplas.",
     "With your other hand empty, you wear both gauntlets.")

both("item.isekai_mahou.enchanter_codex", "Códex de Inscrição", "Codex of Inscription")
both("item.isekai_mahou.enchanter_codex.desc",
     "O caderno do Encantador: cada página é um encantamento gravado no próprio corpo.",
     "The Enchanter's notebook: every page is an enchantment engraved on their own body.")
both("tooltip.isekai_mahou.enchanter_codex.use", "Usar: abre a aba do Encantador (ranks, tarefas, oferenda).",
     "Use: opens the Enchanter tab (ranks, tasks, offering).")
both("tooltip.isekai_mahou.enchanter_codex.rite", "Usar numa mesa de encantamento: realiza a Inscrição do próximo rank.",
     "Use on an enchanting table: performs the Inscription of the next rank.")
both("tooltip.isekai_mahou.enchanter.temp_enchanted", "✦ Encantado por um tempo (Equipamento Encantado)",
     "✦ Temporarily enchanted (Enchanted Gear)")

# ---------------------------------------------------------------- traje
TIER_PT = ("Encantador Noviço", "Inscritor", "Mestre Encantador", "Soberano dos Encantos")
TIER_EN = ("Novice Enchanter", "Inscriber", "Master Enchanter", "Sovereign of Enchantments")
PIECE_PT = ("Diadema", "Casaco", "Calças", "Botas")
PIECE_PT_GEN = ("do", "do", "do", "do")
PIECE_EN = ("Circlet", "Coat", "Trousers", "Boots")
for i, t in enumerate(TIER_IDS):
    both(f"outfit.isekai_mahou.enchanter.tier.{t}", TIER_PT[i], TIER_EN[i])
    for j, p in enumerate(PIECES):
        both(f"item.isekai_mahou.enchanter_{t}_{p}", f"{PIECE_PT[j]} do {TIER_PT[i]}", f"{TIER_EN[i]}'s {PIECE_EN[j]}")
both("tooltip.isekai_mahou.enchanter_outfit.flavor.novice", "Casaco de viagem com um selo de encantamento bordado no peito.",
     "A travel coat with an enchantment seal embroidered on the chest.")
both("tooltip.isekai_mahou.enchanter_outfit.flavor.adept", "Marfim e turquesa; a aureola de runas atrás da cabeça já gira sozinha.",
     "Ivory and turquoise; the rune halo behind the head already spins on its own.")
both("tooltip.isekai_mahou.enchanter_outfit.flavor.master", "Azul da meia-noite e ouro: o manto de quem quebra feitiços com as mãos.",
     "Midnight blue and gold: the mantle of one who breaks spells barehanded.")
both("tooltip.isekai_mahou.enchanter_outfit.flavor.sovereign", "Branco e ouro, runas orbitando a cintura. Magia nenhuma toca este tecido.",
     "White and gold, runes orbiting the waist. No magic touches this cloth.")
both("tooltip.isekai_mahou.enchanter_outfit.tier", "Grau %s — %s (Círculo %s)", "Tier %s — %s (Circle %s)")
both("tooltip.isekai_mahou.enchanter_outfit.set", "Traje completo: encantamentos do Encantador duram +%s%%",
     "Full set: the Enchanter's enchantments last +%s%%")
both("toast.isekai_mahou.enchanter_outfit.title", "Traje: %s", "Outfit: %s")
both("toast.isekai_mahou.enchanter_outfit.subtitle", "Presente do Círculo %s", "Gift of Circle %s")

# ---------------------------------------------------------------- efeitos (encantamentos)
EFFECTS = {
    "enchant_might": ("Encantamento: Força", "Enchantment: Might"),
    "enchant_haste": ("Encantamento: Celeridade", "Enchantment: Celerity"),
    "enchant_aegis": ("Encantamento: Égide", "Enchantment: Aegis"),
    "enchant_vitality": ("Encantamento: Vitalidade", "Enchantment: Vitality"),
    "enchant_imbue": ("Arma Imbuída", "Imbued Weapon"),
    "enchant_overdrive": ("Sobrecarga", "Overdrive"),
    "enchant_gear": ("Equipamento Encantado", "Enchanted Gear"),
    "anti_magic": ("Anti-Magia", "Anti-Magic"),
    "heavenly_restriction": ("Restrição Celestial", "Heavenly Restriction"),
}
for k, (pt, en) in EFFECTS.items():
    both(f"effect.isekai_mahou.{k}", pt, en)
both("entity.isekai_mahou.enchanter_sanctum", "Santuário do Soberano", "Sovereign's Sanctum")

# ---------------------------------------------------------------- glifos
GLYPHS = {
    "enchant_might": (
        "Encantamento: Força", "Enchantment: Might",
        "Encantamento (origem do Encantador). +2 de dano corpo a corpo por nível; cada aplicação soma +1 nível (+1 por "
        "Amplificar) até o teto (3; Encantador 4–5). 30 s (Estender; o Encantador dura bem mais). Inverter: Fraqueza no inimigo.",
        "Enchantment (Enchanter origin). +2 melee damage per level; each application adds +1 level (+1 per Amplify) up to "
        "the cap (3; Enchanter 4–5). 30 s (Extend; much longer for the Enchanter). Invert: Weakness on the enemy."),
    "enchant_haste": (
        "Encantamento: Celeridade", "Enchantment: Celerity",
        "Encantamento. +8% de velocidade e +10% de velocidade de ataque por nível, empilhando até o teto. 30 s. "
        "Inverter: Lentidão e Fadiga no inimigo.",
        "Enchantment. +8% speed and +10% attack speed per level, stacking up to the cap. 30 s. "
        "Invert: Slowness and Mining Fatigue on the enemy."),
    "enchant_aegis": (
        "Encantamento: Égide", "Enchantment: Aegis",
        "Encantamento. +3 de armadura, +1 de resistência e +8% de firmeza por nível. 30 s. Inverter: Exposto.",
        "Enchantment. +3 armor, +1 toughness and +8% knockback resistance per level. 30 s. Invert: Exposed."),
    "enchant_vitality": (
        "Encantamento: Vitalidade", "Enchantment: Vitality",
        "Encantamento. Cura 2 na hora e regenera a cada 2 s (mais por nível). 20 s. Inverter: Sangramento.",
        "Enchantment. Heals 2 at once and regenerates every 2 s (more per level). 20 s. Invert: Bleeding."),
    "enchant_imbue": (
        "Imbuir Arma", "Imbue Weapon",
        "Encantamento. O ELEMENTO da magia vai para a arma do alvo: cada golpe corpo a corpo causa +1,5 de dano por nível "
        "(Arcano +2) e o toque do elemento (queima, molha, gela, eletrifica, pesa, empurra, expõe, corrompe). Monte com um "
        "glifo do elemento. 30 s. Inverter: desencanta o inimigo (tira encantamentos e um efeito bom).",
        "Enchantment. The spell's ELEMENT goes into the target's weapon: each melee hit deals +1.5 damage per level "
        "(Arcane +2) and the element's touch (burns, soaks, chills, shocks, weighs, pushes, exposes, corrupts). Build it with "
        "an element glyph. 30 s. Invert: disenchants the enemy (removes enchantments and one good effect)."),
    "enchant_overdrive": (
        "Sobrecarga", "Overdrive",
        "Encantamento. Por 8 s: +20% de dano, +15% de ataque e +8% de velocidade por nível. Ao acabar vem a Exaustão "
        "(Lentidão e Fraqueza). Inverter: Exaustão forçada no inimigo.",
        "Enchantment. For 8 s: +20% damage, +15% attack speed and +8% speed per level. When it ends comes Exhaustion "
        "(Slowness and Weakness). Invert: forced Exhaustion on the enemy."),
    "enchant_gear": (
        "Equipamento Encantado", "Enchanted Gear",
        "Encantamento. Encanta por um tempo a arma, as ferramentas e a armadura do alvo (os itens brilham): +1 de dano por "
        "nível (+0,75 em projéteis), −5% de dano recebido e +25% de mineração por nível. 45 s. Inverter: Exposto e Fraqueza.",
        "Enchantment. Temporarily enchants the target's weapon, tools and armor (items glow): +1 damage per level (+0.75 on "
        "projectiles), −5% damage taken and +25% mining speed per level. 45 s. Invert: Exposed and Weakness."),
    "anti_magic": (
        "Anti-Magia", "Anti-Magic",
        "Encantamento de assinatura, SÓ EM SI. Níveis I–V: dano de magia cai 45/60/72/84/100% (contra chefes até 70%) e "
        "efeitos mágicos ruins falham (35–100%). Em troca suas magias ofensivas causam 60–35% do dano e manter custa recurso "
        "por segundo. Reconjurar sobe o nível; agache + F encerra. Inverter: Silêncio e desencanto no inimigo.",
        "Signature enchantment, SELF ONLY. Levels I–V: spell damage drops 45/60/72/84/100% (up to 70% against bosses) and "
        "harmful magical effects fail (35–100%). In exchange your offensive spells deal 60–35% damage and upkeep costs "
        "resource per second. Recasting raises the level; sneak + F ends it. Invert: Silence and disenchant on the enemy."),
    "heavenly_restriction": (
        "Restrição Celestial", "Heavenly Restriction",
        "Encantamento de assinatura, SÓ EM SI. Consome TODO o recurso e o trava em zero: você não conjura nada enquanto "
        "durar. Em troca, por nível: força, velocidade, ataque, armadura, vida, pulo, −5% de dano recebido; visão noturna e "
        "inimigos marcados; invisível para detecção mágica e imune ao Acerto Garantido de domínios (de chefe, sofre só 30%). 45 s. Agache + F "
        "encerra. Inverter: drena a mana do inimigo e o silencia.",
        "Signature enchantment, SELF ONLY. Consumes ALL your resource and locks it at zero: you cannot cast anything while it "
        "lasts. In exchange, per level: strength, speed, attack speed, armor, health, jump, −5% damage taken; night vision "
        "and marked enemies; invisible to magical detection and immune to domains' Sure Hit (a boss domain deals only 30%). 45 s. Sneak + F ends it. "
        "Invert: drains the enemy's mana and silences them."),
    "enchant_link": (
        "Elo de Encantamento", "Enchantment Link",
        "Forma de origem do Encantador. Fios de luz levam os efeitos a você, ao seu GRUPO (sem mirar nem enxergar), aos "
        "seus mascotes e a aliados por perto. Alcance 24 (Encantador 36). Até 2 aliados +2 por Dividir +1 a cada 3 "
        "Círculos (máx. 7). Efeitos ruins não atingem aliados.",
        "Enchanter origin form. Threads of light carry the effects to you, your PARTY (no aiming or line of sight needed), "
        "your pets and nearby allies. Range 24 (Enchanter 36). Up to 2 allies +2 per Split +1 every 3 Circles (max 7). "
        "Harmful effects don't hit allies."),
    "enchant_sovereign": (
        "Santuário do Soberano", "Sovereign's Sanctum",
        "Forma G (ultimate) do Encantador. Abre um círculo de inscrição gigante (raio 10, 15 s; o Encantador maior e mais "
        "longo) que pulsa os efeitos em todos lá dentro a cada segundo — encantamentos empilhando nos aliados, o resto nos "
        "inimigos — e é um campo ANTI-MAGIA: magias inimigas não saem e projéteis mágicos se desfazem (chefes resistem).",
        "Enchanter G form (ultimate). Opens a giant inscription circle (radius 10, 15 s; bigger and longer for the "
        "Enchanter) that pulses the effects on everyone inside every second — enchantments stacking on allies, the rest on "
        "enemies — and is an ANTI-MAGIC field: enemy spells fail inside and magic projectiles dissolve (bosses resist)."),
}
for k, (pt, en, dpt, den) in GLYPHS.items():
    both(f"glyph.isekai_mahou.{k}", pt, en)
    both(f"glyph.isekai_mahou.{k}.desc", dpt, den)

# ---------------------------------------------------------------- lendas
both("legend.isekai_mahou.enchanter_emperor_fist", "Punho do Imperador: Quebra-Feitiço", "Emperor's Fist: Spellbreaker")
both("legend.isekai_mahou.enchanter_emperor_fist.desc",
     "O próximo soco carrega uma Anti-Magia invertida e amplificada: o alvo é silenciado e perde os encantamentos e efeitos bons. "
     "Para o lutador que não deixa magia nenhuma terminar.",
     "Your next punch carries an inverted, amplified Anti-Magic: the target is silenced and loses enchantments and good "
     "effects. For the fighter who lets no spell finish.")
both("legend.isekai_mahou.enchanter_celestial_body", "Corpo Celestial: Restrição Absoluta", "Celestial Body: Absolute Restriction")
both("legend.isekai_mahou.enchanter_celestial_body.desc",
     "A Restrição Celestial já em nível alto e longa: toda a mana vira corpo — força, velocidade e sentidos absurdos, "
     "invisível para a magia.",
     "Heavenly Restriction at a high level and long duration: all mana becomes body — absurd strength, speed and senses, "
     "invisible to magic.")
both("legend.isekai_mahou.enchanter_sovereign_crown", "Coroa do Soberano Encantador", "Crown of the Sovereign Enchanter")
both("legend.isekai_mahou.enchanter_sovereign_crown.desc",
     "O Santuário do Soberano ampliado, pulsando Força amplificada, Celeridade, Égide e Vitalidade em todo o grupo enquanto "
     "as magias inimigas se desfazem.",
     "The Sovereign's Sanctum, enlarged, pulsing amplified Might, Celerity, Aegis and Vitality on the whole party while "
     "enemy spells dissolve.")

# ---------------------------------------------------------------- anuncios e mensagens
both("announce.isekai_mahou.enchanter.anti_magic", "ANTI-MAGIA %s", "ANTI-MAGIC %s")
both("announce.isekai_mahou.enchanter.heavenly", "RESTRIÇÃO CELESTIAL %s", "HEAVENLY RESTRICTION %s")
msgs = {
    "capacity": ("%s se desfez: o alvo só carrega %s encantamentos.", "%s faded: the target can only hold %s enchantments."),
    "anti_magic_on": ("Anti-Magia %s: magias inimigas −%s%% · suas magias ofensivas causam %s%%.",
                      "Anti-Magic %s: enemy spells −%s%% · your offensive spells deal %s%%."),
    "anti_magic_off": ("Sem recurso: a Anti-Magia se desfez.", "Out of resource: Anti-Magic faded."),
    "anti_magic_own": ("Anti-Magia ativa: suas magias ofensivas causam só %s%% do dano.",
                       "Anti-Magic active: your offensive spells deal only %s%% damage."),
    "anti_magic_ended": ("Você desfez a Anti-Magia.", "You dispelled your Anti-Magic."),
    "heavenly_on": ("Restrição Celestial %s: toda a mana virou corpo. Agache + F para encerrar.",
                    "Heavenly Restriction %s: all your mana became body. Sneak + F to end it."),
    "heavenly_off": ("A Restrição Celestial terminou: a mana volta aos poucos.", "Heavenly Restriction ended: your mana slowly returns."),
    "heavenly_no_cast": ("Restrição Celestial: você não conjura enquanto durar (agache + F encerra).",
                         "Heavenly Restriction: you cannot cast while it lasts (sneak + F ends it)."),
    "heavenly_domain": ("O domínio procura mana… e não encontra você.", "The domain searches for mana… and cannot find you."),
    "self_only_anti": ("A Anti-Magia só funciona em você mesmo.", "Anti-Magic only works on yourself."),
    "self_only_heavenly": ("A Restrição Celestial só funciona em você mesmo.", "Heavenly Restriction only works on yourself."),
    "need_circle": ("Precisa do Círculo %s (você está no %s).", "Requires Circle %s (you are at %s)."),
    "need_level": ("Precisa do nível %s (você está no %s).", "Requires level %s (you are level %s)."),
    "need_item": ("Oferenda: %s× %s (você tem %s).", "Offering: %s× %s (you have %s)."),
    "need_table": ("Fique perto de uma mesa de encantamento (até 6 blocos).", "Stand near an enchanting table (within 6 blocks)."),
    "not_enchanter": ("Só um Encantador realiza a Inscrição.", "Only an Enchanter can perform the Inscription."),
    "max_rank": ("Você já é Soberano dos Encantos: todas as Inscrições feitas.",
                 "You are already a Sovereign of Enchantments: every Inscription is done."),
    "not_ready": ("Ainda não é hora da Inscrição de %s. Falta:", "The Inscription of %s isn't ready yet. Missing:"),
    "rite_begin": ("Inscrição de %s começou: fique perto da mesa…", "Inscription of %s began: stay near the table…"),
    "rite_broken": ("A Inscrição se desfez (você se afastou). Nada foi gasto.",
                    "The Inscription broke (you moved away). Nothing was spent."),
    "rank_up_title": ("Inscrição %s: %s", "Inscription %s: %s"),
    "rank_up_toast": ("Novos encantamentos gravados no corpo", "New enchantments engraved on your body"),
    "rank_up_sub": ("O corpo é o feitiço", "The body is the spell"),
    "rank_up": ("✦ Rito de Inscrição concluído: você agora é %s.", "✦ Rite of Inscription complete: you are now %s."),
    "rank_perks": ("Acúmulo até %s · %s encantamentos por alvo · assinatura até %s · duração +%s%%",
                   "Stack up to %s · %s enchantments per target · signature up to %s · duration +%s%%"),
    "rank_no_glyph": ("(Você já conhecia os glifos deste rank.)", "(You already knew this rank's glyphs.)"),
    "welcome": ("✦ Encantador: suas magias iniciais estão no Loadout 1 (Autoencanto, Anti-Magia, Golpe) e 2 (Bênção de Força).",
                "✦ Enchanter: your starting spells are in Loadout 1 (Self-Enchant, Anti-Magic, Strike) and 2 (Blessing of Might)."),
    "welcome_hint": ("Use o Códex de Inscrição para ver o Rito (5 ranks). Encante-se, encante o grupo — e lute com as manoplas.",
                     "Use the Codex of Inscription to see the Rite (5 ranks). Enchant yourself, enchant your party — and fight with the gauntlets."),
    "outfit_granted": ("Círculo %s: você recebeu o traje %s (defesa %s · encantamentos +%s%% com o traje completo).",
                       "Circle %s: you received the %s outfit (defense %s · enchantments +%s%% with the full set)."),
    "outfit_inventory": ("Algumas peças foram para o inventário.", "Some pieces went to your inventory."),
    "field_blocked": ("Santuário inimigo: sua magia se desfaz aqui dentro!", "Enemy sanctum: your magic dissolves in here!"),
    "overdrive_end": ("A Sobrecarga passou: Exaustão.", "Overdrive wore off: Exhaustion."),
}
for k, (pt, en) in msgs.items():
    both(f"message.isekai_mahou.enchanter.{k}", pt, en)

# ---------------------------------------------------------------- aba
RANKS = (("Noviço do Encanto", "Enchantment Novice"), ("Inscritor", "Inscriber"), ("Tecelão de Encantos", "Enchantment Weaver"),
         ("Quebra-Feitiço", "Spellbreaker"), ("Corpo Celestial", "Celestial Body"),
         ("Soberano dos Encantos", "Sovereign of Enchantments"))
for i, (pt, en) in enumerate(RANKS):
    both(f"screen.isekai_mahou.enchanter.rank.{i}", pt, en)
screen = {
    "tab": ("Encantador", "Enchanter"),
    "title": ("Encantador — Rito de Inscrição", "Enchanter — Rite of Inscription"),
    "not_class": ("Esta aba é do Encantador. Outras classes podem aprender os glifos dele com uma Lição escrita por um "
                  "Encantador (com custo maior) — mas o Rito de Inscrição e os bônus de classe são só dele.",
                  "This tab belongs to the Enchanter. Other classes can learn their glyphs from a Lesson written by an "
                  "Enchanter (at a higher cost) — but the Rite of Inscription and the class bonuses are theirs alone."),
    "rank_line": ("Rank %s: %s", "Rank %s: %s"),
    "perk.duration": ("Encantamentos duram +%s%%", "Enchantments last +%s%%"),
    "perk.stack": ("Acúmulo até o nível %s", "Stack up to level %s"),
    "perk.capacity": ("%s encantamentos por alvo", "%s enchantments per target"),
    "perk.signature": ("Anti-Magia/Restrição até %s", "Anti-Magic/Restriction up to %s"),
    "perk.skin": ("Pele Antimágica: −%s%% de dano mágico", "Anti-Magic Skin: −%s%% magic damage"),
    "active": ("Em você agora", "On you now"),
    "active_entry": ("%s %s · %ss", "%s %s · %ss"),
    "active_none": ("Nenhum encantamento ativo.", "No active enchantments."),
    "glyphs": ("Glifos de origem", "Origin glyphs"),
    "max": ("Todas as Inscrições concluídas!", "Every Inscription is complete!"),
    "max_desc": ("Você é Soberano dos Encantos. Os bônus de classe estão no máximo; continue encantando o grupo e "
                 "quebrando feitiços.", "You are a Sovereign of Enchantments. Class bonuses are maxed; keep enchanting "
                 "your party and breaking spells."),
    "next": ("Próxima Inscrição %s: %s", "Next Inscription %s: %s"),
    "need_circle": ("Círculo %s (você: %s)", "Circle %s (you: %s)"),
    "need_level": ("Nível %s (você: %s)", "Level %s (you: %s)"),
    "tasks": ("Tarefas (desde a escolha da classe)", "Tasks (since choosing the class)"),
    "req.applied": ("Aplicar encantamentos (aliados contam 2): %s", "Apply enchantments (allies count 2): %s"),
    "req.kills": ("Abates com encantamentos ativos: %s", "Kills while enchanted: %s"),
    "req.ally": ("Encantar aliados (grupo, mascotes): %s", "Enchant allies (party, pets): %s"),
    "req.nullified": ("Dano mágico anulado pela Anti-Magia: %s", "Magic damage nullified by Anti-Magic: %s"),
    "req.overdrive": ("Usar a Sobrecarga: %s", "Use Overdrive: %s"),
    "req.restricted": ("Abates sob Restrição Celestial: %s", "Kills under Heavenly Restriction: %s"),
    "req.boss": ("Chefes derrotados com encantamentos ativos: %s", "Bosses defeated while enchanted: %s"),
    "offering": ("Oferenda (queimada na mesa)", "Offering (burned at the table)"),
    "rewards": ("Recompensas", "Rewards"),
    "reward_xp": ("%s de XP", "%s XP"),
    "reward_glyph": ("Glifo: %s", "Glyph: %s"),
    "reward_awaken": ("Despertar: %s", "Awaken: %s"),
    "reward_item": ("%s× %s", "%s× %s"),
    "reward_perks": ("Bônus de classe maiores (acúmulo, capacidade, assinatura, duração)",
                     "Bigger class bonuses (stack, capacity, signature, duration)"),
    "how": ("Use o Códex numa mesa de encantamento ou o botão abaixo perto dela. Fique 5 s ao lado da mesa.",
            "Use the Codex on an enchanting table or the button below near one. Stay by the table for 5 s."),
    "no_table": ("Nenhuma mesa de encantamento a até 6 blocos.", "No enchanting table within 6 blocks."),
    "perform": ("Realizar Inscrição", "Perform Inscription"),
}
for k, (pt, en) in screen.items():
    both(f"screen.isekai_mahou.enchanter.{k}", pt, en)

# ---------------------------------------------------------------- comandos
both("command.isekai_mahou.enchanter.status",
     "Encantador rank %s · aplicados %s · aliados %s · anulado %s · abates %s · sobrecargas %s · restritos %s · chefes %s",
     "Enchanter rank %s · applied %s · allies %s · nullified %s · kills %s · overdrives %s · restricted %s · bosses %s")
both("command.isekai_mahou.enchanter.fill", "Tarefas de %s completas (falta a oferenda e a mesa).",
     "Tasks of %s completed (offering and table still required).")
both("command.isekai_mahou.enchanter.rank", "Rank do Encantador: %s", "Enchanter rank: %s")

# ---------------------------------------------------------------- pacote de tecnicas de anime (Oito Portoes, Kaioken...)
import os as _os  # noqa: E402
import sys as _sys  # noqa: E402

_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
from class_enchanter_anime_lang import PT as _ANIME_PT, EN as _ANIME_EN  # noqa: E402

PT.update(_ANIME_PT)
EN.update(_ANIME_EN)
