package com.renanpontes.isekaimahou.feature.class_enchanter.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_enchanter.AntiMagic;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchNetwork;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.EnchanterGlyphs;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRanks;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Aba "Encantador" do menu B (só aparece para o Encantador): o rank do Rito de Inscrição, os bônus de classe atuais, os
 * encantamentos ativos em você, os glifos de origem (aprendidos / em qual rank) e, à direita, o próximo rank: Círculo,
 * nível, tarefas com barra de progresso, a oferenda (tem/precisa) e o botão "Realizar Inscrição" (precisa de uma mesa de
 * encantamento a até 6 blocos; o servidor confere tudo de novo).
 */
public class EnchanterTab implements MenuTab {
    private static final int TEAL = 0xFF5FE3C0, GOLD = 0xFFFFD27A, OK = 0xFF7CE07C, BAD = 0xFFE06A6A, MUTED = 0xFF8A8796,
            TEXT = 0xFFE8E6F0, PANEL = 0x60101820, BAR_BG = 0xFF26303A;

    private int x, y, w, h, splitX;
    private Button button;
    private double scroll;
    private int contentH;
    /** Mesa por perto (conferida a cada 10 tiques, não a cada quadro). */
    private boolean tableNear;
    private int tableCheck;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.splitX = x + Mth.clamp((int) (w * 0.42F), 150, 230);
        this.tableNear = tableNear();
        this.tableCheck = 10;
        int bw = x + w - splitX - 10;
        button = screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.enchanter.perform"),
                b -> EnchNetwork.requestRite()).bounds(splitX + 6, y + h - 24, bw, 20).build());
        updateButton();
    }

    private static boolean enchanter() {
        return ClientMagic.data().path() == MagicPath.ENCHANTER;
    }

    private void updateButton() {
        if (button == null) return;
        MagicData d = ClientMagic.data();
        EnchanterRanks.Rank next = EnchanterRanks.get(EnchanterRanks.current(d) + 1);
        button.visible = enchanter() && next != null;
        button.active = next != null && ready(d, next) && tableNear;
    }

    @Override
    public void tick() {
        if (tableCheck-- <= 0) {
            tableCheck = 10;
            tableNear = tableNear();
        }
        updateButton();
    }

    private static boolean ready(MagicData d, EnchanterRanks.Rank r) {
        if (d.circle() < r.circle() || d.level() < r.level()) return false;
        for (EnchanterRanks.Req q : r.reqs()) if (q.have(d) < q.amount()) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        if (p.getAbilities().instabuild) return true;
        for (EnchanterRanks.Offer o : r.offering()) if (o.have(p) < o.count()) return false;
        return true;
    }

    /** Mesa de encantamento a até 6 blocos (mesma regra do servidor). */
    private static boolean tableNear() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        var base = p.blockPosition();
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-6, -2, -6), base.offset(6, 2, 6))) {
            if (p.level().getBlockState(pos).is(Blocks.ENCHANTING_TABLE) && pos.distToCenterSqr(p.position()) <= 36.0) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        g.fill(x, y, splitX - 2, y + h, PANEL);
        g.fill(splitX + 2, y, x + w, y + h, PANEL);
        if (!enchanter()) {
            int ly = y + 8;
            g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.title").withColor(TEAL), x + 6, ly, TEAL);
            for (var line : font.split(Component.translatable("screen.isekai_mahou.enchanter.not_class"), w - 12)) {
                ly += 11;
                g.drawString(font, line, x + 6, ly, MUTED);
            }
            return;
        }
        renderLeft(g, font, d);
        renderRight(g, font, d, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ esquerda: rank, bônus, ativos, glifos
    private void renderLeft(GuiGraphics g, Font font, MagicData d) {
        int rank = EnchanterRanks.current(d);
        int lx = x + 6, ly = y + 6, lw = splitX - x - 12;
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.title"), lx, ly, TEAL);
        ly += 12;
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.rank_line", rank == 0 ? "0" : AntiMagic.roman(rank),
                EnchanterRanks.name(rank)), lx, ly, GOLD);
        ly += 11;
        // 5 losangos de rank
        for (int i = 1; i <= Enchants.MAX_RANK; i++) {
            int cx = lx + 4 + (i - 1) * 14, cy = ly + 4;
            diamond(g, cx, cy, 4, i <= rank ? TEAL : 0xFF3A4450);
        }
        ly += 14;
        // bônus de classe atuais (as mesmas contas do servidor, com o rank sincronizado)
        int cap = 4 + (rank >= 3 ? 1 : 0);
        int capacity = 4 + rank / 2;
        int sig = Math.min(5, 1 + (rank * 4 + 4) / 5);
        int dur = Math.round((0.75F + 0.05F * rank) * 100);
        for (Component c : List.of(
                Component.translatable("screen.isekai_mahou.enchanter.perk.duration", dur),
                Component.translatable("screen.isekai_mahou.enchanter.perk.stack", cap),
                Component.translatable("screen.isekai_mahou.enchanter.perk.capacity", capacity),
                Component.translatable("screen.isekai_mahou.enchanter.perk.signature", AntiMagic.roman(sig)),
                Component.translatable("screen.isekai_mahou.enchanter.perk.skin", 10 + 2 * rank))) {
            g.drawString(font, Component.literal("• ").append(c), lx, ly, TEXT);
            ly += 10;
        }
        ly += 4;
        // encantamentos ativos em você
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.active"), lx, ly, TEAL);
        ly += 11;
        LocalPlayer p = Minecraft.getInstance().player;
        int shown = 0;
        if (p != null) {
            for (Holder<MobEffect> eff : EnchRegistries.enchantments()) {
                MobEffectInstance i = p.getEffect(eff);
                if (i == null) continue;
                String secs = i.isInfiniteDuration() ? "∞" : String.valueOf(Math.max(0, i.getDuration() / 20));
                g.drawString(font, Component.literal("◆ ").withColor(eff.value().getColor() | 0xFF000000)
                        .append(Component.translatable("screen.isekai_mahou.enchanter.active_entry", eff.value().getDisplayName(),
                                AntiMagic.roman(i.getAmplifier() + 1), secs).withColor(TEXT)), lx, ly, TEXT);
                ly += 10;
                if (++shown >= 6) break;
            }
        }
        if (shown == 0) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.active_none"), lx, ly, MUTED);
            ly += 10;
        }
        ly += 4;
        // glifos de origem
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.glyphs"), lx, ly, TEAL);
        ly += 11;
        int col = 0;
        int colW = lw / 2;
        for (String id : EnchanterGlyphs.ALL) {
            if (ly > y + h - 10) break;
            Glyph gl = GlyphRegistry.get(id);
            if (gl == null) continue;
            boolean known = d.knowsGlyph(IsekaiMahou.id(id));
            Component name = gl.displayName();
            String txt = font.plainSubstrByWidth(name.getString(), colW - 12);
            g.drawString(font, (known ? "✔ " : "· ") + txt, lx + col * colW, ly, known ? OK : MUTED);
            col++;
            if (col == 2) {
                col = 0;
                ly += 10;
            }
        }
    }

    // ------------------------------------------------------------------ direita: próximo rank
    private void renderRight(GuiGraphics g, Font font, MagicData d, int mouseX, int mouseY) {
        int rx = splitX + 8, ry = y + 6 - (int) scroll, rw = x + w - splitX - 14;
        int top = y + 2, bottom = y + h - 28;
        g.enableScissor(splitX + 2, top, x + w, bottom);
        int startY = ry;
        int rank = EnchanterRanks.current(d);
        EnchanterRanks.Rank next = EnchanterRanks.get(rank + 1);
        if (next == null) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.max"), rx, ry, GOLD);
            ry += 12;
            for (var line : font.split(Component.translatable("screen.isekai_mahou.enchanter.max_desc"), rw)) {
                g.drawString(font, line, rx, ry, TEXT);
                ry += 10;
            }
            g.disableScissor();
            contentH = ry - startY;
            return;
        }
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.next", AntiMagic.roman(next.rank()), next.title()), rx, ry, GOLD);
        ry += 13;
        ry = check(g, font, rx, ry, d.circle() >= next.circle(),
                Component.translatable("screen.isekai_mahou.enchanter.need_circle", next.circle(), d.circle()));
        ry = check(g, font, rx, ry, d.level() >= next.level(),
                Component.translatable("screen.isekai_mahou.enchanter.need_level", next.level(), d.level()));
        ry += 3;
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.tasks"), rx, ry, TEAL);
        ry += 11;
        for (EnchanterRanks.Req q : next.reqs()) {
            int have = Math.min(q.have(d), q.amount());
            boolean ok = have >= q.amount();
            g.drawString(font, q.label(), rx, ry, ok ? OK : TEXT);
            ry += 10;
            int bw = rw - 50;
            g.fill(rx, ry, rx + bw, ry + 4, BAR_BG);
            g.fill(rx, ry, rx + (int) (bw * (have / (float) q.amount())), ry + 4, ok ? 0xFF4FBF6A : TEAL);
            g.drawString(font, have + "/" + q.amount(), rx + bw + 4, ry - 2, ok ? OK : MUTED);
            ry += 9;
        }
        ry += 3;
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.offering"), rx, ry, TEAL);
        ry += 11;
        LocalPlayer p = Minecraft.getInstance().player;
        int ix = rx;
        for (EnchanterRanks.Offer o : next.offering()) {
            if (ix + 34 > rx + rw) {
                ix = rx;
                ry += 20;
            }
            ItemStack st = new ItemStack(o.item().get(), 1);
            g.renderItem(st, ix, ry);
            int have = p == null ? 0 : o.have(p);
            boolean ok = have >= o.count() || (p != null && p.getAbilities().instabuild);
            g.drawString(font, have + "/" + o.count(), ix + 17, ry + 5, ok ? OK : BAD);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= ry && mouseY < ry + 16 && mouseY < bottom) {
                g.renderTooltip(font, st, mouseX, mouseY);
            }
            ix += 17 + font.width(have + "/" + o.count()) + 8;
        }
        ry += 22;
        g.drawString(font, Component.translatable("screen.isekai_mahou.enchanter.rewards"), rx, ry, TEAL);
        ry += 11;
        List<Component> rewards = new ArrayList<>();
        rewards.add(Component.translatable("screen.isekai_mahou.enchanter.reward_xp", next.xp()));
        for (String gid : next.glyphs()) {
            Glyph gl = GlyphRegistry.get(gid);
            if (gl != null) rewards.add(Component.translatable("screen.isekai_mahou.enchanter.reward_glyph", gl.displayName()));
        }
        if (next.awaken() != null) rewards.add(Component.translatable("screen.isekai_mahou.enchanter.reward_awaken", next.awaken().displayName()));
        for (var gift : next.gifts()) {
            ItemStack st;
            try {
                st = gift.get();
            } catch (RuntimeException ex) {
                continue;
            }
            if (st == null || st.isEmpty()) continue;
            rewards.add(Component.translatable("screen.isekai_mahou.enchanter.reward_item", st.getCount(), st.getHoverName()));
        }
        rewards.add(Component.translatable("screen.isekai_mahou.enchanter.reward_perks"));
        for (Component c : rewards) {
            for (var line : font.split(Component.literal("• ").append(c), rw)) {
                g.drawString(font, line, rx, ry, TEXT);
                ry += 10;
            }
        }
        ry += 4;
        for (var line : font.split(Component.translatable("screen.isekai_mahou.enchanter.how"), rw)) {
            g.drawString(font, line, rx, ry, MUTED);
            ry += 10;
        }
        if (!tableNear) {
            for (var line : font.split(Component.translatable("screen.isekai_mahou.enchanter.no_table"), rw)) {
                g.drawString(font, line, rx, ry, BAD);
                ry += 10;
            }
        }
        g.disableScissor();
        contentH = ry - startY + 6;
    }

    private static int check(GuiGraphics g, Font font, int x, int y, boolean ok, Component text) {
        g.drawString(font, Component.literal(ok ? "✔ " : "✘ ").append(text), x, y, ok ? OK : BAD);
        return y + 10;
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < splitX) return false;
        int view = h - 30;
        double max = Math.max(0, contentH - view);
        scroll = Mth.clamp(scroll - sy * 12, 0, max);
        return true;
    }
}
