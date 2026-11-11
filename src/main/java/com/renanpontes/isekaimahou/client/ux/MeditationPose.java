package com.renanpontes.isekaimahou.client.ux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Meditação visível: quem medita SENTA no chão de pernas cruzadas (pose aplicada pelo {@code HumanoidModelMixin}) e,
 * para quem está meditando, a tela vai ESCURECENDO aos poucos (até ~70% em 12 s) e clareia rápido ao levantar.
 * <p>O jogador local usa o estado do servidor ({@link ClientMagic#meditating}); os outros jogadores são estimados no
 * cliente pela mesma regra do servidor (agachado, no chão e parado por {@link Progression#MEDITATION_DELAY_TICKS}).
 */
public final class MeditationPose {
    private static final float MAX_DARK = 0.72F;
    private static final int FADE_IN_TICKS = 240;
    private static final double SINK = 0.47;

    private static final Map<UUID, Integer> STILL = new HashMap<>();
    private static float dark, darkPrev;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Pre.class, MeditationPose::preRender);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Post.class, MeditationPose::postRender);
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Pre.class, MeditationPose::darken);
    }

    public static boolean isMeditating(LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return ClientMagic.meditating;
        Integer t = STILL.get(p.getUUID());
        return t != null && t >= Progression.MEDITATION_DELAY_TICKS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        darkPrev = dark;
        if (mc.level == null || mc.player == null) {
            STILL.clear();
            dark = darkPrev = 0;
            return;
        }
        // Escurece devagar enquanto medita; clareia rápido ao parar
        if (ClientMagic.meditating && !mc.player.isSpectator()) dark = Math.min(MAX_DARK, dark + MAX_DARK / FADE_IN_TICKS);
        else dark = Math.max(0, dark - MAX_DARK / 12F);

        Map<UUID, Integer> next = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            boolean still = p.isCrouching() && p.onGround() && Math.abs(p.getX() - p.xo) < 1.0E-3
                    && Math.abs(p.getZ() - p.zo) < 1.0E-3 && Math.abs(p.getY() - p.yo) < 1.0E-3 && !p.isUsingItem();
            if (still) next.put(p.getUUID(), STILL.getOrDefault(p.getUUID(), 0) + 1);
        }
        STILL.clear();
        STILL.putAll(next);
    }

    private static void preRender(RenderPlayerEvent.Pre e) {
        if (!isMeditating(e.getEntity())) return;
        e.getPoseStack().pushPose();
        // Assenta o quadril no chão (o agachar já desce 0,125)
        e.getPoseStack().translate(0, -SINK, 0);
    }

    private static void postRender(RenderPlayerEvent.Post e) {
        if (isMeditating(e.getEntity())) e.getPoseStack().popPose();
    }

    private static void darken(RenderGuiEvent.Pre e) {
        float a = Mth.lerp(e.getPartialTick().getGameTimeDeltaPartialTick(false), darkPrev, dark);
        if (a <= 0.003F) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int center = ((int) (a * 255) & 0xFF) << 24;
        int edge = ((int) (Math.min(1F, a * 1.25F) * 255) & 0xFF) << 24;
        // Véu escuro com bordas um pouco mais fechadas (vinheta simples)
        g.fill(0, 0, w, h, center | 0x050310);
        int band = Math.max(8, h / 6);
        g.fillGradient(0, 0, w, band, edge | 0x050310, 0x00050310);
        g.fillGradient(0, h - band, w, h, 0x00050310, edge | 0x050310);
    }

    /** Pose de meditação (pernas cruzadas à frente, tronco reto, mãos no colo). Chamado no fim do setupAnim. */
    public static void apply(HumanoidModel<?> m, LivingEntity entity) {
        if (!isMeditating(entity)) return;
        // Desfaz o agachar
        m.body.xRot = 0;
        m.body.y = 0;
        m.head.y = 0;
        m.hat.y = 0;
        m.rightArm.y = 2;
        m.leftArm.y = 2;
        m.rightLeg.y = 12;
        m.leftLeg.y = 12;
        m.rightLeg.z = 0.1F;
        m.leftLeg.z = 0.1F;
        // Pernas cruzadas
        m.rightLeg.xRot = -1.45F;
        m.rightLeg.yRot = 0.35F;
        m.rightLeg.zRot = 0.08F;
        m.leftLeg.xRot = -1.45F;
        m.leftLeg.yRot = -0.35F;
        m.leftLeg.zRot = -0.08F;
        // Mãos repousando sobre os joelhos, com uma respiração leve
        float breath = Mth.sin(entity.tickCount * 0.08F) * 0.03F;
        m.rightArm.xRot = -0.55F + breath;
        m.rightArm.yRot = -0.25F;
        m.rightArm.zRot = 0.1F;
        m.leftArm.xRot = -0.55F + breath;
        m.leftArm.yRot = 0.25F;
        m.leftArm.zRot = -0.1F;
    }

    private MeditationPose() {}
}
