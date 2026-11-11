package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hadō nº 4 — Raio Branco (homenagem Byakurai): um relâmpago branco disparado da ponta do dedo, instantâneo, até 26
 * blocos (+4 por Acelerar). Acerta o primeiro inimigo da linha (+1 por Perfurar) e para nos blocos (Fantasma
 * atravessa). Dividir dispara raios extras em leque. Pode ser estágio de magia.
 */
public class HadoByakuraiForm extends FormGlyph {
    public HadoByakuraiForm() {
        super("kido_hado_4", 7);
        origin(MagicPath.SHINIGAMI);
        element(Element.WIND);
        tier(1);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 16;
    }

    @Override
    public float hintRange() {
        return 26F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        double range = 26 + 4 * Math.max(0, s.speed);
        Vec3 from = ctx.eyePos();
        Vec3 look = ctx.look();
        int n = s.count();
        for (int i = 0; i < n; i++) {
            float yaw = (i - (n - 1) / 2F) * 6F;
            beam(ctx, from, MoonFangForm.rotateYaw(look, yaw), range, 1 + Math.max(0, s.pierce), s.ghost);
        }
        ShinigamiFx.sound(ctx.level(), from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.9F);
        if (ctx.caster() != null && !ctx.hasOrigin()) ctx.caster().swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static void beam(SpellContext ctx, Vec3 from, Vec3 dir, double range, int maxHits, boolean ghost) {
        ServerLevel level = ctx.level();
        Vec3 to = from.add(dir.scale(range));
        HitResult block = null;
        if (!ghost) {
            HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
            if (r.getType() == HitResult.Type.BLOCK) {
                block = r;
                to = r.getLocation();
            }
        }
        // inimigos ao longo da linha, do mais perto ao mais longe
        List<LivingEntity> along = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(1.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == ctx.caster() || ctx.isAlly(e) || ctx.ignores(e)) continue;
            if (e.getBoundingBox().inflate(0.35).clip(from, to).isPresent()) along.add(e);
        }
        along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        Vec3 end = to;
        int hits = 0;
        for (LivingEntity e : along) {
            Vec3 at = e.position().add(0, e.getBbHeight() / 2, 0);
            ctx.impact(at, dir, e);
            ctx.resolveOnEntity(e, ctx.caster());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.2);
            if (++hits >= maxHits) {
                end = at;
                break;
            }
        }
        if (hits == 0 && block instanceof net.minecraft.world.phys.BlockHitResult bh) {
            ctx.impact(bh.getLocation(), dir, null);
            ctx.resolveOnBlock(bh, ctx.caster());
        }
        zigzag(level, from, end);
        ctx.end(end, dir);
    }

    /** O relâmpago branco: um zigue-zague de pó branco com faíscas. */
    private static void zigzag(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        int segs = Math.max(3, Math.min(14, (int) (len / 2)));
        Vec3 prev = from;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = from.add(d.scale(i / (double) segs));
            if (i < segs) p = p.add((level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6);
            ShinigamiFx.line(level, prev, p, ShinigamiFx.WHITE, 0.25);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.05);
            prev = p;
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
    }
}
