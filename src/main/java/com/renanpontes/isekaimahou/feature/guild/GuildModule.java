package com.renanpontes.isekaimahou.feature.guild;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntCommands;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntingGrounds;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuests;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Modulo "guild": Guilda dos Aventureiros.
 * <ul>
 *   <li>NPCs (GeckoLib): Recepcionista (registro, quadro, entregas, loja), Mestre da Guilda (provas de promoção)
 *       e Aventureiros errantes (conversam, lutam, trocam itens).</li>
 *   <li>Blocos: Quadro de Missões e Balcão da Guilda. Itens: Cartão de Aventureiro e Moeda da Guilda.</li>
 *   <li>Missões data-driven ({@code data/<ns>/guild_quests}) por rank F → S, quadro diário, até 3 ativas; loja
 *       data-driven ({@code data/<ns>/guild_shop}); provas de promoção; aba "Guilda" no menu B.</li>
 *   <li>v0.7: objetivo {@code trial_clear} (Câmaras de Provação), missões do Avatar/Sentinela e do Pedestal Rúnico; a prova
 *       de promoção é sorteada por jogador e por dia entre as opções do rank (prova, _alt, _chamber); promoção dá um
 *       equipamento do Arsenal por id ({@link PromotionGear}); missões e prova no rastreador de objetivos (tecla J), com
 *       seta para o último quadro/recepção/Mestre usado; Pergaminho Lendário aleatório na loja (Rank S).</li>
 * </ul>
 * <ul>
 *   <li>v0.8: Quadros de Facção ({@code faction_board[facing, faction=guild|kingdom|academy|church|underworld]}) com
 *       missões liberadas por patamar de reputação; toda missão da Guilda dá + Guilda; preços pela reputação; aventureiro
 *       clérigo; CONTRATAR aventureiros (companheiros que seguem, lutam, conjuram e sobem de rank — {@link CompanionService});
 *       grupos nascem nas cidades ({@code core Places}) e nas estradas; contadores novos ({@link GuildCounters}).</li>
 *   <li>v0.9: ÁREAS DE CAÇA ({@link HuntingGrounds}): missões de caça marcam um lugar a 150–500 blocos no bioma das
 *       criaturas, com estandarte e seta no rastreador; lá as criaturas nascem em ondas telegrafadas e soltam essências.
 *       Presente de promoção pela arma das 15 classes ({@link PromotionGear}).</li>
 * </ul>
 * O rank é espelhado no contador "guild_rank" (1 = F … 7 = S) e cada missão concluída soma {@code quest.<id>} e
 * {@code guild_quests} nos contadores do MagicData (para Diário/HUD/outros módulos).
 */
public class GuildModule implements Module {
    @Override
    public String id() {
        return "guild";
    }

    @Override
    public void init(IEventBus modBus) {
        GuildContent.register(modBus);
        modBus.addListener(GuildNetwork::register);

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new GuildQuests.QuestLoader(e.getRegistryAccess()));
            e.addListener(new GuildQuests.ShopLoader(e.getRegistryAccess()));
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 20 == 7) GuildService.tickPlayer(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                GuildService.data(sp).markDirty();
                GuildService.sync(sp, true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) GuildService.sync(sp, true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                GuildService.endSession(sp);
                CompanionService.forget(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) CompanionService.forget(sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            AdventurerSpawner.tick(e.getServer());
            HuntingGrounds.onServerTick(e);
        });
        NeoForge.EVENT_BUS.addListener(CompanionService::onIncomingDamage);
        // LOWEST: só mortes de verdade (um totem/fase de chefe cancela antes e o abate não conta).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (LivingDeathEvent e) -> {
            CompanionService.onDeath(e);
            GuildCounters.onDeath(e);
            HuntingGrounds.onDeath(e);
        });
        // v0.9: Áreas de Caça — essências dos mobs da área, limpeza de marcas órfãs, só as criaturas da missão nascem lá.
        NeoForge.EVENT_BUS.addListener(HuntingGrounds::onDrops);
        NeoForge.EVENT_BUS.addListener(HuntingGrounds::onJoin);
        NeoForge.EVENT_BUS.addListener(HuntingGrounds::onPositionCheck);
        NeoForge.EVENT_BUS.addListener(HuntingGrounds::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(GuildCounters::onPlace);
        // Estado estático limpo ao parar o servidor (sessões, cache de salões, assinaturas enviadas).
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            GuildService.clearAll();
            CompanionService.clearAll();
            AdventurerSpawner.clear();
            HuntingGrounds.clearStatic();
        });

        registerCommands();
    }

    /** /mahou guild rank &lt;F..S&gt; [jogadores] | points &lt;n&gt; | complete | reset (operadores, para testes). */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("guild")
                .then(Commands.literal("rank").then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(GuildRank.values()).map(Enum::name), b))
                        .executes(c -> setRank(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> setRank(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("points").then(Commands.argument("n", IntegerArgumentType.integer(-100000, 100000)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GuildData d = GuildService.data(p);
                    if (!d.registered()) GuildService.setRank(p, GuildRank.F);
                    d.addPoints(IntegerArgumentType.getInteger(c, "n"));
                    GuildService.sync(p, true);
                    int total = d.points();
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.points", total), false);
                    return total;
                })))
                .then(Commands.literal("complete").executes(c -> {
                    GuildService.forceComplete(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.complete"), false);
                    return 1;
                }))
                // v0.9: Áreas de Caça (here | scout | list | clear | sweep).
                .then(HuntCommands.build())
                .then(Commands.literal("reset").executes(c -> {
                    GuildService.reset(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.reset"), false);
                    return 1;
                }))
                // v0.8: um grupo de aventureiros aqui (com classe à escolha) e o tempo dos contratos.
                .then(Commands.literal("party")
                        .executes(c -> spawnParty(c, 3, null))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 6))
                                .executes(c -> spawnParty(c, IntegerArgumentType.getInteger(c, "n"), null))
                                .then(Commands.argument("class", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AdventurerEntity.Variant.values())
                                                .map(AdventurerEntity.Variant::id), b))
                                        .executes(c -> spawnParty(c, IntegerArgumentType.getInteger(c, "n"), StringArgumentType.getString(c, "class"))))))
                .then(Commands.literal("contracts").then(Commands.literal("expire").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = 0;
                    for (net.minecraft.server.level.ServerLevel l : p.server.getAllLevels()) {
                        for (var e : l.getAllEntities()) {
                            if (e instanceof AdventurerEntity a && a.isHiredBy(p)) {
                                CompanionService.contractEnded(a);
                                n++;
                            }
                        }
                    }
                    final int total = n;
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.contracts_expired", total), false);
                    return total;
                })))));
    }

    /** Op: cria {@code n} aventureiros perto do jogador (classe opcional). */
    private static int spawnParty(CommandContext<CommandSourceStack> c, int n, String cls) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        AdventurerEntity.Variant variant = null;
        if (cls != null) {
            for (AdventurerEntity.Variant v : AdventurerEntity.Variant.values()) if (v.id().equalsIgnoreCase(cls)) variant = v;
            if (variant == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.guild.bad_class"));
                return 0;
            }
        }
        int made = 0;
        for (int i = 0; i < n; i++) {
            AdventurerEntity a = GuildContent.ADVENTURER.get().create(p.serverLevel());
            if (a == null) continue;
            double ang = i * Math.PI * 2 / n;
            a.moveTo(p.getX() + Math.cos(ang) * 2.5, p.getY(), p.getZ() + Math.sin(ang) * 2.5, p.getRandom().nextFloat() * 360F, 0);
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(a, p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(p.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null);
            if (variant != null) a.setVariantForCommand(variant);
            a.setHome(a.blockPosition());
            if (p.serverLevel().addFreshEntity(a)) made++;
        }
        final int total = made;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.party", total), true);
        return made;
    }

    private static int setRank(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        GuildRank rank = GuildRank.byLetter(StringArgumentType.getString(c, "rank"));
        if (rank == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.guild.bad_rank"));
            return 0;
        }
        for (ServerPlayer p : players) {
            GuildService.setRank(p, rank);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.guild.rank", p.getDisplayName(), rank.letter()), true);
        }
        return players.size();
    }
}
