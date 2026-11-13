"""Onda 6 (city_trades) — traduções dos COMERCIANTES DE OFÍCIO: ofícios, falas, serviços, painel, Diário, dicas.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w6_trades_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). Pode rodar de novo quantas vezes quiser; rode DEPOIS de qualquer gerador
antigo do módulo (settlements_lang.py, settlements_v14_lang.py, settlements_styles_lang.py), que reescrevem o arquivo.
O gerador de monumentos (settlements_w6_monuments_lang.py) mexe no mesmo arquivo: não rode os dois ao mesmo tempo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# ofício: (nome pt, nome en, nome pt no feminino — o NPC mulher usa este)
JOBS = {
    "blacksmith": ("Ferreiro", "Blacksmith", "Ferreira"),
    "healer": ("Curandeiro", "Healer", "Curandeira"),
    "scribe": ("Escriba", "Scribe", "Escriba"),
    "innkeeper": ("Taverneiro", "Innkeeper", "Taverneira"),
    "stablehand": ("Estribeiro", "Stablehand", "Estribeira"),
    "jeweler": ("Joalheiro", "Jeweler", "Joalheira"),
    "lumberjack": ("Lenhador", "Lumberjack", "Lenhadora"),
    "miner": ("Minerador", "Miner", "Mineradora"),
    "arcanist": ("Mercador Arcano", "Arcane Merchant", "Mercadora Arcana"),
}

# ofício: o que ele faz (conversa, página 1)
TALK = {
    "blacksmith": ("Ferramentas, armas e armaduras de ferro, feitas aqui mesmo. Peça gasta? Agache e clique com ela na mão: eu "
                   "conserto. Inteira, eu reforço para durar mais.",
                   "Iron tools, weapons and armor, made right here. Worn piece? Sneak and click with it in hand: I repair it. "
                   "If it is whole, I reinforce it so it lasts longer."),
    "healer": ("Poções de vida e remédios para o caminho. Se chegar machucado ou envenenado, agache e clique: eu cuido de você.",
               "Health potions and remedies for the road. If you come in hurt or poisoned, sneak and click: I will patch you up."),
    "scribe": ("Papel, tinta, livros e pergaminhos em branco para as suas receitas. Às vezes aparece uma página perdida por aqui.",
               "Paper, ink, books and blank scrolls for your recipes. Now and then a lost page turns up here."),
    "innkeeper": ("Comida quente e bebida para a estrada. E quem serve o balcão ouve de tudo: agache e clique que eu conto um boato.",
                  "Hot food and drink for the road. And whoever tends the bar hears everything: sneak and click and I will share a rumor."),
    "stablehand": ("Selas, guias, feno e tudo o que a sua montaria precisa. Com o estábulo maior eu arranjo armaduras e rédeas.",
                   "Saddles, leads, hay and all your mount needs. With a bigger stable I can get armor and reins."),
    "jeweler": ("Compro ouro e pedras, vendo gemas lascadas e o cinzel. As gemas da vitrine mudam todo dia.",
                "I buy gold and stones, and sell chipped gems and the chisel. The gems in the window change every day."),
    "lumberjack": ("Toras e tábuas da nossa terra, sempre. Nos dias bons chega madeira de fora também.",
                   "Logs and planks from our land, always. On good days wood from afar comes in too."),
    "miner": ("Pedra, carvão e o que a mina der. Quanto mais fundo a mina, melhor o minério.",
              "Stone, coal and whatever the mine gives. The deeper the mine, the better the ore."),
    "arcanist": ("Cristais de mana, poções de mana e essências. As essências dos elementos mudam a cada dia.",
                 "Mana crystals, mana potions and essences. The elemental essences change every day."),
}

# ofício: o que vende/faz (dica do painel, uma linha)
SELLS = {
    "blacksmith": ("Ferramentas, armas e armaduras de ferro. Serviço: conserta e reforça a peça da mão.",
                   "Iron tools, weapons and armor. Service: repairs and reinforces the piece in your hand."),
    "healer": ("Poções de vida, cura e resistência. Serviço: vida cheia e sem efeitos ruins.",
               "Health, healing and resistance potions. Service: full health and no bad effects."),
    "scribe": ("Papel, tinta, livros, pergaminhos em branco e páginas perdidas.",
               "Paper, ink, books, blank scrolls and lost pages."),
    "innkeeper": ("Comida, pergaminhos de retorno e incenso. Serviço: boatos (de graça).",
                  "Food, return scrolls and incense. Service: rumors (free)."),
    "stablehand": ("Selas, guias, feno, armaduras de cavalo, apito e rédeas.",
                   "Saddles, leads, hay, horse armor, whistle and reins."),
    "jeweler": ("Cinzel e gemas lascadas (mudam todo dia); compra ouro e pedras.",
                "Chisel and chipped gems (change daily); buys gold and stones."),
    "lumberjack": ("Toras, tábuas, carvão vegetal, andaimes e madeira de outras terras.",
                   "Logs, planks, charcoal, scaffolding and wood from other lands."),
    "miner": ("Pedra, carvão, ferro, cobre, redstone, ouro e, na Cidade, diamante.",
              "Stone, coal, iron, copper, redstone, gold and, at City rank, diamond."),
    "arcanist": ("Cristais e poções de mana, essências (mudam todo dia) e pergaminhos em branco.",
                 "Mana crystals and potions, essences (change daily) and blank scrolls."),
}

# ofício: falas — (olá ×2, ambiente ×3, obrigado ×2), cada uma (pt, en)
LINES = {
    "blacksmith": {
        "hello": [("Ferro quente e preço justo!", "Hot iron, fair price!"), ("O que vai ser? Lâmina ou ferramenta?", "What will it be? Blade or tool?")],
        "ambient": [("Esse aço vai aguentar um dragão.", "This steel will hold against a dragon."),
                    ("Mais um golpe e a lâmina canta.", "One more blow and the blade sings."),
                    ("Peça gasta eu conserto num instante.", "A worn piece I fix in no time.")],
        "thanks": [("Bem batido. Cuide dela!", "Well forged. Take care of it!"), ("Volte quando a lâmina cegar.", "Come back when the blade dulls.")],
    },
    "healer": {
        "hello": [("Deixe-me ver esse machucado.", "Let me see that wound."), ("Ervas frescas, colhidas hoje.", "Fresh herbs, picked today.")],
        "ambient": [("Descanse um pouco, aventureiro.", "Rest a little, adventurer."),
                    ("Uma poção no bolso vale por dois escudos.", "A potion in your pocket is worth two shields."),
                    ("Nada de lutar de barriga vazia!", "No fighting on an empty stomach!")],
        "thanks": [("Pronto. Cuide-se lá fora.", "There. Take care out there."), ("Vá com calma por uns dias.", "Take it easy for a few days.")],
    },
    "scribe": {
        "hello": [("Silêncio, por favor... ah, um cliente!", "Quiet, please... ah, a customer!"), ("Papel novo, tinta fresca.", "New paper, fresh ink.")],
        "ambient": [("Toda grande magia começa num rascunho.", "Every great spell starts as a draft."),
                    ("Quem escreve não esquece.", "Who writes does not forget."),
                    ("Esta página fala de um lugar distante...", "This page speaks of a faraway place...")],
        "thanks": [("Boa leitura!", "Happy reading!"), ("Anotado no livro-caixa.", "Noted in the ledger.")],
    },
    "innkeeper": {
        "hello": [("Senta aí! O que vai querer?", "Have a seat! What will you have?"), ("A panela acabou de sair do fogo.", "The pot just came off the fire.")],
        "ambient": [("Aqui se ouve de tudo, amigo.", "You hear everything in here, friend."),
                    ("Barriga cheia, espada firme.", "Full belly, steady sword."),
                    ("Hoje a casa está animada!", "The house is lively today!")],
        "thanks": [("Bom apetite!", "Enjoy your meal!"), ("A próxima rodada você paga também.", "The next round is on you too.")],
    },
    "stablehand": {
        "hello": [("Cuidado onde pisa! Diga lá.", "Watch your step! Go on."), ("Sua montaria merece o melhor.", "Your mount deserves the best.")],
        "ambient": [("Bicho bem tratado corre o dobro.", "A well-kept beast runs twice as far."),
                    ("Feno fresco chegou hoje cedo.", "Fresh hay came in this morning."),
                    ("Calma, calma, garoto...", "Easy, easy, boy...")],
        "thanks": [("Boa viagem!", "Safe travels!"), ("Traga ela aqui para um descanso.", "Bring her by for a rest.")],
    },
    "jeweler": {
        "hello": [("Olhe, mas com cuidado.", "Look, but carefully."), ("Cada pedra tem a sua luz.", "Every stone has its own light.")],
        "ambient": [("Esse corte ficou perfeito.", "That cut came out perfect."),
                    ("Ouro bom eu reconheço de longe.", "I can tell good gold from afar."),
                    ("Amanhã chegam outras pedras.", "Other stones arrive tomorrow.")],
        "thanks": [("Uma joia de escolha.", "A gem of a choice."), ("Brilhe por aí!", "Go and shine!")],
    },
    "lumberjack": {
        "hello": [("Madeira boa, cortada hoje!", "Good wood, cut today!"), ("Quantas toras vai levar?", "How many logs will you take?")],
        "ambient": [("Para cada árvore que cai, planto duas.", "For every tree that falls, I plant two."),
                    ("Machado afiado, dia curto.", "Sharp axe, short day."),
                    ("Essa tora dá uma viga e tanto.", "This log makes one fine beam.")],
        "thanks": [("Vai construir bonito!", "Build something fine!"), ("Tem mais de onde veio essa.", "There is more where that came from.")],
    },
    "miner": {
        "hello": [("Saiu da mina agora há pouco.", "Fresh out of the mine."), ("Pedra ou minério?", "Stone or ore?")],
        "ambient": [("Lá embaixo tem um veio dos bons.", "There is a fine vein down there."),
                    ("Tocha nunca é demais.", "You can never have too many torches."),
                    ("Quem cava fundo acha coisa boa.", "Dig deep, find good things.")],
        "thanks": [("Bom proveito!", "Put it to good use!"), ("Amanhã tem carga nova.", "A new load comes tomorrow.")],
    },
    "arcanist": {
        "hello": [("A mana está inquieta hoje.", "The mana is restless today."), ("Procura algo... incomum?", "Looking for something... unusual?")],
        "ambient": [("Sinto um bom fluxo nesta cidade.", "I feel a good flow in this city."),
                    ("Um cristal a mais nunca atrapalha.", "One more crystal never hurts."),
                    ("As essências mudam com os dias.", "The essences change with the days.")],
        "thanks": [("Use com sabedoria.", "Use it wisely."), ("Que a sua mana nunca seque.", "May your mana never run dry.")],
    },
}

# boatos do Taverneiro: dicas VERDADEIRAS sobre a cidade
RUMORS = [
    ("Dizem que caravana para cidade distante paga bem mais — mas a estrada é mais perigosa.",
     "They say a caravan to a distant city pays much more — but the road is more dangerous."),
    ("Os comerciantes enchem as prateleiras todo amanhecer. Quem chega cedo leva o melhor.",
     "The tradesmen restock every dawn. Early birds get the best."),
    ("O Ferreiro me contou: com a Ferraria maior ele conserta mais barato e reforça melhor.",
     "The Blacksmith told me: with a bigger Smithy he repairs cheaper and reinforces better."),
    ("Povo feliz trabalha mais rápido. Um festival de vez em quando faz milagre nas obras.",
     "Happy folk work faster. A festival now and then does wonders for the works."),
    ("Cada prédio melhorado traz mais mercadoria para a loja de quem trabalha nele.",
     "Every upgraded building brings more goods to the shop of whoever works in it."),
    ("As coisas raras só aparecem quando a cidade sobe de degrau. Burgo, Cidade, Capital...",
     "The rare things only show up when the city ranks up. Town, City, Capital..."),
    ("O Senhor leva uma parte de cada venda. Cidade com comércio movimentado tem cofre cheio.",
     "The Lord takes a share of every sale. A city with busy trade has a full treasury."),
    ("Uma torre de vigia deixa os batedores avisarem antes de um ataque. Eu fico sabendo primeiro, claro.",
     "A watchtower lets the scouts warn before a raid. I hear it first, of course."),
]

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- conversa
    "trade.talk.shop": ("Minha loja está no nível %s: %s de %s ofertas abertas. Prateleiras cheias de novo todo amanhecer. Melhore o "
                        "prédio e suba o degrau da cidade para o resto.",
                        "My shop is level %s: %s of %s offers open. Shelves are full again every dawn. Upgrade the building and "
                        "rank up the city for the rest."),
    "trade.talk.shop.full": ("Minha loja está no nível %s: todas as %s de %s ofertas abertas. Prateleiras cheias de novo todo amanhecer.",
                             "My shop is level %s: all %s of %s offers open. Shelves are full again every dawn."),
    # ---- chegada e Diário
    "trade.arrived": ("%s chegou e abriu a loja: %s", "%s arrived and opened shop: %s"),
    "trade.diary.arrived": ("%s chegou e abriu a loja: %s", "%s arrived and opened shop: %s"),
    "trade.diary.day": ("Comerciantes: %s negócios ontem, +%s moedas de taxa no cofre", "Tradesmen: %s deals yesterday, +%s coins of fee in the treasury"),
    # ---- o que o prédio traz (planejador, inauguração)
    "trade.fx": ("%s (%s ofertas)", "%s (%s offers)"),
    # ---- o que o degrau da cidade dá aos comerciantes (aba Porte)
    "trade.gain.rare": ("Comerciantes de ofício: +10%% de estoque e %s itens raros novos",
                        "Tradesmen: +10%% stock and %s new rare items"),
    "trade.gain.stock": ("Comerciantes de ofício: +10%% de estoque", "Tradesmen: +10%% stock"),
    # ---- serviços
    "trade.service.poor": ("Faltam moedas: o serviço custa %s.", "Not enough coins: the service costs %s."),
    "trade.smith.repair.ask": ("Consertar %s por %s moedas? Agache e clique de novo.", "Repair %s for %s coins? Sneak and click again."),
    "trade.smith.repair.done": ("%s consertado!", "%s repaired!"),
    "trade.smith.reinforce.ask": ("Reforçar %s (Inquebrável %s) por %s moedas? Agache e clique de novo.",
                                  "Reinforce %s (Unbreaking %s) for %s coins? Sneak and click again."),
    "trade.smith.reinforce.done": ("%s reforçado: Inquebrável %s.", "%s reinforced: Unbreaking %s."),
    "trade.smith.reinforce.locked": ("Para reforçar mais, a Ferraria precisa do nível %s.", "To reinforce further, the Smithy needs level %s."),
    "trade.smith.fine": ("Essa peça já está no melhor que eu sei fazer.", "That piece is already the best I can make it."),
    "trade.heal.ask": ("Tratamento por %s moedas (vida cheia, sem efeitos ruins)? Agache e clique de novo.",
                       "Treatment for %s coins (full health, no bad effects)? Sneak and click again."),
    "trade.heal.done": ("Você está novo em folha!", "You are good as new!"),
    "trade.rumor.intro": ("Chega mais... ouvi uma coisa no balcão.", "Come closer... I heard something at the bar."),
    "trade.rumor.raid": ("Os batedores comentaram: monstros devem atacar a cidade em %s dia(s). Reforce a guarda.",
                         "The scouts were talking: monsters should attack the city in %s day(s). Strengthen the guard."),
    "trade.rumor.raid.today": ("Os batedores entraram correndo: o ataque dos monstros é para hoje!",
                               "The scouts came running in: the monster raid is today!"),
    # ---- comando de teste
    "trade.cmd.head": ("Comerciantes de %s — negócios hoje: %s · taxa hoje: %s · taxa total: %s",
                       "Tradesmen of %s — deals today: %s · fee today: %s · fee total: %s"),
    "trade.cmd.missing": ("  ✘ %s — falta o prédio (%s)", "  ✘ %s — building missing (%s)"),
    "trade.cmd.row": ("  ✔ %s — loja nível %s · %s de %s ofertas", "  ✔ %s — shop level %s · %s of %s offers"),
    "trade.cmd.row.away": ("  … %s — a caminho · loja nível %s · %s de %s ofertas", "  … %s — on the way · shop level %s · %s of %s offers"),
    # ---- painel: sub-aba Comerciantes (dentro de Caravanas)
    "ui.page.shops": ("Comerciantes", "Tradesmen"),
    "ui.shops.title": ("Comerciantes de ofício: %s de %s", "Tradesmen: %s of %s"),
    "ui.shops.here": ("%s nv. %s · %s/%s ofertas", "%s lv. %s · %s/%s offers"),
    "ui.shops.coming": ("%s nv. %s · a caminho", "%s lv. %s · on the way"),
    "ui.shops.missing": ("falta: %s", "missing: %s"),
    "ui.shops.missing.rank": ("falta: %s (%s)", "missing: %s (%s)"),
    "ui.shops.fee": ("Taxa do Senhor: %s%% das vendas e serviços", "Lord's fee: %s%% of sales and services"),
    "ui.shops.fee.today": ("Hoje: %s negócios · +%s moedas", "Today: %s deals · +%s coins"),
    "ui.shops.fee.last": ("Ontem: %s negócios · +%s moedas", "Yesterday: %s deals · +%s coins"),
    "ui.shops.fee.total": ("Total no cofre: %s moedas", "Total into the treasury: %s coins"),
    "ui.shops.hint": ("Clique num comerciante para negociar; agache e clique para o serviço dele. O estoque é da cidade e volta todo "
                      "amanhecer.",
                      "Click a tradesman to trade; sneak and click for his service. Stock belongs to the city and comes back every dawn."),
    "ui.shops.tip.more": ("Melhore o prédio: mais ofertas e mais estoque. Os itens raros pedem um degrau maior da cidade.",
                          "Upgrade the building: more offers and more stock. Rare items need a higher city rank."),
    "ui.shops.tip.all": ("Todas as ofertas abertas.", "All offers open."),
    "ui.shops.tip.build": ("Construa %s para trazer este comerciante.", "Build %s to bring this tradesman."),
    "ui.shops.tip.build.rank": ("Construa %s (liberado no degrau %s) para trazer este comerciante.",
                                "Build %s (unlocked at rank %s) to bring this tradesman."),
    "ui.shops.tip.damaged": ("Prédio danificado: atende como nível 1 até o conserto.", "Damaged building: serves as level 1 until repaired."),
}

RAW = {
    "entity.isekai_mahou.settlement_tradesman": ("Comerciante de Ofício", "Tradesman"),
    "item.isekai_mahou.settlement_tradesman_spawn_egg": ("Ovo de Comerciante de Ofício", "Tradesman Spawn Egg"),
    "hint.isekai_mahou.city_trades": (
        "Comerciante de ofício: o estoque é da cidade e volta todo amanhecer. Agache e clique para o serviço dele (conserto, cura, boato).",
        "Tradesman: the stock belongs to the city and comes back every dawn. Sneak and click for his service (repair, healing, rumor)."),
    "hint.isekai_mahou.city_trades_new": (
        "Um comerciante de ofício chegou! Ferraria, Taverna, Biblioteca... cada prédio traz o seu. Veja em Caravanas → Comerciantes.",
        "A tradesman has arrived! Smithy, Tavern, Library... each building brings its own. See Caravans → Tradesmen."),
}

for job, (pt, en, pt_f) in JOBS.items():
    T["trade.job." + job] = (pt, en)
    T["trade.job." + job + ".f"] = (pt_f, en)
    T["trade.talk." + job] = TALK[job]
    T["ui.shops.tip.sells." + job] = SELLS[job]
    for group, lines in LINES[job].items():
        for i, pair in enumerate(lines):
            T["trade.line.%s.%s.%d" % (job, group, i)] = pair
for i, pair in enumerate(RUMORS):
    T["trade.rumor.%d" % i] = pair


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    added = 0
    for k, v in T.items():
        added += (P + k) not in data
        data[P + k] = v[idx]
    for k, v in RAW.items():
        added += k not in data
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d chaves dos comerciantes de oficio (%d novas)" % (fname, len(T) + len(RAW), added))


def check(fname):
    """Confere que todas as chaves ficaram no arquivo (outro gerador pode ter gravado ao mesmo tempo)."""
    with open(os.path.join(LANG, fname), encoding="utf-8") as f:
        data = json.load(f)
    missing = [k for k in list(P + k for k in T) + list(RAW) if k not in data]
    if missing:
        raise SystemExit("%s: faltam %d chaves (rode de novo): %s" % (fname, len(missing), missing[:5]))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
    check("pt_br.json")
    check("en_us.json")
