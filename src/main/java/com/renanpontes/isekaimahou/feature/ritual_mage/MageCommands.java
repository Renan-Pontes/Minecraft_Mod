package com.renanpontes.isekaimahou.feature.ritual_mage;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.MageResearchData;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.PhenomenaManager;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Phenomenon;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Subcomandos /mahou do Ritual do Mago (precisam de cheats):
 *   ritual mage                       entrega os itens do ritual (kit do Mago)
 *   mage_research reveal              registra todos os fenômenos no Atlas (sem notas)
 *   mage_research reset               apaga o Atlas e as teses
 *   mage_research notes <el> <n>      dá notas de pesquisa de um elemento
 */
public final class MageCommands {

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("ritual").then(Commands.literal("mage").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                for (ItemStack stack : ClassKits.items(MagicPath.MAGE)) {
                    if (!p.getInventory().add(stack)) p.drop(stack, false);
                }
                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mage.ritual"), false);
                return 1;
            })));
            root.then(MahouCommands.op("mage_research")
                    .then(Commands.literal("reveal").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        MageResearchData d = p.getData(MageRegistries.RESEARCH);
                        long now = p.serverLevel().getGameTime();
                        for (Phenomenon ph : PhenomenaManager.available(p.server)) {
                            if (!d.isDiscovered(ph.id.toString())) d.observe(ph.id.toString(), now);
                        }
                        MagePayloads.syncAtlas(p);
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mage.reveal"), false);
                        return 1;
                    }))
                    .then(Commands.literal("reset").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        p.getData(MageRegistries.RESEARCH).reset();
                        MagePayloads.syncAtlas(p);
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mage.reset"), false);
                        return 1;
                    }))
                    .then(Commands.literal("notes").then(Commands.argument("element", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Element.values()).map(e -> e.id), b))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                String raw = StringArgumentType.getString(c, "element");
                                Element e = Element.byId(raw);
                                if (e == null) {
                                    c.getSource().sendFailure(Component.translatable("command.isekai_mahou.mage.bad_element", raw));
                                    return 0;
                                }
                                int n = IntegerArgumentType.getInteger(c, "count");
                                ItemStack stack = ResearchNoteItem.of(ResearchNoteItem.UNKNOWN, e, n);
                                if (!p.getInventory().add(stack)) p.drop(stack, false);
                                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mage.notes", n, e.displayName()), false);
                                return 1;
                            })))));
        });
    }

    private MageCommands() {}
}
