package com.renanpontes.isekaimahou.feature.domain.client;

import com.renanpontes.isekaimahou.feature.domain.DomainNet;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Soco na parede do domo (cliente): o clique de ataque que não acertou uma criatura, mirando a casca de um domo
 * fechado mais perto que qualquer bloco, manda um pacote ao servidor — que confere distância, mira e recarga e aplica
 * a regra dos lados (de fora quebra uma barreira normal; de dentro, só a invertida).
 */
public final class DomainBarrierInput {
    private static final double REACH = 4.8;
    private static long lastSent;

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) return;
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) return;
        long now = mc.level.getGameTime();
        if (now - lastSent < 4 && now >= lastSent) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double blockDist = hit != null && hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : Double.MAX_VALUE;
        DomainDomeEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof DomainDomeEntity d) || !d.isEnclosing() || d.state() != DomainDomeEntity.ACTIVE) continue;
            double t = rayToWall(d, eye, look, REACH);
            if (t < 0 || t >= blockDist || t >= bestT) continue;
            best = d;
            bestT = t;
        }
        if (best == null) return;
        lastSent = now;
        PacketDistributor.sendToServer(new DomainNet.BarrierPunch(best.getId()));
    }

    private static double rayToWall(DomainDomeEntity d, Vec3 eye, Vec3 dir, double max) {
        Vec3 f = eye.subtract(d.position());
        double r = d.radius();
        double b = f.dot(dir);
        double c = f.lengthSqr() - r * r;
        double disc = b * b - c;
        if (disc < 0) return -1;
        double s = Math.sqrt(disc);
        double t = c < 0 ? -b + s : -b - s;
        return t > 0 && t <= max ? t : -1;
    }

    private DomainBarrierInput() {}
}
