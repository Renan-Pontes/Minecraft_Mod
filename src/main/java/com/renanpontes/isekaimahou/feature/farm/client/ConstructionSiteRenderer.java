package com.renanpontes.isekaimahou.feature.farm.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.farm.blueprint.ConstructionSiteBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Canteiro de Obras no mundo: o contorno da obra inteira (cor da planta), os próximos blocos piscando como "fantasma"
 * e o letreiro em cima da estaca: "Casa Pequena — 42%" e, se faltar algo, "Faltando: Tábuas".
 */
public class ConstructionSiteRenderer implements BlockEntityRenderer<ConstructionSiteBlockEntity> {
    private final Font font;

    public ConstructionSiteRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ConstructionSiteBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || be.completed()) return;
        int rgb = be.kind().rgb;
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float t = be.getLevel().getGameTime() + partialTick;
        BlockPos origin = be.getBlockPos();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // Contorno da obra
        AABB box = be.kind().template().bounds(origin, be.look()).move(-origin.getX(), -origin.getY(), -origin.getZ());
        LevelRenderer.renderLineBox(ps, lines, box.inflate(0.01), r, g, b, 0.55F);

        // Próximos blocos (pulsando)
        float pulse = 0.45F + 0.35F * Mth.sin(t / 5F);
        List<BlockPos> next = be.remainingPreview(4);
        for (BlockPos p : next) {
            AABB cell = new AABB(p).move(-origin.getX(), -origin.getY(), -origin.getZ()).deflate(0.06);
            LevelRenderer.renderLineBox(ps, lines, cell, 1F, 1F, 1F, pulse);
        }

        // Letreiro
        int pct = be.total() == 0 ? 100 : be.index() * 100 / be.total();
        Component title = Component.translatable("farm.isekai_mahou.site.label", be.kind().displayName(), pct);
        Component sub = be.missing() != null
                ? Component.translatable("farm.isekai_mahou.site.missing", be.missing().displayName())
                : be.boosted() ? Component.translatable("farm.isekai_mahou.site.label_boost") : null;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(Vec3.atCenterOf(origin)) > 48 * 48) return;
        ps.pushPose();
        ps.translate(0.5, 1.45, 0.5);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        ps.scale(0.022F, -0.022F, 0.022F);
        float w = -font.width(title) / 2F;
        font.drawInBatch(title, w, 0, 0xFF000000 | rgb, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        if (sub != null) {
            int color = be.missing() != null ? 0xFFFF7070 : 0xFFB896FF;
            font.drawInBatch(sub, -font.width(sub) / 2F, 11, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ConstructionSiteBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(ConstructionSiteBlockEntity be) {
        return be.kind().template().bounds(be.getBlockPos(), be.look()).minmax(new AABB(be.getBlockPos()).expandTowards(0, 2, 0));
    }
}
