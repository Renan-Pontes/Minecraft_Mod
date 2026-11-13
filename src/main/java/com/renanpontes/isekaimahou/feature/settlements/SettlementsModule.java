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
        CityDebugCommands.register();
        // onda 6 (city_pace): opção "ritmo da cidade" e a ajuda do jogador no canteiro
        CityPaceConfig.register();
        NeoForge.EVENT_BUS.addListener(CityPace::onRightClick);
        // onda 6 (city_monuments): bônus dos monumentos e templos, bênção diária
        CityMonuments.register();
        // onda 6 (city_trades): comerciantes de ofício (ferreiro, curandeiro, escriba...) ligados aos prédios
        CityTrades.register();
        // v1.0 (city_magic): efeitos de obra — Impulso de Obra, Erguer Obra, Moldar Lote
        com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.register();

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
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            WarService.forget(e.getEntity().getUUID());
            CityPerks.forget(e.getEntity().getUUID());
            CityPlots.forget(e.getEntity().getUUID());
            CityPace.forget(e.getEntity().getUUID());
            CityMonuments.forget(e.getEntity());
            com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.forget(e.getEntity().getUUID());
        });
        // v1.4: bênção dos templos elementais (dano de magia do elemento dentro da cidade)
        NeoForge.EVENT_BUS.addListener(CityPerks::onSpellDamage);
        // onda 7 (sky_island): sem monstro natural na superfície debaixo de uma Ilha Celeste (a sombra dela)
        NeoForge.EVENT_BUS.addListener(SkyCity::onSpawnCheck);
        // onda 7 (sky_access): Corrente Ascendente, Cais Celeste, queda amortecida e NPCs que nunca se perdem
        SkyAccess.register();
        // onda 8 (water_city): Cidade das Águas — fôlego dos cidadãos, dica do barco e a lista das cidades sobre a água
        WaterLife.register();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) SettlementService.sendState(sp, "", false);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            Templates.clear();
            WarService.clear();
            Conquest.clear();
            SettlementService.clear();
            CityPerks.clear();
            CityRaids.clear();
            CityPace.clear();
            CityMonuments.clear();
            com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.clear();
        });
    }
}
