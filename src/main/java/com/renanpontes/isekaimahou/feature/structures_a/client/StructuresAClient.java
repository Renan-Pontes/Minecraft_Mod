package com.renanpontes.isekaimahou.feature.structures_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineRegistry;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "structures_a": renderer do altar e o modelo do item por elemento. */
public class StructuresAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(ShrineRegistry.ALTAR_BE.get(), ShrineAltarRenderer::new));
        // O item do altar troca de modelo conforme o elemento guardado (overrides em models/item/shrine_altar.json).
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                ItemProperties.register(ShrineRegistry.ALTAR_ITEM.get(), IsekaiMahou.id("element"), (stack, level, entity, seed) -> {
                    Element el = ShrineAltarItem.elementOf(stack);
                    return el == null ? 0F : Math.max(0, Element.BASE.indexOf(el));
                })));
    }
}
