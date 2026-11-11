package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;

/**
 * Um golpe do Dragão Primordial: duração, recarga, fases em que existe, onde pode ser usado (no ar / no chão),
 * alcance, peso no sorteio, animação e o código tick a tick.
 */
public final class DragonAttack {
    public enum Where { AIR, GROUND, ANY }

    @FunctionalInterface
    public interface Tick {
        void tick(LivingEntity target, int t);
    }

    final String id;
    final int duration;
    final int cooldown;
    final Tick tick;
    @Nullable String anim;
    int phaseMask = 0b11111;
    Where where = Where.ANY;
    double minRange;
    double maxRange = 64;
    int weight = 4;
    boolean stationary;
    Predicate<LivingEntity> condition = t -> true;
    @Nullable Runnable onEnd;

    private DragonAttack(String id, int duration, int cooldown, Tick tick) {
        this.id = id;
        this.duration = duration;
        this.cooldown = cooldown;
        this.tick = tick;
    }

    public static DragonAttack of(String id, int duration, int cooldown, Tick tick) {
        return new DragonAttack(id, duration, cooldown, tick);
    }

    public DragonAttack anim(String a) { this.anim = a; return this; }

    /** Fases (1..5) em que o golpe existe. */
    public DragonAttack phases(int... ph) {
        int m = 0;
        for (int p : ph) m |= 1 << (p - 1);
        this.phaseMask = m;
        return this;
    }

    public DragonAttack where(Where w) { this.where = w; return this; }
    public DragonAttack range(double min, double max) { this.minRange = min; this.maxRange = max; return this; }
    public DragonAttack weight(int w) { this.weight = w; return this; }
    /** O dragão não se move sozinho durante o golpe (o próprio golpe mexe nele, se precisar). */
    public DragonAttack stationary() { this.stationary = true; return this; }
    public DragonAttack when(Predicate<LivingEntity> c) { this.condition = c; return this; }
    public DragonAttack onEnd(Runnable r) { this.onEnd = r; return this; }

    boolean inPhase(int phase) {
        return (phaseMask & (1 << (phase - 1))) != 0;
    }

    public String id() {
        return id;
    }
}
