package com.renanpontes.isekaimahou.feature.trials.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.feature.trials.TrialDimension;
import com.renanpontes.isekaimahou.feature.trials.TrialInstance;
import com.renanpontes.isekaimahou.feature.trials.TrialNetwork.TrialHud;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Estado da Provação no cliente (último pacote do servidor) e as linhas do rastreador de objetivos:
 * a provação em andamento (prioridade 1000) e, fora dela, a próxima Provação disponível (550).
 */
public final class TrialClientState {
    private static TrialHud hud = TrialHud.NONE;
    private static final TrialInstance.Phase[] PHASES = TrialInstance.Phase.values();

    static void accept(TrialHud h) {
        hud = h;
    }

    static void reset() {
        hud = TrialHud.NONE;
    }

    public static boolean active() {
        return hud.active();
    }

    /** Elemento da provação atual (Arcano se não houver): o céu do Reino usa as cores dele. */
    public static Element element() {
        Element e = hud.active() ? Element.byId(hud.element()) : null;
        return e == null ? Element.ARCANE : e;
    }

    public static TrialInstance.Phase phase() {
        int i = hud.phase();
        return i >= 0 && i < PHASES.length ? PHASES[i] : TrialInstance.Phase.LOBBY;
    }

    static ItemStack icon(Element e) {
        Element base = e.isFusion() ? e.parentA : e;
        var ess = CoreRegistries.ESSENCES.get(base);
        return ess == null ? new ItemStack(Items.NETHER_STAR) : new ItemStack(ess.get());
    }

    /** Linhas da provação em andamento. */
    static List<ObjectiveLine> activeLines() {
        if (!hud.active()) return List.of();
        Element el = element();
        MutableComponent title = TrialsApi.title(el, hud.rank());
        if (hud.challenge()) title.append(Component.translatable("objectives.isekai_mahou.trials.challenge_suffix"));
        ItemStack icon = icon(el);
        int rgb = el.color;
        List<ObjectiveLine> out = new ArrayList<>();
        ObjectiveLine main = switch (phase()) {
            case LOBBY -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.lobby", hud.members()), rgb, icon);
            case COUNTDOWN -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.countdown", hud.seconds()), rgb, icon);
            case INTRO -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.intro"), rgb, icon);
            case WAVE -> ObjectiveLine.progress(title, Component.translatable("objectives.isekai_mahou.trials.wave", hud.wave(), hud.waves(), hud.enemies()),
                    Math.max(0, hud.total() - hud.enemies()), Math.max(1, hud.total()), rgb, icon);
            case BREAK -> ObjectiveLine.of(title, Component.translatable(hud.wave() >= hud.waves()
                    ? "objectives.isekai_mahou.trials.boss_soon" : "objectives.isekai_mahou.trials.break", hud.seconds()), rgb, icon);
            case BOSS_INTRO, BOSS -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.boss"), rgb, icon);
            case VICTORY -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.victory", hud.seconds()), rgb, icon);
            case DEFEAT -> ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.trials.defeat"), rgb, icon);
            default -> null;
        };
        if (main == null) return List.of();
        if (hud.hasTarget()) main = main.withTarget(hud.target(), TrialDimension.KEY);
        out.add(main);
        if (hud.downed() > 0 || hud.selfDowned()) {
            out.add(ObjectiveLine.of(Component.translatable("objectives.isekai_mahou.trials.downed", hud.downed(), hud.members()),
                    hud.selfDowned() ? Component.translatable("objectives.isekai_mahou.trials.self_downed") : Component.empty(),
                    0xFF5A5A, ItemStack.EMPTY));
        }
        return out;
    }

    /** Fora de uma provação: a primeira Provação disponível (para o jogador saber que pode subir de rank). */
    static List<ObjectiveLine> availableLines() {
        if (hud.active()) return List.of();
        MagicData d = ClientMagic.data();
        if (d == null) return List.of();
        for (Element e : Element.values()) {
            if (e == Element.ARCANE || !d.isAwakened(e)) continue;
            int rank = TrialsApi.nextRank(d, e);
            if (rank <= 0 || !TrialsApi.meetsRequirements(d, e, rank, new ArrayList<>())) continue;
            return List.of(ObjectiveLine.of(Component.translatable("objectives.isekai_mahou.trials.available", TrialsApi.title(e, rank)),
                    Component.translatable("objectives.isekai_mahou.trials.available_hint", e.id), e.color, icon(e)));
        }
        return List.of();
    }

    private TrialClientState() {}
}
