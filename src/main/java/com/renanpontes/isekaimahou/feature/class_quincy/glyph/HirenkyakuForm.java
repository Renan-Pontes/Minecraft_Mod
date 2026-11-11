package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Passo Voador (homenagem ao Hirenkyaku): o Quincy desliza sobre uma trilha de Reishi até 6 blocos (+2 por Expandir;
 * +2 com a Letra W) para onde olha, parando antes de paredes, e aplica os efeitos em cada inimigo atravessado. Sem dano
 * de queda logo depois. 1 cópia.
 */
public class HirenkyakuForm extends FormGlyph {
    public HirenkyakuForm() {
        super("quincy_hirenkyaku", 4);
        origin(MagicPath.QUINCY);
        element(Element.WIND);
        tier(1);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public int baseCooldown() {
        return 30;
    }

    @Override
    public float hintRange() {
        return 6F;
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || caster.isPassenger()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        double max = 6 + 2 * Math.max(0, s.aoe);
        if (caster instanceof ServerPlayer sp && Quincy.letter(MagicApi.get(sp)) == Quincy.Letter.WIND) max += 2;
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.35, look.y)), look.z);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 start = caster.position();
        double dist = 0;
        for (double d = 0.5; d <= max; d += 0.5) {
            AABB moved = caster.getBoundingBox().move(dir.scale(d));
            if (!level.noCollision(caster, moved)) break;
            dist = d;
        }
        Vec3 end = start.add(dir.scale(dist));
        // inimigos no caminho (uma vez cada)
        List<LivingEntity> passed = new ArrayList<>();
        AABB path = new AABB(start, end).inflate(1.2, 1.0, 1.2).expandTowards(0, caster.getBbHeight(), 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, e -> e != caster && e.isAlive())) {
            if (ctx.isAlly(e)) continue;
            Vec3 c = e.position();
            double t = Math.max(0, Math.min(1, c.subtract(start).dot(dir) / Math.max(1.0E-3, dist)));
            if (c.distanceTo(start.add(dir.scale(dist * t))) <= 1.4 + e.getBbWidth() / 2) passed.add(e);
        }
        // trilha de Reishi sob os pés
        for (double d = 0; d <= dist; d += 0.6) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(QuincyFx.REISHI, p.x, p.y + 0.05, p.z, 2, 0.25, 0.02, 0.25, 0.0);
            if (((int) (d * 10)) % 12 == 0) QuincyFx.ring(level, p.add(0, 0.05, 0), 0.5, 6, 0F, QuincyFx.WHITE);
        }
        if (caster instanceof ServerPlayer sp) {
            sp.teleportTo(end.x, end.y, end.z);
        } else {
            caster.moveTo(end.x, end.y, end.z, caster.getYRot(), caster.getXRot());
        }
        caster.fallDistance = 0;
        caster.setDeltaMovement(dir.scale(0.25));
        caster.hurtMarked = true;
        QuincyFx.sound(level, start, SoundEvents.PHANTOM_FLAP, 0.7F, 1.9F);
        QuincyFx.sound(level, end, SoundEvents.AMETHYST_BLOCK_STEP, 1.0F, 1.6F);
        for (LivingEntity e : passed) ctx.resolveOnEntity(e, caster);
        ctx.end(end.add(0, caster.getBbHeight() * 0.6, 0), dir);
    }
}
