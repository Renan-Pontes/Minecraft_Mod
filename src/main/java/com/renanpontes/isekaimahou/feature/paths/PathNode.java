package com.renanpontes.isekaimahou.feature.paths;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Nós da árvore de cada elemento (ids do contrato, guardados em {@code MagicData.addNode}).
 * A ordem do enum é a ordem da coluna na aba e da verificação automática.
 * v0.7: o Despertar vem com o elemento; os outros 7 nós são liberados pela Provação do rank correspondente
 * ({@link Progression#TRIAL_NODES}: Aprendiz = rank I ... Arquimago = rank VII).
 */
public enum PathNode {
    AWAKENING("awakening", 1, false),
    APPRENTICE("apprentice", 2, false),
    ADEPT("adept", 3, false),
    FLUIDITY("fluidity", 0, false),
    MASTER("master", 4, true),
    CATACLYSM("cataclysm", 0, true),
    DOMAIN("domain", 0, true),
    ARCHMAGE("archmage", 5, true);

    public final String id;
    /** Tier dos glifos do elemento que o nó ensina (0 = nó especial: gatilho, ultimate, domínio). */
    public final int glyphTier;
    /** Nó "grande": anúncio e som mais fortes. */
    public final boolean major;

    PathNode(String id, int glyphTier, boolean major) {
        this.id = id;
        this.glyphTier = glyphTier;
        this.major = major;
    }

    /** Rank de Provação (1..7) que libera o nó; 0 para o Despertar. */
    public int trialRank() {
        return Progression.trialRankOfNode(id);
    }

    /** Chave de tradução: paths.isekai_mahou.node.<id> (+ ".desc"). */
    public String translationKey() {
        return KEY_PREFIX + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(translationKey());
    }

    public MutableComponent description() {
        return Component.translatable(translationKey() + ".desc");
    }

    private static final String KEY_PREFIX = "paths.isekai_mahou.node.";

    @Nullable
    public static PathNode byId(String id) {
        for (PathNode n : values()) if (n.id.equals(id)) return n;
        return null;
    }

    /** Nó liberado pela Provação do rank (1..7), ou null. */
    @Nullable
    public static PathNode byTrialRank(int rank) {
        if (rank < 1 || rank > Progression.TRIAL_NODES.size()) return null;
        return byId(Progression.TRIAL_NODES.get(rank - 1));
    }
}
