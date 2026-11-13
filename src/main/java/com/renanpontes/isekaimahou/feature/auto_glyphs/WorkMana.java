package com.renanpontes.isekaimahou.feature.auto_glyphs;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Mana do TRABALHO dos glifos de fazenda (v1.5): a magia paga o custo dela normalmente e isso já cobre um pouco de
 * trabalho ("incluído"); o que passar disso é pago por "pontos de trabalho" (1 ponto = 1 de mana), proporcional aos
 * blocos e à dureza deles ({@link #blockPoints}).
 * <ul>
 *   <li>Pedestal: tira da mesma bolsa que paga a conjuração (Núcleo de Mana ligado + Baterias a até 8 blocos;
 *       {@link ManaNet#availableFor}/{@link ManaNet#drainFor}).</li>
 *   <li>Jogador: tira do mana dele (criativo: grátis).</li>
 *   <li>Mobs e outros conjuradores automáticos: grátis, mas só o trabalho "incluído" + o teto de blocos por vez.</li>
 * </ul>
 * Use: {@code open} → {@code pay} para cada bloco (falso = acabou o mana: pare) → {@code commit} no fim.
 */
public final class WorkMana {
    /** Pedestal que paga (mundo + posição); null = não é pedestal. */
    @Nullable private final ServerLevel level;
    @Nullable private final BlockPos pedestal;
    @Nullable private final ServerPlayer player;
    private final boolean free;
    private float included;
    private float available;
    private float spent;

    private WorkMana(@Nullable ServerLevel level, @Nullable BlockPos pedestal, @Nullable ServerPlayer player, boolean free,
                     float included, float available) {
        this.level = level;
        this.pedestal = pedestal;
        this.player = player;
        this.free = free;
        this.included = included;
        this.available = available;
    }

    /**
     * @param pedestal o pedestal que conjurou (null = não veio de pedestal)
     * @param included pontos de trabalho já cobertos pelo custo da magia
     */
    public static WorkMana open(SpellContext ctx, @Nullable RunicPedestalBlockEntity pedestal, float included) {
        if (pedestal != null) {
            BlockPos pos = pedestal.getBlockPos();
            return new WorkMana(ctx.level(), pos, null, false, included, ManaNet.availableFor(ctx.level(), pos));
        }
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null && !(sp instanceof FakePlayer)) {
            if (sp.getAbilities().instabuild) return new WorkMana(null, null, null, true, included, Float.MAX_VALUE);
            return new WorkMana(null, null, sp, false, included, MagicApi.get(sp).mana());
        }
        // Mobs/conjuradores automáticos sem pedestal: só o incluído
        return new WorkMana(null, null, null, false, included, 0F);
    }

    /** Pontos de trabalho para quebrar um bloco desta dureza (terra ~1, pedra ~3, ardósia ~5, obsidiana ~76). */
    public static float blockPoints(float hardness) {
        return 0.5F + Math.max(0F, hardness) * 1.5F;
    }

    /** Dá para pagar mais {@code points} pontos? (não paga) */
    public boolean canPay(float points) {
        return free || points <= included + available;
    }

    /** Paga {@code points}: primeiro do incluído, depois do mana. Falso = não deu (nada foi pago). */
    public boolean pay(float points) {
        if (points <= 0) return true;
        if (free) return true;
        if (points > included + available) return false;
        float fromIncluded = Math.min(included, points);
        included -= fromIncluded;
        float extra = points - fromIncluded;
        available -= extra;
        spent += extra;
        return true;
    }

    /** Mana extra gasta até agora (arredondada para cima). */
    public int spent() {
        return Mth.ceil(spent);
    }

    /** Cobra de verdade o mana extra gasto. Chame UMA vez no fim do trabalho. */
    public int commit() {
        int cost = spent();
        spent = 0;
        if (cost <= 0 || free) return 0;
        if (level != null && pedestal != null) {
            ManaNet.drainFor(level, pedestal, cost);
        } else if (player != null) {
            MagicApi.get(player).consumeMana(Math.min(cost, MagicApi.get(player).mana()));
        }
        return cost;
    }
}
