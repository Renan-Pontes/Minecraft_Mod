package com.renanpontes.isekaimahou.feature.legends_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.entity.AtomicNovaEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * EU SOU ATÔMICO. Carga: três órbitas de átomo (anéis rúnicos azul-violeta com "elétrons" brilhantes) giram em volta
 * do conjurador, quatro círculos mágicos deitados acendem um a um em CAMADAS (girando para lados alternados), a aura
 * dele acende, uma coluna de luz sobe do chão, riscos de luz são sugados para o ponto da explosão e uma semente branca
 * cresce lá; no chão, um círculo rúnico e o círculo do raio da explosão se abrindo. Detonação: um negativo instantâneo
 * (cores invertidas dentro da esfera), núcleo branco, CÚPULA EM CAMADAS (duas cascas de energia girando, atmosfera
 * azul-violeta, aurora externa e a "saia" achatada rente ao chão), halo, raios de luz, três anéis de choque correndo
 * pelo chão e a nuvem em cogumelo luminosa subindo (coluna + chapéu girando com anel).
 */
public class AtomicNovaRenderer extends EntityRenderer<AtomicNovaEntity> {
    private static final ResourceLocation SWIRL = LdRenderTypes.tex("atomic_swirl");
    private static final ResourceLocation RING = LdRenderTypes.tex("atomic_ring");
    private static final ResourceLocation CIRCLE = LdRenderTypes.tex("atomic_circle");
    private static final int VIOLET = AtomicNovaEntity.VIOLET;
    private static final int BLUE = AtomicNovaEntity.BLUE;

    public AtomicNovaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(AtomicNovaEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float time = e.tickCount + pt;
        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        double groundRel = groundY(e, entPos) - entPos.y;
        if (!e.blasting()) renderCharge(e, ageF, time, entPos, cam, groundRel, pt, pose, buffers);
        else renderBlast(e, ageF, time, cam.subtract(entPos), groundRel, pose, buffers);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Chão abaixo do ponto (procurado uma vez; a cratera vem depois). */
    private static double groundY(AtomicNovaEntity e, Vec3 pos) {
        if (!Double.isNaN(e.clientGround)) return e.clientGround;
        BlockHitResult hit = e.level().clip(new ClipContext(pos, pos.subtract(0, 64, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, CollisionContext.empty()));
        e.clientGround = hit.getType() == HitResult.Type.MISS ? pos.y - 64 : hit.getLocation().y;
        return e.clientGround;
    }

    // =====================================================================================
    //  Carga
    // =====================================================================================
    private void renderCharge(AtomicNovaEntity e, float ageF, float time, Vec3 entPos, Vec3 cam, double groundRel, float pt,
                              PoseStack pose, MultiBufferSource buffers) {
        float k = e.charge(ageF);
        float appear = Mth.clamp(ageF / 10F, 0F, 1F);
        Entity caster = e.casterEntity();
        Vec3 cc = entPos;
        float size = 1F;
        if (caster != null) {
            cc = new Vec3(Mth.lerp(pt, caster.xo, caster.getX()), Mth.lerp(pt, caster.yo, caster.getY()) + caster.getBbHeight() * 0.55,
                    Mth.lerp(pt, caster.zo, caster.getZ()));
            size = Math.max(0.8F, caster.getBbHeight() * 0.6F);
        }

        // Órbitas do átomo em volta do conjurador
        pose.pushPose();
        pose.translate(cc.x - entPos.x, cc.y - entPos.y, cc.z - entPos.z);
        Vec3 camC = cam.subtract(cc);
        float orbit = (1.2F + 1.1F * k) * size;
        VertexConsumer ring = buffers.getBuffer(LdRenderTypes.additiveTexture(RING));
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 60F + time * 1.5F));
            pose.mulPose(Axis.XP.rotationDegrees(68F));
            pose.mulPose(Axis.YP.rotationDegrees(time * (9F + i * 4F)));
            int col = i == 1 ? BLUE : VIOLET;
            LdDraw.texFlat(ring, pose.last().pose(), orbit, LdDraw.argb(LdDraw.lighten(col, 0.2F), (0.35F + 0.6F * k) * appear));
            pose.popPose();
        }
        VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
        // Elétrons: um em cada órbita (mesma rotação dos anéis)
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 60F + time * 1.5F));
            pose.mulPose(Axis.XP.rotationDegrees(68F));
            pose.mulPose(Axis.YP.rotationDegrees(time * (9F + i * 4F) * 3F));
            pose.translate(orbit * 0.86F, 0, 0);
            LdDraw.glowSphere(g, pose.last().pose(), 0.1F + 0.06F * k, 6, 8, 0xFFFFFF, 0.95F * appear, 0.6F * appear, Vec3.ZERO);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            LdDraw.halo(g, pose.last().pose(), 0.05F, 0.15F, 0.6F + 0.3F * k, i == 1 ? BLUE : VIOLET, 0.7F * appear, 12);
            pose.popPose();
        }
        // Aura do conjurador
        LdDraw.glowSphere(g, pose.last().pose(), 0.9F * size, 10, 16, VIOLET, 0.0F, 0.55F * k * appear, camC);
        // Círculos mágicos em camadas: acendem um a um e giram para lados alternados
        VertexConsumer circ = buffers.getBuffer(LdRenderTypes.additiveTexture(CIRCLE));
        for (int i = 0; i < 4; i++) {
            float show = Mth.clamp((k - i * 0.17F) / 0.18F, 0F, 1F);
            if (show <= 0.01F) continue;
            float pop = 1F + 0.35F * (1F - show) * (1F - show);
            pose.pushPose();
            pose.translate(0, (i - 1.5F) * 0.8F * size, 0);
            float dir = i % 2 == 0 ? 1F : -1F;
            pose.mulPose(Axis.YP.rotationDegrees(dir * time * (3F + i * 2.2F) + i * 45F));
            float rad = (1.3F + 0.35F * Math.abs(i - 1.5F) + 0.8F * k) * size * pop;
            int col = i % 2 == 0 ? VIOLET : BLUE;
            LdDraw.texFlat(circ, pose.last().pose(), rad, LdDraw.argb(LdDraw.lighten(col, 0.3F), (0.45F + 0.4F * k) * show * appear));
            pose.popPose();
        }
        pose.popPose();

        // Coluna de luz subindo do chão até acima do conjurador
        if (k > 0.15F) {
            float ck = Mth.clamp((k - 0.15F) / 0.5F, 0F, 1F);
            double top = (cc.y - entPos.y) + 4.0 + 6.0 * k;
            float hgt = (float) (top - groundRel);
            if (hgt > 0.5F) {
                pose.pushPose();
                pose.translate(cc.x - entPos.x, groundRel, cc.z - entPos.z);
                pose.mulPose(Axis.YP.rotationDegrees(time * 2F));
                VertexConsumer gc = buffers.getBuffer(LdRenderTypes.GLOW);
                LdDraw.cylinderWall(gc, pose.last().pose(), (0.5F + 0.9F * k) * size, hgt, 24, VIOLET, 0.45F * ck * appear, 0F);
                LdDraw.cylinderWall(gc, pose.last().pose(), (0.25F + 0.4F * k) * size, hgt, 16, 0xFFFFFF, 0.35F * ck * appear, 0F);
                pose.popPose();
            }
        }

        // Semente da explosão + luz sendo sugada. O buffer GLOW é pedido DE NOVO: os círculos/coluna acima trocaram o
        // buffer compartilhado do BufferSource, e o "g" antigo já foi fechado ("Not building!" se reutilizado).
        g = buffers.getBuffer(LdRenderTypes.GLOW);
        Vec3 camRel = cam.subtract(entPos);
        Matrix4f m = pose.last().pose();
        LdDraw.glowSphere(g, m, 0.12F + 0.55F * k, 8, 12, 0xFFFFFF, 0.95F * appear, 0.5F * appear, camRel);
        LdDraw.glowSphere(g, m, 0.3F + 1.1F * k, 10, 16, VIOLET, 0.05F, 0.85F * k, camRel);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LdDraw.halo(g, pose.last().pose(), 0.2F, 0.5F + 1.4F * k, 1.6F + 4.5F * k, VIOLET, 0.6F * k * appear, 28);
        pose.popPose();
        RandomSource rnd = RandomSource.create((long) (time / 2) * 7919L + e.getId());
        for (int i = 0; i < 8; i++) {
            Vec3 dir = randomDir(rnd);
            float ph = Mth.frac(time / 10F + i * 0.13F);
            Vec3 a = dir.scale(2F + 7F * (1F - ph) * (0.5F + k));
            Vec3 b = a.scale(0.55F);
            LdDraw.beamTo(g, m, a, b, camRel, 0.05F + 0.05F * k, i % 2 == 0 ? VIOLET : BLUE, 0F, 0.8F * k * appear);
        }
        // Círculo rúnico no chão, girando
        pose.pushPose();
        pose.translate(0, groundRel + 0.06, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-time * 3F));
        LdDraw.texFlat(buffers.getBuffer(LdRenderTypes.additiveTexture(RING)), pose.last().pose(), 3F + 6F * k,
                LdDraw.argb(VIOLET, 0.65F * appear));
        // O círculo do raio da explosão se abrindo devagar no chão (contragiro)
        pose.mulPose(Axis.YP.rotationDegrees(time * 5F));
        LdDraw.texFlat(buffers.getBuffer(LdRenderTypes.additiveTexture(CIRCLE)), pose.last().pose(),
                e.radius() * (0.25F + 0.75F * LdUtil.easeOut(k)), LdDraw.argb(BLUE, 0.4F * appear));
        pose.popPose();
    }

    // =====================================================================================
    //  Detonação
    // =====================================================================================
    private void renderBlast(AtomicNovaEntity e, float ageF, float time, Vec3 camRel, double groundRel, PoseStack pose,
                             MultiBufferSource buffers) {
        float cur = e.blastRadius(ageF);
        float total = e.blastAlpha(ageF);
        float a = e.domeAlpha(ageF);
        float t = ageF - AtomicNovaEntity.CHARGE;
        if (total <= 0.01F) return;
        Matrix4f m = pose.last().pose();
        renderCloud(e, ageF, time, camRel, groundRel, pose, buffers, total);
        if (a <= 0.01F) {
            renderShock(t, cur, groundRel, pose, buffers, total);
            return;
        }
        int lat = cur > 20 ? 18 : 14, lon = cur > 20 ? 32 : 24;

        // Negativo: os primeiros instantes invertem as cores de tudo dentro da esfera
        if (t < 6F) {
            VertexConsumer inv = buffers.getBuffer(LdRenderTypes.INVERT);
            LdDraw.flatSphere(inv, m, cur, lat, lon, 0xFFFFFFFF);
            if (camRel.lengthSqr() < (double) cur * cur) {
                pose.pushPose();
                pose.translate(camRel.x, camRel.y, camRel.z);
                LdDraw.screenQuad(buffers.getBuffer(LdRenderTypes.INVERT_SCREEN), pose.last().pose(), entityRenderDispatcher.camera, 0xFFFFFFFF);
                pose.popPose();
            }
        }

        VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
        LdDraw.glowSphere(g, m, cur * 0.45F, lat, lon, 0xFFFFFF, 0.85F * a, 0.3F * a, camRel);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 4F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.02F) % 1F, (time * 0.03F) % 1F));
        LdDraw.texSphere(sw, pose.last(), cur * 0.9F, lat, lon, LdDraw.swirl(VIOLET, a), 3F, 0.04F, time);
        pose.mulPose(Axis.ZP.rotationDegrees(30F));
        pose.mulPose(Axis.YP.rotationDegrees(-time * 7F));
        VertexConsumer sw2 = buffers.getBuffer(RenderType.energySwirl(SWIRL, (-time * 0.03F) % 1F, (time * 0.015F) % 1F));
        LdDraw.texSphere(sw2, pose.last(), cur * 0.97F, lat, lon, LdDraw.swirl(LdDraw.lighten(BLUE, 0.2F), 0.75F * a), 2F, 0.06F, time + 30F);
        pose.popPose();

        g = buffers.getBuffer(LdRenderTypes.GLOW);
        LdDraw.glowSphere(g, m, cur, lat, lon, LdDraw.lighten(VIOLET, 0.3F), 0.04F * a, 0.95F * a, camRel);
        // Aurora externa (camada azul, um pouco maior e atrasada)
        float aur = cur * (1.04F + 0.04F * Mth.sin(time * 0.3F));
        LdDraw.glowSphere(g, m, aur, lat, lon, BLUE, 0.0F, 0.45F * a * Mth.clamp(t / 6F, 0F, 1F), camRel);
        // "Saia" achatada rente ao chão: a cúpula se espalhando pelo terreno
        pose.pushPose();
        pose.translate(0, groundRel, 0);
        pose.scale(1.25F, 0.22F, 1.25F);
        Vec3 skirtCam = new Vec3(camRel.x / 1.25, (camRel.y - groundRel) / 0.22, camRel.z / 1.25);
        LdDraw.glowSphere(g, pose.last().pose(), cur, 10, lon, LdDraw.lighten(BLUE, 0.2F), 0.12F * a, 0.7F * a, skirtCam);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LdDraw.halo(g, pose.last().pose(), cur * 0.9F, cur * 1.05F, cur * 1.8F, VIOLET, 0.55F * a, 48);
        pose.popPose();

        // Raios de luz saindo da esfera
        RandomSource rnd = RandomSource.create(e.getId() * 31L);
        for (int i = 0; i < 14; i++) {
            Vec3 dir = randomDir(rnd).yRot(time * 0.004F);
            float len = cur * (1.4F + 0.6F * rnd.nextFloat());
            LdDraw.beamTo(g, m, dir.scale(cur * 0.3F), dir.scale(len), camRel, 0.25F + cur * 0.03F,
                    i % 3 == 0 ? 0xFFFFFF : VIOLET, 0.55F * a, 0F);
        }

        renderShock(t, cur, groundRel, pose, buffers, total);
    }

    /** Três anéis de choque correndo pelo chão (o último bem depois, mais fino) + disco de poeira luminosa. */
    private void renderShock(float t, float cur, double groundRel, PoseStack pose, MultiBufferSource buffers, float a) {
        VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, groundRel + 0.3, 0);
        float run = Math.max(0F, t - AtomicNovaEntity.EXPAND);
        float shock = cur * 1.2F + run * 0.9F;
        Matrix4f m = pose.last().pose();
        LdDraw.ringFlat(g, m, shock, 1.2F + cur * 0.1F, 0xFFFFFF, 0.8F * a, 64);
        LdDraw.ringFlat(g, m, shock * 0.9F, 0.8F + cur * 0.06F, VIOLET, 0.6F * a, 64);
        float second = Mth.clamp((t - 6F) / 6F, 0F, 1F);
        if (second > 0F) LdDraw.ringFlat(g, m, cur * 0.8F + run * 1.4F, 0.6F + cur * 0.04F, BLUE, 0.55F * a * second, 64);
        float third = Mth.clamp((t - AtomicNovaEntity.EXPAND) / 8F, 0F, 1F) * Mth.clamp(1F - run / 60F, 0F, 1F);
        if (third > 0F) LdDraw.ringFlat(g, m, cur * 1.05F + run * 2.2F, 0.4F, 0xE8DEFF, 0.45F * a * third, 64);
        float dust = Mth.clamp(1F - run / 30F, 0F, 1F);
        if (dust > 0F) LdDraw.radialFlat(g, m, cur * 0.6F, shock, LdDraw.argb(VIOLET, 0F), LdDraw.argb(VIOLET, 0.25F * a * dust), 48);
        pose.popPose();
    }

    /** Nuvem em cogumelo luminosa: coluna que sobe do chão e o chapéu que se abre girando, com um anel em volta. */
    private void renderCloud(AtomicNovaEntity e, float ageF, float time, Vec3 camRel, double groundRel, PoseStack pose,
                             MultiBufferSource buffers, float a) {
        float rise = e.cloudRise(ageF);
        if (rise <= 0.01F) return;
        float r = e.radius();
        float stemH = r * (0.6F + 1.1F * rise);
        float alpha = a * Mth.clamp(rise * 3F, 0F, 1F);
        VertexConsumer g = buffers.getBuffer(LdRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, groundRel, 0);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.2F));
        LdDraw.cylinderWall(g, pose.last().pose(), r * 0.16F, stemH, 24, VIOLET, 0.55F * alpha, 0.25F * alpha);
        LdDraw.cylinderWall(g, pose.last().pose(), r * 0.09F, stemH, 16, 0xFFFFFF, 0.35F * alpha, 0.1F * alpha);
        pose.popPose();
        // Chapéu
        float capR = r * (0.35F + 0.45F * rise);
        double capY = groundRel + stemH;
        pose.pushPose();
        pose.translate(0, capY, 0);
        pose.scale(1F, 0.45F, 1F);
        Vec3 capCam = new Vec3(camRel.x, (camRel.y - capY) / 0.45, camRel.z);
        LdDraw.glowSphere(g, pose.last().pose(), capR, 12, 24, LdDraw.lighten(VIOLET, 0.15F), 0.18F * alpha, 0.8F * alpha, capCam);
        pose.mulPose(Axis.YP.rotationDegrees(-time * 3F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.01F) % 1F, (time * 0.02F) % 1F));
        LdDraw.texSphere(sw, pose.last(), capR * 0.92F, 12, 24, LdDraw.swirl(BLUE, 0.7F * alpha), 3F, 0.08F, time);
        pose.popPose();
        // Anel em volta do chapéu
        g = buffers.getBuffer(LdRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, capY - capR * 0.12F, 0);
        LdDraw.ringFlat(g, pose.last().pose(), capR * 1.25F, capR * 0.12F, 0xE8DEFF, 0.5F * alpha, 48);
        pose.popPose();
    }

    private static Vec3 randomDir(RandomSource rnd) {
        double z = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
    }

    @Override
    public ResourceLocation getTextureLocation(AtomicNovaEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
