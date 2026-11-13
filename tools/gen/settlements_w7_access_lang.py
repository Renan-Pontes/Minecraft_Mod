"""Onda 7 (sky_access) — traduções do ACESSO à Ilha Celeste: Corrente Ascendente, Cais Celeste, queda amortecida, comandos.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w7_access_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
Outros agentes da onda 7 gravam no mesmo JSON: se o arquivo mudar entre ler e gravar, o script relê e grava de novo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- Corrente Ascendente
    "sky.updraft.closed": ("Os portões de %s estão fechados: a Corrente Ascendente só leva quem é da cidade.",
                           "The gates of %s are closed: the Updraft only carries its own people."),
    "sky.updraft.no_land": ("A terra de %s ainda está se formando: a corrente não tem onde pousar.",
                            "The land of %s is still forming: the Updraft has nowhere to land yet."),
    "sky.cushion": ("A magia da ilha amortece a sua queda.", "The island's magic cushions your fall."),
    # ---- Cais Celeste e marco no chão
    "sky.pier.built": ("Cais Celeste pronto. A Corrente Ascendente toca o chão em x %s, z %s.",
                       "Sky Pier ready. The Updraft touches the ground at x %s, z %s."),
    "sky.pier.protected": ("O Cais Celeste e o marco da corrente são protegidos.",
                           "The Sky Pier and the Updraft marker are protected."),
    "sky.diary.pier": ("O Cais Celeste ficou pronto: a Corrente Ascendente liga a ilha ao chão (marco em x %s, z %s).",
                       "The Sky Pier is ready: the Updraft links the island to the ground (marker at x %s, z %s)."),
    # ---- comandos de teste
    "cmd.sky.pier": ("%s — Corrente Ascendente em x %s, z %s (chão y %s) · cais: %s blocos · marco: %s blocos · a desmontar: %s · %s",
                     "%s — Updraft at x %s, z %s (ground y %s) · pier: %s blocks · marker: %s blocks · to remove: %s · %s"),
    "cmd.sky.pier.open": ("subida livre", "ride open to all"),
    "cmd.sky.pier.closed": ("portões fechados: só cidadãos sobem", "gates closed: citizens only"),
    "cmd.sky.pier.none": ("A Corrente Ascendente desta ilha ainda não tem lugar (ou o chão debaixo dela não foi achado).",
                          "This island's Updraft has no place yet (or the ground under it was not found)."),
    "cmd.sky.drop": ("%s foi derrubado da ilha. Tem de voltar para casa sozinho em até 5 segundos.",
                     "%s was dropped off the island. They must be back home on their own within 5 seconds."),
    "cmd.sky.drop.none": ("Nenhum NPC desta cidade a menos de 48 blocos de você.", "No NPC of this city within 48 blocks of you."),
}

# chaves sem o prefixo do módulo
RAW = {
    "hint.isekai_mahou.city_sky_updraft": (
        "Corrente Ascendente: entre na coluna brilhante para subir até o Cais Celeste. Agachado, você desce devagar.",
        "Updraft: step into the glowing column to rise to the Sky Pier. Sneak to float down slowly."),
    "hint.isekai_mahou.city_sky_fall": (
        "Cair da Ilha Celeste não mata: a ilha amortece a queda. Para voltar, procure o anel de pedra no chão, debaixo do cais.",
        "Falling off a Sky Island won't kill you: the island cushions the fall. To get back, find the stone ring on the ground, under the pier."),
}


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    for _ in range(5):
        stamp = os.stat(path).st_mtime_ns
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        added = 0
        for k, v in T.items():
            added += (P + k) not in data
            data[P + k] = v[idx]
        for k, v in RAW.items():
            added += k not in data
            data[k] = v[idx]
        # outro gerador gravou enquanto este lia: relê e tenta de novo
        if os.stat(path).st_mtime_ns != stamp:
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("%s: %d chaves do acesso à Ilha Celeste (%d novas)" % (fname, len(T) + len(RAW), added))
        return
    raise SystemExit("%s mudou a cada tentativa: rode de novo" % fname)


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
