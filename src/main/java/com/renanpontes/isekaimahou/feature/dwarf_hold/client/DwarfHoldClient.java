package com.renanpontes.isekaimahou.feature.dwarf_hold.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;
import com.renanpontes.isekaimahou.feature.dwarf_hold.entity.DwarfEntity;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Parte de cliente do "dwarf_hold": anões (GeckoLib), pose de bloqueio do Escudo do Herói, HUD do Monóculo e dicas. */
public class DwarfHoldClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            e.registerEntityRenderer(DwarfRegistry.DWARF_MERCHANT.get(), ctx -> renderer(ctx, "dwarf_merchant"));
            e.registerEntityRenderer(DwarfRegistry.DWARF_SMITH.get(), ctx -> renderer(ctx, "dwarf_smith"));
        });
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() ->
                ItemProperties.register(DwarfRegistry.HERO_SHIELD.get(), ResourceLocation.withDefaultNamespace("blocking"),
                        (stack, level, en, seed) -> en != null && en.isUsingItem() && en.getUseItem() == stack ? 1F : 0F)));
        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("dwarf_hold_appraisal"), new AppraisalHud()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, AppraisalHud::onClientTick);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, AppraisalHud::onTooltip);
    }

    private static GeoEntityRenderer<DwarfEntity> renderer(EntityRendererProvider.Context ctx, String id) {
        GeoEntityRenderer<DwarfEntity> r = new GeoEntityRenderer<>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), true));
        r.withScale(1.0F);
        return r;
    }
}
