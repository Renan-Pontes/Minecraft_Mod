package com.renanpontes.isekaimahou.client.status;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Tinta das condições sobre o próprio modelo da criatura: o modelo é desenhado de novo, translúcido e colorido, por
 * cima do normal (azulado no Gelado, cinza de pedra no Enrijecendo, brasa no Em Chamas, faíscas no Eletrizado,
 * roxo no Corrompido). Vale para os renderizadores do vanilla/mods ({@link RenderLayer}) e para os GeckoLib
 * ({@link GeoRenderLayer}). Congelado e Petrificado já têm a casca de gelo/pedra e não levam tinta.
 */
final class StatusTints {

    /** Cor ARGB da tinta da entidade agora, ou 0 (sem tinta). */
    static int tint(LivingEntity e) {
        if (e.isInvisible()) return 0;
        int chilled = 0, stiff = 0, burning = 0, shocked = 0;
        boolean corrupted = false;
        for (ClientStatuses.Entry en : ClientStatuses.get(e)) {
            switch (en.kind()) {
                case FROZEN -> { return 0; }
                case PETRIFIED -> {
                    if (en.stacks() >= 3) return 0;
                    stiff = en.stacks();
                }
                case CHILLED -> chilled = en.stacks();
                case BURNING -> burning = en.stacks();
                case SHOCKED -> shocked = en.stacks();
                case CORRUPTED -> corrupted = true;
                default -> { }
            }
        }
        float t = (e.tickCount % 1000) + 0.0F;
        if (stiff > 0) return argb(0.28F + 0.17F * stiff, 0x8C8A86);
        if (chilled > 0) return argb(0.16F + 0.07F * chilled, 0x8FD8FF);
        if (burning > 0) return argb((0.14F + 0.04F * burning) * (0.8F + 0.2F * Mth.sin(t * 0.7F)), 0xFF7A2A);
        if (shocked > 0) return (e.tickCount + e.getId()) % 5 < 2 ? argb(0.18F + 0.08F * shocked, 0xB8F4FF) : 0;
        if (corrupted) return argb(0.22F, 0x7A3AA8);
        return 0;
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        return a <= 2 ? 0 : (a << 24) | rgb;
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    /** Renderizadores do vanilla e de mods comuns (modelos {@link EntityModel}), inclusive as skins de jogador. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> r = event.getRenderer((EntityType) type);
            if (r instanceof LivingEntityRenderer lr) lr.addLayer(new VanillaLayer(lr));
        }
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<?> r = event.getSkin(skin);
            if (r instanceof LivingEntityRenderer lr) lr.addLayer(new VanillaLayer(lr));
        }
    }

    /** GeckoLib: a camada entra quando o renderizador monta as camadas (antes do 1º desenho). */
    static void addGeoLayer(GeoRenderEvent.Entity.CompileRenderLayers event) {
        GeoRenderer<?> r = event.getRenderer();
        if (r instanceof GeoEntityRenderer<?> ger) attach(event, ger);
    }

    private static <T extends Entity & GeoAnimatable> void attach(GeoRenderEvent.Entity.CompileRenderLayers event, GeoEntityRenderer<T> r) {
        event.addLayer(new GeoLayer<>(r));
    }

    // =====================================================================================
    //  Camadas
    // =====================================================================================
    private static final class VanillaLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        VanillaLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int tint = tint(e);
            if (tint == 0) return;
            ResourceLocation tex;
            try {
                tex = getTextureLocation(e);
            } catch (RuntimeException ex) {
                return;
            }
            if (tex == null) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
            getParentModel().renderToBuffer(ps, vc, light, LivingEntityRenderer.getOverlayCoords(e, 0F), tint);
        }
    }

    private static final class GeoLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
        GeoLayer(GeoRenderer<T> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack ps, T animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            if (!(animatable instanceof LivingEntity le)) return;
            int tint = tint(le);
            if (tint == 0) return;
            ResourceLocation tex = getTextureResource(animatable);
            if (tex == null) return;
            RenderType rt = RenderType.entityTranslucent(tex);
            try {
                getRenderer().reRender(model, ps, buffers, animatable, rt, buffers.getBuffer(rt), partialTick, light,
                        OverlayTexture.NO_OVERLAY, tint);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.debug("[status] tinta GeckoLib falhou em {}", animatable, ex);
            }
        }
    }

    private StatusTints() {}
}
