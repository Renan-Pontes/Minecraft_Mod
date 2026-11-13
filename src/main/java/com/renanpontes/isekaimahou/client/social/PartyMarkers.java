package com.renanpontes.isekaimahou.client.social;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Release 1.0 — grupo melhorado: <b>marcador dos amigos</b> no HUD. Um losango pequeno com o nome e a distância aparece
 * na direção de cada membro do grupo que está LONGE (mais de {@value #NEAR} blocos, inclusive fora do alcance de
 * visão — a posição vem do servidor). De perto não aparece nada: o nome sobre a cabeça já tem a marca do grupo.
 * Discreto de propósito: sem moldura, sem seta piscando, some com F1 e com qualquer tela aberta.
 * <p>
 * A seta de direção de quem está fora da tela fica no cartão do membro ({@link PartyHud}); o minimapa do Xaero recebe os
 * mesmos pontos em {@link XaeroPartyTracker}.
 */
final class PartyMarkers implements LayeredDraw.Layer {
    /** Até aqui o marcador não aparece (o nome sobre a cabeça resolve). */
    static final int NEAR = 20;
    private static final int GREEN = 0x7CFF8A, RED = 0xFF5A5A;

    // Câmera do último quadro do mundo (para projetar um ponto do mundo na tela do HUD)
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean captured;

    static void onLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ClientSocialView.inParty()) return;
        VIEW.set(e.getModelViewMatrix());
        PROJ.set(e.getProjectionMatrix());
        camPos = e.getCamera().getPosition();
        captured = true;
    }

    static void clear() {
        captured = false;
    }

    /** Onde está o membro agora: a entidade (se o cliente a enxerga) ou o bloco mandado pelo servidor. Null = não se sabe. */
    @Nullable
    static Vec3 where(Minecraft mc, UUID id, float partial) {
        Player p = mc.level == null ? null : mc.level.getPlayerByUUID(id);
        if (p != null) return p.getPosition(partial);
        BlockPos b = ClientSocialView.memberPos(id);
        return b == null ? null : new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
    }

    /** Ângulo (graus, 0 = em frente, positivo = à direita) de um ponto em relação para onde a câmera olha. */
    static float bearing(Minecraft mc, Vec3 target) {
        Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
        float yawTo = (float) (Mth.atan2(target.z - from.z, target.x - from.x) * (180.0 / Math.PI)) - 90F;
        return Mth.wrapDegrees(yawTo - mc.gameRenderer.getMainCamera().getYRot());
    }

    /** Setinha (triângulo de 5 px) centrada em (cx, cy), girada {@code degrees} no sentido horário a partir de "para cima". */
    static void arrow(GuiGraphics g, float cx, float cy, float degrees, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        g.pose().scale(0.5F, 0.5F, 1F);
        for (int i = 0; i < 5; i++) g.fill(-(i + 1), -5 + i * 2, i + 1, -3 + i * 2, argb);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!captured || mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!com.renanpontes.isekaimahou.core.MahouConfig.safe(com.renanpontes.isekaimahou.core.MahouConfig.SHOW_PARTY_MARKERS)) return;
        if (!ClientSocialView.inParty()) {
            captured = false;
            return;
        }
        Font font = mc.font;
        float partial = dt.getGameTimeDeltaPartialTick(true);
        UUID me = mc.player.getUUID();
        String myDim = mc.level.dimension().location().toString();
        Vec3 eye = mc.player.getPosition(partial);
        Vector4f v = new Vector4f();
        for (Member m : ClientSocialView.partyMembers()) {
            if (!m.online() || m.id().equals(me) || !m.dim().equals(myDim)) continue;
            Vec3 pos = where(mc, m.id(), partial);
            if (pos == null) continue;
            double dist = Math.sqrt(pos.distanceToSqr(eye));
            if (dist <= NEAR) continue;
            // Um pouco acima da cabeça
            v.set((float) (pos.x - camPos.x), (float) (pos.y + 2.6 - camPos.y), (float) (pos.z - camPos.z), 1F);
            v.mul(VIEW).mul(PROJ);
            if (v.w <= 0.05F) continue;   // atrás da câmera: a seta do cartão do grupo mostra o lado
            float nx = v.x / v.w, ny = v.y / v.w;
            if (Math.abs(nx) > 0.94F || Math.abs(ny) > 0.90F) continue;
            int sx = Math.round((nx * 0.5F + 0.5F) * g.guiWidth());
            int sy = Math.round((1F - (ny * 0.5F + 0.5F)) * g.guiHeight());

            // Entra suave logo depois do limite, para não "pular" na tela
            int alpha = (int) (0xD0 * Mth.clamp((dist - NEAR) / 6.0, 0.0, 1.0));
            if (alpha < 12) continue;
            boolean hurt = m.maxHp() > 0 && m.hp() > 0 && m.hp() / m.maxHp() <= 0.25F;
            int rgb = hurt ? RED : GREEN;
            MahouUi.diamond(g, sx, sy, 3, MahouUi.alpha(0xFF000000, alpha * 2 / 3));
            MahouUi.diamond(g, sx, sy, 2, MahouUi.alpha(0xFF000000 | rgb, alpha));
            Component label = Component.literal(m.name() + "  " + (int) dist + "m");
            float scale = 0.6F;
            float tw = font.width(label) * scale;
            MahouUi.smallText(g, font, label, sx - tw / 2F, sy + 5, scale, MahouUi.alpha(0xFF000000 | MahouUi.lighten(rgb, 0.35F), alpha), true);
        }
    }
}
