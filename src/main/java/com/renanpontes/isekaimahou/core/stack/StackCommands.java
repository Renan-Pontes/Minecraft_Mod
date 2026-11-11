package com.renanpontes.isekaimahou.core.stack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou stack list} — pendentes por perto; {@code /mahou stack test [minor|major|ultimate] [segundos]} — o mob
 * mais próximo telegrafa um golpe inofensivo em você (entra na Pilha); {@code /mahou stack counter [poder]} — contraria
 * tudo que é hostil por perto. Precisam de cheats.
 */
final class StackCommands {
    private static final List<String> TIERS = Arrays.stream(SpellStack.Tier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList();

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("stack")
                .then(Commands.literal("list").executes(StackCommands::list))
                .then(Commands.literal("test")
                        .executes(c -> test(c, null, 3))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TIERS, b))
                                .executes(c -> test(c, StringArgumentType.getString(c, "tier"), 3))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 30))
                                        .executes(c -> test(c, StringArgumentType.getString(c, "tier"), IntegerArgumentType.getInteger(c, "seconds"))))))
                .then(Commands.literal("counter")
                        .executes(c -> counter(c, 10F))
                        .then(Commands.argument("power", FloatArgumentType.floatArg(0F, 100F))
                                .executes(c -> counter(c, FloatArgumentType.getFloat(c, "power")))))));
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        List<SpellStack.Pending> near = SpellStack.near(level, p.position(), 64);
        if (near.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.stack.list.empty"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.stack.list.header", near.size())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (SpellStack.Pending s : near) {
            LivingEntity caster = s.caster();
            Component who = caster != null ? caster.getDisplayName() : Component.literal("?");
            String secs = String.format(Locale.ROOT, "%.1f", s.ticksLeft(level) / 20F);
            Component line = Component.translatable("command.isekai_mahou.stack.list.entry", s.id(), who, s.name(),
                    s.tier().displayName(), secs);
            c.getSource().sendSuccess(() -> s.counterable() ? line : line.copy().withStyle(ChatFormatting.GRAY), false);
        }
        return near.size();
    }

    private static int test(CommandContext<CommandSourceStack> c, String tierName, int seconds) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        SpellStack.Tier tier = null;
        if (tierName != null) {
            if (!TIERS.contains(tierName)) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.stack.test.bad_tier", tierName, String.join(", ", TIERS)));
                return 0;
            }
            tier = SpellStack.Tier.valueOf(tierName.toUpperCase(Locale.ROOT));
        }
        LivingEntity caster = nearestMob(level, p, 24);
        if (caster == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.stack.test.no_caster"));
            return 0;
        }
        int ticks = seconds * 20;
        Vec3 spot = p.position();
        Telegraph.Shape shape = Telegraph.Shape.circle(spot, 3.0F).warmup(ticks).color(Telegraph.ARCANE).owner(caster)
                .castName(Component.translatable("stack.isekai_mahou.test_name"));
        if (tier != null) shape.tier(tier);
        // Inofensivo: só mostra onde "acertaria"
        Telegraph.delayed(level, shape, hits -> {
            level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 3, 1.2, 0.2, 1.2, 0);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
        });
        SpellStack.Tier shown = tier != null ? tier : SpellStack.Tier.byWarmup(ticks);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.stack.test.ok", caster.getDisplayName(),
                shown.displayName(), seconds), false);
        return 1;
    }

    private static int counter(CommandContext<CommandSourceStack> c, float power) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = SpellStack.counterNear(p.serverLevel(), p.position(), 32, p, power);
        if (n == 0) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.stack.counter.none"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.stack.counter.ok", n), false);
        return n;
    }

    private static LivingEntity nearestMob(ServerLevel level, ServerPlayer p, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                e -> e.isAlive() && !(e instanceof Player))) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private StackCommands() {}
}
