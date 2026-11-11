package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Parte de cliente do modulo "bosses_a": renderers dos Guardiões e das entidades dos ataques. */
public class BossesAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(BossesAClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BossesARegistry.IGNIS.get(), ctx -> new GuardianRenderer<>(ctx, new IgnisModel(), 1.0F, 1.5F));
        e.registerEntityRenderer(BossesARegistry.TIDE_GUARDIAN.get(),
                ctx -> new GuardianRenderer<>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("tide_guardian")), 1.0F, 1.3F));
        e.registerEntityRenderer(BossesARegistry.PRIMORDIAL_GOLEM.get(), ctx -> new GuardianRenderer<>(ctx, new GolemModel(), 1.25F, 2.2F));
        e.registerEntityRenderer(BossesARegistry.ZEPHYR.get(),
                ctx -> new GuardianRenderer<>(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("zephyr")), 1.0F, 1.0F));

        e.registerEntityRenderer(BossesARegistry.SHOCKWAVE.get(), NoopRenderer::new);
        e.registerEntityRenderer(BossesARegistry.METEOR.get(),
                ctx -> new BlockProjectileRenderer<>(ctx, m -> Blocks.MAGMA_BLOCK.defaultBlockState(), m -> m.size() * 1.3F, true));
        e.registerEntityRenderer(BossesARegistry.BOULDER.get(),
                ctx -> new BlockProjectileRenderer<>(ctx, b -> b.size() >= 1F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), b -> b.size() * 1.3F, false));
        e.registerEntityRenderer(BossesARegistry.LAVA_POOL.get(), LavaPoolRenderer::new);
        e.registerEntityRenderer(BossesARegistry.TORNADO.get(),
                // Modelos menores que o efeito real: o tornado é desenhado 1.6x e o redemoinho 2x
                ctx -> new VortexRenderer<>(ctx, "zephyr_tornado", (t, p) -> 1.6F * Math.min(1F, (t.tickCount + p) / 10F), true));
        e.registerEntityRenderer(BossesARegistry.WHIRLPOOL.get(),
                ctx -> new VortexRenderer<>(ctx, "tide_whirlpool", (w, p) -> 2F * w.openness(p), false));
        // Bolha translúcida (modelo de 1 bloco desenhado 2.2x: cobre o jogador inteiro), brilho total para ser vista
        e.registerEntityRenderer(BossesARegistry.TIDE_BUBBLE.get(),
                ctx -> new VortexRenderer<>(ctx, "tide_bubble", (b, p) -> 2.2F * b.visualScale(p), true));
        e.registerEntityRenderer(BossesARegistry.GOLEM_PILLAR.get(), PillarRenderer::new);
        e.registerEntityRenderer(BossesARegistry.GOLEM_SLAB.get(), SlabRenderer::new);
        e.registerEntityRenderer(BossesARegistry.ZEPHYR_CURTAIN.get(), CurtainRenderer::new);
    }
}
