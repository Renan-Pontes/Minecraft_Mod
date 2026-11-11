package com.renanpontes.isekaimahou.client.reputation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.ItemReward;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.api.Reputation.TierRewards;
import com.renanpontes.isekaimahou.core.reputation.ReputationPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Reputação" do menu B, no estilo da Janela de Status:
 * <ul>
 *   <li>topo: título em uso (clique troca: esquerdo = próximo, direito = anterior, Shift = tirar) e quantas facções são amigas;</li>
 *   <li>esquerda: cartões das facções (emblema, nome, patamar colorido, valor e barra até o próximo patamar);</li>
 *   <li>direita: a facção escolhida — escada dos 8 patamares, progresso com números, preços, vantagens do patamar atual e do
 *       próximo, recompensas de cada patamar, rivais/aliados e os títulos (clique para usar).</li>
 * </ul>
 */
public class ReputationTab implements MenuTab {
    private static final int HEADER_H = 18;
    private static final int ROW_H = 30;
    private static final int GREEN = 0xFF7CFF8A;
    private static final int RED = 0xFFFF6B6B;
    private static final int MUTED = 0xFF8A82A0;
    private static final int PANEL_EDGE = 0xFF6E5A9A;

    /** Facção escolhida (lembrada entre aberturas do menu). */
    @Nullable private static String lastSelected;

    @FunctionalInterface
    private interface Click {
        void run(int button, boolean shift);
    }

    private record Hit(int x, int y, int w, int h, Click action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Área rolável. */
    private static final class Area {
        int x, y, w, h, content;
        float scroll;

        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
        int max() { return Math.max(0, content - h); }
        void clamp() { scroll = Mth.clamp(scroll, 0, max()); }
    }

    private final Area list = new Area();
    private final Area detail = new Area();
    private final List<Hit> hits = new ArrayList<>();
    @Nullable private List<Component> tooltip;
    private int x, y, w, h;
    private int mouseX, mouseY;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int listW = Mth.clamp(Math.round(w * 0.36F), 132, 180);
        if (w - listW - 6 < 190) listW = Math.max(108, w - 196);
        int top = y + HEADER_H + 5;
        list.x = x;
        list.y = top;
        list.w = listW;
        list.h = y + h - top;
        detail.x = x + listW + 6;
        detail.y = top;
        detail.w = w - listW - 6;
        detail.h = y + h - top;
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                hit.action().run(button, Screen.hasShiftDown());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Area a = list.contains(mx, my) ? list : detail.contains(mx, my) ? detail : null;
        if (a == null || a.max() <= 0) return false;
        a.scroll -= (float) scrollY * 18F;
        a.clamp();
        return true;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Font font = mc.font;
        hits.clear();
        tooltip = null;
        mouseX = mx;
        mouseY = my;

        List<Faction> factions = new ArrayList<>(Reputation.factions());
        if (factions.isEmpty()) {
            Component msg = Component.translatable("screen.isekai_mahou.reputation.empty");
            g.drawCenteredString(font, msg, x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        Faction selected = factions.get(0);
        for (Faction f : factions) if (f.id().equals(lastSelected)) selected = f;
        lastSelected = selected.id();

        renderHeader(g, font, player, factions);
        renderList(g, font, player, factions, selected);
        renderDetail(g, font, player, selected);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void hover(@Nullable Area clip, int hx, int hy, int hw, int hh, List<Component> lines) {
        if (clip != null && !clip.contains(mouseX, mouseY)) return;
        if (mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh) tooltip = lines;
    }

    private void hit(@Nullable Area clip, int hx, int hy, int hw, int hh, Click action) {
        if (clip != null) {
            int top = Math.max(hy, clip.y), bottom = Math.min(hy + hh, clip.y + clip.h);
            if (bottom <= top) return;
            hits.add(new Hit(hx, top, hw, bottom - top, action));
        } else {
            hits.add(new Hit(hx, hy, hw, hh, action));
        }
    }

    private boolean over(@Nullable Area clip, int hx, int hy, int hw, int hh) {
        if (clip != null && !clip.contains(mouseX, mouseY)) return false;
        return mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh;
    }

    // ---------------------------------------------------------------- Topo: título + resumo
    private void renderHeader(GuiGraphics g, Font font, LocalPlayer player, List<Faction> factions) {
        g.fillGradient(x, y, x + w, y + HEADER_H, 0xC0281E3A, 0xC0140F1E);
        g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, MahouUi.alpha(MahouUi.GOLD_DIM, 0xD0));
        if (GlyphArt.exists(MahouUi.SPRITES)) MahouUi.sprite(g, x + 4, y + 5, MahouUi.STAR_U, MahouUi.STAR_V, 7, 7);
        else MahouUi.diamond(g, x + 7, y + 8, 3, MahouUi.GOLD);

        Component label = Component.translatable("screen.isekai_mahou.reputation.title_label");
        g.drawString(font, label, x + 15, y + 5, MahouUi.TEXT_DIM, false);
        int px = x + 15 + font.width(label) + 5;

        int friends = 0;
        for (Faction f : factions) if (Reputation.isFriendly(player, f.id())) friends++;
        Component summary = Component.translatable("screen.isekai_mahou.reputation.friends",
                Component.literal(String.valueOf(friends)).withColor(GREEN & 0xFFFFFF), factions.size());
        int sw = font.width(summary);
        boolean showSummary = x + w - sw - 6 > px + 90;
        if (showSummary) g.drawString(font, summary, x + w - sw - 6, y + 5, MahouUi.TEXT_DIM, false);

        // Placa do título (clicável)
        List<String> titles = Reputation.titles(player);
        MutableComponent current = Reputation.selectedTitle(player);
        Component shown = current != null ? current
                : Component.translatable(titles.isEmpty() ? "screen.isekai_mahou.reputation.title_locked"
                        : "screen.isekai_mahou.reputation.title_none").withColor(MUTED & 0xFFFFFF);
        int maxPlate = Math.max(40, (showSummary ? x + w - sw - 14 : x + w - 6) - px);
        int pw = Math.min(maxPlate, font.width(shown) + 22);
        boolean hov = over(null, px, y + 2, pw, HEADER_H - 4);
        MahouUi.roundRect(g, px, y + 2, pw, HEADER_H - 4, hov ? 0xD03A2C55 : 0xC0201830, 0xC0120E1C);
        MahouUi.roundOutline(g, px, y + 2, pw, HEADER_H - 4, current != null ? MahouUi.GOLD_DIM : 0xFF3A3150);
        if (!titles.isEmpty()) {
            g.drawString(font, "‹", px + 3, y + 5, hov ? MahouUi.GOLD : MahouUi.GOLD_DIM, false);
            g.drawString(font, "›", px + pw - 7, y + 5, hov ? MahouUi.GOLD : MahouUi.GOLD_DIM, false);
        }
        g.drawString(font, MahouUi.fit(font, shown, pw - 20), px + 10, y + 5, MahouUi.GOLD, true);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("screen.isekai_mahou.reputation.title_label").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable(titles.isEmpty() ? "screen.isekai_mahou.reputation.title_locked_hint"
                : "screen.isekai_mahou.reputation.title_hint").withStyle(ChatFormatting.GRAY));
        hover(null, px, y + 2, pw, HEADER_H - 4, tip);
        if (!titles.isEmpty()) {
            String cur = Reputation.selectedTitleKey(player);
            hit(null, px, y + 2, pw, HEADER_H - 4, (button, shift) -> {
                String next;
                if (shift) {
                    next = null;
                } else {
                    int idx = cur == null ? -1 : titles.indexOf(cur);
                    // a lista circula passando por "nenhum" (índice -1)
                    int n = titles.size() + 1;
                    int pos = Math.floorMod(idx + 1 + (button == 1 ? -1 : 1), n) - 1;
                    next = pos < 0 ? null : titles.get(pos);
                }
                PacketDistributor.sendToServer(new ReputationPayloads.SelectTitle(next == null ? "" : next));
                click(next == null ? 0.8F : 1.2F);
            });
        }
    }

    // ---------------------------------------------------------------- Esquerda: cartões
    private void renderList(GuiGraphics g, Font font, LocalPlayer player, List<Faction> factions, Faction selected) {
        panel(g, list.x, list.y, list.w, list.h, PANEL_EDGE);
        list.content = factions.size() * (ROW_H + 2) + 6;
        list.clamp();
        boolean bar = list.max() > 0;
        g.enableScissor(list.x + 1, list.y + 1, list.x + list.w - 1, list.y + list.h - 1);
        int rx = list.x + 3, rw = list.w - 6 - (bar ? 4 : 0);
        int cy = list.y + 4 - Math.round(list.scroll);
        for (Faction f : factions) {
            int value = Reputation.get(player, f.id());
            Standing s = Standing.of(value);
            boolean sel = f == selected;
            boolean hov = over(list, rx, cy, rw, ROW_H);
            int accent = f.argb();
            if (sel) {
                MahouUi.roundRect(g, rx, cy, rw, ROW_H, MahouUi.alpha(accent, 0x58), MahouUi.alpha(accent, 0x1C));
                MahouUi.roundOutline(g, rx, cy, rw, ROW_H, MahouUi.alpha(accent, 0xE0));
                g.fill(rx, cy + 3, rx + 2, cy + ROW_H - 3, MahouUi.lighten(accent, 0.3F));
            } else {
                MahouUi.roundRect(g, rx, cy, rw, ROW_H, hov ? 0x40FFFFFF : 0x50000000, hov ? 0x20FFFFFF : 0x30000000);
            }
            FactionArt.emblem(g, f, rx + 4, cy + 4, 22);
            int tx = rx + 30, tw = rw - 33;
            g.drawString(font, MahouUi.fit(font, f.name().withStyle(ChatFormatting.BOLD), tw), tx, cy + 3,
                    MahouUi.lighten(accent, 0.25F), true);
            String num = String.valueOf(value);
            int numW = Math.round(font.width(num) * 0.75F);
            MahouUi.smallText(g, font, Component.literal(num), tx + tw - numW, cy + 14, 0.75F, 0xFFE8E2F4, false);
            g.pose().pushPose();
            g.pose().translate(tx, cy + 14, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, MahouUi.fit(font, s.displayName(), Math.round((tw - numW - 4) / 0.75F)), 0, 0, s.argb(), false);
            g.pose().popPose();
            MahouUi.bar(g, tx, cy + ROW_H - 6, tw, 3, ReputationNotices.fraction(value), s.argb());
            if (Reputation.isAllied(player, f.id())) {
                // facção aliada: losango verde no canto do emblema
                MahouUi.diamond(g, rx + 24, cy + 6, 3, 0xFF0B0912);
                MahouUi.diamond(g, rx + 24, cy + 6, 2, GREEN);
            }

            List<Component> tip = List.of(f.coloredName().withStyle(ChatFormatting.BOLD),
                    Component.translatable("screen.isekai_mahou.reputation.row_tip", s.displayName(), value).withStyle(ChatFormatting.GRAY));
            hover(list, rx, cy, rw, ROW_H, tip);
            hit(list, rx, cy, rw, ROW_H, (button, shift) -> {
                if (!f.id().equals(lastSelected)) {
                    lastSelected = f.id();
                    detail.scroll = 0;
                    click(1.0F);
                }
            });
            cy += ROW_H + 2;
        }
        g.disableScissor();
        scrollbar(g, list, PANEL_EDGE);
    }

    // ---------------------------------------------------------------- Direita: detalhes
    private void renderDetail(GuiGraphics g, Font font, LocalPlayer player, Faction f) {
        int accent = f.argb();
        panel(g, detail.x, detail.y, detail.w, detail.h, accent);
        detail.clamp();
        boolean bar = detail.max() > 0;
        int cx = detail.x + 8, cw = detail.w - 16 - (bar ? 4 : 0);
        int top = detail.y + 6 - Math.round(detail.scroll);
        g.enableScissor(detail.x + 1, detail.y + 1, detail.x + detail.w - 1, detail.y + detail.h - 1);

        int value = Reputation.get(player, f.id());
        Standing s = Standing.of(value);
        Standing next = s.next();
        int yy = top;

        // Emblema com moldura + nome + patamar
        int box = 38;
        g.fill(cx - 1, yy - 1, cx + box + 1, yy + box + 1, MahouUi.alpha(accent, 0x40));
        MahouUi.roundRect(g, cx, yy, box, box, MahouUi.darken(accent, 0.7F), MahouUi.darken(accent, 0.88F));
        MahouUi.roundOutline(g, cx, yy, box, box, MahouUi.lighten(accent, 0.2F));
        FactionArt.emblem(g, f, cx + 3, yy + 3, 32);
        int tx = cx + box + 7, tw = cw - box - 7;
        g.pose().pushPose();
        g.pose().translate(tx, yy + 2, 0);
        g.pose().scale(1.25F, 1.25F, 1F);
        g.drawString(font, MahouUi.fit(font, f.name().withStyle(ChatFormatting.BOLD), Math.round(tw / 1.25F)), 0, 0,
                MahouUi.lighten(accent, 0.25F), true);
        g.pose().popPose();
        Component standingLine = Component.empty().append(s.displayName().withStyle(ChatFormatting.BOLD))
                .append(Component.literal("  " + value).withColor(0xE8E2F4))
                .append(Component.literal(" / " + Reputation.MAX).withColor(MUTED & 0xFFFFFF));
        g.drawString(font, MahouUi.fit(font, standingLine, tw), tx, yy + 16, s.argb(), true);
        Component trade = s.canTrade() ? priceText(s) : Component.translatable("screen.isekai_mahou.reputation.no_trade").withColor(RED & 0xFFFFFF);
        Component tradeLine = Component.translatable("screen.isekai_mahou.reputation.price", trade);
        MahouUi.smallText(g, font, tradeLine, tx, yy + 28, 0.75F, MahouUi.TEXT_DIM, false);
        hover(detail, tx, yy + 26, tw, 9, List.of(Component.translatable("screen.isekai_mahou.reputation.price_tip").withStyle(ChatFormatting.GRAY)));
        yy += box + 6;

        // Aliança (v1.0): membros não recebem o seu dano e ajudam nas brigas
        yy = allyRow(g, font, player, f, s, cx, yy, cw);

        // Descrição
        Component desc = f.description();
        if (!desc.getString().isEmpty()) yy += wrap(g, font, desc.copy().withStyle(ChatFormatting.ITALIC), cx, yy, cw, MahouUi.TEXT_DIM) + 4;

        // Escada dos patamares
        yy = ladder(g, font, s, value, cx, yy, cw);

        // Progresso até o próximo patamar
        if (next != null) {
            Component left = Component.translatable("screen.isekai_mahou.reputation.next", next.displayName());
            g.drawString(font, MahouUi.fit(font, left, cw - 60), cx, yy, MahouUi.TEXT, false);
            String nums = value + " / " + next.min;
            g.drawString(font, nums, cx + cw - font.width(nums), yy, 0xFFE8E2F4, false);
            yy += 10;
            int span = next.min - s.min;
            progress(g, cx, yy, cw, (value - s.min) / (float) Math.max(1, span), s.argb(), next.argb());
            yy += 7;
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.reputation.to_next", next.min - value),
                    cx, yy, 0.75F, MUTED, false);
            yy += 9;
        } else {
            g.drawString(font, Component.translatable("screen.isekai_mahou.reputation.max"), cx, yy, MahouUi.GOLD, true);
            yy += 10;
            progress(g, cx, yy, cw, 1F, s.argb(), MahouUi.GOLD_PALE);
            yy += 9;
        }
        yy += 4;

        // Vantagens
        yy = section(g, font, Component.translatable("screen.isekai_mahou.reputation.perks"), cx, yy, cw, accent);
        yy = perk(g, font, f, s, true, cx, yy, cw);
        if (next != null) yy = perk(g, font, f, next, false, cx, yy, cw);
        yy += 3;

        // Recompensas
        yy = section(g, font, Component.translatable("screen.isekai_mahou.reputation.rewards"), cx, yy, cw, accent);
        for (Standing tier : Standing.rewardTiers()) yy = rewardRow(g, font, player, f, tier, s, cx, yy, cw);
        yy += 3;

        // Relações
        yy = section(g, font, Component.translatable("screen.isekai_mahou.reputation.relations"), cx, yy, cw, accent);
        yy = relations(g, font, Component.translatable("screen.isekai_mahou.reputation.rivals"), f.rivals(), true, cx, yy, cw);
        yy = relations(g, font, Component.translatable("screen.isekai_mahou.reputation.allies"), f.allies(), false, cx, yy, cw);
        if (f.neutralWhenFriendly()) {
            yy += wrap(g, font, Component.translatable("screen.isekai_mahou.reputation.neutral_hint"), cx, yy, cw, 0xFF9FD8A0) + 2;
        }
        yy += 3;

        // Títulos
        yy = titles(g, font, player, f, cx, yy, cw);

        g.disableScissor();
        detail.content = yy + Math.round(detail.scroll) - detail.y + 4;
        detail.clamp();
        scrollbar(g, detail, accent);
    }

    /** Botão "Aliada" (liga/desliga) + quantas aliadas o jogador tem. Só com patamar ≥ Neutro e até o máximo. */
    private int allyRow(GuiGraphics g, Font font, LocalPlayer player, Faction f, Standing s, int cx, int yy, int cw) {
        boolean allied = Reputation.isAllied(player, f.id());
        int count = Reputation.allied(player).size();
        boolean lowStanding = !s.atLeast(Standing.NEUTRAL);
        boolean canToggle = allied || (!lowStanding && count < Reputation.MAX_ALLIED);
        Component label = Component.translatable(allied ? "screen.isekai_mahou.reputation.ally_on" : "screen.isekai_mahou.reputation.ally_off");
        int bw = font.width(label) + 12, bh = 14;
        boolean hov = canToggle && over(detail, cx, yy, bw, bh);
        int col = allied ? GREEN : canToggle ? MahouUi.GOLD : MUTED;
        MahouUi.roundRect(g, cx, yy, bw, bh, MahouUi.alpha(col, hov ? 0x70 : allied ? 0x50 : 0x28), MahouUi.alpha(col, hov ? 0x40 : 0x14));
        MahouUi.roundOutline(g, cx, yy, bw, bh, MahouUi.alpha(col, hov ? 0xFF : 0xB0));
        g.drawString(font, label, cx + 6, yy + 3, allied ? GREEN : canToggle ? MahouUi.GOLD_PALE : MUTED, false);
        Component info = Component.translatable("screen.isekai_mahou.reputation.ally_count", count, Reputation.MAX_ALLIED);
        MahouUi.smallText(g, font, info, cx + bw + 6, yy + 4, 0.75F, MahouUi.TEXT_DIM, false);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("screen.isekai_mahou.reputation.ally_title").withStyle(ChatFormatting.GREEN));
        for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.reputation.ally_desc"), 200)) {
            StringBuilder sb = new StringBuilder();
            line.accept((i, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            tip.add(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
        }
        if (!canToggle) {
            tip.add(Component.translatable(lowStanding ? "screen.isekai_mahou.reputation.ally_low" : "screen.isekai_mahou.reputation.ally_full",
                    Reputation.MAX_ALLIED).withStyle(ChatFormatting.RED));
        } else {
            tip.add(Component.translatable(allied ? "screen.isekai_mahou.reputation.ally_click_off" : "screen.isekai_mahou.reputation.ally_click_on")
                    .withStyle(ChatFormatting.YELLOW));
        }
        hover(detail, cx, yy, bw, bh, tip);
        if (canToggle) {
            hit(detail, cx, yy, bw, bh, (button, shift) -> {
                PacketDistributor.sendToServer(new ReputationPayloads.SetAllied(f.id(), !allied));
                click(allied ? 0.8F : 1.3F);
            });
        }
        return yy + bh + 6;
    }

    private static Component priceText(Standing s) {
        int pct = Math.round((s.priceMultiplier() - 1F) * 100F);
        if (pct == 0) return Component.translatable("screen.isekai_mahou.reputation.price_normal").withColor(0xE8E2F4);
        return Component.literal((pct > 0 ? "+" : "−") + Math.abs(pct) + "%").withColor(pct > 0 ? RED & 0xFFFFFF : GREEN & 0xFFFFFF);
    }

    /** 8 segmentos coloridos (um por patamar) com marcador na posição atual. */
    private int ladder(GuiGraphics g, Font font, Standing current, int value, int cx, int yy, int cw) {
        Standing[] all = Standing.values();
        int segW = Math.max(6, cw / all.length);
        int total = segW * all.length;
        int sx0 = cx + (cw - total) / 2;
        int barY = yy + 5;
        for (int i = 0; i < all.length; i++) {
            Standing st = all[i];
            int sx = sx0 + i * segW;
            boolean active = st == current;
            boolean below = st.ordinal() < current.ordinal();
            int col = MahouUi.alpha(st.argb(), active ? 0xFF : below ? 0x90 : 0x48);
            g.fill(sx, barY, sx + segW - 1, barY + 6, col);
            g.fill(sx, barY, sx + segW - 1, barY + 1, MahouUi.alpha(0xFFFFFFFF, active ? 0x70 : 0x20));
            if (active) g.renderOutline(sx - 1, barY - 1, segW + 1, 8, MahouUi.lighten(st.argb(), 0.4F));
            List<Component> tip = new ArrayList<>();
            tip.add(st.displayName().withStyle(ChatFormatting.BOLD));
            tip.add(Component.translatable("screen.isekai_mahou.reputation.range", st.min, st.max()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("screen.isekai_mahou.reputation.price",
                    st.canTrade() ? priceText(st) : Component.translatable("screen.isekai_mahou.reputation.no_trade").withColor(RED & 0xFFFFFF))
                    .withStyle(ChatFormatting.GRAY));
            hover(detail, sx, barY - 2, segW, 10, tip);
        }
        // marcador (losango) na posição exata dentro do segmento atual
        float frac = (value - current.min) / (float) Math.max(1, current.max() - current.min);
        int mx = sx0 + current.ordinal() * segW + Math.round(Mth.clamp(frac, 0F, 1F) * (segW - 2));
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0);
        MahouUi.diamond(g, mx, barY - 2, 2, MahouUi.mix(0xFFFFFFFF, current.argb(), 0.3F * pulse));
        g.fill(mx, barY, mx + 1, barY + 6, 0xFFFFFFFF);
        MahouUi.smallText(g, font, Component.literal(String.valueOf(Reputation.MIN)), sx0, barY + 8, 0.5F, MUTED, false);
        String max = "+" + Reputation.MAX;
        MahouUi.smallText(g, font, Component.literal(max), sx0 + total - font.width(max) * 0.5F, barY + 8, 0.5F, MUTED, false);
        return yy + 18;
    }

    private static void progress(GuiGraphics g, int bx, int by, int bw, float frac, int from, int to) {
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xFF07050C);
        g.fill(bx, by, bx + bw, by + 4, 0xFF161224);
        int fw = Math.round(bw * Mth.clamp(frac, 0F, 1F));
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + 4, MahouUi.lighten(from, 0.2F), MahouUi.darken(from, 0.2F));
            g.fill(bx, by, bx + fw, by + 1, MahouUi.alpha(0xFFFFFFFF, 0x50));
            if (fw < bw) g.fill(bx + fw - 1, by, bx + fw, by + 4, MahouUi.lighten(to, 0.5F));
        }
    }

    private int perk(GuiGraphics g, Font font, Faction f, Standing st, boolean current, int cx, int yy, int cw) {
        Component perk = f.perk(st);
        if (perk.getString().isEmpty()) return yy;
        Component head = Component.literal(current ? "✦ " : "◇ ").append(Component.translatable(current
                ? "screen.isekai_mahou.reputation.perk_now" : "screen.isekai_mahou.reputation.perk_next", st.displayName()));
        g.drawString(font, MahouUi.fit(font, head, cw), cx, yy, current ? st.argb() : MahouUi.alpha(st.argb(), 0xB0), false);
        yy += 10;
        yy += wrap(g, font, perk, cx + 6, yy, cw - 6, current ? MahouUi.TEXT : MUTED);
        return yy + 3;
    }

    private int rewardRow(GuiGraphics g, Font font, LocalPlayer player, Faction f, Standing tier, Standing current,
                          int cx, int yy, int cw) {
        TierRewards r = f.rewards(tier);
        boolean claimed = Reputation.reached(player, f.id(), tier);
        boolean reachedNow = current.atLeast(tier);
        int labelW = Math.min(74, cw / 3);
        int rowTop = yy;
        int lineH = 18;
        int alpha = claimed || reachedNow ? 0xFF : 0xA0;
        g.fill(cx, yy, cx + cw, yy + lineH, claimed ? 0x2048FF70 : 0x18FFFFFF);
        g.drawString(font, MahouUi.fit(font, tier.displayName(), labelW - 2), cx + 3, yy + 5, MahouUi.alpha(tier.argb(), alpha), false);

        int ix = cx + labelW + 2;
        int limit = cx + cw - 14;
        // itens
        for (ItemReward ir : r.items()) {
            ItemStack stack = ir.stack();
            if (stack.isEmpty()) continue;
            if (ix + 16 > limit) {
                ix = cx + labelW + 2;
                yy += lineH;
                g.fill(cx, yy, cx + cw, yy + lineH, claimed ? 0x2048FF70 : 0x18FFFFFF);
            }
            g.renderItem(stack, ix, yy + 1);
            g.renderItemDecorations(font, stack, ix, yy + 1);
            List<Component> tip = new ArrayList<>();
            tip.add(stack.getHoverName().copy().withStyle(stack.getRarity().getStyleModifier()));
            tip.add(Component.literal("× " + stack.getCount()).withStyle(ChatFormatting.GRAY));
            hover(detail, ix, yy + 1, 16, 16, tip);
            ix += 18;
        }
        // textos
        List<Component[]> chips = new ArrayList<>();
        if (r.xp() > 0) chips.add(new Component[] {Component.translatable("screen.isekai_mahou.reputation.reward_xp", r.xp()).withColor(0xD9A8FF), null});
        if (r.glyphScrolls() > 0) {
            chips.add(new Component[] {Component.translatable("screen.isekai_mahou.reputation.reward_glyphs", r.glyphScrolls(), r.glyphTier()).withColor(0x8FEFFF),
                    Component.translatable("screen.isekai_mahou.reputation.reward_glyphs_tip", r.glyphTier())});
        }
        if (r.legend() != null) {
            Component tip = "random".equals(r.legend()) ? Component.translatable("screen.isekai_mahou.reputation.reward_legend_random")
                    : Component.translatable("screen.isekai_mahou.reputation.reward_legend_tip",
                            Component.translatableWithFallback("legend.isekai_mahou." + r.legend(), r.legend()));
            chips.add(new Component[] {Component.translatable("screen.isekai_mahou.reputation.reward_legend").withColor(0xFFD86B), tip});
        }
        if (r.title() != null) {
            chips.add(new Component[] {Component.translatable("screen.isekai_mahou.reputation.reward_title").withColor(0xFFE9A8),
                    Component.translatable(r.title()).withColor(0xFFD86B)});
        }
        for (Component[] chip : chips) {
            int cwid = Math.round(font.width(chip[0]) * 0.75F) + 6;
            if (ix + cwid > limit && ix > cx + labelW + 2) {
                ix = cx + labelW + 2;
                yy += lineH;
                g.fill(cx, yy, cx + cw, yy + lineH, claimed ? 0x2048FF70 : 0x18FFFFFF);
            }
            MahouUi.roundRect(g, ix, yy + 3, cwid, 12, 0x60000000, 0x40000000);
            MahouUi.smallText(g, font, chip[0], ix + 3, yy + 6, 0.75F, 0xFFFFFFFF, false);
            if (chip[1] != null) hover(detail, ix, yy + 3, cwid, 12, List.of(chip[1]));
            ix += cwid + 3;
        }
        if (r.isEmpty()) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.reputation.reward_none"), ix, yy + 6, 0.75F, MUTED, false);
        }
        // marca: recebida / bloqueada
        String mark = claimed ? "✔" : "◇";
        g.drawString(font, mark, cx + cw - 10, rowTop + 5, claimed ? GREEN : MUTED, false);
        hover(detail, cx + cw - 12, rowTop + 2, 12, 14, List.of(Component.translatable(claimed
                ? "screen.isekai_mahou.reputation.claimed" : "screen.isekai_mahou.reputation.locked", tier.displayName())
                .withStyle(claimed ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        return yy + lineH + 2;
    }

    private int relations(GuiGraphics g, Font font, Component label, Map<String, Float> map, boolean rivals, int cx, int yy, int cw) {
        g.drawString(font, label, cx, yy + 2, rivals ? 0xFFFF9C8A : 0xFF9FD8A0, false);
        int ix = cx + font.width(label) + 6;
        if (map.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.reputation.none"), ix, yy + 2, MUTED, false);
            return yy + 14;
        }
        for (Map.Entry<String, Float> e : map.entrySet()) {
            Faction other = Reputation.faction(e.getKey());
            if (other == null) continue;
            Component name = other.name();
            int chipW = 16 + font.width(name) + 6;
            if (ix + chipW > cx + cw && ix > cx + 40) {
                ix = cx + 40;
                yy += 14;
            }
            MahouUi.roundRect(g, ix, yy, chipW, 13, MahouUi.alpha(other.argb(), 0x40), MahouUi.alpha(other.argb(), 0x18));
            FactionArt.emblem(g, other, ix + 2, yy + 1, 11);
            g.drawString(font, name, ix + 15, yy + 3, MahouUi.lighten(other.argb(), 0.3F), false);
            int pct = Math.round(e.getValue() * (rivals ? 50F : 25F));
            Component tip = Component.translatable(rivals ? "screen.isekai_mahou.reputation.rival_tip" : "screen.isekai_mahou.reputation.ally_tip",
                    other.coloredName(), pct);
            hover(detail, ix, yy, chipW, 13, List.of(tip));
            ix += chipW + 3;
        }
        return yy + 16;
    }

    private int titles(GuiGraphics g, Font font, LocalPlayer player, Faction f, int cx, int yy, int cw) {
        List<Standing> withTitle = new ArrayList<>();
        for (Standing st : Standing.rewardTiers()) if (f.title(st) != null) withTitle.add(st);
        if (withTitle.isEmpty()) return yy;
        yy = section(g, font, Component.translatable("screen.isekai_mahou.reputation.titles"), cx, yy, cw, f.argb());
        String selected = Reputation.selectedTitleKey(player);
        for (Standing st : withTitle) {
            String key = f.title(st);
            boolean unlocked = Reputation.hasTitle(player, key);
            boolean using = key.equals(selected);
            boolean hov = unlocked && over(detail, cx, yy, cw, 13);
            g.fill(cx, yy, cx + cw, yy + 13, using ? 0x40FFD86B : hov ? 0x30FFFFFF : 0x14FFFFFF);
            if (using) g.renderOutline(cx, yy, cw, 13, MahouUi.alpha(MahouUi.GOLD, 0xC0));
            String icon = using ? "★" : unlocked ? "☆" : "▪";
            g.drawString(font, icon, cx + 3, yy + 3, using ? MahouUi.GOLD : unlocked ? MahouUi.GOLD_DIM : MUTED, false);
            Component name = Component.translatable(key);
            Component status = using ? Component.translatable("screen.isekai_mahou.reputation.title_equipped")
                    : unlocked ? Component.translatable("screen.isekai_mahou.reputation.title_use")
                    : Component.translatable("screen.isekai_mahou.reputation.locked", st.displayName());
            int sw = Math.round(font.width(status) * 0.75F);
            g.drawString(font, MahouUi.fit(font, name, cw - sw - 22), cx + 14, yy + 3, unlocked ? MahouUi.GOLD_PALE : MUTED, false);
            MahouUi.smallText(g, font, status, cx + cw - sw - 3, yy + 4, 0.75F, using ? MahouUi.GOLD : MUTED, false);
            if (unlocked) {
                hover(detail, cx, yy, cw, 13, List.of(Component.translatable(using ? "screen.isekai_mahou.reputation.title_unequip"
                        : "screen.isekai_mahou.reputation.title_click").withStyle(ChatFormatting.GRAY)));
                hit(detail, cx, yy, cw, 13, (button, shift) -> {
                    PacketDistributor.sendToServer(new ReputationPayloads.SelectTitle(using ? "" : key));
                    click(using ? 0.8F : 1.2F);
                });
            }
            yy += 15;
        }
        return yy + 2;
    }

    // ---------------------------------------------------------------- Peças
    /** Cabeçalho de seção: losango + texto dourado + linha que some à direita. */
    private static int section(GuiGraphics g, Font font, Component title, int cx, int yy, int cw, int accent) {
        MahouUi.diamond(g, cx + 2, yy + 4, 2, MahouUi.lighten(accent, 0.3F));
        Component bold = title.copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, bold, cx + 8, yy, MahouUi.GOLD, true);
        int lx = cx + 8 + font.width(bold) + 5;
        if (lx < cx + cw) g.fillGradient(lx, yy + 4, cx + cw, yy + 5, MahouUi.alpha(MahouUi.GOLD_DIM, 0xC0), MahouUi.alpha(MahouUi.GOLD_DIM, 0x20));
        return yy + 12;
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int tx, int ty, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(20, width));
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), tx, ty + i * 10, color, false);
        return lines.size() * 10;
    }

    /** Painel de "janela de sistema" (fundo escuro, borda fina, cantoneiras na cor de destaque). */
    private static void panel(GuiGraphics g, int px, int py, int pw, int ph, int accent) {
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xE0141026, 0xE00A0814);
        g.renderOutline(px, py, pw, ph, MahouUi.alpha(accent, 0x8C));
        g.fill(px + 2, py + 2, px + pw - 2, py + 3, 0x18FFFFFF);
        MahouUi.brackets(g, px - 1, py - 1, pw + 2, ph + 2, 5, MahouUi.lighten(accent, 0.2F));
    }

    private static void scrollbar(GuiGraphics g, Area a, int accent) {
        int max = a.max();
        if (max <= 0) return;
        int tx = a.x + a.w - 5, ty = a.y + 3, th = a.h - 6;
        g.fill(tx, ty, tx + 2, ty + th, 0x40FFFFFF);
        int knob = Math.max(12, th * a.h / Math.max(1, a.content));
        int ky = ty + Math.round((th - knob) * (a.scroll / max));
        g.fill(tx, ky, tx + 2, ky + knob, MahouUi.alpha(accent, 0xE0));
    }
}
