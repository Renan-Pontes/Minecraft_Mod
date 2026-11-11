package com.renanpontes.isekaimahou.feature.cinematic;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * </pre>
 */
public class CinematicModule implements Module {
    static final List<String> KINDS = List.of("ultimate", "domain", "boss_intro", "boss_phase", "patron", "ascension", "portal");
    static final List<String> STYLES = List.of("spell", "ultimate", "boss", "system");

    @Override
    public String id() {
        return "cinematic";
    }

    @Override
    public void init(IEventBus modBus) {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("cinematic")
                .then(Commands.literal("cutscene")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(KINDS, b))
                                .executes(c -> cutscene(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("focus", EntityArgument.entity())
                                        .executes(c -> cutscene(c, EntityArgument.getEntity(c, "focus"))))))
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
