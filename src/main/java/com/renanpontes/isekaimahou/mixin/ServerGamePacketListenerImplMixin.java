package com.renanpontes.isekaimahou.mixin;

import com.renanpontes.isekaimahou.core.MagicHover;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Não expulsa por "voar" quem está sendo segurado no ar por magia (ver {@link MagicHover}). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void isekaiMahou$magicHover(CallbackInfo ci) {
        if (this.player != null && MagicHover.exempt(this.player)) this.aboveGroundTickCount = 0;
    }
}
