package com.renanpontes.isekaimahou.feature.settlements.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.settlements.entity.SiteSign;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Letreiro da obra: título na cor do estilo e uma barra de progresso de blocos de texto, sempre virada para a câmera. */
public class SiteSignRenderer extends EntityRenderer<SiteSign> {
    private static final int SEGMENTS = 20;
    private static final int FULL_BRIGHT = 0xF000F0;

    public SiteSignRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SiteSign sign, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        if (entityRenderDispatcher.distanceToSqr(sign) > 64 * 64) return;
        Font font = getFont();
        Component title = sign.title();
        int pct = Math.round(sign.progress() * 100);
        int filled = Math.round(sign.progress() * SEGMENTS);
        MutableComponent bar = Component.literal("■".repeat(filled)).withColor(0x7DDA6B)
                .append(Component.literal("■".repeat(SEGMENTS - filled)).withColor(0x3A3226))
                .append(Component.literal("  " + pct + "%").withColor(0xF3E6C8));
        ps.pushPose();
        ps.translate(0.0, 0.5, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.03F, -0.03F, 0.03F);
        Matrix4f m = ps.last().pose();
        int bg = 0x99140E09;
        font.drawInBatch(title, -font.width(title) / 2.0F, -10, 0xFF000000 | sign.color(), false, m, buffers,
                Font.DisplayMode.NORMAL, bg, FULL_BRIGHT);
        font.drawInBatch(bar, -font.width(bar) / 2.0F, 2, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, bg, FULL_BRIGHT);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SiteSign sign) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
