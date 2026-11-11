package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rotação Medicinal (efeito benéfico de origem, tier 2): a técnica da família de médicos — o giro fecha feridas e
 * "desenrosca" o mal do corpo. Cura 5, tira 1 efeito ruim (2 com Amplificar) e dá Regeneração I por 3 s.
 * Inverter (em inimigos): o giro desfaz as bênçãos — dano 4 e tira 1 efeito bom.
 */
public class SpinHealEffect extends EffectGlyph {
    public static final float HEAL = 5F;
    public static final float INVERTED_DAMAGE = 4F;

    public SpinHealEffect() {
        super("spin_heal", 10);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public float baseHeal() { return HEAL; }

    @Override
    public int hintDuration() { return 60; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        boolean ally = target == ctx.caster() || ctx.isAlly(target);
        if (stats.inverted && !ally) {
            ctx.damage(le, INVERTED_DAMAGE, stats, null);
            strip(le, MobEffectCategory.BENEFICIAL, 1);
            SbFx.spiral(ctx.level(), le.position(), SbFx.STEEL, le.getBbWidth() * 0.7 + 0.3, le.getBbHeight(), 12, 0F);
            return;
        }
        ctx.heal(le, HEAL, stats);
        strip(le, MobEffectCategory.HARMFUL, stats.amplify > 0 ? 2 : 1);
        le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(60), 0), ctx.caster());
        SbFx.spiral(ctx.level(), le.position(), SbFx.HEAL, le.getBbWidth() * 0.7 + 0.3, le.getBbHeight() + 0.2, 16,
                ctx.random().nextFloat() * 6F);
        SbFx.sound(ctx.level(), le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
        if (ctx.casterPlayer() instanceof ServerPlayer sp) MagicApi.addCounter(sp, SbProgress.SPIN_HEALS, 1);
    }

    /** Tira até {@code n} efeitos da categoria (os mais longos primeiro). */
    private static void strip(LivingEntity le, MobEffectCategory category, int n) {
        List<MobEffectInstance> list = new ArrayList<>();
        for (MobEffectInstance i : le.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == category && !i.isInfiniteDuration()) list.add(i);
        }
        list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
        for (int k = 0; k < Math.min(n, list.size()); k++) {
            Holder<MobEffect> eff = list.get(k).getEffect();
            le.removeEffect(eff);
        }
    }
}
