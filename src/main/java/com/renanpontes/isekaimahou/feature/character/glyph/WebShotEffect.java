package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Disparo de Teia (Aracnídeo): um novelo de teia gruda no alvo — dano leve, Enraizado (não anda) e Lentidão forte.
 * Em você (Si Mesmo): um "fio de seda" que segura a queda (Queda Lenta).
 */
public class WebShotEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 3.0F;
    public static final int TICKS = 80;

    public WebShotEffect() {
        super("race_web_shot", 9, Element.EARTH, 1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
        Vec3 c = RaceFx.center(le);
        if (le == ctx.caster() || ctx.isAlly(le)) {
            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, stats.duration(120), 0, false, true, true), ctx.caster());
            le.resetFallDistance();
            RaceFx.line(level, c, c.add(0, 3.0, 0), RaceFx.dust(0xF0F0F0, 0.7F));
            RaceFx.sound(level, c, SoundEvents.SPIDER_STEP, 0.8F, 1.4F);
            return;
        }
        ctx.damage(le, DAMAGE, stats, null);
        Statuses.apply(le, Statuses.Kind.ROOTED, 1, stats.duration(40), ctx.caster(), stats.power());
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(TICKS), 2, false, true, true), ctx.caster());
        // Novelo de teia envolvendo o alvo
        RaceFx.send(level, web, c, 24, 0.35, 0.05);
        for (int i = 0; i < 3; i++) {
            RaceFx.ring(level, le.position().add(0, 0.3 + i * le.getBbHeight() / 3.0, 0), 0.5, 8, RaceFx.dust(0xF0F0F0, 0.7F));
        }
        RaceFx.sound(level, c, SoundEvents.SPIDER_AMBIENT, 0.7F, 1.5F);
        RaceFx.sound(level, c, SoundEvents.WOOL_PLACE, 1.0F, 0.8F);
    }
}
