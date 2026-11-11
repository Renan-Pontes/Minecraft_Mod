package com.renanpontes.isekaimahou.feature.bosses_c;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "bosses_c": minichefes das estruturas de classe — Arquimago Renegado, Bruxa Corrompida,
 * Patrono Aprisionado, Ent Corrompido e Lich (com filactério). GeckoLib, barra de chefe, cutscene de
 * apresentação e de fase, falas (Announce estilo chefe), ovos e {@code /mahou spawnboss <id>}.
 * v0.7: todo ataque de área/linha telegrafado no chão (kit de chefes do núcleo), um ataque novo por chefe
 * (Barragem Arcana, Caldeirão Venenoso, Corrente Infernal, Raízes em Linha, Círculo Necrótico), escala de grupo,
 * anti-trapaça (salto/teleporte, sem barco), modo provação e recompensa de grupo (XP 250 + {@code boss.<id>}).
 */
public class BossesCModule implements Module {
    @Override
    public String id() {
        return "bosses_c";
    }

    @Override
    public void init(IEventBus modBus) {
        BossesCRegistry.register(modBus);
        BossesCCommands.register();
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            BossesCRegistry.eggs().forEach(egg -> event.accept(egg.get()));
        }
    }

    /** Resistências/fraquezas elementais (dano de magia). */
    private static void registerResistances() {
        // Patrono: demônio do fogo e das trevas, sofre com a Luz.
        ElementReactions.setMultiplier(IsekaiMahou.id("imprisoned_patron"), Element.FIRE, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("imprisoned_patron"), Element.SHADOW, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("imprisoned_patron"), Element.LIGHT, 1.5F);
        // Ent: madeira queima; terra e água o alimentam.
        ElementReactions.setMultiplier(IsekaiMahou.id("corrupted_ent"), Element.FIRE, 2.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("corrupted_ent"), Element.EARTH, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("corrupted_ent"), Element.WATER, 0.5F);
        // Lich: as trevas são dele (a Luz já pega mais forte por ser morto-vivo).
        ElementReactions.setMultiplier(IsekaiMahou.id("lich"), Element.SHADOW, 0.5F);
        // Bruxa: as próprias maldições a protegem das trevas.
        ElementReactions.setMultiplier(IsekaiMahou.id("corrupted_witch"), Element.SHADOW, 0.75F);
        // Filactério: só cede à Luz.
        ElementReactions.setMultiplier(IsekaiMahou.id("lich_phylactery"), Element.SHADOW, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("lich_phylactery"), Element.LIGHT, 1.5F);
    }
}
