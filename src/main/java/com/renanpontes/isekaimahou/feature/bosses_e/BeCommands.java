package com.renanpontes.isekaimahou.feature.bosses_e;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.DragonCurses;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialChain;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;
import com.renanpontes.isekaimahou.feature.bosses_e.world.ArenaTerraform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * {@code /mahou spawnboss primordial_dragon [rift]} — invoca o Dragão Primordial (dormente na sua frente, ou rasgando
 * a Fenda sobre você como depois do Arauto). {@code /mahou dragon restore} — devolve agora o terreno temporário da
 * arena deste nível. {@code /mahou dragon curse <estátua|correntes|esquecimento|apagar|dreno|ruína|limpar>} — aplica em
 * você uma das maldições do dragão (para testar o resgate agachado, o HUD e as mensagens). Precisam de cheats.
 */
public final class BeCommands {
    private static final List<String> CURSES = List.of("statue", "chains", "forget", "erase", "drain", "ruin", "clear");

    public static void register() {
        MahouCommands.extend((root, ctx) -> root
                .then(MahouCommands.op("spawnboss").then(Commands.literal(PrimordialDragon.ID)
                        .executes(c -> spawn(c, false))
                        .then(Commands.literal("rift").executes(c -> spawn(c, true)))))
                .then(MahouCommands.op("dragon")
                        .then(Commands.literal("restore").executes(BeCommands::restore))
                        .then(Commands.literal("curse").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(CURSES, b))
                                .executes(BeCommands::curse)))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, boolean rift) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        PrimordialDragon d = BeRegistries.PRIMORDIAL_DRAGON.get().create(level);
        if (d == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.primordial_dragon.failed"));
            return 0;
        }
        Vec3 pos = src.getPosition();
        Vec3 at = pos;
        if (!rift) {
            Vec3 look = src.getEntity() != null ? src.getEntity().getLookAngle().multiply(1, 0, 1) : Vec3.ZERO;
            at = pos.add(look.lengthSqr() > 1.0E-4 ? look.normalize().scale(12) : Vec3.ZERO);
        } else {
            d.getPersistentData().putBoolean(PrimordialDragon.RIFT_TAG, true);
            BossKit.setArena(d, pos, PrimordialDragon.DEFAULT_ARENA + 4);
        }
        float yaw = src.getEntity() != null ? src.getEntity().getYRot() + 180F : 0F;
        d.moveTo(at.x, at.y, at.z, yaw, 0F);
        EventHooks.finalizeMobSpawn(d, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.COMMAND, null);
        level.addFreshEntity(d);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.primordial_dragon.spawned"), true);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        ArenaTerraform t = ArenaTerraform.peek(level);
        int n = t == null ? 0 : t.restoreAll(level);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.primordial_dragon.restored", n), true);
        return n;
    }

    private static int curse(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        ServerLevel level = p.serverLevel();
        switch (kind) {
            case "statue" -> DragonCurses.statue(p, 200, null);
            case "forget" -> DragonCurses.curse(p, 500, null);
            case "erase" -> DragonCurses.eraseLoadout(p, 400, null);
            case "drain" -> DragonCurses.drainEssence(p, DragonCurses.MAX_DRAIN, 800, null);
            case "ruin" -> DragonCurses.ruin(p, 1, 1200, null);
            case "clear" -> DragonCurses.clear(p);
            case "chains" -> {
                PrimordialChain ch = BeRegistries.PRIMORDIAL_CHAIN.get().create(level);
                if (ch == null) return 0;
                Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
                ch.moveTo(at.x, p.getY(), at.z, 0F, 0F);
                ch.setup(null, p, 1F);
                level.addFreshEntity(ch);
            }
            default -> {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.primordial_dragon.curse_unknown", String.join(", ", CURSES)));
                return 0;
            }
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.primordial_dragon.curse_applied", kind), false);
        return 1;
    }

    private BeCommands() {}
}
