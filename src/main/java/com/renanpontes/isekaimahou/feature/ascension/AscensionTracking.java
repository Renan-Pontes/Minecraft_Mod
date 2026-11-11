package com.renanpontes.isekaimahou.feature.ascension;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Último Núcleo de Ascensão visto pelo jogador NESTE cliente (para a setinha do rastreador de objetivos) e o altar
 * PÚBLICO mais próximo que o servidor informou ({@link AscensionNetwork.AltarHint}).
 * Código comum sem classes de cliente: o bloco chama {@link #remember} só quando {@code level.isClientSide}
 * (clique no Núcleo, partículas do Núcleo por perto) e o pacote só chega no cliente — no servidor nada aqui é tocado.
 * Um Núcleo quebrado é esquecido pelo próprio rastreador (o chunk carregado não tem mais o bloco).
 */
public final class AscensionTracking {
    private static ResourceKey<Level> dimension;
    private static BlockPos core;

    /** Altar público mais próximo (do servidor). */
    public record PublicAltar(ResourceKey<Level> dim, BlockPos pos, Component name, boolean exact) {}

    private static volatile PublicAltar publicAltar;

    /** Clique no Núcleo: passa a ser o Núcleo seguido. */
    public static void remember(Level level, BlockPos pos) {
        if (!level.isClientSide) return;
        dimension = level.dimension();
        core = pos.immutable();
    }

    /** Núcleo visto de perto: só vira o seguido se ainda não há um nesta dimensão. */
    public static void noticed(Level level, BlockPos pos) {
        if (!level.isClientSide) return;
        if (core == null || dimension != level.dimension()) remember(level, pos);
    }

    public static void forget() {
        dimension = null;
        core = null;
        publicAltar = null;
    }

    /** Esquece só o Núcleo seguido (quebrado), mantendo o altar público informado pelo servidor. */
    public static void forgetCore() {
        dimension = null;
        core = null;
    }

    @Nullable
    public static BlockPos core() {
        return core;
    }

    @Nullable
    public static ResourceKey<Level> dimension() {
        return dimension;
    }

    @Nullable
    public static PublicAltar publicAltar() {
        return publicAltar;
    }

    /** Pacote do servidor (lado cliente). */
    static void acceptAltar(AscensionNetwork.AltarHint hint) {
        if (!hint.present()) {
            publicAltar = null;
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(hint.dim());
        publicAltar = dim == null ? null
                : new PublicAltar(ResourceKey.create(Registries.DIMENSION, dim), hint.pos(), hint.name(), hint.exact());
    }

    private AscensionTracking() {}
}
