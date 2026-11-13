package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.settlements.CityMonuments;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Sub-aba "Bônus" do painel (onda 6, city_monuments), dentro de Geral: UMA LINHA por monumento da cidade — nome, nível,
 * o bônus com nome e o botão Ativo/Adormecido (o degrau limita quantos valem ao mesmo tempo) —, a soma do que a cidade
 * está dando agora e a bênção de quem está olhando. Usa as peças do {@link SettlementPanel}.
 */
final class MonumentPage {
    private static final int ROW = 24;
    private static final String[] ROMAN = {"I", "II", "III"};
    private static final Map<String, ItemStack> ICONS = Map.of(
            "eras_obelisk", new ItemStack(Items.CLOCK),
            "guardian_colossus", new ItemStack(Items.SHIELD),
            "great_lighthouse", new ItemStack(Items.LANTERN),
            "hanging_garden", new ItemStack(Items.FLOWERING_AZALEA),
            "mead_hall", new ItemStack(Items.GOAT_HORN),
            "grand_pagoda", new ItemStack(Items.CHERRY_SAPLING),
            "world_tree", new ItemStack(Items.OAK_SAPLING),
            "grand_cathedral", new ItemStack(Items.AMETHYST_BLOCK),
            "great_forge", new ItemStack(Items.BLAST_FURNACE));

    private final SettlementPanel p;

    MonumentPage(SettlementPanel panel) {
        this.p = panel;
    }

    /** Ícone dos monumentos novos no catálogo e na planta (os antigos estão na tabela do painel). */
    static ItemStack icon(String key) {
        ItemStack st = ICONS.get(key);
        return st != null ? st : new ItemStack(Items.BRICKS);
    }

    private static String roman(int level) {
        return ROMAN[Mth.clamp(level, 1, 3) - 1];
    }

    void render(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = p.font();
        CompoundTag m = t.getCompound("Mon");
        ListTag list = m.getList("List", Tag.TAG_COMPOUND);
        int lx = p.x + 6, width = p.w - 12;
        int limit = m.getInt("Limit"), active = m.getInt("Active");
        boolean planner = t.getBoolean("Planner");

        // ---- cabeçalho: quantos valem agora e o limite do degrau
        g.drawString(f, SettlementsLang.tr("ui.mon.title", active, limit), lx, cy, SettlementPanel.TEXT, false);
        Component lim = SettlementsLang.tr("ui.mon.limit");
        g.drawString(f, lim, lx + width - f.width(lim), cy, SettlementPanel.FAINT, false);
        if (my >= cy && my < cy + 10 && mx >= lx && mx < lx + width) {
            p.tooltip = List.of(SettlementsLang.tr("ui.mon.limit.tip1").withColor(SettlementPanel.TEXT),
                    SettlementsLang.tr("ui.mon.limit.tip2").withColor(SettlementPanel.MUTED),
                    SettlementsLang.tr("ui.mon.limit.tip3").withColor(SettlementPanel.MUTED));
        }
        int top = cy + 13;
        if (list.isEmpty()) {
            p.wrap(g, SettlementsLang.tr("ui.mon.none"), lx + 2, top + 2, width - 4, SettlementPanel.MUTED);
            return;
        }

        // ---- rodapé: soma dos bônus e a bênção de quem olha (altura medida antes para a lista caber)
        ListTag sum = m.getList("Sum", Tag.TAG_STRING);
        MutableComponent total = Component.empty();
        for (int i = 0; i < sum.size(); i++) {
            if (i > 0) total.append(Component.literal(" · "));
            CompoundTag one = new CompoundTag();
            one.putString("T", sum.getString(i));
            total.append(ClientSettlements.text(one, "T"));
        }
        Component sumLine = sum.isEmpty() ? SettlementsLang.tr("ui.mon.sum.none") : SettlementsLang.tr("ui.mon.sum", total);
        Component bless = m.contains("Bless") ? ClientSettlements.text(m, "Bless")
                : SettlementsLang.tr(m.getBoolean("BlessReady") ? "ui.mon.bless.ready" : "ui.mon.bless.used");
        int footH = 10 * (f.split(sumLine, Math.max(40, width - 4)).size() + f.split(bless, Math.max(40, width - 4)).size()) + 6;
        int bottom = p.y + p.h - 2 - footH;

        // ---- lista: ativos primeiro (o servidor já manda nessa ordem)
        int area = Math.max(ROW, bottom - top);
        p.maxScroll = Math.max(0, list.size() * ROW - area);
        p.scroll = Mth.clamp(p.scroll, 0, p.maxScroll);
        g.enableScissor(p.x, top, p.x + p.w, top + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int ry = top + i * ROW - p.scroll;
            if (ry + ROW < top || ry > top + area) continue;
            String key = e.getString("Key");
            int level = e.getInt("Level"), cell = e.getInt("Cell");
            boolean on = e.getBoolean("On"), damaged = e.getBoolean("Damaged"), locked = e.getBoolean("Locked");
            boolean hover = mx >= lx && mx < lx + width && my >= Math.max(ry, top) && my < Math.min(ry + ROW - 2, top + area);
            g.fill(lx, ry, lx + width, ry + ROW - 2, hover ? SettlementPanel.CARD_HOVER : SettlementPanel.CARD);
            g.renderItem(SettlementPanel.iconFor(key), lx + 3, ry + 3);
            Component name = e.getString("Lang").isEmpty() ? Component.literal(key) : Component.translatable(e.getString("Lang"));
            Component title = Component.empty().append(name).append(Component.literal(" " + roman(level)));
            int bw = 72;
            g.drawString(f, f.substrByWidth(title, width - bw - 34).getString(), lx + 23, ry + 2,
                    damaged ? SettlementPanel.RED : on ? SettlementPanel.GOLD : SettlementPanel.MUTED, false);
            Component fx = Component.empty().append(CityMonuments.bonusName(key)).append(Component.literal(": "))
                    .append(CityMonuments.effects(key, level, 1F, false));
            g.drawString(f, f.substrByWidth(fx, width - bw - 34).getString(), lx + 23, ry + 12,
                    on && !damaged ? SettlementPanel.GREEN : SettlementPanel.FAINT, false);
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(title.copy().withColor(SettlementPanel.TEXT));
                tip.add(fx.copy().withColor(SettlementPanel.GREEN));
                if (level < CityMonuments.MAX_LEVEL) {
                    tip.add(SettlementsLang.tr("ui.mon.next", roman(level + 1), CityMonuments.effects(key, level + 1, 1F, false))
                            .withColor(SettlementPanel.MUTED));
                }
                Component carried = CityMonuments.effects(key, level, CityMonuments.BLESSING_SHARE, true);
                if (!carried.getString().isEmpty()) tip.add(SettlementsLang.tr("ui.mon.carried", carried).withColor(SettlementPanel.BLUE));
                if (damaged) tip.add(SettlementsLang.tr("ui.mon.damaged").withColor(SettlementPanel.RED));
                p.tooltip = tip;
            }
            // botão: consagrar / adormecer (só quem planeja a cidade)
            boolean can = planner && (on ? !locked : !damaged && active < limit);
            Component label = SettlementsLang.tr(on ? "ui.mon.on" : "ui.mon.off");
            Component why = !planner ? SettlementsLang.tr("ui.mon.tip.planner")
                    : on ? SettlementsLang.tr(locked ? "ui.mon.tip.locked" : "ui.mon.tip.on")
                    : damaged ? SettlementsLang.tr("ui.mon.damaged")
                    : active >= limit ? SettlementsLang.tr("ui.mon.tip.full", limit) : SettlementsLang.tr("ui.mon.tip.off");
            if (ry >= top - 2 && ry + ROW - 2 <= top + area + 2) {
                p.button(g, mx, my, lx + width - bw - 3, ry + 4, bw, 14, label, can, on, () -> p.act("monument_toggle", Integer.toString(cell)),
                        List.of(why));
            }
        }
        g.disableScissor();

        // ---- rodapé
        int fy = bottom + 4;
        fy = p.wrap(g, sumLine, lx + 2, fy, width - 4, sum.isEmpty() ? SettlementPanel.FAINT : SettlementPanel.GREEN);
        p.wrap(g, bless, lx + 2, fy, width - 4, m.contains("Bless") ? SettlementPanel.GOLD : SettlementPanel.MUTED);
    }
}
