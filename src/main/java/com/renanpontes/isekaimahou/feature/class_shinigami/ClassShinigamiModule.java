package com.renanpontes.isekaimahou.feature.class_shinigami;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ZanpakutoSpiritEntity;
import com.renanpontes.isekaimahou.feature.class_shinigami.glyph.ShinigamiGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "class_shinigami" (v1.0) — a classe SHINIGAMI (homenagem a Bleach, com nomes e visuais próprios).
 * <ul>
 *   <li>Kit: a Zanpakutō ({@code zanpakuto}, espada ligada à alma: selada/Shikai/Bankai) + traje grau I (II–IV nos
 *   Círculos 3/5/8) + Doces Espirituais;</li>
 *   <li>13 glifos de origem (Zanjutsu, Shunpo, Kidō numerados — Hadō 4/31, Bakudō 61/81 —, Presa Lunar, os 5 efeitos de
 *   arquétipo da Shikai) + a forma G Bankai ({@link ShinigamiGlyphs}); magias iniciais nos slots;</li>
 *   <li>Mundo Interior ({@code isekai_mahou:inner_world}): meditação (Jinzen) leva à ilha pessoal, com paisagem pelo
 *   arquétipo, onde o espírito da espada conversa (Dialogue), dá as provas e luta para liberar a Shikai e a Bankai;</li>
 *   <li>Progressão própria: 5 ranks do Caminho da Zanpakutō ({@link ShinigamiRanks}, grava {@code ritual_rank});</li>
 *   <li>Bônus de classe ({@link ShinigamiEvents}): regeneração espiritual meditando, Reiatsu na lâmina, Hohō, poder por
 *   rank, lâmina que não se perde;</li>
 *   <li>Técnicas avançadas ({@link ShinigamiTechniques}): Getsuga Tenshō, Hadō nº 90 Kurohitsugi, Máscara do Vazio +
 *   Cero (vencendo o Vazio Interior no Mundo Interior), Vasto Lorde e Mugetsu (formas G);</li>
 *   <li>5 Magias Lendárias ({@link ShinigamiLegends}); aba "Shinigami" no menu B (cliente).</li>
 * </ul>
 */
public class ClassShinigamiModule implements Module {
    @Override
    public String id() {
        return Shinigami.MODULE;
    }

    @Override
    public void init(IEventBus modBus) {
        ShinigamiRegistries.register(modBus);
        ShinigamiGlyphs.register();
        ShinigamiLegends.register();
        modBus.addListener(ShinigamiLegends::validate);
        modBus.addListener(ShinigamiNetwork::register);
        modBus.addListener(ShinigamiFxNet::register);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);

        // Kit: a zanpakutō (arma do kit), Doces Espirituais e o traje grau I vestido
        ClassKits.addItem(MagicPath.SHINIGAMI, () -> new ItemStack(ShinigamiRegistries.ZANPAKUTO.get()));
        ClassKits.addItem(MagicPath.SHINIGAMI, () -> new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 3));
        for (ArmorItem.Type type : ShinigamiArmor.TYPES) {
            ClassKits.setArmor(MagicPath.SHINIGAMI, type.getSlot(),
                    () -> new ItemStack(ShinigamiArmor.piece(ShinigamiArmor.Tier.APPRENTICE, type).get()));
        }

        ShinigamiEvents.register();
        ShinigamiArmor.registerEvents();
        ShinigamiCommands.register();
    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        e.put(ShinigamiRegistries.SPIRIT.get(), ZanpakutoSpiritEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            for (ItemStack s : ShinigamiRegistries.creativeItems()) e.accept(s);
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(ShinigamiRegistries.SPIRIT_EGG.get());
        }
    }
}
