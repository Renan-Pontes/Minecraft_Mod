package com.renanpontes.isekaimahou.feature.journal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Cópia NO CLIENTE dos alvos dos objetivos atuais (o servidor procura a estrutura e manda pelo pacote
 * {@link JournalNetwork.Target}). Classe comum (sem nada de cliente): o handler do pacote só guarda os dados, e o
 * rastreador de objetivos lê daqui. v1.0: um alvo por trilha — história ({@link #MAIN}) e rota da classe ({@link #CLASS}).
 */
public final class JournalTargets {
    /** Trilha da história principal. */
    public static final int MAIN = 0;
    /** Trilha da rota da classe. */
    public static final int CLASS = 1;
    public static final int TRACKS = 2;

    public enum Status { FOUND, NOT_FOUND }

    /**
     * @param step   passo ("capitulo.passo") ao qual o alvo pertence — o rastreador só usa se ainda for o atual
     * @param yKnown false = altura desconhecida (chunk não carregado no servidor): o cliente usa a altura do jogador
     */
    public record Target(String step, Status status, BlockPos pos, boolean yKnown, ResourceKey<Level> dimension) {}

    private static final Target[] CURRENT = new Target[TRACKS];

    static synchronized void accept(int track, String step, boolean found, BlockPos pos, boolean yKnown, String dimension) {
        if (track < 0 || track >= TRACKS) return;
        if (step.isEmpty()) {
            CURRENT[track] = null;
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(dimension);
        if (dim == null) {
            CURRENT[track] = null;
            return;
        }
        CURRENT[track] = new Target(step, found ? Status.FOUND : Status.NOT_FOUND, pos, yKnown,
                ResourceKey.create(Registries.DIMENSION, dim));
    }

    /** Alvo do passo informado (em qualquer trilha), ou null (outro passo, sem busca ainda, ou nada a apontar). */
    @Nullable
    public static synchronized Target forStep(String stepUid) {
        for (Target t : CURRENT) {
            if (t != null && t.step().equals(stepUid)) return t;
        }
        return null;
    }

    public static synchronized void clear() {
        for (int i = 0; i < TRACKS; i++) CURRENT[i] = null;
    }

    private JournalTargets() {}
}
