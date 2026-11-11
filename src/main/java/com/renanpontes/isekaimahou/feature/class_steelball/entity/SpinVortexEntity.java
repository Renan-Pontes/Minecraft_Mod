package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Turbilhão Giratório: a bola cravada no chão gira tão rápido que o ar e a terra em volta giram junto. Puxa os inimigos
 * para o centro em espiral e aplica os efeitos da magia a cada 1 s. Some sozinho; no máx. 2 por conjurador.
 */
public class SpinVortexEntity extends Entity implements GeoEntity {
    public static final int MAX_PER_CASTER = 2;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SpinVortexEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_GOLD = SynchedEntityData.defineId(SpinVortexEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(SpinVortexEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_vortex.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID caster;

    public SpinVortexEntity(EntityType<? extends SpinVortexEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SpinVortexEntity create(SpellContext ctx, Vec3 at, float radius, int life, boolean golden) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.caster() != null ? ctx.caster().getUUID() : null;
        if (owner != null) {
            // limite por conjurador: o mais antigo se desfaz
            List<SpinVortexEntity> mine = sl.getEntitiesOfClass(SpinVortexEntity.class, new AABB(at, at).inflate(96),
                    v -> owner.equals(v.caster));
            if (mine.size() >= MAX_PER_CASTER) {
                mine.sort((a, b) -> Integer.compare(b.tickCount, a.tickCount));
                mine.get(0).finish();
            }
        }
        SpinVortexEntity e = new SpinVortexEntity(SbRegistry.VORTEX.get(), sl);
        e.setPos(at.x, at.y, at.z);
        e.ctx = ctx;
        e.spellTag = ctx.save();
        e.caster = owner;
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_GOLD, golden);
        e.entityData.set(DATA_LIFE, life);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.5F);
        builder.define(DATA_GOLD, false);
        builder.define(DATA_LIFE, 80);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public boolean golden() {
        return entityData.get(DATA_GOLD);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    /** 0..1: abre nos primeiros 6 ticks e fecha nos últimos 8 (escala do modelo). */
    public float openness(float partial) {
        float t = tickCount + partial;
        float in = Math.min(1F, t / 6F);
        float out = Math.min(1F, Math.max(0F, (life() - t) / 8F));
        return Math.min(in, out);
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount >= life()) {
            finish();
            return;
        }
        SpellContext c = context();
        if (c == null) {
            discard();
            return;
        }
        Vec3 center = position();
        float r = radius();
        // puxão em espiral (tangente + centro); chefes quase não se mexem
        for (LivingEntity e : c.enemiesAround(center.add(0, 0.5, 0), r * 1.6)) {
            Vec3 to = center.subtract(e.position()).multiply(1, 0, 1);
            double d = to.length();
            if (d < 0.3) continue;
            Vec3 in = to.normalize();
            Vec3 tangent = new Vec3(-in.z, 0, in.x);
            double k = BossKit.isBossLike(e) ? 0.15 : 1.0;
            e.setDeltaMovement(e.getDeltaMovement().add(in.scale(0.07 * k)).add(tangent.scale(0.05 * k)));
            e.hurtMarked = true;
        }
        if (tickCount % 20 == 10) {
            c.resolveArea(center.add(0, 0.6, 0), r, this, false);
            SbFx.sound(sl, center, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6F, 1.6F);
        }
        if (tickCount % 2 == 0) {
            float ph = tickCount * 0.35F;
            int rgb = golden() ? SbFx.GOLD : SbFx.STEEL;
            for (int i = 0; i < 3; i++) {
                double a = ph + i * Math.PI * 2 / 3;
                double rr = r * (0.35 + 0.6 * ((tickCount + i * 7) % 20) / 20.0);
                sl.sendParticles(SbFx.dust(rgb, 0.9F), center.x + Math.cos(a) * rr, center.y + 0.15, center.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
        }
        if (tickCount % 10 == 0) SbFx.sound(sl, center, SoundEvents.CHAIN_STEP, 0.4F, 1.5F);
    }

    private void finish() {
        if (level() instanceof ServerLevel && !isRemoved()) {
            SpellContext c = context();
            if (c != null) c.end(position().add(0, 0.5, 0), new Vec3(0, 1, 0));
        }
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
        entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        entityData.set(DATA_GOLD, tag.getBoolean("Gold"));
        entityData.set(DATA_LIFE, tag.getInt("Life"));
        tickCount = tag.getInt("Age");
        spellTag = tag.contains("Spell", Tag.TAG_COMPOUND) ? tag.getCompound("Spell") : null;
        caster = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", radius());
        tag.putBoolean("Gold", golden());
        tag.putInt("Life", life());
        tag.putInt("Age", tickCount);
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
        if (caster != null) tag.putUUID("Caster", caster);
    }
}
