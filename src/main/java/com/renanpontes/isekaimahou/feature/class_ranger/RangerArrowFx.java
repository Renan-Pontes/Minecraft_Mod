package com.renanpontes.isekaimahou.feature.class_ranger;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.RainArrows;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Rastro de vento nas flechas do Caçador: quando uma flecha de um Caçador (arco, glifos, chuvas) entra no mundo, o
 * servidor manda só o id dela; o cliente segue a flecha e desenha a espiral a cada tick (nenhuma partícula do servidor).
 */
public final class RangerArrowFx {
    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || !(e.getEntity() instanceof AbstractArrow arrow)) return;
        boolean rain = arrow.getTags().contains(RainArrows.TAG);
        if (!rain && !(arrow.getOwner() instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.RANGER)) return;
        RangerFxNet.at(arrow.level(), RangerFxNet.ARROW_TRAIL, arrow.position(), Vec3.ZERO, 1F, MagicPath.RANGER.rgb(), arrow.getId(),
                arrow.isCritArrow() ? 1 : 0);
    }

    private RangerArrowFx() {}
}
