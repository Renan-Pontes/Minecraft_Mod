package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renderer dos NPCs da cidade do jogador: modelo GeckoLib + balão de fala com fade sobre a cabeça. */
public class SettlementNpcRenderer extends GeoEntityRenderer<SettlementNpc> {
    private static final int BUBBLE_WIDTH = 150;

    public SettlementNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SettlementNpcModel());
        this.shadowRadius = 0.45F;
    }

    @Override
    public void render(SettlementNpc npc, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(npc, yaw, partialTick, ps, buffers, light);
        Component text = npc.speech();
        if (text == null || entityRenderDispatcher.distanceToSqr(npc) > 20 * 20) return;
        float age = npc.speechAge(partialTick);
        float alpha = Mth.clamp(Math.min(age / 4.0F, (SettlementNpc.SPEECH_TICKS - age) / 15.0F), 0.0F, 1.0F);
        if (alpha <= 0.05F) return;
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(text, BUBBLE_WIDTH);
        if (lines.isEmpty()) return;
        if (lines.size() > 4) lines = lines.subList(0, 4);
        float y = npc.getBbHeight() + (shouldShowName(npc) ? 0.85F : 0.55F) + (lines.size() - 1) * 0.11F;
        ps.pushPose();
        ps.translate(0.0, y, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.022F, -0.022F, 0.022F);
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255.0F);
        int textColor = (a << 24) | 0xFFF4E0;
        int bg = ((int) (alpha * 0.6F * 255.0F) << 24) | 0x1A140C;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            font.drawInBatch(line, -font.width(line) / 2.0F, i * 10.0F, textColor, false, m, buffers, Font.DisplayMode.NORMAL, bg, light);
        }
        ps.popPose();
    }
}
