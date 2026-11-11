package com.renanpontes.isekaimahou.feature.hueco_mundo;

import java.util.function.Supplier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.HmBoss;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Comandos de teste (op): {@code /mahou hueco enter <hollow_world|fey_realm>}, {@code leave}, {@code gate}
 * (abre o portal do reino à frente), {@code boss <hollow_king|sand_empress|thorn_queen>} (invoca ao lado) e
 * {@code rebuild} (refaz os moldes do reino atual na próxima chegada).
 */
public final class HmCommands {

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> hueco = MahouCommands.op("hueco");
            LiteralArgumentBuilder<CommandSourceStack> enter = Commands.literal("enter");
            LiteralArgumentBuilder<CommandSourceStack> gate = Commands.literal("gate");
            for (HmRealm r : HmRealm.values()) {
                enter.then(Commands.literal(r.id).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return HmTravel.enter(p, r) ? 1 : 0;
                }));
                gate.then(Commands.literal(r.id).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return HmTravel.openGate(p.serverLevel(), HmTravel.spotAhead(p), r, 600, p.getYRot()) != null ? 1 : 0;
                }));
            }
            hueco.then(enter);
            hueco.then(gate);
            hueco.then(Commands.literal("leave").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                HmRealm r = HmRealm.of(p.level().dimension());
                if (r == null) {
                    c.getSource().sendFailure(Component.translatable("message.isekai_mahou.hueco_mundo.not_in_realm"));
                    return 0;
                }
                return HmTravel.leave(p, r) ? 1 : 0;
            }));
            hueco.then(Commands.literal("rebuild").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                HmRealm r = HmRealm.of(p.level().dimension());
                if (r == null) {
                    c.getSource().sendFailure(Component.translatable("message.isekai_mahou.hueco_mundo.not_in_realm"));
                    return 0;
                }
                HmSites.resetBuilt(p.serverLevel());
                HmSites.ensureBuilt(p.serverLevel(), r);
                c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.hueco_mundo.rebuilt"), true);
                return 1;
            }));
            LiteralArgumentBuilder<CommandSourceStack> boss = Commands.literal("boss");
            boss.then(Commands.literal("hollow_king").executes(c -> spawn(c.getSource(), HmRegistries.HOLLOW_KING)));
            boss.then(Commands.literal("sand_empress").executes(c -> spawn(c.getSource(), HmRegistries.SAND_EMPRESS)));
            boss.then(Commands.literal("thorn_queen").executes(c -> spawn(c.getSource(), HmRegistries.THORN_QUEEN)));
            hueco.then(boss);
            root.then(hueco);
        });
    }

    private static int spawn(CommandSourceStack src, Supplier<? extends EntityType<? extends HmBoss>> type) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            ServerLevel level = p.serverLevel();
            HmBoss b = type.get().create(level);
            if (b == null) return 0;
            var at = HmTravel.spotAhead(p);
            b.moveTo(at.x, at.y, at.z, p.getYRot() + 180F, 0F);
            b.finalizeSpawn(level, level.getCurrentDifficultyAt(p.blockPosition()), MobSpawnType.COMMAND, null);
            level.addFreshEntity(b);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private HmCommands() {}
}
