package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Furadeira Giratória (efeito de origem, tier 3, Círculo 3): o giro perfura — 4 golpes de 3 em 0,75 s, e o alvo fica
 * Exposto (defesa quebrada). Em blocos, cava um túnel curto na direção do golpe (respeita as regras de destruição,
 * arenas e lugares seguros). Expandir alonga o túnel.
 */
public class SpinDrillEffect extends EffectGlyph {
    public static final float DAMAGE = 3F;
    public static final int HITS = 4;

    public SpinDrillEffect() {
        super("spin_drill", 16);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() { return DAMAGE * HITS; }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(80), ctx.caster(), stats.power() * ctx.powerMult());
        for (int i = 0; i < HITS; i++) {
            final int k = i;
            Runnable hit = () -> {
                if (!le.isAlive() || le.level() != sl) return;
                le.invulnerableTime = 0;
                ctx.damage(le, DAMAGE, stats, null);
                Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
                sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.3);
                SbFx.spinRing(sl, c, SbGlyphs.golden(ctx, stats) ? SbFx.GOLD : SbFx.STEEL, 0.35 + 0.1 * k, 8);
                SbFx.sound(sl, c, SoundEvents.GRINDSTONE_USE, 0.5F, 1.6F + 0.1F * k);
            };
            if (i == 0) hit.run();
            else SpellScheduler.schedule(5 * i, hit);
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel sl = ctx.level();
        if (!Destruction.allowed(sl, ctx.caster())) return;
        Vec3 dir = ctx.look();
        Vec3 from = hit.getLocation().subtract(dir.scale(0.2));
        Vec3 to = hit.getLocation().add(dir.scale(2.5 + 1.5 * Math.max(0, stats.aoe)));
        Destruction.line(sl, from, to, 1.2F, 1.2F, ctx.caster(), Destruction.Mode.BREAK);
        sl.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 10, 0.3, 0.3, 0.3, 0.3);
        SbFx.sound(sl, hit.getLocation(), SoundEvents.GRINDSTONE_USE, 0.8F, 0.8F);
    }
}
