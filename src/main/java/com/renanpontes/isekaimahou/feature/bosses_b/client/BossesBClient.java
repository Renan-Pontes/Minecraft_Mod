package com.renanpontes.isekaimahou.feature.bosses_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_b.BbRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Parte de cliente do modulo "bosses_b": renderers GeckoLib dos chefes e projéteis, névoa do Eclipse/Domínio. */
public class BossesBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::renderers);
        NeoForge.EVENT_BUS.addListener(BbAtmosphere::tick);
        NeoForge.EVENT_BUS.addListener(BbAtmosphere::fogColor);
        NeoForge.EVENT_BUS.addListener(BbAtmosphere::fog);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BbRegistries.ABYSS_SOVEREIGN.get(),
                ctx -> new BbBossRenderer<>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("abyss_sovereign"), true), 1.0F, 1.2F));
        e.registerEntityRenderer(BbRegistries.SERAPH.get(),
                ctx -> new BbBossRenderer<>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("seraph"), true), 1.0F, 0.9F));
        e.registerEntityRenderer(BbRegistries.TWILIGHT_HERALD.get(),
                ctx -> new BbBossRenderer<>(ctx, new HeraldModel(), 1.0F, 1.0F));
        e.registerEntityRenderer(BbRegistries.ABYSSAL_ORB.get(), ctx -> new BoltRenderer(ctx, "abyssal_orb"));
        e.registerEntityRenderer(BbRegistries.CELESTIAL_LANCE.get(), ctx -> new BoltRenderer(ctx, "celestial_lance"));
        e.registerEntityRenderer(BbRegistries.TWILIGHT_ORB.get(), ctx -> new BoltRenderer(ctx, "twilight_orb"));
    }
}
