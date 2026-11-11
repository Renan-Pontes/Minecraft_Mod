package com.renanpontes.isekaimahou.mixin;

import com.renanpontes.isekaimahou.core.social.SocialRules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PvP social (v1.0 — A2): duelo e guerra de cidades liberam o dano entre o par mesmo com o PvP do servidor desligado;
 * membros do mesmo grupo nunca se ferem. Sem regra social, vale o vanilla (PvP do servidor + times).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSocialMixin {
    @Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
    private void isekaiMahou$socialPvp(Player other, CallbackInfoReturnable<Boolean> cir) {
        Boolean rule = SocialRules.canHarm((ServerPlayer) (Object) this, other);
        if (rule != null) cir.setReturnValue(rule);
    }
}
