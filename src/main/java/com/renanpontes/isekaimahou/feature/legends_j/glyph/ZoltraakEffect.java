package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Zoltraak — "a magia ofensiva comum" (homenagem a Frieren): um raio fino de magia pura. Barato, rápido e confiável:
 * dano 6 ARCANO no alvo e o raio continua 8 blocos ATRÁS dele, acertando mais 1 inimigo (+1 por Perfurar) com 60%.
 * Em Si Mesmo: dispara reto da mão até a criatura na mira (32 blocos). Cresce como qualquer glifo com a maestria.
 */
public class ZoltraakEffect extends EffectGlyph {
    public static final float DAMAGE = 6F;
    public static final int COLOR = 0xE6DEFF;
    public static final double BEHIND = 8.0;

    public ZoltraakEffect() {
        super("zoltraak", 8);
        element(Element.ARCANE);
        tier(2);
        minCircle(1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        ServerLevel l = ctx.level();
        Vec3 from;
        LivingEntity hit;
        if (LjUtil.self(ctx, target)) {
            if (c == null || !LjUtil.once(ctx, "zoltraak_self")) return;
            from = c.getEyePosition().add(c.getLookAngle().scale(0.6)).add(0, -0.2, 0);
            hit = LjUtil.lookTarget(c, 32, e -> LjUtil.isFoe(ctx, e));
            Vec3 end = hit != null ? LjUtil.center(hit) : LjUtil.lookPoint(c, 32);
            ray(l, from, end);
            LjUtil.sound(l, from, SoundEvents.ILLUSIONER_CAST_SPELL, 0.7F, 1.8F);
            LjUtil.sound(l, from, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 2.0F);
            if (hit == null) return;
        } else {
            if (!(target instanceof LivingEntity le)) return;
            hit = le;
            from = c != null ? c.getEyePosition() : ctx.eyePos();
        }
        ctx.damage(hit, DAMAGE, stats, c, Element.ARCANE);
        Vec3 hc = LjUtil.center(hit);
        l.sendParticles(ParticleTypes.END_ROD, hc.x, hc.y, hc.z, 4, 0.1, 0.1, 0.1, 0.05);
        // O raio atravessa e segue reto: mais 1 inimigo atrás (+1 por Perfurar), 60% do dano
        Vec3 dir = hc.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 end = hc.add(dir.scale(BEHIND));
        BlockHitResult bh = l.clip(new ClipContext(hc, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hit));
        if (bh.getType() == HitResult.Type.BLOCK) end = bh.getLocation();
        int extra = 1 + Math.max(0, stats.pierce);
        final Vec3 seg0 = hc, seg1 = end;
        final LivingEntity primary = hit;
        List<LivingEntity> behind = l.getEntitiesOfClass(LivingEntity.class, new AABB(seg0, seg1).inflate(0.8),
                e -> e != primary &&LjUtil.isFoe(ctx, e) && e.getBoundingBox().inflate(0.3).clip(seg0, seg1).isPresent());
        behind.sort((a, b) -> Double.compare(a.distanceToSqr(seg0), b.distanceToSqr(seg0)));
        Vec3 last = hc;
        for (int i = 0; i < Math.min(extra, behind.size()); i++) {
            LivingEntity e = behind.get(i);
            if (!LjUtil.once(ctx, "zoltraak@" + e.getUUID())) continue;
            ctx.damage(e, DAMAGE * 0.6F, stats, c, Element.ARCANE);
            last = LjUtil.center(e);
        }
        ray(l, hc, last.equals(hc) ? end : last);
    }

    /** Raio fino branco-violeta com um "anel" de lançamento na origem. */
    private static void ray(ServerLevel l, Vec3 from, Vec3 to) {
        ElementFx.beam(l, from, to, Element.ARCANE, COLOR, 0.18F, 2);
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() > 1.0E-4) ElementFx.shockwave(l, from, COLOR, 0.5F, 6, d.normalize());
    }
}
