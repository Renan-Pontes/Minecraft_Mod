package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Metas de IA comuns aos goblins. */
public final class GoblinGoals {

    /** Com pouca vida, corre para longe de quem o atacou (e volta quando se recupera). */
    public static class FleeWhenWeak extends Goal {
        private final AbstractGoblin goblin;
        private LivingEntity threat;
        private int ticks;
        private int cooldown;

        public FleeWhenWeak(AbstractGoblin goblin) {
            this.goblin = goblin;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean weak() {
            return goblin.getHealth() < goblin.getMaxHealth() * 0.3F;
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (!goblin.canFlee() || !weak()) return false;
            LivingEntity t = goblin.getLastHurtByMob() != null ? goblin.getLastHurtByMob() : goblin.getTarget();
            if (t == null || !t.isAlive() || t.distanceToSqr(goblin) > 16 * 16) return false;
            threat = t;
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            goblin.setFleeing(true);
            runAway();
        }

        private void runAway() {
            Vec3 pos = DefaultRandomPos.getPosAway(goblin, 16, 7, threat.position());
            if (pos != null) goblin.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.45);
        }

        @Override
        public boolean canContinueToUse() {
            return threat != null && threat.isAlive() && ticks < 160 && goblin.getHealth() < goblin.getMaxHealth() * 0.55F;
        }

        @Override
        public void tick() {
            ticks++;
            if (goblin.getNavigation().isDone() && threat.distanceToSqr(goblin) < 14 * 14) runAway();
        }

        @Override
        public void stop() {
            goblin.setFleeing(false);
            threat = null;
            cooldown = 100;
        }
    }

    /** Sem briga: anda até itens no chão e os "guarda" (devolvidos ao morrer). */
    public static class StealItems extends Goal {
        private final AbstractGoblin goblin;
        private ItemEntity item;
        private int searchDelay;
        private int repath;

        public StealItems(AbstractGoblin goblin) {
            this.goblin = goblin;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--searchDelay > 0) return false;
            searchDelay = 20 + goblin.getRandom().nextInt(20);
            if (!goblin.canSteal() || goblin.getTarget() != null || goblin.isFleeing()) return false;
            List<ItemEntity> items = goblin.level().getEntitiesOfClass(ItemEntity.class, goblin.getBoundingBox().inflate(10.0, 4.0, 10.0),
                    e -> e.isAlive() && !e.hasPickUpDelay() && goblin.wantsToPickUp(e.getItem()));
            item = items.stream().min(Comparator.comparingDouble(goblin::distanceToSqr)).orElse(null);
            return item != null;
        }

        @Override
        public void start() {
            repath = 0;
            goblin.getNavigation().moveTo(item, 1.15);
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && goblin.getTarget() == null && !goblin.isFleeing()
                    && goblin.distanceToSqr(item) < 16 * 16;
        }

        @Override
        public void tick() {
            goblin.getLookControl().setLookAt(item, 30.0F, 30.0F);
            if (goblin.distanceToSqr(item) < 2.0) {
                goblin.stealItem(item);
                item = null;
                return;
            }
            if (++repath % 10 == 0) goblin.getNavigation().moveTo(item, 1.15);
        }

        @Override
        public void stop() {
            item = null;
            goblin.getNavigation().stop();
        }
    }

    /**
     * Anda em bando: segue o goblin de maior patente por perto (ou, entre iguais, o "mais antigo" —
     * menor id), mantendo uma distância confortável.
     */
    public static class FollowLeader extends Goal {
        private final AbstractGoblin goblin;
        private AbstractGoblin leader;
        private int delay;

        public FollowLeader(AbstractGoblin goblin) {
            this.goblin = goblin;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean outranks(AbstractGoblin other) {
            return other.rank() > goblin.rank() || (other.rank() == goblin.rank() && other.getId() < goblin.getId());
        }

        @Override
        public boolean canUse() {
            if (--delay > 0) return false;
            delay = 40;
            if (goblin.getTarget() != null || goblin.rank() >= 3) return false;
            leader = goblin.level().getEntitiesOfClass(AbstractGoblin.class, goblin.getBoundingBox().inflate(20.0, 6.0, 20.0),
                            g -> g != goblin && g.isAlive() && outranks(g))
                    .stream().min(Comparator.comparingDouble(goblin::distanceToSqr)).orElse(null);
            return leader != null && goblin.distanceToSqr(leader) > 7 * 7;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && goblin.getTarget() == null && goblin.distanceToSqr(leader) > 3.5 * 3.5;
        }

        @Override
        public void start() {
            delay = 0;
        }

        @Override
        public void tick() {
            if (++delay % 10 == 1) goblin.getNavigation().moveTo(leader, 1.0);
        }

        @Override
        public void stop() {
            leader = null;
            delay = 40;
            goblin.getNavigation().stop();
        }
    }

    private GoblinGoals() {}
}
