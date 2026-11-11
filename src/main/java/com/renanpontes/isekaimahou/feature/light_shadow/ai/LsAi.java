package com.renanpontes.isekaimahou.feature.light_shadow.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.light_shadow.LsRegistries;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * IA temporária colocada em mobs por Medo e Possessão. Os objetivos são marcados por classe, então dá para
 * checar se já existem e removê-los quando o efeito acaba (LsEvents) — sem guardar referências.
 */
public final class LsAi {
    /** Remoções de objetivos adiadas para o fim do tick do servidor (fora da iteração do GoalSelector). */
    private static final List<Runnable> PENDING = new ArrayList<>();

    /** Chamado no ServerTickEvent.Post: aplica as remoções pendentes. */
    public static void flushPending() {
        if (PENDING.isEmpty()) return;
        List<Runnable> run = new ArrayList<>(PENDING);
        PENDING.clear();
        run.forEach(Runnable::run);
    }

    public static void clearPending() {
        PENDING.clear();
    }

    /**
     * Para (se estiver rodando) e remove os objetivos de uma classe. GoalSelector.removeAllGoals sozinho NÃO para
     * o objetivo: ele continuaria "rodando" e travaria as flags de movimento/alvo do mob para sempre.
     */
    private static void removeGoals(GoalSelector sel, Class<?> cls) {
        for (WrappedGoal w : sel.getAvailableGoals()) {
            if (w.isRunning() && cls.isInstance(w.getGoal())) w.stop();
        }
        sel.removeAllGoals(cls::isInstance);
    }

    public static void clearTarget(Mob mob) {
        if (mob.getTarget() != null) mob.setTarget(null);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    public static void setTarget(Mob mob, LivingEntity target) {
        mob.setTarget(target);
        if (mob.getTarget() == target) mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    }

    private static boolean has(GoalSelector sel, Class<?> cls) {
        return sel.getAvailableGoals().stream().anyMatch(w -> cls.isInstance(w.getGoal()));
    }

    // ------------------------------------------------------------------ medo
    public static void setFearSource(LivingEntity e, Vec3 src) {
        CompoundTag d = LsUtil.data(e);
        d.putDouble("fear_x", src.x);
        d.putDouble("fear_y", src.y);
        d.putDouble("fear_z", src.z);
    }

    public static void clearFear(Mob mob) {
        // Adiado: o efeito pode acabar no meio do tick de IA de algum mob; se o medo voltar antes, o objetivo fica
        PENDING.add(() -> {
            if (!mob.hasEffect(LsRegistries.SHADOW_FEAR)) removeGoals(mob.goalSelector, FleeGoal.class);
        });
        if (LsUtil.hasData(mob)) {
            CompoundTag d = LsUtil.data(mob);
            d.remove("fear_x");
            d.remove("fear_y");
            d.remove("fear_z");
        }
    }

    /** Ponto do qual o mob foge: onde a magia foi conjurada, ou o jogador mais perto. */
    public static Vec3 fearSource(LivingEntity e) {
        if (LsUtil.hasData(e)) {
            CompoundTag d = LsUtil.data(e);
            if (d.contains("fear_x")) return new Vec3(d.getDouble("fear_x"), d.getDouble("fear_y"), d.getDouble("fear_z"));
        }
        Player p = e.level().getNearestPlayer(e, 24);
        return p != null ? p.position() : e.position().subtract(e.getLookAngle());
    }

    public static void ensureFlee(PathfinderMob mob) {
        if (!has(mob.goalSelector, FleeGoal.class)) mob.goalSelector.addGoal(-1, new FleeGoal(mob));
    }

    /** Fuga com prioridade máxima enquanto o mob estiver Amedrontado. */
    public static class FleeGoal extends Goal {
        private final PathfinderMob mob;

        public FleeGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.hasEffect(LsRegistries.SHADOW_FEAR);
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            flee();
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone()) flee();
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        private void flee() {
            Vec3 pos = DefaultRandomPos.getPosAway(mob, 16, 7, fearSource(mob));
            if (pos != null) mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.45);
        }
    }

    // ------------------------------------------------------------------ possessão
    public static void ensurePossessed(Mob mob) {
        if (!has(mob.targetSelector, PossessedTargetGoal.class)) mob.targetSelector.addGoal(-1, new PossessedTargetGoal(mob));
        if (mob instanceof PathfinderMob pm && !has(mob.goalSelector, FollowPossessorGoal.class)) {
            mob.goalSelector.addGoal(-1, new FollowPossessorGoal(pm));
        }
    }

    public static void removePossessionGoals(Mob mob) {
        PENDING.add(() -> {
            if (LsUtil.possessor(mob) != null) return; // possuído de novo antes do fim do tick
            removeGoals(mob.targetSelector, PossessedTargetGoal.class);
            removeGoals(mob.goalSelector, FollowPossessorGoal.class);
        });
    }

    /** Escolhe como alvo os inimigos do possessor: quem o atacou, quem ele atacou, ou o monstro mais perto. */
    public static class PossessedTargetGoal extends TargetGoal {
        private LivingEntity candidate;

        public PossessedTargetGoal(Mob mob) {
            super(mob, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!(mob.level() instanceof ServerLevel sl)) return false;
            UUID ownerId = LsUtil.possessor(mob);
            if (ownerId == null) return false;
            candidate = pick(sl, ownerId, LsUtil.owner(sl, ownerId));
            return candidate != null;
        }

        @Override
        public void start() {
            setTarget(mob, candidate);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            UUID ownerId = LsUtil.possessor(mob);
            if (ownerId == null || t == null || !t.isAlive() || !(mob.level() instanceof ServerLevel sl)) return false;
            if (LsUtil.isAlly(sl, ownerId, t)) return false;
            return super.canContinueToUse();
        }

        private LivingEntity pick(ServerLevel sl, UUID ownerId, LivingEntity owner) {
            if (owner != null) {
                LivingEntity a = owner.getLastHurtByMob();
                if (valid(sl, ownerId, a) && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) return a;
                LivingEntity b = owner.getLastHurtMob();
                if (valid(sl, ownerId, b) && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) return b;
                if (owner instanceof Mob m && valid(sl, ownerId, m.getTarget())) return m.getTarget();
            }
            for (LivingEntity e : LsUtil.enemiesOf(sl, ownerId, mob.position(), 12, mob)) {
                if (e instanceof Enemy && valid(sl, ownerId, e)) return e;
            }
            return null;
        }

        private boolean valid(ServerLevel sl, UUID ownerId, LivingEntity e) {
            return e != null && e != mob && e.isAlive() && e.distanceToSqr(mob) < 24 * 24
                    && !LsUtil.isAlly(sl, ownerId, e) && mob.canAttack(e);
        }
    }

    /** Sem alvo, o possuído acompanha quem o possuiu. */
    public static class FollowPossessorGoal extends Goal {
        private final PathfinderMob mob;
        private LivingEntity owner;
        private int recalc;

        public FollowPossessorGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || !(mob.level() instanceof ServerLevel sl)) return false;
            owner = LsUtil.owner(sl, LsUtil.possessor(mob));
            return owner != null && owner.isAlive() && mob.distanceToSqr(owner) > 36;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && mob.getTarget() == null && mob.distanceToSqr(owner) > 6.25
                    && LsUtil.possessor(mob) != null;
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10F, mob.getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                mob.getNavigation().moveTo(owner, 1.2);
            }
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }
    }

    private LsAi() {}
}
