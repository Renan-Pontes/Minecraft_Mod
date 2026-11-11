package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
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
 * Fúria Sangrenta (Meio-Orc): o sangue da Horda ferve. Paga 10% da vida atual (quando é em você) e entra em fúria:
 * +25% de dano corpo a corpo, ataques e passos mais rápidos e 15% do dano corpo a corpo volta como vida (efeito
 * {@code race_blood_fury}; Amplificar forte = nível II). Em aliados não cobra sangue. Com Inverter num inimigo: abre as
 * veias dele (Sangramento, 3 acúmulos).
 */
public class BloodFuryEffect extends RaceEffectGlyph {
    public static final int TICKS = 200;
    private static final int BLOOD = 0xB01E28;

    public BloodFuryEffect() {
        super("race_blood_fury", 10, Element.ARCANE, 1);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        Vec3 c = RaceFx.center(le);
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            Statuses.apply(le, Statuses.Kind.BLEEDING, 3, stats.duration(120), ctx.caster(), stats.power());
            level.sendParticles(RaceFx.dust(BLOOD, 1.2F), c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.05);
            RaceFx.sound(level, c, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8F, 0.7F);
            return;
        }
        if (le == ctx.caster() && le.getHealth() > 2F) {
            // O preço em sangue (sem passar pelo dano: não acorda armaduras nem quebra a conjuração)
            le.setHealth(Math.max(1F, le.getHealth() - le.getHealth() * 0.10F));
        }
        int amp = stats.power() >= 1.8F ? 1 : 0;
        le.addEffect(new MobEffectInstance(CharacterContent.BLOOD_FURY, stats.duration(TICKS), amp, false, true, true), ctx.caster());
        level.sendParticles(RaceFx.dust(BLOOD, 1.6F), c.x, c.y, c.z, 40, 0.5, 0.7, 0.5, 0.05);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 4, 0.3, 0.1, 0.3, 0);
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 1.4, 20, RaceFx.dust(0x6E0E14, 1.4F));
        RaceFx.sound(level, c, SoundEvents.RAVAGER_ROAR, 0.8F, 1.25F);
        RaceFx.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
    }
}
