package com.renanpontes.isekaimahou.core.event;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Eventos de progresso (postados em NeoForge.EVENT_BUS pelo {@code MagicApi}).
 * Ex.: o módulo de Caminhos ouve {@link ElementXp} e {@link Counter} para liberar nós.
 */
public abstract class MagicProgressEvent extends Event {
    private final ServerPlayer player;

    protected MagicProgressEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }

    public static class LevelUp extends MagicProgressEvent {
        public final int oldLevel, newLevel;
        public LevelUp(ServerPlayer p, int oldLevel, int newLevel) { super(p); this.oldLevel = oldLevel; this.newLevel = newLevel; }
    }

    public static class CircleChanged extends MagicProgressEvent {
        public final int oldCircle, newCircle;
        public CircleChanged(ServerPlayer p, int oldCircle, int newCircle) { super(p); this.oldCircle = oldCircle; this.newCircle = newCircle; }
    }

    public static class ElementAwakened extends MagicProgressEvent {
        public final Element element;
        public ElementAwakened(ServerPlayer p, Element e) { super(p); this.element = e; }
    }

    public static class ElementXp extends MagicProgressEvent {
        public final Element element;
        public final int amount, oldLevel, newLevel;
        public ElementXp(ServerPlayer p, Element e, int amount, int oldLevel, int newLevel) {
            super(p); this.element = e; this.amount = amount; this.oldLevel = oldLevel; this.newLevel = newLevel;
        }
    }

    public static class GlyphLearned extends MagicProgressEvent {
        public final ResourceLocation glyph;
        public GlyphLearned(ServerPlayer p, ResourceLocation g) { super(p); this.glyph = g; }
    }

    public static class Counter extends MagicProgressEvent {
        public final String key;
        public final int value;
        public Counter(ServerPlayer p, String key, int value) { super(p); this.key = key; this.value = value; }
    }

    /** Disparado quando o jogador escolhe a classe na tela inicial (depois do kit ser entregue). */
    public static class PathChosen extends MagicProgressEvent {
        public PathChosen(ServerPlayer p) { super(p); }
    }
}
