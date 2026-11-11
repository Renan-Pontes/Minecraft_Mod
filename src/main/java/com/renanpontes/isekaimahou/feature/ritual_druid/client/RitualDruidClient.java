package com.renanpontes.isekaimahou.feature.ritual_druid.client;

import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "ritual_druid": renderer do Espírito Animal e a tela da Árvore Coração. */
public class RitualDruidClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(RitualDruidClient::renderers);
        // O pacote da árvore chega no código comum; a tela é aberta aqui (só existe no cliente)
        DruidNetwork.openView = view -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof HeartTreeScreen open && open.view().pos().equals(view.pos())) open.update(view);
            else mc.setScreen(new HeartTreeScreen(view));
        };
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DruidContent.ANIMAL_SPIRIT.get(), AnimalSpiritRenderer::new);
    }
}
