package com.renanpontes.isekaimahou.feature.market;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.social.SocialEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Módulo "market" — release 1.0: <b>Mercado</b> (leilão entre jogadores) e a dica do grupo melhorado.
 * <ul>
 *   <li>Aba "Mercado" no menu B (categoria Social): comprar, anunciar e retirar — de qualquer lugar.</li>
 *   <li>No mundo: o bloco {@code market_board} (Quadro do Mercado) e, nas Guildas que já existem, AGACHAR + clicar no
 *       Quadro de Missões ({@code quest_board}) abrem a mesma tela.</li>
 *   <li>Regras em {@link MarketService}; dados salvos em {@link MarketData}.</li>
 * </ul>
 * O grupo melhorado (marcador dos amigos no HUD e no mapa) mora em {@code core/social} + {@code client/social}; daqui
 * sai só a dica de primeira vez.
 * Teste: {@code /mahou market open | coins <n> | sellhand <preço> | fake <preço> | list | box | expire | clear}.
 */
public class MarketModule implements Module {
    private static final ResourceLocation QUEST_BOARD = IsekaiMahou.id("quest_board");

    @Override
    public String id() {
        return "market";
    }

    @Override
    public void init(IEventBus modBus) {
        MarketContent.BLOCKS.register(modBus);
        MarketContent.ITEMS.register(modBus);
        modBus.addListener(MarketNet::register);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) e.accept(MarketContent.MARKET_BOARD_ITEM.get());
        });
        MarketCommands.register();

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> MarketService.tick(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) MarketService.onLogin(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> MarketService.onLogout(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> MarketService.clear());
        // Antes do bloco: agachado, o Quadro de Missões abre o Mercado em vez das missões
        bus.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickBlock.class, MarketModule::onUseBlock);
        bus.addListener(SocialEvent.Party.class, MarketModule::onParty);
    }

    /** "…e também no quadro da Guilda": agachar + clicar no Quadro de Missões abre o Mercado (sem mexer no módulo da Guilda). */
    private static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.isCanceled()) return;
        if (!QUEST_BOARD.equals(BuiltInRegistries.BLOCK.getKey(e.getLevel().getBlockState(e.getPos()).getBlock()))) return;
        if (!e.getEntity().isShiftKeyDown()) {
            // Clique normal segue para as missões; só ensina (uma vez) que o Mercado também abre ali
            if (e.getEntity() instanceof ServerPlayer sp && e.getHand() == InteractionHand.MAIN_HAND) Hints.show(sp, "market.quest_board");
            return;
        }
        // Cancela nas duas mãos e nos dois lados (senão o item da mão seria usado/colocado)
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer sp) MarketService.open(sp);
    }

    /** Entrou num grupo: uma dica só, explicando o marcador dos amigos, o XP dividido e o chat de grupo. */
    private static void onParty(SocialEvent.Party e) {
        if (e.getChange() != SocialEvent.PartyChange.JOINED && e.getChange() != SocialEvent.PartyChange.CREATED) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(e.getPlayer());
        if (sp != null) Hints.show(sp, "market.party");
    }
}
