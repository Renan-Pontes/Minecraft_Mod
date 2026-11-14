package com.renanpontes.isekaimahou.feature.transform_laws;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v1.1 — um <b>corpo de transformação</b>: um glifo de EFEITO que só existe dentro da forma Transformação
 * ({@code Transformação → corpo [aumentos] → efeitos → leis}). Antes eram FORMAS (O Todo-Poderoso, Bankai,
 * Vollständig, Máscara do Vazio, Vasto Lorde, Letzt Stil, Monarca das Sombras, Transcendência) — palavra do dono:
 * <i>"A ideia não é bug, é saírem de formas e virarem efeitos pra forma 'transformação'"</i>.
 * <ul>
 *   <li>o corpo entra UMA vez, no começo, em quem conjura ({@link ForgeSessions}); fora da Transformação a magia não
 *       monta (erro do {@code SpellParser});</li>
 *   <li>{@link #bodyTicks}: o corpo dita quanto a transformação inteira dura (aura, golpes, leis); o maior vence;</li>
 *   <li>{@link #minCooldown}: recarga mínima da magia; {@link #supreme()}: só na Transformação Suprema (tecla G,
 *       gasta a Carga de Ultimate), como quando era uma forma ultimate;</li>
 *   <li>{@link #onTransformEnd}: a transformação acabou por fora (reconjurou, comando, morte) — o corpo se desfaz.</li>
 * </ul>
 * O corpo pode encerrar a transformação antes da hora com {@link TransformForge#endCast} e mexer no tempo restante com
 * {@link TransformForge#setCastRemaining}. Implementações costumam estender {@link BodyEffect}.
 */
public interface TransformBody {
    /** Duração do corpo em ticks, com os aumentos postos LOGO DEPOIS dele (Estender, Encurtar...). */
    int bodyTicks(SpellStats stats);

    /** Recarga mínima da magia inteira (ticks). 0 = a da forma. */
    default int minCooldown() {
        return 0;
    }

    /** Era uma forma ultimate: só entra na Transformação Suprema (slot G). */
    default boolean supreme() {
        return false;
    }

    /** Estilo da cutscene de transformação ({@code Cutscenes.transform}). */
    default String cutsceneStyle() {
        return "default";
    }

    /** Cor da cutscene (RGB). */
    default int cutsceneRgb() {
        return 0xE8D8F0;
    }

    /** Título da cutscene (null = nome do glifo). */
    @Nullable
    default Component cutsceneTitle(ServerPlayer sp) {
        return null;
    }

    /** A transformação conjurada acabou (qualquer motivo). Desfaz o estado do corpo se ainda estiver ativo. */
    default void onTransformEnd(LivingEntity entity) {}

    /** O glifo (para nome e id). */
    default EffectGlyph glyph() {
        return (EffectGlyph) this;
    }
}
