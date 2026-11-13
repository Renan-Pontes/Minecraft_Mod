package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Roteia o botão direito nas peças dos Enigmas para o {@link EnigmaRun} certo (antes do bloco/ítem agir). Assim os
 * enigmas podem usar blocos comuns (barris, estantes, concreto) sem abrir menus nem colocar blocos por cima.
 */
public final class EnigmaEvents {
    /** Último clique tratado na mão principal (para a mão secundária não agir na mesma peça no mesmo tick). */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnigmaEvents::onRightClick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST.remove(e.getEntity().getUUID()));
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getEntity() instanceof ServerPlayer sp) || sp.isSpectator()) return;
        BlockPos pos = e.getPos();
        PuzzleCoreBlockEntity core = PuzzleCoreBlockEntity.enigmaAt(sl, pos);
        if (core == null || core.enigma() == null) return;
        long now = sl.getGameTime();
        if (e.getHand() == InteractionHand.OFF_HAND) {
            long[] last = LAST.get(sp.getUUID());
            if (last != null && last[0] == now && last[1] == pos.asLong()) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        Enigma.Use r = core.enigma().onUse(sp, pos);
        if (r == Enigma.Use.PASS) return;
        LAST.put(sp.getUUID(), new long[] {now, pos.asLong()});
        if (r == Enigma.Use.CONSUME) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private EnigmaEvents() {}
}
