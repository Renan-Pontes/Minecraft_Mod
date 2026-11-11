package com.renanpontes.isekaimahou.feature.trials;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "trials": Câmaras de Provação (v0.7 §4). Cada rank elemental (I–VII) é uma instância no Reino das
 * Provações ({@code isekai_mahou:trial_realm}): recepção temática (grupo, convites, altar) → arena circular com
 * ondas, perigos telegrafados e chefe final → recompensas → volta para casa.
 *
 * Entrada: {@code TrialsApi.request} (pacote RequestTrial da aba Provações/Caminhos, comando
 * {@code /mahou trial start}, links no chat). Este módulo instala o {@link TrialsApi.Handler}.
 */
public class TrialsModule implements Module {
    @Override
    public String id() {
        return "trials";
    }

    @Override
    public void init(IEventBus modBus) {
        TrialsRegistry.register(modBus);
        modBus.addListener(TrialNetwork::register);
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TrialManager.TICKETS));
        modBus.addListener(this::onCreativeTabs);
        TrialEvents.register();
        TrialCommands.register();
        TrialsApi.set(new TrialsApi.Handler() {
            @Override
            public boolean canStart(ServerPlayer player, Element element, int rank, List<Component> reasons) {
                return TrialManager.canStart(player, element, rank, reasons);
            }

            @Override
            public void start(ServerPlayer player, Element element, int rank) {
                TrialManager.start(player, element, rank);
            }
        });
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_WORLD.getKey()) return;
        e.accept(TrialsRegistry.ALTAR_ITEM.get());
        e.accept(TrialsRegistry.EXIT_ITEM.get());
        e.accept(TrialsRegistry.RUNE_STONE_ITEM.get());
        e.accept(TrialsRegistry.BARRIER_ITEM.get());
    }
}
