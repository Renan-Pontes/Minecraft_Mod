package com.renanpontes.isekaimahou.feature.fusions_c.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandTsunamiEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Tsunami de Areia: uma ONDA de areia de verdade (textura de areia do jogo) — parede que se curva para a frente com uma
 * crista que quebra, costas em rampa, ondulações correndo pela largura e uma faixa de poeira rolando na crista.
 * Referencial local: +Z = para onde a onda corre.
 */
public class SandTsunamiRenderer extends EntityRenderer<SandTsunamiEntity> {
    public SandTsunamiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(SandTsunamiEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(e.scale() * 0.5 + 2, 6, e.scale() * 0.5 + 2));
    }

    @Override
    public void render(SandTsunamiEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float w = e.scale();
        float h = e.waveHeight(pt);
        float k = h / SandTsunamiEntity.HEIGHT;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        // Perfil (y, z) da frente com a crista curvada e o lábio caindo, depois as costas em rampa até o chão
        List<float[]> front = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            float s = i / 8F;
            front.add(new float[] {s * h, 1.8F * k * s * s});
        }
        front.add(new float[] {h * 0.97F, 1.8F * k + 0.9F * k});
        front.add(new float[] {h * 0.82F, 1.8F * k + 1.5F * k});
        List<float[]> back = new ArrayList<>();
        back.add(new float[] {h, 1.8F * k});
        back.add(new float[] {h * 0.7F, -1.6F * k});
        back.add(new float[] {h * 0.3F, -3.2F * k});
        back.add(new float[] {0, -4.2F * k});
        TextureAtlasSprite sand = FcQuads.sprite("sand");
        TextureAtlasSprite stone = FcQuads.sprite("sandstone");
        VertexConsumer vc = FcQuads.blocks(buffers);
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 5), LightTexture.sky(light));
        sheet(ps, vc, sand, w, front, t, 0xFFF0D0, lit, true);
        sheet(ps, vc, stone, w, back, t, 0xD8C090, lit, false);
        // Poeira rolando na crista
        ps.pushPose();
        ps.translate(0, h * 0.9F, 1.8F * k + 0.6F * k);
        VertexConsumer dust = FcQuads.glow(buffers, FcQuads.NOISE);
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(side * 35F));
            FcQuads.flat(ps, dust, w * 0.55F, 0, 0xF2DFA8, 0.55F * k);
            ps.popPose();
        }
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /**
     * Folha de areia na largura {@code w} seguindo o perfil (y, z); colunas de ~1 bloco (cada célula usa o sprite) e
     * ondulações que correm pela largura ({@code ripple}).
     */
    private static void sheet(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float w, List<float[]> prof, float t,
                              int rgb, int lit, boolean ripple) {
        PoseStack.Pose p = ps.last();
        int cols = Math.max(2, Mth.ceil(w));
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        for (int c = 0; c < cols; c++) {
            float xa = -w / 2 + w * c / cols, xb = -w / 2 + w * (c + 1) / cols;
            float wa = ripple ? 0.35F * Mth.sin(xa * 0.9F + t * 0.35F) : 0F;
            float wb = ripple ? 0.35F * Mth.sin(xb * 0.9F + t * 0.35F) : 0F;
            // As pontas da onda são mais baixas (forma de crista)
            float ea = 1F - 0.5F * (float) Math.pow(Math.abs(xa) / (w / 2), 4), eb = 1F - 0.5F * (float) Math.pow(Math.abs(xb) / (w / 2), 4);
            for (int i = 0; i < prof.size() - 1; i++) {
                float[] a = prof.get(i), b = prof.get(i + 1);
                FcQuads.litVertex(vc, p, xa, a[0] * ea, a[1] + wa * a[0] / 5F, u0, v1, rgb, 255, lit, 0, 0.3F, 1);
                FcQuads.litVertex(vc, p, xb, a[0] * eb, a[1] + wb * a[0] / 5F, u1, v1, rgb, 255, lit, 0, 0.3F, 1);
                FcQuads.litVertex(vc, p, xb, b[0] * eb, b[1] + wb * b[0] / 5F, u1, v0, rgb, 255, lit, 0, 0.3F, 1);
                FcQuads.litVertex(vc, p, xa, b[0] * ea, b[1] + wa * b[0] / 5F, u0, v0, rgb, 255, lit, 0, 0.3F, 1);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SandTsunamiEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
