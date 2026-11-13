package com.renanpontes.isekaimahou.feature.class_steelball;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.SbGlyphs;
import com.renanpontes.isekaimahou.feature.class_steelball.item.CorpsePartItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitTier;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SteelBallItem;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "class_steelball" (v1.0) — STEEL BALL, a classe da ROTAÇÃO (homenagem Steel Ball Run, com nomes e visuais
 * próprios). Bolas de aço que giram e voltam para a mão, unhas giratórias (Atos 1→4), as 5 Lições do Mestre Rotari
 * (NPC itinerante), as 9 Partes do Cadáver Santo escondidas em Sepulturas pelo mundo (e raramente em outros baús),
 * bônus montado a cavalo, roupa de 4 graus (GeckoLib), 10 glifos de origem (com a forma G Rotação Infinita) e 2 lendas.
 * Tudo montável no Grimório. O CORAÇÃO DO CADÁVER SANTO é a 9ª e última parte (cai de um chefe para quem já tem as
 * outras 8) e desperta a forma G LOVE TRAIN ({@link SbLoveTrain}).
 */
public class ClassSteelballModule implements Module {
    @Override
    public String id() {
        return "class_steelball";
    }

    @Override
    public void init(IEventBus modBus) {
        SbRegistry.register(modBus);
        modBus.addListener(SbFxNet::register);
        SbGlyphs.register();
        SbLegends.register();
        modBus.addListener(SbLegends::validate);
        SbEvents.register();
        SbMentorService.register();

        // Kit: a bola de aço (id do MagicPath), a Carta do Mestre e a roupa de Jóquei (grau I) vestida
        ClassKits.addItem(MagicPath.STEELBALL, () -> new ItemStack(SbRegistry.STEEL_BALL.get()));
        ClassKits.addItem(MagicPath.STEELBALL, () -> new ItemStack(SbRegistry.MENTOR_LETTER.get()));
        for (ArmorItem.Type type : SbOutfitTier.TYPES) {
            ClassKits.setArmor(MagicPath.STEELBALL, type.getSlot(), () -> new ItemStack(SbRegistry.outfit(SbOutfitTier.JOCKEY, type).get()));
        }
        // girar a bola na mão conta como "mirando" (Foco)
        ClassResources.addAimItem(stack -> stack.getItem() instanceof SteelBallItem);

        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("steelball")
                .then(Commands.literal("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, SbProgress.MAX_RANK))
                        .executes(c -> setRank(c, IntegerArgumentType.getInteger(c, "rank")))))
                .then(Commands.literal("lesson").executes(ClassSteelballModule::lesson))
                .then(Commands.literal("parts")
                        .then(Commands.literal("all").executes(c -> parts(c, true)))
                        .then(Commands.literal("reset").executes(c -> parts(c, false))))
                .then(Commands.literal("part").then(Commands.argument("part", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(SbPart.values()).map(p -> p.id), b))
                        .executes(c -> {
                            SbPart p = SbPart.byId(StringArgumentType.getString(c, "part"));
                            if (p == null) return 0;
                            CorpsePartItem.absorb(c.getSource().getPlayerOrException(), p);
                            return 1;
                        })))
                .then(Commands.literal("lovetrain").executes(c -> {
                    SbLoveTrain.grant(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mentor").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    return SbMentorService.spawnNear(sp) != null ? 1 : 0;
                }))
                .then(Commands.literal("dismiss").executes(c -> {
                    SbMentorService.dismiss(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("outfit").then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(SbOutfitTier.values()).map(t -> t.id), b))
                        .executes(c -> {
                            SbOutfitTier t = SbOutfitTier.byId(StringArgumentType.getString(c, "tier"));
                            if (t == null) return 0;
                            SbOutfits.deliver(c.getSource().getPlayerOrException(), t);
                            return 1;
                        })))));
    }

    /** /mahou steelball rank N: vai para o rank N (sobe pelas lições, entregando glifos; descer só zera o contador). */
    private static int setRank(CommandContext<CommandSourceStack> c, int target) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        int cur = SbProgress.rank(d);
        if (target < cur) {
            MagicApi.addCounter(sp, SbProgress.RANK, target - d.counter(SbProgress.RANK));
        } else {
            for (int i = cur; i < target; i++) SbProgress.rankUp(sp);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.steelball.rank", target, SbProgress.act(MagicApi.get(sp))), false);
        return 1;
    }

    /** /mahou steelball lesson: o que falta para a próxima lição (Ato, rank, partes). */
    private static int lesson(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        SbProgress.Rank next = SbProgress.next(d);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.steelball.status", SbProgress.rank(d),
                SbProgress.act(d), SbPart.count(d), SbPart.values().length), false);
        if (next == null) return 1;
        var miss = SbProgress.missing(d, next);
        if (miss.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.steelball.ready", next.title()), false);
        } else {
            for (var line : miss) c.getSource().sendSuccess(() -> Component.literal("• ").append(line), false);
        }
        return 1;
    }

    private static int parts(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        for (SbPart p : SbPart.values()) {
            if (all) CorpsePartItem.absorb(sp, p);
            else d.setFlag(p.flag(), false);
        }
        if (!all) MagicApi.addCounter(sp, SbPart.COUNTER, -d.counter(SbPart.COUNTER));
        SbEvents.syncParts(sp);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.steelball.parts", SbPart.count(MagicApi.get(sp))), false);
        return 1;
    }
}
