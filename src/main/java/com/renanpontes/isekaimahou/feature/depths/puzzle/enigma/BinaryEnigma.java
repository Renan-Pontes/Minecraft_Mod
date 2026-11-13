package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.BinaryLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Alavancas do Tesoureiro: uma fileira de alavancas no chão; o Selo (placa no pedestal do meio) pede um número em
 * algarismos romanos. Levante as alavancas cujos pesos somam o número e aperte o botão do pedestal da direita.
 * Nível 1 mostra o peso de cada alavanca numa plaquinha atrás dela. Erro = castigo e as alavancas descem.
 */
public final class BinaryEnigma extends Enigma {
    private final BinaryLogic def;
    private int revealed;

    public BinaryEnigma(int tier, long seed) {
        super(tier, seed);
        def = BinaryLogic.generate(seed, tier);
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.BINARY;
    }

    private int col(int i) {
        return (9 - def.bits) / 2 + i;
    }

    private BlockState lever(EnigmaRun c, boolean on) {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, c.frame().forward()).setValue(LeverBlock.POWERED, on);
    }

    @Override
    public void build(EnigmaRun c) {
        for (int i = 0; i < def.bits; i++) {
            c.set(col(i), 3, 0, lever(c, false));
            c.set(col(i), 4, -1, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            if (tier <= 1) c.sign(col(i), 4, 0, false, Component.empty(), EnigmaRun.lit(BinaryLogic.roman(def.weight(i))), Component.empty(), Component.empty());
        }
        c.set(4, 6, 0, EnigmaRun.pedestal());
        String[] parts = def.display.split(" (?=[+−×])");
        c.sign(4, 6, 1, false, Component.translatable(EnigmaRun.NAME + "binary.seal"), EnigmaRun.lit(parts[0]),
                EnigmaRun.lit(parts.length > 1 ? parts[1] : ""), Component.empty());
        c.set(8, 3, 0, EnigmaRun.pedestal());
        c.set(8, 3, 1, c.floorButton());
    }

    @Override
    public void reset(EnigmaRun c) {
        for (int i = 0; i < def.bits; i++) {
            boolean keep = (revealed >> i & 1) != 0;
            c.set(col(i), 3, 0, lever(c, keep && def.up(i)));
        }
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        if (u == 8 && v == 3 && h == 1) {
            if (!c.ready(p)) return Use.CONSUME;
            judge(c, p);
            return Use.ALLOW;
        }
        if (v == 3 && h == 0 && u >= col(0) && u < col(0) + def.bits && c.get(u, v, 0).getBlock() instanceof LeverBlock) {
            if (!c.ready(p)) return Use.CONSUME;
            return Use.ALLOW;
        }
        if (u == 4 && v == 6 && h >= 0) {
            p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "binary.target", def.display).withColor(0xE8D8B0));
            return Use.CONSUME;
        }
        return Use.PASS;
    }

    private void judge(EnigmaRun c, ServerPlayer p) {
        int sum = 0;
        for (int i = 0; i < def.bits; i++) {
            BlockState s = c.get(col(i), 3, 0);
            if (s.getBlock() instanceof LeverBlock && s.getValue(LeverBlock.POWERED)) sum += def.weight(i);
        }
        if (sum == def.target) {
            c.onSolved();
            return;
        }
        c.punish(p);
        reset(c);
    }

    @Override
    public void describe(EnigmaRun c, ServerPlayer p) {
        p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "binary.target", def.display).withColor(0xE8D8B0));
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        for (int i = 0; i < def.bits; i++) {
            if ((revealed >> i & 1) != 0) continue;
            revealed |= 1 << i;
            c.set(col(i), 3, 0, lever(c, def.up(i)));
            c.particles(c.pos(col(i), 3, 0), ParticleTypes.END_ROD, 10, 0.3);
            c.broadcast(Component.translatable(EnigmaRun.MSG + "binary.reveal", i + 1,
                    Component.translatable(EnigmaRun.MSG + (def.up(i) ? "up" : "down"))).withColor(0xC8A0FF));
            return true;
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("Rev", revealed);
    }

    @Override
    public void load(CompoundTag tag) {
        revealed = tag.getInt("Rev");
    }
}
