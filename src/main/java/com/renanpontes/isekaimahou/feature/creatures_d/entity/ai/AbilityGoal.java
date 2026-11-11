package com.renanpontes.isekaimahou.feature.creatures_d.entity.ai;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

import com.renanpontes.isekaimahou.feature.creatures_d.entity.ElementalCreature;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Habilidade com preparação e recarga: quando o alvo está na distância certa e visível, a criatura
 * dispara a animação, "carrega" por {@code windupTicks} (com partículas de aviso, para o jogador poder
 * reagir) e então executa a ação. {@code holdStill} = para de andar enquanto prepara.
 */
public class AbilityGoal extends Goal {
    public interface Windup {
        void tick(LivingEntity target, int ticksLeft);
    }

    private final ElementalCreature mob;
    private final String animation;
    private final double minDist;
    private final double maxDist;
    private final int windupTicks;
    private final int cooldownMin;
    private final int cooldownMax;
    private final boolean holdStill;
    private final Consumer<LivingEntity> action;
    private Windup windup = (t, left) -> {};
    private Consumer<LivingEntity> onStart = t -> {};
    private Predicate<LivingEntity> condition = t -> true;
    private DoubleSupplier cooldownScale = () -> 1.0;
    private boolean needsSight = true;
    private long readyAt;
    private int timer = -1;

    public AbilityGoal(ElementalCreature mob, String animation, double minDist, double maxDist, int windupTicks,
                       int cooldownMin, int cooldownMax, boolean holdStill, Consumer<LivingEntity> action) {
        this.mob = mob;
        this.animation = animation;
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.windupTicks = windupTicks;
        this.cooldownMin = cooldownMin;
        this.cooldownMax = Math.max(cooldownMin, cooldownMax);
        this.holdStill = holdStill;
        this.action = action;
        // Habilidades que não param o movimento rodam junto com o ataque corpo a corpo/voo
        setFlags(holdStill ? EnumSet.of(Flag.MOVE, Flag.LOOK) : EnumSet.noneOf(Flag.class));
    }

    public AbilityGoal windup(Windup w) {
        this.windup = w;
        return this;
    }

    public AbilityGoal onStart(Consumer<LivingEntity> c) {
        this.onStart = c;
        return this;
    }

    public AbilityGoal when(Predicate<LivingEntity> c) {
        this.condition = c;
        return this;
    }

    public AbilityGoal cooldownScale(DoubleSupplier s) {
        this.cooldownScale = s;
        return this;
    }

    public AbilityGoal ignoreSight() {
        this.needsSight = false;
        return this;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || mob.level().getGameTime() < readyAt) return false;
        double d = mob.distanceTo(t);
        return d >= minDist && d <= maxDist && (!needsSight || mob.getSensing().hasLineOfSight(t)) && condition.test(t);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return timer >= 0 && t != null && t.isAlive();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        timer = windupTicks;
        if (animation != null) mob.playAction(animation);
        if (holdStill) mob.getNavigation().stop();
        LivingEntity t = mob.getTarget();
        if (t != null) onStart.accept(t);
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) {
            timer = -1;
            return;
        }
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (timer > 0) {
            windup.tick(t, timer);
            timer--;
            return;
        }
        action.accept(t);
        timer = -1;
    }

    @Override
    public void stop() {
        timer = -1;
        int cd = cooldownMin + mob.getRandom().nextInt(cooldownMax - cooldownMin + 1);
        readyAt = mob.level().getGameTime() + Math.round(cd * cooldownScale.getAsDouble());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
