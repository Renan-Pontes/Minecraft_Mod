package com.renanpontes.isekaimahou.feature.ritual_necro.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "ritual_necro": renderer do Altar Ossuário e o brilho do Filactério por estágio. */
public class RitualNecroClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(RitualNecroClient::renderers);
        modBus.addListener(RitualNecroClient::setup);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NecroContent.ALTAR_BE.get(), OssuaryAltarRenderer::new);
    }

    /** Propriedade "isekai_mahou:soul_glow" (0, .25, .5, .75) escolhe a textura do Filactério no modelo. */
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(NecroContent.PHYLACTERY.get(), IsekaiMahou.id("soul_glow"),
                (stack, level, entity, seed) -> SoulStore.of(stack).stage() * 0.25F));
    }
}
