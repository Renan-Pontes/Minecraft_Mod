package com.renanpontes.isekaimahou.feature.creatures_d.client;

import com.renanpontes.isekaimahou.feature.creatures_d.CdEntities;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "creatures_d": renderers GeckoLib das criaturas e do projétil elemental. */
public class CreaturesDClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::renderers);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CdEntities.SALAMANDER.get(), ctx -> new ElementalRenderer<>(ctx, "salamander", true, 0.5F).glowing());
        e.registerEntityRenderer(CdEntities.EMBER_IMP.get(), ctx -> new ElementalRenderer<>(ctx, "ember_imp", true, 0.35F).glowing());
        e.registerEntityRenderer(CdEntities.UNDINE.get(), ctx -> new ElementalRenderer<>(ctx, "undine", false, 0.4F).translucent().glowing());
        e.registerEntityRenderer(CdEntities.TIDE_SERPENT.get(), ctx -> new ElementalRenderer<>(ctx, "tide_serpent", false, 0.7F).glowing());
        e.registerEntityRenderer(CdEntities.MOSS_GOLEM.get(), ctx -> new ElementalRenderer<>(ctx, "moss_golem", true, 0.9F).glowing());
        e.registerEntityRenderer(CdEntities.CRYSTAL_MOLE.get(), ctx -> new ElementalRenderer<>(ctx, "crystal_mole", false, 0.45F).glowing());
        e.registerEntityRenderer(CdEntities.SYLPH.get(), ctx -> new ElementalRenderer<>(ctx, "sylph", true, 0.3F).translucent().fullBright().glowing());
        e.registerEntityRenderer(CdEntities.STORM_HAWK.get(), ctx -> new ElementalRenderer<>(ctx, "storm_hawk", true, 0.5F).glowing());
        e.registerEntityRenderer(CdEntities.LUMEN_SPRITE.get(), ctx -> new ElementalRenderer<>(ctx, "lumen_sprite", false, 0.2F).translucent().fullBright().glowing());
        e.registerEntityRenderer(CdEntities.SOLAR_SENTINEL.get(), ctx -> new ElementalRenderer<>(ctx, "solar_sentinel", false, 0.6F).scaled(1.1F).glowing());
        e.registerEntityRenderer(CdEntities.CREEPING_SHADE.get(), ctx -> new ElementalRenderer<>(ctx, "creeping_shade", false, 0.5F).translucent().glowing());
        e.registerEntityRenderer(CdEntities.VOID_BAT.get(), ctx -> new ElementalRenderer<>(ctx, "void_bat", true, 0.3F).glowing());
        e.registerEntityRenderer(CdEntities.WANDERING_SPIRIT.get(), ctx -> new ElementalRenderer<>(ctx, "wandering_spirit", true, 0.3F).translucent().fullBright().glowing());
        e.registerEntityRenderer(CdEntities.ARCANE_GOLEM.get(), ctx -> new ElementalRenderer<>(ctx, "arcane_golem", true, 1.0F).glowing());
        e.registerEntityRenderer(CdEntities.ELEMENTAL_BOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.9F, true));
    }
}
