package com.renanpontes.isekaimahou.feature.dwarf_hold;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.dwarf_hold.entity.DwarfEntity;
import com.renanpontes.isekaimahou.feature.dwarf_hold.item.HeroShieldItem;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Runes;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Módulo "dwarf_hold" — v1.6 (docs/V16_DESIGN.md): a <b>Cidade Anã Perdida</b> ({@code isekai_mahou:dwarf_hold}, estrutura
 * grande no deepslate, y≈−45: portão de pedra, Grande Salão, forja, mercado, minas e o Cofre Selado), os anões
 * (Comerciante e Ferreiro), o <b>Mithril</b> (minério raro abaixo de y 0, lingote, ferramentas, armadura, Machado de
 * Guerra), a <b>Forja Rúnica</b> (runas: bônus pequenos fixos por essência) e os tesouros de anime: <b>Escudo do Herói</b>
 * (devolve dano; Escudo da Ira) e <b>Monóculo de Avaliação</b> (curio "Appraisal").
 * Dados (molde, worldgen, loot, receitas, tags, texturas, modelos, traduções): {@code tools/gen/dwarf_hold.py}.
 * <pre>
 * /locate structure isekai_mahou:dwarf_hold      ·  /mahou dwarf_hold locate | tp | give | spawn merchant|smith | rune &lt;id&gt;
 * </pre>
 */
public class DwarfHoldModule implements Module {
    @Override
    public String id() {
        return "dwarf_hold";
    }

    @Override
    public void init(IEventBus modBus) {
        DwarfRegistry.register(modBus);
        modBus.addListener(DwarfHoldModule::creativeTabs);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> {
            e.put(DwarfRegistry.DWARF_MERCHANT.get(), DwarfEntity.createAttributes().build());
            e.put(DwarfRegistry.DWARF_SMITH.get(), DwarfEntity.createAttributes().build());
        });
        NeoForge.EVENT_BUS.addListener(ItemAttributeModifierEvent.class, Runes::onAttributes);
        NeoForge.EVENT_BUS.addListener(LivingShieldBlockEvent.class, HeroShieldItem::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, DwarfHoldEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, DwarfHoldEvents::onBreak);
        // anões são de pedra e fogo da forja: resistem a Terra e Fogo (aparece no Monóculo)
        for (String id : new String[]{"dwarf_merchant", "dwarf_smith"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.EARTH, 0.5F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.FIRE, 0.75F);
        }
        DwarfCommands.init();
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            for (var item : DwarfRegistry.worldTab()) e.accept(item.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(DwarfRegistry.DWARF_MERCHANT_EGG.get());
            e.accept(DwarfRegistry.DWARF_SMITH_EGG.get());
        }
    }
}
