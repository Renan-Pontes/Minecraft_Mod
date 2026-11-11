package com.renanpontes.isekaimahou.feature.dimensions.client;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.dimensions.Convergence;
import com.renanpontes.isekaimahou.feature.dimensions.DimPayloads;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela do Ritual de Convergência. Os seis elementos base ficam num hexágono; escolha dois em que você é
 * Mestre. A lista à direita mostra o resultado e o custo (a mesma lista que o servidor confere).
 */
public class ConvergenceScreen extends Screen {
    private static final Element[] ORDER = {Element.FIRE, Element.WATER, Element.EARTH, Element.WIND, Element.LIGHT, Element.SHADOW};
    private static final int PANEL_W = 360, PANEL_H = 210;
    private static final int GEM = 22;
    private static final int RADIUS = 58;

    private final BlockPos altar;
    private Element first, second;
    private Button convergeButton;
    private int left, top, cx, cy;

    public ConvergenceScreen(BlockPos altar) {
        super(Component.translatable("screen.isekai_mahou.convergence.title"));
        this.altar = altar;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        cx = left + 92;
        cy = top + 112;
        convergeButton = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.convergence.perform"), b -> perform())
                .bounds(left + 196, top + PANEL_H - 30, 150, 20).build());
        convergeButton.active = false;
    }

    private void perform() {
        if (first == null || second == null) return;
        PacketDistributor.sendToServer(new DimPayloads.Converge(altar, first.id, second.id));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int gemX(int i) {
        return cx + Math.round(Mth.cos((-90 + i * 60) * Mth.DEG_TO_RAD) * RADIUS);
    }

    private int gemY(int i) {
        return cy + Math.round(Mth.sin((-90 + i * 60) * Mth.DEG_TO_RAD) * RADIUS);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < ORDER.length; i++) {
                if (Math.abs(mx - gemX(i)) <= GEM / 2.0 && Math.abs(my - gemY(i)) <= GEM / 2.0) {
                    toggle(ORDER[i]);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void toggle(Element e) {
        if (e == first) { first = second; second = null; }
        else if (e == second) second = null;
        else if (first == null) first = e;
        else if (second == null) second = e;
        else { first = second; second = e; }
    }

    /** Painel desenhado no fundo, para o botão ficar por cima dele. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0100C1C);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF6C5A9E);
        g.renderOutline(left + 2, top + 2, PANEL_W - 4, PANEL_H - 4, 0x553A2E5C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        MagicData data = ClientMagic.data();
        float time = (player.tickCount + partialTick);

        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), left + PANEL_W / 2, top + 8, 0xFFE8DDFF);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.convergence.hint"), cx, top + 24, 0xFF9D93B8);
        g.fill(left + 186, top + 22, left + 187, top + PANEL_H - 8, 0x553A2E5C);

        Element fusion = Convergence.fusion(first, second);
        // Linhas de energia dos escolhidos até o centro
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == first || ORDER[i] == second) line(g, gemX(i), gemY(i), cx, cy, 0xC0000000 | ORDER[i].color);
        }
        // Centro: o resultado da fusão pulsando
        int pulse = (int) (3 + 2 * Mth.sin(time * 0.15F));
        int core = fusion != null ? fusion.color : 0x3A2E5C;
        g.fill(cx - 8 - pulse, cy - 8 - pulse, cx + 8 + pulse, cy + 8 + pulse, 0x40000000 | core);
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFF000000 | core);
        g.renderOutline(cx - 8, cy - 8, 16, 16, 0xFFFFFFFF);

        // Gemas
        for (int i = 0; i < ORDER.length; i++) {
            Element e = ORDER[i];
            int x = gemX(i), y = gemY(i);
            boolean master = data.hasNode(e, Convergence.MASTER_NODE);
            boolean selected = e == first || e == second;
            boolean hover = Math.abs(mouseX - x) <= GEM / 2 && Math.abs(mouseY - y) <= GEM / 2;
            int color = master ? e.color : darken(e.color);
            if (selected) g.fill(x - GEM / 2 - 3, y - GEM / 2 - 3, x + GEM / 2 + 3, y + GEM / 2 + 3, 0x80000000 | e.color);
            g.fill(x - GEM / 2, y - GEM / 2, x + GEM / 2, y + GEM / 2, 0xFF000000 | darken(color));
            g.fill(x - GEM / 2 + 2, y - GEM / 2 + 2, x + GEM / 2 - 2, y + GEM / 2 - 2, 0xFF000000 | color);
            g.fill(x - GEM / 2 + 3, y - GEM / 2 + 3, x - 1, y - 1, 0x60FFFFFF);
            g.renderOutline(x - GEM / 2, y - GEM / 2, GEM, GEM, selected ? 0xFFFFFFFF : hover ? 0xFFCFC4F0 : 0xFF2A2140);
            Component name = e.displayName();
            int ny = i == 0 ? y - GEM / 2 - 10 : i == 3 ? y + GEM / 2 + 3 : y + GEM / 2 + 2;
            g.drawCenteredString(font, name, x, ny, 0xFFFFFFFF);
            if (!master) g.drawCenteredString(font, Component.literal("✖"), x, y - 4, 0xFF5A5070);
        }

        // Coluna da direita: resultado e requisitos
        int rx = left + 196, ry = top + 26;
        if (fusion == null) {
            for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.convergence.choose"), 150)) {
                g.drawString(font, line, rx, ry, 0xFFB8AED6, false);
                ry += 10;
            }
            convergeButton.active = false;
            return;
        }
        g.drawString(font, Component.translatable("screen.isekai_mahou.convergence.result"), rx, ry, 0xFF9D93B8, false);
        ry += 11;
        g.drawString(font, fusion.displayName().withStyle(ChatFormatting.BOLD), rx, ry, 0xFFFFFFFF, true);
        ry += 14;
        List<Convergence.Req> reqs = Convergence.requirements(player, data, first, second);
        boolean all = true;
        for (Convergence.Req r : reqs) {
            all &= r.met();
            Component mark = Component.literal(r.met() ? "✔ " : "✘ ").withStyle(r.met() ? ChatFormatting.GREEN : ChatFormatting.RED);
            for (FormattedCharSequence line : font.split(Component.empty().append(mark).append(r.label()), 152)) {
                if (ry > top + PANEL_H - 40) break;
                g.drawString(font, line, rx, ry, r.met() ? 0xFFD8F5D0 : 0xFFF2C4C4, false);
                ry += 10;
            }
        }
        convergeButton.active = all;
    }

    private static int darken(int rgb) {
        int r = ((rgb >> 16) & 255) * 2 / 5, g = ((rgb >> 8) & 255) * 2 / 5, b = (rgb & 255) * 2 / 5;
        return (r << 16) | (g << 8) | b;
    }

    /** Linha grossa feita de pequenos quadrados (o GuiGraphics só desenha retângulos alinhados). */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s += 2) {
            int x = x0 + (x1 - x0) * s / Math.max(1, steps);
            int y = y0 + (y1 - y0) * s / Math.max(1, steps);
            g.fill(x - 1, y - 1, x + 1, y + 1, argb);
        }
    }
}
