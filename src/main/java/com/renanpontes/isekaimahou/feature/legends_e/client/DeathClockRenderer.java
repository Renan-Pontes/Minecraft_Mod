package com.renanpontes.isekaimahou.feature.legends_e.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_e.entity.DeathClockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * O relógio de O Fim de Toda Vida, deitado no céu sobre a área e feito para ser visto DE BAIXO: fundo escuro
 * translúcido, mostrador de numerais romanos, 12 marcadores que ficam vermelhos conforme o ponteiro passa, o ponteiro
 * (lâmina vermelha com contrapeso), aro de runas girando e um pulso a cada sino. Na execução ele brilha e se desfaz.
 */
public class DeathClockRenderer extends EntityRenderer<DeathClockEntity> {
    private static final int RED = 0xFF2A3A;
    private static final int PALE = 0xE8D8FF;

    public DeathClockRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DeathClockEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float pres = e.presence(ageF);
        if (pres <= 0.01F) {
            super.render(e, yaw, pt, pose, buffers, light);
            return;
        }
        float rc = Mth.clamp(e.area() * 0.6F, 6F, 22F);
        float hand = e.handMarks(ageF);
        float sinceBell = (ageF % DeathClockEntity.MARK_TICKS) / DeathClockEntity.MARK_TICKS;
        float bell = ageF < DeathClockEntity.MARK_TICKS || ageF > DeathClockEntity.DURATION + 2 ? 0F : (float) Math.pow(1F - sinceBell, 3);
        float after = Math.max(0F, ageF - DeathClockEntity.DURATION);
        float burst = after > 0 ? Mth.clamp(1F - after / 10F, 0F, 1F) : 0F;
        float grow = 1F + 0.12F * Mth.clamp(after / DeathClockEntity.END_LIFE, 0F, 1F);
        float r = rc * grow * (0.6F + 0.4F * LeDraw.smooth(ageF / 20F));

        pose.pushPose();
        pose.translate(0, e.height(), 0);

        // Fundo escuro (escurece o céu atrás do relógio)
        VertexConsumer dark = buffers.getBuffer(LeRenderTypes.DARK);
        LeDraw.radialFlat(dark, pose.last().pose(), 0F, r * 1.03F, LeDraw.argb(0x0A0006, 0.62F * pres), LeDraw.argb(0x12000A, 0.45F * pres), 64);

        // Mostrador (visto de baixo sem espelhar)
        pose.pushPose();
        pose.translate(0, -0.03, 0);
        int faceCol = LeDraw.mix(0xFFD8E0, RED, burst * 0.7F);
        LeDraw.texFlatBelow(buffers.getBuffer(LeRenderTypes.additive(LeRenderTypes.CLOCK_FACE)), pose.last().pose(), r,
                LeDraw.argb(faceCol, (0.75F + 0.25F * bell) * pres));
        pose.popPose();

        // Aro de runas girando (dá espessura ao relógio)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(ageF * 0.6F));
        LeDraw.texBand(buffers.getBuffer(LeRenderTypes.additive(LeRenderTypes.RUNE_BAND)), pose.last().pose(), r * 1.01F, -0.9F, 0.4F,
                72, Math.max(4F, r / 1.6F), 0F, LeDraw.argb(0xFF6A7A, 0.7F * pres));
        pose.popPose();

        // Marcadores, ponteiro, pulso
        VertexConsumer g = buffers.getBuffer(LeRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, -0.08, 0);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < DeathClockEntity.MARKS; i++) {
            boolean passed = i > 0 ? hand >= i - 0.02F : hand >= DeathClockEntity.MARKS - 0.02F;
            int col = passed ? RED : PALE;
            float al = (passed ? 1F : 0.55F) * pres;
            float big = i % 3 == 0 ? 1.4F : 1F;
            double a = Math.toRadians(i * 30.0);
            Vec3 dir = new Vec3(Math.sin(a), 0, Math.cos(a));
            Vec3 side = new Vec3(dir.z, 0, -dir.x);
            Vec3 p0 = dir.scale(r * 0.66F), p1 = dir.scale(r * 0.79F);
            LeDraw.softRibbon(g, m, p0, p1, side, 0.32F * big, 0.32F * big, col, al, al);
            Vec3 gem = dir.scale(r * 0.85F);
            LeDraw.softRibbon(g, m, gem.subtract(dir.scale(0.5F * big)), gem.add(dir.scale(0.5F * big)), side, 0.5F * big, 0.5F * big, col, al, al);
        }
        // Ponteiro: lâmina que afina até a ponta + contrapeso
        double ha = Math.toRadians(hand * 30.0);
        Vec3 hd = new Vec3(Math.sin(ha), 0, Math.cos(ha));
        Vec3 hs = new Vec3(hd.z, 0, -hd.x);
        Vec3 tip = hd.scale(r * 0.8F), tail = hd.scale(-r * 0.18F);
        int handCol = LeDraw.mix(RED, 0xFFFFFF, 0.25F + 0.5F * bell);
        LeDraw.ribbon(g, m, tail, Vec3.ZERO, hs, 0.55F, 0.75F, LeDraw.argb(handCol, pres), LeDraw.argb(handCol, pres));
        LeDraw.ribbon(g, m, Vec3.ZERO, tip, hs, 0.75F, 0.06F, LeDraw.argb(handCol, pres), LeDraw.argb(0xFFFFFF, pres));
        LeDraw.softRibbon(g, m, tail, tip, hs, 1.8F, 0.4F, RED, 0.35F * pres, 0.2F * pres);
        LeDraw.radialFlat(g, m, 0F, 1.3F, LeDraw.argb(0xFFFFFF, pres), LeDraw.argb(RED, 0.4F * pres), 24);
        // Pulso de cada sino e o clarão final
        if (bell > 0.01F) LeDraw.ringFlat(g, m, r * (1F + 0.12F * (1F - bell)), 0.8F, RED, 0.8F * bell * pres, 96);
        if (burst > 0.01F) {
            LeDraw.radialFlat(g, m, 0F, r * 1.2F, LeDraw.argb(0xFFFFFF, 0.8F * burst), LeDraw.argb(RED, 0F), 64);
            LeDraw.ringFlat(g, m, r * (1F + 0.6F * (1F - burst)), 1.5F, RED, burst, 96);
        }
        pose.popPose();
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(DeathClockEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DeathClockEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
