package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithBladeEntity;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
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
 * "Espada Mágica Efêmera" (homenagem às espadas mágicas do clã de ferreiros amaldiçoado, que soltam magia de exército e
 * se partem; origem do Ferreiro, Círculo 3): uma espada rúnica do elemento da magia surge sobre o Ferreiro e é
 * arremessada — no impacto libera uma explosão elemental: 12 de dano no alvo e metade em quem estiver a 3 blocos
 * (Expandir +), com o toque do elemento (Fogo queima, Água encharca, Terra prende, Vento joga para cima, Luz cega,
 * Sombra murcha). Depois a lâmina se ESTILHAÇA: ela só serve uma vez.
 */
public class EphemeralSwordEffect extends EffectGlyph {
    public static final float DAMAGE = 12.0F;
    public static final float SPLASH = 3.0F;

    public EphemeralSwordEffect() {
        super("smith_ephemeral_sword", 24);
        element(Element.ARCANE);
        origin(MagicPath.SMITH);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public float hintRadius() { return SPLASH; }

    @Override
    public int hintDuration() { return 60; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        Element el = ctx.primaryElement();
        int color = el.color;
        Vec3 tc = le.position().add(0, le.getBbHeight() * 0.5, 0);
        LivingEntity caster = ctx.caster();
        Vec3 above = (caster != null ? caster.position().add(0, caster.getBbHeight() + 1.4, 0) : tc.add(0, 4, 0));
        ForgedBlade b = ForgedBlade.DEFAULT.withMaterial(BladeMaterial.DIAMOND).withPart(ForgedBlade.Part.LENGTH, 3)
                .withPart(ForgedBlade.Part.WIDTH, 2).withFinish(ForgedBlade.Finish.RUNIC).withRune(el);
        if (SmithBladeEntity.hasRoom(sl, ctx.casterId(), 1)) {
            sl.addFreshEntity(SmithBladeEntity.strike(sl, ctx, above, tc.subtract(above), ForgedBladeItem.create(b), color, 1.6F, 1.4F, 1,
                    new SmithBladeEntity.Impact() {
                        @Override
                        public void onEntity(SmithBladeEntity blade, LivingEntity victim) {
                            blast(ctx, stats, victim, el, blade.position());
                        }
                    }));
        } else {
            blast(ctx, stats, le, el, tc);
        }
        sl.sendParticles(SmithFx.dust(color, 1.4F), above.x, above.y, above.z, 20, 0.3, 0.5, 0.3, 0);
        sl.sendParticles(ParticleTypes.ENCHANT, above.x, above.y, above.z, 20, 0.4, 0.6, 0.4, 0.8);
        SmithFx.sound(sl, above, SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 1.6F);
    }

    /** A explosão do elemento no alvo (e metade em volta) e a lâmina estilhaçando. */
    private static void blast(SpellContext ctx, SpellStats stats, LivingEntity main, Element el, Vec3 at) {
        ServerLevel sl = ctx.level();
        float r = stats.radius(SPLASH);
        ctx.damage(main, DAMAGE, stats, null);
        touch(ctx, stats, main, el);
        for (LivingEntity o : ctx.enemiesAround(at, r)) {
            if (o == main || ctx.ignores(o)) continue;
            ctx.damage(o, DAMAGE * 0.5F, stats, null);
            touch(ctx, stats, o, el);
        }
        ElementFx.burst(sl, at, el, 40, r * 0.5);
        ElementFx.ring(sl, at, el, r, 36);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.2);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.25);
        SmithFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.2F);
        SmithFx.sound(sl, at, SoundEvents.GLASS_BREAK, 1.2F, 0.8F);
        SmithFx.sound(sl, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
    }

    /** O "toque" de cada elemento (pela família do elemento: fusões usam o primeiro pai). */
    private static void touch(SpellContext ctx, SpellStats stats, LivingEntity le, Element el) {
        if (!le.isAlive()) return;
        LivingEntity src = ctx.caster();
        float pw = stats.power() * ctx.powerMult();
        int t = stats.duration(60);
        if (el.matches(Element.FIRE)) Statuses.apply(le, Statuses.Kind.BURNING, 2, t, src, pw);
        else if (el.matches(Element.WATER)) Statuses.apply(le, Statuses.Kind.WET, 1, t, src, pw);
        else if (el.matches(Element.EARTH)) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 1), src);
        else if (el.matches(Element.WIND)) {
            le.push(0, 0.7, 0);
            le.hurtMarked = true;
        } else if (el.matches(Element.LIGHT)) le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), src);
        else if (el.matches(Element.SHADOW)) le.addEffect(new MobEffectInstance(MobEffects.WITHER, t, 0), src);
    }
}
