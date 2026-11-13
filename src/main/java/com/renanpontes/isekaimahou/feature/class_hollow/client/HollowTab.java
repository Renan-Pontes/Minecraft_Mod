package com.renanpontes.isekaimahou.feature.class_hollow.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowForge;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowRanks;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSouls;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.CeroFusionAugment;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.DevourTrigger;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Hollow" do menu B (a página da classe). À esquerda: estágio da evolução, Almas, a fome, por que virou Hollow, a
 * Resurrección e os botões (Hierro passivo, Despertar como Arrancar — que, depois de virar Arrancar, dá lugar ao
 * "Moldar liberação" da release 1.0: a receita de Transformação que entra por cima da Resurrección). À direita (rola): os 5 estágios com requisitos e
 * progresso, as técnicas (aprendidas ou em que estágio chegam), os 3 arquétipos da Resurrección e o guia.
 */
public class HollowTab implements MenuTab {
    private static final int RED = 0xFF000000 | Hollow.REIATSU_RGB;
    private static final int BONE = 0xFF000000 | Hollow.RGB;
    private static final int GREEN = 0xFF7BE07A;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    /** Dica do botão sob o mouse neste quadro (null = nenhuma). */
    private List<Component> hoverTip;
    private int x, y, w, h, listW;
    private int scroll, maxScroll;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Math.max(130, Math.min(176, (int) (w * 0.36F)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        hoverTip = null;
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        if (d == null) return;
        renderSide(g, font, d, mx, my);
        renderPanel(g, font, d);
        if (hoverTip != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : hoverTip) lines.addAll(font.split(c, 210));
            g.renderTooltip(font, lines, mx, my);
        }
    }

    // ------------------------------------------------------------------ esquerda
    private void renderSide(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        MahouUi.frame(g, x, y, listW, h, RED);
        boolean hollow = d.path() == MagicPath.HOLLOW;
        int rank = Hollow.rank(d);
        g.drawString(font, MagicPath.HOLLOW.displayName().withStyle(ChatFormatting.BOLD), x + 7, y + 6, BONE, true);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.hollow.rank_line", Hollow.roman(rank), Hollow.rankTitle(rank)),
                x + 7, y + 18, 0.8F, MahouUi.GOLD_PALE, false);
        for (int i = 0; i < Hollow.MAX_RANK; i++) {
            int cx = x + listW - 12 - (Hollow.MAX_RANK - 1 - i) * 10;
            MahouUi.diamond(g, cx, y + 10, 4, i < rank ? RED : 0x50FFFFFF);
            if (i < rank) MahouUi.diamond(g, cx, y + 10, 2, 0xFFFFFFFF);
        }
        int ly = y + 32;
        int bottom = y + h - (hollow ? 44 : 6);
        if (!hollow) {
            wrap(g, font, MagicPath.HOLLOW.lockedHint().withStyle(ChatFormatting.ITALIC), x + 7, ly, listW - 14, 0xFFE0A070, bottom);
            return;
        }
        ly = wrap(g, font, Component.translatable("screen.isekai_mahou.hollow.souls", Hollow.souls(d), Hollow.soulsTotal(d)),
                x + 7, ly, listW - 14, RED, bottom);
        int now = Minecraft.getInstance().level == null ? 0 : (int) (Minecraft.getInstance().level.getGameTime() / 20L);
        int hunger = HollowSouls.hunger(d, now);
        int since = HollowSouls.secondsSinceFeed(d, now) / 60;
        ly = wrap(g, font, Component.translatable("screen.isekai_mahou.hollow.hunger." + hunger, since), x + 7, ly, listW - 14,
                hunger == 0 ? MahouUi.TEXT_DIM : hunger == 1 ? 0xFFE0A040 : 0xFFFF4050, bottom);
        Hollow.Release r = Hollow.release(d);
        ly = wrap(g, font, r == null ? Component.translatable("screen.isekai_mahou.hollow.no_release")
                : Component.translatable("screen.isekai_mahou.hollow.release_line", r.displayName()), x + 7, ly + 2, listW - 14, MahouUi.TEXT, bottom);
        ly = wrap(g, font, Component.translatable("screen.isekai_mahou.hollow.power", Math.round((Hollow.rankPower(d) - 1) * 100)),
                x + 7, ly, listW - 14, MahouUi.TEXT_DIM, bottom);
        int origin = d.counter(Hollow.ORIGIN);
        if (origin > 0) {
            ly = wrap(g, font, Component.translatable("screen.isekai_mahou.hollow.origin." + Math.min(3, origin),
                    Math.max(0, d.counter(Hollow.SHINIGAMI_RANK)), d.counter(Hollow.ORIGIN_DAY)), x + 7, ly + 2, listW - 14, 0xFF9A8E86, bottom);
        }
        // botões
        int by = y + h - 40;
        if (rank >= Hollow.VASTO_LORDE) {
            boolean off = d.hasFlag(Hollow.FLAG_HIERRO_OFF);
            button(g, font, x + 7, by, listW - 14, 15, Component.translatable(off ? "screen.isekai_mahou.hollow.hierro_on"
                    : "screen.isekai_mahou.hollow.hierro_off"), true, 0xFF8A8478, mx, my,
                    () -> PacketDistributor.sendToServer(new HollowNetwork.Choice(HollowNetwork.CHOICE_TOGGLE_HIERRO, 0)));
        }
        if (r != null) {
            // release 1.0: já é Arrancar — o botão passa a moldar a Resurrección (abre a aba Votos no espaço dela)
            int bx = x + 7, bw = listW - 14, sy = by + 18;
            button(g, font, bx, sy, bw, 15, Component.translatable("screen.isekai_mahou.release_forge.shape"), HollowForge.canShape(d),
                    ReleaseShaper.COLOR, mx, my, ReleaseShaper::open);
            if (mx >= bx && mx < bx + bw && my >= sy && my < sy + 15) hoverTip = ReleaseShaper.tip(d);
            return;
        }
        boolean ready = HollowRanks.arrancarReady(d);
        button(g, font, x + 7, by + 18, listW - 14, 15, Component.translatable("screen.isekai_mahou.hollow.become_arrancar"), ready, RED, mx, my,
                () -> PacketDistributor.sendToServer(new HollowNetwork.Choice(HollowNetwork.CHOICE_OPEN_RELEASE, 0)));
    }

    // ------------------------------------------------------------------ direita (rola)
    private void renderPanel(GuiGraphics g, Font font, MagicData d) {
        int px = x + listW + 6, pw = w - listW - 6;
        MahouUi.frame(g, px, y, pw, h, 0xFF5A1018);
        int top = y + 4, bottom = y + h - 4;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int cy = top + 2 - scroll;
        int cw = pw - 12;
        int start = cy;
        cy = ranks(g, font, d, px + 6, cy, cw);
        cy = techniques(g, font, d, px + 6, cy + 6, cw);
        cy = releases(g, font, d, px + 6, cy + 6, cw);
        cy = guide(g, font, px + 6, cy + 6, cw);
        g.disableScissor();
        int contentH = cy - start;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int barH = Math.max(16, (bottom - top) * (bottom - top) / Math.max(1, contentH));
            int barY = top + (int) ((bottom - top - barH) * (scroll / (float) maxScroll));
            g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, 0x90FFFFFF);
        }
    }

    private int ranks(GuiGraphics g, Font font, MagicData d, int cx, int cy, int cw) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.hollow.evolution").withStyle(ChatFormatting.BOLD), cx, cy, MahouUi.GOLD, true);
        cy += 12;
        int cur = Hollow.rank(d);
        for (int n = 0; n <= Hollow.MAX_RANK; n++) {
            boolean done = n <= cur && d.path() == MagicPath.HOLLOW;
            int color = done ? GREEN : n == cur + 1 ? BONE : MahouUi.TEXT_DIM;
            MutableComponent head = Component.literal(Hollow.roman(n) + "  ").append(Hollow.rankTitle(n)).withStyle(ChatFormatting.BOLD);
            g.drawString(font, MahouUi.fit(font, head, cw), cx, cy, color, true);
            cy += 11;
            HollowRanks.Rank r = HollowRanks.rank(n);
            if (r != null && !done) {
                List<Component> missing = new ArrayList<>();
                HollowRanks.meets(d, r, missing);
                MutableComponent req = Component.translatable("screen.isekai_mahou.hollow.req_circle", r.circle());
                for (HollowRanks.Req q : r.reqs()) {
                    req.append(" · ").append(q.label()).append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")");
                }
                cy = wrap(g, font, req, cx + 6, cy, cw - 6, MahouUi.TEXT_DIM, 0);
            }
            cy = wrap(g, font, Component.translatable("hollow.isekai_mahou.rank_tip." + n), cx + 6, cy, cw - 6, 0xFFB8AEA6, 0) + 3;
        }
        return cy;
    }

    /** v1.4: as técnicas inatas (tecla "Instinto Hollow" e Roda Hollow) e os glifos da classe. */
    private int techniques(GuiGraphics g, Font font, MagicData d, int cx, int cy, int cw) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.hollow.techniques").withStyle(ChatFormatting.BOLD), cx, cy, MahouUi.GOLD, true);
        cy += 12;
        cy = wrap(g, font, Component.translatable("screen.isekai_mahou.hollow.tech_keys", HollowInput.TECH.getTranslatedKeyMessage(),
                HollowInput.WHEEL.getTranslatedKeyMessage()), cx, cy, cw, 0xFFE0D0B0, 0) + 3;
        boolean hollow = d.path() == MagicPath.HOLLOW;
        Minecraft mc = Minecraft.getInstance();
        HollowLooks.Look look = mc.player == null ? null : HollowLooks.of(mc.player);
        boolean rel = look != null && look.released();
        for (HollowTech t : HollowTech.values()) {
            boolean ok = hollow && t.unlocked(d, rel);
            g.blit(t.icon(), cx, cy, 12, 12, 0, 0, 32, 32, 32, 32);
            if (!ok) g.fill(cx, cy, cx + 12, cy + 12, 0xA0000000);
            MutableComponent line = t.displayName().copy().withStyle(ChatFormatting.BOLD);
            line.append(Component.literal("  ")).append(ok ? Component.translatable("screen.isekai_mahou.hollow.known").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("screen.isekai_mahou.hollow.at_rank", Hollow.rankTitle(t.rank)).withStyle(ChatFormatting.GRAY));
            g.drawString(font, MahouUi.fit(font, line, cw - 16), cx + 16, cy + 2, ok ? BONE : MahouUi.TEXT_DIM, true);
            cy += 14;
            cy = wrap(g, font, t.howTo(), cx + 6, cy, cw - 6, 0xFFD8B070, 0);
            cy = wrap(g, font, t.description(), cx + 6, cy, cw - 6, MahouUi.TEXT_DIM, 0) + 3;
        }
        // os glifos novos da classe (montáveis no Grimório)
        g.drawString(font, Component.translatable("screen.isekai_mahou.hollow.class_glyphs").withStyle(ChatFormatting.BOLD), cx, cy + 2, MahouUi.GOLD, true);
        cy += 14;
        for (String id : new String[] {DevourTrigger.PATH, CeroFusionAugment.PATH}) {
            Glyph gl = GlyphRegistry.get(IsekaiMahou.id(id));
            if (gl == null) continue;
            boolean known = d.knowsGlyph(gl.id());
            g.drawString(font, MahouUi.fit(font, gl.displayName().copy().withStyle(ChatFormatting.BOLD).append("  ").append(known
                    ? Component.translatable("screen.isekai_mahou.hollow.known").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("screen.isekai_mahou.hollow.at_rank",
                            Hollow.rankTitle(id.equals(DevourTrigger.PATH) ? Hollow.GILLIAN : Hollow.ADJUCHAS)).withStyle(ChatFormatting.GRAY)), cw),
                    cx, cy, known ? BONE : MahouUi.TEXT_DIM, true);
            cy += 11;
            cy = wrap(g, font, gl.description(), cx + 6, cy, cw - 6, MahouUi.TEXT_DIM, 0) + 2;
        }
        return cy;
    }

    private int releases(GuiGraphics g, Font font, MagicData d, int cx, int cy, int cw) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.hollow.releases").withStyle(ChatFormatting.BOLD), cx, cy, MahouUi.GOLD, true);
        cy += 12;
        Hollow.Release mine = Hollow.release(d);
        for (Hollow.Release r : Hollow.Release.values()) {
            MutableComponent head = r.displayName().withStyle(ChatFormatting.BOLD);
            if (r == mine) head.append(Component.translatable("screen.isekai_mahou.hollow.yours").withStyle(ChatFormatting.GREEN));
            g.drawString(font, MahouUi.fit(font, head, cw), cx, cy, 0xFF000000 | r.rgb, true);
            cy += 11;
            cy = wrap(g, font, Component.literal("“").append(r.command()).append("”"), cx + 6, cy, cw - 6, 0xFFE0D0B0, 0);
            cy = wrap(g, font, r.description(), cx + 6, cy, cw - 6, MahouUi.TEXT_DIM, 0) + 3;
        }
        return cy;
    }

    private int guide(GuiGraphics g, Font font, int cx, int cy, int cw) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.hollow.guide").withStyle(ChatFormatting.BOLD), cx, cy, MahouUi.GOLD, true);
        cy += 12;
        for (int i = 1; i <= 6; i++) {
            cy = wrap(g, font, Component.literal("• ").append(Component.translatable("screen.isekai_mahou.hollow.guide." + i)), cx, cy, cw,
                    MahouUi.TEXT_DIM, 0) + 2;
        }
        return cy;
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int tx, int ty, int tw, int color, int limit) {
        for (FormattedCharSequence line : font.split(text, tw)) {
            if (limit > 0 && ty > limit - 9) break;
            g.drawString(font, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, boolean enabled, int color, int mx, int my,
                        Runnable action) {
        boolean hover = enabled && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int base = enabled ? color : 0xFF555060;
        MahouUi.roundRect(g, bx, by, bw, bh, MahouUi.alpha(MahouUi.lighten(base, hover ? 0.3F : 0F), 0xD0), MahouUi.alpha(MahouUi.darken(base, 0.5F), 0xD0));
        MahouUi.roundOutline(g, bx, by, bw, bh, enabled ? 0xFFFFFFFF : 0xFF3A3640);
        FormattedCharSequence t = MahouUi.fit(font, label, bw - 6);
        g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + 4, enabled ? 0xFFFFFFFF : 0xFF9A96A6, true);
        if (enabled) hits.add(new Hit(bx, by, bw, bh, action));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : hits) {
            if (hit.in(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < x + listW || maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        return true;
    }
}
