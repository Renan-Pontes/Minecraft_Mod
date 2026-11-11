package com.renanpontes.isekaimahou.client.objectives;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Camada de HUD do rastreador de objetivos: painel translúcido no canto superior direito (abaixo do minimapa — posição
 * do {@link HudLayout#tracker()}) com cabeçalho,
 * até 6 linhas (ícone, título, detalhe, barra fina de progresso) e, quando a linha tem destino na mesma
 * dimensão, uma setinha que gira apontando para ele + a distância em blocos.
 */
public class ObjectiveHud implements LayeredDraw.Layer {
    private static final int HEADER_H = 12;
    private static final int LINE_H = 24;
    private static final int LINE_H_SHORT = 15;
    /** Não desce além disto (acima da barra de ação/hotbar). */
    private static final int BOTTOM_MARGIN = 72;
    /** Em telas estreitas a barra de magias do HUD sobe para o canto direito: margem maior. */
    private static final int BOTTOM_MARGIN_NARROW = 116;
    private static final int NARROW_WIDTH = 420;

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        if (!ObjectiveTracker.isEnabled()) return;
        List<ObjectiveLine> lines = ObjectiveTracker.lines();
        if (lines.isEmpty()) return;

        Font font = mc.font;
        float pt = dt.getGameTimeDeltaPartialTick(true);
        int gw = g.guiWidth(), gh = g.guiHeight();
        HudLayout.Rect area = HudLayout.tracker();
        int w = area.w();
        int x = area.x();
        int y = area.y();
        int top = y;
        int maxBottom = gh - (gw < NARROW_WIDTH ? BOTTOM_MARGIN_NARROW : BOTTOM_MARGIN);
        if (y + HEADER_H + LINE_H_SHORT > maxBottom) return;

        renderHeader(g, font, x, y, w);
        y += HEADER_H + 2;
        for (ObjectiveLine line : lines) {
            int h = line.detail().getString().isEmpty() ? LINE_H_SHORT : LINE_H;
            if (y + h > maxBottom) break;
            renderLine(g, font, player, line, x, y, w, h, pt);
            y += h + 2;
        }
        HudLayout.drawn(HudLayout.Region.TRACKER, new HudLayout.Rect(x, top, w, y - top));
    }

    private static void renderHeader(GuiGraphics g, Font font, int x, int y, int w) {
        g.fillGradient(x, y, x + w, y + HEADER_H, 0xC0281E3A, 0xC0140F1E);
        g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, MahouUi.alpha(MahouUi.GOLD_DIM, 0xD0));
        if (GlyphArt.exists(MahouUi.SPRITES)) MahouUi.sprite(g, x + 3, y + 2, MahouUi.STAR_U, MahouUi.STAR_V, 7, 7);
        else MahouUi.diamond(g, x + 6, y + 5, 3, MahouUi.GOLD);
        g.drawString(font, Component.translatable(ObjectiveTracker.page() == ObjectiveTracker.Page.GUILD ? "objectives.isekai_mahou.header.guild" : "objectives.isekai_mahou.header"), x + 13, y + 2, MahouUi.GOLD, true);
        Component key = Component.literal("[").append(CoreKeys.TRACKER.getTranslatedKeyMessage()).append("]");
        MahouUi.smallText(g, font, key, x + w - 3 - font.width(key) * 0.75F, y + 3, 0.75F, MahouUi.TEXT_DIM, false);
    }

    private static void renderLine(GuiGraphics g, Font font, LocalPlayer player, ObjectiveLine line, int x, int y, int w, int h, float pt) {
        int accent = 0xFF000000 | (line.rgb() & 0xFFFFFF);
        g.fillGradient(x, y, x + w, y + h, 0xA8141020, 0xA80C0A14);
        g.fill(x, y, x + 2, y + h, accent);

        int tx = x + 6;
        if (!line.icon().isEmpty()) {
            g.renderItem(line.icon(), x + 4, y + (h - 16) / 2);
            tx = x + 23;
        }
        int right = x + w - 4;

        // Lado direito: seta + distância, outra dimensão, ou contagem n/m
        Component side = null;
        boolean arrow = false;
        float yaw = 0;
        Vec3 pos = line.targetPos();
        if (pos != null) {
            boolean sameDim = line.targetDim() == null || line.targetDim().equals(player.level().dimension());
            if (sameDim) {
                Vec3 me = player.getPosition(pt);
                double dx = pos.x - me.x, dy = pos.y - me.y, dz = pos.z - me.z;
                // yaw do Minecraft: 0 = +Z, cresce virando à direita; 0 relativo = seta para cima
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                yaw = Mth.wrapDegrees(targetYaw - player.getViewYRot(pt));
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                String vertical = dy > 4 ? "↑" : dy < -4 ? "↓" : "";
                side = Component.translatable("objectives.isekai_mahou.distance", dist).append(vertical);
                arrow = true;
            } else {
                side = Component.translatable("objectives.isekai_mahou.other_dim");
            }
        } else if (line.target() > 0) {
            side = Component.literal(Math.min(line.current(), line.target()) + "/" + line.target());
        }

        int sideW = 0;
        if (side != null) {
            float s = arrow ? 1F : 0.75F;
            sideW = (int) Math.ceil(font.width(side) * s);
            int sx = right - sideW;
            MahouUi.smallText(g, font, side, sx, y + 3 + (arrow ? 0 : 1), s, arrow ? MahouUi.TEXT : MahouUi.TEXT_DIM, true);
            if (arrow) {
                drawArrow(g, sx - 7, y + 7, yaw, accent);
                sideW += 14;
            }
        }

        int titleW = right - tx - sideW - (sideW > 0 ? 3 : 0);
        g.drawString(font, MahouUi.fit(font, line.title(), titleW), tx, y + 3, MahouUi.lighten(accent, 0.35F), true);
        if (h >= LINE_H) {
            g.drawString(font, MahouUi.fit(font, line.detail(), right - tx), tx, y + 12, MahouUi.TEXT_DIM, false);
        }
        if (line.target() > 0) MahouUi.bar(g, tx, y + h - 3, right - tx, 2, line.fraction(), accent);
    }

    /** Seta de 10px girando em volta de (cx, cy); sem a textura, uma setinha desenhada em blocos. */
    private static void drawArrow(GuiGraphics g, int cx, int cy, float yaw, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw));
        if (GlyphArt.exists(MahouUi.SPRITES)) {
            float r = ((color >> 16) & 255) / 255F, gr = ((color >> 8) & 255) / 255F, b = (color & 255) / 255F;
            g.setColor(Math.min(1F, r + 0.3F), Math.min(1F, gr + 0.3F), Math.min(1F, b + 0.3F), 1F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(MahouUi.SPRITES, -5, -5, 10, 10, MahouUi.ARROW_U, MahouUi.ARROW_V, 16, 16, MahouUi.SPRITES_W, MahouUi.SPRITES_H);
            RenderSystem.disableBlend();
            g.setColor(1F, 1F, 1F, 1F);
        } else {
            int c = MahouUi.lighten(color, 0.3F);
            g.fill(-1, -4, 1, 5, c);
            g.fill(-2, -3, 2, -2, c);
            g.fill(-3, -2, 3, -1, c);
            g.fill(-4, -1, 4, 0, c);
        }
        g.pose().popPose();
    }
}
