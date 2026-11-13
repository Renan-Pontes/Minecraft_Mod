package com.renanpontes.isekaimahou.feature.vehicles.client;

import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Entradas do piloto de um veículo mágico: W/S/A/D, pulo = subir, agachar = descer.
 * Como nas montarias voadoras, o agachar usado para descer no alto é "engolido" (não chega ao servidor), então
 * ninguém desmonta no meio do voo; perto do chão (ou em veículo de terra/água) agachar desmonta normalmente.
 */
public final class VehicleInput {
    /** O agachar atual começou no alto: continua valendo como "descer" até ser solto. */
    private static boolean sneakLatched;

    static void onMovementInput(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer player)) return;
        if (!(player.getControlledVehicle() instanceof MagicVehicle vehicle)) {
            sneakLatched = false;
            return;
        }
        Input in = e.getInput();
        boolean descend = false;
        if (vehicle.kind().flies()) {
            boolean high = vehicle.heightAbove(2.0) > 1.4;
            if (!in.shiftKeyDown) sneakLatched = false;
            else if (high) sneakLatched = true;
            descend = in.shiftKeyDown && sneakLatched;
        }
        vehicle.setInput(in.forwardImpulse, in.leftImpulse, in.jumping, descend);
        if (descend) in.shiftKeyDown = false;
    }

    private VehicleInput() {}
}
