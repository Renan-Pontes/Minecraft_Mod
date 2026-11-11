package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bakudō nº 61 — Seis Grades de Luz (homenagem Rikujōkōrō): seis lâminas de luz cravam na cintura do alvo e o prendem.
 * Enraizado por 2,5 s e Atordoado por metade disso (Estender aumenta); chefes ficam presos por 40% do tempo. Dano mínimo.
 */
public class BakudoRikujoEffect extends EffectGlyph {
    public static final int DURATION = 50;

    public BakudoRikujoEffect() {
        super("kido_bakudo_61", 10);
        origin(MagicPath.SHINIGAMI);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        int dur = stats.duration(DURATION);
        if (le.getMaxHealth() > 300) dur = Math.max(20, Math.round(dur * 0.4F));
        le.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, dur, 0), ctx.caster());
        le.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, Math.max(10, dur / 2), 0), ctx.caster());
        ctx.damage(le, 1.0F, stats, ctx.caster());
        rods(ctx.level(), le);
    }

    /** As seis lâminas de luz vindo de fora e se cravando no alvo. */
    public static void rods(ServerLevel level, LivingEntity le) {
        Vec3 c = le.position().add(0, le.getBbHeight() * 0.55, 0);
        double r = 1.2 + le.getBbWidth();
        for (int k = 0; k < 6; k++) {
            double a = k * Mth.TWO_PI / 6;
            Vec3 out = c.add(Math.cos(a) * r, (k % 2 == 0 ? 0.25 : -0.15), Math.sin(a) * r);
            ShinigamiFx.line(level, out, c, ShinigamiFx.KIDO_GOLD, 0.15);
        }
        ShinigamiFx.ring(level, c, le.getBbWidth() * 0.7 + 0.2, 14, 0F, ShinigamiFx.WHITE);
        ShinigamiFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
        ShinigamiFx.sound(level, c, SoundEvents.CHAIN_PLACE, 0.8F, 1.2F);
    }

    @Override
    public float baseDamage() {
        return 1.0F;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return -1F;
    }
}
