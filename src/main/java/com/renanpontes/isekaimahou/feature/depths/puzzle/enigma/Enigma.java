package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Um Enigma de raciocínio. A DEFINIÇÃO (respostas, pistas, posições) vem só de {@code seed} + {@code tier} — é refeita
 * igual ao carregar o chunk; só o estado do jogo (luzes, caixas, dígitos digitados…) vai para o NBT do Selo.
 * Tudo roda no servidor; qualquer jogador por perto pode ajudar (grupo).
 */
public abstract class Enigma {
    /** Resposta do enigma a um clique de botão direito. */
    public enum Use {
        /** Não é peça deste enigma. */
        PASS,
        /** Tratado: cancela o comportamento normal do bloco. */
        CONSUME,
        /** Tratado, mas o bloco segue normal (alavanca, botão). */
        ALLOW
    }

    protected final int tier;
    protected final long seed;

    protected Enigma(int tier, long seed) {
        this.tier = tier;
        this.seed = seed;
    }

    public abstract EnigmaType type();

    public int tier() {
        return tier;
    }

    /** Monta as peças pela primeira vez (área já limpa). */
    public abstract void build(EnigmaRun c);

    /** Volta tudo ao começo (botão de recomeçar; nunca deixa ninguém preso). */
    public abstract void reset(EnigmaRun c);

    /** Clique com botão direito numa posição da área (u, v, h do {@link Frame}). */
    public Use use(EnigmaRun c, ServerPlayer p, BlockPos pos, int u, int v, int h) {
        return Use.PASS;
    }

    /** A cada 2 ticks, com gente por perto e o enigma ativo. */
    public void tick(EnigmaRun c, long time) {}

    /** Informação extra ao tocar no Selo (frases das estátuas, pistas…). */
    public void describe(EnigmaRun c, ServerPlayer p) {}

    /** Pista (paga ou ganha do enigma anterior): revela algo a quem está por perto. false = nada mais a revelar. */
    public boolean reveal(EnigmaRun c) {
        return false;
    }

    public void save(CompoundTag tag) {}

    public void load(CompoundTag tag) {}
}
