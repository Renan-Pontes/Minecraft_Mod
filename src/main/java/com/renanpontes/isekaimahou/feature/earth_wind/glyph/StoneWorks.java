package com.renanpontes.isekaimahou.feature.earth_wind.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.TempBlocks;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Construcao animada de pedra conjurada (pilares, muralhas, domo): as colunas crescem um bloco por passo
 * e TODAS as pedras voltam ao normal no mesmo instante, {@code duration} ticks depois de terminar.
 */
public final class StoneWorks {

    /**
     * @param columns     cada coluna e uma lista de posicoes na ordem em que crescem (de baixo para cima)
     * @param interval    ticks entre um passo e o proximo
     * @param duration    quanto tempo a construcao fica de pe depois de pronta
     * @param lift        empurra para cima quem estiver no caminho (pilar/muralha); senao pula a posicao ocupada (domo)
     * @param stopOnFail  se um bloco da coluna nao puder ser colocado, a coluna para de crescer
     */
    public static void build(SpellContext ctx, List<List<BlockPos>> columns, int interval, int duration, boolean lift, boolean stopOnFail) {
        ServerLevel level = ctx.level();
        BlockState stone = EwRegistries.RAISED_STONE.get().defaultBlockState();
        int steps = 0;
        for (List<BlockPos> c : columns) steps = Math.max(steps, c.size());
        if (steps == 0) return;
        final int totalSteps = steps;
        final int buildTicks = totalSteps * interval;
        final boolean[] stopped = new boolean[columns.size()];
        EwTasks.repeat(buildTicks + 1, age -> {
            if (age % interval != 0) return true;
            int step = age / interval;
            if (step >= totalSteps) return false;
            int remaining = duration + buildTicks - age;
            int placed = 0;
            BlockPos last = null;
            for (int ci = 0; ci < columns.size(); ci++) {
                List<BlockPos> col = columns.get(ci);
                if (stopped[ci] || step >= col.size()) continue;
                BlockPos pos = col.get(step);
                if (place(ctx, level, pos, stone, remaining, lift)) {
                    placed++;
                    last = pos;
                } else if (stopOnFail) {
                    stopped[ci] = true;
                }
            }
            if (placed > 0) {
                Vec3 at = Vec3.atCenterOf(last);
                EwFx.sound(level, at, SoundEvents.DEEPSLATE_BRICKS_PLACE, 0.9F, 0.6F + level.random.nextFloat() * 0.2F);
                if (step == 0) EwFx.sound(level, at, SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.5F);
            }
            return true;
        });
    }

    private static boolean place(SpellContext ctx, ServerLevel level, BlockPos pos, BlockState stone, int ticks, boolean lift) {
        if (!EwUtil.canEdit(ctx, pos) || !level.getBlockState(pos).canBeReplaced()) return false;
        List<Entity> inside = level.getEntities((Entity) null, new AABB(pos), e -> e.isAlive() && !e.isSpectator() && !e.noPhysics);
        if (!inside.isEmpty()) {
            if (!lift) return false;
            // so sobe se houver espaco acima de todos; senao o pilar/muralha para aqui
            for (Entity e : inside) {
                double dy = pos.getY() + 1.0 - e.getY();
                AABB moved = e.getBoundingBox().move(0, dy, 0);
                if (!level.noCollision(e, moved) || moved.intersects(new AABB(pos))) return false;
            }
        }
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        if (!TempBlocks.place(level, pos, stone, ticks)) return false;
        // mods de protecao e areas protegidas (ex.: labirintos que proibem escalar) podem vetar a colocacao
        if (ctx.caster() instanceof Player p && EventHooks.onBlockPlace(p, before, Direction.UP)) {
            TempBlocks.revert(level, pos);
            return false;
        }
        for (Entity e : inside) {
            e.teleportTo(e.getX(), pos.getY() + 1.0, e.getZ());
            e.setDeltaMovement(e.getDeltaMovement().x, Math.max(0.1, e.getDeltaMovement().y), e.getDeltaMovement().z);
            e.hurtMarked = true;
            if (e instanceof LivingEntity le && ctx.isAlly(le)) EwUtil.protectFall(le, ticks + 60);
        }
        if (level.random.nextInt(3) == 0) EwFx.debris(level, Vec3.atCenterOf(pos), stone, 6, 0.3);
        return true;
    }

    private StoneWorks() {}
}
