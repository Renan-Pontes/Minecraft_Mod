package com.renanpontes.isekaimahou.feature.legends_d;

import com.renanpontes.isekaimahou.feature.legends_d.atomic.AtomicBubble;
import com.renanpontes.isekaimahou.feature.legends_d.entity.AtomicNovaEntity;
import com.renanpontes.isekaimahou.feature.legends_d.entity.DisintegrationCageEntity;
import com.renanpontes.isekaimahou.feature.legends_d.glyph.AtomicEffect;
import com.renanpontes.isekaimahou.feature.legends_d.glyph.DisintegrationEffect;
import com.renanpontes.isekaimahou.feature.legends_d.glyph.GoatifyEffect;
import com.renanpontes.isekaimahou.feature.legends_d.glyph.TimeStopForm;
import com.renanpontes.isekaimahou.feature.legends_d.goat.Goatify;
import com.renanpontes.isekaimahou.feature.legends_d.timestop.TimeStop;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_d" (v0.8): Atômico, Desintegração, Parar o Tempo (forma G) e Transmutar em Cabra.
 * Lendas: Eu Sou Atômico, Alcance Total, Desintegração, O Mundo, Maldição da Cabra. Ver docs/V08_DESIGN.md §7.2.
 */
public class LegendsDModule implements Module {
    @Override
    public String id() {
        return "legends_d";
    }

    @Override
    public void init(IEventBus modBus) {
        LdRegistry.register(modBus);
        modBus.addListener(LdNet::register);
        modBus.addListener(LegendRecipes::validate);

        GlyphRegistry.register(new AtomicEffect());
        GlyphRegistry.register(new DisintegrationEffect());
        GlyphRegistry.register(new TimeStopForm());
        GlyphRegistry.register(new GoatifyEffect());
        LegendRecipes.register();

        IEventBus bus = NeoForge.EVENT_BUS;
        // Parar o Tempo
        bus.addListener(TimeStop::onEntityTick);
        bus.addListener(TimeStop::onJoin);
        bus.addListener(EventPriority.HIGH, TimeStop::onIncomingDamage);
        bus.addListener(TimeStop::onAttack);
        bus.addListener(TimeStop::onRightClickItem);
        bus.addListener(TimeStop::onRightClickBlock);
        bus.addListener(TimeStop::onLeftClickBlock);
        bus.addListener(TimeStop::onEntityInteract);
        bus.addListener(TimeStop::onUseStart);
        bus.addListener(TimeStop::onCast);
        bus.addListener(TimeStop::onLogin);
        bus.addListener(TimeStop::onLevelUnload);
        bus.addListener(TimeStop::onServerTick);
        // Atômico: o conjurador não sofre com a própria explosão (nem com a queda/escombros/fogo da cratera)
        bus.addListener(EventPriority.HIGH, LegendsDModule::atomicImmunity);
        // Atômico: bolha protetora dos aliados (sem dano, sem empurrão, flutua se o chão sumir e desce devagar)
        bus.addListener(EventPriority.HIGH, AtomicBubble::onIncomingDamage);
        bus.addListener(EventPriority.HIGH, AtomicBubble::onKnockBack);
        bus.addListener(AtomicBubble::tick);
        bus.addListener(AtomicBubble::onJoin);
        // Cabra
        bus.addListener(Goatify::onJoin);
        bus.addListener(Goatify::onLeave);
        bus.addListener(EventPriority.HIGH, Goatify::onDeath);
        bus.addListener(Goatify::onAttack);
        bus.addListener(Goatify::onCast);
        bus.addListener(Goatify::onStartTracking);
        bus.addListener(Goatify::onLogin);
        bus.addListener(Goatify::onRespawn);
        bus.addListener(Goatify::onChangeDimension);
        bus.addListener(Goatify::onEffectRemoved);
        bus.addListener(Goatify::onEffectExpired);
        bus.addListener(Goatify::tick);
        // Limpeza
        bus.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 200 == 0) DisintegrationEffect.prune(e.getServer().overworld().getGameTime());
        });
        bus.addListener((ServerStoppedEvent e) -> {
            TimeStop.clearAll();
            Goatify.clear();
            AtomicNovaEntity.clearAll();
            AtomicBubble.clear();
            DisintegrationCageEntity.clearAll();
            AtomicEffect.clear();
            DisintegrationEffect.clear();
        });
    }

    private static void atomicImmunity(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || !AtomicNovaEntity.immune(victim)) return;
        // Vazio/kill/comandos continuam valendo (nunca deixar alguém caindo no vazio para sempre)
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = e.getSource().getEntity();
        if (src == null || src == victim) e.setCanceled(true);
    }
}
