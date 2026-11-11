package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Grito da Forja (Anão): um brado com o calor da forja. Inimigos atingidos tomam dano de fogo, são empurrados, ficam
 * Atordoados por um instante e pegam fogo. Em você mesmo (Si Mesmo): a forja abençoa o seu equipamento — conserta 10%
 * da durabilidade das armas e armaduras e dá Força. Num aliado (com Inverter): só a Força.
 */
public class ForgeShoutEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 6.0F;
    public static final int STUN_TICKS = 30;
    private static final int EMBER = 0xFF8A3A;

    public ForgeShoutEffect() {
        super("race_forge_shout", 11, Element.FIRE, 2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return STUN_TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        if (le == ctx.caster()) {
            forgeBlessing(ctx, le, stats);
            return;
        }
        if (ctx.isAlly(le)) {
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, stats.duration(160), 0, false, true, true), ctx.caster());
            RaceFx.send(level, ParticleTypes.LAVA, RaceFx.center(le), 4, 0.3, 0);
            RaceFx.sound(level, le.position(), SoundEvents.ANVIL_USE, 0.4F, 1.6F);
            return;
        }
        ctx.damage(le, DAMAGE, stats, null, Element.FIRE);
        Vec3 from = ctx.anchor() != null ? ctx.anchor() : ctx.caster() != null ? ctx.caster().position() : le.position().subtract(ctx.look());
        Vec3 push = le.position().subtract(from);
        push = push.lengthSqr() < 1e-4 ? ctx.look() : push.normalize();
        double k = 0.9 * stats.power() * (1.0 - le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        le.push(push.x * k, 0.3, push.z * k);
        le.hurtMarked = true;
        Statuses.apply(le, Statuses.Kind.STUNNED, 1, stats.duration(STUN_TICKS), ctx.caster(), stats.power());
        le.igniteForSeconds(2F);
        Vec3 c = RaceFx.center(le);
        RaceFx.send(level, ParticleTypes.LAVA, c, 6, 0.3, 0);
        RaceFx.send(level, ParticleTypes.SMALL_FLAME, c, 14, 0.35, 0.06);
        level.sendParticles(RaceFx.dust(EMBER, 1.2F), c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.03);
        RaceFx.sound(level, c, SoundEvents.ANVIL_LAND, 0.55F, 0.75F);
    }

    /** Si Mesmo: a forja conserta 10% das armas/armaduras equipadas e dá Força. */
    private static void forgeBlessing(SpellContext ctx, LivingEntity le, SpellStats stats) {
        ServerLevel level = ctx.level();
        int fixed = 0;
        float frac = 0.10F * stats.power();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = le.getItemBySlot(slot);
            if (s.isEmpty() || !s.isDamageableItem() || s.getDamageValue() <= 0) continue;
            int repair = Math.max(1, Math.round(s.getMaxDamage() * frac));
            s.setDamageValue(Math.max(0, s.getDamageValue() - repair));
            fixed++;
        }
        le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, stats.duration(200), 0, false, true, true), ctx.caster());
        Vec3 c = RaceFx.center(le);
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 1.2, 18, ParticleTypes.SMALL_FLAME);
        RaceFx.send(level, ParticleTypes.LAVA, c, 6 + fixed * 2, 0.4, 0);
        RaceFx.send(level, ParticleTypes.CRIT, c, 16, 0.5, 0.2);
        RaceFx.sound(level, c, SoundEvents.ANVIL_USE, 0.8F, 1.0F);
        RaceFx.sound(level, c, SoundEvents.SMITHING_TABLE_USE, 0.8F, 0.9F);
    }
}
