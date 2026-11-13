package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op):
 * <pre>
 *   /mahou glyphs grant transform_laws   — ensina as 13 Leis de Transformação
 *   /mahou tlaws grant                   — o mesmo
 *   /mahou tlaws test &lt;lei&gt; [segundos] — liga a lei por N s (padrão 60) sem precisar se transformar
 *   /mahou tlaws status                  — leis em vigor, sessões e escolhas da forma racial
 *   /mahou tlaws clear                   — encerra as sessões sem cobrar os preços
 * </pre>
 */
final class TlawCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(Commands.literal("glyphs").requires(s -> s.hasPermission(2))
                    .then(Commands.literal("grant").then(Commands.literal("transform_laws").executes(c -> grant(c.getSource())))));
            LiteralArgumentBuilder<CommandSourceStack> t = Commands.literal("tlaws").requires(s -> s.hasPermission(2));
            t.then(Commands.literal("grant").executes(c -> grant(c.getSource())));
            t.then(Commands.literal("status").executes(c -> status(c.getSource())));
            t.then(Commands.literal("clear").executes(c -> {
                TlawSessions.clear(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal("Leis de Transformação encerradas."), false);
                return 1;
            }));
            t.then(Commands.literal("test").then(Commands.argument("law", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TlawGlyphs.ORDER, b))
                    .executes(c -> test(c.getSource(), StringArgumentType.getString(c, "law"), 60))
                    .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 600))
                            .executes(c -> test(c.getSource(), StringArgumentType.getString(c, "law"),
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds"))))));
            root.then(t);
        });
    }

    private static int grant(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int n = 0;
        for (TransformLawGlyph l : TlawGlyphs.all()) {
            if (MagicApi.get(p).knowsGlyph(l.id())) continue;
            MagicApi.learnGlyphSilently(p, l.id());
            n++;
        }
        int learned = n;
        src.sendSuccess(() -> Component.literal("Leis de Transformação ensinadas: " + learned + " (total " + TlawGlyphs.all().size() + ")."), false);
        return Math.max(1, n);
    }

    private static int test(CommandSourceStack src, String path, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TransformLawGlyph law = TlawGlyphs.get(path);
        if (law == null) {
            src.sendFailure(Component.literal("Lei desconhecida: " + path));
            return 0;
        }
        TlawSessions.INSTANCE.begin(p, IsekaiMahou.id("test_" + path), List.of(law), seconds * 20, null);
        src.sendSuccess(() -> Component.literal("Lei " + path + " ligada por " + seconds + " s."), false);
        return 1;
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<TransformLawGlyph> act = TlawSessions.INSTANCE.active(p);
        src.sendSuccess(() -> Component.literal("Em vigor: ").append(act.isEmpty() ? Component.literal("nenhuma") : TlawFx.names(act)), false);
        long now = p.serverLevel().getGameTime();
        for (TlawSessions.Session s : TlawSessions.sessions(p)) {
            String end = s.endAt == Long.MAX_VALUE ? "enquanto durar o efeito" : ((s.endAt - now) / 20) + " s";
            src.sendSuccess(() -> Component.literal(" - " + s.source + (s.seen ? "" : " (aguardando)") + ": " + end + " → ")
                    .append(TlawFx.names(s.laws)), false);
        }
        List<TransformLawGlyph> picks = TlawRace.picks(MagicApi.get(p));
        src.sendSuccess(() -> Component.literal("Forma racial: ").append(picks.isEmpty() ? Component.literal("nenhuma") : TlawFx.names(picks)), false);
        List<TransformLawGlyph> hollow = TlawRace.picks(MagicApi.get(p), TlawRace.HOLLOW);
        if (!hollow.isEmpty()) src.sendSuccess(() -> Component.literal("Resurrección: ").append(TlawFx.names(hollow)), false);
        return 1;
    }

    private TlawCommands() {}
}
