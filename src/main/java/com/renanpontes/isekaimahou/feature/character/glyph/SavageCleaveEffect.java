package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Golpe Selvagem (Meio-Orc): um talho brutal que abre a carne — dano alto, Sangramento (2 acúmulos) e empurrão.
 * Ferido, o orc bate mais forte: com menos da metade da vida, +50% de dano.
 */
public class SavageCleaveEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 7.0F;
    private static final int BLOOD = 0xB01E28;

    public SavageCleaveEffect() {
        super("race_savage_cleave", 9, Element.ARCANE, 2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return 100; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        boolean wounded = caster != null && caster.getHealth() < caster.getMaxHealth() * 0.5F;
        ctx.damage(le, DAMAGE * (wounded ? 1.5F : 1F), stats, null);
        Statuses.apply(le, Statuses.Kind.BLEEDING, 2, stats.duration(100), caster, stats.power());
        Vec3 dir = caster != null ? le.position().subtract(caster.position()) : ctx.look();
        dir = dir.lengthSqr() < 1e-4 ? ctx.look() : dir.normalize();
        double k = 0.6 * (1.0 - le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        le.push(dir.x * k, 0.15, dir.z * k);
        le.hurtMarked = true;

        // Arco de corte vermelho na frente do alvo
        Vec3 c = RaceFx.center(le);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = -4; i <= 4; i++) {
            Vec3 p = c.add(side.scale(i * 0.22)).add(0, -i * 0.12, 0).subtract(dir.scale(0.4 - Math.abs(i) * 0.05));
            level.sendParticles(RaceFx.dust(i % 2 == 0 ? BLOOD : 0xE04040, 1.1F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, wounded ? 24 : 12, 0.35, 0.35, 0.35, 0.3);
        RaceFx.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.75F);
        if (wounded) RaceFx.sound(level, c, SoundEvents.RAVAGER_ATTACK, 0.8F, 1.1F);
    }
}
