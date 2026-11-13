package com.renanpontes.isekaimahou.feature.world_events;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "world_events" (v1.0) — eventos raros do mundo, um por vez, só no overworld:
 * <ul>
 *   <li><b>Queda de Meteoro</b>: cratera pequena no ermo, invasores alienígenas (Batedor, Atirador, Larva e o
 *       mini-chefe Arauto Estelar) e Minério Estelar;</li>
 *   <li><b>Lua de Sangue</b>: mais monstros à noite, um pouco mais de XP e loot;</li>
 *   <li><b>Tempestade Arcana</b>: magias de água e vento mais fortes;</li>
 *   <li><b>Terremoto</b>: tremor e fendas pequenas com minérios expostos;</li>
 *   <li><b>Tempestade de Mana</b>: toda magia mais forte e cristais de mana caindo do céu.</li>
 * </ul>
 * "Só não vai destruir as cidades": toda mudança de terreno passa pelas regras de {@link WeSites} (longe de lugares
 * do {@code Places}, de estruturas e de qualquer bloco de jogador) e nenhum invasor quebra blocos.
 * Assets/dados/traduções: {@code tools/gen/world_events.py}. Comandos: {@code /mahou world_events ...}.
 */
public class WorldEventsModule implements Module {
    @Override
    public String id() {
        return "world_events";
    }

    @Override
    public void init(IEventBus modBus) {
        WeRegistries.register(modBus);
        modBus.addListener(WeNetwork::register);
        modBus.addListener(this::onCreativeTab);
        WeManager.register();
        WeCommands.register();
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            WeRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            WeRegistries.worldItems().forEach(item -> event.accept(item.get()));
        }
    }

    /** Vindos do vazio entre as estrelas: a sombra mal os arranha, a luz queima; as larvas temem o fogo. */
    private static void registerResistances() {
        for (String id : new String[] {"world_events_alien_grunt", "world_events_alien_spitter", "world_events_alien_larva",
                "world_events_alien_overseer"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.SHADOW, 0.5F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 1.25F);
        }
        ElementReactions.setMultiplier(IsekaiMahou.id("world_events_alien_larva"), Element.FIRE, 1.5F);
    }
}
