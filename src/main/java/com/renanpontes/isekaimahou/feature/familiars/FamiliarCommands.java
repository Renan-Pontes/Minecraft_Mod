package com.renanpontes.isekaimahou.feature.familiars;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos de teste (op): {@code /mahou familiars info|scores|awaken [forma]|form <forma>|stage <1-3>|bond <n>|
 * drift|help|incense|reset} — sempre sobre quem digita.
 */
final class FamiliarCommands {
    private FamiliarCommands() {}

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("familiars")
                .then(Commands.literal("info").executes(FamiliarCommands::info))
                // v1.0 (onda 4) — receita de apoio: "spell" usa agora (sem recarga); "spell clear" esquece;
                // "spell teach <glifos>" ensina uma receita pelos ids (ex.: self heal), com as mesmas regras da aba
                .then(Commands.literal("spell").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarData d = FamiliarContent.data(sp);
                    FamiliarEntity body = FamiliarService.body(sp);
                    if (!d.awakened() || body == null || !FamiliarSpell.castNow(body, sp, d)) return fail(c);
                    return ok(c, d);
                }).then(Commands.literal("clear").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarSpell.teach(sp, null);
                    return 1;
                })).then(Commands.literal("teach").then(Commands.argument("glyphs", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    java.util.List<net.minecraft.resources.ResourceLocation> ids = new java.util.ArrayList<>();
                    for (String s : StringArgumentType.getString(c, "glyphs").trim().split(" ")) {
                        if (!s.isBlank()) ids.add(com.renanpontes.isekaimahou.IsekaiMahou.id(s.trim()));
                    }
                    FamiliarSpell.teach(sp, new com.renanpontes.isekaimahou.glyph.SpellRecipe(ids, "", -1, ""));
                    return 1;
                }))))
                .then(Commands.literal("scores").executes(FamiliarCommands::scores))
                // Desperta já (sem incenso nem fogueira); com forma = força a forma
                .then(Commands.literal("awaken").executes(c -> awaken(c, null))
                        .then(formArg().executes(c -> awaken(c, StringArgumentType.getString(c, "form")))))
                .then(Commands.literal("form").then(formArg().executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarForm f = form(c);
                    FamiliarData d = FamiliarContent.data(sp);
                    if (f == null || !d.awakened()) return fail(c);
                    FamiliarService.setForm(sp, d, f, true);
                    return ok(c, d);
                })))
                .then(Commands.literal("stage").then(Commands.argument("stage", IntegerArgumentType.integer(1, 3)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarData d = FamiliarContent.data(sp);
                    if (!d.awakened()) return fail(c);
                    FamiliarService.setStage(sp, d, IntegerArgumentType.getInteger(c, "stage"), true);
                    return ok(c, d);
                })))
                .then(Commands.literal("bond").then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarData d = FamiliarContent.data(sp);
                    if (!d.awakened()) return fail(c);
                    d.setBond(IntegerArgumentType.getInteger(c, "amount"));
                    FamiliarService.checkStage(sp, d);
                    return ok(c, d);
                })))
                // Reavalia o jeito de jogar e muda de forma na hora se outra combinar mais
                .then(Commands.literal("drift").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarData d = FamiliarContent.data(sp);
                    if (!d.awakened()) return fail(c);
                    FamiliarService.drift(sp, d, true);
                    return ok(c, d);
                }))
                // Dispara a ajuda ativa agora (faro de minério/tesouro, trilha da morte), sem recarga
                .then(Commands.literal("help").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarData d = FamiliarContent.data(sp);
                    FamiliarEntity b = FamiliarService.body(sp);
                    if (!d.awakened() || b == null) return fail(c);
                    FamiliarHelp.active(b, sp, d, true);
                    return ok(c, d);
                }))
                .then(Commands.literal("incense").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    sp.getInventory().placeItemBackInInventory(new ItemStack(FamiliarContent.BOND_INCENSE.get(), 4));
                    return 1;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    FamiliarService.reset(sp);
                    c.getSource().sendSuccess(() -> Component.translatable("commands.isekai_mahou.familiars.reset"), false);
                    return 1;
                }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> formArg() {
        return Commands.argument("form", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(FamiliarForm.ALL).map(f -> f.id), b));
    }

    private static FamiliarForm form(CommandContext<CommandSourceStack> c) {
        return FamiliarForm.byId(StringArgumentType.getString(c, "form"));
    }

    private static int awaken(CommandContext<CommandSourceStack> c, String formId) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        FamiliarForm f = formId == null ? null : FamiliarForm.byId(formId);
        if (formId != null && f == null || !FamiliarService.awaken(sp, f)) return fail(c);
        return ok(c, FamiliarContent.data(sp));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        FamiliarData d = FamiliarContent.data(sp);
        if (!d.awakened()) return fail(c);
        return ok(c, d);
    }

    private static int scores(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        FamiliarShaping.Result r = FamiliarShaping.evaluate(sp);
        for (FamiliarForm f : FamiliarForm.ALL) {
            String line = String.format(java.util.Locale.ROOT, "%s%-15s %.1f", f == r.best() ? "> " : "  ", f.id, r.score(f));
            c.getSource().sendSuccess(() -> Component.literal(line).withColor(f.rgb), false);
        }
        return 1;
    }

    private static int ok(CommandContext<CommandSourceStack> c, FamiliarData d) {
        String line = d.form().id + " stage=" + d.stage() + " bond=" + d.bond() + " summoned=" + d.summoned() + " help=" + d.help()
                + " candidate=" + (d.candidate().isEmpty() ? "-" : d.candidate() + "x" + d.candidateStreak()) + " shifts=" + d.shifts();
        c.getSource().sendSuccess(() -> Component.literal(line).withColor(d.form().rgb), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("commands.isekai_mahou.familiars.fail"));
        return 0;
    }
}
