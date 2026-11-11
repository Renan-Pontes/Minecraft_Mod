package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Roleta mágica da forma Roleta: aparece no chão, gira 2,5 s (a bolinha corre ao contrário) e para num número
 * (europeia, 0–36). O número é sorteado ao nascer e sincronizado, para o cliente desacelerar a roda no lugar certo;
 * o resultado só vale quando ela para:
 * <ul>
 *   <li>Vermelho: os efeitos atingem a área com poder ×1,25;</li>
 *   <li>Preto: ×0,85, mas a área é atingida DUAS vezes;</li>
 *   <li>Zero verde: ×3 e +25 de Tensão (a Sorte aumenta a chance do zero).</li>
 * </ul>
 */
public class RouletteWheelEntity extends Entity implements GeoEntity {
    /** Ordem dos números na roda europeia (sentido horário visto de cima). */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    private static final int[] REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
    public static final int SPIN_TICKS = 50;
    private static final int SECOND_PULSE = 15;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.roulette_wheel.idle");

    private static final EntityDataAccessor<Integer> DATA_RESULT = SynchedEntityData.defineId(RouletteWheelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(RouletteWheelEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    private boolean resolved;
    private int pulsesLeft;
    private int nextPulse;
    private float mult = 1F;

    public RouletteWheelEntity(EntityType<? extends RouletteWheelEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static RouletteWheelEntity create(SpellContext ctx, Vec3 center, float radius, int luck) {
        ServerLevel level = ctx.level();
        RouletteWheelEntity e = new RouletteWheelEntity(GbRegistries.ROULETTE.get(), level);
        e.setPos(center.x, center.y, center.z);
        e.ctx = ctx;
        int number = WHEEL[level.random.nextInt(WHEEL.length)];
        if (number != 0 && luck > 0 && level.random.nextFloat() < 0.005F * luck) number = 0;
        e.entityData.set(DATA_RESULT, number);
        e.entityData.set(DATA_RADIUS, radius);
        e.setYRot(level.random.nextFloat() * 360F);
        Telegraph.circle(level, center, radius, SPIN_TICKS, 0xFFC94A);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RESULT, 0);
        builder.define(DATA_RADIUS, 4.5F);
    }

    public int result() {
        return entityData.get(DATA_RESULT);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public static boolean isRed(int n) {
        for (int r : REDS) if (r == n) return true;
        return false;
    }

    /** Posição do número na roda (0..36). */
    public static int pocketIndex(int number) {
        for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == number) return i;
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount < SPIN_TICKS && random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = radius() * 0.9;
                level().addParticle(ParticleTypes.ENCHANT, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, 0.1, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null) {
            discard();
            return;
        }
        if (tickCount < SPIN_TICKS) {
            // clique da bolinha passando pelas casas, mais lento no fim
            int gap = 2 + tickCount / 12;
            if (tickCount % gap == 0) GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.45F, 1.8F);
            return;
        }
        if (!resolved) {
            resolved = true;
            int n = result();
            String key;
            if (n == 0) {
                mult = 3.0F;
                pulsesLeft = 1;
                key = "green";
                ServerPlayer sp = ctx.casterPlayer();
                if (sp != null && LuckService.isGambler(sp)) LuckService.addFever(sp, 25);
            } else if (isRed(n)) {
                mult = 1.25F;
                pulsesLeft = 1;
                key = "red";
            } else {
                mult = 0.85F;
                pulsesLeft = 2;
                key = "black";
            }
            nextPulse = tickCount;
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null) {
                ChatFormatting color = n == 0 ? ChatFormatting.GREEN : isRed(n) ? ChatFormatting.RED : ChatFormatting.GRAY;
                sp.displayClientMessage(Component.translatable(String.join("", "message.isekai_mahou.gambler.roulette.", key), n,
                        String.format(java.util.Locale.ROOT, "%.2f", mult)).withStyle(color), true);
            }
            GbFx.sound(sl, position(), n == 0 ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F,
                    n == 0 ? 0.7F : 1.2F);
        }
        if (pulsesLeft > 0 && tickCount >= nextPulse) {
            pulsesLeft--;
            nextPulse = tickCount + SECOND_PULSE;
            pulse(sl);
        }
        if (pulsesLeft <= 0 && tickCount > nextPulse + 10) discard();
    }

    private void pulse(ServerLevel sl) {
        float r = radius();
        Vec3 c = position();
        SpellContext copy = ctx.copy(ctx.powerMult() * mult);
        copy.resolveArea(c.add(0, 0.5, 0), r, this, false);
        copy.impact(c, ctx.look(), null);
        if (pulsesLeft == 0) copy.end(c, ctx.look());
        int n = result();
        GbFx.blast(sl, c, r, n == 0 ? 0x2ECC71 : isRed(n) ? 0xD8303A : 0x26222C);
        if (n == 0) GbFx.coins(sl, c.add(0, 1, 0), 12);
        GbFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.5F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
