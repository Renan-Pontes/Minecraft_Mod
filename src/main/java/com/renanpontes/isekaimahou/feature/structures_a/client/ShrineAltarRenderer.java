package com.renanpontes.isekaimahou.feature.structures_a.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineKind;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarBlockEntity;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Essência do elemento flutuando e girando sobre o altar. Se o jogador local já superou a prova deste
 * santuário, a essência fica "repousada" sobre o tampo (oferenda aceita). Durante a prova ela gira rápido e brilha.
 */
public class ShrineAltarRenderer implements BlockEntityRenderer<ShrineAltarBlockEntity> {
    private final ItemRenderer items;
    /** Pilhas de essência por elemento (criadas uma vez, não a cada quadro). */
    private final Map<Element, ItemStack> stacks = new EnumMap<>(Element.class);

    public ShrineAltarRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(ShrineAltarBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Element e = be.element();
        ItemStack stack = stacks.computeIfAbsent(e, el -> {
            var essence = CoreRegistries.ESSENCES.get(el);
            return essence == null ? ItemStack.EMPTY : new ItemStack(essence.get());
        });
        if (stack.isEmpty()) return;
        Level level = be.getLevel();
        float t = (level != null ? level.getGameTime() : 0) + partialTick;
        int seed = (int) be.getBlockPos().asLong();
        boolean done = ClientMagic.data().hasFlag(ShrineKind.of(e).progressKey());
        boolean active = be.isActive();
        int lightAbove = level != null ? LevelRenderer.getLightColor(level, be.getBlockPos().above()) : light;

        ps.pushPose();
        if (done && !active) {
            // Repousada sobre o tampo, deitada.
            ps.translate(0.5, 0.9, 0.5);
            ps.mulPose(Axis.XP.rotationDegrees(90F));
            ps.mulPose(Axis.ZP.rotationDegrees((seed & 63) * 5.6F));
            ps.scale(0.7F, 0.7F, 0.7F);
            items.renderStatic(stack, ItemDisplayContext.FIXED, lightAbove, OverlayTexture.NO_OVERLAY, ps, buffers, level, seed);
        } else {
            float speed = active ? 9F : 2.5F;
            float bob = Mth.sin(t / (active ? 4F : 10F)) * (active ? 0.1F : 0.07F);
            float scale = active ? 1.15F + Mth.sin(t / 3F) * 0.08F : 1.0F;
            ps.translate(0.5, 1.25 + bob + (active ? 0.25 : 0), 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((t * speed) % 360F));
            ps.scale(scale, scale, scale);
            // Durante a prova a essência brilha no escuro (luz máxima).
            int lit = active ? LightTexture.FULL_BRIGHT : lightAbove;
            items.renderStatic(stack, ItemDisplayContext.GROUND, lit, OverlayTexture.NO_OVERLAY, ps, buffers, level, seed);
        }
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ShrineAltarBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.6, 0);
    }
}
