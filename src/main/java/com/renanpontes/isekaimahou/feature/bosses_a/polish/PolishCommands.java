package com.renanpontes.isekaimahou.feature.bosses_a.polish;

import java.util.Comparator;

import javax.annotation.Nullable;

import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v1.0 (boss_polish): {@code /mahou bosspolish <kill|laststand|taunt>} — testa o espetáculo novo no chefe MAIS PERTO
 * (até 48 blocos; vale para os chefes de todos os módulos, sem depender das classes deles):
 * <ul>
 *   <li>{@code kill}: derruba o chefe → golpe final, câmera da derrota, morte em três atos, corpo se desfazendo;</li>
 *   <li>{@code laststand}: deixa o chefe com 14% de vida → fala de último fôlego (e as trocas de fase no caminho);</li>
 *   <li>{@code taunt}: o chefe MATA quem usou o comando (use no survival) → provocação.</li>
 * </ul>
 */
public final class PolishCommands {
    private static final double RANGE = 48.0;

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("bosspolish")
                .then(Commands.literal("kill").executes(c -> run(c, 0)))
                .then(Commands.literal("laststand").executes(c -> run(c, 1)))
                .then(Commands.literal("taunt").executes(c -> run(c, 2)))));
    }

    private static int run(CommandContext<CommandSourceStack> c, int what) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        LivingEntity boss = nearestBoss(level, src.getPosition());
        if (boss == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.bosspolish.none"));
            return 0;
        }
        switch (what) {
            case 0 -> boss.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            case 1 -> boss.setHealth(Math.max(1F, boss.getMaxHealth() * 0.14F));
            default -> {
                if (!(src.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
                    src.sendFailure(Component.translatable("command.isekai_mahou.bosspolish.survival"));
                    return 0;
                }
                player.hurt(level.damageSources().mobAttack(boss), Float.MAX_VALUE);
            }
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.bosspolish.done", boss.getDisplayName()), false);
        return 1;
    }

    @Nullable
    private static LivingEntity nearestBoss(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, RANGE * 2, RANGE * 2, RANGE * 2),
                        e -> e.isAlive() && BossKit.isBossLike(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
    }

    private PolishCommands() {}
}
