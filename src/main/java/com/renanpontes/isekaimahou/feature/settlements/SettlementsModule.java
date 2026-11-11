package com.renanpontes.isekaimahou.feature.settlements;

import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "settlements" (v1.0, docs/V10_DESIGN.md §3): CONTROLE DE CIDADES, CIDADE PRÓPRIA E GUERRA.
 * <ul>
 *   <li><b>Tomar uma cidade de reino</b>: Reino Exaltado → Juramento (defender, tributo, vigília) → Selo → Campeão
 *       da Cidade no coração → Senhor da cidade (impostos, decretos, Guarda Pessoal) — {@link Conquest};</li>
 *   <li><b>Fundar a própria cidade</b>: Carta de Fundação → estilo Reino/Desértico/Nórdico → plantas por nível →
 *       construtores erguem bloco a bloco sem recursos → moradores, guardas, mercadores — {@link SettlementService},
 *       {@code build/**};</li>
 *   <li><b>Guerra</b>: declarar inimiga (afinidade máxima) pelo {@code Allegiance} do núcleo, milícia, NPCs inimigos
 *       lutam/fogem e não negociam, guardas defendem o Senhor e o grupo — {@link WarService}.</li>
 * </ul>
 */
public class SettlementsModule implements Module {
    @Override
    public String id() {
        return "settlements";
    }

    @Override
    public void init(IEventBus modBus) {
        SettlementsContent.register(modBus);
        modBus.addListener(SettlementsNetwork::register);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> BossArena.registerType(SettlementsContent.CHAMPION.get())));
        SettlementCommands.register();

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SettlementService.tickServer(e.getServer()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity().level().isClientSide) return;
            Conquest.onKill(e);
            WarService.onDeath(e);
        });
        NeoForge.EVENT_BUS.addListener(WarService::onDamage);
        NeoForge.EVENT_BUS.addListener(WarService::onInteract);
        NeoForge.EVENT_BUS.addListener(SettlementService::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(Conquest::onReputation);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> WarService.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) SettlementService.sendState(sp, "", false);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            Templates.clear();
            WarService.clear();
            Conquest.clear();
            SettlementService.clear();
        });
    }
}
