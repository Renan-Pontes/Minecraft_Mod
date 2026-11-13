package com.renanpontes.isekaimahou.feature.legends_g.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_g.entity.GrimoireEntity;

import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Grim: o livro da mesa de encantamentos do vanilla, tingido de carmim, flutuando e folheando as próprias páginas.
 * Quando "come" uma magia, a capa bate como uma boca (abre e fecha rápido). Brilha no escuro (é um demônio).
 */
public class GrimoireRenderer extends EntityRenderer<GrimoireEntity> {
    private static final int TINT = 0xFFC0506A;
    private final BookModel book;

    public GrimoireRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.book = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(GrimoireEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float rot = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.pushPose();
        ps.translate(0, 0.2 + Mth.sin(t * 0.1F) * 0.03F, 0);
        // as páginas abertas olham para onde o dono olha
        ps.mulPose(Axis.YP.rotationDegrees(-(rot + 90F)));
        ps.mulPose(Axis.ZP.rotationDegrees(75F));
        float flip = t * 0.04F;
        float f1 = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0F, 1F);
        float f2 = Mth.clamp(Mth.frac(flip + 0.75F) * 1.6F - 0.3F, 0F, 1F);
        long bite = e.sinceBite();
        float open = bite >= 0 && bite < 12 ? Math.abs(Mth.sin((bite + pt) * 0.8F)) : 0.85F + 0.1F * Mth.sin(t * 0.15F);
        book.setupAnim(t, f1, f2, open);
        VertexConsumer vc = EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid);
        book.render(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, TINT);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GrimoireEntity e) {
        return EnchantTableRenderer.BOOK_LOCATION.texture();
    }
}
