package com.renanpontes.isekaimahou.client.stack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.stack.StackPayloads;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * HUD da Pilha: à esquerda, no meio da tela (ao lado da barra de mana — região STACK do {@link HudLayout}), até 5 conjurações HOSTIS pendentes por
 * perto — runa do elemento (ou ícone de perigo), conjurador + golpe, contagem regressiva, tier (Suprema em vermelho
 * pulsando) e "✦" quando você conhece um glifo de contramágica. Golpes iguais do mesmo conjurador viram uma linha "×N".
 * Config de cliente {@code showStack}. Outro módulo pode mudar a margem esquerda ({@link #leftEdge}) ou desligar
 * ({@link #enabled}) e desenhar a própria versão com {@link ClientStack#entries()}.
 */
public final class StackHud implements LayeredDraw.Layer {
    /** Margem esquerda do painel (padrão: logo à direita da barra de mana e dos ícones dela). */
    public static IntSupplier leftEdge = () -> 30;
    /** false = não desenha (outro HUD assume). */
    public static boolean enabled = true;

    static final int MAX_ROWS = 5;
    private static final int ROW_H = 27;
    private static final int GAP = 3;

    static final int MINOR = 0xFF8FD3FF;
    static final int MAJOR = 0xFFFFB547;
    static final int ULTIMATE = 0xFFFF3B30;
    static final int COUNTERED = 0xFFD9B8FF;

    private record Row(ClientStack.Entry head, int count) {}

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        if (!enabled || !showStack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        double now = ClientStack.ticks() + (mc.isPaused() ? 0 : dt.getGameTimeDeltaPartialTick(false));
        List<Row> rows = rows(ClientStack.entries());
        if (rows.isEmpty()) return;

        Font font = mc.font;
        MagicData d = ClientMagic.data();
        boolean knowsCounter = d.knowsGlyph(SpellStack.COUNTERSPELL);
        boolean knowsLeak = d.knowsGlyph(SpellStack.MANA_LEAK);

        int shown = Math.min(MAX_ROWS, rows.size());
        int hidden = rows.size() - shown;
        boolean hint = false;
        for (int i = 0; i < shown; i++) {
            ClientStack.Entry e = rows.get(i).head;
            if (!e.leaving() && canCounter(e, knowsCounter, knowsLeak)) hint = true;
        }
        int headerH = 11;
        int footerH = (hint ? 10 : 0) + (hidden > 0 ? 9 : 0);
        int totalH = headerH + shown * (ROW_H + GAP) - GAP + footerH;
        // Região fixa do meio-esquerdo (HudLayout): à direita da barra de mana, sem invadir o meio da tela
        // (cântico/barra de conjuração) nem o cartão do canto superior esquerdo
        HudLayout.Rect area = HudLayout.stack(totalH);
        int x = area.x();
        int w = area.w();
        int top = area.y();
        HudLayout.drawn(HudLayout.Region.STACK, area);

        header(g, font, x, top, w, rows);
        int y = top + headerH;
        for (int i = 0; i < shown; i++) {
            Row r = rows.get(i);
            row(g, font, r.head, r.count, x, y, w, now, canCounter(r.head, knowsCounter, knowsLeak));
            y += ROW_H + GAP;
        }
        y -= GAP;
        if (hidden > 0) {
            MahouUi.smallText(g, font, Component.translatable("stack.isekai_mahou.more", hidden), x + 2, y + 2, 0.65F, MahouUi.TEXT_DIM, true);
            y += 9;
        }
        if (hint) {
            int c = MahouUi.mix(MahouUi.GOLD, MahouUi.GOLD_PALE, pulse(1200));
            MahouUi.smallText(g, font, Component.translatable("stack.isekai_mahou.can_counter"), x + 2, y + 2, 0.65F, c, true);
        }
    }

    // ------------------------------------------------------------------ linhas

    /** Agrupa golpes iguais (mesmo conjurador, nome e tier) que ainda estão na Pilha; os que saem animam sozinhos. */
    private static List<Row> rows(List<ClientStack.Entry> all) {
        List<ClientStack.Entry> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(e -> e.endTick));
        Map<String, int[]> index = new LinkedHashMap<>();
        List<ClientStack.Entry> heads = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (ClientStack.Entry e : sorted) {
            if (!e.leaving()) {
                String key = e.casterId + "|" + e.castName.getString() + "|" + e.tier.ordinal() + "|" + e.counterable;
                int[] at = index.get(key);
                if (at != null) {
                    counts.set(at[0], counts.get(at[0]) + 1);
                    continue;
                }
                index.put(key, new int[] {heads.size()});
            }
            heads.add(e);
            counts.add(1);
        }
        List<Row> out = new ArrayList<>(heads.size());
        for (int i = 0; i < heads.size(); i++) out.add(new Row(heads.get(i), counts.get(i)));
        return out;
    }

    private static void header(GuiGraphics g, Font font, int x, int y, int w, List<Row> rows) {
        boolean ultimate = false;
        for (Row r : rows) if (!r.head.leaving() && r.head.tier == SpellStack.Tier.ULTIMATE) ultimate = true;
        int accent = ultimate ? MahouUi.mix(ULTIMATE, 0xFFFFC2B8, pulse(700)) : MahouUi.GOLD_DIM;
        // Três cartas empilhadas (a "pilha")
        for (int i = 2; i >= 0; i--) {
            int cx = x + 1 + i * 2, cy = y + 1 + (2 - i);
            g.fill(cx, cy, cx + 6, cy + 7, i == 0 ? accent : MahouUi.darken(accent, 0.25F * i + 0.2F));
            g.fill(cx + 1, cy + 1, cx + 5, cy + 6, i == 0 ? 0xFF241A36 : 0xFF140F1E);
        }
        MahouUi.smallText(g, font, Component.translatable("stack.isekai_mahou.title").withStyle(ChatFormatting.BOLD),
                x + 13, y + 2, 0.75F, ultimate ? accent : MahouUi.GOLD, true);
        g.fill(x + 13, y + 9, x + w, y + 10, MahouUi.alpha(MahouUi.GOLD_DIM, 0x90));
    }

    private static void row(GuiGraphics g, Font font, ClientStack.Entry e, int count, int x0, int y, int w, double now, boolean counterHint) {
        float appear = ease((float) ((now - e.addedTick) / 5.0));
        float leave = 0F;
        if (e.leaving()) {
            int dur = e.countered ? ClientStack.LEAVE_COUNTERED : ClientStack.LEAVE_NORMAL;
            leave = Mth.clamp((float) ((now - e.leaveTick) / dur), 0F, 1F);
        }
        // Contrariada: fica visível e some só no fim; o resto desliza para a esquerda e some
        float fade = e.countered ? Math.max(0F, (leave - 0.6F) / 0.4F) : leave;
        float a = appear * (1F - fade);
        if (a <= 0.02F) return;
        int x = x0 + Math.round((1F - appear) * -18F + (e.leaving() && !e.countered ? leave * -14F : 0F));
        int tc = tierColor(e.tier, now);
        int ai = Math.round(a * 255);

        MahouUi.roundRect(g, x, y, w, ROW_H, MahouUi.alpha(MahouUi.INK_TOP, Math.round(a * 0xE6)),
                MahouUi.alpha(MahouUi.INK_BOTTOM, Math.round(a * 0xE6)));
        int edge = e.tier == SpellStack.Tier.ULTIMATE ? MahouUi.alpha(tc, Math.round(a * (0x90 + 0x6F * pulse(600))))
                : MahouUi.alpha(tc, Math.round(a * 0x70));
        MahouUi.roundOutline(g, x, y, w, ROW_H, edge);
        g.fill(x + 1, y + 2, x + 3, y + ROW_H - 2, MahouUi.alpha(tc, ai));

        // Ícone: runa do elemento, ou o ícone de perigo do tier
        int ix = x + 5, iy = y + 5;
        g.fill(ix - 1, iy - 1, ix + 17, iy + 17, MahouUi.alpha(0xFF07050C, Math.round(a * 0xC0)));
        if (e.countered) {
            icon(g, "countered", ix, iy, 16, a, () -> brokenDiamond(g, ix + 8, iy + 8, MahouUi.alpha(COUNTERED, ai)));
        } else if (e.element != null) {
            withAlpha(a, () -> GlyphArt.element(g, e.element, ix, iy, 16));
            String tierTex = e.tier.name().toLowerCase(Locale.ROOT);
            icon(g, tierTex, ix + 10, iy + 10, 8, a, () -> MahouUi.diamond(g, ix + 14, iy + 14, 3, MahouUi.alpha(tc, ai)));
        } else {
            String tierTex = e.tier.name().toLowerCase(Locale.ROOT);
            icon(g, tierTex, ix, iy, 16, a, () -> {
                MahouUi.diamond(g, ix + 8, iy + 8, 7, MahouUi.alpha(tc, ai));
                MahouUi.diamond(g, ix + 8, iy + 8, 5, MahouUi.alpha(0xFF1A0E10, ai));
                g.fill(ix + 7, iy + 4, ix + 9, iy + 10, MahouUi.alpha(0xFFFFFFFF, ai));
                g.fill(ix + 7, iy + 11, ix + 9, iy + 13, MahouUi.alpha(0xFFFFFFFF, ai));
            });
        }

        // Texto
        int tx = x + 26;
        int tw = w - 26 - 5;
        Component tierLabel = e.tier.displayName().copy().withStyle(ChatFormatting.BOLD);
        float tierScale = 0.55F;
        int tierW = Math.round(font.width(tierLabel) * tierScale);
        int nameColor = e.boss ? 0xFFFFD0A0 : MahouUi.TEXT;
        line(g, font, e.casterName, tx, y + 4, tw - tierW - 12, 0.8F, MahouUi.alpha(nameColor, ai));
        MahouUi.smallText(g, font, tierLabel, x + w - 5 - tierW - 8, y + 5, tierScale, MahouUi.alpha(tc, ai), true);

        Component spell = e.spellName();
        if (count > 1) spell = spell.copy().append(Component.literal(" ×" + count).withStyle(ChatFormatting.GOLD));
        line(g, font, spell, tx, y + 12, tw - 4, 0.72F, MahouUi.alpha(MahouUi.TEXT_DIM, ai));

        // Contagem regressiva
        int barW = tw - 20;
        float frac = e.countered ? 0F : e.remainingFraction(now);
        MahouUi.bar(g, tx, y + 21, barW, 3, frac, MahouUi.alpha(tc, ai));
        if (!e.leaving()) {
            String secs = String.format(Locale.ROOT, "%.1fs", e.ticksLeft(now) / 20.0);
            MahouUi.smallText(g, font, Component.literal(secs), tx + barW + 3, y + 20, 0.6F, MahouUi.alpha(MahouUi.TEXT, ai), true);
        }

        // Canto: ✦ (você pode contrariar) ou ⊘ (não pode ser contrariada)
        if (!e.leaving()) {
            if (!e.counterable) {
                MahouUi.smallText(g, font, Component.literal("⊘"), x + w - 9, y + 3, 0.7F, MahouUi.alpha(0xFFB05A5A, ai), true);
            } else if (counterHint) {
                int c = MahouUi.mix(MahouUi.GOLD, 0xFFFFFFFF, pulse(900));
                MahouUi.smallText(g, font, Component.literal("✦"), x + w - 9, y + 3, 0.75F, MahouUi.alpha(c, ai), true);
            }
        }

        if (e.countered) counteredOverlay(g, font, e, x, y, w, now, a);
        else if (e.leaving() && e.leaveReason == StackPayloads.REASON_RESOLVED) {
            // Resolveu: lampejo branco rápido
            g.fill(x + 1, y + 1, x + w - 1, y + ROW_H - 1, MahouUi.alpha(0xFFFFFFFF, Math.round((1F - leave) * 0x50)));
        }
    }

    /** "CONTRARIADA": risco nos nomes, rachaduras e o carimbo no meio da linha. */
    private static void counteredOverlay(GuiGraphics g, Font font, ClientStack.Entry e, int x, int y, int w, double now, float a) {
        float age = (float) (now - e.leaveTick);
        float flash = Math.max(0F, 1F - age / 6F);
        g.fill(x + 1, y + 1, x + w - 1, y + ROW_H - 1, MahouUi.alpha(0xFFB98CFF, Math.round(a * (0x28 + 0x60 * flash))));
        int ai = Math.round(a * 255);
        // Risco atravessando os nomes
        int strike = Math.round(Math.min(1F, age / 4F) * (w - 30));
        g.fill(x + 26, y + 8, x + 26 + strike, y + 9, MahouUi.alpha(COUNTERED, ai));
        g.fill(x + 26, y + 15, x + 26 + strike, y + 16, MahouUi.alpha(COUNTERED, Math.round(ai * 0.7F)));
        // Rachaduras (fixas por id)
        long seed = e.id * 2654435761L;
        int cx = x + w / 2, cy = y + ROW_H / 2;
        for (int k = 0; k < 4; k++) {
            int px = cx, py = cy;
            double ang = ((seed >>> (k * 7)) & 127) / 127.0 * Math.PI * 2;
            for (int s = 0; s < 5; s++) {
                ang += (((seed >>> (k * 5 + s * 3)) & 7) - 3.5) * 0.18;
                int nx = px + (int) Math.round(Math.cos(ang) * 4), ny = py + (int) Math.round(Math.sin(ang) * 2.2);
                segment(g, px, py, nx, ny, MahouUi.alpha(0xFFFFFFFF, Math.round(ai * 0.55F)));
                px = nx;
                py = ny;
            }
        }
        Component stamp = Component.translatable("stack.isekai_mahou.countered_row").withStyle(ChatFormatting.BOLD);
        float scale = 0.85F + 0.25F * flash;
        int sw = Math.round(font.width(stamp) * scale);
        MahouUi.smallText(g, font, stamp, cx - sw / 2F, cy - 4 * scale, scale, MahouUi.alpha(0xFFFFFFFF, ai), true);
    }

    // ------------------------------------------------------------------ ajudantes

    private static boolean canCounter(ClientStack.Entry e, boolean counterspell, boolean manaLeak) {
        if (!e.counterable) return false;
        if (counterspell) return true;
        return manaLeak && e.tier != SpellStack.Tier.ULTIMATE;
    }

    static int tierColor(SpellStack.Tier tier, double now) {
        return switch (tier) {
            case MINOR -> MINOR;
            case MAJOR -> MAJOR;
            case ULTIMATE -> MahouUi.mix(ULTIMATE, 0xFFFF9A8A, pulse(600));
        };
    }

    private static void line(GuiGraphics g, Font font, Component text, float x, float y, int width, float scale, int color) {
        FormattedCharSequence seq = MahouUi.fit(font, text, Math.max(8, Math.round(width / scale)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, seq, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Ícone 16×16 da arte ({@code textures/gui/stack/<nome>.png}); sem a textura, o desenho de reserva. */
    private static void icon(GuiGraphics g, String name, int x, int y, int size, float alpha, Runnable fallback) {
        ResourceLocation tex = IsekaiMahou.id("textures/gui/stack/" + name + ".png");
        if (GlyphArt.exists(tex)) withAlpha(alpha, () -> GlyphArt.icon(g, tex, x, y, size));
        else fallback.run();
    }

    private static void withAlpha(float alpha, Runnable draw) {
        if (alpha >= 0.99F) {
            draw.run();
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        try {
            draw.run();
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    private static void brokenDiamond(GuiGraphics g, int cx, int cy, int color) {
        MahouUi.diamond(g, cx - 1, cy - 1, 4, color);
        MahouUi.diamond(g, cx + 3, cy + 3, 2, color);
        MahouUi.diamond(g, cx + 4, cy - 4, 1, color);
        MahouUi.diamond(g, cx - 4, cy + 4, 1, color);
    }

    private static void segment(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int px = x0 + (steps == 0 ? 0 : Math.round((x1 - x0) * i / (float) steps));
            int py = y0 + (steps == 0 ? 0 : Math.round((y1 - y0) * i / (float) steps));
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** 0..1..0 com período {@code ms} (relógio real: pulsa também com o jogo pausado). */
    static float pulse(int ms) {
        return 0.5F + 0.5F * Mth.sin((Util.getMillis() % ms) / (float) ms * Mth.TWO_PI);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }

    static boolean showStack() {
        try {
            return MahouConfig.SHOW_STACK.get();
        } catch (IllegalStateException ex) {
            return true;   // config ainda não carregada
        }
    }
}
