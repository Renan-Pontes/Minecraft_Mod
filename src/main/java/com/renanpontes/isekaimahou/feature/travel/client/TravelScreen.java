package com.renanpontes.isekaimahou.feature.travel.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.api.Travel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Tela "Viagem" aberta numa Pedra de Teleporte (custo normal, sem recarga). Fecha sozinha se o jogador se afastar da
 * pedra (longe dela o servidor cobraria como viagem de menu).
 */
public class TravelScreen extends Screen {
    private static final int RUNE = 0xFF6FE3FF;
    /**
     * A sessão de pedra do servidor vale 5 min; passado isso o pedido viraria viagem de menu (×3 e recarga) sem o
     * jogador saber. Fecha um pouco antes (clicar na pedra de novo abre uma sessão nova).
     */
    private static final int MAX_OPEN_TICKS = 5 * 60 * 20 - 200;
    @Nullable
    private final BlockPos stone;
    private final TravelView view = new TravelView(true);
    private int left, top, panelW, panelH;
    private int openTicks;

    public TravelScreen(@Nullable BlockPos stone) {
        super(Component.translatable("screen.isekai_mahou.travel.title_stone"));
        this.stone = stone;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 500);
        panelH = Math.min(height - 16, 280);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        view.init(left + 8, top + 8, panelW - 16, panelH - 14, this::addRenderableWidget);
    }

    @Override
    public void tick() {
        view.tick();
        if (++openTicks > MAX_OPEN_TICKS) {
            onClose();
            return;
        }
        if (minecraft == null || minecraft.player == null || stone == null) return;
        boolean far = minecraft.player.distanceToSqr(Vec3.atCenterOf(stone)) > (Travel.STONE_REACH - 0.5) * (Travel.STONE_REACH - 0.5);
        boolean gone = !BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("travel_stone"))
                .map(b -> minecraft.player.level().getBlockState(stone).is(b)).orElse(true);
        if (far || gone) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MahouUi.frame(g, left, top, panelW, panelH, RUNE);
        // Placa do título no meio da borda de cima
        Component text = getTitle();
        int tw = Math.min(panelW - 16, font.width(text) + 22);
        int px = left + (panelW - tw) / 2, py = top - 6;
        MahouUi.roundRect(g, px, py, tw, 11, 0xFF1C2A38, 0xFF0C141C);
        MahouUi.roundOutline(g, px, py, tw, 11, RUNE);
        MahouUi.diamond(g, px + 5, py + 5, 2, RUNE);
        MahouUi.diamond(g, px + tw - 6, py + 5, 2, RUNE);
        g.drawString(font, MahouUi.fit(font, text, tw - 22), px + 11, py + 2, 0xFFD2F8FF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        view.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        return view.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (view.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (view.keyPressed(key)) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        view.removed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
