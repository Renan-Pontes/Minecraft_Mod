package com.renanpontes.isekaimahou.feature.fusions_c.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Feixes das Misturas C:
 * <ul>
 *   <li>Canhão Elétrico: núcleo branco ofuscante, halo ciano e ANÉIS de choque ao longo do disparo, que somem rápido;</li>
 *   <li>Jato de Vapor: cone de vapor branco que se abre e "rola" para a frente;</li>
 *   <li>Erupção Solar: feixe de plasma dourado em camadas com uma espiral, e um sol pequeno no ponto de impacto;</li>
 *   <li>Raio de Purga: fio de luz dourada.</li>
 * </ul>
 */
public class FusionBeamRenderer extends EntityRenderer<FusionBeamEntity> {
    public FusionBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(FusionBeamEntity e, Frustum frustum, double x, double y, double z) {
        // A caixa da entidade é pequena; o feixe vai longe: testa a caixa do feixe inteiro
        Vec3 a = e.position();
        return frustum.isVisible(new AABB(a, a.add(e.endOffset())).inflate(2));
    }

    @Override
    public void render(FusionBeamEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 d = e.endOffset();
        float len = (float) d.length();
        if (len < 0.05F) return;
        float t = e.tickCount + pt;
        float w = e.scale();
        float open = e.openness(pt, 4);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize());
        ps.pushPose();
        ps.mulPose(q);
        switch (e.kind()) {
            case RAILGUN -> railgun(e, ps, buffers, len, w, t, pt, q);
            case STEAM_JET -> steam(ps, buffers, len, w, t, open);
            case SOLAR_FLARE -> solar(ps, buffers, len, w, t, open, q);
            case PURGE_RAY -> {
                float fade = Mth.clamp((e.lifeLeft() - pt) / 8F, 0F, 1F);
                FcQuads.beam(ps, FcQuads.additive(buffers, FcQuads.BEAM), w, len, -t * 0.1F, 2F, 0xFFE9A8, 0.95F * fade);
                FcQuads.beam(ps, FcQuads.additive(buffers, FcQuads.BEAM), w * 0.4F, len, -t * 0.2F, 1F, 0xFFFFFF, fade);
            }
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** Desenha um quad de câmera num ponto do referencial do feixe (desfaz a rotação do feixe antes). */
    private void billboardAt(PoseStack ps, Quaternionf q, float x, float y, float z, VertexConsumer vc, float half, float rot, int rgb, float alpha) {
        Vector3f v = q.transform(new Vector3f(x, y, z));
        ps.pushPose();
        ps.mulPose(new Quaternionf(q).conjugate());
        ps.translate(v.x, v.y, v.z);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        FcQuads.billboard(ps, vc, half, rot, rgb, alpha);
        ps.popPose();
    }

    private void railgun(FusionBeamEntity e, PoseStack ps, MultiBufferSource buffers, float len, float w, float t, float pt, Quaternionf q) {
        float life = (e.lifeLeft() - pt) / e.maxLife();
        float fade = Mth.clamp(life, 0F, 1F);
        float thin = fade * fade;
        VertexConsumer add = FcQuads.additive(buffers, FcQuads.BEAM);
        FcQuads.beam(ps, add, w * 2.6F * (0.4F + 0.6F * fade), len, -t * 0.3F, 6F, 0x3FA9FF, 0.55F * fade);
        FcQuads.beam(ps, add, w * 1.2F * (0.3F + 0.7F * thin), len, -t * 0.5F, 4F, 0x7FDBFF, 0.9F * fade);
        FcQuads.beam(ps, add, w * 0.45F * thin + 0.03F, len, -t * 0.8F, 3F, 0xFFFFFF, fade);
        // Anéis de choque espaçados ao longo do disparo (abrem enquanto o feixe some)
        VertexConsumer ring = FcQuads.additive(buffers, FcQuads.RING);
        float open = 1F - fade;
        for (float y = 1.5F; y < len; y += 4F) {
            ps.pushPose();
            ps.translate(0, y, 0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 9 + y * 13));
            float rr = w * (1.5F + 3.5F * open) * (1F - 0.4F * y / len);
            FcQuads.ring(ps, ring, rr * 0.7F, rr, 0xBFF0FF, 0.8F * fade, 20);
            ps.popPose();
        }
        // Clarão na boca do canhão
        billboardAt(ps, q, 0, 0, 0, FcQuads.additive(buffers, FcQuads.GLOW), 2.4F * fade, t * 4, 0xBFF0FF, fade);
    }

    private static void steam(PoseStack ps, MultiBufferSource buffers, float len, float w, float t, float open) {
        VertexConsumer vc = FcQuads.glow(buffers, FcQuads.NOISE);
        // Cone de vapor: estreito na boca e largo no fim, a textura rolando para a frente
        FcQuads.tube(ps, vc, 0.2F, w * 1.4F, 0, len, -t * 0.12F, 2F, len / 3F, 0xF4F8FA, 0.75F * open, 0.15F * open, 16);
        FcQuads.tube(ps, vc, 0.1F, w * 0.8F, 0, len * 0.9F, -t * 0.2F, 1F, len / 2F, 0xFFFFFF, 0.85F * open, 0.25F * open, 12);
        FcQuads.beam(ps, FcQuads.additive(buffers, FcQuads.BEAM), w * 0.3F, len * 0.7F, -t * 0.3F, 2F, 0xD8F0FF, 0.5F * open);
    }

    private void solar(PoseStack ps, MultiBufferSource buffers, float len, float w, float t, float open, Quaternionf q) {
        float pulse = 1F + 0.12F * Mth.sin(t * 0.8F);
        VertexConsumer add = FcQuads.additive(buffers, FcQuads.BEAM);
        FcQuads.beam(ps, add, w * 2.2F * pulse * open, len, -t * 0.25F, 5F, 0xFF6A10, 0.6F);
        FcQuads.beam(ps, add, w * 1.2F * pulse * open, len, -t * 0.4F, 3F, 0xFFB300, 0.85F);
        FcQuads.beam(ps, add, w * 0.45F * open, len, -t * 0.7F, 2F, 0xFFF4C8, 1F);
        // Espiral de plasma em volta do feixe
        VertexConsumer glow = FcQuads.additive(buffers, FcQuads.GLOW);
        for (float y = 0.5F; y < len; y += 0.7F) {
            float a = y * 0.9F - t * 0.5F;
            billboardAt(ps, q, Mth.cos(a) * w * 1.6F, y, Mth.sin(a) * w * 1.6F, glow, 0.35F * open, t * 6, 0xFFC040, 0.7F);
        }
        // Pequeno sol no ponto de impacto
        billboardAt(ps, q, 0, len, 0, glow, (1.4F + 0.3F * Mth.sin(t)) * open, t * 3, 0xFFD060, 1F);
        billboardAt(ps, q, 0, len, 0, FcQuads.additive(buffers, FcQuads.RUNE), 2.2F * open, -t * 4, 0xFF8A20, 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(FusionBeamEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
