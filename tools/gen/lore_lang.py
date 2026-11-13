"""
Textos do módulo lore (Enciclopédia, fragmentos, telas) -> src/main/lang/lore/{pt_br,en_us}.json

Fonte da história: docs/LORE.md. Cada item é (pt, en). O script confere os ids contra o Java
(LoreBook.java e LoreFragments.java): entrada/fragmento sem texto, ou texto sem entrada, falha.

Uso:  py -I tools/gen/lore_lang.py
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
JAVA = os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/lore")
OUT = os.path.join(ROOT, "src/main/lang/lore")

# ------------------------------------------------------------------------------------------------ telas e mensagens
UI = {
    "item.isekai_mahou.lore_page": ("Página Perdida", "Lost Page"),
    "item.isekai_mahou.lore_page.desc": ("Use para ler. Pode revelar um lugar no mapa.", "Use to read. It may reveal a place on the map."),
    "block.isekai_mahou.lore_stele": ("Estela Antiga", "Ancient Stele"),
    "hint.isekai_mahou.lore_page": ("Página Perdida: segure e use para ler. Ela pode revelar um lugar.",
                                    "Lost Page: hold it and use to read. It may reveal a place."),
    "hint.isekai_mahou.lore_encyclopedia": ("Fragmento guardado: menu B → Guia de Viagem → Enciclopédia.",
                                            "Fragment saved: B menu → Travel Guide → Encyclopedia."),
    "screen.isekai_mahou.lore.tab": ("Enciclopédia", "Encyclopedia"),
    "screen.isekai_mahou.menu.section.lore": ("Enciclopédia", "Encyclopedia"),
    "screen.isekai_mahou.lore.fragments": ("Fragmentos  %s/%s", "Fragments  %s/%s"),
    "screen.isekai_mahou.lore.page_of": ("%s — página %s de %s", "%s — page %s of %s"),
    "screen.isekai_mahou.lore.open_book": ("Enciclopédia", "Encyclopedia"),
    "screen.isekai_mahou.lore.track": ("Rastrear", "Track"),
    "screen.isekai_mahou.lore.untrack": ("Parar de rastrear", "Stop tracking"),
    "screen.isekai_mahou.lore.search_again": ("Procurar de novo", "Search again"),
    "screen.isekai_mahou.lore.no_place": ("O rastro deste lugar esfriou. Tente procurar de novo em outra região.",
                                          "The trail to this place went cold. Try searching again from another region."),
    "screen.isekai_mahou.lore.place_revealed": ("Lugar revelado: %s", "Place revealed: %s"),
    "screen.isekai_mahou.lore.place_visited": ("✔ Lugar visitado: %s", "✔ Place visited: %s"),
    "screen.isekai_mahou.lore.coords": ("X %s, Z %s", "X %s, Z %s"),
    "screen.isekai_mahou.lore.new_fragment": ("Novo fragmento  ·  %s/%s", "New fragment  ·  %s/%s"),
    "screen.isekai_mahou.lore.set_done": ("Coleção completa: %s", "Collection complete: %s"),
    "screen.isekai_mahou.lore.truth_unlocked": ("Verdade Oculta revelada: %s", "Hidden Truth revealed: %s"),
    "screen.isekai_mahou.lore.locked": ("Esta verdade ainda está escondida.", "This truth is still hidden."),
    "screen.isekai_mahou.lore.locked_set": (
        "Esta verdade ainda está escondida.\n\nJunte todas as páginas da coleção “%s” para revelá-la (%s/%s).\n\nPáginas Perdidas aparecem em baús pelo mundo, em tesouros e nas Estelas Antigas das Ruínas de Velmora.",
        "This truth is still hidden.\n\nGather every page of the “%s” collection to reveal it (%s/%s).\n\nLost Pages turn up in chests around the world, in treasures and on the Ancient Steles of the Ruins of Velmora."),
    "screen.isekai_mahou.lore.not_found": (
        "Página ainda não encontrada.\n\nPáginas Perdidas aparecem em baús pelo mundo (masmorras, torres, templos, criptas), em tesouros enterrados e nas Estelas Antigas das Ruínas de Velmora. Cada página lida revela um lugar no mapa.",
        "Page not found yet.\n\nLost Pages turn up in chests around the world (dungeons, towers, temples, crypts), in buried treasures and on the Ancient Steles of the Ruins of Velmora. Every page you read reveals a place on the map."),
    "lore.isekai_mahou.distance": ("%s blocos a %s", "%s blocks %s"),
    "lore.isekai_mahou.dir.n": ("norte", "north"), "lore.isekai_mahou.dir.ne": ("nordeste", "northeast"),
    "lore.isekai_mahou.dir.e": ("leste", "east"), "lore.isekai_mahou.dir.se": ("sudeste", "southeast"),
    "lore.isekai_mahou.dir.s": ("sul", "south"), "lore.isekai_mahou.dir.sw": ("sudoeste", "southwest"),
    "lore.isekai_mahou.dir.w": ("oeste", "west"), "lore.isekai_mahou.dir.nw": ("noroeste", "northwest"),
    "lore.isekai_mahou.dim.overworld": ("Mundo Normal", "Overworld"),
    "lore.isekai_mahou.dim.the_nether": ("Nether", "Nether"),
    "lore.isekai_mahou.dim.the_end": ("End", "End"),
    "message.isekai_mahou.lore.found": ("✦ Fragmento: %s. O rastro do lugar esfriou — procure de novo pela Enciclopédia.",
                                        "✦ Fragment: %s. The trail went cold — search again from the Encyclopedia."),
    "message.isekai_mahou.lore.found_place": ("✦ Fragmento: %s. Lugar revelado: %s em X/Z %s.",
                                              "✦ Fragment: %s. Place revealed: %s at X/Z %s."),
    "message.isekai_mahou.lore.found_place_dim": ("✦ Fragmento: %s. Lugar revelado: %s em X/Z %s (%s).",
                                                  "✦ Fragment: %s. Place revealed: %s at X/Z %s (%s)."),
    "message.isekai_mahou.lore.set_complete": ("Coleção completa: %s.", "Collection complete: %s."),
    "message.isekai_mahou.lore.set_complete_truth": ("Coleção completa: %s. Verdade Oculta revelada na Enciclopédia: %s.",
                                                     "Collection complete: %s. Hidden Truth revealed in the Encyclopedia: %s."),
    "message.isekai_mahou.lore.page_known": ("Você já conhece este fragmento. Que tal dar a página a um amigo?",
                                             "You already know this fragment. Why not give the page to a friend?"),
    "message.isekai_mahou.lore.all_known": ("Você já conhece todos os fragmentos. Dê esta página a outro aventureiro.",
                                            "You already know every fragment. Give this page to another adventurer."),
    "message.isekai_mahou.lore.stele_blank": ("Pedra lisa. Só as estelas das ruínas antigas têm algo gravado.",
                                              "Smooth stone. Only the steles of the old ruins have anything carved."),
    "message.isekai_mahou.lore.stele_read": ("Você já leu esta estela.", "You have already read this stele."),
    "message.isekai_mahou.lore.stele_nothing_new": ("A estela repete o que você já sabe.", "The stele repeats what you already know."),
    "message.isekai_mahou.lore.arrived": ("Você chegou: %s", "You have arrived: %s"),
    "message.isekai_mahou.lore.relocated": ("Novo rastro: %s em X %s, Z %s", "New trail: %s at X %s, Z %s"),
    "message.isekai_mahou.lore.relocate_none": ("Nenhum rastro de %s por perto.", "No trail of %s nearby."),
    "message.isekai_mahou.lore.treasure_moved": ("Nada aqui... o mapa aponta um pouco mais adiante.",
                                                 "Nothing here... the map points a little farther on."),
    "message.isekai_mahou.lore.treasure_here": ("Algo está enterrado sob os seus pés! Cave em %s.",
                                                "Something is buried under your feet! Dig at %s."),
    "command.isekai_mahou.lore.status": ("Fragmentos: %s/%s · Estelas lidas: %s", "Fragments: %s/%s · Steles read: %s"),
    "command.isekai_mahou.lore.status_set": ("  %s: %s/%s", "  %s: %s/%s"),
    "command.isekai_mahou.lore.reset": ("Dados de lore zerados.", "Lore data reset."),
    "command.isekai_mahou.lore.unknown": ("Fragmento desconhecido: %s", "Unknown fragment: %s"),
    "command.isekai_mahou.lore.page_given": ("Página Perdida entregue.", "Lost Page given."),
    "command.isekai_mahou.lore.unlocked": ("%s fragmento(s) novo(s).", "%s new fragment(s)."),
    "command.isekai_mahou.lore.ruin_none": ("Nenhuma Ruína de Velmora por perto (só chunks novos têm).",
                                            "No Ruin of Velmora nearby (only new chunks have them)."),
    "command.isekai_mahou.lore.teleported": ("Teleportado para X %s, Z %s.", "Teleported to X %s, Z %s."),
    "command.isekai_mahou.lore.no_place": ("O fragmento %s não tem lugar revelado.", "Fragment %s has no revealed place."),
}

CATEGORIES = {
    "world": ("O Mundo", "The World"),
    "history": ("História", "History"),
    "realms": ("Reinos e Facções", "Realms and Factions"),
    "dimensions": ("Dimensões", "Dimensions"),
    "races": ("Raças", "Races"),
    "classes": ("Classes", "Classes"),
    "bosses": ("Chefes e Lendas", "Bosses and Legends"),
    "truths": ("Verdades Ocultas", "Hidden Truths"),
}

SETS = {
    "velmora": ("Crônicas de Velmora", "Chronicles of Velmora"),
    "sages": ("Diários dos Seis Sábios", "Diaries of the Six Sages"),
    "demon": ("Confissões do Rei Demônio", "Confessions of the Demon King"),
    "herald": ("Cartas do Primeiro Invocado", "Letters of the First Summoned"),
    "hunter": ("Caderno do Caçador", "The Hunter's Notebook"),
    "smuggler": ("Mapas do Contrabandista", "The Smuggler's Maps"),
    "deep": ("Runas das Profundezas", "Runes of the Deep"),
    "void": ("Cantos do Vazio", "Songs of the Void"),
}

PLACES = {
    "tower": ("Torre de Desafio", "Challenge Tower"),
    "sanctum": ("Santuário de Ascensão", "Ascension Sanctum"),
    "colossus": ("Ruínas do Colosso", "Colossus Ruins"),
    "arcane_ruins": ("Ruínas Arcanas", "Arcane Ruins"),
    "labyrinth": ("Labirinto do Minotauro", "Minotaur's Labyrinth"),
    "rupture": ("Ruptura do Crepúsculo", "Twilight Rupture"),
    "shrine_fire": ("Santuário do Fogo", "Shrine of Fire"),
    "shrine_water": ("Santuário da Água", "Shrine of Water"),
    "shrine_earth": ("Santuário da Terra", "Shrine of Earth"),
    "shrine_wind": ("Santuário do Vento", "Shrine of Wind"),
    "shrine_light": ("Santuário da Luz", "Shrine of Light"),
    "shrine_shadow": ("Santuário da Sombra", "Shrine of Shadow"),
    "profane_altar": ("Altar Profano", "Profane Altar"),
    "abyssal_chasm": ("Fenda Abissal", "Abyssal Chasm"),
    "nether_citadel": ("Cidadela Demoníaca", "Demon Citadel"),
    "treasure": ("Tesouro enterrado", "Buried treasure"),
    "elemental_gate": ("Portão Elemental", "Elemental Gate"),
    "celestial_stairway": ("Escadaria Celeste", "Celestial Stairway"),
    "crypt": ("Cripta Esquecida", "Forgotten Crypt"),
    "fey_ring": ("Círculo das Fadas", "Fairy Ring"),
    "hydra_lair": ("Covil da Hidra", "Hydra's Lair"),
    "wyvern_peak": ("Ninho do Rei Wyvern", "Wyvern King's Nest"),
    "basilisk_tomb": ("Tumba do Basilisco", "Basilisk's Tomb"),
    "manticore_den": ("Toca da Manticora", "Manticore's Den"),
    "false_hydra_lair": ("Covil da Falsa Hidra", "False Hydra's Lair"),
    "mimic_vault": ("Cofre do Mímico", "Mimic's Vault"),
    "dwarf_hold": ("Cidade Anã Perdida", "Lost Dwarven City"),
    "depths": ("Masmorra em Camadas", "Layered Dungeon"),
    "kobold_den": ("Toca dos Kobolds", "Kobold Den"),
    "garganta": ("Fenda da Garganta", "Garganta Rift"),
    "end_library": ("Biblioteca do Vazio", "Library of the Void"),
    "mage_tower": ("Torre de Mago", "Mage Tower"),
}

# ------------------------------------------------------------------------------------------------ Enciclopédia
# id: (título pt, texto pt, título en, texto en)
ENTRIES = {
    # ---- O Mundo
    "world_asteria": (
        "Astéria, o mundo das duas luas",
        "O mundo onde você acordou se chama Astéria. Três reinos dividem as terras — Aldória, Sahra e Nordheim — e por toda parte há ruínas de um império mais antigo, Velmora.\n\nO ar daqui tem algo que o seu mundo nunca teve: mana. E no céu, duas luas pálidas se apagam no amanhecer.",
        "Asteria, the world of two moons",
        "The world you woke up in is called Asteria. Three realms share its lands — Aldoria, Sahra and Nordheim — and everywhere lie the ruins of an older empire, Velmora.\n\nThe air here holds something your world never had: mana. And in the sky, two pale moons fade at dawn."),
    "world_mana": (
        "Mana",
        "Mana é o sangue do mundo. Dizem que tudo em Astéria — gente, monstros, pedra e vento — é tecido com ela, e que ela vem toda de uma mesma origem.\n\nMagos gastam Mana; guerreiros chamam a mesma força de Vigor; atiradores, de Foco. São três jeitos do mesmo sangue correr.",
        "Mana",
        "Mana is the blood of the world. They say everything in Asteria — people, monsters, stone and wind — is woven from it, and that all of it flows from a single origin.\n\nMages spend Mana; warriors call the same force Vigor; marksmen call it Focus. Three ways for the same blood to run."),
    "world_glyphs": (
        "Glifos e o Grimório",
        "Aqui ninguém nasce sabendo feitiço pronto: magia se ESCREVE. Glifos são a gramática da mana — uma Forma diz como a magia sai, os Efeitos dizem o que ela faz, os Aumentos mudam o jeito, e as Leis valem dentro de um Domínio.\n\nO Grimório é o caderno. O resto é criatividade.",
        "Glyphs and the Grimoire",
        "Nobody here is born knowing ready-made spells: magic is WRITTEN. Glyphs are the grammar of mana — a Form says how the spell comes out, Effects say what it does, Augments change the manner, and Laws rule inside a Domain.\n\nThe Grimoire is the notebook. The rest is creativity."),
    "world_elements": (
        "Os Seis Elementos",
        "Fogo, Água, Terra, Vento, Luz e Sombra. Seis santuários guardam a essência de cada um, e só os dignos atravessam suas provas. O Arcano é a mana sem cor.\n\nElementos podem se fundir — Magma, Gelo, Crepúsculo... Os magos antigos chamavam isso de heresia. Os invocados chamam de terça-feira.",
        "The Six Elements",
        "Fire, Water, Earth, Wind, Light and Shadow. Six shrines keep the essence of each, and only the worthy pass their trials. Arcane is mana without color.\n\nElements can fuse — Magma, Ice, Twilight... The old mages called that heresy. The summoned call it Tuesday."),
    "world_circles": (
        "Os Círculos",
        "A cada dez níveis a alma bate num teto invisível. Só o Ritual de Ascensão o rompe, e ele cobra um preço: encarar a própria Sombra.\n\nUm nativo que chega ao Terceiro Círculo já é um mago de respeito; ao Quinto, uma lenda. Ninguém entende por que os invocados não param de subir.",
        "The Circles",
        "Every ten levels the soul hits an invisible ceiling. Only the Ascension Ritual breaks it, and it has a price: facing your own Shadow.\n\nA native who reaches the Third Circle is a respected mage; the Fifth, a legend. Nobody understands why the summoned never stop climbing."),
    "world_moons": (
        "As Duas Luas",
        "A lua velha, prateada, sempre esteve lá; os elfos dizem que ela guia as flechas. A segunda, pálida e menor, apareceu numa única noite, há pouco mais de mil anos.\n\nA Igreja da Aurora diz que é um presente. Os skalds de Nordheim dizem que é um olho. Ninguém sabe ao certo.",
        "The Two Moons",
        "The old silver moon was always there; the elves say it guides their arrows. The second, pale and smaller, appeared in a single night a little over a thousand years ago.\n\nThe Church of the Dawn calls it a gift. The skalds of Nordheim call it an eye. Nobody knows for sure."),
    "world_summoned": (
        "Os Invocados",
        "De tempos em tempos alguém acorda neste mundo vindo de outro — sem mana, sem história, e crescendo rápido demais. Os nativos os chamam de invocados: para uns, lendas ambulantes; para outros, encrencas ambulantes.\n\nTodos contam a mesma coisa: uma voz suave dizendo que foram trazidos por um motivo. O corpo é novo, feito da mana daqui — por isso cada invocado escolhe a própria raça.",
        "The Summoned",
        "From time to time someone wakes in this world having come from another — without mana, without a past, and growing far too fast. The natives call them the summoned: walking legends to some, walking trouble to others.\n\nThey all tell the same story: a gentle voice saying they were brought here for a reason. The body is new, made of this world's mana — which is why each summoned chooses their own race."),
    "world_souls": (
        "O Ciclo das Almas",
        "Quando um nativo morre, a alma segue o ciclo: as leves sobem ao Santuário Celestial, as pesadas afundam no Abismo, e as que perdem o coração viram Vazios no deserto branco do Mundo Oco.\n\nShinigami conduzem almas; Quincy destroem Vazios; Necromantes mexem no ciclo por conta própria. Os invocados estão FORA do ciclo — quase mortos, viram alma e podem voltar.",
        "The Cycle of Souls",
        "When a native dies, the soul follows the cycle: light ones rise to the Celestial Sanctuary, heavy ones sink into the Abyss, and those that lose their heart become Hollows in the white desert of the Hollow World.\n\nShinigami guide souls; Quincy destroy Hollows; Necromancers meddle with the cycle on their own. The summoned stand OUTSIDE the cycle — near death, they become a soul and may return."),
    # ---- História
    "history_dragon": (
        "A Era do Dragão",
        "Antes dos santuários, antes dos Guardiões e antes até das duas luas, existia um dragão que era todos os elementos ao mesmo tempo. Ele se apresenta como “a origem de toda magia”.\n\nOs primeiros povos aprenderam a escrever a mana dele em glifos. Ele dormia. Enquanto dormia, estava tudo bem.",
        "The Age of the Dragon",
        "Before the shrines, before the Guardians and even before the two moons, there was a dragon that was every element at once. It introduces itself as “the origin of all magic”.\n\nThe first peoples learned to write its mana in glyphs. It slept. While it slept, all was well."),
    "history_velmora": (
        "O Império de Velmora",
        "Há mais de mil anos um só império cobria o continente. Velmora ergueu torres, câmaras de pedra e escolas de glifos; as ruínas que você encontra pelo mundo são dele.\n\nSeis grandes magos serviam na corte. O povo se lembra deles como os Seis Sábios. Do império, só se lembra que caiu em uma noite.",
        "The Empire of Velmora",
        "More than a thousand years ago a single empire covered the continent. Velmora raised towers, stone chambers and schools of glyphs; the ruins you find around the world are its remains.\n\nSix great mages served at court. People remember them as the Six Sages. Of the empire, they only remember that it fell in one night."),
    "history_sages": (
        "Os Seis Sábios",
        "Pyra do Fogo, ferreira. Nerida da Água, curandeira. Brom da Terra, mestre de obras. Aelis do Vento, andarilha. Solen da Luz, sacerdote. Nyra da Sombra, espiã.\n\nDeles vieram a forja rúnica dos anões, as receitas das bruxas, as torres, a Guilda, a Igreja da Aurora e — dizem as más línguas — o Submundo. Os discípulos dos seis fundaram a Academia Arcana.",
        "The Six Sages",
        "Pyra of Fire, a smith. Nerida of Water, a healer. Brom of Earth, a master builder. Aelis of Wind, a wanderer. Solen of Light, a priest. Nyra of Shadow, a spy.\n\nFrom them came the dwarves' rune forge, the witches' recipes, the towers, the Guild, the Church of the Dawn and — so gossip says — the Underworld. The disciples of the six founded the Arcane Academy."),
    "history_sealing": (
        "O Selamento",
        "Quando o Dragão começou a acordar, os Seis Sábios não conseguiram matá-lo: apenas selá-lo além do Crepúsculo. A essência dele foi repartida em seis santuários, um por elemento, e um carcereiro ficou de guarda.\n\nNa mesma noite, a segunda lua apareceu no céu.",
        "The Sealing",
        "When the Dragon began to wake, the Six Sages could not kill it: only seal it beyond the Twilight. Its essence was split among six shrines, one per element, and a jailer was left on watch.\n\nThat same night, the second moon appeared in the sky."),
    "history_rupture": (
        "A Grande Ruptura",
        "Mil anos atrás o mundo rachou. O Abismo afundou, o Santuário Celestial subiu, uma ferida de Luz e Sombra misturadas se abriu no meio do oceano — a Fenda do Crepúsculo — e Velmora caiu em uma noite.\n\nA culpa é do Arauto do Crepúsculo, que invocou o primeiro herói de outro mundo... e o devorou. Os reinos contam os anos a partir daquela noite.",
        "The Great Rupture",
        "A thousand years ago the world cracked. The Abyss sank, the Celestial Sanctuary rose, a wound of mingled Light and Shadow opened in the middle of the ocean — the Twilight Rift — and Velmora fell in one night.\n\nThe blame lies with the Twilight Herald, who summoned the first hero from another world... and devoured him. The realms count their years from that night."),
    "history_ashen": (
        "Os Anos de Cinza",
        "Depois da Ruptura vieram duzentos anos de fome e monstros. Goblins, orcs e kobolds se juntaram na Horda. Os anões desceram e fecharam a porta.\n\nDos sobreviventes nasceram três reinos: Aldória nos campos do sul, Sahra sobre as dunas que cobriram o leste, Nordheim com os soldados presos na neve. A Guilda, a Igreja e a Academia são dessa época.",
        "The Ashen Years",
        "After the Rupture came two hundred years of hunger and monsters. Goblins, orcs and kobolds banded into the Horde. The dwarves went down and shut the door.\n\nFrom the survivors three realms were born: Aldoria in the southern fields, Sahra over the dunes that buried the east, Nordheim with the soldiers stranded in the snow. The Guild, the Church and the Academy date from then."),
    "history_demon_king": (
        "O Rei Demônio",
        "No Nether há uma cidadela de pedra negra, e nela um senhor de demônios que luta como um espadachim de corte — até cansar de fingir. O povo o chama de Rei Demônio; os demônios, de Lorde.\n\nEle não ataca o mundo. Fica lá, repetindo que aquilo “ainda é dele”. Aldória é o reino que ele mais odeia, e ninguém sabe dizer por quê.",
        "The Demon King",
        "In the Nether stands a citadel of black stone, and in it a lord of demons who fights like a court swordsman — until he tires of pretending. The people call him the Demon King; the demons call him Lord.\n\nHe does not attack the world. He stays there, repeating that it is “still his”. Aldoria is the realm he hates most, and nobody can say why."),
    "history_now": (
        "O Ano Mil",
        "Hoje é o ano mil depois da Ruptura. Feras antigas acordaram, a segunda lua parece mais pálida, e a Fenda do Crepúsculo pulsa.\n\nE de repente, em vez de um invocado por geração, apareceram vários de uma vez. Você é um deles.",
        "The Year One Thousand",
        "Today is the year one thousand after the Rupture. Ancient beasts have woken, the second moon looks paler, and the Twilight Rift pulses.\n\nAnd suddenly, instead of one summoned per generation, several appeared at once. You are one of them."),
    # ---- Reinos e facções
    "realm_aldoria": (
        "Aldória, o Reino",
        "O coração humano do continente: campos dourados, cidades muradas, aldeias e o Lorde na capital. Dizem que o rei já foi aventureiro.\n\nAldória fica fora da briga entre Sahra e Nordheim — por enquanto. Aqui se valoriza trabalho, lei e herói que paga as próprias bebidas. Demônios são vistos com desconfiança.",
        "Aldoria, the Kingdom",
        "The human heart of the continent: golden fields, walled cities, villages and the Lord in the capital. They say the king was once an adventurer.\n\nAldoria stays out of the quarrel between Sahra and Nordheim — for now. Work, law and heroes who pay for their own drinks are valued here. Demons are looked at with suspicion."),
    "realm_sahra": (
        "Sahra, o Império do Deserto",
        "Cidades de arenito, caravanas, oásis e o Sultão. Sahra mede o tempo pelas dunas, não pelos reis — e debaixo das dunas dormem províncias inteiras de Velmora.\n\nVende ouro e especiarias, quer o ferro do norte barato e respeita quem tem sangue de dragão.",
        "Sahra, the Desert Empire",
        "Sandstone cities, caravans, oases and the Sultan. Sahra measures time by the dunes, not by kings — and beneath the dunes whole provinces of Velmora lie sleeping.\n\nIt sells gold and spices, wants northern iron cheap, and respects those with dragon blood."),
    "realm_nordheim": (
        "Nordheim, o Reino das Neves",
        "Fortalezas de pedra e madeira, salões de hidromel, lobos e o Jarl. Nordheim não se curva ao frio nem aos monstros.\n\nSeu povo descende dos legionários que ficaram presos no norte quando o império caiu. Vendem ferro, e os skalds cantam os heróis por mil invernos.",
        "Nordheim, the Realm of Snows",
        "Fortresses of stone and timber, mead halls, wolves and the Jarl. Nordheim bows neither to cold nor to monsters.\n\nIts people descend from the legionaries stranded in the north when the empire fell. They sell iron, and the skalds sing of heroes for a thousand winters."),
    "faction_guild": (
        "Guilda dos Aventureiros",
        "A Guilda não liga se você é lenda ou encrenca: quer o seu nome no registro e trabalho bem feito. Todo invocado que se preze começa com um cartão de rank F e uma recepcionista sorridente.\n\nDizem que ela começou com os andarilhos que seguiam Aelis, a Sábia do Vento, que nunca teve casa.",
        "Adventurers' Guild",
        "The Guild does not care whether you are a legend or trouble: it wants your name on the register and work well done. Every summoned worth the name starts with an F-rank card and a smiling receptionist.\n\nThey say it began with the wanderers who followed Aelis, the Sage of Wind, who never had a home."),
    "faction_academy": (
        "Academia Arcana",
        "Os estudiosos da magia: teses, Provações e a gramática dos glifos. Foi fundada pelos discípulos dos Seis Sábios para que nada do que eles sabiam se perdesse.\n\nPerdeu-se bastante mesmo assim. E às vezes a Academia expulsa alguém por “pesquisas proibidas” — o Arquimago Renegado tomou uma torre inteira.",
        "Arcane Academy",
        "The scholars of magic: theses, Trials and the grammar of glyphs. It was founded by the disciples of the Six Sages so that nothing they knew would be lost.\n\nA good deal was lost anyway. And now and then the Academy expels someone for “forbidden research” — the Renegade Archmage took an entire tower."),
    "faction_church": (
        "Igreja da Aurora",
        "“A Aurora sempre volta.” Os sacerdotes curam, purificam e caçam mortos-vivos, seguindo os ensinamentos de Solen, o Sábio da Luz.\n\nA Igreja não perdoa quem mexe no ciclo das almas, e é rival declarada do Submundo.",
        "Church of the Dawn",
        "“The Dawn always returns.” The priests heal, purify and hunt the undead, following the teachings of Solen, the Sage of Light.\n\nThe Church does not forgive those who meddle with the cycle of souls, and is the declared rival of the Underworld."),
    "faction_underworld": (
        "O Submundo",
        "Ladrões, receptadores e contratos noturnos. Quem ajuda o Submundo perde a confiança da Igreja e do Reino.\n\nOs mais velhos juram que a organização nasceu de algo mais nobre: os olhos e ouvidos de Nyra, a Sábia da Sombra, que vigiavam alguma coisa. O que vigiavam, ninguém lembra.",
        "The Underworld",
        "Thieves, fences and night contracts. Help the Underworld and you lose the trust of Church and Kingdom.\n\nThe old hands swear the organization was born from something nobler: the eyes and ears of Nyra, the Sage of Shadow, who kept watch over something. What they watched, nobody remembers."),
    "faction_wildfolk": (
        "O Povo Selvagem",
        "Fadas e espíritos da natureza, mais velhos que Velmora. Proteja a floresta e eles deixam você em paz.\n\nA casa deles é o Reino das Fadas: colinas num crepúsculo que nunca acaba, cogumelos que brilham e risadas que vêm de lugar nenhum.",
        "The Wildfolk",
        "Fairies and nature spirits, older than Velmora. Protect the forest and they leave you alone.\n\nTheir home is the Fey Realm: hills under a twilight that never ends, glowing mushrooms and laughter that comes from nowhere."),
    "faction_horde": (
        "A Horda",
        "Clãs de goblins, orcs e kobolds, inimigos naturais dos reinos. Juntaram-se nos Anos de Cinza e nunca mais se separaram de verdade.\n\nQuando um chefe cai — o Rei Goblin, o Lorde Orc, o Lorde Kobold — os outros sentem o cheiro do medo e resolvem mostrar quem manda.",
        "The Horde",
        "Clans of goblins, orcs and kobolds, natural enemies of the realms. They banded together in the Ashen Years and never truly parted.\n\nWhen a chief falls — the Goblin King, the Orc Lord, the Kobold Lord — the others smell fear and decide to show who is in charge."),
    "faction_dwarves": (
        "Os Anões",
        "Dizem que os anões nunca foram embora: só desceram mais fundo do que alguém teve coragem de cavar. Bem abaixo do deepslate há salões de pedra que nenhum mapa mostra.\n\nLá ainda se forja o Mithril e ainda se gravam runas — uma arte que aprenderam com Pyra. Anões não esquecem um bom freguês. Nem um mau.",
        "The Dwarves",
        "They say the dwarves never left: they only went deeper than anyone dared to dig. Far below the deepslate there are stone halls no map shows.\n\nMithril is still forged there and runes are still carved — an art they learned from Pyra. Dwarves never forget a good customer. Or a bad one."),
    # ---- Dimensões
    "dim_nether": (
        "O Nether",
        "O subsolo de fogo. Para os reinos é só o lugar de onde vêm as fortalezas e o quartzo; para os demônios, é casa.\n\nÉ aqui que fica a Cidadela Demoníaca, com a Forja Infernal e o Altar do Pacto.",
        "The Nether",
        "The underground of fire. To the realms it is merely where fortresses and quartz come from; to demons, it is home.\n\nThis is where the Demon Citadel stands, with the Infernal Forge and the Pact Altar."),
    "dim_end": (
        "O End",
        "O que sobrou do lado de fora quando o mundo rachou: ilhas flutuando sobre o nada. Além do Dragão do Fim, nas ilhas externas, há uma torre de estantes — a Biblioteca do Vazio.\n\nEscribas encapuzados ainda copiam livros que ninguém lê, e os tomos guardam a gramática do próprio espaço.",
        "The End",
        "What was left outside when the world cracked: islands floating over nothing. Beyond the Ender Dragon, on the outer islands, stands a tower of shelves — the Library of the Void.\n\nHooded scribes still copy books nobody reads, and the tomes keep the grammar of space itself."),
    "dim_elemental_plane": (
        "O Plano Elemental",
        "Além do céu existe um arquipélago impossível: as Ilhas de Brasa, o Mar Suspenso, os Picos Terrosos e os Céus de Tempestade. Ali reinam os quatro Guardiões Elementais.\n\nEntra-se pelo Portão Elemental, um círculo de pedra com quatro obeliscos — depois do Terceiro Círculo.",
        "The Elemental Plane",
        "Beyond the sky lies an impossible archipelago: the Ember Isles, the Suspended Sea, the Earthen Peaks and the Storm Skies. The four Elemental Guardians reign there.\n\nYou enter through the Elemental Gate, a stone circle with four obelisks — after the Third Circle."),
    "dim_abyss": (
        "O Abismo",
        "Abaixo do mundo, o Abismo pulsa como um coração negro. A escuridão tem peso, e os olhos que observam do Vazio nunca piscam.\n\nÉ para onde afundam as almas pesadas, e é de onde saem os pactos. O Soberano governa do alto de um trono feito de estrelas mortas.",
        "The Abyss",
        "Below the world, the Abyss beats like a black heart. Darkness has weight there, and the eyes watching from the Void never blink.\n\nIt is where heavy souls sink, and where pacts come from. The Sovereign rules from a throne made of dead stars."),
    "dim_celestial": (
        "O Santuário Celestial",
        "Acima das nuvens, cidades de cristal sobre jardins de nuvens, guardadas por anjos que não toleram intrusos.\n\nÉ para onde sobem as almas leves. O Serafim de seis asas vigia tudo, com a convicção absoluta de que a ordem vale mais que a liberdade.",
        "The Celestial Sanctuary",
        "Above the clouds, crystal cities over gardens of cloud, guarded by angels who tolerate no intruders.\n\nIt is where light souls rise. The six-winged Seraph watches over it all, absolutely convinced that order is worth more than freedom."),
    "dim_fey": (
        "O Reino das Fadas",
        "Colinas num crepúsculo que nunca acaba. Entra-se por um Círculo das Fadas — um anel de cogumelos e pedras na floresta.\n\nCuidado com as risadinhas. E com a Rainha dos Espinhos.",
        "The Fey Realm",
        "Hills under a twilight that never ends. You enter through a Fairy Ring — a circle of mushrooms and stones in the forest.\n\nBeware the giggling. And the Thorn Queen."),
    "dim_hollow": (
        "O Mundo Oco",
        "Há um rasgo no céu que só abre à noite. Do outro lado, um deserto de areia branca sob uma lua que nunca se põe, árvores de quartzo e ossadas do tamanho de torres.\n\nAli vivem os Vazios — almas que perderam o coração e usam máscaras de osso — e, em Las Noches, os que governam a fome deles.",
        "The Hollow World",
        "There is a tear in the sky that only opens at night. On the other side, a desert of white sand under a moon that never sets, quartz trees and bones the size of towers.\n\nThe Hollows live there — souls that lost their hearts and wear masks of bone — and, in Las Noches, those who rule their hunger."),
    "dim_twilight": (
        "A Fenda do Crepúsculo",
        "Uma ferida no tecido do mundo, onde Luz e Sombra se misturam em algo que não deveria existir. Do outro lado o tempo não passa: só existe o crepúsculo eterno e uma arena à sua espera.\n\nA Fenda só deixa passar quem venceu o Soberano do Abismo e o Serafim.",
        "The Twilight Rift",
        "A wound in the fabric of the world, where Light and Shadow mingle into something that should not exist. On the other side time does not pass: there is only eternal twilight and an arena waiting for you.\n\nThe Rift only lets through those who defeated the Abyss Sovereign and the Seraph."),
    "dim_trials": (
        "O Reino das Provações",
        "As Câmaras de Provação ficam fora do mundo: uma recepção silenciosa, um altar, e do outro lado uma arena onde o seu elemento testa se você é digno dele.\n\nNinguém sabe quem as construiu. Todo mago que quer crescer num elemento um dia atravessa essas portas.",
        "The Trial Realm",
        "The Trial Chambers lie outside the world: a silent hall, an altar, and beyond it an arena where your element tests whether you are worthy of it.\n\nNobody knows who built them. Every mage who wants to grow in an element walks through those doors one day."),
    "dim_inner": (
        "O Mundo Interior",
        "Silêncio, respiração lenta e a espada no colo: do outro lado dos olhos fechados há um mundo inteiro. É a alma do Shinigami, e é lá que mora o espírito da Zanpakutō.\n\nÀs vezes ele quer conversar. Às vezes quer lutar.",
        "The Inner World",
        "Silence, slow breathing and the sword on your lap: behind closed eyes lies a whole world. It is the Shinigami's soul, and the spirit of the Zanpakutō lives there.\n\nSometimes it wants to talk. Sometimes it wants to fight."),
    "dim_endless_abyss": (
        "O Abismo Infinito",
        "O fundo do fundo. Tudo o que já foi derrotado deixa um eco, e os ecos escorrem para lá: andares sem fim, ondas de criaturas e as Sombras dos chefes que você já venceu — mais fortes e mais escuras.\n\nSó quem passou do nível 100 aguenta a descida.",
        "The Endless Abyss",
        "The bottom of the bottom. Everything ever defeated leaves an echo, and the echoes drain down there: endless floors, waves of creatures and the Shadows of bosses you have already beaten — stronger and darker.\n\nOnly those past level 100 can endure the descent."),
    "dim_home": (
        "A Dimensão Casa",
        "Um bolso de espaço só seu, com uma casinha pronta e lugar para construir. Não há monstros nem brigas lá dentro.\n\nA magia que dobra o espaço assim é a mesma dos tomos da Biblioteca do Vazio. A Chave da Casa custa caro — pergunte numa estalagem.",
        "The Home Dimension",
        "A pocket of space all your own, with a small house ready and room to build. There are no monsters or fights inside.\n\nThe magic that folds space this way is the same as in the tomes of the Library of the Void. The Home Key is expensive — ask at an inn."),
    # ---- Raças
    "race_human": (
        "Humanos",
        "Versáteis e teimosos, os humanos se adaptam a qualquer caminho. Foram o povo de Velmora e são o dos três reinos.\n\nToda lenda deste mundo começou com um humano que se recusou a desistir.",
        "Humans",
        "Versatile and stubborn, humans adapt to any path. They were the people of Velmora and are the people of the three realms.\n\nEvery legend of this world began with a human who refused to give up."),
    "race_elf": (
        "Elfos",
        "Filhos das florestas antigas — já estavam lá antes do império. Sentidos aguçados, mira impecável, olhos que enxergam no escuro.\n\nDizem que a lua velha guia as flechas deles.",
        "Elves",
        "Children of the ancient forests — they were there before the empire. Keen senses, flawless aim, eyes that see in the dark.\n\nThey say the old moon guides their arrows."),
    "race_dwarf": (
        "Anões",
        "Baixos, robustos e teimosos como rocha. Mineradores natos e mestres da forja, enxergam nas profundezas e riem de venenos.\n\nA maioria desceu na Ruptura; os que você vê na superfície são os curiosos.",
        "Dwarves",
        "Short, sturdy and stubborn as rock. Born miners and masters of the forge, they see in the depths and laugh at poisons.\n\nMost went below at the Rupture; the ones you see on the surface are the curious ones."),
    "race_half_orc": (
        "Meio-Orcs",
        "Sangue da Horda e dos homens, filhos dos Anos de Cinza. Fortes, temidos e difíceis de derrubar.\n\nQuando a fúria acende, nada os para.",
        "Half-Orcs",
        "Blood of the Horde and of men, children of the Ashen Years. Strong, feared and hard to bring down.\n\nWhen the fury lights, nothing stops them."),
    "race_beastfolk": (
        "Bestiais",
        "Meio gente, meio fera: orelhas atentas, cauda inquieta, o faro de um caçador e o salto de um predador.\n\nSão os melhores batedores de Nordheim e das savanas de Sahra.",
        "Beastfolk",
        "Half person, half beast: alert ears, restless tail, a hunter's nose and a predator's leap.\n\nThey are the finest scouts of Nordheim and of the savannas of Sahra."),
    "race_dragonborn": (
        "Dracônicos",
        "Descendentes do sangue dracônico, com chifres, cauda, escamas na cor do elemento ancestral e um sopro devastador.\n\nEm Sahra são tratados com respeito — o deserto se lembra de dragões.",
        "Dragonborn",
        "Descendants of dragon blood, with horns, tail, scales in the color of their ancestral element and a devastating breath.\n\nIn Sahra they are treated with respect — the desert remembers dragons."),
    "race_demon": (
        "Demônios",
        "Marcados por um pacto antigo, carregam chifres, cauda, asas de sombra e o fogo do Abismo nas veias. Encantadores... e perigosos.\n\nNenhum deles assinou nada: a marca é herdada. Aldória desconfia mesmo assim.",
        "Demons",
        "Marked by an ancient pact, they bear horns, a tail, wings of shadow and the fire of the Abyss in their veins. Charming... and dangerous.\n\nNone of them signed anything: the mark is inherited. Aldoria is wary all the same."),
    "race_angel": (
        "Anjos",
        "Filhos do céu com auréola dourada e pequenas asas de penas. Descendem dos que desceram do Santuário Celestial.\n\nA luz sagrada deles cura aliados e queima mortos-vivos.",
        "Angels",
        "Children of the sky with a golden halo and small feathered wings. They descend from those who came down from the Celestial Sanctuary.\n\nTheir holy light heals allies and burns the undead."),
    "race_phantom": (
        "Fantasmas",
        "Uma alma que voltou do ciclo com corpo de névoa: meio translúcida, leve como o vento, enxerga no escuro e deixa golpes a atravessarem.\n\nA Igreja da Aurora não sabe bem o que fazer com eles.",
        "Phantoms",
        "A soul that came back from the cycle with a body of mist: half translucent, light as wind, seeing in the dark and letting blows pass through.\n\nThe Church of the Dawn is not quite sure what to do with them."),
    "race_arachnid": (
        "Aracnídeos",
        "Caçadores das cavernas com olhos a mais e patas finas nas costas. Escalam paredes, tecem teias e mordem com veneno.\n\nVizinhos — e rivais — dos anões lá no fundo.",
        "Arachnids",
        "Cave hunters with extra eyes and thin legs on their backs. They climb walls, weave webs and bite with venom.\n\nNeighbors — and rivals — of the dwarves down below."),
    "race_slime": (
        "Slimes",
        "Mana coagulada que um dia acordou. Um corpo de geleia translúcida que regenera, quica e engole quase tudo.\n\nSubestimado... até virar rei.",
        "Slimes",
        "Clotted mana that one day woke up. A body of translucent jelly that regenerates, bounces and swallows almost anything.\n\nUnderestimated... until it becomes king."),
    # ---- Classes
    "class_mage": (
        "Mago",
        "O Mago não reza nem pactua: estuda. Na Mesa de Pesquisa cada tese é um problema de mana, e cada tese defendida vira um glifo novo.\n\nÉ o caminho da Academia Arcana. Um mago se mede pelo tamanho do vocabulário.",
        "Mage",
        "The Mage neither prays nor bargains: he studies. At the Research Table every thesis is a problem of mana, and every thesis defended becomes a new glyph.\n\nIt is the way of the Arcane Academy. A mage is measured by the size of his vocabulary."),
    "class_witch": (
        "Bruxa",
        "A Bruxa não pede poder: cozinha. Ervas, essências e olhos de coisas que você não quer saber viram receita no caldeirão.\n\nAs receitas mais antigas vêm dos cadernos de Nerida, a Sábia da Água — a que cura e a que amaldiçoa.",
        "Witch",
        "The Witch does not ask for power: she cooks. Herbs, essences and the eyes of things you would rather not know become recipes in the cauldron.\n\nThe oldest recipes come from the notebooks of Nerida, the Sage of Water — the one that heals and the one that curses."),
    "class_warlock": (
        "Bruxo",
        "O Bruxo desenha o círculo com giz, acende as velas e chama alguém do outro lado. Patronos cobram caro, mas pagam melhor.\n\nOs demônios reconhecem um Bruxo de longe: ele lê a mesma letra miúda que eles.",
        "Warlock",
        "The Warlock draws the circle in chalk, lights the candles and calls someone from the other side. Patrons charge dearly, but pay better.\n\nDemons recognize a Warlock from afar: he reads the same fine print they do."),
    "class_druid": (
        "Druida",
        "O Druida planta a própria magia. A Muda Ancestral cresce com oferendas e cuidado, e cada estágio da árvore é um espírito animal e um pedaço do coração do mundo.\n\nÉ o caminho do Povo Selvagem. Quem planta uma floresta nunca caminha sozinho.",
        "Druid",
        "The Druid plants his own magic. The Ancestral Sapling grows with offerings and care, and each stage of the tree is an animal spirit and a piece of the world's heart.\n\nIt is the way of the Wildfolk. Whoever plants a forest never walks alone."),
    "class_necromancer": (
        "Necromante",
        "O Necromante colhe o que os outros deixam para trás: almas na foice, ossos no altar e, à meia-noite, o Rito Ossuário.\n\nEle mexe no ciclo das almas por conta própria. O Lich da Cripta Esquecida é o exemplo de quem não soube parar.",
        "Necromancer",
        "The Necromancer reaps what others leave behind: souls on the scythe, bones on the altar and, at midnight, the Ossuary Rite.\n\nHe meddles with the cycle of souls on his own. The Lich of the Forgotten Crypt is the example of one who did not know when to stop."),
    "class_smith": (
        "Ferreiro",
        "Entre o martelo e a bigorna, o metal escuta. O Ferreiro não compra armas: conversa com o aço até ele nascer.\n\nCada golpe na bigorna é um glifo — a arte das runas, que Pyra ensinou aos anões.",
        "Smith",
        "Between hammer and anvil, the metal listens. The Smith does not buy weapons: he talks to the steel until it is born.\n\nEvery blow on the anvil is a glyph — the art of runes, which Pyra taught the dwarves."),
    "class_rogue": (
        "Ladino",
        "Tudo neste mundo tem dono — até as magias. O Ladino discorda. Bolsos, mana, bênçãos e o feitiço que acabaram de lançar contra você: tudo cabe no Livro-Razão.\n\nO Submundo o recebe de braços abertos (e de olho nos próprios bolsos).",
        "Rogue",
        "Everything in this world has an owner — even spells. The Rogue disagrees. Pockets, mana, blessings and the spell just cast at you: it all fits in the Ledger.\n\nThe Underworld welcomes him with open arms (and an eye on its own pockets)."),
    "class_knight": (
        "Cavaleiro Rúnico",
        "A lâmina rúnica guarda magias como uma bainha guarda o aço. Cinco Juramentos no Altar transformam o escudeiro em Rei Cavaleiro.\n\nAs espadas lendárias — da pedra de Caliburn ao lago onde dorme a verdadeira — são relíquias das legiões de Velmora.",
        "Rune Knight",
        "The rune blade keeps spells the way a scabbard keeps steel. Five Oaths at the Altar turn the squire into a Knight King.\n\nThe legendary swords — from Caliburn's stone to the lake where the true one sleeps — are relics of the legions of Velmora."),
    "class_enchanter": (
        "Encantador",
        "Força, celeridade, égide, vitalidade: o Encantador escreve poder em si e nos aliados como quem escreve num livro.\n\nGuarda duas páginas proibidas — a Anti-Magia e a Restrição Celestial. A arte da Inscrição vem dos escribas do velho império.",
        "Enchanter",
        "Strength, haste, aegis, vitality: the Enchanter writes power onto himself and his allies as one writes in a book.\n\nHe keeps two forbidden pages — Anti-Magic and Heavenly Restriction. The art of Inscription comes from the scribes of the old empire."),
    "class_shinigami": (
        "Shinigami",
        "A sua espada tem nome — você só ainda não o ouviu. Meditando, o Shinigami desce ao Mundo Interior, onde o espírito da lâmina espera.\n\nSelada, Shikai, Bankai: cada liberação é uma promessa entre a alma e o aço. O ofício deles é conduzir almas e purificar Vazios.",
        "Shinigami",
        "Your sword has a name — you just have not heard it yet. In meditation the Shinigami descends to the Inner World, where the spirit of the blade waits.\n\nSealed, Shikai, Bankai: each release is a promise between soul and steel. Their trade is guiding souls and purifying Hollows."),
    "class_quincy": (
        "Quincy",
        "Um Quincy não cria energia: ele a reúne — do ar, dos projéteis, das magias que voam na direção dele — e devolve em flechas.\n\nCaçam Vazios há gerações, e não purificam: destroem. É por isso que não se dão com os Shinigami.",
        "Quincy",
        "A Quincy does not create energy: he gathers it — from the air, from projectiles, from spells flying at him — and returns it as arrows.\n\nThey have hunted Hollows for generations, and they do not purify: they destroy. That is why they do not get along with the Shinigami."),
    "class_gunslinger": (
        "Pistoleiro",
        "A sua mana não sai em raios nem em chamas: sai pelo cano. Cada arma é feita de mana, e cada bala carrega a magia que você montar.\n\nNenhum nativo teria inventado isso. Foi ideia de invocados de antes.",
        "Gunslinger",
        "Your mana comes out neither as lightning nor as flame: it comes out of the barrel. Every gun is made of mana, and every bullet carries the spell you build.\n\nNo native would have invented this. It was the idea of earlier summoned."),
    "class_ranger": (
        "Ranger",
        "O Ranger nunca caça sozinho: a águia marca o alvo, o lobo derruba, o corcel leva para longe. E cada flecha é montada como uma magia.\n\nA Trilha vai do Batedor ao Senhor das Feras — tradição dos batedores de Nordheim.",
        "Ranger",
        "The Ranger never hunts alone: the eagle marks the target, the wolf brings it down, the steed carries you away. And every arrow is built like a spell.\n\nThe Trail runs from Scout to Lord of Beasts — a tradition of the scouts of Nordheim."),
    "class_gambler": (
        "Apostador",
        "Toda magia do Apostador rola a sorte: de −99% a +100% de poder. Azar existe — mas a Tensão sobe a cada rolagem ruim, e quando o JACKPOT vem, a sorte fica infinita.\n\nA Casa sempre ganha... a menos que você seja a Casa.",
        "Gambler",
        "Every Gambler spell rolls the dice: from −99% to +100% power. Bad luck exists — but Tension rises with every bad roll, and when the JACKPOT comes, luck becomes infinite.\n\nThe House always wins... unless you are the House."),
    "class_steelball": (
        "Bola de Aço",
        "Uma bola de aço que gira não é uma pedra arremessada: é uma lição de geometria. O Mestre Rotari ensina a Rotação em cinco lições.\n\nNove Partes do Cadáver Santo estão escondidas em sepulturas pelo mundo. Dizem que o Santo também veio de longe.",
        "Steel Ball",
        "A spinning steel ball is not a thrown stone: it is a lesson in geometry. Master Rotari teaches the Spin in five lessons.\n\nNine Parts of the Holy Corpse lie hidden in tombs around the world. They say the Saint, too, came from far away."),
    # ---- Chefes
    "boss_horde": (
        "Os Reis da Horda",
        "O Rei Goblin conta moedas roubadas numa fortaleza de paliçadas e jura que a coroa é dele por direito. O Lorde Orc nunca perdeu um duelo. O Lorde Kobold guarda o pináculo das Torres.\n\nCaído um, os outros resolvem mostrar quem manda.",
        "The Kings of the Horde",
        "The Goblin King counts stolen coins in a palisade fortress and swears the crown is his by right. The Orc Lord has never lost a duel. The Kobold Lord guards the summit of the Towers.\n\nWhen one falls, the others decide to show who is in charge."),
    "boss_minotaur": (
        "O Minotauro",
        "Num labirinto que muda de lugar, o Minotauro conta os passos de quem entra. Lá dentro não se constrói, não se quebra e não se pula muro.\n\nSó existe você, os corredores e o bufar dele cada vez mais perto.",
        "The Minotaur",
        "In a labyrinth that shifts its place, the Minotaur counts the steps of whoever enters. Inside you cannot build, break or climb over walls.\n\nThere is only you, the corridors and his snorting, closer every time."),
    "boss_dark_masters": (
        "Mestres das Trevas",
        "Numa cripta, um Lich guarda a própria alma num filactério. Num altar profano, um Patrono acorrentado sussurra pactos. No pântano, uma bruxa trocou a alma por um caldeirão sem fundo. Numa torre, um Arquimago expulso sorri com todas as suas ilusões.\n\nNem todo poder deste mundo nasceu limpo.",
        "Masters of Darkness",
        "In a crypt, a Lich keeps his own soul in a phylactery. On a profane altar, a chained Patron whispers pacts. In the swamp, a witch traded her soul for a bottomless cauldron. In a tower, an expelled Archmage smiles with all his illusions.\n\nNot every power in this world was born clean."),
    "boss_horrors": (
        "Horrores Ocultos",
        "Há monstros que rugem e monstros que esperam. A Falsa Hidra canta uma canção que apaga a memória. O Rei Mímico transformou cada baú num estômago. A Manticora caça com uma cauda de espinhos.\n\nCresceram nas sobras da Ruptura, onde ninguém olhava.",
        "Hidden Horrors",
        "Some monsters roar and some monsters wait. The False Hydra sings a song that erases memory. The Mimic King turned every chest into a stomach. The Manticore hunts with a tail of spines.\n\nThey grew in the leftovers of the Rupture, where nobody was looking."),
    "boss_titans": (
        "Titãs Selvagens",
        "Quatro feras tão antigas quanto os santuários: a Hidra do Pântano, o Basilisco do Deserto, o Colosso das Ruínas e o Wyvern Rei dos Picos.\n\nDormiam sob o mundo. Dizem que a ascensão de um invocado as acorda. O Colosso ainda guarda uma cidade que não existe mais.",
        "Wild Titans",
        "Four beasts as old as the shrines: the Swamp Hydra, the Desert Basilisk, the Ruin Colossus and the Wyvern King of the Peaks.\n\nThey slept beneath the world. They say the rise of a summoned wakes them. The Colossus still guards a city that no longer exists."),
    "boss_worm_king": (
        "O Verme-Rei das Profundezas",
        "No fundo da Masmorra em Camadas dorme um verme do tamanho de uma torre. Ele devora geodos de mana — os cristais cresceram na própria carapaça.\n\nSome na terra e irrompe embaixo da presa.",
        "The Worm King of the Depths",
        "At the bottom of the Layered Dungeon sleeps a worm the size of a tower. It devours mana geodes — the crystals grew on its own carapace.\n\nIt vanishes into the earth and erupts beneath its prey."),
    "boss_guardians": (
        "Os Guardiões Elementais",
        "Ignis nas Ilhas de Brasa, Maré no Mar Suspenso, o Golem Primordial nos Picos Terrosos e Zéfiro nos Céus de Tempestade.\n\nSão antigos o bastante para lembrar quem selou os santuários. Quem vence os quatro é lembrado como “o que veio de longe”.",
        "The Elemental Guardians",
        "Ignis in the Ember Isles, Tide in the Suspended Sea, the Primordial Golem in the Earthen Peaks and Zephyr in the Storm Skies.\n\nThey are old enough to remember who sealed the shrines. Whoever defeats all four is remembered as “the one who came from afar”."),
    "boss_sovereign": (
        "O Soberano do Abismo",
        "Governa o Abismo do alto de um trono feito de estrelas mortas, com uma coroa de chifres. É ele quem assina do outro lado dos pactos.\n\nAcredita que o invocado é a chave para encerrar a guerra contra o céu.",
        "The Abyss Sovereign",
        "He rules the Abyss from a throne of dead stars, wearing a crown of horns. He is the one who signs on the other side of every pact.\n\nHe believes the summoned is the key to ending the war against the sky."),
    "boss_seraph": (
        "O Serafim",
        "Seis asas, mil olhos e a convicção absoluta de que a ordem vale mais que a liberdade. Guarda o Santuário Celestial.\n\nAcredita — como o inimigo dele — que o invocado é a chave para encerrar a guerra.",
        "The Seraph",
        "Six wings, a thousand eyes and the absolute conviction that order is worth more than freedom. He guards the Celestial Sanctuary.\n\nHe believes — like his enemy — that the summoned is the key to ending the war."),
    "boss_las_noches": (
        "Os Senhores de Las Noches",
        "Barragan Louisenbairn, o Deus-Rei do Mundo Oco: coroa de osso, machado de cinza e o aspecto da Velhice. A Imperatriz das Areias, a quem cada grão obedece como lâmina.\n\nE os Arrancar — Vazios que arrancaram a própria máscara e ganharam nome e orgulho.",
        "The Lords of Las Noches",
        "Barragan Louisenbairn, God-King of the Hollow World: crown of bone, axe of ash and the aspect of Old Age. The Sand Empress, whom every grain obeys like a blade.\n\nAnd the Arrancar — Hollows who tore off their own mask and gained a name and pride."),
    "boss_demon_lord": (
        "O Lorde Demônio",
        "Senhor da Cidadela Demoníaca. Começa como espadachim de corte — corte amplo, estocada, postura impecável. Depois chama a Legião. E no fim grita que chega de fingir ser humano.\n\nAs últimas palavras são sempre as mesmas: “Esta cidadela... ainda... é minha...”",
        "The Demon Lord",
        "Master of the Demon Citadel. He starts as a court swordsman — wide cut, thrust, flawless stance. Then he calls the Legion. And at the end he shouts that he is done pretending to be human.\n\nHis last words are always the same: “This citadel... is still... mine...”"),
    "boss_star_devourer": (
        "O Devorador de Estrelas",
        "No Observatório, no topo da Biblioteca do Vazio, algo enorme dorme olhando para as estrelas — e comendo-as.\n\nEle come estrelas. Você parece uma.",
        "The Star Devourer",
        "In the Observatory, atop the Library of the Void, something huge sleeps gazing at the stars — and eating them.\n\nIt eats stars. You look like one."),
    "boss_herald": (
        "O Arauto do Crepúsculo",
        "A causa da Grande Ruptura. Há mil anos invocou o primeiro herói de outro mundo... e o devorou. Expande o próprio Domínio, para o tempo e, uma única vez, volta atrás.\n\n“Você que foi invocado de outro mundo... veio ver o fim deste?”",
        "The Twilight Herald",
        "The cause of the Great Rupture. A thousand years ago he summoned the first hero from another world... and devoured him. He expands his own Domain, stops time and, exactly once, turns it back.\n\n“You who were summoned from another world... have you come to see the end of this one?”"),
    "boss_dragon": (
        "O Dragão Primordial",
        "O dragão que era todos os elementos antes de os elementos terem nome. Selado além do Crepúsculo pelos Seis Sábios, acorda quando o Arauto cai — e lembra de cada um que o prendeu.\n\nEle não luta contra um herói: luta contra um exército. E ele volta. Sempre volta.",
        "The Primordial Dragon",
        "The dragon that was every element before the elements had names. Sealed beyond the Twilight by the Six Sages, it wakes when the Herald falls — and remembers everyone who bound it.\n\nIt does not fight a hero: it fights an army. And it returns. It always returns."),
    # ---- Verdades Ocultas
    "truth_towers": (
        "Quem ergueu as torres",
        "Ninguém “não sabe” quem ergueu as Torres de Desafio e as Câmaras de Provação. Foi Brom, o Sábio da Terra, com a Ordem das Provações de Velmora.\n\nEle sabia que um dia viria alguém de fora, sem teto na alma e sem tempo para aprender devagar. As torres são um campo de treino deixado para essa pessoa. A placa na porta — “Vença ou volte” — é um recado para você.",
        "Who raised the towers",
        "It is not true that “nobody knows” who raised the Challenge Towers and the Trial Chambers. It was Brom, the Sage of Earth, with Velmora's Order of Trials.\n\nHe knew that one day someone would come from outside, with no ceiling on their soul and no time to learn slowly. The towers are a training ground left for that person. The sign on the door — “Win or go back” — is a message for you."),
    "truth_seventh": (
        "O Sétimo",
        "Os Sábios não eram seis. Havia um aprendiz, o mais novo, o que anotava tudo: Vesper. Foi ele quem escreveu o rito do Chamado, e foi ele quem aceitou ficar entre a Luz e a Sombra segurando o selo “por alguns anos”.\n\nNinguém voltou para rendê-lo. O Arauto do Crepúsculo não é um monstro que os Sábios enfrentaram. É um deles — o que ficou para trás.",
        "The Seventh",
        "The Sages were not six. There was an apprentice, the youngest, the one who wrote everything down: Vesper. He wrote the rite of the Call, and he agreed to stand between Light and Shadow holding the seal “for a few years”.\n\nNobody came back to relieve him. The Twilight Herald is not a monster the Sages fought. He is one of them — the one left behind."),
    "truth_king": (
        "O homem sob a coroa",
        "O Rei Demônio tem nome de gente: Aldric Velmor, último Imperador de Velmora. Na noite da Ruptura, com a capital caindo, ele assinou o primeiro pacto com o Abismo — a corte inteira em troca de nunca morrer. O Abismo cumpriu ao pé da letra.\n\nA irmã dele, Aldora, recusou. Levou os sobreviventes para os campos do sul e fundou um reino com o próprio nome: Aldória. É por isso que ele diz que tudo “ainda é dele”. E é por isso que odeia tanto aqueles campos dourados.",
        "The man beneath the crown",
        "The Demon King has a human name: Aldric Velmor, last Emperor of Velmora. On the night of the Rupture, with the capital falling, he signed the first pact with the Abyss — his whole court in exchange for never dying. The Abyss honored it to the letter.\n\nHis sister, Aldora, refused. She led the survivors to the southern fields and founded a kingdom bearing her own name: Aldoria. That is why he says everything is “still his”. And that is why he hates those golden fields so much."),
    "truth_call": (
        "Por que você foi trazido",
        "Tudo o que nasce em Astéria é feito da mana do Dragão — e nada feito dela consegue destruí-lo. Por isso os Sábios criaram o Chamado: um rito que puxa uma alma de um mundo SEM mana. Um vaso vazio. Sem teto. Capaz de romper Círculo atrás de Círculo, e de ferir a origem.\n\nA voz suave que você ouviu é o eco deles, torcendo. Mas o Arauto usa o mesmo rito como armadilha: espera cada invocado encher até o Nono Círculo... e chama para jantar. Você foi trazido para terminar o trabalho dos Sábios. Ele acha que você foi trazido para ele.",
        "Why you were brought here",
        "Everything born in Asteria is made of the Dragon's mana — and nothing made of it can destroy it. So the Sages created the Call: a rite that pulls a soul from a world WITHOUT mana. An empty vessel. No ceiling. Able to break Circle after Circle, and to wound the origin.\n\nThe gentle voice you heard is their echo, hoping. But the Herald uses the same rite as a trap: he waits for each summoned to fill up to the Ninth Circle... and calls them to dinner. You were brought to finish the Sages' work. He thinks you were brought for him."),
    "truth_beasts": (
        "Os sonhos do Dragão",
        "A Hidra, o Basilisco, o Colosso e o Wyvern Rei não são só feras velhas. São pedaços do sono do Dragão que escaparam do selo — sonhos com dentes.\n\nEles acordam quando alguém no mundo cresce demais, porque o Dragão, dormindo, sente. Cada Titã que você derruba é o Dragão se mexendo na cama.",
        "The Dragon's dreams",
        "The Hydra, the Basilisk, the Colossus and the Wyvern King are not just old beasts. They are pieces of the Dragon's sleep that slipped past the seal — dreams with teeth.\n\nThey wake when someone in the world grows too strong, because the Dragon, asleep, can feel it. Every Titan you bring down is the Dragon stirring in its bed."),
    "truth_moon": (
        "A segunda lua",
        "A lua pálida não é presente nem olho. É o selo — visto de baixo. Os Sábios o penduraram no céu na noite do Selamento, e o Arauto o segura por dentro do Crepúsculo.\n\nPor isso ela parece mais fraca a cada século. Quando a segunda lua rachar, o Dragão acorda.",
        "The second moon",
        "The pale moon is neither a gift nor an eye. It is the seal — seen from below. The Sages hung it in the sky on the night of the Sealing, and the Herald holds it from inside the Twilight.\n\nThat is why it looks weaker every century. When the second moon cracks, the Dragon wakes."),
    "truth_dwarves": (
        "Por que os anões desceram",
        "Os anões não fugiram da Ruptura. Pyra, a Sábia do Fogo, pediu que eles guardassem lá no fundo a única coisa que ela não podia esconder na superfície: a forja em que as runas do selo foram gravadas.\n\nEles fecharam a porta e esperam há mil anos alguém que saiba pedir do jeito certo. Uma runa não é enfeite. É uma promessa gravada em metal.",
        "Why the dwarves went below",
        "The dwarves did not flee the Rupture. Pyra, the Sage of Fire, asked them to keep, deep down, the one thing she could not hide on the surface: the forge where the runes of the seal were carved.\n\nThey shut the door and have waited a thousand years for someone who knows how to ask the right way. A rune is not an ornament. It is a promise carved in metal."),
}

# ------------------------------------------------------------------------------------------------ Fragmentos
# id: (título pt, texto pt, título en, texto en)
FRAGMENTS = {
    # Crônicas de Velmora
    "velmora_1": (
        "A Ordem das Provações",
        "“Ano 461 do Império. Mestre Brom mandou erguer mais uma torre, e mais uma vez não disse para quem. Sete andares, um guardião no topo e uma placa na porta sul. Quando perguntei quem treinaria ali, ele respondeu: alguém que ainda não nasceu neste mundo.”\n\nA crônica descreve o caminho até uma dessas torres.",
        "The Order of Trials",
        "“Year 461 of the Empire. Master Brom ordered yet another tower raised, and once again would not say for whom. Seven floors, a guardian at the top and a sign on the south door. When I asked who would train there, he answered: someone not yet born in this world.”\n\nThe chronicle describes the way to one of those towers."),
    "velmora_2": (
        "O Teto da Alma",
        "“Os nossos melhores magos param no Quinto Círculo. Não é preguiça: a alma encosta em alguma coisa e não passa. Os Sábios construíram santuários para romper esse teto à força, com um núcleo, quatro pedestais e muita coragem.”\n\nNa margem, alguém desenhou o caminho até um Santuário de Ascensão.",
        "The Ceiling of the Soul",
        "“Our best mages stop at the Fifth Circle. It is not laziness: the soul presses against something and cannot pass. The Sages built sanctums to break that ceiling by force, with a core, four pedestals and a great deal of courage.”\n\nIn the margin, someone drew the way to an Ascension Sanctum."),
    "velmora_3": (
        "O Guarda que Ficou",
        "“Para guardar a cidade de Varen ergueram um golem do tamanho de uma torre e lhe deram uma só ordem: ninguém passa. Varen não existe mais. Ninguém teve coragem de contar a ele.”\n\nO texto termina com a direção das ruínas onde ele ainda monta guarda.",
        "The Guard Who Stayed",
        "“To guard the city of Varen they raised a golem the size of a tower and gave it a single order: none shall pass. Varen no longer exists. Nobody had the heart to tell it.”\n\nThe text ends with the direction of the ruins where it still stands guard."),
    "velmora_4": (
        "A Escola dos Glifos",
        "“Aqui se ensinava que um feitiço é uma frase: Forma, Efeito, Aumento. Os alunos que erravam a gramática explodiam as próprias sobrancelhas. Os que acertavam explodiam as dos outros.”\n\nA página é o resto de um guia para calouros, com um mapa até as Ruínas Arcanas.",
        "The School of Glyphs",
        "“Here it was taught that a spell is a sentence: Form, Effect, Augment. Students who got the grammar wrong blew off their own eyebrows. Those who got it right blew off other people's.”\n\nThe page is what is left of a freshman guide, with a map to the Arcane Ruins."),
    "velmora_5": (
        "O Jardim do Imperador",
        "“Sua Majestade mandou plantar um labirinto de pedra nos jardins e soltar nele o campeão da guarda, para que nenhum assassino chegasse ao palácio. O palácio caiu. O labirinto não. O campeão também não — só mudou bastante.”\n\nO verso da página mostra onde o labirinto foi parar.",
        "The Emperor's Garden",
        "“His Majesty had a stone labyrinth planted in the gardens and the champion of the guard set loose in it, so that no assassin would reach the palace. The palace fell. The labyrinth did not. Neither did the champion — he has merely changed a great deal.”\n\nThe back of the page shows where the labyrinth ended up."),
    "velmora_6": (
        "A Última Noite",
        "“O céu rachou sobre o mar ao pôr do sol. Luz e sombra misturadas, como leite em tinta. A capital afundou antes da meia-noite. Escrevo isto num barco, e a ilha atrás de mim não estava lá ontem.”\n\nÉ o último registro de Velmora. Ele dá a posição da ilha.",
        "The Last Night",
        "“The sky cracked over the sea at sunset. Light and shadow mingled, like milk in ink. The capital sank before midnight. I write this in a boat, and the island behind me was not there yesterday.”\n\nIt is the last record of Velmora. It gives the island's position."),
    # Diários dos Seis Sábios
    "sages_1": (
        "Diário de Pyra",
        "“Guardei o Fogo num santuário e a minha forja em outro lugar, bem mais fundo. Os anões juraram silêncio. Se você está lendo isto e sabe segurar um martelo, vá primeiro ao santuário: o Fogo gosta de saber quem o chama.”",
        "Pyra's Diary",
        "“I kept Fire in a shrine and my forge somewhere else, far deeper. The dwarves swore silence. If you are reading this and know how to hold a hammer, go to the shrine first: Fire likes to know who is calling it.”"),
    "sages_2": (
        "Diário de Nerida",
        "“A Água cura e a Água afoga, e as duas coisas são a mesma receita com doses diferentes. Deixei o santuário perto de onde ela corre. As minhas alunas vão brigar pelos cadernos. Que briguem — só não deixem nenhuma ficar com todos.”",
        "Nerida's Diary",
        "“Water heals and Water drowns, and both are the same recipe in different doses. I left the shrine near where it runs. My students will fight over the notebooks. Let them — just never let one of them keep them all.”"),
    "sages_3": (
        "Diário de Brom",
        "“Pedra não mente. Fiz o santuário da Terra, as câmaras e as torres com a mesma regra: quem entra precisa sair mais forte ou não sair. Não é crueldade. Quem vem depois de nós não vai ter tempo de aprender devagar.”",
        "Brom's Diary",
        "“Stone does not lie. I built the Earth shrine, the chambers and the towers by the same rule: whoever enters must leave stronger or not leave. It is not cruelty. Those who come after us will not have time to learn slowly.”"),
    "sages_4": (
        "Diário de Aelis",
        "“Nunca tive casa, então deixei o Vento num lugar alto e a minha voz dentro do rito. Se um dia alguém for puxado para cá sem pedir, quero que a primeira coisa que ouça seja gentil. Desculpe por isso, aliás. De verdade.”",
        "Aelis's Diary",
        "“I never had a home, so I left the Wind in a high place and my voice inside the rite. If someone is ever pulled here without asking, I want the first thing they hear to be kind. Sorry about that, by the way. Truly.”"),
    "sages_5": (
        "Diário de Solen",
        "“Disse aos meus que a Aurora sempre volta. Não disse que pendurei o selo no céu e que ele parece uma lua. Um sacerdote também escolhe o que ensina. O santuário da Luz fica onde o sol bate primeiro.”",
        "Solen's Diary",
        "“I told my people that the Dawn always returns. I did not tell them that I hung the seal in the sky and that it looks like a moon. A priest, too, chooses what he teaches. The Light shrine stands where the sun strikes first.”"),
    "sages_6": (
        "Diário de Nyra",
        "“Somos seis no retrato e sete na mesa. O menino pediu para ficar de guarda e nenhum de nós teve coragem de dizer não. Deixei as minhas sombras vigiando a porta dele. Se elas esquecerem o que vigiam, a culpa é minha. O santuário da Sombra fica onde ninguém olha duas vezes.”",
        "Nyra's Diary",
        "“We are six in the portrait and seven at the table. The boy asked to stand guard and none of us had the courage to say no. I left my shadows watching his door. If they forget what they watch, the fault is mine. The Shadow shrine stands where nobody looks twice.”"),
    # Confissões do Rei Demônio
    "demon_1": (
        "A Letra Miúda",
        "“Um credor do Abismo ficou preso num altar com quatro correntes. Foi a espiã da minha corte que o prendeu. Li o contrato dele antes de escrever o meu. Deveria ter lido duas vezes.”\n\nA confissão indica onde o altar ainda está.",
        "The Fine Print",
        "“A creditor of the Abyss was bound to an altar with four chains. It was the spy of my court who bound him. I read his contract before writing mine. I should have read it twice.”\n\nThe confession tells where the altar still stands."),
    "demon_2": (
        "Onde Assinei",
        "“Desci pela fenda com a coroa na cabeça e a capital queimando atrás de mim. Pedi uma coisa só: que nenhum de nós morresse naquela noite. Lá embaixo alguém sorriu e disse que isso era fácil.”\n\nEle descreve a descida — uma Fenda Abissal.",
        "Where I Signed",
        "“I went down through the chasm with the crown on my head and the capital burning behind me. I asked for one thing only: that none of us would die that night. Down there someone smiled and said that was easy.”\n\nHe describes the descent — an Abyssal Chasm."),
    "demon_3": (
        "Ainda É Minha",
        "“Ergui a cidadela com a mesma planta do palácio. Mesma sala do trono, mesma guarda, mesmos estandartes — só a pedra mudou. Os meus cortesãos têm chifres agora. Eu continuo usando as luvas.”\n\nA página traz o caminho, no Nether, até a Cidadela Demoníaca.",
        "Still Mine",
        "“I raised the citadel on the same plan as the palace. Same throne room, same guard, same banners — only the stone changed. My courtiers have horns now. I still wear the gloves.”\n\nThe page gives the way, in the Nether, to the Demon Citadel."),
    "demon_4": (
        "O Que Enterrei",
        "“Antes de descer, enterrei o que era de Aldric e não de um rei de demônios: o anel de minha mãe, umas moedas com o meu rosto de menino, bobagens. Minha irmã nunca soube onde. Se você achar, fique. Só não me conte.”",
        "What I Buried",
        "“Before going down I buried what belonged to Aldric and not to a king of demons: my mother's ring, some coins with my face as a boy, trifles. My sister never knew where. If you find it, keep it. Just do not tell me.”"),
    # Cartas do Primeiro Invocado
    "herald_1": (
        "Céu de Duas Luas",
        "“Carta um. Acordei num campo, com duas luas no céu e uma voz dizendo que eu tinha sido trazido por um motivo. Achei que era sonho. Aí um slime me mordeu.\n\nUm velho me mostrou um círculo de pedra com quatro obeliscos e disse que a resposta estava além dele.”",
        "A Sky of Two Moons",
        "“Letter one. I woke in a field, with two moons in the sky and a voice saying I had been brought for a reason. I thought it was a dream. Then a slime bit me.\n\nAn old man showed me a stone circle with four obelisks and said the answer lay beyond it.”"),
    "herald_2": (
        "A Voz Suave",
        "“Carta dois. São duas vozes, não uma. A primeira é gentil e pede desculpas. A segunda só fala quando eu fico mais forte, e fala o meu nome como quem lê um cardápio.\n\nOs anjos da escadaria dizem que não ouvem nenhuma das duas.”",
        "The Gentle Voice",
        "“Letter two. There are two voices, not one. The first is kind and apologizes. The second only speaks when I grow stronger, and says my name like someone reading a menu.\n\nThe angels of the stairway say they hear neither.”"),
    "herald_3": (
        "Eles Não Sobem",
        "“Carta três. Perguntei a um necromante por que os daqui param de crescer e eu não. Ele riu e disse que a alma deles tem dono. A minha, pelo visto, veio sem etiqueta.\n\nEle mora numa cripta e não recebe visitas. Recebeu a mim.”",
        "They Do Not Rise",
        "“Letter three. I asked a necromancer why the people here stop growing and I do not. He laughed and said their souls have an owner. Mine, it seems, came without a label.\n\nHe lives in a crypt and takes no visitors. He took me.”"),
    "herald_4": (
        "As Fadas Sabem",
        "“Carta quatro. As fadas são mais velhas que o império e não gostam de mentir, só de rir. Perguntei quem me chamou. Elas pararam de rir.\n\nDisseram: o menino do crepúsculo está com fome. E me mandaram embora do círculo.”",
        "The Fairies Know",
        "“Letter four. The fairies are older than the empire and do not like to lie, only to laugh. I asked who called me. They stopped laughing.\n\nThey said: the twilight boy is hungry. And they sent me out of the ring.”"),
    "herald_5": (
        "Para Quem Vier Depois",
        "“Última carta. Vou atravessar a Fenda amanhã; cheguei ao Nono Círculo e a segunda voz não para mais. Se eu não voltar, não confie no convite.\n\nEnterrei o que não vou precisar lá dentro. É seu. Vá mais forte do que eu fui — e não vá sozinho.”",
        "For Whoever Comes After",
        "“Last letter. I cross the Rift tomorrow; I reached the Ninth Circle and the second voice never stops now. If I do not return, do not trust the invitation.\n\nI buried what I will not need in there. It is yours. Go stronger than I went — and do not go alone.”"),
    # Caderno do Caçador
    "hunter_1": (
        "A Hidra do Pântano",
        "“Corte uma cabeça e nascem duas. Perdi três lanças aprendendo isso. O toco precisa queimar — fogo, sempre fogo.\n\nAnotei onde fica o covil. Não volto lá sem um mago.”",
        "The Swamp Hydra",
        "“Cut off one head and two grow back. I lost three spears learning that. The stump has to burn — fire, always fire.\n\nI noted where the lair is. I am not going back without a mage.”"),
    "hunter_2": (
        "O Rei dos Picos",
        "“Ele reina sobre as montanhas e as tempestades. Os raios caem onde ele aponta. Subi com corda de cânhamo; desça com corda que não conduza.\n\nO ninho fica no pico marcado.”",
        "The King of the Peaks",
        "“He reigns over mountains and storms. Lightning falls where he points. I climbed with hemp rope; come down with rope that does not conduct.\n\nThe nest is on the marked peak.”"),
    "hunter_3": (
        "Olhos de Pedra",
        "“No túmulo sob as dunas, estátuas de aventureiros ainda seguram as espadas erguidas. Não são estátuas. Não olhe nos olhos dele; lute pelo reflexo ou pelas costas.\n\nA entrada da tumba está assinalada.”",
        "Eyes of Stone",
        "“In the tomb beneath the dunes, statues of adventurers still hold their swords aloft. They are not statues. Do not meet his eyes; fight by reflection or from behind.\n\nThe entrance to the tomb is marked.”"),
    "hunter_4": (
        "Cauda de Espinhos",
        "“Corpo de leão, asas de morcego e uma cauda que dispara espinhos como uma besta de cerco. O rugido gela a coragem; tape os ouvidos e continue andando.\n\nA toca fica no desfiladeiro que desenhei.”",
        "Tail of Spines",
        "“A lion's body, bat wings and a tail that fires spines like a siege crossbow. The roar freezes your courage; cover your ears and keep walking.\n\nThe den is in the ravine I drew.”"),
    "hunter_5": (
        "A Vila Que Esqueceu",
        "“Passei por uma vila vazia e não lembro de ter passado. Está no meu caderno, com a minha letra: NÃO ESCUTE A CANÇÃO. As cabeças só existem quando ninguém olha.\n\nDeixei o lugar marcado. Acho que fui eu.”",
        "The Village That Forgot",
        "“I passed through an empty village and do not remember passing. It is in my notebook, in my handwriting: DO NOT LISTEN TO THE SONG. The heads only exist when nobody is looking.\n\nI left the place marked. I think it was me.”"),
    "hunter_6": (
        "Bom Demais",
        "“Um cofre cheio de baús, todos destrancados. Bom demais para ser verdade — e é exatamente isso. Jogue uma pedra em cada baú antes de abrir. Se a pedra sumir, corra.\n\nO cofre fica onde marquei. Eu avisei.”",
        "Too Good",
        "“A vault full of chests, all unlocked. Too good to be true — and that is exactly it. Throw a stone at each chest before opening. If the stone vanishes, run.\n\nThe vault is where I marked. I warned you.”"),
    # Mapas do Contrabandista
    "smuggler_1": (
        "Primeiro X",
        "“Regra um do ofício: nunca guarde o lucro onde você dorme. Enterrei a primeira parte longe das muralhas, fundo o bastante para o gado não achar.”\n\nO mapa é tosco, mas o X é claro.",
        "First X",
        "“Rule one of the trade: never keep your profit where you sleep. I buried the first share far from the walls, deep enough that the cattle would not find it.”\n\nThe map is crude, but the X is clear."),
    "smuggler_2": (
        "Segundo X",
        "“Os guardas de Aldória aceitam suborno. Os de Nordheim aceitam hidromel. Os de Sahra aceitam os dois e prendem você do mesmo jeito. Esta parte ficou no caminho da fuga.”\n\nOutro X, em outra direção.",
        "Second X",
        "“The guards of Aldoria take bribes. Those of Nordheim take mead. Those of Sahra take both and arrest you anyway. This share stayed along the escape route.”\n\nAnother X, in another direction."),
    "smuggler_3": (
        "Terceiro X",
        "“O Submundo cobra dez por cento de tudo o que passa pelas mãos dele. O que não passa pelas mãos dele ele não cobra. Daí este buraco.”\n\nO terceiro X está circulado duas vezes.",
        "Third X",
        "“The Underworld charges ten percent of everything that passes through its hands. What does not pass through its hands, it does not charge. Hence this hole.”\n\nThe third X is circled twice."),
    "smuggler_4": (
        "Último X",
        "“Se você está lendo isto, eu me aposentei ou fui preso. Nos dois casos, não vou precisar. Beba uma por mim numa estalagem — e pague a sua própria.”\n\nO último X, e uma mancha de vinho.",
        "Last X",
        "“If you are reading this, I have either retired or been arrested. Either way, I will not be needing it. Have a drink for me at an inn — and pay for your own.”\n\nThe last X, and a wine stain."),
    # Runas das Profundezas
    "deep_1": (
        "A Grade Nunca Baixada",
        "As runas dizem: “Fechamos a porta, mas não baixamos a grade. Quem chegar até aqui cavando merece ao menos uma caneca. Tragam ferro bom e modos melhores.”\n\nEmbaixo, uma seta aponta para o fundo — e para os lados.",
        "The Gate Never Lowered",
        "The runes read: “We shut the door but did not lower the gate. Whoever digs their way here deserves at least a mug. Bring good iron and better manners.”\n\nBelow, an arrow points down — and sideways."),
    "deep_2": (
        "Quem Desce",
        "As runas dizem: “Poço de sondagem número nove. Quatro andares abertos, armadilhas armadas, algo grande comendo os geodos lá embaixo. Selado por ordem do rei. Quem desce nem sempre sobe.”\n\nA pedra indica a ruína com o poço.",
        "Those Who Go Down",
        "The runes read: “Survey shaft number nine. Four floors opened, traps set, something big eating the geodes below. Sealed by order of the king. Those who go down do not always come up.”\n\nThe stone points to the ruin with the shaft."),
    "deep_3": (
        "Vizinhos Barulhentos",
        "As runas dizem: “Os kobolds cavaram por cima de nós de novo. Roubam picaretas, cantam mal e juram que têm um rei numa torre. Não alimentem.”\n\nAlguém riscou um mapa até a toca.",
        "Noisy Neighbors",
        "The runes read: “The kobolds have dug above us again. They steal pickaxes, sing badly and swear they have a king in a tower. Do not feed them.”\n\nSomeone scratched a map to the den."),
    # Cantos do Vazio
    "void_1": (
        "O Céu Rasga",
        "“À noite, com a oferenda certa, o céu rasga como papel preto,\ne do outro lado a lua não se põe.\nQuem perdeu o coração caminha na areia branca.\nQuem ainda tem um, que o segure com as duas mãos.”\n\nO canto fala de um altar de osso no meio do nada.",
        "The Sky Tears",
        "“At night, with the right offering, the sky tears like black paper,\nand on the other side the moon does not set.\nThose who lost their heart walk the white sand.\nThose who still have one, hold it with both hands.”\n\nThe song speaks of an altar of bone in the middle of nowhere."),
    "void_2": (
        "O Que o Vazio Escreveu",
        "“Copiamos os livros que ninguém lê,\nporque o espaço tem gramática e alguém precisa lembrar dela.\nNo topo, o leitor dorme e mastiga estrelas.\nNão o acorde. Ou acorde, e traga amigos.”\n\nO canto vem das ilhas externas do End.",
        "What the Void Wrote",
        "“We copy the books nobody reads,\nfor space has a grammar and someone must remember it.\nAt the top, the reader sleeps and chews on stars.\nDo not wake it. Or wake it, and bring friends.”\n\nThe song comes from the outer islands of the End."),
    "void_3": (
        "A Lua Rachada",
        "“Olhei a segunda lua pela lente por quarenta noites.\nEla não gira. Não tem crateras. Tem costuras.\nNa quadragésima noite, uma costura se abriu.\nQueimei as anotações. Esta escapou.”\n\nA página saiu da torre de um mago.",
        "The Cracked Moon",
        "“I watched the second moon through the lens for forty nights.\nIt does not turn. It has no craters. It has seams.\nOn the fortieth night, a seam came open.\nI burned my notes. This one escaped.”\n\nThe page came from a mage's tower."),
}


def java_ids():
    """Ids declarados no Java: entradas da Enciclopédia, fragmentos (pela ordem nas coleções) e lugares."""
    book = open(os.path.join(JAVA, "LoreBook.java"), encoding="utf-8").read()
    entries = re.findall(r'"((?:world|history|realm|faction|dim|race|class|boss|truth)_[a-z_]+)",\s*"[a-z_]+:[a-z_]+"', book)
    cats = re.findall(r'cat\((?:"([a-z]+)"|TRUTHS)', book)
    cats = [c if c else "truths" for c in cats]
    frag = open(os.path.join(JAVA, "LoreFragments.java"), encoding="utf-8").read()
    body = frag[frag.index("static {"):frag.index("/** Peça de montagem")]
    frags, sets, places = [], [], set()
    for m in re.finditer(r'set\("([a-z]+)",\s*0x[0-9A-Fa-f]+,\s*"[a-z_]*",(.*?)\);\n', body, re.S):
        sid, spots = m.group(1), m.group(2)
        sets.append(sid)
        n = 0
        for s in re.finditer(r'\b(at|in|treasure)\(([^)]*)\)', spots):
            n += 1
            frags.append(f"{sid}_{n}")
            if s.group(1) == "treasure":
                places.add("treasure")
            else:
                names = re.findall(r'"([a-z_]+)"', s.group(2))
                places.add(names[0])
    return entries, cats, frags, sets, places


def main():
    entries, cats, frags, sets, places = java_ids()
    problems = []

    def compare(name, declared, written):
        for i in declared:
            if i not in written:
                problems.append(f"{name} sem texto: {i}")
        for i in written:
            if i not in declared:
                problems.append(f"texto de {name} sem id no Java: {i}")

    compare("entrada", entries, ENTRIES)
    compare("categoria", cats, CATEGORIES)
    compare("fragmento", frags, FRAGMENTS)
    compare("coleção", sets, SETS)
    compare("lugar", places, PLACES)
    if problems:
        print("\n".join(problems))
        sys.exit(1)

    pt, en = {}, {}

    def put(key, pair):
        pt[key], en[key] = pair[0], pair[1]

    for k, v in UI.items():
        put(k, v)
    for k, v in CATEGORIES.items():
        put(f"lore.isekai_mahou.cat.{k}", v)
    for k, v in SETS.items():
        put(f"lore.isekai_mahou.set.{k}", v)
    for k, v in PLACES.items():
        put(f"lore.isekai_mahou.place.{k}", v)
    for k, (tp, xp, te, xe) in ENTRIES.items():
        put(f"lore.isekai_mahou.entry.{k}", (tp, te))
        put(f"lore.isekai_mahou.entry.{k}.text", (xp, xe))
    for k, (tp, xp, te, xe) in FRAGMENTS.items():
        put(f"lore.isekai_mahou.frag.{k}", (tp, te))
        put(f"lore.isekai_mahou.frag.{k}.text", (xp, xe))

    os.makedirs(OUT, exist_ok=True)
    for name, data in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(f"lore: {len(pt)} chaves ({len(ENTRIES)} entradas, {len(FRAGMENTS)} fragmentos)")


if __name__ == "__main__":
    main()
