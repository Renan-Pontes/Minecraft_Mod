package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dados do Druida guardados no jogador (attachment "ritual_druid_grove"):
 * <ul>
 *   <li>{@code tree}: posição da Árvore Coração (ou da Muda Ancestral germinando) — só 1 por jogador;</li>
 *   <li>{@code taskStage}/{@code taskBase}: para qual estágio a tarefa de estação está contando e o valor do contador
 *       quando ela começou (o progresso é a diferença);</li>
 *   <li>{@code pending}: mudas plantadas pelo jogador esperando virar árvore (contam em {@code feat.trees_planted}).</li>
 * </ul>
 */
public class DruidGrove {
    public static final int MAX_PENDING = 64;

    public static final Codec<DruidGrove> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.optionalFieldOf("tree").forGetter(g -> Optional.ofNullable(g.tree)),
            Codec.INT.optionalFieldOf("task_stage", -1).forGetter(g -> g.taskStage),
            Codec.INT.optionalFieldOf("task_base", 0).forGetter(g -> g.taskBase),
            GlobalPos.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(g -> g.pending)
    ).apply(i, DruidGrove::new));

    private GlobalPos tree;
    private int taskStage;
    private int taskBase;
    private final List<GlobalPos> pending = new ArrayList<>();

    public DruidGrove() {
        this(Optional.empty(), -1, 0, List.of());
    }

    private DruidGrove(Optional<GlobalPos> tree, int taskStage, int taskBase, List<GlobalPos> pending) {
        this.tree = tree.orElse(null);
        this.taskStage = taskStage;
        this.taskBase = taskBase;
        this.pending.addAll(pending);
    }

    public static DruidGrove of(ServerPlayer player) {
        return player.getData(DruidContent.GROVE);
    }

    public GlobalPos tree() { return tree; }
    public void setTree(GlobalPos pos) { this.tree = pos; }

    public int taskStage() { return taskStage; }
    public int taskBase() { return taskBase; }
    public void startTask(int stage, int base) {
        this.taskStage = stage;
        this.taskBase = base;
    }

    public List<GlobalPos> pending() { return pending; }

    /** Registra uma muda plantada (descarta as mais antigas se passar do limite). */
    public void addPending(GlobalPos pos) {
        if (pending.contains(pos)) return;
        pending.add(pos);
        while (pending.size() > MAX_PENDING) pending.remove(0);
    }
}
