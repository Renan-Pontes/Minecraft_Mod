package com.renanpontes.isekaimahou.feature.cities.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renderer dos NPCs da cidade: modelo GeckoLib + balão de fala sobre a cabeça (com fade). */
public class CityNpcRenderer extends GeoEntityRenderer<CityNpc> {
    private static final int BUBBLE_WIDTH = 160;
    /** Desenhando o retrato na tela de diálogo: sem balão. */
    static boolean portrait;

    public CityNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CityNpcModel());
        this.shadowRadius = 0.45F;
    }

    @Override
    public void render(CityNpc npc, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(npc, yaw, partialTick, ps, buffers, light);
        if (!portrait) {
            renderSpeech(npc, ps, buffers, light, partialTick);
            renderAlert(npc, ps, buffers, partialTick);
        }
    }

    /**
     * Ícone de alerta (v1.0) bem acima da cabeça: "!" dourado-alaranjado (o guarda está abordando alguém) ou "?" amarelo
     * (o morador desconfiou). Pula ao aparecer, balança devagar e brilha no escuro.
     */
    private void renderAlert(CityNpc npc, PoseStack ps, MultiBufferSource buffers, float partialTick) {
        int kind = npc.alertKind();
        if (kind == CityNpc.ALERT_NONE || entityRenderDispatcher.distanceToSqr(npc) > 48 * 48) return;
        Font font = getFont();
        String glyph = kind == CityNpc.ALERT_CONFRONT ? "!" : "?";
        int color = kind == CityNpc.ALERT_CONFRONT ? 0xFFFF6A2A : 0xFFFFE05A;
        float t = npc.tickCount + partialTick;
        float bob = Mth.sin(t * 0.18F) * 0.06F;
        float up = npc.getBbHeight() + (npc.speech() != null ? 1.25F : 0.75F) + (shouldShowName(npc) ? 0.3F : 0.0F) + bob;
        ps.pushPose();
        ps.translate(0.0, up, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        float scale = 0.055F * (1.0F + 0.08F * Mth.sin(t * 0.35F));
        ps.scale(scale, -scale, scale);
        Matrix4f m = ps.last().pose();
        float x = -font.width(glyph) / 2.0F;
        // Contorno escuro + o sinal brilhando (luz cheia).
        int outline = 0xFF1A0E06;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                font.drawInBatch(glyph, x + dx * 0.6F, -4.0F + dy * 0.6F, outline, false, m, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }
        font.drawInBatch(glyph, x, -4.0F, color, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        ps.popPose();
    }

    /** No retrato do diálogo o NPC costuma estar sob a mira: sem a plaquinha de nome por cima da cabeça. */
    @Override
    public boolean shouldShowName(CityNpc npc) {
        return !portrait && super.shouldShowName(npc);
    }

    private void renderSpeech(CityNpc npc, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        Component text = npc.speech();
        if (text == null || entityRenderDispatcher.distanceToSqr(npc) > 20 * 20) return;
        float age = npc.speechAge(partialTick);
        float alpha = Mth.clamp(Math.min(age / 4.0F, (CityNpc.SPEECH_TICKS - age) / 15.0F), 0.0F, 1.0F);
        if (alpha <= 0.05F) return;
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(text, BUBBLE_WIDTH);
        if (lines.isEmpty()) return;
        if (lines.size() > 4) lines = lines.subList(0, 4);
        float rise = (1.0F - Math.min(1.0F, age / 6.0F)) * 0.15F;
        float y = npc.getBbHeight() + (shouldShowName(npc) ? 0.85F : 0.55F) - rise + (lines.size() - 1) * 0.11F;
        ps.pushPose();
        ps.translate(0.0, y, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.022F, -0.022F, 0.022F);
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255.0F);
        int textColor = (a << 24) | 0xF4F0FF;
        int bg = ((int) (alpha * 0.6F * 255.0F) << 24) | 0x10142A;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            font.drawInBatch(line, -font.width(line) / 2.0F, i * 10.0F, textColor, false, m, buffers, Font.DisplayMode.NORMAL, bg, light);
        }
        ps.popPose();
    }
}
