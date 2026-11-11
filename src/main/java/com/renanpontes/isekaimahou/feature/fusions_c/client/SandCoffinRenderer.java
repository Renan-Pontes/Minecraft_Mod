package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandCoffinEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Caixão de Areia: a areia sobe girando em espiral pelo corpo do alvo até fechar um casulo (textura de areia do jogo),
 * com uma camada de poeira rodopiando por fora; no Funeral, o casulo se aperta e implode. Soterrar: um monte de areia
 * baixo e largo cobrindo o alvo.
 */
public class SandCoffinRenderer extends EntityRenderer<SandCoffinEntity> {
    private static final int SEG = 16;
    private static final int ROWS = 10;

    public SandCoffinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SandCoffinEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float closed = e.closed(pt);
        float crush = e.crush(pt);
        float r = e.scale();
        float h = e.coffinHeight();
        TextureAtlasSprite sand = FcQuads.sprite("sand");
        TextureAtlasSprite stone = FcQuads.sprite("sandstone");
        VertexConsumer vc = FcQuads.blocks(buffers);
        ps.pushPose();
        if (e.burial()) {
            // Monte de areia: meia esfera achatada
            float rr = (r + 0.7F) * closed;
            lathe(ps, vc, sand, rr, h * 0.85F * closed, 1F, y -> (float) Math.sqrt(Math.max(0, 1 - y * y)), 0xFFFFFF, 255, light);
        } else {
            float spin = closed < 1 ? t * 25F : t * 3F;
            ps.mulPose(Axis.YP.rotationDegrees(spin));
            float squeeze = 1F - 0.45F * crush;
            float rr = r * squeeze;
            // Casulo: barriga mais larga no meio, fecha em cima (sobe com "closed")
            lathe(ps, vc, sand, rr, h * (1F + 0.15F * crush), closed,
                    y -> 0.75F + 0.3F * Mth.sin(y * Mth.PI) - (y > 0.85F ? (y - 0.85F) * 4F : 0), 0xFFF2D8, 255, light);
            // Faixas de arenito em espiral (dá relevo)
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-spin * 1.6F));
            lathe(ps, vc, stone, rr * 1.06F, h * 0.9F, closed * 0.95F, y -> 0.75F + 0.3F * Mth.sin(y * Mth.PI), 0xE8D2A0, 150, light);
            ps.popPose();
            // Poeira rodopiando por fora
            ps.mulPose(Axis.YP.rotationDegrees(-t * 9F));
            FcQuads.tube(ps, FcQuads.glow(buffers, FcQuads.NOISE), rr * 1.35F, rr * 1.15F, 0, h * closed + 0.2F, t * 0.05F, 2F, 1F,
                    0xE3C77A, 0.45F * (1F - crush), 0.1F, 16);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /**
     * Superfície de revolução (torno): raio = {@code r × profile(y/h)} de y=0 até y=h×{@code upTo}; cada fileira usa
     * o sprite inteiro. Luz do mundo (é areia de verdade, não brilha).
     */
    private static void lathe(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float r, float h, float upTo,
                              java.util.function.Function<Float, Float> profile, int rgb, int a, int light) {
        if (upTo <= 0.01F || h <= 0.01F) return;
        PoseStack.Pose p = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 4), LightTexture.sky(light));
        int rows = Math.max(1, Mth.ceil(ROWS * upTo));
        for (int k = 0; k < rows; k++) {
            float ya = upTo * k / rows, yb = upTo * (k + 1) / rows;
            float ra = r * Math.max(0F, profile.apply(ya)), rb = r * Math.max(0F, profile.apply(yb));
            for (int j = 0; j < SEG; j++) {
                float p0 = Mth.TWO_PI * j / SEG, p1 = Mth.TWO_PI * (j + 1) / SEG;
                float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
                float ua = (j % 2 == 0) ? u0 : (u0 + u1) * 0.5F, ub = (j % 2 == 0) ? (u0 + u1) * 0.5F : u1;
                FcQuads.litVertex(vc, p, c0 * ra, ya * h, s0 * ra, ua, v1, rgb, a, lit, c0, 0.2F, s0);
                FcQuads.litVertex(vc, p, c1 * ra, ya * h, s1 * ra, ub, v1, rgb, a, lit, c1, 0.2F, s1);
                FcQuads.litVertex(vc, p, c1 * rb, yb * h, s1 * rb, ub, v0, rgb, a, lit, c1, 0.2F, s1);
                FcQuads.litVertex(vc, p, c0 * rb, yb * h, s0 * rb, ua, v0, rgb, a, lit, c0, 0.2F, s0);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SandCoffinEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
