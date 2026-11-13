package com.renanpontes.isekaimahou.core.boss;

import com.renanpontes.isekaimahou.core.api.BossArena;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * v1.3 — "a arena tem que ser inquebrável enquanto tiver luta". Fecha os buracos que o {@code BossArena} (núcleo
 * social) deixava, sem mexer nele:
 * <ul>
 *   <li><b>Magias de chefe</b>: os chefes do mod eram liberados do mobGriefing (para efeitos temporários) — então um
 *       glifo conjurado por um chefe que quebra blocos direto (sem passar pela Destruction) furava a arena. Agora,
 *       DENTRO de uma arena selada, nenhum mob (chefe, invocação, lacaio) tem mobGriefing;</li>
 *   <li><b>Quebra de bloco por mob</b> ({@code LivingDestroyBlockEvent}: Wither, devastador, magias de mob que testam o
 *       bloco): cancelada em arena selada e nas áreas sempre protegidas;</li>
 *   <li><b>Lava</b>: lava não espalha fogo dentro da arena selada (o fogo queimaria o cenário);</li>
 *   <li><b>Terra arada</b> não é pisoteada dentro da arena.</li>
 * </ul>
 * Explosões, Destruction e quebra/colocação por jogadores continuam no {@code BossArenaService}. Servidor.
 */
final class ArenaGuard {
    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityMobGriefingEvent.class, ArenaGuard::onGriefing);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingDestroyBlockEvent.class, ArenaGuard::onMobBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockEvent.FluidPlaceBlockEvent.class, ArenaGuard::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockEvent.FarmlandTrampleEvent.class, ArenaGuard::onTrample);
    }

    private static void onGriefing(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent == null || ent instanceof Player || !e.canGrief() || !(ent.level() instanceof ServerLevel level)) return;
        if (BossArena.at(level, ent.position()) != null) e.setCanGrief(false);
    }

    private static void onMobBreak(LivingDestroyBlockEvent e) {
        if (e.getEntity() instanceof Player p && p.getAbilities().instabuild) return;
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        if (BossArena.isProtected(level, e.getPos())) e.setCanceled(true);
    }

    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        // Só o fogo que a lava espalha (queimaria a arena); a mistura lava+água segue normal
        if (!(e.getNewState().getBlock() instanceof BaseFireBlock)) return;
        if (BossArena.at(level, net.minecraft.world.phys.Vec3.atCenterOf(e.getPos())) != null) e.setCanceled(true);
    }

    private static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (e.getLevel() instanceof ServerLevel level && BossArena.at(level, net.minecraft.world.phys.Vec3.atCenterOf(e.getPos())) != null) {
            e.setCanceled(true);
        }
    }

    private ArenaGuard() {}
}
