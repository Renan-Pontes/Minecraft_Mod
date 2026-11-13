package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Véu de Fumaça (origem Ladino, Sombra — benéfico: use com Si Mesmo ou em aliados): invisibilidade curta (3 s;
 * Estender +), e o próximo golpe em 3 s conta como PELAS COSTAS (Emboscada). Os inimigos a 5 blocos (Expandir +)
 * perdem você de vista (largam o alvo) e ficam cegos por 2 s (chefes não).
 */
public class SmokeVeilEffect extends EffectGlyph {
    public static final int TICKS = 60;
    public static final float RADIUS = 5.0F;

    public SmokeVeilEffect() {
        super("rogue_smoke_veil", 9);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public int hintDuration() {
        return TICKS;
    }

    @Override
    public float hintRadius() {
        return RADIUS;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        int ticks = stats.duration(TICKS);
        le.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
        RogueCombat.startAmbush(le, ticks);
        float r = stats.radius(RADIUS);
        Vec3 c = le.position();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r))) {
            if (mob.getTarget() != le || ctx.isAlly(mob)) continue;
            mob.setTarget(null);
            if (!Statuses.isBossLike(mob)) mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), ctx.caster());
        }
        // nuvem densa em volta (quantidade fixa, raio acompanha a área)
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.8, c.z, 6, r * 0.3, 0.5, r * 0.3, 0.01);
        RogueFxNet.at(level, RogueFxNet.SMOKE_VEIL, c, Vec3.ZERO, r, RogueFx.SHADOW, -1);
        RogueFx.sound(level, c, SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.7F);
        RogueFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.1F);
    }
}
