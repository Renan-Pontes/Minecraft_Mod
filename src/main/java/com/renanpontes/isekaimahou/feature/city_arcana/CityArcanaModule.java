package com.renanpontes.isekaimahou.feature.city_arcana;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "city_arcana" — v1.5: construções MÁGICAS da cidade fundada, escolhidas no planejamento (5 níveis
 * procedurais por estilo):
 * <ul>
 *   <li><b>Barreira Arcana</b> ({@link ArcaneWard}): protege a cidade da magia hostil;</li>
 *   <li><b>Obelisco de Nulificação</b> ({@link NullObelisk}): zona sem magia, com o modo escolhido pelo Senhor;</li>
 *   <li><b>Torre de Mana</b> e <b>Farol de Vigia Arcano</b> ({@link ArcanaTowers}): mana para quem a cidade protege e
 *       revelar inimigos/invisíveis.</li>
 * </ul>
 * v1.0 (onda 4, "city_magic"): a Barreira aceita uma RECEITA gravada pelo Senhor (efeitos + leis) que ela aplica nos
 * invasores durante cercos, gastando a mana do Núcleo de Mana ({@link WardRecipe}).
 * <p>Estado em cache por cidade ({@link CityArcana}), tudo com intervalos (2/20/40/60/100 ticks).
 */
public class CityArcanaModule implements Module {
    @Override
    public String id() {
        return "city_arcana";
    }

    @Override
    public void init(IEventBus modBus) {
        modBus.addListener(CityArcanaNet::register);
        CityArcanaCommands.register();
        // o Obelisco corta antes (nada de gastar mana); a Barreira só olha o que passou
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpellCastEvent.Pre.class, NullObelisk::onCastPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, SpellCastEvent.Pre.class, ArcaneWard::onCastPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, ArcaneWard::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, ArcaneWard::onJoin);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, NullObelisk::onRightClick);
        // v1.0 (city_magic): receita da Barreira — gravar no catalisador e a "redoma" que impede a receita de pegar
        // em moradores, aliados ou blocos
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, WardRecipe::onRightClick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SpellDamageEvent.Pre.class, WardRecipe::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, WardRecipe::onJoin);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate.class, WardRecipe::onDetonate);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.class, WardRecipe::onEffect);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> CityArcana.rebuild(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            NullObelisk.forget(e.getEntity().getUUID());
            ArcaneWard.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CityArcana.clear();
            ArcaneWard.clear();
            NullObelisk.clear();
            WardRecipe.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 100 == 7) CityArcana.rebuild(server);
        // estes dois rodam mesmo sem cidades (limpam o que sobrou quando um prédio some)
        if (t % 2 == 0) ArcaneWard.tickProjectiles(server);
        if (t % 20 == 3) NullObelisk.tickPlayers(server);
        if (CityArcana.cities().isEmpty()) return;
        if (t % 40 == 11) ArcaneWard.pulse(server);
        if (t % 40 == 31) WardRecipe.pulse(server);
        if (t % 100 == 77) WardRecipe.regen(server);
        if (t % 60 == 23) ArcanaTowers.tickBeacons(server);
        if (t % 100 == 51) ArcanaTowers.tickSpires(server);
    }
}
