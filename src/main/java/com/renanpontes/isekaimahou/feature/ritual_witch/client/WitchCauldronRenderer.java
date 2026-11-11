package com.renanpontes.isekaimahou.feature.ritual_witch.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_witch.block.WitchCauldronBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Desenha o conteúdo do Caldeirão Ritual: superfície do líquido (água, preparo colorido girando, lodo)
 * e os ingredientes boiando em círculo durante o preparo.
 */
public class WitchCauldronRenderer implements BlockEntityRenderer<WitchCauldronBlockEntity> {
    private static final ResourceLocation BREW = IsekaiMahou.id("block/witch_brew");
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final float MIN = 2F / 16F;
    private static final float MAX = 14F / 16F;

    private final ItemRenderer items;

    public WitchCauldronRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(WitchCauldronBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float h = be.liquidHeight();
        if (h < 0) return;
        WitchCauldronBlockEntity.Phase phase = be.phase();
        boolean water = phase == WitchCauldronBlockEntity.Phase.WATER;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(water ? WATER : BREW);

        float r, g, b;
        if (be.shownR >= 0) {
            r = be.shownR; g = be.shownG; b = be.shownB;
        } else {
            int c = be.color();
            r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F;
        }
        float alpha = switch (phase) {
            case WATER -> 0.8F;
            case RUINED -> 1.0F;
            default -> 0.92F;
        };
        // Poção pronta brilha (luz cheia); o resto usa a luz do ambiente
        int surfaceLight = phase == WitchCauldronBlockEntity.Phase.READY ? LightTexture.FULL_BRIGHT : light;
        // O redemoinho gira as coordenadas de textura (não a geometria), então a superfície continua presa às paredes
        float angle = water ? 0F : be.spin + be.spinSpeed * partialTick;
        float scale = water ? 1F : 0.7F;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        float[][] corners = {{MIN, MIN}, {MIN, MAX}, {MAX, MAX}, {MAX, MIN}};
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        for (float[] corner : corners) {
            float lx = (corner[0] - 0.5F) / (MAX - MIN), lz = (corner[1] - 0.5F) / (MAX - MIN);
            float u = 0.5F + (lx * cos - lz * sin) * scale;
            float v = 0.5F + (lx * sin + lz * cos) * scale;
            vc.addVertex(p, corner[0], h, corner[1]).setColor(r, g, b, alpha)
                    .setUv(sprite.getU(u), sprite.getV(v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(surfaceLight).setNormal(p, 0, 1, 0);
        }

        if (phase == WitchCauldronBlockEntity.Phase.BREWING) renderFloating(be, partialTick, pose, buffers, light, h);
    }

    /** Ingredientes boiando e girando devagar (mais rápido quando alguém mexe). */
    private void renderFloating(WitchCauldronBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, float h) {
        List<Item> list = be.ingredients();
        int n = list.size();
        if (n == 0) return;
        float spin = be.spin + be.spinSpeed * partialTick;
        float time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 100000L) + partialTick;
        for (int i = 0; i < n; i++) {
            float a = spin * 0.8F + i * Mth.TWO_PI / n;
            float radius = n == 1 ? 0.12F : 0.26F;
            float bob = Mth.sin(time * 0.12F + i * 1.7F) * 0.012F;
            pose.pushPose();
            pose.translate(0.5F + Mth.cos(a) * radius, h + 0.015F + bob, 0.5F + Mth.sin(a) * radius);
            pose.mulPose(Axis.YP.rotation(-a + Mth.HALF_PI));
            pose.mulPose(Axis.XP.rotationDegrees(80F));
            pose.scale(0.24F, 0.24F, 0.24F);
            items.renderStatic(new ItemStack(list.get(i)), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
