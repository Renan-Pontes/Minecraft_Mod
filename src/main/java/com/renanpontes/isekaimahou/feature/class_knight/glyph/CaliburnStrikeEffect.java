package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFxNet;
import com.renanpontes.isekaimahou.feature.class_knight.KnightPower;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Caliburn — Espada da Seleção" (a espada na pedra que escolhe o rei; Luz, origem do Cavaleiro). Liberada ao DESPERTAR
 * a Caliburn no Altar do Juramento. Um corte de luz dourada desce sobre o alvo: 9 de dano (×1,4 com a Caliburn ou a
 * Espada do Lago na mão). A seleção abençoa quem a empunha — você ganha 4 corações de Absorção por 10 s (Estender +) —
 * e os aliados a 4 blocos do alvo ganham Regeneração por 3 s.
 */
public class CaliburnStrikeEffect extends EffectGlyph {
    public static final float DAMAGE = 9.0F;
    public static final int GOLD = 0xFFD86B;

    public CaliburnStrikeEffect() {
        super("knight_caliburn_strike", 16);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(3);
        minCircle(2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return 200; }

    @Override
    public float hintRadius() { return 4F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        LivingEntity caster = ctx.caster();
        LegendSword held = KnightSwords.legendIn(caster);
        boolean chosen = held == LegendSword.CALIBURN || held == LegendSword.LAKE;
        ctx.damage(le, DAMAGE * (chosen ? 1.4F : 1F) * KnightPower.mult(ctx), stats, null);
        Vec3 c = KnightFx.center(le);
        // a lâmina dourada desce do céu sobre o alvo
        KnightFxNet.at(sl, KnightFxNet.CALIBURN, c, Vec3.ZERO, 1F, GOLD, le.getId());
        KnightFx.crescent(sl, c, KnightFx.flat(c.subtract(caster != null ? caster.position() : c), new Vec3(0, 0, 1)), 1.3, GOLD, 9, 1.4F);
        KnightFx.sound(sl, c, SoundEvents.BELL_BLOCK, 0.9F, 1.4F);
        KnightFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.1F);
        if (caster != null && caster.isAlive()) {
            caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.duration(200), 1), caster);
            for (LivingEntity ally : sl.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(4.0),
                    a -> a.isAlive() && a != le && ctx.isAlly(a))) {
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0), caster);
            }
            if (chosen && !stats.silent) {
                Announce.send(caster, Component.translatable("announce.isekai_mahou.knight_caliburn_strike"), GOLD, Announce.STYLE_SPELL);
            }
        }
    }
}
