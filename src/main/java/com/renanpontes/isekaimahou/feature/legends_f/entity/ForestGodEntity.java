package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Deus da Floresta (homenagem ao Shishigami de Princesa Mononoke): um cervo divino gigantesco e translúcido, com
 * galhadas de árvore e rosto quase humano, surge e caminha devagar. Ele DÁ e TIRA a vida: a cada passo flores brotam
 * na grama; a cada segundo aliados na área são curados e limpos de venenos, e os inimigos têm a vida arrancada. Com
 * Inverter ele só tira — mais forte, e as plantas murcham em vez de florir.
 */
public class ForestGodEntity extends LfSpellEntity {
    public static final float HEAL = 5F;
    public static final float DAMAGE = 7F;
    public static final int APPEAR = 24;
    public static final int VANISH = 24;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(ForestGodEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DEATH = SynchedEntityData.defineId(ForestGodEntity.class, EntityDataSerializers.BOOLEAN);

    private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.BLUE_ORCHID};

    public ForestGodEntity(EntityType<? extends ForestGodEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, Vec3 at, float yaw, float radius, int life, SpellStats stats, boolean death) {
        ForestGodEntity g = LfRegistry.FOREST_GOD.get().create(ctx.level());
        if (g == null) return;
        g.bind(ctx, stats, death ? 0x5A6B4A : 0xD8F0E0, life);
        g.entityData.set(DATA_RADIUS, radius);
        g.entityData.set(DATA_DEATH, death);
        g.moveTo(at.x, at.y, at.z, yaw, 0);
        ctx.level().addFreshEntity(g);
        LfUtil.sound(ctx.level(), at, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.5F);
        LfUtil.sound(ctx.level(), at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.4F);
        LfUtil.fx(ctx.level(), LfNet.BLOOM, at.add(0, 0.1, 0), new Vec3(0, 1, 0), death ? 0x6B5A3A : 0x8FE07A, 30, radius);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 9F);
        builder.define(DATA_DEATH, false);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    /** Modo "só morte" (Inverter). */
    public boolean deathMode() { return entityData.get(DATA_DEATH); }

    /** 0..1 de opacidade (aparece e some devagar). */
    public float fade(float pt) {
        float a = age + pt;
        float in = Mth.clamp(a / APPEAR, 0F, 1F);
        float out = Mth.clamp((life() + VANISH - a) / VANISH, 0F, 1F);
        return Math.min(in, out);
    }

    /** O cervo anda (no cliente, para animar as pernas). */
    public boolean walking() {
        return age > APPEAR && age < life();
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (age >= life() + VANISH) {
            discard();
            return;
        }
        if (!walking()) return;
        // Caminha devagar para a frente, seguindo o chão
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 next = position().add(-Mth.sin(yaw) * 0.05, 0, Mth.cos(yaw) * 0.05);
        Vec3 ground = LfUtil.groundBelow(level, next.add(0, 3, 0), 10);
        setPos(next.x, ground.y, next.z);
        if (age % 10 == 0) step(level, ctx, (age / 10) % 2 == 0);
        if (age % 20 == 0 && stats != null) pulse(level, ctx, stats);
    }

    /** Um passo de casco: flores brotam (ou murcham) onde ele pisa. */
    private void step(ServerLevel level, SpellContext ctx, boolean left) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double side = left ? 1.1 : -1.1;
        Vec3 hoof = position().add(Mth.cos(yaw) * side, 0, Mth.sin(yaw) * side);
        boolean death = deathMode();
        level.sendParticles(death ? Vfx.ash() : Vfx.happy(), hoof.x, hoof.y + 0.1, hoof.z, 8, 0.6, 0.05, 0.6, 0);
        level.sendParticles(death ? Vfx.smoke() : Vfx.sporeAir(), hoof.x, hoof.y + 0.2, hoof.z, 6, 0.7, 0.1, 0.7, 0);
        LfUtil.sound(level, hoof, death ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.AZALEA_LEAVES_PLACE, 0.9F, 0.6F);
        if (death) return;
        for (int i = 0; i < 3; i++) {
            BlockPos p = BlockPos.containing(hoof.x + level.random.nextGaussian() * 1.6, hoof.y + 0.5, hoof.z + level.random.nextGaussian() * 1.6);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos at = p.above(dy);
                BlockState below = level.getBlockState(at.below());
                if (!level.getBlockState(at).isAir() || !(below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.PODZOL))) continue;
                if (Destruction.protectedAt(level, at, ctx.caster())) break;
                level.setBlockAndUpdate(at, FLOWERS[level.random.nextInt(FLOWERS.length)].defaultBlockState());
                break;
            }
        }
    }

    /** Dar e tirar a vida. */
    private void pulse(ServerLevel level, SpellContext ctx, SpellStats stats) {
        Vec3 c = position().add(0, 1, 0);
        float r = radius();
        boolean death = deathMode();
        if (!death) {
            for (LivingEntity a : LfUtil.alliesAround(ctx, c, r)) {
                ctx.heal(a, HEAL, stats);
                a.removeEffect(MobEffects.POISON);
                a.removeEffect(MobEffects.WITHER);
                level.sendParticles(Vfx.happy(), a.getX(), a.getY() + a.getBbHeight() * 0.7, a.getZ(), 4, 0.3, 0.3, 0.3, 0);
            }
        }
        List<LivingEntity> foes = LfUtil.foesAround(ctx, c, r, 24);
        for (LivingEntity f : foes) {
            ctx.damage(f, DAMAGE * (death ? 1.6F : 1F), stats, this);
            f.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), ctx.caster());
            level.sendParticles(Vfx.sculkSoul(), f.getX(), f.getY() + f.getBbHeight() * 0.6, f.getZ(), 2, 0.2, 0.3, 0.2, 0.02);
            if (!f.isAlive()) {
                LfUtil.fx(level, LfNet.BLOOM, f.position().add(0, 0.1, 0), new Vec3(0, 1, 0), death ? 0x6B5A3A : 0xFFB8D8, 24, 2.5F);
            }
        }
        LfUtil.ring(level, position().add(0, 0.12, 0), death ? 0x4A5A30 : 0x9FF0A0, 16, r);
    }
}
