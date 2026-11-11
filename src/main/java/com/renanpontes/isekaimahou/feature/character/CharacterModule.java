package com.renanpontes.isekaimahou.feature.character;

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "character" — RAÇAS (sem atributos).
 * <ul>
 *   <li>Etapa {@code "race"} da entrada no jogo (logo depois da classe, {@code EntryFlow.STEP_RACE}): tela "Escolha a
 *       sua Raça"; a linha "Raça: X" vai para o letreiro "Parabéns!".</li>
 *   <li>v1.0.1: 11 raças, TODAS livres desde o início (Humano, Elfo, Anão, Meio-Orc, Demônio, Dracônico, Bestial,
 *       Aracnídeo, Fantasma, Anjo, Slime). Trocar depois: Altar da Reencarnação (custa Cristais de Mana).</li>
 *   <li>Cada raça: passiva fraca que cresce nos níveis de raça I–IV ({@link RacePassives}, {@link RaceProgress}), 2 skills
 *       raciais = glifos montáveis no Grimório ({@link RaceGlyphs}) e, no nível V (nível 90 + feito da raça), a
 *       Transformação racial com golpe próprio ({@link RaceForms}, tecla racial).</li>
 *   <li>Relíquias Raciais ({@link com.renanpontes.isekaimahou.feature.character.item.RaceRelicItem}, drops em
 *       {@link RelicDrops}): a única forma de aprender skills de OUTRA raça; nunca são esquecidas.</li>
 *   <li>Aparência racial (orelhas, chifres, caudas, auréola, olhos e patas, corpo espectral, geleia...) e os visuais das
 *       Transformações ({@code RaceFeatureLayer}).</li>
 * </ul>
 * Comandos de op: {@code /mahou character info|feat|form|reset|rebuild}.
 */
public class CharacterModule implements Module {
    @Override
    public String id() {
        return "character";
    }

    @Override
    public void init(IEventBus modBus) {
        CharacterContent.register(modBus);
        CharacterNetwork.register(modBus);
        CharacterService.init();
        RacePassives.init();
        RaceGlyphs.register();
        RaceProgress.init();
        RaceForms.init();
        RelicDrops.init();
        registerCommands();
    }

    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("character")
                .then(Commands.literal("info")
                        .executes(c -> info(c, java.util.List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> info(c, EntityArgument.getPlayers(c, "targets")))))
                // Cumpre o feito racial (testes do nível V / Transformação)
                .then(Commands.literal("feat").then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> feat(c, null))
                        .then(Commands.argument("race", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Race.ALL).map(r -> r.id), b))
                                .executes(c -> feat(c, StringArgumentType.getString(c, "race"))))))
                // Zera a recarga da Transformação
                .then(Commands.literal("form").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                    for (ServerPlayer p : ps) CharacterContent.data(p).setFormReadyAt(0);
                    c.getSource().sendSuccess(() -> Component.translatable("commands.isekai_mahou.character.form", ps.size()), true);
                    return ps.size();
                })))
                .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                    for (ServerPlayer p : ps) {
                        CharacterContent.data(p).reset();
                        CharacterService.flush(p);
                        RaceGlyphs.reconcile(p, false);
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("commands.isekai_mahou.character.reset", ps.size()), true);
                    return ps.size();
                })))
                .then(Commands.literal("rebuild").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                    ps.forEach(CharacterService::grantRebuild);
                    c.getSource().sendSuccess(() -> Component.translatable("commands.isekai_mahou.character.rebuild", ps.size()), true);
                    return ps.size();
                })))));
    }

    private static int info(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            CharacterData d = CharacterContent.data(p);
            MutableComponent line = Component.literal(p.getScoreboardName() + ": ").append(d.race().displayName())
                    .append(Component.literal(d.created() ? "" : " (?)"));
            if (d.race() == Race.DRAGONBORN) line.append(Component.literal("  ")).append(d.ancestry().displayName());
            line.append(Component.literal("  rebirths=" + d.rebirths() + " form_ready=" + d.formReadyAt()));
            c.getSource().sendSuccess(() -> line, false);
            String dbg = RaceProgress.debug(p);
            c.getSource().sendSuccess(() -> Component.literal("  " + dbg).withColor(0xA89EC0), false);
        }
        return players.size();
    }

    private static int feat(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
        Race one = id == null ? null : Race.byId(id);
        if (id != null && one == null) {
            c.getSource().sendFailure(Component.translatable("commands.isekai_mahou.character.unknown_race", id));
            return 0;
        }
        for (ServerPlayer p : ps) {
            Race r = one != null ? one : CharacterContent.data(p).race();
            RaceProgress.completeFeat(p, r);
            CharacterService.flush(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("commands.isekai_mahou.character.feat", ps.size()), true);
        return ps.size();
    }
}
