package com.renanpontes.isekaimahou.feature.mounts.client;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;
import com.renanpontes.isekaimahou.feature.mounts.entity.MountFlight;
import com.renanpontes.isekaimahou.feature.mounts.entity.RideMount;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Painel pequeno à esquerda da barra de itens enquanto se pilota uma montaria (voadora ou de chão):
 * tecla + nome da habilidade, barra de recarga (pulsa enquanto o efeito está ativo) e a altura
 * sobre o chão quando está voando.
 */
public final class MountHud {
    private static final int READY = 0xFFFFD86B;
    private static final int ACTIVE = 0xFF7FF5E6;
    private static final int COOLING = 0xFF6F6A80;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!(mc.player.getControlledVehicle() instanceof RideMount mount)) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        int cd = mount.abilityCooldown();
        int max = Math.max(1, mount.abilityCooldownMax());
        int active = mount.abilityActive();
        Component label = Component.translatable("hud.isekai_mahou.mount_ability", MountInput.ABILITY.getTranslatedKeyMessage(),
                mount.abilityName());
        Component status = cd > 0 ? Component.translatable("hud.isekai_mahou.mount_cooldown", String.format("%.1f", cd / 20.0F))
                : Component.translatable("hud.isekai_mahou.mount_ready");
        int pw = Math.max(font.width(label), font.width(status)) + 10;
        int ph = 30;
        int x0 = w / 2 - 91 - 36 - pw;
        int y0 = h - ph - 2;
        if (x0 < 4) { // tela estreita: acima da barra de itens
            x0 = w / 2 - pw / 2;
            y0 = h - 76 - ph;
        }

        g.fill(x0, y0, x0 + pw, y0 + ph, 0x90000000);
        g.fill(x0, y0, x0 + 2, y0 + ph, cd > 0 ? COOLING : READY);
        g.drawString(font, label, x0 + 6, y0 + 3, cd > 0 ? 0xFFBBBBBB : 0xFFFFFFFF, true);
        g.drawString(font, status, x0 + 6, y0 + 13, cd > 0 ? 0xFF9A96A8 : READY, true);

        int bx = x0 + 6;
        int by = y0 + ph - 5;
        int bw = pw - 12;
        g.fill(bx, by, bx + bw, by + 2, 0xFF2A2833);
        if (active > 0) {
            float pulse = 0.6F + 0.4F * Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.5F);
            int a = (int) (255 * pulse) << 24;
            g.fill(bx, by, bx + bw, by + 2, a | (ACTIVE & 0xFFFFFF));
        } else {
            int fill = (int) (bw * (1.0F - cd / (float) max));
            g.fill(bx, by, bx + fill, by + 2, cd > 0 ? COOLING : READY);
        }

        // Altura sobre o chão (só voando)
        if (mount instanceof FlyingMount flyer && flyer.flight().isAirborne()) {
            double alt = MountFlight.heightAboveGround(mount.asMob(), 64.0);
            Component altText = alt > 64.0 ? Component.translatable("hud.isekai_mahou.mount_altitude_high")
                    : Component.translatable("hud.isekai_mahou.mount_altitude", (int) Math.round(alt));
            g.drawString(font, altText, x0 + pw - font.width(altText) - 4, y0 - 10, 0xFFDDF6FF, true);
        }
    }

    private MountHud() {}
}
