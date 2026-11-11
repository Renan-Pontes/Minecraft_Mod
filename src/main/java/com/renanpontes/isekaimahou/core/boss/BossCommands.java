package com.renanpontes.isekaimahou.core.boss;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou boss telegraph [forma]} — demonstração das telegrafias perto do jogador (não causa dano);
 * {@code /mahou boss shake [intensidade]} — testa o tremor de câmera. Precisam de cheats.
 */
final class BossCommands {
    private static final List<String> SHAPES = List.of("all", "circle", "ring", "line", "cone", "follow", "safe", "blast", "line_blast");

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("boss")
                .then(Commands.literal("telegraph")
                        .executes(c -> demo(c, "all"))
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SHAPES, b))
                                .executes(c -> demo(c, StringArgumentType.getString(c, "shape")))))
                .then(Commands.literal("shake")
                        .executes(c -> shake(c, 1.5F))
                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.1F, 3.0F))
                                .executes(c -> shake(c, FloatArgumentType.getFloat(c, "intensity")))))));
    }

    private static int demo(CommandContext<CommandSourceStack> c, String shape) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!SHAPES.contains(shape)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.boss_kit.unknown", shape, String.join(", ", SHAPES)));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.boss_kit.demo", shapeName(shape)), false);
        if (!shape.equals("all")) {
            spawn(p, shape);
            return 1;
        }
        // Uma forma a cada 25 ticks, sempre ao redor da posição ATUAL do jogador
        int delay = 0;
        for (String s : SHAPES) {
            if (s.equals("all")) continue;
            BossKit.schedule(level, delay, () -> {
                if (!p.isRemoved() && p.level() == level) spawn(p, s);
            });
            delay += 25;
        }
        return 1;
    }

    private static void spawn(ServerPlayer p, String shape) {
        ServerLevel level = p.serverLevel();
        Vec3 feet = p.position();
        Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        switch (shape) {
            case "circle" -> Telegraph.circle(level, feet.add(fwd.scale(6)), 3F, 40, Telegraph.DANGER);
            case "ring" -> Telegraph.ring(level, feet, 3.5F, 6.5F, 50, Telegraph.WARNING);
            case "line" -> Telegraph.line(level, feet.add(right.scale(3)), feet.add(right.scale(3)).add(fwd.scale(14)), 2.5F, 40, Telegraph.VOID);
            case "cone" -> Telegraph.cone(level, feet.add(fwd.scale(8)), p.getYRot() + 180F, 70F, 7F, 30, Telegraph.HOLY);
            case "follow" -> {
                // O impacto é no ponto onde o círculo TRAVOU (centerOf vale dentro do callback), não onde o jogador está
                int[] id = {-1};
                id[0] = Telegraph.delayedFollow(level, p, 2.5F, 60, Telegraph.ARCANE, targets -> {
                    Vec3 at = Telegraph.centerOf(id[0]);
                    hit(p, "follow", at != null ? at : feet).accept(targets);
                });
            }
            case "safe" -> Telegraph.safe(level, feet.add(right.scale(-5)), 2.5F, 80);
            case "blast" -> {
                Vec3 at = feet.add(fwd.scale(5)).add(right.scale(-4));
                Telegraph.delayedBlast(level, at, 4F, 40, Telegraph.DANGER, hit(p, "blast", at));
            }
            case "line_blast" -> {
                Vec3 mid = feet.add(fwd.scale(3));
                Telegraph.delayedLine(level, mid.add(right.scale(-7)), mid.add(right.scale(7)), 3F, 50, Telegraph.FROST,
                        hit(p, "line_blast", mid));
            }
            default -> { }
        }
    }

    /** Callback de demonstração: só efeito visual/sonoro e a contagem de alvos (sem dano). */
    private static Consumer<List<LivingEntity>> hit(ServerPlayer viewer, String shape, Vec3 at) {
        return targets -> {
            ServerLevel level = viewer.serverLevel();
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 6, 1.2, 0.3, 1.2, 0);
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.3F);
            BossKit.shake(level, at, 18, 1.0F, 12);
            viewer.sendSystemMessage(Component.translatable("command.isekai_mahou.boss_kit.hit", shapeName(shape), targets.size()));
        };
    }

    private static int shake(CommandContext<CommandSourceStack> c, float intensity) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BossKit.shake(p.serverLevel(), p.position(), 24, intensity, 30);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.boss_kit.shake", String.format("%.1f", intensity)), false);
        return 1;
    }

    private static Component shapeName(String shape) {
        return Component.translatable("command.isekai_mahou.boss_kit.shape." + shape);
    }

    private BossCommands() {}
}
