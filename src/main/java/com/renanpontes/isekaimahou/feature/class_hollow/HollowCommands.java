package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.CeroFusionAugment;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.DevourTrigger;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTechniques;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code /mahou hollow} — estado do Hollow; para testes (op): {@code hollowfy} (a cena completa, como se tivesse perdido
 * para o Vazio Interior), {@code souls <n>}, {@code rank <0..4>}, {@code release <pantera|murcielago|serpiente>},
 * {@code hunger <minutos>} (simula tempo sem devorar) e {@code choice} (abre a tela "lutar ou aceitar").
 * v1.4: {@code tech <técnica>} (usa a técnica na hora, carga cheia, sem porta de rank/recarga), {@code cooldowns}
 * (zera as recargas das técnicas), {@code glyphs} (dá o aumento e o gatilho novos da classe) e {@code techs} (lista).
 * Release 1.0: {@code forge} (estado do espaço de receita da Resurrección).
 */
public final class HollowCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("hollow")
                .executes(c -> status(c.getSource().getPlayerOrException()))
                .then(MahouCommands.op("hollowfy").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    if (Hollowfication.inProgress(sp)) return 0;
                    Hollowfication.begin(sp, MagicApi.get(sp).path() == MagicPath.HOLLOW ? Hollow.ORIGIN_COMMAND : Hollow.ORIGIN_DEFEATED);
                    return 1;
                }))
                .then(MahouCommands.op("souls").then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    int n = IntegerArgumentType.getInteger(c, "amount");
                    MagicApi.addCounter(sp, Hollow.SOULS, n);
                    if (n > 0) MagicApi.addCounter(sp, Hollow.SOULS_TOTAL, n);
                    HollowRanks.check(sp);
                    c.getSource().sendSuccess(() -> Hollow.msg("cmd_souls", n), false);
                    return 1;
                })))
                .then(MahouCommands.op("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, Hollow.MAX_RANK)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    int target = IntegerArgumentType.getInteger(c, "rank");
                    MagicData d = MagicApi.get(sp);
                    if (target <= Hollow.rank(d)) {
                        MagicApi.addCounter(sp, Hollow.RANK, target - d.counter(Hollow.RANK));
                        HollowEvents.syncLook(sp);
                    } else {
                        if (target >= Hollow.ARRANCAR && Hollow.release(d) == null) {
                            MagicApi.addCounter(sp, Hollow.RELEASE, Hollow.Release.PANTERA.counterValue() - d.counter(Hollow.RELEASE));
                        }
                        HollowRanks.Rank r = HollowRanks.rank(target);
                        if (r != null) HollowRanks.grant(sp, r);
                    }
                    c.getSource().sendSuccess(() -> Hollow.msg("cmd_rank", target), false);
                    return 1;
                })))
                .then(MahouCommands.op("release").then(Commands.argument("release", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Hollow.Release.values()).map(r -> r.id), b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "release");
                            Hollow.Release r = Arrays.stream(Hollow.Release.values()).filter(x -> x.id.equals(id)).findFirst().orElse(null);
                            if (r == null) return 0;
                            MagicData d = MagicApi.get(sp);
                            MagicApi.addCounter(sp, Hollow.RELEASE, r.counterValue() - d.counter(Hollow.RELEASE));
                            HollowEvents.syncLook(sp);
                            c.getSource().sendSuccess(() -> Hollow.msg("cmd_release", r.displayName()), false);
                            return 1;
                        })))
                .then(MahouCommands.op("hunger").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 600)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    int min = IntegerArgumentType.getInteger(c, "minutes");
                    MagicData d = MagicApi.get(sp);
                    int want = Hollow.nowSeconds(sp) - min * 60;
                    MagicApi.addCounter(sp, Hollow.LAST_FEED, want - d.counter(Hollow.LAST_FEED));
                    HollowSouls.hungerTick(sp);
                    c.getSource().sendSuccess(() -> Hollow.msg("cmd_hunger", min), false);
                    return 1;
                })))
                .then(MahouCommands.op("tech").then(Commands.argument("tech", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HollowTech.values()).map(t -> t.id), b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            HollowTech t = HollowTech.byId(StringArgumentType.getString(c, "tech"));
                            if (t == null || !HollowTechniques.debugUse(sp, t)) {
                                c.getSource().sendFailure(Hollow.msg("cmd_tech_fail"));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> Hollow.msg("cmd_tech", t.displayName()), false);
                            return 1;
                        })))
                .then(MahouCommands.op("cooldowns").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    HollowTechniques.resetCooldowns(sp);
                    c.getSource().sendSuccess(() -> Hollow.msg("cmd_cooldowns"), false);
                    return 1;
                }))
                .then(MahouCommands.op("glyphs").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    MagicApi.learnGlyph(sp, CeroFusionAugment.ID);
                    MagicApi.learnGlyph(sp, DevourTrigger.ID);
                    c.getSource().sendSuccess(() -> Hollow.msg("cmd_glyphs"), false);
                    return 1;
                }))
                .then(MahouCommands.op("forge").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    com.renanpontes.isekaimahou.glyph.SpellRecipe r = HollowForge.recipe(sp);
                    Component recipe = r == null ? Component.translatable("screen.isekai_mahou.release_forge.none")
                            : r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.tforge.unnamed") : Component.literal(r.name());
                    sp.sendSystemMessage(Component.translatable("message.isekai_mahou.release_forge.debug",
                            Component.translatable("screen.isekai_mahou.tforge.slot.hollow_resurreccion"),
                            yesNo(HollowForge.canShape(MagicApi.get(sp))), recipe, yesNo(HollowForge.active(sp))).withStyle(ChatFormatting.GRAY));
                    return 1;
                }))
                .then(Commands.literal("techs").executes(c -> techs(c.getSource().getPlayerOrException())))
                .then(MahouCommands.op("choice").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(sp, new HollowNetwork.Open(HollowNetwork.SCREEN_HOLLOW_CHOICE));
                    return 1;
                }))));
    }

    private static Component yesNo(boolean b) {
        return Component.translatable(b ? "gui.yes" : "gui.no").withStyle(b ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /** Lista as técnicas: liberadas (verde) ou o rank em que chegam. */
    private static int techs(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        boolean released = HollowState.released(sp) != null;
        for (HollowTech t : HollowTech.values()) {
            boolean ok = Hollow.isHollow(sp) && t.unlocked(d, released);
            sp.sendSystemMessage(Component.literal(ok ? " ✔ " : " ✖ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(t.displayName().withStyle(ok ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(t.howTo().withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int status(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!Hollow.isHollow(sp)) {
            sp.sendSystemMessage(Hollow.msg("cmd_not_hollow").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        int rank = Hollow.rank(d);
        sp.sendSystemMessage(Hollow.msg("cmd_status", Hollow.roman(rank), Hollow.rankTitle(rank), Hollow.souls(d), Hollow.soulsTotal(d))
                .withColor(Hollow.RGB));
        int since = HollowSouls.secondsSinceFeed(d, Hollow.nowSeconds(sp));
        sp.sendSystemMessage(Hollow.msg("cmd_hunger_status", since / 60).withStyle(ChatFormatting.GRAY));
        Hollow.Release r = Hollow.release(d);
        if (r != null) sp.sendSystemMessage(Hollow.msg("cmd_release_status", r.displayName()));
        for (Component line : HollowRanks.missing(d)) sp.sendSystemMessage(Component.literal(" • ").append(line).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private HollowCommands() {}
}
