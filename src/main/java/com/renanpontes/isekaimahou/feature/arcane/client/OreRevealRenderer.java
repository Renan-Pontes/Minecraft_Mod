package com.renanpontes.isekaimahou.feature.arcane.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.feature.arcane.network.OreRevealPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Glifo Detectar (cliente): desenha o contorno colorido dos minérios encontrados ATRAVÉS das paredes
 * (sem teste de profundidade), piscando de leve e sumindo no fim da duração.
 */
public final class OreRevealRenderer {
    private record Ore(BlockPos pos, int color) {}

    private static final List<Ore> ORES = new ArrayList<>();
    private static long until;
    private static int total = 1;
    /** Dimensão onde os minérios foram achados (trocar de dimensão apaga o destaque). */
    private static ResourceKey<Level> dimension;

    /** Chamado pelo handler do pacote (thread principal do cliente). */
    public static void receive(OreRevealPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ORES.clear();
        for (int i = 0; i < p.positions().size() && i < p.colors().size(); i++) {
            ORES.add(new Ore(p.positions().get(i), p.colors().get(i)));
        }
        total = Math.max(1, p.ticks());
        until = mc.level.getGameTime() + total;
        dimension = mc.level.dimension();
        // Faísca rápida em cada minério na hora da detecção
        for (Ore o : ORES) {
            int c = o.color;
            var dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.8F);
            mc.level.addParticle(dust, o.pos.getX() + 0.5, o.pos.getY() + 0.5, o.pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ORES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != dimension) {
            ORES.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double left = until - mc.level.getGameTime() - partial;
        if (left <= 0) {
            ORES.clear();
            return;
        }
        // Some suavemente no último terço e "pulsa" enquanto ativo
        float fade = (float) Math.min(1.0, left / (total / 3.0));
        float pulse = 0.75F + 0.25F * (float) Math.sin((mc.level.getGameTime() + partial) * 0.25);
        float alpha = Math.max(0.05F, fade * pulse);

        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2.5F);

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        PoseStack pose = new PoseStack();
        for (Ore o : ORES) {
            AABB box = new AABB(o.pos).move(-cam.x, -cam.y, -cam.z).inflate(0.002);
            int c = o.color;
            LevelRenderer.renderLineBox(pose, buf, box, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, alpha);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ORES.clear();
    }

    private OreRevealRenderer() {}
}
