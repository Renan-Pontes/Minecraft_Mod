package com.renanpontes.isekaimahou.client.boss;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.boss.BossPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Tremor de câmera dos chefes. Vários tremores somam (com teto); cada um some com o tempo e com a distância da
 * câmera até o centro. Sempre ativo (não depende das cutscenes), suave: balanço de baixa frequência, metade com
 * "partículas reduzidas".
 */
public final class BossCameraShake {
    private static final float MAX_TOTAL = 3.0F;
    private static final int MAX_ACTIVE = 16;

    private record Active(Vec3 pos, float radius, float intensity, int ticks, long start) {}

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static long ticks;

    public static void receive(BossPayloads.Shake p) {
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(new Active(new Vec3(p.x(), p.y(), p.z()), Math.max(1, p.radius()), p.intensity(), Math.max(1, p.ticks()), ticks));
    }

    static void clear() {
        ACTIVE.clear();
    }

    static void onClientTick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().isPaused()) return;
        ticks++;
        if (!ACTIVE.isEmpty()) ACTIVE.removeIf(a -> ticks - a.start > a.ticks);
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        double now = ticks + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        float k = 0;
        for (Active a : ACTIVE) {
            double age = now - a.start;
            if (age < 0 || age > a.ticks) continue;
            double fade = 1 - age / a.ticks;
            fade *= fade * Math.min(1, age / 2 + 0.4);
            double fall = Mth.clamp(1 - cam.distanceTo(a.pos) / a.radius, 0, 1);
            fall = fall * fall * (3 - 2 * fall);
            k += (float) (a.intensity * fade * fall);
        }
        if (k < 0.01F) return;
        k = Math.min(k, MAX_TOTAL);
        if (MahouConfig.REDUCED_PARTICLES.get()) k *= 0.5F;
        // Soma de senos de frequências "quebradas": balança orgânico, sem tremedeira dura
        double t = now;
        float pitch = (float) ((Math.sin(t * 1.7) * 0.6 + Math.sin(t * 3.1 + 1.3) * 0.4) * k);
        float yaw = (float) ((Math.sin(t * 1.3 + 0.7) * 0.6 + Math.sin(t * 2.9 + 2.1) * 0.4) * k * 0.8);
        float roll = (float) (Math.sin(t * 2.3 + 0.4) * k * 0.5);
        e.setPitch(e.getPitch() + pitch);
        e.setYaw(e.getYaw() + yaw);
        e.setRoll(e.getRoll() + roll);
    }

    private BossCameraShake() {}
}
