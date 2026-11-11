package com.renanpontes.isekaimahou.feature.bosses_d.entity;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Um golpe do Avatar: roda tick a tick por {@code duration} ticks. Recarga, alcance (distância até o alvo),
 * fase mínima e peso decidem quando ele é escolhido. A animação {@code anim} é disparada no início.
 */
public final class AvatarMove {
    @FunctionalInterface
    public interface Tick {
        void run(ServerLevel level, LivingEntity target, int t);
    }

    public final String id;
    public final int duration;
    public final int cooldown;
    public final Tick tick;
    String anim;
    double minRange;
    double maxRange = 48;
    int minPhase = 1;
    int weight = 10;
    boolean stationary = true;
    boolean faceTarget = true;
    boolean ultimate;
    Predicate<LivingEntity> condition = t -> true;
    @Nullable Runnable onEnd;

    private AvatarMove(String id, int duration, int cooldown, Tick tick) {
        this.id = id;
        this.duration = duration;
        this.cooldown = cooldown;
        this.tick = tick;
        this.anim = id;
    }

    public static AvatarMove of(String id, int duration, int cooldown, Tick tick) {
        return new AvatarMove(id, duration, cooldown, tick);
    }

    public AvatarMove anim(String a) { this.anim = a; return this; }

    public AvatarMove range(double min, double max) { this.minRange = min; this.maxRange = max; return this; }

    public AvatarMove phase(int p) { this.minPhase = p; return this; }

    public AvatarMove weight(int w) { this.weight = w; return this; }

    /** O chefe continua se movendo durante o golpe (investidas controlam a própria velocidade). */
    public AvatarMove moving() { this.stationary = false; return this; }

    /** O golpe não vira o chefe para o alvo a cada tick (cones/feixes travados na direção inicial). */
    public AvatarMove locked() { this.faceTarget = false; return this; }

    /** Golpe máximo da fase 3 (forçado ao entrar na fase; recarga longa). */
    public AvatarMove ultimate() { this.ultimate = true; this.minPhase = 3; return this; }

    public AvatarMove when(Predicate<LivingEntity> c) { this.condition = c; return this; }

    public AvatarMove onEnd(Runnable r) { this.onEnd = r; return this; }

    public String anim() { return anim; }
}
