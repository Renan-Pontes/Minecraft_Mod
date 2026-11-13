package com.renanpontes.isekaimahou.feature.class_enchanter;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.EnchanterGlyphs;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRite;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "class_enchanter" (v1.0) — a classe ENCANTADOR (Físicas, Vigor; afinidade Luz e Vento). Ver docs/V10_DESIGN.md §1.
 * <ul>
 *   <li><b>Encantamentos</b> (sistema da classe): 9 glifos de origem que empilham com limite em si e nos aliados — Força,
 *   Celeridade, Égide, Vitalidade, Imbuir Arma, Sobrecarga, Equipamento Encantado e os de assinatura ANTI-MAGIA e
 *   RESTRIÇÃO CELESTIAL (com níveis I–V); forma Elo de Encantamento (usa a Party) e forma G Santuário do Soberano;</li>
 *   <li><b>Rito de Inscrição</b>: 5 ranks numa mesa de encantamento com o Códex ({@code ritual_rank.enchanter} e
 *   {@code ritual_rank});</li>
 *   <li>kit: Manoplas do Encantador ({@code enchanter_gauntlets} + tiers I–IV), Códex, traje de 4 graus (GeckoLib) entregue
 *   nos Círculos 3/5/8; bônus de classe; 3 Magias Lendárias; aba "Encantador" no menu B.</li>
 * </ul>
 * Teste rápido: escolha o Encantador; {@code /mahou enchanter fill}, mesa de encantamento + oferenda e use o Códex nela;
 * {@code /mahou enchanter rank 5} libera tudo.
 */
public class ClassEnchanterModule implements Module {
    @Override
    public String id() {
        return "class_enchanter";
    }

    @Override
    public void init(IEventBus modBus) {
        EnchRegistries.register(modBus);
        modBus.addListener(EnchFxNet::register);
        EnchanterGlyphs.register();
        AntiMagic.register();
        HeavenlyRestriction.register();
        Transformations.register();
        Imbue.register();
        GearEnchant.register();
        EnchanterClass.register();
        EnchanterClass.registerKit();
        EnchanterRite.register();
        EnchCommands.register();
        EnchLegends.register();
        modBus.addListener(EnchNetwork::register);
        modBus.addListener(this::creativeTabs);
        modBus.addListener(this::setup);
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        CreativeModeTab.TabVisibility vis = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        e.accept(EnchRegistries.CODEX.get(), vis);
        e.accept(EnchRegistries.GAUNTLETS.get(), vis);
        for (var g : EnchRegistries.GAUNTLET_TIERS) e.accept(g.get(), vis);
        for (EnchanterOutfit t : EnchanterOutfit.values()) {
            for (ArmorItem.Type type : EnchRegistries.TYPES) e.accept(EnchRegistries.piece(t, type).get(), vis);
        }
    }

    /** Confere as magias iniciais e as lendas com o parser depois que todos os glifos foram registrados. */
    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            for (SpellRecipe r : EnchanterClass.allStarterRecipes()) {
                SpellParser.Result res = SpellParser.parse(r, null, null);
                if (!res.ok()) IsekaiMahou.LOGGER.error("class_enchanter: magia inicial '{}' inválida: {}", r.name(), res.error().getString());
            }
            EnchLegends.validate();
        });
    }
}
