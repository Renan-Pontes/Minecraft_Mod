"""Onda 8 (water_city) — traduções da CIDADE DAS ÁGUAS: fundação sobre a água, prévia, Carta, painel, dicas, comandos.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w8_water_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
O outro agente da onda 8 (city_looks) grava no mesmo JSON: se o arquivo mudar entre ler e gravar, o script relê e
grava de novo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- tipo de terreno e fundação
    "water.terrain": ("Sobre a Água", "On the Water"),
    "water.found.aim": ("Mire na água: o Coração de uma Cidade das Águas nasce em cima dela.",
                        "Aim at the water: a Water City's Heart is born right on top of it."),
    "water.found.dry": (
        "Pouca água aqui: só %s%% do território é água funda (precisa de %s%%). Procure mar aberto, um lago grande ou um rio largo.",
        "Not enough water here: only %s%% of the territory is deep water (it takes %s%%). Look for open sea, a big lake or a wide river."),
    "water.found.structure": ("Há um monumento ou uma construção antiga neste trecho de água. Escolha outro lugar.",
                              "A monument or an old structure sits in these waters. Pick another spot."),
    "water.found.announce": ("%s fundou %s (%s), uma cidade sobre as águas!", "%s founded %s (%s), a city upon the waters!"),
    "water.found.start": ("A praça sobe sobre estacas. Cada obra traz o próprio deque.",
                          "The plaza rises on stilts. Every building brings its own deck."),
    # ---- prévia (uma linha acima da barra de itens)
    "water.place.ground": ("Prévia da cidade · Botão direito: fixar aqui · R: girar a praça · Tab: trocar o terreno",
                           "City preview · Right-click: pin here · R: rotate the plaza · Tab: change terrain"),
    "water.place.free": ("Cidade das Águas · Mire na água · Botão direito: fixar aqui · R: girar a praça · Tab: trocar o terreno",
                         "Water City · Aim at the water · Right-click: pin here · R: rotate the plaza · Tab: change terrain"),
    "water.place.pinned": ("Cidade das Águas · Setas: mover · R: girar · Botão direito: fundar sobre a água · Tab: trocar o terreno",
                           "Water City · Arrows: move · R: rotate · Right-click: found on the water · Tab: change terrain"),
    # ---- Carta de Fundação
    "water.ui.pick.tip": (
        "A cidade nasce em cima da água, em deques e passarelas sobre estacas, com canais para andar de barco. Combina com qualquer estilo.",
        "The city is born on top of the water, on decks and boardwalks over stilts, with canals to boat through. Works with any style."),
    "water.ui.pick.cost": ("Sem custo extra: o custo é achar o lugar — água funda em pelo menos 60%% do território.",
                           "No extra cost: the cost is finding the spot — deep water under at least 60%% of the territory."),
    "water.ui.pick.preview": ("Feche a carta (Esc) e mire na água para ver a cidade na prévia; suas escolhas ficam guardadas.",
                              "Close the charter (Esc) and aim at the water to see the city in the preview; your choices are kept."),
    "water.ui.share": ("Água em %s%% do território", "Water under %s%% of the territory"),
    "water.ui.checking": ("Conferindo a água...", "Checking the water..."),
    # ---- painel (Geral)
    "water.ui.line": ("Cidade das Águas", "Water City"),
    "water.ui.trait": ("Porto: +%s de comida por dia", "Harbor: +%s food a day"),
    "water.ui.tip.deck": (
        "A cidade fica sobre a água: cada obra ergue o próprio deque sobre estacas, e passarelas ligam os lotes à praça.",
        "The city stands on the water: every building raises its own deck on stilts, and boardwalks link the plots to the plaza."),
    "water.ui.tip.canal": (
        "Lote sem obra continua sendo água. Um canal dá a volta em cada muralha: há barcos no cais, na frente da praça, e as pontes dos portões deixam o barco passar por baixo.",
        "A plot with no building stays water. A canal circles every wall: boats wait at the quay, in front of the plaza, and the gate bridges let a boat pass underneath."),
    "water.ui.tip.trait": (
        "Traço — Porto: a pesca dá +%s de comida por dia (cresce com o porte da cidade) e o povo daqui prende o fôlego o dobro do tempo debaixo d'água no território.",
        "Trait — Harbor: fishing brings +%s food a day (more as the city ranks up) and its people hold their breath twice as long underwater within the territory."),
    # ---- comandos de teste
    "cmd.water.only": ("Nenhuma Cidade das Águas por perto. Para criar uma: /mahou city water found [estilo]",
                       "No Water City nearby. To create one: /mahou city water found [style]"),
    "cmd.water.none": ("%s não é uma Cidade das Águas.", "%s is not a Water City."),
    "cmd.water.state": (
        "%s — Cidade das Águas · água em y %s · passarelas: %s trechos (%s colunas), %s%% conferido · enfeites: %s de %s · barcos no cais: %s",
        "%s — Water City · water at y %s · boardwalks: %s stretches (%s columns), %s%% checked · decorations: %s of %s · boats at the quay: %s"),
    "cmd.water.finished": ("Passarelas, pontes e cais prontos: %s blocos postos agora.",
                           "Boardwalks, bridges and quay done: %s blocks placed just now."),
    "cmd.water.redo": ("Enfeites tirados: a cidade refaz passarelas, lanternas e cais nos próximos segundos.",
                       "Decorations removed: the city rebuilds boardwalks, lanterns and the quay over the next seconds."),
    "cmd.water.drop": ("%s foi jogado na água. Tem de voltar ao piso sozinho em até 5 segundos.",
                       "%s was thrown into the water. They must be back on the deck on their own within 5 seconds."),
    "cmd.water.drop.none": ("Nenhum NPC desta cidade a menos de 48 blocos de você (ou não há água perto dele).",
                            "No NPC of this city within 48 blocks of you (or no water close to them)."),
}

# chaves sem o prefixo do módulo
RAW = {
    "hint.isekai_mahou.city_water_found": (
        "Sua cidade nasceu sobre a água! Cada obra ergue o próprio deque; lote sem obra continua sendo canal. Há barcos no cais, na frente da praça.",
        "Your city was born on the water! Every building raises its own deck; plots you leave empty stay as canals. Boats wait at the quay, in front of the plaza."),
    "hint.isekai_mahou.city_water_boat": (
        "Um canal dá a volta em cada muralha, e as pontes dos portões são em arco: o barco passa por baixo.",
        "A canal circles every wall, and the gate bridges are arched: your boat fits underneath."),
}


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    for _ in range(5):
        stamp = os.stat(path).st_mtime_ns
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        added = 0
        for k, v in T.items():
            added += (P + k) not in data
            data[P + k] = v[idx]
        for k, v in RAW.items():
            added += k not in data
            data[k] = v[idx]
        # outro gerador gravou enquanto este lia: relê e tenta de novo
        if os.stat(path).st_mtime_ns != stamp:
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("%s: %d chaves da Cidade das Águas (%d novas)" % (fname, len(T) + len(RAW), added))
        return
    raise SystemExit("%s mudou a cada tentativa: rode de novo" % fname)


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
