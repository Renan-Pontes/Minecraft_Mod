package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Sub-aba "Comerciantes" do painel (onda 6, city_trades), dentro de Caravanas: UMA LINHA por comerciante de ofício —
 * quem a cidade tem (nome, prédio, nível da loja, ofertas abertas) e quem falta (qual prédio traz) — e a taxa que o
 * Senhor recebe das vendas. Só leitura: negociar é com o NPC, no prédio dele. Usa as peças do {@link SettlementPanel}.
 */
final class CityTradesPage {
    private static final int ROW = 18;
    private static final int AMBER = 0xFFE8A440;
    private static final Map<String, ItemStack> ICONS = Map.of(
            "blacksmith", new ItemStack(Items.ANVIL),
            "healer", new ItemStack(Items.BREWING_STAND),
            "scribe", new ItemStack(Items.WRITABLE_BOOK),
            "innkeeper", new ItemStack(Items.HONEY_BOTTLE),
            "stablehand", new ItemStack(Items.SADDLE),
            "jeweler", new ItemStack(Items.AMETHYST_SHARD),
            "lumberjack", new ItemStack(Items.IRON_AXE),
            "miner", new ItemStack(Items.IRON_PICKAXE),
            "arcanist", new ItemStack(Items.ENCHANTED_BOOK),
            // onda 10: Mecânico (Oficina Mecânica)
            "mechanic", new ItemStack(Items.MINECART));

    private final SettlementPanel p;

    CityTradesPage(SettlementPanel panel) {
        this.p = panel;
    }

    void render(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = p.font();
        ListTag list = t.getList("Trades", Tag.TAG_COMPOUND);
        CompoundTag fee = t.getCompound("TradeFee");
        int lx = p.x + 6, width = p.w - 12;
        int have = 0;
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).contains("Shop")) have++;

        // ---- cabeçalho: quantos a cidade tem e a taxa do Senhor
        g.drawString(f, SettlementsLang.tr("ui.shops.title", have, list.size()), lx, cy, SettlementPanel.TEXT, false);
        Component pct = SettlementsLang.tr("ui.shops.fee", fee.getInt("Pct"));
        g.drawString(f, pct, lx + width - f.width(pct), cy, SettlementPanel.GOLD, false);
        Component today = SettlementsLang.tr("ui.shops.fee.today", fee.getInt("Sales"), fee.getInt("Today"));
        g.drawString(f, today, lx, cy + 11, SettlementPanel.MUTED, false);
        Component total = SettlementsLang.tr("ui.shops.fee.total", fee.getInt("Total"));
        g.drawString(f, total, lx + width - f.width(total), cy + 11, SettlementPanel.MUTED, false);
        if (my >= cy && my < cy + 21 && mx >= lx && mx < lx + width) {
            p.tooltip = List.of(pct, today.copy().withColor(SettlementPanel.MUTED),
                    SettlementsLang.tr("ui.shops.fee.last", fee.getInt("SalesLast"), fee.getInt("Last")).withColor(SettlementPanel.FAINT),
                    total.copy().withColor(SettlementPanel.MUTED));
        }

        // ---- a lista (rola se o painel for baixo); embaixo, uma dica de como usar
        int top = cy + 24;
        int bottom = p.y + p.h - 24;
        int area = Math.max(ROW, bottom - top);
        p.maxScroll = Math.max(0, list.size() * ROW - area);
        p.scroll = Mth.clamp(p.scroll, 0, p.maxScroll);
        g.enableScissor(lx, top, lx + width, top + area);
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * ROW - p.scroll;
            if (ry + ROW < top || ry > top + area) continue;
            row(g, f, list.getCompound(i), t.getInt("Level"), lx, ry, width, mx, my, top, top + area);
        }
        g.disableScissor();
        p.wrap(g, SettlementsLang.tr("ui.shops.hint"), lx, top + area + 3, width, SettlementPanel.FAINT);
    }

    /** Uma linha: ícone, quem é (ou o ofício que falta) e, à direita, a loja dele ou o prédio que o traz. */
    private void row(GuiGraphics g, Font f, CompoundTag e, int cityLevel, int lx, int ry, int width, int mx, int my, int clipTop,
                     int clipBottom) {
        String job = e.getString("Job");
        boolean has = e.contains("Shop");
        boolean here = e.getBoolean("Here");
        boolean hover = mx >= lx && mx < lx + width && my >= Math.max(ry, clipTop) && my < Math.min(ry + ROW - 2, clipBottom);
        g.fill(lx, ry, lx + width, ry + ROW - 2, hover ? SettlementPanel.CARD_HOVER : SettlementPanel.CARD);
        g.renderItem(ICONS.getOrDefault(job, new ItemStack(Items.EMERALD)), lx + 1, ry);
        Component building = ClientSettlements.text(e, "Building");
        Component jobName = SettlementsLang.tr("trade.job." + job);

        Component right;
        int rightColor;
        if (has && here) {
            boolean all = e.getInt("Open") >= e.getInt("Total");
            right = SettlementsLang.tr("ui.shops.here", building, e.getInt("Shop"), e.getInt("Open"), e.getInt("Total"));
            rightColor = e.getBoolean("Damaged") ? AMBER : all ? SettlementPanel.GREEN : SettlementPanel.MUTED;
        } else if (has) {
            right = SettlementsLang.tr("ui.shops.coming", building, e.getInt("Shop"));
            rightColor = SettlementPanel.MUTED;
        } else {
            boolean locked = e.getInt("MinLevel") > cityLevel;
            right = locked ? SettlementsLang.tr("ui.shops.missing.rank", building, ClientSettlements.text(e, "Rank"))
                    : SettlementsLang.tr("ui.shops.missing", building);
            rightColor = locked ? SettlementPanel.FAINT : AMBER;
        }
        g.drawString(f, right, lx + width - f.width(right) - 4, ry + 4, rightColor, false);

        // quem atende: o nome do NPC (já com o ofício) ou só o ofício, apagado, para quem falta
        Component left = has && e.contains("Name") ? ClientSettlements.text(e, "Name") : jobName;
        int leftColor = has ? 0xFF000000 | e.getInt("Color") : SettlementPanel.FAINT;
        int room = width - f.width(right) - 34;
        g.drawString(f, f.substrByWidth(left, Math.max(30, room)).getString(), lx + 22, ry + 4, leftColor, false);

        if (!hover) return;
        List<Component> tip = new ArrayList<>();
        tip.add(jobName.copy().withColor(e.getInt("Color")));
        for (var line : f.split(SettlementsLang.tr("ui.shops.tip.sells." + job), 230)) {
            tip.add(Component.literal(SettlementPanel.toString(line)).withColor(SettlementPanel.TEXT));
        }
        Component more;
        if (!has) {
            more = e.getInt("MinLevel") > cityLevel
                    ? SettlementsLang.tr("ui.shops.tip.build.rank", building, ClientSettlements.text(e, "Rank"))
                    : SettlementsLang.tr("ui.shops.tip.build", building);
        } else if (e.getBoolean("Damaged")) {
            more = SettlementsLang.tr("ui.shops.tip.damaged");
        } else {
            more = SettlementsLang.tr(e.getInt("Open") >= e.getInt("Total") ? "ui.shops.tip.all" : "ui.shops.tip.more");
        }
        for (var line : f.split(more, 230)) tip.add(Component.literal(SettlementPanel.toString(line)).withColor(SettlementPanel.MUTED));
        p.tooltip = tip;
    }
}
