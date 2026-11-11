package com.renanpontes.isekaimahou.feature.bosses_b;

import java.util.function.Supplier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.BbBoss;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * {@code /mahou spawnboss <abyss_sovereign|seraph|twilight_herald> [trial]}: invoca o chefe 6 blocos à frente (precisa de
 * cheats). Com {@code trial}, o chefe nasce em modo Provação (sem reset por ociosidade, arena de raio 22 ao redor) — para
 * testar como ele se comporta numa Câmara de Provação.
 */
public final class BbCommands {
    private static final double TEST_ARENA_RADIUS = 22.0;

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("spawnboss")
                .then(boss("abyss_sovereign", BbRegistries.ABYSS_SOVEREIGN))
                .then(boss("seraph", BbRegistries.SERAPH))
                .then(boss("twilight_herald", BbRegistries.TWILIGHT_HERALD))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boss(String id, Supplier<? extends EntityType<? extends BbBoss>> type) {
        return Commands.literal(id)
                .executes(c -> spawn(c, type, false))
                .then(Commands.literal("trial").executes(c -> spawn(c, type, true)));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Supplier<? extends EntityType<? extends BbBoss>> type, boolean trial) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        Vec3 look = src.getEntity() != null ? src.getEntity().getLookAngle().multiply(1, 0, 1) : Vec3.ZERO;
        Vec3 at = src.getPosition().add(look.lengthSqr() > 1.0E-4 ? look.normalize().scale(6) : Vec3.ZERO);
        BbBoss boss = type.get().create(level);
        if (boss == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.bosses_b.spawn_failed"));
            return 0;
        }
        float yaw = src.getEntity() != null ? src.getEntity().getYRot() + 180F : 0F;
        boss.moveTo(at.x, at.y, at.z, yaw, 0F);
        EventHooks.finalizeMobSpawn(boss, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.COMMAND, null);
        if (trial) {
            BossKit.markTrialBoss(boss);
            BossKit.setArena(boss, at, TEST_ARENA_RADIUS);
        }
        level.addFreshEntity(boss);
        if (trial) {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.bosses_b.spawned_trial", boss.getDisplayName(),
                    (int) TEST_ARENA_RADIUS), true);
        } else {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.bosses_b.spawned", boss.getDisplayName()), true);
        }
        return 1;
    }

    private BbCommands() {}
}
