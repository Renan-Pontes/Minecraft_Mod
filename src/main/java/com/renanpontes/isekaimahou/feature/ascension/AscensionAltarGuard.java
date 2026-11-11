package com.renanpontes.isekaimahou.feature.ascension;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Proteção dos altares PÚBLICOS: o Núcleo com {@code PublicAltar:1b} e as peças do padrão em volta dele (pedestais e
 * Pedras Rúnicas nas posições do 9×9) não quebram em sobrevivência, nem por explosões, Wither ou magias. No criativo
 * quebram normalmente (para quem constrói o mundo). Funciona nos dois lados (o flag vai junto no pacote do Núcleo).
 */
public final class AscensionAltarGuard {
    /** Resistência a explosões das peças protegidas (igual à da rocha-mãe). */
    public static final float BLAST_PROOF = 3_600_000F;

    /** O bloco em {@code pos} é um Núcleo de altar público? */
    public static boolean isPublicCore(@Nullable BlockGetter level, BlockPos pos) {
        if (level == null) return false;
        // Nunca carrega chunk só para conferir (explosões e mineração na borda da área carregada).
        if (level instanceof Level l && !l.isLoaded(pos)) return false;
        return level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be && be.isPublic();
    }

    /** Núcleo público dono da peça em {@code pos} (o próprio Núcleo, ou um a uma posição do padrão), ou null. */
    @Nullable
    public static BlockPos publicCoreOf(@Nullable BlockGetter level, BlockPos pos) {
        if (level == null) return null;
        BlockState st = level.getBlockState(pos);
        if (st.is(AscensionRegistry.CORE.get())) return isPublicCore(level, pos) ? pos : null;
        AscensionPattern.Part part;
        if (st.is(AscensionRegistry.PEDESTAL.get())) part = AscensionPattern.Part.PEDESTAL;
        else if (st.is(AscensionRegistry.RUNE_STONE.get())) part = AscensionPattern.Part.RUNE_STONE;
        else return null;
        for (AscensionPattern.Slot s : AscensionPattern.fullLayout()) {
            if (s.part() != part) continue;
            BlockPos core = pos.offset(-s.dx(), 0, -s.dz());
            if (isPublicCore(level, core)) return core;
        }
        return null;
    }

    public static boolean isPublicPiece(@Nullable BlockGetter level, BlockPos pos) {
        return publicCoreOf(level, pos) != null;
    }

    /** Este jogador NÃO pode quebrar a peça (sobrevivência/aventura numa peça de altar público). */
    public static boolean lockedFor(@Nullable Player player, @Nullable BlockGetter level, BlockPos pos) {
        if (player != null && player.getAbilities().instabuild) return false;
        return isPublicPiece(level, pos);
    }

    /** Wither, Dragão e afins não destroem peças de altar público. */
    public static boolean entityMayDestroy(@Nullable Entity entity, BlockGetter level, BlockPos pos) {
        return !isPublicPiece(level, pos);
    }

    private AscensionAltarGuard() {}
}
