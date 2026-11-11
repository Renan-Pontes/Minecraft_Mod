package com.renanpontes.isekaimahou.feature.travel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.travel.block.TravelStoneBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import org.joml.Matrix4f;

import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Obelisco animado (GeckoLib, runas e cristal brilhando no escuro) + letreiro flutuante com o nome do lugar quando o
 * jogador está perto ("Clique para viajar" / "Aproxime-se para sintonizar").
 */
public class TravelStoneRenderer extends GeoBlockRenderer<TravelStoneBlockEntity> {
    private static final double LABEL_RANGE = 10.0;
    private final Font font;

    public TravelStoneRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new DefaultedBlockGeoModel<>(IsekaiMahou.id("travel_stone")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.font = ctx.getFont();
    }

    /** O obelisco passa 2 blocos acima do bloco: sem isto ele some quando o bloco sai da tela. */
    @Override
    public AABB getRenderBoundingBox(TravelStoneBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 3.5, p.getZ() + 1.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(TravelStoneBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        renderLabel(be, pose, buffers);
    }

    private void renderLabel(TravelStoneBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        Component name = be.display();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (name == null || player == null || mc.options.hideGui) return;
        BlockPos p = be.getBlockPos();
        double d2 = player.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
        if (d2 > LABEL_RANGE * LABEL_RANGE) return;
        boolean known = !be.placeId().isEmpty() && Travel.discovered(player, be.placeId());
        Component hint = Component.translatable(known ? "travel.isekai_mahou.stone.label_use" : "travel.isekai_mahou.stone.label_touch")
                .withStyle(known ? ChatFormatting.GRAY : ChatFormatting.AQUA);
        int rgb = 0xFF000000 | be.color();
        // Some aos poucos na borda do alcance
        float fade = (float) Math.min(1.0, (LABEL_RANGE - Math.sqrt(d2)) / 3.0);
        int alpha = Math.max(0x20, (int) (fade * 255));

        pose.pushPose();
        pose.translate(0.5, 2.95, 0.5);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Matrix4f m = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F * fade) << 24;
        float nx = -font.width(name) / 2F;
        font.drawInBatch(name, nx, 0, (alpha << 24) | (rgb & 0xFFFFFF), false, m, buffers, Font.DisplayMode.NORMAL, bg,
                LightTexture.FULL_BRIGHT);
        pose.translate(0, 11, 0);
        pose.scale(0.75F, 0.75F, 0.75F);
        Matrix4f m2 = pose.last().pose();
        font.drawInBatch(hint, -font.width(hint) / 2F, 0, (alpha << 24) | 0xFFFFFF, false, m2, buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
