package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "class_rogue" (v1.0) — LADINO (Físicas, Vigor; Sombra/Vento, difícil Luz). Sistema "Roubo": furtar itens
 * (mobs, NPCs — crime se visto —, chefes e jogadores com PvP), roubar mana/recurso, bênçãos e MAGIAS (Grimório
 * Roubado); crítico e golpes pelas costas com passo de sombra. Ver docs/V10_DESIGN.md §1.
 * <ul>
 *   <li>Kit: Adaga Furtiva ({@code rogue_dagger}, a arma do kit do núcleo), Traje das Sombras I, Grimório Roubado,
 *   Livro-Razão do Ladrão e 8 facas de arremesso. Trajes II/III/IV nos Círculos 3/5/8.</li>
 *   <li>Glifos de origem: {@link RogueGlyphs} (10 + forma G). Lendas: Roubo do Século, Segredo do Saqueador.</li>
 *   <li>Progressão: Livro-Razão do Ladrão, 5 ranks ({@link RogueRanks}, {@code ritual_rank}).</li>
 *   <li>Bônus de classe: {@link RogueEvents} (crítico, dano dobrado pelas costas, Passo Leve, Ponto Vital).</li>
 *   <li>Comandos (op): {@code /mahou rogue status}, {@code /mahou rogue rank <0-5> [jogador]}.</li>
 * </ul>
 */
public class ClassRogueModule implements Module {
    @Override
    public String id() {
        return "class_rogue";
    }

    @Override
    public void init(IEventBus modBus) {
        RogueContent.register(modBus);
        modBus.addListener(RogueNet::register);
        modBus.addListener(RogueGlyphs::validate);
        RogueGlyphs.register();
        RogueEvents.register();

        // Kit: o traje I vestido (substitui o traje genérico da v0.9), o Grimório Roubado, o Livro-Razão e facas.
        // A adaga do kit é entregue pelo núcleo (MagicPath.ROGUE.kitWeapon = rogue_dagger).
        for (ArmorItem.Type type : RogueGear.TYPES) {
            ClassKits.setArmor(MagicPath.ROGUE, type.getSlot(), () -> new ItemStack(RogueContent.piece(RogueGear.T1, type).get()));
        }
        ClassKits.addItem(MagicPath.ROGUE, () -> new ItemStack(RogueContent.STOLEN_GRIMOIRE.get()));
        ClassKits.addItem(MagicPath.ROGUE, () -> new ItemStack(RogueContent.LEDGER.get()));
        ClassKits.addItem(MagicPath.ROGUE, () -> new ItemStack(RogueContent.THROWING_KNIFE.get(), 8));

        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("rogue")
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    status(p);
                    return 1;
                }))
                .then(MahouCommands.op("rank")
                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, RogueRanks.MAX_RANK))
                                .executes(c -> setRank(List.of(c.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(c, "rank"), c.getSource()))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> setRank(EntityArgument.getPlayers(c, "targets"),
                                                IntegerArgumentType.getInteger(c, "rank"), c.getSource())))))));
    }

    private static void status(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        int rank = RogueRanks.rank(d);
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.class_rogue.status", RogueRanks.rankName(rank), rank,
                Math.round(RogueCombat.critChance(p) * 100), d.counter("rogue.steal"), d.counter("rogue.backstab"),
                d.counter("rogue.spell_stolen")).withStyle(ChatFormatting.DARK_PURPLE));
        if (rank < RogueRanks.MAX_RANK) RogueRanks.tryRankUp(p, true);
    }

    /** Ajusta o rank (sobe concedendo as recompensas; para baixo só zera o contador). */
    private static int setRank(java.util.Collection<ServerPlayer> targets, int rank, net.minecraft.commands.CommandSourceStack src) {
        for (ServerPlayer p : targets) {
            MagicData d = MagicApi.get(p);
            int cur = RogueRanks.rank(d);
            if (rank < cur) {
                MagicApi.addCounter(p, RogueRanks.RANK_COUNTER, rank - cur);
                // o contador geral de rituais acompanha (subir de novo não infla o ritual_rank)
                MagicApi.addCounter(p, RogueRanks.GLOBAL_RANK, -Math.min(cur - rank, d.counter(RogueRanks.GLOBAL_RANK)));
            } else {
                for (int r = cur + 1; r <= rank; r++) RogueRanks.promote(p, r);
            }
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.class_rogue.rank_set", p.getDisplayName(), rank), true);
        }
        return targets.size();
    }
}
