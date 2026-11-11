package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * Tornado do Zéfiro: persegue o inimigo mais próximo pelo chão, suga e gira quem encosta e depois arremessa
 * para longe. Modelo GeckoLib translúcido girando + poeira levantada.
 */
public class ZephyrTornado extends Entity implements GeoEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.zephyr_tornado.spin");
    private static final double CATCH_RADIUS = 2.3;
    private static final int FLING_AFTER = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ownerId;
    private int life = 200;
    private float speed = 0.16F;
    private float damage = 3F;
    private double wanderAngle;
    /** Passo do último tick: quem está preso anda junto com o tornado. */
    private Vec3 step = Vec3.ZERO;
    /** Quanto tempo cada entidade está presa / até quando está livre de ser pega de novo. */
    private final Map<UUID, Integer> caught = new HashMap<>();
    private final Map<UUID, Integer> released = new HashMap<>();

    public ZephyrTornado(EntityType<? extends ZephyrTornado> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static ZephyrTornado spawn(LivingEntity owner, Vec3 pos, int life, float speed, float damage) {
        ZephyrTornado t = new ZephyrTornado(BossesARegistry.TORNADO.get(), owner.level());
        t.ownerId = owner.getUUID();
        t.life = life;
        t.speed = speed;
        t.damage = damage;
        t.wanderAngle = owner.getRandom().nextDouble() * Math.PI * 2;
        t.setPos(pos.x, pos.y, pos.z);
        owner.level().addFreshEntity(t);
        return t;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity ownerEntity = ownerId != null ? sl.getEntity(ownerId) : null;
        // Some com o Zéfiro (inclusive durante a animação de morte)
        if (tickCount > life || !(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.5, getZ(), 30, 1.0, 1.2, 1.0, 0.08);
            releaseAll(sl);
            discard();
            return;
        }
        move(sl, owner);
        grab(sl, owner);
        if (tickCount % 4 == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BossSpells.groundState(sl, position())),
                    getX(), getY() + 0.2, getZ(), 4, 1.0, 0.1, 1.0, 0.1);
        }
        if (tickCount % 20 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 1.4F, 0.6F);
        if (tickCount % 15 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.25F, 1.6F);
    }

    private void move(ServerLevel sl, LivingEntity owner) {
        LivingEntity target = owner instanceof Mob m && m.getTarget() != null && m.getTarget().distanceToSqr(this) < 30 * 30
                ? m.getTarget() : null;
        if (target == null) {
            double best = 30 * 30;
            for (LivingEntity e : BossSpells.foesAround(owner, position(), 30)) {
                double d = e.distanceToSqr(this);
                if (d < best) { best = d; target = e; }
            }
        }
        Vec3 dir;
        if (target != null) {
            dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        } else {
            wanderAngle += (random.nextDouble() - 0.5) * 0.3;
            dir = new Vec3(Math.cos(wanderAngle), 0, Math.sin(wanderAngle));
        }
        if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize().scale(speed);
        step = dir;
        double nx = getX() + dir.x, nz = getZ() + dir.z;
        double ny = BossSpells.groundY(sl, nx, getY() + 0.5, nz, 2, 6);
        setPos(nx, ny, nz);
    }

    private void grab(ServerLevel sl, LivingEntity owner) {
        Vec3 c = position();
        AABB box = new AABB(c.x - CATCH_RADIUS, c.y - 0.5, c.z - CATCH_RADIUS, c.x + CATCH_RADIUS, c.y + 5.0, c.z + CATCH_RADIUS);
        released.entrySet().removeIf(en -> en.getValue() < tickCount);
        Map<UUID, Integer> seen = new HashMap<>();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> BossSpells.isFoe(owner, e))) {
            if (released.containsKey(e.getUUID())) continue;
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > CATCH_RADIUS + e.getBbWidth() * 0.5) continue;
            int t = caught.getOrDefault(e.getUUID(), 0) + 1;
            seen.put(e.getUUID(), t);
            if (t == 1) sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 1.0F, 0.8F);
            if (t >= FLING_AFTER) {
                Vec3 out = d < 0.1 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
                e.setDeltaMovement(out.x * 1.3, 0.9, out.z * 1.3);
                e.hurtMarked = true;
                BossSpells.hit(owner, e, Element.WIND, damage * 1.5F);
                released.put(e.getUUID(), tickCount + 30);
                seen.remove(e.getUUID());
                sl.sendParticles(ParticleTypes.GUST, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                continue;
            }
            // Gira em volta do centro, puxando para dentro e para cima
            Vec3 tangent = d < 0.1 ? new Vec3(0, 0, 1) : new Vec3(-dz / d, 0, dx / d);
            Vec3 inward = d < 0.1 ? Vec3.ZERO : new Vec3(-dx / d, 0, -dz / d);
            double vy = Math.min(0.32, Math.max(e.getDeltaMovement().y, 0) + 0.1);
            if (e.getY() - c.y > 4.0) vy = 0.02;
            Vec3 v = tangent.scale(0.42).add(inward.scale(0.14));
            e.setDeltaMovement(v.x + step.x, vy, v.z + step.z);
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (t % 10 == 0) BossSpells.hit(owner, e, Element.WIND, damage);
        }
        caught.clear();
        caught.putAll(seen);
    }

    private void releaseAll(ServerLevel sl) {
        for (Iterator<UUID> it = caught.keySet().iterator(); it.hasNext(); ) {
            Entity e = sl.getEntity(it.next());
            if (e != null) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0));
                e.hurtMarked = true;
            }
            it.remove();
        }
    }

    private void clientFx() {
        double t = tickCount * 0.5;
        for (int i = 0; i < 3; i++) {
            double h = random.nextDouble() * 3.4;
            double r = 0.4 + h * 0.45;
            double a = t + random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            double vx = -Math.sin(a) * 0.25, vz = Math.cos(a) * 0.25;
            level().addParticle(ParticleTypes.CLOUD, x, getY() + h, z, vx, 0.05, vz);
            if (random.nextInt(2) == 0) {
                level().addParticle(ElementFx.dust(Element.WIND, 1.2F), x, getY() + h, z, vx, 0.05, vz);
            }
        }
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SMALL_GUST, getX(), getY() + 0.3, getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
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
}
