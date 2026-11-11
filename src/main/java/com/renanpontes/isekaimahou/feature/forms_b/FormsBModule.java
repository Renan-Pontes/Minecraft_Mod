package com.renanpontes.isekaimahou.feature.forms_b;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellIllusionEntity;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.AuraForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.ConjureForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.IllusionForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.OrbitForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.PortalForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.PulseForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.RuneForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.SealForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.TurretForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.WallForm;
import com.renanpontes.isekaimahou.feature.forms_b.glyph.ZoneForm;
import com.renanpontes.isekaimahou.feature.forms_b.seal.SealHandler;
import com.renanpontes.isekaimahou.feature.forms_b.util.AuraManager;
import com.renanpontes.isekaimahou.feature.forms_b.util.PersistentSpells;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Modulo "forms_b": formas neutras (Arcano) conjunto B — Zona, Muralha, Runa, Órbita, Aura, Pulso,
 * Torre, Invocação, Ilusão, Selo e Portal — com as entidades, o bloco de barreira e o componente do Selo.
 */
public class FormsBModule implements Module {
    @Override
    public String id() {
        return "forms_b";
    }

    @Override
    public void init(IEventBus modBus) {
        FormsBRegistry.register(modBus);
        modBus.addListener(FormsBModule::attributes);
        modBus.addListener(FormsBModule::creativeTab);

        GlyphRegistry.register(new ZoneForm());
        GlyphRegistry.register(new WallForm());
        GlyphRegistry.register(new RuneForm());
        GlyphRegistry.register(new OrbitForm());
        GlyphRegistry.register(new AuraForm());
        GlyphRegistry.register(new PulseForm());
        GlyphRegistry.register(new TurretForm());
        GlyphRegistry.register(new ConjureForm());
        GlyphRegistry.register(new IllusionForm());
        GlyphRegistry.register(new SealForm());
        GlyphRegistry.register(new PortalForm());

        NeoForge.EVENT_BUS.addListener(AuraManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SealHandler::onAttack);
        NeoForge.EVENT_BUS.addListener(SealHandler::onDamage);
        NeoForge.EVENT_BUS.addListener(FormsBModule::serverStopped);
    }

    /** Estado em memória (auras, controle de persistentes, golpes) não sobrevive ao fechar o mundo. */
    private static void serverStopped(ServerStoppedEvent event) {
        AuraManager.clear();
        PersistentSpells.clear();
        SealHandler.clear();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(FormsBRegistry.SPELL_ILLUSION.get(), SpellIllusionEntity.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            event.accept(FormsBRegistry.SPELL_BARRIER_ITEM.get());
        }
    }
}
