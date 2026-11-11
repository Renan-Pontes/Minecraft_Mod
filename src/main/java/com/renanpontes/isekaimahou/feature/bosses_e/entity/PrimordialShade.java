package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Clone de Sombra do Dragão Primordial (fase 5): um dragão menor, escuro e translúcido que circula a arena e de vez
 * em quando MERGULHA numa linha telegrafada. Enquanto houver clones, o dragão recebe metade do dano. Não é uma
 * criatura (sem atributos/ovo): tem vida própria, morre com golpes/flechas/magias, some com o dono, em 35 s e nunca
 * é salvo no mundo.
 */
public class PrimordialShade extends Entity implements GeoEntity {
    private static final int LIFE = 700;
    private static final int DIVE_WARN = 20;
    private static final int DIVE_DASH = 14;
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.primordial_dragon.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ownerId;
    @Nullable private PrimordialDragon owner;
    private float health = 60F;
    private int life;
    private double orbit;
    private int nextDive;
    private int diveTick = -1;
    private boolean diveGo;
    private Vec3 diveFrom = Vec3.ZERO;
    private Vec3 diveTo = Vec3.ZERO;
    private final Set<UUID> diveHit = new HashSet<>();

    public PrimordialShade(EntityType<? extends PrimordialShade> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.nextDive = 50 + level.random.nextInt(60);
    }

    /** Prepara o clone do dono (vida já escalada pelo grupo). */
    public void setup(PrimordialDragon dragon, float hp, double startAngle) {
        this.owner = dragon;
        this.ownerId = dragon.getUUID();
        this.health = hp;
        this.orbit = startAngle;
    }

    public boolean ownedBy(PrimordialDragon d) {
        return ownerId != null && ownerId.equals(d.getUUID());
    }

    @Nullable
    private PrimordialDragon owner() {
        if (owner != null && !owner.isRemoved()) return owner;
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof PrimordialDragon d) owner = d;
        return owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 4, getY() + 1 + random.nextDouble() * 2,
                        getZ() + (random.nextDouble() - 0.5) * 4, 0, 0.02, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 6, getY() + 1.5,
                        getZ() + (random.nextDouble() - 0.5) * 6, 0, 0.02, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        PrimordialDragon d = owner();
        if (d == null || !d.isAlive() || ++life > LIFE) {
            vanish(sl);
            return;
        }
        LivingEntity target = d.getTarget();
        if (diveTick >= 0) {
            tickDive(sl, d);
            return;
        }
        Vec3 c = target != null ? target.position() : d.homeCenter();
        orbit += 0.025;
        // Baixo o bastante para a espada alcançar (corpo entre +2,6 e +4,6 do chão): derrubar clones não pode exigir arco.
        Vec3 want = c.add(Math.cos(orbit) * 9, 2.6 + Math.sin(orbit * 3) * 0.6, Math.sin(orbit) * 9);
        Vec3 v = want.subtract(position());
        Vec3 step = v.length() > 0.6 ? v.normalize().scale(0.6) : v;
        setDeltaMovement(step);
        move(MoverType.SELF, step);
        if (step.horizontalDistanceSqr() > 1.0E-4) setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90F);
        if (target != null && --nextDive <= 0) startDive(sl, d, target);
    }

    /** Mergulho: a linha no chão é marcada antes; só então o clone corre por ela (contrariável na Pilha). */
    private void startDive(ServerLevel sl, PrimordialDragon d, LivingEntity target) {
        Vec3 g = d.groundAt(getX(), target.getY() + 2, getZ(), 6, 12);
        Vec3 from = new Vec3(getX(), g != null ? g.y : target.getY(), getZ());
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-3) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();
        diveFrom = from;
        diveTo = new Vec3(target.getX(), target.getY(), target.getZ()).add(dir.scale(6));
        diveTick = 0;
        diveGo = false;
        diveHit.clear();
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F);
        Telegraph.delayed(sl, Telegraph.Shape.line(diveFrom, new Vec3(diveTo.x, diveFrom.y, diveTo.z), 3.0F).warmup(DIVE_WARN)
                .color(Telegraph.VOID).owner(this).noRunes(), hits -> diveGo = true);
        DragonFx.sound(sl, position(), SoundEvents.PHANTOM_SWOOP, 1.4F, 0.6F);
        triggerAnim("main", "dive");
    }

    private void tickDive(ServerLevel sl, PrimordialDragon d) {
        diveTick++;
        if (diveTick <= DIVE_WARN) {
            Vec3 hover = diveFrom.add(0, 4, 0);
            move(MoverType.SELF, hover.subtract(position()).scale(0.25));
            return;
        }
        if (!diveGo) {
            endDive();
            return;
        }
        int k = diveTick - DIVE_WARN;
        double f = Math.min(1.0, k / (double) DIVE_DASH);
        Vec3 p = diveFrom.lerp(diveTo, f).add(0, 1.2, 0);
        setPos(p.x, p.y, p.z);
        sl.sendParticles(DragonFx.dust(DragonFx.SHADOW, 2.4F), p.x, p.y + 1, p.z, 6, 1.2, 0.6, 1.2, 0);
        for (LivingEntity e : d.enemiesNear(p.add(0, 1, 0), 2.8)) {
            if (!diveHit.add(e.getUUID())) continue;
            if (e.hurt(d.spellSource(this), d.spellPower() * 0.55F)) {
                Statuses.apply(e, Statuses.Kind.CORRUPTED, 2, 100, d, 1F);
            }
        }
        if (k >= DIVE_DASH) endDive();
    }

    private void endDive() {
        diveTick = -1;
        nextDive = 90 + random.nextInt(60);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        Entity src = source.getEntity();
        PrimordialDragon d = owner();
        if (src == this || (d != null && (src == d || (src != null && d.isAlliedTo(src))))) return false;
        health -= amount;
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 10, 0.8, 0.6, 0.8, 0.05);
        DragonFx.sound(sl, position(), SoundEvents.PHANTOM_HURT, 1.2F, 0.5F);
        if (health <= 0) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 60, 1.6, 1.0, 1.6, 0.1);
            sl.sendParticles(DragonFx.dust(DragonFx.SHADOW, 3F), getX(), getY() + 1.5, getZ(), 40, 1.6, 1.0, 1.6, 0);
            DragonFx.sound(sl, position(), SoundEvents.PHANTOM_DEATH, 1.6F, 0.5F);
            discard();
        }
        return true;
    }

    private void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 40, 1.4, 0.8, 1.4, 0.06);
        discard();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6, 2, 6);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ---------------- GeckoLib ----------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PrimordialShade> main = new AnimationController<>(this, "main", 4, s -> s.setAndContinue(FLY));
        main.triggerableAnim("dive", RawAnimation.begin().thenPlay("animation.primordial_dragon.dive"));
        controllers.add(main);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
