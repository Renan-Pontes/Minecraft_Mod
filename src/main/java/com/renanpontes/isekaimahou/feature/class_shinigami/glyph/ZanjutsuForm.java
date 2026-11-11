package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTasks;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Zanjutsu — a arte da espada: um corte em arco (140°) diante do Shinigami, raio 3,2 (+1 por Expandir; +0,8 segurando
 * a zanpakutō), aplicando os efeitos em cada inimigo do arco. Cada Dividir soma mais um corte em sequência (0,25 s
 * depois, alternando a inclinação). Segurando a zanpakutō a técnica sai 15% mais forte. Pode ser estágio de magia.
 */
public class ZanjutsuForm extends FormGlyph {
    public ZanjutsuForm() {
        super("zanpakuto_zanjutsu", 6);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 12;
    }

    @Override
    public float hintRange() {
        return 3.2F;
    }

    @Override
    public float hintRadius() {
        return 3.2F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        LivingEntity caster = ctx.caster();
        boolean blade = caster != null && caster.getMainHandItem().getItem() instanceof ZanpakutoItem;
        if (blade && !ctx.hasOrigin()) ctx.setPowerMult(ctx.powerMult() * 1.15F);
        float radius = 3.2F + Math.max(0, s.aoe) + (blade ? 0.8F : 0F);
        int slashes = s.count();
        slash(ctx, radius, 0);
        for (int i = 1; i < slashes; i++) {
            final int n = i;
            ShinigamiTasks.later(5 * i, () -> {
                LivingEntity c = ctx.caster();
                if (c == null || !c.isAlive() || c.level() != ctx.level()) return;
                slash(ctx, radius, n);
            });
        }
    }

    private void slash(SpellContext ctx, float radius, int index) {
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        if (caster != null && !ctx.hasOrigin()) {
            eye = caster.getEyePosition();
            look = caster.getLookAngle();
            // passo curto para a frente junto do corte
            Vec3 lunge = new Vec3(look.x, 0, look.z);
            if (lunge.lengthSqr() > 1.0E-4) {
                caster.setDeltaMovement(caster.getDeltaMovement().add(lunge.normalize().scale(0.25)));
                caster.hurtMarked = true;
            }
        }
        Vec3 center = eye.add(0, -0.5, 0);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        int hits = 0;
        for (LivingEntity e : ctx.enemiesAround(center, radius + 1.0)) {
            if (ctx.ignores(e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(center);
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            double dist = toFlat.length();
            if (dist > radius + e.getBbWidth() / 2 || Math.abs(to.y) > 2.2) continue;
            if (dist > 0.8 && toFlat.normalize().dot(flat) < Math.cos(Math.toRadians(70))) continue;
            ctx.impact(e.position().add(0, e.getBbHeight() / 2, 0), look, e);
            ctx.resolveOnEntity(e, caster);
            hits++;
        }
        float tilt = index % 2 == 0 ? 0.9F : -0.9F;
        ShinigamiFx.slashArc(level, center, flat, radius * 0.85, 70, tilt, ShinigamiFx.REIATSU);
        ShinigamiFx.slashArc(level, center.add(0, 0.1, 0), flat, radius * 0.6, 60, tilt, ShinigamiFx.WHITE);
        ShinigamiFx.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F + 0.1F * index);
        if (hits > 0) ShinigamiFx.sound(level, center, SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 1.1F);
        if (caster != null && index == 0) caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        ctx.end(center.add(flat.scale(radius)), look);
    }
}
