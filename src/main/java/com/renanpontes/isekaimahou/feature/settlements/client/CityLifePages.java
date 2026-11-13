package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Páginas "cidade viva" do painel (v1.0, city_life): <b>Felicidade</b> (número, motivos, dica, avisos do conselheiro,
 * pedidos dos moradores e festival), <b>Caravanas</b> (destinos com preços, missões, viagens em % e o armazém) e
 * <b>Defesa</b> (o que protege a cidade num ataque e a previsão dos batedores). Usa as peças do {@link SettlementPanel}.
 */
final class CityLifePages {
    private static final String[] KINDS = {"sell_food", "sell_fuel", "buy_food", "buy_fuel", "goods"};

    private final SettlementPanel p;
    /** Destino e tamanho de carga escolhidos na página de caravanas. */
    private String dest = "";
    private int size = 1;

    CityLifePages(SettlementPanel panel) {
        this.p = panel;
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    static int moodColor(String mood) {
        return switch (mood) {
            case "radiant" -> SettlementPanel.GOLD;
            case "happy" -> SettlementPanel.GREEN;
            case "content" -> 0xFFE07BA8;
            case "unhappy" -> 0xFFE8A440;
            default -> SettlementPanel.RED;
        };
    }

    // =====================================================================================
    //  Felicidade
    // =====================================================================================
    void happy(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = p.font();
        CompoundTag h = t.getCompound("Happy");
        int col = (p.w - 16) / 2;
        int lx = p.x + 6, rx = p.x + 10 + col;
        int bottom = p.y + p.h - 2;
        int v = h.getInt("Value");
        String mood = h.getString("Mood").isEmpty() ? "content" : h.getString("Mood");
        int mc = moodColor(mood);
        int ly = cy;
        g.drawString(f, SettlementsLang.tr("ui.happy.title", v), lx, ly, SettlementPanel.TEXT, false);
        Component moodName = SettlementsLang.tr("happy.mood." + mood);
        g.drawString(f, moodName, lx + col - f.width(moodName), ly, mc, false);
        SettlementPanel.bar(g, lx, ly + 11, col, 6, v / 100F, mc);
        ly += 21;
        int delta = h.getInt("Delta");
        g.drawString(f, SettlementsLang.tr("ui.happy.today", signed(delta)), lx, ly, delta >= 0 ? SettlementPanel.GREEN : SettlementPanel.RED, false);
        ly += 12;
        if (h.getInt("RevoltIn") > 0) ly = p.wrap(g, SettlementsLang.tr("ui.happy.revolt", h.getInt("RevoltIn")), lx, ly, col, SettlementPanel.RED) + 2;
        // os motivos, um por linha
        ListTag fl = h.getList("Factors", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size() && ly < bottom - 44; i++) {
            CompoundTag e = fl.getCompound(i);
            int d = e.getInt("D");
            g.drawString(f, SettlementsLang.tr("happy.f." + e.getString("Key")), lx + 4, ly, SettlementPanel.MUTED, false);
            String s = signed(d);
            g.drawString(f, s, lx + col - f.width(s) - 2, ly, d >= 0 ? SettlementPanel.GREEN : SettlementPanel.RED, false);
            ly += 10;
        }
        if (fl.isEmpty()) {
            g.drawString(f, SettlementsLang.tr("ui.happy.calm"), lx + 4, ly, SettlementPanel.FAINT, false);
            ly += 10;
        }
        ly += 4;
        ly = p.wrap(g, SettlementsLang.tr("ui.happy.hint", ClientSettlements.text(h, "Hint")), lx, ly, col, SettlementPanel.GOLD) + 3;
        p.wrap(g, SettlementsLang.tr("ui.happy.fx." + mood, h.getInt("ProdPct"), h.getInt("SpeedPct")), lx, ly, col, SettlementPanel.FAINT);

        // direita: o que a cidade precisa, pedidos dos moradores, festival
        int ry = cy;
        g.drawString(f, SettlementsLang.tr("ui.tips"), rx, ry, SettlementPanel.GOLD, false);
        ry += 11;
        ListTag tips = t.getList("Tips", Tag.TAG_STRING);
        if (tips.isEmpty()) {
            g.drawString(f, SettlementsLang.tr("ui.tips.none"), rx + 4, ry, SettlementPanel.GREEN, false);
            ry += 10;
        }
        for (int i = 0; i < tips.size(); i++) {
            CompoundTag one = new CompoundTag();
            one.putString("T", tips.getString(i));
            ry = p.wrap(g, Component.literal("• ").append(ClientSettlements.text(one, "T")), rx + 2, ry, col - 4, SettlementPanel.TEXT) + 1;
        }
        ry += 5;
        ListTag goals = t.getList("Goals", Tag.TAG_COMPOUND);
        if (t.contains("Goals")) {
            g.drawString(f, SettlementsLang.tr("ui.goals"), rx, ry, SettlementPanel.GOLD, false);
            Component rw = SettlementsLang.tr("ui.goals.reward", t.getInt("GoalCoins"));
            g.drawString(f, rw, rx + col - f.width(rw), ry, SettlementPanel.FAINT, false);
            ry += 11;
            for (int i = 0; i < goals.size(); i++) {
                CompoundTag e = goals.getCompound(i);
                int cur = e.getInt("Cur"), need = Math.max(1, e.getInt("Need"));
                g.fill(rx, ry, rx + col, ry + 21, SettlementPanel.CARD);
                Component n = Component.literal(cur + "/" + need);
                g.drawString(f, f.substrByWidth(ClientSettlements.text(e, "Text"), col - f.width(n) - 12).getString(), rx + 4, ry + 3,
                        SettlementPanel.TEXT, false);
                g.drawString(f, n, rx + col - f.width(n) - 4, ry + 3, SettlementPanel.MUTED, false);
                SettlementPanel.bar(g, rx + 4, ry + 14, col - 8, 4, cur / (float) need, SettlementPanel.GREEN);
                ry += 24;
            }
            if (goals.isEmpty()) {
                g.drawString(f, SettlementsLang.tr("ui.goals.none"), rx + 4, ry, SettlementPanel.FAINT, false);
                ry += 12;
            }
            ry += 3;
        }
        festival(g, t, mx, my, rx, ry, col);
    }

    /** Estado do festival (dia 1/2, espera) e o botão. */
    private void festival(GuiGraphics g, CompoundTag t, int mx, int my, int rx, int ry, int col) {
        Font f = p.font();
        CompoundTag fe = t.getCompound("Fest");
        int day = fe.getInt("Day"), wait = fe.getInt("Wait");
        Component state = day > 0 ? SettlementsLang.tr("ui.fest.running", day, Math.max(2, fe.getInt("Days")))
                : wait > 0 ? SettlementsLang.tr("ui.fest.wait", wait) : SettlementsLang.tr("ui.fest.ready");
        g.drawString(f, state, rx, ry, day > 0 ? SettlementPanel.GOLD : SettlementPanel.MUTED, false);
        ry += 11;
        if (!t.getBoolean("Planner")) return;
        p.button(g, mx, my, rx, ry, col, 14, SettlementsLang.tr("ui.festival2", 40, t.getCompound("Needs").getInt("Festival")),
                t.getBoolean("FestivalOk"), false, () -> p.act("festival", ""), List.of(SettlementsLang.tr("ui.fest.tip")));
    }

    // =====================================================================================
    //  Caravanas
    // =====================================================================================
    private static String time(int secs) {
        return secs >= 60 ? (secs / 60) + "min" : secs + "s";
    }

    /** Estimativa do que a missão rende (as mesmas contas do servidor, sem sorte nem risco). */
    private static int estimate(String kind, int cargo, CompoundTag d) {
        float bonus = 1F + d.getInt("Bonus") / 100F;
        return switch (kind) {
            case "sell_food" -> Math.round(cargo * d.getInt("Food") / 100F * bonus);
            case "sell_fuel" -> Math.round(cargo * d.getInt("Fuel") / 100F * bonus);
            case "buy_food" -> Math.round(cargo * 100F / Math.max(1, d.getInt("FoodBuy")) * (1F + (bonus - 1F) / 2F));
            case "buy_fuel" -> Math.round(cargo * 100F / Math.max(1, d.getInt("FuelBuy")) * (1F + (bonus - 1F) / 2F));
            default -> Math.round(cargo + cargo * d.getInt("Margin") / 100F * bonus);
        };
    }

    void trade(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = p.font();
        CompoundTag c = t.getCompound("Caravans");
        int col = (p.w - 16) / 2;
        int lx = p.x + 6, rx = p.x + 10 + col;
        int bottom = p.y + p.h - 2;
        ListTag active = c.getList("Active", Tag.TAG_COMPOUND);
        int slots = c.getInt("Slots");
        boolean planner = t.getBoolean("Planner");

        // ---- direita, embaixo: armazém (sempre) — desenhado primeiro para saber quanto sobra
        ListTag stash = t.getList("Stash", Tag.TAG_COMPOUND);
        int stashY = bottom - 34;
        int items = 0;
        for (int i = 0; i < stash.size(); i++) items += stash.getCompound(i).getInt("Count");
        g.drawString(f, SettlementsLang.tr("ui.stash", items), rx, stashY, SettlementPanel.GOLD, false);
        int ix = rx;
        for (int i = 0; i < stash.size() && ix + 18 <= rx + col - 64; i++) {
            CompoundTag e = stash.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(e.getString("Item"));
            if (id == null) continue;
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(id), Math.min(99, e.getInt("Count")));
            g.renderItem(st, ix, stashY + 11);
            g.renderItemDecorations(f, st, ix, stashY + 11);
            if (mx >= ix && mx < ix + 16 && my >= stashY + 11 && my < stashY + 27) {
                p.tooltip = List.of(st.getHoverName(), Component.literal("x" + e.getInt("Count")).withColor(SettlementPanel.MUTED));
            }
            ix += 18;
        }
        if (planner) {
            boolean can = t.getBoolean("InCity") && (items > 0 || t.getBoolean("Owner") && t.getInt("Treasury") > 0);
            p.button(g, mx, my, rx + col - 60, stashY + 12, 60, 14, SettlementsLang.tr("ui.collect"), can, false, () -> p.act("collect", ""),
                    List.of(SettlementsLang.tr("ui.collect.tip")));
        }

        if (!c.getBoolean("Market")) {
            p.wrap(g, SettlementsLang.tr("ui.trade.need_market"), lx, cy + 2, col, SettlementPanel.MUTED);
            return;
        }

        // ---- esquerda: destinos
        g.drawString(f, SettlementsLang.tr("ui.trade.title", active.size(), slots), lx, cy, SettlementPanel.TEXT, false);
        ListTag dests = c.getList("Dest", Tag.TAG_COMPOUND);
        int top = cy + 12, rowH = 26, area = bottom - top;
        p.maxScroll = Math.max(0, dests.size() * rowH - area);
        p.scroll = Mth.clamp(p.scroll, 0, p.maxScroll);
        CompoundTag sel = null;
        g.enableScissor(lx, top, lx + col, top + area);
        for (int i = 0; i < dests.size(); i++) {
            CompoundTag d = dests.getCompound(i);
            boolean chosen = d.getString("Id").equals(dest);
            if (chosen) sel = d;
            int ry = top + i * rowH - p.scroll;
            if (ry + rowH < top || ry > top + area) continue;
            boolean hover = mx >= lx && mx < lx + col && my >= Math.max(ry, top) && my < Math.min(ry + rowH - 2, top + area);
            g.fill(lx, ry, lx + col, ry + rowH - 2, hover ? SettlementPanel.CARD_HOVER : SettlementPanel.CARD);
            if (chosen) {
                g.fill(lx, ry, lx + col, ry + 1, SettlementPanel.GOLD);
                g.fill(lx, ry + rowH - 3, lx + col, ry + rowH - 2, SettlementPanel.GOLD);
            }
            Component info = Component.literal(d.getInt("Dist") + "m · " + time(d.getInt("Secs")));
            g.drawString(f, f.substrByWidth(ClientSettlements.text(d, "Name"), col - f.width(info) - 14).getString(), lx + 4, ry + 3,
                    0xFF000000 | d.getInt("Color"), false);
            g.drawString(f, info, lx + col - f.width(info) - 4, ry + 3, SettlementPanel.FAINT, false);
            Component line = d.getBoolean("War") ? SettlementsLang.tr("ui.trade.war")
                    : SettlementsLang.tr("ui.trade.prices", d.getInt("Food"), d.getInt("Fuel"), d.getInt("Margin"));
            g.drawString(f, f.substrByWidth(line, col - 8).getString(), lx + 4, ry + 13, d.getBoolean("War") ? SettlementPanel.RED : SettlementPanel.MUTED, false);
            if (hover) {
                String id = d.getString("Id");
                p.click(lx, Math.max(ry, top), col, rowH - 2, () -> dest = id);
            }
        }
        g.disableScissor();
        if (dests.isEmpty()) p.wrap(g, SettlementsLang.tr(planner ? "ui.trade.no_dest" : "ui.trade.planner_only"), lx, top + 2, col, SettlementPanel.FAINT);

        // ---- direita: viagens em andamento
        int ry = cy;
        g.drawString(f, SettlementsLang.tr("ui.trade.active"), rx, ry, SettlementPanel.GOLD, false);
        ry += 11;
        if (active.isEmpty()) {
            g.drawString(f, SettlementsLang.tr("ui.trade.none"), rx + 4, ry, SettlementPanel.FAINT, false);
            ry += 11;
        }
        for (int i = 0; i < active.size(); i++) {
            CompoundTag a = active.getCompound(i);
            Component state = SettlementsLang.tr("ui.trade.phase." + Mth.clamp(a.getInt("Phase"), 0, 2), time(a.getInt("Secs")));
            g.drawString(f, f.substrByWidth(Component.literal(a.getString("Name")), col - f.width(state) - 8).getString(), rx + 2, ry, SettlementPanel.TEXT, false);
            g.drawString(f, state, rx + col - f.width(state) - 2, ry, SettlementPanel.MUTED, false);
            SettlementPanel.bar(g, rx + 2, ry + 10, col - 4, 3, a.getInt("Pct") / 100F, SettlementPanel.GOLD);
            if (mx >= rx && mx < rx + col && my >= ry && my < ry + 14) {
                p.tooltip = List.of(SettlementsLang.tr("ui.trade.k." + a.getString("Kind")),
                        SettlementsLang.tr("ui.trade.cargo", a.getInt("Cargo")).withColor(SettlementPanel.MUTED));
            }
            ry += 16;
        }
        ry += 3;

        // ---- direita: mandar uma caravana para o destino escolhido
        if (planner && sel != null && ry + 62 < stashY) {
            CompoundTag d = sel;
            g.drawString(f, SettlementsLang.tr("ui.trade.to", ClientSettlements.text(d, "Name")), rx, ry, SettlementPanel.TEXT, false);
            ry += 10;
            // mercado saturado (já mandou caravana para lá hoje): a cotação mostrada já vem pior
            g.drawString(f, SettlementsLang.tr(d.getInt("Glut") > 0 ? "ui.trade.route_glut" : "ui.trade.route", d.getInt("Bonus"), d.getInt("Risk")),
                    rx, ry, d.getInt("Glut") > 0 ? 0xFFE8A440 : SettlementPanel.FAINT, false);
            ry += 11;
            String[] sizes = {"s", "m", "l"};
            Component lab = SettlementsLang.tr("ui.trade.size");
            g.drawString(f, lab, rx, ry + 3, SettlementPanel.MUTED, false);
            int sx = rx + f.width(lab) + 4, sw = Math.max(20, (rx + col - sx - 4) / 3);
            for (int i = 0; i < 3; i++) {
                int idx = i;
                p.button(g, mx, my, sx + i * (sw + 2), ry, sw, 14, SettlementsLang.tr("ui.trade.size." + sizes[i]), true, size == i,
                        () -> size = idx, null);
            }
            ry += 17;
            int[] units = c.getIntArray("Units"), coins = c.getIntArray("Coins");
            boolean free = active.size() < slots && !d.getBoolean("War");
            int bw = (col - 4) / 3;
            for (int i = 0; i < KINDS.length; i++) {
                String kind = KINDS[i];
                boolean sellFood = kind.equals("sell_food"), sellFuel = kind.equals("sell_fuel");
                int want = sellFood || sellFuel ? (units.length > size ? units[size] : 80) : (coins.length > size ? coins[size] : 60);
                int have = sellFood ? c.getInt("FoodSpare") : sellFuel ? c.getInt("FuelSpare") : t.getInt("Treasury");
                int cargo = Math.min(want, have);
                boolean ok = free && cargo >= 10;
                int est = estimate(kind, cargo, d);
                List<Component> tip = new ArrayList<>();
                tip.add(SettlementsLang.tr("ui.trade.k." + kind).withColor(SettlementPanel.TEXT));
                tip.add(SettlementsLang.tr("ui.trade.tip." + kind, cargo, est).withColor(ok ? SettlementPanel.GREEN : SettlementPanel.MUTED));
                if (!free) tip.add(SettlementsLang.tr(d.getBoolean("War") ? "ui.trade.war" : "ui.trade.full").withColor(SettlementPanel.RED));
                else if (cargo < 10) tip.add(SettlementsLang.tr("ui.trade.short").withColor(SettlementPanel.RED));
                String arg = d.getString("Id") + "|" + kind + "|" + size;
                int bx = rx + (i % 3) * (bw + 2), by = ry + (i / 3) * 16;
                p.button(g, mx, my, bx, by, bw, 14, SettlementsLang.tr("ui.trade.b." + kind), ok, false, () -> p.act("caravan_send", arg), tip);
            }
            ry += 34;
        } else if (planner && sel == null && !dests.isEmpty() && ry + 22 < stashY) {
            ry = p.wrap(g, SettlementsLang.tr("ui.trade.pick"), rx, ry, col, SettlementPanel.MUTED) + 2;
        }
        if (c.contains("Last") && ry + 22 < stashY) {
            p.wrap(g, SettlementsLang.tr("ui.trade.last", c.getString("LastName"), ClientSettlements.text(c, "Last")), rx, ry, col, SettlementPanel.FAINT);
        }
    }

    // =====================================================================================
    //  Defesa
    // =====================================================================================
    void defense(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = p.font();
        CompoundTag d = t.getCompound("Defense");
        CompoundTag n = t.getCompound("Needs");
        int col = (p.w - 16) / 2;
        int lx = p.x + 6, rx = p.x + 10 + col;
        int sec = n.getInt("Sec"), secNeed = Math.max(1, n.getInt("SecNeed"));
        int ly = cy;
        g.drawString(f, SettlementsLang.tr("ui.need.security", sec, secNeed), lx, ly, SettlementPanel.TEXT, false);
        SettlementPanel.bar(g, lx, ly + 10, col, 5, sec / (float) secNeed, sec >= secNeed ? SettlementPanel.GREEN : 0xFFE8A440);
        ly += 20;
        CompoundTag pop = t.getCompound("Pop"), cap = t.getCompound("Cap");
        ly = row(g, lx, ly, col, Items.SPYGLASS, SettlementsLang.tr("ui.def.towers", d.getInt("Towers")), d.getInt("Towers") > 0,
                SettlementsLang.tr("ui.def.towers.tip"), mx, my);
        ly = row(g, lx, ly, col, Items.STONE_BRICK_WALL, SettlementsLang.tr("ui.def.walls", d.getInt("Walls"), d.getInt("WallTowers")),
                d.getInt("Walls") > 0, SettlementsLang.tr("ui.def.walls.tip"), mx, my);
        ly = row(g, lx, ly, col, Items.IRON_BARS, SettlementsLang.tr("ui.def.traps", d.getInt("Traps")), d.getInt("Traps") > 0,
                SettlementsLang.tr("ui.def.traps.tip"), mx, my);
        ly = row(g, lx, ly, col, Items.IRON_SWORD, SettlementsLang.tr(d.getInt("Barracks") > 0 ? "ui.def.barracks.yes" : "ui.def.barracks.no",
                d.getInt("Barracks")), d.getInt("Barracks") > 0, SettlementsLang.tr("ui.def.barracks.tip"), mx, my);
        if (d.getInt("Mage") > 0) {
            ly = row(g, lx, ly, col, Items.ENCHANTING_TABLE, SettlementsLang.tr("ui.def.mage"), true, SettlementsLang.tr("ui.def.mage.tip"), mx, my);
        }
        ly = row(g, lx, ly, col, Items.SHIELD, SettlementsLang.tr("ui.def.guards", pop.getInt("guard"), cap.getInt("guard"),
                pop.getInt("militia"), cap.getInt("militia")), pop.getInt("guard") > 0, SettlementsLang.tr("ui.def.guards.tip"), mx, my);
        if (d.getInt("Range") > 0) {
            p.wrap(g, SettlementsLang.tr("ui.def.fire", d.getInt("Range"), d.getInt("Damage")), lx, ly + 2, col, SettlementPanel.FAINT);
        }

        int ry = cy;
        g.drawString(f, SettlementsLang.tr("ui.def.raids"), rx, ry, SettlementPanel.GOLD, false);
        ry += 12;
        int raid = d.contains("Raid") ? d.getInt("Raid") : -1;
        if (raid > 0) {
            ry = p.wrap(g, SettlementsLang.tr("ui.raid.soon", ClientSettlements.text(t, "RaidSide"), raid), rx, ry, col, SettlementPanel.RED) + 2;
        } else if (raid == 0) {
            ry = p.wrap(g, SettlementsLang.tr("ui.raid.now"), rx, ry, col, SettlementPanel.RED) + 2;
        } else if (!d.getBoolean("Raids")) {
            ry = p.wrap(g, SettlementsLang.tr("ui.def.calm"), rx, ry, col, SettlementPanel.GREEN) + 2;
        } else if (!d.getBoolean("Scouts")) {
            ry = p.wrap(g, SettlementsLang.tr("ui.def.no_scouts"), rx, ry, col, 0xFFE8A440) + 2;
        } else {
            int days = d.getInt("Forecast");
            Component fc = days < 0 ? SettlementsLang.tr("ui.def.forecast.none") : days == 0 ? SettlementsLang.tr("ui.def.forecast.today")
                    : SettlementsLang.tr("ui.def.forecast", days);
            ry = p.wrap(g, fc, rx, ry, col, days == 0 ? SettlementPanel.RED : SettlementPanel.TEXT) + 2;
        }
        g.drawString(f, SettlementsLang.tr("ui.def.record", n.getInt("RaidsWon"), n.getInt("RaidsLost")), rx, ry, SettlementPanel.MUTED, false);
        ry += 14;
        p.wrap(g, SettlementsLang.tr("ui.def.hint"), rx, ry, col, SettlementPanel.FAINT);
    }

    /** Uma linha da lista de defesas: ícone, texto (verde = tem, apagado = falta) e dica ao passar o mouse. */
    private int row(GuiGraphics g, int lx, int ly, int col, net.minecraft.world.item.Item icon, Component text, boolean has, Component tip,
                    int mx, int my) {
        Font f = p.font();
        g.renderItem(new ItemStack(icon), lx, ly);
        g.drawString(f, f.substrByWidth(text, col - 22).getString(), lx + 20, ly + 4, has ? SettlementPanel.TEXT : SettlementPanel.FAINT, false);
        if (mx >= lx && mx < lx + col && my >= ly && my < ly + 17) {
            List<Component> lines = new ArrayList<>();
            lines.add(text.copy().withColor(SettlementPanel.TEXT));
            for (var s : f.split(tip, 220)) lines.add(Component.literal(SettlementPanel.toString(s)).withColor(SettlementPanel.MUTED));
            p.tooltip = lines;
        }
        return ly + 18;
    }
}
