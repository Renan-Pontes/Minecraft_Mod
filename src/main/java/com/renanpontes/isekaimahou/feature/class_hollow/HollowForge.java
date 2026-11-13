package com.renanpontes.isekaimahou.feature.class_hollow;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Release 1.0 (onda 4) — <b>Resurrección moldável por magia</b>: a forma liberada do Arrancar tem um ESPAÇO DE RECEITA do
 * módulo {@code transform_laws} (id {@value #SLOT}). O jogador escolhe ali uma receita dele com a forma Transformação;
 * enquanto a Resurrección dura, os efeitos da receita entram nos golpes e na aura e as leis valem. O arquétipo
 * (Pantera, Murciélago, Serpiente), o visual e as técnicas NÃO mudam; sem receita escolhida, tudo fica igual a antes.
 * <p>
 * Começar e terminar já estão ligados pelos ouvintes {@link HollowRelease#ON_START}/{@link HollowRelease#ON_END} (o
 * módulo de leis se inscreve neles); esta classe só cuida do que é da tela da classe: o botão "Moldar liberação", a
 * dica de primeira vez e as consultas ao espaço — por nome de classe, porque o módulo de leis é procurado em tempo de
 * execução e, sem ele, nada aqui quebra.
 */
public final class HollowForge {
    public static final String SLOT = "hollow_resurreccion";
    /** Glifo da forma Transformação (sem ele não dá para montar receita). */
    public static final ResourceLocation FORM = IsekaiMahou.id("transformation");
    /** Dica de primeira vez (texto em {@code hint.isekai_mahou.<id>}). */
    public static final String HINT = "hollow_shape_release";
    private static final String FORGE_CLASS = "com.renanpontes.isekaimahou.feature.transform_laws.TransformForge";

    /**
     * O jogador já consegue moldar a Resurrección? Onda 5: SEMPRE — a liberação é o corpo da receita, então a forma
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

    /** De tempos em tempos: o Arrancar que já tem a forma Transformação recebe a dica do botão "Moldar liberação". */
    static void tick(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.HOLLOW || Hollow.release(d) == null) return;
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

    private HollowForge() {}
}
