package com.renanpontes.isekaimahou.feature.paths.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.feature.paths.PathRules;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Linha do rastreador de objetivos (canto superior direito): a próxima Provação.
 * <ul>
 *   <li>Se alguma Provação já pode começar: "Provação de Fogo II disponível" (abra Caminhos).</li>
 *   <li>Senão, para o elemento ativo na Roda (ou, no Arcano, o 1º elemento com Provação pela frente): progresso
 *       do nível do elemento até o exigido pelo próximo rank, ou o Círculo que falta.</li>
 * </ul>
 * Some dentro das Câmaras de Provação (lá o módulo trials mostra as ondas).
 */
public final class PathsObjectives {
    public static final String ID = "paths";
    public static final int PRIORITY = 200;
    private static final String TRIAL_REALM = "trial_realm";

    public static void register() {
        ObjectiveTracker.register(ID, PRIORITY, PathsObjectives::lines);
    }

    private static List<ObjectiveLine> lines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return List.of();
        if (mc.level.dimension().location().equals(IsekaiMahou.id(TRIAL_REALM))) return List.of();
        MagicData d = ClientMagic.data();
        if (d.path() == MagicPath.NONE) return List.of();

        // 1) Alguma Provação pronta para começar (elemento ativo primeiro, depois a Roda, depois o resto).
        for (Element e : candidates(d)) {
            int rank = TrialsApi.nextRank(d, e);
            if (rank > 0 && TrialsApi.meetsRequirements(d, e, rank, new ArrayList<>())) {
                Component detail = Component.translatable("paths.isekai_mahou.objective.ready",
                        Component.keybind("key.isekai_mahou.menu").withStyle(ChatFormatting.YELLOW));
                return List.of(ObjectiveLine.of(Component.translatable("paths.isekai_mahou.objective.available", TrialsApi.title(e, rank)),
                        detail, e.color, PathRules.elementIcon(e)));
            }
        }

        // 2) Caminho até a próxima Provação do elemento ativo (na Roda em Arcano: o 1º elemento com Provação pela frente).
        Element a = null;
        for (Element e : candidates(d)) {
            if (TrialsApi.nextRank(d, e) > 0) {
                a = e;
                break;
            }
        }
        if (a == null) return List.of();
        int rank = TrialsApi.nextRank(d, a);
        if (rank == 0) return List.of();
        Component title = Component.translatable("paths.isekai_mahou.objective.next", TrialsApi.title(a, rank));
        int needLevel = Progression.trialElementLevel(rank);
        if (d.elementLevel(a) < needLevel) {
            int have = d.element(a).xp, need = xpForElementLevel(needLevel);
            Component detail = Component.translatable("paths.isekai_mahou.objective.level", a.displayName(), needLevel);
            return List.of(ObjectiveLine.progress(title, detail, have, need, a.color, PathRules.elementIcon(a)));
        }
        int needCircle = Progression.trialCircle(rank);
        if (d.circle() < needCircle) {
            return List.of(ObjectiveLine.of(title, Component.translatable("paths.isekai_mahou.objective.circle", needCircle),
                    a.color, PathRules.elementIcon(a)));
        }
        return List.of();
    }

    private static Set<Element> candidates(MagicData d) {
        Set<Element> out = new LinkedHashSet<>();
        if (d.activeElement() != Element.ARCANE) out.add(d.activeElement());
        out.addAll(d.registeredElements());
        for (Element e : PathRules.pathElements()) if (d.isAwakened(e)) out.add(e);
        return out;
    }

    /** XP total do elemento para chegar ao nível informado. */
    private static int xpForElementLevel(int level) {
        int sum = 0;
        for (int l = 0; l < level; l++) sum += Progression.elementXpToNext(l);
        return sum;
    }

    private PathsObjectives() {}
}
