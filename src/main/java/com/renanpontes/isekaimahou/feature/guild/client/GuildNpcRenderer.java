package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildNpcEntity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Renderer dos NPCs da Guilda: modelo GeckoLib + itens nas mãos (ossos "right_item"/"left_item")
 * + balão de fala sobre a cabeça (texto da fala sincronizada, com fade de entrada e saída).
 */
public class GuildNpcRenderer<T extends GuildNpcEntity> extends GeoEntityRenderer<T> {
    private static final int BUBBLE_WIDTH = 150;
    /** Desenhando o retrato na tela da Guilda: sem balão de fala. */
    static boolean portrait;

    public GuildNpcRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        super(ctx, new GuildNpcModel<>(id));
        this.shadowRadius = shadow;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, T npc) {
                return switch (bone.getName()) {
                    case "right_item" -> npc.getMainHandItem();
                    case "left_item" -> npc.getOffhandItem();
                    default -> null;
                };
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T npc) {
                return bone.getName().equals("left_item") ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack ps, GeoBone bone, ItemStack stack, T npc, MultiBufferSource buffers,
                                              float partialTick, int light, int overlay) {
                if (stack.isEmpty()) return;
                ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                if (stack.getItem() instanceof ShieldItem) {
                    boolean left = bone.getName().equals("left_item");
                    ps.translate(0.0, 0.125, left ? 0.25 : -0.25);
                    if (left) ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                }
                super.renderStackForBone(ps, bone, stack, npc, buffers, partialTick, light, overlay);
            }
        });
    }

    /** No retrato (diálogo/tela da Guilda) a plaqueta do companheiro não é desenhada por cima do rosto. */
    @Override
    public boolean shouldShowName(T npc) {
        return !portrait && super.shouldShowName(npc);
    }

    @Override
    public void render(T npc, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(npc, yaw, partialTick, ps, buffers, light);
        if (!portrait) renderSpeech(npc, ps, buffers, light, partialTick);
    }

    /** Balão de fala (como uma placa de nome, mas com várias linhas e fade). */
    private void renderSpeech(T npc, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        Component text = npc.speech();
        if (text == null || entityRenderDispatcher.distanceToSqr(npc) > 24 * 24) return;
        float age = npc.speechAge(partialTick);
        float alpha = Mth.clamp(Math.min(age / 4.0F, (GuildNpcEntity.SPEECH_TICKS - age) / 15.0F), 0.0F, 1.0F);
        if (alpha <= 0.05F) return;
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(text, BUBBLE_WIDTH);
        if (lines.isEmpty()) return;
        // Sobe um pouco ao aparecer, e fica acima da placa de nome quando ela está visível.
        float rise = (1.0F - Math.min(1.0F, age / 6.0F)) * 0.15F;
        float y = npc.getBbHeight() + (shouldShowName(npc) ? 0.85F : 0.55F) - rise + (lines.size() - 1) * 0.11F;
        ps.pushPose();
        ps.translate(0.0, y, 0.0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.022F, -0.022F, 0.022F);
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255.0F);
        int textColor = (a << 24) | 0xFFF3D6;
        int bg = ((int) (alpha * 0.55F * 255.0F) << 24) | 0x1A120A;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float x = -font.width(line) / 2.0F;
            font.drawInBatch(line, x, i * 10.0F, textColor, false, m, buffers, Font.DisplayMode.NORMAL, bg, light);
        }
        ps.popPose();
    }
}
