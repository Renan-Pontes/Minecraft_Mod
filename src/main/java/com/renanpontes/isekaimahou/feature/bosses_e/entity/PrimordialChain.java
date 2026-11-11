package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Âncora das Correntes do Abismo: um obelisco de obsidiana com anéis de corrente que o Dragão Primordial crava no chão,
 * ACORRENTANDO um jogador. Enquanto ela estiver de pé, o preso não se afasta mais que {@link #LEASH} blocos (é puxado
 * de volta), perde 1,5% da vida (nunca abaixo de 10%) e 4% do recurso por segundo — e o dragão se cura. Aliados (ou o
 * próprio preso) quebram a âncora com golpes, flechas ou magias. Se ela durar 15 s, ESMAGA: o preso vira estátua.
 * Temporária: nunca é salva no mundo e some sozinha sem preso/dono.
 */
public class PrimordialChain extends Mob implements Enemy, GeoEntity {
    public static final String ID = "primordial_chain";
    public static final float BASE_HEALTH = 30F;
    public static final double LEASH = 4.5;
    private static final int LIFE = 300;
    private static final int RISE = 16;
    private static final EntityDataAccessor<Integer> DATA_VICTIM = SynchedEntityData.defineId(PrimordialChain.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.primordial_chain.idle");
    private static final RawAnimation RISE_ANIM = RawAnimation.begin().thenPlay("animation.primordial_chain.rise")
            .thenLoop("animation.primordial_chain.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID victimId;
    @Nullable private UUID dragonId;
    private int life;

    public PrimordialChain(EntityType<? extends PrimordialChain> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    /** Prende {@code victim}; a vida cresce com o grupo ({@code hpScale}). Chame antes de pôr no mundo. */
    public void setup(@Nullable PrimordialDragon dragon, LivingEntity victim, float hpScale) {
        this.victimId = victim.getUUID();
        this.dragonId = dragon != null ? dragon.getUUID() : null;
        entityData.set(DATA_VICTIM, victim.getId());
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(BASE_HEALTH * Math.max(1F, hpScale));
        setHealth(getMaxHealth());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VICTIM, -1);
    }

    public boolean ownedBy(PrimordialDragon d) {
        return dragonId != null && dragonId.equals(d.getUUID());
    }

    @Nullable
    public LivingEntity victim() {
        if (level().isClientSide) {
            int id = entityData.get(DATA_VICTIM);
            return id >= 0 && level().getEntity(id) instanceof LivingEntity le ? le : null;
        }
        if (victimId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(victimId) instanceof LivingEntity le ? le : null;
    }

    @Nullable
    private PrimordialDragon dragon() {
        if (dragonId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(dragonId) instanceof PrimordialDragon d && d.isAlive() ? d : null;
    }

    /** Ponto de onde a corrente sai (o anel de cima). */
    public Vec3 chainOrigin() {
        return position().add(0, 2.2, 0);
    }

    // ---------------------------------------------------------------- regras

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {
    }

    @Override
    public boolean isPushedByFluid(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof PrimordialDragon d && ownedBy(d)) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.CRAMMING)) return false;
        if (source.getEntity() != null && isAlliedTo(source.getEntity())) return false;
        boolean ok = super.hurt(source, amount);
        if (ok && level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.defaultBlockState()), getX(), getY() + 1.2, getZ(),
                    8, 0.3, 0.6, 0.3, 0.1);
        }
        return ok;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        setDeltaMovement(Vec3.ZERO);
        life++;
        LivingEntity v = victim();
        if (v == null || !v.isAlive() || v.level() != level() || v.distanceToSqr(this) > 48 * 48
                || (v instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))) {
            shatter(sl, false);
            return;
        }
        if (entityData.get(DATA_VICTIM) != v.getId()) entityData.set(DATA_VICTIM, v.getId());
        if (life < RISE) {
            if (life % 4 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.5, getZ(), 10, 0.6, 0.3, 0.6, 0.1);
            return;
        }
        if (life == RISE) {
            DragonFx.sound(sl, position(), SoundEvents.CHAIN_PLACE, 2F, 0.5F);
            DragonFx.sound(sl, v.position(), SoundEvents.CHAIN_FALL, 1.5F, 0.6F);
        }
        // Corrente: puxa de volta quem tentou fugir.
        Vec3 o = chainOrigin();
        Vec3 to = new Vec3(getX(), v.getY(), getZ()).subtract(v.position());
        double d = to.horizontalDistance();
        if (d > LEASH) {
            Vec3 pull = to.multiply(1, 0, 1).normalize().scale(Math.min(0.9, (d - LEASH) * 0.35 + 0.25));
            v.setDeltaMovement(pull.x, Math.max(v.getDeltaMovement().y, 0.05), pull.z);
            v.hurtMarked = true;
            if (life % 6 == 0) DragonFx.sound(sl, v.position(), SoundEvents.CHAIN_STEP, 1.2F, 0.6F);
        }
        if (life % 20 == 0) {
            PrimordialDragon dr = dragon();
            float took = DragonCurses.drainHealth(v, 0.015F, 0.10F, dr != null ? dr : this);
            float mana = v instanceof ServerPlayer sp ? DragonCurses.drainResource(sp, 0.04F) : 0F;
            if (dr != null && (took > 0 || mana > 0)) dr.healEffectiveFraction(0.0015F);
            DragonFx.line(sl, DragonFx.dust(0xC0182E, 1.1F), v.position().add(0, 1, 0), o, 0.8);
        }
        if (life % 3 == 0) DragonFx.line(sl, DragonFx.dust(0x3A2A4A, 0.9F), o, v.position().add(0, 1, 0), 1.6);
        if (life >= LIFE) {
            // Esmagamento: ninguém quebrou a âncora a tempo.
            DragonCurses.statue(v, 120, dragon());
            DragonFx.sound(sl, v.position(), SoundEvents.ANVIL_LAND, 1.4F, 0.5F);
            shatter(sl, true);
        }
    }

    /** A âncora se desfaz (quebrada, esmagou, perdeu o preso). */
    private void shatter(ServerLevel sl, boolean crushed) {
        if (isRemoved()) return;
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 1.2, getZ(), 30, 0.5,
                1, 0.5, 0.2);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 30, 0.5, 1, 0.5, 0.1);
        DragonFx.sound(sl, position(), SoundEvents.CHAIN_BREAK, 2F, 0.6F);
        if (!crushed && victim() instanceof ServerPlayer sp && sp.isAlive()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.primordial_dragon.chain_free").withStyle(ChatFormatting.GREEN), true);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl && !isRemoved()) shatter(sl, false);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide || !isAlive()) return;
        LivingEntity v = victim();
        if (v == null || tickCount % 2 != 0) return;
        // Elos da corrente (só visual, no cliente): um ponto a cada ~0,6 bloco, com uma barriga que balança.
        Vec3 a = chainOrigin();
        Vec3 b = v.position().add(0, v.getBbHeight() * 0.5, 0);
        Vec3 dd = b.subtract(a);
        int n = Math.min(40, Math.max(2, (int) (dd.length() / 0.6)));
        double sag = Math.min(1.2, dd.length() * 0.12) * (0.8 + 0.2 * Math.sin(tickCount * 0.2));
        for (int i = 1; i < n; i++) {
            double k = i / (double) n;
            Vec3 q = a.add(dd.scale(k)).add(0, -Math.sin(Math.PI * k) * sag, 0);
            level().addParticle(i % 2 == 0 ? DragonFx.dust(0x6A6A78, 0.8F) : DragonFx.dust(0x9A4FD8, 0.7F), q.x, q.y, q.z, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CHAIN_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CHAIN_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHAIN_BREAK;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0,
                s -> s.setAndContinue(tickCount < RISE ? RISE_ANIM : IDLE)).setAnimationSpeed(1.0));
    }
}
