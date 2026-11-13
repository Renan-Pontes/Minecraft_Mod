package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.PathLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Caminho das Cores no piso (5 colunas × 6–7 fileiras de concreto). Siga o ciclo de cores, um passo por vez para uma
 * casa vizinha, da primeira à última fileira. Pular uma casa, pisar fora de ordem ou entrar pelo meio = volta para a
 * entrada (com um pouco de dano nos níveis 2–3). Nos níveis 1–2 a ordem fica exposta numa coluna de blocos à esquerda
 * (da frente para o fundo); no 3 só as primeiras casas do caminho têm velas. Cada jogador tem o próprio progresso.
 */
public final class PathEnigma extends Enigma {
    private static final Block[] PALETTE = {Blocks.RED_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.BLUE_CONCRETE,
            Blocks.LIME_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.ORANGE_CONCRETE};
    private static final String[] COLOR_KEYS = {"red", "yellow", "blue", "lime", "purple", "orange"};

    private final PathLogic def;
    /** Casas do caminho marcadas com vela (começo). */
    private int marked;
    /** Por jogador: {última casa, passo no ciclo}. */
    private final Map<UUID, int[]> progress = new HashMap<>();

    public PathEnigma(int tier, long seed) {
        super(tier, seed);
        def = PathLogic.generate(seed, tier);
        marked = tier >= 3 ? Math.min(def.k - 1, def.path.size() - 1) : 0;
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.PATH;
    }

    private static int u(int cell) {
        return 2 + cell % PathLogic.COLS;
    }

    private static int v(int cell) {
        return 1 + cell / PathLogic.COLS;
    }

    @Override
    public void build(EnigmaRun c) {
        for (int row = 0; row < def.rows; row++) {
            for (int col = 0; col < PathLogic.COLS; col++) {
                c.set(2 + col, 1 + row, -1, PALETTE[def.grid[row][col]].defaultBlockState());
            }
        }
        if (tier <= 2) {
            // legenda: a ordem do ciclo, da frente para o fundo, numa coluna à esquerda (e de novo à direita, longe do Selo)
            for (int i = 0; i < def.k; i++) {
                c.set(0, 1 + i, 0, PALETTE[def.cycle[i]].defaultBlockState());
                c.set(8, 1 + i, 0, PALETTE[def.cycle[i]].defaultBlockState());
            }
        }
        paintMarks(c);
    }

    private void paintMarks(EnigmaRun c) {
        for (int i = 0; i < marked && i < def.path.size(); i++) {
            int cell = def.path.get(i);
            BlockState candle = Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.min(4, i % 4 + 1))
                    .setValue(CandleBlock.LIT, true);
            c.set(u(cell), v(cell), 0, candle);
        }
    }

    @Override
    public void reset(EnigmaRun c) {
        progress.clear();
    }

    @Override
    public void tick(EnigmaRun c, long time) {
        for (ServerPlayer sp : c.playersNear(0)) {
            int[] at = c.feetCell(sp);
            if (at == null) continue;
            int col = at[0] - 2, row = at[1] - 1;
            int[] st = progress.get(sp.getUUID());
            boolean onGrid = col >= 0 && col < PathLogic.COLS && row >= 0 && row < def.rows;
            if (!onGrid) {
                if (st != null) progress.remove(sp.getUUID());   // saiu da grade: recomeça sem castigo
                continue;
            }
            int cell = row * PathLogic.COLS + col;
            if (st == null) {
                if (row == 0 && def.color(cell) == def.cycle[0]) {
                    progress.put(sp.getUUID(), new int[] {cell, 0});
                    c.sound(c.pos(at[0], at[1], 0), SoundEvents.AMETHYST_BLOCK_STEP, 1.0F, 1.2F);
                } else stumble(c, sp);
                continue;
            }
            if (st[0] == cell) continue;
            if (def.step(st[0], cell, st[1] + 1)) {
                st[0] = cell;
                st[1]++;
                c.sound(c.pos(at[0], at[1], 0), SoundEvents.AMETHYST_BLOCK_STEP, 1.0F, 1.0F + 0.05F * (st[1] % 8));
                if (row == def.rows - 1) {
                    progress.clear();
                    c.onSolved();
                    return;
                }
            } else {
                stumble(c, sp);
            }
        }
    }

    private void stumble(EnigmaRun c, ServerPlayer sp) {
        progress.remove(sp.getUUID());
        c.sendToStart(sp);
        if (tier >= 2 && !sp.getAbilities().instabuild) {
            float dmg = Math.min(tier, sp.getHealth() - 1.0F);
            if (dmg > 0) sp.hurt(c.level().damageSources().magic(), dmg);
        }
        c.sound(sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.7F);
        sp.displayClientMessage(Component.translatable(EnigmaRun.MSG + "path.fall").withColor(0xD06060), true);
    }

    @Override
    public void describe(EnigmaRun c, ServerPlayer p) {
        if (tier <= 2) {
            Component[] names = new Component[def.k];
            for (int i = 0; i < def.k; i++) names[i] = Component.translatable(EnigmaRun.MSG + "color." + COLOR_KEYS[def.cycle[i]]);
            Component chain = names[0];
            for (int i = 1; i < def.k; i++) chain = Component.empty().append(chain).append(" → ").append(names[i]);
            p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "path.order", chain).withColor(0xB0B0C8));
        }
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        if (marked >= def.path.size() - 1) return false;
        marked = Math.min(def.path.size() - 1, marked + 3);
        paintMarks(c);
        BlockPos at = c.pos(u(def.path.get(Math.max(0, marked - 1))), v(def.path.get(Math.max(0, marked - 1))), 0);
        c.particles(at, ParticleTypes.END_ROD, 10, 0.3);
        c.broadcast(Component.translatable(EnigmaRun.MSG + "path.reveal").withColor(0xC8A0FF));
        return true;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("Marked", marked);
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("Marked")) marked = tag.getInt("Marked");
    }
}
