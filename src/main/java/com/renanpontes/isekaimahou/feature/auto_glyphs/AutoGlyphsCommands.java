package com.renanpontes.isekaimahou.feature.auto_glyphs;

import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.automation.PedestalCondition;
import com.renanpontes.isekaimahou.glyph.Glyph;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op):
 * <pre>
 *   /mahou glyphs grant auto_glyphs [jogador]   — ensina os 11 glifos do módulo
 *   /mahou auto_glyphs conditions               — confere cada gatilho de pedestal em volta de você (raio 4)
 *   /mahou auto_glyphs quarry list [raio]       — pedreiras perto de você (camada, fundo, blocos)
 *   /mahou auto_glyphs quarry reset [raio]      — esquece as pedreiras perto (recomeçam do topo)
 * </pre>
 */
final class AutoGlyphsCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(Commands.literal("glyphs")
                    .then(MahouCommands.op("grant").then(Commands.literal("auto_glyphs")
                            .executes(c -> grant(c.getSource().getPlayerOrException()))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(c -> grant(EntityArgument.getPlayer(c, "player")))))));
            root.then(MahouCommands.op("auto_glyphs")
                    .then(Commands.literal("conditions").executes(c -> conditions(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("quarry")
                            .then(Commands.literal("list")
                                    .executes(c -> quarryList(c.getSource().getPlayerOrException(), 64))
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100000))
                                            .executes(c -> quarryList(c.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(c, "radius")))))
                            .then(Commands.literal("reset")
                                    .executes(c -> quarryReset(c.getSource().getPlayerOrException(), 16))
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100000))
                                            .executes(c -> quarryReset(c.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(c, "radius")))))));
        });
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (String path : AutoGlyphsModule.GLYPHS) {
            Glyph g = AutoGlyphsModule.glyph(path);
            if (g == null) continue;
            MagicApi.learnGlyphSilently(p, g.id());
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.auto_glyphs.granted", n, list).withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }

    /** Mostra o resultado de cada gatilho como se a magia (raio 4) fosse conjurada aqui. */
    private static int conditions(ServerPlayer p) {
        for (String path : AutoGlyphsModule.GLYPHS) {
            Glyph g = AutoGlyphsModule.glyph(path);
            if (!(g instanceof PedestalCondition cond)) continue;
            boolean ok;
            try {
                ok = cond.ready(p.serverLevel(), p.blockPosition(), p.position(), probeSpell());
            } catch (RuntimeException e) {
                ok = false;
            }
            boolean result = ok;
            p.sendSystemMessage(Component.literal(result ? " ✔ " : " ✘ ").withStyle(result ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(g.displayName().withStyle(ChatFormatting.WHITE)));
        }
        return 1;
    }

    /** Uma receita mínima (Toque + Colheita Fértil) só para dar raio/efeitos à conferência. */
    private static com.renanpontes.isekaimahou.glyph.ParsedSpell probeSpell() {
        var recipe = com.renanpontes.isekaimahou.glyph.SpellRecipe.EMPTY.withGlyphs(java.util.List.of(
                com.renanpontes.isekaimahou.IsekaiMahou.id("touch"), com.renanpontes.isekaimahou.IsekaiMahou.id("harvest_replant")));
        var r = com.renanpontes.isekaimahou.glyph.SpellParser.parse(recipe, (com.renanpontes.isekaimahou.core.MagicData) null);
        if (!r.ok()) throw new IllegalStateException("receita de teste inválida");
        return r.spell();
    }

    private static int quarryList(ServerPlayer p, int radius) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        int n = 0;
        for (Map.Entry<String, QuarryData.Quarry> e : QuarryData.get(level).all().entrySet()) {
            QuarryData.Quarry q = e.getValue();
            if (Math.abs(q.cx - at.getX()) > radius || Math.abs(q.cz - at.getZ()) > radius) continue;
            n++;
            p.sendSystemMessage(Component.literal(String.format(" %s @ %d,%d r%d: camada %d (topo %d, fundo %d)%s — %d blocos",
                    e.getKey().startsWith("p:") ? "Pedestal" : "Mão", q.cx, q.cz, q.r, q.layer, q.top, q.bottom,
                    q.done ? " PRONTA" : "", q.mined)).withStyle(ChatFormatting.GRAY));
        }
        int count = n;
        p.sendSystemMessage(Component.literal("Pedreiras perto: " + count).withStyle(ChatFormatting.LIGHT_PURPLE));
        return Math.max(1, n);
    }

    private static int quarryReset(ServerPlayer p, int radius) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        QuarryData data = QuarryData.get(level);
        int n = 0;
        for (String key : new java.util.ArrayList<>(data.all().keySet())) {
            QuarryData.Quarry q = data.get(key);
            if (q == null || Math.abs(q.cx - at.getX()) > radius || Math.abs(q.cz - at.getZ()) > radius) continue;
            if (data.remove(key)) n++;
        }
        int count = n;
        p.sendSystemMessage(Component.literal("Pedreiras esquecidas: " + count).withStyle(ChatFormatting.LIGHT_PURPLE));
        return Math.max(1, n);
    }

    private AutoGlyphsCommands() {}
}
