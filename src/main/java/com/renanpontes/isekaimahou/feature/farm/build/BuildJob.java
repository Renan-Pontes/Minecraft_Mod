package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Uma construção em andamento de um jogador (varinha ou glifo): uma lista de operações feitas bloco a bloco, poucas por
 * tick (a fila global {@link BuildQueue} limita o total). Para no meio se faltar bloco ou mana — o que já foi feito fica
 * no histórico para o Desfazer.
 */
public final class BuildJob {
    public enum Kind {
        /** Coloca onde estiver livre. */
        PLACE,
        /** Troca: só onde o bloco atual é {@code expect}; o bloco velho volta como item. */
        SWAP,
        /** Desfazer: só onde o bloco atual é {@code expect}; volta ao estado {@code state}. */
        UNDO
    }

    public record Op(BlockPos pos, BlockState state, Kind kind, @Nullable Block expect) {}

    final UUID owner;
    final ResourceKey<Level> dimension;
    private final List<Op> ops;
    private final Vec3 origin;
    private final int rgb;
    private final float manaPerBlock;
    private final int perTick;
    private final boolean recordUndo;
    private final List<UndoHistory.Entry> undo = new ArrayList<>();
    private int index;
    private int done;
    private int age;
    private boolean finished;
    /** Conjurador sem conta (FakePlayer do Pedestal Rúnico) e a fonte fixa de blocos dele (baús em volta). */
    @Nullable
    private ServerPlayer fixedActor;
    @Nullable
    private Supply fixedSupply;

    /** Obra de um conjurador "fantasma" (pedestal): usa os baús dados e não gasta mana por bloco. */
    public static BuildJob automated(ServerPlayer fake, Supply supply, List<Op> ops, Vec3 origin, int rgb, int perTick) {
        BuildJob job = new BuildJob(fake, ops, origin, rgb, 0F, perTick, false);
        job.fixedActor = fake;
        job.fixedSupply = supply;
        return job;
    }

    public BuildJob(ServerPlayer owner, List<Op> ops, Vec3 origin, int rgb, float manaPerBlock, int perTick, boolean recordUndo) {
        this.owner = owner.getUUID();
        this.dimension = owner.level().dimension();
        this.ops = ops;
        this.origin = origin;
        this.rgb = rgb;
        this.manaPerBlock = manaPerBlock;
        this.perTick = Math.max(1, perTick);
        this.recordUndo = recordUndo;
    }

    public int size() {
        return ops.size();
    }

    public boolean finished() {
        return finished;
    }

    /** Roda até {@code budget} operações; devolve quantas gastou. */
    int tick(MinecraftServer server, int budget) {
        if (finished) return 0;
        ServerPlayer player = fixedActor != null ? fixedActor : server.getPlayerList().getPlayer(owner);
        ServerLevel level = server.getLevel(dimension);
        if (player == null || level == null || player.level() != level || player.isRemoved() && fixedActor == null) {
            finish(null, null);
            return 0;
        }
        age++;
        Supply supply = fixedSupply != null ? fixedSupply : Supply.player(player);
        MagicData data = MagicApi.get(player);
        int n = Math.min(budget, perTick);
        int used = 0;
        BlockPos last = null;
        while (used < n && index < ops.size()) {
            Op op = ops.get(index++);
            used++;
            Result r = switch (op.kind) {
                case PLACE -> place(level, player, supply, data, op);
                case SWAP -> swap(level, player, supply, data, op);
                case UNDO -> undo(level, player, supply, op);
            };
            if (r == Result.DONE) {
                done++;
                last = op.pos;
                if (done % 3 == 0) FarmFx.placed(level, op.pos, rgb);
                if (done % 5 == 1) Placer.placeSound(level, op.pos, level.getBlockState(op.pos), 0.6F);
            } else if (r == Result.NO_ITEM) {
                Item item = op.state.getBlock().asItem();
                finish(player, Component.translatable("farm.isekai_mahou.build.missing", new ItemStack(item).getHoverName())
                        .withStyle(ChatFormatting.RED));
                return used;
            } else if (r == Result.NO_MANA) {
                finish(player, Component.translatable("farm.isekai_mahou.build.no_mana", MagicApi.resourceName(player))
                        .withStyle(ChatFormatting.RED));
                return used;
            }
        }
        if (last != null && age % 3 == 0) {
            FarmFx.beam(level, origin, Vec3.atCenterOf(last), FarmFx.dust(rgb, 0.7F), 0.6);
        }
        if (index >= ops.size()) {
            finish(player, null);
        }
        return used;
    }

    private enum Result { DONE, SKIP, NO_ITEM, NO_MANA }

    private boolean payMana(MagicData data, Supply supply) {
        if (supply.free() || manaPerBlock <= 0) return true;
        return data.consumeMana(manaPerBlock);
    }

    private void refundMana(MagicData data, Supply supply) {
        if (!supply.free() && manaPerBlock > 0) data.addMana(manaPerBlock);
    }

    private Result place(ServerLevel level, ServerPlayer player, Supply supply, MagicData data, Op op) {
        // A outra metade de porta/cama vem junto com a parte principal (nunca sozinha e de graça)
        if (Placer.isSecondaryPart(op.state)) return Result.SKIP;
        if (!Placer.replaceable(level, op.pos) || level.getBlockState(op.pos).is(op.state.getBlock())) return Result.SKIP;
        if (!Placer.canEdit(level, player, op.pos)) return Result.SKIP;
        Item item = op.state.getBlock().asItem();
        if (item == Items.AIR) return Result.SKIP;
        ItemStack got = ItemStack.EMPTY;
        if (!supply.free()) {
            got = supply.take(s -> s.is(item), item);
            if (got.isEmpty()) return Result.NO_ITEM;
        }
        if (!payMana(data, supply)) {
            supply.give(got);
            return Result.NO_MANA;
        }
        BlockState before = level.getBlockState(op.pos);
        if (!Placer.place(level, player, op.pos, op.state)) {
            supply.give(got);
            refundMana(data, supply);
            return Result.SKIP;
        }
        Placer.placePartner(level, player, op.pos, op.state);
        if (recordUndo) undo.add(new UndoHistory.Entry(op.pos, before, op.state));
        return Result.DONE;
    }

    private Result swap(ServerLevel level, ServerPlayer player, Supply supply, MagicData data, Op op) {
        BlockState current = level.getBlockState(op.pos);
        if (current.getBlock() != op.expect || current.is(op.state.getBlock())) return Result.SKIP;
        if (!Placer.canEdit(level, player, op.pos) || !Placer.removable(level, op.pos, current)) return Result.SKIP;
        Item item = op.state.getBlock().asItem();
        ItemStack got = ItemStack.EMPTY;
        if (!supply.free()) {
            got = supply.take(s -> s.is(item), item);
            if (got.isEmpty()) return Result.NO_ITEM;
        }
        if (!payMana(data, supply)) {
            supply.give(got);
            return Result.NO_MANA;
        }
        List<ItemStack> old = new ArrayList<>();
        if (!Placer.remove(level, player, op.pos, true, old::add)) {
            supply.give(got);
            refundMana(data, supply);
            return Result.SKIP;
        }
        if (!Placer.place(level, player, op.pos, op.state)) {
            // não deu para pôr o novo: devolve o velho no lugar
            level.setBlock(op.pos, current, Block.UPDATE_ALL);
            supply.give(got);
            refundMana(data, supply);
            return Result.SKIP;
        }
        old.forEach(supply::give);
        if (recordUndo) undo.add(new UndoHistory.Entry(op.pos, current, op.state));
        return Result.DONE;
    }

    private Result undo(ServerLevel level, ServerPlayer player, Supply supply, Op op) {
        BlockState current = level.getBlockState(op.pos);
        if (current.getBlock() != op.expect) return Result.SKIP;
        if (!Placer.remove(level, player, op.pos, true, supply::give)) return Result.SKIP;
        BlockState before = op.state;
        if (before.isAir()) return Result.DONE;
        if (before.getBlock() == Blocks.WATER) {
            level.setBlock(op.pos, before, Block.UPDATE_ALL);
            return Result.DONE;
        }
        Item item = before.getBlock().asItem();
        if (!supply.free()) {
            ItemStack got = item == Items.AIR ? ItemStack.EMPTY : supply.take(s -> s.is(item), item);
            if (got.isEmpty()) return Result.DONE; // o bloco antigo já não está com você: fica vazio
        }
        if (Placer.place(level, player, op.pos, before)) Placer.placePartner(level, player, op.pos, before);
        return Result.DONE;
    }

    private void finish(@Nullable ServerPlayer player, @Nullable Component error) {
        if (finished) return;
        finished = true;
        if (recordUndo && !undo.isEmpty()) UndoHistory.push(owner, new UndoHistory.Record(dimension, List.copyOf(undo)));
        fixedSupply = null;
        if (player == null || fixedActor != null) {
            fixedActor = null;
            return;
        }
        ServerLevel level = player.serverLevel();
        if (error != null) {
            player.displayClientMessage(error, true);
            FarmFx.sound(level, player.position(), SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F);
        } else if (done > 0) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.build.done", done).withColor(rgb), true);
            FarmFx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F);
        }
    }

    /** Cancela (servidor parando, jogador saiu). */
    void cancel() {
        finish(null, null);
    }
}
