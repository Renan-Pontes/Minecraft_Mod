package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Vulcão: um mini-vulcão (modelo GeckoLib) rompe o chão e, durante a magia, cospe bombas de lava em arco nos inimigos
 * — cada bomba é TELEGRAFADA com um círculo no chão onde vai cair. Quem encosta na base queima. No fim, a montanha
 * explode numa última erupção e desaba deixando uma cratera (Destruction, respeita a regra de destruição).
 */
public class MiniVolcanoEntity extends FcSpellEntity implements GeoEntity {
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.mini_volcano.emerge");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mini_volcano.idle");
    private static final RawAnimation ERUPT = RawAnimation.begin().thenPlay("animation.mini_volcano.erupt");
    private static final RawAnimation COLLAPSE = RawAnimation.begin().thenPlay("animation.mini_volcano.collapse");
    private static final EntityDataAccessor<Integer> ERUPT_AGE = SynchedEntityData.defineId(MiniVolcanoEntity.class, EntityDataSerializers.INT);

    public static final int EMERGE_TICKS = 20;
    public static final int COLLAPSE_TICKS = 20;
    private static final int FLIGHT = 24;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int interval = 16;
    private float bombDamage = 9F;
    private int nextBomb = EMERGE_TICKS + 4;
    private boolean finalBlast;
    @Nullable
    private SpellStats stats;

    public MiniVolcanoEntity(EntityType<? extends MiniVolcanoEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 ground) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<MiniVolcanoEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.MINI_VOLCANO.get(), v -> v.isAlive() && owner.equals(v.ownerId())));
            // Zona/Aura repetem o efeito a cada pulso com a MESMA conjuração: o vulcão dela continua (senão afundaria a cada
            // segundo sem nunca cuspir bombas). Uma conjuração NOVA troca o vulcão.
            for (MiniVolcanoEntity v : mine) if (v.ctx == ctx) return;
            // Um vulcão por conjurador: o antigo afunda (sem a erupção final)
            for (MiniVolcanoEntity v : mine) v.discard();
        }
        MiniVolcanoEntity v = new MiniVolcanoEntity(FcRegistries.MINI_VOLCANO.get(), sl);
        v.setPos(ground.x, ground.y, ground.z);
        v.setYRot(sl.random.nextFloat() * 360F);
        float size = 1F + 0.15F * Math.max(0, stats.amplify) + 0.25F * stats.aoe;
        v.setup(ctx, stats, Element.MAGMA, stats.duration(220), size);
        v.stats = stats;
        v.interval = Math.max(8, 16 - 2 * stats.amplify - 2 * stats.split);
        v.bombDamage = 9F;
        sl.addFreshEntity(v);
        // v1.3: o chão racha num impacto de magma e a área ferve enquanto o vulcão vive
        com.renanpontes.isekaimahou.core.ElementFx.impactFx(sl, ground.add(0, 0.2, 0), com.renanpontes.isekaimahou.core.Element.MAGMA, 0xFF4500, 3, null, 1.2F);
        com.renanpontes.isekaimahou.core.ElementFx.area(sl, ground, com.renanpontes.isekaimahou.core.Element.MAGMA, 0xFF4500, 3.0F, 2, 200);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(v.blockPosition().below())),
                ground.x, ground.y + 0.3, ground.z, 60, 1.8, 0.4, 1.8, 0.3);
        FcFx.sound(sl, ground, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.5F, 0.5F);
        FcFx.sound(sl, ground, SoundEvents.LAVA_AMBIENT, 2F, 0.5F);
        BossKit.shake(sl, ground, 20, 0.9F, 20);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ERUPT_AGE, -100);
    }

    /** Boca da cratera (de onde saem as bombas). */
    public Vec3 crater() {
        return position().add(0, 3.1 * scale(), 0);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int age = age();
        float s = scale();
        Vec3 base = position();
        if (age < EMERGE_TICKS) {
            if (age % 4 == 0) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(blockPosition().below())),
                    base.x, base.y + 0.3, base.z, 12, 1.5 * s, 0.3, 1.5 * s, 0.2);
            return;
        }
        // Base quente: queima e afasta quem encosta
        if (age % 10 == 0) {
            for (LivingEntity le : enemiesIn(base.add(0, 1, 0), 2.6 * s)) {
                hit(le, 3F);
                Statuses.applyUpTo(le, Statuses.Kind.BURNING, 1, 100, owner(), power, 3);
                Vec3 out = FcUtil.flat(le.position().subtract(base));
                FcUtil.knock(le, new Vec3(out.x * 0.5, 0.25, out.z * 0.5));
            }
        }
        // Fumaça e brasas na boca
        if (age % 3 == 0) {
            Vec3 c = crater();
            sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 0.3, c.z, 1, 0.3 * s, 0.1, 0.3 * s, 0.02);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 1, 0.3 * s, 0.1, 0.3 * s, 0);
        }
        if (life == COLLAPSE_TICKS) finalEruption(sl);
        if (life > COLLAPSE_TICKS && age >= nextBomb) {
            nextBomb = age + interval;
            spitBomb(sl);
        }
        if (age % 30 == 0) FcFx.sound(sl, base, SoundEvents.LAVA_AMBIENT, 1.6F, 0.6F);
    }

    private void spitBomb(ServerLevel sl) {
        if (ctx == null || stats == null) return;
        Vec3 base = position();
        List<LivingEntity> foes = new ArrayList<>(enemiesIn(base, 16));
        Vec3 land;
        if (!foes.isEmpty()) {
            foes.sort((a, b) -> Double.compare(a.distanceToSqr(base), b.distanceToSqr(base)));
            LivingEntity t = foes.get(random.nextInt(Math.min(4, foes.size())));
            // Mira um pouco à frente de quem corre (o telegrafo mostra onde vai cair: dá para fugir)
            Vec3 lead = t.getDeltaMovement().multiply(FLIGHT * 0.5, 0, FLIGHT * 0.5);
            land = FcUtil.ground(sl, t.position().add(lead), 6);
        } else {
            double a = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * 6;
            land = FcUtil.ground(sl, base.add(Math.cos(a) * d, 2, Math.sin(a) * d), 8);
        }
        float blast = 2.2F + 0.4F * stats.aoe;
        Telegraph.show(sl, Telegraph.Shape.circle(land.add(0, 0.05, 0), blast).warmup(FLIGHT).color(FcFx.MAGMA).noRunes());
        LavaBombEntity.launch(ctx, stats, crater(), land, FLIGHT, bombDamage, blast);
        entityData.set(ERUPT_AGE, age());
        Vec3 c = crater();
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 10, 0.4, 0.3, 0.4, 0.3);
        sl.sendParticles(Vfx.largeSmoke(), c.x, c.y + 0.5, c.z, 8, 0.4, 0.4, 0.4, 0.06);
        FcFx.sound(sl, c, SoundEvents.BLAZE_SHOOT, 1.4F, 0.5F);
        FcFx.sound(sl, c, SoundEvents.LAVA_POP, 1.6F, 0.7F);
    }

    /** A montanha explode e desaba: grande dano, Em Chamas 3, cratera e clarão. */
    private void finalEruption(ServerLevel sl) {
        if (finalBlast) return;
        finalBlast = true;
        entityData.set(ERUPT_AGE, age());
        float s = scale();
        Vec3 base = position();
        float r = 5.5F * s;
        for (LivingEntity le : enemiesIn(base.add(0, 1, 0), r)) {
            hit(le, 14F);
            Statuses.applyUpTo(le, Statuses.Kind.BURNING, 2, 160, owner(), power, 3);
            Vec3 out = FcUtil.flat(le.position().subtract(base));
            FcUtil.knock(le, new Vec3(out.x * 1.2, 0.8, out.z * 1.2));
        }
        Destruction.crater(sl, base, 3.5F * s, owner());
        Vec3 c = crater();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 60, 1.2, 0.8, 1.2, 0.5);
        sl.sendParticles(Vfx.flame(), c.x, c.y, c.z, 80, 0.6, 1.2, 0.6, 0.35);
        sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 1, c.z, 30, 1.5, 1.5, 1.5, 0.08);
        FcFx.shockwave(sl, base.add(0, 0.3, 0), FcFx.fade(FcFx.MAGMA_HOT, FcFx.MAGMA_DARK, 2.5F), 48, 0.6);
        FcFx.sound(sl, base, SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.5F);
        FcFx.sound(sl, base, SoundEvents.BUCKET_EMPTY_LAVA, 2F, 0.4F);
        BossKit.shake(sl, base, 32, 1.8F, 30);
        FcNet.flash(sl, c, 24, FcFx.MAGMA, 0.35F, 14);
    }

    @Override
    protected void clientTick() {
        if (age() < EMERGE_TICKS) return;
        float s = scale();
        double y = getY() + 3.1 * s;
        level().addParticle(Vfx.smoke(), getX() + (random.nextDouble() - 0.5) * s, y, getZ() + (random.nextDouble() - 0.5) * s, 0, 0.12, 0);
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.LAVA, getX(), y, getZ(), 0, 0, 0);
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            int age = age();
            if (age < EMERGE_TICKS) return state.setAndContinue(EMERGE);
            if (lifeLeft() <= COLLAPSE_TICKS) return state.setAndContinue(COLLAPSE);
            if (age - entityData.get(ERUPT_AGE) < 10) return state.setAndContinue(ERUPT);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 160 * 160; }
}
