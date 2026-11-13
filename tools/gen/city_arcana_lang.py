"""v1.0 onda 4 (city_magic) — traduções da DEFESA MONTADA da Barreira Arcana (receita gravada + Núcleo de Mana).

Uso (na raiz do projeto):  py -I tools/gen/city_arcana_lang.py

Junta (e sobrescreve) só as chaves abaixo em src/main/lang/city_arcana/{pt_br,en_us}.json; as outras chaves do módulo
(escritas à mão na v1.5) ficam como estão.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/city_arcana")
P = "city_arcana.isekai_mahou."

# chave: (pt_br, en_us)
T = {
    # ---- Núcleo de Mana
    "ward.core": ("Barreira Arcana de %s · Núcleo de Mana: %s/%s", "Arcane Barrier of %s · Mana Core: %s/%s"),
    "ward.pour": ("Você doou %s de mana ao Núcleo (%s/%s).", "You gave %s mana to the Core (%s/%s)."),
    "ward.pour.full": ("O Núcleo de Mana já está cheio.", "The Mana Core is already full."),
    "ward.pour.none": ("Você está sem mana para doar.", "You have no mana to give."),
    "ward.dry": ("O Núcleo de Mana de %s secou: a Barreira parou de lançar a receita. Doe mana no catalisador!",
                 "The Mana Core of %s ran dry: the Barrier stopped casting its recipe. Give mana at the catalyst!"),
    "ward.fire": ("A Barreira lança \"%s\" nos invasores!", "The Barrier casts \"%s\" on the invaders!"),

    # ---- receita
    "ward.not_lord": ("Só o Senhor de %s grava ou apaga a receita da Barreira.",
                      "Only the Lord of %s can record or erase the Barrier's recipe."),
    "ward.recipe.none": ("Nenhuma receita gravada. A Barreira só protege; não ataca os invasores.",
                         "No recipe recorded. The Barrier only protects; it does not strike invaders."),
    "ward.recipe.no_spell": ("Não há magia aí para gravar. Monte uma no Grimório (efeitos e, se quiser, leis de Domínio).",
                             "There is no spell there to record. Build one in the Grimoire (effects and, if you like, Domain laws)."),
    "ward.recipe.invalid": ("Você não consegue conjurar essa magia, então não pode gravá-la: %s",
                            "You cannot cast that spell, so you cannot record it: %s"),
    "ward.recipe.blocks": ("A Barreira não aceita \"%s\": esse efeito mexe em blocos. Cidades são sagradas.",
                           "The Barrier refuses \"%s\": that effect changes blocks. Cities are sacred."),
    "ward.recipe.empty": ("Essa magia não tem nada que a Barreira use. Ponha efeitos contra inimigos (Lentidão, Queimar, dano...) "
                          "ou leis de Domínio.",
                          "That spell has nothing the Barrier can use. Add effects against enemies (Slow, Burn, damage...) or "
                          "Domain laws."),
    "ward.recipe.set": ("Receita gravada na Barreira de %s: \"%s\". Ela cai nos invasores durante cercos.",
                        "Recipe recorded on the Barrier of %s: \"%s\". It strikes invaders during sieges."),
    "ward.recipe.law_ignored": ("Leis que a Barreira não usa (ela nunca muda blocos nem prende ninguém): %s",
                                "Laws the Barrier does not use (it never changes blocks or traps anyone): %s"),
    "ward.recipe.cleared": ("Receita da Barreira apagada.", "Barrier recipe erased."),
    "ward.recipe.unnamed": ("Magia sem nome", "Unnamed spell"),
    "ward.recipe.status": ("Receita: \"%s\" · Efeitos: %s · Leis: %s · Gasta %s de mana do Núcleo por invasor atingido.",
                           "Recipe: \"%s\" · Effects: %s · Laws: %s · Spends %s Core mana per invader hit."),

    # ---- botões do chat (só o Senhor vê)
    "ward.buttons": ("Gravar a magia do slot:", "Record the spell in slot:"),
    "ward.button.record": ("Grava na Barreira a magia do slot %s do seu conjunto ativo.",
                           "Records the spell in slot %s of your active loadout on the Barrier."),
    "ward.button.pour": ("[Doar mana]", "[Give mana]"),
    "ward.button.pour.tip": ("Passa a sua mana para o Núcleo, 1 para 1 (também: agachar + clicar no catalisador).",
                             "Moves your mana into the Core, 1 for 1 (also: sneak + click the catalyst)."),
    "ward.button.clear": ("[Apagar]", "[Erase]"),
    "ward.button.clear.tip": ("Apaga a receita. A Barreira volta a só proteger.", "Erases the recipe. The Barrier goes back to only protecting."),

    # ---- comandos
    "cmd.no_ward": ("Esta cidade não tem uma Barreira Arcana pronta.", "This city has no finished Arcane Barrier."),
    "cmd.ward.mana": ("Núcleo de Mana da Barreira: %s/%s.", "Barrier Mana Core: %s/%s."),
    "cmd.ward.pulse": ("Teste: a receita da Barreira atingiu %s inimigo(s).", "Test: the Barrier recipe hit %s enemy(ies)."),
}

# chaves completas (fora do prefixo do módulo)
RAW = {
    "hint.isekai_mahou.city_arcana_ward": (
        "Barreira Arcana: clique no catalisador com um Pergaminho de Magia (ou de mão vazia) para gravar a defesa que ela lança nos invasores.",
        "Arcane Barrier: click the catalyst with a Spell Scroll (or empty-handed) to record the defense it casts on invaders."),
}


def merge(fname, idx):
    path = os.path.join(LANG, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in T.items():
        data[P + k] = v[idx]
    for k, v in RAW.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d chaves (city_magic)" % (fname, len(T) + len(RAW)))


if __name__ == "__main__":
    merge("pt_br.json", 0)
    merge("en_us.json", 1)
