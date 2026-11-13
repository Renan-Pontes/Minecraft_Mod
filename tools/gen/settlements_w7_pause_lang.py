"""Onda 7 (city_pause) — traduções de PAUSAR / RETOMAR / DESISTIR de obras ("quero poder parar as construções no meio...").

Uso (na raiz do projeto):  py -I tools/gen/settlements_w7_pause_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
Outros geradores da onda 7 mexem no mesmo JSON: se o arquivo mudar entre ler e gravar, este script relê e grava de novo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

ONLY_PAUSE_PT = " Você pode pausá-la."
ONLY_PAUSE_EN = " You can pause it."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- aba Obras: botões
    "ui.pause.all": ("Pausar tudo", "Pause all"),
    "ui.pause.all.resume": ("Retomar tudo", "Resume all"),
    "ui.pause.all.tip": ("Pausa todas as obras da fila", "Pauses every work in the queue"),
    "ui.pause.all.resume.tip": ("Retoma todas as obras pausadas", "Resumes every paused work"),
    "ui.pause.tip": ("Pausar esta obra", "Pause this work"),
    "ui.pause.tip2": ("Obra pausada não gasta dias nem materiais, e os construtores vão para as outras. O canteiro fica como está.",
                      "A paused work uses no days or materials, and its builders move to the others. The site stays as it is."),
    "ui.pause.resume.tip": ("Retomar esta obra", "Resume this work"),
    "ui.pause.resume.tip2": ("Ela continua de onde parou.", "It carries on from where it stopped."),
    "ui.pause.state": ("⏸ pausada", "⏸ paused"),
    "ui.pause.state.undo": ("desmontando", "dismantling"),
    "ui.pause.cancel": ("Desistir", "Give up"),
    "ui.pause.cancel.confirm": ("Mesmo?", "Sure?"),
    "ui.pause.cancel.tip": ("Desistir desta obra (clique duas vezes)", "Give up on this work (click twice)"),
    "ui.pause.cancel.confirm.tip": ("Clique de novo para desistir da obra", "Click again to give up on the work"),
    "ui.pause.cancel.no": ("Esta obra só pode ser pausada", "This work can only be paused"),
    "ui.pause.cancel.refund": ("Devolve %s moedas ao cofre", "Returns %s coins to the treasury"),
    # ---- o que o cancelamento faz (dica do botão)
    "pause.cancel.tip.queue": ("A obra ainda não mexeu em nada: ela só sai da fila.",
                               "The work has not touched anything yet: it just leaves the queue."),
    "pause.cancel.tip.terrain": ("Os construtores param e o terreno fica como está.",
                                 "The builders stop and the land stays as it is."),
    "pause.cancel.tip.build": ("Os construtores desmontam o canteiro (só os blocos que eles puseram) e o lote volta a ficar livre.",
                               "The builders take the site down (only the blocks they placed) and the plot is free again."),
    "pause.cancel.tip.wall": ("Os construtores desmontam os trechos já erguidos (só os blocos que eles puseram).",
                              "The builders take down the stretches already raised (only the blocks they placed)."),
    "pause.cancel.tip.upgrade": ("Os construtores desmontam a obra e recolocam o prédio como era, no nível %s.",
                                 "The builders take the work down and put the building back as it was, at level %s."),
    "pause.cancel.tip.restyle": ("Os construtores desmontam a obra e recolocam o prédio como era, no estilo %s.",
                                 "The builders take the work down and put the building back as it was, in the %s style."),
    # ---- por que só dá para pausar
    "pause.no.undo": ("Esta obra já foi cancelada: os construtores estão desmontando o canteiro.",
                      "This work was already cancelled: the builders are taking the site down."),
    "pause.no.plaza": ("A praça é o coração da cidade: não dá para desistir dela." + ONLY_PAUSE_PT,
                       "The plaza is the heart of the city: you cannot give up on it." + ONLY_PAUSE_EN),
    "pause.no.repair": ("Conserto não se cancela: o prédio ficaria danificado." + ONLY_PAUSE_PT,
                        "A repair cannot be cancelled: the building would stay damaged." + ONLY_PAUSE_EN),
    "pause.no.tier": ("A reforma da muralha já foi aplicada em parte: desfazer não é seguro." + ONLY_PAUSE_PT,
                      "The wall renovation is already partly applied: undoing it is not safe." + ONLY_PAUSE_EN),
    "pause.no.reform": ("A cidade já mudou de estilo e a praça precisa ser refeita." + ONLY_PAUSE_PT,
                        "The city has already changed style and the plaza must be redone." + ONLY_PAUSE_EN),
    "pause.no.center": ("A subida de degrau já foi aplicada em parte na praça: desfazer não é seguro." + ONLY_PAUSE_PT,
                        "The rank-up is already partly applied to the plaza: undoing it is not safe." + ONLY_PAUSE_EN),
    "pause.no.moved": ("O prédio está mudando de lugar e o antigo já foi desmontado." + ONLY_PAUSE_PT,
                       "The building is moving and the old one was already taken down." + ONLY_PAUSE_EN),
    "pause.no.index": ("Não existe obra com esse número na fila.", "There is no work with that number in the queue."),
    # ---- avisos de quem clicou
    "pause.paused": ("Obra pausada: %s", "Work paused: %s"),
    "pause.resumed": ("Obra retomada: %s", "Work resumed: %s"),
    "pause.all.paused": ("Obras pausadas: %s", "Works paused: %s"),
    "pause.all.resumed": ("Obras retomadas: %s", "Works resumed: %s"),
    "pause.cancelled": ("Obra cancelada: %s.", "Work cancelled: %s."),
    "pause.cancelled.undo": ("Obra cancelada: %s. Os construtores vão desmontar o canteiro.",
                             "Work cancelled: %s. The builders will take the site down."),
    "pause.cancelled.refund": ("%s moedas voltaram ao cofre.", "%s coins went back to the treasury."),
    "pause.undone": ("Canteiro desmontado: %s.", "Site taken down: %s."),
    "pause.undone.back": ("Canteiro desmontado: %s. Os construtores vão recolocar o prédio como era.",
                          "Site taken down: %s. The builders will put the building back as it was."),
    "pause.help.paused": ("Esta obra está pausada: retome-a para entregar materiais.",
                          "This work is paused: resume it to deliver materials."),
    "pause.magic.paused": ("Esta obra está pausada: a magia de obra não pega nela.",
                           "This work is paused: work magic does not take on it."),
    # ---- placa do canteiro, etapa e resumo da manhã
    "pause.sign": ("%s — Pausada · dia %s de %s", "%s — Paused · day %s of %s"),
    "pause.sign.undo": ("%s — desmontando o canteiro", "%s — taking the site down"),
    "pause.stage.undo": ("Desmontando o canteiro", "Taking the site down"),
    "pause.dawn.all": ("obras pausadas", "works paused"),
    # ---- Diário da cidade
    "pause.diary.pause": ("Obra pausada: %s (dia %s de %s).", "Work paused: %s (day %s of %s)."),
    "pause.diary.resume": ("Obra retomada: %s.", "Work resumed: %s."),
    "pause.diary.pause_all": ("Todas as obras foram pausadas (%s).", "All works were paused (%s)."),
    "pause.diary.resume_all": ("As obras pausadas foram retomadas (%s).", "The paused works were resumed (%s)."),
    "pause.diary.cancel": ("Obra cancelada: %s.", "Work cancelled: %s."),
    "pause.diary.cancel.refund": ("Obra cancelada: %s (%s moedas voltaram ao cofre).",
                                  "Work cancelled: %s (%s coins went back to the treasury)."),
    "pause.diary.undone": ("Canteiro desmontado: %s.", "Site taken down: %s."),
    "pause.diary.undone.back": ("Canteiro desmontado: %s. O prédio será recolocado como era.",
                                "Site taken down: %s. The building will be put back as it was."),
    # ---- comandos de teste
    "cmd.pause.paused": ("Obras pausadas agora: %s", "Works paused now: %s"),
    "cmd.pause.resumed": ("Obras retomadas agora: %s", "Works resumed now: %s"),
    "cmd.pause.cancel": ("Obra cancelada: %s (devolvido ao cofre: %s).", "Work cancelled: %s (returned to the treasury: %s)."),
    "cmd.pause.cancel.undo": ("Obra cancelada: %s (devolvido ao cofre: %s). O canteiro vai ser desmontado aos poucos — /mahou city finish termina já.",
                              "Work cancelled: %s (returned to the treasury: %s). The site will be taken down little by little — /mahou city finish ends it now."),
    "cmd.pause.work": ("%s — dia %s de %s (%s%%) · %s", "%s — day %s of %s (%s%%) · %s"),
    "cmd.pause.can": ("pode cancelar (devolve %s)", "can be cancelled (returns %s)"),
    "cmd.pause.none": ("Nenhuma obra na fila.", "No works in the queue."),
}

# chaves sem o prefixo do módulo
RAW = {
    "hint.isekai_mahou.city_pause": (
        "Obra pausada: ela não gasta dias nem materiais e os construtores vão para as outras. Retome quando quiser na aba Obras.",
        "Work paused: it uses no days or materials and its builders move to the others. Resume it any time in the Works tab."),
}


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
        print("%s: %d chaves de pausar/desistir (%d novas)" % (fname, len(T) + len(RAW), added))
        return
    raise SystemExit("%s mudou o tempo todo — rode de novo" % fname)


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
