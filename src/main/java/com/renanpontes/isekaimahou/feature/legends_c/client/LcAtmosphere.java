package com.renanpontes.isekaimahou.feature.legends_c.client;

import com.renanpontes.isekaimahou.feature.legends_c.entity.MalevolentShrineEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.VoidFieldEntity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Céu e névoa de quem está DENTRO de um domínio das Técnicas Malditas (cliente):
 * Santuário Maligno = céu VERMELHO e névoa vermelha (+ vinheta vermelha); Vazio Infinito = céu negro-violeta e névoa
 * escura (o cosmos é desenhado pelo {@link VoidFieldRenderer}). Entra e sai suavemente. Ouvintes com prioridade baixa
 * (pintam por cima da tinta genérica do domínio).
 */
public final class LcAtmosphere {
    private static float shrine, shrineO, voidK, voidO;
    private static float shrineRadius = 20F, voidRadius = 20F;

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        shrineO = shrine;
        voidO = voidK;
        if (mc.level == null || mc.player == null) {
            shrine = voidK = 0F;
            MalevolentShrineEntity.CLIENT_LIVE.clear();
            VoidFieldEntity.CLIENT_LIVE.clear();
            return;
        }
        Vec3 p = mc.player.getEyePosition();
        float targetS = 0F;
        MalevolentShrineEntity.CLIENT_LIVE.removeIf(s -> s.isRemoved() || s.level() != mc.level);
        for (MalevolentShrineEntity s : MalevolentShrineEntity.CLIENT_LIVE) {
            Vec3 c = s.domainCenter();
            if (c.lengthSqr() < 1.0E-6) c = s.position();
            double r = s.radius() + 4.0;
            if (p.distanceToSqr(c) <= r * r) {
                float pr = s.presence(0F);
                if (pr > targetS) {
                    targetS = pr;
                    shrineRadius = s.radius();
                }
            }
        }
        float targetV = 0F;
        VoidFieldEntity.CLIENT_LIVE.removeIf(v -> v.isRemoved() || v.level() != mc.level);
        for (VoidFieldEntity v : VoidFieldEntity.CLIENT_LIVE) {
            double r = v.radius();
            if (p.distanceToSqr(v.position()) <= r * r) {
                float pr = v.presence(0F);
                if (pr > targetV) {
                    targetV = pr;
                    voidRadius = v.radius();
                }
            }
        }
        shrine = approach(shrine, targetS, 0.06F);
        voidK = approach(voidK, targetV, 0.08F);
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    static void fogColor(ViewportEvent.ComputeFogColor e) {
        float pt = (float) e.getPartialTick();
        float s = Mth.lerp(pt, shrineO, shrine), v = Mth.lerp(pt, voidO, voidK);
        if (s <= 0.001F && v <= 0.001F) return;
        float r = e.getRed(), g = e.getGreen(), b = e.getBlue();
        if (s > 0F) {
            var lvl = Minecraft.getInstance().level;
            float pulse = lvl == null ? 1F : 0.9F + 0.1F * Mth.sin((lvl.getGameTime() + pt) * 0.08F);
            r = Mth.lerp(s, r, 0.62F * pulse);
            g = Mth.lerp(s, g, 0.03F);
            b = Mth.lerp(s, b, 0.05F);
        }
        if (v > 0F) {
            r = Mth.lerp(v, r, 0.03F);
            g = Mth.lerp(v, g, 0.01F);
            b = Mth.lerp(v, b, 0.08F);
        }
        e.setRed(r);
        e.setGreen(g);
        e.setBlue(b);
    }

    static void renderFog(ViewportEvent.RenderFog e) {
        float pt = (float) e.getPartialTick();
        float s = Mth.lerp(pt, shrineO, shrine), v = Mth.lerp(pt, voidO, voidK);
        float k = Math.max(s, v);
        if (k <= 0.001F || e.getType() != FogType.NONE) return;
        if (e.getMode() == FogRenderer.FogMode.FOG_SKY) {
            // O céu inteiro vira a cor da névoa (vermelho / negro)
            e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), 0F));
            e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), 2F));
        } else {
            float radius = s >= v ? shrineRadius : voidRadius;
            float far = Math.min(e.getFarPlaneDistance(), radius * (s >= v ? 1.9F : 2.6F) + 16F);
            float near = s >= v ? far * 0.12F : far * 0.4F;
            e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), far));
            e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), near));
        }
        e.setCanceled(true);
    }

    /** Vinheta vermelha nas bordas da tela dentro do Santuário. */
    static void vignette(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        DeltaTracker dt = e.getPartialTick();
        float s = Mth.lerp(dt.getGameTimeDeltaPartialTick(false), shrineO, shrine);
        if (s < 0.02F || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int a = Mth.clamp((int) (s * 120), 0, 255);
        int w = g.guiWidth(), h = g.guiHeight(), band = h / 3;
        int red = 0x8B0A1A;
        g.fillGradient(0, 0, w, band, (a << 24) | red, red);
        g.fillGradient(0, h - band, w, h, red, (a << 24) | red);
    }

    private LcAtmosphere() {}
}
