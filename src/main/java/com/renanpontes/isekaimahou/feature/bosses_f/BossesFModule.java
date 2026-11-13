package com.renanpontes.isekaimahou.feature.bosses_f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.OverworldBoss;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "bosses_f" (v0.9): os 4 chefes do Overworld dos covis do world_v09 — Hidra do Pântano ({@code swamp_hydra}),
 * Wyvern Rei dos Picos ({@code wyvern_king}), Basilisco do Deserto ({@code desert_basilisk}) e Colosso das Ruínas
 * ({@code ruin_colossus}). GeckoLib com animações completas, 3 fases cada, todo golpe grande telegrafado, escala de
 * grupo, anti-trapaça, recompensa de grupo (XP + {@code boss.<id>}), itens de chefe (troféus pedidos pela Ascensão),
 * ovos e {@code /mahou spawnboss <id>}. O chefe colocado pelo molde do covil guarda a casa e volta para ela.
 */
public class BossesFModule implements Module {
    @Override
    public String id() {
        return "bosses_f";
    }

    @Override
    public void init(IEventBus modBus) {
        // v1.0: o chefe provoca quando derruba um jogador (a fala tem trava no próprio chefe)
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer && e.getSource().getEntity() instanceof OverworldBoss b) b.onPlayerSlain();
        });
        BfRegistry.register(modBus);
        BfCommands.register();
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            BfRegistry.eggs().forEach(egg -> event.accept(egg.get()));
            BfRegistry.bossItems().forEach(item -> event.accept(item.get()));
        }
    }

    /** Resistências/fraquezas elementais (dano de magia). */
    private static void registerResistances() {
        // Hidra: criatura do brejo, a água a alimenta; o fogo é a resposta clássica.
        ElementReactions.setMultiplier(IsekaiMahou.id("swamp_hydra"), Element.WATER, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("swamp_hydra"), Element.FIRE, 1.3F);
        // Wyvern: senhor do vento; pedra e gelo o derrubam.
        ElementReactions.setMultiplier(IsekaiMahou.id("wyvern_king"), Element.WIND, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("wyvern_king"), Element.EARTH, 1.3F);
        // Basilisco: escamas de pedra e areia; a água amolece.
        ElementReactions.setMultiplier(IsekaiMahou.id("desert_basilisk"), Element.EARTH, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("desert_basilisk"), Element.WATER, 1.4F);
        // Colosso: pedra viva; a terra quase não o fere, a água infiltra nas rachaduras.
        ElementReactions.setMultiplier(IsekaiMahou.id("ruin_colossus"), Element.EARTH, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("ruin_colossus"), Element.FIRE, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("ruin_colossus"), Element.WATER, 1.3F);
    }
}
