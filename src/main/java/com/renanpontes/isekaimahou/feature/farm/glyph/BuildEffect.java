package com.renanpontes.isekaimahou.feature.farm.glyph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.build.BuildJob;
import com.renanpontes.isekaimahou.feature.farm.build.BuildMaterial;
import com.renanpontes.isekaimahou.feature.farm.build.BuildQueue;
import com.renanpontes.isekaimahou.feature.farm.build.Supply;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Base dos glifos de Construção Arcana: trabalham no bloco atingido (ou embaixo da criatura atingida), usam o material
 * do conjurador (mão secundária → material da varinha → primeiro bloco da barra) e os blocos do inventário/baú ligado.
 * No Pedestal Rúnico (FakePlayer) o material e os blocos vêm dos baús a até {@link #PEDESTAL_CHEST_RANGE} blocos.
 * Cada bloco custa {@link #MANA_PER_BLOCK} além do custo do glifo (a fila constrói alguns por tick, com brilho).
 */
public abstract class BuildEffect extends EffectGlyph {
    public static final float MANA_PER_BLOCK = 0.5F;
    public static final int PEDESTAL_CHEST_RANGE = 2;
    /** Blocos por tick de uma obra de glifo. */
    public static final int SPEED = 16;

    /** Evita a mesma área duas vezes no mesmo tick (formas que acertam o mesmo bloco por várias cópias). */
    private static final Map<SpellContext, Map<Long, Long>> TOUCHED = new WeakHashMap<>();

    protected BuildEffect(String path, int cost) {
        super(path, cost);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        BlockPos below = target.blockPosition().below();
        if (claim(ctx, below)) run(ctx, stats, below, Direction.UP, target.position());
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (claim(ctx, hit.getBlockPos())) run(ctx, stats, hit.getBlockPos(), hit.getDirection(), hit.getLocation());
    }

    private static boolean claim(SpellContext ctx, BlockPos pos) {
        long now = ctx.level().getGameTime();
        synchronized (TOUCHED) {
            Map<Long, Long> m = TOUCHED.computeIfAbsent(ctx, c -> new HashMap<>());
            m.values().removeIf(t -> t != now);
            return m.put(pos.asLong(), now) == null;
        }
    }

    private void run(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at) {
        ServerPlayer player = ctx.casterPlayer();
        if (player == null) return; // mobs não constroem
        Worker w = worker(ctx, player);
        work(ctx, stats, hit, face, at, w);
    }

    /** Quem constrói e com o quê. */
    protected record Worker(ServerPlayer player, Supply supply, @Nullable Block material, boolean automated) {}

    private static Worker worker(SpellContext ctx, ServerPlayer player) {
        if (player instanceof FakePlayer) {
            List<BlockPos> chests = Supply.findContainers(ctx.level(), player.blockPosition(), PEDESTAL_CHEST_RANGE);
            return new Worker(player, Supply.containers(ctx.level(), chests, player.blockPosition()),
                    BuildMaterial.fromContainers(ctx.level(), chests), true);
        }
        return new Worker(player, Supply.player(player), BuildMaterial.forCaster(player), false);
    }

    protected abstract void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w);

    /** Raio inteiro a partir do raio base (Expandir aumenta; Concentrar diminui), com teto. */
    protected static int radius(SpellStats stats, float base, int max) {
        return Math.max(0, Math.min(max, Math.round(stats.radius(base))));
    }

    /** Material obrigatório: avisa e devolve null quando falta. */
    @Nullable
    protected static Block material(Worker w) {
        if (w.material == null && !w.automated) {
            w.player.displayClientMessage(Component.translatable("farm.isekai_mahou.glyph.no_material").withStyle(ChatFormatting.YELLOW), true);
        }
        return w.material;
    }

    /** Põe as operações na fila (com o brilho saindo do ponto de impacto). */
    protected static void submit(SpellContext ctx, Worker w, List<BuildJob.Op> ops, Vec3 origin) {
        if (ops.isEmpty()) return;
        int rgb = FarmFx.BUILD_RGB;
        BuildJob job = w.automated
                ? BuildJob.automated(w.player, w.supply, ops, origin, rgb, SPEED)
                : new BuildJob(w.player, ops, origin, rgb, MANA_PER_BLOCK, SPEED, true);
        if (!BuildQueue.add(job)) {
            if (!w.automated) {
                w.player.displayClientMessage(Component.translatable("farm.isekai_mahou.build.busy").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        ElementFx.burst(ctx.level(), origin, ctx.element(), 10, 0.4);
    }
}
