package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Painel de uma cidade (usado na tela do coração e na aba "Cidade" do menu B). Desenha tudo à mão com áreas
 * clicáveis, lendo o estado de {@link ClientSettlements}. Páginas do Senhor: Visão geral, Plantas, Obras, Decretos e
 * Guerra; visitante: informações e cidadania; cidade de reino: Juramento e desafio.
 */
public final class SettlementPanel {
    static final int TEXT = 0xFFF3E6C8, MUTED = 0xFFB8A27A, FAINT = 0xFF7D6C52;
    static final int GREEN = 0xFF7DDA6B, RED = 0xFFE8735E, GOLD = 0xFFFFD86B, BLUE = 0xFF8FC8FF;
    static final int CARD = 0xC0201810, CARD_HOVER = 0xE0302418, BORDER = 0xFFC9A55A, BORDER_DIM = 0x70C9A55A;
    private static final String[] ROLES = {"builder", "resident", "guard", "merchant", "militia"};
    private static final ItemStack[] ROLE_ICONS = {new ItemStack(Items.IRON_AXE), new ItemStack(Items.RED_BED),
            new ItemStack(Items.IRON_SWORD), new ItemStack(Items.EMERALD), new ItemStack(Items.SHIELD)};

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private int x, y, w, h;
    private String page = "overview";
    private int scroll;
    private int maxScroll;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable private List<Component> tooltip;
    private String confirm = "";
    private long confirmUntil;

    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private CompoundTag st() {
        return ClientSettlements.state();
    }

    private String city() {
        return st().getString("City");
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        tooltip = null;
        maxScroll = 0;
        CompoundTag t = st();
        String mode = t.getString("Mode");
        if (mode.isEmpty() || mode.equals("none")) {
            empty(g);
            return;
        }
        int color = 0xFF000000 | (t.contains("Color") ? t.getInt("Color") : 0xFFD86B);
        header(g, t, color);
        int top = y + 34;
        switch (mode) {
            case "manage" -> manage(g, t, mx, my, top, color);
            case "candidate" -> candidate(g, t, mx, my, top);
            default -> visitor(g, t, mx, my, top);
        }
        if (tooltip != null) g.renderComponentTooltip(font(), tooltip, mx, my);
    }

    private void empty(GuiGraphics g) {
        int cy = y + h / 2 - 20;
        center(g, Component.translatable("settlements.isekai_mahou.ui.none.title"), cy, GOLD);
        wrap(g, Component.translatable("settlements.isekai_mahou.ui.none.text"), x + 20, cy + 16, w - 40, MUTED);
    }

    private void header(GuiGraphics g, CompoundTag t, int color) {
        Font f = font();
        g.fill(x, y, x + w, y + 30, 0x90000000);
        g.fill(x, y + 30, x + w, y + 31, color);
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 4, 0);
        g.pose().scale(1.4F, 1.4F, 1F);
        g.drawString(f, ClientSettlements.text(t, "Name"), 0, 0, color, true);
        g.pose().popPose();
        List<Component> sub = new ArrayList<>();
        String kind = t.getString("Kind");
        if (t.contains("Style")) sub.add(SettlementsLang.tr("style." + t.getString("Style")));
        sub.add(SettlementsLang.tr("ui.kind." + kind));
        if (!t.getString("Lord").isEmpty()) sub.add(Component.translatable("settlements.isekai_mahou.ui.lord", t.getString("Lord")));
        if (t.contains("Level")) sub.add(Component.translatable("settlements.isekai_mahou.ui.level", t.getInt("Level"), t.getInt("MaxLevel")));
        MutableComponent line = Component.empty();
        for (int i = 0; i < sub.size(); i++) {
            if (i > 0) line.append(Component.literal("  ·  ").withColor(FAINT));
            line.append(sub.get(i));
        }
        g.drawString(f, line, x + 6, y + 19, MUTED, false);
        if (t.getBoolean("Citizen")) {
            Component c = Component.translatable("settlements.isekai_mahou.ui.you_citizen");
            g.drawString(f, c, x + w - f.width(c) - 6, y + 4, GREEN, false);
        }
        if (t.getBoolean("Enemy")) {
            Component c = Component.translatable("settlements.isekai_mahou.ui.you_enemy");
            g.drawString(f, c, x + w - f.width(c) - 6, y + 4, RED, false);
        }
    }

    // =====================================================================================
    //  Senhor
    // =====================================================================================
    private void manage(GuiGraphics g, CompoundTag t, int mx, int my, int top, int color) {
        boolean own = "own".equals(t.getString("Kind"));
        String[] pages = own ? new String[] {"overview", "blueprints", "works", "decrees", "war"}
                : new String[] {"overview", "decrees", "war"};
        boolean valid = false;
        for (String p : pages) valid |= p.equals(page);
        if (!valid) page = "overview";
        int bw = Math.min(96, (w - 8) / pages.length - 4);
        for (int i = 0; i < pages.length; i++) {
            String p = pages[i];
            button(g, mx, my, x + 4 + i * (bw + 4), top, bw, 14, SettlementsLang.tr("ui.page." + p),
                    true, page.equals(p), () -> {
                        page = p;
                        scroll = 0;
                    }, null);
        }
        int cy = top + 20;
        switch (page) {
            case "blueprints" -> blueprints(g, t, mx, my, cy);
            case "works" -> works(g, t, mx, my, cy);
            case "decrees" -> decrees(g, t, mx, my, cy);
            case "war" -> war(g, t, mx, my, cy);
            default -> overview(g, t, mx, my, cy, own, color);
        }
    }

    private void overview(GuiGraphics g, CompoundTag t, int mx, int my, int cy, boolean own, int color) {
        Font f = font();
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        int ly = cy;
        if (own) {
            int next = t.getInt("Next"), prev = t.getInt("Prev"), pts = t.getInt("Points");
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.prosperity"), lx, ly, TEXT, false);
            float frac = next < 0 ? 1F : (pts - prev) / (float) Math.max(1, next - prev);
            bar(g, lx, ly + 11, col, 6, frac, color);
            Component pv = next < 0 ? Component.translatable("settlements.isekai_mahou.ui.max_level")
                    : Component.translatable("settlements.isekai_mahou.ui.points", pts, next);
            g.drawString(f, pv, lx, ly + 20, MUTED, false);
            ly += 32;
            if (t.getBoolean("NeedHall")) {
                ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.need_hall"), lx, ly, col, RED) + 4;
            }
        }
        int aff = t.getInt("Affinity");
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.affinity", aff), lx, ly, TEXT, false);
        bar(g, lx, ly + 11, col, 6, aff / 100F, aff >= 100 ? GOLD : 0xFFE07BA8);
        int d = t.getInt("AffDelta");
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.aff_delta", (d >= 0 ? "+" : "") + d), lx, ly + 20,
                d >= 0 ? GREEN : RED, false);
        ly += 32;
        if (aff >= 100) ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.aff_max"), lx, ly, col, GOLD) + 4;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.treasury", t.getInt("Treasury")), lx, ly, GOLD, false);
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.income", t.getInt("Income"), t.getInt("TradesToday")),
                lx, ly + 10, MUTED, false);
        ly += 24;
        boolean atHeart = t.getBoolean("AtHeart");
        List<Component> heartTip = atHeart ? null : List.of(Component.translatable("settlements.isekai_mahou.treasury.at_heart"));
        int half = (col - 4) / 2;
        button(g, mx, my, lx, ly, half, 14, Component.translatable("settlements.isekai_mahou.ui.withdraw", 64),
                atHeart && t.getInt("Treasury") > 0, false, () -> act("withdraw", "64"), heartTip);
        button(g, mx, my, lx + half + 4, ly, half, 14, Component.translatable("settlements.isekai_mahou.ui.withdraw_all"),
                atHeart && t.getInt("Treasury") > 0, false, () -> act("withdraw", "640"), heartTip);
        ly += 18;
        button(g, mx, my, lx, ly, col, 14, Component.translatable("settlements.isekai_mahou.ui.festival", 40), t.getBoolean("FestivalOk"),
                false, () -> act("festival", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.festival.tip")));

        int ry = cy;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.population"), rx, ry, TEXT, false);
        ry += 12;
        CompoundTag pop = t.getCompound("Pop"), cap = t.getCompound("Cap");
        for (int i = 0; i < ROLES.length; i++) {
            if (cap.getInt(ROLES[i]) <= 0 && pop.getInt(ROLES[i]) <= 0) continue;
            g.renderItem(ROLE_ICONS[i], rx, ry - 2);
            g.drawString(f, SettlementsLang.tr("role." + ROLES[i]), rx + 20, ry + 2, TEXT, false);
            Component n = Component.literal(pop.getInt(ROLES[i]) + " / " + cap.getInt(ROLES[i]));
            g.drawString(f, n, rx + col - f.width(n) - 4, ry + 2, pop.getInt(ROLES[i]) >= cap.getInt(ROLES[i]) ? GREEN : MUTED, false);
            ry += 18;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.citizens", t.getInt("Citizens")), rx, ry + 2, MUTED, false);
        ry += 16;
        wars(g, t, mx, my, rx, ry, col, false);
        String key = own ? "abandon" : "abdicate";
        boolean armed = confirm.equals(key) && Util.getMillis() < confirmUntil;
        button(g, mx, my, rx, y + h - 18, col, 14,
                SettlementsLang.tr("ui." + key + (armed ? ".confirm" : "")), true, armed, () -> {
                    if (confirm.equals(key) && Util.getMillis() < confirmUntil) {
                        act(key, "confirm");
                        confirm = "";
                    } else {
                        confirm = key;
                        confirmUntil = Util.getMillis() + 4000;
                    }
                }, List.of(SettlementsLang.tr("ui." + key + ".tip")));
    }

    private void blueprints(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag list = t.getList("Blueprints", Tag.TAG_COMPOUND);
        int rowH = 24;
        int area = y + h - cy - 2;
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, cy, x + w, cy + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int ry = cy + i * rowH - scroll;
            if (ry + rowH < cy || ry > cy + area) continue;
            boolean ok = b.getString("Problem").isEmpty();
            boolean hover = mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + rowH - 2 && my >= cy && my < cy + area;
            g.fill(x + 4, ry, x + w - 4, ry + rowH - 2, hover && ok ? CARD_HOVER : CARD);
            Component name = Component.translatable(b.getString("Lang"));
            int nameColor = b.getBoolean("Wonder") ? GOLD : TEXT;
            g.drawString(f, b.getBoolean("Wonder") ? Component.literal("★ ").append(name) : name, x + 8, ry + 3, nameColor, false);
            Component info = Component.translatable("settlements.isekai_mahou.ui.bp.info", b.getInt("MinLevel"), b.getInt("Points"),
                    b.getInt("Count") + b.getInt("Building"), b.getInt("Max"));
            g.drawString(f, info, x + 8, ry + 13, FAINT, false);
            Component status = ok ? Component.translatable("settlements.isekai_mahou.ui.bp.build")
                    : ClientSettlements.text(b, "Problem");
            int sw = f.width(status);
            g.drawString(f, status, x + w - 10 - sw, ry + 8, ok ? GREEN : RED, false);
            if (hover && my >= cy && my < cy + area) {
                List<Component> tip = new ArrayList<>();
                tip.add(name.copy().withColor(nameColor));
                for (FormattedCharSequence s : f.split(Component.translatable(b.getString("Lang") + ".desc"), 220)) {
                    tip.add(Component.literal(toString(s)).withColor(MUTED));
                }
                tooltip = tip;
                if (ok) {
                    String key = b.getString("Key");
                    hits.add(new Hit(x + 4, Math.max(ry, cy), w - 8, rowH - 2, () -> act("build", key)));
                }
            }
        }
        g.disableScissor();
        if (list.isEmpty()) wrap(g, Component.translatable("settlements.isekai_mahou.ui.bp.none"), x + 8, cy + 4, w - 16, MUTED);
    }

    private void works(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag list = t.getList("Projects", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            wrap(g, Component.translatable("settlements.isekai_mahou.ui.works.none"), x + 8, cy + 4, w - 16, MUTED);
            return;
        }
        int ry = cy;
        for (int i = 0; i < list.size() && ry < y + h - 20; i++) {
            CompoundTag p = list.getCompound(i);
            g.fill(x + 4, ry, x + w - 4, ry + 26, CARD);
            boolean active = p.getBoolean("Active");
            g.drawString(f, Component.translatable(p.getString("Lang")), x + 8, ry + 3, TEXT, false);
            Component state = Component.translatable(active ? "settlements.isekai_mahou.ui.works.active" : "settlements.isekai_mahou.ui.works.queued");
            g.drawString(f, state, x + 8 + f.width(Component.translatable(p.getString("Lang"))) + 8, ry + 3, active ? GREEN : FAINT, false);
            int bw = w - 90;
            bar(g, x + 8, ry + 15, bw, 6, p.getInt("Pct") / 100F, active ? GOLD : 0xFF6A5A40);
            g.drawString(f, Component.literal(p.getInt("Pct") + "%"), x + 12 + bw, ry + 14, MUTED, false);
            if (p.getBoolean("Cancel")) {
                int idx = i;
                button(g, mx, my, x + w - 50, ry + 6, 42, 14, Component.translatable("settlements.isekai_mahou.ui.cancel"), true, false,
                        () -> act("cancel", Integer.toString(idx)), null);
            }
            ry += 30;
        }
        wrap(g, Component.translatable("settlements.isekai_mahou.ui.works.hint"), x + 8, y + h - 12, w - 16, FAINT);
    }

    private void decrees(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        int ry = cy;
        ry = options(g, mx, my, ry, "tax", t.getInt("Tax"), 3);
        ry = options(g, mx, my, ry + 4, "gates", t.getInt("Gates"), 3);
        ry = options(g, mx, my, ry + 4, "order", t.getInt("Order"), 2);
        if (!t.getBoolean("WallDone") && "own".equals(t.getString("Kind"))) {
            wrap(g, Component.translatable("settlements.isekai_mahou.ui.decree.no_wall"), x + 8, ry + 2, w - 16, FAINT);
        }
    }

    private int options(GuiGraphics g, int mx, int my, int ry, String decree, int value, int count) {
        Font f = font();
        g.drawString(f, SettlementsLang.tr("ui.decree." + decree), x + 8, ry, TEXT, false);
        ry += 11;
        int bw = Math.min(120, (w - 16) / count - 4);
        for (int i = 0; i < count; i++) {
            int v = i;
            button(g, mx, my, x + 8 + i * (bw + 4), ry, bw, 14,
                    SettlementsLang.tr("ui.decree." + decree + "." + i), true, value == i,
                    () -> act(decree, Integer.toString(v)), null);
        }
        ry += 17;
        return wrap(g, SettlementsLang.tr("ui.decree." + decree + "." + value + ".desc"), x + 8, ry, w - 16, MUTED) + 2;
    }

    private void war(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        boolean can = t.getBoolean("CanDeclare");
        int ly = wrap(g, Component.translatable(can ? "settlements.isekai_mahou.ui.war.ready" : "settlements.isekai_mahou.ui.war.need",
                t.getInt("Affinity")), lx, cy, col, can ? GOLD : MUTED) + 4;
        ly = wars(g, t, mx, my, lx, ly, col, true) + 4;
        CompoundTag pop = t.getCompound("Pop"), cap = t.getCompound("Cap");
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.militia", pop.getInt("militia"), cap.getInt("militia")),
                lx, ly, TEXT, false);
        ly += 12;
        button(g, mx, my, lx, ly, col, 14, Component.translatable("settlements.isekai_mahou.ui.train", 25), t.getBoolean("CanTrain"), false,
                () -> act("train", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.train.tip")));
        ly += 18;
        wrap(g, Component.translatable("settlements.isekai_mahou.ui.militia.hint"), lx, ly, col, FAINT);

        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.targets"), rx, cy, TEXT, false);
        ListTag list = t.getList("Targets", Tag.TAG_COMPOUND);
        int top = cy + 12, rowH = 18, area = y + h - top - 2;
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(rx, top, rx + col, top + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int ry = top + i * rowH - scroll;
            if (ry + rowH < top || ry > top + area) continue;
            g.fill(rx, ry, rx + col, ry + rowH - 2, CARD);
            Component name = ClientSettlements.text(e, "Name");
            g.drawString(f, f.substrByWidth(name, col - 80).getString(), rx + 4, ry + 4, e.getBoolean("War") ? RED : TEXT, false);
            g.drawString(f, Component.literal(e.getInt("Dist") + "m"), rx + col - 74, ry + 4, FAINT, false);
            if (!e.getBoolean("War") && my >= top && my < top + area) {
                String id = e.getString("Id");
                button(g, mx, my, rx + col - 44, ry + 1, 42, 14, Component.translatable("settlements.isekai_mahou.ui.declare"), can, false,
                        () -> act("declare", id), List.of(Component.translatable("settlements.isekai_mahou.ui.declare.tip")));
            } else if (e.getBoolean("War")) {
                g.drawString(f, Component.literal("⚔"), rx + col - 14, ry + 4, RED, false);
            }
        }
        g.disableScissor();
    }

    /** Lista de guerras (com paz) — devolve o y seguinte. */
    private int wars(GuiGraphics g, CompoundTag t, int mx, int my, int lx, int ly, int col, boolean actions) {
        Font f = font();
        ListTag wars = t.getList("Wars", Tag.TAG_COMPOUND);
        if (wars.isEmpty()) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.peace"), lx, ly, GREEN, false);
            return ly + 12;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.at_war"), lx, ly, RED, false);
        ly += 12;
        for (int i = 0; i < wars.size() && i < 5; i++) {
            CompoundTag e = wars.getCompound(i);
            g.drawString(f, Component.literal("⚔ ").append(ClientSettlements.text(e, "Name")), lx + 4, ly + 3, RED, false);
            if (actions) {
                String id = e.getString("Id");
                Component label;
                String action;
                boolean enabled = true;
                List<Component> tip;
                if (e.getBoolean("OfferIn")) {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.accept");
                    action = "accept_peace";
                    tip = null;
                } else if (e.getBoolean("OfferOut")) {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.sent");
                    action = "peace";
                    enabled = false;
                    tip = null;
                } else {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.offer");
                    action = "peace";
                    tip = List.of(Component.translatable(e.getBoolean("PlayerLed") ? "settlements.isekai_mahou.ui.peace.tip_lord"
                            : "settlements.isekai_mahou.ui.peace.tip_tribute", 100));
                }
                button(g, mx, my, lx + col - 70, ly, 68, 14, label, enabled, false, () -> act(action, id), tip);
            }
            ly += 16;
        }
        return ly;
    }

    // =====================================================================================
    //  Visitante e candidato
    // =====================================================================================
    private void visitor(GuiGraphics g, CompoundTag t, int mx, int my, int top) {
        Font f = font();
        int lx = x + 8, col = w - 16;
        int ly = top + 4;
        if (t.contains("Faction")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.faction", ClientSettlements.text(t, "Faction"),
                    ClientSettlements.text(t, "Standing")), lx, ly, TEXT, false);
            ly += 12;
        }
        if (t.contains("Affinity")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.affinity", t.getInt("Affinity")), lx, ly, TEXT, false);
            ly += 12;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.citizens", t.getInt("Citizens")), lx, ly, MUTED, false);
        ly += 14;
        ly = wars(g, t, mx, my, lx, ly, col, false) + 6;
        citizenship(g, t, mx, my, lx, ly, Math.min(200, col));
    }

    private void citizenship(GuiGraphics g, CompoundTag t, int mx, int my, int lx, int ly, int bw) {
        boolean citizen = t.getBoolean("Citizen");
        boolean enemy = t.getBoolean("Enemy");
        button(g, mx, my, lx, ly, bw, 14, Component.translatable(citizen ? "settlements.isekai_mahou.ui.leave" : "settlements.isekai_mahou.ui.join"),
                !enemy, false, () -> act(citizen ? "leave" : "join", ""),
                List.of(Component.translatable(citizen ? "settlements.isekai_mahou.ui.leave.tip" : "settlements.isekai_mahou.ui.join.tip")));
    }

    private void candidate(GuiGraphics g, CompoundTag t, int mx, int my, int top) {
        Font f = font();
        int lx = x + 8, col = w - 16;
        int ly = top + 2;
        if (t.contains("Faction")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.faction", ClientSettlements.text(t, "Faction"),
                    ClientSettlements.text(t, "Standing")), lx, ly, TEXT, false);
            ly += 12;
        }
        Component lord = t.getString("Lord").isEmpty() ? Component.translatable("settlements.isekai_mahou.ui.no_lord")
                : Component.translatable("settlements.isekai_mahou.ui.lord", t.getString("Lord"));
        g.drawString(f, lord, lx, ly, MUTED, false);
        ly += 14;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.conquest"), lx, ly, GOLD, false);
        ly += 12;
        boolean exalted = t.getBoolean("Exalted");
        ly = step(g, lx, ly, col, exalted, Component.translatable("settlements.isekai_mahou.ui.step.exalted"));
        boolean active = t.getBoolean("OathActive");
        boolean seal = t.getBoolean("Seal");
        ly = step(g, lx, ly, col, seal, Component.translatable("settlements.isekai_mahou.ui.step.oath"));
        if (!active) {
            if (t.contains("OathOther")) {
                ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.oath.other", ClientSettlements.text(t, "OathOther")),
                        lx + 14, ly, col - 14, FAINT) + 2;
            }
            button(g, mx, my, lx + 14, ly, 160, 14, Component.translatable("settlements.isekai_mahou.ui.oath.start"), exalted, false,
                    () -> act("oath", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.oath.tip")));
            ly += 18;
        } else {
            int bw = Math.min(160, col / 2);
            ly = progress(g, lx + 14, ly, bw, "kills", t.getInt("Kills"), t.getInt("KillsNeed"));
            int ty = ly;
            ly = progress(g, lx + 14, ly, bw, "tribute", t.getInt("Tribute"), t.getInt("TributeNeed"));
            if (t.getInt("Tribute") < t.getInt("TributeNeed")) {
                button(g, mx, my, lx + 20 + bw + 40, ty, 90, 14, Component.translatable("settlements.isekai_mahou.ui.oath.pay"), true, false,
                        () -> act("tribute", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.oath.pay.tip")));
            }
            ly = progress(g, lx + 14, ly, bw, "vigil", t.getInt("Vigil"), t.getInt("VigilNeed"));
            ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.oath.vigil_hint"), lx + 14, ly, col - 14, FAINT) + 2;
        }
        ly = step(g, lx, ly, col, false, Component.translatable("settlements.isekai_mahou.ui.step.challenge"));
        int retry = t.getInt("Retry");
        boolean running = t.getBoolean("Running");
        boolean canFight = seal && t.getBoolean("HasSeal") && retry <= 0 && !running;
        Component label = running ? Component.translatable("settlements.isekai_mahou.ui.challenge.running")
                : retry > 0 ? Component.translatable("settlements.isekai_mahou.ui.challenge.retry", retry)
                : Component.translatable("settlements.isekai_mahou.ui.challenge");
        button(g, mx, my, lx + 14, ly, 160, 14, label, canFight, false, () -> act("challenge", ""),
                List.of(Component.translatable("settlements.isekai_mahou.ui.challenge.tip")));
        citizenship(g, t, mx, my, x + w - 168, y + h - 18, 160);
    }

    private int step(GuiGraphics g, int lx, int ly, int col, boolean done, Component text) {
        g.drawString(font(), Component.literal(done ? "✔ " : "• ").append(text), lx, ly, done ? GREEN : TEXT, false);
        return ly + 12;
    }

    private int progress(GuiGraphics g, int lx, int ly, int bw, String key, int cur, int need) {
        Font f = font();
        g.drawString(f, SettlementsLang.tr("ui.oath." + key, Math.min(cur, need), need), lx, ly,
                cur >= need ? GREEN : MUTED, false);
        bar(g, lx, ly + 10, bw, 4, cur / (float) Math.max(1, need), cur >= need ? GREEN : GOLD);
        return ly + 18;
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================
    private void act(String action, String arg) {
        ClientSettlements.action(city(), action, arg);
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, int bw, int bh, Component label, boolean enabled,
                        boolean selected, Runnable action, @Nullable List<Component> tip) {
        boolean hover = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        int bg = !enabled ? 0x80201810 : selected ? 0xE05A4420 : hover ? 0xE0403020 : 0xC0281E14;
        g.fill(bx, by, bx + bw, by + bh, bg);
        int border = selected ? GOLD : enabled ? (hover ? BORDER : BORDER_DIM) : 0x40C9A55A;
        g.fill(bx, by, bx + bw, by + 1, border);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, border);
        g.fill(bx, by, bx + 1, by + bh, border);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, border);
        Font f = font();
        String s = f.substrByWidth(label, bw - 6).getString();
        g.drawString(f, s, bx + (bw - f.width(s)) / 2, by + (bh - 8) / 2, enabled ? (selected ? GOLD : TEXT) : FAINT, false);
        if (enabled) hits.add(new Hit(bx, by, bw, bh, action));
        if (hover && tip != null) tooltip = tip;
    }

    private static void bar(GuiGraphics g, int bx, int by, int bw, int bh, float frac, int color) {
        g.fill(bx, by, bx + bw, by + bh, 0xA0000000);
        int fw = (int) (bw * Mth.clamp(frac, 0F, 1F));
        g.fill(bx, by, bx + fw, by + bh, color);
        g.fill(bx, by, bx + fw, by + 1, 0x60FFFFFF);
    }

    private int wrap(GuiGraphics g, Component text, int tx, int ty, int tw, int color) {
        Font f = font();
        for (FormattedCharSequence line : f.split(text, Math.max(40, tw))) {
            g.drawString(f, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }

    private void center(GuiGraphics g, Component text, int ty, int color) {
        Font f = font();
        g.drawString(f, text, x + (w - f.width(text)) / 2, ty, color, true);
    }

    private static String toString(FormattedCharSequence seq) {
        StringBuilder b = new StringBuilder();
        seq.accept((i, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return b.toString();
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                h.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double dy) {
        if (maxScroll <= 0) return false;
        scroll = Mth.clamp(scroll - (int) (dy * 16), 0, maxScroll);
        return true;
    }
}
