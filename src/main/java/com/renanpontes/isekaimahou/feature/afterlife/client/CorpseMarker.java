package com.renanpontes.isekaimahou.feature.afterlife.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Onde está o seu corpo: uma linha no rastreador de objetivos (com a setinha do núcleo apontando para ele) e um
 * feixe de luz das almas no lugar (marcador no mundo), visível de longe na mesma dimensão.
 */
final class CorpseMarker {
    static final String ID = "isekai_mahou:afterlife";
    /** Logo abaixo de provação/chefe (1000): achar o corpo é urgente. */
    static final int PRIORITY = 900;
    private static final int SOUL = 0x5FE6E6;
    private static final int EMBER = 0xFF7A3A;

    static void register() {
        ObjectiveTracker.register(ID, PRIORITY, CorpseMarker::lines);
    }

    private static List<ObjectiveLine> lines() {
        AfterlifeNet.Mark m = ClientAfterlife.mark;
        Minecraft mc = Minecraft.getInstance();
        if (!m.has() || mc.player == null || ClientAfterlife.active()) return List.of();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, m.dim());
        Component title = Component.translatable(m.charred() ? "objective.isekai_mahou.afterlife.charred" : "objective.isekai_mahou.afterlife.corpse");
        Component detail;
        if (mc.level != null && mc.level.dimension().equals(dim)) {
            int d = (int) Math.round(Math.sqrt(mc.player.blockPosition().distSqr(m.pos())));
            detail = m.count() > 1 ? Component.translatable("objective.isekai_mahou.afterlife.distance_more", d, m.count() - 1)
                    : Component.translatable("objective.isekai_mahou.afterlife.distance", d);
        } else {
            detail = Component.translatable("objective.isekai_mahou.afterlife.other_dim");
        }
        ItemStack icon = new ItemStack(m.charred() ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL);
        return List.of(ObjectiveLine.of(title, detail, m.charred() ? EMBER : SOUL, icon).withTarget(m.pos(), dim));
    }

    /** Feixe no corpo (como o do sinalizador), só a mais de 8 blocos e na mesma dimensão. */
    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        AfterlifeNet.Mark m = ClientAfterlife.mark;
        Minecraft mc = Minecraft.getInstance();
        if (!m.has() || mc.level == null || mc.player == null) return;
        if (!mc.level.dimension().location().equals(m.dim())) return;
        Vec3 cam = e.getCamera().getPosition();
        double dx = m.pos().getX() - cam.x, dz = m.pos().getZ() - cam.z;
        double distSq = dx * dx + dz * dz;
        if (distSq < 8 * 8 || distSq > 768 * 768) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = new PoseStack();
        ps.translate(m.pos().getX() - cam.x, m.pos().getY() - cam.y, m.pos().getZ() - cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int color = 0xFF000000 | (m.charred() ? EMBER : SOUL);
        BeaconRenderer.renderBeaconBeam(ps, buffers, BeaconRenderer.BEAM_LOCATION, partial, 1F, mc.level.getGameTime(), 0, 96, color, 0.12F, 0.18F);
        buffers.endBatch();
    }

    private CorpseMarker() {}
}
