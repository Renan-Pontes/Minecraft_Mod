package com.renanpontes.isekaimahou.feature.nether_citadel.entity;

import java.util.EnumSet;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Súcubo Feiticeira: fica a meia distância e conjura.
 * <ul>
 *   <li><b>Raio Sombrio</b>: linha marcada até o alvo; quem ficar nela leva dano de magia e fica Corrompido;</li>
 *   <li><b>Encanto</b>: um círculo segue o alvo; se pegar, puxa a vítima até ela, deixa-a fraca e lenta, e o súcubo se cura;</li>
 *   <li>frágil de perto: corra até ela.</li>
 * </ul>
 */
public class DemonSuccubus extends CitadelDemon {
    private static final int BOLT_COLOR = 0x9B4DFF;
    private static final int CHARM_COLOR = 0xFF6AC8;
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0F, 0.42F, 0.78F), 1.2F);
    private int boltCd = 40;
    private int charmCd = 120;
    private int castTimer;

    public DemonSuccubus(EntityType<? extends DemonSuccubus> type, Level level) {
        super(type, level);
        this.xpReward = 18;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected String modelId() {
        return "demon_succubus";
    }

    @Override
    protected List<String> actions() {
        return List.of("cast", "charm");
    }

    @Override
    protected void registerCombatGoals() {
        goalSelector.addGoal(2, new KeepDistanceGoal());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (boltCd > 0) boltCd--;
        if (charmCd > 0) charmCd--;
        if (castTimer > 0) {
            castTimer--;
            getNavigation().stop();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !hasLineOfSight(target)) return;
        double d = distanceTo(target);
        if (charmCd <= 0 && d < 12) {
            charm(sl, target);
        } else if (boltCd <= 0 && d < 18) {
            bolt(sl, target);
        }
    }

    private void bolt(ServerLevel sl, LivingEntity target) {
        boltCd = 50 + random.nextInt(30);
        castTimer = 14;
        getLookControl().setLookAt(target, 30F, 30F);
        action("cast");
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.3F);
        Vec3 from = position();
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1).normalize();
        Vec3 probe = from.add(0, 1.2, 0);
        HitResult hit = sl.clip(new ClipContext(probe, probe.add(dir.scale(18)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = hit.getType() == HitResult.Type.MISS ? 18.0 : Math.max(2.0, hit.getLocation().distanceTo(probe) - 0.3);
        Vec3 to = from.add(dir.scale(len));
        Telegraph.delayed(sl, Telegraph.Shape.line(from.add(dir), to, 1.2F).warmup(16).color(BOLT_COLOR).owner(this).noRunes(), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), 7.0F)) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
                }
            }
            for (int i = 0; i <= 16; i++) {
                Vec3 p = from.add(to.subtract(from).scale(i / 16.0));
                sl.sendParticles(ParticleTypes.WITCH, p.x, p.y + 1.1, p.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
            playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.8F);
        });
    }

    private void charm(ServerLevel sl, LivingEntity target) {
        charmCd = 200 + random.nextInt(60);
        castTimer = 22;
        action("charm");
        playSound(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.2F, 0.6F);
        Telegraph.delayed(sl, Telegraph.Shape.follow(target, 1.6F).warmup(22).color(CHARM_COLOR).owner(this).noRunes(), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                Vec3 pull = position().subtract(e.position());
                double dist = pull.length();
                if (dist > 1.0E-3) {
                    Vec3 v = pull.normalize().scale(Math.min(1.4, dist * 0.18));
                    e.setDeltaMovement(v.x, 0.35, v.z);
                    e.hurtMarked = true;
                }
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                heal(6.0F);
                sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                sl.sendParticles(PINK, getX(), getY() + 1.2, getZ(), 12, 0.3, 0.5, 0.3, 0.0);
            }
        });
    }

    /** Mantém 6–12 blocos do alvo, andando de lado quando está na faixa. */
    private class KeepDistanceGoal extends Goal {
        private int strafe = 1;

        KeepDistanceGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null || castTimer > 0) return;
            getLookControl().setLookAt(t, 30F, 30F);
            double d = distanceTo(t);
            if (d > 12 || !hasLineOfSight(t)) {
                if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(t, 1.0);
            } else if (d < 6) {
                Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                Vec3 to = position().add(away.scale(5));
                if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(to.x, to.y, to.z, 1.15);
            } else {
                getNavigation().stop();
                if (tickCount % 40 == 0) strafe = -strafe;
                getMoveControl().strafe(0.0F, strafe * 0.4F);
            }
        }
    }
}
