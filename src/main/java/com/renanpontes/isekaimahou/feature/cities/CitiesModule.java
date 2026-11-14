package com.renanpontes.isekaimahou.feature.cities;

import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.world.CityWorldgen;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "cities" (v0.8, §5): a geração das cidades (world/, trabalho city_build) e a VIDA delas — coração da cidade
 * (lugar nomeado, repovoamento, Cerco), NPCs (guarda, cidadão, mercadores por profissão, Lorde), diálogo, comércio
 * por reputação, serviços, crimes, rumores e comandos.
 * <p>v0.9: três reinos (Aldória = facção {@code kingdom}, Império de Sahra = {@code kingdom_sahra}, Reino de Nordheim =
 * {@code kingdom_nordheim}); cada cidade, seus moradores e seus guardas pertencem ao reino dela; o coração varre a
 * cidade (some com os monstros de outras estruturas) e registra a Pedra de Teleporte e o Altar de Ascensão público.
 * <p>v1.0: abordagem da guarda ({@link CityConfront}: quem vê saque/vandalismo/furto reclama, persuadir/propina/devolver,
 * reincidência = Procurado) e guerra de cidades ({@code Allegiance}: guardas e Lorde atacam, civis e mercadores fogem e
 * ninguém negocia). Membros de facção ALIADA não recebem dano do jogador pelas regras sociais do núcleo.
 * <p>Release 1.0: cidade inicial ({@link CityStart}: jogador novo nasce na praça), estalagem ({@link CityInn}: descanso,
 * quarto por dias, rumores pagos), sidequests ({@link CityQuests}), rotina simples dos moradores ({@link CityRoutine})
 * e o Escriba sem Pergaminhos de Glifo. Costura da release: {@link CityFixtures} (Quadro do Mercado na praça e os dois
 * altares das capitais) e, em {@code trade/CityTrades}, os itens das ondas 1–2 nos mercadores.
 */
public class CitiesModule implements Module {
    @Override
    public String id() {
        return "cities";
    }

    @Override
    public void init(IEventBus modBus) {
        CityWorldgen.register(modBus);
        CitiesContent.register(modBus);
        modBus.addListener(CitiesNetwork::register);
        CityCommands.register();
        CityStart.register();
        // v1.1 "mundo vivo": sinos ao amanhecer e no fim da tarde
        CityBells.register();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) -> CityRoutine.load(e.getServer()));
        NeoForge.EVENT_BUS.addListener(CityQuests::onDeath);

        NeoForge.EVENT_BUS.addListener(CityCrimes::onDamage);
        NeoForge.EVENT_BUS.addListener(CityCrimes::onDeath);
        NeoForge.EVENT_BUS.addListener(CityCrimes::onInteract);
        NeoForge.EVENT_BUS.addListener(CityCrimes::onSpellDamage);
        // v1.0: abordagem da guarda (saque de baús, vandalismo em prédios, furto do Ladino)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CityConfront::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CityConfront::onDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.EntityPlaceEvent e) -> {
            if (!e.isCanceled()) CityConfront.onPlace(e);
        });
        NeoForge.EVENT_BUS.addListener(CityConfront::onCounter);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) CityConfront.onLogout(sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 5 == 0) CityConfront.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerWakeUpEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) CityInn.onWake(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                CityRumors.sync(sp);
                CityCrimes.syncWanted(sp);
                CityQuests.sync(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 40 != 0) return;
            for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) {
                CityRumors.checkArrival(sp);
                CityCrimes.tickWanted(sp);
                CityQuests.tick(sp);
                CityFixtures.hints(sp);
            }
            CitySiege.sweep(e.getServer());
            CityInn.tickRooms(e.getServer());
        });
        // Mob de um cerco que já acabou (ex.: salvo no chunk) não volta ao mundo.
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && CitySiege.isSiegeMob(e.getEntity()) && CitySiege.isOrphan(e.getEntity())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CitySiege.clearAll();
            CityCrimes.clear();
        });
    }
}
