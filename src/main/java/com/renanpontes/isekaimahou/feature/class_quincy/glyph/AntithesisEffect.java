package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.WoundLedger;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A Antítese (homenagem a "The Antithesis"): INVERTE o que aconteceu entre você e o alvo nos últimos 10 s — as feridas
 * trocam de dono. Você se cura do dano que sofreu e o alvo o recebe (como dano mágico, escalado pelo poder; até 60% da
 * vida dele, chefes 20%); o alvo se cura de METADE do dano que sofreu e essa metade passa para você (nunca mata: no
 * mínimo 1 de vida). Use quando estiver mais ferido que o inimigo! Sem feridas para trocar: 2 de dano. Duas espirais
 * espelhadas (luz e sombra) ligam os dois. Rank IV + 350 absorções.
 */
public class AntithesisEffect extends EffectGlyph {
    private static final DustParticleOptions LIGHT = QuincyFx.dust(0xF2FAFF, 1.2F);
    private static final DustParticleOptions SHADOW = QuincyFx.dust(0x1A1030, 1.2F);

    public AntithesisEffect() {
        super("quincy_antithesis", 26);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(4);
        minCircle(7);
    }

    @Override
    public float baseDamage() {
        return 2.0F;
    }

    @Override
    public int hintDuration() {
        return WoundLedger.WINDOW;
    }

    @Override
    public float hintRadius() {
        return -1F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        float mine = caster == null ? 0F : WoundLedger.recent(caster);
        float theirs = WoundLedger.recent(le);
        if (caster == null || (mine <= 0.5F && theirs <= 0.5F)) {
            ctx.damage(le, 2.0F, stats, null);
            return;
        }
        // 1) as minhas feridas: eu me curo, o alvo recebe
        float cap = le.getMaxHealth() * (Statuses.isBossLike(le) ? 0.2F : 0.6F);
        float healed = Math.min(mine, caster.getMaxHealth() - caster.getHealth());
        if (healed > 0) caster.heal(healed);
        float base = Math.min(mine, cap) / Math.max(1F, SpellCost.damageScale());
        if (base > 0) ctx.damage(le, base, stats, null);
        // 2) metade das feridas do alvo: ele se cura e eu recebo (nunca letal)
        float half = theirs * 0.5F;
        if (half > 0 && le.isAlive()) {
            le.heal(half);
            float after = Math.max(1F, caster.getHealth() - half);
            caster.setHealth(after);
        }
        WoundLedger.clear(caster);
        WoundLedger.clear(le);
        visuals(level, caster, le);
        if (caster instanceof ServerPlayer sp && !stats.silent) {
            Announce.send(sp, Quincy.msg("antithesis_shout"), 0xD8E4FF, Announce.STYLE_SPELL);
        }
    }

    /** Duas espirais espelhadas (luz e sombra) trocando de lado entre os dois, e o sinal de "=" sobre cada um. */
    private static void visuals(ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 pa = a.position().add(0, a.getBbHeight() * 0.6, 0);
        Vec3 pb = b.position().add(0, b.getBbHeight() * 0.6, 0);
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.burstNear(level, com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.ANTITHESIS,
                pa, pb, 1F, 0xD8E4FF, a.getId());
        Vec3 d = pb.subtract(pa);
        double len = d.length();
        if (len > 1.0E-3) {
            Vec3 dir = d.normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            int n = Math.min(60, (int) (len * 4));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double ang = t * Math.PI * 4;
                Vec3 base = pa.add(d.scale(t));
                Vec3 off = side.scale(Math.cos(ang) * 0.5).add(up.scale(Math.sin(ang) * 0.5));
                level.sendParticles(LIGHT, base.x + off.x, base.y + off.y, base.z + off.z, 1, 0, 0, 0, 0);
                level.sendParticles(SHADOW, base.x - off.x, base.y - off.y, base.z - off.z, 1, 0, 0, 0, 0);
            }
        }
        for (Vec3 p : new Vec3[]{pa, pb}) {
            for (int k = 0; k < 16; k++) {
                double ang = k * Mth.TWO_PI / 16;
                level.sendParticles(k % 2 == 0 ? LIGHT : SHADOW, p.x + Math.cos(ang) * 0.9, p.y + 0.9, p.z + Math.sin(ang) * 0.9, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
        QuincyFx.sound(level, pa, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
        QuincyFx.sound(level, pb, SoundEvents.BELL_RESONATE, 0.8F, 1.6F);
    }
}
