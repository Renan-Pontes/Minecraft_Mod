package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "glyphs_classes_a" — v1.4: aumento e gatilho próprios de Mago, Bruxa, Bruxo, Druida, Necromante, Ferreiro,
 * Ladino e Cavaleiro (16 glifos de CLASSE: a classe aprende pela progressão; as outras, só por Lição).
 * <ul>
 *   <li>{@link GcaGlyphs}: ids, registro e a Metamagia (que mexe nos aumentos no parse);</li>
 *   <li>{@link GcaAugments}: comportamento dos aumentos (conjuração e dano de magia);</li>
 *   <li>{@link GcaTriggers} + {@link GcaTriggerEngine}: condições e conjuração automática dos gatilhos;</li>
 *   <li>{@link GcaUnlocks}: desbloqueio pela progressão da classe;</li>
 *   <li>{@link GcaCommands}: {@code /mahou glyphs grant glyphs_classes_a}.</li>
 * </ul>
 */
public class GlyphsClassesAModule implements Module {
    @Override
    public String id() {
        return "glyphs_classes_a";
    }

    @Override
    public void init(IEventBus modBus) {
        GcaGlyphs.register();

        // Gatilhos: a tecla não conjura magias reativas (antes de qualquer cobrança/sorte)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, GcaTriggerEngine::onPre);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onCastPost);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onDrink);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onDeath);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onDamagePost);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onCounter);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(GcaTriggers::onServerTick);

        // Aumentos: depois dos cancelamentos (Pre baixo) — Metamagia confere o custo extra, Pacto ajusta o poder
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, GcaAugments::onCastPre);
        NeoForge.EVENT_BUS.addListener(GcaAugments::onCastPost);
        NeoForge.EVENT_BUS.addListener(GcaAugments::onDamagePre);
        NeoForge.EVENT_BUS.addListener(GcaAugments::onDamagePost);
        NeoForge.EVENT_BUS.addListener(GcaAugments::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(GcaAugments::onServerTick);

        // Desbloqueio pela progressão da classe
        NeoForge.EVENT_BUS.addListener(GcaUnlocks::onCounter);
        NeoForge.EVENT_BUS.addListener(GcaUnlocks::onCircle);
        NeoForge.EVENT_BUS.addListener(GcaUnlocks::onPathChosen);
        NeoForge.EVENT_BUS.addListener(GcaUnlocks::onLogin);

        NeoForge.EVENT_BUS.addListener(GlyphsClassesAModule::onLogout);
        NeoForge.EVENT_BUS.addListener(GlyphsClassesAModule::onServerStopped);
        GcaCommands.register();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        GcaTriggerEngine.forget(e.getEntity().getUUID());
        GcaTriggers.forget(e.getEntity().getUUID());
        GcaAugments.forget(e.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent e) {
        GcaTriggerEngine.clear();
        GcaTriggers.clear();
        GcaAugments.clear();
    }
}
