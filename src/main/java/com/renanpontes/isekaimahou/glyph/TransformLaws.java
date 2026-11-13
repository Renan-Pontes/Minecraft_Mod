package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * v1.4 — API pública das <b>Leis de Transformação</b> ({@link TransformLawGlyph}). Quem implementa as regras é o módulo
 * {@code transform_laws} (instala o {@link Backend} no init); sem ele, tudo aqui é inofensivo (nada ativo).
 * <p>
 * Para OUTROS módulos (Resurrección, Bankai, Vollständig...) entrarem no sistema:
 * <pre>
 *   // ao liberar a forma (servidor):
 *   TransformLaws.begin(sp, IsekaiMahou.id("bankai"), TransformLaws.lawsOf(ctx.spell()), ticks, myEffectHolder);
 *   // ao terminar (opcional se passou um probe — a sessão acaba sozinha quando o efeito some):
 *   TransformLaws.end(sp, IsekaiMahou.id("bankai"));
 * </pre>
 * Leis vindas de uma receita ({@link #lawsOf}) já foram validadas pelo {@link SpellParser}. Para a forma registrada em
 * {@link TransformTargets} nada disso é preciso: o módulo começa a sessão sozinho depois da conjuração.
 */
public final class TransformLaws {
    /** Implementação (módulo transform_laws). */
    public interface Backend {
        List<TransformLawGlyph> active(LivingEntity entity);

        void begin(ServerPlayer player, ResourceLocation source, Collection<TransformLawGlyph> laws, int ticks,
                   @Nullable Holder<MobEffect> probe);

        void end(ServerPlayer player, ResourceLocation source);
    }

    private static volatile Backend backend;

    /** Chamado uma vez pelo módulo transform_laws. */
    public static void install(Backend b) {
        backend = b;
    }

    /** Leis de Transformação em vigor na entidade agora (sem repetição; vazio fora de transformação). */
    public static List<TransformLawGlyph> active(@Nullable LivingEntity entity) {
        Backend b = backend;
        return b == null || entity == null ? List.of() : b.active(entity);
    }

    /** A lei {@code path} (ex.: "tlaw_blind_fury") está valendo para a entidade? */
    public static boolean has(@Nullable LivingEntity entity, String path) {
        for (TransformLawGlyph l : active(entity)) if (l.id().getPath().equals(path)) return true;
        return false;
    }

    /**
     * Começa (ou renova) uma sessão de leis para uma transformação. {@code source} identifica a transformação (a mesma
     * fonte substitui a sessão anterior). {@code probe}: enquanto o jogador tiver esse efeito a sessão continua (acaba
     * quando some); null = dura {@code ticks}.
     */
    public static void begin(ServerPlayer player, ResourceLocation source, Collection<TransformLawGlyph> laws, int ticks,
                             @Nullable Holder<MobEffect> probe) {
        // Release 1.0: lista vazia também chega ao módulo — uma liberação sem leis soltas ainda pode ter uma RECEITA de
        // transformação escolhida para ela (espaço com o path de {@code source}; ver transform_laws.TransformForge).
        // Sem leis e sem receita o módulo não faz nada, como antes.
        Backend b = backend;
        if (b != null) b.begin(player, source, laws, ticks, probe);
    }

    /** Termina a sessão da fonte (aplica os preços de fim, como o de Último Suspiro). */
    public static void end(ServerPlayer player, ResourceLocation source) {
        Backend b = backend;
        if (b != null) b.end(player, source);
    }

    /** Leis de Transformação da receita (estágio 0). */
    public static List<TransformLawGlyph> lawsOf(@Nullable ParsedSpell spell) {
        List<TransformLawGlyph> out = new ArrayList<>();
        if (spell == null) return out;
        for (LawGlyph l : spell.laws()) if (l instanceof TransformLawGlyph t && !out.contains(t)) out.add(t);
        return out;
    }

    private TransformLaws() {}
}
