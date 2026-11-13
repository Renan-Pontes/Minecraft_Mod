package com.renanpontes.isekaimahou.feature.endless_abyss.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssNetwork;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssNetwork.Action;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssNetwork.Info;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Abismo" do menu B (só aparece no nível 100+). À esquerda: recorde, botões de entrar/continuar/sair e o ranking;
 * à direita: as Lutas das Sombras — a lista dos chefes que o jogador já venceu, cada um com o botão "Enfrentar".
 * Tudo desenhado à mão (sem widgets), poucos elementos, nada piscando.
 */
public class AbyssTab implements MenuTab {
    private static final int PURPLE = 0xFF9A5CFF, PURPLE_DIM = 0x709A5CFF, TEXT = 0xFFE9E1F5, MUTED = 0xFFA99BC0, FAINT = 0xFF6F6485;
    private static final int CARD = 0xFF1B1526, CARD_HOVER = 0xFF261D36, GOLD = 0xFFFFD86B, RED = 0xFFE8735E;
    private static final int ROW_H = 22;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, maxScroll;
    private long lastRequest;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        request();
    }

    private void request() {
        long now = Util.getMillis();
        if (now - lastRequest < 500) return;
        lastRequest = now;
        PacketDistributor.sendToServer(new Action(Action.INFO, 0, ""));
    }

    @Override
    public void tick() {
        // Estado fresco enquanto a aba está aberta (recorde/ranking mudam pouco: a cada 5 s basta)
        if (Util.getMillis() - lastRequest > 5000) request();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        Info info = AbyssClientState.info;
        int leftW = Math.min(190, w * 2 / 5);
        renderLeft(g, font, info, x, y, leftW, mx, my);
        g.fill(x + leftW + 5, y, x + leftW + 6, y + h, PURPLE_DIM);
        renderShadows(g, font, info, x + leftW + 12, y, w - leftW - 12, mx, my);
    }

    private void renderLeft(GuiGraphics g, Font font, Info info, int lx, int ly, int lw, int mx, int my) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.title").withStyle(ChatFormatting.BOLD), lx, ly, PURPLE, true);
        int cy = ly + 14;
        g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.best", info.best()), lx, cy, info.best() > 0 ? GOLD : MUTED, false);
        cy += 11;
        g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.shadow_wins", info.shadowWins()), lx, cy, MUTED, false);
        cy += 14;
        cy += wrapped(g, font, Component.translatable("screen.isekai_mahou.endless_abyss.desc"), lx, cy, lw, FAINT, 5) + 6;

        if (info.runFloor() != 0) {
            Component where = info.runFloor() > 0 ? Component.translatable("screen.isekai_mahou.endless_abyss.in_run", info.runFloor())
                    : Component.translatable("screen.isekai_mahou.endless_abyss.in_shadow");
            g.drawString(font, where, lx, cy, TEXT, false);
            cy += 12;
            button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.endless_abyss.leave"), mx, my, RED,
                    () -> send(new Action(Action.LEAVE, 0, ""), true));
            cy += 24;
        } else {
            button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.endless_abyss.enter", 1), mx, my, PURPLE,
                    () -> send(new Action(Action.ENTER, 1, ""), true));
            cy += 22;
            if (info.start() > 1) {
                button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.endless_abyss.resume", info.start()), mx, my, GOLD,
                        () -> send(new Action(Action.ENTER, info.start(), ""), true));
                cy += 22;
            }
            cy += 2;
        }

        // Ranking (os 5 melhores do servidor)
        if (!info.top().isEmpty() && cy + 22 <= ly + h) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.ranking").withStyle(ChatFormatting.BOLD), lx, cy, PURPLE, true);
            cy += 12;
            int pos = 1;
            for (AbyssNetwork.RankRow r : info.top()) {
                if (cy + 10 > ly + h) break;
                Component floor = Component.translatable("screen.isekai_mahou.endless_abyss.floor_short", r.floor());
                g.drawString(font, pos + ". " + font.plainSubstrByWidth(r.name(), lw - font.width(floor) - 22), lx, cy, pos == 1 ? GOLD : TEXT, false);
                g.drawString(font, floor, lx + lw - font.width(floor), cy, MUTED, false);
                cy += 10;
                pos++;
            }
        }
    }

    private void renderShadows(GuiGraphics g, Font font, Info info, int lx, int ly, int lw, int mx, int my) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.shadows").withStyle(ChatFormatting.BOLD), lx, ly, PURPLE, true);
        int top = ly + 14, bottom = ly + h;
        if (info.shadows().isEmpty()) {
            wrapped(g, font, Component.translatable("screen.isekai_mahou.endless_abyss.shadows_empty"), lx, top, lw, MUTED, 4);
            maxScroll = 0;
            scroll = 0;
            return;
        }
        int hint = wrapped(g, font, Component.translatable("screen.isekai_mahou.endless_abyss.shadows_hint"), lx, top, lw, FAINT, 2) + 4;
        top += hint;
        boolean canFight = info.runFloor() == 0;
        int listW = lw - 6;
        g.enableScissor(lx, top, lx + lw, bottom);
        int cy = top - scroll;
        for (AbyssNetwork.ShadowRow row : info.shadows()) {
            if (cy + ROW_H > top && cy < bottom) {
                boolean hover = mx >= lx && mx < lx + listW && my >= Math.max(cy, top) && my < Math.min(cy + ROW_H - 2, bottom);
                g.fill(lx, cy, lx + listW, cy + ROW_H - 2, hover ? CARD_HOVER : CARD);
                g.fill(lx, cy, lx + 2, cy + ROW_H - 2, PURPLE);
                int btnW = 62;
                Component name = bossName(row.boss());
                g.drawString(font, font.plainSubstrByWidth(name.getString(), listW - btnW - 14), lx + 6, cy + 2, TEXT, false);
                g.drawString(font, Component.translatable("screen.isekai_mahou.endless_abyss.shadow_row", row.wins()), lx + 6, cy + 11,
                        row.wins() > 0 ? GOLD : FAINT, false);
                int by = cy + 2;
                if (canFight && by >= top && by + 16 <= bottom) {
                    button(g, font, lx + listW - btnW - 3, by, btnW, 16, Component.translatable("screen.isekai_mahou.endless_abyss.fight"), mx, my,
                            PURPLE, () -> send(new Action(Action.SHADOW, 0, row.boss()), true));
                }
            }
            cy += ROW_H;
        }
        g.disableScissor();
        int contentH = info.shadows().size() * ROW_H;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int vh = bottom - top;
            int barH = Math.max(14, vh * vh / Math.max(1, contentH));
            int barY = top + (int) ((vh - barH) * (scroll / (float) maxScroll));
            g.fill(lx + lw - 3, top, lx + lw - 1, bottom, 0x40000000);
            g.fill(lx + lw - 3, barY, lx + lw - 1, barY + barH, PURPLE);
        }
    }

    /** Nome do chefe pela tradução da entidade (o cliente tem o registro). */
    private static Component bossName(String path) {
        ResourceLocation id = ResourceLocation.tryBuild(IsekaiMahou.MODID, path);
        if (id == null) return Component.literal(path);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> t.getDescription()).orElse(Component.literal(path));
    }

    private void send(Action action, boolean close) {
        PacketDistributor.sendToServer(action);
        lastRequest = 0;
        if (close) Minecraft.getInstance().setScreen(null);
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, int mx, int my, int accent, Runnable action) {
        boolean hover = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, mix(accent, 0xFF120D1A, hover ? 0.45F : 0.7F));
        g.renderOutline(bx, by, bw, bh, hover ? 0xFFFFFFFF : accent);
        String shown = font.plainSubstrByWidth(label.getString(), bw - 6);
        g.drawString(font, shown, bx + (bw - font.width(shown)) / 2, by + (bh - 8) / 2, TEXT, true);
        hits.add(new Hit(bx, by, bw, bh, action));
    }

    private static int wrapped(GuiGraphics g, Font font, Component text, int tx, int ty, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), tx, ty + i * 10, color, false);
        return n * 10;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (mx >= hit.x && my >= hit.y && mx < hit.x + hit.w && my < hit.y + hit.h) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 18)));
        return true;
    }
}
