package com.renanpontes.isekaimahou.feature.origins_a.client;

import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TempEnchant;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Parte de cliente do modulo "origins_a": renderers das runas e familiares, Metamorfose e tooltip. */
public class OriginsAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(OaRegistries.EXPLOSIVE_SEAL.get(), RuneRenderers.Seal::new);
            e.registerEntityRenderer(OaRegistries.TELEPORT_CIRCLE.get(), RuneRenderers.Circle::new);
            e.registerEntityRenderer(OaRegistries.WARD.get(), RuneRenderers.WardRenderer::new);
            e.registerEntityRenderer(OaRegistries.FAMILIAR_CROW.get(), FamiliarRenderers.Crow::new);
            e.registerEntityRenderer(OaRegistries.FAMILIAR_CAT.get(), FamiliarRenderers.Cat::new);
        });
        ShapeClient.init();
        NeoForge.EVENT_BUS.addListener(OriginsAClient::onTooltip);
    }

    /** Tempo restante do Encantamento Temporário no tooltip do item. */
    private static void onTooltip(ItemTooltipEvent event) {
        TempEnchant.Data data = event.getItemStack().get(OaRegistries.TEMP_ENCHANT.get());
        Minecraft mc = Minecraft.getInstance();
        if (data == null || mc.level == null) return;
        long left = Math.max(0, data.expires() - mc.level.getGameTime()) / 20;
        String time = String.format("%d:%02d", left / 60, left % 60);
        event.getToolTip().add(Component.translatable("tooltip.isekai_mahou.origins_a.temp_enchant", time)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
