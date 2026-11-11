package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Avisos no chat: "Nova Provação disponível: Fogo II [Iniciar]" (uma vez por rank, quando o jogador fica elegível)
 * e a lista de Provações do jogador (/mahou trial info e o Altar fora do Reino).
 */
final class TrialNotifier {

    /** Avisa (uma vez) cada Provação que acabou de ficar disponível. {@code only} = só esse elemento (null = todos). */
    static void check(ServerPlayer p, @Nullable Element only) {
        if (TrialManager.of(p.getUUID()) != null || TrialDimension.is(p.level())) return;
        MagicData d = MagicApi.get(p);
        for (Element e : Element.values()) {
            if (e == Element.ARCANE || !d.isAwakened(e) || (only != null && e != only)) continue;
            int rank = TrialsApi.nextRank(d, e);
            if (rank <= 0) continue;
            String flag = "trials.notified." + e.id + "." + rank;
            if (d.hasFlag(flag) || !TrialsApi.meetsRequirements(d, e, rank, new ArrayList<>())) continue;
            d.setFlag(flag, true);
            d.markDirty();
            p.sendSystemMessage(Component.literal("✦ ").withColor(e.color)
                    .append(Component.translatable("message.isekai_mahou.trials.available", TrialsApi.title(e, rank)).withStyle(ChatFormatting.GOLD))
                    .append(" ").append(startButton(e, rank, false)));
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.available_hint").withStyle(ChatFormatting.GRAY));
            TrialFx.ding(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 1.5F);
        }
    }

    /** Botão clicável [Iniciar]/[Repetir] que roda {@code /mahou trial start <el> <rank>}. */
    static Component startButton(Element e, int rank, boolean repeat) {
        String cmd = "/mahou trial start " + e.id + " " + rank;
        return Component.translatable(repeat ? "message.isekai_mahou.trials.button_repeat" : "message.isekai_mahou.trials.button_start")
                .withStyle(s -> s.withColor(repeat ? ChatFormatting.AQUA : ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.isekai_mahou.trials.button_hover", TrialsApi.title(e, rank)))));
    }

    /** Lista as Provações de cada elemento despertado: o que já foi feito, o próximo rank e o que falta. */
    static void list(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.list_header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        boolean any = false;
        for (Element e : Element.values()) {
            if (e == Element.ARCANE || !d.isAwakened(e)) continue;
            any = true;
            int cleared = TrialsApi.clearedRank(d, e);
            int next = TrialsApi.nextRank(d, e);
            MutableComponent line = Component.literal("• ").withColor(e.color).append(e.displayName()).append(Component.literal(": "));
            if (cleared > 0) {
                line.append(Component.translatable("message.isekai_mahou.trials.list_cleared", TrialsApi.roman(cleared)).withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.translatable("message.isekai_mahou.trials.list_none").withStyle(ChatFormatting.GRAY));
            }
            if (next == 0) {
                line.append(Component.literal(" · ")).append(Component.translatable("message.isekai_mahou.trials.list_all").withStyle(ChatFormatting.GOLD))
                        .append(" ").append(startButton(e, Progression.MAX_TRIAL_RANK, true));
                p.sendSystemMessage(line);
                continue;
            }
            List<Component> missing = new ArrayList<>();
            boolean ok = TrialsApi.meetsRequirements(d, e, next, missing);
            line.append(Component.literal(" · ")).append(Component.translatable("message.isekai_mahou.trials.list_next", TrialsApi.roman(next))
                    .withStyle(ok ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
            if (ok) line.append(" ").append(startButton(e, next, false));
            else if (cleared > 0) line.append(" ").append(startButton(e, cleared, true));
            p.sendSystemMessage(line);
            for (Component m : missing) {
                p.sendSystemMessage(Component.literal("    ✘ ").withStyle(ChatFormatting.RED).append(m.copy().withStyle(ChatFormatting.GRAY)));
            }
        }
        if (!any) p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.list_empty").withStyle(ChatFormatting.GRAY));
    }

    private TrialNotifier() {}
}
