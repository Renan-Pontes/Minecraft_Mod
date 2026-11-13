package com.renanpontes.isekaimahou.feature.combat_feel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.combat_feel.FeelConfig;
import com.renanpontes.isekaimahou.feature.combat_feel.FeelPayloads;
import com.renanpontes.isekaimahou.feature.combat_feel.Finisher;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;

/**
 * HUD do módulo, bem discreto e colado na mira (nada "explodindo na tela"):
 * <ul>
 *   <li><b>Combo ×N</b> à ESQUERDA da mira (a direita é dos HUDs de classe: tambor do Pistoleiro, varinha da
 *       fazenda...), só a partir da 2ª magia: o número, o bônus pequeno ao lado e um risquinho que encolhe com o tempo
 *       que falta. Some sozinho quando o combo zera.</li>
 *   <li><b>Finalizar</b>: uma linha curta logo acima da mira só enquanto o jogador mira, de perto, num inimigo comum
 *       quase morto.</li>
 * </ul>
 */
public final class ComboHud {
    private static final int FADE_TICKS = 8;
    private static final int POP_TICKS = 5;
    private static final int BAR_W = 34;

    private static int count;
    private static int bonusTenths;
    private static int window = 1;
    private static float ticksLeft;
    /** Ticks desde a última subida do contador (para o "pulo" do número). */
    private static int sincePop = 100;
    /** Depois que o combo acaba: último número mostrado, sumindo. */
    private static int fadeCount;
    private static int fade;

    public static void receive(FeelPayloads.Combo p) {
        if (p.count() <= 0) {
            end();
            return;
        }
        if (p.count() > count) sincePop = 0;
        count = p.count();
        bonusTenths = p.bonusTenths();
        window = Math.max(1, p.window());
        ticksLeft = p.ticksLeft();
        fade = 0;
    }

    private static void end() {
        if (count >= 2) {
            fadeCount = count;
            fade = FADE_TICKS;
        }
        count = 0;
        ticksLeft = 0;
    }

    static void tick() {
        sincePop++;
        if (fade > 0) fade--;
        if (count > 0) {
            ticksLeft--;
            // O servidor avisa o fim; isto só cobre um pacote perdido (folga de meio segundo)
            if (ticksLeft < -10) end();
        }
    }

    static void clear() {
        count = 0;
        fade = 0;
        ticksLeft = 0;
    }

    static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()) return;
        float partial = dt.getGameTimeDeltaPartialTick(false);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        renderFinisher(g, mc, cx, cy);
        renderCombo(g, mc, cx, cy, partial);
    }

    private static void renderCombo(GuiGraphics g, Minecraft mc, int cx, int cy, float partial) {
        if (!FeelConfig.on(FeelConfig.SHOW_COMBO)) return;
        int shown = count >= 2 ? count : fade > 0 ? fadeCount : 0;
        if (shown < 2) return;
        float alpha = count >= 2 ? 1F : Mth.clamp((fade - partial) / FADE_TICKS, 0F, 1F);
        int a = (int) (alpha * 255F);
        if (a < 12) return;

        // Cor esquenta com o combo: claro → dourado → laranja
        int rgb = shown >= 10 ? 0xFF9A3C : shown >= 5 ? 0xFFD35A : 0xE8E4F4;
        Component text = Component.translatable("hud.isekai_mahou.combat_feel.combo", shown);
        float pop = sincePop < POP_TICKS && count >= 2 ? Mth.lerp((sincePop + partial) / POP_TICKS, 1.35F, 1F) : 1F;
        // Ancorado pela DIREITA (encostado na mira): o texto cresce para a esquerda
        int right = cx - 18, y = cy - 4;
        int textW = mc.font.width(text);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(right, y + 4F, 0F);
        ps.scale(pop, pop, 1F);
        g.drawString(mc.font, text, -textW, -4, (a << 24) | rgb, true);
        ps.popPose();

        if (count >= 2 && bonusTenths > 0) {
            // Bônus em letra pequena ao lado: informa sem chamar atenção
            String pct = bonusTenths % 10 == 0 ? String.valueOf(bonusTenths / 10) : String.valueOf(bonusTenths / 10F);
            Component bonus = Component.translatable("hud.isekai_mahou.combat_feel.combo_bonus", pct);
            float bw = mc.font.width(bonus) * 0.75F;
            ps.pushPose();
            ps.translate(right - textW - 4F - bw, y + 2F, 0F);
            ps.scale(0.75F, 0.75F, 1F);
            g.drawString(mc.font, bonus, 0, 0, ((int) (alpha * 170F) << 24) | 0xBFD8FF, true);
            ps.popPose();
        }
        if (count >= 2) {
            float left = Mth.clamp((ticksLeft - partial) / window, 0F, 1F);
            int w = Math.round(BAR_W * left);
            int by = y + 10;
            g.fill(right - BAR_W, by, right, by + 1, (int) (alpha * 70F) << 24);
            if (w > 0) g.fill(right - w, by, right, by + 1, ((int) (alpha * 200F) << 24) | rgb);
        }
    }

    private static void renderFinisher(GuiGraphics g, Minecraft mc, int cx, int cy) {
        if (!FeelConfig.on(FeelConfig.FINISHER_PROMPT) || mc.screen != null) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !Finisher.canFinish(mc.player, hit.getEntity())) return;
        Component text = Component.translatable("hud.isekai_mahou.combat_feel.finish", mc.options.keyUse.getTranslatedKeyMessage());
        // Pulsa de leve para ser notado sem piscar
        float pulse = 0.75F + 0.25F * Mth.sin((mc.level.getGameTime() % 40L) / 40F * Mth.TWO_PI);
        int a = Mth.clamp((int) (pulse * 255F), 40, 255);
        g.drawString(mc.font, text, cx - mc.font.width(text) / 2, cy - 22, (a << 24) | 0xE8C56A, true);
    }

    private ComboHud() {}
}
