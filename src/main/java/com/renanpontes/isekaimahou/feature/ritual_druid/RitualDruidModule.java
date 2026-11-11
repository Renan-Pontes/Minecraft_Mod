package com.renanpontes.isekaimahou.feature.ritual_druid;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Modulo "ritual_druid": Ritual do Druida — o Bosque Sagrado.
 *
 * A Muda Ancestral vira a Árvore Coração (bloco-núcleo que gera uma árvore procedural em 10 estágios).
 * Cada estágio pede Harmonia do bosque (diversidade de flores, árvores, plantações, animais, água, sem corrupção),
 * uma oferenda e uma tarefa de estação. Ao crescer: XP, ritual_rank, glifos de Metamorfose, despertar de
 * Terra/Água/Luz e um Espírito Animal que conta a próxima tarefa e abençoa o druida.
 */
public class RitualDruidModule implements Module {
    @Override
    public String id() {
        return "ritual_druid";
    }

    @Override
    public void init(IEventBus modBus) {
        DruidContent.register(modBus);
        modBus.addListener(DruidNetwork::register);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);

        NeoForge.EVENT_BUS.addListener(DruidEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(DruidEvents::onPlayerTick);

        // Kit do Druida: a muda, farinha de osso, sementes, mudas variadas e uma enxada
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(DruidContent.ANCESTRAL_SAPLING_ITEM.get()));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.BONE_MEAL, 16));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.WHEAT_SEEDS, 16));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.BEETROOT_SEEDS, 8));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.OAK_SAPLING));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.BIRCH_SAPLING));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.SPRUCE_SAPLING));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.CHERRY_SAPLING));
        ClassKits.addItem(MagicPath.DRUID, () -> new ItemStack(Items.STONE_HOE));

        registerCommands();
    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        e.put(DruidContent.ANIMAL_SPIRIT.get(), AnimalSpiritEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            e.accept(DruidContent.ANCESTRAL_SAPLING_ITEM.get());
            e.accept(DruidContent.HEART_TREE_CORE_ITEM.get());
            e.accept(DruidContent.HEART_TREE_LOG_ITEM.get());
            e.accept(DruidContent.HEART_TREE_LEAVES_ITEM.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(DruidContent.ANIMAL_SPIRIT_EGG.get());
        }
    }

    /** /mahou druid [info] | druid advance (op) | druid rank <n> (op) */
    private void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("druid")
                .executes(c -> info(c.getSource()))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(MahouCommands.op("advance").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    HeartTreeCoreBlockEntity core = treeOf(p);
                    DruidStages.Stage next = core == null ? null : DruidStages.get(core.stage() + 1);
                    if (core == null || next == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.ritual_druid.no_tree"));
                        return 0;
                    }
                    DruidProgress.advance(p, core, next);
                    return 1;
                }))
                .then(MahouCommands.op("rank").then(Commands.argument("n", IntegerArgumentType.integer(0, DruidStages.MAX_STAGE))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int n = IntegerArgumentType.getInteger(c, "n");
                            MagicApi.addCounter(p, DruidStages.RANK_COUNTER, n - DruidProgress.rank(p));
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.ritual_druid.rank", n), true);
                            return 1;
                        })))));
    }

    private static HeartTreeCoreBlockEntity treeOf(ServerPlayer p) {
        if (!DruidProgress.hasLivingTree(p)) return null;
        GlobalPos gp = DruidGrove.of(p).tree();
        ServerLevel level = p.server.getLevel(gp.dimension());
        return level != null && level.getBlockEntity(gp.pos()) instanceof HeartTreeCoreBlockEntity core ? core : null;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int rank = DruidProgress.rank(p);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.ritual_druid.info", DruidStages.roman(rank),
                DruidStages.MAX_STAGE).withStyle(ChatFormatting.GREEN), false);
        HeartTreeCoreBlockEntity core = treeOf(p);
        if (core == null) {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.ritual_druid.no_tree").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        var pos = core.getBlockPos();
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.ritual_druid.where", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.GRAY), false);
        DruidStages.Stage next = DruidStages.get(core.stage() + 1);
        if (next == null) {
            src.sendSuccess(() -> Component.translatable("message.isekai_mahou.ritual_druid.complete").withStyle(ChatFormatting.GOLD), false);
            return 1;
        }
        DruidProgress.Status st = DruidProgress.status((ServerLevel) core.getLevel(), core, p, true);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.ritual_druid.status", st.harmony().total(), next.harmony(),
                next.task().describe(next.amount()), Math.max(0, st.taskDone()), next.amount(),
                Component.translatable(st.offeringOk() ? "gui.isekai_mahou.ritual_druid.done" : "gui.isekai_mahou.ritual_druid.pending"))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
