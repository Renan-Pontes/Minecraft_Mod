package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Leque de Facas (forma de origem do Ladino, Vento): arremessa 3 facas espectrais em leque (Dividir +2 cada, máx.
 * 11); cada faca aplica os efeitos em quem acertar. Perfurar atravessa, Acelerar deixa mais rápidas, Expandir abre o
 * leque. Custo por cópia: cada faca é uma cópia dos efeitos ({@link #copies}). Pode ser estágio 2+ (sai do ponto do
 * impacto anterior).
 */
public class KnifeFanForm extends FormGlyph {
    public static final int MAX_KNIVES = 11;

    public KnifeFanForm() {
        super("rogue_knife_fan", 7);
        origin(MagicPath.ROGUE);
        element(Element.WIND);
        tier(3);
        minCircle(2);
    }

    /** Número de facas: 3 + 2 por Dividir. */
    public static int knives(SpellStats s) {
        return Math.min(MAX_KNIVES, 3 + 2 * Math.max(0, s.split));
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return knives(stats);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 16;
    }

    @Override
    public float hintRange() {
        return 24F;
    }

    @Override
    public void cast(SpellContext ctx) {
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        int n = knives(s);
        float spread = 50F + 12F * Math.max(0, s.aoe);
        float speed = 1.5F * Math.max(0.5F, s.speedMult());
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look().normalize();
        for (int i = 0; i < n; i++) {
            float off = n == 1 ? 0F : -spread / 2 + spread * i / (n - 1);
            Vec3 dir = look.yRot(off * Mth.DEG_TO_RAD);
            if (s.spread) dir = dir.add((ctx.random().nextDouble() - 0.5) * 0.2, (ctx.random().nextDouble() - 0.5) * 0.15, 0).normalize();
            RogueKnifeEntity knife = RogueKnifeEntity.spell(ctx, eye.add(dir.scale(0.4)).subtract(0, 0.15, 0), dir, speed);
            level.addFreshEntity(knife);
        }
        RogueFxNet.at(level, RogueFxNet.KNIFE_FAN, eye.add(look).subtract(0, 0.2, 0), look, spread, ctx.color() & 0xFFFFFF, -1);
        RogueFx.sound(level, eye, SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.6F);
        RogueFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.8F);
    }
}
