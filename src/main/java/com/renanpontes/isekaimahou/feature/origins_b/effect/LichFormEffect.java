package com.renanpontes.isekaimahou.feature.origins_b.effect;

import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Forma de Lich: o conjurador vira um lich por um tempo.
 * <ul>
 *   <li>Voo LENTO (atributo de voo do NeoForge + velocidade de voo reduzida, restaurada em ObEvents).</li>
 *   <li>Armadura, robustez e resistência a recuo (modificadores do efeito) e 25% menos dano (ObEvents).</li>
 *   <li>Golpes e magias drenam vida (ObEvents); mortos-vivos comuns não o atacam.</li>
 *   <li>Aura de almas orbitando.</li>
 * </ul>
 */
public class LichFormEffect extends MobEffect {
    /** Velocidade de voo do lich (a normal do jogador é 0.05). */
    public static final float LICH_FLY_SPEED = 0.028F;
    public static final float DEFAULT_FLY_SPEED = 0.05F;

    public LichFormEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x3FE0D0);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.6F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
        ObFx.soulBurst(level, entity.position(), entity.getBbHeight() * 1.4);
        ObFx.boneBurst(level, entity.position().add(0, 1, 0), 16, 0.5);
        // Mortos-vivos que já o perseguiam desistem
        for (Mob m : level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(24),
                m -> m.getTarget() == entity && ObUtil.isUndead(m) && !ObUtil.isBoss(m))) {
            m.setTarget(null);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        if (entity.tickCount % 2 == 0) ObFx.lichAura(level, entity);
        if (entity instanceof ServerPlayer sp) {
            if (!sp.isCreative() && !sp.isSpectator() && Math.abs(sp.getAbilities().getFlyingSpeed() - LICH_FLY_SPEED) > 1.0E-4) {
                sp.getAbilities().setFlyingSpeed(LICH_FLY_SPEED);
                sp.onUpdateAbilities();
            }
            MobEffectInstance inst = sp.getEffect(ObRegistries.LICH_FORM);
            if (inst != null && !inst.isInfiniteDuration() && inst.getDuration() <= 60 && inst.getDuration() % 20 == 0) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.lich_fading", inst.getDuration() / 20)
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        } else if (!(entity instanceof Player)) {
            ObUtil.glide(entity);
        }
        return true;
    }
}
