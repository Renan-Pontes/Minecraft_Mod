package com.renanpontes.isekaimahou.core.anim;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * v1.0 — eventos do bloqueio ({@link GuardService}), no {@code NeoForge.EVENT_BUS}, só no servidor. Para outros módulos
 * reagirem (micro-pausa no aparo, conquistas, técnicas de contra-golpe) sem mexer no núcleo.
 */
public abstract class GuardEvent extends Event {
    private final ServerPlayer player;
    @Nullable
    private final LivingEntity attacker;
    private final DamageSource source;
    private final float damage;

    protected GuardEvent(ServerPlayer player, @Nullable LivingEntity attacker, DamageSource source, float damage) {
        this.player = player;
        this.attacker = attacker;
        this.source = source;
        this.damage = damage;
    }

    /** Quem bloqueou/aparou. */
    public ServerPlayer getPlayer() { return player; }

    /** Quem atacou (null = sem dono, ex.: armadilha). */
    @Nullable
    public LivingEntity getAttacker() { return attacker; }

    public DamageSource getSource() { return source; }

    /** Dano do golpe ANTES do bloqueio. */
    public float getDamage() { return damage; }

    /** Aparo: o golpe foi anulado no tempo certo. */
    public static class Parry extends GuardEvent {
        public Parry(ServerPlayer player, @Nullable LivingEntity attacker, DamageSource source, float damage) {
            super(player, attacker, source, damage);
        }
    }

    /** Bloqueio comum: o golpe passou reduzido ({@link #getReduced()} = dano que sobrou). */
    public static class Block extends GuardEvent {
        private final float reduced;

        public Block(ServerPlayer player, @Nullable LivingEntity attacker, DamageSource source, float damage, float reduced) {
            super(player, attacker, source, damage);
            this.reduced = reduced;
        }

        public float getReduced() { return reduced; }
    }
}
