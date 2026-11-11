package com.renanpontes.isekaimahou.feature.legends_a.client;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ConjuredBladeEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha a arma conjurada com o MODELO do item (espada, tridente, machado...), com a ponta apontando para onde ela
 * voa (ou cravada no chão), um halo da cor da magia e, ao brotar do Campo de Lâminas, um pequeno círculo no chão.
 * Brota subindo do chão, gira devagar no ar e some encolhendo.
 */
public class ConjuredBladeRenderer extends EntityRenderer<ConjuredBladeEntity> {
    private final ItemRenderer items;

    public ConjuredBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public void render(ConjuredBladeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        ItemStack stack = e.item();
        if (stack.isEmpty()) return;
        byte mode = e.mode();
        float scale = e.scale();
        float age = e.tickCount + pt;

        Vec3 d = e.prevRenderDir.lerp(e.renderDir, pt);
        if (d.lengthSqr() < 1.0E-6) d = e.dir();
        d = d.normalize();

        float sink = 0;
        float grow = 1;
        if (mode == ConjuredBladeEntity.RISING) {
            // Sobe de dentro do chão nos primeiros ticks
            float t = e.modeProgress(pt, ConjuredBladeEntity.RISE_TICKS);
            sink = (1 - t * t) * 0.9F * scale;
        } else if (mode == ConjuredBladeEntity.FADE) {
            float t = e.modeProgress(pt, ConjuredBladeEntity.FADE_TICKS);
            grow = 1 - t;
            sink = t * 0.3F;
        }
        if (grow <= 0.01F) return;

        // Comprimento da lâmina a partir do centro do item: cravada, a ponta fica ~0,25 bloco dentro do chão
        float half = 0.62F * scale;
        boolean planted = mode != ConjuredBladeEntity.FLY;

        ps.pushPose();
        if (planted) ps.translate(-d.x * (half - 0.25F), -d.y * (half - 0.25F) - sink, -d.z * (half - 0.25F));

        // Halo da cor da magia (atrás da lâmina, virado para a câmera)
        int rgb = e.color();
        if (mode == ConjuredBladeEntity.FLY || mode == ConjuredBladeEntity.RISING) {
            ps.pushPose();
            ps.mulPose(entityRenderDispatcher.cameraOrientation());
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.6F);
            LegendQuads.disc(ps, LegendQuads.additive(buffers, LegendQuads.GLOW), 0.75F * scale * pulse * grow, age * 4, rgb, 0.55F * grow, true);
            ps.popPose();
        }

        // Alinha +Y (a ponta, depois do giro de -45° do sprite) com a direção
        ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) d.x, (float) d.y, (float) d.z));
        if (mode == ConjuredBladeEntity.FLY) ps.mulPose(Axis.YP.rotationDegrees(age * 18));
        else ps.mulPose(Axis.YP.rotationDegrees((e.getId() * 47) % 360));
        ps.mulPose(Axis.ZP.rotationDegrees(-45));
        float s = scale * grow;
        ps.scale(s, s, s);
        items.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffers,
                e.level(), e.getId());
        ps.popPose();

        // Círculo no chão onde a espada brotou
        if (mode == ConjuredBladeEntity.RISING) {
            float t = e.modeProgress(pt, 20);
            ps.pushPose();
            ps.translate(0, 0.03F, 0);
            LegendQuads.flat(ps, LegendQuads.glow(buffers, LegendQuads.SIGIL), 0.9F * scale * (0.6F + 0.4F * t), age * 3, rgb,
                    0.8F * (1 - t * 0.6F));
            ps.popPose();
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ConjuredBladeEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
