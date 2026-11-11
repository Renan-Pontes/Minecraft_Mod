package com.renanpontes.isekaimahou.feature.automation;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.automation.glyph.CreatureGlyphs;
import com.renanpontes.isekaimahou.feature.automation.glyph.FarmGlyphs;
import com.renanpontes.isekaimahou.feature.automation.glyph.ItemGlyphs;
import com.renanpontes.isekaimahou.feature.automation.glyph.WeatherGlyphs;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

/**
 * Módulo "automation" (v0.7): magia útil e farmável.
 * <ul>
 *   <li>Pedestal Rúnico: conjura sozinho a magia de um Pergaminho de Magia, gastando mana de Baterias.</li>
 *   <li>Bateria de Mana (20 000), Condensador de Mana (queima cristais/essências/lápis...) e Coletor Arcano.</li>
 *   <li>Glifos utilitários: Colheita Fértil, Recolher, Tosquiar, Reproduzir, Arar, Semear, Chamar Chuva,
 *       Céu Limpo, Chamar Tempestade e Forja Arcana.</li>
 * </ul>
 */
public class AutomationModule implements Module {
    @Override
    public String id() {
        return "automation";
    }

    @Override
    public void init(IEventBus modBus) {
        AutoRegistries.register(modBus);
        registerGlyphs();
        modBus.addListener(this::onCapabilities);
        modBus.addListener(this::onCreativeTab);
        NeoForge.EVENT_BUS.addListener(Pedestals::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Pedestals::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(Pedestals::onLightning);
        NeoForge.EVENT_BUS.addListener(Pedestals::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(Pedestals::onServerStopped);
    }

    private static void registerGlyphs() {
        GlyphRegistry.register(new FarmGlyphs.HarvestReplant());
        GlyphRegistry.register(new FarmGlyphs.Till());
        GlyphRegistry.register(new FarmGlyphs.Sow());
        GlyphRegistry.register(new ItemGlyphs.CollectItems());
        GlyphRegistry.register(new ItemGlyphs.SmeltItems());
        GlyphRegistry.register(new CreatureGlyphs.Shear());
        GlyphRegistry.register(new CreatureGlyphs.Breed());
        GlyphRegistry.register(new WeatherGlyphs.CallRain());
        GlyphRegistry.register(new WeatherGlyphs.ClearSky());
        GlyphRegistry.register(new WeatherGlyphs.CallThunder());
    }

    /** Funis e canos: o Coletor só entrega itens; o Condensador só recebe combustível. */
    private void onCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AutoRegistries.ARCANE_COLLECTOR_BE.get(),
                (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side));
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AutoRegistries.MANA_CONDENSER_BE.get(),
                (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side));
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        e.accept(AutoRegistries.RUNIC_PEDESTAL_ITEM.get());
        e.accept(AutoRegistries.MANA_BATTERY_ITEM.get());
        e.accept(AutoRegistries.MANA_BATTERY_ITEM.get().full());
        e.accept(AutoRegistries.MANA_CONDENSER_ITEM.get());
        e.accept(AutoRegistries.ARCANE_COLLECTOR_ITEM.get());
    }
}
