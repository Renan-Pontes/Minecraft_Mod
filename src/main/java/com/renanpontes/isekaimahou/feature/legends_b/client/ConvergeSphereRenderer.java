package com.renanpontes.isekaimahou.feature.legends_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_b.entity.ConvergeSphereEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Convergência. Na carga: dois orbes (cada um com núcleo branco, casca de energia girando, borda brilhante e halo)
 * em espiral até se fundirem, com raios elétricos entre eles e um núcleo que cresce no centro.
 * No voo: a esfera — coração de vazio estrelado, brilho interno, duas cascas de energia em redemoinho que ondulam
 * (a "distorção"), atmosfera brilhante na borda, halo, ondas de espaço distorcido saindo dela e faíscas na superfície.
 */
public class ConvergeSphereRenderer extends EntityRenderer<ConvergeSphereEntity> {
    private static final ResourceLocation SWIRL = LbRenderTypes.tex("converge_swirl");

    public ConvergeSphereRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ConvergeSphereEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float time = e.tickCount + pt;
        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        if (e.phase() == ConvergeSphereEntity.PHASE_CHARGE) renderCharge(e, ageF, time, entPos, cam, pt, pose, buffers);
        else renderSphere(e, ageF, time, cam.subtract(entPos), pose, buffers);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    // =====================================================================================
    //  Carga: dois orbes em espiral
    // =====================================================================================
    private void renderCharge(ConvergeSphereEntity e, float ageF, float time, Vec3 entPos, Vec3 cam, float pt,
                              PoseStack pose, MultiBufferSource buffers) {
        Vec3 dir = e.dir();
        Vec3 center = entPos;
        // Quem segura: posição e mira interpoladas no quadro (sem atraso na primeira pessoa)
        if (e.ownerEntity() instanceof LivingEntity owner) {
            dir = e.holdDir(owner, pt);
            center = ConvergeSphereEntity.holdPoint(owner, dir, pt);
        }
        float prog = Mth.clamp(ageF / e.chargeTicks(), 0F, 1F);
        Vec3 off = ConvergeSphereEntity.orbOffset(dir, prog);
        Vec3 camRel = cam.subtract(center);
        pose.pushPose();
        pose.translate(center.x - entPos.x, center.y - entPos.y, center.z - entPos.z);

        float size = 1F + 0.35F * prog;
        orb(pose, buffers, off, e.colorA(), camRel.subtract(off), time, size);
        orb(pose, buffers, off.scale(-1), e.colorB(), camRel.add(off), time + 7F, size);

        VertexConsumer g = buffers.getBuffer(LbRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        // Raios entre os orbes (piscam trocando o desenho a cada 2 ticks)
        if (prog > 0.25F) {
            float a = Mth.clamp((prog - 0.25F) / 0.3F, 0F, 1F) * 0.8F;
            long seed = (long) (time / 2) * 31L + e.getId();
            LbDraw.arc(g, m, off, off.scale(-1), camRel, seed, 0.5F, 0.035F, e.colorA(), a);
            LbDraw.arc(g, m, off, off.scale(-1), camRel, seed + 7, 0.5F, 0.03F, e.colorB(), a);
        }
        // O núcleo da fusão nasce no centro e cresce
        if (prog > 0.5F) {
            float k = (prog - 0.5F) / 0.5F;
            int core = e.color();
            LbDraw.glowSphere(g, m, 0.12F + 0.5F * k, 8, 12, LbDraw.lighten(core, 0.5F), 0.6F * k, 1F, camRel);
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            LbDraw.halo(g, pose.last().pose(), 0.1F, 0.4F + 0.6F * k, 1.4F + 1.8F * k, core, 0.55F * k, 24);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Um orbe: núcleo branco, casca de energia girando, borda brilhante e halo. */
    private void orb(PoseStack pose, MultiBufferSource buffers, Vec3 at, int rgb, Vec3 camRel, float time, float size) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        float r = (0.32F + 0.04F * Mth.sin(time * 0.6F)) * size;
        VertexConsumer g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, pose.last().pose(), r * 0.5F, 6, 10, 0xFFFFFF, 0.95F, 0.5F, camRel);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 14F));
        pose.mulPose(Axis.XP.rotationDegrees(time * 6F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.03F) % 1F, (time * 0.02F) % 1F));
        LbDraw.texSphere(sw, pose.last(), r, 8, 12, LbDraw.swirl(LbDraw.lighten(rgb, 0.25F), 1F), 2F, 0.06F, time);
        pose.popPose();

        g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, pose.last().pose(), r * 1.18F, 8, 14, rgb, 0.05F, 0.85F, camRel);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LbDraw.halo(g, pose.last().pose(), r * 0.8F, r * 1.25F, r * 3.2F, rgb, 0.5F, 20);
        pose.popPose();
    }

    // =====================================================================================
    //  Voo: a esfera
    // =====================================================================================
    private void renderSphere(ConvergeSphereEntity e, float ageF, float time, Vec3 camRel, PoseStack pose, MultiBufferSource buffers) {
        float r = e.visualRadius(ageF);
        if (r <= 0.02F) return;
        int core = e.color();
        int bright = LbDraw.lighten(core, 0.55F);
        Matrix4f m = pose.last().pose();

        // Coração de vazio (céu do End: o "espaço" apagado lá dentro) + brilho interno por cima
        LbDraw.posSphere(buffers.getBuffer(RenderType.endGateway()), m, r * 0.62F, 10, 18);
        VertexConsumer g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, m, r * 0.76F, 10, 18, bright, 0.3F, 0.95F, camRel);

        // Cascas de energia girando em volta do eixo do voo, ondulando (distorção)
        pose.pushPose();
        pose.mulPose(LbDraw.yTo(e.dir()));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 9F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.025F) % 1F, (time * 0.04F) % 1F));
        LbDraw.texSphere(sw, pose.last(), r, 14, 24, LbDraw.swirl(core, 1F), 3F, 0.05F, time);
        pose.popPose();
        pose.mulPose(Axis.YP.rotationDegrees(-time * 6F));
        pose.mulPose(Axis.ZP.rotationDegrees(25F));
        VertexConsumer sw2 = buffers.getBuffer(RenderType.energySwirl(SWIRL, (-time * 0.035F) % 1F, (time * 0.02F) % 1F));
        LbDraw.texSphere(sw2, pose.last(), r * 1.07F, 12, 20, LbDraw.swirl(LbDraw.lighten(core, 0.35F), 0.55F), 2F, 0.08F, time + 40F);
        pose.popPose();

        // Atmosfera brilhante na borda + halo + ondas de espaço distorcido
        g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, m, r * 1.16F, 10, 18, bright, 0F, 0.9F, camRel);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        Matrix4f bm = pose.last().pose();
        LbDraw.halo(g, bm, r * 0.95F, r * 1.25F, r * 2.5F, core, 0.6F, 36);
        for (int k = 0; k < 2; k++) {
            float ph = ((time / 18F) + k * 0.5F) % 1F;
            float rr = r * (1.15F + 1.1F * ph);
            LbDraw.halo(g, bm, rr - 0.12F * r, rr, rr + 0.1F * r, LbDraw.lighten(core, 0.3F), (1F - ph) * 0.35F, 40);
        }
        // Clarão horizontal na tela (risco de luz atravessando a esfera)
        LbDraw.softRibbon(g, bm, new Vec3(-r * 3.2F, 0, 0), new Vec3(r * 3.2F, 0, 0),
                new Vec3(0, 1, 0), r * 0.08F, r * 0.08F, bright, 0.5F, 0.5F);
        pose.popPose();

        // Faíscas na superfície
        long seed = (long) (time / 2) * 977L + e.getId();
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(seed);
        for (int k = 0; k < 3; k++) {
            Vec3 a = randomOnSphere(rnd).scale(r * 1.02);
            Vec3 b = a.add(randomOnSphere(rnd).scale(r * 0.8)).normalize().scale(r * 1.05);
            LbDraw.arc(g, m, a, b, camRel, seed + k, r * 0.25F, 0.03F + 0.01F * r, bright, 0.8F);
        }
    }

    private static Vec3 randomOnSphere(net.minecraft.util.RandomSource rnd) {
        double z = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
    }

    @Override
    public ResourceLocation getTextureLocation(ConvergeSphereEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
