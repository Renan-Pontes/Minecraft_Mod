package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Prisão de Cristal: segue o alvo cristalizado e é desenhada como um bloco de vidro magenta com aglomerados
 * de ametista. Quando o tempo acaba (ou o efeito sai), o cristal estilhaça e fere o alvo.
 */
public class CrystalPrisonEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(CrystalPrisonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(CrystalPrisonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(CrystalPrisonEntity.class, EntityDataSerializers.FLOAT);

    /** Dano do estilhaço final (já com o poder). */
    private float shatterDamage;
    private boolean shattered;

    public CrystalPrisonEntity(EntityType<? extends CrystalPrisonEntity> type, Level level) {
        super(type, level);
    }

    /** Envolve o alvo (ou renova a prisão que ele já tem). */
    public static void encase(SpellContext ctx, LivingEntity target, int ticks, float shatterDamage, SpellStats stats) {
        ServerLevel sl = ctx.level();
        for (CrystalPrisonEntity p : sl.getEntities(FbRegistries.CRYSTAL_PRISON.get(), p -> p.isAlive() && p.targetId() == target.getId())) {
            p.extendLife(ticks);
            p.ctx = ctx;
            p.setHitStats(stats);
            p.shatterDamage = Math.max(p.shatterDamage, shatterDamage);
            return;
        }
        CrystalPrisonEntity p = new CrystalPrisonEntity(FbRegistries.CRYSTAL_PRISON.get(), sl);
        p.setPos(target.getX(), target.getY(), target.getZ());
        p.setup(ctx, ticks);
        p.setHitStats(stats);
        p.shatterDamage = shatterDamage;
        p.entityData.set(TARGET, target.getId());
        p.entityData.set(WIDTH, target.getBbWidth());
        p.entityData.set(HEIGHT, target.getBbHeight());
        sl.addFreshEntity(p);
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                c.x, c.y, c.z, 30, target.getBbWidth() * 0.6, target.getBbHeight() * 0.5, target.getBbWidth() * 0.6, 0.1);
        FbFx.ring(sl, target.position().add(0, 0.1, 0), Vfx.waxOn(), target.getBbWidth() + 0.6, 16, 0.02);
        FbFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2F, 0.8F);
        FbFx.sound(sl, c, SoundEvents.GLASS_PLACE, 0.8F, 1.2F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
        builder.define(WIDTH, 0.6F);
        builder.define(HEIGHT, 1.8F);
    }

    public int targetId() { return entityData.get(TARGET); }
    public float prisonWidth() { return entityData.get(WIDTH); }
    public float prisonHeight() { return entityData.get(HEIGHT); }

    @Nullable
    public Entity target() {
        return level().getEntity(targetId());
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        Entity t = target();
        if (!(t instanceof LivingEntity le) || !le.isAlive() || !le.hasEffect(FbRegistries.CRYSTALLIZED)) {
            end();
            return;
        }
        // Acompanha o alvo (a gravidade ainda age: se estava no ar, o cristal cai junto)
        setPos(le.getX(), le.getY(), le.getZ());
        if (tickCount % 15 == 0) {
            sl.sendParticles(Vfx.waxOn(), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 2,
                    le.getBbWidth() * 0.5, le.getBbHeight() * 0.5, le.getBbWidth() * 0.5, 0.02);
        }
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        if (shattered) return;
        shattered = true;
        Entity t = target();
        Vec3 c = position().add(0, prisonHeight() * 0.5, 0);
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.AMETHYST_SHARD)), c.x, c.y, c.z, 24,
                prisonWidth() * 0.5, prisonHeight() * 0.4, prisonWidth() * 0.5, 0.15);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGENTA_STAINED_GLASS.defaultBlockState()),
                c.x, c.y, c.z, 30, prisonWidth() * 0.5, prisonHeight() * 0.4, prisonWidth() * 0.5, 0.1);
        FbFx.burst(sl, c, com.renanpontes.isekaimahou.core.Element.CRYSTAL, 12, 0.5);
        FbFx.sound(sl, c, SoundEvents.GLASS_BREAK, 1.0F, 0.9F);
        FbFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.8F);
        if (t instanceof LivingEntity le && le.isAlive()) {
            le.removeEffect(FbRegistries.CRYSTALLIZED);
            if (shatterDamage > 0) hit(le, shatterDamage);
        }
    }

    @Override
    protected void clientTick() {
        Entity t = target();
        if (t != null) setPos(t.getX(), t.getY(), t.getZ());
        if (random.nextInt(6) == 0) {
            level().addParticle(Vfx.waxOn(), getX() + (random.nextDouble() - 0.5) * prisonWidth() * 1.4,
                    getY() + random.nextDouble() * prisonHeight(), getZ() + (random.nextDouble() - 0.5) * prisonWidth() * 1.4, 0, 0.02, 0);
        }
    }
}
