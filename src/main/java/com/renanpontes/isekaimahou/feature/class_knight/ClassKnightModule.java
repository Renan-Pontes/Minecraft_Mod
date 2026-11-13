package com.renanpontes.isekaimahou.feature.class_knight;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitRewards;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "class_knight" (v1.0) — CAVALEIRO RÚNICO, sistema "Lâminas Rúnicas" (docs/V10_DESIGN.md §1).
 * <ul>
 *   <li>Gravar magias inteiras na espada ({@link RuneEngraving}): cada golpe solta a próxima runa.</li>
 *   <li>9 glifos de origem montáveis no Grimório ({@link KnightGlyphs}), incluindo a forma G <b>Excalibur</b>.</li>
 *   <li>6 Espadas Mágicas Lendárias ({@link LegendSword}) despertadas por feitos no Altar do Juramento.</li>
 *   <li>Ritual de 5 Juramentos ({@code oath.KnightOaths}) que grava {@code ritual_rank}.</li>
 *   <li>Lâmina Rúnica (arma do kit, graus I–IV), armadura de 4 graus (GeckoLib), bônus de classe e 2 lendas.</li>
 * </ul>
 */
public class ClassKnightModule implements Module {
    @Override
    public String id() {
        return "class_knight";
    }

    @Override
    public void init(IEventBus modBus) {
        KnightRegistries.register(modBus);
        modBus.addListener(KnightFxNet::register);
        KnightGlyphs.register();
        KnightEvents.register();
        KnightOutfitRewards.register();
        KnightCommands.register();

        // kit: Lâmina Rúnica (a arma exigida pela classe) + Altar do Juramento; a armadura entra no setup comum
        ClassKits.addItem(MagicPath.KNIGHT, () -> new ItemStack(KnightRegistries.RUNIC_BLADE.get()));
        ClassKits.addItem(MagicPath.KNIGHT, () -> new ItemStack(KnightRegistries.OATH_ALTAR_ITEM.get()));

        modBus.addListener(ClassKnightModule::onSetup);
        modBus.addListener(ClassKnightModule::onCreativeTabs);
    }

    private static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // depois de todos os módulos: a armadura do Cavaleiro substitui qualquer traje antigo da classe no kit
            KnightOutfitRewards.registerKitArmor();
            KnightLegends.register();
        });
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        for (var item : KnightRegistries.creativeOrder()) event.accept(item.get());
    }
}
