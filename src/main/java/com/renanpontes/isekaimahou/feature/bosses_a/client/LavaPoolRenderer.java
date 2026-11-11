package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.IgnisLavaPool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Poça de lava: um disco no chão feito de células de meio bloco com a textura animada da lava
 * (lava_still do atlas de blocos), bordas que esmaecem e brilho total. Na fase azul do Ignis fica tingida de azul.
 */
public class LavaPoolRenderer extends EntityRenderer<IgnisLavaPool> {
    private static final ResourceLocation LAVA = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final float CELL = 0.5F;

    public LavaPoolRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(IgnisLavaPool pool, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = pool.visibleRadius(partialTick);
        if (r < 0.05F) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LAVA);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        boolean blue = pool.isBlue();
        int cr = blue ? 120 : 255, cg = blue ? 190 : 255, cb = 255;
        int cells = Mth.ceil(r / CELL);
        for (int ix = -cells; ix < cells; ix++) {
            for (int iz = -cells; iz < cells; iz++) {
                float x0 = ix * CELL, z0 = iz * CELL;
                float cx = x0 + CELL * 0.5F, cz = z0 + CELL * 0.5F;
                float d = Mth.sqrt(cx * cx + cz * cz);
                if (d > r + 0.15F) continue;
                // Borda: quanto mais perto do raio, mais transparente
                int alpha = (int) (Mth.clamp((r + 0.15F - d) / 0.7F, 0F, 1F) * 0.92F * 255);
                // Cada célula usa um quarto da textura (a lava se repete a cada bloco)
                float fu = Mth.positiveModulo(x0, 1F), fv = Mth.positiveModulo(z0, 1F);
                float u0 = sprite.getU(fu), u1 = sprite.getU(fu + CELL);
                float v0 = sprite.getV(fv), v1 = sprite.getV(fv + CELL);
                float y = 0.03F + (Math.abs(ix + iz) % 2) * 0.002F;
                vertex(vc, p, x0, y, z0, u0, v0, cr, cg, cb, alpha);
                vertex(vc, p, x0, y, z0 + CELL, u0, v1, cr, cg, cb, alpha);
                vertex(vc, p, x0 + CELL, y, z0 + CELL, u1, v1, cr, cg, cb, alpha);
                vertex(vc, p, x0 + CELL, y, z0, u1, v0, cr, cg, cb, alpha);
            }
        }
        super.render(pool, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(IgnisLavaPool entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
