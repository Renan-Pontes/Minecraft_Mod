package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bola Escâner: a bola de aço cravejada de luzes voa, para no ar (ou no primeiro obstáculo) e VIBRA — três ondas de
 * varredura saem dela. Todos os inimigos no raio brilham (revelados através das paredes); os mais perigosos (maior vida
 * máxima) ganham a marca de PONTO FRACO (Exposto) e recebem os efeitos da magia. Some depois da terceira onda.
 */
public class ScanBallEntity extends ThrowableProjectile implements GeoEntity {
    public static final int WAVES = 3;
    public static final int WAVE_GAP = 14;
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_ball.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    private boolean initialized;
    private float radius = 20F;
    private int weakPoints = 3;
    private int stoppedAt = -1;
    private int waves;

    public ScanBallEntity(EntityType<? extends ScanBallEntity> type, Level level) {
        super(type, level);
    }

    public static ScanBallEntity spell(SpellContext ctx, Vec3 from, Vec3 dir, float radius, int weakPoints) {
        ScanBallEntity e = new ScanBallEntity(SbRegistry.SCAN_BALL.get(), ctx.level());
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.ctx = ctx;
        e.initialized = true;
        e.radius = radius;
        e.weakPoints = weakPoints;
        e.setPos(from.x, from.y, from.z);
        e.shoot(dir.x, dir.y, dir.z, 1.5F, 0F);
        return e;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() {
        return stoppedAt >= 0 ? 0.0 : 0.01;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !initialized) {
            discard();
            return;
        }
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (stoppedAt < 0 && tickCount >= 16) stop(sl);
        if (stoppedAt >= 0) {
            setDeltaMovement(Vec3.ZERO);
            int since = tickCount - stoppedAt;
            if (since % WAVE_GAP == 0 && waves < WAVES) wave(sl);
            if (since % 2 == 0) SbFx.spinRing(sl, position(), 0x7FE8FF, 0.35, 6);
            if (waves >= WAVES && since > WAVE_GAP * WAVES) {
                sl.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
                if (ctx != null) ctx.end(position(), new Vec3(0, 1, 0));
                discard();
            }
        } else {
            sl.sendParticles(SbFx.dust(0x7FE8FF, 0.6F), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void stop(ServerLevel sl) {
        stoppedAt = tickCount;
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        SbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.9F);
    }

    /** Uma onda de varredura: anéis que crescem e revelam os inimigos; a primeira marca os pontos fracos. */
    private void wave(ServerLevel sl) {
        waves++;
        Vec3 c = position();
        for (int i = 1; i <= 5; i++) {
            final double r = radius * i / 5.0;
            final int ring = i;
            com.renanpontes.isekaimahou.glyph.SpellScheduler.schedule(i, () -> {
                int pts = 10 + ring * 6;
                SbFx.spinRing(sl, c, 0x7FE8FF, r, pts);
                SbFx.spinRing(sl, c.add(0, 1.2, 0), 0x4FB8FF, r * 0.85, pts / 2);
            });
        }
        SbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F + 0.2F * waves);
        SbFx.sound(sl, c, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 1.6F + 0.1F * waves);
        if (ctx == null) return;
        List<LivingEntity> found = new ArrayList<>(ctx.enemiesAround(c, radius));
        for (LivingEntity e : found) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false), ctx.caster());
        }
        if (waves != 1) return;
        found.sort(Comparator.comparingDouble(LivingEntity::getMaxHealth).reversed());
        int marked = 0;
        for (LivingEntity e : found) {
            if (marked >= weakPoints) break;
            marked++;
            Statuses.apply(e, Statuses.Kind.EXPOSED, 2, 120, ctx.caster(), ctx.powerMult());
            Vec3 h = e.position().add(0, e.getBbHeight() * 0.6, 0);
            SbFx.spinRing(sl, h, SbFx.GOLD, 0.45, 10);
            sl.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 3, 0.05, 0.05, 0.05, 0.01);
            e.invulnerableTime = 0;
            ctx.resolveOnEntity(e, this);
        }
        if (ctx.caster() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.scan", found.size(), marked)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level() instanceof ServerLevel sl && stoppedAt < 0) stop(sl);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!(level() instanceof ServerLevel sl) || stoppedAt >= 0) return;
        Vec3 at = result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.4));
        setPos(at.x, at.y, at.z);
        stop(sl);
    }

    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        return stoppedAt < 0 && target != getOwner() && super.canHitEntity(target) && (ctx == null || !ctx.isAlly(target));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        initialized = false;
    }
}
