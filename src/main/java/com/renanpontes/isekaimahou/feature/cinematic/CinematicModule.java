package com.renanpontes.isekaimahou.feature.cinematic;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "cinematic": anúncio do nome das magias, cântico e cutscenes. Quase tudo é de CLIENTE
 * ({@code client/CinematicClient}); o servidor só envia os pacotes do núcleo (Announce / Cutscenes).
 * Aqui ficam apenas os subcomandos de teste (precisam de cheats):
 * <pre>
 * /mahou cinematic cutscene &lt;tipo&gt; [foco]       toca uma cutscene para você (travando os controles)
 * /mahou cinematic announce &lt;estilo&gt; &lt;texto&gt;    anuncia como a entidade que executa (use "execute as" para mobs)
 * /mahou cinematic chant &lt;ticks&gt; &lt;texto&gt;        cântico palavra por palavra sobre a sua cabeça
 * /mahou cinematic test &lt;tipo&gt;                   v1.1: prévia das cenas coreografadas (transform:holy, ultimate:fire,
 *                                                 ultimate:fire:domain_expansion, domain, king_crimson, bites_the_dust...)
 * </pre>
 */
public class CinematicModule implements Module {
    static final List<String> KINDS = List.of("ultimate", "domain", "boss_intro", "boss_phase", "patron", "ascension", "portal", "story");
    static final List<String> STYLES = List.of("spell", "ultimate", "boss", "system");
    /** v1.1 — Tipos aceitos por /mahou cinematic test (coreografias). */
    static final List<String> TEST_KINDS = List.of("transform:default", "transform:holy", "transform:dark", "transform:time",
            "transform:speed", "transform:beast", "transform:spirit", "transform:fire", "transform:ice", "transform:storm",
            "transform:blood", "ultimate:fire", "ultimate:water", "ultimate:earth", "ultimate:wind", "ultimate:light",
            "ultimate:shadow", "ultimate:arcane", "ultimate:lightning", "ultimate:ice", "ultimate:nature", "ultimate:hellfire",
            "ultimate:fire:domain_expansion", "ultimate:arcane:meteor_shower", "ultimate", "domain", "king_crimson",
            "bites_the_dust");

    @Override
    public String id() {
        return "cinematic";
    }

    @Override
    public void init(IEventBus modBus) {
        CinematicConfig.register();
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("cinematic")
                .then(Commands.literal("cutscene")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(KINDS, b))
                                .executes(c -> cutscene(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("focus", EntityArgument.entity())
                                        .executes(c -> cutscene(c, EntityArgument.getEntity(c, "focus"))))))
                .then(Commands.literal("test")
                        .then(Commands.argument("kind", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TEST_KINDS, b))
                                .executes(CinematicModule::test)))
                .then(Commands.literal("announce")
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(STYLES, b))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(CinematicModule::announce))))
                .then(Commands.literal("chant")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(10, 400))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(CinematicModule::chant))))));
    }

    private static int cutscene(CommandContext<CommandSourceStack> c, Entity focus) throws CommandSyntaxException {
        ServerPlayer viewer = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT);
        Component subtitle = Component.translatableWithFallback("cinematic.isekai_mahou.kind." + kind, kind);
        int rgb = kind.startsWith("boss") ? 0xE04848 : 0xB98CFF;
        Cutscenes.play(viewer, kind, focus, Component.translatable("cinematic.isekai_mahou.test_title"), subtitle,
                rgb, 60, true);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cinematic.cutscene", kind), false);
        return 1;
    }

    /** v1.1 — Prévia de uma cena coreografada, focada em você (travando os controles, como no jogo). */
    private static int test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer viewer = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        int colon = kind.indexOf(':');
        String base = colon < 0 ? kind : kind.substring(0, colon);
        String style = colon < 0 ? "" : kind.substring(colon + 1);
        Component title = Component.translatable("cinematic.isekai_mahou.test_title");
        switch (base) {
            case "transform" -> {
                String st = style.isEmpty() ? "default" : style;
                Cutscenes.transform(viewer, Component.translatable("cinematic.isekai_mahou.test_transform"),
                        Component.translatableWithFallback("cinematic.isekai_mahou.style." + st, st), transformColor(st), st, 60);
            }
            case "ultimate" -> {
                String el = style.contains(":") ? style.substring(0, style.indexOf(':')) : style;
                Element element = el.isEmpty() ? null : Element.byId(el);
                if (element == null && !el.isEmpty()) {
                    c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cinematic.bad_kind", String.join(", ", TEST_KINDS)));
                    return 0;
                }
                if (element == null) {
                    Cutscenes.play(viewer, "ultimate", viewer, title, Component.translatable("cinematic.isekai_mahou.kind.ultimate"),
                            0xFF7A3D, 50, true);
                } else {
                    String form = style.contains(":") ? style.substring(style.indexOf(':') + 1) : null;
                    Cutscenes.play(viewer, Cutscenes.ultimateKind(element, form), viewer, title, element.displayName(),
                            element.color, 50, true);
                }
            }
            case "king_crimson" -> Cutscenes.play(viewer, "king_crimson", viewer, Component.literal("King Crimson"),
                    Component.translatable("cinematic.isekai_mahou.kind.king_crimson"), 0xC8102E, 40, true);
            case "bites_the_dust" -> Cutscenes.play(viewer, "bites_the_dust", viewer, Component.literal("Bites the Dust"),
                    Component.translatable("cinematic.isekai_mahou.kind.bites_the_dust"), 0xFF7FBF, 50, true);
            case "domain" -> Cutscenes.play(viewer, "domain", viewer, title,
                    Component.translatable("cinematic.isekai_mahou.kind.domain"), 0xB98CFF, 70, true);
            default -> {
                if (!KINDS.contains(base)) {
                    c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cinematic.bad_kind", String.join(", ", TEST_KINDS)));
                    return 0;
                }
                Cutscenes.play(viewer, kind, viewer, title, Component.translatableWithFallback("cinematic.isekai_mahou.kind." + base, base),
                        base.startsWith("boss") ? 0xE04848 : 0xB98CFF, 60, true);
            }
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cinematic.cutscene", kind), false);
        return 1;
    }

    /** Cor de teste de cada estilo de transformação (no jogo, quem chama escolhe). */
    private static int transformColor(String style) {
        return switch (style) {
            case "holy" -> 0xFFE9A0;
            case "dark" -> 0x8A2BE2;
            case "time" -> 0xC9B7FF;
            case "speed" -> 0xDDE6F0;
            case "beast" -> 0xD0661E;
            case "spirit" -> 0x6FD0FF;
            case "fire" -> 0xFF6A2B;
            case "ice" -> 0xA8E8FF;
            case "storm" -> 0x7FDBFF;
            case "blood" -> 0xC0182E;
            default -> 0xB98CFF;
        };
    }

    private static int announce(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof LivingEntity source)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cinematic.need_entity"));
            return 0;
        }
        String styleName = StringArgumentType.getString(c, "style").toLowerCase(Locale.ROOT);
        int style = STYLES.indexOf(styleName);
        if (style < 0) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cinematic.bad_style", String.join(", ", STYLES)));
            return 0;
        }
        int rgb = switch (style) {
            case Announce.STYLE_ULTIMATE -> 0xFF7A3D;
            case Announce.STYLE_BOSS -> 0xE04848;
            case Announce.STYLE_SYSTEM -> 0xFFD86B;
            default -> 0x7FD4FF;
        };
        Announce.send(source, Component.literal(StringArgumentType.getString(c, "text")), rgb, style);
        return 1;
    }

    private static int chant(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof LivingEntity source)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cinematic.need_entity"));
            return 0;
        }
        Announce.chant(source, StringArgumentType.getString(c, "text"), IntegerArgumentType.getInteger(c, "ticks"));
        return 1;
    }
}
