package com.renanpontes.isekaimahou.feature.legends_a;

import com.renanpontes.isekaimahou.feature.legends_a.glyph.AllInAugment;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.ArsenalGateForm;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.BladeFieldEffect;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.ColossalAugment;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.ConjuredWeaponEffect;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.CraterEffect;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "legends_a": Magias Lendárias A (v0.7). Glifos Tudo ou Nada, Colossal, Explosão Colossal, Portão do Arsenal,
 * Arma Conjurada e Campo de Lâminas; entidades visuais (armas, portais, círculos no céu, explosão) e as lendas
 * "Explosão!", "Portão do Tesouro", "Campo de Lâminas Infinitas" e "Rugido do Dragão".
 */
public class LegendsAModule implements Module {
    @Override
    public String id() {
        return "legends_a";
    }

    @Override
    public void init(IEventBus modBus) {
        LegendsARegistry.register(modBus);

        GlyphRegistry.register(new AllInAugment());
        GlyphRegistry.register(new ColossalAugment());
        GlyphRegistry.register(new CraterEffect());
        GlyphRegistry.register(new ArsenalGateForm());
        GlyphRegistry.register(new ConjuredWeaponEffect());
        GlyphRegistry.register(new BladeFieldEffect());

        LegendRecipes.register();
        // Confere as lendas quando todos os módulos já registraram os glifos
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(LegendRecipes::validate));

        NeoForge.EVENT_BUS.addListener(LegendCasting::onPre);
        NeoForge.EVENT_BUS.addListener(LegendCasting::onPost);
        NeoForge.EVENT_BUS.addListener(LegendCasting::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LegendCasting::onLogout);
        NeoForge.EVENT_BUS.addListener(CraterQueue::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CraterQueue.clear();
            LegendCasting.clear();
        });
    }
}
