"""Onda 8 (city_looks) — traduções do VISUAL NOVO dos prédios e degraus ("tem que ter mudanças mais significativas
visuais do lvl 1 pro 2... até o 5... tem que ser bem diferente").

Uso (na raiz do projeto):  py -I tools/gen/settlements_w8_looks_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
O gerador da cidade da água (settlements_w8_water_lang.py) mexe no mesmo JSON: se o arquivo mudar entre ler e gravar,
este script relê e grava de novo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (sem o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- planejador
    "ui.looks.next": ("Visual no nível %s: %s", "Level %s look: %s"),
    "ui.looks.first": ("Começa como: %s", "Starts as: %s"),
    "ui.looks.redo": ("Refazer no visual novo (%s moedas)", "Rebuild in the new look (%s coins)"),
    "ui.looks.redo.tip": (
        "Este prédio foi erguido antes do visual novo dos níveis. Os construtores o refazem no mesmo nível e estilo. "
        "Esvazie os baús do lote antes.",
        "This building went up before the new level looks. The builders rebuild it at the same level and style. "
        "Empty the lot's chests first."),
    "looks.redo.queued": ("%s será refeito no visual novo", "%s will be rebuilt in the new look"),
    "looks.redo.same": ("Este prédio já está no visual novo", "This building already has the new look"),
    "looks.work": ("Visual novo: %s", "New look: %s"),
    # ---- comandos de teste
    "cmd.looks.info": (
        "Prédios: %s — ainda no visual antigo: %s. Melhore, reforme, ou fique no lote e use /mahou city looks redo.",
        "Buildings: %s — still in the old look: %s. Upgrade, restyle, or stand on the lot and run /mahou city looks redo."),
    "cmd.showcase.done": (
        "Vitrine de %s: %s blocos. Os níveis vão do 1 (oeste) ao último (leste). /mahou city showcase clear tira tudo.",
        "Showcase of %s: %s blocks placed. Levels run from 1 (west) to the last (east). /mahou city showcase clear removes it."),
    "cmd.showcase.cleared": ("Vitrine removida (%s blocos)", "Showcase removed (%s blocks)"),
    "cmd.showcase.unknown": (
        "Prédio desconhecido: %s (use a chave da planta: house_small, farm, smithy, temple... ou wall)",
        "Unknown building: %s (use the blueprint key: house_small, farm, smithy, temple... or wall)"),
    "cmd.showcase.where": (
        "Fique fora de qualquer cidade ou lugar protegido (e fora do Lar) para montar a vitrine",
        "Stand outside any city or protected place (and outside the Home Realm) to set up the showcase"),
}

# o que muda no visual de cada nível: grupo -> [(pt, en) do nível 1..n]
LOOKS = {
    "house": [
        ("choupana de troncos, sem vidro, com cumeeira de palha", "a log hut with unglazed windows and a thatched ridge"),
        ("chalé com base de pedra, janelas de vidro, chaminé e quintal cercado",
         "a cottage on a stone base with glass windows, a chimney and a fenced yard"),
        ("sobrado de pedra lavrada com telhado novo e muro baixo", "a two-storey house of dressed stone with a new roof and a low wall"),
        ("andar de cima rebocado, vitrais, sacada e jardim murado", "a plastered upper floor, stained glass, a balcony and a walled garden"),
        ("casarão nobre de três andares com torreão e pátio calçado", "a noble three-storey manor with a turret and a paved courtyard"),
    ],
    "tavern": [
        ("barraca de troncos com mesas ao ar livre", "a log shack with tables outside"),
        ("casa de madeira sobre pedra, com barris e mais mesas", "a timber house on stone, with barrels and more tables"),
        ("estalagem de dois andares com placa e lanterna", "a two-storey inn with a sign and lantern"),
        ("estalagem com sacada, vitrais e varanda de mesas", "an inn with a balcony, stained glass and a terrace of tables"),
        ("hospedaria nobre de três andares com torreão", "a noble three-storey hostelry with a turret"),
    ],
    "builders_hall": [
        ("telheiro com bancada e pilhas de troncos", "a lean-to with a workbench and log piles"),
        ("galpão de madeira com base de pedra", "a timber shed on a stone base"),
        ("oficina de pedra com guindaste", "a stone workshop with a crane"),
        ("dois andares e pátio de materiais", "two storeys and a materials yard"),
        ("sede da guilda com torre e guindaste alto", "a guild seat with a tower and a tall crane"),
    ],
    "town_hall": [
        ("salão de madeira com sino no poste", "a timber hall with a bell on a post"),
        ("salão com base de pedra e estantes", "a hall on a stone base, with bookshelves"),
        ("paço de dois andares com torre do sino", "a two-storey hall with a bell tower"),
        ("paço largo com colunata e vitrais", "a wide hall with a colonnade and stained glass"),
        ("palácio de três andares com torre alta e estandartes", "a three-storey palace with a tall tower and banners"),
    ],
    "farm": [
        ("canteiro e espantalho", "a crop patch and a scarecrow"),
        ("roça cercada com telheiro de ferramentas", "a fenced field with a tool shelter"),
        ("celeiro pequeno e cercado dos animais", "a small barn and an animal pen"),
        ("celeiro de dois andares com silo", "a two-storey barn with a silo"),
        ("celeiro grande, silo alto e moinho de vento", "a big barn, a tall silo and a windmill"),
    ],
    "market": [
        ("duas bancas de lona", "two canvas stalls"),
        ("quatro bancas em volta do poço", "four stalls around a well"),
        ("pavilhão coberto com bancas", "a roofed pavilion with stalls"),
        ("pavilhão largo com torre do sino", "a wide pavilion with a bell tower"),
        ("grande arcada de dois andares com torre", "a grand two-storey arcade with a tower"),
    ],
    "barracks": [
        ("barracas de lona, fogueira e bonecos de treino", "canvas tents, a campfire and training dummies"),
        ("alojamento de madeira com pátio de treino", "a timber bunkhouse with a training yard"),
        ("quartel de pedra de dois andares", "a two-storey stone barracks"),
        ("quartel com torre de canto e estandarte", "a barracks with a corner tower and banner"),
        ("fortaleza ameada com duas torres na frente", "a crenellated fortress with two front towers"),
    ],
    "watchtower": [
        ("plataforma de troncos com escada", "a log platform with a ladder"),
        ("torre de madeira mais alta, com telhado", "a taller timber tower with a roof"),
        ("torre de pedra ameada", "a crenellated stone tower"),
        ("torre alta com balcão de tiro e telhado", "a tall tower with a firing gallery and roof"),
        ("grande torre com farol no topo", "a great tower with a beacon on top"),
    ],
    "smithy": [
        ("bancada sob telheiro", "a workbench under a lean-to"),
        ("forja aberta com chaminé de pedra", "an open forge with a stone chimney"),
        ("forja fechada com bigorna e rebolo na frente", "a closed forge with an anvil and grindstone out front"),
        ("forja de dois andares com suportes de armas", "a two-storey forge with weapon racks"),
        ("forja de dois andares com chaminé dupla e pátio de armas", "a two-storey forge with twin chimneys and a weapons yard"),
    ],
    "stable": [
        ("amarradouro com feno e cocho", "a hitching rail with hay and a trough"),
        ("baias abertas sob telheiro", "open stalls under a lean-to"),
        ("estábulo coberto", "a roofed stable"),
        ("estábulo com palheiro em cima e piquete", "a stable with a hayloft and a paddock"),
        ("grande estábulo com torreão e piquete", "a large stable with a turret and paddock"),
    ],
    "well": [
        ("poço com moldura de troncos", "a well with a log frame"),
        ("poço de pedra com telhadinho", "a stone well with a small roof"),
        ("poço coberto com bancos", "a roofed well with benches"),
        ("casa do poço com pilares e cochos", "a well house with pillars and troughs"),
        ("pavilhão da cisterna com chafariz", "a cistern pavilion with a fountain"),
    ],
    "garden": [
        ("canteiro de flores", "a flower patch"),
        ("canteiros em fileiras com banco", "rows of flower beds with a bench"),
        ("jardim de sebes com caminhos e árvores", "a hedge garden with paths and trees"),
        ("pérgola e laguinho", "a pergola and a pond"),
        ("jardim nobre com chafariz, coreto e topiaria", "a formal garden with a fountain, gazebo and topiary"),
    ],
    # plantas que já eram procedurais (biblioteca, celeiro, guilda, torre de magia...)
    "proc": [
        ("o prédio básico, sem cercado", "the basic building, no enclosure"),
        ("ganha quintal cercado e enfeites", "gains a fenced yard and trim"),
        ("prédio maior, de pedra lavrada, com muro baixo", "a bigger building of dressed stone with a low wall"),
        ("mais alto e rico, com jardim murado", "taller and richer, with a walled garden"),
        ("o maior, no material nobre do estilo", "the largest, in the style's noble material"),
    ],
    # monumentos (3 níveis)
    "monument": [
        ("o monumento básico", "the basic monument"),
        ("bem maior e mais alto", "much bigger and taller"),
        ("a forma mais rica, com ouro e luzes", "its richest form, with gold and lights"),
    ],
}
# chaves fora do prefixo do módulo (dicas do ui_hud)
RAW = {
    "hint.isekai_mahou.city_looks": (
        "Cada nível agora muda o prédio inteiro. Os prédios antigos ganham o visual novo na próxima melhoria — ou em "
        "\"Refazer no visual novo\", no planejamento.",
        "Each level now changes the whole building. Older buildings get the new look on their next upgrade, or with "
        "\"Rebuild in the new look\" in the planner."),
}

for group, rows in LOOKS.items():
    for i, row in enumerate(rows):
        T["looks.%s.%d" % (group, i + 1)] = row


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    for attempt in range(8):
        with open(path, "rb") as f:
            raw = f.read()
        data = json.loads(raw.decode("utf-8"))
        added = 0
        for k, v in T.items():
            added += (P + k) not in data
            data[P + k] = v[idx]
        for k, v in RAW.items():
            added += k not in data
            data[k] = v[idx]
        out = (json.dumps(dict(sorted(data.items())), ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        # outro gerador gravou enquanto este montava o texto? relê e tenta de novo (nunca grava por cima do que não leu)
        with open(path, "rb") as f:
            if f.read() != raw:
                continue
        with open(path, "wb") as f:
            f.write(out)
        print("%s: %d chaves do visual novo (%d novas)" % (fname, len(T) + len(RAW), added))
        return
    raise SystemExit("%s mudou o tempo todo — rode de novo" % fname)


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
