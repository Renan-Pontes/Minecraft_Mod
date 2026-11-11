package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.journal.bestiary.Bestiary;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Aba "Bestiário" do menu B: todas as criaturas do mod por categoria, com busca, abates, modelo 3D girando
 * (arraste para girar), afinidades elementais (fraquezas/resistências) e descrição. Criaturas ainda não registradas
 * aparecem como "???".
 */
public class BestiaryTab implements MenuTab {
    private static final int LIST_W = 150;
    private static final int ROW_H = 12;
    private static final int PREVIEW_W = 100, PREVIEW_H = 118;
    /** Lembrados entre aberturas do menu. */
    private static ResourceLocation lastSelected;
    private static String lastSearch = "";

    private int x, y, w, h;
    private List<EntityType<?>> all = List.of();
    private final List<Row> rows = new ArrayList<>();
    private EntityType<?> selected;
    private EditBox search;
    private int listScroll, listMax;
    private int descScroll, descMax;
    private float dragYaw;
    private boolean dragging;
    private int listTop, listBottom;
    private int descX, descY, descW, descH;
    private int previewX, previewY;
    /** Elemento sob o mouse (tooltip desenhado por último, por cima de tudo). */
    private Element hoverElement;

    /** Linha da lista: cabeçalho de categoria (type == null) ou uma criatura. */
    private record Row(Bestiary.Category category, EntityType<?> type) {}

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = Minecraft.getInstance().font;
        all = Bestiary.entries();
        search = screen.addTabWidget(new EditBox(font, x + 1, y + 23, LIST_W - 2, 14, Component.translatable("screen.isekai_mahou.bestiary.search")));
        search.setHint(Component.translatable("screen.isekai_mahou.bestiary.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(40);
        search.setValue(lastSearch);
        search.setResponder(s -> {
            lastSearch = s;
            listScroll = 0;
            rebuildRows();
        });
        rebuildRows();
        if (lastSelected != null) {
            for (EntityType<?> t : all) if (EntityType.getKey(t).equals(lastSelected)) selected = t;
        }
        if (selected == null) {
            MagicData d = ClientMagic.data();
            selected = all.stream().filter(t -> Bestiary.isRevealed(d, t)).findFirst().orElse(all.isEmpty() ? null : all.get(0));
        }
    }

    private void rebuildRows() {
        rows.clear();
        MagicData d = ClientMagic.data();
        String q = lastSearch.trim().toLowerCase(Locale.ROOT);
        Bestiary.Category current = null;
        for (EntityType<?> t : all) {
            if (!q.isEmpty()) {
                if (!Bestiary.isRevealed(d, t)) continue;
                if (!t.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            }
            Bestiary.Category c = Bestiary.category(t);
            if (c != current) {
                rows.add(new Row(c, null));
                current = c;
            }
            rows.add(new Row(c, t));
        }
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.header").withStyle(ChatFormatting.BOLD), x, y, JournalUi.GOLD, true);
        int known = (int) all.stream().filter(t -> Bestiary.isRevealed(d, t)).count();
        g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.discovered", known, all.size()), x, y + 11, JournalUi.MUTED, false);
        JournalUi.bar(g, x + 96, y + 13, LIST_W - 98, 3, all.isEmpty() ? 0 : known / (float) all.size(), 0x8FD16A);

        renderList(g, font, d, mx, my);
        if (selected == null) {
            g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.bestiary.empty"), x + LIST_W + (w - LIST_W) / 2, y + h / 2, JournalUi.MUTED);
            return;
        }
        renderDetails(g, font, d, mx, my);
    }

    private void renderList(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        listTop = y + 40;
        listBottom = y + h;
        int contentH = rows.size() * ROW_H;
        listMax = Math.max(0, contentH - (listBottom - listTop));
        listScroll = Mth.clamp(listScroll, 0, listMax);
        g.fill(x, listTop - 1, x + LIST_W, listBottom, 0x30000000);
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.empty"), x + 4, listTop + 4, JournalUi.FAINT, false);
            return;
        }
        g.enableScissor(x, listTop, x + LIST_W, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = listTop + i * ROW_H - listScroll;
            if (ry + ROW_H < listTop || ry > listBottom) continue;
            if (r.type() == null) {
                g.fill(x, ry, x + LIST_W, ry + ROW_H, 0x40000000);
                g.drawString(font, r.category().displayName().withStyle(ChatFormatting.BOLD), x + 4, ry + 2, 0xFF000000 | r.category().color, false);
                continue;
            }
            EntityType<?> t = r.type();
            boolean revealed = Bestiary.isRevealed(d, t);
            boolean hover = JournalUi.inside(mx, my, x, ry, LIST_W, ROW_H) && my >= listTop && my < listBottom;
            if (t == selected) g.fill(x, ry, x + LIST_W, ry + ROW_H, 0x50000000 | (r.category().color & 0xFFFFFF));
            else if (hover) g.fill(x, ry, x + LIST_W, ry + ROW_H, 0x25FFFFFF);
            g.fill(x + 5, ry + 4, x + 8, ry + 7, revealed ? 0xFF000000 | r.category().color : 0xFF3A3448);
            int kills = Bestiary.kills(d, t);
            String count = kills > 0 ? "×" + kills : "";
            int cw = font.width(count);
            Component name = revealed ? t.getDescription() : Component.translatable("screen.isekai_mahou.bestiary.unknown");
            g.drawString(font, JournalUi.ellipsize(font, name.copy(), LIST_W - 18 - cw), x + 12, ry + 2,
                    revealed ? JournalUi.TEXT : JournalUi.FAINT, false);
            if (kills > 0) g.drawString(font, count, x + LIST_W - cw - 4, ry + 2, JournalUi.MUTED, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, x + LIST_W + 2, listTop, listBottom, listScroll, listMax, contentH);
    }

    private void renderDetails(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        hoverElement = null;
        renderInfo(g, font, d, mx, my);
        if (hoverElement != null) g.renderTooltip(font, hoverElement.displayName(), mx, my);
    }

    private void renderInfo(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        EntityType<?> t = selected;
        Bestiary.Category cat = Bestiary.category(t);
        boolean revealed = Bestiary.isRevealed(d, t);
        int rx = x + LIST_W + 10, rw = w - LIST_W - 10;

        // Moldura do modelo 3D
        previewX = rx;
        previewY = y;
        int px1 = rx + PREVIEW_W, py1 = y + PREVIEW_H;
        g.fillGradient(rx, y, px1, py1, 0xF0231B33, 0xF00C0912);
        g.renderOutline(rx, y, PREVIEW_W, PREVIEW_H, revealed ? 0xFF000000 | cat.color : JournalUi.BORDER_SOFT);
        // "chão" com o brilho da categoria
        g.fillGradient(rx + 1, py1 - 22, px1 - 1, py1 - 1, 0x00000000, 0x40000000 | (cat.color & 0xFFFFFF));
        if (revealed) {
            float yaw = dragging ? dragYaw : dragYaw + autoYaw();
            if (!EntityPreview.render(g, t, rx + 1, y + 1, px1 - 1, py1 - 1, yaw)) {
                g.drawCenteredString(font, "?", rx + PREVIEW_W / 2, y + PREVIEW_H / 2 - 4, JournalUi.FAINT);
            }
            g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.bestiary.drag_hint"), rx + PREVIEW_W / 2, py1 - 10, 0x90A79BB8);
        } else {
            g.pose().pushPose();
            g.pose().translate(rx + PREVIEW_W / 2F, y + PREVIEW_H / 2F - 10, 0);
            g.pose().scale(4F, 4F, 1F);
            g.drawCenteredString(font, "?", 0, 0, 0xFF3A3448);
            g.pose().popPose();
        }

        // Informações ao lado
        int ix = px1 + 8, iw = x + w - ix;
        int iy = y;
        Component name = revealed ? t.getDescription() : Component.translatable("screen.isekai_mahou.bestiary.unknown");
        g.drawString(font, JournalUi.ellipsize(font, name.copy().withStyle(ChatFormatting.BOLD), iw), ix, iy, revealed ? 0xFF000000 | cat.color : JournalUi.FAINT, true);
        iy += 11;
        g.drawString(font, cat.displayName(), ix, iy, JournalUi.MUTED, false);
        iy += 13;
        int kills = Bestiary.kills(d, t);
        g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.kills", kills), ix, iy, JournalUi.TEXT, false);
        iy += 10;
        if (cat == Bestiary.Category.BOSS) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.defeated", Bestiary.bossDefeats(d, t)), ix, iy, JournalUi.TEXT, false);
            iy += 10;
        }
        LivingEntity preview = revealed ? EntityPreview.get(t) : null;
        if (preview != null) {
            String hp = (preview.getMaxHealth() % 1F == 0F) ? String.valueOf((int) preview.getMaxHealth()) : String.format(Locale.ROOT, "%.1f", preview.getMaxHealth());
            g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.health", hp), ix, iy, 0xFFFF8F8F, false);
            iy += 10;
        }
        iy += 4;

        // Afinidades elementais (fraquezas e resistências contra magia)
        g.drawString(font, Component.translatable("screen.isekai_mahou.bestiary.affinities").withStyle(ChatFormatting.UNDERLINE), ix, iy, JournalUi.GOLD, false);
        iy += 12;
        int colW = Math.max(60, iw / 2);
        for (int i = 0; i < Element.BASE.size(); i++) {
            Element e = Element.BASE.get(i);
            int ex = ix + (i % 2) * colW, ey = iy + (i / 2) * 11;
            g.fill(ex, ey + 1, ex + 6, ey + 7, 0xFF000000 | e.color);
            g.renderOutline(ex, ey + 1, 6, 6, 0x60000000);
            Component label;
            int color;
            if (!revealed) {
                label = Component.literal("?");
                color = JournalUi.FAINT;
            } else {
                float m = ElementReactions.multiplier(t, e);
                if (m <= 0F) {
                    label = Component.translatable("screen.isekai_mahou.bestiary.immune");
                    color = 0xFFB0B0B0;
                } else if (m > 1.01F) {
                    label = Component.translatable("screen.isekai_mahou.bestiary.weak", fmt(m));
                    color = JournalUi.RED;
                } else if (m < 0.99F) {
                    label = Component.translatable("screen.isekai_mahou.bestiary.resist", fmt(m));
                    color = JournalUi.BLUE;
                } else {
                    label = Component.translatable("screen.isekai_mahou.bestiary.normal");
                    color = JournalUi.FAINT;
                }
            }
            g.drawString(font, JournalUi.ellipsize(font, label.copy(), colW - 10), ex + 9, ey, color, false);
            if (JournalUi.inside(mx, my, ex, ey, colW - 2, 10)) hoverElement = e;
        }

        // Descrição (com rolagem)
        descX = rx;
        descY = y + PREVIEW_H + 6;
        descW = rw;
        descH = y + h - descY;
        if (descH < 24) return;
        JournalUi.panel(g, descX, descY, descW, descH, JournalUi.BORDER_SOFT);
        Component desc;
        if (!revealed) {
            desc = Component.translatable("screen.isekai_mahou.bestiary.locked_desc");
        } else {
            String key = Bestiary.descriptionKey(t);
            desc = I18n.exists(key) ? Component.translatable(key) : Component.translatable("screen.isekai_mahou.bestiary.no_desc");
        }
        int top = descY + 5, bottom = descY + descH - 4;
        List<FormattedCharSequence> lines = font.split(desc, descW - 16);
        int contentH = lines.size() * JournalUi.LINE;
        descMax = Math.max(0, contentH - (bottom - top));
        descScroll = Mth.clamp(descScroll, 0, descMax);
        g.enableScissor(descX + 1, top, descX + descW - 1, bottom);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), descX + 7, top + i * JournalUi.LINE - descScroll, revealed ? JournalUi.TEXT : JournalUi.FAINT, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, descX + descW - 4, top, bottom, descScroll, descMax, contentH);
    }

    /** Giro automático: uma volta a cada 30 s (o resto da divisão fecha em 360°, sem saltos). */
    private static float autoYaw() {
        return (Util.getMillis() % 30000L) * 360F / 30000F;
    }

    private static String fmt(float m) {
        return m == (int) m ? String.valueOf((int) m) : String.format(Locale.ROOT, "%.2f", m).replaceAll("0+$", "");
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= x && mx < x + LIST_W && my >= listTop && my < listBottom) {
            int i = (int) ((my - listTop + listScroll) / ROW_H);
            if (i >= 0 && i < rows.size() && rows.get(i).type() != null && rows.get(i).type() != selected) {
                selected = rows.get(i).type();
                lastSelected = EntityType.getKey(selected);
                descScroll = 0;
                dragYaw = 0;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F));
            }
            return true;
        }
        if (button == 0 && JournalUi.inside(mx, my, previewX, previewY, PREVIEW_W, PREVIEW_H)) {
            // Congela a rotação automática no ângulo atual e passa a seguir o arrasto.
            dragYaw += autoYaw();
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        dragYaw += (float) dx * 2.5F;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        // Volta a girar sozinho a partir do ângulo em que o jogador soltou.
        dragYaw -= autoYaw();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= x && mx < x + LIST_W + 4 && my >= listTop && my < listBottom && listMax > 0) {
            listScroll = Mth.clamp(listScroll - (int) (sy * ROW_H * 2), 0, listMax);
            return true;
        }
        if (descH > 0 && JournalUi.inside(mx, my, descX, descY, descW, descH) && descMax > 0) {
            descScroll = Mth.clamp(descScroll - (int) (sy * 15), 0, descMax);
            return true;
        }
        return false;
    }
}
