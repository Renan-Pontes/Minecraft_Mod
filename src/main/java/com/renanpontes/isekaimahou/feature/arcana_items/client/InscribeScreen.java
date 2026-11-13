package com.renanpontes.isekaimahou.feature.arcana_items.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaNet;
import com.renanpontes.isekaimahou.feature.arcana_items.RecipeScrolls;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela do Pergaminho de Receita em Branco: lista as magias da biblioteca do Grimório (8 por página). Cada botão mostra
 * o custo em níveis; clicar grava (o servidor confere tudo de novo e cobra).
 */
public class InscribeScreen extends Screen {
    private static final int BTN_W = 230, BTN_H = 20, GAP = 3, PER_PAGE = 8;
    private static final int ACCENT = 0xFFC9A0FF;

    private final InteractionHand hand;
    private int page;

    public InscribeScreen(InteractionHand hand) {
        super(Component.translatable("screen.isekai_mahou.arcana_items.inscribe.title"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new InscribeScreen(hand));
    }

    private static List<SpellRecipe> library() {
        return ClientMagic.data().library();
    }

    private int rows() {
        return Math.max(1, Math.min(PER_PAGE, library().size() - page * PER_PAGE));
    }

    private int panelH() {
        return 52 + (rows() + 1) * (BTN_H + GAP) + 6;
    }

    @Override
    protected void init() {
        List<SpellRecipe> lib = library();
        MagicData d = ClientMagic.data();
        int pages = Math.max(1, (lib.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean free = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        int levels = minecraft == null || minecraft.player == null ? 0 : minecraft.player.experienceLevel;
        int manaCost = RecipeScrolls.manaCost(d);

        int x = (width - BTN_W) / 2;
        int y = (height - panelH()) / 2 + 46;
        for (int i = page * PER_PAGE; i < Math.min(lib.size(), (page + 1) * PER_PAGE); i++) {
            SpellRecipe r = lib.get(i);
            int index = i;
            int cost = RecipeScrolls.levelCost(r);
            Component name = r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.arcana_items.inscribe.unnamed")
                    : Component.literal(r.name());
            Component label = Component.translatable("screen.isekai_mahou.arcana_items.inscribe.row", name, r.glyphs().size(), cost);
            Button b = Button.builder(label, btn -> choose(index)).bounds(x, y, BTN_W, BTN_H).build();
            boolean knows = RecipeScrolls.missing(d, r).isEmpty();
            boolean canPay = free || levels >= cost && d.mana() >= manaCost;
            b.active = !r.isEmpty() && knows && canPay;
            if (!knows) b.setTooltip(Tooltip.create(Component.translatable("message.isekai_mahou.arcana_items.scroll_author_missing")));
            else if (!canPay) b.setTooltip(Tooltip.create(Component.translatable("screen.isekai_mahou.arcana_items.inscribe.cant_pay", cost, manaCost)));
            addRenderableWidget(b);
            y += BTN_H + GAP;
        }
        if (lib.isEmpty()) y += BTN_H + GAP;
        y += 3;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(x, y, 24, BTN_H).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(x + BTN_W - 24, y, 24, BTN_H).build()).active = page < pages - 1;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 65, y, BTN_W - 130, BTN_H).build());
    }

    private void turn(int by) {
        page += by;
        rebuildWidgets();
    }

    private void choose(int index) {
        PacketDistributor.sendToServer(new ArcanaNet.Inscribe(index, hand == InteractionHand.OFF_HAND));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int w = BTN_W + 24, h = panelH();
        int x = (width - w) / 2, y = (height - h) / 2;
        MahouUi.frame(g, x, y, w, h, ACCENT);
        g.drawCenteredString(font, title, width / 2, y + 10, MahouUi.GOLD);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.arcana_items.inscribe.cost",
                RecipeScrolls.manaCost(ClientMagic.data())), width / 2, y + 23, MahouUi.TEXT_DIM);
        if (library().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.arcana_items.inscribe.empty"), width / 2, y + 50,
                    MahouUi.TEXT_DIM);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
