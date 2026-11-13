package com.renanpontes.isekaimahou.feature.renown.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.renown.RenownTitles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * O título <b>acima do nome</b> dos outros jogadores — discreto: uma linha menor que o nome, sem fundo, só de perto
 * ({@link #RANGE} blocos), e some quando o jogador se agacha ou está invisível (como o próprio nome).
 */
final class TitleTags {
    /** Distância máxima para ver o título (o nome do vanilla vai até 64). */
    private static final double RANGE = 24;
    /** Tamanho do texto (o nome usa 0.025). */
    private static final float SCALE = 0.018F;

    private TitleTags() {}

    static void onNameTag(RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof Player p) || RenownClientState.TITLES_IN_USE.isEmpty()) return;
        if (!com.renanpontes.isekaimahou.core.MahouConfig.safe(com.renanpontes.isekaimahou.core.MahouConfig.SHOW_PLAYER_TITLES)) return;
        String key = RenownClientState.TITLES_IN_USE.get(p.getUUID());
        if (key == null || e.canRender() == TriState.FALSE) return;
        Minecraft mc = Minecraft.getInstance();
        // As mesmas regras do nome: HUD ligado, não é o próprio jogador da câmera, visível, de pé
        if (mc.player == null || !Minecraft.renderNames() || p == mc.getCameraEntity()) return;
        if (p.isDiscrete() || p.isInvisibleTo(mc.player) || p.isVehicle()) return;
        if (mc.getEntityRenderDispatcher().distanceToSqr(p) > RANGE * RANGE) return;
        Vec3 at = p.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, p.getViewYRot(e.getPartialTick()));
        if (at == null) return;

        Component text = Component.translatable(key);
        int color = 0xFF000000 | RenownTitles.tierColor(RenownTitles.tierOf(key));
        Font font = mc.font;
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        // 0.5 = altura do nome; +0.27 = uma linha acima dele
        pose.translate(at.x, at.y + 0.5 + 0.27, at.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(text, -font.width(text) / 2F, 0, color, false, m, e.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0,
                e.getPackedLight());
        pose.popPose();
    }
}
