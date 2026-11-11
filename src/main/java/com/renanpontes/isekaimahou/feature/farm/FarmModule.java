package com.renanpontes.isekaimahou.feature.farm;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.farm.blueprint.BlueprintKind;
import com.renanpontes.isekaimahou.feature.farm.build.BuildQueue;
import com.renanpontes.isekaimahou.feature.farm.glyph.BuildGlyphs;
import com.renanpontes.isekaimahou.feature.farm.heist.ShrineHeist;
import com.renanpontes.isekaimahou.feature.farm.machine.MachineKind;
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
 * Módulo "farm" (v1.0) — "melhorar o sistema de FARM":
 * <ul>
 *   <li><b>Construção Arcana</b>: Varinha de Construção (preencher, parede, piso, trocar, copiar/colar, desfazer) e 5
 *       glifos montáveis no Grimório (Erguer Parede, Pavimentar, Preencher, Transmutar Blocos, Extração Arcana);</li>
 *   <li><b>Plantas de Casa</b>: Casa Pequena, Torre, Celeiro, Ponte e Horta — um Canteiro de Obras que se constrói
 *       sozinho com os blocos dos baús em volta;</li>
 *   <li><b>Fazendas mágicas</b> de materiais vanilla: Gerador de Pedra/Minério, Árvore Acelerada, Criadouro Arcano e
 *       Lago de Peixes — com mana das Baterias da Automação (por id);</li>
 *   <li><b>Essências</b>: o altar dos Santuários roubado com Toque Suave vira o Pedestal Elemental (Gerador de Essência).</li>
 * </ul>
 */
public class FarmModule implements Module {
    @Override
    public String id() {
        return "farm";
    }

    @Override
    public void init(IEventBus modBus) {
        FarmRegistries.register(modBus);
        GlyphRegistry.register(new BuildGlyphs.ArcaneWall());
        GlyphRegistry.register(new BuildGlyphs.ArcaneFloor());
        GlyphRegistry.register(new BuildGlyphs.ArcaneFill());
        GlyphRegistry.register(new BuildGlyphs.ArcaneTransmute());
        GlyphRegistry.register(new BuildGlyphs.ArcaneExtract());
        modBus.addListener(this::onCapabilities);
        modBus.addListener(this::onCreativeTab);
        NeoForge.EVENT_BUS.addListener(BuildQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(BuildQueue::onLogout);
        NeoForge.EVENT_BUS.addListener(BuildQueue::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ShrineHeist::onLeftClick);
        NeoForge.EVENT_BUS.addListener(ShrineHeist::onServerStopped);
    }

    /** Funis e canos: entrada/combustível por cima e pelos lados; produção sai por baixo e pelos lados. */
    private void onCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FarmRegistries.MACHINE_BE.get(),
                (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side));
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        e.accept(FarmRegistries.CONSTRUCTION_WAND.get());
        for (BlueprintKind k : BlueprintKind.values()) e.accept(FarmRegistries.BLUEPRINTS.get(k).get());
        for (MachineKind k : MachineKind.values()) {
            if (k == MachineKind.ESSENCE) {
                for (Element el : Element.BASE) e.accept(FarmRegistries.pedestalStack(el));
            } else {
                e.accept(FarmRegistries.MACHINE_ITEMS.get(k).get());
            }
        }
    }
}
