package com.renanpontes.isekaimahou.feature.combat_lock.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.combat_lock.LockConfig;
import com.renanpontes.isekaimahou.feature.combat_lock.LockPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * O aviso "!!!" na tela: curto (menos de 1 s), pequeno, no máximo dois ao mesmo tempo. Se o monstro está à vista, o
 * "!!!" aparece sobre a cabeça dele; se está fora da tela ou atrás, na BORDA da tela do lado de onde ele vem (atrás =
 * embaixo). Vermelho = golpe forte; amarelo = pelas costas.
 */
public final class ThreatHud {
    private static final int LIFE = 18, MAX = 2;
    private static final int HEAVY_RGB = 0xFF5A3C, BEHIND_RGB = 0xFFC83C;
    private static final Component TEXT = Component.translatable("hud.isekai_mahou.combat_lock.warn").withStyle(ChatFormatting.BOLD);

    private record Warn(int entityId, byte kind, long start) {}

    private static final List<Warn> ACTIVE = new ArrayList<>();
    private static long ticks;

    /** Pacote do servidor. */
    public static void receive(LockPayloads.Warn p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !LockConfig.on(LockConfig.SHOW_WARNINGS)) return;
        ACTIVE.removeIf(w -> w.entityId == p.entityId());
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Warn(p.entityId(), p.kind(), ticks));
        if (LockConfig.on(LockConfig.WARNING_SOUND)) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, p.kind() == LockPayloads.WARN_HEAVY ? 1.5F : 1.9F);
        }
    }

    static void tick() {
        ticks++;
        if (!ACTIVE.isEmpty()) ACTIVE.removeIf(w -> ticks - w.start >= LIFE);
    }

    static void clear() {
        ACTIVE.clear();
    }

    static void render(GuiGraphics g, DeltaTracker dt) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float partial = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2F, cy = h / 2F;
        Camera cam = mc.gameRenderer.getMainCamera();
        double tanV = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5));
        double tanH = tanV * w / Math.max(1, h);
        int textW = mc.font.width(TEXT);

        for (Warn warn : ACTIVE) {
            float t = (ticks - warn.start + partial) / LIFE;
            if (t < 0F || t >= 1F) continue;
            // Entra com um "pulo" rápido e some no fim
            float alpha = t < 0.12F ? t / 0.12F : t > 0.6F ? 1F - (t - 0.6F) / 0.4F : 1F;
            float pop = t < 0.15F ? Mth.lerp(t / 0.15F, 1.5F, 1F) : 1F;
            float x = cx, y = h - 70F, scale = 1.5F;
            Entity ent = warn.entityId >= 0 ? mc.level.getEntity(warn.entityId) : null;
            if (ent != null) {
                Vec3 rel = ent.getPosition(partial).add(0, ent.getBbHeight() + 0.6, 0).subtract(cam.getPosition());
                Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
                double f = rel.x * look.x() + rel.y * look.y() + rel.z * look.z();
                double u = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
                double r = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
                if (f > 0.1 && Math.abs(r / f) < tanH * 0.9 && Math.abs(u / f) < tanV * 0.82) {
                    // À vista: em cima da cabeça do monstro
                    x = cx + (float) (r / f / tanH) * cx;
                    y = cy - (float) (u / f / tanV) * cy;
                    scale = 1.25F;
                } else {
                    // Fora da tela: na borda, do lado de onde ele vem (frente = cima, atrás = baixo)
                    double ang = Math.atan2(r, f);
                    x = cx + (float) Math.sin(ang) * (cx - 30F);
                    y = cy - (float) Math.cos(ang) * (cy - 70F);
                }
            }
            int a = Mth.clamp((int) (alpha * 255F), 10, 255);
            int color = (a << 24) | (warn.kind == LockPayloads.WARN_HEAVY ? HEAVY_RGB : BEHIND_RGB);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(x, y, 0F);
            ps.scale(scale * pop, scale * pop, 1F);
            g.drawString(mc.font, TEXT, -textW / 2, -4, color, true);
            ps.popPose();
        }
    }

    private ThreatHud() {}
}
