"""Traduções do módulo "settlements" (pt_br principal + en_us). Chamado por tools/gen/settlements.py."""

NS = "isekai_mahou"
P = f"settlements.{NS}."
PT, EN = {}, {}


def tr(key, pt, en):
    PT[key] = pt
    EN[key] = en


def s(key, pt, en):
    tr(P + key, pt, en)


def lines(group, pairs):
    for i, (pt, en) in enumerate(pairs):
        s(f"line.{group}.{i}", pt, en)


# Plantas: (chave, minLevel, efeitos em texto) por estilo — nomes próprios por estilo
BLUEPRINTS = {
    "kingdom": {
        "plaza": ("Praça Real", "Royal Plaza"),
        "house_small": ("Casa de Pedra", "Stone House"),
        "builders_hall": ("Casa dos Construtores", "Builders' Hall"),
        "farm": ("Fazenda", "Farm"),
        "house_large": ("Sobrado", "Townhouse"),
        "market": ("Mercado", "Market"),
        "barracks": ("Quartel", "Barracks"),
        "wall": ("Muralha de Pedra", "Stone Wall"),
        "watchtower": ("Torre de Vigia", "Watchtower"),
        "tavern": ("Taverna", "Tavern"),
        "temple": ("Santuário da Luz", "Sanctuary of Light"),
        "town_hall": ("Prefeitura", "Town Hall"),
        "castle": ("Castelo", "Castle"),
    },
    "desert": {
        "plaza": ("Praça do Oásis", "Oasis Plaza"),
        "house_small": ("Casa de Adobe", "Adobe House"),
        "builders_hall": ("Oficina dos Mestres de Obras", "Master Builders' Workshop"),
        "farm": ("Horta do Oásis", "Oasis Garden"),
        "house_large": ("Casarão", "Manor House"),
        "market": ("Bazar", "Bazaar"),
        "barracks": ("Quartel dos Lanceiros", "Lancers' Barracks"),
        "wall": ("Muro de Arenito", "Sandstone Wall"),
        "temple": ("Templo do Sol", "Temple of the Sun"),
        "town_hall": ("Palácio do Vizir", "Vizier's Palace"),
        "obelisk": ("Obelisco do Sol", "Sun Obelisk"),
        "pyramid": ("Grande Pirâmide", "Great Pyramid"),
        "sphinx": ("Esfinge Guardiã", "Guardian Sphinx"),
    },
    "nordic": {
        "plaza": ("Praça do Fogo", "Fire Square"),
        "house_small": ("Cabana", "Cabin"),
        "builders_hall": ("Salão dos Construtores", "Builders' Lodge"),
        "farm": ("Fazenda", "Homestead"),
        "house_large": ("Casa Comprida", "Longhouse"),
        "market": ("Feira", "Fair"),
        "barracks": ("Salão dos Guerreiros", "Warriors' Hall"),
        "wall": ("Paliçada", "Palisade"),
        "watchtower": ("Torre de Vigia", "Watchtower"),
        "tavern": ("Salão do Hidromel", "Mead Hall"),
        "temple": ("Templo dos Ancestrais", "Temple of the Ancestors"),
        "town_hall": ("Salão do Jarl", "Jarl's Hall"),
    },
}
EFFECTS = {
    "plaza": ("O centro da cidade: Coração, Pedra de Teleporte e bancos. Os construtores erguem sozinhos ao fundar.",
              "The heart of the city: Heart, Teleport Stone and benches. The builders raise it on their own when founded."),
    "house_small": ("+2 vagas de morador. Até 8.", "+2 resident slots. Up to 8."),
    "builders_hall": ("+1 construtor e obras 25% mais rápidas.", "+1 builder and 25% faster construction."),
    "farm": ("+1 morador e +1 de afinidade do povo por dia. Até 4.", "+1 resident and +1 people's affinity per day. Up to 4."),
    "house_large": ("+4 vagas de morador. Até 6.", "+4 resident slots. Up to 6."),
    "market": ("+2 mercadores e +10% de impostos.", "+2 merchants and +10% taxes."),
    "barracks": ("+3 guardas e libera a MILÍCIA (guerreiros da cidade).", "+3 guards and unlocks the MILITIA (city warriors)."),
    "wall": ("Muralha com 4 torres e 4 portões (todos os construtores trabalham juntos). +1 guarda, +1 de afinidade por dia "
             "e libera o decreto dos portões.",
             "Wall with 4 towers and 4 gates (all builders work on it together). +1 guard, +1 affinity per day and unlocks "
             "the gates decree."),
    "watchtower": ("+1 guarda. Até 4.", "+1 guard. Up to 4."),
    "tavern": ("+1 morador, +1 mercador e +2 de afinidade por dia.", "+1 resident, +1 merchant and +2 affinity per day."),
    "temple": ("Altar de Ascensão PÚBLICO pronto para usar e +1 de afinidade por dia.",
               "A ready-to-use PUBLIC Ascension Altar and +1 affinity per day."),
    "town_hall": ("+1 guarda e +25% de impostos. Necessária para a cidade passar do nível 3.",
                  "+1 guard and +25% taxes. Required for the city to grow past level 3."),
    "castle": ("Guardas de elite (mais vida, dano e armadura), +3 guardas, +3 de milícia, +10% de impostos e +1 de afinidade.",
               "Elite guards (more health, damage and armor), +3 guards, +3 militia, +10% taxes and +1 affinity."),
    "obelisk": ("MARAVILHA: aura de regeneração para o seu grupo, os cidadãos e os NPCs da cidade. +1 de afinidade.",
                "WONDER: regeneration aura for your party, citizens and city NPCs. +1 affinity."),
    "pyramid": ("MARAVILHA: +50% de impostos e +3 de afinidade por dia.", "WONDER: +50% taxes and +3 affinity per day."),
    "sphinx": ("MARAVILHA: guardas de elite, +2 guardas, +2 de milícia e +1 de afinidade.",
               "WONDER: elite guards, +2 guards, +2 militia and +1 affinity."),
}
SIGNS = {
    "welcome": ("Bem-vindo à", "Welcome to"), "welcome2": ("nossa cidade!", "our city!"),
    "house": ("Lar, doce lar", "Home sweet home"), "builders_hall": ("Construtores", "Builders"),
    "market": ("Mercado", "Market"), "barracks": ("Quartel", "Barracks"), "tavern": ("Taverna", "Tavern"),
    "temple": ("Templo", "Temple"), "temple2": ("Altar público", "Public altar"), "town_hall": ("Prefeitura", "Town Hall"),
    "castle": ("Castelo", "Castle"), "obelisk": ("Obelisco", "Obelisk"), "obelisk2": ("do Sol", "of the Sun"),
}


def translations():
    PT.clear()
    EN.clear()
    # ---------------------------------------------------------------- registros
    tr(f"block.{NS}.settlement_heart", "Coração da Cidade Fundada", "Founded City Heart")
    tr(f"item.{NS}.founding_charter", "Carta de Fundação", "Founding Charter")
    tr(f"item.{NS}.founding_charter.desc", "Funda a SUA cidade: escolha o estilo (Reino, Desértico ou Nórdico) e o nome. "
       "Os construtores erguem tudo sozinhos, sem gastar recursos.",
       "Found YOUR city: choose the style (Kingdom, Desert or Nordic) and the name. The builders raise everything on their "
       "own, without spending resources.")
    tr(f"item.{NS}.founding_charter.use", "Botão direito no chão, na Superfície, longe de outras cidades e vilas.",
       "Right-click the ground in the Overworld, far from other cities and villages.")
    tr(f"item.{NS}.oath_seal", "Selo do Juramento", "Oath Seal")
    tr(f"item.{NS}.oath_seal.desc", "Use no coração da cidade jurada para desafiar o Campeão e se tornar o Senhor dela.",
       "Use it on the sworn city's heart to challenge its Champion and become its Lord.")
    for eid, pt, en in (("settlement_builder", "Construtor", "Builder"), ("settlement_resident", "Morador", "Resident"),
                        ("settlement_guard", "Guarda", "Guard"), ("settlement_merchant", "Mercador", "Merchant"),
                        ("settlement_militia", "Miliciano", "Militiaman"), ("city_champion", "Campeão da Cidade", "City Champion"),
                        ("settlement_site_sign", "Letreiro de Obra", "Construction Sign")):
        tr(f"entity.{NS}.{eid}", pt, en)
        if eid != "settlement_site_sign":
            tr(f"item.{NS}.{eid}_spawn_egg", f"Ovo de {pt}", f"{en} Spawn Egg")
    for k, (pt, en) in SIGNS.items():
        tr(f"sign.{NS}.settlement.{k}", pt, en)

    # ---------------------------------------------------------------- estilos e plantas
    s("style.kingdom", "Reino", "Kingdom")
    s("style.kingdom.desc", "Muralha de pedra alta, casas de enxaimel e um Castelo no nível máximo. O equilíbrio clássico.",
      "Tall stone wall, timber-framed houses and a Castle at max level. The classic balance.")
    s("style.kingdom.bonus", "Bônus: guardas com +4 de armadura e o Castelo (guardas de elite, +10% de impostos).",
      "Bonus: guards get +4 armor, plus the Castle (elite guards, +10% taxes).")
    s("style.desert", "Desértico", "Desert")
    s("style.desert.desc", "Muros simples de arenito, terraços e cúpulas — e as MARAVILHAS: Obelisco do Sol, Grande Pirâmide "
      "e Esfinge Guardiã.",
      "Simple sandstone walls, terraces and domes — and the WONDERS: Sun Obelisk, Great Pyramid and Guardian Sphinx.")
    s("style.desert.bonus", "Bônus: maravilhas (regeneração na cidade, +50% de impostos, guardas de elite). Obras 10% mais lentas.",
      "Bonus: wonders (regeneration in the city, +50% taxes, elite guards). Building is 10% slower.")
    s("style.nordic", "Nórdico", "Nordic")
    s("style.nordic.desc", "Paliçada de troncos, cabanas e casas compridas de telhado íngreme. Rústico, rápido de erguer.",
      "Log palisade, cabins and steep-roofed longhouses. Rustic and quick to raise.")
    s("style.nordic.bonus", "Bônus: construtores 40% mais rápidos e cada nível custa 35% menos prosperidade.",
      "Bonus: builders are 40% faster and every level costs 35% less prosperity.")
    for style, bps in BLUEPRINTS.items():
        for key, (pt, en) in bps.items():
            s(f"bp.{style}.{key}", pt, en)
            s(f"bp.{style}.{key}.desc", EFFECTS[key][0], EFFECTS[key][1])
    s("bp.auto", "Construída ao fundar", "Built on founding")
    s("bp.need_plaza", "Espere a praça ficar pronta", "Wait for the plaza")
    s("bp.need_level", "Nível %s da cidade", "City level %s")
    s("bp.maxed", "Limite atingido", "Limit reached")
    s("bp.queue_full", "Fila cheia (%s obras)", "Queue full (%s works)")
    s("bp.no_cell", "Sem terreno livre (suba o nível)", "No free plot (level up)")
    s("bp.queued", "Planta aprovada: %s. Os construtores vão começar!", "Blueprint approved: %s. The builders will get started!")
    s("bp.cant_cancel", "A obra já começou — não dá para cancelar.", "The work already started — it can't be cancelled.")

    # ---------------------------------------------------------------- fundação e coração
    s("found.title", "Carta de Fundação — escolha o estilo da sua cidade", "Founding Charter — choose your city's style")
    s("found.name", "Nome da cidade", "City name")
    s("found.default_name", "Vila de %s", "%s's Town")
    s("found.confirm", "Fundar a cidade", "Found the city")
    s("found.overworld", "Cidades só podem ser fundadas na Superfície.", "Cities can only be founded in the Overworld.")
    s("found.already", "Você já fundou uma cidade (abandone a antiga no menu para fundar outra).",
      "You already founded a city (abandon the old one in the menu to found another).")
    s("found.ground", "Precisa de chão firme e espaço livre aqui.", "This needs solid ground and free space.")
    s("found.too_close", "Perto demais de %s (%s blocos). Funde mais longe de outras cidades e vilas.",
      "Too close to %s (%s blocks). Found it farther from other cities and villages.")
    s("found.no_charter", "Você precisa de uma Carta de Fundação.", "You need a Founding Charter.")
    s("found.banner", "%s foi fundada!", "%s has been founded!")
    s("found.announce", "%s fundou a cidade %s (%s)!", "%s founded the city of %s (%s)!")
    s("heart.orphan", "Este coração não pertence mais a nenhuma cidade.", "This heart no longer belongs to any city.")
    s("abandoned", "Você abandonou %s. As construções ficam, mas o povo vai embora.",
      "You abandoned %s. The buildings remain, but the people leave.")
    s("done.title", "Obra concluída: %s", "Construction finished: %s")
    s("level.up", "%s chegou ao nível %s!", "%s reached level %s!")
    s("arrival", "%s chegou à cidade!", "%s arrived in the city!")
    s("npc.named", "%s, %s", "%s, %s")
    s("npc.died", "%s morreu em %s.", "%s died in %s.")
    s("sign.building", "Construindo: %s", "Building: %s")
    s("sign.waiting", "Esperando um construtor: %s", "Waiting for a builder: %s")
    s("coins", "%s moedas", "%s coins")
    s("daily", "[%s] Conta do dia: %s no cofre · afinidade %s", "[%s] Daily ledger: %s to the treasury · affinity %s")
    s("treasury.at_heart", "Vá até o coração da cidade para mexer no cofre.", "Go to the city heart to use the treasury.")
    s("treasury.poor", "O cofre não tem %s moedas.", "The treasury doesn't have %s coins.")
    s("treasury.took", "Você retirou %s do cofre.", "You took %s from the treasury.")
    s("festival.today", "Já houve festival hoje.", "There was already a festival today.")
    s("festival.banner", "Festival em %s! O povo comemora.", "Festival in %s! The people celebrate.")
    s("citizen.enemy", "Esta cidade está em guerra com você.", "This city is at war with you.")
    s("citizen.ruler", "Quem governa uma cidade é cidadão dela.", "A ruler is a citizen of their own city.")
    s("title.lord", "Senhor da Cidade", "City Lord")

    # ---------------------------------------------------------------- conquista
    s("oath.need_exalted", "Você precisa ser EXALTADO com %s para fazer o Juramento.",
      "You must be EXALTED with %s to swear the Oath.")
    s("oath.started", "Juramento feito!", "Oath sworn!")
    s("oath.no_coins", "Você não tem moedas para o tributo.", "You have no coins for the tribute.")
    s("oath.tribute", "Tributo: %s/%s moedas", "Tribute: %s/%s coins")
    s("oath.kill", "Defender a cidade: %s/%s", "Defend the city: %s/%s")
    s("oath.vigil", "Vigília: %s/%s", "Vigil: %s/%s")
    s("oath.done", "Juramento cumprido em %s! Use o Selo no coração da cidade.",
      "Oath fulfilled in %s! Use the Seal on the city heart.")
    s("seal.city", "Cidade jurada: %s", "Sworn city: %s")
    s("seal.use_heart", "Use o Selo no coração da cidade jurada.", "Use the Seal on the sworn city's heart.")
    s("seal.missing", "Você precisa do Selo do Juramento.", "You need the Oath Seal.")
    s("seal.wrong_city", "Este Selo não é desta cidade (ou o Juramento não terminou).",
      "This Seal isn't for this city (or the Oath isn't finished).")
    s("challenge.busy", "Já há um desafio acontecendo aqui.", "A challenge is already happening here.")
    s("challenge.wait", "O Campeão ainda se recupera: tente de novo em %s s.", "The Champion is still recovering: try again in %s s.")
    s("challenge.subtitle", "O guardião de %s aceita o duelo", "The guardian of %s accepts the duel")
    s("challenge.start", "%s desafiou o Campeão de %s!", "%s challenged the Champion of %s!")
    s("challenge.failed", "O desafio falhou. O Campeão volta a guardar a cidade.",
      "The challenge failed. The Champion returns to guard the city.")
    s("champion.roar", "Brado de Honra! O Campeão se fortalece!", "Cry of Honor! The Champion grows stronger!")
    s("lord.banner", "Você é o Senhor de %s!", "You are the Lord of %s!")
    s("lord.announce", "%s agora é o Senhor de %s!", "%s is now the Lord of %s!")
    s("lord.deposed", "Você perdeu o título de Senhor de %s para %s.", "You lost the title of Lord of %s to %s.")
    s("lord.abdicated", "Você abdicou do título de Senhor de %s.", "You abdicated as Lord of %s.")
    s("lord.lost", "O reino perdeu a confiança em você: não é mais o Senhor de %s.",
      "The kingdom lost its trust in you: you are no longer the Lord of %s.")

    # ---------------------------------------------------------------- guerra, milícia, toque de recolher
    s("war.need_affinity", "Só com a AFINIDADE do povo no máximo (100).", "Only with the people's AFFINITY at max (100).")
    s("war.peace_tribute", "Paz feita com %s em troca de %s.", "Peace made with %s in exchange for %s.")
    s("war.peace_sent", "Proposta de paz enviada a %s.", "Peace offer sent to %s.")
    s("war.peace_offer", "%s propõe paz. Aceite no menu da cidade (aba Guerra).", "%s offers peace. Accept it in the city menu (War tab).")
    s("war.refuse", "O povo de %s não negocia com inimigos!", "The people of %s won't deal with enemies!")
    s("war.territory", "Território inimigo: %s", "Enemy territory: %s")
    s("militia.need_barracks", "Construa um Quartel para treinar a milícia.", "Build Barracks to train the militia.")
    s("militia.full", "A milícia está completa.", "The militia is full.")
    s("militia.far", "O coração da cidade precisa estar carregado (vá até lá).", "The city heart must be loaded (go there).")
    s("militia.trained", "%s se juntou à milícia!", "%s joined the militia!")
    s("militia.order.0", "Milícia: SEGUIR o Senhor", "Militia: FOLLOW the Lord")
    s("militia.order.1", "Milícia: DEFENDER a cidade", "Militia: DEFEND the city")
    s("curfew.warn", "Toque de recolher em %s: saia em %s s ou os guardas virão.",
      "Curfew in %s: leave within %s s or the guards will come.")
    s("curfew.arrest", "Os guardas perderam a paciência com você!", "The guards have lost their patience with you!")

    # ---------------------------------------------------------------- rastreador
    s("track.oath", "Juramento: %s", "Oath: %s")
    s("track.oath.detail", "Defender %s/%s · Tributo %s/%s · Vigília %s/%s", "Defend %s/%s · Tribute %s/%s · Vigil %s/%s")
    s("track.challenge", "Desafiar o Campeão: %s", "Challenge the Champion: %s")
    s("track.challenge.detail", "Use o Selo do Juramento no coração da cidade.", "Use the Oath Seal on the city heart.")
    s("track.build", "Obra: %s", "Work: %s")
    s("track.build.detail", "%s · %s na fila", "%s · %s in queue")

    # ---------------------------------------------------------------- comandos
    s("cmd.finished", "%s obras terminadas.", "%s works finished.")
    s("cmd.none", "Você não está numa cidade governada.", "You're not in a ruled city.")
    s("cmd.ok", "%s: nível %s · afinidade %s · cofre %s", "%s: level %s · affinity %s · treasury %s")
    s("cmd.line", "%s (%s) — Senhor %s · nível %s · afinidade %s · cofre %s · obras %s · NPCs %s",
      "%s (%s) — Lord %s · level %s · affinity %s · treasury %s · works %s · NPCs %s")

    # ---------------------------------------------------------------- papéis e status
    for r, pt, en in (("builder", "Construtores", "Builders"), ("resident", "Moradores", "Residents"),
                      ("guard", "Guardas", "Guards"), ("merchant", "Mercadores", "Merchants"), ("militia", "Milícia", "Militia")):
        s(f"role.{r}", pt, en)
    s("status.builder.idle", "Nenhuma obra na fila. Peça uma planta no coração da cidade!",
      "No work in the queue. Ask for a blueprint at the city heart!")
    s("status.builder.busy", "Estou erguendo: %s (%s%%).", "I'm raising: %s (%s%%).")
    s("status.guard.war", "Estamos em guerra com %s. Olhos abertos!", "We're at war with %s. Eyes open!")
    s("status.guard.gates.0", "Portões abertos dia e noite, por decreto.", "Gates open day and night, by decree.")
    s("status.guard.gates.1", "À noite os portões fecham e forasteiros têm de sair.",
      "At night the gates close and outsiders must leave.")
    s("status.guard.gates.2", "Portões fechados por decreto. Ninguém de fora entra.", "Gates shut by decree. No outsiders get in.")
    s("status.merchant.tax.0", "Impostos baixos! Dá para vender barato.", "Low taxes! I can sell cheap.")
    s("status.merchant.tax.1", "Os impostos estão justos. O comércio vai bem.", "Taxes are fair. Business is good.")
    s("status.merchant.tax.2", "Com esses impostos altos, tenho de cobrar mais caro...", "With taxes this high, I have to charge more...")
    s("status.militia.order.0", "Ordens: seguir o Senhor aonde ele for.", "Orders: follow the Lord wherever they go.")
    s("status.militia.order.1", "Ordens: defender a cidade.", "Orders: defend the city.")
    s("status.resident.happy", "Que cidade boa de morar! O Senhor cuida da gente.", "What a great city to live in! The Lord looks after us.")
    s("status.resident.ok", "A vida vai indo. Um festival não faria mal...", "Life goes on. A festival wouldn't hurt...")
    s("status.resident.sad", "Impostos, guerra... o povo anda descontente.", "Taxes, war... the people are unhappy.")

    # ---------------------------------------------------------------- tela
    s("ui.title", "Cidade", "City")
    s("ui.tab", "Cidade", "City")
    s("ui.none.title", "Nenhuma cidade", "No city")
    s("ui.none.text", "Você ainda não tem cidade. Funde a sua com uma Carta de Fundação (papel, blocos de esmeralda, blocos "
      "de ouro e um livro e pena) ou conquiste uma cidade de reino: chegue a EXALTADO com o reino e agache + clique no coração da "
      "cidade para fazer o Juramento.",
      "You don't have a city yet. Found yours with a Founding Charter (paper, emerald blocks, gold blocks and a book and quill) or "
      "conquer a kingdom city: reach EXALTED with the kingdom and sneak + click the city heart to swear the Oath.")
    for k, pt, en in (("own", "Cidade fundada", "Founded city"), ("conquered", "Cidade conquistada", "Conquered city"),
                      ("city", "Cidade de reino", "Kingdom city")):
        s(f"ui.kind.{k}", pt, en)
    for k, pt, en in (("overview", "Visão geral", "Overview"), ("blueprints", "Plantas", "Blueprints"),
                      ("works", "Obras", "Works"), ("decrees", "Decretos", "Decrees"), ("war", "Guerra", "War")):
        s(f"ui.page.{k}", pt, en)
    s("ui.lord", "Senhor: %s", "Lord: %s")
    s("ui.no_lord", "Sem Senhor — o reino governa.", "No Lord — the kingdom rules.")
    s("ui.level", "Nível %s/%s", "Level %s/%s")
    s("ui.you_citizen", "✦ Você é cidadão", "✦ You're a citizen")
    s("ui.you_enemy", "⚔ Inimigo", "⚔ Enemy")
    s("ui.prosperity", "Prosperidade", "Prosperity")
    s("ui.points", "%s / %s para o próximo nível", "%s / %s to the next level")
    s("ui.max_level", "Nível máximo!", "Max level!")
    s("ui.need_hall", "Construa a Prefeitura para passar do nível 3.", "Build the Town Hall to grow past level 3.")
    s("ui.affinity", "Afinidade do povo: %s/100", "People's affinity: %s/100")
    s("ui.aff_delta", "Ontem: %s", "Yesterday: %s")
    s("ui.aff_max", "Afinidade máxima! Você pode declarar guerra e treinar a milícia.",
      "Max affinity! You can declare war and train the militia.")
    s("ui.treasury", "Cofre: %s moedas", "Treasury: %s coins")
    s("ui.income", "Último dia: +%s · trocas hoje: %s", "Last day: +%s · trades today: %s")
    s("ui.withdraw", "Retirar %s", "Withdraw %s")
    s("ui.withdraw_all", "Retirar tudo", "Withdraw all")
    s("ui.festival", "Festival (%s moedas)", "Festival (%s coins)")
    s("ui.festival.tip", "Uma vez por dia: +15 de afinidade, fogos e música na praça.",
      "Once a day: +15 affinity, fireworks and music in the plaza.")
    s("ui.population", "População", "Population")
    s("ui.citizens", "Cidadãos (jogadores): %s", "Citizens (players): %s")
    s("ui.abandon", "Abandonar a cidade", "Abandon the city")
    s("ui.abandon.confirm", "Clique de novo para ABANDONAR", "Click again to ABANDON")
    s("ui.abandon.tip", "O povo vai embora, as guerras acabam e o lugar some do mapa. Os prédios ficam.",
      "The people leave, wars end and the place disappears from the map. The buildings remain.")
    s("ui.abdicate", "Abdicar", "Abdicate")
    s("ui.abdicate.confirm", "Clique de novo para ABDICAR", "Click again to ABDICATE")
    s("ui.abdicate.tip", "Você deixa de ser o Senhor desta cidade (impostos e decretos voltam ao reino).",
      "You stop being this city's Lord (taxes and decrees return to the kingdom).")
    s("ui.bp.info", "Nível %s · +%s de prosperidade · %s/%s", "Level %s · +%s prosperity · %s/%s")
    s("ui.bp.build", "Construir", "Build")
    s("ui.bp.none", "Nenhuma planta disponível.", "No blueprints available.")
    s("ui.works.none", "Nenhuma obra. Escolha uma planta na aba Plantas.", "No works. Pick a blueprint in the Blueprints tab.")
    s("ui.works.active", "em obra", "in progress")
    s("ui.works.queued", "na fila", "queued")
    s("ui.works.hint", "Cada construtor toca uma obra; os blocos só sobem com o construtor no canteiro.",
      "Each builder handles one work; blocks only rise with the builder on site.")
    s("ui.cancel", "Cancelar", "Cancel")
    s("ui.decree.tax", "Impostos", "Taxes")
    s("ui.decree.tax.0", "Baixos", "Low")
    s("ui.decree.tax.1", "Normais", "Normal")
    s("ui.decree.tax.2", "Altos", "High")
    s("ui.decree.tax.0.desc", "×0,75 de renda, 1 moeda por troca, mercadores 15% mais baratos e +4 de afinidade por dia.",
      "×0.75 income, 1 coin per trade, merchants 15% cheaper and +4 affinity per day.")
    s("ui.decree.tax.1.desc", "Renda normal, 2 moedas por troca.", "Normal income, 2 coins per trade.")
    s("ui.decree.tax.2.desc", "×1,4 de renda, 3 moedas por troca, mercadores 25% mais caros e −5 de afinidade por dia.",
      "×1.4 income, 3 coins per trade, merchants 25% pricier and −5 affinity per day.")
    s("ui.decree.gates", "Portões", "Gates")
    s("ui.decree.gates.0", "Abertos", "Open")
    s("ui.decree.gates.1", "Fechar à noite", "Close at night")
    s("ui.decree.gates.2", "Fechados", "Closed")
    s("ui.decree.gates.0.desc", "Os portões ficam abertos. Sem toque de recolher.", "The gates stay open. No curfew.")
    s("ui.decree.gates.1.desc", "À noite a grade desce e forasteiros têm 60 s para sair (os guardas vão atrás de quem ficar).",
      "At night the portcullis drops and outsiders get 60 s to leave (the guards go after anyone who stays).")
    s("ui.decree.gates.2.desc", "Grade sempre fechada e toque de recolher o dia todo para forasteiros.",
      "Portcullis always shut and an all-day curfew for outsiders.")
    s("ui.decree.order", "Ordem da milícia", "Militia orders")
    s("ui.decree.order.0", "Seguir o Senhor", "Follow the Lord")
    s("ui.decree.order.1", "Defender a cidade", "Defend the city")
    s("ui.decree.order.0.desc", "Os milicianos acompanham você (e são trazidos se ficarem para trás).",
      "The militia follows you (and catches up if left behind).")
    s("ui.decree.order.1.desc", "Os milicianos patrulham o coração da cidade.", "The militia patrols the city heart.")
    s("ui.decree.no_wall", "Os portões só funcionam com a muralha pronta.", "The gates only work once the wall is built.")
    s("ui.war.ready", "Afinidade %s/100: o povo te apoia. Você pode declarar guerra.",
      "Affinity %s/100: the people back you. You may declare war.")
    s("ui.war.need", "Afinidade %s/100: declarar guerra e treinar milícia exigem afinidade MÁXIMA.",
      "Affinity %s/100: declaring war and training militia need MAX affinity.")
    s("ui.peace", "✦ Em paz", "✦ At peace")
    s("ui.at_war", "⚔ Em guerra com:", "⚔ At war with:")
    s("ui.peace.offer", "Propor paz", "Offer peace")
    s("ui.peace.sent", "Proposta enviada", "Offer sent")
    s("ui.peace.accept", "Aceitar paz", "Accept peace")
    s("ui.peace.tip_lord", "O Senhor da outra cidade precisa aceitar (só cidades de reino sem Senhor cobram %s moedas).",
      "The other city's Lord must accept (only Lord-less kingdom cities charge %s coins).")
    s("ui.peace.tip_tribute", "Uma cidade de reino sem Senhor aceita a paz por %s moedas do cofre.",
      "A kingdom city without a Lord accepts peace for %s coins from the treasury.")
    s("ui.militia", "Milícia: %s / %s", "Militia: %s / %s")
    s("ui.train", "Treinar miliciano (%s moedas)", "Train militiaman (%s coins)")
    s("ui.train.tip", "Precisa de afinidade máxima e do Quartel (cidade fundada).", "Needs max affinity and Barracks (founded city).")
    s("ui.militia.hint", "Clique num miliciano para trocar a ordem. Eles lutam nas guerras da cidade.",
      "Click a militiaman to switch orders. They fight in the city's wars.")
    s("ui.targets", "Cidades próximas", "Nearby cities")
    s("ui.declare", "Guerra", "War")
    s("ui.declare.tip", "Declarar guerra: os jogadores da guilda/grupo do Senhor inimigo viram inimigos, os NPCs de lá lutam "
      "ou fogem e não negociam com vocês.",
      "Declare war: players in the enemy Lord's guild/party become enemies; their NPCs fight or flee and won't trade with you.")
    s("ui.join", "Tornar-se cidadão", "Become a citizen")
    s("ui.join.tip", "Você passa a pertencer a esta cidade (os guardas te protegem; as guerras dela são suas).",
      "You will belong to this city (the guards protect you; its wars are yours).")
    s("ui.leave", "Deixar a cidadania", "Renounce citizenship")
    s("ui.leave.tip", "Você deixa de pertencer a esta cidade.", "You will no longer belong to this city.")
    s("ui.faction", "Reino: %s · %s", "Kingdom: %s · %s")
    s("ui.conquest", "Tomar o controle da cidade", "Take control of the city")
    s("ui.step.exalted", "Chegar a EXALTADO com o reino", "Reach EXALTED with the kingdom")
    s("ui.step.oath", "Cumprir o Juramento", "Fulfill the Oath")
    s("ui.step.challenge", "Vencer o Campeão no coração da cidade", "Defeat the Champion at the city heart")
    s("ui.oath.start", "Fazer o Juramento", "Swear the Oath")
    s("ui.oath.tip", "Um Juramento por vez. Prove o seu valor à cidade.", "One Oath at a time. Prove your worth to the city.")
    s("ui.oath.other", "Seu Juramento atual é com %s (fazer este troca de cidade).",
      "Your current Oath is with %s (swearing this one switches cities).")
    s("ui.oath.kills", "Defender: %s/%s monstros abatidos na cidade", "Defend: %s/%s monsters slain in the city")
    s("ui.oath.tribute", "Tributo: %s/%s moedas ao cofre", "Tribute: %s/%s coins to the treasury")
    s("ui.oath.vigil", "Vigília: %s/%s s agachado junto ao coração, à noite", "Vigil: %s/%s s crouched by the heart, at night")
    s("ui.oath.pay", "Pagar tributo", "Pay tribute")
    s("ui.oath.pay.tip", "Usa Moedas da Guilda ou esmeraldas do inventário.", "Uses Guild Coins or emeralds from your inventory.")
    s("ui.oath.vigil_hint", "A vigília conta 1 por segundo agachado a até 6 blocos do coração, de noite.",
      "The vigil counts 1 per second crouching within 6 blocks of the heart, at night.")
    s("ui.challenge", "Desafiar o Campeão", "Challenge the Champion")
    s("ui.challenge.retry", "Campeão se recuperando (%s s)", "Champion recovering (%s s)")
    s("ui.challenge.running", "Desafio em andamento!", "Challenge in progress!")
    s("ui.challenge.tip", "Precisa do Selo do Juramento. Fique a até 48 blocos do coração: fugir, morrer ou passar de 6 minutos "
      "encerra o desafio.",
      "Needs the Oath Seal. Stay within 48 blocks of the heart: fleeing, dying or going past 6 minutes ends the challenge.")

    # ---------------------------------------------------------------- falas dos NPCs
    lines("builder.ambient", [
        ("Mais um tijolo, mais um sonho.", "One more brick, one more dream."),
        ("Ouviu esse martelo? É música!", "Hear that hammer? That's music!"),
        ("Planta boa é planta aprovada.", "A good blueprint is an approved blueprint."),
        ("Se cair, a gente levanta de novo. Mais bonito.", "If it falls, we raise it again. Prettier."),
    ])
    lines("builder.talk", [
        ("Eu ergo o que você planejar. Sem pedir uma pedra sequer!", "I'll raise whatever you plan. Without asking for a single stone!"),
        ("Cada planta nova faz a cidade crescer. E eu adoro um canteiro.", "Every new blueprint grows the city. And I love a building site."),
        ("Mais construtores, mais obras ao mesmo tempo. A Casa dos Construtores ajuda.",
         "More builders, more works at once. The Builders' Hall helps."),
        ("Já construí muralha em tempestade. Isso aqui é moleza.", "I've built walls in a storm. This is a breeze."),
    ])
    lines("resident.ambient", [
        ("Bom dia, vizinho!", "Morning, neighbor!"),
        ("O pão hoje saiu quentinho.", "The bread came out warm today."),
        ("Dizem que o Senhor da cidade é poderoso...", "They say the city's Lord is powerful..."),
        ("Que vista bonita daqui da praça.", "Such a nice view from the plaza."),
    ])
    lines("resident.talk", [
        ("Chegamos com uma trouxa nas costas e achamos um lar.", "We came with a bundle on our backs and found a home."),
        ("Se o povo estiver feliz, a cidade prospera. Festivais ajudam!", "If the people are happy, the city prospers. Festivals help!"),
        ("Impostos baixos deixam todo mundo de bom humor.", "Low taxes put everyone in a good mood."),
        ("Ouvi dizer que outras cidades estão se armando. Tomara que não seja com a gente.",
         "I heard other cities are arming up. I hope it's not against us."),
    ])
    lines("resident.desert", [
        ("A sombra das palmeiras vale ouro por aqui.", "The shade of the palms is worth gold around here."),
        ("O Sol abençoa quem constrói maravilhas.", "The Sun blesses those who build wonders."),
        ("Água fresca e um muro firme: é tudo de que precisamos.", "Fresh water and a firm wall: that's all we need."),
    ])
    lines("resident.nordic", [
        ("O frio afia o espírito!", "The cold sharpens the spirit!"),
        ("Hoje à noite tem hidromel no salão.", "There's mead in the hall tonight."),
        ("Nossos ancestrais nos olham das estrelas.", "Our ancestors watch us from the stars."),
    ])
    lines("guard.ambient", [
        ("Tudo calmo no meu turno.", "All quiet on my watch."),
        ("Mantenha a ordem, cidadão.", "Keep the peace, citizen."),
        ("Nenhum monstro passa por mim.", "No monster gets past me."),
        ("Os portões estão sob guarda.", "The gates are under guard."),
    ])
    lines("guard.talk", [
        ("Protegemos o Senhor, o grupo dele e os cidadãos. Quem atacar vocês aqui, vai se ver comigo.",
         "We protect the Lord, their party and the citizens. Whoever attacks you here deals with me."),
        ("Com a muralha pronta, posso fechar os portões à noite por decreto.", "With the wall done, I can close the gates at night by decree."),
        ("Um Castelo ou uma Esfinge nos deixaria bem mais fortes.", "A Castle or a Sphinx would make us much stronger."),
        ("Em guerra, ninguém da cidade inimiga negocia com a gente. E eu também não deixo.",
         "At war, nobody from the enemy city trades with us. And I don't allow it either."),
    ])
    lines("guard.curfew", [
        ("Toque de recolher! Forasteiros, para fora!", "Curfew! Outsiders, out!"),
        ("Portões fechados. Quem não é daqui, que vá embora.", "Gates are shut. If you're not from here, move along."),
    ])
    lines("merchant.ambient", [
        ("Mercadoria fresca! Preço justo!", "Fresh goods! Fair prices!"),
        ("Moedas tilintando, cidade crescendo.", "Coins jingling, city growing."),
        ("Cada troca enche o cofre do Senhor.", "Every trade fills the Lord's treasury."),
        ("Hoje tem promoção... talvez.", "There's a sale today... maybe."),
    ])
    lines("merchant.talk", [
        ("Compro o que a terra dá e vendo o que a cidade precisa.", "I buy what the land gives and sell what the city needs."),
        ("Os impostos mudam meus preços. Fale com o Senhor!", "Taxes change my prices. Talk to the Lord!"),
        ("Um Mercado bem cheio é sinal de cidade rica.", "A busy Market is the sign of a rich city."),
        ("Pode olhar à vontade, cliente!", "Feel free to browse, customer!"),
    ])
    lines("merchant.busy", [
        ("Um momento, estou atendendo outro cliente!", "One moment, I'm with another customer!"),
        ("Calma, um de cada vez!", "Easy, one at a time!"),
    ])
    lines("merchant.hello", [
        ("Bem-vindo! Dê uma olhada.", "Welcome! Take a look."),
        ("O que vai ser hoje?", "What'll it be today?"),
        ("Tenho exatamente o que você precisa.", "I've got just what you need."),
    ])
    lines("merchant.thanks", [
        ("Negócio fechado!", "It's a deal!"),
        ("Volte sempre!", "Come again!"),
        ("Prazer fazer negócio.", "A pleasure doing business."),
    ])
    lines("militia.ambient", [
        ("Pronto para o que der e vier!", "Ready for anything!"),
        ("Treinei a semana toda pra isso.", "I trained all week for this."),
        ("Pela cidade!", "For the city!"),
        ("Aponte o inimigo, Senhor.", "Point out the enemy, my Lord."),
    ])
    lines("militia.talk", [
        ("Somos os guerreiros da cidade. Seguimos o Senhor ou defendemos o coração.",
         "We're the city's warriors. We follow the Lord or defend the heart."),
        ("Clique em mim se quiser trocar a ordem.", "Click me if you want to switch orders."),
        ("Contra uma cidade inimiga, lutamos até o fim.", "Against an enemy city, we fight to the end."),
        ("Se ficarmos para trás, damos um jeito de alcançar.", "If we fall behind, we'll find a way to catch up."),
    ])
    lines("war", [
        ("Inimigo à vista!", "Enemy sighted!"),
        ("Você não é bem-vindo aqui!", "You're not welcome here!"),
        ("Guardas! Um invasor!", "Guards! An intruder!"),
        ("Saia da nossa cidade!", "Get out of our city!"),
    ])
    lines("refuse", [
        ("Não falo com inimigos.", "I don't talk to enemies."),
        ("Nem pensar. Estamos em guerra.", "No way. We're at war."),
        ("Vá embora antes que eu chame os guardas!", "Leave before I call the guards!"),
    ])
    lines("flee", [
        ("Socorro! Inimigos!", "Help! Enemies!"),
        ("Corre, corre!", "Run, run!"),
        ("Alguém chame os guardas!", "Someone call the guards!"),
    ])
    lines("generic", [
        ("Que dia, hein?", "What a day, huh?"),
        ("Estou só de passagem.", "I'm just passing through."),
        ("Esta cidade já viu dias melhores.", "This city has seen better days."),
    ])
    return dict(PT), dict(EN)
