"""Onda 7 (sky_ui) — traduções da TELA da Ilha Celeste (SkyLand): escolha do terreno na Carta de Fundação, prévia no
céu, linhas do painel, enfeites da ilha (comandos de teste) e as dicas de primeira vez.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w7_sky_lang.py

Só ACRESCENTA/atualiza as chaves abaixo em src/main/lang/settlements/{pt_br,en_us}.json — o resto do arquivo não muda
(mesma ordem: chaves em ordem alfabética). NÃO rode os geradores antigos do módulo (settlements_lang.py,
settlements_v14_lang.py, settlements_styles_lang.py...): eles apagam o trabalho das ondas seguintes.
Outros agentes da onda 7 gravam no mesmo JSON: se o arquivo mudar entre ler e gravar, o script relê e grava de novo.

Os nomes do terreno ("sky.terrain.*"), o anúncio de fundação ("sky.found.announce") e o motivo "sky.wait.land" já vêm
de settlements_w7_sky_island_lang.py; as dicas da corrente e da queda, de settlements_w7_access_lang.py — aqui elas
só entram se estiverem faltando (ver KEEP).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/settlements")
P = "settlements.isekai_mahou."

# chave (com o prefixo do módulo): (pt_br, en_us)
T = {
    # ---- Carta de Fundação: a linha do terreno
    "sky.ui.pick.tip": ("A cidade nasce numa ilha flutuante, no céu acima do ponto escolhido. Combina com qualquer estilo.",
                        "The city is born on a floating island, in the sky above the chosen spot. Works with any style."),
    "sky.ui.pick.cost": ("Gasta 1 Núcleo Celeste, além da Carta de Fundação.", "Spends 1 Sky Core, on top of the Founding Charter."),
    "sky.ui.core.missing": ("Falta o Núcleo Celeste no inventário.", "You need a Sky Core in your inventory."),
    "sky.ui.core.recipe": (
        "Como conseguir: fabrique com 2 Membranas de Phantom, 2 Penas, 2 Fragmentos de Ametista, 2 Essências de Vento e 1 Cristal de Mana.",
        "How to get it: craft it with 2 Phantom Membranes, 2 Feathers, 2 Amethyst Shards, 2 Wind Essences and 1 Mana Crystal."),
    "sky.ui.pick.preview": ("Feche a carta (Esc) para ver a ilha na prévia e mover o ponto; suas escolhas ficam guardadas.",
                            "Close the charter (Esc) to see the island in the preview and move the spot; your choices are kept."),
    "sky.ui.need_core": ("Requer um Núcleo Celeste", "Requires a Sky Core"),
    "sky.ui.altitude": ("Nasce na altitude %s", "Forms at altitude %s"),
    "sky.ui.checking": ("Conferindo o céu...", "Checking the sky..."),
    # ---- prévia no mundo (uma linha acima da barra de itens)
    "sky.place.ground": ("Prévia da cidade · Botão direito: fixar aqui · R: girar a praça · Tab: Ilha Celeste",
                         "City preview · Right-click: pin here · R: rotate the plaza · Tab: Sky Island"),
    "sky.place.free": ("Ilha Celeste · Botão direito: fixar aqui · R: girar a praça · Tab: voltar ao chão",
                       "Sky Island · Right-click: pin here · R: rotate the plaza · Tab: back to the ground"),
    "sky.place.pinned": ("Ilha Celeste · Setas: mover · R: girar · Botão direito: fundar no céu · Tab: voltar ao chão",
                         "Sky Island · Arrows: move · R: rotate · Right-click: found in the sky · Tab: back to the ground"),
    # ---- painel da cidade (Geral)
    "sky.ui.line": ("Ilha Celeste · altitude %s", "Sky Island · altitude %s"),
    "sky.ui.forming": ("A terra está se formando: %s%%", "The land is forming: %s%%"),
    "sky.ui.trait": ("Cidadãos sem dano de queda", "No fall damage for citizens"),
    "sky.ui.tip.land": ("A cidade fica numa ilha flutuante, e a ilha cresce junto com ela.",
                        "The city sits on a floating island, and the island grows with it."),
    "sky.ui.tip.trait": ("Traço: quem é da cidade não leva dano de queda no território da ilha.",
                         "Trait: the city's people take no fall damage within the island's territory."),
    "sky.ui.tip.grow": ("Suba e desça pela Corrente Ascendente, no Cais Celeste.", "Go up and down through the Updraft, at the Sky Pier."),
    "sky.ui.tip.forming": ("Obras em terrenos ainda sem terra esperam ela se formar.",
                           "Works on plots that have no land yet wait for it to form."),
    # ---- enfeites da ilha (comandos de teste)
    "sky.decor.cmd.none": ("Esta ilha ainda não tem enfeites (eles entram quando a terra termina de se formar).",
                           "This island has no decorations yet (they come once the land finishes forming)."),
    "sky.decor.cmd.state": ("Enfeites da ilha — cachoeiras: %s · blocos de pé: %s · enfeites na fila: %s · blocos a remover: %s",
                            "Island decorations — waterfalls: %s · blocks standing: %s · decorations queued: %s · blocks to remove: %s"),
    "sky.decor.cmd.redo": ("Enfeites retirados: a ilha refaz cachoeiras, ilhotas e cristais nos próximos segundos.",
                           "Decorations removed: the island rebuilds waterfalls, islets and crystals over the next seconds."),
}

# só entram se ainda não existirem (são do gerador do acesso; ficam aqui para a ilha nunca ficar sem as dicas)
KEEP = {
    "hint.isekai_mahou.city_sky_updraft": (
        "Corrente Ascendente: entre na coluna brilhante para subir até o Cais Celeste. Agachado, você desce devagar.",
        "Updraft: step into the glowing column to rise to the Sky Pier. Sneak to go down slowly."),
    "hint.isekai_mahou.city_sky_fall": (
        "Cair da Ilha Celeste não mata: a ilha amortece a queda. Para voltar, procure o anel de pedra no chão, debaixo do cais.",
        "Falling off the Sky Island does not kill you: the island cushions the fall. To get back, look for the stone ring on the ground, under the pier."),
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
        for k, v in KEEP.items():
            if k not in data:
                data[k] = v[idx]
                added += 1
        # outro gerador gravou enquanto este lia: relê e tenta de novo
        if os.stat(path).st_mtime_ns != stamp:
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("%s: %d chaves da tela da Ilha Celeste (%d novas)" % (fname, len(T) + len(KEEP), added))
        return
    raise SystemExit("%s mudou a cada tentativa: rode de novo" % fname)


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
