package com.renanpontes.isekaimahou.feature.origins_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.origins_b.entity.BoneWarriorEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedDemonEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedImpEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.UndeadServantEntity;
import com.renanpontes.isekaimahou.feature.origins_b.glyph.NecroGlyphs;
import com.renanpontes.isekaimahou.feature.origins_b.glyph.PactGlyphs;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "origins_b": Magias de Origem do Warlock (PACTO) e do Necromante (NECROMANCIA).
 * <ul>
 *   <li>Pacto: Invocar Diabrete (T1), Arma Sombria (T2), Contrato de Morte (T3), Asas Demoníacas (T4), Invocar Demônio Maior (T5).</li>
 *   <li>Necromancia: Erguer Morto (T1), Escudo de Ossos (T2), Drenar Alma (T3), Explodir Cadáver (T3), Exército de Ossos (T4),
 *       Forma de Lich (T5).</li>
 *   <li>Invocações GeckoLib: summoned_imp, summoned_demon, undead_servant, bone_warrior (+ projétil imp_fireball).</li>
 *   <li>Item shadow_blade e efeitos bone_shield, death_contract, demon_wings, lich_form.</li>
 * </ul>
 * Os glifos são ensinados pelos rituais (ritual_warlock/ritual_necro), que os encontram por {@code origin()} e {@code tier()}.
 */
public class OriginsBModule implements Module {
    @Override
    public String id() {
        return "origins_b";
    }

    @Override
    public void init(IEventBus modBus) {
        ObRegistries.register(modBus);
        modBus.addListener(OriginsBModule::attributes);
        modBus.addListener(OriginsBModule::creativeTab);

        for (EffectGlyph g : PactGlyphs.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : NecroGlyphs.all()) GlyphRegistry.register(g);

        // Demônios: imunes a fogo, sofrem com a Luz. Mortos-vivos: resistem às trevas, sofrem com a Luz
        // (a tag minecraft:undead já dá ×1.5 contra Luz no núcleo; 1.35 aqui ≈ ×2 no total).
        for (String id : new String[] {"summoned_imp", "summoned_demon"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.FIRE, 0.0F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 1.75F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.WATER, 1.25F);
        }
        for (String id : new String[] {"undead_servant", "bone_warrior"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 1.35F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.SHADOW, 0.5F);
        }

        ObEvents.register();
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(ObRegistries.SUMMONED_IMP.get(), SummonedImpEntity.createAttributes().build());
        e.put(ObRegistries.SUMMONED_DEMON.get(), SummonedDemonEntity.createAttributes().build());
        e.put(ObRegistries.UNDEAD_SERVANT.get(), UndeadServantEntity.createAttributes().build());
        e.put(ObRegistries.BONE_WARRIOR.get(), BoneWarriorEntity.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(ObRegistries.SUMMONED_IMP_EGG.get());
            e.accept(ObRegistries.SUMMONED_DEMON_EGG.get());
            e.accept(ObRegistries.UNDEAD_SERVANT_EGG.get());
            e.accept(ObRegistries.BONE_WARRIOR_EGG.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            e.accept(ObRegistries.SHADOW_BLADE.get());
        }
    }
}
