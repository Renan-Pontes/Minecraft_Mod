# -*- coding: utf-8 -*-
"""
Gera as traducoes do modulo endless_abyss (pt_br + en_us) a partir de uma tabela unica.
Uso (na raiz do projeto):  py -I tools/gen/endless_abyss_lang.py
"""
import io, json, os, sys
root = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
P = "isekai_mahou.endless_abyss."
L = [
 # (key, pt_br, en_us)
 ("tab." + P[:-1], "Abismo", "Abyss"),
 ("item.isekai_mahou.abyss_shard", "Estilhaço do Abismo", "Abyss Shard"),
 ("item.isekai_mahou.abyss_shard.desc", "Arrancado das profundezas. 8 em volta de um Cristal de Mana formam um Núcleo do Abismo.", "Torn from the depths. 8 around a Mana Crystal form an Abyss Core."),
 ("item.isekai_mahou.abyss_core", "Núcleo do Abismo", "Abyss Core"),
 ("item.isekai_mahou.abyss_core.desc", "Use: enche a mana e a carga das ultimates (recarga de 2 min).", "Use: refills mana and ultimate charge (2 min cooldown)."),
 ("item.isekai_mahou.shadow_fragment", "Fragmento de Sombra", "Shadow Fragment"),
 ("item.isekai_mahou.shadow_fragment.desc", "Resto de uma Sombra vencida. Use: Véu Sombrio por 3 min (força, velocidade e visão noturna).", "Remains of a defeated Shadow. Use: Shadow Veil for 3 min (strength, speed and night vision)."),
 ("entity." + P + "shadow_of", "Sombra de %s", "Shadow of %s"),
 ("entity." + P + "champion", "Campeão do Abismo (%s)", "Abyss Champion (%s)"),
 ("biome.isekai_mahou.endless_abyss", "Abismo Infinito", "Endless Abyss"),

 ("screen." + P + "title", "Abismo Infinito", "Endless Abyss"),
 ("screen." + P + "best", "Recorde: andar %s", "Record: floor %s"),
 ("screen." + P + "shadow_wins", "Sombras vencidas: %s", "Shadows defeated: %s"),
 ("screen." + P + "desc", "Andares sem fim, cada um mais difícil. A cada 5, um chefe; a cada 10, um prêmio raro e um novo ponto de partida. Cair não mata: você volta para onde estava.", "Endless floors, each harder than the last. A boss every 5; a rare prize and a new starting point every 10. Falling does not kill you: you return to where you were."),
 ("screen." + P + "enter", "Entrar — andar %s", "Enter — floor %s"),
 ("screen." + P + "resume", "Continuar — andar %s", "Continue — floor %s"),
 ("screen." + P + "in_run", "Você está no andar %s.", "You are on floor %s."),
 ("screen." + P + "in_shadow", "Luta das Sombras em andamento.", "Shadow Fight in progress."),
 ("screen." + P + "leave","Sair do Abismo", "Leave the Abyss"),
 ("screen." + P + "ranking", "Ranking", "Ranking"),
 ("screen." + P + "floor_short", "andar %s", "floor %s"),
 ("screen." + P + "shadows", "Lutas das Sombras", "Shadow Fights"),
 ("screen." + P + "shadows_hint", "Enfrente a versão sombria dos chefes que você já venceu.", "Face the dark version of the bosses you have already defeated."),
 ("screen." + P + "shadows_empty", "Nenhuma Sombra ainda. Vença um chefe no mundo e a Sombra dele aparece aqui.", "No Shadows yet. Defeat a boss in the world and its Shadow will appear here."),
 ("screen." + P + "shadow_row", "Vitórias: %s", "Wins: %s"),
 ("screen." + P + "fight", "Enfrentar", "Fight"),

 ("bar." + P + "rest", "Abismo — andar %s começa em %ss", "Abyss — floor %s starts in %ss"),
 ("bar." + P + "wave", "Abismo — andar %s · onda %s/%s", "Abyss — floor %s · wave %s/%s"),
 ("bar." + P + "boss", "Abismo — andar %s · chefe", "Abyss — floor %s · boss"),
 ("bar." + P + "shadow", "Luta das Sombras — %s", "Shadow Fight — %s"),
 ("bar." + P + "victory", "Sombra vencida", "Shadow defeated"),
 ("title." + P + "boss", "Uma Sombra desperta", "A Shadow awakens"),

 ("message." + P + "floor_clear", "Andar %s concluído", "Floor %s cleared"),
 ("message." + P + "floor_record", "Andar %s concluído — novo recorde!", "Floor %s cleared — new record!"),
 ("message." + P + "announce", "%s alcançou o andar %s do Abismo Infinito!", "%s reached floor %s of the Endless Abyss!"),
 ("message." + P + "fell", "Você caiu no andar %s do Abismo. Recorde: andar %s.", "You fell on floor %s of the Abyss. Record: floor %s."),
 ("message." + P + "left_floor", "Você saiu do Abismo no andar %s. Recorde: andar %s.", "You left the Abyss on floor %s. Record: floor %s."),
 ("message." + P + "shadow_fell", "A Sombra venceu desta vez.", "The Shadow won this time."),
 ("message." + P + "left", "Você saiu do Abismo.", "You left the Abyss."),
 ("message." + P + "shadow_missing", "Esta Sombra não pode ser invocada neste mundo.", "This Shadow cannot be summoned in this world."),
 ("message." + P + "evacuated", "O Abismo devolveu você para onde estava.", "The Abyss returned you to where you were."),
 ("message." + P + "core_used", "Mana e ultimates restauradas.", "Mana and ultimates restored."),
 ("message." + P + "fragment_used", "Véu Sombrio ativo.", "Shadow Veil active."),
 ("message." + P + "deny.level", "O Abismo Infinito só se abre no nível %s.", "The Endless Abyss only opens at level %s."),
 ("message." + P + "deny.no_dimension", "O Abismo Infinito não existe neste mundo.", "The Endless Abyss does not exist in this world."),
 ("message." + P + "deny.already_in", "Você já está no Abismo.", "You are already in the Abyss."),
 ("message." + P + "deny.spectator", "Você precisa estar vivo e jogando para entrar.", "You must be alive and playing to enter."),
 ("message." + P + "deny.peaceful", "O Abismo não abre na dificuldade Pacífico.", "The Abyss does not open on Peaceful difficulty."),
 ("message." + P + "deny.boss_fight", "Termine a luta contra o chefe antes de entrar.", "Finish the boss fight before entering."),
 ("message." + P + "deny.not_here", "Você não pode entrar no Abismo daqui.", "You cannot enter the Abyss from here."),
 ("message." + P + "deny.combat", "Você está em combate. Espere alguns segundos.", "You are in combat. Wait a few seconds."),
 ("message." + P + "deny.full", "O Abismo está cheio agora. Tente daqui a pouco.", "The Abyss is full right now. Try again shortly."),
 ("message." + P + "deny.not_in", "Você não está no Abismo.", "You are not in the Abyss."),
 ("message." + P + "deny.shadow_unknown", "Essa Sombra não existe.", "That Shadow does not exist."),
 ("message." + P + "deny.shadow_locked", "Vença esse chefe no mundo antes de enfrentar a Sombra dele.", "Defeat that boss in the world before facing its Shadow."),

 ("toast." + P + "unlocked", "O Abismo Infinito se abriu", "The Endless Abyss has opened"),
 ("toast." + P + "unlocked_sub", "Menu (B) → Aventura → Abismo", "Menu (B) → Adventure → Abyss"),
 ("toast." + P + "milestone", "Abismo — andar %s", "Abyss — floor %s"),
 ("toast." + P + "milestone_sub", "Núcleo do Abismo + %s", "Abyss Core + %s"),
 ("toast." + P + "shadow_win", "Sombra vencida", "Shadow defeated"),
 ("toast." + P + "shadow_win_sub", "%s — vitória nº %s", "%s — win #%s"),

 ("command." + P + "record", "Abismo Infinito — seu recorde: andar %s · Sombras vencidas: %s", "Endless Abyss — your record: floor %s · Shadows defeated: %s"),
 ("command." + P + "rank_row", "%s. %s — andar %s", "%s. %s — floor %s"),
 ("command." + P + "status", "Descidas abertas: %s", "Open runs: %s"),

 ("command.isekai_mahou.balance.header", "Curva de XP — nível → próximo: XP do nível | total desde o nível 1", "XP curve — level → next: XP for the level | total since level 1"),
 ("command.isekai_mahou.balance.row", "%s → %s: %s XP | total %s", "%s → %s: %s XP | total %s"),
 ("command.isekai_mahou.balance.span", "Do nível %s ao %s: %s XP", "From level %s to %s: %s XP"),
 ("command.isekai_mahou.balance.hint", "Detalhe: /mahou balance <de> <até>", "Detail: /mahou balance <from> <to>"),
]
keys = [k for k, _, _ in L]
assert len(keys) == len(set(keys)), "chave repetida"
for lang, idx in (("pt_br", 1), ("en_us", 2)):
    with io.open(root + "/src/main/lang/endless_abyss/" + lang + ".json", "w", encoding="utf-8", newline="\n") as f:
        json.dump({row[0]: row[idx] for row in L}, f, ensure_ascii=False, indent=2)
        f.write("\n")
print(len(L), "chaves")
