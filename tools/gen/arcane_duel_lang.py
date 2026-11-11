"""
Gera as traducoes do modulo arcane_duel (pt_br e en_us) em src/main/lang/arcane_duel/.

  py -I tools/gen/arcane_duel_lang.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "lang", "arcane_duel")

M = "message.isekai_mahou.arcane_duel."

# chave: (pt_br, en_us)
T = {
    # ---------------- Glifos ----------------
    "glyph.isekai_mahou.mana_leak": ("Vazamento de Mana", "Mana Leak"),
    "glyph.isekai_mahou.mana_leak.desc": (
        "Contraria conjurações MENORES pendentes na Pilha perto do alvo (MAIORES com Amplificar). Um jogador pode pagar 30 de mana para "
        "salvar a magia; sem mana, ela vaza. Num jogador sem nada na Pilha: drena 30 de mana.",
        "Counters MINOR pending casts on the Stack near the target (MAJOR with Amplify). A player may pay 30 mana to save the spell; "
        "without mana, it leaks away. On a player with nothing on the Stack: drains 30 mana."),
    "glyph.isekai_mahou.redirect": ("Redirecionar", "Redirect"),
    "glyph.isekai_mahou.redirect.desc": (
        "Projéteis inimigos perto do alvo passam a ser seus e voltam, mais rápidos, para quem os lançou. Em você: um espelho em volta.",
        "Enemy projectiles near the target become yours and fly back, faster, at whoever shot them. On yourself: a mirror around you."),
    "glyph.isekai_mahou.fork": ("Copiar Magia", "Fork"),
    "glyph.isekai_mahou.fork.desc": (
        "Copia a última magia de glifo conjurada por OUTRO (jogador, mob ou chefe) a até 16 blocos nos últimos 4 s e a conjura como sua, "
        "saindo de você na sua mira. Não copia Supremas (G) nem outra cópia.",
        "Copies the last glyph spell cast by SOMEONE ELSE (player, mob or boss) within 16 blocks in the last 4 s and casts it as yours, "
        "from you towards your aim. Can't copy ultimates (G) or another copy."),
    "glyph.isekai_mahou.spell_steal": ("Roubar Encanto", "Spell Steal"),
    "glyph.isekai_mahou.spell_steal.desc": (
        "Arranca até 6 efeitos benéficos do alvo e os coloca em você (Amplificar/Estender: duram mais).",
        "Rips up to 6 beneficial effects off the target and puts them on you (Amplify/Extend: they last longer)."),
    "glyph.isekai_mahou.deathtouch": ("Toque Mortal", "Deathtouch"),
    "glyph.isekai_mahou.deathtouch.desc": (
        "Aumento: o dano do glifo anterior mata na hora qualquer mob não-chefe que ficaria abaixo de 25% da vida. Em chefes: +15% de dano.",
        "Augment: the previous glyph's damage instantly kills any non-boss mob that would drop below 25% health. On bosses: +15% damage."),
    "glyph.isekai_mahou.trample": ("Atropelar", "Trample"),
    "glyph.isekai_mahou.trample.desc": (
        "Aumento: o dano que sobra ao matar passa por cima para o inimigo mais próximo a até 5 blocos (até 3 vezes).",
        "Augment: the damage left over on a kill tramples over to the nearest enemy within 5 blocks (up to 3 times)."),
    "glyph.isekai_mahou.instant": ("Instantâneo", "Instant"),
    "glyph.isekai_mahou.instant.desc": (
        "Aumento: a magia sai na hora (tempo de conjuração 0) e nunca entra na Pilha — não pode ser contrariada. Custo +50%.",
        "Augment: the spell goes off at once (0 cast time) and never enters the Stack — it can't be countered. +50% cost."),
    "glyph.isekai_mahou.hexproof": ("Resguardo", "Hexproof"),
    "glyph.isekai_mahou.hexproof.desc": (
        "Você ou um aliado fica 8 s imune a status negativos e a contramágica (os atuais caem). Magias de jogadores inimigos que o "
        "atingem cobram 20 de mana do atacante — sem mana, são anuladas.",
        "You or an ally become immune to harmful effects and to counterspells for 8 s (current ones are cleansed). Enemy players' spells "
        "that hit it charge the attacker 20 mana — without mana, they fizzle."),
    "glyph.isekai_mahou.indestructible": ("Indestrutível", "Indestructible"),
    "glyph.isekai_mahou.indestructible.desc": (
        "Você ou um aliado não cai abaixo de 1 de vida por 4 s (égide dourada). Sobrevive até à Ira Divina.",
        "You or an ally can't drop below 1 health for 4 s (golden aegis). Even survives Wrath."),
    "glyph.isekai_mahou.exile": ("Exilar", "Exile"),
    "glyph.isekai_mahou.exile.desc": (
        "Uma fenda para o nada engole um mob não-chefe com até 50% de vida (+10% por Amplificar): some sem loot, mas conta como abate. "
        "Chefes e jogadores: Exposto II por 6 s.",
        "A rift into nothingness swallows a non-boss mob at or below 50% health (+10% per Amplify): it vanishes without loot, but counts "
        "as a kill. Bosses and players: Exposed II for 6 s."),
    "glyph.isekai_mahou.wrath": ("Ira Divina", "Wrath"),
    "glyph.isekai_mahou.wrath.desc": (
        "Um sigilo colossal se forma no céu e, 1,5 s depois, pilares de luz destroem TODA criatura não-jogador e não-domada na área "
        "(raio 14 + área) — inclusive as suas invocações. Chefes perdem 25% da vida máxima. O chão onde os pilares caem vira vidro.",
        "A colossal sigil forms in the sky and, 1.5 s later, pillars of light destroy EVERY non-player, non-tamed creature in the area "
        "(radius 14 + area) — your summons included. Bosses lose 25% of max health. The ground where the pillars fall turns to glass."),
    "glyph.isekai_mahou.time_walk": ("Passo no Tempo", "Time Walk"),
    "glyph.isekai_mahou.time_walk.desc": (
        "Em você: zera todas as suas recargas e devolve 30% do mana máximo. Recarga própria de 120 s.",
        "On yourself: resets all your cooldowns and restores 30% of your max mana. Own cooldown of 120 s."),
    "glyph.isekai_mahou.in_response": ("Em Resposta", "In Response"),
    "glyph.isekai_mahou.in_response.desc": (
        "Gatilho: o slot fica PREPARADO. Quando uma conjuração hostil entra na Pilha a até 20 blocos, a magia sai sozinha na hora, "
        "mirando quem conjura (paga mana, respeita recarga e silêncio; 1 resposta por conjuração). A tecla não conjura.",
        "Trigger: the slot is READIED. When a hostile cast enters the Stack within 20 blocks, the spell fires by itself at once, aimed at "
        "the caster (pays mana, respects cooldown and silence; 1 response per cast). The key doesn't cast it."),

    # ---------------- Efeitos / entidade ----------------
    "effect.isekai_mahou.hexproof": ("Resguardo", "Hexproof"),
    "effect.isekai_mahou.indestructible": ("Indestrutível", "Indestructible"),
    "entity.isekai_mahou.arcane_duel_fx": ("Visual de Duelo Arcano", "Arcane Duel Effect"),

    # ---------------- Lendas ----------------
    "legend.isekai_mahou.perfect_counter": ("Contramágica Perfeita", "Perfect Counter"),
    "legend.isekai_mahou.wrath_of_god": ("Ira de Deus", "Wrath of God"),
    "legend.isekai_mahou.time_walk": ("Passo no Tempo", "Time Walk"),

    # ---------------- Mensagens ----------------
    M + "mana_leak.paid": ("Vazamento de Mana! Você pagou %s de mana para manter a magia.",
                           "Mana Leak! You paid %s mana to keep your spell."),
    M + "mana_leak.drained": ("Sua mana vazou: -%s", "Your mana leaked: -%s"),
    M + "mana_leak.too_strong": ("Forte demais para o Vazamento (use Amplificar para Maiores)",
                                 "Too strong to leak (Amplify it for Major casts)"),
    M + "mana_leak.nothing": ("Nada na Pilha para vazar", "Nothing on the Stack to leak"),
    M + "redirect.done": ("%s projétil(eis) redirecionado(s)!", "%s projectile(s) redirected!"),
    M + "fork.nothing": ("Nenhuma magia recente de outro para copiar", "No recent spell from someone else to copy"),
    M + "fork.copied": ("Copiou \"%s\" de %s!", "Copied \"%s\" from %s!"),
    M + "spell_steal.nothing": ("O alvo não tem encantos para roubar", "The target has no enchantments to steal"),
    M + "spell_steal.done": ("Roubou %s encanto(s) de %s!", "Stole %s enchantment(s) from %s!"),
    M + "spell_steal.victim": ("%s roubou seus encantos!", "%s stole your enchantments!"),
    M + "time_walk.recovering": ("O Passo no Tempo ainda se recupera (%s s)", "Time Walk is still recovering (%s s)"),
    M + "time_walk.done": ("O tempo volta! %s recarga(s) zerada(s), +%s de mana", "Time rewinds! %s cooldown(s) reset, +%s mana"),
    M + "exile.boss": ("%s é grande demais para a fenda — Exposto!", "%s is too big for the rift — Exposed!"),
    M + "exile.resists": ("%s resiste ao exílio (precisa estar com até %s%% da vida)",
                          "%s resists exile (must be at or below %s%% health)"),
    M + "exile.done": ("%s foi exilado para o nada", "%s was exiled into nothingness"),
    M + "wrath.empty": ("A Ira Divina não encontrou criaturas para julgar", "Wrath found no creatures to judge"),
    M + "wrath.done": ("Ira Divina: %s criatura(s) julgada(s)", "Wrath: %s creature(s) judged"),
    M + "in_response.only": ("Este slot está preparado: só sai Em Resposta a uma magia inimiga",
                             "This slot is readied: it only fires In Response to an enemy spell"),
    M + "in_response.no_mana": ("Em Resposta: mana insuficiente", "In Response: not enough mana"),
    M + "in_response.fired": ("Em Resposta! %s contra %s", "In Response! %s against %s"),
    M + "hexproof.taxed": ("Resguardo: -%s de mana para atingir %s", "Hexproof: -%s mana to hit %s"),
    M + "hexproof.blocked": ("Resguardo: sem %s de mana, sua magia em %s foi anulada",
                             "Hexproof: without %s mana, your spell on %s fizzled"),
    "announce.isekai_mahou.arcane_duel.boss_countered": ("%s QUEBROU O GOLPE DE %s!", "%s BROKE %s's ATTACK!"),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, lang in enumerate(("pt_br", "en_us")):
        data = {k: v[i] for k, v in T.items()}
        with open(os.path.join(OUT, lang + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(f"{len(T)} chaves x 2 idiomas")


if __name__ == "__main__":
    main()
