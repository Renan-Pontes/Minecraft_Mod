package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Pacto pendente do jogador (attachment "warlock_pact"). O Patrono preso no círculo guarda a mesma
 * informação; aqui fica o espelho do lado do jogador para acompanhar o progresso (abates, almas) mesmo
 * longe do círculo, avisar falhas e mostrar o HUD. Também guarda o sacrifício de corações em curso.
 */
public class PactState {
    public static final Codec<PactState> CODEC = CompoundTag.CODEC.xmap(PactState::fromTag, PactState::toTag);

    /** Patrono do pacto pendente (null = nenhum). */
    public UUID patron;
    public PatronKind kind = PatronKind.EMBER_LORD;
    public int pactNumber;
    public PatronDemand demand;
    /** Tempo de jogo (overworld) em que o Patrono se liberta se o tributo não for entregue. */
    public long deadline;
    /** Fim da janela dos tributos com contador (abates/almas). */
    public long windowEnd;
    /** Valor do contador quando o pacto foi aceito (progresso = atual - base). */
    public int baseline;
    public boolean fulfilled;
    public boolean failed;
    /** Altar do círculo (para lembrar o jogador de onde voltar). */
    public BlockPos altar = BlockPos.ZERO;

    /** Sacrifício de corações: até quando dura (tempo de jogo) e quantos corações. */
    public long sacrificeUntil;
    public int sacrificeHearts;

    /**
     * Última invocação (dia do overworld e tempo de jogo). Os Patronos atendem uma vez por noite:
     * sem isso, quem já tem os itens selaria os 10 pactos numa noite só.
     */
    public long lastRitualDay = -1;
    public long lastRitualTime = -1;

    /** Último progresso mostrado (só em memória: evita mensagens repetidas). */
    public transient int lastShownProgress = -1;

    public boolean active() {
        return patron != null && demand != null;
    }

    public void clearPact() {
        patron = null;
        demand = null;
        deadline = 0;
        windowEnd = 0;
        baseline = 0;
        fulfilled = false;
        failed = false;
        lastShownProgress = -1;
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        if (active()) {
            t.putUUID("patron", patron);
            t.putString("kind", kind.id);
            t.putInt("pact", pactNumber);
            t.put("demand", demand.save());
            t.putLong("deadline", deadline);
            t.putLong("window", windowEnd);
            t.putInt("baseline", baseline);
            t.putBoolean("fulfilled", fulfilled);
            t.putBoolean("failed", failed);
            t.putLong("altar", altar.asLong());
        }
        t.putLong("sacrifice_until", sacrificeUntil);
        t.putInt("sacrifice_hearts", sacrificeHearts);
        t.putLong("last_ritual_day", lastRitualDay);
        t.putLong("last_ritual_time", lastRitualTime);
        return t;
    }

    public static PactState fromTag(CompoundTag t) {
        PactState s = new PactState();
        if (t.hasUUID("patron")) {
            s.demand = PatronDemand.load(t.getCompound("demand"));
            if (s.demand != null) {
                s.patron = t.getUUID("patron");
                s.kind = PatronKind.byId(t.getString("kind"));
                s.pactNumber = t.getInt("pact");
                s.deadline = t.getLong("deadline");
                s.windowEnd = t.getLong("window");
                s.baseline = t.getInt("baseline");
                s.fulfilled = t.getBoolean("fulfilled");
                s.failed = t.getBoolean("failed");
                s.altar = BlockPos.of(t.getLong("altar"));
            }
        }
        s.sacrificeUntil = t.getLong("sacrifice_until");
        s.sacrificeHearts = t.getInt("sacrifice_hearts");
        s.lastRitualDay = t.contains("last_ritual_day") ? t.getLong("last_ritual_day") : -1;
        s.lastRitualTime = t.contains("last_ritual_time") ? t.getLong("last_ritual_time") : -1;
        return s;
    }
}
