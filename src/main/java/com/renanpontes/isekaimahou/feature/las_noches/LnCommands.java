package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * Comandos de teste (op): {@code /mahou lasnoches status}, {@code rebuild}, {@code tp <lugar>} (dome, top, throne, gate,
 * barragan, ulquiorra, grimmjow), {@code summon <espada>} (força a chegada na sala) e {@code reset} (Espada voltam já).
 */
final class LnCommands {

    static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> ln = MahouCommands.op("lasnoches");
            ln.then(Commands.literal("status").executes(c -> {
                ServerLevel level = hollow(c.getSource());
                if (level == null) return 0;
                LnData d = LnData.get(level);
                int pct = Math.round(LnBuilder.progress(d) * 100);
                c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.las_noches.status", d.stage, pct,
                        String.valueOf(d.center), d.defeatedAt.keySet().toString()), false);
                return 1;
            }));
            ln.then(Commands.literal("rebuild").executes(c -> {
                ServerLevel level = hollow(c.getSource());
                if (level == null) return 0;
                LnEvents.rebuild(level);
                c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.las_noches.rebuilding"), true);
                return 1;
            }));
            ln.then(Commands.literal("reset").executes(c -> {
                ServerLevel level = hollow(c.getSource());
                if (level == null) return 0;
                LnData d = LnData.get(level);
                d.defeatedAt.clear();
                d.setDirty();
                c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.las_noches.reset"), true);
                return 1;
            }));
            LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp");
            tp.then(Commands.literal("dome").executes(c -> tp(c.getSource(), new BlockPos(0, 1, 12))));
            tp.then(Commands.literal("top").executes(c -> tp(c.getSource(), LnLayout.EXIT_HIGH)));
            tp.then(Commands.literal("throne").executes(c -> tp(c.getSource(), new BlockPos(0, 1, -44))));
            tp.then(Commands.literal("gate").executes(c -> tp(c.getSource(), new BlockPos(0, 1, 80))));
            for (LnLayout.Chamber ch : LnLayout.CHAMBERS) {
                tp.then(Commands.literal(ch.espadaId()).executes(c -> tp(c.getSource(), ch.center())));
            }
            ln.then(tp);
            LiteralArgumentBuilder<CommandSourceStack> summon = Commands.literal("summon");
            for (LnLayout.Chamber ch : LnLayout.CHAMBERS) {
                summon.then(Commands.literal(ch.espadaId()).executes(c -> {
                    ServerLevel level = hollow(c.getSource());
                    if (level == null) return 0;
                    LnData d = LnData.get(level);
                    Optional<EntityType<?>> type = LnEspada.type(ch);
                    if (!d.built() || type.isEmpty()) {
                        c.getSource().sendFailure(Component.translatable("message.isekai_mahou.las_noches.no_espada", ch.espadaId()));
                        return 0;
                    }
                    LnEspada.summon(level, d, ch, type.get(), false);
                    return 1;
                }));
            }
            ln.then(summon);
            root.then(ln);
        });
    }

    private static ServerLevel hollow(CommandSourceStack src) {
        ServerLevel level = src.getServer().getLevel(HmRealm.HOLLOW.key);
        if (level == null) src.sendFailure(Component.translatable("message.isekai_mahou.hueco_mundo.not_in_realm"));
        return level;
    }

    private static int tp(CommandSourceStack src, BlockPos rel) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            ServerLevel level = hollow(src);
            if (level == null) return 0;
            LnData d = LnData.get(level);
            if (d.center == null) {
                // Ainda não construída: entra no Mundo Oco (a construção começa com alguém lá).
                return HmTravel.enter(p, HmRealm.HOLLOW) ? 1 : 0;
            }
            BlockPos to = d.center.offset(rel);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private LnCommands() {}
}
