package com.renanpontes.isekaimahou.feature.bosses_d;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialSentinel;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Módulo "bosses_d": chefes das Câmaras de Provação (v0.7).
 * <ul>
 *   <li>{@code trial_avatar} — Avatar Elemental (um por elemento/rank, 3 fases, golpes telegrafados por elemento,
 *       magias de glifo programáveis);</li>
 *   <li>{@code trial_sentinel} — Sentinela da Provação (elite com escudo frontal, golpe em área e investida).</li>
 * </ul>
 * O módulo trials invoca os dois gravando {@code isekai_mahou.trial_element}/{@code isekai_mahou.trial_rank} no
 * persistentData. Teste: {@code /mahou spawnboss trial_avatar <elemento> <rank>}.
 */
public class BossesDModule implements Module {
    @Override
    public String id() {
        return "bosses_d";
    }

    @Override
    public void init(IEventBus modBus) {
        BdRegistries.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTabs);
        BdCommands.register();
        // v1.0: o Avatar provoca quando derruba um jogador (a fala tem trava no próprio chefe)
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer && e.getSource().getEntity() instanceof TrialAvatar a) a.onPlayerSlain();
        });
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(BdRegistries.TRIAL_AVATAR.get(), TrialAvatar.createAttributes().build());
        e.put(BdRegistries.TRIAL_SENTINEL.get(), TrialSentinel.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(BdRegistries.TRIAL_AVATAR_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            e.accept(BdRegistries.TRIAL_SENTINEL_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
