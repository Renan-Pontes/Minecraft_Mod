package com.renanpontes.isekaimahou.feature.fusions_d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.KrakenTentacleEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tentáculo do Kraken: a pose é calculada aqui a partir do estado (sincronizado) e do tempo nele: brota do chão,
 * ondula, recua antes do golpe, DESABA para a frente até deitar no chão, enrola a ponta ao agarrar e afunda no fim.
 * Pintas bioluminescentes brilham no escuro.
 */
public class KrakenTentacleRenderer extends EntityRenderer<KrakenTentacleEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/fd_kraken_tentacle.png");
    private static final RenderType GLOW = RenderType.eyes(IsekaiMahou.id("textures/entity/fd_kraken_tentacle_glow.png"));
    private static final int N = KrakenTentacleModel.SEGMENTS;

    private final KrakenTentacleModel model;
    private final float[] bend = new float[N];
    private final float[] twist = new float[N];

    public KrakenTentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new KrakenTentacleModel(ctx.bakeLayer(KrakenTentacleModel.LAYER));
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(KrakenTentacleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt + e.getId() * 7;
        float sa = e.stateAge() + pt;
        float scale = e.scale();
        float sink = 0F;
        // Curvatura total desejada (radianos) e quanto a ponta enrola
        float total;
        float curl = 0F;
        // 0 = curva distribuída (ondular, agarrar); 1 = dobra concentrada na base (desabar deitado no chão)
        float baseHeavy = 0F;
        switch (e.state()) {
            case KrakenTentacleEntity.EMERGE -> {
                float k = Mth.clamp(sa / KrakenTentacleEntity.EMERGE_TICKS, 0F, 1F);
                sink = (1F - k) * (1F - k);
                total = 0.5F * (1 - k);
            }
            case KrakenTentacleEntity.WINDUP -> total = -0.55F * Mth.clamp(sa / KrakenTentacleEntity.WINDUP_TICKS, 0F, 1F);
            case KrakenTentacleEntity.SLAM -> {
                float k = Mth.clamp(sa / 3F, 0F, 1F);
                float back = Mth.clamp((sa - 6F) / 4F, 0F, 1F);
                // Tomba pela base e deita no chão ao longo da linha marcada (ponta a ~94% do comprimento)
                total = Mth.lerp(k, -0.55F, 1.8F) * (1F - 0.6F * back);
                baseHeavy = k * (1F - back);
            }
            case KrakenTentacleEntity.GRAB -> {
                float k = Mth.clamp(sa / 8F, 0F, 1F);
                total = 0.65F * k;
                curl = 4.2F * k + 0.25F * Mth.sin(t * 0.6F);
            }
            case KrakenTentacleEntity.RETRACT -> {
                float k = Mth.clamp(sa / KrakenTentacleEntity.RETRACT_TICKS, 0F, 1F);
                sink = k * k;
                total = 0.3F;
            }
            default -> total = 0.25F + 0.12F * Mth.sin(t * 0.08F);
        }
        float wsum = 0F, hsum = 0F;
        for (int i = 0; i < N; i++) {
            wsum += 1F - i * 0.06F;
            hsum += (float) Math.exp(-0.5 * i);
        }
        for (int i = 0; i < N; i++) {
            float k = (i + 1) / (float) N;
            // A curva principal pesa mais perto da base (o golpe tomba o tentáculo inteiro); o enrolar, na ponta
            float w = Mth.lerp(baseHeavy, (1F - i * 0.06F) / wsum, (float) Math.exp(-0.5 * i) / hsum);
            float tipCurl = i >= N - 5 ? curl / 5F : 0F;
            bend[i] = total * w + tipCurl + Mth.sin(t * 0.18F + i * 0.7F) * 0.07F * k;
            twist[i] = Mth.cos(t * 0.13F + i * 0.5F) * 0.09F * k;
        }
        model.pose(bend, twist);
        ps.pushPose();
        ps.translate(0, -sink * 5.2F * scale, 0);
        ps.mulPose(Axis.YP.rotation(e.aim()));
        ps.scale(scale, scale, scale);
        ps.scale(-1F, -1F, 1F);
        ps.translate(0, -1.501F, 0);
        model.renderToBuffer(ps, buffers.getBuffer(model.renderType(TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
        model.renderToBuffer(ps, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(KrakenTentacleEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(KrakenTentacleEntity e) {
        return TEXTURE;
    }
}
