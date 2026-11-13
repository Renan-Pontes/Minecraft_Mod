"""
Traducoes do modulo espada (pt_br + en_us) em src/main/lang/espada/. Falas parafraseadas (nada de citacao literal).

Uso:  py -I tools/gen/espada_lang.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
pt = {}
en = {}


def k(key, p, e):
    pt[key] = p
    en[key] = e


# entidades
k("entity.isekai_mahou.ulquiorra", "Ulquiorra Cifer", "Ulquiorra Cifer")
k("entity.isekai_mahou.barragan", "Barragan Louisenbairn", "Barragan Louisenbairn")
k("entity.isekai_mahou.grimmjow", "Grimmjow Jaegerjaquez", "Grimmjow Jaegerjaquez")
k("entity.isekai_mahou.espada_bolt", "Energia Espiritual", "Spirit Energy")
k("entity.isekai_mahou.barragan.fraccion", "Fracción de Barragan", "Barragan's Fracción")
# itens
k("item.isekai_mahou.reishi_restoration", "Restauração de Reishi", "Reishi Restoration")
k("item.isekai_mahou.reishi_restoration.desc", "Beber: tira TODO o Envelhecido na hora (leite não cura) e cura um pouco.",
  "Drink: removes ALL Aging at once (milk does not cure it) and heals a little.")
k("item.isekai_mahou.murcielago_wing", "Asa de Murciélago", "Murciélago Wing")
k("item.isekai_mahou.murcielago_wing.desc", "Usar: Sonído — some e reaparece até 9 blocos à frente.",
  "Use: Sonído — vanish and reappear up to 9 blocks ahead.")
k("item.isekai_mahou.arrogante_shard", "Fragmento da Arrogante", "Arrogante Shard")
k("item.isekai_mahou.arrogante_shard.desc", "Usar: sopro de Respira — envelhece as criaturas à frente e desfaz projéteis em pó.",
  "Use: a breath of Respira — ages creatures ahead and turns projectiles to dust.")
k("item.isekai_mahou.pantera_claw", "Garra da Pantera", "Pantera Claw")
k("item.isekai_mahou.pantera_claw.desc", "Usar: Desgarrón — cinco garras azuis rasgam tudo à frente.",
  "Use: Desgarrón — five blue claws tear through everything ahead.")
k("item.isekai_mahou.ulquiorra_spawn_egg", "Ovo de Ulquiorra Cifer", "Ulquiorra Cifer Spawn Egg")
k("item.isekai_mahou.barragan_spawn_egg", "Ovo de Barragan Louisenbairn", "Barragan Louisenbairn Spawn Egg")
k("item.isekai_mahou.grimmjow_spawn_egg", "Ovo de Grimmjow Jaegerjaquez", "Grimmjow Jaegerjaquez Spawn Egg")
# status
k("effect.isekai_mahou.aged", "Envelhecido", "Aged")
k("effect.isekai_mahou.aged.description",
  "O Respira de Barragan envelheceu seu corpo: mais lento, mais fraco, menos vida, fome e armadura apodrecendo. Leite não cura.",
  "Barragan's Respira aged your body: slower, weaker, less health, hunger and rotting armor. Milk does not cure it.")
# mensagens gerais e comandos
k("message.isekai_mahou.espada.restored", "Seu corpo se refaz: o Envelhecido sumiu.", "Your body mends itself: the Aging is gone.")
k("message.isekai_mahou.espada.restored_none", "As partículas espirituais restauram suas forças.", "The spirit particles restore your strength.")
k("command.isekai_mahou.espada.spawn_done", "Espada invocado: %s", "Espada summoned: %s")
k("command.isekai_mahou.espada.spawn_fail", "Não deu para invocar o Espada aqui.", "Could not summon the Espada here.")
k("command.isekai_mahou.espada.aged", "Envelhecido aplicado: %s acúmulo(s).", "Aged applied: %s stack(s).")
k("command.isekai_mahou.espada.cured", "Envelhecido removido.", "Aged removed.")

BOSS = {
    "ulquiorra": dict(
        subtitle=("Cuarta Espada — o aspecto do Desespero", "Cuarta Espada — the aspect of Despair"),
        announce={
            "intro": ("Ulquiorra Cifer, a Cuarta Espada, abre os olhos verdes e frios.", "Ulquiorra Cifer, the Cuarta Espada, opens his cold green eyes."),
            "death": ("Ulquiorra se desfaz em cinzas, levado pelo vento de Hueco Mundo.", "Ulquiorra crumbles into ash, carried off by the wind of Hueco Mundo."),
            "oscuras": ("Cero Oscuras!", "Cero Oscuras!"),
            "lanza": ("Lanza del Relámpago!", "Lanza del Relámpago!"),
            "hierro_break": ("O Hierro de Ulquiorra rachou! Agora ele sente os golpes!", "Ulquiorra's Hierro cracked! Now he feels your blows!"),
        },
        quotes={
            "intro": ("Lixo. Você veio até aqui só para medir a distância entre nós.", "Trash. You came all this way only to measure the distance between us."),
            "q1": ("Isso é tudo? Nem vale a pena tirar as mãos dos bolsos.", "Is that all? Not even worth taking my hands out of my pockets."),
            "q2": ("Não vejo nada nos seus olhos que eu precise temer.", "I see nothing in your eyes that I need to fear."),
            "q3": ("Coração... vocês repetem essa palavra como se ela mudasse algo.", "Heart... you keep saying that word as if it changed anything."),
            "q4": ("Resistir só adia o inevitável.", "Resisting only delays the inevitable."),
            "release1": ("Enclávate... Murciélago.", "Enclávate... Murciélago."),
            "q_mur1": ("Agora você entende o que é desespero?", "Do you understand despair now?"),
            "q_mur2": ("Nenhum outro Espada viu esta forma.", "No other Espada has seen this form."),
            "q_mur3": ("Sua esperança é só ruído.", "Your hope is only noise."),
            "release2": ("Resurrección: Segunda Etapa.", "Resurrección: Segunda Etapa."),
            "q_seg1": ("Olhe bem. Isto é o desespero de verdade.", "Look closely. This is true despair."),
            "q_seg2": ("Não sobrou nada em você para quebrar.", "There is nothing left in you to break."),
            "q_seg3": ("Por que você ainda se levanta?", "Why do you still stand up?"),
            "regen": ("Inútil. Meu corpo se refaz.", "Useless. My body mends itself."),
            "tail": ("Fique quieto.", "Be still."),
            "death": ("Então... era isso que vocês guardavam no peito.", "So... this is what you kept inside your chest."),
            "death2": ("Eu teria gostado... de entender um pouco mais.", "I would have liked... to understand a little more."),
        },
        messages={
            "hierro": ("Hierro: golpes fracos quase não passam. Use golpes FORTES para rachar a pele dele!",
                       "Hierro: weak hits barely get through. Use STRONG hits to crack his skin!"),
            "murcielago": ("Murciélago: ele voa e se regenera. Cuidado com o Cero Oscuras!", "Murciélago: he flies and regenerates. Beware of Cero Oscuras!"),
            "oscuras": ("Cero Oscuras: saia da faixa escura marcada no chão!", "Cero Oscuras: get out of the dark marked lane!"),
            "segunda": ("Segunda Etapa: cuidado com a cauda e com a Lanza del Relámpago!", "Segunda Etapa: watch the tail and the Lanza del Relámpago!"),
            "lanza": ("Lanza del Relámpago: o alvo segue você e trava — depois corra para fora do círculo vermelho!",
                      "Lanza del Relámpago: the mark follows you, then locks — run out of the red circle!"),
            "dome": ("Ulquiorra olha para o alto... a Segunda Etapa só desperta no topo da cúpula.",
                     "Ulquiorra looks up... the Segunda Etapa only awakens atop the dome."),
        },
        attacks={
            "bala": ("Bala", "Bala"), "cero": ("Cero", "Cero"), "cero_oscuras": ("Cero Oscuras", "Cero Oscuras"),
            "luz_luna": ("Luz de la Luna", "Luz de la Luna"), "lanza": ("Lanza del Relámpago", "Lanza del Relámpago"),
            "lanza_blast": ("Explosão da Lanza", "Lanza Blast"), "tail": ("Cauda", "Tail"),
        },
        releases={
            "release1": ("Resurrección: Murciélago", "Resurrección: Murciélago"),
            "release1.sub": ("Enclávate, Murciélago — chove reiatsu negro", "Enclávate, Murciélago — black reiatsu rains down"),
            "release2": ("Resurrección: Segunda Etapa", "Resurrección: Segunda Etapa"),
            "release2.sub": ("A escuridão cai sobre Las Noches", "Darkness falls over Las Noches"),
        },
        title=("Aquele que Tocou o Coração", "The One Who Touched the Heart"),
    ),
    "barragan": dict(
        subtitle=("Segunda Espada — o aspecto da Velhice", "Segunda Espada — the aspect of Old Age"),
        announce={
            "intro": ("Barragan Louisenbairn, a Segunda Espada, ergue-se do trono.", "Barragan Louisenbairn, the Segunda Espada, rises from his throne."),
            "death": ("O Deus-Rei vira pó — o mesmo pó que ele espalhava.", "The God-King turns to dust — the same dust he spread."),
            "phase1": ("O Rei convoca a sua Fracción!", "The King summons his Fracción!"),
            "respira": ("Respira!", "Respira!"),
            "dome": ("Domo de Respira!", "Respira Dome!"),
        },
        quotes={
            "intro": ("Ajoelhe-se. Você está diante do Deus-Rei de Hueco Mundo.", "Kneel. You stand before the God-King of Hueco Mundo."),
            "q1": ("Tudo envelhece. Tudo apodrece. Até você.", "Everything ages. Everything rots. Even you."),
            "q2": ("O tempo é meu servo, verme.", "Time is my servant, worm."),
            "q3": ("Que insolência, encarar um rei de pé!", "Such insolence, facing a king standing up!"),
            "q4": ("Sinta seus ossos ficando frágeis.", "Feel your bones growing brittle."),
            "fraccion": ("Fracción! Livrem-me desses vermes!", "Fracción! Rid me of these worms!"),
            "release": ("Apodreça, Arrogante!", "Rot, Arrogante!"),
            "q_rel1": ("Nada que me toca sobrevive ao tempo!", "Nothing that touches me survives time!"),
            "q_rel2": ("Suas armas? Pó. Sua carne? Pó.", "Your weapons? Dust. Your flesh? Dust."),
            "q_rel3": ("Eu sou a morte que chega para todos!", "I am the death that comes for all!"),
            "aged": ("Veja só... já está de costas curvadas.", "Look at you... already bent with age."),
            "death": ("Impossível... o rei... envelhecendo...?", "Impossible... the king... aging...?"),
        },
        messages={
            "respira": ("Respira: o miasma ENVELHECE! Saia do cone e não fique na nuvem.", "Respira: the miasma AGES you! Leave the cone and stay out of the cloud."),
            "projectiles": ("Projéteis apodrecem perto de Barragan — só metade do dano passa.", "Projectiles rot near Barragan — only half the damage gets through."),
            "arrogante": ("Arrogante: ficar colado nele envelhece. Leite não cura — use a Restauração de Reishi.",
                          "Arrogante: standing close ages you. Milk does not cure it — use Reishi Restoration."),
        },
        attacks={
            "gran_caida": ("Gran Caída", "Gran Caída"), "gran_caida_throw": ("Gran Caída Arremessada", "Thrown Gran Caída"),
            "respira": ("Respira", "Respira"), "dome": ("Domo de Respira", "Respira Dome"), "grab": ("Mão do Respira", "Hand of Respira"),
        },
        releases={
            "release": ("Resurrección: Arrogante", "Resurrección: Arrogante"),
            "release.sub": ("A carne vira pó; resta o rei de ossos", "Flesh turns to dust; the king of bones remains"),
        },
        title=("Mais Velho que o Tempo", "Older Than Time"),
    ),
    "grimmjow": dict(
        subtitle=("Sexta Espada — o aspecto da Destruição", "Sexta Espada — the aspect of Destruction"),
        announce={
            "intro": ("Grimmjow Jaegerjaquez, a Sexta Espada, sorri para a briga.", "Grimmjow Jaegerjaquez, the Sexta Espada, grins at the fight."),
            "death": ("Grimmjow cai, rangendo os dentes até o fim.", "Grimmjow falls, grinding his teeth to the very end."),
            "gran_rey": ("Gran Rey Cero!", "Gran Rey Cero!"),
            "desgarron": ("Desgarrón!", "Desgarrón!"),
        },
        quotes={
            "intro": ("Finalmente alguém que vale a pena! Vem, mostra o que tem!", "Finally someone worth it! Come on, show me what you've got!"),
            "q1": ("É só isso? Eu nem suei ainda!", "That's it? I haven't even broken a sweat!"),
            "q2": ("Eu sou o rei! Ninguém fica de pé na minha frente!", "I'm the king! Nobody stands in front of me!"),
            "q3": ("Mais! Me dá mais!", "More! Give me more!"),
            "q4": ("Gosto desse olhar. Vou arrancá-lo da sua cara!", "I like that look. I'm gonna tear it off your face!"),
            "release": ("Kishire, Pantera!", "Kishire, Pantera!"),
            "q_rel1": ("Agora sim! Isso é luta de verdade!", "Now we're talking! This is a real fight!"),
            "q_rel2": ("Vou te despedaçar com estas garras!", "I'll shred you with these claws!"),
            "q_rel3": ("Eu vou ser o rei! Sai do meu caminho!", "I'll be the king! Out of my way!"),
            "death": ("Droga... eu ainda... não terminei...", "Damn it... I'm not... done yet..."),
        },
        messages={
            "pantera": ("Pantera: muito mais rápido! O rugido empurra todos em volta.", "Pantera: much faster! His roar blasts everyone away."),
            "gran_rey": ("Gran Rey Cero: saia da faixa azul marcada!", "Gran Rey Cero: get out of the blue marked lane!"),
            "desgarron": ("Desgarrón: cinco garras, uma depois da outra — saia das cinco faixas!",
                          "Desgarrón: five claws, one after another — leave all five lanes!"),
        },
        attacks={
            "cero": ("Cero", "Cero"), "gran_rey_cero": ("Gran Rey Cero", "Gran Rey Cero"),
            "desgarron": ("Desgarrón", "Desgarrón"), "garra": ("Garra de la Pantera", "Garra de la Pantera"),
        },
        releases={
            "release": ("Resurrección: Pantera", "Resurrección: Pantera"),
            "release.sub": ("Kishire, Pantera — o rei-pantera desperta", "Kishire, Pantera — the panther king awakens"),
        },
        title=("Rival da Pantera", "Rival of the Panther"),
    ),
}

for b, d in BOSS.items():
    k("boss.isekai_mahou.%s.subtitle" % b, *d["subtitle"])
    for n, (p, e) in d["announce"].items():
        k("announce.isekai_mahou.%s.%s" % (b, n), p, e)
    for n, (p, e) in d["quotes"].items():
        k("quote.isekai_mahou.%s.%s" % (b, n), p, e)
    for n, (p, e) in d["messages"].items():
        k("message.isekai_mahou.%s.%s" % (b, n), p, e)
    for n, (p, e) in d["attacks"].items():
        k("attack.isekai_mahou.%s.%s" % (b, n), p, e)
    for n, (p, e) in d["releases"].items():
        k("boss.isekai_mahou.%s.%s" % (b, n), p, e)
    k("title.isekai_mahou.espada.%s" % b, *d["title"])

# lendas
k("legend.isekai_mahou.espada_lanza_relampago", "Lanza del Relámpago", "Lanza del Relámpago")
k("legend.isekai_mahou.espada_respira", "Respira", "Respira")
k("legend.isekai_mahou.espada_desgarron", "Desgarrón", "Desgarrón")

# legendas dos sons
SUBS = {
    "sonido": ("Sonído", "Sonído"), "cero_charge": ("Cero carregando", "Cero charging"), "cero_fire": ("Cero disparado", "Cero fired"),
    "lanza_charge": ("Lança de energia crepita", "Energy lance crackles"), "lanza_explode": ("Explosão gigante", "Massive explosion"),
    "release": ("Resurrección", "Resurrección"), "respira": ("Respira sopra", "Respira breathes"),
    "crumble": ("Algo vira pó", "Something crumbles to dust"), "claw": ("Garras rasgam", "Claws tear"),
    "roar": ("Rugido de pantera", "Panther roars"), "bala": ("Bala disparada", "Bala fired"), "hierro": ("Golpe bate no Hierro", "Hit glances off Hierro"),
}
for n, (p, e) in SUBS.items():
    k("subtitles.isekai_mahou.espada." + n, p, e)


def main():
    for name, d in (("pt_br", pt), ("en_us", en)):
        path = os.path.join(ROOT, "src/main/lang/espada", name + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("espada: %d chaves" % len(pt))


if __name__ == "__main__":
    main()
