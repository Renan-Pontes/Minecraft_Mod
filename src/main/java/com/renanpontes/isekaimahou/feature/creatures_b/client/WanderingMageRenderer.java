package com.renanpontes.isekaimahou.feature.creatures_b.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.WanderingMageEntity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Mago Errante: o renderer de sempre + balão de fala sobre a cabeça (mesmo visual dos NPCs da cidade, em tom arcano). */
public class WanderingMageRenderer extends CbRenderer<WanderingMageEntity> {
    private static final int BUBBLE_WIDTH = 160;

    public WanderingMageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, CbRenderer.model("wandering_mage", "wandering_mage"), true, 0.5F, null);
    }

    @Override
    public void render(WanderingMageEntity mage, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(mage, yaw, partialTick, ps, buffers, light);
        Component text = mage.speech();
        if (text == null || entityRenderDispatcher.distanceToSqr(mage) > 20 * 20) return;
        float age = mage.speechAge(partialTick);
        float alpha = Mth.clamp(Math.min(age / 4.0F, (WanderingMageEntity.SPEECH_TICKS - age) / 15.0F), 0.0F, 1.0F);
        if (alpha <= 0.05F) return;
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(text, BUBBLE_WIDTH);
        if (lines.isEmpty()) return;
        if (lines.size() > 4) lines = lines.subList(0, 4);
        float rise = (1.0F - Math.min(1.0F, age / 6.0F)) * 0.15F;
        float y = mage.getBbHeight() + (shouldShowName(mage) ? 0.85F : 0.55F) - rise + (lines.size() - 1) * 0.11F;
        ps.pushPose();
        ps.translate(0.0, y, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.022F, -0.022F, 0.022F);
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255.0F);
        int textColor = (a << 24) | 0xE8DCFF;
        int bg = ((int) (alpha * 0.6F * 255.0F) << 24) | 0x1A1036;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            font.drawInBatch(line, -font.width(line) / 2.0F, i * 10.0F, textColor, false, m, buffers, Font.DisplayMode.NORMAL, bg, light);
        }
        ps.popPose();
    }
}
