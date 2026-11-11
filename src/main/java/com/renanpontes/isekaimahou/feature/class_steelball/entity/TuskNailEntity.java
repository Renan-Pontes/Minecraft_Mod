package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Unhas do pacote "Tusk" (homenagem às unhas-bala que giram):
 * <ul>
 *   <li>{@link #ACT1} — Ato 1: a unha voa em SACA-ROLHAS em volta da linha da mira (o desenho da rotação no ar) e,
 *   ao acertar, faz o alvo RODOPIAR (Girado + o corpo é torcido para o lado);</li>
 *   <li>{@link #ACT2} — Ato 2: a unha dourada reta; onde acerta (alvo ou chão) deixa um Buraco Espiral que persegue
 *   o inimigo ({@link SpiralHoleEntity}).</li>
 * </ul>
 * Não é salva no mundo (vive 1,5 s): ao carregar, some.
 */
public class TuskNailEntity extends ThrowableProjectile implements GeoEntity {
    public static final byte ACT1 = 1;
    public static final byte ACT2 = 2;
    private static final int LIFE = 30;
    private static final EntityDataAccessor<Byte> DATA_ACT = SynchedEntityData.defineId(TuskNailEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_nail.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    private boolean initialized;
    // saca-rolhas (Ato 1)
    private Vec3 axisFrom = Vec3.ZERO;
    private Vec3 axisDir = new Vec3(0, 0, 1);
    private float speed = 2.0F;
    private float phase;
    private int pierce;
    private int holeTicks = 120;
    private final Set<UUID> hit = new HashSet<>();

    public TuskNailEntity(EntityType<? extends TuskNailEntity> type, Level level) {
        super(type, level);
    }

    /** Unha-bala do Ato 1 (saca-rolhas). */
    public static TuskNailEntity act1(SpellContext ctx, Vec3 from, Vec3 dir, float speed, float phase) {
        TuskNailEntity e = base(ctx, from, ACT1);
        e.axisFrom = from;
        e.axisDir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        e.speed = speed;
        e.phase = phase;
        e.pierce = Math.max(0, ctx.formStats().pierce);
        e.setDeltaMovement(e.axisDir.scale(speed));
        return e;
    }

    /** Unha dourada do Ato 2: reta e rápida; o buraco dura {@code holeTicks}. */
    public static TuskNailEntity act2(SpellContext ctx, Vec3 from, Vec3 dir, float speed, int holeTicks) {
        TuskNailEntity e = base(ctx, from, ACT2);
        e.holeTicks = holeTicks;
        e.shoot(dir.x, dir.y, dir.z, speed, 0.2F);
        return e;
    }

    private static TuskNailEntity base(SpellContext ctx, Vec3 from, byte act) {
        TuskNailEntity e = new TuskNailEntity(SbRegistry.TUSK_NAIL.get(), ctx.level());
        e.entityData.set(DATA_ACT, act);
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.ctx = ctx;
        e.initialized = true;
        e.setPos(from.x, from.y, from.z);
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ACT, ACT1);
    }

    public byte act() {
        return entityData.get(DATA_ACT);
    }

    public boolean golden() {
        return act() == ACT2;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !initialized) {
            discard();
            return;
        }
        if (!level().isClientSide && act() == ACT1) corkscrew();
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (tickCount > LIFE) {
            if (ctx != null) ctx.end(position(), getDeltaMovement());
            discard();
            return;
        }
        int rgb = golden() ? SbFx.GOLD : SbFx.NAIL;
        sl.sendParticles(SbFx.dust(rgb, 0.7F), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        if (tickCount % 2 == 0) {
            sl.sendParticles(golden() ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Ato 1: a próxima posição gira em volta do eixo da mira (raio cresce até 0,55 bloco). */
    private void corkscrew() {
        int t = tickCount + 1;
        Vec3 right = axisDir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(axisDir).normalize();
        double r = Math.min(0.55, 0.12 * t);
        double a = phase + t * 0.95;
        Vec3 next = axisFrom.add(axisDir.scale(speed * t)).add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
        setDeltaMovement(next.subtract(position()));
        hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        if (target == getOwner() || target instanceof TuskNailEntity) return false;
        return ctx == null || !ctx.isAlly(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl) || ctx == null) return;
        Entity target = result.getEntity();
        hit.add(target.getUUID());
        Vec3 at = result.getLocation();
        Entity owner = getOwner();
        ctx.impact(at, getDeltaMovement(), target);
        target.invulnerableTime = 0;
        ctx.resolveOnEntity(target, this);
        if (owner instanceof ServerPlayer sp && target instanceof LivingEntity) MagicApi.addCounter(sp, SbProgress.NAIL_HITS, 1);
        if (act() == ACT1) {
            if (target instanceof LivingEntity le && le.isAlive()) twirl(le, owner instanceof LivingEntity o ? o : null);
            sl.sendParticles(SbFx.dust(SbFx.NAIL, 1.1F), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0);
            SbFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.9F);
            if (pierce > 0) {
                pierce--;
                return;
            }
            discard();
        } else {
            SpiralHoleEntity.chaser(ctx, at, target instanceof LivingEntity le ? le : null, holeTicks);
            SbFx.goldenSpiral(sl, at, getDeltaMovement(), 1.0);
            SbFx.sound(sl, at, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.8F, 1.5F);
            discard();
        }
    }

    /** Rodopio do Ato 1: Girado e o corpo torcido de lado (criaturas comuns giram de verdade). */
    private static void twirl(LivingEntity le, @Nullable LivingEntity owner) {
        SbSpin.apply(le, owner, 0, 50);
        if (le instanceof Mob mob && !BossKit.isBossLike(mob)) {
            float yaw = SbFx.wrap(mob.getYRot() + 120F);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            Vec3 side = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).scale(0.18);
            mob.push(side.x, 0.08, side.z);
            mob.hurtMarked = true;
        }
        if (le.level() instanceof ServerLevel sl) {
            SbFx.spinRing(sl, le.position().add(0, le.getBbHeight() * 0.5, 0), SbFx.NAIL, le.getBbWidth() * 0.7 + 0.3, 10);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 at = result.getLocation();
        if (ctx != null) {
            ctx.impact(at, getDeltaMovement(), null);
            ctx.resolveOnBlock(result, this);
            if (act() == ACT2) SpiralHoleEntity.chaser(ctx, at.add(0, 0.05, 0), null, holeTicks);
        }
        sl.sendParticles(SbFx.dust(golden() ? SbFx.GOLD : SbFx.NAIL, 0.9F), at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0);
        SbFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_STEP, 0.6F, 1.7F);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // nada: a unha vive 1,5 s e não sobrevive a salvar/carregar
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        initialized = false;
    }
}
