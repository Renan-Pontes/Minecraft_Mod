package com.renanpontes.isekaimahou.client.notify;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Aba "Avisos" do menu B (v1.1): os últimos {@link NoticeLog#MAX} avisos do mod — o que saiu do chat, recompensas,
 * lugares descobertos, abas novas — do mais novo para o mais velho, com a hora. Em cima, os botões para ajustar o
 * filtro sem sair do jogo: quanto vai para o chat (Mínimo/Normal/Tudo), a linha acima da hotbar e "Limpar".
 */
public class NoticesTab implements MenuTab {
    private static final int TOP_BAR = 22;
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm");

    private int x, y, w, h;
    private float scroll;
    private int contentH;
    private int cw = 112, bw = 92, lw = 56;

    private record Line(NoticeLog.Entry entry, List<FormattedCharSequence> text, int height) {}

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        NoticeLog.markRead();
        int bx = x + w;
        // Telas estreitas: botões menores (a soma nunca passa da largura da aba)
        boolean small = w < 360;
        cw = small ? 88 : 112;
        bw = small ? 70 : 92;
        lw = small ? 44 : 56;
        Button clear = Button.builder(Component.translatable("screen.isekai_mahou.notices.clear"), b -> {
            NoticeLog.clear();
            scroll = 0;
        }).bounds(bx - lw, y + 1, lw, 16).build();
        screen.addTabWidget(clear);
        boolean bar = MahouConfig.safe(MahouConfig.NOTICE_ACTION_BAR);
        Button barBtn = Button.builder(Component.translatable("screen.isekai_mahou.notices.bar." + (bar ? "on" : "off")), b -> {
            set(MahouConfig.NOTICE_ACTION_BAR, !MahouConfig.safe(MahouConfig.NOTICE_ACTION_BAR));
            screen.refreshTab();
        }).bounds(bx - lw - 4 - bw, y + 1, bw, 16).build();
        barBtn.setTooltip(Tooltip.create(Component.translatable("screen.isekai_mahou.notices.bar.tip")));
        screen.addTabWidget(barBtn);
        MahouConfig.ChatMode mode = MahouConfig.safe(MahouConfig.CHAT_MESSAGES);
        Button chat = Button.builder(Component.translatable("screen.isekai_mahou.notices.chat",
                Component.translatable("screen.isekai_mahou.notices.chat." + mode.name().toLowerCase(java.util.Locale.ROOT))), b -> {
            MahouConfig.ChatMode next = MahouConfig.ChatMode.values()[(mode.ordinal() + 1) % MahouConfig.ChatMode.values().length];
            try {
                MahouConfig.CHAT_MESSAGES.set(next);
                MahouConfig.CHAT_MESSAGES.save();
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.warn("Não foi possível salvar a opção do chat", e);
            }
            screen.refreshTab();
        }).bounds(bx - lw - 4 - bw - 4 - cw, y + 1, cw, 16).build();
        chat.setTooltip(Tooltip.create(Component.translatable("screen.isekai_mahou.notices.chat.tip." + mode.name().toLowerCase(java.util.Locale.ROOT))));
        screen.addTabWidget(chat);
    }

    private static void set(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v, boolean on) {
        try {
            v.set(on);
            v.save();
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("Não foi possível salvar a opção dos avisos", e);
        }
    }

    private int listTop() { return y + TOP_BAR; }
    private int listH() { return h - TOP_BAR; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        // Cabeçalho
        Component head = Component.translatable("screen.isekai_mahou.notices.header", NoticeLog.size(), NoticeLog.MAX);
        int headW = Math.max(30, w - lw - 4 - bw - 4 - cw - 6);
        g.drawString(font, MahouUi.fit(font, head, headW), x + 2, y + 5, MahouUi.GOLD, true);

        int lt = listTop(), lh = listH();
        MahouUi.roundRect(g, x, lt, w, lh, 0xA0100C18, 0xA0080610);
        MahouUi.roundOutline(g, x, lt, w, lh, MahouUi.alpha(MahouUi.GOLD_DIM, 0x90));
        List<NoticeLog.Entry> entries = NoticeLog.entries();
        if (entries.isEmpty()) {
            Component empty = Component.translatable("screen.isekai_mahou.notices.empty");
            List<FormattedCharSequence> lines = font.split(empty, w - 40);
            int yy = lt + lh / 2 - lines.size() * 5;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, x + w / 2 - font.width(l) / 2, yy, MahouUi.TEXT_DIM, false);
                yy += 10;
            }
            return;
        }
        int textX = x + 40, textW = w - 40 - 10;
        List<Line> lines = new ArrayList<>();
        int total = 4;
        for (NoticeLog.Entry e : entries) {
            List<FormattedCharSequence> t = font.split(e.text, textW - (e.count() > 1 ? 22 : 0));
            if (t.size() > 3) t = new ArrayList<>(t.subList(0, 3));
            int lhgt = t.size() * 10 + 4;
            lines.add(new Line(e, t, lhgt));
            total += lhgt;
        }
        contentH = total;
        scroll = Mth.clamp(scroll, 0F, maxScroll());
        g.enableScissor(x + 1, lt + 1, x + w - 1, lt + lh - 1);
        int yy = lt + 3 - Math.round(scroll);
        int idx = 0;
        for (Line l : lines) {
            if (yy + l.height() >= lt && yy <= lt + lh) {
                if (idx % 2 == 1) g.fill(x + 2, yy - 1, x + w - 2, yy + l.height() - 1, 0x18FFFFFF);
                String clock = CLOCK.format(new Date(l.entry().realTime));
                g.drawString(font, clock, x + 5, yy + 1, 0xFF7F7896, false);
                MahouUi.diamond(g, x + 34, yy + 4, 2, color(l.entry().kind));
                int ty = yy + 1;
                for (FormattedCharSequence s : l.text()) {
                    g.drawString(font, s, textX, ty, MahouUi.TEXT, false);
                    ty += 10;
                }
                if (l.entry().count() > 1) {
                    String c = "×" + l.entry().count();
                    g.drawString(font, c, x + w - 8 - font.width(c), yy + 1, MahouUi.GOLD, false);
                }
            }
            yy += l.height();
            idx++;
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int tx = x + w - 4, ty = lt + 2, th = lh - 4;
            g.fill(tx, ty, tx + 2, ty + th, 0x40FFFFFF);
            int knob = Math.max(12, th * lh / Math.max(1, contentH));
            int ky = ty + Math.round((th - knob) * (scroll / max));
            g.fill(tx, ky, tx + 2, ky + knob, MahouUi.GOLD_DIM);
        }
    }

    private static int color(NoticeLog.Kind kind) {
        return switch (kind) {
            case REWARD -> MahouUi.GOLD;
            case WARNING -> 0xFFFF8A6A;
            case IMPORTANT -> 0xFF8FEFFF;
            case MENU -> 0xFFB98CFF;
            default -> 0xFFA89EC0;
        };
    }

    private int maxScroll() {
        return Math.max(0, contentH - listH());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < x || mx >= x + w || my < listTop() || my >= y + h) return false;
        scroll = Mth.clamp(scroll - (float) scrollY * 20F, 0F, maxScroll());
        return true;
    }
}
