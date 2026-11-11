package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rocha arremessada pelo Golem Primordial em arco (mira onde o alvo vai estar). Na fase 2+ se parte em
 * fragmentos ao cair. Não quebra blocos.
 */
public class GolemBoulder extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(GolemBoulder.class, EntityDataSerializers.FLOAT);
    private static final double GRAVITY = 0.05;

    private float damage = 14F;
    private boolean fragments;
    private int flightTicks;

    public GolemBoulder(EntityType<? extends GolemBoulder> type, Level level) {
        super(type, level);
    }

    /** Arremessa em arco balístico de {@code from} até {@code target}. */
    public static GolemBoulder throwAt(LivingEntity owner, Vec3 from, Vec3 target, float size, float damage, boolean fragments) {
        GolemBoulder b = create(owner, from, size, damage, fragments);
        Vec3 d = target.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double t = Mth.clamp(horiz / 1.1, 12, 34);
        double drag = 1.0 + 0.006 * t; // compensa o atrito do ar (0.99 por tick)
        b.setDeltaMovement(d.x / t * drag, (d.y + 0.5 * GRAVITY * t * t) / t, d.z / t * drag);
        b.flightTicks = (int) Math.ceil(t);
        owner.level().addFreshEntity(b);
        return b;
    }

    /** Ticks previstos até a queda (para telegrafar o ponto de impacto). */
    public int flightTicks() { return flightTicks; }

    /** Raio do impacto de uma rocha de tamanho {@code size} (o mesmo da telegrafia). */
    public static float impactRadius(float size) { return 2.6F * size; }

    private static GolemBoulder create(LivingEntity owner, Vec3 from, float size, float damage, boolean fragments) {
        GolemBoulder b = new GolemBoulder(BossesARegistry.BOULDER.get(), owner.level());
        b.setOwner(owner);
        b.setPos(from.x, from.y, from.z);
        b.entityData.set(DATA_SIZE, size);
        b.damage = damage;
        b.fragments = fragments;
        return b;
    }

    public float size() { return entityData.get(DATA_SIZE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
                        getX() + (random.nextDouble() - 0.5) * size(), getY() + 0.4, getZ() + (random.nextDouble() - 0.5) * size(), 0, 0, 0);
            }
            return;
        }
        if (tickCount > 160) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = getOwner();
        return super.canHitEntity(e) && (owner == null || BossSpells.isFoe(owner, e));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        impact(sl, result.getLocation());
        discard();
    }

    private void impact(ServerLevel sl, Vec3 p) {
        float s = size();
        BlockState rock = s >= 1F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), p.x, p.y + 0.4, p.z, (int) (50 * s), 0.6 * s, 0.4, 0.6 * s, 0.2);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BossSpells.groundState(sl, p)), p.x, p.y + 0.2, p.z,
                (int) (30 * s), 1.0 * s, 0.2, 1.0 * s, 0.25);
        sl.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.3, p.z, (int) (10 * s), 0.8 * s, 0.2, 0.8 * s, 0.02);
        if (s >= 1F) sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F * s, 0.55F);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);

        Entity owner = getOwner();
        LivingEntity caster = owner instanceof LivingEntity le ? le : null;
        double r = impactRadius(s);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1),
                e -> owner == null ? !(e instanceof ElementalGuardian) : BossSpells.isFoe(owner, e))) {
            double d = e.position().distanceTo(p);
            if (d > r + e.getBbWidth() * 0.5) continue;
            float dmg = (float) (damage * Math.max(0.5, 1.0 - d / (r + 1.0)));
            if (caster != null) BossSpells.hit(caster, e, Element.EARTH, dmg);
            else e.hurt(damageSources().fallingBlock(this), dmg);
            BossSpells.knock(e, e.position().subtract(p), 0.8 * s, 0.4 * s);
        }

        if (fragments && caster != null) {
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 0.5 * i + random.nextDouble() * 0.6;
                Vec3 start = p.add(0, 0.8, 0);
                Vec3 v = new Vec3(Math.cos(a) * 0.32, 0.5 + random.nextDouble() * 0.15, Math.sin(a) * 0.32);
                GolemBoulder f = create(caster, start, 0.5F, damage * 0.4F, false);
                f.setDeltaMovement(v);
                sl.addFreshEntity(f);
                // Cada fragmento marca onde vai cair (mesma física do voo): nada de dano sem aviso
                int[] ticks = new int[1];
                Vec3 land = predictLanding(sl, start, v, ticks);
                if (land != null) {
                    Telegraph.show(sl, Telegraph.Shape.circle(land, impactRadius(0.5F)).warmup(ticks[0])
                            .color(Telegraph.color(Element.EARTH)).owner(caster).noRunes());
                }
            }
        }
    }

    /**
     * Onde (e em quantos ticks) um fragmento lançado de {@code from} com velocidade {@code v} toca o chão, com a
     * física do {@link ThrowableProjectile} (testa o trecho do tick, depois ×0.99 e gravidade). Null se não achar.
     */
    @Nullable
    private static Vec3 predictLanding(ServerLevel sl, Vec3 from, Vec3 v, int[] ticksOut) {
        Vec3 pos = from, vel = v;
        for (int k = 1; k <= 60; k++) {
            Vec3 next = pos.add(vel);
            BlockPos bp = BlockPos.containing(next);
            BlockState st = sl.getBlockState(bp);
            boolean solid = !st.getCollisionShape(sl, bp).isEmpty() || !st.getFluidState().isEmpty();
            if (solid && vel.y <= 0) {
                ticksOut[0] = k;
                return BossSpells.ground(sl, next.add(0, 0.5, 0));
            }
            pos = next;
            vel = vel.scale(0.99).add(0, -GRAVITY, 0);
        }
        return null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
