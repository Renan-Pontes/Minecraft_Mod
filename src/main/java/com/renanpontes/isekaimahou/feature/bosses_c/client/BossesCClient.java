package com.renanpontes.isekaimahou.feature.bosses_c.client;

import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "bosses_c": renderers GeckoLib dos chefes, correntes do Patrono e projéteis. */
public class BossesCClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BossesCRegistry.RENEGADE_ARCHMAGE.get(), ctx -> new BossGeoRenderer<>(ctx, "renegade_archmage", true, 1.0F, 0.6F));
        e.registerEntityRenderer(BossesCRegistry.ARCHMAGE_ILLUSION.get(), IllusionRenderer::new);
        e.registerEntityRenderer(BossesCRegistry.CORRUPTED_WITCH.get(), ctx -> new BossGeoRenderer<>(ctx, "corrupted_witch", true, 1.0F, 0.6F));
        e.registerEntityRenderer(BossesCRegistry.IMPRISONED_PATRON.get(), PatronRenderer::new);
        e.registerEntityRenderer(BossesCRegistry.CORRUPTED_ENT.get(), ctx -> new BossGeoRenderer<>(ctx, "corrupted_ent", false, 1.0F, 1.4F));
        e.registerEntityRenderer(BossesCRegistry.LICH.get(), ctx -> new BossGeoRenderer<>(ctx, "lich", true, 1.0F, 0.5F));
        e.registerEntityRenderer(BossesCRegistry.LICH_PHYLACTERY.get(), ctx -> new BossGeoRenderer<>(ctx, "lich_phylactery", false, 1.0F, 0.4F));
        e.registerEntityRenderer(BossesCRegistry.ENT_ROOT.get(), ctx -> new BossGeoRenderer<>(ctx, "corrupted_ent_root", false, 1.0F, 0.0F));
        e.registerEntityRenderer(BossesCRegistry.BOLT.get(), BoltRenderer::new);
        e.registerEntityRenderer(BossesCRegistry.CURSED_BOTTLE.get(), ThrownItemRenderer::new);
        e.registerEntityRenderer(BossesCRegistry.HEX_CAT.get(), CatRenderer::new);
    }
}
