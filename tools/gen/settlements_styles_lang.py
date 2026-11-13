"""Onda 5 (city_styles) — traduções dos 4 estilos novos de cidade (Oriental, Élfico, Sombrio, Anão) e da Reforma.

Uso (na raiz do projeto):  py -I tools/gen/settlements_styles_lang.py

Junta (e sobrescreve) as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json sem mexer nas outras.
Rode DEPOIS de tools/gen/settlements_lang.py e settlements_v14_lang.py se eles forem rodados de novo: os nomes dos
prédios de cada estilo novo (bp.<estilo>.<chave>) são COPIADOS do estilo de origem que já está no arquivo (Reino para
Oriental/Élfico/Sombrio, Nórdico para Anão) e só alguns ganham nome próprio (NAMES).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# estilo novo -> estilo de onde vêm os textos dos prédios
SOURCE = {"oriental": "kingdom", "elven": "kingdom", "gothic": "kingdom", "dwarven": "nordic"}

# chave: (pt_br, en_us)
T = {
    # ---- os estilos
    "style.oriental": ("Oriental", "Eastern"),
    "style.oriental.desc": (
        "Telhados curvos de telha escura, madeira vermelha, janelas de papel, cerejeiras na praça e um portão torii em "
        "cada muralha. Uma cidade de harmonia e comércio.",
        "Curved dark-tile roofs, red timber, paper windows, cherry trees in the plaza and a torii gate on every wall. "
        "A city of harmony and trade."),
    "style.oriental.bonus": (
        "Bônus: +1 de felicidade por dia e +15% de impostos. Ponto fraco: obras 15% mais lentas e guardas de armadura "
        "leve (+2).",
        "Bonus: +1 happiness per day and +15% taxes. Weak point: building is 15% slower and guards wear light armor (+2)."),
    "style.elven": ("Élfico", "Elven"),
    "style.elven.desc": (
        "Madeira clara, pedra com musgo, telhados verdes tomados por folhas e luzes suaves. A cidade parece ter brotado "
        "da floresta.",
        "Pale wood, mossy stone, green roofs overgrown with leaves and soft lights. The city seems to have grown out of "
        "the forest."),
    "style.elven.bonus": (
        "Bônus: +30% de comida e metade do gasto de combustível. Ponto fraco: cada degrau pede 25% mais prosperidade e "
        "os guardas não usam armadura.",
        "Bonus: +30% food and half the fuel use. Weak point: each rank needs 25% more prosperity and guards wear no "
        "armor."),
    "style.gothic": ("Sombrio", "Gothic"),
    "style.gothic.desc": (
        "Pedra escura, telhados pontudos com agulhas de ferro, vitrais roxos e lanternas de alma. Imponente e muito bem "
        "guardada.",
        "Dark stone, pointed roofs with iron spires, purple stained glass and soul lanterns. Imposing and very well "
        "guarded."),
    "style.gothic.bonus": (
        "Bônus: guardas com +6 de armadura e +30% de segurança. Ponto fraco: −1 de felicidade por dia e hordas com "
        "esqueletos wither a partir do Burgo.",
        "Bonus: guards with +6 armor and +30% security. Weak point: −1 happiness per day and raids bring wither "
        "skeletons from Town rank on."),
    "style.dwarven": ("Anão", "Dwarven"),
    "style.dwarven.desc": (
        "Pedra talhada com faixas de cobre, telhados de cobre e braseiros de forja na praça. Feita para durar mil anos.",
        "Cut stone with copper bands, copper roofs and forge braziers in the plaza. Built to last a thousand years."),
    "style.dwarven.bonus": (
        "Bônus: minas rendem o dobro (e dão cobre), obras 20% mais rápidas e guardas com +4 de armadura. Ponto fraco: "
        "−25% de comida, +25% de combustível e cada degrau pede 10% mais prosperidade.",
        "Bonus: mines yield double (plus copper), building is 20% faster and guards get +4 armor. Weak point: −25% food, "
        "+25% fuel and each rank needs 10% more prosperity."),

    # ---- felicidade
    "happy.f.style": ("Jeito da cidade (estilo)", "The city's way (style)"),

    # ---- falas dos moradores
    "line.resident.oriental.0": ("As cerejeiras vão florir cedo este ano.", "The cherry trees will bloom early this year."),
    "line.resident.oriental.1": ("Um chá quente resolve quase tudo.", "A hot cup of tea solves almost anything."),
    "line.resident.oriental.2": ("Passe pelo torii com respeito, viajante.", "Walk through the torii with respect, traveler."),
    "line.resident.elven.0": ("A floresta nos empresta este lugar.", "The forest lends us this place."),
    "line.resident.elven.1": ("Ouviu? As folhas estão cantando hoje.", "Hear that? The leaves are singing today."),
    "line.resident.elven.2": ("Não temos pressa. As árvores também não.", "We are in no hurry. Neither are the trees."),
    "line.resident.gothic.0": ("Bela noite... todas são, por aqui.", "Lovely night... they all are, around here."),
    "line.resident.gothic.1": ("As lanternas de alma espantam o que vem do escuro.", "The soul lanterns keep away what comes from the dark."),
    "line.resident.gothic.2": ("Os sinos tocaram sozinhos de novo.", "The bells rang on their own again."),
    "line.resident.dwarven.0": ("Pedra boa não mente!", "Good stone never lies!"),
    "line.resident.dwarven.1": ("A forja não apaga desde o meu avô.", "The forge has not gone cold since my grandfather's day."),
    "line.resident.dwarven.2": ("Hoje tem cerveja escura no salão.", "There's dark ale in the hall tonight."),

    # ---- Reforma de estilo
    "reform.same": ("A cidade já é desse estilo.", "The city already has that style."),
    "reform.busy": ("Espere a reforma do centro e das muralhas terminar antes de trocar o estilo.",
                    "Wait for the center and wall works to finish before changing the style."),
    "reform.poor": ("A reforma custa %s moedas do cofre (há %s).", "This renovation costs %s coins from the treasury (it holds %s)."),
    "reform.started": ("%s agora é uma cidade de estilo %s!", "%s is now a %s city!"),
    "reform.started.info": (
        "Reforma de estilo: os construtores refazem a praça, as ruas, os postes e as muralhas aos poucos. Os prédios "
        "que já existem ficam como estão — reforme um por um no Planejamento quando quiser.",
        "Style renovation: the builders redo the plaza, streets, lamps and walls little by little. Existing buildings "
        "stay as they are — renovate them one by one in the planner whenever you like."),
    "reform.b.same": ("Este prédio já é do estilo da cidade.", "This building already matches the city's style."),
    "reform.b.unique": ("Este prédio só existe no estilo em que foi feito.", "This building only exists in the style it was built in."),
    "reform.b.queued": ("Reforma na fila: %s no estilo %s", "Renovation queued: %s in %s style"),
    "reform.b.done": ("Reforma pronta: %s", "Renovation finished: %s"),
    "reform.work": ("Reforma de estilo: %s", "Style renovation: %s"),
    "ui.reform.title": ("Reforma de estilo", "Style renovation"),
    "ui.reform.go": ("Reformar para %s (%s moedas)", "Renovate to %s (%s coins)"),
    "ui.reform.confirm": ("Confirmar: %s por %s moedas?", "Confirm: %s for %s coins?"),
    "ui.reform.tip": (
        "Troca o estilo da cidade (bônus e ponto fraco, moradores, ruas, praça e muralhas). Custa caro e a obra é "
        "gradual. Nada do que você construiu à mão é apagado; os prédios prontos ficam no estilo antigo até você "
        "reformar cada um.",
        "Changes the city's style (bonus and weak point, residents, streets, plaza and walls). It is expensive and the "
        "work is gradual. Nothing you built by hand is erased; finished buildings keep the old style until you "
        "renovate each one."),
    "ui.reform.pending": ("%s prédios ainda no estilo antigo (reforme no Planejamento).",
                          "%s buildings still in the old style (renovate them in the planner)."),
    "ui.reform.building": ("Reformar no estilo %s (%s moedas)", "Renovate in %s style (%s coins)"),
    "ui.reform.building.tip": (
        "Refaz este prédio no estilo da cidade, no mesmo nível. O terreno é limpo como numa melhoria: esvazie os baús antes.",
        "Rebuilds this building in the city's style at the same level. The plot is cleared like an upgrade: empty the "
        "chests first."),
    "cmd.style.now": ("Estilo: %s — Reforma: %s moedas — prédios em outro estilo: %s",
                      "Style: %s — renovation: %s coins — buildings in another style: %s"),
}

RAW = {
    "hint.isekai_mahou.city_reform": (
        "Reforma começou! Praça, ruas e muralhas mudam aos poucos. Para os prédios, abra o Planejamento e reforme um por um.",
        "Renovation started! Plaza, streets and walls change little by little. For buildings, open the planner and "
        "renovate them one by one."),
}

# nomes próprios de alguns prédios em cada estilo novo (o resto é igual ao do estilo de origem)
NAMES = {
    "oriental": {
        "temple": ("Santuário", "Shrine"),
        "tavern": ("Casa de Chá", "Tea House"),
        "town_hall": ("Palácio do Magistrado", "Magistrate's Hall"),
        "barracks": ("Dojo", "Dojo"),
        "arch": ("Portal Torii", "Torii Gate"),
        "watchtower": ("Torre de Vigia", "Watchtower"),
    },
    "elven": {
        "temple": ("Bosque Sagrado", "Sacred Grove"),
        "tavern": ("Salão das Canções", "Hall of Songs"),
        "town_hall": ("Salão do Conselho", "Council Hall"),
        "barracks": ("Casa dos Sentinelas", "Sentinel Lodge"),
        "watchtower": ("Mirante", "Lookout"),
    },
    "gothic": {
        "temple": ("Catedral", "Cathedral"),
        "tavern": ("Taverna do Corvo", "The Raven Tavern"),
        "town_hall": ("Solar do Conde", "Count's Manor"),
        "watchtower": ("Torre do Sino", "Bell Tower"),
    },
    "dwarven": {
        "temple": ("Salão dos Ancestrais", "Hall of Ancestors"),
        "tavern": ("Cervejaria", "Brewhall"),
        "town_hall": ("Salão do Thane", "Thane's Hall"),
        "barracks": ("Salão dos Escudos", "Shield Hall"),
        "smithy": ("Grande Forja", "Great Forge"),
        "mine": ("Mina Profunda", "Deep Mine"),
    },
}


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    copied = 0
    for style, src in SOURCE.items():
        pre = P + "bp." + src + "."
        keys = [k for k in data if k.startswith(pre)]
        if not keys:
            raise SystemExit("faltam as traduções de prédio do estilo " + src + " em " + fname)
        for k in keys:
            data[P + "bp." + style + "." + k[len(pre):]] = data[k]
            copied += 1
        for key, names in NAMES[style].items():
            data[P + "bp." + style + "." + key] = names[idx]
    for k, v in T.items():
        data[P + k] = v[idx]
    for k, v in RAW.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d chaves de estilo + %d nomes de prédio copiados" % (fname, len(T) + len(RAW), copied))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
