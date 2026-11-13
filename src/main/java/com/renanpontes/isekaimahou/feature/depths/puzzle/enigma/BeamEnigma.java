package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.depths.puzzle.LightMirrorBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneLitBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.BeamLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feixe dos Espelhos: o Emissor (lanterna do mar, na frente) solta um feixe para o fundo; Espelhos de Luz (girar com
 * o botão direito) desviam, pilares bloqueiam. Leve o feixe à Lâmpada-Cristal. Alguns espelhos são de enfeite.
 * O estado fica nos próprios blocos (o NBT não precisa guardar nada). Pista: um espelho do caminho se acerta sozinho.
 */
public final class BeamEnigma extends Enigma {
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.45F), 0.9F);
    private final BeamLogic def;

    public BeamEnigma(int tier, long seed) {
        super(tier, seed);
        def = BeamLogic.generate(seed, tier);
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.BEAM;
    }

    private static int u(int cell) {
        return 1 + cell % BeamLogic.W;
    }

    private static int v(int cell) {
        return 2 + cell / BeamLogic.W;
    }

    /** "flipped" do Espelho de Luz que faz o papel do tipo {@code type} (0/1) da lógica, neste sistema de coordenadas. */
    private static boolean flipped(EnigmaRun c, int type) {
        Frame f = c.frame();
        boolean t0 = LightMirrorBlock.reflect(f.world(0), false) != f.world(1);
        return type == 0 ? t0 : !t0;
    }

    private BlockState mirror(EnigmaRun c, int type) {
        return PuzzleBlocks.MIRROR.get().defaultBlockState().setValue(LightMirrorBlock.FLIPPED, flipped(c, type));
    }

    @Override
    public void build(EnigmaRun c) {
        c.set(u(def.emitterX), 1, 0, Blocks.SEA_LANTERN.defaultBlockState());
        c.set(u(def.target), v(def.target), 0, PuzzleBlocks.LAMP.get().defaultBlockState());
        for (int o : def.obstacles) {
            c.set(u(o), v(o), 0, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            c.set(u(o), v(o), 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        reset(c);
    }

    @Override
    public void reset(EnigmaRun c) {
        for (int i = 0; i < def.mirrors.length; i++) c.set(u(def.mirrors[i]), v(def.mirrors[i]), 0, mirror(c, def.scrambled[i]));
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        if (h != 0) return Use.PASS;
        for (int m : def.mirrors) {
            if (u(m) == u && v(m) == v) return Use.ALLOW;      // o próprio espelho gira (LightMirrorBlock)
        }
        return Use.PASS;
    }

    @Override
    public void tick(EnigmaRun c, long time) {
        if (time % 4 != 0) return;
        ServerLevel sl = c.level();
        Frame f = c.frame();
        boolean draw = time % 8 == 0;
        BlockPos.MutableBlockPos p = c.pos(u(def.emitterX), 1, 0).mutable();
        Direction dir = f.forward();
        BlockPos target = c.pos(u(def.target), v(def.target), 0);
        for (int step = 0; step < 80; step++) {
            p.move(dir);
            if (!f.covers(p)) break;
            if (p.equals(target)) {
                BlockState s = sl.getBlockState(target);
                if (s.hasProperty(RuneLitBlock.LIT)) sl.setBlock(target, s.setValue(RuneLitBlock.LIT, true), 3);
                c.sound(target, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
                c.onSolved();
                return;
            }
            BlockState s = sl.getBlockState(p);
            if (s.is(PuzzleBlocks.MIRROR.get())) {
                dir = LightMirrorBlock.reflect(dir, s.getValue(LightMirrorBlock.FLIPPED));
                if (draw) sl.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.0);
                continue;
            }
            if (!s.getCollisionShape(sl, p).isEmpty()) break;
            if (draw) sl.sendParticles(BEAM, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.18, 0.03, 0.18, 0.0);
        }
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        for (int i = 0; i < def.pathMirrors; i++) {
            int m = def.mirrors[i];
            BlockState want = mirror(c, def.solution[i]);
            BlockState now = c.get(u(m), v(m), 0);
            if (now.is(PuzzleBlocks.MIRROR.get()) && now.getValue(LightMirrorBlock.FLIPPED) == want.getValue(LightMirrorBlock.FLIPPED)) continue;
            c.set(u(m), v(m), 0, want);
            c.particles(c.pos(u(m), v(m), 0), ParticleTypes.END_ROD, 14, 0.3);
            c.broadcast(Component.translatable(EnigmaRun.MSG + "beam.reveal").withColor(0xC8A0FF));
            return true;
        }
        return false;
    }
}
