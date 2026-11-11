package com.renanpontes.isekaimahou.feature.character.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Determinação (Humano): a vontade humana que se recusa a cair. Tira os efeitos ruins e as condições (menos a
 * Exaustão Mágica, o preço das magias lendárias), cura e dá Absorção. Em aliados (formas de área) faz o mesmo.
 * Com Inverter num inimigo: quebra a vontade dele — tira até 3 efeitos bons e o deixa Exposto.
 */
public class ResolveEffect extends RaceEffectGlyph {
    public static final float HEAL = 4.0F;
    public static final int ABSORB_TICKS = 300;

    public ResolveEffect() {
        super("race_resolve", 10, Element.ARCANE, 1);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public float baseHeal() { return HEAL; }

    @Override
    public int hintDuration() { return ABSORB_TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        boolean friend = le == ctx.caster() || ctx.isAlly(le);
        if (!friend) {
            breakWill(ctx, le, stats);
            return;
        }
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance i : le.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !i.is(CoreRegistries.MAGIC_EXHAUSTION)) {
                bad.add(i.getEffect());
            }
        }
        bad.forEach(le::removeEffect);
        for (Statuses.Kind k : Statuses.Kind.values()) {
            if (Statuses.has(le, k)) Statuses.clear(le, k);
        }
        le.clearFire();
        ctx.heal(le, HEAL, stats);
        int amp = stats.power() >= 1.8F ? 1 : 0;
        le.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.duration(ABSORB_TICKS), amp, false, true, true),
                ctx.caster());

        // Coluna dourada subindo + estrela de totem
        Vec3 c = RaceFx.center(le);
        RaceFx.spiral(level, le, RaceFx.dust(0xE8C77A, 1.1F), 18, 0.55);
        RaceFx.send(level, ParticleTypes.TOTEM_OF_UNDYING, c, 18 + 4 * bad.size(), 0.35, 0.25);
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 0.9, 14, RaceFx.dust(0xFFF2B0, 0.9F));
        RaceFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.35F);
        RaceFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.6F);
    }

    /** Inverter: tira até 3 efeitos bons do inimigo e o deixa Exposto. */
    private static void breakWill(SpellContext ctx, LivingEntity le, SpellStats stats) {
        int removed = 0;
        for (MobEffectInstance i : new ArrayList<>(le.getActiveEffects())) {
            if (removed >= 3) break;
            if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && !i.isInfiniteDuration()) {
                le.removeEffect(i.getEffect());
                removed++;
            }
        }
        Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(120), ctx.caster(), stats.power());
        Vec3 c = RaceFx.center(le);
        RaceFx.send(ctx.level(), ParticleTypes.ENCHANTED_HIT, c, 16 + removed * 6, 0.35, 0.2);
        RaceFx.dustBurst(ctx.level(), c, 0x8A7038, 14, 0.35);
        RaceFx.sound(ctx.level(), c, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.4F);
    }
}
