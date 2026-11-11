package com.renanpontes.isekaimahou.core.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Uma reação elemental aconteceu (ELETROCUTAR, DERRETER, ESTILHAÇAR...). Postado em NeoForge.EVENT_BUS no servidor
 * DEPOIS de a reação ser aplicada. Informativo (não cancelável).
 *
 * <p>{@link #getContext()} é null quando a reação veio de um golpe corpo a corpo (Estilhaçar) ou de uma fonte sem magia.
 */
public class ElementReactionEvent extends Event {
    private final LivingEntity target;
    private final ElementReactions.Reaction reaction;
    @Nullable private final SpellContext context;
    @Nullable private final LivingEntity causer;
    @Nullable private final Element element;

    public ElementReactionEvent(LivingEntity target, ElementReactions.Reaction reaction, @Nullable SpellContext context,
                                @Nullable LivingEntity causer, @Nullable Element element) {
        this.target = target;
        this.reaction = reaction;
        this.context = context;
        this.causer = causer;
        this.element = element;
    }

    public LivingEntity getTarget() { return target; }
    public ElementReactions.Reaction getReaction() { return reaction; }
    /** Contexto da magia que causou a reação (null em golpes corpo a corpo). */
    @Nullable public SpellContext getContext() { return context; }
    /** Quem causou (conjurador ou atacante), se houver. */
    @Nullable public LivingEntity getCauser() { return causer; }
    /** Elemento do golpe que disparou a reação (null em golpes físicos). */
    @Nullable public Element getElement() { return element; }
}
