package com.renanpontes.isekaimahou.feature.creatures_e.entity;

import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;

/**
 * Um golpe/habilidade de criatura: animacao com preparacao ({@link #windup}) e efeito no meio dela.
 * O {@link CreatureCombatGoal} escolhe o primeiro golpe disponivel (fora de recarga, no alcance e com a condicao).
 * Os alcances sao medidos de borda a borda das caixas de colisao.
 */
public final class CreatureMove {
    /** Acao no momento do golpe. */
    @FunctionalInterface
    public interface Action {
        void run(LivingEntity target);
    }

    /** Executada a cada tick do golpe (canalizacoes, rajadas). */
    @FunctionalInterface
    public interface Ticker {
        void tick(LivingEntity target, int tick);
    }

    final String name;
    int windup = 8;
    int length = 16;
    int cooldown = 40;
    double minRange = 0;
    double maxRange = 3;
    boolean needsSight = true;
    boolean roots = true;
    Predicate<LivingEntity> condition = t -> true;
    Action start;
    Action perform;
    Ticker during;

    private CreatureMove(String name) {
        this.name = name;
    }

    /** {@code name} = nome da animacao ("animation.&lt;criatura&gt;.&lt;name&gt;") e chave da recarga. */
    public static CreatureMove of(String name) {
        return new CreatureMove(name);
    }

    public CreatureMove timing(int windup, int length) {
        this.windup = windup;
        this.length = Math.max(length, windup);
        return this;
    }

    public CreatureMove cooldown(int ticks) {
        this.cooldown = ticks;
        return this;
    }

    public CreatureMove range(double min, double max) {
        this.minRange = min;
        this.maxRange = max;
        return this;
    }

    /** Nao precisa ver o alvo (ex.: teleporte, golpes de area). */
    public CreatureMove blind() {
        this.needsSight = false;
        return this;
    }

    /** A criatura continua se movendo durante o golpe. */
    public CreatureMove mobile() {
        this.roots = false;
        return this;
    }

    public CreatureMove when(Predicate<LivingEntity> c) {
        this.condition = c;
        return this;
    }

    public CreatureMove onStart(Action a) {
        this.start = a;
        return this;
    }

    public CreatureMove perform(Action a) {
        this.perform = a;
        return this;
    }

    public CreatureMove during(Ticker t) {
        this.during = t;
        return this;
    }
}
