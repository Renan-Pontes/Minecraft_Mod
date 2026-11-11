package com.renanpontes.isekaimahou.feature.class_quincy;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.QuincyNpcEntity;
import com.renanpontes.isekaimahou.feature.class_quincy.glyph.QuincyGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "class_quincy" (v1.0) — a classe QUINCY (homenagem a Bleach, com nomes e visuais próprios).
 * <ul>
 *   <li>Kit: Cruz Quincy ({@code quincy_cross}, vira um Arco Espiritual) + traje Quincy grau I (II–IV nos Círculos 3/5/8);</li>
 *   <li>10 glifos de origem + a forma G Vollständig ({@link QuincyGlyphs}), magias iniciais nos slots;</li>
 *   <li>Absorção: passiva (engole projéteis) e a forma Absorção (devolve tudo como flechas);</li>
 *   <li>Progressão própria: 5 ranks do Auswählen ({@link QuincyRanks}) — absorver o poder de NPCs Quincy rendidos nos
 *   acampamentos ({@code quincy_camp}) e de jogadores Quincy que aceitarem ({@link Auswahlen}); Letras no rank III;</li>
 *   <li>Técnicas avançadas ({@link QuincyTechniques}): Heilig Bogen, Sankt Zwinger, A Antítese, Quincy: Letzt Stil (treino
 *   com a Luva Sanrei) e O Todo-Poderoso (formas G); 10 Letras (5 novas: H, J, X, B, D);</li>
 *   <li>5 Magias Lendárias ({@link QuincyLegends}); aba "Quincy" no menu B (cliente).</li>
 * </ul>
 */
public class ClassQuincyModule implements Module {
    @Override
    public String id() {
        return Quincy.MODULE;
    }

    @Override
    public void init(IEventBus modBus) {
        QuincyRegistries.register(modBus);
        QuincyGlyphs.register();
        QuincyLegends.register();
        modBus.addListener(QuincyLegends::validate);
        modBus.addListener(QuincyNetwork::register);
        modBus.addListener(QuincyFxNet::register);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);

        // Kit: a Cruz (arma do kit) e o traje grau I vestido
        ClassKits.addItem(MagicPath.QUINCY, () -> new ItemStack(QuincyRegistries.CROSS.get()));
        ClassKits.addItem(MagicPath.QUINCY, () -> new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 4));
        for (ArmorItem.Type type : QuincyArmor.TYPES) {
            ClassKits.setArmor(MagicPath.QUINCY, type.getSlot(),
                    () -> new ItemStack(QuincyArmor.piece(QuincyArmor.Tier.APPRENTICE, type).get()));
        }
        // Puxar o Arco Espiritual conta como "mirando" (Foco parado e agachado)
        ClassResources.addAimItem(QuincyRegistries::isCross);

        QuincyEvents.register();
        QuincyArmor.registerEvents();
        QuincyCommands.register();
    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        e.put(QuincyRegistries.QUINCY_NPC.get(), QuincyNpcEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            for (ItemStack s : QuincyRegistries.creativeItems()) e.accept(s);
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(QuincyRegistries.NPC_EGG.get());
        }
    }
}
