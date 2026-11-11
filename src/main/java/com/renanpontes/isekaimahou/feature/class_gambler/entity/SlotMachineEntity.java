package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.JackpotService;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
 * Caça-Níquel da forma Caça-Níquel: aparece à sua frente, a alavanca desce e os 3 rolos giram, parando um de cada vez.
 * Símbolos: Cereja, Sino, BAR e 7. O prêmio sai em FICHAS teleguiadas que levam a magia:
 * <ul>
 *   <li>7-7-7: 7 fichas ×2 e JACKPOT (se você ainda não estiver nele);</li>
 *   <li>três iguais: 5 fichas ×1,5 · par: 3 fichas ×1 · nada: 1 ficha ×0,7.</li>
 * </ul>
 * Cada Dividir soma +1 ficha ao prêmio. A Sorte aumenta a chance de os rolos repetirem o símbolo.
 */
public class SlotMachineEntity extends Entity implements GeoEntity {
    public static final int CHERRY = 0, BELL = 1, BAR = 2, SEVEN = 3;
    /** Tick em que cada rolo para (o cliente usa o mesmo número). */
    public static final int[] STOP = {22, 30, 38};
    private static final int PAYOUT = 42;
    private static final int LIFE = 62;
    private static final RawAnimation PULL = RawAnimation.begin().thenPlay("animation.slot_machine.pull")
            .thenLoop("animation.slot_machine.idle");

    private static final EntityDataAccessor<Integer> DATA_REELS = SynchedEntityData.defineId(SlotMachineEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    private int extraCoins;
    private boolean paid;

    public SlotMachineEntity(EntityType<? extends SlotMachineEntity> type, Level level) {
        super(type, level);
    }

    public static SlotMachineEntity create(SpellContext ctx, Vec3 pos, float yaw, int luck, int extraCoins) {
        ServerLevel level = ctx.level();
        SlotMachineEntity e = new SlotMachineEntity(GbRegistries.SLOT_MACHINE.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.yRotO = yaw;
        e.ctx = ctx;
        e.extraCoins = Math.max(0, extraCoins);
        RandomSource r = level.random;
        int a = r.nextInt(4);
        int b = luck > 0 && r.nextFloat() < 0.04F * luck ? a : r.nextInt(4);
        int c = luck > 0 && r.nextFloat() < 0.04F * luck ? b : r.nextInt(4);
        e.entityData.set(DATA_REELS, a | (b << 4) | (c << 8));
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_REELS, 0);
    }

    /** Símbolo do rolo {@code i} (0..2). */
    public int reel(int i) {
        return (entityData.get(DATA_REELS) >> (4 * i)) & 0xF;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null) {
            discard();
            return;
        }
        if (tickCount == 1) GbFx.sound(sl, position(), SoundEvents.LEVER_CLICK, 1.0F, 0.7F);
        if (tickCount < STOP[2] && tickCount % 3 == 0) GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 1.9F);
        for (int i = 0; i < 3; i++) {
            if (tickCount == STOP[i]) {
                GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.8F + reel(i) * 0.2F);
                Vec3 p = reelPos(i);
                sl.sendParticles(GbFx.dust(GbFx.GOLD, 0.8F), p.x, p.y, p.z, 4, 0.05, 0.05, 0.05, 0);
            }
        }
        if (tickCount == PAYOUT && !paid) payout(sl);
        if (tickCount >= LIFE) {
            sl.sendParticles(GbFx.dust(GbFx.GOLD, 1.4F), getX(), getY() + 1.1, getZ(), 30, 0.4, 0.8, 0.4, 0);
            GbFx.sound(sl, position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.4F);
            discard();
        }
    }

    /** Ponto (no mundo) do visor do rolo i, para as partículas. */
    private Vec3 reelPos(int i) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 front = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return position().add(0, 1.45, 0).add(front.scale(0.5)).add(right.scale((i - 1) * 0.3));
    }

    private void payout(ServerLevel sl) {
        paid = true;
        int a = reel(0), b = reel(1), c = reel(2);
        int coins;
        float mult;
        String key;
        if (a == b && b == c && a == SEVEN) {
            coins = 7;
            mult = 2.0F;
            key = "jackpot";
        } else if (a == b && b == c) {
            coins = 5;
            mult = 1.5F;
            key = "triple";
        } else if (a == b || b == c || a == c) {
            coins = 3;
            mult = 1.0F;
            key = "pair";
        } else {
            coins = 1;
            mult = 0.7F;
            key = "none";
        }
        coins += extraCoins;
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) {
            sp.displayClientMessage(Component.translatable(String.join("", "message.isekai_mahou.gambler.slot.", key), coins,
                            String.format(java.util.Locale.ROOT, "%.1f", mult))
                    .withStyle(key.equals("jackpot") ? ChatFormatting.LIGHT_PURPLE : mult >= 1.5F ? ChatFormatting.GOLD : ChatFormatting.WHITE), true);
            if (key.equals("jackpot") && LuckService.isGambler(sp) && !JackpotService.active(sp)) JackpotService.start(sp, 0);
        }
        Vec3 spout = position().add(0, 1.0, 0).add(frontDir().scale(0.7));
        SpellContext pay = ctx.copy(ctx.powerMult() * mult);
        LivingEntity caster = ctx.caster();
        List<LivingEntity> foes = ctx.enemiesAround(position(), 18);
        for (int i = 0; i < coins; i++) {
            Vec3 dir;
            if (!foes.isEmpty()) {
                LivingEntity t = foes.get(i % foes.size());
                dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(spout).normalize();
            } else {
                dir = caster != null ? caster.getLookAngle() : frontDir();
            }
            dir = dir.add((sl.random.nextDouble() - 0.5) * 0.25, 0.15 + sl.random.nextDouble() * 0.1, (sl.random.nextDouble() - 0.5) * 0.25)
                    .normalize();
            sl.addFreshEntity(ThrownCardEntity.spell(pay, spout, dir, 1.1F, ThrownCardEntity.Mode.COIN));
        }
        GbFx.coins(sl, spout, 6 + coins * 2);
        if (mult >= 1.5F) {
            GbFx.sound(sl, spout, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
            GbFx.sound(sl, spout, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.5F);
        } else {
            GbFx.sound(sl, spout, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.2F);
        }
        GbFx.sound(sl, spout, SoundEvents.CHAIN_FALL, 0.8F, 1.7F);
    }

    private Vec3 frontDir() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
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
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(PULL)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
