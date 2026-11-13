package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TransformLaws;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Release 1.0 (onda 4) — <b>liberações moldáveis por magia</b>: a Shikai e a Bankai têm cada uma um ESPAÇO DE RECEITA
 * do módulo {@code transform_laws} (ids {@value #SHIKAI} e {@value #BANKAI}). O jogador escolhe ali uma receita dele
 * com a forma Transformação; enquanto a liberação dura, os efeitos da receita entram nos golpes e na aura e as leis
 * valem. A identidade da liberação (visual, assinatura do arquétipo, nome, técnicas) NÃO muda; sem receita escolhida,
 * tudo fica igual a antes.
 * <p>
 * A ligação é pela API do núcleo ({@link TransformLaws#begin}/{@link TransformLaws#end}): a fonte
 * {@code isekai_mahou:<espaço>} faz o módulo de leis achar a receita, validar de novo, cobrar a mana e pôr por cima.
 * As consultas ao espaço (receita escolhida, ativa agora) vão por nome de classe: o módulo de leis é procurado em
 * tempo de execução, e sem ele nada aqui quebra.
 */
public final class ReleaseForge {
    public static final String SHIKAI = "shikai";
    public static final String BANKAI = "bankai";
    /** Glifo da forma Transformação (sem ele não dá para montar receita). */
    public static final ResourceLocation FORM = IsekaiMahou.id("transformation");
    /** Dica de primeira vez (texto em {@code hint.isekai_mahou.<id>}). */
    public static final String HINT = "shinigami_shape_release";
    /** Marca do módulo de leis: "este jogador tem a liberação do espaço" (o espaço aparece na aba Votos). */
    private static final String SLOT_FLAG = "tforge.slot.";
    private static final String FORGE_CLASS = "com.renanpontes.isekaimahou.feature.transform_laws.TransformForge";
    /** Folga sobre o tempo da liberação: quem encerra a receita é o fim da liberação, não o relógio da sessão. */
    private static final int SLACK = 40;

    /**
     * O jogador já consegue moldar a liberação? Onda 5: SEMPRE — a liberação é o corpo da receita, então a forma
     * Transformação não precisa mais ser conhecida (o editor fica na aba Votos → Moldar).
     */
    public static boolean canShape(MagicData d) {
        return true;
    }

    /** Círculo em que a forma Transformação chega (para o aviso do botão). */
    public static int formCircle() {
        Glyph g = GlyphRegistry.get(FORM);
        return g == null ? 5 : Math.max(1, g.minCircle());
    }

    /** A liberação começou: a receita do espaço (se houver) entra por cima. Só para Shinigamis. */
    static void begin(ServerPlayer sp, String slot, int ticks) {
        if (!Shinigami.isShinigami(sp)) return;
        TransformLaws.begin(sp, IsekaiMahou.id(slot), List.of(), ticks + SLACK, null);
    }

    /** A liberação acabou (as leis da receita cobram o preço de fim, como em qualquer transformação). */
    static void end(ServerPlayer sp, String slot) {
        TransformLaws.end(sp, IsekaiMahou.id(slot));
    }

    /**
     * Uma vez por segundo: quem tem a liberação E a forma Transformação ganha o espaço na aba Votos (antes mesmo da
     * primeira liberação) e a dica do botão "Moldar liberação".
     */
    static void second(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SHINIGAMI || !Shinigami.hasShikai(d)) return;
        if (!d.hasFlag(SLOT_FLAG + SHIKAI)) d.setFlag(SLOT_FLAG + SHIKAI, true);
        if (Shinigami.hasBankai(d) && !d.hasFlag(SLOT_FLAG + BANKAI)) d.setFlag(SLOT_FLAG + BANKAI, true);
        if (!Hints.seen(sp, HINT)) Hints.show(sp, HINT);
    }

    // =====================================================================================
    //  Consultas ao módulo de leis (por nome: ele pode não estar presente)
    // =====================================================================================
    private static boolean looked;
    private static Method recipeMethod, activeMethod, plannedMethod, cooldownMethod;

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> c = Class.forName(FORGE_CLASS);
            recipeMethod = c.getMethod("recipe", Player.class, String.class);
            activeMethod = c.getMethod("active", LivingEntity.class, String.class);
            plannedMethod = c.getMethod("plannedDurationMult", Player.class, String.class);
            cooldownMethod = c.getMethod("activeCooldownMult", LivingEntity.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // sem o módulo de leis: sem receita, nada ativo
        }
    }

    /** Receita escolhida para o espaço (servidor ou cliente); null = nenhuma. */
    @Nullable
    public static SpellRecipe recipe(@Nullable Player player, String slot) {
        look();
        if (player == null || recipeMethod == null) return null;
        try {
            return recipeMethod.invoke(null, player, slot) instanceof SpellRecipe r && !r.isEmpty() ? r : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Onda 5: quanto a receita escolhida vai esticar/encurtar a liberação que está para começar (Estender/Encurtar no
     * corpo). 1 = sem receita, sem mana para ela ou sem o módulo de leis. Chamar ANTES de {@link #begin}.
     */
    static float plannedDuration(ServerPlayer sp, String slot) {
        look();
        if (plannedMethod == null) return 1F;
        try {
            return plannedMethod.invoke(null, sp, slot) instanceof Float f ? Math.max(0.25F, Math.min(3F, f)) : 1F;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 1F;
        }
    }

    /** Onda 5: quanto a receita ATIVA do espaço mexe na recarga da liberação (ler ANTES de {@link #end}). */
    static float cooldownMult(ServerPlayer sp, String slot) {
        look();
        if (cooldownMethod == null) return 1F;
        try {
            return cooldownMethod.invoke(null, sp, slot) instanceof Float f ? Math.max(0.25F, Math.min(3F, f)) : 1F;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 1F;
        }
    }

    /** A receita do espaço está valendo agora? (servidor) */
    public static boolean active(@Nullable LivingEntity entity, String slot) {
        look();
        if (entity == null || activeMethod == null) return false;
        try {
            return Boolean.TRUE.equals(activeMethod.invoke(null, entity, slot));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private ReleaseForge() {}
}
