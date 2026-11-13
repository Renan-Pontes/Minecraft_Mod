package com.renanpontes.isekaimahou.feature.class_smith;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.glyph.SmithGlyphs;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitTier;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "class_smith" (v1.0): o FERREIRO e a Forja Arcana.
 * <ul>
 *   <li>Lâmina Forjada: uma arma que vira adaga/espada/espada longa/montante/machado/lança/martelo/foice, com comprimento,
 *   largura, curva, ponta, serrilha, empunhadura, acabamento, runa, material e qualidade — modelo 3D montado peça a peça;</li>
 *   <li>Bigorna Arcana (tela de montagem) e os glifos de forja (Alongar, Alargar, Afiar Ponta, Serrilhar, Fosquear,
 *   Gravar Runa, Reforjar) + Martelada, Chuva de Fagulhas, Metal Fundido e a forma G Forja Estelar;</li>
 *   <li>herda o caminho das lâminas (Campo de Lâminas, Arma Conjurada, Portão do Arsenal — origem Ferreiro);</li>
 *   <li>Provas da Forja (5 ranks, {@code ritual_rank}), bônus de classe, kit (martelo + lâmina + bigorna + roupa I),
 *   roupa de 4 graus (GeckoLib), 2 lendas, aba "Forja" no menu B.</li>
 * </ul>
 * Teste: escolha o Ferreiro, coloque a Bigorna Arcana do kit e clique nela; conjure "Alongar Lâmina" com a lâmina na
 * mão; {@code /mahou smith blade} dá uma lâmina de cada família; {@code /mahou smith rank 5}.
 */
public class ClassSmithModule implements Module {
    @Override
    public String id() {
        return "class_smith";
    }

    @Override
    public void init(IEventBus modBus) {
        SmithRegistries.register(modBus);
        SmithGlyphs.register();
        SmithLegends.register();
        modBus.addListener(SmithNet::register);
        modBus.addListener(SmithFxNet::register);
        modBus.addListener(this::creativeTabs);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(ClassSmithModule::validate));
        SmithEvents.init();
        SmithCommands.register();
        registerKit();
    }

    /** Kit do Ferreiro: martelo (o núcleo também garante), uma adaga forjada, a Bigorna Arcana, ferro e a roupa I. */
    private static void registerKit() {
        ClassKits.addItem(MagicPath.SMITH, () -> new ItemStack(SmithRegistries.SMITH_HAMMER.get()));
        ClassKits.addItem(MagicPath.SMITH, () -> ForgedBladeItem.create(ForgedBlade.DEFAULT.withPart(ForgedBlade.Part.LENGTH, 1)));
        ClassKits.addItem(MagicPath.SMITH, () -> new ItemStack(SmithRegistries.ARCANE_ANVIL_ITEM.get()));
        ClassKits.addItem(MagicPath.SMITH, () -> new ItemStack(Items.IRON_INGOT, 8));
        ClassKits.addItem(MagicPath.SMITH, () -> new ItemStack(Items.BREAD, 8));
        for (ArmorItem.Type type : SmithOutfitTier.TYPES) {
            ClassKits.setArmor(MagicPath.SMITH, type.getSlot(), () -> new ItemStack(SmithRegistries.piece(SmithOutfitTier.APPRENTICE, type).get()));
        }
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        CreativeModeTab.TabVisibility vis = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        e.accept(SmithRegistries.ARCANE_ANVIL_ITEM.get(), vis);
        e.accept(SmithRegistries.SMITH_HAMMER.get(), vis);
        SmithRegistries.HAMMER_TIERS.forEach(h -> e.accept(h.get(), vis));
        for (BladeMaterial m : BladeMaterial.VALUES) {
            e.accept(ForgedBladeItem.create(ForgedBlade.DEFAULT.withMaterial(m).withPart(ForgedBlade.Part.LENGTH, 2)), vis);
        }
        e.accept(SmithRegistries.STAR_METAL.get(), vis);
        for (SmithOutfitTier tier : SmithOutfitTier.VALUES) {
            for (ArmorItem.Type type : SmithOutfitTier.TYPES) e.accept(SmithRegistries.piece(tier, type).get(), vis);
        }
    }

    /** Confere as magias prontas e as lendas depois que TODOS os módulos registraram os glifos. */
    private static void validate() {
        SmithLegends.validate();
        java.util.List<SmithStarter.ClassSpell> all = new java.util.ArrayList<>(SmithStarter.STARTER);
        for (int r = 1; r <= SmithRanks.MAX_RANK; r++) all.addAll(SmithStarter.unlocked(r));
        for (SmithStarter.ClassSpell cs : all) {
            SpellRecipe recipe = cs.recipe("pt_br");
            SpellParser.Result res = SpellParser.parse(recipe, null, null);
            if (!res.ok()) IsekaiMahou.LOGGER.error("class_smith: magia pronta '{}' inválida: {}", recipe.name(), res.error().getString());
        }
    }
}
