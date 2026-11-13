package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.CoordinatedStrike;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.EngravedRune;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.GoldenSpin;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.RussianRoulette;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.ShunpoCast;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.Sklaverei;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment.TrickRicochet;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.glyph.GcbGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "glyphs_classes_b" — v1.4: um AUMENTO e um GATILHO novos para Encantador, Shinigami, Quincy, Pistoleiro,
 * Patrulheiro, Apostador e Steel Ball (14 glifos de origem; ver {@link GcbGlyphs}).
 * <ul>
 *   <li>Aprender: a própria classe pelo rank ({@link ClassUnlocks}); as outras só por Lição (regra do núcleo para
 *   glifos de origem).</li>
 *   <li>Gatilhos: {@link TriggerEvents} (condições) + {@link TriggerCaster} (conjuração automática).</li>
 *   <li>Aumentos: pacote {@code augment}.</li>
 *   <li>Teste: {@code /mahou glyphs grant glyphs_classes_b [jogadores]}.</li>
 * </ul>
 */
public class GlyphsClassesBModule implements Module {
    @Override
    public String id() {
        return GcbIds.MODULE;
    }

    @Override
    public void init(IEventBus modBus) {
        GcbGlyphs.register();
        GcbCommands.register();

        // Gatilhos: a tecla não conjura; condições
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpellCastEvent.Pre.class, TriggerCaster::onPre);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, TriggerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.Counter.class, TriggerEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, TriggerEvents::onEffectAdded);

        // Aumentos: depois da Sorte do Apostador (LOW) e das lendas — o teleporte do Shunpo só quando a magia vai sair
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellCastEvent.Pre.class, RussianRoulette::onPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellCastEvent.Pre.class, ShunpoCast::onPre);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Post.class, CoordinatedStrike::onPost);
        NeoForge.EVENT_BUS.addListener(SpellDamageEvent.Post.class, EngravedRune::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(SpellDamageEvent.Post.class, Sklaverei::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(SpellDamageEvent.Post.class, TrickRicochet::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(SpellDamageEvent.Post.class, GoldenSpin::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, EngravedRune::onDamage);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> EngravedRune.tick(e.getServer()));

        // Aprender pelo rank da classe
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.Counter.class, ClassUnlocks::onCounter);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, ClassUnlocks::onLogin);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, ClassUnlocks::onPlayerTick);

        // Limpeza
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, e -> {
            EngravedRune.forget(e.getEntity().getUUID());
            GoldenSpin.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> clear());
    }

    private static void forget(Player p) {
        TriggerCaster.forget(p.getUUID());
        TriggerEvents.forget(p.getUUID());
        ShunpoCast.forget(p.getUUID());
        RussianRoulette.forget(p.getUUID());
        TrickRicochet.forget(p.getUUID());
    }

    private static void clear() {
        TriggerCaster.clear();
        TriggerEvents.clear();
        ShunpoCast.clear();
        RussianRoulette.clear();
        TrickRicochet.clear();
        Sklaverei.clear();
        EngravedRune.clear();
        GoldenSpin.clear();
    }
}
