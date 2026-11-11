package com.renanpontes.isekaimahou.feature.bosses_b.client;

import com.renanpontes.isekaimahou.feature.bosses_b.entity.AbyssSovereign;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.TwilightHerald;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Atmosfera das lutas: durante o Eclipse do Soberano a névoa fica preta-violeta e curta; dentro do domínio próprio
 * do Arauto a névoa ganha o tom do crepúsculo. Transições suaves (por tick, interpoladas no frame).
 */
public final class BbAtmosphere {
    private static float eclipse;
    private static float eclipseO;
    private static float twilight;
    private static float twilightO;

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        eclipseO = eclipse;
        twilightO = twilight;
        float wantE = 0F;
        float wantT = 0F;
        if (mc.level != null && mc.player != null) {
            if (!mc.level.getEntitiesOfClass(AbyssSovereign.class, mc.player.getBoundingBox().inflate(48),
                    s -> s.isAlive() && s.eclipseActive()).isEmpty()) {
                wantE = 1F;
            }
            for (TwilightHerald h : mc.level.getEntitiesOfClass(TwilightHerald.class, mc.player.getBoundingBox().inflate(48),
                    TwilightHerald::domainActive)) {
                double r = h.domainRadius() + 2;
                if (mc.player.distanceToSqr(h.domainCenter()) < r * r) wantT = 1F;
            }
        }
        eclipse = approach(eclipse, wantE, 0.04F);
        twilight = approach(twilight, wantT, 0.06F);
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    static void fogColor(ViewportEvent.ComputeFogColor e) {
        float pt = (float) e.getPartialTick();
        float ec = Mth.lerp(pt, eclipseO, eclipse);
        float tw = Mth.lerp(pt, twilightO, twilight);
        if (ec <= 0F && tw <= 0F) return;
        float r = e.getRed();
        float g = e.getGreen();
        float b = e.getBlue();
        if (tw > 0F) {
            r = Mth.lerp(tw * 0.7F, r, 0.55F);
            g = Mth.lerp(tw * 0.7F, g, 0.30F);
            b = Mth.lerp(tw * 0.7F, b, 0.50F);
        }
        if (ec > 0F) {
            r = Mth.lerp(ec, r, 0.05F);
            g = Mth.lerp(ec, g, 0.01F);
            b = Mth.lerp(ec, b, 0.09F);
        }
        e.setRed(r);
        e.setGreen(g);
        e.setBlue(b);
    }

    static void fog(ViewportEvent.RenderFog e) {
        float pt = (float) e.getPartialTick();
        float ec = Mth.lerp(pt, eclipseO, eclipse);
        float tw = Mth.lerp(pt, twilightO, twilight);
        if (ec <= 0F && tw <= 0F) return;
        float far = e.getFarPlaneDistance();
        float f = Mth.lerp(tw, far, Math.min(far, 56F));
        f = Mth.lerp(ec, f, Math.min(f, 20F));
        e.setFarPlaneDistance(f);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), f * 0.1F));
        e.setCanceled(true);
    }

    private BbAtmosphere() {}
}
