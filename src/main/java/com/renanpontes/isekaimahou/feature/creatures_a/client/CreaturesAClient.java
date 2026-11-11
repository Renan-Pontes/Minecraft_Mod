package com.renanpontes.isekaimahou.feature.creatures_a.client;

import com.renanpontes.isekaimahou.feature.creatures_a.CaRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "creatures_a": renderers GeckoLib dos slimes e goblins. */
public class CreaturesAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CaRegistry.SLIME.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.FIRE_SLIME.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.WATER_SLIME.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.STONE_SLIME.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.WIND_SLIME.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.POISON_JELLY.get(), MahouSlimeRenderer::new);
        e.registerEntityRenderer(CaRegistry.KING_SLIME.get(), MahouSlimeRenderer::new);

        // Escalas: goblins ~1,2 bloco; hobgoblin ~1,9; rei ~2,6. Itens ficam menores na mão de goblins pequenos.
        e.registerEntityRenderer(CaRegistry.GOBLIN.get(), ctx -> new GoblinRenderer(ctx, 1.0F, 0.4F, 0.75F));
        e.registerEntityRenderer(CaRegistry.GOBLIN_ARCHER.get(), ctx -> new GoblinRenderer(ctx, 1.0F, 0.4F, 0.75F));
        e.registerEntityRenderer(CaRegistry.GOBLIN_SHAMAN.get(), ctx -> new GoblinRenderer(ctx, 1.0F, 0.4F, 0.75F));
        e.registerEntityRenderer(CaRegistry.GOBLIN_WOLF_RIDER.get(), ctx -> new GoblinRenderer(ctx, 1.0F, 0.7F, 0.75F));
        e.registerEntityRenderer(CaRegistry.HOBGOBLIN.get(), ctx -> new GoblinRenderer(ctx, 1.55F, 0.6F, 0.65F));
        e.registerEntityRenderer(CaRegistry.GOBLIN_KING.get(), ctx -> new GoblinRenderer(ctx, 1.8F, 0.9F, 0.6F));
    }
}
