package com.renanpontes.isekaimahou.feature.space_magic;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de teste (op): {@code /mahou space_magic learn|scrolls|box|gate|visitall|mark|clearmark|recover|bossloot}.
 * {@code recover}: Recuperar Pertences sem custo nem recarga; {@code bossloot}: solta aos seus pés um item marcado
 * como loot de chefe (para testar o envio ao Item Box com o inventário cheio).
 */
final class SpaceCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("space_magic")
                .then(MahouCommands.op("learn").executes(SpaceCommands::learn))
                .then(MahouCommands.op("scrolls").executes(SpaceCommands::scrolls))
                .then(MahouCommands.op("box").executes(c -> {
                    ItemBoxMenu.open(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(MahouCommands.op("gate").executes(SpaceCommands::gate))
                .then(MahouCommands.op("visitall").executes(SpaceCommands::visitAll))
                .then(MahouCommands.op("recover").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    RecoverBelongings.resetCooldown(sp);
                    return RecoverBelongings.begin(sp, 0F) ? 1 : 0;
                }))
                .then(MahouCommands.op("bossloot").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    ItemEntity drop = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 0.3, sp.getZ(), new ItemStack(Items.DIAMOND, 8));
                    drop.getPersistentData().putBoolean(BossLootBox.TAG, true);
                    drop.setPickUpDelay(20);
                    sp.level().addFreshEntity(drop);
                    return 1;
                }))
                .then(MahouCommands.op("mark").executes(SpaceCommands::mark))
                .then(MahouCommands.op("clearmark").executes(c -> {
                    SpaceData.setMark(c.getSource().getPlayerOrException(), null);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.mark_cleared"), false);
                    return 1;
                }))));
    }

    private static int learn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (ResourceLocation id : SpaceGlyphs.ALL) MagicApi.learnGlyph(sp, id);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.learned", SpaceGlyphs.ALL.length), false);
        return 1;
    }

    private static int scrolls(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (ResourceLocation id : SpaceGlyphs.ALL) {
            var stack = SpaceEvents.scroll(id);
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.scrolls", SpaceGlyphs.ALL.length), false);
        return 1;
    }

    /** Abre o portal como se tivesse conjurado (sem gastar mana). */
    private static int gate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Vec3 dir = sp.getLookAngle().multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        SpaceGates.request(sp, sp.position().add(dir.scale(2.6)), sp.getYRot(), SpaceGlyphs.SPACE_COLOR);
        return 1;
    }

    /** Conta todas as dimensões carregadas como "visitadas" (para testar o Abrir Portal). */
    private static int visitAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        int n = 0;
        for (ServerLevel level : sp.server.getAllLevels()) {
            String key = SpaceGates.visitKey(level.dimension());
            if (d.counter(key) == 0) {
                MagicApi.addCounter(sp, key, 1);
                n++;
            }
        }
        int added = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.visited", added), false);
        return 1;
    }

    private static int mark(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        SpaceData.Mark m = SpaceData.mark(sp);
        int slots = SpaceData.boxSlots(MagicApi.get(sp).circle());
        int used = SpaceData.boxUsed(sp);
        if (m == null) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.info_nomark", used, slots), false);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.space_magic.info", SpaceGates.dimName(m.dim().location()),
                    (int) m.pos().x, (int) m.pos().y, (int) m.pos().z, used, slots), false);
        }
        return 1;
    }

    private SpaceCommands() {}
}
