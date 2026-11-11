package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Banner grande de subida de NÍVEL ("NÍVEL 12!" + o que melhorou: vida, mana/vigor/foco, complexidade, glifos Arcanos novos)
 * e de CÍRCULO ("CÍRCULO 5!" + título + o que foi liberado). Entra com um "pop", brilho passando no título e as
 * linhas surgindo uma a uma. O tempo só corre enquanto o HUD está visível (telas abertas e cutscenes pausam).
 */
final class HudBanner {
    private static final float DURATION = 5600F;
    private static final float FADE_IN = 260F;
    private static final float FADE_OUT = 700F;
    /** Um nível novo dentro deste tempo (ms) atualiza o banner de nível atual em vez de enfileirar outro. */
    private static final float MERGE_WINDOW = 1500F;
    private static final int MAX_QUEUE = 4;

    private static final int LEVEL_ACCENT = 0xFFB98CFF;
    private static final int LEVEL_TITLE = 0xFFFFE6A0;
    private static final int CIRCLE_ACCENT = 0xFFFFC94F;
    private static final int CIRCLE_TITLE = 0xFFFFD86B;

    /** Uma melhoria: ícone (sprite do HUD), cor do ícone, texto e cor do texto. */
    private record Item(int[] icon, int iconColor, Component text, int color) {}

    private static final class Banner {
        final boolean circle;
        int from, to;
        Component title = Component.empty();
        Component caption;
        final List<Item> items = new ArrayList<>();
        // Nível: glifos Arcanos aprendidos (chegam na mesma sincronização ou logo depois) e tier liberado
        int arcaneBaseline;
        int arcaneLearned;
        int arcaneTier;
        int capCircle;
        float age;
        boolean started;

        Banner(boolean circle) {
            this.circle = circle;
        }
    }

    private static final LinkedList<Banner> QUEUE = new LinkedList<>();
    private static long lastFrame;

    private HudBanner() {}

    static void clear() {
        QUEUE.clear();
        lastFrame = 0;
    }

    // =====================================================================================
    //  Eventos (vindos do HudState ao perceber a mudança nos dados sincronizados)
    // =====================================================================================
    static void levelUp(MagicData d, int from, int to, int arcaneBefore) {
        Banner last = QUEUE.peekLast();
        if (last != null && !last.circle && last.age < MERGE_WINDOW) {
            // Vários níveis seguidos (abate grande, banco aplicado): um banner só, com o total
            last.to = to;
            buildLevel(last, d);
            return;
        }
        Banner b = new Banner(false);
        b.from = from;
        b.to = to;
        b.arcaneBaseline = arcaneBefore;
        buildLevel(b, d);
        offer(b);
    }

    static void circleUp(MagicData d, int from, int to) {
        Banner b = new Banner(true);
        b.from = from;
        b.to = to;
        buildCircle(b, d);
        offer(b);
    }

    private static void offer(Banner b) {
        // Fila cheia: descarta o mais antigo que ainda não apareceu (o que está na tela termina normalmente)
        while (QUEUE.size() >= MAX_QUEUE) {
            if (QUEUE.size() > 1) QUEUE.remove(1);
            else QUEUE.clear();
        }
        QUEUE.addLast(b);
    }

    private static void buildLevel(Banner b, MagicData d) {
        int c = d.circle();
        b.title = Component.translatable("hud.isekai_mahou.ui_hud.banner.level", b.to);
        int gained = b.to - b.from;
        b.caption = gained > 1 ? Component.translatable("hud.isekai_mahou.ui_hud.banner.levels", gained) : null;
        b.items.clear();
        int hp = Progression.bonusHealth(b.to) - Progression.bonusHealth(b.from);
        if (hp > 0) {
            b.items.add(new Item(HudDraw.HEART, 0xFFFF6B6B,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.health", hp), 0xFFFFC4C4));
        }
        // v1.0: o máximo é o Limite treinável (piso pelo nível + treino, até o teto): mostra o que subiu e o novo teto
        int mana = ResourceTraining.limit(b.to, c, d.manaTrained()) - ResourceTraining.limit(b.from, c, d.manaTrained());
        if (mana > 0) b.items.add(resourceItem(d, mana, ResourceTraining.ceiling(b.to, c)));
        int glyphs = Progression.maxGlyphs(b.to, c) - Progression.maxGlyphs(b.from, c);
        if (glyphs > 0) {
            b.items.add(new Item(HudDraw.TABLET, 0xFF8FEFFF,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.glyphs", glyphs, Progression.maxGlyphs(b.to, c)), 0xFFC4F6FF));
        }
        b.arcaneTier = 0;
        for (int tier = 1; tier <= 5; tier++) {
            int lv = Progression.arcaneUnlockLevel(tier);
            if (lv > b.from && lv <= b.to) b.arcaneTier = tier;
        }
        b.capCircle = b.to >= Progression.levelCap(c) && c < Progression.MAX_CIRCLE ? c + 1 : 0;
    }

    /** "+N Mana máxima · teto X" (Vigor/Foco), com o cristal na cor do recurso da classe. */
    private static Item resourceItem(MagicData d, int amount, int ceiling) {
        ResourceStyle st = ResourceStyle.of(d.path().resource());
        return new Item(HudDraw.CRYSTAL, st.labelLight(), st.maxGain(amount, ceiling), st.light());
    }

    private static void buildCircle(Banner b, MagicData d) {
        int from = b.from, to = b.to, level = d.level();
        b.title = Component.translatable("hud.isekai_mahou.ui_hud.banner.circle", to);
        b.caption = Component.translatable("hud.isekai_mahou.ui_hud.banner.circle_caption",
                HudDraw.tr("screen.isekai_mahou.status_window.circle_title." + to));
        b.items.clear();
        b.items.add(new Item(HudDraw.STAR, CIRCLE_TITLE,
                Component.translatable("hud.isekai_mahou.ui_hud.banner.level_cap", Progression.levelCap(to)), 0xFFFFF0B8));
        int mana = ResourceTraining.limit(level, to, d.manaTrained()) - ResourceTraining.limit(level, from, d.manaTrained());
        if (mana > 0) b.items.add(resourceItem(d, mana, ResourceTraining.ceiling(level, to)));
        int glyphs = Progression.maxGlyphs(level, to) - Progression.maxGlyphs(level, from);
        if (glyphs > 0) {
            b.items.add(new Item(HudDraw.TABLET, 0xFF8FEFFF,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.glyphs", glyphs, Progression.maxGlyphs(level, to)), 0xFFC4F6FF));
        }
        if (Progression.maxSpellElements(to) > Progression.maxSpellElements(from)) {
            b.items.add(new Item(HudDraw.ORB_L, 0xFFFF9F6B,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.elements", Progression.maxSpellElements(to)), 0xFFFFD8C0));
        }
        if (!Progression.ultimatesAllowed(from) && Progression.ultimatesAllowed(to)) {
            b.items.add(new Item(HudDraw.STAR, HudDraw.GOLD,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.ultimates"), HudDraw.GOLD_LIGHT));
        }
        if (!Progression.simpleDomainAllowed(from) && Progression.simpleDomainAllowed(to)) {
            b.items.add(new Item(HudDraw.RING, 0xFF8FEFFF,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.simple_domain"), 0xFFC4F6FF));
        }
        if (!Progression.domainAllowed(from) && Progression.domainAllowed(to)) {
            b.items.add(new Item(HudDraw.RING, 0xFFFF6B9F,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.domain"), 0xFFFFC4D8));
        }
    }

    /** Linhas na tela: as fixas + a dos glifos Arcanos (dinâmica nos primeiros segundos) + o aviso de limite. */
    private static List<Item> displayItems(Banner b) {
        if (b.circle) return b.items;
        if (b.age < 4000F) b.arcaneLearned = Math.max(b.arcaneLearned, HudState.arcaneKnown - b.arcaneBaseline);
        List<Item> out = new ArrayList<>(b.items);
        if (b.arcaneLearned > 0) {
            out.add(new Item(HudDraw.STAR, Element.ARCANE.argb(),
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.arcane", b.arcaneLearned), 0xFFE6D8FF));
        } else if (b.arcaneTier > 0) {
            out.add(new Item(HudDraw.STAR, Element.ARCANE.argb(),
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.arcane_tier", b.arcaneTier), 0xFFE6D8FF));
        }
        if (b.capCircle > 0) {
            out.add(new Item(HudDraw.STAR, HudDraw.GOLD,
                    Component.translatable("hud.isekai_mahou.ui_hud.banner.cap", b.capCircle).withStyle(ChatFormatting.BOLD), HudDraw.GOLD));
        }
        return out;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    /** Desenha o banner atual a partir de {@code minTop} (abaixo do Olho Analítico/barras de chefe). */
    static void render(GuiGraphics g, Minecraft mc, int minTop) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0F : Mth.clamp(now - lastFrame, 0L, 100L);
        lastFrame = now;
        Banner b = QUEUE.peekFirst();
        if (b == null) return;
        if (!b.started) {
            b.started = true;
            dt = 0F;
            if (b.circle) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.25F, 0.8F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.0F));
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.9F));
            }
        }
        b.age += dt;
        if (b.age >= DURATION) {
            QUEUE.pollFirst();
            return;
        }
        draw(g, mc, b, minTop);
    }

    private static void draw(GuiGraphics g, Minecraft mc, Banner b, int minTop) {
        Font font = mc.font;
        int gw = g.guiWidth(), gh = g.guiHeight(), cx = gw / 2;
        float age = b.age;
        float alpha = Math.min(1F, age / FADE_IN) * Math.min(1F, (DURATION - age) / FADE_OUT);
        if (alpha <= 0.02F) return;
        int accent = b.circle ? CIRCLE_ACCENT : LEVEL_ACCENT;
        int titleColor = b.circle ? CIRCLE_TITLE : LEVEL_TITLE;

        int top = Math.max(minTop, Math.round(gh * 0.16F));
        // Nunca desce até a mira (nem até o cântico logo acima dela)
        int limit = gh / 2 - 16;
        // Espaço livre entre o card e o rastreador de objetivos (altura estimada com folga)
        int maxHalf = Math.max(56, TopSpace.maxHalf(mc, gw, top, top + 96));
        int titleW = Math.max(1, font.width(b.title));
        List<Item> items = displayItems(b);
        int rowMax = Math.min(Math.min(gw - 40, 340), 2 * maxHalf - 8);

        // 1ª tentativa: tamanho cheio. Se passar do limite (barras de chefe + Olho Analítico ocupando o topo),
        // versão compacta: título menor, letra pequena nas linhas, espaçamentos menores.
        float base = 1.5F, rs = 1F;
        int titleH = 0, titleY = 0, capY = 0, rowsY = 0, rowH = 11, bottom = 0;
        List<List<Item>> rows = List.of();
        boolean compact = false;
        for (int pass = 0; pass < 2; pass++) {
            compact = pass == 1;
            base = compact ? 1.5F : Math.max(1.5F, Math.min(gw >= 360 ? 2.5F : 2F, (2F * maxHalf - 16F) / titleW));
            rs = 1F;
            // Se alguma melhoria não cabe numa linha, todas ficam em letra menor (telas estreitas / rastreador aberto)
            if (compact) rs = 0.75F;
            else for (Item it : items) {
                if (itemWidth(font, it, 1F) > rowMax) {
                    rs = 0.75F;
                    break;
                }
            }
            rows = flow(font, items, rowMax, rs);
            rowH = rs < 1F ? 9 : 11;
            titleH = Math.round(8 * base);
            titleY = top + (compact ? 5 : 7);
            capY = titleY + titleH + (compact ? 3 : 4);
            rowsY = capY + (b.caption != null ? (compact ? 10 : 12) : 0) + 1;
            bottom = rowsY + rows.size() * rowH + 4;
            if (bottom <= limit) break;
        }
        // Mesmo compacto não cabe: mostra só as linhas que cabem (o título sempre aparece)
        if (bottom > limit && !rows.isEmpty()) {
            int fit = Mth.clamp((limit - 4 - rowsY) / rowH, 0, rows.size());
            rows = rows.subList(0, fit);
            bottom = rowsY + fit * rowH + 4;
        }
        float scale = base * (1.55F - 0.55F * HudDraw.easeOutBack(age / 420F));
        int titlePx = Math.round(titleW * base);
        int widest = 0;
        for (List<Item> row : rows) widest = Math.max(widest, rowWidth(font, row, rs));
        int half = Math.min(maxHalf, Math.max(titlePx, widest) / 2 + (compact ? 30 : 44));

        // Faixa escura que abre do centro + filetes na cor do banner
        float open = HudDraw.easeOut(age / 450F);
        HudDraw.hBand(g, cx, top, bottom, Math.round(half * (0.35F + 0.65F * open)), HudDraw.fade(0xC0080612, alpha));
        int lineHalf = Math.round(half * open);
        HudDraw.hBand(g, cx, top, top + 1, lineHalf, HudDraw.fade(accent, alpha));
        HudDraw.hBand(g, cx, bottom - 1, bottom, lineHalf, HudDraw.fade(accent, alpha));
        HudDraw.diamond(g, cx, top, 2, HudDraw.fade(HudDraw.brighten(accent, 0.4F), alpha));
        HudDraw.diamond(g, cx, bottom - 1, 2, HudDraw.fade(HudDraw.brighten(accent, 0.4F), alpha));
        // Brilho atrás do título (pulsa devagar)
        float glow = 0.16F + 0.1F * HudDraw.pulse(1800);
        HudDraw.hBand(g, cx, titleY - 3, titleY + titleH + 3, Math.min(half, titlePx / 2 + 22), HudDraw.fade(accent, glow * alpha));

        if (b.circle) sparkles(g, cx, titleY, titlePx, titleH, half, age, alpha);
        title(g, font, b.title, cx, titleY + titleH / 2F, scale, titleColor, alpha);

        // Reflexo de luz atravessando o título
        if (age > 420F && age < 1400F) {
            float t = (age - 420F) / 980F;
            int span = titlePx + 24;
            int sx = cx - span / 2 + Math.round(span * t);
            g.enableScissor(sx - 4, titleY - 4, sx + 5, titleY + titleH + 4);
            title(g, font, b.title, cx, titleY + titleH / 2F, scale, 0xFFFFFFFF, 0.8F * alpha);
            g.disableScissor();
        }

        if (b.caption != null) {
            float ca = alpha * Mth.clamp((age - 260F) / 220F, 0F, 1F);
            Component cap = b.caption.copy().withStyle(ChatFormatting.ITALIC);
            int cw = font.width(cap);
            HudDraw.text(g, font, cap, cx - cw / 2, capY + Math.round((1F - ca) * 3F), HudDraw.fade(b.circle ? HudDraw.GOLD_LIGHT : 0xFFE6D8FF, ca), true);
        }

        // Melhorias: linhas surgindo uma a uma
        for (int r = 0; r < rows.size(); r++) {
            float ra = Mth.clamp((age - (380F + r * 130F)) / 200F, 0F, 1F);
            if (ra <= 0F) continue;
            List<Item> row = rows.get(r);
            int rx = cx - rowWidth(font, row, rs) / 2;
            int ry = rowsY + r * rowH + Math.round((1F - ra) * 4F);
            float a = alpha * ra;
            for (Item it : row) {
                int iy = ry + (it.icon()[3] > 7 ? -1 : 0) - (rs < 1F ? 1 : 0);
                HudDraw.sprite(g, it.icon(), rx + (7 - it.icon()[2]) / 2, iy, HudDraw.fade(it.iconColor(), a));
                if (rs < 1F) HudDraw.scaledText(g, font, it.text(), rx + 10, ry, rs, HudDraw.fade(it.color(), a), true);
                else HudDraw.text(g, font, it.text(), rx + 10, ry, HudDraw.fade(it.color(), a), true);
                rx += itemWidth(font, it, rs) + 14;
            }
        }
    }

    private static void title(GuiGraphics g, Font font, Component text, int cx, float cy, float scale, int color, float alpha) {
        int tw = font.width(text);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0F);
        g.pose().scale(scale, scale, 1F);
        HudDraw.text(g, font, text, -tw / 2, -4, HudDraw.fade(color, alpha), true);
        g.pose().popPose();
    }

    /** Estrelinhas piscando em volta do título do Círculo (sem passar da faixa). */
    private static void sparkles(GuiGraphics g, int cx, int titleY, int titleW, int titleH, int half, float age, float alpha) {
        int hw = titleW / 2;
        int near = Math.min(hw + 12, half - 4), far = Math.min(hw + 22, half - 4);
        int[][] spots = {
                {-near, 2}, {near, 2},
                {-far, titleH - 3}, {far, titleH - 3},
                {-hw / 2, -5}, {hw / 2, titleH + 3}
        };
        for (int i = 0; i < spots.length; i++) {
            float tw = 0.5F + 0.5F * Mth.sin(age / 170F + i * 1.7F);
            HudDraw.spriteCentered(g, HudDraw.STAR, cx + spots[i][0], titleY + spots[i][1], HudDraw.fade(HudDraw.GOLD_LIGHT, tw * alpha));
        }
    }

    private static List<List<Item>> flow(Font font, List<Item> items, int maxW, float scale) {
        List<List<Item>> rows = new ArrayList<>();
        List<Item> row = new ArrayList<>();
        int w = 0;
        for (Item it : items) {
            int iw = itemWidth(font, it, scale);
            if (!row.isEmpty() && w + 14 + iw > maxW) {
                rows.add(row);
                row = new ArrayList<>();
                w = 0;
            }
            w += (row.isEmpty() ? 0 : 14) + iw;
            row.add(it);
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /** Ícone (10 px) + texto na escala da linha. */
    private static int itemWidth(Font font, Item it, float scale) {
        return 10 + (int) Math.ceil(font.width(it.text()) * scale);
    }

    private static int rowWidth(Font font, List<Item> row, float scale) {
        int w = 0;
        for (Item it : row) w += itemWidth(font, it, scale);
        return w + Math.max(0, row.size() - 1) * 14;
    }
}
