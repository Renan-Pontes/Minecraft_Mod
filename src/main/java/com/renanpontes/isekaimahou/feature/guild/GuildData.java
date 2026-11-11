package com.renanpontes.isekaimahou.feature.guild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Dados da Guilda de um jogador (Data Attachment do módulo, salvo no mundo e copiado na morte).
 * O cliente recebe uma VISÃO pronta (pacote GuildNetwork.State), não esta classe.
 * Todos os campos são opcionais no codec: saves antigos abrem normalmente (campos novos começam vazios).
 */
public final class GuildData {
    /** Missões da Guilda ativas ao mesmo tempo (a prova de promoção não conta). */
    public static final int MAX_ACTIVE = 3;
    /** Missões de facção (Reino, Academia, Igreja, Submundo) ativas ao mesmo tempo — contam à parte. */
    public static final int MAX_FACTION_ACTIVE = 2;

    /**
     * Um aventureiro contratado (v0.8): id da entidade, fim do contrato (tempo de jogo do Overworld) e um retrato
     * (nome, classe, rank) para o rastreador quando a entidade não está carregada.
     */
    public record Hire(UUID id, long end, Component name, int variant, int rank) {
        public static final Codec<Hire> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Hire::id),
                Codec.LONG.fieldOf("end").forGetter(Hire::end),
                ComponentSerialization.CODEC.optionalFieldOf("name", Component.empty()).forGetter(Hire::name),
                Codec.INT.optionalFieldOf("variant", 0).forGetter(Hire::variant),
                Codec.INT.optionalFieldOf("rank", 1).forGetter(Hire::rank)
        ).apply(i, Hire::new));
    }

    public static final Codec<GuildData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("rank", 0).forGetter(d -> d.rank),
            Codec.INT.optionalFieldOf("points", 0).forGetter(d -> d.points),
            Codec.INT.optionalFieldOf("completed", 0).forGetter(d -> d.completed),
            ActiveQuest.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(d -> d.active),
            ActiveQuest.CODEC.optionalFieldOf("trial").forGetter(d -> Optional.ofNullable(d.trial)),
            Codec.LONG.optionalFieldOf("board_day", -1L).forGetter(d -> d.boardDay),
            ResourceLocation.CODEC.listOf().optionalFieldOf("board_taken", List.of()).forGetter(d -> new ArrayList<>(d.boardTaken)),
            GlobalPos.CODEC.optionalFieldOf("hall").forGetter(d -> Optional.ofNullable(d.hall)),
            GlobalPos.CODEC.optionalFieldOf("master_pos").forGetter(d -> Optional.ofNullable(d.masterPos)),
            Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("boards", Map.of()).forGetter(d -> d.boards),
            Hire.CODEC.listOf().optionalFieldOf("hires", List.of()).forGetter(d -> d.hires)
    ).apply(i, GuildData::new));

    /** 0 = não registrado; 1..7 = F..S. */
    private int rank;
    private int points;
    private int completed;
    private final List<ActiveQuest> active = new ArrayList<>();
    private ActiveQuest trial;
    /** Dia do quadro em que {@link #boardTaken} vale (muda à meia-noite do jogo). */
    private long boardDay;
    private final Set<ResourceLocation> boardTaken = new LinkedHashSet<>();
    /** Último quadro/recepção usado (onde entregar missões) — a setinha do rastreador de objetivos aponta para cá. */
    private GlobalPos hall;
    /** Último Mestre da Guilda visitado (provas de promoção). */
    private GlobalPos masterPos;
    /** v0.8: último Quadro de cada facção (não-Guilda) usado — seta das missões de facção. */
    private final Map<String, GlobalPos> boards = new LinkedHashMap<>();
    /** v0.8: aventureiros contratados. */
    private final List<Hire> hires = new ArrayList<>();

    // ---------- Temporários ----------
    /** Algo mudou: reenviar a visão ao cliente e atualizar os cartões. */
    public boolean dirty = true;
    /** Assinatura da última visão enviada (evita reenviar a mesma coisa). */
    public int lastSentHash;

    public GuildData() {
        this(0, 0, 0, List.of(), Optional.empty(), -1L, List.of(), Optional.empty(), Optional.empty(), Map.of(), List.of());
    }

    private GuildData(int rank, int points, int completed, List<ActiveQuest> active, Optional<ActiveQuest> trial, long boardDay,
                      List<ResourceLocation> boardTaken, Optional<GlobalPos> hall, Optional<GlobalPos> masterPos,
                      Map<String, GlobalPos> boards, List<Hire> hires) {
        this.rank = Math.max(0, Math.min(GuildRank.values().length, rank));
        this.points = Math.max(0, points);
        this.completed = Math.max(0, completed);
        this.active.addAll(active);
        this.trial = trial.orElse(null);
        this.boardDay = boardDay;
        this.boardTaken.addAll(boardTaken);
        this.hall = hall.orElse(null);
        this.masterPos = masterPos.orElse(null);
        this.boards.putAll(boards);
        this.hires.addAll(hires);
    }

    public boolean registered() { return rank > 0; }
    public int rankLevel() { return rank; }
    public GuildRank rank() { return GuildRank.byLevel(rank); }
    public int points() { return points; }
    public int completed() { return completed; }
    public List<ActiveQuest> active() { return active; }
    public ActiveQuest trial() { return trial; }
    public Optional<GlobalPos> hall() { return Optional.ofNullable(hall); }
    public Optional<GlobalPos> masterPos() { return Optional.ofNullable(masterPos); }

    public void setRank(GuildRank r) { rank = r == null ? 0 : r.level(); dirty = true; }
    public void addPoints(int n) { points = Math.max(0, points + n); dirty = true; }
    public void addCompleted() { completed++; dirty = true; }
    public void setTrial(ActiveQuest q) { trial = q; dirty = true; }

    /** Guarda onde fica o balcão/quadro (só marca para reenviar se mudou). */
    public void setHall(GlobalPos pos) {
        if (!pos.equals(hall)) {
            hall = pos;
            dirty = true;
        }
    }

    public void setMasterPos(GlobalPos pos) {
        if (!pos.equals(masterPos)) {
            masterPos = pos;
            dirty = true;
        }
    }

    /** Último Quadro usado de uma facção (a Guilda usa {@link #hall()}). */
    public Optional<GlobalPos> board(String faction) {
        return Optional.ofNullable(boards.get(faction));
    }

    public Map<String, GlobalPos> boards() {
        return boards;
    }

    public void setBoard(String faction, GlobalPos pos) {
        if (!pos.equals(boards.get(faction))) {
            boards.put(faction, pos);
            dirty = true;
        }
    }

    public List<Hire> hires() {
        return hires;
    }

    public void addHire(Hire h) {
        hires.removeIf(x -> x.id().equals(h.id()));
        hires.add(h);
        dirty = true;
    }

    public boolean removeHire(UUID id) {
        boolean removed = hires.removeIf(x -> x.id().equals(id));
        if (removed) dirty = true;
        return removed;
    }

    public ActiveQuest find(ResourceLocation id) {
        for (ActiveQuest q : active) if (q.id.equals(id)) return q;
        return null;
    }

    /** Missões já pegas no quadro de hoje (zera quando o dia muda). */
    public Set<ResourceLocation> takenToday(long day) {
        if (day != boardDay) {
            boardDay = day;
            boardTaken.clear();
            dirty = true;
        }
        return boardTaken;
    }

    public void markDirty() { dirty = true; }
}
