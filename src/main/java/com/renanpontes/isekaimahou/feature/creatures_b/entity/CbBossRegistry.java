package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros dos chefes do módulo: a arma arremessada ({@code boss_thrown_axe}) e
 * {@code /mahou spawnboss <orc_lord|minotaur>} (invoca o chefe à frente de quem usou; precisa de cheats).
 */
public final class CbBossRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<BossThrownAxe>> THROWN_AXE = ENTITIES.register("boss_thrown_axe",
            () -> EntityType.Builder.<BossThrownAxe>of(BossThrownAxe::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .clientTrackingRange(8).updateInterval(2).build("boss_thrown_axe"));

    private static final Map<String, Supplier<? extends EntityType<?>>> BOSSES = new LinkedHashMap<>();

    static {
        BOSSES.put("orc_lord", CbRegistries.ORC_LORD);
        BOSSES.put("minotaur", CbRegistries.MINOTAUR);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
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
            source.sendFailure(Component.translatable("command.isekai_mahou.spawnboss.cb_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.spawnboss.cb_done", name), true);
        return 1;
    }

    private CbBossRegistry() {}
}
