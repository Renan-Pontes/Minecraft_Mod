package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.LavaGeyserEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Gêiser de Lava: no aviso, um disco de rachaduras incandescentes pulsando no chão; na erupção, uma coluna de LAVA
 * de verdade (textura animada do jogo) borbulhando, com núcleo brilhante, coroa de respingos no topo e anel de calor
 * na base. A coluna sobe rápido e afunda no fim.
 */
public class LavaGeyserRenderer extends EntityRenderer<LavaGeyserEntity> {
    public LavaGeyserRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(LavaGeyserEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float r = e.scale();
        int age = e.age();
        ps.pushPose();
        ps.translate(0, 0.03, 0);
        if (age < LavaGeyserEntity.WARMUP) {
            float k = (age + pt) / LavaGeyserEntity.WARMUP;
            float pulse = 0.6F + 0.4F * Mth.sin(t * 0.9F);
            VertexConsumer glow = FcQuads.additive(buffers, FcQuads.GLOW);
            FcQuads.flat(ps, glow, (r + 0.8F) * (0.4F + 0.6F * k), t * 2F, 0xFF5A10, 0.9F * pulse * k);
            VertexConsumer cracks = FcQuads.additive(buffers, FcQuads.CRACKS);
            FcQuads.flat(ps, cracks, r + 0.9F, -t, 0xFF8A20, 0.7F * k);
            ps.popPose();
            return;
        }
        float h = e.columnHeight(pt);
        if (h > 0.05F) {
            TextureAtlasSprite lava = FcQuads.sprite("lava_still");
            TextureAtlasSprite flow = FcQuads.sprite("lava_flow");
            VertexConsumer vc = FcQuads.blocksGlow(buffers);
            FcQuads.spriteTube(ps, vc, flow, r * 0.95F, 0, h, 1F, 14, 0xFFFFFF, 1F, t, 0.08F);
            FcQuads.spriteTube(ps, vc, lava, r * 0.6F, 0, h + 0.4F, 1F, 10, 0xFFE0A0, 1F, t + 7, 0.12F);
            // Núcleo incandescente + coroa de respingos
            VertexConsumer add = FcQuads.additive(buffers, FcQuads.BEAM);
            FcQuads.beam(ps, add, r * 0.75F, h + 0.6F, -t * 0.08F, 2F, 0xFF7A20, 0.8F);
            ps.pushPose();
            ps.translate(0, h, 0);
            VertexConsumer glow = FcQuads.additive(buffers, FcQuads.GLOW);
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6 + t * 0.15F;
                ps.pushPose();
                ps.translate(Mth.cos(a) * r * 0.8F, 0.3F + 0.3F * Mth.sin(t * 0.5F + i), Mth.sin(a) * r * 0.8F);
                ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
                FcQuads.billboard(ps, glow, 0.6F + 0.2F * Mth.sin(t + i), t * 5, 0xFF9A30, 0.85F);
                ps.popPose();
            }
            FcQuads.flat(ps, glow, r * 2.2F, t * 3, 0xFF6A10, 0.6F);
            ps.popPose();
            FcQuads.ring(ps, FcQuads.additive(buffers, FcQuads.RING), r, r + 1.6F, 0xFF4500, 0.7F, 28);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LavaGeyserEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
