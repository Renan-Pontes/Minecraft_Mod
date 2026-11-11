package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/** IA compartilhada das invocacoes (Golem de Pedra, Silfide): dono, aliados, seguir e defender. */
public final class SummonAi {

    /** Dono de uma invocacao: qualquer entidade viva (jogador ou mob conjurador). */
    public static LivingEntity owner(Entity summon, UUID ownerId) {
        if (ownerId == null) return null;
        Player p = summon.level().getPlayerByUUID(ownerId);
        if (p != null) return p;
        if (summon.level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) return le;
        return null;
    }

    /** {@code e} e do "time" da invocacao? (ela mesma, o dono, outras invocacoes/pets do dono, aliados do dono). */
    public static boolean isFriendly(Mob summon, Entity e) {
        if (e == null) return false;
        if (e == summon) return true;
        UUID ownerId = summon instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        if (ownerId == null) return false;
        if (e.getUUID().equals(ownerId)) return true;
        if (e instanceof OwnableEntity o && ownerId.equals(o.getOwnerUUID())) return true;
        LivingEntity owner = owner(summon, ownerId);
        if (owner != null && owner.isAlliedTo(e)) return true;
        // jogadores sao aliados entre si quando o PvP esta desligado
        return owner instanceof Player && e instanceof Player && summon.getServer() != null && !summon.getServer().isPvpAllowed();
    }

    /** Segue o dono; teleporta para perto dele se ficar muito longe. */
    public static class FollowOwnerGoal<T extends PathfinderMob & OwnableEntity> extends Goal {
        private final T mob;
        private final double speed;
        private final float startDist;
        private final float stopDist;
        private final boolean flying;
        private LivingEntity owner;
        private int recalc;

        public FollowOwnerGoal(T mob, double speed, float startDist, float stopDist, boolean flying) {
            this.mob = mob;
            this.speed = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            this.flying = flying;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = mob.getOwner();
            if (o == null || o.isSpectator() || o.level() != mob.level() || mob.getTarget() != null) return false;
            if (mob.distanceToSqr(o) < startDist * startDist) return false;
            owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && mob.getTarget() == null && !mob.getNavigation().isDone()
                    && mob.distanceToSqr(owner) > stopDist * stopDist;
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (mob.distanceToSqr(owner) > 24 * 24) {
                teleportNear();
            } else if (flying) {
                mob.getNavigation().moveTo(owner.getX(), owner.getY() + owner.getBbHeight() + 0.5, owner.getZ(), speed);
            } else {
                mob.getNavigation().moveTo(owner, speed);
            }
        }

        private void teleportNear() {
            for (int i = 0; i < 12; i++) {
                double dx = (mob.getRandom().nextDouble() - 0.5) * 6;
                double dz = (mob.getRandom().nextDouble() - 0.5) * 6;
                double x = owner.getX() + dx;
                double z = owner.getZ() + dz;
                double y = owner.getY() + (flying ? 1.5 : 0);
                if (Math.abs(dx) < 1.5 && Math.abs(dz) < 1.5) continue;
                if (!flying && mob.level().getBlockState(BlockPos.containing(x, y - 0.5, z)).getCollisionShape(mob.level(),
                        BlockPos.containing(x, y - 0.5, z)).isEmpty()) continue;
                if (!mob.level().noCollision(mob, mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()))) continue;
                mob.moveTo(x, y, z, mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
                return;
            }
        }
    }

    /** Ataca quem feriu o dono ({@code ownerAttacks=false}) ou quem o dono atacou ({@code ownerAttacks=true}). */
    public static class DefendOwnerGoal<T extends Mob & OwnableEntity> extends TargetGoal {
        private final T summon;
        private final boolean ownerAttacks;
        private LivingEntity candidate;
        private int timestamp;

        public DefendOwnerGoal(T summon, boolean ownerAttacks) {
            super(summon, false);
            this.summon = summon;
            this.ownerAttacks = ownerAttacks;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = summon.getOwner();
            if (owner == null) return false;
            candidate = ownerAttacks ? owner.getLastHurtMob() : owner.getLastHurtByMob();
            int ts = ownerAttacks ? owner.getLastHurtMobTimestamp() : owner.getLastHurtByMobTimestamp();
            return ts != timestamp && candidate != null && !isFriendly(summon, candidate)
                    && canAttack(candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            LivingEntity owner = summon.getOwner();
            if (owner != null) timestamp = ownerAttacks ? owner.getLastHurtMobTimestamp() : owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    private SummonAi() {}
}
