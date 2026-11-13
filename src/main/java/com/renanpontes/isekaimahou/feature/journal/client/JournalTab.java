package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.journal.JournalNetwork;
import com.renanpontes.isekaimahou.feature.journal.JournalService;
import com.renanpontes.isekaimahou.feature.journal.JournalTargets;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalRewards;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;
import com.renanpontes.isekaimahou.feature.journal.story.StoryBook;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Diário" do menu B: capítulos à esquerda (bloqueado / em andamento / concluído), e à direita o capítulo
 * escolhido com os objetivos (✔ e progresso), as recompensas e a crônica (texto de lore com rolagem).
 * Clicar num objetivo mostra a crônica daquele passo. Botão para chamar o Espírito Guia (dica do objetivo atual).
 * <p>
 * v1.0: a lista tem duas seções — a ROTA DA CLASSE do jogador (no topo, na cor da classe) e a HISTÓRIA. Capítulos
 * secretos só aparecem depois de abrirem.
 * <p>
 * v1.0 release (campanha): a história aparece agrupada em ATOS (cabeçalhos; o mouse mostra a pergunta do ato), a crônica
 * de cada capítulo guarda os "Ecos" (as falas da campanha que já passaram — vistas, puladas ou com a campanha desligada)
 * e um botãozinho ao lado do Espírito Guia liga/desliga a campanha (a mesma opção do menu do Minecraft).
 */
public class JournalTab implements MenuTab {
    private static final int LIST_W = 132;
    private static final int ENTRY_H = 30;
    private static final int HEADER_H = 13;
    private static final int STEP_H = 12;
    /** Lembra a escolha entre aberturas do menu. */
    private static String lastChapter;
    /** Recarga local do botão (o servidor confere de novo). */
    private static long guideReadyAt;

    /** Uma linha da lista: cabeçalho de seção (chapter null) ou capítulo (com o nº dentro da trilha). */
    private record Row(@Nullable Component header, int headerColor, @Nullable JournalChapter chapter, int number, int top, int height,
                       @Nullable Component tip) {}

    private int x, y, w, h;
    /** Capítulo escolhido (id, nas duas trilhas). */
    @Nullable
    private String selected;
    private int selectedStep = -1;
    private int listScroll, listMax;
    private int loreScroll, loreMax;
    private int bookVersion = -1;
    private Button guideButton;
    private Button campaignButton;
    /** Estado da campanha já refletido no botão (para não recriar a dica a cada tick). */
    private int campaignShown = -1;

    // Áreas calculadas no render (para os cliques)
    private int listTop, listBottom;
    private int stepsTop;
    private int loreX, loreY, loreW, loreH;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        guideButton = screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.journal.call_guide"), b -> callGuide())
                .bounds(x, y + h - 18, LIST_W - 22, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.isekai_mahou.journal.call_guide.tooltip")))
                .build());
        // Campanha: "se o player pular, pulou" — um clique desliga as falas do Eco (elas continuam guardadas no Diário).
        campaignButton = screen.addTabWidget(Button.builder(Component.literal("✦"), b -> {
            StoryClient.toggleCampaign();
            click();
        }).bounds(x + LIST_W - 20, y + h - 18, 20, 18).build());
        campaignShown = -1;
        refreshCampaignButton();
    }

    private void refreshCampaignButton() {
        int now = StoryClient.campaign() ? 1 : 0;
        if (now == campaignShown) return;
        campaignShown = now;
        campaignButton.setMessage(Component.literal(now == 1 ? "✦" : "✧"));
        campaignButton.setTooltip(Tooltip.create(Component.translatable(now == 1
                ? "screen.isekai_mahou.journal.campaign.on" : "screen.isekai_mahou.journal.campaign.off")));
    }

    private void callGuide() {
        long now = Util.getMillis();
        if (now < guideReadyAt || JournalBook.client().isEmpty()) return;
        guideReadyAt = now + JournalService.GUIDE_COOLDOWN * 50L;
        PacketDistributor.sendToServer(JournalNetwork.CallGuide.INSTANCE);
    }

    @Override
    public void tick() {
        long left = guideReadyAt - Util.getMillis();
        guideButton.active = left <= 0 && !JournalBook.client().isEmpty();
        guideButton.setMessage(left > 0
                ? Component.translatable("screen.isekai_mahou.journal.call_guide.wait", (left + 999) / 1000)
                : Component.translatable("screen.isekai_mahou.journal.call_guide"));
        refreshCampaignButton();
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Font font = Minecraft.getInstance().font;
        JournalBook book = JournalBook.client();
        MagicData d = ClientMagic.data();
        if (book.isEmpty()) {
            Component empty = Component.translatable("screen.isekai_mahou.journal.empty");
            g.drawCenteredString(font, empty, x + w / 2, y + h / 2 - 4, JournalUi.MUTED);
            return;
        }
        List<Row> rows = rows(book, d);
        if (bookVersion != JournalBook.clientVersion || selectedChapter(book, rows) == null) {
            bookVersion = JournalBook.clientVersion;
            selected = defaultSelection(book, d, rows);
            selectedStep = -1;
            loreScroll = 0;
        }
        renderList(g, font, book, d, rows, mx, my);
        JournalChapter ch = selectedChapter(book, rows);
        if (ch != null) renderChapter(g, font, book, d, ch, numberOf(ch, rows), mx, my);
    }

    /** A lista: rota da classe do jogador (se houver) e a história (sem os secretos ainda fechados). */
    private static List<Row> rows(JournalBook book, MagicData d) {
        List<Row> out = new ArrayList<>();
        int top = 0;
        MagicPath path = d.path();
        List<JournalChapter> track = book.classTrack(path);
        if (!track.isEmpty()) {
            Component head = Component.translatable("screen.isekai_mahou.journal.section.class", path.displayName());
            out.add(new Row(head, path.rgb(), null, 0, top, HEADER_H, null));
            top += HEADER_H;
            for (int i = 0; i < track.size(); i++) {
                out.add(new Row(null, 0, track.get(i), i + 1, top, ENTRY_H, null));
                top += ENTRY_H + 2;
            }
            out.add(new Row(Component.translatable("screen.isekai_mahou.journal.section.story"), 0xFFD86B, null, 0, top, HEADER_H, null));
            top += HEADER_H;
        }
        int lastAct = -1;
        for (int i = 0; i < book.chapters().size(); i++) {
            if (!book.visible(i, d)) continue;
            JournalChapter c = book.chapters().get(i);
            // Campanha: cabeçalho do Ato antes do primeiro capítulo dele. Ato ainda fechado não mostra o nome (sem spoiler).
            StoryBook.Act act = StoryBook.actOf(c.order());
            if (act.number() != lastAct) {
                lastAct = act.number();
                if (book.state(i, d) != JournalBook.State.LOCKED) {
                    out.add(new Row(act.title(), act.color(), null, 0, top, HEADER_H, act.question()));
                    top += HEADER_H;
                }
            }
            out.add(new Row(null, 0, c, i + 1, top, ENTRY_H, null));
            top += ENTRY_H + 2;
        }
        return out;
    }

    @Nullable
    private JournalChapter selectedChapter(JournalBook book, List<Row> rows) {
        if (selected == null) return null;
        for (Row r : rows) if (r.chapter() != null && r.chapter().id().equals(selected)) return r.chapter();
        return null;
    }

    private static int numberOf(JournalChapter ch, List<Row> rows) {
        for (Row r : rows) if (r.chapter() == ch) return r.number();
        return 1;
    }

    @Nullable
    private String defaultSelection(JournalBook book, MagicData d, List<Row> rows) {
        if (lastChapter != null) {
            for (Row r : rows) if (r.chapter() != null && r.chapter().id().equals(lastChapter)) return lastChapter;
        }
        JournalChapter active = book.active(d);
        if (active != null) return active.id();
        if (book.allDone(d)) {
            for (int i = book.chapters().size() - 1; i >= 0; i--) if (book.visible(i, d)) return book.chapters().get(i).id();
        }
        for (Row r : rows) if (r.chapter() != null) return r.chapter().id();
        return null;
    }

    private void renderList(GuiGraphics g, Font font, JournalBook book, MagicData d, List<Row> rows, int mx, int my) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.journal.header").withStyle(ChatFormatting.BOLD), x, y, JournalUi.GOLD, true);
        int done = book.doneCount(d), total = book.visibleCount(d);
        g.drawString(font, Component.translatable("screen.isekai_mahou.journal.progress", done, total), x, y + 11, JournalUi.MUTED, false);
        JournalUi.bar(g, x + 70, y + 13, LIST_W - 72, 3, total == 0 ? 0 : Math.min(1F, done / (float) total), 0xFFD86B);

        listTop = y + 24;
        listBottom = y + h - 22;
        Row lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        int contentH = lastRow == null ? 0 : lastRow.top() + lastRow.height() + 2;
        listMax = Math.max(0, contentH - (listBottom - listTop));
        listScroll = Mth.clamp(listScroll, 0, listMax);
        g.enableScissor(x, listTop, x + LIST_W, listBottom);
        long time = Util.getMillis();
        Component headerTip = null;
        for (Row row : rows) {
            int ey = listTop + row.top() - listScroll;
            if (ey + row.height() < listTop || ey > listBottom) continue;
            if (row.chapter() == null) {
                if (row.tip() != null && JournalUi.inside(mx, my, x, ey, LIST_W, HEADER_H) && my >= listTop && my < listBottom) {
                    headerTip = row.tip();
                }
                // Cabeçalho de seção: título pequeno com um filete na cor.
                int color = 0xFF000000 | row.headerColor();
                g.drawString(font, JournalUi.ellipsize(font, row.header().copy().withStyle(ChatFormatting.BOLD), LIST_W - 4), x + 1, ey + 2, color, false);
                g.fill(x, ey + HEADER_H - 2, x + LIST_W, ey + HEADER_H - 1, 0x60000000 | row.headerColor());
                continue;
            }
            JournalChapter ch = row.chapter();
            JournalBook.State state = book.stateOf(ch, d);
            boolean isSelected = ch.id().equals(selected);
            boolean locked = state == JournalBook.State.LOCKED;
            boolean hover = JournalUi.inside(mx, my, x, ey, LIST_W, ENTRY_H) && my >= listTop && my < listBottom;
            int accent = locked ? 0xFF4A4458 : 0xFF000000 | ch.color();
            int bg = isSelected ? 0x50000000 | (ch.color() & 0xFFFFFF) : hover ? 0x30FFFFFF : 0x22000000;
            g.fill(x, ey, x + LIST_W, ey + ENTRY_H, locked ? 0x22000000 : bg);
            g.fill(x, ey, x + 2, ey + ENTRY_H, accent);
            if (isSelected) g.renderOutline(x, ey, LIST_W, ENTRY_H, accent);

            if (locked) {
                g.fill(x + 6, ey + 7, x + 22, ey + 23, 0xFF231E2E);
                g.renderOutline(x + 6, ey + 7, 16, 16, 0xFF4A4458);
                g.drawCenteredString(font, "?", x + 14, ey + 11, JournalUi.FAINT);
            } else {
                g.renderItem(icon(ch, time), x + 6, ey + 7);
            }
            Component number = numberLabel(ch, row.number());
            g.drawString(font, number, x + 26, ey + 4, locked ? JournalUi.FAINT : JournalUi.MUTED, false);
            Component title = locked ? Component.translatable("screen.isekai_mahou.journal.locked_title") : ch.title();
            g.drawString(font, JournalUi.ellipsize(font, title, LIST_W - 40), x + 26, ey + 16,
                    locked ? JournalUi.FAINT : 0xFF000000 | ch.color(), false);
            String mark = switch (state) {
                case DONE -> "✔";
                case ACTIVE -> "✦";
                case LOCKED -> "▪";
            };
            int markColor = switch (state) {
                case DONE -> JournalUi.GREEN;
                case ACTIVE -> pulse(ch.color(), time);
                case LOCKED -> JournalUi.FAINT;
            };
            g.drawString(font, mark, x + LIST_W - 10, ey + 4, markColor, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, x + LIST_W + 2, listTop, listBottom, listScroll, listMax, contentH);
        // A pergunta do Ato (campanha), só com o mouse em cima do cabeçalho.
        if (headerTip != null) g.renderTooltip(font, font.split(headerTip.copy().withStyle(ChatFormatting.ITALIC), 170), mx, my);
    }

    /** "Capítulo 3" (história) ou "Rota · 1" (rota da classe). */
    private static Component numberLabel(JournalChapter ch, int number) {
        return ch.isClassRoute() ? Component.translatable("screen.isekai_mahou.journal.class_n", number)
                : Component.translatable("screen.isekai_mahou.journal.chapter_n", number);
    }

    private void renderChapter(GuiGraphics g, Font font, JournalBook book, MagicData d, JournalChapter ch, int chapterNumber,
                               int mx, int my) {
        JournalBook.State state = book.stateOf(ch, d);
        int rx = x + LIST_W + 10, rw = w - LIST_W - 10;
        int cy = y;
        Component number = ch.isClassRoute()
                ? Component.translatable("screen.isekai_mahou.journal.class_header", ch.magicPath().displayName())
                : numberLabel(ch, chapterNumber);
        g.drawString(font, number, rx, cy, JournalUi.MUTED, false);
        String stateKey = "screen.isekai_mahou.journal.state." + state.name().toLowerCase(java.util.Locale.ROOT);
        Component stateText = Component.translatable(stateKey);
        int stateColor = switch (state) {
            case DONE -> JournalUi.GREEN;
            case ACTIVE -> JournalUi.GOLD;
            case LOCKED -> JournalUi.FAINT;
        };
        g.drawString(font, stateText, rx + rw - font.width(stateText), cy, stateColor, false);
        cy += 11;

        if (state == JournalBook.State.LOCKED) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.journal.locked_title").withStyle(ChatFormatting.BOLD), rx, cy + 2, JournalUi.FAINT, true);
            JournalUi.panel(g, rx, cy + 18, rw, 60, JournalUi.BORDER_SOFT);
            JournalUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.journal.locked_text"), rx + 8, cy + 28, rw - 16, JournalUi.MUTED);
            stepsTop = Integer.MAX_VALUE;
            loreH = 0;
            return;
        }

        // Título grande
        g.pose().pushPose();
        g.pose().translate(rx, cy + 1, 0);
        g.pose().scale(1.35F, 1.35F, 1F);
        g.drawString(font, JournalUi.ellipsize(font, ch.title(), (int) (rw / 1.35F)), 0, 0, 0xFF000000 | ch.color(), true);
        g.pose().popPose();
        cy += 16;

        Player player = Minecraft.getInstance().player;
        List<JournalStep> steps = ch.visibleSteps();
        List<JournalStep> required = ch.required();
        int reqDone = (int) required.stream().filter(s -> JournalBook.stepDone(ch, s, d, player)).count();
        Component objectives = Component.translatable("screen.isekai_mahou.journal.objectives", reqDone, required.size());
        g.drawString(font, objectives, rx, cy, JournalUi.TEXT, false);
        int ow = font.width(objectives) + 6;
        JournalUi.bar(g, rx + ow, cy + 3, rw - ow, 3, required.isEmpty() ? 1F : reqDone / (float) required.size(), ch.color());
        cy += 13;

        // Objetivos
        stepsTop = cy;
        JournalStep current = state == JournalBook.State.ACTIVE ? JournalBook.currentStep(ch, d, player) : null;
        for (int i = 0; i < steps.size(); i++) {
            JournalStep s = steps.get(i);
            int sy = cy + i * STEP_H;
            boolean done = JournalBook.stepDone(ch, s, d, player);
            boolean hover = JournalUi.inside(mx, my, rx, sy - 1, rw, STEP_H);
            if (i == selectedStep) g.fill(rx - 2, sy - 2, rx + rw, sy + STEP_H - 2, 0x40000000 | (ch.color() & 0xFFFFFF));
            else if (hover) g.fill(rx - 2, sy - 2, rx + rw, sy + STEP_H - 2, 0x20FFFFFF);
            // O objetivo atual (o mesmo do rastreador) ganha uma setinha pulsando.
            boolean isCurrent = !done && s == current;
            String mark = done ? "✔" : isCurrent ? "➤" : s.optional() ? "✧" : "◇";
            int markColor = done ? JournalUi.GREEN : isCurrent ? pulse(ch.color(), Util.getMillis())
                    : s.optional() ? JournalUi.FAINT : JournalUi.GOLD;
            g.drawString(font, mark, rx, sy, markColor, false);

            int target = s.condition().target();
            int cur = Math.min(target, Math.max(0, s.condition().current(d, player)));
            Component progress = !done && target > 1 ? Component.literal(cur + "/" + target) : null;
            // Objetivo atual com lugar localizado: "⌖ 340m" no lugar da contagem.
            JournalTargets.Target located = s == current ? JournalTargets.forStep(s.uid(ch)) : null;
            if (located != null && located.status() == JournalTargets.Status.FOUND && player != null
                    && located.dimension().equals(player.level().dimension())) {
                double dx = located.pos().getX() + 0.5 - player.getX(), dz = located.pos().getZ() + 0.5 - player.getZ();
                progress = Component.translatable("screen.isekai_mahou.journal.located", (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
            }
            MutableComponent label = s.title(ch);
            if (s.optional()) label = label.copy().append(" ").append(Component.translatable("screen.isekai_mahou.journal.optional")
                    .withStyle(ChatFormatting.DARK_GRAY));
            int labelW = rw - 12 - (progress != null ? font.width(progress) + 6 : 0);
            int labelColor = done ? 0xFFB9E8B5 : s.optional() ? JournalUi.MUTED : JournalUi.TEXT;
            g.drawString(font, JournalUi.ellipsize(font, label, labelW), rx + 11, sy, labelColor, false);
            if (progress != null) g.drawString(font, progress, rx + rw - font.width(progress), sy, JournalUi.MUTED, false);
        }
        cy += steps.size() * STEP_H + 2;

        // Recompensas
        Component rewards = rewardsLine(ch.rewards(), 0);
        if (rewards != null) {
            cy += JournalUi.wrapped(g, font, rewards, rx, cy, rw, JournalUi.GOLD) + 3;
        }

        // Crônica
        loreX = rx;
        loreY = cy;
        loreW = rw;
        loreH = y + h - cy;
        if (loreH < 30) return;
        JournalUi.panel(g, loreX, loreY, loreW, loreH, JournalUi.BORDER_SOFT);
        Component header;
        MutableComponent text;
        if (selectedStep >= 0 && selectedStep < steps.size()) {
            JournalStep s = steps.get(selectedStep);
            header = s.title(ch);
            text = s.lore(ch).copy();
            // Dica e recompensa do passo logo abaixo da crônica.
            text.append("\n\n").append(Component.translatable("screen.isekai_mahou.journal.hint_label").withStyle(ChatFormatting.AQUA))
                    .append(" ").append(hint(ch, s));
            // "No caminho" (v1.0 release): o que este marco abre de novo e onde conseguir — só nos passos que têm.
            String extra = s.key(ch) + ".extra";
            if (I18n.exists(extra)) {
                text.append("\n\n").append(Component.translatable("screen.isekai_mahou.journal.extra_label").withStyle(ChatFormatting.GOLD))
                        .append(" ").append(Component.translatable(extra));
            }
            Component stepRewards = rewardsLine(s.rewards(), s.xp());
            if (stepRewards != null) text.append("\n\n").append(stepRewards.copy().withStyle(ChatFormatting.GOLD));
        } else {
            header = Component.translatable(state == JournalBook.State.DONE ? "screen.isekai_mahou.journal.chronicle_done"
                    : "screen.isekai_mahou.journal.chronicle");
            text = ch.lore().copy();
            if (state == JournalBook.State.DONE) {
                text.append("\n\n").append(ch.outro());
                if (ch.finale() && ch.epilogue() > 0) {
                    text.append("\n\n").append(Component.translatable("screen.isekai_mahou.journal.epilogue").withStyle(ChatFormatting.BOLD));
                    for (int i = 1; i <= ch.epilogue(); i++) text.append("\n").append(ch.epilogueLine(i));
                }
            }
            appendEchoes(text, ch, d);
        }
        g.drawString(font, JournalUi.ellipsize(font, header.copy().withStyle(ChatFormatting.BOLD), loreW - 12), loreX + 6, loreY + 5,
                0xFF000000 | ch.color(), false);
        g.fill(loreX + 6, loreY + 15, loreX + loreW - 6, loreY + 16, JournalUi.BORDER_SOFT);
        int textTop = loreY + 19, textBottom = loreY + loreH - 4;
        List<FormattedCharSequence> lines = font.split(text, loreW - 16);
        int contentH = lines.size() * JournalUi.LINE;
        loreMax = Math.max(0, contentH - (textBottom - textTop));
        loreScroll = Mth.clamp(loreScroll, 0, loreMax);
        g.enableScissor(loreX + 1, textTop, loreX + loreW - 1, textBottom);
        for (int i = 0; i < lines.size(); i++) {
            int ly = textTop + i * JournalUi.LINE - loreScroll;
            if (ly + JournalUi.LINE < textTop || ly > textBottom) continue;
            g.drawString(font, lines.get(i), loreX + 7, ly, JournalUi.TEXT, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, loreX + loreW - 4, textTop, textBottom, loreScroll, loreMax, contentH);
    }

    /**
     * "Ecos" do capítulo: as batidas da campanha ligadas a ele que já passaram — vistas, puladas, com a campanha
     * desligada, ou cujo marco (capítulo aberto, passo cumprido) aconteceu antes de a campanha existir. Quem pulou lê aqui.
     */
    private static void appendEchoes(MutableComponent text, JournalChapter ch, MagicData d) {
        boolean head = false;
        for (StoryBook.Beat b : StoryBook.forChapter(ch.id())) {
            boolean passed = d.hasFlag(b.seenFlag()) || switch (b.trigger()) {
                case CHAPTER_BEGIN -> d.hasFlag("journal." + b.key() + ".begun");
                case CHAPTER_DONE, STEP -> d.hasFlag("journal." + b.key());
                default -> false;
            };
            if (!passed) continue;
            if (!head) {
                head = true;
                text.append("\n\n").append(Component.translatable("screen.isekai_mahou.journal.echoes")
                        .withStyle(s -> s.withColor(StoryBook.ECHO).withBold(true)));
            }
            text.append("\n\n").append(Component.literal("✦ ").append(b.title()).withStyle(s -> s.withColor(b.rgb())));
            if (b.scene()) text.append("\n").append(b.narration().withStyle(ChatFormatting.ITALIC));
            int line = 1;
            for (StoryBook.Part part : b.parts()) {
                for (int k = 0; k < part.lines(); k++) {
                    text.append("\n").append(StoryBook.speaker(part.speaker()).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(b.line(line++));
                }
            }
        }
    }

    /** "Recompensas: 150 XP · 3x Cristal de Mana · 1 Pergaminho de Glifo (até tier 1)". {@code bonusXp}: XP extra do passo. */
    private static Component rewardsLine(JournalRewards r, int bonusXp) {
        List<Component> parts = new ArrayList<>();
        if (r.xp() + bonusXp > 0) parts.add(Component.translatable("screen.isekai_mahou.journal.reward_xp", r.xp() + bonusXp));
        for (JournalRewards.ItemReward ir : r.items()) {
            Item item = BuiltInRegistries.ITEM.getOptional(ir.id()).orElse(null);
            if (item != null && item != Items.AIR) {
                parts.add(Component.translatable("screen.isekai_mahou.journal.reward_item", ir.count(), new ItemStack(item).getHoverName()));
            }
        }
        if (r.elementEssences() > 0) parts.add(Component.translatable("screen.isekai_mahou.journal.reward_essences", r.elementEssences()));
        for (ResourceLocation g : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(g);
            if (glyph != null) parts.add(Component.translatable("message.isekai_mahou.journal.reward_glyph", glyph.displayName()));
        }
        if (r.scrolls() > 0) parts.add(Component.translatable("screen.isekai_mahou.journal.reward_scrolls", r.scrolls(), r.scrollTier()));
        if (r.legendScrolls() > 0) parts.add(Component.translatable("screen.isekai_mahou.journal.reward_legends", r.legendScrolls()));
        if (parts.isEmpty()) return null;
        MutableComponent line = Component.translatable("screen.isekai_mahou.journal.rewards").append(" ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(parts.get(i).copy().withStyle(ChatFormatting.WHITE));
        }
        return line;
    }

    private static ItemStack icon(JournalChapter ch, long time) {
        if (ch.icons().isEmpty()) return new ItemStack(Items.WRITABLE_BOOK);
        ResourceLocation id = ch.icons().get((int) ((time / 1200) % ch.icons().size()));
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.WRITABLE_BOOK);
        return new ItemStack(item);
    }

    private static int pulse(int rgb, long time) {
        float f = 0.75F + 0.25F * Mth.sin(time / 250F);
        return JournalUi.shade(rgb, f);
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        JournalBook book = JournalBook.client();
        if (book.isEmpty()) return false;
        MagicData d = ClientMagic.data();
        List<Row> rows = rows(book, d);
        if (mx >= x && mx < x + LIST_W && my >= listTop && my < listBottom) {
            int ry = (int) (my - listTop + listScroll);
            for (Row r : rows) {
                if (r.chapter() == null || ry < r.top() || ry >= r.top() + ENTRY_H) continue;
                if (!r.chapter().id().equals(selected)) {
                    selected = r.chapter().id();
                    selectedStep = -1;
                    loreScroll = 0;
                    lastChapter = selected;
                    click();
                }
                break;
            }
            return true;
        }
        JournalChapter sel = selectedChapter(book, rows);
        if (sel != null && my >= stepsTop) {
            JournalChapter ch = sel;
            int rx = x + LIST_W + 10;
            int i = (int) Math.floor((my + 2 - stepsTop) / STEP_H);
            if (mx >= rx - 2 && mx < x + w && i >= 0 && i < ch.visibleSteps().size()) {
                selectedStep = selectedStep == i ? -1 : i;
                loreScroll = 0;
                click();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= x && mx < x + LIST_W + 4 && my >= listTop && my < listBottom && listMax > 0) {
            listScroll = Mth.clamp(listScroll - (int) (sy * 16), 0, listMax);
            return true;
        }
        if (loreH > 0 && JournalUi.inside(mx, my, loreX, loreY, loreW, loreH) && loreMax > 0) {
            loreScroll = Mth.clamp(loreScroll - (int) (sy * 15), 0, loreMax);
            return true;
        }
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    /** Dica do passo na variante da classe do jogador (passos com "hint_by"); sem tradução da variante, a geral. */
    private static Component hint(JournalChapter ch, JournalStep s) {
        String key = s.hintKey(ch, ClientMagic.data().path());
        return I18n.exists(key) ? Component.translatable(key) : s.hint(ch);
    }
}
