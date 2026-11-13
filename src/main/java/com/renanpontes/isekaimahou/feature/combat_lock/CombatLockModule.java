package com.renanpontes.isekaimahou.feature.combat_lock;

import java.util.List;

import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Módulo "combat_lock" — v1.0:
 * <ul>
 *   <li><b>Travar o alvo</b>: tecla trava num inimigo; a câmera acompanha (3ª pessoa por cima do ombro, 1ª pessoa com mira
 *       suave), marcador discreto, roda/tecla troca de alvo, solta sozinho. Cliente: {@code client/LockOn}; o servidor
 *       ({@link LockServer}) só acerta a mira das magias no alvo.</li>
 *   <li><b>Aviso "!!!"</b>: golpe forte de monstro comum ou ataque pelas costas ({@link ThreatWarnings} +
 *       {@code client/ThreatHud}).</li>
 * </ul>
 * O <b>bloqueio com aparo</b> da mesma entrega fica no núcleo das animações de combate
 * ({@code core/anim/GuardService}, {@code client/anim/GuardInput}), junto da esquiva. Comandos de teste:
 * {@code /mahou combat_lock info|warn [heavy|behind]} e {@code /mahou guard info|hit [dano] [ticks]}.
 */
public class CombatLockModule implements Module {
    @Override
    public String id() {
        return "combat_lock";
    }

    @Override
    public void init(IEventBus modBus) {
        LockConfig.register();
        modBus.addListener(CombatLockModule::registerPayloads);
        LockServer.init(NeoForge.EVENT_BUS);
        ThreatWarnings.init(NeoForge.EVENT_BUS);
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("combat_lock")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("warn").executes(c -> warn(c.getSource(), LockPayloads.WARN_HEAVY))
                        .then(Commands.literal("heavy").executes(c -> warn(c.getSource(), LockPayloads.WARN_HEAVY)))
                        .then(Commands.literal("behind").executes(c -> warn(c.getSource(), LockPayloads.WARN_BEHIND))))));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("combat_lock-1");
        r.playToServer(LockPayloads.Target.TYPE, LockPayloads.Target.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) LockServer.receive(sp, p.entityId());
        }));
        r.playToClient(LockPayloads.Warn.TYPE, LockPayloads.Warn.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.feature.combat_lock.client.ThreatHud.receive(p)));
    }

    // ---------------- Teste ----------------
    private static int info(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        LivingEntity t = LockServer.target(p);
        Component name = t == null ? Component.translatable("command.isekai_mahou.combat_lock.none") : t.getDisplayName();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_lock.info", name,
                LockConfig.on(LockConfig.AIM_ASSIST), LockConfig.on(LockConfig.WARNINGS_ENABLED)), false);
        return 1;
    }

    /** Manda um "!!!" de teste: do monstro mais próximo (até 16 blocos) ou, sem monstro, só o aviso na borda. */
    private static int warn(CommandSourceStack source, byte kind) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        List<Mob> mobs = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), Mob::isAlive);
        Mob nearest = null;
        for (Mob m : mobs) {
            if (nearest == null || m.distanceToSqr(p) < nearest.distanceToSqr(p)) nearest = m;
        }
        PacketDistributor.sendToPlayer(p, new LockPayloads.Warn(nearest == null ? -1 : nearest.getId(), kind));
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_lock.warn"), false);
        return 1;
    }
}
