package com.renanpontes.isekaimahou.feature.ritual_witch.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchHeat;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchNetwork;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchProgress;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipe;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.BottledCurseItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.RevelationPotionItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Livro de Receitas da Bruxa: uma receita por página dupla.
 * Esquerda: nome, tipo, resultado e efeitos. Direita: ingredientes NA ORDEM, mexidas, calor e luas permitidas.
 */
public class WitchRecipeBookScreen extends Screen {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/gui/witch_recipe_book.png");
    private static final int W = 256;
    private static final int H = 170;
    // Ícones na textura (abaixo do livro)
    private static final int MOON_U = 0, MOON_V = 172, MOON_DIM_V = 184, MOON_SIZE = 11;
    private static final int FLAME_U = 100, FLAME_V = 172, FLAME_OFF_V = 184;
    private static final int SPOON_U = 112, SPOON_V = 172;
    private static final int STAR_U = 124, STAR_V = 172;

    private static final int INK = 0x3B2416;
    private static final int TITLE = 0x4A1F66;
    private static final int FADED = 0x7A6450;

    private final List<WitchNetwork.BookEntry> entries;
    private final int total;
    private int page;
    private int left, top;
    private PageButton back, forward;

    public WitchRecipeBookScreen(WitchNetwork.BookData data) {
        super(Component.translatable("item.isekai_mahou.witch_recipe_book"));
        this.entries = data.entries();
        this.total = data.total();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2 - 8);
        back = addRenderableWidget(new PageButton(left + 18, top + H - 22, false, b -> turn(-1), true));
        forward = addRenderableWidget(new PageButton(left + W - 41, top + H - 22, true, b -> turn(1), true));
        updateButtons();
    }

    private void turn(int d) {
        page = Math.max(0, Math.min(entries.size() - 1, page + d));
        updateButtons();
    }

    private void updateButtons() {
        back.visible = page > 0;
        forward.visible = page < entries.size() - 1;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        g.blit(TEXTURE, left, top, 0, 0, W, H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Component footer = Component.translatable("screen.isekai_mahou.witch_book.footer",
                entries.isEmpty() ? 0 : page + 1, Math.max(1, entries.size()), entries.size(), total);
        g.drawString(font, footer, left + (W - font.width(footer)) / 2, top + H - 19, FADED, false);

        if (entries.isEmpty()) {
            renderEmpty(g);
            return;
        }
        WitchNetwork.BookEntry e = entries.get(page);
        List<Component> tip = new ArrayList<>();
        renderLeft(g, e, mouseX, mouseY, tip);
        renderRight(g, e, mouseX, mouseY, tip);
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private void renderEmpty(GuiGraphics g) {
        int x = left + 16, y = top + 30;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.witch_book.empty"), 104)) {
            g.drawString(font, line, x, y, TITLE, false);
            y += 10;
        }
        y += 6;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.witch_book.empty_hint"), 104)) {
            g.drawString(font, line, x, y, INK, false);
            y += 10;
        }
        renderHeatGuide(g, left + 138, top + 12);
    }

    // ---------------------------------------------------------------- página esquerda
    private void renderLeft(GuiGraphics g, WitchNetwork.BookEntry e, int mx, int my, List<Component> tip) {
        int cx = left + 68;
        int y = top + 14;
        for (FormattedCharSequence line : font.split(WitchRecipe.displayName(e.id()), 106)) {
            g.drawString(font, line, cx - font.width(line) / 2, y, TITLE, false);
            y += 10;
        }
        Component kind = kindOf(e.result());
        g.drawString(font, kind, cx - font.width(kind) / 2, y, FADED, false);
        y += 13;

        // Resultado grande (2x) com a quantidade de doses
        ItemStack result = e.result();
        g.pose().pushPose();
        g.pose().translate(cx - 16, y, 0);
        g.pose().scale(2F, 2F, 1F);
        g.renderItem(result, 0, 0);
        g.pose().popPose();
        if (inside(mx, my, cx - 16, y, 32, 32)) tip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), result));
        y += 34;
        Component doses = Component.translatable("screen.isekai_mahou.witch_book.doses", Math.max(1, result.getCount()));
        g.drawString(font, doses, cx - font.width(doses) / 2, y, INK, false);
        y += 13;

        // Descrição + efeitos (as linhas de tooltip do item, sem o nome)
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component desc = WitchRecipe.description(e.id());
        if (!desc.getString().isEmpty()) lines.addAll(font.split(desc.copy().withStyle(ChatFormatting.ITALIC), 106));
        Minecraft mc = Minecraft.getInstance();
        List<Component> itemLines = result.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        for (int i = 1; i < itemLines.size(); i++) {
            String s = itemLines.get(i).getString();
            if (s.isBlank()) continue;
            lines.addAll(font.split(Component.literal(s), 106));
        }
        int maxY = top + H - 34;
        for (FormattedCharSequence line : lines) {
            if (y + 9 > maxY) break;
            g.drawString(font, line, left + 15, y, INK, false);
            y += 9;
        }

        // Lua de hoje (verde = serve para esta receita), para planejar a próxima noite
        if (minecraft != null && minecraft.level != null) {
            int today = minecraft.level.getMoonPhase();
            MutableComponent now = Component.translatable("screen.isekai_mahou.witch_book.today", moonName(today));
            String text = font.plainSubstrByWidth(now.getString(), 106);
            g.drawString(font, text, cx - font.width(text) / 2, top + H - 31, e.moonAllowed(today) ? 0x2E6B2E : 0x8A2A2A, false);
        }
    }

    // ---------------------------------------------------------------- página direita
    private void renderRight(GuiGraphics g, WitchNetwork.BookEntry e, int mx, int my, List<Component> tip) {
        int x = left + 138;
        int y = top + 12;
        g.drawString(font, Component.translatable("screen.isekai_mahou.witch_book.ingredients"), x, y, TITLE, false);
        y += 12;
        int n = e.ingredients().size();
        int rowH = n <= 6 ? 16 : 12;
        for (int i = 0; i < n; i++) {
            Item item = BuiltInRegistries.ITEM.getOptional(e.ingredients().get(i)).orElse(null);
            String num = (i + 1) + ".";
            g.drawString(font, num, x, y + (rowH - 8) / 2, FADED, false);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                int ix = x + 11, iy = y + (rowH - 16) / 2;
                g.renderItem(stack, ix, iy);
                String name = font.plainSubstrByWidth(stack.getHoverName().getString(), 84);
                g.drawString(font, name, x + 29, y + (rowH - 8) / 2, INK, false);
                if (inside(mx, my, ix, y, 100, rowH)) tip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            } else {
                g.drawString(font, Component.literal("?").withStyle(ChatFormatting.DARK_RED), x + 14, y + (rowH - 8) / 2, INK, false);
            }
            y += rowH;
        }

        // Mexidas e calor numa linha
        y = Math.max(y + 3, top + 118);
        g.blit(TEXTURE, x, y, SPOON_U, SPOON_V, 11, 11);
        Component stirs = Component.translatable("screen.isekai_mahou.witch_book.stirs", e.stirs());
        g.drawString(font, stirs, x + 13, y + 2, INK, false);
        if (inside(mx, my, x, y, 13 + font.width(stirs), 11)) {
            tip.add(Component.translatable("screen.isekai_mahou.witch_book.stirs_tip"));
        }
        WitchHeat heat = WitchHeat.byOrdinal(e.heat());
        int hx = x + 70;
        for (int i = 1; i <= 3; i++) g.blit(TEXTURE, hx + (i - 1) * 9, y, FLAME_U, i <= heat.ordinal() ? FLAME_V : FLAME_OFF_V, 9, 11);
        if (inside(mx, my, hx, y, 27, 11)) {
            tip.add(Component.translatable("screen.isekai_mahou.witch_book.heat", heat.displayName()));
            tip.add(Component.translatable(heat.hintKey).withStyle(ChatFormatting.GRAY));
        }

        // Luas permitidas (0 = cheia ... 7)
        y += 15;
        for (int phase = 0; phase < 8; phase++) {
            boolean ok = e.moonAllowed(phase);
            int ix = x + phase * 12;
            g.blit(TEXTURE, ix, y, MOON_U + phase * 12, ok ? MOON_V : MOON_DIM_V, MOON_SIZE, MOON_SIZE);
            if (inside(mx, my, ix, y, MOON_SIZE, MOON_SIZE)) {
                tip.add(moonName(phase));
                tip.add(Component.translatable(ok ? "screen.isekai_mahou.witch_book.moon_ok" : "screen.isekai_mahou.witch_book.moon_no")
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        if (e.night()) {
            int sx = x + 97;
            g.blit(TEXTURE, sx, y, STAR_U, STAR_V, 11, 11);
            if (inside(mx, my, sx, y, 11, 11)) tip.add(Component.translatable("screen.isekai_mahou.witch_book.night"));
        }
    }

    /** Guia de calor (aparece no livro vazio). */
    private void renderHeatGuide(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.witch_book.heat_guide"), x, y, TITLE, false);
        y += 14;
        for (WitchHeat h : new WitchHeat[] {WitchHeat.LOW, WitchHeat.MEDIUM, WitchHeat.HIGH}) {
            for (int i = 1; i <= 3; i++) g.blit(TEXTURE, x + (i - 1) * 9, y, FLAME_U, i <= h.ordinal() ? FLAME_V : FLAME_OFF_V, 9, 11);
            y += 13;
            for (FormattedCharSequence line : font.split(Component.translatable(h.hintKey), 104)) {
                g.drawString(font, line, x, y, INK, false);
                y += 9;
            }
            y += 4;
        }
    }

    private static Component kindOf(ItemStack result) {
        int tier = result.getItem() instanceof RevelationPotionItem r ? r.tier() : 0;
        if (tier > 0) return Component.translatable("screen.isekai_mahou.witch_book.kind_revelation", WitchProgress.roman(tier));
        if (result.getItem() instanceof BottledCurseItem) return Component.translatable("screen.isekai_mahou.witch_book.kind_curse");
        return Component.translatable("screen.isekai_mahou.witch_book.kind_elixir");
    }

    /** Nome da fase da lua (0 = cheia ... 4 = nova ... 7). */
    private static MutableComponent moonName(int phase) {
        String key = "witch_moon.isekai_mahou." + (phase & 7);
        return Component.translatable(key);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
