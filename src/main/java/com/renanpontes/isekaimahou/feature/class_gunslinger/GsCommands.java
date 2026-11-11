package com.renanpontes.isekaimahou.feature.class_gunslinger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_gunslinger.duel.DuelService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.PathService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Subcomandos de teste (cheats): {@code /mahou gunslinger rank <1-5>} (concede os ranks até N),
 * {@code /mahou gunslinger duel <1|2>} (duelo agora, sem esperar o meio-dia) e {@code /mahou gunslinger reload}
 * (enche o tambor da arma na mão).
 */
public final class GsCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("gunslinger")
                .then(Commands.literal("rank").then(Commands.argument("n", IntegerArgumentType.integer(1, TriggerPath.MAX_RANK))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            int n = IntegerArgumentType.getInteger(c, "n");
                            for (int i = 1; i <= n; i++) PathService.grant(sp, i);
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gunslinger.rank", n), false);
                            return 1;
                        })))
                .then(Commands.literal("duel").then(Commands.argument("tier", IntegerArgumentType.integer(1, 2))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            boolean ok = DuelService.start(sp, IntegerArgumentType.getInteger(c, "tier"), true);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("reload").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    var stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
                    ManaGunItem gun = ManaGunItem.of(stack);
                    if (gun == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.gunslinger.no_gun"));
                        return 0;
                    }
                    ManaGunItem.setRounds(stack, gun.kind().rounds);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gunslinger.reloaded"), false);
                    return 1;
                }))));
    }

    private GsCommands() {}
}
