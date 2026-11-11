package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.InfinityFieldEntity;
import com.renanpontes.isekaimahou.feature.legends_c.infinity.Infinity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * A esfera do Infinito: distorção SUTIL em volta do dono — casca de "ar ondulando" (textura de redemoinho quase
 * invisível que ondula), borda fresnel azul-clara, anéis finos girando em eixos diferentes. Quando projéteis estão
 * presos, brilha mais; sob Acerto Garantido a casca pisca e racha (raios).
 */
public class InfinityFieldRenderer extends EntityRenderer<InfinityFieldEntity> {
    private static final ResourceLocation SWIRL = LcRenderTypes.tex("swirl");

    public InfinityFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(InfinityFieldEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity o = e.ownerEntity();
        if (o == null) return;
        Vec3 fieldPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 center = o.getPosition(pt).add(0, o.getBbHeight() * 0.5, 0);
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        Vec3 camRel = cam.subtract(center);
        float r = (float) (Infinity.STOP + o.getBbWidth() * 0.5 + 0.35);
        boolean inside = camRel.lengthSqr() < r * r;
        float time = e.tickCount + pt;
        float appear = Mth.clamp((e.tickCount + pt) / 10F, 0F, 1F);
        boolean pierced = e.pierced();
        float flicker = pierced ? 0.25F + 0.5F * RandomSource.create((long) (time * 0.5F)).nextFloat() : 1F;
        float k = appear * flicker * (1F + Math.min(0.6F, e.heldCount() * 0.08F));
        int tint = pierced ? 0xFFB0B0 : LcFx.BLUE_LIGHT;

        pose.pushPose();
        pose.translate(center.x - fieldPos.x, center.y - fieldPos.y, center.z - fieldPos.z);
        Matrix4f m = pose.last().pose();

        // Ar ondulando: casca de redemoinho fraquinha (o "vidro" do espaço infinito)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.2F));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.03F) * 25F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.004F) % 1F, (time * 0.006F) % 1F));
        LcDraw.texSphere(sw, pose.last(), r, 14, 24, LcDraw.swirl(tint, (inside ? 0.05F : 0.16F) * k), 2F, 0.035F, time);
        pose.popPose();

        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        // Borda fresnel (quase invisível de frente, brilha nas beiradas)
        LcDraw.glowSphere(g, m, r * 1.01F, 12, 22, tint, 0F, 0.28F * k, camRel);
        LcDraw.glowSphere(g, m, r * 0.94F, 10, 18, 0xFFFFFF, 0F, 0.10F * k, camRel);
        // Anéis finos girando (só de fora: em primeira pessoa riscariam a tela)
        for (int i = 0; i < (inside ? 0 : 3); i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * (1.5F + i) + i * 60F));
            pose.mulPose(Axis.XP.rotationDegrees(35F + i * 50F + Mth.sin(time * 0.02F + i) * 10F));
            LcDraw.ringFlat(g, pose.last().pose(), r * (0.98F - i * 0.03F), 0.04F, tint, 0.22F * k, 56);
            pose.popPose();
        }
        // Acerto Garantido: rachaduras elétricas na casca
        if (pierced) {
            long seed = (long) (time / 2) * 131L + e.getId();
            RandomSource rnd = RandomSource.create(seed);
            for (int i = 0; i < 4; i++) {
                Vec3 a = LcDraw.randomOnSphere(rnd).scale(r);
                Vec3 b = a.add(LcDraw.randomOnSphere(rnd).scale(r * 0.7)).normalize().scale(r);
                LcDraw.arc(g, m, a, b, camRel, seed + i, r * 0.3F, 0.025F, 0xFF4040, 0.8F);
            }
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(InfinityFieldEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(InfinityFieldEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
