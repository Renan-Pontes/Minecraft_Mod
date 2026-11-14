package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.Auras;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pressão Espiritual (Bleach — Reiatsu), Shinigami. A sua pressão vale {@code (10 + 1,2 × nível) × poder}; quem tem
 * vida máxima bem menor que ela ajoelha.
 * <ul>
 *   <li><b>Em você</b>: aura de 10 s (Estender +), raio 6 (Expandir +). A cada ½ s, inimigos no raio: pressão ≥ 2× a
 *       vida máxima → ajoelham (Enraizados, Lentidão V, Fraqueza II); ≥ 1× → Lentidão II e Fraqueza; ≥ ½ → Lentidão I.
 *       Chefes: só Lentidão I. Jogadores só no PvP, e só sentem o peso (Lentidão I).</li>
 *   <li><b>Num alvo</b>: a mesma regra só nele, por 5 s.</li>
 * </ul>
 * Visual: um anel vermelho escuro "pesando" no chão a cada segundo (nada de tela tremendo).
 */
public class SpiritualPressureEffect extends EffectGlyph {
    public SpiritualPressureEffect() {
        super("lk_spiritual_pressure", 18);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() { return 0F; }

    @Override
    public int hintDuration() { return 200; }

    @Override
    public float hintRadius() { return 6F; }

    /** Pressão do conjurador (nível × poder). */
    public static float pressure(SpellContext ctx, SpellStats stats) {
        return (10F + 1.2F * LkUtil.level(ctx.caster())) * LkUtil.power(ctx, stats);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c == null || !(target instanceof LivingEntity t)) return;
        ServerLevel l = ctx.level();
        if (LkUtil.self(ctx, target)) {
            if (!LkUtil.once(ctx, "pressure")) return;
            float r = stats.radius(6F);
            Auras.pressure(ctx, c, stats.duration(200), r, pressure(ctx, stats));
            Vec3 p = c.position();
            ElementFx.shockwave(l, p.add(0, 0.1, 0), LkFx.REIATSU, r, 24, null);
            ElementFx.aura(l, c, Element.SHADOW, LkFx.REIATSU, 1.2F, 3, 40);
            LkUtil.sound(l, p, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 0.5F);
            LkUtil.say(c, "pressure_on", ChatFormatting.RED);
            return;
        }
        if (!LkUtil.isFoe(ctx, t)) return;
        Auras.press(ctx, c, t, pressure(ctx, stats), stats.duration(100));
        ElementFx.shockwave(l, t.position().add(0, 0.1, 0), LkFx.REIATSU, 1.5F, 14, null);
    }
}
