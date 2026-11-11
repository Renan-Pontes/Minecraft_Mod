package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Parte de cliente do módulo "hueco_mundo": renderers GeckoLib, portais, projéteis e os céus dos dois reinos. */
public class HuecoMundoClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        modBus.addListener((RegisterDimensionSpecialEffectsEvent e) -> {
            e.register(IsekaiMahou.id("hollow_world"), new RealmSkyEffects(RealmSkyEffects.Style.HOLLOW));
            e.register(IsekaiMahou.id("fey_realm"), new RealmSkyEffects(RealmSkyEffects.Style.FEY));
        });
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(HmRegistries.HOLLOW_CRAWLER.get(), ctx -> new HmGeoRenderer<>(ctx, "hollow_crawler", true, 1.0F, 0.7F));
        e.registerEntityRenderer(HmRegistries.HOLLOW_BRUTE.get(), ctx -> new HmGeoRenderer<>(ctx, "hollow_brute", true, 1.0F, 1.0F));
        e.registerEntityRenderer(HmRegistries.HOLLOW_WRAITH.get(), ctx -> new HmGeoRenderer<>(ctx, "hollow_wraith", false, 1.0F, 0.6F));
        e.registerEntityRenderer(HmRegistries.MENOS_GIANT.get(), ctx -> new HmGeoRenderer<>(ctx, "menos_giant", false, 1.0F, 1.6F));
        e.registerEntityRenderer(HmRegistries.ARRANCAR.get(), ctx -> new HmGeoRenderer<>(ctx, "arrancar", true, 1.0F, 0.5F));
        e.registerEntityRenderer(HmRegistries.FEY_PIXIE.get(), ctx -> new HmGeoRenderer<>(ctx, "fey_pixie", true, 1.0F, 0.25F));
        e.registerEntityRenderer(HmRegistries.BRAMBLE_HOUND.get(), ctx -> new HmGeoRenderer<>(ctx, "bramble_hound", true, 1.0F, 0.5F));
        e.registerEntityRenderer(HmRegistries.HOLLOW_KING.get(), ctx -> new HmGeoRenderer<>(ctx, "hollow_king", true, 1.0F, 1.2F));
        e.registerEntityRenderer(HmRegistries.SAND_EMPRESS.get(), ctx -> new HmGeoRenderer<>(ctx, "sand_empress", true, 1.0F, 0.8F));
        e.registerEntityRenderer(HmRegistries.THORN_QUEEN.get(), ctx -> new HmGeoRenderer<>(ctx, "thorn_queen", true, 1.0F, 1.0F));
        e.registerEntityRenderer(HmRegistries.REALM_GATE.get(), RealmGateRenderer::new);
        e.registerEntityRenderer(HmRegistries.HM_BOLT.get(), HmBoltRenderer::new);
    }
}
