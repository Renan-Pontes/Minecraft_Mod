package com.renanpontes.isekaimahou.feature.trials.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.trials.TrialNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "trials": céu do Reino das Provações, aba "Provações" do menu B e as linhas do
 * rastreador de objetivos (provação em andamento e próxima disponível).
 */
public class TrialsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        TrialNetwork.clientHud = TrialClientState::accept;
        modBus.addListener((RegisterDimensionSpecialEffectsEvent e) -> e.register(IsekaiMahou.id("trial_realm"), new TrialSkyEffects()));
        MenuTabs.register("trials", 15, Component.translatable("tab.isekai_mahou.trials"), TrialsTab::new);
        ObjectiveTracker.register("trials", 1000, TrialClientState::activeLines);
        ObjectiveTracker.register("trials_available", 550, TrialClientState::availableLines);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> TrialClientState.reset());
    }
}
