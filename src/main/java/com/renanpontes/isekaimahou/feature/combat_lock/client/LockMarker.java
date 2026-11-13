package com.renanpontes.isekaimahou.feature.combat_lock.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Marcador discreto do alvo travado: uma setinha clara (um "V") flutuando logo acima da cabeça dele, sempre virada
 * para a câmera, um pouco maior de longe para não sumir. Nada na tela (HUD) — só no mundo.
 */
final class LockMarker {
    private static final int COLOR = 0xE6FFE9A8, EDGE = 0xB02A2418;
    private static MultiBufferSource.BufferSource buffers;

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        LivingEntity t = LockOn.target();
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.level == null || mc.options.hideGui) return;
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 pos = t.getPosition(partial);
        float time = (mc.level.getGameTime() % 2400) + partial;
        double x = pos.x - camPos.x;
        double y = pos.y + t.getBbHeight() + 0.38 + Mth.sin(time * 0.18F) * 0.04 - camPos.y;
        double z = pos.z - camPos.z;
        float dist = (float) Math.sqrt(x * x + y * y + z * z);
        float s = 0.1F * (1F + Mth.clamp((dist - 5F) / 16F, 0F, 1.8F));

        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(512));
        PoseStack ps = new PoseStack();
        ps.translate(x, y, z);
        ps.mulPose(cam.rotation());
        ps.scale(s, s, s);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        // Contorno escuro (um pouco maior, atrás) e o "V" claro por cima
        chevron(vc, m, 1.22F, -0.14F, -0.002F, EDGE);
        chevron(vc, m, 1.0F, 0F, 0F, COLOR);
        buffers.endBatch();
    }

    /** "V" com a ponta em (0, y0): dois braços de 1 de altura, 0,95 de abertura e 0,42 de espessura (vezes {@code k}). */
    private static void chevron(VertexConsumer vc, Matrix4f m, float k, float y0, float z, int argb) {
        float w = 0.95F * k, h = 1.0F * k, th = 0.42F * k;
        for (int side = -1; side <= 1; side += 2) {
            vc.addVertex(m, 0F, y0, z).setColor(argb);
            vc.addVertex(m, side * w, y0 + h, z).setColor(argb);
            vc.addVertex(m, side * (w - th), y0 + h, z).setColor(argb);
            vc.addVertex(m, 0F, y0 + th * 0.9F, z).setColor(argb);
        }
    }

    private LockMarker() {}
}
