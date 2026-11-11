package com.renanpontes.isekaimahou.core.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Dano de MAGIA de glifo ({@link SpellContext#damage}) em uma criatura. Postado em NeoForge.EVENT_BUS (servidor).
 * Ordem dentro do {@code damage}: dano base (poder, crítico) → reações/status ({@code ElementReactions.onSpellHit}) →
 * {@link Pre} (cancelável, muda o valor) → {@code hurt} → {@link Post} (resultado).
 * <p>
 * {@link #stats()} são os aumentos do glifo que causou o dano (ex.: um aumento de módulo em
 * {@code stats().custom(id)}); {@link #ctx()} traz conjurador, magia e elemento.
 */
public abstract class SpellDamageEvent extends Event {
    private final SpellContext ctx;
    private final LivingEntity target;
    private final Element element;
    private final SpellStats stats;
    @Nullable private final Entity direct;
    private final DamageSource source;

    protected SpellDamageEvent(SpellContext ctx, LivingEntity target, Element element, SpellStats stats,
                               @Nullable Entity direct, DamageSource source) {
        this.ctx = ctx;
        this.target = target;
        this.element = element;
        this.stats = stats;
        this.direct = direct;
        this.source = source;
    }

    public SpellContext ctx() { return ctx; }
    public LivingEntity target() { return target; }
    /** Elemento do dano (numa magia multi-elemento, o do glifo). */
    public Element element() { return element; }
    public SpellStats stats() { return stats; }
    /** Entidade que acertou (projétil...), ou null. */
    @Nullable public Entity direct() { return direct; }
    public DamageSource source() { return source; }

    /** Antes do {@code hurt}. Cancelar = nenhum dano (o {@code damage} devolve 0). */
    public static class Pre extends SpellDamageEvent implements ICancellableEvent {
        private final float original;
        private float amount;

        public Pre(SpellContext ctx, LivingEntity target, Element element, SpellStats stats, @Nullable Entity direct,
                   DamageSource source, float amount) {
            super(ctx, target, element, stats, direct, source);
            this.original = amount;
            this.amount = amount;
        }

        /** Dano que vai para o {@code hurt} (já com reações elementais). */
        public float getAmount() { return amount; }

        /** Muda o dano (≤ 0 = nada acontece, como cancelar). */
        public void setAmount(float amount) { this.amount = Float.isNaN(amount) ? 0F : amount; }

        /** Valor que chegou ao evento (antes de outros ouvintes mexerem). */
        public float getOriginalAmount() { return original; }
    }

    /** Depois do {@code hurt}. */
    public static class Post extends SpellDamageEvent {
        private final float amount;
        private final boolean landed;
        private final float dealt;
        private final float overkill;
        private final boolean killed;

        public Post(SpellContext ctx, LivingEntity target, Element element, SpellStats stats, @Nullable Entity direct,
                    DamageSource source, float amount, boolean landed, float dealt, float overkill, boolean killed) {
            super(ctx, target, element, stats, direct, source);
            this.amount = amount;
            this.landed = landed;
            this.dealt = dealt;
            this.overkill = overkill;
            this.killed = killed;
        }

        /** Dano pedido ao {@code hurt} (antes de armadura/resistências). */
        public float getAmount() { return amount; }
        /** O {@code hurt} aceitou o dano (false = imune, invulnerável, bloqueado...). */
        public boolean landed() { return landed; }
        /** Vida que o alvo de fato perdeu. */
        public float dealt() { return dealt; }
        /** Dano (já com armadura/resistências) além da vida que o alvo tinha; 0 se não morreu. */
        public float overkill() { return overkill; }
        /** O alvo estava vivo e morreu por este dano (totem salva = false). */
        public boolean killed() { return killed; }
    }
}
