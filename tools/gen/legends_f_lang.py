"""
Traducoes do modulo legends_f (pt_br + en_us) -> src/main/lang/legends_f/{pt_br,en_us}.json
Rodado pelo tools/gen/legends_f.py (ou sozinho: py -I tools/gen/legends_f_lang.py).
Tecnicas de anime das 5 classes magicas: glifos, efeitos, entidades, letreiros, mensagens e Magias Lendarias.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src", "main", "lang", "legends_f")

# (chave, pt_br, en_us)
ENTRIES = []


def e(key, pt, en):
    ENTRIES.append((key, pt, en))


def glyph(gid, pt, en, pt_desc, en_desc):
    e("glyph.isekai_mahou." + gid, pt, en)
    e("glyph.isekai_mahou." + gid + ".desc", pt_desc, en_desc)


# =====================================================================================================  GLIFOS
# ---------------- Mago (Frieren) ----------------
glyph("mage_mana_sense", "Detectar Mana", "Mana Detection",
      "Técnica do Mago (homenagem a Frieren). Um pulso de mana se espalha por 24 blocos (Expandir aumenta): toda "
      "criatura com mana fica com o contorno brilhando por 10 s e você vê a chama da mana de cada uma — verde fraca, "
      "amarela, vermelha forte, roxa = chefe. Diz quantas presenças há e qual a mais forte. Quem está com Mana Oculta "
      "não aparece.",
      "Mage technique (Frieren homage). A mana pulse spreads 24 blocks (Expand widens it): every creature with mana "
      "glows for 10 s and you see each one's mana flame — green weak, yellow, red strong, purple = boss. Tells how many "
      "presences there are and which is the strongest. Anyone under Mana Concealment stays hidden.")
glyph("mage_conceal_mana", "Ocultar Mana", "Mana Concealment",
      "Técnica do Mago (homenagem a Frieren). Suprime a sua mana por 60 s: criaturas a mais de 5 blocos não te notam "
      "(e quem te caçava de longe perde o rastro) e Detectar Mana não te acha. O PRIMEIRO golpe que você der libera "
      "tudo de uma vez — magia ×1,6, golpe ×1,3 — e o efeito acaba. Funciona em aliados.",
      "Mage technique (Frieren homage). Suppresses your mana for 60 s: creatures farther than 5 blocks don't notice you "
      "(hunters lose your trail) and Mana Detection can't find you. Your FIRST hit releases it all at once — spells "
      "×1.6, blows ×1.3 — and the effect ends. Works on allies.")
glyph("mage_general_barrier", "Barreira Defensiva Geral", "General Defensive Barrier",
      "Técnica do Mago (homenagem a Frieren). Uma cúpula de hexágonos de mana (raio 3,5; 12 s) em volta do alvo: "
      "projéteis inimigos se desfazem na parede, monstros são empurrados para fora e golpes/magias vindos de fora "
      "batem nela. Aguenta 135 de dano (×poder) antes de se estilhaçar. Expandir/Estender aumentam.",
      "Mage technique (Frieren homage). A dome of mana hexagons (radius 3.5; 12 s) around the target: enemy projectiles "
      "dissolve on the wall, monsters are pushed out and blows/spells coming from outside hit the dome. Holds 135 damage "
      "(×power) before shattering. Expand/Extend increase it.")
# ---------------- Bruxa ----------------
glyph("witch_broom", "Vassoura da Bruxa Viajante", "Wandering Witch's Broom",
      "Técnica da Bruxa (homenagem a Elaina). Conjura uma vassoura e monta nela: voa para onde você olha (W/S, A/D "
      "desliza, agachar desce) deixando um rastro de pétalas. 90 s (Estender aumenta); Acelerar e Amplificar deixam "
      "mais rápida. Ao descer, você cai devagar.",
      "Witch technique (Elaina homage). Conjures a broom and mounts it: it flies where you look (W/S, A/D strafes, "
      "sneak to get off) leaving a trail of petals. 90 s (Extend lengthens); Accelerate and Amplify make it faster. "
      "You float down when you get off.")
glyph("witch_sleep_curse", "Maldição do Sono Eterno", "Curse of Eternal Sleep",
      "Técnica da Bruxa (homenagem à Bela Adormecida). Espinhos de roseira e uma roca encantada: o alvo dorme por 8 s "
      "(chefes 2 s, jogadores 3 s) — não anda, não ataca e esquece quem caçava. O primeiro dano acorda, mas sai ×1,5.",
      "Witch technique (Sleeping Beauty homage). Rose thorns and an enchanted spindle: the target sleeps for 8 s "
      "(bosses 2 s, players 3 s) — it can't move or attack and forgets its target. The first damage wakes it, at ×1.5.")
glyph("witch_envy_hands", "Mãos da Inveja", "Hands of Envy",
      "Técnica da Bruxa (homenagem leve à Bruxa da Inveja). Mãos negras brotam do chão (raio 6) por 5 s: cada uma "
      "agarra um inimigo, arrasta para o centro e aperta — 3,5 de dano a cada 0,5 s, Corrupção e Lentidão IV. "
      "6 mãos; cada Dividir dá +2.",
      "Witch technique (light Witch of Envy homage). Black hands burst from the ground (radius 6) for 5 s: each grabs "
      "an enemy, drags it to the center and squeezes — 3.5 damage every 0.5 s, Corruption and Slowness IV. 6 hands; "
      "each Split adds 2.")
# ---------------- Bruxo ----------------
glyph("warlock_chainsaw", "Pacto da Motosserra", "Chainsaw Pact",
      "Técnica do Bruxo (homenagem a Chainsaw Man). Puxa a cordinha do coração: motosserras saem da cabeça e dos "
      "braços por 30 s. Golpes corpo a corpo +7, fazem Sangrar e devolvem 25% como vida; correndo, rasga quem está na "
      "frente. Uma vez por transformação, um golpe fatal te deixa de pé com 30% da vida.",
      "Warlock technique (Chainsaw Man homage). Pull the cord in your heart: chainsaws burst from your head and arms "
      "for 30 s. Melee +7, causes Bleeding and heals you for 25%; sprinting shreds whoever is in front. Once per "
      "transformation, a lethal blow leaves you standing at 30% health.")
glyph("warlock_faust", "Contrato Faustiano", "Faustian Contract",
      "Técnica do Bruxo. Assina um contrato com o demônio (60 s): +0,4 de Poder Mágico e mana ×2, mas −30% de vida "
      "máxima. Cláusula: derrotar 3 inimigos (+1 por Amplificar) antes do prazo. Cumprido: vida cheia, mana e XP. "
      "Descumprido: o demônio cobra 25% da vida, com Fraqueza e Lentidão.",
      "Warlock technique. Sign a contract with the devil (60 s): +0.4 Spell Power and ×2 mana regen, but −30% max "
      "health. Clause: defeat 3 enemies (+1 per Amplify) before the deadline. Fulfilled: full health, mana and XP. "
      "Broken: the devil takes 25% of your health, with Weakness and Slowness.")
glyph("warlock_bang", "Bang!", "Bang!",
      "Técnica do Bruxo (homenagem ao dedo-arma do contrato de controle). Você aponta o dedo e diz \"Bang.\": meio "
      "segundo depois o alvo explode por dentro — 24 de dano que fura boa parte da armadura e arremessa longe.",
      "Warlock technique (homage to the control contract's finger gun). Point your finger and say \"Bang.\": half a "
      "second later the target bursts from within — 24 damage that pierces much of the armor and hurls it away.")
# ---------------- Druida ----------------
glyph("druid_forest_god", "Deus da Floresta", "Forest God",
      "Técnica LENDÁRIA do Druida (homenagem ao Shishigami de Princesa Mononoke). Um cervo divino gigantesco caminha "
      "pela área por 12 s: flores brotam a cada passo; a cada segundo, aliados a até 9 blocos são curados e limpos de "
      "venenos, e inimigos perdem 7 de vida. Com Inverter ele só tira a vida: 11 por segundo.",
      "Druid LEGENDARY technique (Princess Mononoke's Forest Spirit homage). A colossal divine stag walks the area for "
      "12 s: flowers bloom at every step; every second, allies within 9 blocks are healed and cleansed of poison, and "
      "enemies lose 7 health. With Invert it only takes life: 11 per second.")
glyph("druid_sage_mode", "Modo Sábio", "Sage Mode",
      "Técnica do Druida (homenagem ao Senjutsu de Naruto). Fique PARADO 3 s absorvendo a energia natural (andar "
      "desfaz). Depois, 40 s de Modo Sábio: pigmento laranja nos olhos, +0,35 de Poder Mágico, +4 de ataque e "
      "armadura, mais velocidade e mana; sente os inimigos a 20 blocos e cada golpe solta uma onda em volta do alvo.",
      "Druid technique (Naruto's Senjutsu homage). Stand STILL for 3 s absorbing natural energy (moving breaks it). Then "
      "40 s of Sage Mode: orange pigment around the eyes, +0.35 Spell Power, +4 attack and armor, more speed and mana; "
      "you sense enemies within 20 blocks and every blow releases a wave around the target.")
glyph("druid_kodama", "Chamado dos Kodama", "Call of the Kodama",
      "Técnica do Druida (homenagem aos espíritos de Princesa Mononoke). Chama 3 Kodama (Dividir: +1 cada; máx. 8) "
      "por 90 s: seguem você balançando a cabeça, a cada 3 s curam o aliado mais ferido (o dobro perto de árvores) e "
      "prendem em raízes quem te ferir.",
      "Druid technique (Princess Mononoke spirits homage). Calls 3 Kodama (Split: +1 each; max 8) for 90 s: they follow "
      "you rattling their heads, heal the most wounded ally every 3 s (double near trees) and root whoever hurts you.")
# ---------------- Necromante (Solo Leveling) ----------------
glyph("necro_arise", "Levante-se!", "Arise!",
      "Técnica do Necromante (homenagem a Solo Leveling). Ergue as sombras de criaturas mortas por perto (até 60 s "
      "depois da morte) como Soldados das Sombras PERMANENTES do seu exército (limite: 3 + Círculo + 2×rank). Cada "
      "sombra tem 3 tentativas: as fracas sempre levantam; fortes e chefes resistem (seu nível e Amplificar ajudam).",
      "Necromancer technique (Solo Leveling homage). Raises the shadows of creatures that died nearby (up to 60 s after "
      "death) as PERMANENT Shadow Soldiers of your army (limit: 3 + Circle + 2×rank). Each shadow gets 3 tries: weak "
      "ones always rise; strong ones and bosses resist (your level and Amplify help).")
glyph("necro_shadow_command", "Comando do Monarca", "Monarch's Command",
      "Técnica do Necromante. Ordens ao Exército das Sombras: num inimigo — \"Ataquem!\"; em você — \"Saiam!\" "
      "(chama o exército; manter custa mana por segundo), de novo alterna seguir/guardar posição; agachado — "
      "\"Voltem.\" Olhando para um soldado seu: mostra a ficha dele (agachado 2× liberta para sempre).",
      "Necromancer technique. Orders to the Shadow Army: on an enemy — \"Attack!\"; on yourself — \"Come out!\" (summons "
      "the army; keeping it out drains mana per second), again toggles follow/hold position; sneaking — \"Return.\" "
      "Looking at one of your soldiers: shows its card (sneak twice to release it forever).")
glyph("necro_shadow_exchange", "Troca de Sombras", "Shadow Exchange",
      "Técnica do Necromante. Troca de lugar na hora com um dos seus soldados manifestados — o mais perto do ponto "
      "atingido, ou (em você) o que estiver na sua mira.",
      "Necromancer technique. Instantly swap places with one of your manifested soldiers — the one closest to the point "
      "hit, or (on yourself) the one you're aiming at.")
glyph("necro_monarch", "Monarca das Sombras", "Shadow Monarch",
      "Forma G LENDÁRIA do Necromante (homenagem a Solo Leveling). Por 30 s (Estender: até 60 s) todo o exército sai "
      "de graça — até quem estava se refazendo —, soldados ×1,5 mais fortes e resistentes, armadura de sombras do "
      "Monarca, Medo nos inimigos, os efeitos pulsam a cada 2 s (8 blocos) e toda criatura que morrer a até 24 blocos "
      "é erguida na hora.",
      "Necromancer LEGENDARY G form (Solo Leveling homage). For 30 s (Extend: up to 60 s) the whole army comes out for "
      "free — even those still reforming —, soldiers ×1.5 stronger and tougher, the Monarch's shadow armor, Fear on "
      "enemies, effects pulse every 2 s (8 blocks) and every creature that dies within 24 blocks rises at once.")

# =====================================================================================================  EFEITOS
e("effect.isekai_mahou.mana_concealed", "Mana Oculta", "Mana Concealed")
e("effect.isekai_mahou.enchanted_sleep", "Sono Encantado", "Enchanted Sleep")
e("effect.isekai_mahou.chainsaw_devil", "Demônio da Motosserra", "Chainsaw Devil")
e("effect.isekai_mahou.faust_contract", "Contrato Faustiano", "Faustian Contract")
e("effect.isekai_mahou.sage_mode", "Modo Sábio", "Sage Mode")
e("effect.isekai_mahou.shadow_monarch", "Monarca das Sombras", "Shadow Monarch")

# =====================================================================================================  ENTIDADES
e("entity.isekai_mahou.shadow_soldier", "Soldado das Sombras", "Shadow Soldier")
e("entity.isekai_mahou.shadow_soldier.named", "%s das Sombras: %s", "Shadow %s: %s")
e("entity.isekai_mahou.shadow_soldier.grade_0", "Soldado", "Soldier")
e("entity.isekai_mahou.shadow_soldier.grade_1", "Cavaleiro", "Knight")
e("entity.isekai_mahou.shadow_soldier.grade_2", "Elite", "Elite")
e("entity.isekai_mahou.shadow_soldier.grade_3", "Marechal", "Marshal")
e("entity.isekai_mahou.kodama", "Kodama", "Kodama")
e("entity.isekai_mahou.witch_broom", "Vassoura da Bruxa", "Witch's Broom")
e("entity.isekai_mahou.general_barrier", "Barreira Defensiva Geral", "General Defensive Barrier")
e("entity.isekai_mahou.envy_hands", "Mãos da Inveja", "Hands of Envy")
e("entity.isekai_mahou.forest_god", "Deus da Floresta", "Forest God")

# =====================================================================================================  LETREIROS
A = "announce.isekai_mahou.legends_f."
e(A + "arise", "LEVANTE-SE!", "ARISE!")
e(A + "attack", "Ataquem.", "Attack.")
e(A + "come_out", "Saiam.", "Come out.")
e(A + "monarch", "MONARCA DAS SOMBRAS", "SHADOW MONARCH")
e(A + "barrier", "Barreira Defensiva Geral", "General Defensive Barrier")
e(A + "reveal", "Mana liberada!", "Mana released!")
e(A + "broom", "Bruxa Viajante", "Wandering Witch")
e(A + "envy", "Mãos da Inveja", "Hands of Envy")
e(A + "chainsaw", "VRUUUM! DEMÔNIO DA MOTOSSERRA!", "VRRROOM! CHAINSAW DEVIL!")
e(A + "chainsaw_heart", "O motor do coração ruge de novo!", "The heart's engine roars again!")
e(A + "faust", "Contrato selado.", "Contract sealed.")
e(A + "faust_done", "CONTRATO CUMPRIDO", "CONTRACT FULFILLED")
e(A + "faust_fail", "O DEMÔNIO COBRA A DÍVIDA", "THE DEVIL COLLECTS")
e(A + "bang", "Bang.", "Bang.")
e(A + "forest_god", "DEUS DA FLORESTA", "FOREST GOD")
e(A + "forest_god_death", "O DEUS DA FLORESTA TIRA A VIDA", "THE FOREST GOD TAKES LIFE")
e(A + "sage", "MODO SÁBIO!", "SAGE MODE!")

# =====================================================================================================  MENSAGENS
M = "message.isekai_mahou.legends_f."
e(M + "unlock", "✦ Técnica de classe desbloqueada: %s", "✦ Class technique unlocked: %s")
e(M + "arise_none", "Nenhuma sombra para erguer aqui (criaturas mortas há menos de 60 s).",
  "No shadows to raise here (creatures that died less than 60 s ago).")
e(M + "arise_ok", "%s se ergueu da sombra! Exército: %s/%s", "%s rose from the shadow! Army: %s/%s")
e(M + "arise_fail", "A sombra resiste... (restam %s tentativas, chance %s%%)", "The shadow resists... (%s tries left, %s%% chance)")
e(M + "arise_lost", "A sombra se desfez no ar.", "The shadow dissolved.")
e(M + "army_full", "Exército cheio (%s). Liberte um soldado ou suba de Círculo/rank para comandar mais.",
  "Army full (%s). Release a soldier or raise your Circle/rank to command more.")
e(M + "army_empty", "Seu exército está vazio — use Levante-se! sobre inimigos derrotados.",
  "Your army is empty — use Arise! on defeated enemies.")
e(M + "army_none_ready", "Todos os soldados ainda estão se refazendo na sua sombra.", "All soldiers are still reforming in your shadow.")
e(M + "army_regen", "%s soldado(s) ainda se refazendo na sombra.", "%s soldier(s) still reforming in the shadow.")
e(M + "army_no_mana", "Mana insuficiente para chamar mais soldados.", "Not enough mana to summon more soldiers.")
e(M + "army_out", "Exército: %s/%s fora da sombra (−%s de mana/s)", "Army: %s/%s out of the shadow (−%s mana/s)")
e(M + "army_recall", "%s soldado(s) voltaram para a sua sombra.", "%s soldier(s) returned to your shadow.")
e(M + "army_hold", "Exército: guardem posição.", "Army: hold position.")
e(M + "army_follow", "Exército: sigam-me.", "Army: follow me.")
e(M + "army_upkeep", "Sua mana acabou: o exército voltou para a sombra.", "Your mana ran out: the army returned to the shadow.")
e(M + "soldier_down", "%s caiu e volta para a sombra (pronto de novo em %s s).", "%s fell and returns to the shadow (ready again in %s s).")
e(M + "soldier_card", "%s — vida %s/%s · abates %s", "%s — health %s/%s · kills %s")
e(M + "soldier_release_confirm", "Agache e use o Comando de novo em %s em até 5 s para libertá-lo PARA SEMPRE.",
  "Sneak and use the Command on %s again within 5 s to release it FOREVER.")
e(M + "soldier_released", "%s foi libertado e descansa em paz.", "%s was released and rests in peace.")
e(M + "exchange_none", "Nenhum soldado seu fora da sombra para trocar de lugar.", "None of your soldiers is out to swap places with.")
e(M + "monarch_start", "O Monarca desperta: %s soldados se erguem (exército %s/%s). Quem cair perto de você se levanta.",
  "The Monarch awakens: %s soldiers rise (army %s/%s). Whoever falls near you will rise.")
e(M + "monarch_empty", "O Monarca desperta sem exército (0/%3$s) — quem cair perto de você se levanta!",
  "The Monarch awakens with no army (0/%3$s) — whoever falls near you will rise!")
e(M + "monarch_end", "A presença do Monarca recua.", "The Monarch's presence recedes.")
e(M + "sense_none", "Nenhuma mana por perto... ou está muito bem escondida.", "No mana nearby... or it is very well hidden.")
e(M + "sense_found", "Mana detectada: %s presença(s) · a mais forte: %s (%s de vida)", "Mana detected: %s presence(s) · strongest: %s (%s health)")
e(M + "conceal_on", "Mana oculta por %s s. O primeiro golpe revela tudo.", "Mana concealed for %s s. Your first hit reveals it all.")
e(M + "broom_on", "Vassoura pronta! Olhe para onde quer ir · W/S frente/trás · A/D desliza · agachar desce.",
  "Broom ready! Look where you want to go · W/S forward/back · A/D strafe · sneak to get off.")
e(M + "broom_renew", "A magia da vassoura foi renovada.", "The broom's magic was renewed.")
e(M + "broom_ending", "A vassoura está perdendo a magia — procure onde pousar!", "The broom is losing its magic — find a place to land!")
e(M + "sleep_you", "Você está caindo num sono encantado...", "You are falling into an enchanted sleep...")
e(M + "faust_busy", "Você já tem um contrato em andamento.", "You already have a contract in progress.")
e(M + "faust_terms", "Cláusula do contrato: derrote %s inimigo(s) em %s s. Senão, o demônio cobra.",
  "Contract clause: defeat %s enemy(ies) within %s s. Otherwise the devil collects.")
e(M + "faust_progress", "Contrato: %s/%s almas · %s s", "Contract: %s/%s souls · %s s")
e(M + "sage_meditate", "Fique PARADO: absorvendo energia natural...", "Stay STILL: absorbing natural energy...")
e(M + "sage_broken", "Você se moveu — a energia natural escapou.", "You moved — the natural energy slipped away.")
e(M + "kodama_on", "%s Kodama vieram da floresta.", "%s Kodama came from the forest.")
e(M + "kodama_full", "Já há Kodama demais te seguindo.", "Too many Kodama are already following you.")

# =====================================================================================================  LENDAS
L = "legend.isekai_mahou."
e(L + "frieren_suppression", "Supressão de Mana", "Mana Suppression")
e(L + "general_defensive_magic", "Magia Defensiva Geral", "General Defensive Magic")
e(L + "wandering_witch", "A Bruxa Viajante", "The Wandering Witch")
e(L + "sleeping_beauty", "Sono da Bela Adormecida", "Sleeping Beauty's Slumber")
e(L + "unseen_envy", "Mãos da Inveja", "Hands of Envy")
e(L + "chainsaw_devil", "Demônio da Motosserra", "Chainsaw Devil")
e(L + "bang", "Bang.", "Bang.")
e(L + "faustian_bargain", "Pacto de Fausto", "Faust's Bargain")
e(L + "forest_spirit", "O Deus da Floresta", "The Forest God")
e(L + "sage_art", "Arte Sábia: Modo Sábio", "Sage Art: Sage Mode")
e(L + "kodama_grove", "Bosque dos Kodama", "Kodama Grove")
e(L + "arise", "Levante-se!", "Arise!")
e(L + "shadow_monarch", "Monarca das Sombras", "Shadow Monarch")


def main():
    pt, en = {}, {}
    for k, p, n in ENTRIES:
        if k in pt:
            raise RuntimeError("chave duplicada: " + k)
        pt[k], en[k] = p, n
    os.makedirs(LANG, exist_ok=True)
    for name, data in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("  traducoes: %d chaves (pt_br + en_us)" % len(pt))


if __name__ == "__main__":
    main()
