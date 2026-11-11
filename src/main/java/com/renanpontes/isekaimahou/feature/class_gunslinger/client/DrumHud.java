package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Tambor ao lado da mira (só com arma de mana na mão): um cartucho aceso por bala (o revólver desenha o tambor
 * redondo; as outras armas, uma fileira), o anel de progresso da recarga e o aviso "Recarregar [F]" com o tambor vazio.
 * Fica à direita da mira (o aviso de arena do núcleo é embaixo dela; nada perto do minimapa).
 */
public final class DrumHud implements LayeredDraw.Layer {
    private static final int LIT = 0xFFF2C14E;
    private static final int LIT_HI = 0xFFFFF2B0;
    private static final int EMPTY = 0x70FFFFFF;
    private static final int SHADOW = 0x90000000;

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (ManaGunItem.of(stack) == null) stack = mc.player.getOffhandItem();
        ManaGunItem gun = ManaGunItem.of(stack);
        if (gun == null) return;
        int rounds = ManaGunItem.rounds(stack);
        GunKind kind = gun.kind();
        int cx = g.guiWidth() / 2 + 14;
        int cy = g.guiHeight() / 2 + 6;
        float partial = dt.getGameTimeDeltaPartialTick(true);
        float reload = GunAnimations.reloadProgress(partial);
        if (kind == GunKind.REVOLVER) {
            // tambor de 6: as câmaras giram um pouco a cada tiro
            int r = 7;
            int ox = cx + 8, oy = cy + 1;
            for (int i = 0; i < kind.rounds; i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * i / kind.rounds + (reload >= 0 ? reload * Math.PI * 4 : 0);
                int x = ox + (int) Math.round(Math.cos(a) * r);
                int y = oy + (int) Math.round(Math.sin(a) * r);
                pip(g, x, y, i < rounds);
            }
            g.fill(ox, oy, ox + 2, oy + 2, SHADOW);
        } else {
            int per = Math.min(kind.rounds, 8);
            for (int i = 0; i < kind.rounds; i++) {
                int col = i % per, row = i / per;
                pip(g, cx + 2 + col * 5, cy - 3 + row * 6, i < rounds);
            }
        }
        Font font = mc.font;
        if (reload >= 0) {
            int w = 34;
            int bx = cx + 1, by = cy + 13;
            g.fill(bx - 1, by - 1, bx + w + 1, by + 3, SHADOW);
            g.fill(bx, by, bx + Mth.ceil(w * reload), by + 2, LIT);
            MahouUi.smallText(g, font, Component.translatable("hud.isekai_mahou.gunslinger.reloading"), bx, by + 4, 0.6F, LIT_HI, true);
        } else if (rounds == 0) {
            MahouUi.smallText(g, font, Component.translatable("hud.isekai_mahou.gunslinger.reload_hint",
                    mc.options.keySwapOffhand.getTranslatedKeyMessage()), cx + 1, cy + 13, 0.65F, 0xFFFF8A6A, true);
        }
    }

    private static void pip(GuiGraphics g, int x, int y, boolean lit) {
        g.fill(x - 1, y - 1, x + 3, y + 4, SHADOW);
        if (lit) {
            g.fill(x, y, x + 2, y + 3, LIT);
            g.fill(x, y, x + 2, y + 1, LIT_HI);
        } else {
            g.fill(x, y, x + 2, y + 3, EMPTY);
        }
    }
}
