package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Raiz do Mundo (modelo GeckoLib): uma raiz gigante e retorcida rompe o chão debaixo de um inimigo, arremessa,
 * ENROLA-SE nele (Enraizado + aperto contínuo) e no fim desaba esmagando. Sem alvo, só brota e golpeia a área.
 */
public class WorldRootEntity extends FcSpellEntity implements GeoEntity {
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.world_root.emerge");
    private static final RawAnimation SQUEEZE = RawAnimation.begin().thenLoop("animation.world_root.squeeze");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.world_root.slam");
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(WorldRootEntity.class, EntityDataSerializers.INT);

    public static final int EMERGE_TICKS = 10;
    public static final int SLAM_TICKS = 14;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float emergeDamage = 8F;
    @Nullable
    private Vec3 holdAt;

    public WorldRootEntity(EntityType<? extends WorldRootEntity> type, Level level) {
        super(type, level);
    }

    /** Teto de raízes vivas por conjurador (Zona/Aura/Eco repetindo a magia não enchem o mundo de raízes). */
    private static final int MAX_PER_OWNER = 24;

    public static void sprout(SpellContext ctx, SpellStats stats, Vec3 ground, @Nullable LivingEntity target, int holdTicks, float emergeDamage) {
        ServerLevel sl = ctx.level();
        if (FcUtil.owned(sl, FcRegistries.WORLD_ROOT.get(), ctx.casterId()) >= MAX_PER_OWNER) return;
        // Já está enrolado numa raiz: uma segunda puxaria o alvo de um lado para o outro
        if (target != null && !sl.getEntities(FcRegistries.WORLD_ROOT.get(),
                r -> r.isAlive() && r.entityData.get(TARGET) == target.getId()).isEmpty()) return;
        WorldRootEntity r = new WorldRootEntity(FcRegistries.WORLD_ROOT.get(), sl);
        r.setPos(ground.x, ground.y, ground.z);
        r.setYRot(sl.random.nextFloat() * 360F);
        boolean boss = target != null && Statuses.isBossLike(target);
        int hold = target == null ? 16 : boss ? 30 : holdTicks;
        r.setup(ctx, stats, Element.NATURE, EMERGE_TICKS + hold + SLAM_TICKS, 0.9F + sl.random.nextFloat() * 0.3F + 0.1F * Math.max(0, stats.amplify));
        r.entityData.set(TARGET, target != null ? target.getId() : -1);
        r.emergeDamage = emergeDamage;
        sl.addFreshEntity(r);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(r.blockPosition().below())),
                ground.x, ground.y + 0.2, ground.z, 24, 0.8, 0.2, 0.8, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
    }

    @Nullable
    private LivingEntity target() {
        Entity e = level().getEntity(entityData.get(TARGET));
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int age = age();
        Vec3 base = position();
        LivingEntity t = target();
        if (age == 4) {
            // A raiz rompe o chão: rachaduras e um buraco no ponto de saída (regra de destruição)
            Destruction.surface(sl, base, 1.8F * scale(), owner(), Destruction.Mode.CRUSH);
            Destruction.sphere(sl, base.add(0, -0.5, 0), 1.1F, owner(), Destruction.Mode.BREAK);
            for (LivingEntity le : enemiesIn(base.add(0, 1, 0), 1.8 * scale())) {
                hit(le, emergeDamage);
                FcUtil.knock(le, new Vec3(0, 0.75, 0));
            }
            FcFx.sound(sl, base, SoundEvents.ROOTED_DIRT_BREAK, 1.6F, 0.5F);
            FcFx.sound(sl, base, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.6F);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                    base.x, base.y + 0.5, base.z, 30, 0.6, 0.6, 0.6, 0.3);
        }
        if (t != null && age >= EMERGE_TICKS && lifeLeft() > SLAM_TICKS) {
            // Enrolado: preso alto na raiz, apertado a cada segundo
            if (holdAt == null) holdAt = base.add(0, 1.4 * scale(), 0);
            if (!FcUtil.untouchable(t) && !Statuses.isBossLike(t)) {
                if (t.position().distanceToSqr(holdAt) > 0.09) t.teleportTo(holdAt.x, holdAt.y, holdAt.z);
                t.setDeltaMovement(Vec3.ZERO);
                t.hurtMarked = true;
                t.resetFallDistance();
            }
            if (age % 10 == 0) Statuses.apply(t, Statuses.Kind.ROOTED, 1, 30, owner(), power);
            if ((age - EMERGE_TICKS) % 20 == 0) {
                hit(t, 2.5F);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()),
                        t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                FcFx.sound(sl, t.position(), SoundEvents.WOOD_BREAK, 1F, 0.5F);
            }
        }
        if (lifeLeft() == SLAM_TICKS - 6) slam(sl, base, t);
        if (age % 6 == 0) sl.sendParticles(FcFx.dust(FcFx.NATURE, 1.1F), base.x, base.y + 1.5 * scale(), base.z, 2, 0.5, 1.2, 0.5, 0);
    }

    /** A raiz desaba sobre o chão: dano em área e quem estava preso é jogado para baixo. */
    private void slam(ServerLevel sl, Vec3 base, @Nullable LivingEntity t) {
        // A raiz se enrola de volta e bate no próprio pé: onda em volta da base
        Vec3 hitAt = base;
        for (LivingEntity le : enemiesIn(hitAt.add(0, 0.5, 0), 2.6 * scale())) {
            hit(le, 6F);
            FcUtil.knock(le, new Vec3(0, -0.8, 0));
        }
        if (t != null && t.distanceToSqr(hitAt) > 2.6 * 2.6 * scale() * scale()) {
            hit(t, 6F);
            FcUtil.knock(t, new Vec3(0, -0.8, 0));
        }
        Destruction.surface(sl, hitAt, 1.5F * scale(), owner(), Destruction.Mode.CRUSH);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(blockPosition().below())),
                hitAt.x, hitAt.y + 0.2, hitAt.z, 30, 1.2, 0.2, 1.2, 0.25);
        FcFx.shockwave(sl, hitAt.add(0, 0.2, 0), FcFx.dust(FcFx.BARK, 1.6F), 20, 0.3);
        FcFx.sound(sl, hitAt, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4F, 0.6F);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (age() < EMERGE_TICKS) return state.setAndContinue(EMERGE);
            if (lifeLeft() <= SLAM_TICKS) return state.setAndContinue(SLAM);
            return state.setAndContinue(SQUEEZE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
