"""Onda 6 (city_pace) — traduções do RITMO DA CIDADE: obras em dias, etapas, Diário, ajuda do jogador, inauguração.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w6_pace_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). Rode de novo DEPOIS de qualquer gerador antigo do módulo
(settlements_lang.py, settlements_v14_lang.py, settlements_styles_lang.py): este aqui também corrige três textos deles
que falavam de obra instantânea (as descrições do Impulso de Obra e do Erguer Obra e a dica da melhoria).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- durações
    "pace.day": ("%s dia", "%s day"),
    "pace.days": ("%s dias", "%s days"),
    # ---- etapas da obra
    "pace.stage.1": ("Terreno e fundação", "Ground and foundation"),
    "pace.stage.2": ("Andaime e estrutura", "Scaffold and frame"),
    "pace.stage.3": ("Paredes", "Walls"),
    "pace.stage.4": ("Telhado", "Roof"),
    "pace.stage.5": ("Acabamento", "Finishing"),
    "pace.stage.piece": ("Trecho %s de %s", "Section %s of %s"),
    # ---- placa do canteiro
    "pace.sign": ("%s — %s · dia %s de %s", "%s — %s · day %s of %s"),
    # ---- resumo do amanhecer (uma linha no chat)
    "pace.dawn": ("Obras de %s: %s", "Works in %s: %s"),
    "pace.dawn.item": ("%s (dia %s de %s)", "%s (day %s of %s)"),
    "pace.dawn.queue": (" · +%s na fila", " · +%s queued"),
    "pace.dawn.done": (" · %s concluída(s) ontem", " · %s finished yesterday"),
    "pace.dawn.idle": ("fila vazia", "queue empty"),
    "pace.dawn.waiting": ("esperando a vez", "waiting for their turn"),
    # ---- inauguração
    "pace.done.new": ("Inauguração: %s — %s", "Inauguration: %s — %s"),
    "pace.done.up": ("%s: nível %s → %s (%s → %s)", "%s: level %s → %s (%s → %s)"),
    "pace.done.grand": ("Grande obra de %s", "Great work of %s"),
    # ---- Diário da cidade
    "pace.diary.start": ("Começou: %s (leva %s)", "Started: %s (takes %s)"),
    "pace.diary.stage": ("%s — nova etapa: %s", "%s — new stage: %s"),
    "pace.diary.done": ("Inaugurado: %s — %s", "Inaugurated: %s — %s"),
    "pace.diary.up": ("%s chegou ao nível %s: %s", "%s reached level %s: %s"),
    "pace.diary.other": ("Concluído: %s", "Finished: %s"),
    "pace.diary.gift": ("%s entregou materiais para %s", "%s delivered materials for %s"),
    "pace.diary.rank": ("A cidade subiu de degrau: agora é %s!", "The city ranked up: it is now a %s!"),
    # ---- ajuda do jogador
    "pace.help.hands": ("Você ajudou em %s (mão de obra de hoje: %s%%)", "You helped with %s (today's hand work: %s%%)"),
    "pace.help.hands.full": ("Por hoje os construtores dão conta do resto. Volte amanhã!",
                             "The builders can handle the rest for today. Come back tomorrow!"),
    "pace.help.materials": ("%s materiais entregues: %s adiantou %s%%.", "%s materials delivered: %s moved ahead %s%%."),
    "pace.help.full": ("Esta obra já recebeu todo o material de que precisava.", "This work already got all the materials it needed."),
    "pace.help.none": ("Você não tem materiais de obra (toras, tábuas, pedra, tijolos, vidro, ferro, cobre).",
                       "You have no building materials (logs, planks, stone, bricks, glass, iron, copper)."),
    # ---- magia de obra (rende uma fração de dia, com limite diário)
    "pace.magic.limit": ("A magia já fez o que podia por esta obra hoje. Amanhã tem mais.",
                         "Magic has done all it can for this work today. More tomorrow."),
    "pace.magic.erect": ("Erguer Obra: %s adiantou %s%% (−%s de mana).", "Raise Work: %s moved ahead %s%% (−%s mana)."),
    # ---- painel
    "ui.page.diary": ("Diário", "Diary"),
    "ui.page.diary.tip": ("Diário da cidade: o que começou, o que andou e o que foi inaugurado.",
                          "City Diary: what started, what moved on and what was inaugurated."),
    "ui.pace.row": ("dia %s de %s · %s", "day %s of %s · %s"),
    "ui.pace.day": ("Dia %s de %s", "Day %s of %s"),
    "ui.pace.takes": ("Leva %s", "Takes %s"),
    "ui.pace.left": ("Falta: %s", "Left: %s"),
    "ui.pace.up": ("A melhoria leva %s", "The upgrade takes %s"),
    "ui.pace.gain": ("Você ganha: %s", "You get: %s"),
    "ui.pace.deliver": ("Materiais", "Materials"),
    "ui.pace.deliver.tip1": ("Entregue materiais do seu inventário para adiantar esta obra (até 1/4 dela).",
                             "Deliver materials from your inventory to move this work ahead (up to 1/4 of it)."),
    "ui.pace.deliver.tip2": ("Valem toras, tábuas, pedra, tijolos, vidro, ferro e cobre. Você precisa estar na cidade.",
                             "Logs, planks, stone, bricks, glass, iron and copper count. You must be in the city."),
    "ui.pace.speed": ("Ritmo: %s%%", "Pace: %s%%"),
    "ui.pace.speed.tip1": ("Ritmo das obras da cidade", "Pace of the city's works"),
    "ui.pace.speed.tip2": ("Sobe com a Casa dos Construtores, serraria, oficina, cidade feliz e festival.",
                           "Goes up with the Builders' Hall, sawmill, workshop, a happy city and festivals."),
    "ui.pace.speed.tip3": ("Mais construtores = mais obras ao mesmo tempo; os que sobram ajudam um pouco.",
                           "More builders = more works at once; spare builders help a little."),
    "ui.pace.hint": ("As obras levam dias e andam com você longe (dormir conta). Para ajudar: agache e clique no canteiro com a "
                     "mão vazia.",
                     "Works take days and go on while you are away (sleeping counts). To help: sneak and click the site with an "
                     "empty hand."),
    "ui.diary.none": ("O Diário está em branco. Planeje uma obra e volte amanhã.",
                      "The Diary is blank. Plan a work and come back tomorrow."),
    "ui.diary.today": ("Hoje", "Today"),
    "ui.diary.yesterday": ("Ontem", "Yesterday"),
    "ui.diary.ago": ("Há %s dias", "%s days ago"),
    # ---- comandos de teste
    "cmd.days": ("Passaram %s dia(s) para as obras da cidade.", "%s day(s) passed for the city's works."),
    "cmd.pace": ("%s — ritmo do servidor ×%s · velocidade da cidade %s%% · %s construtores · %s obras",
                 "%s — server pace ×%s · city speed %s%% · %s builders · %s works"),
    "cmd.pace.work": ("%s: dia %s de %s (%s%%, %s) — faltam %s dias", "%s: day %s of %s (%s%%, %s) — %s days left"),
    "cmd.pace.set": ("Ritmo da cidade agora: ×%s (duração das obras).", "City pace is now ×%s (how long works take)."),
    "cmd.pace.fail": ("A config das cidades ainda não carregou.", "The city config has not loaded yet."),
    # ---- textos antigos que falavam de obra instantânea
    "ui.plan.upgrade.tip": ("Grátis, mas leva dias: o prédio é refeito maior e mais bonito e o efeito aumenta.",
                            "Free, but it takes days: the building is rebuilt bigger and prettier and its effect grows."),
    "ui.page.works.tip": ("Obras: a fila dos construtores, em dias, e o Diário da cidade.",
                          "Works: the builders' queue, in days, and the City Diary."),
}

# chaves sem o prefixo do módulo
RAW = {
    "hint.isekai_mahou.city_pace": (
        "As obras da cidade levam dias — e andam com você longe (dormir conta). Em Obras você vê o \"dia X de Y\" e o Diário.",
        "City works take days — and they go on while you are away (sleeping counts). Works shows \"day X of Y\" and the Diary."),
    "hint.isekai_mahou.city_pace_help": (
        "Você ajudou na obra! Dá para martelar um pouco por dia em cada canteiro e entregar materiais na aba Obras.",
        "You helped the build! You can hammer a little each day at every site and deliver materials in the Works tab."),
    "isekai_mahou.configuration.cityPace": ("Ritmo da cidade", "City pace"),
    "isekai_mahou.configuration.cityPace.tooltip": (
        "Multiplica a duração das obras da cidade, em dias do jogo. 1 = normal; 0,5 = metade do tempo; 2 = o dobro. Vale a "
        "opção do servidor.",
        "Multiplies how many in-game days city works take. 1 = normal; 0.5 = half the time; 2 = twice as long. The server's "
        "setting is the one that counts."),
    "isekai_mahou.configuration.section.isekai.mahou.city.common.toml": ("Cidades: ritmo das obras", "Cities: work pace"),
    "isekai_mahou.configuration.section.isekai.mahou.city.common.toml.title": ("Isekai Mahou — Cidades", "Isekai Mahou — Cities"),
    "glyph.isekai_mahou.city_hasten.desc": (
        "EFEITO de cidade. Na SUA cidade (Senhor ou conselho): as obras em andamento perto do alvo andam mais rápido por 12 s. "
        "Amplificar: mais rápido; Estender: mais tempo; Expandir: mais longe; Canalizar: mantém enquanto segura. Cresce com seu nível "
        "e Círculo (até 4×). As obras levam dias: a magia adianta um pouco, e cada obra aceita no máximo 3/4 de dia de magia por dia. "
        "Arcano: nível 5.",
        "City EFFECT. In YOUR city (Lord or council): works in progress near the target go faster for 12 s. Amplify: faster; Extend: "
        "longer; Expand: farther; Channel: keeps it up while held. Grows with your level and Circle (up to 4×). Works take days: "
        "magic moves them ahead a little, and each work takes at most 3/4 of a day of magic per day. Arcane: level 5."),
    "glyph.isekai_mahou.city_erect.desc": (
        "EFEITO de cidade. Na SUA cidade: faz DE UMA VEZ uma parte do trabalho da obra mais perto do alvo (de 1/4 a 2/3 de dia, "
        "conforme o Círculo), por METADE da sua mana máxima. Cada obra aceita no máximo 3/4 de dia de magia por dia — a espera "
        "continua, a magia só adianta. A cidade precisa de construtor e o povo não pode estar na miséria. Arcano: nível 18, Círculo 3.",
        "City EFFECT. In YOUR city: does AT ONCE a share of the nearest work (1/4 to 2/3 of a day, by Circle), for HALF your max "
        "mana. Each work takes at most 3/4 of a day of magic per day — the wait remains, magic only moves it ahead. The city needs a "
        "builder and the people cannot be miserable. Arcane: level 18, Circle 3."),
    "hint.isekai_mahou.city_magic_build": (
        "Magia de obra: ponha o efeito Impulso de Obra numa magia e conjure perto da obra — os construtores adiantam um pouco do dia.",
        "Building magic: put the Work Surge effect in a spell and cast it near the site — the builders get a little ahead of the day."),
}


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    added = 0
    for k, v in list(T.items()) + []:
        added += (P + k) not in data
        data[P + k] = v[idx]
    for k, v in RAW.items():
        added += k not in data
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d chaves do ritmo da cidade (%d novas)" % (fname, len(T) + len(RAW), added))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
