package com.renanpontes.isekaimahou.feature.stands;

import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "stands" (v1.1) — duas coisas que o dono pediu juntas:
 * <ol>
 *   <li><b>Evoluções próprias dos glifos</b> ("como os do Tusk"): {@link Evolutions} declara os estágios (nome, nível,
 *       cor, peças de comportamento) de glifos de anime — Chidori → Raikiri → Kirin, Boost → Balance Breaker →
 *       Juggernaut Drive, Gomu Gomu → Gear 2 → Gear 4... — e {@link EvolutionEffects} executa as peças.</li>
 *   <li><b>Stands</b>: quando quem é da classe Steel Ball usa um glifo de JoJo, um espírito translúcido aparece e faz o
 *       golpe ({@link StandTriggers} no servidor → {@link StandNet} → desenho no cliente). Outras classes: nenhum
 *       stand, o glifo funciona igual.</li>
 * </ol>
 * Ver docs/v11/s_stands_evolutions.md.
 */
public class StandsModule implements Module {
    @Override
    public String id() {
        return "stands";
    }

    @Override
    public void init(IEventBus modBus) {
        Evolutions.register();
        modBus.addListener(StandNet::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, false, SpellDamageEvent.Pre.class, EvolutionEffects::onDamagePre);
        bus.addListener(EventPriority.LOW, false, SpellDamageEvent.Post.class, EvolutionEffects::onDamagePost);
        bus.addListener(EventPriority.LOW, false, SpellCastEvent.Post.class, EvolutionEffects::onCast);
        bus.addListener(EventPriority.LOWEST, false, SpellCastEvent.Post.class, StandTriggers::onCast);
        bus.addListener(ServerTickEvent.Post.class, e -> EvolutionEffects.tick(e.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            EvolutionEffects.forget(e.getEntity().getUUID());
            StandTriggers.forget(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            EvolutionEffects.clear();
            StandTriggers.clear();
        });

        // Teste (op): /mahou stand <stand> [ação] — mostra o stand em você por 5 s, de qualquer classe (só para ver o visual)
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> cmd = MahouCommands.op("stand");
            for (Stand s : Stand.values()) {
                String name = s.name().toLowerCase(Locale.ROOT);
                LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name).executes(c -> preview(c, s, Stand.Action.GUARD));
                for (Stand.Action a : Stand.Action.values()) {
                    node.then(Commands.literal(a.name().toLowerCase(Locale.ROOT)).executes(c -> preview(c, s, a)));
                }
                cmd.then(node);
            }
            root.then(cmd);
        });
    }

    private static int preview(CommandContext<CommandSourceStack> c, Stand s, Stand.Action a) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        StandNet.showAll(sp, s, a, 100);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.stands.preview",
                s.name().toLowerCase(Locale.ROOT), a.name().toLowerCase(Locale.ROOT)), false);
        return 1;
    }
}
