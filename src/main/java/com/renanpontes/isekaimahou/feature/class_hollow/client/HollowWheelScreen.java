package com.renanpontes.isekaimahou.feature.class_hollow.client;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * SÓ CLIENTE (v1.4). A RODA HOLLOW: as técnicas que não ficam na tecla "Instinto Hollow" (Pesquisa, Regeneração,
 * Hierro, Garganta, Negación, Descorrer, Resurrección). Segurar a tecla, mirar com o mouse e soltar usa; clique,
 * Enter ou 1–7 também. Bloqueadas (rank) aparecem apagadas com o estágio em que chegam; em recarga, com os segundos.
 */
public class HollowWheelScreen extends Screen {
    private static final HollowTech[] TECHS = HollowTech.wheelTechs();
    private static final int N = TECHS.length;
    private static final int RADIUS = 82;

    private int selected = -1;
    private final long opened = Util.getMillis();
    private boolean mouseMoved;
    private double lastMouseX = -1, lastMouseY = -1;

    HollowWheelScreen() {
        super(Component.translatable("screen.isekai_mahou.hollow.wheel"));
    }

    private static boolean released() {
        Minecraft mc = Minecraft.getInstance();
        HollowLooks.Look l = mc.player == null ? null : HollowLooks.of(mc.player);
        return l != null && l.released();
    }

    private static boolean unlocked(HollowTech t) {
        MagicData d = ClientMagic.data();
        return d != null && t.unlocked(d, released());
    }

    private void select(int i) {
        int next = Math.floorMod(i, N);
        if (next != selected) {
            selected = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
        }
    }

    private void confirm() {
        if (selected < 0) {
            onClose();
            return;
        }
        HollowTech t = TECHS[selected];
        onClose();
        HollowInput.send(HollowNetwork.TECH_WHEEL, t.ordinal());
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (lastMouseX >= 0 && (Math.abs(mx - lastMouseX) > 0.5 || Math.abs(my - lastMouseY) > 0.5)) mouseMoved = true;
        lastMouseX = mx;
        lastMouseY = my;
        if (!mouseMoved) return;
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 18 * 18) return;
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

    /** Segurar a tecla, mirar e soltar = usa. */
    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (HollowInput.WHEEL.matches(key, scan) && mouseMoved && selected >= 0 && Util.getMillis() - opened > 150) {
            confirm();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x40100004, 0x80000000);
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

        // anel rubro (o buraco do Hollow)
        int accent = selected >= 0 ? 0xFF000000 | TECHS[selected].rgb : 0xFF000000 | Hollow.REIATSU_RGB;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            int x = cx + (int) Math.round(Math.cos(a) * (RADIUS - 30)), y = cy + (int) Math.round(Math.sin(a) * (RADIUS - 30));
            g.fill(x - 1, y - 1, x + 1, y + 1, MahouUi.alpha(0xFFD0142C, 0x90));
        }

        for (int i = 0; i < N; i++) drawOption(g, font, i, cx, cy);

        // centro: a técnica escolhida, o que faz e o custo
        Component title = selected >= 0 ? TECHS[selected].displayName() : this.title;
        int tw = font.width(title);
        MahouUi.roundRect(g, cx - tw / 2 - 6, cy - 8, tw + 12, 16, 0xE0200608, 0xE00C0204);
        MahouUi.roundOutline(g, cx - tw / 2 - 6, cy - 8, tw + 12, 16, MahouUi.alpha(accent | 0x202020, 0xD0));
        g.drawString(font, title, cx - tw / 2, cy - 4, 0xFFFFFFFF, true);
        g.pose().popPose();

        if (selected >= 0) {
            HollowTech t = TECHS[selected];
            int boxW = Math.min(300, width - 20);
            int y = cy + RADIUS + 22;
            for (FormattedCharSequence line : font.split(t.description(), boxW)) {
                g.drawCenteredString(font, line, cx, y, 0xFFE8DCD4);
                y += 10;
                if (y > height - 40) break;
            }
            if (!unlocked(t)) {
                g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.hollow.wheel_locked", Hollow.rankTitle(t.rank))
                        .withStyle(ChatFormatting.GRAY), cx, y + 2, 0xFFA09090);
            }
        }
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.hollow.wheel_tip"), cx, height - 22, 0xB0E8E2F4);
    }

    private void drawOption(GuiGraphics g, Font font, int i, int cx, int cy) {
        HollowTech t = TECHS[i];
        boolean sel = i == selected;
        boolean ok = unlocked(t);
        double a = i * Math.PI * 2 / N;
        int px = cx + (int) Math.round(Math.sin(a) * RADIUS), py = cy - (int) Math.round(Math.cos(a) * RADIUS);
        int s = sel ? 30 : 26;
        int x = px - s / 2, y = py - s / 2;
        int color = 0xFF000000 | t.rgb;
        if (sel) g.fill(x - 3, y - 3, x + s + 3, y + s + 3, MahouUi.alpha(color | 0x303030, 0x50));
        MahouUi.roundRect(g, x - 1, y - 1, s + 2, s + 2, 0xE0201014, 0xE00A0406);
        g.blit(t.icon(), x, y, s, s, 0, 0, 32, 32, 32, 32);
        if (!ok) g.fill(x, y, x + s, y + s, 0xB0000000);
        float cd = HollowInput.cooldown(t);
        if (ok && cd > 0) {
            int h = Math.round(s * cd);
            g.fill(x, y + s - h, x + s, y + s, 0x90000000);
            String sec = String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(HollowInput.cooldownSeconds(t)));
            g.drawCenteredString(font, sec, px, py - 4, 0xFFFFFFFF);
        }
        MahouUi.roundOutline(g, x - 1, y - 1, s + 2, s + 2, sel ? 0xFFFFFFFF : ok ? MahouUi.alpha(color | 0x404040, 0xC0) : 0xFF3A3036);
        Component name = t.displayName();
        int nw = font.width(name);
        int ny = py + s / 2 + 3;
        g.drawString(font, name, px - nw / 2, ny, sel ? 0xFFFFFFFF : ok ? 0xFFE6DFCF : 0xFF7A7070, true);
        g.drawString(font, String.valueOf(i + 1), x + 2, y + 1, 0xC0FFFFFF, true);
    }
}
