package com.renanpontes.isekaimahou.feature.ritual_necro;

import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.PhylacteryItem;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRite;
import com.renanpontes.isekaimahou.feature.ritual_necro.rite.NecroRites;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulHarvest;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "ritual_necro": Ritual do Necromante — Colheita de Almas.
 * <ul>
 *   <li>Filactério guarda almas (por tipo de mob) de mortes com a Foice de Ossos ou com ele na mão secundária.</li>
 *   <li>Altar Ossuário (multibloco: anel de Blocos de Osso + crânios/velas/luzes de alma nos cantos).</li>
 *   <li>À meia-noite, o conjunto de almas do próximo rank (data/&lt;ns&gt;/necro_rites/rank_&lt;n&gt;.json) é oferecido:
 *       XP, glifos de Necromancia, despertar de Sombra/Terra, limite de servos ({@code necro.servant_cap}) e um servo.</li>
 * </ul>
 */
public class RitualNecroModule implements Module {
    @Override
    public String id() {
        return "ritual_necro";
    }

    @Override
    public void init(IEventBus modBus) {
        NecroContent.register(modBus);
        modBus.addListener(RitualNecroModule::creativeTab);

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new NecroRites.Loader()));
        // Prioridade baixa: só colhe a alma se nenhum outro módulo cancelou a morte (ex.: totens, ressurreição)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SoulHarvest::onDeath);
        NeoForge.EVENT_BUS.addListener(SoulHarvest::onServerTick);
        NeoForge.EVENT_BUS.addListener(SoulHarvest::onLogin);
        NeoForge.EVENT_BUS.addListener(SoulHarvest::onServerStopping);

        registerKit();
        registerCommands();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        NecroContent.creativeItems().forEach(event::accept);
    }

    /** Kit do Necromante: Filactério, Foice, Altar, 16 ossos e 4 Blocos de Osso (o resto do anel sai dos ossos). */
    private static void registerKit() {
        ClassKits.addItem(MagicPath.NECROMANCER, () -> new ItemStack(NecroContent.PHYLACTERY.get()));
        ClassKits.addItem(MagicPath.NECROMANCER, () -> new ItemStack(NecroContent.BONE_SCYTHE.get()));
        ClassKits.addItem(MagicPath.NECROMANCER, () -> new ItemStack(NecroContent.ALTAR_ITEM.get()));
        ClassKits.addItem(MagicPath.NECROMANCER, () -> new ItemStack(Items.BONE, 16));
        ClassKits.addItem(MagicPath.NECROMANCER, () -> new ItemStack(Items.BONE_BLOCK, 4));
    }

    /** /mahou necro rank &lt;n&gt; | souls &lt;mob&gt; &lt;n&gt; | fill (operadores, para testes). */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("necro")
                .then(Commands.literal("rank").then(Commands.argument("n", IntegerArgumentType.integer(0, 20)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    int target = IntegerArgumentType.getInteger(c, "n");
                    MagicApi.addCounter(p, NecroRitual.RANK, target - NecroRitual.rank(d));
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.necro.rank", target), false);
                    return target;
                })))
                .then(Commands.literal("souls").then(Commands.argument("mob", ResourceArgument.resource(ctx, Registries.ENTITY_TYPE))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, SoulStore.MAX_PER_TYPE)).executes(c -> {
                            Holder.Reference<EntityType<?>> mob = ResourceArgument.getEntityType(c, "mob");
                            int n = IntegerArgumentType.getInteger(c, "n");
                            return addSouls(c, Map.of(mob.key().location(), n));
                        }))))
                .then(Commands.literal("fill").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    NecroRite rite = NecroRites.get(NecroRitual.rank(MagicApi.get(p)) + 1);
                    if (rite == null) {
                        c.getSource().sendFailure(NecroRitual.msg(NecroRites.isEmpty() ? "no_rites" : "max_rank"));
                        return 0;
                    }
                    return addSouls(c, rite.souls());
                }))));
    }

    /** Põe almas no Filactério da mão do jogador (comandos de teste). */
    private static int addSouls(CommandContext<CommandSourceStack> c, Map<ResourceLocation, Integer> souls) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack phyl = PhylacteryItem.find(p, false);
        if (phyl.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.necro.no_phylactery"));
            return 0;
        }
        SoulStore store = SoulStore.of(phyl);
        int total = 0;
        for (Map.Entry<ResourceLocation, Integer> e : souls.entrySet()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(e.getKey())) continue;
            store = store.add(e.getKey(), e.getValue());
            total += e.getValue();
        }
        phyl.set(NecroContent.SOULS.get(), store);
        int added = total;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.necro.souls", added), false);
        return added;
    }
}
