package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneLitBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.LightsLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Lights Out no chão: grade n×n de Lâmpadas Rúnicas no piso (3×3, 4×4, 5×5). Tocar numa inverte ela e as vizinhas;
 * acenda todas. Pista: uma casa que faz parte de uma solução brilha.
 */
public final class LightsEnigma extends Enigma {
    private final LightsLogic def;
    private boolean[] lit;
    private boolean[] parity;
    private int hintCell = -1;
    private long hintUntil;

    public LightsEnigma(int tier, long seed) {
        super(tier, seed);
        def = LightsLogic.generate(seed, tier);
        lit = def.start.clone();
        parity = new boolean[def.n * def.n];
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.LIGHTS;
    }

    private int off() {
        return 4 - def.n / 2;
    }

    private void paint(EnigmaRun c, int idx) {
        c.set(off() + idx % def.n, 2 + idx / def.n, -1, PuzzleBlocks.LAMP.get().defaultBlockState().setValue(RuneLitBlock.LIT, lit[idx]));
    }

    @Override
    public void build(EnigmaRun c) {
        for (int i = 0; i < lit.length; i++) paint(c, i);
    }

    @Override
    public void reset(EnigmaRun c) {
        lit = def.start.clone();
        parity = new boolean[lit.length];
        hintCell = -1;
        build(c);
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        int col = u - off(), row = v - 2;
        if (h != -1 || col < 0 || col >= def.n || row < 0 || row >= def.n) return Use.PASS;
        if (!c.ready(p)) return Use.CONSUME;
        int idx = row * def.n + col;
        int n = def.n;
        LightsLogic.toggle(lit, n, idx);
        parity[idx] = !parity[idx];
        int[] touched = {idx, idx - n, idx + n, col > 0 ? idx - 1 : -1, col < n - 1 ? idx + 1 : -1};
        for (int t : touched) if (t >= 0 && t < lit.length) paint(c, t);
        if (idx == hintCell) hintCell = -1;
        boolean all = LightsLogic.allLit(lit);
        c.sound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, all ? 1.7F : 0.8F + 0.03F * count());
        if (all) c.onSolved();
        return Use.CONSUME;
    }

    private int count() {
        int k = 0;
        for (boolean b : lit) if (b) k++;
        return k;
    }

    @Override
    public void tick(EnigmaRun c, long time) {
        if (hintCell >= 0 && time < hintUntil && time % 6 == 0) {
            BlockPos p = c.pos(off() + hintCell % def.n, 2 + hintCell / def.n, 0);
            c.level().sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.01);
        }
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        // solução a partir de agora = solução original XOR o que já foi apertado (cada toque é a própria inversa)
        for (int i = 0; i < lit.length; i++) {
            if (def.solution[i] != parity[i]) {
                hintCell = i;
                hintUntil = c.level().getGameTime() + 400;
                c.broadcast(Component.translatable(EnigmaRun.MSG + "lights.reveal").withColor(0xC8A0FF));
                return true;
            }
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("Lit", bits(lit));
        tag.putLong("Par", bits(parity));
    }

    @Override
    public void load(CompoundTag tag) {
        if (!tag.contains("Lit")) return;
        lit = unbits(tag.getLong("Lit"), lit.length);
        parity = unbits(tag.getLong("Par"), lit.length);
    }

    static long bits(boolean[] b) {
        long x = 0;
        for (int i = 0; i < b.length; i++) if (b[i]) x |= 1L << i;
        return x;
    }

    static boolean[] unbits(long x, int n) {
        boolean[] b = new boolean[n];
        for (int i = 0; i < n; i++) b[i] = (x >> i & 1L) != 0;
        return b;
    }
}
