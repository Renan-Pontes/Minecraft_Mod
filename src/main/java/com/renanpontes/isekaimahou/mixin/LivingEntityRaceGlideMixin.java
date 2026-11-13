package com.renanpontes.isekaimahou.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.renanpontes.isekaimahou.feature.character.RaceFlight;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v1.3.1 — voo planado das formas aladas (Anjo, Demônio, Dracônico): o vanilla só mantém o "planar de élitro" com um
 * élitro no peito. No servidor, quem está numa forma alada plana sem item ({@link RaceFlight#handleFallFlying}); para
 * todo o resto, o vanilla segue igual. Custo: um {@code instanceof} por entidade viva por tick.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRaceGlideMixin {
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void isekaiMahou$raceGlide(CallbackInfo ci) {
        if ((Object) this instanceof Player p && RaceFlight.handleFallFlying(p)) ci.cancel();
    }
}
