package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Escamas (Dragonato): escamas dracônicas cobrem o corpo — +armadura e resistência, −25% de dano de magias e 20% do
 * dano corpo a corpo volta para quem bateu (efeito {@code race_dragon_scales}; Amplificar forte = nível II). Brilham na
 * cor da ancestralidade. Em aliados também. Com Inverter num inimigo: arranca as escamas dele (Exposto, 2 acúmulos).
 */
public class DragonScalesEffect extends RaceEffectGlyph {
    public static final int TICKS = 240;

    public DragonScalesEffect() {
        super("race_dragon_scales", 10, Element.ARCANE, 2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        CharacterData d = casterData(ctx);
        int rgb = d != null && d.race() == Race.DRAGONBORN ? d.ancestry().color : Race.DRAGONBORN.rgb;
        Vec3 c = RaceFx.center(le);
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            Statuses.apply(le, Statuses.Kind.EXPOSED, 2, stats.duration(140), ctx.caster(), stats.power());
            level.sendParticles(RaceFx.dust(rgb, 1.0F), c.x, c.y, c.z, 18, 0.35, 0.45, 0.35, 0.08);
            RaceFx.sound(level, c, SoundEvents.SHIELD_BREAK, 0.7F, 1.2F);
            return;
        }
        int amp = stats.power() >= 1.8F ? 1 : 0;
        le.addEffect(new MobEffectInstance(CharacterContent.DRAGON_SCALES, stats.duration(TICKS), amp, false, true, true), ctx.caster());
        // Escamas se fechando: losangos de pó subindo em espiral + brilho
        RaceFx.spiral(level, le, RaceFx.dust(rgb, 1.2F), 24, 0.6);
        RaceFx.spiral(level, le, RaceFx.dust(0xFFF2B0, 0.7F), 12, 0.45);
        level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 14, 0.4, 0.5, 0.4, 0.4);
        RaceFx.sound(level, c, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.8F);
        RaceFx.sound(level, c, SoundEvents.ENDER_DRAGON_GROWL, 0.25F, 1.6F);
    }
}
