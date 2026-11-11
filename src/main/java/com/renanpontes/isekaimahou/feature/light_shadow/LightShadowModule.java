package com.renanpontes.isekaimahou.feature.light_shadow;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.GuardianFairyEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.LivingShadowEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.glyph.LightEffects;
import com.renanpontes.isekaimahou.feature.light_shadow.glyph.ShadowEffects;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "light_shadow": glifos de efeito de LUZ e SOMBRA (14 + 14), os efeitos de status que eles usam,
 * as invocações (Fada Guardiã, Sombra Viva) e as zonas sagradas (Barreira Sagrada, Santuário).
 */
public class LightShadowModule implements Module {
    @Override
    public String id() {
        return "light_shadow";
    }

    @Override
    public void init(IEventBus modBus) {
        LsRegistries.register(modBus);
        modBus.addListener(LightShadowModule::attributes);
        modBus.addListener(LightShadowModule::creativeTab);

        for (EffectGlyph g : LightEffects.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : ShadowEffects.all()) GlyphRegistry.register(g);

        // Resistências/fraquezas das invocações
        ElementReactions.setMultiplier(IsekaiMahou.id("living_shadow"), Element.LIGHT, 2.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("living_shadow"), Element.SHADOW, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_guardian_fairy"), Element.LIGHT, 0.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_guardian_fairy"), Element.SHADOW, 1.5F);

        LsEvents.register();
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(LsRegistries.GUARDIAN_FAIRY.get(), GuardianFairyEntity.createAttributes().build());
        e.put(LsRegistries.LIVING_SHADOW.get(), LivingShadowEntity.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(LsRegistries.GUARDIAN_FAIRY_EGG.get());
            e.accept(LsRegistries.LIVING_SHADOW_EGG.get());
        }
    }
}
