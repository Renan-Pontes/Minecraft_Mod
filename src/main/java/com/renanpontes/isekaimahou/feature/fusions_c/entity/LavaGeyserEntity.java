package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gêiser de Lava: o chão racha e borbulha por {@link #WARMUP} ticks; depois uma coluna de lava explode para cima,
 * arremessando e queimando quem está em cima, e continua jorrando por 2 s (quem fica dentro é jogado de novo para o alto
 * e acumula Em Chamas). A superfície em volta fica queimada (Destruction SCORCH, respeita a regra de destruição).
 * {@code scale()} = raio da coluna.
 */
public class LavaGeyserEntity extends FcSpellEntity {
    public static final int WARMUP = 14;
    public static final int ACTIVE = 44;
    private static final int MAX_PER_OWNER = 6;

    private float blastDamage = 8F;

    public LavaGeyserEntity(EntityType<? extends LavaGeyserEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 ground, float radius, float blastDamage) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<? extends LavaGeyserEntity> mine = sl.getEntities(FcRegistries.LAVA_GEYSER.get(), g -> g.isAlive() && owner.equals(g.ownerId()));
            if (mine.size() >= MAX_PER_OWNER) mine.get(0).discard();
        }
        LavaGeyserEntity g = new LavaGeyserEntity(FcRegistries.LAVA_GEYSER.get(), sl);
        g.setPos(ground.x, ground.y, ground.z);
        g.setup(ctx, stats, Element.MAGMA, WARMUP + ACTIVE + stats.duration(20) - 20, radius);
        g.blastDamage = blastDamage;
        sl.addFreshEntity(g);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MAGMA_BLOCK.defaultBlockState()),
                ground.x, ground.y + 0.1, ground.z, 16, radius * 0.6, 0.05, radius * 0.6, 0.15);
        FcFx.sound(sl, ground, SoundEvents.LAVA_AMBIENT, 1.4F, 0.6F);
    }

    /** Altura atual da coluna (0 no aquecimento). */
    public float columnHeight(float pt) {
        float a = age() + pt - WARMUP;
        if (a <= 0) return 0F;
        float rise = Math.min(1F, a / 4F);
        float fall = Math.min(1F, Math.max(0F, (lifeLeft() - pt) / 10F));
        return (5F + 2.5F * scale()) * rise * fall;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int age = age();
        float r = scale();
        Vec3 base = position();
        if (age < WARMUP) {
            if (age % 3 == 0) {
                sl.sendParticles(ParticleTypes.LAVA, base.x, base.y + 0.1, base.z, 2, r * 0.5, 0.0, r * 0.5, 0.0);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + 0.2, base.z, 2, r * 0.4, 0.05, r * 0.4, 0.01);
            }
            if (age == WARMUP - 4) FcFx.sound(sl, base, SoundEvents.LAVA_POP, 1.5F, 0.5F);
            return;
        }
        if (age == WARMUP) erupt(sl, base, r);
        float h = columnHeight(0);
        // Quem fica na coluna é jogado de novo e queima mais (máx. Em Chamas 4)
        if (age % 5 == 0 && h > 1F) {
            for (LivingEntity le : enemiesIn(base.add(0, h * 0.5, 0), Math.max(r + 0.6, h * 0.5))) {
                if (Mth.square(le.getX() - base.x) + Mth.square(le.getZ() - base.z) > (r + 0.6) * (r + 0.6)) continue;
                if (le.getY() > base.y + h) continue;
                hit(le, 2.5F);
                Statuses.applyUpTo(le, Statuses.Kind.BURNING, 1, 100, owner(), power, 4);
                Vec3 v = le.getDeltaMovement();
                FcUtil.knock(le, new Vec3(-v.x * 0.5, Math.max(0, 0.55 - v.y), -v.z * 0.5));
                le.resetFallDistance();
            }
        }
        if (age % 2 == 0 && h > 1F) {
            Vec3 top = base.add(0, h, 0);
            sl.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 2, r * 0.4, 0.2, r * 0.4, 0.0);
            sl.sendParticles(ParticleTypes.FALLING_LAVA, top.x, top.y, top.z, 3, r + 0.8, 0.3, r + 0.8, 0.0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 0.3, top.z, 1, r * 0.4, 0.2, r * 0.4, 0.03);
        }
        if (age % 12 == 0) FcFx.sound(sl, base, SoundEvents.LAVA_AMBIENT, 1.2F, 0.7F);
    }

    private void erupt(ServerLevel sl, Vec3 base, float r) {
        for (LivingEntity le : enemiesIn(base.add(0, 1, 0), r + 1.2)) {
            hit(le, blastDamage);
            Statuses.applyUpTo(le, Statuses.Kind.BURNING, 1, 120, owner(), power, 2);
            FcUtil.knock(le, new Vec3(0, 1.25, 0));
            le.resetFallDistance();
        }
        Destruction.surface(sl, base, r + 1.0F, owner(), Destruction.Mode.SCORCH);
        sl.sendParticles(ParticleTypes.LAVA, base.x, base.y + 0.5, base.z, 30, r * 0.5, 0.4, r * 0.5, 0.2);
        sl.sendParticles(ParticleTypes.FLAME, base.x, base.y + 1, base.z, 40, r * 0.4, 1.5, r * 0.4, 0.15);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(BlockPos.containing(base).below())),
                base.x, base.y + 0.2, base.z, 30, r, 0.2, r, 0.3);
        FcFx.shockwave(sl, base.add(0, 0.2, 0), FcFx.fade(FcFx.MAGMA_HOT, FcFx.MAGMA_DARK, 1.8F), 28, 0.35);
        FcFx.sound(sl, base, SoundEvents.BUCKET_EMPTY_LAVA, 2.0F, 0.5F);
        FcFx.sound(sl, base, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
        BossKit.shake(sl, base, 14, 0.7F, 10);
    }

    @Override
    protected void clientTick() {
        // Rachaduras brilhando no aquecimento; respingos e brasas com a coluna ativa
        float r = scale();
        if (age() < WARMUP) {
            if (random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * r;
                level().addParticle(ParticleTypes.SMOKE, getX() + Math.cos(a) * d, getY() + 0.05, getZ() + Math.sin(a) * d, 0, 0.05, 0);
            }
        } else {
            float h = columnHeight(0);
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ParticleTypes.FLAME, getX() + Math.cos(a) * r, getY() + random.nextDouble() * h, getZ() + Math.sin(a) * r,
                        Math.cos(a) * 0.05, 0.1, Math.sin(a) * 0.05);
            }
        }
    }
}
