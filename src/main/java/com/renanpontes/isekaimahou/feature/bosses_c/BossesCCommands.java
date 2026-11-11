package com.renanpontes.isekaimahou.feature.bosses_c;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou spawnboss <renegade_archmage|corrupted_witch|imprisoned_patron|corrupted_ent|lich>}: invoca o chefe
 * alguns blocos à frente de quem usou o comando (ou na posição da fonte, no console/bloco de comando).
 * Outros módulos de chefes adicionam os seus ids no mesmo subcomando (o Brigadier junta os nós).
 */
public final class BossesCCommands {
    private static final Map<String, Supplier<? extends EntityType<?>>> BOSSES = new LinkedHashMap<>();

    static {
        BOSSES.put("renegade_archmage", BossesCRegistry.RENEGADE_ARCHMAGE);
        BOSSES.put("corrupted_witch", BossesCRegistry.CORRUPTED_WITCH);
        BOSSES.put("imprisoned_patron", BossesCRegistry.IMPRISONED_PATRON);
        BOSSES.put("corrupted_ent", BossesCRegistry.CORRUPTED_ENT);
        BOSSES.put("lich", BossesCRegistry.LICH);
    }

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = MahouCommands.op("spawnboss");
            for (Map.Entry<String, Supplier<? extends EntityType<?>>> e : BOSSES.entrySet()) {
                spawn.then(Commands.literal(e.getKey()).executes(c -> spawn(c.getSource(), e.getValue().get())));
            }
            root.then(spawn);
        });
    }

    private static int spawn(CommandSourceStack source, EntityType<?> type) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            // Até 6 blocos na direção do olhar, parando antes de paredes.
            Vec3 eye = viewer.getEyePosition();
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) {
                Vec3 to = eye.add(look.normalize().scale(6.0));
                BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
                Vec3 at = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(look.normalize().scale(1.5));
                pos = new Vec3(at.x, viewer.getY(), at.z);
            }
        }
        Entity boss = type.spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.spawnboss.class_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.spawnboss.class_done", name), true);
        return 1;
    }

    private BossesCCommands() {}
}
