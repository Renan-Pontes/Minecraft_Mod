"""Onda 6 (city_monuments) — traduções dos MONUMENTOS COM BÔNUS: nomes dos bônus, linhas de efeito, a sub-aba "Bônus",
a bênção diária, os monumentos novos (todos os estilos) e a maravilha de cada estilo que não tinha.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w6_monuments_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). Rode de novo DEPOIS de qualquer gerador antigo do módulo
(settlements_lang.py, settlements_v14_lang.py, settlements_styles_lang.py): este aqui também reescreve a descrição da
Estátua e do Arco (agora monumentos com bônus) nos 7 estilos.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."
STYLES = ["kingdom", "desert", "nordic", "oriental", "elven", "gothic", "dwarven"]

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- nome do bônus de cada monumento
    "mon.name.statue": ("Memória dos Heróis", "Memory of Heroes"),
    "mon.name.arch": ("Passo do Viajante", "Traveler's Stride"),
    "mon.name.temple": ("Graça da Luz", "Grace of Light"),
    "mon.name.temple_fire": ("Bênção do Fogo", "Blessing of Fire"),
    "mon.name.temple_water": ("Bênção da Água", "Blessing of Water"),
    "mon.name.temple_earth": ("Bênção da Terra", "Blessing of Earth"),
    "mon.name.temple_wind": ("Bênção do Vento", "Blessing of Wind"),
    "mon.name.temple_light": ("Bênção da Luz", "Blessing of Light"),
    "mon.name.temple_shadow": ("Bênção da Sombra", "Blessing of Shadow"),
    "mon.name.temple_arcane": ("Bênção Arcana", "Arcane Blessing"),
    "mon.name.magic_tower": ("Saber da Torre", "Tower Lore"),
    "mon.name.mana_spire": ("Fonte de Mana", "Mana Spring"),
    "mon.name.obelisk": ("Bênção do Sol", "Sun's Blessing"),
    "mon.name.pyramid": ("Tesouro do Faraó", "Pharaoh's Treasure"),
    "mon.name.sphinx": ("Olhar da Esfinge", "Sphinx's Gaze"),
    "mon.name.castle": ("Estandarte Real", "Royal Standard"),
    "mon.name.eras_obelisk": ("Sabedoria das Eras", "Wisdom of Ages"),
    "mon.name.guardian_colossus": ("Vigília do Colosso", "Colossus' Vigil"),
    "mon.name.great_lighthouse": ("Luz do Farol", "Lighthouse Beam"),
    "mon.name.hanging_garden": ("Fartura do Jardim", "Garden's Bounty"),
    "mon.name.mead_hall": ("Coragem do Salão", "Hall's Courage"),
    "mon.name.grand_pagoda": ("Serenidade do Pagode", "Pagoda's Serenity"),
    "mon.name.world_tree": ("Seiva da Árvore-Mãe", "Mother Tree's Sap"),
    "mon.name.grand_cathedral": ("Véu da Catedral", "Cathedral's Veil"),
    "mon.name.great_forge": ("Têmpera da Forja", "Forge's Temper"),
    # ---- linhas de efeito
    "mon.fx": ("%s: %s", "%s: %s"),
    "mon.stat.xp": ("+%s%% de XP", "+%s%% XP"),
    "mon.stat.element_xp": ("+%s%% de maestria de %s", "+%s%% %s mastery"),
    "mon.stat.element_xp_all": ("+%s%% de maestria de elemento", "+%s%% element mastery"),
    "mon.stat.spell": ("+%s%% de dano de %s", "+%s%% %s damage"),
    "mon.stat.spell_all": ("+%s%% de dano de toda magia", "+%s%% damage for every spell"),
    "mon.stat.max_mana": ("+%s%% de mana máxima", "+%s%% max mana"),
    "mon.stat.mana_regen": ("+%s%% de regeneração de mana", "+%s%% mana regeneration"),
    "mon.stat.heal": ("cura %s%% da vida a cada 10 s", "heals %s%% of health every 10 s"),
    "mon.stat.armor": ("+%s de armadura", "+%s armor"),
    "mon.stat.health": ("+%s%% de vida máxima", "+%s%% max health"),
    "mon.stat.speed": ("+%s%% de velocidade", "+%s%% speed"),
    "mon.stat.luck": ("+%s de sorte", "+%s luck"),
    "mon.stat.satiety": ("+%s de fome por minuto", "+%s hunger per minute"),
    "mon.stat.tax": ("+%s%% de impostos", "+%s%% taxes"),
    "mon.stat.security": ("+%s de segurança", "+%s security"),
    "mon.stat.food": ("+%s%% de comida", "+%s%% food"),
    "mon.stat.approval": ("+%s de felicidade/dia", "+%s happiness/day"),
    "mon.stat.caravan": ("+%s%% de lucro das caravanas", "+%s%% caravan profit"),
    # ---- regras e avisos
    "mon.gain": ("Até %s monumento(s) ativo(s) ao mesmo tempo", "Up to %s active monument(s) at once"),
    "mon.max_level": ("Monumentos vão até o nível %s.", "Monuments go up to level %s."),
    "mon.toggle.on": ("%s consagrado: %s vale a partir de agora.", "%s consecrated: %s is now in effect."),
    "mon.toggle.off": ("%s adormeceu: o bônus dele parou.", "%s is dormant: its bonus stopped."),
    "mon.toggle.full": ("O degrau deixa %s monumento(s) ativo(s) (%s). Adormeça um antes.",
                        "This rank allows %s active monument(s) (%s). Put one to sleep first."),
    "mon.toggle.wait": ("Foi consagrado hoje: só pode adormecer depois do amanhecer.",
                        "Consecrated today: it can only go dormant after dawn."),
    "mon.toggle.damaged": ("Conserte o monumento antes de consagrar.", "Repair the monument before consecrating it."),
    "mon.done.dormant": ("%s está pronto, mas adormecido: a cidade já tem %s monumento(s) ativo(s), o limite de %s. Troque em Geral → Bônus.",
                         "%s is finished but dormant: the city already has %s active monument(s), the limit for a %s. Swap in Overview → Bonuses."),
    "mon.diary.active": ("%s consagrado — %s: %s", "%s consecrated — %s: %s"),
    "mon.enter": ("Bênçãos de %s: %s", "Blessings of %s: %s"),
    "mon.bless.got": ("Bênção recebida — %s: %s (vale por um dia, também fora da cidade)",
                      "Blessing received — %s: %s (lasts one day, outside the city too)"),
    "mon.bless.done": ("Você já recebeu uma bênção hoje. Volte depois do amanhecer.",
                       "You already received a blessing today. Come back after dawn."),
    "mon.bless.member": ("Só quem a cidade protege recebe a bênção.", "Only those the city protects receive the blessing."),
    "mon.bless.dormant": ("Este monumento está adormecido — não dá bênção.", "This monument is dormant — it gives no blessing."),
    # ---- painel: Geral → Bônus
    "ui.page.bonus": ("Bônus", "Bonuses"),
    "ui.page.bonus.tip": ("Monumentos e templos: os bônus ativos da cidade e a sua bênção.",
                          "Monuments and temples: the city's active bonuses and your blessing."),
    "ui.mon.title": ("Monumentos ativos: %s de %s", "Active monuments: %s of %s"),
    "ui.mon.limit": ("limite do degrau", "rank limit"),
    "ui.mon.limit.tip1": ("Quantos monumentos valem ao mesmo tempo", "How many monuments work at once"),
    "ui.mon.limit.tip2": ("Vila 1 · Burgo 2 · Cidade 3 · Capital 5", "Village 1 · Town 2 · City 3 · Capital 5"),
    "ui.mon.limit.tip3": ("Os outros ficam adormecidos: escolha quais valem.", "The others stay dormant: choose which ones count."),
    "ui.mon.none": ("Nenhum monumento ainda. No Planejamento, construa uma Estátua, um Arco, um templo ou uma maravilha: cada um "
                    "leva dias e dá um bônus a quem a cidade protege.",
                    "No monuments yet. In Planning, build a Statue, an Arch, a temple or a wonder: each takes days and gives a "
                    "bonus to those the city protects."),
    "ui.mon.sum": ("Na cidade agora: %s", "In the city now: %s"),
    "ui.mon.sum.none": ("Nenhum bônus ativo.", "No active bonus."),
    "ui.mon.bless": ("Sua bênção: %s — %s (%s min)", "Your blessing: %s — %s (%s min)"),
    "ui.mon.bless.ready": ("Bênção de hoje disponível: agache e toque um monumento ativo com a mão vazia.",
                           "Today's blessing is available: sneak and touch an active monument with an empty hand."),
    "ui.mon.bless.used": ("Bênção de hoje já recebida.", "Today's blessing already received."),
    "ui.mon.next": ("Nível %s: %s", "Level %s: %s"),
    "ui.mon.carried": ("Bênção levada (1 dia): %s", "Carried blessing (1 day): %s"),
    "ui.mon.damaged": ("Danificado: conserte para o bônus voltar.", "Damaged: repair it to bring the bonus back."),
    "ui.mon.on": ("Ativo", "Active"),
    "ui.mon.off": ("Adormecido", "Dormant"),
    "ui.mon.tip.planner": ("Só quem planeja a cidade troca os monumentos ativos.", "Only city planners swap the active monuments."),
    "ui.mon.tip.locked": ("Consagrado hoje: fica ativo até o amanhecer.", "Consecrated today: stays active until dawn."),
    "ui.mon.tip.on": ("Clique para adormecer (libera a vaga para outro).", "Click to make it dormant (frees the slot for another)."),
    "ui.mon.tip.full": ("Sem vaga: o degrau deixa %s ativo(s). Adormeça outro antes.",
                        "No slot: this rank allows %s active. Put another to sleep first."),
    "ui.mon.tip.off": ("Clique para consagrar: o bônus vale na hora.", "Click to consecrate: the bonus starts right away."),
    # ---- felicidade
    "happy.f.monuments": ("Monumentos", "Monuments"),
    # ---- comandos de teste
    "cmd.mon": ("Monumentos de %s: %s ativos de %s (%s)", "Monuments of %s: %s active of %s (%s)"),
    "cmd.mon.you": ("Valendo para você agora:%s", "In effect for you now:%s"),
    "cmd.mon.cleared": ("Bênção apagada (dá para receber outra hoje).", "Blessing cleared (you can receive another today)."),
}

# nome e descrição dos monumentos novos (iguais em todos os estilos)
COMMON = {
    "hanging_garden": (
        "Jardim Suspenso", "Hanging Garden",
        "MONUMENTO — Fartura do Jardim: a cidade colhe mais e fica mais feliz; quem ela protege não passa fome lá dentro. 3 níveis.",
        "MONUMENT — Garden's Bounty: the city harvests more and is happier; those it protects never go hungry inside. 3 levels."),
    "eras_obelisk": (
        "Obelisco das Eras", "Obelisk of Ages",
        "MONUMENTO — Sabedoria das Eras: mais XP e mais maestria de elemento para quem a cidade protege. 3 níveis.",
        "MONUMENT — Wisdom of Ages: more XP and more element mastery for those the city protects. 3 levels."),
    "great_lighthouse": (
        "Grande Farol", "Great Lighthouse",
        "MONUMENTO — Luz do Farol: as caravanas rendem mais, entram mais impostos e o passo fica mais leve na cidade. 3 níveis.",
        "MONUMENT — Lighthouse Beam: caravans earn more, taxes rise and you move faster in the city. 3 levels."),
    "guardian_colossus": (
        "Colosso do Guardião", "Guardian Colossus",
        "MONUMENTO — Vigília do Colosso: armadura para quem a cidade protege e mais segurança. 3 níveis.",
        "MONUMENT — Colossus' Vigil: armor for those the city protects and more security. 3 levels."),
}

# maravilha de cada estilo que não tinha
WONDERS = {
    "nordic": ("mead_hall", "Salão dos Heróis", "Hall of Heroes",
               "MARAVILHA — Coragem do Salão: mais vida máxima para quem a cidade protege e um povo mais feliz. 3 níveis.",
               "WONDER — Hall's Courage: more max health for those the city protects and a happier people. 3 levels."),
    "oriental": ("grand_pagoda", "Grande Pagode", "Grand Pagoda",
                 "MARAVILHA — Serenidade do Pagode: a mana volta mais rápido e a maestria de elemento cresce mais. 3 níveis.",
                 "WONDER — Pagoda's Serenity: mana returns faster and element mastery grows quicker. 3 levels."),
    "elven": ("world_tree", "Árvore-Mãe", "Mother Tree",
              "MARAVILHA — Seiva da Árvore-Mãe: cura aos poucos quem a cidade protege e a cidade colhe mais. 3 níveis.",
              "WONDER — Mother Tree's Sap: slowly heals those the city protects and the city harvests more. 3 levels."),
    "gothic": ("grand_cathedral", "Catedral das Sombras", "Cathedral of Shadows",
               "MARAVILHA — Véu da Catedral: mais mana máxima para quem a cidade protege e mais segurança. 3 níveis.",
               "WONDER — Cathedral's Veil: more max mana for those the city protects and more security. 3 levels."),
    "dwarven": ("great_forge", "Grande Forja", "Great Forge",
                "MARAVILHA — Têmpera da Forja: armadura para quem a cidade protege e mais impostos. 3 níveis.",
                "WONDER — Forge's Temper: armor for those the city protects and more taxes. 3 levels."),
}

# Estátua e Arco viraram monumentos com bônus: descrição nova nos 7 estilos (o nome de cada estilo não muda)
REWORDED = {
    "statue": ("MONUMENTO — Memória dos Heróis: mais XP para quem a cidade protege, e felicidade todo dia. 3 níveis.",
               "MONUMENT — Memory of Heroes: more XP for those the city protects, and happiness every day. 3 levels."),
    "arch": ("MONUMENTO — Passo do Viajante: passo mais rápido na cidade e caravanas que rendem mais. Um pouco de felicidade. 3 níveis.",
             "MONUMENT — Traveler's Stride: faster movement in the city and caravans that earn more. A little happiness. 3 levels."),
}

for style in STYLES:
    for key, (pt, en, dpt, den) in COMMON.items():
        T["bp.%s.%s" % (style, key)] = (pt, en)
        T["bp.%s.%s.desc" % (style, key)] = (dpt, den)
    for key, (dpt, den) in REWORDED.items():
        T["bp.%s.%s.desc" % (style, key)] = (dpt, den)
for style, (key, pt, en, dpt, den) in WONDERS.items():
    T["bp.%s.%s" % (style, key)] = (pt, en)
    T["bp.%s.%s.desc" % (style, key)] = (dpt, den)

# chaves fora do prefixo do módulo (dicas de primeira vez)
RAW = {
    "hint.isekai_mahou.city_monument": (
        "Monumento pronto! Ativo, ele dá o bônus a quem a cidade protege. Agache e toque nele com a mão vazia para levar a bênção por um dia.",
        "Monument finished! While active it gives its bonus to those the city protects. Sneak and touch it with an empty hand to carry the blessing for a day."),
    "hint.isekai_mahou.city_monument_bless": (
        "Bênção: metade do bônus do monumento, por um dia, também fora da cidade. Uma por dia — escolha bem.",
        "Blessing: half of the monument's bonus, for one day, outside the city too. One per day — choose well."),
}


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
    print("%s: %d chaves dos monumentos (%d novas)" % (fname, len(T) + len(RAW), added))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
