package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithSparkEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Chuva de Fagulhas" (origem do Ferreiro): uma martelada no ar solta um LEQUE de fagulhas incandescentes
 * (4 + 2 por Dividir, máx. 12) que voam em arco curto (~14 blocos). Cada fagulha aplica os efeitos com 40% do poder —
 * é uma "escopeta" de forja: muitas cópias baratas. Custo por cópia: {@link #copies} = número de fagulhas.
 * Espalhar abre o leque; Acelerar = fagulhas mais rápidas e retas. Estágio: sai da origem do estágio.
 */
public class SparkShowerForm extends FormGlyph {
    public static final float SHOT_POWER = 0.4F;
    public static final int BASE_SPARKS = 4;
    public static final int MAX_SPARKS = 12;

    public SparkShowerForm() {
        super("smith_spark_shower", 8);
        origin(MagicPath.SMITH);
        tier(2);
        minCircle(2);
    }

    /** Fagulhas desta conjuração. */
    public static int sparks(SpellStats s) {
        return Math.min(MAX_SPARKS, BASE_SPARKS + 2 * Math.max(0, s.split));
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return sparks(stats);
    }

    @Override
    public int baseCastTicks() { return 4; }

    @Override
    public int baseCooldown() { return 25; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float hintRange() { return 14F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        int n = sparks(s);
        float cone = s.spread ? 32F : 16F;
        float speed = 1.15F * s.speedMult();
        Vec3 origin = ctx.eyePos().add(ctx.look().scale(0.6)).subtract(0, 0.25, 0);
        SpellContext shot = ctx.copy(ctx.powerMult() * SHOT_POWER);
        int color = ctx.recipe().color() >= 0 ? ctx.recipe().color() : SmithFx.MOLTEN;
        for (int i = 0; i < n; i++) {
            float yaw = (ctx.random().nextFloat() - 0.5F) * 2F * cone;
            float pitch = (ctx.random().nextFloat() - 0.5F) * cone * 0.6F - 4F;
            Vec3 dir = com.renanpontes.isekaimahou.glyph.CoreGlyphs.rotate(ctx.look(), yaw, pitch);
            sl.addFreshEntity(SmithSparkEntity.create(sl, shot, origin, dir, speed * (0.85F + ctx.random().nextFloat() * 0.3F), color));
        }
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        SmithFx.forgeSparks(sl, origin, SmithFx.MOLTEN, 16);
        SmithFx.sound(sl, origin, SoundEvents.ANVIL_USE, 0.7F, 1.6F);
        SmithFx.sound(sl, origin, SoundEvents.FIRECHARGE_USE, 0.6F, 1.3F);
    }
}
