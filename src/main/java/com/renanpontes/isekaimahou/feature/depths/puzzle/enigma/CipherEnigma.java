package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneLitBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.RuneStoneBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.CipherLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

/**
 * Cifra Rúnica: Pedras Rúnicas na frente (cada uma com a sua runa numa placa), a Fechadura no meio (pede um número)
 * e Tabuletas (estantes com placa) espalhadas pelo fundo e pelos lados, cada uma com UMA pista. Toque as pedras na
 * ordem que escreve o número. Errou o número inteiro = castigo e recomeça a digitação.
 */
public final class CipherEnigma extends Enigma {
    private static final int[][] POSTS = {{0, 4}, {8, 4}, {0, 7}, {2, 7}, {4, 7}, {6, 7}, {8, 7}, {0, 6}, {8, 6}};

    private final CipherLogic def;
    private final int[][] posts;
    private final List<Integer> entered = new ArrayList<>();
    private int revealed;
    private final Map<BlockPos, Long> flash = new HashMap<>();

    public CipherEnigma(int tier, long seed) {
        super(tier, seed);
        def = CipherLogic.generate(seed, tier);
        List<int[]> spots = new ArrayList<>(List.of(POSTS));
        java.util.Collections.shuffle(spots, new Random(seed ^ 0xC1F3L));
        posts = spots.subList(0, Math.min(spots.size(), def.clues.size())).toArray(new int[0][]);
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.CIPHER;
    }

    private int stoneCol(int i) {
        return switch (def.runes.length) {
            case 3 -> new int[] {2, 4, 6}[i];
            case 4 -> new int[] {1, 3, 5, 7}[i];
            default -> 2 + i;
        };
    }

    private int stoneAt(int u) {
        for (int i = 0; i < def.runes.length; i++) if (stoneCol(i) == u) return i;
        return -1;
    }

    private String codeText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < def.code.length; i++) b.append(i == 0 ? "" : " ").append(def.code[i]);
        return b.toString();
    }

    @Override
    public void build(EnigmaRun c) {
        for (int i = 0; i < def.runes.length; i++) {
            c.set(stoneCol(i), 2, 0, PuzzleBlocks.STONE.get().defaultBlockState().setValue(RuneStoneBlock.RUNE, i % 4));
            c.sign(stoneCol(i), 2, 1, false, Component.empty(), EnigmaRun.lit(def.runes[i]), Component.empty(), Component.empty());
        }
        c.set(4, 4, 0, Blocks.LODESTONE.defaultBlockState());
        c.sign(4, 4, 1, false, Component.translatable(EnigmaRun.NAME + "cipher.lock"), EnigmaRun.lit(codeText()), Component.empty(),
                Component.translatable(EnigmaRun.NAME + "cipher.lock2"));
        for (int i = 0; i < posts.length; i++) {
            c.set(posts[i][0], posts[i][1], 0, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            c.sign(posts[i][0], posts[i][1], 1, false, Component.translatable(EnigmaRun.NAME + "cipher.tablet"),
                    EnigmaRun.lit(def.clues.get(i).text(def.runes)), Component.empty(), Component.empty());
        }
    }

    @Override
    public void reset(EnigmaRun c) {
        entered.clear();
        for (int i = 0; i < def.runes.length; i++) setLit(c, i, false);
    }

    private void setLit(EnigmaRun c, int i, boolean on) {
        var st = c.get(stoneCol(i), 2, 0);
        if (st.hasProperty(RuneLitBlock.LIT) && st.getValue(RuneLitBlock.LIT) != on) c.set(stoneCol(i), 2, 0, st.setValue(RuneLitBlock.LIT, on));
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        if (v == 2 && (h == 0 || h == 1)) {
            int i = stoneAt(u);
            if (i < 0) return Use.PASS;
            if (!c.ready(p)) return Use.CONSUME;
            press(c, p, i);
            return Use.CONSUME;
        }
        if (u == 4 && v == 4 && (h == 0 || h == 1)) {
            p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "cipher.code", codeText()).withColor(0xE8D8B0));
            return Use.CONSUME;
        }
        for (int i = 0; i < posts.length; i++) {
            if (posts[i][0] == u && posts[i][1] == v && (h == 0 || h == 1)) {
                p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "cipher.clue", def.clues.get(i).text(def.runes)).withColor(0xE8D8B0));
                return Use.CONSUME;
            }
        }
        return Use.PASS;
    }

    private void press(EnigmaRun c, ServerPlayer p, int i) {
        entered.add(i);
        setLit(c, i, true);
        flash.put(c.pos(stoneCol(i), 2, 0), c.level().getGameTime() + 8);
        c.sound(c.pos(stoneCol(i), 2, 0), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3F, 0.6F + 0.12F * i);
        StringBuilder typed = new StringBuilder();
        for (int k = 0; k < def.code.length; k++) typed.append(k < entered.size() ? def.runes[entered.get(k)] : "_").append(' ');
        for (ServerPlayer sp : c.playersNear(4)) sp.displayClientMessage(Component.translatable(EnigmaRun.MSG + "cipher.typed", typed.toString().trim()).withColor(0xC8B8FF), true);
        if (entered.size() < def.code.length) return;
        boolean ok = true;
        for (int k = 0; k < def.code.length; k++) ok &= def.values[entered.get(k)] == def.code[k];
        entered.clear();
        if (ok) c.onSolved();
        else c.punish(p);
    }

    @Override
    public void tick(EnigmaRun c, long time) {
        if (flash.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Long>> it = flash.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (time < e.getValue()) continue;
            var st = c.level().getBlockState(e.getKey());
            if (st.hasProperty(RuneLitBlock.LIT) && st.getValue(RuneLitBlock.LIT)) c.level().setBlock(e.getKey(), st.setValue(RuneLitBlock.LIT, false), 3);
            it.remove();
        }
    }

    @Override
    public void describe(EnigmaRun c, ServerPlayer p) {
        p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "cipher.code", codeText()).withColor(0xE8D8B0));
        p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "cipher.tablets", posts.length).withColor(0xB0B0C8));
        for (int i = 0; i < def.runes.length; i++) {
            if ((revealed >> i & 1) != 0) p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "cipher.value", def.runes[i], def.values[i]).withColor(0xC8A0FF));
        }
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        for (int i = 0; i < def.runes.length; i++) {
            if ((revealed >> i & 1) != 0) continue;
            revealed |= 1 << i;
            c.particles(c.pos(stoneCol(i), 2, 1), ParticleTypes.ENCHANT, 20, 0.4);
            c.broadcast(Component.translatable(EnigmaRun.MSG + "cipher.value", def.runes[i], def.values[i]).withColor(0xC8A0FF));
            return true;
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("Rev", revealed);
        tag.putIntArray("Typed", entered.stream().mapToInt(Integer::intValue).toArray());
    }

    @Override
    public void load(CompoundTag tag) {
        revealed = tag.getInt("Rev");
        entered.clear();
        for (int x : tag.getIntArray("Typed")) if (x >= 0 && x < def.runes.length) entered.add(x);
    }
}
