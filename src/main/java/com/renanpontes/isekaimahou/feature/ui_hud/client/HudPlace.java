package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.Objects;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.status.ClientPlaces;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * Linha discreta logo abaixo do card: o lugar onde o jogador está (marcador na cor do lugar, nome, facção e escudo
 * verde de zona segura) e, se o jogador estiver Procurado, um selo vermelho com o tempo que falta.
 * Aparece/some deslizando. v0.9: NÃO empurra mais o topo central — a largura máxima dela vem das faixas fixas de
 * {@link TopSpace} (o Olho Analítico fica parado ao entrar numa cidade ou ao ficar Procurado).
 */
final class HudPlace {
    /** Altura reservada abaixo do card (a barra de mana já começa abaixo dela). */
    static final int RESERVED = 10;
    private static final long FADE_MS = 380L;
    private static final float SCALE = 0.75F;
    private static final int MAX_W = 190;

    @Nullable private static String lastId;
    @Nullable private static Places.Place shown;
    private static long changedAt;

    private HudPlace() {}

    static void clear() {
        lastId = null;
        shown = null;
        changedAt = 0;
    }

    static void render(GuiGraphics g, Minecraft mc) {
        if (HudCard.bottom <= 0 || mc.player == null) return;
        long now = Util.getMillis();
        Places.Place cur = ClientPlaces.current();
        String id = cur == null ? null : cur.id();
        if (!Objects.equals(id, lastId)) {
            lastId = id;
            changedAt = now;
            if (cur != null) shown = cur;
        }
        float t = Math.min(1F, (now - changedAt) / (float) FADE_MS);
        float alpha = cur != null ? HudDraw.easeOut(t) : (shown == null ? 0F : 1F - t);
        if (cur == null && t >= 1F) shown = null;
        Places.Place place = cur != null ? cur : shown;
        int wantedTicks = StatusView.wantedTicks(mc.player);
        boolean wanted = wantedTicks != 0;
        if ((place == null || alpha <= 0.03F) && !wanted) return;

        Font font = mc.font;
        int y = HudCard.bottom + 2;
        int x = 4 - Math.round((1F - alpha) * 10F);
        // Tela estreita / GUI grande: não invade as barras de chefe, os ícones de efeito nem o rastreador de objetivos
        // Mede nas linhas que a faixa PODE ocupar (card sem e com as 2 linhas extras): meditar não muda a largura
        int limit = Math.max(70, TopSpace.leftSideLimit(mc, g.guiWidth(), g.guiHeight(), HudCard.minBottom() + 2, HudCard.maxBottom() + 2 + 9));
        // O selo de Procurado tem prioridade: o nome do lugar encolhe (reticências) para os dois caberem numa linha
        // (uma 2ª linha bateria no cristal da barra de mana)
        int badgeW = wanted ? wantedWidth(font, wantedTicks) + 3 : 0;
        int end = 4;
        if (place != null && alpha > 0.03F) end = placeRow(g, font, place, x, y, alpha, Math.min(MAX_W, limit - x - 3 - badgeW));
        if (wanted) wantedBadge(g, font, wantedTicks, Math.max(4, end + 3), y);
    }

    /** Marcador + nome do lugar + facção; devolve a borda direita. */
    private static int placeRow(GuiGraphics g, Font font, Places.Place place, int x, int y, float a, int maxW) {
        int col = 0xFF000000 | place.color();
        int light = HudDraw.brighten(col, 0.45F);
        Component name = place.name();
        MutableComponent sub = factionName(place.faction());

        int nameW = Math.round(font.width(name) * SCALE);
        int subW = sub == null ? 0 : Math.round(font.width(sub) * SCALE) + 5;
        int safeW = place.safeZone() ? 9 : 0;
        int w = 11 + nameW + subW + safeW + 4;
        if (w > maxW && sub != null) {
            w -= subW;
            subW = 0;
            sub = null;
        }
        if (w > maxW) w = Math.max(40, maxW);

        // Faixa translúcida que esmaece para a direita, com o filete da cor do lugar
        g.fill(x, y, x + w, y + 9, HudDraw.fade(0x9006080F, a));
        g.fill(x + w, y + 1, x + w + 3, y + 8, HudDraw.fade(0x5006080F, a));
        g.fill(x, y, x + 1, y + 9, HudDraw.fade(col, a));
        g.fill(x + 1, y + 8, x + w, y + 9, HudDraw.fade(col, 0.35F * a));
        HudDraw.sprite(g, HudDraw.PIN, x + 3, y + 1, HudDraw.fade(light, a));

        int tx = x + 11;
        int maxText = Math.round((w - 11 - subW - safeW - 3) / SCALE);
        FormattedCharSequence nm = HudDraw.ellipsize(font, name, Math.max(12, maxText));
        g.pose().pushPose();
        g.pose().translate(tx, y + 2F, 0);
        g.pose().scale(SCALE, SCALE, 1F);
        HudDraw.text(g, font, nm, 0, 0, HudDraw.fade(light, a), true);
        g.pose().popPose();
        tx += Math.round(font.width(nm) * SCALE) + 2;
        if (sub != null) {
            HudDraw.scaledText(g, font, Component.literal("· ").append(sub), tx, y + 2F, SCALE, HudDraw.fade(HudDraw.SYS_MUTED, a), false);
            tx += subW;
        }
        // Zona segura (cidade protegida): escudo verde
        if (place.safeZone()) HudDraw.sprite(g, HudDraw.SHIELD, tx + 1, y + 1, HudDraw.fade(0xFF7CE08A, 0.9F * a));
        return x + w + 3;
    }

    @Nullable
    private static MutableComponent factionName(String id) {
        if (id == null || id.isEmpty()) return null;
        Reputation.Faction f = Reputation.faction(id);
        return f != null ? f.name() : HudDraw.tr("faction.isekai_mahou." + id);
    }

    private static int wantedWidth(Font font, int ticks) {
        int lw = Math.round(font.width(Component.translatable("hud.isekai_mahou.ui_hud.wanted")) * SCALE);
        int tw = ticks < 0 ? 0 : Math.round(font.width(" " + StatusView.time(ticks)) * SCALE);
        return 10 + lw + tw + 4;
    }

    /** Selo vermelho pulsante "PROCURADO 2:30"; devolve a borda direita. */
    private static int wantedBadge(GuiGraphics g, Font font, int ticks, int x, int y) {
        float p = HudDraw.pulse(900);
        int red = HudDraw.lerp(p, 0xFFFF4D4D, 0xFFFFB0A0);
        Component label = Component.translatable("hud.isekai_mahou.ui_hud.wanted");
        String time = ticks < 0 ? "" : " " + StatusView.time(ticks);
        int lw = Math.round(font.width(label) * SCALE);
        int tw = time.isEmpty() ? 0 : Math.round(font.width(time) * SCALE);
        int w = 10 + lw + tw + 4;
        g.fill(x, y, x + w, y + 9, 0xD0300808);
        g.renderOutline(x, y, w, 9, HudDraw.fade(red, 0.6F + 0.4F * p));
        HudDraw.sprite(g, HudDraw.WANTED, x + 2, y + 1, red);
        HudDraw.scaledText(g, font, label, x + 10, y + 2F, SCALE, 0xFFFFE0D8, true);
        if (!time.isEmpty()) HudDraw.scaledText(g, font, Component.literal(time), x + 10 + lw, y + 2F, SCALE, 0xFFFFA0A0, false);
        return x + w;
    }
}
