package com.renanpontes.isekaimahou.feature.legends_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_d.entity.DisintegrationCageEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Desintegração. A gaiola: círculos sagrados empilhados (girando em sentidos alternados) que se FECHAM em volta do
 * alvo, grades verticais de luz dourada, parede translúcida subindo e um círculo maior no chão. O julgamento: um
 * pilar de luz branco-dourada desce do céu (feixe estilo farol até as nuvens), clarão e anel de choque no chão.
 */
public class DisintegrationCageRenderer extends EntityRenderer<DisintegrationCageEntity> {
    private static final ResourceLocation CIRCLE = LdRenderTypes.tex("holy_circle");
    private static final int GOLD = DisintegrationCageEntity.GOLD;
    private static final int WHITE_GOLD = DisintegrationCageEntity.WHITE_GOLD;

    public DisintegrationCageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DisintegrationCageEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float time = e.tickCount + pt;
        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 camRel = entityRenderDispatcher.camera.getPosition().subtract(entPos);
        float r = e.radius(), h = e.height();
        float k = e.closing(ageF);
        float pres = e.presence(ageF);
        float pil = e.pillar(ageF);
        Matrix4f m = pose.last().pose();

        // Pilar de luz do céu (o feixe opaco vem primeiro: escreve profundidade)
        if (pil > 0.01F) {
            float beamR = r * 0.92F * (0.4F + 0.6F * pil);
            LdDraw.beaconBeam(pose, buffers, new Vec3(0, -2, 0), new Vec3(0, 260, 0), WHITE_GOLD, beamR, r * 1.3F, pil, time);
        }

        if (pres > 0.01F) {
            float ring = r * (1F + (1F - k) * 0.9F);
            int n = Mth.clamp(Math.round(h / 1.7F), 4, 10);
            VertexConsumer tex = buffers.getBuffer(LdRenderTypes.additiveTexture(CIRCLE));
            for (int i = 0; i < n; i++) {
                float y = h * i / (n - 1F);
                pose.pushPose();
                pose.translate(0, y + 0.05, 0);
                pose.mulPose(Axis.YP.rotationDegrees((i % 2 == 0 ? 1 : -1) * time * (3F + 2F * k) + i * 23F));
                float a = pres * (0.4F + 0.55F * k) * (i == 0 ? 1F : 0.85F);
                LdDraw.texFlat(tex, pose.last().pose(), ring, LdDraw.argb(i % 2 == 0 ? WHITE_GOLD : GOLD, a));
                pose.popPose();
            }
            // Círculo maior no chão (o julgamento marcado)
            pose.pushPose();
            pose.translate(0, 0.04, 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * 1.2F));
            LdDraw.texFlat(tex, pose.last().pose(), r * 1.45F, LdDraw.argb(GOLD, 0.55F * pres));
            pose.popPose();

            VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
            // Grades verticais de luz
            int bars = 10;
            for (int i = 0; i < bars; i++) {
                double a = Math.PI * 2 * i / bars + time * 0.02;
                Vec3 base = new Vec3(Math.cos(a) * ring, 0, Math.sin(a) * ring);
                LdDraw.beamTo(g, m, base, base.add(0, h, 0), camRel, 0.07F, WHITE_GOLD, 0.7F * pres * k, 0.15F * pres);
            }
            // Parede translúcida e brilho interno
            LdDraw.cylinderWall(g, m, ring, h, 40, GOLD, 0.28F * pres * k, 0F);
            LdDraw.cylinderWall(g, m, ring * 0.98F, h * 0.6F, 40, 0xFFFFFF, 0.12F * pres * k, 0F);
        }

        if (pil > 0.01F) {
            VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
            float t = ageF - DisintegrationCageEntity.CHARGE;
            // Núcleo branco ao longo do pilar + clarão no chão
            LdDraw.beamTo(g, m, new Vec3(0, -1, 0), new Vec3(0, 120, 0), camRel, r * 0.5F * pil, 0xFFFFFF, 0.8F * pil, 0F);
            pose.pushPose();
            pose.translate(0, 0.3, 0);
            float shock = r * (1.1F + 0.25F * t);
            LdDraw.ringFlat(g, pose.last().pose(), shock, 0.5F + r * 0.15F, WHITE_GOLD, 0.9F * pil, 48);
            pose.popPose();
            pose.pushPose();
            pose.translate(0, 1.0, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            LdDraw.halo(g, pose.last().pose(), 0.2F, r * 0.8F, r * 3.2F, WHITE_GOLD, 0.7F * pil, 32);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DisintegrationCageEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
