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
import net.minecraft.world.entity.player.Player;

/**
 * Asas Demoníacas. O voo vem do atributo {@code neoforge:creative_flight} (modificador do próprio efeito):
 * ele some sozinho quando o efeito termina, é removido (leite), na morte ou ao sair do jogo — nada de
 * "mayfly" preso no jogador. O pouso seguro (Queda Lenta) é dado em ObEvents quando o efeito acaba no ar.
 * Mobs não voam: planam (queda lenta, sem dano de queda) enquanto durar.
 */
public class DemonWingsEffect extends MobEffect {
    public DemonWingsEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7A1018);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        if (entity.level().isClientSide) return;
        ServerLevel level = (ServerLevel) entity.level();
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.3F);
        ObFx.hellfireBurst(level, entity.position(), 1.0);
        if (entity instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator()) {
            // Já sai voando: um impulso para cima (no chão o cliente desligaria o voo) e o voo ligado
            if (sp.onGround()) {
                sp.setDeltaMovement(sp.getDeltaMovement().x, 0.6, sp.getDeltaMovement().z);
                sp.hurtMarked = true;
            }
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        if (entity.tickCount % 2 == 0) ObFx.demonWings(level, entity);
        boolean flying = entity instanceof Player p ? p.getAbilities().flying : !entity.onGround();
        if (flying && entity.tickCount % 14 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 0.6F);
        }
        if (!(entity instanceof Player)) ObUtil.glide(entity);
        // Aviso nos últimos 3 segundos
        MobEffectInstance inst = entity.getEffect(ObRegistries.DEMON_WINGS);
        if (inst != null && entity instanceof ServerPlayer sp && !inst.isInfiniteDuration() && inst.getDuration() <= 60 && inst.getDuration() % 20 == 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.wings_fading", inst.getDuration() / 20)
                    .withStyle(ChatFormatting.RED), true);
        }
        return true;
    }
}
