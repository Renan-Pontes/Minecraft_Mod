"""Onda 9 (isles) — traduções do ARQUIPÉLAGO: Ilha Celeste e Cidade das Águas com uma ilha/plataforma por lote, pontes,
poço da Corrente Ascendente no meio, comandos de teste.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w9_isles_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo: eles apagam o trabalho das ondas
seguintes. Se o arquivo mudar entre ler e gravar (outro gerador), o script relê e grava de novo.
Algumas dicas e textos de escolha da Carta foram REESCRITOS para valer para as cidades novas (as dicas só aparecem uma
vez, na fundação; as cidades antigas já as viram).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- planta
    "isle.no_free": ("Na cidade de ilhas cada prédio ganha a sua ilha num terreno da planta: escolha o terreno no painel (Planejar).",
                     "In an island city every building gets its own island on a plot of the map: pick the plot in the panel (Plan)."),
    "isle.formed": ("Uma ilha nova se formou em %s: a ponte até ela vem logo em seguida.",
                    "A new island has formed in %s: the bridge to it follows shortly."),
    "isle.diary.core": ("A ilha do meio se formou bem acima das nuvens: praça e poço da Corrente Ascendente.",
                        "The central island formed high above the clouds: plaza and the Updraft shaft."),
    "isle.diary.shaft": ("O poço da Corrente Ascendente está pronto no meio da ilha (marco no chão em x %s, z %s).",
                         "The Updraft shaft is ready in the middle of the island (ground marker at x %s, z %s)."),
    "isle.shaft.built": ("Corrente Ascendente pronta, no meio da cidade. Ela toca o chão em x %s, z %s.",
                         "Updraft ready, in the middle of the city. It touches the ground at x %s, z %s."),
    # ---- painel (dicas do Geral)
    "isle.ui.tip.land": (
        "A cidade é um arquipélago no céu: a ilha do meio tem a praça, e cada lote ganha a SUA ilha quando você manda construir nele — ligada por uma ponte.",
        "The city is an archipelago in the sky: the central island holds the plaza, and every plot gets its OWN island when you order a building there — linked by a bridge."),
    "isle.ui.tip.grow": (
        "Suba e desça pela Corrente Ascendente: o poço fica no meio da ilha do centro, ao lado da praça (agachado, você desce).",
        "Go up and down through the Updraft: its shaft is in the middle of the central island, next to the plaza (sneak to go down)."),
    "isle.water.tip.deck": (
        "A cidade é um arquipélago sobre a água: cada obra ergue a SUA plataforma sobre estacas, separada das outras por água aberta e ligada por uma ponte em arco.",
        "The city is an archipelago on the water: every building raises its OWN platform on stilts, separated from the others by open water and linked by an arched bridge."),
    "isle.water.tip.canal": (
        "Lote sem obra continua sendo mar. Os barcos ficam no porto de dentro, num canto da praça, e passam por baixo das pontes. A muralha vira um quebra-mar em volta das plataformas.",
        "A plot with no building stays open sea. Boats wait in the inner harbor, at a corner of the plaza, and pass under the bridges. The wall becomes a breakwater around the platforms."),
    # ---- Carta de Fundação (reescritos: a cidade nova é sempre um arquipélago)
    "sky.ui.pick.tip": (
        "A cidade nasce num arquipélago bem acima das nuvens: uma ilha no meio, com a Corrente Ascendente, e uma ilha para cada lote, ligadas por pontes. Combina com qualquer estilo.",
        "The city is born as an archipelago high above the clouds: a central island with the Updraft, and one island for each plot, linked by bridges. Works with any style."),
    "water.ui.pick.tip": (
        "A cidade nasce em cima da água: uma plataforma para a praça e uma para cada lote, sobre estacas, ligadas por pontes em arco — dá para andar de barco entre elas. Combina com qualquer estilo.",
        "The city is born on top of the water: a platform for the plaza and one for each plot, on stilts, linked by arched bridges — you can boat between them. Works with any style."),
    # ---- comandos de teste
    "cmd.isle.state": ("%s — Ilha Celeste (arquipélago) · altitude %s · ilhas: %s (%s prontas; %s de lote, %s de passagem, %s de vigia) · blocos de ponte: %s · %s%%",
                       "%s — Sky Island (archipelago) · altitude %s · islands: %s (%s ready; %s plot, %s stepping, %s watch) · bridge blocks: %s · %s%%"),
    "cmd.isle.only": ("Só numa Ilha Celeste de ilhas (as fundadas a partir desta versão).",
                      "Only in an island-style Sky Island (founded from this version on)."),
    "cmd.isle.center": ("O terreno 0 0 é a praça: ela já está na ilha do meio.", "Plot 0 0 is the plaza: it already sits on the central island."),
    "cmd.isle.forced": ("Ilha do terreno %s %s na fila (com as ilhotas de passagem e as pontes até a praça).",
                        "Island for plot %s %s queued (with the stepping islets and bridges to the plaza)."),
    "cmd.isle.exists": ("O terreno %s %s já tem ilha.", "Plot %s %s already has an island."),
    # ---- onda 9b: fundar mais fácil ("devemos facilitar começar a cidade agora... inclusive a sky e a water")
    "found.move_away": ("Perto demais de %s (%s blocos). Afaste-se mais uns %s blocos dali e tente de novo.",
                        "Too close to %s (%s blocks). Move about %s more blocks away from it and try again."),
    "found.full": ("Inventário cheio: abra um espaço para a Carta de Fundação (e para o Núcleo Celeste, se for no céu).",
                   "Inventory full: free a slot for the Founding Charter (and for the Sky Core, if founding in the sky)."),
    "found.far": ("O ponto escolhido ficou longe demais: chegue mais perto e tente de novo.",
                  "The chosen spot is too far away: get closer and try again."),
    "sky.ui.core.recipe": (
        "Como conseguir: o Alquimista da cidade inicial vende, ou fabrique com 2 Penas, 2 Fragmentos de Ametista e 1 Cristal de Mana.",
        "How to get it: the Alchemist in the starting city sells it, or craft it with 2 Feathers, 2 Amethyst Shards and 1 Mana Crystal."),
    "water.ui.pick.cost": (
        "Sem custo extra: o custo é achar o lugar — água funda sob pelo menos 35%% do território (mar, baía, lago grande ou rio largo).",
        "No extra cost: the cost is finding the spot — deep water under at least 35%% of the territory (sea, bay, big lake or wide river)."),
}

# chaves sem o prefixo do módulo (dicas: reescritas para a cidade de ilhas, e genéricas o bastante para as antigas)
RAW = {
    "hint.isekai_mahou.city_sky_found": (
        "Sua cidade nasceu num arquipélago acima das nuvens! Primeiro se forma a ilha do meio; cada lote ganha a sua ilha, com ponte, quando você manda construir nele.",
        "Your city was born as an archipelago above the clouds! The central island forms first; each plot gets its own island, with a bridge, when you order a building there."),
    "hint.isekai_mahou.city_sky_updraft": (
        "Corrente Ascendente: entre na coluna brilhante para subir até a cidade. Agachado, você desce devagar.",
        "Updraft: step into the glowing column to rise to the city. Sneak to float down slowly."),
    "hint.isekai_mahou.city_sky_fall": (
        "Cair da Ilha Celeste não mata: a ilha amortece a queda. Para voltar, procure o anel de pedra no chão, debaixo da Corrente Ascendente.",
        "Falling off the Sky Island won't kill you: the island softens the fall. To get back, look for the stone ring on the ground, under the Updraft."),
    "hint.isekai_mahou.city_water_found": (
        "Sua cidade nasceu sobre a água! Cada obra ergue a própria plataforma, ligada por ponte; lote sem obra continua sendo água. Há barcos no cais.",
        "Your city was born on the water! Every building raises its own platform, linked by a bridge; plots you leave empty stay as water. Boats wait at the quay."),
    "hint.isekai_mahou.city_water_boat": (
        "As pontes em arco deixam o barco passar por baixo: dá para dar a volta na cidade de barco.",
        "The arched bridges let your boat pass underneath: you can sail around the city."),
    # ---- onda 9b: fundar mais fácil
    "hint.isekai_mahou.city_how": (
        "Que tal a SUA cidade? Compre a Carta de Fundação com o Escriba da cidade inicial (ou fabrique: livro e pena, 2 lingotes de ouro e 2 papéis). Segure a Carta e clique com o botão direito no lugar: no chão, na água (Tab) ou no céu (com um Núcleo Celeste).",
        "How about YOUR own city? Buy a Founding Charter from the Scribe in the starting city (or craft one: book and quill, 2 gold ingots and 2 paper). Hold it and right-click the spot: on land, on water (Tab) or in the sky (with a Sky Core)."),
    "item.isekai_mahou.founding_charter.desc": (
        "Funda a SUA cidade — no chão, sobre a água ou no céu (com um Núcleo Celeste) — no estilo e com o nome que quiser. Os construtores erguem tudo sozinhos. Fabricada barato ou comprada com o Escriba da cidade inicial.",
        "Found YOUR city — on land, on the water or in the sky (with a Sky Core) — in the style and with the name you choose. The builders raise everything on their own. Cheap to craft, or buy it from the Scribe in the starting city."),
    "item.isekai_mahou.sky_core.use": (
        "Com ele e a Carta de Fundação no inventário, sua cidade pode nascer no céu (Tab na prévia). É gasto na fundação. O Alquimista da cidade inicial vende.",
        "With it and the Founding Charter in your inventory, your city can be born in the sky (Tab in the preview). Spent on founding. The Alchemist in the starting city sells it."),
}

# guia do diário (arquivo src/main/lang/story: NÃO é ordenado — o valor é trocado no lugar, a ordem fica)
STORY = {
    "guide.isekai_mahou.city_found.where": (
        "Carta de Fundação: o Escriba da cidade inicial vende uma por dia; ou, na bancada, livro e pena no meio, um lingote de ouro de cada lado e um papel em cima e outro embaixo. Para a cidade no céu, o Núcleo Celeste: o Alquimista vende, ou 2 penas, 2 fragmentos de ametista e 1 Cristal de Mana.",
        "Founding Charter: the Scribe in the starting city sells one a day; or, at the crafting table, book and quill in the middle, a gold ingot on each side and a paper above and below. For a sky city, the Sky Core: the Alchemist sells it, or 2 feathers, 2 amethyst shards and 1 Mana Crystal."),
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
        print("%s: %d chaves do arquipélago (%d novas)" % (fname, len(T) + len(RAW), added))
        return
    raise SystemExit("%s mudou a cada tentativa: rode de novo" % fname)


def story(fname, idx):
    path = os.path.join(ROOT, "src/main/lang/story", fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in STORY.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d chaves do guia" % (fname, len(STORY)))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
    story("pt_br.json", 0)
    story("en_us.json", 1)
