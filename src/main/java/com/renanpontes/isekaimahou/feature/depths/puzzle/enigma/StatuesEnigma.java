package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.BinaryLogic;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic.StatuesLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * Estátuas da Verdade: 2–4 bustos no fundo, cada um com placa (I, II…) e uma alavanca na frente. Tocar numa estátua
 * mostra o que ela diz; tocar no Selo mostra a regra e todas as frases. Puxe a alavanca de quem guarda a Chave.
 * Alavanca errada = castigo (dano que nunca mata; criaturas nos níveis 2–3) e ela volta para baixo.
 * Pista: revela se uma estátua é Fiel ou Traidora (ou, se isso não for certo, onde a Chave NÃO está).
 */
public final class StatuesEnigma extends Enigma {
    private static final Block[] HEADS = {Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL, Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD};

    private final StatuesLogic def;
    private int revealedNature;
    private int excluded;

    public StatuesEnigma(int tier, long seed) {
        super(tier, seed);
        def = StatuesLogic.generate(seed, tier);
    }

    @Override
    public EnigmaType type() {
        return EnigmaType.STATUES;
    }

    private int col(int i) {
        return switch (def.n) {
            case 2 -> new int[] {2, 6}[i];
            case 3 -> new int[] {1, 4, 7}[i];
            default -> new int[] {1, 3, 5, 7}[i];
        };
    }

    private int statueAt(int u) {
        for (int i = 0; i < def.n; i++) if (col(i) == u) return i;
        return -1;
    }

    static Component name(int i) {
        return Component.literal(BinaryLogic.roman(i + 1));
    }

    @Override
    public void build(EnigmaRun c) {
        int rot = RotationSegment.convertToSegment(c.frame().forward().getOpposite());
        for (int i = 0; i < def.n; i++) {
            int u = col(i);
            c.set(u, 6, 0, EnigmaRun.pedestal());
            c.set(u, 6, 1, HEADS[i % HEADS.length].defaultBlockState().setValue(SkullBlock.ROTATION, rot));
            c.set(u, 7, 0, Blocks.POLISHED_ANDESITE.defaultBlockState());
            c.sign(u, 5, 0, true, Component.empty(), Component.translatable(EnigmaRun.NAME + "statue", name(i)), Component.empty(),
                    Component.translatable(EnigmaRun.NAME + "statue.touch"));
            c.set(u, 4, 0, lever(c, false));
        }
    }

    private static BlockState lever(EnigmaRun c, boolean on) {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, c.frame().forward()).setValue(LeverBlock.POWERED, on);
    }

    @Override
    public void reset(EnigmaRun c) {
        for (int i = 0; i < def.n; i++) c.set(col(i), 4, 0, lever(c, false));
    }

    @Override
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        int i = statueAt(u);
        if (i < 0) return Use.PASS;
        if ((v == 6 && (h == 0 || h == 1)) || (v == 5 && h == 0)) {
            p.sendSystemMessage(says(i));
            c.sound(pos, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.6F, 0.6F + 0.15F * i);
            return Use.CONSUME;
        }
        if (v != 4 || h != 0 || !(c.get(u, 4, 0).getBlock() instanceof LeverBlock)) return Use.PASS;
        if (!c.ready(p)) return Use.CONSUME;
        if (c.get(u, 4, 0).getValue(LeverBlock.POWERED)) return Use.ALLOW;
        if (i == def.key) {
            c.particles(c.pos(u, 6, 1), ParticleTypes.END_ROD, 20, 0.4);
            c.onSolved();
            return Use.ALLOW;
        }
        c.particles(c.pos(u, 6, 1), ParticleTypes.ANGRY_VILLAGER, 6, 0.3);
        c.punish(p);
        return Use.CONSUME;
    }

    private Component says(int i) {
        StatuesLogic.Statement s = def.says[i];
        String k = EnigmaRun.NAME + "say.";
        MutableComponent line = switch (s.kind()) {
            case KNIGHT -> Component.translatable(k + "knight", name(s.a()));
            case KNAVE -> Component.translatable(k + "knave", name(s.a()));
            case SAME -> s.a() == i ? Component.translatable(k + "same_me", name(s.b())) : Component.translatable(k + "same", name(s.a()), name(s.b()));
            case DIFF -> s.a() == i ? Component.translatable(k + "diff_me", name(s.b())) : Component.translatable(k + "diff", name(s.a()), name(s.b()));
            case KEY -> s.a() == i ? Component.translatable(k + "key_me") : Component.translatable(k + "key", name(s.a()));
            case NOTKEY -> s.a() == i ? Component.translatable(k + "notkey_me") : Component.translatable(k + "notkey", name(s.a()));
            case ONE_KNIGHT -> Component.translatable(k + "one_knight");
            case TWO_KNAVES -> Component.translatable(k + "two_knaves");
            case KEY_KNIGHT -> Component.translatable(k + "key_knight");
            case KEY_KNAVE -> Component.translatable(k + "key_knave");
            case KNAVE_OR_KEY -> Component.translatable(k + "knave_or_key", name(s.a()));
        };
        return Component.translatable(EnigmaRun.MSG + "statues.says", name(i), line).withColor(0xE8D8B0);
    }

    @Override
    public void describe(EnigmaRun c, ServerPlayer p) {
        for (int i = 0; i < def.n; i++) p.sendSystemMessage(says(i));
        for (int i = 0; i < def.n; i++) {
            if ((revealedNature >> i & 1) != 0) {
                p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "statues.nature", name(i),
                        Component.translatable(EnigmaRun.MSG + (def.certainNature(i) == 1 ? "statues.knight" : "statues.knave"))).withColor(0xC8A0FF));
            }
            if ((excluded >> i & 1) != 0) p.sendSystemMessage(Component.translatable(EnigmaRun.MSG + "statues.notkey", name(i)).withColor(0xC8A0FF));
        }
    }

    @Override
    public boolean reveal(EnigmaRun c) {
        for (int i = 0; i < def.n; i++) {
            int nat = def.certainNature(i);
            if ((revealedNature >> i & 1) != 0 || nat < 0) continue;
            revealedNature |= 1 << i;
            c.particles(c.pos(col(i), 6, 1), ParticleTypes.ENCHANT, 20, 0.4);
            c.broadcast(Component.translatable(EnigmaRun.MSG + "statues.nature", name(i),
                    Component.translatable(EnigmaRun.MSG + (nat == 1 ? "statues.knight" : "statues.knave"))).withColor(0xC8A0FF));
            return true;
        }
        for (int i = 0; i < def.n; i++) {
            if (i == def.key || (excluded >> i & 1) != 0) continue;
            excluded |= 1 << i;
            c.broadcast(Component.translatable(EnigmaRun.MSG + "statues.notkey", name(i)).withColor(0xC8A0FF));
            return true;
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("Nat", revealedNature);
        tag.putInt("Excl", excluded);
    }

    @Override
    public void load(CompoundTag tag) {
        revealedNature = tag.getInt("Nat");
        excluded = tag.getInt("Excl");
    }
}
