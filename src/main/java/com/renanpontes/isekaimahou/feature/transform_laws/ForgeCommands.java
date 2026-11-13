package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op) da Transformação montada:
 * <pre>
 *   /mahou tforge grant                  — ensina a forma Transformação e as 13 leis
 *   /mahou tforge use &lt;espaço&gt; &lt;tecla&gt;   — põe no espaço a receita da tecla 1–4 (Z X C G) do conjunto ativo
 *   /mahou tforge remove &lt;espaço&gt;        — tira a receita do espaço
 *   /mahou tforge test &lt;espaço&gt; [seg]    — liga a receita do espaço por N s (padrão 30) sem a transformação base
 *                                          (cobra a mana da receita, como no jogo; criativo não paga)
 *   /mahou tforge status                 — sessões ativas e a receita de cada espaço
 *   /mahou tforge stop                   — encerra tudo (sem cobrar os preços das leis)
 * </pre>
 * Espaços: todos os de {@code TransformForge.slotIds()} — veja {@code /mahou tforge slots} (onda 5: toda transformação tem o seu).
 */
final class ForgeCommands {


    static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> t = MahouCommands.op("tforge");
            t.then(Commands.literal("grant").executes(c -> grant(c.getSource())));
            t.then(Commands.literal("status").executes(c -> status(c.getSource())));
            // onda 5: todas as transformações editáveis e um jeito de moldar sem abrir a tela
            t.then(Commands.literal("slots").executes(c -> slots(c.getSource())));
            t.then(Commands.literal("shape").then(Commands.argument("slot", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TransformForge.slotIds(), b))
                    .then(Commands.argument("glyphs", StringArgumentType.greedyString())
                            .executes(c -> shape(c.getSource(), StringArgumentType.getString(c, "slot"), StringArgumentType.getString(c, "glyphs"))))));
            t.then(Commands.literal("stop").executes(c -> {
                TlawSessions.clear(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal("Transformações encerradas."), false);
                return 1;
            }));
            t.then(Commands.literal("use").then(Commands.argument("slot", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TransformForge.slotIds(), b))
                    .then(Commands.argument("key", IntegerArgumentType.integer(1, 4))
                            .executes(c -> use(c.getSource(), StringArgumentType.getString(c, "slot"), IntegerArgumentType.getInteger(c, "key"))))));
            t.then(Commands.literal("remove").then(Commands.argument("slot", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TransformForge.slotIds(), b))
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ForgeData.set(p, StringArgumentType.getString(c, "slot"), SpellRecipe.EMPTY);
                        c.getSource().sendSuccess(() -> Component.literal("Receita removida do espaço."), false);
                        return 1;
                    })));
            t.then(Commands.literal("test").then(Commands.argument("slot", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TransformForge.slotIds(), b))
                    .executes(c -> test(c.getSource(), StringArgumentType.getString(c, "slot"), 30))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                            .executes(c -> test(c.getSource(), StringArgumentType.getString(c, "slot"), IntegerArgumentType.getInteger(c, "seconds"))))));
            root.then(t);
        });
    }

    private static int grant(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MagicData d = MagicApi.get(p);
        int n = 0;
        if (!d.knowsGlyph(TransformForge.FORM_ID)) {
            MagicApi.learnGlyphSilently(p, TransformForge.FORM_ID);
            n++;
        }
        for (TransformLawGlyph l : TlawGlyphs.all()) {
            if (d.knowsGlyph(l.id())) continue;
            MagicApi.learnGlyphSilently(p, l.id());
            n++;
        }
        int learned = n;
        src.sendSuccess(() -> Component.literal("Forma Transformação + leis ensinadas: " + learned + " glifo(s) novo(s). Círculo mínimo da forma: "
                + TransformForge.MIN_CIRCLE + "."), false);
        return Math.max(1, n);
    }

    /** Lista TODOS os espaços registrados: se o jogador tem a transformação e a receita guardada nela. */
    private static int slots(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MagicData d = MagicApi.get(p);
        List<String> ids = TransformForge.slotIds();
        src.sendSuccess(() -> Component.literal("Transformações editáveis: " + ids.size()), false);
        for (String id : ids) {
            TransformForge.Slot s = TransformForge.slot(id);
            if (s == null) continue;
            boolean has = s.available().test(d);
            SpellRecipe r = ForgeData.recipe(p, id);
            src.sendSuccess(() -> Component.literal(" - " + id + " (").append(s.title().get()).append(Component.literal(")"
                    + (has ? "" : " [o jogador não tem]") + ": " + (r.isEmpty() ? "sem receita" : (r.glyphs().size() - 1) + " peça(s)")
                    + (ForgeSessions.active(p, IsekaiMahou.id(id)) ? " · ATIVA" : ""))), false);
        }
        return ids.size();
    }

    /** Molda um espaço pelos ids dos glifos (sem o corpo), ex.: {@code /mahou tforge shape kaioken extend ignite}. */
    private static int shape(CommandSourceStack src, String slot, String glyphs) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (TransformForge.slot(slot) == null) {
            src.sendFailure(Component.literal("Espaço desconhecido: " + slot + " (veja /mahou tforge slots)"));
            return 0;
        }
        List<net.minecraft.resources.ResourceLocation> ids = new java.util.ArrayList<>();
        ids.add(TransformForge.FORM_ID);
        for (String part : glyphs.trim().split("\s+")) {
            net.minecraft.resources.ResourceLocation id = part.contains(":") ? net.minecraft.resources.ResourceLocation.tryParse(part) : IsekaiMahou.id(part);
            if (id == null || com.renanpontes.isekaimahou.glyph.GlyphRegistry.get(id) == null) {
                src.sendFailure(Component.literal("Glifo desconhecido: " + part));
                return 0;
            }
            ids.add(id);
        }
        SpellRecipe r = SpellRecipe.EMPTY.withGlyphs(List.copyOf(ids));
        // mesma validação da tela: só o que o jogador conhece, limite de glifos das magias (criativo não checa)
        SpellParser.Result res = TransformForge.validate(r, p.getAbilities().instabuild ? null : MagicApi.get(p));
        if (!res.ok()) {
            src.sendFailure(Component.literal("Receita recusada: ").append(res.error()));
            return 0;
        }
        ForgeData.set(p, slot, r);
        src.sendSuccess(() -> Component.literal("Espaço " + slot + " moldado: " + (ids.size() - 1) + " peça(s), "
                + TransformForge.slotMana(res.spell()) + " de mana, duração ×" + TransformForge.durationMult(res.spell().formStats())
                + ", recarga ×" + TransformForge.cooldownMult(res.spell().formStats())), false);
        for (Component line : TransformForge.summary(res.spell(), false)) src.sendSuccess(() -> line, false);
        return 1;
    }

    private static int use(CommandSourceStack src, String slot, int key) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (TransformForge.slot(slot) == null) {
            src.sendFailure(Component.literal("Espaço desconhecido: " + slot));
            return 0;
        }
        MagicData d = MagicApi.get(p);
        SpellRecipe r = d.peekSlot(d.activeLoadout(), key - 1).main();
        if (r == null || r.isEmpty()) {
            src.sendFailure(Component.literal("A tecla " + key + " do conjunto ativo está vazia."));
            return 0;
        }
        SpellParser.Result res = TransformForge.validate(r, d);
        if (!res.ok() || !TransformForge.isForge(res.spell())) {
            src.sendFailure(Component.literal("A receita da tecla " + key + " não é uma Transformação válida: ")
                    .append(res.ok() ? Component.translatable("message.isekai_mahou.tforge.not_forge") : res.error()));
            return 0;
        }
        // Comando de teste: grava direto (não exige que o espaço já esteja visível para o jogador)
        ForgeData.set(p, slot, r);
        src.sendSuccess(() -> Component.literal("Espaço " + slot + " ← ").append(ForgeSessions.recipeName(r)), false);
        return 1;
    }

    private static int test(CommandSourceStack src, String slot, int seconds) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (TransformForge.slot(slot) == null) {
            src.sendFailure(Component.literal("Espaço desconhecido: " + slot));
            return 0;
        }
        TransformForge.Result r = TransformForge.beginSlot(p, slot, seconds * 20, null, List.of());
        if (!r.ok()) {
            src.sendFailure(Component.literal("A receita não entrou: ").append(r.error() != null ? r.error()
                    : Component.literal("o espaço " + slot + " está sem receita (use /mahou tforge use " + slot + " <tecla>).")));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Receita do espaço " + slot + " ligada por " + seconds + " s."), false);
        return 1;
    }

    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        long now = p.serverLevel().getGameTime();
        List<ForgeSessions.Session> act = ForgeSessions.sessions(p);
        src.sendSuccess(() -> Component.literal("Transformações por receita ativas: " + act.size()), false);
        for (ForgeSessions.Session s : act) {
            String end = s.probe != null ? "enquanto durar o efeito" : ((s.endAt - now) / 20) + " s";
            src.sendSuccess(() -> Component.literal(" - " + s.source + (s.seen ? "" : " (aguardando)") + ": " + end + " · golpes "
                    + s.strike.size() + " · aura " + s.aura.size() + " · alcance " + s.radius + " · ")
                    .append(s.element.displayName()), false);
        }
        List<String> ids = new ArrayList<>(TransformForge.slotIds());
        for (TransformForge.Slot s : TransformForge.slotsFor(MagicApi.get(p))) if (!ids.contains(s.id())) ids.add(s.id());
        for (String id : ids) {
            SpellRecipe r = ForgeData.recipe(p, id);
            if (r.isEmpty()) continue;
            src.sendSuccess(() -> Component.literal("Espaço " + id + ": ").append(ForgeSessions.recipeName(r))
                    .append(Component.literal(" (" + r.glyphs().size() + " glifos)")), false);
        }
        src.sendSuccess(() -> Component.literal("Fonte da forma conjurada: " + IsekaiMahou.id(TransformForge.FORM)), false);
        return 1;
    }

    private ForgeCommands() {}
}
