package com.renanpontes.isekaimahou.feature.dimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeaconBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Feixe dourado do Farol Celestial aceso (dois feixes: halo dourado largo + núcleo branco fino). */
public class CelestialBeaconRenderer implements BlockEntityRenderer<CelestialBeaconBlockEntity> {
    private static final int GOLD = 0xFFFFE9A8;
    private static final int CORE = 0xFFFFFFFF;

    public CelestialBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CelestialBeaconBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.isActive()) return;
        long time = be.getLevel().getGameTime();
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time, 1,
                BeaconRenderer.MAX_RENDER_Y, GOLD, 0.25F, 0.35F);
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 2.0F, time, 1,
                BeaconRenderer.MAX_RENDER_Y, CORE, 0.08F, 0.12F);
    }

    @Override
    public boolean shouldRenderOffScreen(CelestialBeaconBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(CelestialBeaconBlockEntity be) {
        return AABB.INFINITE;
    }
}
