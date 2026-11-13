package com.renanpontes.isekaimahou.feature.espada;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.aging.RespiraRot;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBoss;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "espada" (v1.3) — os chefes Espada do Las Noches, cada um com modelo GeckoLib por forma, música tema
 * ({@code BossThemed}), telegrafia em todo golpe grande, escala de grupo, anti-trapaça, título e Magia Lendária:
 * <ul>
 *   <li><b>Ulquiorra Cifer</b> ({@code ulquiorra}) — selado → Murciélago (60%) → Segunda Etapa (25%);</li>
 *   <li><b>Barragan Louisenbairn</b> ({@code barragan}) — Respira e o status <b>Envelhecido</b>; Arrogante (50%);</li>
 *   <li><b>Grimmjow Jaegerjaquez</b> ({@code grimmjow}) — brigão rápido; Pantera (50%).</li>
 * </ul>
 * O módulo las_noches invoca pelos ids reservados. Teste: {@code /mahou spawnboss ulquiorra|barragan|grimmjow}.
 * Assets: {@code tools/gen/espada.py} (modelos/texturas/animações) e {@code tools/gen/espada_sfx.py} (sons).
 */
public class EspadaModule implements Module {
    @Override
    public String id() {
        return "espada";
    }

    @Override
    public void init(IEventBus modBus) {
        EspadaRegistries.register(modBus);
        EspadaSounds.register(modBus);
        EspadaCommands.register();
        EspadaTechniques.register();
        Aging.init();
        RespiraRot.init();
        modBus.addListener(EspadaPayloads::register);
        modBus.addListener(EspadaLegends::setup);
        modBus.addListener(this::onCreativeTab);
        registerResistances();
        // v1.0: o Espada provoca quando derruba um jogador (a fala tem trava no próprio chefe)
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer && e.getSource().getEntity() instanceof EspadaBoss b) b.onPlayerSlain();
        });
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            EspadaRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            EspadaRegistries.items().forEach(item -> event.accept(item.get()));
        }
    }

    /** Resistências/fraquezas elementais (dano de magia). Hollows: a luz (Quincy/Shinigami) os fere mais. */
    private static void registerResistances() {
        ElementReactions.setMultiplier(IsekaiMahou.id("ulquiorra"), Element.SHADOW, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("ulquiorra"), Element.LIGHT, 1.3F);
        ElementReactions.setMultiplier(IsekaiMahou.id("barragan"), Element.SHADOW, 0.6F);
        ElementReactions.setMultiplier(IsekaiMahou.id("barragan"), Element.LIGHT, 1.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("barragan"), Element.EARTH, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("grimmjow"), Element.WIND, 0.6F);
        ElementReactions.setMultiplier(IsekaiMahou.id("grimmjow"), Element.LIGHT, 1.25F);
    }
}
