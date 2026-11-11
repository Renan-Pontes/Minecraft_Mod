package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;

/**
 * Um golpe/magia de chefe: dura {@code duration} ticks e recebe o tick atual (0..duration-1) em {@link Tick}.
 * O {@link BbBoss} escolhe entre os disponíveis (fase, distância, recarga, condição) com sorteio por peso.
 */
public final class BossAttack {
    @FunctionalInterface
    public interface Tick {
        void tick(LivingEntity target, int t);
    }

    final String id;
    final int duration;
    final int cooldown;
    final Tick tick;
    String anim;
    int minPhase = 1;
    double minRange = 0;
    double maxRange = 64;
    int weight = 3;
    boolean stationary;
    boolean faceTarget = true;
    Predicate<LivingEntity> condition = t -> true;
    Runnable onEnd;

    private BossAttack(String id, int duration, int cooldown, Tick tick) {
        this.id = id;
        this.duration = duration;
        this.cooldown = cooldown;
        this.tick = tick;
    }

    public static BossAttack of(String id, int duration, int cooldown, Tick tick) {
        return new BossAttack(id, duration, cooldown, tick);
    }

    /** Animação disparada (controlador "action") ao começar. */
    public BossAttack anim(String a) { this.anim = a; return this; }

    public BossAttack phase(int min) { this.minPhase = min; return this; }

    public BossAttack range(double min, double max) { this.minRange = min; this.maxRange = max; return this; }

    public BossAttack weight(int w) { this.weight = w; return this; }

    /** O chefe para de andar enquanto executa. */
    public BossAttack stationary() { this.stationary = true; return this; }

    /** O golpe controla a própria rotação (não mira no alvo automaticamente). */
    public BossAttack freeFacing() { this.faceTarget = false; return this; }

    public BossAttack when(Predicate<LivingEntity> c) { this.condition = c; return this; }

    /** Chamado ao terminar OU ao ser cancelado (troca de fase, morte): limpe estados sincronizados aqui. */
    public BossAttack onEnd(Runnable r) { this.onEnd = r; return this; }

    public String id() { return id; }
}
