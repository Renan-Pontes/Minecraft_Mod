package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mahou shinigami} (qualquer jogador): estado da classe e do próximo rank. Com cheats: {@code rank <0-5>} (dá as
 * recompensas que faltam), {@code archetype <id>} (troca o arquétipo e ensina o glifo), {@code feats} (completa os
 * feitos do próximo rank), {@code inner} (entra/sai do Mundo Interior na hora), {@code shikai} (libera/sela a Shikai sem
 * precisar usar a espada) e {@code forge} (release 1.0: estado dos espaços de receita da Shikai e da Bankai).
 */
public final class ShinigamiCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("shinigami")
                .executes(c -> status(c.getSource().getPlayerOrException()))
                .then(MahouCommands.op("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, Shinigami.MAX_RANK)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    int target = IntegerArgumentType.getInteger(c, "rank");
                    MagicData d = MagicApi.get(sp);
                    if (target < Shinigami.rank(d)) {
                        MagicApi.addCounter(sp, Shinigami.RANK, target - d.counter(Shinigami.RANK));
                    } else {
                        if (target >= 2 && Shinigami.archetype(d) == null) {
                            MagicApi.addCounter(sp, Shinigami.ARCHETYPE, Archetype.WEIGHT.counterValue() - d.counter(Shinigami.ARCHETYPE));
                        }
                        for (int r = Shinigami.rank(d) + 1; r <= target; r++) ShinigamiRanks.grant(sp, ShinigamiRanks.rank(r));
                    }
                    ShinigamiEvents.syncPassives(sp);
                    c.getSource().sendSuccess(() -> Shinigami.msg("cmd_rank", target), false);
                    return 1;
                })))
                .then(MahouCommands.op("archetype").then(Commands.argument("archetype", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Archetype.values()).map(a -> a.id), b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Archetype a = Archetype.byId(StringArgumentType.getString(c, "archetype"));
                            if (a == null) {
                                c.getSource().sendFailure(Shinigami.msg("cmd_archetype_unknown"));
                                return 0;
                            }
                            MagicData d = MagicApi.get(sp);
                            MagicApi.addCounter(sp, Shinigami.ARCHETYPE, a.counterValue() - d.counter(Shinigami.ARCHETYPE));
                            if (Shinigami.rank(d) >= 2) MagicApi.learnGlyph(sp, IsekaiMahou.id(a.glyph));
                            if (InnerWorld.inOwn(sp)) InnerWorld.reshape(sp);
                            c.getSource().sendSuccess(() -> Shinigami.msg("cmd_archetype", a.displayName()), false);
                            return 1;
                        })))
                .then(MahouCommands.op("feats").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    ShinigamiRanks.Rank next = ShinigamiRanks.rank(Shinigami.rank(MagicApi.get(sp)) + 1);
                    if (next == null) return 0;
                    for (ShinigamiRanks.Req q : next.reqs()) {
                        int missing = q.amount() - q.current().applyAsInt(MagicApi.get(sp));
                        if (missing <= 0) continue;
                        String key = switch (q.key()) {
                            case "kills" -> Shinigami.KILLS;
                            case "kido" -> Shinigami.KIDO;
                            case "meditations" -> Shinigami.MEDITATIONS;
                            case "shikai_hits" -> Shinigami.SHIKAI_HITS;
                            case "releases" -> Shinigami.RELEASES;
                            default -> "boss.shinigami_test";
                        };
                        MagicApi.addCounter(sp, key, missing);
                    }
                    c.getSource().sendSuccess(() -> Shinigami.msg("cmd_feats", Shinigami.roman(next.number())), false);
                    return 1;
                }))
                .then(MahouCommands.op("shikai").executes(c -> {
                    Release.toggleShikai(c.getSource().getPlayerOrException(), net.minecraft.world.InteractionHand.MAIN_HAND);
                    return 1;
                }))
                .then(MahouCommands.op("forge").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    for (String slot : new String[] {ReleaseForge.SHIKAI, ReleaseForge.BANKAI}) forgeLine(sp, slot);
                    return 1;
                }))
                .then(MahouCommands.op("inner").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    if (InnerWorld.is(sp.level())) InnerWorld.leave(sp);
                    else InnerWorld.enter(sp);
                    return 1;
                }))));
    }

    /** Uma linha por espaço de receita: se dá para moldar, a receita escolhida e se ela está valendo agora. */
    private static void forgeLine(ServerPlayer sp, String slot) {
        com.renanpontes.isekaimahou.glyph.SpellRecipe r = ReleaseForge.recipe(sp, slot);
        Component recipe = r == null ? Component.translatable("screen.isekai_mahou.release_forge.none")
                : r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.tforge.unnamed") : Component.literal(r.name());
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.release_forge.debug",
                Component.translatable("screen.isekai_mahou.tforge.slot." + slot), yesNo(ReleaseForge.canShape(MagicApi.get(sp))), recipe,
                yesNo(ReleaseForge.active(sp, slot))).withStyle(ChatFormatting.GRAY));
    }

    private static Component yesNo(boolean b) {
        return Component.translatable(b ? "gui.yes" : "gui.no").withStyle(b ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static int status(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        int rank = Shinigami.rank(d);
        List<Component> lines = new ArrayList<>();
        lines.add(Shinigami.msg("status_head", Shinigami.roman(rank), Shinigami.rankTitle(rank)).withColor(Shinigami.RGB));
        Archetype a = Shinigami.archetype(d);
        lines.add(Shinigami.msg("status_spirit", Shinigami.spiritName(d), a == null ? Shinigami.msg("status_no_archetype") : a.displayName())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Shinigami.msg("status_release", Component.translatable(com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem.STATE_KEY
                + (Shinigami.hasBankai(d) ? 2 : Shinigami.hasShikai(d) ? 1 : 0))).withStyle(ChatFormatting.GRAY));
        ShinigamiRanks.Rank next = ShinigamiRanks.rank(rank + 1);
        if (next != null) {
            List<Component> missing = new ArrayList<>();
            boolean ok = ShinigamiRanks.meets(d, next, missing);
            lines.add(Shinigami.msg(ok ? "status_ready" : "status_next", Shinigami.roman(next.number())).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            for (Component m : missing) lines.add(Component.literal(" ✘ ").withStyle(ChatFormatting.RED).append(m.copy().withStyle(ChatFormatting.GRAY)));
        }
        for (Component c : lines) sp.sendSystemMessage(c);
        return 1;
    }

    private ShinigamiCommands() {}
}
