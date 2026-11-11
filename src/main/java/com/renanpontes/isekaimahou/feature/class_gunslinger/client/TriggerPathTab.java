package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsTechniques;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Caminho do Gatilho" do menu B. Esquerda: o Arsenal de Mana (as 5 armas, liberadas pelo rank, com o botão
 * "Materializar") e os bônus da classe. Direita: os 5 ranks com requisitos (barras), recompensas (glifos, arma,
 * lenda, XP) e os botões "Concluir" e "Desafiar ao Meio-Dia". Rola com a roda do mouse.
 */
public class TriggerPathTab implements MenuTab {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    private static final int GREEN = 0xFF7BE07A;
    private static final int ACCENT = 0xFFD9A441;
    private static final int LIST_W = 118;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Tip(int x, int y, int w, int h, List<Component> lines) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, maxScroll;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        tips.clear();
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        renderArsenal(g, font, d, mx, my);
        renderPath(g, font, d, mx, my);
        for (Tip t : tips) {
            if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + t.h()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component c : t.lines()) lines.addAll(font.split(c, 220));
                g.renderTooltip(font, lines, mx, my);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ Arsenal
    private void renderArsenal(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        MahouUi.frame(g, x, y, LIST_W, h, ACCENT);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.gunslinger.arsenal").withStyle(ChatFormatting.BOLD),
                LIST_W - 12), x + 6, y + 6, MahouUi.GOLD, true);
        boolean own = d.path() == MagicPath.GUNSLINGER;
        int ry = y + 19;
        for (GunKind k : GunKind.values()) {
            boolean unlocked = TriggerPath.unlocked(d, k);
            MahouUi.roundRect(g, x + 4, ry, LIST_W - 8, 26, unlocked ? 0x40D9A441 : 0x30000000, 0x60000000);
            ItemStack icon = new ItemStack(GsRegistries.GUNS.get(k).get());
            g.renderItem(icon, x + 7, ry + 5);
            g.drawString(font, MahouUi.fit(font, k.displayName(), LIST_W - 34), x + 26, ry + 3, unlocked ? MahouUi.TEXT : MahouUi.TEXT_DIM, true);
            List<Component> tip = new ArrayList<>();
            tip.add(k.displayName().withStyle(ChatFormatting.GOLD));
            tip.add(k.description().withStyle(ChatFormatting.GRAY));
            if (!unlocked) {
                MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.gunslinger.unlock_at", ROMAN[k.unlockRank - 1]),
                        x + 26, ry + 15, 0.7F, MahouUi.TEXT_DIM, false);
            } else if (own) {
                final GunKind pick = k;
                button(g, font, x + 26, ry + 13, LIST_W - 34, 11, Component.translatable("screen.isekai_mahou.gunslinger.materialize"),
                        true, ACCENT, mx, my, y, y + h, () -> PacketDistributor.sendToServer(new GsNetwork.Materialize(pick.ordinal())),
                        List.of(Component.translatable("screen.isekai_mahou.gunslinger.materialize.tip").withStyle(ChatFormatting.GRAY)));
            }
            tips.add(new Tip(x + 4, ry, 20, 26, tip));
            ry += 28;
        }
        // Bônus de classe
        int by = ry + 4;
        if (by + 20 < y + h) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.gunslinger.bonus").withStyle(ChatFormatting.BOLD), x + 6, by,
                    MahouUi.GOLD, true);
            by += 11;
            // texto em 75%: quebra as linhas na largura "ampliada" para caber na coluna
            for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.gunslinger.bonus.desc"),
                    (int) ((LIST_W - 12) / 0.75F))) {
                if (by + 9 > y + h - 4) break;
                g.pose().pushPose();
                g.pose().translate(x + 6, by, 0);
                g.pose().scale(0.75F, 0.75F, 1F);
                g.drawString(font, line, 0, 0, MahouUi.TEXT_DIM, false);
                g.pose().popPose();
                by += 8;
            }
        }
    }

    // ------------------------------------------------------------------ Caminho
    private void renderPath(GuiGraphics g, Font font, MagicData d, int mx, int my) {
        int px = x + LIST_W + 6, pw = w - LIST_W - 6;
        MahouUi.frame(g, px, y, pw, h, ACCENT);
        int rank = TriggerPath.rankOf(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.gunslinger.title").withStyle(ChatFormatting.BOLD), px + 8, y + 6,
                ACCENT, true);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.gunslinger.subtitle"), px + 8, y + 17, 0.8F,
                MahouUi.TEXT_DIM, false);
        for (int i = 0; i < TriggerPath.MAX_RANK; i++) {
            int cx = px + pw - 14 - (TriggerPath.MAX_RANK - 1 - i) * 11;
            MahouUi.diamond(g, cx, y + 11, 4, i < rank ? MahouUi.GOLD : 0x60FFFFFF);
            if (i < rank) MahouUi.diamond(g, cx, y + 11, 2, MahouUi.GOLD_PALE);
        }
        boolean own = d.path() == MagicPath.GUNSLINGER;
        if (!own) {
            Component note = Component.translatable("screen.isekai_mahou.gunslinger.other_class");
            MahouUi.smallText(g, font, note, px + pw - 8 - font.width(note) * 0.75F, y + 19, 0.75F, MahouUi.TEXT_DIM, false);
        }
        int top = y + 30, bottom = y + h - 4;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int cy = top - scroll;
        for (TriggerPath.Rank r : TriggerPath.ranks()) {
            cy += card(g, font, d, r, px + 6, cy, pw - 12, own, rank, mx, my, top, bottom) + 4;
        }
        g.disableScissor();
        int contentH = cy + scroll - top;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int barH = Math.max(12, (bottom - top) * (bottom - top) / Math.max(1, contentH));
            int barY = top + (int) ((bottom - top - barH) * (scroll / (float) maxScroll));
            g.fill(px + pw - 4, top, px + pw - 2, bottom, 0x40000000);
            g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, MahouUi.GOLD_DIM);
        }
    }

    private int card(GuiGraphics g, Font font, MagicData d, TriggerPath.Rank r, int cx, int cy, int cw, boolean own, int done, int mx,
                     int my, int clipTop, int clipBottom) {
        int n = r.number();
        boolean complete = n <= done;
        boolean next = n == done + 1;
        boolean showButtons = own && next;
        int ch = 16 + (r.reqs().size() + 1) * 11 + 13 + (showButtons ? 18 : 0) + 4;
        int accent = complete ? MahouUi.GOLD : next ? ACCENT : 0xFF6A6478;
        MahouUi.roundRect(g, cx, cy, cw, ch, complete ? 0x50403010 : next ? 0x30D9A441 : 0x40000000, 0x70000000);
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(accent, 0xB0));
        MutableComponent title = Component.literal(ROMAN[n - 1] + " · ").append(TriggerPath.title(n));
        g.drawString(font, MahouUi.fit(font, title.withStyle(ChatFormatting.BOLD), cw - 80), cx + 6, cy + 4, accent, true);
        Component state = Component.translatable(complete ? "screen.isekai_mahou.gunslinger.done"
                : next ? "screen.isekai_mahou.gunslinger.next" : "screen.isekai_mahou.gunslinger.locked");
        g.drawString(font, state, cx + cw - 6 - font.width(state), cy + 4, complete ? GREEN : next ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);

        int ly = cy + 16;
        boolean lvOk = complete || (d.level() >= r.level() && d.circle() >= r.circle());
        check(g, cx + 6, ly, lvOk);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.gunslinger.req_level", r.level(), r.circle()), cw - 20),
                cx + 16, ly, lvOk ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
        ly += 11;
        for (TriggerPath.Req q : r.reqs()) {
            int cur = Math.min(q.current(d), q.amount());
            boolean ok = complete || cur >= q.amount();
            check(g, cx + 6, ly, ok);
            int barW = Math.min(70, cw / 4);
            g.drawString(font, MahouUi.fit(font, Component.translatable(q.langKey(), q.amount()), cw - barW - 58), cx + 16, ly,
                    ok ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            MahouUi.bar(g, cx + cw - barW - 38, ly + 2, barW, 4, complete ? 1F : cur / (float) q.amount(), ok ? GREEN : ACCENT);
            String num = complete ? "✔" : cur + "/" + q.amount();
            g.drawString(font, num, cx + cw - 6 - font.width(num), ly, ok ? GREEN : MahouUi.TEXT_DIM, false);
            ly += 11;
        }
        // Recompensas
        List<Component> rewardTip = new ArrayList<>();
        MutableComponent reward = Component.translatable("screen.isekai_mahou.gunslinger.reward").withStyle(ChatFormatting.GOLD);
        int rx = cx + 6;
        for (String gid : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(gid);
            if (glyph == null) continue;
            GlyphArt.glyph(g, glyph, rx, ly, 10);
            rx += 12;
            rewardTip.add(glyph.displayName().withStyle(ChatFormatting.GOLD));
            rewardTip.add(glyph.description().withStyle(ChatFormatting.GRAY));
        }
        // técnicas de anime liberadas neste rank (algumas pedem um feito a mais)
        for (GsTechniques.Technique t : GsTechniques.ofRank(n)) {
            Glyph glyph = t.glyphObj();
            if (glyph == null) continue;
            GlyphArt.glyph(g, glyph, rx, ly, 10);
            boolean has = d.knowsGlyph(glyph.id());
            if (has) g.fill(rx + 7, ly + 7, rx + 10, ly + 10, GREEN);
            rx += 12;
            rewardTip.add(Component.translatable("screen.isekai_mahou.gunslinger.tech.title", glyph.displayName())
                    .withColor(0xFF000000 | t.rgb()));
            rewardTip.add(glyph.description().withStyle(ChatFormatting.GRAY));
            for (Component line : t.requirement(d)) rewardTip.add(Component.literal("  • ").append(line).withStyle(has ? ChatFormatting.GREEN
                    : ChatFormatting.YELLOW));
        }
        if (r.weapon() != null) {
            reward.append(" ").append(r.weapon().displayName().withStyle(ChatFormatting.YELLOW));
            rewardTip.add(Component.translatable("screen.isekai_mahou.gunslinger.reward_weapon", r.weapon().displayName()));
        }
        if (r.legend() != null) {
            Legends.Legend legend = Legends.get(r.legend());
            if (legend != null) {
                reward.append(" ").append(Component.translatable("screen.isekai_mahou.gunslinger.reward_legend").withStyle(ChatFormatting.LIGHT_PURPLE));
                rewardTip.add(Component.translatable("screen.isekai_mahou.gunslinger.reward_legend.tip", legend.title()));
            }
        }
        reward.append(" ").append(Component.translatable("screen.isekai_mahou.gunslinger.reward_xp", r.xp()).withStyle(ChatFormatting.AQUA));
        g.drawString(font, MahouUi.fit(font, reward, cw - (rx - cx) - 6), rx, ly + 1, MahouUi.TEXT, false);
        if (!rewardTip.isEmpty() && ly >= clipTop && ly + 11 <= clipBottom) tips.add(new Tip(cx + 6, ly, cw - 12, 11, rewardTip));
        ly += 13;

        if (showButtons) {
            List<Component> missing = new ArrayList<>();
            boolean can = TriggerPath.meets(d, n, missing);
            int bw = Math.min(118, (cw - 18) / 2);
            button(g, font, cx + 6, ly + 1, bw, 15, Component.translatable("screen.isekai_mahou.gunslinger.claim"), can, ACCENT, mx, my,
                    clipTop, clipBottom, () -> PacketDistributor.sendToServer(new GsNetwork.Claim(n)), missing);
            int duel = TriggerPath.pendingDuel(d);
            if (duel > 0) {
                List<Component> tip = List.of(Component.translatable(duel == 2 ? "screen.isekai_mahou.gunslinger.duel_tip2"
                        : "screen.isekai_mahou.gunslinger.duel_tip1").withStyle(ChatFormatting.GRAY));
                button(g, font, cx + 12 + bw, ly + 1, bw, 15, Component.translatable("screen.isekai_mahou.gunslinger.duel"), true,
                        0xFFE0503A, mx, my, clipTop, clipBottom, () -> PacketDistributor.sendToServer(new GsNetwork.Duel(duel)), tip);
            }
        }
        return ch;
    }

    private static void check(GuiGraphics g, int x, int y, boolean ok) {
        if (ok) {
            g.fill(x + 1, y + 4, x + 3, y + 6, GREEN);
            g.fill(x + 3, y + 5, x + 4, y + 7, GREEN);
            g.fill(x + 4, y + 2, x + 5, y + 6, GREEN);
            g.fill(x + 5, y + 1, x + 6, y + 3, GREEN);
        } else {
            MahouUi.diamond(g, x + 3, y + 4, 2, 0x80FFFFFF);
        }
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, boolean enabled, int color, int mx, int my,
                        int clipTop, int clipBottom, Runnable action, List<Component> tip) {
        boolean hover = enabled && mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int base = enabled ? color : 0xFF555060;
        MahouUi.roundRect(g, bx, by, bw, bh, MahouUi.alpha(MahouUi.lighten(base, hover ? 0.3F : 0F), 0xD0),
                MahouUi.alpha(MahouUi.darken(base, 0.5F), 0xD0));
        MahouUi.roundOutline(g, bx, by, bw, bh, enabled ? MahouUi.GOLD : 0xFF3A3640);
        FormattedCharSequence t = MahouUi.fit(font, label, bw - 6);
        g.drawString(font, t, bx + (bw - font.width(t)) / 2, by + (bh - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF9A96A6, true);
        boolean visible = by >= clipTop && by + bh <= clipBottom;
        if (enabled && visible) hits.add(new Hit(bx, by, bw, bh, action));
        if (!tip.isEmpty() && visible) tips.add(new Tip(bx, by, bw, bh, tip));
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
        if (mx < x + LIST_W || maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        return true;
    }
}
