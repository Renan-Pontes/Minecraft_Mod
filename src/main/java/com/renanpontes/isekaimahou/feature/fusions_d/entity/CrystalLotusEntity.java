package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Lótus de Cristal: uma flor de cristal desabrocha no chão e gira, disparando estilhaços em espiral (2 braços + 1 por
 * Dividir). No fim, a flor explode numa coroa de estilhaços. Cristal: cada estilhaço acumula Petrificação.
 */
public class CrystalLotusEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Integer> ARMS = SynchedEntityData.defineId(CrystalLotusEntity.class, EntityDataSerializers.INT);
    private static final int BLOOM = 12;
    private float damage = 3F;
    private SpellStats stats = new SpellStats();

    public CrystalLotusEntity(EntityType<? extends CrystalLotusEntity> type, Level level) {
        super(type, level);
    }

    public static void bloom(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.CRYSTAL_LOTUS.get(), ctx.casterId(), 2);
        Vec3 g = FdUtil.ground(sl, at, 6);
        CrystalLotusEntity l = new CrystalLotusEntity(FdRegistries.CRYSTAL_LOTUS.get(), sl);
        l.setPos(g.x, g.y, g.z);
        l.setYRot(sl.random.nextFloat() * 360F);
        l.setup(ctx, stats.duration(110), stats);
        l.damage = 3.0F * stats.power();
        l.stats = stats;
        l.entityData.set(ARMS, Math.min(5, 2 + Math.max(0, stats.split)));
        sl.addFreshEntity(l);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), g.x, g.y + 0.2, g.z,
                30, 0.8, 0.1, 0.8, 0.15);
        FdFx.ring(sl, g.add(0, 0.1, 0), FdFx.fade(FdFx.CRYSTAL_LIGHT, FdFx.CRYSTAL, 1.4F), 1.8, 24, 0);
        FdFx.sound(sl, g, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.8F);
        FdFx.sound(sl, g, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.4F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ARMS, 2);
    }

    public int arms() { return entityData.get(ARMS); }

    /** Ângulo da espiral (radianos), igual no cliente para o visual acompanhar os disparos. */
    public float spiral(float pt) {
        return (tickCount + pt) * 0.32F + getYRot() * ((float) Math.PI / 180F);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (tickCount < BLOOM || tickCount > maxLife() - 6 || tickCount % 3 != 0) return;
        Vec3 c = position().add(0, 0.9, 0);
        int n = arms();
        float base = spiral(0F);
        for (int k = 0; k < n; k++) {
            double a = base + Math.PI * 2 * k / n;
            Vec3 dir = new Vec3(Math.cos(a), 0.08 + 0.06 * Math.sin(tickCount * 0.4), Math.sin(a)).normalize();
            CrystalSpikeEntity.fire(ctx, sl, c.add(dir.scale(0.6)), dir.scale(0.95 * stats.speedMult()), damage, stats);
        }
        if (tickCount % 6 == 0) FdFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F + random.nextFloat() * 0.6F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position().add(0, 0.9, 0);
        for (int k = 0; k < 18; k++) {
            double a = Math.PI * 2 * k / 18;
            Vec3 dir = new Vec3(Math.cos(a), k % 2 == 0 ? 0.05 : 0.35, Math.sin(a)).normalize();
            CrystalSpikeEntity.fire(ctx, sl, c, dir.scale(1.1), damage * 1.3F, stats);
        }
        for (LivingEntity le : foes(c, 2.6)) hit(le, damage * 2F, null);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), c.x, c.y, c.z,
                50, 0.6, 0.4, 0.6, 0.3);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        FdFx.shockwave(sl, c, FdFx.fade(FdFx.CRYSTAL_LIGHT, FdFx.CRYSTAL, 1.6F), 32, 0.5);
        FdFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0F, 0.6F);
        FdFx.sound(sl, c, SoundEvents.GLASS_BREAK, 1.6F, 0.7F);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 0.4 + random.nextDouble() * 1.4;
            level().addParticle(Vfx.endRod(), getX() + Math.cos(a) * r, getY() + 0.3 + random.nextDouble(), getZ() + Math.sin(a) * r,
                    0, 0.02, 0);
        }
    }
}
