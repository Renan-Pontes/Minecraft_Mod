package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * Unha giratória (homenagem às unhas que giram como balas). Rápida e leve:
 * <ul>
 *   <li>Ato 1 — vai reta;</li>
 *   <li>Ato 2 — persegue o alvo em espiral e deixa o corpo Girado;</li>
 *   <li>Ato 3 — agachado, o atirador "entra no buraco" da primeira unha que acertar e reaparece atrás do alvo;</li>
 *   <li>{@link Mode#INFINITE} — a unha dourada da Rotação Infinita (Ato 4): prende o alvo no giro sem fim.</li>
 * </ul>
 */
public class SpinNailEntity extends ThrowableProjectile implements GeoEntity {
    public enum Mode { SPELL, INFINITE, MOB }

    private static final int LIFE = 30;
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(SpinNailEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_GOLD = SynchedEntityData.defineId(SpinNailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_nail.spin");
    /** Conjurações (contexto) que já usaram o buraco do Ato 3 — um teleporte por conjuração. */
    private static final Set<SpellContext> WORMHOLED = Collections.newSetFromMap(new WeakHashMap<>());

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    private int act = 1;
    private int pierce;
    private float damage = 2F;
    private int infiniteTicks = 200;
    private final Set<UUID> hit = new HashSet<>();

    public SpinNailEntity(EntityType<? extends SpinNailEntity> type, Level level) {
        super(type, level);
    }

    public static SpinNailEntity spell(SpellContext ctx, Vec3 from, Vec3 dir, float speed, int act, boolean golden) {
        SpinNailEntity e = new SpinNailEntity(SbRegistry.NAIL.get(), ctx.level());
        e.entityData.set(DATA_MODE, (byte) Mode.SPELL.ordinal());
        e.entityData.set(DATA_GOLD, golden);
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.ctx = ctx;
        e.spellTag = ctx.save();
        e.act = act;
        e.pierce = Math.max(0, ctx.formStats().pierce);
        e.setPos(from.x, from.y, from.z);
        e.shoot(dir.x, dir.y, dir.z, speed, act >= 2 ? 1.5F : 0.6F);
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    /** Unha dourada da Rotação Infinita: ao acertar, prende o alvo por {@code ticks}. */
    public static SpinNailEntity infinite(SpellContext ctx, Vec3 from, Vec3 dir, int ticks) {
        SpinNailEntity e = spell(ctx, from, dir, 3.0F, 4, true);
        e.entityData.set(DATA_MODE, (byte) Mode.INFINITE.ordinal());
        e.infiniteTicks = ticks;
        e.pierce = 0;
        return e;
    }

    /** Unhas do Mestre no desafio. */
    public static SpinNailEntity mob(ServerLevel level, LivingEntity thrower, Vec3 dir, float damage) {
        SpinNailEntity e = new SpinNailEntity(SbRegistry.NAIL.get(), level);
        e.entityData.set(DATA_MODE, (byte) Mode.MOB.ordinal());
        e.setOwner(thrower);
        e.damage = damage;
        Vec3 from = thrower.getEyePosition().subtract(0, 0.3, 0);
        e.setPos(from.x, from.y, from.z);
        e.shoot(dir.x, dir.y, dir.z, 2.0F, 3.0F);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODE, (byte) 0);
        builder.define(DATA_GOLD, false);
    }

    public Mode mode() {
        int i = entityData.get(DATA_MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, i))];
    }

    public boolean golden() {
        return entityData.get(DATA_GOLD);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public void tick() {
        super.tick();
        SbFxNet.trail(this);
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount > LIFE) {
            SpellContext c = context();
            if (c != null) c.end(position(), getDeltaMovement());
            discard();
            return;
        }
        if (mode() != Mode.MOB && act >= 2 && tickCount > 2) steer();
        // rastro desenhado no cliente (SbFxNet.trail)
    }

    /** Ato 2+: a unha corrige o rumo em espiral para o inimigo mais próximo. */
    private void steer() {
        SpellContext c = context();
        if (c == null) return;
        LivingEntity best = null;
        double bestD = 12 * 12;
        for (LivingEntity e : c.enemiesAround(position(), 12)) {
            if (hit.contains(e.getUUID())) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-3) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(position()).normalize();
        // um pouco de giro lateral: a trajetória desenha uma espiral
        Vec3 side = new Vec3(-to.z, 0, to.x).scale(0.18 * Math.sin(tickCount * 1.3));
        setDeltaMovement(v.normalize().lerp(to, 0.35).add(side).normalize().scale(speed));
        hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        Entity owner = getOwner();
        if (target == owner) return false;
        SpellContext c = context();
        return c == null || !c.isAlly(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity target = result.getEntity();
        Entity owner = getOwner();
        Vec3 at = result.getLocation();
        hit.add(target.getUUID());
        switch (mode()) {
            case MOB -> {
                target.invulnerableTime = 0;
                target.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), damage);
                discard();
            }
            case INFINITE -> {
                SpellContext c = context();
                if (c != null && target instanceof LivingEntity le && le.isAlive()) {
                    c.impact(at, getDeltaMovement(), target);
                    InfiniteSpinEntity.attach(c, le, infiniteTicks);
                }
                SbFxNet.at(sl, SbFxNet.NAIL_HIT, at, getDeltaMovement(), 4F, SbFx.GOLD, -1);
                SbFx.sound(sl, at, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0F, 1.4F);
                discard();
            }
            case SPELL -> {
                SpellContext c = context();
                target.invulnerableTime = 0;
                target.hurt(damageSources().thrown(this, owner), 1.0F);
                if (owner instanceof ServerPlayer sp && target instanceof LivingEntity) MagicApi.addCounter(sp, SbProgress.NAIL_HITS, 1);
                if (target instanceof LivingEntity le && act >= 2) SbSpin.apply(le, owner instanceof LivingEntity o ? o : null, 0, 40);
                if (c != null) {
                    c.impact(at, getDeltaMovement(), target);
                    SpellScheduler.schedule(1, () -> {
                        if (target.isAlive() && target.level() == sl) {
                            target.invulnerableTime = 0;
                            c.resolveOnEntity(target, this);
                        }
                    });
                    if (act >= 3 && owner instanceof ServerPlayer sp && sp.isShiftKeyDown() && target instanceof LivingEntity le
                            && WORMHOLED.add(c)) {
                        wormhole(sl, sp, le);
                    }
                }
                SbFxNet.at(sl, SbFxNet.NAIL_HIT, at, getDeltaMovement(), Math.min(3, act), golden() ? SbFx.GOLD : SbFx.NAIL, -1);
                if (pierce > 0) {
                    pierce--;
                } else {
                    discard();
                }
            }
        }
    }

    /** Ato 3: o atirador entra no buraco da unha e sai às costas do alvo. */
    private static void wormhole(ServerLevel sl, ServerPlayer sp, LivingEntity target) {
        Vec3 back = target.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-3) back = sp.position().subtract(target.position()).multiply(1, 0, 1).scale(-1);
        back = back.normalize();
        Vec3 dest = target.position().subtract(back.scale(target.getBbWidth() + 1.2));
        if (!sl.noCollision(sp, sp.getDimensions(sp.getPose()).makeBoundingBox(dest))) return;
        Vec3 from = sp.position();
        SbFxNet.at(sl, SbFxNet.WORMHOLE, from, dest, 1F, SbFx.NAIL, sp.getId());
        sp.stopRiding();
        sp.teleportTo(sl, dest.x, dest.y, dest.z, (float) Math.toDegrees(Math.atan2(-back.x, back.z)), sp.getXRot());
        sl.sendParticles(SbFx.dust(SbFx.NAIL, 1.2F), dest.x, dest.y + 1, dest.z, 16, 0.3, 0.6, 0.3, 0);
        SbFx.sound(sl, dest, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.6F);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 at = result.getLocation();
        SpellContext c = context();
        if (c != null && mode() == Mode.SPELL) {
            c.impact(at, getDeltaMovement(), null);
            c.resolveOnBlock(result, this);
        } else if (c != null) {
            c.end(at, getDeltaMovement());
        }
        SbFxNet.at(sl, SbFxNet.NAIL_HIT, at, Vec3.atLowerCornerOf(result.getDirection().getNormal()), golden() ? 2F : 1F,
                golden() ? SbFx.GOLD : SbFx.NAIL, -1);
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
        super.addAdditionalSaveData(tag);
        tag.putByte("Mode", (byte) mode().ordinal());
        tag.putBoolean("Gold", golden());
        tag.putInt("Act", act);
        tag.putInt("Pierce", pierce);
        tag.putFloat("Damage", damage);
        tag.putInt("Infinite", infiniteTicks);
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_MODE, tag.getByte("Mode"));
        entityData.set(DATA_GOLD, tag.getBoolean("Gold"));
        act = Math.max(1, tag.getInt("Act"));
        pierce = tag.getInt("Pierce");
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 2F;
        infiniteTicks = tag.contains("Infinite") ? tag.getInt("Infinite") : 200;
        spellTag = tag.contains("Spell", Tag.TAG_COMPOUND) ? tag.getCompound("Spell") : null;
    }
}
