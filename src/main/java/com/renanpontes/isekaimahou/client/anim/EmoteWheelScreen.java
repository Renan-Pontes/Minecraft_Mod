package com.renanpontes.isekaimahou.client.anim;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Roda de emotes (tecla "Emotes", sem tecla por padrão): 8 opções em círculo — acenar, reverência, provocar, sentar,
 * comemorar, apontar, cruzar braços, dançar. Escolha pela direção do mouse, scroll ou teclas 1–8; clique/Enter (ou
 * soltar a tecla depois de mirar) confirma, Esc/botão direito fecha. Toca na hora e o servidor repassa a quem está vendo.
 */
public class EmoteWheelScreen extends Screen {
    static final KeyMapping KEY = new KeyMapping("key.isekai_mahou.emotes", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, CoreKeys.CATEGORY);

    private static final List<String> EMOTES = MahouAnims.EMOTES;
    private static final int N = EMOTES.size();
    private static final int RADIUS = 74;
    private static final int[] COLORS = {0xFF8FD3FF, 0xFFFFD86B, 0xFFFF7A6B, 0xFF9BE07A, 0xFFFFB36B, 0xFFB98CFF, 0xFFB0B8C8, 0xFFFF8FD8};

    private int selected = -1;
    private final long opened = Util.getMillis();
    private boolean mouseMoved;
    private double lastMouseX = -1, lastMouseY = -1;

    EmoteWheelScreen() {
        super(Component.translatable("screen.isekai_mahou.emotes.title"));
    }

    private static Component label(int i) {
        return Component.translatable("screen.isekai_mahou.emote." + EMOTES.get(i));
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private void select(int i) {
        int next = Math.floorMod(i, N);
        if (next != selected) {
            selected = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.25F));
        }
    }

    private void confirm() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (selected < 0 || p == null) {
            onClose();
            return;
        }
        String id = EMOTES.get(selected);
        onClose();
        if (!AnimConfig.EMOTES.get()) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.anim.emotes_off"), true);
            return;
        }
        if (!MahouAnimsClient.play(p, id)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.anim.emote_busy"), true);
            return;
        }
        PacketDistributor.sendToServer(new AnimPayloads.Melee(id));
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (lastMouseX >= 0 && (Math.abs(mx - lastMouseX) > 0.5 || Math.abs(my - lastMouseY) > 0.5)) mouseMoved = true;
        lastMouseX = mx;
        lastMouseY = my;
        if (!mouseMoved) return;
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 16 * 16) return;
        // 0 = em cima, sentido horário
        double ang = Math.atan2(dx, -dy);
        select((int) Math.round(ang / (Math.PI * 2 / N)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) select(Math.max(selected, 0) + (sy > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            mouseMoved(mx, my);
            confirm();
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            confirm();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + N) {
            select(key - GLFW.GLFW_KEY_1);
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Segurar a tecla, mirar com o mouse e soltar = escolhe (como uma roda de jogo de luta). */
    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (KEY.matches(key, scan) && mouseMoved && selected >= 0 && Util.getMillis() - opened > 150) {
            confirm();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x30000000, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = this.font;
        int cx = width / 2, cy = height / 2;
        float open = Mth.clamp((Util.getMillis() - opened) / 140F, 0F, 1F);
        float ease = 1F - (1F - open) * (1F - open);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.7F + 0.3F * ease, 0.7F + 0.3F * ease, 1F);
        g.pose().translate(-cx, -cy, 0);

        int accent = selected >= 0 ? COLORS[selected] : MahouUi.GOLD;
        ring(g, cx, cy, RADIUS - 26, MahouUi.alpha(accent, 0x50));
        for (int i = 0; i < N; i++) {
            int[] p = pos(i, cx, cy, RADIUS - 38);
            MahouUi.diamond(g, p[0], p[1], i == selected ? 3 : 2, i == selected ? COLORS[i] : 0x80FFFFFF);
        }

        // Centro: título
        Component title = selected >= 0 ? label(selected) : this.title;
        int tw = font.width(title);
        MahouUi.roundRect(g, cx - tw / 2 - 6, cy - 8, tw + 12, 16, 0xE0201830, 0xE0100C18);
        MahouUi.roundOutline(g, cx - tw / 2 - 6, cy - 8, tw + 12, 16, MahouUi.alpha(accent, 0xD0));
        g.drawString(font, title, cx - tw / 2, cy - 4, selected >= 0 ? 0xFFFFFFFF : MahouUi.GOLD_PALE, true);

        for (int i = 0; i < N; i++) drawOption(g, font, i, cx, cy);
        g.pose().popPose();

        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.emotes.tip"), cx, height - 30, 0xB0E8E2F4);
    }

    private void drawOption(GuiGraphics g, Font font, int i, int cx, int cy) {
        boolean sel = i == selected;
        int color = COLORS[i];
        Component text = label(i);
        int[] p = pos(i, cx, cy, RADIUS);
        int w = Math.max(56, font.width(text) + 18), h = 18;
        int x = p[0] - w / 2, y = p[1] - h / 2;
        int top = sel ? MahouUi.alpha(MahouUi.darken(color, 0.55F), 0xF0) : 0xE0201830;
        int bottom = sel ? MahouUi.alpha(MahouUi.darken(color, 0.8F), 0xF0) : 0xE00E0B16;
        if (sel) g.fill(x - 2, y - 2, x + w + 2, y + h + 2, MahouUi.alpha(color, 0x40));
        MahouUi.roundRect(g, x, y, w, h, top, bottom);
        MahouUi.roundOutline(g, x, y, w, h, sel ? MahouUi.lighten(color, 0.3F) : MahouUi.alpha(color, 0x90));
        g.drawString(font, text, x + (w - font.width(text)) / 2 + 3, y + 5, sel ? 0xFFFFFFFF : MahouUi.TEXT, sel);
        MahouUi.smallText(g, font, Component.literal(String.valueOf(i + 1)), x + 3, y + 2, 0.6F, MahouUi.alpha(color, 0xC0), false);
    }

    /** Posição da opção {@code i} (0 = em cima, sentido horário). */
    private static int[] pos(int i, int cx, int cy, int r) {
        double a = i * Math.PI * 2 / N;
        return new int[] {cx + (int) Math.round(Math.sin(a) * r), cy - (int) Math.round(Math.cos(a) * r)};
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int n = 56;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
