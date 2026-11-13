package com.renanpontes.isekaimahou.feature.renown;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.social.SocialEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "renown" — release 1.0: <b>Ranking</b>, <b>títulos</b> e <b>recordes de tempo por chefe</b>.
 * <ul>
 *   <li>{@link RenownRanking}: quadro com maior nível, chefes mortos, andar do Abismo, torres conquistadas, cidade mais
 *       rica e mais populosa. No bloco Quadro de Renome ({@code renown_board}, receita + aparece ao lado dos quadros
 *       das cidades — {@link BoardPlacer}) e na aba Renome do menu B.</li>
 *   <li>{@link RenownTitles}: títulos por feitos, escolhidos na aba Renome; aparecem acima do nome (cliente) e no chat
 *       ({@link RenownChat}).</li>
 *   <li>{@link BossTimer}: cronômetro da luta e melhor tempo por chefe (pessoal e do servidor).</li>
 * </ul>
 * Dados salvos em {@link RenownData}. Teste: {@code /mahou renown board|status|check|grant|revoke|record|timer|fake|unfake|clearrecords}.
 */
public class RenownModule implements Module {
    /** De quanto em quanto tempo (ticks) a foto de quem está online é refeita. */
    private static final int SNAPSHOT_EVERY = 600;

    @Override
    public String id() {
        return "renown";
    }

    @Override
    public void init(IEventBus modBus) {
        RenownContent.BLOCKS.register(modBus);
        RenownContent.ITEMS.register(modBus);
        modBus.addListener(RenownNetwork::register);
        modBus.addListener(this::onCreativeTabs);
        RenownCommands.register();

        IEventBus bus = NeoForge.EVENT_BUS;
        // Títulos: qualquer progresso marca o jogador para uma checagem (feita uma vez por segundo, no tick)
        bus.addListener((MagicProgressEvent.Counter e) -> RenownTitles.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.LevelUp e) -> RenownTitles.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.CircleChanged e) -> RenownTitles.markDirty(e.getPlayer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            RenownRanking.snapshot(sp);
            RenownTitles.onLogin(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            RenownRanking.snapshot(sp);
            RenownRanking.forget(sp.getUUID());
            RenownTitles.onLogout(sp);
        });
        bus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> {
            RenownTitles.clear();
            RenownChat.clear();
            BossTimer.clear();
        });

        // Cronômetro de chefe (LOWEST na morte: só conta se ninguém cancelou a morte — fases, quase-morte...)
        bus.addListener((SocialEvent.ArenaSealed e) -> BossTimer.onSealed(e));
        bus.addListener((SocialEvent.ArenaReleased e) -> BossTimer.onReleased(e));
        bus.addListener((LivingDamageEvent.Post e) -> BossTimer.onDamage(e));
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> BossTimer.onDeath(e));

        // Título no chat (LOWEST: depois do chat de grupo, que cancela a mensagem normal)
        bus.addListener(EventPriority.LOWEST, (ServerChatEvent e) -> RenownChat.onChat(e));
        bus.addListener((PlayerEvent.NameFormat e) -> RenownChat.onNameFormat(e));

        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> BoardPlacer.onUseBlock(e));
    }

    private static void tick(MinecraftServer server) {
        RenownTitles.tick(server);
        BossTimer.tick(server);
        RenownChat.tick(server);
        if (server.getTickCount() % SNAPSHOT_EVERY == 7) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) RenownRanking.snapshot(p);
        }
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) e.accept(RenownContent.RENOWN_BOARD_ITEM.get());
    }
}
