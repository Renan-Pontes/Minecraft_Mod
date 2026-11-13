package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.earth_wind.EwNetwork;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Tremor de tela (Tremor, Fenda, pancada do Golem...): balanca a camera e diminui ate parar. */
public final class CameraShake {
    private static int ticksLeft;
    private static int totalTicks;
    private static float intensity;

    public static void start(EwNetwork.Shake p) {
        float current = totalTicks > 0 ? intensity * ticksLeft / (float) totalTicks : 0;
        if (p.intensity() < current) return;
        intensity = Math.min(3.0F, p.intensity());
        totalTicks = Math.max(1, p.ticks());
        ticksLeft = totalTicks;
    }

    public static void onClientTick(ClientTickEvent.Post e) {
        if (ticksLeft > 0) ticksLeft--;
        else totalTicks = 0;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (ticksLeft <= 0 || totalTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float pt = (float) e.getPartialTick();
        float fade = Math.max(0, (ticksLeft - pt) / totalTicks);
        float k = intensity * fade * (!com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 0.5F : 1.0F) * MahouConfig.shakeScale();
        double t = mc.level.getGameTime() + pt;
        e.setPitch(e.getPitch() + (float) (Math.sin(t * 2.3) * k));
        e.setYaw(e.getYaw() + (float) (Math.cos(t * 1.9) * k * 0.8));
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 3.1) * k * 0.6));
    }

    private CameraShake() {}
}
