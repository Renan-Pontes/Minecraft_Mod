package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlock;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do modulo "forms_b": renderers das entidades, cor da barreira, tooltip e brilho do Selo. */
public class FormsBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FormsBClient::renderers);
        modBus.addListener(FormsBClient::blockColors);
        modBus.addListener(FormsBClient::itemColors);
        NeoForge.EVENT_BUS.addListener(SealClient::tooltip);
        NeoForge.EVENT_BUS.addListener(SealClient::tick);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FormsBRegistry.SPELL_ZONE.get(), ZoneRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_WALL.get(), NoopRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_RUNE.get(), RuneRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_ORB.get(), OrbRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_TURRET.get(), TurretRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_CONJURER.get(), ConjurerRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_ILLUSION.get(), IllusionRenderer::new);
        e.registerEntityRenderer(FormsBRegistry.SPELL_PORTAL.get(), PortalRenderer::new);
    }

    /** A barreira é tingida pela cor do elemento guardado no estado do bloco. */
    private static void blockColors(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tint) -> {
            Element[] v = Element.values();
            int i = state.getValue(SpellBarrierBlock.ELEMENT);
            return i >= 0 && i < v.length ? v[i].color : Element.ARCANE.color;
        }, FormsBRegistry.SPELL_BARRIER.get());
    }

    private static void itemColors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, tint) -> Element.ARCANE.argb(), FormsBRegistry.SPELL_BARRIER_ITEM.get());
    }
}
