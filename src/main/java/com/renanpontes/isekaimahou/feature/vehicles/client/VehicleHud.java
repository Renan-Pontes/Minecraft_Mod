package com.renanpontes.isekaimahou.feature.vehicles.client;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Medidor pequeno à esquerda da barra de itens enquanto se pilota um veículo mágico: de onde vem a energia
 * (sua mana / tanque / sem energia), a carga do tanque de cristais e (onda 10) a vida do veículo. Três linhas curtas e
 * duas barras finas — nada além disso.
 */
public final class VehicleHud {
    private static final int TANK = 0xFF7FF5E6;
    private static final int EMPTY = 0xFFE0563B;
    private static final int LIFE = 0xFF7DDA6B;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!(mc.player.getControlledVehicle() instanceof MagicVehicle vehicle)) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        byte src = vehicle.energySource();
        int pct = vehicle.getFuel() * 100 / MagicVehicle.MAX_FUEL;
        Component status = switch (src) {
            // "Mana" / "Vigor" / "Foco": o recurso da classe de quem pilota
            case MagicVehicle.SRC_MANA -> Component.translatable("hud.isekai_mahou.vehicle_src_mana", MagicApi.resourceName(mc.player));
            case MagicVehicle.SRC_TANK -> Component.translatable("hud.isekai_mahou.vehicle_src_tank");
            case MagicVehicle.SRC_FREE -> Component.translatable("hud.isekai_mahou.vehicle_src_free");
            default -> Component.translatable("hud.isekai_mahou.vehicle_src_none");
        };
        Component tank = Component.translatable("hud.isekai_mahou.vehicle_tank", pct);
        int hp = Math.round(vehicle.healthFraction() * 100);
        Component life = Component.translatable("hud.isekai_mahou.vehicle_health", hp);
        int pw = Math.max(font.width(status), Math.max(font.width(tank), font.width(life))) + 10;
        int ph = 40;
        int x0 = w / 2 - 91 - 36 - pw;
        int y0 = h - ph - 2;
        if (x0 < 4) { // tela estreita: acima da barra de itens
            x0 = w / 2 - pw / 2;
            y0 = h - 76 - ph;
        }
        boolean none = src == MagicVehicle.SRC_NONE;
        g.fill(x0, y0, x0 + pw, y0 + ph, 0x90000000);
        g.fill(x0, y0, x0 + 2, y0 + ph, none ? EMPTY : TANK);
        g.drawString(font, status, x0 + 6, y0 + 3, none ? EMPTY : 0xFFFFFFFF, true);
        g.drawString(font, tank, x0 + 6, y0 + 13, 0xFFBBBBBB, true);
        g.drawString(font, life, x0 + 6, y0 + 23, hp < 25 ? EMPTY : 0xFFBBBBBB, true);
        int bx = x0 + 6;
        int by = y0 + ph - 7;
        int bw = pw - 12;
        g.fill(bx, by, bx + bw, by + 2, 0xFF2A2833);
        g.fill(bx, by, bx + bw * pct / 100, by + 2, TANK);
        g.fill(bx, by + 3, bx + bw, by + 5, 0xFF2A2833);
        g.fill(bx, by + 3, bx + bw * hp / 100, by + 5, hp < 25 ? EMPTY : hp < 50 ? 0xFFE8C25A : LIFE);
    }

    private VehicleHud() {}
}
