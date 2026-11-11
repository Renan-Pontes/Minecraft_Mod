package com.renanpontes.isekaimahou.feature.hueco_mundo;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Eventos do módulo: chegada nos reinos (descoberta, contador, cutscene), chefes dos lugares, registro dos lugares e o
 * atalho das classes espirituais (o Shinigami aprende o Portão Senkai e o Quincy a Ponte de Reishi ao chegar no 2º rank
 * do ritual da classe — e quem já passou dele aprende ao entrar no mundo).
 */
public final class HmEvents {
    private static final int SITE_CHECK_TICKS = 100;
    /** Rank do ritual de classe que libera o atalho para o Mundo Oco. */
    public static final int SHORTCUT_RANK = 2;
    private static final String RANK_COUNTER = "ritual_rank";

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HmEvents::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(HmEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(HmEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(HmEvents::onLogin);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> HmSites.registerBuilt(e.getServer()));
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        HmRealm realm = HmRealm.of(e.getTo());
        if (realm == null) return;
        ServerLevel level = sp.serverLevel();
        // Chegada por qualquer caminho (portal, Viagem, comando): garante o hub e os lugares.
        HmSites.ensureBuilt(level, realm);
        boolean first = MagicApi.get(sp).counter(realm.visitKey()) == 0;
        MagicApi.addCounter(sp, realm.visitKey(), 1);
        Travel.discover(sp, realm.hubPlaceId);
        if (first) {
            Cutscenes.play(sp, "portal", sp, realm.displayName(),
                    HmText.of("cutscene.isekai_mahou.hueco_mundo.arrive", realm.id), realm.color, 80, false);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % SITE_CHECK_TICKS != 0) return;
        HmRealm realm = HmRealm.of(level.dimension());
        if (realm == null || level.players().isEmpty()) return;
        HmSites.tick(level, realm);
    }

    private static void onCounter(MagicProgressEvent.Counter e) {
        if (RANK_COUNTER.equals(e.key) && e.value >= SHORTCUT_RANK) grantShortcut(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && MagicApi.get(sp).counter(RANK_COUNTER) >= SHORTCUT_RANK) grantShortcut(sp);
    }

    /** Glifo de atalho da classe espiritual (ou null para as outras classes). */
    public static ResourceLocation shortcutGlyph(MagicPath path) {
        if (path == MagicPath.SHINIGAMI) return IsekaiMahou.id("garganta_senkai");
        if (path == MagicPath.QUINCY) return IsekaiMahou.id("garganta_reishi");
        return null;
    }

    private static void grantShortcut(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        ResourceLocation glyph = shortcutGlyph(d.path());
        if (glyph == null || d.knowsGlyph(glyph)) return;
        if (MagicApi.learnGlyph(sp, glyph)) {
            String key = d.path() == MagicPath.SHINIGAMI ? "message.isekai_mahou.hueco_mundo.shortcut_learned.shinigami"
                    : "message.isekai_mahou.hueco_mundo.shortcut_learned.quincy";
            sp.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA));
        }
    }

    private HmEvents() {}
}
