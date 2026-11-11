package com.renanpontes.isekaimahou.feature.cities.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Cristal do coração da cidade: um octaedro que flutua e gira sobre o pedestal, com um núcleo mais claro girando ao
 * contrário e quatro lascas em órbita. Desenhado sempre aceso (emissivo). A cor é a do reino (v0.9): azul de Aldória,
 * âmbar-sol de Sahra, branco-gelo de Nordheim.
 */
public class CityHeartRenderer implements BlockEntityRenderer<CityHeartBlockEntity> {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/city_heart_crystal.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public CityHeartRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CityHeartBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        float t = (be.getLevel().getGameTime() % 24000L) + partialTick;
        float bob = Mth.sin(t * 0.06F) * 0.08F;
        int[] c = colors(be.kingdom());
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        ps.pushPose();
        ps.translate(0.5, 1.55 + bob, 0.5);
        // Cristal principal.
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.6F));
        octahedron(ps, vc, 0.28F, 0.5F, c[0], c[1], c[2], 215);
        ps.popPose();
        // Núcleo brilhante girando ao contrário.
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-t * 3.2F));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.04F) * 12.0F));
        octahedron(ps, vc, 0.13F, 0.24F, c[3], c[4], c[5], 255);
        ps.popPose();
        // Lascas em órbita.
        for (int i = 0; i < 4; i++) {
            float a = t * 2.4F + i * 90.0F;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(a));
            ps.translate(0.62, Mth.sin((t + i * 20) * 0.08F) * 0.12F, 0.0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 6.0F));
            octahedron(ps, vc, 0.06F, 0.1F, c[6], c[7], c[8], 200);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Cristal, núcleo e lascas (RGB) do reino. */
    private static int[] colors(CityKingdom k) {
        return switch (k) {
            case SAHRA -> new int[] {255, 176, 80, 255, 240, 200, 255, 205, 110};
            case NORDHEIM -> new int[] {190, 235, 255, 250, 255, 255, 215, 245, 255};
            default -> new int[] {120, 190, 255, 225, 245, 255, 150, 210, 255};
        };
    }

    /** Octaedro com 8 faces triangulares (cada uma como um quad com um vértice repetido). */
    private static void octahedron(PoseStack ps, VertexConsumer vc, float w, float h, int r, int g, int b, int a) {
        PoseStack.Pose pose = ps.last();
        Vector3f top = new Vector3f(0, h, 0);
        Vector3f bottom = new Vector3f(0, -h, 0);
        Vector3f[] ring = {new Vector3f(w, 0, 0), new Vector3f(0, 0, w), new Vector3f(-w, 0, 0), new Vector3f(0, 0, -w)};
        for (int i = 0; i < 4; i++) {
            Vector3f p0 = ring[i];
            Vector3f p1 = ring[(i + 1) % 4];
            tri(pose, vc, top, p1, p0, r, g, b, a);
            tri(pose, vc, bottom, p0, p1, (int) (r * 0.7F), (int) (g * 0.75F), b, a);
        }
    }

    private static void tri(PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f c, int r, int g, int bl, int al) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        vertex(pose, vc, a, 0.5F, 0.0F, n, r, g, bl, al);
        vertex(pose, vc, b, 0.0F, 1.0F, n, r, g, bl, al);
        vertex(pose, vc, c, 1.0F, 1.0F, n, r, g, bl, al);
        vertex(pose, vc, c, 1.0F, 1.0F, n, r, g, bl, al);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vector3f p, float u, float v, Vector3f n, int r, int g, int b, int a) {
        vc.addVertex(pose, p.x(), p.y(), p.z()).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, n.x(), n.y(), n.z());
    }

    @Override
    public AABB getRenderBoundingBox(CityHeartBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0).expandTowards(0.0, 2.0, 0.0);
    }
}
