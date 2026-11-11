package com.renanpontes.isekaimahou.feature.bosses_d;

import java.util.Arrays;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialElement;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * {@code /mahou spawnboss trial_avatar [elemento] [rank]} e {@code /mahou spawnboss trial_sentinel [elemento] [rank]}
 * (precisa de cheats). Sem elemento: Arcano; sem rank: 1.
 */
public final class BdCommands {
    private static final SuggestionProvider<CommandSourceStack> ELEMENTS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Element.values()).map(e -> e.id), b);

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("spawnboss")
                .then(entry("trial_avatar", BdRegistries.TRIAL_AVATAR))
                .then(entry("trial_sentinel", BdRegistries.TRIAL_SENTINEL))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entry(String id, Supplier<? extends EntityType<? extends Mob>> type) {
        return Commands.literal(id)
                .executes(c -> spawn(c, id, type, Element.ARCANE.id, 1))
                .then(Commands.argument("element", StringArgumentType.word()).suggests(ELEMENTS)
                        .executes(c -> spawn(c, id, type, StringArgumentType.getString(c, "element"), 1))
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, Progression.MAX_TRIAL_RANK))
                                .executes(c -> spawn(c, id, type, StringArgumentType.getString(c, "element"),
                                        IntegerArgumentType.getInteger(c, "rank")))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String id, Supplier<? extends EntityType<? extends Mob>> type,
                             String elementId, int rank) {
        CommandSourceStack src = c.getSource();
        Element element = Element.byId(elementId);
        if (element == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.bosses_d.bad_element", elementId));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 look = src.getEntity() != null ? src.getEntity().getLookAngle().multiply(1, 0, 1) : Vec3.ZERO;
        Vec3 at = src.getPosition().add(look.lengthSqr() > 1.0E-4 ? look.normalize().scale(6) : Vec3.ZERO);
        Mob mob = type.get().create(level);
        if (mob == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.bosses_d.spawn_failed"));
            return 0;
        }
        TrialElement.write(mob, element, rank);
        float yaw = src.getEntity() != null ? src.getEntity().getYRot() + 180F : 0F;
        mob.moveTo(at.x, at.y, at.z, yaw, 0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.COMMAND, null);
        level.addFreshEntity(mob);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.bosses_d.spawned",
                entityName(id), element.displayName(), rank), true);
        return 1;
    }

    /** Nome da entidade pela chave "entity.isekai_mahou.<id>". */
    private static Component entityName(String id) {
        String key = "entity.isekai_mahou." + id;
        return Component.translatable(key);
    }

    private BdCommands() {}
}
