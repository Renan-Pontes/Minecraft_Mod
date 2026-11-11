package com.renanpontes.isekaimahou.feature.creatures_a.entity.slime;

import java.util.EnumSet;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;

/** IA dos slimes do módulo (versões públicas das metas do slime vanilla + revide do slime neutro). */
public final class SlimeGoals {

    private static SlimeHopControl hop(MahouSlime s) {
        return s.getMoveControl() instanceof SlimeHopControl c ? c : null;
    }

    /** Persegue o alvo pulando na direção dele. */
    public static class Attack extends Goal {
        private final MahouSlime slime;
        private int growTiredTimer;

        public Attack(MahouSlime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = slime.getTarget();
            return t != null && slime.canAttack(t) && hop(slime) != null;
        }

        @Override
        public void start() {
            growTiredTimer = reducedTickDelay(300);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = slime.getTarget();
            return t != null && slime.canAttack(t) && --growTiredTimer > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = slime.getTarget();
            if (t != null) slime.lookAt(t, 10.0F, 10.0F);
            SlimeHopControl c = hop(slime);
            if (c != null) c.setDirection(slime.getYRot(), slime.hopsAggressively());
        }
    }

    /** Na água/lava: pula para boiar. */
    public static class Swim extends Goal {
        private final MahouSlime slime;

        public Swim(MahouSlime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (slime.isInWater() || slime.isInLava()) && hop(slime) != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (slime.getRandom().nextFloat() < 0.8F) slime.getJumpControl().jump();
            SlimeHopControl c = hop(slime);
            if (c != null) c.setWantedMovement(1.2);
        }
    }

    /** Continua pulando sempre (o slime nunca fica parado de vez). */
    public static class KeepJumping extends Goal {
        private final MahouSlime slime;

        public KeepJumping(MahouSlime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !slime.isPassenger();
        }

        @Override
        public void tick() {
            SlimeHopControl c = hop(slime);
            if (c != null) c.setWantedMovement(1.0);
        }
    }

    /** Sem alvo: escolhe direções aleatórias de tempos em tempos. */
    public static class RandomDirection extends Goal {
        private final MahouSlime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public RandomDirection(MahouSlime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return slime.getTarget() == null
                    && (slime.onGround() || slime.isInWater() || slime.isInLava() || slime.hasEffect(MobEffects.LEVITATION))
                    && hop(slime) != null;
        }

        @Override
        public void tick() {
            if (--nextRandomizeTime <= 0) {
                nextRandomizeTime = adjustedTickDelay(40 + slime.getRandom().nextInt(60));
                chosenDegrees = slime.getRandom().nextInt(360);
            }
            SlimeHopControl c = hop(slime);
            if (c != null) c.setDirection(chosenDegrees, false);
        }
    }

    /** Revide: quem machucou o slime vira o alvo (é o único jeito do slime azul, neutro, atacar). */
    public static class Retaliate extends TargetGoal {
        private final MahouSlime slime;
        private int timestamp;

        public Retaliate(MahouSlime slime) {
            super(slime, true);
            this.slime = slime;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = slime.getLastHurtByMob();
            if (attacker == null || slime.getLastHurtByMobTimestamp() == timestamp || attacker instanceof MahouSlime) return false;
            return canAttack(attacker, TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting());
        }

        @Override
        public void start() {
            slime.setTarget(slime.getLastHurtByMob());
            targetMob = slime.getTarget();
            timestamp = slime.getLastHurtByMobTimestamp();
            unseenMemoryTicks = 300;
            super.start();
        }
    }

    /** Caça jogadores próximos (só variedades hostis). */
    public static class HuntPlayers extends NearestAttackableTargetGoal<Player> {
        private final MahouSlime slime;

        public HuntPlayers(MahouSlime slime) {
            super(slime, Player.class, 10, true, false, p -> Math.abs(p.getY() - slime.getY()) <= 4.0 + slime.getSize());
            this.slime = slime;
        }

        @Override
        public boolean canUse() {
            return !slime.isNeutral() && super.canUse();
        }
    }

    /** Briga com golens de ferro (só variedades hostis). */
    public static class HuntGolems extends NearestAttackableTargetGoal<IronGolem> {
        private final MahouSlime slime;

        public HuntGolems(MahouSlime slime) {
            super(slime, IronGolem.class, true);
            this.slime = slime;
        }

        @Override
        public boolean canUse() {
            return !slime.isNeutral() && super.canUse();
        }
    }

    private SlimeGoals() {}
}
