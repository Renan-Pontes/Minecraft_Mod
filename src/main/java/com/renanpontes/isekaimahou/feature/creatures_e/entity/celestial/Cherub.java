package com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.SkyWanderGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Querubim (Jardins de Nuvens): pequeno celestial de suporte. Nao ataca: cura e abencoa os celestiais
 * feridos ou em combate, foge de jogadores proximos e, se atacado, cega o agressor com um clarao e chama os guardas.
 */
public class Cherub extends DimensionalCreature {
    private static final int HEAL_CAST = 14;

    private int healCooldown = 40;
    private int healTicks;
    private int blessCooldown = 100;
    private int flashCooldown;
    private LivingEntity healTarget;
    private LivingEntity fleeFrom;
    private int fleeTicks;

    public Cherub(EntityType<? extends Cherub> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(30, 2, 2, 0.25)
                .add(Attributes.FLYING_SPEED, 0.13)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public Faction faction() {
        return Faction.CELESTIAL;
    }

    @Override
    protected int experience() {
        return 8;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of();
    }

    @Override
    protected List<String> extraAnimations() {
        return List.of("heal", "bless", "flash");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(3, new HoverNearHealGoal());
        goalSelector.addGoal(5, new SkyWanderGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // =====================================================================================
    //  Cura, bencao e clarao
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) return;
        if (healCooldown > 0) healCooldown--;
        if (blessCooldown > 0) blessCooldown--;
        if (flashCooldown > 0) flashCooldown--;
        if (fleeTicks > 0 && --fleeTicks == 0) fleeFrom = null;

        if (healTicks > 0) {
            if (healTarget == null || !healTarget.isAlive() || distanceTo(healTarget) > 18) {
                healTicks = 0;
                setFocus(null);
            } else if (--healTicks == 0) {
                finishHeal(level, healTarget);
            }
            return;
        }
        if (healCooldown <= 0) {
            LivingEntity ally = findWounded();
            if (ally != null) {
                healTarget = ally;
                healTicks = HEAL_CAST;
                healCooldown = 50;
                setFocus(ally);
                playAction("heal");
                CeFx.sound(level, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
            } else {
                healCooldown = 20;
            }
        }
        if (blessCooldown <= 0) {
            List<LivingEntity> fighting = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12),
                    e -> e != this && CelestialHost.isCelestial(e) && e instanceof Mob m && m.getTarget() != null);
            if (!fighting.isEmpty()) {
                bless(level, fighting);
                blessCooldown = 300;
            } else {
                blessCooldown = 40;
            }
        }
    }

    /** Celestial ferido mais grave por perto (inclui o proprio querubim). */
    private LivingEntity findWounded() {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14),
                        e -> e.isAlive() && CelestialHost.isCelestial(e) && e.getHealth() < e.getMaxHealth() * 0.9F
                                && (e == this || hasLineOfSight(e)))
                .stream().min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
    }

    private void finishHeal(ServerLevel level, LivingEntity ally) {
        ally.heal(6.0F);
        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0), this);
        Vec3 at = center(ally);
        CeFx.line(level, position().add(0, 0.5, 0), at, CeFx.dust(0xFFE27A, 1.0F), 3);
        CeFx.burst(level, at, ParticleTypes.HEART, 4, 0.4, 0.0);
        CeFx.burst(level, at, ParticleTypes.END_ROD, 10, 0.4, 0.05);
        CeFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.5F);
        CeFx.sound(level, at, SoundEvents.ALLAY_ITEM_GIVEN, 0.8F, 1.3F);
        setFocus(null);
    }

    /** Morreu no meio da cura: some com o feixe de luz. */
    @Override
    public void die(DamageSource source) {
        healTicks = 0;
        healTarget = null;
        setFocus(null);
        super.die(source);
    }

    private void bless(ServerLevel level, List<LivingEntity> allies) {
        playAction("bless");
        for (LivingEntity a : allies) {
            a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0), this);
            a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0), this);
            CeFx.ring(level, a.position().add(0, 0.2, 0), 0.8, CeFx.dust(0xFFE27A, 1.0F), 14, 0.08);
        }
        CeFx.burst(level, position().add(0, 0.5, 0), ParticleTypes.END_ROD, 16, 0.4, 0.08);
        CeFx.sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.8F);
    }

    @Override
    protected void onHurtBy(LivingEntity attacker, DamageSource source, float amount) {
        CelestialHost.alert(this, attacker);
        fleeFrom = attacker;
        fleeTicks = 120;
        if (flashCooldown <= 0 && distanceTo(attacker) < 8 && level() instanceof ServerLevel level) {
            flashCooldown = 160;
            playAction("flash");
            attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), this);
            CeFx.burst(level, position().add(0, 0.5, 0), ParticleTypes.FLASH, 1, 0.0, 0.0);
            CeFx.burst(level, position().add(0, 0.5, 0), ParticleTypes.END_ROD, 30, 0.2, 0.25);
            CeFx.sound(level, position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.8F);
        }
    }

    // =====================================================================================
    //  IA de movimento
    // =====================================================================================

    /** Foge de quem o atacou e de jogadores (fora do criativo) muito perto. */
    private class FleeGoal extends Goal {
        private Entity threat;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (fleeFrom != null && fleeFrom.isAlive() && distanceTo(fleeFrom) < 12) {
                threat = fleeFrom;
                return true;
            }
            Player p = level().getNearestPlayer(Cherub.this, 4.5);
            if (p != null && !p.isCreative() && !p.isSpectator()) {
                threat = p;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return threat != null && threat.isAlive() && distanceTo(threat) < 10 && !getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 away = position().subtract(threat.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            Vec3 dest = position().add(away.normalize().scale(8)).add(0, 2 + random.nextInt(3), 0);
            BlockPos p = BlockPos.containing(dest);
            for (int i = 0; i < 4 && !level().getBlockState(p).isAir(); i++) p = p.above();
            getNavigation().moveTo(dest.x, p.getY() + 0.2, dest.z, 1.4);
        }

        @Override
        public void stop() {
            threat = null;
        }
    }

    /** Durante a cura, chega perto do aliado. */
    private class HoverNearHealGoal extends Goal {
        HoverNearHealGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return healTicks > 0 && healTarget != null && healTarget != Cherub.this;
        }

        @Override
        public void tick() {
            if (healTarget == null) return;
            getLookControl().setLookAt(healTarget, 30.0F, 30.0F);
            if (distanceTo(healTarget) > 5) {
                getNavigation().moveTo(healTarget.getX(), healTarget.getY() + healTarget.getBbHeight() + 1.0, healTarget.getZ(), 1.1);
            } else {
                getNavigation().stop();
            }
        }
    }

    // =====================================================================================
    //  Visual, sons, dados
    // =====================================================================================

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(4) == 0) level().addParticle(CeFx.dust(0xFFE27A, 0.5F), getRandomX(0.6), getY() + 0.9, getRandomZ(0.6), 0, -0.01, 0);
        if (r.nextInt(12) == 0) level().addParticle(ParticleTypes.END_ROD, getRandomX(0.5), getY() + 0.4, getRandomZ(0.5), 0, -0.01, 0);
        Entity f = focus();
        if (f != null) {
            Vec3 from = position().add(0, 0.5, 0);
            Vec3 to = f.position().add(0, f.getBbHeight() * 0.5, 0);
            CeFx.clientLine(level(), from, to, CeFx.dust(0xFFF4C0, 0.7F), 1.5, 0.08);
            if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.END_ROD, to.x, to.y, to.z, 0, 0.05, 0);
        }
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.4, 0);
        CeFx.burst(level, c, ParticleTypes.END_ROD, 20, 0.3, 0.08);
        CeFx.burst(level, c, CeFx.dust(0xFFFFFF, 1.0F), 20, 0.4, 0.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BlessCooldown", blessCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        blessCooldown = tag.getInt("BlessCooldown");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }
}
