package com.renanpontes.isekaimahou.core.event;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Conjuração de magia (jogador OU mob). Postado em NeoForge.EVENT_BUS pelo {@code SpellCaster}.
 * Pre pode ser cancelado (ex.: Lei "Anulação" de um Domínio, área anti-magia).
 */
public abstract class SpellCastEvent extends Event {
    private final LivingEntity caster;
    private final SpellContext context;

    protected SpellCastEvent(LivingEntity caster, SpellContext context) {
        this.caster = caster;
        this.context = context;
    }

    public LivingEntity getCaster() { return caster; }
    public SpellContext getContext() { return context; }

    public static class Pre extends SpellCastEvent implements ICancellableEvent {
        public Pre(LivingEntity caster, SpellContext ctx) { super(caster, ctx); }
    }

    public static class Post extends SpellCastEvent {
        public Post(LivingEntity caster, SpellContext ctx) { super(caster, ctx); }
    }
}
