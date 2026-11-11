package com.renanpontes.isekaimahou.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comando /mahou. Módulos adicionam subcomandos com {@link #extend} no init deles, ex.:
 * <pre>MahouCommands.extend((root, ctx) -> root.then(Commands.literal("guild")...));</pre>
 *
 * Subcomandos do núcleo (precisam de cheats, menos "info"):
 *   info | level <n> | circle <n> | xp <n> | fill | charge | path <classe> | kit <classe> | reset
 *   element awaken <el|all> | element xp <el> <n> | glyph learn <id|all> | unlock all
 *   legend list (qualquer jogador) | legend give <id> [jogadores]
 */
public final class MahouCommands {
    private static final List<BiConsumer<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext>> EXTENSIONS = new ArrayList<>();

    public static synchronized void extend(BiConsumer<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> ext) {
        EXTENSIONS.add(ext);
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mahou");

        root.then(Commands.literal("info").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            MagicData d = MagicApi.get(p);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.info",
                    d.path().displayName(), d.level(), d.xp(), Progression.xpToNext(d.level()), d.circle(),
                    (int) d.mana(), d.maxMana(), d.glyphs().size()), false);
            if (d.xpBank() > 0) {
                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.info_bank", d.xpBank(), d.bankCap())
                        .withStyle(ChatFormatting.AQUA), false);
            }
            return 1;
        }));

        root.then(op("level").then(Commands.argument("n", IntegerArgumentType.integer(1, Progression.HIDDEN_MAX_LEVEL)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            MagicApi.setLevel(p, IntegerArgumentType.getInteger(c, "n"));
            return 1;
        })));
        root.then(op("circle").then(Commands.argument("n", IntegerArgumentType.integer(1, Progression.MAX_CIRCLE)).executes(c -> {
            MagicApi.setCircle(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"));
            return 1;
        })));
        root.then(op("xp").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
            MagicApi.giveXp(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"), "command");
            return 1;
        })));
        root.then(op("fill").executes(c -> {
            MagicApi.get(c.getSource().getPlayerOrException()).fillMana();
            return 1;
        }));
        root.then(op("charge").executes(c -> {
            MagicData d = MagicApi.get(c.getSource().getPlayerOrException());
            for (Element e : Element.values()) d.setCharge(e, Progression.MAX_CHARGE);
            return 1;
        }));
        root.then(op("path").then(Commands.argument("path", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MagicPath.values()).map(p -> p.id), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicPath path = MagicPath.byId(StringArgumentType.getString(c, "path"));
                    MagicApi.get(p).setPath(path);
                    if (path == MagicPath.NONE) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                                com.renanpontes.isekaimahou.network.CorePayloads.OpenPathScreen.INSTANCE);
                    }
                    return 1;
                })));
        root.then(op("kit").then(Commands.argument("path", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MagicPath.PLAYABLE).map(p -> p.id), b))
                .executes(c -> {
                    ClassKits.give(c.getSource().getPlayerOrException(), MagicPath.byId(StringArgumentType.getString(c, "path")));
                    return 1;
                })));
        root.then(op("reset").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.setData(CoreRegistries.MAGIC, new MagicData());
            CoreGameplay.updateGrowthHealth(p);
            CoreGameplay.syncAll(p);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    com.renanpontes.isekaimahou.network.CorePayloads.OpenPathScreen.INSTANCE);
            return 1;
        }));

        root.then(op("element")
                .then(Commands.literal("awaken").then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(elementIdsPlusAll(), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "element");
                            for (Element e : Element.values()) {
                                if (e != Element.ARCANE && (id.equals("all") || e.id.equals(id))) MagicApi.awakenElement(p, e);
                            }
                            return 1;
                        })))
                .then(Commands.literal("xp").then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Element.values()).map(e -> e.id), b))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                            Element e = Element.byId(StringArgumentType.getString(c, "element"));
                            if (e == null) return 0;
                            MagicApi.giveElementXp(c.getSource().getPlayerOrException(), e, IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))));

        root.then(op("glyph").then(Commands.literal("learn")
                .then(Commands.literal("all").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (Glyph g : GlyphRegistry.all()) MagicApi.learnGlyphSilently(p, g.id());
                    return 1;
                }))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(GlyphRegistry.all().stream().map(Glyph::id), b))
                        .executes(c -> {
                            ResourceLocation id = ResourceLocationArgument.getId(c, "id");
                            return MagicApi.learnGlyph(c.getSource().getPlayerOrException(), id) ? 1 : 0;
                        }))));

        root.then(op("unlock").then(Commands.literal("all").executes(MahouCommands::unlockAll)));

        root.then(Commands.literal("legend")
                .then(Commands.literal("list").executes(MahouCommands::legendList))
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Legends.all().stream().map(Legends.Legend::id), b))
                                .executes(c -> legendGive(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> legendGive(c, EntityArgument.getPlayers(c, "targets"))))))
                // Teste rápido (op): conjura a lenda na hora, como se fosse você, sem gastar mana nem slot
                .then(Commands.literal("cast").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Legends.all().stream().map(Legends.Legend::id), b))
                                .executes(MahouCommands::legendCast))));

        for (var ext : EXTENSIONS) ext.accept(root, ctx);
        dispatcher.register(root);
    }

    private static int legendCast(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "id");
        Legends.Legend legend = Legends.get(id);
        if (legend == null) {
            c.getSource().sendFailure(Component.literal("? " + id));
            return 0;
        }
        var ctx = com.renanpontes.isekaimahou.glyph.SpellCaster.castFrom(p, legend.element(), legend.recipe(), 1.0F,
                null, null, true);
        if (ctx == null) {
            c.getSource().sendFailure(Component.literal("✗ " + id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("✦ " + legend.recipe().name()), false);
        return 1;
    }

    /** Libera tudo para testar no criativo: Círculo 10, nível 100, todos os elementos e glifos. */
    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MagicApi.setCircle(p, Progression.MAX_CIRCLE);
        MagicApi.setLevel(p, Progression.MAX_LEVEL);
        for (Element e : Element.values()) if (e != Element.ARCANE) MagicApi.awakenElement(p, e);
        for (Glyph g : GlyphRegistry.all()) MagicApi.learnGlyphSilently(p, g.id());
        MagicData d = MagicApi.get(p);
        for (Element e : Element.values()) d.setCharge(e, Progression.MAX_CHARGE);
        d.fillMana();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.unlocked"), false);
        return 1;
    }

    /** Lista as Magias Lendárias registradas; para quem pode, cada linha é clicável e sugere o "give". */
    private static int legendList(CommandContext<CommandSourceStack> c) {
        List<Legends.Legend> all = Legends.all();
        CommandSourceStack src = c.getSource();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.legend.empty").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.legend.header", all.size()).withStyle(ChatFormatting.GOLD), false);
        boolean canGive = src.hasPermission(2);
        for (Legends.Legend l : all) {
            Component line = Component.translatable("command.isekai_mahou.legend.entry",
                    l.title().withColor(l.color()), Component.literal(l.id()).withStyle(ChatFormatting.DARK_GRAY),
                    l.element().displayName(), l.minCircle()).withStyle(s -> {
                        if (!canGive) return s.withColor(ChatFormatting.GRAY);
                        return s.withColor(ChatFormatting.GRAY)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mahou legend give " + l.id()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("command.isekai_mahou.legend.click")));
                    });
            src.sendSuccess(() -> line, false);
        }
        return all.size();
    }

    private static int legendGive(CommandContext<CommandSourceStack> c, java.util.Collection<ServerPlayer> targets) {
        String id = StringArgumentType.getString(c, "id");
        Legends.Legend legend = Legends.get(id);
        if (legend == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.legend.unknown", id));
            return 0;
        }
        for (ServerPlayer p : targets) {
            com.renanpontes.isekaimahou.core.api.Rewards.give(p, Legends.scroll(legend));
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.legend.given",
                    p.getDisplayName(), legend.title().withColor(legend.color())), true);
        }
        return targets.size();
    }

    private static List<String> elementIdsPlusAll() {
        List<String> l = new ArrayList<>(Arrays.stream(Element.values()).filter(e -> e != Element.ARCANE).map(e -> e.id).toList());
        l.add("all");
        return l;
    }

    /** Subcomando que precisa de cheats (nível de permissão 2). */
    public static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
    }

    private MahouCommands() {}
}
