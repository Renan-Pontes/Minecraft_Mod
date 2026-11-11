package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Clivar (VENTO, t4, C4) — o corte que se ADAPTA ao alvo: dano = 10 + 8% da vida máxima (3% em chefes) + 1 por ponto
 * de armadura, Sangrando 5, e uma GRADE de cortes desenhada sobre o corpo. Com Si Mesmo, cliva o(s) inimigo(s) mais
 * perto à sua frente (até 8 blocos; Dividir = mais alvos). Alvo protegido pela barreira FECHADA de outro domínio
 * (você do lado de fora): o corte bate na barreira em vez dele.
 */
public class CleaveEffect extends EffectGlyph {
    public CleaveEffect() {
        super("cleave", 22);
        element(Element.WIND);
        tier(4);
        minCircle(4);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (LcUtil.selfTarget(ctx, target)) {
            for (LivingEntity e : inFront(ctx, 8.0, stats.count())) cleave(ctx, stats, e, 10F, 0.08F, 0.03F, 1F, 5);
            return;
        }
        if (target instanceof LivingEntity le) cleave(ctx, stats, le, 10F, 0.08F, 0.03F, 1F, 5);
    }

    /** Inimigos vivos num cone de ~60° à frente, do mais perto ao mais longe. */
    private static List<LivingEntity> inFront(SpellContext ctx, double range, int max) {
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(eye, range)) {
            Vec3 to = LcUtil.center(e).subtract(eye);
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) > 0.5) list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        return list.size() > max ? list.subList(0, max) : list;
    }

    /**
     * Um Clivar no alvo. Usado também pelo Santuário Maligno (com números menores por ser contínuo).
     *
     * @return true se acertou o alvo (false = bateu numa barreira ou o alvo é inválido)
     */
    public static boolean cleave(SpellContext ctx, SpellStats stats, LivingEntity t, float base, float pct, float pctBoss,
                                 float perArmor, int bleed) {
        if (!t.isAlive()) return false;
        ServerLevel l = ctx.level();
        LivingEntity caster = ctx.caster();
        Vec3 c = LcUtil.center(t);
        LcUtil.Wall wall = LcUtil.firstWall(l, ctx.eyePos(), c, caster);
        if (wall != null) {
            LcUtil.strikeWall(l, wall, caster != null ? caster.position() : ctx.eyePos(), caster, (base + 6F) * stats.power() * ctx.powerMult());
            if (LcUtil.budget(l, caster, "cleave_fx", 24)) {
                LcFx.grid(l, wall.point(), 1.2F, 1.6F, l.random.nextFloat() * 6.28F, 6, LcFx.SLASH_CORE);
            }
            return false;
        }
        float frac = LcUtil.isBoss(t) ? pctBoss : pct;
        float dmg = base + frac * t.getMaxHealth() + perArmor * t.getArmorValue();
        ctx.damage(t, dmg, stats, null);
        Statuses.apply(t, Statuses.Kind.BLEEDING, bleed, stats.duration(120), caster, ctx.powerMult());
        if (LcUtil.budget(l, caster, "cleave_fx", 24)) {
            int cuts = 7 + Math.max(0, stats.aoe) * 2;
            LcFx.grid(l, c, t.getBbWidth() * 0.5F + 0.35F, t.getBbHeight() * 0.5F + 0.25F, l.random.nextFloat() * 6.28F, cuts, LcFx.SLASH_CORE);
            l.sendParticles(LcFx.dust(0x9A0A14, 1.5F), c.x, c.y, c.z, 18, t.getBbWidth() * 0.4, t.getBbHeight() * 0.35,
                    t.getBbWidth() * 0.4, 0.12);
            LcFx.sound(l, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.9F);
            LcFx.sound(l, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
        }
        return true;
    }
}
