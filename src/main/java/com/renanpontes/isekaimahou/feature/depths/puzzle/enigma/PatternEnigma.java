package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneLitBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.PatternLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

/**
 * Fileiras do Oráculo no piso: as primeiras fileiras (Lâmpadas, da frente para o fundo) seguem uma regra; a última
 * (Placas Rúnicas, depois de uma fileira vazia) é do jogador: toque para acender/apagar e aperte o botão do pedestal.
 * Erro = castigo. Pista: uma casa da resposta se acerta sozinha.
 */
public final class PatternEnigma extends Enigma {
    private final PatternLogic def;
    private boolean[] answer = new boolean[PatternLogic.W];
    private int revealed;

    public PatternEnigma(int tier, long seed) {
        super(tier, seed);
        def = PatternLogic.generate(seed, tier);
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.PATTERN;
    }

    private int answerRow() {
        return 2 + def.shown.length + 1;
    }

    @Override
    public void build(EnigmaRun c) {
        for (int r = 0; r < def.shown.length; r++) {
            for (int i = 0; i < PatternLogic.W; i++) {
                boolean on = (def.shown[r] >> i & 1) != 0;
                c.set(1 + i, 2 + r, -1, PuzzleBlocks.LAMP.get().defaultBlockState().setValue(RuneLitBlock.LIT, on));
            }
        }
        for (int i = 0; i < PatternLogic.W; i++) paint(c, i);
        c.set(8, answerRow(), 0, EnigmaRun.pedestal());
        c.set(8, answerRow(), 1, c.floorButton());
        c.set(0, answerRow(), 0, Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void paint(EnigmaRun c, int i) {
        c.set(1 + i, answerRow(), -1, PuzzleBlocks.TILE.get().defaultBlockState().setValue(RuneLitBlock.LIT, answer[i]));
    }

    @Override
    public void reset(EnigmaRun c) {
        for (int i = 0; i < answer.length; i++) {
            if ((revealed >> i & 1) == 0) answer[i] = false;
            paint(c, i);
        }
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        if (u == 8 && v == answerRow() && h == 1) {
            if (!c.ready(p)) return Use.CONSUME;
            judge(c, p);
            return Use.ALLOW;
        }
        if (h != -1 || v != answerRow() || u < 1 || u > PatternLogic.W) return Use.PASS;
        if (!c.ready(p)) return Use.CONSUME;
        int i = u - 1;
        if ((revealed >> i & 1) != 0) {
            c.tell(p, Component.translatable(EnigmaRun.MSG + "pattern.fixed").withColor(0x9A8AB0));
            return Use.CONSUME;
        }
        answer[i] = !answer[i];
        paint(c, i);
        c.sound(pos, SoundEvents.AMETHYST_BLOCK_STEP, 1.0F, answer[i] ? 1.4F : 0.8F);
        return Use.CONSUME;
    }

    private void judge(EnigmaRun c, ServerPlayer p) {
        int bits = 0;
        for (int i = 0; i < answer.length; i++) if (answer[i]) bits |= 1 << i;
        if (bits == def.answer) {
            c.onSolved();
            return;
        }
        c.punish(p);
        reset(c);
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        for (int i = 0; i < PatternLogic.W; i++) {
            if ((revealed >> i & 1) != 0) continue;
            boolean want = (def.answer >> i & 1) != 0;
            if (answer[i] == want && hasWrongOther(i)) continue;   // prefere revelar uma casa que está errada
            answer[i] = want;
            revealed |= 1 << i;
            paint(c, i);
            BlockPos at = c.pos(1 + i, answerRow(), 0);
            c.particles(at, ParticleTypes.END_ROD, 12, 0.3);
            c.broadcast(Component.translatable(EnigmaRun.MSG + "pattern.reveal", i + 1,
                    Component.translatable(EnigmaRun.MSG + (want ? "on" : "off"))).withColor(0xC8A0FF));
            return true;
        }
        return false;
    }

    private boolean hasWrongOther(int skip) {
        for (int i = 0; i < PatternLogic.W; i++) {
            if (i == skip || (revealed >> i & 1) != 0) continue;
            if (answer[i] != ((def.answer >> i & 1) != 0)) return true;
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("Ans", LightsEnigma.bits(answer));
        tag.putInt("Rev", revealed);
    }

    @Override
    public void load(CompoundTag tag) {
        answer = LightsEnigma.unbits(tag.getLong("Ans"), PatternLogic.W);
        revealed = tag.getInt("Rev");
    }
}
