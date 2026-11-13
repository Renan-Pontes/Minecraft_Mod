package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Esmagamento Planetário (homenagem): um núcleo de gravidade sobe do chão, arranca o terreno em volta e puxa os
 * inimigos para si; os blocos arrancados formam uma ESFERA DE ROCHA em volta deles, que gira, se fecha esmagando
 * e então desaba. Os destroços caem e se desfazem — nada fica no mundo além da cratera de onde o terreno saiu.
 */
public class PlanetCoreEntity extends FdSpellEntity implements RubbleEntity.Anchor {
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(PlanetCoreEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RELEASED = SynchedEntityData.defineId(PlanetCoreEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SHELL = SynchedEntityData.defineId(PlanetCoreEntity.class, EntityDataSerializers.FLOAT);

    private static final int RISE = 22, GATHER_END = 50, COLLAPSE = 12;
    private static final int SLOTS = 84;

    private Vec3 ground = Vec3.ZERO;
    private float pullRadius = 10F;
    private float damage = 2.5F;
    private int holdEnd = 110;
    private int nextSlot;
    private final Set<UUID> captured = new LinkedHashSet<>();

    public PlanetCoreEntity(EntityType<? extends PlanetCoreEntity> type, Level level) {
        super(type, level);
    }

    public static void cast(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.PLANET_CORE.get(), ctx.casterId(), 1);
        PlanetCoreEntity p = new PlanetCoreEntity(FdRegistries.PLANET_CORE.get(), sl);
        p.ground = FdUtil.ground(sl, at, 8);
        p.setPos(p.ground.x, p.ground.y + 0.5, p.ground.z);
        p.pullRadius = Math.min(20F, stats.radius(10F));
        p.damage = 2.5F * stats.power();
        p.holdEnd = GATHER_END + Math.min(160, stats.duration(60));
        p.setup(ctx, p.holdEnd + COLLAPSE + 12, stats);
        p.entityData.set(SHELL, Math.min(6F, 3.4F + 0.6F * stats.aoe));
        sl.addFreshEntity(p);
        Telegraph.show(sl, Telegraph.Shape.circle(p.ground, p.pullRadius).warmup(RISE).color(FdFx.GRAVITY).noFlash());
        FdFx.sound(sl, at, SoundEvents.WARDEN_EMERGE, 1.6F, 0.6F);
        FdFx.sound(sl, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4F, 0.5F);
        BossKit.shake(sl, p.ground, 28, 1.0F, RISE + 10);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SCALE, 1F);
        b.define(RELEASED, false);
        b.define(SHELL, 3.4F);
    }

    public float shellRadius() { return entityData.get(SHELL); }

    @Override
    public Vec3 rubbleCenter() { return position().add(0, 0.5, 0); }

    @Override
    public float shellScale() { return entityData.get(SCALE); }

    @Override
    public float shellAngle() { return tickCount * 0.035F; }

    @Override
    public boolean released() { return entityData.get(RELEASED); }

    /** Ponto da vaga i numa esfera (Fibonacci): os destroços se espalham por igual. */
    private static Vec3 slot(int i, float r) {
        double y = 1 - (i + 0.5) * 2.0 / SLOTS;
        double rad = Math.sqrt(1 - y * y);
        double th = i * Math.PI * (3 - Math.sqrt(5));
        return new Vec3(Math.cos(th) * rad * r, y * r, Math.sin(th) * rad * r);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int t = tickCount;
        if (t <= RISE) {
            float e = t / (float) RISE;
            setPos(ground.x, ground.y + 0.5 + 7.0 * (1 - (1 - e) * (1 - e)), ground.z);
        }
        if (t == RISE) {
            // O chão de onde o núcleo saiu vira cratera
            Destruction.crater(sl, ground, Math.min(10F, 3F + pullRadius * 0.25F), owner());
            FdFx.sound(sl, ground, SoundEvents.GENERIC_EXPLODE.value(), 2F, 0.6F);
        }
        Vec3 c = rubbleCenter();
        float shell = shellRadius();
        if (t < holdEnd) pullAndPin(sl, c, shell, t);
        if (t < GATHER_END) gatherRubble(sl, c, shell);
        if (t >= GATHER_END && t < holdEnd && t % 10 == 0) {
            for (LivingEntity le : capturedLiving(sl)) {
                hit(le, damage, null);
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), owner());
                if (le instanceof ServerPlayer) le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0), owner());
            }
            FdFx.sound(sl, c, SoundEvents.STONE_BREAK, 1.6F, 0.5F);
            FdFx.sphere(sl, c, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), shell, 20);
        }
        if (t >= holdEnd && t < holdEnd + COLLAPSE) {
            float e = (t - holdEnd + 1) / (float) COLLAPSE;
            entityData.set(SCALE, 1F - 0.62F * e * e);
            if (t == holdEnd) {
                FdFx.sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 2F, 0.5F);
                BossKit.shake(sl, c, 32, 1.4F, COLLAPSE + 6);
            }
        }
        if (t == holdEnd + COLLAPSE) collapse(sl, c, shell);
    }

    private void pullAndPin(ServerLevel sl, Vec3 c, float shell, int t) {
        float strength = t < RISE ? 0.08F : 0.2F;
        int idx = 0;
        for (LivingEntity le : foes(c, t < GATHER_END ? pullRadius + 2 : shell + 3)) {
            boolean boss = FdUtil.isBoss(le);
            Vec3 body = FdUtil.center(le);
            double d = body.distanceTo(c);
            if (!boss && (captured.contains(le.getUUID()) || d < shell - 0.6)) captured.add(le.getUUID());
            if (captured.contains(le.getUUID())) {
                // Preso no núcleo: cada um numa posição perto do centro
                double a = idx++ * 2.4;
                Vec3 spot = c.add(Math.cos(a) * 0.9, -le.getBbHeight() * 0.5 + Math.sin(a * 0.7) * 0.4, Math.sin(a) * 0.9);
                Vec3 v = spot.subtract(le.position()).scale(0.45);
                FdUtil.setMotion(le, v);
                le.resetFallDistance();
                if (le instanceof Mob m) m.getNavigation().stop();
                continue;
            }
            Vec3 to = c.subtract(body);
            if (d < 0.1) continue;
            double s = strength * (boss ? 0.25 : 1.0) * (0.5 + 0.8 * (1 - Math.min(1, d / pullRadius)));
            FdUtil.setMotion(le, le.getDeltaMovement().scale(0.85).add(to.scale(s / d)).add(0, 0.04, 0));
            le.resetFallDistance();
        }
    }

    private List<LivingEntity> capturedLiving(ServerLevel sl) {
        List<LivingEntity> out = new ArrayList<>();
        captured.removeIf(id -> !(sl.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        for (UUID id : captured) if (sl.getEntity(id) instanceof LivingEntity le) out.add(le);
        return out;
    }

    /** Arranca blocos do terreno em volta (de verdade, com a destruição ligada) e manda cada um para uma vaga da casca. */
    private void gatherRubble(ServerLevel sl, Vec3 c, float shell) {
        LivingEntity caster = owner();
        boolean destroy = Destruction.allowed(sl, caster);
        for (int i = 0; i < 2 && nextSlot < SLOTS; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 2 + random.nextDouble() * (pullRadius - 2);
            int x = Mth.floor(ground.x + Math.cos(a) * d), z = Mth.floor(ground.z + Math.sin(a) * d);
            BlockPos top = FdUtil.surface(sl, x, z, Mth.floor(ground.y + 5), Mth.floor(ground.y - 6));
            BlockState st = top != null ? sl.getBlockState(top) : Blocks.COBBLESTONE.defaultBlockState();
            Vec3 from = top != null ? Vec3.atCenterOf(top) : new Vec3(x + 0.5, ground.y, z + 0.5);
            boolean real = destroy && top != null && !Destruction.protectedAt(sl, top, caster);
            // Sem destruição a esfera nasce de cópias do terreno (nada some do chão)
            if (!RubbleEntity.shell(sl, st, from, this, slot(nextSlot, shell), 18 + random.nextInt(14))) break;
            nextSlot++;
            if (real) Destruction.sphere(sl, from, 0.5F, caster, Destruction.Mode.ERASE);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), from.x, from.y + 0.5, from.z, 6, 0.3, 0.2, 0.3, 0.1);
        }
        if (tickCount % 6 == 0) FdFx.sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.5F);
        if (tickCount % 4 == 0) FdFx.implode(sl, c, FdFx.dust(FdFx.GRAVITY, 2F), shell + 2, 16, 0.25);
    }

    private void collapse(ServerLevel sl, Vec3 c, float shell) {
        entityData.set(RELEASED, true);
        for (LivingEntity le : foes(c, shell * 0.9F + 1)) {
            hit(le, damage * 7F, null);
            Statuses.apply(le, Statuses.Kind.STUNNED, 1, 40, owner(), 1F);
            if (!FdUtil.isBoss(le)) FdUtil.setMotion(le, new Vec3(0, -0.4, 0));
        }
        captured.clear();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        FdFx.sphere(sl, c, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), shell * 0.5, 80);
        FdFx.shockwave(sl, c, FdFx.fade(FdFx.GRAVITY, 0x120A20, 3F), 40, 0.7);
        FdFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.4F);
        FdFx.sound(sl, c, SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
        FdFx.sound(sl, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2F, 0.6F);
        BossKit.shake(sl, c, 48, 2.6F, 30);
    }

    @Override
    protected void clientTick() {
        if (released()) return;
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 2 + random.nextDouble() * 4;
            level().addParticle(Vfx.reversePortal(), getX() + Math.cos(a) * d, getY() + 0.5 + (random.nextDouble() - 0.5) * 3,
                    getZ() + Math.sin(a) * d, -Math.cos(a) * 0.15, 0, -Math.sin(a) * 0.15);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        captured.clear();
        super.remove(reason);
    }
}
