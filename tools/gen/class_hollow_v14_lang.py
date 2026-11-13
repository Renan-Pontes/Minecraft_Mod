"""
v1.4 — traducoes do rework da classe Hollow (tecnicas inatas, Roda Hollow, glifos novos da classe).
MESCLA em src/main/lang/class_hollow/{pt_br,en_us}.json: chaves existentes sao atualizadas no lugar, novas vao no fim.
Uso (na raiz do projeto):  py -I tools/gen/class_hollow_v14_lang.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/class_hollow")

M = "message.isekai_mahou.hollow."
T = "hollow_tech.isekai_mahou."
S = "screen.isekai_mahou.hollow."
G = "glyph.isekai_mahou."

PT = {
    # ---------------- teclas
    "key.isekai_mahou.hollow_technique": "Instinto Hollow (técnicas)",
    "key.isekai_mahou.hollow_wheel": "Roda Hollow",
    # ---------------- técnicas inatas
    T + "cero": "Cero",
    T + "cero.desc": "A energia se junta na boca da máscara (na ponta dos dedos, no Arrancar) e sai num feixe que atravessa TODOS os inimigos e explode no fim. Quanto mais você segura, mais grosso, longe e forte. A cor é a do seu corpo: rubro, carmim, negro-rubro, e a do arquétipo no Arrancar. Murciélago liberado: vira a Lanza del Relámpago. Custo: 5–12% de Vigor.",
    T + "cero.how": "Segure [Instinto Hollow] e solte (carga mínima 0,5 s, cheia 1,8 s).",
    T + "bala": "Bala",
    T + "bala.desc": "Reiatsu socado num tiro instantâneo, 20× mais rápido que o Cero e bem mais fraco: até 24 blocos, empurra um pouco. Feito para metralhar: 4 tiros por segundo. Custo: 1,2% de Vigor por tiro.",
    T + "bala.how": "Toque [Instinto Hollow] (toques rápidos = rajada).",
    T + "gran_rey_cero": "Gran Rey Cero",
    T + "gran_rey_cero.desc": "O Cero misturado ao próprio sangue (paga 2 de vida): um feixe COLOSSAL de 64 blocos que atravessa paredes e faz o chão tremer. Do Vasto Lorde em diante; liberado (Resurrección) carrega mais rápido, custa menos e bate ×1,5. Custo: 30% de Vigor (22% liberado). Recarga 45 s (30 s).",
    T + "gran_rey_cero.how": "Agachado: segure [Instinto Hollow] 1,5 s (1 s liberado) e solte.",
    T + "cero_oscuras": "Cero Oscuras",
    T + "cero_oscuras.desc": "Só o Arrancar LIBERADO: o Cero fica NEGRO, com a borda na cor do arquétipo. Atravessa paredes, definha quem toca e deixa um vazio no fim que pulsa 3 vezes. Custo: 35% de Vigor. Recarga 60 s.",
    T + "cero_oscuras.how": "Liberado: segure o Cero até a carga escurecer (2,2 s) e solte.",
    T + "sonido": "Sonído",
    T + "sonido.desc": "O passo do Hollow: some com um estalo e reaparece até 10 blocos à frente (+2 por estágio), deixando uma imagem residual. Mirando um inimigo, aparece ÀS COSTAS dele, virado para ele. Até 3 passos seguidos; depois 2,5 s de recarga. Custo: 3% de Vigor por passo.",
    T + "sonido.how": "Correndo: aperte [Instinto Hollow].",
    T + "pesquisa": "Pesquisa",
    T + "pesquisa.desc": "O sentido do Hollow: um pulso revela tudo o que vive a 32 blocos, através das paredes, por 10 s. Hostis em rubro, jogadores em branco, presenças fortes em negro. Custo: 4% de Vigor.",
    T + "pesquisa.how": "Agachado: toque [Instinto Hollow] — ou pela Roda Hollow.",
    T + "regen": "Regeneração Instantânea",
    T + "regen.desc": "A carne se fecha na hora: cura 30% da vida (40% do Vasto Lorde em diante) e apaga fogo, veneno e definhar. Paga 5 Almas; sem Almas, paga com a fome. Recarga 15 s.",
    T + "regen.how": "Roda Hollow.",
    T + "hierro": "Hierro",
    T + "hierro.desc": "A pele de ferro. PASSIVA, cresce com o corpo: −10% de dano (Gillian), −15% (Adjuchas), −25% (Vasto Lorde), −30% (Arrancar). Concentrada: −30% por 10 s, ou −50% e sem ser empurrado por 8 s do Vasto Lorde em diante. Custo: 8% de Vigor.",
    T + "hierro.how": "Passiva sempre; concentrar pela Roda Hollow.",
    T + "garganta": "Garganta",
    T + "garganta.desc": "Enfia as garras no ar e RASGA o céu (1,5 s): uma boca negra de dentes se abre e leva ao Mundo Oco — ou de volta, se você já estiver lá. Custo: 15% de Vigor. Recarga 30 s (e a dos atalhos do Mundo Oco).",
    T + "garganta.how": "Roda Hollow.",
    T + "negacion": "Negación",
    T + "negacion.desc": "A fuga dos Hollows: uma coluna de luz dourada desce do céu sobre você por 3 s — NADA te toca (e você também não ataca), os perseguidores perdem o alvo e, quando a luz some, ela te leva até 20 blocos para longe do inimigo mais próximo. Custo: 20% de Vigor. Recarga 3 min.",
    T + "negacion.how": "Roda Hollow (Vasto Lorde em diante).",
    T + "descorrer": "Descorrer",
    T + "descorrer.desc": "O Arrancar abre um rasgo no espaço e atravessa: reaparece onde está olhando, até 40 blocos. Custo: 10% de Vigor. Recarga 8 s.",
    T + "descorrer.how": "Roda Hollow (Arrancar).",
    T + "resurreccion": "Resurrección",
    T + "resurreccion.desc": "A TRANSFORMAÇÃO do Arrancar: o comando de liberação, uma coluna de Reiatsu na cor do arquétipo sobe por 2 s e estoura — a pressão empurra e fere tudo em 8 blocos, o chão treme e a forma liberada (Pantera, Murciélago ou Serpiente) dura 60 s. Liberado: técnicas Hollow +25%, Gran Rey Cero mais forte e o Cero Oscuras. Ao acabar, o corpo cobra (Fraqueza, Lentidão, Vigor a 25%). Custo: 40% de Vigor. Recarga 5 min.",
    T + "resurreccion.how": "Roda Hollow (Arrancar, com o arquétipo escolhido).",
    # ---------------- glifos novos da classe
    G + "hollowfied_cero_fusionado": "Cero Fusionado",
    G + "hollowfied_cero_fusionado.desc": "Aumento do Hollow: a magia ganha um CERO — no instante em que ela sai, um feixe fino dispara da sua boca (ou da mão, no Arrancar) para onde você olha: 22 blocos, atravessa todos os inimigos (dano próprio 3, +1 por estágio do Hollow, com o poder da magia), na cor do seu Cero. Não aplica os efeitos da magia. Custa 8 de mana e +0,4 s de conjuração; não acumula.",
    G + "hollowfied_on_devour": "Ao Devorar uma Alma",
    G + "hollowfied_on_devour.desc": "Gatilho do Hollow (vai no começo da magia): o slot sai SOZINHO quando você devora uma alma, mirando o inimigo mais próximo — e fica mais forte quanto maior a alma: +1% de poder por Alma daquele abate (até +100%: chefes e jogadores). Cada disparo gasta 1 Alma guardada. Apertar a tecla do slot não conjura. No máximo 1 vez por segundo.",
    # ---------------- glifos antigos (aposentados)
    G + "hollowfied_cero.desc": "(Antigo) O Cero agora é uma TÉCNICA INATA: segure a tecla Instinto Hollow (R). Este glifo só existe para magias antigas e dispara um Cero de carga cheia.",
    G + "hollowfied_bala.desc": "(Antigo) A Bala agora é técnica inata: toque Instinto Hollow (R). Este glifo só existe para magias antigas.",
    G + "hollowfied_pesquisa.desc": "(Antigo) A Pesquisa agora é técnica inata: agachado + toque em Instinto Hollow, ou a Roda Hollow (H). Só para magias antigas.",
    G + "hollowfied_regen.desc": "(Antigo) A Regeneração agora é técnica inata, na Roda Hollow (H). Só para magias antigas.",
    G + "hollowfied_hierro.desc": "(Antigo) O Hierro agora é passivo e se concentra pela Roda Hollow (H). Só para magias antigas.",
    G + "hollowfied_sonido.desc": "(Antigo) O Sonído agora é técnica inata: correndo + Instinto Hollow (R). Só para magias antigas.",
    G + "hollowfied_garganta.desc": "(Antigo) A Garganta agora é técnica inata, na Roda Hollow (H). Só para magias antigas.",
    G + "hollowfied_gran_rey_cero.desc": "(Antigo) O Gran Rey Cero agora é técnica inata: agachado + segurar Instinto Hollow (R). Só para magias antigas.",
    G + "hollowfied_descorrer.desc": "(Antigo) O Descorrer agora é técnica inata, na Roda Hollow (H). Só para magias antigas.",
    G + "hollowfied_resurreccion.desc": "(Antigo) A Resurrección agora é técnica inata, na Roda Hollow (H), com invocação e transformação completas. Só para magias antigas.",
    # ---------------- estágios
    "hollow.isekai_mahou.rank_tip.0": "A fome nasce: Cero, Bala, Pesquisa e Regeneração — tudo na tecla Instinto Hollow (R) e na Roda Hollow (H).",
    "hollow.isekai_mahou.rank_tip.1": "O corpo incha de almas: Hierro (passivo −10% e concentrável) e o gatilho \"Ao Devorar uma Alma\".",
    "hollow.isekai_mahou.rank_tip.2": "A fera esperta: Sonído, Garganta e o aumento \"Cero Fusionado\". Ombreiras de osso.",
    "hollow.isekai_mahou.rank_tip.3": "O topo dos Hollows: chifres longos, Gran Rey Cero, Negación, Hierro −25% e o Cero negro-rubro.",
    "hollow.isekai_mahou.rank_tip.4": "A máscara se parte: forma humana, Resurrección, Descorrer e — liberado — o Cero Oscuras.",
    # ---------------- mensagens
    M + "became_tip.3": "• Sem devorar por 20 minutos, a fome enfraquece. A Regeneração (Roda Hollow) paga com Almas.",
    M + "became_tip.4": "• Suas técnicas são INATAS: tecla Instinto Hollow (padrão R) — toque = Bala, segure = Cero, agachado = Pesquisa, correndo = Sonído.",
    M + "became_tip.5": "• Roda Hollow (padrão H): as outras técnicas. Tudo explicado na aba Hollow do menu B.",
    M + "tech_cd": "%s: %s s",
    M + "tech_locked": "%s chega no estágio %s.",
    M + "tech_silenced": "Silenciado: o Reiatsu não sai.",
    M + "tech_exhausted": "Exausto: o Reiatsu não responde.",
    M + "tech_unlocked": "Técnicas novas: %s",
    M + "gran_rey_short": "O sangue ainda não se misturou ao Cero — segure mais.",
    M + "oscuras_shout": "Cero Oscuras!",
    M + "oscuras_ready": "A carga escurece... Cero Oscuras pronto — solte!",
    M + "release_already": "Você já está liberado.",
    M + "negacion_start": "Negación: a luz te envolve — nada te toca.",
    M + "negacion_end": "A luz te leva para longe.",
    M + "garganta_tearing": "As garras rasgam o céu...",
    M + "devour_trigger": "Alma devorada — a magia sai sozinha (+%s%%)",
    M + "devour_trigger_only": "Esta magia só sai sozinha, quando você devora uma alma.",
    M + "cmd_tech": "Técnica usada: %s",
    M + "cmd_tech_fail": "Não deu para usar essa técnica agora (id inválido, sem arquétipo ou anulada).",
    M + "cmd_cooldowns": "Recargas das técnicas Hollow zeradas.",
    M + "cmd_glyphs": "Glifos da classe Hollow aprendidos: Cero Fusionado e Ao Devorar uma Alma.",
    # ---------------- telas e HUD
    S + "techniques": "Técnicas (inatas)",
    S + "known": "(liberada)",
    S + "tech_keys": "Tecla %s = Instinto Hollow (toque, segure, agachado, correndo). Tecla %s = Roda Hollow. Não precisa montar magia nenhuma.",
    S + "class_glyphs": "Glifos da classe (Grimório)",
    S + "wheel": "Roda Hollow",
    S + "wheel_tip": "Mire e solte a tecla · clique · 1–7 · botão direito fecha",
    S + "wheel_locked": "Chega no estágio %s",
    S + "guide.4": "Regeneração (Roda Hollow) cura muito, mas custa 5 Almas. O Hierro é passivo e cresce com o corpo.",
    S + "guide.6": "No Arrancar a máscara se parte: escolha a Resurrección (uma vez) e libere-a pela Roda Hollow.",
    "hud.isekai_mahou.hollow.charging": "%s (carregando)",
}

EN = {
    "key.isekai_mahou.hollow_technique": "Hollow Instinct (techniques)",
    "key.isekai_mahou.hollow_wheel": "Hollow Wheel",
    T + "cero": "Cero",
    T + "cero.desc": "Energy gathers in the mask's mouth (at the fingertips, as an Arrancar) and fires as a beam that pierces EVERY enemy and explodes at the end. The longer you hold, the thicker, farther and stronger. Its color is your body's: crimson, scarlet, black-red, and your archetype's as an Arrancar. Released Murciélago: becomes the Lanza del Relámpago. Cost: 5–12% Vigor.",
    T + "cero.how": "Hold [Hollow Instinct] and release (min charge 0.5 s, full 1.8 s).",
    T + "bala": "Bala",
    T + "bala.desc": "Reiatsu punched into an instant shot, 20× faster than a Cero and much weaker: up to 24 blocks, small knockback. Made to spam: 4 shots per second. Cost: 1.2% Vigor per shot.",
    T + "bala.how": "Tap [Hollow Instinct] (fast taps = burst).",
    T + "gran_rey_cero": "Gran Rey Cero",
    T + "gran_rey_cero.desc": "A Cero mixed with your own blood (costs 2 health): a COLOSSAL 64-block beam that goes through walls and shakes the ground. From Vasto Lorde on; while released (Resurrección) it charges faster, costs less and hits ×1.5. Cost: 30% Vigor (22% released). Cooldown 45 s (30 s).",
    T + "gran_rey_cero.how": "Sneaking: hold [Hollow Instinct] 1.5 s (1 s released) and release.",
    T + "cero_oscuras": "Cero Oscuras",
    T + "cero_oscuras.desc": "RELEASED Arrancar only: the Cero turns BLACK, edged with your archetype's color. Goes through walls, withers whatever it touches and leaves a void at the end that pulses 3 times. Cost: 35% Vigor. Cooldown 60 s.",
    T + "cero_oscuras.how": "Released: hold the Cero until the charge turns dark (2.2 s) and release.",
    T + "sonido": "Sonído",
    T + "sonido.desc": "The Hollow's step: vanish with a snap and reappear up to 10 blocks ahead (+2 per stage), leaving an afterimage. Aiming at an enemy, you appear BEHIND it, facing it. Up to 3 steps in a row; then 2.5 s cooldown. Cost: 3% Vigor per step.",
    T + "sonido.how": "While sprinting: press [Hollow Instinct].",
    T + "pesquisa": "Pesquisa",
    T + "pesquisa.desc": "The Hollow's sense: a pulse reveals everything alive within 32 blocks, through walls, for 10 s. Hostiles in red, players in white, strong presences in black. Cost: 4% Vigor.",
    T + "pesquisa.how": "Sneaking: tap [Hollow Instinct] — or use the Hollow Wheel.",
    T + "regen": "Instant Regeneration",
    T + "regen.desc": "The flesh closes at once: heals 30% health (40% from Vasto Lorde on) and clears fire, poison and wither. Costs 5 Souls; without Souls, costs hunger. Cooldown 15 s.",
    T + "regen.how": "Hollow Wheel.",
    T + "hierro": "Hierro",
    T + "hierro.desc": "Iron skin. PASSIVE, grows with your body: −10% damage (Gillian), −15% (Adjuchas), −25% (Vasto Lorde), −30% (Arrancar). Concentrated: −30% for 10 s, or −50% and no knockback for 8 s from Vasto Lorde on. Cost: 8% Vigor.",
    T + "hierro.how": "Always passive; concentrate via the Hollow Wheel.",
    T + "garganta": "Garganta",
    T + "garganta.desc": "Sink your claws into the air and TEAR the sky (1.5 s): a black toothed maw opens and leads to the Hollow World — or back, if you are already there. Cost: 15% Vigor. Cooldown 30 s (plus the Hollow World shortcut cooldown).",
    T + "garganta.how": "Hollow Wheel.",
    T + "negacion": "Negación",
    T + "negacion.desc": "The Hollows' escape: a golden pillar of light descends on you for 3 s — NOTHING touches you (and you can't attack), pursuers lose their target and, when the light fades, it carries you up to 20 blocks away from the nearest enemy. Cost: 20% Vigor. Cooldown 3 min.",
    T + "negacion.how": "Hollow Wheel (Vasto Lorde and up).",
    T + "descorrer": "Descorrer",
    T + "descorrer.desc": "The Arrancar opens a rift in space and steps through: reappears where you look, up to 40 blocks. Cost: 10% Vigor. Cooldown 8 s.",
    T + "descorrer.how": "Hollow Wheel (Arrancar).",
    T + "resurreccion": "Resurrección",
    T + "resurreccion.desc": "The Arrancar's TRANSFORMATION: the release command, a pillar of Reiatsu in your archetype's color rises for 2 s and bursts — the pressure pushes and hurts everything within 8 blocks, the ground shakes and the released form (Pantera, Murciélago or Serpiente) lasts 60 s. Released: Hollow techniques +25%, stronger Gran Rey Cero and the Cero Oscuras. When it ends, the body pays (Weakness, Slowness, Vigor at 25%). Cost: 40% Vigor. Cooldown 5 min.",
    T + "resurreccion.how": "Hollow Wheel (Arrancar, archetype chosen).",
    G + "hollowfied_cero_fusionado": "Fused Cero",
    G + "hollowfied_cero_fusionado.desc": "Hollow augment: the spell gains a CERO — the moment it is cast, a thin beam fires from your mouth (or hand, as an Arrancar) where you look: 22 blocks, pierces every enemy (own damage 3, +1 per Hollow stage, with the spell's power), in your Cero's color. Doesn't apply the spell's effects. Costs 8 mana and +0.4 s cast time; doesn't stack.",
    G + "hollowfied_on_devour": "On Devouring a Soul",
    G + "hollowfied_on_devour.desc": "Hollow trigger (goes at the start of the spell): the slot fires BY ITSELF when you devour a soul, aiming at the nearest enemy — and grows stronger the bigger the soul: +1% power per Soul from that kill (up to +100%: bosses and players). Each fire spends 1 stored Soul. Pressing the slot key doesn't cast. At most once per second.",
    G + "hollowfied_cero.desc": "(Legacy) Cero is now an INNATE TECHNIQUE: hold the Hollow Instinct key (R). This glyph only exists for old spells and fires a fully charged Cero.",
    G + "hollowfied_bala.desc": "(Legacy) Bala is now an innate technique: tap Hollow Instinct (R). This glyph only exists for old spells.",
    G + "hollowfied_pesquisa.desc": "(Legacy) Pesquisa is now an innate technique: sneak + tap Hollow Instinct, or the Hollow Wheel (H). Old spells only.",
    G + "hollowfied_regen.desc": "(Legacy) Regeneration is now an innate technique, on the Hollow Wheel (H). Old spells only.",
    G + "hollowfied_hierro.desc": "(Legacy) Hierro is now passive and is concentrated via the Hollow Wheel (H). Old spells only.",
    G + "hollowfied_sonido.desc": "(Legacy) Sonído is now an innate technique: sprint + Hollow Instinct (R). Old spells only.",
    G + "hollowfied_garganta.desc": "(Legacy) Garganta is now an innate technique, on the Hollow Wheel (H). Old spells only.",
    G + "hollowfied_gran_rey_cero.desc": "(Legacy) Gran Rey Cero is now an innate technique: sneak + hold Hollow Instinct (R). Old spells only.",
    G + "hollowfied_descorrer.desc": "(Legacy) Descorrer is now an innate technique, on the Hollow Wheel (H). Old spells only.",
    G + "hollowfied_resurreccion.desc": "(Legacy) Resurrección is now an innate technique, on the Hollow Wheel (H), with full invocation and transformation. Old spells only.",
    "hollow.isekai_mahou.rank_tip.0": "Hunger is born: Cero, Bala, Pesquisa and Regeneration — all on the Hollow Instinct key (R) and the Hollow Wheel (H).",
    "hollow.isekai_mahou.rank_tip.1": "The body swells with souls: Hierro (passive −10%, can be concentrated) and the \"On Devouring a Soul\" trigger.",
    "hollow.isekai_mahou.rank_tip.2": "The clever beast: Sonído, Garganta and the \"Fused Cero\" augment. Bone pauldrons.",
    "hollow.isekai_mahou.rank_tip.3": "The peak of Hollows: long horns, Gran Rey Cero, Negación, Hierro −25% and the black-red Cero.",
    "hollow.isekai_mahou.rank_tip.4": "The mask breaks: human form, Resurrección, Descorrer and — when released — the Cero Oscuras.",
    M + "became_tip.3": "• 20 minutes without devouring weakens you. Regeneration (Hollow Wheel) costs Souls.",
    M + "became_tip.4": "• Your techniques are INNATE: Hollow Instinct key (default R) — tap = Bala, hold = Cero, sneaking = Pesquisa, sprinting = Sonído.",
    M + "became_tip.5": "• Hollow Wheel (default H): the other techniques. Everything is explained in the Hollow tab of the B menu.",
    M + "tech_cd": "%s: %s s",
    M + "tech_locked": "%s arrives at the %s stage.",
    M + "tech_silenced": "Silenced: your Reiatsu won't come out.",
    M + "tech_exhausted": "Exhausted: your Reiatsu doesn't answer.",
    M + "tech_unlocked": "New techniques: %s",
    M + "gran_rey_short": "The blood hasn't mixed into the Cero yet — hold longer.",
    M + "oscuras_shout": "Cero Oscuras!",
    M + "oscuras_ready": "The charge darkens... Cero Oscuras ready — release!",
    M + "release_already": "You are already released.",
    M + "negacion_start": "Negación: the light surrounds you — nothing can touch you.",
    M + "negacion_end": "The light carries you away.",
    M + "garganta_tearing": "Your claws tear the sky...",
    M + "devour_trigger": "Soul devoured — the spell fires by itself (+%s%%)",
    M + "devour_trigger_only": "This spell only fires by itself, when you devour a soul.",
    M + "cmd_tech": "Technique used: %s",
    M + "cmd_tech_fail": "Couldn't use that technique now (invalid id, no archetype, or nullified).",
    M + "cmd_cooldowns": "Hollow technique cooldowns reset.",
    M + "cmd_glyphs": "Hollow class glyphs learned: Fused Cero and On Devouring a Soul.",
    S + "techniques": "Techniques (innate)",
    S + "known": "(unlocked)",
    S + "tech_keys": "Key %s = Hollow Instinct (tap, hold, sneaking, sprinting). Key %s = Hollow Wheel. No spell building needed.",
    S + "class_glyphs": "Class glyphs (Grimoire)",
    S + "wheel": "Hollow Wheel",
    S + "wheel_tip": "Aim and release the key · click · 1–7 · right click closes",
    S + "wheel_locked": "Arrives at the %s stage",
    S + "guide.4": "Regeneration (Hollow Wheel) heals a lot but costs 5 Souls. Hierro is passive and grows with your body.",
    S + "guide.6": "As an Arrancar the mask breaks: choose your Resurrección (once) and release it from the Hollow Wheel.",
    "hud.isekai_mahou.hollow.charging": "%s (charging)",
}


def merge(path, add):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in add.items():
        data[k] = v
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    assert set(PT) == set(EN), sorted(set(PT) ^ set(EN))
    merge(os.path.join(LANG, "pt_br.json"), PT)
    merge(os.path.join(LANG, "en_us.json"), EN)
    print("ok:", len(PT), "chaves")


if __name__ == "__main__":
    main()
