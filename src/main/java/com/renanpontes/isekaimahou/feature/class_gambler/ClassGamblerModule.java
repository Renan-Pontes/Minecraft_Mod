package com.renanpontes.isekaimahou.feature.class_gambler;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.CasinoDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.glyph.GamblerGlyphs;
import com.renanpontes.isekaimahou.feature.class_gambler.glyph.JackpotTriggers;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.GamblerPassives;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.JackpotService;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfits;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseChallenge;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "class_gambler" (v1.0) — o APOSTADOR (Destreza, Foco; homenagem Hakari e Gambit). Ver docs/V10_DESIGN.md §1.
 * <ul>
 *   <li>Sistema "Sorte": toda magia rola de −99% a +100% de poder; Tensão e JACKPOT (sorte infinita) — {@code luck/};</li>
 *   <li>10 glifos de origem + a forma G "Cassino" — {@code glyph/} (Carta, Dados, Roleta, Caça-Níquel, Cara ou Coroa,
 *   Amuleto da Sorte, Mão Real, Dobrar a Aposta, Dados Viciados, Ao Jackpot);</li>
 *   <li>Baralho Arcano (arma do kit, graus I–IV), Carta de Baralho e Ficha de Cassino — {@code item/};</li>
 *   <li>traje em 4 graus (GeckoLib) entregue nos Círculos 3/5/8 — {@code outfit/};</li>
 *   <li>progressão "Fichas da Casa" (5 ranks, {@code ritual_rank}) com a Rodada da Casa — {@code rank/};</li>
 *   <li>bônus de classe (Sorte, Esquiva da Sorte, Fortuna), magias iniciais e 2 lendas.</li>
 * </ul>
 */
public class ClassGamblerModule implements Module {
    @Override
    public String id() {
        return "class_gambler";
    }

    @Override
    public void init(IEventBus modBus) {
        GbRegistries.register(modBus);
        GamblerOutfits.register(modBus);
        GamblerGlyphs.register();
        modBus.addListener(GbNet::register);
        modBus.addListener(GamblerLegends::setup);
        modBus.addListener(ClassGamblerModule::creativeTab);

        // kit: o Baralho Arcano (a arma exigida pelo MagicPath), cartas para jogar e duas fichas para a 1ª aposta
        ClassKits.addItem(MagicPath.GAMBLER, () -> new ItemStack(GbRegistries.DECK.get()));
        ClassKits.addItem(MagicPath.GAMBLER, () -> new ItemStack(GbRegistries.CARD.get(), 16));
        ClassKits.addItem(MagicPath.GAMBLER, () -> new ItemStack(GbRegistries.CASINO_CHIP.get(), 2));

        // Gatilho "Ao Jackpot" bloqueia a tecla antes de qualquer rolagem; a sorte rola depois dos cancelamentos
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, JackpotTriggers::onPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LuckService::onCastPre);
        NeoForge.EVENT_BUS.addListener(JackpotService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(JackpotService::onDeath);
        NeoForge.EVENT_BUS.addListener(JackpotService::onLogout);
        NeoForge.EVENT_BUS.addListener(HouseChallenge::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(HouseChallenge::onDeath);
        NeoForge.EVENT_BUS.addListener(HouseChallenge::onLogout);
        NeoForge.EVENT_BUS.addListener(GamblerPassives::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(GamblerPassives::onDeath);
        NeoForge.EVENT_BUS.addListener(GamblerPassives::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(GamblerPassives::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(GamblerStarter::onPathChosen);
        NeoForge.EVENT_BUS.addListener(ClassGamblerModule::onLogout);
        NeoForge.EVENT_BUS.addListener(ClassGamblerModule::onServerTick);
        NeoForge.EVENT_BUS.addListener(ClassGamblerModule::onServerStopped);
        GamblerCommands.register();
        AnimeTricks.register();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        GbRegistries.creativeItems().forEach(h -> e.accept(h.value()));
        GamblerOutfits.all().forEach(e::accept);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LuckService.forget(e.getEntity().getUUID());
        CasinoDomainEntity.forget(e.getEntity().getUUID());
    }

    /** Limpeza leve uma vez por minuto (estados de Jackpot vencidos de quem saiu). */
    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 1200 == 0) JackpotService.prune(e.getServer().overworld().getGameTime());
    }

    private static void onServerStopped(ServerStoppedEvent e) {
        LuckService.clear();
        JackpotService.clear();
        JackpotTriggers.clear();
        HouseChallenge.clear();
        CasinoDomainEntity.clearAll();
    }
}
