package com.renanpontes.isekaimahou.feature.class_gambler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.CasinoDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GamblerDiceEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.IdleDeathDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.RouletteWheelEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.SlotMachineEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderers GeckoLib das mesas do Apostador. As partes que dependem do resultado (face do dado, roda e bolinha da
 * roleta, rolos do caça-níquel) são giradas no modelo ({@code setCustomAnimations}) a partir dos dados sincronizados;
 * o resto (brilho, alavanca, cartas orbitando) vem dos arquivos de animação.
 */
public final class GbGeoRenderers {
    private static final float TAU = Mth.TWO_PI;

    private static void rot(DefaultedEntityGeoModel<?> model, String bone, float x, float y, float z) {
        GeoBone b = model.getBone(bone).orElse(null);
        if (b != null) {
            b.setRotX(x);
            b.setRotY(y);
            b.setRotZ(z);
        }
    }

    private static void hide(DefaultedEntityGeoModel<?> model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }

    private static float ease(float x) {
        float c = Mth.clamp(x, 0F, 1F);
        return 1F - (1F - c) * (1F - c) * (1F - c);
    }

    // =====================================================================================
    //  Dado
    // =====================================================================================
    public static class DiceRenderer extends GeoEntityRenderer<GamblerDiceEntity> {
        /** Giro (x, y, z) que leva a face N para cima; a "placa" da face garante o número certo em cima. */
        private static final float[][] FACE_UP = {
                {0, 0, 0}, {0, 0, 0}, {Mth.HALF_PI, 0, 0}, {0, 0, -Mth.HALF_PI}, {0, 0, Mth.HALF_PI}, {-Mth.HALF_PI, 0, 0}, {Mth.PI, 0, 0}};

        public DiceRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("gambler_dice")) {
                @Override
                public void setCustomAnimations(GamblerDiceEntity e, long id, AnimationState<GamblerDiceEntity> state) {
                    super.setCustomAnimations(e, id, state);
                    int face = e.face();
                    if (face == 0) {
                        float t = e.tickCount + state.getPartialTick();
                        rot(this, "die", t * 0.55F, t * 0.21F, t * 0.43F);
                    } else {
                        float[] r = FACE_UP[Mth.clamp(face, 1, 6)];
                        rot(this, "die", r[0], r[1], r[2]);
                    }
                    for (int k = 1; k <= 6; k++) hide(this, "plate" + k, face != k);
                }
            });
            this.shadowRadius = 0.2F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            withScale(1.4F);
        }
    }

    // =====================================================================================
    //  Roleta
    // =====================================================================================
    public static class RouletteRenderer extends GeoEntityRenderer<RouletteWheelEntity> {
        /** Voltas da roda até parar e voltas extras da bolinha (ao contrário). */
        private static final float WHEEL_TURNS = 3F * TAU;
        private static final float BALL_TURNS = 5F * TAU;

        public RouletteRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("roulette_wheel")) {
                @Override
                public void setCustomAnimations(RouletteWheelEntity e, long id, AnimationState<RouletteWheelEntity> state) {
                    super.setCustomAnimations(e, id, state);
                    float t = e.tickCount + state.getPartialTick();
                    float k = ease(t / RouletteWheelEntity.SPIN_TICKS);
                    float pocket = RouletteWheelEntity.pocketIndex(e.result()) * TAU / RouletteWheelEntity.WHEEL.length;
                    rot(this, "wheel", 0, WHEEL_TURNS * k, 0);
                    // a bolinha é filha da roda: no fim fica na casa sorteada; antes corre ao contrário
                    rot(this, "ballOrbit", 0, pocket + (BALL_TURNS + WHEEL_TURNS) * (1F - k), 0);
                }
            });
            this.shadowRadius = 0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void render(RouletteWheelEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.tickCount + pt;
            float s = 1.3F * Math.min(1F, t / 6F);
            this.scaleWidth = s;
            this.scaleHeight = s;
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }

    // =====================================================================================
    //  Caça-Níquel
    // =====================================================================================
    public static class SlotMachineRenderer extends GeoEntityRenderer<SlotMachineEntity> {
        public SlotMachineRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("slot_machine")) {
                @Override
                public void setCustomAnimations(SlotMachineEntity e, long id, AnimationState<SlotMachineEntity> state) {
                    super.setCustomAnimations(e, id, state);
                    float t = e.tickCount + state.getPartialTick();
                    for (int i = 0; i < 3; i++) {
                        boolean spinning = t < SlotMachineEntity.STOP[i];
                        rot(this, "reel" + i, spinning ? t * (1.1F + i * 0.13F) : 0F, 0, 0);
                        int sym = e.reel(i);
                        for (int k = 0; k < 4; k++) hide(this, "sym" + i + "_" + k, spinning || sym != k);
                    }
                }
            });
            this.shadowRadius = 0.6F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void render(SlotMachineEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.tickCount + pt;
            float grow = Math.min(1F, t / 5F);
            float shrink = Math.min(1F, Math.max(0F, (62F - t) / 5F));
            float s = Math.max(0.01F, Math.min(grow, shrink));
            this.scaleWidth = s;
            this.scaleHeight = s;
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }

    // =====================================================================================
    //  Cassino (domínio)
    // =====================================================================================
    public static class CasinoRenderer extends GeoEntityRenderer<CasinoDomainEntity> {
        /** Raio do salão no modelo (blocos): a escala leva até o raio real do domínio. */
        private static final float MODEL_RADIUS = 2F;

        public CasinoRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("casino_domain")) {
                @Override
                public void setCustomAnimations(CasinoDomainEntity e, long id, AnimationState<CasinoDomainEntity> state) {
                    super.setCustomAnimations(e, id, state);
                    float t = e.tickCount + state.getPartialTick();
                    rot(this, "wheelRing", 0, t * 0.02F, 0);
                }
            });
            this.shadowRadius = 0F;
        }

        @Override
        public void render(CasinoDomainEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float s = Math.max(0.01F, e.radius() / MODEL_RADIUS * e.openness(pt));
            this.scaleWidth = s;
            this.scaleHeight = Math.max(0.01F, Math.min(s, e.radius() / MODEL_RADIUS * 0.5F));
            super.render(e, yaw, pt, ps, buffers, LightTexture.FULL_BRIGHT);
        }

        @Override
        public RenderType getRenderType(CasinoDomainEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public boolean shouldShowName(CasinoDomainEntity animatable) {
            return false;
        }
    }

    // =====================================================================================
    //  Domínio da Morte Ociosa (o salão do Cassino com a textura de pachinko em neon, tingido pela cor do Reach)
    // =====================================================================================
    public static class IdleDeathRenderer extends GeoEntityRenderer<IdleDeathDomainEntity> {
        private static final float MODEL_RADIUS = 2F;

        public IdleDeathRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<IdleDeathDomainEntity>(IsekaiMahou.id("casino_domain")) {
                @Override
                public void setCustomAnimations(IdleDeathDomainEntity e, long id, AnimationState<IdleDeathDomainEntity> state) {
                    super.setCustomAnimations(e, id, state);
                    float t = e.tickCount + state.getPartialTick();
                    rot(this, "wheelRing", 0, -t * 0.05F, 0);
                }
            }.withAltTexture(IsekaiMahou.id("idle_death_gamble")));
            this.shadowRadius = 0F;
        }

        @Override
        public void render(IdleDeathDomainEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float s = Math.max(0.01F, e.radius() / MODEL_RADIUS * e.openness(pt));
            this.scaleWidth = s;
            this.scaleHeight = Math.max(0.01F, Math.min(s, e.radius() / MODEL_RADIUS * 0.6F));
            super.render(e, yaw, pt, ps, buffers, LightTexture.FULL_BRIGHT);
        }

        @Override
        public RenderType getRenderType(IdleDeathDomainEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public Color getRenderColor(IdleDeathDomainEntity e, float partialTick, int packedLight) {
            int reach = e.reach();
            if (reach <= 0) return Color.ofRGBA(1F, 1F, 1F, 0.95F);
            int rgb = com.renanpontes.isekaimahou.feature.class_gambler.GbFx.reachColor(reach, e.tickCount);
            float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
            // só tinge um pouco: mistura com branco
            return Color.ofRGBA(0.55F + r * 0.45F, 0.55F + g * 0.45F, 0.55F + b * 0.45F, 0.95F);
        }

        @Override
        public boolean shouldShowName(IdleDeathDomainEntity animatable) {
            return false;
        }
    }

    private GbGeoRenderers() {}
}
