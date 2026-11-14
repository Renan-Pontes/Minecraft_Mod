package com.renanpontes.isekaimahou.feature.stands;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Uma PEÇA de comportamento de um estágio de evolução (dados puros; quem executa é {@link EvolutionEffects}).
 * Os estágios de um glifo são listas de peças — é assim que "Chidori → Raikiri → Kirin" vira dado em vez de código.
 * <ul>
 *   <li>{@link Kind#DMG}: multiplica o dano do glifo ({@code a}); em jogadores conta metade do extra.</li>
 *   <li>{@link Kind#ARCS}: o golpe salta para {@code c} inimigos perto (6 blocos) com {@code a} do dano.</li>
 *   <li>{@link Kind#SKY}: raio do céu no alvo ({@code a} do dano em raio {@code b}), 1 vez a cada {@code c} ticks.</li>
 *   <li>{@link Kind#BLACK_FLAME}: anel de chama negra no chão por {@code c} ticks, raio {@code b}, {@code a} do dano a cada 0,5 s.</li>
 *   <li>{@link Kind#SHOCK}: onda que empurra (força {@code b}) e fere os inimigos em volta (raio {@code c}/10) com {@code a}.</li>
 *   <li>{@link Kind#VOLLEY}: {@code c} raios extras da sua mão em outros inimigos (até 12 blocos) com {@code a}.</li>
 *   <li>{@link Kind#PIERCE}: {@code a} do dano entra de novo ignorando armadura (destrói por dentro).</li>
 *   <li>{@link Kind#FREEZE}: o alvo trava por {@code c} ticks (chefes: metade e mais fraco).</li>
 *   <li>{@link Kind#BUFF}: ao conjurar, você ganha {@code effect} nível {@code b} por {@code c} ticks (intervalo {@code a} s).</li>
 *   <li>{@link Kind#BERSERK}: ao conjurar, Força I e Velocidade I por 6 s, mas a fúria cobra 2 de vida (nunca mata).</li>
 * </ul>
 */
public record Perk(Kind kind, float a, float b, int c, @Nullable Holder<MobEffect> effect) {
    public enum Kind { DMG, ARCS, SKY, BLACK_FLAME, SHOCK, VOLLEY, PIERCE, FREEZE, BUFF, BERSERK }

    public static Perk dmg(float mult) { return new Perk(Kind.DMG, mult, 0, 0, null); }
    public static Perk arcs(int n, float frac) { return new Perk(Kind.ARCS, frac, 0, n, null); }
    public static Perk sky(float frac, float radius, int cooldown) { return new Perk(Kind.SKY, frac, radius, cooldown, null); }
    public static Perk blackFlame(float frac, float radius, int ticks) { return new Perk(Kind.BLACK_FLAME, frac, radius, ticks, null); }
    public static Perk shock(float frac, float knock, float radius) { return new Perk(Kind.SHOCK, frac, knock, Math.round(radius * 10), null); }
    public static Perk volley(int n, float frac) { return new Perk(Kind.VOLLEY, frac, 0, n, null); }
    public static Perk pierce(float frac) { return new Perk(Kind.PIERCE, frac, 0, 0, null); }
    public static Perk freeze(int ticks) { return new Perk(Kind.FREEZE, 0, 0, ticks, null); }
    public static Perk buff(Holder<MobEffect> effect, int amp, int ticks, int cooldownSec) {
        return new Perk(Kind.BUFF, cooldownSec, amp, ticks, effect);
    }
    public static Perk berserk() { return new Perk(Kind.BERSERK, 0, 0, 0, MobEffects.DAMAGE_BOOST); }

    /** Peça que age ao conjurar (as outras agem no acerto). */
    public boolean onCast() { return kind == Kind.BUFF || kind == Kind.BERSERK; }
}
