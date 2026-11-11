"""
Pacote de técnicas de anime do Ladino (class_rogue): traduções.
Uso (na raiz do projeto):  py -I tools/gen/class_rogue_techniques.py

As traduções do módulo são escritas à mão em src/main/lang/class_rogue/{pt_br,en_us}.json; este script só
ACRESCENTA/ATUALIZA as chaves do pacote (glifos, mensagens, anúncios, entidade, lendas), sem mexer nas outras.
A imagem residual é desenhada com a pele do jogador (não precisa de textura nova).
Confere que todo '%' é um marcador válido (%s, %N$s ou %%).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LANG = os.path.join(ROOT, "src", "main", "lang", "class_rogue")
G = "glyph.isekai_mahou."
M = "message.isekai_mahou.class_rogue."
E = []


def e(key, pt, en):
    E.append((key, pt, en))


# ------------------------------------------------------------------ entidade, anúncios, mensagens, lendas
e("entity.isekai_mahou.rogue_afterimage", "Imagem Residual", "Afterimage")
e("announce.isekai_mahou.rogue_rhythm_echo", "Ritmo de Eco", "Rhythm Echo")
e("announce.isekai_mahou.rogue_godspeed", "Velocidade Divina: Rajada!", "Godspeed: Whirlwind!")
e("announce.isekai_mahou.rogue_one_cut", "Um corte...", "One cut...")
e("announce.isekai_mahou.rogue_one_cut.kill", "...uma morte. Eliminado.", "...one death. Eliminated.")
e(M + "lurk", "Espreitando por %s s: os monstros esquecem você.", "Lurking for %s s: monsters forget you.")
e(M + "lurk_strike", "Da espreita: golpe pelas costas!", "From the shadows: a strike from behind!")
e(M + "lurk_broken", "Você foi notado: a espreita acabou.", "You were noticed: the lurk is over.")
e(M + "lurk_end", "A espreita acabou.", "Your lurk ended.")
e(M + "drain", "Toque Dreno: +%s de vida, +%s de %s.", "Drain Touch: +%s health, +%s %s.")
e(M + "godspeed", "Velocidade Divina por %s s: o corpo virou raio.", "Godspeed for %s s: your body turned to lightning.")
e(M + "godspeed_dodge", "Reflexo de raio: desviou e contra-atacou!", "Lightning reflex: dodged and countered!")
e(M + "heart", "Coração arrancado!", "Heart ripped out!")
e(M + "tech_catchup", "Técnicas novas do Livro-Razão aprendidas: %s.", "New Ledger techniques learned: %s.")
e("legend.isekai_mahou.rogue_godspeed_thunder", "Velocidade Divina: Trovão Ligeiro", "Godspeed: Swift Thunder")
e("legend.isekai_mahou.rogue_one_cut_one_kill", "Um Corte, Uma Morte", "One Cut, One Kill")
e("legend.isekai_mahou.rogue_thousand_shadows", "Ritmo de Eco: Dança das Mil Sombras", "Rhythm Echo: Dance of a Thousand Shadows")

# ------------------------------------------------------------------ glifos
e(G + "rogue_lurk", "Espreitar", "Lurk")
e(G + "rogue_lurk.desc", "EFEITO do Ladino (Sombra, benéfico; homenagem à habilidade de ladrão do aventureiro azarado de outro "
  "mundo). Você (ou um aliado) some por 15 s (Estender +): invisível e ESQUECIDO pelos monstros — quem não estiver colado (2,5 "
  "blocos) larga o alvo e não consegue mirar de novo. Atacar revela, e esse ataque conta como pelas costas; levar dano também "
  "revela. Chefes não se deixam enganar. Desbloqueio: Livro-Razão, rank I.",
  "Rogue EFFECT (Shadow, beneficial; homage to the thief skill of the unlucky adventurer from another world). You (or an ally) "
  "vanish for 15 s (Extend +): invisible and FORGOTTEN by monsters — anything not right next to you (2.5 blocks) drops its target "
  "and can't aim again. Attacking reveals you, and that attack counts as from behind; taking damage also reveals you. Bosses "
  "aren't fooled. Unlock: Ledger, rank I.")
e(G + "rogue_drain_touch", "Toque Dreno", "Drain Touch")
e(G + "rogue_drain_touch.desc", "EFEITO do Ladino (Sombra; homenagem à habilidade de lich que o aventureiro azarado aprendeu). A mão "
  "suga VIDA e RECURSO: 5 de dano e você recupera 60%% do dano causado em vida. De um jogador, 4%% do recurso máximo DELE passa "
  "para você; de criaturas, você ganha 2,5%% do SEU máximo (4%% de criaturas mágicas). Desbloqueio: Livro-Razão, rank I.",
  "Rogue EFFECT (Shadow; homage to the lich skill the unlucky adventurer learned). Your hand drains HEALTH and RESOURCE: 5 damage "
  "and you recover 60%% of the damage dealt as health. From a player, 4%% of THEIR max resource flows to you; from creatures, you "
  "gain 2.5%% of YOUR max (4%% from magical creatures). Unlock: Ledger, rank I.")
e(G + "rogue_assassin_claw", "Garra Assassina", "Assassin's Claw")
e(G + "rogue_assassin_claw.desc", "EFEITO do Ladino (Sombra, Círculo 2; homenagem à mão-garra da família de assassinos de elite). Os "
  "dedos viram garras: 9 de dano (×1,5 em quem já sangra) e 2 de Sangramento. Pelas costas, se a criatura ficar com 25%% da "
  "vida ou menos: ARRANCAR O CORAÇÃO — morte na hora e você recupera 15%% da vida. Chefes não morrem assim; em jogadores vira "
  "+40%% de dano. Desbloqueio: Livro-Razão, rank II.",
  "Rogue EFFECT (Shadow, Circle 2; homage to the claw-hand of the elite assassin family). Your fingers become claws: 9 damage (×1.5 "
  "against bleeding targets) and 2 Bleeding. From behind, if the creature is left at 25%% health or less: RIP OUT THE HEART — "
  "instant death and you recover 15%% of your health. Bosses don't die this way; against players it becomes +40%% damage. "
  "Unlock: Ledger, rank II.")
e(G + "rogue_rhythm_echo", "Ritmo de Eco", "Rhythm Echo")
e(G + "rogue_rhythm_echo.desc", "FORMA do Ladino (Sombra, Círculo 3; homenagem aos passos em ritmo do jovem assassino). Você corre em "
  "volta do inimigo na mira (até 14 blocos; Acelerar +3) num ritmo que confunde os olhos: 3 imagens residuais com a sua aparência "
  "(+1 por Dividir, até 6) giram em volta dele enquanto você some. O alvo se perde (monstros largam o alvo e ficam lentos). "
  "Depois de 1,2 s cada imagem avança e aplica os efeitos com 40%% do poder, e você aparece NAS COSTAS dele e aplica os efeitos "
  "inteiros (Apunhalar ×2,5!). Sem alvo: as imagens viram iscas em volta de você. Custo: 1 + 0,4 cópia por imagem. Desbloqueio: "
  "Livro-Razão, rank III.",
  "Rogue FORM (Shadow, Circle 3; homage to the young assassin's rhythmic steps). You run around the aimed enemy (up to 14 blocks; "
  "Accelerate +3) in a rhythm that fools the eye: 3 afterimages that look like you (+1 per Split, up to 6) circle them while you "
  "vanish. The target is lost (monsters drop their target and slow down). After 1.2 s each image lunges and applies the effects at "
  "40%% power, then you appear BEHIND them and apply the full effects (Backstab ×2.5!). With no target: the images become decoys "
  "around you. Cost: 1 + 0.4 copy per image. Unlock: Ledger, rank III.")
e(G + "rogue_godspeed", "Velocidade Divina", "Godspeed")
e(G + "rogue_godspeed.desc", "EFEITO do Ladino (Raio, Círculo 5; homenagem ao corpo elétrico do herdeiro da família de assassinos). "
  "Em você (Si Mesmo) — RAJADA: 10 s (Estender +) de corpo elétrico — Velocidade III, Salto II e Pressa II; o primeiro golpe que "
  "viria a cada 1 s é DESVIADO por reflexo (fica uma imagem residual), você aparece nas costas de quem atacou e contra-ataca com "
  "6 de raio; seus golpes corpo a corpo eletrocutam. Num inimigo — TROVÃO: um relâmpago desce no alvo (8 de dano de raio), "
  "Eletrocutado e Atordoado por 1 s (chefes não ficam atordoados). Desbloqueio: Livro-Razão, rank IV.",
  "Rogue EFFECT (Lightning, Circle 5; homage to the electric body of the assassin family's heir). On yourself (Self) — WHIRLWIND: "
  "10 s (Extend +) of electric body — Speed III, Jump II and Haste II; the first blow coming at you each second is DODGED by "
  "reflex (leaving an afterimage), you appear behind the attacker and counter with 6 lightning damage; your melee hits shock. On "
  "an enemy — THUNDER: a lightning bolt strikes the target (8 lightning damage), Shocked and Stunned for 1 s (bosses aren't "
  "stunned). Unlock: Ledger, rank IV.")
e(G + "rogue_one_cut", "Lâmina Maldita: Um Corte, Uma Morte", "Cursed Blade: One Cut, One Kill")
e(G + "rogue_one_cut.desc", "EFEITO do Ladino (Sombra, Círculo 7; homenagem à espada imperial amaldiçoada da assassina de olhos "
  "vermelhos). Um corte de 6 deixa no alvo uma maldição que sobe pelo corpo como marcas negras; em 3 s ela chega ao coração: "
  "criaturas comuns MORREM (veneno amaldiçoado, sem cura). Chefes perdem 12%% da vida máxima e jogadores 30%% (até 400, × "
  "poder) e murcham — contra eles ninguém morre na hora. Cortar de novo não reinicia a contagem. Desbloqueio: Livro-Razão, "
  "rank V.",
  "Rogue EFFECT (Shadow, Circle 7; homage to the cursed imperial sword of the red-eyed assassin). A cut for 6 leaves a curse that "
  "climbs the body as black marks; in 3 s it reaches the heart: ordinary creatures DIE (cursed poison, no cure). Bosses lose 12%% "
  "of max health and players 30%% (up to 400, × power) and wither — against them no one dies instantly. Cutting again doesn't "
  "reset the countdown. Unlock: Ledger, rank V.")

VALID = re.compile(r"%(?:\d+\$)?s|%%")


def check(text, key):
    if "%" in VALID.sub("", text):
        sys.exit("'%' solto em " + key + ": " + text)


def main():
    keys = set()
    for key, p, n in E:
        if key in keys:
            sys.exit("chave repetida: " + key)
        keys.add(key)
        check(p, key)
        check(n, key)
    for name, idx in (("pt_br", 1), ("en_us", 2)):
        path = os.path.join(LANG, name + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for entry in E:
            data[entry[0]] = entry[idx]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("class_rogue_techniques: %d chaves" % len(E))


if __name__ == "__main__":
    main()
