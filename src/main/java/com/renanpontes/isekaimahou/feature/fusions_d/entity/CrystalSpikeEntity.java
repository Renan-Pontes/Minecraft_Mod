package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Estilhaço do Lótus de Cristal: um espinho de cristal reto e rápido. A trajetória é calculada igual no servidor e
 * no cliente (velocidade enviada ao nascer), então dezenas deles quase não custam pacotes. Some ao bater.
 */
public class CrystalSpikeEntity extends FdSpellEntity {
    /** Teto global de estilhaços vivos. */
    private static final int MAX_ALIVE = 220;
    private static int alive;
    private boolean counted;
    private float damage = 3F;

    public CrystalSpikeEntity(EntityType<? extends CrystalSpikeEntity> type, Level level) {
        super(type, level);
    }

    public static void fire(@Nullable SpellContext ctx, ServerLevel sl, Vec3 from, Vec3 velocity, float damage, SpellStats stats) {
        if (alive >= MAX_ALIVE) return;
        CrystalSpikeEntity s = new CrystalSpikeEntity(FdRegistries.CRYSTAL_SPIKE.get(), sl);
        s.setPos(from.x, from.y, from.z);
        s.setDeltaMovement(velocity);
        s.setup(ctx, 22, stats);
        s.damage = damage;
        sl.addFreshEntity(s);
    }

    public static void resetCount() {
        alive = 0;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && !counted) {
            counted = true;
            alive++;
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide && counted) {
            counted = false;
            alive = Math.max(0, alive - 1);
        }
    }

    @Override
    public void tick() {
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) setPos(position().add(v));
        super.tick();
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 from = position();
        Vec3 v = getDeltaMovement();
        Vec3 to = from.add(v);
        BlockHitResult block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        Predicate<Entity> filter = e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && !ally(le);
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(sl, this, from, end, getBoundingBox().expandTowards(v).inflate(0.4), filter);
        if (ent != null && ent.getEntity() instanceof LivingEntity le) {
            hit(le, damage, null);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                    le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 8, 0.2, 0.2, 0.2, 0.15);
            FdFx.sound(sl, ent.getLocation(), SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 1.4F);
            discard();
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            sl.sendParticles(FdFx.dust(FdFx.CRYSTAL_LIGHT, 0.8F), end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0);
            if (random.nextInt(3) == 0) FdFx.sound(sl, end, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.35F, 1.6F);
            discard();
            return;
        }
        setPos(to);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {}

    @Override
    protected void clientTick() {
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.WAX_ON, getX(), getY(), getZ(), 0, 0, 0);
    }
}
