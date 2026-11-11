package com.renanpontes.isekaimahou.core.status;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos do núcleo A1 (todos precisam de cheats):
 * <pre>
 * /mahou destruction [status|on|off]                         regra mahouSpellDestruction
 * /mahou destruction test sphere|crater|cylinder|line|surface &lt;raio&gt; [modo]   no bloco que você está olhando
 * /mahou place list | tp &lt;id&gt; | remove &lt;id&gt;
 * /mahou status give &lt;alvos&gt; &lt;condição&gt; [acúmulos] [segundos] | clear &lt;alvos&gt; [condição]
 * </pre>
 */
final class StatusCommands {
    private static final List<String> SHAPES = List.of("sphere", "crater", "cylinder", "line", "surface");

    static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("destruction")
                    .executes(StatusCommands::destructionStatus)
                    .then(Commands.literal("status").executes(StatusCommands::destructionStatus))
                    .then(Commands.literal("on").executes(c -> setDestruction(c, true)))
                    .then(Commands.literal("off").executes(c -> setDestruction(c, false)))
                    .then(Commands.literal("test")
                            .then(Commands.argument("shape", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(SHAPES, b))
                                    .then(Commands.argument("radius", FloatArgumentType.floatArg(1F, 48F))
                                            .executes(c -> test(c, null))
                                            .then(Commands.argument("mode", StringArgumentType.word())
                                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(modeIds(), b))
                                                    .executes(c -> test(c, StringArgumentType.getString(c, "mode"))))))));

            root.then(MahouCommands.op("place")
                    .then(Commands.literal("list").executes(StatusCommands::placeList))
                    .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.string())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(placeIds(c.getSource()), b))
                            .executes(StatusCommands::placeTp)))
                    .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.string())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(placeIds(c.getSource()), b))
                            .executes(StatusCommands::placeRemove))));

            root.then(MahouCommands.op("status")
                    .then(Commands.literal("give").then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("kind", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(kindIds(), b))
                                    .executes(c -> give(c, 1, 0))
                                    .then(Commands.argument("stacks", IntegerArgumentType.integer(1, 10))
                                            .executes(c -> give(c, IntegerArgumentType.getInteger(c, "stacks"), 0))
                                            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                    .executes(c -> give(c, IntegerArgumentType.getInteger(c, "stacks"),
                                                            IntegerArgumentType.getInteger(c, "seconds"))))))))
                    .then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.entities())
                            .executes(c -> clearStatus(c, null))
                            .then(Commands.argument("kind", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(kindIds(), b))
                                    .executes(c -> clearStatus(c, StringArgumentType.getString(c, "kind")))))));
        });
    }

    // =====================================================================================
    //  /mahou destruction
    // =====================================================================================
    private static int destructionStatus(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        boolean rule = DestructionRules.ruleOn(level);
        boolean config = com.renanpontes.isekaimahou.core.MahouConfig.SPELL_BLOCK_DESTRUCTION.get();
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.destruction.status",
                onOff(rule), onOff(config), onOff(griefing), Destruction.queued()), false);
        return rule && config ? 1 : 0;
    }

    private static int setDestruction(CommandContext<CommandSourceStack> c, boolean on) {
        GameRules.Key<GameRules.BooleanValue> key = DestructionRules.rule();
        if (key == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.destruction.no_rule"));
            return 0;
        }
        c.getSource().getServer().getGameRules().getRule(key).set(on, c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.isekai_mahou.destruction.on"
                : "command.isekai_mahou.destruction.off").withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> c, String modeId) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String shape = StringArgumentType.getString(c, "shape");
        float radius = FloatArgumentType.getFloat(c, "radius");
        Destruction.Mode mode = Destruction.Mode.ERASE;
        if (modeId != null) {
            mode = modeOf(modeId);
            if (mode == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.destruction.bad_mode", modeId,
                        String.join(", ", modeIds())));
                return 0;
            }
        }
        if (!SHAPES.contains(shape)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.destruction.bad_shape", shape, String.join(", ", SHAPES)));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(96));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        int n = switch (shape) {
            case "crater" -> Destruction.crater(level, at, radius, p);
            case "cylinder" -> Destruction.cylinder(level, at.subtract(0, radius, 0), radius * 0.5F, radius * 3F, p, mode);
            case "line" -> Destruction.line(level, eye.add(p.getLookAngle().scale(2)), at, Math.max(1F, radius / 4F), Math.max(2F, radius / 3F), p, mode);
            case "surface" -> Destruction.surface(level, at, radius, p, mode);
            default -> Destruction.sphere(level, at, radius, p, mode);
        };
        if (n == 0) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.destruction.denied"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.destruction.queued", n), false);
        return n;
    }

    // =====================================================================================
    //  /mahou place
    // =====================================================================================
    private static int placeList(CommandContext<CommandSourceStack> c) {
        List<Places.Place> all = PlaceData.everywhere(c.getSource().getLevel());
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.place.none"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.place.header", all.size()).withStyle(ChatFormatting.GOLD), false);
        for (Places.Place p : all) {
            BlockPos ce = p.center();
            Component line = Component.translatable("command.isekai_mahou.place.line", p.name().copy().withColor(p.color()), p.id(),
                    p.type(), p.dim().location().toString(), ce.getX(), ce.getY(), ce.getZ(),
                    Component.translatable(p.safeZone() ? "command.isekai_mahou.place.safe" : "command.isekai_mahou.place.unsafe"))
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mahou place tp " + quote(p.id())))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("command.isekai_mahou.place.click_tp"))));
            c.getSource().sendSuccess(() -> line, false);
        }
        return all.size();
    }

    private static int placeTp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "id");
        Places.Place place = Places.get(p.serverLevel(), id);
        if (place == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.place.unknown", id));
            return 0;
        }
        ServerLevel level = c.getSource().getServer().getLevel(place.dim());
        if (level == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.place.unknown", id));
            return 0;
        }
        BlockPos ce = place.center();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ce.getX(), ce.getZ());
        if (y <= level.getMinBuildHeight()) y = ce.getY();
        p.teleportTo(level, ce.getX() + 0.5, y, ce.getZ() + 0.5, p.getYRot(), p.getXRot());
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.place.tp", place.name()), false);
        return 1;
    }

    private static int placeRemove(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        Places.Place place = Places.get(c.getSource().getLevel(), id);
        if (place == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.place.unknown", id));
            return 0;
        }
        Places.remove(c.getSource().getLevel(), id);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.place.removed", place.name(), id), true);
        return 1;
    }

    private static List<String> placeIds(CommandSourceStack s) {
        return PlaceData.everywhere(s.getLevel()).stream().map(p -> quote(p.id())).toList();
    }

    private static String quote(String id) {
        return id.matches("[a-zA-Z0-9_.+-]+") ? id : '"' + id + '"';
    }

    // =====================================================================================
    //  /mahou status
    // =====================================================================================
    private static int give(CommandContext<CommandSourceStack> c, int stacks, int seconds) throws CommandSyntaxException {
        Statuses.Kind kind = kindOf(StringArgumentType.getString(c, "kind"));
        if (kind == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.status.bad_kind",
                    StringArgumentType.getString(c, "kind"), String.join(", ", kindIds())));
            return 0;
        }
        Collection<? extends Entity> targets = EntityArgument.getEntities(c, "targets");
        LivingEntity source = c.getSource().getEntity() instanceof LivingEntity le ? le : null;
        int n = 0;
        for (Entity e : targets) {
            if (!(e instanceof LivingEntity le)) continue;
            Statuses.apply(le, kind, stacks, seconds > 0 ? seconds * 20 : Statuses.DEFAULT_TICKS, source, 1.0F);
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.status.given", kind.displayName(), stacks, count), true);
        return n;
    }

    private static int clearStatus(CommandContext<CommandSourceStack> c, String kindId) throws CommandSyntaxException {
        Statuses.Kind only = null;
        if (kindId != null) {
            only = kindOf(kindId);
            if (only == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.status.bad_kind", kindId, String.join(", ", kindIds())));
                return 0;
            }
        }
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) {
            if (!(e instanceof LivingEntity le)) continue;
            for (Statuses.Kind k : Statuses.Kind.values()) if (only == null || only == k) Statuses.clear(le, k);
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.status.cleared", count), true);
        return n;
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================
    private static Component onOff(boolean on) {
        return Component.translatable(on ? "command.isekai_mahou.status_core.on" : "command.isekai_mahou.status_core.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static List<String> modeIds() {
        return Arrays.stream(Destruction.Mode.values()).map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static Destruction.Mode modeOf(String id) {
        for (Destruction.Mode m : Destruction.Mode.values()) if (m.name().equalsIgnoreCase(id)) return m;
        return null;
    }

    private static List<String> kindIds() {
        return Arrays.stream(Statuses.Kind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static Statuses.Kind kindOf(String id) {
        for (Statuses.Kind k : Statuses.Kind.values()) {
            if (k.name().equalsIgnoreCase(id) || k.id().equalsIgnoreCase(id)) return k;
        }
        return null;
    }

    private StatusCommands() {}
}
