package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Estrela Cadente: nasce no alto e mergulha até o ponto marcado (o chão pisca antes, dá para fugir).
 * No impacto explode em luz: dano em área e empurrão, sem quebrar blocos. Desenhada como uma estrela do Nether
 * grande e brilhante com rastro de partículas.
 */
public class FallingStarEntity extends Entity implements ItemSupplier {
    private static final ItemStack STAR = new ItemStack(Items.NETHER_STAR);

    @Nullable
    private SpellContext ctx;
    private Vec3 targetPos = Vec3.ZERO;
    private float damage = 6F;
    private SpellStats hitStats = FbUtil.PLAIN;
    private float radius = 2.5F;
    private int age;

    public FallingStarEntity(EntityType<? extends FallingStarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Lança uma estrela do céu acima de {@code target}. */
    public static void launch(SpellContext ctx, Vec3 target, float damage, float radius, SpellStats stats) {
        ServerLevel sl = ctx.level();
        // Começa até 22 blocos acima, mas abaixo de um eventual teto (cavernas)
        Vec3 top = target.add((sl.random.nextDouble() - 0.5) * 6, 22, (sl.random.nextDouble() - 0.5) * 6);
        BlockHitResult ceiling = sl.clip(new ClipContext(target.add(0, 0.5, 0), top, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 start = ceiling.getType() == HitResult.Type.MISS ? top
                : ceiling.getLocation().add(target.add(0, 0.5, 0).subtract(ceiling.getLocation()).normalize().scale(0.8));
        FallingStarEntity star = new FallingStarEntity(FbRegistries.FALLING_STAR.get(), sl);
        star.ctx = ctx;
        star.targetPos = target;
        star.damage = damage;
        star.hitStats = FbUtil.hitStats(stats);
        star.radius = radius;
        star.setPos(start.x, start.y, start.z);
        Vec3 dir = target.subtract(start);
        double dist = Math.max(1.0, dir.length());
        // Primeiro devagar (telegrafado), depois acelera no tick
        star.setDeltaMovement(dir.scale(0.55 / dist));
        sl.addFreshEntity(star);
        // Aviso no chão
        FbFx.ring(sl, target.add(0, 0.15, 0), FbFx.dust(FbFx.ASTRAL_LIGHT, 1.2F), radius, 24, 0);
        FbFx.ring(sl, target.add(0, 0.15, 0), ParticleTypes.END_ROD, radius * 0.4, 8, 0.01);
        FbFx.sound(sl, start, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4F, 0.6F);
    }

    @Override
    public ItemStack getItem() {
        return STAR;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) {
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            trail();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        age++;
        // Acelera em direção ao alvo
        Vec3 to = targetPos.subtract(position());
        double dist = to.length();
        double speed = Math.min(1.8, v.length() + 0.08);
        v = dist > 1.0E-3 ? to.scale(speed / dist) : v;
        setDeltaMovement(v);
        hasImpulse = true;

        Vec3 from = position();
        Vec3 next = from.add(v);
        BlockHitResult block = sl.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
        // Acertou alguém no caminho?
        AABB sweep = new AABB(from, end).inflate(0.6);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !ally(e))) {
            if (e.getBoundingBox().inflate(0.4).clip(from, end).isPresent()) {
                impact(sl, e.position().add(0, e.getBbHeight() * 0.3, 0));
                return;
            }
        }
        if (block.getType() != HitResult.Type.MISS || age > 100) {
            impact(sl, end);
            return;
        }
        if (dist <= speed) {
            impact(sl, targetPos);
            return;
        }
        setPos(next.x, next.y, next.z);
        if (age % 3 == 0) sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.02);
    }

    private boolean ally(Entity e) {
        return ctx != null ? ctx.isAlly(e) : false;
    }

    private void trail() {
        Vec3 v = getDeltaMovement();
        for (int i = 0; i < 4; i++) {
            double f = i / 4.0;
            double x = getX() - v.x * f, y = getY() - v.y * f, z = getZ() - v.z * f;
            level().addParticle(FbFx.dust(i % 2 == 0 ? FbFx.ASTRAL_LIGHT : FbFx.ASTRAL, 1.6F), x, y + 0.3, z, 0, 0, 0);
        }
        level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(),
                (random.nextDouble() - 0.5) * 0.08, 0.04, (random.nextDouble() - 0.5) * 0.08);
        if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.FIREWORK, getX(), getY() + 0.3, getZ(), 0, 0, 0);
    }

    private void impact(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.5, at.z, 50, 0.3, 0.3, 0.3, 0.25);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 30, 0.2, 0.2, 0.2, 0.3);
        FbFx.shockwave(sl, at.add(0, 0.2, 0), FbFx.dust(FbFx.ASTRAL_LIGHT, 1.6F), 36, 0.6);
        FbFx.shockwave(sl, at.add(0, 0.25, 0), ParticleTypes.CLOUD, 20, 0.35);
        FbFx.sound(sl, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0F, 0.7F);
        FbFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.4F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !ally(e) && e.distanceToSqr(at) <= radius * radius)) {
            e.invulnerableTime = 0;
            if (ctx != null) ctx.damage(e, damage, hitStats, this);
            else e.hurt(damageSources().magic(), damage);
            Vec3 push = e.position().subtract(at);
            push = push.lengthSqr() < 1.0E-3 ? new Vec3(0, 1, 0) : push.normalize();
            FbUtil.addMotion(e, new Vec3(push.x * 0.6, 0.35, push.z * 0.6));
        }
        FbFx.burst(sl, at.add(0, 0.5, 0), Element.ASTRAL, 20, 0.6);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
