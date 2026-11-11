package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.feature.journal.JournalTargets;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalCondition;
import com.renanpontes.isekaimahou.feature.journal.data.JournalLocate;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Provedor do rastreador de objetivos (canto da tela): o capítulo atual com o progresso dos obrigatórios e o PASSO atual
 * com o progresso cur/meta, a dica curta como detalhe e — se o servidor localizou o lugar — a seta com a distância.
 * Tudo calculado no cliente com o livro sincronizado e o {@code ClientMagic.data()}.
 */
public final class JournalObjectives {
    public static final String ID = "journal";
    /** História principal: abaixo de Provação/chefe (1000) e Ascensão (600), acima da Guilda (300). */
    public static final int PRIORITY = 500;
    /** v1.0: rota da classe (uma linha), logo abaixo da história. */
    public static final String CLASS_ID = "journal_class";
    public static final int CLASS_PRIORITY = 450;

    public static void register() {
        ObjectiveTracker.register(ID, PRIORITY, JournalObjectives::lines);
        ObjectiveTracker.register(CLASS_ID, CLASS_PRIORITY, JournalObjectives::classLines);
    }

    /**
     * Rota da classe: uma linha com o passo atual (ícone da classe, cor da classe), o progresso cur/meta, a dica curta
     * e — se o servidor achou o lugar — a seta.
     */
    private static List<ObjectiveLine> classLines() {
        LocalPlayer player = Minecraft.getInstance().player;
        JournalBook book = JournalBook.client();
        if (player == null) return List.of();
        MagicData d = ClientMagic.data();
        JournalChapter ch = book.classActive(d);
        if (ch == null || !d.hasFlag(ch.begunFlag())) return List.of();
        JournalStep step = JournalBook.currentStep(ch, d, player);
        if (step == null) return List.of();
        ItemStack chapterIcon = icon(ch.icons().isEmpty() ? null : ch.icons().get(0), new ItemStack(Items.WRITABLE_BOOK));
        int target = step.condition().target();
        int cur = Math.max(0, Math.min(target, step.condition().current(d, player)));
        MutableComponent title = Component.literal("✦ ").append(step.title(ch));
        JournalTargets.Target located = JournalTargets.forStep(step.uid(ch));
        Component detail = detail(ch, step, d, player, located);
        if (detail.getString().isEmpty()) detail = Component.translatable("screen.isekai_mahou.journal.tracker.class", ch.magicPath().displayName());
        ObjectiveLine line = target > 1
                ? ObjectiveLine.progress(title, detail, cur, target, ch.color(), icon(step.icon(), chapterIcon))
                : ObjectiveLine.of(title, detail, ch.color(), icon(step.icon(), chapterIcon));
        if (located != null && located.status() == JournalTargets.Status.FOUND) {
            double y = located.yKnown() ? located.pos().getY() + 0.5 : player.getY();
            line = line.withTarget(new Vec3(located.pos().getX() + 0.5, y, located.pos().getZ() + 0.5), located.dimension());
        }
        return List.of(line);
    }

    private static List<ObjectiveLine> lines() {
        LocalPlayer player = Minecraft.getInstance().player;
        JournalBook book = JournalBook.client();
        if (player == null || book.isEmpty()) return List.of();
        MagicData d = ClientMagic.data();
        JournalChapter ch = book.active(d);
        // Antes da abertura do capítulo (escolha de classe pendente) não há nada a seguir ainda.
        if (ch == null || !d.hasFlag(ch.begunFlag())) return List.of();

        List<ObjectiveLine> out = new ArrayList<>(2);
        List<JournalStep> required = ch.required();
        int done = 0;
        for (JournalStep s : required) if (JournalBook.stepDone(ch, s, d, player)) done++;
        ItemStack chapterIcon = icon(ch.icons().isEmpty() ? null : ch.icons().get(0), new ItemStack(Items.WRITABLE_BOOK));
        Component chapterTitle = Component.translatable("screen.isekai_mahou.journal.tracker.chapter", book.indexOf(ch) + 1, ch.title());
        out.add(ObjectiveLine.progress(chapterTitle, Component.empty(), done, required.size(), ch.color(), chapterIcon));

        JournalStep step = JournalBook.currentStep(ch, d, player);
        if (step == null) return out;
        JournalCondition c = step.condition();
        int target = c.target();
        int cur = Math.max(0, Math.min(target, c.current(d, player)));
        MutableComponent title = step.title(ch);
        if (step.optional()) title = Component.literal("✧ ").append(title);
        ItemStack icon = icon(step.icon(), chapterIcon);
        JournalTargets.Target located = JournalTargets.forStep(step.uid(ch));
        Component detail = detail(ch, step, d, player, located);
        ObjectiveLine line = target > 1
                ? ObjectiveLine.progress(title, detail, cur, target, ch.color(), icon)
                : ObjectiveLine.of(title, detail, ch.color(), icon);
        if (located != null && located.status() == JournalTargets.Status.FOUND) {
            // Altura desconhecida: usa a do jogador (a seta não mostra ↑/↓ à toa).
            double y = located.yKnown() ? located.pos().getY() + 0.5 : player.getY();
            line = line.withTarget(new Vec3(located.pos().getX() + 0.5, y, located.pos().getZ() + 0.5), located.dimension());
        }
        out.add(line);
        return out;
    }

    /**
     * Detalhe da linha do passo: "em outra dimensão", "nada por perto", o que falta para a Provação, ou a dica curta.
     */
    private static Component detail(JournalChapter ch, JournalStep step, MagicData d, LocalPlayer player,
                                    JournalTargets.Target located) {
        JournalLocate loc = step.locate();
        if (loc != null && loc.dimension() != null && !loc.dimension().equals(player.level().dimension().location())) {
            return Component.translatable("screen.isekai_mahou.journal.tracker.elsewhere");
        }
        if (located != null && located.status() == JournalTargets.Status.NOT_FOUND) {
            return Component.translatable("screen.isekai_mahou.journal.tracker.not_found");
        }
        if (step.condition() instanceof JournalCondition.Trial trial) {
            Component t = trialDetail(trial, d);
            if (t != null) return t;
        }
        String tip = step.tipKey(ch, d.path());
        if (I18n.exists(tip)) return Component.translatable(tip);
        tip = step.tipKey(ch);
        return I18n.exists(tip) ? Component.translatable(tip) : Component.empty();
    }

    /**
     * Provação ainda não feita: escolhe o elemento mais adiantado e diz o que falta (ou que já pode começar).
     * Null quando o passo pede um elemento específico já concluído (deixa a dica normal).
     */
    private static Component trialDetail(JournalCondition.Trial trial, MagicData d) {
        int rank = trial.rank();
        Element best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Element e : Element.values()) {
            if (e == Element.ARCANE || !d.isAwakened(e)) continue;
            if (trial.element() != null && trial.element() != e) continue;
            if (TrialsApi.clearedRank(d, e) >= rank) continue;
            // Pronto para entrar vale mais; depois, quem está mais perto do nível pedido.
            List<Component> missing = new ArrayList<>();
            int score = (TrialsApi.meetsRequirements(d, e, rank, missing) ? 10_000 : 0)
                    + d.elementLevel(e) * 10 - Math.max(0, rank - 1 - TrialsApi.clearedRank(d, e)) * 100;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) return null;
        List<Component> missing = new ArrayList<>();
        if (TrialsApi.meetsRequirements(d, best, rank, missing)) {
            return Component.translatable("screen.isekai_mahou.journal.tracker.trial_ready", TrialsApi.title(best, rank));
        }
        if (!missing.isEmpty()) return missing.get(0);
        return Component.translatable("screen.isekai_mahou.journal.tracker.trial_need", Progression.trialElementLevel(rank),
                Progression.trialCircle(rank));
    }

    private static ItemStack icon(ResourceLocation id, ItemStack fallback) {
        if (id == null) return fallback;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null || item == Items.AIR ? fallback : new ItemStack(item);
    }

    private JournalObjectives() {}
}
