package com.renanpontes.isekaimahou.feature.lore;

import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "lore" — release 1.0: <b>a lore do mundo, pra quem quiser saber</b> (bíblia em {@code docs/LORE.md}).
 * <ul>
 *   <li><b>Enciclopédia</b> no menu B (seção do Guia de Viagem, ao lado do Diário): {@link LoreBook}.</li>
 *   <li><b>Páginas Perdidas</b> colecionáveis ({@code lore_page}) em baús do mundo ({@link LoreLoot}), nas ruínas e
 *       nos tesouros: 37 fragmentos em 8 coleções ({@link LoreFragments}); fechar uma coleção abre uma Verdade
 *       Oculta.</li>
 *   <li><b>Ruínas de Velmora</b> (estruturas pequenas {@code lore_ruin} / {@code lore_ruin_sand}) com a
 *       <b>Estela Antiga</b> ({@code lore_stele}).</li>
 *   <li>Ler um fragmento <b>revela um lugar</b> (estrutura, covil de chefe ou tesouro enterrado): coordenadas no chat,
 *       seta no rastreador e a posição guardada na Enciclopédia ({@link LoreService}).</li>
 * </ul>
 * API para outros módulos: {@link LoreApi}, {@link LoreEvent}. Teste: {@code /mahou lore ...} ({@link LoreCommands}).
 */
public class LoreModule implements Module {
    @Override
    public String id() {
        return "lore";
    }

    @Override
    public void init(IEventBus modBus) {
        LoreContent.register(modBus);
        modBus.addListener(LoreNetwork::register);
        LoreCommands.register();

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((LootTableLoadEvent e) -> LoreLoot.onLoad(e));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) LoreService.sync(sp);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) LoreService.sync(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LoreService.onLogout(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> LoreService.clearAll());
        bus.addListener((ServerTickEvent.Post e) -> LoreService.tick(e.getServer()));
        // Dica de primeira vez: pegou uma Página Perdida do chão
        bus.addListener((ItemEntityPickupEvent.Post e) -> {
            if (e.getOriginalStack().is(LoreContent.LORE_PAGE.get())) Hints.show(e.getPlayer(), "lore_page");
        });
    }
}
