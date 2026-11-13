package com.renanpontes.isekaimahou.feature.forms_b.summon;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos da Forja de Invocações.
 * <ul>
 *   <li>Qualquer jogador: {@code /mahou summons <follow|guard|hunt|stay>} — o "comando curto" para dar ordem a todas
 *       as suas invocações (o mesmo que a tecla de ordem).</li>
 *   <li>Op (testes): {@code /mahou summons info} e {@code /mahou summons test <corpo> [glifos...]} — conjura
 *       "Invocação + corpo + glifos" (na ordem escrita) sem gastar mana nem checar conhecimento. {@code <corpo>} = id
 *       do glifo do corpo (ex.: summon_golem) ou {@code spirit} para o espírito.</li>
 * </ul>
 */
public final class SummonCommands {
    private SummonCommands() {}

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> summons = Commands.literal("summons");
            for (SummonOrder order : SummonOrder.values()) {
                summons.then(Commands.literal(order.id).executes(c -> SummonForge.command(c.getSource().getPlayerOrException(), order)));
            }
            summons.then(MahouCommands.op("info").executes(SummonCommands::info));
            summons.then(MahouCommands.op("test").then(Commands.argument("body", StringArgumentType.word())
                    .suggests((c, b) -> {
                        List<String> ids = new ArrayList<>();
                        ids.add("spirit");
                        for (SummonBodies.Body body : SummonBodies.all()) ids.add(body.glyph().getPath());
                        return SharedSuggestionProvider.suggest(ids, b);
                    })
                    .executes(c -> test(c, ""))
                    .then(Commands.argument("glyphs", StringArgumentType.greedyString())
                            .executes(c -> test(c, StringArgumentType.getString(c, "glyphs"))))));
            root.then(summons);
        });
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<Component> lines = SummonForge.describe(sp);
        if (lines.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.forms_b.order_none"), false);
            return 0;
        }
        for (Component line : lines) c.getSource().sendSuccess(() -> line, false);
        return lines.size();
    }

    /** Conjura Invocação + corpo + os glifos pedidos (separados por espaço), como o jogador faria, mas de graça. */
    private static int test(CommandContext<CommandSourceStack> c, String extra) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String body = StringArgumentType.getString(c, "body");
        List<String> names = new ArrayList<>();
        names.add("conjure");
        if (!body.equals("spirit")) names.add(body);
        for (String s : extra.trim().split(" ")) if (!s.isBlank()) names.add(s.trim());
        List<ResourceLocation> glyphs = new ArrayList<>();
        for (String n : names) {
            ResourceLocation id = n.contains(":") ? ResourceLocation.tryParse(n) : IsekaiMahou.id(n);
            Glyph g = id == null ? null : GlyphRegistry.get(id);
            if (g == null) {
                c.getSource().sendFailure(Component.literal("Glifo desconhecido: " + n));
                return 0;
            }
            glyphs.add(id);
        }
        SpellRecipe recipe = new SpellRecipe(glyphs, "Teste da Forja", -1, "");
        Element element = SpellCost.primaryElement(recipe);
        SpellContext ctx = SpellCaster.castFrom(sp, element, recipe, 1.0F, null, null, false);
        if (ctx == null) {
            c.getSource().sendFailure(Component.literal("Receita invalida: " + glyphs));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Forja: " + glyphs), false);
        return 1;
    }
}
