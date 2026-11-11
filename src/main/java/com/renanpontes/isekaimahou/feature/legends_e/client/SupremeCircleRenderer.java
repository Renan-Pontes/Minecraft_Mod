package com.renanpontes.isekaimahou.feature.legends_e.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_e.entity.SupremeCircleEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Os círculos da Magia de Nível Supremo, montados em camadas ao longo dos 8 s: anel externo de runas (20 blocos),
 * anel do meio com a estrela de 12 pontas girando ao contrário, selo central, colunas de luz na borda, seis círculos
 * pequenos em pé em volta do conjurador, o DOMO de faixas de runas e, no fim, um círculo enorme no céu sobre o alvo
 * ligado ao domo por um fio de luz. Liberação: tudo clareia e se expande. Contrariada: os círculos estilhaçam em cacos
 * avermelhados que caem.
 */
public class SupremeCircleRenderer extends EntityRenderer<SupremeCircleEntity> {
    private static final int BASE = 0xBFE3FF;
    private static final float R = SupremeCircleEntity.RADIUS;
    private static final float DOME_H = 0.5F;

    public SupremeCircleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SupremeCircleEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        int st = e.state();
        float stAge = Math.max(0F, ageF - e.stateAge());
        float p = st == SupremeCircleEntity.CHANNELING ? e.progress(ageF) : 1F;
        float fade, flare = 0F;
        switch (st) {
            case SupremeCircleEntity.UNLEASHED -> {
                fade = Mth.clamp(1F - stAge / SupremeCircleEntity.UNLEASH_LIFE, 0F, 1F);
                flare = Mth.clamp(1F - stAge / 12F, 0F, 1F);
            }
            case SupremeCircleEntity.SHATTERED, SupremeCircleEntity.FIZZLED -> {
                // Pisca e apaga em poucos ticks; os cacos seguem caindo
                fade = Mth.clamp(1F - stAge / 8F, 0F, 1F) * ((int) stAge % 2 == 0 ? 1F : 0.4F);
            }
            default -> fade = 1F;
        }
        if (e.instant() && st == SupremeCircleEntity.CHANNELING) p = Mth.clamp(p * 1.4F, 0F, 1F);
        int rgb = LeDraw.mix(BASE, e.color(), 0.25F);
        int accent = LeDraw.lighten(e.color(), 0.35F);
        float pulse = 0.85F + 0.15F * Mth.sin(ageF * (0.25F + 0.5F * p));
        float k = (0.65F + 0.35F * p) * pulse * fade;
        float grow = 1F + 0.12F * (1F - flare) * (st == SupremeCircleEntity.UNLEASHED ? Math.min(1F, stAge / 10F) : 0F);

        float outer = LeDraw.smooth(p / 0.12F);
        float mid = LeDraw.smooth((p - 0.08F) / 0.14F);
        float core = LeDraw.smooth((p - 0.18F) / 0.14F);
        float crown = LeDraw.smooth((p - 0.14F) / 0.2F);
        float dome = LeDraw.smooth((p - 0.32F) / 0.3F);
        float sky = LeDraw.smooth((p - 0.48F) / 0.3F);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(lerpPos(e, pt));

        if (fade > 0.01F) {
            // ---------------- Camadas texturizadas ----------------
            int white = LeDraw.lighten(rgb, 0.5F + 0.5F * flare);
            flat(pose, buffers, LeRenderTypes.RING_OUTER, 0.06F, ageF * 0.35F, R * outer * grow, LeDraw.argb(white, outer * 0.95F * k));
            flat(pose, buffers, LeRenderTypes.RING_MID, 0.10F, -ageF * 0.6F, 13.5F * mid * grow, LeDraw.argb(white, mid * 0.9F * k));
            flat(pose, buffers, LeRenderTypes.SIGIL_CORE, 0.14F, ageF * 1.3F, 7F * core * grow, LeDraw.argb(accent, core * k));
            // segundo nível: anel do meio de novo, mais alto e menor, girando no outro sentido
            flat(pose, buffers, LeRenderTypes.RING_MID, 1.4F, ageF * 0.9F, 9F * crown * grow, LeDraw.argb(white, crown * 0.55F * k));

            // Domo: faixas de runas em latitudes crescentes, girando em sentidos alternados
            VertexConsumer band = buffers.getBuffer(LeRenderTypes.additive(LeRenderTypes.RUNE_BAND));
            for (int i = 1; i <= 4; i++) {
                float d = LeDraw.smooth((p - 0.3F - i * 0.07F) / 0.18F) * dome;
                if (d <= 0.01F) continue;
                double th = Math.toRadians(i * 17.0);
                float r = (float) (R * Math.cos(th)) * grow;
                float y = (float) (R * Math.sin(th) * DOME_H);
                float h = 1.5F - i * 0.18F;
                pose.pushPose();
                pose.translate(0, y, 0);
                pose.mulPose(Axis.YP.rotationDegrees((i % 2 == 0 ? -1 : 1) * ageF * (0.5F + i * 0.15F)));
                LeDraw.texBand(band, pose.last().pose(), r, -h * 0.5F, h * 0.5F, 64, Math.max(3F, r / 2.2F), 0F,
                        LeDraw.argb(white, d * 0.85F * k));
                pose.popPose();
            }
            // selo no topo do domo, olhando para baixo
            float apex = (float) (R * Math.sin(Math.toRadians(80)) * DOME_H) + 0.6F;
            flat(pose, buffers, LeRenderTypes.SIGIL_CORE, apex, -ageF * 1.8F, 3.4F * dome, LeDraw.argb(accent, dome * k));

            // Seis círculos pequenos em pé em volta do conjurador
            VertexConsumer small = buffers.getBuffer(LeRenderTypes.additive(LeRenderTypes.SIGIL_CORE));
            for (int i = 0; i < 6; i++) {
                float a = i * 60F + ageF * 0.8F;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(a));
                pose.translate(0, 1.7F + 0.15F * Mth.sin(ageF * 0.1F + i), 4.2F);
                pose.mulPose(Axis.ZP.rotationDegrees(ageF * 2.5F * (i % 2 == 0 ? 1 : -1)));
                LeDraw.texUpright(small, pose.last().pose(), 1.3F * crown, LeDraw.argb(accent, crown * 0.9F * k));
                pose.popPose();
            }

            // Círculo no céu sobre o alvo
            Vec3 off = e.targetOffset();
            if (sky > 0.01F) {
                float skyY = (float) off.y + 40F;
                pose.pushPose();
                pose.translate(off.x, skyY, off.z);
                float sg = sky * (1F + 0.25F * flare);
                flatHere(pose, buffers, LeRenderTypes.RING_OUTER, ageF * -0.4F, 26F * sg, LeDraw.argb(white, sky * k));
                flatHere(pose, buffers, LeRenderTypes.RING_MID, ageF * 0.7F, 17F * sg, LeDraw.argb(white, sky * 0.9F * k));
                flatHere(pose, buffers, LeRenderTypes.SIGIL_CORE, ageF * -1.2F, 8F * sg, LeDraw.argb(accent, sky * k));
                pose.popPose();
            }

            // ---------------- Brilho (geometria aditiva) ----------------
            VertexConsumer g = buffers.getBuffer(LeRenderTypes.GLOW);
            Matrix4f m = pose.last().pose();
            pose.pushPose();
            pose.translate(0, 0.04, 0);
            Matrix4f gm = pose.last().pose();
            LeDraw.radialFlat(g, gm, 0F, R * 1.05F * outer * grow, LeDraw.argb(rgb, 0.22F * k + 0.5F * flare), LeDraw.argb(rgb, 0F), 48);
            LeDraw.ringFlat(g, gm, R * outer * grow, 0.45F, LeDraw.lighten(rgb, 0.5F), 0.8F * outer * k, 96);
            pose.popPose();
            // Colunas de luz na borda
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45 + ageF * 0.35);
                float h = (3F + 7F * p) * outer;
                Vec3 base = new Vec3(Math.cos(a) * R * 0.97 * grow, 0.05, Math.sin(a) * R * 0.97 * grow);
                Vec3 top = base.add(0, h, 0);
                float al = 0.45F * outer * k * (0.7F + 0.3F * Mth.sin(ageF * 0.4F + i));
                LeDraw.softRibbon(g, m, base, top, new Vec3(1, 0, 0), 0.45F, 0.1F, LeDraw.lighten(rgb, 0.4F), al, 0F);
                LeDraw.softRibbon(g, m, base, top, new Vec3(0, 0, 1), 0.45F, 0.1F, LeDraw.lighten(rgb, 0.4F), al, 0F);
            }
            // Pilar de luz em volta do conjurador
            float colH = (2F + 9F * p) * crown;
            LeDraw.cylinder(g, m, 1.1F + 0.3F * p, 0F, colH, 24, LeDraw.lighten(rgb, 0.3F), 0.06F * k, 0.45F * k, cam, 0F);
            // Fio de luz do topo do domo até o círculo do céu
            if (sky > 0.3F) {
                Vec3 a0 = new Vec3(0, apex, 0);
                Vec3 a1 = new Vec3(off.x, off.y + 40, off.z);
                Vec3 side = LeDraw.facingSide(a0, a1, cam);
                float al = (sky - 0.3F) / 0.7F * k;
                LeDraw.softRibbon(g, m, a0, a1, side, 0.35F + 1.2F * flare, 0.6F + 2F * flare, LeDraw.lighten(rgb, 0.6F), 0.7F * al, 0.7F * al);
            }
            // Clarão da liberação
            if (flare > 0.01F) {
                pose.pushPose();
                pose.translate(0, 0.3, 0);
                Matrix4f fm = pose.last().pose();
                float rr = R * (1F + (1F - flare) * 0.8F);
                LeDraw.ringFlat(g, fm, rr, 1.4F, 0xFFFFFF, flare, 96);
                LeDraw.radialFlat(g, fm, 0F, rr, LeDraw.argb(0xFFFFFF, 0.5F * flare), LeDraw.argb(rgb, 0F), 48);
                pose.popPose();
            }
        }

        if (st == SupremeCircleEntity.SHATTERED || st == SupremeCircleEntity.FIZZLED) shards(e, pose, buffers, stAge, st == SupremeCircleEntity.SHATTERED);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private static Vec3 lerpPos(SupremeCircleEntity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** Quad texturizado deitado na altura {@code y}, girado {@code rot} graus. */
    private static void flat(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float y, float rot, float half, int argb) {
        if (half <= 0.01F || (argb >>> 24) == 0) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        flatHere(pose, buffers, tex, rot, half, argb);
        pose.popPose();
    }

    private static void flatHere(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float rot, float half, int argb) {
        if (half <= 0.01F || (argb >>> 24) == 0) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(rot));
        LeDraw.texFlat(buffers.getBuffer(LeRenderTypes.additive(tex)), pose.last().pose(), half, argb);
        pose.popPose();
    }

    /** Cacos dos círculos voando para fora e caindo (avermelhados se contrariada, cinzentos se a magia falhou). */
    private static void shards(SupremeCircleEntity e, PoseStack pose, MultiBufferSource buffers, float t, boolean countered) {
        float fade = Mth.clamp(1F - t / SupremeCircleEntity.BREAK_LIFE, 0F, 1F);
        if (fade <= 0) return;
        VertexConsumer g = buffers.getBuffer(LeRenderTypes.GLOW);
        RandomSource rnd = RandomSource.create(e.getId() * 31L + 7L);
        int color = countered ? 0xFF8A9A : 0x9AA8C0;
        for (int i = 0; i < 64; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r0 = 2 + rnd.nextDouble() * (R - 2);
            double v = 0.15 + rnd.nextDouble() * 0.35;
            double vy = 0.1 + rnd.nextDouble() * 0.35;
            double r = r0 + v * t;
            double y = 0.2 + (i % 5 == 0 ? 6 : 0) + vy * t - 0.018 * t * t;
            float size = 0.4F + rnd.nextFloat() * 1.1F;
            pose.pushPose();
            pose.translate(Math.cos(a) * r, Math.max(0.05, y), Math.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(rnd.nextFloat() * 360F + t * 12F));
            pose.mulPose(Axis.XP.rotationDegrees(t * (6F + rnd.nextFloat() * 20F)));
            LeDraw.shard(g, pose.last().pose(), size, LeDraw.argb(LeDraw.mix(color, 0xFFFFFF, rnd.nextFloat() * 0.5F), 0.85F * fade));
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRender(SupremeCircleEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SupremeCircleEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
