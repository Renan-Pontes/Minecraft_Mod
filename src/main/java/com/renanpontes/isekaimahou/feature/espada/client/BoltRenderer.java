package com.renanpontes.isekaimahou.feature.espada.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBolt;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * SÓ CLIENTE. Projéteis dos Espada como um RISCO de energia (fita aditiva virada para a câmera, esticada na direção do
 * voo): miolo branco + borda da cor do tipo. Lança de Luz de la Luna é longa e grossa; Bala é curta.
 * <ul>
 *   <li>Lanza del Relámpago: lança verde enorme (miolo branco, brilho que pulsa, ponta em losango);</li>
 *   <li>Garra de Desgarrón: meia-lua AZUL em pé (marca de garra gigante, ~3 blocos de altura) com rastro.</li>
 * </ul>
 */
public class BoltRenderer extends EntityRenderer<EspadaBolt> {
    public BoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(EspadaBolt bolt, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        // As garras e a lança são bem maiores que a caixa do projétil.
        if (bolt.kind() == EspadaBolt.CLAW || bolt.kind() == EspadaBolt.LANZA) return bolt.distanceToSqr(x, y, z) < 128 * 128;
        return super.shouldRender(bolt, frustum, x, y, z);
    }

    @Override
    public void render(EspadaBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 vel = bolt.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) vel = Vec3.directionFromRotation(bolt.getXRot(), bolt.getYRot());
        Vec3 dir = vel.normalize();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTick, bolt.xo, bolt.getX());
        double y = Mth.lerp(partialTick, bolt.yo, bolt.getY());
        double z = Mth.lerp(partialTick, bolt.zo, bolt.getZ());
        Vec3 toCam = cam.subtract(x, y, z);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int c = bolt.color();
        if (bolt.kind() == EspadaBolt.CLAW) {
            claw(vc, m, dir, toCam, c, bolt.tickCount + partialTick);
            super.render(bolt, yaw, partialTick, pose, buffers, light);
            return;
        }
        float len, width;
        switch (bolt.kind()) {
            case EspadaBolt.LANCE -> { len = 3.2F; width = 0.45F; }
            case EspadaBolt.LANZA -> { len = 6.5F; width = 0.75F; }
            case EspadaBolt.AXE -> { len = 1.6F; width = 0.9F; }
            case EspadaBolt.DART -> { len = 1.4F; width = 0.22F; }
            default -> { len = 1.1F; width = 0.28F; }
        }
        Vec3 side = side(dir, toCam);
        Vec3 tail = dir.scale(-len);
        Vec3 head = dir.scale(0.25);
        if (bolt.kind() == EspadaBolt.LANZA) {
            float pulse = 0.85F + 0.15F * Mth.sin((bolt.tickCount + partialTick) * 1.3F);
            quad(vc, m, tail.scale(1.15), head.add(dir.scale(0.4)), side.scale(width * 2.2F * pulse), EspadaFx.ULQ_DEEP, 90);
            quad(vc, m, tail, head, side.scale(width), c, 170);
            quad(vc, m, tail.scale(0.85), head, side.scale(width * 0.4F), 0xFFFFFF, 240);
            // Ponta em losango.
            quad(vc, m, head, head.add(dir.scale(1.3)), side.scale(width * 0.9F), 0xCFFFE0, 220);
            super.render(bolt, yaw, partialTick, pose, buffers, light);
            return;
        }
        quad(vc, m, tail, head, side.scale(width), c, 140);
        quad(vc, m, tail.scale(0.7), head, side.scale(width * 0.4), 0xFFFFFF, 230);
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    /**
     * Garra de Desgarrón: marca de garra GIGANTE em pé (meia-lua de ~4 blocos, curvada para a frente, pontas para trás)
     * com um rastro longo esmaecendo — w10: maior e com o rastro esticado, para ler como "garra lançada" voando longe.
     * Rastro com menos camadas no preset Baixo/Médio.
     */
    private static void claw(VertexConsumer vc, Matrix4f m, Vec3 dir, Vec3 toCam, int c, float age) {
        Vec3 f = new Vec3(dir.x, 0, dir.z);
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        float grow = Mth.clamp(age / 3F, 0.35F, 1F);
        double h = 2.0 * grow;
        int seg = 12;
        int layers = com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(com.renanpontes.isekaimahou.core.MahouConfig.Graphics.HIGH) ? 6 : 3;
        // o rastro só se estica depois que a garra já andou um pouco (não nasce atrás de quem lançou)
        double trail = Math.min(1.2, age * 0.25);
        for (int layer = 0; layer < layers; layer++) {
            // camada 0 = a lâmina; as outras = rastro para trás, cada vez mais fraco e mais fino
            Vec3 back = f.scale(-trail * layer);
            int alphaOuter = layer == 0 ? 180 : Math.max(18, 120 - layer * 22);
            float thin = layer == 0 ? 1F : 1F - layer * 0.1F;
            for (int i = 0; i < seg; i++) {
                double a0 = -1.15 + 2.3 * i / seg, a1 = -1.15 + 2.3 * (i + 1) / seg;
                Vec3 p0 = up.scale(h * Math.sin(a0)).add(f.scale(h * 0.55 * (Math.cos(a0) - 1))).add(back);
                Vec3 p1 = up.scale(h * Math.sin(a1)).add(f.scale(h * 0.55 * (Math.cos(a1) - 1))).add(back);
                // mais grossa no meio, afina nas pontas
                float w0 = (float) (0.5 * Math.cos(a0 * 0.9)) * thin, w1 = (float) (0.5 * Math.cos(a1 * 0.9)) * thin;
                Vec3 s = side(p1.subtract(p0).normalize(), toCam.subtract(p0));
                quadW(vc, m, p0, p1, s, w0, w1, c, alphaOuter);
                if (layer == 0) quadW(vc, m, p0, p1, s, w0 * 0.35F, w1 * 0.35F, 0xE8F8FF, 240);
                else if (layer == 1) quadW(vc, m, p0, p1, s, w0 * 0.25F, w1 * 0.25F, 0xCFEFFF, 120);
            }
        }
    }

    private static Vec3 side(Vec3 dir, Vec3 toCam) {
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        return side.normalize();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 s, int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        vc.addVertex(m, (float) (a.x + s.x), (float) (a.y + s.y), (float) (a.z + s.z)).setColor(r, g, bl, alpha / 3);
        vc.addVertex(m, (float) (a.x - s.x), (float) (a.y - s.y), (float) (a.z - s.z)).setColor(r, g, bl, alpha / 3);
        vc.addVertex(m, (float) (b.x - s.x), (float) (b.y - s.y), (float) (b.z - s.z)).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) (b.x + s.x), (float) (b.y + s.y), (float) (b.z + s.z)).setColor(r, g, bl, alpha);
    }

    /** Trecho de fita com largura diferente em cada ponta (mesma transparência). */
    private static void quadW(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 s, float wa, float wb, int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        Vec3 sa = s.scale(wa), sb = s.scale(wb);
        vc.addVertex(m, (float) (a.x + sa.x), (float) (a.y + sa.y), (float) (a.z + sa.z)).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) (a.x - sa.x), (float) (a.y - sa.y), (float) (a.z - sa.z)).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) (b.x - sb.x), (float) (b.y - sb.y), (float) (b.z - sb.z)).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) (b.x + sb.x), (float) (b.y + sb.y), (float) (b.z + sb.z)).setColor(r, g, bl, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(EspadaBolt bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
