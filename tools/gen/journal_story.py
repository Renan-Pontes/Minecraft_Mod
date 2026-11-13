"""
Textos da release 1.0 do módulo "journal" (job "story"): a CAMPANHA (Atos, falas do Eco, cutscenes de marco), a seção
"Onde Conseguir" do Guia de Viagem e as descrições do Bestiário que faltavam.

Escreve src/main/lang/story/pt_br.json e en_us.json — pasta própria, porque src/main/lang/journal é reescrita inteira
por tools/gen/journal.py. Seguro de rodar de novo:  py -I tools/gen/journal_story.py

Confere (e falha se não bater):
  - cada batida de feature/journal/story/StoryBook.java tem título e o nº certo de falas (cenas: narração também);
  - cada assunto de feature/journal/guide/GuideTopics.java tem nome, "onde" e "como";
  - cada criatura do catálogo de feature/journal/bestiary/Bestiary.java tem descrição em algum src/main/lang;
  - nenhuma chave daqui repete, com texto diferente, uma chave de outra pasta de tradução.
A fonte da história é docs/LORE.md (seção 14). As Verdades Ocultas da Enciclopédia NÃO são contadas aqui: o Eco só
dá a pista e manda atrás das Páginas Perdidas.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken  # noqa: E402
JAVA = os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/journal")
LANG = os.path.join(ROOT, "src/main/lang")
OUT = os.path.join(LANG, "story")

PT, EN = {}, {}


def t(key, pt, en):
    assert key not in PT, key
    PT[key] = pt
    EN[key] = en


# ======================================================================================================================
#  Telas, opções e comandos
# ======================================================================================================================
t("cinematic.isekai_mahou.kind.story", "CRÔNICA", "CHRONICLE")
t("screen.isekai_mahou.journal.echoes", "Ecos da campanha", "Echoes of the campaign")
t("screen.isekai_mahou.journal.campaign.on",
  "Campanha LIGADA: o Eco conta a história do mundo nos marcos da sua jornada. Clique para desligar — nada se perde, tudo fica guardado aqui no Diário.",
  "Campaign ON: the Echo tells the world's story at the milestones of your journey. Click to turn it off — nothing is lost, it all stays here in the Journal.")
t("screen.isekai_mahou.journal.campaign.off",
  "Campanha DESLIGADA: sem falas nem cutscenes da história. Os \"Ecos\" de cada capítulo continuam aqui para ler quando quiser. Clique para ligar.",
  "Campaign OFF: no story lines or cutscenes. Each chapter's \"Echoes\" stay here to read whenever you like. Click to turn it on.")

t("screen.isekai_mahou.journal.extra_label", "No caminho:", "On the way:")
t("hint.isekai_mahou.journal_where", "Não sabe onde conseguir algo? Menu B → Guia de Viagem → Onde?",
  "Not sure where to get something? B menu → Travel Guide → Where?")
t("screen.isekai_mahou.where.tab", "Onde Conseguir", "Where to Get")
# Nome curto da seção na coluna do Guia de Viagem (cabem ~9 letras).
t("screen.isekai_mahou.menu.section.where", "Onde?", "Where?")
t("screen.isekai_mahou.where.header", "Onde Conseguir", "Where to Get")
t("screen.isekai_mahou.where.sub", "onde fica e como se faz", "where it is and how it works")
t("screen.isekai_mahou.where.next", "Seu próximo passo", "Your next step")
t("screen.isekai_mahou.where.next_none", "Nenhum objetivo em aberto agora. Explore, cuide da sua cidade — ou escolha um assunto ao lado.",
  "No open objective right now. Explore, look after your city — or pick a topic on the left.")
t("screen.isekai_mahou.where.next_tip", "A seta do rastreador (tecla J) aponta o lugar quando o passo tem um. Os assuntos ao lado dizem onde conseguir o resto.",
  "The tracker arrow (J key) points to the place when the step has one. The topics on the left tell you where to get everything else.")
t("screen.isekai_mahou.where.where", "Onde", "Where")
t("screen.isekai_mahou.where.how", "Como", "How")
t("screen.isekai_mahou.where.level", "nv %s", "lv %s")
t("screen.isekai_mahou.where.from_level", "Serve a partir do nível %s.", "Useful from level %s.")

t("isekai_mahou.configuration.section.isekai.mahou.story.client.toml", "Campanha e história (só você)", "Campaign and story (you only)")
t("isekai_mahou.configuration.section.isekai.mahou.story.client.toml.title", "Isekai Mahou — Campanha e história", "Isekai Mahou — Campaign and story")
t("isekai_mahou.configuration.storyCampaign", "Campanha (falas do Eco)", "Campaign (the Echo's lines)")
t("isekai_mahou.configuration.storyCampaign.tooltip",
  "O Eco (Espírito Guia) conta a história do mundo nos marcos da sua jornada: falas curtas, que não prendem e podem ser puladas. Desligada, nada se perde: tudo fica nos \"Ecos\" de cada capítulo do Diário.",
  "The Echo (Guide Spirit) tells the world's story at the milestones of your journey: short lines that never lock you and can be skipped. When off nothing is lost: it all stays in each Journal chapter's \"Echoes\".")
t("isekai_mahou.configuration.storyMilestoneCutscenes", "Cutscenes nos marcos", "Milestone cutscenes")
t("isekai_mahou.configuration.storyMilestoneCutscenes.tooltip",
  "Cutscene curta (6 s, sem travar os controles) no começo de cada Ato e nas grandes viradas. Desligada, a narração vira uma fala. A opção geral \"Cutscenes\" também vale.",
  "A short cutscene (6 s, controls never locked) at the start of each Act and at the big turns. When off, the narration becomes a spoken line. The general \"Cutscenes\" option also applies.")

t("command.isekai_mahou.story.status", "Campanha: %s · Cutscenes de marco: %s · Ecos: %s/%s · na fila: %s",
  "Campaign: %s · Milestone cutscenes: %s · Echoes: %s/%s · queued: %s")
t("command.isekai_mahou.story.on", "ligada", "on")
t("command.isekai_mahou.story.off", "desligada", "off")
t("command.isekai_mahou.story.reset", "Campanha zerada (%s marcadores apagados).", "Campaign reset (%s markers cleared).")
t("command.isekai_mahou.story.unknown", "Batida desconhecida: %s", "Unknown beat: %s")

# ======================================================================================================================
#  Campanha — Atos e quem fala
# ======================================================================================================================
ACTS = [
    (1, "Ato I — O Chamado", "Act I — The Call", "Onde estou?", "Where am I?"),
    (2, "Ato II — Os Seis", "Act II — The Six", "Quem selou este mundo?", "Who sealed this world?"),
    (3, "Ato III — As Sobras da Ruptura", "Act III — What the Rupture Left", "O que a Ruptura deixou?", "What did the Rupture leave behind?"),
    (4, "Ato IV — As Mãos do Dragão", "Act IV — The Dragon's Hands", "De onde vem a magia?", "Where does magic come from?"),
    (5, "Ato V — Luz e Sombra", "Act V — Light and Shadow", "De que lado você está?", "Whose side are you on?"),
    (6, "Ato VI — O Crepúsculo", "Act VI — The Twilight", "Por que eu?", "Why me?"),
    (7, "Ato VII — A Origem", "Act VII — The Origin", "E agora?", "And now?"),
    # Onda 5 — "o Dragão é o fim... depois dele é só a torre infinita": o Epílogo é só o Abismo Infinito (as masmorras,
    # torres e biomas que ficavam aqui foram para dentro dos Atos II, IV e V).
    (8, "Epílogo — O Abismo Infinito", "Epilogue — The Endless Abyss", "Até onde você desce?", "How deep can you go?"),
]
for n, pt, en, qpt, qen in ACTS:
    t(f"story.isekai_mahou.act.{n}", pt, en)
    t(f"story.isekai_mahou.act.{n}.question", qpt, qen)

for sid, pt, en in [
    ("echo", "Eco", "Echo"),
    ("narrator", "Crônica", "Chronicle"),
    ("unknown", "???", "???"),
    ("sage", "Voz do Santuário", "Voice of the Shrine"),
    ("guardian", "Guardião Elemental", "Elemental Guardian"),
    ("herald", "Arauto do Crepúsculo", "Twilight Herald"),
    ("inscription", "Inscrição na Porta", "Inscription on the Door"),
]:
    t(f"story.isekai_mahou.speaker.{sid}", pt, en)

# ======================================================================================================================
#  Campanha — batidas: id -> (título pt, título en, narração pt|None, narração en|None, [(fala pt, fala en), ...])
# ======================================================================================================================
BEATS = {
    # ---------------------------------------------------------------- Ato I
    "a1_call": ("Ato I — O Chamado", "Act I — The Call",
                "Duas luas num céu que você não conhece. Alguém chamou — e você veio.",
                "Two moons in a sky you do not know. Someone called — and you came.", [
                    ("Este mundo se chama Astéria. E, antes que pergunte: não fui bem eu que te chamei. Eu sou só o Eco de quem chamou.",
                     "This world is called Asteria. And before you ask: it wasn't quite me who called you. I'm only the Echo of those who did."),
                    ("Antes de salvar qualquer coisa, sobreviva. O resto eu conto no caminho — só se você quiser ouvir.",
                     "Before saving anything, survive. I'll tell the rest along the way — only if you want to hear it."),
                ]),
    "a1_city": ("Três Reinos", "Three Kingdoms", None, None, [
        ("Aldória, Sahra e Nordheim: três reinos, todos erguidos sobre os escombros de um império só. Ninguém aqui gosta de lembrar disso.",
         "Aldoria, Sahra and Nordheim: three kingdoms, all built on the rubble of a single empire. Nobody here likes to remember that."),
        ("A Guilda foi ideia de uma andarilha chamada Aelis. Ela nunca teve casa — então fez uma para todo mundo que também não tinha.",
         "The Guild was the idea of a wanderer named Aelis. She never had a home — so she made one for everyone else who didn't."),
    ]),
    "a1_trial": ("Feitas Sob Medida", "Made to Measure", None, None, [
        ("Ninguém sabe quem construiu as Câmaras de Provação. Eu tenho um palpite: alguém que sabia que você viria. Elas servem direitinho para treinar gente de fora.",
         "Nobody knows who built the Trial Chambers. I have a guess: someone who knew you were coming. They fit a little too well for training outsiders."),
    ]),
    # ---------------------------------------------------------------- Ato II
    "a2_six": ("Ato II — Os Seis", "Act II — The Six",
               "Seis sábios, seis santuários. Eles não mataram o Dragão: só o trancaram.",
               "Six sages, six shrines. They did not kill the Dragon: they only locked it away.", [
                   ("Pyra, Nerida, Brom, Aelis, Solen e Nyra. Repartiram a essência do Dragão em seis pedaços e selaram cada um num santuário.",
                    "Pyra, Nerida, Brom, Aelis, Solen and Nyra. They split the Dragon's essence into six pieces and sealed each one in a shrine."),
                   ("Cada elemento que você desperta é um desses pedaços respondendo. Eles gostam de você. Não sei se isso é bom.",
                    "Every element you awaken is one of those pieces answering. They like you. I'm not sure that's good."),
               ]),
    "a2_shrine": ("A Voz do Santuário", "The Voice of the Shrine", None, None, [
        ("\"Quem veio de longe não deve nada a este mundo. Por isso mesmo pode salvá-lo.\"",
         "\"Those who come from afar owe this world nothing. That is exactly why they can save it.\""),
        ("Você também ouviu? Os santuários guardam um resto da voz de quem os selou. Essa aí... eu conheço de algum lugar.",
         "You heard it too? The shrines keep a trace of the voice that sealed them. That one... I know it from somewhere."),
    ]),
    "a2_shadow": ("A Sombra e o Teto", "The Shadow and the Ceiling", None, None, [
        ("A Ascensão cobra um preço: encarar a própria Sombra. Você encarou. A maioria dos nativos para no Terceiro Círculo — a alma deles encosta no Dragão.",
         "Ascension has a price: facing your own Shadow. You did. Most natives stop at the Third Circle — their souls press up against the Dragon."),
        ("A sua não encosta em nada. Você veio de um mundo sem mana: um vaso vazio. Não tem teto.",
         "Yours presses against nothing. You came from a world without mana: an empty vessel. There is no ceiling."),
    ]),
    "a2_goblin": ("As Ruínas de Velmora", "The Ruins of Velmora", None, None, [
        ("A Horda vive nas ruínas de Velmora, o velho império. O trono do Rei Goblin era um pedaço de muralha imperial — e ele nem sabia em cima de quê estava sentado.",
         "The Horde lives in the ruins of Velmora, the old empire. The Goblin King's throne was a chunk of imperial wall — and he never knew what he was sitting on."),
    ]),
    "a2_watcher": ("Alguém Está Contando", "Someone Is Counting", None, None, [
        ("...Três. Continue.", "...Three. Go on."),
        ("Você sentiu isso? Alguém contou os seus Círculos. Não fui eu... e eu não gostei da voz.",
         "Did you feel that? Someone counted your Circles. It wasn't me... and I didn't like that voice."),
    ]),
    "a2_seventh": ("O Sétimo Nome", "The Seventh Name", None, None, [
        ("O Arquimago foi expulso da Academia por estudar o Chamado — o rito que te trouxe. Dizem que ele achou um sétimo nome nos papéis dos Seis.",
         "The Archmage was expelled from the Academy for studying the Call — the rite that brought you here. They say he found a seventh name in the papers of the Six."),
        ("Eu devia saber de quem. É estranho... tem um buraco bem aí na minha memória.",
         "I should know whose. It's strange... there's a hole in my memory right there."),
    ]),
    # ---------------------------------------------------------------- Ato III
    "a3_rupture": ("Ato III — As Sobras da Ruptura", "Act III — What the Rupture Left",
                   "Há mil anos, o mundo rachou numa única noite. Muita coisa que devia ter morrido não morreu.",
                   "A thousand years ago the world cracked in a single night. Much that should have died did not.", [
                       ("Chamam de Grande Ruptura. O império de Velmora caiu, o Abismo afundou, o Santuário subiu. O que você vai enfrentar agora é o que sobrou daquela noite.",
                        "They call it the Great Rupture. The empire of Velmora fell, the Abyss sank, the Sanctuary rose. What you face now is what was left of that night."),
                   ]),
    "a3_lich": ("Fora do Ciclo", "Outside the Cycle", None, None, [
        ("O Lich era um necromante que não soube parar. Mexeu tanto no ciclo das almas que acabou do lado de fora dele. Como você — só que por escolha.",
         "The Lich was a necromancer who didn't know when to stop. He meddled with the cycle of souls until he ended up outside it. Like you — only by choice."),
    ]),
    "a3_colossus": ("O Último Guarda", "The Last Guard", None, None, [
        ("O Colosso ainda guardava os portões de uma cidade que não existe mais. Ninguém avisou a ele que o império acabou. Mil anos de plantão.",
         "The Colossus was still guarding the gates of a city that no longer exists. Nobody told it the empire was over. A thousand years on watch."),
    ]),
    "a3_hydra": ("O que a Canção Apaga", "What the Song Erases", None, None, [
        ("A canção apagava memórias... Engraçado. Tem coisas que eu também não lembro — e eu nunca ouvi canção nenhuma.",
         "The song erased memories... Funny. There are things I don't remember either — and I never heard any song."),
    ]),
    # ---------------------------------------------------------------- Ato IV
    "a4_plane": ("Ato IV — As Mãos do Dragão", "Act IV — The Dragon's Hands",
                 "Acima do céu, quatro guardiões esperam. São mais velhos que os sábios — e lembram de tudo.",
                 "Above the sky, four guardians wait. They are older than the sages — and they remember everything.", [
                     ("Ignis, Maré, o Golem e Zéfiro são as mãos do Dragão que não foram seladas com ele. Não são maus. São leais. Dá no mesmo, quando você está na frente.",
                      "Ignis, Tide, the Golem and Zephyr are the hands of the Dragon that were not sealed with it. They aren't evil. They're loyal. Same thing, when you're standing in front of one."),
                 ]),
    "a4_guardian": ("O que Veio de Longe", "The One from Afar", None, None, [
        ("\"O que veio de longe... outra vez. O último não voltou do Crepúsculo.\"",
         "\"The one from afar... again. The last did not return from the Twilight.\""),
        ("O último? ...Eu não sei do que ele está falando. Juro. Vamos em frente.",
         "The last one? ...I don't know what it's talking about. I swear. Let's keep going."),
    ]),
    "a4_fusion": ("Heresia", "Heresy", None, None, [
        ("Fusão é dois pedaços da essência se reencontrando. Os magos antigos chamavam isso de heresia. Eu chamo de saudade.",
         "A fusion is two pieces of the essence finding each other again. The old mages called it heresy. I call it missing someone."),
    ]),
    # ---------------------------------------------------------------- Ato V
    "a5_war": ("Ato V — Luz e Sombra", "Act V — Light and Shadow",
               "A Ruptura separou a Luz da Sombra. Faz mil anos que as duas metades brigam para saber quem tem razão.",
               "The Rupture split Light from Shadow. For a thousand years the two halves have fought over who is right.", [
                   ("O Soberano do Abismo e o Serafim vão te oferecer exatamente a mesma coisa: \"encerre a guerra\". Cada um do seu lado, claro.",
                    "The Abyss Sovereign and the Seraph will offer you exactly the same thing: \"end the war\". Each from their own side, of course."),
               ]),
    "a5_sovereign": ("O Primeiro Pacto", "The First Pact", None, None, [
        ("Foi o Soberano quem assinou o primeiro pacto, na noite da Ruptura. Quem assinou do outro lado da mesa virou o primeiro demônio.",
         "It was the Sovereign who signed the first pact, on the night of the Rupture. Whoever signed across the table became the first demon."),
        ("Hoje o chamam de Rei Demônio. Ele ergueu uma cidadela no Nether e, dizem, ainda fala como um imperador.",
         "Today they call him the Demon King. He raised a citadel in the Nether and, they say, still talks like an emperor."),
    ]),
    "a5_balance": ("O Equilíbrio Quebrado", "The Broken Balance", None, None, [
        ("Caíram os dois. Era a briga deles que mantinha o Crepúsculo dormindo — Luz de um lado, Sombra do outro, e a fenda fechada no meio.",
         "Both have fallen. It was their feud that kept the Twilight asleep — Light on one side, Shadow on the other, and the rift shut between them."),
        ("Eu devia ter te contado antes. Desculpa. Eu só lembrei agora.",
         "I should have told you sooner. I'm sorry. I only remembered just now."),
    ]),
    "a5_demon": ("Ainda É Minha", "Still Mine", None, None, [
        ("\"Esta cidadela ainda é minha\"... Ele não estava falando da cidadela. Existe uma coleção de Páginas Perdidas só com as confissões dele. Vale a leitura.",
         "\"This citadel is still mine\"... He wasn't talking about the citadel. There is a whole collection of Lost Pages with his confessions. Worth the read."),
        ("Os aldorianos desconfiam de demônios mais que qualquer outro povo. Tem um motivo de família nisso.",
         "Aldorians distrust demons more than any other people. There's a family reason behind it."),
    ]),
    # ---------------------------------------------------------------- Ato VI
    "a6_called": ("O Arauto Chama", "The Herald Calls",
                  "Nove Círculos. Um vaso cheio. Do outro lado da Fenda, alguém diz o seu nome.",
                  "Nine Circles. A full vessel. On the far side of the Rift, someone speaks your name.", [
                      ("\"Mil anos. Eu só precisava de um que chegasse inteiro.\"",
                       "\"A thousand years. I only needed one who arrived whole.\""),
                      ("Essa voz... eu conheço. Conheço muito. Por que eu não consigo lembrar de quem é?",
                       "That voice... I know it. I know it well. Why can't I remember whose it is?"),
                      ("Você não é obrigado a atender. Mas ele vai continuar chamando.",
                       "You don't have to answer. But he will keep calling."),
                  ]),
    "a6_rift": ("O Primeiro Invocado", "The First Summoned", None, None, [
        ("O Chamado que te trouxe... foi ele quem usou primeiro. A primeira alma que atendeu, ele devorou, para ficar com a força dela. Foi isso que rachou o mundo.",
         "The Call that brought you here... he was the first to use it. The first soul that answered, he devoured, to keep its strength. That is what cracked the world."),
        ("Você não precisa perdoar ninguém aqui dentro. Só precisa sair inteiro.",
         "You don't have to forgive anyone in here. You only have to walk out whole."),
    ]),
    "a6_herald": ("Rendam o Meu Posto", "Relieve My Post",
                  "\"Então... rendam o meu posto.\" O Arauto caiu. E a Fenda não fechou.",
                  "\"Then... relieve my post.\" The Herald has fallen. And the Rift did not close.", [
                      ("Render o posto? Ele estava de guarda. Esse tempo todo... Eu acho que a gente esqueceu alguém lá dentro.",
                       "Relieve his post? He was standing guard. All this time... I think we forgot someone in there."),
                      ("Olha para cima: a segunda lua está rachando. Isso não devia acontecer com uma lua.",
                       "Look up: the second moon is cracking. That shouldn't happen to a moon."),
                  ]),
    "a6_hollow": ("As Almas com Fome", "The Hungry Souls", None, None, [
        ("O Mundo Oco é para onde vão as almas que perderam o coração. O ciclo das almas quebrou na Ruptura; desde então, só sobrou a fome.",
         "The Hollow World is where souls go when they lose their hearts. The cycle of souls broke in the Rupture; since then, only the hunger is left."),
    ]),
    # ---------------------------------------------------------------- Ato VII
    "a7_dragon": ("Ato VII — A Origem", "Act VII — The Origin",
                  "Antes de os elementos terem nome, existia um só. Ele dormiu por mil anos. Agora acordou.",
                  "Before the elements had names, there was only one. It slept for a thousand years. Now it is awake.", [
                      ("Nada nascido da mana dele consegue feri-lo de verdade. É por isso que o Chamado puxa gente de fora — e por isso puxou vários de uma vez.",
                       "Nothing born of its mana can truly wound it. That is why the Call pulls people from outside — and why it pulled several at once."),
                      ("Ele não é mau. É fome e sono. Não tente odiá-lo: só faça ele dormir.",
                       "It isn't evil. It is hunger and sleep. Don't try to hate it: just put it back to sleep."),
                  ]),
    "a7_after": ("Ele Volta", "It Comes Back", None, None, [
        ("Ele volta. Sempre volta — não dá para matar a mana. Os Seis não sabiam disso.",
         "It comes back. It always comes back — you can't kill mana. The Six didn't know that."),
        ("O posto de guardião agora é de vocês, os invocados. Só que desta vez ninguém fica sozinho nele. Eu fico também.",
         "The guardian's post belongs to you now, the summoned. Only this time nobody holds it alone. I'm staying too."),
    ]),
    # ---------------------------------------------------------------- Epílogo (o Abismo Infinito)
    "e_abyss": ("As Sombras", "The Shadows", None, None, [
        ("As Sombras não são os chefes de verdade. São o que o Abismo guardou deles: cada golpe que você deu lá em cima deixou um eco aqui embaixo.",
         "The Shadows aren't the real bosses. They are what the Abyss kept of them: every blow you struck up there left an echo down here."),
        ("Eu não sei se isto tem fundo. Mas é o único lugar onde a brasa do Dragão sossega. Desça o quanto quiser — eu desço junto.",
         "I don't know if this has a bottom. But it's the only place where the Dragon's ember settles. Go as deep as you like — I'm coming down with you."),
    ]),
    # ---------------------------------------------------------------- Mundo aberto
    "x_lore": ("Páginas Perdidas", "Lost Pages", None, None, [
        ("Uma Página Perdida! São pedaços do que eu esqueci. Feche uma coleção e a Enciclopédia abre uma Verdade Oculta — e cada página ainda marca um lugar no mapa.",
         "A Lost Page! These are pieces of what I forgot. Complete a collection and the Encyclopedia opens a Hidden Truth — and each page marks a place on the map, too."),
    ]),
    "x_truth": ("Uma Verdade Oculta", "A Hidden Truth", None, None, [
        ("Então era isso... Obrigado por me lembrar. Mesmo doendo um pouco.",
         "So that's what it was... Thank you for reminding me. Even if it stings a little."),
    ]),
    "x_city": ("Uma Cidade Sua", "A City of Your Own", None, None, [
        ("Uma cidade sua! Aldória também começou assim: gente teimosa, um punhado de sobreviventes e um campo vazio.",
         "A city of your own! Aldoria began like this too: stubborn people, a handful of survivors and an empty field."),
        ("Cuide da Felicidade do povo. Gente feliz constrói mais rápido, atrai vizinhos... e faz festival de dois dias.",
         "Look after your people's Happiness. Happy folk build faster, draw in neighbors... and throw two-day festivals."),
    ]),
    "x_towers": ("Vença ou Volte", "Win or Turn Back", None, None, [
        ("\"Vença ou volte.\"", "\"Win or turn back.\""),
        ("Está gravado em todas as torres, na mesma letra. Alguém fazia questão. As Crônicas de Velmora dizem quem — se você achar as páginas.",
         "It's carved on every tower, in the same hand. Someone insisted on it. The Chronicles of Velmora say who — if you find the pages."),
    ]),
    "x_dwarf": ("Nunca Fomos Embora", "We Never Left", None, None, [
        ("Os anões desceram nos Anos de Cinza e o mundo achou que eles tinham sumido. \"Nunca fomos embora\", eles dizem. \"Só fechamos a porta.\"",
         "The dwarves went below in the Ashen Years and the world thought they had vanished. \"We never left,\" they say. \"We just shut the door.\""),
    ]),
    "x_depths": ("Mana na Pedra", "Mana in the Stone", None, None, [
        ("Quanto mais fundo, mais mana na pedra. O Verme-Rei come geodos de mana há tanto tempo que os cristais cresceram na carapaça dele.",
         "The deeper you go, the more mana in the stone. The Worm King has eaten mana geodes for so long that crystals grew on its shell."),
    ]),
    "x_library": ("O Céu de Fora", "The Sky Outside", None, None, [
        ("O End é o que sobrou do lado de fora quando o mundo rachou. Esta biblioteca guarda a gramática do espaço — e o Devorador de Estrelas come o que resta do céu lá fora.",
         "The End is what was left outside when the world cracked. This library keeps the grammar of space — and the Star Devourer eats what remains of the sky out there."),
    ]),
}
for bid, (tpt, ten, npt, nen, lines) in BEATS.items():
    t(f"story.isekai_mahou.{bid}.title", tpt, ten)
    if npt is not None:
        t(f"story.isekai_mahou.{bid}.narration", npt, nen)
    for i, (lpt, len_) in enumerate(lines, 1):
        t(f"story.isekai_mahou.{bid}.{i}", lpt, len_)

# ======================================================================================================================
#  Onde Conseguir — grupos e assuntos: id -> (nome, onde, como) em pt e en
# ======================================================================================================================
for gid, pt, en in [
    ("city", "Sua Cidade", "Your City"),
    ("town", "Nas Cidades dos Reinos", "In the Kingdoms' Cities"),
    ("power", "Poder e Progresso", "Power and Progress"),
    ("combat", "Combate", "Combat"),
    ("world", "Mundo", "World"),
]:
    t(f"guide.isekai_mahou.group.{gid}", pt, en)

TOPICS = {
    "city_found": (
        ("Fundar a sua cidade",
         "Carta de Fundação: o Escriba da cidade inicial vende uma por dia; ou, na bancada, livro e pena no meio, um lingote de ouro de cada lado e um papel em cima e outro embaixo. Para a cidade no céu, o Núcleo Celeste: o Alquimista vende, ou 2 penas, 2 fragmentos de ametista e 1 Cristal de Mana.",
         "Segure a Carta: um holograma mostra no terreno a praça, os 24 primeiros lotes e a muralha (verde = bom, amarelo = desnível, azul = água, vermelho = não dá). Botão direito fixa; as setas movem (Ctrl = 4 blocos) e R gira; botão direito de novo (ou Enter) abre a tela de estilo e nome. A Carta só é gasta quando a cidade é fundada de verdade."),
        ("Founding your city",
         "Founding Charter: the Scribe in the starting city sells one a day; or, at the crafting table, book and quill in the middle, a gold ingot on each side and a paper above and below. For a sky city, the Sky Core: the Alchemist sells it, or 2 feathers, 2 amethyst shards and 1 Mana Crystal.",
         "Hold the Charter: a hologram shows the plaza, the first 24 plots and the wall on the ground (green = good, yellow = slope, blue = water, red = not here). Right-click pins it; arrows move it (Ctrl = 4 blocks) and R rotates; right-click again (or Enter) opens the style and name screen. The Charter is only spent when the city is really founded.")),
    "city_build": (
        ("Construtores e obras",
         "Painel da cidade (clique no Coração da Cidade) → abas Planejar e Obras.",
         "Os construtores erguem tudo sozinhos, sem gastar recursos. O limite deles é 2 + o nível do centro, +2 por nível da Casa dos Construtores e +1 por Oficina (até 24). Cada construtor toca uma obra; os que sobram ajudam (+50% de velocidade cada). \"Posição livre…\" deixa escolher o lugar e o giro do prédio, e um prédio pronto pode ser movido pagando moedas do tesouro."),
        ("Builders and works",
         "City panel (click the City Heart) → Plan and Works tabs.",
         "Builders raise everything by themselves, at no resource cost. Their cap is 2 + the center's level, +2 per Builders' Hall level and +1 per Workshop (up to 24). Each builder runs one work; spare ones help (+50% speed each). \"Free position…\" lets you pick a building's spot and facing, and a finished building can be moved for coins from the treasury.")),
    "city_happiness": (
        ("Felicidade do povo",
         "Painel da cidade → aba Felicidade: mostra o humor, cada motivo do dia (+ ou −) e o que mais pesa.",
         "Comida, combustível, moradia, segurança, lazer e imposto baixo sobem a Felicidade. Com 70 ou mais a cidade produz mais, constrói de 10 a 20% mais rápido, recebe o dobro de imigrantes e ganha uma caravana extra. Ela nunca cai mais de 10 por dia; abaixo de 10 por 3 dias seguidos o povo se revolta — com aviso a cada dia."),
        ("The people's Happiness",
         "City panel → Happiness tab: it shows the mood, each of today's reasons (+ or −) and what weighs the most.",
         "Food, fuel, housing, security, leisure and low taxes raise Happiness. At 70 or more the city produces more, builds 10–20% faster, gets double immigration and one extra caravan. It never drops more than 10 a day; below 10 for 3 days in a row the people revolt — with a warning each day.")),
    "city_festival": (
        ("Festival",
         "Painel da cidade: o Senhor decreta o Festival. O banquete pede comida no celeiro.",
         "Dura 2 dias de jogo, com enfeites nas ruas e fogos à noite. Dá +8 de Felicidade no começo e +4 por dia, impostos +30% e caravanas +20%. Um novo festival pode ser decretado 5 dias depois do começo do último."),
        ("Festival",
         "City panel: the Lord decrees the Festival. The feast needs food in the granary.",
         "It lasts 2 game days, with street decorations and fireworks at night. It gives +8 Happiness at the start and +4 per day, +30% taxes and +20% caravans. A new festival can be decreed 5 days after the last one began.")),
    "city_caravans": (
        ("Caravanas e recursos",
         "Painel da cidade → abas Caravanas e Recursos. As caravanas pedem um Mercado construído.",
         "A caravana vai a uma das 10 cidades ou vilas mais próximas (até de outros jogadores) vender ou comprar comida e combustível, ou trocar mercadorias. Longe paga mais; ir várias vezes no mesmo dia à mesma cidade derruba o preço. Mina, serraria, fazenda e porto mandam produtos todo dia para o armazém, e \"Coletar tudo\" pega impostos e produtos de qualquer lugar dentro da cidade."),
        ("Caravans and resources",
         "City panel → Caravans and Resources tabs. Caravans need a built Market.",
         "A caravan goes to one of the 10 nearest cities or villages (even other players') to sell or buy food and fuel, or to trade goods. Farther pays more; repeated same-day trips to one city flood its market. Mine, sawmill, farm and port send goods to the storehouse every day, and \"Collect all\" takes taxes and goods from anywhere inside the city.")),
    "city_defense": (
        ("Defesa da cidade",
         "Painel da cidade → aba Defesa.",
         "Nos cercos, as Torres de Vigia, as torres da muralha e a Torre de Magia atiram na horda; o Campo de Armadilhas fere e atrasa; o Quartel fortalece guardas e milícia. Com uma Torre de Vigia, batedores avisam um dia antes."),
        ("City defense",
         "City panel → Defense tab.",
         "During sieges the Watchtowers, the wall towers and the Magic Tower shoot the horde; the Trap Field hurts and slows it; the Barracks strengthen guards and militia. With a Watchtower, scouts warn you a day ahead.")),
    "city_magic": (
        ("Magia na cidade",
         "No seu Grimório e nos prédios arcanos da cidade.",
         "Ponha o efeito Impulso de Obra numa magia e conjure perto de uma obra: os construtores trabalham mais rápido. Na Barreira Arcana, clique no catalisador com um Pergaminho de Magia para gravar a defesa que ela lança nos invasores. Nenhuma magia sua estraga a cidade."),
        ("Magic in the city",
         "In your Grimoire and in the city's arcane buildings.",
         "Put the Work Boost effect in a spell and cast it near a work site: the builders work faster. At the Arcane Barrier, click the catalyst with a Spell Scroll to record the defense it casts on invaders. None of your spells damage the city.")),
    "home_key": (
        ("Chave do Lar",
         "Com o Estalajadeiro de qualquer cidade dos reinos: 128 Moedas da Guilda.",
         "Use a chave para ganhar a sua ilha: casa mobiliada, horta, lago e espaço para construir. Depois, a tecla Home (ou menu B → Guia de Viagem → Lar) leva e traz — só com 2 minutos fora de combate. Lá ninguém toma dano, e dá para convidar amigos pela aba."),
        ("Home Key",
         "From the Innkeeper of any kingdom city: 128 Guild Coins.",
         "Use the key to get your island: a furnished house, a farm, a pond and room to build. After that, the Home key (or B menu → Travel Guide → Home) takes you there and back — only after 2 minutes out of combat. Nobody takes damage there, and you can invite friends from the tab.")),
    "inn": (
        ("Estalagem",
         "Com o Estalajadeiro, na estalagem de cada cidade.",
         "Descansar (5 moedas) enche vida e mana e dá Bem Descansado por 10 min. Um quarto por 3 ou 7 dias (14 ou 28 moedas) vira o seu ponto de renascer. Rumores pagos apontam covis de chefe (8) e masmorras ou torres (5), e só cobram se acharem algo. Ele também vende Pergaminho de Retorno e Incenso do Vínculo."),
        ("Inn",
         "With the Innkeeper, at the inn of every city.",
         "Resting (5 coins) refills health and mana and grants Well Rested for 10 min. A room for 3 or 7 days (14 or 28 coins) becomes your respawn point. Paid rumors point to boss lairs (8) and dungeons or towers (5), and only charge if something is found. He also sells the Scroll of Return and Bond Incense.")),
    "altars": (
        ("Altares do Recomeço e da Reencarnação",
         "Nas capitais dos reinos, em lados opostos da praça. Também há santuários raros pelo mundo (viram marcador no mapa), e os dois altares têm receita.",
         "Recomeço: devolve todos os pontos de talento e desfaz a especialização por 12 Cristais de Mana. Reencarnação: trocar de raça custa 32 Cristais + 1 Núcleo de Guardião; de classe, 64 + 1 Núcleo. Nível, Círculo e elementos ficam; o que era só da classe antiga se perde."),
        ("Altars of New Beginnings and Reincarnation",
         "In the kingdoms' capitals, on opposite sides of the plaza. There are also rare shrines in the world (they become map markers), and both altars have recipes.",
         "New Beginnings: refunds every talent point and undoes the specialization for 12 Mana Crystals. Reincarnation: changing race costs 32 Crystals + 1 Guardian Core; changing class, 64 + 1 Core. Level, Circle and elements stay; whatever belonged only to the old class is lost.")),
    "market_board": (
        ("Mercado entre jogadores",
         "Quadro do Mercado, na praça de toda cidade; agachar + clique num Quadro de Missões também abre. A partir do nível 5: menu B → Mercado.",
         "Venda e compre com Moedas da Guilda, mesmo com o vendedor fora do jogo. A taxa é de 5% na venda. Cada jogador tem até 10 anúncios; eles vencem em 7 dias reais e o item volta para a sua caixa de coleta."),
        ("Player market",
         "Market Board, in every city's plaza; sneak + click on a Quest Board opens it too. From level 5: B menu → Market.",
         "Sell and buy with Guild Coins, even while the seller is offline. The fee is 5% on a sale. Each player has up to 10 listings; they expire after 7 real days and the item returns to your collection box.")),
    "renown_board": (
        ("Quadro de Renome",
         "Ao lado do Quadro de Missões das cidades, ou menu B → Renome.",
         "Mostra o Ranking (nível, chefes, andar do Abismo, torres, cidade mais rica e mais populosa), os Títulos para usar sobre o nome e os Recordes de tempo contra cada chefe."),
        ("Renown Board",
         "Beside the Quest Board in cities, or B menu → Renown.",
         "It shows the Ranking (level, bosses, Abyss floor, towers, richest and most populous city), the Titles to wear above your name and the time Records against each boss.")),
    "mounts": (
        ("Montarias",
         "Dragão Terrestre: rédeas na loja da Guilda (rank E, 45 moedas) ou com o Estalajadeiro (reputação Amigável); também dá para domar um selvagem na savana ou na planície com carne. Pesadelo: 1 chance em 6 a cada chefe (garantido no 6º). Cervo da Aurora: presente ao chegar a Honrado com um dos reinos.",
         "As Rédeas chamam a montaria já domada e selada; se ela morrer, as Rédeas voltam para você. A tecla R usa a habilidade da montaria."),
        ("Mounts",
         "Land Drake: reins at the Guild shop (rank E, 45 coins) or from the Innkeeper (Friendly reputation); you can also tame a wild one in savanna or plains with meat. Nightmare: 1 in 6 chance per boss (guaranteed on the 6th). Aurora Stag: a gift on reaching Honored with one of the kingdoms.",
         "The Reins call the mount already tamed and saddled; if it dies, the Reins come back to you. The R key uses the mount's ability.")),
    "vehicles": (
        ("Veículos",
         "Na bancada de trabalho (veja no livro de receitas): Vassoura Arcana, Tapete Voador, Barco Arcano, Carroça-Golem e Dirigível Arcano.",
         "Andam com a sua mana (sempre deixam 25%) e depois com o tanque do veículo — carregue clicando com um Cristal de Mana. Agachar + bater guarda o veículo de volta como item, com a carga."),
        ("Vehicles",
         "At the crafting table (see the recipe book): Arcane Broom, Flying Carpet, Arcane Boat, Golem Cart and Arcane Airship.",
         "They run on your mana (always leaving 25%) and then on the vehicle's tank — charge it by clicking with a Mana Crystal. Sneak + hit packs the vehicle back into an item, keeping its charge.")),
    "talents": (
        ("Talentos e especialização",
         "Menu B → Talentos (aparece no nível 2).",
         "Você ganha 1 ponto a cada 2 níveis. São 3 ramos por classe; cada nó pede um nível mínimo e 3 pontos no nó de cima. No Círculo 5 você escolhe 1 de 2 especializações. Para refazer tudo: Altar do Recomeço."),
        ("Talents and specialization",
         "B menu → Talents (appears at level 2).",
         "You earn 1 point every 2 levels. Each class has 3 branches; each node needs a minimum level and 3 points in the node above. At Circle 5 you pick 1 of 2 specializations. To redo it all: Altar of New Beginnings.")),
    "glyph_mastery": (
        ("Maestria de Glifos e Círculo da magia",
         "Só usando: cada glifo de uma magia ganha experiência quando o efeito acerta de verdade (inimigo vale mais; conjurar no ar não vale nada). O nível aparece na aba Glifos e no Grimório.",
         "A maestria vai de I a X. Cada nível tira 2% do custo de mana daquele glifo e, nos glifos de efeito, dá +1,5% de poder. Com maestria alta o círculo mágico da conjuração fica mais rico. E cada magia pode ter o seu próprio Círculo: no Grimório, os três botões depois da barra de cor escolhem forma, símbolo e cor."),
        ("Glyph Mastery and the spell's Circle",
         "Only by using them: every glyph in a spell gains experience when the effect really lands (enemies are worth more; casting into the air is worth nothing). The level shows in the Glyphs tab and in the Grimoire.",
         "Mastery goes from I to X. Each level takes 2% off that glyph's mana cost and, for effect glyphs, adds +1.5% power. With high mastery the casting circle gets richer. And every spell can have its own Circle: in the Grimoire, the three buttons after the color bar pick shape, symbol and color.")),
    "transformation": (
        ("Transformação (a sua forma)",
         "A forma Transformação vem do caminho Arcano, no Círculo 5. Monte a receita no Grimório, como um Domínio: Transformação + efeitos + leis.",
         "Escolha a receita no menu B → Personagem → Votos, em \"Receita montada\" (clique troca, botão direito tira). Ela entra por cima da sua forma racial quando você se transforma: os efeitos de ataque saem nos seus golpes e os de apoio pulsam em você."),
        ("Transformation (your own form)",
         "The Transformation form comes from the Arcane path, at Circle 5. Build the recipe in the Grimoire, like a Domain: Transformation + effects + laws.",
         "Pick the recipe in B menu → Character → Vows, under \"Built recipe\" (click cycles, right-click removes). It layers on top of your racial form when you transform: attack effects ride on your hits and support effects pulse on you.")),
    "summons": (
        ("Invocações moldadas pela receita",
         "No Grimório: a forma Invocação + um glifo de invocação (salamandra, ondina, golem, sílfide, fada, sombra ou ent) + efeitos.",
         "A criatura nasce no lugar do espírito e usa os efeitos da receita nos golpes dela. Dividir = quantas, Expandir e Concentrar = tamanho, Estender = duração, Amplificar = força. Ordens (seguir, guardar um ponto, caçar o alvo, ficar): tecla O, agachar + clique na invocação, ou os glifos de Ordem."),
        ("Summons shaped by the recipe",
         "In the Grimoire: the Conjuration form + a summon glyph (salamander, undine, golem, sylph, fairy, shadow or ent) + effects.",
         "The creature spawns instead of the spirit and uses the recipe's effects on its hits. Split = how many, Expand and Concentrate = size, Extend = duration, Amplify = strength. Orders (follow, guard a point, hunt the target, stay): the O key, sneak + click on the summon, or the Order glyphs.")),
    "familiar": (
        ("Familiar",
         "Incenso do Vínculo: com o Estalajadeiro (14 moedas) ou na bancada (essência arcana + cristal de mana + pena + osso + favo de mel).",
         "A partir do nível 10, segure o incenso por 4 s perto de uma fogueira acesa. O Familiar toma a forma do seu jeito de jogar e muda com você. Ele não luta: ajuda (busca itens, avisa de perigo, cura de leve), carrega uma bolsa e pode aprender uma magia de apoio sua. Cresce no nível 30 e no 75."),
        ("Familiar",
         "Bond Incense: from the Innkeeper (14 coins) or at the crafting table (arcane essence + mana crystal + feather + bone + honeycomb).",
         "From level 10, hold the incense for 4 s near a lit campfire. The Familiar takes the shape of how you play and changes with you. It doesn't fight: it helps (fetches items, warns of danger, heals a little), carries a bag and can learn one of your support spells. It grows at level 30 and 75.")),
    "gems": (
        ("Gemas",
         "Minerando minérios (chance pequena), em chefes (1 gema lapidada), raramente em monstros, e com o Joalheiro das cidades (gemas lascadas com reputação Amigável; o Cinzel custa 16 moedas). Três gemas iguais na bancada viram a do próximo corte.",
         "Só Ferreiro, Cavaleiro Rúnico e Encantador engastam: clique com a gema sobre o item no inventário (o cinzel tira a última). Armas têm 3 engastes (níveis 10, 35 e 60) e cada peça de armadura tem 1 (nível 20)."),
        ("Gems",
         "From mining ores (small chance), bosses (1 cut gem), rarely from monsters, and from the city Jeweler (chipped gems at Friendly reputation; the Chisel costs 16 coins). Three equal gems at the crafting table become the next cut.",
         "Only the Smith, Rune Knight and Enchanter can socket: click the gem onto the item in the inventory (the chisel takes the last one out). Weapons have 3 sockets (levels 10, 35 and 60) and each armor piece has 1 (level 20).")),
    "gear": (
        ("Equipamento: conjunto, kit e vínculo",
         "Menu B → Equipamento.",
         "Quatro peças do mesmo conjunto dão um bônus. Perdeu o kit da classe? A recuperação devolve as peças que faltam, no melhor grau que você já teve, de graça a cada 40 min de jogo (ou por 4 Cristais de Mana). Matar com a arma da classe na mão sobe o Vínculo dela (1 a 10): +1,5% de dano por nível e uma habilidade no 10."),
        ("Equipment: set, kit and bond",
         "B menu → Equipment.",
         "Four pieces of the same set give a bonus. Lost your class kit? Recovery returns the missing pieces, at the best tier you have carried, free every 40 min of play (or for 4 Mana Crystals). Kills with the class weapon in hand raise its Bond (1 to 10): +1.5% damage per level and an ability at 10.")),
    "endless_abyss": (
        ("Abismo Infinito",
         "Menu B → Abismo, a partir do nível 100.",
         "Andares sem fim, cada um mais forte; a cada 5, a Sombra de um chefe que você já venceu. Rende Estilhaços do Abismo, Fragmentos de Sombra e, a cada 10 andares novos, um Núcleo do Abismo. Morrer ou sair te devolve para onde entrou, sem perder itens. Um colega de grupo de nível 100+ pode entrar na pausa entre os andares."),
        ("Endless Abyss",
         "B menu → Abyss, from level 100.",
         "Endless floors, each one stronger; every 5th brings the Shadow of a boss you have beaten. It pays Abyss Shards, Shadow Fragments and, every 10 new floors, an Abyss Core. Dying or leaving returns you to where you entered, with no item loss. A party mate of level 100+ can join during the pause between floors.")),
    "lock_guard": (
        ("Travar o alvo, bloquear e aparar",
         "Não pede item: são teclas (dá para trocar em Opções → Controles → Isekai Mahou).",
         "O botão do meio do mouse trava no inimigo mais perto da mira; a roda troca de alvo. Segurar o Mouse 4 com arma ou cajado na mão bloqueia metade do dano que vem de frente. Apertar na hora do golpe (0,3 s) APARA: o golpe é anulado e o atacante fica atordoado. Golpe muito forte quebra a guarda — esse pede aparo ou esquiva."),
        ("Lock on, block and parry",
         "No item needed: they are keys (rebind them in Options → Controls → Isekai Mahou).",
         "The middle mouse button locks onto the enemy nearest the crosshair; the wheel switches target. Holding Mouse 4 with a weapon or staff in hand blocks half the damage coming from the front. Pressing right as the hit lands (0.3 s) PARRIES: the hit is cancelled and the attacker is stunned. A very heavy hit breaks your guard — parry or dodge that one.")),
    "finisher_combo": (
        ("Golpe de misericórdia e combo de magias",
         "Em qualquer luta contra monstros comuns.",
         "Um inimigo comum abaixo de 10% de vida cambaleia: chegue perto e clique com o botão direito para o golpe de misericórdia. Conjurar uma magia até 4 s depois da outra forma um Combo: +2% de poder por magia, até +20%. Parou, zerou."),
        ("Mercy stroke and spell combo",
         "In any fight against common monsters.",
         "A common enemy below 10% health staggers: get close and right-click for the mercy stroke. Casting a spell within 4 s of the last one builds a Combo: +2% power per spell, up to +20%. Stop and it resets.")),
    "portals": (
        ("Portais e dimensões",
         "Menu B → Guia de Viagem → Lugares → \"Guia de Portais\".",
         "Para cada dimensão, mostra onde fica o portal, como abrir e o que ainda falta (Círculo, chefes). \"Rastrear o mais próximo\" põe uma seta no rastreador. Os lugares que você descobre também viram pontos no minimapa."),
        ("Portals and dimensions",
         "B menu → Travel Guide → Places → \"Portal Guide\".",
         "For each dimension it shows where the portal is, how to open it and what is still missing (Circle, bosses). \"Track the nearest\" puts an arrow on the tracker. Places you discover also become minimap points.")),
    "puzzles": (
        ("Enigmas das masmorras",
         "Um por andar da Masmorra em Camadas, e também na Cidadela Demoníaca, na Biblioteca do Vazio e no cofre da Fortaleza Anã. Procure o Selo Rúnico.",
         "São quatro tipos: alavancas que acendem lâmpadas em grupo, pisar em cada placa uma única vez, levar um feixe de luz por 3 espelhos e repetir uma sequência de runas. Resolver abre as grades que prendem o baú e dá XP."),
        ("Dungeon puzzles",
         "One per floor of the Layered Dungeon, and also in the Demon Citadel, the Void Library and the Dwarf Hold's vault. Look for the Rune Seal.",
         "There are four kinds: levers that light lamps in groups, stepping on every plate exactly once, steering a light beam through 3 mirrors and repeating a rune sequence. Solving one opens the grates around the chest and gives XP.")),
    "towers": (
        ("Torres de Desafio e ilhas voadoras",
         "Torres pelo mundo (pedra, areia, gelo, floresta, submersa e flutuante). As ilhas voadoras com ruínas ficam no céu: uma Corrente Ascendente brilhante sobe até elas.",
         "Cada andar pode ter afixos (monstros Fortificados, Velozes, Ardentes...): passe o mouse no nome deles, no chat, para ver o que fazem. O jogo guarda o seu recorde de tempo. Os rumores da estalagem apontam a torre mais próxima."),
        ("Challenge Towers and sky islands",
         "Towers across the world (stone, sand, frost, forest, sunken and floating). Sky islands with ruins hang in the sky: a glowing Updraft rises to them.",
         "Each floor may have affixes (Fortified, Swift, Burning monsters...): hover their names in chat to see what they do. The game keeps your best time. Inn rumors point to the nearest tower.")),
    "magic_biomes": (
        ("Biomas mágicos",
         "Floresta de Cristal: partes de florestas frias e taigas. Pântano Assombrado: o lado mais úmido dos pântanos. Bosque Crepuscular: o coração das florestas escuras. Caverna de Cristal Mágico: debaixo de terras muito secas. Ermo de Cristal Ígneo: Nether. Jardins Astrais: ilhas externas do End.",
         "Só nascem em terreno novo (áreas ainda não exploradas). A Floresta de Cristal e a Caverna de Cristal Mágico dão +15% de regeneração de mana enquanto você está dentro."),
        ("Magic biomes",
         "Crystal Forest: parts of cold forests and taigas. Haunted Swamp: the wetter side of swamps. Twilight Grove: the heart of dark forests. Magic Crystal Cavern: beneath very dry land. Embercrystal Wastes: Nether. Astral Gardens: the outer End islands.",
         "They only generate in new terrain (areas not explored yet). The Crystal Forest and the Magic Crystal Cavern give +15% mana regeneration while you are inside.")),
    "lore_pages": (
        ("Páginas Perdidas e a Enciclopédia",
         "Páginas Perdidas: em baús (do mod e do jogo), nas Ruínas de Velmora e em tesouros enterrados. Estelas Antigas: pedras gravadas dentro das ruínas.",
         "Ler uma página revela um Fragmento e marca no rastreador o lugar de que ele fala (estrutura, covil de chefe ou tesouro), com as coordenadas. Fechar uma coleção libera uma Verdade Oculta na Enciclopédia (menu B → Guia de Viagem → Enciclopédia)."),
        ("Lost Pages and the Encyclopedia",
         "Lost Pages: in chests (the mod's and the game's), in the Ruins of Velmora and in buried treasure. Ancient Steles: carved stones inside the ruins.",
         "Reading a page reveals a Fragment and marks on the tracker the place it speaks of (structure, boss lair or treasure), with coordinates. Completing a collection unlocks a Hidden Truth in the Encyclopedia (B menu → Travel Guide → Encyclopedia).")),
    "world_events": (
        ("Eventos do mundo",
         "Acontecem sozinhos no Mundo Normal: Queda de Meteoro, Lua de Sangue, Tempestade Arcana, Terremoto e Tempestade de Mana.",
         "O meteoro deixa uma cratera com Minério Estelar e traz invasores (o Arauto Estelar é o minichefe); o Ferreiro das cidades compra Metal Estelar. A Lua de Sangue traz mais monstros e mais XP. As tempestades deixam as magias mais fortes. Nenhum evento estraga cidades."),
        ("World events",
         "They happen on their own in the Overworld: Meteor Fall, Blood Moon, Arcane Storm, Earthquake and Mana Storm.",
         "The meteor leaves a crater with Star Ore and brings invaders (the Star Herald is the mini-boss); the city Blacksmith buys Star Metal. The Blood Moon brings more monsters and more XP. The storms make spells stronger. No event ever damages cities.")),
}
for tid, ((npt, wpt, hpt), (nen, wen, hen)) in TOPICS.items():
    t(f"guide.isekai_mahou.{tid}", npt, nen)
    t(f"guide.isekai_mahou.{tid}.where", wpt, wen)
    t(f"guide.isekai_mahou.{tid}.how", hpt, hen)

# ======================================================================================================================
#  "No caminho": o que cada marco da história abre de novo (linha extra do passo no Diário) — "capitulo.passo" -> (pt, en)
# ======================================================================================================================
EXTRAS = {
    "awakening.first_spell": (
        "cada glifo ganha Maestria com o uso (de I a X): gasta menos mana e bate mais forte. Conjurar no ar não conta.",
        "every glyph gains Mastery with use (I to X): it costs less mana and hits harder. Casting into the air doesn't count."),
    "awakening.slime_hunt": (
        "o botão do meio do mouse trava no alvo; segurar o Mouse 4 bloqueia e, apertado na hora do golpe, apara. Inimigo quase morto cambaleia: botão direito de perto dá o golpe de misericórdia.",
        "the middle mouse button locks onto a target; holding Mouse 4 blocks and, pressed right as the hit lands, parries. A nearly dead enemy staggers: right-click up close for the mercy stroke."),
    "guild_and_trials.reach_city": (
        "na cidade, o Estalajadeiro vende a Chave do Lar (128 moedas), a praça tem o Quadro do Mercado e o Quadro de Renome fica ao lado do de Missões. Nas capitais, a praça tem os Altares do Recomeço e da Reencarnação.",
        "in the city, the Innkeeper sells the Home Key (128 coins), the plaza has the Market Board and the Renown Board stands beside the Quest Board. In capitals, the plaza has the Altars of New Beginnings and Reincarnation."),
    "guild_and_trials.level_5": (
        "no nível 2 a aba Talentos abre no menu B (1 ponto a cada 2 níveis) e, no 5, o Mercado entre jogadores.",
        "at level 2 the Talents tab opens in the B menu (1 point every 2 levels) and, at 5, the player Market."),
    "guild_and_trials.level_10": (
        "no nível 10 já dá para fazer o ritual do Familiar: Incenso do Vínculo (Estalajadeiro, 14 moedas) segurado perto de uma fogueira acesa.",
        "at level 10 you can perform the Familiar ritual: Bond Incense (Innkeeper, 14 coins) held near a lit campfire."),
    "six_shrines.shrine_trial": (
        "ruínas pequenas pelo mundo guardam Estelas Antigas e Páginas Perdidas — ler uma marca um lugar no mapa e enche a Enciclopédia.",
        "small ruins across the world hold Ancient Steles and Lost Pages — reading one marks a place on the map and fills the Encyclopedia."),
    "six_shrines.third_circle": (
        "no Círculo 3 a forma Invocação aceita os glifos de invocação e de Ordem: a criatura nasce moldada pela sua receita (tecla O dá as ordens).",
        "at Circle 3 the Conjuration form takes summon and Order glyphs: the creature is born shaped by your recipe (the O key gives the orders)."),
    "dark_masters.fifth_circle": (
        "no Círculo 5 o caminho Arcano ensina a forma Transformação — monte a receita no Grimório e escolha em Personagem → Votos — e a aba Talentos libera a especialização da classe.",
        "at Circle 5 the Arcane path teaches the Transformation form — build the recipe in the Grimoire and pick it in Character → Vows — and the Talents tab unlocks your class specialization."),
    "twilight.ninth_circle": (
        "no nível 100 o Abismo Infinito abre no menu B: andares sem fim e as Sombras dos chefes que você já venceu.",
        "at level 100 the Endless Abyss opens in the B menu: endless floors and the Shadows of bosses you have already beaten."),
    # Onda 5: o Dragão é o fim da história.
    "primordial.primordial_dragon": (
        "o Dragão é o fim da história. Depois dele só resta o Abismo Infinito (menu B → Abismo) — e o teto de nível sobe de 150 para 200.",
        "the Dragon is the end of the story. After it only the Endless Abyss remains (B menu → Abyss) — and the level ceiling rises from 150 to 200."),
}
for sid, (pt, en) in EXTRAS.items():
    t(f"journal.isekai_mahou.{sid}.extra", pt, en)

# ======================================================================================================================
#  Onda 5 — passos novos em capítulos de OUTROS módulos (o JSON do capítulo foi ajustado sem rodar o gerador do módulo;
#  o texto do passo fica aqui): "capitulo.passo" -> (título, crônica, dica, dica curta), cada um (pt, en).
#  Títulos e dicas curtas: até 26 letras (rastreador); dica: até 300 (caixa de diálogo).
# ======================================================================================================================
MOVED_STEPS = {
    # Terras Encantadas (magic_biomes) saiu de depois do Dragão para o começo do Ato II: obrigatório só achar UMA.
    "magic_biomes.explore": (
        ("Ache uma Terra Encantada", "Find an Enchanted Land"),
        ("Onde a mana vazou para a terra, até o chão brilha. Uma só já basta para entender por que ninguém ergue cidades ali.",
         "Where mana leaked into the land, even the ground glows. One is enough to understand why no one raises cities there."),
        ("Siga a bússola do diário até a Terra Encantada mais próxima: Floresta de Cristal (florestas frias), Pântano Assombrado, "
         "Bosque Crepuscular (Florestas Escuras) ou uma Caverna de Cristal. Só nascem em terreno novo: se a seta sumir, explore mais longe.",
         "Follow the journal compass to the nearest Enchanted Land: Crystal Forest (cool forests), Haunted Swamp, Twilight Grove "
         "(Dark Forests) or a Crystal Cavern. They only generate in new terrain: if the arrow vanishes, explore farther."),
        ("Qualquer bioma mágico", "Any magic biome")),
}
for sid, (title, lore, hint, tip) in MOVED_STEPS.items():
    for text in title + tip:
        assert len(text) <= 26, (sid, text)
    for text in hint:
        assert len(text) <= 300, (sid, len(text))
    t(f"journal.isekai_mahou.{sid}", *title)
    t(f"journal.isekai_mahou.{sid}.lore", *lore)
    t(f"journal.isekai_mahou.{sid}.hint", *hint)
    t(f"journal.isekai_mahou.{sid}.tip", *tip)
# O capítulo das Terras Encantadas nunca teve desfecho (a chave aparecia crua no chat ao concluir).
t("journal.isekai_mahou.magic_biomes.outro",
  "Você viu onde a mana encharca a terra — e voltou para contar. As outras Terras Encantadas continuam por aí, cada uma com os seus cristais.",
  "You have seen where mana soaks the land — and came back to tell of it. The other Enchanted Lands are still out there, each with crystals of its own.")

# ======================================================================================================================
#  Bestiário — descrições que faltavam (v1.6 e ondas 1–4)
# ======================================================================================================================
BESTIARY = {
    "false_hydra_head": ("Uma das cabeças da Falsa Hidra. Só existe enquanto ninguém olha para ela — e canta para você esquecer que olhou.",
                         "One of the False Hydra's heads. It only exists while nobody looks at it — and it sings so you forget you looked."),
    "vault_mimic": ("Baú com dentes que serve ao Rei Mímico. No cofre dele, desconfie de tudo o que parece tesouro.",
                    "A chest with teeth that serves the Mimic King. In his vault, distrust anything that looks like treasure."),
    "afterlife_reaper": ("Vem buscar as almas que demoram demais a voltar para o corpo. Não tem pressa nem raiva: só serviço.",
                         "It comes for souls that take too long to return to their bodies. No hurry, no anger: only duty."),
    "void_scribe": ("Escriba da Biblioteca do Vazio, no End. Copia a gramática do espaço há mil anos e não gosta de leitores.",
                    "A scribe of the Void Library, in the End. It has copied the grammar of space for a thousand years and dislikes readers."),
    "star_wisp": ("Fagulha de um céu que não existe mais. Ronda as estantes da Biblioteca do Vazio e explode em luz fria.",
                  "A spark from a sky that no longer exists. It drifts between the shelves of the Void Library and bursts into cold light."),
    "trial_guard": ("Soldado conjurado das Câmaras de Provação. Não é de verdade — mas bate como se fosse.",
                    "A conjured soldier of the Trial Chambers. It isn't real — but it hits as if it were."),
    "star_devourer": ("Dorme no observatório da Biblioteca do Vazio e come o que sobrou do céu de fora. Um dos chefes mais fortes do End.",
                      "It sleeps in the observatory of the Void Library and eats what is left of the sky outside. One of the strongest bosses in the End."),
    "grimmjow": ("O Sexto Espada de Las Noches: uma pantera em forma de gente. Luta por gosto e fica mais rápido quanto mais apanha.",
                 "The Sixth Espada of Las Noches: a panther shaped like a man. He fights for the joy of it and gets faster the more he is hurt."),
    "barragan": ("Deus-Rei de Hueco Mundo, a Velhice em pessoa. Tudo o que chega perto dele envelhece e vira pó.",
                 "God-King of Hueco Mundo, Old Age itself. Everything that comes near him ages and turns to dust."),
    "ulquiorra": ("O Quarto Espada: asas negras e um vazio no peito. Guarda o fundo de Las Noches e não entende o que é um coração.",
                  "The Fourth Espada: black wings and a hole in his chest. He guards the depths of Las Noches and does not understand what a heart is."),
    "trial_champion": ("O melhor das Câmaras de Provação: aparece para quem já venceu as ondas e ainda quer mais.",
                       "The best of the Trial Chambers: it appears for those who beat the waves and still want more."),
    "city_champion": ("O defensor de uma cidade jurada. Quem o vence com o Selo do Juramento se torna o Senhor dela.",
                      "The defender of a sworn city. Whoever beats him with the Oath Seal becomes its Lord."),
    "familiar_spirit": ("O seu Familiar: nasce do Incenso do Vínculo, toma a forma do seu jeito de jogar e muda com você. Não luta — ajuda, e carrega uma bolsa.",
                        "Your Familiar: born from the Bond Incense, it takes the shape of how you play and changes with you. It doesn't fight — it helps, and carries a bag."),
    "citizen": ("Gente comum das cidades dos reinos. Trabalha de dia, passa na estalagem à noite e às vezes tem um pedido para você.",
                "Ordinary folk of the kingdoms' cities. They work by day, drop by the inn at night and sometimes have a request for you."),
    "city_guard": ("Guarda das cidades dos reinos. Protege o povo, fecha o portão para a horda e não gosta de magia perto das casas.",
                   "A guard of the kingdoms' cities. Protects the people, shuts the gate on the horde and dislikes magic near the houses."),
    "city_merchant": ("Mercador das cidades: ferreiro, alquimista, joalheiro, estalajadeiro... Os preços melhoram com a sua reputação.",
                      "A city merchant: blacksmith, alchemist, jeweler, innkeeper... Prices improve with your reputation."),
    "city_lord": ("Quem manda numa cidade dos reinos. Dá as missões mais sérias — e lembra de quem ajudou.",
                  "The one who rules a kingdom city. Hands out the most serious quests — and remembers who helped."),
    "settlement_resident": ("Morador da SUA cidade. Feliz, trabalha melhor e chama os parentes; infeliz, reclama — e um dia se revolta.",
                            "A resident of YOUR city. Happy, they work better and call their relatives; unhappy, they complain — and one day revolt."),
    "settlement_builder": ("Construtor da sua cidade: ergue casas, muralhas e torres sozinho. Quanto mais construtores, mais obras ao mesmo tempo.",
                           "A builder of your city: raises houses, walls and towers alone. The more builders, the more works at once."),
    "settlement_guard": ("Guarda da sua cidade. Patrulha as ruas e segura a linha quando a horda chega.",
                         "A guard of your city. Patrols the streets and holds the line when the horde arrives."),
    "settlement_militia": ("Morador que pega em armas quando a cidade é atacada. Com um Quartel, luta bem melhor.",
                           "A resident who takes up arms when the city is attacked. With a Barracks, they fight much better."),
    "settlement_merchant": ("Mercador da sua cidade. Abre a loja no Mercado e viaja com as caravanas para as cidades vizinhas.",
                            "A merchant of your city. Opens shop at the Market and travels with the caravans to neighboring cities."),
    "dwarf_merchant": ("Comerciante da Fortaleza Anã. Troca mithril e runas por coisas da superfície — e pechincha como ninguém.",
                       "A trader of the Dwarf Hold. Swaps mithril and runes for surface goods — and haggles like nobody else."),
    "dwarf_smith": ("Ferreiro anão, herdeiro das runas de Pyra. Trabalha na Forja Rúnica, no fundo da fortaleza.",
                    "A dwarven smith, heir to Pyra's runes. Works at the Rune Forge, deep in the hold."),
    "hellfire_imp": ("Diabrete de chamas negras invocado pela fusão Infernal. Pequeno, rápido e bem mal-educado.",
                     "An imp of black flames summoned by the Infernal fusion. Small, fast and very rude."),
    "twilight_clone": ("Uma cópia sua feita de Crepúsculo: luta ao seu lado por alguns instantes e se desfaz em luz e sombra.",
                       "A copy of you made of Twilight: it fights beside you for a few moments and comes apart into light and shadow."),
    "dark_young": ("Filhote Sombrio chamado por uma Magia Lendária: uma massa de tentáculos e cascos que pisoteia tudo no caminho.",
                   "A Dark Young called by a Legendary Spell: a mass of tentacles and hooves that tramples everything in its way."),
    "kodama": ("Espírito pequeno das árvores, chamado pelas técnicas do Druida. Onde há kodamas, a floresta está saudável.",
               "A small spirit of the trees, called by the Druid's techniques. Where there are kodama, the forest is healthy."),
    "forest_god": ("O Deus da Floresta, invocado pela maior técnica do Druida. Onde ele pisa, a vida brota — e os inimigos murcham.",
                   "The Forest God, summoned by the Druid's greatest technique. Where it steps, life sprouts — and enemies wither."),
    "shadow_soldier": ("Soldado erguido da sombra de um inimigo caído. Obedece ao Necromante sem falar nem recuar.",
                       "A soldier raised from the shadow of a fallen enemy. It obeys the Necromancer without speaking or retreating."),
    "spectral_colossus": ("Gigante de mana pura erguido por um ultimate. Dura pouco — e esmaga muito.",
                          "A giant of pure mana raised by an ultimate. It lasts a short while — and crushes a great deal."),
}
for eid, (pt, en) in BESTIARY.items():
    t(f"bestiary.isekai_mahou.{eid}.desc", pt, en)


# ======================================================================================================================
#  Conferências
# ======================================================================================================================
def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def fail(msg):
    print("ERRO:", msg)
    sys.exit(1)


def check():
    # --- batidas do StoryBook
    src = read(os.path.join(JAVA, "story/StoryBook.java"))
    java_beats = {}
    for m in re.finditer(r'\b(talk|scene)\("([a-z0-9_]+)"(.*?)\);', src, re.S):
        lines = sum(int(n) for n in re.findall(r'echo\((\d+)\)', m.group(3)))
        lines += sum(int(n) for n in re.findall(r'part\("[a-z_]+",\s*(\d+)\)', m.group(3)))
        java_beats[m.group(2)] = (m.group(1) == "scene", lines)
        for sp in re.findall(r'part\("([a-z_]+)"', m.group(3)):
            if f"story.isekai_mahou.speaker.{sp}" not in PT:
                fail(f"falante sem nome: {sp} (batida {m.group(2)})")
    if not java_beats:
        fail("nenhuma batida lida de StoryBook.java")
    for bid, (scene, lines) in java_beats.items():
        if bid not in BEATS:
            fail(f"batida sem texto: {bid}")
        _, _, npt, _, texts = BEATS[bid]
        if scene and npt is None:
            fail(f"cena sem narração: {bid}")
        if len(texts) != lines:
            fail(f"batida {bid}: {lines} falas no Java, {len(texts)} no texto")
    for bid in BEATS:
        if bid not in java_beats:
            fail(f"texto de batida que não existe no Java: {bid}")
    acts = set(int(n) for n in re.findall(r'new Act\((\d+),', src))
    for n in acts:
        if f"story.isekai_mahou.act.{n}" not in PT:
            fail(f"ato sem nome: {n}")

    # --- passos citados (gatilhos STEP/capítulo do StoryBook e as linhas "No caminho") existem no livro
    steps, chapters = set(), set()
    for path in glob.glob(os.path.join(ROOT, "src/main/resources/data/isekai_mahou/journal/*.json")):
        try:
            data = json.loads(read(path))
        except ValueError:
            print("AVISO: JSON inválido (outro agente editando?):", path)
            continue
        for ch in (data["chapters"] if isinstance(data, dict) else data):
            chapters.add(ch["id"])
            for st in ch["steps"]:
                steps.add(ch["id"] + "." + st["id"])
    for sid in EXTRAS:
        if sid not in steps:
            fail(f"linha \"No caminho\" de um passo que não existe: {sid}")
    for sid in MOVED_STEPS:
        if sid not in steps:
            fail(f"texto de um passo que não existe no Diário: {sid}")
    for m in re.finditer(r'Trigger\.(STEP|CHAPTER_BEGIN|CHAPTER_DONE),\s*"([a-z0-9_.]+)"', read(os.path.join(JAVA, "story/StoryBook.java"))):
        pool = steps if m.group(1) == "STEP" else chapters
        if m.group(2) not in pool:
            fail(f"gatilho do StoryBook aponta para algo que não existe no Diário: {m.group(2)}")
    for m in re.finditer(r'\b(?:talk|scene)\("[a-z0-9_]+",\s*\d+,\s*"([a-z0-9_]*)"', read(os.path.join(JAVA, "story/StoryBook.java"))):
        if m.group(1) and m.group(1) not in chapters:
            fail(f"batida guardada num capítulo que não existe: {m.group(1)}")

    # --- assuntos do GuideTopics
    src = read(os.path.join(JAVA, "guide/GuideTopics.java"))
    topics = re.findall(r'add\("([a-z0-9_]+)",\s*"([a-z_]+)"', src)
    if not topics:
        fail("nenhum assunto lido de GuideTopics.java")
    for tid, group in topics:
        if tid not in TOPICS:
            fail(f"assunto sem texto: {tid}")
        if f"guide.isekai_mahou.group.{group}" not in PT:
            fail(f"grupo sem nome: {group}")
    for tid in TOPICS:
        if tid not in [x[0] for x in topics]:
            fail(f"texto de assunto que não existe no Java: {tid}")

    # --- outras pastas de tradução: chaves repetidas e descrições do Bestiário
    others = {"pt_br": {}, "en_us": {}}
    for lg in others:
        for path in glob.glob(os.path.join(LANG, "*", lg + ".json")):
            if os.path.basename(os.path.dirname(path)) == "story":
                continue
            try:
                others[lg].update(json.loads(read(path)))
            except ValueError:
                print("AVISO: JSON inválido (outro agente editando?):", path)
    for lg, mine in (("pt_br", PT), ("en_us", EN)):
        for k, v in list(mine.items()):
            if k in others[lg]:
                if others[lg][k] != v:
                    # Outro módulo já descreve: a dele vale (a build falharia com duas diferentes).
                    print(f"AVISO [{lg}]: {k} já existe em outra pasta — a minha foi retirada")
                del mine[k]
    src = read(os.path.join(JAVA, "bestiary/Bestiary.java"))
    block = src.split("static {", 1)[1].split("private static void put", 1)[0]
    block = re.sub(r"//[^\n]*", "", block)
    missing = []
    for eid in re.findall(r'"([a-z0-9_]+)"', block):
        k = f"bestiary.isekai_mahou.{eid}.desc"
        if k not in PT and k not in others["pt_br"]:
            missing.append(eid)
    if missing:
        fail("criaturas do catálogo sem descrição: " + ", ".join(missing))
    # --- as duas línguas com as mesmas chaves
    if set(PT) != set(EN):
        fail("pt_br e en_us com chaves diferentes: " + ", ".join(sorted(set(PT) ^ set(EN))))


def tab_icon():
    """Ícone da seção "Onde?" no Guia de Viagem: uma bússola dourada com a agulha vermelha."""
    out = hex_rgba("#1A1226")
    gold, gold_d = hex_rgba("#FFD86B"), darken(hex_rgba("#FFD86B"), 0.35)
    face, face_d = hex_rgba("#EDE4D0"), hex_rgba("#C9BDA3")
    red, blue = hex_rgba("#E0564A"), hex_rgba("#6FA8E8")
    c = Canvas(16, 16)
    c.from_rows([
        "......oooo......",
        "....ooGGGGoo....",
        "...oGGggggGGo...",
        "..oGgFFFRFFgGo..",
        "..oGFFFFRRFFGo..",
        ".oGgFFFRRFFFgGo.",
        ".oGgFFFRRFFFgGo.",
        ".oGgFFFooFFFgGo.",
        ".oGgFFFBBFFFgGo.",
        ".oGgFFFBBFFfgGo.",
        "..oGFFBBFFFfGo..",
        "..oGgFBFFffgGo..",
        "...oGGggggGGo...",
        "....ooGGGGoo....",
        "......oooo......",
        "................",
    ], {"o": out, "G": gold, "g": gold_d, "F": face, "f": face_d, "R": red, "B": blue})
    path = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui/tab/where.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    c.save(path)


def save(name, obj):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    check()
    tab_icon()
    save("pt_br.json", PT)
    save("en_us.json", EN)
    print(f"story: {len(PT)} chaves em pt_br e en_us ({len(BEATS)} batidas, {len(TOPICS)} assuntos, {len(BESTIARY)} descrições)")
