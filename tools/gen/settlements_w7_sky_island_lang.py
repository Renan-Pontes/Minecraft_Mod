"""Onda 7 (sky_island) — traduções da ILHA CELESTE (SkyLand): fundação no céu, terra se formando, Núcleo Celeste, comandos.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w7_sky_island_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
Os textos da TELA de fundação, do painel e do acesso à ilha são do estágio 2 (settlements_w7_sky_lang.py e os outros
geradores w7): este arquivo só tem o que o estágio 1 usa.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- tipo de terreno (o campo novo do pacote Found: "ground" / "skyland")
    "sky.terrain.ground": ("No chão", "On the ground"),
    "sky.terrain.skyland": ("Ilha Celeste (SkyLand)", "Sky Island (SkyLand)"),
    # ---- fundação no céu
    "sky.found.too_high": ("O terreno aqui é alto demais para uma Ilha Celeste. Escolha um lugar mais baixo.",
                           "The land here is too high for a Sky Island. Pick a lower spot."),
    "sky.found.blocked": ("Já existe algo no céu acima deste ponto. A Ilha Celeste precisa de céu livre.",
                          "Something is already in the sky above this spot. A Sky Island needs open sky."),
    "sky.found.no_core": ("Fundar no céu pede um Núcleo Celeste no inventário.",
                          "Founding in the sky takes a Sky Core in your inventory."),
    "sky.found.announce": ("%s fundou %s (%s), uma cidade nas nuvens!", "%s founded %s (%s), a city in the clouds!"),
    "sky.found.forming": ("A terra está se formando no céu — você sobe quando a praça existir.",
                          "The land is forming in the sky — you go up once the plaza exists."),
    "sky.lifted": ("Bem-vindo a %s. A ilha continua crescendo à sua volta.", "Welcome to %s. The island keeps growing around you."),
    # ---- a ilha cresce com a cidade
    "sky.growing": ("A ilha de %s está ganhando terra nova.", "The island of %s is gaining new land."),
    "sky.grown": ("A terra nova de %s terminou de se formar.", "The new land of %s has finished forming."),
    "sky.formed": ("A ilha de %s está completa.", "The island of %s is complete."),
    "sky.expand.blocked": ("A ilha não pode crescer: há outro lugar do céu no caminho.",
                           "The island cannot grow: another place in the sky is in the way."),
    # ---- Diário da cidade
    "sky.diary.formed": ("A Ilha Celeste terminou de se formar.", "The Sky Island has finished forming."),
    "sky.diary.grow": ("A ilha começou a crescer: um anel novo de terra se forma na borda.",
                       "The island began to grow: a new ring of land is forming at the rim."),
    "sky.diary.grown": ("A terra nova terminou de se formar: os terrenos de fora já têm chão.",
                        "The new land has finished forming: the outer plots have ground now."),
    # ---- obras esperando o chão
    "sky.sign.land": ("%s — aguardando a terra", "%s — waiting for the land"),
    "sky.wait.land": ("Aguardando a terra se formar", "Waiting for the land to form"),
    # ---- comandos de teste
    "cmd.sky.only": ("Nenhuma Ilha Celeste por perto. Para criar uma: /mahou city sky found [estilo]",
                     "No Sky Island nearby. To create one: /mahou city sky found [style]"),
    "cmd.sky.ground": ("%s é uma cidade no chão.", "%s is a ground city."),
    "cmd.sky.done": ("%s — Ilha Celeste · altitude %s · terra completa (raio %s, borda a até %s blocos do Coração)",
                     "%s — Sky Island · altitude %s · land complete (radius %s, rim up to %s blocks from the Heart)"),
    "cmd.sky.state": ("%s — Ilha Celeste · altitude %s · terra pronta: raio %s · formando o raio %s: anel %s de %s (%s%%) · anel pulado: %s",
                      "%s — Sky Island · altitude %s · land ready: radius %s · forming radius %s: ring %s of %s (%s%%) · skipped ring: %s"),
    "cmd.sky.finished": ("Ilha completa: %s blocos colocados agora.", "Island complete: %s blocks placed now."),
    "cmd.sky.partial": ("%s blocos colocados, mas faltam colunas em chunks não carregados — chegue mais perto da borda.",
                        "%s blocks placed, but columns in unloaded chunks are missing — get closer to the rim."),
    "cmd.sky.no_land": ("A praça da ilha ainda não existe.", "The island's plaza does not exist yet."),
}

# chaves sem o prefixo do módulo
RAW = {
    "item.isekai_mahou.sky_core": ("Núcleo Celeste", "Sky Core"),
    "item.isekai_mahou.sky_core.desc": ("Um pedaço de céu preso em cristal: sustenta uma ilha inteira no ar.",
                                        "A piece of sky caught in crystal: it holds a whole island in the air."),
    "item.isekai_mahou.sky_core.use": (
        "Com ele e a Carta de Fundação no inventário, sua cidade pode nascer numa Ilha Celeste (SkyLand). É gasto na fundação.",
        "With it and the Founding Charter in your inventory, your city can be born on a Sky Island (SkyLand). Spent on founding."),
    "hint.isekai_mahou.city_sky_found": (
        "Sua cidade nasceu numa Ilha Celeste! A terra se forma aos poucos: cada obra começa quando o chão do lote existir.",
        "Your city was born on a Sky Island! The land forms little by little: each work starts once its plot has ground."),
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
    print("%s: %d chaves da Ilha Celeste (%d novas)" % (fname, len(T) + len(RAW), added))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
