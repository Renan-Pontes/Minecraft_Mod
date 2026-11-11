package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
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
import net.minecraft.world.phys.Vec3;

/**
 * Metal Fundido (Fogo, origem do Ferreiro): um jorro de metal derretido — 5 de dano de Fogo, Em Chamas e o metal que
 * esfria "pesa" (Lentidão). Armadura não protege: alvos com armadura ≥ 6 levam +30% (o metal escorre pelas frestas).
 * Inverter: metal ESFRIADO — sem fogo, mas prende mais (Lentidão II e Exposto).
 */
public class MoltenEffect extends EffectGlyph {
    public static final float DAMAGE = 5.0F;
    public static final int DURATION = 60;

    public MoltenEffect() {
        super("smith_molten", 9);
        element(Element.FIRE);
        origin(MagicPath.SMITH);
        tier(1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return DURATION; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel sl = ctx.level();
        float dmg = DAMAGE;
        if (target instanceof LivingEntity le && le.getArmorValue() >= 6) dmg *= 1.3F;
        ctx.damage(target, dmg, stats, null);
        if (target instanceof LivingEntity le && le.isAlive()) {
            if (stats.inverted) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(DURATION + 20), 1), ctx.caster());
                Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(DURATION), ctx.caster(), stats.power());
            } else {
                Statuses.apply(le, Statuses.Kind.BURNING, 1 + Math.max(0, stats.amplify), stats.duration(DURATION), ctx.caster(), stats.power());
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(DURATION), 0), ctx.caster());
            }
        }
        Vec3 c = SmithFx.center(target);
        sl.sendParticles(stats.inverted ? SmithFx.dust(0x8A8F99, 1.2F) : SmithFx.dust(SmithFx.MOLTEN, 1.2F), c.x, c.y, c.z, 18, 0.3, 0.4, 0.3, 0);
        sl.sendParticles(stats.inverted ? ParticleTypes.CLOUD : ParticleTypes.LAVA, c.x, c.y, c.z, stats.inverted ? 6 : 4, 0.25, 0.3, 0.25, 0.01);
        sl.sendParticles(ParticleTypes.DRIPPING_LAVA, c.x, c.y + 0.4, c.z, 6, 0.3, 0.2, 0.3, 0);
        SmithFx.sound(sl, c, stats.inverted ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.LAVA_POP, 0.9F, 0.8F);
    }
}
