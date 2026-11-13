package com.renanpontes.isekaimahou.feature.renown.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.reputation.ReputationPayloads;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.BossRow;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Category;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Request;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Row;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.TitleRow;
import com.renanpontes.isekaimahou.feature.renown.RenownRanking;
import com.renanpontes.isekaimahou.feature.renown.RenownTitles;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Renome" do menu B, com três seções (sub-abas no topo, para não lotar o menu):
 * <ul>
 *   <li><b>Ranking</b>: categorias à esquerda, os 10 melhores à direita e a posição do jogador embaixo;</li>
 *   <li><b>Títulos</b>: todos os títulos (liberados primeiro), com requisito e progresso; clique para usar/tirar;</li>
 *   <li><b>Recordes</b>: melhor tempo do servidor por chefe e o melhor do jogador.</li>
 * </ul>
 * Tudo desenhado à mão, sem widgets; nada pisca.
 */
public class RenownTab implements MenuTab {
    static final int RANKING = 0, TITLES = 1, RECORDS = 2;
    private static final String[] SECTION_KEYS = {"ranking", "titles", "records"};
    private static final String[] CATEGORY_ORDER = {RenownRanking.LEVEL, RenownRanking.BOSSES, RenownRanking.ABYSS, RenownRanking.TOWERS,
            RenownRanking.CITY_RICH, RenownRanking.CITY_POP};

    private static final int GOLD = 0xFFFFD86B, SILVER = 0xFFC9D2DC, BRONZE = 0xFFC0763A, TEXT = 0xFFE9E1F5, MUTED = 0xFFA99BC0,
            FAINT = 0xFF6F6485, GREEN = 0xFF7CFF8A;
    private static final int CARD = 0xFF1B1526, CARD_ALT = 0xFF211A2E, CARD_HOVER = 0xFF2B2140, ACCENT = 0xFFD49A22, LINE = 0x60D49A22;

    /** Seção e categoria abertas (lembradas enquanto o jogo está aberto). */
    static int section = RANKING;
    private static String category = RenownRanking.LEVEL;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, maxScroll;
    private long lastRequest;
    /** Lista de títulos já ordenada (refeita quando chega uma lista nova). */
    private RenownNetwork.Titles sortedFrom;
    private List<TitleRow> sorted = List.of();

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        lastRequest = 0;
        request();
    }

    private void request() {
        long now = Util.getMillis();
        if (now - lastRequest < 600) return;
        lastRequest = now;
        PacketDistributor.sendToServer(new Request(section == TITLES ? Request.TITLES : Request.BOARD));
    }

    @Override
    public void tick() {
        // Os números mudam devagar: a cada 5 s basta
        if (Util.getMillis() - lastRequest > 5000) request();
    }

    // ------------------------------------------------------------------------------------------------ desenho
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        // Sub-abas
        int cx = x;
        for (int i = 0; i < SECTION_KEYS.length; i++) {
            Component label = Component.translatable("screen.isekai_mahou.renown.section." + SECTION_KEYS[i]);
            int cw = font.width(label) + 14;
            boolean on = section == i, hover = inside(mx, my, cx, y, cw, 14);
            g.fill(cx, y, cx + cw, y + 14, on ? CARD_HOVER : hover ? CARD_ALT : CARD);
            if (on) g.fill(cx, y + 13, cx + cw, y + 14, ACCENT);
            g.drawString(font, label, cx + 7, y + 3, on ? GOLD : hover ? TEXT : MUTED, false);
            int target = i;
            hits.add(new Hit(cx, y, cw, 14, () -> {
                section = target;
                scroll = 0;
                lastRequest = 0;
                request();
            }));
            cx += cw + 3;
        }
        g.fill(x, y + 15, x + w, y + 16, LINE);
        int top = y + 21, bottom = y + h;
        switch (section) {
            case TITLES -> renderTitles(g, font, top, bottom, mx, my);
            case RECORDS -> renderRecords(g, font, top, bottom, mx, my);
            default -> renderRanking(g, font, top, bottom, mx, my);
        }
    }

    // ---------------------------------------------------------------- Ranking
    private void renderRanking(GuiGraphics g, Font font, int top, int bottom, int mx, int my) {
        maxScroll = 0;
        int leftW = Math.min(112, w / 3);
        int cy = top;
        for (String id : CATEGORY_ORDER) {
            boolean on = id.equals(category), hover = inside(mx, my, x, cy, leftW, 17);
            g.fill(x, cy, x + leftW, cy + 17, on ? CARD_HOVER : hover ? CARD_ALT : CARD);
            if (on) g.fill(x, cy, x + 2, cy + 17, ACCENT);
            String label = font.plainSubstrByWidth(Component.translatable("screen.isekai_mahou.renown.cat." + id).getString(), leftW - 10);
            g.drawString(font, label, x + 6, cy + 5, on ? GOLD : hover ? TEXT : MUTED, false);
            hits.add(new Hit(x, cy, leftW, 17, () -> category = id));
            cy += 19;
        }

        int rx = x + leftW + 8, rw = w - leftW - 8;
        Category cat = null;
        for (Category c : RenownClientState.board.categories()) if (c.id().equals(category)) cat = c;
        g.drawString(font, Component.translatable("screen.isekai_mahou.renown.cat." + category + ".long").withStyle(ChatFormatting.BOLD),
                rx, top, GOLD, true);
        int ry = top + 13;
        if (cat == null || cat.rows().isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.renown.empty"), rx, ry + 2, FAINT, false);
            return;
        }
        boolean city = isCity(category);
        int rowH = Math.max(12, Math.min(16, (bottom - 14 - ry) / Math.max(1, cat.rows().size())));
        int pos = 1;
        Component tip = null;
        for (Row r : cat.rows()) {
            if (ry + rowH > bottom - 13) break;
            g.fill(rx, ry, rx + rw, ry + rowH - 1, r.self() ? CARD_HOVER : pos % 2 == 0 ? CARD_ALT : CARD);
            if (r.self()) g.fill(rx, ry, rx + 2, ry + rowH - 1, GREEN);
            int ty = ry + (rowH - 9) / 2;
            int rankColor = pos == 1 ? GOLD : pos == 2 ? SILVER : pos == 3 ? BRONZE : FAINT;
            g.drawString(font, pos + ".", rx + 5, ty, rankColor, false);
            Component value = valueText(category, r.value());
            int vw = font.width(value);
            g.drawString(font, value, rx + rw - vw - 5, ty, pos == 1 ? GOLD : TEXT, false);
            // Nome (+ título em uso, ou o dono da cidade) no espaço que sobra
            int nameX = rx + 24, room = rw - 24 - vw - 12;
            String name = font.plainSubstrByWidth(r.name(), room);
            g.drawString(font, name, nameX, ty, r.self() ? GREEN : TEXT, false);
            int used = font.width(name) + 6;
            Component extra = city ? Component.translatable("screen.isekai_mahou.renown.city_owner", r.detail())
                    : r.title().isEmpty() ? null : Component.translatable(r.title());
            if (extra != null && room - used > 30) {
                int color = city ? FAINT : dim(RenownTitles.tierColor(RenownTitles.tierOf(r.title())));
                g.drawString(font, font.plainSubstrByWidth(extra.getString(), room - used), nameX + used, ty, color, false);
            }
            if (inside(mx, my, rx, ry, rw, rowH - 1)) tip = detailText(category, r);
            ry += rowH;
            pos++;
        }
        Component mine = cat.selfRank() > 0
                ? Component.translatable("screen.isekai_mahou.renown.your_rank", cat.selfRank(), valueText(category, cat.selfValue()))
                : Component.translatable(city ? "screen.isekai_mahou.renown.no_city" : "screen.isekai_mahou.renown.unranked");
        g.drawString(font, mine, rx, bottom - 10, cat.selfRank() > 0 ? GREEN : FAINT, false);
        // Por último, para ficar por cima das linhas
        if (tip != null) hover(g, font, tip, mx, my);
    }

    private static boolean isCity(String cat) {
        return RenownRanking.CITY_RICH.equals(cat) || RenownRanking.CITY_POP.equals(cat);
    }

    private static Component valueText(String cat, long value) {
        return Component.translatable("screen.isekai_mahou.renown.value." + cat, value);
    }

    /** Linha de detalhe (aparece ao passar o mouse): classe e Círculo, chefes diferentes, andares... */
    private static Component detailText(String cat, Row r) {
        return switch (cat) {
            case RenownRanking.LEVEL -> Component.translatable("screen.isekai_mahou.renown.detail.level", className(r.detail()), r.aux());
            case RenownRanking.BOSSES -> Component.translatable("screen.isekai_mahou.renown.detail.bosses", r.aux());
            case RenownRanking.ABYSS -> Component.translatable("screen.isekai_mahou.renown.detail.abyss", r.aux());
            case RenownRanking.TOWERS -> Component.translatable("screen.isekai_mahou.renown.detail.towers", r.aux());
            default -> Component.translatable("screen.isekai_mahou.renown.detail.city", r.detail(), r.aux());
        };
    }

    private static Component className(String pathId) {
        MagicPath path = pathId == null || pathId.isEmpty() ? null : MagicPath.byId(pathId);
        return path == null || !path.isPlayable() ? Component.translatable("screen.isekai_mahou.renown.no_class") : path.displayName();
    }

    // ---------------------------------------------------------------- Títulos
    private List<TitleRow> sortedTitles() {
        RenownNetwork.Titles t = RenownClientState.titles;
        if (t != sortedFrom) {
            sortedFrom = t;
            List<TitleRow> list = new ArrayList<>(t.rows());
            // Em uso → liberados → os mais perto de liberar
            list.sort(Comparator.<TitleRow>comparingInt(r -> r.key().equals(t.selected()) ? 0 : r.unlocked() ? 1 : 2)
                    .thenComparingDouble(r -> r.unlocked() ? 0 : -(r.progress() / (double) Math.max(1, r.goal()))));
            sorted = list;
        }
        return sorted;
    }

    private void renderTitles(GuiGraphics g, Font font, int top, int bottom, int mx, int my) {
        RenownNetwork.Titles t = RenownClientState.titles;
        List<TitleRow> rows = sortedTitles();
        int unlocked = 0;
        for (TitleRow r : rows) if (r.unlocked()) unlocked++;
        MutableComponent current = t.selected().isEmpty() ? Component.translatable("screen.isekai_mahou.renown.title_none")
                : RenownTitles.display(t.selected());
        g.drawString(font, Component.translatable("screen.isekai_mahou.renown.title_current", current), x, top, TEXT, false);
        Component count = Component.translatable("screen.isekai_mahou.renown.title_count", unlocked, rows.size());
        g.drawString(font, count, x + w - font.width(count), top, MUTED, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.renown.title_hint"), x, top + 11, FAINT, false);

        int listTop = top + 24, rowH = 24, listW = w - 6;
        g.enableScissor(x, listTop, x + w, bottom);
        int cy = listTop - scroll;
        for (TitleRow r : rows) {
            if (cy + rowH > listTop && cy < bottom) {
                boolean using = r.key().equals(t.selected());
                boolean hover = r.unlocked() && my >= listTop && my < bottom && inside(mx, my, x, cy, listW, rowH - 2);
                int tier = RenownTitles.tierColor(r.tier());
                g.fill(x, cy, x + listW, cy + rowH - 2, hover ? CARD_HOVER : using ? CARD_ALT : CARD);
                g.fill(x, cy, x + 2, cy + rowH - 2, r.unlocked() ? 0xFF000000 | tier : 0xFF3A3348);
                Component status = using ? Component.translatable("screen.isekai_mahou.renown.title_using")
                        : r.unlocked() ? Component.translatable("screen.isekai_mahou.renown.title_use")
                        : Component.literal(r.progress() + " / " + r.goal());
                int sw = font.width(status);
                g.drawString(font, status, x + listW - sw - 6, cy + 3, using ? GOLD : r.unlocked() ? (hover ? TEXT : MUTED) : FAINT, false);
                g.drawString(font, font.plainSubstrByWidth(Component.translatable(r.key()).getString(), listW - sw - 20), x + 7, cy + 3,
                        r.unlocked() ? 0xFF000000 | tier : MUTED, false);
                Component desc = r.feat() ? Component.translatable(r.key() + ".desc") : Component.translatable("screen.isekai_mahou.renown.title_other");
                g.drawString(font, font.plainSubstrByWidth(desc.getString(), listW - 80), x + 7, cy + 13, FAINT, false);
                if (!r.unlocked() && r.goal() > 0) {
                    // Barrinha de progresso fina, à direita
                    int bw = 60, bx = x + listW - bw - 6, by = cy + 16;
                    g.fill(bx, by, bx + bw, by + 2, 0xFF2E2740);
                    g.fill(bx, by, bx + (int) (bw * Math.min(1F, r.progress() / (float) r.goal())), by + 2, dim(tier));
                }
                int hy = Math.max(cy, listTop), hb = Math.min(cy + rowH - 2, bottom);
                if (r.unlocked() && hb > hy) {
                    hits.add(new Hit(x, hy, listW, hb - hy, () -> select(using ? "" : r.key())));
                }
            }
            cy += rowH;
        }
        g.disableScissor();
        scrollbar(g, listTop, bottom, rows.size() * rowH);
    }

    private void select(String key) {
        PacketDistributor.sendToServer(new ReputationPayloads.SelectTitle(key));
        PacketDistributor.sendToServer(new Request(Request.TITLES));
        lastRequest = Util.getMillis();
    }

    // ---------------------------------------------------------------- Recordes
    private void renderRecords(GuiGraphics g, Font font, int top, int bottom, int mx, int my) {
        List<BossRow> rows = RenownClientState.board.bosses();
        g.drawString(font, Component.translatable("screen.isekai_mahou.renown.records_hint"), x, top, FAINT, false);
        int listTop = top + 13, rowH = 24, listW = w - 6;
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.renown.records_empty"), x, listTop + 2, MUTED, false);
            maxScroll = 0;
            scroll = 0;
            return;
        }
        g.enableScissor(x, listTop, x + w, bottom);
        int cy = listTop - scroll;
        int i = 0;
        for (BossRow r : rows) {
            if (cy + rowH > listTop && cy < bottom) {
                boolean holder = r.mine() > 0 && r.mine() <= r.ticks();
                g.fill(x, cy, x + listW, cy + rowH - 2, i % 2 == 0 ? CARD : CARD_ALT);
                g.fill(x, cy, x + 2, cy + rowH - 2, holder ? GREEN : ACCENT);
                String time = FightTimerHud.format(r.ticks());
                int tw = font.width(time);
                g.drawString(font, time, x + listW - tw - 6, cy + 3, GOLD, false);
                Component mine = r.mine() > 0 ? Component.translatable("screen.isekai_mahou.renown.records_mine", FightTimerHud.format(r.mine()))
                        : Component.translatable("screen.isekai_mahou.renown.records_mine_none");
                int mw = font.width(mine);
                g.drawString(font, mine, x + listW - mw - 6, cy + 13, holder ? GREEN : r.mine() > 0 ? MUTED : FAINT, false);
                g.drawString(font, font.plainSubstrByWidth(bossName(r.boss()).getString(), listW - tw - 20), x + 7, cy + 3, TEXT, false);
                Component by = Component.translatable("screen.isekai_mahou.renown.records_by", r.holders(), r.day() + 1);
                g.drawString(font, font.plainSubstrByWidth(by.getString(), listW - mw - 20), x + 7, cy + 13, FAINT, false);
            }
            cy += rowH;
            i++;
        }
        g.disableScissor();
        scrollbar(g, listTop, bottom, rows.size() * rowH);
    }

    /** Nome do chefe pela tradução da entidade (o cliente tem o registro). */
    private static Component bossName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Component.literal(id);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(t -> t.getDescription()).orElse(Component.literal(rl.getPath()));
    }

    // ------------------------------------------------------------------------------------------------ utilidades
    private void scrollbar(GuiGraphics g, int top, int bottom, int contentH) {
        int vh = bottom - top;
        maxScroll = Math.max(0, contentH - vh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (maxScroll <= 0) return;
        int barH = Math.max(14, vh * vh / Math.max(1, contentH));
        int barY = top + (int) ((vh - barH) * (scroll / (float) maxScroll));
        g.fill(x + w - 3, top, x + w - 1, bottom, 0x40000000);
        g.fill(x + w - 3, barY, x + w - 1, barY + barH, ACCENT);
    }

    /** Dica pequena junto do mouse (uma linha). */
    private static void hover(GuiGraphics g, Font font, Component text, int mx, int my) {
        int tw = font.width(text);
        int tx = Math.min(mx + 10, g.guiWidth() - tw - 8), ty = my - 14;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xF0120D1A);
        g.renderOutline(tx - 3, ty - 3, tw + 6, 14, LINE);
        g.drawString(font, text, tx, ty, MUTED, false);
        g.pose().popPose();
    }

    private static boolean inside(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && my >= by && mx < bx + bw && my < by + bh;
    }

    /** A cor um pouco apagada (títulos de outros jogadores no quadro: presentes, mas sem gritar). */
    private static int dim(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (r * 7 / 10) << 16 | (gr * 7 / 10) << 8 | (b * 7 / 10);
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
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 20)));
        return true;
    }
}
