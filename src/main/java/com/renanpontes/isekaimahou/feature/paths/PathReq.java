package com.renanpontes.isekaimahou.feature.paths;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.TrialsApi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Um requisito (de nó, de entrada na Provação ou de feito). Código COMUM: o servidor usa para liberar e o
 * cliente para desenhar ✔/✘ e "n/m" (contadores, nós e flags chegam ao cliente pela sincronização do MagicData).
 */
public sealed interface PathReq {

    /** Progresso atual (comparado com {@link #target()}). {@code player} fica para requisitos de inventário. */
    int current(MagicData d, @Nullable Player player);

    int target();

    MutableComponent label();

    /** Divisor só para exibição (ex.: ticks → segundos). */
    default int divisor() {
        return 1;
    }

    default boolean met(MagicData d, @Nullable Player player) {
        return current(d, player) >= target();
    }

    /** Mostra "n/m" na aba (contagens; requisitos de sim/não não mostram). */
    default boolean countable() {
        return target() > 1;
    }

    // =====================================================================================

    /** O elemento precisa estar despertado. */
    record Awakened(Element element) implements PathReq {
        @Override public int current(MagicData d, Player p) { return d.isAwakened(element) ? 1 : 0; }
        @Override public int target() { return 1; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.awakened", element.displayName());
        }
    }

    /** Nível de domínio do elemento. */
    record ElementLevel(Element element, int level) implements PathReq {
        @Override public int current(MagicData d, Player p) { return Math.max(0, d.elementLevel(element)); }
        @Override public int target() { return level; }
        @Override public boolean countable() { return true; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.element_level_of", element.displayName());
        }
    }

    /** Círculo mínimo. */
    record Circle(int circle) implements PathReq {
        @Override public int current(MagicData d, Player p) { return d.circle(); }
        @Override public int target() { return circle; }
        /** "1/2" deixa claro quantos Círculos faltam (Círculo 1 todo mundo já tem: só ✔). */
        @Override public boolean countable() { return circle > 1; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.circle", circle);
        }
    }

    /**
     * Provação do elemento concluída até o rank. Usa o MAIOR rank concluído: vencer a Provação V também vale
     * para I–IV (mundos antigos e comandos podem ter só a flag mais alta).
     */
    record Trial(Element element, int rank) implements PathReq {
        @Override public int current(MagicData d, Player p) { return TrialsApi.clearedRank(d, element) >= rank ? 1 : 0; }
        @Override public int target() { return 1; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.trial", TrialsApi.title(element, rank));
        }
    }

    /** Nó de outro elemento (o de um elemento-pai: Convergência das fusões). */
    record Node(Element element, PathNode node) implements PathReq {
        @Override public int current(MagicData d, Player p) { return d.hasNode(element, node.id) ? 1 : 0; }
        @Override public int target() { return 1; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.parent_node", element.displayName(), node.displayName());
        }
    }

    /** Contador de feito/estatística do MagicData (kill.*, cast.*, feat.*, visit.*, boss.*, ritual_rank). */
    record Counter(String key, int target, String labelKey, List<Component> args, int divisor) implements PathReq {
        public Counter(String key, int target, String labelKey, Component... args) {
            this(key, target, labelKey, List.of(args), 1);
        }

        @Override public int current(MagicData d, Player p) { return d.counter(key); }
        @Override public MutableComponent label() {
            return Component.translatable(labelKey, args.toArray());
        }
    }

    /** Chefe derrotado (contador boss.<id>), usado na Convergência do Crepúsculo. */
    record Boss(String bossId, String fallbackKey) implements PathReq {
        @Override public int current(MagicData d, Player p) { return d.counter("boss." + bossId) > 0 ? 1 : 0; }
        @Override public int target() { return 1; }
        @Override public MutableComponent label() {
            return Component.translatable("paths.isekai_mahou.req.boss", PathRules.entityName(bossId, fallbackKey));
        }
    }
}
