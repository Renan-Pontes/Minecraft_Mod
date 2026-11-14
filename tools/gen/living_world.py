"""
v1.1 "mundo vivo" — traduções das partes em Java (só escreve src/main/lang/living_world/{pt_br,en_us}.json).

Cobre: opção "worldLife" (ambience/client/AmbientLife), Chuva de Estrelas (world_events/event/StarShower),
encontros na estrada (world_events/WeEncounters), falas das crianças (cities/entity/Citizen).
Pode rodar quantas vezes quiser: não mexe em nenhum outro arquivo.

    py -X utf8 -I tools/gen/living_world.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "lang", "living_world")

PT, EN = {}, {}


def tr(key, pt, en):
    assert key not in PT, key
    PT[key] = pt
    EN[key] = en


def lines(prefix, pairs):
    for i, (pt, en) in enumerate(pairs):
        tr(f"{prefix}.{i}", pt, en)


# ---------------------------------------------------------------- opção (Mods > Isekai Mahou > Config)
tr("isekai_mahou.configuration.worldLife", "Mundo vivo", "Living world")
tr("isekai_mahou.configuration.worldLife.tooltip",
   "Vaga-lumes nas noites quentes, folhas caindo das copas, pólen nos campos, motas de mana nos biomas mágicos e "
   "estrelas cadentes. Leve; desligado no preset gráfico BAIXO.",
   "Fireflies on warm nights, leaves falling from the canopy, pollen over meadows, mana motes in magic biomes and "
   "shooting stars. Light; off on the LOW graphics preset.")

# ---------------------------------------------------------------- Chuva de Estrelas
tr("world_event.isekai_mahou.star_shower", "Chuva de Estrelas", "Star Shower")
tr("world_event.isekai_mahou.star_shower.start", "Estrelas cadentes riscam o céu esta noite. Algumas chegam ao chão...",
   "Shooting stars streak across the sky tonight. Some reach the ground...")
tr("world_event.isekai_mahou.star_shower.end", "A Chuva de Estrelas terminou.", "The Star Shower is over.")
tr("hint.isekai_mahou.world_events.star_shower",
   "Chuva de Estrelas: fique ao ar livre e siga os rastros de luz — fragmentos caem perto de você.",
   "Star Shower: stay outdoors and follow the trails of light — fragments fall near you.")

# ---------------------------------------------------------------- Encontros na estrada
lines("encounter.isekai_mahou.travellers.greet", [
    ("Bons ventos, viajante! A estrada anda tranquila? Dê uma olhada nas mercadorias.",
     "Fair winds, traveller! Is the road quiet? Have a look at my wares."),
    ("Ah, um rosto amigo. Estamos indo para a cidade — quer negociar antes?",
     "Ah, a friendly face. We're heading to town — care to trade first?"),
    ("Caravana do reino! Preço de estrada, só hoje.", "Kingdom caravan! Road prices, today only."),
])
lines("encounter.isekai_mahou.pilgrims.greet", [
    ("Que os céus guiem seus passos. Vamos em peregrinação à cidade.",
     "May the heavens guide your steps. We're on pilgrimage to the city."),
    ("Olá! Faz dias que andamos... ainda bem que a estrada tem gente boa.",
     "Hello! We've been walking for days... good thing the road has kind folk."),
    ("Ouviu a canção? Nosso bardo não para nunca!", "Did you hear the song? Our bard never stops!"),
])
lines("encounter.isekai_mahou.wounded.help", [
    ("Ei... você aí... por favor, me ajude...", "Hey... you there... please, help me..."),
    ("Fui atacado na estrada... não consigo andar...", "I was attacked on the road... I can't walk..."),
    ("Um pouco de comida... ou uma poção... qualquer coisa...", "Some food... or a potion... anything..."),
])
tr("encounter.isekai_mahou.wounded.need", "Comida ou uma poção... por favor. (Clique com ela na mão.)",
   "Food or a potion... please. (Use it on me.)")
lines("encounter.isekai_mahou.wounded.thanks", [
    ("Obrigado! Você salvou a minha vida.", "Thank you! You saved my life."),
    ("Que os deuses te paguem, aventureiro.", "May the gods repay you, adventurer."),
])
tr("encounter.isekai_mahou.wounded.rumor",
   "%1$s te dá algumas moedas e avisa: \"Cuidado com %2$s, a uns %3$s blocos para o %4$s. Foi lá que me pegaram.\"",
   "%1$s gives you some coins and warns: \"Beware of %2$s, about %3$s blocks to the %4$s. That's where they got me.\"")
tr("encounter.isekai_mahou.wounded.reward", "%s te agradece e te dá algumas moedas.",
   "%s thanks you and gives you some coins.")
tr("encounter.isekai_mahou.fairy.blessing", "As fadas dançam à sua volta — Bênção das Fadas!",
   "The fairies dance around you — Fairy Blessing!")
tr("hint.isekai_mahou.encounter.wounded",
   "Alguém ferido na estrada: clique nele com comida ou uma poção na mão.",
   "Someone wounded on the road: use food or a potion on them.")
tr("hint.isekai_mahou.encounter.fairy_ring",
   "Um brilho na grama, ali perto... um círculo de fadas. Fique dentro dele por alguns segundos.",
   "A glow in the grass nearby... a fairy ring. Stand inside it for a few seconds.")
tr("command.isekai_mahou.encounter.started", "Encontro \"%s\" começou perto de você.", "Encounter \"%s\" started near you.")
tr("command.isekai_mahou.encounter.failed",
   "Não deu: tipo desconhecido (travellers, wounded, fairy_ring), fora do mundo normal ou sem chão livre por perto.",
   "Failed: unknown kind (travellers, wounded, fairy_ring), not in the Overworld, or no free ground nearby.")
tr("command.isekai_mahou.encounter.cleared", "%s encontro(s) encerrado(s).", "%s encounter(s) cleared.")

# ---------------------------------------------------------------- crianças da cidade
lines("dialogue.isekai_mahou.cities.kid", [
    ("Pega-pega! Você tá com ela!", "Tag! You're it!"),
    ("Quando eu crescer vou ser aventureiro igual a você!", "When I grow up I'll be an adventurer like you!"),
    ("Você já viu um dragão de verdade?", "Have you ever seen a real dragon?"),
    ("Minha mãe disse pra não falar com estranhos... mas você parece legal.",
     "My mum said not to talk to strangers... but you look nice."),
    ("Me mostra uma magia? Só uma, vai!", "Show me a spell? Just one, please!"),
    ("O guarda não consegue me pegar, eu sou o mais rápido!", "The guard can't catch me, I'm the fastest!"),
])


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(data.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    assert PT.keys() == EN.keys()
    write("pt_br.json", PT)
    write("en_us.json", EN)
    print(f"living_world: {len(PT)} chaves")
