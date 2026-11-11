package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.IceCoffinEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Caixão de Gelo: um prisma HEXAGONAL de gelo compactado translúcido com ponta de cristal em cima e em baixo, brilho
 * gelado por dentro, círculo de runas girando na base e um tremor (rachando) nos últimos instantes antes de estilhaçar.
 */
public class IceCoffinRenderer extends EntityRenderer<IceCoffinEntity> {
    private static final int SIDES = 6;

    public IceCoffinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(IceCoffinEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float grow = Mth.clamp((e.age() + pt) / 5F, 0F, 1F);
        float r = e.scale() * (0.6F + 0.4F * grow);
        float h = e.coffinHeight() * grow;
        float left = e.lifeLeft() - pt;
        ps.pushPose();
        // Rachando: treme nos últimos 10 ticks
        if (left < 10) ps.translate((e.getRandom().nextFloat() - 0.5F) * 0.06F, 0, (e.getRandom().nextFloat() - 0.5F) * 0.06F);
        ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 60));
        TextureAtlasSprite ice = FcQuads.sprite("packed_ice");
        TextureAtlasSprite blue = FcQuads.sprite("blue_ice");
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 9), LightTexture.sky(light));
        VertexConsumer vc = FcQuads.blocks(buffers);
        prism(ps, vc, ice, r, -0.15F, h, 0xE6F8FF, 170, lit);
        prism(ps, vc, blue, r * 0.55F, 0.1F, h * 0.85F, 0xB8E4FF, 110, lit);
        // Brilho frio por dentro
        VertexConsumer add = FcQuads.additive(buffers, FcQuads.BEAM);
        FcQuads.beam(ps, add, r * 0.9F, h, t * 0.02F, h, 0x8FDFFF, 0.35F + (left < 10 ? 0.3F * Mth.sin(t * 2.5F) : 0F));
        // Runas na base
        ps.pushPose();
        ps.translate(0, 0.04, 0);
        FcQuads.flat(ps, FcQuads.additive(buffers, FcQuads.RUNE), r + 0.8F, t * 2, 0x9FE8FF, 0.75F * grow);
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** Prisma hexagonal com pontas piramidais em cima e embaixo (textura de bloco, luz do mundo). */
    private static void prism(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float r, float y0, float y1, int rgb, int a, int lit) {
        PoseStack.Pose p = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1(), um = (u0 + u1) * 0.5F;
        float tip = r * 0.9F;
        for (int i = 0; i < SIDES; i++) {
            float a0 = i * Mth.TWO_PI / SIDES, a1 = (i + 1) * Mth.TWO_PI / SIDES;
            float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
            float nx = Mth.cos((a0 + a1) * 0.5F), nz = Mth.sin((a0 + a1) * 0.5F);
            // Lateral
            FcQuads.litVertex(vc, p, x0, y0, z0, u0, v1, rgb, a, lit, nx, 0, nz);
            FcQuads.litVertex(vc, p, x1, y0, z1, u1, v1, rgb, a, lit, nx, 0, nz);
            FcQuads.litVertex(vc, p, x1, y1, z1, u1, v0, rgb, a, lit, nx, 0, nz);
            FcQuads.litVertex(vc, p, x0, y1, z0, u0, v0, rgb, a, lit, nx, 0, nz);
            // Ponta de cima
            FcQuads.litVertex(vc, p, x0, y1, z0, u0, v1, rgb, a, lit, nx, 0.6F, nz);
            FcQuads.litVertex(vc, p, x1, y1, z1, u1, v1, rgb, a, lit, nx, 0.6F, nz);
            FcQuads.litVertex(vc, p, 0, y1 + tip, 0, um, v0, rgb, a, lit, nx, 0.6F, nz);
            FcQuads.litVertex(vc, p, 0, y1 + tip, 0, um, v0, rgb, a, lit, nx, 0.6F, nz);
            // Ponta de baixo (meio enterrada)
            FcQuads.litVertex(vc, p, x1, y0, z1, u1, v0, rgb, a, lit, nx, -0.6F, nz);
            FcQuads.litVertex(vc, p, x0, y0, z0, u0, v0, rgb, a, lit, nx, -0.6F, nz);
            FcQuads.litVertex(vc, p, 0, y0 - tip * 0.5F, 0, um, v1, rgb, a, lit, nx, -0.6F, nz);
            FcQuads.litVertex(vc, p, 0, y0 - tip * 0.5F, 0, um, v1, rgb, a, lit, nx, -0.6F, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(IceCoffinEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
