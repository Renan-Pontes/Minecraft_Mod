package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Rotação Infinita (Ato 4): a espiral dourada que prende o alvo num giro sem fim. Enquanto dura, o alvo fica preso
 * no lugar girando (chefes só ficam muito lentos), recebe o giro (dano) e os efeitos da magia a cada 0,5 s e não
 * consegue fugir. Some quando o tempo acaba ou o alvo cai — não é morte instantânea, é pior: é não poder sair.
 */
public class InfiniteSpinEntity extends Entity implements GeoEntity {
    public static final int PULSE = 10;
    public static final float SPIN_DAMAGE = 2.0F;
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(InfiniteSpinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(InfiniteSpinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(InfiniteSpinEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_infinite_spin.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID targetId;
    @Nullable
    private Vec3 anchor;

    public InfiniteSpinEntity(EntityType<? extends InfiniteSpinEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Prende {@code target} na Rotação Infinita (substitui uma espiral que já estivesse nele). */
    public static void attach(SpellContext ctx, LivingEntity target, int ticks) {
        ServerLevel sl = ctx.level();
        for (InfiniteSpinEntity old : sl.getEntitiesOfClass(InfiniteSpinEntity.class, target.getBoundingBox().inflate(2),
                e -> target.getUUID().equals(e.targetId))) {
            old.discard();
        }
        InfiniteSpinEntity e = new InfiniteSpinEntity(SbRegistry.INFINITE.get(), sl);
        e.setPos(target.getX(), target.getY(), target.getZ());
        e.ctx = ctx;
        e.spellTag = ctx.save();
        e.targetId = target.getUUID();
        e.anchor = target.position();
        e.entityData.set(DATA_TARGET, target.getId());
        e.entityData.set(DATA_LIFE, Math.max(40, ticks));
        e.entityData.set(DATA_SIZE, Math.max(0.8F, Math.max(target.getBbWidth(), target.getBbHeight() * 0.6F)));
        sl.addFreshEntity(e);
        SbFxNet.at(sl, SbFxNet.INFINITE, target.position(), Vec3.ZERO, e.size(), SbFx.GOLD, target.getId());
        SbFx.sound(sl, target.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.6F, 1.8F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_LIFE, 200);
        builder.define(DATA_SIZE, 1.0F);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    /** 0..1: a espiral abre e se fecha no fim. */
    public float openness(float partial) {
        float t = tickCount + partial;
        return Math.min(Math.min(1F, t / 8F), Math.max(0F, Math.min(1F, (life() - t) / 10F)));
    }

    @Nullable
    public Entity target() {
        int id = entityData.get(DATA_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public void tick() {
        super.tick();
        Entity t = target();
        if (t != null) setPos(t.getX(), t.getY(), t.getZ());
        if (!(level() instanceof ServerLevel sl)) return;
        if (!(t instanceof LivingEntity target) || !target.isAlive() || tickCount >= life()) {
            collapse(sl);
            return;
        }
        boolean boss = BossKit.isBossLike(target);
        if (anchor == null) anchor = target.position();
        if (boss) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 3, false, false), ctx != null ? ctx.caster() : null);
        } else {
            // preso no giro: não sai do lugar
            target.setDeltaMovement(Vec3.ZERO);
            if (target.position().distanceToSqr(anchor) > 0.04) {
                if (target instanceof ServerPlayer sp) sp.teleportTo(anchor.x, anchor.y, anchor.z);
                else target.setPos(anchor.x, anchor.y, anchor.z);
            }
            target.hurtMarked = true;
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
                float yaw = SbFx.wrap(mob.getYRot() + 30F);
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
            }
        }
        SpellContext c = context();
        if (c != null && tickCount % PULSE == 0) {
            target.invulnerableTime = 0;
            c.damage(target, SPIN_DAMAGE, c.formStats(), this);
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                c.resolveOnEntity(target, this);
            }
            SbSpin.apply(target, c.caster(), SbSpin.MAX_LEVEL, 30);
            SbFx.sound(sl, target.position(), SoundEvents.CHAIN_HIT, 0.6F, 1.2F + (tickCount % 40) / 80F);
        }
        if (tickCount % 3 == 0) {
            float ph = tickCount * 0.5F;
            SbFx.spiral(sl, target.position(), SbFx.GOLD, size() * 0.9 + 0.3, target.getBbHeight() + 0.4, 10, ph);
        }
        if (tickCount % 20 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    4, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private void collapse(ServerLevel sl) {
        Vec3 p = position().add(0, size() * 0.5, 0);
        SbFx.goldenSpiral(sl, p, new Vec3(0, 0, 1), 1.2);
        sl.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.7F);
        SpellContext c = context();
        if (c != null) c.end(p, new Vec3(0, 1, 0));
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        // a espiral não sobrevive a recarregar o mundo (o alvo pode ter mudado de lugar): some no primeiro tick
        // (sem a magia: ao sumir não dispara o próximo estágio)
        entityData.set(DATA_LIFE, 0);
        spellTag = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Spent", true);
    }
}
