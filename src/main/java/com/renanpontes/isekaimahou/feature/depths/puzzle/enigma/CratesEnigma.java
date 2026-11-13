package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneLitBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.CratesLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Pedras do Peso (Sokoban): barris que se EMPURRAM com o botão direito, uma casa para longe de você (de pé no chão,
 * colado no barril). Leve todos às Placas Rúnicas. Mureta de 1 bloco em volta, pilares de basalto no meio. Barril
 * preso numa quina? Botão de recomeçar. Pista: o próximo empurrão de uma solução mínima (ou o aviso "travou").
 */
public final class CratesEnigma extends Enigma {
    private final CratesLogic def;
    private int[] crates;
    private int moves;
    private int hintCrate = -1, hintDir = -1;
    private long hintUntil;

    public CratesEnigma(int tier, long seed) {
        super(tier, seed);
        def = CratesLogic.generate(seed, tier);
        crates = def.start.clone();
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.CRATES;
    }

    private int u0() {
        return 1 + (7 - def.w) / 2;
    }

    private int u(int cell) {
        return u0() + cell % def.w;
    }

    private static int v(int cell, int w) {
        return 2 + cell / w;
    }

    private static BlockState crate() {
        return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
    }

    @Override
    public void build(EnigmaRun c) {
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        int ua = u0() - 1, ub = u0() + def.w;
        for (int u = ua; u <= ub; u++) {
            c.set(u, 1, 0, wall);
            c.set(u, 2 + def.d, 0, wall);
        }
        for (int v = 2; v < 2 + def.d; v++) {
            c.set(ua, v, 0, wall);
            c.set(ub, v, 0, wall);
        }
        for (int i = 0; i < def.pillar.length; i++) {
            if (!def.pillar[i]) continue;
            c.set(u(i), v(i, def.w), 0, Blocks.POLISHED_BASALT.defaultBlockState());
            c.set(u(i), v(i, def.w), 1, Blocks.POLISHED_BASALT.defaultBlockState());
        }
        for (int g : def.goals) c.set(u(g), v(g, def.w), -1, PuzzleBlocks.TILE.get().defaultBlockState());
        placeCrates(c);
    }

    private void placeCrates(EnigmaRun c) {
        for (int cr : crates) c.set(u(cr), v(cr, def.w), 0, crate());
        paintGoals(c);
    }

    private void paintGoals(EnigmaRun c) {
        for (int g : def.goals) {
            boolean on = false;
            for (int cr : crates) on |= cr == g;
            c.set(u(g), v(g, def.w), -1, PuzzleBlocks.TILE.get().defaultBlockState().setValue(RuneLitBlock.LIT, on));
        }
    }

    @Override
    public void reset(EnigmaRun c) {
        for (int cr : crates) c.set(u(cr), v(cr, def.w), 0, Blocks.AIR.defaultBlockState());
        // quem estiver de pé onde um barril vai reaparecer volta para a entrada (ninguém fica preso dentro de bloco)
        for (ServerPlayer sp : c.playersNear(0)) {
            int[] at = c.feetCell(sp);
            if (at == null) continue;
            for (int s : def.start) if (at[0] == u(s) && at[1] == v(s, def.w)) c.sendToStart(sp);
        }
        crates = def.start.clone();
        moves = 0;
        hintCrate = -1;
        placeCrates(c);
    }

    private int crateAt(int u, int v) {
        int x = u - u0(), y = v - 2;
        if (x < 0 || x >= def.w || y < 0 || y >= def.d) return -1;
        for (int i = 0; i < crates.length; i++) if (crates[i] == y * def.w + x) return i;
        return -1;
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        if (h != 0) return Use.PASS;
        int ci = crateAt(u, v);
        if (ci < 0) return Use.PASS;
        if (!c.ready(p)) return Use.CONSUME;
        int[] at = c.feetCell(p);
        int du = at == null ? 99 : u - at[0], dv = at == null ? 99 : v - at[1];
        if (Math.abs(du) + Math.abs(dv) != 1) {
            c.tell(p, Component.translatable(EnigmaRun.MSG + "crates.how").withColor(0x9A8AB0));
            return Use.CONSUME;
        }
        int nu = u + du, nv = v + dv;
        int x = nu - u0(), y = nv - 2;
        boolean inside = x >= 0 && x < def.w && y >= 0 && y < def.d;
        if (!inside || def.pillar[y * def.w + x] || crateAt(nu, nv) >= 0 || occupied(c, nu, nv)) {
            c.sound(pos, SoundEvents.STONE_HIT, 1.0F, 0.6F);
            c.tell(p, Component.translatable(EnigmaRun.MSG + "crates.blocked").withColor(0x9A8AB0));
            return Use.CONSUME;
        }
        c.set(u, v, 0, Blocks.AIR.defaultBlockState());
        crates[ci] = y * def.w + x;
        c.set(nu, nv, 0, crate());
        moves++;
        hintCrate = -1;
        paintGoals(c);
        c.sound(c.pos(nu, nv, 0), SoundEvents.PISTON_EXTEND, 0.6F, 1.3F);
        boolean all = true;
        for (int g : def.goals) {
            boolean on = false;
            for (int cr : crates) on |= cr == g;
            all &= on;
        }
        if (all) c.onSolved();
        return Use.CONSUME;
    }

    private static boolean occupied(EnigmaRun c, int u, int v) {
        BlockPos p = c.pos(u, v, 0);
        return !c.level().getEntitiesOfClass(LivingEntity.class, new AABB(p).setMaxY(p.getY() + 2), LivingEntity::isAlive).isEmpty();
    }

    @Override
    public void tick(EnigmaRun c, long time) {
        if (hintCrate < 0 || time > hintUntil || time % 6 != 0) return;
        int[] d = CratesLogic.dir(hintDir);
        int cu = u(hintCrate), cv = v(hintCrate, def.w);
        BlockPos from = c.pos(cu, cv, 1);
        BlockPos to = c.pos(cu + d[0], cv + d[1], 0);
        c.level().sendParticles(ParticleTypes.END_ROD, from.getX() + 0.5, from.getY() + 0.2, from.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.0);
        c.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, to.getX() + 0.5, to.getY() + 0.3, to.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
    }

    @Override
    public void describe(EnigmaRun c, ServerPlayer p) {
        p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "crates.status", moves, def.minPushes).withColor(0xB0B0C8));
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        int[] first = CratesLogic.firstPush(def.w, def.d, def.pillar, crates, def.goals);
        if (first == null) {
            c.broadcast(Component.translatable(EnigmaRun.MSG + "crates.stuck").withColor(0xD0A060));
            return true;
        }
        if (first[0] < 0) return false;
        hintCrate = first[0];
        hintDir = first[1];
        hintUntil = c.level().getGameTime() + 400;
        c.broadcast(Component.translatable(EnigmaRun.MSG + "crates.reveal", first[2]).withColor(0xC8A0FF));
        return true;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putIntArray("Crates", crates);
        tag.putInt("Moves", moves);
    }

    @Override
    public void load(CompoundTag tag) {
        int[] a = tag.getIntArray("Crates");
        if (a.length == def.start.length) crates = a;
        moves = tag.getInt("Moves");
    }
}
