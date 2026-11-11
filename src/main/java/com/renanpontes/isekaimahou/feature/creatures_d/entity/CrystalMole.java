package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Toupeira de Cristal: tímida moradora das cavernas com cristais de ametista nas costas.
 * Se um jogador chega perto, cava e reaparece longe. Provocada, atira estilhaços de cristal e
 * faz emboscadas: some na terra e brota atrás do alvo dando uma garrada.
 */
public class CrystalMole extends ElementalCreature {
    private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(CrystalMole.class, EntityDataSerializers.BOOLEAN);
    private int digTicks;
    private int underTicks;
    private boolean ambush;
    private long nextFleeBurrow;

    public CrystalMole(EntityType<? extends CrystalMole> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public Element element() {
        return Element.EARTH;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast", "burrow", "emerge"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURROWED, false);
    }

    public boolean isBurrowed() {
        return entityData.get(BURROWED);
    }

    private void setBurrowed(boolean b) {
        entityData.set(BURROWED, b);
        setInvisible(b);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new AbilityGoal(this, null, 5, 14, 0, 160, 240, true, t -> startBurrow(true))
                .when(t -> onGround()));
        goalSelector.addGoal(3, new AbilityGoal(this, "cast", 3, 12, 10, 70, 110, true, this::shardVolley)
                .windup((t, left) -> {
                    if (left % 2 == 0) serverParticles(ElementFx.dust(Element.CRYSTAL, 0.8F), position().add(0, 0.8, 0), 3, 0.3, 0);
                }));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Enquanto cava ou está debaixo da terra, a IA fica parada (a máquina de estados roda no aiStep). */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isBurrowed() || digTicks > 0;
    }

    private void shardVolley(LivingEntity target) {
        MobMagic.aim(this, target);
        for (int i = 0; i < 3; i++) {
            ElementalBolt.shoot(this, target, Element.EARTH, 3.0F, 1.0F, 4.0F + i * 4.0F, Items.AMETHYST_SHARD);
        }
        playSoundHere(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.4F);
    }

    private boolean canDigHere() {
        BlockPos below = blockPosition().below();
        BlockState s = level().getBlockState(below);
        return onGround() && (s.is(BlockTags.MINEABLE_WITH_SHOVEL) || s.is(BlockTags.MINEABLE_WITH_PICKAXE))
                && s.getDestroySpeed(level(), below) >= 0 && s.getDestroySpeed(level(), below) < 30;
    }

    private void startBurrow(boolean asAmbush) {
        if (isBurrowed() || digTicks > 0 || !canDigHere()) return;
        ambush = asAmbush;
        digTicks = 15;
        getNavigation().stop();
        playAction("burrow");
        playSoundHere(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 1.2F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (digTicks > 0) {
            BlockState below = level().getBlockState(blockPosition().below());
            serverParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), position(), 6, 0.3, 0.1);
            if (digTicks % 4 == 0) playSoundHere(below.getSoundType(level(), blockPosition().below(), this).getHitSound(), 0.8F, 1.0F);
            if (--digTicks == 0) {
                setBurrowed(true);
                underTicks = 20 + random.nextInt(20);
            }
        } else if (isBurrowed()) {
            if (tickCount % 3 == 0) {
                BlockState below = level().getBlockState(blockPosition().below());
                serverParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), position(), 2, 0.2, 0.05);
            }
            if (--underTicks <= 0) emerge();
        } else if (tickCount % 20 == 0 && getTarget() == null && level().getGameTime() >= nextFleeBurrow) {
            // Tímida: um jogador muito perto a faz fugir por baixo da terra
            Player p = level().getNearestPlayer(this, 4.0);
            if (p != null && !p.isShiftKeyDown() && !p.isCreative() && !p.isSpectator() && random.nextInt(3) == 0) {
                nextFleeBurrow = level().getGameTime() + 200;
                startBurrow(false);
            }
        }
    }

    /** Sai da terra: atrás do alvo (emboscada) ou longe dos jogadores (fuga). */
    private void emerge() {
        LivingEntity t = getTarget();
        Vec3 origin = position();
        boolean moved = false;
        for (int i = 0; i < 10 && !moved; i++) {
            Vec3 dest;
            if (ambush && t != null && t.isAlive()) {
                Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
                dest = t.position().subtract(back.normalize().scale(1.8)).add(random.nextGaussian() * 0.6, 2, random.nextGaussian() * 0.6);
            } else {
                double ang = random.nextDouble() * Math.PI * 2;
                double dist = 8 + random.nextDouble() * 5;
                dest = origin.add(Math.cos(ang) * dist, 3, Math.sin(ang) * dist);
            }
            moved = randomTeleport(dest.x, dest.y, dest.z, false);
        }
        setBurrowed(false);
        playAction("emerge");
        BlockState below = level().getBlockState(blockPosition().below());
        serverParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), position(), 30, 0.5, 0.2);
        serverParticles(ElementFx.dust(Element.CRYSTAL, 1.0F), position().add(0, 0.5, 0), 12, 0.4, 0);
        playSoundHere(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.9F);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        if (ambush && t != null && distanceToSqr(t) < 3.0 * 3.0) {
            MobMagic.aim(this, t);
            doHurtTarget(t);
        }
        ambush = false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && isAlive() && getHealth() < getMaxHealth() * 0.4F && random.nextInt(3) == 0) {
            startBurrow(false);
        }
        return r;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return (isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPickable() {
        return !isBurrowed() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isBurrowed() && super.isPushable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Burrowed")) {
            setBurrowed(true);
            underTicks = 10;
        }
    }

    @Override
    protected void clientEffects() {
        if (!isBurrowed() && random.nextInt(10) == 0) {
            Vec3 p = localPoint(-0.1, 0.65, 0);
            level().addParticle(ElementFx.dust(Element.CRYSTAL, 0.6F), p.x + random.nextGaussian() * 0.15, p.y, p.z + random.nextGaussian() * 0.15, 0, 0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isBurrowed() ? null : SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RABBIT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }
}
