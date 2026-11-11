package com.renanpontes.isekaimahou.feature.bosses_g;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "bosses_g" (v0.9) — mais chefes do Overworld, cada um com o seu covil gerado pelo módulo:
 * <ul>
 *   <li><b>Falsa Hidra</b> ({@code false_hydra}) sob a vila abandonada ({@code false_hydra_lair}): corpo enterrado, cabeças
 *       que somem quando você olha, Canto do Esquecimento (apaga o HUD, embaralha a barra de itens e a cura);</li>
 *   <li><b>Rei Mímico</b> ({@code mimic_king}) no Cofre do Tesouro ({@code mimic_vault}): baús viram mímicos, língua
 *       marcada que puxa e devora, cospe o jogador;</li>
 *   <li><b>Manticora</b> ({@code manticore}) na Toca da Manticora ({@code manticore_den}): leque de espinhos, voo curto e
 *       mergulho, rugido que amedronta.</li>
 * </ul>
 * GeckoLib, fases, telegrafia em todo golpe grande, escala de grupo, anti-trapaça, recompensas de grupo, loot próprio,
 * ovos e {@code /mahou spawnboss <id>}. Assets: {@code tools/gen/bosses_g.py}.
 */
public class BossesGModule implements Module {
    @Override
    public String id() {
        return "bosses_g";
    }

    @Override
    public void init(IEventBus modBus) {
        BgRegistries.register(modBus);
        BgCommands.register();
        modBus.addListener(BgPayloads::register);
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            BgRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            BgRegistries.items().forEach(item -> event.accept(item.get()));
        }
    }

    /** Resistências/fraquezas elementais (dano de magia). */
    private static void registerResistances() {
        // Falsa Hidra: criatura das trevas debaixo da terra; a luz a expõe.
        ElementReactions.setMultiplier(IsekaiMahou.id("false_hydra"), Element.SHADOW, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("false_hydra"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("false_hydra"), Element.EARTH, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("false_hydra_head"), Element.SHADOW, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("false_hydra_head"), Element.LIGHT, 1.5F);
        // Rei Mímico e mímicos: madeira e ouro (queima fácil, aguenta pedrada).
        ElementReactions.setMultiplier(IsekaiMahou.id("mimic_king"), Element.FIRE, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("mimic_king"), Element.EARTH, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("vault_mimic"), Element.FIRE, 1.5F);
        // Manticora: fera do deserto que voa (o vento a desvia) e detesta água.
        ElementReactions.setMultiplier(IsekaiMahou.id("manticore"), Element.FIRE, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("manticore"), Element.WIND, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("manticore"), Element.WATER, 1.25F);
    }
}
