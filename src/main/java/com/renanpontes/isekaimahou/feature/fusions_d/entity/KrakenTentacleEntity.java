package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Tentáculo do Kraken: rompe o chão (abre um buraco de verdade), balança e caça. Inimigo longe → ergue-se e DESABA
 * sobre ele numa linha telegrafada (racha o chão onde bate); inimigo perto → agarra, ergue no ar, aperta e o arremessa
 * contra o chão. Abismo: cada golpe deixa Molhado (combina com Raio/Gelo).
 */
public class KrakenTentacleEntity extends FdSpellEntity {
    public static final int EMERGE = 0, IDLE = 1, WINDUP = 2, SLAM = 3, GRAB = 4, RETRACT = 5;
    public static final int EMERGE_TICKS = 12, WINDUP_TICKS = 12, SLAM_TICKS = 10, GRAB_TICKS = 44, RETRACT_TICKS = 14;
    /** Comprimento do modelo (blocos) na escala 1: 10 segmentos ≈ 74,6 px. A linha do golpe acompanha o tamanho real. */
    private static final float MODEL_LENGTH = 4.66F;
    /** Quem já está preso por algum tentáculo (para dois não brigarem pelo mesmo). */
    private static final Set<UUID> GRABBED = new HashSet<>();

    private static final EntityDataAccessor<Float> AIM = SynchedEntityData.defineId(KrakenTentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(KrakenTentacleEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 8F;
    private int cooldown = 6;
    @Nullable
    private UUID held;
    private Vec3 slamDir = new Vec3(0, 0, 1);

    public KrakenTentacleEntity(EntityType<? extends KrakenTentacleEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, Vec3 ground, float yaw, SpellStats stats, int life) {
        ServerLevel sl = ctx.level();
        KrakenTentacleEntity t = new KrakenTentacleEntity(FdRegistries.KRAKEN_TENTACLE.get(), sl);
        t.setPos(ground.x, ground.y, ground.z);
        t.setup(ctx, life, stats);
        t.damage = 8F * stats.power();
        t.cooldown = 4 + sl.random.nextInt(10);
        t.entityData.set(AIM, yaw);
        t.entityData.set(SCALE, Math.min(2.6F, 1.8F + 0.2F * Math.max(0, stats.amplify)));
        sl.addFreshEntity(t);
        // Rompe o chão: buraco de verdade (se a destruição estiver ligada) + terra voando
        LivingEntity caster = ctx.caster();
        Destruction.sphere(sl, ground.add(0, -0.5, 0), 1.4F, caster, Destruction.Mode.BREAK);
        BlockState under = sl.getBlockState(BlockPos.containing(ground).below());
        if (under.isAir()) under = Blocks.DIRT.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), ground.x, ground.y + 0.3, ground.z, 40, 0.8, 0.4, 0.8, 0.3);
        sl.sendParticles(ParticleTypes.SPLASH, ground.x, ground.y + 0.5, ground.z, 30, 0.6, 0.6, 0.6, 0.2);
        FdFx.sound(sl, ground, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5F, 0.6F);
        FdFx.sound(sl, ground, SoundEvents.ROOTED_DIRT_BREAK, 1.6F, 0.5F);
    }

    public static void clearAll() {
        GRABBED.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(AIM, 0F);
        b.define(SCALE, 1.5F);
    }

    /** Direção para onde o tentáculo se inclina (radianos, convenção do renderer). */
    public float aim() { return entityData.get(AIM); }

    public float scale() { return entityData.get(SCALE); }

    /** Até onde a ponta chega quando o tentáculo desaba deitado (um pouco menos que o comprimento: a base é curva). */
    private float reach() {
        return MODEL_LENGTH * scale() * 0.92F;
    }

    private Vec3 aimVec() {
        float a = aim();
        return new Vec3(-Mth.sin(a), 0, -Mth.cos(a));
    }

    private void aimAt(Vec3 p) {
        Vec3 d = p.subtract(position());
        entityData.set(AIM, (float) Math.atan2(-d.x, -d.z));
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int s = state();
        if (s == EMERGE) {
            if (stateAge >= EMERGE_TICKS) setState(IDLE);
            return;
        }
        if (s != RETRACT && tickCount >= maxLife() - RETRACT_TICKS) {
            release();
            setState(RETRACT);
            FdFx.sound(sl, position(), SoundEvents.SQUID_AMBIENT, 1.2F, 0.4F);
            return;
        }
        switch (s) {
            case IDLE -> {
                if (--cooldown > 0) return;
                LivingEntity t = nearestFoe();
                if (t == null) { cooldown = 10; return; }
                aimAt(t.position());
                double d = Math.sqrt(t.distanceToSqr(getX(), t.getY(), getZ()));
                if (d <= 4.2 && !FdUtil.isBoss(t) && !GRABBED.contains(t.getUUID())) grab(sl, t);
                else windup(sl, t);
            }
            case SLAM -> { if (stateAge >= SLAM_TICKS) { setState(IDLE); cooldown = 14; } }
            case GRAB -> holdTick(sl);
            default -> {}
        }
    }

    @Nullable
    private LivingEntity nearestFoe() {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : foes(position().add(0, 1, 0), reach() + 1)) {
            double d = e.distanceToSqr(this);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void windup(ServerLevel sl, LivingEntity t) {
        setState(WINDUP);
        Vec3 base = position();
        Vec3 dir = t.position().subtract(base).multiply(1, 0, 1);
        slamDir = dir.lengthSqr() < 1.0E-3 ? aimVec() : dir.normalize();
        Vec3 end = base.add(slamDir.scale(reach()));
        FdFx.sound(sl, base, SoundEvents.SQUID_SQUIRT, 1.4F, 0.4F);
        Telegraph.delayedLine(sl, base.add(slamDir.scale(0.8)), end, 2.8F, WINDUP_TICKS, FdFx.ABYSS, hits -> {
            if (!isAlive() || state() != WINDUP) return;
            setState(SLAM);
            slam(sl, base, end, hits);
        });
    }

    private void slam(ServerLevel sl, Vec3 base, Vec3 end, java.util.List<LivingEntity> hits) {
        for (LivingEntity le : hits) {
            if (ally(le) || FdUtil.untouchable(le)) continue;
            hit(le, damage, null);
            if (!FdUtil.isBoss(le)) FdUtil.setMotion(le, le.getDeltaMovement().add(slamDir.x * 0.4, 0.55, slamDir.z * 0.4));
        }
        // O golpe racha o chão ao longo da linha
        Destruction.line(sl, base.add(slamDir.scale(1.5)), end, 2.0F, 1.6F, owner(), Destruction.Mode.CRUSH);
        for (int i = 2; i <= 8; i += 2) {
            Vec3 p = base.add(end.subtract(base).scale(i / 8.0));
            BlockState st = sl.getBlockState(BlockPos.containing(p).below());
            if (!st.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y + 0.2, p.z, 14, 0.6, 0.1, 0.6, 0.2);
            sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.4, p.z, 12, 0.6, 0.2, 0.6, 0.2);
        }
        FdFx.sound(sl, end, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2F, 0.5F);
        FdFx.sound(sl, end, SoundEvents.GENERIC_SPLASH, 1.6F, 0.5F);
        BossKit.shake(sl, end, 22, 1.2F, 12);
    }

    private void grab(ServerLevel sl, LivingEntity t) {
        setState(GRAB);
        held = t.getUUID();
        GRABBED.add(held);
        FdFx.sound(sl, t.position(), SoundEvents.SLIME_ATTACK, 1.4F, 0.4F);
        FdFx.sound(sl, t.position(), SoundEvents.SQUID_HURT, 1.4F, 0.5F);
    }

    /** Centro da volta que a ponta enrolada faz (pose GRAB do renderer): a vítima fica DENTRO do enrolamento. */
    private Vec3 holdPoint() {
        return position().add(aimVec().scale(1.05 * scale())).add(0, 1.85 * scale(), 0);
    }

    private void holdTick(ServerLevel sl) {
        LivingEntity t = held != null && sl.getEntity(held) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (t == null) {
            release();
            setState(IDLE);
            cooldown = 8;
            return;
        }
        Vec3 spot = holdPoint().subtract(0, t.getBbHeight() * 0.5, 0);
        FdUtil.setMotion(t, spot.subtract(t.position()).scale(0.4));
        t.resetFallDistance();
        if (t instanceof Mob m) m.getNavigation().stop();
        if (stateAge % 10 == 5) {
            hit(t, damage * 0.4F, null);
            sl.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            FdFx.sound(sl, t.position(), SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
        }
        if (stateAge >= GRAB_TICKS) {
            // Arremessa contra o chão
            hit(t, damage * 0.9F, null);
            FdUtil.setMotion(t, aimVec().scale(0.9).add(0, -1.6, 0));
            release();
            setState(IDLE);
            cooldown = 16;
            FdFx.sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.5F);
        }
    }

    private void release() {
        if (held != null) GRABBED.remove(held);
        held = null;
    }

    /** Qualquer saída do mundo (fim, limite, chunk descarregado, servidor parando) solta a vítima do registro. */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (held != null) GRABBED.remove(held);
        held = null;
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_WATER, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + 1 + random.nextDouble() * 4 * scale(),
                    getZ() + (random.nextDouble() - 0.5) * 1.5, 0, 0, 0);
        }
    }
}
