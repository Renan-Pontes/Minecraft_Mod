package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildFaction;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.ObjectiveView;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.QuestView;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Desenho das telas da Guilda: tema "madeira e pergaminho" (painel escuro com borda dourada, cartões cor de couro),
 * selos de rank nas cores do HUD, barras de progresso, botões desenhados à mão e cartões de missão.
 * Os cliques são registrados como {@link Hit} durante o desenho e testados no mouseClicked.
 */
final class GuildUi {
    static final int BG_TOP = 0xF4211810, BG_BOTTOM = 0xF4140E09;
    static final int CARD = 0xFF33281B, CARD_HOVER = 0xFF3F3121, CARD_DIM = 0xFF262019;
    static final int BORDER = 0xFFC9A55A, BORDER_DIM = 0x70C9A55A, BORDER_SOFT = 0x40C9A55A;
    static final int TEXT = 0xFFF3E6C8, MUTED = 0xFFB8A27A, FAINT = 0xFF7D6C52;
    static final int GREEN = 0xFF7DDA6B, RED = 0xFFE8735E, GOLD = 0xFFFFD86B, BLUE = 0xFF8FC8FF;

    /** Área clicável registrada no último quadro. */
    record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    /** Coleta de cliques e tooltips de um quadro. */
    static final class Frame {
        final List<Hit> hits = new ArrayList<>();
        ItemStack hoverStack = ItemStack.EMPTY;
        List<Component> hoverText;

        void clear() {
            hits.clear();
            hoverStack = ItemStack.EMPTY;
            hoverText = null;
        }

        boolean click(double mx, double my) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) {
                    clickSound();
                    h.action.run();
                    return true;
                }
            }
            return false;
        }

        void tooltips(GuiGraphics g, Font font, int mx, int my) {
            if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
            else if (hoverText != null) g.renderComponentTooltip(font, hoverText, mx, my);
        }
    }

    private GuildUi() {}

    static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // =====================================================================================
    //  Painéis e molduras
    // =====================================================================================
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, BG_TOP, BG_BOTTOM);
        g.renderOutline(x, y, w, h, BORDER);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, BORDER_SOFT);
        // Cantoneiras douradas
        for (int[] c : new int[][]{{x, y}, {x + w - 5, y}, {x, y + h - 5}, {x + w - 5, y + h - 5}}) {
            g.fill(c[0], c[1], c[0] + 5, c[1] + 5, BORDER);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 4, c[1] + 4, 0xFF6B5326);
        }
    }

    static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int accent) {
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, BORDER_DIM);
        g.fill(x, y, x + 2, y + h, accent);
    }

    static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER_DIM);
        g.fill(x + w / 2 - 2, y - 1, x + w / 2 + 2, y + 2, BORDER);
    }

    /** Selo do rank: quadrado com a letra no centro (escala conforme o tamanho). */
    static void rankBadge(GuiGraphics g, Font font, int x, int y, int size, GuildRank rank, boolean dim) {
        rankBadge(g, font, x, y, size, rank, dim, rank == null ? 0xFF55504A : rank.argb);
    }

    /** Selo do rank com a cor da moldura escolhida (missões de facção: cor da facção). */
    static void rankBadge(GuiGraphics g, Font font, int x, int y, int size, GuildRank rank, boolean dim, int frameColor) {
        int color = frameColor;
        if (dim) color = mix(color, 0xFF201A14, 0.55F);
        g.fill(x, y, x + size, y + size, 0xFF120D08);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, mix(color, 0xFF000000, 0.55F));
        g.renderOutline(x, y, size, size, color);
        g.fill(x + 2, y + 2, x + size - 2, y + 3, mix(color, 0xFFFFFFFF, 0.25F));
        Component letter = rank == null ? Component.translatable("guild.isekai_mahou.rank.none") : rank.letter();
        float scale = Math.max(1.0F, size / 11.0F);
        g.pose().pushPose();
        g.pose().translate(x + size / 2.0F, y + size / 2.0F - 3.5F * scale + 0.5F, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, letter, -font.width(letter) / 2, 0, dim ? FAINT : 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    static void progressBar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        frac = Mth.clamp(frac, 0, 1);
        g.fill(x, y, x + w, y + h, 0xFF0E0A06);
        int fw = Math.round((w - 2) * frac);
        if (fw > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, mix(color, 0xFFFFFFFF, 0.25F), color);
        }
        g.renderOutline(x, y, w, h, BORDER_SOFT);
    }

    /** Botão desenhado (com hover e estado desabilitado). Registra o clique se habilitado. */
    static void button(GuiGraphics g, Font font, Frame f, int x, int y, int w, int h, Component label, int mx, int my,
                       boolean enabled, int accent, Runnable action) {
        boolean hover = enabled && over(mx, my, x, y, w, h);
        int base = enabled ? mix(accent, 0xFF1A130C, hover ? 0.35F : 0.55F) : 0xFF2A241D;
        g.fill(x, y, x + w, y + h, base);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, enabled ? mix(accent, 0xFFFFFFFF, 0.2F) : 0xFF3A332A);
        g.renderOutline(x, y, w, h, enabled ? (hover ? 0xFFFFFFFF : accent) : 0xFF4A4036);
        Component shown = ellipsize(font, label, w - 6);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, enabled ? TEXT : FAINT, enabled);
        if (enabled) f.hits.add(new Hit(x, y, w, h, action));
    }

    static void item(GuiGraphics g, Frame f, ItemStack stack, int x, int y, int mx, int my) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
        if (over(mx, my, x, y, 16, 16)) f.hoverStack = stack;
    }

    static Component ellipsize(Font font, Component text, int width) {
        if (font.width(text) <= width) return text;
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Component.literal(cut.getString() + "…").withStyle(text.getStyle());
    }

    static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n * 10;
    }

    static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int coins() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(GuildContent.GUILD_COIN.get())) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(GuildContent.GUILD_COIN.get())) n += s.getCount();
        return n;
    }

    /** Moedas do jogador (ícone + número). Retorna a largura usada. */
    static int coinCounter(GuiGraphics g, Font font, int x, int y) {
        g.renderItem(new ItemStack(GuildContent.GUILD_COIN.get()), x, y);
        String n = String.valueOf(coins());
        g.drawString(font, n, x + 18, y + 4, GOLD, true);
        return 18 + font.width(n);
    }

    // =====================================================================================
    //  Cartão de missão
    // =====================================================================================
    /** Altura do cartão de uma missão. */
    static int questHeight(QuestView q, boolean withDesc) {
        return 6 + 11 + (withDesc ? 10 : 0) + q.objectives().size() * 11 + 20;
    }

    private static final String CATEGORY_KEY = "screen.isekai_mahou.guild.category.";

    private static int categoryColor(String category) {
        return switch (category) {
            case "boss" -> 0xFFFF7A6B;
            case "trial" -> 0xFFD9A8FF;
            case "chamber" -> 0xFFFFC94F;
            case "kill" -> 0xFFFFB066;
            case "gather" -> GREEN;
            case "explore" -> BLUE;
            default -> MUTED;
        };
    }

    /** Botões do cartão (à direita do título); cada entrada: rótulo, cor, habilitado, ação. */
    record CardButton(Component label, int accent, boolean enabled, Runnable action) {}

    /**
     * Desenha uma missão: selo do rank, título, descrição, objetivos com progresso e recompensas.
     * {@code status} (opcional) é escrito à direita quando não há botões.
     */
    static void quest(GuiGraphics g, Font font, Frame f, QuestView q, int x, int y, int w, boolean withDesc, int mx, int my,
                      List<CardButton> buttons, Component status, int statusColor) {
        int h = questHeight(q, withDesc);
        GuildRank rank = q.guildRank();
        boolean dim = q.state() == GuildNetwork.Q_LOCKED || q.state() == GuildNetwork.Q_TAKEN;
        boolean hover = over(mx, my, x, y, w, h);
        GuildFaction faction = q.guildFaction();
        int accent = q.state() == GuildNetwork.Q_READY ? GREEN : q.state() == GuildNetwork.Q_TRIAL_OFFER ? 0xFFB46BFF
                : faction.isGuild() ? rank.argb : faction.argb();
        card(g, x, y, w, h, dim ? CARD_DIM : hover ? CARD_HOVER : CARD, dim ? mix(accent, 0xFF201A14, 0.6F) : accent);
        rankBadge(g, font, x + 6, y + 6, 20, rank, dim, faction.isGuild() ? rank.argb : faction.argb());
        q.hunt().ifPresent(hv -> huntFlag(g, font, f, hv, x + 21, y + 2, mx, my, dim));
        // Categoria (caça, coleta, chefe...) em letra miúda sob o selo do rank.
        if (!q.category().isEmpty()) {
            Component cat = Component.translatableWithFallback(CATEGORY_KEY + q.category(), q.category());
            g.pose().pushPose();
            g.pose().translate(x + 16, y + 29, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(font, cat, -font.width(cat) / 2, 0, dim ? FAINT : categoryColor(q.category()), false);
            g.pose().popPose();
        }

        // Botões / status à direita do título
        int right = x + w - 5;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            CardButton b = buttons.get(i);
            int bw = Math.max(44, font.width(b.label) + 10);
            right -= bw;
            button(g, font, f, right, y + 4, bw, 14, b.label, mx, my, b.enabled, b.accent, b.action);
            right -= 3;
        }
        if (buttons.isEmpty() && status != null) {
            int sw = font.width(status);
            right -= sw;
            g.drawString(font, status, right, y + 7, statusColor, true);
            right -= 4;
        }

        int tx = x + 32;
        int ty = y + 6;
        Component title = q.title().copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, ellipsize(font, title, right - tx - 4), tx, ty + 1, dim ? MUTED : TEXT, true);
        ty += 11;
        if (withDesc) {
            g.drawString(font, ellipsize(font, q.description(), x + w - tx - 6), tx, ty, dim ? FAINT : MUTED, false);
            if (font.width(q.description()) > x + w - tx - 6 && over(mx, my, tx, ty, x + w - tx - 6, 9)) {
                f.hoverText = List.of(q.title(), q.description().copy().withStyle(ChatFormatting.GRAY));
            }
            ty += 10;
        }
        // Objetivos
        int barW = Math.min(70, (x + w - tx) / 3);
        for (ObjectiveView o : q.objectives()) {
            g.pose().pushPose();
            g.pose().translate(tx, ty, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            g.renderItem(o.icon(), 0, 0);
            g.pose().popPose();
            boolean done = o.done();
            int labelW = x + w - tx - 12 - barW - 36;
            g.drawString(font, ellipsize(font, o.label(), labelW), tx + 11, ty, done ? GREEN : dim ? FAINT : TEXT, false);
            int bx = x + w - 8 - barW - 30;
            progressBar(g, bx, ty + 1, barW, 6, o.target() == 0 ? 1 : o.progress() / (float) o.target(), done ? GREEN : accent);
            String pt = done ? "✔" : o.progress() + "/" + o.target();
            g.drawString(font, pt, x + w - 6 - font.width(pt), ty, done ? GREEN : MUTED, false);
            ty += 11;
        }
        // Recompensas
        ty += 1;
        g.fill(tx, ty, x + w - 6, ty + 1, BORDER_SOFT);
        ty += 2;
        int rx = tx;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.rewards"), rx, ty + 5, FAINT, false);
        rx += font.width(Component.translatable("screen.isekai_mahou.guild.rewards")) + 4;
        if (q.coins() > 0) rx = rewardChip(g, font, f, new ItemStack(GuildContent.GUILD_COIN.get()), "" + q.coins(), GOLD, rx, ty, mx, my,
                Component.translatable("screen.isekai_mahou.guild.reward_coins", q.coins()));
        if (q.xp() > 0) rx = rewardChip(g, font, f, new ItemStack(Items.EXPERIENCE_BOTTLE), "" + q.xp(), 0xFFB98CFF, rx, ty, mx, my,
                Component.translatable("screen.isekai_mahou.guild.reward_xp", q.xp()));
        if (q.points() > 0) {
            String s = "★" + q.points();
            g.drawString(font, s, rx, ty + 5, 0xFFFFC94F, true);
            if (over(mx, my, rx, ty + 3, font.width(s), 11)) {
                f.hoverText = List.of(Component.translatable("screen.isekai_mahou.guild.reward_points", q.points()));
            }
            rx += font.width(s) + 6;
        }
        // Reputação (v0.8): "+20" na cor da facção, com o nome no tooltip.
        for (var e : q.reputation().entrySet()) {
            Reputation.Faction rf = Reputation.faction(e.getKey());
            int color = rf != null ? rf.argb() : MUTED;
            String txt = (e.getValue() > 0 ? "+" : "") + e.getValue() + "♦";
            if (rx + font.width(txt) > x + w - 4) break;
            g.drawString(font, txt, rx, ty + 5, color, true);
            if (over(mx, my, rx, ty + 3, font.width(txt), 11)) {
                Component name = rf != null ? rf.coloredName() : Component.literal(e.getKey());
                f.hoverText = List.of(Component.translatable("screen.isekai_mahou.guild.reward_rep", e.getValue(), name));
            }
            rx += font.width(txt) + 6;
        }
        for (ItemStack s : q.items()) {
            if (rx + 16 > x + w - 4) break;
            item(g, f, s, rx, ty, mx, my);
            rx += 18;
        }
    }

    /**
     * v0.9: bandeirinha "⚑" no canto do selo de rank das missões com Área de Caça (na cor da área), com a explicação no
     * tooltip: no quadro, "ganha uma Área de Caça ao aceitar"; ativa, o nome da área, a distância e a onda.
     */
    private static void huntFlag(GuiGraphics g, Font font, Frame f, GuildNetwork.HuntView hv, int x, int y, int mx, int my, boolean dim) {
        int color = dim ? FAINT : 0xFF000000 | (hv.phase() == GuildNetwork.HUNT_FIGHT ? 0xFF5B4A : hv.rgb());
        g.drawString(font, "⚑", x, y, color, true);
        if (!over(mx, my, x - 1, y - 1, 8, 10)) return;
        List<Component> tip = new ArrayList<>();
        if (hv.phase() == GuildNetwork.HUNT_OFFER) {
            tip.add(Component.translatable("screen.isekai_mahou.guild.hunt.offer").withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("screen.isekai_mahou.guild.hunt.offer_desc", hv.waves()).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(hv.name().copy().withColor(hv.rgb()));
            var p = Minecraft.getInstance().player;
            if (p != null && hv.pos().isPresent() && hv.pos().get().dimension() == p.level().dimension()) {
                var c = hv.pos().get().pos();
                int dist = (int) Math.sqrt(p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5));
                tip.add(Component.translatable("screen.isekai_mahou.guild.hunt.distance", dist).withStyle(ChatFormatting.GRAY));
            }
            tip.add(hv.phase() == GuildNetwork.HUNT_FIGHT
                    ? Component.translatable("screen.isekai_mahou.guild.hunt.fighting", Math.max(1, hv.wave()), hv.waves()).withStyle(ChatFormatting.RED)
                    : Component.translatable("screen.isekai_mahou.guild.hunt.travel").withStyle(ChatFormatting.YELLOW));
        }
        f.hoverText = tip;
    }

    private static int rewardChip(GuiGraphics g, Font font, Frame f, ItemStack icon, String text, int color, int x, int y, int mx, int my,
                                  Component tip) {
        g.renderItem(icon, x, y);
        g.drawString(font, text, x + 16, y + 5, color, true);
        int w = 16 + font.width(text) + 6;
        if (over(mx, my, x, y, w, 16)) f.hoverText = List.of(tip);
        return x + w;
    }

    // =====================================================================================
    //  Cartão de Aventureiro (desenho grande)
    // =====================================================================================
    static void adventurerCard(GuiGraphics g, Font font, int x, int y, int w, GuildNetwork.State s, Component name) {
        GuildRank rank = s.guildRank();
        int h = 92;
        int color = rank == null ? 0xFF6B6055 : rank.argb;
        g.fill(x, y, x + w, y + h, mix(color, 0xFF15100B, 0.78F));
        g.fillGradient(x, y, x + w, y + 14, mix(color, 0xFF15100B, 0.35F), mix(color, 0xFF15100B, 0.6F));
        g.renderOutline(x, y, w, h, color);
        Component head = Component.translatable("screen.isekai_mahou.guild.card_title");
        g.drawString(font, ellipsize(font, head, w - 8), x + 4, y + 3, 0xFFFFFFFF, true);
        rankBadge(g, font, x + w - 34, y + 20, 28, rank, false);
        g.drawString(font, ellipsize(font, name, w - 44), x + 5, y + 20, TEXT, true);
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_rank", rank == null
                ? Component.translatable("guild.isekai_mahou.rank.none") : rank.letter()), x + 5, y + 32, MUTED, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_completed", s.completed()), x + 5, y + 43, MUTED, false);
        GuildRank next = rank == null ? null : rank.next();
        int by = y + 60;
        if (next != null) {
            int need = next.pointsRequired;
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_points", s.points(), need), x + 5, by, TEXT, false);
            int base = rank.pointsRequired;
            progressBar(g, x + 5, by + 11, w - 10, 7, (s.points() - base) / (float) Math.max(1, need - base),
                    s.points() >= need ? GREEN : color);
            Component hint = s.trial().isPresent() ? Component.translatable("screen.isekai_mahou.guild.card_trial_going")
                    : s.trialReady() ? Component.translatable("screen.isekai_mahou.guild.card_trial_ready", next.letter())
                    : Component.translatable("screen.isekai_mahou.guild.card_next", next.letter());
            g.drawString(font, ellipsize(font, hint, w - 10), x + 5, by + 21, s.trialReady() ? GREEN : FAINT, false);
        } else if (rank != null) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_points_max", s.points()), x + 5, by, GOLD, false);
            g.drawString(font, ellipsize(font, Component.translatable("screen.isekai_mahou.guild.card_max"), w - 10), x + 5, by + 12, GOLD, false);
        }
    }
}
