"""Gera os textos e o icone do trabalho "cities_npc" da release 1.0 (vida das cidades dos NPCs).

Uso (na raiz do projeto):  py -I tools/gen/cities_npc.py

Gera:
  - src/main/lang/cities_npc/{pt_br,en_us}.json: estalagem (descanso, quarto por dias, rumores pagos), sidequests
    (16 modelos: titulo, historia, passos, escolha e desfechos), rotina (loja fechada, taverna), cidade inicial e os
    comandos de teste. As chaves antigas continuam em src/main/lang/cities (tools/gen/cities.py) — este arquivo so
    ACRESCENTA chaves novas (chave repetida com texto diferente quebra o build).
  - textures/mob_effect/well_rested.png (efeito "Bem Descansado").
Os ids e a quantidade de passos de cada pedido precisam bater com feature/cities/CityQuests.java (conferido aqui).
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/cities_npc")
OLD_LANG = os.path.join(ROOT, "src/main/lang/cities")
NS = "isekai_mahou"
T = (0, 0, 0, 0)
PT, EN = {}, {}


def tr(key, pt, en):
    assert key not in PT, f"chave repetida: {key}"
    PT[key] = pt
    EN[key] = en


# =================================================================================================
#  Icone do efeito
# =================================================================================================
def effect_icon():
    """Bem Descansado: lua crescente e estrelas sobre um travesseiro."""
    c = Canvas(18, 18)
    c.circle(8, 7, 5.5, hex_rgba("FFE9A8"))
    c.shade_sphere(8, 7, 5.5, light=0.25, dark=0.2)
    c.circle(10.5, 5.5, 4.6, T)          # recorte que faz a lua crescente
    for (x, y) in ((13, 3), (15, 7), (12, 9)):
        c.set(x, y, hex_rgba("FFF6D0"))
    c.set(13, 2, hex_rgba("FFE9A8")); c.set(13, 4, hex_rgba("FFE9A8"))
    c.set(12, 3, hex_rgba("FFE9A8")); c.set(14, 3, hex_rgba("FFE9A8"))
    for x in range(3, 15):               # travesseiro
        c.set(x, 14, hex_rgba("EAF2FF"))
        c.set(x, 15, hex_rgba("C4D4EC"))
    for x in range(4, 14):
        c.set(x, 13, hex_rgba("F8FBFF"))
    c.set(2, 15, hex_rgba("9FB4D4")); c.set(15, 15, hex_rgba("9FB4D4"))
    c.outline(hex_rgba("2A3550"))
    c.save(os.path.join(A, "textures/mob_effect/well_rested.png"))


# =================================================================================================
#  Estalagem, rotina, cidade inicial, comandos
# =================================================================================================
def general():
    tr(f"effect.{NS}.well_rested", "Bem Descansado", "Well Rested")
    tr(f"effect.{NS}.well_rested.description", "Dormiu numa cama de verdade: +15% de regeneração de mana e cura suave.",
       "Slept in a real bed: +15% mana regeneration and gentle healing.")

    services = {
        "inn_rest": ("Descansar", "Rest",
                     "Durma agora: vida e mana cheias, tira a exaustão e dá Bem Descansado (10 min). De noite, a noite passa se ninguém mais estiver acordado. De graça para hóspedes.",
                     "Sleep now: full health and mana, clears exhaustion and grants Well Rested (10 min). At night, the night passes if nobody else is awake. Free for guests."),
        "inn_rent3": ("Alugar quarto (3 dias)", "Rent a room (3 days)",
                      "O quarto vira a sua casa: é nele que você renasce. Dormir na cama dá Bem Descansado. Alugar de novo estende o prazo.",
                      "The room becomes your home: you respawn there. Sleeping in the bed grants Well Rested. Renting again extends it."),
        "inn_rent7": ("Alugar quarto (7 dias)", "Rent a room (7 days)",
                      "Uma semana de casa garantida, pelo preço de duas estadias curtas.",
                      "A week of guaranteed home, for the price of two short stays."),
        "inn_tip_boss": ("Rumor: covil de chefe", "Rumor: a boss lair",
                         "Marca no rastreador o covil de um chefe por perto. Só paga se houver rumor.",
                         "Marks a nearby boss lair on your tracker. You only pay if there is a rumor."),
        "inn_tip_place": ("Rumor: masmorra ou torre", "Rumor: dungeon or tower",
                          "Marca no rastreador uma masmorra, torre ou passagem por perto. Só paga se houver rumor.",
                          "Marks a nearby dungeon, tower or passage on your tracker. You only pay if there is a rumor."),
        "inn_checkout": ("Encerrar o aluguel", "Check out",
                         "Devolve o quarto. Seu ponto de renascimento volta a ser o de antes.",
                         "Gives the room back. Your respawn point goes back to what it was before."),
    }
    for k, (pt, en, dpt, den) in services.items():
        tr(f"service.{NS}.cities.{k}", pt, en)
        tr(f"service.{NS}.cities.{k}.desc", dpt, den)

    lines = {
        "service.inn_rest.done": ("Cama feita e sopa quente. Pronto: você está novo em folha!",
                                  "Fresh sheets and hot soup. There: good as new!"),
        "service.inn_rest.done_night": ("Dormiu feito pedra! Já é de manhã, e você está novo em folha.",
                                        "You slept like a log! It's morning already, and you're good as new."),
        "service.inn_rent.done": ("Quarto reservado: a cama em %s %s %s é sua por %s dias. Se o pior acontecer lá fora, é aqui que você acorda.",
                                  "Room booked: the bed at %s %s %s is yours for %s days. If the worst happens out there, this is where you wake up."),
        "service.inn_rent.extended": ("Feito! Seu quarto agora está pago por %s dias.", "Done! Your room is now paid for %s days."),
        "service.inn_checkout.done": ("Quarto devolvido. Volte sempre!", "Room returned. Come back anytime!"),
        "service.inn_tip.wait": ("Calma, deixa eu puxar pela memória... pergunte de novo daqui a pouco.",
                                 "Hold on, let me think... ask me again in a moment."),
        "service.inn_tip.none": ("Hoje ninguém contou nada que valha uma moeda. Fique com o seu dinheiro.",
                                 "Nobody told me anything worth a coin today. Keep your money."),
        "service.inn_tip_boss.done": ("Um caçador jurou ter visto %1$s, a uns %2$s blocos %3$s. Dizem que algo grande mora lá. Marquei no seu rastreador.",
                                      "A hunter swore he saw %1$s, about %2$s blocks %3$s. They say something big lives there. I marked it on your tracker."),
        "service.inn_tip_place.done": ("Um viajante falou de %1$s, a uns %2$s blocos %3$s. Quem volta de lá não volta de bolso vazio. Marquei no seu rastreador.",
                                       "A traveler spoke of %1$s, about %2$s blocks %3$s. Whoever comes back doesn't come back empty-handed. I marked it on your tracker."),
        "merchant.closed.0": ("Fechado! Volte de manhã, quando o sol nascer.", "Closed! Come back in the morning, at sunrise."),
        "merchant.closed.1": ("A esta hora? A loja só abre de manhã.", "At this hour? The shop only opens in the morning."),
        "merchant.closed.2": ("*boceja* Estamos fechados... amanhã cedo eu te atendo.", "*yawns* We're closed... I'll serve you first thing tomorrow."),
        "citizen.tavern.0": ("Mais uma rodada! O dia hoje foi longo.", "Another round! It's been a long day."),
        "citizen.tavern.1": ("A sopa da estalagem é a melhor do reino.", "The inn's soup is the best in the realm."),
        "citizen.tavern.2": ("Depois do trabalho, nada como uma caneca cheia.", "After work, nothing beats a full mug."),
        "citizen.tavern.3": ("Senta aí, forasteiro! Hoje tem história boa.", "Have a seat, stranger! Good stories tonight."),
    }
    for k, (pt, en) in lines.items():
        tr(f"dialogue.{NS}.cities.{k}", pt, en)

    tr(f"message.{NS}.cities.inn.expired", "Seu aluguel em %s terminou.", "Your room in %s has expired.")
    tr(f"message.{NS}.cities.inn.last_day", "Seu quarto em %s vence amanhã cedo.", "Your room in %s runs out tomorrow morning.")
    tr(f"message.{NS}.cities.inn.the_inn", "a estalagem", "the inn")

    tr(f"screen.{NS}.cities.quest", "Pedido", "Request")
    tr(f"screen.{NS}.cities.trade_closed", "Loja fechada: abre de manhã.", "Shop closed: opens in the morning.")
    tr(f"objective.{NS}.cities.quest", "Pedido: %s", "Request: %s")
    tr(f"reason.{NS}.cities.quest", "pedido de um morador", "a townsperson's request")

    # Estruturas que ainda não tinham nome de rumor.
    rumors = {
        "depths_dungeon": ("uma masmorra das profundezas", "a dungeon of the depths"),
        "dwarf_hold": ("um salão anão sob a montanha", "a dwarven hold under the mountain"),
        "end_library": ("uma biblioteca esquecida", "a forgotten library"),
        "fey_ring": ("um anel de fadas", "a fairy ring"),
        "garganta_site": ("um rasgo no céu, uma Garganta", "a tear in the sky, a Garganta"),
        "nether_citadel": ("uma cidadela demoníaca", "a demonic citadel"),
        "quincy_camp": ("um acampamento Quincy", "a Quincy camp"),
        "sakura_torii_shrine": ("um santuário entre cerejeiras", "a shrine among cherry trees"),
        "steelball_saint_tomb": ("a tumba de um santo", "a saint's tomb"),
        "tower_frost": ("uma torre de gelo", "a tower of frost"),
        "tower_sand": ("uma torre de areia", "a tower of sand"),
        "tower_stone": ("uma torre de pedra", "a tower of stone"),
    }
    for k, (pt, en) in rumors.items():
        tr(f"rumor.{NS}.cities.structure.{k}", pt, en)

    commands = {
        "start.info": ("Cidade inicial: %s. Nascimento do mundo: %s %s %s (original: %s).",
                       "Starting city: %s. World spawn: %s %s %s (original: %s)."),
        "start.state.set": ("definida", "set"),
        "start.state.not_found": ("nenhuma cidade perto", "no city nearby"),
        "start.state.off": ("desligada", "off"),
        "start.state.undecided": ("ainda não procurada", "not searched yet"),
        "start.tp": ("Você foi para a praça da cidade inicial.", "You went to the starting city's plaza."),
        "start.none": ("Este mundo não tem cidade inicial.", "This world has no starting city."),
        "start.not_found": ("Nenhuma cidade do mod foi achada perto. O ponto de nascimento não mudou.",
                            "No mod city was found nearby. The spawn point did not change."),
        "start.cleared": ("Cidade inicial desligada: o ponto de nascimento do mundo voltou ao original.",
                          "Starting city turned off: the world spawn is back to the original."),
        "inn.rested": ("Descanso aplicado.", "Rest applied."),
        "inn.expired": ("Aluguel encerrado.", "Rent ended."),
        "inn.no_room": ("Você não tem quarto alugado.", "You have no rented room."),
        "inn.status": ("Quarto em %s %s %s — faltam %s dia(s).", "Room at %s %s %s — %s day(s) left."),
        "quest.stepped": ("Passo pulado.", "Step skipped."),
        "quest.no_active": ("Nenhum pedido ativo (ou já está pronto para entregar).", "No active request (or it is ready to hand in)."),
        "quest.reset": ("Pedido apagado e limites do dia zerados.", "Request cleared and daily limits reset."),
        "quest.bad_id": ("Pedido desconhecido. Veja /mahou city quest list.", "Unknown request. See /mahou city quest list."),
        "quest.no_npc": ("Nenhum morador a 24 blocos para ser quem pede.", "No townsperson within 24 blocks to be the giver."),
        "quest.started": ("Pedido \"%s\" começado com %s.", "Request \"%s\" started with %s."),
        "routine.on": ("ligada", "on"),
        "routine.off": ("desligada", "off"),
        "routine.status": ("Rotina dos moradores: %s · fase %s · hora do dia %s.", "Townsfolk routine: %s · phase %s · time of day %s."),
    }
    for k, (pt, en) in commands.items():
        tr(f"command.{NS}.cities.{k}", pt, en)


# =================================================================================================
#  Sidequests
# =================================================================================================
Q = f"quest.{NS}.cities."

QUEST_GENERAL = {
    "hook.0": ("Ei, você aí! Tem um minutinho? Preciso de ajuda.", "Hey, you there! Got a minute? I need a hand."),
    "hook.1": ("Aventureiro! Chegou bem na hora. Posso pedir um favor?", "Adventurer! Just in time. May I ask a favor?"),
    "hook.2": ("Psiu! Você parece de confiança. Fala comigo um instante?", "Psst! You look trustworthy. Talk to me for a moment?"),
    "hook.3": ("Com licença... será que você poderia me ajudar?", "Excuse me... could you possibly help me?"),
    "thanks.0": ("Sabia que podia contar com você, %s!", "I knew I could count on you, %s!"),
    "thanks.1": ("Obrigado, %s! Volte quando resolver.", "Thank you, %s! Come back when it's done."),
    "thanks.2": ("Ótimo! Vou ficar aqui esperando, %s.", "Wonderful! I'll be waiting right here, %s."),
    "declined": ("Tudo bem... se mudar de ideia, estou por aqui.", "All right... if you change your mind, I'll be around."),
    "later": ("Sem pressa. Estou por aqui.", "No rush. I'll be around."),
    "abandoned": ("Desistiu? Que pena. Vou procurar outra pessoa.", "Giving up? A pity. I'll find someone else."),
    "none": ("Hoje não preciso de nada, obrigado.", "I don't need anything today, thank you."),
    "busy": ("Você já está ajudando %s. Uma coisa de cada vez!", "You're already helping %s. One thing at a time!"),
    "accept": ("Aceitar", "Accept"),
    "decline": ("Agora não", "Not now"),
    "back": ("Já volto", "I'll be back"),
    "abandon": ("Desistir", "Give up"),
    "abandon.desc": ("Cancela este pedido. Você pode pegar outro depois.", "Cancels this request. You can take another later."),
    "note": ("✦ Pedido: %s", "✦ Request: %s"),
    "choose": ("✦ Como você resolve isso?", "✦ How do you settle this?"),
    "remind": ("Ainda conto com você: %s.", "I'm still counting on you: %s."),
    "reward": ("Recompensa: %s moedas · %s de reputação · %s XP", "Reward: %s coins · %s reputation · %s XP"),
    "next": ("Pedido: %s", "Request: %s"),
    "return": ("Volte para %s", "Return to %s"),
    "expired": ("O pedido de %s caducou.", "%s's request has lapsed."),
    "done": ("Pedido concluído", "Request complete"),
    "target.guard": ("um guarda", "a guard"),
    "target.citizen": ("um morador", "a townsperson"),
    "target.lord": ("quem governa a cidade", "the city's ruler"),
    "target.merchant": ("outro mercador", "another merchant"),
    "target.merchant.blacksmith": ("o ferreiro", "the blacksmith"),
    "target.merchant.alchemist": ("o alquimista", "the alchemist"),
    "target.merchant.scribe": ("o escriba", "the scribe"),
    "target.merchant.jeweler": ("o joalheiro", "the jeweler"),
    "target.merchant.innkeeper": ("o estalajadeiro", "the innkeeper"),
    "target.merchant.priest": ("o sacerdote", "the priest"),
    "target.merchant.fence": ("o receptador", "the fence"),
    "target.merchant.grocer": ("o quitandeiro", "the grocer"),
    "target.merchant.tailor": ("o alfaiate", "the tailor"),
}

# Por pedido: title, intro (%s = jogador), sN (texto curto do passo: %1$s e %2$s conforme o tipo), sN.say (fala do alvo
# ou narração: %1$s jogador, %2$s quem pediu), ready (%s jogador), a/b (opções) e a.done/b.done (%s jogador).
QUESTS = {
    # ------------------------------------------------------------------ cidadãos
    "lost_cat": {
        "title": ("O gato fujão", "The runaway cat"),
        "intro": ("%s, por favor! O Bolota, meu gato, pulou a muralha atrás de um passarinho e não voltou. Algum vizinho deve ter visto para que lado ele foi. Você traria ele de volta?",
                  "%s, please! Dumpling, my cat, jumped the wall after a bird and never came back. Some neighbor must have seen which way he went. Would you bring him home?"),
        "s0": ("Pergunte a %1$s se viu o gato", "Ask %1$s about the cat"),
        "s0.say": ("O gato de %2$s? Vi, sim! Um bicho gordo, laranja, correndo para fora do portão. Foi para o mato.",
                   "%2$s's cat? I saw him! A fat orange thing, bolting out the gate. He went into the brush."),
        "s1": ("Procure o gato fora da muralha, %1$s", "Look for the cat outside the wall, %1$s"),
        "s1.say": ("Em cima de um galho, um gato laranja mia para você. Com paciência (e alguns arranhões), ele desce para o seu colo.",
                   "On a branch, an orange cat meows at you. With patience (and a few scratches), he climbs down into your arms."),
        "ready": ("Bolota! Seu sem-vergonha! ...Nem sei como agradecer, %s.", "Dumpling! You rascal! ...I don't know how to thank you, %s."),
        "a": ("Não foi nada", "It was nothing"),
        "b": ("Aceito uma gorjeta", "I'll take a tip"),
        "a.done": ("Que os deuses te paguem, %s. A cidade inteira vai saber do seu bom coração.",
                   "May the gods repay you, %s. The whole city will hear of your kind heart."),
        "b.done": ("Claro, claro! É pouco, mas é de coração. O Bolota agradece.", "Of course! It isn't much, but it's from the heart. Dumpling thanks you."),
    },
    "fever_tea": {
        "title": ("Chá para a febre", "Tea for the fever"),
        "intro": ("%s, meu filho ardeu em febre a noite toda. O alquimista sabe de um chá que resolve, mas eu não posso sair de perto do menino. Você falaria com ele por mim?",
                  "%s, my son burned with fever all night. The alchemist knows a tea that helps, but I can't leave the boy's side. Would you speak to him for me?"),
        "s0": ("Fale com %1$s sobre o chá", "Talk to %1$s about the tea"),
        "s0.say": ("Febre de criança? Chá de dente-de-leão resolve. Só que o meu estoque acabou... Colha uns quatro e leve direto para %2$s. A receita é só ferver.",
                   "A child's fever? Dandelion tea does it. But I'm out of stock... Pick four and take them straight to %2$s. Just boil them."),
        "s1": ("Leve %1$s× %2$s para quem pediu", "Bring %1$s× %2$s to the one who asked"),
        "s1.say": ("Dentes-de-leão! Vou ferver agora mesmo... Pronto. Olha só, a febre já está cedendo. Você salvou a nossa noite, %1$s.",
                   "Dandelions! I'll boil them right now... There. Look, the fever is already going down. You saved our night, %1$s."),
        "ready": ("O menino já dorme tranquilo. Você salvou a nossa noite, %s.", "The boy is sleeping soundly now. You saved our night, %s."),
        "a": ("Fico feliz em ajudar", "Happy to help"),
        "b": ("Remédio tem preço", "Medicine has a price"),
        "a.done": ("Obrigada de verdade. Vou contar para todo mundo quem cuidou do meu filho.", "Thank you, truly. I'll tell everyone who looked after my son."),
        "b.done": ("É justo... tome. Guardei essas moedas para uma emergência mesmo.", "Fair enough... here. I was saving these coins for an emergency anyway."),
    },
    "lost_purse": {
        "title": ("A bolsa perdida", "The lost purse"),
        "intro": ("Ai, %s, que desgraça! Perdi a bolsa com as economias do mês. Acho que foi perto do portão... Pergunte à guarda se alguém achou, por favor!",
                  "Oh, %s, what a disaster! I lost my purse with the month's savings. Near the gate, I think... Ask the guard if anyone found it, please!"),
        "s0": ("Pergunte a %1$s sobre a bolsa", "Ask %1$s about the purse"),
        "s0.say": ("Uma bolsa? Ninguém entregou nada. Mas um corvo passou por aqui levando algo brilhante e voou para fora da muralha. Eu olharia por lá.",
                   "A purse? Nobody handed one in. But a crow flew by carrying something shiny, out over the wall. I'd look there."),
        "s1": ("Procure a bolsa fora da muralha, %1$s", "Look for the purse outside the wall, %1$s"),
        "s1.say": ("Num ninho baixo, entre gravetos, a bolsa de %2$s. Ainda está pesada de moedas.",
                   "In a low nest, among the twigs: %2$s's purse. Still heavy with coins."),
        "ready": ("Você achou?! Diga que achou, %s!", "You found it?! Tell me you found it, %s!"),
        "a": ("Devolver a bolsa inteira", "Return the whole purse"),
        "b": ("Ficar com uma parte", "Keep a share"),
        "a.done": ("Está tudo aqui! Tome, fique com isto. Honestidade assim é rara, %s.", "It's all here! Take this. Honesty like yours is rare, %s."),
        "b.done": ("Está... mais leve do que eu lembrava. Bom, antes isso do que nada. Obrigado, eu acho.",
                   "It's... lighter than I remember. Well, better than nothing. Thanks, I suppose."),
    },
    "night_noises": {
        "title": ("Barulhos na noite", "Noises in the night"),
        "intro": ("%s, você que é de luta: toda noite escuto grunhidos do outro lado da muralha. Ninguém aqui dorme mais. Dê um jeito no que estiver rondando por lá e avise a guarda.",
                  "%s, you look like a fighter: every night I hear growling beyond the wall. Nobody here sleeps anymore. Deal with whatever is prowling out there and tell the guard."),
        "s0": ("Derrote %1$s monstros", "Defeat %1$s monsters"),
        "s0.say": ("O último monstro cai. Os arredores ficam em silêncio.", "The last monster falls. The surroundings go quiet."),
        "s1": ("Avise %1$s do que encontrou", "Tell %1$s what you found"),
        "s1.say": ("Tudo isso tão perto da muralha? Vou reforçar a ronda. Diga a %2$s que pode dormir em paz.",
                   "All that, so close to the wall? I'll double the patrol. Tell %2$s to sleep easy."),
        "ready": ("E então, %s? O que era aquele barulho todo?", "Well, %s? What was all that noise?"),
        "a": ("Só uns bichos. Já passou.", "Just some critters. It's over."),
        "b": ("Monstros. Fique atento.", "Monsters. Stay alert."),
        "a.done": ("Ufa! Então era coisa da minha cabeça. Tome, pelo incômodo.", "Phew! So it was all in my head. Here, for your trouble."),
        "b.done": ("Prefiro saber a verdade. Vou avisar os vizinhos para trancarem as portas. Obrigado por não me enrolar, %s.",
                   "I'd rather know the truth. I'll warn the neighbors to bar their doors. Thanks for being straight with me, %s."),
    },
    "love_letter": {
        "title": ("Carta de amor", "Love letter"),
        "intro": ("%s, preciso de um favor... delicado. Escrevi uma carta para alguém, mas me falta coragem para entregar. Deixe com um dos moradores: aqui todo mundo se conhece, ela chega.",
                  "%s, I need a... delicate favor. I wrote a letter to someone, but I lack the nerve to deliver it. Leave it with one of the townsfolk: everyone knows everyone here, it will arrive."),
        "s0": ("Entregue a carta a %1$s", "Hand the letter to %1$s"),
        "s0.say": ("Uma carta de %2$s? Ah, eu sabia! Deixa comigo, chega na pessoa certa. E diga que flores ajudam: papoulas, de preferência.",
                   "A letter from %2$s? Ha, I knew it! Leave it to me, it'll reach the right person. And say flowers help: poppies, preferably."),
        "s1": ("Leve %1$s× %2$s para quem pediu", "Bring %1$s× %2$s to the one who asked"),
        "s1.say": ("Papoulas! Era o que faltava. Agora... o que eu digo quando a gente se encontrar, %1$s?",
                   "Poppies! Just what was missing. Now... what do I say when we meet, %1$s?"),
        "ready": ("E agora, %s? O que eu digo quando a gente se encontrar?", "And now, %s? What do I say when we meet?"),
        "a": ("Diga o que sente, sem enfeite", "Say what you feel, plainly"),
        "b": ("Capriche no poema", "Go all in on a poem"),
        "a.done": ("Tem razão. Vou lá agora, antes que a coragem acabe. Obrigado, %s!", "You're right. I'm going now, before my courage runs out. Thank you, %s!"),
        "b.done": ("Um poema! Isso! \"Teus olhos são dois cristais de mana...\" Perfeito. Obrigado, %s!",
                   "A poem! Yes! \"Thine eyes are two mana crystals...\" Perfect. Thank you, %s!"),
    },
    # ------------------------------------------------------------------ guardas
    "extra_patrol": {
        "title": ("Ronda extra", "Extra patrol"),
        "intro": ("Estamos com pouca gente, %s, e os monstros perceberam. Faça uma ronda por mim: limpe os arredores e dê uma olhada no velho posto de vigia.",
                  "We're short-handed, %s, and the monsters have noticed. Walk a patrol for me: clear the surroundings and check the old lookout post."),
        "s0": ("Derrote %1$s monstros nos arredores", "Defeat %1$s monsters nearby"),
        "s0.say": ("Os arredores estão limpos. Falta ver o posto de vigia.", "The surroundings are clear. The lookout post is next."),
        "s1": ("Verifique o posto de vigia, %1$s", "Check the lookout post, %1$s"),
        "s1.say": ("Do alto dá para ver longe: rastros frescos, mas nenhum acampamento. Por enquanto.",
                   "From up here you can see far: fresh tracks, but no camp. For now."),
        "ready": ("De volta e inteiro. Relatório, %s?", "Back in one piece. Report, %s?"),
        "a": ("Relatório completo, sem cobrar", "Full report, no charge"),
        "b": ("Ronda extra, paga extra", "Extra patrol, extra pay"),
        "a.done": ("É de gente assim que a guarda precisa. O capitão vai saber o seu nome, %s.", "That's the kind of person the guard needs. The captain will hear your name, %s."),
        "b.done": ("Justo. Trabalho é trabalho. Aqui está, do caixa da guarnição.", "Fair. Work is work. Here, from the garrison's coffer."),
    },
    "lost_recruit": {
        "title": ("O recruta sumido", "The missing recruit"),
        "intro": ("%s, um recruta meu não apareceu para o turno. Se o capitão descobrir, o rapaz está frito. A última vez que o viram foi na estalagem... Ache-o antes do capitão.",
                  "%s, one of my recruits missed his shift. If the captain finds out, the lad is done for. He was last seen at the inn... Find him before the captain does."),
        "s0": ("Pergunte pelo recruta: fale com %1$s", "Ask about the recruit: talk to %1$s"),
        "s0.say": ("O recruta? Bebeu três canecas, disse que ia \"ver as estrelas\" e saiu cambaleando pelo portão. Aliás, não pagou a conta.",
                   "The recruit? Drank three mugs, said he'd go \"watch the stars\" and staggered out the gate. Didn't pay his tab, by the way."),
        "s1": ("Procure o recruta fora da muralha, %1$s", "Look for the recruit outside the wall, %1$s"),
        "s1.say": ("Debaixo de uma árvore, o recruta ronca abraçado ao elmo. Ele acorda assustado: \"Não conta pro sargento! Eu te pago!\"",
                   "Under a tree, the recruit snores, hugging his helmet. He wakes with a start: \"Don't tell the sergeant! I'll pay you!\""),
        "ready": ("Achou o rapaz, %s? Onde ele estava?", "Found the lad, %s? Where was he?"),
        "a": ("\"Ele se perdeu na ronda\"", "\"He got lost on patrol\""),
        "b": ("Contar a verdade", "Tell the truth"),
        "a.done": ("Perdido na ronda, é? Hmm. Vou fingir que acredito. (O recruta desliza umas moedas para a sua mão.)",
                   "Lost on patrol, was he? Hmm. I'll pretend I believe it. (The recruit slips some coins into your hand.)"),
        "b.done": ("Dormindo de ressaca. Eu imaginava. Ele vai lavar o estábulo por um mês. E você, %s, tem a minha confiança.",
                   "Sleeping off a hangover. I thought so. He'll scrub the stables for a month. And you, %s, have my trust."),
    },
    "wall_arrows": {
        "title": ("Flechas para a muralha", "Arrows for the wall"),
        "intro": ("A aljava da muralha está quase vazia, %s. O ferreiro faz as pontas, mas faltam penas para as flechas. Traga-me algumas e depois avise o ferreiro para começar.",
                  "The wall's quivers are nearly empty, %s. The blacksmith forges the heads, but we lack feathers for the shafts. Bring me some, then tell the blacksmith to start."),
        "s0": ("Leve %1$s× %2$s para quem pediu", "Bring %1$s× %2$s to the one who asked"),
        "s0.say": ("Boas penas. Agora corra ao ferreiro e diga que a encomenda da muralha pode começar.",
                   "Good feathers. Now run to the blacksmith and say the wall's order can begin."),
        "s1": ("Avise %1$s da encomenda", "Tell %1$s about the order"),
        "s1.say": ("Flechas para a muralha? Já era hora. Diga a %2$s que ficam prontas antes de anoitecer.",
                   "Arrows for the wall? About time. Tell %2$s they'll be ready before nightfall."),
        "ready": ("O ferreiro já começou? Ótimo. Quanto às penas, %s...", "The blacksmith has started? Good. As for the feathers, %s..."),
        "a": ("São por conta da casa", "They're on the house"),
        "b": ("Penas custam moedas", "Feathers cost coins"),
        "a.done": ("A cidade agradece. Cada flecha dessas leva o seu nome, %s.", "The city thanks you. Every one of those arrows carries your name, %s."),
        "b.done": ("Certo. A guarnição paga as suas contas. Aqui está.", "Right. The garrison pays its debts. Here."),
    },
    "gate_rumor": {
        "title": ("Boato no portão", "Rumor at the gate"),
        "intro": ("%s, andam dizendo que tem coisa rondando o portão de noite. Eu não posso largar o posto. Ouça o que o povo viu e, se for verdade, resolva.",
                  "%s, people say something prowls by the gate at night. I can't leave my post. Hear what the folk saw and, if it's true, deal with it."),
        "s0": ("Ouça o que %1$s viu", "Hear what %1$s saw"),
        "s0.say": ("Eu vi, sim! Olhos brilhando no escuro, bem perto do portão. Mais de um par. Diga a %2$s que não é invenção minha!",
                   "I did see it! Eyes glowing in the dark, right by the gate. More than one pair. Tell %2$s I'm not making it up!"),
        "s1": ("Derrote %1$s monstros", "Defeat %1$s monsters"),
        "s1.say": ("Os olhos brilhantes não vão mais assustar ninguém.", "The glowing eyes won't frighten anyone again."),
        "ready": ("Então o boato era verdade. Bom trabalho, %s.", "So the rumor was true. Good work, %s."),
        "a": ("Era só o meu dever", "Just doing my duty"),
        "b": ("Cobrar pelo serviço", "Charge for the job"),
        "a.done": ("Falou como um de nós. A guarda não esquece, %s.", "Spoken like one of us. The guard won't forget, %s."),
        "b.done": ("Mercenário, hein? Tudo bem: serviço feito é serviço pago.", "A mercenary, eh? Fine: a job done is a job paid."),
    },
    # ------------------------------------------------------------------ mercadores
    "late_delivery": {
        "title": ("Entrega atrasada", "Late delivery"),
        "intro": ("%s, estou com a loja cheia e uma encomenda atrasada. Leve este pacote a outro mercador da cidade: é coisa do grêmio, qualquer um deles recebe.",
                  "%s, my shop is packed and I have an overdue parcel. Take this package to another merchant in town: it's guild business, any of them will take it."),
        "s0": ("Entregue o pacote a %1$s", "Deliver the package to %1$s"),
        "s0.say": ("A encomenda de %2$s, até que enfim! Mas avise: o fornecedor não vem mais, a estrada está tomada de monstros.",
                   "%2$s's parcel, at last! But pass this on: the supplier won't come anymore, the road is crawling with monsters."),
        "s1": ("Limpe a estrada: derrote %1$s monstros", "Clear the road: defeat %1$s monsters"),
        "s1.say": ("A estrada está livre. As carroças podem voltar a passar.", "The road is clear. The carts can roll again."),
        "ready": ("Entregou e ainda limpou a estrada? Você vale ouro, %s.", "Delivered it and cleared the road too? You're worth your weight in gold, %s."),
        "a": ("Cobrar o combinado", "Charge the agreed fee"),
        "b": ("Fica como favor", "Call it a favor"),
        "a.done": ("Combinado é combinado. Aqui está, com um agrado a mais.", "A deal is a deal. Here you go, with a little extra."),
        "b.done": ("Favor de freguês não se esquece. Passe na loja quando quiser, %s: para você, o preço é de amigo.",
                   "A customer's favor is never forgotten. Drop by anytime, %s: for you, it's the friend's price."),
    },
    "short_stock": {
        "title": ("Estoque em falta", "Out of stock"),
        "intro": ("Chegou em boa hora, %s! Meu estoque acabou e o fornecedor sumiu. Se você me trouxer o que falta, eu pago bem.",
                  "You came at the right time, %s! I'm out of stock and my supplier vanished. Bring me what's missing and I'll pay well."),
        "s0": ("Leve %1$s× %2$s para quem pediu", "Bring %1$s× %2$s to the one who asked"),
        "s0.say": ("Isso mesmo! Salvou o meu dia. Só mais uma coisa: avise outro mercador que o meu fornecedor sumiu. Ele compra do mesmo.",
                   "That's it! You saved my day. One more thing: warn another merchant that my supplier vanished. He buys from the same man."),
        "s1": ("Avise %1$s sobre o fornecedor", "Warn %1$s about the supplier"),
        "s1.say": ("O fornecedor de %2$s sumiu? Bom saber, eu ia pagar adiantado. Obrigado pelo aviso.",
                   "%2$s's supplier vanished? Good to know, I was about to pay in advance. Thanks for the warning."),
        "ready": ("Avisou? Ótimo. Vamos acertar as contas, %s.", "You warned him? Good. Let's settle up, %s."),
        "a": ("Preço justo", "A fair price"),
        "b": ("Pechinchar", "Haggle"),
        "a.done": ("Negócio limpo, do jeito que eu gosto. Volte sempre, %s.", "Clean business, just how I like it. Come again, %s."),
        "b.done": ("Você pechincha melhor do que eu! Tá bom, tá bom... leve.", "You haggle better than I do! All right, all right... take it."),
    },
    "apple_thief": {
        "title": ("O ladrão de maçãs", "The apple thief"),
        "intro": ("%s! Alguém anda roubando da minha banca toda manhã. Um morador viu quem foi, mas não quer se meter comigo. Para você talvez conte.",
                  "%s! Someone has been stealing from my stall every morning. A townsperson saw who it was but won't get involved with me. They might tell you."),
        "s0": ("Pergunte a %1$s quem é o ladrão", "Ask %1$s who the thief is"),
        "s0.say": ("Vi, sim... é um menino magrinho. Ele corre para fora da muralha com a comida. Não seja duro com ele, tá?",
                   "I saw... it's a skinny little boy. He runs outside the wall with the food. Don't be hard on him, all right?"),
        "s1": ("Encontre o esconderijo, %1$s", "Find the hideout, %1$s"),
        "s1.say": ("Numa cabana de galhos, um menino divide maçãs com a irmã pequena. Ele encara você, apavorado.",
                   "In a hut of branches, a boy shares apples with his little sister. He stares at you, terrified."),
        "ready": ("E então, %s? Achou o ladrão?", "Well, %s? Did you find the thief?"),
        "a": ("Entregar o menino", "Turn the boy in"),
        "b": ("\"Eles só têm fome\"", "\"They're just hungry\""),
        "a.done": ("Um moleque! Vou falar com a guarda. Tome a sua recompensa, você mereceu.", "A street kid! I'll have a word with the guard. Take your reward, you earned it."),
        "b.done": ("Fome...? Duas crianças sozinhas? ...Diga a eles que venham à banca. Tem trabalho e tem comida. E obrigado por me contar, %s.",
                   "Hungry...? Two children on their own? ...Tell them to come to the stall. There's work and there's food. And thank you for telling me, %s."),
    },
    "rival_prices": {
        "title": ("De olho na concorrência", "Eyeing the competition"),
        "intro": ("%s, aqui entre nós: preciso saber quanto a concorrência anda cobrando. Passe em outra loja, puxe conversa e depois me traga papel para eu anotar tudo.",
                  "%s, between us: I need to know what the competition is charging. Drop by another shop, make small talk, then bring me paper so I can write it all down."),
        "s0": ("Puxe conversa com %1$s", "Make small talk with %1$s"),
        "s0.say": ("Meus preços? Os melhores da cidade, claro! ...Espera. Foi %2$s que mandou você, não foi? Que raposa.",
                   "My prices? Best in town, of course! ...Wait. %2$s sent you, right? That old fox."),
        "s1": ("Leve %1$s× %2$s para anotar os preços", "Bring %1$s× %2$s to write the prices down"),
        "s1.say": ("Papel! Ótimo. Agora me conte tudo, %1$s. Desconfiaram de alguma coisa?", "Paper! Good. Now tell me everything, %1$s. Did they suspect anything?"),
        "ready": ("E os preços da concorrência, %s?", "And the competition's prices, %s?"),
        "a": ("Contar os preços de verdade", "Report the real prices"),
        "b": ("Avisar que o rival percebeu", "Admit the rival caught on"),
        "a.done": ("Hah! Eu sabia que estavam cobrando demais. Amanhã baixo os meus. Tome a sua parte.",
                   "Hah! I knew they were overcharging. Tomorrow I lower mine. Here's your share."),
        "b.done": ("Percebeu?! Droga. Bom... melhor competir de cara limpa, então. Obrigado pela franqueza, %s.",
                   "Caught on?! Blast. Well... better to compete honestly, then. Thanks for your candor, %s."),
    },
    # ------------------------------------------------------------------ Lorde
    "people_voice": {
        "title": ("A voz do povo", "The voice of the people"),
        "intro": ("%s, quem governa só ouve o que querem que ouça. Ande pela cidade por mim: escute um morador, um mercador e um guarda, e traga-me a verdade.",
                  "%s, a ruler only hears what others want heard. Walk the city for me: listen to a townsperson, a merchant and a guard, and bring me the truth."),
        "s0": ("Escute %1$s", "Listen to %1$s"),
        "s0.say": ("O palácio quer saber? Diga que as ruas estão bonitas, mas o pão ficou caro. E que a gente gosta quando %2$s aparece na praça.",
                   "The palace wants to know? Say the streets look lovely, but bread got expensive. And that we like it when %2$s comes down to the plaza."),
        "s1": ("Escute %1$s", "Listen to %1$s"),
        "s1.say": ("Para o palácio? Os impostos do portão pesam, mas estrada segura compensa. Mais guardas nas caravanas: é o que eu pediria.",
                   "For the palace? The gate taxes weigh on us, but safe roads make up for it. More guards on the caravans: that's what I'd ask."),
        "s2": ("Escute %1$s", "Listen to %1$s"),
        "s2.say": ("Do palácio? Então diga: faltam botas e sobram turnos. Mas ninguém aqui larga a muralha.",
                   "From the palace? Then say this: too few boots, too many shifts. But nobody here abandons the wall."),
        "ready": ("Você ouviu o meu povo, %s. O que eles dizem?", "You have heard my people, %s. What do they say?"),
        "a": ("Contar tudo, até as queixas", "Tell everything, complaints included"),
        "b": ("Dizer que está tudo ótimo", "Say all is well"),
        "a.done": ("Pão caro, impostos, botas... Poucos teriam coragem de me dizer isso. Você serve bem a este reino, %s.",
                   "Costly bread, taxes, boots... Few would dare tell me that. You serve this realm well, %s."),
        "b.done": ("Tudo ótimo? Que alívio. Tome, pela boa notícia. (Ao lado do trono, um conselheiro franze a testa.)",
                   "All is well? What a relief. Take this, for the good news. (Beside the throne, an advisor frowns.)"),
    },
    "border_threat": {
        "title": ("Ameaça na fronteira", "Threat at the border"),
        "intro": ("%s, os batedores falam de monstros se juntando perto das nossas terras. Preciso de alguém de confiança: reduza o número deles e veja com os próprios olhos o que há no marco da fronteira.",
                  "%s, the scouts speak of monsters gathering near our lands. I need someone I can trust: thin their numbers and see with your own eyes what lies at the border stone."),
        "s0": ("Derrote %1$s monstros", "Defeat %1$s monsters"),
        "s0.say": ("A horda diminuiu. Falta ver o marco da fronteira.", "The horde is thinned. The border stone is next."),
        "s1": ("Vá até o marco da fronteira, %1$s", "Go to the border stone, %1$s"),
        "s1.say": ("No marco de pedra, pegadas de todo tamanho seguem para longe da cidade. Por ora, eles recuaram.",
                   "At the stone marker, tracks of every size lead away from the city. For now, they have pulled back."),
        "ready": ("Você voltou, %s. O que viu na fronteira?", "You have returned, %s. What did you see at the border?"),
        "a": ("Pedir reforço para a guarda", "Ask for more guards"),
        "b": ("Pedir ouro pela missão", "Ask for gold"),
        "a.done": ("Pensa na cidade antes de si. Assim será: mais guardas na muralha. E a minha gratidão, %s.",
                   "You think of the city before yourself. So be it: more guards on the wall. And my gratitude, %s."),
        "b.done": ("Um preço justo por um trabalho perigoso. O tesouro paga.", "A fair price for dangerous work. The treasury pays."),
    },
    "grand_feast": {
        "title": ("O grande banquete", "The grand feast"),
        "intro": ("%s, quero dar um banquete, mas os celeiros do palácio estão no fim. Traga-me trigo e depois leve a encomenda ao estalajadeiro: ele cuida do resto.",
                  "%s, I wish to hold a feast, but the palace granaries are nearly bare. Bring me wheat, then take the order to the innkeeper: he will handle the rest."),
        "s0": ("Leve %1$s× %2$s para quem pediu", "Bring %1$s× %2$s to the one who asked"),
        "s0.say": ("Trigo dourado! Excelente. Agora diga ao estalajadeiro que o forno pode acender.",
                   "Golden wheat! Excellent. Now tell the innkeeper the ovens may be lit."),
        "s1": ("Entregue a encomenda: fale com %1$s", "Deliver the order: talk to %1$s"),
        "s1.say": ("Banquete do palácio? Deixa comigo! Pão, assado e torta. Diga a %2$s que fica pronto ao pôr do sol.",
                   "A palace feast? Leave it to me! Bread, roast and pie. Tell %2$s it will be ready by sundown."),
        "ready": ("Está tudo encaminhado, %s. Só falta decidir uma coisa: quem senta à mesa?", "All is in motion, %s. One thing remains: who sits at the table?"),
        "a": ("Banquete aberto ao povo", "A feast open to all"),
        "b": ("Banquete só da corte", "A feast for the court only"),
        "a.done": ("Abram as portas do salão! Hoje a cidade inteira janta comigo. E vai lembrar de quem teve a ideia, %s.",
                   "Open the hall doors! Tonight the whole city dines with me. And it will remember whose idea it was, %s."),
        "b.done": ("Uma noite elegante, então. Os nobres saberão ser generosos com você.", "An elegant evening, then. The nobles will know how to be generous with you."),
    },
}


def quests():
    for k, (pt, en) in QUEST_GENERAL.items():
        tr(Q + k, pt, en)
    for qid, texts in QUESTS.items():
        for k, (pt, en) in texts.items():
            tr(f"{Q}{qid}.{k}", pt, en)


def check_quests():
    """Os ids e o número de passos precisam bater com CityQuests.java (cada passo tem texto e fala)."""
    java = open(os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/cities/CityQuests.java"), encoding="utf-8").read()
    found = re.findall(r'add\("([a-z_]+)", "([a-z]+)", new Outcome\([^)]*\), new Outcome\([^)]*\), (.*)\);', java)
    assert found, "nenhum pedido achado em CityQuests.java"
    ids = set()
    for qid, _giver, steps in found:
        ids.add(qid)
        n = len(re.findall(r'\b(?:fetch|kill|talk|visit)\(', steps))
        assert qid in QUESTS, f"pedido {qid} sem textos"
        need = ["title", "intro", "ready", "a", "b", "a.done", "b.done"]
        for i in range(n):
            need += [f"s{i}", f"s{i}.say"]
        for k in need:
            assert k in QUESTS[qid], f"pedido {qid}: falta o texto {k}"
        assert f"s{n}" not in QUESTS[qid], f"pedido {qid}: texto de passo sobrando (o Java tem {n} passos)"
        for target in re.findall(r'talk\("([a-z:_]+)"\)', steps):
            assert "target." + target.replace(":", ".") in QUEST_GENERAL, f"alvo sem nome: {target}"
    for qid in QUESTS:
        assert qid in ids, f"textos de um pedido que não existe no Java: {qid}"


def check_no_clash():
    """Nenhuma chave nova pode repetir (com outro texto) uma chave do módulo antigo."""
    for lang, mine in (("pt_br", PT), ("en_us", EN)):
        old = json.load(open(os.path.join(OLD_LANG, lang + ".json"), encoding="utf-8"))
        for k, v in mine.items():
            assert k not in old or old[k] == v, f"chave já existe em lang/cities com outro texto: {k}"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    effect_icon()
    general()
    quests()
    check_quests()
    check_no_clash()
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(PT.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))
    print(f"cities_npc: {len(PT)} traduções, {len(QUESTS)} pedidos")


if __name__ == "__main__":
    main()
