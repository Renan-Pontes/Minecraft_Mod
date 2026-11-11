package com.renanpontes.isekaimahou.feature.potions.client;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.potions.PotionTooltips;
import com.renanpontes.isekaimahou.feature.potions.PotionsContent;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente do módulo potions: renderer do frasco em voo e o nome do recurso do jogador nas dicas (Mana/Vigor/Foco). */
public class PotionsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(PotionsContent.THROWN_POTION.get(), ThrownItemRenderer::new));
        PotionTooltips.localResourceName = () -> {
            Player p = Minecraft.getInstance().player;
            return p == null ? null : MagicApi.resourceName(p);
        };
    }
}
