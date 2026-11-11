package com.renanpontes.isekaimahou.core.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.SpellStack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Uma conjuração pendente foi CONTRARIADA (já saiu da Pilha; efeitos, letreiro e contadores
 * {@code counter.success}/{@code counter.boss} já foram aplicados pelo núcleo). Postado em NeoForge.EVENT_BUS.
 * <p>
 * Para IMPEDIR uma contramágica (ex.: "Resguardo"), ouça {@link Attempt} — é outro evento (não herda deste).
 */
public class SpellCounteredEvent extends Event {
    private final ServerLevel level;
    private final SpellStack.Pending pending;
    @Nullable private final LivingEntity counteredBy;
    private final float power;

    public SpellCounteredEvent(ServerLevel level, SpellStack.Pending pending, @Nullable LivingEntity counteredBy, float power) {
        this.level = level;
        this.pending = pending;
        this.counteredBy = counteredBy;
        this.power = power;
    }

    public ServerLevel getLevel() { return level; }
    public SpellStack.Pending getPending() { return pending; }
    /** Quem contrariou (null = sem autor, ex.: comando). */
    @Nullable public LivingEntity getCounteredBy() { return counteredBy; }
    public float getPower() { return power; }
    /** O conjurador contrariado era chefe (ficou Atordoado/Exposto conforme o tier). */
    public boolean wasBoss() { return pending.bossCaster(); }

    /**
     * Antes de contrariar (todas as regras de poder/recarga ainda não foram checadas). Cancelar = a pendente fica
     * protegida: {@code counter} devolve {@code NOT_COUNTERABLE}. Não herda de {@link SpellCounteredEvent}.
     */
    public static class Attempt extends Event implements ICancellableEvent {
        private final ServerLevel level;
        private final SpellStack.Pending pending;
        @Nullable private final LivingEntity counteredBy;
        private final float power;

        public Attempt(ServerLevel level, SpellStack.Pending pending, @Nullable LivingEntity counteredBy, float power) {
            this.level = level;
            this.pending = pending;
            this.counteredBy = counteredBy;
            this.power = power;
        }

        public ServerLevel getLevel() { return level; }
        public SpellStack.Pending getPending() { return pending; }
        @Nullable public LivingEntity getCounteredBy() { return counteredBy; }
        public float getPower() { return power; }
    }
}
