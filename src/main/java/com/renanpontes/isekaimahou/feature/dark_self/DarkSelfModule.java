package com.renanpontes.isekaimahou.feature.dark_self;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.dark_self.entity.DarkSelfEntity;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Módulo "dark_self" — v1.1: a <b>Sombra</b> do nível 100 ("Dark Link"). No nível 100 o jogador não sobe mais até vencer
 * a própria sombra; andando com a mana cheia, longe das cidades, a sombra dele some e, segundos depois, uma cópia escura
 * dele sai do chão e luta com as magias dele. Ver {@link DarkSelfService} e {@link DarkSelfEntity}.
 * <p>
 * Comandos (cheats): {@code /mahou darkself status|spawn|reset|pass [jogadores]}.
 */
public class DarkSelfModule implements Module {
    @Override
    public String id() {
        return "dark_self";
    }

    @Override
    public void init(IEventBus modBus) {
        DarkSelfRegistry.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) ->
                e.put(DarkSelfRegistry.DARK_SELF.get(), DarkSelfEntity.createAttributes().build()));
        modBus.addListener(DarkSelfNetwork::register);
        DarkSelfService.init(NeoForge.EVENT_BUS);
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("darkself")
                .then(sub("status", (src, sp) -> {
                    src.sendSuccess(() -> DarkSelfService.status(sp), false);
                    return true;
                }, null))
                .then(sub("spawn", (src, sp) -> DarkSelfService.forceSpawn(sp), "command.isekai_mahou.dark_self.spawn"))
                .then(sub("reset", (src, sp) -> {
                    DarkSelfService.reset(sp);
                    return true;
                }, "command.isekai_mahou.dark_self.reset"))
                .then(sub("pass", (src, sp) -> {
                    DarkSelfService.pass(sp);
                    return true;
                }, "command.isekai_mahou.dark_self.pass"))));
    }

    private interface Action {
        /** @return false = não deu (ex.: já há um duelo) */
        boolean run(CommandSourceStack src, ServerPlayer sp);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> sub(String name, Action action, String doneKey) {
        return Commands.literal(name)
                .executes(c -> run(c.getSource(), List.of(c.getSource().getPlayerOrException()), action, doneKey))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> run(c.getSource(), EntityArgument.getPlayers(c, "targets"), action, doneKey)));
    }

    private static int run(CommandSourceStack src, Collection<ServerPlayer> targets, Action action, String doneKey)
            throws CommandSyntaxException {
        int n = 0;
        for (ServerPlayer sp : targets) {
            if (action.run(src, sp)) {
                n++;
                if (doneKey != null) src.sendSuccess(() -> Component.translatable(doneKey, sp.getName()), true);
            } else {
                src.sendFailure(Component.translatable("command.isekai_mahou.dark_self.busy", sp.getName()));
            }
        }
        return n;
    }
}
