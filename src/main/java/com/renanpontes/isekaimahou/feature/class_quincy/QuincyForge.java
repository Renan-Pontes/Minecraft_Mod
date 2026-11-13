package com.renanpontes.isekaimahou.feature.class_quincy;

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
 * Release 1.0 (onda 4) — <b>Vollständig moldável por magia</b>: a forma G do Quincy tem um ESPAÇO DE RECEITA do módulo
 * {@code transform_laws} (id {@value #SLOT}). O jogador escolhe ali uma receita dele com a forma Transformação;
 * enquanto as asas de luz duram, os efeitos da receita entram nos golpes/flechas e na aura e as leis valem. As asas, a
 * auréola, a saraivada e o nome NÃO mudam; sem receita escolhida, tudo fica igual a antes.
 * <p>
 * A ligação é pela API do núcleo ({@link TransformLaws#begin}/{@link TransformLaws#end}): a fonte
 * {@code isekai_mahou:vollstandig} faz o módulo de leis achar a receita, validar de novo, cobrar a mana e pôr por cima.
 * As consultas ao espaço (receita escolhida, ativa agora) vão por nome de classe: o módulo de leis é procurado em
 * tempo de execução, e sem ele nada aqui quebra.
 */
public final class QuincyForge {
    public static final String SLOT = "vollstandig";
    /** Glifo da forma Transformação (sem ele não dá para montar receita). */
    public static final ResourceLocation FORM = IsekaiMahou.id("transformation");
    /** Dica de primeira vez (texto em {@code hint.isekai_mahou.<id>}). */
    public static final String HINT = "quincy_shape_release";
    /** Marca do módulo de leis: "este jogador tem a liberação do espaço" (o espaço aparece na aba Votos). */
    private static final String SLOT_FLAG = "tforge.slot." + SLOT;
    private static final String FORGE_CLASS = "com.renanpontes.isekaimahou.feature.transform_laws.TransformForge";
    /** Folga sobre o tempo da forma: quem encerra a receita é o fim do Vollständig, não o relógio da sessão. */
    private static final int SLACK = 40;

    /** O jogador tem o Vollständig (o glifo da forma G)? */
    public static boolean hasRelease(MagicData d) {
        return d.knowsGlyph(IsekaiMahou.id(Quincy.VOLLSTANDIG));
    }

    /**
     * O jogador já consegue moldar a forma? Onda 5: SEMPRE — o Vollständig é o corpo da receita, então a forma
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

    /** O Vollständig começou: a receita do espaço (se houver) entra por cima. Só para Quincys. */
    static void begin(ServerPlayer sp, int ticks) {
        if (!Quincy.isQuincy(sp)) return;
        TransformLaws.begin(sp, IsekaiMahou.id(SLOT), List.of(), ticks + SLACK, null);
    }

    /** O Vollständig acabou (as leis da receita cobram o preço de fim, como em qualquer transformação). */
    static void end(ServerPlayer sp) {
        TransformLaws.end(sp, IsekaiMahou.id(SLOT));
    }

    /**
     * Uma vez por segundo: quem tem o Vollständig E a forma Transformação ganha o espaço na aba Votos (antes mesmo da
     * primeira liberação) e a dica do botão "Moldar liberação".
     */
    static void second(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.QUINCY || !hasRelease(d)) return;
        if (!d.hasFlag(SLOT_FLAG)) d.setFlag(SLOT_FLAG, true);
        if (!Hints.seen(sp, HINT)) Hints.show(sp, HINT);
    }

    // =====================================================================================
    //  Consultas ao módulo de leis (por nome: ele pode não estar presente)
    // =====================================================================================
    private static boolean looked;
    private static Method recipeMethod, activeMethod;

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> c = Class.forName(FORGE_CLASS);
            recipeMethod = c.getMethod("recipe", Player.class, String.class);
            activeMethod = c.getMethod("active", LivingEntity.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // sem o módulo de leis: sem receita, nada ativo
        }
    }

    /** Receita escolhida para o espaço (servidor ou cliente); null = nenhuma. */
    @Nullable
    public static SpellRecipe recipe(@Nullable Player player) {
        look();
        if (player == null || recipeMethod == null) return null;
        try {
            return recipeMethod.invoke(null, player, SLOT) instanceof SpellRecipe r && !r.isEmpty() ? r : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** A receita do espaço está valendo agora? (servidor) */
    public static boolean active(@Nullable LivingEntity entity) {
        look();
        if (entity == null || activeMethod == null) return false;
        try {
            return Boolean.TRUE.equals(activeMethod.invoke(null, entity, SLOT));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private QuincyForge() {}
}
