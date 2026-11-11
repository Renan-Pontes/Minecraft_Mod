"""Traducoes do modulo class_gambler (pt_br + en_us). Uso: py -I tools/gen/class_gambler_lang.py"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
T = {}


def k(key, pt, en):
    T[key] = (pt, en)


# ------------------------------------------------------------------ itens
k("item.isekai_mahou.gambler_deck", "Baralho Arcano", "Arcane Deck")
for n, (pt, en) in enumerate([("Baralho Arcano I", "Arcane Deck I"), ("Baralho Arcano II: Mesa Alta", "Arcane Deck II: High Table"),
                              ("Baralho Arcano III: Ouro da Casa", "Arcane Deck III: House Gold"),
                              ("Baralho Arcano IV: Fortuna Eterna", "Arcane Deck IV: Eternal Fortune")], 1):
    k("item.isekai_mahou.gambler_deck_t%d" % n, pt, en)
k("item.isekai_mahou.gambler_card", "Carta de Baralho", "Playing Card")
k("item.isekai_mahou.casino_chip", "Ficha de Cassino", "Casino Chip")
PIECES = {"helmet": ("{h}", "{h}"), "chestplate": ("{c}", "{c}"), "leggings": ("Calças {p}", "{p} Trousers"),
          "boots": ("Sapatos {p}", "{p} Shoes")}
OUT = {
    "dealer": dict(pt=dict(h="Viseira do Crupiê", c="Colete do Crupiê", p="do Crupiê"),
                   en=dict(h="Dealer's Visor", c="Dealer's Vest", p="Dealer's")),
    "high_roller": dict(pt=dict(h="Fedora da Mesa Alta", c="Smoking da Mesa Alta", p="da Mesa Alta"),
                        en=dict(h="High Roller's Fedora", c="High Roller's Tuxedo", p="High Roller's")),
    "casino_king": dict(pt=dict(h="Fedora do Rei do Cassino", c="Terno do Rei do Cassino", p="do Rei do Cassino"),
                        en=dict(h="Casino King's Fedora", c="Casino King's Suit", p="Casino King's")),
    "fortune_lord": dict(pt=dict(h="Cartola do Senhor da Fortuna", c="Sobretudo do Senhor da Fortuna", p="do Senhor da Fortuna"),
                         en=dict(h="Fortune Lord's Top Hat", c="Fortune Lord's Greatcoat", p="Fortune Lord's")),
}
for tier, names in OUT.items():
    for piece, (fpt, fen) in PIECES.items():
        iid = ("gambler_" + piece) if tier == "dealer" else ("gambler_%s_%s" % (tier, piece))
        k("item.isekai_mahou." + iid, fpt.format(**names["pt"]), fen.format(**names["en"]))

# ------------------------------------------------------------------ entidades e efeitos
k("entity.isekai_mahou.thrown_card", "Carta Carregada", "Charged Card")
k("entity.isekai_mahou.gambler_dice", "Dado Mágico", "Magic Die")
k("entity.isekai_mahou.roulette_wheel", "Roleta Mágica", "Magic Roulette")
k("entity.isekai_mahou.slot_machine", "Caça-Níquel Mágico", "Magic Slot Machine")
k("entity.isekai_mahou.casino_domain", "Cassino da Fortuna", "Casino of Fortune")
k("effect.isekai_mahou.gambler_jackpot", "Jackpot", "Jackpot")
k("effect.isekai_mahou.gambler_lucky", "Sortudo", "Lucky")
k("effect.isekai_mahou.gambler_jinxed", "Azarado", "Jinxed")
k("effect.isekai_mahou.gambler_jackpot.description", "Sorte infinita: toda rolagem sai no máximo, recurso cheio, regeneração forte e mais velocidade.",
  "Infinite luck: every roll hits the maximum, full resource, strong regeneration and extra speed.")
k("effect.isekai_mahou.gambler_lucky.description", "Rolagens de sorte melhores, saques melhores e chance de esquivar golpes.",
  "Better luck rolls, better loot and a chance to dodge hits.")
k("effect.isekai_mahou.gambler_jinxed.description", "Recebe mais dano, erra golpes e rola pior a sorte.",
  "Takes more damage, misses attacks and rolls worse luck.")

# ------------------------------------------------------------------ glifos
G = {
    "card_throw": ("Carta", "Card",
                   "FORMA (Apostador). Arremessa cartas carregadas de energia que levam a magia e estouram ao acertar. "
                   "Cada Dividir abre o leque com +2 cartas (1, 3, 5...) e cada carta aplica os efeitos (custo por cópia). "
                   "Perfurar, Ricochete, Teleguiado e Acelerar valem; pode ser estágio 2+.",
                   "FORM (Gambler). Throws energy-charged cards that carry the spell and burst on hit. Each Split widens the fan "
                   "by +2 cards (1, 3, 5...) and every card applies the effects (cost per copy). Pierce, Ricochet, Homing and "
                   "Accelerate work; can be stage 2+."),
    "dice_roll": ("Dados", "Dice",
                  "FORMA (Apostador). Joga dois dados mágicos que quicam pelo chão; quando param, a SOMA decide a explosão "
                  "(poder = soma/7, raio 2,5 + soma/4). Dobradinha ×1,5; Doze (6+6) ×2,5 e +Tensão; Olhos de Cobra (1+1) ×0,3. "
                  "Dividir joga um dado a mais (entra na soma).",
                  "FORM (Gambler). Throws two magic dice that bounce on the ground; when they stop, the SUM decides the blast "
                  "(power = sum/7, radius 2.5 + sum/4). Doubles ×1.5; Boxcars (6+6) ×2.5 and +Tension; Snake Eyes (1+1) ×0.3. "
                  "Split throws one extra die (added to the sum)."),
    "roulette_wheel": ("Roleta", "Roulette",
                       "FORMA (Apostador). Uma roleta aparece onde você mira (até 24 blocos) e gira 2,5 s com o aviso no chão. "
                       "Vermelho ×1,25; Preto ×0,85 duas vezes; Zero verde ×3 e +Tensão. Raio 4,5 (Expandir vale).",
                       "FORM (Gambler). A roulette appears where you aim (up to 24 blocks) and spins for 2.5 s with a ground "
                       "warning. Red ×1.25; Black ×0.85 twice; green Zero ×3 and +Tension. Radius 4.5 (Expand works)."),
    "slot_machine": ("Caça-Níquel", "Slot Machine",
                     "FORMA (Apostador). Invoca uma máquina à sua frente; os 3 rolos giram e o prêmio sai em fichas teleguiadas "
                     "que levam a magia: nada 1 ficha ×0,7 · par 3 ×1 · trinca 5 ×1,5 · 7-7-7 7 fichas ×2 e JACKPOT. "
                     "Dividir = +1 ficha.",
                     "FORM (Gambler). Summons a machine in front of you; the 3 reels spin and the prize comes out as homing chips "
                     "that carry the spell: nothing 1 chip ×0.7 · pair 3 ×1 · triple 5 ×1.5 · 7-7-7 7 chips ×2 and JACKPOT. "
                     "Split = +1 chip."),
    "casino_domain": ("Domínio: Cassino da Fortuna", "Domain: Casino of Fortune",
                      "FORMA G (ultimate do Apostador). Abre um cassino de raio 10 por 12 s em volta de você (Expandir e Estender "
                      "valem). A cada segundo a sorte é rolada e TODOS os inimigos dentro recebem os efeitos com o poder sorteado "
                      "(Acerto Garantido). Aliados ficam Sortudos, a Tensão sobe e o Jackpot lá dentro dura ×1,5. Aceita Leis.",
                      "G FORM (Gambler ultimate). Opens a radius-10 casino around you for 12 s (Expand and Extend work). Every "
                      "second luck is rolled and ALL enemies inside receive the effects at the rolled power (Sure Hit). Allies "
                      "become Lucky, Tension rises and a Jackpot inside lasts ×1.5. Accepts Laws."),
    "fate_coin": ("Cara ou Coroa", "Heads or Tails",
                  "EFEITO (Apostador). Uma moeda gira sobre o alvo: CARA = 9 de dano de Luz; COROA = 4 de dano de Sombra e o "
                  "alvo fica Azarado por 6 s. A sua Sorte aumenta a chance de Cara (até 80%). Inverter = sempre Coroa.",
                  "EFFECT (Gambler). A coin spins over the target: HEADS = 9 Light damage; TAILS = 4 Shadow damage and the target "
                  "becomes Jinxed for 6 s. Your Luck raises the chance of Heads (up to 80%). Invert = always Tails."),
    "lucky_charm": ("Amuleto da Sorte", "Lucky Charm",
                    "EFEITO (Apostador, Luz). O alvo fica Sortudo por 15 s (Amplificar sobe o nível, até III) e cura um pouco. "
                    "Sortudo: rolagens e saques melhores e chance de esquivar golpes. Inverter = Azarado no inimigo.",
                    "EFFECT (Gambler, Light). The target becomes Lucky for 15 s (Amplify raises the level, up to III) and heals "
                    "a little. Lucky: better rolls and loot and a chance to dodge hits. Invert = Jinxed on the enemy."),
    "royal_flush": ("Mão Real", "Royal Hand",
                    "EFEITO (Apostador). Puxa uma mão de pôquer para cada alvo: 8 de dano × a mão (carta alta ×0,6 ... trinca "
                    "×1,5 · sequência ×1,9 · flush ×2,2 · full house ×2,6 · quadra ×3,2 · straight flush ×4 · ROYAL FLUSH ×6). "
                    "A Sorte dá cartas extras (até 8) e fica a melhor mão.",
                    "EFFECT (Gambler). Draws a poker hand for each target: 8 damage × the hand (high card ×0.6 ... three of a kind "
                    "×1.5 · straight ×1.9 · flush ×2.2 · full house ×2.6 · four of a kind ×3.2 · straight flush ×4 · ROYAL FLUSH ×6). "
                    "Luck draws extra cards (up to 8) and keeps the best hand."),
    "double_down": ("Dobrar a Aposta", "Double Down",
                    "AUMENTO (Apostador). Exagera a sorte da magia: o lado bom dobra o ganho (+50% vira +100%), o lado ruim corta "
                    "pela metade. Dobra a chance de JACKPOT e a Tensão de cada giro. Tudo ou nada!",
                    "AUGMENT (Gambler). Exaggerates the spell's luck: the good side doubles the gain (+50% becomes +100%), the bad "
                    "side is halved. Doubles the JACKPOT chance and each roll's Tension. All or nothing!"),
    "loaded_dice": ("Dados Viciados", "Loaded Dice",
                    "AUMENTO (Apostador). Resultado estável: tira o Azar Total, o Azar e a Sorte Grande do sorteio (metade da "
                    "chance de Jackpot). Nos Dados, toda face sai 3 ou mais.",
                    "AUGMENT (Gambler). Steady result: removes Bust, Bad and Big Luck from the draw (half the Jackpot chance). "
                    "On Dice, every face rolls 3 or higher."),
    "trigger_jackpot": ("Ao Jackpot", "On Jackpot",
                        "GATILHO (Apostador, no começo da receita). O slot sai SOZINHO, mirando o inimigo mais próximo, no "
                        "instante em que você tira um JACKPOT — já com o poder máximo. Apertar a tecla não conjura.",
                        "TRIGGER (Gambler, at the start of the recipe). The slot fires BY ITSELF, aiming at the nearest enemy, the "
                        "moment you hit a JACKPOT — already at maximum power. Pressing the key does not cast it."),
}
for gid, (pt, en, dpt, den) in G.items():
    k("glyph.isekai_mahou." + gid, pt, en)
    k("glyph.isekai_mahou.%s.desc" % gid, dpt, den)

# ------------------------------------------------------------------ lendas
k("legend.isekai_mahou.kinetic_royal_fan", "Leque Cinético Real", "Royal Kinetic Fan")
k("legend.isekai_mahou.eternal_fortune_casino", "Cassino da Fortuna Eterna", "Casino of Eternal Fortune")

# ------------------------------------------------------------------ sorte
BANDS = {"bust": ("Azar Total", "Bust"), "bad": ("Azar", "Bad Luck"), "low": ("Fraco", "Weak"), "even": ("Na Média", "Even"),
         "good": ("Bom", "Good"), "great": ("Ótimo", "Great"), "natural": ("Sorte Grande", "Big Luck"),
         "jackpot": ("JACKPOT!", "JACKPOT!")}
for b, (pt, en) in BANDS.items():
    k("luck.isekai_mahou.band." + b, pt, en)
HANDS = {"high_card": ("Carta Alta", "High Card"), "pair": ("Par", "Pair"), "two_pair": ("Dois Pares", "Two Pair"),
         "three": ("Trinca", "Three of a Kind"), "straight": ("Sequência", "Straight"), "flush": ("Flush", "Flush"),
         "full_house": ("Full House", "Full House"), "four": ("Quadra", "Four of a Kind"),
         "straight_flush": ("Straight Flush", "Straight Flush"), "royal_flush": ("ROYAL FLUSH", "ROYAL FLUSH")}
for h, (pt, en) in HANDS.items():
    k("poker.isekai_mahou." + h, pt, en)

# ------------------------------------------------------------------ HUD
k("hud.isekai_mahou.gambler.rolling", "Girando a sorte...", "Rolling the dice...")
k("hud.isekai_mahou.gambler.power", "Poder %s", "Power %s")
k("hud.isekai_mahou.gambler.fever", "Tensão %s", "Tension %s")
k("hud.isekai_mahou.gambler.jackpot", "Sorte infinita: %ss", "Infinite luck: %ss")
k("hud.isekai_mahou.gambler.challenge", "Rodada da Casa", "House Round")
k("hud.isekai_mahou.gambler.challenge_rank", "Rodada: %s", "Round: %s")
k("hud.isekai_mahou.gambler.challenge_line", "Abates %s/%s · %ss", "Kills %s/%s · %ss")
k("hud.isekai_mahou.gambler.challenge_win", "Você venceu a casa!", "You beat the house!")
k("hud.isekai_mahou.gambler.challenge_lose", "A casa venceu...", "The house wins...")

# ------------------------------------------------------------------ mensagens
M = "message.isekai_mahou.gambler."
k(M + "jackpot_banner", "JACKPOT! SORTE INFINITA!", "JACKPOT! INFINITE LUCK!")
k(M + "jackpot_start", "JACKPOT! %ss de sorte infinita · +%s Ficha(s) de Cassino", "JACKPOT! %ss of infinite luck · +%s Casino Chip(s)")
k(M + "jackpot_end", "A sorte infinita acabou.", "The infinite luck is over.")
k(M + "dodge", "Esquiva da Sorte!", "Lucky Dodge!")
k(M + "jinx_miss", "Azarado! O golpe errou.", "Jinxed! The attack missed.")
k(M + "flip_heads", "Cara!", "Heads!")
k(M + "flip_tails", "Coroa!", "Tails!")
k(M + "hand", "Mão Real: %s (×%s)", "Royal Hand: %s (×%s)")
k(M + "dice", "Dados: %s = %s · poder ×%s", "Dice: %s = %s · power ×%s")
k(M + "dice.snake_eyes", "Olhos de Cobra!", "Snake Eyes!")
k(M + "dice.boxcars", "DOZE!", "BOXCARS!")
k(M + "dice.doubles", "Dobradinha!", "Doubles!")
k(M + "roulette.green", "Roleta: %s VERDE! Poder ×%s", "Roulette: %s GREEN! Power ×%s")
k(M + "roulette.red", "Roleta: %s vermelho · poder ×%s", "Roulette: %s red · power ×%s")
k(M + "roulette.black", "Roleta: %s preto · poder ×%s, duas vezes", "Roulette: %s black · power ×%s, twice")
k(M + "slot.jackpot", "7-7-7! JACKPOT! %s fichas · poder ×%s", "7-7-7! JACKPOT! %s chips · power ×%s")
k(M + "slot.triple", "Trinca! %s fichas · poder ×%s", "Triple! %s chips · power ×%s")
k(M + "slot.pair", "Par: %s fichas · poder ×%s", "Pair: %s chips · power ×%s")
k(M + "slot.none", "Nada... %s ficha(s) · poder ×%s", "Nothing... %s chip(s) · power ×%s")
k(M + "trigger_only", "Esta magia tem o gatilho Ao Jackpot: ela sai sozinha quando você tira um JACKPOT.",
  "This spell has the On Jackpot trigger: it fires by itself when you hit a JACKPOT.")
k(M + "outfit_granted", "Círculo %s alcançado: você recebeu o traje %s (defesa %s, +%s de Foco).",
  "Circle %s reached: you received the %s outfit (defense %s, +%s Focus).")
k(M + "outfit_equipped", "O traje novo foi vestido; as peças antigas foram para a mochila.",
  "The new outfit was equipped; the old pieces went to your backpack.")
k(M + "outfit_inventory", "As peças foram para a mochila (você estava vestindo outra coisa).",
  "The pieces went to your backpack (you were wearing something else).")
k(M + "rank_banner", "FICHA DA CASA: %s", "HOUSE CHIP: %s")
k(M + "rank_up", "Você subiu de rank na Casa: %s! +%s XP", "You ranked up in the House: %s! +%s XP")
k(M + "challenge.banner", "RODADA DA CASA — %s", "HOUSE ROUND — %s")
k(M + "challenge.start", "Aposta de %s ficha(s) feita! Abata %s inimigos em %s segundos. Toda magia rola com Dobrar a Aposta: tudo ou nada!",
  "Bet of %s chip(s) placed! Defeat %s enemies in %s seconds. Every spell rolls with Double Down: all or nothing!")
k(M + "challenge.lose", "A Rodada acabou com %s/%s abates. A casa ficou com a aposta; a mesa reabre em 2 minutos.",
  "The Round ended with %s/%s kills. The house keeps the bet; the table reopens in 2 minutes.")
k(M + "challenge.missing", "Ainda falta para a próxima Ficha da Casa:", "Still missing for the next House Chip:")
k(M + "challenge.no_chips", "A aposta desta Rodada é de %s Fichas de Cassino.", "This Round's bet is %s Casino Chips.")
k(M + "challenge.not_gambler", "Só um Apostador senta nesta mesa.", "Only a Gambler sits at this table.")
k(M + "challenge.maxed", "Você já é o dono da casa (Ficha de Diamante).", "You already own the house (Diamond Chip).")
k(M + "challenge.retry", "A mesa reabre em %ss.", "The table reopens in %ss.")

# ------------------------------------------------------------------ ranks
RANKS = [("Ficha de Bronze", "Bronze Chip"), ("Ficha de Prata", "Silver Chip"), ("Ficha de Ouro", "Gold Chip"),
         ("Ficha de Platina", "Platinum Chip"), ("Ficha de Diamante", "Diamond Chip")]
for n, (pt, en) in enumerate(RANKS, 1):
    k("rank.isekai_mahou.gambler.%d" % n, pt, en)
k("rank.isekai_mahou.gambler.missing_level", "Nível %s e Círculo %s", "Level %s and Circle %s")
R = "rank.isekai_mahou.gambler.req."
k(R + "rolls", "Rolar a sorte %s vezes", "Roll your luck %s times")
k(R + "card_hit", "Acertar %s cartas", "Hit %s cards")
k(R + "jackpot", "Tirar %s Jackpots", "Hit %s Jackpots")
k(R + "big_win", "%s grandes vitórias (Ótimo ou melhor)", "%s big wins (Great or better)")
k(R + "lucky_kill", "%s abates de sorte (logo após um giro bom)", "%s lucky kills (right after a good roll)")
k(R + "casino", "Abrir o Cassino %s vezes", "Open the Casino %s times")
k(R + "boss", "Derrotar %s chefe(s)", "Defeat %s boss(es)")

# ------------------------------------------------------------------ aba Mesa da Casa
S = "screen.isekai_mahou.gambler."
k(S + "tab", "Mesa da Casa", "House Table")
k(S + "luck_title", "Sorte", "Luck")
k(S + "luck_points", "Pontos de Sorte: %s", "Luck points: %s")
k(S + "expected", "Poder médio por giro: ×%s", "Average power per roll: ×%s")
k(S + "fever", "Tensão: %s/100", "Tension: %s/100")
k(S + "odds", "Chances por giro", "Odds per roll")
k(S + "hint", "Toda magia sua rola a sorte. O azar enche a Tensão; em 100, o próximo giro é JACKPOT.",
  "Every spell of yours rolls luck. Bad luck fills Tension; at 100, the next roll is a JACKPOT.")
k(S + "hint_other", "Magias com glifos do Apostador (aprendidos por Lição) também rolam a sorte.",
  "Spells with Gambler glyphs (learned through a Lesson) also roll luck.")
k(S + "house", "Fichas da Casa", "House Chips")
k(S + "done", "Conquistada", "Earned")
k(S + "next", "Próxima", "Next")
k(S + "locked", "Bloqueada", "Locked")
k(S + "req_level", "Nível %s · Círculo %s", "Level %s · Circle %s")
k(S + "req_round", "Rodada da Casa: %s abates em %ss (aposta: %s fichas)", "House Round: %s kills in %ss (bet: %s chips)")
k(S + "reward", "Prêmio:", "Prize:")
k(S + "reward_deck", "Baralho %s", "Deck %s")
k(S + "reward_legends", "2 Lendas", "2 Legends")
k(S + "reward_xp", "+%s XP", "+%s XP")
k(S + "need_chips", "Precisa de %s Fichas de Cassino (você tem %s).", "Needs %s Casino Chips (you have %s).")
k(S + "round_tip", "Aposte as fichas e vença a Rodada: abata os inimigos antes do tempo acabar. Vencer devolve a aposta em dobro.",
  "Bet the chips and win the Round: defeat the enemies before time runs out. Winning pays the bet back double.")
k(S + "start", "Apostar %s e jogar a Rodada", "Bet %s and play the Round")

# ------------------------------------------------------------------ avisos (toasts)
k("toast.isekai_mahou.gambler.outfit", "Traje: %s", "Outfit: %s")
k("toast.isekai_mahou.gambler.outfit.sub", "Presente do Círculo %s", "Circle %s gift")
k("toast.isekai_mahou.gambler.rank", "%s", "%s")
k("toast.isekai_mahou.gambler.rank.sub", "Rank %s/%s da Casa", "House rank %s/%s")

# ------------------------------------------------------------------ dicas de itens
k("tooltip.isekai_mahou.gambler_deck.flavor", "\"A casa sempre ganha... a menos que a casa seja eu.\"",
  "\"The house always wins... unless I am the house.\"")
k("tooltip.isekai_mahou.gambler_deck.tier", "Grau %s", "Tier %s")
k("tooltip.isekai_mahou.gambler_deck.throw", "Clique direito: carta afiada (%s de dano; Ás = dobro)",
  "Right-click: razor card (%s damage; Ace = double)")
k("tooltip.isekai_mahou.gambler_deck.table", "Agachado + clique direito: Mesa da Casa", "Sneak + right-click: House Table")
k("tooltip.isekai_mahou.gambler_card", "Clique direito: arremessa a carta.", "Right-click: throw the card.")
k("tooltip.isekai_mahou.casino_chip", "A aposta da Rodada da Casa do Apostador. Cai dos Jackpots.",
  "The bet for the Gambler's House Round. Dropped by Jackpots.")
k("tooltip.isekai_mahou.casino_chip.flip", "Clique direito: cara ou coroa.", "Right-click: heads or tails.")
k("tooltip.isekai_mahou.gambler_outfit.tier", "Grau %s · %s (Círculo %s)", "Tier %s · %s (Circle %s)")
k("tooltip.isekai_mahou.gambler_outfit.set", "Traje completo: +1 de Sorte nas rolagens e +3% de esquiva da sorte",
  "Full outfit: +1 Luck on rolls and +3% lucky dodge")
k("tooltip.isekai_mahou.gambler_outfit.dealer", "Viseira verde, colete e gravata borboleta: quem dá as cartas.",
  "Green visor, vest and bow tie: the one who deals the cards.")
k("tooltip.isekai_mahou.gambler_outfit.high_roller", "Smoking de lapela vermelha para as apostas altas.",
  "Red-lapel tuxedo for the high stakes.")
k("tooltip.isekai_mahou.gambler_outfit.casino_king", "Terno branco e ouro: a casa é sua.", "White and gold suit: the house is yours.")
k("tooltip.isekai_mahou.gambler_outfit.fortune_lord", "Sobretudo onde os naipes brilham. A sorte obedece.",
  "A greatcoat where the suits glow. Luck obeys.")
for t, (pt, en) in {"dealer": ("Crupiê", "Dealer"), "high_roller": ("Mesa Alta", "High Roller"),
                    "casino_king": ("Rei do Cassino", "Casino King"), "fortune_lord": ("Senhor da Fortuna", "Fortune Lord")}.items():
    k("outfit.isekai_mahou.gambler." + t, pt, en)

# ------------------------------------------------------------------ comandos
k("command.isekai_mahou.gambler.fever", "Tensão ajustada para %s.", "Tension set to %s.")
k("command.isekai_mahou.gambler.rank", "Casa do Apostador: rank %s.", "Gambler's House: rank %s.")
k("command.isekai_mahou.gambler.luck", "Sorte: %s · Tensão: %s · poder médio ×%s · esquiva %s%%",
  "Luck: %s · Tension: %s · average power ×%s · dodge %s%%")

# ------------------------------------------------------------------ pacote de tecnicas de anime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from class_gambler_anime_lang import ENTRIES as _ANIME  # noqa: E402

T.update(_ANIME)

out = os.path.join(ROOT, "src/main/lang/class_gambler")
for idx, lang in ((0, "pt_br"), (1, "en_us")):
    data = {key: v[idx] for key, v in sorted(T.items())}
    with open(os.path.join(out, lang + ".json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
print(len(T), "chaves")
