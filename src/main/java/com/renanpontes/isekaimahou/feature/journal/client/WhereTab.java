package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;
import com.renanpontes.isekaimahou.feature.journal.guide.GuideTopics;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Seção "Onde Conseguir" do Guia de Viagem (menu B): à esquerda o índice ({@link GuideTopics}), à direita ONDE fica e
 * COMO se consegue o assunto escolhido. A primeira linha, "Seu próximo passo", junta o objetivo atual da história e da
 * rota da classe com a dica de onde resolver cada um — o Diário responde no caminho, sem dica em cada item.
 */
public class WhereTab implements MenuTab {
    public static final String TAB_ID = "where";
    private static final int LIST_W = 132;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 13;
    /** Lembra o assunto entre aberturas do menu (null = "Seu próximo passo"). */
    @Nullable
    private static String lastTopic;

    private record Row(@Nullable GuideTopics.Group group, @Nullable GuideTopics.Topic topic, int top, int height) {}

    private int x, y, w, h;
    private final List<Row> rows = new ArrayList<>();
    private int contentH;
    private int listScroll, listMax;
    private int textScroll, textMax;
    private int listTop, listBottom;
    private int textX, textY, textW, textH;
    @Nullable
    private String selected = lastTopic;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rows.clear();
        int top = 0;
        // Linha fixa do topo: o próximo passo (sem grupo e sem assunto).
        rows.add(new Row(null, null, top, ROW_H));
        top += ROW_H + 2;
        for (GuideTopics.Group g : GuideTopics.groups()) {
            List<GuideTopics.Topic> topics = GuideTopics.in(g);
            if (topics.isEmpty()) continue;
            rows.add(new Row(g, null, top, HEADER_H));
            top += HEADER_H;
            for (GuideTopics.Topic t : topics) {
                rows.add(new Row(g, t, top, ROW_H));
                top += ROW_H + 1;
            }
            top += 2;
        }
        contentH = top;
    }

    @Nullable
    private GuideTopics.Topic selectedTopic() {
        if (selected == null) return null;
        for (Row r : rows) if (r.topic() != null && r.topic().id().equals(selected)) return r.topic();
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        g.drawString(font, Component.translatable("screen.isekai_mahou.where.header").withStyle(ChatFormatting.BOLD), x, y, JournalUi.GOLD, true);
        g.drawString(font, JournalUi.ellipsize(font, Component.translatable("screen.isekai_mahou.where.sub"), LIST_W), x, y + 11, JournalUi.MUTED, false);

        listTop = y + 24;
        listBottom = y + h;
        listMax = Math.max(0, contentH - (listBottom - listTop));
        listScroll = Mth.clamp(listScroll, 0, listMax);
        g.enableScissor(x, listTop, x + LIST_W, listBottom);
        for (Row row : rows) {
            int ry = listTop + row.top() - listScroll;
            if (ry + row.height() < listTop || ry > listBottom) continue;
            if (row.topic() == null && row.group() != null) {
                int color = 0xFF000000 | row.group().color();
                g.drawString(font, JournalUi.ellipsize(font, row.group().title().withStyle(ChatFormatting.BOLD), LIST_W - 4), x + 1, ry + 2, color, false);
                g.fill(x, ry + HEADER_H - 2, x + LIST_W, ry + HEADER_H - 1, 0x60000000 | row.group().color());
                continue;
            }
            GuideTopics.Topic t = row.topic();
            boolean isSel = t == null ? selected == null : t.id().equals(selected);
            boolean hover = JournalUi.inside(mx, my, x, ry, LIST_W, ROW_H) && my >= listTop && my < listBottom;
            int rgb = t == null ? 0xFFD86B : row.group().color();
            boolean early = t != null && d.level() < t.minLevel();
            g.fill(x, ry, x + LIST_W, ry + ROW_H, isSel ? 0x50000000 | rgb : hover ? 0x30FFFFFF : 0x22000000);
            g.fill(x, ry, x + 2, ry + ROW_H, early ? 0xFF4A4458 : 0xFF000000 | rgb);
            if (isSel) g.renderOutline(x, ry, LIST_W, ROW_H, 0xFF000000 | rgb);
            g.renderItem(t == null ? new ItemStack(Items.COMPASS) : icon(t), x + 4, ry + 1);
            Component title = t == null ? Component.translatable("screen.isekai_mahou.where.next") : t.title();
            int tw = LIST_W - 26;
            if (early) {
                Component lv = Component.translatable("screen.isekai_mahou.where.level", t.minLevel());
                g.drawString(font, lv, x + LIST_W - 3 - font.width(lv), ry + 5, JournalUi.FAINT, false);
                tw -= font.width(lv) + 4;
            }
            g.drawString(font, JournalUi.ellipsize(font, title, tw), x + 23, ry + 5, early ? JournalUi.FAINT : JournalUi.TEXT, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, x + LIST_W + 2, listTop, listBottom, listScroll, listMax, contentH);

        renderDetail(g, font, d);
    }

    private void renderDetail(GuiGraphics g, Font font, MagicData d) {
        int rx = x + LIST_W + 10, rw = w - LIST_W - 10;
        GuideTopics.Topic topic = selectedTopic();
        Component title;
        int rgb;
        MutableComponent text;
        if (topic == null) {
            title = Component.translatable("screen.isekai_mahou.where.next");
            rgb = 0xFFD86B;
            text = nextStep(d);
        } else {
            title = topic.title();
            rgb = 0xFFD86B;
            for (GuideTopics.Group gr : GuideTopics.groups()) if (gr.id().equals(topic.group())) rgb = gr.color();
            text = Component.empty();
            if (d.level() < topic.minLevel()) {
                text.append(Component.translatable("screen.isekai_mahou.where.from_level", topic.minLevel()).withStyle(ChatFormatting.GRAY)).append("\n\n");
            }
            text.append(Component.translatable("screen.isekai_mahou.where.where").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append("\n").append(topic.where()).append("\n\n")
                    .append(Component.translatable("screen.isekai_mahou.where.how").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append("\n").append(topic.how());
        }
        g.pose().pushPose();
        g.pose().translate(rx, y + 1, 0);
        g.pose().scale(1.35F, 1.35F, 1F);
        g.drawString(font, JournalUi.ellipsize(font, title, (int) (rw / 1.35F)), 0, 0, 0xFF000000 | rgb, true);
        g.pose().popPose();

        textX = rx;
        textY = y + 18;
        textW = rw;
        textH = y + h - textY;
        JournalUi.panel(g, textX, textY, textW, textH, JournalUi.BORDER_SOFT);
        int top = textY + 6, bottom = textY + textH - 4;
        List<FormattedCharSequence> lines = font.split(text, textW - 16);
        int height = lines.size() * JournalUi.LINE;
        textMax = Math.max(0, height - (bottom - top));
        textScroll = Mth.clamp(textScroll, 0, textMax);
        g.enableScissor(textX + 1, top, textX + textW - 1, bottom);
        for (int i = 0; i < lines.size(); i++) {
            int ly = top + i * JournalUi.LINE - textScroll;
            if (ly + JournalUi.LINE < top || ly > bottom) continue;
            g.drawString(font, lines.get(i), textX + 7, ly, JournalUi.TEXT, false);
        }
        g.disableScissor();
        JournalUi.scrollbar(g, textX + textW - 4, top, bottom, textScroll, textMax, height);
    }

    /** "Seu próximo passo": o objetivo atual da história e o da rota da classe, cada um com a dica de onde resolver. */
    private static MutableComponent nextStep(MagicData d) {
        JournalBook book = JournalBook.client();
        Player player = Minecraft.getInstance().player;
        MutableComponent out = Component.empty();
        boolean any = false;
        JournalChapter main = book.active(d);
        if (main != null && d.hasFlag(main.begunFlag())) {
            any |= stepBlock(out, main, JournalBook.currentStep(main, d, player), d,
                    Component.translatable("screen.isekai_mahou.journal.section.story"));
        }
        // Depois do Dragão: o Abismo Infinito anda ao lado de algum capítulo antigo que tenha ficado por fazer.
        JournalChapter post = book.epilogueActive(d);
        if (post != null && post != main && d.hasFlag(post.begunFlag())) {
            if (any) out.append("\n\n");
            any |= stepBlock(out, post, JournalBook.currentStep(post, d, player), d, post.title());
        }
        JournalChapter cls = book.classActive(d);
        if (cls != null && d.hasFlag(cls.begunFlag())) {
            if (any) out.append("\n\n");
            any |= stepBlock(out, cls, JournalBook.currentStep(cls, d, player), d,
                    Component.translatable("screen.isekai_mahou.journal.section.class", cls.magicPath().displayName()));
        }
        if (!any) out.append(Component.translatable("screen.isekai_mahou.where.next_none").withStyle(ChatFormatting.GRAY));
        out.append("\n\n").append(Component.translatable("screen.isekai_mahou.where.next_tip").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private static boolean stepBlock(MutableComponent out, JournalChapter ch, @Nullable JournalStep step, MagicData d, Component section) {
        if (step == null) return false;
        out.append(section.copy().withStyle(s -> s.withColor(ch.color()).withBold(true))).append("\n")
                .append(Component.literal("➤ ").append(step.title(ch)).withStyle(ChatFormatting.WHITE)).append("\n");
        String key = step.hintKey(ch, d.path());
        out.append(I18n.exists(key) ? Component.translatable(key) : step.hint(ch));
        String extra = step.key(ch) + ".extra";
        if (I18n.exists(extra)) {
            out.append("\n").append(Component.translatable("screen.isekai_mahou.journal.extra_label").withStyle(ChatFormatting.GOLD))
                    .append(" ").append(Component.translatable(extra));
        }
        return true;
    }

    private static ItemStack icon(GuideTopics.Topic t) {
        Item item = BuiltInRegistries.ITEM.getOptional(t.icon()).orElse(null);
        return item == null || item == Items.AIR ? new ItemStack(Items.WRITABLE_BOOK) : new ItemStack(item);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || mx < x || mx >= x + LIST_W || my < listTop || my >= listBottom) return false;
        int ry = (int) (my - listTop + listScroll);
        for (Row r : rows) {
            if (ry < r.top() || ry >= r.top() + r.height()) continue;
            if (r.topic() == null && r.group() != null) break;
            String id = r.topic() == null ? null : r.topic().id();
            if (id == null ? selected != null : !id.equals(selected)) {
                selected = id;
                lastTopic = id;
                textScroll = 0;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
            break;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= x && mx < x + LIST_W + 4 && my >= listTop && my < listBottom && listMax > 0) {
            listScroll = Mth.clamp(listScroll - (int) (sy * 16), 0, listMax);
            return true;
        }
        if (textH > 0 && JournalUi.inside(mx, my, textX, textY, textW, textH) && textMax > 0) {
            textScroll = Mth.clamp(textScroll - (int) (sy * 15), 0, textMax);
            return true;
        }
        return false;
    }
}
