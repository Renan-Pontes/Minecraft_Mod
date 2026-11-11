"""Traducoes do pacote de tecnicas de anime do class_gambler (importado por class_gambler_lang.py). %% = sinal de porcento."""

ENTRIES = {}


def k(key, pt, en):
    ENTRIES[key] = (pt, en)


# ------------------------------------------------------------------ glifos
G = {
    "gambler_fate_dice": (
        "Dados do Destino", "Dice of Fate",
        "Efeito (origem do Apostador). Dois dados gigantes caem sobre o alvo e mostram o destino: 1,2 de dano por ponto "
        "da soma. 2 (Olhos de Cobra): o azar vira contra você (Azarado). 7 (Sete da Sorte): você cura e fica Sortudo. "
        "Dupla: ×1,5 e o alvo fica Azarado. 12: ×3 e um raio de luz (+10 de Tensão). A Sorte rola de novo um dado baixo; "
        "Dados Viciados: faces ≥ 3.",
        "Effect (Gambler origin). Two giant dice fall on the target and show its fate: 1.2 damage per pip of the sum. "
        "2 (Snake Eyes): the bad luck turns on you (Jinxed). 7 (Lucky Seven): you heal and become Lucky. Doubles: ×1.5 "
        "and the target is Jinxed. 12: ×3 and a bolt of light (+10 Tension). Luck rerolls a low die; Loaded Dice: faces ≥ 3."),
    "bungee_gum": (
        "Goma Elástica", "Elastic Gum",
        "Efeito (origem do Apostador; homenagem ao mágico das cartas). Gruda um fio de aura rosa, elástico como borracha e "
        "grudento como chiclete. Num inimigo: 3 de dano e, por 6 s (Estender), ele não se afasta mais que 4 blocos. "
        "AGACHE para contrair: ele vem voando até você e é arremessado para onde você olha (8 de dano ao bater). Chefes "
        "só são puxados de leve. Em você (Si Mesmo): gruda no bloco para onde você olha (24 blocos) e te puxa até lá.",
        "Effect (Gambler origin; a tribute to the card magician). Sticks a pink aura thread, elastic as rubber and sticky "
        "as gum. On an enemy: 3 damage and, for 6 s (Extend), it can't get farther than 4 blocks. SNEAK to contract: it "
        "flies to you and gets thrown where you look (8 damage on impact). Bosses are only tugged lightly. On yourself "
        "(Self): sticks to the block you look at (24 blocks) and pulls you there."),
    "texture_surprise": (
        "Textura Surpresa", "Texture Surprise",
        "Efeito (origem do Apostador; homenagem ao mágico das cartas). Uma folha de ilusão que muda o que os outros veem. "
        "Em você (Si Mesmo): DISFARCE por 6 s — invisível, os monstros perdem o rastro e só notam você de perto; o "
        "primeiro golpe ou magia saindo do disfarce é uma SURPRESA (+60%%). Num inimigo: TEXTURA FALSA — até 3 monstros "
        "por perto (+1 por Amplificar) passam a atacá-lo, e ele fica cego, enjoado e Azarado (3 de dano).",
        "Effect (Gambler origin; a tribute to the card magician). A sheet of illusion that changes what others see. On "
        "yourself (Self): DISGUISE for 6 s — invisible, monsters lose your trail and only notice you up close; the first "
        "hit or spell out of the disguise is a SURPRISE (+60%%). On an enemy: FAKE TEXTURE — up to 3 nearby monsters "
        "(+1 per Amplify) start attacking it, and it is blinded, nauseous and Jinxed (3 damage)."),
    "gambler_all_in": (
        "Aposta Total", "All-In Wager",
        "Efeito (origem do Apostador; homenagem ao anime das apostas compulsivas). Um duelo de dados contra o alvo: os "
        "dois rolam 2d6. Se você vence, causa 10 + 2 × diferença, cura metade, ROUBA 1 efeito bom (+1 por Amplificar) e "
        "15%% da mana de jogadores; ele fica Azarado e você Sortudo. Empate: os dois levam um pouco. Se você perde, VOCÊ "
        "leva o dano (6 + 1,5 × diferença) e ele cura. Toda aposta tem risco.",
        "Effect (Gambler origin; a tribute to the compulsive-gambling anime). A dice duel against the target: both roll "
        "2d6. If you win, you deal 10 + 2 × the difference, heal half, STEAL 1 good effect (+1 per Amplify) and 15%% of a "
        "player's mana; it becomes Jinxed and you Lucky. Tie: both take a little. If you lose, YOU take the damage "
        "(6 + 1.5 × difference) and it heals. Every bet has a risk."),
    "idle_death_gamble": (
        "Expansão de Domínio: Morte Ociosa", "Domain Expansion: Idle Death Gamble",
        "Forma G (ultimate) do Apostador, rank V da Casa (homenagem ao domínio de pachinko). Um salão de neon de raio 11 "
        "por 15 s (Expandir/Estender). A cada 2 s uma RODADA: a sorte rola e os efeitos acertam todos os inimigos dentro "
        "(Acerto Garantido); então vem o Reach — Verde, Vermelho, Ouro ou Arco-Íris — com 6%%/22%%/50%%/100%% de chance "
        "de JACKPOT (+3%% por rodada; Lição: metade). JACKPOT IMORTAL: 251 ticks (os 4:11 viraram ticks) + 2 s por rank de "
        "recurso infinito, regeneração total, música própria — e golpes fatais não matam. Aceita Leis de Domínio.",
        "Gambler G form (ultimate), House rank V (a tribute to the pachinko domain). A neon hall of radius 11 for 15 s "
        "(Expand/Extend). Every 2 s a ROUND: luck rolls and the effects hit every enemy inside (Sure Hit); then comes the "
        "Reach — Green, Red, Gold or Rainbow — with a 6%%/22%%/50%%/100%% JACKPOT chance (+3%% per round; Lesson: half). "
        "IMMORTAL JACKPOT: 251 ticks (the 4:11 became ticks) + 2 s per rank of infinite resource, full regeneration, its "
        "own music — and fatal blows don't kill. Accepts Domain Laws."),
}
for gid, (pt, en, dpt, den) in G.items():
    k("glyph.isekai_mahou." + gid, pt, en)
    k("glyph.isekai_mahou.%s.desc" % gid, dpt, den)

# ------------------------------------------------------------------ efeito, entidades, lendas
k("effect.isekai_mahou.gambler_disguise", "Disfarce", "Disguise")
k("entity.isekai_mahou.gambler_gum_tether", "Goma Elástica", "Elastic Gum")
k("entity.isekai_mahou.idle_death_domain", "Domínio da Morte Ociosa", "Idle Death Gamble Domain")
k("legend.isekai_mahou.gambler_bungee_trick", "Truque do Mágico: Goma Elástica", "Magician's Trick: Elastic Gum")
k("legend.isekai_mahou.gambler_bungee_trick.desc",
  "Três cartas teleguiadas que grudam a Goma Elástica e cobrem o alvo com a Textura Falsa: puxe, arremesse e deixe os "
  "outros monstros terminarem o serviço.",
  "Three homing cards that stick the Elastic Gum and cover the target with Fake Texture: pull, throw and let the "
  "other monsters finish the job.")
k("legend.isekai_mahou.gambler_all_or_nothing", "Aposta Total: Tudo ou Nada", "All-In: All or Nothing")
k("legend.isekai_mahou.gambler_all_or_nothing.desc",
  "O duelo de dados amplificado e com a aposta dobrada, seguido pelos Dados do Destino. Vencer rende muito; perder dói.",
  "The dice duel amplified and with the bet doubled, followed by the Dice of Fate. Winning pays a lot; losing hurts.")
k("legend.isekai_mahou.gambler_immortal_jackpot", "Morte Ociosa: Jackpot Imortal", "Idle Death: Immortal Jackpot")
k("legend.isekai_mahou.gambler_immortal_jackpot.desc",
  "O domínio de pachinko ampliado: cada rodada joga os Dados do Destino e puxa uma Mão Real, com a aposta dobrada — até o "
  "Jackpot Imortal.",
  "The enlarged pachinko domain: every round throws the Dice of Fate and draws a Royal Hand with the bet doubled — until "
  "the Immortal Jackpot.")

# ------------------------------------------------------------------ anuncios e mensagens
k("announce.isekai_mahou.gambler.all_in", "APOSTA TOTAL!", "ALL-IN!")
k("announce.isekai_mahou.gambler.idle_death", "EXPANSÃO DE DOMÍNIO: MORTE OCIOSA", "DOMAIN EXPANSION: IDLE DEATH GAMBLE")
k("announce.isekai_mahou.gambler.immortal_jackpot", "JACKPOT IMORTAL!", "IMMORTAL JACKPOT!")
M = "message.isekai_mahou.gambler."
k(M + "immortal_banner", "JACKPOT! IMORTAL POR 4:11 (EM TICKS)!", "JACKPOT! IMMORTAL FOR 4:11 (IN TICKS)!")
k(M + "immortal_start", "JACKPOT IMORTAL! %ss sem morrer, sorte infinita · +%s Ficha(s)",
  "IMMORTAL JACKPOT! %ss without dying, infinite luck · +%s Chip(s)")
k(M + "immortal_save", "Golpe fatal… o corpo se regenera por inteiro!", "A fatal blow… your body fully regenerates!")
k(M + "idle_death_open", "Morte Ociosa aberta: rodadas a cada 2 s — espere o Reach!", "Idle Death open: a round every 2 s — wait for the Reach!")
k(M + "reach", "REACH %s! Chance de Jackpot: %s%%", "REACH %s! Jackpot chance: %s%%")
k(M + "reach_green", "VERDE", "GREEN")
k(M + "reach_red", "VERMELHO", "RED")
k(M + "reach_gold", "OURO", "GOLD")
k(M + "reach_rainbow", "ARCO-ÍRIS", "RAINBOW")
k(M + "surprise", "Surpresa! (+60%% de dano)", "Surprise! (+60%% damage)")
k(M + "disguise_on", "Disfarce por %ss: os monstros perdem o seu rastro.", "Disguised for %ss: monsters lose your trail.")
k(M + "gum_no_anchor", "Nada para grudar a goma por perto (mire num bloco).", "Nothing nearby to stick the gum to (aim at a block).")
k(M + "gum_stuck", "Grudou! Agache para contrair e arremessar.", "Stuck! Sneak to contract and throw.")
k(M + "all_in_win", "Aposta Total: %s contra %s — você leva tudo!", "All-In: %s against %s — you take it all!")
k(M + "all_in_tie", "Aposta Total: empate em %s — os dois perdem um pouco.", "All-In: a tie at %s — both lose a little.")
k(M + "all_in_lose", "Aposta Total: %s contra %s — a casa perdeu…", "All-In: %s against %s — the house lost…")
k(M + "fate_snake", "Dados do Destino: %s + %s = %s — Olhos de Cobra! O azar é seu.", "Dice of Fate: %s + %s = %s — Snake Eyes! The bad luck is yours.")
k(M + "fate_twelve", "Dados do Destino: %s + %s = %s — DOZE! Raio da sorte!", "Dice of Fate: %s + %s = %s — TWELVE! Lucky bolt!")
k(M + "fate_seven", "Dados do Destino: %s + %s = %s — Sete da Sorte!", "Dice of Fate: %s + %s = %s — Lucky Seven!")
k(M + "fate_double", "Dados do Destino: %s + %s = %s — Dupla!", "Dice of Fate: %s + %s = %s — Doubles!")
k(M + "fate_roll", "Dados do Destino: %s + %s = %s", "Dice of Fate: %s + %s = %s")
k("screen.isekai_mahou.gambler.reward_legends", "5 Lendas", "5 Legends")
